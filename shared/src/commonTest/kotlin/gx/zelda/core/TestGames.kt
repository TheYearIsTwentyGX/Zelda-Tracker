package gx.zelda.core

/**
 * A game with no trackers, for testing the "Coming soon" paths whichever real games are still
 * waiting for theirs. Released between Tri Force Heroes (2015) and Breath of the Wild (2017).
 */
val comingSoonGame = Game("coming_soon", "Coming Soon Game", ReleaseDate(2016, 1, 1), "Test", listOf(SaveFile("file_1", "File 1")))

/** [Games.all] with [comingSoonGame] in its place by release date. */
val gamesWithComingSoon: List<Game> = (Games.all + comingSoonGame).sortedBy { it.released }

/** The ids of the games with trackers, oldest first: the ones shown on a fresh install. */
val gamesWithTrackers: List<String> get() = Games.all.filterNot { it.comingSoon }.map { it.id }
