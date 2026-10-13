package gx.zelda.botw.armor.data

import gx.zelda.botw.armor.model.ArmorCatalog
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object ArmorRepository {
    const val CATALOG_PATH = "files/botw/armor.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ArmorCatalog = json.decodeFromString(ArmorCatalog.serializer(), text)

    suspend fun load(): ArmorCatalog = parse(Res.readBytes(CATALOG_PATH).decodeToString())
}
