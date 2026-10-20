package com.blaze.photobooth.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.blaze.photobooth.R
import com.blaze.photobooth.ui.feature.capture.CaptureRoute
import com.blaze.photobooth.ui.feature.home.HomeRoute
import com.blaze.photobooth.ui.feature.pick.PickRoute
import com.blaze.photobooth.ui.feature.result.ResultRoute
import com.blaze.photobooth.ui.feature.placeholder.PlaceholderAction
import com.blaze.photobooth.ui.feature.placeholder.PlaceholderScreen
import com.blaze.photobooth.ui.feature.setup.SetupRoute
import com.blaze.photobooth.ui.theme.BoothMotion
import kotlin.math.roundToInt

/**
 * The shared-transition scope every screen sits in, for shared elements such as the
 * Album → Detail strip hero (§7 A10). Pair it with Nav 3's `LocalNavAnimatedContentScope`.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * The app's navigation (README §8.3): a Nav 3 back stack of typed keys shown by [NavDisplay] inside a
 * [SharedTransitionLayout]. Each entry gets its own saveable state and ViewModel store. Forward and back
 * use the §7 A1 slide-and-fade; predictive back scrubs the same pop animation.
 *
 * Until their milestones land, Album, Detail, Add frame and Pick's edit mode are [PlaceholderScreen]s that walk
 * the real flow.
 */
@Composable
fun BoothNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)

    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sharedTransitionScope = this,
                transitionSpec = { forwardTransition() },
                popTransitionSpec = { popTransition() },
                predictivePopTransitionSpec = { popTransition() },
                entryProvider = entryProvider {
                    entry<Home> {
                        HomeRoute(
                            onStart = { backStack.go(Setup) },
                            onOpenAlbum = { backStack.go(Album) },
                            onOpenStrip = { backStack.go(Detail(it)) },
                            onContinueDraft = { complete -> backStack.go(if (complete) Pick() else Capture()) },
                        )
                    }
                    entry<Setup> {
                        SetupRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onStart = { backStack.go(Capture()) },
                            onAddFrame = { backStack.go(AddFrame) },
                        )
                    }
                    entry<AddFrame> {
                        Placeholder(
                            emoji = "🖼️", title = R.string.placeholder_add_frame_title, body = R.string.placeholder_add_frame_body,
                            milestone = "M7", backStack = backStack,
                        )
                    }
                    entry<Capture> { key ->
                        val retake = key.retakeIndex
                        CaptureRoute(
                            retakeIndex = retake,
                            onLeave = { backStack.removeLastOrNull() },
                            // Capture is done with once every shot is taken: back from Pick doesn't re-enter it.
                            // A retake lands back on the Pick underneath it.
                            onFinished = {
                                backStack.removeAll { it is Capture }
                                if (retake == null) backStack.go(Pick())
                            },
                        )
                    }
                    entry<Pick> { key ->
                        if (key.editSessionId == null) {
                            PickRoute(
                                onRetakeAll = {
                                    backStack.removeAll { it is Pick }
                                    backStack.go(Capture())
                                },
                                onRetake = { backStack.go(Capture(retakeIndex = it)) },
                                onDiscarded = { backStack.home() },
                                onPrinted = { backStack.print(it) },
                            )
                        } else {
                            Placeholder(
                                emoji = "✂️", title = R.string.placeholder_pick_title, body = R.string.placeholder_pick_edit_body,
                                milestone = "M4", backStack = backStack,
                                PlaceholderAction(R.string.placeholder_pick_edit_next) { backStack.removeLastOrNull() },
                            )
                        }
                    }
                    entry<Result> { key ->
                        ResultRoute(
                            sessionId = key.sessionId,
                            onAgain = { backStack.leaveResultFor(Setup) },
                            onAlbum = { backStack.leaveResultFor(Album) },
                            onHome = { backStack.home() },
                        )
                    }
                    entry<Album> {
                        Placeholder(
                            emoji = "📒", title = R.string.placeholder_album_title, body = R.string.placeholder_album_body,
                            milestone = "M5", backStack = backStack,
                            PlaceholderAction(R.string.placeholder_album_next) { backStack.go(Detail(SampleSessionId)) },
                        )
                    }
                    entry<Detail> { key ->
                        Placeholder(
                            emoji = "🖼️", title = R.string.placeholder_detail_title, body = R.string.placeholder_detail_body,
                            milestone = "M5", backStack = backStack,
                            PlaceholderAction(R.string.placeholder_detail_next) { backStack.go(Pick(editSessionId = key.sessionId)) },
                        )
                    }
                },
            )
        }
    }
}

/** Stand-in session id the Album placeholder opens until M5 lists real sessions. */
private const val SampleSessionId = "sample"

@Composable
private fun Placeholder(
    emoji: String,
    title: Int,
    body: Int,
    milestone: String,
    backStack: NavBackStack<NavKey>,
    vararg actions: PlaceholderAction,
) {
    PlaceholderScreen(
        emoji = emoji,
        title = title,
        body = body,
        milestone = milestone,
        onBack = { backStack.removeLastOrNull() },
        actions = actions.toList(),
    )
}

/** Push [key] unless it's already on top (a double tap shouldn't stack the same screen twice). */
private fun NavBackStack<NavKey>.go(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Everything but Home goes: ✕ → "Discard session?", or Result's ← (§3). */
private fun NavBackStack<NavKey>.home() {
    removeAll { it !is Home }
    if (isEmpty()) add(Home)
}

/** "Print ✿" (§8.3): drop the capture flow so back from Result goes Home, then show the result. */
private fun NavBackStack<NavKey>.print(sessionId: String) {
    removeAll { it is Setup || it is Capture || it is Pick }
    add(Result(sessionId))
}

/** Result is a dead end for back (§3), so leaving it replaces it instead of stacking on top. */
private fun NavBackStack<NavKey>.leaveResultFor(key: NavKey) {
    removeAll { it is Result }
    go(key)
}

// §7 A1: forward slides in 12% and fades with Gentle; pop is the exact reverse.

private fun slideDistance(fullWidth: Int) = (fullWidth * BoothMotion.NavSlideFraction).roundToInt()

private fun AnimatedContentTransitionScope<Scene<NavKey>>.forwardTransition(): ContentTransform =
    (slideInHorizontally(BoothMotion.gentle()) { slideDistance(it) } + fadeIn(BoothMotion.gentle()))
        .togetherWith(slideOutHorizontally(BoothMotion.gentle()) { -slideDistance(it) } + fadeOut(BoothMotion.gentle()))

private fun AnimatedContentTransitionScope<Scene<NavKey>>.popTransition(): ContentTransform =
    (slideInHorizontally(BoothMotion.gentle()) { -slideDistance(it) } + fadeIn(BoothMotion.gentle()))
        .togetherWith(slideOutHorizontally(BoothMotion.gentle()) { slideDistance(it) } + fadeOut(BoothMotion.gentle()))
