package gx.zelda.checklist.model

import gx.zelda.checklist.LinkFixture
import gx.zelda.checklist.LinkFixture.item
import gx.zelda.checklist.LinkFixture.rewardsProgress
import gx.zelda.checklist.data.ChecklistLinks
import gx.zelda.checklist.data.ChecklistRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `sameAs` links: reading and writing through them, counts, story gating and validation. */
class ChecklistLinkTest {
    private val rewards = LinkFixture.rewards

    @Test
    fun linksParse() {
        assertEquals(SameAs("items", "wallet", 2), SameAs.parse("items:wallet@2"))
        assertEquals(SameAs(null, "goddess_sword", 5), SameAs.parse("goddess_sword@5"))
        assertEquals(SameAs("heart_pieces", "batreaux", null), SameAs.parse("heart_pieces:batreaux"))
        listOf("", "items:", ":wallet", "items:wallet@0", "items:wallet@", "Items:wallet", "a:b:c", "wallet@1@2").forEach {
            assertNull(SameAs.parse(it), it)
        }
        assertEquals("items:wallet@2", SameAs("items", "wallet", 2).toString())
    }

    @Test
    fun aPlainLinkReadsAndWritesTheItemItNames() {
        assertFalse(rewardsProgress().isDone(item("reward_10")))
        assertTrue(rewardsProgress(items = mapOf("harp" to 1)).isDone(item("reward_10")))

        val ticked = rewardsProgress().withDone(item("reward_10"), true)
        assertEquals(emptyMap(), ticked.levels, "a linked item has no level of its own")
        assertEquals(mapOf("harp" to 1), ticked.linked["items"])
        assertEquals(emptyMap(), ticked.withDone(item("reward_10"), false).linked["items"])
    }

    /** "The 30-crystal reward is the Big Wallet": the wallet at 2 or more shows it ticked. */
    @Test
    fun aTierLinkReadsAsDoneFromThatTierUp() {
        fun done(wallet: Int) = listOf("reward_5", "reward_30", "reward_50").filter { rewardsProgress(items = mapOf("wallet" to wallet)).isDone(item(it)) }
        assertEquals(emptyList(), done(0))
        assertEquals(listOf("reward_5"), done(1))
        assertEquals(listOf("reward_5", "reward_30"), done(2))
        assertEquals(listOf("reward_5", "reward_30", "reward_50"), done(3))
    }

    /** Ticking raises the wallet to at least the tier; unticking lowers it below, as ticking tiers on its own page does. */
    @Test
    fun aTierLinkWritesTheTier() {
        fun wallet(from: Int, id: String, done: Boolean) =
            rewardsProgress(items = mapOf("wallet" to from)).withDone(item(id), done).linked.getValue("items")["wallet"] ?: 0
        assertEquals(2, wallet(0, "reward_30", true), "the tiers before it come too")
        assertEquals(3, wallet(3, "reward_5", true), "a higher tier is kept")
        assertEquals(1, wallet(3, "reward_30", false), "the tiers after it go")
        assertEquals(0, wallet(3, "reward_5", false))
        assertEquals(1, wallet(1, "reward_50", false), "a lower tier is kept")
    }

    @Test
    fun aLinkInTheSameFileReadsItsOwnLevels() {
        val items = LinkFixture.items
        val progress = ChecklistProgress().withDone(items.item("harp_song")!!, true)
        assertEquals(mapOf("harp" to 1), progress.levels)
        assertTrue(progress.isDone(items.item("harp")!!))
        assertEquals(ChecklistCount(2, 7), items.count(progress), "each row counts: the Harp and the Harp Song")
    }

    @Test
    fun countsAndFormulasReadThroughLinks() {
        val progress = rewardsProgress(items = mapOf("wallet" to 2, "harp" to 1), levels = mapOf("gold" to 1))
        assertEquals(ChecklistCount(4, 6), rewards.count(progress))
        assertEquals(SummaryValue("Rewards", 4.0, 6.0), rewards.evaluate(rewards.summaries.single(), progress), "the maximum counts linked items done")
        assertEquals("reward_50", rewards.next(progress)?.id)
    }

    @Test
    fun storyGatingFollowsLinksBothWays() {
        val story = LinkFixture.story
        // The trial is the Sword's second tier: the sword at 2 means the trial is done, so what it unlocks shows.
        val storyProgress = ChecklistProgress(linked = mapOf("items" to mapOf("sword" to 2))).withDone(story.item("start")!!, true)
        val gate = StoryGate.of(true, story, storyProgress)
        // The trial requires the temple, so reaching it counts the temple as reached too.
        assertEquals(setOf("start", "temple", "trial"), gate.done)
        assertFalse(gate.isLocked(item("after_trial"), rewardsProgress()))
        assertFalse(gate.isLocked(item("reward_30"), rewardsProgress(items = mapOf("wallet" to 1))))

        // Before the temple, the Big Wallet ticked in Items shows the 30-crystal reward: ticked is never hidden.
        val early = StoryGate.of(true, story, ChecklistProgress().withDone(story.item("start")!!, true))
        assertTrue(early.isLocked(item("reward_30"), rewardsProgress(items = mapOf("wallet" to 1))))
        assertFalse(early.isLocked(item("reward_30"), rewardsProgress(items = mapOf("wallet" to 2))))
    }

    @Test
    fun theFixtureIsSound() {
        LinkFixture.catalogs.forEach { (file, catalog) -> assertEquals(emptyList(), catalog.problems(), file) }
        assertEquals(emptyList(), ChecklistLinks.problems(LinkFixture.game, LinkFixture.catalogs))
    }

    private fun problems(itemsJson: String): List<String> =
        ChecklistRepository.parse("""{"sections": [{"id": "s", "title": "S", "items": [$itemsJson]}]}""").problems()

    private val tiered = """{"id": "wallet", "name": "Wallet", "tiers": [{"name": "A"}, {"name": "B"}]}"""

    @Test
    fun problemsCatchBadLinksInAFile() {
        assertEquals(listOf("item 'x': bad sameAs 'wallet@0': write it as [file:]item[@tier]"), problems("""{"id": "x", "name": "X", "sameAs": "wallet@0"}, $tiered"""))
        assertEquals(listOf("item 'x' is the same as unknown item 'nope'"), problems("""{"id": "x", "name": "X", "sameAs": "nope"}"""))
        assertEquals(listOf("item 'x' is the same as itself"), problems("""{"id": "x", "name": "X", "sameAs": "x"}"""))
        assertEquals(
            listOf("item 'x' names tier 3 of 'wallet', which has 2"),
            problems("""$tiered, {"id": "x", "name": "X", "sameAs": "wallet@3"}"""),
        )
        assertEquals(
            listOf("item 'x' is the same as 'wallet', which has tiers: name one, as 'wallet@1'"),
            problems("""$tiered, {"id": "x", "name": "X", "sameAs": "wallet"}"""),
        )
        assertEquals(
            listOf("item 'x' names tier 1 of 'y', which has no tiers"),
            problems("""{"id": "y", "name": "Y"}, {"id": "x", "name": "X", "sameAs": "y@1"}"""),
        )
        assertEquals(
            listOf("item 'x' has tiers and a sameAs link: only an item without tiers can be linked"),
            problems("""$tiered, {"id": "x", "name": "X", "sameAs": "wallet@1", "tiers": [{"name": "A"}]}"""),
        )
        assertEquals(
            listOf("item 'x' is the same as 'y', which comes after it: link the later of two items without tiers to the earlier"),
            problems("""{"id": "x", "name": "X", "sameAs": "y"}, {"id": "y", "name": "Y"}"""),
        )
    }

    /** A link to a linked item is refused, which also rules out loops of any length. */
    @Test
    fun problemsCatchChainsAndLoops() {
        assertEquals(
            listOf("item 'z' is the same as 'y', which is itself linked: link to what that names instead"),
            problems("""{"id": "x", "name": "X"}, {"id": "y", "name": "Y", "sameAs": "x"}, {"id": "z", "name": "Z", "sameAs": "y"}"""),
        )
        val loop = problems("""{"id": "x", "name": "X", "sameAs": "y"}, {"id": "y", "name": "Y", "sameAs": "x"}""")
        assertTrue(loop.any { "'x' is the same as 'y', which is itself linked" in it }, loop.toString())
        assertTrue(loop.any { "'y' is the same as 'x', which is itself linked" in it }, loop.toString())
    }

    @Test
    fun problemsCatchBadLinksBetweenFiles() {
        fun crossProblems(rewardsItem: String, storyItem: String = """{"id": "start", "name": "Start"}"""): List<String> {
            val catalogs = LinkFixture.catalogs + mapOf(
                "link/rewards.json" to ChecklistRepository.parse("""{"sections": [{"id": "r", "title": "R", "items": [$rewardsItem]}]}"""),
                "link/main_quest.json" to ChecklistRepository.parse("""{"sections": [{"id": "q", "title": "Q", "items": [$storyItem]}]}"""),
            )
            return ChecklistLinks.problems(LinkFixture.game, catalogs)
        }
        assertEquals(
            listOf("rewards: item 'x' is the same as 'shop:harp', but link has no checklist 'shop'"),
            crossProblems("""{"id": "x", "name": "X", "sameAs": "shop:harp"}"""),
        )
        assertEquals(
            listOf("rewards: item 'x' is the same as unknown item 'lute' in items"),
            crossProblems("""{"id": "x", "name": "X", "sameAs": "items:lute"}"""),
        )
        assertEquals(
            listOf("rewards: item 'x' names its own file: write it as 'y'"),
            crossProblems("""{"id": "x", "name": "X", "sameAs": "rewards:y"}, {"id": "y", "name": "Y"}"""),
        )
        assertEquals(
            listOf("rewards: item 'x' names tier 4 of 'wallet', which has 3"),
            crossProblems("""{"id": "x", "name": "X", "sameAs": "items:wallet@4"}"""),
        )
        assertEquals(
            listOf("rewards: item 'x' is the same as 'items:harp_song', which is itself linked: link to what that names instead"),
            crossProblems("""{"id": "x", "name": "X", "sameAs": "items:harp_song"}"""),
        )
        // Items is listed before Rewards: an item there without tiers holds the progress, not one here.
        assertEquals(
            listOf("main_quest: item 'start' is the same as 'rewards:x', in a file listed after main_quest: link the item in the later file to the earlier"),
            crossProblems("""{"id": "x", "name": "X"}""", """{"id": "start", "name": "Start", "sameAs": "rewards:x"}"""),
        )
        // Two files naming each other: each is linked to a linked item.
        val loop = crossProblems("""{"id": "x", "name": "X", "sameAs": "main_quest:start"}""", """{"id": "start", "name": "Start", "sameAs": "rewards:x"}""")
        assertEquals(2, loop.count { "which is itself linked" in it }, loop.toString())
    }
}
