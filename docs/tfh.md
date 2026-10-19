# Tri Force Heroes

Every Tri Force Heroes tracker is a checklist (`docs/checklist-trackers.md`). The data is in
`shared/src/commonMain/composeResources/files/tfh/`. `TfhDatasetTest` checks the counts below and
the links between files, and `ChecklistDatasetsTest` checks the format, ids, regions and
`requires`. `TfhOverviewTest` checks the overview rows.

The trackers follow the game as patched (Ver. 2.1.0, the last update), so they include the Den of
Trials and the two outfits from the free Ver. 2.0.0 update. There was no paid DLC. Nothing is
fetched at runtime: the JSON was compiled offline from the sources below, with scripts that aren't
committed.

## Save files

**One file.** Zelda Wiki's *Saving*: "Tri Force Heroes is the only main series game that does not
allow for multiple save files." Perfectly Nintendo's spec list, from the Japanese site, gives "Save
data files: 1". `Games.TFH` had three placeholder files; it now has one, labelled *Save File*. It
keeps the id `file_1`, so nothing stored under it moves.

**Release:** Japan 22 October 2015, North America and Europe 23 October, Australia 24 October
(Zelda Wiki, citing the *Encyclopedia*; IGN "Initial Release: Oct 22, 2015").
`ReleaseDate(2015, 10, 22)` was already right.

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | — | A row per checklist below (`ChecklistProgressProvider`) |
| Adventure | `adventure.json` | Regions: the 8 areas. 32 levels (4 per area) with their items, bosses and end-chest materials; 96 Drablands Challenges (3 per level); and 9 King Tuft rewards (an area's gold material for its 12 challenges, and the Gold Rupee for the full list). Completion List = `levels + challenges` (0–128, the in-game board); Areas cleared = the eight fourth levels (0–8) |
| Outfits | `outfits.json` | All 38 outfits, each with its effect, Madame Couture's recipe and price, and when it unlocks. Tags say how the unusual ones are got (Story, Gift, Daily Riches, Friendly Tokens, Street Merchant, Gold materials, Ver. 2.0.0) |
| Den of Trials | `den_of_trials.json` | The 8 zones of 5 floors (B1–B40), in order, ending with three Shadow Links and the Fierce Deity Armor |

**How challenges open.** Zelda Wiki (*Drablands Challenge*, *Level*) and Prima agree: clearing an
area's fourth level opens all 12 of its challenges at once, and the levels within an area can be
played in any order. So every challenge `requires` its area's fourth level, and every level
`requires` what opens its area:

| Area | Opened by |
|---|---|
| Woodlands | — |
| Riverside, Volcano, Ice Cavern | Forest Temple |
| Fortress | Water, Fire and Ice Temples |
| The Dunes, The Ruins | The Lady's Lair |
| Sky Realm | Desert and Grim Temples |

The levels come first in the file, then the challenges, so the overview's *Next* runs through the
story before the challenges.

**King Tuft's rewards** are kept as their own tab: each is a one-off gold material (Supple Leaf,
Thornberry…) that only he gives outside the Coliseum, and three outfits need them. They count
toward the tracker's bar (137) but not the *Completion List* (128), which matches the game's board.

Not tracked:
- **The Coliseum.** PvP: a win pays 20 Rupees and a random material, with no counts, titles or
  one-time prizes. Its gold materials also come from King Tuft and, since Ver. 2.1.0, from the
  Street Merchant. Online play ended with the 3DS network.
- **Daily Riches, the Street Merchant, the Faux Hero and Hero Points.** Repeatable shops and
  counters. Their one lasting reward each (the Dapper Spinner, the Cheer Outfit's Frilly Fabric,
  Friendly Tokens for the Tri Suit and Timeless Tunic, the Bear Minimum's power-up) is in Outfits.
- **The Drablands Diary** (9 volumes) and the "scenic spots" it hints at in each level. The game
  records none of it, and Zelda Wiki doesn't cover every spot reliably.
- **Materials.** Seventy of them, gathered repeatedly; each outfit's recipe names what it needs.
- **The Den of Trials full run.** Zelda Wiki says clearing all 40 floors from B1 in one session
  leaves eight chests of materials at the bottom. No other source checked mentions it.
- **Miiverse stamps.** Miiverse closed in 2017.

**Missable: nothing.** Levels and challenges can be replayed, and the Lady's Glasses, Collar and
Parasol come with each story level's first clear.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), raw wikitext: *Drablands*, *Drablands Challenge*, *Level* and
  all 32 level pages (items, challenges and a materials table per challenge), *Outfit* and the
  pages of several outfits, *Magical Material*, *Den of Trials*, *Coliseum*, *Street Merchant*,
  *Daily Riches*, *Friendly Token*, *Hero Point*, *Drablands Diary*, *Saving*, and the game's page.
- **Prima Games' official eGuide**, the free part: the walkthrough page of every level, the
  *Odds and Ends* challenge pages ("Challenge 1/2/3" with materials), *Challenge Types*, the outfit
  pages (effects only) and the Coliseum overview.
- **IGN wiki**: *Costumes* (38, with costs), *Materials and Collectibles*, *Den of Trials*, *DLC*,
  *The Coliseum* and the world pages.
- **Zelda Dungeon** (zeldadungeon.net), through Wayback Machine snapshots because the site blocks
  scripted requests: *Tri Force Heroes Levels* (2021) and *Tri Force Heroes Outfits* (2021). Its
  other TFH pages weren't archived with content.
- **Perfectly Nintendo** (17 September 2015), for the save-file count.

### Counts

| | Zelda Wiki | Prima | IGN | Zelda Dungeon |
|---|---|---|---|---|
| Areas | 8 | 8 ("World 1–8") | 8 | 8 |
| Levels | 32, same names and order | 32 | 32 | 32, same names and order |
| Challenges | 96 (3 per level; 20 types) | 96 ("Challenge 1–3" on all 32) | — | — |
| Completion List | 128 | — | — | — |
| Outfits | 38 | 36 pages (before Ver. 2.0.0) | 38 (2 as "DLC Costumes") | 36 |
| Outfit Rupee total | 32,800 | — | — | — |
| Den of Trials | 40 floors, 8 zones | — | 40 floors, 8 zones | listed as a level |

Each level's three challenges, their order and every challenge's reward materials were compared
between Zelda Wiki's per-level tables and Prima by script: identical apart from the wording below.

## Discrepancies and how they were settled

**Challenges**
- **Wording.** Names follow Zelda Wiki ("Fewer Heart Containers!"); Prima uses title case without
  the "!". Zelda Wiki writes both "Guard the Orb!" and "Guard the orb!"; the second is used, like
  "Transport the orb quickly!". Moblin Base's "Defeat all foes sans sword!" is Prima's "Defeat All
  Foes Without a Sword"; Zelda Wiki's is used and Prima's noted. The exact in-game strings weren't
  confirmed from a screenshot.
- **Order.** Zelda Wiki's prose lists some levels' challenges in a different order from its own
  tables (Frozen Plateau, Sealed Gateway, Infinity Dunes and four more). The tables, which match
  Prima, are used.
- **Fewer Heart Containers:** "reduced by five" (Zelda Wiki) and "a mere four" instead of nine
  (Prima) agree.

**Outfits**
- **Count.** Zelda Dungeon's list has 36: it leaves out the Lady's Ensemble and the Cursed Tights,
  which Zelda Wiki and IGN both list (38). Prima's 36 pages predate Ver. 2.0.0's Linebeck's Uniform
  and Fierce Deity Armor. 38 is used.
- **Names.** Zelda Wiki and the in-game Catalog (as Zelda Wiki quotes it) say *Bear Minimum*,
  *Dapper Spinner* and *Robowear*; Zelda Dungeon says *Bear Minimum Digs*, *Dapper Spinner Kit* and
  *Robo Wear*, and IGN also says *Dapper Spinner Kit*. Zelda Wiki's are used. Prima calls the
  Cursed Tights "Kinda Cursed Tights", which Zelda Wiki gives as the name on receipt.
- **Torrent Robe:** 1 Fresh Kelp (Zelda Wiki) or 2 (IGN). **Sword Master Suit:** 2,000 Rupees
  (Zelda Wiki) or 1,500 (IGN). Zelda Wiki's are used, since its own outfit list and pages agree and
  only its prices add up to its stated 32,800 total.
- **Cursed Tights:** King Tuft gives them once the curse is lifted (Zelda Wiki), or Princess Styla
  after the game is beaten (IGN): the same moment, since the Lady's Ensemble needs the Sky
  Temple's Parasol. The row `requires` the Lady's Ensemble.
- **Friendly Tokens** from the Street Merchant cost 2,000 Rupees (Zelda Wiki) or 3,000 (IGN). In
  notes only.
- **Materials.** Zelda Wiki's trivia says all outfits need 115 materials; the recipes add up to
  116, perhaps counting the Hero's Tunic's Hytopian Silk, which Sir Combsly gives. Unresolved.

**Adventure**
- **King Tuft's Gold Rupee** (300 Rupees for the whole Completion List) is from Zelda Wiki only;
  IGN confirms the eight gold materials.
- Bosses and mini-bosses agree between Zelda Wiki and Prima. The Lady's Lair boss is "The Lady's
  Pets" to Zelda Wiki; Prima names the three it brings back (Margoma, Arrghus and Moldorm).
