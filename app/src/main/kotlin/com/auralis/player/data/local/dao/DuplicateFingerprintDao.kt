package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auralis.player.data.local.entity.DuplicateFingerprintEntity

@Dao
interface DuplicateFingerprintDao {
    @Query("SELECT * FROM duplicate_fingerprints WHERE folderId = :folderId AND documentId = :documentId LIMIT 1")
    suspend fun find(folderId: Long, documentId: String): DuplicateFingerprintEntity?

    @Query("SELECT * FROM duplicate_fingerprints WHERE contentHash = :contentHash LIMIT 1")
    suspend fun findByHash(contentHash: String): DuplicateFingerprintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fingerprint: DuplicateFingerprintEntity)

    @Query("DELETE FROM duplicate_fingerprints WHERE folderId = :folderId")
    suspend fun deleteForFolder(folderId: Long)
}
