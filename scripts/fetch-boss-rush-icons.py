#!/usr/bin/env python3
"""
Download the Lightning Round Records board's boss pictures from Zelda Wiki.

    scripts/fetch-boss-rush-icons.py            fetch the pictures that aren't bundled yet
    scripts/fetch-boss-rush-icons.py --force    re-download every picture

Zelda Wiki has no in-game icon for Skyward Sword's bosses (only The Imprisoned's map icons), so
these are its renders ("SS Koloktos Render.png" and so on), saved as the wiki's own thumbnail
(MediaWiki `iiurlwidth`/`iiurlheight`, fitted in a 128 px square), so nothing beyond Python is
needed. Ghirahim's first two fights share a render, as do The Imprisoned's three; the Horde
Battle uses the Bokoblin icon. They land in
shared/src/commonMain/composeResources/drawable/ss_boss_<icon>.png, where <icon> is each boss's
`icon` in files/ss/boss_rush.json. See docs/ss.md.
"""

import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "shared/src/commonMain/composeResources/drawable"
API = "https://zeldawiki.wiki/w/api.php"
HEADERS = {"User-Agent": "ZeldaGX boss icon fetcher (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}
BOX = 128

# A boss's `icon` (files/ss/boss_rush.json) → wiki file.
ICONS = {
    "ghirahim": "File:SS Ghirahim Render.png",
    "scaldera": "File:SS Scaldera Render.png",
    "moldarach": "File:SS Moldarach Render.png",
    "imprisoned": "File:SS The Imprisoned Render.png",
    "koloktos": "File:SS Koloktos Render.png",
    "tentalus": "File:SS Tentalus Render.png",
    "horde": "File:SSHD Bokoblin Icon.png",
    "ghirahim_3": "File:SS Ghirahim Render 3.png",
    "demise": "File:SS Demise Render.png",
}


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as r:
        return r.read()


def fetch(title: str, out: Path) -> None:
    query = urllib.parse.urlencode({
        "action": "query", "titles": title, "prop": "imageinfo", "iiprop": "url|size",
        "format": "json",
    })
    pages = json.loads(get(f"{API}?{query}"))["query"]["pages"].values()
    info = next(iter(pages)).get("imageinfo")
    if not info:
        raise SystemExit(f"{title} isn't on Zelda Wiki")
    w, h = info[0]["width"], info[0]["height"]
    if max(w, h) <= BOX:
        url = info[0]["url"]
    else:
        # The thumbnail width that fits the longer side in BOX.
        width = BOX if w >= h else max(1, round(w * BOX / h))
        query = urllib.parse.urlencode({
            "action": "query", "titles": title, "prop": "imageinfo", "iiprop": "url",
            "iiurlwidth": width, "format": "json",
        })
        url = next(iter(json.loads(get(f"{API}?{query}"))["query"]["pages"].values()))["imageinfo"][0]["thumburl"]
    data = get(url)
    if not data.startswith(b"\x89PNG"):
        raise SystemExit(f"{title}: the picture isn't a PNG")
    out.write_bytes(data)


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for icon, title in ICONS.items():
        out = OUT_DIR / f"ss_boss_{icon}.png"
        if "--force" in sys.argv[1:] or not out.exists():
            fetch(title, out)
            print(f"  {out.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
