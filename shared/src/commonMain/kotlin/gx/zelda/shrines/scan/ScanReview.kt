package gx.zelda.shrines.scan

import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineState

/** What to do with one shrine when the review is applied. */
enum class ScanChoice(val label: String) {
    LEAVE("Leave"),
    FOUND("Found"),
    COMPLETED("Done"),
}

enum class ReviewSection {
    /** On the map with a blue glyph, not yet ticked here. */
    COMPLETED,

    /** On the map with an orange glyph, not yet marked found here. */
    NOT_DONE,

    /** In a screenshot but couldn't be read: covered, stacked, or the shots disagree. */
    UNSURE,

    /** Outside every screenshot, or only ever at the screen edge. */
    OFF_SCREEN,
}

/** A shrine the review lists, what the scan suggests for it, and what the user may pick instead. */
data class ReviewItem(
    val shrine: Shrine,
    val reading: SpotReading,
    val current: ShrineState,
    val section: ReviewSection,
    val suggested: ScanChoice,
    val options: List<ScanChoice>,
)

/**
 * The scan's suggestions for one save file. Nothing here is written until [applyTo]; Completed and
 * Not done readings are pre-selected, Unsure ones are left for the user, Not on the map is never
 * written, and a shrine already completed here is never downgraded.
 */
class ScanReview(
    val items: List<ReviewItem>,
    /** Shrines whose spot was clearly empty. */
    val notPresent: List<Shrine>,
    /** Readings per verdict, over the shrines the review covers. */
    val counts: Map<ScanVerdict, Int>,
    /** Shrines where the map agrees with what's tracked already. */
    val upToDate: Int,
    /** Completed here, but the map shows them unfinished or missing; left as Completed. */
    val keptCompleted: List<Shrine>,
) {
    fun section(section: ReviewSection): List<ReviewItem> = items.filter { it.section == section }

    val initialSelections: Map<String, ScanChoice> get() = items.associate { it.shrine.id to it.suggested }

    /** The changes [selections] would make to [progress], as shrine id to new state. */
    fun changes(progress: ShrineProgress, selections: Map<String, ScanChoice>): Map<String, ShrineState> =
        items.mapNotNull { item ->
            val current = progress.state(item.shrine.id)
            val target = when (selections[item.shrine.id] ?: ScanChoice.LEAVE) {
                ScanChoice.LEAVE -> null
                ScanChoice.FOUND -> ShrineState.FOUND.takeIf { current == ShrineState.UNDISCOVERED }
                ScanChoice.COMPLETED -> ShrineState.COMPLETED.takeIf { current != ShrineState.COMPLETED }
            }
            target?.let { item.shrine.id to it }
        }.toMap()

    /**
     * [progress] with [selections] applied. Choices are checked against [progress] itself, so a
     * shrine completed since the scan ran is still never downgraded.
     */
    fun applyTo(progress: ShrineProgress, selections: Map<String, ScanChoice>): ShrineProgress =
        changes(progress, selections).entries.fold(progress) { p, (id, state) -> p.withState(id, state) }

    companion object {
        /** [includeDlc] false leaves the Champions' Ballad shrines out, as the tracker does when hiding them. */
        fun build(
            catalog: ShrineCatalog,
            readings: Map<String, SpotReading>,
            progress: ShrineProgress,
            includeDlc: Boolean,
        ): ScanReview {
            val items = mutableListOf<ReviewItem>()
            val notPresent = mutableListOf<Shrine>()
            val kept = mutableListOf<Shrine>()
            val counts = mutableMapOf<ScanVerdict, Int>()
            var upToDate = 0
            for (shrine in catalog.shrines) {
                if (shrine.dlc && !includeDlc) continue
                val reading = readings[shrine.id] ?: continue
                counts[reading.verdict] = (counts[reading.verdict] ?: 0) + 1
                val current = progress.state(shrine.id)
                fun item(section: ReviewSection, suggested: ScanChoice, options: List<ScanChoice>) {
                    items += ReviewItem(shrine, reading, current, section, suggested, options)
                }
                val completed = current == ShrineState.COMPLETED
                when (reading.verdict) {
                    ScanVerdict.COMPLETED ->
                        if (completed) upToDate++ else item(ReviewSection.COMPLETED, ScanChoice.COMPLETED, listOf(ScanChoice.LEAVE, ScanChoice.COMPLETED))
                    ScanVerdict.NOT_DONE -> when (current) {
                        ShrineState.COMPLETED -> kept += shrine
                        ShrineState.FOUND -> upToDate++
                        ShrineState.UNDISCOVERED -> item(ReviewSection.NOT_DONE, ScanChoice.FOUND, listOf(ScanChoice.LEAVE, ScanChoice.FOUND))
                    }
                    ScanVerdict.NOT_PRESENT -> {
                        notPresent += shrine
                        when (current) {
                            ShrineState.COMPLETED -> kept += shrine
                            ShrineState.UNDISCOVERED -> upToDate++
                            ShrineState.FOUND -> Unit
                        }
                    }
                    ScanVerdict.UNSURE -> if (!completed) {
                        val offScreen = reading.reason == UnsureReason.NOT_IN_SHOT || reading.reason == UnsureReason.SCREEN_EDGE
                        val options = if (current == ShrineState.UNDISCOVERED) ScanChoice.entries else listOf(ScanChoice.LEAVE, ScanChoice.COMPLETED)
                        item(if (offScreen) ReviewSection.OFF_SCREEN else ReviewSection.UNSURE, ScanChoice.LEAVE, options)
                    }
                }
            }
            return ScanReview(items, notPresent, counts, upToDate, kept)
        }
    }
}
