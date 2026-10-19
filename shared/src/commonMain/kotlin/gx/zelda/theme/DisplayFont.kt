package gx.zelda.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import gx.zelda.resources.Res
import gx.zelda.resources.cinzel_bold
import gx.zelda.resources.fredoka_semibold
import gx.zelda.resources.josefin_sans_regular
import gx.zelda.resources.pixelify_sans_semibold
import gx.zelda.resources.press_start_2p
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * The lettering a game's headers are set in: an openly licensed look-alike for its era, never
 * Nintendo's own fonts (docs/fonts.md lists each one and its licence). Only display, headline and
 * titleLarge styles use it, so top bars and card headings change while body text stays readable.
 *
 * [scale] shrinks wide faces so a tracker's title still fits a top bar; [tracking] is extra
 * letter-spacing in em.
 */
enum class DisplayFont(
    val resource: FontResource?,
    val weight: FontWeight = FontWeight.Normal,
    val scale: Float = 1f,
    val tracking: Float = 0f,
) {
    /** The system font: the neutral theme. */
    DEFAULT(null),

    /** Press Start 2P, an 8×8 NES/Game Boy pixel face. Every glyph is a full em wide, hence the scale. */
    PIXEL_8BIT(Res.font.press_start_2p, scale = 0.64f),

    /** Pixelify Sans, a proportional 16-bit (SNES/GBA) pixel face. */
    PIXEL_16BIT(Res.font.pixelify_sans_semibold, FontWeight.SemiBold),

    /** Fredoka, rounded and chunky, standing in for Wind Waker's and the DS/3DS games' interface lettering. */
    ROUNDED(Res.font.fredoka_semibold, FontWeight.SemiBold),

    /**
     * Cinzel, Roman capitals like the series' logo, for the 3D console games. Caps only, so it runs
     * a little smaller to fit a top bar.
     */
    SERIF(Res.font.cinzel_bold, FontWeight.Bold, scale = 0.92f),

    /** Josefin Sans, thin and geometric with open spacing, for the Sheikah Slate and Purah Pad. */
    SHEIKAH(Res.font.josefin_sans_regular, scale = 1.05f, tracking = 0.06f),
}

/** Material's type scale with [font] on the display, headline and titleLarge styles. */
@Composable
fun DisplayFont.typography(): Typography {
    val resource = resource ?: return remember { Typography() }
    val family = FontFamily(Font(resource, weight))
    return remember(this, family) {
        val base = Typography()
        fun TextStyle.display() = copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = fontSize * scale,
            lineHeight = lineHeight * maxOf(scale, 0.8f),
            letterSpacing = tracking.em,
        )
        base.copy(
            displayLarge = base.displayLarge.display(),
            displayMedium = base.displayMedium.display(),
            displaySmall = base.displaySmall.display(),
            headlineLarge = base.headlineLarge.display(),
            headlineMedium = base.headlineMedium.display(),
            headlineSmall = base.headlineSmall.display(),
            titleLarge = base.titleLarge.display(),
        )
    }
}

/** [titleLarge][Typography.titleLarge] in this font, for a game's name outside its own screens. */
@Composable
fun DisplayFont.titleStyle(): TextStyle = typography().titleLarge
