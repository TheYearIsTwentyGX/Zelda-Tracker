package gx.zelda.mainquest.data

import gx.zelda.core.Games
import gx.zelda.mainquest.model.ItemCategory
import gx.zelda.mainquest.model.MainQuestCatalog
import gx.zelda.mainquest.model.MainQuestSection
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Structural checks on the bundled Minish Cap main_quest.json. Sources are in docs/mc-main-quest.md. */
class McMainQuestDatasetTest {
    private fun withCatalog(block: (MainQuestCatalog) -> Unit) = runTest { block(MainQuestRepository.load(Games.MC)) }

    @Test
    fun hasOnlyTheMinishCapChecklists() = withCatalog { c ->
        assertEquals(listOf(MainQuestSection.DUNGEONS, MainQuestSection.ITEMS, MainQuestSection.TIGER_SCROLLS), c.sections)
        assertTrue(c.towers.isEmpty() && c.divineBeasts.isEmpty() && c.memories.isEmpty())
    }

    @Test
    fun botwKeepsItsOwnThreeChecklists() = runTest {
        val botw = MainQuestRepository.load(Games.BOTW)
        assertEquals(listOf(MainQuestSection.TOWERS, MainQuestSection.DIVINE_BEASTS, MainQuestSection.MEMORIES), botw.sections)
    }

    @Test
    fun hasTheSixDungeonsInStoryOrder() = withCatalog { c ->
        assertEquals((1..6).toList(), c.dungeons.map { it.number })
        assertEquals(
            listOf("Deepwood Shrine", "Cave of Flames", "Fortress of Winds", "Temple of Droplets", "Palace of Winds", "Dark Hyrule Castle"),
            c.dungeons.map { it.name },
        )
        assertEquals(
            listOf("Big Green Chuchu", "Gleerok", "Mazaal", "Big Octorok", "Gyorg Pair"),
            c.dungeons.take(5).map { it.boss },
        )
        assertTrue(c.dungeons.last().boss.startsWith("Vaati"))
        assertEquals(
            listOf("Gust Jar", "Cane of Pacci", "Mole Mitts", "Flame Lantern", "Roc's Cape", null),
            c.dungeons.map { it.item },
        )
    }

    @Test
    fun fourElementsAndNoneInTheFortressOfWinds() = withCatalog { c ->
        assertEquals(listOf("Earth", "Fire", null, "Water", "Wind", null), c.dungeons.map { it.element })
        assertEquals(4, c.elementTotal)
        for (dungeon in c.dungeons) {
            dungeon.element?.let { assertTrue("$it Element" in dungeon.rewards, "${dungeon.id} rewards its Element") }
        }
        assertTrue("Ocarina of Wind" in c.dungeons.single { it.id == "fortress_of_winds" }.rewards)
    }

    @Test
    fun theSanctuaryChangesTheSwordAfterFireWaterAndWind() = withCatalog { c ->
        // Earth waits for Fire: the two are infused together.
        assertEquals(listOf("cave_of_flames", "temple_of_droplets", "palace_of_winds"), c.dungeons.filter { it.sanctuary != null }.map { it.id })
        val swords = c.items.filter { it.category == ItemCategory.SWORD }.map { it.name }
        assertEquals(
            listOf("Smith's Sword", "White Sword", "White Sword (Two Elements)", "White Sword (Three Elements)", "Four Sword"),
            swords,
        )
        assertTrue("Four Sword" in c.dungeons.single { it.element == "Wind" }.sanctuary!!)
    }

    @Test
    fun everyBossButVaatiGivesAHeartContainer() = withCatalog { c ->
        assertEquals(5, c.dungeons.count { "Heart Container" in it.rewards })
        assertTrue(c.dungeons.last().rewards.isEmpty())
    }

    @Test
    fun everyDungeonIsFullyDescribed() = withCatalog { c ->
        assertEquals(c.dungeons.size, c.dungeons.map { it.id }.toSet().size, "dungeon ids are unique")
        for (dungeon in c.dungeons) {
            listOf(dungeon.id, dungeon.name, dungeon.region, dungeon.access, dungeon.boss, dungeon.tip).forEach {
                assertTrue(it.isNotBlank(), "${dungeon.id} has a blank field")
            }
        }
    }

    @Test
    fun everyDungeonItemIsOnTheItemList() = withCatalog { c ->
        val names = c.items.map { it.name }.toSet()
        c.dungeons.mapNotNull { it.item }.forEach { assertTrue(it in names, "$it is listed under Items") }
        assertTrue("Ocarina of Wind" in names)
    }

    @Test
    fun hasEveryItemAndUpgrade() = withCatalog { c ->
        assertEquals(38, c.items.size)
        assertEquals(c.items.size, c.items.map { it.id }.toSet().size, "item ids are unique")
        assertEquals(c.items.size, c.items.map { it.name }.toSet().size, "item names are unique")
        val counts = c.items.groupingBy { it.category }.eachCount()
        assertEquals(
            mapOf(
                ItemCategory.SWORD to 5,
                ItemCategory.SHIELD to 2,
                ItemCategory.ITEM to 13,
                ItemCategory.EQUIPMENT to 3,
                ItemCategory.QUEST to 2,
                ItemCategory.UPGRADE to 9,
                ItemCategory.BOTTLE to 4,
            ),
            counts,
        )
        assertEquals(ItemCategory.entries, c.itemsByCategory.map { it.first }, "every category is used, in order")
        val upgrades = c.items.filter { it.category == ItemCategory.UPGRADE }.map { it.name }
        listOf("Big Bomb Bag", "Large Quiver", "Big Wallet").forEach { kind ->
            assertEquals(3, upgrades.count { it.startsWith(kind) }, "three ${kind}s")
        }
        assertEquals(
            setOf("Grip Ring", "Power Bracelets", "Flippers"),
            c.items.filter { it.category == ItemCategory.EQUIPMENT }.map { it.name }.toSet(),
        )
        for (item in c.items) {
            listOf(item.id, item.name, item.region, item.how).forEach { assertTrue(it.isNotBlank(), "${item.id} has a blank field") }
        }
    }

    @Test
    fun onlyTheLightArrowsAreMissableAndOnlyTheMirrorShieldIsPostGame() = withCatalog { c ->
        assertEquals(listOf("light_arrows"), c.items.filter { it.missable }.map { it.id })
        assertEquals(listOf("mirror_shield"), c.items.filter { it.postgame }.map { it.id })
    }

    @Test
    fun hasTheEightTigerScrollsAndThreeUpgrades() = withCatalog { c ->
        assertEquals(
            listOf(
                "Spin Attack", "Rock Breaker", "Roll Attack", "Dash Attack",
                "Peril Beam", "Sword Beam", "Down Thrust", "Great Spin Attack",
            ),
            c.scrolls.map { it.name },
        )
        assertEquals(8, c.total(MainQuestSection.TIGER_SCROLLS), "the tab counts scrolls, not upgrades")
        assertEquals(listOf("Scarblade", "Splitblade", "Greatblade"), c.techniqueUpgrades.map { it.teacher })
        assertEquals(c.tigerScrolls.size, c.tigerScrolls.map { it.id }.toSet().size, "technique ids are unique")
        assertEquals(4, c.scrolls.count { it.teacher == "Swiftblade" })
        assertEquals(
            setOf("Swiftblade", "Grayblade", "Waveblade", "Grimblade", "Swiftblade I"),
            c.scrolls.map { it.teacher }.toSet(),
            "five of the Blade Brothers give Tiger Scrolls",
        )
        val last = c.scrolls.last()
        assertEquals("Swiftblade I", last.teacher)
        assertTrue("seven" in last.requirement, "the Great Spin Attack needs the other seven scrolls")
        for (scroll in c.tigerScrolls) {
            listOf(scroll.name, scroll.teacher, scroll.location, scroll.requirement, scroll.effect).forEach {
                assertTrue(it.isNotBlank(), "${scroll.id} has a blank field")
            }
        }
        assertTrue(c.techniqueUpgrades.all { "Fuse Kinstones" in it.requirement }, "each upgrade dojo opens with a fusion")
        assertNull(c.scrolls.firstOrNull { it.upgrade })
    }
}
