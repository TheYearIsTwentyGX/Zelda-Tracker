# TotK Shrines of Light and Lightroots

`shared/src/commonMain/composeResources/files/totk/shrines.json` backs the Tears of the Kingdom
*Shrines & Lightroots* tracker. It uses the same format as BotW's `shrines.json` (`ShrineCatalog`)
plus the fields below. `TotkShrineDatasetTest` checks it.

Nothing is fetched at runtime. The JSON was built offline by joining a datamine with the wikis,
using scripts that aren't committed (as with the BotW Koroks).

## Counts

- **152 shrines**, Dungeon000–151 in the game data. Zelda Wiki, Zelda Fandom and Game8 all list
  152, with the same spellings.
- **120 Lightroots**, CheckPoint000–147 in the game data (with gaps), one under every shrine that isn't on a sky
  island.
- **32 sky shrines, and they are exactly the ones with no Lightroot.** Cave shrines do have one.
- **Layers:** 32 sky, 91 surface, 29 cave.

## How a Lightroot is linked to its shrine

`Lightroot.shrine` holds the shrine's id; `ShrineCatalog.lightrootOf` / `shrineOf` resolve both
ways. Three independent checks agree on all 120 pairs:

1. The game's own numbering: `CheckPointNNN` sits under `DungeonNNN`.
2. The nearest shrine entrance in x/z.
3. The name. A Lightroot's name is its shrine's name reversed character for character, hyphens
   included, then re-capitalised: Oromuwak Shrine → **Kawumoro** Lightroot, Jochi-iu → Ui-ihcoj.
   No name has an apostrophe or an accent.

112 Lightroots share their shrine's x/z to within 0.01 units. The furthest apart are Mayaotaki →
Ikatoayam (24 units), Turakawak → Kawakarut (21), Igashuk → Kuhsagi (17) and Motsusis → Sisustom
(10). Each is still nearest its own shrine.

## Fields

```json
{"id": "oromuwak", "name": "Oromuwak", "region": "Lindor's Brow", "area": "Tabantha Frontier",
 "trial": "…", "type": "puzzle", "quest": null, "location": "…", "layer": "surface",
 "x": -3077.6, "y": 348.06, "z": -1616.87}
{"id": "kawumoro", "name": "Kawumoro", "shrine": "oromuwak", "x": …, "y": …, "z": …}
```

| Field | Source | Notes |
|---|---|---|
| `name` | Game text (`LocationMarker` msbt, from the objmap-totk repo) | Matches Zelda Wiki for all 152 + 120. |
| `region` | Datamined Skyview Tower region | See [Grouping](#grouping). |
| `area` | Datamined map area, e.g. "Hebra Mountains Sky" | Cave shrines have no named cave area, so they take the surface area at the same x/z. |
| `layer` | Datamined: the map file the entrance actor is placed in (`Sky__*`, grid cell, `Cave__*`) | Zelda Wiki marks 5 of the 29 cave shrines as surface: Suariwak, Joniu, Jiotak, Chichim and O-ogim. The data puts them in cave maps. |
| `trial` | Zelda Wiki shrine pages | The in-game subtitle: "Rauru's Blessing", "Proving Grounds: Smash", "Combat Training: Archery", or a puzzle title. |
| `type` | From the subtitle | `blessing` = Rauru's Blessing (51). `combat` = Proving Grounds (15) and Combat Training (7). Everything else is `puzzle` (79), including Siyamotsus ("Unlit Blessing", which has a torch puzzle). |
| `quest` | Zelda Wiki's Shrine Quest list | 31 quests, each tied to one shrine. Side adventures that only gate a shrine (Yiga Clan Exam etc.) aren't counted. |
| `location` | Written for this app from the wiki descriptions | At most 110 characters. |
| `x`, `y`, `z` | radar-totk.zeldamods.org, objmap's TotK backend | The `Translate` of the shrine's `DgnObj_ZonauShrineEntrance_A_01` and the Lightroot's `FldObj_MinusFieldCheckPoint_A_01` actor, rounded to 0.01. |

### Coordinates

Game world units, as for the BotW Koroks: **x** grows east, **z** grows south and **y** is height,
with the origin at the map centre. objmap TotK's "Go to coordinates" box takes the same numbers.

- Lightroots are at y −902 to −246, in the Depths.
- Sky shrines are at 534 to 2404, and surface shrines at 21 to 896. The ranges overlap, so the
  layer comes from the map file, not from a height cut-off.

## Grouping

Shrines are grouped by the **15 Skyview Tower regions**. A Lightroot is listed under its shrine's
region. Why:

- They are the game's own map areas (`MapTower`), so every shrine's region is datamined, not
  editorial. For the 91 surface shrines the radar backend's own `region` field agrees. Sky and
  cave shrines take the tower region at their x/z.
- They match the BotW tracker, which groups by Sheikah Tower, so the two games read the same.
- The groups are even, 5 to 14 shrines each. Zelda Wiki's 8 regions run from 5 (Faron) to 41
  (Central Hyrule), and Game8's 11 mix regions with the Great Sky Island.

`area` keeps the finer place name, and search matches it. The order is Lookout Landing and
Hyrule Field first, then clockwise from the north: Thyphlo Ruins, Eldin Canyon, Ulri Mountain,
Upland Zorana, Mount Lanayru, Sahasra Slope, Rabella Wetlands, Popla Foothills, Gerudo Canyon,
Gerudo Highlands, Lindor's Brow, Rospro Pass, Pikida Stonegrove.

## Lights of Blessing

The `upgrades` block, with sources from Zelda Wiki (Light of Blessing, Heart Container, Life
Gauge), Zelda Fandom and Game8:

- One Light of Blessing per shrine, 152 in all. Four buy one Heart Container or Stamina Vessel at
  a Goddess Statue.
- Link starts with 3 hearts and 1 stamina wheel. The caps are 40 hearts and 3 wheels, which is 10
  vessels.
- **`bossHearts`: 6**, from Colgera, Marbled Gohma, Mucktorok, Queen Gibdo, the Seized Construct
  and Phantom Ganon at Hyrule Castle ("Crisis at Hyrule Castle"). No tracker records these, so
  they are entered by hand. Game8's text says five, but its own maximums only work with six.
- **`freeUpgrade`**: "A Call from the Depths" gives a Heart Container or a Stamina Vessel, your
  choice, for no Lights.
- **Both can't be maxed.** 38 + 6 + 1 = 45 upgrades against 37 + 10 = 47. The best you can do is
  40 hearts with 8 vessels, or 38 hearts with 10.
- **No Lights outside shrines.**
  - The green objects carried to reveal hidden shrines are *Mysterious Crystals*. The shrine they
    reveal gives the usual one Light.
  - *Crystallized Charges* are Zonai currency: they buy Energy Wells at the Crystal Refineries.
  - *Sage's Wills* also go to Goddess Statues, but they power up the Sages.
- **Gloom** removes hearts only until they recover. It never lowers the maximum, so the planner
  ignores it.
- **Horned Statue** (Royal Hidden Passage under Lookout Landing): it buys a heart or vessel for 100
  rupees and sells your choice back for 120. It never trades Lights.

## Known uncertainties

- **Queen Gibdo's Heart Container** is sourced from Zelda Fandom only (Zelda Wiki's reward field
  is blank). The six-boss total is consistent with every source's maximums.
- **Tower region for sky and cave shrines** assumes a tower's map area covers the sky above and
  the caves below it. The game only tags surface placements with a tower.
- **Trial subtitles where sources differ:**
  - Siyamotsus: "Unlit Blessing" on its own Zelda Wiki page, Fandom and Game8. Zelda Wiki's list
    says "Rauru's Blessing".
  - Anedamimik: "A Retraced Path" (the wiki's list drops the "A").
  - Capitalisation varies across sources for Ijo-o and Sahirow. The wiki's shrine pages were used.
- **Rakashog's sky archipelago:** Zelda Wiki's list says North Gerudo; its shrine page says East.
  The location hint just says the Gerudo Sky Archipelago.
