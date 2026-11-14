package com.mohamed.fileflow

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    var status by remember { mutableStateOf("لم يبدأ فحص ملفات الجهاز بعد.") }

    fun saveMediaIndex() {
        scope.launch {
            busy = true
            status = "جارٍ قراءة بيانات الوسائط وحفظ الفهرس محليًا..."
            try {
                val files = withContext(Dispatchers.IO) {
                    AndroidFileIndexer.scanMedia(context)
                }
                withContext(Dispatchers.IO) {
                    val database = FileIndexDatabase(context)
                    if (AndroidFileIndexer.hasImageAccess(context)) {
                        database.replaceSource("image", files.filter { it.source == "image" })
                    }
                    if (AndroidFileIndexer.hasVideoAccess(context)) {
                        database.replaceSource("video", files.filter { it.source == "video" })
                    }
                    if (AndroidFileIndexer.hasAudioAccess(context)) {
                        database.replaceSource("audio", files.filter { it.source == "audio" })
                    }
                    indexedCount = database.allFiles().size
                    database.close()
                }
                status = "اكتمل فحص الوسائط المسموح بها. عدد العناصر المفهرسة: ${files.size}"
            } catch (error: Exception) {
                status = "تعذر إكمال الفهرسة: ${error.localizedMessage ?: "خطأ غير معروف"}"
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) saveMediaIndex()
        else status = "لم تُمنح صلاحيات الوسائط. يمكنك اختيار مجلد يدويًا بدلًا من ذلك."
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(
                    uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                scope.launch {
                    busy = true
                    status = "جارٍ فهرسة المجلد الذي اخترته..."
                    try {
                        val files = withContext(Dispatchers.IO) {
                            AndroidFileIndexer.scanPickedTree(context, uri)
                        }
                        withContext(Dispatchers.IO) {
                            val database = FileIndexDatabase(context)
                            database.replaceSource("saf:${uri}", files)
                            indexedCount = database.allFiles().size
                            database.close()
                        }
                        status = "اكتملت فهرسة المجلد. عدد ملفاته: ${files.size}"
                    } catch (error: Exception) {
                        status = "تعذرت قراءة المجلد: ${error.localizedMessage ?: "خطأ غير معروف"}"
                    } finally {
                        busy = false
                    }
                }
            } catch (error: Exception) {
                status = "تعذر حفظ إذن الوصول للمجلد: ${error.localizedMessage ?: "خطأ"}"
            }
        }
    }

    LaunchedEffect(Unit) {
        indexedCount = withContext(Dispatchers.IO) {
            val database = FileIndexDatabase(context)
            val count = database.allFiles().size
            database.close()
            count
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Rounded.Storage, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ربط ملفات الهاتف", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("$indexedCount", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (busy) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val missing = AndroidFileIndexer.requiredMediaPermissions().filter {
                        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) saveMediaIndex()
                    else permissionLauncher.launch(missing.toTypedArray())
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("منح صلاحيات الوسائط وفهرستها")
            }
            TextButton(
                onClick = { treeLauncher.launch(null) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("اختيار مجلد لفهرسته")
            }
        }
    }
}
