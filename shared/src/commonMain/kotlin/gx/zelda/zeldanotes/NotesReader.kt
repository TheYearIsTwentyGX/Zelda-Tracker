package gx.zelda.zeldanotes

import gx.zelda.core.Game
import gx.zelda.screencam.live.KorokSpot
import gx.zelda.shrines.scan.MapImage
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The part of the world a view shows below the app's header, in world units. */
data class WorldRect(val x0: Double, val z0: Double, val x1: Double, val z1: Double)

/** What came of one screenshot or recording frame. */
sealed interface NotesRead {
    data class Placed(
        val pose: NotesPose,
        val droplets: Int,
        val inliers: Int,
        val view: WorldRect,
        /** Per Korok, for those this view could judge; empty when it couldn't judge any. */
        val verdicts: Map<String, NotesVerdict>,
        /** Why nothing was judged, when that's the case. */
        val note: String? = null,
    ) : NotesRead

    data class Rejected(val reason: String) : NotesRead
}

/** Reads Zelda Notes Navigation screenshots: droplets, then where they are, then which Koroks are gone. */
class NotesReader(private val template: DropletTemplate, spots: List<KorokSpot>) {
    private val detector = DropletDetector(template)
    private val registrar = PointRegistrar(spots)
    private val judge = PresenceJudge(template, spots)

    /**
     * Reads [image]. With [near], a recording's previous pose, the map search stays close to it,
     * which places frames with only a few droplets.
     */
    fun read(image: MapImage, near: NotesPose? = null): NotesRead {
        val notes = NotesImage.of(image, template)
        val droplets = detector.detect(notes)
        if (droplets.size < PointRegistrar.MIN_TRACKED_INLIERS) {
            return NotesRead.Rejected("No Korok droplets found: is this the Zelda Notes map with Hidden Koroks shown?")
        }
        val shift = notes.width * TRACK_SHIFT
        // The tracked search keeps the middle of the readable band near where it was; that band runs
        // from top to bottom, so its centre is (top + bottom) / 2.
        val reg = near?.let { registrar.register(droplets, notes.width, notes.bottom + notes.top, it, shift) }
            ?: registrar.register(droplets, notes.width, notes.height)
            ?: return NotesRead.Rejected("Couldn't place this on the map (${droplets.size} droplets): include more Koroks in view")
        val pose = reg.pose
        val view = WorldRect(pose.worldX(0.0), pose.worldZ(notes.top.toDouble()), pose.worldX(notes.width.toDouble()), pose.worldZ(notes.bottom.toDouble()))
        val note = when {
            !notes.hideDiscovered -> "Turn on Hide Discovered Destinations: with it off, collected Koroks still show"
            !judge.canJudge(pose) -> "Zoomed out too far to judge Koroks: zoom in about ${zoomNeeded(pose)}×"
            else -> null
        }
        val verdicts = if (note == null) judge.judge(notes, pose, droplets) else emptyMap()
        return NotesRead.Placed(pose, droplets.size, reg.inliers, view, verdicts, note)
    }

    private fun zoomNeeded(pose: NotesPose): Int =
        ceil(template.width / PresenceJudge.MAX_ICON_UNITS / pose.scale).roundToInt().coerceAtLeast(2)

    companion object {
        /** Between recording frames the view may move this share of the screen width. */
        const val TRACK_SHIFT = 0.6

        suspend fun load(game: Game, spots: List<KorokSpot>): NotesReader = NotesReader(DropletTemplate.load(game), spots)
    }
}

/**
 * Verdicts gathered across views, Korok by Korok: drawn in any view means still to collect (a
 * droplet can't appear by mistake, but one can be missed); otherwise gone in a clear view means
 * collected; otherwise unsure. Koroks never judged aren't listed.
 */
class NotesEvidence {
    private val seen = HashMap<String, NotesVerdict>()
    private val _views = mutableListOf<WorldRect>()

    /** Views that judged Koroks, for the coverage map. */
    val views: List<WorldRect> get() = _views

    fun add(read: NotesRead) {
        if (read !is NotesRead.Placed || read.verdicts.isEmpty()) return
        _views += read.view
        for ((id, v) in read.verdicts) seen[id] = merge(seen[id], v)
    }

    fun verdicts(): Map<String, NotesVerdict> = seen

    private fun merge(a: NotesVerdict?, b: NotesVerdict): NotesVerdict = when {
        a == null -> b
        a == NotesVerdict.PRESENT || b == NotesVerdict.PRESENT -> NotesVerdict.PRESENT
        a == NotesVerdict.ABSENT || b == NotesVerdict.ABSENT -> NotesVerdict.ABSENT
        else -> NotesVerdict.UNSURE
    }
}
