package gx.zelda.map.ui

import gx.zelda.art.ArtIds
import gx.zelda.map.model.MarkerIcon

/**
 * The icon's asset id. Bundled by scripts/build-map-icons.py as `map_<name>` (`botw/marker/<name>`)
 * or `map_eow_<name>` (`eow/marker/<name>`).
 */
val MarkerIcon.artId: String
    get() = if (resourceName.startsWith("map_eow_")) ArtIds.marker("eow", resourceName.removePrefix("map_eow_"))
    else ArtIds.marker("botw", resourceName.removePrefix("map_"))
