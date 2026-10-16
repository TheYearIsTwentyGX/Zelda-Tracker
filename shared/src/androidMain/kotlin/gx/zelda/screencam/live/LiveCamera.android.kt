package gx.zelda.screencam.live

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

@Composable
actual fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    // Bumped to re-read the permission: after the dialog, and on coming back from Settings.
    var checks by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        checks++
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checks++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val granted = remember(checks) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val rationale = remember(checks) {
        context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: false
    }
    return CameraPermission(
        CameraAccess.of(granted, asked, rationale),
        request = { launcher.launch(Manifest.permission.CAMERA) },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun LiveCameraView(onFrame: suspend (GrayImage) -> Unit, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onFrame)
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                // Preview and analysis share a 16:9 aspect, so the analysed frame is what's on screen
                // (centre-cropped the same way). 1280×720 is plenty: markers are ~15 px across.
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val preview = Preview.Builder().setResolutionSelector(resolution).build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val frame = uprightLuma(image)
                        runBlocking { callback(frame) }
                    } finally {
                        image.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = modifier,
    )
}

/** The Y plane, rotated as the camera reports so it matches the upright preview. */
private fun uprightLuma(image: ImageProxy): GrayImage {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val stride = plane.rowStride
    val pixelStride = plane.pixelStride
    val w = image.width
    val h = image.height
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    fun at(x: Int, y: Int): Float = (bytes[y * stride + x * pixelStride].toInt() and 0xff).toFloat()
    return when (image.imageInfo.rotationDegrees) {
        90 -> GrayImage(h, w, FloatArray(w * h) { i -> at(i / h, h - 1 - i % h) })
        180 -> GrayImage(w, h, FloatArray(w * h) { i -> at(w - 1 - i % w, h - 1 - i / w) })
        270 -> GrayImage(h, w, FloatArray(w * h) { i -> at(w - 1 - i / h, i % h) })
        else -> GrayImage(w, h, FloatArray(w * h) { i -> at(i % w, i / w) })
    }
}

actual fun logLiveStats(line: String) {
    Log.d("KorokScan", line)
}

@Composable
actual fun isDebugBuild(): Boolean = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
