package gx.zelda.checklist.data

import gx.zelda.checklist.StoryFixture
import gx.zelda.checklist.StoryFixture.gate
import gx.zelda.checklist.StoryFixture.progress
import gx.zelda.checklist.model.ChecklistPrefs
import kotlin.test.Test
import kotlin.test.assertEquals

/** The overview row with story gating: the bar keeps its total, and *Next* skips what's locked. */
class ChecklistStoryGateProviderTest {
    private val catalog = StoryFixture.gated

    @Test
    fun theBarKeepsItsTotalAndTheDetailSaysHowManyUnlockLater() {
        val row = ChecklistProgressProvider.summarize("T", catalog, progress("start" to 1), ChecklistPrefs(), gate())
        assertEquals(1, row.done)
        assertEquals(8, row.total)
        assertEquals("Pieces 1 / 6 · Upgrades 0 / 2 · 4 unlock later", row.secondary)
        assertEquals("Typo piece, in Field", row.next)
        assertEquals("Pieces 1 / 6 · Upgrades 0 / 2", ChecklistProgressProvider.summarize("T", catalog, progress("start" to 1), ChecklistPrefs()).secondary)
    }

    @Test
    fun withEverythingUnlockedDoneNextSaysMoreUnlockLater() {
        val done = progress("start" to 1, "next_to_it" to 1, "typo" to 1)
        val row = ChecklistProgressProvider.summarize("T", catalog, done, ChecklistPrefs(), gate())
        assertEquals("Everything unlocked is done · 4 unlock later in the story", row.next)
    }

    @Test
    fun withSpoilersHiddenTheCountLeavesOutTheLocked() {
        val row = ChecklistProgressProvider.summarize("T", catalog, progress(), ChecklistPrefs(hideUnchecked = true), gate())
        assertEquals("3 pieces still to do in Field", row.next)
    }
}
