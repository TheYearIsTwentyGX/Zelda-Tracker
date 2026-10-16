package gx.zelda.map.ui

import gx.zelda.map.model.MarkerIcon
import gx.zelda.resources.Res
import gx.zelda.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource

/** The icon's drawable, bundled as `drawable/<resourceName>` by scripts/build-map-icons.py. */
internal fun markerDrawable(icon: MarkerIcon): DrawableResource? = Res.allDrawableResources[icon.resourceName]
