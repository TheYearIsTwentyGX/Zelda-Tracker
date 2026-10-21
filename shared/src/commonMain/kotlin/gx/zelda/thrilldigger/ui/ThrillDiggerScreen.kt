package gx.zelda.thrilldigger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.adventure.model.AdventureChrome
import gx.zelda.adventure.ui.HideSystemBars
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.MenuOrBackButton
import gx.zelda.thrilldigger.model.Difficulty
import gx.zelda.thrilldigger.model.DiggerAnalysis
import gx.zelda.thrilldigger.model.DiggerBoard
import gx.zelda.thrilldigger.model.Hole
import gx.zelda.thrilldigger.model.HoleOdds
import gx.zelda.thrilldigger.model.TotalInference
import kotlin.math.roundToInt

/**
 * The Thrill Digger solver. Landscape first: the board fills the left and a panel on the right
 * holds the picker, the difficulty and the chosen hole's odds; in a short landscape window (a phone
 * on its side) the top bar and system bars hide, as on HW:DE's map, and the panel carries the
 * menu or Back button. Portrait puts the board on top and the picker under it. The round's Rupee
 * total sits big at the top of the panel, or above the picker in portrait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThrillDiggerScreen(viewModel: ThrillDiggerViewModel, title: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val gameTotal by viewModel.gameTotal.collectAsStateWithLifecycle()
    var enteringTotal by remember { mutableStateOf(false) }
    check?.let { BoardCheckDialog(it, onDismiss = viewModel::dismissCheck) }
    val closeTotal = {
        enteringTotal = false
        viewModel.dismissGameTotal()
    }
    // Filled in, or the skipped holes cleared some other way: nothing left to work out.
    val resolved = state?.total?.resolved ?: true
    LaunchedEffect(resolved) { if (resolved) closeTotal() }
    state?.takeIf { enteringTotal && !resolved }?.let { GameTotalDialog(it, gameTotal, viewModel, onDismiss = closeTotal) }
    val enterTotal = { enteringTotal = true }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val bars = !(landscape && maxHeight.value < AdventureChrome.COMPACT_HEIGHT_DP)
        HideSystemBars(hidden = !bars)
        val panelWidth = (maxWidth * 0.4f).coerceIn(280.dp, 380.dp)
        val screenWidth = maxWidth
        Scaffold(
            topBar = {
                if (bars) TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { MenuOrBackButton(onBack) },
                    actions = { state?.let { BoardActions(it, viewModel) } },
                )
            },
        ) { padding ->
            val s = state
            if (s == null) {
                LoadingOrError(false, "", padding)
                return@Scaffold
            }
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
                    DiggerBoardView(s, viewModel, Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier.width(panelWidth).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!bars) {
                            // The top bar is hidden: its menu or Back button, title and actions come here.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MenuOrBackButton(onBack)
                                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                BoardActions(s, viewModel)
                            }
                        }
                        RupeeTotalPanel(s, enterTotal)
                        DifficultyPicker(s.difficulty, viewModel::setDifficulty)
                        HolePicker(s, viewModel)
                        StatusLine(s, problem)
                        TargetDetails(s)
                        RngControls(s, viewModel)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DifficultyPicker(s.difficulty, viewModel::setDifficulty)
                    // As wide as the screen, as tall as its rows need.
                    val cell = (screenWidth - 24.dp) / s.difficulty.width
                    DiggerBoardView(s, viewModel, Modifier.fillMaxWidth().height(cell * s.difficulty.height))
                    StatusLine(s, problem)
                    RupeeTotalPanel(s, enterTotal)
                    HolePicker(s, viewModel)
                    TargetDetails(s)
                    RngControls(s, viewModel)
                }
            }
        }
    }
}

@Composable
private fun BoardActions(state: ThrillDiggerUiState, viewModel: ThrillDiggerViewModel) {
    TextButton(onClick = { viewModel.undo() }, enabled = state.canUndo) { Text("Undo") }
    TextButton(onClick = { viewModel.reset() }, enabled = !state.board.isEmpty) { Text("Reset") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultyPicker(current: Difficulty, onSelect: (Difficulty) -> Unit) {
    val all = Difficulty.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        all.forEachIndexed { i, d ->
            SegmentedButton(
                selected = d == current,
                onClick = { onSelect(d) },
                shape = SegmentedButtonDefaults.itemShape(i, all.size),
                icon = {},
            ) { Text(d.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** "C3": the hole's column letter and row number. */
internal fun holeLabel(board: DiggerBoard, index: Int): String = "${'A' + board.column(index)}${board.row(index) + 1}"

private fun percent(p: Double): String = when {
    p <= 0.0 -> "0%"
    p >= 1.0 -> "100%"
    p < 0.01 -> "<1%"
    p > 0.99 -> ">99%"
    else -> "${(p * 100).roundToInt()}%"
}

private val Safe = Color(0xFF2E7D32)
private val Risky = Color(0xFFF9A825)
private val Deadly = Color(0xFFC62828)
private val Recommended = Color(0xFFFFB300)

/** Green at no Bomb chance through amber to red at one in two or worse, blended into [surface]. */
private fun heat(bomb: Double, surface: Color): Color {
    val t = (bomb / 0.5).coerceIn(0.0, 1.0).toFloat()
    val hot = if (t < 0.5f) lerp(Safe, Risky, t * 2) else lerp(Risky, Deadly, (t - 0.5f) * 2)
    return lerp(surface, hot, 0.45f)
}

/** The board, its holes as large as fit in [modifier]'s space. */
@Composable
private fun DiggerBoardView(state: ThrillDiggerUiState, viewModel: ThrillDiggerViewModel, modifier: Modifier) {
    val d = state.difficulty
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = minOf(maxWidth / d.width, maxHeight / d.height)
        Column {
            for (r in 0 until d.height) {
                Row {
                    for (c in 0 until d.width) {
                        val i = r * d.width + c
                        HoleCell(state, i, cell, onTap = { viewModel.tapHole(i) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HoleCell(state: ThrillDiggerUiState, index: Int, size: Dp, onTap: () -> Unit) {
    val hole = state.board.holes[index]
    val odds = state.odds(index)
    val colors = MaterialTheme.colorScheme
    val recommended = index == state.recommended
    val target = index == state.target
    val conflicted = index in state.conflict
    val background = when {
        hole != Hole.UNKNOWN -> colors.surfaceContainerHighest
        odds != null -> heat(odds.bomb, colors.surfaceContainer)
        else -> colors.surfaceContainer
    }
    val border = when {
        conflicted -> BorderStroke(3.dp, colors.error)
        target -> BorderStroke(3.dp, colors.primary)
        recommended -> BorderStroke(3.dp, Recommended)
        else -> BorderStroke(1.dp, colors.outlineVariant)
    }
    val shape = RoundedCornerShape(8.dp)
    val description = buildString {
        append(holeLabel(state.board, index)).append(": ").append(holeName(hole))
        if (odds != null) append(", Bomb ${percent(odds.bomb)}, Rupoor ${percent(odds.rupoor)}")
        if (recommended) append(", recommended")
        if (conflicted) append(", conflicts")
    }
    Box(
        Modifier
            .size(size)
            .padding(2.dp)
            .clip(shape)
            .background(background)
            .border(border, shape)
            .clickable(role = Role.Button, onClick = onTap)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = target
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            hole != Hole.UNKNOWN -> HoleIcon(hole, Modifier.fillMaxSize(0.8f))
            odds != null -> OddsLabel(odds, state.difficulty.rupoors > 0, size)
            else -> Text("?", color = colors.onSurfaceVariant)
        }
        if (recommended) {
            Text("★", color = Recommended, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp))
        }
    }
}

/** An undug hole's Bomb chance, and its Rupoor chance on boards that have them; a certain hazard shows faded. */
@Composable
private fun OddsLabel(odds: HoleOdds, rupoors: Boolean, size: Dp) {
    when {
        odds.bomb >= 1 - 1e-9 -> HoleIcon(Hole.BOMB, Modifier.fillMaxSize(0.7f), alpha = 0.5f)
        odds.rupoor >= 1 - 1e-9 -> HoleIcon(Hole.RUPOOR, Modifier.fillMaxSize(0.7f), alpha = 0.5f)
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val small = size < 52.dp
            val icon = if (small) 11.dp else 14.dp
            val text = if (small) 11.sp else 13.sp
            Row(verticalAlignment = Alignment.CenterVertically) {
                HoleIcon(Hole.BOMB, Modifier.size(icon))
                Text(percent(odds.bomb), fontSize = text, fontWeight = FontWeight.Bold)
            }
            if (rupoors) Row(verticalAlignment = Alignment.CenterVertically) {
                HoleIcon(Hole.RUPOOR, Modifier.size(icon))
                Text(percent(odds.rupoor), fontSize = text * 0.9f)
            }
        }
    }
}

/**
 * The picker: the five Rupees in a row with their names and hazard counts, then the Rupoor, the
 * Bomb, Skipped (dug, colour not seen) and Clear. A tap puts that in the target hole: the one
 * tapped, or else the recommended one.
 */
@Composable
private fun HolePicker(state: ThrillDiggerUiState, viewModel: ThrillDiggerViewModel) {
    val rows = listOf(Hole.RUPEES, listOf(Hole.RUPOOR, Hole.BOMB, Hole.SKIPPED, Hole.UNKNOWN))
    val current = state.target?.let { state.board.holes[it] }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { hole ->
                    PickerTile(hole, chosen = hole == current, enabled = state.target != null, onClick = { viewModel.enter(hole) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PickerTile(hole: Hole, chosen: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val detail = when (hole) {
        Hole.UNKNOWN -> "Not dug"
        Hole.RUPOOR -> "−10"
        Hole.BOMB -> "Game over"
        Hole.SKIPPED -> "Missed it"
        else -> hole.hazardRange!!
    }
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.outlineVariant),
        modifier = modifier.semantics { selected = chosen },
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hole == Hole.UNKNOWN) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text("✕", fontSize = 20.sp, color = colors.onSurfaceVariant) }
            } else {
                HoleIcon(hole, Modifier.size(32.dp))
            }
            Text(if (hole == Hole.UNKNOWN) "Clear" else hole.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One line on how the round stands: Rupees left, a finished board, or an impossible one; and the
 * [problem] the last action ran into, if any. The haul is [RupeeTotalPanel]'s.
 */
@Composable
private fun StatusLine(state: ThrillDiggerUiState, problem: String?) {
    problem?.let { Text("Something went wrong: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    val colors = MaterialTheme.colorScheme
    val (text, color) = when (val a = state.analysis) {
        is DiggerAnalysis.Impossible ->
            "These ${a.conflict.size} outlined holes can't all be true: change or clear one of them." to colors.error
        is DiggerAnalysis.Solved -> when {
            state.board.isEmpty -> "Tap what you dug up: it goes in the ★ hole, or in the hole you tap first." to colors.onSurfaceVariant
            a.allRupeesFound -> "Every Rupee is dug up: ${state.total.text} Rupees this round." to colors.primary
            else -> "${state.rupeesLeft} Rupees left to dig" to colors.onSurface
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

/** The target hole: its odds and what its Rupee is likely to be, or what's been entered there. */
@Composable
private fun TargetDetails(state: ThrillDiggerUiState) {
    val index = state.target ?: return
    val hole = state.board.holes[index]
    val odds = state.odds(index)
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val heading = holeLabel(state.board, index) + if (index == state.recommended) " · recommended" else ""
        Text(heading, style = MaterialTheme.typography.titleSmall)
        when {
            hole.isRupee -> Text("${holeName(hole)}: ${hole.hazardRange} hazards around it, worth ${hole.rupees}", style = MaterialTheme.typography.bodySmall)
            hole == Hole.SKIPPED -> Text(
                "Dug, colour not seen: a Rupee worth 1 to 300, and no clue for its neighbours. Enter the in-game total to work it out, or tap its colour if you know it.",
                style = MaterialTheme.typography.bodySmall,
            )
            hole != Hole.UNKNOWN -> Text(holeName(hole), style = MaterialTheme.typography.bodySmall)
            odds != null -> {
                val parts = buildList {
                    add("Bomb ${percent(odds.bomb)}")
                    if (state.difficulty.rupoors > 0) add("Rupoor ${percent(odds.rupoor)}")
                    add("Rupee ${percent(odds.safe)}")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                val worth = (odds.expectedRupees * 10).roundToInt() / 10.0
                Text("Worth $worth Rupees on average", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (odds.safe > 0) {
                    // If it's a Rupee, how likely each colour is.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Hole.RUPEES.forEachIndexed { c, rupee ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                HoleIcon(rupee, Modifier.size(20.dp))
                                Text(percent(odds.colours[c]), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}

/**
 * The Rupees this round has earned from the holes entered, big enough to read at a glance: the
 * known part, and with skipped holes, "+ ? from N skipped", the range, and Enter in-game total.
 */
@Composable
private fun RupeeTotalPanel(state: ThrillDiggerUiState, onEnterGameTotal: () -> Unit) {
    val total = state.total
    val colors = MaterialTheme.colorScheme
    val description = if (total.resolved) "${total.known} Rupees this round" else "${total.known} Rupees this round, plus ${total.skipped} skipped, ${total.text} in all"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Text("Rupees this round", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${total.known}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            if (!total.resolved) {
                Text(
                    " + ? from ${total.skipped} skipped",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }
        if (!total.resolved) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${total.text} in all", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onEnterGameTotal) { Text("Enter in-game total") }
            }
        }
    }
}

/**
 * Asks for the round's total as the game shows it and says what it makes the skipped holes: a
 * colour for each, offered to fill in, or the colour sets it could be.
 */
@Composable
private fun GameTotalDialog(state: ThrillDiggerUiState, check: GameTotalCheck?, viewModel: ThrillDiggerViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val entered = text.toIntOrNull()
    // Only an answer for this board and the number in the box.
    val result = check?.takeIf { it.board == state.board && it.gameTotal == entered }?.result
    val total = state.total
    val submit = { entered?.let { viewModel.checkGameTotal(it) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter in-game total") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val holes = if (total.skipped == 1) "skipped hole is" else "${total.skipped} skipped holes are"
                Text(
                    "The round's total the game shows while you dig. The $holes worth that less the ${total.known} entered.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter(Char::isDigit).take(5) },
                    label = { Text("Rupees this round") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                if (result != null) Text(inferenceText(state.board, result), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            if (result is TotalInference.Fill) {
                TextButton(onClick = { viewModel.fillFromGameTotal() }) { Text(if (result.fill.size == 1) "Fill it in" else "Fill them in") }
            } else {
                TextButton(onClick = { submit() }, enabled = entered != null) { Text("Check") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun colourName(hole: Hole) = "${hole.label} Rupee"

/** What an in-game total says about [board]'s skipped holes, in a sentence or two. */
internal fun inferenceText(board: DiggerBoard, result: TotalInference): String = when (result) {
    is TotalInference.Fill -> if (result.fill.size == 1) {
        "That hole was a ${colourName(result.fill.values.single())}: fill it in?"
    } else {
        "Those holes were " + result.fill.entries.joinToString(", ") { (i, h) -> "${holeLabel(board, i)} ${h.label}" } + ": fill them in?"
    }
    is TotalInference.Ambiguous -> {
        val sets = result.combos.map { c -> c.joinToString(" + ") { it.label } }
        if (sets.size == 1) {
            "They're ${sets.single()}, but the total can't tell which hole is which. Tap one's colour if you remember it."
        } else {
            "It's ambiguous: " + sets.joinToString(", or ") + (if (result.more) ", or more" else "") + "."
        }
    }
    is TotalInference.NoMatch -> "No Rupees for the skipped holes add up to ${result.difference} and fit the board. Check the total and the holes entered."
}

/**
 * Where the odds come from: the switch for the game's RNG (not on Beginner), how many of its boards
 * fit, the loop lock, and Export board (checked against the RNG once finished).
 */
@Composable
private fun RngControls(state: ThrillDiggerUiState, viewModel: ThrillDiggerViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.rngAvailable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use the game's RNG", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = state.rngOn, onCheckedChange = { viewModel.setRng(it) })
            }
            val small = MaterialTheme.typography.bodySmall
            when (val source = state.source) {
                OddsSource.EveryBoard -> Text("Odds over every board the rules allow.", style = small, color = colors.onSurfaceVariant)
                is OddsSource.Listing -> {
                    Text("Listing the boards the game's RNG can deal… ${(source.progress * 100).roundToInt()}%", style = small, color = colors.onSurfaceVariant)
                    LinearProgressIndicator(progress = { source.progress }, modifier = Modifier.fillMaxWidth())
                }
                OddsSource.NoRngFit -> Text(
                    "None of the RNG's boards fits, so these are the odds over every board. Right after starting the game, its RNG may not have settled yet: run around Eldin for a bit first.",
                    style = small,
                    color = colors.error,
                )
                is OddsSource.RngFailed -> {
                    Text("Couldn't use the RNG's boards (${source.reason}), so these are the odds over every board.", style = small, color = colors.error)
                    TextButton(onClick = { viewModel.retryRng() }) { Text("Try again") }
                }
                is OddsSource.Rng -> {
                    val loops = if (source.loops.size == 1) "loop ${source.loops.single() + 1} of ${source.loopCount}" else "${source.loops.size} of ${source.loopCount} loops"
                    Text("${thousands(source.states)} of the RNG's boards fit, on $loops.", style = small)
                    if (source.lockMissed) {
                        Text("Nothing on the locked loop ${source.locked!! + 1} fits, so every loop is counted.", style = small, color = colors.error)
                    }
                    if (source.locked != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Locked to loop ${source.locked + 1}", style = small, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.unlockLoop() }) { Text("Unlock") }
                        }
                    } else if (state.canLockLoop) {
                        TextButton(onClick = { viewModel.lockLoop() }) { Text("Lock to loop ${source.loops.single() + 1} for later rounds") }
                    }
                }
            }
        }
        if (!state.board.isEmpty) {
            TextButton(onClick = { viewModel.exportBoard() }) { Text(if (state.finished) "Export board and check it against the RNG" else "Export board") }
        }
    }
}

private fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** The board as text, with how many RNG states deal it once it's finished, to copy into a report. Copy works while it's checking. */
@Composable
private fun BoardCheckDialog(check: BoardCheck, onDismiss: () -> Unit) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export board") },
        text = {
            SelectionContainer {
                Text(check.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(check.text)) }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
