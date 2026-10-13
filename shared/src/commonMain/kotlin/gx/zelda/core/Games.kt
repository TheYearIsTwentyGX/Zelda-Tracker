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

data class Tracker(val id: String, val title: String, val description: String)

object Games {
    const val BOTW_ARMOR = "botw_armor"
    const val BOTW_FAIRIES = "botw_fairies"

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
            Tracker(BOTW_ARMOR, "Armor Progress", "Collected armor, upgrade levels, set bonuses and materials"),
            Tracker(BOTW_FAIRIES, "Great Fairy Fountains", "Where to find each Great Fairy and what opening her fountain costs"),
        ),
    )

    /** Switch 2 Edition: two save files, no Master Mode. */
    val TOTK = Game(
        id = "totk",
        title = "Tears of the Kingdom",
        year = 2023,
        saveFiles = listOf(SaveFile("file_1", "Save File 1"), SaveFile("file_2", "Save File 2")),
    )

    val all = listOf(BOTW, TOTK)
}
