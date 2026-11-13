package com.auralis.player.ui.screens.home

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassTextButton

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Song
import com.auralis.player.domain.util.HOME_RECENT_ITEMS
import com.auralis.player.domain.util.firstVisible
import com.auralis.player.domain.util.hasMoreToShow
import com.auralis.player.ui.components.AlbumGridTile
import com.auralis.player.ui.components.ArtworkImage
import com.auralis.player.ui.components.EmptyState
import com.auralis.player.ui.components.PlayAction
import com.auralis.player.ui.components.ScreenHeader
import com.auralis.player.ui.navigation.Destination
import com.auralis.player.ui.navigation.ScrollResetEffect
import com.auralis.player.ui.screens.AlbumCard
import com.auralis.player.ui.screens.MenuSongRow
import com.auralis.player.ui.screens.MusicScreensViewModel
import com.auralis.player.ui.screens.PlaylistTarget
import com.auralis.player.ui.screens.PlaylistTargetDialog

/**
 * Width of an album card in the Home carousels. The cover is square, so this is also its height:
 * the same ~156dp cover the Library's album grid shows on a typical phone (and the cover height of
 * the previous Home album tile). The card itself is the shared [AlbumGridTile], untouched.
 */
private val AlbumCardWidth = 156.dp

/** Gap between carousel cards, same as the Library's album grid. */
private val CarouselSpacing = 16.dp

/** Where a song row's text starts (16dp row padding + 44dp artwork + 12dp gap), so dividers line up with the titles. */
private val SongDividerInset = 72.dp

/**
 * The Home page: one vertically scrolling list, `Header > Recently Played > Today's Suggestions >
 * Your Favorites > Today's Music`. Every section is a plain item of the single [LazyColumn]; the
 * three album rows scroll sideways only. The bottom bar and mini player are laid out by the
 * Scaffold, which pads this screen, so the last song can always be scrolled fully into view.
 */
@Composable
fun HomeScreen(onOpenAlbum: (Long) -> Unit, onOpenArtist: (Long) -> Unit, onOpenSearch: () -> Unit) {
    val model: MusicScreensViewModel = hiltViewModel()
    // Nullable "not loaded yet" states keep a section from flashing its empty state for a frame.
    val stats by model.stats.collectAsState(initial = null)
    val scan by model.scanProgress.collectAsState()
    val history by model.recentHistory.collectAsState(initial = null)
    val albumList by model.albums.collectAsState(initial = null)
    val artists by model.artists.collectAsState(initial = emptyList())
    val favoriteAlbums by model.favoriteAlbums.collectAsState(initial = emptyList())
    val daily by model.dailyMix.collectAsState(initial = null)

    // How many of Today's Music are shown. The count lives in the view model, so it survives
    // recompositions and unrelated state changes, and also an activity recreation (rotation, theme
    // change) while Home stays on screen. It is reset to one page when Home leaves the composition
    // (navigating to another tab or screen), so coming back starts at 25 again. Only this count
    // resets; the library, favorites, playlists and queue are not touched.
    val visibleSongCount by model.todaysMusicVisible.collectAsState()
    val context = LocalContext.current
    DisposableEffect(model) {
        onDispose {
            // A rotation or theme change also disposes this composition, but the user has not left the page.
            if (context.findActivity()?.isChangingConfigurations != true) model.resetTodaysMusicPaging()
        }
    }
    // What the "Add to Playlist" picker is adding, if it is open: a song or a whole album.
    var playlistTarget by remember { mutableStateOf<PlaylistTarget?>(null) }

    val unknownArtist = stringResource(R.string.library_unknown_artist)
    val artistNames = remember(artists) { artists.associate { it.id to it.name } }
    val recentItems = remember(history, albumList) {
        val songs = history
        val albums = albumList
        if (songs == null || albums == null) null else buildRecentItems(songs, albums.associateBy { it.id })
    }
    val suggestions = daily?.albums.orEmpty()
    val todaysSongs = daily?.songs.orEmpty()
    val libraryEmpty = stats?.songCount == 0
    // Today's Music is paged: the first page, then one more page per "Show More" until all are shown.
    val displayedSongs = remember(todaysSongs, visibleSongCount) { todaysSongs.firstVisible(visibleSongCount) }
    val hasMoreSongs = hasMoreToShow(todaysSongs.size, visibleSongCount)

    val listState = rememberLazyListState()
    val recentRowState = rememberLazyListState()
    val suggestionsRowState = rememberLazyListState()
    val favoritesRowState = rememberLazyListState()
    // Tapping the Home icon returns to the top, including the horizontal rows.
    ScrollResetEffect(Destination.Home.route) {
        listState.scrollToItem(0)
        recentRowState.scrollToItem(0)
        suggestionsRowState.scrollToItem(0)
        favoritesRowState.scrollToItem(0)
    }

    fun artistLine(album: Album): String = album.albumArtistId?.let(artistNames::get) ?: unknownArtist

    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        item(key = "header") {
            ScreenHeader(
                title = "Auralis",
                subtitle = "Your Music, My Way",
                action = {
                    GlassToolbarButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
            )
        }
        if (scan.isScanning) {
            item(key = "scan") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text("Scanning ${scan.currentName.orEmpty()} · ${scan.processed} files", Modifier.padding(16.dp))
                }
            }
        }
        if (libraryEmpty && !scan.isScanning) {
            item(key = "library_empty") {
                EmptyState(
                    title = "Your library is empty",
                    body = "Add a music folder in Settings to scan the songs on your device.",
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        } else {
            // --- Recently Played -------------------------------------------------
            if (recentItems != null) {
                item(key = "recent_header") { HomeSectionHeader("Recently Played") }
                if (recentItems.isEmpty()) {
                    item(key = "recent_empty") { RecentEmptyState() }
                } else {
                    item(key = "recent_row") {
                        LazyRow(
                            state = recentRowState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(CarouselSpacing),
                        ) {
                            items(recentItems, key = { it.key }) { entry ->
                                when (entry) {
                                    is RecentItem.OfAlbum -> AlbumCard(
                                        entry.album,
                                        artistName = artistLine(entry.album),
                                        model = model,
                                        onOpen = { onOpenAlbum(entry.album.id) },
                                        modifier = Modifier.width(AlbumCardWidth),
                                        onAddToPlaylist = { playlistTarget = it },
                                        onOpenArtist = onOpenArtist,
                                    )
                                    is RecentItem.OfSong -> SongCoverTile(
                                        entry.song,
                                        onClick = { model.play(listOf(entry.song)) },
                                        modifier = Modifier.width(AlbumCardWidth),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Today's Suggestions ---------------------------------------------
            if (suggestions.isNotEmpty()) {
                item(key = "suggestions_header") { HomeSectionHeader("Today's Suggestions", "Picked for you today") }
                item(key = "suggestions_row") {
                    AlbumRow(suggestions, suggestionsRowState, "suggestion_", ::artistLine, onOpenAlbum, onOpenArtist, model) { playlistTarget = it }
                }
            }

            // --- Your Favorites --------------------------------------------------
            if (favoriteAlbums.isNotEmpty()) {
                item(key = "favorites_header") { HomeSectionHeader("Your Favorites") }
                item(key = "favorites_row") {
                    AlbumRow(favoriteAlbums, favoritesRowState, "favorite_", ::artistLine, onOpenAlbum, onOpenArtist, model) { playlistTarget = it }
                }
            }

            // --- Today's Music ---------------------------------------------------
            if (displayedSongs.isNotEmpty()) {
                item(key = "songs_header") {
                    HomeSectionHeader(
                        "Today's Music",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PlayAction(onClick = { model.playInOrder(displayedSongs) }, compact = true)
                                PlayAction(
                                    onClick = { model.playRandomized(displayedSongs) },
                                    label = "Shuffle",
                                    icon = Icons.Default.Shuffle,
                                    compact = true,
                                )
                            }
                        },
                    )
                }
                itemsIndexed(displayedSongs, key = { _, song -> "today_${song.id}" }) { index, song ->
                    MenuSongRow(
                        song = song,
                        model = model,
                        onPlay = { model.play(displayedSongs, index) },
                        onAddToPlaylist = { playlistTarget = it },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                    )
                }
                if (hasMoreSongs) {
                    item(key = "show_more") {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            GlassTextButton(onClick = { model.showMoreTodaysMusic() }) {
                                Text("Show More", style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
    }

    PlaylistTargetDialog(playlistTarget, model) { playlistTarget = null }
}

/** The Activity behind a (possibly wrapped) Context, or null if there is none. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A horizontally scrolling row of album cards (title, then artist, under each cover), each with the
 * shared album three-dot menu next to its name; "Add to Playlist" goes to [onAddToPlaylist].
 */
@Composable
private fun AlbumRow(
    albums: List<Album>,
    state: LazyListState,
    keyPrefix: String,
    artistLine: (Album) -> String,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    model: MusicScreensViewModel,
    onAddToPlaylist: (PlaylistTarget) -> Unit,
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(CarouselSpacing),
    ) {
        items(albums, key = { "$keyPrefix${it.id}" }) { album ->
            AlbumCard(
                album,
                artistName = artistLine(album),
                model = model,
                onOpen = { onOpenAlbum(album.id) },
                modifier = Modifier.width(AlbumCardWidth),
                onAddToPlaylist = onAddToPlaylist,
                onOpenArtist = onOpenArtist,
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun RecentEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Nothing played yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Albums and songs you listen to will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Card for a recently played song that belongs to no album: the same square, flat cover with the
 * title and artist underneath as [AlbumGridTile], minus the album-only track-count label.
 */
@Composable
private fun SongCoverTile(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        ArtworkImage(
            song.artworkUri,
            Modifier.fillMaxWidth().aspectRatio(1f),
            targetSize = 220.dp,
            shape = RectangleShape,
        )
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            song.artistName?.takeIf(String::isNotBlank) ?: "Unknown artist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

private sealed interface RecentItem {
    val key: String

    data class OfAlbum(val album: Album) : RecentItem {
        override val key: String get() = "album_${album.id}"
    }

    data class OfSong(val song: Song) : RecentItem {
        override val key: String get() = "song_${song.id}"
    }
}

/**
 * Folds the play history (newest first) into the "Recently Played" row: every album once, at the
 * position of its most recent play, and songs that have no album as songs. At most [HOME_RECENT_ITEMS].
 */
private fun buildRecentItems(history: List<Song>, albumsById: Map<Long, Album>): List<RecentItem> {
    val seenAlbums = HashSet<Long>()
    val result = ArrayList<RecentItem>()
    for (song in history) {
        if (result.size >= HOME_RECENT_ITEMS) break
        val album = song.albumId?.let(albumsById::get)
        if (album == null) {
            result.add(RecentItem.OfSong(song))
        } else if (seenAlbums.add(album.id)) {
            result.add(RecentItem.OfAlbum(album))
        }
    }
    return result
}
