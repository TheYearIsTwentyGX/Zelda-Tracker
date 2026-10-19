package gx.zelda.adventure.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import gx.zelda.adventure.model.AdventureMap
import gx.zelda.adventure.model.Reward
import gx.zelda.adventure.model.RewardSlot
import gx.zelda.adventure.model.RewardType
import gx.zelda.adventure.model.Search
import gx.zelda.adventure.model.Square
import gx.zelda.adventure.model.SquareStatus
import gx.zelda.core.ui.Tag
import gx.zelda.resources.Res
import gx.zelda.resources.allDrawableResources
import org.jetbrains.compose.resources.painterResource

/** One square: its battle and A-rank limits, its rewards, Gold Skulltulas and searches, all tickable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquareSheet(
    state: AdventureUiState,
    square: Square,
    images: SquareImages,
    viewModel: AdventureMapViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SquareHeader(state, square)
            if (state.spoilers.isHidden(square)) {
                Text(
                    "Unexplored: this save file hasn't reached it yet, so spoiler-safe mode keeps it hidden.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { viewModel.reveal(square.id) }) { Text("Reveal") }
                return@Column
            }
            Battle(state.map, square)
            StatusRow(state, square, viewModel)
            if (square.rewards.isNotEmpty() || square.givesCards.isNotEmpty()) {
                Section("Rewards")
                square.rewards.forEach { RewardRow(state, it, viewModel) }
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
                    CheckRow(
                        checked = state.progress.isDone(gs.id),
                        onChecked = { viewModel.setDone(gs.id, it) },
                        icon = { RewardIcon(RewardIconKind.SKULLTULA, 28.dp) },
                        title = "Gold Skulltula ${i + 1}",
                        lines = listOfNotNull(gs.condition, where),
                    )
                }
            }
            Searches(state, square, images, viewModel)
            if (square.quiz.isNotEmpty()) {
                Section("Quiz answers")
                if (state.spoilers.secretsHidden(square)) {
                    Text("Hidden until you use the Compass.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(square.quiz.mapIndexed { i, a -> "${i + 1}. $a" }.joinToString("   "), style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider()
            val complete = state.progress.isComplete(square)
            OutlinedButton(onClick = { viewModel.setSquareDone(square.id, !complete) }) {
                Text(if (complete) "Clear this square" else "Mark square complete")
            }
        }
    }
}

@Composable
private fun SquareHeader(state: AdventureUiState, square: Square) {
    val map = state.map
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(listOfNotNull(square.label, square.title).joinToString(" · "), style = MaterialTheme.typography.headlineSmall)
            if (square.finalBoss && !state.spoilers.isHidden(square)) {
                Tag("Dark Ruler", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (square.id == map.start) Tag("Start")
        }
        Text(
            "${square.altLabel} in SBAllen's guide · ${map.regionName(square.region)} square",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Battle(map: AdventureMap, square: Square) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        square.battle?.let { Text(it.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
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
        aRankLine(square)?.let {
            Text("A rank: $it", style = MaterialTheme.typography.bodyMedium)
            Text(
                "The game doesn't show these limits; they're the guides' findings. Damage is a percentage of your health.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        square.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** "KOs ≥ 1,200 · Time ≤ 15:00 · Damage ≤ 149%". */
fun aRankLine(square: Square): String? = listOfNotNull(
    square.aRank.kos?.let { "KOs ≥ ${thousands(it)}" },
    square.aRank.minutes?.let { "Time ≤ $it:00" },
    square.aRank.damage?.let { "Damage ≤ $it%" },
).joinToString(" · ").ifEmpty { null }

private fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
private fun StatusRow(state: AdventureUiState, square: Square, viewModel: AdventureMapViewModel) {
    val status = state.progress.status(square)
    val options = SquareStatus.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = status == option,
                onClick = { viewModel.setStatus(square.id, option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(option.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun RewardRow(state: AdventureUiState, reward: Reward, viewModel: AdventureMapViewModel) {
    val map = state.map
    val owner = reward.warrior?.let(map::warriorName)
    val title = when (reward.type) {
        RewardType.HEART_CONTAINER, RewardType.HEART_PIECE, RewardType.WEAPON, RewardType.OUTFIT -> "${reward.name} · ${owner ?: "?"}"
        RewardType.WARRIOR -> "Unlocks ${reward.name}"
        else -> reward.name
    }
    CheckRow(
        checked = state.progress.isDone(reward.id),
        onChecked = { viewModel.setDone(reward.id, it) },
        icon = { RewardIcon(reward.type, 28.dp) },
        title = title,
        lines = listOf(howToEarn(map, reward)),
    )
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

@Composable
private fun Searches(state: AdventureUiState, square: Square, images: SquareImages, viewModel: AdventureMapViewModel) {
    if (square.searches.isEmpty()) return
    Section("Searches")
    val shown = state.spoilers.visibleSearches(square)
    val held = square.searches.size - shown.size
    if (held > 0) {
        Text(
            if (held == 1) "1 search here is hidden." else "$held searches here are hidden.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { viewModel.useCompass(square.id) }) { Text("Use the Compass") }
    }
    val spots = shown.filterNot { it.wholeSquare }
    if (spots.isNotEmpty()) SearchGrid(state.map, square, spots, images)
    shown.forEach { search ->
        val card = state.map.card(search.card)
        val number = spots.indexOf(search).takeIf { it >= 0 }?.let { "${it + 1}. " }.orEmpty()
        val where = if (search.wholeSquare) "on the whole square" else "row ${search.row!! + 1}, column ${search.col!! + 1}"
        val have = if (state.inventory.has(search.card)) {
            "You have this card"
        } else {
            val from = state.map.sourcesOf(search.card).map { it.label }
            "Not found yet" + if (from.isNotEmpty()) " (from ${from.joinToString()})" else ""
        }
        CheckRow(
            checked = state.progress.isDone(search.id),
            onChecked = { viewModel.setDone(search.id, it) },
            icon = { CardIcon(search.card, 28.dp) },
            title = "$number${card?.name ?: search.card}, $where",
            lines = listOfNotNull(card?.effects?.joinToString(" or ") { it.label }, have),
        )
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

@Composable
private fun CheckRow(checked: Boolean, onChecked: (Boolean) -> Unit, icon: @Composable () -> Unit, title: String, lines: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChecked(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(4.dp))
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

enum class RewardIconKind(val resourceName: String) {
    HEART_CONTAINER("hwde_reward_heart_container"),
    HEART_PIECE("hwde_reward_heart_piece"),
    SKULLTULA("hwde_reward_skulltula"),
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
    val icon = Res.allDrawableResources[kind.resourceName]
    if (icon == null) Box(Modifier.size(size)) else Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(size))
}

/** An item card's icon, bundled as `drawable/hwde_card_<id>.png` by tools/hwde/build_maps.py. */
@Composable
fun CardIcon(cardId: String, size: Dp) {
    val icon = Res.allDrawableResources["hwde_card_$cardId"]
    if (icon == null) Box(Modifier.size(size)) else Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(size))
}
