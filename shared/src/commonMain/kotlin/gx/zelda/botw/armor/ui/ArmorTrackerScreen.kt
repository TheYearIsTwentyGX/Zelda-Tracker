package gx.zelda.botw.armor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker

private enum class ArmorTab(val label: String, val title: String) {
    ARMOR("Armor", "Armor Progress"),
    MATERIALS("Materials", "Total Materials Required"),
}

/** BotW armor tracker shell: an Armor tab and a Materials tab sharing one [ArmorViewModel]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmorTrackerScreen(
    state: ArmorUiState?,
    viewModel: ArmorViewModel,
    onOpenPiece: (String) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ArmorTab.ARMOR) }
    Scaffold(
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        when (tab) {
            ArmorTab.ARMOR -> ArmorListScreen(state, viewModel, onOpenPiece, padding)
            ArmorTab.MATERIALS -> MaterialTotalsScreen(state, viewModel, onOpenPiece, padding)
        }
    }
}
