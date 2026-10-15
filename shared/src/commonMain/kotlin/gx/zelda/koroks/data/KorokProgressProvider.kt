package gx.zelda.koroks.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ItemCount
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.koroks.model.HestuPlan
import gx.zelda.koroks.model.KorokCatalog
import gx.zelda.koroks.model.KorokPrefs
import gx.zelda.koroks.model.KorokProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** The overview's Korok Seeds row: seeds collected out of 900, and Hestu's upgrades. */
class KorokProgressProvider(
    private val storage: KorokStorage,
    private val catalog: CachedLoad<KorokCatalog> = CachedLoad { KorokRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.KOROKS
    override val title = "Korok Seeds"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(catalog, progress, prefs) })
    }

    companion object {
        fun summarize(catalog: KorokCatalog, progress: KorokProgress, prefs: KorokPrefs): ProgressSummary {
            val hestu = HestuPlan.of(catalog, progress)
            val upgrades = catalog.hestu.pouches.sumOf { it.costs.size }
            val bought = catalog.hestu.pouches.sumOf { hestu.boughtIn(it) }
            return ProgressSummary(
                title = "Korok Seeds",
                done = hestu.collected,
                total = hestu.total,
                secondary = "Found ${catalog.koroks.count { progress.isFound(it.id) }} · Hestu upgrades $bought / $upgrades",
                next = next(catalog, progress, prefs, hestu),
                mapItems = ItemCount("Korok seeds", hestu.collected, hestu.total),
            )
        }

        /**
         * Hestu first when the seeds in hand buy something; then a found Korok whose seed is
         * still to collect; then the region closest to done, naming a place only while spoilers
         * are shown (a hidden row shows only its region).
         */
        private fun next(catalog: KorokCatalog, progress: KorokProgress, prefs: KorokPrefs, hestu: HestuPlan): String? {
            if (hestu.isConsistent && hestu.affordable > 0) {
                return "Visit Hestu: your seeds buy ${countOf(hestu.affordable, "upgrade")}"
            }
            val left = catalog.koroks.filter { it.id !in progress.completed }
            left.firstOrNull { progress.isFound(it.id) }?.let {
                return "Collect the seed you found near ${it.nearestPlace} (${it.region})"
            }
            val (region, inRegion) = catalog.byRegion(left).minByOrNull { it.second.size } ?: return null
            val more = countOf(inRegion.size, "seed")
            return if (prefs.hideUndiscovered) {
                "$more left in the $region Tower region"
            } else {
                "A seed near ${inRegion.first().nearestPlace} in the $region Tower region ($more left there)"
            }
        }
    }
}
