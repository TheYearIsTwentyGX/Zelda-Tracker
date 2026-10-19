package gx.zelda.armor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.core.ui.BulkConfirmDialog
import gx.zelda.core.ui.BulkUndoSnackbar
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.rememberBulkCompleteState

private enum class ArmorTab(val label: String, val title: String) {
    ARMOR("Armor", "Armor Progress"),
    MATERIALS("Materials", "Total Materials Required"),
}

/** Armor tracker shell: an Armor tab and a Materials tab sharing one [ArmorViewModel]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmorTrackerScreen(
    state: ArmorUiState?,
    loadFailed: Boolean,
    viewModel: ArmorViewModel,
    onOpenPiece: (String) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ArmorTab.ARMOR) }
    val bulk = rememberBulkCompleteState()
    val snackbar = remember { SnackbarHostState() }
    val bulkChange by viewModel.bulkChange.collectAsStateWithLifecycle()
    BulkUndoSnackbar(bulkChange, snackbar, onUndo = { viewModel.undoBulkChange(it) }, onDismiss = viewModel::dismissBulkChange)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map {
                                    SaveFileOption(it.file, "${it.collected} / ${state.catalog.pieces.size} armor collected")
                                },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = { MenuOrBackButton(onBack) },
            )
        },
        bottomBar = {
            NavigationBar {
                ArmorTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                if (t == ArmorTab.ARMOR) Icons.AutoMirrored.Filled.List else Icons.Filled.Build,
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The armor list couldn't be loaded.", padding)
            return@Scaffold
        }
        when (tab) {
            ArmorTab.ARMOR -> ArmorListScreen(state, viewModel, bulk, onOpenPiece, padding)
            ArmorTab.MATERIALS -> MaterialTotalsScreen(state, viewModel, onOpenPiece, padding)
        }
        BulkConfirmDialog(bulk, state.saveFile.label)
    }
}
