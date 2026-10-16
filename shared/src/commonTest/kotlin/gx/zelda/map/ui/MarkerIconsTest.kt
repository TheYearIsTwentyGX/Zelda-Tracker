package gx.zelda.map.ui

import gx.zelda.map.model.MarkerIcon
import kotlin.test.Test
import kotlin.test.assertTrue

class MarkerIconsTest {
    @Test
    fun everyMarkerIconIsBundled() {
        val missing = MarkerIcon.entries.filter { markerDrawable(it) == null }
        assertTrue(missing.isEmpty(), "no drawable for $missing; run scripts/build-map-icons.py")
    }
}
