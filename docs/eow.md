# Echoes of Wisdom

Every Echoes of Wisdom tracker is a checklist (`docs/checklist-trackers.md`). Its data is in
`shared/src/commonMain/composeResources/files/eow/`. `EowDatasetTest` checks the counts below and
the links between files. `ChecklistDatasetsTest` checks the format, ids, regions and `requires`.

Nothing is fetched at runtime. The JSON was compiled offline from the wikis and guides below, with
scripts that aren't committed (as for TotK's shrines).

## Save files

**Two save files per Switch user**, each with its own manual save and rolling autosaves inside it.

- Zelda Wiki, *Saving*: "Up to two Save Files can be chosen, with each supporting four Autosave files".
- Game8, *How Does Saving Work*: up to 2 save files, with 5 autosaves each. The autosave count
  doesn't matter to the app, so 4 against 5 was left unsettled.

A few guides (Critical Hits, Game Rant) say one file per profile. That contradicts both Zelda Wiki
and Game8's title-screen screenshot. Hero Mode is a setting, not a separate file.

## Trackers

| Tracker | File | Contents |
|---|---|---|
| Progress Overview | — | A row per checklist below (`ChecklistProgressProvider`) |
| Main Quest | `main_quest.json` | 35 story steps covering the 18 Main Quests and 8 dungeons; Tri Rod, Bind, Reverse Bond and Tri's Power Lv. 2–11 (tiers); the 3 Swordfighter weapons |
| Echoes | `echoes.json` | 127 echoes (34 Object, 93 Monster) in Notebook order |
| Heart Pieces | `heart_pieces.json` | 40 Pieces of Heart and 7 Heart Containers. Hearts = `3 + floor(pieces / 4) + containers`, 3 to 20 |
| Might Crystals | `might_crystals.json` | 85 places holding 150 crystals (`crystals.value`), and Lueburry's upgrades with their costs as tier values (`upgrades.value`, 150) |
| Side Quests | `side_quests.json` | 50 |
| Accessories | `accessories.json` | 28 accessories, and the Great Fairy's 4 slot purchases |
| Smoothie Recipes | `smoothies.json` | 59 smoothies and 10 potions, in recipe-number order |
| Slumber Dojo | `slumber_dojo.json` | 15 challenges, 15 hidden goal times and 6 cumulative rewards |
| Stamp Stands | `stamp_stands.json` | 25 stands and 5 card rewards |
| Bottles, Automatons & More | `collectibles.json` | 4 Fairy Bottles, 6 Automatons, 11 outfits (3 amiibo-only), and 7 mini-games with first-clear tiers |

**Why these.** Game8's *100% Checklist* lists:
- Heart Pieces 40, Might Crystals 150, Stamps 25, Echoes 127;
- Automatons 6, Smoothies 69, Slumber Dojo stages 15, Side Quests 50;
- Accessories 28, Clothes 8 (+3 amiibo), Minigames 4.

After the credits, the in-game map counts the missing Heart Pieces, Might Crystals and Stamps.
The Deku Tree counts missing Echoes per region. The four small sets share one tabbed checklist,
so the menu and overview stay short.

Not tracked:
- **Wanted posters and figurines** aren't in EoW.
- **Golden Eggs** are an ingredient with no fixed count.
- **Treasure chests** have no published total.
- **Bosses** are rows of the Main Quest.

**Missable:** nothing. Game8's missables page says nothing in EoW can be missed for good. Rift-only
echoes stay reachable, and Glide Path only pauses during Lands of the Goddesses. No item sets
`missable`, so no file shows the *Missable* chip.

## Regions

Every file uses the same regions in the same order, the top-level locations of Zelda Wiki's
*Locations in Echoes of Wisdom*:

Suthorn Prairie · Hyrule Castle · Hyrule Field · Eastern Hyrule Field · Lake Hylia · Gerudo Desert ·
Jabul Waters · Eldin Volcano · Hebra Mountain · Faron Wetlands · Eternal Forest

A file lists only the regions it uses. The sub-location (village, cave, house, dungeon) goes in
`area`. Edge cases follow Zelda Wiki:

- **Kakariko Village**, with the Slumber Dojo and its shop, is in **Eldin Volcano**. Places only
  near it ("cave east of Kakariko", "far west of Kakariko") stay in Hyrule Field.
- **Lake Hylia**, with the Great Fairy Shrine, is its own region.
- **Eastern Hyrule Field**, with the Eastern Temple and Dampé Studio, is its own region.
- **Hyrule Castle Town** is inside **Hyrule Castle**, as Zelda Wiki lists it.
- **Suthorn Forest** (Lueburry's House, the Suthorn Ruins) is in **Suthorn Prairie**. Zelda Wiki's
  top-level list also names Suthorn Forest, but its Suthorn Prairie section contains it.
- **Holy Mount Lanayru** and the Lanayru Temple are in **Hebra Mountain**: Holy Mount Lanayru is
  the peak.
- **Rifts and Still World dungeons** go under the region whose rift leads to them, with the
  Still World name in `area` ("Stilled Goron City"). Null's Body and the Stilled Ancient Ruins
  are in the Eternal Forest.

Items with no place, such as smoothie recipes, Tri's Power and accessories bought anywhere, go
under their section's title.

The research drafts used a coarser list ("Suthorn", "Hyrule Castle Town", and Hyrule Field
covering Kakariko, Lake Hylia and Eastern Hyrule Field). It was remapped to the list above from
each item's `area`. Guides disagree on the region of some border spots:
- the far-east Eldin stamp is in the Eternal Forest on Nintendo Life;
- the Gerudo entrance crystal is in Suthorn Prairie on Game8;
- the Seesyde Holmill crystal is in Hyrule Field on Zelda Dungeon.

Each of these items' `notes` gives the alternative.

## Story links

With *Hide what the story hasn't unlocked yet* on (Settings → Progress), each checklist leaves out
what the Main Quest hasn't reached on the save file. It uses each item's `unlockedBy`, steps of
`main_quest.json`; docs/checklist-trackers.md, *Story gating*, explains the rules, and
`scripts/derive-story-links.py` holds EoW's table.

**Counts.** 500 of the 509 items outside the Main Quest are linked. The other 9 are had from the
very start: the Echoes learned escaping the castle, and the amiibo outfits.

**How a step was chosen.** An item is gated by the **latest** of where it is, the dungeon or Still
World its text names, and its `after` text.

- *Places.*
  - Suthorn is gated by `escape_castle`.
  - Once the Suthorn Ruins are cleared, all of Hyrule can be explored, though each region's rifts
    wait for the story. So every other region is gated by `suthorn_ruins`.
  - The Eternal Forest needs all three Lands of the Goddesses temples.
- *Dungeons and Still Worlds.*

  | Where | Step that opens it |
  |---|---|
  | The Suthorn Ruins | `stilled_suthorn_forest` |
  | The Gerudo Sanctum | `cryptic_cavern` |
  | The Jabul Ruins | `lord_jabu_jabus_den` |
  | Hyrule Castle | `still_missing_castle_town` |
  | The Eldin Temple | `eldin_crater_shortcut` |
  | The Lanayru Temple | `holy_mount_lanayru_ascent` |
  | The Faron Temple | `faron_sweet_spot` |
  | Null's Body | `stilled_ancient_ruins` |
  | The Stilled Goron City, Heart Lake and Faron | `hyrule_castle` (*Lands of the Goddesses*) |
- *`after` text*:
  - "After X" is gated by step X, and "During X" by the step that opens X.
  - *Still Missing* runs from Lueburry's report to the King's rescue. So during it means both
    Gerudo Sanctum and Jabul Ruins, and after it means `hyrule_castle`.
- *Smoothies, potions and stamp rewards* are gated by `suthorn_ruins`, since the shops and the
  Stamp Guy are outside Suthorn.
- *What it requires.* A side quest that follows another takes the later gate, as Dampé's
  automaton quests do.

Sources: the data's own `after` notes, and region access from
[Nintendo Life](https://www.nintendolife.com/guides/zelda-echoes-of-wisdom-which-region-should-i-visit-first)
and [Siliconera](https://www.siliconera.com/best-legend-of-zelda-echoes-of-wisdom-dungeons-rifts-order/),
which agree the three Goddess regions can be visited early but their rifts open only with the
story. `EowDatasetTest` spot-checks the links.

## Sources

Every count was checked against three sources:
- **Zelda Wiki** (zeldawiki.wiki): the *… in Echoes of Wisdom* list pages and each quest,
  location, dungeon and item page, read through the MediaWiki API.
- **Zelda Dungeon** (zeldadungeon.net) wiki guides: *Echoes of Wisdom Heart Pieces*,
  *Might Crystals*, *Echoes*, *Main Quests*, *Side Quests*, *Stamp Locations*,
  *Slumber Dojo Training Sessions*, *Items* and *Automatons*. The site blocks scripted requests
  behind Cloudflare, so its pages were read from Wayback Machine snapshots (2025–2026).
- **Game8** (game8.co/games/Zelda-Echoes-of-Wisdom): Echoes List, Heart Piece Locations, Might
  Crystal Locations, Side Quests, Accessories, Stamp Locations, Slumber Dojo, Minigames,
  Recipes Please, Tri's Power, Missables, Saving, Fairy Bottles, and the 100% Checklist.

**Nintendo Life** was the third source for echo locations, since Zelda Dungeon's echo list gives
none, and the tie-breaker for stamp placement. IGN wasn't reachable.

### Counts

| | Zelda Wiki | Zelda Dungeon | Game8 |
|---|---|---|---|
| Echoes | 127 (34 / 93) | 127 | 127 (34 / 93) |
| Pieces of Heart | 40 | 40 | 40 |
| Heart Containers | 7 | (page unreachable; its crystal page lists the same 7 bosses) | 7 |
| Might Crystals | 150 stated, **148** in its table | 150 in 85 places | 150 in 85 places |
| Main Quests | 18 | 18 | 18 |
| Side Quests | 50 | 50 | 50 |
| Accessories | 28 | 28 | 28 |
| Recipes | 60 rows: 59 smoothies + Unfortunate Smoothie, no potions | 69, numbered (59 + 10 potions) | 69 |
| Slumber Dojo challenges | 15 | 15 | 15 |
| Stamp Stands | no list | 25 | 25 (Nintendo Life 25) |
| Automatons | 6 | 6 | 6 |
| Outfits | 11 | 11 | 8 + 3 amiibo |

## Discrepancies and how they were settled

**Hearts and crystals**
- **Might Crystals, 148 against 150.** Zelda Wiki's table is missing a 2-crystal chest in a
  locked room of the southern Gerudo Desert ruins, opened by a switch under a dune. Zelda
  Dungeon and Game8 (#22) both list it. Added as `mc_gerudo_southern_ruins_chest`, so all three
  sources agree on 85 places and 150 crystals.
- **Upgrade costs:**
  - Sword, Bow and Bombs cost 10 + 20 each, and Energy 5 + 10 + 20: 125 in all. Zelda Wiki and
    Game8 agree.
  - The last 25 crystals buy Lueburry's Energy-refilling pot or statue. Zelda Wiki calls it a
    pot, Game8 a statue.
  - The upgrade effects, and the rule that the top levels open after *Still Missing*, come from
    Game8 only.
- **Heart Containers:** one per boss (Seismic Talus, Mogryph, Vocavor, Ganon, Volvagia, Skorchill,
  Gohma); Null gives none. `EowDatasetTest` checks they match the seven Main Quest dungeons that
  list one.
- **Pieces of Heart:**
  - Zelda Dungeon's and Game8's 40 were matched one to one. Each item's `notes` gives both
    numbers, and 27 of the 40 are also on Zelda Wiki's location pages.
  - The *Cave Along the Beach* piece is at the west end of Zora Cove on Zelda Wiki and Zelda
    Dungeon, but the east on Game8. West is used.
  - The Kakariko Acorn Gathering limit is 20 s on Game8 and 25 s on Zelda Dungeon. Both are noted.
  - An Eldin piece (Zelda Dungeon #30, Game8 #31) and a Faron underwater cave were matched by
    elimination, since their descriptions differ. Both are flagged in `notes`.
- **Slumber Dojo pieces.** The heart draft said "all eleven training sessions"; there are 15
  challenges. The pieces come at **2 and 11 challenges cleared**, as Zelda Wiki and Game8 both
  list the cumulative rewards, so the text was corrected. `EowDatasetTest` checks the dojo's
  milestone rewards against the heart and accessory files.

**Echoes**
- **Needlefly cost:** 2 on Zelda Wiki and Zelda Dungeon, 5 on Game8. 2 is used.
- **Where an echo is first learned.** Sources disagree on Wolfos, Crow, Tektite, Hydrozol, Wind
  Cannon, the Lv. 2 Moblins, Sword Moblin, Mini-Moldorm and Bombfish. The file uses the earliest
  place in the story that two sources, or Zelda Wiki's location tables, support. The alternatives
  are in `notes`. **Tektite** has the least certain location (Lake Hylia is used).
- **Order:** Game8's No. 1–127 matches Zelda Wiki's list order, and is taken to be the Notebook
  order (`notes` starts "No. N").

**Main Quest and Tri**
- **Main Quest:** step order follows Zelda Wiki's objective tables and Game8's walkthrough.
  Each dungeon gives a Heart Container and 5 Might Crystals; Hyrule Castle gives only the
  container.
- **Stilled Ancient Ruins boss:** Zelda Wiki says "Tekom", Game8 says "Impostor Zelda".
- **Tri's Power** levels and triangle caps (4/5/6 at Lv. 2/6/9) agree. **Not verified:** how many
  rifts each level needs, which no source publishes. The cost reductions differ slightly by
  level (Sword Moblin is at Lv. 5 on Zelda Wiki, Lv. 4 on Game8), so only shared examples are
  listed.

**Recipes**
- **Count:** 69 (Zelda Dungeon's numbered list and Game8). The Unfortunate Smoothie, a failed
  mix with no recipe number, is left out.
- **Smoothie ingredients:** Zelda Wiki and Zelda Dungeon agree on all 59.
- **Potions:** recipes and effects come from Zelda Dungeon only.
- **Golden Eggs:** Game8's claim that registering every recipe gives 10 is unverified.

**Side quests**
- **A Mountainous Mystery** unlock:
  - Game8: *The Rift on Eldin Volcano*, *Automaton Engineer Dampé* and *The Fireworks Artist*.
  - Zelda Wiki (marked as lacking sources): *Zappy Shipwreck*, *Let's Play a Game*,
    *Cotton-Candy Hunt* and General Wright's rumours.

  Both are in the item's notes; only the story gate is in `after`.
- **Automaton Engineer Dampé** reward: Zelda Wiki gives the Techtite, Clockwork Key and 5 Twisted
  Pumpkins; Game8 gives only the Techtite. Zelda Wiki is used.
- **Unverified details:** the exact echo The Zora Child's Fate wants, the container for Precious
  Treasure, and how the tumbleweed is delivered in Elusive Tumbleweeds.
- **Quest chains** use `requires`:
  - Dampé's six quests;
  - Glide Path;
  - Curious Child → An Out-There Zol;
  - Runaway Horse → Impa's Gift → From the Heart;
  - Let's Play a Game → Cotton-Candy Hunt.

  *A Treat for My Person* isn't linked to the cat quest, because the Black Cat Clothes amiibo
  outfit works instead.

**Slumber Dojo and stamps**
- **Slumber Dojo rewards,** settled two sources against one:
  - Blank-Slate Battle: Electro Apple ×3 (Game8: ×5).
  - Titans' Gathering hidden goal: Radiant Butter ×5 (Game8: ×7).
  - Moblins' Revenge: 20 Rupees (Zelda Wiki: 25).

  The last challenge is "The Final Titans' Gathering" on Zelda Wiki and Zelda Dungeon (Game8:
  "The Titans' Final Gathering").
- **Stamp Stands** have no official numbering, and Zelda Dungeon, Game8 and Nintendo Life each
  number them differently. All 25 were matched across the three and are listed by region.
- **Stamp card rewards:** 10 Fresh Milk, 3 Golden Eggs, a Fairy Bottle, 5 Monster Stones and the
  Stamp Suit (Zelda Dungeon, Nintendo Life). Game8's "Monster Stones ×1" is taken as its error.

**Collectibles and mini-games**
- **Clockwork Bangle:** Zelda Wiki says Dampé sells it for 5 Monster Stones. Game8 lists 5 Monster
  Stones as the reward for all six Automatons. This is unresolved and noted on the item.
- **Flag Race short course under 0:23:** Zelda Wiki gives a Red Rupee, then 3 Might Crystals under
  0:17. Game8 gives the crystals for both times. Zelda Wiki is used.
- **Mini-games:** Game8 counts 4 (Mango Rush, Acorn Gathering, Slumber Dojo, Flag Race). Zelda Wiki
  adds the Goron Race, which is tracked as the side quest *Ready? Set? Goron!*. The tab lists
  Mango Rush, the Flag Race and the five Acorn Gathering courses. Acorn time limits come from
  Game8 only.
- **Automaton repair costs** come from Zelda Dungeon only.
