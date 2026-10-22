package gx.zelda.checklist.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * One save file's progress on one checklist: how many steps of each item are done, by item id.
 * An item without tiers is 0 or 1; an item with tiers counts up to its tier count. Items at 0 are
 * left out, and ids the dataset no longer has are ignored.
 *
 * An item with a [ChecklistItem.sameAs] link has no level here: it's read from and written to the
 * item it names, in [levels] for one in this file or in [linked] for one in another of the game's
 * checklists. Everything that asks this class for a level ([level], [isDone], counts, formulas, the
 * story gate) so sees the shared progress.
 */
@Serializable
data class ChecklistProgress(
    val levels: Map<String, Int> = emptyMap(),
    /** The game's other checklists' [levels] on the same save file, by file name ("items"). Read with this one, never saved in it. */
    @Transient val linked: Map<String, Map<String, Int>> = emptyMap(),
) {
    /** Steps done on [item], never more than it has. */
    fun level(item: ChecklistItem): Int {
        val link = item.link ?: return (levels[item.id] ?: 0).coerceIn(0, item.steps)
        return link.levelFrom(raw(link.ref) ?: 0)
    }

    fun isDone(item: ChecklistItem): Boolean = level(item) >= item.steps

    /** Something done on [item]: ticked, or at least one tier. A spoiler-hidden row shows once it is. */
    fun isStarted(item: ChecklistItem): Boolean = level(item) > 0

    fun withLevel(item: ChecklistItem, level: Int): ChecklistProgress {
        val clamped = level.coerceIn(0, item.steps)
        val link = item.link ?: return withRaw(LevelRef(null, item.id), clamped)
        return withRaw(link.ref, link.canonicalFor(clamped >= item.steps, raw(link.ref) ?: 0))
    }

    /** Each item at its level, as [withLevel] does one. */
    fun withLevels(levels: Map<ChecklistItem, Int>): ChecklistProgress =
        levels.entries.fold(this) { progress, (item, level) -> progress.withLevel(item, level) }

    /** Ticks every step of [item], or clears them all. */
    fun withDone(item: ChecklistItem, done: Boolean): ChecklistProgress = withLevel(item, if (done) item.steps else 0)

    /** The level stored at [ref], as stored: null when absent. */
    fun raw(ref: LevelRef): Int? = if (ref.file == null) levels[ref.itemId] else linked[ref.file]?.get(ref.itemId)

    /** Stores [level] at [ref] as it is (0 or null removes it), for a link or an undo. */
    fun withRaw(ref: LevelRef, level: Int?): ChecklistProgress {
        fun Map<String, Int>.with() = if (level == null || level <= 0) this - ref.itemId else this + (ref.itemId to level)
        return if (ref.file == null) {
            copy(levels = levels.with())
        } else {
            copy(linked = linked + (ref.file to linked[ref.file].orEmpty().with()))
        }
    }
}

/** Where [ChecklistItem]'s level is stored: its own id, or the item its link names. */
val ChecklistItem.storedAt: LevelRef get() = link?.ref ?: LevelRef(null, id)

/** Viewing preferences for one checklist, shared by every save file. */
@Serializable
data class ChecklistPrefs(
    /** Spoiler-safe mode: a row with nothing done shows only its heading until revealed. */
    val hideUnchecked: Boolean = false,
)
