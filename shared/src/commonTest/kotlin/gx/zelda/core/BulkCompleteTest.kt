package gx.zelda.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BulkCompleteTest {
    private fun scope(total: Int, done: Int, started: Int = done, filtered: Boolean = false) =
        BulkScope("Hyrule Field", total = total, done = done, started = started, noun = "piece", filtered = filtered)

    @Test
    fun marksUntilEverythingIsCompleteThenClears() {
        val none = scope(5, 0)
        assertEquals(BulkAction.MARK, none.action)
        assertEquals(false, none.checked)
        assertEquals(5, none.changes)
        assertEquals("Mark all complete", none.actionLabel)

        val some = scope(5, 2, started = 3)
        assertEquals(BulkAction.MARK, some.action)
        assertNull(some.checked, "a dash: some are started")
        assertEquals(3, some.changes)

        val all = scope(5, 5)
        assertEquals(BulkAction.CLEAR, all.action)
        assertEquals(true, all.checked)
        assertEquals(5, all.changes)
        assertEquals("Clear all", all.actionLabel)
        assertEquals("Clear all in Hyrule Field, 5 of 5 done", all.contentDescription)
    }

    @Test
    fun aClearAlwaysAsksAndAMarkAsksPastAHandful() {
        assertFalse(scope(5, 2).needsConfirm, "3 to mark")
        assertTrue(scope(5, 1).needsConfirm, "4 to mark")
        assertTrue(scope(1, 1).needsConfirm, "clearing even one asks")
    }

    @Test
    fun aFilteredScopeSaysItActsOnTheRowsShown() {
        val s = scope(3, 1, filtered = true)
        assertEquals("Mark 2 shown complete", s.actionLabel)
        assertEquals("Clear 3 shown", scope(3, 3, filtered = true).actionLabel)
        assertTrue("Only the ones the filters show change." in s.confirmText("File 1"))
    }

    @Test
    fun theConfirmNamesTheCountTheScopeAndTheSaveFile() {
        val s = scope(12, 0).copy(note = "Remake isn't changed.")
        assertEquals("Mark 12 pieces complete?", s.confirmTitle)
        assertEquals(
            "12 pieces in Hyrule Field on File 2 will be marked complete.\n\nRemake isn't changed.\n\nYou can undo it right after.",
            s.confirmText("File 2"),
        )
        assertEquals("Clear 1 piece?", scope(1, 1).confirmTitle)
    }

    @Test
    fun ofCountsItemsDoneAndStarted() {
        val levels = mapOf("a" to 3, "b" to 1, "c" to 0)
        val s = BulkScope.of("Upgrades", levels.keys.toList(), "upgrade", isDone = { levels.getValue(it) == 3 }, isStarted = { levels.getValue(it) > 0 })
        assertEquals(BulkScope("Upgrades", total = 3, done = 1, started = 2, noun = "upgrade"), s)
    }

    @Test
    fun aChangeSaysWhatItDidAndWhere() {
        val file = SaveFile("file_1", "File 1")
        assertEquals("Marked 5 pieces complete on File 1", BulkChange(BulkAction.MARK, 5, "piece", "pieces", file, Unit).message)
        assertEquals("Cleared 1 piece on File 1", BulkChange(BulkAction.CLEAR, 1, "piece", "pieces", file, Unit).message)
    }

    @Test
    fun aMapSnapshotPutsBackOnlyItsKeysExactly() {
        val before = mapOf("a" to 1, "b" to 2, "stale" to 9)
        val snapshot = MapSnapshot.of(before, listOf("a", "c"))
        assertEquals(mapOf("a" to 1, "c" to null), snapshot.before)
        // After the change, and a later edit to "b" outside the snapshot.
        val now = mapOf("a" to 3, "b" to 5, "c" to 1, "stale" to 9)
        assertEquals(mapOf("a" to 1, "b" to 5, "stale" to 9), snapshot.restore(now))
    }

    @Test
    fun aSetSnapshotPutsBackOnlyItsKeys() {
        val snapshot = SetSnapshot.of(setOf("a", "x"), listOf("a", "b"))
        assertEquals(setOf("a", "x", "y"), snapshot.restore(setOf("b", "x", "y")))
        assertEquals(setOf("x"), SetSnapshot.of(emptySet(), listOf("a")).restore(setOf("a", "x")))
    }
}
