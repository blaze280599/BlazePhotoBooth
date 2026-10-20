package com.blaze.photobooth.ui.feature.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blaze.photobooth.R
import com.blaze.photobooth.camera.ComposeScreenFlash
import com.blaze.photobooth.core.BoothSounds
import com.blaze.photobooth.core.rememberBoothHaptics
import com.blaze.photobooth.core.rememberBoothSounds
import kotlinx.coroutines.launch

/**
 * Stateful Capture: the camera permission (§12), binding the camera to this screen's lifecycle,
 * the screen flash, sounds and haptics (§6.5), pausing in the background (§13) and leaving.
 * [retakeIndex] shoots only that shot again (Pick's retake, §4.4); leaving it never asks or discards.
 */
@Composable
fun CaptureRoute(
    onLeave: () -> Unit,
    onFinished: () -> Unit,
    retakeIndex: Int? = null,
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.factory(retakeIndex)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current

    // §12: pre-prompt card → system dialog → "Open settings" if it's blocked for good.
    var requested by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var rationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        requested = true
        granted = ok
        rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCameraPermission(context) }
    val permission = CameraPermission.of(granted, requested, rationale)

    // §9.5: the front camera's screen flash lives in this window.
    val screenFlash = remember(activity) { activity?.window?.let(::ComposeScreenFlash) }
    DisposableEffect(screenFlash) { onDispose { screenFlash?.restore() } }

    if (granted && state.loaded) {
        LaunchedEffect(owner, state.lens, state.mirror, state.bindAttempt, settings.screenFlash) {
            viewModel.camera.bind(
                owner = owner,
                lens = state.lens,
                mirrorPreview = state.mirror,
                screenFlash = screenFlash.takeIf { settings.screenFlash },
                backFlash = state.backFlash,
                targetRotation = view.display?.rotation ?: Surface.ROTATION_0,
            )
        }
        DisposableEffect(owner) { onDispose { viewModel.camera.unbind() } }
    }

    // §13: backgrounded mid-countdown → pause; back again → a fresh countdown.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    val haptics = rememberBoothHaptics()
    val sounds = rememberBoothSounds()
    haptics.enabled = settings.haptics
    val currentSettings by rememberUpdatedState(settings)
    val finished by rememberUpdatedState(onFinished)
    val snackbar = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.capture_save_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CaptureEffect.Tick -> {
                    haptics.clockTick()
                    if (currentSettings.sounds) sounds.tick(last = effect.secondsLeft == 1)
                }
                CaptureEffect.Shutter -> {
                    haptics.confirm()
                    if (currentSettings.sounds || BoothSounds.shutterSoundRequired()) sounds.shutter()
                }
                CaptureEffect.SaveFailed -> launch { snackbar.showSnackbar(saveFailed) }
                CaptureEffect.Finished -> finished()
            }
        }
    }

    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val retake = state.retakeIndex != null
    val close = {
        if (state.taken > 0 && !retake) {
            viewModel.pause()
            confirmLeave = true
        } else {
            viewModel.leave(onLeave)
        }
    }
    BackHandler(enabled = state.taken > 0 || retake, onBack = close)

    CaptureScreen(
        state = state,
        permission = permission,
        screenFlashOn = screenFlash?.visible == true,
        snackbarHostState = snackbar,
        onEvent = { event ->
            when (event) {
                CaptureEvent.Close -> close()
                CaptureEvent.RequestPermission -> launcher.launch(Manifest.permission.CAMERA)
                CaptureEvent.OpenSettings -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                else -> viewModel.onEvent(event)
            }
        },
    ) { modifier ->
        val request by viewModel.camera.surfaceRequest.collectAsStateWithLifecycle()
        request?.let {
            CameraXViewfinder(
                surfaceRequest = it,
                modifier = modifier,
                implementationMode = ImplementationMode.EMBEDDED,
                contentScale = ContentScale.Crop,
            )
        }
    }

    if (confirmLeave) {
        LeaveBoothDialog(
            shots = state.taken,
            onStay = { confirmLeave = false },
            onLeave = {
                confirmLeave = false
                viewModel.leave(onLeave)
            },
        )
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
