package gx.zelda.mainquest.data

import gx.zelda.core.Games
import gx.zelda.mainquest.model.MainQuestCatalog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled BotW main_quest.json. */
class BotwMainQuestDatasetTest {
    private fun withCatalog(block: (MainQuestCatalog) -> Unit) = runTest { block(MainQuestRepository.load(Games.BOTW)) }

    @Test
    fun hasAllFifteenTowers() = withCatalog { c ->
        assertEquals(15, c.towers.size)
        assertEquals(c.towers.size, c.towers.map { it.id }.toSet().size, "tower ids are unique")
        assertEquals(c.towers.size, c.towers.map { it.name }.toSet().size, "tower names are unique")
        assertTrue(c.towers.all { it.name.endsWith(" Tower") })
        assertEquals("great_plateau", c.towers.first().id, "the first tower comes first")
        for (tower in c.towers) {
            listOf(tower.id, tower.name, tower.region, tower.location, tower.tip).forEach {
                assertTrue(it.isNotBlank(), "${tower.id} has a blank field")
            }
        }
    }

    @Test
    fun hasTheFourDivineBeasts() = withCatalog { c ->
        assertEquals(listOf("vah_ruta", "vah_rudania", "vah_medoh", "vah_naboris"), c.divineBeasts.map { it.id })
        assertEquals(listOf("Mipha", "Daruk", "Revali", "Urbosa"), c.divineBeasts.map { it.champion })
        assertEquals(
            listOf("Waterblight Ganon", "Fireblight Ganon", "Windblight Ganon", "Thunderblight Ganon"),
            c.divineBeasts.map { it.boss },
        )
        assertEquals(
            listOf("Mipha's Grace", "Daruk's Protection", "Revali's Gale", "Urbosa's Fury"),
            c.divineBeasts.map { it.ability },
        )
    }

    @Test
    fun everyBeastIsFullyDescribed() = withCatalog { c ->
        for (beast in c.divineBeasts) {
            listOf(beast.name, beast.race, beast.region, beast.location, beast.start, beast.abilityEffect).forEach {
                assertTrue(it.isNotBlank(), "${beast.id} has a blank field")
            }
            assertEquals("Divine Beast ${beast.name}", beast.quest, "${beast.id}'s main quest is named after it")
            assertTrue(beast.prep.isNotEmpty() && beast.prep.all { it.isNotBlank() }, "${beast.id} has no prep")
            assertEquals(beast.ability, beast.rewards.first(), "${beast.id} lists its Champion's ability first")
            assertTrue("Heart Container" in beast.rewards, "${beast.id}'s Blight drops a Heart Container")
        }
    }

    @Test
    fun hasAllEighteenMemoriesInAdventureLogOrder() = withCatalog { c ->
        assertEquals((1..18).toList(), c.memories.map { it.number })
        assertEquals(c.memories.size, c.memories.map { it.id }.toSet().size, "memory ids are unique")
        assertEquals(c.memories.size, c.memories.map { it.title }.toSet().size, "memory titles are unique")
        for (memory in c.memories) {
            listOf(memory.title, memory.location, memory.hint).forEach {
                assertTrue(it.isNotBlank(), "memory ${memory.number} has a blank field")
            }
        }
    }

    @Test
    fun capturedMemoriesAreTheTwelvePicturesPlusTheFinalOne() = withCatalog { c ->
        assertEquals(12, c.albumSize)
        assertEquals((1..12).toList(), c.memories.mapNotNull { it.photo }, "album pictures run 1–12 in log order")
        val final = c.memories.single { it.finalPicture }
        assertEquals(17, final.number)
        assertEquals("Zelda's Awakening", final.title)
        assertNull(final.photo)
        assertEquals(13, c.memories.count { it.isCaptured })
    }

    @Test
    fun theOtherFiveMemoriesComeFromQuests() = withCatalog { c ->
        val fromQuests = c.memories.filter { !it.isCaptured }
        assertEquals(listOf(2, 4, 6, 10, 18), fromQuests.map { it.number })
        assertEquals(
            c.divineBeasts.map { it.quest }.toSet() + "The Hero's Sword",
            fromQuests.map { it.quest }.toSet(),
            "one per Divine Beast, plus the Master Sword",
        )
        assertTrue(c.memories.filter { it.isCaptured }.all { it.quest == null }, "picture memories need no quest")
    }
}
