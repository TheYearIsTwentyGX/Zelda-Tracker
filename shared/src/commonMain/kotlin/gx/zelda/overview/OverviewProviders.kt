package gx.zelda.overview

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.armor.data.ArmorProgressProvider
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.bosses.data.BossProgressProvider
import gx.zelda.bosses.data.BossStorage
import gx.zelda.compendium.data.CompendiumProgressProvider
import gx.zelda.compendium.data.CompendiumStorage
import gx.zelda.core.Game
import gx.zelda.core.ProgressProvider
import gx.zelda.core.TrackerKind
import gx.zelda.fairies.data.FairyProgressProvider
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.figurines.data.FigurineProgressProvider
import gx.zelda.figurines.data.FigurineStorage
import gx.zelda.koroks.data.KorokProgressProvider
import gx.zelda.koroks.data.KorokStorage
import gx.zelda.mainquest.data.MainQuestProgressProvider
import gx.zelda.mainquest.data.MainQuestStorage
import gx.zelda.shrines.data.ShrineProgressProvider
import gx.zelda.shrines.data.ShrineStorage
import gx.zelda.shrines.data.SpiritOrbProgressProvider
import gx.zelda.shrines.data.shrineCatalogLoad
import gx.zelda.sidequests.data.SideQuestProgressProvider
import gx.zelda.sidequests.data.SideQuestStorage

/**
 * The Progress Overview's rows for [game], in the order its trackers are listed.
 *
 * To add a tracker, give its [TrackerKind] a branch returning its [ProgressProvider]s (see that
 * interface's KDoc); `emptyList()` leaves it out of the overview.
 */
fun overviewProviders(dataStore: DataStore<Preferences>, game: Game): List<ProgressProvider> {
    val kinds = game.trackers.map { it.kind }
    return kinds.flatMap { kind ->
        when (kind) {
            TrackerKind.ARMOR -> listOf(ArmorProgressProvider(ArmorStorage(dataStore, game)))
            TrackerKind.FAIRIES -> listOf(FairyProgressProvider(FairyStorage(dataStore, game)))
            TrackerKind.MAIN_QUEST -> listOf(MainQuestProgressProvider(MainQuestStorage(dataStore, game)))
            TrackerKind.SHRINES -> {
                val storage = ShrineStorage(dataStore, game)
                val catalog = shrineCatalogLoad(storage)
                val mainQuest = MainQuestStorage(dataStore, game).takeIf { TrackerKind.MAIN_QUEST in kinds }
                listOf(ShrineProgressProvider(storage, catalog), SpiritOrbProgressProvider(storage, mainQuest, catalog))
            }
            TrackerKind.COMPENDIUM -> listOf(CompendiumProgressProvider(CompendiumStorage(dataStore, game)))
            TrackerKind.KOROKS -> listOf(KorokProgressProvider(KorokStorage(dataStore, game)))
            TrackerKind.OVERVIEW -> emptyList()
            TrackerKind.SIDE_QUESTS -> listOf(SideQuestProgressProvider(SideQuestStorage(dataStore, game)))
            TrackerKind.BOSSES -> listOf(BossProgressProvider(BossStorage(dataStore, game)))
            // The map's counts are the shrine, Korok and tower trackers', which have rows already.
            TrackerKind.MAP -> emptyList()
            TrackerKind.FIGURINES -> listOf(FigurineProgressProvider(FigurineStorage(dataStore, game)))
        }
    }
}
