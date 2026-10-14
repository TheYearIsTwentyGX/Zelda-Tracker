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
}
