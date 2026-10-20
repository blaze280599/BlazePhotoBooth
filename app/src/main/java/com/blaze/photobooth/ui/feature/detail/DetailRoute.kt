package com.blaze.photobooth.ui.feature.detail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.ui.feature.export.ExportMessages

/**
 * Stateful Detail (§4.7). Back is Nav 3's own pop (so predictive back works); Edit opens Pick in edit mode;
 * after a delete, [onDeleted] goes to Album, which offers Undo. Save and Share snackbars and the share sheet come
 * through [ExportMessages].
 */
@Composable
fun DetailRoute(
    sessionId: String,
    onBack: () -> Unit,
    onEdit: (sessionId: String) -> Unit,
    onDeleted: (sessionId: String) -> Unit,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.factory(sessionId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted by rememberUpdatedState(onDeleted)
    val back by rememberUpdatedState(onBack)
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DetailEffect.Deleted -> deleted(effect.sessionId)
            }
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) back() }
    ExportMessages(viewModel.messages, snackbar)

    DetailScreen(
        state = state,
        snackbarHostState = snackbar,
        onEvent = { event ->
            when (event) {
                DetailEvent.Back -> onBack()
                DetailEvent.Edit -> if (!state.deleting) onEdit(sessionId)
                else -> viewModel.onEvent(event)
            }
        },
    )
}
