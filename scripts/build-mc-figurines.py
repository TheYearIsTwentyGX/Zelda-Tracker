#!/usr/bin/env python3
"""
Build files/mc/figurines.json: Carlov's 136 figurines in The Minish Cap.

    scripts/build-mc-figurines.py            download what isn't cached yet, then build
    scripts/build-mc-figurines.py --refresh  re-download everything first

Sources (see docs/mc-figurines.md):
- Zelda Wiki, "Figurines in The Minish Cap": number, name, the North American in-game description
  and who is pictured.
- The zeldaret/tmc decompilation, src/fileselect.c (gUnk_080FC3E4), pinned to TMC_COMMIT: each
  figurine's egg colour (its category) and the story flag or Kinstone fusion that adds it to
  Carlov's pool, for the US build. Zelda Wiki's Figurine Gallery table is checked against it below.

Downloads are cached in build/figurines-cache. Exits non-zero if a sanity check fails.
"""

import json
import re
import sys
import urllib.request
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "shared/src/commonMain/composeResources/files/mc/figurines.json"
CACHE = ROOT / "build/figurines-cache"
HEADERS = {"User-Agent": "ZeldaGX figurine dataset builder (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}
TMC_COMMIT = "6fb6dfb4a7efbe24d0fd1dda5097af6131faacde"
WIKI_LIST = (
    "https://zeldawiki.wiki/w/api.php?action=parse&page=Figurines_in_The_Minish_Cap"
    "&prop=wikitext&format=json&formatversion=2"
)
DECOMP = f"https://raw.githubusercontent.com/zeldaret/tmc/{TMC_COMMIT}/src/fileselect.c"

# The decomp's figurine type (egg palette) → category. Zelda Wiki, Figurine Gallery: blue eggs
# hold characters, red sets and scenes, green common enemies, gold bosses and sub-bosses.
CATEGORIES = [
    {"id": "characters", "name": "Characters", "egg": "Blue"},
    {"id": "scenes", "name": "Scenes", "egg": "Red"},
    {"id": "enemies", "name": "Enemies", "egg": "Green"},
    {"id": "bosses", "name": "Bosses", "egg": "Gold"},
]
TYPE_TO_CATEGORY = {"1": "characters", "0": "scenes", "2": "enemies", "3": "bosses"}

# Story checkpoints in play order. `level` is the figurine device's progress value
# (sub_0808804C in src/object/figurineDevice.c); `flag` is the one figurine gated by a dungeon
# flag instead. `added` is how many figurines with no side requirement each one adds, Zelda Wiki's
# Figurine Gallery "U" column, which the build checks.
CHECKPOINTS = [
    {"id": "gallery_opens", "level": "0", "added": 48, "name": "Figurine Gallery opens",
     "detail": "After Deepwood Shrine, once the Earth Element makes the White Sword"},
    {"id": "cave_of_flames", "level": "1", "added": 3, "name": "Cave of Flames",
     "detail": "Clear the Cave of Flames (Fire Element)"},
    {"id": "king_possessed", "level": "2", "added": 9, "name": "Vaati takes the king",
     "detail": "Vaati possesses King Daltus, on the first trip to the Western Wood"},
    {"id": "fortress_of_winds", "level": "3", "added": 25, "name": "Fortress of Winds",
     "detail": "Clear the Fortress of Winds (Ocarina of Wind)"},
    {"id": "big_blue_chuchu", "level": "UNK_6_8", "added": 1, "name": "Big Blue Chuchu",
     "detail": "Defeat the Big Blue Chuchu in the Temple of Droplets (Flame Lantern)"},
    {"id": "temple_of_droplets", "level": "4", "added": 12, "name": "Temple of Droplets",
     "detail": "Clear the Temple of Droplets (Water Element)"},
    {"id": "royal_crypt", "level": "5", "added": 5, "name": "Royal Crypt",
     "detail": "Speak to King Gustaf at the end of the Royal Crypt"},
    {"id": "palace_of_winds_entered", "level": "6", "added": 6, "name": "Palace of Winds reached",
     "detail": "Reach the Palace of Winds (Ezlo's talk at its entrance)"},
    {"id": "palace_of_winds", "level": "7", "added": 10, "name": "Palace of Winds",
     "detail": "Clear the Palace of Winds (Wind Element)"},
    {"id": "vaati_defeated", "level": "9", "added": 6, "name": "Final series",
     "detail": "Defeat Vaati and watch the credits", "finalSeries": True},
]

# Figurines that also need a side task. The decomp says which flag or fusion it checks; the
# wording follows Zelda Wiki's Figurine Gallery "Optional" table. `checkpoint` is the story point
# the game also requires (US build), if any.
REQUIREMENTS = {
    10: ("Fuse Kinstones with the Postman", None),
    22: ("Suck the dust off the empty stall's carpet in Hyrule Town with the Gust Jar", "king_possessed"),
    24: ("Meet the Crenel Hermit in his cave on Mount Crenel", None),
    36: ("Fuse Kinstones with Din, Nayru or Farore until a Joy Butterfly appears", None),
    37: ("Fuse Kinstones with Dampé", None),
    45: ("Dig out the Goron Cave at Lon Lon Ranch", None),
    66: ("Fuse Kinstones with the Goron punching the wall", None),
    67: ("Fuse Kinstones with Spookter until Spekter leaves Hyrule Town", "royal_crypt"),
    111: ("Fuse Kinstones to free a Golden Octorok, e.g. with the Business Scrub in Castor Wilds, "
          "then the one in the newly opened Minish Woods tree", None),
    112: ("Fuse Kinstones to free a Golden Tektite, e.g. with the Minish in the south-east room "
          "of Melari's Mine", None),
    113: ("Fuse Kinstones to free a Golden Rope, e.g. with Flurris by the stairs on the Wind "
          "Tribe tower's third floor", None),
}


def fetch(url: str, name: str, refresh: bool) -> str:
    path = CACHE / name
    if refresh or not path.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS)) as r:
            path.write_bytes(r.read())
    return path.read_text(encoding="utf-8")


def clean(s: str) -> str:
    """Wikitext to the plain text the game shows; the player's name reads "Link"."""
    s = re.sub(r"\{\{Color TMC\|[^|]*\|([^}]*)\}\}", r"\1", s)
    s = s.replace("{{Player Name}}", "Link").replace("[Player Name]", "Link")
    s = re.sub(r"<br\s*/?>", " ", s)
    s = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", s)
    s = s.replace("’", "'").replace("‘", "'")
    return re.sub(r"\s+", " ", s).strip()


def wiki_rows(text: str) -> list[dict]:
    text = json.loads(text)["parse"]["wikitext"]
    rows = []
    for chunk in re.split(r"\n\|-\s*\n", text[text.index("|columns="):])[1:]:
        cells = [c[1:].strip() for c in chunk.split("\n") if c.startswith("|")]
        if len(cells) >= 5:
            rows.append({"number": int(cells[1]), "name": clean(cells[0]), "description": clean(cells[2]),
                         "pictured": clean(cells[4])})
    return rows


def decomp_table(src: str) -> list[dict]:
    """gUnk_080FC3E4 as the US build compiles it: EU and JP are undefined."""
    start = src.index("const struct_080FC3E4 gUnk_080FC3E4[] = {")
    body = src[start:src.index("};", start)]
    entries, active = [], []
    for line in body.splitlines()[1:]:
        s = line.strip()
        if s.startswith("#ifdef"):
            active.append(False)  # only EU / JP are tested
        elif s.startswith("#ifndef") or s.startswith("#if !defined"):
            active.append(True)
        elif s.startswith("#if"):
            active.append(False)  # #if defined(EU) || defined(JP)
        elif s.startswith("#else"):
            active[-1] = not active[-1]
        elif s.startswith("#endif"):
            active.pop()
        elif all(active) and s.startswith("{"):
            bank, flag, level, kind = (x.strip() for x in s.strip("{},").split(","))
            entries.append({"bank": bank, "flag": flag, "level": level, "type": kind})
    return entries


def main() -> None:
    refresh = "--refresh" in sys.argv
    rows = wiki_rows(fetch(WIKI_LIST, "figurines_in_the_minish_cap.json", refresh))
    table = decomp_table(fetch(DECOMP, f"fileselect-{TMC_COMMIT[:8]}.c", refresh))
    by_level = {c["level"]: c["id"] for c in CHECKPOINTS}
    failures = []

    figurines = []
    for row in rows:
        n = row["number"]
        entry = table[n]  # entry 0 is unused, so figurine n is entry n
        requirement, needs = REQUIREMENTS.get(n, (None, None))
        level = entry["level"]
        if level in by_level:
            checkpoint = by_level[level]
            if requirement:
                failures.append(f"#{n} has a requirement but the game gates it on story level {level}")
        else:
            # A fusion or flag; the story point the game also checks, if any, comes from REQUIREMENTS.
            checkpoint = needs or "gallery_opens"
            if not requirement:
                failures.append(f"#{n} is gated by {level} {entry['flag']} but has no requirement")
        figurine = {
            "number": n,
            "name": row["name"],
            "category": TYPE_TO_CATEGORY[entry["type"]],
            "checkpoint": checkpoint,
            "description": row["description"],
            "pictured": row["pictured"],
        }
        if requirement:
            figurine["requirement"] = requirement
        figurines.append(figurine)

    numbers = [f["number"] for f in figurines]
    if numbers != list(range(1, 137)):
        failures.append(f"expected figurines 1-136 in order, got {len(numbers)}")
    story = Counter(f["checkpoint"] for f in figurines if "requirement" not in f)
    for c in CHECKPOINTS:
        if story[c["id"]] != c["added"]:
            failures.append(f"{c['id']}: {story[c['id']]} figurines, Zelda Wiki says {c['added']}")
    if len(REQUIREMENTS) != 11:
        failures.append("Zelda Wiki lists 11 optional figurines")
    if failures:
        sys.exit("\n".join(failures))

    checkpoints = [{k: v for k, v in c.items() if k not in ("level", "added")} for c in CHECKPOINTS]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as f:
        f.write("{\n")
        f.write(f'"categories": {json.dumps(CATEGORIES, ensure_ascii=False)},\n')
        f.write('"checkpoints": [\n')
        f.write(",\n".join(json.dumps(c, ensure_ascii=False) for c in checkpoints))
        f.write("\n],\n\"figurines\": [\n")
        f.write(",\n".join(json.dumps(x, ensure_ascii=False) for x in figurines))
        f.write("\n]\n}\n")
    print(f"wrote {len(figurines)} figurines to {OUT.relative_to(ROOT)}")
    print(dict(Counter(f["category"] for f in figurines)))


if __name__ == "__main__":
    main()
