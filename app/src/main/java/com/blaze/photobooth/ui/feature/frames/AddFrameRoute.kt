package com.blaze.photobooth.ui.feature.frames

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.core.rememberBoothHaptics
import kotlinx.coroutines.delay

/**
 * "+ Add frame" (README §4.9), from Setup's carousel or Manage frames. Opens the system Photo Picker at once (images
 * only, no permission needed); cancelling it leaves. [onDone] runs after the heart pop of a saved frame.
 */
@Composable
fun AddFrameRoute(
    onExit: () -> Unit,
    onDone: () -> Unit,
    viewModel: AddFrameViewModel = viewModel(factory = AddFrameViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exit by rememberUpdatedState(onExit)
    val done by rememberUpdatedState(onDone)
    val haptics = rememberBoothHaptics()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        when {
            uri != null -> viewModel.open(uri)
            // Backed out of the picker before choosing anything: nothing to do here.
            viewModel.uiState.value.step == AddFrameStep.Picking -> exit()
        }
    }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            picker.launch(request)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AddFrameEffect.Exit -> exit()
                AddFrameEffect.LaunchPicker -> picker.launch(request)
            }
        }
    }
    LaunchedEffect(state.step) {
        if (state.step == AddFrameStep.Saved) {
            haptics.confirm()
            delay(SavedMillis)
            done()
        }
    }
    // The editor and the name panel step back inside the flow; elsewhere back leaves (nothing is saved before Save).
    BackHandler(enabled = state.step == AddFrameStep.Editor) { viewModel.onEvent(AddFrameEvent.EditorCancel) }
    BackHandler(enabled = state.step == AddFrameStep.Confirm && state.naming) { viewModel.onEvent(AddFrameEvent.CancelNaming) }

    AddFrameScreen(state = state, onEvent = viewModel::onEvent)
}
