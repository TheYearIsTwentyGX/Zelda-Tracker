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
- **The game's Master Mode rank-up table**: `HardModeManager::rankUpEnemy` in the BotW decompilation
  ([zeldaret/botw](https://github.com/zeldaret/botw), `src/Game/AOC/aocHardModeManager.cpp`), with leoetlino's
  [botw-re-notes](https://github.com/leoetlino/botw-re-notes) (`difficulty_scaling.md`) for how it combines with
  world-level scaling. It gives each spawn's Master Mode variant (see [Master Mode](#master-mode)).
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
  file. Master Mode also ranks every Lynel up a tier (see below), so the scaling ones start a colour higher and can
  reach Golden.

Hinox, Talus and Molduga actors all have `LevelSensorMode` 0, so the world level never changes them. Only Master Mode
does.

## Master Mode

On a Master Mode file the game swaps many enemies for the next tier as it places them. This is
`HardModeManager::rankUpEnemy` in the decompilation:

- **Who it applies to:** any actor whose name starts with `Enemy`, unless its placement has `DisableRankUpForHardMode`
  or a `UniqueName`, or it is in a Test of Strength shrine.
- **The table matches names exactly.** For the bosses it is:

  | Normal Mode | Master Mode |
  |---|---|
  | `Enemy_Giant_Junior` (Hinox) | `Enemy_Giant_Middle` (Blue Hinox) |
  | `Enemy_Giant_Middle` (Blue Hinox) | `Enemy_Giant_Senior` (Black Hinox) |
  | `Enemy_Golem_Junior` (Stone Talus) | `Enemy_Golem_Middle` (Stone Talus (Luminous)) |
  | `Enemy_Golem_Middle` (Luminous) | `Enemy_Golem_Senior` (Stone Talus (Rare)) |
  | `Enemy_Lynel_Junior` → `_Middle` → `_Senior` → `_Dark` → `_Gold` | one colour up: Red → Blue-Maned → White-Maned → Silver → Golden |

- **What never changes:** Black Hinoxes, Stalnoxes (`Enemy_Giant_Bone`), Rare, Igneo and Frost Taluses, Moldugas, the
  Champions' Ballad bosses and the Ploymus Mountain Lynel (`Enemy_Lynel_Junior_Mountain`) aren't in the table.
- **Order:** botw-re-notes says the rank-up comes *after* world-level scaling and ignores `LevelSensorMode`. A
  scaling Lynel's weakest Master Mode colour is one above its Normal Mode one.
- **Exemptions:** none of the boss placements has a `UniqueName`. Five have `DisableRankUpForHardMode`
  (`noMasterModeRankUp`):
  - Hinoxes at Hanu Pond, Uten Marsh and Rabella Wetlands;
  - the Great Plateau's Stone Talus (Hopper Pond);
  - Master Mode's own Great Plateau Lynel.

`scripts/build-botw-bosses.py` applies the table to each placement's actor and parameters. Where the result differs,
it writes `masterModeVariant` and `masterModeDrops`. `BossCatalog.spawnsFor` hands a Master Mode file those variants
(`BossSpawn.onFile`), so the tracker, its search and filters, the progress overview and the map all show the boss the
file really has. The detail screen says what the boss is in the other mode. The ids don't change, so a spawn's
progress is the same key on every file.

| Variant | Normal Mode | Master Mode |
|---|---|---|
| Hinox / Blue / Black / Stalnox | 7 / 16 / 7 / 10 | 1 / 7 / 22 / 10 |
| Stone / Luminous / Rare Talus | 11 / 12 / 7 | 1 / 10 / 19 |
| Igneo / Frost Talus, Titan | 5 / 5 / 1 | 5 / 5 / 1 |
| Molduga, Molduking | 4, 1 | 4, 1 |
| Red / Blue-Maned / White-Maned / Silver Lynel | 7 / 7 / 8 | 1 / 6 / 8 / 8 (23 with the extra Lynel) |

## Tier audit (October 2026)

A user reported Gerudo Taluses listed as Stone Talus (Luminous) that were Rare in the game. Every spawn's variant was
checked again:

1. **Normal Mode:** each was compared with its radar actor (`Enemy_Golem_Middle` and so on) and objmap's
   `names.json`.
2. **Master Mode:** each was compared with the rank-up table above.
3. **Counts:** checked against Kilton's totals (Hinox 40, Talus 40, Molduga 4) and Zelda Wiki's 22 Lynels plus one
   in Master Mode.

**Result:** no Normal Mode variant was wrong, and no count was off. The Gerudo Taluses at Rutimala Hill, Mount
Nabooru and Gerudo Canyon Stable are `Enemy_Golem_Middle`, Luminous, in the map data, with `LevelSensorMode` 0 and no
exemption.

**What was missing was Master Mode.** The dataset gave one variant per spawn, so a Master Mode file showed the Normal
Mode one. On Master Mode those three, like all 12 Luminous Taluses, are Rare, which is what the user met. 64 spawns
differ by mode; they are listed below, and `BotwBossDatasetTest` pins the Gerudo Taluses and the rank-up rule.
Before this, the detail screen said a Master Mode boss "may be stronger".

Smaller changes made with the audit:

- The Hyrule Castle gatehouse notes said "Never changes colour". They now say the colour doesn't rise with the world
  level, as Master Mode ranks those Lynels up too.
- The Coliseum note says each colour is a tier higher on Master Mode.

<details>
<summary>Every spawn whose variant differs on Master Mode</summary>

**Hinox** (21)

| Region | Near | HashId | Normal Mode | Master Mode |
|---|---|---|---|---|
| Hateno | Phalian Highlands | 1833514674 | Hinox | Blue Hinox |
| Hateno | Purifier Lake | 3350013125 | Hinox | Blue Hinox |
| Hateno | Mount Dunsel | 373348007 | Hinox | Blue Hinox |
| Hateno | Trotter's Downfall | 3572226454 | Blue Hinox | Black Hinox |
| Lanayru | Kincean Island | 1602515170 | Hinox | Blue Hinox |
| Lanayru | Ralis Pond | 2083136285 | Blue Hinox | Black Hinox |
| Lanayru | Talus Plateau | 3706280648 | Blue Hinox | Black Hinox |
| Lanayru | Rucco Maag Shrine | 3831867213 | Blue Hinox | Black Hinox |
| Faron | Tobio's Hollow | 1903632265 | Hinox | Blue Hinox |
| Faron | Kamah Plateau | 4099815924 | Hinox | Blue Hinox |
| Faron | Eventide Island | 1925665429 | Blue Hinox | Black Hinox |
| Lake | Herin Lake | 3272527560 | Blue Hinox | Black Hinox |
| Central | Applean Forest | 1266282060 | Blue Hinox | Black Hinox |
| Central | Bottomless Swamp | 3744176720 | Blue Hinox | Black Hinox |
| Central | Dah Kaso Shrine | 1949698080 | Blue Hinox | Black Hinox |
| Ridgeland | Hyrule Ridge | 3627901807 | Blue Hinox | Black Hinox |
| Ridgeland | West Hyrule Plains | 2472409077 | Blue Hinox | Black Hinox |
| Woodland | Thyphlo Ruins | 2530315670 | Blue Hinox | Black Hinox |
| Woodland | Lake Mekar | 3637193649 | Blue Hinox | Black Hinox |
| Woodland | Irch Plain | 4080554254 | Blue Hinox | Black Hinox |
| Akkala | Akkala Parade Ground Ruins | 1384742674 | Blue Hinox | Black Hinox |

**Talus** (22)

| Region | Near | HashId | Normal Mode | Master Mode |
|---|---|---|---|---|
| Dueling Peaks | Mable Ridge | 2009332164 | Stone Talus | Stone Talus (Luminous) |
| Dueling Peaks | South Nabi Lake | 4234397911 | Stone Talus | Stone Talus (Luminous) |
| Dueling Peaks | West Necluda | 1450825465 | Stone Talus | Stone Talus (Luminous) |
| Dueling Peaks | Dueling Peaks | 3873221297 | Stone Talus | Stone Talus (Luminous) |
| Dueling Peaks | Outpost Ruins | 2367737901 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Hateno | Ovli Plain | 139564765 | Stone Talus | Stone Talus (Luminous) |
| Hateno | Squabble River | 3688513041 | Stone Talus | Stone Talus (Luminous) |
| Hateno | Mapla Point | 3529818719 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Lanayru | Ja'Abu Ridge | 1063952470 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Faron | Floria Falls | 4097205930 | Stone Talus | Stone Talus (Luminous) |
| Faron | Gama Cove | 3707687090 | Stone Talus | Stone Talus (Luminous) |
| Faron | Martha's Landing | 3817875409 | Stone Talus | Stone Talus (Luminous) |
| Lake | Deya Lake | 481698249 | Stone Talus | Stone Talus (Luminous) |
| Lake | Oseira Plains | 491623064 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Central | Crenel Hills | 853217538 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Central | Lake Kolomo | 3812413946 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Ridgeland | Tanagar Canyon | 3605751469 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Wasteland | Koukot Plateau | 2827830463 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Wasteland | Champion's Gate | 975119268 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Gerudo | Rutimala Hill | 3900382371 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Gerudo | Mount Nabooru | 3273877672 | Stone Talus (Luminous) | Stone Talus (Rare) |
| Gerudo | Gerudo Canyon Stable | 372172601 | Stone Talus (Luminous) | Stone Talus (Rare) |

**Lynel** (21)

| Region | Near | HashId | Normal Mode | Master Mode |
|---|---|---|---|---|
| Hateno | Lanayru Road - East Gate | 1492521341 | Lynel | Blue-Maned Lynel |
| Lanayru | Tarm Point | 438554606 | Blue-Maned Lynel | White-Maned Lynel |
| Faron | Keelay Plain | 232962450 | Lynel | Blue-Maned Lynel |
| Lake | Oseira Plains | 1819222204 | Lynel | Blue-Maned Lynel |
| Lake | Oseira Plains | 459804714 | Lynel | Blue-Maned Lynel |
| Central | Coliseum Ruins | 4200912374 | Lynel | Blue-Maned Lynel |
| Central | First Gatehouse | 2284947922 | Blue-Maned Lynel | White-Maned Lynel |
| Central | Second Gatehouse | 3515533418 | White-Maned Lynel | Silver Lynel |
| Ridgeland | Shae Loya Shrine | 3391176708 | Lynel | Blue-Maned Lynel |
| Hebra | Hebra West Summit | 1423341458 | Blue-Maned Lynel | White-Maned Lynel |
| Hebra | N. Tabantha Snowfield | 3788100745 | White-Maned Lynel | Silver Lynel |
| Hebra | Mount Drena | 2378625228 | White-Maned Lynel | Silver Lynel |
| Hebra | N. Tabantha Snowfield | 329409903 | White-Maned Lynel | Silver Lynel |
| Woodland | Aldor Foothills | 4192271973 | Blue-Maned Lynel | White-Maned Lynel |
| Woodland | East Deplian Badlands | 4101701470 | White-Maned Lynel | Silver Lynel |
| Woodland | Deplian Badlands | 3371712234 | White-Maned Lynel | Silver Lynel |
| Woodland | Drenan Highlands | 4246046905 | White-Maned Lynel | Silver Lynel |
| Akkala | Akkala Wilds | 3396778219 | Blue-Maned Lynel | White-Maned Lynel |
| Akkala | Akkala | 1529588945 | Blue-Maned Lynel | White-Maned Lynel |
| Gerudo | Kuh Takkar Shrine | 86620264 | Blue-Maned Lynel | White-Maned Lynel |
| Gerudo | Gerudo Highlands | 1309263371 | White-Maned Lynel | Silver Lynel |

</details>

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
| `variant` | Compendium entry id of the boss placed there on Normal Mode. For a scaling Lynel, its weakest colour. |
| `region` | Sheikah Tower region, from the game's MapTower polygons (point in polygon, as for Koroks). The polygons are matched to region names by which Koroks they contain. Every spawn falls in exactly one polygon. Hyrule Castle is in Central. |
| `x`, `y`, `z` | World position, as in `koroks.json`: x east, z south, y height. |
| `nearestPlace` | Closest named place on the map, measured flat, from the same sources as the Korok hints (base-game shrines only). Inside Hyrule Castle it is radar's room name (`Lockup`, `East Passage`, `First Gatehouse`…). |
| `drops` | The variant's Compendium drops. |
| `dlc` | Champions' Ballad; not counted toward a medal. |
| `masterModeOnly` | Only on Master Mode files. |
| `scalesWithWorldLevel` | `LevelSensorMode` 1. |
| `noMasterModeRankUp` | `DisableRankUpForHardMode`. |
| `masterModeVariant` | The variant on a Master Mode file, when the rank-up changes it. |
| `masterModeDrops` | That variant's Compendium drops. |
| `otherHashIds` | Other actors that are the same fight (Coliseum Ruins, Ploymus Mountain). |
| `note` | Anything a player should know about this spawn. |

The file also has `regions` (tower order) and `kinds`: each kind's name, plural, its Medal of Honor and how many spawns
the medal counts, and its variants in display order. Spawns are ordered by kind, region, variant, then north to south.

## On the map

The map's **Bosses** layer (`BossLayer` in `map/data/BotwMapLayers.kt`) draws one marker per spawn the active file
has, at the spawn's `x`/`z`. These are radar's positions for the boss actor itself, so nothing had to be matched by
region or kind. A grouped spawn (Coliseum Ruins, Ploymus Mountain) is drawn at its kept actor, the same spot as the
others. Markers show each kind's icon (see `docs/map-icons.md`) and are named with the file's variant. They follow
the tracker's *Hide unfound bosses*, and their sheet opens the spawn's page in the tracker.

