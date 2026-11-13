package com.auralis.player.ui.screens.edit

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.player.data.artwork.ArtworkStore
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.ArtworkEdit
import com.auralis.player.domain.model.Edit
import com.auralis.player.domain.model.FileTags
import com.auralis.player.domain.model.MetadataEditRequest
import com.auralis.player.domain.model.Song
import com.auralis.player.domain.model.SongTagEdit
import com.auralis.player.domain.model.TagChanges
import com.auralis.player.domain.repository.MusicRepository
import com.auralis.player.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class EditField { Title, Artist, Album, Year, TrackNumber, Genre, Comment, AlbumArtist, Composer, DiscNumber }

sealed interface EditTarget {
    data class OfSong(val songId: Long) : EditTarget
    data class OfAlbum(val albumId: Long) : EditTarget
}

/**
 * One text field of the form. [original] is what the file contained. A [mixed] field (an album whose
 * tracks disagree) shows "Multiple Values" and counts as changed only once the user types a replacement.
 */
data class FieldState(val original: String = "", val text: String = "", val mixed: Boolean = false) {
    val changed: Boolean get() = if (mixed) text.isNotEmpty() else text.trim() != original.trim()
    val value: String? get() = text.trim().ifEmpty { null }
}

data class TrackEdit(
    val songId: Long,
    val fileName: String,
    val editable: Boolean,
    val note: String?,
    val title: FieldState,
    val artist: FieldState,
    val trackNumber: FieldState,
    val discNumber: FieldState,
) {
    val anyChanged: Boolean get() = title.changed || artist.changed || trackNumber.changed || discNumber.changed

    fun withText(field: EditField, text: String): TrackEdit = when (field) {
        EditField.Title -> copy(title = title.copy(text = text))
        EditField.Artist -> copy(artist = artist.copy(text = text))
        EditField.TrackNumber -> copy(trackNumber = trackNumber.copy(text = text))
        EditField.DiscNumber -> copy(discNumber = discNumber.copy(text = text))
        else -> this
    }
}

sealed interface ArtworkChoice {
    data object Unchanged : ArtworkChoice
    data object Removed : ArtworkChoice
    class Replaced(val jpeg: ByteArray) : ArtworkChoice
}

class SaveConfirmation(
    val trackCount: Int,
    val skippedTracks: Int,
    val overwrittenFields: List<String>,
    val removesArtwork: Boolean,
    val playingWarning: Boolean,
)

data class EditUiState(
    val target: EditTarget? = null,
    val loading: Boolean = false,
    val loadError: String? = null,
    val isAlbum: Boolean = false,
    val heading: String = "",
    val fields: Map<EditField, FieldState> = emptyMap(),
    val tracks: List<TrackEdit> = emptyList(),
    val directory: String = "",
    val artworkPath: String? = null,
    val hadArtwork: Boolean = false,
    val artworkEdit: ArtworkChoice = ArtworkChoice.Unchanged,
    val editable: Boolean = true,
    val notes: List<String> = emptyList(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val confirm: SaveConfirmation? = null,
    val askDiscard: Boolean = false,
) {
    val isDirty: Boolean
        get() = fields.values.any { it.changed } || tracks.any { it.anyChanged } || artworkEdit != ArtworkChoice.Unchanged

    val hasErrors: Boolean
        get() = fields.any { (field, state) -> validateField(field, state) != null } ||
            tracks.any { t ->
                validateField(EditField.Title, t.title) != null ||
                    validateField(EditField.TrackNumber, t.trackNumber) != null ||
                    validateField(EditField.DiscNumber, t.discNumber) != null
            }

    val canSave: Boolean get() = !loading && loadError == null && !saving && editable && isDirty && !hasErrors

    val showsArtwork: Boolean
        get() = when (artworkEdit) {
            ArtworkChoice.Unchanged -> artworkPath != null
            ArtworkChoice.Removed -> false
            is ArtworkChoice.Replaced -> true
        }

    /** "Restore Original Artwork" only exists in the session that changed it, while the original is still known. */
    val canRestoreArtwork: Boolean get() = artworkEdit != ArtworkChoice.Unchanged && hadArtwork
}

/** An inline error for a field, or null when the value is fine. Untouched fields never show an error. */
fun validateField(field: EditField, state: FieldState): String? {
    if (!state.changed) return null
    val t = state.text.trim()
    return when (field) {
        EditField.Title -> if (t.isEmpty()) "The title can't be empty." else null
        EditField.Year ->
            if (t.isNotEmpty() && t.toIntOrNull()?.let { it in 1000..9999 } != true) "Enter a four-digit year." else null
        EditField.TrackNumber, EditField.DiscNumber ->
            if (t.isNotEmpty() && t.toIntOrNull()?.let { it in 1..9999 } != true) "Enter a number from 1 to 9999." else null
        else -> null
    }
}

@HiltViewModel
class EditFileViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val player: PlayerController,
    private val artworkStore: ArtworkStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    val genres: StateFlow<List<String>> =
        repository.observeGenreNames().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var loadJob: Job? = null

    // --- Opening / closing -------------------------------------------------------------

    fun openSong(song: Song) = load(EditTarget.OfSong(song.id), song.title, null)

    fun openAlbum(album: Album) = load(EditTarget.OfAlbum(album.id), album.title, null)

    fun requestClose() {
        val s = _state.value
        if (s.saving) return
        if (s.isDirty) _state.update { it.copy(askDiscard = true) } else close()
    }

    fun discardAndClose() = close()
    fun keepEditing() = _state.update { it.copy(askDiscard = false) }
    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    private fun close() {
        loadJob?.cancel()
        _state.value = EditUiState()
    }

    // --- Loading ---------------------------------------------------------------------------

    private fun load(target: EditTarget, heading: String, errorAfterLoad: String?) {
        loadJob?.cancel()
        _state.value = EditUiState(target = target, loading = true, isAlbum = target is EditTarget.OfAlbum, heading = heading)
        loadJob = viewModelScope.launch {
            try {
                when (target) {
                    is EditTarget.OfSong -> loadSong(target.songId)
                    is EditTarget.OfAlbum -> loadAlbum(target.albumId)
                }
                if (errorAfterLoad != null) _state.update { it.copy(saveError = errorAfterLoad) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = e.message ?: "Could not load this item.") }
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(loading = false, loadError = message) }
    }

    private fun fs(value: String?): FieldState = (value ?: "").let { FieldState(it, it) }

    private suspend fun loadSong(songId: Long) {
        val song = repository.getSongsByIds(listOf(songId)).firstOrNull()
        if (song == null) {
            fail("This song is no longer in the library.")
            return
        }
        val snapshot = repository.readSongTags(songId)
        val album = song.albumId?.let { repository.observeAlbum(it).first() }
        val dbAlbumArtist = album?.albumArtistId?.let { repository.observeArtist(it).first()?.name }
        val tags = snapshot?.tags

        // File values win; the library's values are only shown when the file could not be read at all.
        val fields = mapOf(
            EditField.Title to fs(tags?.title ?: song.title),
            EditField.Artist to fs(if (tags != null) tags.artist else song.artistName),
            EditField.Album to fs(if (tags != null) tags.album else song.albumTitle),
            EditField.Year to fs((if (tags != null) tags.year else song.year)?.toString()),
            EditField.TrackNumber to fs((if (tags != null) tags.trackNumber else song.trackNumber)?.toString()),
            EditField.Genre to fs(if (tags != null) tags.genre else song.genreName),
            EditField.Comment to fs(tags?.comment),
            EditField.AlbumArtist to fs(if (tags != null) tags.albumArtist else dbAlbumArtist),
            EditField.Composer to fs(if (tags != null) tags.composer else song.composer),
            EditField.DiscNumber to fs((if (tags != null) tags.discNumber else song.discNumber)?.toString()),
        )
        val notes = buildList {
            snapshot?.notEditableReason?.let { add(it) }
            if (tags == null && snapshot?.notEditableReason == null) {
                add("The tags in this file couldn't be read, so the library's values are shown.")
            }
        }
        _state.update {
            it.copy(
                loading = false,
                heading = song.title,
                fields = fields,
                directory = song.documentId.substringAfter(':'),
                artworkPath = song.artworkUri,
                hadArtwork = song.artworkUri != null || snapshot?.embeddedArtwork != null,
                editable = snapshot != null && snapshot.notEditableReason == null,
                notes = notes,
            )
        }
    }

    private suspend fun loadAlbum(albumId: Long) {
        val album = repository.observeAlbum(albumId).first()
        if (album == null) {
            fail("This album is no longer in the library.")
            return
        }
        val songs = repository.observeAlbumSongs(albumId).first()
        val dbAlbumArtist = album.albumArtistId?.let { repository.observeArtist(it).first()?.name }
        val snapshots = songs.map { it to repository.readSongTags(it.id) }

        fun values(pick: (Song, FileTags?) -> String?): List<String?> =
            snapshots.map { (song, snap) -> pick(song, snap?.tags) }

        val fields = mapOf(
            EditField.Album to fs(album.title),
            EditField.AlbumArtist to common(values { _, t -> if (t != null) t.albumArtist else dbAlbumArtist }),
            EditField.Year to common(values { s, t -> (if (t != null) t.year else s.year)?.toString() }),
            EditField.Genre to common(values { s, t -> if (t != null) t.genre else s.genreName }),
            EditField.Composer to common(values { s, t -> if (t != null) t.composer else s.composer }),
            EditField.Comment to common(values { _, t -> t?.comment }),
        )
        val tracks = snapshots.map { (song, snap) ->
            val t = snap?.tags
            TrackEdit(
                songId = song.id,
                fileName = song.displayName ?: song.title,
                editable = snap != null && snap.notEditableReason == null,
                note = snap?.notEditableReason,
                title = fs(t?.title ?: song.title),
                artist = fs(if (t != null) t.artist else song.artistName),
                trackNumber = fs((if (t != null) t.trackNumber else song.trackNumber)?.toString()),
                discNumber = fs((if (t != null) t.discNumber else song.discNumber)?.toString()),
            )
        }
        val blocked = tracks.filter { !it.editable }
        val notes = buildList {
            if (blocked.isNotEmpty()) {
                add("${blocked.size} of ${tracks.size} tracks can't be edited and will be skipped. ${blocked.first().note.orEmpty()}")
            }
        }
        val parents = songs.map { it.documentId.substringAfter(':').substringBeforeLast('/', "") }.distinct()
        _state.update {
            it.copy(
                loading = false,
                heading = album.title,
                fields = fields,
                tracks = tracks,
                directory = when {
                    parents.size > 1 -> "Multiple locations"
                    parents.firstOrNull().isNullOrEmpty() -> "Music folder root"
                    else -> parents.first()
                },
                artworkPath = album.artworkUri,
                hadArtwork = album.artworkUri != null,
                editable = tracks.any { t -> t.editable },
                notes = notes,
            )
        }
    }

    private fun common(values: List<String?>): FieldState {
        val normalized = values.map { it?.trim().orEmpty() }
        val first = normalized.firstOrNull().orEmpty()
        return if (normalized.all { it == first }) FieldState(first, first) else FieldState("", "", mixed = true)
    }

    // --- Editing -----------------------------------------------------------------------------

    fun setField(field: EditField, text: String) {
        _state.update { s ->
            s.copy(fields = s.fields + (field to (s.fields[field] ?: FieldState()).copy(text = text)), saveError = null)
        }
    }

    fun setTrackField(songId: Long, field: EditField, text: String) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { if (it.songId == songId) it.withText(field, text) else it }, saveError = null)
        }
    }

    fun pickArtwork(uri: Uri) {
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { bytes -> artworkStore.toEmbeddableJpeg(bytes) }
            }
            if (jpeg == null) {
                _state.update { it.copy(saveError = "That file isn't a picture Auralis can use. Choose a JPG, PNG or WEBP image.") }
            } else {
                _state.update { it.copy(artworkEdit = ArtworkChoice.Replaced(jpeg), saveError = null) }
            }
        }
    }

    fun removeArtwork() {
        _state.update { it.copy(artworkEdit = ArtworkChoice.Removed, saveError = null) }
    }

    fun restoreArtwork() {
        _state.update { it.copy(artworkEdit = ArtworkChoice.Unchanged, saveError = null) }
    }

    // --- Saving ------------------------------------------------------------------------------

    fun requestSave() {
        val s = _state.value
        if (!s.canSave) return
        if (s.isAlbum) {
            val request = buildRequest(s)
            val mixedOverwrites = s.fields.filter { (_, f) -> f.mixed && f.changed }.keys.map { fieldLabel(it) }
            val playing = player.uiState.value
            val confirmation = SaveConfirmation(
                trackCount = request.songs.size,
                skippedTracks = s.tracks.count { !it.editable },
                overwrittenFields = mixedOverwrites,
                removesArtwork = s.artworkEdit == ArtworkChoice.Removed,
                playingWarning = playing.isPlaying && request.songs.any { e -> e.songId == playing.currentSong?.id },
            )
            _state.update { it.copy(confirm = confirmation) }
        } else {
            performSave()
        }
    }

    fun confirmSave() = performSave()

    private fun fieldLabel(field: EditField): String = when (field) {
        EditField.AlbumArtist -> "Album Artist"
        EditField.TrackNumber -> "Track Number"
        EditField.DiscNumber -> "Disc Number"
        else -> field.name
    }

    private fun performSave() {
        val s = _state.value
        val target = s.target ?: return
        val request = buildRequest(s)
        _state.update { it.copy(saving = true, saveError = null, confirm = null) }
        viewModelScope.launch {
            val outcome = try {
                repository.applyMetadataEdits(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (outcome == null) {
                _state.update { it.copy(saving = false, saveError = "Saving failed unexpectedly. Nothing was reported as saved.") }
                return@launch
            }
            if (outcome.updatedSongIds.isNotEmpty()) player.refreshSongs(outcome.updatedSongIds.toSet())
            if (outcome.allSucceeded) {
                val n = outcome.updatedSongIds.size
                Toast.makeText(context, if (n == 1) "Changes saved" else "Changes saved to $n tracks", Toast.LENGTH_SHORT).show()
                close()
            } else {
                val shown = outcome.failures.take(3).joinToString("\n") { "${it.title}: ${it.reason}" }
                val more = outcome.failures.size - 3
                val message = buildString {
                    if (outcome.updatedSongIds.isNotEmpty()) {
                        append("Saved ${outcome.updatedSongIds.size}, but ${outcome.failures.size} failed.\n")
                    }
                    append(shown)
                    if (more > 0) append("\nand $more more.")
                }
                if (outcome.updatedSongIds.isEmpty()) {
                    _state.update { it.copy(saving = false, saveError = message) }
                } else {
                    // Some files did change: reload so the form shows what is really in the files now.
                    load(target, s.heading, message)
                }
            }
        }
    }

    private fun buildRequest(s: EditUiState): MetadataEditRequest {
        fun strOf(f: FieldState?): Edit<String> = if (f != null && f.changed) Edit.Change(f.value) else Edit.Keep
        fun numOf(f: FieldState?): Edit<Int> = if (f != null && f.changed) Edit.Change(f.value?.toIntOrNull()) else Edit.Keep
        fun str(field: EditField): Edit<String> = strOf(s.fields[field])
        fun num(field: EditField): Edit<Int> = numOf(s.fields[field])

        val artwork: ArtworkEdit = when (val a = s.artworkEdit) {
            ArtworkChoice.Unchanged -> ArtworkEdit.Keep
            ArtworkChoice.Removed -> ArtworkEdit.Remove
            is ArtworkChoice.Replaced -> ArtworkEdit.Replace(a.jpeg)
        }
        val target = s.target
        if (target is EditTarget.OfSong) {
            val changes = TagChanges(
                title = str(EditField.Title),
                artist = str(EditField.Artist),
                album = str(EditField.Album),
                albumArtist = str(EditField.AlbumArtist),
                composer = str(EditField.Composer),
                genre = str(EditField.Genre),
                comment = str(EditField.Comment),
                year = num(EditField.Year),
                trackNumber = num(EditField.TrackNumber),
                discNumber = num(EditField.DiscNumber),
                artwork = artwork,
            )
            return MetadataEditRequest(listOf(SongTagEdit(target.songId, changes)))
        }
        // Album: the album-wide fields go to every track, but only the ones the user actually changed, and
        // only tracks that end up with at least one change are written at all.
        val shared = TagChanges(
            album = str(EditField.Album),
            albumArtist = str(EditField.AlbumArtist),
            composer = str(EditField.Composer),
            genre = str(EditField.Genre),
            comment = str(EditField.Comment),
            year = num(EditField.Year),
            artwork = artwork,
        )
        val edits = s.tracks.filter { it.editable }.mapNotNull { t ->
            val merged = shared.copy(
                title = strOf(t.title),
                artist = strOf(t.artist),
                trackNumber = numOf(t.trackNumber),
                discNumber = numOf(t.discNumber),
            )
            if (merged.isEmpty) null else SongTagEdit(t.songId, merged)
        }
        return MetadataEditRequest(edits, albumId = (target as? EditTarget.OfAlbum)?.albumId)
    }
}
