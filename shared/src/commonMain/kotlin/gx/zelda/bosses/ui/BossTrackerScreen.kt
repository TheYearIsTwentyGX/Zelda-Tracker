package gx.zelda.bosses.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.bosses.model.BossSpawn
import gx.zelda.core.DiscoveryState
import gx.zelda.core.ui.FoundToggle
import gx.zelda.core.ui.HiddenRow
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.groupRow
import kotlinx.coroutines.launch

/**
 * Overworld bosses on the active save file: a tab per kind (Hinox, Talus, Molduga, Lynel), each
 * grouped by tower region. Swipe between kinds or tap a tab; the tab row shows each kind's count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossTrackerScreen(
    state: BossUiState?,
    loadFailed: Boolean,
    viewModel: BossViewModel,
    onOpenSpawn: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Overworld Bosses", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { SaveFileOption(it.file, "${it.defeated} / ${it.total} defeated") },
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
                    if (state != null) BossMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                if (loadFailed) {
                    Text(
                        "The boss list couldn't be loaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }
        BossPages(state, viewModel, onOpenSpawn, Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun BossMenu(state: BossUiState, viewModel: BossViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide unfound bosses") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUndiscovered, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUndiscovered(!state.prefs.hideUndiscovered)
                open = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BossPages(state: BossUiState, viewModel: BossViewModel, onOpenSpawn: (String) -> Unit, modifier: Modifier) {
    val pages = state.pages
    if (pages.isEmpty()) return
    val pager = rememberPagerState { pages.size }
    // Hoisted so each tab keeps its scroll position while its page is off screen.
    val listStates = pages.map { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = pager.targetPage) {
            pages.forEachIndexed { i, page ->
                Tab(
                    selected = pager.targetPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(page.kind.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${page.tally.defeated} / ${page.tally.total}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
        // The search and filters stay put above the pages, so swiping keeps what was typed.
        BossFilterBar(state, pages[pager.targetPage], viewModel)
        HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { i ->
            BossList(state, pages[i], viewModel, onOpenSpawn, listStates[i])
        }
    }
}

@Composable
private fun BossFilterBar(state: BossUiState, page: BossPage, viewModel: BossViewModel) {
    val filters = state.filters
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search place, variant or region")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.incompleteOnly,
                onClick = { viewModel.setIncompleteOnly(!filters.incompleteOnly) },
                label = { Text("Not defeated") },
            )
            // A variant filter only means something on a tab with more than one variant.
            if (page.variants.size > 1) {
                for (variant in page.variants) {
                    FilterChip(
                        selected = variant in filters.variants,
                        onClick = { viewModel.toggleVariant(variant) },
                        label = { Text(state.data.variantName(variant)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BossList(
    state: BossUiState,
    page: BossPage,
    viewModel: BossViewModel,
    onOpenSpawn: (String) -> Unit,
    listState: LazyListState,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // While searching, the summary would push the results down under the keyboard.
        if (state.filters.query.isBlank()) {
            item(key = "_summary") { KindSummary(state, page) }
        }
        if (page.groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No ${page.kind.plural} match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((region, spawns) in page.groups) {
            // The header counts the whole region, not just the spawns the filters leave showing.
            val count = page.regionCounts.getValue(region)
            item(key = "h_$region", contentType = "header") {
                RegionHeader(region, found = count.found, done = count.defeated, total = count.total)
            }
            itemsIndexed(spawns, key = { _, spawn -> spawn.id }, contentType = { _, _ -> "boss" }) { i, spawn ->
                val last = i == spawns.lastIndex
                if (state.isHidden(spawn)) {
                    HiddenRow(
                        "Unfound ${page.kind.name}",
                        onReveal = { viewModel.reveal(spawn.id) },
                        onFound = { viewModel.setState(spawn.id, DiscoveryState.FOUND) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                } else {
                    BossRow(
                        spawn,
                        name = state.data.variantName(spawn.variant),
                        bossState = state.progress.state(spawn.id),
                        onStateChange = { viewModel.setState(spawn.id, it) },
                        onOpen = { onOpenSpawn(spawn.id) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                }
            }
        }
    }
}

/** "Hinox 12 / 40 · Medal of Honor: Hinox", with what's found and anything the medal doesn't count. */
@Composable
private fun KindSummary(state: BossUiState, page: BossPage) {
    val kind = page.kind
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(page.tally.headline, style = MaterialTheme.typography.titleSmall)
        val extras = buildList {
            add("${page.tally.found} found")
            if (page.uncounted.isNotEmpty()) {
                val names = page.uncounted.joinToString { state.data.variantName(it.variant) }
                add(if (kind.medal != null) "$names (DLC) not counted" else "$names listed too")
            }
        }
        Text(
            extras.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BossRow(
    spawn: BossSpawn,
    name: String,
    bossState: DiscoveryState,
    onStateChange: (DiscoveryState) -> Unit,
    onOpen: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val defeated = bossState == DiscoveryState.COMPLETED
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Unticking a defeated boss leaves it found.
                .toggleable(value = defeated, role = Role.Checkbox) {
                    onStateChange(if (it) DiscoveryState.COMPLETED else DiscoveryState.FOUND)
                }
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Checkbox(checked = defeated, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (defeated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (spawn.dlc) Tag("DLC")
                    if (spawn.masterModeOnly) Tag("Master Mode")
                    if (spawn.scalesWithWorldLevel) Tag("Scales")
                }
                Text(
                    "Near ${spawn.nearestPlace}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FoundToggle(bossState, onStateChange)
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Details for $name near ${spawn.nearestPlace}")
            }
        }
        if (showDivider) RowDivider()
    }
}
