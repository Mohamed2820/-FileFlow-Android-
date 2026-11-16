package com.mohamed.fileflow

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class ExtractedContent(val text: String, val method: String, val error: String? = null)

/** Bounded, on-device text extraction. Original files are only read, never modified. */
object FileContentReader {
    private const val MAX_TEXT_CHARS = 20_000
    private const val MAX_PLAIN_BYTES = 2 * 1024 * 1024
    private const val MAX_OFFICE_BYTES = 4 * 1024 * 1024
    private const val MAX_PDF_BYTES = 30L * 1024L * 1024L
    private const val MAX_ZIP_ENTRIES = 250
    private val pdfLock = Any()
    @Volatile private var pdfInitialized = false

    private val plainTextExtensions = setOf(
        "txt", "md", "csv", "json", "xml", "html", "htm", "log",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "css",
        "sql", "yaml", "yml", "toml", "properties", "gradle", "sh",
        "c", "h", "cpp", "hpp", "go", "rs"
    )

    fun extract(context: Context, uri: Uri, name: String, sizeBytes: Long): ExtractedContent {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in plainTextExtensions && extension !in setOf("pdf", "docx", "pptx", "xlsx")) {
            return ExtractedContent("", "metadata_only")
        }
        if (extension == "pdf" && sizeBytes > MAX_PDF_BYTES) {
            return ExtractedContent("", "pdf_skipped_large")
        }

        return try {
            when {
                extension in plainTextExtensions -> {
                    val bytes = openInput(context, uri).use { readLimited(it, MAX_PLAIN_BYTES) }
                    ExtractedContent(decodeText(bytes).take(MAX_TEXT_CHARS), "plain_text")
                }
                extension == "pdf" -> extractPdf(context, uri)
                else -> extractOffice(context, uri, extension)
            }
        } catch (error: Exception) {
            ExtractedContent(
                "",
                "extraction_error",
                "\${error.javaClass.simpleName}: \${error.localizedMessage ?: error.message ?: "unknown extraction error"}"
            )
        }
    }

    private fun openInput(context: Context, uri: Uri): InputStream {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw IllegalArgumentException("File URI has no path")
            return FileInputStream(File(path))
        }
        return context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Android returned no input stream for $uri")
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var remaining = maxBytes
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val charset: Charset
        val offset: Int
        when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                charset = StandardCharsets.UTF_8
                offset = 3
            }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                charset = StandardCharsets.UTF_16LE
                offset = 2
            }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                charset = StandardCharsets.UTF_16BE
                offset = 2
            }
            else -> {
                val utf8 = try {
                    StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString()
                } catch (_: Exception) { null }
                return (utf8 ?: Charset.forName("windows-1256")
                    .decode(ByteBuffer.wrap(bytes)).toString()).take(MAX_TEXT_CHARS)
            }
        }
        return charset.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
            .take(MAX_TEXT_CHARS)
    }

    private fun extractPdf(context: Context, uri: Uri): ExtractedContent {
        synchronized(pdfLock) {
            if (!pdfInitialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                pdfInitialized = true
            }
        }
        openInput(context, uri).use { input ->
            PDDocument.load(input).use { document ->
                return ExtractedContent(PDFTextStripper().getText(document).take(MAX_TEXT_CHARS), "pdf_text")
            }
        }
    }

    private fun extractOffice(context: Context, uri: Uri, extension: String): ExtractedContent {
        val selectedEntries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0
        var entryCount = 0
        openInput(context, uri).use { raw ->
            ZipInputStream(raw).use { zip ->
                while (entryCount < MAX_ZIP_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    val path = entry.name
                    val relevant = when (extension) {
                        "docx" -> path == "word/document.xml"
                        "pptx" -> Regex("""ppt/slides/slide\d+\.xml""").matches(path)
                        "xlsx" -> path == "xl/sharedStrings.xml" ||
                            Regex("""xl/worksheets/sheet\d+\.xml""").matches(path)
                        else -> false
                    }
                    if (relevant && !entry.isDirectory && totalBytes < MAX_OFFICE_BYTES) {
                        val bytes = readLimited(zip, MAX_OFFICE_BYTES - totalBytes)
                        totalBytes += bytes.size
                        selectedEntries[path] = bytes
                    }
                    zip.closeEntry()
                }
            }
        }

        val text = when (extension) {
            "docx" -> selectedEntries["word/document.xml"]?.let(::xmlText).orEmpty()
            "pptx" -> selectedEntries.toSortedMap().values.joinToString("\n") { xmlText(it) }
            "xlsx" -> {
                val shared = selectedEntries["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
                selectedEntries.filterKeys { it.startsWith("xl/worksheets/sheet") }
                    .toSortedMap().values.joinToString("\n") { worksheetText(it, shared) }
            }
            else -> ""
        }
        return ExtractedContent(text.take(MAX_TEXT_CHARS), "office_open_xml")
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(String(bytes, StandardCharsets.UTF_8)))
        return parser
    }

    private fun localTag(parser: XmlPullParser): String = (parser.name ?: "").substringAfter(':')

    private fun xmlText(bytes: ByteArray): String {
        val parser = newParser(bytes)
        val result = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && result.length < MAX_TEXT_CHARS) {
            when (event) {
                XmlPullParser.START_TAG -> if (localTag(parser) == "t") {
                    result.append(parser.nextText()).append(' ')
                }
                XmlPullParser.END_TAG -> if (localTag(parser) in setOf("p", "tr", "sp")) {
                    result.append('\n')
                }
            }
            event = parser.next()
        }
        return result.toString()
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        var totalChars = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && totalChars < MAX_TEXT_CHARS * 4) {
            when (event) {
                XmlPullParser.START_TAG -> when (localTag(parser)) {
                    "si" -> { inItem = true; current.setLength(0) }
                    "t" -> if (inItem) current.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (localTag(parser) == "si" && inItem) {
                    strings += current.toString()
                    totalChars += current.length
                    inItem = false
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun worksheetText(bytes: ByteArray, shared: List<String>): String {
        val parser = newParser(bytes)
        val result = StringBuilder()
        var inCell = false
        var cellType = ""
        var cellValue = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && result.length < MAX_TEXT_CHARS) {
            when (event) {
                XmlPullParser.START_TAG -> when (localTag(parser)) {
                    "c" -> {
                        inCell = true
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = ""
                    }
                    "v", "t" -> if (inCell) {
                        val value = parser.nextText()
                        if (cellValue.isBlank()) cellValue = value
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag(parser) == "c" && inCell) {
                        val value = if (cellType == "s") {
                            shared.getOrNull(cellValue.toIntOrNull() ?: -1).orEmpty()
                        } else cellValue
                        if (value.isNotBlank()) result.append(value).append(' ')
                        inCell = false
                    } else if (localTag(parser) == "row") result.append('\n')
                }
            }
            event = parser.next()
        }
        return result.toString()
    }
}
