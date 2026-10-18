# A Link to the Past

Every A Link to the Past tracker is a checklist (`docs/checklist-trackers.md`). The data is in
`shared/src/commonMain/composeResources/files/alttp/`. `AlttpDatasetTest` checks the counts below
and the links between files, and `ChecklistDatasetsTest` checks the format, ids, regions and
`requires`. `AlttpOverviewTest` checks the overview rows.

The trackers follow the **original SNES release** (1991). Where the Game Boy Advance version (*A Link
to the Past & Four Swords*, 2002) differs, the item's `notes` say so. Nothing is fetched at
runtime: the JSON was compiled offline from the sources below, with scripts that aren't committed
(as for Echoes of Wisdom).

## Save files

**Three files**, as on the SNES cartridge. StrategyWiki's *Gameplay* page says to "move the fairy
to the save slot (top, middle, or bottom)".
`Games.ALTTP` already had three placeholder files, so their ids (`file_1`–`file_3`) didn't change.
Neither Zelda Dungeon nor StrategyWiki gives a count for the GBA version, whose files also hold
Four Swords progress.

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | — | A row per checklist below (`ChecklistProgressProvider`) |
| Main Quest | `main_quest.json` | 9 Light World steps (the escape, Sahasrahla, the 3 Pendant dungeons, the Master Sword, Agahnim) and 9 Dark World steps (the 7 Crystal dungeons, Ganon's Tower, Ganon). Each dungeon row names its boss, reward and treasure. Pendants = `light_world.value` (0–3), Crystals = `dark_world.value` (0–7) |
| Items & Equipment | `items.json` | 7 equipment rows (sword, shield, mail and gloves as tiers; Pegasus Shoes, Zora's Flippers, Moon Pearl), 18 item-screen items (upgrades such as Silver Arrows, the Magical Boomerang, Magic Powder and the Flute as tiers), 4 Bottles, and 3 upgrades: half magic and the Pond of Happiness bomb and arrow capacities, 7 tiers each. Bombs = `10 + bomb_capacity.value` (10–50), Arrows = `30 + arrow_capacity.value` (30–70) |
| Heart Pieces | `heart_pieces.json` | 24 Pieces of Heart (18 Light World, 6 Dark World) and 11 Heart Containers. Hearts = `3 + floor(pieces / 4) + containers`, 3 to 20 |

The Bombs and Arrows totals needed one engine change: a formula can now name an item id
(`bomb_capacity.value`) as well as a section id. Both upgrades sit in one *Upgrades* tab but need
a total each. See `docs/checklist-trackers.md`, *Formulas*.

**Why these.** speedrun.com's 100% rules for ALttP are the clearest statement of what
completionists collect:
- every category: "a full inventory with all maximum level items, 20 hearts and 1/2 magic",
  shown on the menu with all 4 Bottles;
- the glitched categories also need all 24 Heart Pieces, all bosses (both Agahnims and Ganon),
  and all Pendants and Crystals;
- one category spells out "all 11 heart containers and 24 heart pieces", the Gold Sword, the
  Mirror Shield and the Red Mail;
- **not required** in any category: intermediate upgrades, bomb and arrow capacity, and maps,
  compasses and big keys.

The three checklists cover all of that. Capacity upgrades are tracked anyway, since they're the
one money sink players chase, but the overview shows them only as totals. Zelda Dungeon's
walkthrough counts "Heart Pieces x/24" per chapter.

Not tracked:
- **Maps, compasses and big keys.** speedrun.com excludes them, and no source gives their chests
  precisely enough for a useful *how to*. StrategyWiki finds all three in each of the 12 regular
  dungeons; Hyrule Castle has a Map and Big Key, and Hyrule Castle Tower has none.
- **Every chest.** The "216 items" figure comes from the randomizer, not from any of these sources.
- **Mini-games.** The three that matter give Pieces of Heart and are tagged *Mini-game* in Heart
  Pieces. These are the 15-Second Game, the Village of Outcasts Treasure Chest Game and the
  Digging Game. The Kakariko and Lost Woods chest games and the Shooting Gallery only pay Rupees.
- **The Fat Fairy, the Waterfall of Wishing and the Mad Batter** are covered as the upgrades they
  give: the Golden Sword and Silver Arrows, the Red Shield and Magical Boomerang (all tiers), and
  half magic.
- **Bottle contents.** Potions, fairies, bees and the Good Bee are refills, not collectibles. The
  four Bottles are tracked.
- **Bombs.** Their item slot fills on the first pickup.
- **GBA-only content**: the Palace of the Four Sword, the Riddle Quest (Basket, Hurricane Spin)
  and the Golden Bee shop are in notes only.

**Missable: nothing.** Neither Zelda Dungeon nor StrategyWiki flags any SNES item, piece or
upgrade as missable. The Lamp even has fallback chests (Link's House, the secret passage,
Zelda's cell). No item sets `missable`. The one GBA missable, the Riddle Quest's Fake Master
Swords, isn't tracked.

## Regions

Zelda Wiki's *Locations in A Link to the Past* has two top-level locations with places: **Light
World** and **Dark World**. The Sacred Realm has nothing to collect. Main Quest and Heart Pieces
use those two regions. Each item's world is where Link picks it up, and the place (Kakariko
Village, Death Mountain, Swamp of Evil, a dungeon) goes in `area`. Items & Equipment has no
regions: an upgrade's tiers span both worlds, so each row's `area` or tiers say where.

Order follows the story where it can, since the overview's *Next* is the first undone item:
- Main Quest is in level order, with `requires` for the hard gates. The Master Sword needs the 3
  Pendant dungeons and Agahnim needs the Master Sword. Every Dark World dungeon needs Agahnim, and
  Ice Palace and Misery Mire also need Thieves' Town (Titan's Mitt). Turtle Rock needs Misery
  Mire (Cane of Somaria), Ganon's Tower needs all 7 Crystals and Ganon needs the tower.
- Heart Pieces follow Zelda Dungeon's numbering, which tracks the walkthrough.

## Sources

Every count was checked against three sources:
- **Zelda Wiki** (zeldawiki.wiki), read through the MediaWiki API:
  - *Piece of Heart*, *Heart Container*, *Bottle*, *Pond of Happiness*, *Waterfall of Wishing*,
    *Cursed Fairy*, *Mad Batter*, *Treasure Chest Game*, *15-Second Game*, *Digging Game*,
    *Shooting Gallery*;
  - *Items in* and *Locations in A Link to the Past*;
  - each dungeon page and each major item page.
- **Zelda Dungeon** (zeldadungeon.net), read from Wayback Machine snapshots (mostly December 2024)
  because the site blocks scripted requests:
  - wiki pages: *A Link to the Past Heart Pieces*, *Bottles*, *Items*, *Upgrades*, *Dungeons*,
    *Bosses*, *Fairy Fountains* and *(GBA)*; *Pond of Happiness*, *Golden Bee*, *Magic Shop*,
    *Riddle Quest* and *Palace of the Four Sword*;
  - the walkthrough chapters, whose "New in this section" boxes list items and pieces.
- **StrategyWiki** (strategywiki.org, *The Legend of Zelda: A Link to the Past*), through its API:
  - *Heart Pieces*, *Items*, *Versions*, *Gameplay*, *Fairy Springs*;
  - every area and dungeon page.

**speedrun.com** (ALttP category rules) settled what "100%" covers.

### Counts

| | Zelda Wiki | Zelda Dungeon | StrategyWiki |
|---|---|---|---|
| Pieces of Heart | 24 (table 18 Light + 6 Dark; prose says 19 + 5) | 24 (6 on its Dark World map) | 24 (18 + 6) |
| Heart Containers | 11 (Sanctuary + 10 bosses) | 11 | 11 |
| Max hearts | 20 | 20 | 20 |
| Bottles | 4 | 4 | 4 |
| Bomb capacity | 10 → 50, table lists 25/30/35/40/50 | 10 → 50, +5 to 40 then +10 | max 50 |
| Arrow capacity | 30 → 70, eight rows from 30 | max 70 | max 70; 1,400 Rupees for everything |
| Dungeons with a Pendant / Crystal | 3 / 7 | 3 / 7 | 3 / 7 |
| Dungeon treasures | 11 | 11 | 11 |

## Discrepancies and how they were settled

**Heart Pieces**
- **Light/Dark split.** Zelda Wiki's prose says 19 and 5, but its own table, Zelda Dungeon and
  StrategyWiki all give 18 and 6. The difference is the Mire Shed in the Swamp of Evil (Zelda
  Wiki #23, "the cave west of Misery Mire"), which is in the Dark World. 18 and 6 are used, and
  `AlttpDatasetTest` checks them.
- **Numbering.** The three sources number the pieces differently. Zelda Dungeon and StrategyWiki
  swap four pairs and order the Stake Garden and Bumper Cave differently. Zelda Wiki uses the
  official Nintendo guide's order. Each piece's `notes` gives all three numbers.
- **What a piece needs:**
  - *Graveyard ledge*: to reach the ladder, Zelda Dungeon needs the Pegasus Shoes or Titan's
    Mitt, StrategyWiki the Magic Hammer. Both are noted, and only the shared Mirror and Bombs are
    in the line.
  - *Bumper Cave ledge*: the Hookshot (Zelda Wiki, Zelda Dungeon) or the Power Glove
    (StrategyWiki), plus the Magic Cape. Noted.
  - *Treasure Chest Game*: StrategyWiki adds the Magic Hammer. Noted.
  - *Mimic Cave*: StrategyWiki lists only the Mirror, Zelda Dungeon the way into Turtle Rock as
    well. Zelda Dungeon's is used.
- **Blind's Hideout:** the cracked wall is to the right (Zelda Dungeon) or on the north
  (StrategyWiki). Both are noted.

**Items and upgrades**
- **Pond of Happiness.** Zelda Wiki's bomb table (25, 30, 35, 40, 50) leaves out 15 and 20, and
  its arrow table starts at 30, which is the starting capacity. Zelda Dungeon says +5 per 100
  Rupees until 40, then a last jump to 50. StrategyWiki's 1,400 Rupee total means 7 upgrades of
  each. Used: bombs 15, 20, 25, 30, 35, 40, 50 and arrows 35, 40, 45, 50, 55, 60, 70, as tiers
  worth 5 (the last worth 10).
- **Fourth Bottle.** Zelda Dungeon says the purple chest appears after the Tempered Sword,
  StrategyWiki once the smiths are reunited. The man who opens it is near the Desert of Mystery's
  entrance (Zelda Wiki), by the signpost west of the Great Swamp (Zelda Dungeon) or "in the
  canyon" (StrategyWiki). `after` uses the reunion, which comes first; the rest is in `notes`.
- **Tempered Sword:** Zelda Dungeon's 10-Rupee fee is used; the other two give no price. Zelda
  Wiki calls the Tempered and Golden Swords the Master Sword Lv2 and Lv3. The tiers use Zelda
  Dungeon's names, which are also the ones players use.
- **Quake Medallion:** Link throws a rock (Zelda Dungeon), a skull (Zelda Wiki) or the sign
  (StrategyWiki) into the ring of stones. All three are noted.
- **Bombos Medallion:** the Dark World spot is "near the swamp" (Zelda Wiki, Zelda Dungeon) or
  the east end of the canyon (StrategyWiki). Noted.
- **Ice Rod cave:** on the east shore (Zelda Wiki, Zelda Dungeon) or the north-east corner of Lake
  Hylia (StrategyWiki). Noted.
- **Red Shield:** 500 Rupees at the Dark World Shield Shop on the SNES, 70 on the GBA (Zelda
  Wiki), or free from the Waterfall of Wishing.

**Main Quest**
- Bosses, Pendants, Crystals and dungeon treasures agree in all three sources.
  `AlttpDatasetTest` checks that each dungeon's "Treasure:" names an item in Items & Equipment
  (11 of them), and that the 11 Heart Containers match the Main Quest steps that give one.
- Neither Zelda Dungeon nor StrategyWiki gives the Pendants' colours, so rows use their names:
  Courage (Eastern Palace), Power (Desert Palace) and Wisdom (Tower of Hera).
- **Palace of the Four Sword (GBA):** Zelda Dungeon says it opens once Ganon and Vaati are both
  beaten on the same file, StrategyWiki once Four Swords is finished. It's mentioned in the Ganon
  row's notes only.

**Unverified:** the GBA save-file count and the GBA Pond of Happiness, which no source describes
as changed.
