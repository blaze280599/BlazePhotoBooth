package com.blaze.photobooth.ui.feature.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful Result (§4.5). Back goes Home (§3): Print left only Home beneath Result, so the system back (and its
 * predictive animation) does that by itself; the ← button does the same.
 */
@Composable
fun ResultRoute(
    sessionId: String,
    onAgain: () -> Unit,
    onAlbum: () -> Unit,
    onHome: () -> Unit,
    viewModel: ResultViewModel = viewModel(factory = ResultViewModel.factory(sessionId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val home by rememberUpdatedState(onHome)
    LaunchedEffect(state.missing) { if (state.missing) home() }
    ResultScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                ResultEvent.Again -> onAgain()
                ResultEvent.Album -> onAlbum()
                ResultEvent.Home -> onHome()
            }
        },
    )
}
