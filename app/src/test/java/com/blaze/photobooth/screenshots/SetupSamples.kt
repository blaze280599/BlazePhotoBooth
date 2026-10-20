package com.blaze.photobooth.screenshots

import android.content.Context
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.BoothConfig
import com.blaze.photobooth.model.FrameId
import com.blaze.photobooth.model.FrameSource
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.StripSpec
import com.blaze.photobooth.render.BitmapOps
import com.blaze.photobooth.render.SampleArt
import com.blaze.photobooth.ui.feature.setup.FrameCardState
import com.blaze.photobooth.ui.feature.setup.SetupUiState
import java.time.LocalDate

/** Setup states for screenshots, built from the real catalog (her frames from assets, all themes). */
object SetupSamples {

    private val date = LocalDate.of(2026, 10, 20)

    private fun cards(context: Context?): List<FrameCardState> {
        val her = context?.let { ctx ->
            TestArt.herFrames(ctx).map { FrameCardState(FrameOption.Artwork(it), TestArt.load(ctx, it.thumb)) }
        }.orEmpty()
        return her + FrameTheme.entries.map { FrameCardState(FrameOption.Theme(it)) }
    }

    /** Strawberry Milk on the classic strip. With a [context], the carousel also holds her frames. */
    fun theme(context: Context? = null, filter: Boolean = false): SetupUiState {
        val config = BoothConfig(frameId = FrameId.theme(FrameTheme.Strawberry))
        return SetupUiState(
            config = config,
            frames = cards(context),
            preview = StripSpec(FrameSource.Theme(FrameTheme.Strawberry), config.layout, config.filter, date = date, preview = true),
            filterSample = if (filter) SampleArt.filterSample(240) else null,
        )
    }

    /** One of her frames selected (Lilac Pals, the 3-window one, so "Set by frame" shows 3 cuts). */
    fun herFrame(context: Context, id: String = "lilac-pals"): SetupUiState {
        val frame = TestArt.herFrames(context).first { it.id == FrameId.asset(id) }
        val art = TestArt.load(context, frame.artwork, maxWidth = BitmapOps.PreviewMaxPx)
        val config = BoothConfig(frameId = frame.id)
        return SetupUiState(
            config = config,
            frames = cards(context),
            preview = StripSpec(FrameSource.Image(frame, art), filter = config.filter, date = date, preview = true),
        )
    }
}
