package gx.zelda.compendium.model

import gx.zelda.core.SaveFile
import kotlinx.serialization.Serializable

/** The Compendium's tabs, in the game's order. */
@Serializable
enum class CompendiumCategory(val label: String) {
    CREATURES("Creatures"),
    MONSTERS("Monsters"),
    MATERIALS("Materials"),
    EQUIPMENT("Equipment"),
    TREASURE("Treasure"),
}

@Serializable
enum class EntrySource(val label: String) {
    BASE("Base game"),
    /** Added by The Champions' Ballad, which renumbers every entry after it. */
    DLC2("The Champions' Ballad"),
    /** Added by The Master Trials, but only to Master Mode files, which are numbered on their own. */
    MASTER_MODE("Master Mode"),
}

/** How Symin, at the Hateno Ancient Tech Lab, sells a missing entry's picture once "Sunshroom Sensing" is done. */
@Serializable
enum class PictureSale {
    /** 100 rupees. */
    STANDARD,
    /** An Elite Enemy Picture: 500 rupees, and only after Dark Beast Ganon is defeated. */
    ELITE,
    /** Not sold; the entry has to be photographed (or, for Sunshroom, is registered by the quest itself). */
    NONE,
}

@Serializable
data class CompendiumEntry(
    /** Shared with armor material ids where the entry is also an upgrade material (e.g. `hearty_bass`). */
    val id: String,
    /** Number on a Normal Mode file with both DLC packs; null for entries only in Master Mode. */
    val number: Int? = null,
    /** Number on a Master Mode file with both DLC packs. */
    val masterNumber: Int,
    val name: String,
    val category: CompendiumCategory,
    val source: EntrySource = EntrySource.BASE,
    /**
     * Creatures 048–083 (fish, snails, crabs, insects…), whose entries show hearts recovered and
     * cooking effects rather than materials. The tracker heads them "Food"; the game has no such header.
     */
    val food: Boolean = false,
    /** The entry's "Common Locations", as the in-game Compendium lists them. */
    val locations: List<String> = emptyList(),
    /** The entry's "Common Drops" or recoverable materials. */
    val drops: List<String> = emptyList(),
    val picture: PictureSale = PictureSale.STANDARD,
    /** Only photographable at certain times or in one place (a boss fight, a DLC trial…); [note] says when. */
    val limited: Boolean = false,
    /** When or where the entry can be photographed, or how else it's registered, where that's unusual. */
    val note: String? = null,
) {
    /** Whether a save file in this mode has the entry at all. */
    fun isIn(masterMode: Boolean): Boolean = masterMode || number != null

    /** The number shown on a file in this mode, e.g. 084; null when that mode doesn't have the entry. */
    fun numberIn(masterMode: Boolean): Int? = if (masterMode) masterNumber else number
}

@Serializable
data class CompendiumCatalog(
    /** Every entry in Master Mode's order, which is Normal Mode's order with five more entries. */
    val entries: List<CompendiumEntry>,
) {
    private val byId = entries.associateBy { it.id }

    fun entry(id: String): CompendiumEntry? = byId[id]

    /** The entries a save file has, in its own order. */
    fun entriesFor(file: SaveFile): List<CompendiumEntry> = entriesFor(file.masterMode)

    fun entriesFor(masterMode: Boolean): List<CompendiumEntry> =
        if (masterMode) entries else entries.filter { it.isIn(masterMode = false) }

    /** How many of [photographed] are entries this save file has; ids from the other mode don't count. */
    fun photographedCount(file: SaveFile, photographed: Set<String>): Int =
        entriesFor(file).count { it.id in photographed }
}

/** "084" — numbers are always shown three digits wide, as in the game. */
fun compendiumNumber(number: Int): String = number.toString().padStart(3, '0')
