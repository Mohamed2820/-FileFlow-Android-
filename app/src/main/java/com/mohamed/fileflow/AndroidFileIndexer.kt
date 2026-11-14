package com.mohamed.fileflow

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile

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

class FileIndexDatabase(context: Context) :
    SQLiteOpenHelper(context, "fileflow-index.db", null, 1) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versioned migrations will be added here before changing the schema.
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
                writableDatabase.insertWithOnConflict(
                    "indexed_files", null, values, SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun allFiles(): List<IndexedFile> {
        val result = mutableListOf<IndexedFile>()
        readableDatabase.query(
            "indexed_files", null, null, null, null, null, "name COLLATE NOCASE ASC"
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
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                rows += IndexedFile(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    name = cursor.getString(nameCol) ?: "بدون اسم",
                    mimeType = if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                    sizeBytes = if (cursor.isNull(sizeCol)) -1L else cursor.getLong(sizeCol),
                    modifiedSeconds = if (cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol),
                    category = category,
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

    fun scanPickedTree(context: Context, treeUri: Uri): List<IndexedFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val rows = mutableListOf<IndexedFile>()
        val visitedDirectories = mutableSetOf<String>()

        // Document providers are external and may fail on individual folders.
        // Continue indexing the rest of the selected tree instead of aborting.
        fun visit(directory: DocumentFile) {
            val directoryKey = directory.uri.toString()
            if (!visitedDirectories.add(directoryKey)) return

            val children = try {
                directory.listFiles()
            } catch (_: SecurityException) {
                return
            } catch (_: Exception) {
                return
            }

            children.forEach { child ->
                try {
                    when {
                        child.isDirectory -> visit(child)
                        child.isFile -> {
                            val mime = child.type
                            val category = when {
                                mime?.startsWith("image/") == true -> "images"
                                mime?.startsWith("video/") == true -> "videos"
                                mime?.startsWith("audio/") == true -> "audio"
                                else -> "other"
                            }
                            rows += IndexedFile(
                                uri = child.uri.toString(),
                                name = child.name ?: "بدون اسم",
                                mimeType = mime,
                                sizeBytes = queryDocumentSize(context, child.uri) ?: -1L,
                                modifiedSeconds = child.lastModified() / 1000L,
                                category = category,
                                source = "saf:${treeUri}"
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    // A revoked or restricted child must not stop the whole scan.
                } catch (_: Exception) {
                    // Providers can invalidate a document while enumeration is running.
                }
            }
        }

        visit(root)
        return rows
    }
}
