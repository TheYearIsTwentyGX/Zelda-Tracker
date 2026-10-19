package gx.zelda.adventure.model

/**
 * When the map screen hides its top bar and tabs (and the system bars) to give the map the
 * height: on the Map tab in a landscape window that's short, as a phone on its side is (Material's
 * compact height, under 480 dp). It goes by the window's own size, so split screen and foldables
 * follow what's actually on screen; the List tab and portrait always keep them.
 */
object AdventureChrome {
    const val COMPACT_HEIGHT_DP = 480f

    fun visible(widthDp: Float, heightDp: Float, tab: AdventureTab): Boolean =
        !(tab == AdventureTab.MAP && widthDp > heightDp && heightDp < COMPACT_HEIGHT_DP)
}
