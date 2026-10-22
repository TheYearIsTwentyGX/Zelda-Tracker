package gx.zelda.settings

import gx.zelda.core.AppSettings
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.TestMain
import gx.zelda.core.ThemeMode
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings' light/dark choice and the game-themes switch. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceSettingsTest {
    private val main = TestMain()

    @BeforeTest fun setUp() = main.setUp()

    @AfterTest fun tearDown() = main.tearDown()

    @Test
    fun aFreshInstallFollowsTheSystemWithGameThemesOn() {
        assertEquals(ThemeMode.SYSTEM, AppSettings().themeMode)
        assertTrue(AppSettings().gameThemes)
    }

    /**
     * Settings saved before these existed load with the defaults, and the open menu sections older
     * versions stored are ignored.
     */
    @Test
    fun olderSettingsLoadWithTheDefaults() {
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(AppSettings.serializer(), """{"expandedGames":["botw"]}""")
        assertEquals(ThemeMode.SYSTEM, old.themeMode)
        assertTrue(old.gameThemes)
    }

    @Test
    fun choicesPersist() = runTest {
        val dataStore = createDataStore(Files.createTempDirectory("zgx").toString())
        val vm = main.track(AppSettingsViewModel(AppSettingsStorage(dataStore)))
        backgroundScope.launch { vm.settings.collect {} }
        // Let the state load before writing (see HeartViewModelTest).
        vm.settings.filterNotNull().first()
        vm.setThemeMode(ThemeMode.DARK).join()
        vm.setGameThemes(false).join()

        val reloaded = AppSettingsStorage(dataStore).settings.first()
        assertEquals(ThemeMode.DARK, reloaded.themeMode)
        assertFalse(reloaded.gameThemes)
        val live = vm.settings.filterNotNull().first { !it.gameThemes }
        assertEquals(ThemeMode.DARK, live.themeMode)

        vm.setThemeMode(ThemeMode.LIGHT).join()
        vm.setGameThemes(true).join()
        val back = AppSettingsStorage(dataStore).settings.first()
        assertEquals(ThemeMode.LIGHT, back.themeMode)
        assertTrue(back.gameThemes)
    }
}
