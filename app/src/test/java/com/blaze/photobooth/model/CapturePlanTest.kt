package com.blaze.photobooth.model

import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.render.LayoutGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** README §4.3 / §5.5.1: shot i is framed like slot i; bonus shots use the first slot. */
class CapturePlanTest {

    @Test
    fun themeLayoutShotsUseTheirCellsThenBonusUsesTheFirst() {
        val cells = LayoutGeometry.cells(BoothLayout.Classic)
        val plan = CapturePlan(cells, bonusShots = 2)
        assertEquals(4, plan.cuts)
        assertEquals(6, plan.totalShots)
        for (i in 0 until 4) assertEquals(cells[i].aspect, plan.aspectFor(i), 0f)
        assertEquals(cells[0].aspect, plan.aspectFor(4), 0f)
        assertEquals(cells[0].aspect, plan.aspectFor(5), 0f)
        assertFalse(plan.isBonus(3))
        assertTrue(plan.isBonus(4))
    }

    @Test
    fun lilacPalsHasThreeWindowsSoShotsFourAndFiveAreBonus() {
        val frames = FrameRepository.parseManifest(File("src/main/assets/frames/frames.json").readText())
        val lilac = frames.first { it.id == FrameId.asset("lilac-pals") }
        val plan = CapturePlan(lilac.slots, bonusShots = 2)
        assertEquals(3, plan.cuts)
        assertEquals(5, plan.totalShots)
        assertEquals(listOf(0, 1, 2, 0, 0), (0 until 5).map(plan::slotFor))
        assertEquals(lilac.slots[2].aspect, plan.aspectFor(2), 0f)
        assertEquals(lilac.slots[0].aspect, plan.aspectFor(4), 0f)
    }

    @Test
    fun differentlyShapedWindowsGetTheirOwnAspect() {
        val slots = listOf(SlotRect(0f, 0f, 400f, 300f), SlotRect(0f, 400f, 300f, 400f), SlotRect(0f, 900f, 300f, 300f))
        val plan = CapturePlan(slots, bonusShots = 0)
        assertEquals(listOf(4f / 3f, 0.75f, 1f), (0 until 3).map(plan::aspectFor))
    }

    @Test
    fun everyLayoutPlansCutsPlusBonus() {
        for (layout in BoothLayout.entries) for (bonus in BoothConfig.BonusChoices) {
            val plan = CapturePlan(LayoutGeometry.cells(layout), bonus)
            assertEquals(layout.cuts + bonus, plan.totalShots)
        }
    }
}
