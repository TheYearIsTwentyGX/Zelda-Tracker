package gx.zelda.theme

import androidx.compose.ui.graphics.Color

/**
 * Every game's theme, by game id. A game without one yet (and Home, Settings and the menu) uses
 * [Neutral]. The brief and sources behind each are in docs/themes.md; the contrast every pair
 * needs is checked by `GameThemeContrastTest`.
 */
object GameThemes {
    /** The app's own look: Sheikah-slate blue on deep teal, with Triforce gold for highlights. */
    val Neutral = GameTheme(
        id = "neutral",
        light = GamePalette(
            primary = Color(0xFF00687A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFB3EDF8),
            onPrimaryContainer = Color(0xFF001F26),
            secondary = Color(0xFF6F5D00),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFE08E),
            onSecondaryContainer = Color(0xFF221B00),
            tertiary = Color(0xFF386A20),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFB7F399),
            onTertiaryContainer = Color(0xFF042100),
            surface = Color(0xFFF5FAFB),
            onSurface = Color(0xFF171D1E),
            surfaceVariant = Color(0xFFDBE4E6),
            onSurfaceVariant = Color(0xFF3F484A),
            outline = Color(0xFF6F797A),
            star = Color(0xFF8A6A00),
        ),
        // The app's original dark scheme, unchanged.
        dark = GamePalette(
            primary = Color(0xFF5FD4EA),
            onPrimary = Color(0xFF00363F),
            primaryContainer = Color(0xFF0F4A55),
            onPrimaryContainer = Color(0xFFB3EDF8),
            secondary = Color(0xFFE8C15A),
            onSecondary = Color(0xFF3B2F00),
            secondaryContainer = Color(0xFF574500),
            onSecondaryContainer = Color(0xFFFFE08E),
            tertiary = Color(0xFF9CD67F),
            onTertiary = Color(0xFF113800),
            tertiaryContainer = Color(0xFF285112),
            onTertiaryContainer = Color(0xFFB7F399),
            surface = Color(0xFF0B1619),
            onSurface = Color(0xFFDCE6E8),
            surfaceVariant = Color(0xFF213236),
            onSurfaceVariant = Color(0xFFB5C7CB),
            outline = Color(0xFF7F9195),
            outlineVariant = Color(0xFF34474B),
            star = Color(0xFFE8C15A),
            containers = listOf(Color(0xFF071012), Color(0xFF111E21), Color(0xFF152427), Color(0xFF1C2D31), Color(0xFF24373B)),
        ),
    )

    /**
     * NES. Dark is the subscreen: black, heart red, item-box blue, Triforce orange. Light is the
     * overworld: tan ground and forest green. Colours are NES palette entries, darkened a step
     * where text needs the contrast.
     */
    val LOZ = GameTheme(
        id = "loz",
        light = GamePalette(
            primary = Color(0xFF005800),
            onPrimary = Color(0xFFFCFCFC),
            primaryContainer = Color(0xFFB8F8B8),
            onPrimaryContainer = Color(0xFF005800),
            secondary = Color(0xFFA81000),
            onSecondary = Color(0xFFFCFCFC),
            secondaryContainer = Color(0xFFF87858),
            onSecondaryContainer = Color(0xFF000000),
            tertiary = Color(0xFF0000BC),
            onTertiary = Color(0xFFFCFCFC),
            tertiaryContainer = Color(0xFFA4E4FC),
            onTertiaryContainer = Color(0xFF0000BC),
            surface = Color(0xFFFCE0A8),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFF4D098),
            onSurfaceVariant = Color(0xFF503000),
            outline = Color(0xFF6C6C6C),
            star = Color(0xFF7C4C00),
            error = Color(0xFFA81000),
            onError = Color(0xFFFCFCFC),
        ),
        dark = GamePalette(
            primary = Color(0xFFF83800),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFF881400),
            onPrimaryContainer = Color(0xFFFCE0A8),
            secondary = Color(0xFF6888FC),
            onSecondary = Color(0xFF000000),
            secondaryContainer = Color(0xFF0000BC),
            onSecondaryContainer = Color(0xFFFCFCFC),
            tertiary = Color(0xFFFCA044),
            onTertiary = Color(0xFF000000),
            tertiaryContainer = Color(0xFF503000),
            onTertiaryContainer = Color(0xFFFCE0A8),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFFCFCFC),
            surfaceVariant = Color(0xFF181818),
            onSurfaceVariant = Color(0xFFBCBCBC),
            outline = Color(0xFF7C7C7C),
            star = Color(0xFFFCA044),
        ),
        display = DisplayFont.PIXEL_8BIT,
        shapes = ShapeStyle.PIXEL,
        ornament = Ornament.PIXEL_RULE,
        progress = ProgressStyle.SEGMENTED,
    )

    /**
     * Cel-shaded sea. Light is the parchment sea chart with deep-sea blue; dark is the GameCube
     * item menu's forest green with the cerulean sea as the accent.
     */
    val WW = GameTheme(
        id = "ww",
        light = GamePalette(
            primary = Color(0xFF00639B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCDE5FF),
            onPrimaryContainer = Color(0xFF001D33),
            secondary = Color(0xFF1F5A3A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFB4F0CB),
            onSecondaryContainer = Color(0xFF002112),
            tertiary = Color(0xFFA33A2A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFDAD3),
            onTertiaryContainer = Color(0xFF3E0500),
            surface = Color(0xFFF7EFD9),
            onSurface = Color(0xFF2A2216),
            surfaceVariant = Color(0xFFE8D8A8),
            onSurfaceVariant = Color(0xFF4F4430),
            outline = Color(0xFF7E705A),
            star = Color(0xFF7D5D00),
        ),
        dark = GamePalette(
            primary = Color(0xFF7CC8F8),
            onPrimary = Color(0xFF00344F),
            primaryContainer = Color(0xFF0E4C70),
            onPrimaryContainer = Color(0xFFCDE5FF),
            secondary = Color(0xFFE8D8A8),
            onSecondary = Color(0xFF3A2F12),
            secondaryContainer = Color(0xFF54472A),
            onSecondaryContainer = Color(0xFFF7EBC8),
            tertiary = Color(0xFFFFB4A6),
            onTertiary = Color(0xFF5F1408),
            tertiaryContainer = Color(0xFF7F2A1C),
            onTertiaryContainer = Color(0xFFFFDAD3),
            surface = Color(0xFF0F2419),
            onSurface = Color(0xFFE6F2E8),
            surfaceVariant = Color(0xFF24402F),
            onSurfaceVariant = Color(0xFFBFD6C6),
            outline = Color(0xFF87A090),
            star = Color(0xFFF5CF5A),
        ),
        display = DisplayFont.ROUNDED,
        shapes = ShapeStyle.SOFT,
        ornament = Ornament.WAVES,
        progress = ProgressStyle.ROUNDED,
    )

    /** GBA storybook miniature: Ezlo's leaf green, Kinstone gold and warm wood, on paper or bark. */
    val MC = GameTheme(
        id = "mc",
        light = GamePalette(
            primary = Color(0xFF2F6E14),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFC4EFA8),
            onPrimaryContainer = Color(0xFF0B2000),
            secondary = Color(0xFF7A5A00),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFE08A),
            onSecondaryContainer = Color(0xFF261A00),
            tertiary = Color(0xFF8A4F24),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFDCC2),
            onTertiaryContainer = Color(0xFF301400),
            surface = Color(0xFFFBF7EC),
            onSurface = Color(0xFF22201A),
            surfaceVariant = Color(0xFFECE3CF),
            onSurfaceVariant = Color(0xFF4E4636),
            outline = Color(0xFF7F7562),
            star = Color(0xFF8A6400),
        ),
        dark = GamePalette(
            primary = Color(0xFF8FD86A),
            onPrimary = Color(0xFF133800),
            primaryContainer = Color(0xFF2C5212),
            onPrimaryContainer = Color(0xFFC4EFA8),
            secondary = Color(0xFFF0C64A),
            onSecondary = Color(0xFF3F2E00),
            secondaryContainer = Color(0xFF5C4400),
            onSecondaryContainer = Color(0xFFFFE08A),
            tertiary = Color(0xFFE8A877),
            onTertiary = Color(0xFF4A2300),
            tertiaryContainer = Color(0xFF6B3A16),
            onTertiaryContainer = Color(0xFFFFDCC2),
            surface = Color(0xFF1A1610),
            onSurface = Color(0xFFEFE6D4),
            surfaceVariant = Color(0xFF3A3326),
            onSurfaceVariant = Color(0xFFD2C6AE),
            outline = Color(0xFF9A8F7A),
            star = Color(0xFFF0C64A),
        ),
        display = DisplayFont.PIXEL_16BIT,
        ornament = Ornament.VINE,
        progress = ProgressStyle.SEGMENTED,
    )

    /**
     * Minimal and ancient: "Zelda White" lettering on the Sheikah Slate's dark glass, Sheikah blue
     * glow and Guardian orange. Light turns it round: Zelda White paper with deep slate blue.
     */
    val BOTW = GameTheme(
        id = "botw",
        light = GamePalette(
            primary = Color(0xFF00677F),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFBDEBFA),
            onPrimaryContainer = Color(0xFF001F28),
            secondary = Color(0xFF9A4100),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDBC8),
            onSecondaryContainer = Color(0xFF331100),
            tertiary = Color(0xFF6A5B2A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFF3E1A8),
            onTertiaryContainer = Color(0xFF221B00),
            surface = Color(0xFFF6F1E3),
            onSurface = Color(0xFF1D1B16),
            surfaceVariant = Color(0xFFE6DFCC),
            onSurfaceVariant = Color(0xFF4B463A),
            outline = Color(0xFF7A7464),
            star = Color(0xFF7E5F00),
        ),
        dark = GamePalette(
            primary = Color(0xFF5CD3F5),
            onPrimary = Color(0xFF00313D),
            primaryContainer = Color(0xFF0B4A5C),
            onPrimaryContainer = Color(0xFFBDEFFF),
            secondary = Color(0xFFFF9A55),
            onSecondary = Color(0xFF3D1600),
            secondaryContainer = Color(0xFF6B2F00),
            onSecondaryContainer = Color(0xFFFFDCC4),
            tertiary = Color(0xFFD9C48A),
            onTertiary = Color(0xFF3A2F00),
            tertiaryContainer = Color(0xFF54471A),
            onTertiaryContainer = Color(0xFFF6E6B4),
            surface = Color(0xFF0E1214),
            onSurface = Color(0xFFF4EEDC),
            surfaceVariant = Color(0xFF232A2D),
            onSurfaceVariant = Color(0xFFC9C3B2),
            outline = Color(0xFF8C8A80),
            star = Color(0xFFF2C94C),
        ),
        display = DisplayFont.SHEIKAH,
        shapes = ShapeStyle.CUT,
        ornament = Ornament.GLYPHS,
        progress = ProgressStyle.THIN,
    )

    /** The themed games by id. The rest, Hyrule Warriors ("hwde") included, use [Neutral] until their batch. */
    val byId: Map<String, GameTheme> = listOf(LOZ, WW, MC, BOTW).associateBy { it.id }

    /** [gameId]'s theme; [Neutral] for no game or one without a theme yet. */
    fun of(gameId: String?): GameTheme = byId[gameId] ?: Neutral
}
