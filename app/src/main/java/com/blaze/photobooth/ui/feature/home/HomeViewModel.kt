package com.blaze.photobooth.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blaze.photobooth.BoothApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val source: HomeSource) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(source.albumCount, source.recentStrip, source.draft) { count, recent, draft ->
            HomeUiState(albumCount = count, recentStrip = recent, draft = draft?.takeIf { it.shotCount > 0 })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    fun discardDraft() {
        viewModelScope.launch { source.discardDraft() }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                HomeViewModel(app.container.homeSource)
            }
        }
    }
}
