package gx.zelda.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One row of a game's Progress Overview: where one save file stands with one tracker.
 *
 * [done] / [total] drive the row's progress bar. [secondary] is a short line of detail under it,
 * and [next] a suggestion for what to do next. A [next] line must follow the tracker's own
 * spoiler setting: with "Hide undiscovered" on, it names only things the save file has found.
 */
data class ProgressSummary(
    val title: String,
    val done: Int,
    val total: Int,
    val secondary: String? = null,
    val next: String? = null,
    /** What this row adds toward the in-game map completion %; null if it adds nothing. */
    val mapItems: ItemCount? = null,
) {
    /** 0–1 for the progress bar; 0 when there is nothing to do. */
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/** [done] of [total] things of one kind, e.g. 87 of 120 "shrines". */
data class ItemCount(val label: String, val done: Int, val total: Int)

/**
 * Supplies one Progress Overview row for a tracker. A tracker can have more than one (Shrines
 * also gives the Spirit Orbs' Hearts & Stamina row).
 *
 * **Adding a tracker to the overview** takes one provider and one line:
 * 1. Implement this interface next to the tracker's storage, e.g. `sidequests/data/SideQuestProgressProvider.kt`.
 *    [summary] reads the tracker's own storage for the given save file (and its prefs, for the
 *    spoiler setting) and maps it to a [ProgressSummary]. Load the bundled dataset through a
 *    [CachedLoad], so switching save files doesn't parse it again. Take the loader as a
 *    constructor parameter, as the view models do, so tests can pass a fixture or a failure.
 * 2. Return it for the tracker's [TrackerKind] in `overview/OverviewProviders.kt`. That `when` is
 *    exhaustive, so adding a [TrackerKind] doesn't compile until it has a branch there.
 *
 * The flow may throw (a missing or malformed dataset, unreadable storage): the overview catches
 * it and shows the row as unavailable, and the other rows carry on. It retries when the save
 * file changes or the overview is opened again.
 */
interface ProgressProvider {
    /** The tracker a tap on the row opens. */
    val kind: TrackerKind

    /** The row's title while it loads or when it is unavailable. */
    val title: String

    /** The row for [saveFile], updated as the tracker's data changes. */
    fun summary(saveFile: SaveFile): Flow<ProgressSummary>
}

/** Loads a value on first use and keeps it. A failure isn't kept, so the next call tries again. */
class CachedLoad<T : Any>(private val load: suspend () -> T) {
    private val mutex = Mutex()
    private var value: T? = null

    suspend fun get(): T = mutex.withLock { value ?: load().also { value = it } }
}

/** "1 shrine", "3 shrines": for the overview's secondary and next lines. */
fun countOf(n: Int, noun: String, plural: String = noun + "s"): String = "$n ${if (n == 1) noun else plural}"
