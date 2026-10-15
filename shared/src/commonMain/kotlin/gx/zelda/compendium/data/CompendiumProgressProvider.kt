package gx.zelda.compendium.data

import gx.zelda.compendium.model.CompendiumCatalog
import gx.zelda.compendium.model.compendiumNumber
import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The overview's Compendium row: entries photographed out of the save file's own total (389, or 394 in Master Mode). */
class CompendiumProgressProvider(
    private val storage: CompendiumStorage,
    private val catalog: CachedLoad<CompendiumCatalog> = CachedLoad { CompendiumRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.COMPENDIUM
    override val title = "Hyrule Compendium"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(storage.photographed(saveFile.id).map { summarize(catalog, saveFile, it) })
    }

    companion object {
        fun summarize(catalog: CompendiumCatalog, file: SaveFile, photographed: Set<String>): ProgressSummary {
            val entries = catalog.entriesFor(file)
            val missing = entries.filter { it.id !in photographed }
            val mostMissing = missing.groupBy { it.category }.maxByOrNull { it.value.size }
            return ProgressSummary(
                title = "Hyrule Compendium",
                done = entries.size - missing.size,
                total = entries.size,
                secondary = mostMissing?.let { (category, left) -> "Most missing: ${category.label} (${left.size} left)" },
                next = missing.firstOrNull()?.let { entry ->
                    val number = entry.numberIn(file.masterMode)?.let { "${compendiumNumber(it)} " }.orEmpty()
                    "Photograph $number${entry.name}"
                },
            )
        }
    }
}
