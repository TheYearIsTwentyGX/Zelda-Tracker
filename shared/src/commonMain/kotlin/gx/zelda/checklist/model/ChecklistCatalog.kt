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

    /** The noun and plural for [items]: their section's when they're all from one, else the file's. */
    fun nounsOf(items: List<ChecklistItem>): Pair<String, String> {
        val section = items.map(::sectionOf).distinct().singleOrNull()
        return if (section != null) nounOf(section) to pluralOf(section) else noun to (plural ?: regularPlural(noun))
    }

    fun hiddenLabelOf(item: ChecklistItem): String {
        val section = sectionOf(item)
        return section.hiddenLabel ?: hiddenLabel ?: "Unchecked ${nounOf(section)}"
    }

    /** Steps done and steps in all of [of] (by default the file's total); an item with tiers is a step per tier. */
    fun count(progress: ChecklistProgress, of: List<ChecklistItem> = counted): ChecklistCount =
        ChecklistCount(done = of.sumOf { progress.level(it) }, total = of.sumOf { it.steps })

    /**
     * What a formula name is worth on [progress]: a section id is its steps done, and
     * `<section id>.value` the sum of their [ChecklistItem.value]s. An item id works the same for
     * that one item, e.g. a tiered upgrade's `bomb_capacity.value`.
     */
    fun nameValue(name: String, progress: ChecklistProgress): Double {
        val id = name.removeSuffix(".value")
        val of = section(id)?.items ?: item(id)?.let(::listOf) ?: throw IllegalArgumentException("Unknown name '$name'")
        return if (name.endsWith(".value")) {
            of.sumOf { it.valueOfSteps(progress.level(it)) }
        } else {
            of.sumOf { progress.level(it) }.toDouble()
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

    /** Every item done, linked ones too: what a formula's maximum is worked out from. */
    private val complete: ChecklistProgress by lazy { ChecklistProgress().withLevels(items.associateWith { it.steps }) }

    /** True when every item in [ChecklistItem.requires] is done. */
    fun isUnlocked(item: ChecklistItem, progress: ChecklistProgress): Boolean =
        item.requires.all { id -> itemsById[id]?.let(progress::isDone) ?: true }

    /**
     * What to do next: the first item not yet done whose [ChecklistItem.requires] are all done, in
     * the file's order and outside [ChecklistSection.separate] sections; null when those are all done.
     * Items [isLocked] says the story hasn't reached are never suggested.
     */
    fun next(progress: ChecklistProgress, isLocked: (ChecklistItem) -> Boolean = { false }): ChecklistItem? {
        val left = counted.filterNot { progress.isDone(it) || isLocked(it) }
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
            item.unlockedBy.forEach { if (!ID.matches(it)) add("$at: bad unlockedBy id '$it'") }
            if (item.unlockedBy.size != item.unlockedBy.toSet().size) add("$at lists an unlockedBy step twice")
            if (item.value < 0) add("$at has a negative value")
            item.tiers.forEach { if (it.name.isBlank()) add("$at has a tier with no name") }
            item.sameAs?.let { addAll(linkProblems(item, it)) }
        }
        (summaries + sections.mapNotNull { it.summary }).forEach { summary ->
            val formula = runCatching { Formula.parse(summary.formula) }
                .onFailure { add(it.message ?: "bad formula '${summary.formula}'") }
                .getOrNull() ?: return@forEach
            formula.names.filter { it.removeSuffix(".value").let { id -> section(id) == null && item(id) == null } }
                .forEach { add("summary '${summary.label}' reads unknown section or item '$it'") }
        }
        requireCycle()?.let { add("requires loop: ${it.joinToString(" → ")}") }
    }

    /**
     * What's wrong with [item]'s [ChecklistItem.sameAs] link [text]. One into another file is
     * checked against that file by `gx.zelda.checklist.data.ChecklistLinks.problems`, since a
     * catalog can't see the game's other files.
     */
    private fun linkProblems(item: ChecklistItem, text: String): List<String> {
        val at = "item '${item.id}'"
        val link = item.link ?: return listOf("$at: bad sameAs '$text': write it as [file:]item[@tier]")
        val tiered = listOfNotNull("$at has tiers and a sameAs link: only an item without tiers can be linked".takeIf { item.tiers.isNotEmpty() })
        if (link.file != null) return tiered
        val target = itemsById[link.itemId] ?: return tiered + "$at is the same as unknown item '${link.itemId}'"
        if (target == item) return tiered + "$at is the same as itself"
        val order = "$at is the same as '${target.id}', which comes after it: link the later of two items without tiers to the earlier"
        return tiered + linkTargetProblems(at, link, target) + listOfNotNull(order.takeIf { target.tiers.isEmpty() && items.indexOf(target) > items.indexOf(item) })
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

/**
 * What's wrong with a link [at] (an item, as problems name it) to [target]: a link to a link (which
 * also rules out loops), or a tier that the target doesn't have, or that's missing.
 */
fun linkTargetProblems(at: String, link: SameAs, target: ChecklistItem): List<String> = buildList {
    if (target.link != null) add("$at is the same as '$link', which is itself linked: link to what that names instead")
    when {
        target.tiers.isEmpty() && link.tier != null -> add("$at names tier ${link.tier} of '${target.id}', which has no tiers")
        target.tiers.isNotEmpty() && link.tier == null -> add("$at is the same as '${target.id}', which has tiers: name one, as '${target.id}@1'")
        link.tier != null && link.tier > target.steps -> add("$at names tier ${link.tier} of '${target.id}', which has ${target.steps}")
    }
}

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
    /**
     * Steps of the game's Main Quest (its story file, see [gx.zelda.checklist.data.storyFileOf]) that
     * must all be done before this can be had. Hidden until then while story gating is on; see [StoryGate].
     */
    val unlockedBy: List<String> = emptyList(),
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
    /**
     * The same real thing as another item of the game, so the two share one progress:
     * `[file:]item[@tier]`, e.g. `items:wallet@2` for tier 2 of `wallet` in the game's `items.json`,
     * or `goddess_sword@2` in this file. Only an item without tiers can have one; its level is the
     * other's. See [SameAs].
     */
    val sameAs: String? = null,
) {
    /** [sameAs], parsed; null when there's none or it's malformed (which [ChecklistCatalog.problems] reports). */
    val link: SameAs? by lazy { sameAs?.let(SameAs::parse) }

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
