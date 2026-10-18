package gx.zelda.core

/** A game shown on the home screen. Games with no trackers are listed as "coming soon". */
data class Game(
    val id: String,
    val title: String,
    /** The first release anywhere; [Games.all] is in this order. */
    val released: ReleaseDate,
    /** The platform it first came out on, e.g. "Game Boy Advance". */
    val platform: String,
    /** The save files the game keeps per user; each one is tracked separately. */
    val saveFiles: List<SaveFile>,
    val trackers: List<Tracker> = emptyList(),
) {
    val year: Int get() = released.year

    /** No trackers yet: shown as "Coming soon" and hidden until turned on in Settings. */
    val comingSoon: Boolean get() = trackers.isEmpty()

    fun saveFile(id: String?): SaveFile = saveFiles.firstOrNull { it.id == id } ?: saveFiles.first()
}

/**
 * A game's original release date. [sameDay] orders games released on the same day, lowest first
 * (Oracle of Seasons before Oracle of Ages).
 */
data class ReleaseDate(val year: Int, val month: Int, val day: Int, val sameDay: Int = 0) : Comparable<ReleaseDate> {
    override fun compareTo(other: ReleaseDate): Int = compareValuesBy(this, other, { it.year }, { it.month }, { it.day }, { it.sameDay })
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
    MAP,
    /** Pieces of Heart and Heart Containers, with a running hearts total. */
    HEART_PIECES,
    KINSTONES,
    FIGURINES,

    /**
     * A generic checklist read from a JSON file ([Tracker.dataFile]); a game can list any number.
     * The format is in docs/checklist-trackers.md.
     */
    CHECKLIST,
}

/**
 * One tracker a game offers. A [TrackerKind.CHECKLIST] names its dataset in [dataFile], relative to
 * the bundled `files/` folder (e.g. `"eow/echoes.json"`); other kinds find their data by game id.
 */
data class Tracker(val kind: TrackerKind, val title: String, val description: String, val dataFile: String? = null) {
    init {
        require((kind == TrackerKind.CHECKLIST) == (dataFile != null)) { "$title: only a checklist has a dataFile, and it needs one" }
    }
}

object Games {
    /** Famicom Disk System in Japan, a year before the NES cartridge. */
    val LOZ = comingSoon("loz", "The Legend of Zelda", ReleaseDate(1986, 2, 21), "NES")
    val AOL = comingSoon("aol", "Zelda II: The Adventure of Link", ReleaseDate(1987, 1, 14), "NES")
    val ALTTP = comingSoon("alttp", "A Link to the Past", ReleaseDate(1991, 11, 21), "SNES")
    val LA = comingSoon("la", "Link's Awakening", ReleaseDate(1993, 6, 6), "Game Boy")
    val OOT = comingSoon("oot", "Ocarina of Time", ReleaseDate(1998, 11, 21), "Nintendo 64")

    /**
     * Two files on the Nintendo 64. An owl save is a temporary quit-save on one of them, not a third
     * file. Every tracker is a checklist (docs/checklist-trackers.md); the data is written up in docs/mm.md.
     */
    val MM = Game(
        id = "mm",
        title = "Majora's Mask",
        released = ReleaseDate(2000, 4, 27),
        platform = "Nintendo 64",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(TrackerKind.CHECKLIST, "Main Quest", "The four temples, their bosses and Remains, and Majora", dataFile = "mm/main_quest.json"),
            Tracker(TrackerKind.CHECKLIST, "Masks", "All 24 masks, with how and when to get each", dataFile = "mm/masks.json"),
            Tracker(TrackerKind.CHECKLIST, "Heart Pieces", "All 52 Pieces of Heart and 4 Heart Containers, with your hearts total", dataFile = "mm/heart_pieces.json"),
            Tracker(TrackerKind.CHECKLIST, "Bombers' Notebook", "The notebook's 20 people: their schedules and rewards", dataFile = "mm/bombers_notebook.json"),
            Tracker(TrackerKind.CHECKLIST, "Stray Fairies", "Clock Town's fairy, the 15 in each temple and the Great Fairies' rewards", dataFile = "mm/stray_fairies.json"),
            Tracker(TrackerKind.CHECKLIST, "Spider Houses", "The 30 Gold Skulltulas in each of the Swamp and Oceanside Spider Houses", dataFile = "mm/spider_houses.json"),
            Tracker(TrackerKind.CHECKLIST, "Songs", "Every song and where it's learned", dataFile = "mm/songs.json"),
            Tracker(TrackerKind.CHECKLIST, "Items & Equipment", "Items, swords, wallets, quivers, bomb bags, magic and the six Bottles", dataFile = "mm/items.json"),
            Tracker(TrackerKind.CHECKLIST, "Owls, Maps & Bank", "Owl Statues, Tingle's maps and the bank's rewards", dataFile = "mm/world.json"),
        ),
    )

    /** Released together in Japan; Seasons is the one usually played first. */
    val OOS = comingSoon("oos", "Oracle of Seasons", ReleaseDate(2001, 2, 27, sameDay = 0), "Game Boy Color")
    val OOA = comingSoon("ooa", "Oracle of Ages", ReleaseDate(2001, 2, 27, sameDay = 1), "Game Boy Color")

    /**
     * The GBA multiplayer game bundled with A Link to the Past (North America first). Counted as
     * mainline: its own story (Vaati's first appearance) placed in Nintendo's official timeline,
     * and later sold on its own as the Anniversary Edition.
     */
    val FS = comingSoon("fs", "Four Swords", ReleaseDate(2002, 12, 2), "Game Boy Advance")
    val WW = comingSoon("ww", "The Wind Waker", ReleaseDate(2002, 12, 13), "GameCube")
    val FSA = comingSoon("fsa", "Four Swords Adventures", ReleaseDate(2004, 3, 18), "GameCube")
    val TP = comingSoon("tp", "Twilight Princess", ReleaseDate(2006, 11, 19), "Wii & GameCube")

    /** The DS games keep two files. */
    val PH = comingSoon("ph", "Phantom Hourglass", ReleaseDate(2007, 6, 23), "Nintendo DS", files = 2)
    val ST = comingSoon("st", "Spirit Tracks", ReleaseDate(2009, 12, 7), "Nintendo DS", files = 2)
    val SS = comingSoon("ss", "Skyward Sword", ReleaseDate(2011, 11, 18), "Wii")
    val ALBW = comingSoon("albw", "A Link Between Worlds", ReleaseDate(2013, 11, 22), "Nintendo 3DS")
    val TFH = comingSoon("tfh", "Tri Force Heroes", ReleaseDate(2015, 10, 22), "Nintendo 3DS")

    /**
     * Two save files per Switch user, each with its own autosaves (Zelda Wiki, Saving; Game8). Every
     * tracker is a checklist (docs/checklist-trackers.md); the data and its sources are in docs/eow.md.
     */
    val EOW = Game(
        id = "eow",
        title = "Echoes of Wisdom",
        released = ReleaseDate(2024, 9, 26),
        platform = "Nintendo Switch",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            eowChecklist("Main Quest", "Rifts, dungeons and bosses, Tri's Power and the Swordfighter weapons", "main_quest"),
            eowChecklist("Echoes", "All 127 echoes: where each is first learned and what it costs", "echoes"),
            eowChecklist("Heart Pieces", "All 40 Pieces of Heart and 7 Heart Containers by region, with your hearts total", "heart_pieces"),
            eowChecklist("Might Crystals", "All 150 crystals in 85 places, and Lueburry's upgrades", "might_crystals"),
            eowChecklist("Side Quests", "All 50 side quests: who gives them, where, and the reward", "side_quests"),
            eowChecklist("Accessories", "All 28 accessories and the Great Fairy's accessory slots", "accessories"),
            eowChecklist("Smoothie Recipes", "All 59 smoothies and 10 potions, with ingredients and effects", "smoothies"),
            eowChecklist("Slumber Dojo", "The 15 battle challenges, their hidden goal times and Shisho's rewards", "slumber_dojo"),
            eowChecklist("Stamp Stands", "All 25 Stamp Stands and the stamp card rewards", "stamp_stands"),
            eowChecklist("Bottles, Automatons & More", "Fairy Bottles, Dampé's Automatons, outfits and mini-games", "collectibles"),
        ),
    )

    /** Switch 2 Edition: two Normal Mode files, plus Master Mode's own separate file. */
    val BOTW = Game(
        id = "botw",
        title = "Breath of the Wild",
        released = ReleaseDate(2017, 3, 3),
        platform = "Wii U & Switch",
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
            Tracker(TrackerKind.MAP, "Map", "Shrines, Korok Seeds, Sheikah Towers and overworld bosses on the map of Hyrule"),
        ),
    )

    /** Switch 2 Edition: two save files, no Master Mode. */
    val TOTK = Game(
        id = "totk",
        title = "Tears of the Kingdom",
        released = ReleaseDate(2023, 5, 12),
        platform = "Nintendo Switch",
        saveFiles = listOf(SaveFile("file_1", "Save File 1"), SaveFile("file_2", "Save File 2")),
        trackers = listOf(
            Tracker(TrackerKind.ARMOR, "Armor Progress", "Collected armor, upgrade levels, set bonuses and materials"),
            Tracker(TrackerKind.FAIRIES, "Great Fairy Fountains", "Where to find each Great Fairy and how to free her"),
            Tracker(TrackerKind.SHRINES, "Shrines & Lightroots", "All 152 shrines, the 120 Lightroots beneath them and a Light of Blessing planner"),
        ),
    )

    /** Three save files, as on the GBA cartridge. */
    val MC = Game(
        id = "mc",
        title = "The Minish Cap",
        released = ReleaseDate(2004, 11, 4),
        platform = "Game Boy Advance",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(TrackerKind.HEART_PIECES, "Heart Pieces", "All 44 Pieces of Heart and 6 Heart Containers by region, with your hearts total"),
            Tracker(TrackerKind.KINSTONES, "Kinstone Fusions", "All 100 fusions by region: who to fuse with, which piece, and what each one opens"),
            Tracker(TrackerKind.MAIN_QUEST, "Main Quest & Items", "Dungeons and Elements, every item and upgrade, and the Tiger Scrolls"),
            Tracker(TrackerKind.FIGURINES, "Carlov's Figurines", "All 136 figurines by story checkpoint, Mysterious Shells and the odds of a new one"),
        ),
    )

    /** Every game, oldest first: a new game slots in by its release date wherever it's listed here. */
    val all: List<Game> = listOf(
        BOTW, TOTK, MC,
        LOZ, AOL, ALTTP, LA, OOT, MM, OOS, OOA, FS, WW, FSA, TP, PH, ST, SS, ALBW, TFH, EOW,
    ).sortedBy { it.released }

    fun byId(id: String): Game = all.first { it.id == id }

    private fun eowChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "eow/$file.json")

    /** A game with no trackers yet; [files] placeholder save files, as many as the game itself keeps. */
    private fun comingSoon(id: String, title: String, released: ReleaseDate, platform: String, files: Int = 3) = Game(
        id = id,
        title = title,
        released = released,
        platform = platform,
        saveFiles = (1..files).map { SaveFile("file_$it", "File $it") },
    )
}
