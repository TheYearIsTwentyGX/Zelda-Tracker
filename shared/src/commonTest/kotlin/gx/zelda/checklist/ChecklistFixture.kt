package gx.zelda.checklist

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.model.ChecklistCatalog

/**
 * A small checklist in the documented format (docs/checklist-trackers.md): two regions, three
 * sections, a tiered item, a missable one, a prerequisite and a hearts formula.
 */
object ChecklistFixture {
    const val JSON = """
{
  "regions": ["Hyrule Field", "Jabul Waters"],
  "noun": "heart", "plural": "hearts",
  "summaries": [{"label": "Hearts", "formula": "3 + floor(pieces / 4) + containers"}],
  "sections": [
    {
      "id": "pieces", "title": "Pieces of Heart", "noun": "piece", "hiddenLabel": "Uncollected Piece of Heart",
      "items": [
        {"id": "field_cave", "name": "Cave under the rock", "region": "Hyrule Field", "area": "Cave", "howTo": "Lift the rock."},
        {"id": "field_race", "name": "Horse race", "region": "Hyrule Field", "howTo": "Win the race.", "missable": true, "reward": "50 Rupees"},
        {"id": "zora_cove", "name": "Zora Cove ledge", "region": "Jabul Waters", "howTo": "Swim to the ledge.", "requires": ["boss_jabul"]},
        {"id": "river_zora", "name": "River Zora minigame", "region": "Jabul Waters", "tags": ["Minigame"]},
        {"id": "field_tree", "name": "Top of the big tree", "region": "Hyrule Field"}
      ]
    },
    {
      "id": "containers", "title": "Heart Containers", "noun": "container",
      "items": [
        {"id": "boss_jabul", "name": "Jabul Ruins boss", "region": "Jabul Waters", "after": "Jabul Waters rift"}
      ]
    },
    {
      "id": "upgrades", "title": "Upgrades", "noun": "upgrade",
      "summary": {"label": "Crystals spent", "formula": "upgrades.value"},
      "items": [
        {"id": "sword", "name": "Sword", "value": 5, "tiers": [{"name": "Level 2"}, {"name": "Level 3", "value": 10}, {"name": "Level 4", "value": 20}]}
      ]
    }
  ]
}
"""

    val catalog: ChecklistCatalog by lazy { ChecklistRepository.parse(JSON, strict = true) }
}
