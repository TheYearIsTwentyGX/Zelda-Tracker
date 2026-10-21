package gx.zelda.upgrades.data

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.upgrades.logic.UpgradeCalculator
import gx.zelda.upgrades.logic.UpgradeTotalsMode
import gx.zelda.upgrades.model.MaterialKind
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradePrefs
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Skyward Sword's `files/ss/upgrades.json`, against the counts and prices in docs/ss.md. */
class SsUpgradeDatasetTest {
    private suspend fun catalog(): UpgradeCatalog = UpgradeRepository.load(Games.SS)

    @Test
    fun theTrackerIsListedForSkywardSwordOnly() {
        assertEquals(listOf("ss"), Games.all.filter { g -> g.trackers.any { it.kind == TrackerKind.ITEM_UPGRADES } }.map { it.id })
    }

    @Test
    fun sixteenItemsInFourSections() = runTest {
        val c = catalog()
        assertEquals(listOf("items", "shields", "pouch", "potions"), c.sections.map { it.id })
        val bySection = c.items.groupBy { it.section }.mapValues { (_, items) -> items.map { it.id } }
        assertEquals(listOf("slingshot", "beetle", "bug_net", "bow"), bySection["items"])
        assertEquals(listOf("wooden_shield", "iron_shield", "sacred_shield", "hylian_shield"), bySection["shields"])
        assertEquals(listOf("seed_satchel", "quiver", "bomb_bag"), bySection["pouch"])
        assertEquals(listOf("heart_potion", "stamina_potion", "air_potion", "revitalizing_potion", "guardian_potion"), bySection["potions"])
        // Upgrades per item, in list order: 26 in all, 7 of them potion infusions.
        assertEquals(listOf(1, 3, 1, 2, 2, 2, 2, 0, 2, 2, 2, 2, 1, 1, 2, 1), c.items.map { it.maxLevel })
        assertEquals(26, c.items.sumOf { it.maxLevel })
        assertEquals(7, c.items.filter { it.section == "potions" }.sumOf { it.maxLevel })
    }

    @Test
    fun theFormsAreTheGamesNames() = runTest {
        val c = catalog()
        fun forms(id: String) = c.item(id)!!.levels.map { it.name }
        assertEquals(listOf("Beetle", "Hook Beetle", "Quick Beetle", "Tough Beetle"), forms("beetle"))
        assertEquals(listOf("Slingshot", "Scattershot"), forms("slingshot"))
        assertEquals(listOf("Bow", "Iron Bow", "Sacred Bow"), forms("bow"))
        assertEquals(listOf("Bug Net", "Big Bug Net"), forms("bug_net"))
        assertEquals(listOf("Wooden Shield", "Banded Shield", "Braced Shield"), forms("wooden_shield"))
        assertEquals(listOf("Iron Shield", "Reinforced Shield", "Fortified Shield"), forms("iron_shield"))
        assertEquals(listOf("Sacred Shield", "Divine Shield", "Goddess Shield"), forms("sacred_shield"))
        assertEquals(listOf("Hylian Shield"), forms("hylian_shield"))
        assertEquals(listOf("Small Seed Satchel", "Medium Seed Satchel", "Large Seed Satchel"), forms("seed_satchel"))
        assertEquals(listOf("Small Quiver", "Medium Quiver", "Large Quiver"), forms("quiver"))
        assertEquals(listOf("Small Bomb Bag", "Medium Bomb Bag", "Large Bomb Bag"), forms("bomb_bag"))
        assertEquals(listOf("Heart Potion", "Heart Potion+", "Heart Potion++"), forms("heart_potion"))
        assertEquals(listOf("Revitalizing Potion", "Revitalizing Potion+", "Revitalizing Potion++"), forms("revitalizing_potion"))
        assertEquals(listOf("Stamina Potion", "Stamina Potion+"), forms("stamina_potion"))
        assertEquals(listOf("Air Potion", "Air Potion+"), forms("air_potion"))
        assertEquals(listOf("Guardian Potion", "Guardian Potion+"), forms("guardian_potion"))
    }

    /** Every upgrade has a price in Rupees and materials, except the Hook Beetle, which a robot fits for free. */
    @Test
    fun everyStepHasItsCosts() = runTest {
        val c = catalog()
        for (item in c.items) {
            assertEquals(item.upgrades.size + 1, item.levels.size, item.id)
            item.upgrades.forEachIndexed { i, step ->
                val what = "${item.id} → ${item.levels[i + 1].name}"
                if (step.free) {
                    assertEquals(0, step.rupees, what)
                    assertTrue(step.materials.isEmpty(), what)
                } else {
                    assertTrue(step.rupees > 0, what)
                    assertTrue(step.materials.isNotEmpty(), what)
                    step.materials.forEach { assertTrue(it.qty > 0, what) }
                    assertEquals(step.materials.size, step.materials.map { it.material }.toSet().size, "$what lists a material twice")
                }
                assertTrue(step.by.isNotBlank(), what)
            }
            assertTrue(item.acquire.isNotEmpty(), "${item.id}: where to get it")
        }
        assertEquals(listOf("Hook Beetle"), c.items.flatMap { i -> i.upgrades.withIndex().filter { it.value.free }.map { i.levels[it.index + 1].name } })
        // Potions are infused by Bertie, the rest upgraded by Gondo.
        c.items.filter { it.section == "potions" }.flatMap { it.upgrades }.forEach { assertEquals("Bertie's Infusion Shop", it.by) }
        c.items.filter { it.section != "potions" }.flatMap { it.upgrades }.filterNot { it.free }.forEach { assertEquals("Gondo's Scrap Shop", it.by) }
    }

    /** Ids are saved progress and name icons, so they're unique and snake_case. */
    @Test
    fun idsAreUniqueAndStable() = runTest {
        val c = catalog()
        val snake = Regex("[a-z][a-z0-9_]*")
        for (ids in listOf(c.sections.map { it.id }, c.items.map { it.id }, c.items.flatMap { i -> i.levels.map { it.id } }, c.materials.map { it.id })) {
            assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
            ids.forEach { assertTrue(snake.matches(it), it) }
        }
    }

    @Test
    fun everyReferenceResolves() = runTest {
        val c = catalog()
        val sections = c.sections.map { it.id }.toSet()
        val materials = c.materials.map { it.id }.toSet()
        c.items.forEach { assertTrue(it.section in sections, it.id) }
        val used = c.items.flatMap { i -> i.upgrades.flatMap { s -> s.materials.map { it.material } } }.toSet()
        assertEquals(materials, used, "every material is used, and every one used is listed")
        c.materials.forEach { assertTrue(it.farm.isNotBlank(), "${it.id}: where to farm it") }
        // Only the pouch items are carried several at once, and only the potions can be filtered out.
        assertEquals(listOf("seed_satchel", "quiver", "bomb_bag"), c.items.filter { it.multiple }.map { it.id })
        assertEquals(listOf("potions"), c.optionalSections.map { it.id })
        assertTrue(c.items.none { it.missable }, "nothing here can be missed (docs/ss.md)")
    }

    /** All 12 bugs and 16 treasures go into something, in the Collection screen's order. */
    @Test
    fun twelveBugsAndSixteenTreasures() = runTest {
        val c = catalog()
        assertEquals(12, c.materials.count { it.kind == MaterialKind.BUG })
        assertEquals(16, c.materials.count { it.kind == MaterialKind.TREASURE })
        assertEquals((1..28).toList(), c.materials.map { it.gameOrder })
    }

    /**
     * Everything fully upgraded, one of each pouch item, takes exactly what the Bugs & Treasures
     * checklist's "Used in …: N in all" note says for every bug and treasure (each treasure's total
     * is also Zelda Wiki's *Quantities Required* table). The two files can't drift apart.
     */
    @Test
    fun theFullTotalsMatchTheBugsAndTreasuresChecklist() = runTest {
        val c = catalog()
        val checklist = ChecklistRepository.load("ss/bugs_treasures.json")
        val inAll = Regex(""": (\d+) in all\.""")
        val expected = checklist.items.associate { item -> item.name to inAll.find(item.notes!!)!!.groupValues[1].toInt() }
        val totals = UpgradeCalculator.totals(c, UpgradeProgress(), UpgradePrefs(), UpgradeTotalsMode.ALL)
        assertEquals(expected, totals.lines.associate { it.material.name to it.qty })
    }

    @Test
    fun theFullTotalsInRupees() = runTest {
        val c = catalog()
        val all = UpgradeCalculator.totals(c, UpgradeProgress(), UpgradePrefs(), UpgradeTotalsMode.ALL)
        // Items 400, shields 480, pouch items 450, potions 180.
        assertEquals(1_510, all.rupees)
        assertEquals(26, all.stepsRemaining)
        assertEquals(15, all.itemsCounted, "all but the Hylian Shield")
        val noPotions = UpgradeCalculator.totals(c, UpgradeProgress(), UpgradePrefs(hiddenSections = setOf("potions")), UpgradeTotalsMode.ALL)
        assertEquals(1_330, noPotions.rupees)
        assertEquals(19, noPotions.stepsRemaining)
        assertTrue(noPotions.lines.none { it.material.kind == MaterialKind.BUG }, "only potions take bugs")
    }

    /** The Rupee prices match the Items & Equipment and Shields checklists' "Upgrade … and N Rupees" lines. */
    @Test
    fun thePricesMatchTheChecklists() = runTest {
        val c = catalog()
        val price = Regex("""and (\d+) Rupees\.$""")
        val tiers = listOf("ss/items.json", "ss/shields.json").flatMap { ChecklistRepository.load(it).items }
            .flatMap { item -> item.tiers.ifEmpty { null }?.map { it.name to it.howTo } ?: listOf(item.name to item.howTo) }
            .mapNotNull { (name, howTo) -> price.find(howTo.orEmpty())?.let { name to it.groupValues[1].toInt() } }
            .toMap()
        val ours = c.items.flatMap { item -> item.upgrades.mapIndexedNotNull { i, s -> if (s.free) null else item.levels[i + 1].name to s.rupees } }.toMap()
        assertEquals(tiers.size, ours.size)
        // The checklist names infused potions' rows by the potion (Stamina Potion+), which is the form too.
        assertEquals(tiers, ours)
    }
}
