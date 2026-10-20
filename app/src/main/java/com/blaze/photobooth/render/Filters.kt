package com.blaze.photobooth.render

import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.blaze.photobooth.model.PhotoFilter

/**
 * The filters' colour matrices as ready-made colour filters (README §5.3). One matrix per filter
 * feeds export (`Paint.colorFilter`), thumbnails (Compose [ColorFilter]) and, from M3, the live viewfinder.
 */
object Filters {

    private val platform = PhotoFilter.entries.associateWith { ColorMatrixColorFilter(it.matrix) }
    private val compose = PhotoFilter.entries.associateWith { ColorFilter.colorMatrix(ColorMatrix(it.matrix.copyOf())) }

    /** For `Paint.colorFilter`; null for Original so the photo is drawn untouched. */
    fun platform(filter: PhotoFilter): ColorMatrixColorFilter? =
        if (filter == PhotoFilter.Original) null else platform.getValue(filter)

    /** For Compose `Image(colorFilter = …)`; null for Original. */
    fun compose(filter: PhotoFilter): ColorFilter? =
        if (filter == PhotoFilter.Original) null else compose.getValue(filter)
}
