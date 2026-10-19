package gx.zelda.shrines.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.DiscoverySnapshot
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Game
import gx.zelda.core.JsonStore
import gx.zelda.core.activeSaveFileStore
import gx.zelda.shrines.model.ShrinePrefs
import gx.zelda.shrines.model.ShrineProgress
import gx.zelda.shrines.model.ShrineSnapshot
import gx.zelda.shrines.model.ShrineState
import gx.zelda.shrines.model.UpgradeKind
import kotlinx.coroutines.flow.Flow

/**
 * Persistence for a game's shrine tracker: found and completed shrines, Lightroots (TotK) and
 * Spirit Orb purchases per save file, plus viewing preferences shared across files. The active
 * save file is the one the game's other trackers use too.
 */
class ShrineStorage(dataStore: DataStore<Preferences>, val game: Game) {
    private val progressStores = game.saveFiles.associate { file ->
        file.id to JsonStore(dataStore, "${game.id}_shrines@${file.id}", ShrineProgress.serializer(), ShrineProgress())
    }
    private val prefsStore = JsonStore(dataStore, "${game.id}_shrines_prefs", ShrinePrefs.serializer(), ShrinePrefs())
    private val activeStore = activeSaveFileStore(dataStore, game)

    val activeSaveFile: Flow<String> = activeStore.data
    val prefs: Flow<ShrinePrefs> = prefsStore.data

    fun progress(saveFileId: String): Flow<ShrineProgress> = store(saveFileId).data

    suspend fun setActiveSaveFile(id: String) = activeStore.update { game.saveFile(id).id }

    suspend fun setState(saveFileId: String, shrineId: String, state: ShrineState) =
        updateProgress(saveFileId) { it.withState(shrineId, state) }

    /** Unticking a completed shrine leaves it found. */
    suspend fun setCompleted(saveFileId: String, shrineId: String, completed: Boolean) =
        setState(saveFileId, shrineId, if (completed) ShrineState.COMPLETED else ShrineState.FOUND)

    /** Purchases are stored as entered, never below zero. */
    suspend fun setBought(saveFileId: String, hearts: Int, stamina: Int) =
        updateProgress(saveFileId) { it.copy(heartsBought = hearts.coerceAtLeast(0), staminaBought = stamina.coerceAtLeast(0)) }

    /** Entered by hand, never below zero. */
    suspend fun setBossHearts(saveFileId: String, hearts: Int) =
        updateProgress(saveFileId) { it.copy(bossHearts = hearts.coerceAtLeast(0)) }

    suspend fun setFreeUpgrade(saveFileId: String, kind: UpgradeKind?) =
        updateProgress(saveFileId) { it.copy(freeUpgrade = kind) }

    suspend fun setLightrootState(saveFileId: String, lightrootId: String, state: DiscoveryState) =
        updateProgress(saveFileId) { it.withLightrootState(lightrootId, state) }

    /**
     * Completes every one of [shrineIds], or clears them back to found, in one write. Returns
     * what it replaced, for [restore].
     */
    suspend fun setAllCompleted(saveFileId: String, shrineIds: Collection<String>, completed: Boolean): ShrineSnapshot {
        val before = store(saveFileId).swap { it.withAll(shrineIds, completed) }
        return ShrineSnapshot(shrines = DiscoverySnapshot.of(before, shrineIds))
    }

    /** The same for Lightroots: lit, or cleared back to found. */
    suspend fun setAllLit(saveFileId: String, lightrootIds: Collection<String>, lit: Boolean): ShrineSnapshot {
        val before = store(saveFileId).swap { it.copy(lightroots = it.lightroots.withAll(lightrootIds, lit)) }
        return ShrineSnapshot(lightroots = DiscoverySnapshot.of(before.lightroots, lightrootIds))
    }

    /** Puts back what [setAllCompleted] or [setAllLit] replaced, in one write; other ids keep their state. */
    suspend fun restore(saveFileId: String, snapshot: ShrineSnapshot) = updateProgress(saveFileId) { it.restored(snapshot) }

    suspend fun updateProgress(saveFileId: String, transform: (ShrineProgress) -> ShrineProgress) =
        store(saveFileId).update(transform)

    suspend fun updatePrefs(transform: (ShrinePrefs) -> ShrinePrefs) = prefsStore.update(transform)

    private fun store(saveFileId: String): JsonStore<ShrineProgress> = progressStores.getValue(game.saveFile(saveFileId).id)
}
