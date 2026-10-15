package gx.zelda.bosses.model

import gx.zelda.core.SaveFile

/**
 * Where one save file stands with one kind of boss, whatever the list's filters show: what the
 * tracker's tabs and summary lines show, and what a progress overview would report.
 */
data class BossKindTally(
    val kind: BossKind,
    /** Spawns of this kind on the save file defeated at least once. */
    val defeated: Int,
    val found: Int,
    /** Every spawn of this kind on the save file, DLC ones included. */
    val total: Int,
    /** Defeated spawns that count toward the kind's Medal of Honor (not DLC, not Master Mode-only). */
    val medalDefeated: Int,
    /** The spawns the medal counts. */
    val medalTotal: Int,
) {
    /** "Hinox 12 / 40 · Medal of Honor: Hinox"; the medal is Kilton's, for beating every one the game counts. */
    val headline: String
        get() {
            val count = "${kind.name} $medalDefeated / $medalTotal"
            return when {
                kind.medal == null -> "$count · no Medal of Honor for ${kind.plural}"
                medalDefeated == medalTotal -> "$count · ${kind.medal} earned (from Kilton)"
                else -> "$count · ${kind.medal}"
            }
        }
}

/** One [BossKindTally] per kind, in the catalog's order. */
fun BossCatalog.tallies(file: SaveFile, progress: BossProgress): List<BossKindTally> {
    val onFile = spawnsFor(file)
    return kinds.map { kind ->
        val all = onFile.filter { it.kind == kind.id }
        val counted = all.filter { it.countsForMedal }
        BossKindTally(
            kind = kind,
            defeated = all.count { progress.isDefeated(it.id) },
            found = all.count { progress.isFound(it.id) },
            total = all.size,
            medalDefeated = counted.count { progress.isDefeated(it.id) },
            medalTotal = counted.size,
        )
    }
}
