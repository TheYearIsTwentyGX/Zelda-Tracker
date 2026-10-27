package gx.zelda.art

import gx.zelda.adventure.data.AdventureRepository
import gx.zelda.armor.data.ArmorRepository
import gx.zelda.bossrush.data.BossRushRepository
import gx.zelda.core.Game
import gx.zelda.core.TrackerKind
import gx.zelda.map.model.MarkerIcon
import gx.zelda.map.ui.artId
import gx.zelda.upgrades.data.UpgradeRepository

/**
 * Every asset id the app draws, by game, read from the same datasets the screens use. Settings
 * measures an art pack's coverage against it ("BotW 412/412"), and tests check that the app's own
 * art covers all of it.
 */
object ArtCatalog {
    /** Thrill Digger's seven pictures (`ss/thrill/<name>`). */
    val THRILL = listOf("green", "blue", "red", "silver", "gold", "rupoor", "bomb")

    /** Hyrule Warriors' Adventure Mode reward icons (`hwde/reward/<name>`). */
    val HWDE_REWARDS = listOf("heart_container", "heart_piece", "skulltula")

    /** The ids [game] uses; empty for a game that draws no art. */
    suspend fun ids(game: Game): List<String> = when (game.id) {
        "botw" -> markers(game) +
            ArmorRepository.load(game).pieces.map { ArtIds.armor(game.id, it.id) } +
            ArtIds.BOTW_WORLD
        "eow" -> markers(game) + ArtIds.EOW_HYRULE
        "totk" -> ArmorRepository.load(game).pieces.map { ArtIds.armor(game.id, it.id) }
        "ss" -> {
            val upgrades = UpgradeRepository.load(game)
            upgrades.items.flatMap { item -> item.levels.map { ArtIds.ssUpgrade(it.id) } }.distinct() +
                upgrades.materials.map { ArtIds.ssMaterial(it.id) } +
                BossRushRepository.load(game).catalog.bosses.map { ArtIds.ssBoss(it.icon) }.distinct() +
                THRILL.map(ArtIds::thrill)
        }
        "hwde" -> {
            val maps = game.trackers.filter { it.kind == TrackerKind.ADVENTURE_MAP }.mapNotNull { it.dataFile }.map { AdventureRepository.load(it) }
            maps.flatMap { m -> m.cards.map { ArtIds.hwdeCard(it.id) } }.distinct() +
                HWDE_REWARDS.map(ArtIds::hwdeReward) +
                maps.filter { it.art != null }.map { ArtIds.hwdeMap(it.id) }
        }
        else -> emptyList()
    }

    private fun markers(game: Game) = MarkerIcon.entries.map { it.artId }.filter { ArtIds.game(it) == game.id }
}
