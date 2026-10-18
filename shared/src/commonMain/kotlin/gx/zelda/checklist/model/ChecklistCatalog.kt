package gx.zelda.checklist.model

import kotlinx.serialization.Serializable

/**
 * One checklist tracker's dataset: a bundled JSON file named by a [gx.zelda.core.Tracker]'s
 * `dataFile`. The format is written up in docs/checklist-trackers.md; [problems] checks a file
 * against it.
 */
@Serializable
data class ChecklistCatalog(
    /** Regions in display order. Items name one of these, or none for a tracker with no geography. */
    val regions: List<String> = emptyList(),
    /** What one item is called, for the overview's lines: "echo", "side quest". */
    val noun: String = "entry",
    val plural: String? = null,
    /** What a spoiler-hidden row says instead of its name, e.g. "Unlearned echo". */
    val hiddenLabel: String? = null,
    /** Totals shown above the list, e.g. hearts from pieces and containers. */
    val summaries: List<ChecklistSummary> = emptyList(),
    val sections: List<ChecklistSection>,
) {
    /** Every item, section by section. */
    val items: List<ChecklistItem> by lazy { sections.flatMap { it.items } }

    /** The items the file's total and [next] count: every section's but the [ChecklistSection.separate] ones. */
    val counted: List<ChecklistItem> by lazy { sections.filterNot { it.separate }.flatMap { it.items } }

    private val itemsById: Map<String, ChecklistItem> by lazy { items.associateBy { it.id } }
    private val sectionByItem: Map<String, ChecklistSection> by lazy {
        sections.flatMap { s -> s.items.map { it.id to s } }.toMap()
    }

    fun item(id: String): ChecklistItem? = itemsById[id]

    fun section(id: String): ChecklistSection? = sections.firstOrNull { it.id == id }

    fun sectionOf(item: ChecklistItem): ChecklistSection = sectionByItem.getValue(item.id)

    /** The heading [item] is listed under: its region, or its section's title when it has none. */
    fun groupOf(item: ChecklistItem): String = item.region ?: sectionOf(item).title

    /** Headings in display order: [regions], then the titles of sections with region-less items. */
    val groupOrder: List<String> by lazy { (regions + sections.map { it.title }).distinct() }

    /** [visible] split by heading in [groupOrder]; headings with nothing in [visible] are left out. */
    fun grouped(visible: List<ChecklistItem>): List<Pair<String, List<ChecklistItem>>> {
        val byGroup = visible.groupBy(::groupOf)
        return groupOrder.mapNotNull { g -> byGroup[g]?.let { g to it } }
    }

    fun nounOf(section: ChecklistSection): String = section.noun ?: noun

    /** The section's plural, else its noun's regular plural, else the catalog's. Irregular ones ("echoes") are spelled out in the file. */
    fun pluralOf(section: ChecklistSection): String = section.plural ?: section.noun?.let(::regularPlural) ?: plural ?: regularPlural(noun)

    fun hiddenLabelOf(item: ChecklistItem): String {
        val section = sectionOf(item)
        return section.hiddenLabel ?: hiddenLabel ?: "Unchecked ${nounOf(section)}"
    }

    /** Steps done and steps in all of [of] (by default the file's total); an item with tiers is a step per tier. */
    fun count(progress: ChecklistProgress, of: List<ChecklistItem> = counted): ChecklistCount =
        ChecklistCount(done = of.sumOf { progress.level(it) }, total = of.sumOf { it.steps })

    /**
     * What a formula name is worth on [progress]: a section id is its steps done, and
     * `<section id>.value` the sum of their [ChecklistItem.value]s.
     */
    fun nameValue(name: String, progress: ChecklistProgress): Double {
        val section = section(name.removeSuffix(".value")) ?: throw IllegalArgumentException("Unknown name '$name'")
        return if (name.endsWith(".value")) {
            section.items.sumOf { it.valueOfSteps(progress.level(it)) }
        } else {
            section.items.sumOf { progress.level(it) }.toDouble()
        }
    }

    /** [summary] on [progress], and with every item done. */
    fun evaluate(summary: ChecklistSummary, progress: ChecklistProgress): SummaryValue {
        val formula = Formula.parse(summary.formula)
        return SummaryValue(
            label = summary.label,
            value = formula.eval { nameValue(it, progress) },
            max = formula.eval { nameValue(it, complete) },
        )
    }

    /** Every item done: what a formula's maximum is worked out from. */
    private val complete: ChecklistProgress by lazy { ChecklistProgress(items.associate { it.id to it.steps }) }

    /** True when every item in [ChecklistItem.requires] is done. */
    fun isUnlocked(item: ChecklistItem, progress: ChecklistProgress): Boolean =
        item.requires.all { id -> itemsById[id]?.let(progress::isDone) ?: true }

    /**
     * What to do next: the first item not yet done whose [ChecklistItem.requires] are all done, in
     * the file's order and outside [ChecklistSection.separate] sections; null when those are all done.
     */
    fun next(progress: ChecklistProgress): ChecklistItem? {
        val left = counted.filterNot(progress::isDone)
        return left.firstOrNull { isUnlocked(it, progress) } ?: left.firstOrNull()
    }

    /**
     * Everything wrong with this dataset, as readable lines; empty when it's sound. Dataset tests
     * assert this is empty for every checklist a game lists.
     */
    fun problems(): List<String> = buildList {
        if (sections.isEmpty()) add("no sections")
        if (sections.isNotEmpty() && sections.all { it.separate }) add("every section is separate, so nothing counts toward the total")
        sections.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { add("duplicate section id '$it'") }
        sections.forEach { s ->
            if (!ID.matches(s.id)) add("bad section id '${s.id}'")
            if (s.title.isBlank()) add("section '${s.id}' has no title")
            if (s.items.isEmpty()) add("section '${s.id}' has no items")
        }
        items.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { add("duplicate item id '$it'") }
        sections.map { it.id }.intersect(items.map { it.id }.toSet()).forEach { add("'$it' is both a section and an item id") }
        if (regions.size != regions.toSet().size) add("a region is listed twice")
        regions.filter { r -> items.none { it.region == r } }.forEach { add("region '$it' has no items") }
        items.forEach { item ->
            val at = "item '${item.id}'"
            if (!ID.matches(item.id)) add("bad item id '${item.id}'")
            if (item.name.isBlank()) add("$at has no name")
            if (item.region != null && item.region !in regions) add("$at: unknown region '${item.region}'")
            item.requires.forEach { if (it !in itemsById) add("$at requires unknown item '$it'") else if (it == item.id) add("$at requires itself") }
            if (item.value < 0) add("$at has a negative value")
            item.tiers.forEach { if (it.name.isBlank()) add("$at has a tier with no name") }
        }
        (summaries + sections.mapNotNull { it.summary }).forEach { summary ->
            val formula = runCatching { Formula.parse(summary.formula) }
                .onFailure { add(it.message ?: "bad formula '${summary.formula}'") }
                .getOrNull() ?: return@forEach
            formula.names.filter { section(it.removeSuffix(".value")) == null }
                .forEach { add("summary '${summary.label}' reads unknown section '$it'") }
        }
        requireCycle()?.let { add("requires loop: ${it.joinToString(" → ")}") }
    }

    /** A chain of [ChecklistItem.requires] that leads back to its start, if there is one. */
    private fun requireCycle(): List<String>? {
        val done = mutableSetOf<String>()
        fun visit(id: String, path: List<String>): List<String>? {
            if (id in path) return path.dropWhile { it != id } + id
            if (!done.add(id)) return null
            return itemsById[id]?.requires?.firstNotNullOfOrNull { visit(it, path + id) }
        }
        return items.firstNotNullOfOrNull { visit(it.id, emptyList()) }
    }
}

private val ID = Regex("[a-z0-9_]+")

/** "entry" → "entries", "box" → "boxes", "quest" → "quests". */
private fun regularPlural(noun: String): String = when {
    noun.length > 1 && noun.endsWith("y") && noun[noun.length - 2] !in "aeiou" -> noun.dropLast(1) + "ies"
    listOf("s", "x", "z", "ch", "sh").any(noun::endsWith) -> noun + "es"
    else -> noun + "s"
}

/** A tab of the tracker, e.g. "Pieces of Heart"; a file with one section shows no tabs. */
@Serializable
data class ChecklistSection(
    val id: String,
    val title: String,
    /** Overrides the catalog's [ChecklistCatalog.noun] for this section. */
    val noun: String? = null,
    val plural: String? = null,
    val hiddenLabel: String? = null,
    /** One line shown above the section's list. */
    val note: String? = null,
    /** A total shown in the section's tab, e.g. Might Crystals collected. */
    val summary: ChecklistSummary? = null,
    /**
     * Counted on its own, e.g. another release's version of the list: its tab and the overview's
     * section counts include it, but the file's total and [ChecklistCatalog.next] leave it out.
     */
    val separate: Boolean = false,
    val items: List<ChecklistItem>,
)

/** One thing to tick off. Every field but [id] and [name] is optional. */
@Serializable
data class ChecklistItem(
    /** Stable: progress is saved by it, so it never changes once shipped. Unique in its file. */
    val id: String,
    val name: String,
    val region: String? = null,
    /** The exact spot: a cave, a house, a dungeon. */
    val area: String? = null,
    /** How to get it, precise enough to find it. */
    val howTo: String? = null,
    /** Items in the same file to do first. */
    val requires: List<String> = emptyList(),
    /** A story point to reach first, as text, e.g. "After Jabul Ruins". */
    val after: String? = null,
    /** Can be missed for good on a save file. */
    val missable: Boolean = false,
    /** What one step is worth in a summary formula's `<section>.value`. */
    val value: Double = 1.0,
    val reward: String? = null,
    val notes: String? = null,
    /** Shown on the row and searchable, e.g. an echo's category. */
    val tags: List<String> = emptyList(),
    /** Stages of one thing tracked on one row, e.g. upgrade levels; done when every tier is. */
    val tiers: List<ChecklistTier> = emptyList(),
) {
    /** One step, or one per tier. */
    val steps: Int get() = if (tiers.isEmpty()) 1 else tiers.size

    /** The summed value of the first [level] steps. */
    fun valueOfSteps(level: Int): Double =
        if (tiers.isEmpty()) (if (level >= 1) value else 0.0) else tiers.take(level).sumOf { it.value ?: value }
}

@Serializable
data class ChecklistTier(
    val name: String,
    val howTo: String? = null,
    val reward: String? = null,
    /** Overrides the item's [ChecklistItem.value] for this step. */
    val value: Double? = null,
)

/** A total worked out from the sections, e.g. `"Hearts"` = `3 + floor(pieces / 4) + containers`. */
@Serializable
data class ChecklistSummary(val label: String, val formula: String)

/** A [ChecklistSummary] worked out: [value] now, [max] with everything done. */
data class SummaryValue(val label: String, val value: Double, val max: Double) {
    /** "Hearts 5¼ / 20". */
    val text: String get() = "$label ${formatAmount(value)} / ${formatAmount(max)}"
}

data class ChecklistCount(val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done >= total
}
