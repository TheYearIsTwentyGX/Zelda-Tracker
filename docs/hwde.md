# Hyrule Warriors: Definitive Edition

HW:DE (Switch, 2018) is the app's first spin-off. It's listed after the mainline games under a
**Spin-offs** heading (`Game.spinOff`), and dated by DE's own first release (Japan, 22 March 2018)
rather than Hyrule Warriors' 2014 one, because what's tracked, its Adventure Mode maps, is DE's:
the Wii U and 3DS releases lay out their maps and rewards differently.

Each Adventure Mode map is a tracker of its own (`TrackerKind.ADVENTURE_MAP`), drawn as the map
with its squares to tap. This page covers the data, where it comes from, how the art lines up and
what isn't known.

## Maps

DE has every map from both earlier games, all unlocked from the start:

| Map | Squares | In the app |
|---|---:|---|
| Adventure Map | 128 | Yes (phase 1) |
| Great Sea Map | 98 | Yes (phase 2a) |
| Master Quest Map | 128 | Yes (phase 2b) |
| Master Wind Waker Map | 98 | Yes (phase 2a) |
| Twilight Map | 93 | Planned |
| Termina Map | 85 | Planned |
| Koholint Island Map | 84 | Planned |
| Grand Travels Map | 128 | Planned |
| Lorule Map | 128 | Planned |
| Rewards Map | 13 | Yes (phase 2b) |

Sources: Zelda Wiki's *Adventure Mode* page (its gallery and per-map sections), the Koei Wiki and
the contents of SBAllen's guide. Square counts are the Helper's, and match MetroWind's extraction
of SBAllen's guide for the nine main maps.

## Save files

One save per Switch user. The game has no file select. Its save is a single file that save
managers swap whole (GBAtemp's 100% save thread), and on the 3DS a button combination deletes the
only save. The tracker has one file, **Save File** (`file_1`), as Tri Force Heroes does. Storage
is keyed by file, so a second one would only be a line in `Games.kt`.

## The data

`files/hwde/<map>.json`, read as `adventure/model/AdventureMap.kt` (its KDoc has every field). In
short:

- **Squares**:
  - id `r<row>c<col>`;
  - Prima's label ("A-6") and SBAllen's ("F-1");
  - difficulty colour, battle type and mission, any map rule, and which warriors the battle
    allows;
  - the A-rank limits (KOs, minutes, damage) and the wavy walls (`blockades`);
  - rewards, item cards given on a clear, Gold Skulltulas, searches and quiz answers;
  - a `note` where the sources disagree.
- **Rewards** have a slot: *"A" Rank Victory*, *Battle Victory* or *Treasure*. A treasure names its
  keep, and whether it's in a pot or jar. Each reward also has a type (Heart Container, Piece of
  Heart, weapon and level, costume, warrior, material, fairy food, companion fairy or fairy
  clothing) and the warrior it belongs to.
- **Searches** use one card on one cell of the search screen: `row` 0–10 and `col` 0–15 over the
  square's picture (the original game's 16×11 tiles). A whole-square search
  (`"wholeSquare": true`) has no cell.
- **Cards**: each card's in-game name, what it does on this map (`effects`: reveals a reward,
  hidden enemies, opens a path, lifts a rule, or the Compass), Zelda Wiki's description and its
  Item Card Shop price.
- **Every id is a storage key.** Ids are built from the content (`r1c16.hc_linkle`,
  `r1c16.weapon_boots_1`, `r1c1.gs2`, `r1c1.search_bomb`), never from a position.
  `tools/hwde/ids/<map>.txt` keeps every id ever written: the converter won't drop one without
  `--allow-removed`, and `HwdeAssetsTest` fails if the JSON loses one.

`AdventureMap.problems()` checks a file:
- ids and grid positions;
- regions, warriors and cards that exist;
- search cells in range;
- walls that match from both sides;
- the start and final boss;
- and that every card used in a search is given by some square.

`HwdeDatasetTest` runs it on every map, and pins the Adventure Map's counts and the independent
squares below.

### Rows and columns

Guides label squares two ways, and some mix them:

- **Prima's official guide, Zelda Wiki, Nintendo Life and RPG Site**: a row letter A–H from the top,
  then the column 1–16. The start is **H-8** and the Dark Ruler **A-6**.
- **SBAllen's guide and the Helper**: the column letter A–P, then the row 1–8. The same squares
  are **H-8** and **F-1**.

The game draws neither on its map. The app labels squares Prima's way, and each sheet also gives
SBAllen's. Ids are `r<row>c<col>`, so neither convention is baked into storage.

## Sources

The converter, `tools/hwde/build_maps.py`, reads these at pinned versions (its README has the
commands).

1. **[Hyrule Warriors Helper](https://github.com/Fabian-F/HyruleWarriorsHelper)** by Fabian-F, `v2`
   branch at `bdc126b`. This is the main source: Switch-only data with typed rewards, search
   cells, walls, warrior restrictions and quiz answers. It credits SBAllen's guide and
   community corrections (its issues #3–#31). The converter reads its TypeScript data with a
   tokenizer and never runs its code.
2. **SBAllen's Unlockable Guide** (GameFAQs FAQ 73095, v2.57, Switch pages), as the Wayback Machine
   archived it on 14 May 2019. It's used to cross-check every square, for the start square, and
   for its platform markers (it splits 3DS and Switch values). None of its text is in the app.
3. **[Zelda Wiki](https://zeldawiki.wiki/wiki/Item_Card)**, the *Item Card* table: card names, the
   maps each is on, what each does there and its shop price. The item-card and reward icons are
   its HWDE files.
4. **Independent checks**:
   - [Nintendo Life's DE weapon list](https://www.nintendolife.com/guides/guide-hyrule-warriors-definitive-edition-all-weapons-and-how-to-unlock-them)
     gives each weapon's square;
   - [RPG Site's DE character guide](https://www.rpgsite.net/feature/7191-hyrule-warriors-definitive-edition-how-to-unlock-every-character)
     gives each warrior's.

Zelda Wiki's per-square pages are stubs, Zelda Dungeon's Adventure Mode guide covers the Wii U
layout, and Game8 doesn't cover HW:DE, so none of those are used.

### Cross-check: Adventure Map

The Helper against SBAllen, after the corrections. `build_maps.py --check` prints this report.

| Check | Agree |
|---|---|
| Mission text | 128 / 128 |
| A-rank KOs, time and damage | 128 / 128 each |
| Searches (cards per square) | 126 / 128 (SBAllen's 3DS-only searches excluded) |
| Every reward, by slot | 128 / 128 after corrections |
| Nintendo Life weapon squares | 8 / 9 |
| RPG Site warrior squares | 4 / 4 |

Fixed in `tools/hwde/corrections.json`, each with its reason:
- **A-16** (SBAllen P-1): Toon Link's Piece of Heart at North Oasis is 3DS-only in SBAllen's guide.
  The Helper has it on the Switch too, so it's removed; Linkle's is the Switch one.
- **A-16**: Fairy Headwear - Royal Hood (N. Settlement) is listed by SBAllen for both versions and
  missing from the Helper, so it's added.
- **A-12** (SBAllen L-1): clearing it gives a Recorder card, in SBAllen's square entry and in his
  card table. It's missing from the Helper, so it's added.

Accepted, and noted on the square in the app:
- **B-1** (SBAllen A-2): SBAllen lists a Bomb search on the top wall. The Helper removed it as a
  "wrong search location" (commit `ae3da64`). The Helper's DE-only data is kept.
- **G-7**: the Helper has a Bomb search on the top wall; SBAllen lists none. The Helper's is kept.
- **Great Fairy Lv. 1**: Nintendo Life puts it at G-16. SBAllen (both versions) and the Helper put
  it at H-1 (SBAllen A-8), and nothing at G-16 gives it, so H-1 stands.

Counts after corrections:
- 111 Heart Containers and 58 Pieces of Heart;
- 24 Gold Skulltulas;
- 10 weapons, 6 costumes and 4 warriors (Twili Midna, Young Link, Tingle and Skull Kid);
- 29 fairy clothes, 11 fairy foods and 6 companion fairies;
- 94 searches, using 12 cards.

### Cross-check: Great Sea Map

The Great Sea is 14×7 squares; the start is B-9 and the Dark Ruler C-9. Fourteen squares carry a
**Great Sea Rule**, which the right card lifts: the Boomerang beats a Big Octo, the Cannon a
warship, the Hero's Bow a cyclone. Seven squares take the Wind Waker on the whole square, to stop
headwinds that lock a neighbour.

| Check | Agree |
|---|---|
| Mission text and rules | 98 / 98 |
| A-rank KOs, time and damage | 97 / 98 (D-4 below) |
| Searches | 96 / 98 |
| Every reward, by slot | 97 / 98 after corrections (C-11 below) |
| Nintendo Life weapon squares | 26 / 27 |
| RPG Site warrior squares | 4 / 4 |

Fixed:
- **B-8** (SBAllen H-2) and **C-13** (SBAllen M-3): SBAllen's guide gives a Salvage Arm card on a
  clear at each. The Helper lists only the warrior unlocked there; its issue #12 had reported B-8's
  card missing too. Both are added.

Accepted, noted on the square:
- **C-11** (SBAllen K-3): the Helper has Tetra's unlock on a clear and a Sea Map search; SBAllen's
  guide has neither. RPG Site also puts Tetra here, so the Helper's data is kept.
- **D-4**: SBAllen's guide gives no KO limit ("N/A") for this Adventure Battle. The Helper's 1,200
  is what every other yellow, purple and orange Adventure Battle on the map needs, so it's kept.
- **E-3** (SBAllen C-5): SBAllen's guide lists a Deku Leaf search on a windmill. The Helper has none
  and draws no windmill. The Master Wind Waker Map's E-3, the same island, does have one in both,
  so the guide's line may have been copied across.
- **Great Fairy Lv. 2**: Nintendo Life puts it at "H-1", which isn't a square on a seven-row map.
  SBAllen's guide and the Helper put it at C-1. Nintendo Life writes five other squares on this map
  SBAllen's way, column first (the Mirror's "A-4" is Prima's D-1), and those are converted.

### Cross-check: Master Wind Waker Map

The same islands as the Great Sea, with different battles. Its start is G-3, beside the Dark Ruler
at G-4, which the Hookshot opens. Every square has an **Endless Night Rule** (85) or a Great Sea
Rule (13). Its only weapons are Medli's Rito Harp, levels 2 to 4.

| Check | Agree |
|---|---|
| Mission text and rules | 98 / 98 |
| A-rank KOs, time and damage | 98 / 98 each |
| Searches | 95 / 98 |
| Every reward, by slot | 98 / 98 |
| Nintendo Life weapon squares | 3 / 3 |

Accepted, noted on the square:
- **C-1 and D-1** (SBAllen A-3 and A-4): the Helper has the Wind Waker search on C-1, and SBAllen's
  guide has it on D-1, the square below. The Helper's is kept.
- **F-1** (SBAllen A-6): SBAllen's guide lists a Hyoi Pear search on a seagull. The Helper has none
  and draws no seagull.

On both maps:
- **SBAllen's "Engless Night Rule"** typo, which the Helper copied on three squares, is corrected to
  "Endless".
- **The Sea Map card** is called that because Zelda Wiki's HWDE pages call it so ("A Sea Map Item
  Card"). The guides call it the Sea Chart.
- **The Master Wind Waker Map's Power Bracelet** gets the Great Sea's card id (`power_bracelet_2`):
  it lifts Stone Heads to reveal enemies, as on the Great Sea, not the Adventure Map's boulders.

### Cross-check: Master Quest Map

The original game's Second Quest map, 16×8, from H-8 to the Dark Ruler at A-1. Every square plays
under a **Master Quest Rule** (no healing, no guarding, no items…). Its 35 weapons are each
warrior's level 3.

| Check | Agree |
|---|---|
| Mission text and rules | 128 / 128 |
| A-rank KOs, time and damage | 128 / 128 each |
| Searches | 127 / 128 after corrections |
| Every reward, by slot | 127 / 128 (H-1 below) |
| Nintendo Life weapon squares | 35 / 35 |

Fixed:
- **F-16 and G-16** (SBAllen P-6 and P-7): the Helper lists the same Ladder cell twice on each.
  SBAllen's guide has one Ladder search per square, so the copy is removed.

Accepted:
- **A-7** (SBAllen G-1), noted on the square: the Helper has the Goddess's Harp on butterflies two
  cells right of the lone rock; SBAllen's guide uses the Recorder on that cell. The Helper's art
  shows butterflies there.
- **H-1** (SBAllen A-8): the Helper gives a Compass card on a clear, added from its issue #12 (a
  Switch player's report); SBAllen's guide lists none.
- SBAllen calls the Recorder the "Whistle" on this map.

### The Rewards Map

Its 13 squares sit in a diamond in the middle of the game's 16×8 grid (SBAllen's I-1, H-2, J-2 …
I-8; Prima's A-9 … H-9). Each opens when its Gold Skulltula illustration is complete, so the map
has no start, no searches and no Skulltulas of its own. In spoiler-safe mode every square counts as
reached.

Neither the Helper nor anyone else has art for it, so its JSON has no `art`. The app draws each
square as a plain tile with its illustration's name. SBAllen's guide lists the squares by that name
too, which is how the report matches them.

Fixed:
- **F-7, "Rulers of Twilight"**: an A rank gives Ganondorf's Standard Armor (Boss), per SBAllen's
  guide and Zelda Wiki's Costume page. The Helper had a material.

Accepted:
- SBAllen's "Rules of Twilight" typo.
- Boss Rush's KO limit, which SBAllen leaves blank. His notes say the map follows the 1,000 KOs /
  99% rules, as the Helper has.
- **"Evil Power"**: Cia's three Boss costumes (Masked, Unmasked, Hatless) come from one A rank, and
  are kept as one tick.
- **The Ganondorf costume's name**: SBAllen calls it "Standard Outfit (Boss)", Zelda Wiki
  "Standard Armor (Boss)". The wiki's is used.

## A-rank limits

The game never shows them. These are SBAllen's, and he notes they're his findings. On the Switch,
damage is scored as a percentage of the warrior's health rather than the 3DS's raw number.

On the Adventure Map the limits follow the square's colour:

| Squares | Adventure Battle KOs | Damage |
|---|---:|---:|
| Black and green | 1,000 | 99% |
| Yellow, purple and orange | 1,200 | 149% |
| Blue and red | 1,600 | 199% (one red square: 149%) |

Battles have 15 minutes, or 7 for some Challenge Battles, and quiz battles need 3 KOs. The sheet
says the limits are estimates.

## The map's art

Each square is a 256×176 lossless WebP (`files/hwde/<map>/<square id>.webp`). Each map also has a
1280×440 lossy preview (`preview.webp`), drawn when zoomed out. The full-size squares are decoded
only while they're on screen, keeping the last 48.

- **What they are**: the Helper's pictures of the in-game map. Its credits call them self-made,
  modelled on the game's resources, Metroid_Lover's GameFAQs maps and Spriters Resource rips.
  - The layout matches the game square for square, search features included: the soft-dirt X,
    butterflies, rings of fire, and caves once bombed.
  - The colours are the NES palette, not DE's shaded HD art. The user confirmed that's fine.
  - The pictures show each square as it looks once searched, with bombed walls open. So in
    spoiler-safe mode the art can still give a Bomb spot away; the app hides only its own markers.
- **How it lines up** (checked with a prototype against Zelda Wiki's DE screenshot,
  `File:HWDE Adventure Map.png`, 1280×720):
  - The 128 squares stitched together make 4096×1408: the NES overworld at one pixel per pixel,
    each square 16×11 tiles of 16 px.
  - In the DE screenshot, the map's grid is the 1024×352 rectangle at (96, 214), exactly a quarter
    of that size. Every square boundary falls on a wavy in-game border.
- **Swapping the art** needs no code: point `art.squares` or `art.preview` in the map's JSON at
  other pictures. For captures of DE's own map:
  - from a 1280×720 Switch screenshot of the map, crop (96, 214)–(1120, 566) for the preview, or
    cut it into a 16×8 grid of 64×44 squares;
  - at another resolution, scale those numbers by width ÷ 1280;
  - a square's picture can be any size with the 16:11 shape, but `squareWidth` and `squareHeight`
    must say what it is. The search grid is always 16×11 cells over it.

  `HwdeAssetsTest` checks every square has a lossless picture of the size the JSON gives. Captures
  carry the game's own icons and Link's sprite, so pick the display with the fewest.

## Edges and squares you can challenge

The map draws the line between each pair of neighbouring squares as the game does:
- **Wavy (a wall):** clearing one square doesn't open the other. It's a grey zigzag with a light
  outline and small dark posts at its ends, about eight zigzags along a square's long side and six
  along its short one.
- **Straight:** a thin light seam; clearing either square opens the other.
- **Opened:** a wall that a path search done on either square (Hookshot, Raft, Ooccoo…) has
  opened. It's drawn straight. The data doesn't say which neighbour such a search opens, so all
  of that square's walls open.

There are no one-way walls. A wall recorded on either square counts for both, and the converter
writes each wall on both squares.

The style is worked out by `AdventureReach.edge` (`adventure/model/AdventureReach.kt`), and tested
for each kind. The look was checked against Zelda Wiki's DE screenshot (the 4× crop under *The
map's art*). Lines are drawn at screen resolution on the pixel-snapped square edges, so they stay
sharp at any zoom. Their colours are fixed, the game's, rather than the theme's.

**Squares you can challenge now** are drawn at full brightness; the rest are dimmed, always (they
can still be opened). They are:
- the start;
- every square already cleared;
- and each neighbour of a cleared square across an edge that isn't a wall.

The Rewards Map has no start, so all its squares count. The same set is what spoiler-safe mode
calls reached; that mode also hides the details of squares outside it.

The *Challengeable now* chip filters to them. With *Not cleared* on, they're listed first. The
overview's next step prefers them too.

**A simplification:** in the game a neighbour opens only if the clear earned the rank on the arrow
between the two squares (C, B or A). No source lists those arrows (Zelda Wiki's per-square pages
have them for only a few squares), so here any clear opens its neighbours. The map's *About* note
says so.

## Landscape

On the Map tab, in a landscape window shorter than 480 dp (Material's compact height: a phone on
its side, or a short split-screen window), the app hides the top bar, the Map/List tabs and the
system bars, which a swipe from the edge brings back for a moment.
- The filter row becomes the toolbar: ☰ at its start, then Cards, List and ⋮ at its end, plus the
  save-file picker on a game with more than one file. Every target is at least 48 dp, and nothing
  covers the map's row and column labels.
- *List* switches tab, which brings the bars back. So do portrait and leaving the screen.
- Turning the window between portrait and landscape opens the map afresh for the new shape, using
  all the height it now has. A resize within the same shape keeps the zoom.

`AdventureChrome.visible` decides from the window's own size, not the device's orientation, and is
tested for orientation and tab.

## Spoiler-safe mode

Off by default. It's one switch for every HW:DE map, in each map's ⋮ menu.

- A square this save file hasn't reached shows as **Unexplored**: dimmed, with nothing about it,
  until revealed for the session. Reached means the start, every cleared square, and each
  neighbour of a cleared square across an open side. A side is open if there's no wall, or if the
  cleared square's path search (Hookshot, Raft…) is done. Which neighbour such a search opens
  isn't known, so all of them count.
- On a reached square, search spots, quiz answers and Gold Skulltula locations stay hidden until
  *Use the Compass*. That mirrors the Compass card, which shows a square's secrets in the game.
  Searches already done always show.
- Filters and the list's search never match on what's hidden, except an Unexplored square's colour.
  The overview's next step only names reached squares.

## Item cards

A card counts as found once a square that gives it is cleared, since DE's Item Card Shop then
sells it. The Cards panel lets you set any card by hand, and set it back to automatic. *Searchable
now* filters to squares with an undone search whose card you have.

## Not known, so not tracked

- **Which reward a search reveals.** The game marks such rewards "Search Required", but no source
  records which reward goes with which search. The sheet gives the card's effect ("Reveals a hidden
  reward", "Reveals hidden enemies", "Opens a way…") rather than guess.
- **The rank arrow on each side**: the C/B/A needed to open a neighbour. No source lists them; only
  the walls are known.
- **The Great Sea's hazards** are shown as the square's rule. Using the card that lifts one counts
  as that square's search; the rule itself isn't tracked as lifted.
- **Termina's moon.** When it falls it resets the map, but not the rewards. The tracker records
  what's been done at least once and doesn't reset.

## Licence

This is a personal, sideloaded app; everything below is used on that basis only.

- **Hyrule Warriors** and its art are Nintendo's and Koei Tecmo's.
- **The Hyrule Warriors Helper** has no licence, so its data and pictures are bundled here only,
  with credit. A public or store release would need Fabian-F's permission or other art and data.
- **SBAllen's guide** is copyrighted and may only be hosted by GameFAQs. It's used to check the
  data, and none of its text is copied in. The Gold Skulltula conditions and places are short
  facts carried over from the Helper, which took them from the guide.
- **MetroWind's extraction** (WTFPL, attribute SBAllen) was only consulted while researching.
- **Zelda Wiki's text** (the card descriptions) is CC BY-SA; its icons are Nintendo's.
