package com.blaze.photobooth.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.PillShape
import kotlinx.coroutines.flow.collectLatest

enum class SquishyButtonStyle { Primary, Tonal, Outlined }

/**
 * A pill button that squishes to 0.92× on press and springs back with an overshoot on release
 * (README §7 A3). The scale lives in a `graphicsLayer`, so pressing never recomposes the content.
 */
@Composable
fun SquishyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SquishyButtonStyle = SquishyButtonStyle.Primary,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(BoothMotion.PressedScale, BoothMotion.snappy())
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    // A quick tap can release before the squish shows; finish it first so every tap feels squishy.
                    if (scale.value > SquishVisibleScale) scale.animateTo(BoothMotion.PressedScale, BoothMotion.snappy())
                    scale.animateTo(1f, BoothMotion.release())
                }
            }
        }
    }
    val squish = Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
    when (style) {
        SquishyButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = modifier.then(squish),
            enabled = enabled,
            shape = PillShape,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        SquishyButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier.then(squish),
            enabled = enabled,
            shape = PillShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = BoothTheme.extraColors.raised,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        SquishyButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.then(squish),
            enabled = enabled,
            shape = PillShape,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

private const val SquishVisibleScale = 0.95f

@Preview(name = "Squishy buttons · light")
@Composable
private fun SquishyButtonPreview() {
    BoothTheme(darkTheme = false) { SquishyButtonSamples() }
}

@Preview(name = "Squishy buttons · dark")
@Composable
private fun SquishyButtonDarkPreview() {
    BoothTheme(darkTheme = true) { SquishyButtonSamples() }
}

/** Shared by the previews and the screenshot test. */
@Composable
internal fun SquishyButtonSamples() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SquishyButton(onClick = {}, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp)) {
                Text("Start ✿", style = MaterialTheme.typography.titleLarge)
            }
            SquishyButton(onClick = {}, style = SquishyButtonStyle.Tonal) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("My Album")
            }
            SquishyButton(onClick = {}, style = SquishyButtonStyle.Outlined) { Text("Maybe later") }
            SquishyButton(onClick = {}, enabled = false) { Text("Disabled") }
        }
    }
}
