package gx.zelda.kinstones.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.groupRow
import gx.zelda.kinstones.model.KinstoneColor
import gx.zelda.kinstones.model.KinstoneFusion
import gx.zelda.kinstones.model.PartnerKind

/**
 * Every Kinstone fusion on the active save file, grouped by where the partner is, with the random
 * fusions first. Tick a row once it's fused; search and the filters narrow the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinstoneTrackerScreen(
    state: KinstoneUiState?,
    loadFailed: Boolean,
    viewModel: KinstoneViewModel,
    onOpenFusion: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kinstone Fusions", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { SaveFileOption(it.file, "${it.fused} / ${it.total} fused") },
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
                actions = {
                    if (state != null) KinstoneMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The fusion list couldn't be loaded.", padding)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            KinstoneFilterBar(state, viewModel)
            FusionList(state, viewModel, onOpenFusion)
        }
    }
}

@Composable
private fun KinstoneMenu(state: KinstoneUiState, viewModel: KinstoneViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide results not fused") },
            trailingIcon = { Checkbox(checked = state.prefs.hideResults, onCheckedChange = null) },
            onClick = {
                viewModel.setHideResults(!state.prefs.hideResults)
                open = false
            },
        )
    }
}

@Composable
private fun KinstoneFilterBar(state: KinstoneUiState, viewModel: KinstoneViewModel) {
    val filters = state.filters
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search partner, place, piece or result")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.notFusedOnly,
                onClick = { viewModel.setNotFusedOnly(!filters.notFusedOnly) },
                label = { Text("Not fused") },
            )
            RegionChip(state, viewModel)
            KinstoneColor.entries.forEach { color ->
                FilterChip(
                    selected = color in filters.colors,
                    onClick = { viewModel.toggleColor(color) },
                    label = { Text(color.label) },
                    leadingIcon = { ColorDot(color) },
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.rewardKinds.forEach { reward ->
                FilterChip(
                    selected = reward in filters.rewards,
                    onClick = { viewModel.toggleReward(reward) },
                    label = { Text(reward.label) },
                )
            }
        }
    }
}

/** "All regions" or the one region shown; tap to pick another. */
@Composable
private fun RegionChip(state: KinstoneUiState, viewModel: KinstoneViewModel) {
    var open by remember { mutableStateOf(false) }
    val region = state.filters.region
    Box {
        FilterChip(
            selected = region != null,
            onClick = { open = true },
            label = { Text(region ?: "All regions") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All regions") }, onClick = { viewModel.setRegion(null); open = false })
            state.catalog.regions.forEach { r ->
                val count = state.regionCounts[r] ?: return@forEach
                DropdownMenuItem(
                    text = { Text(r) },
                    trailingIcon = {
                        Text(
                            "${count.fused} / ${count.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { viewModel.setRegion(r); open = false },
                )
            }
        }
    }
}

@Composable
private fun FusionList(state: KinstoneUiState, viewModel: KinstoneViewModel, onOpenFusion: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "_summary") {
            Text(
                "${state.fused} / ${state.total} fused · Gold ${state.gold.fused} / ${state.gold.total} · " +
                    "Hearts ${state.hearts.fused} / ${state.hearts.total}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (state.groups.isEmpty()) {
            item(key = "_empty") {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "No fusions match these filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.filters.isFiltering) {
                        TextButton(onClick = viewModel::clearFilters) { Text("Clear filters") }
                    }
                }
            }
        }
        for ((region, inRegion) in state.groups) {
            // The header counts the whole region, not just the fusions the filters leave showing.
            val count = state.regionCounts.getValue(region)
            item(key = "h_$region", contentType = "header") { RegionHeader(region, found = null, done = count.fused, total = count.total, suffix = "") }
            itemsIndexed(inRegion, key = { _, f -> f.id }, contentType = { _, _ -> "fusion" }) { i, fusion ->
                val last = i == inRegion.lastIndex
                FusionRow(
                    fusion,
                    fused = state.progress.isFused(fusion.id),
                    hidden = state.isHidden(fusion),
                    onFusedChange = { viewModel.setFused(fusion.id, it) },
                    onReveal = { viewModel.reveal(fusion.id) },
                    onOpen = { onOpenFusion(fusion.id) },
                    showDivider = !last,
                    modifier = Modifier.groupRow(last),
                )
            }
        }
    }
}

@Composable
private fun FusionRow(
    fusion: KinstoneFusion,
    fused: Boolean,
    hidden: Boolean,
    onFusedChange: (Boolean) -> Unit,
    onReveal: () -> Unit,
    onOpen: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = fused, role = Role.Checkbox, onValueChange = onFusedChange)
                .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Checkbox(checked = fused, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorDot(fusion.color)
                    Text(
                        fusion.partner,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (fused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    "${fusion.pieceName} · ${fusion.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hidden) {
                    HiddenResult(onReveal)
                } else {
                    Text(fusion.result, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                val tags = fusionTags(fusion)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { Tag(it) }
                    }
                }
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Details of the ${fusion.partner} fusion")
            }
        }
        if (showDivider) RowDivider()
    }
}

/** In place of a hidden result: a lock and "Tap to reveal". */
@Composable
private fun HiddenResult(onReveal: () -> Unit) {
    TextButton(onClick = onReveal, modifier = Modifier.padding(0.dp)) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
        Text("Result hidden · tap to reveal", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Short tags under a fusion: Gold, random, missable, and what it needs first. */
internal fun fusionTags(fusion: KinstoneFusion): List<String> = listOfNotNull(
    "Gold · story".takeIf { fusion.isGold },
    "Random fuser".takeIf { fusion.random },
    "Object".takeIf { fusion.partnerKind == PartnerKind.OBJECT && !fusion.isGold },
    "Missable".takeIf { fusion.missable != null },
    "Needs something first".takeIf { fusion.requires != null },
)

/** The piece's colour as a small dot. */
@Composable
internal fun ColorDot(color: KinstoneColor) {
    Box(Modifier.size(10.dp).background(color.swatch, CircleShape))
}

/** Close to the pieces' in-game colours. */
internal val KinstoneColor.swatch: Color
    get() = when (this) {
        KinstoneColor.GREEN -> Color(0xFF3FA34D)
        KinstoneColor.BLUE -> Color(0xFF3D7BD9)
        KinstoneColor.RED -> Color(0xFFD9433D)
        KinstoneColor.GOLD -> Color(0xFFE0B020)
    }
