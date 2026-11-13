package com.auralis.player.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.auralis.player.ui.screens.detail.AlbumDetailScreen
import com.auralis.player.ui.screens.detail.AlbumDetailViewModel
import com.auralis.player.ui.screens.detail.ArtistDetailScreen
import com.auralis.player.ui.screens.detail.ArtistDetailViewModel
import com.auralis.player.ui.screens.equalizer.EqualizerScreen
import com.auralis.player.ui.screens.home.HomeScreen
import com.auralis.player.ui.screens.library.LibraryScreen
import com.auralis.player.ui.screens.player.MiniPlayer
import com.auralis.player.ui.screens.player.NowPlayingScreen
import com.auralis.player.ui.screens.player.QueueScreen
import com.auralis.player.ui.screens.playlists.CollectionDetailScreen
import com.auralis.player.ui.screens.playlists.PlaylistDetailScreen
import com.auralis.player.ui.screens.playlists.PlaylistDetailViewModel
import com.auralis.player.ui.screens.playlists.PlaylistsScreen
import com.auralis.player.ui.screens.playlists.SmartCollection
import com.auralis.player.ui.screens.search.SearchScreen
import com.auralis.player.ui.screens.settings.SettingsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.ui.components.EditFileActions
import com.auralis.player.ui.components.LocalEditFileActions
import com.auralis.player.ui.screens.edit.EditFileHost
import com.auralis.player.ui.screens.edit.EditFileViewModel

private const val NOW_PLAYING_ROUTE = "now_playing"
private const val PLAYLIST_DETAIL_ROUTE = "playlist"
private const val COLLECTION_DETAIL_ROUTE = "collection"
private const val EQUALIZER_ROUTE = "equalizer"
private const val QUEUE_ROUTE = "queue"
private const val ALBUM_DETAIL_ROUTE = "album"
private const val ARTIST_DETAIL_ROUTE = "artist"

@Composable
fun AuralisNavGraph() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val scrollResets = remember { ScrollResetSignals() }
    // One editor for the whole app: any song or album menu opens it through LocalEditFileActions.
    val editFile: EditFileViewModel = hiltViewModel()
    val editActions = remember(editFile) { EditFileActions(editSong = editFile::openSong, editAlbum = editFile::openAlbum) }

    CompositionLocalProvider(LocalScrollResetSignals provides scrollResets, LocalEditFileActions provides editActions) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // The mini player + bottom tabs hide on the full Now Playing
            // screen, which has its own back affordance instead.
            if (currentRoute != NOW_PLAYING_ROUTE) {
                Column {
                    MiniPlayer(onOpenNowPlaying = { navController.navigateSmart(NOW_PLAYING_ROUTE) })
                    AppBottomBar(navController)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            // These screens use edge-to-edge backdrops and inset their scrollable content
            // themselves, so the backdrop can continue behind system and floating bars.
            modifier = when {
                currentRoute == NOW_PLAYING_ROUTE -> Modifier
                currentRoute?.startsWith("$ALBUM_DETAIL_ROUTE/") == true -> Modifier
                currentRoute?.startsWith("$PLAYLIST_DETAIL_ROUTE/") == true -> Modifier
                currentRoute?.startsWith("$COLLECTION_DETAIL_ROUTE/") == true -> Modifier
                currentRoute == Destination.Playlists.route -> Modifier
                currentRoute == Destination.Settings.route -> Modifier
                else -> Modifier.padding(innerPadding)
            },
            enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 18 } },
            exitTransition = { fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 24 } },
            popEnterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 24 } },
            popExitTransition = { fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 24 } },
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                    onOpenSearch = { navController.openSection(Destination.Search) },
                )
            }
            composable(Destination.Library.route) {
                LibraryScreen(
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                )
            }
            composable(
                route = "$ALBUM_DETAIL_ROUTE/{${AlbumDetailViewModel.ALBUM_ID_ARG}}",
                arguments = listOf(navArgument(AlbumDetailViewModel.ALBUM_ID_ARG) { type = NavType.LongType }),
            ) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    bottomInset = innerPadding.calculateBottomPadding(),
                )
            }
            composable(
                route = "$ARTIST_DETAIL_ROUTE/{${ArtistDetailViewModel.ARTIST_ID_ARG}}",
                arguments = listOf(navArgument(ArtistDetailViewModel.ARTIST_ID_ARG) { type = NavType.LongType }),
            ) {
                ArtistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                )
            }
            composable(Destination.Search.route) {
                SearchScreen(
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                )
            }
            composable(Destination.Playlists.route) {
                PlaylistsScreen(
                    onOpenPlaylist = { id -> navController.navigateSmart("$PLAYLIST_DETAIL_ROUTE/$id") },
                    onOpenCollection = { collection -> navController.navigateSmart("$COLLECTION_DETAIL_ROUTE/${collection.key}") },
                    bottomInset = innerPadding.calculateBottomPadding(),
                )
            }
            composable(
                route = "$COLLECTION_DETAIL_ROUTE/{${SmartCollection.KIND_ARG}}",
                arguments = listOf(navArgument(SmartCollection.KIND_ARG) { type = NavType.StringType }),
            ) { entry ->
                val collection = SmartCollection.fromKey(entry.arguments?.getString(SmartCollection.KIND_ARG))
                if (collection != null) CollectionDetailScreen(
                    collection,
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                    bottomInset = innerPadding.calculateBottomPadding(),
                )
            }
            composable(
                route = "$PLAYLIST_DETAIL_ROUTE/{${PlaylistDetailViewModel.PLAYLIST_ID_ARG}}",
                arguments = listOf(
                    navArgument(PlaylistDetailViewModel.PLAYLIST_ID_ARG) { type = NavType.LongType },
                ),
            ) {
                PlaylistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                    bottomInset = innerPadding.calculateBottomPadding(),
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenEqualizer = { navController.navigateSmart(EQUALIZER_ROUTE) },
                    bottomInset = innerPadding.calculateBottomPadding(),
                )
            }
            composable(EQUALIZER_ROUTE) {
                EqualizerScreen(onBack = { navController.popBackStack() })
            }
            composable(NOW_PLAYING_ROUTE) {
                NowPlayingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigateSmart(QUEUE_ROUTE) },
                    onOpenEqualizer = { navController.navigateSmart(EQUALIZER_ROUTE) },
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                )
            }
            composable(QUEUE_ROUTE) {
                QueueScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { id -> navController.navigateSmart("$ALBUM_DETAIL_ROUTE/$id") },
                    onOpenArtist = { id -> navController.navigateSmart("$ARTIST_DETAIL_ROUTE/$id") },
                )
            }
        }
    }
    EditFileHost(editFile)
    }
}
