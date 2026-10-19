package gx.zelda.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * How one game looks inside its own screens: a light and a dark palette, the lettering of its
 * headers, the corners of its cards, and the small ornaments shared components draw. The briefs
 * behind each are in docs/themes.md.
 */
@Immutable
data class GameTheme(
    /** The game's id, or "neutral". */
    val id: String,
    val light: GamePalette,
    val dark: GamePalette,
    val display: DisplayFont = DisplayFont.DEFAULT,
    val shapes: ShapeStyle = ShapeStyle.ROUNDED,
    val ornament: Ornament = Ornament.NONE,
    val progress: ProgressStyle = ProgressStyle.SOLID,
) {
    private val lightScheme = light.toColorScheme(dark = false, other = dark)
    private val darkScheme = dark.toColorScheme(dark = true, other = light)

    fun colorScheme(dark: Boolean): ColorScheme = if (dark) darkScheme else lightScheme

    fun palette(dark: Boolean): GamePalette = if (dark) this.dark else light

    fun decor(dark: Boolean): GameDecor = palette(dark).let { GameDecor(ornament, progress, it.star, it.accents) }
}

/** The corners of cards, sheets and buttons. */
enum class ShapeStyle(val shapes: Shapes) {
    /** Material's own. */
    ROUNDED(Shapes()),

    /** Near-square, like an 8- or 16-bit window. */
    PIXEL(
        Shapes(
            extraSmall = RoundedCornerShape(1.dp),
            small = RoundedCornerShape(2.dp),
            medium = RoundedCornerShape(2.dp),
            large = RoundedCornerShape(4.dp),
            extraLarge = RoundedCornerShape(4.dp),
        ),
    ),

    /** Clipped corners, like the Sheikah Slate's frames. */
    CUT(
        Shapes(
            extraSmall = CutCornerShape(3.dp),
            small = CutCornerShape(4.dp),
            medium = CutCornerShape(8.dp),
            large = CutCornerShape(10.dp),
            extraLarge = CutCornerShape(14.dp),
        ),
    ),

    /** Soft and round, toy-like. */
    SOFT(
        Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
    ),
}

/** The rule [OrnamentDivider] draws under a region's header. */
enum class Ornament {
    NONE,

    /** A row of square pixels, the edge of an NES window. */
    PIXEL_RULE,

    /** A rolling wave line, Wind Waker's sea. */
    WAVES,

    /** A winding stem with leaves, The Minish Cap's undergrowth. */
    VINE,

    /** A thin line with a diamond and end ticks, Sheikah tech. */
    GLYPHS,

    /** Two courses of offset bricks, Zelda II's palace stone. */
    BRICKS,

    /** A row of small Triforces. */
    TRIFORCES,

    /** A hairline broken by a dot in each season's colour ([GamePalette.accents]), in turn. */
    SEASONS,

    /** The accents as equal bands, one per Link in Four Swords. */
    COLOUR_BANDS,
}

/** How [GameProgressBar] draws progress. */
enum class ProgressStyle {
    /** Material's bar. */
    SOLID,

    /** Square blocks, filled one by one like a pixel-art meter. */
    SEGMENTED,

    /** A thick bar with round ends. */
    ROUNDED,

    /** A hairline bar. */
    THIN,
}

/** What shared components read from the current game's theme beyond Material's colours and shapes. */
@Immutable
data class GameDecor(
    val ornament: Ornament,
    val progress: ProgressStyle,
    val star: Color,
    /** [GamePalette.accents] for the current mode. */
    val accents: List<Color> = emptyList(),
)

/** The neutral theme's decor until a [gx.zelda.core.ui.ZeldaTheme] provides one. */
val LocalGameDecor = staticCompositionLocalOf { GameThemes.Neutral.decor(dark = true) }

/**
 * The look chosen in Settings: [dark] already resolves "follow the system"; [gameThemes] off keeps
 * every screen in the neutral theme. Null outside the app (tests, previews): the system's mode,
 * themes on.
 */
@Immutable
data class Appearance(val dark: Boolean, val gameThemes: Boolean)

val LocalAppearance = staticCompositionLocalOf<Appearance?> { null }
