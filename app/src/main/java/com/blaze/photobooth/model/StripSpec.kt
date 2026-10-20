package com.blaze.photobooth.model

import android.graphics.Bitmap
import com.blaze.photobooth.render.LayoutGeometry
import java.io.File
import java.time.LocalDate

/** A rectangle in strip canvas pixels. Pure Kotlin so geometry and slot detection are JVM-testable. */
data class SlotRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val aspect: Float get() = w / h

    fun overlaps(other: SlotRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}

data class CanvasSize(val width: Int, val height: Int)

/** Where an image frame's files live. */
sealed interface ArtworkLocation {
    /** Bundled under `assets/` (her frames, §5.5.3). */
    data class Asset(val path: String) : ArtworkLocation

    /** Imported on the phone into `files/frames/<uuid>/` (§4.9, M7). */
    data class LocalFile(val file: File) : ArtworkLocation
}

/**
 * An artwork frame (§5.5): a picture with transparent windows. [slots] are the windows in the
 * artwork's own pixels, already in row-major order; their count sets the number of cuts.
 */
data class ImageFrame(
    val id: FrameId,
    val name: String,
    val width: Int,
    val height: Int,
    val slots: List<SlotRect>,
    val textArea: SlotRect?,
    val artwork: ArtworkLocation,
    val thumb: ArtworkLocation,
)

/**
 * The two kinds of frame, behind one entry point (README §10). Everything downstream asks the
 * [StripSpec] for `slots` and `canvasSize`, never which kind of frame it is.
 */
sealed interface FrameSource {
    val id: FrameId

    data class Theme(val theme: FrameTheme) : FrameSource {
        override val id: FrameId get() = FrameId.theme(theme)
    }

    /** [bitmap] is the artwork at any resolution; it's stretched over the frame's native canvas. */
    class Image(val frame: ImageFrame, val bitmap: Bitmap) : FrameSource {
        override val id: FrameId get() = frame.id
    }
}

/** One stored shot (§9.4). Originals stay unfiltered. */
data class Shot(val index: Int, val file: File, val width: Int, val height: Int)

/**
 * What to draw (§10). [layout] only applies to theme frames; image frames bring their own geometry.
 * [preview] adds the cute placeholder silhouettes in empty cells (Setup, Pick); exports never set it.
 */
data class StripSpec(
    val frame: FrameSource,
    val layout: BoothLayout = BoothLayout.Default,
    val filter: PhotoFilter = PhotoFilter.Original,
    val caption: String? = null,
    val date: LocalDate? = null,
    val preview: Boolean = false,
) {
    val canvasSize: CanvasSize
        get() = when (frame) {
            is FrameSource.Theme -> CanvasSize(layout.canvasWidth, layout.canvasHeight)
            is FrameSource.Image -> CanvasSize(frame.frame.width, frame.frame.height)
        }

    val slots: List<SlotRect>
        get() = when (frame) {
            is FrameSource.Theme -> LayoutGeometry.cells(layout)
            is FrameSource.Image -> frame.frame.slots
        }

    val cuts: Int get() = slots.size
}
