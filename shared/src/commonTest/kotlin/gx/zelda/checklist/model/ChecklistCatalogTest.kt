package gx.zelda.checklist.model

import gx.zelda.checklist.ChecklistFixture
import gx.zelda.checklist.data.ChecklistRepository
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecklistCatalogTest {
    private val c = ChecklistFixture.catalog
    private fun item(id: String) = c.item(id)!!

    @Test
    fun parsesTheDocumentedFormat() {
        assertEquals(listOf("pieces", "containers", "upgrades"), c.sections.map { it.id })
        assertEquals(7, c.items.size)
        val race = item("field_race")
        assertTrue(race.missable)
        assertEquals("50 Rupees", race.reward)
        assertEquals(listOf("boss_jabul"), item("zora_cove").requires)
        assertEquals("Jabul Waters rift", item("boss_jabul").after)
        assertEquals(3, item("sword").steps)
        assertEquals(1, item("field_cave").steps)
        assertTrue(c.problems().isEmpty(), c.problems().toString())
    }

    @Test
    fun strictParsingRejectsAMisspeltField() {
        val typo = """{"sections": [{"id": "a", "title": "A", "items": [{"id": "x", "name": "X", "howto": "…"}]}]}"""
        assertFailsWith<SerializationException> { ChecklistRepository.parse(typo, strict = true) }
        assertNull(ChecklistRepository.parse(typo).items.single().howTo, "the app itself ignores unknown keys")
    }

    @Test
    fun countsAreInStepsWithATierEach() {
        val progress = ChecklistProgress()
            .withDone(item("field_cave"), true)
            .withLevel(item("sword"), 2)
        assertEquals(ChecklistCount(done = 3, total = 9), c.count(progress))
        assertEquals(ChecklistCount(1, 5), c.count(progress, c.section("pieces")!!.items))
        assertEquals(ChecklistCount(2, 3), c.count(progress, c.section("upgrades")!!.items))
    }

    @Test
    fun levelsAreClampedToTheItem() {
        val sword = item("sword")
        assertEquals(3, ChecklistProgress().withLevel(sword, 9).level(sword))
        assertEquals(emptyMap(), ChecklistProgress().withLevel(sword, 2).withLevel(sword, -1).levels)
        // A stored level above the item's steps (the dataset shrank) still reads as done, not more.
        assertEquals(1, ChecklistProgress(mapOf("field_cave" to 4)).level(item("field_cave")))
        assertTrue(ChecklistProgress().withLevel(sword, 1).isStarted(sword))
        assertFalse(ChecklistProgress().withLevel(sword, 1).isDone(sword))
    }

    @Test
    fun summaryFormulasUseCountsAndValues() {
        val hearts = c.summaries.single()
        assertEquals(SummaryValue("Hearts", 3.0, 5.0), c.evaluate(hearts, ChecklistProgress()))

        val four = listOf("field_cave", "field_race", "zora_cove", "river_zora").fold(ChecklistProgress()) { p, id -> p.withDone(item(id), true) }
        assertEquals(4.0, c.evaluate(hearts, four).value)
        assertEquals(5.0, c.evaluate(hearts, four.withDone(item("boss_jabul"), true)).value)
        assertEquals("Hearts 5 / 5", c.evaluate(hearts, four.withDone(item("boss_jabul"), true)).text)

        // A tier's own value overrides the item's: 5 + 10 for two tiers, 35 with all three.
        val spent = c.section("upgrades")!!.summary!!
        assertEquals(SummaryValue("Crystals spent", 15.0, 35.0), c.evaluate(spent, ChecklistProgress().withLevel(item("sword"), 2)))
    }

    /** An item id reads that one item, like a section id reads its section. */
    @Test
    fun formulasCanReadOneItem() {
        val progress = ChecklistProgress().withLevel(item("sword"), 2).withDone(item("field_cave"), true)
        assertEquals(SummaryValue("Sword", 2.0, 3.0), c.evaluate(ChecklistSummary("Sword", "sword"), progress))
        assertEquals(SummaryValue("Base + sword", 25.0, 45.0), c.evaluate(ChecklistSummary("Base + sword", "10 + sword.value"), progress))
        assertEquals(1.0, c.evaluate(ChecklistSummary("Cave", "field_cave"), progress).value)
        assertTrue(ChecklistRepository.parse(
            """{"summaries": [{"label": "S", "formula": "x.value + a"}], "sections": [{"id": "a", "title": "A", "items": [{"id": "x", "name": "X"}]}]}""",
        ).problems().isEmpty())
    }

    @Test
    fun nextIsTheFirstUndoneItemWhosePrerequisitesAreDone() {
        val progress = listOf("field_cave", "field_race").fold(ChecklistProgress()) { p, id -> p.withDone(item(id), true) }
        // zora_cove needs boss_jabul, so river_zora comes first.
        assertEquals("river_zora", c.next(progress)?.id)
        assertEquals("zora_cove", c.next(progress.withDone(item("boss_jabul"), true))?.id)
        assertFalse(c.isUnlocked(item("zora_cove"), progress))

        val all = c.items.fold(ChecklistProgress()) { p, i -> p.withDone(i, true) }
        assertNull(c.next(all))
        assertEquals("zora_cove", c.next(all.withDone(item("zora_cove"), false))?.id)
    }

    @Test
    fun groupsFollowTheRegionOrderThenSections() {
        assertEquals(
            listOf("Hyrule Field" to listOf("field_cave", "field_race", "field_tree"), "Jabul Waters" to listOf("zora_cove", "river_zora", "boss_jabul"), "Upgrades" to listOf("sword")),
            c.grouped(c.items).map { (g, items) -> g to items.map { it.id } },
        )
        assertEquals("Upgrades", c.groupOf(item("sword")))
    }

    @Test
    fun nounsAndHiddenLabelsFallBack() {
        assertEquals("Uncollected Piece of Heart", c.hiddenLabelOf(item("field_cave")))
        assertEquals("Unchecked container", c.hiddenLabelOf(item("boss_jabul")))
        assertEquals("pieces", c.pluralOf(c.section("pieces")!!))
        val bare = ChecklistRepository.parse("""{"sections": [{"id": "a", "title": "A", "items": [{"id": "x", "name": "X"}]}]}""")
        assertEquals("entries", bare.pluralOf(bare.sections.single()))
        val quests = ChecklistRepository.parse("""{"noun": "side quest", "sections": [{"id": "a", "title": "A", "items": [{"id": "x", "name": "X"}]}]}""")
        assertEquals("side quests", quests.pluralOf(quests.sections.single()))
        assertEquals("Unchecked entry", bare.hiddenLabelOf(bare.items.single()))
    }

    @Test
    fun problemsFindsBrokenData() {
        val broken = ChecklistRepository.parse(
            """
            {"regions": ["Field", "Field", "Empty"],
             "summaries": [{"label": "Bad", "formula": "3 +"}, {"label": "Unknown", "formula": "nope + a.value"}],
             "sections": [
               {"id": "a", "title": "A", "items": [
                 {"id": "x", "name": "X", "region": "Field", "requires": ["y", "ghost"]},
                 {"id": "y", "name": "Y", "region": "Nowhere", "requires": ["w"]},
                 {"id": "w", "name": "W", "region": "Field", "requires": ["y"]},
                 {"id": "x", "name": "", "value": -1},
                 {"id": "Bad Id", "name": "Z"}
               ]},
               {"id": "b", "title": "B", "items": []}
             ]}
            """,
        )
        val problems = broken.problems()
        listOf(
            "duplicate item id 'x'",
            "a region is listed twice",
            "region 'Empty' has no items",
            "item 'y': unknown region 'Nowhere'",
            "item 'x' requires unknown item 'ghost'",
            "item 'x' has no name",
            "item 'x' has a negative value",
            "bad item id 'Bad Id'",
            "section 'b' has no items",
            "summary 'Unknown' reads unknown section or item 'nope'",
            "requires loop: y → w → y",
        ).forEach { expected -> assertTrue(problems.any { it == expected }, "missing \"$expected\" in $problems") }
        assertTrue(problems.any { it.startsWith("Formula \"3 +\"") }, problems.toString())
    }
}
