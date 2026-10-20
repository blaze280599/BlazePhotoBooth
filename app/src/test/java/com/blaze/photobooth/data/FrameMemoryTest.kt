package com.blaze.photobooth.data

import android.content.ComponentCallbacks2
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blaze.photobooth.data.frames.CustomFrameSource
import com.blaze.photobooth.data.frames.FrameRepository
import com.blaze.photobooth.render.BitmapOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * M9, low memory: the cached frame artwork (her five frames at preview size and their swatches) is given back when
 * Android asks: all of it once the app is out of sight, half while it's on screen. It is decoded again when needed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameMemoryTest {

    private val repo = FrameRepository(
        RuntimeEnvironment.getApplication(),
        object : CustomFrameSource {
            override val frames = MutableStateFlow(emptyList<com.blaze.photobooth.data.frames.CustomFrame>())
        },
        MutableStateFlow(emptySet()),
    )

    private suspend fun fill() {
        for (frame in repo.bundledFrames()) {
            repo.thumbnail(frame)
            repo.artwork(frame, maxWidth = BitmapOps.PreviewMaxPx)
        }
    }

    @Test
    fun outOfSightTheCacheIsEmptied() = runTest {
        fill()
        assertTrue(repo.cachedKb() > 0)
        repo.trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(0, repo.cachedKb())
        // Still works afterwards: decoded again.
        val frame = repo.bundledFrames().first()
        assertEquals(BitmapOps.PreviewMaxPx, repo.artwork(frame, maxWidth = BitmapOps.PreviewMaxPx).width)
    }

    @Test
    fun onScreenItKeepsAtMostHalfTheBudget() = runTest {
        fill()
        @Suppress("DEPRECATION") // still delivered on older Android versions
        repo.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        // The budget is 48 MB; what's on screen right now (the newest entries) stays.
        assertTrue(repo.cachedKb() in 1..24 * 1024)
    }
}
