package gx.zelda.upgrades.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import gx.zelda.art.ArtIds
import gx.zelda.art.ArtImage
import gx.zelda.upgrades.model.UpgradeItem
import gx.zelda.upgrades.model.UpgradeMaterial

/*
 * In-game icons from Zelda Wiki, bundled by scripts/fetch-ss-upgrade-icons.py: each level of an item
 * as `drawable/ss_up_<level id>.png` (`ss/upgrade/<level id>`), each treasure and bug as
 * `drawable/ss_mat_<material id>.png` (`ss/material/<material id>`).
 */

/** [item]'s icon at [level], dimmed when [dimmed]. Decorative: the name is always beside it. */
@Composable
fun UpgradeItemIcon(item: UpgradeItem, level: Int, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) =
    IconOrSpace(ArtIds.ssUpgrade(item.levels[level.coerceIn(0, item.maxLevel)].id), size, modifier, dimmed)

@Composable
fun MaterialIcon(material: UpgradeMaterial, size: Dp, modifier: Modifier = Modifier) =
    IconOrSpace(ArtIds.ssMaterial(material.id), size, modifier, dimmed = false)

/** A missing icon keeps the same footprint, so rows stay aligned. */
@Composable
private fun IconOrSpace(id: String, size: Dp, modifier: Modifier, dimmed: Boolean) {
    ArtImage(id, contentDescription = null, modifier = modifier.size(size), alpha = if (dimmed) 0.45f else 1f)
}
