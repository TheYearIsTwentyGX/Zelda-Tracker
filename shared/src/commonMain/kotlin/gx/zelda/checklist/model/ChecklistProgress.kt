package gx.zelda.checklist.model

import kotlinx.serialization.Serializable

/**
 * One save file's progress on one checklist: how many steps of each item are done, by item id.
 * An item without tiers is 0 or 1; an item with tiers counts up to its tier count. Items at 0 are
 * left out, and ids the dataset no longer has are ignored.
 */
@Serializable
data class ChecklistProgress(val levels: Map<String, Int> = emptyMap()) {
    /** Steps done on [item], never more than it has. */
    fun level(item: ChecklistItem): Int = (levels[item.id] ?: 0).coerceIn(0, item.steps)

    fun isDone(item: ChecklistItem): Boolean = level(item) >= item.steps

    /** Something done on [item]: ticked, or at least one tier. A spoiler-hidden row shows once it is. */
    fun isStarted(item: ChecklistItem): Boolean = level(item) > 0

    fun withLevel(item: ChecklistItem, level: Int): ChecklistProgress {
        val clamped = level.coerceIn(0, item.steps)
        return copy(levels = if (clamped == 0) levels - item.id else levels + (item.id to clamped))
    }

    /** Ticks every step of [item], or clears them all. */
    fun withDone(item: ChecklistItem, done: Boolean): ChecklistProgress = withLevel(item, if (done) item.steps else 0)
}

/** Viewing preferences for one checklist, shared by every save file. */
@Serializable
data class ChecklistPrefs(
    /** Spoiler-safe mode: a row with nothing done shows only its heading until revealed. */
    val hideUnchecked: Boolean = false,
)
