package gx.zelda.bossrush

import gx.zelda.bossrush.data.BossRushRepository
import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.resources.Res
import gx.zelda.resources.allDrawableResources
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Skyward Sword's `files/ss/boss_rush.json`: the decompilation's 12 record slots, in its order, and the Side Quests item it points at. */
class BossRushDatasetTest {
    @Test
    fun theBoardIsSkywardSwordsAndATrackerNotATool() {
        assertEquals(listOf("ss"), Games.all.filter { g -> g.trackers.any { it.kind == TrackerKind.BOSS_RUSH } }.map { it.id })
        assertTrue(!TrackerKind.BOSS_RUSH.isTool && !TrackerKind.BOSS_RUSH.usesDataFile)
    }

    @Test
    fun twelveBossesInTheGamesOrder() = runTest {
        val c = BossRushRepository.load(Games.SS).catalog
        assertEquals(
            listOf(
                "ghirahim_1", "scaldera", "moldarach", "imprisoned_1", "koloktos", "tentalus",
                "ghirahim_2", "imprisoned_2", "imprisoned_3", "horde", "ghirahim_3", "demise",
            ),
            c.bosses.map { it.id },
        )
        // STORYFLAG_<flag>_TIME_MIN/SEC/MS in zeldaret/ss, in the same order (getBossHighscore's switch).
        assertEquals(
            listOf("GHIRAHIM1", "SCALDERA", "MOLDERACH", "IMP1", "KOLOKTOS", "TENTALUS", "GHIRAHIM2", "IMP2", "IMP3", "HORDE", "GHIRAHIM3", "DEMISE"),
            c.bosses.map { it.flag },
        )
        assertEquals(12, c.bosses(heroMode = true).size)
        assertEquals(11, c.bosses(heroMode = false).size)
        assertEquals(listOf("demise"), c.bosses.filter { it.heroOnly }.map { it.id }, "Demise is Hero Mode only")
        assertEquals(c.bosses.dropLast(1), c.bosses(heroMode = false), "a Normal file shows the same order without Demise")
        assertEquals(c.bosses.size, c.bosses.map { it.id }.toSet().size)
        assertEquals(
            listOf("Ghirahim (1st battle)", "Ghirahim (2nd battle)", "Ghirahim (3rd battle)"),
            c.bosses.filter { it.name == "Ghirahim" }.map { it.title },
        )
    }

    @Test
    fun everyBossHasItsPicture() = runTest {
        val c = BossRushRepository.load(Games.SS).catalog
        c.bosses.forEach { assertNotNull(Res.allDrawableResources["ss_boss_${it.icon}"], it.id) }
    }

    /** The prizes and the story gate are the Side Quests item's, not copied: it exists, with its tiers and its step. */
    @Test
    fun itPointsAtTheSideQuestsLightningRound() = runTest {
        val data = BossRushRepository.load(Games.SS)
        assertEquals("ss/side_quests.json", data.catalog.sideQuest.dataFile)
        val quest = assertNotNull(data.sideQuest)
        assertEquals("thunder_dragon_boss_rush", quest.id)
        assertEquals(listOf("hero_song_lanayru"), quest.unlockedBy)
        assertEquals(listOf("4 wins in a row", "8 wins in a row", "11 wins in a row"), quest.tiers.map { it.name })
        assertNotNull(ChecklistRepository.load("ss/main_quest.json").item("hero_song_lanayru"))
    }
}
