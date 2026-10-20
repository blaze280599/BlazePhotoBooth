package com.blaze.photobooth.ui.feature.pick

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.blaze.photobooth.BoothApp
import com.blaze.photobooth.R
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.render.Filters
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** The draft's shots, for the M3 stand-in of Pick. */
@Immutable
data class PickPlaceholderState(val shots: List<PickShot> = emptyList(), val filter: PhotoFilter = PhotoFilter.Original)

/** One stored shot and its width ÷ height. */
@Immutable
data class PickShot(val index: Int, val file: File, val aspect: Float)

class PickPlaceholderViewModel(draftStore: BoothDraftStore) : ViewModel() {
    val state: StateFlow<PickPlaceholderState> = draftStore.draft.map { d ->
        PickPlaceholderState(
            shots = d?.shots.orEmpty().map { PickShot(it.index, draftStore.shotFile(it.index), it.width.toFloat() / it.height) },
            filter = d?.config?.toConfig()?.filter ?: PhotoFilter.Original,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickPlaceholderState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as BoothApp
                PickPlaceholderViewModel(app.container.draftStore)
            }
        }
    }
}

/**
 * Where Capture lands until M4 builds Pick (§4.4): every shot in the draft in a 3-column grid (with the
 * chosen filter), so a finished sequence can be checked, plus the placeholder's Print ✿ walk-through.
 */
@Composable
fun PickPlaceholderRoute(
    onBack: () -> Unit,
    onPrint: () -> Unit,
    viewModel: PickPlaceholderViewModel = viewModel(factory = PickPlaceholderViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PickPlaceholderScreen(state, onBack, onPrint)
}

@Composable
fun PickPlaceholderScreen(state: PickPlaceholderState, onBack: () -> Unit, onPrint: () -> Unit) {
    val extra = BoothTheme.extraColors
    val colorFilter = Filters.compose(state.filter)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = extra.raised,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                text = stringResource(R.string.placeholder_pick_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.placeholder_coming, "M4"),
                style = MaterialTheme.typography.titleLarge.handwritten(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = pluralStringResource(R.plurals.pick_placeholder_shots, state.shots.size, state.shots.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.shots, key = { it.index }) { shot ->
                    AsyncImage(
                        model = shot.file,
                        contentDescription = stringResource(R.string.cd_shot, shot.index + 1),
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(shot.aspect.coerceIn(0.5f, 2f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
            SquishyButton(
                onClick = onPrint,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Text(stringResource(R.string.placeholder_pick_next), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
