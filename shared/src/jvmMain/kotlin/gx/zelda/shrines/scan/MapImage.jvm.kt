package gx.zelda.shrines.scan

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual fun decodeMapImage(bytes: ByteArray): MapImage? {
    val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
    val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    return MapImage(image.width, image.height, pixels)
}
