package gx.zelda.map.model

import gx.zelda.core.DiscoveryState
import gx.zelda.core.DiscoveryState.COMPLETED
import gx.zelda.core.DiscoveryState.FOUND
import gx.zelda.core.DiscoveryState.UNDISCOVERED
import gx.zelda.shrines.scan.WorldPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which markers the map shows, given its own filters and each tracker's spoiler setting. */
class MapLayerFilterTest {
    private fun layer(id: String, clustered: Boolean = false) = MapLayer(
        id = id,
        title = id,
        noun = id,
        stateLabels = linkedMapOf(UNDISCOVERED to "Undiscovered", FOUND to "Found", COMPLETED to "Completed"),
        kind = MarkerKind.KOROK,
        clustered = clustered,
    )

    private val shrines = layer("shrines")
    private val koroks = layer("koroks", clustered = true)

    private fun entry(id: String, state: DiscoveryState) = MapEntry(id, "Name $id", "", "Region", WorldPoint(0.0, 0.0), state)

    private fun data(layer: MapLayer, hide: Boolean) = LayerData(
        layer,
        listOf(entry("u", UNDISCOVERED), entry("f", FOUND), entry("c", COMPLETED)),
        hideUndiscovered = hide,
    )

    private fun List<MapMarker>.shown() = map { it.key + if (it.anonymous) "?" else "" }.toSet()

    @Test
    fun everythingShowsByDefault() {
        val markers = visibleMarkers(listOf(data(shrines, hide = false)), MapPrefs())
        assertEquals(setOf("shrines:u", "shrines:f", "shrines:c"), markers.shown())
    }

    @Test
    fun hiddenUndiscoveredAreLeftOff() {
        val markers = visibleMarkers(listOf(data(shrines, hide = true)), MapPrefs())
        assertEquals(setOf("shrines:f", "shrines:c"), markers.shown())
    }

    @Test
    fun hiddenUndiscoveredCanShowAsUnnamedDots() {
        val markers = visibleMarkers(listOf(data(shrines, hide = true)), MapPrefs(undiscoveredAsDots = true))
        assertEquals(setOf("shrines:u?", "shrines:f", "shrines:c"), markers.shown())
    }

    @Test
    fun dotsOptionChangesNothingWhenTheTrackerHidesNothing() {
        val markers = visibleMarkers(listOf(data(shrines, hide = false)), MapPrefs(undiscoveredAsDots = true))
        assertEquals(setOf("shrines:u", "shrines:f", "shrines:c"), markers.shown())
    }

    @Test
    fun revealedEntriesAreNamed() {
        val revealed = setOf("shrines:u")
        assertEquals(
            setOf("shrines:u", "shrines:f", "shrines:c"),
            visibleMarkers(listOf(data(shrines, hide = true)), MapPrefs(), revealed).shown(),
        )
    }

    @Test
    fun eachLayerFollowsItsOwnTrackersSetting() {
        val markers = visibleMarkers(listOf(data(shrines, hide = false), data(koroks, hide = true)), MapPrefs())
        assertEquals(
            setOf("shrines:u", "shrines:f", "shrines:c", "koroks:f", "koroks:c"),
            markers.shown(),
        )
    }

    @Test
    fun hideCompletedDropsCompletedOnly() {
        val markers = visibleMarkers(listOf(data(shrines, hide = true)), MapPrefs(hideCompleted = true, undiscoveredAsDots = true))
        assertEquals(setOf("shrines:u?", "shrines:f"), markers.shown())
    }

    @Test
    fun layersSwitchedOffShowNothing() {
        val markers = visibleMarkers(
            listOf(data(shrines, hide = false), data(koroks, hide = false)),
            MapPrefs(hiddenLayers = setOf("koroks")),
        )
        assertTrue(markers.all { it.layer.id == "shrines" })
        assertEquals(3, markers.size)
    }

    @Test
    fun pinnedEntryAlwaysShowsNamed() {
        val pinned = setOf("koroks:u", "koroks:c")
        val prefs = MapPrefs(hiddenLayers = setOf("koroks"), hideCompleted = true)
        val markers = visibleMarkers(listOf(data(koroks, hide = true)), prefs, pinned = pinned)
        assertEquals(setOf("koroks:u", "koroks:c"), markers.shown())
    }

    @Test
    fun anonymousOnlyForUndiscoveredUnderTheSpoilerSetting() {
        assertTrue(isAnonymous(entry("u", UNDISCOVERED), hideUndiscovered = true, revealed = false))
        assertEquals(false, isAnonymous(entry("u", UNDISCOVERED), hideUndiscovered = true, revealed = true))
        assertEquals(false, isAnonymous(entry("f", FOUND), hideUndiscovered = true, revealed = false))
        assertEquals(false, isAnonymous(entry("u", UNDISCOVERED), hideUndiscovered = false, revealed = false))
    }

    // Clustering: one clusterer per layer, and what a cluster shows.

    @Test
    fun onlyClusteredLayersHaveAClustererAndEachHasItsOwn() {
        assertNull(shrines.clustererId)
        assertEquals("cluster:koroks", koroks.clustererId)
        assertNotEquals(koroks.clustererId, layer("bosses", clustered = true).clustererId)
    }

    @Test
    fun clusterCountsRemainingAndTakesTheLeastDoneState() {
        val markers = listOf(FOUND, COMPLETED, COMPLETED).mapIndexed { i, s -> MapMarker(koroks, entry("$i", s), false) }
        assertEquals(ClusterSummary(total = 3, remaining = 1, state = FOUND), clusterSummary(markers))
    }

    @Test
    fun allCompletedClusterHasNothingRemaining() {
        val markers = List(4) { MapMarker(koroks, entry("$it", COMPLETED), false) }
        assertEquals(ClusterSummary(total = 4, remaining = 0, state = COMPLETED), clusterSummary(markers))
    }

    @Test
    fun anonymousMembersCountAsUndiscovered() {
        // An unnamed dot's real state is undiscovered anyway; the cluster must not hint otherwise.
        val markers = listOf(MapMarker(koroks, entry("a", UNDISCOVERED), true), MapMarker(koroks, entry("b", COMPLETED), false))
        assertEquals(ClusterSummary(total = 2, remaining = 1, state = UNDISCOVERED), clusterSummary(markers))
    }
}
