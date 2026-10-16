package gx.zelda.mainquest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SheikahTower(
    val id: String,
    val name: String,
    val region: String,
    /** Where the tower stands, in one line. */
    val location: String,
    /** What's in the way of the climb and how to get past it. */
    val tip: String,
)

@Serializable
data class DivineBeast(
    val id: String,
    val name: String,
    val champion: String,
    val race: String,
    val region: String,
    val location: String,
    /** The main quest that frees it. */
    val quest: String,
    /** How the quest starts, including any quest that has to come first. */
    val start: String,
    /** What to bring or wear. */
    val prep: List<String>,
    val boss: String,
    /** The Champion's ability the beast grants, and what it does. */
    val ability: String,
    val abilityEffect: String,
    val rewards: List<String>,
)

@Serializable
data class Memory(
    /** Position in the Adventure Log's Recovered Memories, 1–18. */
    val number: Int,
    val title: String,
    val location: String,
    /** Which of the twelve pictures in the Sheikah Slate album leads here; null if it isn't one. */
    val photo: Int? = null,
    /** The last picture, which Impa shows once all twelve album memories are recovered. */
    val finalPicture: Boolean = false,
    /** The main quest that triggers it, for memories that aren't from a picture. */
    val quest: String? = null,
    val hint: String,
) {
    val id: String get() = "memory_$number"

    /** Whether "Captured Memories" asks for it: the twelve album pictures plus the final one. */
    val isCaptured: Boolean get() = photo != null || finalPicture
}

/** A Minish Cap dungeon, in the order the story sends Link through them. */
@Serializable
data class Dungeon(
    val id: String,
    /** 1–6. */
    val number: Int,
    val name: String,
    val region: String,
    /** How to reach it and get in, including what has to come first. */
    val access: String,
    /** The item found inside; null for Dark Hyrule Castle, which has none. */
    val item: String? = null,
    val boss: String,
    /** "Earth", "Fire", "Water" or "Wind"; null for the two dungeons without one. */
    val element: String? = null,
    val rewards: List<String>,
    /** What the Elemental Sanctuary then does to the sword, if this dungeon's Element changes it. */
    val sanctuary: String? = null,
    val tip: String,
)

/** How the Items tab groups the Minish Cap's items, in the order it shows them. */
@Serializable
enum class ItemCategory(val label: String) {
    @SerialName("sword") SWORD("Swords"),
    @SerialName("shield") SHIELD("Shields"),
    @SerialName("item") ITEM("Items"),
    @SerialName("equipment") EQUIPMENT("Equipment"),
    @SerialName("quest") QUEST("Quest items"),
    /** Bomb bags, quivers and wallets. Each kind's three can be found in any order. */
    @SerialName("upgrade") UPGRADE("Bag, quiver & wallet upgrades"),
    @SerialName("bottle") BOTTLE("Bottles"),
}

@Serializable
data class InventoryItem(
    val id: String,
    val name: String,
    val category: ItemCategory,
    /** Where it is, as the game's map names it. */
    val region: String,
    /** How to get it, including any cost or Kinstone Fusion. */
    val how: String,
    /** Lost for good if it isn't picked up in time; [how] says by when. */
    val missable: Boolean = false,
    /** Only after the final boss. */
    val postgame: Boolean = false,
)

/**
 * A sword technique from one of the Blade Brothers. The eight [upgrade] = false ones each come
 * with a Tiger Scroll; the three upgrades improve a technique already learned and give no scroll.
 */
@Serializable
data class TigerScroll(
    val id: String,
    val name: String,
    val teacher: String,
    /** Where his dojo is and how to get in. */
    val location: String,
    /** What he wants before he'll teach it. None of them charge Rupees. */
    val requirement: String,
    /** What the technique does, and how to perform it. */
    val effect: String,
    val upgrade: Boolean = false,
)

/**
 * The checklists, in the order the screen shows them. BotW has the first three and The Minish
 * Cap the last three; a game's screen shows only the ones its catalog has (see
 * [MainQuestCatalog.sections]).
 */
enum class MainQuestSection { TOWERS, DIVINE_BEASTS, MEMORIES, DUNGEONS, ITEMS, TIGER_SCROLLS }

/** One game's main quest checklists. Each list is empty for a game without it. */
@Serializable
data class MainQuestCatalog(
    val towers: List<SheikahTower> = emptyList(),
    val divineBeasts: List<DivineBeast> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val dungeons: List<Dungeon> = emptyList(),
    val items: List<InventoryItem> = emptyList(),
    /** Tiger Scrolls and technique upgrades together, in the order the tab lists them. */
    val tigerScrolls: List<TigerScroll> = emptyList(),
) {
    /** The checklists this game has, in screen order. */
    val sections: List<MainQuestSection> get() = MainQuestSection.entries.filter { ids(it).isNotEmpty() }

    /** The Tiger Scroll section counts the eight scrolls; the upgrades are counted apart. */
    fun ids(section: MainQuestSection): List<String> = when (section) {
        MainQuestSection.TOWERS -> towers.map { it.id }
        MainQuestSection.DIVINE_BEASTS -> divineBeasts.map { it.id }
        MainQuestSection.MEMORIES -> memories.map { it.id }
        MainQuestSection.DUNGEONS -> dungeons.map { it.id }
        MainQuestSection.ITEMS -> items.map { it.id }
        MainQuestSection.TIGER_SCROLLS -> scrolls.map { it.id }
    }

    fun total(section: MainQuestSection): Int = ids(section).size

    /** How many of [done] are items in [section]; unknown ids don't count. */
    fun doneCount(section: MainQuestSection, done: Set<String>): Int = ids(section).count { it in done }

    /** Album memories recovered, out of twelve; Impa reveals the final picture at twelve. */
    fun albumCount(done: Set<String>): Int = memories.count { it.photo != null && it.id in done }

    val albumSize: Int get() = memories.count { it.photo != null }

    /** The techniques that come with a Tiger Scroll. */
    val scrolls: List<TigerScroll> get() = tigerScrolls.filterNot { it.upgrade }

    val techniqueUpgrades: List<TigerScroll> get() = tigerScrolls.filter { it.upgrade }

    /** Technique upgrades learned, from the Tiger Scroll section's [done] ids. */
    fun upgradeCount(done: Set<String>): Int = techniqueUpgrades.count { it.id in done }

    /** Elements recovered: one per cleared dungeon that holds one. */
    fun elementCount(done: Set<String>): Int = dungeons.count { it.element != null && it.id in done }

    val elementTotal: Int get() = dungeons.count { it.element != null }

    /** [items] grouped by category, in [ItemCategory] order. */
    val itemsByCategory: List<Pair<ItemCategory, List<InventoryItem>>>
        get() = items.groupBy { it.category }.toList().sortedBy { it.first.ordinal }
}

/**
 * What one save file has done: ids of the towers activated, beasts freed and memories recovered
 * (BotW), or of the dungeons cleared, items obtained and techniques learned (The Minish Cap).
 * [tigerScrolls] holds the technique upgrades' ids too.
 */
@Serializable
data class MainQuestProgress(
    val towers: Set<String> = emptySet(),
    val divineBeasts: Set<String> = emptySet(),
    val memories: Set<String> = emptySet(),
    val dungeons: Set<String> = emptySet(),
    val items: Set<String> = emptySet(),
    val tigerScrolls: Set<String> = emptySet(),
) {
    fun done(section: MainQuestSection): Set<String> = when (section) {
        MainQuestSection.TOWERS -> towers
        MainQuestSection.DIVINE_BEASTS -> divineBeasts
        MainQuestSection.MEMORIES -> memories
        MainQuestSection.DUNGEONS -> dungeons
        MainQuestSection.ITEMS -> items
        MainQuestSection.TIGER_SCROLLS -> tigerScrolls
    }

    fun with(section: MainQuestSection, id: String, done: Boolean): MainQuestProgress {
        val ids = done(section).let { if (done) it + id else it - id }
        return when (section) {
            MainQuestSection.TOWERS -> copy(towers = ids)
            MainQuestSection.DIVINE_BEASTS -> copy(divineBeasts = ids)
            MainQuestSection.MEMORIES -> copy(memories = ids)
            MainQuestSection.DUNGEONS -> copy(dungeons = ids)
            MainQuestSection.ITEMS -> copy(items = ids)
            MainQuestSection.TIGER_SCROLLS -> copy(tigerScrolls = ids)
        }
    }
}
