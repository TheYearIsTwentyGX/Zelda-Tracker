package gx.zelda.adventure.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import gx.zelda.adventure.model.AdventureChrome
import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.AdventureReach
import gx.zelda.adventure.model.Reward
import gx.zelda.adventure.model.RewardSlot
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Search
import gx.zelda.adventure.model.Square
import gx.zelda.adventure.model.SquareStatus
import gx.zelda.art.ArtIds
import gx.zelda.art.ArtImage
import gx.zelda.core.ui.Tag

/** A square's details in a bottom sheet, opened all the way so nothing needs dragging up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquareSheet(
    state: AdventureUiState,
    square: Square,
    images: SquareImages,
    viewModel: AdventureMapViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SquareDetails(state, square, images, viewModel, onClose = null, Modifier.navigationBarsPadding())
    }
}

/**
 * A square's details down the right of a short landscape window ([AdventureChrome.squareContainer]),
 * with the map or list still usable beside it. Tapping another square swaps what it shows; ✕, back
 * or tapping the same square closes it.
 */
@Composable
fun SquarePanel(
    state: AdventureUiState,
    square: Square,
    images: SquareImages,
    viewModel: AdventureMapViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CloseOnBack(enabled = true, onBack = onClose)
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        SquareDetails(state, square, images, viewModel, onClose = onClose, Modifier.fillMaxSize().navigationBarsPadding())
    }
}

/**
 * One square, as the sheet and the side panel both show it. What's ticked most comes first, so the
 * usual visit (an A rank and a reward or two) needs no scrolling: the label, battle and A-rank
 * limits; the Cleared / A-rank control and *Mark complete*; then every reward, Gold Skulltula and
 * search as a tick box, two to a row where they fit. The mission, notes, search picture and the
 * rest follow.
 */
@Composable
fun SquareDetails(
    state: AdventureUiState,
    square: Square,
    images: SquareImages,
    viewModel: AdventureMapViewModel,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // A new square starts at the top.
    val scroll = key(square.id) { rememberScrollState() }
    Column(
        modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val hidden = state.spoilers.isHidden(square)
        SquareHeader(state, square, hidden, onClose)
        if (hidden) {
            Text(
                "Unexplored: this save file hasn't reached it yet, so spoiler-safe mode keeps it hidden.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { viewModel.reveal(square.id) }) { Text("Reveal") }
            return@Column
        }
        AdventureReach.unsealing(square)?.takeIf { AdventureReach.sealed(state.progress, square) }?.let { seal ->
            Tag("Sealed: do its ${state.map.card(seal.card)?.name ?: seal.card} search before the battle")
        }
        CompletionControls(state, square, viewModel)
        Checks(state, square, viewModel)
        HorizontalDivider()
        Battle(state.map, square)
        if (square.givesCards.isNotEmpty()) {
            Section("Item cards")
            square.givesCards.forEach { card ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardIcon(card, 28.dp)
                    Column {
                        Text("${state.map.card(card)?.name ?: card} item card", style = MaterialTheme.typography.bodyLarge)
                        Text("On a clear, by any warrior", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (square.skulltulas.isNotEmpty()) {
            Section("Gold Skulltulas")
            square.skulltulas.forEachIndexed { i, gs ->
                val where = if (state.spoilers.secretsHidden(square)) "Where: hidden until you use the Compass" else gs.where?.let { "Where: $it" }
                Column {
                    Text("${i + 1}. ${gs.condition}", style = MaterialTheme.typography.bodyMedium)
                    where?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        SearchDetails(state, square, images)
        if (square.quiz.isNotEmpty()) {
            Section("Quiz answers")
            if (state.spoilers.secretsHidden(square)) {
                Text("Hidden until you use the Compass.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(square.quiz.mapIndexed { i, a -> "${i + 1}. $a" }.joinToString("   "), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** The label, then the battle and its A-rank limits on one line; ✕ when [onClose] is given. */
@Composable
private fun SquareHeader(state: AdventureUiState, square: Square, hidden: Boolean, onClose: (() -> Unit)?) {
    val map = state.map
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = if (onClose != null) 4.dp else 0.dp)) {
        Column(Modifier.weight(1f).padding(top = if (onClose != null) 8.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(listOfNotNull(square.label, square.title).joinToString(" · "), style = MaterialTheme.typography.titleLarge)
                if (square.finalBoss && !hidden) {
                    Tag("Dark Ruler", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
                }
                if (square.id in map.starts) Tag("Start")
            }
            val line = if (hidden) "${map.regionName(square.region)} square"
            else listOfNotNull(square.battle?.label, aRankLine(square)?.let { "A rank: $it" }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onClose?.let {
            IconButton(onClick = it) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
    }
}

/** The Cleared / A-rank control and *Mark complete*: side by side where there's room. */
@Composable
private fun CompletionControls(state: AdventureUiState, square: Square, viewModel: AdventureMapViewModel) {
    val complete = state.progress.isComplete(square)
    val markAll = @Composable {
        OutlinedButton(onClick = { viewModel.setSquareDone(square.id, !complete) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text(if (complete) "Untick all" else "Mark complete", maxLines = 1)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 360.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusRow(state, square, viewModel, Modifier.weight(1f))
                markAll()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusRow(state, square, viewModel, Modifier.fillMaxWidth())
                markAll()
            }
        }
    }
}

@Composable
private fun StatusRow(state: AdventureUiState, square: Square, viewModel: AdventureMapViewModel, modifier: Modifier) {
    val status = state.progress.status(square)
    val options = SquareStatus.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = status == option,
                onClick = { viewModel.setStatus(square.id, option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                // No tick: the fill shows the choice, and the labels keep the room.
                icon = {},
                label = { Text(if (option == SquareStatus.NONE) "Not yet" else option.label, maxLines = 1) },
            )
        }
    }
}

/** One tick box: a reward, a Gold Skulltula or a search. */
private class Check(val id: String, val title: String, val line: String?, val icon: @Composable () -> Unit)

/** Every reward, Gold Skulltula and search on [square] as tick boxes, two to a row where they fit. */
@Composable
private fun Checks(state: AdventureUiState, square: Square, viewModel: AdventureMapViewModel) {
    val map = state.map
    val shown = state.spoilers.visibleSearches(square)
    val spots = shown.filterNot { it.wholeSquare }
    val checks = buildList {
        square.rewards.forEach { r -> add(Check(r.id, rewardTitle(map, r), howToEarn(map, r)) { RewardIcon(r.type, 24.dp) }) }
        square.skulltulas.forEachIndexed { i, gs -> add(Check(gs.id, "Gold Skulltula ${i + 1}", gs.condition) { RewardIcon(RewardIconKind.SKULLTULA, 24.dp) }) }
        shown.forEach { s ->
            val number = spots.indexOf(s).takeIf { it >= 0 }?.let { "${it + 1}. " }.orEmpty()
            val where = if (s.wholeSquare) "Whole square" else "Row ${s.row!! + 1}, column ${s.col!! + 1}"
            val have = if (state.inventory.has(s.card)) "you have it" else "not found yet"
            add(Check(s.id, "$number${map.card(s.card)?.name ?: s.card}", "$where · $have") { CardIcon(s.card, 24.dp) })
        }
    }
    if (checks.isNotEmpty()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = AdventureChrome.checkColumns(maxWidth.value)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                checks.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { c ->
                            CheckCell(
                                checked = state.progress.isDone(c.id),
                                onChecked = { viewModel.setDone(c.id, it) },
                                check = c,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    val held = square.searches.size - shown.size
    if (held > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (held == 1) "1 search here is hidden." else "$held searches here are hidden.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { viewModel.useCompass(square.id) }) { Text("Use the Compass") }
        }
    }
}

@Composable
private fun CheckCell(checked: Boolean, onChecked: (Boolean) -> Unit, check: Check, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 6.dp))
        check.icon()
        Column(Modifier.weight(1f).padding(start = 6.dp, end = 4.dp)) {
            Text(check.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            check.line?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The mission and everything else about the battle; the A-rank limits are in the header. */
@Composable
private fun Battle(map: AdventureMap, square: Square) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(square.mission, style = MaterialTheme.typography.titleMedium)
        square.rule?.let {
            Tag(it, container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
        }
        if (square.warriors.isNotEmpty()) {
            val names = square.warriors.map { pick -> map.warriorName(pick.warrior) + (pick.weapon?.let { " (${it.replace('_', ' ')})" } ?: "") }
            Text(
                if (names.size == 1) "Warrior: ${names.single()}" else "Warrior: one of ${names.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        square.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.tertiary)
        }
        Text(
            listOfNotNull(
                "${square.altLabel} in SBAllen's guide · ${map.regionName(square.region)} square.",
                "The game doesn't show A-rank limits; they're the guides' findings. Damage is a percentage of your health.".takeIf { aRankLine(square) != null },
            ).joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "KOs ≥ 1,200 · Time ≤ 15:00 · Damage ≤ 149%". */
fun aRankLine(square: Square): String? = listOfNotNull(
    square.aRank.kos?.let { "KOs ≥ ${thousands(it)}" },
    square.aRank.minutes?.let { "Time ≤ $it:00" },
    square.aRank.damage?.let { "Damage ≤ $it%" },
).joinToString(" · ").ifEmpty { null }

private fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** "Heart Container · Zelda", "Unlocks Marin", "Fairy Food - Magic Beans". */
fun rewardTitle(map: AdventureMap, reward: Reward): String {
    val owner = reward.warrior?.let(map::warriorName)
    return when (reward.type) {
        RewardType.HEART_CONTAINER, RewardType.HEART_PIECE, RewardType.WEAPON, RewardType.OUTFIT -> "${reward.name} · ${owner ?: "?"}"
        RewardType.WARRIOR -> "Unlocks ${reward.name}"
        else -> reward.name
    }
}

/** "A rank as Zelda", "Clear it, any warrior", "Capture East Goron Keep as Zelda", "In a pot at South Field Keep". */
fun howToEarn(map: AdventureMap, reward: Reward): String {
    // Costumes and warrior unlocks go to whoever clears it; the rest need their owner.
    val anyone = reward.type in setOf(RewardType.OUTFIT, RewardType.WARRIOR) || reward.warrior == null
    val who = if (anyone) "any warrior" else map.warriorName(reward.warrior!!)
    return when (reward.slot) {
        RewardSlot.A_RANK -> if (anyone) "A rank, any warrior" else "A rank as $who"
        RewardSlot.CLEAR -> if (anyone) "Clear it, any warrior" else "Clear it as $who"
        RewardSlot.TREASURE -> {
            val keep = reward.keep ?: "a keep"
            when {
                reward.container != null -> "In a ${reward.container} at $keep"
                anyone -> "Capture $keep"
                else -> "Capture $keep as $who"
            }
        }
    }
}

/** The square's picture with each search spot outlined, and what each card does there. */
@Composable
private fun SearchDetails(state: AdventureUiState, square: Square, images: SquareImages) {
    val shown = state.spoilers.visibleSearches(square)
    if (shown.isEmpty()) return
    Section("Searches")
    val spots = shown.filterNot { it.wholeSquare }
    if (spots.isNotEmpty()) SearchGrid(state.map, square, spots, images)
    shown.forEach { search ->
        val card = state.map.card(search.card)
        val number = spots.indexOf(search).takeIf { it >= 0 }?.let { "${it + 1}. " }.orEmpty()
        val have = if (state.inventory.has(search.card)) {
            "You have this card"
        } else {
            val from = state.map.sourcesOf(search.card).map { it.label }
            "Not found yet" + if (from.isNotEmpty()) " (from ${from.joinToString()})" else ""
        }
        Column {
            Text("$number${card?.name ?: search.card}", style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(card?.effects?.joinToString(" or ") { it.label }, have).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The square's picture with the search screen's 16×11 grid over it, and each search's cell
 * outlined and numbered as the list below numbers them.
 */
@Composable
private fun SearchGrid(map: AdventureMap, square: Square, spots: List<Search>, images: SquareImages) {
    LaunchedEffect(square.id) { images.load(listOf(square)) }
    val text = rememberTextMeasurer()
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val description = spots.mapIndexed { i, s -> "${i + 1} at row ${s.row!! + 1}, column ${s.col!! + 1}" }.joinToString("; ")
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(map.squareWidth.toFloat() / map.squareHeight)
            .semantics { contentDescription = "Search spots: $description" },
    ) {
        images.version
        val cw = size.width / AdventureMap.SEARCH_COLS
        val ch = size.height / AdventureMap.SEARCH_ROWS
        images.square(square)?.let {
            drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None)
        } ?: drawRect(placeholder)
        for (c in 1 until AdventureMap.SEARCH_COLS) drawLine(Color.Black.copy(alpha = 0.25f), Offset(c * cw, 0f), Offset(c * cw, size.height))
        for (r in 1 until AdventureMap.SEARCH_ROWS) drawLine(Color.Black.copy(alpha = 0.25f), Offset(0f, r * ch), Offset(size.width, r * ch))
        spots.forEachIndexed { i, s ->
            val tl = Offset(s.col!! * cw, s.row!! * ch)
            drawRect(Color.Black, tl, Size(cw, ch), style = Stroke(5.dp.toPx()))
            drawRect(Color(0xFFFFEB3B), tl, Size(cw, ch), style = Stroke(2.5.dp.toPx()))
            val layout = text.measure("${i + 1}", TextStyle(color = Color.White, fontWeight = FontWeight.Bold))
            val at = Offset(tl.x + cw / 2 - layout.size.width / 2f, (tl.y - layout.size.height).coerceAtLeast(0f))
            drawRect(Color.Black.copy(alpha = 0.7f), at, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
            drawText(layout, topLeft = at)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

/** The reward icons, `hwde/reward/<artName>`. */
enum class RewardIconKind(val artName: String) {
    HEART_CONTAINER("heart_container"),
    HEART_PIECE("heart_piece"),
    SKULLTULA("skulltula"),
}

/** The game's Adventure Mode icon for hearts and Skulltulas; other rewards keep the same space. */
@Composable
fun RewardIcon(type: RewardType, size: Dp) = when (type) {
    RewardType.HEART_CONTAINER -> RewardIcon(RewardIconKind.HEART_CONTAINER, size)
    RewardType.HEART_PIECE -> RewardIcon(RewardIconKind.HEART_PIECE, size)
    else -> Box(Modifier.size(size))
}

@Composable
fun RewardIcon(kind: RewardIconKind, size: Dp) {
    ArtImage(ArtIds.hwdeReward(kind.artName), contentDescription = null, modifier = Modifier.size(size))
}

/** An item card's icon (`hwde/card/<id>`), bundled as `drawable/hwde_card_<id>.png` by tools/hwde/build_maps.py. */
@Composable
fun CardIcon(cardId: String, size: Dp) {
    ArtImage(ArtIds.hwdeCard(cardId), contentDescription = null, modifier = Modifier.size(size))
}
