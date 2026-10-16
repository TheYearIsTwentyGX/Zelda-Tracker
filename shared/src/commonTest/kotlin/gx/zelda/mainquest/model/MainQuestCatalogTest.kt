package gx.zelda.mainquest.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MainQuestCatalogTest {
    private fun tower(id: String) = SheikahTower(id, id, "Region", "Somewhere", "Climb it.")
    private fun memory(number: Int, photo: Int? = null) = Memory(number, "Memory $number", "Somewhere", photo = photo, hint = "Look.")

    private val catalog = MainQuestCatalog(
        towers = listOf(tower("a"), tower("b"), tower("c")),
        divineBeasts = emptyList(),
        memories = listOf(memory(1, photo = 1), memory(2), memory(3, photo = 2)),
    )

    @Test
    fun doneCountIgnoresUnknownIds() {
        assertEquals(2, catalog.doneCount(MainQuestSection.TOWERS, setOf("a", "c", "not_a_tower")))
        assertEquals(0, catalog.doneCount(MainQuestSection.MEMORIES, setOf("a")), "tower ids aren't memories")
    }

    @Test
    fun albumCountOnlyCountsPictures() {
        assertEquals(2, catalog.albumSize)
        assertEquals(1, catalog.albumCount(setOf("memory_1", "memory_2")))
    }

    @Test
    fun progressKeepsEachSectionApart() {
        val progress = MainQuestProgress()
            .with(MainQuestSection.TOWERS, "a", true)
            .with(MainQuestSection.MEMORIES, "memory_2", true)
            .with(MainQuestSection.TOWERS, "b", true)
            .with(MainQuestSection.TOWERS, "a", false)

        assertEquals(setOf("b"), progress.done(MainQuestSection.TOWERS))
        assertEquals(emptySet(), progress.done(MainQuestSection.DIVINE_BEASTS))
        assertEquals(setOf("memory_2"), progress.done(MainQuestSection.MEMORIES))
    }

    private fun dungeon(id: String, element: String?) = Dungeon(id, 1, id, "Region", "Go there.", boss = "Boss", element = element, rewards = emptyList(), tip = "Hit it.")
    private fun scroll(id: String, upgrade: Boolean = false) = TigerScroll(id, id, "Teacher", "Dojo", "None", "Swing.", upgrade)

    private val minishCap = MainQuestCatalog(
        dungeons = listOf(dungeon("earth", "Earth"), dungeon("fortress", null), dungeon("fire", "Fire")),
        items = listOf(InventoryItem("jar", "Jar", ItemCategory.ITEM, "Woods", "Find it.")),
        tigerScrolls = listOf(scroll("spin"), scroll("beam"), scroll("faster", upgrade = true)),
    )

    @Test
    fun sectionsAreTheNonEmptyChecklistsInOrder() {
        assertEquals(listOf(MainQuestSection.TOWERS, MainQuestSection.MEMORIES), catalog.sections)
        assertEquals(listOf(MainQuestSection.DUNGEONS, MainQuestSection.ITEMS, MainQuestSection.TIGER_SCROLLS), minishCap.sections)
    }

    @Test
    fun tigerScrollSectionCountsScrollsAndUpgradesApart() {
        val done = setOf("spin", "faster")
        assertEquals(2, minishCap.total(MainQuestSection.TIGER_SCROLLS))
        assertEquals(1, minishCap.doneCount(MainQuestSection.TIGER_SCROLLS, done))
        assertEquals(1, minishCap.upgradeCount(done))
    }

    @Test
    fun elementsCountOnlyDungeonsThatHoldOne() {
        assertEquals(2, minishCap.elementTotal)
        assertEquals(1, minishCap.elementCount(setOf("earth", "fortress")))
    }

    @Test
    fun minishCapProgressKeepsEachSectionApart() {
        val progress = MainQuestProgress()
            .with(MainQuestSection.DUNGEONS, "earth", true)
            .with(MainQuestSection.ITEMS, "jar", true)
            .with(MainQuestSection.TIGER_SCROLLS, "spin", true)
            .with(MainQuestSection.ITEMS, "jar", false)

        assertEquals(MainQuestProgress(dungeons = setOf("earth"), tigerScrolls = setOf("spin")), progress)
    }
}
