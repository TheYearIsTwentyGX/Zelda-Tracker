#!/usr/bin/env python3
"""
Build the Hyrule Warriors: Definitive Edition Adventure Mode maps for the app.

    tools/hwde/build_maps.py --map adventure_map           write the map's JSON, art and icons
    tools/hwde/build_maps.py --map adventure_map --check   only print the cross-check report
    tools/hwde/build_maps.py --self-test                   test the parser and the id mapping

Sources, all pinned so a run is repeatable (docs/hwde.md has the details and the licences):
- Fabian-F/HyruleWarriorsHelper, `v2` branch, at HWH_SHA: the squares, rewards, search cells and
  the per-square art. Its TypeScript data files are read with a small tokenizer; none of their
  code is run.
- SBAllen's GameFAQs guide for the Switch version, as archived by the Wayback Machine: used only to
  cross-check, and for the start square. None of its text is written out.
- Zelda Wiki's "Item Card" table: each card's in-game name, what it does on each map, and its price
  in the Item Card Shop. Its icons become drawable/hwde_card_<id>.png.

Then corrections.json is applied (each entry names its source), the ids are checked against the
lock file ids/<map>.txt, and the report lists every disagreement between the sources. A map is
ready when each line of its report is fixed in corrections.json or written up in docs/hwde.md.
Downloads are cached in tools/hwde/.cache/ (git-ignored).
"""

import argparse
import gzip
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
CACHE = HERE / ".cache"
FILES = ROOT / "shared/src/commonMain/composeResources/files"
DRAWABLE = ROOT / "shared/src/commonMain/composeResources/drawable"
HEADERS = {"User-Agent": "ZeldaGX HW:DE map builder (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}

HWH_SHA = "bdc126b9434d5d7144d6262c50b5b42db17faab4"  # v2 branch, 2026-10-18
HWH_RAW = f"https://raw.githubusercontent.com/Fabian-F/HyruleWarriorsHelper/{HWH_SHA}/"
SBALLEN = "https://gamefaqs.gamespot.com/switch/230454-hyrule-warriors-definitive-edition/faqs/73095/"
WIKI_API = "https://zeldawiki.wiki/w/api.php"

# Columns of Zelda Wiki's Item Card table, in order.
WIKI_MAP_COLUMNS = ["adventure_map", "master_quest_map", "twilight_map", "termina_map", "great_sea_map",
                    "master_wind_waker_map", "koholint_island_map", "grand_travels_map", "lorule_map"]

# Our map id -> its HWH id, name, SBAllen page and the Wayback snapshot of it, and the word that
# names it in the wiki's per-map card descriptions ("Adventure/Master Quest/Koholint: ...").
MAPS = {
    "adventure_map": {"hwh": "adventure", "name": "Adventure Map",
                      "sballen": ("adventure-map", "20190514154015"), "wiki": "Adventure"},
    "great_sea_map": {"hwh": "great-sea", "name": "Great Sea Map",
                      "sballen": ("great-sea-map", "20190514154017"), "wiki": "Great Sea"},
    "master_quest_map": {"hwh": "master-quest", "name": "Master Quest Map",
                         "sballen": ("master-quest-map", "20190514154011"), "wiki": "Master Quest"},
    # The Rewards Map's 13 squares sit on the game's full 16x8 grid. Nobody has art for it, so the
    # app draws plain tiles named by each square's illustration; SBAllen lists them by that name.
    "rewards_map": {"hwh": "rewards", "name": "Rewards Map", "grid": (16, 8), "art": False, "titles": True,
                    "sballen": ("rewards-map", "20190514154019"), "wiki": "Rewards"},
    "twilight_map": {"hwh": "twilight", "name": "Twilight Map",
                     "sballen": ("twilight-map", "20190514154018"), "wiki": "Twilight"},
    "termina_map": {"hwh": "termina", "name": "Termina Map",
                    "sballen": ("termina-map", "20190514154015"), "wiki": "Termina"},
    "koholint_island_map": {"hwh": "koholint-island", "name": "Koholint Island Map",
                            "sballen": ("koholint-island-map", "20210114211231"), "wiki": "Koholint"},
    # Two starting squares (SBAllen: "Starting squares: D-8, K-5"), and the Phantom Hourglass's
    # middle section, which opens at J-4 once both Giant Bosses (A-8, M-2) are beaten.
    "grand_travels_map": {"hwh": "grand-travels", "name": "Grand Travels Map",
                          "gates": [("J-4", ["A-8", "M-2"])],
                          "sballen": ("grand-travels-map", "20250828115220"), "wiki": "Grand Travels"},
    "lorule_map": {"hwh": "lorule", "name": "Lorule Map",
                   "sballen": ("lorule-map", "20240308033435"), "wiki": "Lorule"},
    "master_wind_waker_map": {"hwh": "master-wind-waker", "name": "Master Wind Waker Map",
                              "sballen": ("master-wind-waker-map", "20190514154017"), "wiki": "Master Wind Waker"},
}

# Independent pins: squares (in Prima's labels) that other DE guides give. The report checks them.
# Nintendo Life, "Hyrule Warriors: Definitive Edition: All Weapons And How To Unlock Them", as
# (weapon, square). It names weapons by level ("Golden Gauntlets") and writes some squares
# SBAllen's way (column first): those are converted here.
NINTENDO_LIFE_WEAPONS = {
    "adventure_map": [("gauntlets", "G-14"), ("great_fairy", "G-16"), ("epona", "E-15"), ("spinner", "C-2"),
                      ("naginata", "G-11"), ("summoning_gate", "A-9"), ("baton", "C-15"), ("dominion_rod", "A-2"),
                      ("trident", "B-8")],
    "great_sea_map": [("magic_rod", "F-14"), ("gauntlets", "B-3"), ("great_fairy", "H-1"), ("epona", "G-1"),
                      ("spinner", "E-1"), ("naginata", "G-14"), ("spear", "A-14"), ("summoning_gate", "A-4"),
                      ("hammer", "E-10"), ("baton", "B-13"), ("dominion_rod", "G-3"), ("shackle", "D-10"),
                      ("goddess_blade", "B-4"), ("great_swords", "F-2"), ("trident", "A-1"), ("demon_blade", "F-8"),
                      ("zora_scale", "D-4"), ("parasol", "A-11"), ("scimitars", "G-7"), ("scepter", "D-14"),
                      ("dragon_spear", "A-7"), ("ring", "G-6"),
                      # Written column first: SBAllen's A-4, I-4, E-2, F-4 and N-3.
                      ("mirror", "D-1"), ("mask", "D-9"), ("balloon", "B-5"), ("ocarina", "D-6"), ("light_sword", "C-14")],
    # Written column first for the last two: SBAllen's K-6 and M-2.
    "master_wind_waker_map": [("rito_harp", "D-4"), ("rito_harp", "F-11"), ("rito_harp", "B-13")],
    "master_quest_map": [
        ("hylian_sword", "H-6"),
        ("magic_rod", "B-8"),
        ("gauntlets", "A-11"),
        ("great_fairy", "A-9"),
        ("epona", "D-13"),
        ("giant_blade", "G-6"),
        ("naginata", "E-3"),
        ("harp", "A-7"),
        ("book_of_sorcery", "A-4"),
        ("spinner", "F-15"),
        ("spear", "B-6"),
        ("summoning_gate", "D-16"),
        ("hammer", "A-3"),
        ("rapier", "G-5"),
        ("baton", "B-10"),
        ("dominion_rod", "B-16"),
        ("shackle", "B-3"),
        ("goddess_blade", "G-15"),
        ("great_swords", "B-4"),
        ("trident", "C-15"),
        ("scepter", "B-2"),
        ("dragon_spear", "G-13"),
        ("ring", "A-10"),
        ("mirror", "A-16"),  # written column first: SBAllen P-1
        ("mask", "H-2"),  # written column first: SBAllen B-8
        ("balloon", "A-6"),  # written column first: SBAllen F-1
        ("crossbows", "B-5"),  # written column first: SBAllen E-2
        ("ocarina", "D-5"),  # written column first: SBAllen E-4
        ("light_sword", "C-2"),  # written column first: SBAllen B-3
        ("cutlass", "F-2"),  # written column first: SBAllen B-6
        ("sail", "H-3"),  # written column first: SBAllen C-8
        ("parasol", "D-15"),
        ("scimitars", "G-16"),
        ("demon_blade", "D-1"),
        ("zora_scale", "A-12"),
    ],
    "twilight_map": [
        ("hylian_sword", "C-2"),
        ("magic_rod", "E-1"),
        ("gauntlets", "G-5"),
        ("spinner", "D-5"),
        ("giant_blade", "G-8"),
        ("book_of_sorcery", "A-9"),
        ("spear", "E-3"),
        ("rapier", "D-10"),
        ("shackle", "E-4"),
        ("scimitars", "A-3"),
        ("parasol", "A-11"),
        ("crossbows", "D-6"),  # written column first: SBAllen F-4
        ("light_sword", "F-6"),
        ("cutlass", "D-4"),
        ("sail", "F-5"),  # written column first: SBAllen E-6
        ("balloon", "A-7"),  # written column first: SBAllen G-1
            ("great_swords", "H-2"),  # Nintendo Life; SBAllen and the Helper: H-4
    ],
    "termina_map": [
        ("epona", "H-8"),
        ("naginata", "C-7"),
        ("harp", "H-13"),
        ("summoning_gate", "D-16"),
        ("hammer", "H-7"),
        ("baton", "F-13"),
        ("dominion_rod", "A-3"),
        ("goddess_blade", "C-6"),
        ("demon_blade", "F-6"),
        ("zora_scale", "G-14"),
        ("scepter", "F-7"),
        ("dragon_spear", "D-14"),
        ("ring", "C-15"),
        ("mirror", "A-14"),  # written column first: SBAllen N-1
        ("mask", "A-1"),
        ("ocarina", "B-6"),  # written column first: SBAllen F-2
        ("trident", "B-1"),
            ("great_fairy", "B-5"),  # Nintendo Life; SBAllen and the Helper: B-7
    ],
}
# RPG Site, "Hyrule Warriors: Definitive Edition: How to unlock every character".
RPG_SITE_WARRIORS = {
    "adventure_map": {"young_link": "G-9", "skull_kid": "C-13", "twili_midna": "C-6", "tingle": "A-12"},
    "great_sea_map": {"toon_link": "B-9", "medli": "B-8", "tetra": "C-11", "king_daphnes": "C-13"},
}

# The Helper names a few weapon types by their first level's name; the game's own type name is used.
WEAPON_NAMES = {"epona": "Horse"}

# Card ids the Helper reuses across maps whose cards differ: the Master Wind Waker Map's Power
# Bracelet is the Great Sea's (Stone Heads, its own icon), not the Adventure Map's.
MAP_CARD_IDS = {"master_wind_waker_map": {"power-bracelet": "power-bracelet-2"}}

# Card groups the Helper names but doesn't define: a whole-square search any of their cards does.
CARD_GROUPS = {
    "instrument": {
        "name": "Any Instrument of the Sirens",
        "anyOf": ["full_moon_cello", "conch_horn", "sea_lily_bell", "surf_harp", "wind_marimba", "coral_triangle",
                  "organ_of_evening_calm", "thunder_drum"],
        "effects": ["rule"],
        "text": "Any of the eight Instruments banishes the Nightmare over a square. Each one used for the first "
                "time adds to the Egg Counter; with all eight, the Wind Fish wakes.",
    },
}

# Lorule's Fissures: a Wall Painting search on one world's square opens the same square in the other
# world, eight columns over (Zelda Wiki, Adventure Mode; SBAllen's H-7 opens his P-7).
FISSURE_MAPS = {"lorule_map": ("wall_painting", 8)}

# Koholint's Nightmares: a square with this card's whole-square search can't be played until it's
# done. The Egg's search (all eight Instruments used: the Wind Fish wakes) banishes every Nightmare
# for good, so it lifts them all (SBAllen's map notes).
SEAL_MAPS = {"koholint_island_map": ("instrument", "r1c7")}

REGION_ORDER = ["colorless", "green", "yellow", "purple", "orange", "blue", "red"]
REGION_NAMES = {"colorless": "Black", "green": "Green", "yellow": "Yellow", "purple": "Purple",
                "orange": "Orange", "blue": "Blue", "red": "Red"}
ROW_LETTERS = "ABCDEFGH"
COL_LETTERS = "ABCDEFGHIJKLMNOP"
SQUARE_W, SQUARE_H = 256, 176


# ---------------------------------------------------------------------------- fetching

def fetch(url: str, cache_name: str) -> bytes:
    path = CACHE / cache_name
    if path.exists():
        return path.read_bytes()
    path.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as r:
                data = r.read()
            break
        except Exception as e:  # noqa: BLE001 - retried, then re-raised
            if attempt == 3:
                raise RuntimeError(f"{url}: {e}") from e
            time.sleep(3 * (attempt + 1))
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    path.write_bytes(data)
    time.sleep(0.5)
    return data


def hwh(path: str) -> bytes:
    return fetch(HWH_RAW + path, f"hwh/{HWH_SHA[:10]}/{path}")


# ---------------------------------------------------------------------------- TypeScript literals

def ts_literal(source: str, name: str | None = None):
    """
    The object or array literal assigned by the first `export const <name> = ` in a prettier-formatted
    TypeScript data file, as Python values. It only tokenizes: strings, numbers, true/false/null/
    undefined, unquoted keys and trailing commas. Anything else is an error, so no code is ever run.
    """
    pattern = r"export const (\w+)(?:\s*:\s*[\w<>\[\], ]+)?\s*=\s*"
    for m in re.finditer(pattern, source):
        if name is None or m.group(1) == name:
            return _Parser(source, m.end()).value()
    raise ValueError(f"no `export const {name or ''}` literal")


class _Parser:
    def __init__(self, s: str, i: int):
        self.s, self.i = s, i

    def ws(self):
        while True:
            while self.i < len(self.s) and self.s[self.i] in " \t\r\n":
                self.i += 1
            if self.s.startswith("//", self.i):
                self.i = self.s.index("\n", self.i)
            elif self.s.startswith("/*", self.i):
                self.i = self.s.index("*/", self.i) + 2
            else:
                return

    def value(self):
        self.ws()
        c = self.s[self.i]
        if c == "{":
            return self.obj()
        if c == "[":
            return self.arr()
        if c in "'\"`":
            return self.string()
        m = re.compile(r"-?\d+(\.\d+)?").match(self.s, self.i)
        if m:
            self.i = m.end()
            return float(m.group()) if m.group(1) else int(m.group())
        for word, v in (("true", True), ("false", False), ("null", None), ("undefined", None)):
            if self.s.startswith(word, self.i) and not self.s[self.i + len(word)].isalnum():
                self.i += len(word)
                return v
        raise ValueError(f"unexpected {self.s[self.i:self.i + 30]!r}")

    def string(self):
        q = self.s[self.i]
        self.i += 1
        out = []
        while self.s[self.i] != q:
            c = self.s[self.i]
            if c == "\\":
                n = self.s[self.i + 1]
                out.append({"n": "\n", "t": "\t"}.get(n, n))
                self.i += 2
            else:
                if q == "`" and c == "$" and self.s[self.i + 1] == "{":
                    raise ValueError("template interpolation")
                out.append(c)
                self.i += 1
        self.i += 1
        return "".join(out)

    def key(self):
        self.ws()
        if self.s[self.i] in "'\"":
            return self.string()
        m = re.compile(r"[A-Za-z_$][\w$]*").match(self.s, self.i)
        if not m:
            raise ValueError(f"bad key at {self.s[self.i:self.i + 30]!r}")
        self.i = m.end()
        return m.group()

    def obj(self):
        self.i += 1
        out = {}
        while True:
            self.ws()
            if self.s[self.i] == "}":
                self.i += 1
                return out
            k = self.key()
            self.ws()
            if self.s[self.i] != ":":
                raise ValueError(f"expected ':' after {k}")
            self.i += 1
            out[k] = self.value()
            self.ws()
            if self.s[self.i] == ",":
                self.i += 1

    def arr(self):
        self.i += 1
        out = []
        while True:
            self.ws()
            if self.s[self.i] == "]":
                self.i += 1
                return out
            out.append(self.value())
            self.ws()
            if self.s[self.i] == ",":
                self.i += 1


# ---------------------------------------------------------------------------- ids and labels

def slug(text: str) -> str:
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", text.lower().replace("'", ""))).strip("_")


def hwh_id_to_square(tile_id: str) -> tuple[int, int]:
    """HWH's (and SBAllen's) "F1" is column F, row 1: (row, col), both from 1."""
    return int(tile_id[1:]), COL_LETTERS.index(tile_id[0]) + 1


def square_id(row: int, col: int) -> str:
    return f"r{row}c{col}"


def prima_label(row: int, col: int) -> str:
    """Prima's official guide and Zelda Wiki: row letter, then column number ("A-6")."""
    return f"{ROW_LETTERS[row - 1]}-{col}"


def sballen_label(row: int, col: int) -> str:
    """SBAllen's guide and HWH: column letter, then row number ("F-1")."""
    return f"{COL_LETTERS[col - 1]}-{row}"


def prima_to_square(label: str) -> tuple[int, int]:
    letter, number = label.split("-")
    return ROW_LETTERS.index(letter) + 1, int(number)


def sballen_to_square(label: str) -> tuple[int, int]:
    letter, number = label.split("-")
    return int(number), COL_LETTERS.index(letter) + 1


def norm(text: str) -> str:
    return re.sub(r"[^a-z0-9]", "", (text or "").lower())


# ---------------------------------------------------------------------------- sources

class Lookups:
    def __init__(self):
        src = lambda p: hwh(p).decode("utf-8")  # noqa: E731
        self.characters = {c["id"]: c for c in ts_literal(src("src/data/characters.ts"), "characters")}
        self.weapons = {w["id"]: w for w in ts_literal(src("src/data/weapons.ts"), "weapons")}
        self.cards = {c["id"]: c for c in ts_literal(src("src/data/item-cards.ts"), "itemCards")}
        self.enemies = {e["id"]: e for e in ts_literal(src("src/data/enemies.ts"), "enemies")}
        self.materials = {m["id"]: m for m in ts_literal(src("src/data/materials.ts"), "materials")}

    def character_name(self, cid: str) -> str:
        return self.characters[cid]["name"]

    def answer_name(self, aid: str) -> str:
        if aid in self.characters:
            return self.characters[aid]["name"]
        return self.enemies[aid]["name"]


def sballen_page(map_id: str) -> dict:
    """SBAllen's squares, by his label: the Switch side of every cell (3DS-only lines dropped)."""
    page, ts = MAPS[map_id]["sballen"]
    raw = fetch(f"https://web.archive.org/web/{ts}id_/{SBALLEN}{page}", f"sballen/{page}-{ts}.html")
    text = raw.decode("utf-8", "replace")
    ds = r'<img[^>]*73095-150\.png[^>]*/>'
    sw = r'<img[^>]*73095-151\.png[^>]*/>'

    def lines(cell: str) -> list[str]:
        out = []
        for line in re.split(r"<br\s*/?>|</p>\s*<p>|\s\|\s", cell):
            if re.search(ds, line):
                continue  # a 3DS-only entry
            t = html.unescape(re.sub(r"<[^>]+>", " ", re.sub(sw, "", line)))
            t = re.sub(r"\s+", " ", t).strip(" |")
            if t and t.lower() not in ("none", "n/a"):
                out.append(t)
        return out

    squares = {}
    for m in re.finditer(r'Map (?:- )?([^<]+?)<a id="[^"]*"></a></h4>(<table.*?</table>)', text, re.S):
        rows = re.findall(r"<tr>(.*?)</tr>", m.group(2), re.S)
        cells = {}
        for head, body in zip(rows, rows[1:]):
            heads = re.findall(r"<th[^>]*>(.*?)</th>", head, re.S)
            if not heads:
                continue
            tds = re.findall(r"<td[^>]*>(.*?)</td>", body, re.S)
            for k, h in enumerate(heads):
                if k < len(tds):
                    cells[html.unescape(re.sub(r"<[^>]+>", "", h)).strip()] = lines(tds[k])
        squares[html.unescape(m.group(1)).strip()] = cells
    plain = re.sub(r"<[^>]+>", " ", text)
    start = re.search(r"Starting square:\s*([A-P]-\d+)", plain)
    starts = re.search(r"Starting squares:\s*([A-P]-\d+(?:\s*,\s*[A-P]-\d+)*)", plain)
    boss = re.search(r"Final Boss square:\s*([A-P]-\d+)", re.sub(r"<[^>]+>", " ", text))
    labels = re.findall(r"[A-P]-\d+", starts.group(1)) if starts else ([start.group(1)] if start else [])
    return {"squares": squares, "start": labels[0] if labels else None, "otherStarts": labels[1:],
            "boss": boss and boss.group(1)}


def wiki_cards() -> list[dict]:
    """Zelda Wiki's HWDE Item Card table: name, which maps, what it does, the shop price."""
    q = urllib.parse.urlencode({"action": "parse", "page": "Item Card", "prop": "wikitext", "format": "json"})
    data = json.loads(fetch(f"{WIKI_API}?{q}", "wiki/Item_Card.json"))
    text = data["parse"]["wikitext"]["*"]
    table = text[text.index("==List of Item Cards=="):]
    table = table[:table.index("\n}}")]
    out = []
    for block in table.split("\n|-")[1:]:
        cells = [c.strip() for c in re.split(r"\n\||\s\|\s", "\n" + block.strip()) if c.strip()]
        if len(cells) < 12:
            continue
        name, flags, use, price = cells[0], cells[1:10], cells[10], cells[11]
        out.append({
            # "Wind Waker (Item) [2]" -> "Wind Waker": the wiki's disambiguation isn't the card's name.
            "name": re.sub(r"(\s*(\[[^\]]*\]|\([^)]*\)))+$", "", re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", name)),
            "maps": [m for m, f in zip(WIKI_MAP_COLUMNS, flags) if "Y" in f],
            "use": use,
            "price": None if "N/A" in price else int(re.sub(r"\D", "", price) or 0) or None,
        })
    return out


def card_use(use: str, wiki_word: str) -> str:
    """
    The description for one map, from text like "Adventure/Master Quest:... <br/>Great Sea/Master
    Wind Waker (Hero's Bow): ..." (the wiki also writes "GreatSea"). Without a part for the map, the
    first part, without its prefix.
    """
    use = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", use)
    parts = [re.sub(r"<[^>]+>", " ", p).strip() for p in re.split(r"<br\s*/?>", use)]
    prefix = re.compile(r"([^:.]{1,80}):\s*(.*)")
    for p in parts:
        m = prefix.fullmatch(p)
        if m and norm(wiki_word) in [norm(n) for n in re.sub(r"\([^)]*\)", "", m.group(1)).split("/")]:
            return m.group(2).strip()
    m = prefix.fullmatch(parts[0])
    return (m.group(2) if m else parts[0]).strip()


def card_name_on(use: str, wiki_word: str) -> str | None:
    """The card's name on one map where the wiki gives it in the prefix: "Lorule (Bow): ..." -> "Bow"."""
    for p in re.split(r"<br\s*/?>", use):
        m = re.match(r"\s*([^:.]{1,80}):", re.sub(r"<[^>]+>", " ", p))
        if m and norm(wiki_word) in [norm(n) for n in re.sub(r"\([^)]*\)", "", m.group(1)).split("/")]:
            named = re.search(r"\(([^)]+)\)", m.group(1))
            return named.group(1).strip() if named else None
    return None


def card_effects(use: str, card_id: str) -> list[str]:
    if card_id == "compass":
        return ["compass"]
    effects = []
    if re.search(r"secret reward", use):
        effects.append("reward")
    if re.search(r"reveal(ing)? (hidden )?enem|enemy locations", use):
        effects.append("enemies")
    if re.search(r"removing locks|opening the square|reach new|unlock a hidden part|allowing passage", use):
        effects.append("path")
    if re.search(r"(removing|lifts?)[^.]*(restriction|rules?)|Dispels|Eliminates Nightmares", use):
        effects.append("rule")
    return effects or ["other"]


# ---------------------------------------------------------------------------- conversion

def convert(map_id: str, lk: Lookups, cards_wiki: list[dict], sb: dict) -> dict:
    cfg = MAPS[map_id]
    hwh_map = ts_literal(hwh(f"src/data/maps/{cfg['hwh']}/{cfg['hwh']}-map.ts").decode("utf-8"))
    tiles = hwh_map["tiles"]
    cols, rows = cfg.get("grid") or (max(hwh_id_to_square(t["id"])[1] for t in tiles), max(hwh_id_to_square(t["id"])[0] for t in tiles))

    used_cards: list[str] = []
    warriors: set[str] = set()

    def card(cid: str) -> str:
        cid = MAP_CARD_IDS.get(map_id, {}).get(cid, cid).replace("-", "_")
        if cid not in used_cards:
            used_cards.append(cid)
        return cid

    def warrior(cid: str) -> str:
        warriors.add(cid)
        return cid.replace("-", "_")

    squares = []
    for t in sorted(tiles, key=lambda t: hwh_id_to_square(t["id"])):
        row, col = hwh_id_to_square(t["id"])
        sid = square_id(row, col)
        challenge = t["challenge"]
        m = re.match(r"(Adventure|Challenge) Battle:\s*(.*)", challenge)
        sq = {
            "id": sid, "label": prima_label(row, col), "altLabel": sballen_label(row, col),
            "row": row, "col": col, "region": t["difficulty"],
            "battle": m.group(1).lower() if m else None,
            "mission": (m.group(2) if m else challenge).strip(),
        }
        if cfg.get("titles"):
            sq["title"] = re.search(r'illustration "([^"]+)"', challenge).group(1)
        if t.get("additionalRule"):
            # The Helper keeps SBAllen's "Engless Night Rule" typo on a few squares.
            sq["rule"] = t["additionalRule"].replace("Engless Night", "Endless Night")
        if t.get("isFinalBoss"):
            sq["finalBoss"] = True
        sel = t.get("characterSelection") or {}
        if sel.get("status") == "restricted":
            sq["warriors"] = [{"warrior": warrior(a["characterId"]),
                               **({"weapon": a["weaponId"].replace("-", "_")} if a.get("weaponId") else {})}
                              for a in sel["alternatives"]]
        req = t.get("requirements") or {}
        sq["aRank"] = {k2: req[k1] for k1, k2 in (("kills", "kos"), ("minutes", "minutes"), ("damage", "damage")) if req.get(k1)}
        if t.get("blockades"):
            sq["blockades"] = sorted(t["blockades"], key=["north", "east", "south", "west"].index)

        rewards, gives = [], []
        r = t.get("rewards") or {}
        for slot, items in (("aRank", [r["aRank"]] if r.get("aRank") else []), ("clear", r.get("clear") or []),
                            ("treasure", r.get("treasure") or [])):
            for x in items:
                if x["type"] == "item-card":
                    gives.append(card(x["itemCardId"]))
                    continue
                rewards.append(reward(x, slot, lk, warrior))
        assign_ids(sid, rewards)
        if rewards:
            sq["rewards"] = rewards
        if gives:
            sq["givesCards"] = gives

        gs = [skulltula(s) for s in (r.get("skulltulas") or [])]
        for i, g in enumerate(gs, 1):
            g["id"] = f"{sid}.gs{i}"
        if gs:
            sq["skulltulas"] = [{"id": g.pop("id"), **g} for g in gs]

        searches = []
        for s in t.get("search") or []:
            searches.append({"card": card(s["itemCardId"]), "row": s["target"]["row"], "col": s["target"]["column"]})
        full = t.get("fullTileSearch")
        if full:
            searches.append({"card": card(full.get("itemCardId") or full["itemCardGroupId"]), "wholeSquare": True})
        seen = Counter()
        for s in searches:
            seen[s["card"]] += 1
            s_id = f"{sid}.search_{s['card']}" + (f"_{seen[s['card']]}" if seen[s["card"]] > 1 else "")
            s["id"] = s_id
        if searches:
            sq["searches"] = [{"id": s.pop("id"), **s} for s in searches]
        if t.get("quizAnswers"):
            sq["quiz"] = [lk.answer_name(a) for a in t["quizAnswers"]]
        squares.append(sq)

    flagged = [x["id"] for x in squares if x.get("finalBoss")]
    if len(flagged) > 1 and sb.get("boss"):
        # The Helper also flags Grand Travels' two Giant Beasts; the Dark Ruler is SBAllen's.
        boss = square_id(*sballen_to_square(sb["boss"]))
        for x in squares:
            if x.get("finalBoss") and x["id"] != boss:
                del x["finalBoss"]
    # A wall lies between two squares, so it's on both: the Helper has one (Grand Travels B7's east
    # side) on one square only.
    at = {(x["row"], x["col"]): x for x in squares}
    step = {"north": (-1, 0, "south"), "south": (1, 0, "north"), "east": (0, 1, "west"), "west": (0, -1, "east")}
    for x in squares:
        for side in list(x.get("blockades", [])):
            dr, dc, opp = step[side]
            other = at.get((x["row"] + dr, x["col"] + dc))
            if other is not None and opp not in other.get("blockades", []):
                other["blockades"] = sorted(other.get("blockades", []) + [opp], key=["north", "east", "south", "west"].index)
                print(f"   walls: {x['label']}'s {side} wall added to {other['label']} too")
    if map_id in FISSURE_MAPS:
        card_id, span = FISSURE_MAPS[map_id]
        ids = {x["id"] for x in squares}
        for x in squares:
            for search in x.get("searches", []):
                if search["card"] == card_id:
                    other = square_id(x["row"], x["col"] + span if x["col"] <= span else x["col"] - span)
                    if other in ids:
                        search["opens"] = [other]
    if map_id in SEAL_MAPS:
        card_id, egg = SEAL_MAPS[map_id]
        egg_search = next(s["id"] for x in squares if x["id"] == egg for s in x.get("searches", []) if s["card"] == card_id)
        for x in squares:
            own = [s["id"] for s in x.get("searches", []) if s["card"] == card_id and s.get("wholeSquare")]
            if own:
                x["sealedBy"] = own + ([egg_search] if egg_search not in own else [])
    regions_used = {s["region"] for s in squares}
    out = {
        "id": map_id,
        "name": cfg["name"],
        "cols": cols,
        "rows": rows,
        "start": square_id(*sballen_to_square(sb["start"])) if sb.get("start") else None,
        **({"otherStarts": [square_id(*sballen_to_square(x)) for x in sb["otherStarts"]]} if sb.get("otherStarts") else {}),
        **({"gates": [{"square": square_id(*sballen_to_square(sq)), "afterCleared": [square_id(*sballen_to_square(x)) for x in after]}
                      for sq, after in cfg["gates"]]} if cfg.get("gates") else {}),
        "finalBoss": next((s["id"] for s in squares if s.get("finalBoss")), None),
        **({"art": {"squares": f"hwde/{map_id}", "preview": f"hwde/{map_id}/preview.webp",
                    "squareWidth": SQUARE_W, "squareHeight": SQUARE_H}} if cfg.get("art", True) else {}),
        "regions": [{"id": r_, "name": REGION_NAMES[r_]} for r_ in REGION_ORDER if r_ in regions_used],
        "cards": map_cards(map_id, used_cards, lk, cards_wiki),
        "warriors": sorted(({"id": w.replace("-", "_"), "name": lk.character_name(w)} for w in warriors),
                           key=lambda w: w["name"]),
        "squares": squares,
    }
    return out


def reward(x: dict, slot: str, lk: Lookups, warrior) -> dict:
    t = x["type"]
    out: dict = {"slot": slot}
    if t == "heart-container":
        out.update(type="heart_container", warrior=warrior(x["characterId"]), name="Heart Container")
    elif t == "heart-piece":
        out.update(type="heart_piece", warrior=warrior(x["characterId"]), name="Piece of Heart")
    elif t == "outfit":
        out.update(type="outfit", warrior=warrior(x["characterId"]), name=x["outfitName"])
    elif t == "character":
        out.update(type="warrior", warrior=warrior(x["characterId"]), name=lk.character_name(x["characterId"]))
    elif t == "weapon":
        w = lk.weapons[x["weaponId"]]
        out.update(type="weapon", warrior=warrior(w["characterId"]), weapon=x["weaponId"].replace("-", "_"),
                   level=str(x["level"]), name=f"{WEAPON_NAMES.get(x['weaponId'], w['name'])} Lv. {x['level']}")
    elif t == "material":
        mat = lk.materials.get(x.get("materialId") or "", {})
        out.update(type="material", name=mat.get("name", "Material"))
    elif t == "fairy":
        kind, _, name = x["text"].partition(" - ")
        kind = kind.strip()
        out.update(type="fairy_food" if kind == "Fairy Food" else "my_fairy" if kind.startswith("My Fairy") else "fairy_clothing",
                   name=x["text"].strip())
    else:
        out.update(type="other", name=x.get("text", "?"))
    if x.get("location"):
        keep = x["location"]
        m = re.match(r"(.*?)\s*\[(Pot|Jar)\]$", keep)
        if m:
            keep = m.group(1)
            out["container"] = m.group(2).lower()
        out["keep"] = keep
    return out


def assign_ids(sid: str, rewards: list[dict]):
    seen = Counter()
    for r in rewards:
        t = r["type"]
        w = r.get("warrior", "")
        base = {
            "heart_container": f"hc_{w}", "heart_piece": f"poh_{w}", "warrior": f"unlock_{w}",
            "outfit": f"outfit_{w}_{slug(r['name'])}",
            "weapon": f"weapon_{r.get('weapon')}_{slug(r.get('level', ''))}",
        }.get(t, f"{t}_{slug(r['name'].partition(' - ')[2] or r['name'])}")
        seen[base] += 1
        r_id = f"{sid}.{base}" + (f"_{seen[base]}" if seen[base] > 1 else "")
        rest = {k: v for k, v in r.items()}
        r.clear()
        r["id"] = r_id
        r.update(rest)


def skulltula(text: str) -> dict:
    """ "KO 1000 enemies. Located just outside ..." -> condition and where, in short form. """
    text = text.strip()
    same = re.search(r"\s*It is located in the same place as Gold Skulltula #1\.?", text)
    if same:
        cond = text[:same.start()].rstrip(". ")
        return {"condition": cond, "where": "Same place as the first"}
    m = re.match(r"(.*?\.)\s+(.*)", text)
    if m:
        cond, where = m.group(1).rstrip(". "), m.group(2).strip()
        where = re.sub(r"^(It is )?[Ll]ocated\s+", "", where).rstrip(".")
        return {"condition": cond, "where": where[:1].upper() + where[1:]}
    return {"condition": text.rstrip(".")}


def map_cards(map_id: str, used: list[str], lk: Lookups, cards_wiki: list[dict]) -> list[dict]:
    alias = {"goddessharp": "goddesssharp", "tearsoflight": "tearoflight", "seachart": "seamap", "bow": "herosbow"}
    wiki_by_name: dict[str, list[dict]] = {}
    for c in cards_wiki:
        wiki_by_name.setdefault(norm(c["name"]), []).append(c)
    out = []
    for cid in used:
        if cid in CARD_GROUPS:
            g = CARD_GROUPS[cid]
            out.append({"id": cid, "name": g["name"], "effects": g["effects"], "text": g["text"], "anyOf": g["anyOf"]})
            continue
        hwh_card = lk.cards[cid.replace("_", "-")]
        key = norm(hwh_card["name"])
        options = wiki_by_name.get(alias.get(key, key), [])
        w = next((c for c in options if map_id in c["maps"]), options[0] if options else None)
        use = card_use(w["use"], MAPS[map_id]["wiki"]) if w else ""
        named = card_name_on(w["use"], MAPS[map_id]["wiki"]) if w else None
        out.append({
            "id": cid,
            "name": named or (re.sub(r"\s*\(.*\)$", "", w["name"]) if w else hwh_card["name"]),
            "effects": card_effects(use, cid),
            "text": re.sub(r"\s+", " ", re.sub(r"\s*On the [A-Z][\w ]* [Mm]ap only,[^.]*\.", "", use)).strip(),
            **({"shopPrice": w["price"]} if w and w["price"] else {}),
        })
    return out


# ---------------------------------------------------------------------------- corrections, ids

def apply_corrections(m: dict, corrections: list[dict], lk: "Lookups") -> list[str]:
    applied = []
    by_label = {s["label"]: s for s in m["squares"]}
    for c in corrections:
        if c["map"] != m["id"]:
            continue
        op = c["op"]
        if op == "accept":
            continue  # a report line that's been looked into; report() leaves it out
        sq = by_label[c["square"]]
        if op == "removeReward":
            before = len(sq.get("rewards", []))
            sq["rewards"] = [r for r in sq.get("rewards", []) if r["id"] != c["id"]]
            if len(sq["rewards"]) == before:
                raise ValueError(f"correction {c}: no such reward")
        elif op == "removeSearch":
            before = len(sq.get("searches", []))
            sq["searches"] = [x for x in sq.get("searches", []) if x["id"] != c["id"]]
            if len(sq["searches"]) == before:
                raise ValueError(f"correction {c}: no such search")
            if not sq["searches"]:
                del sq["searches"]
        elif op == "addReward":
            sq.setdefault("rewards", []).append(c["reward"])
            w = c["reward"].get("warrior")
            if w and w not in {x["id"] for x in m["warriors"]}:
                m["warriors"] = sorted(m["warriors"] + [{"id": w, "name": lk.character_name(w.replace("_", "-"))}], key=lambda x: x["name"])
        elif op == "addCard":
            sq.setdefault("givesCards", []).append(c["card"])
        elif op == "note":
            sq["note"] = c["note"]
        else:
            raise ValueError(f"unknown correction op {op}")
        applied.append(f"{c['square']} {op}: {c.get('id') or c.get('reward', {}).get('id') or ''} ({c['source']})")
    return applied


def all_ids(m: dict) -> list[str]:
    ids = []
    for s in m["squares"]:
        ids.append(s["id"])
        ids += [x["id"] for k in ("rewards", "skulltulas", "searches") for x in s.get(k, [])]
    return ids


def check_lock(m: dict, allow_removed: bool, write: bool) -> list[str]:
    lock = HERE / "ids" / f"{m['id']}.txt"
    ids = all_ids(m)
    dupes = [i for i, n in Counter(ids).items() if n > 1]
    if dupes:
        raise ValueError(f"duplicate ids: {dupes[:5]}")
    locked = lock.read_text().split() if lock.exists() else []
    removed = sorted(set(locked) - set(ids))
    if removed and not allow_removed:
        raise SystemExit(f"{len(removed)} locked ids would disappear (progress is stored under them): "
                         f"{removed[:10]} — fix it, or pass --allow-removed if that's really intended")
    if write:
        lock.parent.mkdir(parents=True, exist_ok=True)
        lock.write_text("\n".join(sorted(set(locked) | set(ids))) + "\n")
    return removed


# ---------------------------------------------------------------------------- the report

def report(m: dict, sb: dict, lk: Lookups, cards_wiki: list[dict], accepted: list[dict]) -> tuple[list[str], list[str]]:
    """The report's open lines, and the lines corrections.json accepts (with the reason)."""
    lines = []
    sbsq = sb["squares"]
    names = {w["id"]: w["name"] for w in m["warriors"]}
    card_names = {c["id"]: c["name"] for c in m["cards"]}

    def ours(s: dict, slot: str) -> Counter:
        out = Counter()
        for r in s.get("rewards", []):
            if r["slot"] != slot:
                continue
            w = names.get(r.get("warrior", ""), "")
            t = r["type"]
            text = {"heart_container": f"Heart Container - {w}", "heart_piece": f"Piece of Heart - {w}",
                    "weapon": f"{r['name'].replace(' Lv. ', ' Lv.')} - {w}", "outfit": f"{r['name']} - {w}",
                    "warrior": w, "material": r["name"] if r["name"] == "Material" else f"{r['name']} Material"}.get(t, r["name"])
            out[norm(text)] += 1
        if slot == "clear":
            for c in s.get("givesCards", []):
                out[norm(card_names[c] + " Item Card")] += 1
        if slot == "treasure" and s.get("skulltulas"):
            out[norm("Gold Skulltula x2")] += 1
        return out

    def theirs(cells: dict, key: str) -> Counter:
        out = Counter()
        for line in cells.get(key, []):
            line = re.sub(r"\s*\([^)]*\)\s*$", "", line)
            line = re.sub(r"^(.*?) (Heart Container|Piece of Heart)$", r"\2 - \1", line)  # "Skull Kid Heart Container"
            line = re.sub(r"^Lv\.(\d\+?) (.*?) - ", r"\2 Lv.\1 - ", line)  # "Lv.4 Rental Hammer - Ravio"
            line = re.sub(r"^Epona (Lv\.)", r"Horse \1", line)
            out[norm(line).replace("conpass", "compass").replace("seachart", "seamap").replace("tearsoflight", "tearoflight")
                .replace("powerwrist", "powerbracelet")] += 1
        return out

    for s in m["squares"]:
        b = sbsq.get(s["altLabel"]) or sbsq.get(s.get("title", ""))
        tag = f"{s['label']} ({s['altLabel']})"
        if b is None:
            lines.append(f"{tag}: not in SBAllen's guide")
            continue
        mission = (s["battle"].title() + " Battle: " if s["battle"] else "") + s["mission"]
        # SBAllen puts the map rule under the mission, as a line of its own.
        their_mission = (b.get("Mission") or [""])[0]
        if norm(mission) != norm(their_mission):
            lines.append(f"{tag} mission: ours {mission!r} / SBAllen {their_mission!r}")
        their_rule = " ".join((b.get("Mission") or [])[1:]).replace("Engless", "Endless")
        if norm(s.get("rule") or "") != norm(their_rule):
            lines.append(f"{tag} rule: ours {s.get('rule')!r} / SBAllen {their_rule or None!r}")
        for key, field, unit in (("A-Rank KOs", "kos", ""), ("A-Rank Time", "minutes", " Minutes"), ("A-Rank Damage", "damage", "")):
            theirs_v = re.sub(r"\D", "", " ".join(b.get(key, [])))
            ours_v = str(s["aRank"].get(field, ""))
            if theirs_v != ours_v:
                lines.append(f"{tag} {field}: ours {ours_v or '-'} / SBAllen {theirs_v or '-'}")
        ours_search = sorted(norm(card_names[x["card"]]).rstrip("s") for x in s.get("searches", []))
        theirs_search = sorted(norm(re.sub(r"^ONLY\s*-?\s*", "", t.split(":")[0])).replace("seachart", "seamap").replace("whistle", "recorder").replace("tearsoflight", "tearoflight").rstrip("s") for t in b.get("Search", []))
        if ours_search != theirs_search:
            lines.append(f"{tag} searches: ours {ours_search} / SBAllen {theirs_search}")
        for slot, key in (("aRank", "A-Rank Victory"), ("clear", "Battle Victory"), ("treasure", "Treasure")):
            o, t = ours(s, slot), theirs(b, key)
            if o != t:
                lines.append(f"{tag} {slot}: only ours {sorted((o - t).elements())} / only SBAllen {sorted((t - o).elements())}")
    if sb.get("boss") and m.get("finalBoss") != square_id(*sballen_to_square(sb["boss"])):
        lines.append(f"final boss: ours {m.get('finalBoss')} / SBAllen {sb['boss']}")

    for c in m["cards"]:
        w = [x for x in cards_wiki if norm(x["name"]).startswith(norm(c["name"])) or norm(c["name"]) in norm(x["use"])]
        if c.get("anyOf"):
            continue  # a group: its cards are checked one by one
        if not any(m["id"] in x["maps"] for x in w):
            lines.append(f"card {c['id']}: Zelda Wiki's table doesn't list it on this map")
    wiki_here = [x["name"] for x in cards_wiki if m["id"] in x["maps"]]
    for name in wiki_here:
        if not any(norm(c["name"]) == norm(re.sub(r"\s*\(.*\)$", "", name)) for c in m["cards"]):
            lines.append(f"card {name!r}: on this map in Zelda Wiki's table, but no square uses or gives it")

    by_label = {s["label"]: s for s in m["squares"]}
    for weapon, label in NINTENDO_LIFE_WEAPONS.get(m["id"], []):
        has = [r for r in by_label.get(label, {}).get("rewards", []) if r.get("weapon") == weapon]
        if not has:
            where = [s["label"] for s in m["squares"] for r in s.get("rewards", []) if r.get("weapon") == weapon]
            lines.append(f"Nintendo Life: {weapon} at {label}; ours at {where}")
    for w, label in RPG_SITE_WARRIORS.get(m["id"], {}).items():
        has = [r for r in by_label.get(label, {}).get("rewards", []) if r["type"] == "warrior" and r.get("warrior") == w]
        if not has:
            where = [s["label"] for s in m["squares"] for r in s.get("rewards", []) if r["type"] == "warrior" and r.get("warrior") == w]
            lines.append(f"RPG Site: {w} at {label}; ours at {where}")
    open_lines, done = [], []
    for line in lines:
        hit = next((a for a in accepted if a["map"] == m["id"] and line.startswith(a["line"])), None)
        (done if hit else open_lines).append(line + (f"  [accepted: {hit['source']}]" if hit else ""))
    return open_lines, done


# ---------------------------------------------------------------------------- art and icons

def copy_art(m: dict):
    if "art" not in m:
        return
    hwh_id = MAPS[m["id"]]["hwh"]
    out = FILES / "hwde" / m["id"]
    out.mkdir(parents=True, exist_ok=True)
    wanted = {"preview.webp"}
    (out / "preview.webp").write_bytes(hwh(f"public/map-previews/{hwh_id}.webp"))
    for s in m["squares"]:
        name = f"{s['id']}.webp"
        wanted.add(name)
        (out / name).write_bytes(hwh(f"public/map-tiles/{hwh_id}/{s['altLabel'].replace('-', '')}.webp"))
    for stray in out.iterdir():
        if stray.name not in wanted:
            stray.unlink()


REWARD_ICONS = {
    "heart_container": "HW Heart Container Adventure Mode Icon.png",
    "heart_piece": "HW Piece of Heart Adventure Mode Icon.png",
    "skulltula": "HW Gold Skulltula Adventure Mode Icon.png",
}


def wiki_file(title: str) -> bytes | None:
    q = urllib.parse.urlencode({"action": "query", "titles": f"File:{title}", "prop": "imageinfo",
                                "iiprop": "url", "format": "json"})
    info = json.loads(fetch(f"{WIKI_API}?{q}", f"wiki/info/{slug(title)}.json"))
    page = next(iter(info["query"]["pages"].values()))
    if "imageinfo" not in page:
        return None
    return fetch(page["imageinfo"][0]["url"], f"wiki/files/{slug(title)}.png")


def fetch_icons(m: dict):
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    for kind, title in REWARD_ICONS.items():
        data = wiki_file(title)
        if data is None:
            raise SystemExit(f"Zelda Wiki has no {title}")
        (DRAWABLE / f"hwde_reward_{kind}.png").write_bytes(data)
    for c in m["cards"]:
        suffix = re.search(r"_(\d)$", c["id"])
        title = f"HWDE {c['name']} Icon{' ' + suffix.group(1) if suffix else ''}.png"
        data = wiki_file(title)
        if data is None:  # fall back to the Helper's own card art
            data = hwh(f"public/item-cards/{c['id'].replace('_', '-')}.png")
        (DRAWABLE / f"hwde_card_{c['id']}.png").write_bytes(data)


# ---------------------------------------------------------------------------- main

def self_test():
    src = "import x from 'y';\nexport const a = { id: 'A1', n: -2, ok: true, list: [1, 'two', { k: \"q\\\"s\" },], u: undefined, // c\n};"
    assert ts_literal(src) == {"id": "A1", "n": -2, "ok": True, "list": [1, "two", {"k": 'q"s'}], "u": None}
    try:
        ts_literal("export const a = { x: foo() };")
        raise AssertionError("code was accepted")
    except ValueError:
        pass
    assert hwh_id_to_square("F1") == (1, 6) and hwh_id_to_square("H8") == (8, 8) and hwh_id_to_square("P1") == (1, 16)
    assert prima_label(1, 6) == "A-6" and sballen_label(1, 6) == "F-1" and prima_label(8, 8) == "H-8"
    assert prima_to_square("G-14") == (7, 14) and sballen_to_square("A-2") == (2, 1)
    assert skulltula("KO 1000 enemies. Located just outside the west exit of North Entrance Keep.") == \
        {"condition": "KO 1000 enemies", "where": "Just outside the west exit of North Entrance Keep"}
    assert skulltula("Complete the first mission and KO 150 enemies. It is located in the same place as Gold Skulltula #1.")["where"] == \
        "Same place as the first"
    assert card_use("Adventure/Master Quest:Moves boulders.<br/>Great Sea/Master Wind Waker:Lifts heads.", "Great Sea") == "Lifts heads."
    assert card_use("GreatSea/Master Wind Waker (Hero's Bow): Destroys [[Cyclone]]s.<br/>Lorule (Bow): Beamos.", "Master Wind Waker") == "Destroys Cyclones."
    assert card_use("Great Sea/Master Wind Waker: Hits switches.<br/>Lorule: Posts.", "Lorule") == "Posts."
    assert card_use("Reveals all secrets within a battle square.", "Adventure") == "Reveals all secrets within a battle square."
    assert card_effects("Destroys Warships, removing Great Sea rules from that square.", "cannon") == ["rule"]
    assert card_name_on("Great Sea/Master Wind Waker (Hero's Bow): Cyclones.<br/>Lorule (Bow): Beamos.", "Lorule") == "Bow"
    assert card_name_on("Great Sea/Master Wind Waker (Hero's Bow): Cyclones.<br/>Lorule (Bow): Beamos.", "Great Sea") == "Hero's Bow"
    assert card_name_on("Burns bushes.", "Adventure") is None
    assert card_effects("Stops headwinds, removing locks on certain adjacent squares.", "windwaker") == ["path"]
    assert card_effects("Resets the map without waiting for the Moon and restores the time restriction.", "song_of_time") == ["other"]
    assert card_effects("Use all four on the clock tower to unlock a hidden part of the map.", "giant") == ["path"]
    assert card_effects("Burns bushes, unlocking a secret reward.", "candle") == ["reward"]
    assert card_effects("Opens caves, unlocking a secret reward or revealing enemies in that square.", "bomb") == ["reward", "enemies"]
    rs = [{"slot": "aRank", "type": "heart_container", "warrior": "zelda", "name": "Heart Container"},
          {"slot": "treasure", "type": "heart_container", "warrior": "zelda", "name": "Heart Container"}]
    assign_ids("r1c6", rs)
    assert [r["id"] for r in rs] == ["r1c6.hc_zelda", "r1c6.hc_zelda_2"]
    print("self-test ok")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--map", choices=sorted(MAPS), action="append")
    ap.add_argument("--check", action="store_true", help="only print the report; write nothing")
    ap.add_argument("--allow-removed", action="store_true", help="let locked ids disappear")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        self_test()
        return
    lk = Lookups()
    cards_wiki = wiki_cards()
    corrections = json.loads((HERE / "corrections.json").read_text())
    for map_id in args.map or sorted(MAPS):
        sb = sballen_page(map_id)
        m = convert(map_id, lk, cards_wiki, sb)
        applied = apply_corrections(m, corrections, lk)
        removed = check_lock(m, args.allow_removed, write=not args.check)
        lines, accepted = report(m, sb, lk, cards_wiki, [c for c in corrections if c["op"] == "accept"])
        print(f"== {m['name']}: {len(m['squares'])} squares, {len(all_ids(m))} ids")
        for a in applied:
            print(f"   corrected {a}")
        for r_ in removed:
            print(f"   REMOVED id {r_}")
        for a in accepted:
            print(f"   accepted {a}")
        print(f"   {len(lines)} open report line(s)")
        for line in lines:
            print(f"   - {line}")
        if not args.check:
            (FILES / "hwde").mkdir(parents=True, exist_ok=True)
            (FILES / "hwde" / f"{map_id}.json").write_text(json.dumps(m, indent=1, ensure_ascii=False) + "\n")
            copy_art(m)
            fetch_icons(m)


if __name__ == "__main__":
    main()
