package com.blaze.photobooth.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/** README §5.6.2: the catalogue is complete, its strings exist, and every pool can fill the longest session. */
@RunWith(AndroidJUnit4::class)
class PosesTest {

    private val res = RuntimeEnvironment.getApplication().resources

    @Test
    fun fiftyOnePosesInThreeLists() {
        assertEquals(51, Poses.all.size)
        assertEquals(25, Poses.all.count { it.who == Who.Solo })
        assertEquals(14, Poses.all.count { it.who == Who.Duo })
        assertEquals(12, Poses.all.count { it.who == Who.Squad })
    }

    @Test
    fun idsAreUniqueAndStable() {
        assertEquals(Poses.all.size, Poses.all.map { it.id }.toSet().size)
        assertTrue(Poses.all.all { it.id.matches(Regex("[a-z]+(-[a-z]+)*")) })
        Poses.all.forEach { assertEquals(it, Poses.find(it.id)) }
        assertEquals(null, Poses.find("moonwalk"))
        assertEquals(null, Poses.find(null))
    }

    @Test
    fun everyPoseHasItsStringsUnderItsOwnName() {
        for (pose in Poses.all) {
            val key = pose.id.replace('-', '_')
            assertEquals("pose_${key}_name", res.getResourceEntryName(pose.name))
            assertTrue(pose.id, res.getString(pose.name).isNotBlank())
            assertTrue(pose.id, pose.emoji.isNotBlank())
            pose.korean?.let { ko ->
                assertEquals("pose_${key}_ko", res.getResourceEntryName(ko))
                assertTrue("${pose.id}'s Korean name is Hangul", res.getString(ko).any { it in '가'..'힣' })
            }
        }
        // The README's table has 13 Korean names (볼하트, 손하트, …).
        assertEquals(13, Poses.all.count { it.korean != null })
        assertEquals("Pinch each other's cheeks", res.getString(R.string.pose_cheek_pinch_name))
        assertEquals("볼하트", res.getString(R.string.pose_cheek_heart_ko))
    }

    @Test
    fun noPoseStringIsLeftWithoutAPose() {
        val used = Poses.all.flatMap { listOfNotNull(it.name, it.korean) }.toSet()
        val poseStrings = R.string::class.java.fields.filter { it.name.matches(Regex("pose_.+_(name|ko)")) }.map { it.getInt(null) }
        assertEquals(used, poseStrings.toSet())
    }

    @Test
    fun everyPoolCanFillTheLongestSession() {
        val longest = BoothLayout.Six.cuts + BoothConfig.BonusChoices.max()
        for (crowd in Crowd.entries) {
            val pool = Poses.poolFor(crowd)
            assertTrue("$crowd has ${pool.size} poses", pool.size >= longest)
            // Every mood is there, so the warm-up, the heart and the variety rule can all be met.
            assertEquals(Mood.entries.toSet(), pool.map { it.mood }.toSet())
            assertTrue(pool.count { it.mood == Mood.Cute } >= 1 && pool.count { it.mood == Mood.Love } >= 1)
        }
        assertEquals(25, Poses.poolFor(Crowd.Solo).size)
        assertEquals(39, Poses.poolFor(Crowd.Duo).size)
        assertEquals(37, Poses.poolFor(Crowd.Squad).size)
    }
}
