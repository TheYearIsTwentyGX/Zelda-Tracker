package gx.zelda.screencam.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory

actual fun decodeGrayImage(bytes: ByteArray): GrayImage? {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return try {
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        GrayImage(bitmap.width, bitmap.height, FloatArray(px.size) {
            val c = px[it]
            0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
        })
    } finally {
        bitmap.recycle()
    }
}
