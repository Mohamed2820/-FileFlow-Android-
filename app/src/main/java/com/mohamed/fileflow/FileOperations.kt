package com.mohamed.fileflow

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class FileOperationResult(
    val success: Boolean,
    val file: IndexedFile? = null,
    val message: String
)

/**
 * File mutations are deliberately explicit. Shared-storage path operations are
 * attempted only when Android has granted MANAGE_EXTERNAL_STORAGE; SAF
 * operations use only document URIs for which the user granted access.
 */
object FileOperations {

    /**
     * Android 11+ requires explicit user approval before changing or deleting
     * many MediaStore-owned files. The UI launches these senders and retries
     * the requested operation only after Android reports approval.
     */
    fun mediaWriteRequest(context: Context, file: IndexedFile): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uri = runCatching { Uri.parse(file.uri) }.getOrNull() ?: return null
        if (uri.scheme != "content" || uri.authority != "media") return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }.getOrNull()
    }

    fun mediaDeleteRequest(context: Context, files: Collection<IndexedFile>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = files.mapNotNull { file ->
            runCatching { Uri.parse(file.uri) }.getOrNull()?.takeIf {
                it.scheme == "content" && it.authority == "media"
            }
        }.distinct()
        if (uris.isEmpty()) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }.getOrNull()
    }

    fun mimeType(context: Context, file: IndexedFile): String {
        val uri = runCatching { Uri.parse(file.uri) }.getOrNull()
        val providerType = uri?.let { runCatching { context.contentResolver.getType(it) }.getOrNull() }
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() && it != "*/*" && it != "application/octet-stream" }

        val indexedType = file.mimeType?.substringBefore(';')
            ?.takeIf { it.isNotBlank() && it != "*/*" && it != "application/octet-stream" }
        if (providerType != null) return providerType
        if (indexedType != null) return indexedType

        val extension = file.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "pdf" -> "application/pdf"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "ppt" -> "application/vnd.ms-powerpoint"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "txt", "md", "kt", "kts", "java", "py", "js", "ts", "json", "xml", "html", "css", "csv" -> "text/plain"
                else -> "*/*"
            }
    }

    fun rename(context: Context, file: IndexedFile, newName: String): FileOperationResult {
        val name = newName.trim()
        if (name.isBlank() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\') || name.any { it.code < 32 }
        ) return FileOperationResult(false, message = "اسم الملف غير صالح.")

        return runCatching {
            val uri = Uri.parse(file.uri)

            // MediaStore owns its indexed item identity; ask it to rename first.
            if (uri.scheme == "content" && uri.authority == "media") {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
                val changed = context.contentResolver.update(uri, values, null, null)
                if (changed > 0) {
                    return FileOperationResult(true, file.copy(name = name), "تمت إعادة التسمية.")
                }
            }

            val document = DocumentFile.fromSingleUri(context, uri)
            if (document != null && document.canWrite() && document.renameTo(name)) {
                return FileOperationResult(true, file.copy(uri = document.uri.toString(), name = name), "تمت إعادة التسمية.")
            }

            val pathFile = resolveSharedStorageFile(context, uri)
            if (pathFile != null) {
                val parent = pathFile.parentFile
                    ?: return FileOperationResult(false, message = "تعذر تحديد مجلد الملف.")
                val target = File(parent, name)
                if (target.exists()) return FileOperationResult(false, message = "يوجد ملف آخر بهذا الاسم.")
                if (!pathFile.renameTo(target)) {
                    return FileOperationResult(false, message = "رفض Android إعادة تسمية الملف.")
                }
                return FileOperationResult(true, file.copy(name = name), "تمت إعادة التسمية.")
            }

            FileOperationResult(false, message = "هذا المصدر لا يسمح بإعادة التسمية. أعد اختيار مجلده مع صلاحية الكتابة.")
        }.getOrElse { error ->
            FileOperationResult(false, message = "تعذرت إعادة التسمية: ${error.localizedMessage ?: "خطأ في الوصول"}.")
        }
    }

    fun delete(context: Context, file: IndexedFile): FileOperationResult {
        return runCatching {
            val uri = Uri.parse(file.uri)

            // Prefer deleting through the owning provider so MediaStore metadata
            // is updated together with the underlying file.
            if (uri.scheme == "content") {
                val providerDeleted = runCatching {
                    context.contentResolver.delete(uri, null, null) > 0
                }.getOrDefault(false)
                if (providerDeleted) return FileOperationResult(true, file, "تم حذف الملف.")
            }

            val document = DocumentFile.fromSingleUri(context, uri)
            if (document != null && document.canWrite() && document.delete()) {
                return FileOperationResult(true, file, "تم حذف الملف.")
            }

            val pathFile = resolveSharedStorageFile(context, uri)
            if (pathFile != null && pathFile.delete()) {
                return FileOperationResult(true, file, "تم حذف الملف.")
            }

            FileOperationResult(false, message = "لم يسمح Android بحذف الملف. قد تحتاج إلى إعادة اختيار مجلده بصلاحية الكتابة.")
        }.getOrElse { error ->
            FileOperationResult(false, message = "تعذر حذف الملف: ${error.localizedMessage ?: "خطأ في الوصول"}.")
        }
    }

    fun transferToTree(
        context: Context,
        file: IndexedFile,
        destinationTree: Uri,
        move: Boolean
    ): FileOperationResult {
        var created: DocumentFile? = null
        return try {
            val destination = DocumentFile.fromTreeUri(context, destinationTree)
                ?: return FileOperationResult(false, message = "مجلد الوجهة غير متاح.")
            if (!destination.isDirectory || !destination.canWrite()) {
                return FileOperationResult(false, message = "مجلد الوجهة لا يسمح بالكتابة.")
            }

            val mime = mimeType(context, file)
            val safeName = uniqueName(destination, file.name)
            created = destination.createFile(mime, safeName)
                ?: return FileOperationResult(false, message = "تعذر إنشاء الملف في الوجهة.")

            val sourceUri = Uri.parse(file.uri)
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("تعذر قراءة الملف الأصلي.")
            val output = context.contentResolver.openOutputStream(created.uri, "w")
                ?: throw IllegalStateException("تعذر الكتابة في مجلد الوجهة.")
            input.use { source -> output.use { target -> source.copyTo(target, 64 * 1024) } }

            val copied = file.copy(
                uri = created.uri.toString(),
                name = created.name ?: safeName,
                mimeType = mime,
                source = "saf:$destinationTree"
            )
            if (!move) {
                FileOperationResult(true, copied, "تم نسخ الملف.")
            } else {
                val deleteResult = delete(context, file)
                if (deleteResult.success) {
                    FileOperationResult(true, copied, "تم نقل الملف.")
                } else {
                    created.delete()
                    FileOperationResult(false, message = "تم النسخ لكن تعذر حذف الأصل؛ أُلغيت الوجهة لتجنب اعتبار العملية نقلًا ناجحًا. ${deleteResult.message}")
                }
            }
        } catch (error: Exception) {
            created?.delete()
            FileOperationResult(false, message = "فشلت عملية ${if (move) "النقل" else "النسخ"}: ${error.localizedMessage ?: "خطأ في الوصول"}.")
        }
    }

    fun renameFolder(context: Context, folderTree: Uri, newName: String): FileOperationResult {
        val name = newName.trim()
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
            return FileOperationResult(false, message = "اسم المجلد غير صالح.")
        }
        return runCatching {
            val folder = DocumentFile.fromTreeUri(context, folderTree)
                ?: return FileOperationResult(false, message = "المجلد المحدد غير متاح.")
            if (!folder.isDirectory || !folder.canWrite()) {
                return FileOperationResult(false, message = "المجلد المحدد لا يسمح بإعادة التسمية.")
            }
            if (folder.name == name) return FileOperationResult(true, message = "لم يتغير اسم المجلد.")
            if (!folder.renameTo(name)) {
                FileOperationResult(false, message = "رفض مزود الملفات إعادة تسمية المجلد.")
            } else {
                FileOperationResult(true, message = "تمت إعادة تسمية المجلد إلى $name.")
            }
        }.getOrElse { error ->
            FileOperationResult(false, message = "تعذرت إعادة تسمية المجلد: ${error.localizedMessage ?: "خطأ في الوصول"}.")
        }
    }

    fun createFolder(context: Context, parentTree: Uri, name: String): FileOperationResult {
        val folderName = name.trim()
        if (folderName.isBlank() || folderName == "." || folderName == ".." ||
            folderName.contains('/') || folderName.contains('\\')
        ) return FileOperationResult(false, message = "اسم المجلد غير صالح.")

        return runCatching {
            val parent = DocumentFile.fromTreeUri(context, parentTree)
                ?: return FileOperationResult(false, message = "المجلد الأب غير متاح.")
            if (!parent.isDirectory || !parent.canWrite()) {
                return FileOperationResult(false, message = "المجلد المحدد لا يسمح بإنشاء مجلدات.")
            }
            if (parent.findFile(folderName) != null) {
                return FileOperationResult(false, message = "يوجد مجلد أو ملف بهذا الاسم.")
            }
            val created = parent.createDirectory(folderName)
                ?: return FileOperationResult(false, message = "تعذر إنشاء المجلد.")
            FileOperationResult(true, message = "تم إنشاء المجلد: ${created.name ?: folderName}")
        }.getOrElse { error ->
            FileOperationResult(false, message = "تعذر إنشاء المجلد: ${error.localizedMessage ?: "خطأ في الوصول"}.")
        }
    }

    private fun uniqueName(parent: DocumentFile, requested: String): String {
        if (parent.findFile(requested) == null) return requested
        val dot = requested.lastIndexOf('.')
        val base = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        var index = 1
        while (index < 10000) {
            val candidate = "$base ($index)$extension"
            if (parent.findFile(candidate) == null) return candidate
            index++
        }
        throw IllegalStateException("تعذر إنشاء اسم فريد للملف.")
    }

    private fun resolveSharedStorageFile(context: Context, uri: Uri): File? {
        if (!AllFilesIndexer.hasAccess(context)) return null
        if (uri.scheme == "file") return uri.path?.let(::File)
        if (uri.scheme != "content") return null

        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index < 0 || cursor.isNull(index)) null
                    else cursor.getString(index)?.takeIf(String::isNotBlank)?.let(::File)
                }
            }
        }.getOrNull()?.takeIf { it.exists() && it.isFile }
    }
}
