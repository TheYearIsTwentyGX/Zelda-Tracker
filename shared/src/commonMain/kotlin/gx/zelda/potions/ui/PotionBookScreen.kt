package gx.zelda.potions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.core.BulkAction
import gx.zelda.core.countOf
import gx.zelda.core.ui.BulkCompleteRow
import gx.zelda.core.ui.BulkConfirmDialog
import gx.zelda.core.ui.BulkUndoSnackbar
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.rememberBulkCompleteState
import gx.zelda.theme.GameProgressBar
import gx.zelda.theme.LocalGameDecor
import gx.zelda.upgrades.logic.thousands
import gx.zelda.upgrades.ui.UpgradeItemIcon

/** The recipe book's cover: every potion as a card, how much of its recipe is made, and the book's mark-all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotionBookScreen(
    viewModel: PotionViewModel,
    title: String,
    onOpenPotion: (String) -> Unit,
    onOpenTotals: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
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
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state?.let { s ->
                            SaveFilePicker(
                                current = s.saveFile,
                                options = s.saveFiles.map { SaveFileOption(it.file, "${it.made} / ${s.steps} steps made") },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = { MenuOrBackButton(onBack) },
            )
        },
    ) { padding ->
        val s = state
        if (s == null) {
            LoadingOrError(loadFailed, "The potion recipes couldn't be loaded.", padding)
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            item(key = "_header", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Luv sells each potion at the Potion Shop in the Bazaar; Bertie infuses it with bugs at the " +
                            "counter beside her. Pick one to see its recipe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${s.made} / ${s.steps} steps made", style = MaterialTheme.typography.labelLarge)
                    GameProgressBar(progress = { if (s.steps == 0) 0f else s.made.toFloat() / s.steps }, modifier = Modifier.fillMaxWidth())
                    BulkCompleteRow(s.bulkScope(), bulk, onApply = { viewModel.setAllMade(it == BulkAction.MARK) })
                    TotalsCard(s, onOpenTotals)
                }
            }
            items(s.cards, key = { it.potion.id }) { card -> PotionCardView(card, onClick = { onOpenPotion(card.potion.id) }) }
        }
        BulkConfirmDialog(bulk, s.saveFile.label)
    }
}

@Composable
private fun PotionCardView(card: PotionCard, onClick: () -> Unit) {
    val potion = card.potion
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${potion.name}, ${card.made} of ${card.steps} steps made" },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UpgradeItemIcon(potion, card.shownLevel, size = 64.dp, dimmed = card.made == 0)
            Text(
                potion.levelName(card.shownLevel.takeIf { card.made > 0 } ?: 0),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            StepDots(card.made, card.steps)
            Text(
                if (card.done) "Fully infused" else "${card.made} / ${card.steps} made",
                style = MaterialTheme.typography.labelMedium,
                color = if (card.done) LocalGameDecor.current.star else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The way to the Totals screen, with its sums in the scope last picked there. */
@Composable
private fun TotalsCard(state: PotionUiState, onClick: () -> Unit) {
    val totals = state.totals
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Total Materials Required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${countOf(totals.materialCount, "bug")} · ${thousands(totals.rupees)} Rupees (${state.totalsMode.potionLabel.lowercase()})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** One dot per recipe step: the base potion, then each infusion, filled once made. */
@Composable
internal fun StepDots(made: Int, steps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(steps) { i ->
            Box(
                Modifier.size(10.dp).background(
                    if (i < made) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            )
        }
    }
}
