package gx.zelda.hearts.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.hearts.model.HeartCatalog
import gx.zelda.hearts.model.HeartPrefs
import gx.zelda.hearts.model.HeartProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** The overview's Heart Pieces row: pieces and containers collected, and the hearts they add up to. */
class HeartProgressProvider(
    private val storage: HeartStorage,
    private val catalog: CachedLoad<HeartCatalog> = CachedLoad { HeartRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.HEART_PIECES
    override val title = "Heart Pieces"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(catalog, progress, prefs) })
    }

    companion object {
        /** With spoilers hidden, the next step names only the region of the next uncollected entry. */
        fun summarize(catalog: HeartCatalog, progress: HeartProgress, prefs: HeartPrefs): ProgressSummary {
            val total = catalog.total(progress)
            val next = catalog.entries.firstOrNull { !progress.isCollected(it.id) }
            return ProgressSummary(
                title = "Heart Pieces",
                done = total.collected,
                total = total.total,
                secondary = "${total.heartsLabel} of ${total.maxHearts} · " +
                    "Pieces ${total.pieces} / ${total.piecesTotal} · Containers ${total.containers} / ${total.containersTotal}",
                next = when {
                    next == null -> "Every heart collected"
                    prefs.hideUncollected -> {
                        val left = catalog.entries.count { it.region == next.region && !progress.isCollected(it.id) }
                        "${countOf(left, "heart")} still to find in ${next.region}"
                    }
                    else -> "${next.name}, in ${next.region}"
                },
            )
        }
    }
}
