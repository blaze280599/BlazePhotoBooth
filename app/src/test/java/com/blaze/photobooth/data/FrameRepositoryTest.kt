package com.blaze.photobooth.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.frames.CustomFrame
import com.blaze.photobooth.data.frames.CustomFrameSource
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.data.frames.FrameVisibility
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.ImageFrame
import com.blaze.photobooth.model.SlotRect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * README §4.2, §4.8, §5.5.3: the carousel is her bundled frames, then her imported ones, then the themes; hidden
 * frames leave it but can still be found (for strips and drafts that use them); Manage frames lists both kinds.
 */
@RunWith(AndroidJUnit4::class)
class FrameRepositoryTest {

    private fun imported(uuid: String, name: String): ImageFrame = ImageFrame(
        id = FrameId.custom(uuid),
        name = name,
        width = 1200,
        height = 3600,
        slots = listOf(SlotRect(72f, 72f, 1056f, 760f)),
        textArea = null,
        artwork = ArtworkLocation.LocalFile(File("frame.png")),
        thumb = ArtworkLocation.LocalFile(File("thumb.png")),
    )

    private val shown: ImageFrame = imported("a", "Frame 1")
    private val hidden: ImageFrame = imported("b", "Frame 2")
    private val deleted: ImageFrame = imported("c", "Frame 3")

    private val custom: MutableStateFlow<List<CustomFrame>> = MutableStateFlow(
        listOf(
            CustomFrame(shown, FrameVisibility.Shown),
            CustomFrame(hidden, FrameVisibility.Hidden),
            CustomFrame(deleted, FrameVisibility.Deleted),
        ),
    )
    private val hiddenBundled: MutableStateFlow<Set<FrameId>> = MutableStateFlow(setOf(FrameId.asset("usagi-gold")))

    private val repo = FrameRepository(
        RuntimeEnvironment.getApplication(),
        object : CustomFrameSource {
            override val frames: Flow<List<CustomFrame>> = custom
        },
        hiddenBundled,
    )

    private val bundledIds = listOf(
        "lilac-pals", "usagi-gold", "ikmyeong-night", "blossom-picnic", "rribbitt-sky", "molang-pineapple", "molang-pool",
    ).map(FrameId::asset)

    @Test
    fun theCarouselSkipsHiddenAndDeletedFrames() = runTest {
        val ids = repo.options.first().map { it.id }
        assertEquals(bundledIds - FrameId.asset("usagi-gold") + shown.id + FrameTheme.entries.map(FrameId::theme), ids)
    }

    @Test
    fun hiddenFramesCanStillBeFound() = runTest {
        val all = repo.allOptions.first().map { it.id }
        assertEquals(bundledIds + listOf(shown.id, hidden.id, deleted.id) + FrameTheme.entries.map(FrameId::theme), all)
        assertEquals(deleted, (repo.find(deleted.id) as FrameOption.Artwork).frame)
        assertNotNull(repo.find(FrameId.asset("usagi-gold")))
        assertNull(repo.find(FrameId.custom("gone")))
    }

    @Test
    fun manageFramesListsBundledThenImportedButNotDeleted() = runTest {
        val rows = repo.managed.first()
        assertEquals(bundledIds + listOf(shown.id, hidden.id), rows.map { it.frame.id })
        assertEquals(listOf(false, true, false, false, false, false, false, false, true), rows.map { it.hidden })
        assertEquals(List(bundledIds.size) { true } + listOf(false, false), rows.map { it.bundled })
    }

    @Test
    fun showingAFrameAgainPutsItBackInItsPlace() = runTest {
        hiddenBundled.value = emptySet()
        custom.value = custom.value.map { if (it.frame == hidden) it.copy(visibility = FrameVisibility.Shown) else it }
        val ids = repo.options.first().map { it.id }
        assertEquals(bundledIds + listOf(shown.id, hidden.id), ids.take(bundledIds.size + 2))
    }
}
