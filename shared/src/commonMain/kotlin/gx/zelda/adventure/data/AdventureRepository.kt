package gx.zelda.adventure.data

import gx.zelda.adventure.model.AdventureMap
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object AdventureRepository {
    /** A tracker's `dataFile` is relative to the bundled `files/` folder, e.g. `hwde/adventure_map.json`. */
    fun path(dataFile: String): String = "files/$dataFile"

    private val lenient = Json { ignoreUnknownKeys = true }
    private val strict = Json

    /** Unknown keys are ignored in the app; [strict] rejects them, which the dataset tests use. */
    fun parse(text: String, strict: Boolean = false): AdventureMap =
        (if (strict) this.strict else lenient).decodeFromString(AdventureMap.serializer(), text)

    suspend fun load(dataFile: String, strict: Boolean = false): AdventureMap =
        parse(Res.readBytes(path(dataFile)).decodeToString(), strict)
}
