package gx.zelda.potions.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.potions.model.PotionBook
import gx.zelda.upgrades.data.UpgradeRepository
import gx.zelda.upgrades.data.UpgradeStorage
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The overview's Potion Infusions row: recipe steps made (each base potion bought, each infusion),
 * the potions complete, and the next step with who does it. Its progress is Item Upgrades' potions,
 * so the two rows agree; this one counts every potion whatever Item Upgrades' Potions chip says.
 */
class PotionProgressProvider(
    private val storage: UpgradeStorage,
    override val title: String = "Potion Infusions",
    private val catalog: CachedLoad<UpgradeCatalog> = CachedLoad { UpgradeRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.POTIONS

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(storage.progress(saveFile.id).map { summarize(catalog, it, title) })
    }

    companion object {
        fun summarize(catalog: UpgradeCatalog, progress: UpgradeProgress, title: String = "Potion Infusions"): ProgressSummary {
            val potions = PotionBook.potions(catalog)
            val complete = potions.count { PotionBook.isDone(it, progress) }
            val next = potions.firstNotNullOfOrNull { potion -> PotionBook.recipe(potion, progress).firstOrNull { !it.made } }
            return ProgressSummary(
                title = title,
                done = potions.sumOf { PotionBook.made(it, progress) },
                total = potions.sumOf { PotionBook.steps(it) },
                secondary = "${countOf(complete, "potion")} fully infused of ${potions.size}",
                next = next?.let { "${it.name} (${it.by})" } ?: "All done",
            )
        }
    }
}
