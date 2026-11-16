package com.mohamed.fileflow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageAccessPanel(onIndexUpdated: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasBroadAccess by remember { mutableStateOf(AllFilesIndexer.hasAccess(context)) }
    var indexedCount by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("لم يبدأ الفحص بعد.") }

    fun scanAndSave() {
        if (busy) return
        busy = true
        scope.launch {
            status = "جارٍ فحص الملفات وحفظ بياناتها محليًا..."
            try {
                val broadAccessAtScan = AllFilesIndexer.hasAccess(context)
                // The broad-access path walks mounted shared-storage volumes directly.
                val media = if (broadAccessAtScan) emptyList() else withContext(Dispatchers.IO) {
                    AndroidFileIndexer.scanMedia(context)
                }
                val scanResult = if (broadAccessAtScan) {
                    withContext(Dispatchers.IO) { AllFilesIndexer.scan(context) }
                } else {
                    TreeScanResult(emptyList(), 0)
                }
                val allFiles = scanResult.files

                val count = withContext(Dispatchers.IO) {
                    val db = FileIndexDatabase(context)
                    try {
                        if (broadAccessAtScan) {
                            // The full index now includes media too; remove the older
                            // MediaStore collection rows to avoid duplicate cards.
                            if (scanResult.skippedEntries == 0) {
                                db.replaceSource("all-files", allFiles)
                            } else {
                                // Keep previous rows when a protected subtree could not be scanned.
                                db.upsertFiles(allFiles)
                            }
                            db.replaceSource("image", emptyList())
                            db.replaceSource("video", emptyList())
                            db.replaceSource("audio", emptyList())
                        } else {
                            // Never erase a cached category when its permission is denied.
                            if (AndroidFileIndexer.hasImageAccess(context)) {
                                db.replaceSource("image", media.filter { it.source == "image" })
                            }
                            if (AndroidFileIndexer.hasVideoAccess(context)) {
                                db.replaceSource("video", media.filter { it.source == "video" })
                            }
                            if (AndroidFileIndexer.hasAudioAccess(context)) {
                                db.replaceSource("audio", media.filter { it.source == "audio" })
                            }
                        }
                        db.allFiles().size
                    } finally {
                        db.close()
                    }
                }

                indexedCount = count
                hasBroadAccess = AllFilesIndexer.hasAccess(context)
                onIndexUpdated()
                status = if (broadAccessAtScan && scanResult.skippedEntries > 0) {
                    "اكتمل الفحص جزئيًا: تم اكتشاف ${allFiles.size} ملف، وتعذرت قراءة ${scanResult.skippedEntries} مجلد/عنصر. لم تُحذف نتائج الفحص السابق. ${scanResult.issues.firstOrNull()?.reason.orEmpty()} إجمالي السجلات: $count."
                } else {
                    "اكتمل الفحص: ${media.size + allFiles.size} ملف تم اكتشافه. إجمالي الملفات المحفوظة: $count."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = "فشل الفحص: ${error.localizedMessage ?: "خطأ غير معروف"}"
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val anyMediaAccess =
            AndroidFileIndexer.hasImageAccess(context) ||
            AndroidFileIndexer.hasVideoAccess(context) ||
            AndroidFileIndexer.hasAudioAccess(context)
        if (results.values.any { it } || anyMediaAccess) {
            scanAndSave()
        } else {
            status = "لم تُمنح صلاحيات الوسائط. يمكنك اختيار مجلد محدد يدويًا."
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null && !busy) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                busy = true
                scope.launch {
                    status = "جارٍ فهرسة المجلد..."
                    try {
                        val scanResult = withContext(Dispatchers.IO) {
                            AndroidFileIndexer.scanPickedTree(context, treeUri)
                        }
                        val count = withContext(Dispatchers.IO) {
                            val db = FileIndexDatabase(context)
                            try {
                                if (scanResult.skippedEntries == 0) {
                                    db.replaceSource("saf:$treeUri", scanResult.files)
                                }
                                db.allFiles().size
                            } finally {
                                db.close()
                            }
                        }
                        indexedCount = count
                        onIndexUpdated()
                        status = if (scanResult.skippedEntries == 0) {
                            "اكتملت فهرسة المجلد: ${scanResult.files.size} ملف."
                        } else {
                            "الفهرسة جزئية: تم اكتشاف ${scanResult.files.size} ملف، وتعذرت قراءة ${scanResult.skippedEntries} عناصر/مجلدات. ${scanResult.issues.firstOrNull()?.reason.orEmpty()} احتفظت بنتائج الفهرسة السابقة."
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        status = "تعذرت قراءة المجلد: ${error.localizedMessage ?: "خطأ"}"
                    } finally {
                        busy = false
                    }
                }
            } catch (error: Exception) {
                status = "تعذر حفظ إذن المجلد: ${error.localizedMessage ?: "خطأ"}"
            }
        }
    }

    LaunchedEffect(Unit) {
        indexedCount = withContext(Dispatchers.IO) {
            val db = FileIndexDatabase(context)
            try {
                db.allFiles().size
            } finally {
                db.close()
            }
        }
    }

    val latestHasBroadAccess by rememberUpdatedState(hasBroadAccess)
    val latestScan by rememberUpdatedState(::scanAndSave)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = AllFilesIndexer.hasAccess(context)
                val wasGranted = latestHasBroadAccess
                hasBroadAccess = grantedNow
                if (Build.VERSION.SDK_INT >= 30 && grantedNow && !wasGranted) {
                    latestScan()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("الوصول إلى ملفات الهاتف", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("$indexedCount ملف", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 30 && !AllFilesIndexer.hasAccess(context)) {
                        val appSettingsIntent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        ).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try {
                            context.startActivity(appSettingsIntent)
                            status = "فعّل السماح بالوصول لإدارة كل الملفات. سيبدأ الفحص تلقائيًا عند العودة."
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                status = "اختر FileFlow وفعّل الوصول الشامل، ثم ارجع للتطبيق."
                            } catch (error: Exception) {
                                status = "تعذر فتح إعدادات الوصول: ${error.localizedMessage ?: "خطأ"}"
                            }
                        }
                    } else if (Build.VERSION.SDK_INT >= 30 && AllFilesIndexer.hasAccess(context)) {
                        // MANAGE_EXTERNAL_STORAGE already provides the required shared-file
                        // access. Do not request extra photo/video/audio permissions.
                        scanAndSave()
                    } else {
                        val missing = AndroidFileIndexer.requiredMediaPermissions().filter { permission ->
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) scanAndSave()
                        else permissionLauncher.launch(missing.toTypedArray())
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        Build.VERSION.SDK_INT >= 30 && !hasBroadAccess -> "السماح بالوصول لكل الملفات"
                        Build.VERSION.SDK_INT == 29 -> "فهرسة الوسائط واختيار مجلدات"
                        else -> "طلب الصلاحية وفهرسة الملفات"
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 30 && AllFilesIndexer.hasAccess(context)) {
                        scanAndSave()
                    } else {
                        val missing = AndroidFileIndexer.requiredMediaPermissions().filter { permission ->
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) scanAndSave()
                        else permissionLauncher.launch(missing.toTypedArray())
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إعادة فهرسة جميع الملفات")
            }

            TextButton(
                onClick = { treeLauncher.launch(null) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("اختيار مجلد محدد")
            }

            Text(
                "الوصول الشامل يتطلب موافقة خاصة من Android. وقد تظل مجلدات النظام المحمية مثل Android/data غير متاحة.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
