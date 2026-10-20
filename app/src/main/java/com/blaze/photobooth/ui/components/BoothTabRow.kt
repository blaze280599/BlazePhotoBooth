package com.blaze.photobooth.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.ui.theme.BoothMotion
import kotlinx.coroutines.launch

/**
 * The pill-shaped tab row on top of a sheet's [HorizontalPager][androidx.compose.foundation.pager.HorizontalPager]
 * (Setup §4.2, Pick §4.4): the selected tab turns strawberry, and tapping one glides the pager there.
 */
@Composable
fun BoothTabRow(
    @StringRes labels: List<Int>,
    pagerState: PagerState,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = pagerState.currentPage == index
            val container by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "tab",
            )
            val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(container)
                    .selectable(selected = selected, role = Role.Tab) {
                        if (!selected) onTick()
                        scope.launch { pagerState.animateScrollToPage(index, animationSpec = BoothMotion.gentle()) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            }
        }
    }
}
