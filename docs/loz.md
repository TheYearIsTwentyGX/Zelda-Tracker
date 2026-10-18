# The Legend of Zelda

Every Legend of Zelda tracker is a checklist (`docs/checklist-trackers.md`). Its data is in
`shared/src/commonMain/composeResources/files/loz/`. `LozDatasetTest` checks the counts below and
the links between files. `ChecklistDatasetsTest` checks the format, ids and `requires`.

Nothing is fetched at runtime. The JSON was compiled offline from the sources below with a script
that isn't committed (as for Echoes of Wisdom).

## Save files

**Three files** on the file-select screen (REGISTER YOUR NAME / ELIMINATION MODE), from
StrategyWiki's *Gameplay* page. That's the placeholder count `Games.kt` already had, so only the
trackers were added. The Famicom Disk System original also has three, but none of the three sources
says so for it specifically.

**The Second Quest is on the same file.** Beating the First Quest turns the file into a Second
Quest file (its icon shows Link holding a sword), and naming a new file ZELDA starts there (Zelda
Wiki, citing Nintendo Power; StrategyWiki's *Cheats*). Link starts the Second Quest again with
3 hearts. So every file has a **First Quest** and a **Second Quest** tab rather than the game
getting extra save files. Completionists count the Second Quest as part of finishing the game.

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | — | A row per checklist below (`ChecklistProgressProvider`) |
| Dungeons | `dungeons.json` | Levels 1–9 for each quest (9 + 9): entrance, what's needed to get in, boss, items, Old Man hints. Levels 1–8 give a Triforce piece; Level 9 needs all eight. |
| Items & Equipment | `items.json` | 27 items per quest (27 + 27), in a rough order of play, with where to find or buy each and what it costs |
| Heart Containers | `heart_containers.json` | 13 per quest (13 + 13): 8 from bosses (*Levels*) and 5 hidden on the *Overworld*. *First Quest hearts* and *Second Quest hearts* each run `3 + <quest>`, 3 to 16 |

**Why these.** None of the sources defines a 100% list. They agree that completion means the
items, the 13 Heart Containers and the 8 Triforce pieces, in both quests. The two bomb-bag upgrades
are the only other things guides list as objectives (StrategyWiki's dungeon objectives), so they're
rows of Items & Equipment.

Not tracked:
- **Secret Rupee caves** ("It's a secret to everybody"), **door repairs**, **Money Making Games**,
  **shops** and **hint caves.** They're reference lists in the guides, not goals. Most can be visited
  again and have no lasting effect. StrategyWiki counts 14 secret-Rupee caves in the First Quest;
  no other source gives a count.
- **Maps, Compasses and small keys**, one set per level.
- **The Clock**, a random drop, and **Rupees, hearts and fairies.**
- **Challenge runs** (no sword, fewest hearts), which none of the sources covers.

**Missable:** only the 8 "Take any one you want" caves (four per quest). The Old Man offers a Heart
Container or a Red Water of Life, and taking the potion loses that container for good (Zelda Wiki,
*Heart Container*). `LozDatasetTest` checks nothing else is flagged.

## Screens

The game has no named regions, so there are none in the files (Heart Containers uses *Levels* and
*Overworld* to split boss containers from hidden ones). Each item's `area` names the landmark and
the screen. Screens are a grid of 16 columns, **A–P from west to east**, and 8 rows, **1–8 from
north to south**; the start screen is **H8**. The sources don't use one grid:
- Zelda Dungeon gives directions from the start screen ("right 4, up 5");
- StrategyWiki splits the map into eight sections with their own tables.

Both were converted to this grid. Every level, Heart Container and overworld item landed on the
same screen by both methods, except the one Second Quest container below. The grid is ours, so it
appears in parentheses after the landmark rather than on its own.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), read through the MediaWiki API: *Level-1 (The Legend of Zelda)*
  to *Level-8*, *Level-9*, *Second Quest*, *Heart Container*, *Permanently Missable Items*,
  *Merchant*, *Food*, *Letter*, *Ladder*, *White Sword*, *Magical Sword*, *Power Bracelet*,
  *Blue Ring*, *Graveyard*, *Death Mountain*, *Money Making Game* and *Bomb Bag*.
- **Zelda Dungeon** (zeldadungeon.net), read from Wayback Machine snapshots (2024–2026) because the
  site blocks scripted requests: its wiki's *The Legend of Zelda Heart Containers*, *Items* and
  *Dungeons*, and its First Quest walkthrough (*The Gathering*, *Level 1: The Eagle* to *Level 9*).
  Its Second Quest dungeon page, *Secret Rupees* and *Door Repairs* couldn't be read.
- **StrategyWiki**, read through its API: *Items*, *Gameplay*, *Cheats*, *Overworld* (Quest 1 and
  Quest 2, and the per-screen tables *Hyrule 1–8*), and *Underworld* for every level of both quests.

The level names (the Eagle, the Moon, the Manji, the Snake, the Lizard, the Dragon, the Demon, the
Lion, Death Mountain) are Zelda Dungeon's titles and Zelda Wiki's "also known as" names. The Second
Quest levels have no names; their map shapes are in `notes`. Only Zelda Wiki gives them: Levels 1–5
spell E, A, L, D, Z (ZELDA scrambled).

### Counts

| | Zelda Wiki | Zelda Dungeon | StrategyWiki |
|---|---|---|---|
| Levels per quest | 9 | 9 | 9 |
| Heart Containers per quest | 3 + 8 + 5 = 16 | 13 (8 bosses, 5 hidden) | the same 5 overworld spots |
| Level 9 Heart Container | none | none | none |
| Bomb upgrades | 2 per quest | 2 (First Quest) | 2 per quest |
| White Sword | 5 hearts | 5 | 5 (one walkthrough page says 6) |
| Magical Sword | 12 hearts | 12 | 12 |

## Discrepancies and how they were settled

**Levels**
- **Lost Hills (First Quest Level 5).** Zelda Wiki and Zelda Dungeon's walkthrough say walk up four
  screens in a row; Zelda Dungeon's dungeon list says five; StrategyWiki says "keep climbing". Four
  is used and the other count is in the level's `notes`.
- **Second Quest Level 3's entrance.** Zelda Wiki says it's under a pond on a green hill in western
  Hyrule. StrategyWiki and Zelda Dungeon put it where the First Quest's Level 2 was (M4), and
  StrategyWiki lists no prerequisites. M4 is used, with Zelda Wiki's version in `notes`.
- **The hungry blocker** in First Quest Level 7 and Second Quest Level 8 is a Goriya on Zelda Wiki
  and Zelda Dungeon. StrategyWiki calls it a Moblin (and a Goriya elsewhere on the Level 7 page).
  Goriya is used.
- **Old Men who take a Heart Container** in the Second Quest: Zelda Wiki says one in Level 4 and two
  in Level 7, Zelda Dungeon mentions only Level 7's two, and StrategyWiki one in each. Zelda Wiki's
  three is used in the section note and the level rows.
- **Old Man hints** are quoted where two sources give the same words. Level 8's is only on
  StrategyWiki, so it's left out.

**Heart Containers**
- **Second Quest Death Mountain cave.** Everyone agrees it's a Recorder staircase on Death
  Mountain. StrategyWiki's table puts it one screen east of Spectacle Rock (G1); following Zelda
  Dungeon's directions ends at I1. No third source gave a screen, so `area` says "near Spectacle
  Rock" and `notes` gives both. **Not verified in game.**
- **Second Quest Graveyard cave.** Zelda Dungeon says it needs the Power Bracelet; StrategyWiki
  just has Link push the gravestone. Both are in `notes`.

**Items**
- **White Sword:** 5 Heart Containers in every source except one StrategyWiki walkthrough page (6).
  5 is used.
- **The Second Quest's Letter and Magical Sword** are under boulders at B2 and J1 (StrategyWiki;
  Zelda Wiki agrees on the sword). Zelda Dungeon only says the Letter has moved.
- **Names.** The files use the in-game names: Stepladder (Zelda Wiki's *Ladder*), Recorder
  (Zelda Dungeon's *Whistle*), Magical Rod (Zelda Dungeon's and StrategyWiki's *Magical Wand*),
  Water of Life (Zelda Dungeon's *Life Potion* and *2nd Potion*), Food (StrategyWiki's *Monster
  Bait*).
