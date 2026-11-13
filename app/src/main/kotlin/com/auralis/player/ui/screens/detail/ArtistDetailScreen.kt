package com.auralis.player.ui.screens.detail

import com.auralis.player.ui.components.GlassToolbarButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.domain.util.releaseYear
import com.auralis.player.domain.model.Song
import com.auralis.player.ui.components.AlbumGridTile
import com.auralis.player.ui.components.EmptyState
import com.auralis.player.ui.components.PlayAction
import com.auralis.player.ui.components.SectionHeading
import com.auralis.player.ui.screens.AlbumCard
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog
import com.auralis.player.ui.screens.MusicScreensViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Artist page: the artist's name at the top, an "Albums" section directly beneath it
 * (oldest release first, undated albums last; sorting happens in the repository), then
 * the artist's songs.
 */
@Composable
fun ArtistDetailScreen(onBack: () -> Unit, onOpenAlbum: (Long) -> Unit = {}, onOpenArtist: (Long) -> Unit = {}) {
    val model: MusicScreensViewModel = hiltViewModel()
    val artist by model.artist.collectAsState(initial = null)
    val albums by model.artistAlbums.collectAsState(initial = emptyList())
    val songs by model.artistSongs.collectAsState(initial = emptyList())
    val songCount = uniqueArtistSongCount(songs)
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Responsive: as many ~156 dp wide cards per row as fit, never fewer than two.
        val columns = (maxWidth / 156.dp).toInt().coerceAtLeast(2)

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassToolbarButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                    Spacer(Modifier.weight(1f))
                    artist?.let { current ->
                        GlassToolbarButton(onClick = { model.favoriteArtist(current.id, current.isFavorite) }) {
                            Icon(
                                if (current.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(if (current.isFavorite) R.string.details_unfavorite else R.string.details_favorite),
                            )
                        }
                    }
                }
                artist?.let { current ->
                    Text(
                        current.name,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Text(
                        stringResource(R.string.artist_song_count, songCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            if (artist != null && albums.isNotEmpty()) {
                item {
                    SectionHeading(stringResource(R.string.library_tab_albums), Modifier.padding(horizontal = 16.dp))
                }
                items(albums.chunked(columns), key = { row -> "albums-${row.first().id}" }) { rowAlbums ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowAlbums.forEach { album ->
                            AlbumCard(
                                album,
                                artistName = artist?.name,
                                model = model,
                                onOpen = { onOpenAlbum(album.id) },
                                modifier = Modifier.weight(1f),
                                showYear = true,
                                onAddToPlaylist = { playlistTarget = it },
                                onOpenArtist = onOpenArtist,
                            )
                        }
                        repeat(columns - rowAlbums.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            if (songs.isEmpty()) {
                if (artist != null) item { EmptyState("No tracks found", "This artist has no available songs.") }
            } else {
                item {
                    SectionHeading("Songs", Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PlayAction(onClick = { model.play(songs) })
                        PlayAction(onClick = { model.shuffle(songs) }, label = stringResource(R.string.action_shuffle))
                    }
                }
                items(songs, key = { "song-${it.id}" }) { song ->
                    MenuSongRow(
                        song,
                        model,
                        onPlay = { model.play(songs, songs.indexOf(song)) },
                        onAddToPlaylist = { playlistTarget = it },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                    )
                }
            }
        }
    }
    PlaylistTargetDialog(playlistTarget, model) { playlistTarget = null }
}

internal fun uniqueArtistSongCount(songs: List<Song>): Int = songs.asSequence().map(Song::id).distinct().count()
