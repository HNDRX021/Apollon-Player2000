package com.auralis.music.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SongEntity::class, MusicFolderEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        @Volatile private var INSTANCE: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "auralis.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
