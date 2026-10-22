package gx.zelda.checklist

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.checklist.model.StoryGate

/**
 * A Main Quest that branches (fire and water both follow the forest), and a checklist whose items
 * are unlocked by its steps: one, two at once, none, and one naming a step that doesn't exist.
 */
object StoryFixture {
    const val STORY_JSON = """
{
  "sections": [
    {"id": "quest", "title": "Main Quest", "items": [
      {"id": "forest", "name": "Forest Temple"},
      {"id": "fire", "name": "Fire Temple", "requires": ["forest"]},
      {"id": "water", "name": "Water Temple", "requires": ["forest"]},
      {"id": "finale", "name": "Finale", "requires": ["fire", "water"]}
    ]}
  ]
}
"""

    const val GATED_JSON = """
{
  "regions": ["Field", "Mountain"],
  "noun": "heart",
  "sections": [
    {"id": "pieces", "title": "Pieces", "noun": "piece", "hiddenLabel": "Uncollected piece", "items": [
      {"id": "start", "name": "Start piece", "region": "Field"},
      {"id": "forest_piece", "name": "Forest piece", "region": "Field", "unlockedBy": ["forest"], "missable": true},
      {"id": "next_to_it", "name": "Ledge beside it", "region": "Field", "requires": ["forest_piece"]},
      {"id": "typo", "name": "Typo piece", "region": "Field", "unlockedBy": ["no_such_step"]},
      {"id": "mountain_piece", "name": "Mountain piece", "region": "Mountain", "unlockedBy": ["fire"]},
      {"id": "both", "name": "Fire and water piece", "region": "Mountain", "unlockedBy": ["fire", "water"]}
    ]},
    {"id": "upgrades", "title": "Upgrades", "noun": "upgrade", "items": [
      {"id": "quiver", "name": "Quiver", "unlockedBy": ["fire"], "tiers": [{"name": "30 arrows"}, {"name": "40 arrows"}]}
    ]}
  ]
}
"""

    val story: ChecklistCatalog by lazy { ChecklistRepository.parse(STORY_JSON, strict = true) }
    val gated: ChecklistCatalog by lazy { ChecklistRepository.parse(GATED_JSON, strict = true) }

    /** The gate with [steps] of the story done. */
    fun gate(vararg steps: String, enabled: Boolean = true): StoryGate =
        StoryGate.of(enabled, story, steps.fold(ChecklistProgress()) { p, id -> p.withDone(story.item(id)!!, true) })

    /** Progress on [gated] with [levels] set, by item id. */
    fun progress(vararg levels: Pair<String, Int>): ChecklistProgress =
        levels.fold(ChecklistProgress()) { p, (id, level) -> p.withLevel(gated.item(id)!!, level) }
}
