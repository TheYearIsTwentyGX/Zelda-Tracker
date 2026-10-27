# Licensing audit

This is what the app bundles and where each part came from, with what has to change before a Google Play or
App Store release. Today's app is a personal, sideloaded build. A store build may ship only what we own or may
license. Nintendo's art, and art and data whose owners grant no licence, moves into an **art pack** the user
imports themselves, the way emulators load user-supplied files.

Audited on 2026-10-27 at `069c1c8`. Echoes of Wisdom's map (tiles, icons and eow-tools positions) is in another
worker's open PR; its rows below are marked *pending* and get classified when it lands.

## Classes

| Class | Meaning | Store build |
|---|---|---|
| **safe** | Ours, or openly licensed and fine to ship as is | Ships |
| **attribute** | May ship, with credit or a licence notice in the app | Ships with credit |
| **pack** | Nintendo's art, or art with no licence | Leaves the store build; the art pack supplies it |
| **replace** | Ours, but it depicts a Nintendo trademark | Replaced in the store build |
| **derived** | Low-resolution measurements of the game's screen that the scanners match against | Ships (low risk; see [Scanner references](#scanner-references)) |
| **data** | Facts in our own wording, compiled from the sources in [Data sources](#data-sources) | Ships, with credit |
| **risky** | A third party's compilation copied wholesale, or someone else's text | Needs the action listed |
| **repo** | In the private repo for tests or tooling, never shipped | Unaffected while the repo is private |

## Bundled files

`scripts/licence-inventory.py` builds this table from the files themselves. Each file is matched against
the script's rules, and the first rule that matches wins. `--write` refreshes the table, and `--check` fails
if a file matches no rule or the table is stale. A new asset therefore can't join the app unclassified.

<!-- inventory:start -->

| Group | Files | Size | Source | Owner | Licence | Class |
|---|---:|---:|---|---|---|---|
| BotW map icons (shrine, tower) | 6 | 7.1 KB | objmap `public/icons/*.svg`, split by `scripts/build-map-icons.py` | Nintendo design; objmap tracing | GPL-3.0 (tracing) | **pack** |
| BotW map icons (Korok, bosses) | 5 | 33.7 KB | Zelda Wiki file uploads (`scripts/build-map-icons.py`) | Nintendo | none granted | **pack** |
| Armor icons (BotW, TotK) | 153 | 4.4 MB | Zelda Wiki file uploads (`scripts/fetch-armor-icons.py`) | Nintendo | none granted | **pack** |
| HW:DE item card icons | 70 | 275.5 KB | Zelda Wiki file uploads (`tools/hwde/build_maps.py`) | Nintendo/Koei Tecmo | none granted | **pack** |
| HW:DE reward icons | 3 | 33.9 KB | Zelda Wiki file uploads (`tools/hwde/build_maps.py`) | Nintendo/Koei Tecmo | none granted | **pack** |
| SS Lightning Round boss pictures | 9 | 203.8 KB | Zelda Wiki renders (`scripts/fetch-boss-rush-icons.py`) | Nintendo | none granted | **pack** |
| SS Thrill Digger pictures | 7 | 110.3 KB | Zelda Wiki renders (`scripts/fetch-thrill-digger-icons.py`) | Nintendo | none granted | **pack** |
| SS item upgrade icons | 42 | 211.4 KB | Zelda Wiki file uploads (`scripts/fetch-ss-upgrade-icons.py`) | Nintendo | none granted | **pack** |
| SS material icons | 28 | 88.1 KB | Zelda Wiki file uploads (`scripts/fetch-ss-upgrade-icons.py`) | Nintendo | none granted | **pack** |
| BotW map tiles | 1700 | 9.8 MB | objmap.zeldamods.org MapTex tiles (`tools/map-tiles/build_tiles.py`) | Nintendo | none granted | **pack** |
| BotW map tile manifest | 1 | 850 B | `tools/map-tiles/build_tiles.py` | ours | - | **safe** |
| HW:DE Adventure Map squares and previews | 979 | 2.4 MB | Hyrule Warriors Helper `public/map-tiles/`, `public/map-previews/` | Fabian-F, after Nintendo/Koei Tecmo layouts | none (no licence) | **pack** |
| Zelda Notes droplet template | 1 | 9.2 KB | crop of the Switch App's Zelda Notes map, from the owner's phone | Nintendo | none granted | **pack** |
| Map-screen chrome references (live scan) | 2 | 98.8 KB | grey thumbnails of the owner's photos of the map screen (`ScreenChromeReferenceTest`) | derived from Nintendo's screen | - | **derived** |
| Korok marker template (live scan) | 1 | 2.2 KB | 28×18 grey average of the found-Korok marker from the owner's clips | derived from Nintendo's screen | - | **derived** |
| BotW shrine map (positions, icon masks) | 1 | 12.3 KB | objmap and radar positions; icon silhouettes measured from screenshots | datamined; derived | - | **derived** |
| HW:DE Adventure Map data | 10 | 806.1 KB | Hyrule Warriors Helper `v2` data (`tools/hwde/build_maps.py`) | Fabian-F; card text Zelda Wiki | none (no licence); CC BY-SA text | **risky** |
| BotW Korok positions | 1 | 259.0 KB | objmap `korok_ids.json` and radar.zeldamods.org | datamined from the game | not stated | **risky** |
| BotW overworld boss positions | 1 | 61.8 KB | radar.zeldamods.org (`scripts/build-botw-bosses.py`) | datamined from the game | not stated | **risky** |
| TotK shrine and Lightroot positions | 1 | 57.5 KB | radar-totk.zeldamods.org, objmap-totk | datamined from the game | not stated | **risky** |
| MC figurine gallery | 1 | 39.8 KB | Zelda Wiki (game text); zeldaret/tmc (`scripts/build-mc-figurines.py`) | Nintendo text | none granted | **risky** |
| Tracker data | 136 | 2.5 MB | compiled from the sources in the data table | facts; our wording | - | **data** |
| Theme fonts | 5 | 310.2 KB | Google Fonts (`docs/fonts.md`) | their authors | SIL OFL 1.1 | **safe** |
| Launcher icon (Triforce) | 1 | 548 B | drawn for this app | ours; depicts the Triforce | Nintendo trademark | **replace** |
| Launcher icon background and wrappers | 3 | 1.2 KB | drawn for this app | ours | - | **safe** |
| Test photos of Nintendo screens | 188 | 11.5 MB | the owner's photos, videos and Switch App screenshots | Nintendo screens | - | **repo** |
| Map alignment overlay | 1 | 354.0 KB | `build_tiles.py --debug-overlay` | Nintendo map art | none granted | **repo** |

Shipped today, by class: data 136 files (2.5 MB), derived 4 files (113.4 KB), pack 3003 files (17.6 MB), replace 1 files (548 B), risky 14 files (1.2 MB), safe 9 files (312.2 KB).

<!-- inventory:end -->

## Images

- **Nothing in `composeResources/drawable` is ours.** All 323 files are Nintendo's art, or tracings and
  renders of it:
  - **Zelda Wiki uploads:** the wiki hosts the game's icons and renders under fair-use terms of its own. It
    grants nobody a licence to them.
  - **The `instrument` card:** the one HW:DE card icon with no wiki file (*Any Instrument of the Sirens*). The
    build script fell back to the Hyrule Warriors Helper's own picture for it. The 264×174 card icons (Fire
    Rod, Goddess's Harp, Sea Lily Bell, Wind Waker) are the wiki's own uploads.
- **The objmap shrine and tower icons** are vector tracings of the game's map icons, from objmap's repository,
  which is GPL-3.0. The design underneath is still Nintendo's. Meeting the GPL wouldn't make the store build
  safe, so they go to the pack with the rest.
- **The map tiles** are BotW's `MapTex`, which objmap serves without granting a licence
  (`tools/map-tiles/README.md`).
- **The HW:DE squares** are the Helper's pictures of the NES-style Adventure Map. The Helper has no licence.
- **Theme colours** were sampled from Spriters Resource sheets that were never committed (`docs/themes.md`).
  Colours aren't protectable, so they're **safe**.
- **Theme ornaments** are drawn in code (`theme/Ornaments.kt`). Two depict the Triforce: `triforces()` (A Link
  to the Past, Hyrule Warriors, and A Link Between Worlds inverted) and `crest()` (Ocarina of Time).
  Both are **replace** in the store build. The rest are generic shapes (waves, vine, bricks, clouds, rails,
  rope, a clock arc), and BotW's "glyph line" is a line with diamonds, not a Sheikah eye.
- **The launcher icon** (`ic_launcher_foreground.xml`) is our own geometry, three gold triangles making a
  Triforce. It's **replace**.
- **UI icons** come from `material-icons-core` (Apache-2.0): **attribute** (a notice in the credits).
- **Repo-only images** are the owner's photos and screenshots of Nintendo screens, used as test fixtures, and
  the map alignment overlay. They're fine while the repo stays private, and must not be published with it.
- **Echoes of Wisdom** (*pending*): the map tiles come from Zelda Wiki's map, which is Nintendo's art, so
  **pack**. The map icons are **pack** unless they're drawn for the app.

## Scanner references

The live Korok scan, the photo shrine scan and the Zelda Notes import match the camera against references
taken from the game's screen.

| File | What it is | Class | Note |
|---|---|---|---|
| `files/botw/map/**.webp` | The map tiles, which `MapLocator` (`screencam/live/MapLocator.kt:20`) reads in grey to place the camera's view | **pack** | The live Korok scan **cannot work without them**. A store build without a BotW map pack must turn the live scan off and say why, without saying where art comes from |
| `files/botw/map_chrome.json`, `map_chrome_track.bin` | Grey thumbnails and histograms of the map screen's frame, made by `ScreenChromeReferenceTest` from the owner's photos | **derived** | Measurements, not a usable picture. Ships |
| `files/botw/korok_marker.json` | A 28×18 grey average of the found-Korok map marker, from the owner's clips | **derived** | Ships |
| `files/botw/shrine_map.json` | Shrine and tower positions, plus icon silhouettes the photo scan matches | **derived** | Ships. The positions are covered in [Data sources](#data-sources) |
| `files/botw/notes_korok.png` | A crop of the Switch App's Zelda Notes map, as a colour PNG | **pack** | It's a picture of Nintendo's UI. Convert it to a grey template like `korok_marker.json` so that the Zelda Notes import keeps working in the store build |

## Data sources

Every dataset is offline JSON. Nothing is fetched at runtime. Most of it is facts (names, places, counts,
prices, coordinates) in our own short wording, compiled by hand from several sources and checked between
them. Facts aren't copyrightable. Credit is still owed, and the store build needs a credits screen that names
the sources.

**Classes for data:**
- **facts**: names, places, counts and numbers in our own words. Credit only.
- **close text**: wording copied from someone else, whether verbatim or near it. Rewrite it, or quote it under
  its licence.
- **wholesale**: someone else's whole compilation, taken as a set. The risk depends on the source's terms.
  The EU database right can protect a compilation's selection even when each fact is free.

| Game | Datasets | Main sources | Content | Class | Action |
|---|---|---|---|---|---|
| BotW | `koroks` (900), `bosses` (109), `shrine_map` (136 + 15 towers) | objmap `korok_ids.json`, radar.zeldamods.org, objmap `game_files` | Datamined ids and coordinates, each actor's `korok_type` | **wholesale** | Positions extracted from the game are facts, and the set is the game's own, not zeldamods' selection. Keep it, and credit zeldamods (objmap, radar) in the app |
| BotW | `shrines`, `side_quests`, `main_quest`, `fairies`, `korok_puzzle_types` | Zelda Wiki, Zelda Fandom, Game8, Thonky | Facts; hints, tips and directions in our words | **facts** | Credit |
| BotW | `compendium` (394) | Zelda Wiki's Compendium numbering; Hyrule Compendium API (in-game location text) | Numbers, names, short in-game location names | **facts** | Credit |
| BotW, TotK | `armor` (107 / 135 pieces) | Zelda Wiki's armor tables, checked against Game8, Fandom and others | Defence and upgrade tables, complete | **wholesale** (of facts) | Numbers are the game's. Keep, and credit Zelda Wiki |
| TotK | `shrines` (152 + 120 Lightroots) | radar-totk.zeldamods.org, objmap-totk; Zelda Wiki | Datamined coordinates; `location` written for this app | **wholesale** | As for BotW: keep, and credit zeldamods |
| HW:DE | 10 Adventure Map JSONs (~1,100 squares) | Hyrule Warriors Helper `v2` (Fabian-F, **no licence**); checked against SBAllen's guide | The Helper's whole dataset. Card text verbatim from Zelda Wiki; Gold Skulltula conditions in SBAllen's wording, via the Helper | **wholesale** + **close text** | **Pack-only** (owner's decision): the map data moves to the pack as `hwde/data/*`, and the store build ships no HW:DE data |
| MC | `figurines` (136) | Zelda Wiki (the North American gallery text), zeldaret/tmc | `description` is **Nintendo's own text**, verbatim | **close text** | Rewrite all 136 in our own words, or drop the field from the store build |
| MC | `kinstones` (100) | Zelda Wiki, zeldaret/tmc; **Thonky's** numbering and shape names | Facts, arranged in Thonky's numbering | **facts** (Thonky's arrangement) | Credit Thonky, or renumber by the decomp's fusion id and use descriptive shape names |
| MC | `heart_pieces`, `main_quest` | Zelda Wiki, Zelda Dungeon, StrategyWiki, Zelda Universe, Zelda's Palace | Facts | **facts** | Credit |
| SS | 10 datasets, Thrill Digger, Lightning Round | Zelda Wiki, Zelda Dungeon, Samurai Gamers, ssrando, zeldaret/ss, lepelog's description | Facts. The Thrill Digger generator and RNG are our own code from lepelog's description of the game's behaviour; the decomp gave only symbol names and flag ids | **facts** | Credit. lepelog's solver has no licence, and none of its code or pictures is used |
| OoS, OoA | rings, secrets, items and the rest | Zelda Wiki, Zelda Dungeon, TourianTourist's GameFAQs ring guide, StrategyWiki | Facts; ring notes summarise TourianTourist's findings, with credit | **facts** | Credit (already inline) |
| LoZ | 3 datasets | Zelda Wiki, Zelda Dungeon, StrategyWiki | Facts; a few short Old Man quotes | **facts** | Short quotes are de minimis |
| AoL, ALttP, LA, OoT, MM, FS, FSA, WW, TP, PH, ST, ALBW, TFH, EoW | 3–11 datasets each | Zelda Wiki, Zelda Dungeon (Wayback), StrategyWiki, IGN, Zelda's Palace, decompilations, manuals (`docs/<game>.md`) | Facts in our own words (WW's methods "rewritten more plainly"; TFH challenge names in Zelda Wiki's wording); one FSA manual quote | **facts** | Credit |
| EoW (*pending*) | map marker positions | MrCheeze's eow-tools datamine (**no licence**) | Datamined coordinates | **wholesale** | As for objmap: keep, and credit MrCheeze |

**Short in-game text** (item, place and mission names, and the odd quoted hint or line) is the game's own.
In these amounts it's de minimis or nominative, so it stays.

### Upstream sources

| Source | Used for | Licence | Class |
|---|---|---|---|
| Zelda Wiki (zeldawiki.wiki) | Most datasets' facts; HW:DE card text; every bitmap icon | Text **CC BY-SA 4.0**; files are Nintendo's | Text: **attribute**. Files: **pack** |
| objmap (zeldamods/objmap) | BotW positions; shrine and tower icon tracings; map tiles | Code **GPL-3.0** (none copied; the projection is reimplemented arithmetic); map art: none granted | Positions: **data**. Icons and tiles: **pack** |
| radar.zeldamods.org, radar-totk, objmap-totk | BotW and TotK positions and actor parameters | Not stated | **data**, credit |
| MrCheeze/eow-tools (*pending*) | EoW marker positions | No licence file | **data**, credit |
| Hyrule Warriors Helper (Fabian-F) | HW:DE maps, rewards, searches, art | **None** | **pack** (data and art) |
| SBAllen's GameFAQs guide | HW:DE cross-check | © SBAllen, GameFAQs-only hosting | Checked against only. Its wording survives only in the Gold Skulltula conditions, which go to the pack |
| MetroWind's SBAllen extraction | Consulted while researching | WTFPL | Not bundled |
| lepelog/thrill-digger | Thrill Digger rules, as described | None | Rules only: **facts** |
| zeldaret decompilations (oot, tmc, tp, tww, ss), LADX disassembly, ssrando, wwrando, Hyrule Compendium API | Flags, counts, ids, tie-breakers | Varies; we use facts only | **facts** |
| Zelda Dungeon, StrategyWiki, Zelda Fandom, Zelda Universe, Zelda's Palace, Thonky, Game8, IGN, Samurai Gamers, Nintendo Life, RPG Site, PowerPyx and other guides | Cross-checks and facts | All rights reserved, or CC BY-SA (StrategyWiki, Fandom) | **facts**; credit the main ones |
| GameFAQs authors (TourianTourist, silktail, Anton299, DBM11085, ZFS), Prima guides, Nintendo manuals | Cross-checks; short quotes | All rights reserved | **facts**; credit where named in notes |

## Code and fonts

| Component | Licence | Class | Obligation |
|---|---|---|---|
| Theme fonts: Press Start 2P, Pixelify Sans, Fredoka, Josefin Sans, Cinzel | SIL OFL 1.1 | **attribute** | OFL §2: each copy must carry the copyright notice and licence. Today they're only in `docs/licenses/`, so the app must show them too |
| Kotlin, kotlinx (coroutines, serialization), Compose Multiplatform, AndroidX (activity, lifecycle, navigation, DataStore, CameraX), Material icons | Apache-2.0 | **attribute** | A notice in the credits |
| MapCompose (`ovh.plrapps:mapcompose-mp`) | Apache-2.0 | **attribute** | A notice in the credits |
| okio, kotlinx-io (transitive) | Apache-2.0 | **attribute** | A notice in the credits |
| TwelveMonkeys ImageIO WebP | BSD-3-Clause | **safe** | JVM tests only, not in the APK |
| Our own code | No LICENSE file yet | – | The owner is deciding |

## Trademarks and naming

"Zelda", "Hyrule", "Triforce", "Sheikah", "Korok" and the Triforce symbol are Nintendo's trademarks. Stores
reject or take down apps whose name, icon or developer branding uses another company's marks. Naming a game
in the description, to say what the app works with, is nominative use and generally accepted, as long as
nothing suggests Nintendo endorses the app.

| Item | Today | Store build |
|---|---|---|
| App name | "ZeldaGX" (`AndroidManifest.xml:12`, `HomeScreen.kt:51`, `AppDrawer.kt:125`) | **"Hero's Ledger GX"** (owner's choice). Run a trademark search (USPTO, EUIPO, and the store listings) before publishing |
| applicationId | `gx.zelda.zeldagx` | **`gx.herosledger`**. The personal build keeps `gx.zelda.zeldagx`, so the owner's install and data carry on |
| Launcher icon | A Triforce | An original mark, e.g. a ledger with a tick. No Triforce, crest or Sheikah eye |
| Theme ornaments | `triforces()`, `crest()` | Generic triangle and diamond motifs in the store build; the personal build keeps today's |
| Store title and subtitle | – | No Nintendo marks |
| Store description | – | Games named descriptively: "An unofficial completion tracker for The Legend of Zelda™ series." |
| Store screenshots | – | Taken with no art pack installed |
| In-app game, item and place names | Throughout | Descriptive use: they stay |

**Disclaimer.** It goes in the store listing and in Settings → About:

> Hero's Ledger GX is an unofficial fan-made tracker. It is not affiliated with, endorsed or sponsored by
> Nintendo. The Legend of Zelda and related names are trademarks of Nintendo.

**Links.**
- **objmap links:** the app's only external links today are the "Open in objmap" links on the Korok and
  boss detail screens (`KorokCatalog.kt:32`, `BossCatalog.kt:79`). objmap shows Nintendo's map art, so the
  store build drops them.
- **About texts:** the map About dialog (`WorldMapScreen.kt:258`) and the HW:DE About sheet
  (`AdventureMapScreen.kt:452`) name where the art came from. In the store build they say only that the art
  comes from the user's art pack.
- **Support link:** a generic "Support & bug reports" link, for example to a Discord or community page, is
  fine. Neither the app nor the channel it links to may point to sources of Nintendo art or art packs; that
  includes pinned posts and channel rules.
- **No instructions:** the app gives no instructions for getting art, in either build.

## Attribution in the app today

- **Map About dialog:** "Map art © Nintendo … via objmap.zeldamods.org", plus objmap's positions and icons.
- **HW:DE About sheet:** the Helper, SBAllen, Nintendo Life, RPG Site and Zelda Wiki. It has no CC BY-SA
  notice and no licence link.
- **Missing:** a credits screen, the OFL texts, Apache-2.0 notices, Zelda Wiki's CC BY-SA notice, and the
  disclaimer.
- **Where the full source lists are:** README's *Data sources* section and each `docs/<game>.md` list
  their sources in full. That's enough for the repo, but not for users of a store build.

## Action list

In priority order. P0 blocks a store release. The phase is the PR that does it (see
[The phases](#the-phases)).

| # | Priority | Action | Phase |
|---|---|---|---|
| 1 | P0 | Remove every **pack** file from the store build: all drawables, BotW map tiles, HW:DE art, `notes_korok.png` (converted to a template first) | 2, 5 |
| 2 | P0 | HW:DE is pack-only: its map JSON moves to the pack as `hwde/data/*`; the store build lists HW:DE as needing a pack | 2, 5 |
| 3 | P0 | Default art for every screen, so the store build works with no pack: generic markers, glyphs, map without tiles | 3 |
| 4 | P0 | Store name "Hero's Ledger GX", applicationId `gx.herosledger`, a new launcher icon, no Triforce ornaments, after a trademark search | 5 |
| 5 | P0 | Drop the objmap links and the art-source About text from the store build | 5 |
| 6 | P0 | Turn off the live Korok scan when no BotW map tiles are installed, with a message that names no source | 3 |
| 7 | P1 | Credits & licences screen: Zelda Wiki (CC BY-SA 4.0), zeldamods (objmap, radar), MrCheeze (eow-tools), Thonky, the main guide sites, the five OFL texts, Apache-2.0 notices, and the disclaimer | 6 (on hold) |
| 8 | P1 | Rewrite the 136 MC figurine descriptions in our own words, or leave them out of the store build | 6 (on hold) |
| 9 | P2 | Kinstones: credit Thonky's numbering, or renumber by the decomp's fusion id | 6 (on hold) |
| 10 | P2 | A LICENSE for our own code (owner deciding) | 6 (on hold) |
| 11 | P2 | Store listing text with the disclaimer, and a generic support link | 6 (on hold) |
| 12 | P3 | Keep the repo private while it holds test photos, pack-0 art and the map overlay. If it's ever opened, move those out | – |
| 13 | – | Classify Echoes of Wisdom's map tiles, icons and positions when that PR lands (`scripts/licence-inventory.py --check` fails until it does) | with that PR |

## The phases

Each phase is one PR. The gate is the local `:shared:jvmTest` and `:androidApp:assembleDebug`.

1. **This audit.**
2. **The art pack system.** It covers:
   - a versioned zip format and asset ids (`<game>/<kind>/<name>`);
   - a validating importer that stores packs in app-private storage;
   - the `GameArt` resolver behind every icon, map tile, square and boss picture;
   - Settings → Art packs, with per-game coverage.

   Today's art is reached as pack 0, so nothing changes visibly.
3. **Default art** drawn in code, game by game.
4. **The pack builder:** `tools/artpack/`, a desktop tool that reuses the fetch scripts. It produces the pack
   zip on the user's own machine.
5. **Flavours.** The **store** build (Hero's Ledger GX, `gx.herosledger`, safe files only) and the
   **personal** build (ZeldaGX, `gx.zelda.zeldagx`, which installs pack 0 itself). The art leaves the shared
   module, and a check stops it coming back.
6. **Text and attribution.** On hold for the owner's remaining decisions.
