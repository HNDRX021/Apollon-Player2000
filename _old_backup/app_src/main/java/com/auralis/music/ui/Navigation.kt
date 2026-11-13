package com.auralis.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auralis.music.data.SongEntity
import com.auralis.music.ui.screens.AlbumScreen
import com.auralis.music.ui.screens.ArtistScreen
import com.auralis.music.ui.screens.EqualizerScreen
import com.auralis.music.ui.screens.FolderManagerScreen
import com.auralis.music.ui.screens.HomeScreen
import com.auralis.music.ui.screens.LibraryScreen
import com.auralis.music.ui.screens.PlayerScreen
import com.auralis.music.ui.screens.PlaylistScreen
import com.auralis.music.ui.screens.PlaylistsScreen
import com.auralis.music.ui.screens.QueueScreen
import com.auralis.music.ui.screens.ScanScreen
import com.auralis.music.ui.screens.SearchScreen
import com.auralis.music.ui.screens.SettingsScreen
import com.auralis.music.ui.screens.SongDetailsScreen
import java.net.URLEncoder

@Composable
fun AuralisRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val isDark = when (settings.theme) {
        "light" -> false
        "system" -> androidx.compose.foundation.isSystemInDarkTheme()
        else -> true
    }
    val nav = rememberNavController()
    androidx.compose.runtime.LaunchedEffect(settings.language) {
        val tag = if (settings.language == "fa") "fa" else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    val dir = if (settings.language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides dir) {
        AuralisTheme(isDark, accentFromHex(settings.accent), settings.uiAnimations) {
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(vm, { nav.navigate(it) { launchSingleTop = true } }) }
                    composable("library") { LibraryScreen(vm, { nav.navigate(it) }) }
                    composable("search") { SearchScreen(vm, { nav.navigate(it) }) }
                    composable("playlists") { PlaylistsScreen(vm, { nav.navigate(it) }) }
                    composable("playlist/{id}") { backStack -> PlaylistScreen(vm, backStack.arguments?.getString("id")?.toLongOrNull() ?: 0, nav::popBackStack) }
                    composable("album/{album}/{artist}") { backStack ->
                        AlbumScreen(
                            vm,
                            decode(backStack.arguments?.getString("album").orEmpty()),
                            decode(backStack.arguments?.getString("artist").orEmpty()),
                            nav::popBackStack
                        )
                    }
                    composable("artist/{name}") { backStack -> ArtistScreen(vm, decode(backStack.arguments?.getString("name").orEmpty()), nav::popBackStack) }
                    composable("song/{id}") { backStack -> SongDetailsScreen(vm, backStack.arguments?.getString("id")?.toLongOrNull() ?: 0, nav::popBackStack) }
                    composable("player") { PlayerScreen(vm, nav::popBackStack, { nav.navigate("queue") }, { nav.navigate("equalizer") }) }
                    composable("queue") { QueueScreen(vm, nav::popBackStack) }
                    composable("equalizer") { EqualizerScreen(vm, nav::popBackStack) }
                    composable("settings") { SettingsScreen(vm, { nav.navigate(it) }) }
                    composable("folders") { FolderManagerScreen(vm, nav::popBackStack) }
                    composable("scan") { ScanScreen(vm, nav::popBackStack) }
                }
            }
        }
    }
}

fun routeForAlbum(album: String, artist: String) = "album/${encode(album)}/${encode(artist)}"
fun routeForArtist(name: String) = "artist/${encode(name)}"
private fun encode(v: String) = URLEncoder.encode(v, Charsets.UTF_8.name())
private fun decode(v: String) = java.net.URLDecoder.decode(v, Charsets.UTF_8.name())
