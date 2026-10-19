package gx.zelda.adventure.model

/** Where a tapped square's details open. */
enum class SquareContainer {
    /** A bottom sheet, opened all the way. */
    SHEET,

    /** A panel down the right-hand side, with the map or list still on the left. */
    PANEL,
}

/**
 * How the map screen lays itself out for the window. It goes by the window's own size, so split
 * screen and foldables follow what's actually on screen.
 * - It hides its top bar and tabs (and the system bars) to give the map the height on the Map tab
 *   in a landscape window that's short, as a phone on its side is (Material's compact height,
 *   under 480 dp). The List tab and portrait always keep them.
 * - In that same short landscape window, on either tab, a square's details open in a side panel:
 *   a bottom sheet there gets about half the width and little height.
 */
object AdventureChrome {
    const val COMPACT_HEIGHT_DP = 480f

    /** The side panel's share of the window's width, kept between [PANEL_MIN_DP] and [PANEL_MAX_DP]. */
    const val PANEL_FRACTION = 0.42f
    const val PANEL_MIN_DP = 320f
    const val PANEL_MAX_DP = 480f

    /** The width each tick box needs to sit two to a row. */
    const val CHECK_CELL_MIN_DP = 170f

    fun visible(widthDp: Float, heightDp: Float, tab: AdventureTab): Boolean =
        !(tab == AdventureTab.MAP && shortLandscape(widthDp, heightDp))

    fun squareContainer(widthDp: Float, heightDp: Float): SquareContainer =
        if (shortLandscape(widthDp, heightDp)) SquareContainer.PANEL else SquareContainer.SHEET

    fun panelWidthDp(widthDp: Float): Float = (widthDp * PANEL_FRACTION).coerceIn(PANEL_MIN_DP, PANEL_MAX_DP)

    /** How many tick boxes go side by side in a column [widthDp] wide: two where they fit. */
    fun checkColumns(widthDp: Float): Int = if (widthDp >= 2 * CHECK_CELL_MIN_DP) 2 else 1

    private fun shortLandscape(widthDp: Float, heightDp: Float) = widthDp > heightDp && heightDp < COMPACT_HEIGHT_DP
}
