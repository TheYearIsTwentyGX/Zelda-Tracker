package gx.zelda.figurines.ui

import gx.zelda.core.countOf
import gx.zelda.figurines.model.FigurineOdds
import gx.zelda.figurines.model.FigurineTally
import kotlin.math.roundToInt

/**
 * The collection card's lines about Carlov's machine. They name no figurine and no checkpoint, so
 * they are spoiler-safe.
 */
fun oddsLines(tally: FigurineTally): List<String> {
    val odds = tally.odds
    return when {
        tally.owned == tally.total -> listOf("Every figurine won.")
        odds.complete -> listOf("You have every figurine Carlov has made so far. He makes more as the story goes on.")
        else -> buildList {
            val shown = odds.shownChance()
            val actual = odds.actualChance()
            add(
                if (actual == shown) {
                    "Next draw: $shown% with one shell."
                } else {
                    "Next draw: $actual% with one shell. Carlov shows $shown%, but the game never rolls below " +
                        "${FigurineOdds.minimumChance(tally.owned)}% at ${countOf(tally.owned, "figurine")} owned."
                },
            )
            add(
                "One shell a draw is cheapest: about ${shells(odds.expectedShellsForNext)} per new figurine. " +
                    "${odds.shellsForCertainty} at once make Carlov show 100%.",
            )
            val left = tally.available - tally.owned
            if (left > 1) add("About ${shells(odds.expectedShellsForRest)} to win the $left available now.")
        }
    }
}

private fun shells(expected: Double): String = countOf(expected.roundToInt().coerceAtLeast(1), "shell")
