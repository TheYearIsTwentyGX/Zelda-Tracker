package gx.zelda.overview

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import gx.zelda.adventure.data.AdventureProgressProvider
import gx.zelda.adventure.data.AdventureStorage
import gx.zelda.armor.data.ArmorProgressProvider
import gx.zelda.armor.data.ArmorStorage
import gx.zelda.bosses.data.BossProgressProvider
import gx.zelda.bosses.data.BossStorage
import gx.zelda.compendium.data.CompendiumProgressProvider
import gx.zelda.compendium.data.CompendiumStorage
import gx.zelda.checklist.data.ChecklistProgressProvider
import gx.zelda.checklist.data.ChecklistStorage
import gx.zelda.core.Game
import gx.zelda.core.ProgressProvider
import gx.zelda.core.TrackerKind
import gx.zelda.fairies.data.FairyProgressProvider
import gx.zelda.fairies.data.FairyStorage
import gx.zelda.hearts.data.HeartProgressProvider
import gx.zelda.hearts.data.HeartStorage
import gx.zelda.kinstones.data.KinstoneProgressProvider
import gx.zelda.kinstones.data.KinstoneStorage
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
import gx.zelda.upgrades.data.UpgradeProgressProvider
import gx.zelda.upgrades.data.UpgradeStorage

/**
 * The Progress Overview's rows for [game]: one set per game, built from the trackers that game
 * lists in `core/Games.kt`, in that order. A game that doesn't list [TrackerKind.OVERVIEW] (TotK)
 * has no overview, so it gets none; nothing is built from another game's trackers.
 *
 * To add a tracker, give its [TrackerKind] a branch returning its [ProgressProvider]s (see that
 * interface's KDoc); `emptyList()` leaves it out of the overview. A branch is shared by every game
 * with that tracker, so a provider that differs by game (Main Quest) works it out from the game's
 * own dataset.
 */
fun overviewProviders(dataStore: DataStore<Preferences>, game: Game): List<ProgressProvider> {
    val kinds = game.trackers.map { it.kind }
    if (TrackerKind.OVERVIEW !in kinds) return emptyList()
    return game.trackers.flatMap { tracker ->
        when (tracker.kind) {
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
            TrackerKind.HEART_PIECES -> listOf(HeartProgressProvider(HeartStorage(dataStore, game)))
            TrackerKind.KINSTONES -> listOf(KinstoneProgressProvider(KinstoneStorage(dataStore, game)))
            TrackerKind.FIGURINES -> listOf(FigurineProgressProvider(FigurineStorage(dataStore, game)))
            // One generic row per checklist, so a game made of checklists gets its overview for free.
            TrackerKind.CHECKLIST -> listOf(
                ChecklistProgressProvider(ChecklistStorage(dataStore, game, tracker.dataFile!!), tracker.title),
            )
            TrackerKind.ADVENTURE_MAP -> listOf(
                AdventureProgressProvider(AdventureStorage(dataStore, game, tracker.dataFile!!), tracker.title),
            )
            // A tool keeps no progress.
            TrackerKind.THRILL_DIGGER -> emptyList()
            TrackerKind.ITEM_UPGRADES -> listOf(UpgradeProgressProvider(UpgradeStorage(dataStore, game), tracker.title))
        }
    }
}
