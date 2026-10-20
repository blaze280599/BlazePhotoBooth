package com.blaze.photobooth.ui.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.PillShape
import com.blaze.photobooth.ui.theme.SheetShape
import com.blaze.photobooth.ui.theme.handwritten

/**
 * Settings (README §4.8), a modal sheet over Home. In M1 it's a read-only preview: the rows
 * show their defaults but don't do anything yet (the sheet says so). M8 wires them to DataStore.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SettingsSheetContent()
    }
}

private sealed interface Trailing {
    data class Toggle(val defaultOn: Boolean) : Trailing
    data object Soon : Trailing
}

private class SettingItem(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int?,
    val trailing: Trailing,
)

private val Rows = listOf(
    SettingItem(Icons.AutoMirrored.Rounded.VolumeUp, R.string.settings_sounds, null, Trailing.Toggle(defaultOn = true)),
    SettingItem(Icons.Rounded.Vibration, R.string.settings_haptics, null, Trailing.Toggle(defaultOn = true)),
    SettingItem(Icons.Rounded.FlashOn, R.string.settings_screen_flash, null, Trailing.Toggle(defaultOn = true)),
    SettingItem(Icons.Rounded.Flip, R.string.settings_save_mirrored, null, Trailing.Toggle(defaultOn = true)),
    SettingItem(Icons.Rounded.SaveAlt, R.string.settings_auto_save, R.string.settings_auto_save_summary, Trailing.Toggle(defaultOn = false)),
    SettingItem(Icons.Rounded.Collections, R.string.settings_manage_frames, R.string.settings_manage_frames_summary, Trailing.Soon),
    SettingItem(Icons.Rounded.Storage, R.string.settings_storage, R.string.settings_storage_summary, Trailing.Soon),
    SettingItem(Icons.Rounded.Info, R.string.settings_about, R.string.settings_about_summary, Trailing.Soon),
)

@Composable
fun SettingsSheetContent(modifier: Modifier = Modifier) {
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
            text = stringResource(R.string.settings_preview_note),
            style = MaterialTheme.typography.titleMedium.handwritten(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        Rows.forEach { row -> SettingsRow(row) }
    }
}

@Composable
private fun SettingsRow(row: SettingItem) {
    ListItem(
        headlineContent = { Text(stringResource(row.title)) },
        supportingContent = row.summary?.let { { Text(stringResource(it)) } },
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                // Decorative: the row's text already names the setting.
                Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        trailingContent = {
            when (val trailing = row.trailing) {
                is Trailing.Toggle -> Switch(
                    checked = trailing.defaultOn,
                    onCheckedChange = null,
                    enabled = false,
                    // Keep "on" readable as on, even while the switch can't be used yet.
                    colors = SwitchDefaults.colors(
                        disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                Trailing.Soon -> SoonPill()
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun SoonPill() {
    Surface(shape = PillShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = stringResource(R.string.settings_soon),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview(name = "Settings sheet · light")
@Composable
private fun SettingsSheetPreview() {
    BoothTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape) { SettingsSheetContent() }
    }
}

@Preview(name = "Settings sheet · dark")
@Composable
private fun SettingsSheetDarkPreview() {
    BoothTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape) { SettingsSheetContent() }
    }
}
