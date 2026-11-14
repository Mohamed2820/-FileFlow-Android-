package com.mohamed.fileflow

data class FileEntry(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
    val relativePath: String?,
    val categoryId: String,
    val source: String,
    val durationMillis: Long?,
    val isFavorite: Boolean,
    val lastOpenedAt: Long
)

data class InstalledApp(
    val label: String,
    val packageName: String
)

data class StorageSnapshot(
    val totalBytes: Long,
    val availableBytes: Long
) {
    val usedBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)
}

data class FileFlowSnapshot(
    val entries: List<FileEntry>,
    val recentEntries: List<FileEntry>,
    val favoriteEntries: List<FileEntry>,
    val installedApps: List<InstalledApp>,
    val storage: StorageSnapshot,
    val selectedFolderCount: Int,
    val mediaAccessDescription: String
)
