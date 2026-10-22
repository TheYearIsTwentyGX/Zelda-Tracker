# The Wind Waker

Every Wind Waker tracker is a checklist (`docs/checklist-trackers.md`). The data is in
`shared/src/commonMain/composeResources/files/ww/`. `WwDatasetTest` checks the counts below and the
links between files, and `ChecklistDatasetsTest` checks the format, ids, regions and `requires`.
`WwOverviewTest` checks the overview rows.

The trackers follow the **GameCube original** (2002), North American and European release. Where
**Wind Waker HD** (Wii U, 2013) differs, the item's `notes` say so. Nothing is fetched at runtime:
the JSON was compiled offline from the sources below, with scripts that aren't committed (as for
Echoes of Wisdom and A Link to the Past).

## Save files

**Three files.** The game's memory-card block holds three quest logs: zeldaret/tww,
`include/m_Do/m_Do_MemCardRWmng.h`, `card_gamedata gamedata[3]`. HD has three as well.
`Games.WW` already had three placeholder files, so their ids (`file_1` to `file_3`) didn't change.

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | none | A row per checklist below (`ChecklistProgressProvider`) |
| Main Quest | `main_quest.json` | 20 story steps in order, from Outset Island to Ganondorf: the 6 dungeons with boss and treasure, the 3 Pearls, Hyrule Castle, the Master Sword's power, the Triforce of Courage and Ganon's Tower. A second tab has the 8 Triforce Shards. Pearls = `dins_pearl + farores_pearl + nayrus_pearl` (0–3) |
| Items & Equipment | `items.json` | 5 equipment rows (sword with 4 tiers and shield with 2; Power Bracelets, Pirate's Charm, Hero's Charm), 17 items (Picto Box and Hero's Bow tiered), 4 Bottles and 5 upgrades (wallet, bomb bag, quiver, magic, Hurricane Spin). Wallet = `200 + wallet.value` (200–5,000); Bombs and Arrows = `30 + …` (30–99) |
| Heart Pieces | `heart_pieces.json` | 44 Pieces of Heart and 6 Heart Containers. Hearts = `3 + floor(pieces / 4) + containers` (3–20) |
| Charts | `charts.json` | 41 Treasure Charts (tiers: chart found, treasure salvaged), Wind Waker HD's Treasure Charts 42–46 on a `separate` tab, 8 Triforce Charts (found, deciphered) and 12 Special Charts. *Treasure Charts found* = `treasure_charts - treasure_charts.value` and *Treasures salvaged* = `treasure_charts.value` (each 0–41) |
| Sea Chart | `sea_chart.json` | The 49 squares, A1 to G7, a region per row. Each square's notes list what the other trackers have there |
| Sea Secrets | `sea_secrets.json` | 20 Secret Caves, 7 Submarines, 20 lookout-platform groups and 6 Big Octos |
| Nintendo Gallery | `figurines.json` | 134 figurines by gallery room; 16 are `missable` |
| Songs | `songs.json` | The 6 songs, with their notes |
| Great Fairies | `great_fairies.json` | The 7 Great Fairies and the Queen of Fairies |
| Side Quests | `side_quests.json` | 8 quests (Joy Pendants, Mail Sorting and Lenzo as tiers; Skull Necklaces, Golden Feathers, Knight's Crests, Mila, the Traveling Merchants), 8 Withered Trees, and the 5 Tingle Statues plus Ankle's reward |

No engine change was needed. The one list that exists only in HD, Treasure Charts 42 to 46, is a
`separate` section (added for Link's Awakening): it has its own tab and count but stays out of the
file's total, *All* and *Next*, so the GameCube's 41 stay the total. No other count differs enough
between versions to need a tab of its own (see *Wind Waker HD* below).

**Why these.** speedrun.com's Wind Waker categories say what completionists collect:
- **100%** asks for: all hearts; double magic; the Hurricane Spin; every item on the Items screen
  (Tingle Tuner, Deluxe Picto Box, Magic Armor, the three bags and 4 Bottles among them); all songs;
  the Triforce; the three Pearls; the full-power Master Sword; the Mirror Shield; the Power
  Bracelets; the Pirate's and Hero's Charms; all 41 Treasure Charts and 8 Triforce Charts; and the
  99 Bomb Bag, 99 Quiver and 5,000 Wallet;
- **100% All Menus + Figurines** adds: all Special Charts; all 134 figurines; all Tingle Statues;
  the whole Sea Chart; fishing up all 49 treasures; full dungeon clears; the Hand-Me-Down Tingle
  Tuner; the Pages of the Legend; and Tingle's discounts.

The trackers cover everything in the first list and most of the second. The 49 treasures are the
41 Treasure Charts' *Treasure salvaged* tiers and the 8 Triforce Shards. The Great Fairies, Secret
Caves, Submarines, lookouts and Big Octos are what the Special Charts point to, and most of them
give a Piece of Heart, a chart or an upgrade.

Not tracked:
- **Dungeon clears** (every room and chest, maps, compasses and keys), as for ALttP: no source
  gives them precisely enough for a useful *how to*.
- **The Hand-Me-Down Tingle Tuner, Tingle's discounts and the Pages of the Legend.** They're
  GameCube + Game Boy Advance extras, and HD dropped the Tingle Tuner. The Knuckle figurine's notes
  cover the one that matters to the Gallery.
- **The Hero's Clothes**, which Link always gets in the prologue.
- **Spoils, bait and potions**, which are consumables. Joy Pendants, Skull Necklaces, Golden
  Feathers and Knight's Crests appear as the side quests that use them.
- **Mini-games that only pay Rupees** (the Boating Course, Piggy-Sitting, extra Sinking Ships
  wins). The ones that give a Piece of Heart or a chart are rows in those trackers.

**Duplicates.** A few things are in two trackers, as in Ocarina of Time, and the notes point to the
other one:
- the Great Fairies' gifts are also the Upgrades tab;
- the Queen of Fairies is also the Hero's Bow's second tier;
- Mila's Bottle, Orca's Hurricane Spin and the Traveling Merchants' Piece of Heart are also in Side
  Quests.

**Missable.** On the GameCube, only figurines (Zelda Wiki, *Permanently Missable Items*):
- Kogoli, Zephos & Cyclos, Big Octo, Helmaroc King, Phantom Ganon, Puppet Ganon and the Wind
  Temple's Wizzrobe;
- Tetra and her six crew;
- Knuckle;
- Link & the King of Red Lions (lost if any of the others are).

A Second Quest keeps the figurines, so missed ones can still be had. Gohma, Kalle Demos, Jalhalla
and Molgera aren't missable: their rematches in Ganon's Tower are a second chance. No item, piece or
chart is missable.

## Regions

Zelda Wiki's *Locations in The Wind Waker* has the 49 islands and the Great Sea. Files with
geography use the island as the region, listed in **Sea Chart order** (A1 north-west to G7
south-east), and give the spot within it in `area`:
- each treasure is listed where it's salvaged;
- Hyrule and Ganon's Tower are under Tower of the Gods, the way in;
- the Earth and Wind Temples are under Headstone Island and Gale Isle.

`WwDatasetTest` checks every region is an island and that the order matches the Sea Chart.

Some files use other regions:
- the Sea Chart uses Rows A to G;
- the Nintendo Gallery uses its seven rooms, by Zelda Wiki's official names;
- Charts' Triforce and Special tabs, Items & Equipment and Songs have none.

The Main Quest is in story order, with `requires` for the hard gates:
- the Tower of the Gods needs the three Pearls;
- both temples need the second Forsaken Fortress;
- the Master Sword's power needs both temples;
- the Triforce of Courage needs the 8 shards and the Master Sword's power;
- Ganondorf comes last.

Heart Pieces follow Zelda Dungeon's numbering, which follows its walkthrough; each piece's notes
give its number.

## Wind Waker HD

The differences, and where each is noted:
- **Swift Sail** (won at the Windfall Auction): the Sail, Wind's Requiem and Auction rows. Not a
  row of its own, since the GameCube has none.
- **Triforce Charts:** HD keeps only charts 1, 3 and 5 (renumbered 1 to 3); the other five
  shards are found where their charts were. The shards' and charts' notes say which.
- **Treasure Charts 42 to 46** are HD-only. They lead to the five old Triforce Shard spots, which
  now hold Rupees except Two-Eye Reef's, which has the Piece of Heart. They're on the separate
  *HD Charts 42–46* tab, out of the totals, since GameCube completion counts 41. The Hero's Charm,
  Savage Labyrinth, Pawprint Isle cave and lookout rows also mention them.
- **Hero's Charm:** on the Savage Labyrinth's floor 50 in HD, where the GameCube has a Piece of
  Heart. HD moves that piece to Treasure Chart 46 (Two-Eye Reef), so both versions have 44 pieces.
  Mrs. Marie's 40 Joy Pendants give Treasure Chart 45 instead.
- **Tingle Bottle** replaces the Tingle Tuner. Without the tuner, the Tingle Statues are bombed
  with ordinary Bombs and have no hint, and Knuckle turns up on Tingle Island after all five.
- **Picto Box:** holds 12 pictographs (3 on the GameCube). The Deluxe Picto Box needs no Forest
  Firefly. Carlov carves 12 figurines a day instead of 1. The figurine count is the same, 134.
- **Hero Mode** (double damage, no recovery hearts) is a file option that changes nothing tracked.
- Also noted: HD's 500-Rupee starting wallet; Magic Armor costing Rupees; Forest Water lasting 30
  minutes instead of 20; Treasure Chart 18's Purple Rupee.

## Sources

Every count was checked against at least two of:
- **Zelda Wiki** (zeldawiki.wiki), through the MediaWiki API:
  - *Treasure Chart* (its table of all 46 charts), *Triforce Chart*, *Triforce Shard*, *Special
    Chart*, *Piece of Heart*, *Heart Container*;
  - *Figurines in The Wind Waker* (all 134, by room) and *Permanently Missable Items*;
  - *Great Fairy*, *Wallet*, *Quiver*, *Bomb Bag*, *Magic Meter*, *Bottle*;
  - *Submarine*, *Lookout Platform*, *Big Octo*, *Tingle Statue*, *Forest Tree*, *Joy Pendant*,
    *Skull Necklace*, *Golden Feather*, *Knight's Crest*, *Mail Center (Mini-Game)*;
  - *Sea Chart*, *Fishman*, *Locations in The Wind Waker*, the six song pages, the items' pages, and
    *Swift Sail*, *Tingle Bottle* and *Hero Mode*;
  - island pages for the Secret Caves.
- **Zelda Dungeon** (zeldadungeon.net), read from Wayback Machine snapshots because the site blocks
  scripted requests: *The Wind Waker Heart Pieces*, *Figurines*, *Items*, *Upgrades*, *Bottles*,
  *Songs* and *Treasure Charts*.
- **StrategyWiki** (*The Legend of Zelda: The Wind Waker*), through its API:
  - *Islands* (a section per square, with each fishman's hint);
  - *Pieces of Heart*, *Nintendo Gallery*, *Quest charts* (the Secret Cave and Platform Chart
    grids), *Side-quests*, *Minigames*, *Wind Waker Songs* and *Versions*.
- **The Wind Waker Randomizer** (LagoLunatic/wwrando: `SECRET_CAVE_ENTRANCES` and
  `logic/item_locations.txt`) and the **TWWHD Randomizer** (SuperDude88, `location_data.yaml`):
  the Secret Caves and their GameCube and HD contents.
- **zeldaret/tww** (decompilation): the save-file count.
- **speedrun.com** (Wind Waker category rules): what 100% covers.

### Counts

| | Zelda Wiki | Zelda Dungeon | StrategyWiki |
|---|---|---|---|
| Pieces of Heart | 44 | 44 (its #44 is HD's) | stub list, consistent |
| Heart Containers | 6 bosses | 6 | 6 |
| Treasure Charts | 41 (+5 HD) | 41 | — |
| Triforce Charts | 8 (3 in HD) | 8 | 8 |
| Special Charts | 12 | — | 2 described |
| Sea Chart squares | 49, three charted at the start | — | 49, a fishman each |
| Figurines | 134 (14/18/19/18/14/27/24 by room) | 134 (same per hall) | 134 by room (no Outset/Windfall list) |
| Missable figurines | 16 | flags most of them | marks *one chance* / *limited* |
| Songs | 6 | 6 | 6 |
| Great Fairies | 7 + Queen | 7 (Upgrades page) | — |
| Submarines / Big Octos | 7 / 6 | — | Platform Chart grid |
| Secret Caves | island pages | — | 20 on its chart grid (wwrando: 20) |
| Bottles | 4 | 4 | — |
| Save files | — | — | — (zeldaret/tww: 3) |

## Story links

With *Hide what the story hasn't unlocked yet* on (Settings → Progress), each checklist leaves out
what the Main Quest hasn't reached on the save file. It uses each item's `unlockedBy`, a step of
`main_quest.json`; docs/checklist-trackers.md, *Story gating*, explains the rules, and
`scripts/derive-story-links.py` holds WW's table.

All 419 items outside the Main Quest are linked. Even the Hero's Sword, Shield and Telescope are
gated, by `outset_island`, whose rewards they are.

**How a step was chosen.** The Great Sea opens with the Wind Waker (`dragon_roost_island`). After
that, what gates something is mostly the gear it needs, and each item's text names it. So an item
is gated by the **latest** of:

- its `after` text;
- where it is:
  - Windfall: `windfall_sail`;
  - Forest Haven: `dins_pearl`;
  - a dungeon: the step that opens it (the Forbidden Woods by `forest_haven`, the Tower of the
    Gods by `nayrus_pearl`, Hyrule Castle by `tower_of_the_gods`, the second Forsaken Fortress by
    `hyrule_castle`, the Earth and Wind Temples by `forsaken_fortress_second`);
  - any other island, Outset included: `dragon_roost_island`;
- the gear its text names, by the step that gives it:

  | Gear | Step |
  |---|---|
  | Grappling Hook, Wind's Requiem, Song of Passing, Hyoi Pears and Beedle | `dragon_roost_island` |
  | Deku Leaf, Boomerang | `forest_haven` |
  | Bombs and the boat's cannon | `windfall_bombs` |
  | Hero's Bow, Ballad of Gales, Command Melody, Darknuts' Knight's Crests | `nayrus_pearl` |
  | Skull Hammer, Master Sword | `hyrule_castle` |
  | Fire and Ice Arrows, Hookshot, Power Bracelets, Iron Boots, Mirror Shield | `forsaken_fortress_second` |

  The Fairy Queen gives the Fire and Ice Arrows only once Aryll is rescued.
- the treasure chart it names ("Open Treasure Chart 33"), whose gate it shares.

For a treasure chart's row, it's the chart's own how-to that counts (its first tier), not where
its treasure is salvaged.

**Figurines** need a colour pictograph shown to Carlov:
- The Deluxe Picto Box needs a Forest Firefly from Forest Haven, and the Nintendo Gallery's door
  needs a Hyoi Pear. So none comes before `dins_pearl`.
- After that, each figurine is gated by the **earliest** place its subject can be photographed.
- Gohma is met only before the Deluxe Picto Box, so hers is gated by `triforce_of_courage`, for
  her rematch in Ganon's Tower.
- Lenzo sells Legendary Pictographs once Aryll is rescued (`forsaken_fortress_second`).
- Tetra's crew come with her figurine (Hyrule Castle).

**Judgement calls**, gated as early as the sources allow:
- Where the text offers alternatives ("with arrows, the Boomerang or the cannon"; "either Great
  Fairy"), the earliest is used.
- Some enemies are gated by an open island where they're first met, even when that island's cave
  needs later gear.

Sources: Zelda Wiki (Nintendo Gallery, Deluxe Picto Box, Picto Box, Lenzo's Research Assistant,
Hyoi Pear, Fire Arrow, Ballad of Gales, Song of Passing, Bait Bag, Magic Armor), read through its
API, and the data's own how-to text. `WwDatasetTest` spot-checks the links.

## Discrepancies and how they were settled

**Pieces of Heart**
- **#44.** Zelda Dungeon's list is HD's: its #44 is salvaged at Two-Eye Reef with Treasure Chart 46,
  with a note that the GameCube piece is at the bottom of the Savage Labyrinth. Zelda Wiki and
  wwrando agree on the GameCube one, which is used. In the Japanese GameCube release a piece is
  under Link's house instead (Zelda Wiki); the North American and European placement is tracked.
- **Rose's big pig** (Outset Island): Zelda Wiki says carrying it needs the Power Bracelets, Zelda
  Dungeon lists only All-Purpose Bait. Both are in the row's notes.
- **Sinking Ships** (Zelda Dungeon's and StrategyWiki's name) is Zelda Wiki's *Squid-Hunt*. The row
  notes both.

**Upgrades.** The two wallet, bomb-bag and quiver fairies can be visited in either order. The first
visited gives the middle size and the second the largest:
- Zelda Wiki's tables list Western Fairy 60 and Thorned 99 for the quiver, and Eastern 60 and
  Southern 99 for bombs;
- Zelda Dungeon's Upgrades page has the opposite bomb order.

The tiers are named by capacity and both fairies are given, as in Ocarina of Time.

**Charts**
- Zelda Wiki's methods were rewritten more plainly. Where its wording was ambiguous, Zelda
  Dungeon and StrategyWiki settled it:
  - Treasure Chart 7 and 23 (Sinking Ships);
  - 16 (the Seven-Star Isles Wizzrobes);
  - 25 (reached through the Cliff Plateau cave, per wwrando).
- **Triforce Chart 6:** Zelda Wiki's *Outset Island* page says the Savage Labyrinth holds Triforce
  Chart 7. Its *Triforce Chart* page, wwrando and the HD randomizer all say 6 (7 is Stone Watcher
  Island). 6 is used.
- **Beedle's Bottle:** Zelda Wiki says the masked Beedle sells it once you have Beedle's Chart.
  Zelda Dungeon names only the shop, open once Link can sail anywhere. Both are noted.

**Secret Caves**
- **StrategyWiki** has gaps:
  - its *Islands* page has no Angular Isles cave, though its own Secret Cave Chart grid marks G5;
  - its Dragon Roost fishman's hint points at a Deku Leaf alcove, which isn't a cave.
  Zelda Wiki and wwrando settle both: the Angular Isles cave is on the small isle, and the Dragon
  Roost cave is under the boulder by the Wind Shrine.
- The grid's "." at D4 isn't a cave; Six-Eye Reef has none in any source.
- Fire Mountain, Ice Ring Isle, the Cabana Labyrinth and the Savage Labyrinth count as Secret Caves
  (on the chart, and in wwrando). The Islet of Steel interior and the Great Fairy fountains don't.

**Square codes.** StrategyWiki names a square by row letter (A to G, north to south), then column
(1 to 7, west to east): Dragon Roost Island is B6. Zelda Dungeon puts the column letter first, so
the same square is F2 there. StrategyWiki's form is used throughout.

**Nintendo Gallery**
- **Pairs:** Zelda Wiki says one pictograph of either subject is enough. Zelda Dungeon says to get
  both, or all four, in one shot. That's for Skett & Akoot, Pompie & Vera, Potova & Joanna and the
  Killer Bees. Both views are in the notes, and the safe one is recommended.
- **Rooms:** Zelda Dungeon calls them Halls A to G (Enemies, Bosses and Mini-Bosses, Great Sea).
  Zelda Wiki's official names are used: Dungeon Room, Forsaken Fortress Room, Ocean Room.
- **Names:** Zelda Wiki's are used. Its two Darknut and two Wizzrobe figurines are named
  *Darknut (with shield)* and *Wizzrobe (Wind Temple mini-boss)* to tell them apart.
- **Jalhalla:** Zelda Dungeon says to retake him in Ganon's Tower if missed "in the Tower of the
  Gods". He's the Earth Temple's boss; the row says so.

**Unverified**
- The Bird's Peak Rock chart's number in HD: second of three by Zelda Wiki's table; the HD
  randomizer still calls it 3.
- The Shark Island cave's time limit: tagged *verify* on Zelda Wiki, so no time is given.
- The songs' time signatures, which no source pairs with each song; only the notes are given.
