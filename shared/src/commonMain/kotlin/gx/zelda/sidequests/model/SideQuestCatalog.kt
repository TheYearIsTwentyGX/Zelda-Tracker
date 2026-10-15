package gx.zelda.sidequests.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which Adventure Log list a quest is in; the tracker has a tab for each. */
@Serializable
enum class QuestKind(val label: String, val noun: String) {
    @SerialName("side") SIDE("Side Quests", "side quest"),
    /** Quests that reveal or open a shrine; each one names its shrine by [SideQuest.shrineId]. */
    @SerialName("shrine") SHRINE("Shrine Quests", "Shrine Quest"),
    /** The Master Trials' and The Champions' Ballad's "EX" side quests. */
    @SerialName("dlc") DLC("DLC", "DLC quest"),
}

@Serializable
data class SideQuest(
    val id: String,
    /** As the Adventure Log shows it, e.g. "EX Treasure: Ancient Mask". */
    val name: String,
    val kind: QuestKind,
    /**
     * The Sheikah Tower region the quest giver stands in; one of [SideQuestCatalog.regions]. A
     * Shrine Quest uses its shrine's region, so it groups the same way in both trackers.
     */
    val region: String,
    /** Who gives the quest, or what starts it (a monument, a diary). */
    val giver: String,
    /** Where to find [giver], in one line. */
    val giverLocation: String,
    /** What has to happen first, e.g. "After freeing Vah Medoh"; null when nothing does. */
    val prerequisites: String? = null,
    /** What the quest asks, without giving the solution away. */
    val objective: String,
    val reward: String,
    /** Armor piece ids in the game's armor.json that [reward] includes. */
    val rewardArmor: List<String> = emptyList(),
    /** The shrine a Shrine Quest leads to, by its id in the game's shrines.json. */
    val shrineId: String? = null,
    /** When the quest can only be done at certain times, weathers or a Blood Moon; null otherwise. */
    val limited: String? = null,
    /** Which DLC pack a DLC quest comes from: "The Master Trials" or "The Champions' Ballad". */
    val pack: String? = null,
)

@Serializable
data class SideQuestCatalog(
    /** Tower regions in the order the list shows them: the same as the shrine tracker's. */
    val regions: List<String>,
    val quests: List<SideQuest>,
) {
    fun ofKind(kind: QuestKind): List<SideQuest> = quests.filter { it.kind == kind }

    /** How many quests of [kind] are in [completed]. */
    fun completedCount(kind: QuestKind, completed: Set<String>): Int = quests.count { it.kind == kind && it.id in completed }

    /** [visible] split by region in [regions] order; regions with none are left out. */
    fun byRegion(visible: List<SideQuest>): List<Pair<String, List<SideQuest>>> {
        val grouped = visible.groupBy { it.region }
        return regions.mapNotNull { region -> grouped[region]?.let { region to it } }
    }
}
