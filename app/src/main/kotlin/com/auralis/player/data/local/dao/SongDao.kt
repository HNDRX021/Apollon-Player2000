package com.auralis.player.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.auralis.player.data.local.entity.PlaybackHistoryEntity
import com.auralis.player.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/** Slim projection used only for duplicate-detection lookups (Phase 4). */
data class SongFingerprintRow(
    val id: Long,
    val documentId: String,
    val fileSize: Long,
    val lastModified: Long,
    val contentFingerprint: String?,
    val albumId: Long?,
)

/**
 * A song plus its resolved artist/album display names — everything the UI
 * (song lists, mini player, Now Playing) and the playback layer (which
 * needs [SongEntity.documentId] + [SongEntity.folderId] to build a
 * playable content URI) need, in one query instead of N+1 lookups.
 */
data class SongWithMetadata(
    @Embedded val song: SongEntity,
    val artistName: String?,
    val albumTitle: String?,
    val artworkUri: String? = null,
    val genreName: String? = null,
)

/** Aggregate used by the Settings storage section. */
data class SongTotals(
    val count: Int,
    val sizeBytes: Long,
    val durationMs: Long,
)

private const val SONG_WITH_METADATA_SELECT = """
    SELECT songs.*, artists.name AS artistName, albums.title AS albumTitle, albums.artworkUri AS artworkUri,
        genres.name AS genreName FROM songs
    LEFT JOIN artists ON artists.id = songs.artistId
    LEFT JOIN albums ON albums.id = songs.albumId
    LEFT JOIN genres ON genres.id = songs.genreId
"""

/**
 * The same rows and joins as [SONG_WITH_METADATA_SELECT] for the LIVE list queries (all songs, album, artist,
 * search, Home...), except that the lyrics column comes back as NULL instead of being read. These results are
 * mapped straight to `Song`, which has no lyrics, so `songs.*` copied every song's full lyrics text (up to
 * 100 KB each) out of the database on every emission, for the whole library. Spelled out column by column
 * because `songs.*` cannot leave one out: a new `songs` column has to be added here too (Room fails the build
 * if a required one is missing). Lookups that need the full entity keep using [SONG_WITH_METADATA_SELECT].
 */
private const val SONG_LIST_SELECT = """
    SELECT songs.id, songs.documentId, songs.folderId, songs.filePath, songs.displayName, songs.fileSize,
        songs.lastModified, songs.contentFingerprint, songs.title, songs.titleSortKey, songs.artistId,
        songs.albumId, songs.genreId, songs.composer, songs.year, songs.trackNumber, songs.totalTracks,
        songs.discNumber, songs.totalDiscs, songs.durationMs, songs.bitrateKbps, songs.sampleRateHz,
        songs.mimeType, songs.dateAdded, songs.dateModified, songs.isAvailable, songs.isFavorite,
        songs.favoritedAt, songs.playCount, songs.lastPlayedAt, CAST(NULL AS TEXT) AS lyrics,
        artists.name AS artistName, albums.title AS albumTitle, albums.artworkUri AS artworkUri,
        genres.name AS genreName FROM songs
    LEFT JOIN artists ON artists.id = songs.artistId
    LEFT JOIN albums ON albums.id = songs.albumId
    LEFT JOIN genres ON genres.id = songs.genreId
"""

@Dao
interface SongDao {

    // --- Reads used by the Home screen and playback --------------------------

    @Query(
        """
        $SONG_LIST_SELECT
        INNER JOIN playback_history ON playback_history.songId = songs.id
        WHERE songs.isAvailable = 1
        GROUP BY songs.id
        ORDER BY MAX(playback_history.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<SongWithMetadata>>

    @Query(
        "$SONG_LIST_SELECT WHERE songs.isAvailable = 1 ORDER BY songs.dateAdded DESC LIMIT :limit",
    )
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<SongWithMetadata>>

    @Query(
        "$SONG_LIST_SELECT WHERE songs.isAvailable = 1 AND songs.isFavorite = 1 ORDER BY songs.favoritedAt DESC LIMIT :limit",
    )
    fun observeFavorites(limit: Int = 20): Flow<List<SongWithMetadata>>

    @Query(
        "$SONG_LIST_SELECT WHERE songs.isAvailable = 1 AND songs.playCount > 0 " +
            "ORDER BY songs.playCount DESC, songs.lastPlayedAt DESC, songs.titleSortKey ASC LIMIT :limit",
    )
    fun observeMostPlayed(limit: Int = 20): Flow<List<SongWithMetadata>>

    @Query("SELECT isFavorite FROM songs WHERE id = :songId")
    fun observeIsFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT lyrics FROM songs WHERE id = :songId LIMIT 1")
    fun observeLyrics(songId: Long): Flow<String?>

    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :songId")
    suspend fun setLyrics(songId: Long, lyrics: String?)

    @Query("$SONG_WITH_METADATA_SELECT WHERE songs.id = :songId LIMIT 1")
    suspend fun findWithMetadataById(songId: Long): SongWithMetadata?

    @Query(
        "SELECT COUNT(*) AS count, COALESCE(SUM(fileSize), 0) AS sizeBytes, " +
            "COALESCE(SUM(durationMs), 0) AS durationMs FROM songs WHERE isAvailable = 1",
    )
    fun observeTotals(): Flow<SongTotals>

    /** Unordered; the caller restores the wanted order. Used to rebuild a saved queue. */
    @Query("$SONG_WITH_METADATA_SELECT WHERE songs.id IN (:songIds)")
    suspend fun findWithMetadataByIds(songIds: List<Long>): List<SongWithMetadata>

    // --- Reads reserved for later phases (Library / Search / Album screens) --

    @Query("$SONG_LIST_SELECT WHERE songs.isAvailable = 1 ORDER BY songs.titleSortKey ASC")
    fun observeAll(): Flow<List<SongWithMetadata>>

    @Query("$SONG_LIST_SELECT WHERE songs.isAvailable = 1 AND songs.albumId = :albumId ORDER BY songs.discNumber, songs.trackNumber")
    fun observeByAlbum(albumId: Long): Flow<List<SongWithMetadata>>

    /** Songs of an album artist: every available song on an album whose album-artist tag is [artistId]. */
    @Query("$SONG_LIST_SELECT WHERE songs.isAvailable = 1 AND albums.albumArtistId = :artistId ORDER BY songs.titleSortKey ASC")
    fun observeByArtist(artistId: Long): Flow<List<SongWithMetadata>>

    @Query("$SONG_LIST_SELECT WHERE songs.isAvailable = 1 AND songs.genreId = :genreId")
    fun observeByGenre(genreId: Long): Flow<List<SongWithMetadata>>

    /**
     * Matches title, artist, album, composer and genre. [query] must already
     * have its LIKE wildcards (`%`, `_`, `\\`) escaped with a backslash.
     */
    @Query(
        "$SONG_LIST_SELECT " +
            "WHERE songs.isAvailable = 1 AND (" +
            "songs.title LIKE '%' || :query || '%' ESCAPE '\\' OR " +
            "artists.name LIKE '%' || :query || '%' ESCAPE '\\' OR " +
            "albums.title LIKE '%' || :query || '%' ESCAPE '\\' OR " +
            "songs.composer LIKE '%' || :query || '%' ESCAPE '\\' OR " +
            "genres.name LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY songs.titleSortKey ASC LIMIT 200",
    )
    fun search(query: String): Flow<List<SongWithMetadata>>

    // --- Duplicate-detection / import support (Phase 4 scanner) -------------

    @Query("SELECT * FROM songs WHERE documentId = :documentId LIMIT 1")
    suspend fun findByDocumentId(documentId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    suspend fun findEntityById(songId: Long): SongEntity?

    @Query(
        "SELECT id, documentId, fileSize, lastModified, contentFingerprint, albumId FROM songs WHERE documentId IN (:documentIds)",
    )
    suspend fun findFingerprintsByDocumentIds(documentIds: List<String>): List<SongFingerprintRow>

    @Query(
        "SELECT id, documentId, fileSize, lastModified, contentFingerprint, albumId FROM songs WHERE contentFingerprint = :hash LIMIT 1",
    )
    suspend fun findFingerprintByContentHash(hash: String): SongFingerprintRow?

    @Query("SELECT documentId FROM songs WHERE folderId = :folderId AND isAvailable = 1")
    suspend fun getAvailableDocumentIds(folderId: Long): List<String>

    @Query("UPDATE songs SET isAvailable = 0 WHERE folderId = :folderId AND documentId IN (:documentIds)")
    suspend fun markMissingByIds(folderId: Long, documentIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>): List<Long>

    /**
     * Used for a metadata refresh on a file that already has a row
     * (`DuplicateVerdict.UpdateMetadata`). Deliberately a real UPDATE, not
     * [upsert] — REPLACE deletes-then-reinserts on a primary-key conflict,
     * which cascades through the FK and would silently wipe the song's
     * favorite status, playlist memberships, and play history.
     */
    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET isAvailable = 1 WHERE documentId = :documentId")
    suspend fun markAvailable(documentId: String)

    @Query("DELETE FROM songs WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM songs WHERE folderId = :folderId")
    suspend fun countForFolder(folderId: Long): Int

    @Query("SELECT COUNT(*) FROM songs WHERE albumId = :albumId AND isAvailable = 1")
    suspend fun countAvailableInAlbum(albumId: Long): Int

    // --- Mutations used by the Home screen and playback ----------------------

    @Query(
        "UPDATE songs SET isFavorite = :isFavorite, favoritedAt = :favoritedAt WHERE id = :songId",
    )
    suspend fun setFavorite(songId: Long, isFavorite: Boolean, favoritedAt: Long?)

    /** Favorites [songId] only if it is not a favorite yet, so a song that already is one keeps its place in Favorites. Returns 1 when it changed, 0 otherwise. */
    @Query("UPDATE songs SET isFavorite = 1, favoritedAt = :favoritedAt WHERE id = :songId AND isFavorite = 0")
    suspend fun favoriteIfNot(songId: Long, favoritedAt: Long): Int

    /**
     * Favorites every song of [songIds] that is not a favorite yet; the others are left untouched (no
     * duplicates, no reordering). The new ones get descending timestamps in the given order, so Favorites
     * (newest first) lists an album's tracks in track order. Returns how many songs were newly favorited.
     */
    @Transaction
    suspend fun favoriteMissing(songIds: List<Long>, now: Long): Int {
        var added = 0
        for (songId in songIds) {
            if (favoriteIfNot(songId, now - added) > 0) added++
        }
        return added
    }

    @Query(
        """
        UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt
        WHERE id = :songId
        """,
    )
    suspend fun incrementPlayCount(songId: Long, playedAt: Long)

    @Insert
    suspend fun insertPlaybackHistory(entry: PlaybackHistoryEntity): Long

    /**
     * One play = the song's counters plus one history row, written in a single transaction. Done as two
     * separate writes, every played track invalidated the songs table and then the history table one after
     * the other, so every live library query (all songs, Home...) re-ran twice per track change; as one
     * transaction Room notifies observers once, after the commit.
     */
    @Transaction
    suspend fun recordPlay(songId: Long, playedAt: Long) {
        incrementPlayCount(songId, playedAt)
        insertPlaybackHistory(PlaybackHistoryEntity(songId = songId, playedAt = playedAt))
    }
}
