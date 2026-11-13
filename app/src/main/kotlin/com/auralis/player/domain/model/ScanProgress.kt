package com.auralis.player.domain.model

/** Live state of the library scanner, observed by Home and Settings. */
data class ScanProgress(
    val isScanning: Boolean = false,
    val processed: Int = 0,
    val currentName: String? = null,
    val folderId: Long? = null,
    /** True if the most recent scan could not read a folder (e.g. access was revoked). */
    val lastScanFailed: Boolean = false,
    val failedFolderId: Long? = null,
)

/**
 * What one finished scan run found. Published once per run (a manual refresh covers all enabled
 * folders in a single run), so the UI can say "found 3 new songs" or "nothing new" when it ends.
 * A cancelled run publishes nothing.
 */
data class ScanOutcome(
    /** Changes for every run, so two identical results are still two distinct events. */
    val id: Long,
    val newSongs: Int,
    val updatedSongs: Int,
    val foldersScanned: Int,
    /** Folders that could not be read (missing, or access was revoked). */
    val failedFolderIds: List<Long>,
)
