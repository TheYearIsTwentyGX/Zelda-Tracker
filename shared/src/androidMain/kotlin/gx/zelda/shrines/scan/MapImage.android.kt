package gx.zelda.shrines.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory

actual fun decodeMapImage(bytes: ByteArray): MapImage? {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        MapImage(bitmap.width, bitmap.height, pixels)
    } finally {
        bitmap.recycle()
    }
}
