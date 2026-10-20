package com.blaze.photobooth.data.frames

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.FrameCutout
import com.blaze.photobooth.render.FrameImport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * A picked frame image, decoded and analysed, waiting for her to confirm or adjust its windows (README §4.9).
 * Bitmaps live in native memory; the big pixel arrays exist only while detecting and saving.
 */
class ImportedFrame internal constructor(
    /** The artwork at import size (≤ 7200 px long edge, ≤ 12 MP, §13), windows as in the picked file. */
    val artwork: Bitmap,
    /** Mode B's keyed copy (boxes see-through), or null. */
    private val keyed: Bitmap?,
    val analysis: FrameImport.Analysis,
    /** The picked file's own width (before any downscale), for the "may look blurry" warning. */
    val sourceWidth: Int,
    /** [artwork] at most [FrameImporter.PreviewEdge] px on its long edge: the Finding and editor picture. */
    val preview: Bitmap,
    private val previewKeyed: Bitmap?,
) {
    val width: Int get() = artwork.width
    val height: Int get() = artwork.height

    internal fun keyedPixels(atPreview: Boolean): IntArray? = (if (atPreview) previewKeyed else keyed)?.pixels()
}

/** Decodes a picked image, finds its windows, and saves the finished frame (README §4.9, §5.5.2, §11.1). */
class FrameImporter(
    private val context: Context,
    private val store: CustomFrameStore,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** A quick small decode for the "Finding the photo windows…" animation. */
    suspend fun quickPreview(uri: Uri): Bitmap = withContext(io) {
        decode(uri) { w, h -> var s = 1; while (maxOf(w, h) / (s * 2) >= PreviewEdge) s *= 2; s }
            .let { BitmapOps.downscale(it, PreviewEdge) }
    }

    /** Decodes [uri] at import size and runs the detector. Throws if the file can't be read as an image. */
    suspend fun open(uri: Uri): ImportedFrame {
        var sample = 1
        val decoded = withContext(io) {
            decode(uri) { w, h -> FrameImport.sampleSize(w, h).also { sample = it } }
        }
        val sourceWidth = decoded.width * sample
        return withContext(compute) {
            val (tw, th) = FrameImport.targetSize(decoded.width, decoded.height)
            val sized = if (tw == decoded.width && th == decoded.height) decoded else decoded.scale(tw, th).also { decoded.recycle() }
            prepare(sized, sourceWidth)
        }
    }

    /** [artwork] as decoded (ARGB_8888, import size): detect, then make the preview copies. Tests start here. */
    fun prepare(artwork: Bitmap, sourceWidth: Int): ImportedFrame {
        val w = artwork.width
        val h = artwork.height
        val analysis = FrameImport.analyze(artwork.pixels(), w, h, sourceWidth)
        val keyed = analysis.keyed?.let { Bitmap.createBitmap(it, w, h, Bitmap.Config.ARGB_8888) }
        return ImportedFrame(
            artwork = artwork,
            keyed = keyed,
            analysis = FrameImport.Analysis(analysis.windows, analysis.mode, keyed = null, tooSmall = analysis.tooSmall),
            sourceWidth = sourceWidth,
            preview = BitmapOps.downscale(artwork, PreviewEdge),
            previewKeyed = keyed?.let { BitmapOps.downscale(it, PreviewEdge) },
        )
    }

    /** The artwork with [windows] (frame pixels) see-through, at preview size: what Confirm shows. */
    suspend fun cutPreview(frame: ImportedFrame, windows: List<SlotRect>): Bitmap = withContext(compute) {
        cut(frame, frame.preview, atPreview = true, windows)
    }

    /** Cuts the windows at full size and saves `frame.png`, `thumb.png` and the row (§11.1, §11.2). */
    suspend fun save(frame: ImportedFrame, windows: List<SlotRect>, name: String): FrameId {
        val finished = withContext(compute) { cut(frame, frame.artwork, atPreview = false, windows) }
        try {
            return store.add(name, frame.width, frame.height, windows) { artFile, thumbFile ->
                withContext(io) {
                    writePng(finished, artFile)
                    val (thW, thH) = FrameImport.thumbSize(finished.width, finished.height)
                    val thumb = finished.scale(thW, thH)
                    writePng(thumb, thumbFile)
                    if (thumb !== finished) thumb.recycle()
                }
            }
        } finally {
            finished.recycle()
        }
    }

    private fun cut(frame: ImportedFrame, base: Bitmap, atPreview: Boolean, windows: List<SlotRect>): Bitmap {
        val w = base.width
        val h = base.height
        val scale = w / frame.width.toFloat()
        val pixels = base.pixels()
        val actions = FrameCutout.plan(pixels, w, h, scale, frame.analysis.keyedWindows, windows)
        val keyed = if (actions.any { it is FrameCutout.Action.KeepKeyed }) frame.keyedPixels(atPreview) else null
        FrameCutout.apply(pixels, keyed, w, h, scale, actions, FrameCutout.keyPadFor(frame.width, frame.height))
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun decode(uri: Uri, sampleFor: (Int, Int) -> Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.setTargetSampleSize(sampleFor(info.size.width, info.size.height))
        }
        // 16-bit PNGs decode as RGBA_F16; the detector and PNG writer want plain 8-bit ARGB.
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
        }
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Couldn't encode $file" }
            out.fd.sync()
        }
    }

    companion object {
        /** The long edge of the picture shown while finding windows, on Confirm and in the editor. */
        const val PreviewEdge = 1600
    }
}

private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
