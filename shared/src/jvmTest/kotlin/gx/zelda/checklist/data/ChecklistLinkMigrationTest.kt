package gx.zelda.checklist.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.armor.model.PieceProgress
import gx.zelda.checklist.LinkFixture
import gx.zelda.core.DATA_STORE_FILE
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.upgrades.data.UpgradeStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one-time merge of progress ticked separately before `sameAs` links: ticked on either side is
 * ticked, the higher tier wins, per save file, and running it again changes nothing. The saves are
 * written as the app stored them before links.
 */
class ChecklistLinkMigrationTest {
    private val migration = ChecklistLinkMigration(listOf(LinkFixture.game)) { LinkFixture.catalogs.getValue(it) }

    private fun items(file: String = "file_1") = stringPreferencesKey("link_checklist_items@$file")
    private fun rewards(file: String = "file_1") = stringPreferencesKey("link_checklist_rewards@$file")
    private fun story(file: String = "file_1") = stringPreferencesKey("link_checklist_main_quest@$file")

    /** File 1 as the old app left it: the 30- and 10-crystal rewards ticked, the Wallet at its first tier, the trial ticked and the Sword at 1. */
    private fun oldSave(): MutablePreferences = mutablePreferencesOf(
        rewards() to """{"levels":{"reward_10":1,"reward_30":1,"gold":1}}""",
        items() to """{"levels":{"wallet":1,"sword":1}}""",
        story() to """{"levels":{"start":1,"trial":1}}""",
    )

    @Test
    fun tickedOnEitherSideIsTickedAtTheHigherTier() = runTest {
        val prefs = oldSave()
        assertTrue(migration.shouldMigrate(prefs))
        val migrated = migration.migrate(prefs)
        // The Big Wallet (reward_30) beats the Medium; the Harp comes across; the trial means the Sword's second tier.
        assertEquals("""{"levels":{"wallet":2,"sword":2,"harp":1}}""", migrated[items()])
        assertEquals("""{"levels":{"gold":1}}""", migrated[rewards()], "the rewards keep no level of their own; the Gold Rupee isn't linked")
        assertEquals("""{"levels":{"start":1}}""", migrated[story()])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun aHigherTierOnTheOtherSideIsKept() = runTest {
        val migrated = migration.migrate(
            mutablePreferencesOf(rewards() to """{"levels":{"reward_5":1}}""", items() to """{"levels":{"wallet":3}}"""),
        )
        assertEquals("""{"levels":{"wallet":3}}""", migrated[items()])
        assertEquals("{}", migrated[rewards()])
    }

    @Test
    fun oneSideOnly() = runTest {
        val rewardsOnly = migration.migrate(mutablePreferencesOf(rewards() to """{"levels":{"reward_50":1}}"""))
        assertEquals("""{"levels":{"wallet":3}}""", rewardsOnly[items()], "a store that wasn't there is made for what moves into it")
        assertEquals("{}", rewardsOnly[rewards()])

        val itemsOnly = mutablePreferencesOf(items() to """{"levels":{"wallet":2,"harp":1}}""")
        assertFalse(migration.shouldMigrate(itemsOnly), "the canonical side is already where it's read from")

        // Items' own same-file link: the Harp Song's tick moves to the Harp.
        val sameFile = migration.migrate(mutablePreferencesOf(items() to """{"levels":{"harp_song":1}}"""))
        assertEquals("""{"levels":{"harp":1}}""", sameFile[items()])
    }

    @Test
    fun runningAgainChangesNothing() = runTest {
        val once = migration.migrate(oldSave())
        val twice = migration.migrate(once)
        assertEquals(once, twice)
        assertFalse(migration.shouldMigrate(twice))
    }

    @Test
    fun eachSaveFileMergesOnItsOwn() = runTest {
        val migrated = migration.migrate(
            mutablePreferencesOf(
                rewards("file_1") to """{"levels":{"reward_5":1}}""",
                items("file_2") to """{"levels":{"wallet":2}}""",
                rewards("file_2") to """{"levels":{"reward_10":1}}""",
            ),
        )
        assertEquals("""{"levels":{"wallet":1}}""", migrated[items("file_1")])
        assertEquals("""{"levels":{"wallet":2,"harp":1}}""", migrated[items("file_2")])
        assertEquals("{}", migrated[rewards("file_2")])
        assertNull(migrated[story("file_1")], "nothing to merge there")
    }

    /** A store that can't be read keeps what it had, and the links into it wait. */
    @Test
    fun anUnreadableStoreIsLeftAlone() = runTest {
        val prefs = mutablePreferencesOf(
            rewards("file_1") to """{"levels":{"reward_5":1,"gold":1}}""",
            items("file_1") to "not json",
            rewards("file_2") to "not json",
            items("file_2") to """{"levels":{"wallet":1}}""",
        )
        assertFalse(migration.shouldMigrate(prefs), "nothing that can be merged")
        val migrated = migration.migrate(prefs)
        assertEquals("not json", migrated[items("file_1")])
        assertEquals("""{"levels":{"reward_5":1,"gold":1}}""", migrated[rewards("file_1")])
        assertEquals("not json", migrated[rewards("file_2")])
        assertEquals("""{"levels":{"wallet":1}}""", migrated[items("file_2")])
    }

    @Test
    fun aGameNeverPlayedLoadsNothing() = runTest {
        var loads = 0
        val counting = ChecklistLinkMigration(listOf(LinkFixture.game)) { loads++; LinkFixture.catalogs.getValue(it) }
        assertFalse(counting.shouldMigrate(mutablePreferencesOf(stringPreferencesKey("app_settings") to "{}")))
        assertEquals(0, loads)
    }

    /**
     * End to end on Skyward Sword: a store written by the old app, opened by the new one. Both
     * migrations run before the first read: Item Upgrades' Hylian Shield moves to the Shields row
     * (#79), then that row's tick, as a link, to the Lightning Round's 8-win tier.
     */
    @Test
    fun theAppMergesAnOldSkywardSwordSaveWhenItOpensTheStore() = runTest {
        val dir = Files.createTempDirectory("zgx").toString()
        val oldScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val old = PreferenceDataStoreFactory.createWithPath(scope = oldScope, produceFile = { "$dir/$DATA_STORE_FILE".toPath() })
        old.edit {
            it[stringPreferencesKey("ss_checklist_gratitude_crystals@file_1")] = """{"levels":{"batreaux_5":1,"batreaux_30":1,"wryna_finding_kukiel":1}}"""
            it[stringPreferencesKey("ss_checklist_items@file_1")] = """{"levels":{"wallet":1,"sailcloth":1}}"""
            it[stringPreferencesKey("ss_checklist_heart_pieces@file_1")] = """{"levels":{"goddess_chest_bug_rock":1}}"""
            it[stringPreferencesKey("ss_checklist_items@file_2")] = """{"levels":{"wallet":4}}"""
            it[stringPreferencesKey("ss_upgrades@file_1")] = """{"items":{"hylian_shield":{"owned":true}}}"""
        }
        oldScope.coroutineContext[Job]!!.cancelAndJoin()

        val dataStore = createDataStore(dir)
        fun storage(file: String) = ChecklistStorage(dataStore, Games.SS, "ss/$file.json")
        val items = storage("items").progress("file_1").first()
        assertEquals(mapOf("wallet" to 2, "goddess_harp" to null), mapOf("wallet" to items.levels["wallet"], "goddess_harp" to items.levels["goddess_harp"]))
        assertEquals(mapOf("wing_ceremony" to 1), storage("main_quest").progress("file_1").first().levels, "the Sailcloth is the Wing Ceremony's")
        assertEquals(mapOf("wryna_finding_kukiel" to 1), storage("gratitude_crystals").progress("file_1").first().levels)
        assertEquals(mapOf("cube_summit_waterfall" to 2), storage("goddess_cubes").progress("file_1").first().levels, "the chest's piece opened it, so the cube was struck")
        assertEquals(mapOf("thunder_dragon_boss_rush" to 2), storage("side_quests").progress("file_1").first().levels)
        assertEquals(emptyMap(), storage("shields").progress("file_1").first().levels)
        assertEquals(mapOf("wallet" to 4), storage("items").progress("file_2").first().levels, "File 2 had nothing to merge")
        assertEquals(PieceProgress(owned = true), UpgradeStorage(dataStore, Games.SS).progress("file_1").first().of("hylian_shield"))
        assertFalse(ChecklistLinkMigration().shouldMigrate(dataStore.data.first()))
    }
}
