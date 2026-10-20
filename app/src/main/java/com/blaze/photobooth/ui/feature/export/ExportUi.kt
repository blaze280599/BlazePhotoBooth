package com.blaze.photobooth.ui.feature.export

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.core.rememberBoothHaptics
import com.blaze.photobooth.data.export.ExportError
import com.blaze.photobooth.ui.theme.BoothMotion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A save or share button (README §4.5, §4.7, §7 A12): a round icon with its label below. While it saves, the icon
 * becomes a spinner and the label counts (*"2 of 4"*, or [progressLabel]); when it's done, the icon morphs into a
 * ✓ (`AnimatedContent`, bouncy) and the label says [doneLabel]. Disabled while any export runs. [width] null lets
 * the caller size it (e.g. `Modifier.weight(1f)`).
 */
@Composable
fun ExportAction(
    icon: ImageVector,
    @StringRes label: Int,
    state: ExportActionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 96.dp,
    @StringRes progressLabel: Int = R.string.export_progress_short,
    @StringRes doneLabel: Int = R.string.export_saved,
) {
    val reduceMotion = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    val usable = enabled && state.phase == ExportPhase.Idle
    val text = when (state.phase) {
        ExportPhase.Idle -> stringResource(label)
        ExportPhase.Busy ->
            if (state.total > 1) stringResource(progressLabel, (state.done + 1).coerceAtMost(state.total), state.total) else stringResource(label)
        ExportPhase.Done -> stringResource(doneLabel)
    }
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = usable, role = Role.Button, onClickLabel = stringResource(label), onClick = onClick)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(vertical = 4.dp)
            .alpha(if (enabled || state.phase != ExportPhase.Idle) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                } else {
                    (scaleIn(BoothMotion.bouncy(), initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                }
            },
            label = "exportIcon",
        ) { phase ->
            Box(
                Modifier
                    .size(48.dp)
                    .background(if (phase == ExportPhase.Done) colors.primary else colors.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when (phase) {
                    ExportPhase.Idle -> Icon(icon, contentDescription = null, tint = colors.primary)
                    ExportPhase.Busy -> CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = colors.primary,
                        strokeWidth = 3.dp,
                    )
                    ExportPhase.Done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary)
                }
            }
        }
        // Two lines, so "Save photos" still fits with big fonts (§6.7).
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Shows [messages] (§7 A12, §13): a snackbar for every save, with a `CONFIRM` haptic on success and **Retry** on
 * errors, and the system share sheet. A new message replaces the one on screen.
 */
@Composable
fun ExportMessages(messages: Flow<ExportMessage>, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val haptics = rememberBoothHaptics()
    val currentContext by rememberUpdatedState(context)
    LaunchedEffect(messages) {
        messages.collect { message ->
            if (message is ExportMessage.Share) {
                try {
                    currentContext.startActivity(message.intent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("ExportMessages", "No share sheet", e)
                }
                return@collect
            }
            if (message is ExportMessage.Saved) haptics.confirm() else haptics.reject()
            val retry: (() -> Unit)? = when (message) {
                is ExportMessage.Failed -> message.retry
                is ExportMessage.AutoSaveFailed -> message.retry
                else -> null
            }
            val text = ExportCopy.text(resources, message)
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(
                    message = text,
                    actionLabel = retry?.let { resources.getString(R.string.export_retry) },
                    withDismissAction = retry != null,
                    duration = if (retry != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) retry?.invoke()
            }
        }
    }
}

/** The words for each [ExportMessage] (§6.6). */
object ExportCopy {
    fun text(resources: android.content.res.Resources, message: ExportMessage): String = when (message) {
        is ExportMessage.Saved -> {
            val saved = when (message.what) {
                SavedWhat.Strip -> resources.getString(R.string.export_strip_saved)
                SavedWhat.Strips -> resources.getQuantityString(R.plurals.export_strips_saved, message.count, message.count)
                SavedWhat.Photos -> resources.getQuantityString(R.plurals.export_photos_saved, message.count, message.count)
            }
            if (message.missing > 0) {
                resources.getQuantityString(R.plurals.export_some_missing, message.missing, saved, message.missing)
            } else {
                saved
            }
        }
        is ExportMessage.Failed -> when {
            message.error == ExportError.MissingFile -> resources.getString(R.string.export_error_missing)
            message.error == ExportError.NoSpace -> resources.getString(R.string.export_error_space)
            message.saved > 0 -> resources.getQuantityString(R.plurals.export_error_partial, message.saved, message.saved)
            else -> resources.getString(R.string.export_error)
        }
        is ExportMessage.AutoSaveFailed -> resources.getString(R.string.export_auto_save_failed)
        is ExportMessage.ShareFailed ->
            resources.getString(if (message.error == ExportError.MissingFile) R.string.export_error_missing else R.string.export_share_error)
        is ExportMessage.Share -> ""
    }
}
