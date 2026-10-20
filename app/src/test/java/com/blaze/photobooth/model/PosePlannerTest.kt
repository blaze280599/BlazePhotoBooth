package com.blaze.photobooth.model

import com.blaze.photobooth.R
import com.blaze.photobooth.model.Poses.who
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** README §5.6.1: one pose per shot, no repeats, a cute start, a love ending, varied moods, ~60% crowd poses. */
class PosePlannerTest {

    private fun mood(id: String) = checkNotNull(Poses.find(id)) { "unknown pose $id" }.mood

    /** Every rule of §5.6.1, strictly (no relaxing needed with the real catalogue). */
    private fun assertFollowsTheRules(plan: List<String>, crowd: Crowd, cuts: Int, total: Int, what: String) {
        assertEquals("$what: one pose per shot", total, plan.size)
        assertEquals("$what: no repeats in $plan", total, plan.toSet().size)
        val pool = Poses.poolFor(crowd).map { it.id }.toSet()
        assertTrue("$what: every pose suits the crowd: $plan", plan.all { it in pool })
        assertEquals("$what: shot 1 is a cute warm-up: $plan", Mood.Cute, mood(plan[0]))
        if (cuts >= 2) assertEquals("$what: the last cut is a love pose: $plan", Mood.Love, mood(plan[cuts - 1]))
        for (i in 1 until total) assertNotEquals("$what: shots $i and ${i + 1} share a mood: $plan", mood(plan[i - 1]), mood(plan[i]))
    }

    /** Built-in layouts (§5.1) and her frames (§5.5.6: Lilac Pals has 3 windows, the others 4). */
    private val cutCounts = (BoothLayout.entries.map { it.cuts } + listOf(3, 4)).distinct()

    @Test
    fun everyLayoutBonusAndCrowdFollowsTheRules() {
        for (crowd in Crowd.entries) for (cuts in cutCounts) for (bonus in BoothConfig.BonusChoices) for (seed in 0L until 150L) {
            val total = cuts + bonus
            assertFollowsTheRules(PosePlanner.plan(crowd, cuts, total, seed), crowd, cuts, total, "$crowd $cuts+$bonus seed $seed")
        }
    }

    @Test
    fun herLilacPalsWithThreeWindows() {
        for (crowd in Crowd.entries) for (seed in 0L until 500L) {
            val plan = PosePlanner.plan(crowd, cuts = 3, total = 5, seed = seed)
            assertFollowsTheRules(plan, crowd, 3, 5, "Lilac Pals $crowd seed $seed")
            // Shot 3 is her strip's last window: a heart to end on.
            assertEquals(Mood.Love, mood(plan[2]))
        }
    }

    @Test
    fun theTenShotMaximumSixGridPlusFourBonus() {
        assertEquals(10, BoothLayout.Six.cuts + BoothConfig.BonusChoices.max())
        for (crowd in Crowd.entries) for (seed in 0L until 1_000L) {
            assertFollowsTheRules(PosePlanner.plan(crowd, cuts = 6, total = 10, seed = seed), crowd, 6, 10, "Six Grid +4 $crowd seed $seed")
        }
    }

    @Test
    fun soloPolaroidWarmsUpRatherThanEndingOnAHeart() {
        // One cut is both the first shot and the last cut: the warm-up rule wins (§5.6.1).
        for (seed in 0L until 100L) assertEquals(Mood.Cute, mood(PosePlanner.plan(Crowd.Duo, cuts = 1, total = 5, seed = seed)[0]))
        assertEquals(Mood.Cute, PosePlanner.requiredMood(0, cuts = 1))
        assertEquals(null, PosePlanner.requiredMood(3, cuts = 1))
    }

    @Test
    fun theSameSeedGivesTheSamePlanAndSeedsVary() {
        val a = PosePlanner.plan(Crowd.Duo, cuts = 4, total = 6, seed = 1_760_000_000_000L)
        assertEquals(a, PosePlanner.plan(Crowd.Duo, cuts = 4, total = 6, seed = 1_760_000_000_000L))
        val plans = (0L until 100L).map { PosePlanner.plan(Crowd.Solo, cuts = 4, total = 6, seed = it) }.toSet()
        assertTrue("sessions should differ, got ${plans.size} different plans", plans.size > 90)
        // Every solo pose gets its turn somewhere.
        assertEquals(Poses.poolFor(Crowd.Solo).map { it.id }.toSet(), plans.flatten().toSet())
    }

    @Test
    fun justMeOnlyGetsSoloPoses() {
        for (seed in 0L until 200L) {
            assertTrue(PosePlanner.plan(Crowd.Solo, 6, 10, seed).all { Poses.find(it)?.who == Who.Solo })
        }
    }

    @Test
    fun twoOfUsAndSquadLeanAboutSixtyPercentTowardTheirOwnPoses() {
        for (crowd in listOf(Crowd.Duo, Crowd.Squad)) {
            val picks = (0L until 2_000L).flatMap { PosePlanner.plan(crowd, cuts = 4, total = 6, seed = it) }
            val own = picks.count { Poses.find(it)?.who == crowd.who }.toDouble() / picks.size
            assertTrue("$crowd: ${"%.2f".format(own)} of the poses are its own", own in 0.5..0.7)
            // …and the rest come from the solo list.
            assertTrue(picks.all { Poses.find(it)?.who in setOf(Who.Solo, crowd.who) })
        }
    }

    @Test
    fun theMoodRuleIsRelaxedBeforeAnyPoseRepeats() {
        // Only cute poses: "different moods" and "end on love" can't hold, but the poses never repeat.
        val cuteOnly = (1..5).map { Pose("cute-$it", "✿", Who.Solo, Mood.Cute, R.string.pose_smile_name, null) }
        for (seed in 0L until 50L) {
            val plan = PosePlanner.plan(Crowd.Solo, cuts = 4, total = 5, seed = seed, catalogue = cuteOnly)
            assertEquals(5, plan.toSet().size)
        }
        // Cute and love only, alternating is possible: it keeps the rules where it can.
        val twoMoods = cuteOnly + (1..5).map { Pose("love-$it", "♡", Who.Solo, Mood.Love, R.string.pose_smile_name, null) }
        val plan = PosePlanner.plan(Crowd.Solo, cuts = 4, total = 6, seed = 7, catalogue = twoMoods)
        val moods = plan.map { id -> twoMoods.first { it.id == id }.mood }
        assertEquals(listOf(Mood.Cute, Mood.Love, Mood.Cute, Mood.Love, Mood.Cute, Mood.Love), moods)
    }

    @Test
    fun swappingGivesAnUnusedPoseAndKeepsTheRules() {
        for (crowd in Crowd.entries) for (seed in 0L until 100L) {
            val plan = PosePlanner.plan(crowd, cuts = 4, total = 6, seed = seed)
            for (index in plan.indices) {
                val swapped = PosePlanner.swap(plan, index, crowd, cuts = 4, random = Random(seed))
                assertNotEquals(plan[index], swapped[index])
                assertTrue("a swap brings a pose that isn't in the plan yet", swapped[index] !in plan)
                assertEquals("only shot $index changes", plan.filterIndexed { i, _ -> i != index }, swapped.filterIndexed { i, _ -> i != index })
                assertEquals(6, swapped.toSet().size)
                // The rules hold whenever an unused pose could keep them (Just me has only 3 love poses: once the
                // plan uses all three, swapping the last cut relaxes "end on love" rather than repeat one).
                val strict = Poses.poolFor(crowd).any { p ->
                    p.id !in plan && PosePlanner.requiredMood(index, 4).let { it == null || it == p.mood } &&
                        p.mood != plan.getOrNull(index - 1)?.let(::mood) && p.mood != plan.getOrNull(index + 1)?.let(::mood)
                }
                if (strict) assertFollowsTheRules(swapped, crowd, 4, 6, "$crowd swap $index seed $seed")
            }
        }
    }

    @Test
    fun swappingAgainAndAgainNeverRepeatsInsideThePlan() {
        var plan = PosePlanner.plan(Crowd.Squad, cuts = 3, total = 5, seed = 3)
        val random = Random(9)
        repeat(40) {
            plan = PosePlanner.swap(plan, 1, Crowd.Squad, cuts = 3, random = random)
            assertEquals(5, plan.toSet().size)
        }
        assertEquals(plan, PosePlanner.swap(plan, 9, Crowd.Squad, cuts = 3, random = random))
    }

    @Test
    fun repairFillsInOldDraftsAndMendsUnknownPoses() {
        val fresh = PosePlanner.plan(Crowd.Duo, 4, 6, seed = 42)
        assertEquals(fresh, PosePlanner.repair(emptyList(), Crowd.Duo, 4, 6, seed = 42))
        assertEquals(fresh, PosePlanner.repair(fresh.take(3), Crowd.Duo, 4, 6, seed = 42))
        assertEquals(fresh, PosePlanner.repair(fresh, Crowd.Duo, 4, 6, seed = 1))
        val broken = fresh.toMutableList().also { it[2] = "moonwalk" }
        val mended = PosePlanner.repair(broken, Crowd.Duo, 4, 6, seed = 42)
        assertEquals(fresh.filterIndexed { i, _ -> i != 2 }, mended.filterIndexed { i, _ -> i != 2 })
        assertFollowsTheRules(mended, Crowd.Duo, 4, 6, "mended")
        val repeated = fresh.toMutableList().also { it[4] = it[1] }
        assertEquals(6, PosePlanner.repair(repeated, Crowd.Duo, 4, 6, seed = 42).toSet().size)
    }
}
