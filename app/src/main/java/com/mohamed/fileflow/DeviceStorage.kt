package com.mohamed.fileflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import android.provider.MediaStore
import android.content.ContentUris
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale

data class DeviceStorageInfo(val total: Long, val available: Long, val used: Long)

fun readDeviceStorage(context: Context): DeviceStorageInfo {
    // UUID_DEFAULT represents the device's primary internal storage volume.
    // Unlike /data's raw filesystem statistics, StorageStatsManager reports
    // the Android storage volume used by the user's shared files and apps.
    val statsManager = context.getSystemService(StorageStatsManager::class.java)
    if (statsManager != null) {
        try {
            val total = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
            val available = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT).coerceIn(0L, total)
            if (total > 0L) {
                return DeviceStorageInfo(
                    total = total,
                    available = available,
                    used = (total - available).coerceAtLeast(0L)
                )
            }
        } catch (_: Exception) {
            // Fall back to the primary shared-storage filesystem if the
            // platform provider cannot report volume statistics.
        }
    }

    val primaryStorage = Environment.getExternalStorageDirectory()
    val stat = StatFs(primaryStorage.absolutePath)
    val total = stat.totalBytes.coerceAtLeast(0L)
    val available = stat.availableBytes.coerceIn(0L, total)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<DeviceStorageInfo?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshStorage() {
        scope.launch {
            info = withContext(Dispatchers.IO) { readDeviceStorage(context) }
        }
    }

    LaunchedEffect(Unit) { refreshStorage() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStorage()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val current = info
    val progress = if (current != null && current.total > 0L) {
        current.used.toFloat() / current.total.toFloat()
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row {
                Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("مساحة التخزين الداخلي", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("قراءة من Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(14.dp))
            if (current == null) {
                Text("جارٍ قراءة مساحة التخزين...", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    formatBytes(current.used) + " مستخدم من " + formatBytes(current.total),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المتاح للنظام: " + formatBytes(current.available), style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format(Locale.getDefault(), "%.0f%%", progress * 100) + " مستخدم",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "المصدر: إحصاءات وحدة التخزين الداخلية في Android. السعة المعروضة هي سعة وسيط التخزين، وقد تختلف المساحة القابلة للاستخدام عن الرقم المطبوع على الهاتف.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

    fun scan(context: Context): List<IndexedFile> {
        val rows = mutableListOf<IndexedFile>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
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
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val mime = if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol)
                val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() }?.let(android.net.Uri::decode) ?: "بدون اسم"
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
