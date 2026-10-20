package com.blaze.photobooth.ui.feature.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.StripPreview
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.PillShape
import com.blaze.photobooth.ui.theme.SheetShape
import com.blaze.photobooth.ui.theme.handwritten

/**
 * Manage frames (README §4.8): her bundled frames (hide or show) and the ones she added on the phone (rename, move up
 * or down, hide or show, delete). Hidden frames are dimmed and leave Setup's carousel.
 */
@Composable
fun ManageFramesScreen(
    state: ManageFramesUiState,
    onEvent: (ManageFramesEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val extra = BoothTheme.extraColors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = { onEvent(ManageFramesEvent.Back) })
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "bundled-header") {
                    SectionHeader(stringResource(R.string.manage_bundled), stringResource(R.string.manage_bundled_note))
                }
                items(state.bundled, key = { it.id.value }) { row -> FrameRow(row, first = false, last = false, onEvent) }
                item(key = "imported-header") { SectionHeader(stringResource(R.string.manage_imported), null) }
                if (state.loaded && state.imported.isEmpty()) {
                    item(key = "empty") { EmptyImported() }
                }
                items(state.imported, key = { it.id.value }) { row ->
                    FrameRow(row, first = row == state.imported.first(), last = row == state.imported.last(), onEvent)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SheetShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) {
                SquishyButton(
                    onClick = { onEvent(ManageFramesEvent.Add) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.manage_add), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp),
        )
    }
    state.renaming?.let { RenameDialog(state.renameText, onEvent) }
    state.deleting?.let { DeleteDialog(it, onEvent) }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = BoothTheme.extraColors.raised,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.manage_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.manage_note),
                    style = MaterialTheme.typography.titleSmall.handwritten(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, note: String?) {
    Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyImported() {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(
            stringResource(R.string.manage_imported_empty),
            style = MaterialTheme.typography.titleMedium.handwritten(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        )
    }
}

/** A swatch, the name and window count (plus a *Hidden* pill), then the row's buttons. */
@Composable
private fun FrameRow(row: ManagedFrameRow, first: Boolean, last: Boolean, onEvent: (ManageFramesEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val renameLabel = stringResource(R.string.cd_manage_rename, row.name)
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (row.bundled) {
                        Modifier
                    } else {
                        Modifier.clickable(onClickLabel = renameLabel, role = Role.Button) { onEvent(ManageFramesEvent.Rename(row.id)) }
                    },
                )
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dim = if (row.hidden) Modifier.alpha(0.45f) else Modifier
            Box(
                Modifier
                    .size(width = 44.dp, height = 72.dp)
                    .then(dim),
                contentAlignment = Alignment.Center,
            ) {
                val thumb = row.thumbnail
                if (thumb != null) {
                    val spec = remember(row.frame, thumb) { StripSpec(FrameSource.Image(row.frame, thumb), preview = true) }
                    StripPreview(spec, Modifier.height(72.dp))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(dim),
                    )
                    if (!row.bundled) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(14.dp),
                        )
                    }
                }
                Text(
                    pluralStringResource(R.plurals.manage_windows, row.windows, row.windows),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // Its own line: imported rows have four buttons, which leaves too little width beside the count.
                if (row.hidden) {
                    Surface(shape = PillShape, color = colors.secondaryContainer, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            stringResource(R.string.manage_hidden),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (!row.bundled) {
                IconButton(onClick = { onEvent(ManageFramesEvent.Move(row.id, -1)) }, enabled = !first) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_manage_up, row.name))
                }
                IconButton(onClick = { onEvent(ManageFramesEvent.Move(row.id, +1)) }, enabled = !last) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_manage_down, row.name))
                }
            }
            IconButton(onClick = { onEvent(ManageFramesEvent.SetHidden(row.id, !row.hidden)) }) {
                Icon(
                    if (row.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (row.hidden) R.string.cd_manage_show else R.string.cd_manage_hide, row.name),
                    tint = if (row.hidden) colors.onSurfaceVariant else colors.primary,
                )
            }
            if (!row.bundled) {
                IconButton(onClick = { onEvent(ManageFramesEvent.Delete(row.id)) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.cd_manage_delete, row.name), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(text: String, onEvent: (ManageFramesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ManageFramesEvent.CancelRename) },
        title = { Text(stringResource(R.string.manage_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { onEvent(ManageFramesEvent.SetRenameText(it)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onEvent(ManageFramesEvent.ConfirmRename) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ManageFramesEvent.ConfirmRename) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.manage_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ManageFramesEvent.CancelRename) }) { Text(stringResource(R.string.manage_cancel)) }
        },
    )
}

@Composable
private fun DeleteDialog(question: DeleteQuestion, onEvent: (ManageFramesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ManageFramesEvent.CancelDelete) },
        title = { Text(stringResource(R.string.manage_delete_title, question.row.name)) },
        text = {
            Text(
                if (question.strips > 0) {
                    pluralStringResource(R.plurals.manage_delete_body_used, question.strips, question.strips)
                } else {
                    stringResource(R.string.manage_delete_body_unused)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ManageFramesEvent.ConfirmDelete) }) { Text(stringResource(R.string.manage_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ManageFramesEvent.CancelDelete) }) { Text(stringResource(R.string.manage_cancel)) }
        },
    )
}
