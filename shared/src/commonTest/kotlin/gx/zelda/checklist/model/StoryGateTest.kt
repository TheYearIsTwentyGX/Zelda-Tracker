package gx.zelda.checklist.model

import gx.zelda.checklist.StoryFixture
import gx.zelda.checklist.StoryFixture.gate
import gx.zelda.checklist.StoryFixture.progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryGateTest {
    private val catalog = StoryFixture.gated

    private fun item(id: String) = catalog.item(id)!!

    @Test
    fun anItemIsLockedUntilItsStepIsDoneAndOnlyWithGatingOn() {
        assertTrue(gate().isLocked(item("forest_piece"), progress()))
        assertFalse(gate("forest").isLocked(item("forest_piece"), progress()))
        assertFalse(gate(enabled = false).isLocked(item("forest_piece"), progress()))
        assertFalse(StoryGate.OFF.isLocked(item("forest_piece"), progress()))
        assertFalse(gate().isLocked(item("start"), progress()), "no unlockedBy: available from the start")
    }

    @Test
    fun anythingTickedAheadOfTheStoryIsNeverLocked() {
        assertFalse(gate().isLocked(item("mountain_piece"), progress("mountain_piece" to 1)))
        assertFalse(gate().isLocked(item("quiver"), progress("quiver" to 1)), "one tier done is started")
    }

    @Test
    fun severalStepsMustAllBeDone() {
        assertTrue(gate("forest", "fire").isLocked(item("both"), progress()))
        assertFalse(gate("forest", "fire", "water").isLocked(item("both"), progress()))
    }

    @Test
    fun anUnknownStepGatesNothing() {
        assertFalse(gate().isLocked(item("typo"), progress()))
    }

    @Test
    fun stepNamesAreKnownWithGatingOff() {
        assertEquals(listOf("Fire Temple", "Water Temple"), gate(enabled = false).stepsFor(item("both")))
        assertEquals(emptyList(), gate().stepsFor(item("typo")))
    }

    @Test
    fun storyOrderIsHowFarIntoTheStoryEachStepComes() {
        assertEquals(mapOf("forest" to 0, "fire" to 1, "water" to 1, "finale" to 2), storyOrder(StoryFixture.story))
    }

    @Test
    fun nextSkipsLockedItems() {
        val done = progress("start" to 1)
        assertEquals("typo", catalog.next(done) { gate().isLocked(it, done) }?.id, "next_to_it still needs the locked piece")
        assertEquals("forest_piece", catalog.next(done)?.id)
    }

    @Test
    fun problemsCheckUnlockedByIds() {
        val bad = catalog.copy(
            sections = catalog.sections.map { s ->
                s.copy(items = s.items.map { if (it.id == "start") it.copy(unlockedBy = listOf("Forest Temple", "fire", "fire")) else it })
            },
        )
        assertEquals(
            listOf("item 'start': bad unlockedBy id 'Forest Temple'", "item 'start' lists an unlockedBy step twice"),
            bad.problems(),
        )
        assertEquals(emptyList(), catalog.problems())
    }
}
