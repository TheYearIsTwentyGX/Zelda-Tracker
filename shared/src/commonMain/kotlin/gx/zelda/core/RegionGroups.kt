package gx.zelda.core

/**
 * [items] split by region in [regions] order, for the region-grouped trackers; regions with
 * nothing in [items] are left out, and so is anything whose region isn't in [regions].
 */
fun <T> groupByRegion(regions: List<String>, items: List<T>, regionOf: (T) -> String): List<Pair<String, List<T>>> {
    val grouped = items.groupBy(regionOf)
    return regions.mapNotNull { region -> grouped[region]?.let { region to it } }
}

/**
 * Spoiler-safe mode for the region-grouped trackers: an entry is hidden while the mode is on, it
 * hasn't been found and the user hasn't tapped to reveal it.
 */
fun isSpoilerHidden(hideUndiscovered: Boolean, found: Boolean, revealed: Boolean): Boolean =
    hideUndiscovered && !found && !revealed

/**
 * Whether a list entry matches [query]. A [hidden] entry only matches on its [region], so a search
 * can't reveal what its row hides; [visibleText] is only read for an entry that isn't hidden.
 */
fun matchesQuery(query: String, hidden: Boolean, region: String, visibleText: () -> List<String?>): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val searchable = if (hidden) listOf(region) else visibleText().filterNotNull()
    return searchable.any { it.contains(q, ignoreCase = true) }
}
