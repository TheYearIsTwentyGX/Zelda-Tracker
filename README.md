# ZeldaGX

A progress tracker for Zelda games. Android for now; built with Compose Multiplatform so an
iOS port is mostly a matter of adding the iOS targets.

## Features

**Breath of the Wild: Progress Overview**
- The first card under BotW: every tracker's progress on the active save file on one screen, each
  with a progress bar and its numbers. Tap a row to open that tracker.
- Shrines (completed of 120, found, and the Champions' Ballad's 16 counted apart), Korok Seeds (of
  900, and Hestu upgrades bought), Hyrule Compendium (of the save file's own total: 389, or 394 in
  Master Mode), Main Quest (towers, Divine Beasts and memories), Armor (collected, and how many are
  fully upgraded; the armor list's DLC and amiibo filters apply), Great Fairy Fountains (open, and
  what the next costs), Hearts & Stamina (Spirit Orb upgrades bought, hearts and stamina wheels) and
  Side Quests (completed across the Side, Shrine and DLC tabs, with each tab's tally) and Overworld
  Bosses (spawns defeated, each kind's count, and the next Medal of Honor to earn).
- Most rows suggest a next step. With a tracker's *Hide undiscovered* on, the suggestion names only
  shrines or Koroks already found or quests already started, as the list does, and otherwise just
  the region.
- **Map completion**: Korok seeds, shrines and Divine Beasts count toward the map's percentage
  (Nintendo Life, Screen Rant), so the overview shows how many of those 1,024 items are done. Named
  locations count too, but sources disagree on how many there are (187 or 226) and no tracker
  records them, so this is a count of items toward 100%, not the percentage.
- The save file picker switches every tracker. A tracker whose data can't be loaded shows as
  unavailable without holding up the rest.

**Breath of the Wild: Armor Progress**
- All 107 armor pieces: base game, The Master Trials, The Champions' Ballad and amiibo.
- In-game inventory icon for every piece, in the list and on its detail page.
- Mark pieces as collected and set each one's upgrade level (★0–★4).
- Defense at every level, piece effects, and set bonuses with live active/inactive status.
- Materials for every remaining upgrade on each piece.
- **Total Materials Required**: the sum of every remaining upgrade, for *Collected Armor* or
  *All Armor (Including Uncollected)*. Tap a material to see which pieces need it.
- DLC and amiibo filters for both the list and the totals.

**Breath of the Wild: Great Fairy Fountains**
- Where to find Cotera, Kaysa, Mija and Tera: region, directions and nearby landmarks.
- Mark each fountain as open. The screen shows what the next one costs (100 / 500 / 1,000 /
  10,000 rupees, by how many are already open) and how far armor can be upgraded.
- Shares the active save file with the armor tracker.

**Breath of the Wild: Main Quest Progress**
- **Sheikah Towers**: all 15, with where each one stands and what's in the way of the climb.
- **Divine Beasts**: Vah Ruta, Rudania, Medoh and Naboris, with the Champion, the main quest that
  frees each one and how it starts, what to prepare, the Blight boss and the rewards.
- **Recovered Memories**: all 18 in Adventure Log order. The 12 album pictures carry their photo
  number, the location and a hint (including which stable Pikango recognises it at). The final
  picture (#17) unlocks once the 12 are found, and the 5 quest-triggered memories say which quest
  triggers them.
- Mark towers activated, beasts freed and memories recovered. Each tab shows its count, and the
  save file picker shows all three.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Shrines**
- All 120 shrines plus the 16 from The Champions' Ballad, grouped by tower region with counts per
  region and overall (e.g. 87 / 120).
- Each shrine is Undiscovered, Found or Completed: tick a row to complete it, and use its pin
  to mark it found. Completing implies found; unticking leaves it found.
- **Hide undiscovered shrines** (top-bar menu, off by default, same for every save file): an
  undiscovered shrine's row shows only "Undiscovered shrine" under its region until you tap to
  reveal it for the session or mark it found. Search only matches such rows by region.
- Each shrine's trial, whether it's a Puzzle, Test of Strength or Blessing, its Shrine Quest if it
  has one, and a one-line location hint.
- Search by shrine, trial, quest or region; filter to incomplete shrines or Shrine Quests, and
  hide the DLC.
- **Spirit Orbs**: orbs earned from completed shrines, what you've spent on Heart Containers and
  Stamina Vessels, and what's left. 120 orbs in the base game; the Champions' Ballad's four Great
  Plateau shrines add 4 more (its other twelve give emblems). Purchases are entered by hand.
  Hearts count 3 to start, one per Divine Beast marked freed in Main Quest Progress on the same
  save file, and those bought; Goddess Statues stop at 30, so each freed beast lowers how many
  can be bought (27 with none, 23 with all four). A number entered before a beast was freed is
  kept and flagged in red if it now goes past 30. All 120 orbs buy 30 upgrades (31 with the DLC),
  short of the 33 that max both hearts and stamina.
- **Scan map screenshots** (top-bar menu): reads shrine states from screenshots of the in-game map
  (2D map, fully zoomed out, 1920×1080 straight from the Switch album; one of the north half and one
  of the south). Each shrine comes out as *Completed* (blue glyph), *Not done* (orange glyph),
  *Not on the map* (spot clearly empty) or *Unsure*, with why: covered by another icon, a pin, a
  stamp or the Hero's Path trail; stacked with another shrine (Ree Dahee and Shee Venath share a
  spot); at the screen edge; or in no screenshot. Any number of screenshots merge: a definite reading
  beats an edge or missing one, and two definite readings that disagree become Unsure.
  Nothing is written until you review it. Completed and Not done are pre-ticked, Unsure shrines are
  listed by name for you to decide, Not on the map is never written, a shrine already completed is
  never downgraded, and Apply asks for confirmation before writing to the active save file. With
  *Hide undiscovered shrines* on, empty spots are only counted and unsure undiscovered shrines stay
  hidden until tapped. Screenshots at the wrong size, not fully zoomed out or with too few icons to
  line up are rejected with a reason.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Hyrule Compendium**
- All 389 entries (385 base game + 4 from The Champions' Ballad) in the in-game order and
  numbering, and Master Mode's 394, with its five extra entries and its own numbers, on the
  Master Mode file.
- A tab per category with photographed counts; Creatures is split into animals and food (048–083,
  the entries that show hearts recovered rather than materials). Search by name, number, location
  or drop, and a *Missing only* filter.
- Each entry's common locations and drops, and how to buy its picture from Symin if you can't
  take it (Elite Enemy Pictures only after Dark Beast Ganon). Entries that can only be
  photographed at certain times — bosses, DLC trials — are marked *Limited*.
- Tracks Save File 1, Save File 2 and Master Mode separately, sharing the active file.

**Breath of the Wild: Korok Seeds**
- All 900 seeds grouped by tower region, with counts per region and overall (e.g. 312 / 900).
- Each row shows the puzzle type (Lone rock, Race to the ring, Pinwheel balloons… 29 in all), the
  nearest named place and objmap's id; one-off puzzles also show objmap's own name for them.
- Undiscovered, Found or Collected per seed, like shrines: tick a row to collect it, use its pin to
  mark it found.
- **Hide undiscovered Koroks** (top-bar menu): the same spoiler-safe mode as shrines, but a
  separate setting, so one can be on without the other. Hidden rows only match a search by region
  and never match a puzzle-type filter.
- Search by place, puzzle type, region or id; filter to incomplete seeds or to any mix of puzzle
  types (bottom sheet, with how many seeds each type has).
- Tap › for a seed's page: how to solve its puzzle type, region, nearest place, coordinates, and
  **Open in objmap**, which opens objmap.zeldamods.org centred on that Korok with its game data.
- **Hestu**: seeds collected against the weapon, bow and shield upgrades you've bought from him
  (entered by hand), what the seeds in hand buy cheapest-first, and what's left. Maxing every
  pouch takes 441 seeds; the rest only count toward Hestu's Gift for all 900.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Side Quests**
- All 76 base-game side quests, the 42 Shrine Quests and the 13 DLC side quests ("EX" quests from
  The Master Trials and The Champions' Ballad), a swipeable tab each, grouped by tower region with
  counts per region and per tab.
- Each quest is Not started, In progress or Completed: tick a row to complete it, and use its play
  button to mark it in progress. Completing implies started; unticking leaves it in progress.
- **Hide quests not started** (top-bar menu): the same spoiler-safe mode as shrines and Koroks, but
  its own setting. A hidden quest's row shows only its region until tapped, and search only matches
  it by region.
- Search by quest, giver, place, reward, region or linked shrine; filter to incomplete quests.
- Tap › for a quest's page: giver and where to find them, what has to happen first, a spoiler-light
  objective, the reward and, where it's well sourced, when it can only be done (night, a Blood
  Moon). Reward armor opens in Armor Progress.
- A Shrine Quest's page shows its shrine's state from the shrine tracker on the same save file.
  Completing the quest doesn't complete the shrine, since the trial inside is still to do.
- EX quests are here because the Adventure Log lists them under Side Quests. The DLC's five main
  quests (EX The Champions' Ballad and the four EX Champion … Song quests) are Main Quests in the
  log, so they aren't.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Overworld Bosses**
- Every fixed Hinox, Stalnox, Talus, Molduga and Lynel spawn: 40 Hinoxes, 40 Taluses, 4 Moldugas and 22 Lynels, plus
  The Champions' Ballad's Igneo Talus Titan and Molduking (marked DLC) and Master Mode's extra Lynel (on the Master
  Mode file only).
- A swipeable tab per kind with its count, each grouped by tower region with counts per region.
- Not found, Found or Defeated per spawn (defeated once is enough: bosses return after a Blood Moon). Tick a row to
  mark it defeated, use its pin to mark it found.
- Each tab's headline tracks Kilton's **Medals of Honor**, e.g. "Hinox 12 / 40 · Medal of Honor: Hinox". DLC bosses
  are listed but not counted; Lynels have no medal.
- **Hide unfound bosses** (top-bar menu): the same spoiler-safe mode as shrines and Koroks, with its own setting.
- Search by place, variant, region or id; filter to bosses not yet defeated, or by variant (Blue Hinox, Frost
  Talus…).
- Tap › for a spawn's page: its Compendium entry and number, drops, region, nearest place, coordinates, notes (the
  Coliseum Lynel, the Hyrule Castle bosses, Eventide Island…) and **Open in objmap**. Lynels whose colour rises with
  the world level are tagged *Scales* and listed at their weakest colour.
- Shares the active save file with the other BotW trackers.

**Tears of the Kingdom: Armor Progress**
- All 135 armor pieces in 35 sets plus single pieces, with the same collection, upgrade, set
  bonus and material-totals features as BotW.
- Inventory icon for every piece; pieces that are also in BotW share its icon.
- Prices in Poes (Bargainer Statues) and Bubbul Gems (Koltin) as well as rupees.
- No DLC, and every amiibo piece can also be found in the world, so there are no source filters.
  The list sorts A–Z only, because the in-game sort orders haven't been verified yet.

**Tears of the Kingdom: Great Fairy Fountains**
- Where to find Tera, Mija, Kaysa and Cotera, and the Stable Trotters side adventure that frees
  each one (Tera first; she unlocks the other three).
- Mark each fairy as freed; the screen shows how far armor can be upgraded and the per-upgrade
  rupee fee (10 / 50 / 200 / 500).

**Tears of the Kingdom: Shrines & Lightroots**
- All 152 Shrines of Light and the 120 Lightroots beneath them, as two swipeable tabs with their
  counts (e.g. 87 / 152 and 40 / 120), each grouped by Skyview Tower region with per-region counts.
- Each shrine's trial (Rauru's Blessing, Proving Grounds, Combat Training or a puzzle), whether it
  is on a sky island or in a cave, its Shrine Quest if it has one, its map area and a location hint.
- Each Lightroot points at the shrine above it, and its name is the shrine's spelled backwards
  (Kawumoro under Oromuwak Shrine). Only the 32 sky shrines have none. A shrine's row shows its
  Lightroot, a Lightroot's row shows its shrine, and › opens a page with the pair. Each half is
  Undiscovered, Found or Completed / Lit on its own.
- The same **Hide undiscovered** setting as BotW, covering both tabs, plus search and an
  *Incomplete* / *Unlit* filter. A hidden Lightroot only matches a search by region, and finding a
  shrine doesn't reveal the Lightroot below it.
- **Lights of Blessing**: Lights earned from completed shrines, Heart Containers and Stamina
  Vessels bought, the six boss Heart Containers (five temples and Phantom Ganon, entered by hand)
  and A Call from the Depths' free heart or vessel. Maxing both takes 47 upgrades against the 45
  available, so the card says you can't. There are no Lights outside shrines, and Crystallized
  Charges are Zonai currency, so neither counts.

All three TotK trackers track **Save File 1** and **Save File 2** separately and share the active file.

## Layout

```
shared/       Kotlin Multiplatform module: models, logic, Compose UI, datasets
  src/commonMain/kotlin/gx/zelda/armor/    ← armor tracker, shared by every game
  src/commonMain/kotlin/gx/zelda/fairies/  ← Great Fairy tracker, shared by every game
  src/commonMain/kotlin/gx/zelda/mainquest/ ← main quest tracker (BotW only for now)
  src/commonMain/kotlin/gx/zelda/shrines/  ← shrine tracker (BotW; TotK with Lightroots) and Spirit Orb / Light of Blessing planner
  src/commonMain/kotlin/gx/zelda/shrines/scan/  ← map-screenshot scanner and its review screen
  src/commonMain/kotlin/gx/zelda/compendium/  ← Hyrule Compendium tracker
  src/commonMain/kotlin/gx/zelda/koroks/   ← Korok Seed tracker and Hestu planner
  src/commonMain/kotlin/gx/zelda/sidequests/  ← side quest tracker (Side, Shrine and DLC quests)
  src/commonMain/kotlin/gx/zelda/bosses/   ← overworld boss tracker (Hinox, Talus, Molduga, Lynel)
  src/commonMain/kotlin/gx/zelda/core/ui/  ← shared UI, incl. the region-card pieces both use
  src/commonMain/kotlin/gx/zelda/overview/  ← Progress Overview screen and its provider list
  src/commonMain/composeResources/files/<game>/armor.json    ← per-game armor data
  src/commonMain/composeResources/files/<game>/fairies.json  ← per-game Great Fairies
  src/commonMain/composeResources/files/<game>/shrines.json  ← per-game shrines (TotK's also has the Lightroots)
  src/commonMain/composeResources/files/<game>/shrine_map.json  ← shrine/tower world positions + icon templates
  src/commonMain/composeResources/files/<game>/koroks.json   ← per-game Korok Seeds + Hestu costs
  src/commonMain/composeResources/files/<game>/korok_puzzle_types.json  ← puzzle-type taxonomy
  src/commonMain/composeResources/files/botw/bosses.json     ← overworld boss spawns
  src/commonMain/composeResources/files/botw/main_quest.json ← towers, Divine Beasts, memories
  src/commonMain/composeResources/files/botw/compendium.json  ← BotW Hyrule Compendium
  src/commonMain/composeResources/files/botw/side_quests.json ← BotW side, Shrine and DLC quests
  src/commonMain/composeResources/drawable/armor_<id>.png    ← armor icons, shared by every game
androidApp/   Android application shell
```

Each game's trackers are listed in `core/Games.kt`; storage keys are prefixed with the game id.

### Adding a tracker to the Progress Overview

Write a `ProgressProvider` (`core/ProgressSummary.kt`) next to the tracker's storage, e.g.
`koroks/data/KorokProgressProvider.kt`: it maps one save file's progress to a `ProgressSummary`
(title, done / total, an optional detail line, an optional next step, and whether it counts
toward map completion). Then return it for the tracker's `TrackerKind` in
`overview/OverviewProviders.kt`; that `when` is exhaustive, so a new kind won't compile until it
has a branch. The interface's KDoc has the details.

## Building

Requires JDK 17+ and the Android SDK (set `sdk.dir` in `local.properties`).

```bash
./gradlew :shared:jvmTest            # unit + dataset validation tests
./gradlew :androidApp:assembleDebug  # APK at androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug   # install on a connected device/emulator
```

### TGXCode buttons

`.tgxcode/commands.json` declares these buttons:

- **Build** / **Rebuild** (`scripts/build.sh`, `--clean` for Rebuild) assemble the debug APK,
  finding the SDK even without a `local.properties`.
- **Install on phone** (`scripts/install-wireless.sh`) finds the phone over Wireless Debugging
  (it uses adb's mDNS discovery and connects if needed), installs the debug build and opens it.
  Pair the phone once by hand first; the script prints the `adb pair` command if it can't find one.
- **Land** (`scripts/land.sh`) merges the current worktree's PR and fast-forwards the main
  checkout. `--status` and `--dry-run` show what it would do.

## How the map scan works

`ShrineMapScanner` is pure Kotlin in `commonMain`, with no OpenCV or ML Kit. Each platform only
decodes the image (`BitmapFactory` on Android, `ImageIO` on the JVM). It is a port of the Python
feasibility prototype in `tools/shrine-map-spike` on branch `worktree-shrine-map-spike` (see its
`REPORT.md`):

1. Colour masks in OpenCV's 8-bit HSV, computed with OpenCV's fixed-point formula so the
   prototype's thresholds carry over.
2. Candidate shrine and tower icons: connected components of the icon-blue masks, each confirmed
   by matching a template around it.
3. Registration. The fully zoomed-out map is world × 0.135 px plus a translation. Candidates vote
   for a translation against the world positions of their own class (shrine or tower), and the
   inliers are averaged. A fit needs at least 3 icons, and at least half of the icons found. A free
   least-squares scale more than 3% from 0.135 means the map isn't fully zoomed out.
4. Each shrine's projected spot is classified from a window around it: silhouette score, outline
   coverage, glyph colour, and other colours covering the spot.

**Hero's Path mode** is detected from its green frame. Trail pixels count as unknown: icons are
matched on the pixels around the trail, and a spot only counts as empty when at least half its
outline is free of trail. In the golden test, pasting a real trail over icons known to be there
leaves every one readable except Shee Vaneer, which already half-overlaps the Dueling Peaks pair.
The timeline slider replaces the usual HUD as the covered area.

Golden tests (`shared/src/jvmTest/.../scan/ShrineMapGoldenTest.kt`) run the scanner on four real
screenshots in `shared/src/jvmTest/resources/map-samples`. The all-complete save gives 132
Completed and 4 Unsure: Hawa Koth (under the Travel Medallion), Akh Va'quot (under Vah Medoh), and
Ree Dahee and Shee Venath (stacked). The partial save, whose south shot is in Hero's Path mode,
reads Tena Ko'sah, Qua Raym and Shae Mo'sah as Not done. The tests also compare every shrine in
every shot with the prototype's CSV output. The only differences are three spots in the Hero's
Path shot, where trail covers most of the outline: the prototype calls them empty and the port
calls them Unsure.

`shrine_map.json` holds the shrine and tower positions from objmap.zeldamods.org, matched to
`shrines.json` by name, and the prototype's 33×33 shrine and tower silhouettes.

## Data sources

Armor data was compiled from Zelda Wiki and cross-checked against Zelda Fandom and Game8.
Where sources disagreed, the majority value was used.

BotW main quest data (towers, Divine Beasts and Recovered Memories) comes from Zelda Wiki: the
memory list and Pikango's hints from its Memory and Captured Memories pages, and the beasts from
each Divine Beast quest page. Tower climbing tips are cross-checked against Zelda Fandom, Zelda
Dungeon, GameWith and RankedBoost, and memory directions against RPG Site and Nintendo Insider.

TotK armor data comes from Zelda Wiki's TotK armor tables, cross-checked against Game8,
Fextralife, Thonky, Zelda Universe, Gamer Guides and Samurai Gamers. TotK Great Fairy data comes
from Zelda Wiki, PowerPyx, Nintendo Life and Shacknews. The TotK Materials pouch order is from
Zelda Wiki only.

BotW shrine data (names, trials, Shrine Quests, locations) comes from Zelda Wiki and Zelda Fandom.
Shrines are grouped by Sheikah Tower region as Game8 and Thonky group them; the wikis use
geographic regions instead, so a few border shrines (e.g. Ishto Soh, Kah Mael, Rona Kachta) sit
under a different region there. Trial names keep the in-game wording, including the "A" in
"A Minor Test of Strength".

TotK shrine and Lightroot data (`files/totk/shrines.json`) combines objmap's TotK data with the
wikis; see `docs/totk-shrines.md`. Names, positions, sky/surface/cave and Skyview Tower regions
are datamined (radar-totk.zeldamods.org and the objmap-totk repo). Trials, Shrine Quests and
location hints come from Zelda Wiki, cross-checked against Zelda Fandom and Game8. The Light of
Blessing numbers are from Zelda Wiki, Fandom and Game8.

BotW Compendium numbering is from Zelda Wiki's *Hyrule Compendium Items in Breath of the Wild*
(Normal and Master Mode, with and without The Champions' Ballad). Common locations and drops are
the in-game text, from the Hyrule Compendium API (api.hyrule-compendium.com) checked against
Zelda Wiki's `Data:Hyrule Compendium/BotW`, which also supplied the drops the API got wrong.
Locations for entries the game lists as unknown, Symin's picture sales and the *Limited* notes
come from Zelda Wiki and Zelda Fandom.

Armor icons are the in-game inventory icons, downloaded from Zelda Wiki by
`scripts/fetch-armor-icons.py`. Icons are keyed by piece id, so a piece that appears in both games
(same id, e.g. `hylian_hood`) shares one image, taken from BotW (`File:BotW <name> Icon.png`).
Pieces only in TotK use `File:TotK <name> Icon.png`. Run the script after adding a piece to any
game's `armor.json`; it fetches only the icons that are missing (`--force` re-downloads all of
them), and each game's armor dataset test fails if any piece has no icon.

BotW Korok Seed data (`files/botw/koroks.json`, `files/botw/korok_puzzle_types.json`) comes
from objmap.zeldamods.org and its radar backend: ids, positions, tower regions and each seed's
datamined `korok_type`, which decides its puzzle type. The puzzle-type names and descriptions
follow Zelda Wiki. See `docs/korok-puzzle-types.md` for the fields, the coordinate system and how
the types map. Hestu's per-upgrade seed costs are from Zelda Wiki's Korok Seed page
(208 + 73 + 160 = 441).

BotW side quest data (`files/botw/side_quests.json`) comes from Zelda Wiki's *Side Quests in Breath
of the Wild* and *Shrine Quest* pages and each quest's page, cross-checked against Game8's side
quest, Shrine Quest and DLC lists: 76 side quests, 42 Shrine Quests and 13 EX side quests. Zelda
Fandom and Thonky give the same 76. Regions are tower regions, worked out from where the quest giver stands using
objmap's tower-region map data, which Game8's per-quest regions agree with. A Shrine Quest takes its
shrine's region instead, so both trackers group it the same way, and its `shrineId` is the shrine's
id in `shrines.json`. Timing notes are only given where a source states them.

BotW overworld boss data (`files/botw/bosses.json`) comes from objmap.zeldamods.org and its radar backend: every
boss actor's id, position, tower region and parameters, with names and drops from `compendium.json`. Counts and the
Medals of Honor are cross-checked against Zelda Wiki; where they differ, the game data wins.
`scripts/build-botw-bosses.py` rebuilds it, and `docs/overworld-bosses.md` covers the fields, the counts and what's
uncertain (Lynel colours).
