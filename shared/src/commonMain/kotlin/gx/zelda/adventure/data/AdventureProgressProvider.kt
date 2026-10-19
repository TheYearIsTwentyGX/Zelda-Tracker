package gx.zelda.adventure.data

import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.AdventureNext
import gx.zelda.adventure.model.AdventurePrefs
import gx.zelda.adventure.model.AdventureProgress
import gx.zelda.adventure.model.AdventureSpoilers
import gx.zelda.adventure.model.AdventureTotals
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Tally
import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The overview row for one Adventure Mode map: everything done of all (two battle steps per square
 * plus every reward, Skulltula and search), the squares cleared and A-ranked and the collectibles,
 * and the next thing to do.
 */
class AdventureProgressProvider(
    private val storage: AdventureStorage,
    override val title: String,
    private val map: CachedLoad<AdventureMap> = CachedLoad { AdventureRepository.load(storage.dataFile) },
) : ProgressProvider {
    override val kind = TrackerKind.ADVENTURE_MAP
    override val dataFile: String get() = storage.dataFile

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val map = map.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(title, map, progress, prefs) })
    }

    companion object {
        fun summarize(title: String, map: AdventureMap, progress: AdventureProgress, prefs: AdventurePrefs): ProgressSummary {
            val totals = AdventureTotals(map, progress)
            fun part(label: String, t: Tally) = "$label ${t.done}/${t.total}"
            val parts = listOfNotNull(
                part("Cleared", totals.cleared),
                "A-rank ${totals.aRanked.done}",
                totals.rewards(RewardType.HEART_CONTAINER).takeIf { it.total > 0 }?.let { part("Heart Containers", it) },
                totals.rewards(RewardType.HEART_PIECE).takeIf { it.total > 0 }?.let { part("Pieces", it) },
                totals.skulltulas.takeIf { it.total > 0 }?.let { part("Skulltulas", it) },
                totals.rewards(RewardType.WEAPON).takeIf { it.total > 0 }?.let { part("Weapons", it) },
            )
            return ProgressSummary(
                title = title,
                done = totals.all.done,
                total = totals.all.total,
                secondary = parts.joinToString(" · "),
                next = AdventureNext.line(map, progress, AdventureSpoilers(map, progress, prefs.spoilerSafe)),
            )
        }
    }
}
