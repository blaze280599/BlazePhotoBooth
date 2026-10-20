package com.blaze.photobooth.model

/** What a theme scatters around its cells (§5.2). Drawn as vector paths by the renderer. */
enum class Decoration { None, Sparkles, Hearts, CloudsAndStars, Clovers, StarsAndMoons, Daisies, Petals, Sprockets }

/**
 * Built-in, code-drawn frame themes (README §5.2). Colours are ARGB ints so the pure-Kotlin
 * model stays free of Android types. A theme with [backgroundBottom] == [backgroundTop] is solid.
 * [stickerColors] are the pastel inks its stickers are painted with.
 */
enum class FrameTheme(
    val id: String,
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val ink: Int,
    val decoration: Decoration,
    val stickerColors: List<Int>,
) {
    Milk("milk", 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF3A3A3A.toInt(), Decoration.None, emptyList()),
    Ink(
        "ink", 0xFF1E1E22.toInt(), 0xFF1E1E22.toInt(), 0xFFFFFFFF.toInt(), Decoration.Sparkles,
        listOf(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt()),
    ),
    Strawberry(
        // Ink #C02660: the footer stays ≥ 3:1 on the gradient's deeper pink bottom (§6.7; #D6336C was 2.9:1).
        "strawberry", 0xFFFFD6E3.toInt(), 0xFFFFB8CF.toInt(), 0xFFC02660.toInt(), Decoration.Hearts,
        listOf(0xFFFF6F9C.toInt(), 0xFFFFFFFF.toInt(), 0xFFE84A7F.toInt()),
    ),
    Cloud(
        "cloud", 0xFFCFE8FF.toInt(), 0xFFCFE8FF.toInt(), 0xFF3B6FB6.toInt(), Decoration.CloudsAndStars,
        listOf(0xFFFFFFFF.toInt(), 0xFFFFE38A.toInt(), 0xFF8EC2F5.toInt()),
    ),
    Matcha(
        "matcha", 0xFFDCEFD6.toInt(), 0xFFDCEFD6.toInt(), 0xFF4F7A45.toInt(), Decoration.Clovers,
        listOf(0xFF7DB86F.toInt(), 0xFF5E9E52.toInt(), 0xFFFFFFFF.toInt()),
    ),
    Lavender(
        "lavender", 0xFFE6DCFF.toInt(), 0xFFCDB9FF.toInt(), 0xFF6B4FBB.toInt(), Decoration.StarsAndMoons,
        listOf(0xFFFFFFFF.toInt(), 0xFFFFE38A.toInt(), 0xFF9C83F0.toInt()),
    ),
    Butter(
        "butter", 0xFFFFF1B8.toInt(), 0xFFFFF1B8.toInt(), 0xFFB07A00.toInt(), Decoration.Daisies,
        listOf(0xFFFFFFFF.toInt(), 0xFFFFD86B.toInt()),
    ),
    Cherry(
        "cherry", 0xFFFFE9EF.toInt(), 0xFFFFE9EF.toInt(), 0xFFC2185B.toInt(), Decoration.Petals,
        listOf(0xFFFFB3C7.toInt(), 0xFFFF8FB1.toInt(), 0xFFFFD1DC.toInt()),
    ),
    Film("film", 0xFF111111.toInt(), 0xFF111111.toInt(), 0xFFF5F5F5.toInt(), Decoration.Sprockets, listOf(0xFFF5F5F5.toInt())),
    ;

    val isGradient: Boolean get() = backgroundTop != backgroundBottom

    companion object {
        val Default = Strawberry

        fun fromId(id: String?): FrameTheme? = entries.firstOrNull { it.id == id }
    }
}
