package gx.zelda.sidequests.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gx.zelda.shrines.model.ShrineState
import gx.zelda.sidequests.model.QuestState
import gx.zelda.sidequests.model.SideQuest
import gx.zelda.sidequests.model.questLabel

/**
 * One quest: who gives it and where, what it needs first, what it asks and what it gives. A Shrine
 * Quest also shows its shrine's state from the shrine tracker, which completing the quest doesn't
 * change. [onOpenArmor] opens a reward armor piece in the armor tracker; null hides the links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideQuestDetailScreen(
    state: SideQuestUiState,
    questId: String,
    viewModel: SideQuestViewModel,
    onOpenArmor: ((String) -> Unit)?,
    onBack: () -> Unit,
) {
    val quest = state.catalog.quests.firstOrNull { it.id == questId } ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(quest.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${quest.kind.noun.replaceFirstChar { it.uppercase() }} · ${state.saveFile.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = state.progress.state(quest.id)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = QuestState.entries
                options.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = current == option,
                        onClick = { viewModel.setState(quest.id, option) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        label = { Text(option.questLabel, maxLines = 1) },
                    )
                }
            }

            quest.limited?.let { Note(it) }

            Section("Quest giver") {
                Text(quest.giver, style = MaterialTheme.typography.bodyLarge)
                Text(quest.giverLocation, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull("${quest.region} Tower region", quest.pack).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            quest.prerequisites?.let { prerequisites ->
                Section("Before you start") {
                    Text(prerequisites, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Section("Objective") {
                Text(quest.objective, style = MaterialTheme.typography.bodyLarge)
            }

            Section("Reward") {
                Text(quest.reward, style = MaterialTheme.typography.bodyLarge)
                if (onOpenArmor != null) {
                    quest.rewardArmor.forEach { pieceId ->
                        val name = state.data.armorNames[pieceId] ?: return@forEach
                        OutlinedButton(onClick = { onOpenArmor(pieceId) }) {
                            Text(name)
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "in Armor Progress",
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }

            if (quest.shrineId != null) {
                Section("Shrine") { LinkedShrine(state, quest) }
            }
        }
    }
}

/** The Shrine Quest's shrine as the shrine tracker has it on the same save file. */
@Composable
private fun LinkedShrine(state: SideQuestUiState, quest: SideQuest) {
    val name = state.shrineName(quest) ?: quest.shrineId
    val shrineState = state.shrineState(quest)
    Text("$name Shrine", style = MaterialTheme.typography.bodyLarge)
    Text(
        when (shrineState) {
            null -> "Its state isn't available."
            ShrineState.UNDISCOVERED -> "Not marked found in Shrines yet."
            else -> "${shrineState.label} in Shrines."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (shrineState == ShrineState.COMPLETED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
    Text(
        "Completing the quest doesn't complete the shrine: mark the shrine in the Shrines tracker once you've " +
            "done its trial.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** A highlighted line, e.g. when the quest can only be done at night or under a Blood Moon. */
@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}
