package com.blaze.photobooth.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.PI

/**
 * How far the phone has just been tilted, as two small angles for a `graphicsLayer` (README §4.2, the Setup preview's
 * tilt and parallax). Only draw code should read [rotationX] / [rotationY], so a sensor update redraws one layer and
 * never recomposes. Both stay 0 when tilting is off (reduce motion, previews, no sensor).
 */
@Stable
class DeviceTilt internal constructor() {
    private val x = mutableFloatStateOf(0f)
    private val y = mutableFloatStateOf(0f)

    /** Degrees around the horizontal axis (top edge away when the phone tips back), within ±[TiltMath.MaxDegrees]. */
    val rotationX: Float get() = x.floatValue

    /** Degrees around the vertical axis (right edge away when the phone tips right), within ±[TiltMath.MaxDegrees]. */
    val rotationY: Float get() = y.floatValue

    internal val filter = TiltMath.Filter()

    internal fun update(gravityX: Float, gravityY: Float) {
        filter.add(gravityX, gravityY)
        x.floatValue = filter.rotationX
        y.floatValue = filter.rotationY
    }

    internal fun reset() {
        filter.reset()
        x.floatValue = 0f
        y.floatValue = 0f
    }
}

/**
 * The device's tilt while the screen is resumed (the gravity sensor, or the accelerometer if there's none). With
 * [enabled] false (reduce motion, §6.7) no sensor is used and the angles stay 0.
 */
@Composable
fun rememberDeviceTilt(enabled: Boolean): DeviceTilt {
    val tilt = remember { DeviceTilt() }
    val context = LocalContext.current
    val active = enabled && !LocalInspectionMode.current
    LifecycleResumeEffect(active, context) {
        val manager = if (active) context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager else null
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = tilt.update(event.values[0], event.values[1])
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose {
            manager?.unregisterListener(listener)
            tilt.reset()
        }
    }
    return tilt
}

/**
 * The tilt maths, pure so it's unit-tested. The preview follows how the phone *moves*, not how it's held: a slow
 * baseline drifts to the current pose (about a second), so holding the phone at any angle settles back to level, and
 * a quick tilt leans the strip by up to ±[MaxDegrees]. A light low-pass keeps sensor noise from jittering it.
 */
object TiltMath {
    const val MaxDegrees = 3f
    private const val Gravity = SensorManager.GRAVITY_EARTH

    /** Degrees of strip lean per degree of phone tilt. */
    private const val Gain = 0.6f

    /** Per sensor event (≈ 50 Hz): how fast the baseline follows the pose, and how much each reading counts. */
    const val BaselineRate = 0.02f
    const val Smoothing = 0.2f

    /** A gravity change (m/s²) as a clamped lean in degrees. */
    fun lean(delta: Float): Float {
        val degrees = (delta / Gravity).coerceIn(-1f, 1f) * (180f / PI.toFloat()) * Gain
        return degrees.coerceIn(-MaxDegrees, MaxDegrees)
    }

    class Filter {
        private var baseX = Float.NaN
        private var baseY = Float.NaN
        private var smoothX = 0f
        private var smoothY = 0f

        val rotationX: Float get() = lean(-smoothY)
        val rotationY: Float get() = lean(smoothX)

        fun add(gx: Float, gy: Float) {
            if (baseX.isNaN()) {
                baseX = gx
                baseY = gy
            }
            baseX += (gx - baseX) * BaselineRate
            baseY += (gy - baseY) * BaselineRate
            smoothX += ((gx - baseX) - smoothX) * Smoothing
            smoothY += ((gy - baseY) - smoothY) * Smoothing
        }

        fun reset() {
            baseX = Float.NaN
            baseY = Float.NaN
            smoothX = 0f
            smoothY = 0f
        }
    }
}
