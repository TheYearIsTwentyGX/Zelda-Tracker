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
actual fun LiveCameraView(onFrame: suspend (GrayImage) -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("No camera on this platform") }
}

actual fun logLiveStats(line: String) = Unit

@Composable
actual fun isDebugBuild(): Boolean = false
