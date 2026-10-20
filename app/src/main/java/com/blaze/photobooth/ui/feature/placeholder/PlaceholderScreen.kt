package com.blaze.photobooth.ui.feature.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blaze.photobooth.R
import com.blaze.photobooth.ui.components.BlobShape
import com.blaze.photobooth.ui.components.FloatingParticles
import com.blaze.photobooth.ui.components.SquishyButton
import com.blaze.photobooth.ui.components.SquishyButtonStyle
import com.blaze.photobooth.ui.components.boothParticleColors
import com.blaze.photobooth.ui.theme.BlobPolygons
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten

@Immutable
data class PlaceholderAction(@param:StringRes val label: Int, val onClick: () -> Unit)

/**
 * A cute stand-in for a screen that a later milestone builds (M1 shell, README §16).
 * It has a back button and buttons that walk the real flow onward, so every destination
 * and predictive back can be tried today.
 */
@Composable
fun PlaceholderScreen(
    emoji: String,
    @StringRes title: Int,
    @StringRes body: Int,
    milestone: String,
    onBack: () -> Unit,
    actions: List<PlaceholderAction>,
    modifier: Modifier = Modifier,
) {
    val extra = BoothTheme.extraColors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extra.gradientTop, extra.gradientBottom))),
    ) {
        FloatingParticles(colors = boothParticleColors(), modifier = Modifier.fillMaxSize(), count = 8, seed = emoji.hashCode())
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = BoothTheme.extraColors.raised,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Spacer(Modifier.weight(1f))
            BlobShape(
                modifier = Modifier.size(150.dp),
                from = BlobPolygons.Flower,
                to = BlobPolygons.Cookie,
                brush = Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
                ),
            ) {
                Text(emoji, fontSize = 56.sp, modifier = Modifier.clearAndSetSemantics {})
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.placeholder_coming, milestone),
                style = MaterialTheme.typography.titleLarge.handwritten(),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1.2f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.forEachIndexed { index, action ->
                    SquishyButton(
                        onClick = action.onClick,
                        modifier = Modifier.fillMaxWidth(),
                        style = if (index == 0) SquishyButtonStyle.Primary else SquishyButtonStyle.Tonal,
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) {
                        Text(stringResource(action.label), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Preview(name = "Placeholder · light", widthDp = 411, heightDp = 923)
@Composable
private fun PlaceholderPreview() {
    BoothTheme(darkTheme = false) { PlaceholderSample() }
}

@Preview(name = "Placeholder · dark", widthDp = 411, heightDp = 923)
@Composable
private fun PlaceholderDarkPreview() {
    BoothTheme(darkTheme = true) { PlaceholderSample() }
}

/** Shared by the previews and the screenshot test. */
@Composable
internal fun PlaceholderSample() {
    PlaceholderScreen(
        emoji = "🎀",
        title = R.string.placeholder_setup_title,
        body = R.string.placeholder_setup_body,
        milestone = "M2",
        onBack = {},
        actions = listOf(PlaceholderAction(R.string.placeholder_setup_next) {}),
    )
}
