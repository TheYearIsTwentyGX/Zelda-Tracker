package gx.zelda.overview

import gx.zelda.armor.data.ArmorProgressProvider
import gx.zelda.armor.data.ArmorRepository
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.model.PieceProgress
import gx.zelda.armor.model.Source
import gx.zelda.bosses.data.BossProgressProvider
import gx.zelda.bosses.data.BossRepository
import gx.zelda.bosses.data.BossStorage
import gx.zelda.compendium.data.CompendiumProgressProvider
import gx.zelda.compendium.data.CompendiumRepository
import gx.zelda.compendium.data.CompendiumStorage
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Games
import gx.zelda.core.ItemCount
import gx.zelda.core.ProgressProvider
import gx.zelda.core.ProgressSummary
import gx.zelda.core.createDataStore
import gx.zelda.fairies.data.FairyProgressProvider
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.koroks.data.KorokProgressProvider
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.mainquest.data.MainQuestProgressProvider
import gx.zelda.mainquest.data.MainQuestRepository
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestSection
import gx.zelda.shrines.data.ShrineProgressProvider
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.data.SpiritOrbProgressProvider
import gx.zelda.sidequests.data.SideQuestProgressProvider
import gx.zelda.sidequests.data.SideQuestRepository
import gx.zelda.sidequests.data.SideQuestStorage
import gx.zelda.sidequests.model.QuestKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Each tracker's overview row, computed from real datasets and progress written to a fresh store. */
class ProgressProvidersTest {
    private val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
    private val game = Games.BOTW
    private val file1 = game.saveFile("normal_1")
    private val master = game.saveFile("master")

    private suspend fun ProgressProvider.on(file: String = "normal_1"): ProgressSummary = summary(game.saveFile(file)).first()

    @Test
    fun shrinesCountTheBaseGameAndTheDlcApart() = runTest {
        val catalog = ShrineRepository.load(game)
        val storage = ShrineStorage(dataStore, game)
        catalog.base.take(3).forEach { storage.setState("normal_1", it.id, DiscoveryState.COMPLETED) }
        catalog.base.drop(3).take(2).forEach { storage.setState("normal_1", it.id, DiscoveryState.FOUND) }
        storage.setState("normal_1", catalog.dlc.first().id, DiscoveryState.COMPLETED)

        val summary = ShrineProgressProvider(storage).on()
        assertEquals(3, summary.done)
        assertEquals(120, summary.total)
        assertEquals("Found 6 · DLC 1 / 16", summary.secondary)
        assertEquals(ItemCount("shrines", 3, 120), summary.mapItems, "DLC shrines don't count toward the map")
        assertEquals("Complete ${catalog.base[3].name} Shrine, found in ${catalog.base[3].region}", summary.next)

        storage.updatePrefs { it.copy(showDlc = false) }
        assertEquals("Found 5", ShrineProgressProvider(storage).on().secondary)
    }

    @Test
    fun shrineSuggestionNamesNothingUndiscoveredWhileSpoilersAreHidden() = runTest {
        val catalog = ShrineRepository.load(game)
        val storage = ShrineStorage(dataStore, game)
        // The Great Plateau's four, so the region closest to done is the next one with fewest shrines.
        catalog.shrines.filter { it.region == "Great Plateau" }.forEach { storage.setState("normal_1", it.id, DiscoveryState.COMPLETED) }

        val shown = ShrineProgressProvider(storage).on().next
        assertNotNull(shown)
        assertTrue(catalog.shrines.any { "${it.name} Shrine" in shown }, "names a shrine while spoilers are shown: $shown")

        storage.updatePrefs { it.copy(hideUndiscovered = true) }
        val hidden = ShrineProgressProvider(storage).on().next
        assertNotNull(hidden)
        assertTrue(hidden.endsWith("Tower region"), hidden)
        assertFalse(catalog.shrines.any { it.name in hidden }, "no shrine named: $hidden")

        // A found shrine is no spoiler, as in the list.
        val found = catalog.shrines.first { it.region == "Hebra" }
        storage.setState("normal_1", found.id, DiscoveryState.FOUND)
        assertEquals("Complete ${found.name} Shrine, found in Hebra", ShrineProgressProvider(storage).on().next)
    }

    @Test
    fun spiritOrbsCountBeastHeartsFromTheSameSaveFile() = runTest {
        val catalog = ShrineRepository.load(game)
        val shrines = ShrineStorage(dataStore, game)
        val mainQuest = MainQuestStorage(dataStore, game)
        catalog.base.take(9).forEach { shrines.setState("normal_1", it.id, DiscoveryState.COMPLETED) }
        shrines.setBought("normal_1", hearts = 1, stamina = 0)
        mainQuest.setDone("normal_1", MainQuestSection.DIVINE_BEASTS, "vah_ruta", true)
        mainQuest.setDone("normal_2", MainQuestSection.DIVINE_BEASTS, "vah_medoh", true)

        val summary = SpiritOrbProgressProvider(shrines, mainQuest).on()
        assertEquals(1, summary.done)
        assertEquals(31, summary.total, "124 orbs with the DLC shown")
        assertEquals("5 hearts · 1 of 3 stamina wheels", summary.secondary, "3 + Vah Ruta + 1 bought")
        assertEquals("Pray at a Goddess Statue: 1 upgrade ready to buy", summary.next, "9 orbs - 4 spent = 5 in hand")
        assertNull(summary.mapItems)

        shrines.setBought("normal_1", hearts = 2, stamina = 2)
        assertEquals("More upgrades entered than the orbs earned pay for; check the Spirit Orbs card", SpiritOrbProgressProvider(shrines, mainQuest).on().next)
    }

    @Test
    fun koroksCountSeedsAndHestu() = runTest {
        val catalog = KorokRepository.load(game)
        val storage = KorokStorage(dataStore, game)
        catalog.koroks.take(5).forEach { storage.setState("normal_1", it.id, DiscoveryState.COMPLETED) }
        storage.setBought("normal_1", "weapon", 2)

        val summary = KorokProgressProvider(storage).on()
        assertEquals(5, summary.done)
        assertEquals(900, summary.total)
        assertEquals("Found 5 · Hestu upgrades 2 / 35", summary.secondary)
        assertEquals(ItemCount("Korok seeds", 5, 900), summary.mapItems)
        // 5 seeds - 3 spent = 2 in hand: the bow's and the shield's first upgrades cost 1 each.
        assertEquals("Visit Hestu: your seeds buy 2 upgrades", summary.next)
    }

    @Test
    fun korokSuggestionNamesNoPlaceWhileSpoilersAreHidden() = runTest {
        val storage = KorokStorage(dataStore, game)
        val shown = KorokProgressProvider(storage).on().next
        assertNotNull(shown)
        assertTrue(" near " in shown, shown)

        storage.updatePrefs { it.copy(hideUndiscovered = true) }
        val hidden = KorokProgressProvider(storage).on().next
        assertNotNull(hidden)
        assertFalse(" near " in hidden, hidden)
        assertTrue(hidden.endsWith("Tower region"), hidden)
    }

    @Test
    fun compendiumCountsTheSaveFilesOwnMode() = runTest {
        val catalog = CompendiumRepository.load(game)
        val storage = CompendiumStorage(dataStore, game)
        val masterOnly = catalog.entries.first { !it.isIn(masterMode = false) }
        val both = catalog.entriesFor(file1).take(2).map { it.id }
        (both + masterOnly.id).forEach {
            storage.setPhotographed("normal_1", it, true)
            storage.setPhotographed("master", it, true)
        }

        val normal = CompendiumProgressProvider(storage).on()
        assertEquals(2, normal.done, "a Master Mode entry doesn't count on a Normal file")
        assertEquals(catalog.entriesFor(file1).size, normal.total)
        val masterSummary = CompendiumProgressProvider(storage).on("master")
        assertEquals(3, masterSummary.done)
        assertEquals(catalog.entriesFor(master).size, masterSummary.total)
        assertEquals(5, masterSummary.total - normal.total)
        assertNotNull(normal.next)
        assertTrue(normal.next!!.startsWith("Photograph "))
    }

    @Test
    fun mainQuestAddsUpItsThreeChecklists() = runTest {
        val catalog = MainQuestRepository.load(game)
        val storage = MainQuestStorage(dataStore, game)
        catalog.towers.take(2).forEach { storage.setDone("normal_1", MainQuestSection.TOWERS, it.id, true) }
        storage.setDone("normal_1", MainQuestSection.DIVINE_BEASTS, catalog.divineBeasts[0].id, true)
        catalog.memories.take(3).forEach { storage.setDone("normal_1", MainQuestSection.MEMORIES, it.id, true) }

        val summary = MainQuestProgressProvider(storage).on()
        assertEquals(6, summary.done)
        assertEquals(15 + 4 + 18, summary.total)
        assertEquals("Towers 2 / 15 · Beasts 1 / 4 · Memories 3 / 18", summary.secondary)
        assertEquals(ItemCount("Divine Beasts", 1, 4), summary.mapItems)
        assertEquals("Free ${catalog.divineBeasts[1].name}: ${catalog.divineBeasts[1].quest}", summary.next)
    }

    @Test
    fun armorCountsTheVisiblePiecesAndTheFullyUpgradedOnes() = runTest {
        val catalog = ArmorRepository.load(game)
        val storage = ArmorStorage(dataStore, game)
        val upgradable = catalog.pieces.filter { it.isUpgradable && it.source == Source.BASE }
        val amiibo = catalog.pieces.first { it.source == Source.AMIIBO }
        storage.updatePieces("normal_1") {
            mapOf(
                upgradable[0].id to PieceProgress(owned = true, level = upgradable[0].maxLevel),
                upgradable[1].id to PieceProgress(owned = true, level = 1),
                amiibo.id to PieceProgress(owned = true),
            )
        }

        val all = ArmorProgressProvider(storage).on()
        assertEquals(3, all.done)
        assertEquals(catalog.pieces.size, all.total)
        assertEquals("Fully upgraded 1 / ${catalog.pieces.count { it.isUpgradable }}", all.secondary)

        storage.updatePrefs { it.copy(showAmiibo = false) }
        val noAmiibo = ArmorProgressProvider(storage).on()
        assertEquals(2, noAmiibo.done, "the armor list's filters apply")
        assertEquals(catalog.pieces.count { it.source != Source.AMIIBO }, noAmiibo.total)
    }

    @Test
    fun fairiesShowWhatTheNextFountainCosts() = runTest {
        val storage = FairyStorage(dataStore, game)
        storage.setUnlocked("normal_1", "tera", true)
        storage.setUnlocked("normal_1", "mija", true)

        val summary = FairyProgressProvider(storage).on()
        assertEquals(2, summary.done)
        assertEquals(4, summary.total)
        assertEquals("Armor upgrades up to ★2", summary.secondary)
        assertEquals("The next fountain costs 1,000 rupees", summary.next)
    }

    @Test
    fun sideQuestsCountEachTab() = runTest {
        val catalog = SideQuestRepository.load(game)
        val storage = SideQuestStorage(dataStore, game)
        catalog.ofKind(QuestKind.SIDE).take(3).forEach { storage.setState("normal_1", it.id, DiscoveryState.COMPLETED) }
        storage.setState("normal_1", catalog.ofKind(QuestKind.SHRINE).first().id, DiscoveryState.COMPLETED)
        storage.setState("normal_1", catalog.ofKind(QuestKind.DLC).first().id, DiscoveryState.FOUND)

        val summary = SideQuestProgressProvider(storage).on()
        assertEquals(4, summary.done, "a quest only started doesn't count")
        assertEquals(catalog.quests.size, summary.total)
        val (side, shrine, dlc) = listOf(QuestKind.SIDE, QuestKind.SHRINE, QuestKind.DLC).map { catalog.ofKind(it).size }
        assertEquals("Side 3 / $side · Shrine 1 / $shrine · DLC 0 / $dlc", summary.secondary)
        assertNull(summary.mapItems, "side quests don't count toward the map")
        val started = catalog.ofKind(QuestKind.DLC).first()
        assertEquals("Finish ${started.name} (${started.region})", summary.next)
    }

    @Test
    fun sideQuestSuggestionNamesNothingNotStartedWhileSpoilersAreHidden() = runTest {
        val catalog = SideQuestRepository.load(game)
        val storage = SideQuestStorage(dataStore, game)
        val shown = SideQuestProgressProvider(storage).on().next
        assertNotNull(shown)
        assertTrue(shown.startsWith("Start ") && catalog.quests.any { it.name in shown }, shown)

        storage.updatePrefs { it.copy(hideUndiscovered = true) }
        val hidden = SideQuestProgressProvider(storage).on().next
        assertNotNull(hidden)
        assertTrue(hidden.endsWith("Tower region"), hidden)
        assertFalse(catalog.quests.any { it.name in hidden || it.giver in hidden }, "no quest or giver named: $hidden")

        // A started quest is no spoiler, as in the list.
        val started = catalog.quests.first { it.region == "Hebra" }
        storage.setState("normal_1", started.id, DiscoveryState.FOUND)
        assertEquals("Finish ${started.name} (Hebra)", SideQuestProgressProvider(storage).on().next)
    }

    @Test
    fun bossesCountEverySpawnAndSuggestTheNextMedal() = runTest {
        val catalog = BossRepository.load(game)
        val storage = BossStorage(dataStore, game)
        val hinoxes = catalog.spawnsFor(file1).filter { it.kind == "hinox" }
        hinoxes.take(2).forEach { storage.setDefeated("normal_1", it.id, true) }
        storage.setState("normal_1", hinoxes[2].id, DiscoveryState.FOUND)

        val summary = BossProgressProvider(storage).on()
        assertEquals(2, summary.done, "a boss only found doesn't count")
        assertEquals(108, summary.total, "DLC titans included")
        assertEquals("Hinox 2 / 40 · Talus 0 / 41 · Molduga 0 / 5 · Lynel 0 / 22", summary.secondary)
        assertEquals("Hinox 2 / 40 · Medal of Honor: Hinox", summary.next)
        assertNull(summary.mapItems, "bosses don't count toward the map")
        assertEquals(109, BossProgressProvider(storage).on("master").total, "Master Mode's extra Lynel")

        // Lynels have no medal, so they never hold up the last line.
        catalog.spawnsFor(file1).filter { it.countsForMedal && it.kind != "lynel" }.forEach { storage.setDefeated("normal_1", it.id, true) }
        assertEquals("All Medals of Honor earned", BossProgressProvider(storage).on().next)
    }

    @Test
    fun aSaveFileWithNoDataYetShowsZeroEverywhere() = runTest {
        val providers = overviewProviders(dataStore, game)
        assertEquals(
            listOf("Armor", "Great Fairy Fountains", "Main Quest", "Shrines", "Hearts & Stamina", "Hyrule Compendium", "Korok Seeds", "Side Quests", "Overworld Bosses"),
            providers.map { it.title },
        )
        providers.forEach { provider ->
            val summary = provider.on("normal_2")
            assertEquals(0, summary.done, provider.title)
            assertTrue(summary.total > 0, provider.title)
            assertEquals(0f, summary.fraction, provider.title)
        }
    }
}
