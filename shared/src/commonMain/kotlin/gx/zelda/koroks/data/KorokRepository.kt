package gx.zelda.koroks.data

import gx.zelda.core.Game
import gx.zelda.koroks.model.KorokCatalog
import gx.zelda.koroks.model.KorokPuzzleTypes
import gx.zelda.resources.Res
import kotlinx.serialization.json.Json

object KorokRepository {
    /** Each game's Koroks live at `files/<game id>/koroks.json`. */
    fun catalogPath(game: Game): String = "files/${game.id}/koroks.json"

    /** Each game's puzzle types live at `files/<game id>/korok_puzzle_types.json`. */
    fun puzzleTypesPath(game: Game): String = "files/${game.id}/korok_puzzle_types.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): KorokCatalog = json.decodeFromString(KorokCatalog.serializer(), text)

    fun parsePuzzleTypes(text: String): KorokPuzzleTypes = json.decodeFromString(KorokPuzzleTypes.serializer(), text)

    suspend fun load(game: Game): KorokCatalog = parse(Res.readBytes(catalogPath(game)).decodeToString())

    suspend fun loadPuzzleTypes(game: Game): KorokPuzzleTypes =
        parsePuzzleTypes(Res.readBytes(puzzleTypesPath(game)).decodeToString())
}
