package gx.zelda.shrines.scan

/** What the map shows at one shrine's spot. */
enum class ScanVerdict(val label: String) {
    /** The shrine icon with a blue glyph. */
    COMPLETED("Completed"),

    /** The shrine icon with an orange glyph: found, not finished. */
    NOT_DONE("Not done"),

    /** The spot couldn't be read; see [UnsureReason]. */
    UNSURE("Unsure"),

    /** The spot is clearly visible and empty, so the shrine hasn't been found. */
    NOT_PRESENT("Not on the map"),
}

enum class UnsureReason(val label: String) {
    /** Something is drawn over the spot: another icon, a pin, a stamp, a label, the HUD or the trail. */
    COVERED("Covered"),

    /** Another shrine's icon is drawn in the same place (Ree Dahee and Shee Venath). */
    STACKED("Stacked with another shrine"),

    /** Too close to the edge of the screenshot to judge. */
    SCREEN_EDGE("At the screen edge"),

    /** Outside every screenshot. */
    NOT_IN_SHOT("Not in any screenshot"),

    /** Screenshots gave different definite answers. */
    CONFLICT("Screenshots disagree"),
}

/**
 * One shrine's reading, from one screenshot or merged from several. [note] says in a few words why
 * an [ScanVerdict.UNSURE] spot couldn't be read. [iconSeen] is true when some of a shrine icon was
 * visible even if the reading is unsure, so an empty reading of the same spot elsewhere can't win.
 */
data class SpotReading(
    val verdict: ScanVerdict,
    val reason: UnsureReason? = null,
    val note: String = "",
    val iconSeen: Boolean = false,
) {
    val isDefinite: Boolean get() = verdict != ScanVerdict.UNSURE

    companion object {
        fun unsure(reason: UnsureReason, note: String = reason.label, iconSeen: Boolean = false) =
            SpotReading(ScanVerdict.UNSURE, reason, note, iconSeen)
    }
}

/** Why a screenshot can't be used; [ShotScan.Rejected.message] explains it to the user. */
enum class RejectReason {
    UNREADABLE,
    WRONG_SIZE,
    TOO_FEW_ICONS,
    NOT_MIN_ZOOM,

    /** A photo in which no map screen could be found. */
    NO_SCREEN,

    /** A photo in which the screen is too small to read the icons. */
    SCREEN_TOO_SMALL,
}

/** Where a screenshot sits on the map: screen = [scale] × world + ([tx], [ty]). */
data class MapRegistration(
    val scale: Double,
    val tx: Double,
    val ty: Double,
    /** Shrine and tower icons that line up with their world positions. */
    val anchors: Int,
    /** Root-mean-square distance between those icons and where the fit puts them, in pixels. */
    val rmsPx: Double,
    /** The scale a free least-squares fit gives on the same anchors; close to [scale] at minimum zoom. */
    val fittedScale: Double,
)

sealed interface ShotScan {
    data class Rejected(val reason: RejectReason, val message: String) : ShotScan

    /** [readings] has an entry for every shrine in the map data. */
    data class Read(
        val registration: MapRegistration,
        val readings: Map<String, SpotReading>,
        /** Taken in Hero's Path mode; the trail was read around rather than through. */
        val heroesPath: Boolean = false,
    ) : ShotScan
}

/**
 * Combines one shrine's readings from several screenshots. A definite reading beats one taken at
 * the screen edge or outside the shot; definite readings that disagree make the result unsure, and
 * so does an empty reading when another shot saw part of an icon there.
 */
fun mergeReadings(readings: List<SpotReading>): SpotReading {
    val seen = readings.filter { it.reason != UnsureReason.NOT_IN_SHOT && it.reason != UnsureReason.SCREEN_EDGE }
    if (seen.isEmpty()) {
        return if (readings.any { it.reason == UnsureReason.SCREEN_EDGE }) {
            SpotReading.unsure(UnsureReason.SCREEN_EDGE, "At the screen edge in every screenshot")
        } else {
            SpotReading.unsure(UnsureReason.NOT_IN_SHOT)
        }
    }
    val definite = seen.filter { it.isDefinite }
    val verdicts = definite.map { it.verdict }.toSet()
    return when {
        verdicts.size > 1 -> SpotReading.unsure(
            UnsureReason.CONFLICT,
            "Screenshots disagree: " + verdicts.joinToString(" vs ") { it.label.lowercase() },
            iconSeen = true,
        )
        verdicts.isEmpty() -> seen.firstOrNull { it.reason == UnsureReason.STACKED }
            ?: seen.firstOrNull { it.iconSeen }
            ?: seen.first()
        verdicts.single() == ScanVerdict.NOT_PRESENT && seen.any { it.iconSeen } -> SpotReading.unsure(
            UnsureReason.CONFLICT,
            "Empty in one screenshot, partly visible icon in another",
            iconSeen = true,
        )
        else -> definite.first()
    }
}

/** Merges every successfully read screenshot, shrine by shrine. */
fun mergeShots(shots: List<ShotScan.Read>, shrineIds: Collection<String>): Map<String, SpotReading> =
    shrineIds.associateWith { id ->
        mergeReadings(shots.map { it.readings[id] ?: SpotReading.unsure(UnsureReason.NOT_IN_SHOT) })
    }
