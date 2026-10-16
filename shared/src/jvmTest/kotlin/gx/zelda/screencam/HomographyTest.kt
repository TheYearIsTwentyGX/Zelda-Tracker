package gx.zelda.screencam

import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomographyTest {
    /** A screen seen from the side: 1920×1080 canonical corners onto a keystoned quad. */
    private val corners = listOf(Point(551.0, 663.0), Point(3620.0, 643.0), Point(3538.0, 2404.0), Point(644.0, 2288.0))

    private fun assertNear(expected: Point, actual: Point, tolerance: Double, label: String = "") =
        assertTrue(hypot(expected.x - actual.x, expected.y - actual.y) < tolerance, "$label expected $expected, got $actual")

    @Test
    fun fourPointsFitExactly() {
        val h = assertNotNull(Homography.fit(ScreenChrome.CORNERS, corners))
        for (i in 0..3) assertNear(corners[i], h.map(ScreenChrome.CORNERS[i]), 1e-6)
    }

    @Test
    fun inverseAndCompositionUndoEachOther() {
        val h = assertNotNull(Homography.fit(ScreenChrome.CORNERS, corners))
        val p = Point(700.0, 300.0)
        assertNear(p, h.inverse().map(h.map(p)), 1e-6)
        assertNear(p, (h.inverse() * h).map(p), 1e-6)
        val shift = Homography.scaleTranslate(2.0, 5.0, 7.0)
        assertNear(h.map(shift.map(p)), (h * shift).map(p), 1e-6)
    }

    @Test
    fun threePointsDontFit() {
        assertNull(Homography.fit(corners.take(3), corners.take(3)))
    }

    @Test
    fun leastSquaresAveragesNoise() {
        val truth = assertNotNull(Homography.fit(ScreenChrome.CORNERS, corners))
        val random = Random(3)
        val src = List(60) { Point(random.nextDouble(0.0, 1920.0), random.nextDouble(0.0, 1080.0)) }
        val dst = src.map { truth.map(it).let { p -> Point(p.x + random.nextDouble(-1.5, 1.5), p.y + random.nextDouble(-1.5, 1.5)) } }
        val fit = assertNotNull(Homography.fit(src, dst))
        for (c in ScreenChrome.CORNERS) assertNear(truth.map(c), fit.map(c), 2.0, "corner $c")
    }

    @Test
    fun ransacIgnoresOutliers() {
        val truth = assertNotNull(Homography.fit(ScreenChrome.CORNERS, corners))
        val random = Random(5)
        val src = List(80) { Point(random.nextDouble(0.0, 1920.0), random.nextDouble(0.0, 1080.0)) }
        // 30% of the pairs are wrong, as when an icon is matched to the wrong shrine.
        val dst = src.mapIndexed { i, p -> if (i % 10 < 3) Point(random.nextDouble(0.0, 4000.0), random.nextDouble(0.0, 3000.0)) else truth.map(p) }
        val (fit, inliers) = assertNotNull(Homography.ransac(src, dst, threshold = 3.0))
        assertEquals(src.indices.filter { it % 10 >= 3 }, inliers)
        for (c in ScreenChrome.CORNERS) assertNear(truth.map(c), fit.map(c), 0.01, "corner $c")
        assertNull(Homography.ransac(src.take(3), dst.take(3), threshold = 3.0))
    }

    @Test
    fun localScaleIsPixelsPerPixel() {
        assertEquals(2.0, Homography.scaleTranslate(2.0, 10.0, -4.0).localScale(Point(100.0, 100.0)), 1e-9)
    }
}
