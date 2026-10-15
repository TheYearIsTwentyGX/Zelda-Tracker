package gx.zelda.core

/** A game shown on the home screen. Games with no trackers are listed as "coming soon". */
data class Game(
    val id: String,
    val title: String,
    val year: Int,
    /** The save files the game keeps per user; each one is tracked separately. */
    val saveFiles: List<SaveFile>,
    val trackers: List<Tracker> = emptyList(),
) {
    fun saveFile(id: String?): SaveFile = saveFiles.firstOrNull { it.id == id } ?: saveFiles.first()
}

data class SaveFile(
    val id: String,
    val label: String,
    /** A Master Mode file, which some trackers show differently (e.g. extra Compendium entries). */
    val masterMode: Boolean = false,
)

/** The kinds of tracker a game can offer; each kind has one set of screens shared by every game. */
enum class TrackerKind {
    ARMOR,
    FAIRIES,
    MAIN_QUEST,
    SHRINES,
    COMPENDIUM,
    KOROKS,
    /** Every other tracker's progress on one screen; see `overview/OverviewProviders.kt`. */
    OVERVIEW,
    SIDE_QUESTS,
    BOSSES,
}

data class Tracker(val kind: TrackerKind, val title: String, val description: String)

object Games {
    /** Switch 2 Edition: two Normal Mode files, plus Master Mode's own separate file. */
    val BOTW = Game(
        id = "botw",
        title = "Breath of the Wild",
        year = 2017,
        saveFiles = listOf(
            SaveFile("normal_1", "Save File 1"),
            SaveFile("normal_2", "Save File 2"),
            SaveFile("master", "Master Mode", masterMode = true),
        ),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(TrackerKind.ARMOR, "Armor Progress", "Collected armor, upgrade levels, set bonuses and materials"),
            Tracker(TrackerKind.FAIRIES, "Great Fairy Fountains", "Where to find each Great Fairy and what opening her fountain costs"),
            Tracker(TrackerKind.MAIN_QUEST, "Main Quest Progress", "Sheikah Towers, Divine Beasts and Recovered Memories"),
            Tracker(TrackerKind.SHRINES, "Shrines", "Shrines by region, Shrine Quests and a Spirit Orb planner"),
            Tracker(TrackerKind.COMPENDIUM, "Hyrule Compendium", "Every entry photographed, by category, with where to find what's missing"),
            Tracker(TrackerKind.KOROKS, "Korok Seeds", "All 900 seeds by region and puzzle type, and Hestu's upgrades"),
            Tracker(TrackerKind.SIDE_QUESTS, "Side Quests", "Side Quests, Shrine Quests and DLC quests by region, with givers and rewards"),
            Tracker(TrackerKind.BOSSES, "Overworld Bosses", "Every Hinox, Talus, Molduga and Lynel by region, and Kilton's Medals of Honor"),
        ),
    )

    /** Switch 2 Edition: two save files, no Master Mode. */
    val TOTK = Game(
        id = "totk",
        title = "Tears of the Kingdom",
        year = 2023,
        saveFiles = listOf(SaveFile("file_1", "Save File 1"), SaveFile("file_2", "Save File 2")),
        trackers = listOf(
            Tracker(TrackerKind.ARMOR, "Armor Progress", "Collected armor, upgrade levels, set bonuses and materials"),
            Tracker(TrackerKind.FAIRIES, "Great Fairy Fountains", "Where to find each Great Fairy and how to free her"),
            Tracker(TrackerKind.SHRINES, "Shrines & Lightroots", "All 152 shrines, the 120 Lightroots beneath them and a Light of Blessing planner"),
        ),
    )

    val all = listOf(BOTW, TOTK)

    fun byId(id: String): Game = all.first { it.id == id }
}
