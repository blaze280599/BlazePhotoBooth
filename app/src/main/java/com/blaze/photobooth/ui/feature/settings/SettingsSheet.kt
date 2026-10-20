package com.blaze.photobooth.ui.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.R
import com.blaze.photobooth.data.export.StorageStats
import com.blaze.photobooth.data.export.StorageUsage
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape
import com.blaze.photobooth.ui.theme.handwritten

/**
 * Settings (README §4.8), a modal sheet over Home. Since M6 the switches are real (DataStore, §11.6) and *Storage
 * used* is counted each time the sheet opens. *Manage frames* opens its own screen (M7).
 */
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onManageFrames: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refreshStorage() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SettingsSheetContent(state, viewModel::onEvent, onManageFrames = onManageFrames)
    }
}

@Composable
fun SettingsSheetContent(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onManageFrames: () -> Unit = {},
) {
    val s = state.settings
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.settings_note),
            style = MaterialTheme.typography.titleMedium.handwritten(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        ToggleRow(Icons.AutoMirrored.Rounded.VolumeUp, R.string.settings_sounds, null, s.sounds) {
            onEvent(SettingsEvent.SetSounds(it))
        }
        ToggleRow(Icons.Rounded.Vibration, R.string.settings_haptics, null, s.haptics) { onEvent(SettingsEvent.SetHaptics(it)) }
        ToggleRow(Icons.Rounded.FlashOn, R.string.settings_screen_flash, null, s.screenFlash) {
            onEvent(SettingsEvent.SetScreenFlash(it))
        }
        ToggleRow(Icons.Rounded.Flip, R.string.settings_save_mirrored, R.string.settings_save_mirrored_summary, s.saveMirrored) {
            onEvent(SettingsEvent.SetSaveMirrored(it))
        }
        ToggleRow(Icons.Rounded.SaveAlt, R.string.settings_auto_save, R.string.settings_auto_save_summary, s.autoSaveStrip) {
            onEvent(SettingsEvent.SetAutoSave(it))
        }
        SettingsRow(
            Icons.Rounded.Collections,
            R.string.settings_manage_frames,
            stringResource(R.string.settings_manage_frames_summary),
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.cd_settings_manage_frames), role = Role.Button, onClick = onManageFrames),
        ) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsRow(Icons.Rounded.Storage, R.string.settings_storage, storageText(state.storage))
        SettingsRow(Icons.Rounded.Info, R.string.settings_about, stringResource(R.string.settings_about_summary))
    }
}

/** "42 strips · 310 MB" (§4.8), or "Counting…" until the walk is done. */
@Composable
private fun storageText(usage: StorageUsage?): String =
    if (usage == null) {
        stringResource(R.string.settings_storage_measuring)
    } else {
        pluralStringResource(R.plurals.settings_storage_value, usage.strips, usage.strips, StorageStats.megabytes(usage.bytes))
    }

/** A switch row: the whole row toggles it (one big touch target). */
@Composable
private fun ToggleRow(icon: ImageVector, @StringRes title: Int, @StringRes summary: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        icon,
        title,
        summary?.let { stringResource(it) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    @StringRes title: Int,
    summary: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                // Decorative: the row's text already names the setting.
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.padding(horizontal = 8.dp),
    )
}

private val PreviewState = SettingsUiState(storage = StorageUsage(strips = 42, bytes = 310_000_000))

@Preview(name = "Settings sheet · light")
@Composable
private fun SettingsSheetPreview() {
    BoothTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape) { SettingsSheetContent(PreviewState, {}) }
    }
}

@Preview(name = "Settings sheet · dark")
@Composable
private fun SettingsSheetDarkPreview() {
    BoothTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape) { SettingsSheetContent(PreviewState, {}) }
    }
}
