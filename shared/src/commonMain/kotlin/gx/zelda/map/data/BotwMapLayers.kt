package gx.zelda.map.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.core.DiscoveryState
import gx.zelda.core.Game
import gx.zelda.core.TrackerKind
import gx.zelda.koroks.data.KorokRepository
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.koroks.model.Korok
import gx.zelda.koroks.model.KorokData
import gx.zelda.mainquest.data.MainQuestRepository
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.mainquest.model.MainQuestSection
import gx.zelda.mainquest.model.SheikahTower
import gx.zelda.map.model.MapEntry
import gx.zelda.map.model.MapLayer
import gx.zelda.map.model.MarkerKind
import gx.zelda.shrines.data.ShrineRepository
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.model.Shrine
import gx.zelda.shrines.scan.ShrineMapRepository
import gx.zelda.shrines.scan.WorldPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Layer ids, which App.kt also uses to open an entry in its tracker. */
object MapLayers {
    const val SHRINES = "shrines"
    const val KOROKS = "koroks"
    const val TOWERS = "towers"
}

/** The layers [game] has trackers for, in the order the chips show them. */
fun botwMapLayers(dataStore: DataStore<Preferences>, game: Game): List<MapLayerProvider> {
    val kinds = game.trackers.map { it.kind }
    return buildList {
        if (TrackerKind.SHRINES in kinds) add(ShrineLayer(ShrineStorage(dataStore, game)))
        if (TrackerKind.KOROKS in kinds) add(KorokLayer(KorokStorage(dataStore, game)))
        if (TrackerKind.MAIN_QUEST in kinds) add(TowerLayer(MainQuestStorage(dataStore, game)))
    }
}

/** Shrines at their `shrine_map.json` positions, in the shrine tracker's three states. */
class ShrineLayer(
    private val storage: ShrineStorage,
    load: suspend () -> List<Pair<Shrine, WorldPoint>> = {
        val positions = ShrineMapRepository.load(storage.game).shrines
        ShrineRepository.load(storage.game).shrines.mapNotNull { s -> positions[s.id]?.let { s to it } }
    },
) : MapLayerProvider {
    override val layer = MapLayer(
        id = MapLayers.SHRINES,
        title = "Shrines",
        noun = "shrine",
        stateLabels = linkedMapOf(
            DiscoveryState.UNDISCOVERED to "Undiscovered",
            DiscoveryState.FOUND to "Found",
            DiscoveryState.COMPLETED to "Completed",
        ),
        kind = MarkerKind.SHRINE,
        spoilerSetting = "Hide undiscovered shrines",
    )

    private val shrines = LoadOnce(load)

    override fun entries(saveFileId: String): Flow<List<MapEntry>> =
        shrines.withProgress(storage.progress(saveFileId)) { (shrine, at), progress ->
            MapEntry(
                id = shrine.id,
                name = "${shrine.name} Shrine",
                subtitle = if (shrine.dlc) "${shrine.trial} · The Champions' Ballad" else shrine.trial,
                region = shrine.region,
                world = at,
                state = progress.state(shrine.id),
                dlc = shrine.dlc,
            )
        }

    override val hideUndiscovered: Flow<Boolean> = storage.prefs.map { it.hideUndiscovered }

    override suspend fun setState(saveFileId: String, id: String, state: DiscoveryState) =
        storage.setState(saveFileId, id, state)
}

/** Every Korok Seed at its own position, in the Korok tracker's three states. */
class KorokLayer(
    private val storage: KorokStorage,
    load: suspend () -> List<Pair<Korok, String>> = {
        val data: KorokData = KorokRepository.loadData(storage.game)
        data.catalog.koroks.map { it to data.typeOf(it).name }
    },
) : MapLayerProvider {
    override val layer = MapLayer(
        id = MapLayers.KOROKS,
        title = "Koroks",
        noun = "Korok",
        stateLabels = linkedMapOf(
            DiscoveryState.UNDISCOVERED to "Undiscovered",
            DiscoveryState.FOUND to "Found",
            DiscoveryState.COMPLETED to "Collected",
        ),
        kind = MarkerKind.KOROK,
        clustered = true,
        spoilerSetting = "Hide undiscovered Koroks",
    )

    private val koroks = LoadOnce(load)

    override fun entries(saveFileId: String): Flow<List<MapEntry>> =
        koroks.withProgress(storage.progress(saveFileId)) { (korok, typeName), progress ->
            MapEntry(
                id = korok.id,
                name = typeName,
                subtitle = "Korok ${korok.id} · near ${korok.nearestPlace}",
                region = korok.region,
                world = WorldPoint(korok.x, korok.z),
                state = progress.state(korok.id),
            )
        }

    override val hideUndiscovered: Flow<Boolean> = storage.prefs.map { it.hideUndiscovered }

    override suspend fun setState(saveFileId: String, id: String, state: DiscoveryState) =
        storage.setState(saveFileId, id, state)
}

/**
 * Sheikah Towers, activated or not, at their `shrine_map.json` positions. Towers stand out on the
 * horizon, so the layer has no spoiler setting.
 */
class TowerLayer(
    private val storage: MainQuestStorage,
    load: suspend () -> List<Pair<SheikahTower, WorldPoint>> = {
        val positions = ShrineMapRepository.loadTowerPositions(storage.game)
        MainQuestRepository.load(storage.game).towers.mapNotNull { t -> positions[t.name]?.let { t to it } }
    },
) : MapLayerProvider {
    override val layer = MapLayer(
        id = MapLayers.TOWERS,
        title = "Towers",
        noun = "tower",
        stateLabels = linkedMapOf(
            DiscoveryState.UNDISCOVERED to "Not activated",
            DiscoveryState.COMPLETED to "Activated",
        ),
        kind = MarkerKind.TOWER,
    )

    private val towers = LoadOnce(load)

    override fun entries(saveFileId: String): Flow<List<MapEntry>> =
        towers.withProgress(storage.progress(saveFileId)) { (tower, at), progress ->
            MapEntry(
                id = tower.id,
                name = tower.name,
                subtitle = tower.location,
                region = tower.region,
                world = at,
                state = if (tower.id in progress.towers) DiscoveryState.COMPLETED else DiscoveryState.UNDISCOVERED,
            )
        }

    override val hideUndiscovered: Flow<Boolean> = flowOf(false)

    override suspend fun setState(saveFileId: String, id: String, state: DiscoveryState) =
        storage.setDone(saveFileId, MainQuestSection.TOWERS, id, state == DiscoveryState.COMPLETED)
}
