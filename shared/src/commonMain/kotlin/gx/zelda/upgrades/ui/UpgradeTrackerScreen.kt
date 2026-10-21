package gx.zelda.upgrades.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.core.ui.BulkConfirmDialog
import gx.zelda.core.ui.BulkUndoSnackbar
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.rememberBulkCompleteState

private enum class UpgradeTab(val label: String, val title: String) {
    ITEMS("Items", "Item Upgrades"),
    MATERIALS("Materials", "Total Materials Required"),
}

/** The item-upgrade tracker: an Items tab and a Materials tab sharing one [UpgradeViewModel], as the armor tracker's do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeTrackerScreen(
    state: UpgradeUiState?,
    loadFailed: Boolean,
    viewModel: UpgradeViewModel,
    title: String,
    onOpenItem: (itemId: String, copy: Int) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(UpgradeTab.ITEMS) }
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
                        Text(if (tab == UpgradeTab.ITEMS) title else tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map {
                                    SaveFileOption(it.file, "${it.obtained} / ${state.catalog.items.size} items obtained")
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
                UpgradeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                if (t == UpgradeTab.ITEMS) Icons.AutoMirrored.Filled.List else Icons.Filled.Build,
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
            LoadingOrError(loadFailed, "The upgrade list couldn't be loaded.", padding)
            return@Scaffold
        }
        when (tab) {
            UpgradeTab.ITEMS -> UpgradeListScreen(state, viewModel, bulk, onOpenItem, padding)
            UpgradeTab.MATERIALS -> UpgradeMaterialsScreen(state, viewModel, onOpenItem, padding)
        }
        BulkConfirmDialog(bulk, state.saveFile.label)
    }
}
