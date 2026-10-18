# Skyward Sword

Skyward Sword's trackers are checklists (`TrackerKind.CHECKLIST`, see `docs/checklist-trackers.md`):
eight JSON files in `shared/src/commonMain/composeResources/files/ss/`, listed in `core/Games.kt`
after a Progress Overview. `SsDatasetTest` checks the counts below and the links between files, and
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
