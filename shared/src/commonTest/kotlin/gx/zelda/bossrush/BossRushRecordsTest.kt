package gx.zelda.bossrush

import gx.zelda.bossrush.data.BossRushData
import gx.zelda.bossrush.data.BossRushProgressProvider
import gx.zelda.bossrush.model.BossRushBoard
import gx.zelda.bossrush.model.BossRushBoss
import gx.zelda.bossrush.model.BossRushCatalog
import gx.zelda.bossrush.model.BossRushRecords
import gx.zelda.bossrush.model.RecordEntry
import gx.zelda.bossrush.model.SideQuestRef
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.StoryGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A file's records: the personal best, the history, edits and deletes, the board's slots and the overview row. */
class BossRushRecordsTest {
    private val catalog = BossRushCatalog(
        SideQuestRef("ss/side_quests.json", "thunder_dragon_boss_rush"),
        listOf(
            BossRushBoss("ghirahim_1", "Ghirahim", "1st battle", "Skyview Temple", "ghirahim", "GHIRAHIM1"),
            BossRushBoss("scaldera", "Scaldera", null, "Earth Temple", "scaldera", "SCALDERA"),
            BossRushBoss("demise", "Demise", null, "Demise's realm", "demise", "DEMISE", heroOnly = true),
        ),
    )

    @Test
    fun theBestIsTheFastestAndTheEarlierOnATie() {
        val r = BossRushRecords()
            .withAdded("scaldera", 9_000, day = 1)
            .withAdded("scaldera", 7_500, day = 2)
            .withAdded("scaldera", 8_000, day = 3)
            .withAdded("scaldera", 7_500, day = 4)
        assertEquals(RecordEntry(2, 7_500, 2), r.best("scaldera"))
        assertEquals(RecordEntry(1, 9_000, 1), r.previousBest("scaldera"), "what the record beat")
        assertEquals(listOf(1, 2, 3, 4), r.of("scaldera").map { it.id })
        assertNull(r.best("koloktos"))
        assertNull(BossRushRecords().withAdded("scaldera", 1, null).previousBest("scaldera"), "a first time beat nothing")
    }

    @Test
    fun editingAndDeletingAnEntryMovesTheBest() {
        val r = BossRushRecords().withAdded("scaldera", 9_000, 1).withAdded("scaldera", 7_500, 2)
        val edited = r.withEdited("scaldera", 2, 9_500)
        assertEquals(RecordEntry(2, 9_500, 2), edited.of("scaldera")[1], "the date stays")
        assertEquals(1, edited.best("scaldera")!!.id)
        assertEquals(r, r.withEdited("scaldera", 7, 1), "no such entry")
        val deleted = r.without("scaldera", 2)
        assertEquals(9_000, deleted.best("scaldera")!!.time)
        assertEquals(BossRushRecords(), deleted.without("scaldera", 1), "an emptied boss leaves no key")
        assertEquals(r, deleted.withEntries("scaldera", r.of("scaldera")), "undo puts the list back")
        assertEquals(listOf(1, 2), deleted.withAdded("scaldera", 1, 3).of("scaldera").map { it.id }, "a new id follows the highest left")
    }

    @Test
    fun theHistoryIsShortButNeverLosesTheBest() {
        var r = BossRushRecords().withAdded("scaldera", 100, day = 0)
        repeat(BossRushRecords.HISTORY + 5) { r = r.withAdded("scaldera", 1_000 + it, day = it.toLong()) }
        val list = r.of("scaldera")
        assertEquals(BossRushRecords.HISTORY, list.size)
        assertEquals(100, r.best("scaldera")!!.time, "the oldest entry is the best, so it stays")
        assertEquals(1_000 + BossRushRecords.HISTORY + 4, list.last().time)
        assertEquals(list.map { it.id }.toSet().size, list.size)
    }

    @Test
    fun aNormalBoardHidesDemiseButKeepsItsRecord() {
        val r = BossRushRecords().withAdded("demise", 30_000, 1).withAdded("scaldera", 7_000, 1)
        val normal = BossRushBoard(catalog, r, heroMode = false)
        assertEquals(listOf("ghirahim_1", "scaldera"), normal.rows.map { it.boss.id })
        assertEquals(1 to 2, normal.set to normal.slots)
        assertEquals(7_000, normal.sumOfBests)
        assertEquals(listOf("demise"), normal.hidden.map { it.id })
        assertEquals("ghirahim_1", normal.nextWithout?.id)
        val hero = BossRushBoard(catalog, r, heroMode = true)
        assertEquals(2 to 3, hero.set to hero.slots)
        assertEquals(37_000, hero.sumOfBests)
        assertTrue(hero.hidden.isEmpty())
        assertEquals(listOf(2, 1), r.withAdded("scaldera", 6_000, 2).let { BossRushBoard(catalog, it, true).rows[1].history.map { e -> e.id } }, "newest first")
        assertEquals(1_000, BossRushBoard(catalog, r.withAdded("scaldera", 6_000, 2), true).rows[1].improvement)
        assertNull(BossRushBoard(catalog, BossRushRecords(), false).sumOfBests)
    }

    @Test
    fun theOverviewRowCountsTheFilesSlots() {
        val r = BossRushRecords().withAdded("scaldera", 7_543, 1).withAdded("demise", 30_000, 1)
        val normal = BossRushProgressProvider.summarize(BossRushBoard(catalog, r, heroMode = false))
        assertEquals("Lightning Round Records", normal.title)
        assertEquals(1 to 2, normal.done to normal.total)
        assertEquals("Records set 1/2 · bests total 1:15.43", normal.secondary)
        assertEquals("Ghirahim (1st battle)", normal.next)
        val hero = BossRushProgressProvider.summarize(BossRushBoard(catalog, r, heroMode = true))
        assertEquals("Records set 2/3 (Hero Mode) · bests total 6:15.43", hero.secondary)
        val empty = BossRushProgressProvider.summarize(BossRushBoard(catalog, BossRushRecords(), heroMode = false))
        assertEquals("Records set 0/2", empty.secondary)
        val full = BossRushRecords().withAdded("ghirahim_1", 1, 1).withAdded("scaldera", 1, 1)
        assertEquals("Every boss has a record", BossRushProgressProvider.summarize(BossRushBoard(catalog, full, false)).next)
    }

    @Test
    fun theStoryGateOnlyChangesTheSuggestion() {
        val quest = ChecklistItem(id = "thunder_dragon_boss_rush", name = "Lightning Round", unlockedBy = listOf("hero_song_lanayru"))
        val data = BossRushData(catalog, quest)
        val steps = mapOf("hero_song_lanayru" to "Lanayru's part")
        assertEquals(listOf("Lanayru's part"), BossRushProgressProvider.lockedBy(data, StoryGate(enabled = true, stepNames = steps)))
        assertEquals(emptyList(), BossRushProgressProvider.lockedBy(data, StoryGate(enabled = false, stepNames = steps)), "gating off")
        assertEquals(emptyList(), BossRushProgressProvider.lockedBy(data, StoryGate(true, setOf("hero_song_lanayru"), steps)), "reached")
        assertEquals(emptyList(), BossRushProgressProvider.lockedBy(BossRushData(catalog, null), StoryGate(true, emptySet(), steps)), "no item, no gate")
        val board = BossRushBoard(catalog, BossRushRecords(), false)
        assertEquals("Opens after Lanayru's part", BossRushProgressProvider.summarize(board, listOf("Lanayru's part")).next)
        val withRecord = BossRushBoard(catalog, BossRushRecords().withAdded("scaldera", 1, 1), false)
        val row = BossRushProgressProvider.summarize(withRecord, listOf("Lanayru's part"))
        assertEquals(1, row.done, "records entered ahead of the story still count")
        assertEquals("Ghirahim (1st battle)", row.next)
    }
}
