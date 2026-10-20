package com.blaze.photobooth.data

import com.blaze.photobooth.data.files.BoothDraft
import com.blaze.photobooth.data.files.BoothDraftStore
import com.blaze.photobooth.data.files.DraftConfig
import com.blaze.photobooth.data.files.DraftShot
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.Crowd
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.Lens
import com.blaze.photobooth.model.Mood
import com.blaze.photobooth.model.PosePlanner
import com.blaze.photobooth.model.Poses
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/** README §5.6.1 / §11.4: the pose plan lives in `draft.json`, survives a restart, and old drafts get one. */
class PosePlanDraftTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val lilac = BoothConfig(frameId = FrameId.asset("lilac-pals"), bonusShots = 2, crowd = Crowd.Duo)

    private fun store(scope: TestScope) = BoothDraftStore(tmp.root, io = StandardTestDispatcher(scope.testScheduler), clock = { Now })

    private fun BoothDraftStore.fakeShot(index: Int) = shotFile(index).apply { parentFile?.mkdirs(); writeText("jpeg $index") }

    private companion object {
        const val Now = 1_760_000_000_000L

        /** A `draft.json` exactly as v1.0.0 wrote it: no crowd, no pose plan. */
        const val V1Draft = """{"version":1,"createdAt":1760950000000,"config":{"layout":"classic","frame":"asset:lilac-pals",""" +
            """"filter":"original","timerSec":5,"bonusShots":2,"lens":"front","mirror":true,"backFlash":"off"},"totalShots":5,""" +
            """"shots":[{"index":0,"width":2400,"height":1940,"takenAt":1760950010000}],"selection":[],"caption":null,"showDate":true}"""
    }

    @Test
    fun beginPlansAPosePerShotAndItSurvivesARestart() = runTest {
        val d = store(this).begin(lilac, totalShots = 5)
        assertEquals(PosePlanner.plan(Crowd.Duo, cuts = 3, total = 5, seed = Now), d.posePlan)
        assertEquals(3, d.cuts)
        assertEquals(Mood.Cute, Poses.find(d.poseFor(0))?.mood)
        assertEquals(Mood.Love, Poses.find(d.poseFor(2))?.mood)
        assertEquals(d.posePlan, store(this).current()?.posePlan)
        assertEquals(Crowd.Duo, store(this).current()?.config?.toConfig()?.crowd)
    }

    @Test
    fun theJsonRoundTripsThePlanAndTheCrowd() {
        val draft = BoothDraft(
            createdAt = 42L,
            config = DraftConfig.from(lilac),
            totalShots = 3,
            posePlan = listOf("cheek-heart", "cheek-pinch", "back-hug"),
        )
        val text = BoothDraftStore.encode(draft)
        assertTrue(text.contains("\"posePlan\":[\"cheek-heart\",\"cheek-pinch\",\"back-hug\"]"))
        assertTrue(text.contains("\"crowd\":\"duo\""))
        assertEquals(draft, BoothDraftStore.decode(text))
    }

    @Test
    fun aV1DraftGetsAPlanOnLoadTheSameOneEveryTime() = runTest {
        File(tmp.root, "_draft").mkdirs()
        File(tmp.root, "_draft/draft.json").writeText(V1Draft)
        File(tmp.root, "_draft/shot_0.jpg").writeText("jpeg 0")
        val first = store(this).current()
        checkNotNull(first)
        assertEquals(1, first.taken)
        assertEquals(Crowd.Solo, first.config.toConfig().crowd)
        // Lilac Pals: 3 windows + 2 bonus.
        assertEquals(PosePlanner.plan(Crowd.Solo, cuts = 3, total = 5, seed = 1_760_950_000_000L), first.posePlan)
        // Nothing was written yet, but the next load (another process) plans exactly the same poses.
        assertEquals(first.posePlan, store(this).current()?.posePlan)
    }

    @Test
    fun aSwapIsSavedAtOnceAndOnlyChangesThatShot() = runTest {
        val s = store(this)
        val before = s.begin(lilac, totalShots = 5).posePlan
        val after = checkNotNull(s.swapPose(1, Random(5))).posePlan
        assertNotEquals(before[1], after[1])
        assertEquals(before.filterIndexed { i, _ -> i != 1 }, after.filterIndexed { i, _ -> i != 1 })
        assertEquals(after, store(this).current()?.posePlan)
        assertEquals(5, after.toSet().size)
    }

    @Test
    fun retakesPickChoicesAndConfigChangesKeepThePlan() = runTest {
        val s = store(this)
        val plan = s.begin(lilac, totalShots = 5).posePlan
        s.fakeShot(2)
        s.addShot(DraftShot(2, 10, 10))
        s.addShot(DraftShot(2, 20, 20)) // Pick's "retake one" (§4.4)
        s.updatePick(listOf(2, null, null), caption = null, showDate = true)
        s.updateConfig(lilac.copy(lens = Lens.Back, layout = BoothLayout.Six))
        val reopened = checkNotNull(store(this).current())
        assertEquals(plan, reopened.posePlan)
        // The retake of shot 3 shows the pose planned for it.
        assertEquals(plan[2], reopened.poseFor(2))
        assertEquals(Crowd.Duo, reopened.config.toConfig().crowd)
    }

    @Test
    fun anUnknownPoseFromAnotherVersionIsMended() = runTest {
        val s = store(this)
        val plan = s.begin(lilac, totalShots = 5).posePlan
        val file = File(s.dir, BoothDraftStore.DraftFileName)
        file.writeText(file.readText().replace("\"${plan[3]}\"", "\"moonwalk\""))
        val mended = checkNotNull(store(this).current()).posePlan
        assertEquals(plan.filterIndexed { i, _ -> i != 3 }, mended.filterIndexed { i, _ -> i != 3 })
        assertTrue(Poses.find(mended[3]) != null)
    }
}
