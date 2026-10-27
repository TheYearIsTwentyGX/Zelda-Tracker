package gx.zelda.map.data

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.shrines.scan.WorldPoint
import kotlinx.coroutines.test.runTest
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Echoes of Wisdom's map markers (`files/eow/map/markers.json`, written by
 * tools/eow-map/build_markers.py from tools/eow-map/matches.tsv) against its checklists: every item
 * of a mapped section has exactly one place on the map. docs/eow-map.md has the sources.
 */
class EowMapDatasetTest {
    private fun withMarkers(block: suspend (ChecklistMarkers) -> Unit) = runTest { block(ChecklistMarkers.load(EowMap.MARKERS)) }

    @Test
    fun eowHasAMapOfItsOwn() {
        assertTrue(Games.EOW.trackers.any { it.kind == TrackerKind.MAP })
        assertSame(MapSpecs.EOW, mapSpecFor(Games.EOW))
        assertSame(MapSpecs.BOTW, mapSpecFor(Games.BOTW))
        assertEquals(listOf("heart_pieces", "stamp_stands", "might_crystals"), checklistMapSources(Games.EOW).map { it.layerId })
        // Every source is one of the game's checklists.
        val files = Games.EOW.trackers.mapNotNull { it.dataFile }
        assertTrue(EowMap.sources.all { it.dataFile in files })
    }

    @Test
    fun everyItemOfAMappedSectionIsPlacedOnce() = withMarkers { markers ->
        assertEquals(emptyList(), markers.unplaced)
        for (source in EowMap.sources) {
            val catalog = ChecklistRepository.load(source.dataFile, strict = true)
            val expected = catalog.sections.filter { it.id in source.sections }.flatMap { s -> s.items.map { it.id } }
            val placed = markers.markers.filter { it.file == source.layerId }.map { it.item }
            assertEquals(expected, placed, "${source.layerId}, in file order")
            assertTrue(source.sections.keys.all { catalog.section(it) != null }, "${source.layerId}: ${source.sections.keys}")
        }
        assertEquals(EowMap.sources.map { it.layerId }.toSet(), markers.markers.map { it.file }.toSet())
    }

    @Test
    fun countsMatchTheGame() = withMarkers { markers ->
        assertEquals(mapOf("heart_pieces" to 47, "stamp_stands" to 25, "might_crystals" to 85), markers.markers.groupingBy { it.file }.eachCount())
        // 85 places hold all 150 Might Crystals; the script checks each pin's count against its item's value.
        val crystals = ChecklistRepository.load("eow/might_crystals.json").section("crystals")!!.items
        assertEquals(150, crystals.sumOf { it.value.roundToInt() })
    }

    @Test
    fun everyPinIsTheGamesOwnOnceAndOnTheMap() = withMarkers { markers ->
        val pins = markers.markers.filterNot { it.key.startsWith("HeartContainer") }
        assertEquals(pins.size, pins.map { it.key }.toSet().size, "a pin used twice")
        assertTrue(pins.all { Regex("HeartPiece \\d+|Piece\\d{3}|Stamp_\\d{2}").matches(it.key) }, "unknown key")
        for (m in markers.markers) {
            val at = EowMap.world.toMap(WorldPoint(m.x, m.z))
            assertTrue(at.x in 0.0..1.0 && at.y in 0.0..1.0, "${m.item} is off the map")
        }
    }

    @Test
    fun spotChecks() = withMarkers { markers ->
        val at = markers.markers.associate { it.item to WorldPoint(it.x, it.z) }
        // The game's first Piece of Heart pin, on Suthorn Beach.
        assertEquals(WorldPoint(216.0, 482.25), at["poh_suthorn_beach"])
        assertEquals("HeartPiece 0", markers.markers.first { it.item == "poh_suthorn_beach" }.key)
        // Both Slumber Dojo pieces are pinned on the dojo, in Kakariko.
        assertEquals(at["poh_slumber_dojo_two"], at["poh_slumber_dojo_eleven"])
        // A Heart Container is at its dungeon, where the boss's five Might Crystals are pinned.
        assertEquals(at["mc_suthorn_ruins"], at["hc_suthorn_ruins"])
        assertEquals(at["mc_hyrule_castle_dungeon"], at["hc_hyrule_castle"])
        // Pieces given in a building are pinned on it: Zelda Dungeon's landmarks for the Slumber Dojo and Condé's
        // House (Snowball Magic) are within 2 units.
        assertNear(WorldPoint(69.75, 197.6), at.getValue("poh_slumber_dojo_two"))
        assertNear(WorldPoint(442.5, 94.61), at.getValue("poh_snowball_magic"))
    }

    private fun assertNear(expected: WorldPoint, actual: WorldPoint) =
        assertTrue(hypot(expected.x - actual.x, expected.z - actual.z) < 2, "$actual isn't near $expected")
}
