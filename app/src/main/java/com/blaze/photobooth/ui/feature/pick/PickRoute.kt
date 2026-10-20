package com.blaze.photobooth.ui.feature.pick

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.R
import com.blaze.photobooth.core.rememberBoothHaptics
import kotlinx.coroutines.launch

/**
 * Stateful Pick: haptics for every placement (§6.5), the A8 fly-in, the Print shake, and the §3 back rules:
 * back asks "Retake everything?", ✕ asks "Discard session?". [onRetake] shoots one shot again (§4.4).
 *
 * With [editSessionId] it's Edit (§4.7): Save re-renders that saved strip and calls [onEditDone]; ✕ and back
 * cancel with no changes ([onCancelEdit]), with Nav 3's own back so predictive back works.
 */
@Composable
fun PickRoute(
    onRetakeAll: () -> Unit,
    onRetake: (shotIndex: Int) -> Unit,
    onDiscarded: () -> Unit,
    onPrinted: (sessionId: String) -> Unit,
    editSessionId: String? = null,
    onEditDone: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    viewModel: PickViewModel = viewModel(factory = PickViewModel.factory(editSessionId)),
) {
    val editing = editSessionId != null
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberBoothHaptics()
    val snackbar = remember { SnackbarHostState() }
    var flight by remember { mutableStateOf<PickFlight?>(null) }
    var flightKey by remember { mutableIntStateOf(0) }
    var rejectCount by remember { mutableIntStateOf(0) }
    val printed by rememberUpdatedState(onPrinted)
    val editDone by rememberUpdatedState(onEditDone)
    val printFailed = stringResource(if (editing) R.string.pick_save_failed else R.string.pick_print_failed)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PickEffect.Placed -> {
                    haptics.segmentTick()
                    flight = PickFlight(effect.shot, effect.slot, ++flightKey)
                }
                PickEffect.Cleared -> haptics.segmentTick()
                PickEffect.Rejected -> {
                    haptics.reject()
                    rejectCount++
                }
                is PickEffect.Printed -> {
                    haptics.confirm()
                    if (editing) editDone() else printed(effect.sessionId)
                }
                PickEffect.PrintFailed -> launch { snackbar.showSnackbar(printFailed) }
            }
        }
    }

    val missing by rememberUpdatedState(if (editing) onCancelEdit else onDiscarded)
    LaunchedEffect(state.missing) { if (state.missing) missing() }

    var confirmRetakeAll by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    if (editing) {
        // Back cancels the edit by itself (Nav 3's pop); only a save in progress holds it.
        BackHandler(enabled = state.printing) {}
    } else {
        BackHandler(enabled = !state.printing) { confirmRetakeAll = true }
    }

    PickScreen(
        state = state,
        flight = flight,
        rejectCount = rejectCount,
        snackbarHostState = snackbar,
        onEvent = { event ->
            when (event) {
                PickEvent.Back -> if (editing) onCancelEdit() else confirmRetakeAll = true
                PickEvent.Close -> if (editing) onCancelEdit() else confirmDiscard = true
                // Choosing a frame or filter, or flipping the date, ticks like Setup's choices (§6.5).
                is PickEvent.SelectFrame, is PickEvent.SelectFilter, is PickEvent.SetShowDate -> {
                    haptics.segmentTick()
                    viewModel.onEvent(event)
                }
                is PickEvent.Retake -> {
                    viewModel.onEvent(PickEvent.DismissPeek)
                    onRetake(event.index)
                }
                else -> viewModel.onEvent(event)
            }
        },
    )

    if (confirmRetakeAll) {
        RetakeAllDialog(
            onStay = { confirmRetakeAll = false },
            onRetake = {
                confirmRetakeAll = false
                viewModel.retakeAll(onRetakeAll)
            },
        )
    }
    if (confirmDiscard) {
        DiscardSessionDialog(
            onStay = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                viewModel.discard(onDiscarded)
            },
        )
    }
}
