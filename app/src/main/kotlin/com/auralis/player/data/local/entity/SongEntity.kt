package com.auralis.player.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single track. [documentId] (the SAF stable document id, distinct from
 * [filePath]) is the primary key used for duplicate detection — see
 * `domain.util.TrackIdentity`. [discNumber] defaults to 1 (not null) so
 * that single-disc albums collapse naturally into plain track-number
 * ordering; see `domain.util.TrackOrdering`.
 */
@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = MusicFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genreId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["documentId"], unique = true),
        Index(value = ["folderId"]),
        Index(value = ["artistId"]),
        Index(value = ["albumId"]),
        Index(value = ["genreId"]),
        Index(value = ["titleSortKey"]),
        Index(value = ["contentFingerprint"]),
    ],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // --- Identity / duplicate detection -----------------------------------
    val documentId: String,
    val folderId: Long,
    val filePath: String,
    val displayName: String,
    val fileSize: Long,
    val lastModified: Long,
    /** Optional deep-hash, only populated when "Deep duplicate scan" is on. */
    val contentFingerprint: String? = null,

    // --- Metadata ----------------------------------------------------------
    val title: String,
    val titleSortKey: String,
    val artistId: Long? = null,
    val albumId: Long? = null,
    val genreId: Long? = null,
    val composer: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val totalTracks: Int? = null,
    val discNumber: Int = 1,
    val totalDiscs: Int? = null,
    val durationMs: Long,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val mimeType: String,

    // --- Library bookkeeping -------------------------------------------------
    val dateAdded: Long,
    val dateModified: Long,
    /** false once the scanner can no longer resolve [documentId] on disk. */
    val isAvailable: Boolean = true,

    // --- Favorites (folded in — single-user app, avoids a join) -----------
    val isFavorite: Boolean = false,
    val favoritedAt: Long? = null,

    // --- Fast aggregates (append-only log lives in PlaybackHistoryEntity) --
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,

    /** User-edited or downloaded lyrics, stored in the app and preserved across metadata rescans. */
    val lyrics: String? = null,
)
