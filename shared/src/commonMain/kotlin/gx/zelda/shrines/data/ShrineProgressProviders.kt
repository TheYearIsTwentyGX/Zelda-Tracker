package gx.zelda.shrines.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ItemCount
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.shrines.model.OrbPlan
import gx.zelda.shrines.model.ShrineCatalog
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.ui.wheels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The shrine catalog the overview's Shrines and Hearts & Stamina rows share. */
fun shrineCatalogLoad(storage: ShrineStorage): CachedLoad<ShrineCatalog> = CachedLoad { ShrineRepository.load(storage.game) }

/** The overview's Shrines row: base-game shrines completed out of 120, with the DLC's counted apart. */
class ShrineProgressProvider(
    private val storage: ShrineStorage,
    private val catalog: CachedLoad<ShrineCatalog> = shrineCatalogLoad(storage),
) : ProgressProvider {
    override val kind = TrackerKind.SHRINES
    override val title = "Shrines"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(catalog, progress, prefs) })
    }

    companion object {
        fun summarize(catalog: ShrineCatalog, progress: ShrineProgress, prefs: ShrinePrefs): ProgressSummary {
            val base = catalog.base
            val done = catalog.baseCompleted(progress.completed)
            val found = catalog.shrines.count { progress.isFound(it.id) && (prefs.showDlc || !it.dlc) }
            val dlc = catalog.dlc
            val details = listOfNotNull(
                "Found $found",
                if (prefs.showDlc && dlc.isNotEmpty()) "DLC ${dlc.count { it.id in progress.completed }} / ${dlc.size}" else null,
            )
            return ProgressSummary(
                title = "Shrines",
                done = done,
                total = base.size,
                secondary = details.joinToString(" · "),
                next = next(catalog, progress, prefs),
                mapItems = ItemCount("shrines", done, base.size),
            )
        }

        /**
         * A found shrine not yet completed, since its name is no spoiler; otherwise the region
         * closest to done, naming its next shrine only while spoilers are shown.
         */
        private fun next(catalog: ShrineCatalog, progress: ShrineProgress, prefs: ShrinePrefs): String? {
            val left = (if (prefs.showDlc) catalog.shrines else catalog.base).filter { it.id !in progress.completed }
            left.firstOrNull { progress.isFound(it.id) }?.let { return "Complete ${it.name} Shrine, found in ${it.region}" }
            val (region, inRegion) = catalog.byRegion(left).minByOrNull { it.second.size } ?: return null
            val more = countOf(inRegion.size, "shrine")
            return if (prefs.hideUndiscovered) {
                "$more left in the $region Tower region"
            } else {
                "${inRegion.first().name} Shrine in the $region Tower region ($more left there)"
            }
        }
    }
}

/**
 * The overview's Hearts & Stamina row, from the Spirit Orb plan: upgrades bought out of what
 * every orb buys. Freed Divine Beasts come from the Main Quest tracker, as on the Shrines screen.
 */
class SpiritOrbProgressProvider(
    private val storage: ShrineStorage,
    /** Null for a game without the Main Quest tracker. */
    private val mainQuest: MainQuestStorage?,
    private val catalog: CachedLoad<ShrineCatalog> = shrineCatalogLoad(storage),
) : ProgressProvider {
    override val kind = TrackerKind.SHRINES
    override val title = "Hearts & Stamina"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        val beasts = mainQuest?.divineBeastsFreed(saveFile.id)?.map { it.size } ?: flowOf(0)
        emitAll(
            combine(storage.progress(saveFile.id), storage.prefs, beasts) { progress, prefs, freed ->
                summarize(OrbPlan.of(catalog, progress, includeDlc = prefs.showDlc, beastsFreed = freed))
            },
        )
    }

    companion object {
        fun summarize(plan: OrbPlan): ProgressSummary = ProgressSummary(
            title = "Hearts & Stamina",
            done = plan.bought,
            total = plan.totalUpgrades,
            secondary = "${countOf(plan.hearts, "heart")} · " +
                "${wheels(plan.upgrades, plan.staminaBought)} of ${wheels(plan.upgrades, plan.upgrades.maxStaminaVessels)} stamina wheels",
            next = when {
                !plan.isConsistent -> "More upgrades entered than the orbs earned pay for; check the Spirit Orbs card"
                plan.readyToBuy > 0 -> "Pray at a Goddess Statue: ${countOf(plan.readyToBuy, "upgrade")} ready to buy"
                plan.upgradesLeft == 0 -> null
                else -> "${countOf(plan.toNext, "more orb")} for the next upgrade"
            },
        )
    }
}
