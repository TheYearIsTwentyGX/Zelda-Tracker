# Link's Awakening

Link's Awakening's trackers are checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): eight JSON files in
`shared/src/commonMain/composeResources/files/la/`, listed in `core/Games.kt` after a Progress
Overview. `LaDatasetTest` checks the counts below; `ChecklistDatasetsTest` checks the format.

The data is for the **Game Boy original (1993) and Link's Awakening DX (1998)**. Where the 2019
Switch remake differs for an item, its `notes` say so. Where the remake differs a lot for a
collectible, its list is a **separate section**: its own tab, labelled *Switch …*, with its own
count and totals, left out of the tracker's total, the *All* tab and the overview's *Next*. That's
the one engine addition this game needed (`ChecklistSection.separate`, documented in
`docs/checklist-trackers.md`). Without it, a DX player's bar could never reach the end, and the
two versions' rows would be mixed under the same region cards. Nothing is fetched at runtime.

## Save files

Three: **File 1–3**. Zelda Wiki's *Saving* page gives three for the Game Boy original, and the LADX
disassembly (zladx/LADX-Disassembly, `src/constants/memory/sram.asm`) has `SaveGame1`–`SaveGame3`.
The Switch remake also has three (Twinfinite's save guide, the weakest of the three sources). The
placeholder in `Games.kt` was already three, so the ids `file_1`–`file_3` are unchanged.

## Files and counts

| File | Tracker | Sections (ids) | Count |
|---|---|---|---|
| `main_quest.json` | Main Quest | `quest` | 22 steps: the Sword, 5 keys, 8 dungeons, the Egg and story steps; summary: 8 Instruments of the Sirens |
| `items.json` | Items & Equipment | `equipment`, `upgrades`, `golden_leaves`, *`switch_bottles`* | 15 items (Sword, Shield and Power Bracelet with 2 tiers each), 3 capacity upgrades, 5 Golden Leaves; Switch: 3 Fairy Bottles |
| `heart_pieces.json` | Heart Pieces | `pieces`, `containers`, *`switch_pieces`*, *`switch_containers`* | 12 + 8; Hearts `3 + pieces / 4 + containers` = 14. Switch: 32 + Dampé's container; Hearts on Switch = 20 |
| `seashells.json` | Secret Seashells | `seashells`, `mansion`, *`switch_seashells`*, *`switch_mansion`* | 26 + the Level 2 Sword; Switch: 50 + 5 Mansion rewards |
| `trading.json` | Trading Sequence | `trading` | 14, each step `requires` the one before |
| `songs.json` | Songs | `songs` | 3 |
| `photographs.json` | Photographs | `photos` | 12 (DX only), 4 missable |
| `color_dungeon.json` | Color Dungeon | `color_dungeon` | 6 steps (DX and Switch) |

Sections in *italics* are `separate`. The Switch tabs reuse the original's ids with a `switch_`
prefix for the spots the remake kept: all 12 Pieces of Heart, and 24 of the 26 seashells (all but
the Mansion's two bonus shells). `LaDatasetTest` checks this. Ids are also unique across all eight
files.

Items are in the order you'd suggest doing them, since the overview's *Next* is the first one not
done. The Main Quest is one chain: every step `requires` the one before it (the Ghost and Catfish's
Maw both follow Angler's Tunnel). Its summary counts Instruments with `value`: each dungeon has the
default value 1 and every other step `value: 0`. The Heart Containers are the bosses named in the
Main Quest's notes (`Boss: Moldorm.`), which the test checks.

Regions are the island's areas, in roughly the order a player reaches them: Mabe Village, South of
the Village, Toronbo Shores, Mysterious Forest, Koholint Prairie (with the Cemetery), Ukuku Prairie,
Goponga Swamp, Tabahl Wasteland, Kanalet Castle, Pothole Field, Tal Tal Heights (with Dampé's
Shack), Martha's Bay, Animal Village, Yarna Desert, Ancient Ruins, Rapids Ride and the Tal Tal
Mountain Range. A dungeon's Piece of Heart or seashell is under the region it's in, with the
dungeon in `area`. Tags give the items needed.

### What's tracked, and what isn't

These choices follow what LA completion guides list as 100%: dungeons and instruments, Pieces of
Heart, Secret Seashells, the trading sequence, the songs, and on DX the photos and the Color
Dungeon.

- **Items & Equipment** lists the inventory: Sword, Shield, Magic Powder, Roc's Feather, Shovel,
  Bombs, Power Bracelet, Pegasus Boots, Ocarina, Flippers, Hookshot, Bow, Magic Rod, Boomerang and
  Secret Medicine. The upgrades of one item are its tiers (Level 2 Sword, Mirror Shield, Power
  Bracelet L-2). The Magnifying Lens and the trade items are in the Trading Sequence, the songs in
  Songs, and the five dungeon keys in the Main Quest.
- **Capacity upgrades** are tracked by what they give (Magic Powder 20 → 40, Bombs 30 → 60, Arrows
  30 → 60), not by Mad Batter. Each of the three Mad Batters gives one upgrade of your choice, so
  his location doesn't decide the upgrade. All three locations are in each upgrade's notes.
- **A few things are in two trackers**, because a player looks for them in both: the Switch
  Mansion's 5-seashell Piece of Heart (Heart Pieces and Secret Seashells), and the Level 2 Sword
  (Items, and the Seashell Mansion).
- **Switch-only extras kept in notes:** Chamber Dungeons at Dampé's Shack (the Panel Dungeon, in
  the Japanese release), the Trendy Game's figures, Hero Mode, the Seashell Sensor,
  the Chamber Stones and the remake's renamed items (Koholint Sword, Powerful Bracelet, Goat's
  Letter, Li'l Devil for the Mad Batter). Dampé's rewards that count toward a collectible (two
  Pieces of Heart, the Heart Container, a seashell, a bottle) are in their trackers.
- **The Color Dungeon** is in the Switch remake too, not DX-only as first assumed. Its tracker says
  so. Photographs are DX-only: the Switch remake replaced the Camera Shop with Dampé's Shack.
- **Left out:** dungeon maps, compasses, Nightmare Keys and Owl Statues (Stone Beaks); Pieces of
  Power and Guardian Acorns (temporary drops); the Trendy Game prizes other than the Yoshi Doll and
  the collectibles.

### Missable

- **The Mansion's bonus seashells (Game Boy and DX).** The Seashell Mansion adds a shell only if
  you walk in carrying exactly 5, and again at exactly 10. Pass either count without visiting and
  that shell is gone. Zelda Wiki also says that shells still out in the world vanish or turn into
  20 Rupees once you have the Level 2 Sword, so the Mansion's note says to collect all 26 first.
  On Switch the rewards are "at least", and nothing is missable.
- **Photos 2–4 (Marin)** can only be taken while Marin follows you, before the Walrus wakes.
  **Photo 10 (Richard at the gate)** is gone once Kanalet Castle's gate is open.

## Story links

With *Hide what the story hasn't unlocked yet* on (Settings → Progress), each checklist leaves out
what the Main Quest hasn't reached on the save file. It uses each item's `unlockedBy`, a step of
`main_quest.json`; docs/checklist-trackers.md, *Story gating*, explains the rules, and
`scripts/derive-story-links.py` holds LA's table.

**Counts.** 173 of the 196 items outside the Main Quest are linked. The other 23 can be had in Mabe
Village and on Toronbo Shores from the start: the Tool Shop, the Trendy Game, the Fishing Pond and
the first trades.

**How a step was chosen.** An item is gated by the **latest** of the following.

- *Places*, by the step that opens them. A dungeon is gated by the step before it, the one that
  gives its key.

  | Places | Step |
  |---|---|
  | The Mysterious Forest, the prairies, Pothole Field, the Cemetery and Tabahl Wasteland | `find_sword` |
  | Goponga Swamp | `rescue_bowwow` |
  | Kanalet Castle | `bottle_grotto` |
  | Animal Village | `dream_shrine` |
  | Yarna Desert | `walrus` |
  | Tal Tal Heights, Martha's Bay, the Rapids Ride | `angler_key` |
  | The mountain range | `anglers_tunnel` |
  | The Ancient Ruins | `catfishs_maw` |
- *Gear* (tags and text), by the step before the dungeon that gives it:

  | Gear | Step |
  |---|---|
  | Roc's Feather | `tail_key` |
  | Power Bracelet | `rescue_bowwow` |
  | Pegasus Boots | `slime_key` |
  | Flippers | `angler_key` |
  | Hookshot, the Magnifying Lens | `anglers_tunnel` |
  | Bracelet L-2 | `face_key` |
  | Mirror Shield | `bird_key` |
  | The Ocarina | `dream_shrine` |
- *Others.*
  - Marin follows Link only after the Dream Shrine, so her photos are gated by `dream_shrine`.
  - The Ghost's bottle and photo come after Angler's Tunnel.
  - The Camera Shop is outside Mabe, so photos are gated by `find_sword`.
- *`after`.* A dungeon named alone means after it.

`LaDatasetTest` spot-checks the links and counts the start items.

## Sources

- **Zelda Wiki** (zeldawiki.wiki, read as wikitext through its API): Secret Seashell, Seashell
  Mansion, Piece of Heart, Heart Container, Trading Quest, The Travels of Link, Instrument of the
  Sirens, Nightmare, Dungeons in Link's Awakening, the eight dungeon pages, Color Dungeon, Mad
  Batter, Golden Leaf, Chamber Dungeon, Hero Mode, House by the Bay, the item, key, song and
  character pages, the game pages for all three versions, and Saving. Its per-location lists for LA
  are stubs, so for those it was used for counts and rewards only.
- **Zelda Dungeon**: the Heart Piece and Secret Seashell pages for the Game Boy and Switch versions,
  the Trading Sequence, Photographs, Li'l Devil, Color Dungeon, Golden Leaf, Chamber Dungeon, Fairy
  Bottle and Items pages. The live site blocks automated fetches, so these were read from Wayback
  Machine copies (2025–2026).
- **Zelda's Palace**: pieces of heart, secret seashells, upgrades, trading sequence, photographs,
  items, equipment, Ocarina songs and its Game Boy / Switch differences page. It covers both
  versions on one page.
- **Nintendo Life**: the Switch remake's Heart Piece and Secret Seashell guides. Its seashell guide
  marks the ones also in the Game Boy Color version.
- **StrategyWiki** (Photographs, Trading Sidequest, Secret Seashells, Heart Pieces, Secrets) and
  **Zelda Universe** (equipment upgrades), for spot checks.
- **zladx/LADX-Disassembly**, the DX disassembly, to settle disagreements: `bank1.asm` (a new game's
  maximums: Arrows 30, Bombs 30, Magic Powder 20), `entities/18_mad_batter.asm` (the upgraded
  maximums `$40, $60, $60`) and `sram.asm` (three save slots).

## Discrepancies

### Counts

None. Every source gives 12 Pieces of Heart (32 on Switch), 8 boss Heart Containers (and Dampé's
one more on Switch), 14 hearts at most (20 on Switch), 26 seashells (50 on Switch), 5 Golden
Leaves, 14 trades, 3 songs and 12 DX photos.

### Heart Pieces and seashells

| Topic | Sources | Used |
|---|---|---|
| Switch: which Dampé challenge gives which reward | "Fill Up Your Hearts" gives a Piece of Heart, four challenges a seashell, all of Tier 2 another piece and "Ticking Clock" the Heart Container (Zelda Dungeon, Nintendo Life); the first four give both the piece and the seashell (Zelda's Palace); the fourth of Levels 1 and 2 give the seashell and the piece (Zelda Wiki) | Zelda Dungeon and Nintendo Life, the others in the notes |
| Switch Mansion at 50 | Chamber Stone and 300 Rupees (Zelda Wiki), 280 (Zelda's Palace), the Chamber Stone only (Nintendo Life) | 300 Rupees, the others noted |
| Rapids Race times | Piece of Heart under 40 s (Zelda Dungeon, Nintendo Life) or 45 s (Zelda's Palace); seashell 35 s (Zelda Dungeon, Nintendo Life; "to be confirmed" on Zelda's Palace) | 40 s and 35 s |
| House by the Bay seashell | Only after you help the Ghost (Zelda Dungeon), or either way (Zelda's Palace) | Both in the notes |
| Cemetery cave Piece of Heart | Roc's Feather (Zelda Dungeon); the Pegasus Boots or Hookshot (Zelda's Palace); Bombs and Boots (Nintendo Life, Switch) | Roc's Feather, the rest noted |
| Switch Martha's Bay stair cave | Flippers and Pegasus Boots (Zelda Dungeon); Boomerang or Fire Rod for the bushes (Zelda's Palace); a jump-slash (Nintendo Life) | Flippers, the rest noted |
| Pothole Field gap (Switch) | Boots optional (Zelda Dungeon, Nintendo Life) or needed (Zelda's Palace) | Roc's Feather, the Boots noted |
| Items for a few seashells | Zelda's Palace lists the Pegasus Boots or Power Bracelet where the others don't (desert boulder, Owl Statue across the bridge, Martha's Bay phone booth) | Zelda Dungeon's, the other noted where it matters |
| Switch Fishing Pond Blooper seashell | After Catfish's Maw (Zelda Dungeon, Zelda's Palace) or Face Shrine (Nintendo Life) | Catfish's Maw |
| Regions for border spots | The seashell below the Witch's Hut: Ukuku Prairie (Zelda Dungeon's Game Boy page) or Koholint Prairie (the rest). The broken-bridge Piece of Heart: Tal Tal Heights or the Mountain Range. The House by the Bay: Martha's Bay or Toronbo Shores | The majority: Koholint Prairie, Tal Tal Mountain Range, Martha's Bay |
| Kanalet seashell west of the gate | Game Boy and DX need the Flying Rooster; the Switch remake lets the Hookshot cross | Each version's own |

### Everything else

| Topic | Sources | Used |
|---|---|---|
| Bomb and Arrow capacity | 30 → 60 (the disassembly); 20 → 60 (Switch guides: Zelda Universe, Zelda Wiki's "triples") | 30 → 60, with the Switch's 20 in the notes |
| Which Mad Batter gives which upgrade | You choose at each one (Zelda Wiki, Zelda's Palace); Zelda Dungeon's items page ties Bombs to Martha's Bay and Arrows to the mountains | Your choice, tracked by upgrade |
| Honeycomb timing | After Key Cavern (Zelda Wiki) or after entering Kanalet Castle (Zelda Dungeon) | Both noted |
| The dog that trades the Dog Food | CiaoCiao (Zelda Wiki's trading table, Zelda Dungeon), YipYip (Zelda Wiki's Dog Food page), a mini BowWow (StrategyWiki) | CiaoCiao |
| Mamasha's name | Mamasha (Zelda Wiki, Zelda Dungeon), Mamahl (StrategyWiki) | Mamasha, noted |
| The Necklace | Martha's bikini top in the Japanese and European Game Boy releases; the Necklace in DX (Zelda Wiki's DX page says every localized DX, its Necklace page the European one) and on Switch | Necklace, noted |
| Photo 8, the fisherman | Needs the Magnifying Lens (StrategyWiki, Zelda's Palace), after the Fishing Hook (Zelda Dungeon), or just talk to him (Zelda Wiki) | Noted |
| Photo 2, the cliff | South-west of Toronbo Shores (Zelda Wiki, StrategyWiki, Zelda's Palace) or south-east (Zelda Dungeon) | South-west |
| The Color Dungeon's tunics | Red is attack and Blue defence (Zelda Wiki, Zelda Dungeon); Zelda's Palace swaps them | Red attack, Blue defence |
| Frog's Song of Soul | The Pegasus Boots reach Mamu on Game Boy and the Hookshot on Switch (Zelda's Palace); after the Hookshot (Zelda Wiki) | Both noted |
| The Egg's maze | Four possible paths (Zelda Wiki), seven (Zelda Dungeon) | "Set per save file" |

## Version notes kept in `notes`

- **DX:** the Color Dungeon (shared with Switch) and the twelve photos; Secret Medicine also in the
  last dungeons' chests; the Necklace in place of the Japanese bikini top.
- **Switch:** Koholint Sword at 40 seashells; Powerful Bracelet; Li'l Devils; bombs sold only after
  Bottle Grotto; Marin goes to the Walrus without the Honeycomb trade; the Goriya sells your item
  back for 300 Rupees; Manbo's Mambo warps to any Warp Point; Boss Heart Containers can be left
  behind; the Color Dungeon's guards sell Magic Powder instead of asking a riddle, and green can be
  had again; the Trendy Game's figures; Chamber Dungeons at Dampé's Shack.
