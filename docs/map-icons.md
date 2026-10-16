# BotW map marker icons

The map's markers use Breath of the Wild's own map icons. `scripts/build-map-icons.py` writes them to
`shared/src/commonMain/composeResources/drawable/map_*`, and `MarkerIcon` (`map/model/MarkerLook.kt`) names
each one. `markerLook()` picks a marker's icon from its layer, state and DLC flag.

## Icons and sources

| File | Marker | Source |
| --- | --- | --- |
| `map_shrine.xml` | Shrine, found or undiscovered | objmap `public/icons/mapicon_dungeon.svg`, glyph recoloured orange |
| `map_shrine_done.xml` | Shrine, completed | objmap `mapicon_dungeon.svg`, all blue as objmap draws it |
| `map_shrine_dlc.xml` | Champions' Ballad shrine, not completed | objmap `mapicon_dungeon_dlc.svg`, glyph recoloured orange |
| `map_shrine_dlc_done.xml` | Champions' Ballad shrine, completed | objmap `mapicon_dungeon_dlc.svg` |
| `map_tower.xml` | Sheikah Tower, not activated | objmap `mapicon_tower.svg`, recoloured orange |
| `map_tower_active.xml` | Sheikah Tower, activated | objmap `mapicon_tower.svg` |
| `map_korok.png` | Korok seed, any state | Zelda Wiki `File:BotW Korok Seed Icon.png`, scaled from 144 to 64 px |

- **objmap icons:** from [github.com/zeldamods/objmap](https://github.com/zeldamods/objmap/tree/master/public/icons),
  fetched October 2026. They're vector tracings of the game's map icons. The script keeps objmap's path data,
  its 1066.4-unit viewBox, its blue (`#3DB2E5`, `#82C8E5` for towers) and its outline (`#003781`).
  - It leaves out objmap's CSS glow, which vector drawables can't draw.
  - It crops the viewport to the icon (600 units, 680 for the taller tower).
- **Korok icon:** the Korok Seed's inventory icon, downloaded through Zelda Wiki's API the same way
  `scripts/fetch-armor-icons.py` gets armor icons. objmap's own Korok marker (`mapicon_korok.png`) is a
  30×28 bitmap, too small to scale up.

## States

The game's map shows a shrine you've found but not finished with an orange glyph inside the blue frame. Once it's
completed, the whole icon is blue. The shrine scanner (`ShrineMapScanner`) reads shrine states from screenshots
by that same glyph colour. objmap's SVG is a single blue shape, so the script splits each shrine into two paths:

1. **Frame:** objmap's outer diamond minus the inner diamond, whose upper edges objmap's second subpath already
   traces. Always blue.
2. **Glyph:** objmap's head curve, closed through the inner diamond's bottom corner, minus the keyhole (objmap's
   first subpath). Orange or blue by state.

The frame is drawn first, so the DLC glyph's spire covers it where it rises through the top, as it does in
objmap's icon.

| Layer | Undiscovered | Found | Done |
| --- | --- | --- | --- |
| Shrines | orange-glyph icon, greyscale and faded | orange glyph | blue glyph |
| Koroks | seed in greyscale on a grey disc | seed on an orange disc | seed on a blue disc |
| Towers | (no such state) | orange tower, not activated | blue tower, activated |

- **Korok states:** the seed icon has no state variants, so a Korok shows its state on the disc behind it. The
  disc uses the same blue, orange and grey as the layer chips and Korok clusters.
- **Hidden markers:** a marker a tracker's *Hide undiscovered* setting hides (shown with *Show hidden
  places*) always uses the plain undiscovered look. It never shows the DLC spire or the entry's real state.
- **Size:** markers are full size from map scale 0.5 in (shrines 26 dp, towers 30 dp, Korok discs 20 dp). Zoomed
  further out they shrink, down to half size at scale 0.125 and below, the most zoomed-out tile level. At that
  point the whole map fits a phone screen and 136 full-size shrines would pile on top of each other.
  `iconScale()` rounds the size to 0.05 steps, so markers only recompose when the size changes a step.

## Rebuilding

```bash
python3 -m pip install Pillow          # only needed to fetch the Korok icon
python3 scripts/build-map-icons.py     # rewrite the vectors; fetch map_korok.png if it's missing
python3 scripts/build-map-icons.py --force   # re-download map_korok.png too
```

The script carries objmap's path data inline, so rewriting the vectors needs no network.
`MarkerIconsTest` fails if any `MarkerIcon` has no bundled drawable.

## Licence

The icon designs are Nintendo's.

- **objmap's tracings** are in its repository, which is GPL-3.0.
- **The Korok icon** is the game's own, as hosted by Zelda Wiki.

The map art has the same status (see `tools/map-tiles/README.md`): fine for a personal, sideloaded build, but
the icons would need replacing, or the app would need to meet the GPL's terms, before any store release.
