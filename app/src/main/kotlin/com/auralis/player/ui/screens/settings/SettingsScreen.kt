package com.auralis.player.ui.screens.settings

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.GlassAlertDialog

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.File
import com.auralis.player.ui.components.CropAspect
import com.auralis.player.ui.components.rememberCroppedImagePicker
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCompact
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.auralis.player.R
import com.auralis.player.audio.EqualizerModel
import com.auralis.player.domain.model.LibraryStats
import com.auralis.player.settings.AlbumSort
import com.auralis.player.settings.AppSettings
import com.auralis.player.settings.ArtistSort
import com.auralis.player.settings.DuplicateHandlingMode
import com.auralis.player.settings.SongSort
import com.auralis.player.settings.ThemeMode
import com.auralis.player.ui.components.ScreenHeader
import com.auralis.player.ui.navigation.Destination
import com.auralis.player.ui.navigation.ScrollResetEffect
import com.auralis.player.ui.screens.MusicScreensViewModel
import kotlin.math.roundToInt

/** The selection dialogs of the Settings page; at most one is open at a time. */
private enum class SettingsDialog {
    Theme, Wallpaper, SleepTimer, DuplicateMode, SongSort, AlbumSort, ArtistSort, AudioFormats,
}

private val DefaultSleepTimerChoices = listOf(15, 30, 45, 60, 90, 120)

@Composable
fun SettingsScreen(onOpenEqualizer: () -> Unit, bottomInset: Dp = 0.dp) {
    val model: MusicScreensViewModel = hiltViewModel()
    val settings by model.appSettings.collectAsState(initial = AppSettings())
    val folders by model.folders.collectAsState(initial = emptyList())
    val stats by model.stats.collectAsState(initial = null)
    val scan by model.scanProgress.collectAsState()
    val eq by model.equalizerState.collectAsState()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var folderToRepair by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val repairId = folderToRepair
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val pickedName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Music folder"
            if (repairId != null) {
                model.renewFolderAccess(repairId, uri.toString(), pickedName)
                folderToRepair = null
            } else {
                model.addFolder(uri.toString(), pickedName)
            }
        }
        folderToRepair = null
    }
    // Photos (the gallery) opens first; the picked picture then goes through the crop screen, and the
    // result is stored privately so the wallpaper keeps working without any file permission.
    val pickBackground = rememberCroppedImagePicker(
        title = "Wallpaper",
        initialAspect = CropAspect.Original,
        maxOutputEdge = 3072,
    ) { jpeg ->
        val previous = settings.backgroundImageUri
        val saved = runCatching { saveWallpaper(context, jpeg) }.getOrNull()
        if (saved != null) {
            model.setBackgroundImageUri(saved)
            deleteOwnWallpaper(context, previous)
        } else {
            Toast.makeText(context, "The wallpaper couldn't be saved.", Toast.LENGTH_SHORT).show()
        }
    }
    val listState = rememberLazyListState()
    ScrollResetEffect(Destination.Settings.route) { listState.scrollToItem(0) }

    fun closeSearch() {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching) { closeSearch() }

    // Search filters rows by keyword; with an empty query every row is shown.
    val needle = query.trim()
    fun hit(vararg terms: String): Boolean = needle.isEmpty() || terms.any { it.contains(needle, ignoreCase = true) }

    val eqStatus = if (!eq.enabled) {
        "Off"
    } else {
        eq.selectedPresetId?.let { id -> EqualizerModel.builtInById(id)?.englishName ?: EqualizerModel.customNameOf(id) } ?: "Custom"
    }
    val folderSubtitle = when {
        scan.isScanning -> "Scanning ${scan.currentName.orEmpty()} · ${scan.processed} files"
        folders.isEmpty() -> "No folders added yet"
        else -> "${folders.size} ${if (folders.size == 1) "folder" else "folders"} · Tap to add another"
    }
    val hasWallpaper = settings.backgroundImageUri != null

    // --- Appearance ----------------------------------------------------------------------------
    val appearanceRows = buildList<@Composable () -> Unit> {
        if (hit("theme", "appearance", "dark", "light", "system")) add {
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                title = "Theme",
                value = themeLabel(settings.themeMode),
                onClick = { dialog = SettingsDialog.Theme },
            )
        }
        if (hit("accent color", "colour", "color", "purple", "red", "orange", "amber", "green", "blue", "pink", "custom")) add {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = SettingsTokens.RowHorizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIconTile(Icons.Outlined.Palette)
                    Spacer(Modifier.width(SettingsTokens.IconGap))
                    Text("Accent color", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        accentDisplayName(settings.accentColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AccentColorPicker(
                    selected = settings.accentColor,
                    onSelect = { color ->
                        // Wallpaper colors would override the accent, so picking one switches that off.
                        if (settings.dynamicColorEnabled) model.setDynamicColorEnabled(false)
                        model.setAccentColor(color)
                    },
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hit("wallpaper colors", "dynamic", "material you", "color")) add {
            SettingsToggleRow(
                icon = Icons.Outlined.ColorLens,
                title = "Wallpaper colors",
                subtitle = "Use your system wallpaper's colors",
                checked = settings.dynamicColorEnabled,
                onCheckedChange = { model.setDynamicColorEnabled(it) },
            )
        }
        if (hit("wallpaper", "background", "image")) add {
            SettingsRow(
                icon = Icons.Outlined.Wallpaper,
                title = "Wallpaper",
                subtitle = if (hasWallpaper) "Custom image" else "Default",
                onClick = { dialog = SettingsDialog.Wallpaper },
                trailing = {
                    WallpaperThumbnail(
                        uri = settings.backgroundImageUri,
                        modifier = Modifier.size(width = 44.dp, height = 32.dp),
                        shape = RoundedCornerShape(8.dp),
                    )
                },
            )
        }
    }

    // --- Playback ------------------------------------------------------------------------------
    val playbackRows = buildList<@Composable () -> Unit> {
        if (hit("equalizer", "eq", "sound", "preset", "bass")) add {
            SettingsRow(
                icon = Icons.Outlined.Equalizer,
                title = "Equalizer",
                value = eqStatus,
                onClick = onOpenEqualizer,
            )
        }
        if (hit("crossfade", "blend", "transition")) add {
            SettingsToggleRow(
                icon = Icons.Outlined.SwapHoriz,
                title = "Crossfade",
                subtitle = "Blend consecutive tracks",
                checked = settings.crossfadeEnabled,
                onCheckedChange = { model.setCrossfadeEnabled(it) },
            )
        }
        if (hit("replaygain", "replay gain", "normalization", "loudness", "volume")) add {
            SettingsToggleRow(
                icon = Icons.Outlined.GraphicEq,
                title = "ReplayGain normalization",
                checked = settings.replayGainEnabled,
                onCheckedChange = { model.setReplayGainEnabled(it) },
            )
        }
        if (hit("previous", "restart", "track", "beginning")) add {
            SettingsToggleRow(
                icon = Icons.Outlined.SkipPrevious,
                title = "Restart track when Previous is pressed",
                subtitle = "Near the beginning",
                checked = settings.restartTrackOnPrevious,
                onCheckedChange = { model.setRestartTrackOnPrevious(it) },
            )
        }
        if (hit("pause", "headphones", "disconnect", "unplug", "bluetooth")) add {
            SettingsToggleRow(
                icon = Icons.Outlined.Headphones,
                title = "Pause when headphones disconnect",
                checked = settings.pauseOnAudioBecomingNoisy,
                onCheckedChange = { model.setPauseOnAudioBecomingNoisy(it) },
            )
        }
        if (hit("sleep timer", "sleep", "timer", "minutes")) add {
            SettingsRow(
                icon = Icons.Outlined.Bedtime,
                title = "Sleep timer",
                value = "${settings.defaultSleepTimerMinutes} min",
                onClick = { dialog = SettingsDialog.SleepTimer },
            )
        }
        if (hit("crossfade length", "crossfade", "length", "seconds", "duration")) add {
            CrossfadeLengthRow(
                seconds = settings.crossfadeSeconds,
                enabled = settings.crossfadeEnabled,
                onCommit = { seconds -> if (seconds != settings.crossfadeSeconds) model.setCrossfadeSeconds(seconds) },
            )
        }
    }

    // --- Music Library -------------------------------------------------------------------------
    val libraryRows = buildList<@Composable () -> Unit> {
        if (hit("music folders", "folder", "folders", "library", "scan", "add", "import")) add {
            SettingsRow(
                icon = Icons.Outlined.Folder,
                title = "Music folders",
                subtitle = folderSubtitle,
                onClick = { folderToRepair = null; folderPicker.launch(null) },
            )
        }
        folders.forEach { folder ->
            if (hit("music", "folder", folder.displayName, "rescan", "remove", "included", "library")) add {
                MusicSourceRow(
                    name = folder.displayName,
                    enabled = folder.isEnabled,
                    scanning = scan.isScanning && scan.folderId == folder.id,
                    scanText = "Scanning ${scan.currentName.orEmpty()} · ${scan.processed} files",
                    needsAccessRepair = scan.lastScanFailed && scan.failedFolderId == folder.id,
                    onEnabled = { model.setFolderEnabled(folder.id, it) },
                    onRescan = { model.rescanFolder(folder.id) },
                    onCancelScan = { model.cancelFolderScan(folder.id); model.cancelRefresh() },
                    onGrantAccess = { folderToRepair = folder.id; folderPicker.launch(null) },
                    onRemove = { model.removeFolder(folder.id) },
                )
            }
        }
        stats?.let { summary ->
            if (hit("library storage", "storage", "songs", "albums", "artists", "playlists", "size", "duration", "library")) add {
                LibraryStorageRow(summary)
            }
        }
    }

    // --- Library preferences (existing options, kept) ------------------------------------------
    val preferenceRows = buildList<@Composable () -> Unit> {
        if (hit("compact song rows", "compact", "rows", "density")) add {
            SettingsToggleRow(
                icon = Icons.Outlined.ViewCompact,
                title = "Compact song rows",
                checked = settings.compactMode,
                onCheckedChange = { model.setCompactMode(it) },
            )
        }
        if (hit(".nomedia", "nomedia", "respect", "folders", "hidden")) add {
            SettingsToggleRow(
                icon = Icons.Outlined.VisibilityOff,
                title = "Respect .nomedia folders",
                checked = settings.respectNoMedia,
                onCheckedChange = { model.setRespectNoMedia(it) },
            )
        }
        if (hit("duplicate handling", "duplicate", "duplicates")) add {
            SettingsRow(
                icon = Icons.Outlined.ContentCopy,
                title = "Duplicate handling",
                subtitle = stringResource(
                    if (settings.duplicateHandlingMode == DuplicateHandlingMode.SKIP_IDENTICAL_CONTENT) R.string.settings_duplicates_content else R.string.settings_duplicates_document,
                ),
                onClick = { dialog = SettingsDialog.DuplicateMode },
            )
        }
        if (hit("song order", "sort", "songs", "order")) add {
            SettingsRow(
                icon = Icons.Outlined.SortByAlpha,
                title = "Song order",
                value = settings.songSort.displayName(),
                onClick = { dialog = SettingsDialog.SongSort },
            )
        }
        if (hit("album order", "sort", "albums", "order")) add {
            SettingsRow(
                icon = Icons.Outlined.Album,
                title = "Album order",
                value = settings.albumSort.displayName(),
                onClick = { dialog = SettingsDialog.AlbumSort },
            )
        }
        if (hit("artist order", "sort", "artists", "order")) add {
            SettingsRow(
                icon = Icons.Outlined.Person,
                title = "Artist order",
                value = settings.artistSort.displayName(),
                onClick = { dialog = SettingsDialog.ArtistSort },
            )
        }
        if (hit("audio formats", "formats", "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus")) add {
            SettingsRow(
                icon = Icons.Outlined.AudioFile,
                title = "Audio formats",
                value = "${settings.enabledAudioFormats.size} of ${AppSettings.DEFAULT_AUDIO_FORMATS.size}",
                onClick = { dialog = SettingsDialog.AudioFormats },
            )
        }
    }

    // --- Notifications -------------------------------------------------------------------------
    val notificationRows = buildList<@Composable () -> Unit> {
        if (hit("notifications", "notification", "sound", "android settings")) add {
            NotificationsRow(
                onOpen = {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra("android.provider.extra.APP_PACKAGE", context.packageName),
                        )
                    }
                },
            )
        }
    }

    val nothingFound = needle.isNotEmpty() &&
        listOf(appearanceRows, playbackRows, libraryRows, preferenceRows, notificationRows).all { it.isEmpty() }

    // Keep settings on the same full-screen app backdrop as the floating player and navigation bar.
    // Content still respects system bars, and reserves scroll space for the bottom panels.
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            state = listState,
            contentPadding = PaddingValues(bottom = 28.dp + bottomInset),
        ) {
            item {
                ScreenHeader(
                    title = "Settings",
                    action = {
                        GlassToolbarButton(onClick = { if (searching) closeSearch() else searching = true }) {
                            Icon(
                                if (searching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searching) "Close search" else "Search settings",
                            )
                        }
                    },
                )
            }
            item {
                AnimatedVisibility(
                    visible = searching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    SettingsSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (appearanceRows.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsSectionLabel("Appearance")
                    SettingsCard(appearanceRows)
                }
            }
            if (playbackRows.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsSectionLabel("Playback")
                    SettingsCard(playbackRows)
                }
            }
            if (libraryRows.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsSectionLabel("Music Library")
                    SettingsCard(libraryRows)
                }
            }
            if (preferenceRows.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsSectionLabel("Library preferences")
                    SettingsCard(preferenceRows)
                    Text(
                        "Format and duplicate changes apply to new scans.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
            }
            if (notificationRows.isNotEmpty()) item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)) {
                    SettingsCard(notificationRows)
                }
            }
            if (nothingFound) item {
                Text(
                    "No settings match “$needle”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
                )
            }
        }
    }

    // --- Dialogs -------------------------------------------------------------------------------
    when (dialog) {
        SettingsDialog.Theme -> ChoiceDialog(
            title = "Theme",
            options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).map { it to themeLabel(it) },
            selected = settings.themeMode,
            onSelect = { model.setThemeMode(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Wallpaper -> GlassAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.app_background)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WallpaperThumbnail(
                        uri = settings.backgroundImageUri,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(14.dp),
                        maxDecodeSize = 640.dp,
                    )
                    Text(
                        if (hasWallpaper) "Your image is shown behind the app." else "Choose an image to show behind the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                GlassTextButton(onClick = { dialog = null; pickBackground() }) {
                    Text(stringResource(R.string.choose_background_image))
                }
            },
            dismissButton = {
                if (hasWallpaper) {
                    GlassTextButton(onClick = { deleteOwnWallpaper(context, settings.backgroundImageUri); model.setBackgroundImageUri(null); dialog = null }) {
                        Text(stringResource(R.string.remove_background_image))
                    }
                } else {
                    GlassTextButton(onClick = { dialog = null }) { Text("Close") }
                }
            },
        )
        SettingsDialog.SleepTimer -> ChoiceDialog(
            title = "Sleep timer",
            options = (DefaultSleepTimerChoices + settings.defaultSleepTimerMinutes).distinct().sorted().map { it to "$it minutes" },
            selected = settings.defaultSleepTimerMinutes,
            onSelect = { model.setDefaultSleepTimerMinutes(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.DuplicateMode -> ChoiceDialog(
            title = "Duplicate handling",
            options = listOf(
                DuplicateHandlingMode.SAF_IDENTITY_ONLY to stringResource(R.string.settings_duplicates_document),
                DuplicateHandlingMode.SKIP_IDENTICAL_CONTENT to stringResource(R.string.settings_duplicates_content),
            ),
            selected = settings.duplicateHandlingMode,
            onSelect = { model.setDuplicateHandlingMode(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SongSort -> ChoiceDialog(
            title = "Song order",
            options = SongSort.entries.map { it to it.displayName() },
            selected = settings.songSort,
            onSelect = { model.setSongSort(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.AlbumSort -> ChoiceDialog(
            title = "Album order",
            options = AlbumSort.entries.map { it to it.displayName() },
            selected = settings.albumSort,
            onSelect = { model.setAlbumSort(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.ArtistSort -> ChoiceDialog(
            title = "Artist order",
            options = ArtistSort.entries.map { it to it.displayName() },
            selected = settings.artistSort,
            onSelect = { model.setArtistSort(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.AudioFormats -> GlassAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Audio formats") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppSettings.DEFAULT_AUDIO_FORMATS.sorted().forEach { extension ->
                        val enabled = extension in settings.enabledAudioFormats
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = enabled,
                                    role = Role.Switch,
                                    onValueChange = { model.setAudioFormatEnabled(extension, it) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(extension.uppercase(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            SettingsSwitch(checked = enabled, onCheckedChange = null)
                        }
                    }
                }
            },
            confirmButton = { GlassTextButton(onClick = { dialog = null }) { Text("Done") } },
        )
        null -> Unit
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/** "DATE_ADDED" -> "Date added". */
private fun Enum<*>.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val ink = MaterialTheme.colorScheme.onSurface
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { Text("Search settings") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                GlassToolbarButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ink.copy(alpha = 0.08f),
            unfocusedContainerColor = ink.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Crossfade duration: the current value under the title and a slider in the accent colour. The value
 * shown follows the thumb while dragging and is saved when the drag ends. The slider keeps the
 * application's supported range and is inactive while Crossfade is off.
 */
@Composable
private fun CrossfadeLengthRow(seconds: Int, enabled: Boolean, onCommit: (Int) -> Unit) {
    var sliderValue by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    val shown = sliderValue.roundToInt()
    val scheme = MaterialTheme.colorScheme
    val description = "Crossfade length, $shown seconds"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsTokens.RowHorizontalPadding)
            .padding(top = 12.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIconTile(Icons.Outlined.Tune)
            Spacer(Modifier.width(SettingsTokens.IconGap))
            Column(Modifier.weight(1f)) {
                Text(
                    "Crossfade length",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    if (shown == 1) "1 second" else "$shown seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onCommit(sliderValue.roundToInt()) },
            valueRange = AppSettings.MIN_CROSSFADE_SECONDS.toFloat()..AppSettings.MAX_CROSSFADE_SECONDS.toFloat(),
            steps = AppSettings.MAX_CROSSFADE_SECONDS - AppSettings.MIN_CROSSFADE_SECONDS - 1,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                activeTickColor = Color.Transparent,
                inactiveTrackColor = scheme.onSurface.copy(alpha = 0.14f),
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/**
 * One music source (folder): its name, an "Included in library" switch, and small muted actions
 * (Rescan / Cancel scan / Grant access / Remove) that act only on this source.
 */
@Composable
private fun MusicSourceRow(
    name: String,
    enabled: Boolean,
    scanning: Boolean,
    scanText: String,
    needsAccessRepair: Boolean,
    onEnabled: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onCancelScan: () -> Unit,
    onGrantAccess: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        SettingsToggleRow(
            icon = Icons.Outlined.MusicNote,
            title = name,
            subtitle = if (enabled) "Included in library" else "Paused",
            checked = enabled,
            onCheckedChange = onEnabled,
        )
        Row(
            // GlassTextButtons carry 16 dp of inner padding, so the first label lines up with the title text.
            Modifier.padding(start = SettingsTokens.TextInset - 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scanning) {
                GlassTextButton(onClick = onCancelScan, muted = true) {
                    Text(stringResource(R.string.scan_cancel), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                GlassTextButton(onClick = onRescan, muted = true) {
                    Text("Rescan", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (needsAccessRepair) {
                GlassTextButton(onClick = onGrantAccess) {
                    Text(stringResource(R.string.folder_grant_access), style = MaterialTheme.typography.labelLarge)
                }
            }
            GlassTextButton(onClick = onRemove, muted = true) {
                Text("Remove", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (scanning) {
            Column(Modifier.padding(start = SettingsTokens.TextInset, end = SettingsTokens.RowHorizontalPadding, bottom = 12.dp)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.primary,
                    trackColor = scheme.onSurface.copy(alpha = 0.10f),
                )
                Text(
                    scanText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (needsAccessRepair) {
            Text(
                stringResource(R.string.scan_failed_folder, name),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
                modifier = Modifier.padding(start = SettingsTokens.TextInset, end = SettingsTokens.RowHorizontalPadding, bottom = 12.dp),
            )
        }
    }
}

/** The library totals, straight from the database statistics, as a compact two-row grid. */
@Composable
private fun LibraryStorageRow(stats: LibraryStats) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsTokens.RowHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIconTile(Icons.Outlined.Storage)
            Spacer(Modifier.width(SettingsTokens.IconGap))
            Text("Library storage", style = MaterialTheme.typography.bodyLarge)
        }
        Column(
            Modifier.padding(start = SettingsTokens.IconTileSize + SettingsTokens.IconGap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                StatCell("${stats.songCount}", "Songs", Modifier.weight(1f))
                StatCell("${stats.albumCount}", "Albums", Modifier.weight(1f))
                StatCell("${stats.artistCount}", "Artists", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatCell("${stats.playlistCount}", "Playlists", Modifier.weight(1f))
                StatCell(formatBytes(stats.totalSizeBytes), "Size", Modifier.weight(1f))
                StatCell(formatDurationHours(stats.totalDurationMs), "Duration", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun NotificationsRow(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = SettingsTokens.RowHorizontalPadding, end = SettingsTokens.RowHorizontalPadding, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            SettingsIconTile(Icons.Outlined.Notifications)
            Spacer(Modifier.width(SettingsTokens.IconGap))
            Column(Modifier.weight(1f)) {
                Text("Notifications", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Control playback notification visibility and sound in Android settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(
            Modifier
                .padding(start = SettingsTokens.IconTileSize + SettingsTokens.IconGap)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_notification_options),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.padding(start = 2.dp).size(20.dp),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes bytes"
}

private fun formatDurationHours(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0) / 60_000L
    return "${totalMinutes / 60} h ${totalMinutes % 60} m"
}

/** Stores the cropped wallpaper in the app's private files and returns its `file:` URI. */
private fun saveWallpaper(context: Context, jpeg: ByteArray): String {
    val directory = File(context.filesDir, "wallpapers").also { it.mkdirs() }
    val file = File(directory, "bg-${System.currentTimeMillis()}.jpg")
    file.writeBytes(jpeg)
    return Uri.fromFile(file).toString()
}

/** Deletes a wallpaper this app saved itself (an older picked-from-Files URI is left alone). */
private fun deleteOwnWallpaper(context: Context, uriString: String?) {
    if (uriString == null) return
    val uri = Uri.parse(uriString)
    if (uri.scheme != "file") return
    val file = File(uri.path ?: return)
    if (file.parentFile == File(context.filesDir, "wallpapers")) file.delete()
}
