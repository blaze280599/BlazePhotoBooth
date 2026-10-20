package com.blaze.photobooth.screenshots

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.ui.feature.album.AlbumFilter
import com.blaze.photobooth.ui.feature.album.AlbumGrouping
import com.blaze.photobooth.ui.feature.album.AlbumItem
import com.blaze.photobooth.ui.feature.album.AlbumScreen
import com.blaze.photobooth.ui.feature.album.AlbumUiState
import com.blaze.photobooth.ui.feature.detail.DetailFrame
import com.blaze.photobooth.ui.feature.detail.DetailPhoto
import com.blaze.photobooth.ui.feature.detail.DetailScreen
import com.blaze.photobooth.ui.feature.detail.DetailTab
import com.blaze.photobooth.ui.feature.detail.DetailUiState
import com.blaze.photobooth.ui.feature.home.HomeScreen
import com.blaze.photobooth.ui.feature.home.HomeUiState
import com.blaze.photobooth.ui.feature.home.RecentStrip
import com.blaze.photobooth.ui.feature.pick.PickScreen
import com.blaze.photobooth.ui.theme.BoothTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Album, Detail, Edit and Home's peek (README §4.1, §4.6, §4.7) with generated pastel strips (no people). The
 * pictures normally load through Coil from files; here Coil's preview handler hands over generated bitmaps by
 * path. One class per state (graphicsLayer gotcha, §15.4).
 */
internal object AlbumSamples {
    private val context get() = RuntimeEnvironment.getApplication()

    /** Pictures by file path, for [FakeImages]. */
    val images = HashMap<String, Bitmap>()

    private fun her(id: String) = TestArt.herFrames(context).first { it.id == FrameId.asset(id) }

    private fun strip(spec: StripSpec, scale: Float = 0.3f): Bitmap =
        TestArt.render(context, spec, TestArt.photos(spec.cuts), scale)

    private fun herStrip(id: String, filter: PhotoFilter): Bitmap {
        val frame = her(id)
        return strip(StripSpec(FrameSource.Image(frame, TestArt.load(context, frame.artwork, maxWidth = 400)), filter = filter))
    }

    private fun themeStrip(theme: FrameTheme, layout: BoothLayout, filter: PhotoFilter, caption: String? = null) =
        strip(StripSpec(FrameSource.Theme(theme), layout, filter, caption = caption, date = LocalDate.of(2026, 10, 20)))

    private fun item(id: String, date: LocalDate, layoutId: String, favorite: Boolean, bitmap: Bitmap): AlbumItem {
        val file = File("/album/$id/thumb.jpg")
        images[file.path] = bitmap
        return AlbumItem(
            id = id,
            createdAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            date = date,
            layoutId = layoutId,
            favorite = favorite,
            image = file,
            version = 1,
            aspect = bitmap.width.toFloat() / bitmap.height,
        )
    }

    val items: List<AlbumItem> by lazy {
        listOf(
            item("s1", LocalDate.of(2026, 10, 20), SessionEntity.ImageLayoutId, true, herStrip("lilac-pals", PhotoFilter.Milky)),
            item("s2", LocalDate.of(2026, 10, 18), "grid", false, themeStrip(FrameTheme.Cloud, BoothLayout.Grid, PhotoFilter.Cool, "best day")),
            item("s3", LocalDate.of(2026, 10, 12), "classic", true, themeStrip(FrameTheme.Strawberry, BoothLayout.Classic, PhotoFilter.Peach)),
            item("s4", LocalDate.of(2026, 10, 3), "solo", false, themeStrip(FrameTheme.Butter, BoothLayout.Solo, PhotoFilter.Original)),
            item("s5", LocalDate.of(2026, 9, 27), SessionEntity.ImageLayoutId, false, herStrip("usagi-gold", PhotoFilter.Original)),
            item("s6", LocalDate.of(2026, 9, 14), "trio", true, themeStrip(FrameTheme.Matcha, BoothLayout.Trio, PhotoFilter.Film)),
            item("s7", LocalDate.of(2026, 9, 2), "six", false, themeStrip(FrameTheme.Lavender, BoothLayout.Six, PhotoFilter.Mono)),
        )
    }

    fun album(filter: AlbumFilter = AlbumFilter.All, selection: Set<String> = emptySet()) = AlbumUiState(
        loaded = true,
        total = items.size,
        entries = AlbumGrouping.group(AlbumGrouping.filter(items, filter)),
        filters = AlbumGrouping.filters(items),
        filter = filter,
        selection = selection,
    )

    /** Her Lilac Pals session: 3 cuts in the strip and 2 unused bonus shots. */
    fun detail(): DetailUiState {
        val frame = her("lilac-pals")
        val photos = TestArt.photos(5).map { it }
        val strip = TestArt.render(
            context,
            StripSpec(FrameSource.Image(frame, TestArt.load(context, frame.artwork, maxWidth = 600)), filter = PhotoFilter.Peach),
            photos.take(3),
            scale = 0.5f,
        )
        val stripFile = File("/album/d1/strip.jpg")
        images[stripFile.path] = strip
        val shots = photos.mapIndexed { i, bitmap ->
            val file = File("/album/d1/shot_$i.jpg")
            images[file.path] = bitmap
            DetailPhoto(shotIndex = i, file = file, aspect = bitmap.width.toFloat() / bitmap.height, version = 1)
        }
        return DetailUiState(
            loaded = true,
            sessionId = "d1",
            date = LocalDate.of(2026, 10, 20),
            frame = DetailFrame.Named(frame.name),
            filter = PhotoFilter.Peach,
            favorite = true,
            strip = stripFile,
            stripVersion = 1,
            thumb = stripFile,
            thumbVersion = 1,
            aspect = strip.width.toFloat() / strip.height,
            cuts = shots.take(3),
            unused = shots.drop(3),
        )
    }

    fun home(): HomeUiState {
        val file = File("/album/s3/thumb.jpg")
        images[file.path] = themeStrip(FrameTheme.Strawberry, BoothLayout.Classic, PhotoFilter.Peach)
        return HomeUiState(albumCount = 26, recentStrip = RecentStrip("s3", file, version = 1))
    }
}

/** Coil's preview handler serves [AlbumSamples.images] by path, synchronously, instead of reading files. */
@Composable
internal fun FakeImages(content: @Composable () -> Unit) {
    val handler = AsyncImagePreviewHandler { request ->
        // A missing sample shows as a magenta square, so a broken path is obvious in the PNG.
        ((request.data as? File)?.let { AlbumSamples.images[it.path] } ?: Missing).asImage()
    }
    CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides handler, content = content)
}

private val Missing: Bitmap by lazy { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF00FF.toInt()) } }

abstract class AlbumShot {
    @get:Rule
    val compose = createComposeRule()

    protected fun snap(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { BoothTheme(darkTheme = dark, reduceMotion = false) { FakeImages(content) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AlbumEmptyScreenshotTest : AlbumShot() {
    /** No strips yet: the camera-with-a-heart, the copy and Start. */
    @Test
    fun empty() = snap("album_empty_light", dark = false) { AlbumScreen(AlbumUiState(loaded = true), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AlbumFilledLightScreenshotTest : AlbumShot() {
    /** Seven strips over two months; her frames, themes, ♡ on three. */
    @Test
    fun filled() = snap("album_filled_light", dark = false) { AlbumScreen(AlbumSamples.album(), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AlbumFilledDarkScreenshotTest : AlbumShot() {
    @Test
    fun filled() = snap("album_filled_dark") { AlbumScreen(AlbumSamples.album(), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AlbumFavoritesScreenshotTest : AlbumShot() {
    /** The Favourites chip: only the ♡ strips, still under their months. */
    @Test
    fun favorites() = snap("album_favorites_dark") { AlbumScreen(AlbumSamples.album(AlbumFilter.Favorites), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class AlbumSelectScreenshotTest : AlbumShot() {
    /** Long-press multi-select: two picked, the action bar with Delete (and Save to Photos, Soon). */
    @Test
    fun select() = snap("album_select_dark") { AlbumScreen(AlbumSamples.album(selection = setOf("s1", "s3")), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class DetailStripScreenshotTest : AlbumShot() {
    @Test
    fun strip() = snap("detail_strip_dark") { DetailScreen(AlbumSamples.detail(), onEvent = {}) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class DetailPhotosScreenshotTest : AlbumShot() {
    /** The Photos tab: cut 1 with the Peach filter, and the two unused shots below. */
    @Test
    fun photos() = snap("detail_photos_dark") { DetailScreen(AlbumSamples.detail(), onEvent = {}, initialTab = DetailTab.Photos) }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class PickEditScreenshotTest : AlbumShot() {
    /** Edit (§4.7): Pick on a saved strip, re-picked with a bonus shot; "Edit your strip" and Save changes. */
    @Test
    fun edit() = snap("pick_edit_dark") {
        PickScreen(PickSamples.theme(listOf(0, 5, 2, 3), caption = "best day").copy(editing = true), onEvent = {})
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Pixel9Qualifiers)
class HomePeekScreenshotTest : AlbumShot() {
    /** §4.1: the album badge and the newest strip peeking out behind My Album at −8°. */
    @Test
    fun peek() = snap("home_peek_dark") { HomeScreen(AlbumSamples.home(), onEvent = {}) }
}
