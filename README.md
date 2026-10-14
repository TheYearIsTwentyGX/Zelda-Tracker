# ZeldaGX

A progress tracker for Zelda games. Android for now; built with Compose Multiplatform so an
iOS port is mostly a matter of adding the iOS targets.

## Features

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
  Divine Beast Heart Containers aren't tracked.
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

Both TotK trackers track **Save File 1** and **Save File 2** separately and share the active file.

## Layout

```
shared/       Kotlin Multiplatform module: models, logic, Compose UI, datasets
  src/commonMain/kotlin/gx/zelda/armor/    ← armor tracker, shared by every game
  src/commonMain/kotlin/gx/zelda/fairies/  ← Great Fairy tracker, shared by every game
  src/commonMain/kotlin/gx/zelda/shrines/  ← shrine tracker and Spirit Orb planner
  src/commonMain/kotlin/gx/zelda/shrines/scan/  ← map-screenshot scanner and its review screen
  src/commonMain/composeResources/files/<game>/armor.json    ← per-game armor data
  src/commonMain/composeResources/files/<game>/fairies.json  ← per-game Great Fairies
  src/commonMain/composeResources/files/<game>/shrines.json  ← per-game shrines
  src/commonMain/composeResources/files/<game>/shrine_map.json  ← shrine/tower world positions + icon templates
  src/commonMain/composeResources/drawable/armor_<id>.png    ← armor icons, shared by every game
androidApp/   Android application shell
```

Each game's trackers are listed in `core/Games.kt`; storage keys are prefixed with the game id.

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

TotK armor data comes from Zelda Wiki's TotK armor tables, cross-checked against Game8,
Fextralife, Thonky, Zelda Universe, Gamer Guides and Samurai Gamers. TotK Great Fairy data comes
from Zelda Wiki, PowerPyx, Nintendo Life and Shacknews. The TotK Materials pouch order is from
Zelda Wiki only.

BotW shrine data (names, trials, Shrine Quests, locations) comes from Zelda Wiki and Zelda Fandom.
Shrines are grouped by Sheikah Tower region as Game8 and Thonky group them; the wikis use
geographic regions instead, so a few border shrines (e.g. Ishto Soh, Kah Mael, Rona Kachta) sit
under a different region there. Trial names keep the in-game wording, including the "A" in
"A Minor Test of Strength".

Armor icons are the in-game inventory icons, downloaded from Zelda Wiki by
`scripts/fetch-armor-icons.py`. Icons are keyed by piece id, so a piece that appears in both games
(same id, e.g. `hylian_hood`) shares one image, taken from BotW (`File:BotW <name> Icon.png`).
Pieces only in TotK use `File:TotK <name> Icon.png`. Run the script after adding a piece to any
game's `armor.json`; it fetches only the icons that are missing (`--force` re-downloads all of
them), and each game's armor dataset test fails if any piece has no icon.
