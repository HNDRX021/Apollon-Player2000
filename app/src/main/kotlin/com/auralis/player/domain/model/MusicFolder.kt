package com.auralis.player.domain.model

import com.auralis.player.data.local.entity.MusicFolderEntity

data class MusicFolder(
    val id: Long,
    val displayName: String,
    val isRecursive: Boolean,
    val isEnabled: Boolean,
    val lastScannedAt: Long?,
)

fun MusicFolderEntity.toDomain(): MusicFolder = MusicFolder(
    id = id,
    displayName = displayName,
    isRecursive = isRecursive,
    isEnabled = isEnabled,
    lastScannedAt = lastScannedAt,
)
