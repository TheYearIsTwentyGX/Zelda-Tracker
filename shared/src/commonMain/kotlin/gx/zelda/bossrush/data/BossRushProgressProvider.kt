package gx.zelda.bossrush.data

import gx.zelda.bossrush.model.BossRushBoard
import gx.zelda.bossrush.model.RecordTime
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.CachedLoad
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.SaveFile
import gx.zelda.core.SaveFileSettingsStorage
import gx.zelda.core.TrackerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * The overview's Lightning Round row: bosses with a record of the slots the file's mode shows
 * (11 on a Normal file, 12 on a Hero Mode one), the sum of the bests, and the next boss without one.
 */
class BossRushProgressProvider(
    private val storage: BossRushStorage,
    private val settings: SaveFileSettingsStorage,
    override val title: String = "Lightning Round Records",
    private val storyGate: (String) -> Flow<StoryGate> = { flowOf(StoryGate.OFF) },
    private val data: CachedLoad<BossRushData> = CachedLoad { BossRushRepository.load(storage.game) },
) : ProgressProvider {
    override val kind = TrackerKind.BOSS_RUSH

    override fun summary(saveFile: SaveFile): Flow<ProgressSummary> = flow {
        val data = data.get()
        emitAll(
            combine(storage.records(saveFile.id), settings.settings(saveFile.id), storyGate(saveFile.id)) { records, s, gate ->
                summarize(BossRushBoard(data.catalog, records, s.heroMode), lockedBy(data, gate), title)
            },
        )
    }

    companion object {
        /** The story steps still to do before the round opens, by name; empty when it's open or gating is off. */
        fun lockedBy(data: BossRushData, gate: StoryGate): List<String> {
            val unlockedBy = data.sideQuest?.unlockedBy.orEmpty()
            if (!gate.enabled) return emptyList()
            return unlockedBy.filter { it in gate.stepNames && it !in gate.done }.mapNotNull(gate.stepNames::get)
        }

        fun summarize(board: BossRushBoard, lockedBy: List<String> = emptyList(), title: String = "Lightning Round Records"): ProgressSummary {
            val mode = if (board.heroMode) " (Hero Mode)" else ""
            val sum = board.sumOfBests?.let { " · bests total ${RecordTime.format(it)}" }.orEmpty()
            val next = when {
                // Records already entered always count; the lock only replaces the suggestion.
                lockedBy.isNotEmpty() && board.set == 0 -> "Opens after ${lockedBy.joinToString(", ")}"
                else -> board.nextWithout?.title ?: "Every boss has a record"
            }
            return ProgressSummary(
                title = title,
                done = board.set,
                total = board.slots,
                secondary = "Records set ${board.set}/${board.slots}$mode$sum",
                next = next,
            )
        }
    }
}
