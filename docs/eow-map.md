# Echoes of Wisdom map

The EoW **Map** tracker shows three checklists on the map of Hyrule:
- **Hearts:** 40 Pieces of Heart and 7 Heart Containers, from `heart_pieces.json`;
- **Stamps:** the 25 Stamp Stands, from `stamp_stands.json`;
- **Might Crystals:** 85 places holding 150 crystals, from `might_crystals.json`.

It's BotW's map engine (`map/`), drawn from a per-game `MapSpec`. Each checklist is a `ChecklistMapLayer` over the
checklist's own storage, so a marker *is* its row:
- **Ticking.** Ticking either one ticks both, `sameAs` links included. Mark-all in the list shows on the map at once.
- **Story gating.** With *Hide what the story hasn't unlocked yet* on, the map leaves out what the list leaves out.
  The chip keeps the true total and adds "· 3 later".
- **Spoilers.** The list's *Hide unchecked* is the layer's spoiler setting:
  - places with nothing ticked are left off;
  - with *Show hidden places* they're unnamed grey markers.
- **Off the map.** The stamp-card rewards and Lueburry's upgrades aren't places, so their sections aren't on the map.
- **Show on map.** An item page in a mapped section has it. The list's top bar has a map button.
- **Open in tracker.** A marker's sheet opens the item's page.

## Map art

`tools/map-tiles/build_eow_tiles.py` builds `files/eow/map/` from Zelda Wiki's
[`File:EoW Map of Hyrule.png`](https://zeldawiki.wiki/wiki/File:EoW_Map_of_Hyrule.png):
- **The image:** the explored in-game map, 3600×2442, stitched by the wiki's Squall.
- **Background:** its torn edges are transparent, so they're laid on dark parchment (`#261A10`). The map's
  background is the same colour.
- **Tiles:** four levels, 450 to 3600 px wide, as 256 px WebP. 206 tiles, about 0.8 MB.
  `EowMapTilePyramidTest` holds it to 3 MB.

Zelda Dungeon's interactive map uses the same art, at twice the size (7200 px). Registering the two images puts
its art at half size 576 px above this one, with a mean difference of 1.5/255, so it adds no detail. Still World
areas need no art of their own: the game pins whatever is in a cave, rift or dungeon at its entrance on the
overworld, and so does every map below.

## Positions

`files/eow/map/markers.json` gives each item's place in game world units (x east, z south). Its pin key is the
game's own: `HeartPiece 0`–`39`, `Piece000`–`149` or `Stamp_00`–`24`. The positions come from the game's
data, datamined by [MrCheeze/eow-tools](https://github.com/MrCheeze/eow-tools), pinned at commit `e6a309a4`:
- **Pieces of Heart:** `ui/UiMapIconItem.json`, row `HeartPiece`. The pin's index is the heart piece's id.
- **Might Crystals:** row `WorldGlobePiece`, joined by index to `PieceOfPowers.json`. The 85 used rows hold
  49×1, 21×2, 8×3 and 7×5 crystals, 150 in all, which is exactly our items' `value`s.
- **Stamp Stands:** the 25 `StampTable` actors in level `Field.json`.
- **Heart Containers** have no pin: they're in the dungeons. Each one reuses its dungeon's ×5 crystal pin. The
  boss gives both, and the pin marks the dungeon.

The same numbers are on Zelda Dungeon's interactive map, which the user suggested as the source:
- **Its storage:** it keeps `lng = 1.67·x − 554` and `lat = −1.67·z + 399` on a 1272-unit Leaflet CRS
  (`Transformation(450/1272, 225, −450/1272, 225)`, 7200 px at full zoom).
- **The check:** `build_markers.py --cross-check` finds a Zelda Dungeon marker for every pin. Hearts and crystals
  are within 1 unit, and four stamps are off by up to 1.8.
- **Why it isn't the primary source:** its data is behind Cloudflare (it was read from a Wayback Machine snapshot
  of 2026-06-02), its markers have no names, and only 31 of their descriptions are archived.

zeldamaps.com's and IGN's maps agree too.

**World to pixels.** On the 3600 px art, `px = 4.72642·x + 232.08` and `py = 4.72642·z + 94.75`. These come from
Zelda Dungeon's transform and the image registration above. `tiles.json` records them as world bounds
(x −49.10 to 712.57, z −20.05 to 496.62), as BotW's does, and `MapProjectionTest` pins them.

## Matching pins to our items

The game's ids aren't ours, so `tools/eow-map/matches.tsv` matches each item to a pin by hand. It has one row per
item: key, file, item, confidence, evidence, and an optional `at`, the pin a Heart Container reuses.
`tools/eow-map/build_markers.py` looks the positions up and writes `markers.json`. It refuses to write a table that
misses an item, uses a pin twice, or gives a crystal place a count other than its item's `value`.

The evidence, roughly in order of weight:
- **The crystal count** is a hard constraint.
- **What hides a crystal.** The object at the pin in `Field.json` (a rock, a Holmill dig spot, grass, ice, a sand
  mound) matches the item's category.
- **Quest crystals.** The pin's unlock condition (`Gliding_Valcano070`, `GetHorse_Forest010`) names the side quest.
- **Placement level.** A pin placed in a cave or rift level matches a cave or "Stilled …" area.
- **Text.** IGN names all 40 heart pins ("Heart Piece 25 (Kakariko - Slumber Dojo)"). 22 pins have an archived
  Zelda Dungeon description, and Zelda Wiki's Might Crystal table was a further check.
- **Place.** Zelda Dungeon's place labels and landmarks (shops, the dojo, houses, caves, dungeons).

Every region was then checked on overlay crops (`build_eow_tiles.py --debug-overlay`). 144 matches are high
confidence and 13 medium; nothing is unplaced.

The 13 medium rows are the doubtful pairs. They're listed in `docs/eow.md` (*Map*) with the game's other
disagreements.

## Icons

The game's own white map icons for a Piece of Heart, a Stamp and a Might Crystal, and the Heart Container's item
icon, all from Zelda Wiki (`docs/map-icons.md`). They sit on the state disc that BotW's Koroks use: orange until
ticked, blue after, grey when hidden. A ×2, ×3 or ×5 badge counts a place's crystals.

## Rebuilding

```bash
python3 tools/eow-map/build_markers.py             # download the eow-tools tables once, check matches.tsv, write markers.json
python3 tools/eow-map/build_markers.py --offline --cross-check
python3 tools/map-tiles/build_eow_tiles.py         # needs Pillow; --debug-overlay <png> draws every marker with its id
```

Then run `./gradlew :shared:jvmTest`: `EowMapDatasetTest` checks that every mapped item has one place, and spot-checks
a few places.

## Licence

- **The map art and icons** are Nintendo's, from Zelda Wiki, whose file pages mark them *Copyright*. They're bundled
  for a personal, sideloaded build only, as BotW's are.
- **The positions** are game data. eow-tools has no licence file, and neither Zelda Dungeon, zeldamaps nor IGN grants
  reuse; only the derived positions are bundled.

Both belong in a user-imported art pack before any store release (`docs/licensing-audit.md`).
