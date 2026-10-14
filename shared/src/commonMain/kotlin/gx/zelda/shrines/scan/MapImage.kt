package gx.zelda.shrines.scan

/** A decoded screenshot: [argb] holds `width × height` pixels, row by row, as 0xAARRGGBB. */
class MapImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "expected ${width * height} pixels, got ${argb.size}" }
    }
}

/** Decodes a JPEG or PNG; null if the bytes aren't an image the platform can read. */
expect fun decodeMapImage(bytes: ByteArray): MapImage?
