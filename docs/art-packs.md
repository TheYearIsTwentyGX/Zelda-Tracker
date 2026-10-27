# Art packs

An art pack is a zip of pictures that the app draws in place of its own: icons, map tiles and
Adventure Map squares. Users import packs in **Settings → Art packs**. That way a store build can
ship without Nintendo's art (`docs/licensing-audit.md`) and still look like the game for anyone
who supplies the art themselves. The app never says where a pack comes from, and neither may
anything it links to.

## How the app finds art

`GameArt` (`art/GameArt.kt`) is the one lookup. Every picture has an asset id, and for each id:

1. **Installed packs:** the newest import wins where two cover the same id.
2. **The art bundled with the app** (pack 0), while `GameArt.bundled` is on. Today that's all of
   it. A store build will turn it off once phase 5 lands.
3. **Nothing:** the screen draws its own stand-in. Today that's an empty space or a coloured disc;
   phase 3 adds drawn defaults.

Screens use it through:
- **`ArtImage(id, …)`, a composable:** armor, SS upgrades and materials, Lightning Round bosses,
  Thrill Digger, HW:DE cards and rewards, and the BotW map markers.
- **`GameArt.tile(id, level, row, col, bundledPath)`:** every map's `TilePyramid.provider` (BotW, EoW)
  and the live Korok scan (`BundledTiles`).
- **`GameArt.square(id, square, bundledPath)`:** the HW:DE squares and previews (`SquareImages`).

**Storage and performance:**
- **Where packs live:** in app-private storage (`files/artpacks/`), unpacked, one folder per pack,
  with `index.json` listing them newest first. They're left out of Android backups
  (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`).
- **Loading:** `GameArt.index` is a `StateFlow`, so an import or a removal shows at once. At start-up
  the store is read in the background, and map tiles wait for it, so a map never caches a tile as
  missing.
- **Pack pictures:** these are decoded off the main thread and kept in a cache of the 256 most
  recently used.
- **Tiles:** MapCompose keeps its own tile cache, and each tile is one file read.

## Asset ids

`<game>/<kind>/<name>`, using lowercase letters, digits and underscores; game ids are as in
`core/Games.kt`. Ids are stable: a pack built today must keep working. `ArtCatalog`
(`art/ArtCatalog.kt`) lists every id the app draws, game by game. Coverage in Settings
("Breath of the Wild: 119/119") is measured against it.

| Kind | Ids | What | Was bundled as |
|---|---|---|---|
| `marker` | `botw/marker/{shrine,shrine_done,shrine_dlc,shrine_dlc_done,tower,tower_active,korok,boss_hinox,boss_talus,boss_molduga,boss_lynel}` | BotW map markers | `drawable/map_*` |
| `marker` | `eow/marker/{heart_piece,heart_container,stamp,might_crystal}` | EoW map markers | `drawable/map_eow_*` |
| `armor` | `botw/armor/<piece id>`, `totk/armor/<piece id>` | Armor icons, per game | `drawable/armor_*` (shared) |
| `upgrade` | `ss/upgrade/<level id>` | SS item upgrade icons | `drawable/ss_up_*` |
| `material` | `ss/material/<material id>` | SS treasures and bugs | `drawable/ss_mat_*` |
| `boss` | `ss/boss/<icon>` | SS Lightning Round bosses | `drawable/ss_boss_*` |
| `thrill` | `ss/thrill/{green,blue,red,silver,gold,rupoor,bomb}` | Thrill Digger | `drawable/thrill_*` |
| `card` | `hwde/card/<card id>` | HW:DE item cards | `drawable/hwde_card_*` |
| `reward` | `hwde/reward/{heart_container,heart_piece,skulltula}` | HW:DE rewards | `drawable/hwde_reward_*` |
| `map` | `botw/map/world` | BotW's world map: a tileset | `files/botw/map/` |
| `map` | `eow/map/hyrule` | EoW's map of Hyrule: a tileset | `files/eow/map/` |
| `map` | `hwde/map/<map id>` | An Adventure Map's squares: a squareset | `files/hwde/<map id>/` |
| `data` | `hwde/data/<map id>` | An Adventure Map's JSON (reserved for HW:DE moving into packs) | `files/hwde/<map id>.json` |

`ArtIds.BUNDLED_PREFIXES` maps each icon kind to its old drawable prefix. The pack builder
(phase 4) uses the same table.

## The zip

`manifest.json` sits at the top level of the zip, and files can be anywhere inside it. The manifest
names every file the app should use; anything else in the zip is skipped.

```json
{
  "format": 1,
  "pack": { "id": "my-pack", "name": "My pack", "version": "2026.10", "created": "2026-10-27" },
  "icons": {
    "botw/marker/shrine": "botw/marker/shrine.xml",
    "botw/armor/hylian_hood": "armor/hylian_hood.png",
    "totk/armor/hylian_hood": "armor/hylian_hood.png"
  },
  "tilesets": {
    "botw/map/world": { "path": "botw/map/{level}/{row}/{col}.webp", "levels": 4, "tileSize": 256, "width": 10000, "height": 8000 }
  },
  "squaresets": {
    "hwde/map/adventure_map": { "path": "hwde/adventure_map/{id}.webp", "squares": ["r1c1", "r1c2"],
                                "preview": "hwde/adventure_map/preview.webp", "width": 256, "height": 176 }
  },
  "data": { "hwde/data/adventure_map": "hwde/adventure_map.json" }
}
```

- **`format`** is 1. A pack with a higher number is refused ("made for a newer version").
- **Two ids can share a file**, as BotW and TotK armor do above.
- **Packs can be partial:** one game, one kind, even one icon. Whatever a pack leaves out keeps the
  next source's art.

## Rules

`ArtPackStore.import` (`art/ArtPackStore.kt`) checks everything before it keeps anything. Files go
into a temporary folder, which is renamed into place only when the import completes.

**The whole pack is refused when:**
- the file isn't a zip;
- it has more than 20,000 entries;
- `manifest.json` is missing, unreadable or over 2 MB;
- `format` is newer than the app's;
- it unpacks to more than 300 MB;
- nothing in it can be used.

**An entry is ignored** (and counted) if its id isn't well formed, names a game the app doesn't
know, or is a kind the app doesn't draw.

**An asset is rejected** (and listed, with the reason) when any of its files breaks a rule. A
tileset or squareset is all or nothing: one bad tile drops the whole set. The rules:

| | Formats | Size |
|---|---|---|
| Icon | PNG or WebP, or an Android vector drawable (`.xml`) with no `@` resource references and no DTD | At most 512 KB and 1024 px a side |
| Tile | PNG, WebP or JPEG | Exactly `tileSize` square, at most 128 KB. The tileset's geometry must equal the app's map exactly (levels, tile size, width, height), since marker positions depend on it, and every tile must be present |
| Square | PNG, WebP or JPEG | Exactly `width`×`height` (at most 1024 a side), at most 128 KB. Square ids look like `r1c1` |
| Preview | PNG, WebP or JPEG | At most 4096 px a side and 2 MB |
| Data | JSON that parses | At most 4 MB |

**Paths and pictures:**
- Paths must be relative, with `/` between plain segments (letters, digits, `.`, `_`, `-`) and no `.`
  or `..`. Nothing can be written outside the pack's folder.
- Pictures are checked by their headers (format and size), not decoded. A file that passes but then
  won't decode is drawn as missing.

## Tests

- **`ArtPackStoreTest`** builds zips and covers every rule above: resolving and priority, removal,
  unsafe paths, unknown ids, the newer format, size limits, tileset geometry and all-or-nothing
  sets, squares and data.
- **`ArtCatalogTest`** checks that the bundled art covers every catalogue id and that every bundled
  drawable has an id. It also checks that the world tileset's geometry matches `tiles.json`, and that
  `ImageHeader` reads the real files.
