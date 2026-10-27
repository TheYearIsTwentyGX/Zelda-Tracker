package gx.zelda.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberDocumentPicker(mimeTypes: List<String>, onPicked: (PickedDocument) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) callback(PickedDocument(displayName(context, uri) ?: "File") { path -> copy(context, uri, path) })
    }
    return { launcher.launch(mimeTypes.toTypedArray()) }
}

private suspend fun copy(context: Context, uri: Uri, path: String) = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: error("Can't open $uri")
    input.use { src -> File(path).also { it.parentFile?.mkdirs() }.outputStream().use { src.copyTo(it) } }
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()
