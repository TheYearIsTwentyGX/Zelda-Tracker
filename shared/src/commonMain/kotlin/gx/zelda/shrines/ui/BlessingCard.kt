package gx.zelda.shrines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gx.zelda.core.ui.Stepper
import gx.zelda.shrines.model.BlessingCurrency
import gx.zelda.shrines.model.OrbPlan
import gx.zelda.shrines.model.OrbUpgrades
import gx.zelda.shrines.model.UpgradeKind

/**
 * Spirit Orbs (BotW) or Lights of Blessing (TotK) earned from completed shrines, what the user says
 * they've bought with them, and what's still to come. Purchases are entered by hand; the app can't
 * see the save. In BotW, hearts also count the Divine Beasts freed in the Main Quest tracker; with
 * [bossHeartsByHand], the boss Heart Containers are entered here instead.
 */
@Composable
fun BlessingCard(
    plan: OrbPlan,
    bossHeartsByHand: Boolean,
    onBoughtChange: (hearts: Int, stamina: Int) -> Unit,
    onBossHeartsChange: (Int) -> Unit,
    onFreeUpgradeChange: (UpgradeKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val up = plan.upgrades
    val c = up.currency
    val words = Wording.of(c)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${plan.earned} / ${plan.total} earned",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                when {
                    !plan.isConsistent ->
                        "That's more upgrades than ${c.count(plan.earned)} pay for. Check the shrines marked complete, or the numbers below."
                    plan.readyToBuy > 0 ->
                        "${c.count(plan.inHand)} in hand: enough for ${upgradeCount(plan.readyToBuy)} at a Goddess Statue."
                    plan.unearned == 0 -> "Every ${c.one} is spent."
                    else -> "${c.count(plan.inHand)} in hand; ${plan.toNext} more for the next upgrade."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (plan.isConsistent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Stepper(
                label = "Heart Containers bought",
                detail = "${plan.hearts} of ${up.maxHearts} hearts, " +
                    (if (plan.beastHearts == 0) "none from ${words.bosses} yet" else "${plan.beastHearts} from ${words.bosses}") +
                    (if (plan.freeUpgrade == UpgradeKind.HEART) " and 1 free" else ""),
                value = plan.heartsBought,
                // Never below what's entered: an over-max number stays put and is flagged below.
                max = plan.maxHeartsBought,
                onValueChange = { onBoughtChange(it, plan.staminaBought) },
            )
            if (bossHeartsByHand) {
                Stepper(
                    label = "Heart Containers from bosses",
                    detail = words.bossHeartsDetail(up.bossHearts),
                    value = plan.beastHearts,
                    max = up.bossHearts,
                    onValueChange = onBossHeartsChange,
                )
            }
            up.freeUpgrade?.let { source ->
                FreeUpgradeChoice(source, plan.freeUpgrade, onFreeUpgradeChange)
            }
            if (plan.heartsOverMax) {
                Text(
                    "That makes ${plan.hearts} hearts, but the game stops at ${up.maxHearts}. With " +
                        "${words.bossCount(plan.heartsNotBought)}, Goddess Statues sell ${plan.maxHeartsBought} at most. " +
                        words.checkOverMax,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Stepper(
                label = "Stamina Vessels bought",
                detail = "${wheels(up, plan.staminaVessels)} of ${wheels(up, up.maxStaminaVessels)} stamina wheels" +
                    (if (plan.freeUpgrade == UpgradeKind.STAMINA) ", 1 vessel free" else ""),
                value = plan.staminaBought,
                max = plan.maxStaminaBought,
                onValueChange = { onBoughtChange(plan.heartsBought, it) },
            )
            Text(
                "${c.count(plan.unearned)} still in unfinished shrines; ${upgradeCount(plan.upgradesLeft)} left to buy in all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plan.upgradesToMaxBoth > plan.totalUpgrades) {
                Text(
                    "You can't max both. All ${c.count(plan.total)} buy ${plan.totalUpgrades} upgrades, but maxing hearts and stamina takes " +
                        "${plan.upgradesToMaxBoth}: ${up.maxHeartsBought - up.bossHearts} Heart Containers (the ${up.bossHearts} " +
                        "${words.bosses} give the rest) and ${up.maxStaminaVessels} Stamina Vessels" +
                        (up.freeUpgrade?.let { ", less the free one from $it" } ?: "") + ". ${words.swap}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Every ${up.orbsPerUpgrade} ${c.many} buy one upgrade. ${words.footnote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "A Call from the Depths": not yet claimed, or taken as a heart or a vessel. */
@Composable
private fun FreeUpgradeChoice(source: String, current: UpgradeKind?, onChange: (UpgradeKind?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Free upgrade from $source", style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(null to "Not yet", UpgradeKind.HEART to "Heart", UpgradeKind.STAMINA to "Stamina")
            options.forEachIndexed { i, (kind, label) ->
                SegmentedButton(
                    selected = current == kind,
                    onClick = { onChange(kind) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(label) },
                )
            }
        }
    }
}

/** What differs between the games' planners besides the currency's name. */
private class Wording(
    /** Where boss Heart Containers come from, in the plural. */
    val bosses: String,
    val bossCount: (Int) -> String,
    val bossHeartsDetail: (max: Int) -> String,
    /** Closes the over-max warning: what to check. */
    val checkOverMax: String,
    /** How to trade hearts for stamina and back. */
    val swap: String,
    val footnote: String,
) {
    companion object {
        fun of(currency: BlessingCurrency): Wording = when (currency) {
            BlessingCurrency.SPIRIT_ORB -> Wording(
                bosses = "Divine Beasts",
                bossCount = { if (it == 1) "1 Divine Beast freed" else "$it Divine Beasts freed" },
                bossHeartsDetail = { "One per Divine Beast freed, up to $it" },
                checkOverMax = "Check this number or Main Quest Progress.",
                swap = "The Horned Statue in Hateno Village swaps one for the other.",
                footnote = "Each base-game shrine gives one orb, and so do the Champions' Ballad's four Great " +
                    "Plateau shrines; its other twelve give an emblem instead. Each Divine Beast marked freed in " +
                    "Main Quest Progress adds a Heart Container.",
            )
            BlessingCurrency.LIGHT_OF_BLESSING -> Wording(
                bosses = "bosses",
                bossCount = { if (it == 1) "1 boss Heart Container" else "$it boss Heart Containers" },
                bossHeartsDetail = { "One each from the five temple bosses and Phantom Ganon at Hyrule Castle; $it in all" },
                checkOverMax = "Check these numbers.",
                swap = "The Horned Statue under Lookout Landing buys either back for 100 rupees and sells your " +
                    "choice for 120, so you can swap one for the other.",
                footnote = "Each shrine gives one Light of Blessing, and nothing else does: a Mysterious Crystal " +
                    "only reveals a hidden shrine, and Crystallized Charges are Zonai currency for the Crystal " +
                    "Refineries. Gloom only takes hearts until they recover; it never lowers the maximum.",
            )
        }
    }
}

private fun upgradeCount(n: Int) = if (n == 1) "1 upgrade" else "$n upgrades"

/** Stamina wheels after [vessels]: 1 + 7 vessels → "2⅖". */
private fun wheels(up: OrbUpgrades, vessels: Int): String {
    val whole = up.startingStaminaWheels + vessels / OrbUpgrades.VESSELS_PER_WHEEL
    return "$whole" + listOf("", "⅕", "⅖", "⅗", "⅘")[vessels % OrbUpgrades.VESSELS_PER_WHEEL]
}
