package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auralis.player.data.local.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {

    @Query("SELECT * FROM genres ORDER BY name ASC")
    fun observeAll(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): GenreEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(genre: GenreEntity): Long
}
