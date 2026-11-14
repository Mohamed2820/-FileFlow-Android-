package com.mohamed.fileflow

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import android.Manifest
import android.database.Cursor
import android.content.ContentUris
import android.provider.BaseColumns
import java.util.ArrayDeque

/**
 * Android storage boundary for FileFlow.
 *
 * Shared media is read through MediaStore and arbitrary documents are read only
 * from folders the user explicitly grants through the Storage Access Framework.
 * No raw shared-storage path traversal or MANAGE_EXTERNAL_STORAGE permission is used.
 */
internal class AndroidFileRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val database = FileIndexDatabase(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun mediaAccessDescription(): String {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            val granted = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            return if (granted) "صلاحية قراءة الوسائط ممنوحة" else "لم تُمنح صلاحية قراءة الوسائط"
        }
        val images = hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
        val audio = hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        val selectedVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val visualLabel = when {
            images && videos -> "الصور والفيديوهات: وصول كامل"
            images -> "الصور: وصول كامل"
            videos -> "الفيديوهات: وصول كامل"
            selectedVisual -> "الصور والفيديوهات: وصول جزئي حسب اختيارك"
            else -> "الصور والفيديوهات: غير مسموح"
        }
        val audioLabel = if (audio) "الصوتيات: مسموح" else "الصوتيات: غير مسموح"
        return "$visualLabel • $audioLabel"
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun canReadImages(): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    private fun canReadVideos(): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return hasPermission(Manifest.permission.READ_MEDIA_VIDEO) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    private fun canReadAudio(): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    /**
     * Persists read access to a user-selected folder. The URI, not a fabricated
     * filesystem path, is retained because SAF providers need not expose paths.
     */
    fun rememberSelectedFolder(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val current = preferences.getStringSet(KEY_TREE_URIS, emptySet()).orEmpty().toMutableSet()
        current.add(uri.toString())
        preferences.edit().putStringSet(KEY_TREE_URIS, current).apply()
    }

    fun refreshIndex(): FileFlowSnapshot {
        val imageEntries = if (canReadImages()) queryMedia(MediaKind.IMAGE) else emptyList()
        val videoEntries = if (canReadVideos()) queryMedia(MediaKind.VIDEO) else emptyList()
        val audioEntries = if (canReadAudio()) queryMedia(MediaKind.AUDIO) else emptyList()

        // Refresh each source independently. This also removes stale cached rows
        // when a permission is revoked or the user's partial selection changes.
        database.replaceSourceEntries(SOURCE_IMAGES, imageEntries)
        database.replaceSourceEntries(SOURCE_VIDEOS, videoEntries)
        database.replaceSourceEntries(SOURCE_AUDIO, audioEntries)

        val treeUris = preferences.getStringSet(KEY_TREE_URIS, emptySet()).orEmpty().toList()
        treeUris.forEach { treeUri ->
            val source = SAF_SOURCE_PREFIX + treeUri
            val entries = try {
                scanSelectedTree(Uri.parse(treeUri), source)
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
            database.replaceSourceEntries(source, entries)
        }

        return FileFlowSnapshot(
            entries = database.getAllEntries(),
            recentEntries = database.getRecentEntries(),
            favoriteEntries = database.getFavoriteEntries(),
            installedApps = loadLaunchableApps(),
            storage = readStorageSnapshot(),
            selectedFolderCount = treeUris.size,
            mediaAccessDescription = mediaAccessDescription()
        )
    }

    fun setFavorite(entry: FileEntry, favorite: Boolean) {
        database.setFavorite(entry.uri, favorite)
    }

    fun openEntry(context: Context, entry: FileEntry): Boolean {
        return try {
            val uri = Uri.parse(entry.uri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, entry.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "فتح الملف").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            database.markOpened(entry.uri)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareEntry(context: Context, entry: FileEntry): Boolean {
        return try {
            val uri = Uri.parse(entry.uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = entry.mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(resolver, entry.displayName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة الملف").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryMedia(kind: MediaKind): List<FileEntry> {
        val entries = ArrayList<FileEntry>()
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getExternalVolumeNames(appContext).toList() }
                .getOrDefault(emptyList())
                .ifEmpty { listOf(MediaStore.VOLUME_EXTERNAL) }
        } else {
            listOf(MediaStore.VOLUME_EXTERNAL)
        }

        volumes.forEach { volume ->
            val collection = when (kind) {
                MediaKind.IMAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(volume) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaKind.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Video.Media.getContentUri(volume) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaKind.AUDIO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Audio.Media.getContentUri(volume) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            try {
                val projection = mutableListOf(
                    BaseColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    projection += MediaStore.MediaColumns.RELATIVE_PATH
                }
                if (kind != MediaKind.IMAGE) {
                    projection += MediaStore.MediaColumns.DURATION
                }

                resolver.query(collection, projection.toTypedArray(), null, null,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
                        val uri = ContentUris.withAppendedId(collection, id)
                        val name = cursor.stringOrNull(MediaStore.MediaColumns.DISPLAY_NAME)
                            ?: "ملف-$id"
                        val mime = cursor.stringOrNull(MediaStore.MediaColumns.MIME_TYPE)
                            ?: resolver.getType(uri)
                        val size = cursor.longOrZero(MediaStore.MediaColumns.SIZE)
                        val modified = cursor.longOrZero(MediaStore.MediaColumns.DATE_MODIFIED)
                        val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.stringOrNull(MediaStore.MediaColumns.RELATIVE_PATH)
                        } else null
                        val duration = if (kind == MediaKind.IMAGE) null
                            else cursor.longOrZero(MediaStore.MediaColumns.DURATION)

                        val category = if (size == 0L) CATEGORY_EMPTY else when (kind) {
                            MediaKind.IMAGE -> CATEGORY_IMAGES
                            MediaKind.VIDEO -> CATEGORY_VIDEOS
                            MediaKind.AUDIO -> CATEGORY_AUDIO
                        }
                        entries += FileEntry(
                            uri = uri.toString(),
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            modifiedSeconds = modified,
                            relativePath = relativePath,
                            categoryId = category,
                            source = kind.source,
                            durationMillis = duration,
                            isFavorite = false,
                            lastOpenedAt = 0L
                        )
                    }
                }
            } catch (_: SecurityException) {
                // A permission can change while the app is running; skip this volume.
            } catch (_: IllegalArgumentException) {
                // A provider may not expose a particular collection on every device.
            }
        }
        return entries
    }

    private fun scanSelectedTree(treeUri: Uri, source: String): List<FileEntry> {
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return emptyList()
        if (!root.canRead()) return emptyList()

        data class Candidate(
            val uri: String,
            val name: String,
            val mime: String?,
            val size: Long,
            val modified: Long,
            val path: String
        )

        data class PendingDirectory(val document: DocumentFile, val path: String, val depth: Int)

        val candidates = ArrayList<Candidate>()
        val projectRoots = HashSet<String>()
        val queue = ArrayDeque<PendingDirectory>()
        val visitedDirectories = HashSet<String>()
        val rootName = root.name?.takeIf { it.isNotBlank() } ?: "مجلد مختار"
        queue.add(PendingDirectory(root, rootName, 0))
        visitedDirectories.add(root.uri.toString())

        val projectMarkerFiles = setOf(
            ".git", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "package.json", "pyproject.toml",
            "cargo.toml", "go.mod", "pom.xml", "composer.json"
        )
        val projectFolderNames = setOf("projects", "project", "مشاريع", "مشروع")
        val studyFolderNames = setOf(
            "study", "studies", "university", "college", "courses",
            "الدراسة", "دراسة", "جامعة", "كلية", "مواد", "محاضرات", "مذكرات"
        )

        var visitedFiles = 0
        while (queue.isNotEmpty() && visitedFiles < MAX_INDEXED_FILES) {
            val current = queue.removeFirst()
            if (current.depth > MAX_DIRECTORY_DEPTH) continue
            val children = try {
                current.document.listFiles()
            } catch (_: SecurityException) {
                emptyArray()
            } catch (_: RuntimeException) {
                emptyArray()
            }

            children.forEach { child ->
                val childName = child.name ?: return@forEach
                val childPath = "${current.path}/$childName"
                if (child.isDirectory) {
                    val uriKey = child.uri.toString()
                    if (childName.lowercase() == ".git") {
                        projectRoots.add(current.path)
                    }
                    if (childName.lowercase() in projectFolderNames) {
                        projectRoots.add(childPath)
                    }
                    if (visitedDirectories.add(uriKey)) {
                        queue.add(PendingDirectory(child, childPath, current.depth + 1))
                    }
                } else if (child.isFile) {
                    visitedFiles++
                    val mime = child.type ?: mimeFromName(childName)
                    val candidate = Candidate(
                        uri = child.uri.toString(),
                        name = childName,
                        mime = mime,
                        size = runCatching { child.length() }.getOrDefault(0L),
                        modified = (runCatching { child.lastModified() }.getOrDefault(0L) / 1000L),
                        path = childPath
                    )
                    candidates += candidate

                    if (childName.lowercase() in projectMarkerFiles) {
                        projectRoots.add(current.path)
                    }
                }
            }
        }

        return candidates.map { file ->
            val lowerPathSegments = file.path.lowercase().split('/')
            val inStudyFolder = lowerPathSegments.any { it in studyFolderNames }
            val inProjectRoot = projectRoots.any { rootPath ->
                file.path == rootPath || file.path.startsWith("$rootPath/")
            } || lowerPathSegments.any { it in projectFolderNames }
            val mime = file.mime
            val category = when {
                file.size == 0L -> CATEGORY_EMPTY
                mime?.startsWith("image/") == true -> CATEGORY_IMAGES
                mime?.startsWith("video/") == true -> CATEGORY_VIDEOS
                mime?.startsWith("audio/") == true -> CATEGORY_AUDIO
                inStudyFolder -> CATEGORY_STUDY
                inProjectRoot -> CATEGORY_PROJECTS
                else -> CATEGORY_OTHER
            }
            FileEntry(
                uri = file.uri,
                displayName = file.name,
                mimeType = mime,
                sizeBytes = file.size,
                modifiedSeconds = file.modified,
                relativePath = file.path,
                categoryId = category,
                source = source,
                durationMillis = null,
                isFavorite = false,
                lastOpenedAt = 0L
            )
        }
    }

    private fun loadLaunchableApps(): List<InstalledApp> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.queryIntentActivities(
                launchIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        return resolveInfos.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            InstalledApp(
                label = resolveInfo.loadLabel(appContext.packageManager).toString(),
                packageName = activityInfo.packageName
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun readStorageSnapshot(): StorageSnapshot {
        val volumePath = appContext.getExternalFilesDir(null)?.absolutePath
            ?: appContext.filesDir.absolutePath
        return try {
            val stat = StatFs(volumePath)
            StorageSnapshot(totalBytes = stat.totalBytes, availableBytes = stat.availableBytes)
        } catch (_: Exception) {
            StorageSnapshot(totalBytes = 0L, availableBytes = 0L)
        }
    }

    private fun mimeFromName(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private enum class MediaKind(val source: String) {
        IMAGE(SOURCE_IMAGES),
        VIDEO(SOURCE_VIDEOS),
        AUDIO(SOURCE_AUDIO)
    }

    companion object {
        private const val PREFERENCES_NAME = "fileflow-storage"
        private const val KEY_TREE_URIS = "selected_tree_uris"
        private const val SOURCE_IMAGES = "media:images"
        private const val SOURCE_VIDEOS = "media:videos"
        private const val SOURCE_AUDIO = "media:audio"
        private const val SAF_SOURCE_PREFIX = "saf:"
        private const val MAX_DIRECTORY_DEPTH = 40
        private const val MAX_INDEXED_FILES = 100_000

        private const val CATEGORY_STUDY = "study"
        private const val CATEGORY_IMAGES = "images"
        private const val CATEGORY_VIDEOS = "videos"
        private const val CATEGORY_AUDIO = "audio"
        private const val CATEGORY_PROJECTS = "projects"
        private const val CATEGORY_EMPTY = "empty"
        private const val CATEGORY_OTHER = "other"
    }
}
