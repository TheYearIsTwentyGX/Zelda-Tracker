package gx.zelda.core

/** A game shown on the home screen. Games with no trackers are listed as "coming soon". */
data class Game(
    val id: String,
    val title: String,
    val year: Int,
    val trackers: List<Tracker> = emptyList(),
)

data class Tracker(val id: String, val title: String, val description: String)

object Games {
    const val BOTW_ARMOR = "botw_armor"

    val all = listOf(
        Game(
            id = "botw",
            title = "Breath of the Wild",
            year = 2017,
            trackers = listOf(
                Tracker(BOTW_ARMOR, "Armor Progress", "Collected armor, upgrade levels, set bonuses and materials"),
            ),
        ),
        Game(id = "totk", title = "Tears of the Kingdom", year = 2023),
    )
}
