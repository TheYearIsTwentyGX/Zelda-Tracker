# A Link Between Worlds

Every A Link Between Worlds tracker is a checklist (`docs/checklist-trackers.md`). The data is in
`shared/src/commonMain/composeResources/files/albw/`. `AlbwDatasetTest` checks the counts below and
the links between files, and `ChecklistDatasetsTest` checks the format, ids, regions and
`requires`. `AlbwOverviewTest` checks the overview rows.

Nothing is fetched at runtime: the JSON was compiled offline from the sources below, with scripts
that aren't committed (as for A Link to the Past).

## Save files

**Three files.** StrategyWiki's *Gameplay* page: "There are three files to choose from." The other
sources don't give a count. `Games.ALBW` already had three placeholder files, so their ids
(`file_1`–`file_3`) didn't change. Hero Mode, unlocked by finishing the game, starts a new file in
one of the same three slots.

**Release:** North America and Europe 22 November 2013, Australia 23 November, Japan 26 December
(Zelda Wiki's infobox, citing the *Encyclopedia*). `ReleaseDate(2013, 11, 22)` was already right.

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | — | A row per checklist below (`ChecklistProgressProvider`) |
| Main Quest | `main_quest.json` | 10 Hyrule steps (the Captain's sword, Zelda's Pendant of Courage, Sahasrahla, the Eastern Palace, Queen Oren's Smooth Gem, Rosso's Power Glove, the House of Gales, the Tower of Hera, the Master Sword, Hyrule Castle) and 11 Lorule steps (the 7 Sage dungeons, the Triforce of Courage, Lorule Castle, Yuga Ganon, the ending). Pendants = `hyrule.value` (0–3), Sages = `lorule.value` (0–7) |
| Items & Equipment | `items.json` | Ravio's Shop: 9 items × 3 tiers (Rented, Bought, Nice *X*); Equipment: 14 rows, 22 steps (sword ×4, shield ×2, mail ×2, gloves ×2, Lamp and Net ×2, and 8 single items); Master Ore: 4; Bottles: 5. Nice upgrades = `ravio.value` (0–9): only the Nice tier is worth 1 |
| Lost Maiamai | `maiamais.json` | 100 Maiamai, 50 per world, `area` = the in-game Maiamai Map area (9 in Hyrule, 7 in Lorule). Rewards earned = `floor(maiamai / 10)` (0–10: nine Nice upgrades and the Great Spin) |
| Heart Pieces | `heart_pieces.json` | 28 Pieces of Heart (18 Hyrule, 10 Lorule) and 10 Heart Containers. Hearts = `3 + floor(pieces / 4) + containers`, 3 to 20 |
| Mini-games | `minigames.json` | 7 prize games, 13 steps: Cucco Ranch (Egg, Chick, Rooster), both Rupee Rushes, Hyrule Hotfoot (75 s, 65 s), Lorule's Fortune's Choice, Octoball Derby and the Treacherous Tower (Beginner, Intermediate, Advanced, Advanced again) |

**Ravio's items.** Each is one row with three tiers: rented, bought, then upgraded by Mother
Maiamai. Ticking *Bought* ticks *Rented* too, which reads as "had it at least on loan"; the tiers
are there so a row shows how far along an item is. Mother Maiamai only upgrades bought items, so
the order holds for the last two.

**The Great Spin** isn't a row: it's Mother Maiamai's reward for all 100, the tenth of the Lost
Maiamai tracker's *Rewards earned*. The Super Lamp and Super Net are tiers of the Lamp and Net
(Equipment) and of the Treacherous Tower (Mini-games), since both trackers name them.

Not tracked:
- **Hint Ghosts.** Hints bought with Play Coins (Zelda Wiki), not collectibles.
- **Monster Tail, Horn and Guts.** Stackable consumables from chests and Fortune's Choice.
- **The Street Merchant.** A shop: its Bottle is in Bottles and its Smooth Gem in the Main Quest.
- **Weather Vanes.** Save points and the Bell's warp spots, 22 by Zelda Wiki's list. Visiting them
  isn't something the game or any source counts toward completion, and Zelda Dungeon names only 19.
- **Hyrule's Fortune's Choice.** Pays only Rupees; Lorule's (which has a Piece of Heart) is tracked.
- **Hero Mode.** A new file, not progress on this one; it's mentioned in the ending's notes.
- **Treasure Hunter caves, StreetPass Shadow Link battles, Rosso's rock-clearing chest and the
  Great Rupee Fairy's fairies:** Rupees only.
- **Smooth Gem, Letter in a Bottle, Premium Milk, Bow of Light, Scoot and Foul Fruit, Maiamai Map,
  Energy Gauge.** Quest items handed over, a final-fight item, consumables, or things that come
  with another row.

**Missable: nothing.** No source flags any item, piece or Maiamai as missable for good, so no item
sets `missable`.

## Regions

Zelda Wiki's two worlds, **Hyrule** and **Lorule**. An item's world is where Link picks it up; the
place goes in `area`. Items & Equipment has no regions, as for ALttP: an item's tiers span both
worlds, and region headings would sort before *Ravio's Shop*. Master Ore and Bottles name the world
in `area`.

One row on the Lorule tab of Main Quest stands in Hyrule: the **Desert Palace** is in Hyrule's
desert, so its region is Hyrule, though it's a Sage dungeon. Its boss is through a Fissure
in Misery Mire, so its Heart Container's region is Lorule.

Order follows the story, since the overview's *Next* is the first undone item:
- Main Quest follows Zelda Dungeon's walkthrough. `requires` holds the hard gates: the Eastern
  Palace needs Sahasrahla, the House of Gales the Smooth Gem (Zora's Flippers), the Tower of Hera
  the Power Glove, the Master Sword all three Pendants, Hyrule Castle the Master Sword and every
  Lorule dungeon Hyrule Castle. The Desert Palace also needs the Thieves' Hideout, since the Sand
  Rod only comes back to Ravio after it. The Triforce of Courage needs all seven Sages, Lorule
  Castle needs the Triforce, and Yuga Ganon and the ending follow.
- Lost Maiamai and Heart Pieces follow Zelda Dungeon's numbering, which tracks its walkthrough.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), wikitext through the MediaWiki API: *Lost Maiamai*, *Mother
  Maiamai*, *Maiamai Map*, *Piece of Heart*, *Heart Container*, *Ravio's Shop*, *Bottle*, *Weather
  Vane*, *Treacherous Tower*, *Mini-Games in A Link Between Worlds* and each mini-game, *Master
  Ore*, the sword pages, *Hylian Shield*, *Lamp*, *Bug-Catching Net*, *Bell*, *Pouch*, *Bee Badge*,
  *Golden Bee*, *Smooth Gem*, *Street Merchant*, *Letter in a Bottle*, *Pendants of Virtue*, *Seven
  Sages* and each Sage, *Yuga Ganon*, *Triforce of Courage*, *Hero Mode*, and every dungeon page.
- **Zelda Dungeon** (zeldadungeon.net), from Wayback Machine snapshots (2024–2026) because the site
  blocks scripted requests: the numbered *Maiamai Locations* (1–100) and *Heart Pieces* (1–28),
  *Bottles*, *Item Upgrades*, *Master Ore Locations*, *Items*, *Bosses*, *Ravio's Shop*,
  *Treacherous Tower*, *Hero Mode*, and all 12 walkthrough chapters with their "New in this
  section" boxes.
- **IGN wiki**: *Maiamai Locations*, *Heart Pieces*, *Empty Bottles*, *Ravio*, *Mother Maiamai*,
  *Master Ore Locations*, *Mini-Games*.
- **Zelda Universe** guides (Wayback snapshots): *Pieces of Heart*, *Lost Maiamais*, *Bottles*.
  Added as a third list where IGN's is incomplete and Zelda Wiki's a stub.
- **StrategyWiki**: *Gameplay*, for the save files.

### Counts

| | Zelda Wiki | Zelda Dungeon | IGN | Zelda Universe |
|---|---|---|---|---|
| Lost Maiamai | 100 (50 + 50, by map area) | 100 (numbered) | 95 (no Lorule Lake) | 100 |
| Pieces of Heart | 28 (no list) | 28 | 28 (one listed twice) | 28 (18 + 10) |
| Heart Containers | 10 | 10 | — | — |
| Max hearts | 20 | — | — | — |
| Bottles | 5 | 5 | 5 | 5 |
| Master Ore | 4 | 4 | 4 | — |
| Ravio's items | 9 | 9 | 9 | — |
| Pendants / Sages | 3 / 7 | 3 / 7 | — | — |

Maiamai per area (Zelda Wiki, as on the in-game map). Hyrule: Lake Hylia 5, Hyrule Castle 8,
Kakariko Village 5, Southern Ruins 3, Eastern Ruins 6, Lost Woods 8, Sanctuary & Zora's Domain 7,
Death Mountain 5, Desert 3. Lorule: Thieves' Town & Lorule Castle 15, Graveyard 3, Misery Mire 3,
Dark Ruins 10, Skull Woods 8, Lorule Lake 5, Death Mountain 6.

## Discrepancies and how they were settled

**Lost Maiamai**
- IGN has no Lorule Lake section (95 in all). The other three have its five; 100 is used.
- The sources group and number Maiamai differently. Each row's `notes` gives Zelda Dungeon's
  number, Zelda Wiki's area number and IGN's position. Zelda Dungeon and Zelda Wiki were matched by
  description; four matches rest on that alone and are marked "(probably)".
- What a Maiamai needs: Zelda Universe adds the Hammer for one Death Mountain rock; Zelda Dungeon
  lists only Bombs for the Southern Ruins cave pool where Zelda Wiki also dives (Flippers); Zelda
  Dungeon allows the Hammer or Titan's Mitt for a Lost Woods rock where IGN and Zelda Universe say
  the Mitt. All are noted.

**Heart Pieces**
- Zelda Wiki's ALBW list is a stub, so the 28 pieces were checked between Zelda Dungeon, IGN and
  Zelda Universe; all 28 match. IGN lists Fortune's Choice twice.
- **Hyrule/Lorule split.** Zelda Dungeon says "the first 14 can be acquired while in Hyrule, the
  next 14 … Lorule", which is about *when*. Four of its second 14 are picked up in Hyrule (the
  Stylish Woman, the Graveyard cliff cave, the Death Mountain spire and the cave by the
  Blacksmith's), giving 18 and 10, as Zelda Universe's headings have. `AlbwDatasetTest` checks it.
- Graveyard cliff cave: Zelda Dungeon and Zelda Universe need Bombs for a cracked wall; IGN
  doesn't mention it. Noted.

**Items**
- **Rental prices.** Sand Rod 50 (Zelda Wiki, Zelda Dungeon) or 100 (IGN): 50 used. Zelda Wiki's
  *House of Gales* rents the Tornado Rod for 20; its own shop table, Zelda Dungeon and IGN say 50:
  50 used. Zelda Wiki alone says re-renting the Bow costs 10 before the Eastern Palace. Noted.
- **When Ravio's Shop opens.** After returning the Smooth Gem (Zelda Wiki), or once you have the
  Flippers or have taken the Power Glove into the Death Mountain cave (Zelda Dungeon). Both are in
  the Smooth Gem step's notes. Buying starts with the three Pendants (both).
- **Swords.** Zelda Dungeon's *Captain's Sword* is Zelda Wiki's *Forgotten Sword*; the row uses the
  former and notes the latter. The upgrades are Master Sword Lv2 and Lv3 in this game's text;
  Zelda Dungeon's "Tempered Sword" page title is an ALttP carry-over.
- **Mail.** Zelda Wiki says whichever of the Swamp Palace and Lorule Castle chests is opened first
  holds the Blue Mail. Lorule Castle needs every Sage, so the Swamp Palace always comes first:
  Blue Mail there, Red Mail in Lorule Castle.
- **Letter in a Bottle.** East of the House of Gales / Lake Hylia (Zelda Dungeon, IGN, Zelda
  Universe) or the river east of Hyrule Castle (Zelda Wiki). The majority is used; both noted.
- **Hylian Shield chest.** Zelda Wiki's *Hylian Shield* says after the four Fire Wizzrobes on the
  first floor; its *Turtle Rock* page and Zelda Dungeon say past the grate bridge on B1. The B1
  version is used; both noted.
- **Golden Bee.** 9,999 Rupees in Lorule's shops (Zelda Wiki's shop tables) or 888 from a man near
  the Lost Woods (Zelda Wiki's *Golden Bee* only). Noted as unconfirmed.
- **Cucco Ranch** is Cucco Rush to Zelda Dungeon and Zelda Universe, Cucco Dash to IGN. Zelda
  Wiki's name is used.

**Main Quest**
- Bosses, Sages, Pendants and treasures agree everywhere. The Pendant of Courage comes from Zelda
  at Hyrule Castle, not the Eastern Palace, which gives none. `AlbwDatasetTest` checks that each
  dungeon's "Treasure:" names something in Items & Equipment (8 of them) and that the 10 Heart
  Containers match the Main Quest steps that give one.
- Where the Sages give the Triforce of Courage is unnamed in the game: Zelda Wiki's *Seven Sages*
  calls it the Chamber of the Sages, its *Triforce* page the Temple of Light. The row uses the
  former.

**Unverified:** whether Mother Maiamai keeps count of Maiamai brought while Link owns nothing she
can upgrade. No source says.
