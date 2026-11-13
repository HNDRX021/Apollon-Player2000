package com.auralis.player.ui.screens.playlists

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.auralis.player.ui.components.GlassAlertDialog
import com.auralis.player.ui.components.GlassTextButton
import com.auralis.player.ui.components.glassTextFieldColors
import com.auralis.player.domain.model.Playlist

private const val MAX_PLAYLIST_NAME_LENGTH = 80

/**
 * Name entry for creating or renaming a playlist. The field is focused (and, when renaming, the old name
 * selected) as soon as the dialog opens, the keyboard's Done key saves, and a blank name cannot be saved.
 */
@Composable
internal fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    initial: String = "",
    onConfirm: (String) -> Unit,
) {
    var field by remember(title, initial) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    val canSave = field.text.isNotBlank()
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = field,
                onValueChange = { if (it.text.length <= MAX_PLAYLIST_NAME_LENGTH) field = it },
                label = { Text("Playlist name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = glassTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(field.text.trim()) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            GlassTextButton(onClick = { onConfirm(field.text.trim()) }, enabled = canSave) { Text(confirmLabel) }
        },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks before deleting [playlist]; the songs stay in the library either way. */
@Composable
internal fun DeletePlaylistDialog(playlist: Playlist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${playlist.name}?") },
        text = { Text("The playlist is removed. Your songs stay in the library.") },
        confirmButton = { GlassTextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
