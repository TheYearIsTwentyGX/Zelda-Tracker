package gx.zelda.sidequests.data

import gx.zelda.armor.data.ArmorRepository
import gx.zelda.core.Games
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.sidequests.model.QuestKind
import gx.zelda.sidequests.model.SideQuestCatalog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled BotW side_quests.json, and its links to shrines.json and armor.json. */
class BotwSideQuestDatasetTest {
    private fun withCatalog(block: suspend (SideQuestCatalog) -> Unit) = runTest { block(SideQuestRepository.load(Games.BOTW)) }

    @Test
    fun hasEveryQuest() = withCatalog { c ->
        assertEquals(76, c.ofKind(QuestKind.SIDE).size, "base-game side quests")
        assertEquals(42, c.ofKind(QuestKind.SHRINE).size, "Shrine Quests")
        assertEquals(DLC_QUESTS, c.ofKind(QuestKind.DLC).size, "the DLC's EX side quests")
    }

    @Test
    fun idsAndNamesAreUnique() = withCatalog { c ->
        listOf(c.quests.map { it.id }, c.quests.map { it.name }).forEach {
            assertEquals(it.size, it.toSet().size, "duplicates: ${it.groupBy { v -> v }.filter { e -> e.value.size > 1 }.keys}")
        }
        c.quests.forEach { assertTrue(Regex("[a-z0-9]+(_[a-z0-9]+)*").matches(it.id), "bad id ${it.id}") }
    }

    @Test
    fun regionsAreTheShrineTrackersTowerRegions() = withCatalog { c ->
        assertEquals(ShrineRepository.load(Games.BOTW).regions, c.regions)
        c.quests.forEach { assertTrue(it.region in c.regions, "${it.id}: unknown region ${it.region}") }
    }

    @Test
    fun everyShrineQuestLinksToItsShrine() = withCatalog { c ->
        val shrines = ShrineRepository.load(Games.BOTW).shrines.associateBy { it.id }
        val shrineQuests = c.ofKind(QuestKind.SHRINE)
        shrineQuests.forEach { quest ->
            val shrine = assertNotNull(shrines[quest.shrineId], "${quest.id}: no shrine ${quest.shrineId}")
            assertEquals(shrine.quest, quest.name, "${quest.id}: the shrine names a different quest")
            assertEquals(shrine.region, quest.region, "${quest.id}: groups under a different region than its shrine")
        }
        assertEquals(shrineQuests.size, shrineQuests.map { it.shrineId }.toSet().size, "two quests share a shrine")
        assertEquals(
            shrines.values.filter { it.quest != null }.map { it.id }.toSet(),
            shrineQuests.map { it.shrineId }.toSet(),
            "every shrine with a Shrine Quest has one here",
        )
        c.quests.filter { it.kind != QuestKind.SHRINE }.forEach { assertNull(it.shrineId, "${it.id} isn't a Shrine Quest") }
    }

    @Test
    fun rewardArmorIsRealArmor() = withCatalog { c ->
        val armor = ArmorRepository.load(Games.BOTW).pieces.map { it.id }.toSet()
        c.quests.forEach { quest ->
            quest.rewardArmor.forEach { assertTrue(it in armor, "${quest.id}: no armor piece $it") }
        }
    }

    @Test
    fun onlyDlcQuestsNameAPack() = withCatalog { c ->
        c.quests.forEach { quest ->
            if (quest.kind == QuestKind.DLC) {
                assertTrue(quest.pack in PACKS, "${quest.id}: pack ${quest.pack}")
                assertTrue(quest.name.startsWith("EX "), "${quest.id}: DLC quests are the EX quests")
            } else {
                assertNull(quest.pack, "${quest.id} isn't DLC")
            }
        }
    }

    @Test
    fun everyQuestHasItsDetails() = withCatalog { c ->
        c.quests.forEach { quest ->
            listOf(quest.name, quest.giver, quest.giverLocation, quest.objective, quest.reward).forEach {
                assertTrue(it.isNotBlank(), "${quest.id}: blank field")
            }
            listOfNotNull(quest.prerequisites, quest.limited).forEach { assertTrue(it.isNotBlank(), "${quest.id}: blank note") }
        }
    }

    /** Spot checks of well-known quests. */
    @Test
    fun knownQuests() = withCatalog { c ->
        val byName = c.quests.associateBy { it.name }
        assertEquals("Hateno", byName.getValue("Hylian Homeowner").region)
        assertEquals("mijah_rokee", byName.getValue("Under a Red Moon").shrineId)
        assertNotNull(byName.getValue("Under a Red Moon").limited, "needs a Blood Moon")
        assertEquals("lakna_rokee", byName.getValue("The Stolen Heirloom").shrineId)
    }

    private companion object {
        const val DLC_QUESTS = 13
        val PACKS = setOf("The Master Trials", "The Champions' Ballad")
    }
}
