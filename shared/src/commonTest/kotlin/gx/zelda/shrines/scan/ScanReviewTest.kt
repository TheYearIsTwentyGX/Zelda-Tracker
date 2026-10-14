package gx.zelda.shrines.scan

import gx.zelda.shrines.model.OrbUpgrades
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState
import gx.zelda.shrines.model.TrialType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanReviewTest {
    private fun shrine(id: String, dlc: Boolean = false) =
        Shrine(id, id, "Great Plateau", "Trial", TrialType.PUZZLE, dlc = dlc, location = "here")

    private val catalog = ShrineCatalog(
        regions = listOf("Great Plateau"),
        upgrades = OrbUpgrades(4, 3, 30, 1, 10, 4),
        shrines = listOf("a", "b", "c", "d", "e", "f", "g", "h").map { shrine(it) } + shrine("dlc", dlc = true),
    )

    private val readings = mapOf(
        "a" to SpotReading(ScanVerdict.COMPLETED),
        "b" to SpotReading(ScanVerdict.COMPLETED),
        "c" to SpotReading(ScanVerdict.NOT_DONE),
        "d" to SpotReading(ScanVerdict.NOT_DONE),
        "e" to SpotReading(ScanVerdict.NOT_PRESENT),
        "f" to SpotReading.unsure(UnsureReason.COVERED),
        "g" to SpotReading.unsure(UnsureReason.NOT_IN_SHOT),
        "h" to SpotReading(ScanVerdict.NOT_PRESENT),
        "dlc" to SpotReading(ScanVerdict.COMPLETED),
    )

    // b is already completed, d is completed although the map says not done, h is found.
    private val progress = ShrineProgress()
        .withState("b", ShrineState.COMPLETED)
        .withState("d", ShrineState.COMPLETED)
        .withState("h", ShrineState.FOUND)

    private val review = ScanReview.build(catalog, readings, progress, includeDlc = true)

    @Test
    fun sortsReadingsIntoSections() {
        assertEquals(listOf("a", "dlc"), review.section(ReviewSection.COMPLETED).map { it.shrine.id })
        assertEquals(listOf("c"), review.section(ReviewSection.NOT_DONE).map { it.shrine.id })
        assertEquals(listOf("f"), review.section(ReviewSection.UNSURE).map { it.shrine.id })
        assertEquals(listOf("g"), review.section(ReviewSection.OFF_SCREEN).map { it.shrine.id })
        assertEquals(listOf("e", "h"), review.notPresent.map { it.id })
        assertEquals(listOf("d"), review.keptCompleted.map { it.id })
        // b (completed both ways) and e (empty and undiscovered here).
        assertEquals(2, review.upToDate)
        assertEquals(3, review.counts[ScanVerdict.COMPLETED])
    }

    @Test
    fun suggestsCompletedAndNotDoneAndLeavesUnsureAlone() {
        val selections = review.initialSelections
        assertEquals(ScanChoice.COMPLETED, selections["a"])
        assertEquals(ScanChoice.FOUND, selections["c"])
        assertEquals(ScanChoice.LEAVE, selections["f"])
        assertEquals(ScanChoice.LEAVE, selections["g"])
        assertEquals(mapOf("a" to ShrineState.COMPLETED, "dlc" to ShrineState.COMPLETED, "c" to ShrineState.FOUND), review.changes(progress, selections))
    }

    @Test
    fun applyingWritesOnlyTheSelectedChanges() {
        val applied = review.applyTo(progress, review.initialSelections + ("f" to ScanChoice.COMPLETED) + ("a" to ScanChoice.LEAVE))
        assertEquals(ShrineState.UNDISCOVERED, applied.state("a"))
        assertEquals(ShrineState.FOUND, applied.state("c"))
        assertEquals(ShrineState.COMPLETED, applied.state("f"))
        // Not present is never written, and what was tracked stays.
        assertEquals(ShrineState.FOUND, applied.state("h"))
        assertEquals(ShrineState.UNDISCOVERED, applied.state("e"))
        assertEquals(ShrineState.COMPLETED, applied.state("d"))
    }

    @Test
    fun neverDowngradesACompletedShrine() {
        // c was completed in the tracker after the scan ran; its "Found" suggestion must not undo that.
        val later = progress.withState("c", ShrineState.COMPLETED)
        val applied = review.applyTo(later, review.initialSelections)
        assertEquals(ShrineState.COMPLETED, applied.state("c"))
        assertTrue("c" !in review.changes(later, review.initialSelections))
        // Completed shrines aren't offered for review at all, even when unsure.
        val unsureCompleted = ScanReview.build(catalog, readings + ("b" to SpotReading.unsure(UnsureReason.COVERED)), progress, true)
        assertTrue(unsureCompleted.items.none { it.shrine.id == "b" })
    }

    @Test
    fun foundShrinesOnlyOfferCompletingWhenUnsure() {
        val found = progress.withState("f", ShrineState.FOUND)
        val item = ScanReview.build(catalog, readings, found, true).items.single { it.shrine.id == "f" }
        assertEquals(listOf(ScanChoice.LEAVE, ScanChoice.COMPLETED), item.options)
    }

    @Test
    fun hiddenDlcIsLeftOut() {
        val noDlc = ScanReview.build(catalog, readings, progress, includeDlc = false)
        assertTrue(noDlc.items.none { it.shrine.dlc })
        assertEquals(2, noDlc.counts[ScanVerdict.COMPLETED])
    }
}
