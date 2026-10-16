package gx.zelda.shrines.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

actual fun decodeMapImage(bytes: ByteArray): MapImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // A 50 MP photo would need 200 MB of pixels; halve it until it fits MAX_PHOTO_SIDE.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
        inSampleSize = sample
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
