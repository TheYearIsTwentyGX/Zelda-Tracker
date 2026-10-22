package gx.zelda.upgrades.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import gx.zelda.armor.model.PieceProgress
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.core.DATA_STORE_FILE
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
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
 * The one-time merge of Skyward Sword's old, separate progress: each save file's Item Upgrades and
 * Items & Equipment / Shields levels, at the higher of the two, and the old Potion Infusions tab's.
 * The saves below are written in the old formats, as the app stored them before.
 */
class SharedProgressMigrationTest {
    private val migration = SharedProgressMigration()

    private fun key(name: String) = stringPreferencesKey(name)

    private fun items(file: String = "file_1") = key("ss_checklist_items@$file")
    private fun shields(file: String = "file_1") = key("ss_checklist_shields@$file")
    private fun upgrades(file: String = "file_1") = key("ss_upgrades@$file")

    /** An old save on File 1: both trackers used, disagreeing, with a second Seed Satchel and potions in both places. */
    private fun oldSave(): MutablePreferences = mutablePreferencesOf(
        // Items & Equipment: Beetle to the Hook Beetle, Bow to the Iron Bow, a Small Bomb Bag (capacity) and the
        // Earth Temple's Bomb Bag, two Heart Potion infusions and the Stamina Potion+.
        items() to """{"levels":{"beetle":2,"bow":2,"extra_bomb_bag":1,"bomb_bag":1,"sailcloth":1,"heart_potion":2,"stamina_potion":1}}""",
        shields() to """{"levels":{"wooden_shield":3}}""",
        // Item Upgrades: the Tough Beetle (higher), a plain Bow (lower), the Banded Shield (lower), a Seed Satchel
        // and a second one, a Hylian Shield, an Air Potion bought and a Heart Potion+ it already had.
        upgrades() to """{"items":{"beetle":{"owned":true,"level":3},"bow":{"owned":true},"wooden_shield":{"owned":true,"level":1},""" +
            """"seed_satchel":{"owned":true,"level":1},"seed_satchel#2":{"owned":true,"level":2},"hylian_shield":{"owned":true},""" +
            """"quiver":{"level":2},"air_potion":{"owned":true},"heart_potion":{"owned":true,"level":1}},"copies":{"seed_satchel":2}}""",
    )

    @Test
    fun bothStoresMergeAtTheHigherLevel() = runTest {
        val prefs = oldSave()
        assertTrue(migration.shouldMigrate(prefs))
        val migrated = migration.migrate(prefs)

        assertEquals(
            // beetle: Tough (3) beats Hook (1) → tier 4. bow: Iron (tier 2) beats Bow (tier 1). The Earth Temple's
            // Bomb Bag and the Sailcloth are untouched; the potions have left.
            """{"levels":{"beetle":4,"bow":2,"extra_bomb_bag":1,"bomb_bag":1,"sailcloth":1,"seed_satchel":2}}""",
            migrated[items()],
        )
        // The Braced Shield (tier 3) beats the Banded (level 1); the Hylian Shield comes across.
        assertEquals("""{"levels":{"wooden_shield":3,"hylian_shield":1}}""", migrated[shields()])
        assertEquals(
            // Copy 2 and the copy count stay; an uncollected Quiver's level had nothing to keep. Heart Potion++ (2
            // infusions) beats Heart Potion+; the Stamina Potion+ means the Stamina Potion was bought.
            """{"items":{"seed_satchel#2":{"owned":true,"level":2},"air_potion":{"owned":true},"heart_potion":{"owned":true,"level":2},""" +
                """"stamina_potion":{"owned":true,"level":1}},"copies":{"seed_satchel":2}}""",
            migrated[upgrades()],
        )
        assertFalse(migration.shouldMigrate(migrated))
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
        val prefs = mutablePreferencesOf(
            upgrades("file_1") to """{"items":{"slingshot":{"owned":true,"level":1}}}""",
            items("file_2") to """{"levels":{"slingshot":1}}""",
            upgrades("file_3") to """{"items":{"slingshot":{"owned":true}}}""",
            items("file_3") to """{"levels":{"slingshot":2}}""",
        )
        val migrated = migration.migrate(prefs)
        assertEquals("""{"levels":{"slingshot":2}}""", migrated[items("file_1")])
        assertEquals("""{"levels":{"slingshot":1}}""", migrated[items("file_2")], "nothing to merge on File 2")
        assertEquals("""{"levels":{"slingshot":2}}""", migrated[items("file_3")], "the Scattershot beats the Slingshot")
        listOf("file_1", "file_3").forEach { assertEquals("{}", migrated[upgrades(it)], it) }
        assertNull(migrated[upgrades("file_2")], "a store that wasn't there isn't made")
        assertNull(migrated[shields("file_1")])
    }

    /** Only Item Upgrades used, or only the checklists: each keeps what it had, in the shared place. */
    @Test
    fun oneStoreOnly() = runTest {
        val onlyUpgrades = migration.migrate(mutablePreferencesOf(upgrades() to """{"items":{"bomb_bag":{"owned":true,"level":2}}}"""))
        assertEquals("""{"levels":{"extra_bomb_bag":3}}""", onlyUpgrades[items()])
        assertEquals("{}", onlyUpgrades[upgrades()])

        val onlyChecklist = mutablePreferencesOf(items() to """{"levels":{"bow":3}}""")
        assertFalse(migration.shouldMigrate(onlyChecklist), "the checklist's levels are already where they're shared from")

        val potionsOnly = migration.migrate(mutablePreferencesOf(items() to """{"levels":{"guardian_potion":1}}"""))
        assertEquals("{}", potionsOnly[items()])
        assertEquals("""{"items":{"guardian_potion":{"owned":true,"level":1}}}""", potionsOnly[upgrades()])
    }

    /** A store that can't be read (written by something newer, or damaged) is left as it is, with its file. */
    @Test
    fun anUnreadableStoreIsLeftAlone() = runTest {
        val prefs = mutablePreferencesOf(
            upgrades("file_1") to """{"items":{"beetle":{"owned":true,"level":1}}}""",
            items("file_1") to "not json",
            upgrades("file_2") to """{"items":{"beetle":{"owned":true,"level":1}}}""",
        )
        val migrated = migration.migrate(prefs)
        assertEquals("not json", migrated[items("file_1")])
        assertEquals("""{"items":{"beetle":{"owned":true,"level":1}}}""", migrated[upgrades("file_1")])
        assertEquals("""{"levels":{"beetle":2}}""", migrated[items("file_2")])
    }

    /**
     * End to end: a store written by the old app, opened by the new one. The merge happens before
     * the first read, so neither tracker ever sees the old, separate progress.
     */
    @Test
    fun theAppMergesAnOldSaveWhenItOpensTheStore() = runTest {
        val dir = Files.createTempDirectory("zgx").toString()
        val oldScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val old = PreferenceDataStoreFactory.createWithPath(scope = oldScope, produceFile = { "$dir/$DATA_STORE_FILE".toPath() })
        old.edit { it += oldSave() }
        oldScope.coroutineContext[Job]!!.cancelAndJoin()

        val dataStore = createDataStore(dir)
        val upgrades = UpgradeStorage(dataStore, Games.SS)
        val progress = upgrades.progress("file_1").first()
        assertEquals(PieceProgress(owned = true, level = 3), progress.of("beetle"), "the Tough Beetle")
        assertEquals(PieceProgress(owned = true, level = 1), progress.of("bow"), "the Iron Bow")
        assertEquals(PieceProgress(owned = true, level = 1), progress.of("seed_satchel"))
        assertEquals(PieceProgress(owned = true, level = 2), progress.of("seed_satchel#2"))
        assertEquals(PieceProgress(owned = true, level = 2), progress.of("heart_potion"))
        assertEquals(4, ChecklistStorage(dataStore, Games.SS, "ss/items.json").progress("file_1").first().levels["beetle"])
        assertFalse(migration.shouldMigrate(dataStore.data.first()))
    }
}
