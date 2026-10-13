package gx.zelda.botw.armor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import gx.zelda.botw.armor.model.ArmorPiece
import gx.zelda.resources.Res
import gx.zelda.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The piece's inventory icon, bundled as `drawable/armor_<id>.png` by scripts/fetch-armor-icons.py. */
fun armorIcon(pieceId: String): DrawableResource? = Res.allDrawableResources["armor_$pieceId"]

/**
 * [piece]'s icon at [size], dimmed when [dimmed]. Decorative: the name is always shown beside it.
 * A piece without an icon keeps the same footprint so rows stay aligned.
 */
@Composable
fun ArmorIcon(piece: ArmorPiece, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val icon = armorIcon(piece.id)
    val sized = modifier.size(size)
    if (icon == null) {
        Box(sized)
    } else {
        Image(
            painterResource(icon),
            contentDescription = null,
            modifier = sized.alpha(if (dimmed) 0.45f else 1f),
        )
    }
}
