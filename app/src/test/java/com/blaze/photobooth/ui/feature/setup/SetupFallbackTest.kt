package com.blaze.photobooth.ui.feature.setup

import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.ArtworkLocation
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.ImageFrame
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.SlotRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * README §4.2: Setup falls back gracefully when the frame it remembers is hidden or deleted, and picks the frame she
 * has just imported.
 */
class SetupFallbackTest {

    private fun frame(id: FrameId) = FrameOption.Artwork(
        ImageFrame(id, id.localId, 1200, 3600, listOf(SlotRect(0f, 0f, 10f, 10f)), null, ArtworkLocation.Asset("x"), ArtworkLocation.Asset("x")),
    )

    private val lilac = frame(FrameId.asset("lilac-pals"))
    private val usagi = frame(FrameId.asset("usagi-gold"))
    private val mine = frame(FrameId.custom("1234"))
    private val themes = FrameTheme.entries.map { FrameOption.Theme(it) }

    @Test
    fun aSavedFrameThatStillShowsIsKept() {
        val saved = BoothConfig(frameId = mine.id, filter = PhotoFilter.Mono, timerSec = 3)
        assertEquals(saved, SetupViewModel.resolve(saved, listOf(lilac, mine) + themes, mirror = true))
    }

    @Test
    fun aHiddenOrDeletedFrameFallsBackToTheFirstOneShowing() {
        val saved = BoothConfig(frameId = mine.id, filter = PhotoFilter.Mono, timerSec = 3)
        // She hid (or deleted) it: the first frame in the carousel takes its place; every other choice stays.
        val resolved = SetupViewModel.resolve(saved, listOf(lilac, usagi) + themes, mirror = true)
        assertEquals(saved.copy(frameId = lilac.id), resolved)
        // Her bundled frames all hidden too: the first theme.
        assertEquals(FrameId.theme(FrameTheme.entries.first()), SetupViewModel.resolve(saved, themes, mirror = true).frameId)
    }

    @Test
    fun firstLaunchStartsOnTheFirstFrame() {
        val resolved = SetupViewModel.resolve(null, listOf(usagi, lilac) + themes, mirror = false)
        assertEquals(usagi.id, resolved.frameId)
        assertEquals(false, resolved.mirror)
    }

    @Test
    fun theFrameSheJustImportedIsSelected() {
        val before = (listOf(lilac, usagi) + themes).map { it.id }.toSet()
        assertEquals(mine.id, SetupViewModel.newlyImported(before, listOf(lilac, usagi, mine) + themes))
        // Nothing new (or only a bundled frame shown again): no change.
        assertNull(SetupViewModel.newlyImported(before, listOf(lilac, usagi) + themes))
        assertNull(SetupViewModel.newlyImported(before - usagi.id, listOf(lilac, usagi) + themes))
    }
}
