package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "duplicate_fingerprints",
    primaryKeys = ["folderId", "documentId"],
    indices = [Index(value = ["contentHash"])],
)
data class DuplicateFingerprintEntity(
    val folderId: Long,
    val documentId: String,
    val fileSize: Long,
    val lastModified: Long,
    val contentHash: String,
)
