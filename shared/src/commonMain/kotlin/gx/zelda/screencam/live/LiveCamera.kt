package gx.zelda.screencam.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Where the app stands with the camera permission. */
enum class CameraAccess {
    GRANTED,

    /** Never asked in this screen's lifetime: explain, then ask. */
    NOT_ASKED,

    /** Refused once: the system will still show its dialog again. */
    DENIED,

    /** Refused for good ("Don't ask again", or twice): only the app's settings can change it. */
    DENIED_PERMANENTLY,

    /** No camera on this platform. */
    UNAVAILABLE,
    ;

    companion object {
        /**
         * Android doesn't say "denied for good" outright: after a refusal, the system offers a
         * rationale while it would still show the dialog, and stops once it won't.
         */
        fun of(granted: Boolean, asked: Boolean, showRationale: Boolean): CameraAccess = when {
            granted -> GRANTED
            !asked -> NOT_ASKED
            showRationale -> DENIED
            else -> DENIED_PERMANENTLY
        }
    }
}

class CameraPermission(val access: CameraAccess, val request: () -> Unit, val openSettings: () -> Unit)

/** The camera permission, re-checked whenever the app comes back to the front. */
@Composable
expect fun rememberCameraPermission(): CameraPermission

/**
 * The back camera's preview, filling [modifier]'s bounds (centre-cropped), with every analysed
 * frame's luma passed to [onFrame], turned upright as the preview shows it. [onFrame] runs on the
 * camera's analysis thread and may take its time: frames that arrive meanwhile are dropped.
 */
@Composable
expect fun LiveCameraView(onFrame: suspend (GrayImage) -> Unit, modifier: Modifier = Modifier)

/** Writes a line of live-scan statistics where developers can read it (logcat tag `KorokScan`). */
expect fun logLiveStats(line: String)

/** True in a debuggable build: the stats overlay starts shown. */
@Composable
expect fun isDebugBuild(): Boolean
