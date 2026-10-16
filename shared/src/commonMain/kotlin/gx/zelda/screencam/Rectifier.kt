package gx.zelda.screencam

import gx.zelda.shrines.scan.MapImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Warps part of a photo into an upright image of the screen. */
object Rectifier {
    /**
     * Samples [photo] at canonical screen positions: output pixel (x, y) is canonical point
     * ((x + ½) / [scale], (y + ½) / [scale]), taken through [screenToPhoto]. Where the photo has more
     * than ~1.3 pixels per output pixel, each output pixel averages an n×n grid of bilinear samples,
     * which smooths moiré instead of aliasing it. Outside the photo is black.
     */
    fun rectify(photo: MapImage, screenToPhoto: Homography, width: Int, height: Int, scale: Double = 1.0): MapImage {
        val k = screenToPhoto.localScale(Point(width / 2.0 / scale, height / 2.0 / scale)) / scale
        val n = ceil(k / 1.3).toInt().coerceIn(1, 4)
        val out = IntArray(width * height)
        val m = screenToPhoto.m
        val rgb = DoubleArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0.0
            var g = 0.0
            var b = 0.0
            for (sy in 0 until n) for (sx in 0 until n) {
                val cx = (x + (sx + 0.5) / n) / scale
                val cy = (y + (sy + 0.5) / n) / scale
                val w = m[6] * cx + m[7] * cy + m[8]
                val px = (m[0] * cx + m[1] * cy + m[2]) / w
                val py = (m[3] * cx + m[4] * cy + m[5]) / w
                bilinear(photo, px - 0.5, py - 0.5, rgb)
                r += rgb[0]
                g += rgb[1]
                b += rgb[2]
            }
            val nn = (n * n).toDouble()
            out[y * width + x] = (0xff shl 24) or (channel(r / nn) shl 16) or (channel(g / nn) shl 8) or channel(b / nn)
        }
        return MapImage(width, height, out)
    }

    private fun channel(v: Double) = v.roundToInt().coerceIn(0, 255)

    /** Bilinear sample at pixel coordinates (centres on integers), edges clamped, black well outside. */
    private fun bilinear(img: MapImage, fx: Double, fy: Double, out: DoubleArray) {
        out.fill(0.0)
        if (fx < -1 || fy < -1 || fx > img.width || fy > img.height) return
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val ax = fx - x0
        val ay = fy - y0
        for (j in 0..1) for (i in 0..1) {
            val wgt = (if (i == 0) 1 - ax else ax) * (if (j == 0) 1 - ay else ay)
            if (wgt == 0.0) continue
            val c = img.argb[(y0 + j).coerceIn(0, img.height - 1) * img.width + (x0 + i).coerceIn(0, img.width - 1)]
            out[0] += wgt * ((c shr 16) and 0xff)
            out[1] += wgt * ((c shr 8) and 0xff)
            out[2] += wgt * (c and 0xff)
        }
    }
}

/**
 * Averages colour (not brightness) over a (2·[radius]+1)² box. A camera turns the screen's RGB
 * stripes into a fine colour checkerboard that breaks thin icon outlines into specks; brightness
 * edges stay sharp.
 */
fun smoothChroma(image: MapImage, radius: Int = 2): MapImage {
    val w = image.width
    val h = image.height
    val n = w * h
    val y = FloatArray(n)
    val cb = FloatArray(n)
    val cr = FloatArray(n)
    for (i in 0 until n) {
        val c = image.argb[i]
        val r = ((c shr 16) and 0xff).toFloat()
        val g = ((c shr 8) and 0xff).toFloat()
        val b = (c and 0xff).toFloat()
        y[i] = 0.299f * r + 0.587f * g + 0.114f * b
        cb[i] = b - y[i]
        cr[i] = r - y[i]
    }
    boxBlur(cb, w, h, radius)
    boxBlur(cr, w, h, radius)
    val out = IntArray(n) { i ->
        val r = y[i] + cr[i]
        val b = y[i] + cb[i]
        val g = (y[i] - 0.299f * r - 0.114f * b) / 0.587f
        (0xff shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
    return MapImage(w, h, out)
}

private fun channel(v: Float) = v.roundToInt().coerceIn(0, 255)

/** Separable box blur with running sums; edges clamped. */
private fun boxBlur(v: FloatArray, w: Int, h: Int, r: Int) {
    val tmp = FloatArray(v.size)
    val size = 2f * r + 1
    for (row in 0 until h) {
        val o = row * w
        var s = 0f
        for (i in -r..r) s += v[o + i.coerceIn(0, w - 1)]
        for (x in 0 until w) {
            tmp[o + x] = s / size
            s += v[o + (x + r + 1).coerceAtMost(w - 1)] - v[o + (x - r).coerceAtLeast(0)]
        }
    }
    for (x in 0 until w) {
        var s = 0f
        for (i in -r..r) s += tmp[i.coerceIn(0, h - 1) * w + x]
        for (row in 0 until h) {
            v[row * w + x] = s / size
            s += tmp[(row + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(row - r).coerceAtLeast(0) * w + x]
        }
    }
}
