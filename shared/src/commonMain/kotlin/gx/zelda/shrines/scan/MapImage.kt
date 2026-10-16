package gx.zelda.shrines.scan

/** A decoded screenshot: [argb] holds `width × height` pixels, row by row, as 0xAARRGGBB. */
class MapImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "expected ${width * height} pixels, got ${argb.size}" }
    }
}

/**
 * Decodes a JPEG or PNG; null if the bytes aren't an image the platform can read. Android halves big
 * photos until their long side is at most [MAX_PHOTO_SIDE]; screenshots are never that big.
 */
expect fun decodeMapImage(bytes: ByteArray): MapImage?

/** The longest side a decoded photo keeps; more pixels than this add memory, not detail. */
const val MAX_PHOTO_SIDE = 4096
