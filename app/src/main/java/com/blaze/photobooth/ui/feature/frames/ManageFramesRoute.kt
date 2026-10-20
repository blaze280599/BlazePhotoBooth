package com.blaze.photobooth.ui.feature.frames

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.R

/** Manage frames (README §4.8), opened from Settings. */
@Composable
fun ManageFramesRoute(
    onBack: () -> Unit,
    onAddFrame: () -> Unit,
    viewModel: ManageFramesViewModel = viewModel(factory = ManageFramesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is ManageFramesMessage.Deleted -> resources.getString(R.string.manage_deleted, message.name)
                is ManageFramesMessage.KeptHidden -> resources.getString(R.string.manage_kept_hidden, message.name)
            }
            snackbar.showSnackbar(text)
        }
    }
    ManageFramesScreen(
        state = state,
        snackbarHostState = snackbar,
        onEvent = { event ->
            when (event) {
                ManageFramesEvent.Back -> onBack()
                ManageFramesEvent.Add -> onAddFrame()
                else -> viewModel.onEvent(event)
            }
        },
    )
}
