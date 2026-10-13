package gx.zelda.botw.fairies.data

import gx.zelda.botw.fairies.model.FairyCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object FairyRepository {
    const val CATALOG_PATH = "files/botw/fairies.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): FairyCatalog = json.decodeFromString(FairyCatalog.serializer(), text)

    suspend fun load(): FairyCatalog = parse(Res.readBytes(CATALOG_PATH).decodeToString())
}
