package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.storyOrder
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every [gx.zelda.checklist.model.ChecklistItem.unlockedBy] link in every game, checked against
 * that game's story file (docs/checklist-trackers.md, *Story gating*). Each game's own dataset test
 * spot-checks a few of its links.
 */
class StoryLinksDatasetTest {
    @Test
    fun everyLinkNamesAnUntieredStepOfTheGamesMainQuest() = runTest {
        val problems = Games.all.flatMap { game ->
            val files = game.trackers.filter { it.kind == TrackerKind.CHECKLIST }.mapNotNull { it.dataFile }
            val storyFile = storyFileOf(game)
            val story = storyFile?.let { ChecklistRepository.load(it) }
            val order = story?.let(::storyOrder).orEmpty()
            files.flatMap { file ->
                val catalog = ChecklistRepository.load(file)
                when {
                    file == storyFile -> catalog.items.filter { it.unlockedBy.isNotEmpty() }.map { "$file: '${it.id}' is in the story file itself" }
                    story == null -> catalog.items.filter { it.unlockedBy.isNotEmpty() }.map { "$file: '${it.id}' has unlockedBy but ${game.id} has no main_quest.json" }
                    else -> linkProblems(file, catalog, story, order)
                }
            }
        }
        assertEquals(emptyList(), problems)
    }

    /**
     * Unknown or tiered steps, and an item that unlocks earlier in the story than something it
     * requires: a mis-mapped link, since it can't be had before what it needs.
     */
    private fun linkProblems(file: String, catalog: ChecklistCatalog, story: ChecklistCatalog, order: Map<String, Int>): List<String> =
        catalog.items.flatMap { item ->
            val steps = item.unlockedBy.map { it to story.item(it) }
            val bad = steps.mapNotNull { (id, step) ->
                when {
                    step == null -> "$file: '${item.id}' is unlocked by unknown step '$id'"
                    step.tiers.isNotEmpty() -> "$file: '${item.id}' is unlocked by tiered step '$id'"
                    else -> null
                }
            }
            val depth = item.unlockedBy.maxOfOrNull { order[it] ?: -1 } ?: -1
            val early = item.requires.mapNotNull(catalog::item).mapNotNull { needed ->
                val neededDepth = needed.unlockedBy.maxOfOrNull { order[it] ?: -1 } ?: -1
                "$file: '${item.id}' unlocks before '${needed.id}', which it requires".takeIf { neededDepth > depth }
            }
            bad + early
        }
}
