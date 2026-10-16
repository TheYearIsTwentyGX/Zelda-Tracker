package gx.zelda.screencam.live

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** The JVM target only runs host tests: there's no camera. */
@Composable
actual fun rememberCameraPermission(): CameraPermission = CameraPermission(CameraAccess.UNAVAILABLE, request = {}, openSettings = {})

@Composable
actual fun LiveCameraView(onFrame: suspend (GrayImage) -> Unit, modifier: Modifier, onCameraInfo: (String) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("No camera on this platform") }
}

actual fun logLiveStats(line: String) = Unit

@Composable
actual fun isDebugBuild(): Boolean = false

/** Frames as JPEG and state as JSON lines in [dir]; the JVM's recorder, for tests. */
class FileFrameRecorder(private val dir: java.io.File) : FrameRecorder {
    override val location: String = dir.absolutePath

    init {
        dir.mkdirs()
    }

    override fun frame(index: Int, image: GrayImage) {
        val bi = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val v = image[x, y].toInt().coerceIn(0, 255)
            bi.setRGB(x, y, (v shl 16) or (v shl 8) or v)
        }
        javax.imageio.ImageIO.write(bi, "jpg", java.io.File(dir, "frame-%04d.jpg".format(index)))
    }

    override fun line(json: String) = java.io.File(dir, "state.jsonl").appendText(json + "\n")

    override fun session(json: String) = java.io.File(dir, "session.json").writeText(json + "\n")
}

@Composable
actual fun rememberFrameRecorderFactory(): (() -> FrameRecorder)? = null
