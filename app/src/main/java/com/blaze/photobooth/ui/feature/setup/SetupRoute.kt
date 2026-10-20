package com.blaze.photobooth.ui.feature.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Stateful Setup: collects [SetupViewModel] state and turns navigation events into callbacks. */
@Composable
fun SetupRoute(
    onBack: () -> Unit,
    onStart: () -> Unit,
    onAddFrame: () -> Unit,
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetupScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                SetupEvent.Back -> onBack()
                SetupEvent.Start -> viewModel.startSession(onStart)
                SetupEvent.AddFrame -> onAddFrame()
                else -> viewModel.onEvent(event)
            }
        },
    )
}
