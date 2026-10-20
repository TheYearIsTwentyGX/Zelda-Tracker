#!/usr/bin/env python3
"""
Download the Thrill Digger solver's Rupee, Rupoor and Bomb pictures from Zelda Wiki.

    scripts/fetch-thrill-digger-icons.py            fetch the pictures that aren't bundled yet
    scripts/fetch-thrill-digger-icons.py --force    re-download every picture

Each is Skyward Sword's own render on Zelda Wiki ("SS Green Rupee Render.png" and so on), saved
as the wiki's own thumbnail (MediaWiki `iiurlwidth`), so nothing beyond Python is needed. They
land in shared/src/commonMain/composeResources/drawable/thrill_*.png. The Rupoor's purple minus
sign is drawn by the app over the picture (thrilldigger/ui/HoleIcon.kt), not baked in. See
docs/ss.md.
"""

import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "shared/src/commonMain/composeResources/drawable"
API = "https://zeldawiki.wiki/w/api.php"
HEADERS = {"User-Agent": "ZeldaGX Thrill Digger icon fetcher (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}

# Drawable name → (wiki file, thumbnail width). The Rupee renders are 640×1003 and the Bomb's
# 1052×1432, so these widths give pictures about 125 px tall: sharp at 40 dp on a 3× screen.
ICONS = {
    "thrill_green": ("File:SS Green Rupee Render.png", 80),
    "thrill_blue": ("File:SS Blue Rupee Render.png", 80),
    "thrill_red": ("File:SS Red Rupee Render.png", 80),
    "thrill_silver": ("File:SS Silver Rupee Render.png", 80),
    "thrill_gold": ("File:SS Gold Rupee Render.png", 80),
    "thrill_rupoor": ("File:SS Rupoor Render.png", 80),
    "thrill_bomb": ("File:SS Bomb Render.png", 92),
}


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as r:
        return r.read()


def fetch(title: str, width: int, out: Path) -> None:
    query = urllib.parse.urlencode({
        "action": "query", "titles": title, "prop": "imageinfo", "iiprop": "url",
        "iiurlwidth": width, "format": "json",
    })
    pages = json.loads(get(f"{API}?{query}"))["query"]["pages"].values()
    info = next(iter(pages)).get("imageinfo")
    if not info:
        raise SystemExit(f"{title} isn't on Zelda Wiki")
    data = get(info[0]["thumburl"])
    if not data.startswith(b"\x89PNG"):
        raise SystemExit(f"{title}: the thumbnail isn't a PNG")
    out.write_bytes(data)


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, (title, width) in ICONS.items():
        out = OUT_DIR / f"{name}.png"
        if "--force" in sys.argv[1:] or not out.exists():
            fetch(title, width, out)
            print(f"  {out.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
