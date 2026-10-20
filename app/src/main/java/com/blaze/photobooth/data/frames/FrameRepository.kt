package com.blaze.photobooth.data.frames

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.ImageFrame
import com.blaze.photobooth.model.SlotRect
import com.blaze.photobooth.render.BitmapOps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.io.InputStream

/** One entry in the Setup frame carousel. */
sealed interface FrameOption {
    val id: FrameId

    /** "Her frames ♡": bundled (§5.5.6) or imported on the phone (M7). */
    data class Artwork(val frame: ImageFrame) : FrameOption {
        override val id: FrameId get() = frame.id
    }

    data class Theme(val theme: FrameTheme) : FrameOption {
        override val id: FrameId get() = FrameId.theme(theme)
    }
}

/**
 * Imported image frames (README §4.9). M7 backs this with the Room `frames` table; until then
 * there are none.
 */
interface CustomFrameSource {
    val frames: Flow<List<ImageFrame>>
}

object EmptyCustomFrameSource : CustomFrameSource {
    override val frames: Flow<List<ImageFrame>> = flowOf(emptyList())
}

/**
 * Every frame the booth offers (README §5.5.3), in carousel order: her bundled frames
 * (`assets/frames/frames.json`, in file order), then her imported ones, then the 9 built-in themes.
 * Artwork bitmaps are decoded off the main thread and cached.
 */
class FrameRepository(
    private val context: Context,
    private val custom: CustomFrameSource = EmptyCustomFrameSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val bundledLock = Mutex()
    private var bundled: List<ImageFrame>? = null

    /** Artwork and thumbnails by `"<frameId>@<maxWidth>"`, sized in kilobytes. */
    private val bitmaps = object : LruCache<String, Bitmap>(BitmapCacheKb) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }

    val options: Flow<List<FrameOption>> =
        combine(flow { emit(bundledFrames()) }, custom.frames) { bundled, imported ->
            bundled.map(FrameOption::Artwork) + imported.map(FrameOption::Artwork) + FrameTheme.entries.map(FrameOption::Theme)
        }

    suspend fun bundledFrames(): List<ImageFrame> = bundledLock.withLock {
        bundled ?: withContext(io) { loadBundled() }.also { bundled = it }
    }

    /**
     * The artwork for [frame], at most [maxWidth] px wide (null = native size; the preview passes
     * [BitmapOps.PreviewMaxPx]).
     */
    suspend fun artwork(frame: ImageFrame, maxWidth: Int? = null): Bitmap = load("${frame.id}", frame.artwork, maxWidth)

    /** The ~300 px carousel thumbnail. */
    suspend fun thumbnail(frame: ImageFrame): Bitmap = load("${frame.id}#thumb", frame.thumb, null)

    private suspend fun load(name: String, location: ArtworkLocation, maxWidth: Int?): Bitmap {
        val key = "$name@${maxWidth ?: 0}"
        bitmaps.get(key)?.let { return it }
        return withContext(io) {
            BitmapOps.decode(open = { open(location) }, maxWidth = maxWidth)
        }.also { bitmaps.put(key, it) }
    }

    private fun open(location: ArtworkLocation): InputStream = when (location) {
        is ArtworkLocation.Asset -> context.assets.open(location.path)
        is ArtworkLocation.LocalFile -> FileInputStream(location.file)
    }

    private fun loadBundled(): List<ImageFrame> = try {
        val text = context.assets.open(Manifest).bufferedReader().use { it.readText() }
        parseManifest(text)
    } catch (e: Exception) {
        // A broken manifest must never take Setup down: the themes still work.
        Log.e("FrameRepository", "Couldn't read $Manifest", e)
        emptyList()
    }

    companion object {
        const val AssetDir = "frames"
        const val Manifest = "$AssetDir/frames.json"

        /** 48 MB: room for her five frames at preview size plus a full-size one for export. */
        private const val BitmapCacheKb = 48 * 1024

        private val json = Json { ignoreUnknownKeys = true }

        /** Parses `frames.json` (§5.5.3). Pure, so it's unit-tested against the real manifest. */
        fun parseManifest(text: String): List<ImageFrame> = json.decodeFromString<List<ManifestEntry>>(text).map { e ->
            ImageFrame(
                id = FrameId.asset(e.id),
                name = e.name,
                width = e.width,
                height = e.height,
                slots = e.slots.map { it.toSlot() },
                textArea = e.textArea?.toSlot(),
                artwork = ArtworkLocation.Asset("$AssetDir/${e.file}"),
                thumb = ArtworkLocation.Asset("$AssetDir/${e.thumb}"),
            )
        }
    }
}

@Serializable
private data class ManifestEntry(
    val id: String,
    val name: String,
    val file: String,
    val thumb: String,
    val width: Int,
    val height: Int,
    val slots: List<ManifestRect>,
    val textArea: ManifestRect? = null,
)

@Serializable
private data class ManifestRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun toSlot() = SlotRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
}
