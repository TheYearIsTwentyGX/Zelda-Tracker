package gx.zelda.core

/** Width and height from a WebP's RIFF header, for the lossy, lossless and extended formats. */
fun webpSize(b: ByteArray): Pair<Int, Int>? {
    fun u(i: Int) = b[i].toInt() and 0xff
    if (b.size < 30 || String(b, 0, 4) != "RIFF" || String(b, 8, 4) != "WEBP") return null
    return when (webpFormat(b)) {
        "VP8 " -> {
            if (u(23) != 0x9d || u(24) != 0x01 || u(25) != 0x2a) return null
            ((u(26) or (u(27) shl 8)) and 0x3fff) to ((u(28) or (u(29) shl 8)) and 0x3fff)
        }
        "VP8L" -> {
            val bits = u(21) or (u(22) shl 8) or (u(23) shl 16) or (u(24) shl 24)
            ((bits and 0x3fff) + 1) to (((bits shr 14) and 0x3fff) + 1)
        }
        "VP8X" -> ((u(24) or (u(25) shl 8) or (u(26) shl 16)) + 1) to ((u(27) or (u(28) shl 8) or (u(29) shl 16)) + 1)
        else -> null
    }
}

/** The WebP's first chunk: "VP8 " (lossy), "VP8L" (lossless) or "VP8X" (extended); null if it isn't a WebP. */
fun webpFormat(b: ByteArray): String? =
    if (b.size < 16 || String(b, 0, 4) != "RIFF" || String(b, 8, 4) != "WEBP") null else String(b, 12, 4)
