package com.auralis.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import com.auralis.player.domain.model.Song

object MediaItemFactory {

    /**
     * [MediaItem.mediaId] is set to the song's database id (as a string) so
     * [PlayerController] can match a player-reported media item back to its
     * [Song] by simple index lookup into the queue it was given — see
     * [PlayerController.playQueue].
     */
    fun from(song: Song, playableUri: Uri): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(playableUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumTitle)
                    // Lets the media notification and lock screen show the cover.
                    .setArtworkUri(song.artworkUri?.let { Uri.fromFile(File(it)) })
                    .build(),
            )
            .build()
}
