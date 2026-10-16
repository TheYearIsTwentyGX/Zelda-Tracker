package gx.zelda.screencam

import gx.zelda.shrines.scan.MapImage
import gx.zelda.shrines.scan.decodeMapImage
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A synthetic phone photo of a screen showing a screenshot: a tilted 16:9 panel in a dim room, drawn
 * through the panel's RGB stripes. Each photo pixel averages the light over its own area widened by
 * the lens blur and any hand shake, as a sensor does, so the stripes alias into moiré the way they
 * do in real photos. Then tone curve, glare, noise and JPEG.
 */
object FakeCamera {
    class Shot(
        val width: Int = 3000,
        val height: Int = 2250,
        /** Share of the photo's width the screen spans. */
        val screenWidth: Double = 0.75,
        val yawDeg: Double = 0.0,
        val pitchDeg: Double = 0.0,
        val rollDeg: Double = 0.0,
        val shift: Point = Point(0.0, 0.0),
        /** Red, green and blue gain (white balance). */
        val gains: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0),
        val exposure: Double = 1.0,
        val gamma: Double = 1.0,
        /** Lens blur (Gaussian sigma), in photo pixels. */
        val blur: Double = 0.8,
        /** Horizontal hand shake during the exposure, in photo pixels. */
        val motion: Double = 0.0,
        val noise: Double = 2.0,
        /** Peak brightness of a soft glare spot right of centre. */
        val glare: Double = 0.0,
        val room: Double = 35.0,
        val jpegQuality: Float = 0.9f,
        val seed: Int = 7,
        /** Light samples per photo pixel. */
        val samples: Int = 16,
        /** Box radius of the phone's colour noise reduction, in photo pixels; 0 for none. */
        val chromaDenoise: Int = 2,
    )

    /** The screen's corners (top left, top right, bottom right, bottom left) in photo pixels. */
    fun corners(s: Shot): List<Point> {
        val f = 0.78 * s.width
        val d = f / (s.screenWidth * s.width)
        val yaw = Math.toRadians(s.yawDeg)
        val pitch = Math.toRadians(s.pitchDeg)
        val roll = Math.toRadians(s.rollDeg)
        return listOf(-0.5 to -0.28125, 0.5 to -0.28125, 0.5 to 0.28125, -0.5 to 0.28125).map { (x0, y0) ->
            val x1 = x0 * cos(roll) - y0 * sin(roll)
            val y1 = x0 * sin(roll) + y0 * cos(roll)
            val y2 = y1 * cos(pitch)
            val z2 = y1 * sin(pitch)
            val x3 = x1 * cos(yaw) + z2 * sin(yaw)
            val z3 = -x1 * sin(yaw) + z2 * cos(yaw) + d
            Point(s.width / 2.0 + s.shift.x + f * x3 / z3, s.height / 2.0 + s.shift.y + f * y2 / z3)
        }
    }

    fun shoot(screen: MapImage, s: Shot): MapImage {
        val toScreen = Homography.fit(corners(s), ScreenChrome.CORNERS.map { Point(it.x * screen.width / ScreenChrome.WIDTH, it.y * screen.height / ScreenChrome.HEIGHT) })!!
        val w = s.width
        val h = s.height
        val planes = List(3) { FloatArray(w * h) }
        val random = Random(s.seed)
        // Where each pixel gathers light: its own square, spread by the lens and the shake.
        val dx = DoubleArray(s.samples) { random.nextDouble() + random.nextGaussian() * s.blur + (random.nextDouble() - 0.5) * s.motion }
        val dy = DoubleArray(s.samples) { random.nextDouble() + random.nextGaussian() * s.blur }
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (k in 0 until s.samples) {
                val p = toScreen.map(x + dx[k], y + dy[k])
                if (p.x >= 0 && p.y >= 0 && p.x < screen.width && p.y < screen.height) {
                    // Each screen pixel is three vertical stripes with a dark gap below them.
                    val c = screen.argb[p.y.toInt() * screen.width + p.x.toInt()]
                    if (p.y - floor(p.y) < 0.88) {
                        when (((p.x - floor(p.x)) * 3).toInt()) {
                            0 -> r += 3 * ((c shr 16) and 0xff) / 0.88f
                            1 -> g += 3 * ((c shr 8) and 0xff) / 0.88f
                            else -> b += 3 * (c and 0xff) / 0.88f
                        }
                    }
                } else if (p.x > -40 && p.y > -40 && p.x < screen.width + 40 && p.y < screen.height + 40) {
                    r += 10f; g += 10f; b += 12f // bezel
                } else {
                    val v = (s.room * (0.6 + 0.4 * sin(x / 310.0) * cos(y / 270.0))).toFloat()
                    r += v * 1.05f; g += v; b += v * 0.9f
                }
            }
            val n = s.samples.toFloat()
            planes[0][y * w + x] = r / n
            planes[1][y * w + x] = g / n
            planes[2][y * w + x] = b / n
        }
        if (s.chromaDenoise > 0) chromaDenoise(planes, w, h, s.chromaDenoise)
        val gx = w * 0.55
        val gy = h * 0.45
        val gs = 0.18 * w
        val out = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val glare = s.glare * exp(-((x - gx) * (x - gx) + (y - gy) * (y - gy)) / (2 * gs * gs))
            val ch = IntArray(3) { c ->
                val lin = (planes[c][i] / 255.0 * s.gains[c] * s.exposure).coerceAtLeast(0.0)
                val v = 255.0 * lin.pow(s.gamma) + 6 + glare + random.nextDouble(-1.7, 1.7) * s.noise
                v.roundToInt().coerceIn(0, 255)
            }
            (0xff shl 24) or (ch[0] shl 16) or (ch[1] shl 8) or ch[2]
        }
        return jpeg(MapImage(w, h, out), s.jpegQuality)
    }

    /** What a phone's image processor does to colour noise: blur Cb and Cr, keep Y. */
    private fun chromaDenoise(planes: List<FloatArray>, w: Int, h: Int, radius: Int) {
        val (r, g, b) = planes
        val y = FloatArray(w * h) { 0.299f * r[it] + 0.587f * g[it] + 0.114f * b[it] }
        val cb = FloatArray(w * h) { b[it] - y[it] }
        val cr = FloatArray(w * h) { r[it] - y[it] }
        for (plane in listOf(cb, cr)) {
            val tmp = FloatArray(plane.size)
            for (yy in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -radius..radius) sum += plane[yy * w + (x + d).coerceIn(0, w - 1)]
                tmp[yy * w + x] = sum / (2 * radius + 1)
            }
            for (yy in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -radius..radius) sum += tmp[(yy + d).coerceIn(0, h - 1) * w + x]
                plane[yy * w + x] = sum / (2 * radius + 1)
            }
        }
        for (i in 0 until w * h) {
            r[i] = y[i] + cr[i]
            b[i] = y[i] + cb[i]
            g[i] = (y[i] - 0.299f * r[i] - 0.114f * b[i]) / 0.587f
        }
    }

    /** Box–Muller. */
    private fun Random.nextGaussian(): Double = sqrt(-2 * ln(1 - nextDouble())) * cos(2 * Math.PI * nextDouble())

    /** [image] turned a quarter clockwise. */
    fun rotate(image: MapImage): MapImage {
        val w = image.height
        val h = image.width
        return MapImage(w, h, IntArray(w * h) { i -> image.argb[(image.height - 1 - i % w) * image.width + i / w] })
    }

    private fun jpeg(image: MapImage, quality: Float): MapImage {
        val bi = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        bi.setRGB(0, 0, image.width, image.height, image.argb, 0, image.width)
        val bytes = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        ImageIO.createImageOutputStream(bytes).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(bi, null, null), param)
        }
        writer.dispose()
        return decodeMapImage(bytes.toByteArray())!!
    }
}
