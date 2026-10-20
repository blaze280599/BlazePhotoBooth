package com.blaze.photobooth.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blaze.photobooth.R

/**
 * A toolbar action that isn't built yet (Result §4.5, Detail §4.7, Album's multi-select): dimmed, with a "Soon"
 * tag. Saving to Photos and sharing arrive in M6, which swaps these for real buttons. [width] null lets the caller size it
 * (e.g. `Modifier.weight(1f)`).
 */
@Composable
fun SoonAction(icon: ImageVector, @StringRes label: Int, modifier: Modifier = Modifier, width: Dp? = 96.dp) {
    val text = stringResource(label)
    val description = stringResource(R.string.cd_result_soon, text)
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .padding(12.dp)
                    .alpha(0.5f),
            )
            Text(
                stringResource(R.string.result_soon),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 14.dp, y = (-4).dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
