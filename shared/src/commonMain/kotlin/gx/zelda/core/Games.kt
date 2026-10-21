package gx.zelda.core

/** A game shown on the home screen. Games with no trackers are listed as "coming soon". */
data class Game(
    val id: String,
    val title: String,
    /** The first release anywhere; [Games.all] is in this order, mainline games before spin-offs. */
    val released: ReleaseDate,
    /** The platform it first came out on, e.g. "Game Boy Advance". */
    val platform: String,
    /** The save files the game keeps per user; each one is tracked separately. */
    val saveFiles: List<SaveFile>,
    val trackers: List<Tracker> = emptyList(),
    /**
     * Not a mainline Zelda game (Hyrule Warriors, say). Spin-offs are listed after every mainline
     * game, under their own "Spin-offs" heading on the home screen, in the menu and in Settings.
     */
    val spinOff: Boolean = false,
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

    /**
     * One of Hyrule Warriors' Adventure Mode maps, drawn as the in-game map with its squares to tap
     * and track; [Tracker.dataFile] names the map (e.g. `"hwde/adventure_map.json"`). The format is
     * in docs/hwde.md.
     */
    ADVENTURE_MAP,

    /**
     * Skyward Sword's Thrill Digger solver: a tool, not a tracker. It keeps no progress, so it has no
     * Progress Overview row and no mark-all, and the menu and home screen list it under "Tools".
     */
    THRILL_DIGGER,

    /**
     * Skyward Sword's item upgrades (Gondo's Scrap Shop and Bertie's potion infusions), modelled on
     * the armor tracker: each item's level, the materials and Rupees for every upgrade left, and the
     * totals. Its data is `files/<game id>/upgrades.json`; see docs/ss.md.
     */
    ITEM_UPGRADES,
    ;

    /** True for the kinds that read their data from a [Tracker.dataFile], so a game can list several. */
    val usesDataFile: Boolean get() = this == CHECKLIST || this == ADVENTURE_MAP

    /** A helper for playing rather than a record of progress: listed after the trackers, under "Tools". */
    val isTool: Boolean get() = this == THRILL_DIGGER
}

/**
 * One tracker a game offers. A [TrackerKind.CHECKLIST] or [TrackerKind.ADVENTURE_MAP] names its
 * dataset in [dataFile], relative to the bundled `files/` folder (e.g. `"eow/echoes.json"`); other
 * kinds find their data by game id.
 */
data class Tracker(val kind: TrackerKind, val title: String, val description: String, val dataFile: String? = null) {
    init {
        require(kind.usesDataFile == (dataFile != null)) { "$title: only a checklist or an Adventure map has a dataFile, and it needs one" }
    }

    val isTool: Boolean get() = kind.isTool
}

object Games {
    /**
     * Famicom Disk System in Japan, a year before the NES cartridge. Three save files; the Second
     * Quest carries on in the same file, so every checklist has a section per quest. The data and
     * its sources are in docs/loz.md.
     */
    val LOZ = Game(
        id = "loz",
        title = "The Legend of Zelda",
        released = ReleaseDate(1986, 2, 21),
        platform = "NES",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            lozChecklist("Dungeons", "Levels 1–9 of both quests: entrances, bosses, items and Triforce pieces", "dungeons"),
            lozChecklist("Items & Equipment", "All 27 items in each quest, with where to find or buy them", "items"),
            lozChecklist("Heart Containers", "All 13 in each quest, from bosses and secret caves, with your hearts", "heart_containers"),
        ),
    )

    /**
     * Three save files. Replaying a beaten file keeps the levels and spells but not the items or
     * containers; that isn't tracked separately. The data and its sources are in docs/aol.md.
     */
    val AOL = Game(
        id = "aol",
        title = "Zelda II: The Adventure of Link",
        released = ReleaseDate(1987, 1, 14),
        platform = "NES",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            aolChecklist("Palaces", "The six palaces and the Great Palace: crystals, items and all 8 bosses", "palaces"),
            aolChecklist("Items", "The 14 items: palace treasures, the Hammer and Magic Key, and the townsfolk's quest items", "items"),
            aolChecklist("Spells & Techniques", "The 8 spells and the towns that teach them, and the 2 sword thrusts", "spells"),
            aolChecklist("Heart & Magic Containers", "All 4 of each, with your life and magic meters", "containers"),
            aolChecklist("Levels & Link Dolls", "Attack, Magic and Life to level 8, and the 6 Link Dolls", "levels"),
        ),
    )

    /**
     * Three files on the SNES cartridge (StrategyWiki: "top, middle, or bottom"). Tracked as the
     * original SNES release; GBA differences are in item notes. Every tracker is a checklist; the data
     * and its sources are in docs/alttp.md.
     */

    /**
     * Three files on the SNES cartridge (StrategyWiki: "top, middle, or bottom"). Tracked as the
     * original SNES release; GBA differences are in item notes. Every tracker is a checklist; the data
     * and its sources are in docs/alttp.md.
     */
    val ALTTP = Game(
        id = "alttp",
        title = "A Link to the Past",
        released = ReleaseDate(1991, 11, 21),
        platform = "SNES",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            alttpChecklist("Main Quest", "The 3 Pendants and 7 Crystals: every dungeon and boss, Agahnim and Ganon", "main_quest"),
            alttpChecklist("Items & Equipment", "Every item and its upgrades, the 4 Bottles, magic and bomb and arrow capacity", "items"),
            alttpChecklist("Heart Pieces", "All 24 Pieces of Heart and 11 Heart Containers, with your hearts total", "heart_pieces"),
        ),
    )

    /**
     * Three files on the Game Boy cartridge, as on DX (the disassembly's SaveGame1–3) and the Switch
     * remake. Every tracker is a checklist for the original and DX; the Switch remake's own lists are
     * `separate` tabs. The data and its sources are in docs/la.md.
     */
    val LA = Game(
        id = "la",
        title = "Link's Awakening",
        released = ReleaseDate(1993, 6, 6),
        platform = "Game Boy",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            laChecklist("Main Quest", "The eight dungeons, their keys and Instruments of the Sirens, and the Wind Fish's Egg", "main_quest"),
            laChecklist("Items & Equipment", "Every item and its upgrades, the Mad Batters' capacity upgrades and the five Golden Leaves", "items"),
            laChecklist("Heart Pieces", "All 12 Pieces of Heart and 8 Heart Containers, with your hearts, and the Switch remake's 32", "heart_pieces"),
            laChecklist("Secret Seashells", "All 26 seashells and the Seashell Mansion, and the Switch remake's 50", "seashells"),
            laChecklist("Trading Sequence", "From the Yoshi Doll to the Magnifying Lens: 14 trades", "trading"),
            laChecklist("Songs", "The three Ocarina songs and where to learn them", "songs"),
            laChecklist("Photographs", "DX: all 12 photos for The Travels of Link, and the ones you can miss", "photographs"),
            laChecklist("Color Dungeon", "DX and Switch: the graveyard puzzle, the bosses and the Fairy's tunics", "color_dungeon"),
        ),
    )

    /** Three files on the Nintendo 64 cartridge (each with a backup copy), as on the 3DS. */
    val OOT = Game(
        id = "oot",
        title = "Ocarina of Time",
        released = ReleaseDate(1998, 11, 21),
        platform = "Nintendo 64",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(TrackerKind.CHECKLIST, "Main Quest", "Dungeons in order, the Spiritual Stones and the Medallions", dataFile = "oot/main_quest.json"),
            Tracker(TrackerKind.CHECKLIST, "Items & Equipment", "Every item, sword, shield, tunic and boots, and each upgrade", dataFile = "oot/items.json"),
            Tracker(TrackerKind.CHECKLIST, "Heart Pieces", "All 36 Pieces of Heart and 8 Heart Containers, with your hearts", dataFile = "oot/heart_pieces.json"),
            Tracker(TrackerKind.CHECKLIST, "Gold Skulltulas", "All 100 by area, with age, time and items needed, and the rewards", dataFile = "oot/gold_skulltulas.json"),
            Tracker(TrackerKind.CHECKLIST, "Songs", "All 12 songs: where to learn each one and its notes", dataFile = "oot/songs.json"),
            Tracker(TrackerKind.CHECKLIST, "Great Fairies", "The six fountains: spells, magic and defense", dataFile = "oot/great_fairies.json"),
            Tracker(TrackerKind.CHECKLIST, "Bottles", "All four bottles and how to win them", dataFile = "oot/bottles.json"),
            Tracker(TrackerKind.CHECKLIST, "Big Poes", "All 10 in Hyrule Field, for the Poe Collector's bottle", dataFile = "oot/big_poes.json"),
            Tracker(TrackerKind.CHECKLIST, "Trading Sequences", "The mask quest and the trade for Biggoron's Sword", dataFile = "oot/trading.json"),
        ),
    )

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

    /**
     * Released together with Ages in Japan; Seasons is the one usually played first. Three save files
     * (Zelda Wiki, Saving). Every tracker is a checklist; linked-game content is tagged "Linked". The
     * data is written up in docs/oos.md.
     */
    val OOS = Game(
        id = "oos",
        title = "Oracle of Seasons",
        released = ReleaseDate(2001, 2, 27, sameDay = 0),
        platform = "Game Boy Color",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(TrackerKind.CHECKLIST, "Main Quest", "The eight dungeons and their Essences of Nature, Onox, and the linked finale", dataFile = "oos/main_quest.json"),
            Tracker(TrackerKind.CHECKLIST, "Items & Equipment", "Every item and seed, the upgrades as tiers, and the Quest Items", dataFile = "oos/items.json"),
            Tracker(TrackerKind.CHECKLIST, "Rod of Seasons", "The Rod and the four seasons from the Temple of Seasons' towers", dataFile = "oos/seasons.json"),
            Tracker(TrackerKind.CHECKLIST, "Heart Pieces", "All 12 Pieces of Heart and 9 Heart Containers, with your hearts", dataFile = "oos/heart_pieces.json"),
            Tracker(TrackerKind.CHECKLIST, "Rings", "All 64 rings and the Ring Box, with where to get each in Seasons", dataFile = "oos/rings.json"),
            Tracker(TrackerKind.CHECKLIST, "Gasha Spots", "The 16 soft-soil spots for Gasha Seeds, and the ring classes each one gives", dataFile = "oos/gasha.json"),
            Tracker(TrackerKind.CHECKLIST, "Trading Sequence", "From the Cuccodex to the Noble Sword", dataFile = "oos/trading.json"),
            Tracker(TrackerKind.CHECKLIST, "Animal Companions", "Ricky, Dimitri and Moosh, the flute and Natzu", dataFile = "oos/animal_companions.json"),
            Tracker(TrackerKind.CHECKLIST, "Linked Secrets", "The passwords that link to Ages, and the 20 secrets to tell and take back", dataFile = "oos/linked_secrets.json"),
            Tracker(TrackerKind.CHECKLIST, "Subrosia", "The Subrosian story, the Market, ores and the Smithy, and pastimes", dataFile = "oos/subrosia.json"),
        ),
    )

    /**
     * Released the same day as Seasons. Three save files (Zelda Wiki, Saving). Every tracker is a
     * checklist; linked-game content is tagged "Linked", and the ring ids match Seasons'. The data is
     * written up in docs/ooa.md.
     */
    val OOA = Game(
        id = "ooa",
        title = "Oracle of Ages",
        released = ReleaseDate(2001, 2, 27, sameDay = 1),
        platform = "Game Boy Color",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(TrackerKind.CHECKLIST, "Main Quest", "The eight dungeons and their Essences of Time, Veran, and the linked finale", dataFile = "ooa/main_quest.json"),
            Tracker(TrackerKind.CHECKLIST, "Items & Equipment", "Every item and seed, the upgrades as tiers, and the Quest Items", dataFile = "ooa/items.json"),
            Tracker(TrackerKind.CHECKLIST, "Harp of Ages", "The Harp and its three tunes: Echoes, Currents and Ages", dataFile = "ooa/harp_songs.json"),
            Tracker(TrackerKind.CHECKLIST, "Heart Pieces", "All 12 Pieces of Heart and 9 Heart Containers, with your hearts", dataFile = "ooa/heart_pieces.json"),
            Tracker(TrackerKind.CHECKLIST, "Rings", "All 64 rings and the Ring Box, with where to get each in Ages", dataFile = "ooa/rings.json"),
            Tracker(TrackerKind.CHECKLIST, "Gasha Spots", "The 16 soft-soil spots for Gasha Seeds, and the ring classes each one gives", dataFile = "ooa/gasha.json"),
            Tracker(TrackerKind.CHECKLIST, "Trading Sequence", "From the Poe Clock to the Noble Sword", dataFile = "ooa/trading.json"),
            Tracker(TrackerKind.CHECKLIST, "Animal Companions", "Ricky, Dimitri and Moosh, the flute and the Nuun Highlands", dataFile = "ooa/animal_companions.json"),
            Tracker(TrackerKind.CHECKLIST, "Linked Secrets", "The passwords that link to Seasons, and the 20 secrets to tell and take back", dataFile = "ooa/linked_secrets.json"),
        ),
    )

    /**
     * The GBA multiplayer game bundled with A Link to the Past (North America first). Counted as
     * mainline: its own story (Vaati's first appearance) placed in Nintendo's official timeline,
     * and later sold on its own as the Anniversary Edition.
     *
     * Three files: on the GBA they're A Link to the Past's, which hold both games' progress; the
     * Anniversary Edition (DSiWare, 2011) has three of its own. Its one checklist has the Anniversary
     * Edition's and the GBA's own content as separate sections; the data and its sources are in docs/fs.md.
     */
    val FS = Game(
        id = "fs",
        title = "Four Swords",
        released = ReleaseDate(2002, 12, 2),
        platform = "Game Boy Advance",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(
                TrackerKind.CHECKLIST,
                "Stages & Keys",
                "The 9 Great Fairy keys and Vaati's Palace's 3 doors, the Anniversary Edition's 18 levels and the GBA's ALttP link-ups",
                dataFile = "fs/stages.json",
            ),
        ),
    )

    /**
     * Three files on the GameCube memory card (zeldaret/tww: `card_gamedata gamedata[3]`). Tracked as the
     * GameCube original; Wind Waker HD differences are in item notes. Every tracker is a checklist; the
     * data and its sources are in docs/ww.md.
     */
    val WW = Game(
        id = "ww",
        title = "The Wind Waker",
        released = ReleaseDate(2002, 12, 13),
        platform = "GameCube",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            wwChecklist("Main Quest", "Dungeons and bosses, the three Pearls, the Master Sword and the 8 Triforce Shards", "main_quest"),
            wwChecklist("Items & Equipment", "Every item and upgrade: wallets, quivers, bomb bags, magic, the Hero's Charm and 4 Bottles", "items"),
            wwChecklist("Heart Pieces", "All 44 Pieces of Heart and 6 Heart Containers, with your hearts total", "heart_pieces"),
            wwChecklist("Charts", "All 41 Treasure Charts and their treasure, the 8 Triforce Charts and 12 Special Charts", "charts"),
            wwChecklist("Sea Chart", "The 49 squares of the Great Sea, charted by feeding each fishman", "sea_chart"),
            wwChecklist("Sea Secrets", "The 20 Secret Caves, 7 Submarines, lookout platforms and 6 Big Octos", "sea_secrets"),
            wwChecklist("Nintendo Gallery", "All 134 figurines by room, with the missable ones flagged", "figurines"),
            wwChecklist("Songs", "The six Wind Waker songs and where each is learned", "songs"),
            wwChecklist("Great Fairies", "The seven Great Fairies' upgrades and the Queen of Fairies", "great_fairies"),
            wwChecklist("Side Quests", "Joy Pendants, Mail Sorting, Lenzo, the Withered Trees and Tingle Statues", "side_quests"),
        ),
    )

    /**
     * Three files on the memory card ("You can store up to three saved-data files", the manual's
     * Select Game screen). Tracked as the Hyrulean Adventure; Navi Trackers, the Japan-only mode,
     * isn't. Every tracker is a checklist; the data and its sources are in docs/fsa.md.
     */
    val FSA = Game(
        id = "fsa",
        title = "Four Swords Adventures",
        released = ReleaseDate(2004, 3, 18),
        platform = "GameCube",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            Tracker(
                TrackerKind.CHECKLIST,
                "Hyrulean Adventure",
                "All 24 stages in 8 levels with their bosses, the 6 Maidens and Zelda, and the 4 Royal Jewels",
                dataFile = "fsa/hyrulean_adventure.json",
            ),
            Tracker(
                TrackerKind.CHECKLIST,
                "Tingle's Tower & Shadow Battle",
                "Multiplayer: the 8 Tingle's Tower mini-games and the Dark Shadow Battle arenas",
                dataFile = "fsa/tingle_and_shadow_battle.json",
            ),
        ),
    )

    /**
     * Three files on the GameCube, the Wii and HD (Zelda Wiki, Saving). Tracked as the GameCube
     * original; the Wii version is mirrored but has the same collectibles, and HD's differences are
     * item notes or `separate` tabs. Every tracker is a checklist; the data and its sources are in docs/tp.md.
     */
    val TP = Game(
        id = "tp",
        title = "Twilight Princess",
        released = ReleaseDate(2006, 11, 19),
        platform = "Wii & GameCube",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            tpChecklist("Main Quest", "The nine dungeons and their bosses, the Tears of Light, the Fused Shadows and Mirror Shards", "main_quest"),
            tpChecklist("Items & Equipment", "Every item, sword, shield and tunic, the wallets, quivers and bomb bags, and the 4 Bottles", "items"),
            tpChecklist("Heart Pieces", "All 45 Pieces of Heart and 8 Heart Containers, with your hearts total", "heart_pieces"),
            tpChecklist("Poe Souls", "All 60 Poes by region, and Jovani's rewards", "poe_souls"),
            tpChecklist("Golden Bugs", "All 24 Golden Bugs for Agitha, and her rewards", "golden_bugs"),
            tpChecklist("Hidden Skills", "The 7 Hidden Skills and the Howling Stones that call the Golden Wolf", "hidden_skills"),
            tpChecklist("Cave of Ordeals", "All 50 floors and the Great Fairy's rewards, and HD's Cave of Shadows", "cave_of_ordeals"),
            tpChecklist("Sidequests & Collections", "Sidequests, the Postman's letters, the Fishing Journal and HD's 50 stamps", "sidequests"),
        ),
    )

    /**
     * Two files ("You can create up to two different files", Nintendo's manual), the placeholder's
     * ids. Every tracker is a checklist; the data and its sources are in docs/ph.md.
     */
    val PH = Game(
        id = "ph",
        title = "Phantom Hourglass",
        released = ReleaseDate(2007, 6, 23),
        platform = "Nintendo DS",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            phChecklist("Main Quest", "The temples and the Ghost Ship, the Temple of the Ocean King's floors, the Spirits and the Pure Metals", "main_quest"),
            phChecklist("Items & Equipment", "Every item, the Bomb Bag, Quiver and Bombchu Bag upgrades, and the Phantom Hourglass's 25 minutes", "items"),
            phChecklist("Heart Containers", "All 13: seven bosses and six from shops, mini-games and fishing, with your hearts", "heart_containers"),
            phChecklist("Spirit Gems", "All 20 Power, Wisdom and Courage Gems, and the spirits' upgrades at 10 and 20", "spirit_gems"),
            phChecklist("Treasure Maps", "All 31 maps and their Sunken Treasures, by the sea each map is found in", "treasure_maps"),
            phChecklist("Ship Parts & Treasure", "The eight sets of eight parts, Golden included, and the eight kinds of Treasure", "ship_parts"),
            phChecklist("Sea Chart & Islands", "Every island, hidden ones included, and the six Golden Frogs' warps", "sea_chart"),
            phChecklist("Sidequests", "Fishing, the trading quest, the mini-games' prizes and Beedle's cards", "sidequests"),
        ),
    )

    /**
     * Two files ("a maximum of two save files at any time", Nintendo's manual), the placeholder's
     * ids. Every tracker is a checklist; the data and its sources are in docs/st.md.
     */
    val ST = Game(
        id = "st",
        title = "Spirit Tracks",
        released = ReleaseDate(2009, 12, 7),
        platform = "Nintendo DS",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            stChecklist("Main Quest", "Five temples, six climbs of the Tower of Spirits, the Rail Maps and the Lokomo duets", "main_quest"),
            stChecklist("Items & Equipment", "Every item, the Bomb Bag and Quiver upgrades and both Swordsman's Scrolls", "items"),
            stChecklist("Heart Containers", "All 13: five bosses and eight side activities, with your hearts", "heart_containers"),
            stChecklist("Force Gems", "All 20 Force Gems: who gives each and the tracks it opens", "force_gems"),
            stChecklist("Passengers & Cargo", "Every passenger and cargo run, and what it pays", "deliveries"),
            stChecklist("Rabbits", "All 50 rabbits by realm, and Bunnio's prizes", "rabbits"),
            stChecklist("Stamp Stations", "All 20 stamps and Niko's rewards", "stamps"),
            stChecklist("Songs", "The five Spirit Flute songs", "songs"),
            stChecklist("Stations & Warp Gates", "All 22 stations and the 8 Warp Gates to open", "stations"),
            stChecklist("Train Parts", "All 32 train parts in 8 sets, and the Treasure each costs", "train_parts"),
            stChecklist("Minigames & Secrets", "Each minigame's and hidden station's lasting prizes", "minigames"),
        ),
    )

    /**
     * Three files on the Wii and in Skyward Sword HD, whose extra autosave slot isn't a file of its own.
     * Every tracker but Item Upgrades is a checklist for the Wii original, with HD's differences in
     * item notes, plus the Thrill Digger solver as a tool. The data and its sources are in docs/ss.md.
     */
    val SS = Game(
        id = "ss",
        title = "Skyward Sword",
        released = ReleaseDate(2011, 11, 18),
        platform = "Wii",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            ssChecklist("Main Quest", "The seven dungeons, the Sacred Flames, the Silent Realms, the Song of the Hero and the Goddess Sword", "main_quest"),
            ssChecklist("Items & Equipment", "Every item and its upgrades with their materials, the Adventure Pouch, Bottles and wallets", "items"),
            ssChecklist("Heart Pieces", "All 24 Pieces of Heart and 6 Heart Containers, with your hearts total", "heart_pieces"),
            ssChecklist("Goddess Cubes", "All 27 Goddess Cubes and the Goddess Chests they open in the sky", "goddess_cubes"),
            ssChecklist("Gratitude Crystals", "All 80 crystals: the side quests that give them, the 15 loose ones and Batreaux's rewards", "gratitude_crystals"),
            ssChecklist("Side Quests & Minigames", "The minigames and their prizes, the other side quests and the Lightning Round", "side_quests"),
            ssChecklist("Shields", "All 10 shields, their upgrades and materials", "shields"),
            ssChecklist("Bugs & Treasures", "All 12 bugs and 16 treasures: where to find them and what they upgrade", "bugs_treasures"),
            Tracker(TrackerKind.ITEM_UPGRADES, "Item Upgrades", "Every Scrap Shop upgrade and potion infusion, with the treasures, bugs and Rupees still needed"),
            Tracker(TrackerKind.THRILL_DIGGER, "Thrill Digger Solver", "Every undug hole's Bomb and Rupoor odds, and the safest hole to dig next"),
        ),
    )

    /**
     * Three files (StrategyWiki, Gameplay). Every tracker is a checklist; the data and its sources
     * are in docs/albw.md.
     */
    val ALBW = Game(
        id = "albw",
        title = "A Link Between Worlds",
        released = ReleaseDate(2013, 11, 22),
        platform = "Nintendo 3DS",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2"), SaveFile("file_3", "File 3")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the active save file"),
            albwChecklist("Main Quest", "The three Pendants and Yuga, then the seven Sages' dungeons, Lorule Castle and Yuga Ganon", "main_quest"),
            albwChecklist("Items & Equipment", "Ravio's nine items rented, bought and made Nice, your gear, the Master Ore and the five Bottles", "items"),
            albwChecklist("Lost Maiamai", "All 100, 50 in each world, by Maiamai Map area, and Mother Maiamai's rewards", "maiamais"),
            albwChecklist("Heart Pieces", "All 28 Pieces of Heart and 10 Heart Containers, with your hearts", "heart_pieces"),
            albwChecklist("Mini-games", "Every prize game worth winning once and the Treacherous Tower's courses", "minigames"),
        ),
    )

    /**
     * One save file, the only mainline game without a choice of files (Zelda Wiki, Saving; the
     * Japanese site's "Save data files: 1"). Every tracker is a checklist; the data and its sources
     * are in docs/tfh.md.
     */
    val TFH = Game(
        id = "tfh",
        title = "Tri Force Heroes",
        released = ReleaseDate(2015, 10, 22),
        platform = "Nintendo 3DS",
        saveFiles = listOf(SaveFile("file_1", "Save File")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every tracker at a glance for the save file"),
            tfhChecklist("Adventure", "All 32 Drablands levels, their 96 challenges and King Tuft's rewards", "adventure"),
            tfhChecklist("Outfits", "All 38 outfits: what each does, and Madame Couture's materials and price", "outfits"),
            tfhChecklist("Den of Trials", "The eight zones of the 40-floor Den, and the Fierce Deity Armor", "den_of_trials"),
        ),
    )

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

    /**
     * A spin-off, tracked as the Definitive Edition: its Adventure Mode maps, squares and rewards
     * are DE's own (they differ from the Wii U and 3DS releases'), so it's dated by DE's first
     * release, in Japan, rather than Hyrule Warriors' 2014 one. One save per Switch user: no file
     * select, and the save is a single file. Each Adventure Mode map is its own tracker; the data
     * and its sources are in docs/hwde.md.
     */
    val HWDE = Game(
        id = "hwde",
        title = "Hyrule Warriors: Definitive Edition",
        released = ReleaseDate(2018, 3, 22),
        platform = "Nintendo Switch",
        saveFiles = listOf(SaveFile("file_1", "Save File")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", "Every Adventure Mode map at a glance"),
            hwdeMap("Adventure Map", "The 128 squares of the first map: battles, A-rank limits, rewards, Gold Skulltulas and item-card searches", "adventure_map"),
            hwdeMap("Great Sea Map", "The 98 squares of the Great Sea, its Great Sea Rules and the cards that lift them, and its warriors to unlock", "great_sea_map"),
            hwdeMap("Master Quest Map", "The 128 squares of the Second Quest's map under Master Quest Rules, with every weapon's level 3", "master_quest_map"),
            hwdeMap("Master Wind Waker Map", "The Great Sea again under Endless Night Rules: 98 squares, with Medli's Rito Harps", "master_wind_waker_map"),
            hwdeMap("Twilight Map", "Twilight Princess's Hyrule: 93 squares, the Twilight clouds and the Tears of Light that clear them, and every weapon's level 4", "twilight_map"),
            hwdeMap("Termina Map", "Majora's Mask's Termina: 85 squares against the falling Moon, the masks and Giants, and more level-4 weapons", "termina_map"),
            hwdeMap("Koholint Island Map", "Link's Awakening's island: 84 squares, the Nightmares the Instruments of the Sirens banish, and Marin", "koholint_island_map"),
            hwdeMap("Grand Travels Map", "The Ocean King's seas and New Hyrule: 128 squares from two starts, the Phantom Hourglass and Toon Zelda", "grand_travels_map"),
            hwdeMap("Lorule Map", "Hyrule and Lorule side by side: 128 squares joined by Fissures, Ravio and Yuga", "lorule_map"),
            hwdeMap("Rewards Map", "The 13 battles the Gold Skulltula illustrations open, and their Boss costumes", "rewards_map"),
        ),
        spinOff = true,
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

    /** Mainline games oldest first, then spin-offs oldest first: the order of [all]. */
    val order: Comparator<Game> = compareBy<Game>({ it.spinOff }, { it.released })

    /** Every game in [order]: a new game slots in by its release date wherever it's listed here. */
    val all: List<Game> = listOf(
        BOTW, TOTK, MC,
        LOZ, AOL, ALTTP, LA, OOT, MM, OOS, OOA, FS, WW, FSA, TP, PH, ST, SS, ALBW, TFH, EOW,
        HWDE,
    ).sortedWith(order)

    fun byId(id: String): Game = all.first { it.id == id }

    private fun lozChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "loz/$file.json")

    private fun aolChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "aol/$file.json")

    private fun laChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "la/$file.json")

    private fun alttpChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "alttp/$file.json")

    private fun stChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "st/$file.json")

    private fun albwChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "albw/$file.json")

    private fun wwChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "ww/$file.json")

    private fun tfhChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "tfh/$file.json")

    private fun tpChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "tp/$file.json")

    private fun phChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "ph/$file.json")

    private fun ssChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "ss/$file.json")

    private fun eowChecklist(title: String, description: String, file: String) =
        Tracker(TrackerKind.CHECKLIST, title, description, dataFile = "eow/$file.json")

    private fun hwdeMap(title: String, description: String, file: String) =
        Tracker(TrackerKind.ADVENTURE_MAP, title, description, dataFile = "hwde/$file.json")

    /** A game with no trackers yet; [files] placeholder save files, as many as the game itself keeps. */
    private fun comingSoon(id: String, title: String, released: ReleaseDate, platform: String, files: Int = 3) = Game(
        id = id,
        title = title,
        released = released,
        platform = platform,
        saveFiles = (1..files).map { SaveFile("file_$it", "File $it") },
    )
}
