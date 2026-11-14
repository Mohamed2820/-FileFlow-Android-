package com.mohamed.fileflow

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Stores metadata only. File bytes stay in their original locations.
 * User state is kept separately so refreshing an index does not erase favourites
 * or the last-opened timestamp.
 */
internal class FileIndexDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE file_entries (
                uri TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL,
                mime_type TEXT,
                size_bytes INTEGER NOT NULL,
                modified_seconds INTEGER NOT NULL,
                relative_path TEXT,
                category_id TEXT NOT NULL,
                source TEXT NOT NULL,
                duration_millis INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_file_entries_category ON file_entries(category_id)")
        db.execSQL("CREATE INDEX index_file_entries_source ON file_entries(source)")
        db.execSQL(
            """
            CREATE TABLE file_user_state (
                uri TEXT PRIMARY KEY NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                last_opened_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first persistent schema. Future migrations must be additive.
    }

    fun replaceSourceEntries(source: String, entries: List<FileEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("file_entries", "source = ?", arrayOf(source))
            entries.forEach { entry ->
                val values = ContentValues().apply {
                    put("uri", entry.uri)
                    put("display_name", entry.displayName)
                    put("mime_type", entry.mimeType)
                    put("size_bytes", entry.sizeBytes)
                    put("modified_seconds", entry.modifiedSeconds)
                    put("relative_path", entry.relativePath)
                    put("category_id", entry.categoryId)
                    put("source", source)
                    put("duration_millis", entry.durationMillis)
                }
                db.insertOrThrow("file_entries", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllEntries(): List<FileEntry> = queryEntries(
        "SELECT f.*, COALESCE(s.is_favorite, 0) AS is_favorite, " +
            "COALESCE(s.last_opened_at, 0) AS last_opened_at " +
            "FROM file_entries f LEFT JOIN file_user_state s ON s.uri = f.uri " +
            "ORDER BY f.display_name COLLATE NOCASE ASC"
    )

    fun getRecentEntries(limit: Int = 100): List<FileEntry> = queryEntries(
        "SELECT f.*, COALESCE(s.is_favorite, 0) AS is_favorite, " +
            "COALESCE(s.last_opened_at, 0) AS last_opened_at " +
            "FROM file_entries f INNER JOIN file_user_state s ON s.uri = f.uri " +
            "WHERE s.last_opened_at > 0 ORDER BY s.last_opened_at DESC LIMIT ?",
        arrayOf(limit.toString())
    )

    fun getFavoriteEntries(): List<FileEntry> = queryEntries(
        "SELECT f.*, COALESCE(s.is_favorite, 0) AS is_favorite, " +
            "COALESCE(s.last_opened_at, 0) AS last_opened_at " +
            "FROM file_entries f INNER JOIN file_user_state s ON s.uri = f.uri " +
            "WHERE s.is_favorite = 1 ORDER BY f.display_name COLLATE NOCASE ASC"
    )

    fun setFavorite(uri: String, favorite: Boolean) {
        val db = writableDatabase
        ensureUserState(db, uri)
        val values = ContentValues().apply { put("is_favorite", if (favorite) 1 else 0) }
        db.update("file_user_state", values, "uri = ?", arrayOf(uri))
    }

    fun markOpened(uri: String, openedAtMillis: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        ensureUserState(db, uri)
        val values = ContentValues().apply { put("last_opened_at", openedAtMillis) }
        db.update("file_user_state", values, "uri = ?", arrayOf(uri))
    }

    private fun ensureUserState(db: SQLiteDatabase, uri: String) {
        val values = ContentValues().apply {
            put("uri", uri)
            put("is_favorite", 0)
            put("last_opened_at", 0L)
        }
        db.insertWithOnConflict("file_user_state", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun queryEntries(sql: String, args: Array<String> = emptyArray()): List<FileEntry> {
        val result = ArrayList<FileEntry>()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            val uriIndex = cursor.getColumnIndexOrThrow("uri")
            val nameIndex = cursor.getColumnIndexOrThrow("display_name")
            val mimeIndex = cursor.getColumnIndexOrThrow("mime_type")
            val sizeIndex = cursor.getColumnIndexOrThrow("size_bytes")
            val modifiedIndex = cursor.getColumnIndexOrThrow("modified_seconds")
            val pathIndex = cursor.getColumnIndexOrThrow("relative_path")
            val categoryIndex = cursor.getColumnIndexOrThrow("category_id")
            val sourceIndex = cursor.getColumnIndexOrThrow("source")
            val durationIndex = cursor.getColumnIndexOrThrow("duration_millis")
            val favoriteIndex = cursor.getColumnIndexOrThrow("is_favorite")
            val openedIndex = cursor.getColumnIndexOrThrow("last_opened_at")

            while (cursor.moveToNext()) {
                result += FileEntry(
                    uri = cursor.getString(uriIndex),
                    displayName = cursor.getString(nameIndex),
                    mimeType = cursor.getString(mimeIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                    modifiedSeconds = cursor.getLong(modifiedIndex),
                    relativePath = cursor.getString(pathIndex),
                    categoryId = cursor.getString(categoryIndex),
                    source = cursor.getString(sourceIndex),
                    durationMillis = if (cursor.isNull(durationIndex)) null else cursor.getLong(durationIndex),
                    isFavorite = cursor.getInt(favoriteIndex) == 1,
                    lastOpenedAt = cursor.getLong(openedIndex)
                )
            }
        }
        return result
    }

    companion object {
        private const val DATABASE_NAME = "fileflow-index.db"
        private const val DATABASE_VERSION = 1
    }
}
