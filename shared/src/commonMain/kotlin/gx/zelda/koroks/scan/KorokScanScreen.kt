package gx.zelda.koroks.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.koroks.model.Korok
import gx.zelda.screencam.live.CameraAccess
import gx.zelda.screencam.live.KorokVerdict
import gx.zelda.screencam.live.LiveCameraView
import gx.zelda.screencam.live.LivePhase
import gx.zelda.screencam.live.isDebugBuild
import gx.zelda.screencam.live.rememberFrameRecorderFactory
import gx.zelda.screencam.live.rememberCameraPermission
import kotlin.math.max

/** Korok Seeds → ⋮ → Scan the map live: the camera, what it's found, then the review before Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KorokScanScreen(state: KorokScanUiState?, loadFailed: Boolean, viewModel: KorokScanViewModel, onBack: () -> Unit) {
    val debug = isDebugBuild()
    val recorders = rememberFrameRecorderFactory()
    var showStats by rememberSaveable { mutableStateOf(debug) }
    var confirming by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Scan Koroks live")
                        if (state != null) {
                            Text(state.saveFile.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state != null && state.review == null && state.applied == null) {
                        if (debug && recorders != null) {
                            TextButton(onClick = { if (state.recording == null) viewModel.startRecording(recorders) else viewModel.stopRecording() }) {
                                Text(if (state.recording == null) "Save frames" else "Stop saving")
                            }
                        }
                        TextButton(onClick = { showStats = !showStats }) { Text(if (showStats) "Hide stats" else "Stats") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state == null -> LoadingOrError(loadFailed, "The Korok scan couldn't be loaded.", padding)
            state.applied != null -> Applied(state.applied, padding, onBack)
            state.review != null -> Review(state, state.review, viewModel, padding, onApply = { confirming = true })
            else -> CameraPane(state, viewModel, showStats, padding)
        }
    }
    if (confirming && state?.review != null) {
        val n = state.selected.count { it !in state.progress.completed }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Apply to ${state.saveFile.label}?") },
            text = { Text("Mark ${seeds(n)} collected. Nothing is unmarked.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    viewModel.apply()
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

private fun seeds(n: Int) = if (n == 1) "1 Korok seed" else "$n Korok seeds"

@Composable
private fun CameraPane(state: KorokScanUiState, viewModel: KorokScanViewModel, showStats: Boolean, padding: PaddingValues) {
    val permission = rememberCameraPermission()
    Column(Modifier.fillMaxSize().padding(padding)) {
        when (permission.access) {
            CameraAccess.GRANTED -> Box(Modifier.weight(1f).fillMaxWidth()) {
                LiveCameraView(onFrame = viewModel::analyse, modifier = Modifier.fillMaxSize(), onCameraInfo = viewModel::cameraInfo)
                Overlay(state.live, Modifier.fillMaxSize())
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(statusLine(state.live.phase), style = MaterialTheme.typography.bodySmall)
                        state.live.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                        state.recording?.let { Text("Saving frames to $it", style = MaterialTheme.typography.labelSmall) }
                        if (showStats && state.live.stats.isNotEmpty()) {
                            Text(state.live.stats, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            else -> PermissionCard(permission.access, permission.request, permission.openSettings, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Found ${state.found} · empty ${state.empty} · unsure ${state.unsure}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::finish, enabled = state.found + state.unsure > 0) { Text("Done") }
        }
    }
}

private fun statusLine(phase: LivePhase) = when (phase) {
    LivePhase.FINDING_SCREEN -> "Fit the whole TV or Switch screen in view, showing the map."
    LivePhase.FINDING_PLACE -> "Finding your place… zoom the map all the way in: Korok markers only show at max zoom."
    LivePhase.TRACKING -> "Pan slowly with the left stick. Green: a found Korok. Grey: none there."
}

/** The tracked screen and each Korok spot in view, drawn over the centre-cropped preview. */
@Composable
private fun Overlay(live: LiveView, modifier: Modifier) {
    if (live.frameWidth == 0) return
    Canvas(modifier) {
        // The preview fills the view centre-cropped, like the analysed frame.
        val scale = max(size.width / live.frameWidth, size.height / live.frameHeight)
        val ox = (size.width - live.frameWidth * scale) / 2
        val oy = (size.height - live.frameHeight * scale) / 2
        fun at(x: Double, y: Double) = Offset(ox + x.toFloat() * scale, oy + y.toFloat() * scale)
        // The best outline that was turned down, so you can see what it's looking at.
        if (live.screen == null) live.candidate?.let { corners ->
            val path = Path().apply {
                moveTo(at(corners[0].x, corners[0].y).x, at(corners[0].x, corners[0].y).y)
                for (c in corners.drop(1)) at(c.x, c.y).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, Color(0xFFFFCA28), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
        }
        live.screen?.let { corners ->
            val path = Path().apply {
                moveTo(at(corners[0].x, corners[0].y).x, at(corners[0].x, corners[0].y).y)
                for (c in corners.drop(1)) at(c.x, c.y).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, Color(0xFF4FC3F7), style = Stroke(width = 2.dp.toPx()))
        }
        for (s in live.spots) {
            val c = at(s.x, s.y)
            when (s.verdict) {
                KorokVerdict.FOUND -> drawCircle(Color(0xFF66BB6A), radius = 14.dp.toPx(), center = c, style = Stroke(width = 3.dp.toPx()))
                KorokVerdict.EMPTY -> drawCircle(Color(0xFFB0BEC5), radius = 4.dp.toPx(), center = c)
                KorokVerdict.UNSURE -> drawCircle(Color(0xFFFFCA28), radius = 10.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PermissionCard(access: CameraAccess, request: () -> Unit, openSettings: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Point your camera at the map", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Open the 2D map, zoom all the way in, and film the TV or Switch screen while you pan around. The game " +
                        "marks every Korok you've found; the app reads those marks. Frames are read on the phone and never stored.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (access) {
                    CameraAccess.NOT_ASKED -> Button(onClick = request) { Text("Allow camera") }
                    CameraAccess.DENIED -> {
                        Text("The scan needs the camera to see the screen.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = request) { Text("Try again") }
                    }
                    CameraAccess.DENIED_PERMANENTLY -> {
                        Text(
                            "Camera access is turned off for this app. Turn it on in the app's settings, then come back.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = openSettings) { Text("Open settings") }
                    }
                    CameraAccess.UNAVAILABLE -> Text("This device has no camera.", style = MaterialTheme.typography.bodySmall)
                    CameraAccess.GRANTED -> Unit
                }
            }
        }
    }
}

@Composable
private fun Review(state: KorokScanUiState, review: KorokScanReview, viewModel: KorokScanViewModel, padding: PaddingValues, onApply: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(Modifier.weight(1f)) {
            item(key = "_summary") {
                Text(
                    buildString {
                        append("Seen collected in the game: ${review.toCollect.size + review.alreadyCollected}")
                        if (review.alreadyCollected > 0) append(" (${review.alreadyCollected} already marked here)")
                        append(". Seen empty: ${review.empty}, never written.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (review.toCollect.isNotEmpty()) {
                item(key = "_h_found") { Header("Found on the map", "Ticked to mark collected.") }
                // A marker means the Korok is found in the game, so naming it spoils nothing.
                items(review.toCollect, key = { it.id }) { KorokRow(it, state, viewModel, mayHide = false) }
            }
            if (review.unsure.isNotEmpty()) {
                item(key = "_h_unsure") { Header("Unsure", "A marker may be there. Tick any you know you've collected.") }
                items(review.unsure, key = { "u_" + it.id }) { KorokRow(it, state, viewModel, mayHide = true) }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = viewModel::keepScanning) { Text("Keep scanning") }
            Button(onClick = onApply, enabled = state.selected.any { it !in state.progress.completed }, modifier = Modifier.weight(1f)) {
                Text("Apply ${state.selected.count { it !in state.progress.completed }}")
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KorokRow(korok: Korok, state: KorokScanUiState, viewModel: KorokScanViewModel, mayHide: Boolean) {
    val hidden = mayHide && state.isHidden(korok)
    val checked = korok.id in state.selected
    Row(
        Modifier.fillMaxWidth()
            .let { if (hidden) it.clickable { viewModel.reveal(korok.id) } else it.toggleable(checked, role = Role.Checkbox) { on -> viewModel.setSelected(korok.id, on) } }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (hidden) "Undiscovered Korok · tap to show" else "${korok.id} · ${korok.nearestPlace}", style = MaterialTheme.typography.bodyLarge)
            Text(korok.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!hidden) Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Applied(n: Int, padding: PaddingValues, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Marked ${seeds(n)} collected.", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack) { Text("Back to Korok Seeds") }
    }
}
