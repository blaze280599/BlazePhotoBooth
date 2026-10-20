package com.blaze.photobooth.ui.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.ui.feature.settings.SettingsSheet

/** Stateful Home: collects [HomeViewModel] state, owns the settings sheet, and turns events into navigation. */
@Composable
fun HomeRoute(
    onStart: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenStrip: (sessionId: String) -> Unit,
    onContinueDraft: (complete: Boolean) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    HomeScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                HomeEvent.Start -> onStart()
                HomeEvent.OpenAlbum -> onOpenAlbum()
                HomeEvent.OpenSettings -> showSettings = true
                is HomeEvent.OpenStrip -> onOpenStrip(event.sessionId)
                HomeEvent.ContinueDraft -> onContinueDraft(state.draft?.complete == true)
                HomeEvent.DiscardDraft -> viewModel.discardDraft()
            }
        },
    )

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}
