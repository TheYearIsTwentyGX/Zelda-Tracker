package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object ChecklistRepository {
    /** A tracker's `dataFile` is relative to the bundled `files/` folder, e.g. `eow/echoes.json`. */
    fun path(dataFile: String): String = "files/$dataFile"

    private val lenient = Json { ignoreUnknownKeys = true }
    private val strict = Json

    /**
     * Unknown keys are ignored in the app, so a newer file still loads; [strict] rejects them, which
     * the dataset tests use to catch a misspelt field.
     */
    fun parse(text: String, strict: Boolean = false): ChecklistCatalog =
        (if (strict) this.strict else lenient).decodeFromString(ChecklistCatalog.serializer(), text)

    suspend fun load(dataFile: String, strict: Boolean = false): ChecklistCatalog =
        parse(Res.readBytes(path(dataFile)).decodeToString(), strict)
}
