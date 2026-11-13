package com.auralis.player.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.auralis.player.data.local.AppDatabase
import com.auralis.player.data.local.dao.DuplicateFingerprintDao
import com.auralis.player.data.local.dao.AlbumDao
import com.auralis.player.data.local.dao.ArtistDao
import com.auralis.player.data.local.dao.GenreDao
import com.auralis.player.data.local.dao.MusicFolderDao
import com.auralis.player.data.local.dao.PlaybackHistoryDao
import com.auralis.player.data.local.dao.PlaylistDao
import com.auralis.player.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS duplicate_fingerprints (folderId INTEGER NOT NULL, documentId TEXT NOT NULL, fileSize INTEGER NOT NULL, lastModified INTEGER NOT NULL, contentHash TEXT NOT NULL, PRIMARY KEY(folderId, documentId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_duplicate_fingerprints_contentHash ON duplicate_fingerprints(contentHash)")
        }
    }
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE albums ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE artists ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        }
    }

    // Keep a real migration; never discard a user's library on schema updates.
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE albums ADD COLUMN releaseDate TEXT")
            // Album years used to be left empty; fill them from the songs already stored.
            db.execSQL(
                "UPDATE albums SET year = (SELECT MIN(songs.year) FROM songs " +
                    "WHERE songs.albumId = albums.id AND songs.year IS NOT NULL)",
            )
            // Full release dates are filled in by the next library scan (see LibraryScanner).
        }
    }

    // Keep a real migration; never discard a user's library on schema updates.
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN customArtworkUri TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT")
        }
    }

    // Keep a real migration; never discard a user's library on schema updates.
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides
    fun provideArtistDao(db: AppDatabase): ArtistDao = db.artistDao()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideGenreDao(db: AppDatabase): GenreDao = db.genreDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideMusicFolderDao(db: AppDatabase): MusicFolderDao = db.musicFolderDao()

    @Provides
    fun providePlaybackHistoryDao(db: AppDatabase): PlaybackHistoryDao = db.playbackHistoryDao()

    @Provides
    fun provideDuplicateFingerprintDao(db: AppDatabase): DuplicateFingerprintDao = db.duplicateFingerprintDao()
}
