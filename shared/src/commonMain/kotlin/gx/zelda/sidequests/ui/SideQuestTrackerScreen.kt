package gx.zelda.sidequests.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.core.BulkAction
import gx.zelda.core.ui.BulkCompleteCheckbox
import gx.zelda.core.ui.BulkCompleteRow
import gx.zelda.core.ui.BulkCompleteState
import gx.zelda.core.ui.BulkConfirmDialog
import gx.zelda.core.ui.BulkUndoSnackbar
import gx.zelda.core.ui.FoundToggle
import gx.zelda.core.ui.FoundToggleStyle
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
import gx.zelda.core.ui.rememberBulkCompleteState
import gx.zelda.shrines.model.ShrineState
import gx.zelda.sidequests.model.QuestKind
import gx.zelda.sidequests.model.QuestState
import gx.zelda.sidequests.model.SideQuest
import kotlinx.coroutines.launch

/** The in-progress toggle: a play button rather than the shrine trackers' map pin. */
internal val InProgressToggle = FoundToggleStyle(Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow, "In progress", "Mark in progress")

/** The tab label and the save file picker's short noun. */
private val QuestKind.shortNoun: String
    get() = when (this) {
        QuestKind.SIDE -> "side"
        QuestKind.SHRINE -> "shrine"
        QuestKind.DLC -> "DLC"
    }

/**
 * Side Quests, Shrine Quests and the DLC's EX quests on the active save file, a tab each, grouped
 * by tower region. Swipe between the tabs or tap one to jump to it; search and the Incomplete
 * filter apply to every tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideQuestTrackerScreen(
    state: SideQuestUiState?,
    loadFailed: Boolean,
    viewModel: SideQuestViewModel,
    onOpenQuest: (String) -> Unit,
    onBack: () -> Unit,
) {
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
                        Text("Side Quests", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { summary ->
                                    SaveFileOption(
                                        summary.file,
                                        state.kinds.joinToString(" · ") {
                                            "${summary.completed[it] ?: 0}/${state.total(it)} ${it.shortNoun}"
                                        },
                                    )
                                },
                                onSelect = viewModel::setSaveFile,
                            )
                        }
                    }
                },
                navigationIcon = { MenuOrBackButton(onBack) },
                actions = {
                    if (state != null) SideQuestMenu(state, viewModel)
                },
            )
        },
    ) { padding ->
        if (state == null) {
            LoadingOrError(loadFailed, "The quest list couldn't be loaded.", padding)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            SideQuestTabs(state, viewModel, bulk, onOpenQuest)
        }
        BulkConfirmDialog(bulk, state.saveFile.label)
    }
}

@Composable
private fun SideQuestMenu(state: SideQuestUiState, viewModel: SideQuestViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Hide quests not started") },
            trailingIcon = { Checkbox(checked = state.prefs.hideUndiscovered, onCheckedChange = null) },
            onClick = {
                viewModel.setHideUndiscovered(!state.prefs.hideUndiscovered)
                open = false
            },
        )
    }
}

@Composable
private fun SideQuestTabs(state: SideQuestUiState, viewModel: SideQuestViewModel, bulk: BulkCompleteState, onOpenQuest: (String) -> Unit) {
    val kinds = state.kinds
    val pager = rememberPagerState { kinds.size }
    // Hoisted so each tab keeps its scroll position while its page is off screen.
    val listStates = kinds.map { rememberLazyListState() }
    val scope = rememberCoroutineScope()

    // Search sits above the tabs, so it stays put while swiping and the keyboard never covers it.
    SideQuestFilterBar(state, viewModel)
    PrimaryTabRow(selectedTabIndex = pager.targetPage) {
        kinds.forEachIndexed { i, kind ->
            Tab(
                selected = pager.targetPage == i,
                onClick = { scope.launch { pager.animateScrollToPage(i) } },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(kind.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${state.completedCount(kind)} / ${state.total(kind)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
    HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
        QuestList(state, kinds[page], viewModel, bulk, onOpenQuest, listStates[page])
    }
}

@Composable
private fun SideQuestFilterBar(state: SideQuestUiState, viewModel: SideQuestViewModel) {
    val filters = state.filters
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filters.query, viewModel::setQuery, "Search quest, giver, place or reward")
        FilterChip(
            selected = filters.incompleteOnly,
            onClick = { viewModel.setIncompleteOnly(!filters.incompleteOnly) },
            label = { Text("Incomplete") },
        )
    }
}

@Composable
private fun QuestList(
    state: SideQuestUiState,
    kind: QuestKind,
    viewModel: SideQuestViewModel,
    bulk: BulkCompleteState,
    onOpenQuest: (String) -> Unit,
    listState: LazyListState,
) {
    val progress = state.progress
    val groups = state.groups(kind)
    val counts = state.regionCounts[kind].orEmpty()
    val quests = state.catalog.ofKind(kind)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "_summary") {
            Text(
                buildString {
                    append("${state.completedCount(kind)} / ${quests.size} ${kind.noun}s complete")
                    append(" · ${quests.count { progress.isStarted(it.id) && it.id !in progress.completed }} in progress")
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        // Acts on what the search and chip leave showing on this tab.
        if (groups.isNotEmpty()) {
            item(key = "_bulk") {
                BulkCompleteRow(
                    state.bulkScope(kind),
                    bulk,
                    onApply = { viewModel.setAllDone(kind, null, it == BulkAction.MARK) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (groups.isEmpty()) {
            item(key = "_empty") {
                Text(
                    "No quests match these filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        for ((region, inRegion) in groups) {
            // The header counts the whole region, not just the quests the filters leave showing.
            val count = counts.getValue(region)
            item(key = "h_$region", contentType = "header") {
                RegionHeader(region, found = count.started, done = count.completed, total = count.total, foundLabel = "Started", leading = {
                    BulkCompleteCheckbox(state.bulkScope(kind, region), bulk, onApply = { viewModel.setAllDone(kind, region, it == BulkAction.MARK) })
                })
            }
            itemsIndexed(inRegion, key = { _, quest -> quest.id }, contentType = { _, _ -> "quest" }) { i, quest ->
                val last = i == inRegion.lastIndex
                if (state.isHidden(quest)) {
                    HiddenRow(
                        "${kind.noun.replaceFirstChar { it.uppercase() }} not started",
                        onReveal = { viewModel.reveal(quest.id) },
                        onFound = { viewModel.setState(quest.id, QuestState.FOUND) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                        toggleStyle = InProgressToggle,
                    )
                } else {
                    QuestRow(
                        quest,
                        questState = progress.state(quest.id),
                        shrineState = state.shrineState(quest),
                        onStateChange = { viewModel.setState(quest.id, it) },
                        onOpen = { onOpenQuest(quest.id) },
                        showDivider = !last,
                        modifier = Modifier.groupRow(last),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestRow(
    quest: SideQuest,
    questState: QuestState,
    shrineState: ShrineState?,
    onStateChange: (QuestState) -> Unit,
    onOpen: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val completed = questState == QuestState.COMPLETED
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Unticking a completed quest leaves it in progress.
                .toggleable(value = completed, role = Role.Checkbox) {
                    onStateChange(if (it) QuestState.COMPLETED else QuestState.FOUND)
                }
                .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Checkbox(checked = completed, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    quest.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${quest.giver} · ${quest.giverLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val tags = questTags(quest, shrineState)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { Tag(it) }
                    }
                }
            }
            FoundToggle(questState, onStateChange, InProgressToggle)
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Details of ${quest.name}")
            }
        }
        if (showDivider) RowDivider()
    }
}

/** Short tags under a quest: whether it's time-limited, its DLC pack and the linked shrine's state. */
private fun questTags(quest: SideQuest, shrineState: ShrineState?): List<String> = listOfNotNull(
    "Limited".takeIf { quest.limited != null },
    quest.pack,
    when (shrineState) {
        ShrineState.COMPLETED -> "Shrine completed"
        ShrineState.FOUND -> "Shrine found"
        else -> null
    },
)
