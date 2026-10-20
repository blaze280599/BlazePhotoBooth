package com.blaze.photobooth.ui.feature.album

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.R
import com.blaze.photobooth.ui.feature.export.ExportMessages
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stateful Album (§4.6): opens Detail, and shows one Undo snackbar per delete for [UndoMillis] (§4.7), including a
 * delete made in Detail, which comes back here. While selecting, back clears the selection.
 */
@Composable
fun AlbumRoute(
    onBack: () -> Unit,
    onOpen: (sessionId: String) -> Unit,
    onStart: () -> Unit,
    viewModel: AlbumViewModel = viewModel(factory = AlbumViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val open by rememberUpdatedState(onOpen)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AlbumEffect.Open -> open(effect.id)
            }
        }
    }

    LaunchedEffect(undo) {
        val batch = undo ?: return@LaunchedEffect
        // A delete made in Detail has already used part of its time on the way back here.
        val remaining = UndoMillis - (System.currentTimeMillis() - batch.at)
        if (remaining <= 0) {
            viewModel.undoExpired(batch)
            return@LaunchedEffect
        }
        val result = withTimeoutOrNull(remaining) {
            snackbar.showSnackbar(
                message = resources.getQuantityString(R.plurals.album_deleted, batch.ids.size, batch.ids.size),
                actionLabel = resources.getString(R.string.album_undo),
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(batch) else viewModel.undoExpired(batch)
    }

    ExportMessages(viewModel.messages, snackbar)

    // Only while selecting, so the predictive back animation (A1) still works the rest of the time.
    BackHandler(enabled = state.selecting) { viewModel.onEvent(AlbumEvent.ClearSelection) }

    AlbumScreen(
        state = state,
        snackbarHostState = snackbar,
        onEvent = { event ->
            when (event) {
                AlbumEvent.Back -> onBack()
                AlbumEvent.Start -> onStart()
                else -> viewModel.onEvent(event)
            }
        },
    )
}

/** How long Undo is offered after a delete (§4.7). */
const val UndoMillis = 5_000L
