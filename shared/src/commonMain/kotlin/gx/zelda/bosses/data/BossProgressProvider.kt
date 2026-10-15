package gx.zelda.bosses.data

import gx.zelda.bosses.model.BossCatalog
import gx.zelda.bosses.model.BossKindTally
import gx.zelda.bosses.model.tallies
import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The overview's Overworld Bosses row: spawns defeated on the save file (DLC included), and Kilton's medals. */
class BossProgressProvider(
    private val storage: BossStorage,
    private val catalog: CachedLoad<BossCatalog> = CachedLoad { BossRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.BOSSES
    override val title = "Overworld Bosses"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(storage.progress(saveFile.id).map { summarize(catalog.tallies(saveFile, it)) })
    }

    companion object {
        /**
         * The next medal still to earn, by its tab's headline. It names a kind, never a spawn, so it
         * needs no spoiler check.
         */
        fun summarize(tallies: List<BossKindTally>): ProgressSummary = ProgressSummary(
            title = "Overworld Bosses",
            done = tallies.sumOf { it.defeated },
            total = tallies.sumOf { it.total },
            secondary = tallies.joinToString(" · ") { "${it.kind.name} ${it.defeated} / ${it.total}" },
            next = tallies.firstOrNull { it.kind.medal != null && it.medalDefeated < it.medalTotal }?.headline
                ?: "All Medals of Honor earned",
        )
    }
}
