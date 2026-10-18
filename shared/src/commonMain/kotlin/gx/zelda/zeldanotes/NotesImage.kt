package gx.zelda.zeldanotes

import gx.zelda.shrines.scan.MapImage
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A Zelda Notes Navigation screenshot (or recording frame), resized so its Korok droplets are the
 * template's size: grey, plus how much of each pixel is droplet tan and shrine blue (0–1).
 * Rows above [top] and from [bottom] down are the app's header and Categories bar, never read.
 */
class NotesImage(
    val width: Int,
    val height: Int,
    val grey: FloatArray,
    val tan: FloatArray,
    val blue: FloatArray,
    val top: Int,
    val bottom: Int,
    /** True when the "Hide Discovered Destinations" switch reads as on; false when it's off or not found. */
    val hideDiscovered: Boolean,
    /** Source pixels to work pixels. */
    val toWork: Double,
) {
    companion object {
        /**
         * On the reference phone (1224 px wide) the header's shading ends here, and the Categories
         * bar's starts this far up; droplets under either are paler, so neither band is read.
         */
        private const val TOP_REF = 500.0
        private const val BOTTOM_REF = 420.0

        /** Fewer whole droplets than this and the screen width sizes them instead. */
        private const val MIN_SIZED_BLOBS = 8

        /** The reference phone's screen width, where a droplet is the template crop's size. */
        const val REF_WIDTH = 1224.0

        fun of(image: MapImage, template: DropletTemplate): NotesImage {
            val iconScale = iconScale(image, template) ?: (image.width / REF_WIDTH)
            val f = template.workFactor / iconScale
            val w = max(1, (image.width * f).roundToInt())
            val h = max(1, (image.height * f).roundToInt())
            val grey = FloatArray(w * h)
            val tan = FloatArray(w * h)
            val blue = FloatArray(w * h)
            resample(image, w, h, grey, tan, blue)
            val k = iconScale * f
            return NotesImage(
                w, h, grey, tan, blue,
                top = (TOP_REF * k).roundToInt().coerceIn(0, h),
                bottom = (h - BOTTOM_REF * k).roundToInt().coerceIn(0, h),
                hideDiscovered = toggleOn(image, iconScale),
                toWork = f,
            )
        }

        /** Area-averages [image] down to w × h: grey, and the fraction of tan and blue pixels. */
        private fun resample(image: MapImage, w: Int, h: Int, grey: FloatArray, tan: FloatArray, blue: FloatArray) {
            val sx = image.width.toDouble() / w
            val sy = image.height.toDouble() / h
            val xs = IntArray(w + 1) { min(image.width, floor(it * sx).toInt()) }
            for (y in 0 until h) {
                val y0 = floor(y * sy).toInt()
                val y1 = max(y0 + 1, min(image.height, floor((y + 1) * sy).toInt()))
                for (x in 0 until w) {
                    val x0 = xs[x]
                    val x1 = max(x0 + 1, xs[x + 1])
                    var g = 0f
                    var t = 0
                    var b = 0
                    for (yy in y0 until y1) {
                        val o = yy * image.width
                        for (xx in x0 until min(x1, image.width)) {
                            val c = image.argb[o + xx]
                            g += luma(c)
                            when (colourOf(c)) {
                                TAN -> t++
                                BLUE -> b++
                            }
                        }
                    }
                    val n = ((y1 - y0) * (min(x1, image.width) - x0)).coerceAtLeast(1)
                    grey[y * w + x] = g / n
                    tan[y * w + x] = t.toFloat() / n
                    blue[y * w + x] = b.toFloat() / n
                }
            }
        }

        /**
         * How much bigger than the template crop this screen draws droplets: the median width of tan
         * blobs shaped like one whole droplet. Null when too few are seen to tell.
         */
        internal fun iconScale(image: MapImage, template: DropletTemplate): Double? {
            // Half resolution is plenty to measure blobs about 40 px wide.
            val w = image.width / 2
            val h = image.height / 2
            val mask = BooleanArray(w * h) { i -> colourOf(image.argb[(i / w) * 2 * image.width + (i % w) * 2]) == TAN }
            val widths = mutableListOf<Double>()
            for (b in Blobs.of(mask, w, h)) {
                val bw = b.width * 2.0
                val bh = b.height * 2.0
                val k = bw / template.fillWidth
                if (k < 0.5 || k > 2.0) continue
                val aspect = (bh / bw) / (template.fillHeight.toDouble() / template.fillWidth)
                val fill = (b.area * 4.0 / (bw * bh)) / template.fillDensity
                if (aspect in 0.9..1.1 && fill in 0.85..1.15) widths += k
            }
            if (widths.size < MIN_SIZED_BLOBS) return null
            widths.sort()
            return widths[widths.size / 2]
        }

        /**
         * Finds the switch's teal pill in the top-left and checks its white knob sits on the right.
         * [iconScale] sizes the search to this screen.
         */
        internal fun toggleOn(image: MapImage, iconScale: Double): Boolean {
            val x1 = (image.width * 0.45).toInt()
            val y0 = (250 * iconScale).toInt().coerceIn(0, image.height)
            val y1 = (520 * iconScale).toInt().coerceIn(0, image.height)
            var teal = 0
            var minX = Int.MAX_VALUE
            var maxX = -1
            var knobX = 0L
            var knob = 0
            for (y in y0 until y1) for (x in 0 until x1) {
                val c = image.argb[y * image.width + x]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                if (r < 40 && g in 105..155 && b in 140..190) {
                    teal++
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                } else if (r > 230 && g > 230 && b > 195) {
                    knob++
                    knobX += x
                }
            }
            // The pill is about 110 × 57 px on the reference phone, its knob a third of that.
            if (teal < 0.35 * 110 * 57 * iconScale * iconScale || knob == 0) return false
            return knobX.toDouble() / knob > (minX + maxX) / 2.0
        }

        internal const val OTHER = 0
        internal const val TAN = 1
        internal const val BLUE = 2

        /** Droplet tan (hue 30–64°, fairly saturated and bright) or shrine blue; anything else is map. */
        internal fun colourOf(c: Int): Int {
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            if (hi < 140 || hi == lo) return OTHER
            val s = (hi - lo).toFloat() / hi
            val hue = when (hi) {
                r -> 60f * (g - b) / (hi - lo)
                g -> 120f + 60f * (b - r) / (hi - lo)
                else -> 240f + 60f * (r - g) / (hi - lo)
            }.let { if (it < 0) it + 360f else it }
            return when {
                hue in 30f..64f && s >= 0.27f && hi >= 170 -> TAN
                hue in 190f..220f && s >= 0.35f -> BLUE
                else -> OTHER
            }
        }

        internal fun luma(c: Int): Float = 0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
    }
}

/** A 4-connected blob of a mask: its bounding box and pixel count. */
internal class Blob(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val area: Int) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
}

internal object Blobs {
    fun of(mask: BooleanArray, w: Int, h: Int): List<Blob> {
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val out = mutableListOf<Blob>()
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            var area = 0
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w
                val y = i / w
                area++
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                if (x > 0 && mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && mask[i - w] && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && mask[i + w] && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            out += Blob(minX, minY, maxX, maxY, area)
        }
        return out
    }
}
