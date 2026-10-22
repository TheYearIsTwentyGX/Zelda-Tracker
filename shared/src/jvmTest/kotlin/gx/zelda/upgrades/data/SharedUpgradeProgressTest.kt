package gx.zelda.upgrades.data

import gx.zelda.armor.model.PieceProgress
import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.core.CountingDataStore
import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.upgrades.model.UpgradeItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Item Upgrades and the Items & Equipment and Shields checklists share one progress for the same
 * things: the checklist's level is stored, and the upgrade tracker reads and writes through it.
 */
class SharedUpgradeProgressTest {
    private val dataStore = CountingDataStore(createDataStore(Files.createTempDirectory("zgx").toString()))
    private val upgrades = UpgradeStorage(dataStore, Games.SS)
    private val items = ChecklistStorage(dataStore, Games.SS, "ss/items.json")
    private val shields = ChecklistStorage(dataStore, Games.SS, "ss/shields.json")
    private val sideQuests = ChecklistStorage(dataStore, Games.SS, "ss/side_quests.json")

    private suspend fun itemsCatalog(): ChecklistCatalog = ChecklistRepository.load("ss/items.json")

    private suspend fun upgrade(key: String, file: String = "file_1") = upgrades.progress(file).first().of(key)

    private suspend fun checklistLevel(storage: ChecklistStorage, id: String, file: String = "file_1") =
        storage.progress(file).first().levels[id] ?: 0

    @Test
    fun aChecklistTierShowsAsTheUpgradeLevel() = runTest {
        val catalog = itemsCatalog()
        // Beetle, Hook Beetle, Quick Beetle: the Quick Beetle is upgrade 2 (the Hook Beetle is the robot's).
        items.setLevel("file_1", catalog.item("beetle")!!, 3)
        assertEquals(PieceProgress(owned = true, level = 2), upgrade("beetle"))
        assertEquals("Quick Beetle", catalog.item("beetle")!!.tiers[2].name)

        items.setLevel("file_1", catalog.item("extra_bomb_bag")!!, 1)
        assertEquals(PieceProgress(owned = true, level = 0), upgrade("bomb_bag"), "the pouch Bomb Bag is the checklist's capacity row")
        items.setDone("file_1", catalog.item("bomb_bag")!!, true)
        assertEquals(PieceProgress(owned = true, level = 0), upgrade("bomb_bag"), "the Earth Temple's Bomb Bag isn't the pouch one")

        shields.setDone("file_1", ChecklistRepository.load("ss/shields.json").item("hylian_shield")!!, true)
        assertEquals(PieceProgress(owned = true), upgrade("hylian_shield"))

        items.setLevel("file_1", catalog.item("beetle")!!, 0)
        assertEquals(PieceProgress(), upgrade("beetle"))
        assertEquals(PieceProgress(), upgrade("beetle", "file_2"), "other save files are untouched")
    }

    @Test
    fun anUpgradeLevelShowsAsTheChecklistTierInOneWrite() = runTest {
        val before = dataStore.writes
        upgrades.update("file_1") { p ->
            p.withCopy("beetle") { it.copy(owned = true, level = 2) }
                .withCopy("wooden_shield") { it.copy(owned = true, level = 1) }
                .withCopy("heart_potion") { it.copy(owned = true) }
        }
        assertEquals(1, dataStore.writes - before, "the checklists and the upgrade key change together")
        assertEquals(3, checklistLevel(items, "beetle"))
        assertEquals(2, checklistLevel(shields, "wooden_shield"))
        assertEquals(0, checklistLevel(items, "heart_potion"), "the potions are the upgrade tracker's own")
        assertEquals(PieceProgress(owned = true), upgrade("heart_potion"))

        // Uncollecting clears the checklist row: it has no level without the item.
        upgrades.update("file_1") { p -> p.withCopy("beetle") { it.copy(owned = false) } }
        assertEquals(0, checklistLevel(items, "beetle"))
        assertEquals(PieceProgress(), upgrade("beetle"))
    }

    /** Copy 1 of a pouch item is the checklist's row; the others are Item Upgrades' alone. */
    @Test
    fun extraPouchCopiesStayInItemUpgrades() = runTest {
        val satchel = UpgradeRepository.load(Games.SS).item("seed_satchel")!!
        upgrades.update("file_1") { p ->
            p.withCopyAdded(satchel).withCopy("seed_satchel#2") { it.copy(owned = true, level = 2) }
                .withCopy("seed_satchel") { it.copy(owned = true, level = 0) }
        }
        assertEquals(1, checklistLevel(items, "seed_satchel"), "copy 1 is the Small Seed Satchel")
        val progress = upgrades.progress("file_1").first()
        assertEquals(2, progress.copyCount(satchel))
        assertEquals(PieceProgress(owned = true, level = 2), progress.of("seed_satchel#2"))

        items.setLevel("file_1", itemsCatalog().item("seed_satchel")!!, 3)
        val after = upgrades.progress("file_1").first()
        assertEquals(PieceProgress(owned = true, level = 2), after.of("seed_satchel"))
        assertEquals(PieceProgress(owned = true, level = 2), after.of("seed_satchel#2"), "copy 2 doesn't follow the checklist")
        assertEquals(2, after.copyCount(satchel))

        // Removing copy 2 leaves copy 1, and so the checklist, as it is.
        upgrades.update("file_1") { it.withCopyRemoved(satchel, 2) }
        assertEquals(3, checklistLevel(items, "seed_satchel"))
    }

    /** Item Upgrades' mark-all collects through the checklists in one write, and its undo puts back exactly what was there. */
    @Test
    fun markAllAndItsUndoGoThroughTheChecklists() = runTest {
        items.setLevel("file_1", itemsCatalog().item("bow")!!, 2)
        val keys = listOf("bow", "beetle", "hylian_shield", "heart_potion")
        val before = dataStore.writes
        val snapshot = upgrades.setAllOwned("file_1", keys, owned = true)
        assertEquals(1, dataStore.writes - before)
        assertEquals(mapOf("bow" to PieceProgress(true, 1), "beetle" to null, "hylian_shield" to null, "heart_potion" to null), snapshot.before)
        assertEquals(2, checklistLevel(items, "bow"), "levels stay")
        assertEquals(1, checklistLevel(items, "beetle"))
        // The Shields row is the same as the Lightning Round's 8-win tier, which holds its progress.
        assertEquals(0, checklistLevel(shields, "hylian_shield"))
        assertEquals(2, checklistLevel(sideQuests, "thunder_dragon_boss_rush"))

        upgrades.restore("file_1", snapshot)
        assertEquals(2, checklistLevel(items, "bow"))
        assertEquals(0, checklistLevel(items, "beetle"))
        assertEquals(0, checklistLevel(sideQuests, "thunder_dragon_boss_rush"), "not left at the 4-win tier the mark brought with it")
        assertEquals(PieceProgress(), upgrade("heart_potion"))
    }

    /** The Lightning Round's level behind the Hylian Shield comes back exactly, whatever tiers were around it. */
    @Test
    fun undoingTheHylianShieldPutsTheLightningRoundBack() = runTest {
        val bossRush = ChecklistRepository.load("ss/side_quests.json").item("thunder_dragon_boss_rush")!!
        sideQuests.setLevel("file_1", bossRush, 1)
        val snapshot = upgrades.setAllOwned("file_1", listOf("hylian_shield"), owned = true)
        assertEquals(mapOf("hylian_shield" to 1), snapshot.stored)
        assertEquals(2, checklistLevel(sideQuests, "thunder_dragon_boss_rush"))

        val before = dataStore.writes
        upgrades.restore("file_1", snapshot)
        assertEquals(1, dataStore.writes - before)
        assertEquals(1, checklistLevel(sideQuests, "thunder_dragon_boss_rush"), "the 4-win Piece of Heart stays")
        assertEquals(PieceProgress(), upgrade("hylian_shield"))
    }

    /** The checklist's mark-all, the other way. */
    @Test
    fun theChecklistsMarkAllShowsInItemUpgrades() = runTest {
        val catalog = itemsCatalog()
        val capacity = catalog.section("capacity")!!.items
        items.setLevels("file_1", capacity.associateWith { it.steps })
        val satchel: UpgradeItem = UpgradeRepository.load(Games.SS).item("seed_satchel")!!
        val progress = upgrades.progress("file_1").first()
        listOf("seed_satchel", "quiver", "bomb_bag").forEach { assertEquals(PieceProgress(true, 2), progress.of(it), it) }
        assertEquals(1, progress.copyCount(satchel))
    }
}
