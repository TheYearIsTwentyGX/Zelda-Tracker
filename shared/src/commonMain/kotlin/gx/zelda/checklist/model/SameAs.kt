package gx.zelda.checklist.model

/**
 * Where a level is stored: [itemId] in the checklist being read ([file] null), or in the same
 * game's checklist named [file] (its file name without `.json`, e.g. `items`), on the same save file.
 */
data class LevelRef(val file: String?, val itemId: String)

/**
 * A [ChecklistItem.sameAs] link, parsed: the item is the same real thing as [itemId], or as its
 * tier [tier] (1-based) when that item has tiers, so the two share one progress. `items:wallet@2`
 * is tier 2 of `wallet` in the game's `items.json`; `goddess_sword@2` is in the same file.
 *
 * The item that declares it (the *alias*) keeps no level of its own: it reads and writes [ref]'s,
 * the *canonical* level. See docs/checklist-trackers.md, "Linked items".
 */
data class SameAs(val file: String?, val itemId: String, val tier: Int? = null) {
    val ref: LevelRef get() = LevelRef(file, itemId)

    /** The canonical level at which the alias counts as done: its tier, or 1 for an untiered item. */
    private val step: Int get() = tier ?: 1

    /** The alias's level, 0 or 1, when the canonical item is at [canonical]. */
    fun levelFrom(canonical: Int): Int = if (canonical >= step) 1 else 0

    /**
     * The canonical level once the alias is ticked or unticked, from [canonical]: ticking raises it
     * to at least the tier (so the tiers before it too), unticking lowers it to below the tier (so
     * the tiers after it go too), as ticking a tier on the item's own page does.
     */
    fun canonicalFor(done: Boolean, canonical: Int): Int = if (done) maxOf(canonical, step) else minOf(canonical, step - 1)

    override fun toString(): String = listOfNotNull(file?.let { "$it:" }, itemId, tier?.let { "@$it" }).joinToString("")

    companion object {
        private val FORMAT = Regex("(?:([a-z0-9_]+):)?([a-z0-9_]+)(?:@([1-9][0-9]{0,3}))?")

        /** `[file:]item[@tier]`, or null when [text] isn't in that form. */
        fun parse(text: String): SameAs? = FORMAT.matchEntire(text)?.let { m ->
            val (file, item, tier) = m.destructured
            SameAs(file.ifEmpty { null }, item, tier.ifEmpty { null }?.toInt())
        }
    }
}
