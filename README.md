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
  Divine Beast Heart Containers aren't tracked.
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
  src/commonMain/kotlin/gx/zelda/mainquest/ ← main quest tracker (BotW only for now)
  src/commonMain/kotlin/gx/zelda/shrines/  ← shrine tracker and Spirit Orb planner
  src/commonMain/kotlin/gx/zelda/compendium/  ← Hyrule Compendium tracker
  src/commonMain/kotlin/gx/zelda/koroks/   ← Korok Seed tracker and Hestu planner
  src/commonMain/kotlin/gx/zelda/core/ui/  ← shared UI, incl. the region-card pieces both use
  src/commonMain/composeResources/files/<game>/armor.json    ← per-game armor data
  src/commonMain/composeResources/files/<game>/fairies.json  ← per-game Great Fairies
  src/commonMain/composeResources/files/<game>/shrines.json  ← per-game shrines
  src/commonMain/composeResources/files/<game>/koroks.json   ← per-game Korok Seeds + Hestu costs
  src/commonMain/composeResources/files/<game>/korok_puzzle_types.json  ← puzzle-type taxonomy
  src/commonMain/composeResources/files/botw/main_quest.json ← towers, Divine Beasts, memories
  src/commonMain/composeResources/files/botw/compendium.json  ← BotW Hyrule Compendium
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
