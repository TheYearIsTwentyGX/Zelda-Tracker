package gx.zelda.figurines.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.figurines.model.FigurineCatalog
import gx.zelda.figurines.model.FigurineTally
import gx.zelda.figurines.model.tally
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** An overview row for Carlov's figurines: owned of 136, each category's count and the next draw's odds. */
class FigurineProgressProvider(
    private val storage: FigurineStorage,
    private val catalog: CachedLoad<FigurineCatalog> = CachedLoad { FigurineRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.FIGURINES
    override val title = "Carlov's Figurines"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(storage.progress(saveFile.id).map { summarize(catalog.tally(it)) })
    }

    companion object {
        /** The next step names no figurine, so it needs no spoiler check. */
        fun summarize(tally: FigurineTally): ProgressSummary {
            val odds = tally.odds
            val left = tally.available - tally.owned
            return ProgressSummary(
                title = "Carlov's Figurines",
                done = tally.owned,
                total = tally.total,
                secondary = tally.categories.joinToString(" · ") { "${it.category.name} ${it.owned} / ${it.total}" },
                next = when {
                    tally.owned == tally.total -> "Every figurine won"
                    odds.complete -> "Nothing new at Carlov's until the story moves on"
                    else -> "${countOf(left, "figurine")} to win now · ${odds.actualChance()}% a draw with one shell"
                },
            )
        }
    }
}
