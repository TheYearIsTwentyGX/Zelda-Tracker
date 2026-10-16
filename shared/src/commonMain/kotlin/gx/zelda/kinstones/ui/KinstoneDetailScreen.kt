package gx.zelda.kinstones.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One fusion: who to fuse with and where, the piece, when it becomes possible and what it needs
 * first, and what it does. With results hidden, the result stays hidden until revealed or fused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinstoneDetailScreen(state: KinstoneUiState, fusionId: String, viewModel: KinstoneViewModel, onBack: () -> Unit) {
    val fusion = state.catalog.fusion(fusionId) ?: return
    val fused = state.progress.isFused(fusion.id)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fusion.partner, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${fusion.pieceName} Kinstone · ${state.saveFile.label}",
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
            FilterChip(
                selected = fused,
                onClick = { viewModel.setFused(fusion.id, !fused) },
                label = { Text(if (fused) "Fused" else "Not fused") },
            )

            fusion.missable?.let { Note(it) }

            Section(if (fusion.random) "Who" else "Fuse with") {
                Text(fusion.partner, style = MaterialTheme.typography.bodyLarge)
                Text(fusion.location, style = MaterialTheme.typography.bodyMedium)
                Text(
                    fusion.region,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Kinstone Piece") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(fusion.color)
                    Text(fusion.pieceName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
                if (fusion.isGold) {
                    Text(
                        "A Gold piece: a story item, given where it's needed rather than found at random.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Section("When") {
                Text("${state.catalog.stageLabel(fusion)} (stage ${fusion.stage} of ${state.catalog.stages.size})", style = MaterialTheme.typography.bodyLarge)
                fusion.requires?.let { Text("First: $it", style = MaterialTheme.typography.bodyMedium) }
            }

            Section("Result") {
                if (state.isHidden(fusion)) {
                    Text("Hidden until fused.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { viewModel.reveal(fusion.id) }) { Text("Reveal") }
                } else {
                    Text(fusion.result, style = MaterialTheme.typography.bodyLarge)
                    fusion.resultRegion?.let {
                        Text(
                            "Appears in $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        fusion.rewards.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    fusion.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
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

/** A highlighted line: how the fusion can be missed. */
@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}
