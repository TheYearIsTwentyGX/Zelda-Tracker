#!/usr/bin/env python3
"""
Download Skyward Sword's inventory icons for the Item Upgrades tracker from Zelda Wiki.

    scripts/fetch-ss-upgrade-icons.py            fetch icons that aren't bundled yet
    scripts/fetch-ss-upgrade-icons.py --force    re-download every icon

Every level of every item in files/ss/upgrades.json, and every treasure and bug it uses, has the wiki
file "SS <name> Icon.png" (e.g. "SS Hook Beetle Icon.png", "SS Heart Potion+ Icon.png"), the game's
own inventory and Collection icon. They're saved unscaled as
shared/src/commonMain/composeResources/drawable/ss_up_<level id>.png and ss_mat_<material id>.png,
which the app looks up by id (upgrades/ui/UpgradeIcons.kt). Exits non-zero if any is missing.
See docs/ss.md.
"""

import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "shared/src/commonMain/composeResources/files/ss/upgrades.json"
OUT_DIR = ROOT / "shared/src/commonMain/composeResources/drawable"
API = "https://zeldawiki.wiki/w/api.php"
HEADERS = {"User-Agent": "ZeldaGX SS upgrade icon fetcher (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as r:
        return r.read()


def icon_urls(titles: list[str]) -> dict[str, str | None]:
    """Wiki file title → its download URL, or None when the file doesn't exist."""
    urls: dict[str, str | None] = {}
    for i in range(0, len(titles), 50):
        query = urllib.parse.urlencode({
            "action": "query", "titles": "|".join(titles[i:i + 50]),
            "prop": "imageinfo", "iiprop": "url", "format": "json",
        })
        result = json.loads(get(f"{API}?{query}"))["query"]
        # The API answers with normalized titles; map them back.
        normalized = {n["to"]: n["from"] for n in result.get("normalized", [])}
        for page in result["pages"].values():
            title = normalized.get(page["title"], page["title"])
            info = page.get("imageinfo")
            urls[title] = info[0]["url"] if info else None
    return urls


def main() -> int:
    force = "--force" in sys.argv[1:]
    data = json.loads(DATA.read_text())
    # Drawable name → wiki file.
    wanted = {f"ss_up_{level['id']}": f"File:SS {level['name']} Icon.png" for item in data["items"] for level in item["levels"]}
    wanted |= {f"ss_mat_{m['id']}": f"File:SS {m['name']} Icon.png" for m in data["materials"]}

    todo = {name: title for name, title in wanted.items() if force or not (OUT_DIR / f"{name}.png").exists()}
    if not todo:
        print(f"All {len(wanted)} icons already present.")
        return 0

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    urls = icon_urls(list(todo.values()))
    missing = []
    for name, title in todo.items():
        url = urls.get(title)
        if url is None:
            missing.append(title)
            continue
        png = get(url)
        if not png.startswith(b"\x89PNG"):
            missing.append(f"{title} (not a PNG)")
            continue
        (OUT_DIR / f"{name}.png").write_bytes(png)
        print(f"  {name}")

    print(f"Fetched {len(todo) - len(missing)} of {len(todo)} icons.")
    if missing:
        print("No wiki icon for: " + ", ".join(missing), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
