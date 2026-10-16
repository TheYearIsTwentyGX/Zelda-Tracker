# The Minish Cap: Main Quest & Items

`shared/src/commonMain/composeResources/files/mc/main_quest.json` backs The Minish Cap's *Main
Quest & Items* tracker. It uses the same format as BotW's `main_quest.json` (`MainQuestCatalog`),
filling in the three lists BotW leaves empty: `dungeons`, `items` and `tigerScrolls`. BotW's three
(`towers`, `divineBeasts`, `memories`) are absent, and the screen shows only the lists a game has.
`McMainQuestDatasetTest` checks it.

Nothing is fetched at runtime. The text was written for this app from the sources below.

## Sources

1. **Zelda Wiki** (zeldawiki.wiki), read through its MediaWiki API: the pages for every dungeon,
   boss, Element, item and Blade Brother, plus *Items in The Minish Cap*, *Tiger Scroll*,
   *Blade Brothers*, *Heart Container*, *Elemental Sanctuary*, *Melari*, *Rem*, *Belari*,
   *Librari* and the area pages (Castor Wilds, Wind Ruins, Royal Crypt, Veil Falls, Cloud Tops).
2. **StrategyWiki**, *The Legend of Zelda: The Minish Cap* `/Items`, `/Tiger Scrolls` and
   `/Bosses`: written independently of Zelda Wiki. It agrees on every item's location and every
   technique's teacher.
3. **Zelda Universe**, *Sword Techniques* guide: teachers, dojo locations, how to get in and the
   requirement for each technique and upgrade.
4. **Zelda Dungeon** wiki. Its pages refuse automated requests, so it was read only through
   search-result excerpts. Those confirm the White Sword coming from Melari after the Cave of
   Flames, the Bomb Bag from Belari, the four bottles, and Grimblade needing the Flame Lantern.

Zelda Fandom mirrors Zelda Wiki's text, so it wasn't counted as a separate source.

## Counts

- **6 dungeons**: Deepwood Shrine, Cave of Flames, Fortress of Winds, Temple of Droplets, Palace
  of Winds and Dark Hyrule Castle. Bosses are Big Green Chuchu, Gleerok, Mazaal, Big Octorok, the
  Gyorg Pair and Vaati (three forms). Every boss but Vaati leaves a Heart Container (Zelda Wiki,
  *Heart Container*: 3 + 11 from pieces + 5 bosses + 1 from Librari = 20).
- **4 Elements**: Earth (Deepwood), Fire (Cave of Flames), Water (Temple of Droplets) and Wind
  (Palace of Winds). The Fortress of Winds and Dark Hyrule Castle have none: the Wind Tribe took
  the Wind Element to the Cloud Tops. The Fortress gives the Ocarina of Wind instead.
- **38 items**, by category:

  | Category | Count | Entries |
  |---|---|---|
  | Swords | 5 | Smith's Sword, White Sword, White Sword (Two Elements), White Sword (Three Elements), Four Sword |
  | Shields | 2 | Small Shield, Mirror Shield |
  | Items | 13 | Gust Jar, Cane of Pacci, Mole Mitts, Flame Lantern, Pegasus Boots, Roc's Cape, Ocarina of Wind, Bomb Bag, Remote Bombs, Boomerang, Magical Boomerang, Bow, Light Arrows |
  | Equipment | 3 | Grip Ring, Power Bracelets, Flippers (the Quest Status screen's three) |
  | Quest items | 2 | Jabber Nut, Kinstone Bag |
  | Upgrades | 9 | 3 Big Bomb Bags, 3 Large Quivers, 3 Big Wallets |
  | Bottles | 4 | Trilby Highlands, Eastern Hills, Stockwell's Dog Food, Goron cave |

  Each upgrade is named by where it is found, since the three of a kind can come in any order.
  Left out: one-use quest items (Broken Picori Blade, Wake-Up Mushroom, Graveyard Key, Spare Key,
  Dog Food), dungeon keys, maps and compasses, and the collection items the Kinstone, figurine and
  Heart Piece trackers cover (Tingle Trophy, Carlov Medal, Mysterious Shells, Pieces of Heart).
- **8 Tiger Scrolls**: Spin Attack, Rock Breaker, Dash Attack and Down Thrust from Swiftblade;
  Roll Attack from Grayblade; Peril Beam from Waveblade; Sword Beam from Grimblade; and the Great
  Spin Attack from Swiftblade I, who asks for "all 7 Tiger Scrolls" first. That's where the "7"
  often quoted for the game comes from. The Great Spin Attack is the eighth scroll.
- **3 technique upgrades**, from Scarblade (faster Spin Attack charge), Splitblade (faster Split
  Gauge) and Greatblade (longer Great Spin Attack). They give no Tiger Scroll, so the tab counts
  them separately from the eight. Each dojo is a waterfall opened by a Kinstone Fusion with
  another brother: Grayblade, Grimblade and Waveblade respectively.
- **No technique costs Rupees.** The conditions are items, hearts or other scrolls.

## Fields

```json
{"id": "cave_of_flames", "number": 2, "name": "Cave of Flames", "region": "Mount Crenel",
 "access": "…", "item": "Cane of Pacci", "boss": "Gleerok", "element": "Fire",
 "rewards": ["Fire Element", "Heart Container"], "sanctuary": "…", "tip": "…"}
{"id": "light_arrows", "name": "Light Arrows (Bow of Light)", "category": "item",
 "region": "Cloud Tops", "how": "…", "missable": true}
{"id": "faster_spin", "name": "Faster Spin Attack", "teacher": "Scarblade", "location": "…",
 "requirement": "…", "effect": "…", "upgrade": true}
```

| Field | Notes |
|---|---|
| `dungeons[].sanctuary` | Set on the three dungeons whose Element changes the sword at the Elemental Sanctuary. Earth waits for Fire and the two are infused together (Zelda Wiki, *Earth Element*), so Deepwood Shrine has none. |
| `items[].category` | `sword`, `shield`, `item`, `equipment`, `quest`, `upgrade` or `bottle`; the Items tab groups by it, in that order. |
| `items[].missable` | Only the Light Arrows. Gregal dies if the Cloud Tops are reached through Veil Falls before he's saved. |
| `items[].postgame` | Only the Mirror Shield: Biggoron eats the Small Shield after Vaati is beaten. |
| `tigerScrolls[].upgrade` | The three technique upgrades. `MainQuestCatalog.scrolls` is the eight scrolls the tab counts; `techniqueUpgrades` is the rest. |

Progress is stored per save file under `mc_main_quest@file_1` … `@file_3`. `dungeons`, `items`
and `tigerScrolls` (holding the upgrades' ids too) are new fields on `MainQuestProgress` with
empty defaults, so BotW's stored progress reads unchanged.

## Known uncertainties

- **Rock Breaker's sword.** Zelda Wiki says Swiftblade teaches it once Link has the White Sword
  (his line is "Come again when you have a new sword in hand!"). Zelda Universe and Zelda Dungeon
  say the White Sword (Two Elements). The Sanctuary is the next stop after Melari anyway, so the
  entry gives Swiftblade's own words.
- **Waveblade's heart requirement.** Zelda Wiki quotes him asking for "more than 10 hearts".
  Zelda Universe and Zelda Dungeon list no requirement beyond reaching him with the Flippers. The
  entry keeps the quoted line.
- **Starting arrow capacity.** Zelda Wiki's *Arrow* page says 50, but its *Quiver* table makes
  the first Large Quiver raise it to 50. The Items tab gives bomb and Rupee capacities only.
- **Big Bomb Bag in Stockwell's Shop** isn't sold in the European version (Zelda Wiki, *Bomb
  Bag*). The entry says so; it isn't hidden for European players.
- **The Wind Ruins beanstalk Quiver.** Every source says only "fuse Kinstones with a Minish", so
  the entry doesn't name one.
- **Kinstone Bag.** Sources agree it's given in Hyrule Town after Deepwood Shrine, but not on who
  hands it over (Zelda's Palace says a travelling merchant), so the entry doesn't name anyone.
