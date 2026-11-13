package com.auralis.player.domain.repository

import android.net.Uri
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.AlbumRecentPlays
import com.auralis.player.domain.model.Artist
import com.auralis.player.domain.model.LibraryStats
import com.auralis.player.domain.model.MusicFolder
import com.auralis.player.domain.model.Playlist
import com.auralis.player.domain.model.ScanOutcome
import com.auralis.player.domain.model.ScanProgress
import com.auralis.player.domain.model.PlaylistEntry
import com.auralis.player.domain.model.MetadataEditOutcome
import com.auralis.player.domain.model.MetadataEditRequest
import com.auralis.player.domain.model.SongTagSnapshot
import com.auralis.player.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deliberately trimmed to what the Home, Now Playing, and Settings screens
 * actually call. Playlist/Album/Artist browse screens get their own
 * methods added in the phase that builds their UI.
 */
interface MusicRepository {
    /** Live library-scan state; scans run in an app-level scope and survive screen changes. */
    val scanProgress: StateFlow<ScanProgress>

    /** What the most recent finished scan run found (new songs, unreadable folders); null before the first. */
    val scanOutcome: StateFlow<ScanOutcome?>

    fun observeRecentlyPlayedSongs(limit: Int = 20): Flow<List<Song>>
    fun observeRecentlyAddedSongs(limit: Int = 20): Flow<List<Song>>
    fun observeFavoriteSongs(limit: Int = 20): Flow<List<Song>>
    fun observeMostPlayedSongs(limit: Int = 20): Flow<List<Song>>

    suspend fun setFavorite(songId: Long, isFavorite: Boolean)
    suspend fun setAlbumFavorite(albumId: Long, isFavorite: Boolean)
    suspend fun setArtistFavorite(artistId: Long, isFavorite: Boolean)

    /**
     * Registers a newly SAF-picked folder and immediately scans it.
     * @param treeUri the persisted (`content://...tree/...`) URI string —
     *   the caller is responsible for having already taken a persistable
     *   permission grant on it.
     */
    suspend fun addFolderAndScan(treeUri: String, displayName: String)

    /**
     * Resolves the playable SAF content URI for a song, by looking up its
     * folder's persisted tree URI and combining it with the song's own
     * [Song.documentId]. Returns null if the owning folder no longer
     * exists (e.g. the user removed it after this song was queued).
     */
    suspend fun resolvePlayableUri(song: Song): Uri?

    suspend fun resolveParentFolderUri(song: Song): Uri?
    suspend fun refreshSongMetadata(songId: Long): Boolean

    /** Resolves songs by id, returned in the order of [ids]; ids that no longer exist are dropped. */
    suspend fun getSongsByIds(ids: List<Long>): List<Song>

    /** Records a play: bumps the song's play count and appends to history. */
    suspend fun recordPlayed(songId: Long)

    // --- Library browse + search --------------------------------------------

    fun observeAllSongs(): Flow<List<Song>>
    fun observeAlbums(): Flow<List<Album>>
    fun observeRecentAlbums(limit: Int): Flow<List<Album>>

    /**
     * Per album, how many plays (and on how many different local days) the history holds since
     * [sinceMillis]. Albums with no plays in that window are absent. Feeds Home's "Your Favorites".
     */
    fun observeAlbumRecentPlays(sinceMillis: Long): Flow<List<AlbumRecentPlays>>

    /** Ids of songs that sit in at least one playlist (each once, however many playlists hold it). */
    fun observeSavedSongIds(): Flow<List<Long>>
    fun observeLibraryStats(): Flow<LibraryStats>
    fun observeArtists(): Flow<List<Artist>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun searchAlbums(query: String): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>

    fun observeIsFavorite(songId: Long): Flow<Boolean>
    fun observeLyrics(songId: Long): Flow<String?>
    suspend fun setLyrics(songId: Long, lyrics: String?)
    fun observeAlbum(albumId: Long): Flow<Album?>
    fun observeAlbumSongs(albumId: Long): Flow<List<Song>>
    fun observeArtist(artistId: Long): Flow<Artist?>
    fun observeArtistSongs(artistId: Long): Flow<List<Song>>

    /** Albums whose album-artist tag is [artistId], oldest release first (undated albums last). */
    fun observeArtistAlbums(artistId: Long): Flow<List<Album>>

    /** The album-artist id stored for [albumId], or null when the album or its artist is unknown. */
    suspend fun findAlbumArtistId(albumId: Long): Long?

    // --- Playlists -----------------------------------------------------------

    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylist(playlistId: Long): Flow<Playlist?>
    fun observePlaylistEntries(playlistId: Long): Flow<List<PlaylistEntry>>
    /** Creates an empty playlist and returns its id, or -1 when [name] is blank and nothing was created. */
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long)

    /**
     * Appends [songIds] (in order) to the end of the playlist, skipping songs it already holds and repeats
     * inside [songIds]; existing entries and their order are untouched. Returns how many were added.
     */
    suspend fun addSongsToPlaylistSkippingDuplicates(playlistId: Long, songIds: List<Long>): Int

    /** Favorites every song of [songIds] that is not one yet (the others are left alone). Returns how many were newly favorited. */
    suspend fun favoriteSongs(songIds: List<Long>): Int
    suspend fun removePlaylistEntry(entryId: Long)

    /** Persists a new order for a playlist; [orderedEntryIds] are entry ids (not song ids), first to last. */
    suspend fun reorderPlaylist(playlistId: Long, orderedEntryIds: List<Long>)

    // --- Folder management (Settings screen) --------------------------------

    fun observeFolders(): Flow<List<MusicFolder>>
    suspend fun setFolderEnabled(folderId: Long, isEnabled: Boolean)
    suspend fun removeFolder(folderId: Long)
    suspend fun rescanFolder(folderId: Long)

    /**
     * The manual Refresh: scans every enabled folder in one run, picking up new files and leaving
     * existing songs untouched. Returns false (and does nothing) when there is no enabled folder.
     */
    suspend fun refreshLibrary(): Boolean
    fun cancelRefresh()
    suspend fun renewFolderAccess(folderId: Long, treeUri: String, displayName: String)
    fun cancelFolderScan(folderId: Long)

    // --- Edit File (metadata editor) ----------------------------------------------

    /** The tags inside the song's file plus whether it can be edited; null when the song no longer exists. */
    suspend fun readSongTags(songId: Long): SongTagSnapshot?

    /** Writes the edits into the audio files, then updates the library. Never touches files it was not asked to. */
    suspend fun applyMetadataEdits(request: MetadataEditRequest): MetadataEditOutcome

    /** Every genre name in the library, A to Z, for the genre picker. */
    fun observeGenreNames(): Flow<List<String>>

    /**
     * Sets the playlist's own cover from [jpeg] (already cropped), or removes it with null so the automatic
     * cover is used again. Returns false when [jpeg] could not be stored.
     */
    suspend fun setPlaylistArtwork(playlistId: Long, jpeg: ByteArray?): Boolean
}
