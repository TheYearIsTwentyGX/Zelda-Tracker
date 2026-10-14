package gx.zelda.shrines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gx.zelda.shrines.model.OrbPlan
import gx.zelda.shrines.model.OrbUpgrades

/**
 * Spirit Orbs earned from completed shrines, what the user says they've bought with them, and
 * what's still to come. Purchases are entered by hand; the app can't see the save.
 */
@Composable
fun SpiritOrbCard(plan: OrbPlan, onBoughtChange: (hearts: Int, stamina: Int) -> Unit, modifier: Modifier = Modifier) {
    val up = plan.upgrades
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spirit Orbs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${plan.earned} / ${plan.total} earned",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                when {
                    !plan.isConsistent ->
                        "That's more upgrades than ${orbCount(plan.earned)} pay for. Check the shrines marked complete, or the numbers below."
                    plan.readyToBuy > 0 ->
                        "${orbCount(plan.inHand)} in hand: enough for ${upgradeCount(plan.readyToBuy)} at a Goddess Statue."
                    plan.unearned == 0 -> "Every orb is spent."
                    else -> "${orbCount(plan.inHand)} in hand; ${plan.toNext} more for the next upgrade."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (plan.isConsistent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Stepper(
                label = "Heart Containers bought",
                detail = "${up.startingHearts + plan.heartsBought} hearts, plus any from Divine Beasts",
                value = plan.heartsBought,
                max = up.maxHeartsBought,
                onValueChange = { onBoughtChange(it, plan.staminaBought) },
            )
            Stepper(
                label = "Stamina Vessels bought",
                detail = "${wheels(up, plan.staminaBought)} of ${wheels(up, up.maxStaminaVessels)} stamina wheels",
                value = plan.staminaBought,
                max = up.maxStaminaVessels,
                onValueChange = { onBoughtChange(plan.heartsBought, it) },
            )
            Text(
                "${orbCount(plan.unearned)} still in unfinished shrines; ${upgradeCount(plan.upgradesLeft)} left to buy in all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plan.upgradesToMaxBoth > plan.totalUpgrades) {
                Text(
                    "All ${plan.total} orbs buy ${plan.totalUpgrades} upgrades, but maxing hearts and stamina takes " +
                        "${plan.upgradesToMaxBoth}: ${up.maxHeartsBought - up.bossHearts} Heart Containers (the ${up.bossHearts} " +
                        "Divine Beasts give the rest) and ${up.maxStaminaVessels} Stamina Vessels. The Horned Statue in " +
                        "Hateno Village swaps one for the other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Every ${up.orbsPerUpgrade} orbs buy one upgrade. Each base-game shrine gives one orb, and so do " +
                    "the Champions' Ballad's four Great Plateau shrines; its other twelve give an emblem instead. " +
                    "Divine Beast Heart Containers aren't tracked here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "−  n  +" with a label; the buttons stop at 0 and [max]. */
@Composable
private fun Stepper(label: String, detail: String, value: Int, max: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { onValueChange(value - 1) }, enabled = value > 0) {
            Text("−", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "Fewer" })
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp).semantics { contentDescription = "$label: $value of $max" },
        )
        FilledTonalIconButton(onClick = { onValueChange(value + 1) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "More" })
        }
    }
}

private fun upgradeCount(n: Int) = if (n == 1) "1 upgrade" else "$n upgrades"

private fun orbCount(n: Int) = if (n == 1) "1 orb" else "$n orbs"

/** Stamina wheels after [vessels]: 1 + 7 vessels → "2⅖". */
private fun wheels(up: OrbUpgrades, vessels: Int): String {
    val whole = up.startingStaminaWheels + vessels / OrbUpgrades.VESSELS_PER_WHEEL
    return "$whole" + listOf("", "⅕", "⅖", "⅗", "⅘")[vessels % OrbUpgrades.VESSELS_PER_WHEEL]
}
