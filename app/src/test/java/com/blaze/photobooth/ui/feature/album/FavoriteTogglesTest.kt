package com.blaze.photobooth.ui.feature.album

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** README §13: rapid ♡ taps show at once, land in order, and never flicker back to a stale stored value. */
@RunWith(AndroidJUnit4::class) // android.util.Log
class FavoriteTogglesTest {

    @Test
    fun rapidTapsEndOnTheLastTapAndTheStoreAgrees() = runTest {
        val lock = Mutex()
        val writes = mutableListOf<Boolean>()
        var stored = false
        val toggles = FavoriteToggles(backgroundScope) { _, value ->
            lock.withLock {
                delay(100)
                writes += value
                stored = value
            }
        }

        assertTrue(toggles.toggle("a", stored))
        assertFalse(toggles.toggle("a", stored))
        assertTrue(toggles.toggle("a", stored))
        assertEquals(true, toggles.overrides.value["a"])

        // The database reports the first write (true), then the second (false): the heart keeps showing the last tap.
        delay(150)
        toggles.onStored(mapOf("a" to true))
        assertTrue(toggles.resolve("a", storedValue = true))
        delay(100)
        toggles.onStored(mapOf("a" to false))
        assertTrue("a stale false must not show", toggles.resolve("a", storedValue = false))

        advanceTimeBy(10_000)
        runCurrent()
        toggles.onStored(mapOf("a" to stored))
        assertEquals(listOf(true, false, true), writes)
        assertTrue(stored)
        assertEquals("settled: no override left", emptyMap<String, Boolean>(), toggles.overrides.value)
    }

    @Test
    fun aFailedWriteFallsBackToTheStoredValue() = runTest {
        val toggles = FavoriteToggles(backgroundScope) { _, _ -> error("disk full") }
        toggles.toggle("a", storedValue = false)
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(toggles.resolve("a", storedValue = false))
        assertTrue(toggles.overrides.value.isEmpty())
    }
}
