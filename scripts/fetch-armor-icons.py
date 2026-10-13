#!/usr/bin/env python3
"""
Download the inventory icon for every armor piece, in every game, from Zelda Wiki.

    scripts/fetch-armor-icons.py            fetch icons that aren't bundled yet
    scripts/fetch-armor-icons.py --force    re-download every icon

Each piece's icon is the wiki file "<game> <name> Icon.png" (e.g. "BotW Hylian Hood Icon.png").
It is saved as shared/src/commonMain/composeResources/drawable/armor_<id>.png, which the app
looks up by piece id. Icons are shared between games: a piece whose id is in an earlier game in
GAMES uses that game's icon, so TotK only fetches pieces BotW doesn't have. Exits non-zero if any
piece has no icon on the wiki.
"""

import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = ROOT / "shared/src/commonMain/composeResources/files"
# (data directory, wiki file prefix), in order of precedence for shared pieces.
GAMES = [("botw", "BotW"), ("totk", "TotK")]
OUT_DIR = ROOT / "shared/src/commonMain/composeResources/drawable"
API = "https://zeldawiki.wiki/w/api.php"
HEADERS = {"User-Agent": "ZeldaGX armor icon fetcher (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as r:
        return r.read()


def icon_urls(prefix: str, names: list[str]) -> dict[str, str | None]:
    """Wiki file title for each name → its download URL, or None when the file doesn't exist."""
    urls: dict[str, str | None] = {}
    for i in range(0, len(names), 50):
        titles = [f"File:{prefix} {n} Icon.png" for n in names[i:i + 50]]
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


def fetch_game(game: str, prefix: str, force: bool, claimed: set[str]) -> list[str]:
    """Fetches [game]'s icons that no earlier game supplies; returns the names with no wiki icon."""
    pieces = json.loads((FILES / game / "armor.json").read_text())["pieces"]
    own = [p for p in pieces if p["id"] not in claimed]
    claimed.update(p["id"] for p in pieces)
    shared = len(pieces) - len(own)

    todo = [p for p in own if force or not (OUT_DIR / f"armor_{p['id']}.png").exists()]
    if not todo:
        print(f"{prefix}: all {len(own)} icons already present ({shared} shared with an earlier game).")
        return []

    urls = icon_urls(prefix, [p["name"] for p in todo])
    missing = []
    for p in todo:
        url = urls.get(f"File:{prefix} {p['name']} Icon.png")
        if url is None:
            missing.append(f"{p['name']} ({prefix})")
            continue
        (OUT_DIR / f"armor_{p['id']}.png").write_bytes(get(url))
        print(f"  {p['id']}")

    print(f"{prefix}: fetched {len(todo) - len(missing)} of {len(todo)} icons ({shared} shared with an earlier game).")
    return missing


def main() -> int:
    force = "--force" in sys.argv[1:]
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    claimed: set[str] = set()
    missing = [name for game, prefix in GAMES for name in fetch_game(game, prefix, force, claimed)]
    if missing:
        print("No wiki icon for: " + ", ".join(missing), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
