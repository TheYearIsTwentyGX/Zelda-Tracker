package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * The overview row for any checklist: steps done of all, the file's summaries and each section's
 * count, and the next thing to do. Every game made of checklists gets its rows from this.
 */
class ChecklistProgressProvider(
    private val storage: ChecklistStorage,
    override val title: String,
    private val catalog: CachedLoad<ChecklistCatalog> = CachedLoad { ChecklistRepository.load(storage.dataFile) },
    /** What the story has unlocked on a save file; see [StoryProgress]. */
    private val storyGate: (String) -> Flow<StoryGate> = { flowOf(StoryGate.OFF) },
) : ProgressProvider {
    override val kind = TrackerKind.CHECKLIST
    override val dataFile: String get() = storage.dataFile

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(
            combine(storage.progress(saveFile.id), storage.prefs, storyGate(saveFile.id)) { progress, prefs, gate ->
                summarize(title, catalog, progress, prefs, gate)
            },
        )
    }

    companion object {
        /**
         * The secondary line lists the summaries, then each section's count when there's more than
         * one, then how many the story hasn't unlocked. With spoilers hidden, the next step names
         * only how many are left where it is. The bar always counts everything, locked or not.
         */
        fun summarize(
            title: String,
            catalog: ChecklistCatalog,
            progress: ChecklistProgress,
            prefs: ChecklistPrefs,
            gate: StoryGate = StoryGate.OFF,
        ): ProgressSummary {
            val count = catalog.count(progress)
            val isLocked = { item: ChecklistItem -> gate.isLocked(item, progress) }
            val locked = catalog.counted.count(isLocked)
            val parts = catalog.summaries.map { catalog.evaluate(it, progress).text } +
                if (catalog.sections.size > 1) {
                    catalog.sections.map { s -> catalog.count(progress, s.items).let { "${s.title} ${it.done} / ${it.total}" } }
                } else {
                    emptyList()
                }
            val detail = parts + listOfNotNull("$locked unlock later".takeIf { locked > 0 })
            val next = catalog.next(progress, isLocked)
            return ProgressSummary(
                title = title,
                done = count.done,
                total = count.total,
                secondary = detail.joinToString(" · ").ifEmpty { null },
                next = when {
                    next == null && locked > 0 -> "Everything unlocked is done · $locked unlock later in the story"
                    next == null -> "All done"
                    prefs.hideUnchecked && !progress.isStarted(next) -> {
                        val section = catalog.sectionOf(next)
                        val group = catalog.groupOf(next)
                        val left = section.items.count { catalog.groupOf(it) == group && !progress.isDone(it) && !isLocked(it) }
                        "${countOf(left, catalog.nounOf(section), catalog.pluralOf(section))} still to do in $group"
                    }
                    next.region != null -> "${next.name}, in ${next.region}"
                    else -> next.name
                },
            )
        }
    }
}
