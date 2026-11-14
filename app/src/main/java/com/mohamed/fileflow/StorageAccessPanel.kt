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
fun StorageAccessPanel() {
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
                // With broad access, scan MediaStore.Files once so photos/videos/audio
                // are not omitted when their separate granular permissions are absent.
                val media = if (broadAccessAtScan) emptyList() else withContext(Dispatchers.IO) {
                    AndroidFileIndexer.scanMedia(context)
                }
                val allFiles = if (broadAccessAtScan) {
                    withContext(Dispatchers.IO) { AllFilesIndexer.scan(context) }
                } else {
                    emptyList()
                }

                val count = withContext(Dispatchers.IO) {
                    val db = FileIndexDatabase(context)
                    try {
                        if (broadAccessAtScan) {
                            // The full index now includes media too; remove the older
                            // MediaStore collection rows to avoid duplicate cards.
                            db.replaceSource("all-files", allFiles)
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
                status = "اكتمل الفحص: ${media.size + allFiles.size} ملف في الفهرس. عدد الملفات المحفوظة في قاعدة البيانات: $count."
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
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                busy = true
                scope.launch {
                    status = "جارٍ فهرسة المجلد..."
                    try {
                        val files = withContext(Dispatchers.IO) {
                            AndroidFileIndexer.scanPickedTree(context, treeUri)
                        }
                        val count = withContext(Dispatchers.IO) {
                            val db = FileIndexDatabase(context)
                            try {
                                db.replaceSource("saf:$treeUri", files)
                                db.allFiles().size
                            } finally {
                                db.close()
                            }
                        }
                        indexedCount = count
                        status = "اكتملت فهرسة المجلد: ${files.size} ملف."
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
                    } else {
                        val missing = AndroidFileIndexer.requiredMediaPermissions().filter { permission ->
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            scanAndSave()
                        } else {
                            permissionLauncher.launch(missing.toTypedArray())
                        }
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
                    val missing = AndroidFileIndexer.requiredMediaPermissions().filter { permission ->
                        ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) scanAndSave() else permissionLauncher.launch(missing.toTypedArray())
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تحديث فهرسة الصور والفيديو والصوت")
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
