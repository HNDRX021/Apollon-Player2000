package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auralis.player.data.local.entity.MusicFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicFolderDao {

    @Query("SELECT * FROM music_folders ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<MusicFolderEntity>>

    @Query("SELECT * FROM music_folders WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MusicFolderEntity?

    @Query("SELECT * FROM music_folders WHERE isEnabled = 1 ORDER BY addedAt ASC")
    suspend fun getEnabled(): List<MusicFolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: MusicFolderEntity): Long

    @Query("UPDATE music_folders SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE music_folders SET treeUri = :treeUri, displayName = :displayName, isEnabled = 1 WHERE id = :id")
    suspend fun updateTreeAccess(id: Long, treeUri: String, displayName: String)

    @Query("UPDATE music_folders SET lastScannedAt = :scannedAt WHERE id = :id")
    suspend fun markScanned(id: Long, scannedAt: Long)

    @Query("DELETE FROM music_folders WHERE id = :id")
    suspend fun delete(id: Long)
}
