# Map tiles

`build_tiles.py` builds the tile pyramid behind the BotW map screen. It is a one-off prep script,
and the app never fetches anything at runtime.

## What it does

1. **Downloads** the zoom-6 tiles of objmap.zeldamods.org's in-game map art that cover the playable area.
   - The playable area is x −5000…5000 and z −4000…4000 in world units, which comes to 1,320 tiles of 256 px.
   - Requests go over one kept-alive connection, at most 4 per second, with an identifying User-Agent.
   - Tiles are cached in `tools/map-tiles/.cache/`, which is gitignored, so a rerun only fetches what's missing.
2. **Stitches** the 10000×8000 px playable area. objmap's zoom 6 is exactly one pixel per world unit: its Leaflet
   transform puts world `(x, z)` at pixel `(2^z·(x/64 + 93.75), 2^z·(z/64 + 78.125))`.
3. **Builds four levels**, 1250, 2500, 5000 and 10000 px wide, with Lanczos downsampling.
   - Each level is cut into 256 px WebP tiles at quality 80.
   - Tiles go to `shared/src/commonMain/composeResources/files/botw/map/<level>/<row>/<col>.webp`, level 0 being the most zoomed out.
   - Edge tiles are padded with a sea colour that the app also uses as the map's background.
4. Writes `files/botw/map/tiles.json`, which records the sizes, world bounds, source and date.

## Rerunning

```bash
python3 -m pip install Pillow                         # needs WebP support (the default wheels have it)
python3 tools/map-tiles/build_tiles.py                 # fetch missing tiles, rebuild everything
python3 tools/map-tiles/build_tiles.py --offline       # rebuild from the cache only
python3 tools/map-tiles/build_tiles.py --debug-overlay /tmp/overlay.png
```

- **`--debug-overlay`** also writes level 1 with every shrine (cyan), tower (orange) and Korok (yellow) drawn
  from the bundled JSON. Use it to eyeball alignment after changing anything. `alignment-overlay.jpg` is what it
  looked like for the committed tiles.
- **`--quality`** changes the WebP quality.
- **`--rate`** changes the request limit.
- **After a rebuild**, run `./gradlew :shared:jvmTest`. `MapTilePyramidTest` checks that every tile is there,
  that each one is a 256 px WebP, and the size budget: 48 KB per tile and 15 MB in total.

## Licence

- **Map art:** Nintendo's, extracted from the game by the zeldamods project. objmap grants no licence for it.
  It's bundled here for a personal, sideloaded app only.
- **objmap's code:** GPL-3.0. None of it is copied. The projection above is reimplemented arithmetic.
