package com.mohamed.fileflow

import org.junit.Assert.assertEquals
import org.junit.Test

class FileClassifierTest {
    @Test
    fun classifiesCommonMediaFromExtensionWhenMimeIsGeneric() {
        assertEquals("images", FileClassifier.classify("camera.JPG", "application/octet-stream"))
        assertEquals("videos", FileClassifier.classify("movie.MP4", "application/octet-stream"))
        assertEquals("audio", FileClassifier.classify("voice.M4A", "application/octet-stream"))
    }

    @Test
    fun classifiesDocumentsArchivesCodeAndInstallers() {
        assertEquals("documents", FileClassifier.classify("lecture.PDF", "application/octet-stream"))
        assertEquals("documents", FileClassifier.classify("notes.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals("archives", FileClassifier.classify("backup.7z", "application/octet-stream"))
        assertEquals("code", FileClassifier.classify("Main.KT", "application/octet-stream"))
        assertEquals("code", FileClassifier.classify("worker.py", "text/plain"))
        assertEquals("installers", FileClassifier.classify("package.apk", "application/octet-stream"))
    }

    @Test
    fun keepsUnknownFormatsUnclassified() {
        assertEquals("other", FileClassifier.classify("opaque.data", "application/octet-stream"))
        assertEquals("other", FileClassifier.classify("no-extension", null))
    }

    @Test
    fun usesMimeTypeForExtensionlessMedia() {
        assertEquals("images", FileClassifier.classify("photo", "image/jpeg"))
        assertEquals("videos", FileClassifier.classify("clip", "video/mp4"))
        assertEquals("audio", FileClassifier.classify("recording", "audio/ogg"))
    }
    @Test
    fun academicSubjectNameWinsOverSourceCodeExtension() {
        assertEquals("study", FileClassifier.classify("Genetics.py", "application/octet-stream"))
        assertEquals("study", FileClassifier.classify("محاضرات فسيولوجي.kt", "text/plain"))
        assertEquals("وراثة", FileClassifier.studySubject("Genetics_Lecture_02.pdf"))
        assertEquals("فسيولوجي", FileClassifier.studySubject("ملخص فسيولوجيا النبات.pdf"))
        assertEquals("زراعة أنسجة", FileClassifier.studySubject("Plant_Tissue_Culture_Notes.pdf"))
        assertEquals("بيوتكنولوجي", FileClassifier.studySubject("Introduction_to_Biotechnology.pdf"))
    }

    @Test
    fun smartClassificationUsesParentFoldersAndContent() {
        val byFolder = FileClassifier.classifyDetailed(
            name = "chapter-01.pdf",
            mimeType = "application/pdf",
            parentPath = "الدراسة/الكيمياء"
        )
        assertEquals("study", byFolder.mainCategory)
        assertEquals("كيمياء", byFolder.subCategory)
        org.junit.Assert.assertTrue(byFolder.confidence >= 0.25)

        val byContent = FileClassifier.classifyDetailed(
            name = "lecture-notes.pdf",
            mimeType = "application/pdf",
            extractedText = "Introduction to organic chemistry and chemical bonds"
        )
        assertEquals("study", byContent.mainCategory)
        assertEquals("كيمياء", byContent.subCategory)
    }

    @Test
    fun arabicNormalizationRemovesDiacriticsAndNormalizesDigits() {
        val result = FileClassifier.classifyDetailed(
            name = "ملخص كِيمِيَاء ٢٠٢٦.pdf",
            mimeType = "application/pdf"
        )
        assertEquals("study", result.mainCategory)
        assertEquals("كيمياء", result.subCategory)
    }

    @Test
    fun explicitProjectNamesAreGroupedAsProjects() {
        assertEquals("projects", FileClassifier.classify("MOMO_Project_Main.kt", "application/octet-stream"))
        assertEquals("projects", FileClassifier.classify("مشروع التخرج.pdf", "application/pdf"))
    }

}
