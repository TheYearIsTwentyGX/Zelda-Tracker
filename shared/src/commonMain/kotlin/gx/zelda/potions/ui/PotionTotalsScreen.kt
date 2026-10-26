package gx.zelda.potions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.core.countOf
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.potions.model.PotionTotals
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.logic.thousands
import gx.zelda.upgrades.ui.RupeeTotal
import gx.zelda.upgrades.ui.TotalsCountRow
import gx.zelda.upgrades.ui.TotalsModeSwitch
import gx.zelda.upgrades.ui.materialTotalRows

/** The scope switch's names for the potions: [UpgradeTotalsMode.COLLECTED] counts the potions bought. */
internal val UpgradeTotalsMode.potionLabel: String
    get() = when (this) {
        UpgradeTotalsMode.COLLECTED -> "Potions you've bought"
        UpgradeTotalsMode.ALL -> "All potions"
    }

/**
 * The recipe book's Total Materials Required: every bug the potion steps left take, Bertie's and
 * Luv's Rupees, and, per bug, where to catch it and which infusions need how many. Item Upgrades'
 * Materials tab, over the potions only, with the base potions' prices added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotionTotalsScreen(viewModel: PotionViewModel, onOpenPotion: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Total Materials Required", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state?.let { s ->
                            SaveFilePicker(
                                current = s.saveFile,
                                options = s.saveFiles.map { SaveFileOption(it.file, "${it.made} / ${s.steps} steps made") },
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
    ) { padding ->
        val s = state
        if (s == null) {
            LoadingOrError(loadFailed, "The potion recipes couldn't be loaded.", padding)
            return@Scaffold
        }
        val totals = s.totals
        val lines = remember(totals, s.materialSort) { totals.infusions.sorted(s.materialSort) }
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxWidth()) {
            item(key = "_controls") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalsModeSwitch(s.totalsMode, viewModel::setTotalsMode, label = { it.potionLabel })
                    RupeeTotal(totals.rupees)
                    rupeeBreakdown(totals)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TotalsCountRow(
                        "${countOf(totals.stepsRemaining, "step")} on ${countOf(totals.potionsCounted, "potion")}",
                        s.materialSort,
                        viewModel::setMaterialSort,
                    )
                }
            }
            if (lines.isEmpty()) {
                item(key = "_empty") {
                    Text(
                        when {
                            totals.stepsRemaining > 0 -> "No bugs left to catch: just buy the potions from Luv."
                            s.totalsMode == UpgradeTotalsMode.COLLECTED ->
                                "Nothing left to infuse on the potions you've bought. Tick a potion bought on its recipe, or switch to All potions."
                            else -> "Every potion is fully infused. Nice."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            materialTotalRows(
                lines,
                expanded,
                onToggle = { id -> expanded = if (expanded == id) null else id },
                onOpenItem = { potionId, _ -> onOpenPotion(potionId) },
                usesNoun = "infusions",
            )
        }
    }
}

/** "180 at Bertie's for 7 infusions · 320 at Luv's for 5 potions", leaving out a part that's 0. */
internal fun rupeeBreakdown(totals: PotionTotals): String? = listOfNotNull(
    "${thousands(totals.infusions.rupees)} at Bertie's for ${countOf(totals.infusions.stepsRemaining, "infusion")}"
        .takeIf { totals.infusions.stepsRemaining > 0 },
    "${thousands(totals.baseRupees)} at Luv's for ${countOf(totals.basesToBuy.size, "potion")}"
        .takeIf { totals.basesToBuy.isNotEmpty() },
).joinToString(" · ").takeIf { it.isNotEmpty() }
