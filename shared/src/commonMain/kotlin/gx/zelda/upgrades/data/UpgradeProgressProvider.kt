package gx.zelda.upgrades.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.logic.thousands
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The overview's Item Upgrades row: items obtained, upgrades done of the total, and the materials
 * and Rupees still needed to finish everything. It counts one copy of each pouch item and leaves
 * out the sections the tracker's chips turn off, as its totals do.
 */
class UpgradeProgressProvider(
    private val storage: UpgradeStorage,
    override val title: String = "Item Upgrades",
    private val catalog: CachedLoad<UpgradeCatalog> = CachedLoad { UpgradeRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.ITEM_UPGRADES

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(catalog, progress, prefs, title) })
    }

    companion object {
        fun summarize(catalog: UpgradeCatalog, progress: UpgradeProgress, prefs: UpgradePrefs, title: String = "Item Upgrades"): ProgressSummary {
            val items = UpgradeCalculator.visibleItems(catalog, prefs)
            val obtained = items.count { item -> progress.copiesOf(item).any(progress::owned) }
            val (done, total) = UpgradeCalculator.upgradeCount(catalog, progress, prefs)
            val left = UpgradeCalculator.totals(catalog, progress, prefs, UpgradeTotalsMode.ALL)
            val needed = if (left.materialCount == 0 && left.rupees == 0) {
                "nothing left to gather"
            } else {
                "${countOf(left.materialCount, "material")} and ${thousands(left.rupees)} Rupees to go"
            }
            // The first upgrade left on something you have, in list order.
            val next = items.firstNotNullOfOrNull { item ->
                UpgradeCalculator.countedCopy(item, progress, UpgradeTotalsMode.COLLECTED)
                    ?.let { UpgradeCalculator.remaining(it, progress).firstOrNull() }
            }
            return ProgressSummary(
                title = title,
                done = obtained,
                total = items.size,
                secondary = "Upgrades $done / $total · $needed",
                next = next?.let { "${it.name} (${it.step.by})" },
            )
        }
    }
}
