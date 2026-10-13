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

data class SaveFile(val id: String, val label: String)

/** The kinds of tracker a game can offer; each kind has one set of screens shared by every game. */
enum class TrackerKind { ARMOR, FAIRIES }

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
            SaveFile("master", "Master Mode"),
        ),
        trackers = listOf(
            Tracker(TrackerKind.ARMOR, "Armor Progress", "Collected armor, upgrade levels, set bonuses and materials"),
            Tracker(TrackerKind.FAIRIES, "Great Fairy Fountains", "Where to find each Great Fairy and what opening her fountain costs"),
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
        ),
    )

    val all = listOf(BOTW, TOTK)

    fun byId(id: String): Game = all.first { it.id == id }
}
