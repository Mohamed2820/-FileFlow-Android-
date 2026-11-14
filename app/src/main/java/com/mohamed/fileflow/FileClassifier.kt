package com.mohamed.fileflow

import java.util.Locale

/**
 * Offline, explainable classification.
 * Media/installers are identified by reliable MIME/format evidence first.
 * Named academic subjects and explicit project names are evaluated before
 * source-code extensions so a file such as "Genetics.py" belongs to Study.
 */
object FileClassifier {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "svg")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "ts", "mpeg", "mpg")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "mid", "midi", "amr")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "md", "epub", "csv")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val codeExtensions = setOf("kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "xml", "json", "yaml", "yml", "toml", "c", "h", "cpp", "hpp", "go", "rs", "sh", "gradle", "properties", "sql")
    private val installerExtensions = setOf("apk", "aab", "apks", "xapk")

    private val studySubjects = linkedMapOf(
        "فسيولوجي" to listOf("فسيولوجي", "فسيولوجيا", "physiology", "physiological"),
        "وراثة" to listOf("وراثة", "وراثه", "جينات", "جيني", "genetics", "genetic"),
        "كيمياء" to listOf("كيمياء", "كيميا", "chemistry", "chemical", "biochemistry", "biochemical"),
        "أراضي" to listOf("أراضي", "اراضي", "تربة", "تربه", "soil", "soils"),
        "نبات" to listOf("نبات", "نباتات", "botany", "plant", "plants"),
        "ميكروبيولوجي" to listOf("ميكروبيولوجي", "ميكروبيولوجيا", "ميكروبات", "microbiology", "microbe"),
        "بيولوجيا جزيئية" to listOf("بيولوجيا جزيئية", "جزيئية", "molecular biology", "molecular"),
        "خلية" to listOf("خلية", "خلايا", "cell biology", "cytology"),
        "إحصاء" to listOf("إحصاء", "احصاء", "statistics", "statistical"),
        "اقتصاد زراعي" to listOf("اقتصاد زراعي", "agricultural economics", "agri economics"),
        "فاكهة" to listOf("فاكهة", "فاكهه", "pomology", "fruit"),
        "ميكنة" to listOf("ميكنة", "ميكنه", "mechanization", "agricultural machinery"),
        "تقسيم نبات" to listOf("تقسيم نبات", "تصنيف نبات", "plant taxonomy", "plant classification")
    )

    fun studySubject(name: String): String? {
        val normalized = normalize(name)
        return studySubjects.entries.firstOrNull { (_, terms) ->
            terms.any { term -> matchesTerm(normalized, normalize(term)) }
        }?.key
    }

    fun classify(name: String, mimeType: String?): String {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val normalizedName = normalize(name)
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return when {
            mime.startsWith("image/") || extension in imageExtensions -> "images"
            mime.startsWith("video/") || extension in videoExtensions -> "videos"
            mime.startsWith("audio/") || extension in audioExtensions -> "audio"
            mime == "application/vnd.android.package-archive" || extension in installerExtensions -> "installers"

            // Semantic filename matching intentionally precedes code extensions.
            studySubject(name) != null -> "study"
            listOf("مشروع", "مشاريع", "مشروعات", "project", "projects").any {
                matchesTerm(normalizedName, normalize(it))
            } -> "projects"

            mime == "application/pdf" || mime.startsWith("text/") ||
                mime.contains("msword") || mime.contains("officedocument") ||
                mime.contains("spreadsheet") || mime.contains("presentation") ||
                mime.contains("epub") || extension in documentExtensions -> "documents"
            mime.contains("zip") || mime.contains("rar") || extension in archiveExtensions -> "archives"
            extension in codeExtensions -> "code"
            else -> "other"
        }
    }

    private fun matchesTerm(text: String, term: String): Boolean {
        if (term.any { it.code in 0x0600..0x06FF }) return text.contains(term)
        return Regex("(^|[^a-z0-9])" + Regex.escape(term) + "([^a-z0-9]|$)")
            .containsMatchIn(text)
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace('_', ' ')
        .replace('-', ' ')
        .replace('.', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
