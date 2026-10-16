package gx.zelda.screencam.live

import gx.zelda.shrines.scan.MapImage
import kotlin.math.floor

/** A grey image, 0–255 as floats, row by row: a camera frame's luma or a map tile. */
class GrayImage(val width: Int, val height: Int, val pixels: FloatArray = FloatArray(width * height)) {
    init {
        require(pixels.size == width * height) { "expected ${width * height} pixels, got ${pixels.size}" }
    }

    operator fun get(x: Int, y: Int): Float = pixels[y * width + x]

    /** Bilinear sample at pixel coordinates (centres on integers); NaN outside the image. */
    fun sample(fx: Double, fy: Double): Float {
        if (fx < 0 || fy < 0 || fx > width - 1 || fy > height - 1) return Float.NaN
        val x0 = floor(fx).toInt().coerceAtMost(width - 2)
        val y0 = floor(fy).toInt().coerceAtMost(height - 2)
        val ax = (fx - x0).toFloat()
        val ay = (fy - y0).toFloat()
        val i = y0 * width + x0
        val top = pixels[i] + ax * (pixels[i + 1] - pixels[i])
        val bottom = pixels[i + width] + ax * (pixels[i + width + 1] - pixels[i + width])
        return top + ay * (bottom - top)
    }

    /** Half the size, each pixel the mean of a 2×2 block. */
    fun half(): GrayImage {
        val w = width / 2
        val h = height / 2
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val r0 = 2 * y * width
            val r1 = r0 + width
            for (x in 0 until w) {
                out[y * w + x] = 0.25f * (pixels[r0 + 2 * x] + pixels[r0 + 2 * x + 1] + pixels[r1 + 2 * x] + pixels[r1 + 2 * x + 1])
            }
        }
        return GrayImage(w, h, out)
    }

    /** [k] times smaller, each pixel the mean of a k×k block. */
    fun shrink(k: Int): GrayImage {
        if (k <= 1) return this
        val w = width / k
        val h = height / k
        val out = FloatArray(w * h)
        val inv = 1f / (k * k)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (dy in 0 until k) {
                val o = (y * k + dy) * width + x * k
                for (dx in 0 until k) s += pixels[o + dx]
            }
            out[y * w + x] = s * inv
        }
        return GrayImage(w, h, out)
    }

    /** As an opaque grey [MapImage], for code that works on colour images. */
    fun toMapImage(): MapImage = MapImage(width, height, IntArray(pixels.size) {
        val v = pixels[it].toInt().coerceIn(0, 255)
        (0xff shl 24) or (v shl 16) or (v shl 8) or v
    })

    companion object {
        /** A camera frame's luma plane, [rowStride] bytes apart, unsigned. */
        fun fromLuma(luma: ByteArray, width: Int, height: Int, rowStride: Int = width): GrayImage {
            val out = FloatArray(width * height)
            for (y in 0 until height) {
                val o = y * rowStride
                for (x in 0 until width) out[y * width + x] = (luma[o + x].toInt() and 0xff).toFloat()
            }
            return GrayImage(width, height, out)
        }

        fun fromMapImage(image: MapImage): GrayImage = GrayImage(image.width, image.height, FloatArray(image.argb.size) {
            val c = image.argb[it]
            0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
        })
    }
}

/** Decodes a WebP, PNG or JPEG to grey; null if the platform can't read it. */
expect fun decodeGrayImage(bytes: ByteArray): GrayImage?
