package com.auralis.player.ui.screens.edit

import com.auralis.player.ui.components.GlassToolbarButton
import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.GlassAlertDialog
import com.auralis.player.ui.components.GlassButton
import com.auralis.player.ui.components.GlassButtonStyle
import com.auralis.player.ui.components.GlassDropdownMenu
import com.auralis.player.ui.components.GlassMenuEntry

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.auralis.player.ui.components.ArtworkImage

private val WideBreakpoint = 720.dp
private val FieldShape = RoundedCornerShape(12.dp)

/**
 * The "Edit File" editor for a song or a whole album. It is a full-screen sheet on phones and a centred
 * card on wide screens, and it is hosted once at the top of the app, so it opens over whatever screen
 * the three-dot menu was used on.
 */
@Composable
fun EditFileHost(vm: EditFileViewModel) {
    val state by vm.state.collectAsState()
    val genres by vm.genres.collectAsState()
    if (state.target == null) return

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.pickArtwork(uri)
    }
    Dialog(
        onDismissRequest = vm::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        EditorSurface(state, genres, vm) { picker.launch("image/*") }
    }
    state.confirm?.let { confirmation ->
        ConfirmSaveDialog(confirmation, onConfirm = vm::confirmSave, onDismiss = vm::dismissConfirm)
    }
    if (state.askDiscard) {
        GlassAlertDialog(
            onDismissRequest = vm::keepEditing,
            title = { Text("Discard changes?") },
            text = { Text("Your edits haven't been saved and will be lost.") },
            confirmButton = { GlassTextButton(onClick = vm::discardAndClose) { Text("Discard") } },
            dismissButton = { GlassTextButton(onClick = vm::keepEditing) { Text("Keep Editing") } },
        )
    }
}

@Composable
private fun EditorSurface(
    state: EditUiState,
    genres: List<String>,
    vm: EditFileViewModel,
    onPickArtwork: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val wide = maxWidth >= WideBreakpoint
        Surface(
            modifier = if (wide) {
                Modifier.widthIn(max = 980.dp).fillMaxWidth(0.94f).fillMaxHeight(0.92f)
            } else {
                Modifier.fillMaxSize().systemBarsPadding()
            },
            shape = if (wide) RoundedCornerShape(20.dp) else RectangleShape,
            color = scheme.surface,
            contentColor = scheme.onSurface,
            border = if (wide) BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)) else null,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassToolbarButton(onClick = vm::requestClose, enabled = !state.saving) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            if (state.isAlbum) "Edit Album" else "Edit File",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            state.heading,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                when {
                    state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.loadError != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.loadError, color = scheme.onSurfaceVariant)
                    }
                    else -> EditorBody(Modifier.weight(1f), state, genres, vm, wide, onPickArtwork)
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    state.saveError?.let {
                        Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        GlassTextButton(onClick = vm::requestClose, enabled = !state.saving) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        GlassButton(onClick = vm::requestSave, enabled = state.canSave) {
                            if (state.saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Saving…")
                            } else {
                                Text("Save Changes")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorBody(
    modifier: Modifier,
    state: EditUiState,
    genres: List<String>,
    vm: EditFileViewModel,
    wide: Boolean,
    onPickArtwork: () -> Unit,
) {
    val enabled = state.editable && !state.saving
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.notes.forEach { NoteBanner(it) }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                ArtworkSection(state, enabled, vm, onPickArtwork, Modifier.width(260.dp))
                MetadataFields(state, genres, vm, enabled, compact = false, modifier = Modifier.weight(1f))
            }
        } else {
            ArtworkSection(state, enabled, vm, onPickArtwork, Modifier.fillMaxWidth())
            MetadataFields(state, genres, vm, enabled, compact = true, modifier = Modifier.fillMaxWidth())
        }
        if (state.isAlbum) TracksSection(state, vm, enabled)
    }
}

@Composable
private fun NoteBanner(text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = FieldShape, color = scheme.onSurface.copy(alpha = 0.06f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

// --- Artwork ----------------------------------------------------------------------------------

@Composable
private fun ArtworkSection(
    state: EditUiState,
    enabled: Boolean,
    vm: EditFileViewModel,
    onPick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val showOverlay = enabled && (hovered || pressed || focused)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .hoverable(interaction)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Change artwork",
                    onClick = onPick,
                ),
        ) {
            when (val edit = state.artworkEdit) {
                is ArtworkChoice.Replaced -> ReplacedPreview(edit.jpeg, shape)
                ArtworkChoice.Removed -> ArtworkImage(null, Modifier.fillMaxSize(), targetSize = 260.dp, shape = shape)
                ArtworkChoice.Unchanged -> ArtworkImage(state.artworkPath, Modifier.fillMaxSize(), targetSize = 300.dp, shape = shape)
            }
            val overlayAlpha by animateFloatAsState(if (showOverlay) 1f else 0f, tween(150), label = "artworkOverlay")
            if (overlayAlpha > 0f) {
                Box(
                    Modifier.fillMaxSize().alpha(overlayAlpha).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color.White)
                        Text("Change Artwork", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            // Always visible, so touch screens (which have no hover) can see the cover is editable.
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.widthIn(max = 260.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassButton(onClick = onPick, style = GlassButtonStyle.Secondary, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Change Artwork")
            }
            GlassTextButton(onClick = vm::removeArtwork, enabled = enabled && state.showsArtwork) { Text("Remove Artwork") }
            if (state.canRestoreArtwork) {
                GlassTextButton(onClick = vm::restoreArtwork, enabled = enabled) { Text("Restore Original Artwork") }
            }
        }
    }
}

@Composable
private fun ReplacedPreview(jpeg: ByteArray, shape: androidx.compose.ui.graphics.Shape) {
    val bitmap = remember(jpeg) { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap() }
    Box(Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "New artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// --- Fields -----------------------------------------------------------------------------------------

@Composable
private fun MetadataFields(
    state: EditUiState,
    genres: List<String>,
    vm: EditFileViewModel,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    @Composable
    fun field(label: String, field: EditField, mod: Modifier = Modifier, numeric: Boolean = false, multiline: Boolean = false) {
        val value = state.fields[field] ?: FieldState()
        TagTextField(
            label = label,
            state = value,
            onChange = { vm.setField(field, it) },
            modifier = mod,
            enabled = enabled,
            numeric = numeric,
            multiline = multiline,
            error = validateField(field, value),
        )
    }

    @Composable
    fun genre(mod: Modifier) {
        GenreField(state.fields[EditField.Genre] ?: FieldState(), genres, enabled, mod) { vm.setField(EditField.Genre, it) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!state.isAlbum) {
            field("Title", EditField.Title, Modifier.fillMaxWidth())
            field("Artist", EditField.Artist, Modifier.fillMaxWidth())
            field("Album", EditField.Album, Modifier.fillMaxWidth())
            if (compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    field("Year", EditField.Year, Modifier.weight(1f), numeric = true)
                    field("Track Number", EditField.TrackNumber, Modifier.weight(1f), numeric = true)
                }
                genre(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    field("Year", EditField.Year, Modifier.weight(1f), numeric = true)
                    field("Track Number", EditField.TrackNumber, Modifier.weight(1f), numeric = true)
                    genre(Modifier.weight(1.6f))
                }
            }
            field("Comment", EditField.Comment, Modifier.fillMaxWidth(), multiline = true)
            field("Album Artist", EditField.AlbumArtist, Modifier.fillMaxWidth())
            field("Composer", EditField.Composer, Modifier.fillMaxWidth())
            field("Disc Number", EditField.DiscNumber, Modifier.fillMaxWidth(), numeric = true)
        } else {
            field("Album", EditField.Album, Modifier.fillMaxWidth())
            field("Album Artist", EditField.AlbumArtist, Modifier.fillMaxWidth())
            if (compact) {
                field("Year", EditField.Year, Modifier.fillMaxWidth(), numeric = true)
                genre(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    field("Year", EditField.Year, Modifier.weight(1f), numeric = true)
                    genre(Modifier.weight(1.6f))
                }
            }
            field("Composer", EditField.Composer, Modifier.fillMaxWidth())
            field("Comment", EditField.Comment, Modifier.fillMaxWidth(), multiline = true)
        }
        // The location is shown, never edited: Auralis doesn't move files.
        TagTextField(
            label = "Directory",
            state = FieldState(state.directory, state.directory),
            onChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = true,
            readOnly = true,
        )
    }
}

@Composable
private fun GenreField(
    state: FieldState,
    genres: List<String>,
    enabled: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TagTextField(
            label = "Genre",
            state = state,
            onChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            trailing = {
                GlassToolbarButton(onClick = { open = true }, enabled = enabled && genres.isNotEmpty()) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose an existing genre")
                }
            },
        )
        GlassDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            entries = genres.map { name -> GlassMenuEntry(name, onClick = { onChange(name) }, autoIcon = false) },
        )
    }
}

@Composable
private fun TagTextField(
    label: String,
    state: FieldState,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    numeric: Boolean = false,
    multiline: Boolean = false,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            // A label in the accent colour marks a field that has been edited.
            color = if (state.changed) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        TextField(
            value = state.text,
            onValueChange = { onChange(if (numeric) it.filter(Char::isDigit).take(4) else it) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 8 else 1,
            placeholder = { if (state.mixed) Text("Multiple Values") },
            trailingIcon = trailing,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            shape = FieldShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ink.copy(alpha = 0.08f),
                unfocusedContainerColor = ink.copy(alpha = 0.06f),
                disabledContainerColor = ink.copy(alpha = 0.04f),
                errorContainerColor = scheme.error.copy(alpha = 0.08f),
                focusedIndicatorColor = scheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = scheme.error,
            ),
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

// --- Album tracks ---------------------------------------------------------------------------------------

@Composable
private fun TracksSection(state: EditUiState, vm: EditFileViewModel, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tracks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${state.tracks.size} tracks. Changes made here apply to that track only.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        state.tracks.forEachIndexed { index, track ->
            TrackEditCard(index + 1, track, enabled && track.editable, vm)
        }
    }
}

@Composable
private fun TrackEditCard(position: Int, track: TrackEdit, enabled: Boolean, vm: EditFileViewModel) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.onSurface.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$position", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    track.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!track.editable && track.note != null) {
                Text(track.note, style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
            TagTextField(
                "Title", track.title, { vm.setTrackField(track.songId, EditField.Title, it) },
                Modifier.fillMaxWidth(), enabled, error = validateField(EditField.Title, track.title),
            )
            TagTextField(
                "Artist", track.artist, { vm.setTrackField(track.songId, EditField.Artist, it) },
                Modifier.fillMaxWidth(), enabled,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TagTextField(
                    "Track Number", track.trackNumber, { vm.setTrackField(track.songId, EditField.TrackNumber, it) },
                    Modifier.weight(1f), enabled, numeric = true, error = validateField(EditField.TrackNumber, track.trackNumber),
                )
                TagTextField(
                    "Disc Number", track.discNumber, { vm.setTrackField(track.songId, EditField.DiscNumber, it) },
                    Modifier.weight(1f), enabled, numeric = true, error = validateField(EditField.DiscNumber, track.discNumber),
                )
            }
        }
    }
}

// --- Dialogs ----------------------------------------------------------------------------------------------

@Composable
private fun ConfirmSaveDialog(confirmation: SaveConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save changes to ${confirmation.trackCount} ${if (confirmation.trackCount == 1) "track" else "tracks"}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This rewrites the tags inside the audio files themselves. Auralis keeps no backup of the old tags.")
                if (confirmation.overwrittenFields.isNotEmpty()) {
                    Text("These fields currently differ between tracks and will be set to one value on every track: ${confirmation.overwrittenFields.joinToString(", ")}.")
                }
                if (confirmation.removesArtwork) Text("The embedded cover will be removed from every track.")
                if (confirmation.skippedTracks > 0) {
                    Text("${confirmation.skippedTracks} ${if (confirmation.skippedTracks == 1) "track" else "tracks"} can't be edited and will be skipped.")
                }
                if (confirmation.playingWarning) Text("A track from this album is playing; its playback may skip while its file is saved.")
            }
        },
        confirmButton = { GlassTextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
