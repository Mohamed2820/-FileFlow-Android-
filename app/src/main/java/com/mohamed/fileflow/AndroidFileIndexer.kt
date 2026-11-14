package com.mohamed.fileflow

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque

/**
 * Device-local file index. It stores metadata and content URIs only; it never
 * copies, moves, renames, edits, or deletes source files while indexing.
 */
data class IndexedFile(
    val uri: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
    val category: String,
    val source: String
)

data class TreeScanResult(
    val files: List<IndexedFile>,
    val skippedEntries: Int
)

class FileIndexDatabase(context: Context) :
    SQLiteOpenHelper(context, "fileflow-index.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE indexed_files (
                uri TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                mime_type TEXT,
                size_bytes INTEGER NOT NULL,
                modified_seconds INTEGER NOT NULL,
                category TEXT NOT NULL,
                source TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_indexed_files_category ON indexed_files(category)")
        db.execSQL("CREATE INDEX idx_indexed_files_name ON indexed_files(name)")
        db.execSQL("CREATE TABLE custom_categories (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, parent_id TEXT, sort_order INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE file_category_overrides (uri TEXT PRIMARY KEY NOT NULL, category_id TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_file_category_overrides_category ON file_category_overrides(category_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, parent_id TEXT, sort_order INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS file_category_overrides (uri TEXT PRIMARY KEY NOT NULL, category_id TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_file_category_overrides_category ON file_category_overrides(category_id)")
        }
        if (oldVersion < 3) {
            // Reclassify indexed metadata using semantic filename rules.
            // Collect first, then update after closing the read cursor.
            val rows = mutableListOf<Triple<String, String, String?>>()
            db.query(
                "indexed_files",
                arrayOf("uri", "name", "mime_type"),
                null, null, null, null, null
            ).use { cursor ->
                val uriIndex = cursor.getColumnIndexOrThrow("uri")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val mimeIndex = cursor.getColumnIndexOrThrow("mime_type")
                while (cursor.moveToNext()) {
                    rows += Triple(
                        cursor.getString(uriIndex),
                        cursor.getString(nameIndex),
                        if (cursor.isNull(mimeIndex)) null else cursor.getString(mimeIndex)
                    )
                }
            }
            rows.forEach { (uri, name, mime) ->
                val decodedName = android.net.Uri.decode(name)
                val values = ContentValues().apply {
                    put("name", decodedName)
                    put("category", FileClassifier.classify(decodedName, mime))
                }
                db.update("indexed_files", values, "uri = ?", arrayOf(uri))
            }
        }
    }

    fun replaceSource(source: String, files: List<IndexedFile>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("indexed_files", "source = ?", arrayOf(source))
            files.forEach { file ->
                val values = ContentValues().apply {
                    put("uri", file.uri)
                    put("name", file.name)
                    put("mime_type", file.mimeType)
                    put("size_bytes", file.sizeBytes)
                    put("modified_seconds", file.modifiedSeconds)
                    put("category", file.category)
                    put("source", file.source)
                }
                val insertedRowId = writableDatabase.insertWithOnConflict(
                    "indexed_files", null, values, SQLiteDatabase.CONFLICT_REPLACE
                )
                if (insertedRowId == -1L) {
                    throw SQLiteException("Failed to store indexed file metadata")
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun upsertFiles(files: List<IndexedFile>) {
        if (files.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            files.forEach { file ->
                val values = ContentValues().apply {
                    put("uri", file.uri)
                    put("name", file.name)
                    put("mime_type", file.mimeType)
                    put("size_bytes", file.sizeBytes)
                    put("modified_seconds", file.modifiedSeconds)
                    put("category", file.category)
                    put("source", file.source)
                }
                val row = writableDatabase.insertWithOnConflict(
                    "indexed_files", null, values, SQLiteDatabase.CONFLICT_REPLACE
                )
                if (row == -1L) throw SQLiteException("Failed to update file metadata")
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun deleteFiles(uris: Collection<String>) {
        if (uris.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            uris.forEach { uri ->
                writableDatabase.delete("indexed_files", "uri = ?", arrayOf(uri))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }


    fun filesForCategory(category: String, query: String = ""): List<IndexedFile> {
        val selection: String
        val args: Array<String>
        if (category == "empty") {
            selection = "size_bytes = 0 AND (? = '' OR name LIKE ? OR mime_type LIKE ?)"
            args = arrayOf(query, "%$query%", "%$query%")
        } else {
            selection = "category = ? AND (? = '' OR name LIKE ? OR mime_type LIKE ?)"
            args = arrayOf(category, query, "%$query%", "%$query%")
        }
        return queryFiles("($selection) AND category != 'system'", args)
    }

    fun systemFiles(): List<IndexedFile> = queryFiles("category = 'system'", null)

    fun countByCategory(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        readableDatabase.rawQuery("SELECT category, COUNT(*) FROM indexed_files WHERE category != 'system' GROUP BY category", null).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getInt(1)
        }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM indexed_files WHERE size_bytes = 0 AND category != 'system'", null).use { cursor ->
            if (cursor.moveToFirst()) result["empty"] = cursor.getInt(0)
        }
        return result
    }

    fun customCategories(): List<Triple<String, String, String?>> {
        val result = mutableListOf<Triple<String, String, String?>>()
        readableDatabase.query("custom_categories", arrayOf("id", "name", "parent_id"), null, null, null, null, "sort_order ASC, name COLLATE NOCASE ASC").use { cursor ->
            while (cursor.moveToNext()) result += Triple(cursor.getString(0), cursor.getString(1), if (cursor.isNull(2)) null else cursor.getString(2))
        }
        return result
    }

    fun addCustomCategory(id: String, name: String, parentId: String? = null) {
        val values = ContentValues().apply {
            put("id", id); put("name", name)
            if (parentId == null) putNull("parent_id") else put("parent_id", parentId)
        }
        writableDatabase.insertOrThrow("custom_categories", null, values)
    }

    fun renameCustomCategory(id: String, name: String) {
        writableDatabase.update("custom_categories", ContentValues().apply { put("name", name) }, "id = ?", arrayOf(id))
    }

    fun moveCustomCategory(id: String, parentId: String?) {
        if (parentId == id) return
        var ancestor = parentId
        val visited = mutableSetOf<String>()
        while (ancestor != null && visited.add(ancestor)) {
            if (ancestor == id) return // Prevent moving a category inside one of its descendants.
            ancestor = readableDatabase.query(
                "custom_categories", arrayOf("parent_id"), "id = ?", arrayOf(ancestor),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }
        writableDatabase.update(
            "custom_categories",
            ContentValues().apply { if (parentId == null) putNull("parent_id") else put("parent_id", parentId) },
            "id = ?",
            arrayOf(id)
        )
    }

    fun deleteCustomCategory(id: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("file_category_overrides", "category_id = ?", arrayOf(id))
            writableDatabase.delete("custom_categories", "id = ?", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun assignFileToCustomCategory(uri: String, categoryId: String) {
        writableDatabase.insertWithOnConflict(
            "file_category_overrides", null,
            ContentValues().apply { put("uri", uri); put("category_id", categoryId) },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun filesInCustomCategory(categoryId: String): List<IndexedFile> =
        queryFiles("category != 'system' AND uri IN (SELECT uri FROM file_category_overrides WHERE category_id = ?)", arrayOf(categoryId))

    private fun queryFiles(selection: String?, args: Array<String>?): List<IndexedFile> {
        val result = mutableListOf<IndexedFile>()
        readableDatabase.query("indexed_files", null, selection, args, null, null, "name COLLATE NOCASE ASC").use { cursor ->
            while (cursor.moveToNext()) {
                result += IndexedFile(
                    uri = cursor.getString(cursor.getColumnIndexOrThrow("uri")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                    sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")),
                    modifiedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("modified_seconds")),
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    source = cursor.getString(cursor.getColumnIndexOrThrow("source"))
                )
            }
        }
        return result
    }

    fun allFiles(): List<IndexedFile> {
        val result = mutableListOf<IndexedFile>()
        readableDatabase.query(
            "indexed_files", null, "category != ?", arrayOf("system"), null, null, "name COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += IndexedFile(
                    uri = cursor.getString(cursor.getColumnIndexOrThrow("uri")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                    sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")),
                    modifiedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("modified_seconds")),
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    source = cursor.getString(cursor.getColumnIndexOrThrow("source"))
                )
            }
        }
        return result
    }
}

object AndroidFileIndexer {
    fun requiredMediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasImageAccess(context: Context): Boolean =
        hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ||
            (Build.VERSION.SDK_INT >= 34 &&
                hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) ||
            (Build.VERSION.SDK_INT <= 32 &&
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE))

    fun hasVideoAccess(context: Context): Boolean =
        hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ||
            (Build.VERSION.SDK_INT >= 34 &&
                hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) ||
            (Build.VERSION.SDK_INT <= 32 &&
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE))

    fun hasAudioAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun scanMedia(context: Context): List<IndexedFile> {
        val found = mutableListOf<IndexedFile>()
        if (hasImageAccess(context)) {
            found += queryCollection(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "images", "image")
        }
        if (hasVideoAccess(context)) {
            found += queryCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "videos", "video")
        }
        if (hasAudioAccess(context)) {
            found += queryCollection(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio", "audio")
        }
        return found
    }

    private fun queryCollection(
        context: Context,
        collection: Uri,
        category: String,
        source: String
    ): List<IndexedFile> {
        val rows = mutableListOf<IndexedFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val cursor = context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        ) ?: throw IllegalStateException("تعذر قراءة قائمة ملفات الوسائط من Android")
        cursor.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = android.net.Uri.decode(cursor.getString(nameCol) ?: "بدون اسم")
                val relativePath = if (cursor.isNull(relativePathCol)) "" else cursor.getString(relativePathCol).orEmpty()
                val isSystemFile = SystemFileClassifier.isSystemPath(relativePath, displayName)
                rows += IndexedFile(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    name = displayName,
                    mimeType = if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                    sizeBytes = if (cursor.isNull(sizeCol)) -1L else cursor.getLong(sizeCol),
                    modifiedSeconds = if (cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol),
                    category = if (isSystemFile) "system" else category,
                    source = source
                )
            }
        }
        return rows
    }

    private fun queryDocumentSize(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                    if (column >= 0 && !cursor.isNull(column)) cursor.getLong(column) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun scanPickedTree(context: Context, treeUri: Uri): TreeScanResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return TreeScanResult(emptyList(), skippedEntries = 1)
        val rows = mutableListOf<IndexedFile>()
        var skippedEntries = 0
        val visitedDirectories = mutableSetOf<String>()
        val pending = ArrayDeque<Pair<DocumentFile, String>>()
        pending.addLast(root to (root.name ?: ""))

        // Iterative traversal avoids stack overflow on deeply nested provider trees.
        // A faulty provider or revoked child permission must not stop other folders.
        while (pending.isNotEmpty()) {
            val (directory, parentPath) = pending.removeLast()
            if (!visitedDirectories.add(directory.uri.toString())) continue

            val children = try {
                directory.listFiles()
            } catch (_: SecurityException) {
                skippedEntries++
                continue
            } catch (_: Exception) {
                skippedEntries++
                continue
            }

            for (child in children) {
                try {
                    when {
                        child.isDirectory -> pending.addLast(child to listOf(parentPath, child.name.orEmpty()).filter { it.isNotBlank() }.joinToString("/"))
                        child.isFile -> {
                            val name = child.name?.takeIf { it.isNotBlank() }?.let { android.net.Uri.decode(it) } ?: "بدون اسم"
                            val mime = child.type
                            rows += IndexedFile(
                                uri = child.uri.toString(),
                                name = name,
                                mimeType = mime,
                                sizeBytes = queryDocumentSize(context, child.uri) ?: -1L,
                                modifiedSeconds = (child.lastModified().coerceAtLeast(0L)) / 1000L,
                                category = if (SystemFileClassifier.isSystemPath(parentPath, name)) "system" else FileClassifier.classify(name, mime),
                                source = "saf:$treeUri"
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    skippedEntries++
                } catch (_: Exception) {
                    skippedEntries++
                }
            }
        }
        return TreeScanResult(rows, skippedEntries)
    }
}
