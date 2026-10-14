package gx.zelda.mainquest.model

import kotlinx.serialization.Serializable

@Serializable
data class SheikahTower(
    val id: String,
    val name: String,
    val region: String,
    /** Where the tower stands, in one line. */
    val location: String,
    /** What's in the way of the climb and how to get past it. */
    val tip: String,
)

@Serializable
data class DivineBeast(
    val id: String,
    val name: String,
    val champion: String,
    val race: String,
    val region: String,
    val location: String,
    /** The main quest that frees it. */
    val quest: String,
    /** How the quest starts, including any quest that has to come first. */
    val start: String,
    /** What to bring or wear. */
    val prep: List<String>,
    val boss: String,
    /** The Champion's ability the beast grants, and what it does. */
    val ability: String,
    val abilityEffect: String,
    val rewards: List<String>,
)

@Serializable
data class Memory(
    /** Position in the Adventure Log's Recovered Memories, 1–18. */
    val number: Int,
    val title: String,
    val location: String,
    /** Which of the twelve pictures in the Sheikah Slate album leads here; null if it isn't one. */
    val photo: Int? = null,
    /** The last picture, which Impa shows once all twelve album memories are recovered. */
    val finalPicture: Boolean = false,
    /** The main quest that triggers it, for memories that aren't from a picture. */
    val quest: String? = null,
    val hint: String,
) {
    val id: String get() = "memory_$number"

    /** Whether "Captured Memories" asks for it: the twelve album pictures plus the final one. */
    val isCaptured: Boolean get() = photo != null || finalPicture
}

/** The three checklists, in the order the screen shows them. */
enum class MainQuestSection { TOWERS, DIVINE_BEASTS, MEMORIES }

@Serializable
data class MainQuestCatalog(
    val towers: List<SheikahTower>,
    val divineBeasts: List<DivineBeast>,
    val memories: List<Memory>,
) {
    fun ids(section: MainQuestSection): List<String> = when (section) {
        MainQuestSection.TOWERS -> towers.map { it.id }
        MainQuestSection.DIVINE_BEASTS -> divineBeasts.map { it.id }
        MainQuestSection.MEMORIES -> memories.map { it.id }
    }

    fun total(section: MainQuestSection): Int = ids(section).size

    /** How many of [done] are items in [section]; unknown ids don't count. */
    fun doneCount(section: MainQuestSection, done: Set<String>): Int = ids(section).count { it in done }

    /** Album memories recovered, out of twelve; Impa reveals the final picture at twelve. */
    fun albumCount(done: Set<String>): Int = memories.count { it.photo != null && it.id in done }

    val albumSize: Int get() = memories.count { it.photo != null }
}

/** What one save file has done: ids of the towers activated, beasts freed and memories recovered. */
@Serializable
data class MainQuestProgress(
    val towers: Set<String> = emptySet(),
    val divineBeasts: Set<String> = emptySet(),
    val memories: Set<String> = emptySet(),
) {
    fun done(section: MainQuestSection): Set<String> = when (section) {
        MainQuestSection.TOWERS -> towers
        MainQuestSection.DIVINE_BEASTS -> divineBeasts
        MainQuestSection.MEMORIES -> memories
    }

    fun with(section: MainQuestSection, id: String, done: Boolean): MainQuestProgress {
        val ids = done(section).let { if (done) it + id else it - id }
        return when (section) {
            MainQuestSection.TOWERS -> copy(towers = ids)
            MainQuestSection.DIVINE_BEASTS -> copy(divineBeasts = ids)
            MainQuestSection.MEMORIES -> copy(memories = ids)
        }
    }
}
