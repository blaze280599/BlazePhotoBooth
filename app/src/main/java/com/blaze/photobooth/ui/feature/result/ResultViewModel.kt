package com.blaze.photobooth.ui.feature.result

import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.data.SessionRepository
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.export.AutoSaveStatus
import com.blaze.photobooth.data.export.ExportItem
import com.blaze.photobooth.data.export.ExportJob
import com.blaze.photobooth.data.export.ExportService
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.ui.feature.export.ExportKey
import com.blaze.photobooth.ui.feature.export.ExportMessage
import com.blaze.photobooth.ui.feature.export.ExportPhase
import com.blaze.photobooth.ui.feature.export.ExportTasks
import com.blaze.photobooth.ui.feature.export.SavedWhat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the saved `strip.jpg` of [sessionId], sized for the screen, and runs Save strip, Save photos and Share
 * (§4.5, §11.5). With Settings' auto-save on (§4.8), [autoSave] reports the save Print started, and Save strip
 * shows it.
 */
class ResultViewModel(
    private val sessionId: String,
    private val sessions: SessionRepository,
    exports: ExportService,
    autoSave: Flow<Map<String, AutoSaveStatus>>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = state.asStateFlow()

    private val tasks = ExportTasks(viewModelScope, exports)
    val messages: Flow<ExportMessage> = tasks.messages

    private var session: SessionEntity? = null
    private var cutJobs: List<ExportJob> = emptyList()

    init {
        viewModelScope.launch {
            val row = sessions.session(sessionId)
            session = row
            val strip = try {
                withContext(io) {
                    val file = sessions.files.strip(sessionId)
                    BitmapOps.forScreen(BitmapOps.decode(open = { file.inputStream() }, maxWidth = DisplayMaxPx)).asImageBitmap()
                }
            } catch (e: Exception) {
                Log.e("ResultViewModel", "No strip for $sessionId", e)
                null
            }
            cutJobs = row?.let { r -> ExportItem.cutsOf(sessions.shots(sessionId)).map { ExportJob(r, it) } }.orEmpty()
            state.update {
                it.copy(strip = strip, saved = row != null, missing = row == null || strip == null, cuts = cutJobs.size)
            }
        }
        viewModelScope.launch { tasks.actions.collect { a -> state.update { it.copy(actions = a) } } }
        viewModelScope.launch {
            autoSave.map { it[sessionId] }.distinctUntilChanged().collect { status ->
                when (status) {
                    AutoSaveStatus.Saved -> tasks.markDone(ExportKey.Strip)
                    AutoSaveStatus.Failed -> tasks.post(ExportMessage.AutoSaveFailed(retry = { onEvent(ResultEvent.SaveStrip) }))
                    AutoSaveStatus.Saving, null -> Unit
                }
                state.update { it.copy(autoSave = status) }
            }
        }
    }

    fun onEvent(event: ResultEvent) {
        val row = session ?: return
        // The auto-save is still writing the strip: one export at a time.
        if (state.value.autoSave == AutoSaveStatus.Saving) return
        when (event) {
            ResultEvent.SaveStrip ->
                if (tasks.actions.value[ExportKey.Strip].phase == ExportPhase.Idle) {
                    tasks.save(ExportKey.Strip, SavedWhat.Strip, listOf(ExportJob(row, ExportItem.Strip)))
                }
            ResultEvent.SavePhotos -> tasks.save(ExportKey.AllPhotos, SavedWhat.Photos, cutJobs)
            ResultEvent.Share -> tasks.share(listOf(ExportJob(row, ExportItem.Strip)))
            ResultEvent.Again, ResultEvent.Album, ResultEvent.Home -> Unit // the route's
        }
    }

    companion object {
        /** The strip never shows wider than about half the screen, so 900 px is plenty and light on memory. */
        private const val DisplayMaxPx = 900

        fun factory(sessionId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                val c = app.container
                ResultViewModel(sessionId, c.sessionRepository, c.exports, c.autoSaver.status)
            }
        }
    }
}
