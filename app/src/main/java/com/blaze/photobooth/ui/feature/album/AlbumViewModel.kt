package com.blaze.photobooth.ui.feature.album

import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.DeletedBatch
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.SessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * The album (README §4.6): every saved strip, newest first under month headers, with the filter chips, ♡,
 * multi-select and delete. Deletes are soft (§11.2): cards shrink away, the rows are hidden, and the route shows
 * one Undo snackbar per batch ([undo]).
 */
class AlbumViewModel(
    private val sessions: SessionRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val leaveMillis: Long = LeaveMillis,
) : ViewModel() {

    private val favorites = FavoriteToggles(viewModelScope) { id, favorite -> sessions.setFavorite(id, favorite) }
    private val filter = MutableStateFlow<AlbumFilter>(AlbumFilter.All)
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val leaving = MutableStateFlow<Set<String>>(emptySet())

    /** Picture aspect ratios by `path@version`: read once from the JPEG header, never again. */
    private val aspects = ConcurrentHashMap<String, Float>()

    @Volatile
    private var stored: Map<String, Boolean> = emptyMap()

    private val effectChannel = Channel<AlbumEffect>(Channel.BUFFERED)
    val effects: Flow<AlbumEffect> = effectChannel.receiveAsFlow()

    /** The delete that can still be undone; the route shows its snackbar. */
    val undo: StateFlow<DeletedBatch?> = sessions.pendingUndo

    private val items: Flow<List<AlbumItem>> = sessions.sessions
        .map { list -> list.map(::itemOf) }
        .flowOn(io)
        .onEach { list ->
            stored = list.associate { it.id to it.favorite }
            favorites.onStored(stored)
        }

    val uiState: StateFlow<AlbumUiState> =
        combine(items, favorites.overrides, filter, selection, leaving) { all, overrides, chosen, selected, gone ->
            val shown = if (overrides.isEmpty()) all else all.map { item -> overrides[item.id]?.let { item.copy(favorite = it) } ?: item }
            val filters = AlbumGrouping.filters(shown)
            val active = chosen.takeIf { it in filters } ?: AlbumFilter.All
            val ids = shown.mapTo(HashSet()) { it.id }
            AlbumUiState(
                loaded = true,
                total = shown.size,
                entries = AlbumGrouping.group(AlbumGrouping.filter(shown, active)),
                filters = filters,
                filter = active,
                selection = selected.filterTo(HashSet()) { it in ids },
                leaving = gone,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), AlbumUiState())

    fun onEvent(event: AlbumEvent) {
        when (event) {
            is AlbumEvent.Tap -> if (selection.value.isNotEmpty()) toggleSelected(event.id) else effectChannel.trySend(AlbumEffect.Open(event.id))
            is AlbumEvent.LongPress -> selection.update { it + event.id }
            is AlbumEvent.ToggleFavorite -> favorites.toggle(event.id, stored[event.id] ?: false)
            is AlbumEvent.SelectFilter -> {
                filter.value = event.filter
                selection.value = emptySet()
            }
            AlbumEvent.ClearSelection -> selection.value = emptySet()
            AlbumEvent.DeleteSelected -> {
                val ids = selection.value
                selection.value = emptySet()
                delete(ids)
            }
            AlbumEvent.Back, AlbumEvent.Start -> Unit // the route's
        }
    }

    /** Undo (§4.7): the cards come back. */
    fun undo(batch: DeletedBatch) {
        leaving.update { it - batch.ids.toSet() }
        viewModelScope.launch { sessions.restore(batch) }
    }

    /** The Undo snackbar timed out or was swiped away. */
    fun undoExpired(batch: DeletedBatch) = sessions.dismissUndo(batch)

    private fun toggleSelected(id: String) = selection.update { if (id in it) it - id else it + id }

    /** The cards shrink and fade first (§4.7), then the soft delete lands and the rest of the grid closes up. */
    private fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        leaving.update { it + ids }
        viewModelScope.launch {
            delay(leaveMillis)
            try {
                sessions.softDelete(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Delete failed", e)
                leaving.update { it - ids }
            }
        }
    }

    private fun itemOf(session: SessionEntity): AlbumItem {
        val image = sessions.files.thumbOrStrip(session.id)
        val version = image.lastModified()
        return AlbumItem(
            id = session.id,
            createdAt = session.createdAt,
            date = AlbumGrouping.dateOf(session.createdAt, zone()),
            layoutId = session.layoutId,
            favorite = session.isFavorite,
            image = image,
            version = version,
            aspect = aspects.getOrPut("${image.path}@$version") { aspectOf(image) },
        )
    }

    companion object {
        private const val Tag = "AlbumViewModel"
        private const val StopTimeoutMillis = 5_000L

        /** How long a deleted card takes to shrink away (§4.7). */
        const val LeaveMillis = 260L

        /** A classic strip's shape, for a picture that can't be read. */
        private const val FallbackAspect = 1f / 3f

        /** Width / height from the JPEG header (no pixels decoded). */
        fun aspectOf(file: File): Float {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else FallbackAspect
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                AlbumViewModel(app.container.sessionRepository)
            }
        }
    }
}
