package com.blaze.photobooth.ui.feature.album

import androidx.compose.runtime.Immutable
import com.blaze.photobooth.ui.feature.export.ExportActions

/**
 * Everything the album shows (README §4.6). [entries] are the filtered strips under their month headers; [total]
 * counts every strip, so an empty filter ("no favourites yet") is told apart from an empty album. [selection] is
 * the multi-select (empty = not selecting); [leaving] are cards shrinking away before their soft delete lands.
 */
@Immutable
data class AlbumUiState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val entries: List<AlbumEntry> = emptyList(),
    val filters: List<AlbumFilter> = listOf(AlbumFilter.All, AlbumFilter.Favorites),
    val filter: AlbumFilter = AlbumFilter.All,
    val selection: Set<String> = emptySet(),
    val leaving: Set<String> = emptySet(),
    /** The multi-select's *Save to Photos* (§4.6, §11.5). */
    val actions: ExportActions = ExportActions(),
) {
    val selecting: Boolean get() = selection.isNotEmpty()
    val empty: Boolean get() = loaded && total == 0
    val shown: Int get() = entries.count { it is AlbumEntry.Strip }
}

sealed interface AlbumEvent {
    /** Tap a card: open it, or (while selecting) select it. */
    data class Tap(val id: String) : AlbumEvent

    /** Long-press a card: start (or extend) the multi-select. */
    data class LongPress(val id: String) : AlbumEvent
    data class ToggleFavorite(val id: String) : AlbumEvent
    data class SelectFilter(val filter: AlbumFilter) : AlbumEvent
    data object ClearSelection : AlbumEvent
    data object DeleteSelected : AlbumEvent

    /** Saves every selected strip to Photos, one after another (§4.6). */
    data object SaveSelected : AlbumEvent
    data object Back : AlbumEvent

    /** The empty state's Start button. */
    data object Start : AlbumEvent
}

sealed interface AlbumEffect {
    data class Open(val id: String) : AlbumEffect
}
