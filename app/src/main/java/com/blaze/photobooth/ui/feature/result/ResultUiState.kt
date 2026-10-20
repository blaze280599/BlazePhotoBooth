package com.blaze.photobooth.ui.feature.result

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** What Result shows (README §4.5): the printed strip, already saved in the album. */
@Immutable
data class ResultUiState(
    val strip: ImageBitmap? = null,
    /** The session is in Room and on disk (§11.1): the "Saved to your album ♡" chip is true. */
    val saved: Boolean = false,
    /** The session doesn't exist (e.g. deleted): the route goes Home. */
    val missing: Boolean = false,
) {
    val aspect: Float get() = strip?.let { it.width.toFloat() / it.height } ?: (1f / 3f)
}

sealed interface ResultEvent {
    data object Again : ResultEvent
    data object Album : ResultEvent
    data object Home : ResultEvent
}
