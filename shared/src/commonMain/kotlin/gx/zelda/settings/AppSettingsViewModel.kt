package gx.zelda.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gx.zelda.core.AppSettings
import gx.zelda.core.AppSettingsStorage
import gx.zelda.core.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app-wide settings behind the home screen, the menu and the Settings screen. One instance is
 * shared by all three, so a game turned off in Settings leaves the other two at once.
 */
class AppSettingsViewModel(private val storage: AppSettingsStorage) : ViewModel() {
    /** Null until the stored settings have loaded, so hidden games never flash up first. */
    val settings: StateFlow<AppSettings?> = storage.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Each write returns its job, so a test can wait for it. Hiding the last game shown is ignored. */
    fun setShown(gameId: String, shown: Boolean): Job = viewModelScope.launch { storage.setShown(gameId, shown) }

    fun setExpanded(gameId: String, expanded: Boolean): Job = viewModelScope.launch { storage.setExpanded(gameId, expanded) }

    fun setThemeMode(mode: ThemeMode): Job = viewModelScope.launch { storage.setThemeMode(mode) }

    fun setGameThemes(on: Boolean): Job = viewModelScope.launch { storage.setGameThemes(on) }
}
