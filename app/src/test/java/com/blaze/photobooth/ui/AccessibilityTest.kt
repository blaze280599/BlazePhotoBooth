package com.blaze.photobooth.ui

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.export.StorageUsage
import com.blaze.photobooth.screenshots.AlbumSamples
import com.blaze.photobooth.screenshots.CaptureSamples
import com.blaze.photobooth.screenshots.FakeImages
import com.blaze.photobooth.screenshots.FrameSamples
import com.blaze.photobooth.screenshots.PickSamples
import com.blaze.photobooth.screenshots.Pixel9Qualifiers
import com.blaze.photobooth.screenshots.ResultSamples
import com.blaze.photobooth.screenshots.SetupSamples
import com.blaze.photobooth.ui.feature.album.AlbumScreen
import com.blaze.photobooth.ui.feature.capture.CameraPermission
import com.blaze.photobooth.ui.feature.capture.CaptureScreen
import com.blaze.photobooth.ui.feature.capture.SequencePhase
import com.blaze.photobooth.ui.feature.detail.DetailScreen
import com.blaze.photobooth.ui.feature.detail.DetailTab
import com.blaze.photobooth.ui.feature.frames.AddFrameScreen
import com.blaze.photobooth.ui.feature.frames.ManageFramesScreen
import com.blaze.photobooth.ui.feature.home.HomeScreen
import com.blaze.photobooth.ui.feature.home.SampleFilledHomeState
import com.blaze.photobooth.ui.feature.pick.PickEvent
import com.blaze.photobooth.ui.feature.pick.PickScreen
import com.blaze.photobooth.ui.feature.pick.PickTab
import com.blaze.photobooth.ui.feature.result.ResultScreen
import com.blaze.photobooth.ui.feature.settings.SettingsSheetContent
import com.blaze.photobooth.ui.feature.settings.SettingsUiState
import com.blaze.photobooth.ui.feature.setup.SetupScreen
import com.blaze.photobooth.ui.feature.setup.SetupTab
import com.blaze.photobooth.ui.theme.BoothTheme
import com.blaze.photobooth.ui.theme.SheetShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * README §6.7, TalkBack: every button, chip, card and switch says what it is, touch targets are at least 48 dp,
 * decorations stay silent, and the key moments (countdown, slots filling, selections) are announced. These read the
 * semantics tree the way TalkBack does; the screens are the real ones with sample states.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Pixel9Qualifiers)
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun show(dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { BoothTheme(darkTheme = dark, reduceMotion = true) { FakeImages(content) } }
        compose.waitForIdle()
    }

    // ---- Every screen ----

    @Test
    fun home() {
        show { HomeScreen(SampleFilledHomeState, onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("Settings").assert(hasClickAction())
        compose.onNodeWithContentDescription("Blaze Booth").assert(isHeading)
        // The blob's camera is decoration: the wordmark already names the app.
        assertTrue(compose.onAllWithDescription("Blaze Booth camera").isEmpty())
        compose.onNodeWithContentDescription("Your latest strip").assert(hasClickLabel("Open"))
        compose.onNodeWithContentDescription("12 strips in your album").assertExists()
    }

    @Test
    fun setupLayout() {
        show { SetupScreen(SetupSamples.herFrame(context), onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("Back").assert(hasClickAction())
        compose.onNodeWithText("Make your strip").assert(isHeading)
    }

    @Test
    fun setupFramesAreNamedRadioCards() {
        show { SetupScreen(SetupSamples.herFrame(context), onEvent = {}, initialTab = SetupTab.Frame) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("Lilac Pals, one of her frames")
            .assertIsSelected()
            .assert(hasRole(Role.RadioButton))
        compose.onNodeWithContentDescription("Usagi, one of her frames").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        compose.onNodeWithText("Her frames ♡").assert(isHeading)
    }

    @Test
    fun setupOptionsSayWhatTheyMean() {
        show { SetupScreen(SetupSamples.theme(), onEvent = {}, initialTab = SetupTab.Options) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("5 seconds").assertIsSelected()
        compose.onNodeWithContentDescription("2 bonus shots").assertIsSelected()
        // The whole row is the switch, so its name is read with it.
        compose.onNode(isToggleable() and hasText("Mirror selfies", substring = true)).assertIsOn()
    }

    @Test
    fun captureCountdownIsAnnouncedPolitely() {
        show {
            CaptureScreen(
                state = CaptureSamples.theme(SequencePhase.Countdown(3), taken = 1),
                permission = CameraPermission.Granted,
                onEvent = {},
                viewfinder = CaptureSamples.fakeViewfinder,
            )
        }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("3").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithContentDescription("Shot 2 of 6").assertExists()
        compose.onNodeWithContentDescription("Pause").assert(hasRole(Role.Button))
        compose.onNodeWithContentDescription("Leave the booth").assert(hasClickAction())
        compose.onNodeWithContentDescription("Switch camera").assertExists()
    }

    @Test
    fun capturePermissionCard() {
        show(dark = false) {
            CaptureScreen(
                state = CaptureSamples.theme(SequencePhase.Idle),
                permission = CameraPermission.Blocked,
                onEvent = {},
                viewfinder = CaptureSamples.fakeViewfinder,
            )
        }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithText("Open settings").assert(hasClickAction())
    }

    @Test
    fun pickSlotsAndShotsSayWhereTheyAre() {
        val events = mutableListOf<PickEvent>()
        show { PickScreen(PickSamples.lilac(listOf(0, 3, null)), onEvent = { events += it }) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        // "2 of 3 slots filled", announced as it changes.
        compose.onNodeWithContentDescription("2 of 3 slots filled")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithContentDescription("Slot 1 of 3, filled").assert(hasClickLabel("Take it out"))
        compose.onNodeWithContentDescription("Slot 3 of 3, empty").assertExists()
        // A placed shot and an unused bonus shot.
        compose.onNodeWithContentDescription("Shot 1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "In slot 1"))
            .assert(hasClickLabel("Take it out"))
        val bonus = compose.onNodeWithContentDescription("Shot 5, bonus")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not in the strip"))
            .assert(hasClickLabel("Put it in the strip"))
            .assert(SemanticsMatcher("long-press peeks") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Peek" })
        // TalkBack's double-tap places the shot (it used to open the peek instead); double-tap-and-hold peeks.
        bonus.performClick()
        bonus.performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf(PickEvent.TapShot(4), PickEvent.Peek(4)), events)
    }

    @Test
    fun pickTextTabSwitchIsNamed() {
        show(dark = false) { PickScreen(PickSamples.theme(listOf(0, 1, 2, 3)), onEvent = {}, initialTab = PickTab.Text) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNode(isToggleable() and hasText("Date stamp", substring = true)).assertIsOn()
    }

    @Test
    fun result() {
        show { ResultScreen(ResultSamples.state(), onEvent = {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("Your printed strip").assertExists()
        compose.onNodeWithContentDescription("Home").assert(hasClickAction())
        compose.onNodeWithText("Save strip").assert(hasClickAction())
    }

    @Test
    fun albumCardsAreOneStopEach() {
        show { AlbumScreen(AlbumSamples.album(), onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        val cards = compose.onAllWithDescriptionPrefix("Strip from ")
        assertTrue("cards: ${cards.size}", cards.isNotEmpty())
        // Each card's ♡ is its own switch.
        assertTrue(compose.onAllWithDescription("Favourite").all { it.config.getOrNull(SemanticsProperties.ToggleableState) != null })
    }

    @Test
    fun albumSelectionSaysSelected() {
        show { AlbumScreen(AlbumSamples.album(selection = setOf("s1")), onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        val selected = compose.onAllWithDescriptionPrefix("Strip from ").filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals(1, selected.size)
        compose.onNodeWithContentDescription("Stop selecting").assert(hasClickAction())
    }

    @Test
    fun detail() {
        show { DetailScreen(AlbumSamples.detail(), onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("Your strip").assertExists()
    }

    @Test
    fun detailPhotos() {
        show { DetailScreen(AlbumSamples.detail(), onEvent = {}, initialTab = DetailTab.Photos) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithText("Cut 1 of 3").assertExists()
    }

    @Test
    fun settings() {
        show(dark = false) {
            Surface(Modifier.wrapContentSize(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = SheetShape) {
                SettingsSheetContent(SettingsUiState(storage = StorageUsage(strips = 23, bytes = 48_200_000)), onEvent = {})
            }
        }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNode(isToggleable() and hasText("Haptics", substring = true)).assertIsOn()
        compose.onNodeWithText("Manage frames", substring = true).assert(hasClickLabel("Open Manage frames"))
    }

    @Test
    fun manageFrames() {
        show { ManageFramesScreen(FrameSamples.manage(context), onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("Hide Lilac Pals").performScrollTo().assert(hasClickAction())
    }

    @Test
    fun addFrame() {
        show(dark = false) { AddFrameScreen(FrameSamples.confirm(), onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
        compose.onNodeWithContentDescription("Cancel adding a frame").assert(hasClickAction())
    }

    @Test
    fun slotEditor() {
        show { AddFrameScreen(FrameSamples.editor(), onEvent = {}) }
        compose.checkEveryActionIsLabelledAndBigEnough()
    }

    // ---- Helpers ----

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun ComposeContentTestRule.onAllWithDescription(text: String): List<SemanticsNode> =
        onAllNodes(hasContentDescription(text)).fetchSemanticsNodes()

    private fun ComposeContentTestRule.onAllWithDescriptionPrefix(prefix: String): List<SemanticsNode> =
        onAllNodes(SemanticsMatcher("description starts with $prefix") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(prefix) }
        }).fetchSemanticsNodes()
}

/**
 * Every node TalkBack can act on (click, toggle, select) has a name (its own words or a description), and its touch
 * area (the layout node, which includes Material's minimum interactive size) is at least 48 × 48 dp (§1, §6.7).
 */
internal fun ComposeContentTestRule.checkEveryActionIsLabelledAndBigEnough() {
    val actionable = onAllNodes(SemanticsMatcher("actionable") { node ->
        SemanticsActions.OnClick in node.config || node.config.getOrNull(SemanticsProperties.ToggleableState) != null
    }).fetchSemanticsNodes()
    assertTrue("nothing to check", actionable.isNotEmpty())
    val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
    val problems = actionable.mapNotNull { node ->
        val c = node.config
        val words = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(c.getOrNull(SemanticsProperties.EditableText)?.text)
        val named = words.any { it.isNotBlank() }
        val w = node.layoutInfo.width / density
        val h = node.layoutInfo.height / density
        val visible = node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
        when {
            !visible -> null
            !named -> "unnamed ${c.getOrNull(SemanticsProperties.Role)} at ${node.boundsInRoot}"
            // Allow half a dp of rounding.
            w < 47.5f || h < 47.5f -> "\"${words.first()}\" is ${"%.1f".format(w)} × ${"%.1f".format(h)} dp"
            else -> null
        }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
}
