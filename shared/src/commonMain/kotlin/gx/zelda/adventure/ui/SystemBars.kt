package gx.zelda.adventure.ui

import androidx.compose.runtime.Composable

/**
 * While [hidden], hides the status and navigation bars (a swipe from the edge shows them for a
 * moment); they come back when it's no longer hidden or the screen goes.
 */
@Composable
expect fun HideSystemBars(hidden: Boolean)
