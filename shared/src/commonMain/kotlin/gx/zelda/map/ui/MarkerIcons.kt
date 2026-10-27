package gx.zelda.map.ui

import gx.zelda.art.ArtIds
import gx.zelda.map.model.MarkerIcon

/** The icon's asset id: `map_shrine`, bundled by scripts/build-map-icons.py, is `botw/marker/shrine`. */
val MarkerIcon.artId: String get() = ArtIds.marker("botw", resourceName.removePrefix("map_"))
