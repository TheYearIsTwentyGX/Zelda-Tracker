package gx.zelda.screencam.live

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** ImageIO, with TwelveMonkeys' plugin on the classpath for the WebP map tiles. */
actual fun decodeGrayImage(bytes: ByteArray): GrayImage? {
    val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
    val px = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    return GrayImage(image.width, image.height, FloatArray(px.size) {
        val c = px[it]
        0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
    })
}
