package com.blaze.photobooth.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.blaze.photobooth.AppContainer
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.db.ShotEntity
import com.blaze.photobooth.data.files.SessionFileStore
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.StickerKind
import com.blaze.photobooth.render.Stickers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Debug builds only (README §15.3). If `files/debug_seed` exists when the app starts, it seeds that many pastel
 * sample strips (generated "photos" with numbers, never people) spread over the last months, then deletes the
 * file. The agent creates the file with `adb shell run-as`, to test the album's months, filters and scrolling
 * without taking photos. Every seeded strip has the caption "sample ✿". Writing `clear` instead of a count soft-deletes
 * them all (purged on the next start). `delete <id> <id> …` soft-deletes exactly those strips (M9 clean-up).
 */
class SampleSeedProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        // Providers start before Application.onCreate; the container exists once the main thread is free.
        Handler(Looper.getMainLooper()).post {
            val container = (context.applicationContext as BoothApp).container
            container.appScope.launch {
                try {
                    SampleSeeder(container).runIfRequested(File(context.filesDir, TriggerName))
                } catch (e: Exception) {
                    Log.e(Tag, "Seeding failed", e)
                }
            }
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    companion object {
        const val Tag = "SampleSeed"
        const val TriggerName = "debug_seed"
    }
}

private class SampleSeeder(private val c: AppContainer) {

    private val files: SessionFileStore get() = c.sessionRepository.files

    suspend fun runIfRequested(trigger: File) {
        if (!trigger.isFile) return
        val request = trigger.readText().trim()
        trigger.delete()
        if (request.startsWith(DeleteRequest)) {
            // Soft-deletes exactly the listed session ids (the next start purges them); any other strip is untouched.
            // M9's clean-up before the gift used it, so her real strip could never be hit by accident.
            val wanted = request.removePrefix(DeleteRequest).split(' ', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val found = c.sessionRepository.sessions.first().map { it.id }.filter { it in wanted }
            c.sessionRepository.softDelete(found)
            Log.i(SampleSeedProvider.Tag, "Deleted ${found.size} of ${wanted.size} listed strips")
            return
        }
        if (request == ClearRequest) {
            // Soft-deletes every sample strip (caption "sample ✿"); the next start purges them. Nothing else is touched.
            val samples = c.sessionRepository.sessions.first().filter { it.caption == Caption }.map { it.id }
            c.sessionRepository.softDelete(samples)
            Log.i(SampleSeedProvider.Tag, "Cleared ${samples.size} sample strips")
            return
        }
        val count = request.toIntOrNull()?.coerceIn(1, MaxCount) ?: DefaultCount
        Log.i(SampleSeedProvider.Tag, "Seeding $count sample strips")
        val options = c.frameRepository.options.first()
        val her = options.filterIsInstance<FrameOption.Artwork>()
        val themes = options.filterIsInstance<FrameOption.Theme>()
        val rng = Random(count * 7919)
        var at = System.currentTimeMillis() - DayMillis
        repeat(count) { n ->
            val option = if (n % 3 == 0 && her.isNotEmpty()) her[(n / 3) % her.size] else themes[n % themes.size]
            val layout = BoothLayout.entries[n % BoothLayout.entries.size]
            seedOne(n, option, layout, PhotoFilter.entries[n % PhotoFilter.entries.size], at, favorite = rng.nextInt(10) < 3, bonus = rng.nextInt(3))
            at -= DayMillis * (3 + rng.nextInt(8))
        }
        Log.i(SampleSeedProvider.Tag, "Seeded $count sample strips")
    }

    private suspend fun seedOne(n: Int, option: FrameOption, layout: BoothLayout, filter: PhotoFilter, at: Long, favorite: Boolean, bonus: Int) {
        val frame = when (option) {
            is FrameOption.Artwork -> FrameSource.Image(option.frame, c.frameRepository.artwork(option.frame, maxWidth = null))
            is FrameOption.Theme -> FrameSource.Theme(option.theme)
        }
        val date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
        val spec = StripSpec(frame, layout, filter, caption = Caption, date = date)
        val slots = spec.slots
        val total = slots.size + bonus
        val id = UUID.randomUUID().toString()
        val dir = files.dir(id).apply { mkdirs() }
        val shots = (0 until total).map { i ->
            val aspect = slots[if (i < slots.size) i else 0].aspect
            val w = if (aspect >= 1f) ShotPx else (ShotPx * aspect).roundToInt()
            val h = if (aspect >= 1f) (ShotPx / aspect).roundToInt() else ShotPx
            val bitmap = photo(n * 10 + i + 1, w, h)
            FileOutputStream(files.shot(id, i)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            ShotEntity(id, i, slotIndex = i.takeIf { it < slots.size }, width = w, height = h)
        }
        c.stripExporter.export(spec, shots.filter { it.slotIndex != null }.map { files.shot(id, it.shotIndex) }, dir)
        val session = SessionEntity(
            id = id,
            createdAt = at,
            updatedAt = at,
            layoutId = if (option is FrameOption.Theme) layout.id else SessionEntity.ImageLayoutId,
            frameId = option.id.value,
            filterId = filter.id,
            caption = Caption,
            showDate = true,
            isFavorite = favorite,
        )
        c.database.sessions().insert(session, shots)
    }

    /** A pastel gradient with a white disc, a number and a heart. No people. */
    private fun photo(number: Int, width: Int, height: Int): Bitmap {
        val (top, bottom) = Palettes[number % Palettes.size]
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
        val r = minOf(width, height) / 4.5f
        p.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(width / 2f, height / 2f, r, p)
        p.color = 0xFFF0628F.toInt()
        p.textSize = r
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(number.toString(), width / 2f, height / 2f + p.textSize * 0.36f, p)
        canvas.withTranslation(width * 0.8f, height * 0.22f) {
            scale(r * 0.7f, r * 0.7f)
            drawPath(Stickers.path(StickerKind.Heart), p)
        }
        return bitmap
    }

    companion object {
        const val Caption = "sample ✿"
        const val ClearRequest = "clear"
        const val DeleteRequest = "delete "
        const val DefaultCount = 12
        const val MaxCount = 60
        const val ShotPx = 1000
        const val DayMillis = 86_400_000L
        val Palettes = listOf(
            0xFFFFB3C7.toInt() to 0xFFFFECB3.toInt(),
            0xFFB3DCFF.toInt() to 0xFFD6C4FF.toInt(),
            0xFFBEF0D2.toInt() to 0xFFFFF0B4.toInt(),
            0xFFFFCDAA.toInt() to 0xFFFAAAD2.toInt(),
        )
    }
}
