"""Build data/coords.json: world (x, z) of every shrine and tower, joined to the
app's shrines.json by name.

Source: objmap.zeldamods.org's static marker summary (the same file the map
front-end loads), plus its StaticMsg name tables. No scraping.

    python fetch_coords.py path/to/shrines.json
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

BASE = "https://objmap.zeldamods.org/game_files"
OUT = Path(__file__).parent / "data" / "coords.json"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "zeldagx-shrine-spike/0.1"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def norm(name):
    name = re.sub(r"\s+Shrine$", "", name)
    return re.sub(r"[^a-z]", "", name.lower())


def main(shrines_path):
    shrines = json.load(open(shrines_path))["shrines"]
    summary = get(f"{BASE}/map_summary/MainField/static.json")["markers"]
    # DLC shrine names (Dungeon120+) live only in the Dungeon table.
    names = get(f"{BASE}/text/StaticMsg/Dungeon.json")
    names.update(get(f"{BASE}/text/StaticMsg/LocationMarker.json"))

    by_name = {}
    for m in summary["Dungeon"]:
        by_name[norm(names[m["MessageID"]])] = m

    out_shrines = []
    missing = []
    for s in shrines:
        m = by_name.get(norm(s["name"]))
        if m is None:
            missing.append(s["name"])
            continue
        t = m["Translate"]
        out_shrines.append({
            "id": s["id"], "name": s["name"], "region": s["region"],
            "dlc": bool(s.get("dlc")), "flag": m["SaveFlag"],
            "x": round(t["X"], 2), "z": round(t["Z"], 2),
        })
    if missing:
        sys.exit(f"no objmap marker for: {missing}")

    towers = [{
        "name": names.get(m["MessageID"], m["MessageID"]),
        "x": round(m["Translate"]["X"], 2), "z": round(m["Translate"]["Z"], 2),
    } for m in summary["Tower"]]

    # Korok seed spots: never drawn on the in-game map, so they serve as
    # decoy positions for testing the "Not present" path (robustness.py).
    koroks = [[round(m["Translate"]["X"]), round(m["Translate"]["Z"])] for m in summary["Korok"]]

    OUT.parent.mkdir(exist_ok=True)
    json.dump({"source": f"{BASE}/map_summary/MainField/static.json",
               "shrines": out_shrines, "towers": towers},
              open(OUT, "w"), indent=1)
    json.dump(koroks, open(OUT.parent / "koroks.json", "w"))
    print(f"wrote {len(out_shrines)} shrines, {len(towers)} towers -> {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
