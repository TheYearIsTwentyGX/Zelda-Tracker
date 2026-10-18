package gx.zelda.checklist.data

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import kotlin.test.Test
import kotlin.test.assertEquals

class ChecklistProgressProviderTest {
    private val catalog = ChecklistFixture.catalog

    private fun progress(vararg done: String) = done.fold(ChecklistProgress()) { p, id -> p.withDone(catalog.item(id)!!, true) }

    @Test
    fun countsStepsAndListsSummariesThenSections() {
        val row = ChecklistProgressProvider.summarize("Hearts & More", catalog, progress("field_cave", "boss_jabul"), ChecklistPrefs())
        assertEquals("Hearts & More", row.title)
        assertEquals(2, row.done)
        assertEquals(9, row.total)
        assertEquals("Hearts 4 / 5 · Pieces of Heart 1 / 5 · Heart Containers 1 / 1 · Upgrades 0 / 3", row.secondary)
    }

    @Test
    fun nextNamesTheFirstUnlockedItem() {
        val row = ChecklistProgressProvider.summarize("T", catalog, progress("field_cave", "field_race", "field_tree"), ChecklistPrefs())
        assertEquals("River Zora minigame, in Jabul Waters", row.next)
    }

    @Test
    fun withSpoilersHiddenNextOnlySaysHowManyAreLeftWhere() {
        val row = ChecklistProgressProvider.summarize("T", catalog, progress("field_cave"), ChecklistPrefs(hideUnchecked = true))
        assertEquals("2 pieces still to do in Hyrule Field", row.next)
        // zora_cove is locked until the boss, so the next is the last piece in the field.
        val one = ChecklistProgressProvider.summarize("T", catalog, progress("field_cave", "field_race", "river_zora"), ChecklistPrefs(hideUnchecked = true))
        assertEquals("1 piece still to do in Hyrule Field", one.next)
    }

    @Test
    fun aRegionlessItemIsNamedAloneOrByItsSection() {
        val allButSword = catalog.items.filter { it.id != "sword" }.map { it.id }.toTypedArray()
        assertEquals("Sword", ChecklistProgressProvider.summarize("T", catalog, progress(*allButSword), ChecklistPrefs()).next)
        assertEquals(
            "1 upgrade still to do in Upgrades",
            ChecklistProgressProvider.summarize("T", catalog, progress(*allButSword), ChecklistPrefs(hideUnchecked = true)).next,
        )
    }

    @Test
    fun everythingDone() {
        val all = catalog.items.fold(ChecklistProgress()) { p, i -> p.withDone(i, true) }
        val row = ChecklistProgressProvider.summarize("T", catalog, all, ChecklistPrefs(hideUnchecked = true))
        assertEquals("All done", row.next)
        assertEquals(1f, row.fraction)
    }

    @Test
    fun aSeparateSectionHasItsCountButStaysOutOfTheBar() {
        val v = ChecklistFixture.versions
        val row = ChecklistProgressProvider.summarize("T", v, ChecklistProgress().withDone(v.item("remake_well")!!, true), ChecklistPrefs())
        assertEquals(0, row.done)
        assertEquals(2, row.total)
        assertEquals("Hearts 3 / 3½ · Original 0 / 2 · Remake 1 / 3", row.secondary)
        assertEquals("Well, in Mabe Village", row.next)
    }

    @Test
    fun aOneSectionFileHasNoSectionCounts() {
        val single = ChecklistRepository.parse("""{"sections": [{"id": "a", "title": "A", "items": [{"id": "x", "name": "X"}]}]}""")
        assertEquals(null, ChecklistProgressProvider.summarize("T", single, ChecklistProgress(), ChecklistPrefs()).secondary)
    }
}
