package com.blaze.photobooth.camera

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ExperimentalMirrorMode
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionFilter
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.Observer
import androidx.lifecycle.LifecycleOwner
import com.blaze.photobooth.model.BackFlash
import com.blaze.photobooth.model.Lens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Whether the camera can take a photo right now. */
sealed interface CameraStatus {
    data object Starting : CameraStatus
    data object Ready : CameraStatus

    /** Couldn't bind, or another app holds the camera (§13): the screen shows Retry. */
    data class Failed(val reason: Reason) : CameraStatus

    enum class Reason { InUse, Disabled, NoCamera, Other }
}

/**
 * CameraX for the booth (README §9.1): Preview + ImageCapture bound to the Capture screen's lifecycle.
 * Both are 4:3 so the viewfinder and the photo see the same field of view; each is then centre-cropped
 * to the slot's aspect (§9.2, §9.4). Photos use `CAPTURE_MODE_MINIMIZE_LATENCY` at the highest
 * resolution ≤ 12 MP. The front camera uses a screen flash (§9.5); the back camera's LED flash is Off or
 * Auto, as chosen ([BackFlash], §13).
 */
class CameraController(private val context: Context) {

    private val surface = MutableStateFlow<SurfaceRequest?>(null)

    /** Hand this to `CameraXViewfinder`. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = surface.asStateFlow()

    private val statusFlow = MutableStateFlow<CameraStatus>(CameraStatus.Starting)
    val status: StateFlow<CameraStatus> = statusFlow.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var boundLens: Lens? = null
    private var stateObserver: Observer<CameraState>? = null

    /** Capture callbacks land here, off the main thread. */
    private val captureExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "booth-capture") }

    /** Which lenses this phone has (for the flip button). */
    suspend fun availableLenses(): Set<Lens> = try {
        val p = provider()
        Lens.entries.filter { p.hasCamera(selector(it)) }.toSet()
    } catch (e: Exception) {
        Log.w(Tag, "Couldn't list cameras", e)
        emptySet()
    }

    /**
     * (Re)binds the camera. [screenFlash] is used for the front camera only, [backFlash] for the back one.
     * [targetRotation] is the display's rotation (`Surface.ROTATION_*`), so photos come out upright (§13).
     */
    @androidx.annotation.OptIn(ExperimentalMirrorMode::class)
    suspend fun bind(
        owner: LifecycleOwner,
        lens: Lens,
        mirrorPreview: Boolean,
        screenFlash: ImageCapture.ScreenFlash?,
        backFlash: BackFlash = BackFlash.Default,
        targetRotation: Int = Surface.ROTATION_0,
    ) {
        statusFlow.value = CameraStatus.Starting
        try {
            val p = provider()
            val selector = selector(lens)
            if (!p.hasCamera(selector)) {
                statusFlow.value = CameraStatus.Failed(CameraStatus.Reason.NoCamera)
                return
            }
            unbind()
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(FourByThree).build())
                .setMirrorMode(if (mirrorPreview) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
                .setTargetRotation(targetRotation)
                .build()
                .also { it.setSurfaceProvider { request -> surface.value = request } }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(photoResolution)
                .setTargetRotation(targetRotation)
                .apply {
                    val mode = flashMode(lens, backFlash, screenFlash != null)
                    if (mode == ImageCapture.FLASH_MODE_SCREEN && screenFlash != null) setScreenFlash(screenFlash)
                    setFlashMode(mode)
                }
                .build()
            boundLens = lens
            val bound = p.bindToLifecycle(owner, selector, preview, capture)
            imageCapture = capture
            camera = bound
            watch(bound, owner)
            Log.i(Tag, "Bound $lens camera, photo ${capture.resolutionInfo?.resolution}, flash mode ${capture.flashMode}")
        } catch (e: Exception) {
            Log.e(Tag, "Couldn't bind the $lens camera", e)
            statusFlow.value = CameraStatus.Failed(CameraStatus.Reason.Other)
        }
    }

    /** The quick toggle in Capture's top bar: switches the back camera's LED flash without rebinding. */
    fun setBackFlash(backFlash: BackFlash) {
        val capture = imageCapture ?: return
        if (boundLens != Lens.Back) return
        capture.flashMode = flashMode(Lens.Back, backFlash, screenFlashAvailable = false)
        Log.i(Tag, "Back flash $backFlash")
    }

    fun unbind() {
        camera?.let { c -> stateObserver?.let { c.cameraInfo.cameraState.removeObserver(it) } }
        stateObserver = null
        camera = null
        boundLens = null
        imageCapture?.screenFlash = null
        imageCapture = null
        provider?.unbindAll()
        surface.value = null
    }

    /** Waits up to [timeoutMillis] for the camera to be open. */
    suspend fun awaitReady(timeoutMillis: Long = ReadyTimeoutMillis): Boolean =
        withTimeoutOrNull(timeoutMillis) { status.first { it == CameraStatus.Ready } } != null

    /**
     * Takes one photo. [onStarted] fires the moment the sensor exposes (after any screen flash), which is
     * when the shutter sound and flash belong. The caller must close the returned [ImageProxy].
     */
    suspend fun takePicture(onStarted: () -> Unit): ImageProxy {
        val capture = imageCapture ?: throw IllegalStateException("Camera isn't bound")
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureStarted() = onStarted()

                    override fun onCaptureSuccess(image: ImageProxy) {
                        cont.resume(image) { _, value, _ -> value.close() }
                    }

                    override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
                },
            )
        }
    }

    /** Call when the screen is gone for good. */
    fun release() {
        unbind()
        captureExecutor.shutdown()
    }

    private suspend fun provider(): ProcessCameraProvider =
        provider ?: ProcessCameraProvider.awaitInstance(context).also { provider = it }

    private fun watch(camera: Camera, owner: LifecycleOwner) {
        val observer = Observer<CameraState> { state ->
            statusFlow.value = statusFor(state, statusFlow.value)
            state.error?.let { Log.w(Tag, "Camera state ${state.type}, error ${it.code}") }
        }
        stateObserver = observer
        camera.cameraInfo.cameraState.observe(owner, observer)
    }

    companion object {
        private const val Tag = "CameraController"
        private const val ReadyTimeoutMillis = 5_000L

        /** Photos are at most 12 MP (§9.1): plenty for a strip, and faster than the full 50 MP sensor. */
        const val MaxPhotoPixels = 12_000_000L

        private val BusyErrors = setOf(CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE)

        /**
         * §13: what Capture shows for the camera's [state]. Busy → "another app might be using it", disabled or any
         * other critical error → the error card (both offer Retry); open → ready. A failure stays on screen while the
         * camera closes after it ([previous]). Pure.
         */
        fun statusFor(state: CameraState, previous: CameraStatus): CameraStatus {
            val error = state.error
            return when {
                error != null && error.code in BusyErrors -> CameraStatus.Failed(CameraStatus.Reason.InUse)
                error != null && error.code == CameraState.ERROR_CAMERA_DISABLED -> CameraStatus.Failed(CameraStatus.Reason.Disabled)
                error != null && error.type == CameraState.ErrorType.CRITICAL -> CameraStatus.Failed(CameraStatus.Reason.Other)
                state.type == CameraState.Type.OPEN -> CameraStatus.Ready
                else -> previous.takeIf { it is CameraStatus.Failed } ?: CameraStatus.Starting
            }
        }

        private val FourByThree = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

        private val photoResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(FourByThree)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .setResolutionFilter(ResolutionFilter { sizes, _ -> atMost12Mp(sizes) })
            .build()

        /**
         * The `ImageCapture` flash mode for a lens (§9.5, §13): the front camera uses the screen flash when
         * there is one; the back camera's LED is off unless the user picked Auto. Pure, so it's unit-tested.
         */
        fun flashMode(lens: Lens, backFlash: BackFlash, screenFlashAvailable: Boolean): Int = when (lens) {
            Lens.Front -> if (screenFlashAvailable) ImageCapture.FLASH_MODE_SCREEN else ImageCapture.FLASH_MODE_OFF
            Lens.Back -> when (backFlash) {
                BackFlash.Off -> ImageCapture.FLASH_MODE_OFF
                BackFlash.Auto -> ImageCapture.FLASH_MODE_AUTO
            }
        }

        /** The supported sizes ≤ 12 MP, largest first (all of them if none is that small). */
        fun atMost12Mp(sizes: List<Size>): List<Size> =
            sizes.filter { it.width.toLong() * it.height <= MaxPhotoPixels }
                .sortedByDescending { it.width.toLong() * it.height }
                .ifEmpty { sizes }

        private fun selector(lens: Lens) = when (lens) {
            Lens.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
            Lens.Back -> CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
}
