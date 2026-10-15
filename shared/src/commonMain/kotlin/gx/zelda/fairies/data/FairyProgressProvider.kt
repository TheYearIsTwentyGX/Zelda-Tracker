package gx.zelda.fairies.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.fairies.model.FairyCatalog
import gx.zelda.fairies.ui.rupees
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The overview's Great Fairies row: fountains open, how far armor can be upgraded, and what the next one costs. */
class FairyProgressProvider(
    private val storage: FairyStorage,
    private val catalog: CachedLoad<FairyCatalog> = CachedLoad { FairyRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.FAIRIES
    override val title = "Great Fairy Fountains"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(storage.unlocked(saveFile.id).map { summarize(catalog, it) })
    }

    companion object {
        fun summarize(catalog: FairyCatalog, unlocked: Set<String>): ProgressSummary {
            val open = catalog.unlockedCount(unlocked)
            return ProgressSummary(
                title = "Great Fairy Fountains",
                done = open,
                total = catalog.fairies.size,
                secondary = if (open == 0) "Armor can't be upgraded yet" else "Armor upgrades up to ★$open",
                next = catalog.nextCost(open)?.let { "The next fountain costs ${rupees(it)}" },
            )
        }
    }
}
