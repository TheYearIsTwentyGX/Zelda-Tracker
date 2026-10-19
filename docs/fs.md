# Four Swords

Four Swords has one checklist (`docs/checklist-trackers.md`), `Stages & Keys`, after a Progress
Overview. The data is in `shared/src/commonMain/composeResources/files/fs/stages.json`.
`FsDatasetTest` checks the counts below and the links between rows, `ChecklistDatasetsTest` checks
the format, ids, regions and `requires`, and `FsOverviewTest` checks the overview row.

The game is the **Game Boy Advance original** (2002, bundled with *A Link to the Past & Four
Swords*) and the **Anniversary Edition** (DSiWare, 2011). Their stages, keys and doors are the same,
and they make up the counted tabs. Each version's own content is a `separate` section: it has its
own tab and count but stays out of the total and *Next*, since a player has only one of the two
versions. Nothing is fetched at runtime: the JSON was compiled offline from the sources below.

## Save files

**Three files**, `file_1`–`file_3`, the placeholder's ids, so nothing moved.
- **GBA:** Four Swords has no files of its own. Zelda Wiki, *File-Selection Screen*: "Each Saved game
  in the Game Boy Advance version of A Link to the Past stores the player's progress for both A Link
  to the Past and Four Swords". Zelda Wiki's screenshot of the combined screen shows three files, as
  ALttP has (`docs/alttp.md`). The app keeps the two games' progress apart, under their own game ids,
  so FS's File 1 is the same cartridge file as ALttP's File 1 but has its own ticks.
- **Anniversary Edition:** Zelda Wiki, *Four Swords Anniversary Edition*: "The game now has its own
  File Selection, instead of a shared one as in A Link to the Past & Four Swords". Its file screen
  screenshot shows three files.
- No text source states the number three for either version; the count comes from the screenshots.
- Keys are given to each Link ("I grant you each a Silver Key"), so every player's own file holds its
  progress. The tracker is for your file.

## Trackers

| Tab (section id) | Separate | Contents |
|---|---|---|
| Silver Keys (`silver`) | | The optional Chambers of Insight, the 3 Silver Keys and Vaati's Palace's silver door |
| Golden Keys (`golden`) | | The 3 Golden Keys and the golden door |
| Hero's Keys (`heros`) | | The 3 Hero's Keys and the 12-level hero's door |
| Realm of Memories (`realm_of_memories`) | Anniversary | 9 levels behind its silver, golden and hero's doors (A Link to the Past, Link's Awakening, The Legend of Zelda), ending with the Master Sword |
| Hero's Trial (`heros_trial`) | Anniversary | Opening it (5 medals or 30,000 Rupees) and its 9 levels, ending with the Hurricane Spin |
| ALttP Link-ups (`alttp_links`) | GBA | Sword Beams (the Master Sword in ALttP), 10 Medals of Courage, the Hurricane Spin (the Lumberjack's Riddles) and the Palace of the Four Sword |

- **Counts:** 13 counted rows (5 + 4 + 4). Summaries: Keys = 0–9 (each key has `value` 1; the
  Chambers and doors have 0) and Palace doors = 0–3.
- **Regions** are the stages: Chambers of Insight, Sea of Trees, Talus Cave, Death Mountain and
  Vaati's Palace. The separate sections have none, so their rows list under the tab's title. The
  Realm's door and level are in each row's `area`, and the Hero's Trial's in its name.
- **One checklist.** The game has no story beyond the stages, so there's no Main Quest, and the
  version-only lists are short enough to be tabs.
- **Order and links:**
  - Each key tier `requires` the previous tier's palace door, and each door `requires` its three
    keys.
  - Each Realm of Memories and Hero's Trial level `requires` the one before, since a door opens only
    once the previous one is cleared.
  - The Realm's first level and the Palace of the Four Sword `require` the silver door, which is
    beating Vaati.

**Why these.** speedrun.com's **100%** rules: "You must Finish/Get: All Silver Keys, All Gold Keys,
All Hero Keys, All Realm of memories maps, All Hero Trail Maps, All Vaati's Palace doors". That is
the 9 keys, 3 palace doors, 9 Realm of Memories levels and 9 Hero's Trial levels: every tab but
the link-ups. The run starts at the Anniversary Edition's colour selection.

Not tracked:
- **Medals of Courage as a count.** A medal goes to the Link with the most Rupees at the end of each
  stage, and they keep piling up with replays, so there is no total to reach. The two thresholds that
  unlock something are tracked: 10 on the GBA (Lumberjack's Riddles) and 5 in the Anniversary
  Edition (Hero's Trial). The Anniversary Edition awards medals in multiplayer only.
- **Items.** Each Link carries one item at a time and swaps it at podiums. Items last only for a stage
  (in the Hero's Trial, only for a level), so there's nothing to collect. Zelda Wiki lists 9: Bomb,
  Boomerang, Bow, Chain Chomp, Gnat Hat, Magnetic Glove, Pegasus Boots, Roc's Cape and Shield.
  StrategyWiki leaves out the Chain Chomp. The stage rows' notes say which items each stage adds.
- **Heart Containers.** You start each stage with 6 hearts, get 2 more per stage (to 8; 14 in the
  hero's door), and they reset afterwards (Zelda Wiki, *Heart Container*).
- **Rupee Shards and Mystical Seeds.** These are per-run pickups: four shards make 500 Rupees, and
  seeds raise power, defense or speed for the stage.
- **The hidden "25" emblems** in three Hero's Trial levels. They're decoration for the series' 25th
  anniversary, with no reward, so they're in the level notes.
- **Rupee-total "medals".** None exist: a stage's Rupee total only decides which key its Great
  Fairy gives, and the only medal is the Medal of Courage.

**Missable: nothing.** A stage can be replayed for any key, and every later door stays open.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), read through the MediaWiki API:
  - *The Legend of Zelda: Four Swords* and *Four Swords Anniversary Edition*;
  - the stage pages: *Stages in Four Swords*, *Chambers of Insight*, *Sea of Trees*, *Talus Cave*,
    *Death Mountain (Stage)*, *Vaati's Palace*, *Realm of Memories*, *Hero's Trial*;
  - keys and rewards: *Silver Key*, *Golden Key*, *Hero's Key*, *Medal of Courage*, *Great Fairy of
    Forest*, *Hurricane Spin*, *Master Sword*, *Lumberjack's Riddles*, *Palace of the Four Sword*;
  - the bosses: *Big Manhandla*, *Dera Zol*, *Gouen*, *Vaati*;
  - everything else: *Items in Four Swords*, *Rupee*, *Heart Container*, *File-Selection Screen*,
    *A Link to the Past & Four Swords*.
- **Zelda Dungeon** (zeldadungeon.net), from Wayback Machine snapshots (2023–2026): *The Legend of
  Zelda: Four Swords*, *Realm of Memories*, *Hero's Trial*.
- **StrategyWiki**, *The Legend of Zelda: Four Swords*: the main page, *Items*, *Unlockables* and
  *Walkthrough*. It has no Anniversary Edition pages.
- **GameFAQs**: Anton299's Anniversary Edition text dump (the game's own text, which settles exact
  names), AzulFria's Anniversary walkthrough, and the ALttP&FS guide and text dump.
- **speedrun.com**, Four Swords category rules: settled what "100%" covers.

### Counts

| | Zelda Wiki | Zelda Dungeon | StrategyWiki | Game text |
|---|---|---|---|---|
| Main stages, each with a Great Fairy | 3 (Sea of Trees, Talus Cave, Death Mountain) | 3 | 3 | 3 |
| Key tiers (Rupees needed) | Silver 1,000 · Golden 3,000 · Hero's 5,000 | 1,000 · 3,000 · 5,000 | — | — |
| Vaati's Palace doors | 3 | 3 | — | 3 |
| Hero's door levels | 12 (Vaati's Palace); 9 (game article) | 12 | — | "a path of 12 levels" |
| Realm of Memories | 3 doors × 3 levels | 3 × 3 | — | — |
| Hero's Trial | 3 doors × 3 levels; 5 medals or 30,000 Rupees | 3 × 3; 30,000 Rupees | — | both messages |

## Discrepancies and how they were settled

- **The stages.** The three main stages are the Sea of Trees, Talus Cave and **Death Mountain** in
  every source; there is no separate Palace of Winds stage. "Palace of Winds" is
  another name for **Vaati's Palace** (Zelda Wiki, citing Hyrule Historia; the fairy calls it
  "Vaati's palace of winds"). The Sea of Trees boss is **Big Manhandla**.
- **Hero's door length.** Zelda Wiki's game article says nine floors. Its *Vaati's Palace* page,
  Zelda Dungeon and the game's text all say 12, with a boss every three. 12 is used.
- **Key names.** The game's text says **Golden Key** and **Hero's Key** ("Hero Keys" in places), as
  Zelda Wiki does. speedrun.com, Zelda Dungeon and a GameFAQs guide say "Gold Key". StrategyWiki's
  walkthrough says "Master Keys", which is wrong. The game's names are used.
- **Golden Key threshold.** One GameFAQs guide says 1,000 Rupees; every other source, and the game,
  says 3,000. 3,000 is used.
- **Medal names.** "Medal of Courage" (the ALttP&FS manual, via Zelda Wiki), "hero medals" (the
  Anniversary Edition's text), "Triforce Medal(lions)" (StrategyWiki and fan guides). Zelda Wiki's
  name is used, with the others in notes.
- **Hero's Trial unlock.** Zelda Dungeon gives only the 30,000 Rupees. Zelda Wiki and both in-game
  messages ("You have collected five hero medals…", "You have collected 30,000 Rupees…") give
  either. Both are in the row.
- **Door names.** Silver, golden and hero's (Zelda Wiki), Silver, Gold and Hero (Zelda Dungeon), or
  Right, Left and Center (AzulFria). Zelda Wiki's are used.
- **Palace of the Four Sword.** StrategyWiki says beating Vaati is enough. Zelda Wiki and the
  official encyclopedia also need Ganon beaten on the same file. Both are in the row's notes, and
  the row uses the stricter one: it `requires` the silver door (Vaati), and its `after` names Ganon.
- **Lumberjack's Riddles.** Zelda Wiki's *A Link to the Past & Four Swords* page also needs
  Agahnim's first defeat, which its quest page leaves out. It's in the row's text.

**Unverified:** the save-file count, from screenshots only (see above).
