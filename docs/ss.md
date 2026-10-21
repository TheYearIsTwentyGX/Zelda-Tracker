# Skyward Sword

Skyward Sword's trackers are checklists (`TrackerKind.CHECKLIST`, see `docs/checklist-trackers.md`):
eight JSON files in `shared/src/commonMain/composeResources/files/ss/`, listed in `core/Games.kt`
after a Progress Overview. After them comes one tool, the [Thrill Digger Solver](#thrill-digger-solver)
(`TrackerKind.THRILL_DIGGER`). `SsDatasetTest` checks the counts below and the links between files, and
`ChecklistDatasetsTest` checks the format. No engine change was needed.

The data is for the **Wii original (2011)**. Where **Skyward Sword HD** (Switch, 2021) differs for an
item, its `notes` say so, starting `HD:`: button controls (swing and aim with the right stick, the
tightrope and minigames with the left), the Zelda & Loftwing amiibo's fast travel between the Surface
and the Sky (on the Sailcloth and the first Goddess Cube), autosave and Hero Mode's own slot (on
Demise), item descriptions shown only the first time, and the Song of the Hero glitch that HD removed.
**No count or price differs:** an HD guide (Samurai Gamers) gives the same 24 Pieces of Heart,
27 Goddess Cubes, 80 Gratitude Crystals, 5 Bottles, 8 pouch slots, 10 shields, 12 bugs and
16 treasures, and the same shop and Scrap Shop prices, as the Wii sources. The JSON was compiled
offline from the sources below with scripts that aren't committed, as for the other games. Nothing is
fetched at runtime.

## Save files

**Three: File 1–3.** The zeldaret/ss decompilation (`include/toBeSorted/file_manager.h`) keeps
`SaveFile saveFiles[3]`, and Zelda Wiki's *Saving* page and Samurai Gamers' save guide give three for
HD. HD adds an autosave slot above the three on the file select. It holds the autosave of the file
you're playing, so it isn't a fourth file to track. On the Wii, Hero Mode is saved over the cleared
file; HD lets it go in a slot of its own. The placeholder in `Games.kt` was already three, so the ids
`file_1`–`file_3` are unchanged.

## Files and counts

| File | Tracker | Sections (ids) | Count |
|---|---|---|---|
| `main_quest.json` | Main Quest | `quest`, `silent_realms`, `hero_song`, `sacred_flames`, `sword` | 30 story steps with the 7 dungeons, 4 Silent Realms, 4 parts of the Song of the Hero, 3 Sacred Flames, and the sword's 5 forms as tiers of one row (46 steps). Summaries: Dungeons `quest.value` (of 7), Silent Realms (of 4), Sacred Flames (of 3, on its tab) |
| `items.json` | Items & Equipment | `equipment`, `capacity`, `potions`, `pouch`, `bottles`, `wallets` | 13 items (21 steps: Slingshot, Bug Net, Beetle, Digging Mitts and Bow with their upgrades as tiers), 3 capacity items (Seed Satchel, Bomb Bag, Quiver; 3 tiers each), 5 potion infusions (7 steps), 5 Adventure Pouch rows, 5 Bottles, the Wallet (4 tiers) and Extra Wallets (3). Summaries: Pouch slots `pouch.value` (4 to 8), Rupees you can carry `300 + wallet.value + extra_wallets.value` (300 to 9,900) |
| `heart_pieces.json` | Heart Pieces | `pieces`, `containers` | 24 + 6; Hearts `6 + pieces / 4 + containers`, 6 to 18 |
| `goddess_cubes.json` | Goddess Cubes | `cubes` | 27 cubes, each with two tiers: *Cube struck* (value 1) and *Chest opened* (value 0), so 54 steps. Summaries: Goddess Cubes struck `cubes.value` and Goddess Chests opened `cubes - cubes.value`, each of 27 |
| `gratitude_crystals.json` | Gratitude Crystals | `crystal_quests`, `loose_crystals`, `batreaux` | 12 quests (11 × 5 and Orielle's Loftwing × 10 = 65, as `value`), 15 loose crystals, and Batreaux's 7 rewards (5, 10, 30, 40, 50, 70, 80). Gratitude Crystals `crystal_quests.value + loose_crystals.value` = 80 |
| `side_quests.json` | Side Quests & Minigames | `minigames`, `side_quests`, `lightning_round` | 7 minigames (15 steps: prize tiers), 3 side quests with no crystals (5 steps), and the Lightning Round's boss rush (4, 8 and 11 wins) and Silent Realm time trials (4) |
| `shields.json` | Shields | `shields` | Wooden, Iron and Sacred shields with their two upgrades each as tiers, and the Hylian Shield: 10 |
| `bugs_treasures.json` | Bugs & Treasures | `bugs`, `treasures` | 12 + 16, one of each |

Ids are unique across all eight files (`SsDatasetTest` checks). Items are in the order you'd suggest
doing them, since the overview's *Next* is the first one not done. The Main Quest is one chain: every
story step `requires` the one before it. Each trial `requires` the step that teaches its song, each
Sacred Flame its dungeon, and Levias's part of the Song of the Hero the three dragons' parts. Dungeons
have the default `value` 1 and every other step `value: 0`. Dungeon notes start `Boss: X.`; the six
Heart Containers are those six bosses (Sky Keep has none: Dreadfuse is a mini-boss).

**Regions.** Location lists (Heart Pieces, Goddess Cubes, Gratitude Crystals, Side Quests) use the
game's areas, in this order, each file only those it needs: Skyloft, The Sky, Thunderhead, Sealed
Grounds, Faron Woods, Lake Floria, Eldin Volcano, Volcano Summit, Lanayru Mine, Lanayru Desert,
Lanayru Sand Sea, Lanayru Gorge. Dungeons are in `area`, under the region they're in. The Main Quest,
Items, Shields and Bugs & Treasures have no regions, like Link's Awakening's Main Quest: each row has
its place in `area`, and bugs and treasures, found in several places, carry their regions as tags.

**Links between files**, which the test checks:
- The 6 Goddess Chests holding a Piece of Heart are the 6 pieces tagged *Goddess Chest*.
- The 7 pieces tagged *Minigame* or *Side quest* are the 7 rows tagged *Piece of Heart* in Side
  Quests & Minigames (6) and Gratitude Crystals (Batreaux's at 10).
- The Adventure Pouch expansion and the Thunderhead Bottle in Items & Equipment each come from one
  Goddess Chest.

A few things are in two trackers on purpose, because a player looks for them in both: the wallets
(Items, and Batreaux's rewards), the Hylian Shield (Shields, and the Lightning Round), and the Pieces
of Heart above.

### What's tracked, and why

Skyward Sword has no completion percentage, so these follow what SS 100% guides and checklists list
(Zelda Dungeon's collectible pages, Zelda Wiki's per-game lists, and the Skyward Sword randomizer's
check list, which is every item location in the game): the dungeons, Silent Realms and Song of the
Hero; the Pieces of Heart and Heart Containers; the Goddess Cubes and their chests; the Gratitude
Crystals and Batreaux; the minigames' prizes; and the upgrades. Bugs and treasures fill the game's own
Collection screen and are what the upgrades are made of, so completionists track one of each.

- **Items** are the item wheel and the worn gear: 13 items. Quest items (the tablets, the Ancient Sea
  Chart, the Stone of Trials, the Triforce) are Main Quest rewards, and the swords are the Main Quest's
  *Sword* row. The Practice Sword, only lent for the Waterfall Cave, is in that row's notes.
- **Upgrades** at Gondo's Scrap Shop are each item's tiers, with the treasures, bugs and Rupees for
  each. The "Used in …" note on every bug and treasure is worked out from the same recipe table, so
  they can't disagree; each treasure's total matches Zelda Wiki's *Quantities Required* table.
- **Potion infusions** are listed (all three sources give the same costs but one), but an infused
  potion is used up when drunk, so the tab counts each infusion made at least once.
- **Goddess Cubes** are one row per cube with the chest as its second tier, since a cube is pointless
  without its chest and the chest can't be opened without it. The two totals count each.
- **The Lightning Round** is in Lanayru Gorge with the Thunder Dragon (not the Thunderhead). Its
  12th win (Demise) is possible only in Hero Mode, so it's in the 11-win tier's notes, not a tier.
- **Left out:** Medals (pouch items, mostly from Goddess Chests: they're named as those chests'
  rewards); ammunition and the plain potions; Mia the cat (no reward on the Wii; HD adds a Red Rupee);
  the two Spiral Charge boulder chests (ordinary chests); shops and services (Beedle's wares, Strich,
  Sparrot's fortunes, the Item Check); shield repair prices; Life Medals' extra hearts (the Hearts
  total counts permanent hearts, 18 at most); Gorko's later Goddess Wall prizes and the Silent Realms'
  Tears of Light (in notes).

### Missable

Only **Fledge's barrels** (20 Rupees, before the Wing Ceremony). No Piece of Heart, Heart Container,
Goddess Cube, chest or Gratitude Crystal can be lost for good: dungeons can be re-entered, the Volcano
Summit and Lanayru Gorge stay open after the Song of the Hero, and the flooded Faron Woods drains.
Cawlin's letter and Peatrice's admirer each have two endings, but both give 5 crystals.

## Sources

- **Zelda Wiki** (zeldawiki.wiki, read as wikitext through its API): Piece of Heart, Heart Container,
  Goddess Cube, Goddess's Treasure Chest, Gratitude Crystal, Batreaux, Side Quests in Skyward Sword
  and each quest's page, Mini-Games in Skyward Sword and each minigame, Thunder Dragon's Lightning
  Round, Lumpy Pumpkin, Pumm, Kina, Owlan, Fledge, Gorko; every dungeon, Sky Keep, Sacred Flame,
  Silent Realm, Song of the Hero, the songs and swords, Levias; Upgrade (the Scrap Shop and Infusion
  tables), Items in Skyward Sword and each item, Adventure Pouch, Beedle's Airshop, Gear Shop, Bottle,
  Wallet, Extra Wallet, Potion, Infusion Shop, Collection Screen, Shield and each shield, Bug,
  Treasure; Saving, and *The Legend of Zelda: Skyward Sword HD* (its changes).
- **Zelda Dungeon** (its walkthrough, chapters 1–18, and wiki: Heart Pieces, Goddess Cubes, Gratitude
  Crystals and each crystal quest, the minigames, Goddess Wall, Song of the Hero, Silent Realm,
  Upgrades, Bottles, Adventure Pouch, Items, Shields, Bugs, Treasure). The live site blocks automated
  fetches, so these were read from Wayback Machine copies.
- **Samurai Gamers** (an HD guide): the Song of the Hero chapter, Scrap Shop, Potion Shop, Beedle's
  Air Shop, Gear Shop, Empty Bottle locations, Bugs and Treasure lists, saving.
- **StrategyWiki** (Gratitude Crystals) and **Zelda's Palace** (Pieces of Heart, shops). StrategyWiki's
  other pages and Zelda Universe were behind a Cloudflare challenge.
- **The Skyward Sword randomizer** (github.com/ssrando/ssrando: `checks.yaml` and
  `logic/requirements/*.yaml`), read from the game's files: which item is at every check, which cube
  opens which chest, and the tools each needs. It settles the guides' disagreements about where
  things are.
- **zeldaret/ss**, the decompilation, for the save-file count.

## Discrepancies

### Counts

None that survive checking. Every source gives 24 Pieces of Heart, 6 Heart Containers (6 to 18
hearts), 27 Goddess Cubes, 80 Gratitude Crystals from 12 quests and 15 loose ones, 5 Bottles, 8 pouch
slots, 10 shields, 12 bugs and 16 treasures. Zelda Dungeon's crystal page says "the remaining 60" for
the quests, but its own list of the 12 adds up to 65, and 65 + 15 = 80. Its items page lists Bilocyte
among seven Heart Container sources for six containers; Bilocyte gives none.

### Heart Pieces, Goddess Cubes and Gratitude Crystals

| Topic | Sources say | Used |
|---|---|---|
| Batreaux's Piece of Heart | 10 crystals (Zelda Dungeon, StrategyWiki, Zelda Wiki's *Gratitude Crystal* table, the randomizer); 15 (Zelda Wiki's *Batreaux* page, Zelda's Palace) | 10, the game's data; 15 in the notes |
| Beedle's 1,600-Rupee Piece of Heart | After buying the Life Medal (Zelda's Palace shops, Gamer Guides); after the Big Wallet (Zelda's Palace pieces page); after the other health items (Zelda Universe) | After the Life Medal, the others in the notes |
| Rickety Coaster | Open after the Sandship (Zelda Dungeon's heart list) or after Moldarach in the Shipyard (Zelda Wiki, Zelda Dungeon's minigame page) | After the Shipyard's Moldarach |
| Gorko's riddle | After the first Imprisoned fight, with the Harp (Zelda Wiki, Zelda Dungeon, the randomizer) or after the Fire Sanctuary (Zelda's Palace) | After the first Imprisoned; listed late so *Next* doesn't send you too early |
| Fledge's game | Pumpkin Pull (Zelda Wiki, the game), Pumpkin Archery (the randomizer) | Pumpkin Pull, the other name in notes |
| Sandship stern chest | Needs the Clawshots (Zelda Dungeon) or the Bow (Zelda's Palace) | Clawshots, the Bow in notes |
| Goddess Chest places | The same island named three ways: "minor island south of Skyloft" (Zelda Wiki), "near the Lumpy Pumpkin" (Zelda Dungeon), "closest to the Faron pillar" (the randomizer); likewise the volcanic island, the island by Bamboo Island and the north-east island | Named by a landmark you can see from the Sky |
| Lake Floria cube's chest | North-east Skyloft (Zelda Wiki); west and east in two paragraphs (Zelda Dungeon) | No compass point: glide from the big waterfall island |
| Cube order | Zelda Wiki and Zelda Dungeon both order theirs by availability, but numbers 17–21 need the Clawshots by their own directions | Zelda Dungeon's order, with tool tags |
| Sand Oasis cube | Bomb an Ampilus with the Hook Beetle (Zelda Dungeon); just reach the oasis (the randomizer) | Bomb the Ampilus |
| Dodoh's extra reward | A free High Dive (Zelda Wiki) or half price (Zelda Dungeon) | Both in notes |
| Kina's quest start | After the Fire Sanctuary (Zelda Wiki), the second Imprisoned and Pumm's chores (Zelda Dungeon), or the soup delivery (the randomizer) | After the Fire Sanctuary with Pumm's chores begun |
| Owlan's quest | Starts after draining Faron Woods (Zelda Wiki) or after Levias, finishing after Faron's song part (Zelda Dungeon) | Zelda Dungeon's |
| Fledge's potions | Two Stamina Potions, after the Mining Facility and the Cistern (Zelda Dungeon, Zelda Wiki); one and "later" (StrategyWiki) | Two |
| Cawlin's letter | After the first Imprisoned (Zelda Dungeon); the Harp needed (the randomizer) | After the Goddess's Harp |
| Clean Cut's lowest prize | 15 cuts (Zelda Wiki); Zelda Dungeon lists only 20 and 28 | Tiers at 15, 20 and 28 |
| Rickety Coaster prizes | Zelda Wiki and Zelda Dungeon give different time bands and lesser prizes; both give the Piece of Heart under 1:05 on Heart Stopping! | Tiers at 0:30 (Scary!) and 1:05; the bands in notes |
| Lightning Round's 12th win | 9,900 Rupees (Zelda Wiki) or "fills your wallet" (Zelda Dungeon); Hero Mode only | In notes |

### Main Quest, Items, Shields, Bugs and Treasures

| Topic | Sources say | Used |
|---|---|---|
| Song of the Hero | Four parts: the three dragons and Levias (Zelda Wiki, Zelda Dungeon's text, Samurai Gamers); "from the three dragons" (Zelda Dungeon's infobox) | Four, the dragons in any order and Levias last |
| The Skyloft trial's name | Goddess's Silent Realm (Zelda Wiki, Zelda Dungeon) | Hylia's Silent Realm, the other name in notes |
| Third Imprisoned fight | Starts on your first landing in Faron after Levias; Eldin and Lanayru don't need it (Zelda Dungeon, Samurai Gamers) | Its own step; Faron's part needs it |
| True Master Sword | Zelda blesses it at the Temple of Hylia (Zelda Wiki), after the second Imprisoned (Zelda Dungeon's chapter 15) | The Temple of Hylia, after `imprisoned_second` |
| Skyview's mini-boss | Stalfos (Zelda Wiki), Stalmaster (Zelda Dungeon's items page) | Stalfos |
| Scattershot and Reinforced Shield | 50 Rupees (Zelda Dungeon, Samurai Gamers), 20 (Zelda Wiki) | 50, 20 in notes |
| Braced Shield | 50 Rupees (Zelda Wiki, Zelda Dungeon's upgrades page, Samurai Gamers), 30 (Zelda Dungeon's shields page) | 50 |
| Banded Shield | 1 Monster Claw (Zelda Wiki, Zelda Dungeon, Samurai Gamers' Scrap Shop), 2 (Samurai Gamers' treasure list) | 1 |
| Medium Seed Satchel | 3 Ornamental Skulls (Zelda Wiki, Zelda Dungeon; it matches Zelda Wiki's total), 2 (Samurai Gamers) | 3 |
| Heart Potion++ | 3 Deku Hornets (Zelda Wiki, Samurai Gamers), 1 (Zelda Dungeon) | 3 |
| Guardian Potion | 200 Rupees (Zelda Wiki), 60 (Samurai Gamers) | 200, both in notes |
| Digging and Mogma Mitts | Zelda Wiki's upgrade table has the Digging Mitts as the upgrade; its item pages, Zelda Dungeon and the story have the Digging Mitts first (Eldin) and the Mogma Mitts second (Fire Sanctuary) | Digging, then Mogma |
| Thunderhead Bottle's cube | "In the Volcano Summit" (Zelda Wiki); the Fire Dragon's cave during the Song of the Hero (Zelda Dungeon, Samurai Gamers) | The same cube, described both ways |
| Goddess Cube for the pouch | Outside Skyview Temple (Zelda Wiki), Deep Woods near Gorko (Zelda Dungeon) | Deep Woods near Gorko, on the way to Skyview |
| Woodland Rhino Beetle | Skyloft and Faron Woods (Zelda Wiki), the Deep Woods (Zelda Dungeon, Samurai Gamers) | Faron Woods, tagged Skyloft too |
| Golden Skull, Monster Horn, Goddess Plume | Different lists of where to get them (Zelda Wiki, Zelda Dungeon) | Both, in notes |

## Thrill Digger Solver

Tubert's Thrill Digger, on Eldin Volcano, is Minesweeper with Rupees. The solver (`thrilldigger/`,
`TrackerKind.THRILL_DIGGER`) gives every undug hole its exact chance of a Bomb and of a Rupoor, and
says which hole to dig next. It's listed last under Skyward Sword, under a **Tools** heading in the
menu and on the home card. It keeps no progress, so it has no Progress Overview row, no mark-all
and no save file. Its boards are saved under `ss_thrill_digger`, one per difficulty with its own
undo history, so they survive rotation and the app being closed.

### The rules

| Difficulty | Holes | Bombs | Rupoors | Cost |
|---|---|---|---|---|
| Beginner | 5 × 4 | 4 | 0 | 30 |
| Intermediate | 6 × 5 | 4 | 4 | 50 |
| Expert | 8 × 5 | 8 | 8 | 70 |

A dug Rupee's colour counts the **hazards (Bombs and Rupoors alike)** in its eight neighbours: Green 0,
Blue 1–2, Red 3–4, Silver 5–6, Gold 7–8. Rupees are worth 1, 5, 20, 100 and 300. A Rupoor takes 10,
and a Bomb ends the round (and costs a heart). The board ends when every Rupee is dug.

**How the game deals.**
1. It drops the 8 (or 4) Bombs into random empty holes, then the Rupoors.
2. If any 2×2 square is all hazards, it deals again, up to ten times, and keeps the eleventh deal
   whatever it holds.
3. It fills every other hole with the Rupee its neighbours call for.

On Expert about 40% of deals have such a block (`ThrillDiggerSolver.blockShare`), so this changes
the odds a lot. Beginner's is 0.3% and Intermediate's about 5%.

**Sources.**
- The counts are in Zelda Wiki's *Thrill Digger* page, and the costs in its infobox.
- The colour table is the Mogma's tip behind the game area.
- The generator (hazards counted together, the 2×2 re-deal, the values) is from lepelog's
  `thrill-digger` (github.com/lepelog/thrill-digger, `thrill-digger-lib/src/thrill_digger.rs`). It's
  a port of the game's hole minigame actor, whose functions the zeldaret/ss decompilation names in
  `config/SOUE01/rels/d_a_obj_hole_minigameNP/symbols.txt`: `placeBombs`, `placeRupoors`,
  `fillRupees`, `isBombOrRupoor`.
- **Discrepancy:** Zelda's Palace gives Intermediate 5 Bombs and 5 Rupoors. Zelda Wiki and the
  game's code say 4 and 4.

### The solver

The reference was **lepelog's Thrill Digger solver** (lepelog.github.io/thrill-digger), a well-known
one from the Skyward Sword randomizer community. Its UX is a board where each hole takes one of the
seven pictures, with the odds and a ranking per hole. It has no licence, so none of its code or
pictures are used; only the game's rules were taken from it. lepelog's solver goes further for
Expert: it lists every board the game's random number generator can produce. We do that too, as
the [RNG mode](#the-rng-mode), re-implemented from the description. The solver below counts every
board the dealing rules allow. It's the fallback, and Beginner's and Intermediate's default.

`ThrillDiggerSolver` is exact. A board's weight is how often the game deals it:
- 1 for a board without an all-hazard 2×2 block;
- p¹⁰ ÷ ((1 − p¹¹) / (1 − p)) for one with a block, where p is the share of even deals that have one.

The block rule ties every hole to its neighbours, so holes away from the dug ones aren't
interchangeable. That rules out the usual "frontier groups plus combinatorics for the rest".
Instead `HazardCounter` sweeps the board **a column at a time** (a transfer matrix):
- A state is two neighbouring columns' hazard patterns (5 bits each) and the number of hazards so far.
- A dug Rupee's colour is checked once the columns either side of it are known, and a 2×2 block once
  its second column is.
- Run forward and backward, it gives each hole's share of the boards, and for each undug hole how
  many hazards would be around it, so the colour it would show.
- It runs once with blocks allowed and once without, and the two are mixed by the weights above.
- A **skipped** hole (dug, colour not seen, `Hole.SKIPPED`) is never a hazard but has no count to
  check, so it's in every column's "never a hazard" mask and not among the Rupee rows. It gets no
  odds and is never recommended.
- The Bombs and Rupoors still hidden are interchangeable among the hazard holes, so a hole's Bomb
  chance is its hazard chance × Bombs left ÷ hazards left.

For each undug hole it reports:
- the chances of a Bomb, a Rupoor and a Rupee;
- the chance of each Rupee colour;
- what a dig there is worth on average;
- the information a dig there gives (the entropy of the seven things it might show).

**The recommendation:** the lowest Bomb chance, then the lowest Rupoor chance, then the most
information, then the best average haul, then the first hole in reading order. Near-equal chances
count as ties. Holes that must be hazards are never recommended. Once only those are left, every
Rupee is dug.

**Impossible boards.** When no deal fits the entered holes, the solver finds a smallest set of
entered holes that can't all be true: each one is cleared in turn and stays cleared if the board is
still impossible without it. The screen outlines that set in red. Too many Bombs, or a Rupoor on
Beginner, count as impossible too.

**Speed.** Expert's worst case is a few million steps. On the desktop JVM it's about 4 ms a board,
16 ms at worst (the empty board). `ThrillDiggerSolverTest` asserts under 250 ms. The solver runs off
the main thread.

**Tests.** `ThrillDiggerSolverTest` checks the solver against:
- brute-force enumeration of every weighted hazard set: the empty Beginner board, every first dig,
  and dealt boards partly dug on all three difficulties;
- boards worked by hand;
- boards dealt by a re-implementation of the game's rules (always possible, and certain once every
  Rupee is dug);
- a Monte Carlo estimate of the block share;
- brute force again with some dug Rupees turned into skipped holes.

It also checks impossible-board conflicts, that every conflict found is minimal, and the time.
`ThrillDiggerViewModelTest` covers entering holes, the recommendation, undo, reset, difficulties
and saving.

### The RNG mode

Skyward Sword's random number generator (`SsRng`) works like this:
- Each step multiplies the 32-bit state by 1664525, adds 1013904223, and folds the 64-bit result's
  high half into its low half.
- The float the game draws is the state's top 23 bits as a mantissa.
- The fold makes the map many-to-one, so from any start the state falls into one of a few loops and
  never leaves it.
- The game calls the RNG thousands of times a frame. Starts reach their loop within 1.3 million
  steps 99% of the time (a median of 440,000), which is seconds of play.

A board is dealt from wherever the RNG is when the round starts, so only the boards dealt from loop
states can come up. That's about 2.6 million on any difficulty, out of billions of valid Expert
boards. lepelog described the idea and the generator (its README and `rng.rs`). The code here is
our own, from that description.

**The loops** (`RngLoops`) were found by running the RNG from 20,000 random starts until each
reached a known loop. A start that hadn't arrived after 3 million steps was taken to be on a new loop, found with Brent's method.
Each loop is named by its smallest state, and its share is the fraction of starts that ended on it:

| Anchor | States | Share of starts |
|---|---|---|
| 5862 | 1,708,724 | 76.8% |
| 8002 | 127,646 | 7.5% |
| 4172 | 155,834 | 7.2% |
| 34267 | 354,835 | 3.4% |
| 56857 | 146,318 | 2.5% |
| 409493 | 48,534 | 1.3% |
| 61847 | 81,673 | 1.2% |
| 82622 | 26,128 | 0.02% |

That's 2,649,692 states. One start in 20,000 reached an 8-state loop, which is left out. lepelog
lists the same big loops, and a 1,371-state one no start reached. A state's weight is its loop's
share ÷ its loop's length: the loops are as likely as a random start makes them, and the states on
a loop equally likely. lepelog weighs every state equally instead.

**Listing the boards.** `RngBoardSource` deals every state's board (`ThrillDiggerDeal`, the same
rules as above) the first time a difficulty's RNG odds are wanted:
- It runs in the background with a progress bar, and the odds over every board show meanwhile.
- It takes 0.5 s on a desktop JVM, so a few seconds on a phone.
- It keeps them for as long as the app runs: one Long per state (`PackedBoard`: the hazard bits,
  then which hazards are Bombs), 21 MB per difficulty.
- Nothing is bundled. All the distinct boards come to 12 MB raw, 6.9 MB compressed, more than
  listing them costs.

**The odds** (`RngSolver`) keep the states whose board agrees with every entered hole and add up
their weights. They're slowest with nothing dug, which is worked out once and cached, or one hole
dug: 160 ms on the desktop. The more holes are dug, the faster.

**Benefit.** Measured over 120 Expert play-outs on RNG-dealt boards, digging each mode's
recommended hole until a Bomb or the end:
- the rules alone: 2 boards cleared, 123 Rupees on average;
- the RNG mode: 32 boards cleared, 150 Rupees on average.

**On screen:**
- **Use the game's RNG** is a switch per difficulty: on for Expert, off for Intermediate, and absent
  for Beginner, where 2.6 million states spread evenly over its 4,845 hazard layouts.
- The panel says how many of the RNG's boards fit, and on which loops.
- When none fits, the odds fall back to every board, with a note: right after the game starts, its
  RNG may not have reached a loop yet.
- **Loop lock:** once only one loop fits, a button locks the difficulty to it, since the RNG stays on
  its loop until the game restarts. Later rounds then count only that loop's boards, or every
  loop's if nothing on it fits.

**Export board** (any board but an empty one) shows the board as text, with a Copy button: a row of
letters per row, G B R S Y for the Rupees, P for a Rupoor, X for a Bomb, ? for undug. A finished
board (every Rupee entered, `DiggerBoard.isFinished`) is also checked against the RNG port, with how
many states deal it. Its undug holes must all be hazards, so they're written H (a Bomb or a Rupoor)
and the check only asks that a state's hazards be there, along with every entered Rupee, Rupoor and
Bomb. A skipped hole is written D (dug, colour not seen; the key line says so when there is one):
the RNG mode, and the check, only ask that a state put some Rupee there. A skipped hole counts as
dug, so a round can be finished with some. When exactly one state fits, its board follows, saying which H was which. The text and Copy
show at once; the check follows when the RNG's boards are listed, and if anything goes wrong it
says "Can't check this board" and why, never crashing.
- **The first real board checked** (2026-10-20, a test in `ThrillDiggerRngTest`): an Expert round
  with all 24 Rupees and 3 Rupoors entered and 13 holes undug was dealt by exactly one state, on
  loop 1. A port that was wrong would match a given hazard layout about once in 240,000 such
  checks (2.65 million states against C(40,16) ≈ 6.3·10¹⁰ layouts, a tenth of which put Rupoors on
  those 3 holes), so this is good evidence the port is right.
- After a round ends on a Bomb the game shows every hole. Entering them all and sending the export
  should give "1 RNG state deals this board" or more.
- "No RNG state" would mean the port is wrong, or the RNG hadn't reached a loop.
- Impossible boards export too, since a real board the rules call impossible is worth knowing about.

`ThrillDiggerRngTest` checks:
- the RNG step and float;
- that every loop is a loop of its length, anchored at its smallest state;
- packing;
- the 2×2 check against brute force;
- that dealt boards follow the rules;
- the odds against counting a small table's states directly;
- skipped holes, against counting the states with any Rupee there;
- loop filtering, a board no state deals, and finding a board's own state;
- the full Expert table's size and solve time.

`ThrillDiggerViewModelTest` covers the defaults per difficulty, narrowing, the loop lock, the
fallback and exporting, skipped holes (undo, reset, the D in the export), filling one from the
in-game total, and that a save from before skipped holes still loads.

### The screen

- **Landscape first.** The board fills the left and a panel on the right holds:
  - **Rupees this round**, in large type (below);
  - the difficulty;
  - the picker;
  - a status line: Rupees left to dig, a finished board, or the conflict;
  - the chosen hole's details: its chances, its colour chances with small Rupee pictures, and its
    average worth.
- In a short landscape window (under 480 dp tall, as on HW:DE's map) the top bar and system bars
  hide. The panel's top row then carries ☰ or Back, the title, **Undo** and **Reset**.
- **Portrait:** the board on top, then the status, Rupees this round, the picker and the details.
- **Undug holes** are tinted by their Bomb chance: green at 0%, through amber, to red at 50% and
  over. They show the Bomb percentage and, on boards with Rupoors, the Rupoor percentage under it,
  each by a small picture. A hole that must be a Bomb or a Rupoor shows that picture faded.
- **The recommended hole** has a gold outline and a ★. The hole the next entry goes into has the
  theme's primary outline.
- **Entering a dig: tap a hole, then tap what it showed.** With no hole tapped, the picker fills the
  recommended hole, and after each entry the selection goes back to the new recommendation, so
  following the advice is **one tap per dig**. That's faster than a paint mode: each dig shows
  something different, so a brush would need changing nearly every time. Tapping the selected hole
  again deselects it, and **Clear** empties a hole.
- **The picker** is the five Rupees in a row, then the Rupoor, the Bomb, **Skipped** and Clear. Each
  has its name and its hazard range (`0`, `1–2`…), `−10` for the Rupoor, *Game over* for the Bomb
  and *Missed it* for Skipped. The tile matching what's in the target hole is outlined.
- **Skipped** is for a hole you dug without noticing the Rupee: a grey Rupee with a "?". It's safe
  and dug (so it counts towards a finished round), but gives its neighbours no clue. Tap its
  colour later if you remember it.
- **Rupees this round** adds up the entered holes (`RupeeTotal`): Green 1, Blue 5, Red 20, Silver
  100, Gold 300, Rupoor −10 (`Hole.rupees`). With skipped holes it shows the known part, "+ ? from
  N skipped", and the range (each skipped hole 1 to 300).
- **Enter in-game total** (shown while a hole is skipped) takes the Rupees the round has earned as
  the game counts them (or the wallet now less the wallet before the round) and works out the
  skipped holes (`GameTotal.infer`). The skipped holes are worth the difference from the known
  part. The five values are distinct, so one skipped hole's colour is certain: "That hole was a
  Blue Rupee: fill it in?". With several, every set of colours that adds up is tried in every
  order on the board (up to 200 orders), keeping the orders the solver calls possible, since the
  colours are clues for their neighbours. One order left is offered to fill in; otherwise it lists
  up to six colour sets ("Green + Blue, but the total can't tell which hole is which", or "It's
  ambiguous: …"). The smallest count where two sets share a sum is six holes (five Greens and a
  Silver, or a Blue and five Reds: 105). Filling in is one undoable change, and the filled holes
  are ordinary clues from then on. A total nothing adds up to says to check the total and the
  holes.
- Under the details: the RNG switch and its status, and **Export board** ([The RNG mode](#the-rng-mode)).
- **Undo** steps back through every change on that difficulty, Reset included, up to 200 steps.
  Reset clears the board for a new round.

### Pictures

Skyward Sword's own renders, from Zelda Wiki through its API by `scripts/fetch-thrill-digger-icons.py`.
They're the wiki's own thumbnails, so nothing is resized locally. All are bundled as
`drawable/thrill_*.png`.

| File | Zelda Wiki file | Size |
|---|---|---|
| `thrill_green.png` … `thrill_gold.png` | `File:SS Green Rupee Render.png`, `SS Blue …`, `SS Red …`, `SS Silver …`, `SS Gold Rupee Render.png` | 80 × 125 |
| `thrill_rupoor.png` | `File:SS Rupoor Render.png` | 80 × 125 |
| `thrill_bomb.png` | `File:SS Bomb Render.png` | 92 × 125 |

The Rupoor and the Silver Rupee are the same shape and close in shade. `HoleIcon` draws a **dark
purple minus sign, ringed in white, over the Rupoor's right-hand side**, so the two can't be confused.
It's drawn in code, not baked into the picture.
