# Zelda II: The Adventure of Link

Every Zelda II tracker is a checklist (`docs/checklist-trackers.md`). Its data is in
`shared/src/commonMain/composeResources/files/aol/`. `AolDatasetTest` checks the counts below and
the links between files. `ChecklistDatasetsTest` checks the format, ids, regions and `requires`.

Nothing is fetched at runtime. The JSON was compiled offline from the sources below with a script
that isn't committed (as for Echoes of Wisdom).

## Save files

**Three files** (StrategyWiki, *Gameplay*: "enter your name in one of the three save slots"). That's
the placeholder count `Games.kt` already had, so only the trackers were added.

**Replaying a beaten file.** After the ending, the same file starts again. Zelda Wiki's and Zelda
Dungeon's *Second Quest* pages agree on what carries over:
- **Kept:** the Attack, Magic and Life levels and every spell.
- **Collected again:** the palace items and the Heart and Magic Containers.

Only Zelda Wiki's main game page says the sword techniques carry over too. Unlike The Legend of
Zelda's Second Quest, the world is unchanged, and no source treats the replay as part of
completion, so it gets no tabs of its own. Someone replaying can untick the items and containers.

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | — | A row per checklist below (`ChecklistProgressProvider`) |
| Palaces | `palaces.json` | The 6 palaces and the Great Palace (done when the crystal is set, or the Triforce taken), and their 8 bosses with the XP each gives. The Great Palace `requires` the other six |
| Items | `items.json` | 14: the 6 palace items, the Hammer and the Magic Key, and 6 quest items (Trophy, Bagu's note, Mirror, Water of Life, fountain water, Child). The five that earn a spell name it in `reward` |
| Spells & Techniques | `spells.json` | The 8 spells, one per town, with what the wise man wants, the effect and the MP cost at Magic level 1 and 8; the 2 sword thrusts |
| Heart & Magic Containers | `containers.json` | 4 Heart Containers and 4 Magic Containers. *Life meter* `4 + heart_containers` and *Magic meter* `4 + magic_containers`, each 4 to 8 |
| Levels & Link Dolls | `levels.json` | Attack, Magic and Life as three rows of 7 tiers each (levels 2–8, with the XP each costs); the 6 Link Dolls |

**Why these.** None of Zelda Wiki, Zelda Dungeon or StrategyWiki defines a 100% list. The only
explicit one is TASVideos' *100%* category (submission #7039). It covers:
- all palaces and bosses;
- the experience level-ups;
- the inventory items, quest items and keys;
- the spells and sword techniques;
- the Heart and Magic Containers;
- the Link Dolls.

That's the set above. The Triforce is the Great Palace's `reward` rather than a row of its own.

Not tracked:
- **Point Bags.** Zelda Wiki lists 30 fixed ones that don't come back once taken. No guide treats
  them as collectibles, and TASVideos' 100% leaves them out.
- **Magic jars, fairies and the extra lives** earned every 9,000 XP after level 8, as TASVideos
  leaves them out too.
- **The Rebonack mini-bosses** in the Three-Eye Rock Palace (two of them) are in that palace's
  notes rather than the boss list.

**Missable:** only the two Link Dolls inside palaces (Three-Eye Rock and the Great Palace), which
can't be reached once the palace is finished (Zelda Dungeon's wiki). Every doll is gone for good
once taken (Zelda Wiki, Zelda Dungeon, StrategyWiki), but that's collecting it, not missing it.

## Regions

Zelda Wiki's three overworld areas, in map order: **Western Hyrule**, **Death Mountain** and
**Eastern Hyrule**. Maze Island and the Valley of Death are in Eastern Hyrule. The town, cave or
palace goes in `area`, and a file lists only the regions it uses. The levels have no place, so they
sit under their section's title.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), read through the MediaWiki API:
  - the game page, each palace page and *Magic of The Adventure of Link*;
  - *Heart Container*, *Magic Container*, *Doll*, *Experience*, *Second Quest* and *Treasure Bag*;
  - the items: *Trophy*, *Water of Life*, *Mirror*, *Child*, *Hammer*, *Magical Key*, *Crystal*;
  - the techniques: *Down Thrust*, *Jump Thrust*;
  - the bosses: *Bosses in The Adventure of Link*, *Mazura*, *Jermafenser*, *Dark Link*;
  - each spell page, and *Kasuto*, *Valley of Death* and *Bagu*.
- **Zelda Dungeon** (zeldadungeon.net), read from Wayback Machine snapshots because the site blocks
  scripted requests. There are two versions:
  - the old guide (2013–14): *Heart Containers*, *Magic Containers*, *Spells*, *Items*,
    *Level Ups*, *Bosses* and walkthrough chapters 1–2;
  - the current wiki (2025–26): the game page, *Link Doll*, *The Adventure of Link Heart
    Containers*, *Magic Containers*, *Magic Level*, *Second Quest*, and the Fire, Spell, Thunder,
    Reflect and Fairy spell, Water of Life, Kidnapped Child and Hammer pages.

  The old guide's chapters 3–7 couldn't be fetched (the Wayback Machine was rate-limiting).
- **StrategyWiki**, read through its API: *Walkthrough*, *Items*, *Hearts and Potions*, *Spells
  and Skills*, *Link*, *Gameplay*, *Bosses*, *Cheats*, *Death Mountain* and each palace's page.
- **TASVideos** submission #7039 (*100%*), for its definition of 100% and to break ties.

### Counts

| | Zelda Wiki | Zelda Dungeon | StrategyWiki |
|---|---|---|---|
| Palaces | 6 + Great Palace | 6 + Great Palace | 6 + Grand Palace |
| Spells | 8 | 8 | 8 |
| Sword techniques | 2 | 2 | 2 |
| Heart Containers | 4 (4 → 8 bars) | 4 | 4 |
| Magic Containers | 4 (4 → 8 bars) | 4 | 4 |
| Link Dolls | no count | 6 (once "five" on the old guide's chapter 2, which also says six) | 5 listed (misses the southeast swamp) |
| Levels | 1–8 each | 1–8 each | 1–8 each |

## Names

The NES English names, the wikis' names and StrategyWiki's differ a lot. The files use the name two
sources share and give the others in `notes`:

| Used | Also called |
|---|---|
| Horsehead | Mazura (Zelda Wiki and Zelda Dungeon wiki article titles) |
| Helmethead | Jermafenser (Zelda Wiki and Zelda Dungeon wiki) |
| Rebonack | Horseback Ironknuckle (StrategyWiki), Ironknuckle (Zelda Dungeon's old guide) |
| Dark Link | Shadow Link (StrategyWiki, Zelda Dungeon's old guide), Link's Shadow (Zelda Wiki's boss list) |
| Maze Island Palace | Maze Palace (StrategyWiki) |
| Palace on the Sea | Ocean Palace (StrategyWiki, Zelda Dungeon's old guide) |
| Three-Eye Rock Palace | Hidden Palace (StrategyWiki, Zelda Dungeon's old guide) |
| Great Palace | Grand Palace (StrategyWiki) |
| Handy Glove | Power Glove (StrategyWiki) |
| Flute | Whistle (Zelda Wiki's palace page) |
| Magic Key | Magical Key (Zelda Wiki, Zelda Dungeon's old guide) |
| Trophy | Statue (StrategyWiki) |
| Water of Life | Medicine (StrategyWiki) |
| Child | Lost Child (StrategyWiki), Kidnapped Child (Zelda Dungeon wiki) |
| Upward Thrust | Jump Thrust (Zelda Wiki, Zelda Dungeon wiki) |
| Downward Thrust | Down Thrust (Zelda Wiki) |

## Discrepancies and how they were settled

**Spells and levels**
- **How many magic bars each spell wants.**
  - Thunder needs all 8 (every source).
  - Spell needs 7 (StrategyWiki's walkthrough, Zelda Dungeon's wiki). StrategyWiki's Hidden
    Palace page says "three magic containers", which agrees if it means three extra.
  - Fire 5 and Reflect 6 are on StrategyWiki only, so each row says so. Zelda Dungeon's wiki only
    says the requirement rises with each eastern spell.
- **Spell's MP at Magic level 3:** 98 on StrategyWiki, 96 on Zelda Wiki and Zelda Dungeon's spell
  page. The rows give levels 1 and 8 only, which every source agrees on; the 98/96 split is noted.
- **Zelda Dungeon's Magic Level table** repeats the Fire row for Fairy. Its own Fairy page agrees
  with Zelda Wiki and StrategyWiki, which are used.
- **Magic XP for levels 5–8:** 1,200 / 2,200 / 3,500 / 6,000 on Zelda Wiki, StrategyWiki, Zelda
  Dungeon's Magic Level page and TASVideos' frame data. Zelda Dungeon's old guide and its game page
  say 1,000 / 2,000 / 3,000 / 5,000. The majority is used and the other is in the Magic row's notes.

**Places**
- **Water of Life cave:** Zelda Wiki says just west of Midoro Palace. StrategyWiki and both Zelda
  Dungeon versions put it behind a boulder in the Moruge Swamp, north of Saria. The Moruge Swamp is
  used.
- **Palace on the Sea and its Heart Container:** Zelda Dungeon's wiki says to head east from Mido.
  Mido is in Western Hyrule; Zelda Wiki and StrategyWiki say east of Nabooru, which is used.
- **Midoro Swamp Heart Container:** StrategyWiki and Zelda Dungeon's old walkthrough describe the
  eastern of two boulder caves facing each other near Midoro Palace. Zelda Dungeon's heart container
  pages start from a rock south of Rauru, which leads to the same cave.
- **First Magic Container:** in the cave south of North Castle on Zelda Dungeon's wiki,
  StrategyWiki and TASVideos. Zelda Wiki says southeast, Zelda Dungeon's old guide southwest.
  South is used.
- **Spectacle Rock Magic Container:** StrategyWiki and Zelda Dungeon put it under the boulder west
  of the Hammer's cave. Zelda Wiki says it's in the eastern cavern. West is used and Zelda Wiki's
  version is in `notes`.
- **Maze Island Magic Container:** Zelda Wiki, Zelda Dungeon and StrategyWiki's list put it on the
  west or southwest side. StrategyWiki's walkthrough says the northwest corner, which is in `notes`.
- **Mido's sick child** is a daughter on Zelda Wiki and Zelda Dungeon, a son on StrategyWiki. The
  rows don't say which.

**Not verified:** whether the sword techniques survive a replay, which only Zelda Wiki's game page
states.
