package gx.zelda.bossrush.model

import kotlinx.serialization.Serializable

/**
 * Skyward Sword's Lightning Round (`files/ss/boss_rush.json`): the bosses the Thunder Dragon keeps a
 * best time for, in the game's order, and the Side Quests item that holds the round's prizes and
 * story gate, so neither is copied here.
 */
@Serializable
data class BossRushCatalog(
    val sideQuest: SideQuestRef,
    val bosses: List<BossRushBoss>,
) {
    /** The bosses a file's board shows: every one on a Hero Mode file, all but the [BossRushBoss.heroOnly] ones on a Normal file. */
    fun bosses(heroMode: Boolean): List<BossRushBoss> = bosses.filter { heroMode || !it.heroOnly }

    fun boss(id: String): BossRushBoss? = bosses.firstOrNull { it.id == id }
}

/** A checklist item elsewhere: [itemId] in [dataFile] (relative to `files/`). */
@Serializable
data class SideQuestRef(val dataFile: String, val itemId: String)

@Serializable
data class BossRushBoss(
    /** Stable: records are kept under it. */
    val id: String,
    val name: String,
    /** Which of the boss's fights, for one fought more than once ("2nd battle"). */
    val fight: String? = null,
    /** Where the original fight is. */
    val place: String,
    /** The picture, `drawable/ss_boss_<icon>.png`, shared by a boss's fights. */
    val icon: String,
    /** The decompilation's name for the boss's record, `STORYFLAG_<flag>_TIME_MIN/SEC/MS`. */
    val flag: String,
    /** Only offered on a Hero Mode file (Demise). */
    val heroOnly: Boolean = false,
    val notes: String? = null,
) {
    /** "Ghirahim (2nd battle)", or just the name. */
    val title: String get() = fight?.let { "$name ($it)" } ?: name
}

/** One time entered for a boss: [time] in hundredths ([RecordTime]), on [day] (epoch day, local), [id] unique within the boss. */
@Serializable
data class RecordEntry(val id: Int, val time: Int, val day: Long? = null)

/**
 * One save file's Lightning Round times: every time entered for each boss, oldest first. The game
 * keeps only the best; the board keeps a short history too, so improvements show. Records for a
 * boss the file doesn't show (Demise on a Normal file) are kept, never dropped.
 */
@Serializable
data class BossRushRecords(val entries: Map<String, List<RecordEntry>> = emptyMap()) {
    fun of(bossId: String): List<RecordEntry> = entries[bossId].orEmpty()

    /** The personal best: the fastest time, the earlier entry on a tie. */
    fun best(bossId: String): RecordEntry? = of(bossId).minWithOrNull(BEST_FIRST)

    fun hasRecord(bossId: String): Boolean = of(bossId).isNotEmpty()

    /**
     * The best before [best] was set: the fastest of the entries made before it, so "−2.15" can show
     * what the record beat. Null when it's the first time entered.
     */
    fun previousBest(bossId: String): RecordEntry? {
        val best = best(bossId) ?: return null
        return of(bossId).filter { it.id < best.id }.minWithOrNull(BEST_FIRST)
    }

    /**
     * [time] added for [bossId] on [day]. Past [HISTORY] entries the oldest goes, but never the
     * best, so the record survives however many slower runs are entered.
     */
    fun withAdded(bossId: String, time: Int, day: Long?): BossRushRecords {
        val list = of(bossId)
        val added = list + RecordEntry(id = (list.maxOfOrNull { it.id } ?: 0) + 1, time = time, day = day)
        return withEntries(bossId, trimmed(added))
    }

    /** Entry [entryId]'s time changed to [time]; its date stays. Unchanged when there's no such entry. */
    fun withEdited(bossId: String, entryId: Int, time: Int): BossRushRecords {
        val list = of(bossId)
        if (list.none { it.id == entryId }) return this
        return withEntries(bossId, list.map { if (it.id == entryId) it.copy(time = time) else it })
    }

    fun without(bossId: String, entryId: Int): BossRushRecords = withEntries(bossId, of(bossId).filterNot { it.id == entryId })

    /** [bossId]'s entries replaced, as an undo puts them back; an empty list removes the boss. */
    fun withEntries(bossId: String, list: List<RecordEntry>): BossRushRecords =
        copy(entries = if (list.isEmpty()) entries - bossId else entries + (bossId to list))

    private fun trimmed(list: List<RecordEntry>): List<RecordEntry> {
        var kept = list
        while (kept.size > HISTORY) {
            val best = kept.minWith(BEST_FIRST)
            kept = kept - kept.first { it != best }
        }
        return kept
    }

    companion object {
        /** Times kept per boss, the best among them. */
        const val HISTORY = 20

        val BEST_FIRST: Comparator<RecordEntry> = compareBy<RecordEntry> { it.time }.thenBy { it.id }
    }
}

/** One boss on a file's board. [history] is newest first. */
data class BossRow(
    val boss: BossRushBoss,
    val best: RecordEntry?,
    val previousBest: RecordEntry?,
    val history: List<RecordEntry>,
) {
    /** What the best took off the one before it, in hundredths; null for a first time. */
    val improvement: Int? get() = if (best != null && previousBest != null) previousBest.time - best.time else null
}

/** A file's board: the bosses its mode shows, with their records, and what it keeps hidden. */
data class BossRushBoard(val catalog: BossRushCatalog, val records: BossRushRecords, val heroMode: Boolean) {
    val rows: List<BossRow> = catalog.bosses(heroMode).map { boss ->
        BossRow(boss, records.best(boss.id), records.previousBest(boss.id), records.of(boss.id).asReversed())
    }

    /** Record slots: 11 on a Normal file, 12 on a Hero Mode file. */
    val slots: Int get() = rows.size

    /** Bosses with a record. */
    val set: Int get() = rows.count { it.best != null }

    /** The sum of the best times shown, in hundredths; null with none. */
    val sumOfBests: Int? get() = rows.mapNotNull { it.best?.time }.takeIf { it.isNotEmpty() }?.sum()

    /** Bosses with records the mode hides (Demise's, on a file switched back to Normal). */
    val hidden: List<BossRushBoss> = catalog.bosses.filter { it !in catalog.bosses(heroMode) && records.hasRecord(it.id) }

    /** The first boss shown without a record, in the game's order. */
    val nextWithout: BossRushBoss? get() = rows.firstOrNull { it.best == null }?.boss
}
