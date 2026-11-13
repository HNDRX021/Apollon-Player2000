package com.auralis.player.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.auralis.player.R
import com.auralis.player.ui.components.AlbumGridTile
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Song
import com.auralis.player.domain.util.releaseYear
import com.auralis.player.ui.components.AddToPlaylistDialog
import com.auralis.player.ui.components.LocalEditFileActions
import com.auralis.player.ui.components.RowMenuButton
import com.auralis.player.ui.components.RowMenuItem
import com.auralis.player.ui.components.ShowcaseSongRow

/*
 * The song and album menus shared by Home, Search and the Library, so the three screens behave the
 * same and the wiring to the player, favorites and playlists exists once.
 */

/** What the "Add to Playlist" picker is about to add: one song, or every track of an album. */
sealed interface PlaylistTarget {
    data class OfSong(val song: Song) : PlaylistTarget
    data class OfAlbum(val album: Album) : PlaylistTarget
}

internal sealed interface SongMenuDestination {
    data class Artist(val id: Long) : SongMenuDestination
    data class Album(val id: Long) : SongMenuDestination
}

internal fun songMenuDestinations(artistId: Long?, albumId: Long?): List<SongMenuDestination> = buildList {
    artistId?.let { add(SongMenuDestination.Artist(it)) }
    albumId?.let { add(SongMenuDestination.Album(it)) }
}

/**
 * The playlist picker for [target]; nothing is shown while [target] is null. Picking a playlist adds
 * the song, or the whole album in track order, to it and closes the picker through [onDismiss].
 */
@Composable
fun PlaylistTargetDialog(target: PlaylistTarget?, model: MusicScreensViewModel, onDismiss: () -> Unit) {
    val playlists by model.playlists.collectAsState(initial = emptyList())
    if (target == null) return
    AddToPlaylistDialog(
        playlists = playlists,
        onPick = { playlist ->
            when (target) {
                is PlaylistTarget.OfSong -> model.addSongToPlaylist(playlist.id, target.song.id, playlist.name)
                is PlaylistTarget.OfAlbum -> model.addAlbumToPlaylist(target.album.id, playlist.id, playlist.name)
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/**
 * The three-dot menu next to an album's name (Library, Search and Home): Add to Playlist, Add to
 * Queue, Play Next and Favorite, in that order. "Favorite" toggles the album and shows a filled
 * accent-coloured heart while it is a favorite. "Add to Playlist" hands the album to [onAddToPlaylist]
 * (the last parameter, so it can be a trailing lambda) so the screen can open the picker
 * ([PlaylistTargetDialog]). Pass it as the `menu` of an AlbumGridTile.
 */
@Composable
fun AlbumMenuButton(
    album: Album,
    model: MusicScreensViewModel,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (PlaylistTarget) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val editActions = LocalEditFileActions.current
    RowMenuButton(
        items = listOfNotNull(
            RowMenuItem("Add to Playlist", onClick = { onAddToPlaylist(PlaylistTarget.OfAlbum(album)) }),
            RowMenuItem("Add to Queue", onClick = { model.addAlbumToQueue(album.id) }),
            RowMenuItem("Play Next", onClick = { model.playAlbumNext(album.id) }),
            album.albumArtistId?.let { artistId ->
                RowMenuItem(stringResource(R.string.media_go_to_artist), onClick = { onOpenArtist(artistId) })
            },
            RowMenuItem(
                "Favorite",
                onClick = { model.favoriteAlbum(album.id, album.isFavorite) },
                trailingIcon = if (album.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                highlighted = album.isFavorite,
            ),
            editActions?.let { actions ->
                RowMenuItem("Edit File", { actions.editAlbum(album) }, leadingIcon = Icons.Outlined.Edit, dividerBefore = true)
            },
        ),
        contentDescription = "Album actions",
        modifier = modifier,
    )
}

/**
 * THE song row (see [ShowcaseSongRow]) with THE song menu, identical on every screen: Play Next, Add to
 * Queue, Add to Playlist, Open Folder, Refresh Metadata, any [extraItems] this screen needs (remove from
 * playlist, queue moves...), then Edit File in its own section. The heart sits inline before the
 * three-dot button. Tapping the row runs [onPlay]; [onAddToPlaylist] receives the song so the screen can
 * open the picker ([PlaylistTargetDialog]).
 */
@Composable
fun MenuSongRow(
    song: Song,
    model: MusicScreensViewModel,
    onPlay: () -> Unit,
    onAddToPlaylist: (PlaylistTarget) -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
    showAlbum: Boolean = true,
    extraItems: List<RowMenuItem> = emptyList(),
    onOpenArtist: ((Long) -> Unit)? = null,
    onOpenAlbum: ((Long) -> Unit)? = null,
) {
    val editActions = LocalEditFileActions.current
    val openFolderLabel = stringResource(R.string.song_open_folder)
    val refreshLabel = stringResource(R.string.song_refresh_metadata)
    val goToArtistLabel = stringResource(R.string.media_go_to_artist)
    val goToAlbumLabel = stringResource(R.string.media_go_to_album)
    val navigationTargets = songMenuDestinations(song.artistId, song.albumId)
    val items = buildList {
        add(RowMenuItem("Play Next", { model.playNext(song) }))
        add(RowMenuItem("Add to Queue", { model.addToQueue(song) }))
        add(RowMenuItem("Add to Playlist", { onAddToPlaylist(PlaylistTarget.OfSong(song)) }))
        navigationTargets.forEach { target ->
            when (target) {
                is SongMenuDestination.Artist -> onOpenArtist?.let { onOpen ->
                    add(RowMenuItem(goToArtistLabel, { onOpen(target.id) }, dividerBefore = true))
                }
                is SongMenuDestination.Album -> onOpenAlbum?.let { onOpen ->
                    add(RowMenuItem(goToAlbumLabel, { onOpen(target.id) }))
                }
            }
        }
        add(RowMenuItem(openFolderLabel, { model.openSongFolder(song) }))
        add(RowMenuItem(refreshLabel, { model.refreshSongMetadata(song) }))
        addAll(extraItems)
        if (editActions != null) {
            add(RowMenuItem("Edit File", { editActions.editSong(song) }, leadingIcon = Icons.Outlined.Edit, dividerBefore = true))
        }
    }
    ShowcaseSongRow(
        song = song,
        onPlay = onPlay,
        onFavorite = { model.favorite(song) },
        menuItems = items,
        modifier = modifier,
        isCurrent = isCurrent,
        leading = leading,
        dragHandle = dragHandle,
        showAlbum = showAlbum,
    )
}

/**
 * THE album card, identical on every screen: square cover with the track-count label, the album name on
 * top, the artist underneath, and the album three-dot menu ([AlbumMenuButton]). [artistName] is the album
 * artist's display name (each screen already has it).
 */
@Composable
fun AlbumCard(
    album: Album,
    artistName: String?,
    model: MusicScreensViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showYear: Boolean = false,
    onAddToPlaylist: (PlaylistTarget) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    // With [showYear] (artist page) the second line is "2019 · Artist"; the year is the album's own release metadata.
    val year = if (showYear) album.releaseYear else null
    AlbumGridTile(
        album = album,
        line1 = album.title,
        line2 = listOfNotNull(year?.toString(), artistName?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null },
        onClick = onOpen,
        modifier = modifier,
        menu = { AlbumMenuButton(album, model, onAddToPlaylist = onAddToPlaylist, onOpenArtist = onOpenArtist) },
    )
}
