package gx.zelda.compendium.logic

import gx.zelda.compendium.model.CompendiumCategory
import gx.zelda.compendium.model.CompendiumEntry

/**
 * A run of list rows under one header; [title] is null when the tab needs no header.
 * [photographed] and [total] count the whole section on the save file, whatever the search left.
 */
data class CompendiumSection(
    val key: String,
    val title: String?,
    val entries: List<CompendiumEntry>,
    val photographed: Int,
    val total: Int,
)

/** Photographed / total for one category on one save file. */
data class CategoryProgress(val category: CompendiumCategory, val photographed: Int, val total: Int)

object CompendiumFilter {
    /**
     * Whether [entry] matches a search: a number ("84", "084" or "#84", as numbered in this mode),
     * or text found in its name, a common location or a drop.
     */
    fun matches(entry: CompendiumEntry, query: String, masterMode: Boolean): Boolean {
        val q = query.trim().removePrefix("#").trim()
        if (q.isEmpty()) return true
        q.toIntOrNull()?.let { return entry.numberIn(masterMode) == it }
        return entry.name.contains(q, ignoreCase = true) ||
            entry.locations.any { it.contains(q, ignoreCase = true) } ||
            entry.drops.any { it.contains(q, ignoreCase = true) }
    }

    /**
     * The sections a tab shows. [entries] are the save file's own, in order; [category] null is
     * the All tab. Headers appear on the All tab (one per category) and on Creatures, split into
     * animals and the edible creatures ([CompendiumEntry.food]). Empty sections are dropped.
     */
    fun sections(
        entries: List<CompendiumEntry>,
        category: CompendiumCategory?,
        query: String,
        missingOnly: Boolean,
        photographed: Set<String>,
        masterMode: Boolean,
    ): List<CompendiumSection> {
        fun section(key: String, title: String?, all: List<CompendiumEntry>) = CompendiumSection(
            key = key,
            title = title,
            entries = all.filter { (!missingOnly || it.id !in photographed) && matches(it, query, masterMode) },
            photographed = all.count { it.id in photographed },
            total = all.size,
        )
        val categories = category?.let(::listOf) ?: CompendiumCategory.entries
        return categories.flatMap { cat ->
            val inCategory = entries.filter { it.category == cat }
            // Keys match sectionKey.
            when {
                cat == CompendiumCategory.CREATURES -> listOf(
                    section("creatures", "Creatures", inCategory.filterNot { it.food }),
                    section("food", "Creatures · Food", inCategory.filter { it.food }),
                )
                category == null -> listOf(section(cat.name.lowercase(), cat.label, inCategory))
                else -> listOf(section(cat.name.lowercase(), null, inCategory))
            }
        }.filter { it.entries.isNotEmpty() }
    }

    /** The [CompendiumSection.key] of the section [entry] is listed under on a tab with headers. */
    fun sectionKey(entry: CompendiumEntry): String = when {
        entry.category != CompendiumCategory.CREATURES -> entry.category.name.lowercase()
        entry.food -> "food"
        else -> "creatures"
    }

    /** Each category's progress over the save file's own [entries], ignoring any search or filter. */
    fun progress(entries: List<CompendiumEntry>, photographed: Set<String>): List<CategoryProgress> =
        CompendiumCategory.entries.map { cat ->
            val inCategory = entries.filter { it.category == cat }
            CategoryProgress(cat, inCategory.count { it.id in photographed }, inCategory.size)
        }
}
