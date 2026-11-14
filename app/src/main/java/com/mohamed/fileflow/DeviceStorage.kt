package com.mohamed.fileflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.content.ContentUris
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class DeviceStorageInfo(val total: Long, val available: Long, val used: Long)

fun readDeviceStorage(): DeviceStorageInfo {
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    val total = stat.totalBytes
    val available = stat.availableBytes
    return DeviceStorageInfo(total, available, (total - available).coerceAtLeast(0L))
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "غير معروف"
    val units = arrayOf("بايت", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

@Composable
fun DeviceStorageCard() {
    val info = remember { readDeviceStorage() }
    val progress = if (info.total > 0) info.used.toFloat() / info.total else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row {
                Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("مساحة الجهاز", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("قراءة مباشرة من Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(14.dp))
            Text(formatBytes(info.used) + " مستخدم من " + formatBytes(info.total), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("المتاح: " + formatBytes(info.available), style = MaterialTheme.typography.bodySmall)
                Text("المستخدم: " + String.format(Locale.getDefault(), "%.0f%%", progress * 100), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

object AllFilesIndexer {
    fun hasAccess(context: Context): Boolean = when {
        android.os.Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        android.os.Build.VERSION.SDK_INT == 29 -> false // Android 10 scoped storage does not grant broad file access.
        else -> ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun safeDisplayName(raw: String?): String {
        val value = raw?.takeIf { it.isNotBlank() } ?: "بدون اسم"
        // Some providers expose percent-encoded UTF-8 names as display names.
        return if (Regex("(%[0-9A-Fa-f]{2}){2,}").containsMatchIn(value)) {
            runCatching { Uri.decode(value) }.getOrDefault(value)
        } else value
    }

    fun scan(context: Context): List<IndexedFile> {
        val rows = mutableListOf<IndexedFile>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val cursor = context.contentResolver.query(
            collection, projection, null, null,
            MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE ASC"
        ) ?: throw IllegalStateException("تعذر قراءة فهرس ملفات التخزين من Android")
        cursor.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val mediaType = cursor.getInt(mediaTypeCol)
                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ||
                    mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ||
                    mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO) continue
                val id = cursor.getLong(idCol)
                val mime = if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol)
                val name = safeDisplayName(cursor.getString(nameCol))
                rows += IndexedFile(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    name = name,
                    mimeType = mime,
                    sizeBytes = if (cursor.isNull(sizeCol)) -1L else cursor.getLong(sizeCol),
                    modifiedSeconds = if (cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol),
                    category = FileClassifier.classify(name, mime),
                    source = "all-files"
                )
            }
        }
        return rows
    }
}
