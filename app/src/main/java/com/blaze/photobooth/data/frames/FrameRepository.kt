package com.blaze.photobooth.data.frames

import android.content.ComponentCallbacks2
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.roundToInt

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

/** Whether an image frame shows in Setup's carousel (README §4.8, §11.2). */
enum class FrameVisibility {
    Shown,

    /** She hid it in Manage frames; it can be shown again. */
    Hidden,

    /** She deleted it, but saved sessions still use it: kept (out of sight) for Edit until they're gone. */
    Deleted,
}

/** An imported frame and where it stands. */
data class CustomFrame(val frame: ImageFrame, val visibility: FrameVisibility)

/** Imported image frames (README §4.9): the Room `frames` table ([CustomFrameStore]). */
interface CustomFrameSource {
    /** Every imported frame in her order, hidden and deleted-but-kept ones included. */
    val frames: Flow<List<CustomFrame>>
}

object EmptyCustomFrameSource : CustomFrameSource {
    override val frames: Flow<List<CustomFrame>> = flowOf(emptyList())
}

/** A row in Manage frames (§4.8): her bundled frames, then her imported ones (deleted ones aren't listed). */
data class ManagedFrame(val frame: ImageFrame, val bundled: Boolean, val hidden: Boolean)

/**
 * Every frame the booth offers (README §5.5.3), in carousel order: her bundled frames
 * (`assets/frames/frames.json`, in file order), then her imported ones (in her order), then the 9 built-in themes.
 * Frames she hid (or deleted while strips still use them) leave the carousel ([options]) but can still be found
 * by id ([allOptions], [find]), so a saved strip or a draft can always be re-rendered with its frame.
 * Artwork bitmaps are decoded off the main thread and cached.
 */
class FrameRepository(
    private val context: Context,
    private val custom: CustomFrameSource = EmptyCustomFrameSource,
    /** Ids of bundled frames she hid (`asset:<id>`), from DataStore. */
    private val hiddenBundled: Flow<Set<FrameId>> = flowOf(emptySet()),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val bundledLock = Mutex()
    private var bundled: List<ImageFrame>? = null

    /** Artwork and thumbnails by `"<frameId>@<maxWidth>"`, sized in kilobytes. */
    private val bitmaps = object : LruCache<String, Bitmap>(BitmapCacheKb) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }

    private class Catalog(val bundled: List<ImageFrame>, val hidden: Set<FrameId>, val custom: List<CustomFrame>)

    private val catalog: Flow<Catalog> =
        combine(flow { emit(bundledFrames()) }, hiddenBundled, custom.frames) { b, hidden, c -> Catalog(b, hidden, c) }

    /** Setup's carousel and Pick's frame tab: everything she hasn't hidden or deleted. */
    val options: Flow<List<FrameOption>> = catalog.map { c ->
        c.bundled.filter { it.id !in c.hidden }.map(FrameOption::Artwork) +
            c.custom.filter { it.visibility == FrameVisibility.Shown }.map { FrameOption.Artwork(it.frame) } +
            FrameTheme.entries.map(FrameOption::Theme)
    }

    /** Every frame that can still be drawn, hidden and deleted-but-kept ones included (Capture, Pick, Edit, Detail). */
    val allOptions: Flow<List<FrameOption>> = catalog.map { c ->
        c.bundled.map(FrameOption::Artwork) + c.custom.map { FrameOption.Artwork(it.frame) } + FrameTheme.entries.map(FrameOption::Theme)
    }

    /** Manage frames (§4.8): her bundled frames, then her imported ones, each with its hidden state. */
    val managed: Flow<List<ManagedFrame>> = catalog.map { c ->
        c.bundled.map { ManagedFrame(it, bundled = true, hidden = it.id in c.hidden) } +
            c.custom.filter { it.visibility != FrameVisibility.Deleted }
                .map { ManagedFrame(it.frame, bundled = false, hidden = it.visibility == FrameVisibility.Hidden) }
    }

    /** The frame with [id], even if it's hidden; null if it's gone (§13). */
    suspend fun find(id: FrameId): FrameOption? = allOptions.first().firstOrNull { it.id == id }

    suspend fun bundledFrames(): List<ImageFrame> = bundledLock.withLock {
        bundled ?: withContext(io) { loadBundled() }.also { bundled = it }
    }

    /**
     * The artwork for [frame], at most [maxWidth] px wide (null = native size; the preview passes
     * [BitmapOps.PreviewMaxPx]).
     */
    suspend fun artwork(frame: ImageFrame, maxWidth: Int? = null): Bitmap =
        load("${frame.id}", frame.artwork, maxWidth, onScreen = maxWidth != null)

    /** The ~300 px carousel thumbnail. */
    suspend fun thumbnail(frame: ImageFrame): Bitmap = load("${frame.id}#thumb", frame.thumb, null, onScreen = true)

    /** [onScreen]: a preview or swatch, so its GPU upload starts at once ([BitmapOps.forScreen]); not for exports. */
    private suspend fun load(name: String, location: ArtworkLocation, maxWidth: Int?, onScreen: Boolean): Bitmap {
        val key = "$name@${maxWidth ?: 0}"
        bitmaps.get(key)?.let { return it }
        return withContext(io) {
            BitmapOps.decode(open = { open(location) }, maxWidth = maxWidth).also { if (onScreen) BitmapOps.forScreen(it) }
        }.also { bitmaps.put(key, it) }
    }

    /**
     * The system is short of memory (M9): with the app out of sight the cached artwork goes (it's quick to decode
     * again); while it's on screen, half of it. Called from `BoothApp.onTrimMemory`.
     */
    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) bitmaps.evictAll() else bitmaps.trimToSize(BitmapCacheKb / 2)
    }

    /** How much artwork is cached, in kilobytes (for tests). */
    internal fun cachedKb(): Int = bitmaps.size()

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

        internal val json = Json { ignoreUnknownKeys = true }

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

/** A rect as `{x, y, w, h}` in frame pixels: `frames.json` slots and the `frames.slots_json` column (§11.2). */
@Serializable
internal data class ManifestRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun toSlot() = SlotRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    companion object {
        fun of(slot: SlotRect) = ManifestRect(
            slot.x.roundToInt(),
            slot.y.roundToInt(),
            (slot.right.roundToInt() - slot.x.roundToInt()),
            (slot.bottom.roundToInt() - slot.y.roundToInt()),
        )
    }
}
