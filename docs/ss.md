# Skyward Sword

Skyward Sword's trackers are checklists (`TrackerKind.CHECKLIST`, see `docs/checklist-trackers.md`):
eight JSON files in `shared/src/commonMain/composeResources/files/ss/`, listed in `core/Games.kt`
after a Progress Overview. After them come the [Potion Infusions](#potion-infusions) recipe book
(`TrackerKind.POTIONS`) and [Item Upgrades](#item-upgrades) (`TrackerKind.ITEM_UPGRADES`, modelled on
BotW's armor tracker), both from `upgrades.json` and [sharing progress](#shared-progress) with the
checklists, then the [Lightning Round Records](#lightning-round-records) board
(`TrackerKind.BOSS_RUSH`, from `boss_rush.json`), and one tool, the
[Thrill Digger Solver](#thrill-digger-solver) (`TrackerKind.THRILL_DIGGER`). `SsDatasetTest` checks the counts below and the links between files,
`ChecklistDatasetsTest` checks the format, and `ChecklistLinksDatasetTest` the [linked items](#linked-items),
which share one progress between files.

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

**Hero Mode is per file, and the user says which.** A file's mode can't change in the game (Hero
Mode is a new file started from a cleared one), but the app can't know it, so unlike BotW's fixed
`SaveFile.masterMode` it's user data: `SaveFileSettings.heroMode` in `core/SaveFileSettings.kt`,
stored under `ss_file_settings@file_N` and set by the *Hero Mode* switch on the
[Lightning Round Records](#lightning-round-records) board. Only that board reads it so far; other
trackers can use it for their own Hero Mode differences (none are wired in yet). It's
`SaveFileSettingsStorage`, generic over games, so another game's Hero Mode file could use it too.

## Files and counts

| File | Tracker | Sections (ids) | Count |
|---|---|---|---|
| `main_quest.json` | Main Quest | `quest`, `silent_realms`, `hero_song`, `sacred_flames`, `sword` | 30 story steps with the 7 dungeons, 4 Silent Realms, 4 parts of the Song of the Hero, 3 Sacred Flames, and the sword's 5 forms as tiers of one row (46 steps). Summaries: Dungeons `quest.value` (of 7), Silent Realms (of 4), Sacred Flames (of 3, on its tab) |
| `items.json` | Items & Equipment | `equipment`, `capacity`, `pouch`, `bottles`, `wallets` | 13 items (21 steps: Slingshot, Bug Net, Beetle, Digging Mitts and Bow with their upgrades as tiers), 3 capacity items (Seed Satchel, Bomb Bag, Quiver; 3 tiers each), 5 Adventure Pouch rows, 5 Bottles, the Wallet (4 tiers) and Extra Wallets (3). Summaries: Pouch slots `pouch.value` (4 to 8), Rupees you can carry `300 + wallet.value + extra_wallets.value` (300 to 9,900) |
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
of Heart above. Since October 2026 each is one progress: see [Linked items](#linked-items).

### Linked items

Each row below is the same real thing as a row, or one tier of a row, in another tracker, so they
share one progress (`sameAs`, see docs/checklist-trackers.md, *Linked items*): tick either and both
show it, on every screen and the overview. Until October 2026 they were ticked separately, which
the user found with the wallets ("some of them are rewards from Batreaux"). Saves from before were
merged once: ticked on either side is ticked, at the higher tier. The **canonical** item holds the
progress; the linked row reads and writes it. `ChecklistLinksDatasetTest` pins every link.

| Linked row | Is the same as (canonical) | Why |
|---|---|---|
| Gratitude Crystals: Batreaux's 5 / 30 / 50 / 80 crystals | Items & Equipment: Wallet, tiers 1 / 2 / 3 / 4 (Medium, Big, Giant, Tycoon) | His wallet rewards are the Wallet's upgrades; nothing else gives them |
| Gratitude Crystals: Batreaux's 10 crystals | Heart Pieces: *Batreaux: 10 Gratitude Crystals* | The same Piece of Heart |
| Heart Pieces: the 6 *Goddess Chest* pieces (Faron pillar, Beedle's Island, the locked shed, the volcanic island, atop the waterfall, Bug Rock) | Goddess Cubes: their cubes' tier 2, *Chest opened* (Skyview entrance, Temple of Time mine cart, the sand slide, the Great Tree's south-east ledge, the Pirate Stronghold, the Volcano Summit waterfall) | The piece is the chest's prize; opening the chest means the cube was struck, so tier 1 comes with it |
| Heart Pieces: Rickety Coaster, Pumpkin Pull, Lightning Round 4 wins, Pumm's chores | Side Quests & Minigames: Rickety Coaster tier 2, Pumpkin Pull tier 3, Lightning Round tier 1, Pumm's chandelier debt tier 3 | The tier whose prize is the piece |
| Side Quests & Minigames: Dodoh's High Dive, Gorko's Goddess Wall riddles | Heart Pieces: *Dodoh's High Dive*, *Gorko's Goddess Wall riddle* | Each row is won by its piece |
| Side Quests & Minigames: Harp performance with Kina | Pumm's chandelier debt tier 3, on the same list | The first performance is Pumm's last chore |
| Shields: Hylian Shield | Side Quests & Minigames: Lightning Round tier 2 (8 wins) | Its only source; Item Upgrades' Hylian Shield follows (below) |
| Items & Equipment: Pouch expansion (Pumpkin Landing), Thunderhead Bottle | Goddess Cubes: Gorko's cube and the cube by Eldin's lair, tier 2 | The chest's prize |
| Items & Equipment: Sailcloth, Goddess's Harp, Water Dragon's Scale, Clawshots, Fireshield Earrings | Main Quest: The Wing Ceremony, The Temple of Time, Farore's, Nayru's and Din's Silent Realms | The step ends with the item; it comes from nowhere else |
| Main Quest: The Kikwis | Items & Equipment: Slingshot tier 1 | Bucha gives the Slingshot for finding them |
| Main Quest: The Goddess Sword, Farore's / Nayru's / Din's Flame, The Temple of Hylia | Main Quest: the Sword row's tiers 1–5 (Goddess Sword to True Master Sword) | Each is the sword's form |

32 links. Ticking a tier link raises the canonical item to that tier, so its earlier tiers come
too; unticking it lowers the item below that tier, so the later ones go. That follows the tier rows
as the data already modelled them, so:
- ticking the Hylian Shield also ticks the Lightning Round's 4-win Piece of Heart, and unticking that
  piece unticks the Hylian Shield and the 11 wins;
- the Rickety Coaster's Heart Stopping! piece ticks its Scary! tier;
- unticking the Goddess Sword step clears the whole Sword row, the flames and the Temple of Hylia.

**Counts.** Each tracker still counts every row it lists, linked or not, so its total means what it
did: Heart Pieces 24 pieces and 6 containers, Goddess Cubes 54 steps, Gratitude Crystals' Batreaux
tab 7 rewards, Items & Equipment's wallets 4 tiers. A linked thing ticked shows in both trackers'
counts, as it's in both lists; no number adds the trackers up, so nothing is counted twice in one
total. The Main Quest counts both its flames and the sword's forms, as it always did (46 steps).

**Main Quest steps** that are linked rows (the Kikwis, the Goddess Sword, the flames, the Temple of
Hylia) count as done from the item they're linked to, so [story gating](#story-links) unlocks what
they gate when either is ticked.

**Not linked, and why.** These look alike but aren't the same event, so a link would tick one for
the other:
- **Extra Wallets** come only from Beedle's Airshop (100 Rupees each; Zelda Wiki, *Extra Wallet*),
  not Batreaux, so they have no other row. The four Wallet tiers are Batreaux's.
- **Parrow's Bottle** comes partway through Orielle's Gratitude Crystal quest, before her crystals,
  so the bottle doesn't mean the quest is done.
- **The Small Seed Satchel, Small Bomb Bag and Small Quiver** chests are one way to get the first
  tier; the Gear Shop sells each too.
- **A dungeon and its Heart Container** (Main Quest and Heart Pieces): the boss's container is
  picked up separately.
- **The Digging Mitts and the Key Pieces, the Hook Beetle and the Power Generator**, and each
  dungeon's item: the item comes early in the step, which goes on after it.
- **Luv's Bottle, Fledge's Adventure Pouch and Owlan's Wooden Shield** are given after the Goddess
  Sword is drawn, not by drawing it, and that step is already the sword's first tier.
- **The Sealed Temple Bottle's Revitalizing Potion** isn't one bought from Luv, so Potion Infusions
  doesn't count it.

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
- **Potion infusions** were a tab of Items & Equipment until October 2026; they're now the
  [Potion Infusions](#potion-infusions) recipe book. An infused potion is used up when drunk, so it
  counts each step made at least once.
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
- **zeldaret/ss**, the decompilation, for the save-file count, and for the Lightning Round's records
  (`storyflag_map.h`, `d_lyt_mini_game.cpp`, the Thunder Dragon's symbols).
- **Lightning Round Records** also used Zelda Wiki (*Thunder Dragon's Lightning Round*, *Hero Mode*,
  *Lanayru (Dragon)*), Fandom (zelda.fandom.com, the same page), Game8 and Nintendo Life (HD
  guides to the prizes). Zelda Dungeon's page was blocked to fetches and the Wayback Machine was
  down, so only search extracts of it were read; they list the same 12 bosses in the same order.

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

## Story links

With *Hide what the story hasn't unlocked yet* on (Settings → Progress), each checklist leaves out
what the Main Quest hasn't reached on the save file. It uses each item's `unlockedBy`, a step of
`main_quest.json`; docs/checklist-trackers.md, *Story gating*, explains the rules.

**Counts.** 154 of the 163 items outside the Main Quest are linked. The other 9 can be had from the
start, before the Wing Ceremony:
- Skyloft's four bugs (the Skyloft Mantis, Sky Stag Beetle, Starry Firefly and Blessed Butterfly);
- the Amber Relic and the Monster Claw, which Keese drop over Skyloft at night;
- the Iron and Sacred Shields from the Bazaar;
- Fledge's barrels.

**How a step was chosen**, following the convention in `scripts/derive-story-links.py`:

- *Inside a dungeon*, items, chests and the boss's Heart Container are gated by the step that opens
  the dungeon:

  | Dungeon | Step that opens it |
  |---|---|
  | Skyview Temple | `faron_woods` (the Kikwis) |
  | Earth Temple | `key_pieces` |
  | LMF | `power_generator` |
  | Ancient Cistern | `lake_floria` |
  | Sandship | `lanayru_sand_sea` |
  | Fire Sanctuary | `volcano_summit` |

  So the Gust Bellows and Moldarach's container show once the generator is set, before the LMF is
  finished.
- *Areas* are gated by the step that first takes you there:

  | Area | Step |
  |---|---|
  | Faron Woods and the Sealed Grounds | `statue_of_the_goddess` |
  | The Deep Woods | `faron_woods` |
  | Eldin Volcano | `skyview_temple` |
  | Lanayru | `earth_temple` |
  | The Thunderhead | `ballad_of_the_goddess` |
  | Lake Floria and the Great Tree's top | `trial_farore` (the Water Dragon's Scale) |
  | The Sand Sea and everything behind a Clawshot target | `trial_nayru` |
  | The Volcano Summit | `trial_din` (the Fireshield Earrings) |
  | Lanayru Gorge and the Song of the Hero's areas | `levias` |
- *Rewards* are gated by their step:
  - the Sailcloth by `wing_ceremony`;
  - the Slingshot by `faron_woods`;
  - the Goddess's Harp by `temple_of_time`;
  - each Silent Realm's item by its trial.
- *After a dungeon, outside it*: things whose `after` names a dungeon but which are found outside it
  are gated by that dungeon's step. Examples are the Temple of Time cube and its Goddess Chest,
  the Sand Oasis cube, Peatrice's quest and Fledge's push-ups, all gated by
  `lanayru_mining_facility`.
- *Optional content* is gated by the step that opens it:
  - Batreaux, Kukiel and the night-time crystals by `skyview_temple` (the Ruby Tablet);
  - Dodoh's Party Wheel, its High Dive and the Fun Fun Island piece by `farores_courage` (Scrapper
    and the Isle of Songs);
  - the Lightning Round and the Hylian Shield by `hero_song_lanayru` (the Life Tree Fruit);
  - Beedle's Airshop by `wing_ceremony` (the Sailcloth).
- *Judgement calls*, all gated as early as the sources allow:
  - Pumm's chandelier debt is tiered, and its first job needs only flight, so it's gated by
    `statue_of_the_goddess`. The Piece of Heart for finishing it needs the harp, so it's gated by
    `temple_of_time`.
  - The Evil Crystal is gated by `skyview_temple` (Dark Keese on Eldin), and the Dusk Relic by
    `farores_courage` (the first Silent Realm).
  - Bombs come from the Earth Temple, so the bombable rocks in Faron and Lanayru are gated by
    `key_pieces` and `earth_temple`.

**Sources** are the same as the data's (above): Zelda Wiki's item, area and quest pages, the Zelda
Dungeon walkthrough's chapter order, and the randomizer's requirement files for what each check
needs. `SsDatasetTest` spot-checks the links and lists the nine start items, so a new item can't
go unreviewed.

## Item Upgrades

`TrackerKind.ITEM_UPGRADES`, in `upgrades/`, listed after the checklists. It's the BotW Armor
tracker's layout for Gondo's Scrap Shop and Bertie's potion infusions: a list with icons, mark
collected, a level per item, a page per item with the materials for every upgrade left, and **Total
Materials Required** with the Collected / All switch. Its data is `files/ss/upgrades.json`.

**What it reuses from armor.** `MaterialCost`, `MaterialSort`, `Acquisition` / `Cost` /
`AcquireMethod`, `PieceProgress` (owned + level) and `OwnershipFilter` are imported from `armor/`
unchanged; nothing in `armor/` was edited, so BotW's and TotK's armor behave as before. The screens
follow the armor screens (section cards with a bulk-complete box on each header, a
`BulkCompleteRow` under the filters, `LoadingOrError`, `SaveFilePicker`, `StarLevelButton`,
`SortMenu`) and wear SS's theme automatically through the `gameId` route. Sections are laid out with
`groupByRegion`. There are no discovery sets: nothing here is a spoiler to hide.

**Why not shared armor code.** An armor piece has slots, sets, defence and set bonuses, and its totals
are tied to `ArmorPiece`; an upgrade item has named forms, Rupee prices, copies and an optional
section. Generalising `ArmorCalculator` would have meant touching every armor file for a small
`totals` loop, so the calculator is its own (`UpgradeCalculator`) and the armor tests are untouched.

### The data

| Section (id) | Items | Upgrades | Who |
|---|---|---|---|
| Items (`items`) | Slingshot → Scattershot; Beetle → Hook → Quick → Tough Beetle; Bug Net → Big Bug Net; Bow → Iron → Sacred Bow | 7 | Gondo (the Hook Beetle: a robot in Lanayru Desert, free) |
| Shields (`shields`) | Wooden → Banded → Braced; Iron → Reinforced → Fortified; Sacred → Divine → Goddess; Hylian Shield (no upgrades) | 6 | Gondo |
| Pouch Items (`pouch`) | Small → Medium → Large Seed Satchel, Quiver and Bomb Bag | 6 | Gondo |
| Potions (`potions`, optional) | Heart Potion → + → ++; Stamina → +; Air → +; Revitalizing → + → ++; Guardian → + | 7 | Bertie, at Luv's Potion Shop |

16 items, 26 upgrades, 28 materials (all 12 bugs and all 16 treasures). Every step has its Rupees
and materials except the Hook Beetle's (`free: true`). Each item has where to get it (`acquire`, with
the price when sold) and `missable` (none is: every shop item stays on sale and every Goddess Chest
stays open). Each material has its Collection-screen position, regions and where to farm it, copied
from the Bugs & Treasures checklist so the two say the same. Ids are snake_case: items by their base
name, levels by their form (`heart_potion_plus_plus`), materials by name.

**Names.** The game's own: the pouch items are *Small*, *Medium* and *Large* (not "Large" and
"Extra-Large"), and the pouch Bomb Bag is the *Small Bomb Bag*, separate from the Earth Temple's Bomb
Bag, which holds 10 and takes no slot. The Slingshot's and the Bow's built-in seed and arrow room
isn't a pouch item either.

**Full totals** (everything from scratch, one of each pouch item): 195 materials and 1,510 Rupees
(items 400, shields 480, pouch items 450, potions 180); without the potions, 145 treasures and 1,330
Rupees. `SsUpgradeDatasetTest` checks each material's total against the Bugs & Treasures checklist's
"Used in …: N in all" note, and every Rupee price against the Items & Equipment and Shields
checklists' tiers, so the files can't drift apart.

### The totals rule

As the user asked: **every item fully upgraded, and exactly one of each pouch item** (one Seed
Satchel, one Quiver, one Bomb Bag) fully upgraded.
- You can carry several of each pouch item in Adventure Pouch slots, and track each copy: *Add Seed
  Satchel #2* under the last one, or on its page. Copy 1 is stored under the item's id and later ones
  as `seed_satchel#2`…, so adding copies never moves the first one's progress. Removing a copy moves
  the later ones down a number; copy 1 can't be removed, only unticked.
- The copy that counts is your **most upgraded owned copy** (the lowest number on a tie): that's the
  one you'd finish. With none owned, *All items* counts copy 1 from the start and *Collected items*
  counts nothing. Every other copy is tagged *Not in totals*; its own page still lists its upgrades.
- *Collected items* counts what's left above each owned item's level. *All items (including not yet
  obtained)* adds every other item's whole path. A level saved on an unticked item doesn't count.
- The **Potions** chip is on by default, because "all items" includes them. Off, the potions leave the
  list, the totals, *Mark all* and the overview row; their progress is kept. The preference is shared
  by every save file, as armor's DLC chip is.
- Total Rupees show above the materials. Tapping a material lists where to farm it and each upgrade
  that needs it, with how many.

### Storage

`UpgradeStorage`: one `UpgradeProgress` per save file under `ss_upgrades@file_N` (extra copies, the
copy counts and the potions), and `ss_upgrades_prefs` (the hidden sections and the material sort),
shared by every file. Copy 1 of every other item is read from and written to its checklist (see
[Shared progress](#shared-progress)); `progress` joins the two and `update` splits them again in one
DataStore edit, so every change is still one write. The active save file is SS's, shared with the
checklists. *Mark all complete* collects or uncollects the copies shown in one write and leaves levels
alone, except that an uncollected shared item loses its upgrades (below); its undo puts back exactly
those copies, levels included.

### Shared progress

The Items & Equipment and Shields checklists have the same items as tiers, and until October 2026 the
two trackers kept separate progress for them: ticking the Quick Beetle in one didn't show in the other.
Now they share one. `UpgradeLinks.SS` pairs each upgrade item with its checklist row:

| Item Upgrades | Checklist row | Checklist level |
|---|---|---|
| `slingshot`, `beetle`, `bug_net`, `bow` | Items & Equipment's same ids (`equipment`) | 0 without the item, then 1 + its upgrade level |
| `wooden_shield`, `iron_shield`, `sacred_shield` | Shields' same ids | the same |
| `hylian_shield` | Shields' `hylian_shield`, a [linked row](#linked-items) stored as the Lightning Round's tier 2 in Side Quests & Minigames (`ChecklistLink.via`) | owned while the Lightning Round is at 8 wins or more; collecting it raises it to 8 wins, uncollecting lowers it to 4 wins |
| `seed_satchel`, `quiver` | Items & Equipment's same ids (`capacity`) | the same, for copy 1 |
| `bomb_bag` | Items & Equipment's `extra_bomb_bag` (`capacity`) | the same, for copy 1; the checklist's `bomb_bag` is the Earth Temple's, which takes no slot and can't be upgraded |

**Which store is canonical: the checklists'.** Their keys (`ss_checklist_items@file_N`,
`ss_checklist_shields@file_N`) shipped first, with SS's checklists, and the checklist engine is shared
by every game: making its levels the source needs no engine change at all, where the reverse would
need a hook in the engine for one game's items. One tier count holds the upgrade tracker's state
losslessly (owned with level *n* is tier *n* + 1), except a level kept on an uncollected item, which
the totals already ignored. So unticking a shared item clears its upgrades, as unticking its
checklist row does; items only Item Upgrades has (extra copies, potions) keep their level as before.
The only shared-code change is `ChecklistStorage.progressKey`, so the key format is written once.

**Tiers ↔ levels.** The tiers are the base item then each form, so they line up with the upgrade
levels one for one: the Beetle's are Beetle, Hook Beetle, Quick Beetle, Tough Beetle, and the Hook
Beetle is upgrade 1 in both, the free one the robot fits (`free: true`), not one of Gondo's.
`SsUpgradeDatasetTest` checks every pair has the same forms in the same order, and that every item
but the potions is linked once.

**Pouch copies.** Copy 1 of the Seed Satchel, Quiver and Bomb Bag is the checklist's capacity row.
*Seed Satchel #2* and later copies, and how many you track, are Item Upgrades' alone: the checklist
has one row each, for a 100% run. Removing a copy never touches copy 1.

**Potions** have one store too: `ss_upgrades@file_N`, which the Potion Infusions book and Item
Upgrades' Potions section both read. Their checklist tab is gone.

**The migration** (`SharedProgressMigration`, a DataStore migration in `createDataStore`). Each save
file is merged on its own, once, when the app opens the store and before anything reads it:
- each linked item's copy 1 moves from `ss_upgrades@file_N` to its checklist row, at the higher of the
  two levels (an uncollected copy counts as 0);
- each old Potion Infusions row moves from `ss_checklist_items@file_N` to the upgrade key: its tiers
  were infusions, so the level is the higher of the two and the potion counts as bought;
- extra copies and copy counts stay as they are; a file whose stores can't be read is left alone.

It moves rather than copies, so it's idempotent: nothing writes those places any more, so once a
file is merged there's nothing left to merge. `SharedProgressMigrationTest` runs it on saves in the old
formats (both stores, differing levels both ways, extra copies, potions, one store only, an unreadable
one) and end to end through a store written by the old app.

**The Hylian Shield's chain.** The Shields row is linked to the Lightning Round's 8-win tier (see
[Linked items](#linked-items)), so it keeps no level of its own and Item Upgrades reads and writes
that tier directly: collecting it in Item Upgrades, ticking it in Shields and winning the 8th fight in
Side Quests & Minigames are one tick. An old save's Item Upgrades Hylian Shield is moved to the
Shields row by `SharedProgressMigration`, then to the Lightning Round by the link migration, which
runs after it. Item Upgrades' *Mark all* undo puts the Lightning Round's level back exactly
(`UpgradeStorage.UpgradeSnapshot.stored`), since owning the shield or not can't say whether the
4-win tier was ticked. `SsUpgradeDatasetTest` checks `UpgradeLinks.SS` follows the row's `sameAs`.

**Overview rows.** Items & Equipment, Shields and Item Upgrades count the same things from the same
place, so they always agree, each in its own terms (tiers for a 100% run; items obtained, upgrades
and materials left). Items & Equipment no longer counts potions; Potion Infusions and Item Upgrades
(with its chip on) do, from one store.

### Overview row

Items obtained (an item counts once any copy is owned) of the 16, then upgrades done and what's
left, "Upgrades 0 / 26 · 195 materials and 1,510 Rupees to go" on a new file (one copy of each pouch
item; the potions chip applies), and *Next*:
the first upgrade left on something you have, with who does it.

### Icons

The game's inventory icons for every form of every item, and its Collection icons for the 28
materials: Zelda Wiki's `File:SS <name> Icon.png` (e.g. `SS Hook Beetle Icon.png`,
`SS Heart Potion++ Icon.png`, `SS Amber Relic Icon.png`), fetched unscaled through the wiki's API by
`scripts/fetch-ss-upgrade-icons.py` as `drawable/ss_up_<level id>.png` and `ss_mat_<material id>.png`
(70 files, 40–150 px). An item's row shows the form you have.

### Sources and discrepancies

Every recipe and price was checked against at least two independent sources:
- **Zelda Wiki**, through its API: Upgrading (with its *Quantities Required* table), Scrap Shop, Gear
  Shop, Rupin, Potion Shop, Infusion Shop, Treasure, Insect, Pouch, Hylian Shield, Thunder Dragon's
  Lightning Round, and each item's page.
- **Zelda Dungeon**'s wiki, from Wayback copies since the live site blocks fetches: Skyward Sword
  Upgrades, Gondo, Gear Shop, Luv and Bertie's Potion Shop, Beedle's Air Shop, Upgrade System and the
  item pages.
- **Zelda Universe**'s 2011 guides: equipment upgrades, treasures and bugs.
- **GamerGuides**' HD guide (equipment upgrades, potion infusions, the Bazaar) and **GamingBolt**'s HD
  upgrade list, with GameRevolution for the potions.
- Samurai Gamers copies Zelda Dungeon's table row for row (its errors too), and Fandom's wiki is a
  fork of Zelda Wiki, so neither counts as a second source. IGN and Nintendo Life couldn't be read.

All 19 Scrap Shop recipes and 7 infusions agree with the Items & Equipment and Shields checklists,
which came from the same sources earlier; the tests hold the two together.

| Topic | Sources say | Used |
|---|---|---|
| Hook Beetle | The request listed it as a Scrap Shop upgrade; every source has an Ancient Robot in Lanayru Desert fit it free when you save it from Technoblins, on your first visit in the story | Free, by the robot (`free: true`); Gondo does Quick and Tough only |
| Pouch item names | The request said Seed Satchel → Large → Extra-Large | The game's Small, Medium and Large Seed Satchel, Quiver and Bomb Bag (Zelda Wiki, Zelda Dungeon, the item icons) |
| Who infuses potions | The request said Bertie / the Potion Shop | Luv sells the base potions; Bertie infuses at the same counter (Zelda Wiki, Zelda Dungeon, GamerGuides) |
| Potion spelling | "Heart Potion +" with a space in HD's text and icons; "Heart Potion+" in the Wii icons | The Wii spelling, as the checklists use |
| Scattershot, Reinforced Shield | 20 Rupees (Zelda Wiki); 50 (Zelda Dungeon, Zelda Universe, GamerGuides, GamingBolt) | 50; the step's note says 20 |
| Heart Potion++ | 1 Deku Hornet (Zelda Dungeon's Upgrades and Heart Potion++ pages); 3 (Zelda Wiki, Zelda Dungeon's own Potion Shop and Heart Potion pages, GamerGuides, GamingBolt, GameRevolution) | 3 |
| Medium Seed Satchel | 2 Ornamental Skulls (Samurai Gamers); 3 (everyone else, and Zelda Wiki's total of 12) | 3 |
| Divine Shield | 1 Ornamental Skull (GamerGuides); 3 (Zelda Wiki, Zelda Dungeon, Zelda Universe, GamingBolt) | 3 |
| Tough Beetle | 4 Ancient Flowers (GamerGuides); 3 (the rest, and Zelda Wiki's total of 7) | 3 |
| Guardian Potion | 60 Rupees (Zelda Dungeon's shop page); 200 (Zelda Wiki quoting the game, Zelda Dungeon's item page, GamerGuides) | 200 |
| Revitalizing Potion | 50 Rupees (GamingBolt); 30 (Zelda Wiki, Zelda Dungeon, GamerGuides) | 30 |
| Small Seed Satchel, Small Quiver | 50 Rupees (GamingBolt, Zelda Dungeon's satchel infobox: that's the Item Check's buying price); 100 and 150 (Zelda Wiki's in-game quotes, Zelda Dungeon's Gear Shop, GamerGuides) | 100 and 150 |
| A satchel with the Slingshot | Zelda Wiki's Small Seed Satchel page says one comes with it; its Seed Satchel and Gear Shop pages and Zelda Dungeon say the Slingshot has built-in room for 20 seeds and satchels are bought | Built-in room, in the notes |
| Hylian Shield | Hero Mode only (GameRevolution); 8 Lightning Round wins in either mode (Zelda Wiki) | Either mode; GameRevolution's treasure totals are wrong too |

**From one source only**, so written loosely or left out: the Air Potion going on sale with the Water
Dragon's Scale (GamerGuides) and the Stamina Potion after the Lanayru Mining Facility (GamerGuides and
Zelda Universe's walkthrough); the free satchel, quiver and bomb bag Goddess Chests' exact places
(Zelda Wiki; Zelda Universe confirms each exists); the shields' strengths (Zelda Wiki gives only
ratios, on stub pages, so the effects are approximate); how many Arrows the Bow holds without a quiver (no source says, so it isn't given); and that Gondo
has no story lock (no source says either way; he offers what you carry). Item Check selling prices
(Zelda Wiki only) aren't used. Nothing is missable: every base item is a story item, a shop item that
stays on sale, or a prize that can be won again.

## Potion Infusions

`TrackerKind.POTIONS`, in `potions/`, listed just before Item Upgrades. A recipe book for Luv's and
Bertie's potions, which the user asked for in place of Items & Equipment's Potion Infusions tab.

- **The cover** (`PotionBookScreen`): a card per base potion (Heart, Stamina, Air, Revitalizing,
  Guardian) with its icon, the most infused form you've made and a dot per step, the save file picker,
  and *Mark all complete*.
- **A recipe** (`PotionRecipeScreen`): the base potion, then each infusion in order, each a card with
  its icon, effect, who does it, its Rupees and its bugs as icons with quantities, and a *Bought* /
  *Infused* box. Then *Still needed* (that potion's bugs and Rupees left, the base price included) and
  *Where to catch them* (each bug's regions and how to farm it).
- **Who does what**, from `upgrades.json` and checked by `SsUpgradeDatasetTest`: Luv sells the five
  base potions at her Potion Shop in the Bazaar (20, 50, 20, 30 and 200 Rupees), and Bertie does every
  infusion at the Infusion Shop on the same counter, for bugs only.
- **Total Materials Required** (`PotionTotalsScreen`, from a card on the cover; its route is
  `PotionBookTotals`, sharing the book's view model): Item Upgrades' Materials tab over the potions.
  `PotionBook.totals` runs `UpgradeCalculator.totals` on the potions only (an overload that takes the
  items instead of the chips), so its bugs and Bertie's Rupees are by construction Item Upgrades'
  with potions minus without, in either scope, which `PotionTotalsTest` pins (50 bugs, 180 Rupees
  with nothing collected). On top it adds Luv's price for each potion counted that isn't bought:
  - *Potions you've bought* is `UpgradeTotalsMode.COLLECTED`: the infusions left on bought potions,
    so no base prices;
  - *All potions* is `ALL`, the default here (Item Upgrades defaults to Collected), since the book
    plans every recipe and Luv sells all five from the start: 50 bugs and 500 Rupees in all
    (180 + 320). The scope lives in the view model, so it survives the save-file switch.
  The rows, scope switch, Rupee total and sort row are Item Upgrades' composables
  (`materialTotalRows`, `TotalsModeSwitch`, `RupeeTotal`, `TotalsCountRow`), and the sort is Item
  Upgrades' `materialSort` pref, shared by both screens. A use opens that potion's recipe.
- **Steps tick like a checklist's tiers**: making Heart Potion++ makes the Heart Potion and Heart
  Potion+ too; unticking the Heart Potion clears its infusions.

**No new data.** It reads `upgrades.json`'s `potions` section (`PotionBook.potions`), and its progress
is those items' state in `ss_upgrades@file_N`: *owned* is bought, the level is the infusions done.
So the book and Item Upgrades' Potions section always agree. Item Upgrades keeps the potions, with
its Potions chip, because its Total Materials Required is the one place that adds bugs up across
everything; the chip doesn't hide them from the book. The notes from the old checklist tab (the
Heart Potion++'s Deku Hornets and the Guardian Potion's price) moved to `upgrades.json`.

**Overview row** (`PotionProgressProvider`): steps made of 12 (5 potions, 7 infusions), how many
potions are fully infused, and the next step with who does it ("Heart Potion (Luv's Potion Shop)").

**Tests:** `PotionBookTest` (the recipes, the tick rules, the row), `PotionTotalsTest` (both
scopes, Luv's prices, the cross-check with Item Upgrades' totals) and `PotionViewModelTest` (the
cards, one write per tick, the progress shared with Item Upgrades both ways, mark all and undo, save
files, the totals following ticks in either screen and the save-file switch).

## Lightning Round Records

`TrackerKind.BOSS_RUSH`, in `bossrush/`, listed after Item Upgrades and before the Thrill Digger
solver. After you give the Thunder Dragon the Life Tree Fruit in Lanayru Gorge, he offers the
Lightning Round: rematches against the bosses you've beaten, one after another. The user asked for
a records board. It's a **tracker, not a tool**: it keeps per-file progress and has a Progress
Overview row, which tools don't.

### What the game keeps

**One best time per boss, per save file.** That's all.
- The zeldaret/ss decompilation (Wii, SOUE01) stores each boss's record as three story flags,
  `STORYFLAG_<BOSS>_TIME_MIN`, `_SEC` and `_MS` (`include/d/flag/storyflag_map.h`). There are 12
  bosses, so 36 flags.
- The Thunder Dragon's actor has a `setBest<Boss>Time` for each, plus `isNewBestTime` and
  `storeNewBestTime`.
- There is **no** run-total time, no best per run length and no longest-streak record. The only run
  state is the current time, the boss index and how many have been beaten in this run.
- During a fight the HUD shows the running timer and that boss's best (`setShowBestTime`). After a
  win the Dragon shows the result. Neither the code nor any guide has a separate records list.
- Story flags live in the save file, so records are per file. A Hero Mode file has its own,
  because it's a different file.

**The board mirrors that.**
- Each boss has one slot, with the best time on it. The board adds a short history (the last 20
  times, the best always kept) so improvements show.
- It also shows two totals the game doesn't: the records set, and the sum of the bests.
- **Time format:** `m:ss.cc`, in hundredths. The timer keeps milliseconds and shows six digits,
  `mm ss cc`; `updateLongTimer` in `src/d/lyt/d_lyt_mini_game.cpp` divides by 10 for the hundredths.
  Times cap at **59:59.99** (`TIME_CONVERT(59, 59, 99)`).
- **The 50-Rupee bonus.** The Side Quests item's notes already say that each first win, and each new
  record on a boss, pays 50 Rupees (Zelda Wiki, Fandom, Game8).

### The bosses

`files/ss/boss_rush.json`, in the decompilation's order (its `getBossHighscore` switch), which is
also the story's. The ids are stable, since records are kept under them.

| # | id | Boss | Where first fought |
|---|---|---|---|
| 1 | `ghirahim_1` | Ghirahim (1st battle) | Skyview Temple |
| 2 | `scaldera` | Scaldera | Earth Temple |
| 3 | `moldarach` | Moldarach | Lanayru Mining Facility |
| 4 | `imprisoned_1` | The Imprisoned (1st battle) | Sealed Grounds |
| 5 | `koloktos` | Koloktos | Ancient Cistern |
| 6 | `tentalus` | Tentalus | Sandship |
| 7 | `ghirahim_2` | Ghirahim (2nd battle) | Fire Sanctuary |
| 8 | `imprisoned_2` | The Imprisoned (2nd battle) | Sealed Grounds |
| 9 | `imprisoned_3` | The Imprisoned (3rd battle) | Sealed Grounds |
| 10 | `horde` | Horde Battle | Sealed Grounds, in the past |
| 11 | `ghirahim_3` | Ghirahim (3rd battle) | Sealed Grounds, in the past |
| 12 | `demise` | Demise (Hero Mode only) | Demise's realm |

- Levias and Bilocyte aren't in it (Zelda Wiki, *Lanayru (Dragon)*). Each entry's `flag` names its
  story flags, and `BossRushDatasetTest` pins both orders.
- **Normal is 11 slots, Hero Mode 12.** That's the user's count, and Zelda Wiki's upper bound.
- The Dragon offers only bosses already beaten on the file, so how many a Normal file actually
  offers is disputed:
  - Zelda Wiki: "between nine and 11";
  - Fandom and Game8: 9, with the Horde Battle, Ghirahim's third battle and Demise for Hero Mode.
    A cleared Normal save resumes before the final battles.
- The board keeps the decompilation's 11 non-Demise slots on a Normal file. A slot the file can't
  fight just stays empty. The Horde Battle and Ghirahim 3 carry a note.
- **Hero Mode** puts all 12 in the round from the start, at Hero Mode's double damage (Zelda Wiki,
  Fandom).
- **Skyward Sword HD:** no Lightning Round difference was found in any source.
- **The first boss is yours and the Dragon picks the rest.** Game8 and Nintendo Life say at random.
  Zelda Wiki and Fandom say "depending on how well Link performed", uncited. The selection code
  isn't decompiled yet. Either way, it doesn't affect the board.

**Pictures.**
- Zelda Wiki has no in-game icon for these bosses (only The Imprisoned's map icons). So
  `scripts/fetch-boss-rush-icons.py` bundles each boss's render as `drawable/ss_boss_<icon>.png`,
  fitted in 128 px.
- A boss fought more than once shares one picture through its `icon`, except Ghirahim's third
  battle, which has its own (*SS Ghirahim Render 3*).
- The Horde Battle uses *SSHD Bokoblin Icon*.

### Prizes, not copied

**Prizes.**
- The prizes for wins in a row live in Side Quests & Minigames' `thunder_dragon_boss_rush` item
  (`sideQuest` in `boss_rush.json`), where they're ticked. The board reads that item at runtime
  (`BossRushData.sideQuest`) and shows its tiers and notes.
- The research agreed with what was already there:
  - 1 win: 20 Rupees; 2: a small treasure; 3: 100; 4: a Piece of Heart (later 200);
  - 5: 300; 6: a rare treasure; 7: 500; 8: the Hylian Shield (later 1,000);
  - 9: 2,000; 10: three rare treasures; 11: 3,000; Hero Mode's 12th: a full wallet (9,900).
  - Dying forfeits the prize; record bonuses stay.
- Nothing about prizes changed.

**Story gating.**
- The same item's `unlockedBy` (`hero_song_lanayru`, Lanayru's part of the Song of the Hero, after
  which the Dragon runs the round) gates the board. `bossRushStoryGate` reads it through
  `StoryProgress` on `ss/side_quests.json`.
- With gating on, a file before that step shows a *Not open yet* card, and the overview's *Next*
  says "Opens after Lanayru's part".
- Nothing is hidden: times entered always show and count.

### The board

`BossRushScreen`, landscape first like the Thrill Digger.
- **Landscape:** the boss cards (picture, name, fight, best time) fill the left, under the totals.
  The right-hand panel holds the chosen boss's personal best, in big type, with its date and how much
  it beat the previous best by, then the time entry, its history, and the prizes card.
- **Short landscape** (a phone on its side) hides the bars as HW:DE's map does. The panel then
  carries the menu or Back button, the save file picker and the Hero Mode switch.
- **Portrait:** the panel sits above the cards.

**Entry.** `RecordTime.parse` accepts:
- `m:ss.cc` and `m:ss`;
- `s.cc` in seconds alone;
- `1.45.32`, for a decimal keypad with no colon;
- a comma decimal point;
- bare digits, which fill from the right like a stopwatch (`14532` is 1:45.32).

Seconds after minutes must be under 60, and zero and anything past 59:59.99 are refused. The parsed
time shows under the field as you type. Enter adds it, dated today on the device's calendar
(`todayEpochDay`, a `java.time` actual), and clears the field.

**Editing.** Each history row can be edited (the time; its date stays) or deleted.
- Every add, edit and delete is **one write** to the file's `ss_boss_rush@file_N`, read and changed
  inside the same DataStore edit.
- Each change gives a snackbar with **Undo**, which puts back the boss's whole list from before, on
  the file it was made on.

**Hero Mode switch.**
- One write to `ss_file_settings@file_N`. It never touches records.
- Switching a file to Normal hides Demise's record and says so ("kept but hidden"); switching back
  shows it again.

**Mark-all** doesn't apply, so there's none.

### Storage and the overview

**Storage.**
- `BossRushRecords` maps each boss id to its entries, oldest first. An entry has an `id`, unique
  within the boss, the `time` in hundredths, and the `day` as an epoch day.
- The best is the fastest time, the earlier entry on a tie.
- History is capped at `HISTORY` = 20. The oldest entry goes first, but never the best.
- The view model reads the active file, its records, its settings and its story gate inside one
  `flatMapLatest`, so a switch never pairs one file's records with another's mode (see the
  lost-update notes on save-file switches in other trackers' tests).

**Overview row** (`BossRushProgressProvider`):
- It counts the bosses with a record against the file's slots, in that file's mode: "Records set
  7/11" or "Records set 3/12 (Hero Mode)", then "· bests total 12:34.56".
- *Next* names the first boss without a record, or "Every boss has a record".
- A Normal file doesn't count Demise's hidden record.

**Tests:**
- `RecordTimeTest`: formats and parse edge cases.
- `BossRushDatasetTest`: the order, the flags, 11 against 12, the pictures, and the Side Quests
  item and its step.
- `BossRushRecordsTest`: the best and ties, history trimming, edits and deletes, hidden records,
  and the row and the gate.
- `BossRushViewModelTest`: one write per add, edit and delete; undo, also across a file switch;
  each file's mode; the switch keeping Demise; the gate; a broken dataset.
- `SaveFileSettingsTest`, and `BossRushOverviewTest` (the row following each file's mode).

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

**The recommendation** (`ThrillDiggerSolver.recommend`, both modes): the lowest Bomb chance, then
the lowest Rupoor chance; then, among holes that are **certainly safe**, the one nearest the hole
dug last; then the most information, then the best average haul, then the first hole in reading
order. Near-equal chances count as ties. Holes that must be hazards are never recommended. Once
only those are left, every Rupee is dug.
- *Certainly safe* is an exact zero for both the Bomb and the Rupoor chance, not a near-zero: both
  solvers only add weight to a hole's hazard when some board puts one there, so a hole no board
  puts a hazard in is exactly 0.
- *Nearest* is fewest king moves (Chebyshev distance), then fewest rook steps (Manhattan), so a
  hole in line with the last dig beats a diagonal one. A Green Rupee can clear six or seven holes,
  and without this the most informative of them could be across the board; the user wants the
  next dig beside the last. That puts proximity ahead of information among certain holes: they're
  all free digs, so the order only changes which is dug first, and the others stay certain.
- *Dug last* is the end of the dig order (below). With nothing dug, or for holes that might hide a
  hazard, nearness plays no part, and the order is as before.
- The screen marks every certainly safe hole with a small green check; the recommended one keeps
  its gold ★.

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

It also checks impossible-board conflicts, that every conflict found is minimal, the time, and the
nearness tie-break: beside the last of two Greens, in line before diagonal, and on random boards the
nearest certain hole when there is one and the same hole as without a last dig when there isn't.
`ThrillDiggerViewModelTest` covers entering holes, the recommendation (beside a Green, the most
recent one, in both modes), undo, reset, difficulties and saving. `RupeeTotalTest` covers the
total's floor, its dig order from the history, and the in-game total with and without the floor.

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
  100, Gold 300, Rupoor −10 (`Hole.rupees`). **It never goes below 0**: in the game a Rupoor dug
  with less than 10 takes the total to 0, not into debt. So the order matters (a Red, a Rupoor and
  a Blue make 15; a Rupoor, a Blue and a Red 25), and the total is walked dig by dig,
  `total = max(0, total + value)`. With skipped holes it shows the known part (the walk with each
  skipped hole worth nothing), "+ ? from N skipped", and the range: every skipped hole Green to
  every one Gold. Taking the floor step by step keeps the total rising with each hole's worth, so
  those are the ends; a skipped hole then a Rupoor is 0–290.
- **The dig order** (`DiggerBoard.digOrder`) comes from the undo history, which keeps every earlier
  board. A hole's place is when it last went from undug to dug, so correcting its colour, or
  filling in a skipped hole, keeps its place, and clearing it and digging it again moves it to the
  end. Holes dug before the history begins (a board from an older save, or past its 200 steps)
  come first, and holes dug in the same change (only an old save does that) go in reading order.
  The last hole in the order is the one the recommendation stays near.
- **Enter in-game total** (shown while a hole is skipped) takes the Rupees the round has earned as
  the game counts them (the game shows a running total for the round, not just the wallet) and works out the
  skipped holes (`GameTotal.infer`). It walks the holes in dig order as the total does, floor
  and all, trying each colour for each skipped hole and keeping the ways that end on the game's
  total (`GameTotal.ways`, which first works out which totals can still reach it from each step,
  so only ways that fit are listed). Up to 200 ways are then tried on the board, keeping those the
  solver calls possible, since the colours are clues for their neighbours.
  - The five values are distinct, so one skipped hole above the floor is certain: "That hole was a
    Blue Rupee: fill it in?".
  - **The floor makes some ambiguous.** A skipped hole dug at 0 and then a Rupoor: a Green (1) and
    a Blue (5) both end at 0, so "The total can't tell: that hole was Green or Blue." A Red (20)
    still leaves 10, so 10 is certain. The board can still settle it: a Rupoor beside the hole
    means it wasn't Green.
  - One way left is offered to fill in. Otherwise it lists up to six colour sets ("Green + Blue,
    but the total can't tell which hole is which", or "The total can't tell: …"), and any skipped
    holes that are the same colour every way are offered to fill in on their own ("Either way, F5
    Red: fill it in?"). Without the floor, the smallest count where two sets share a sum is six
    holes (five Greens and a Silver, or a Blue and five Reds: 105).
  - Filling in is one undoable change, and the filled holes are ordinary clues from then on. A
    total nothing adds up to says to check the total and the holes.
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
