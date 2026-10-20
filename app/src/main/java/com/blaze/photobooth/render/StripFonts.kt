package com.blaze.photobooth.render

import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.util.Log
import com.blaze.photobooth.R

/**
 * Canvas typefaces for the strip footer (README §10): Fredoka SemiBold for the wordmark and
 * Gaegu Bold for captions and dates. Both fall back to the system font for glyphs they lack
 * (✿, Hangul, emoji). Built once and cached by [StripRenderer].
 */
class StripFonts(resources: Resources) {

    val wordmark: Typeface = build(resources, R.font.fredoka, "'wght' 600")
    val handwritten: Typeface = build(resources, R.font.gaegu_bold, null)

    private fun build(resources: Resources, resId: Int, variation: String?): Typeface = try {
        val font = Font.Builder(resources, resId).apply { if (variation != null) setFontVariationSettings(variation) }.build()
        Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
            .setSystemFallback("sans-serif")
            .build()
    } catch (e: Exception) {
        Log.w("StripFonts", "Falling back to the default typeface", e)
        Typeface.DEFAULT_BOLD
    }
}
