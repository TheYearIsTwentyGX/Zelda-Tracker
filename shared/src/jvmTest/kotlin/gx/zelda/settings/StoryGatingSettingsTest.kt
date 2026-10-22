package gx.zelda.settings

import gx.zelda.core.AppSettings
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.TestMain
import gx.zelda.core.createDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings' "Hide what the story hasn't unlocked yet" switch. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryGatingSettingsTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    @Test
    fun itIsOffByDefaultAndInSettingsSavedBeforeIt() {
        assertFalse(AppSettings().storyGating)
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(AppSettings.serializer(), """{"themeMode":"DARK","gameThemes":false}""")
        assertFalse(old.storyGating)
    }

    @Test
    fun itPersists() = runTest {
        val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
        val vm = main.track(AppSettingsViewModel(AppSettingsStorage(dataStore)))
        backgroundScope.launch { vm.settings.collect {} }
        // Let the state load before writing (see HeartViewModelTest).
        vm.settings.filterNotNull().first()
        vm.setStoryGating(true).join()
        assertTrue(AppSettingsStorage(dataStore).settings.first().storyGating)
        vm.settings.filterNotNull().first { it.storyGating }

        vm.setStoryGating(false).join()
        assertFalse(AppSettingsStorage(dataStore).settings.first().storyGating)
    }
}
