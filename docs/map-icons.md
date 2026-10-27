# Map marker icons

The BotW map's markers use Breath of the Wild's own map icons, and EoW's use Echoes of Wisdom's (see *Echoes of
Wisdom* below). `scripts/build-map-icons.py` writes them to
`shared/src/commonMain/composeResources/drawable/map_*`, and `MarkerIcon` (`map/model/MarkerLook.kt`) names
each one. `markerLook()` picks a marker's icon from its layer, state and DLC flag, or for a boss from its kind.

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
| `map_boss_hinox.png` | Hinox, Blue/Black Hinox or Stalnox, any state | Zelda Wiki `File:BotW Medal of Honor： Hinox Icon.png`, 64 px |
| `map_boss_talus.png` | Any Talus, Igneo Talus Titan included | Zelda Wiki `File:BotW Medal of Honor： Talus Icon.png`, 64 px |
| `map_boss_molduga.png` | Molduga or Molduking | Zelda Wiki `File:BotW Medal of Honor： Molduga Icon.png`, 64 px |
| `map_boss_lynel.png` | Any Lynel | Zelda Wiki `File:BotW Lynel Mask Icon.png`, 64 px |

- **objmap icons:** from [github.com/zeldamods/objmap](https://github.com/zeldamods/objmap/tree/master/public/icons),
  fetched October 2026. They're vector tracings of the game's map icons. The script keeps objmap's path data,
  its 1066.4-unit viewBox, its blue (`#3DB2E5`, `#82C8E5` for towers) and its outline (`#003781`).
  - It leaves out objmap's CSS glow, which vector drawables can't draw.
  - It crops the viewport to the icon (600 units, 680 for the taller tower).
- **Korok icon:** the Korok Seed's inventory icon, downloaded through Zelda Wiki's API the same way
  `scripts/fetch-armor-icons.py` gets armor icons. objmap's own Korok marker (`mapicon_korok.png`) is a
  30×28 bitmap, too small to scale up.
- **Boss icons:** neither the game's map nor objmap has an icon for a Hinox, Talus, Molduga or Lynel (objmap draws
  enemies as dots). The closest game art for each kind is the inventory icon of the Medal of Honor Kilton gives for
  it:
  - Hinox has a red ribbon, Talus teal and Molduga green, each with that boss on the medal.
  - There's no Lynel medal, so Lynels use the Lynel Mask, the game's icon of a Lynel's head.
  - One icon covers a whole kind; the marker's sheet names the variant.
- **Bitmap sizes:** the bitmap icons are the wiki's own 64 px thumbnails of its 144 px files (MediaWiki
  `iiurlwidth`), so the script needs nothing beyond Python. `map_korok.png` was scaled locally with Pillow before
  this; it's only re-fetched with `--force`.

## Echoes of Wisdom

| File | Marker | Source |
| --- | --- | --- |
| `map_eow_heart_piece.png` | Piece of Heart | Zelda Wiki `File:EoW Piece of Heart Map Icon.png`, 64 px |
| `map_eow_heart_container.png` | Heart Container | Zelda Wiki `File:EoW Heart Container Icon.png`, scaled from 134 to 64 px |
| `map_eow_stamp.png` | Stamp Stand | Zelda Wiki `File:EoW Stamp Map Icon.png`, 64 px |
| `map_eow_might_crystal.png` | Might Crystal | Zelda Wiki `File:EoW Might Crystal Map Icon.png`, 64 px |

- **Map icons:** the game's own map icons are white glyphs, 64 px files on the wiki, fetched as they are.
- **Heart Container:** the game's map has no icon for one, since containers are inside dungeons, so its item icon
  is used.
- **On the marker:** each glyph is drawn at 72 % of the marker on the Korok's state disc: orange until ticked,
  blue after, grey when a spoiler setting hides it.
  - A hidden Heart Container shows the Piece of Heart glyph, so it doesn't give itself away.
  - A Might Crystal place holding several crystals has a ×2, ×3 or ×5 badge.
- **Markers:** `MarkerKind.CHECKLIST`. `markerLook()` takes the entry's icon or the layer's.

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
| Bosses | kind's icon in greyscale on a grey disc | icon on an orange disc | icon on a blue disc (defeated) |

- **Korok and boss states:** the seed and boss icons have no state variants, so they show their state on the disc
  behind them. The disc uses the same blue, orange and grey as the layer chips and Korok clusters.
- **Hidden markers:** a marker a tracker's *Hide undiscovered* setting hides (shown with *Show hidden
  places*) always uses the plain undiscovered look. It never shows the DLC spire or the entry's real state. A hidden
  boss keeps its kind's icon, since the boss tracker's hidden rows name the kind ("Unfound Talus").
- **Size:** markers are full size from map scale 0.5 in (shrines 26 dp, towers 30 dp, Korok discs 20 dp, boss
  discs 24 dp). Zoomed
  further out they shrink, down to half size at scale 0.125 and below, the most zoomed-out tile level. At that
  point the whole map fits a phone screen and 136 full-size shrines would pile on top of each other.
  `iconScale()` rounds the size to 0.05 steps, so markers only recompose when the size changes a step. Bosses stop
  shrinking at three quarters (`layerIconScale()`). There are only about a hundred, spread out, and below 18 dp the
  medals' ribbons, which tell the kinds apart, run together.

## Rebuilding

```bash
python3 scripts/build-map-icons.py           # rewrite the vectors; fetch any missing bitmap icon (BotW and EoW)
python3 scripts/build-map-icons.py --force   # re-download the bitmap icons too
```

The script carries objmap's path data inline, so rewriting the vectors needs no network.
`MarkerIconsTest` fails if any `MarkerIcon` has no bundled drawable.

## Licence

The icon designs are Nintendo's.

- **objmap's tracings** are in its repository, which is GPL-3.0.
- **The Korok and boss icons**, and all of EoW's, are the games' own, as hosted by Zelda Wiki.

The map art has the same status (see `tools/map-tiles/README.md`): fine for a personal, sideloaded build, but
the icons would need replacing before any store release. Meeting the GPL wouldn't be enough, since the designs are
Nintendo's. `docs/licensing-audit.md` classifies them as art-pack assets.
