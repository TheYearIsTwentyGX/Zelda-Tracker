package gx.zelda.armor.data

import gx.zelda.armor.model.ArmorCatalog
import gx.zelda.armor.model.ArmorPrefs
import gx.zelda.armor.model.ArmorProgress
import gx.zelda.armor.model.PieceProgress
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
 * The overview's Armor row: pieces collected, and how many are fully upgraded. Counts the pieces
 * the armor list shows, so its DLC and amiibo filters apply here too.
 */
class ArmorProgressProvider(
    private val storage: ArmorStorage,
    private val catalog: CachedLoad<ArmorCatalog> = CachedLoad { ArmorRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.ARMOR
    override val title = "Armor"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        // Progress from before save files existed only shows up once it's moved; the armor tracker does the same.
        storage.migrateLegacy()
        val catalog = catalog.get()
        emitAll(combine(storage.pieces(saveFile.id), storage.prefs) { pieces, prefs -> summarize(catalog, pieces, prefs) })
    }

    companion object {
        fun summarize(catalog: ArmorCatalog, pieces: Map<String, PieceProgress>, prefs: ArmorPrefs): ProgressSummary {
            val progress = ArmorProgress.of(pieces, prefs)
            val visible = catalog.pieces.filter { progress.isVisible(it.source) }
            val owned = visible.filter { progress.of(it.id).owned }
            val upgradable = visible.filter { it.isUpgradable }
            val maxed = owned.count { it.isUpgradable && progress.of(it.id).level >= it.maxLevel }
            return ProgressSummary(
                title = "Armor",
                done = owned.size,
                total = visible.size,
                secondary = "Fully upgraded $maxed / ${upgradable.size}",
            )
        }
    }
}
