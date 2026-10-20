package com.blaze.photobooth.ui.feature.setup

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.blaze.photobooth.R
import com.blaze.photobooth.data.frames.FrameOption
import com.blaze.photobooth.model.BoothLayout
import com.blaze.photobooth.model.FrameTheme
import com.blaze.photobooth.model.PhotoFilter

// Display names for the catalog (§5.1–5.3), kept in strings.xml (§6.6).

@get:StringRes
val BoothLayout.nameRes: Int
    get() = when (this) {
        BoothLayout.Classic -> R.string.layout_classic
        BoothLayout.Grid -> R.string.layout_grid
        BoothLayout.Trio -> R.string.layout_trio
        BoothLayout.Six -> R.string.layout_six
        BoothLayout.Solo -> R.string.layout_solo
    }

@get:StringRes
val FrameTheme.nameRes: Int
    get() = when (this) {
        FrameTheme.Milk -> R.string.theme_milk
        FrameTheme.Ink -> R.string.theme_ink
        FrameTheme.Strawberry -> R.string.theme_strawberry
        FrameTheme.Cloud -> R.string.theme_cloud
        FrameTheme.Matcha -> R.string.theme_matcha
        FrameTheme.Lavender -> R.string.theme_lavender
        FrameTheme.Butter -> R.string.theme_butter
        FrameTheme.Cherry -> R.string.theme_cherry
        FrameTheme.Film -> R.string.theme_film
    }

@get:StringRes
val PhotoFilter.nameRes: Int
    get() = when (this) {
        PhotoFilter.Original -> R.string.filter_original
        PhotoFilter.Mono -> R.string.filter_mono
        PhotoFilter.Milky -> R.string.filter_milky
        PhotoFilter.Peach -> R.string.filter_peach
        PhotoFilter.Cool -> R.string.filter_cool
        PhotoFilter.Film -> R.string.filter_film
    }

/** Her frames carry their own names (from frames.json); themes are translated. */
@Composable
@ReadOnlyComposable
fun FrameOption.displayName(): String = when (this) {
    is FrameOption.Artwork -> frame.name
    is FrameOption.Theme -> stringResource(theme.nameRes)
}
