package gx.zelda.shrines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
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
import gx.zelda.core.DiscoveryState
import gx.zelda.core.ui.CardInset
import gx.zelda.core.ui.FoundToggle
import gx.zelda.core.ui.HiddenRow
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.RowDivider
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.SearchField
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.groupRow
import gx.zelda.shrines.model.Lightroot
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.model.ShrineLayer
import gx.zelda.shrines.model.ShrineState
import gx.zelda.shrines.model.TrialType
import kotlinx.coroutines.launch

/**
 * Shrines by region, which are complete on the active save file, and the Spirit Orb (Light of
 * Blessing) planner. A game with Lightroots (TotK) gets a second tab for them, and each shrine
 * opens a page showing it with its Lightroot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShrineTrackerScreen(
    state: ShrineUiState?,
    loadFailed: Boolean,
    viewModel: ShrineViewModel,
    onBack: () -> Unit,
    title: String = "Shrines",
    /** Null for a game without map scanning. */
    onScanMap: (() -> Unit)? = null,
    /** Opens a shrine's page with its Lightroot; only offered for a game with Lightroots. */
    onOpenShrine: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { SaveFileOption(it.file, saveFileDetail(state, it)) },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = { MenuOrBackButton(onBack) },
                actions = {
                    if (state != null) ShrineMenu(state, viewModel, onScanMap)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The shrine list couldn't be loaded.", padding)
            return@Scaffold
        }
        if (state.catalog.lightroots.isEmpty()) {
            ShrineList(state, viewModel, padding, rememberLazyListState(), onOpenShrine = null)
        } else {
            ShrineAndLightrootTabs(state, viewModel, padding, onOpenShrine)
        }
    }
}

private fun saveFileDetail(state: ShrineUiState, summary: ShrineSaveSummary): String = buildString {
    val catalog = state.catalog
    if (catalog.lightroots.isEmpty()) {
        append("${summary.completed} / ${catalog.base.size} shrines complete")
    } else {
        append("${summary.completed} / ${catalog.base.size} shrines · ${summary.lightrootsLit} / ${catalog.lightroots.size} Lightroots")
    }
}

/** The two lists a game with Lightroots shows, swiped between like Main Quest Progress's tabs. */
@Composable
private fun ShrineAndLightrootTabs(
    state: ShrineUiState,
    viewModel: ShrineViewModel,
    padding: PaddingValues,
    onOpenShrine: (String) -> Unit,
) {
    val pager = rememberPagerState { 2 }
    // Hoisted so each list keeps its scroll position while its page is off screen.
    val shrineList = rememberLazyListState()
    val lightrootList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val catalog = state.catalog
    val tabs = listOf(
        "Shrines" to "${catalog.baseCompleted(state.progress.completed)} / ${catalog.base.size}",
        "Lightroots" to "${catalog.lightrootCount(state.progress.lightroots.completed)} / ${catalog.lightroots.size}",
    )
    Column(Modifier.fillMaxSize().padding(padding)) {
        PrimaryTabRow(selectedTabIndex = pager.targetPage) {
            tabs.forEachIndexed { page, (label, count) ->
                Tab(
                    selected = pager.targetPage == page,
                    onClick = { scope.launch { pager.animateScrollToPage(page) } },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
        }
        HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                ShrineList(state, viewModel, PaddingValues(0.dp), shrineList, onOpenShrine)
            } else {
                LightrootList(state, viewModel, lightrootList, onOpenShrine)
            }
        }
    }
}

@Composable
private fun ShrineMenu(state: ShrineUiState, viewModel: ShrineViewModel, onScanMap: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(if (state.catalog.lightroots.isEmpty()) "Hide undiscovered shrines" else "Hide undiscovered shrines & Lightroots") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUndiscovered, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUndiscovered(!state.prefs.hideUndiscovered)
                open = false
            },
        )
        if (onScanMap != null) {
            DropdownMenuItem(
                text = { Text("Scan map screenshots or photos") },
                onClick = {
                    open = false
                    onScanMap()
                },
            )
        }
    }
}

/** [onOpenShrine] is null for a game without Lightroots, whose rows have no page to open. */
@Composable
private fun ShrineList(
    state: ShrineUiState,
    viewModel: ShrineViewModel,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onOpenShrine: ((String) -> Unit)?,
) {
    val progress = state.progress
    val catalog = state.catalog
    val shownByRegion = catalog.byRegion(state.shown).toMap()
    val groups = catalog.byRegion(state.matching)

    LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
        // While searching, the orb card would push the results down under the keyboard.
        if (state.filters.query.isBlank()) {
            item(key = "_orbs") {
                BlessingCard(
                    state.orbs,
                    bossHeartsByHand = state.bossHeartsByHand,
                    onBoughtChange = viewModel::setBought,
                    onBossHeartsChange = viewModel::setBossHearts,
                    onFreeUpgradeChange = viewModel::setFreeUpgrade,
                    modifier = Modifier.padding(horizontal = CardInset).padding(top = 8.dp),
                )
            }
        }
        item(key = "_filters") { ShrineFilterBar(state, viewModel) }
        if (groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No shrines match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((region, shrines) in groups) {
            // The header counts the whole region, not just the shrines the filters leave showing.
            val inRegion = shownByRegion[region].orEmpty()
            item(key = "h_$region") {
                RegionHeader(
                    region,
                    found = inRegion.count { progress.isFound(it.id) },
                    done = inRegion.count { it.id in progress.completed },
                    total = inRegion.size,
                )
            }
            itemsIndexed(shrines, key = { _, shrine -> shrine.id }) { i, shrine ->
                val last = i == shrines.lastIndex
                val modifier = Modifier.groupRow(last)
                if (state.isHidden(shrine)) {
                    HiddenRow(
                        "Undiscovered shrine",
                        onReveal = { viewModel.reveal(shrine.id) },
                        onFound = { viewModel.setState(shrine.id, ShrineState.FOUND) },
                        showDivider = !last,
                        modifier = modifier,
                    )
                } else {
                    val lightroot = catalog.lightrootOf(shrine)
                    ShrineRow(
                        shrine,
                        shrineState = progress.state(shrine.id),
                        onStateChange = { viewModel.setState(shrine.id, it) },
                        showDivider = !last,
                        modifier = modifier,
                        lightroot = lightroot,
                        lightrootState = lightroot?.let { progress.lightroots.state(it.id) },
                        onOpen = onOpenShrine?.let { open -> { open(shrine.id) } },
                    )
                }
            }
        }
    }
}

/** Lightroots under their shrines' regions; each row opens its shrine's page. */
@Composable
private fun LightrootList(
    state: ShrineUiState,
    viewModel: ShrineViewModel,
    listState: LazyListState,
    onOpenShrine: (String) -> Unit,
) {
    val catalog = state.catalog
    val lit = state.progress.lightroots
    val allByRegion = catalog.lightrootsByRegion(catalog.lightroots).toMap()
    val groups = catalog.lightrootsByRegion(state.matchingLightroots)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "_filters") { LightrootFilterBar(state, viewModel) }
        if (groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No Lightroots match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((region, roots) in groups) {
            val inRegion = allByRegion[region].orEmpty()
            item(key = "h_$region") {
                RegionHeader(
                    region,
                    found = inRegion.count { lit.isFound(it.id) },
                    done = inRegion.count { it.id in lit.completed },
                    total = inRegion.size,
                )
            }
            itemsIndexed(roots, key = { _, root -> root.id }) { i, root ->
                val last = i == roots.lastIndex
                val modifier = Modifier.groupRow(last)
                if (state.isHidden(root)) {
                    HiddenRow(
                        "Undiscovered Lightroot",
                        onReveal = { viewModel.revealLightroot(root.id) },
                        onFound = { viewModel.setLightrootState(root.id, DiscoveryState.FOUND) },
                        showDivider = !last,
                        modifier = modifier,
                    )
                } else {
                    val shrine = catalog.shrineOf(root)
                    LightrootRow(
                        root,
                        shrine,
                        rootState = lit.state(root.id),
                        shrineState = state.progress.state(shrine.id),
                        onStateChange = { viewModel.setLightrootState(root.id, it) },
                        onOpen = { onOpenShrine(shrine.id) },
                        showDivider = !last,
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun LightrootFilterBar(state: ShrineUiState, viewModel: ShrineViewModel) {
    val filters = state.filters
    val catalog = state.catalog
    val lit = state.progress.lightroots
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search Lightroot, shrine or region")
        FilterChip(
            selected = filters.incompleteOnly,
            onClick = { viewModel.setIncompleteOnly(!filters.incompleteOnly) },
            label = { Text("Unlit") },
        )
        Text(
            "${catalog.lightrootCount(lit.completed)} / ${catalog.lightroots.size} Lightroots lit · " +
                "${catalog.lightroots.count { lit.isFound(it.id) }} found",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (filters.query.isBlank()) {
            Text(
                "Every shrine except those on sky islands has a Lightroot directly beneath it in the Depths, named " +
                    "with the shrine's name spelled backwards. Lighting one brightens the Depths around it and makes it a travel point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LightrootRow(
    lightroot: Lightroot,
    shrine: Shrine,
    rootState: DiscoveryState,
    shrineState: ShrineState,
    onStateChange: (DiscoveryState) -> Unit,
    onOpen: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val lit = rootState == DiscoveryState.COMPLETED
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Unticking a lit Lightroot leaves it found.
                .toggleable(value = lit, role = Role.Checkbox) {
                    onStateChange(if (it) DiscoveryState.COMPLETED else DiscoveryState.FOUND)
                }
                .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Checkbox(checked = lit, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${lightroot.name} Lightroot",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (lit) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Beneath ${shrine.name} Shrine · shrine ${shrineState.pairLabel(lightroot = false)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FoundToggle(rootState, onStateChange)
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "${lightroot.name} and ${shrine.name} Shrine")
            }
        }
        if (showDivider) RowDivider()
    }
}

/** How a row names the state of the other half of a shrine–Lightroot pair: "lit", "found", "not found". */
internal fun DiscoveryState.pairLabel(lightroot: Boolean): String = when (this) {
    DiscoveryState.COMPLETED -> if (lightroot) "lit" else "complete"
    DiscoveryState.FOUND -> "found"
    DiscoveryState.UNDISCOVERED -> "not found"
}

@Composable
private fun ShrineFilterBar(state: ShrineUiState, viewModel: ShrineViewModel) {
    val filters = state.filters
    val progress = state.progress
    val catalog = state.catalog
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, if (catalog.lightroots.isEmpty()) "Search shrine, trial or quest" else "Search shrine, trial, quest or place")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.incompleteOnly,
                onClick = { viewModel.setIncompleteOnly(!filters.incompleteOnly) },
                label = { Text("Incomplete") },
            )
            FilterChip(
                selected = filters.questsOnly,
                onClick = { viewModel.setQuestsOnly(!filters.questsOnly) },
                label = { Text("Shrine Quests") },
            )
            if (catalog.dlc.isNotEmpty()) {
                FilterChip(
                    selected = state.prefs.showDlc,
                    onClick = { viewModel.setShowDlc(!state.prefs.showDlc) },
                    label = { Text("DLC") },
                )
            }
        }
        Text(
            buildString {
                append("${catalog.base.count { it.id in progress.completed }} / ${catalog.base.size} shrines complete")
                append(" · ${catalog.base.count { progress.isFound(it.id) }} found")
                if (state.prefs.showDlc && catalog.dlc.isNotEmpty()) {
                    append(" · DLC ${catalog.dlc.count { it.id in progress.completed }} / ${catalog.dlc.size}")
                }
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShrineRow(
    shrine: Shrine,
    shrineState: ShrineState,
    onStateChange: (ShrineState) -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    lightroot: Lightroot? = null,
    lightrootState: DiscoveryState? = null,
    /** Opens the shrine's page; null in a game without one. */
    onOpen: (() -> Unit)? = null,
) {
    val completed = shrineState == ShrineState.COMPLETED
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Unticking a completed shrine leaves it found.
                .toggleable(value = completed, role = Role.Checkbox) {
                    onStateChange(if (it) ShrineState.COMPLETED else ShrineState.FOUND)
                }
                .padding(start = 4.dp, end = if (onOpen == null) 4.dp else 0.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Checkbox(checked = completed, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${shrine.name} Shrine",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        shrine.trial,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    typeTag(shrine)?.let { Tag(it) }
                    if (shrine.layer != ShrineLayer.SURFACE) Tag(shrine.layer.label)
                    if (shrine.dlc) Tag("DLC")
                }
                shrine.quest?.let {
                    Text(
                        "Shrine Quest: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    shrine.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lightroot != null && lightrootState != null) {
                    Text(
                        "Lightroot: ${lightroot.name} · ${lightrootState.pairLabel(lightroot = true)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lightrootState == DiscoveryState.COMPLETED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FoundToggle(shrineState, onStateChange)
            if (onOpen != null) {
                IconButton(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "${shrine.name} Shrine and its Lightroot")
                }
            }
        }
        if (showDivider) RowDivider()
    }
}

/**
 * Puzzles go untagged, and most trial names already say "Test of Strength", "Proving Grounds",
 * "Combat Training" or "Blessing"; tag a fight or a blessing only when its name doesn't.
 */
internal fun typeTag(shrine: Shrine): String? = when (shrine.type) {
    TrialType.PUZZLE -> null
    TrialType.COMBAT -> shrine.type.label.takeUnless { combatWords.any { it in shrine.trial } }
    TrialType.BLESSING -> shrine.type.label.takeUnless { "Blessing" in shrine.trial }
}

private val combatWords = listOf("Test of Strength", "Proving Grounds", "Combat Training")
