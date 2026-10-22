package gx.zelda.potions

import gx.zelda.armor.model.MaterialCost
import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.Games
import gx.zelda.core.TrackerKind
import gx.zelda.potions.data.PotionProgressProvider
import gx.zelda.potions.model.PotionBook
import gx.zelda.upgrades.data.UpgradeRepository
import gx.zelda.upgrades.model.UpgradeCatalog
import gx.zelda.upgrades.model.UpgradeProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Potion Infusions recipe book on Skyward Sword's data: its recipes, the tick rules and its overview row. */
class PotionBookTest {
    private suspend fun catalog(): UpgradeCatalog = UpgradeRepository.load(Games.SS)

    private fun made(vararg potions: Pair<String, PieceProgress>) = UpgradeProgress(items = potions.toMap())

    @Test
    fun theBookIsSkywardSwordsAndListsTheFivePotions() = runTest {
        assertEquals(listOf("ss"), Games.all.filter { g -> g.trackers.any { it.kind == TrackerKind.POTIONS } }.map { it.id })
        val potions = PotionBook.potions(catalog())
        assertEquals(listOf("heart_potion", "stamina_potion", "air_potion", "revitalizing_potion", "guardian_potion"), potions.map { it.id })
        // Each base potion and each infusion: 5 + 7.
        assertEquals(listOf(3, 2, 2, 3, 2), potions.map(PotionBook::steps))
    }

    /** Luv sells the base potion; Bertie does each infusion, for bugs and Rupees. */
    @Test
    fun aRecipeIsTheBasePotionThenEachInfusion() = runTest {
        val heart = catalog().item("heart_potion")!!
        val recipe = PotionBook.recipe(heart, UpgradeProgress())
        assertEquals(listOf("Heart Potion", "Heart Potion+", "Heart Potion++"), recipe.map { it.name })
        assertEquals(listOf("Luv's Potion Shop", "Bertie's Infusion Shop", "Bertie's Infusion Shop"), recipe.map { it.by })
        assertEquals(listOf(20, 20, 30), recipe.map { it.rupees })
        assertEquals(emptyList(), recipe[0].materials)
        assertEquals(
            listOf(MaterialCost("blessed_butterfly", 3), MaterialCost("woodland_rhino_beetle", 1), MaterialCost("volcanic_ladybug", 1)),
            recipe[1].materials,
        )
        assertEquals("Heals all your hearts, two servings", recipe[2].effect)
        assertTrue(recipe.none { it.made })

        // Everything left: the potion, both infusions, and the butterflies of both added together.
        val needs = PotionBook.needs(heart, UpgradeProgress())
        assertEquals(70, needs.rupees)
        assertEquals(6, needs.materials.single { it.material == "blessed_butterfly" }.qty)
        assertEquals(13, needs.materialCount)

        val plus = made("heart_potion" to PieceProgress(owned = true, level = 1))
        assertEquals(listOf(true, true, false), PotionBook.recipe(heart, plus).map { it.made })
        assertEquals(30, PotionBook.needs(heart, plus).rupees)
        assertEquals(2, PotionBook.made(heart, plus))
    }

    /** Steps tick as a checklist's tiers do: making one makes those before it, unmaking one unmakes those after. */
    @Test
    fun stepsTickInOrder() = runTest {
        val heart = catalog().item("heart_potion")!!
        assertEquals(PieceProgress(owned = true, level = 2), PotionBook.withStep(heart, PieceProgress(), 2, made = true))
        assertEquals(PieceProgress(owned = true, level = 0), PotionBook.withStep(heart, PieceProgress(), 0, made = true))
        val full = PieceProgress(owned = true, level = 2)
        assertEquals(PieceProgress(owned = true, level = 0), PotionBook.withStep(heart, full, 1, made = false))
        assertEquals(PieceProgress(), PotionBook.withStep(heart, full, 0, made = false), "unbuying it clears the infusions")
        assertEquals(full, PotionBook.withStep(heart, full, 1, made = true), "making a step already made changes nothing")
        // A level kept on an uncollected potion (Item Upgrades keeps it) counts as nothing made.
        assertEquals(PieceProgress(owned = true, level = 0), PotionBook.withStep(heart, PieceProgress(level = 2), 0, made = true))
        assertFalse(PotionBook.isDone(heart, made("heart_potion" to PieceProgress(level = 2))))
    }

    @Test
    fun theOverviewRowCountsStepsMade() = runTest {
        val c = catalog()
        val fresh = PotionProgressProvider.summarize(c, UpgradeProgress())
        assertEquals("Potion Infusions", fresh.title)
        assertEquals(0 to 12, fresh.done to fresh.total)
        assertEquals("0 potions fully infused of 5", fresh.secondary)
        assertEquals("Heart Potion (Luv's Potion Shop)", fresh.next)

        val some = PotionProgressProvider.summarize(
            c,
            made("heart_potion" to PieceProgress(true, 2), "stamina_potion" to PieceProgress(true, 0)),
        )
        assertEquals(4 to 12, some.done to some.total)
        assertEquals("1 potion fully infused of 5", some.secondary)
        assertEquals("Stamina Potion+ (Bertie's Infusion Shop)", some.next)

        val all = PotionProgressProvider.summarize(c, UpgradeProgress(items = PotionBook.potions(c).associate { it.id to PotionBook.allMade(it, true) }))
        assertEquals(12 to 12, all.done to all.total)
        assertEquals("All done", all.next)
    }
}
