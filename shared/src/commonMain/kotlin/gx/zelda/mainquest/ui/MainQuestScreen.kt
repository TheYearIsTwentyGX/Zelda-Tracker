package gx.zelda.mainquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.LoadingOrError
import gx.zelda.core.ui.SaveFileOption
import gx.zelda.core.ui.SaveFilePicker
import gx.zelda.core.ui.Tag
import gx.zelda.mainquest.model.DivineBeast
import gx.zelda.mainquest.model.Dungeon
import gx.zelda.mainquest.model.InventoryItem
import gx.zelda.mainquest.model.MainQuestSection
import gx.zelda.mainquest.model.Memory
import gx.zelda.mainquest.model.SheikahTower
import gx.zelda.mainquest.model.TigerScroll
import kotlinx.coroutines.launch

/** The most checklists a game's catalog can have: one scroll position is kept per page. */
private val MAX_SECTIONS = MainQuestSection.entries.size

private val MainQuestSection.label: String
    get() = when (this) {
        MainQuestSection.TOWERS -> "Towers"
        MainQuestSection.DIVINE_BEASTS -> "Divine Beasts"
        MainQuestSection.MEMORIES -> "Memories"
        MainQuestSection.DUNGEONS -> "Dungeons"
        MainQuestSection.ITEMS -> "Items"
        MainQuestSection.TIGER_SCROLLS -> "Tiger Scrolls"
    }

/** "Activated", "Freed" or "Recovered": the tag on a finished item. */
private val MainQuestSection.doneWord: String
    get() = when (this) {
        MainQuestSection.TOWERS -> "Activated"
        MainQuestSection.DIVINE_BEASTS -> "Freed"
        MainQuestSection.MEMORIES -> "Recovered"
        MainQuestSection.DUNGEONS -> "Cleared"
        MainQuestSection.ITEMS -> "Obtained"
        MainQuestSection.TIGER_SCROLLS -> "Learned"
    }

/** The noun the save file picker counts in. */
private val MainQuestSection.shortNoun: String
    get() = when (this) {
        MainQuestSection.TOWERS -> "towers"
        MainQuestSection.DIVINE_BEASTS -> "beasts"
        MainQuestSection.MEMORIES -> "memories"
        MainQuestSection.DUNGEONS -> "dungeons"
        MainQuestSection.ITEMS -> "items"
        MainQuestSection.TIGER_SCROLLS -> "scrolls"
    }

/**
 * Main quest progress on the active save file: Sheikah Towers, Divine Beasts and Recovered
 * Memories in BotW, or dungeons, items and Tiger Scrolls in The Minish Cap. The tab row doubles as
 * the summary, with each checklist's count under its name; swipe between the checklists or tap a
 * tab to jump to one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainQuestScreen(
    state: MainQuestUiState?,
    loadFailed: Boolean,
    viewModel: MainQuestViewModel,
    onBack: () -> Unit,
    title: String = "Main Quest Progress",
) {
    // Until the catalog loads there are no checklists to page through.
    val sections = state?.catalog?.sections.orEmpty()
    val pager = rememberPagerState { sections.size }
    // Hoisted so each checklist keeps its scroll position while its page is off screen.
    val listStates = List(MAX_SECTIONS) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state != null) {
                            SaveFilePicker(
                                current = state.saveFile,
                                options = state.saveFiles.map { summary ->
                                    SaveFileOption(
                                        summary.file,
                                        sections.joinToString(" · ") {
                                            "${summary.counts[it] ?: 0}/${state.catalog.total(it)} ${it.shortNoun}"
                                        },
                                    )
                                },
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
        if (state == null) {
            LoadingOrError(loadFailed, "The main quest list couldn't be loaded.", padding)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pager.targetPage) {
                sections.forEachIndexed { index, s ->
                    Tab(
                        selected = pager.targetPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${state.doneCount(s)} / ${state.catalog.total(s)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
            HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
                val section = sections[page]
                LazyColumn(
                    state = listStates[page],
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "summary") { SummaryCard(state, section) }
                    when (section) {
                        MainQuestSection.TOWERS -> items(state.catalog.towers, key = { it.id }) { tower ->
                            TowerCard(tower, state.isDone(section, tower.id)) { viewModel.setDone(section, tower.id, it) }
                        }
                        MainQuestSection.DIVINE_BEASTS -> items(state.catalog.divineBeasts, key = { it.id }) { beast ->
                            BeastCard(beast, state.isDone(section, beast.id)) { viewModel.setDone(section, beast.id, it) }
                        }
                        MainQuestSection.MEMORIES -> items(state.catalog.memories, key = { it.id }) { memory ->
                            MemoryCard(memory, state.isDone(section, memory.id)) { viewModel.setDone(section, memory.id, it) }
                        }
                        MainQuestSection.DUNGEONS -> items(state.catalog.dungeons, key = { it.id }) { dungeon ->
                            DungeonCard(dungeon, state.isDone(section, dungeon.id)) { viewModel.setDone(section, dungeon.id, it) }
                        }
                        MainQuestSection.ITEMS -> state.catalog.itemsByCategory.forEach { (category, inCategory) ->
                            item(key = "_${category.name}") {
                                GroupHeader(category.label, inCategory.count { state.isDone(section, it.id) }, inCategory.size)
                            }
                            items(inCategory, key = { it.id }) { item ->
                                InventoryItemCard(item, state.isDone(section, item.id)) { viewModel.setDone(section, item.id, it) }
                            }
                        }
                        MainQuestSection.TIGER_SCROLLS -> {
                            items(state.catalog.scrolls, key = { it.id }) { scroll ->
                                TigerScrollCard(scroll, state.isDone(section, scroll.id)) { viewModel.setDone(section, scroll.id, it) }
                            }
                            val upgrades = state.catalog.techniqueUpgrades
                            if (upgrades.isNotEmpty()) {
                                item(key = "_upgrades") { GroupHeader("Technique upgrades", state.upgradeCount, upgrades.size) }
                                items(upgrades, key = { it.id }) { upgrade ->
                                    TigerScrollCard(upgrade, state.isDone(section, upgrade.id)) { viewModel.setDone(section, upgrade.id, it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: MainQuestUiState, section: MainQuestSection) {
    val done = state.doneCount(section)
    val total = state.catalog.total(section)
    SectionCard {
        Text("$done / $total ${section.label.lowercase()} ${section.doneWord.lowercase()}", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Text(
            when (section) {
                MainQuestSection.TOWERS ->
                    "Each tower you activate fills in its part of the map and becomes a travel gate."
                MainQuestSection.DIVINE_BEASTS ->
                    "Impa's \"Free the Divine Beasts\" asks for all four, in any order. Each one you free grants its " +
                        "Champion's ability and a Heart Container, and fires on Hyrule Castle when you face Calamity Ganon."
                MainQuestSection.MEMORIES -> memoriesSummary(state)
                MainQuestSection.DUNGEONS ->
                    "Elements: ${state.elementCount} / ${state.catalog.elementTotal}. Each one goes to the Elemental " +
                        "Sanctuary in Hyrule Castle's courtyard, which infuses it into the White Sword: Earth and Fire " +
                        "together let you split in two, Water in three, and Wind makes it the Four Sword. The Fortress " +
                        "of Winds and Dark Hyrule Castle have no Element."
                MainQuestSection.ITEMS -> itemsSummary(state)
                MainQuestSection.TIGER_SCROLLS ->
                    "Technique upgrades: ${state.upgradeCount} / ${state.catalog.techniqueUpgrades.size}. Only the Spin " +
                        "Attack is needed to finish the game. Swiftblade I teaches the Great Spin Attack once you have " +
                        "the other seven scrolls, and three more brothers, found through Kinstone Fusions, improve " +
                        "techniques you already know. Nobody charges for lessons."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun memoriesSummary(state: MainQuestUiState): String {
    val album = state.albumCount
    val albumSize = state.catalog.albumSize
    val albumLine = if (album < albumSize) {
        "Album pictures: $album / $albumSize. Impa shows you the final picture (memory 17) once all $albumSize are recovered."
    } else {
        "All $albumSize album pictures recovered, so Impa will show you the final picture (memory 17)."
    }
    return "$albumLine The four Champion memories come from the Divine Beast quests and the last from drawing the " +
        "Master Sword. Recovering the 13 pictures completes \"Captured Memories\" and unlocks a post-credits scene."
}

private fun itemsSummary(state: MainQuestUiState): String {
    val missable = state.catalog.items.filter { it.missable }.joinToString { it.name }
    val postgame = state.catalog.items.filter { it.postgame }.joinToString { it.name }
    return buildString {
        append(
            "The three bomb bags, three quivers and three wallets can be found in any order; each one raises the " +
                "limit a step (bombs 10 → 30 → 50 → 99, Rupees 100 → 300 → 500 → 999).",
        )
        if (missable.isNotEmpty()) append(" Missable: $missable.")
        if (postgame.isNotEmpty()) append(" After the final boss: $postgame.")
    }
}

@Composable
private fun TowerCard(tower: SheikahTower, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    ItemCard(
        title = tower.name,
        tags = listOf(tower.region),
        doneWord = MainQuestSection.TOWERS.doneWord,
        done = done,
        onDoneChange = onDoneChange,
    ) {
        Text(tower.location, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Note(tower.tip)
    }
}

@Composable
private fun BeastCard(beast: DivineBeast, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    ItemCard(
        title = "Divine Beast ${beast.name}",
        tags = listOf(beast.region, "${beast.champion} · ${beast.race}"),
        doneWord = MainQuestSection.DIVINE_BEASTS.doneWord,
        done = done,
        onDoneChange = onDoneChange,
    ) {
        Text(beast.location, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Labeled("Main quest: ${beast.quest}", beast.start)
        Labeled("Prepare", beast.prep.joinToString("\n") { "• $it" })
        Labeled("Boss", beast.boss)
        Labeled("Reward", beast.rewards.joinToString(" · "))
        Note("${beast.ability}: ${beast.abilityEffect}")
    }
}

@Composable
private fun MemoryCard(memory: Memory, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    val source = when {
        memory.photo != null -> "Album picture ${memory.photo}"
        memory.finalPicture -> "Final picture"
        else -> memory.quest?.let { "Quest: $it" }
    }
    ItemCard(
        title = "${memory.number}. ${memory.title}",
        tags = listOfNotNull(source),
        doneWord = MainQuestSection.MEMORIES.doneWord,
        done = done,
        onDoneChange = onDoneChange,
    ) {
        Text(memory.location, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(memory.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DungeonCard(dungeon: Dungeon, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    ItemCard(
        title = "${dungeon.number}. ${dungeon.name}",
        tags = listOfNotNull(dungeon.region, dungeon.element?.let { "$it Element" }),
        doneWord = MainQuestSection.DUNGEONS.doneWord,
        done = done,
        onDoneChange = onDoneChange,
    ) {
        Labeled("Getting there", dungeon.access)
        dungeon.item?.let { Labeled("Item", it) }
        Labeled("Boss", dungeon.boss)
        if (dungeon.rewards.isNotEmpty()) Labeled("Reward", dungeon.rewards.joinToString(" · "))
        dungeon.sanctuary?.let { Labeled("Elemental Sanctuary", it) }
        Note(dungeon.tip)
    }
}

@Composable
private fun InventoryItemCard(item: InventoryItem, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    ItemCard(
        title = item.name,
        tags = listOfNotNull(item.region, "Missable".takeIf { item.missable }, "Post-game".takeIf { item.postgame }),
        doneWord = MainQuestSection.ITEMS.doneWord,
        done = done,
        onDoneChange = onDoneChange,
    ) {
        Text(item.how, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TigerScrollCard(scroll: TigerScroll, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    ItemCard(
        title = scroll.name,
        tags = listOf(scroll.teacher),
        doneWord = MainQuestSection.TIGER_SCROLLS.doneWord,
        done = done,
        onDoneChange = onDoneChange,
    ) {
        Text(scroll.location, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Labeled("Before he'll teach it", scroll.requirement)
        Note(scroll.effect)
    }
}

/** A heading inside a checklist, with how much of the group is done. */
@Composable
private fun GroupHeader(label: String, done: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("$done / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A checklist entry: title, tags and a switch, then [content]. */
@Composable
private fun ItemCard(
    title: String,
    tags: List<String>,
    doneWord: String,
    done: Boolean,
    onDoneChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { Tag(it) }
                    if (done) {
                        Tag(
                            doneWord,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Switch(
                checked = done,
                onCheckedChange = onDoneChange,
                modifier = Modifier.semantics { contentDescription = "$title ${doneWord.lowercase()}" },
            )
        }
        content()
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
