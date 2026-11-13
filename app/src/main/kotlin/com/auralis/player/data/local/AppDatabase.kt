package com.auralis.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.auralis.player.data.local.dao.AlbumDao
import com.auralis.player.data.local.dao.ArtistDao
import com.auralis.player.data.local.dao.GenreDao
import com.auralis.player.data.local.dao.DuplicateFingerprintDao
import com.auralis.player.data.local.dao.MusicFolderDao
import com.auralis.player.data.local.dao.PlaybackHistoryDao
import com.auralis.player.data.local.dao.PlaylistDao
import com.auralis.player.data.local.dao.SongDao
import com.auralis.player.data.local.entity.AlbumEntity
import com.auralis.player.data.local.entity.ArtistEntity
import com.auralis.player.data.local.entity.GenreEntity
import com.auralis.player.data.local.entity.DuplicateFingerprintEntity
import com.auralis.player.data.local.entity.MusicFolderEntity
import com.auralis.player.data.local.entity.PlaybackHistoryEntity
import com.auralis.player.data.local.entity.PlaylistEntity
import com.auralis.player.data.local.entity.PlaylistSongCrossRef
import com.auralis.player.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        GenreEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        MusicFolderEntity::class,
        PlaybackHistoryEntity::class,
        DuplicateFingerprintEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun genreDao(): GenreDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun musicFolderDao(): MusicFolderDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun duplicateFingerprintDao(): DuplicateFingerprintDao

    companion object {
        const val DATABASE_NAME = "auralis.db"
    }
}
