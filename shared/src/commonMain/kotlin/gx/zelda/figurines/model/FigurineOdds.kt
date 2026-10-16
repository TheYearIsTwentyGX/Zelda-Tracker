package gx.zelda.figurines.model

/**
 * The odds of a new figurine from Carlov's machine, as the game works them out
 * (zeldaret/tmc, `src/object/figurineDevice.c`; see docs/mc-figurines.md). [available] is how many
 * figurines Carlov can give the save file right now (the game's pool) and [owned] how many of them
 * it has.
 *
 * - Carlov shows ⌊100 × (available − owned) / available⌋ % for one shell, and never less than 1 %
 *   while any are left. Each extra shell adds 1 %, up to 100 %.
 * - The draw itself then raises that to a minimum the game doesn't show: 15 % until 50 figurines
 *   are owned, 12 % until 80, 9 % until 110 and 6 % after.
 */
data class FigurineOdds(val available: Int, val owned: Int) {
    /** Nothing left to win until more figurines become available. */
    val complete: Boolean get() = owned >= available

    /** The percentage Carlov's machine shows when [shells] are put in. */
    fun shownChance(shells: Int = 1): Int {
        if (complete || shells < 1) return 0
        val base = (100 * (available - owned) / available).coerceAtLeast(1)
        return (base + shells - 1).coerceAtMost(100)
    }

    /** The chance the draw actually rolls against, in percent. */
    fun actualChance(shells: Int = 1): Int =
        if (complete || shells < 1) 0 else maxOf(shownChance(shells), minimumChance(owned))

    /** Shells to put in for the machine to show 100 %. */
    val shellsForCertainty: Int get() = if (complete) 0 else 101 - shownChance(1)

    /**
     * Shells a new figurine costs on average, one shell per draw. One at a time is always the
     * cheapest way: each extra shell adds only 1 %.
     */
    val expectedShellsForNext: Double get() = if (complete) 0.0 else 100.0 / actualChance(1)

    /** Shells to win every figurine available now, on average, one shell per draw. */
    val expectedShellsForRest: Double
        get() = (owned until available).sumOf { 100.0 / FigurineOdds(available, it).actualChance(1) }

    companion object {
        /** The lowest chance the game rolls against, by how many figurines are owned. */
        fun minimumChance(owned: Int): Int = when {
            owned < 50 -> 15
            owned < 80 -> 12
            owned < 110 -> 9
            else -> 6
        }
    }
}
