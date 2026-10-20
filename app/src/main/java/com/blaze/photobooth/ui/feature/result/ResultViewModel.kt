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
import com.blaze.photobooth.render.BitmapOps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads the saved `strip.jpg` of [sessionId], sized for the screen. */
class ResultViewModel(
    private val sessionId: String,
    private val sessions: SessionRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val state = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessions.session(sessionId)
            val strip = try {
                withContext(io) {
                    val file = sessions.files.strip(sessionId)
                    BitmapOps.decode(open = { file.inputStream() }, maxWidth = DisplayMaxPx).asImageBitmap()
                }
            } catch (e: Exception) {
                Log.e("ResultViewModel", "No strip for $sessionId", e)
                null
            }
            state.update { it.copy(strip = strip, saved = session != null, missing = session == null || strip == null) }
        }
    }

    companion object {
        /** The strip never shows wider than about half the screen, so 900 px is plenty and light on memory. */
        private const val DisplayMaxPx = 900

        fun factory(sessionId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                ResultViewModel(sessionId, app.container.sessionRepository)
            }
        }
    }
}
