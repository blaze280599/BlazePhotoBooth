package com.blaze.photobooth.ui.feature.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blaze.photobooth.R
import com.blaze.photobooth.core.LocalReduceMotion
import com.blaze.photobooth.model.Pose
import com.blaze.photobooth.ui.components.FitText
import com.blaze.photobooth.ui.components.fixedScale
import com.blaze.photobooth.ui.theme.BoothMotion
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.handwritten

/** Which pose card Capture shows (README §5.6.1). */
enum class PoseCardKind(val large: Boolean) {
    /** Before the first shot: a big card, *"First up"*. */
    FirstUp(large = true),

    /** Idle or paused later in a session (a continued draft, a retake): the shot that's next. */
    UpNext(large = true),

    /** During the countdown: the same card, a little smaller. */
    Now(large = false),

    /** During the 1.2 s "Nice!" beat: *"Next: …"*, so they can get ready. */
    Next(large = false),
}

/**
 * The pose card (§5.6.1): a big emoji, the pose name in Fredoka, the Korean name in Gaegu and a 🔀. Tapping it
 * (anywhere) swaps the pose. It pops in on the *Bouncy* spring and cross-fades when the pose or kind changes; with
 * reduce motion it only fades. For TalkBack it is one polite live region, so the pose is read out whenever it
 * changes, and in particular when each countdown starts (*"Pose: Cheek hearts"*).
 *
 * It sits in a fixed spot over the viewfinder, so with big fonts (§6.7) its words grow but shrink back to fit
 * (never below their 100% size), and the emoji keeps its size.
 */
@Composable
fun PoseCard(kind: PoseCardKind, pose: Pose, onSwap: (() -> Unit)?, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val name = stringResource(pose.name)
    val spoken = stringResource(
        when (kind) {
            PoseCardKind.FirstUp -> R.string.cd_pose_first_up
            PoseCardKind.UpNext -> R.string.cd_pose_up_next
            PoseCardKind.Now -> R.string.cd_pose_now
            PoseCardKind.Next -> R.string.cd_pose_next
        },
        name,
    )
    val swapLabel = stringResource(R.string.cd_pose_swap)
    Box(
        modifier
            .clip(CardShape)
            .then(if (onSwap != null) Modifier.clickable(onClickLabel = swapLabel, role = Role.Button, onClick = onSwap) else Modifier)
            // One stop for TalkBack: the words, read politely whenever they change, with "Swap the pose" as the action.
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
                if (onSwap != null) {
                    role = Role.Button
                    onClick(label = swapLabel) {
                        onSwap()
                        true
                    }
                }
            },
    ) {
        AnimatedContent(
            targetState = kind to pose,
            transitionSpec = {
                val change = if (reduceMotion) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    (scaleIn(BoothMotion.bouncy(), initialScale = 0.85f) + fadeIn()) togetherWith fadeOut()
                }
                change using SizeTransform(clip = false)
            },
            contentAlignment = Alignment.TopCenter,
            label = "pose",
        ) { (k, p) ->
            CardBody(k, p, swappable = onSwap != null)
        }
    }
}

@Composable
private fun CardBody(kind: PoseCardKind, pose: Pose, swappable: Boolean) {
    val large = kind.large
    Surface(
        shape = CardShape,
        color = BoothTheme.extraColors.raised.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (large) 14.dp else 10.dp),
        ) {
            Text(pose.emoji, fontSize = (if (large) 44.sp else 30.sp).fixedScale(), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f)) {
                val label = when (kind) {
                    PoseCardKind.FirstUp -> R.string.pose_first_up
                    PoseCardKind.UpNext -> R.string.pose_up_next
                    PoseCardKind.Next -> R.string.pose_next
                    PoseCardKind.Now -> null
                }
                if (label != null) {
                    FitText(
                        stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Start,
                    )
                }
                FitText(
                    stringResource(pose.name),
                    style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                )
                if (pose.korean != null) {
                    FitText(
                        stringResource(pose.korean),
                        style = (if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium).handwritten(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            if (swappable) {
                // The 🔀 shows the card can be tapped; the whole card is the (≥ 48 dp) target.
                Box(
                    Modifier
                        .size(if (large) 40.dp else 34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(if (large) 22.dp else 18.dp),
                    )
                }
            }
        }
    }
}

private val CardShape = RoundedCornerShape(20.dp)
