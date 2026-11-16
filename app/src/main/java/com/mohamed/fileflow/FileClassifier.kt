package com.mohamed.fileflow

import java.util.Locale

/**
 * Offline, explainable classification.
 * Media/installers are identified by reliable MIME/format evidence first.
 * Named academic subjects and explicit project names are evaluated before
 * source-code extensions so a file such as "Genetics.py" belongs to Study.
 */
data class SmartClassification(
    val mainCategory: String,
    val subCategory: String? = null,
    val confidence: Double = 0.0
)

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
        "تقسيم نبات" to listOf("تقسيم نبات", "تصنيف نبات", "plant taxonomy", "plant classification"),
        "بيوتكنولوجي" to listOf("بيوتكنولوجي", "تقنية حيوية", "biotechnology", "biotech"),
        "هندسة وراثية" to listOf("هندسة وراثية", "genetic engineering"),
        "زراعة أنسجة" to listOf("زراعة أنسجة", "زراعة انسجة", "tissue culture", "plant tissue"),
        "مناعة" to listOf("مناعة", "مناعه", "immunology", "immune system"),
        "علم الحيوان" to listOf("علم الحيوان", "حيوان", "zoology", "animal science"),
        "معلوماتية حيوية" to listOf("معلوماتية حيوية", "bioinformatics"),
        "أحياء دقيقة" to listOf("أحياء دقيقة", "احياء دقيقه", "microbiology", "microbial")
    )

    fun studySubject(name: String): String? {
        val normalized = normalize(name)
        var bestSubject: String? = null
        var bestMatchedLength = 0
        for ((subject, terms) in studySubjects) {
            for (term in terms) {
                val normalizedTerm = normalize(term)
                if (normalizedTerm.length > bestMatchedLength && matchesTerm(normalized, normalizedTerm)) {
                    bestSubject = subject
                    bestMatchedLength = normalizedTerm.length
                }
            }
        }
        return bestSubject
    }


    /**
     * Classifies semantic documents using filename, all parent-folder names and
     * extracted text. The file extension is retained only as the third-level
     * grouping key; it never overrides a confident subject match.
     */
    fun classifyDetailed(
        name: String,
        mimeType: String?,
        parentPath: String = "",
        extractedText: String = ""
    ): SmartClassification {
        val fallback = classify(name, mimeType)
        if (fallback in setOf("images", "videos", "audio", "installers")) {
            return SmartClassification(fallback)
        }

        val normalizedName = normalize(name)
        val normalizedPath = normalize(parentPath)
        val normalizedText = normalize(extractedText.take(20_000))
        val scores = studySubjects.map { (subject, terms) ->
            val score = terms.distinct().sumOf { term ->
                val normalizedTerm = normalize(term)
                var value = 0.0
                val occurrences = countTerm(normalizedText, normalizedTerm)
                if (occurrences > 0) value += 3.0 * kotlin.math.ln1p(occurrences.toDouble())
                if (matchesTerm(normalizedName, normalizedTerm)) value += 5.0
                if (matchesTerm(normalizedPath, normalizedTerm)) value += 4.0
                value
            }
            subject to score
        }.sortedByDescending { it.second }

        val best = scores.firstOrNull()
        val secondScore = scores.getOrNull(1)?.second ?: 0.0
        if (best != null && best.second >= 2.0) {
            val confidence = ((best.second - secondScore) / best.second).coerceIn(0.0, 1.0)
            if (confidence >= 0.25) {
                val textAdjustedConfidence = if (normalizedText.isBlank()) confidence * 0.5 else confidence
                return SmartClassification("study", best.first, textAdjustedConfidence)
            }
        }

        val projectTerms = listOf("مشروع", "مشاريع", "مشروعات", "project", "projects")
        val projectScore = projectTerms.sumOf { term ->
            val normalizedTerm = normalize(term)
            var value = 0.0
            val occurrences = countTerm(normalizedText, normalizedTerm)
            if (occurrences > 0) value += 3.0 * kotlin.math.ln1p(occurrences.toDouble())
            if (matchesTerm(normalizedName, normalizedTerm)) value += 5.0
            if (matchesTerm(normalizedPath, normalizedTerm)) value += 4.0
            value
        }
        if (projectScore >= 5.0) {
            val textAdjustedConfidence = if (normalizedText.isBlank()) 0.5 else 1.0
            return SmartClassification("projects", confidence = textAdjustedConfidence)
        }

        return SmartClassification(fallback, confidence = 0.0)
    }

    private fun countTerm(text: String, term: String): Int {
        if (text.isBlank() || term.isBlank()) return 0
        return if (term.any { it.code in 0x0600..0x06FF }) {
            Regex(Regex.escape(term)).findAll(text).count()
        } else {
            Regex("(^|[^a-z0-9])" + Regex.escape(term) + "([^a-z0-9]|$)")
                .findAll(text).count()
        }
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
            extension in codeExtensions -> "code"

            mime == "application/pdf" || mime.startsWith("text/") ||
                mime.contains("msword") || mime.contains("officedocument") ||
                mime.contains("spreadsheet") || mime.contains("presentation") ||
                mime.contains("epub") || extension in documentExtensions -> "documents"
            mime.contains("zip") || mime.contains("rar") || extension in archiveExtensions -> "archives"
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
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ة', 'ه')
        .replace('ى', 'ي')
        .map { character ->
            if (character in '\u0660'..'\u0669') {
                ('0'.code + (character.code - '\u0660'.code)).toChar()
            } else character
        }
        .joinToString("")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace('.', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
