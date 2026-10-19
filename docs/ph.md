# Phantom Hourglass

Phantom Hourglass's trackers are checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): eight JSON files in
`shared/src/commonMain/composeResources/files/ph/`, listed in `core/Games.kt` after a Progress
Overview. `PhDatasetTest` checks the counts below; `ChecklistDatasetsTest` checks the format. No
engine change was needed. Nothing is fetched at runtime.

## Save files

Two: **File 1** and **File 2**. Nintendo's European manual says on its File Selection Screen page:
"You can create up to two different files", and its COPY option copies "to the other data file".
The placeholder in `Games.kt` already had two, so the ids `file_1` and `file_2` are unchanged.

## Files and counts

| File | Tracker | Sections (ids) | Count |
|---|---|---|---|
| `main_quest.json` | Main Quest | `quest` | 21 steps in one chain; summaries: Spirits 3, Sea Charts 4, Pure Metals 3 |
| `items.json` | Items & Equipment | `equipment`, `key_items`, `hourglass` | 9 items (the Sword with 2 tiers, Bombs, Bow and Bombchus with 3), 9 key items, 10 sands; summaries: Bombs 30, Arrows 50, Bombchus 30, Phantom Hourglass minutes 10 → 25 |
| `heart_containers.json` | Heart Containers | `bosses`, `others` | 7 + 6; Hearts `3 + bosses + others` = 16 |
| `spirit_gems.json` | Spirit Gems | `power`, `wisdom`, `courage`, `spirit_upgrades` | 20 + 20 + 20, and 3 spirits with Level 1 and 2 as tiers |
| `treasure_maps.json` | Treasure Maps | `maps` | 31 maps, each with two tiers: *Map* and *Salvaged*; summaries: Maps found 31, Treasures salvaged 31 |
| `ship_parts.json` | Ship Parts & Treasure | `bright_ship` … `golden_ship`, `treasure` | 8 sets × 8 parts, 8 Treasures |
| `sea_chart.json` | Sea Chart & Islands | `islands`, `golden_frogs` | 16 islands, 6 Golden Frogs |
| `sidequests.json` | Sidequests | `fishing`, `trading`, `minigames`, `beedle` | 8 fishing steps (6 fish), 5 trades, 6 mini-games, 4 cards |

Ids are unique across all eight files, and `PhDatasetTest` checks this. They're descriptive, not
numbered: a Treasure Map's id names where it's found (`map_cannon_beehives`), because the sources
number some maps differently (below). Renaming a map's `#` in its name later keeps its progress.

Regions are Zelda Wiki's four quadrants of the World of the Ocean King, in the order their Sea
Charts open: **Southwestern, Northwestern, Southeastern, Northeastern Sea**. The Temple of the Ocean
King is under the Southwestern Sea (it's on Mercay Island), with the floor in `area`. Things with no
place (a gem that comes by letter, Beedle's and the Masked Ship's wares, which follow you between
seas) have no region and are listed under their section's title.

Items are in the order you'd suggest doing them, since the overview's *Next* is the first one not
done. The Main Quest is one chain: every step `requires` the one before it.

### Summaries

- **Main Quest.** Spirits, Sea Charts and Pure Metals each add up named steps
  (`temple_of_fire + temple_of_wind + temple_of_courage`), so no step needs a `value`.
- **Capacities** are tiers with values: the first tier is the item with its first bag (10 Bombs,
  20 Arrows, 10 Bombchus), each upgrade adds its difference. So the Bombs total reads 0 until you
  have Bombs and 30 at most.
- **Phantom Hourglass minutes** is `10 + hourglass.value`: 10 when you take it from the altar (Blaaz's
  sand included, Zelda Wiki), 2 from each of the five later bosses but the Cubus Sisters, and 1 from
  each of the five Sunken Treasures that hold Sand of Hours: 25. The five sands are also Treasure Maps
  #2, #4, #9, #11 and #23, which the test checks.
- **Treasure Maps.** *Maps found* adds `min(<map>, 1)` over the 31 maps, and *Treasures salvaged* is
  the steps done less that. A map ticked once is found; twice, salvaged.

### What's tracked, and what isn't

The set follows what completion guides list for Phantom Hourglass: the 13 Heart Containers, the
60 Spirit Gems, the 31 Treasure Maps, the Ship Parts, the Treasures, the Golden Frogs, the fishing
journal and the trading quest (Zelda Wiki, Zelda Dungeon and IGN all have a page for each).

- **Items & Equipment** has the inventory (Sword, Wooden Shield, Boomerang, Bombs, Bow, Bombchus,
  Grappling Hook, Hammer, Shovel) and the key items (Phantom Hourglass, Cannon, Salvage Arm, Sun Key,
  Cyclone Slate, Ghost Key, Regal Necklace, King's Key, Fishing Rod). The Sea Charts and the Pure
  Metals are Main Quest steps.
- **Ship Parts** are random, so each part's row says where parts come from rather than a place.
  Which sets are common is set per save file (Zelda Dungeon). The SS Linebeck's own set is left out:
  you start with seven of its parts and buy the eighth, Eddo's Cannon, in the Main Quest.
- **Treasure** is the eight kinds on the Collection Screen; tick one when you've found one.
- **Sea Chart & Islands** has every island you can land on, hidden ones included, and the six Golden
  Frogs. The four Sea Charts are Main Quest steps. The Traveler's Ships aren't islands; they're in the
  Sidequests where they matter.
- **Sidequests** has the fishing journal's six fish, the Rod and Big Catch Lure, the trading quest,
  the mini-games' best prizes (as tiers where there are more than one), and Beedle's three
  membership cards and the Prize Postcard.
- **The "Courage Crest"** isn't a sidequest of its own. It's the crest copied from the Sacred Crest
  Chamber onto the Southwestern Sea Chart to find the Sun Key, so it's in the Main Quest
  (`totok_b4_b6`).
- **A few things are in two trackers**, because a player looks for them in both: the Heart
  Containers that are mini-game prizes or the Neptoona, the five Sands of Hours, and the dungeon
  items.
- **Left out:** Battle mode, Fallen Wisdom (hints sold by the Fallen Adventurers), the Temple of the
  Ocean King's Best Time, the Treasure Teller's prices, potions, Rupee prizes, and Ship Part trading
  over Wi-Fi (shut down in 2014).

### Missable

Nothing is missable for good. The Masked Ship and its Heart Container and Courage Gem depend on the
DS clock, not the story. Zelda Wiki says the S.S. Wayfarer, the last trade, appears only once, but
only for that trade, so it can't be missed before you need it.

## Sources

- **Zelda Wiki** (zeldawiki.wiki, read as wikitext through its API): Power Gem, Wisdom Gem, Courage
  Gem and Spirit Gem (their numbered tables), Treasure Map and Treasure Map #1–#31, Sand of Hours,
  Phantom Hourglass (Item), Heart Container (the series table), Ship Part (the full list), Treasure,
  Temple of the Ocean King, Items in Phantom Hourglass, Locations in Phantom Hourglass, the dungeon
  and island pages, Quiver, Bomb Bag, Bombchu Bag, Masked Ship, Beedle's Shop Ship, Mercay Island
  Shop, Shooting Range, Cannon Game, Goron Game, Treasure Maze, Traveler's Ship, Nyeve, Neptoona,
  Fishing, Trading Quest, Golden Frog, Cyclone Slate, the key item pages and Beedle's cards.
- **Zelda Dungeon**: its Phantom Hourglass Treasure Maps page (each map's place, condition and
  Sunken Treasure), Ship Part, Spirit Gem and Heart Container pages, and the Mercay Island walkthrough
  page. The live site blocks automated fetches, so these were read from Wayback Machine copies
  (2018–2025).
- **IGN**'s Phantom Hourglass guide wiki: Heart Containers, Spirit Gems, Treasure Maps, Ship Parts,
  Beedle's Shop, Fishing, Traveler's Ships, Items & Weapons and Islands.
- **StrategyWiki**: the walkthrough chapters, Heart Containers, Sidequests, Items and The Ship.
- **Nintendo's manual** (the European English e-manual) for the save files.

## Discrepancies

### Counts

None. Every source gives 16 hearts at most (3 + 7 bosses + 6 others), 20 of each Spirit Gem, 31
Treasure Maps, 9 sets of 8 Ship Parts, 8 Treasures, 6 Golden Frogs, 6 fish and 25 minutes of sand.

### Treasure Maps

| Topic | Sources | Used |
|---|---|---|
| Numbers of the maps from Goron Island to Harrow Island | Zelda Wiki: #14 Goron Island (the scared Goron), #15 Goron Temple, #16 Goron Island (Bombchu), #17 Isle of Frost, #18–#19 Harrow Island. Zelda Dungeon: #14–#15 Harrow, #16 scared Goron, #17 Goron Temple, #18 Bombchu, #19 Isle of Frost. Both put the same Sunken Treasure with the same map | Zelda Wiki's: it runs in story order, and IGN says the first Harrow maps come after the Temple of Ice, after the Goron and Frost maps. The ids don't use numbers, so a fix later keeps progress |
| When Harrow Island's first two maps can be dug | After the Southeastern Sea Chart (Zelda Dungeon); after the Temple of Ice (IGN; Zelda Wiki says a Yook reveals the island after the Azurine) | After the Temple of Ice |
| #6's Sunken Treasure | South of Zauz's Island (Zelda Wiki), west of Bannan Island (Zelda Dungeon) | Zelda Dungeon's, which gives a direction for every map; noted here |
| #1's Sunken Treasure | Near Spirit Island (Zelda Wiki), south-west of Molida Island (Zelda Dungeon); the same corner of the sea | Both in the row |
| #10's timing | Once you have the Shovel (StrategyWiki digs it before the Temple of Wind); after the Temple of Courage, whose statue hints at it (Zelda Dungeon) | The Shovel; the hint is noted |
| #17 (Isle of Frost islet) | Cross the bridge (Zelda Dungeon); grapple across after the Temple of Ice (IGN) | Both readings fit the row's wording |

### Spirit Gems

| Topic | Sources | Used |
|---|---|---|
| Power Gem on Molida | High ledge in the Molida Island Cavern (Zelda Wiki), south-east of Romanos's hut (IGN) | Zelda Wiki |
| Power Gem on Bannan | Islet east of the dock (Zelda Wiki's table), south of the lower section (Zelda Wiki's Bannan page), south-west (IGN) | The Bannan page's |
| Wisdom Gem on the Isle of Ruins | North of Max's Temple, by pushing a boulder (Zelda Wiki), south of Mutoh's Temple (IGN) | Zelda Wiki |
| Wisdom Gem on Zauz's Island | Glide there with a Cucco (Zelda Wiki), the Grappling Hook or a Cucco (IGN) | The Cucco, which works on the first visit |
| Courage Gem on Molida's islet | West of the Temple of Courage (Zelda Wiki), to the north (IGN) | Zelda Wiki |
| Courage Gem on Dee Ess Island | Two Hinoxes (Zelda Wiki), two Eye Brutes (IGN) | Hinoxes |
| Courage Gem on the Isle of the Dead | The Rupoor room behind McNey's cave (Zelda Wiki), a secret cave near the docks (IGN); McNey's cave is the one by the shore | Zelda Wiki |

### Hearts and items

| Topic | Sources | Used |
|---|---|---|
| The island shop's Heart Container | After the Goron Temple and the Quiver and Bombchu Bag upgrades (Zelda Wiki); after buying the Bomb Bag, Quiver and Bombchu Bag in that order (IGN); after the Bombchu Bag (StrategyWiki) | IGN's three upgrades, Zelda Wiki's noted |
| Maze Island's sea | Northeastern (Zelda Wiki's locations, Zelda Dungeon), Northwestern (IGN's Heart Container page) | Northeastern |
| Quiver upgrades | 30 from the Shooting Range and 50 from the shop (Zelda Wiki's table) | Named by capacity, both sources in each tier |
| The Big Catch Lure | Show the Old Wayfarer a Loovar (Zelda Wiki), one of each of the first three fish (StrategyWiki) | Zelda Wiki, the other noted |
| Beedle's cards | Freebie at 20 points, Compliment at 50, Complimentary at 100 (IGN); Zelda Wiki's Complimentary Card page says 50 and its Compliment Card page 50 | IGN's ranks |
| The Ho Ho Tribe's ship | Southeastern Sea (Zelda Wiki's Traveler's Ship page), the south-west (its Nyeve page) | No region; the row names the ship |

### Ship Part names

Zelda Wiki's list uses the North American names. IGN and StrategyWiki differ for six, noted on
those parts: Strong Cannon / Staunch Cannon, Conning Tower / Control Tower, Battle Wheel / Screw
Wheel (IGN), Rock Wheel / Stone Wheel, Spike Handrail / Spiked Handrail and Shell Wheel / Shell
Paddle (IGN and StrategyWiki). How many parts of one set add a heart also differs: 3, 6 and 8 (Zelda
Wiki, Zelda Dungeon), or 4, 6 and 8 (IGN, StrategyWiki). The set notes give Zelda Wiki's.
