package gx.zelda.figurines.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.figurines.model.FigurineCatalog
import gx.zelda.figurines.model.FigurinePrefs
import gx.zelda.figurines.model.FigurineProgress
import gx.zelda.figurines.model.FigurineTally
import gx.zelda.figurines.model.tally
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** An overview row for Carlov's figurines: owned of 136, shells held, each category's count and the next draw's odds. */
class FigurineProgressProvider(
    private val storage: FigurineStorage,
    private val catalog: CachedLoad<FigurineCatalog> = CachedLoad { FigurineRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.FIGURINES
    override val title = "Carlov's Figurines"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(catalog, progress, prefs) })
    }

    companion object {
        fun summarize(catalog: FigurineCatalog, progress: FigurineProgress, prefs: FigurinePrefs): ProgressSummary {
            val tally = catalog.tally(progress)
            return ProgressSummary(
                title = "Carlov's Figurines",
                done = tally.owned,
                total = tally.total,
                secondary = (listOf(countOf(progress.shells, "shell")) + tally.categories.map { "${it.category.name} ${it.owned} / ${it.total}" })
                    .joinToString(" · "),
                next = next(catalog, tally, progress, prefs),
            )
        }

        /**
         * What to win now; else a side task that opens one; else what moves the story on. It names
         * no figurine, and with *Hide figurines not yet available* on, no side task or later
         * checkpoint either, as the list hides them.
         */
        private fun next(catalog: FigurineCatalog, tally: FigurineTally, progress: FigurineProgress, prefs: FigurinePrefs): String {
            if (tally.owned == tally.total) return "Every figurine won"
            val left = tally.available - tally.owned
            if (left > 0) {
                val toWin = "${countOf(left, "figurine")} to win now"
                return if (progress.shells == 0) {
                    "$toWin: find Mysterious Shells to draw"
                } else {
                    "$toWin · ${tally.odds.actualChance()}% a draw with one shell"
                }
            }
            val reached = catalog.checkpointIndex(catalog.reached(progress).id)
            val waiting = catalog.figurines.filter {
                !progress.isOwned(it.number) && it.requirement != null && catalog.checkpointIndex(it.checkpoint) <= reached
            }
            if (waiting.isNotEmpty()) {
                return if (prefs.hideUnavailable) {
                    "${countOf(waiting.size, "figurine")} waiting on a side task"
                } else {
                    "${waiting.first().label} needs a side task: ${waiting.first().requirement}"
                }
            }
            val later = catalog.checkpoints.drop(reached + 1).firstOrNull { c ->
                catalog.figurines.any { it.checkpoint == c.id && !progress.isOwned(it.number) }
            }
            val untilLater = "Nothing new at Carlov's until the story moves on"
            return if (later == null || prefs.hideUnavailable) untilLater else "$untilLater: ${later.detail}"
        }
    }
}
