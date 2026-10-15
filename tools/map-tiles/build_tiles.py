#!/usr/bin/env python3
"""Builds the bundled BotW map tile pyramid from objmap's game-file tiles.

One-off prep script; the app never fetches tiles. It downloads the zoom-6 tiles that cover the
playable area (1 px per world unit) from objmap.zeldamods.org, politely and once: tiles are
cached in tools/map-tiles/.cache/ (gitignored), so a rerun only fetches what's missing. It then
stitches the 10000x8000 playable area, builds four levels (1250, 2500, 5000 and 10000 px wide)
and writes 256 px WebP tiles plus a tiles.json manifest into the app's compose resources.

    python3 tools/map-tiles/build_tiles.py                 # fetch missing tiles, rebuild
    python3 tools/map-tiles/build_tiles.py --offline       # rebuild from the cache only
    python3 tools/map-tiles/build_tiles.py --debug-overlay overlay.png

Needs Pillow with WebP support (pip install Pillow).
"""

import argparse
import datetime
import http.client
import io
import json
import math
import shutil
import sys
import time
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / ".cache" / "z6"
RES = ROOT / "shared/src/commonMain/composeResources/files/botw"
OUT = RES / "map"

TILE_HOST = "objmap.zeldamods.org"
TILE_PATH = "/game_files/maptex/6/{col}/{row}.png"
USER_AGENT = "ZeldaGX-map-tiles/1.0 (one-off fetch for a personal offline tracker app)"
SOURCE_ZOOM = 6
SOURCE_TILE = 256

# The playable area in world units (x east, z south); docs/korok-puzzle-types.md.
MIN_X, MAX_X, MIN_Z, MAX_Z = -5000, 5000, -4000, 4000
# objmap's Leaflet transform at zoom z: px = 2^z * (x/64 + 93.75), py = 2^z * (z/64 + 78.125).
# At z6 that is px = x + 6000, py = z + 5000.
ORIGIN_PX = 2 ** SOURCE_ZOOM * (MIN_X / 64 + 93.75)
ORIGIN_PY = 2 ** SOURCE_ZOOM * (MIN_Z / 64 + 78.125)
FULL_W = MAX_X - MIN_X
FULL_H = MAX_Z - MIN_Z

TILE = 256
LEVELS = 4
QUALITY = 80
PAD = (40, 60, 70)  # sea colour for the unused part of edge tiles; the app's MapBackground matches it


def source_tiles():
    c0, c1 = int(ORIGIN_PX // SOURCE_TILE), int((ORIGIN_PX + FULL_W - 1) // SOURCE_TILE)
    r0, r1 = int(ORIGIN_PY // SOURCE_TILE), int((ORIGIN_PY + FULL_H - 1) // SOURCE_TILE)
    return [(col, row) for row in range(r0, r1 + 1) for col in range(c0, c1 + 1)]


def fetch(tiles, offline, rate):
    CACHE.mkdir(parents=True, exist_ok=True)
    missing = [t for t in tiles if not (CACHE / f"{t[0]}_{t[1]}.png").exists()]
    print(f"{len(tiles)} source tiles, {len(missing)} not cached")
    if missing and offline:
        sys.exit("--offline but tiles are missing from the cache")
    # One kept-alive connection, so the whole run costs objmap a single TLS handshake.
    conn = None
    for i, (col, row) in enumerate(missing):
        path = TILE_PATH.format(col=col, row=row)
        started = time.monotonic()
        for attempt in range(3):
            try:
                conn = conn or http.client.HTTPSConnection(TILE_HOST, timeout=30)
                conn.request("GET", path, headers={"User-Agent": USER_AGENT})
                resp = conn.getresponse()
                data = resp.read()
                if resp.status == 404:
                    data = None
                elif resp.status != 200:
                    raise http.client.HTTPException(f"HTTP {resp.status}")
                break
            except (OSError, http.client.HTTPException):
                if conn:
                    conn.close()
                conn = None
                if attempt == 2:
                    raise
                time.sleep(5 * (attempt + 1))
        if data is not None:
            (CACHE / f"{col}_{row}.png").write_bytes(data)
        else:
            print(f"  404: https://{TILE_HOST}{path}")
        if (i + 1) % 100 == 0:
            print(f"  {i + 1}/{len(missing)}", flush=True)
        time.sleep(max(0.0, 1 / rate - (time.monotonic() - started)))
    if conn:
        conn.close()


def stitch(tiles):
    full = Image.new("RGB", (FULL_W, FULL_H), PAD)
    for col, row in tiles:
        path = CACHE / f"{col}_{row}.png"
        if not path.exists():
            continue
        tile = Image.open(path).convert("RGB")
        full.paste(tile, (int(col * SOURCE_TILE - ORIGIN_PX), int(row * SOURCE_TILE - ORIGIN_PY)))
    return full


def build_levels(full):
    levels = []
    for level in range(LEVELS):
        div = 2 ** (LEVELS - 1 - level)
        w, h = math.ceil(FULL_W / div), math.ceil(FULL_H / div)
        img = full if div == 1 else full.resize((w, h), Image.LANCZOS)
        levels.append(img)
    return levels


def write_tiles(levels, quality):
    if OUT.exists():
        shutil.rmtree(OUT)
    meta, total = [], 0
    for level, img in enumerate(levels):
        cols, rows = math.ceil(img.width / TILE), math.ceil(img.height / TILE)
        for row in range(rows):
            for col in range(cols):
                box = (col * TILE, row * TILE, min((col + 1) * TILE, img.width), min((row + 1) * TILE, img.height))
                crop = img.crop(box)
                tile = Image.new("RGB", (TILE, TILE), PAD)
                tile.paste(crop, (0, 0))
                buf = io.BytesIO()
                tile.save(buf, "WEBP", quality=quality, method=6)
                path = OUT / str(level) / str(row) / f"{col}.webp"
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(buf.getvalue())
                total += len(buf.getvalue())
        meta.append({"level": level, "width": img.width, "height": img.height, "cols": cols, "rows": rows})
        print(f"level {level}: {img.width}x{img.height}, {cols}x{rows} tiles")
    manifest = {
        "_comment": "Generated by tools/map-tiles/build_tiles.py. Map art (c) Nintendo, tiles via objmap.zeldamods.org.",
        "tileSize": TILE,
        "format": "webp",
        "fullWidth": FULL_W,
        "fullHeight": FULL_H,
        "world": {"minX": MIN_X, "maxX": MAX_X, "minZ": MIN_Z, "maxZ": MAX_Z},
        "levels": meta,
        "source": f"https://{TILE_HOST}" + TILE_PATH.replace("{col}/{row}", "{x}/{y}"),
        "generated": datetime.date.today().isoformat(),
    }
    (OUT / "tiles.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"{sum(m['cols'] * m['rows'] for m in meta)} tiles, {total / 1e6:.2f} MB")


def debug_overlay(levels, path):
    img = levels[1].copy()
    scale = img.width / FULL_W
    draw = ImageDraw.Draw(img)

    def dot(x, z, r, fill):
        px, py = (x - MIN_X) * scale, (z - MIN_Z) * scale
        draw.ellipse((px - r, py - r, px + r, py + r), fill=fill, outline=(0, 0, 0))

    koroks = json.loads((RES / "koroks.json").read_text())["koroks"]
    for k in koroks:
        dot(k["x"], k["z"], 2, (255, 230, 0))
    shrine_map = json.loads((RES / "shrine_map.json").read_text())
    for x, z in shrine_map["shrines"].values():
        dot(x, z, 5, (0, 200, 255))
    for x, z in shrine_map["towers"].values():
        dot(x, z, 7, (255, 80, 0))
    img.save(path)
    print(f"debug overlay: {path}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--offline", action="store_true", help="use only cached tiles")
    ap.add_argument("--rate", type=float, default=4, help="max requests per second (default 4)")
    ap.add_argument("--quality", type=int, default=QUALITY, help="WebP quality (default 80)")
    ap.add_argument("--debug-overlay", type=Path, help="also write level 1 with shrines, towers and Koroks drawn on")
    args = ap.parse_args()

    tiles = source_tiles()
    fetch(tiles, args.offline, args.rate)
    levels = build_levels(stitch(tiles))
    write_tiles(levels, args.quality)
    if args.debug_overlay:
        debug_overlay(levels, args.debug_overlay)


if __name__ == "__main__":
    main()
