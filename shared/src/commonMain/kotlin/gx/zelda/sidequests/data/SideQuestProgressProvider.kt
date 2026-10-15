package gx.zelda.sidequests.data

import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.TrackerKind
import gx.zelda.core.countOf
import gx.zelda.sidequests.model.QuestKind
import gx.zelda.sidequests.model.SideQuestCatalog
import gx.zelda.sidequests.model.SideQuestPrefs
import gx.zelda.sidequests.model.SideQuestProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** The overview's Side Quests row: quests completed across every tab, with each tab's tally. */
class SideQuestProgressProvider(
    private val storage: SideQuestStorage,
    private val catalog: CachedLoad<SideQuestCatalog> = CachedLoad { SideQuestRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.SIDE_QUESTS
    override val title = "Side Quests"

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val catalog = catalog.get()
        emitAll(combine(storage.progress(saveFile.id), storage.prefs) { progress, prefs -> summarize(catalog, progress, prefs) })
    }

    companion object {
        fun summarize(catalog: SideQuestCatalog, progress: SideQuestProgress, prefs: SideQuestPrefs): ProgressSummary {
            val tabs = QuestKind.entries.filter { kind -> catalog.quests.any { it.kind == kind } }
            return ProgressSummary(
                title = "Side Quests",
                done = catalog.quests.count { it.id in progress.completed },
                total = catalog.quests.size,
                secondary = tabs.joinToString(" · ") { kind ->
                    "${kind.shortLabel} ${catalog.completedCount(kind, progress.completed)} / ${catalog.ofKind(kind).size}"
                },
                next = next(catalog, progress, prefs),
            )
        }

        /** "Side", "Shrine", "DLC": short enough for all three tabs to fit on the secondary line. */
        private val QuestKind.shortLabel: String
            get() = when (this) {
                QuestKind.SIDE -> "Side"
                QuestKind.SHRINE -> "Shrine"
                QuestKind.DLC -> "DLC"
            }

        /**
         * A quest already started, which is no spoiler; then the region closest to done, naming a
         * quest and its giver only while spoilers are shown (a hidden row shows only its region).
         */
        private fun next(catalog: SideQuestCatalog, progress: SideQuestProgress, prefs: SideQuestPrefs): String? {
            val left = catalog.quests.filter { it.id !in progress.completed }
            catalog.byRegion(left.filter { progress.isStarted(it.id) }).firstOrNull()?.second?.first()?.let {
                return "Finish ${it.name} (${it.region})"
            }
            val (region, inRegion) = catalog.byRegion(left).minByOrNull { it.second.size } ?: return null
            val more = countOf(inRegion.size, "quest")
            return if (prefs.hideUndiscovered) {
                "$more left in the $region Tower region"
            } else {
                val quest = inRegion.first()
                "Start ${quest.name}: ${quest.giver}, ${quest.giverLocation} ($more left in the $region Tower region)"
            }
        }
    }
}
