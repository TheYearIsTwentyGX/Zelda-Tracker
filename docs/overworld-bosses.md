# BotW overworld bosses: the dataset

`shared/src/commonMain/composeResources/files/botw/bosses.json` lists every fixed overworld mini-boss spawn in Breath
of the Wild: Hinoxes (including Stalnoxes), Taluses, Moldugas and Lynels. `BotwBossDatasetTest` checks it.
`scripts/build-botw-bosses.py` builds it; run it again to rebuild from the sources below (downloads are cached in
`build/bosses-cache`, and `--refresh` re-downloads them).

## Sources

- **radar.zeldamods.org** (`/objs/MainField?q=<actor>` and `/obj/MainField/<section>/<hashId>`), objmap's backend:
  every boss actor placed in `MainField`, with its `HashId`, map section, position and parameters
  (`LevelSensorMode`, `IsHardModeActor`, `DisableRankUpForHardMode`). Actors in `AocField` (the Trial of the Sword)
  are left out: they aren't overworld spawns.
- **objmap `game_files`**: `ecosystem/MapTower.json` (the game's tower-region polygons),
  `map_summary/MainField/static.json` with the `StaticMsg/LocationMarker` and `StaticMsg/Dungeon` text (named places),
  and `names.json`, which confirms each actor's in-game name.
- **`files/botw/compendium.json`**: each variant's name, number and drops. A spawn's `variant` *is* its Compendium id,
  and its `drops` are copied from that entry, so they use the Compendium's names.
- **Zelda Wiki** (Medal of Honor, Kilton, Lynel, Coliseum Ruins, Molduking, Igneo Talus Titan) to cross-check counts
  and for what the map data doesn't say.

**Game data wins.** Where a wiki and the map data disagree, the map data is used. On counts they agree everywhere (see
below), so nothing had to be overruled.

## Counts

| Kind | Spawns | Medal of Honor counts | Zelda Wiki |
|---|---|---|---|
| Hinox | 40: 7 Hinox, 16 Blue, 7 Black, 10 Stalnox | 40 | "all 40 Hinoxes and Stalnoxes" |
| Talus | 41: 11 Stone, 12 Luminous, 7 Rare, 5 Igneo, 5 Frost, 1 Igneo Talus Titan (DLC) | 40 | "all 40 Taluses"; the Titan doesn't count |
| Molduga | 5: 4 Molduga, 1 Molduking (DLC) | 4 | "all four Moldugas"; the Molduking doesn't count |
| Lynel | 22, plus 1 on Master Mode files only | none | "22 Lynels … an additional Lynel on the Great Plateau during Master Mode" |

The variant counts come from the actors (`Enemy_Giant_Junior/Middle/Senior/Bone`, `Enemy_Golem_Junior/Middle/Senior/
Fire/Ice/Fire_R`, `Enemy_Sandworm(R)`, `Enemy_Lynel_Junior/Middle/Senior/Dark/Junior_Mountain`); objmap's `names.json`
gives `Enemy_Golem_Middle` = Stone Talus (Luminous) and `Enemy_Golem_Senior` = Stone Talus (Rare).

## Medals of Honor

The brief described them as The Master Trials' "EX Medals of Honor" quests. Zelda Wiki says otherwise: they are
**Key Items Kilton gives at Fang and Bone** (*Medal of Honor: Hinox*, *: Talus*, *: Molduga*) once every Hinox and
Stalnox, every Talus or every Molduga has been beaten; he will also say how many you've beaten so far ("[X] out of 40").
The wiki says they're offered after Calamity Ganon is defeated, and doesn't tie them to a DLC pack. There is no Lynel
medal. The tracker shows this as each tab's headline, e.g. "Hinox 12 / 40 · Medal of Honor: Hinox", counting only the
spawns the medal counts.

A boss beaten once counts: in-game, a boss already defeated shows "★ Defeated" next to its health bar even after it
comes back with a Blood Moon. That is what *Defeated* means in the tracker.

## DLC

The Champions' Ballad adds two bosses that are fixed `MainField` actors at one place each, so they are listed, marked
`dlc`, and left out of the medal counts (Zelda Wiki says neither counts):

- **Igneo Talus Titan** (`Enemy_Golem_Fire_R`), Lake Darman, during *EX Champion Daruk's Song*.
- **Molduking** (`Enemy_SandwormR`), East Barrens, during *EX Champion Urbosa's Song*; it returns after every Blood
  Moon once beaten.

Both only appear once their quest has started, so "fixed spawn" here means a fixed place, not always present.

## Lynel colours

Lynel colours are the least certain part of the data:

- **18 of the 22** have `LevelSensorMode` 1: their colour rises with the world level. The dataset records the colour
  the game places (`variant`), which is the weakest it can be, and sets `scalesWithWorldLevel`. A save far into the game
  may meet any of them as a stronger Lynel, up to Silver (Golden on Master Mode). The tracker tags them "Scales".
- **Coliseum Ruins**: four actors at one spot, one per colour (Red, Blue-Maned, White-Maned, Silver), each created by
  a flag `BattleArena_Level1`…`4`. Zelda Wiki says the Coliseum's enemies scale with the number of Divine Beasts freed.
  It is one spawn, listed as Red with the others in `otherHashIds`; exactly which beast count gives which colour isn't
  in the map data.
- **Ploymus Mountain**: two Red Lynel actors, one removed when Vah Ruta's quest reaches the mountain
  (`Water_Relic_Step1_1`) and one created after it (`RinelMt_Enter`). One spawn, never scaling.
- **Hyrule Castle**: the First Gatehouse (Blue-Maned) and Second Gatehouse (White-Maned) Lynels never scale.
- **Master Mode**: one extra White-Maned Lynel on the Great Plateau (`IsHardModeActor`), only listed on the Master Mode
  file. Master Mode also ranks many enemies up a tier; five spawns carry `DisableRankUpForHardMode`
  (`noMasterModeRankUp`). Whether the rank-up applies to Hinoxes, Taluses and Moldugas without that flag isn't settled
  here, so the detail screen only says the colour *may* be higher on a Master Mode file.

Hinox, Talus and Molduga actors all have `LevelSensorMode` 0: their variant is fixed.

## `bosses.json`

```json
{"hashId": 1925665429, "mapSection": "J-8", "kind": "hinox", "variant": "blue_hinox", "region": "Faron",
 "x": 4537.11, "y": 110.76, "z": 3701.95, "nearestPlace": "Eventide Island",
 "drops": ["Hinox Toenail", "Hinox Tooth", "Hinox Guts", "Roasted Bass", "…"],
 "note": "Eventide Island, where you arrive with nothing and must clear the island to leave."}
```

| Field | Meaning |
|---|---|
| `hashId` | The game's `HashId` for the boss actor: the stable id, and progress is stored under it. Unique, including `otherHashIds`. |
| `mapSection` | Map section whose map file holds the actor; with `hashId` it makes the objmap link `#/map/z7,<x>,<z>?id=MainField,<section>,<hashId>`. |
| `kind` | `hinox`, `talus`, `molduga` or `lynel` (the tracker's tabs). |
| `variant` | Compendium entry id of the boss placed there. For a scaling Lynel, its weakest colour. |
| `region` | Sheikah Tower region, from the game's MapTower polygons (point in polygon, as for Koroks). The polygons are matched to region names by which Koroks they contain. Every spawn falls in exactly one polygon. Hyrule Castle is in Central. |
| `x`, `y`, `z` | World position, as in `koroks.json`: x east, z south, y height. |
| `nearestPlace` | Closest named place on the map, measured flat, from the same sources as the Korok hints (base-game shrines only). Inside Hyrule Castle it is radar's room name (`Lockup`, `East Passage`, `First Gatehouse`…). |
| `drops` | The variant's Compendium drops. |
| `dlc` | Champions' Ballad; not counted toward a medal. |
| `masterModeOnly` | Only on Master Mode files. |
| `scalesWithWorldLevel` | `LevelSensorMode` 1. |
| `noMasterModeRankUp` | `DisableRankUpForHardMode`. |
| `otherHashIds` | Other actors that are the same fight (Coliseum Ruins, Ploymus Mountain). |
| `note` | Anything a player should know about this spawn. |

The file also has `regions` (tower order) and `kinds`: each kind's name, plural, its Medal of Honor and how many spawns
the medal counts, and its variants in display order. Spawns are ordered by kind, region, variant, then north to south.
