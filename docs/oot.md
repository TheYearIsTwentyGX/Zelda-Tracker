# Ocarina of Time

Ocarina of Time's trackers are checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): nine JSON files in
`shared/src/commonMain/composeResources/files/oot/`, listed in `core/Games.kt` after a Progress
Overview. `OotDatasetTest` checks the counts below; `ChecklistDatasetsTest` checks the format.

The data is for the **original N64 release**. Where Ocarina of Time 3D or Master Quest differ for an
item, its `notes` say so; there are no separate trackers for them. Nothing is fetched at runtime.

## Save files

Three: **File 1–3**. The N64 cartridge's SRAM holds three save slots and a backup of each
(zeldaret/oot, `src/code/z_sram.c`: `gSramSlotOffsets` lists slots 0–2 and "the latter three saves
are backup saves for the former saves"). Ocarina of Time 3D also has three. The placeholder in
`Games.kt` was already three, so the ids `file_1`–`file_3` are unchanged.

## Files and counts

| File | Tracker | Sections (ids) | Count |
|---|---|---|---|
| `main_quest.json` | Main Quest | Childhood (`childhood`), Seven Years Later (`adulthood`) | 6 + 12 steps; summaries: 3 Spiritual Stones, 6 Medallions |
| `items.json` | Items & Equipment | `inventory`, `equipment`, `upgrades`, `quest_items` | 18 items (2 tiered), 11 equipment, 8 upgrades (2–3 tiers each), 2 quest items |
| `heart_pieces.json` | Heart Pieces | `pieces`, `containers` | 36 + 8; Hearts `3 + pieces / 4 + containers` = 20 |
| `gold_skulltulas.json` | Gold Skulltulas | `skulltulas`, `rewards` | 100 tokens (56 overworld, 44 dungeon) + 6 House of Skulltula rewards |
| `songs.json` | Songs | `songs`, `warp_songs` | 6 + 6 |
| `great_fairies.json` | Great Fairies | `great_fairies` | 6 |
| `bottles.json` | Bottles | `bottles` | 4 |
| `big_poes.json` | Big Poes | `big_poes` | 10 |
| `trading.json` | Trading Sequences | Masks (`child`), Biggoron's Sword (`adult`) | 8 + 12, each step `requires` the one before |

Items are in the order you'd suggest doing them, since the overview's *Next* is the first one not
done. The Main Quest's summaries count Stones and Medallions with `value`: the steps that give one
have the default value 1 and the rest (meeting Zelda, mini-dungeons, Ganon) have `value: 0`.

Gold Skulltula and Heart Piece regions are the game's areas, in the order a player reaches them.
Tags on each give the age (`Child`, `Adult` or `Either`), `Night` for night-only Gold Skulltulas,
and the items or songs needed, so search and the tag line show what to bring.

### What's tracked, and what isn't

- **Items & Equipment** follows the pause subscreens: the Items grid (less the four bottles and two
  trade slots, which have their own trackers), the Equipment grid's swords, shields, tunics, boots
  and upgrades, and two Quest Status items (Stone of Agony, Gerudo's Card). The Kokiri Tunic and
  Boots you start with are left out. Deku Stick and Nut capacity aren't shown on a subscreen but
  are tracked with the upgrades. Magic and double defense come from the Great Fairies, so they're
  only in that tracker.
- **Upgrades that come in either order** (bullet bag, bomb bag, quiver, Deku Nut capacity) have
  tiers named by capacity, each giving both sources ("whichever you do first").
- **A few things appear in two trackers** because a player looks for them in both: the wallets and
  Stone of Agony (Items, and the House of Skulltula rewards), the 50-token Piece of Heart (Heart
  Pieces, and the rewards), Biggoron's Sword (Equipment, and the end of the adult trade) and the
  spells (Items, and the Great Fairies). The notes point to the other tracker.
- **Left out:** dungeon maps, compasses and keys; Magic Bean planting spots (the Gold Skulltulas
  and Heart Pieces they lead to are tracked); the Scarecrow's Song (you compose it, and it isn't on
  the Quest Status screen); the Goron, Zora and Gerudo Masks (borrowed for free, nobody buys them);
  minigames whose only prizes are tracked elsewhere; Gossip Stones and cows.

### Missable

- **Dampé's gravedigging Piece of Heart.** On the N64 its flag is set when it's dug up, so leaving
  the graveyard without picking it up loses it (fixed on the 3DS). The decomp (`z_en_tk.c`) draws
  prizes from a cycle that guarantees it within 15 digs in one visit.
- **Deku Nut capacity / the Poacher's Saw.** On the N64 the Mask of Truth's Deku Nut upgrade on the
  Forest Stage can't be had after you get the Poacher's Saw (Zelda Wiki, StrategyWiki; fixed on the
  3DS). The upgrade item and the saw are both marked missable.

## Sources

- **Zelda Wiki** (zeldawiki.wiki, read as wikitext through its API): Great Fairy and Great Fairy
  Fountain, Bottle, Wallet, Bomb Bag, Quiver, Bullet Bag, Golden and Silver Scale, Gauntlets, Magic
  Meter, Deku Stick and Deku Nut (upgrades), Trading Sequence, Happy Mask Shop, Running Man, Letter
  in a Bottle, Talon's Chicken Game, Cucco Lady, Poe Collector, Big Poe, the equipment pages, and
  the location and dungeon pages for Gold Skulltula counts.
- **StrategyWiki**, *The Legend of Zelda: Ocarina of Time*: Gold Skulltulas, Heart Containers (all 36
  pieces), Item Trading, The Happy Mask Shop, Ocarina Songs (the notes), Upgrades and Fairy
  Fountains.
- **Zelda Dungeon**: the Gold Skulltula, Heart Piece and Big Poe guides. The live site blocks
  automated fetches, so they were read from Wayback Machine copies (2021 for the first two, 2026 for
  the Big Poes).
- **GameFAQs** couldn't be fetched (HTTP 403). The well-known fan guides used in its place:
  **Flying Omelette**'s Gold Skulltula FAQ, **Zelda Universe**, **Zelda's Palace**, **Jegged** and
  **GameWith**.
- **zeldaret/oot**, the N64 decompilation, to settle disagreements: `z_sram.c` (save slots),
  `z_fishing.c`, `z_en_tk.c` (gravedigging), `z_en_po_relay.c` (Dampé's race), `z_en_gb.c` (Poe
  Collector) and `z_en_po_field.c` (Big Poe spawns).

## Discrepancies

### Gold Skulltulas

All five sources agree on 100 and on every dungeon's count.

| Topic | Sources | Used |
|---|---|---|
| The adult one by the ruined gate outside Ganon's Castle | StrategyWiki, Zelda Dungeon and GameWith list it apart from Hyrule Castle; Zelda Wiki's Hyrule Castle page counts only the 2 child ones | Its own region, *Outside Ganon's Castle* (Hyrule Castle 2) |
| Night-only: Lakeside Lab pool crate, Goron City centre platform | StrategyWiki says night; the others don't (both are indoors) | Any time, noted |
| Night-only: Kakariko tree | StrategyWiki and Flying Omelette say night; Zelda Dungeon and GameWith don't say | Night |
| Hookshot or Longshot (Zora's River south wall, Gerudo Valley behind the tent, Gerudo's Fortress roof, Zora's Fountain hidden cave) | Zelda Dungeon or StrategyWiki say Longshot, others the Hookshot | Hookshot, with the Longshot view in the notes |
| Shadow Temple Like Like room, single and triple skull pots | StrategyWiki says Longshot; the others name no item | Longshot, noted |
| Death Mountain Trail cracked wall | Climb the wall (StrategyWiki, Zelda Dungeon) or Boomerang (Flying Omelette, GameWith) | Bombs; both ways in the notes |
| Hyrule Castle Song of Storms grotto | Only GameWith names the Boomerang | Included (the token is up on a wall) |
| Dodongo's Cavern scarecrow one | StrategyWiki says either age (a child statue-and-backflip trick) | Adult, trick noted |
| Fire Temple boss key loop; Spirit Temple Time Block room | Sources describe the route only | Megaton Hammer and Hookshot added from the route; worth a check in play |

### Pieces of Heart and Big Poes

| Topic | Sources | Used |
|---|---|---|
| Child fishing weight | 10 lb (Zelda Dungeon, Zelda Universe, Zelda's Palace), 9 lb (StrategyWiki), 4 lb over the record (Zelda Wiki) | "About 10 lb"; the decomp's threshold shows as about 9 lb |
| Dampé's race | First race or a later one | A later race, in 60 s or less, once you have the Hookshot (decomp) |
| Ages: ReDead grave, Windmill, ledge above Dodongo's Cavern, flooded Hyrule Field grotto | Sources split Child / Adult / Either | Either, with each route in the notes |
| Man on the roof | Adult only (Zelda Universe, Zelda's Palace); child side-jump too (Zelda Dungeon, StrategyWiki) | Adult, child trick noted |
| Gerudo Valley crate needs Iron Boots; a bean Piece of Heart in Gerudo Valley; Horseback Archery "1,000 points twice" | Zelda Universe only | Rejected |
| Big Poe on the silver boulder fork | Kokiri Forest road (Zelda Dungeon, Zelda Universe, GameWith) or Kakariko road (Jegged) | Kokiri Forest road |
| Big Poes on foot | Horseback only (most guides) or sometimes on foot (Zelda Wiki) | The decomp gives a 40% chance on foot, so the howTo says to ride |

Each Big Poe's notes give its number in Zelda Dungeon's, Zelda Universe's, Jegged's and GameWith's
guides, which number the same ten spots differently.

### Everything else

| Topic | Sources | Used |
|---|---|---|
| Golden Scale | A fish at least 4 lb over the starting record (Zelda Wiki); 13 lb or more (StrategyWiki) | "A fish that beats the Fishing Pond record", both in the notes |
| Running Man (Bunny Hood) | Hyrule Field (Zelda Wiki); around Lon Lon Ranch (StrategyWiki) | Hyrule Field; both in the notes |
| The Odd Mushroom's product | "Odd Potion" (N64), "Odd Medicine" (StrategyWiki), "Odd Poultice" (Zelda Wiki, from the 3DS) | Odd Potion; the 3DS name in the notes |
| Big Bomb Bag's name | Big Bomb Bag (Zelda Wiki), Bigger Bomb Bag (StrategyWiki) | Zelda Wiki's names for the bomb bags and quivers; "Bigger"/"Biggest" for bullet bags, as Zelda Wiki has them |

The songs' notes, the Great Fairies' rewards and the adult trade's timers (3, 3 and 4 minutes)
agree across Zelda Wiki and StrategyWiki.

## Version notes kept in `notes`

- **3DS:** Shard of Agony for the Stone of Agony; Hover Boots become an item; the frog game uses
  different buttons; the gravedigging and Poacher's Saw bugs are fixed; the Water Temple's gated
  Gold Skulltula needs no spin attack.
- **Master Quest:** only the dungeons change. Each keeps its Gold Skulltula count but the spots
  move; the Ice Cavern's Piece of Heart takes a bomb on a frozen switch instead of Blue Fire. The
  overworld, songs, trades and upgrades are the same.
