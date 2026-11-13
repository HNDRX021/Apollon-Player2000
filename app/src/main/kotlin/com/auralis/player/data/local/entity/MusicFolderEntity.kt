package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-added Storage Access Framework folder that the scanner (Phase 4)
 * will walk. [treeUri] is the persisted SAF tree URI string
 * (`content://...tree/...`), which survives across app restarts as long as
 * [androidx.documentfile] persistable URI permissions are taken.
 */
@Entity(
    tableName = "music_folders",
    indices = [Index(value = ["treeUri"], unique = true)],
)
data class MusicFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val isRecursive: Boolean = true,
    val isEnabled: Boolean = true,
    val addedAt: Long,
    val lastScannedAt: Long? = null,
)
