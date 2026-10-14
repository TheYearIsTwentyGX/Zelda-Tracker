package gx.zelda.shrines.scan

import kotlin.test.Test
import kotlin.test.assertEquals

class MergeReadingsTest {
    private val completed = SpotReading(ScanVerdict.COMPLETED)
    private val notDone = SpotReading(ScanVerdict.NOT_DONE)
    private val absent = SpotReading(ScanVerdict.NOT_PRESENT)
    private val edge = SpotReading.unsure(UnsureReason.SCREEN_EDGE)
    private val outside = SpotReading.unsure(UnsureReason.NOT_IN_SHOT)
    private val covered = SpotReading.unsure(UnsureReason.COVERED, "Covered by a stamp")
    private val partialIcon = SpotReading.unsure(UnsureReason.COVERED, "Only part of an icon is visible", iconSeen = true)

    @Test
    fun aDefiniteReadingBeatsTheEdgeAndOtherShots() {
        assertEquals(completed, mergeReadings(listOf(edge, completed)))
        assertEquals(notDone, mergeReadings(listOf(notDone, outside)))
        assertEquals(absent, mergeReadings(listOf(outside, edge, absent)))
    }

    @Test
    fun aDefiniteReadingBeatsACoveredSpot() {
        assertEquals(completed, mergeReadings(listOf(covered, completed)))
        assertEquals(absent, mergeReadings(listOf(absent, covered)))
    }

    @Test
    fun disagreeingDefiniteReadingsAreUnsure() {
        for (pair in listOf(listOf(completed, notDone), listOf(completed, absent), listOf(absent, notDone))) {
            val merged = mergeReadings(pair)
            assertEquals(ScanVerdict.UNSURE, merged.verdict)
            assertEquals(UnsureReason.CONFLICT, merged.reason)
        }
    }

    @Test
    fun emptyIsUnsureWhenAnotherShotSawPartOfAnIcon() {
        assertEquals(UnsureReason.CONFLICT, mergeReadings(listOf(absent, partialIcon)).reason)
        assertEquals(completed, mergeReadings(listOf(completed, partialIcon)))
    }

    @Test
    fun withNothingDefiniteTheReasonSaysWhy() {
        assertEquals(UnsureReason.SCREEN_EDGE, mergeReadings(listOf(edge, outside)).reason)
        assertEquals(UnsureReason.NOT_IN_SHOT, mergeReadings(listOf(outside, outside)).reason)
        assertEquals(covered, mergeReadings(listOf(outside, covered)))
        val stacked = SpotReading.unsure(UnsureReason.STACKED, iconSeen = true)
        assertEquals(stacked, mergeReadings(listOf(covered, stacked)))
    }
}
