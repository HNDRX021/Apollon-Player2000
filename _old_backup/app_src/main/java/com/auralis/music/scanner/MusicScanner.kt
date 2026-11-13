package com.auralis.music.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.auralis.music.data.LibraryRepository
import com.auralis.music.data.MusicFolderEntity
import com.auralis.music.metadata.MetadataExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class MusicScanner(
    private val context: Context,
    private val resolver: ContentResolver,
    private val repository: LibraryRepository,
    private val artworkDir: File
) {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    suspend fun scanAll() = withContext(Dispatchers.IO) {
        repository.folders.first().filter { it.enabled }.forEach { scanFolder(it) }
    }

    suspend fun scanFolder(folder: MusicFolderEntity) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.treeUri))
        if (root == null || !root.canRead()) {
            _state.value = ScanState(error = "Folder access is no longer available.")
            return@withContext
        }

        val files = mutableListOf<DocumentFile>()
        collectAudioFiles(root, files, folder.recursive)
        val seen = ArrayList<String>(files.size)
        _state.value = ScanState(total = files.size)

        for ((index, file) in files.withIndex()) {
            try {
                val uri = file.uri
                val existing = repository.findByUri(uri.toString())
                val song = MetadataExtractor.extract(
                    resolver, uri, folder.id,
                    file.name ?: "Unknown",
                    file.length(),
                    file.lastModified(),
                    artworkDir
                )
                if (song != null) {
                    if (existing != null) {
                        repository.updateSong(
                            song.copy(
                                id = existing.id,
                                dateAdded = existing.dateAdded,
                                isFavorite = existing.isFavorite,
                                playCount = existing.playCount,
                                lastPlayedAt = existing.lastPlayedAt,
                                lastPositionMs = existing.lastPositionMs
                            )
                        )
                    } else {
                        val moved = repository.findByFingerprint(folder.id, song.fingerprint)
                        if (moved != null && moved.sourceUri != song.sourceUri) {
                            repository.updateSong(
                                song.copy(
                                    id = moved.id,
                                    dateAdded = moved.dateAdded,
                                    isFavorite = moved.isFavorite,
                                    playCount = moved.playCount,
                                    lastPlayedAt = moved.lastPlayedAt,
                                    lastPositionMs = moved.lastPositionMs
                                )
                            )
                        } else {
                            repository.upsertSong(song)
                        }
                    }
                    seen += song.sourceUri
                    _state.value = ScanState(
                        total = files.size,
                        processed = index + 1,
                        currentFile = song.title,
                        failed = _state.value.failed
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.value = ScanState(
                    total = files.size,
                    processed = index + 1,
                    failed = _state.value.failed + 1,
                    currentFile = file.name.orEmpty()
                )
            }
        }

        repository.deleteMissingInFolder(folder.id, seen)
        _state.value = ScanState(total = files.size, processed = files.size, finished = true, failed = _state.value.failed)
    }

    private fun collectAudioFiles(node: DocumentFile, output: MutableList<DocumentFile>, recursive: Boolean) {
        val children = runCatching { node.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isDirectory && recursive) {
                collectAudioFiles(child, output, true)
            } else if (child.isFile && isSupported(child.name)) {
                output += child
            }
        }
    }

    private fun isSupported(name: String?): Boolean =
        when (name?.substringAfterLast('.', "")?.lowercase()) {
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus" -> true
            else -> false
        }
}

data class ScanState(
    val total: Int = 0,
    val processed: Int = 0,
    val currentFile: String = "",
    val failed: Int = 0,
    val finished: Boolean = false,
    val error: String? = null
)
