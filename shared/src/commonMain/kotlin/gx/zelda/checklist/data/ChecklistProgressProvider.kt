package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistPrefs
import gx.zelda.checklist.model.ChecklistProgress
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

/**
 * The overview row for any checklist: steps done of all, the file's summaries and each section's
 * count, and the next thing to do. Every game made of checklists gets its rows from this.
 */
class ChecklistProgressProvider(
    private val storage: ChecklistStorage,
    override val title: String,
    private val catalog: CachedLoad<ChecklistCatalog> = CachedLoad { ChecklistRepository.load(storage.dataFile) },
) : ProgressProvider {
    override val kind = TrackerKind.CHECKLIST
    override val dataFile: String get() = storage.dataFile

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(title, catalog, progress, prefs) })
    }

    companion object {
        /**
         * The secondary line lists the summaries, then each section's count when there's more than
         * one. With spoilers hidden, the next step names only how many are left where it is.
         */
        fun summarize(title: String, catalog: ChecklistCatalog, progress: ChecklistProgress, prefs: ChecklistPrefs): ProgressSummary {
            val count = catalog.count(progress)
            val parts = catalog.summaries.map { catalog.evaluate(it, progress).text } +
                if (catalog.sections.size > 1) {
                    catalog.sections.map { s -> catalog.count(progress, s.items).let { "${s.title} ${it.done} / ${it.total}" } }
                } else {
                    emptyList()
                }
            val next = catalog.next(progress)
            return ProgressSummary(
                title = title,
                done = count.done,
                total = count.total,
                secondary = parts.joinToString(" · ").ifEmpty { null },
                next = when {
                    next == null -> "All done"
                    prefs.hideUnchecked && !progress.isStarted(next) -> {
                        val section = catalog.sectionOf(next)
                        val group = catalog.groupOf(next)
                        val left = section.items.count { catalog.groupOf(it) == group && !progress.isDone(it) }
                        "${countOf(left, catalog.nounOf(section), catalog.pluralOf(section))} still to do in $group"
                    }
                    next.region != null -> "${next.name}, in ${next.region}"
                    else -> next.name
                },
            )
        }
    }
}
