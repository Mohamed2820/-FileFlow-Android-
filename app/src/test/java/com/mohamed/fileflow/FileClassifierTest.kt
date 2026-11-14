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
}
