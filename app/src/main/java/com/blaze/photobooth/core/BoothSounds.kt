package com.blaze.photobooth.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaActionSound
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The booth's sounds (README §6.5): a soft beep per countdown tick and the system shutter click.
 * Sounds follow the phone's volume and silent mode. The in-app Sounds switch joins in M8
 * ([com.blaze.photobooth.data.prefs.BoothSettings.sounds]).
 */
class BoothSounds {

    private val shutter = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    private val tick = beepTrack(Beep.pcm(TickHz, TickMillis))
    private val lastTick = beepTrack(Beep.pcm(LastTickHz, LastTickMillis))

    /** One countdown tick; the last second gets a brighter note. */
    fun tick(last: Boolean) {
        val track = (if (last) lastTick else tick) ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: IllegalStateException) {
            Log.w(Tag, "Beep failed", e)
        }
    }

    fun shutter() = shutter.play(MediaActionSound.SHUTTER_CLICK)

    fun release() {
        shutter.release()
        tick?.release()
        lastTick?.release()
    }

    private fun beepTrack(pcm: ShortArray): AudioTrack? = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Beep.SampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    } catch (e: Exception) {
        // No beep is better than no booth.
        Log.w(Tag, "Couldn't create the countdown beep", e)
        null
    }

    companion object {
        private const val Tag = "BoothSounds"
        private const val TickHz = 880f
        private const val TickMillis = 90
        private const val LastTickHz = 1320f
        private const val LastTickMillis = 110

        /**
         * Some regions require the shutter sound no matter what (§6.5); the Sounds switch (M8)
         * must not silence it there.
         */
        fun shutterSoundRequired(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && MediaActionSound.mustPlayShutterSound()
    }
}

/** A [BoothSounds] that lives as long as the calling composable. */
@Composable
fun rememberBoothSounds(): BoothSounds {
    val sounds = remember { BoothSounds() }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    return sounds
}

/** A soft sine "bip" with a quick attack and a gentle decay, so it never clicks. Pure, so it's unit-tested. */
object Beep {
    const val SampleRate = 44_100
    private const val AttackMillis = 6f
    private const val ReleaseMillis = 25f

    fun pcm(frequencyHz: Float, durationMillis: Int, volume: Float = 0.22f, sampleRate: Int = SampleRate): ShortArray {
        val n = (sampleRate * durationMillis / 1000f).roundToInt()
        val attack = sampleRate * AttackMillis / 1000f
        val release = sampleRate * ReleaseMillis / 1000f
        return ShortArray(n) { i ->
            val t = i.toFloat() / sampleRate
            val rise = min(1f, i / attack)
            val fall = min(1f, (n - 1 - i) / release)
            val decay = exp(-t * 18f)
            val v = sin(2 * PI * frequencyHz * t).toFloat() * volume * rise * fall * decay
            (v * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}
