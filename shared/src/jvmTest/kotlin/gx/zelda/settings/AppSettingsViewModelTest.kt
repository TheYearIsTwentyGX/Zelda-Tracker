package gx.zelda.settings

import gx.zelda.armor.data.ArmorStorage
import gx.zelda.armor.model.PieceProgress
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.TestMain
import gx.zelda.core.comingSoonGame
import gx.zelda.core.createDataStore
import gx.zelda.core.Games
import gx.zelda.core.gamesWithComingSoon
import gx.zelda.core.gamesWithTrackers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsViewModelTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    @Test
    fun aFreshInstallShowsTheGamesWithTrackers() = runTest {
        val vm = main.track(AppSettingsViewModel(AppSettingsStorage(newDataStore())))
        backgroundScope.launchCollect(vm)
        val settings = vm.settings.filterNotNull().first()
        assertEquals(gamesWithTrackers, settings.shownGames().map { it.id })
        assertTrue(settings.expandedGames.isEmpty())
    }

    /**
     * Choices and open sections are written to the DataStore, so a new view model sees them. Uses
     * [comingSoonGame] as the game turned on, whichever real games are still coming soon.
     */
    @Test
    fun choicesPersist() = runTest {
        val dataStore = newDataStore()
        val games = gamesWithComingSoon
        val vm = main.track(AppSettingsViewModel(AppSettingsStorage(dataStore, games)))
        backgroundScope.launchCollect(vm)
        // Let the state load before writing (see HeartViewModelTest).
        vm.settings.filterNotNull().first()
        vm.setShown(comingSoonGame.id, true).join()
        vm.setShown("totk", false).join()
        vm.setExpanded("botw", true).join()

        val expected = games.filter { (!it.comingSoon || it == comingSoonGame) && it != Games.TOTK }.map { it.id }
        val reloaded = AppSettingsStorage(dataStore, games).settings.first()
        assertEquals(expected, reloaded.shownGames(games).map { it.id })
        assertEquals(setOf("botw"), reloaded.expandedGames)
        assertEquals(expected, vm.settings.filterNotNull().first { "botw" in it.expandedGames }.shownGames(games).map { it.id })
    }

    @Test
    fun theLastGameCannotBeHidden() = runTest {
        val vm = main.track(AppSettingsViewModel(AppSettingsStorage(newDataStore())))
        backgroundScope.launchCollect(vm)
        vm.settings.filterNotNull().first()
        val others = Games.all.filter { !it.comingSoon && it != Games.MC }
        others.forEach { vm.setShown(it.id, false).join() }
        vm.setShown("mc", false).join()
        val settings = vm.settings.filterNotNull().first { it.gameChoices.size == others.size }
        assertEquals(listOf("mc"), settings.shownGames().map { it.id })
        assertTrue("mc" !in settings.gameChoices)
    }

    /** Hiding a game only hides it: its trackers' progress stays where it was. */
    @Test
    fun hidingAGameKeepsItsProgress() = runTest {
        val dataStore = newDataStore()
        val armor = ArmorStorage(dataStore, Games.TOTK)
        armor.updatePieces("file_1") { it + ("hylian_hood" to PieceProgress(owned = true)) }

        val storage = AppSettingsStorage(dataStore)
        storage.setShown("totk", false)
        assertTrue(Games.TOTK !in storage.settings.first().shownGames())
        assertEquals(PieceProgress(owned = true), armor.pieces("file_1").first()["hylian_hood"])
    }

    /** [AppSettingsViewModel.settings] only updates while something collects it. */
    private fun CoroutineScope.launchCollect(vm: AppSettingsViewModel) = launch { vm.settings.collect {} }
}
