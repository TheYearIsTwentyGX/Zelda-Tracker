package gx.zelda.map.model

import gx.zelda.core.DiscoveryState
import gx.zelda.core.DiscoveryState.COMPLETED
import gx.zelda.core.DiscoveryState.FOUND
import gx.zelda.core.DiscoveryState.UNDISCOVERED
import gx.zelda.map.model.MarkerIcon.DLC_SHRINE
import gx.zelda.map.model.MarkerIcon.DLC_SHRINE_DONE
import gx.zelda.map.model.MarkerIcon.HINOX
import gx.zelda.map.model.MarkerIcon.KOROK
import gx.zelda.map.model.MarkerIcon.LYNEL
import gx.zelda.map.model.MarkerIcon.MOLDUGA
import gx.zelda.map.model.MarkerIcon.SHRINE
import gx.zelda.map.model.MarkerIcon.SHRINE_DONE
import gx.zelda.map.model.MarkerIcon.TALUS
import gx.zelda.map.model.MarkerIcon.TOWER
import gx.zelda.map.model.MarkerIcon.TOWER_ACTIVE
import gx.zelda.shrines.scan.WorldPoint
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which in-game icon each marker draws, and how its state shows. */
class MarkerLookTest {
    private val threeStates = linkedMapOf(UNDISCOVERED to "Undiscovered", FOUND to "Found", COMPLETED to "Completed")
    private val shrines = MapLayer("shrines", "Shrines", "shrine", threeStates, MarkerKind.SHRINE)
    private val koroks = MapLayer("koroks", "Koroks", "Korok", threeStates, MarkerKind.KOROK, clustered = true)
    private val towers = MapLayer(
        "towers", "Towers", "tower", linkedMapOf(UNDISCOVERED to "Not activated", COMPLETED to "Activated"), MarkerKind.TOWER,
    )

    private val bosses = MapLayer(
        "bosses", "Bosses", "boss", linkedMapOf(UNDISCOVERED to "Not found", FOUND to "Found", COMPLETED to "Defeated"),
        MarkerKind.BOSS,
    )

    private fun marker(
        layer: MapLayer,
        state: DiscoveryState,
        dlc: Boolean = false,
        anonymous: Boolean = false,
        icon: MarkerIcon? = null,
    ) = MapMarker(layer, MapEntry("id", "Name", "", "Region", WorldPoint(0.0, 0.0), state, dlc, icon), anonymous)

    private fun look(
        layer: MapLayer,
        state: DiscoveryState,
        dlc: Boolean = false,
        anonymous: Boolean = false,
        icon: MarkerIcon? = null,
    ) = markerLook(marker(layer, state, dlc, anonymous, icon))

    @Test
    fun shrineGlyphTurnsBlueWhenCompleted() {
        assertEquals(MarkerLook(SHRINE, UNDISCOVERED, greyed = true, disc = false), look(shrines, UNDISCOVERED))
        assertEquals(MarkerLook(SHRINE, FOUND, greyed = false, disc = false), look(shrines, FOUND))
        assertEquals(MarkerLook(SHRINE_DONE, COMPLETED, greyed = false, disc = false), look(shrines, COMPLETED))
    }

    @Test
    fun championsBalladShrinesHaveTheirOwnIcon() {
        assertEquals(DLC_SHRINE, look(shrines, FOUND, dlc = true).icon)
        assertEquals(DLC_SHRINE_DONE, look(shrines, COMPLETED, dlc = true).icon)
    }

    @Test
    fun anAnonymousMarkerGivesNothingAway() {
        // Even completed or DLC: the entry's state and DLC flag are hidden along with its name.
        val hidden = look(shrines, COMPLETED, dlc = true, anonymous = true)
        assertEquals(MarkerLook(SHRINE, UNDISCOVERED, greyed = true, disc = false), hidden)
        assertEquals(MarkerLook(KOROK, UNDISCOVERED, greyed = true, disc = true), look(koroks, FOUND, anonymous = true))
    }

    @Test
    fun towersAreOrangeUntilActivatedAndNeverGreyed() {
        assertEquals(MarkerLook(TOWER, UNDISCOVERED, greyed = false, disc = false), look(towers, UNDISCOVERED))
        assertEquals(MarkerLook(TOWER_ACTIVE, COMPLETED, greyed = false, disc = false), look(towers, COMPLETED))
    }

    @Test
    fun koroksKeepTheSeedAndShowTheirStateOnTheDisc() {
        for (state in DiscoveryState.entries) {
            val look = look(koroks, state)
            assertEquals(KOROK, look.icon)
            assertEquals(state, look.state)
            assertTrue(look.disc)
            assertEquals(state == UNDISCOVERED, look.greyed, "$state")
        }
    }

    @Test
    fun bossesShowTheirKindsIconOnAStateDisc() {
        assertEquals(listOf(HINOX, TALUS, MOLDUGA, LYNEL), listOf("hinox", "talus", "molduga", "lynel").map(::bossIcon))
        for (icon in listOf(HINOX, TALUS, MOLDUGA, LYNEL)) {
            for (state in DiscoveryState.entries) {
                assertEquals(MarkerLook(icon, state, greyed = state == UNDISCOVERED, disc = true), look(bosses, state, icon = icon))
            }
        }
        // The DLC Molduking is a Molduga like any other.
        assertEquals(MOLDUGA, look(bosses, FOUND, dlc = true, icon = MOLDUGA).icon)
    }

    /** The boss tracker's hidden rows name the kind ("Unfound Talus"), so a hidden boss keeps its kind's icon. */
    @Test
    fun aHiddenBossKeepsItsKindButNotItsState() {
        assertEquals(MarkerLook(TALUS, UNDISCOVERED, greyed = true, disc = true), look(bosses, COMPLETED, anonymous = true, icon = TALUS))
    }

    @Test
    fun bossesStopShrinkingAtThreeQuarters() {
        assertEquals(0.75f, layerIconScale(MarkerKind.BOSS, iconScale(0.0)))
        assertEquals(0.75f, layerIconScale(MarkerKind.BOSS, iconScale(0.25)))
        assertEquals(1f, layerIconScale(MarkerKind.BOSS, iconScale(0.5)))
        // Other layers keep the plain zoom scale.
        assertEquals(0.5f, layerIconScale(MarkerKind.KOROK, iconScale(0.0)))
        assertEquals(0.5f, layerIconScale(MarkerKind.SHRINE, iconScale(0.0)))
    }

    @Test
    fun everyStateLooksDifferentWithinALayer() {
        for (layer in listOf(shrines, koroks, towers, bosses)) {
            val looks = layer.states.map { look(layer, it) }
            assertEquals(looks.size, looks.toSet().size, "${layer.id}: $looks")
        }
    }

    @Test
    fun markersGrowFromHalfToFullSizeAsTheMapZoomsIn() {
        assertEquals(0.5f, iconScale(0.0))
        assertEquals(0.5f, iconScale(0.1))
        assertEquals(0.5f, iconScale(0.125))
        assertEquals(0.75f, iconScale(0.25))
        assertEquals(1f, iconScale(0.5))
        assertEquals(1f, iconScale(2.5))
        val steps = (0..250).map { iconScale(it / 100.0) }
        assertEquals(steps, steps.sorted(), "never shrinks while zooming in")
        steps.forEach { assertEquals((it * 20).roundToInt() / 20f, it, "a 0.05 step") }
    }
}
