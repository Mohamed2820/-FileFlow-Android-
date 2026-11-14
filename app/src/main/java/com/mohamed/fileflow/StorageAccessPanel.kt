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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageAccessPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var indexedCount by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("لم يبدأ الفحص بعد.") }

    fun scanAndSave() {
        scope.launch {
            busy = true
            status = "جارٍ فحص الملفات وحفظ بياناتها محليًا..."
            try {
                val media = withContext(Dispatchers.IO) { AndroidFileIndexer.scanMedia(context) }
                val allFiles = if (AllFilesIndexer.hasAccess()) withContext(Dispatchers.IO) {
                    AllFilesIndexer.scan(context)
                } else emptyList()
                val count = withContext(Dispatchers.IO) {
                    val db = FileIndexDatabase(context)
                    db.replaceSource("image", media.filter { it.source == "image" })
                    db.replaceSource("video", media.filter { it.source == "video" })
                    db.replaceSource("audio", media.filter { it.source == "audio" })
                    if (AllFilesIndexer.hasAccess()) db.replaceSource("all-files", allFiles)
                    val total = db.allFiles().size
                    db.close()
                    total
                }
                indexedCount = count
                status = "اكتمل الفحص: ${media.size + allFiles.size} ملف تم اكتشافه. الملفات غير الوسائط: ${allFiles.size}."
            } catch (e: Exception) {
                status = "فشل الفحص: ${e.localizedMessage ?: "خطأ غير معروف"}"
            } finally { busy = false }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it } || AndroidFileIndexer.hasImageAccess(context) ||
            AndroidFileIndexer.hasVideoAccess(context) || AndroidFileIndexer.hasAudioAccess(context)) {
            scanAndSave()
        } else status = "لم تُمنح صلاحيات الوسائط. يمكنك اختيار مجلد يدويًا."
    }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                scope.launch {
                    busy = true
                    status = "جارٍ فهرسة المجلد..."
                    try {
                        val files = withContext(Dispatchers.IO) { AndroidFileIndexer.scanPickedTree(context, uri) }
                        val count = withContext(Dispatchers.IO) {
                            val db = FileIndexDatabase(context)
                            db.replaceSource("saf:${uri}", files)
                            val total = db.allFiles().size
                            db.close()
                            total
                        }
                        indexedCount = count
                        status = "اكتملت فهرسة المجلد: ${files.size} ملف."
                    } catch (e: Exception) {
                        status = "تعذرت قراءة المجلد: ${e.localizedMessage ?: "خطأ"}"
                    } finally { busy = false }
                }
            } catch (e: Exception) { status = "تعذر حفظ إذن المجلد: ${e.localizedMessage ?: "خطأ"}" }
        }
    }

    LaunchedEffect(Unit) {
        indexedCount = withContext(Dispatchers.IO) {
            val db = FileIndexDatabase(context)
            val n = db.allFiles().size
            db.close()
            n
        }
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
                    if (Build.VERSION.SDK_INT >= 30 && !AllFilesIndexer.hasAccess()) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        try { context.startActivity(intent) }
                        catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        status = "فعّل السماح بالوصول لإدارة كل الملفات من إعدادات Android، ثم ارجع واضغط فهرسة كل الملفات."
                    } else scanAndSave()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(if (AllFilesIndexer.hasAccess()) "فهرسة كل الملفات" else "السماح بالوصول لكل الملفات")
            }
            OutlinedButton(
                onClick = {
                    val missing = AndroidFileIndexer.requiredMediaPermissions().filter {
                        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) scanAndSave() else permissionLauncher.launch(missing.toTypedArray())
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("تحديث فهرسة الصور والفيديو والصوت") }
            TextButton(onClick = { treeLauncher.launch(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("اختيار مجلد محدد")
            }
            Text(
                "ملاحظة: الوصول الشامل يتطلب موافقة خاصة من Android. قد تظل مجلدات النظام المحمية مثل Android/data غير متاحة.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
