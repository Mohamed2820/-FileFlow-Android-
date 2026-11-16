package com.mohamed.fileflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import android.webkit.MimeTypeMap
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
import java.io.File
import java.util.ArrayDeque

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
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT == 29 -> false
        else -> ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Enumerates mounted shared-storage roots directly rather than relying on
     * MediaStore's media/document index. This includes ordinary files and
     * removable volumes when Android exposes their mounted directories.
     */
    fun scan(context: Context): TreeScanResult {
        if (!hasAccess(context)) {
            return TreeScanResult(
                emptyList(),
                1,
                listOf(ScanIssue("storage", "لم يمنح Android صلاحية الوصول الشامل إلى الملفات."))
            )
        }

        val issues = mutableListOf<ScanIssue>()
        val rows = mutableListOf<IndexedFile>()
        var skipped = 0
        val visitedDirectories = HashSet<String>()
        val visitedFiles = HashSet<String>()
        val pending = ArrayDeque<Pair<File, String>>()
        val roots = linkedMapOf<String, File>()

        fun addRoot(file: File?) {
            if (file == null) return
            try {
                if (file.exists() && file.isDirectory) {
                    roots[file.canonicalPath] = file
                }
            } catch (error: Exception) {
                skipped++
                issues += ScanIssue(file.absolutePath,
                    "تعذر الوصول إلى وحدة التخزين: ${error.localizedMessage ?: error.javaClass.simpleName}")
            }
        }

        // Always include the primary shared storage root.
        addRoot(Environment.getExternalStorageDirectory())

        // Android 11+ exposes the mounted storage-volume directories. This
        // discovers removable SD-card roots without assuming a fixed UUID path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val manager = context.getSystemService(StorageManager::class.java)
                manager?.storageVolumes?.forEach { volume ->
                    val state = volume.state
                    if (state == Environment.MEDIA_MOUNTED ||
                        state == Environment.MEDIA_MOUNTED_READ_ONLY) {
                        addRoot(volume.directory)
                    }
                }
            } catch (error: Exception) {
                skipped++
                issues += ScanIssue("/storage",
                    "تعذر تعداد وحدات التخزين الخارجية: ${error.localizedMessage ?: error.javaClass.simpleName}")
            }
        }

        roots.values.forEach { root -> pending.addLast(root to "") }

        val hiddenOrTemporary: (File) -> Boolean = { file ->
            val name = file.name
            name.startsWith(".") || name.startsWith("~$") ||
                name.endsWith(".tmp", ignoreCase = true)
        }

        while (pending.isNotEmpty()) {
            val (directory, relativePath) = pending.removeLast()
            val canonicalDirectory = try {
                directory.canonicalPath
            } catch (error: Exception) {
                skipped++
                issues += ScanIssue(directory.absolutePath,
                    "تعذر حل المسار الحقيقي للمجلد: ${error.localizedMessage ?: error.javaClass.simpleName}")
                continue
            }
            if (!visitedDirectories.add(canonicalDirectory)) continue

            var listFailure: String? = null
            val children = try {
                directory.listFiles()
            } catch (error: SecurityException) {
                listFailure = "الوصول إلى المجلد مرفوض من Android: ${error.localizedMessage ?: error.javaClass.simpleName}"
                null
            } catch (error: Exception) {
                listFailure = "فشل قراءة المجلد: ${error.localizedMessage ?: error.javaClass.simpleName}"
                null
            }

            if (children == null) {
                skipped++
                issues += ScanIssue(
                    directory.absolutePath,
                    listFailure ?: "تعذر تعداد محتويات المجلد (listFiles أعاد null أو لا توجد صلاحية)."
                )
                continue
            }

            for (child in children) {
                try {
                    if (hiddenOrTemporary(child)) continue
                    val name = child.name
                    if (child.isDirectory) {
                        val childRelative = if (relativePath.isBlank()) name else "$relativePath/$name"
                        val normalizedParent = relativePath.replace('\\', '/').trimEnd('/')
                        val isAndroidProtected = normalizedParent.equals("Android", ignoreCase = true) &&
                            (name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true))
                        if (isAndroidProtected) {
                            skipped++
                            issues += ScanIssue(child.absolutePath,
                                "مجلد محمي بواسطة Android (Android/data أو Android/obb)، لا يمكن فهرسته.")
                        } else {
                            pending.addLast(child to childRelative)
                        }
                        continue
                    }
                    if (!child.isFile) continue

                    val canonicalFile = try {
                        child.canonicalPath
                    } catch (error: Exception) {
                        skipped++
                        issues += ScanIssue(child.absolutePath,
                            "تعذر تحديد هوية الملف: ${error.localizedMessage ?: error.javaClass.simpleName}")
                        continue
                    }
                    if (!visitedFiles.add(canonicalFile)) continue

                    val size = try { child.length().coerceAtLeast(0L) } catch (_: Exception) { -1L }
                    val modified = try { child.lastModified().coerceAtLeast(0L) / 1000L } catch (_: Exception) { 0L }
                    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    val uri = Uri.fromFile(child)
                    val extracted = FileContentReader.extract(context, uri, name, size)
                    if (extracted.error != null) {
                        issues += ScanIssue(child.absolutePath, extracted.error)
                    }

                    val classification = FileClassifier.classifyDetailed(
                        name = name,
                        mimeType = mime,
                        parentPath = relativePath,
                        extractedText = extracted.text
                    )
                    rows += IndexedFile(
                        uri = uri.toString(),
                        name = name,
                        mimeType = mime,
                        sizeBytes = size,
                        modifiedSeconds = modified,
                        category = classification.mainCategory,
                        source = "all-files",
                        subCategory = classification.subCategory,
                        confidence = classification.confidence
                    )
                } catch (error: SecurityException) {
                    skipped++
                    issues += ScanIssue(child.absolutePath,
                        "صلاحية مرفوضة: ${error.localizedMessage ?: error.javaClass.simpleName}")
                } catch (error: Exception) {
                    skipped++
                    issues += ScanIssue(child.absolutePath,
                        "تعذر فهرسة الملف: ${error.localizedMessage ?: error.javaClass.simpleName}")
                }
            }
        }

        return TreeScanResult(rows, skipped, issues)
    }
}
