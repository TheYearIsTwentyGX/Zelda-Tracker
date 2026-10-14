package gx.zelda.koroks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.Stepper
import gx.zelda.koroks.model.HestuPlan

/**
 * Seeds collected (Koroks ticked off), the inventory upgrades the user says they've bought from
 * Hestu, and what the seeds in hand pay for. Purchases are entered by hand; the app can't see the save.
 */
@Composable
fun HestuCard(plan: HestuPlan, onBoughtChange: (pouchId: String, upgrades: Int) -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hestu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${plan.collected} / ${plan.total} seeds",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                summary(plan),
                style = MaterialTheme.typography.bodyMedium,
                color = if (plan.isConsistent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            for (pouch in plan.costs.pouches) {
                val bought = plan.boughtIn(pouch)
                Stepper(
                    label = "${pouch.name} upgrades bought",
                    detail = buildString {
                        append("${plan.slots(pouch)} of ${pouch.maxSlots} slots")
                        append(plan.nextCost(pouch)?.let { " · next costs ${seedCount(it)}" } ?: " · maxed")
                    },
                    value = bought,
                    max = pouch.costs.size,
                    onValueChange = { onBoughtChange(pouch.id, it) },
                )
            }
            Text(
                "Maxing every pouch takes ${plan.costs.total} seeds; the other ${plan.total - plan.costs.total} " +
                    "only count toward Hestu's Gift for all ${plan.total}. Weapon slots leave out the extra one " +
                    "the Master Sword brings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun summary(plan: HestuPlan): String = when {
    !plan.isConsistent ->
        "That's more upgrades than ${seedCount(plan.collected)} pay for. Check the Koroks marked collected, or the numbers below."
    plan.isMaxed -> "Every pouch is maxed. ${seedCount(plan.inHand)} in hand, with nothing left to buy."
    plan.affordable > 0 ->
        "${seedCount(plan.inHand)} in hand: enough for ${upgradeCount(plan.affordable)}, cheapest first."
    else -> {
        val cheapest = plan.costs.pouches.mapNotNull(plan::nextCost).min()
        "${seedCount(plan.inHand)} in hand; the cheapest next upgrade costs ${seedCount(cheapest)}."
    }
}.let { line ->
    if (plan.isConsistent && !plan.isMaxed && plan.stillToCollect > 0) {
        "$line ${seedCount(plan.stillToCollect)} more to max every pouch."
    } else {
        line
    }
}

private fun seedCount(n: Int) = if (n == 1) "1 seed" else "$n seeds"

private fun upgradeCount(n: Int) = if (n == 1) "1 upgrade" else "$n upgrades"
