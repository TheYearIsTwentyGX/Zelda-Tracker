#!/usr/bin/env python3
"""
Classify every file the app bundles, and every image kept in the repo, for the licence audit.

    scripts/licence-inventory.py            print the inventory table
    scripts/licence-inventory.py --write    rewrite the table in docs/licensing-audit.md
    scripts/licence-inventory.py --check    exit non-zero if a file matches no rule, or the doc's table is stale

Each file is matched against RULES in order; the first rule whose glob matches wins. A new asset
needs a rule (or must fit an existing one) before --check passes, so nothing joins the app
unclassified. docs/licensing-audit.md explains the classes and the sources behind each rule.
"""

import fnmatch
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs" / "licensing-audit.md"
START, END = "<!-- inventory:start -->", "<!-- inventory:end -->"

RES = "shared/src/commonMain/composeResources"
# (directory, whether its files ship in the app, file suffixes to include; None = all)
SCAN = [
    (RES, True, None),
    ("androidApp/src/main/res", True, None),
    ("shared/src/jvmTest/resources", False, (".jpg", ".jpeg", ".png", ".webp", ".gif")),
    ("tools", False, (".jpg", ".jpeg", ".png", ".webp", ".gif")),
]

# Classes:
#   safe    ours, or openly licensed and fine to ship as is
#   pack    Nintendo's or unlicensed third-party art: leaves the store build for the art pack
#   derived low-resolution measurements of the game's screen that the scanners match against
#   data    facts compiled from the sources in the audit's data table
#   risky   a third party's compilation copied wholesale (see the audit's action list)
#   repo    kept in the private repo for tests or tooling, never shipped
#   replace ours, but depicts a Nintendo trademark
RULES = [
    # --- drawables
    ("BotW map icons (shrine, tower)", f"{RES}/drawable/map_shrine*.xml", "objmap `public/icons/*.svg`, split by `scripts/build-map-icons.py`", "Nintendo design; objmap tracing", "GPL-3.0 (tracing)", "pack"),
    ("BotW map icons (shrine, tower)", f"{RES}/drawable/map_tower*.xml", "objmap `public/icons/*.svg`, split by `scripts/build-map-icons.py`", "Nintendo design; objmap tracing", "GPL-3.0 (tracing)", "pack"),
    ("BotW map icons (Korok, bosses)", f"{RES}/drawable/map_*.png", "Zelda Wiki file uploads (`scripts/build-map-icons.py`)", "Nintendo", "none granted", "pack"),
    ("Armor icons (BotW, TotK)", f"{RES}/drawable/armor_*.png", "Zelda Wiki file uploads (`scripts/fetch-armor-icons.py`)", "Nintendo", "none granted", "pack"),
    ("HW:DE item card icons", f"{RES}/drawable/hwde_card_instrument.png", "Hyrule Warriors Helper `public/item-cards/` (Zelda Wiki has no file)", "Fabian-F, after Nintendo/Koei Tecmo", "none (no licence)", "pack"),
    ("HW:DE item card icons", f"{RES}/drawable/hwde_card_*.png", "Zelda Wiki file uploads (`tools/hwde/build_maps.py`)", "Nintendo/Koei Tecmo", "none granted", "pack"),
    ("HW:DE reward icons", f"{RES}/drawable/hwde_reward_*.png", "Zelda Wiki file uploads (`tools/hwde/build_maps.py`)", "Nintendo/Koei Tecmo", "none granted", "pack"),
    ("SS Lightning Round boss pictures", f"{RES}/drawable/ss_boss_*.png", "Zelda Wiki renders (`scripts/fetch-boss-rush-icons.py`)", "Nintendo", "none granted", "pack"),
    ("SS Thrill Digger pictures", f"{RES}/drawable/thrill_*.png", "Zelda Wiki renders (`scripts/fetch-thrill-digger-icons.py`)", "Nintendo", "none granted", "pack"),
    ("SS item upgrade icons", f"{RES}/drawable/ss_up_*.png", "Zelda Wiki file uploads (`scripts/fetch-ss-upgrade-icons.py`)", "Nintendo", "none granted", "pack"),
    ("SS material icons", f"{RES}/drawable/ss_mat_*.png", "Zelda Wiki file uploads (`scripts/fetch-ss-upgrade-icons.py`)", "Nintendo", "none granted", "pack"),
    # --- map art
    ("BotW map tiles", f"{RES}/files/botw/map/*.webp", "objmap.zeldamods.org MapTex tiles (`tools/map-tiles/build_tiles.py`)", "Nintendo", "none granted", "pack"),
    ("BotW map tile manifest", f"{RES}/files/botw/map/tiles.json", "`tools/map-tiles/build_tiles.py`", "ours", "-", "safe"),
    ("HW:DE Adventure Map squares and previews", f"{RES}/files/hwde/*.webp", "Hyrule Warriors Helper `public/map-tiles/`, `public/map-previews/`", "Fabian-F, after Nintendo/Koei Tecmo layouts", "none (no licence)", "pack"),
    # --- scanner references
    ("Zelda Notes droplet template", f"{RES}/files/botw/notes_korok.png", "crop of the Switch App's Zelda Notes map, from the owner's phone", "Nintendo", "none granted", "pack"),
    ("Map-screen chrome references (live scan)", f"{RES}/files/botw/map_chrome*", "grey thumbnails of the owner's photos of the map screen (`ScreenChromeReferenceTest`)", "derived from Nintendo's screen", "-", "derived"),
    ("Korok marker template (live scan)", f"{RES}/files/botw/korok_marker.json", "28×18 grey average of the found-Korok marker from the owner's clips", "derived from Nintendo's screen", "-", "derived"),
    ("BotW shrine map (positions, icon masks)", f"{RES}/files/botw/shrine_map.json", "objmap and radar positions; icon silhouettes measured from screenshots", "datamined; derived", "-", "derived"),
    # --- data
    ("HW:DE Adventure Map data", f"{RES}/files/hwde/*.json", "Hyrule Warriors Helper `v2` data (`tools/hwde/build_maps.py`)", "Fabian-F; card text Zelda Wiki", "none (no licence); CC BY-SA text", "risky"),
    ("BotW Korok positions", f"{RES}/files/botw/koroks.json", "objmap `korok_ids.json` and radar.zeldamods.org", "datamined from the game", "not stated", "risky"),
    ("BotW overworld boss positions", f"{RES}/files/botw/bosses.json", "radar.zeldamods.org (`scripts/build-botw-bosses.py`)", "datamined from the game", "not stated", "risky"),
    ("TotK shrine and Lightroot positions", f"{RES}/files/totk/shrines.json", "radar-totk.zeldamods.org, objmap-totk", "datamined from the game", "not stated", "risky"),
    ("MC figurine gallery", f"{RES}/files/mc/figurines.json", "Zelda Wiki (game text); zeldaret/tmc (`scripts/build-mc-figurines.py`)", "Nintendo text", "none granted", "risky"),
    ("Tracker data", f"{RES}/files/*/*.json", "compiled from the sources in the data table", "facts; our wording", "-", "data"),
    # --- fonts
    ("Theme fonts", f"{RES}/font/*.ttf", "Google Fonts (`docs/fonts.md`)", "their authors", "SIL OFL 1.1", "safe"),
    # --- launcher icon
    ("Launcher icon (Triforce)", "androidApp/src/main/res/drawable/ic_launcher_foreground.xml", "drawn for this app", "ours; depicts the Triforce", "Nintendo trademark", "replace"),
    ("Launcher icon background and wrappers", "androidApp/src/main/res/*", "drawn for this app", "ours", "-", "safe"),
    # --- repo only
    ("Test photos of Nintendo screens", "shared/src/jvmTest/resources/*", "the owner's photos, videos and Switch App screenshots", "Nintendo screens", "-", "repo"),
    ("Map alignment overlay", "tools/map-tiles/alignment-overlay.jpg", "`build_tiles.py --debug-overlay`", "Nintendo map art", "none granted", "repo"),
]


def files():
    for rel, ships, suffixes in SCAN:
        base = ROOT / rel
        for p in sorted(base.rglob("*")):
            if p.is_file() and (suffixes is None or p.suffix.lower() in suffixes):
                yield p.relative_to(ROOT).as_posix(), ships, p.stat().st_size


def classify():
    groups, unmatched = {}, []
    for path, ships, size in files():
        rule = next((r for r in RULES if fnmatch.fnmatchcase(path, r[1])), None)
        if rule is None:
            unmatched.append(path)
            continue
        name, _, source, owner, licence, cls = rule
        g = groups.setdefault(name, {"source": source, "owner": owner, "licence": licence, "cls": cls,
                                     "ships": ships, "count": 0, "size": 0})
        g["count"] += 1
        g["size"] += size
    return groups, unmatched


def human(n):
    for unit in ("B", "KB", "MB"):
        if n < 1024 or unit == "MB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024


def table(groups):
    rows = ["| Group | Files | Size | Source | Owner | Licence | Class |", "|---|---:|---:|---|---|---|---|"]
    order = [r[0] for r in RULES]
    for ships in (True, False):
        for name, g in sorted(groups.items(), key=lambda kv: order.index(kv[0])):
            if g["ships"] == ships:
                rows.append(f"| {name} | {g['count']} | {human(g['size'])} | {g['source']} | {g['owner']} | {g['licence']} | **{g['cls']}** |")
    totals = {}
    for g in groups.values():
        if g["ships"]:
            t = totals.setdefault(g["cls"], [0, 0])
            t[0] += g["count"]
            t[1] += g["size"]
    summary = ", ".join(f"{cls} {n} files ({human(s)})" for cls, (n, s) in sorted(totals.items()))
    return "\n".join(rows) + f"\n\nShipped today, by class: {summary}.\n"


def block(text):
    return f"{START}\n\n{text}\n{END}"


def main():
    groups, unmatched = classify()
    text = table(groups)
    if "--write" in sys.argv:
        doc = DOC.read_text()
        a, b = doc.index(START), doc.index(END) + len(END)
        DOC.write_text(doc[:a] + block(text) + doc[b:])
    elif "--check" in sys.argv:
        ok = True
        if unmatched:
            ok = False
            print(f"{len(unmatched)} file(s) match no rule in scripts/licence-inventory.py:", file=sys.stderr)
            for p in unmatched[:20]:
                print(f"  {p}", file=sys.stderr)
        if block(text) not in DOC.read_text():
            ok = False
            print("docs/licensing-audit.md's inventory is stale: run scripts/licence-inventory.py --write", file=sys.stderr)
        sys.exit(0 if ok else 1)
    else:
        print(text)
        for p in unmatched:
            print(f"UNCLASSIFIED {p}", file=sys.stderr)


if __name__ == "__main__":
    main()
