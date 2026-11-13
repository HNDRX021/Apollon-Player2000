package com.auralis.player.domain.model

/*
 * Models for the "Edit File" feature: what the user wants changed in an audio file's tags, and what
 * the tags of a file currently contain. The tag fields that are not part of an [Edit] are never
 * touched, so everything else in the file (ReplayGain, lyrics, custom frames, ...) is preserved.
 */

/** One field of an edit: leave it alone, or change it. [Change] with a null/blank value clears the field. */
sealed interface Edit<out T> {
    data object Keep : Edit<Nothing>
    data class Change<T>(val value: T?) : Edit<T>
}

/** What to do with the embedded cover image. [Replace] carries an already normalised JPEG. */
sealed interface ArtworkEdit {
    data object Keep : ArtworkEdit
    data object Remove : ArtworkEdit
    class Replace(val jpegBytes: ByteArray) : ArtworkEdit
}

/** The tag changes for one file. */
data class TagChanges(
    val title: Edit<String> = Edit.Keep,
    val artist: Edit<String> = Edit.Keep,
    val album: Edit<String> = Edit.Keep,
    val albumArtist: Edit<String> = Edit.Keep,
    val composer: Edit<String> = Edit.Keep,
    val genre: Edit<String> = Edit.Keep,
    val comment: Edit<String> = Edit.Keep,
    val year: Edit<Int> = Edit.Keep,
    val trackNumber: Edit<Int> = Edit.Keep,
    val discNumber: Edit<Int> = Edit.Keep,
    val artwork: ArtworkEdit = ArtworkEdit.Keep,
) {
    /** True when applying these changes would not touch the file at all. */
    val isEmpty: Boolean get() = this == TagChanges()
}

/** The tags exactly as stored in a file (blank values are null). */
data class FileTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val composer: String?,
    val genre: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val comment: String?,
)

/** What the editor needs to know about one file when it opens. */
class SongTagSnapshot(
    val songId: Long,
    /** The tags read from the file itself; null when the file could not be read (the editor then shows library values). */
    val tags: FileTags?,
    val embeddedArtwork: ByteArray?,
    /** Null when the file can be edited; otherwise the reason it cannot, in words for the user. */
    val notEditableReason: String?,
    /** Set when reading the tags failed (so the form is showing library values instead of file values). */
    val readProblem: String?,
)

class SongTagEdit(val songId: Long, val changes: TagChanges)

/**
 * A batch of edits. [albumId] is set when the whole album is being edited: renaming the album then
 * renames its existing row (keeping its id, favorite state and cover) instead of creating a new one.
 */
class MetadataEditRequest(val songs: List<SongTagEdit>, val albumId: Long? = null)

class SongEditFailure(val songId: Long, val title: String, val reason: String)

class MetadataEditOutcome(val updatedSongIds: List<Long>, val failures: List<SongEditFailure>) {
    val allSucceeded: Boolean get() = failures.isEmpty()
}
