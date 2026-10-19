package gx.zelda.core

/** What a "mark everything complete" toggle does when tapped. */
enum class BulkAction { MARK, CLEAR }

/**
 * What one "mark everything complete" toggle covers: a tab, a region card, or a tracker's *All*
 * tab. The toggle marks every item in it complete, or clears them all when they already are. Counts
 * are in items, so an upgrade with four levels is one item, done only at its top level.
 *
 * It names no items, only the scope's [label] and counts, so it never gives away a spoiler-hidden
 * row. The shared UI for it is in `core/ui/BulkCompleteControl.kt`; docs/checklist-trackers.md
 * says how a tracker plugs in.
 */
data class BulkScope(
    /** The tab or region, as its header names it: "Pieces of Heart", "Hyrule Field", "Everything". */
    val label: String,
    val total: Int,
    /** Items in the scope that are fully complete. */
    val done: Int,
    /** Items with anything done, complete ones included; a clear changes each of these. */
    val started: Int,
    val noun: String,
    val plural: String = noun + "s",
    /** True when a filter or search hides some of the tab, so the toggle acts on the items shown only. */
    val filtered: Boolean = false,
    /** One more line for the confirm dialog, e.g. which tabs the *All* tab leaves out. */
    val note: String? = null,
) {
    val action: BulkAction get() = if (total > 0 && done >= total) BulkAction.CLEAR else BulkAction.MARK

    /** How many items the [action] changes: those not complete, or for a clear every started one. */
    val changes: Int get() = if (action == BulkAction.CLEAR) started else total - done

    /** The tri-state box: true when all are complete, false when none is started, null (a dash) otherwise. */
    val checked: Boolean? get() = when {
        total > 0 && done >= total -> true
        started == 0 -> false
        else -> null
    }

    /** A clear always asks first; a mark asks once it changes more than a handful of items. */
    val needsConfirm: Boolean get() = action == BulkAction.CLEAR || changes > CONFIRM_ABOVE

    /** What the toggle says it will do: "Mark all complete", "Clear all", "Mark 3 shown complete". */
    val actionLabel: String get() = when (action) {
        BulkAction.MARK -> if (filtered) "Mark $changes shown complete" else "Mark all complete"
        BulkAction.CLEAR -> if (filtered) "Clear $changes shown" else "Clear all"
    }

    /** For a screen reader: "Mark all complete in Hyrule Field, 2 of 5 done". */
    val contentDescription: String get() = "$actionLabel in $label, $done of $total done"

    val confirmTitle: String get() = when (action) {
        BulkAction.MARK -> "Mark ${things(changes)} complete?"
        BulkAction.CLEAR -> "Clear ${things(changes)}?"
    }

    /** The dialog's text: where, which save file, and what else it does and doesn't touch. */
    fun confirmText(saveFile: String): String {
        val what = when (action) {
            BulkAction.MARK -> "${things(changes)} in $label on $saveFile will be marked complete."
            BulkAction.CLEAR -> "${things(changes)} in $label on $saveFile will be cleared to not done."
        }
        val others = if (filtered) " Only the ones the filters show change." else ""
        return listOfNotNull(what + others, note, "You can undo it right after.").joinToString("\n\n")
    }

    private fun things(n: Int): String = countOf(n, noun, plural)

    companion object {
        /** A mark of up to this many items happens without asking: it's quick to see and to undo. */
        const val CONFIRM_ABOVE = 3

        /** The scope over [items], given how to tell when one is complete and when one is started. */
        fun <T> of(
            label: String,
            items: List<T>,
            noun: String,
            plural: String = noun + "s",
            filtered: Boolean = false,
            note: String? = null,
            isDone: (T) -> Boolean,
            isStarted: (T) -> Boolean,
        ): BulkScope = BulkScope(
            label = label,
            total = items.size,
            done = items.count(isDone),
            started = items.count(isStarted),
            noun = noun,
            plural = plural,
            filtered = filtered,
            note = note,
        )
    }
}

/**
 * A bulk change just written to [saveFile]: [changed] items marked complete or cleared. It's kept
 * so its snackbar's Undo can put back [snapshot], which is whatever the tracker needs to restore,
 * usually a [MapSnapshot] or [SetSnapshot] taken inside the same write.
 */
data class BulkChange<S>(
    val action: BulkAction,
    val changed: Int,
    val noun: String,
    val plural: String,
    val saveFile: SaveFile,
    val snapshot: S,
) {
    /** The snackbar's line: "Marked 5 pieces complete on File 1". */
    val message: String get() = when (action) {
        BulkAction.MARK -> "Marked ${countOf(changed, noun, plural)} complete on ${saveFile.label}"
        BulkAction.CLEAR -> "Cleared ${countOf(changed, noun, plural)} on ${saveFile.label}"
    }
}

/**
 * The values [before] some keys of a progress map had, null where a key was absent, so an undo
 * puts back exactly those and leaves every other key as it is now.
 */
data class MapSnapshot<K, V : Any>(val before: Map<K, V?>) {
    fun restore(into: Map<K, V>): Map<K, V> = into.toMutableMap().apply {
        before.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
    }

    companion object {
        fun <K, V : Any> of(map: Map<K, V>, keys: Iterable<K>): MapSnapshot<K, V> = MapSnapshot(keys.associateWith { map[it] })
    }
}

/** The same for a set of ids, e.g. a tracker's completed shrines: which of [keys] were in it. */
data class SetSnapshot<T>(val keys: Set<T>, val members: Set<T>) {
    fun restore(into: Set<T>): Set<T> = into - keys + members

    companion object {
        fun <T> of(set: Set<T>, keys: Iterable<T>): SetSnapshot<T> = keys.toSet().let { SetSnapshot(it, set intersect it) }
    }
}
