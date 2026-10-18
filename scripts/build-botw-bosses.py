#!/usr/bin/env python3
"""
Build files/botw/bosses.json: every fixed overworld mini-boss spawn in Breath of the Wild.

    scripts/build-botw-bosses.py            download what isn't cached yet, then build
    scripts/build-botw-bosses.py --refresh  re-download everything first

Sources (see docs/overworld-bosses.md):
- radar.zeldamods.org, objmap's backend: every Hinox, Stalnox, Talus, Molduga and Lynel actor
  placed in MainField, with its HashId, map section, position and parameters.
- objmap.zeldamods.org game_files: MapTower.json (tower-region polygons), the map's static markers
  and their LocationMarker/Dungeon text (nearest-place hints).
- files/botw/compendium.json: each variant's Compendium name and drops.
- files/botw/koroks.json: calibrates which MapTower polygon is which tower region.
- The game's Master Mode rank-up table, HardModeManager::rankUpEnemy in the BotW decompilation
  (github.com/zeldaret/botw, src/Game/AOC/aocHardModeManager.cpp): gives each spawn's Master Mode variant.

Downloads are cached in build/bosses-cache. Exits non-zero if a sanity check fails.
"""

import json
import sys
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = ROOT / "shared/src/commonMain/composeResources/files/botw"
CACHE = ROOT / "build/bosses-cache"
HEADERS = {"User-Agent": "ZeldaGX boss dataset builder (github.com/TheYearIsTwentyGX/Zelda-Tracker)"}
RADAR = "https://radar.zeldamods.org"
OBJMAP = "https://objmap.zeldamods.org/game_files"

# Actor → (kind, Compendium id of the variant). The variant ids are the Compendium's own ids.
ACTORS = {
    "Enemy_Giant_Junior": ("hinox", "hinox"),
    "Enemy_Giant_Middle": ("hinox", "blue_hinox"),
    "Enemy_Giant_Senior": ("hinox", "black_hinox"),
    "Enemy_Giant_Bone": ("hinox", "stalnox"),
    "Enemy_Giant_Bone_AllDay": ("hinox", "stalnox"),
    "Enemy_Golem_Junior": ("talus", "stone_talus"),
    "Enemy_Golem_Middle": ("talus", "stone_talus_luminous"),
    "Enemy_Golem_Senior": ("talus", "stone_talus_rare"),
    "Enemy_Golem_Fire": ("talus", "igneo_talus"),
    "Enemy_Golem_Ice": ("talus", "frost_talus"),
    "Enemy_Golem_Fire_R": ("talus", "igneo_talus_titan"),
    "Enemy_Sandworm": ("molduga", "molduga"),
    "Enemy_SandwormR": ("molduga", "molduking"),
    "Enemy_Lynel_Junior": ("lynel", "lynel"),
    "Enemy_Lynel_Junior_Mountain": ("lynel", "lynel"),
    "Enemy_Lynel_Middle": ("lynel", "blue_maned_lynel"),
    "Enemy_Lynel_Senior": ("lynel", "white_maned_lynel"),
    "Enemy_Lynel_Dark": ("lynel", "silver_lynel"),
    "Enemy_Lynel_Gold": ("lynel", "golden_lynel"),
}

# Master Mode's rank-up: HardModeManager::rankUpEnemy (zeldaret/botw, src/Game/AOC/aocHardModeManager.cpp)
# swaps these actors for the next tier on a Master Mode file, unless the placement has
# DisableRankUpForHardMode or a UniqueName. Names match exactly, so Enemy_Giant_Bone (Stalnox),
# Enemy_Golem_Fire/Ice, Enemy_Sandworm and Enemy_Lynel_Junior_Mountain (Ploymus Mountain) never
# change. leoetlino's botw-re-notes (difficulty_scaling.md): the rank-up happens after world-level
# scaling and ignores LevelSensorMode.
MASTER_MODE_RANK_UP = {
    "Enemy_Giant_Junior": "Enemy_Giant_Middle",
    "Enemy_Giant_Middle": "Enemy_Giant_Senior",
    "Enemy_Golem_Junior": "Enemy_Golem_Middle",
    "Enemy_Golem_Middle": "Enemy_Golem_Senior",
    "Enemy_Lynel_Junior": "Enemy_Lynel_Middle",
    "Enemy_Lynel_Middle": "Enemy_Lynel_Senior",
    "Enemy_Lynel_Senior": "Enemy_Lynel_Dark",
    "Enemy_Lynel_Dark": "Enemy_Lynel_Gold",
}

KINDS = [
    {"id": "hinox", "name": "Hinox", "plural": "Hinoxes", "medal": "Medal of Honor: Hinox",
     "variants": ["hinox", "blue_hinox", "black_hinox", "stalnox"]},
    {"id": "talus", "name": "Talus", "plural": "Taluses", "medal": "Medal of Honor: Talus",
     "variants": ["stone_talus", "stone_talus_luminous", "stone_talus_rare", "igneo_talus", "frost_talus",
                  "igneo_talus_titan"]},
    {"id": "molduga", "name": "Molduga", "plural": "Moldugas", "medal": "Medal of Honor: Molduga",
     "variants": ["molduga", "molduking"]},
    {"id": "lynel", "name": "Lynel", "plural": "Lynels", "medal": None,
     "variants": ["lynel", "blue_maned_lynel", "white_maned_lynel", "silver_lynel"]},
]

# Which variants are added by The Champions' Ballad and don't count toward a Medal of Honor.
DLC_VARIANTS = {"igneo_talus_titan": "EX Champion Daruk's Song", "molduking": "EX Champion Urbosa's Song"}

# Spawns that are one fight but several actors, keyed by the actor kept as the spawn. The others
# are listed in `otherHashIds`; only one of them is ever in the world.
GROUPS = {
    # Coliseum Ruins: one actor per colour, created by the BattleArena_Level1..4 flags.
    4200912374: [1544805954, 237760997, 1578404536],
    # Ploymus Mountain: one actor before Vah Ruta's quest reaches the mountain, another after.
    1396251553: [2291678401],
}

NOTES = {
    4200912374: "Coliseum Ruins. The game places one Lynel per colour here and shows one: Red, Blue-Maned, "
                "White-Maned or Silver as more Divine Beasts are freed (BattleArena_Level1–4), each a tier "
                "higher on Master Mode (Blue-Maned up to Golden). Its colour follows that, not the world level.",
    1396251553: "Ploymus Mountain: the Lynel Sidon sends you to for Shock Arrows. The game swaps in a second "
                "actor once Vah Ruta's quest reaches the mountain; it is the same fight. Never changes colour.",
    2284947922: "Hyrule Castle, First Gatehouse. Its colour doesn't rise with the world level.",
    3515533418: "Hyrule Castle, Second Gatehouse. Its colour doesn't rise with the world level.",
    1288562591: "Master Mode only: not on Normal Mode files.",
    73882430: "Hyrule Castle Lockup. Unlike other Stalnoxes it is there day and night; beating it opens the "
              "chest with the Hylian Shield.",
    1943962273: "Hyrule Castle, East Passage.",
    1925665429: "Eventide Island, where you arrive with nothing and must clear the island to leave.",
    667761767: "Champions' Ballad: Lake Darman, during EX Champion Daruk's Song. Doesn't count toward the "
               "Medal of Honor: Talus.",
    1755179653: "Champions' Ballad: East Barrens, during EX Champion Urbosa's Song; back after every Blood "
                "Moon once beaten. Doesn't count toward the Medal of Honor: Molduga.",
}


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as r:
        return r.read()


def cached(name: str, url: str, refresh: bool):
    path = CACHE / name
    if refresh or not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(get(url))
    return json.loads(path.read_text())


def inside(ring, x, z) -> bool:
    hit = False
    for i in range(len(ring)):
        (x1, z1), (x2, z2) = ring[i], ring[i - 1]
        if (z1 > z) != (z2 > z) and x < (x2 - x1) * (z - z1) / (z2 - z1) + x1:
            hit = not hit
    return hit


def tower_keys(polygons, x, z):
    return [key for key, geoms in polygons.items() for poly in geoms
            if inside(poly[0], x, z) and not any(inside(h, x, z) for h in poly[1:])]


def master_mode_actor(name: str, params: dict) -> str:
    """The actor a Master Mode file places instead of [name]: one tier up, unless exempt."""
    if params.get("DisableRankUpForHardMode") or "UniqueName" in params:
        return name
    return MASTER_MODE_RANK_UP.get(name, name)


def main() -> int:
    refresh = "--refresh" in sys.argv
    compendium = {e["id"]: e for e in json.loads((FILES / "compendium.json").read_text())["entries"]}
    koroks = json.loads((FILES / "koroks.json").read_text())

    towers = cached("MapTower.json", f"{OBJMAP}/ecosystem/MapTower.json", refresh)
    polygons = {k: [g["coordinates"] for g in v] for k, v in towers.items()}
    markers = cached("static.json", f"{OBJMAP}/map_summary/MainField/static.json", refresh)["markers"]
    location_text = cached("LocationMarker.json", f"{OBJMAP}/text/StaticMsg/LocationMarker.json", refresh)
    dungeon_text = cached("Dungeon.json", f"{OBJMAP}/text/StaticMsg/Dungeon.json", refresh)

    # Which polygon is which region: the region most Koroks inside it are in.
    votes = defaultdict(Counter)
    for k in koroks["koroks"]:
        for key in tower_keys(polygons, k["x"], k["z"]):
            votes[key][k["region"]] += 1
    region_of = {key: v.most_common(1)[0][0] for key, v in votes.items()}
    assert sorted(region_of.values()) == sorted(koroks["regions"]), region_of

    # Named places, as the Korok hints use them: map locations, settlements, towers, labs and the
    # base game's shrines (Dungeon000–119; the Champions' Ballad's 120–135 are left out).
    places = []
    for kind, ms in markers.items():
        for m in ms:
            mid = m.get("MessageID")
            if kind == "Korok" or not mid:
                continue
            if kind == "Dungeon":
                if int(mid[len("Dungeon"):]) >= 120:
                    continue
                name = dungeon_text.get(mid)
            else:
                name = location_text.get(mid)
            if name:
                places.append((name, m["Translate"]["X"], m["Translate"]["Z"]))

    def nearest(x, z):
        return min(places, key=lambda p: (p[1] - x) ** 2 + (p[2] - z) ** 2)[0]

    actors = []
    for name in ACTORS:
        found = cached(f"search/{name}.json", f"{RADAR}/objs/MainField?q={name}&withMapNames=true&limit=-1", refresh)
        actors += [o for o in found if o["name"] == name]
    by_hash = {}
    for o in actors:
        by_hash[o["hash_id"]] = cached(f"obj/{o['map_name']}_{o['hash_id']}.json",
                                       f"{RADAR}/obj/MainField/{o['map_name']}/{o['hash_id']}", refresh)

    grouped = {h for others in GROUPS.values() for h in others}
    spawns = []
    for h, o in by_hash.items():
        if h in grouped:
            continue
        kind, variant = ACTORS[o["name"]]
        params = o["data"]["!Parameters"]
        x, y, z = o["pos"]
        keys = tower_keys(polygons, x, z)
        if len(keys) != 1:
            print(f"{h} {o['name']} is in tower polygons {keys}", file=sys.stderr)
            return 1
        entry = compendium[variant]
        spawn = {
            "hashId": h,
            "mapSection": o["map_name"],
            "kind": kind,
            "variant": variant,
            "region": region_of[keys[0]],
            "x": x, "y": y, "z": z,
            # Inside Hyrule Castle radar names the room, which says more than the nearest marker.
            "nearestPlace": o.get("location") or nearest(x, z),
            "drops": entry.get("drops", []),
        }
        if variant in DLC_VARIANTS:
            spawn["dlc"] = True
        if o.get("hard_mode"):
            spawn["masterModeOnly"] = True
        if params.get("LevelSensorMode") == 1:
            spawn["scalesWithWorldLevel"] = True
        if params.get("DisableRankUpForHardMode"):
            spawn["noMasterModeRankUp"] = True
        ranked = master_mode_actor(o["name"], params)
        if ranked != o["name"]:
            mm_kind, mm_variant = ACTORS[ranked]
            assert mm_kind == kind, (h, ranked)
            spawn["masterModeVariant"] = mm_variant
            spawn["masterModeDrops"] = compendium[mm_variant].get("drops", [])
        if h in GROUPS:
            spawn["otherHashIds"] = GROUPS[h]
        if h in NOTES:
            spawn["note"] = NOTES[h]
        spawns.append(spawn)

    kind_order = [k["id"] for k in KINDS]
    variant_order = [v for k in KINDS for v in k["variants"]]
    region_order = koroks["regions"]
    spawns.sort(key=lambda s: (kind_order.index(s["kind"]), region_order.index(s["region"]),
                               variant_order.index(s["variant"]), s["z"], s["x"]))

    counts = Counter((s["kind"], bool(s.get("dlc")), bool(s.get("masterModeOnly"))) for s in spawns)
    expected = {("hinox", False, False): 40, ("talus", False, False): 40, ("molduga", False, False): 4,
                ("lynel", False, False): 22, ("lynel", False, True): 1,
                ("talus", True, False): 1, ("molduga", True, False): 1}
    if dict(counts) != expected:
        print(f"Unexpected counts: {dict(counts)}", file=sys.stderr)
        return 1

    for s in spawns:
        variants = next(k["variants"] for k in KINDS if k["id"] == s["kind"])
        mm = s.get("masterModeVariant")
        if mm is not None and mm not in variants:
            print(f"{s['hashId']}: Master Mode variant {mm} isn't listed for {s['kind']}", file=sys.stderr)
            return 1

    kinds = []
    for k in KINDS:
        base = sum(1 for s in spawns if s["kind"] == k["id"] and not s.get("dlc") and not s.get("masterModeOnly"))
        kinds.append({**k, "medalTotal": base if k["medal"] else None})
    out = {
        "coordinates": koroks["coordinates"],
        "regions": region_order,
        "kinds": [{key: v for key, v in k.items() if v is not None} for k in kinds],
        "spawns": spawns,
    }
    (FILES / "bosses.json").write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n")
    for (kind, dlc, mm), n in sorted(counts.items()):
        print(f"{kind:8} {'DLC ' if dlc else ''}{'Master Mode ' if mm else ''}{n}")
    for s in spawns:
        if "masterModeVariant" in s:
            print(f"  Master Mode: {s['hashId']:>10} {s['region']:13} {s['nearestPlace']:24} "
                  f"{s['variant']} -> {s['masterModeVariant']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
