package com.auralis.music.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["album", "albumArtist"]),
        Index(value = ["artist"]),
        Index(value = ["genre"]),
        Index(value = ["folderId"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["playCount"])
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val folderId: Long? = null,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val composer: String,
    val genre: String,
    val year: Int?,
    val trackNumber: Int?,
    val totalTracks: Int?,
    val discNumber: Int?,
    val totalDiscs: Int?,
    val durationMs: Long,
    val bitrate: Int?,
    val sampleRate: Int?,
    val mimeType: String,
    val fileName: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val artworkPath: String?,
    val fingerprint: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val lastPositionMs: Long = 0,
    val playCount: Long = 0,
    val isFavorite: Boolean = false
)

@Entity(tableName = "music_folders", indices = [Index(value = ["treeUri"], unique = true)])
data class MusicFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val recursive: Boolean = true,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index(value = ["playlistId", "position"], unique = true)]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int
)
