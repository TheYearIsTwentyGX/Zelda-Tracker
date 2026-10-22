# Twilight Princess

Twilight Princess's trackers are checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): eight JSON files in
`shared/src/commonMain/composeResources/files/tp/`, listed in `core/Games.kt` after a Progress
Overview. `TpDatasetTest` checks the counts below; `ChecklistDatasetsTest` checks the format.

The data is for the **GameCube original (2006)**. The Wii version is a mirror image: east and west
are swapped, but every collectible is in the same place and the counts are the same. Directions in
the files are the GameCube's, which are also Twilight Princess HD's in Normal Mode (HD's Hero Mode
is mirrored like the Wii). Where a landmark beats a compass direction, the `howTo` uses it.

Where **Twilight Princess HD (2016)** differs for an item, its `notes` start with "HD:". HD-only
lists are **separate sections** (`separate: true`): their own tab, labelled *HD …*, counted on
their own and left out of the tracker's total, the *All* tab and the overview's *Next*. There are
three: the 50 Miiverse stamps, the Cave of Shadows and HD's two extra items (the Ghost Lantern and
the Colossal Wallet). No engine change was needed. Nothing is fetched at runtime.

## Save files

Three: **File 1–3**. Zelda Wiki's *Saving* page gives three Quest Logs on the GameCube and the Wii,
and HD keeps three (each one picks Normal or Hero Mode when it's made). The placeholder in
`Games.kt` was already three, so the ids `file_1`–`file_3` are unchanged.

## Files and counts

| File | Tracker | Sections (ids) | Count |
|---|---|---|---|
| `main_quest.json` | Main Quest | `light_spirits`, `mirror_of_twilight`, `twilight_realm` | 25 steps in one chain: 9 dungeons, 3 Twilights and the story between. Summaries: Tears of Light 48, Fused Shadows 3, Mirror Shards 4 |
| `items.json` | Items & Equipment | `inventory`, `equipment`, `upgrades`, `bottles`, *`hd_items`* | 15 items (21 steps with tiers), 8 equipment (9), 6 upgrades (8), 4 Bottles; HD: 2. Summaries: Rupees 300 → 1,000, Arrows 30 → 100, Bomb Bags 3, Bombs per bag 30 → 60 |
| `heart_pieces.json` | Heart Pieces | `pieces`, `containers` | 45 + 8; Hearts `3 + floor(pieces / 5) + containers` = 20 |
| `poe_souls.json` | Poe Souls | `poes`, `rewards` | 60 Poes and Jovani's 3 rewards |
| `golden_bugs.json` | Golden Bugs | `bugs`, `agitha` | 24 bugs (12 species, male and female) and Agitha's 2 wallets |
| `hidden_skills.json` | Hidden Skills | `skills`, `howling_stones` | 7 skills and 7 Howling Stones |
| `cave_of_ordeals.json` | Cave of Ordeals | `floors`, *`cave_of_shadows`* | 50 floors; HD: the Cave of Shadows' 40 |
| `sidequests.json` | Sidequests & Collections | `sidequests`, `letters`, `fish`, *`hd_stamps`* | 12 sidequests (14 steps), 16 letters, 6 fish; HD: 50 stamps |

Sections in *italics* are `separate`. Ids are unique across all eight files (`TpDatasetTest`
checks), so the Main Quest's steps are named for the event (`wrestle_bo`, `draw_master_sword`,
`rutela_gift`) and the items for the item (`iron_boots`, `master_sword`, `zora_armor`).

Items are in the order you'd suggest doing them, since the overview's *Next* is the first one not
done. The Main Quest is one chain: every step `requires` the one before. Its summaries name item
ids: Fused Shadows are `forest_temple + goron_mines + lakebed_temple`, Mirror Shards
`arbiters_grounds + snowpeak_ruins + temple_of_time + city_in_the_sky` (one shard is still in the
Mirror Chamber when you reach it after the Arbiter's Grounds; the other three are the next three
dungeons' rewards), and Tears of Light `16 * (faron_twilight + eldin_twilight + lanayru_twilight)`.
The Heart Containers are the bosses named in the Main Quest's notes (`Boss: Diababa.`), which the
test checks. The Hidden Skills tab comes before the Howling Stones so that *Next* alternates
between a stone and the skill it unlocks.

Regions are the in-game map's provinces, with each dungeon as a region of its own, in roughly the
order a player reaches them: Ordona Province, Faron Province, Forest Temple, Eldin Province,
Goron Mines, Lanayru Province, Lakebed Temple, Gerudo Desert, Arbiter's Grounds, Snowpeak, Snowpeak
Ruins, Temple of Time, City in the Sky, Palace of Twilight and Hyrule Castle. Castle Town, Lake
Hylia and Zora's River are in Lanayru; Kakariko, Death Mountain and the Hidden Village in Eldin;
the Sacred Grove in Faron; the Cave of Ordeals in the Gerudo Desert. Hyrule Field's parts go by
the province the map puts them in. Each file lists only the regions it uses. The Main Quest and
the Cave of Ordeals have none. Tags give the items or form needed (*Wolf*, *Night*, *Clawshot*…).

### What's tracked, and what isn't

These follow what TP 100% guides list: dungeons, Pieces of Heart, Poe Souls, Golden Bugs, Hidden
Skills, the Cave of Ordeals, the upgrades and Bottles, and the minigames that give something.

- **Main Quest:** the Ordon days, the Hyrule Castle escape, the three Twilights (Tears of Light),
  the nine dungeons with boss, mini-boss and dungeon item in their notes, and the story steps that
  gate them: Bo's sumo, King Bulblin, Telma's wagon, Rutela's Zora Armor, Midna's desperate hour,
  the Master Sword, the cannon to the desert, the Reekfish, Ilia's memory and the sky characters.
- **Items & Equipment:** the Items screen, with an item's upgrades as tiers (Fishing Rod and Coral
  Earring; Bombs, Water Bombs and Bomblings; Clawshot and Double Clawshots; the Dominion Rod and its
  restored power; the Ancient Sky Book and the finished Sky Book). Equipment is the Collection
  screen's swords, shields and clothing, with the light-filled Master Sword as a tier. The Wooden
  Sword is in the Main Quest only: it isn't kept. Upgrades are the two wallets and quivers as tiers
  (with `value`s that add up to their capacities), the three Bomb Bags by where they're found and
  the Giant Bomb Bag.
- **A few things are in two trackers**, because a player looks for them in both: wallets (Items and
  Agitha), quivers (Items and the STAR Game), Iza's bomb bags (Items and Sidequests), Jovani's
  Bottle and Ghost Lantern (Items and Poe Souls), the Colossal Wallet (Items and the Cave of
  Shadows), and the minigame Pieces of Heart (Heart Pieces and Sidequests). Sidequests that are
  only a purchase or a Bottle (Coro, the Hylian Shield, the Fishing Hole Bottle) are only in Items.
- **Howling Stones:** seven, including the Sacred Grove's Zelda's Lullaby stone, which teaches no
  skill but opens the way to the Master Sword. The other six each call the Golden Wolf for one skill.
  Ending Blow needs no stone.
- **Cave of Ordeals:** every floor is a row, with its enemies, the item needed to get through
  (Spinner from floor 11, Ball and Chain from 21, the restored Dominion Rod from 31, Double
  Clawshots from 41) and the Great Fairy's reward every tenth floor. Floors 17, 33 and 44 have Poes,
  which are also in Poe Souls.
- **Sidequests & Collections:** Talo's target practice, Iza's river and Rapid Ride, the Fishing
  Hole's Sinking Lure, Rollgoal, Charlo, the STAR Game's two rounds, the Malo Mart donations and
  the hot springwater, and the Snowpeak races; the Postman's 16 letters; and the six Fishing
  Journal fish.
- **Left out:** dungeon maps, compasses and keys; Tears of Light one by one (each Twilight is one
  step); Ooccoo's dungeons (her letters are tracked); the Wooden Sword, the goat herding and the
  Cucco and cat events, which leave nothing behind, except the goat herding's Piece of Heart.

### Missable

Only two Ooccoo letters. Using Ooccoo in the first dungeon you meet her (normally the Forest
Temple) loses "DEAR ADVENTURER" for good; never using her by the Temple of Time (or Snowpeak:
sources differ) loses "Dear Adventurer (2)". Everything else stays available until the final battle.

### HD

- **Tears of Light:** 12 per Twilight instead of 16 (the step notes say so; the summary is the
  GameCube's 48).
- **Wallets:** 500, 1,000 and 2,000 Rupees instead of 300, 600 and 1,000; the Cave of Shadows adds
  the 9,999-Rupee Colossal Wallet. There is no "Tycoon Wallet" in TP (that's Skyward Sword).
- **Ghost Lantern:** Jovani gives it with his Bottle for 20 Poe Souls. It glows while an Imp Poe is
  nearby, day or night.
- **Miiverse stamps:** 50 (26 Hylian Letters and 24 pictures), mostly in chests that held Rupees in
  the original. Nine aren't in chests: Agitha's (12 Golden Bugs), Malo Mart's (bought), the Rupee
  stamp (Gengle), the five by the Great Fairy's doors in the Cave of Ordeals and Twili Midna (all 49
  others, so it has no region).
- **Cave of Shadows:** opened with the Wolf Link amiibo, 40 floors as Wolf Link in three runs (to
  floor 6, 20 and 40), each run opening later in the story.
- **Other amiibo:** Link and Toon Link refill arrows, Zelda and Sheik hearts, and Ganondorf doubles
  the damage you take. They change nothing tracked, so they're only noted here.
- Sera's cat needs one fish in HD, two on the GameCube and Wii.

## Sources

- **Zelda Wiki** (zeldawiki.wiki, read as wikitext through its API): Piece of Heart, Heart Container,
  Poe Soul (all 60, GameCube and Wii directions), Imp Poe, Jovani, Gengle, Ghost Lantern, Golden
  Bug, Agitha, Howling Stone, Hidden Skill, Golden Wolf, Cave of Ordeals, Cave of Shadows, Miiverse
  (the HD stamp table), Wallet, Quiver, Bomb Bag, Bottle, Tears of Light, Mirror Shard, Ancient Sky
  Book, Owl Statue, amiibo, Twilight Princess HD, Saving, the Malo Mart, armor, shield and sword
  pages, the minigame pages (STAR Game, Rollgoal, Rapids Ride, Fishing Hole), Fish Journal, Letter,
  Postman, Ooccoo, Sera and Yeto.
- **Zelda Dungeon**: the HD walkthrough (Normal Mode, so GameCube orientation) and its wiki's Heart
  Pieces, Poe Souls, Golden Bugs, Howling Stone, Bottles and Upgrades pages, read from Wayback
  Machine copies where the live site blocked automated fetches.
- **StrategyWiki**: Heart Containers, Poe Souls, Golden Bugs, Hidden Moves, Cave of Ordeals, Items,
  Equipment and Minigames. Its control templates give the GameCube direction explicitly.
- **Zelda's Palace**: Pieces of Heart and Poe Souls (Wii orientation) and HD stamps (Normal Mode).
  It and Zelda Wiki are the only sources that list the stamps; they agree on all 50.
- **GameFAQs** guides (DBM11085's Golden Bug FAQ and walkthrough, ZFS's Hidden Skills FAQ, the
  fishing FAQs) and the **LP Archive** screenshots of all 16 letters, for spot checks.
- **zeldaret/tp**, the decompilation, for the letters' triggers (`d_meter2_info.cpp`) and the
  Ooccoo flags.

## Story links

With *Hide what the story hasn't unlocked yet* on (Settings → Progress), each checklist leaves out
what the Main Quest hasn't reached on the save file. It uses each item's `unlockedBy`, a step of
`main_quest.json`; docs/checklist-trackers.md, *Story gating*, explains the rules, and
`scripts/derive-story-links.py` holds TP's table.

**Counts.** 360 of the 365 items outside the Main Quest are linked. The other 5 can be had in Ordon
from the first day: the Fishing Rod, the Slingshot, Sera's Bottle, the Greengill and the Ordon
Catfish.

**How a step was chosen:**

- *Inside a dungeon*, chests, Poes, stamps and the boss's Heart Container are gated by the step
  that opens the dungeon:

  | Dungeon | Step that opens it |
  |---|---|
  | Forest Temple | `faron_twilight` |
  | Goron Mines | `wrestle_bo` (the Iron Boots) |
  | Lakebed Temple | `rutela_gift` (the Zora Armor) |
  | Arbiter's Grounds | `gerudo_desert` |
  | Snowpeak Ruins | `reekfish_scent` |
  | Temple of Time | `snowpeak_ruins` |
  | City in the Sky | `sky_characters` |
  | Palace of Twilight | `city_in_the_sky` |
  | Hyrule Castle | `palace_of_twilight` |

  When an item needs something from a later dungeon, the later step wins. One HD stamp in the
  Goron Mines needs the Clawshot, so it's gated by `lakebed_temple`.
- *Provinces*: Link is human in a province once its Twilight lifts, so Faron, Eldin and Lanayru are
  gated by `faron_twilight`, `eldin_twilight` and `lanayru_twilight`. The Gerudo Desert and the
  Bridge of Eldin's middle are gated by `gerudo_desert`, and Snowpeak by `reekfish_scent`.
- *The `after` text*:
  - a dungeon named alone ("After Goron Mines") is gated by that dungeon's step;
  - a dungeon named with its item ("Arbiter's Grounds (Spinner)") is gated by the step that opens
    the dungeon, since that's when you can first have the item.
- *Wolf Link at will* comes with the Shadow Crystal (`draw_master_sword`). So Poes outside the
  dungeons, Jovani's rewards and the Ghost Lantern are gated by it. The exception is Jovani's own
  Poe, met as a wolf after the Lakebed Temple.
- *The Cave of Ordeals*:
  - Only some floors name what they need (1, 11, 21, 31 and 41). Each floor in between inherits
    the gate of the last floor that did.
  - So floors 1–20 are gated by `gerudo_desert`, 21–30 by `reekfish_scent` (the Ball and Chain),
    31–40 by `ilia_memory` (the restored Dominion Rod) and 41–50 by `sky_characters` (the Double
    Clawshots).
  - The Cave of Shadows follows the same rule.
- *Hidden Skills* follow their Howling Stones, since each skill requires its stone.
- *Judgement calls*, all gated as early as the sources allow:
  - The Malo Mart bridge fund, its Springwater Rush and the Castle Town branch (and the Magic Armor)
    come after the Master Sword.
  - The Wooden Shield is gated by `eldin_twilight`, when Malo opens in Kakariko. The Hylian Shield
    is gated by `king_bulblin_bridge`, once Colin is back.
  - Twili Midna, the 50th stamp, needs the other 49, so it's gated by `palace_of_twilight`.

`TpDatasetTest` spot-checks the links and lists the five start items, so a new item can't go
unreviewed.

## Discrepancies

### Counts

None that matter. Every source gives 45 Pieces of Heart, 8 boss Heart Containers (none from
Ganondorf), 20 hearts at most, 60 Poe Souls (the Cave of Ordeals' three included), 24 Golden Bugs,
7 Hidden Skills, 50 Cave of Ordeals floors, 16 letters, 4 Bottles, 3 Fused Shadows, 4 Mirror
Shards, 16 Tears of Light per Twilight (12 in HD) and 50 HD stamps. Zelda Wiki describes the Cave
of Shadows as runs of "5, 15 and 20 floors", but its chests and exits are on floors 6, 20 and 40
(Zelda Dungeon), so it has 40 rows.

### Rewards and requirements

- **Jovani at 60 souls:** only Zelda's Palace says he gives 200 Rupees himself; the others only
  describe Gengle's 200 Rupees on every later visit. Both are rows; the 60-soul row's notes say so.
  The 20-soul Bottle of Great Fairy's Tears is in every source.
- **Agitha's payments:** each bug after the first pays 50 Rupees, or 100 when it completes a pair.
  Zelda Dungeon's total of 1,650 means the first and last bugs pay only the wallets; StrategyWiki
  says the first also pays 50.
- **Cave of Ordeals past floor 10:** the Spinner (Zelda Wiki, Zelda Dungeon) or the Clawshot
  (StrategyWiki). The files use the Spinner, floor 11. Enemy counts differ on about 13 floors;
  the files use Zelda Wiki's and say "about" where the gap matters.
- **Owl Statues:** Zelda Dungeon says the Dominion Rod alone moves them; StrategyWiki and Zelda's
  Palace say it needs its power restored by Shad, which the files follow.
- **Snowpeak races:** the Piece of Heart is for beating Yeta, in the second race (StrategyWiki,
  Zelda Dungeon). Beating Yeto only opens her race.
- **Malo Mart:** delivering the hot springwater cuts the last donation from 2,000 to 200 Rupees
  (Zelda Wiki's Malo Mart page, the Zelda Dungeon wiki); Zelda Wiki's Magic Armor page mentions only
  2,000. The repaired bridge is at Castle Town's east gate per the game's text; the Magic Armor page
  says west.
- **Howling Stone songs:** Zelda Wiki (after the Hyrule Encyclopedia) names Song of Healing,
  Requiem of Spirit, Prelude of Light, Zelda's Lullaby, two unnamed songs and the main theme; a
  GameFAQs guide calls two of them Oath to Order and Nocturne of Shadow. The files use Zelda Wiki's.
- **Golden Wolf spots:** the Great Spin wolf is in Castle Town's Central Square (Zelda Wiki) or on
  the road up to Hyrule Castle (StrategyWiki); the files say the path from the Central Square up
  to the castle, which fits both.
- **Agitha's letter** needs a bug handed in only per the Zelda Dungeon wiki.

### Places

- **Mirrored guides.** DBM11085's "GameCube" Golden Bug FAQ and Zelda's Palace's heart and Poe pages
  use Wii directions. The files follow Zelda Wiki, StrategyWiki in GameCube mode and Zelda
  Dungeon's HD Normal Mode walkthrough, and use landmarks where those disagree.
- **Directions that disagree:** the Kakariko Gorge tree Poe and the Hidden Village balcony Poe
  (Zelda Wiki and StrategyWiki opposite), the Palace of Twilight pieces' wings and the Kakariko Gorge
  Double Clawshot piece (Zelda Dungeon and StrategyWiki), the Isle of Riches Poe's tier (three
  sources, three tiers), the Female Pill Bug and the Male Snail. Each item's notes give both.
- **Zelda Dungeon's filing slips:** it lists the Castle Town east-bridge Poe under "Sacred Grove",
  the Temple of Time owl Poe under "Sacred Grove (Past)" and the Faron owl piece under "Ordon
  Woods". The files follow the other sources.
- **Night:** every outdoor Poe is tagged *Night* except the Sacred Grove boulder and waterfall Poes,
  where no source says; their notes say so.
