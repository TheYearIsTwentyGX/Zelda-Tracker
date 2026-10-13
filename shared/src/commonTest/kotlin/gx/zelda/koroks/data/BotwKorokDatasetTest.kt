package gx.zelda.koroks.data

import gx.zelda.core.Games
import gx.zelda.koroks.model.KorokCatalog
import gx.zelda.koroks.model.KorokPuzzleTypes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled BotW koroks.json and korok_puzzle_types.json. */
class BotwKorokDatasetTest {
    private fun withData(block: (KorokCatalog, KorokPuzzleTypes) -> Unit) = runTest {
        block(KorokRepository.load(Games.BOTW), KorokRepository.loadPuzzleTypes(Games.BOTW))
    }

    @Test
    fun hasEveryKorok() = withData { c, _ ->
        assertEquals(900, c.koroks.size)
    }

    @Test
    fun idsAreUnique() = withData { c, _ ->
        listOf(c.koroks.map { it.id }, c.koroks.map { it.hashId }).forEach {
            assertEquals(it.size, it.toSet().size, "duplicates: ${it.groupBy { v -> v }.filter { e -> e.value.size > 1 }.keys}")
        }
        c.koroks.forEach { assertTrue(Regex("[ACDEFGHKLNPRTWXZ][0-9]{2,3}").matches(it.id), "bad id ${it.id}") }
    }

    @Test
    fun everyRegionIsATowerRegion() = withData { c, _ ->
        assertEquals(
            listOf(
                "Great Plateau", "Dueling Peaks", "Hateno", "Lanayru", "Faron", "Lake", "Central", "Ridgeland",
                "Tabantha", "Hebra", "Woodland", "Eldin", "Akkala", "Wasteland", "Gerudo",
            ),
            c.regions,
        )
        c.koroks.forEach { assertTrue(it.region in c.regions, "${it.id}: unknown region ${it.region}") }
    }

    /** Seeds per tower region, as Game8 and objmap group them (Hyrule Castle counts as Central). */
    @Test
    fun koroksPerRegion() = withData { c, _ ->
        assertEquals(
            mapOf(
                "Great Plateau" to 18, "Dueling Peaks" to 59, "Hateno" to 66, "Lanayru" to 62, "Faron" to 58,
                "Lake" to 92, "Central" to 114, "Ridgeland" to 80, "Tabantha" to 37, "Hebra" to 73,
                "Woodland" to 35, "Eldin" to 45, "Akkala" to 57, "Wasteland" to 68, "Gerudo" to 36,
            ),
            c.koroks.groupingBy { it.region }.eachCount(),
        )
    }

    @Test
    fun everyKorokIsOnTheMap() = withData { c, _ ->
        for (k in c.koroks) {
            assertTrue(k.x in -5000.0..5000.0 && k.z in -4000.0..4000.0, "${k.id} is off the map: ${k.x}, ${k.z}")
            assertTrue(k.y in -100.0..1200.0, "${k.id} has an odd height: ${k.y}")
            listOf(k.mapSection, k.nearestPlace, k.internalType).forEach { assertTrue(it.isNotBlank(), "${k.id} has a blank field") }
        }
    }

    @Test
    fun puzzleTypesAreWellFormed() = withData { c, t ->
        val ids = t.types.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate puzzle type ids")
        assertTrue(ids.size in 15..30, "${ids.size} puzzle types")
        assertTrue("other" in ids)
        val korokIds = c.koroks.map { it.id }.toSet()
        for (type in t.types) {
            assertTrue(Regex("[a-z]+(_[a-z]+)*").matches(type.id), "bad id ${type.id}")
            assertTrue(type.group in t.groups, "${type.id}: unknown group ${type.group}")
            listOf(type.name, type.description).forEach { assertTrue(it.isNotBlank(), "${type.id} has a blank field") }
            assertTrue(type.rules.isNotEmpty() && type.rules.all { it.isNotBlank() }, "${type.id} has no rules")
            assertTrue(type.examples.size in 1..2, "${type.id} needs one or two examples")
            type.examples.forEach { assertTrue(it in korokIds, "${type.id}: unknown example $it") }
        }
    }

    @Test
    fun classificationsUseKnownTypes() = withData { c, t ->
        val ids = t.types.map { it.id }.toSet()
        for (k in c.koroks) {
            if (k.puzzleType == null) {
                assertNull(k.typeSource, "${k.id} has a source but no type")
            } else {
                assertTrue(k.puzzleType in ids, "${k.id}: unknown puzzle type ${k.puzzleType}")
                assertNotNull(k.typeSource, "${k.id} has a type but no source")
            }
        }
        // Every example shows its own type once classified.
        val byId = c.koroks.associateBy { it.id }
        for (type in t.types) {
            type.examples.forEach { ex -> byId.getValue(ex).puzzleType?.let { assertEquals(type.id, it, "example $ex") } }
        }
    }

    @Test
    fun spotChecks() = withData { c, _ ->
        fun korok(id: String) = c.koroks.first { it.id == id }
        with(korok("Z07")) {
            assertEquals("Lanayru", region)
            assertEquals("Rock Lift", internalType)
            assertEquals("lift_rock", puzzleType)
        }
        assertEquals("Central", korok("X25").region, "Hyrule Castle is in the Central tower region")
        assertEquals("rock_behind_cracked_wall", korok("F18").puzzleType)
        assertNull(korok("L26").puzzleType, "one-off puzzles are left for review")
    }
}
