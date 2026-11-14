package com.mohamed.fileflow

import java.util.Locale

/** Offline classification based on a provider MIME type and file extension. */
object FileClassifier {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "svg")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "ts", "mpeg", "mpg")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "mid", "midi", "amr")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "md", "epub", "csv")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val codeExtensions = setOf("kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "xml", "json", "yaml", "yml", "toml", "c", "h", "cpp", "hpp", "go", "rs", "sh", "gradle", "properties", "sql")
    private val installerExtensions = setOf("apk", "aab", "apks", "xapk")

    fun classify(name: String, mimeType: String?): String {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") || extension in imageExtensions -> "images"
            mime.startsWith("video/") || extension in videoExtensions -> "videos"
            mime.startsWith("audio/") || extension in audioExtensions -> "audio"
            mime == "application/vnd.android.package-archive" || extension in installerExtensions -> "installers"
            extension in codeExtensions -> "code"
            mime == "application/pdf" || mime.startsWith("text/") ||
                mime.contains("msword") || mime.contains("officedocument") ||
                mime.contains("spreadsheet") || mime.contains("presentation") ||
                mime.contains("epub") || extension in documentExtensions -> "documents"
            mime.contains("zip") || mime.contains("rar") || extension in archiveExtensions -> "archives"
            else -> "other"
        }
    }
}
