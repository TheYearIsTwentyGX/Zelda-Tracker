package gx.zelda.mainquest.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ItemCount
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.mainquest.model.MainQuestCatalog
import gx.zelda.mainquest.model.MainQuestProgress
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The overview's Main Quest row: every checklist the game has together, each counted below.
 * Towers, Divine Beasts and memories in BotW; dungeons, items and Tiger Scrolls in The Minish Cap.
 */
class MainQuestProgressProvider(
    private val storage: MainQuestStorage,
    private val catalog: CachedLoad<MainQuestCatalog> = CachedLoad { MainQuestRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.MAIN_QUEST
    override val title = "Main Quest"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(storage.progress(saveFile.id).map { summarize(catalog, it) })
    }

    companion object {
        fun summarize(catalog: MainQuestCatalog, progress: MainQuestProgress): ProgressSummary =
            if (catalog.dungeons.isEmpty()) summarizeBotw(catalog, progress) else summarizeDungeons(catalog, progress)

        private fun summarizeBotw(catalog: MainQuestCatalog, progress: MainQuestProgress): ProgressSummary {
            fun done(section: MainQuestSection) = catalog.doneCount(section, progress.done(section))
            val towers = done(MainQuestSection.TOWERS)
            val beasts = done(MainQuestSection.DIVINE_BEASTS)
            val memories = done(MainQuestSection.MEMORIES)
            val beastTotal = catalog.total(MainQuestSection.DIVINE_BEASTS)
            return ProgressSummary(
                title = "Main Quest",
                done = towers + beasts + memories,
                total = MainQuestSection.entries.sumOf { catalog.total(it) },
                secondary = "Towers $towers / ${catalog.total(MainQuestSection.TOWERS)} · " +
                    "Beasts $beasts / $beastTotal · " +
                    "Memories $memories / ${catalog.total(MainQuestSection.MEMORIES)}",
                next = next(catalog, progress),
                mapItems = ItemCount("Divine Beasts", beasts, beastTotal),
            )
        }

        private fun summarizeDungeons(catalog: MainQuestCatalog, progress: MainQuestProgress): ProgressSummary {
            fun done(section: MainQuestSection) = catalog.doneCount(section, progress.done(section))
            val dungeons = done(MainQuestSection.DUNGEONS)
            val items = done(MainQuestSection.ITEMS)
            val scrolls = done(MainQuestSection.TIGER_SCROLLS)
            return ProgressSummary(
                title = "Main Quest",
                done = dungeons + items + scrolls,
                total = catalog.sections.sumOf { catalog.total(it) },
                secondary = "Dungeons $dungeons / ${catalog.total(MainQuestSection.DUNGEONS)} · " +
                    "Items $items / ${catalog.total(MainQuestSection.ITEMS)} · " +
                    "Tiger Scrolls $scrolls / ${catalog.total(MainQuestSection.TIGER_SCROLLS)}",
                next = catalog.dungeons.firstOrNull { it.id !in progress.dungeons }?.let { "Clear ${it.name} in ${it.region}" },
            )
        }

        /** The next Divine Beast, then the next tower, then the next memory in Adventure Log order. */
        private fun next(catalog: MainQuestCatalog, progress: MainQuestProgress): String? {
            catalog.divineBeasts.firstOrNull { it.id !in progress.divineBeasts }?.let { return "Free ${it.name}: ${it.quest}" }
            catalog.towers.firstOrNull { it.id !in progress.towers }?.let { return "Activate ${it.name}" }
            catalog.memories.firstOrNull { it.id !in progress.memories }?.let { return "Recover memory ${it.number}: ${it.location}" }
            return null
        }
    }
}
