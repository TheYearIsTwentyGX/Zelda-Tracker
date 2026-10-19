# Four Swords Adventures

Every Four Swords Adventures tracker is a checklist (`docs/checklist-trackers.md`). The data is in
`shared/src/commonMain/composeResources/files/fsa/`. `FsaDatasetTest` checks the counts below and
the links between the two files, `ChecklistDatasetsTest` checks the format, ids, regions and
`requires`, and `FsaOverviewTest` checks the overview rows.

The game is the **GameCube release** (Japan, March 2004), tracked as its main mode, the
**Hyrulean Adventure**, plus the multiplayer unlocks. Nothing is fetched at runtime: the JSON was
compiled offline from the sources below.

## Save files

**Three files**, `file_1`–`file_3`, the placeholder's ids, so nothing moved.
- The North American manual, p. 24 (Select Game Screen): "You can store up to three saved-data
  files." Its screenshot labels them Quest Log 1, 2 and 3.
- p. 11: "Hyrulean Adventure requires 3 blocks available on the Memory Card." The game offers a save
  after each stage. "Shadow Battle data cannot be saved."

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | — | A row per checklist below (`ChecklistProgressProvider`) |
| Hyrulean Adventure | `hyrulean_adventure.json` | All 24 stages, 3 in each of the 8 levels, each with its boss (as a tag), the items found there and what it gives. Maidens rescued = 0–7 (the six Shrine Maidens and Princess Zelda), Royal Jewels = 0–4 |
| Tingle's Tower & Shadow Battle | `tingle_and_shadow_battle.json` | **Tingle's Tower**: the 8 mini-games, one per level. **Shadow Battle**: the Dark World arenas, unlocked by finishing the game |

- **Regions** are the eight levels, in Zelda Wiki's spelling (the game's): Whereabouts of the Wind,
  Eastern Hyrule, Death Mountain, Near the Fields, The Dark World, The Desert of Doubt, Frozen
  Hyrule, Realm of the Heavens. Each stage's `area` is its number, "Stage 1-1" to "Stage 8-3".
- **Order.** Stages open strictly in order, so each `requires` the one before.
- **Tingle's Tower.** Each mini-game's `after` names the second stage of its level, which brings
  Tingle's invitation. It's text rather than `requires` because the stages are in the other file.
- **Summaries.** Maidens and Royal Jewels are summaries over item ids (the stage that frees each
  one), so no extra rows are needed.

**Why these.** Very little in FSA lasts beyond a stage, so the trackers stick to what a save file
keeps:
- stage and level progress;
- rescued Maidens and the Royal Jewels (on the Status screen, manual p. 23);
- unlocked Tingle's Tower mini-games;
- the Force Fairy stock.

speedrun.com has only Any% (full game, and per stage 1-1 to 8-3). There is no 100% category, and
GameFAQs lists exactly two unlockables: the 8 mini-games and the 5 Dark arenas.

**Force Gems.** Every stage needs **2,000** Force Gems to power the Four Sword and break the dark
barrier at its end (in multiplayer, the players' combined total). Reach the barrier short and
you're sent back to the start. The target is the same everywhere, so it's in the tracker's note, not
on each row.

Not tracked:
- **Items.** Each Link holds one item at a time, swapped at podiums, and nothing carries over
  between stages. Great Fairy Lv2 upgrades last until the item is put down (manual p. 29). Each
  stage's `notes` list its items, for reference.
- **Heart Containers.** They reset every stage: you start with four (three filled) and find two to
  four more in each (Zelda Wiki). There are **no Pieces of Heart**.
- **Force Fairies.** Clearing a level with Force Gems in hand gives one per 1,000, up to 99. That's
  a stock that gets spent, not something to collect.
- **The five base Shadow Battle arenas.** They're open from the start; only the Dark ones are an
  unlock.
- **Navi Trackers** (Japan and Korea only, originally *Tetra's Trackers*). It's a separate
  GBA-only, score-based mode: up to four players hunt Tetra's pirates for stamps (medals) against
  the clock. Sources disagree on what finishing it means: 12 stages (Japanese Wikipedia; Zelda
  Dungeon's "all 12 of Tetra's tests") or six Certificates of Achievement (Zelda Wiki, a section
  flagged for improvement). It also isn't in the North American and European releases this app
  follows. It could be a small checklist later if a source settles its 12 courses.

**Missable: nothing.** Cleared stages can be replayed, and a declined Tingle invitation comes back
whenever you return to the level.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), read through the MediaWiki API:
  - the game page, *Stages in Four Swords Adventures*, *Level* and each of the 24 stage and 8 level
    pages;
  - *Bosses* and *Items in Four Swords Adventures*, *Force Gem*, *Force Fairy*, *Heart Container*,
    *Maiden*, *Royal Jewel*, *Moon Pearl*;
  - the other modes: *Tingle's Tower* (its mini-game table) and each mini-game's page, *Shadow
    Battle*, *Navi Trackers*;
  - *Saving* and *Speedrun Records*.
- **Zelda Dungeon** (zeldadungeon.net), from Wayback Machine snapshots: the eight-chapter "complete
  100% walkthrough" and the wiki pages on Force Gems, Tingle's Tower, Shadow Battle and Navi
  Trackers.
- **The North American instruction manual** (scan via the Wayback Machine): save files, Force Gems,
  the Status screen, items and Great Fairy upgrades, Tingle's Tower and Shadow Battle.
- **Zelda Universe**'s walkthrough (chapters 1–8), StrategyWiki (a stub covering 1-1 and 1-2), and
  English and Japanese Wikipedia.
- **Zeldapedia** (Fandom), *Tingle Tower*: a one-line description of each mini-game.
- **Nintendo's Japanese site** (nintendo.co.jp/ngc/g4sj): stage flow, Shadow Battle arenas and Navi
  Trackers.
- **GameFAQs**: the cheats page (unlockables) and the "more than 2000 Force Gems" Q&A.
- **speedrun.com** (game `fsa`): categories and per-stage boards.

### Counts

| | Zelda Wiki | Zelda Dungeon | Manual / other |
|---|---|---|---|
| Levels × stages | 8 × 3 | 8 × 3 | 24 (Japanese Wikipedia: 3 × 8); speedrun.com boards 1-1 to 8-3 |
| Force Gems per stage | 2,000 | 2,000 | 2,000 (Japanese Wikipedia; the manual shows it on the Status screen) |
| Maidens + Zelda | 6 + 1 | 6 + 1 | — |
| Royal Jewels | 4 | 4 | — |
| Tingle's Tower mini-games | 8 | — | 8 (English Wikipedia, GameFAQs) |
| Shadow Battle arenas | 5 + 5 Dark | — | 5 + 5 (Japanese Wikipedia, Nintendo JP lists the 5) |
| Save files | — | — | 3 (manual p. 24) |
| Pieces of Heart | none | none | none |

## Discrepancies and how they were settled

- **Level names.** Zelda Universe writes "Whereabouts of the Winds" and "Desert of Doubts". The
  game, Zelda Wiki and Zelda Dungeon use **Whereabouts of the Wind** and **The Desert of Doubt**,
  which are used here. Stage 7-2 is the **Temple of Ice** on Zelda Wiki (citing the Encyclopedia)
  and "The Ice Temple" on Zelda Dungeon; noted.
- **Bosses of the open-air stages.** For 1-1, 3-1, 4-1, 5-1, 5-2, 6-1 and 7-1, Zelda Dungeon calls
  the end fight the stage's boss, while Zelda Wiki lists some only as sub-bosses. The tag is the end
  fight either way. Stage 1-1's Chief Soldier is the "Squad Leader" on StrategyWiki.
- **Where Ganon is fought.** Zelda Wiki files the Ganon fight under the Tower of Winds. Zelda
  Dungeon's walkthrough and speedrun.com (timing ends on the last hit on Ganon in 8-3) put it at
  the end of the Palace of Winds, which is used.
- **Items per stage.** Zelda Wiki's infoboxes sometimes list more than the walkthroughs, e.g. the
  Bow, Fire Rod and Pegasus Boots in The Mountain Path. Rows follow Zelda Dungeon and note Zelda
  Wiki's extras. Treat the item lists as a guide.
- **Force Gem target: per stage or per level.** Zelda Wiki's *Force Gem* page and Zelda Dungeon say
  "level", but the manual, Japanese Wikipedia and Nintendo's site show it per stage, and every
  stage ends in a barrier. Per stage is used.
- **Tingle's Tower frequency.** The manual says "There is a Tingle's Tower in each stage". Zelda
  Wiki and GameFAQs say one mini-game per level, unlocked by its second stage, which the count of 8
  bears out. Zelda Wiki's version is used.
- **Shadow Battle unlocks.** Zelda Wiki, both Wikipedias and GameFAQs: the five Dark arenas appear
  when the Hyrulean Adventure is finished. The manual says the number of courses "increases when
  you clear stages", which could mean something more gradual. No source lists another unlock, so
  the single unlock is tracked and the manual is quoted in its notes.
- **Saving.** The manual offers a save after each stage; Zelda Wiki says it's automatic when a level
  is finished. Neither affects the tracker.
- **Heart Containers per stage.** Zelda Wiki says two to four; Zelda Dungeon's chapter headers show
  one to four. They aren't tracked.
- **Navi Trackers' regions.** Japanese Wikipedia says Japan only; Zelda Wiki and Zelda Dungeon
  include the Korean release, which reuses the Japanese disc.
