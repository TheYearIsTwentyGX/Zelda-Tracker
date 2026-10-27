package gx.zelda.art

/** A picture's format and size, read from its first bytes without decoding it. */
data class ImageHeader(val format: String, val width: Int, val height: Int) {
    companion object {
        /** PNG, WebP (lossy, lossless or extended) or JPEG; null for anything else or a truncated header. */
        fun read(b: ByteArray): ImageHeader? = runCatching { png(b) ?: webp(b) ?: jpeg(b) }.getOrNull()

        private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
        private fun be32(b: ByteArray, i: Int) = (u8(b, i) shl 24) or (u8(b, i + 1) shl 16) or (u8(b, i + 2) shl 8) or u8(b, i + 3)
        private fun be16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
        private fun le16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
        private fun le24(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8) or (u8(b, i + 2) shl 16)
        private fun ascii(b: ByteArray, i: Int, n: Int) = (i until i + n).map { b[it].toInt().toChar() }.joinToString("")

        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        private fun png(b: ByteArray): ImageHeader? {
            if (b.size < 24 || !(0 until 8).all { b[it] == PNG[it] } || ascii(b, 12, 4) != "IHDR") return null
            return ImageHeader("png", be32(b, 16), be32(b, 20))
        }

        private fun webp(b: ByteArray): ImageHeader? {
            if (b.size < 30 || ascii(b, 0, 4) != "RIFF" || ascii(b, 8, 4) != "WEBP") return null
            return when (ascii(b, 12, 4)) {
                "VP8 " -> ImageHeader("webp", le16(b, 26) and 0x3FFF, le16(b, 28) and 0x3FFF)
                "VP8L" -> {
                    val bits = u8(b, 21) or (u8(b, 22) shl 8) or (u8(b, 23) shl 16) or (u8(b, 24) shl 24)
                    ImageHeader("webp", (bits and 0x3FFF) + 1, ((bits shr 14) and 0x3FFF) + 1)
                }
                "VP8X" -> ImageHeader("webp", le24(b, 24) + 1, le24(b, 27) + 1)
                else -> null
            }
        }

        /** JPEG: the first start-of-frame marker holds the size. */
        private fun jpeg(b: ByteArray): ImageHeader? {
            if (b.size < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) return null
            var i = 2
            while (i + 9 < b.size) {
                if (u8(b, i) != 0xFF) return null
                val marker = u8(b, i + 1)
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    return ImageHeader("jpeg", be16(b, i + 7), be16(b, i + 5))
                }
                i += 2 + be16(b, i + 2)
            }
            return null
        }
    }
}
