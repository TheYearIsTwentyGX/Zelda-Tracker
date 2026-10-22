package gx.zelda.checklist

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.core.Game
import gx.zelda.core.ReleaseDate
import gx.zelda.core.SaveFile
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind

/**
 * A game whose checklists share progress through `sameAs` links: Rewards' wallet rewards are tiers of
 * Items' Wallet, its harp reward is Items' Harp, Items' Harp Song is its own Harp, and the Main
 * Quest's trial is the Sword's second tier. Some rewards are gated by Main Quest steps.
 */
object LinkFixture {
    val game = Game(
        id = "link",
        title = "Link Game",
        released = ReleaseDate(2030, 1, 1),
        platform = "Test",
        saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
        trackers = listOf(
            Tracker(TrackerKind.OVERVIEW, "Progress Overview", ""),
            Tracker(TrackerKind.CHECKLIST, "Main Quest", "", dataFile = "link/main_quest.json"),
            Tracker(TrackerKind.CHECKLIST, "Items", "", dataFile = "link/items.json"),
            Tracker(TrackerKind.CHECKLIST, "Rewards", "", dataFile = "link/rewards.json"),
        ),
    )

    const val STORY_JSON = """
{
  "sections": [
    {"id": "quest", "title": "Main Quest", "items": [
      {"id": "start", "name": "Start"},
      {"id": "temple", "name": "Temple", "requires": ["start"]},
      {"id": "trial", "name": "Trial", "sameAs": "items:sword@2", "requires": ["temple"]}
    ]}
  ]
}
"""

    const val ITEMS_JSON = """
{
  "sections": [
    {"id": "gear", "title": "Gear", "items": [
      {"id": "sword", "name": "Sword", "tiers": [{"name": "Plain Sword"}, {"name": "Trial Sword"}]},
      {"id": "harp", "name": "Harp"},
      {"id": "harp_song", "name": "Harp Song", "sameAs": "harp"}
    ]},
    {"id": "wallets", "title": "Wallets", "items": [
      {"id": "wallet", "name": "Wallet", "tiers": [{"name": "Medium Wallet"}, {"name": "Big Wallet"}, {"name": "Giant Wallet"}]}
    ]}
  ]
}
"""

    const val REWARDS_JSON = """
{
  "regions": ["Town"],
  "noun": "reward",
  "summaries": [{"label": "Rewards", "formula": "rewards"}],
  "sections": [
    {"id": "rewards", "title": "Rewards", "hiddenLabel": "Unclaimed reward", "items": [
      {"id": "reward_5", "name": "5 crystals", "region": "Town", "sameAs": "items:wallet@1", "reward": "Medium Wallet"},
      {"id": "reward_10", "name": "10 crystals", "region": "Town", "sameAs": "items:harp", "reward": "Harp"},
      {"id": "reward_30", "name": "30 crystals", "region": "Town", "sameAs": "items:wallet@2", "unlockedBy": ["temple"], "reward": "Big Wallet"},
      {"id": "reward_50", "name": "50 crystals", "region": "Town", "sameAs": "items:wallet@3", "unlockedBy": ["temple"], "reward": "Giant Wallet"},
      {"id": "gold", "name": "Gold Rupee", "region": "Town"},
      {"id": "after_trial", "name": "After the trial", "region": "Town", "unlockedBy": ["trial"]}
    ]}
  ]
}
"""

    val story: ChecklistCatalog by lazy { ChecklistRepository.parse(STORY_JSON, strict = true) }
    val items: ChecklistCatalog by lazy { ChecklistRepository.parse(ITEMS_JSON, strict = true) }
    val rewards: ChecklistCatalog by lazy { ChecklistRepository.parse(REWARDS_JSON, strict = true) }

    /** Every file, by `dataFile`, as [gx.zelda.checklist.data.ChecklistLinks] takes them. */
    val catalogs: Map<String, ChecklistCatalog> by lazy {
        mapOf("link/main_quest.json" to story, "link/items.json" to items, "link/rewards.json" to rewards)
    }

    /** Rewards' progress as its storage reads it: its own [levels], and Items' [items] levels. */
    fun rewardsProgress(items: Map<String, Int> = emptyMap(), levels: Map<String, Int> = emptyMap()) =
        ChecklistProgress(levels, linked = mapOf("items" to items, "main_quest" to emptyMap()))

    fun item(id: String) = (rewards.item(id) ?: items.item(id) ?: story.item(id))!!
}
