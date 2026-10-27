package gx.zelda.armor.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import gx.zelda.armor.model.ArmorPiece
import gx.zelda.art.ArtIds
import gx.zelda.art.ArtImage

/**
 * [piece]'s inventory icon in [gameId] (`<game>/armor/<id>`, bundled as `drawable/armor_<id>.png`
 * by scripts/fetch-armor-icons.py) at [size], dimmed when [dimmed]. Decorative: the name is always
 * shown beside it. A piece without an icon keeps the same footprint so rows stay aligned.
 */
@Composable
fun ArmorIcon(piece: ArmorPiece, gameId: String, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    ArtImage(ArtIds.armor(gameId, piece.id), contentDescription = null, modifier = modifier.size(size), alpha = if (dimmed) 0.45f else 1f)
}
