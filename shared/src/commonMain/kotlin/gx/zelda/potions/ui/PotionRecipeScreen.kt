package gx.zelda.potions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gx.zelda.armor.model.MaterialCost
import gx.zelda.core.ui.Tag
import gx.zelda.potions.model.RecipeStep
import gx.zelda.theme.LocalGameDecor
import gx.zelda.upgrades.logic.thousands
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.ui.MaterialIcon
import gx.zelda.upgrades.ui.UpgradeItemIcon

/**
 * One potion's recipe: the base potion from Luv, then each of Bertie's infusions, each with its
 * bugs, Rupees and a box for "made"; then what's still needed and where to catch each bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotionRecipeScreen(viewModel: PotionViewModel, potionId: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val potion = s.potion(potionId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(potion?.name ?: "Unknown potion", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.saveFile.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (potion == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This potion isn't in the book.")
            }
            return@Scaffold
        }
        val recipe = s.recipe(potion)
        val needs = s.needs(potion)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UpgradeItemIcon(potion, potion.maxLevel, size = 72.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(potion.levelName(potion.maxLevel), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val made = recipe.count { it.made }
                    StepDots(made, recipe.size)
                    Text(
                        if (made == recipe.size) "Fully infused" else "$made of ${recipe.size} steps made",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (made == recipe.size) LocalGameDecor.current.star else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            recipe.forEachIndexed { i, step ->
                if (i > 0) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                StepCard(s.catalog, step, onMade = { viewModel.setStepMade(potion.id, step.index, it) })
            }

            InfoCard(title = "Still needed") {
                if (needs.isEmpty) {
                    Text("Nothing: you've made every step.", style = MaterialTheme.typography.bodyMedium, color = LocalGameDecor.current.star)
                } else {
                    Text(
                        "${thousands(needs.rupees)} Rupees" + if (needs.materialCount > 0) " and ${needs.materialCount} bugs" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    MaterialChips(s.catalog, needs.materials)
                }
            }

            val materials = recipe.flatMap { it.materials }.map { it.material }.distinct().map(s.catalog::material)
            if (materials.isNotEmpty()) {
                InfoCard(title = "Where to catch them") {
                    materials.forEachIndexed { i, material ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MaterialIcon(material, size = 36.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(material.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                if (material.regions.isNotEmpty()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        material.regions.forEach { Tag(it) }
                                    }
                                }
                                Text(material.farm, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            potion.notes?.let { InfoCard(title = "Notes") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}

/** One step: its form and effect, who makes it and for what, and a whole-card box for "made". */
@Composable
private fun StepCard(catalog: UpgradeCatalog, step: RecipeStep, onMade: (Boolean) -> Unit) {
    val label = if (step.isBase) "Bought" else "Infused"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (step.made) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(value = step.made, role = Role.Checkbox, onValueChange = onMade),
            ) {
                UpgradeItemIcon(step.potion, step.index, size = 48.dp, dimmed = !step.made, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    step.effect?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(checked = step.made, onCheckedChange = null)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tag(step.by)
                Tag(
                    step.rupees?.let { "${thousands(it)} Rupees" } ?: "Not sold",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (step.materials.isNotEmpty()) MaterialChips(catalog, step.materials)
            step.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Each material as its icon, quantity and name. */
@Composable
private fun MaterialChips(catalog: UpgradeCatalog, materials: List<MaterialCost>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        materials.forEach { cost ->
            val material = catalog.material(cost.material)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                MaterialIcon(material, size = 28.dp, modifier = Modifier.padding(end = 6.dp))
                Text("×${cost.qty}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(" ${material.name}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
