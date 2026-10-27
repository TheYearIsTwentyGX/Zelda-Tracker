package gx.zelda.map.model

import gx.zelda.adventure.model.AdventureChrome
import gx.zelda.adventure.model.AdventureTab
import gx.zelda.adventure.model.SquareContainer

/** Where a tapped marker's details open. */
enum class MarkerContainer {
    /** A bottom sheet, opened all the way. */
    SHEET,

    /** A panel down the right-hand side, with the map still in use on the left. */
    PANEL,
}

/**
 * How the map screen lays itself out for the window, the same way the HW:DE Adventure Maps' Map tab
 * does ([AdventureChrome]), so the two map screens behave alike:
 * - In a landscape window that's short, as a phone on its side is, it hides its top bar (and the
 *   system bars) to give the map the height. The chip row then carries back, the save file, About
 *   and the menu. Portrait, and a tablet on its side, keep the bar.
 * - In that same window a marker's details open in a side panel, since a bottom sheet there gets
 *   about half the width and little height. The map stays full width under the panel, and a marker
 *   opened under it is panned into the part still showing ([needsReveal]).
 */
object MapChrome {
    fun barsVisible(widthDp: Float, heightDp: Float): Boolean = AdventureChrome.visible(widthDp, heightDp, AdventureTab.MAP)

    fun markerContainer(widthDp: Float, heightDp: Float): MarkerContainer =
        when (AdventureChrome.squareContainer(widthDp, heightDp)) {
            SquareContainer.PANEL -> MarkerContainer.PANEL
            SquareContainer.SHEET -> MarkerContainer.SHEET
        }

    fun panelWidthDp(widthDp: Float): Float = AdventureChrome.panelWidthDp(widthDp)

    /**
     * Whether a marker drawn [markerX] px from the map's left edge is out of the part of it the
     * panel leaves showing, [visibleWidth] px wide, or within [margin] px of either side of it.
     */
    fun needsReveal(markerX: Float, visibleWidth: Float, margin: Float): Boolean =
        markerX < margin || markerX > visibleWidth - margin
}
