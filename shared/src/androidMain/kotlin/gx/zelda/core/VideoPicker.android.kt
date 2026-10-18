package gx.zelda.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import gx.zelda.shrines.scan.MapImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberVideoPicker(onPicked: (PickedVideo) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri) ?: "Recording"
            callback(PickedVideo(name) { everyMs, maxWidth, onFrame -> readFrames(context, uri, everyMs, maxWidth, onFrame) })
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
}

private suspend fun readFrames(
    context: Context,
    uri: Uri,
    everyMs: Long,
    maxWidth: Int,
    onFrame: suspend (Int, Int, MapImage) -> Unit,
) {
    val retriever = MediaMetadataRetriever()
    try {
        withContext(Dispatchers.IO) { retriever.setDataSource(context, uri) }
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maxWidth
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maxWidth
        val total = (durationMs / everyMs + 1).toInt()
        for (i in 0 until total) {
            val bitmap = withContext(Dispatchers.Default) { frameAt(retriever, i * everyMs * 1000, width, height, maxWidth) } ?: continue
            val image = try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                MapImage(bitmap.width, bitmap.height, pixels)
            } finally {
                bitmap.recycle()
            }
            onFrame(i, total, image)
        }
    } finally {
        retriever.release()
    }
}

/** The frame nearest [timeUs], no wider than [maxWidth]; null past the end or if it won't decode. */
private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long, width: Int, height: Int, maxWidth: Int): Bitmap? {
    val scale = minOf(1.0, maxWidth.toDouble() / width)
    val w = (width * scale).toInt()
    val h = (height * scale).toInt()
    val frame = if (Build.VERSION.SDK_INT >= 27) {
        retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, w, h)
    } else {
        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { full ->
            if (full.width <= maxWidth) full else Bitmap.createScaledBitmap(full, w, h, true).also { full.recycle() }
        }
    } ?: return null
    // getPixels needs a software ARGB bitmap.
    if (frame.config == Bitmap.Config.ARGB_8888) return frame
    return frame.copy(Bitmap.Config.ARGB_8888, false).also { frame.recycle() }
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
