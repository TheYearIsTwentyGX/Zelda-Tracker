package gx.zelda.bossrush.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.adventure.model.AdventureChrome
import gx.zelda.adventure.ui.HideSystemBars
import gx.zelda.art.ArtIds
import gx.zelda.art.ArtImage
import gx.zelda.bossrush.model.BossRow
import gx.zelda.bossrush.model.BossRushBoss
import gx.zelda.bossrush.model.RecordEntry
import gx.zelda.bossrush.model.RecordTime
import gx.zelda.core.formatEpochDay
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.theme.GameProgressBar
import gx.zelda.theme.LocalGameDecor

/**
 * The Lightning Round Records board. Landscape first, like the Thrill Digger solver: the bosses fill
 * the left as cards with their best times, and a panel on the right holds the chosen boss's best,
 * the time entry and its history; a phone on its side hides the bars and the panel carries the menu,
 * the save file and the Hero Mode switch. Portrait puts the panel above the bosses, in one scrolling list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossRushScreen(viewModel: BossRushViewModel, title: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val lastChange by viewModel.lastChange.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(lastChange) {
        val change = lastChange ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(changeText(change), actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(change) else viewModel.dismissChange(change)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val bars = !(landscape && maxHeight.value < AdventureChrome.COMPACT_HEIGHT_DP)
        HideSystemBars(hidden = !bars)
        val panelWidth = (maxWidth * 0.42f).coerceIn(300.dp, 420.dp)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (bars) TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            state?.let { FilePicker(it, viewModel) }
                        }
                    },
                    navigationIcon = { MenuOrBackButton(onBack) },
                    actions = { state?.let { HeroModeSwitch(it, viewModel) } },
                )
            },
        ) { padding ->
            val s = state
            if (s == null) {
                LoadingOrError(loadFailed, "The Lightning Round couldn't be loaded.", padding)
                return@Scaffold
            }
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(padding).padding(start = 8.dp, end = 8.dp)) {
                    BossGrid(s, viewModel, Modifier.weight(1f).fillMaxHeight(), PaddingValues(vertical = 8.dp))
                    Column(
                        Modifier.width(panelWidth).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!bars) {
                            // The top bar is hidden: its menu or Back button, title, file and switch come here.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MenuOrBackButton(onBack)
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    FilePicker(s, viewModel)
                                }
                                HeroModeSwitch(s, viewModel)
                            }
                        }
                        BossPanel(s, viewModel)
                        PrizesCard(s)
                    }
                }
            } else {
                // One scrolling list, so a long history never squeezes the bosses off the screen.
                BossGrid(s, viewModel, Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), PaddingValues(vertical = 8.dp), portrait = true)
            }
        }
    }
}

private fun changeText(change: RecordChange): String = when (change.kind) {
    RecordChange.Kind.ADDED -> "${change.bossTitle}: ${RecordTime.format(change.time)}" + if (change.newBest) ", a new best!" else " added"
    RecordChange.Kind.EDITED -> "${change.bossTitle}: changed to ${RecordTime.format(change.time)}"
    RecordChange.Kind.DELETED -> "${change.bossTitle}: ${RecordTime.format(change.time)} deleted"
}

@Composable
private fun FilePicker(state: BossRushUiState, viewModel: BossRushViewModel) {
    SaveFilePicker(
        current = state.saveFile,
        options = state.saveFiles.map { SaveFileOption(it.file, "${it.set} / ${it.slots} records" + if (it.heroMode) " · Hero Mode" else "") },
        onSelect = viewModel::setSaveFile,
    )
}

/** The file's mode: Skyward Sword locks it to the file, so it's set once per file here. */
@Composable
private fun HeroModeSwitch(state: BossRushUiState, viewModel: BossRushViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp).semantics(mergeDescendants = true) {},
    ) {
        Text("Hero Mode", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 6.dp))
        Switch(checked = state.heroMode, onCheckedChange = { viewModel.setHeroMode(it) })
    }
}

/** The bosses as cards, under the board's totals and notes; in [portrait], under the chosen boss's panel too, with the prizes last. */
@Composable
private fun BossGrid(state: BossRushUiState, viewModel: BossRushViewModel, modifier: Modifier, contentPadding: PaddingValues, portrait: Boolean = false) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (portrait) item(key = "_panel", span = { GridItemSpan(maxLineSpan) }) { BossPanel(state, viewModel) }
        item(key = "_header", span = { GridItemSpan(maxLineSpan) }) { BoardHeader(state) }
        items(state.rows, key = { it.boss.id }) { row ->
            BossCard(row, selected = row.boss.id == state.selected.boss.id, onClick = { viewModel.select(row.boss.id) })
        }
        if (portrait) item(key = "_prizes", span = { GridItemSpan(maxLineSpan) }) { PrizesCard(state) }
    }
}

@Composable
private fun BoardHeader(state: BossRushUiState) {
    val board = state.board
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.locked) LockedBanner(state.lockedBy)
        Text(
            "Records set ${board.set} / ${board.slots}" + if (state.heroMode) " (Hero Mode)" else "",
            style = MaterialTheme.typography.labelLarge,
        )
        GameProgressBar(progress = { if (board.slots == 0) 0f else board.set.toFloat() / board.slots }, modifier = Modifier.fillMaxWidth())
        board.sumOfBests?.let {
            Text(
                "Best times total ${RecordTime.format(it)}" + if (board.set < board.slots) " (${board.set} bosses)" else " (every boss)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        board.hidden.takeIf { it.isNotEmpty() }?.let { hidden ->
            Text(
                "${hidden.joinToString(" and ") { it.title }}'s ${if (hidden.size == 1) "record is" else "records are"} kept but hidden: " +
                    "this is a Normal file. Turn on Hero Mode to show ${if (hidden.size == 1) "it" else "them"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "The Thunder Dragon offers only bosses already beaten on the file. You pick the first and he picks the rest; " +
                "the game keeps your best time on each boss.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LockedBanner(lockedBy: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 10.dp))
            Text(
                "Not open yet on this file: the Thunder Dragon starts the Lightning Round after ${lockedBy.joinToString(" and ")} " +
                    "(Main Quest). Times you enter still count.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BossCard(row: BossRow, selected: Boolean, onClick: () -> Unit) {
    val best = row.best
    OutlinedCard(
        onClick = onClick,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${row.boss.title}, " + (best?.let { "best ${RecordTime.format(it.time)}" } ?: "no record")
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BossIcon(row.boss, 44.dp, dimmed = best == null)
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(row.boss.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.boss.fight?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    best?.let { RecordTime.format(it.time) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (best != null) LocalGameDecor.current.star else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A boss's picture; decorative, the name is always beside it. A missing picture keeps its space. */
@Composable
private fun BossIcon(boss: BossRushBoss, size: Dp, dimmed: Boolean = false) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        ArtImage(
            ArtIds.ssBoss(boss.icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = if (dimmed) 0.45f else 1f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The chosen boss: its best, big, the time entry, and its history to edit or delete. */
@Composable
private fun BossPanel(state: BossRushUiState, viewModel: BossRushViewModel) {
    val row = state.selected
    val boss = row.boss
    var editing by remember { mutableStateOf<RecordEntry?>(null) }
    editing?.let { entry ->
        EditTimeDialog(boss, entry, onDismiss = { editing = null }, onSave = { time ->
            viewModel.editTime(boss.id, entry.id, time)
            editing = null
        })
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BossIcon(boss, 56.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(boss.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(boss.place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val best = row.best
            Column {
                Text("Personal best", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    best?.let { RecordTime.format(it.time) } ?: "No record yet",
                    style = if (best != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                    fontFamily = if (best != null) FontFamily.Monospace else null,
                    fontWeight = FontWeight.Bold,
                    color = if (best != null) LocalGameDecor.current.star else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (best != null) {
                    val improvement = row.improvement
                    Text(
                        listOfNotNull(
                            best.day?.let { "Set ${formatEpochDay(it)}" },
                            improvement?.let { "${RecordTime.formatDifference(it)} faster than ${RecordTime.format(row.previousBest!!.time)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TimeEntry(boss, onAdd = { viewModel.addTime(boss.id, it) })
            boss.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (row.history.isNotEmpty()) {
                HorizontalDivider()
                Text("Times entered", style = MaterialTheme.typography.labelLarge)
                row.history.forEach { entry ->
                    HistoryRow(entry, isBest = entry == best, onEdit = { editing = entry }, onDelete = { viewModel.deleteEntry(boss.id, entry.id) })
                }
            }
        }
    }
}

/**
 * The quick way in: digits fill from the right like a stopwatch (14532 is 1:45.32), and Enter adds
 * the time and clears the field for the next one. The time it reads shows under it as you type.
 */
@Composable
private fun TimeEntry(boss: BossRushBoss, onAdd: (Int) -> Unit) {
    var text by rememberSaveable(boss.id) { mutableStateOf("") }
    val parsed = RecordTime.parse(text)
    val add = {
        parsed?.let {
            onAdd(it)
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() || it in ":.,'\"" }.take(9) },
            label = { Text("New time") },
            placeholder = { Text("m:ss.cc or digits") },
            supportingText = {
                Text(
                    when {
                        text.isBlank() -> "Digits fill from the right: 14532 is 1:45.32"
                        parsed == null -> "Not a time (up to 59:59.99)"
                        else -> "= ${RecordTime.format(parsed)}"
                    },
                )
            },
            isError = text.isNotBlank() && parsed == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { add() }, enabled = parsed != null, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)) { Text("Add") }
    }
}

@Composable
private fun HistoryRow(entry: RecordEntry, isBest: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            RecordTime.format(entry.time),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isBest) FontWeight.Bold else null,
            color = if (isBest) LocalGameDecor.current.star else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            listOfNotNull(if (isBest) "Best" else null, entry.day?.let(::formatEpochDay)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${RecordTime.format(entry.time)}") }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${RecordTime.format(entry.time)}") }
    }
}

@Composable
private fun EditTimeDialog(boss: BossRushBoss, entry: RecordEntry, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable(entry.id) { mutableStateOf(RecordTime.format(entry.time)) }
    val parsed = RecordTime.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${boss.title}'s time") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() || it in ":.,'\"" }.take(9) },
                singleLine = true,
                isError = parsed == null,
                supportingText = { Text(parsed?.let { "= ${RecordTime.format(it)}" } ?: "Not a time (up to 59:59.99)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onSave) }),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            )
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The round's prizes, read from Side Quests & Minigames' Lightning Round item rather than copied:
 * its tiers (4, 8 and 11 wins in a row) and its notes on the other wins.
 */
@Composable
private fun PrizesCard(state: BossRushUiState) {
    val quest = state.data.sideQuest ?: return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Prizes for wins in a row", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            quest.tiers.forEach { tier ->
                Text("${tier.name}: ${tier.reward.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                tier.howTo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            quest.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Tick the prizes in Side Quests & Minigames.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
