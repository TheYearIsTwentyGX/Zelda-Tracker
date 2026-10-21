package gx.zelda.upgrades.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import gx.zelda.resources.Res
import gx.zelda.resources.allDrawableResources
import gx.zelda.upgrades.model.UpgradeItem
import gx.zelda.upgrades.model.UpgradeMaterial
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/*
 * In-game icons from Zelda Wiki, bundled by scripts/fetch-ss-upgrade-icons.py: each level of an item
 * as `drawable/ss_up_<level id>.png`, each treasure and bug as `drawable/ss_mat_<material id>.png`.
 */

fun levelIcon(levelId: String): DrawableResource? = Res.allDrawableResources["ss_up_$levelId"]

fun materialIcon(materialId: String): DrawableResource? = Res.allDrawableResources["ss_mat_$materialId"]

/** [item]'s icon at [level], dimmed when [dimmed]. Decorative: the name is always beside it. */
@Composable
fun UpgradeItemIcon(item: UpgradeItem, level: Int, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) =
    IconOrSpace(levelIcon(item.levels[level.coerceIn(0, item.maxLevel)].id), size, modifier, dimmed)

@Composable
fun MaterialIcon(material: UpgradeMaterial, size: Dp, modifier: Modifier = Modifier) =
    IconOrSpace(materialIcon(material.id), size, modifier, dimmed = false)

/** A missing icon keeps the same footprint, so rows stay aligned. */
@Composable
private fun IconOrSpace(icon: DrawableResource?, size: Dp, modifier: Modifier, dimmed: Boolean) {
    val sized = modifier.size(size)
    if (icon == null) {
        Box(sized)
    } else {
        Image(painterResource(icon), contentDescription = null, modifier = sized.alpha(if (dimmed) 0.45f else 1f))
    }
}
