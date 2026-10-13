#!/usr/bin/env python3
"""
Download the BotW inventory icon for every armor piece from Zelda Wiki.

    scripts/fetch-armor-icons.py            fetch icons that aren't bundled yet
    scripts/fetch-armor-icons.py --force    re-download every icon

Each piece's icon is the wiki file "BotW <name> Icon.png" (144x144). It is saved as
shared/src/commonMain/composeResources/drawable/armor_<id>.png, which the app looks up
by piece id. Exits non-zero if any piece has no icon on the wiki.
"""

import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "shared/src/commonMain/composeResources/files/botw/armor.json"
OUT_DIR = ROOT / "shared/src/commonMain/composeResources/drawable"
API = "https://zeldawiki.wiki/w/api.php"
HEADERS = {"User-Agent": "ZeldaGX armor icon fetcher (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as r:
        return r.read()


def icon_urls(names: list[str]) -> dict[str, str | None]:
    """Wiki file title for each name → its download URL, or None when the file doesn't exist."""
    urls: dict[str, str | None] = {}
    for i in range(0, len(names), 50):
        titles = [f"File:BotW {n} Icon.png" for n in names[i:i + 50]]
        query = urllib.parse.urlencode({
            "action": "query", "titles": "|".join(titles),
            "prop": "imageinfo", "iiprop": "url", "format": "json",
        })
        result = json.loads(get(f"{API}?{query}"))["query"]
        # The API answers with normalized titles (e.g. "'" stays, "_" → " "); map them back.
        normalized = {n["to"]: n["from"] for n in result.get("normalized", [])}
        for page in result["pages"].values():
            title = normalized.get(page["title"], page["title"])
            info = page.get("imageinfo")
            urls[title] = info[0]["url"] if info else None
    return urls


def main() -> int:
    force = "--force" in sys.argv[1:]
    pieces = json.loads(CATALOG.read_text())["pieces"]
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    todo = [p for p in pieces if force or not (OUT_DIR / f"armor_{p['id']}.png").exists()]
    if not todo:
        print(f"All {len(pieces)} icons already present.")
        return 0

    urls = icon_urls([p["name"] for p in todo])
    missing = []
    for p in todo:
        url = urls.get(f"File:BotW {p['name']} Icon.png")
        if url is None:
            missing.append(p["name"])
            continue
        (OUT_DIR / f"armor_{p['id']}.png").write_bytes(get(url))
        print(f"  {p['id']}")

    print(f"Fetched {len(todo) - len(missing)} of {len(todo)} icons.")
    if missing:
        print("No wiki icon for: " + ", ".join(missing), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
