package com.blaze.photobooth.ui.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.ui.feature.album.AlbumGrouping
import com.blaze.photobooth.ui.feature.album.AlbumViewModel
import com.blaze.photobooth.ui.feature.album.FavoriteToggles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Detail (README §4.7): one saved strip, its cuts and unused shots, ♡, Edit and Delete. It follows the session's
 * row, so a favourite from anywhere or a saved Edit shows up at once.
 */
class DetailViewModel(
    private val sessionId: String,
    private val sessions: SessionRepository,
    private val frames: FrameRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow(DetailUiState(sessionId = sessionId))
    val uiState: StateFlow<DetailUiState> = state.asStateFlow()

    private val effectChannel = Channel<DetailEffect>(Channel.BUFFERED)
    val effects: Flow<DetailEffect> = effectChannel.receiveAsFlow()

    private val favorites = FavoriteToggles(viewModelScope) { id, favorite -> sessions.setFavorite(id, favorite) }

    @Volatile
    private var storedFavorite = false

    init {
        viewModelScope.launch {
            var loadedVersion: Long? = null
            sessions.observe(sessionId).collect { session ->
                if (session == null || session.deletedAt != null) {
                    if (!state.value.deleting) state.update { it.copy(missing = true) }
                    return@collect
                }
                storedFavorite = session.isFavorite
                favorites.onStored(mapOf(sessionId to session.isFavorite))
                if (loadedVersion != session.updatedAt) {
                    loadedVersion = session.updatedAt
                    load(session)
                }
                state.update { it.copy(favorite = favorites.resolve(sessionId, session.isFavorite)) }
            }
        }
        viewModelScope.launch {
            favorites.overrides.collect { overrides ->
                state.update { it.copy(favorite = overrides[sessionId] ?: storedFavorite) }
            }
        }
    }

    fun onEvent(event: DetailEvent) {
        when (event) {
            DetailEvent.ToggleFavorite -> favorites.toggle(sessionId, storedFavorite)
            DetailEvent.Delete -> delete()
            DetailEvent.Back, DetailEvent.Edit -> Unit // the route's
        }
    }

    private suspend fun load(session: SessionEntity) {
        val files = sessions.files
        val loaded = try {
            withContext(io) {
                val shots = sessions.shots(sessionId)
                val photos = shots.map { shot ->
                    val file = files.shot(sessionId, shot.shotIndex)
                    shot to DetailPhoto(
                        shotIndex = shot.shotIndex,
                        file = file,
                        aspect = if (shot.height > 0) shot.width.toFloat() / shot.height else 1f,
                        version = file.lastModified(),
                    )
                }
                val strip = files.stripOrThumb(sessionId)
                val thumb = files.thumbOrStrip(sessionId)
                state.value.copy(
                    loaded = true,
                    date = AlbumGrouping.dateOf(session.createdAt, zone()),
                    frame = frameOf(session),
                    filter = PhotoFilter.fromId(session.filterId) ?: PhotoFilter.Original,
                    strip = strip,
                    stripVersion = strip.lastModified(),
                    thumb = thumb,
                    thumbVersion = thumb.lastModified(),
                    aspect = AlbumViewModel.aspectOf(strip),
                    cuts = photos.filter { it.first.slotIndex != null }.sortedBy { it.first.slotIndex }.map { it.second },
                    unused = photos.filter { it.first.slotIndex == null }.map { it.second },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(Tag, "Couldn't load $sessionId", e)
            state.value.copy(loaded = true)
        }
        state.value = loaded
    }

    private suspend fun frameOf(session: SessionEntity): DetailFrame {
        val id = FrameId(session.frameId)
        return when (id.kind) {
            FrameId.Kind.Theme -> FrameTheme.fromId(id.localId)?.let { DetailFrame.Theme(it) } ?: DetailFrame.Unknown
            FrameId.Kind.Asset, FrameId.Kind.Custom ->
                frames.bundledFrames().firstOrNull { it.id == id }?.let { DetailFrame.Named(it.name) } ?: DetailFrame.Unknown
            null -> DetailFrame.Unknown
        }
    }

    /** The strip shrinks and fades (§4.7), then the soft delete lands and the route returns to Album for Undo. */
    private fun delete() {
        if (state.value.deleting) return
        state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            delay(AlbumViewModel.LeaveMillis)
            try {
                sessions.softDelete(listOf(sessionId))
                effectChannel.send(DetailEffect.Deleted(sessionId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Delete failed", e)
                state.update { it.copy(deleting = false) }
            }
        }
    }

    companion object {
        private const val Tag = "DetailViewModel"

        fun factory(sessionId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                DetailViewModel(sessionId, app.container.sessionRepository, app.container.frameRepository)
            }
        }
    }
}
