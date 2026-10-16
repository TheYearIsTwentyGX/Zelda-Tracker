package gx.zelda.kinstones.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.kinstones.model.FusionReward
import gx.zelda.kinstones.model.KinstoneCatalog
import gx.zelda.kinstones.model.KinstoneProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** An overview row for Kinstone Fusions: fusions done, with the Gold and heart tallies. */
class KinstoneProgressProvider(
    private val storage: KinstoneStorage,
    private val catalog: CachedLoad<KinstoneCatalog> = CachedLoad { KinstoneRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.KINSTONES
    override val title = "Kinstone Fusions"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(storage.progress(saveFile.id).map { summarize(catalog, it) })
    }

    companion object {
        fun summarize(catalog: KinstoneCatalog, progress: KinstoneProgress): ProgressSummary {
            val gold = catalog.fusions.filter { it.isGold }
            val hearts = catalog.fusions.filter { FusionReward.HEART in it.rewards }
            return ProgressSummary(
                title = "Kinstone Fusions",
                done = catalog.fusedCount(progress.fused),
                total = catalog.fusions.size,
                secondary = "Gold ${gold.count { progress.isFused(it.id) }} / ${gold.size} · " +
                    "Hearts ${hearts.count { progress.isFused(it.id) }} / ${hearts.size}",
                next = next(catalog, progress),
            )
        }

        /**
         * The fixed fusion left that the story allows soonest, in list order. It names a partner,
         * never a result, and the list shows partners even with results hidden, so it needs no
         * spoiler check.
         */
        private fun next(catalog: KinstoneCatalog, progress: KinstoneProgress): String? {
            val left = catalog.fusions.filter { !progress.isFused(it.id) }
            val fusion = catalog.byRegion(left.filter { !it.random }).flatMap { it.second }.minByOrNull { it.stage }
                ?: return left.firstOrNull()?.let { "${countOf(left.size, "random fusion")} left: fuse with anyone who offers" }
            return "Fuse with ${fusion.partner} (${fusion.region}) · ${countOf(left.size, "fusion")} left"
        }
    }
}
