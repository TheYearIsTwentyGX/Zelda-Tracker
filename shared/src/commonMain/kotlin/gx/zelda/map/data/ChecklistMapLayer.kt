package gx.zelda.map.data

import gx.zelda.checklist.data.ChecklistRepository
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.ChecklistProgress
import gx.zelda.checklist.model.StoryGate
import gx.zelda.core.CachedLoad
import gx.zelda.core.DiscoveryState
import gx.zelda.map.model.MapEntry
import gx.zelda.map.model.MapLayer
import gx.zelda.map.model.MarkerIcon
import gx.zelda.map.model.MarkerKind
import gx.zelda.resources.Res
import gx.zelda.shrines.scan.WorldPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A checklist shown as a map layer: the layer's chip and sheet wording, and which of the file's
 * sections are places, with each one's icon. Sections left out (rewards, upgrades) aren't places.
 */
data class ChecklistMapSource(
    /** The checklist, as its tracker names it: `eow/heart_pieces.json`. */
    val dataFile: String,
    val title: String,
    val noun: String,
    /** What a spoiler-hidden marker is called, as the checklist's hidden rows say it. */
    val hiddenLabel: String,
    val sections: Map<String, MarkerIcon>,
    val notDone: String = "Not collected",
    val done: String = "Collected",
    /** How many things one item's place holds, for the marker's badge: a chest of Might Crystals. */
    val count: (ChecklistItem) -> Int = { 1 },
) {
    /** The layer's id, which is also the file's stem, so "Open in tracker" knows the checklist. */
    val layerId: String get() = dataFile.substringAfterLast('/').removeSuffix(".json")
}

/** A game's `map/markers.json`: where each checklist item is, in world units, and anything that couldn't be placed. */
@Serializable
data class ChecklistMarkers(val markers: List<Marker>, val unplaced: List<Unplaced> = emptyList()) {
    @Serializable
    data class Marker(val file: String, val item: String, val key: String, val x: Double, val z: Double)

    @Serializable
    data class Unplaced(val file: String, val item: String, val reason: String)

    /** Positions of [file]'s items (by file stem), by item id. */
    fun positions(file: String): Map<String, WorldPoint> =
        markers.filter { it.file == file }.associate { it.item to WorldPoint(it.x, it.z) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ChecklistMarkers = json.decodeFromString(serializer(), text)

        suspend fun load(path: String): ChecklistMarkers = parse(Res.readBytes(path).decodeToString())
    }
}

/**
 * One checklist's places on the map, ticked exactly as in the list: a marker reads the item's level
 * from [ChecklistStorage] (so `sameAs` links and mark-all show at once) and writes it with
 * [ChecklistStorage.setDone]. Story gating leaves out what the list leaves out, and the list's
 * "Hide unchecked" is the layer's spoiler setting. Items without a position aren't drawn.
 */
class ChecklistMapLayer(
    private val source: ChecklistMapSource,
    private val storage: ChecklistStorage,
    private val gate: (String) -> Flow<StoryGate>,
    load: suspend () -> List<Placed>,
) : MapLayerProvider {
    /** An item of a mapped section, at its place, with that section's icon. */
    class Placed(val item: ChecklistItem, val at: WorldPoint, val icon: MarkerIcon, val sectionTitle: String)

    override val layer = MapLayer(
        id = source.layerId,
        title = source.title,
        noun = source.noun,
        stateLabels = linkedMapOf(DiscoveryState.UNDISCOVERED to source.notDone, DiscoveryState.COMPLETED to source.done),
        kind = MarkerKind.CHECKLIST,
        spoilerSetting = "Hide unchecked",
        icon = source.sections.values.firstOrNull(),
        hiddenLabel = source.hiddenLabel,
    )

    private val placed = LoadOnce(load)

    /** The placed items and the gate on [saveFileId], with that file's progress. */
    private fun withProgress(saveFileId: String): Flow<Triple<List<Placed>, ChecklistProgress, StoryGate>> = flow {
        val items = placed.get()
        emitAll(combine(storage.progress(saveFileId), gate(saveFileId)) { progress, gate -> Triple(items, progress, gate) })
    }

    override fun entries(saveFileId: String): Flow<List<MapEntry>> = withProgress(saveFileId).map { (items, progress, gate) ->
        items.filterNot { gate.isLocked(it.item, progress) }.map { entry(it, progress) }
    }

    override fun locked(saveFileId: String): Flow<Int> = withProgress(saveFileId).map { (items, progress, gate) ->
        items.count { gate.isLocked(it.item, progress) }
    }

    override val hideUndiscovered: Flow<Boolean> = storage.prefs.map { it.hideUnchecked }

    override suspend fun setState(saveFileId: String, id: String, state: DiscoveryState) {
        val item = placed.get().firstOrNull { it.item.id == id }?.item ?: return
        storage.setDone(saveFileId, item, state == DiscoveryState.COMPLETED)
    }

    private fun entry(placed: Placed, progress: ChecklistProgress): MapEntry {
        val item = placed.item
        return MapEntry(
            id = item.id,
            name = item.name,
            subtitle = listOfNotNull(item.area, item.howTo).joinToString("\n"),
            region = item.region ?: placed.sectionTitle,
            world = placed.at,
            state = if (progress.isDone(item)) DiscoveryState.COMPLETED else DiscoveryState.UNDISCOVERED,
            icon = placed.icon,
            count = source.count(item),
        )
    }

    companion object {
        /** [source]'s items in its mapped sections, in file order, at their positions in [markers]; unplaced items are skipped. */
        fun placedItems(source: ChecklistMapSource, markers: CachedLoad<ChecklistMarkers>): suspend () -> List<Placed> = {
            val catalog = ChecklistRepository.load(source.dataFile)
            val positions = markers.get().positions(source.layerId)
            catalog.sections.filter { it.id in source.sections }.flatMap { section ->
                section.items.mapNotNull { item ->
                    positions[item.id]?.let { Placed(item, it, source.sections.getValue(section.id), section.title) }
                }
            }
        }
    }
}
