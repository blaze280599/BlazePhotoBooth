package com.blaze.photobooth.ui.feature.detail

import androidx.compose.runtime.Immutable
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.ui.feature.export.ExportActions
import java.io.File
import java.time.LocalDate

/**
 * One saved strip (README §4.7). [strip] is `strip.jpg` (or the thumbnail if the strip is lost) and [thumb] the
 * album's picture, used as the hero's placeholder so the shared element (§7 A10) has a picture from its first
 * frame. [cuts] are the photos in the strip in slot order; [unused] are the bonus shots that didn't make it.
 */
@Immutable
data class DetailUiState(
    val loaded: Boolean = false,
    val sessionId: String = "",
    val date: LocalDate? = null,
    val frame: DetailFrame = DetailFrame.Unknown,
    val filter: PhotoFilter = PhotoFilter.Original,
    val favorite: Boolean = false,
    val strip: File? = null,
    val stripVersion: Long = 0,
    val thumb: File? = null,
    val thumbVersion: Long = 0,
    /** The strip's width / height. */
    val aspect: Float = 1f / 3f,
    val cuts: List<DetailPhoto> = emptyList(),
    val unused: List<DetailPhoto> = emptyList(),
    /** Delete was tapped: the strip shrinks away, then the route goes back to Album. */
    val deleting: Boolean = false,
    /** The session is gone (deleted elsewhere): the route leaves. */
    val missing: Boolean = false,
    /** Save strip, Save photo, Save all and Share (§11.5). */
    val actions: ExportActions = ExportActions(),
) {
    /** The Photos pager: the cuts, then the unused shots. */
    val photos: List<DetailPhoto> get() = cuts + unused
}

/** One original shot, shown with the session's filter (§4.7). [aspect] is width / height. */
@Immutable
data class DetailPhoto(val shotIndex: Int, val file: File, val aspect: Float, val version: Long)

/** What the strip was printed on, for the subtitle. A frame that no longer exists still has its saved strip. */
@Immutable
sealed interface DetailFrame {
    data class Theme(val theme: FrameTheme) : DetailFrame
    data class Named(val name: String) : DetailFrame
    data object Unknown : DetailFrame
}

sealed interface DetailEvent {
    data object Back : DetailEvent
    data object ToggleFavorite : DetailEvent
    data object Edit : DetailEvent
    data object Delete : DetailEvent
    data object SaveStrip : DetailEvent

    /** *Save this photo*: the Photos pager's page in view (a cut or an unused shot). */
    data class SavePhoto(val shotIndex: Int) : DetailEvent
    data object SaveAll : DetailEvent
    data class Share(val what: ShareWhat) : DetailEvent
}

/** What Detail's Share menu sends (§11.5). */
sealed interface ShareWhat {
    data object Strip : ShareWhat
    data class Photo(val shotIndex: Int) : ShareWhat

    /** Every cut, in strip order. */
    data object AllPhotos : ShareWhat
}

sealed interface DetailEffect {
    data class Deleted(val sessionId: String) : DetailEffect
}
