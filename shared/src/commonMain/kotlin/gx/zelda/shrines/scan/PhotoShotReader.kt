package gx.zelda.shrines.scan

import gx.zelda.screencam.Homography
import gx.zelda.screencam.Point
import gx.zelda.screencam.Rectifier
import gx.zelda.screencam.ScreenChrome
import gx.zelda.screencam.ScreenFinder
import gx.zelda.screencam.smoothChroma
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Reads a phone photo of the zoomed-out map, of a TV or the Switch's own screen:
 *
 * 1. [ScreenFinder] proposes outlines for the lit screen. Each is tried with all four corners as
 *    top left, and the one whose frame looks most like [ScreenChrome]'s wins, which also says which
 *    way up the screen is (so EXIF orientation doesn't matter).
 * 2. The photo is warped into the screenshot's 1920×1080 frame, its colour moiré smoothed away, and its
 *    colours matched to the reference frame's: normal or Hero's Path, whichever lines up more icons.
 * 3. The scanner's icon detection and registration pair icons with their world positions, and a
 *    RANSAC homography from world to photo corrects the outline's few pixels of error. Twice.
 * 4. The final warp is read with [ShotSource.CAMERA]'s thresholds.
 */
class PhotoShotReader(
    private val scanner: ShrineMapScanner,
    private val data: ShrineMapData,
    private val chrome: ScreenChrome,
) {
    private val shrinePoints = data.shrines.values.toList()

    fun read(photo: MapImage): ShotScan {
        val (screen, score) = locate(photo) ?: return noScreen()
        if (score < MIN_FRAME_SCORE) return noScreen()
        if (screen.localScale(CENTRE) < MIN_PHOTO_PX) return if (fillsPhoto(screen, photo)) scanner.wrongSize(photo) else tooSmall()

        var toPhoto = screen
        var heroesPath: Boolean? = null
        for (radius in REFINE_RADII) {
            val raw = smoothChroma(Rectifier.rectify(photo, toPhoto, W, H), CHROMA_RADIUS)
            val icons = heroesPath?.let { icons(chrome.matchColours(raw, it)) } ?: run {
                val normal = icons(chrome.matchColours(raw, heroesPath = false))
                val trail = icons(chrome.matchColours(raw, heroesPath = true))
                heroesPath = trail.anchors > normal.anchors
                if (heroesPath == true) trail else normal
            }
            toPhoto = refine(icons, toPhoto, radius) ?: break
        }
        val screenImage = chrome.matchColours(smoothChroma(Rectifier.rectify(photo, toPhoto, W, H), CHROMA_RADIUS), heroesPath == true)
        return when (val scan = scanner.scan(screenImage, ShotSource.CAMERA, heroesPath)) {
            // A photo's few icons can line up by chance where a screenshot's can't; ask for more.
            is ShotScan.Read -> if (scan.registration.anchors < MIN_ANCHORS || scan.registration.rmsPx > MAX_RMS_PX) tooFewIcons() else scan
            is ShotScan.Rejected -> when (scan.reason) {
                RejectReason.TOO_FEW_ICONS -> tooFewIcons()
                RejectReason.NOT_MIN_ZOOM -> ShotScan.Rejected(
                    scan.reason,
                    "This map isn't fully zoomed out. Zoom all the way out (right stick) and take the photo again.",
                )
                else -> scan
            }
        }
    }

    /** The best candidate's canonical → photo homography and how much its frame looks like the reference. */
    private fun locate(photo: MapImage): Pair<Homography, Double>? {
        var best: Pair<Homography, Double>? = null
        for (outline in ScreenFinder.candidates(photo)) for (turn in 0..3) {
            val corners = List(4) { outline[(it + turn) % 4] }
            val h = Homography.fit(ScreenChrome.CORNERS, corners) ?: continue
            val thumb = Rectifier.rectify(photo, h, ScreenChrome.THUMB_W, ScreenChrome.THUMB_H, ScreenChrome.THUMB_W / W.toDouble())
            val score = chrome.frameScore(thumb)
            if (score > (best?.second ?: -2.0)) best = h to score
        }
        return best
    }

    private class Icons(val groups: List<Pair<List<ShrineMapScanner.Detection>, List<WorldPoint>>>, val registration: MapRegistration?) {
        val anchors: Int get() = registration?.anchors ?: 0
    }

    private fun icons(screen: MapImage): Icons {
        val m = MapMasks.of(screen)
        val groups = listOf(
            scanner.detectShrines(m, ShrineMapScanner.CAMERA) to shrinePoints,
            scanner.detectTowers(m, ShrineMapScanner.CAMERA) to data.towers,
        )
        return Icons(groups, scanner.register(groups, data.minZoomScale) ?: scanner.registerFreeScale(groups))
    }

    /**
     * A better canonical → photo homography from the icons found on the current warp: each icon
     * within [radius] of where the registration puts a world point is a (world, photo) pair. Null
     * when there aren't enough, or the map doesn't look fully zoomed out (left for the scanner to say).
     */
    private fun refine(icons: Icons, toPhoto: Homography, radius: Double): Homography? {
        val reg = icons.registration ?: return null
        if (abs(reg.fittedScale / data.minZoomScale - 1) > ZOOM_TOLERANCE) return null
        val world = ArrayList<Point>()
        val inPhoto = ArrayList<Point>()
        for ((detections, points) in icons.groups) for (d in detections) {
            var nearest: WorldPoint? = null
            var dist = radius
            for (w in points) {
                val e = hypot(d.x - (reg.scale * w.x + reg.tx), d.y - (reg.scale * w.z + reg.ty))
                if (e < dist) {
                    dist = e
                    nearest = w
                }
            }
            if (nearest == null) continue
            world += Point(nearest.x, nearest.z)
            inPhoto += toPhoto.map(d.x + 0.5, d.y + 0.5)
        }
        val k = toPhoto.localScale(CENTRE)
        val (worldToPhoto, inliers) = Homography.ransac(world, inPhoto, threshold = 2.5 * k) ?: return null
        if (inliers.size < MIN_INLIERS) return null
        // Pixel (i, j) covers canonical [i, i+1), and the scanner places world points at s·world + t in pixel indices.
        val s = data.minZoomScale
        return worldToPhoto * Homography.scaleTranslate(1 / s, -(reg.tx + 0.5) / s, -(reg.ty + 0.5) / s)
    }

    /** True when the screen is the whole image: a resized screenshot rather than a photo. */
    private fun fillsPhoto(screen: Homography, photo: MapImage): Boolean {
        val tol = 0.02 * max(photo.width, photo.height)
        val photoCorners = listOf(Point(0.0, 0.0), Point(photo.width.toDouble(), 0.0), Point(photo.width.toDouble(), photo.height.toDouble()), Point(0.0, photo.height.toDouble()))
        return ScreenChrome.CORNERS.all { c ->
            val p = screen.map(c)
            photoCorners.any { hypot(it.x - p.x, it.y - p.y) < tol }
        }
    }

    private fun tooFewIcons() = ShotScan.Rejected(
        RejectReason.TOO_FEW_ICONS,
        "Couldn't line this photo up with the map: it needs at least $MIN_ANCHORS shrine or tower icons in sharp " +
            "focus. Use the 2D map, fully zoomed out, keep glare off the screen, or use a screenshot from the Switch album.",
    )

    private fun noScreen() = ShotScan.Rejected(
        RejectReason.NO_SCREEN,
        "Couldn't find the map screen in this photo. Get the whole screen in the picture with a little space around it, " +
            "and avoid reflections.",
    )

    private fun tooSmall() = ShotScan.Rejected(
        RejectReason.SCREEN_TOO_SMALL,
        "The screen is too small in this photo to read the icons. Move closer or zoom in so the screen fills most of the picture.",
    )

    companion object {
        private const val W = ShrineMapScanner.WIDTH
        private const val H = ShrineMapScanner.HEIGHT
        private val CENTRE = Point(W / 2.0, H / 2.0)

        /** Frame likeness a screen needs; real photos score about 0.9, a lit room or glare well under 0.4. */
        private const val MIN_FRAME_SCORE = 0.6

        /** Photo pixels per screen pixel below which icons are too blurred to read. */
        private const val MIN_PHOTO_PX = 0.75

        /** Pairing radius for each refinement pass, in screen pixels: wide while the outline is rough, then tight. */
        private val REFINE_RADII = doubleArrayOf(14.0, 8.0)
        private const val MIN_INLIERS = 8

        /** Box radius for smoothing colour moiré, in screen pixels: wide enough for the stripes, narrow next to a 3 px outline. */
        private const val CHROMA_RADIUS = 1

        /** A photo's final fit needs this many icons, this close; screenshots manage with 3 (see [ShrineMapScanner]). */
        private const val MIN_ANCHORS = 8
        private const val MAX_RMS_PX = 2.0

        /** The outline alone can be a few percent off; a zoomed-in map is much further. */
        private const val ZOOM_TOLERANCE = 0.06
    }
}
