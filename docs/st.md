# Spirit Tracks

Spirit Tracks's trackers are checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): eleven JSON files in
`shared/src/commonMain/composeResources/files/st/`, listed in `core/Games.kt` after a Progress
Overview. `StDatasetTest` checks the counts below; `ChecklistDatasetsTest` checks the format. No
engine change was needed. Nothing is fetched at runtime.

## Save files

Two: **File 1** and **File 2**. Nintendo's European manual: "You can have a maximum of two save
files at any time. If both are full, you must delete one if you want to begin a new game." Its
copy option copies a file "into the other save slot". Zelda Wiki's *Saving* page has no Spirit
Tracks section. The placeholder in `Games.kt` already had two, so the ids `file_1` and `file_2` are
unchanged.

## Files and counts

| File | Tracker | Sections (ids) | Count |
|---|---|---|---|
| `main_quest.json` | Main Quest | `quest` | 27 steps in one chain; summaries: Temples cleared 5, Rail Maps 4, Lokomo duets 5 |
| `items.json` | Items & Equipment | `equipment`, `upgrades`, `techniques` | 12 items (Sword and Bow with 2 tiers), Bomb Bag 10/20/30 and Quiver 20/30/50 as tiers, both Swordsman's Scrolls; summaries: Bombs 30, Arrows 50 |
| `heart_containers.json` | Heart Containers | `containers` | 13 (5 bosses, 8 others); Hearts `3 + containers` = 16 |
| `force_gems.json` | Force Gems | `gems` | 20 |
| `deliveries.json` | Passengers & Cargo | `deliveries` | 25 runs; summaries: Passengers 15, Cargo 10 |
| `rabbits.json` | Rabbits | `rabbits`, `bunnio_rewards` | 50 (10 per realm) and Bunnio's 8 prizes |
| `stamps.json` | Stamp Stations | `stamps`, `stamp_rewards` | 20 stamps and Niko's 3 rewards |
| `songs.json` | Songs | `flute_songs` | 5 |
| `stations.json` | Stations & Warp Gates | `stations`, `warp_gates` | 22 stations, Warp Gates A–H |
| `train_parts.json` | Train Parts | `spirit_train` … `golden_train` | 8 sets × 4 parts; summary: Complete sets 8 |
| `minigames.json` | Minigames & Secrets | `challenges` | 15 challenges, 24 steps |

Ids are unique across all eleven files, and `StDatasetTest` checks this. They're named by giver or
place, never by number, because the sources number Force Gems and rabbits in different orders:
Force Gems are `gem_<giver>`, deliveries `ride_<passenger>` or `cargo_<goods>_<recipient>`, stamps
`stamp_<place>`, stations `station_<place>`, gates `warp_gate_<letter>`, rabbits
`<grass|snow|ocean|fire|sand>_<place>` and hearts `<source>_heart`. Each Force Gem's notes give
all three sources' numbers.

Regions follow Zelda Wiki's *Locations in Spirit Tracks*, in the order they're reached: Forest
Realm (with Hyrule Castle and Castle Town), Tower of Spirits, Snow, Ocean, Fire and Sand Realm, and
the Dark Realm. Each file declares only the ones it uses. Items & Equipment, Songs and Train Parts
have no regions, only an `area`.

Items are in the order you'd suggest doing them, since the overview's *Next* is the first one not
done. The rabbits, stamps, Force Gems and deliveries are in the order they open up, not grouped by
realm; the region cards still group them. The Main Quest is one chain.

### Links between files

`requires` can't cross files, so links are by name. Every Force Gem is a delivery's reward,
written `Force Gem: <the gem's name>`, and `StDatasetTest` checks that the twenty names match
`force_gems.json` exactly. The boss Heart Containers are the bosses named in the temples' notes
(`Boss: Stagnox.`), which the test also checks. Minigame prizes that count elsewhere say so in
`reward` ("Heart Container (also in Heart Containers)").

### What's tracked, and what isn't

speedrun.com's 100% rules for Spirit Tracks ask for 16 hearts, all Force Gems, every station
discovered, every warp gate active, all rabbits, the best quiver and bomb bag, every song, every
stamp, both Swordsman's Scrolls, the Shield of Antiquity, the Engineer's Clothes and Malladus. Each
has a tracker, which is why Stations & Warp Gates and Songs exist.

- **Rail Maps** are four (Forest, Snow, Ocean, Fire), one per climb of the Tower of Spirits. Spirit
  Tracks has no "Lost Rail Map glyphs": the Sand Realm's tracks come from Anjean's Force Gem, which
  isn't one of the 20 (the Prima guide, quoted on Zelda Wiki). They're Main Quest steps with a
  summary.
- **The Tower of Spirits** is six climbs, each a Main Quest step, as speedrun.com splits it. Floor
  numbers are only in notes because the sources disagree.
- **Songs** are the five on the Collection screen. The Lokomo duets (Songs of Restoration) are
  played once and aren't on the Collection screen, so they're Main Quest steps with their own total.
- **Deliveries overlap Force Gems** on purpose: every gem is a delivery. Passengers & Cargo adds
  the story runs (Alfonzo, Kenzo to the bridge, Kagoron's Mega Ice), Kenzo to Anouki Village and
  Teacher (Rupees only).
- **Train Parts** has all 32, the Spirit Train's four story parts included, so *Complete sets*
  reaches 8. Each part's cost in Treasure is in its `howTo`.
- **Treasure isn't tracked.** Its 16 kinds are currency: random finds sold to Linebeck III and
  spent on train parts. A "found each kind" list would be ticked in the first hour. The fixed
  super-rare prizes the Golden Train needs (Alchemy Stones, Regal Rings) are in Minigames &
  Secrets. Every part together costs 200 pieces: Demon Fossil 25, Stalfos Skull 19, Star Fragment
  9, Bee Larvae 18, Wood Heart 19, Dark Pearl Loop 12, Pearl Necklace 21, Ruto Crown 13, Dragon
  Scale 14, Pirate Necklace 14, Palace Dish 7, Goron Amber 7, Mystic Jade 8 (9 by Zelda Wiki),
  Ancient Gold Piece 10, Alchemy Stone 2, Regal Ring 2.
- **No bottles.** Potions sit in two potion slots.
- **Left out:** dungeon maps, Small and Boss Keys, Tears of Light, Prize Postcards (a lottery),
  letters, random Treasure chests, Battle Mode, Tag Mode trading, the Recruit Uniform and the Royal
  Engineer's Certificate (Main Quest rewards), and repeat prizes.

### Missable

Nothing is missable for good. Zelda Wiki's *Permanently Missable Items* has no Spirit Tracks
section, and no guide flags anything. The Song of Healing can be learned at later temple entrances
and the Song of Discovery at the Trading Post if skipped.

## Sources

- **Zelda Wiki** (zeldawiki.wiki, read as wikitext through its API): the game page, Locations in
  Spirit Tracks, Tower of Spirits, Force Gem (with its Prima-numbered list), Stamp Station, Stamp
  Book, Niko, Rabbit and the five rabbit kinds, Rabbitland Rescue, Bunnio, Lokomo, Spirit Flute and
  the five songs, Song of Restoration, Rail Map, Spacetime Gate, Station, Train Car, Treasure, Items
  in Spirit Tracks, Bomb Bag, Quiver, Shield of Antiquity, Lokomo Sword, Bow of Light, Engineer's
  Clothes, Beedle's Air Shop and its cards, Russell, Linebeck III, Goron Country Store, Goron Target
  Range, Pirate Hideout, Take 'Em All On!, the hidden stations, Passenger, Cargo, both Swordsman's
  Scrolls and Permanently Missable Items.
- **Zelda Dungeon**: the twelve-chapter walkthrough and its Force Gems, Heart Containers, Items,
  Rabbits, Songs, Stamp Stations, Train Parts and Treasure pages, read from Wayback Machine copies
  (2025) because the live site blocks automated fetches.
- **IGN**'s Spirit Tracks guide wiki: Force Gems, Heart Containers, Rabbits, Train Parts, Equipment
  & Items, Stamp Stations, the minigame and hidden-station pages, Side Quests and Collectibles.
- **StrategyWiki**: the walkthrough, Heart Containers, Rabbits, Stamp Stands, Train Parts, the
  songs, and Weapons and items.
- **speedrun.com**'s API for the 100% rules, and **Nintendo's manual** for the save files.

## Discrepancies

### Counts

None that change a total. Every source gives 20 Force Gems (Zelda Wiki also counts Anjean's as a
21st, but Prima, which it quotes, doesn't), 13 Heart Containers and 16 hearts at most, 50 rabbits
(10 per realm), 20 Stamp Stations, 5 songs, 4 Rail Maps, 8 train sets of 4 parts and 8 warp gates.

| Topic | Sources | Used |
|---|---|---|
| Force Gem numbering | Three different orders: Prima/Zelda Wiki (roughly by Rail Map), Zelda Dungeon (walkthrough order), IGN (map order) | Named by giver; all three numbers in each gem's notes |
| Rabbit numbering | Zelda Wiki (Prima), Zelda Dungeon's rabbit page and its walkthrough number them differently | Named by place, matched by place and by each source's prerequisite |
| Stations | Zelda Wiki says "22 interconnected Stations", but its gallery lists 29 places (temples, the Fire Sanctuary, the Dark Realm) | The 22 train stops that aren't temples or the Tower: exactly 22. This is inferred, not stated |

### Main Quest

| Topic | Sources | Used |
|---|---|---|
| Rail Map floors | 2, 6, 10, 16 (Zelda Wiki *Rail Map*); 3, 7, 12, 17 (Zelda Dungeon) | Both in notes |
| Final climb floors | 23–30 under the altar (Zelda Wiki); 30 down to 24, compass on 24 (Zelda Dungeon); compass on 25 (IGN) | "Floors below the altar", the numbers in notes |
| Warp Gate A | Opens after Steem's duet (Zelda Wiki), on first entering the Snow Realm (Zelda Dungeon) | After the Snow Rail Map, both noted |
| Noko's timing | Possible after Carben's duet (Zelda Wiki); Zelda Dungeon does it when Kagoron asks | Listed early, noted |
| Mega Ice price | Haggled to 10 Rupees or free (Zelda Dungeon), no lower than 50 for a full load (IGN) | Both noted |

### Items and upgrades

| Topic | Sources | Used |
|---|---|---|
| Quiver sources | Goron Country Store (2,000 Rupees) gives 30 and the Pirate Hideout (3,000–3,999 points) 50 (Zelda Wiki, IGN); the reverse (Zelda Dungeon) | Tiers by capacity (20/30/50), each naming both sources |
| Pirate Hideout quiver score | 3,000–3,999 (Zelda Wiki), 3,000–4,000 (IGN), 3,500–4,000 (Zelda Dungeon) | 3,000–3,999, Zelda Dungeon noted |
| First Bomb Bag | 10 bombs (Zelda Wiki, Zelda Dungeon), 20 (IGN, likely a slip) | 10 / 20 / 30 |
| Medium Bomb Bag | The Whip Race under 1:30 (IGN), 1:15–1:30 (Zelda Dungeon), under 2:00 (IGN's items page), beat the record (Zelda Wiki) | Under 1:30 |
| Swordsman's Scroll numbers | Scroll 1 is the Sword Beam from Bunnio and 2 the Great Spin from Niko (Zelda Wiki, IGN); the reverse (Zelda Dungeon's walkthrough) | Named by technique |
| Bunnio's prizes | 5 rabbits: a Heart Container (all). 10 of one kind: Treasure, per kind (Zelda Dungeon, Bunnio's quote on Zelda Wiki); "10 in total or one of each kind" (Zelda Wiki); 10 rabbits (StrategyWiki). One of each kind: Treasure (Zelda Dungeon, Zelda Wiki). All 50: the Sword Beam (all) | 8 prizes: five, one of each, ten of each kind ×5, all 50 |

### Heart Containers and minigames

| Topic | Sources | Used |
|---|---|---|
| Russell's fee | 20 Rupees (Zelda Wiki, IGN), 50 (Zelda Dungeon) | 20, noted |
| Pirate Hideout heart | 4,000+ (Zelda Wiki, Zelda Dungeon), 4,000–4,500 (IGN) | 4,000+, IGN noted |
| Ends of the Earth prizes | Brainteaser a Treasure, Braintwister a Heart Container, Brainmasher a Regal Ring (Zelda Wiki, IGN; the same caves in Zelda Dungeon's walkthrough); the Regal Ring "after all three" (Zelda Dungeon's Treasure page) | Zelda Wiki and IGN |
| Beedle's cards | 500 points for the Gold Card (all four); the Club Card costs 100 Rupees (Zelda Wiki, IGN) | As listed |

### Force Gems and warp gates

| Topic | Sources | Used |
|---|---|---|
| Joe's tracks | West of Castle Town (Zelda Dungeon), the Castle Town–Forest Temple shortcut (Zelda Wiki) | Zelda Dungeon, Zelda Wiki noted |
| Warp Gate letters D and F | Zelda Wiki (Prima): D joins the Wellspring and Snow to Fire, opened by the Goron passenger; F joins the Goron Target Range and the Ocean Floor, opened by the Mega Ice Goron. Zelda Dungeon swaps them | Zelda Wiki, the swap noted on both gates and gems |
| Rael's gem timing | After the Sand Wand (Zelda Wiki), after the Sand Temple (Zelda Dungeon, IGN) | After the Sand Temple, noted |

### Rabbits

| Topic | Sources | Used |
|---|---|---|
| Ocean rabbit by the Forest border | Opened by Lucia's gem (Zelda Wiki, Zelda Dungeon's walkthrough), or by Lucia's and Niboshi's (Zelda Dungeon's rabbit page) | Lucia's |
| Grass rabbit south of the Trading Post | Needs Carben's gem (Zelda Wiki), the Ocean Rail Map (Zelda Dungeon's rabbit page), reachable from the main line (its walkthrough) | Carben's gem, the others noted |
| Fire Realm rabbits north of Goron Village | Two need nothing and five need Embrose's duet (Zelda Wiki); the guides place three in the north before the temple and five in the temple maze (walkthrough), or four plus one by Disorientation Station (rabbit page) | 2 + 1 (Embrose) north, 4 in the maze, 1 on the Tower line, 1 by Disorientation Station, 1 at the Ends of the Earth. The least certain list here |
| Anouki Village's rabbit | West of the village (Zelda Dungeon, twice), south by the warp gate (StrategyWiki) | West |

### Train parts and smaller ones

| Topic | Sources | Used |
|---|---|---|
| Golden Freight Car | 1 Regal Ring, 4 Pearl Necklaces and 1 Mystic Jade (Zelda Dungeon, IGN), 2 Mystic Jades (Zelda Wiki, whose total of 9 matches) | 1, noted |
| Stagecoach cannon | Tower Cannon (Zelda Wiki, IGN), "Skull Cannon" in Zelda Dungeon's table (a slip: its cost is the Tower Cannon's) | Tower Cannon |
| Golden names | Brawny Cannon and Royal Passenger Car (Zelda Wiki, Zelda Dungeon); Golden Cannon and Golden Passenger Car (IGN, the European names) | US names, IGN's in notes |
| Train hearts | Spirit Train 4, a mixed train 5, a full set 6, Golden 8 (all three) | Section notes |
| Song note colours | Named differently by each source | Zelda Dungeon's |
| Ocean Temple stamp | Bombs and Whip (Zelda Wiki), the Boomerang too (Zelda Dungeon, StrategyWiki) | All three tagged |
| Place names | StrategyWiki uses European ones (Outset, Mayscore, Snowfall Sanctuary…) | US names throughout |
