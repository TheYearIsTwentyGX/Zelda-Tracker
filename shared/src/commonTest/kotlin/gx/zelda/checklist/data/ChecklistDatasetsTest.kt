package gx.zelda.checklist.data

import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every checklist any game lists, checked against the format in docs/checklist-trackers.md: it
 * loads with no unknown keys and [gx.zelda.checklist.model.ChecklistCatalog.problems] finds
 * nothing. A new game's checklists are covered as soon as they're added to `core/Games.kt`;
 * per-game counts go in that game's own dataset test.
 */
class ChecklistDatasetsTest {
    private val checklists = Games.all.flatMap { game -> game.trackers.filter { it.kind == TrackerKind.CHECKLIST }.map { game to it } }

    @Test
    fun everyChecklistLoadsStrictlyAndIsSound() = runTest {
        checklists.forEach { (game, tracker) ->
            val file = tracker.dataFile!!
            assertTrue(file.startsWith("${game.id}/"), "$file isn't in ${game.id}/")
            val catalog = ChecklistRepository.load(file, strict = true)
            assertEquals(emptyList(), catalog.problems(), file)
        }
    }

    @Test
    fun aGameListsEachDatasetOnce() {
        Games.all.forEach { game ->
            val files = game.trackers.mapNotNull { it.dataFile }
            assertEquals(files.size, files.toSet().size, game.id)
            // Storage keys use the file's name, so two files with one name in a game would share progress.
            assertEquals(files.size, files.map { it.substringAfterLast('/') }.toSet().size, game.id)
        }
    }
}
