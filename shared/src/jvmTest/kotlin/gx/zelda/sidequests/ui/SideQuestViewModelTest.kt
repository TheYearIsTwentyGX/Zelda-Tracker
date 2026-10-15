package gx.zelda.sidequests.ui

import gx.zelda.core.Games
import gx.zelda.core.createDataStore
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.ShrineState
import gx.zelda.sidequests.data.SideQuestData
import gx.zelda.sidequests.data.SideQuestRepository
import gx.zelda.sidequests.data.SideQuestStorage
import gx.zelda.sidequests.model.QuestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SideQuestViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun newDataStore() = createDataStore(Files.createTempDirectory("zgx").toString())

    private val catalog = SideQuestRepository.parse(
        """{"regions":["Dueling Peaks"],"quests":[{"id":"the_stolen_heirloom","name":"The Stolen Heirloom","kind":"shrine",""" +
            """"region":"Dueling Peaks","giver":"Impa","giverLocation":"Kakariko Village","objective":"Find it",""" +
            """"reward":"Access to the Lakna Rokee shrine","shrineId":"lakna_rokee"}]}""",
    )

    @Test
    fun malformedDatasetReportsLoadFailure() = runTest {
        val vm = SideQuestViewModel(SideQuestStorage(newDataStore(), Games.BOTW)) {
            SideQuestData(SideQuestRepository.parse("""{"regions": "not a list"}"""))
        }
        assertTrue(vm.loadFailed.value)
        assertNull(vm.state.value)
    }

    @Test
    fun shrineQuestShowsTheShrineTrackersStateForTheActiveFile() = runTest {
        val dataStore = newDataStore()
        val shrines = ShrineStorage(dataStore, Games.BOTW)
        val vm = SideQuestViewModel(SideQuestStorage(dataStore, Games.BOTW), shrines) { SideQuestData(catalog) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertFalse(vm.loadFailed.value)
        val quest = catalog.quests.single()

        assertEquals(ShrineState.UNDISCOVERED, vm.state.filterNotNull().first().shrineState(quest))

        shrines.setState("normal_2", "lakna_rokee", ShrineState.COMPLETED)
        assertEquals(ShrineState.UNDISCOVERED, vm.state.filterNotNull().first().shrineState(quest), "another save file")

        vm.setSaveFile("normal_2")
        assertEquals(ShrineState.COMPLETED, vm.state.filterNotNull().first { it.saveFile.id == "normal_2" }.shrineState(quest))

        vm.setState(quest.id, QuestState.COMPLETED)
        val now = vm.state.filterNotNull().first { quest.id in it.progress.completed }
        assertEquals(1, now.completedCount(quest.kind))
        assertEquals(ShrineState.COMPLETED, now.shrineState(quest))
        assertEquals(emptySet(), shrines.progress("normal_1").first().completed, "the quest never writes to the shrine tracker")
    }
}
