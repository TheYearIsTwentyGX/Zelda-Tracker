# Oracle of Seasons

Oracle of Seasons is built entirely from checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): ten JSON files in
`shared/src/commonMain/composeResources/files/oos/` plus the tracker lines in `core/Games.kt`.
`OosChecklistDatasetTest` checks the counts and cross-references below, and `ChecklistDatasetsTest`
checks the format.

The data is for the original Game Boy Color release (the 3DS Virtual Console is the same game).
Nothing is fetched at runtime. The files were compiled by hand from the sources below.

## Save files

Three files, `file_1` to `file_3` ("File 1" to "File 3"). Zelda Wiki's *Saving* page: "Three save
files are also available for use in the game" (said of Ages, which shares Seasons' engine and file
screen). These are the ids the coming-soon placeholder already had.

## Linked games

Finishing one Oracle game gives a password (the *Secret to Labrynna* here) that starts the other as a
**linked game**. A linked game adds the twenty secrets, extra upgrades, Biggoron's Sword, a Heart
Container and the true final battle against Twinrova and Ganon in the Room of Rites. Linked-only
content is **listed, not hidden**:

- items that only exist in a linked game are tagged **Linked** (Twinrova, Ganon, Biggoron's Sword,
  Bombchus, the linked Heart Container, linked-only rings);
- upgrade tiers that only a linked game reaches say so in their name: *Master Sword (Linked)*,
  *Mirror Shield (Linked)*, *99 of each seed (Linked)*, *50 Bombs (Linked)*;
- the *Linked Secrets* tracker covers the passwords and every secret in both directions.

## Trackers and counts

| File | Tracker | Sections (items) |
|---|---|---|
| `main_quest.json` | Main Quest | Prologue 2 (value 0), Essences of Nature 8, Finale 3 (Onox, then Twinrova and Ganon tagged Linked). Summary: Essences of Nature of 8. |
| `items.json` | Items & Equipment | Items 15 (with the five seeds), Upgrades 7 rows (16 steps), Quest Items 21 rows (22 steps). Summaries: seeds of each (20 + the satchel tiers' values, 99 at most) and bombs (10 + the bomb tiers', 50 at most). |
| `seasons.json` | Rod of Seasons | The Rod (value 0) and Winter, Summer, Spring, Autumn. Summary: Seasons of 4. |
| `heart_pieces.json` | Heart Pieces | Pieces of Heart 12, Heart Containers 9 (8 bosses and 1 from a linked secret). Summary: hearts = 3 + pieces / 4 + containers, 15 at most. |
| `rings.json` | Rings | Ring Box 1 row (L-1, L-2, L-3: 3 steps), Magic Rings 64. Summary: Rings of 64. |
| `gasha.json` | Gasha Spots | 16 soft-soil spots, each tagged with its ring classes |
| `trading.json` | Trading Sequence | 13: the 12 trade items and the Noble Sword |
| `animal_companions.json` | Animal Companions | 6 |
| `linked_secrets.json` | Linked Secrets | Linking 4, Secrets from Ages 10, Secrets for Ages 10 (2 steps each) |
| `subrosia.json` | Subrosia | Visits 7, Subrosia Market 6 rows (9 steps), Ores & Smithy 6 rows (7 steps), Pastimes 4 |

Cross-references (`requires`), checked by the test:
- General Onox requires all eight dungeons; Twinrova requires Onox and Ganon requires Twinrova.
- Each trade requires the one before it, from the Cuccodex to the Noble Sword.
- In Subrosia: the Hard Ore needs the Red and Blue Ore, the Iron Shield needs the Hard Ore, the
  Ribbon needs the Star-Shaped Ore.

Some things appear in two trackers because a completionist looks for them in both places, as in
Majora's Mask: Subrosia's ores, Ribbon, Member's Card and Pirate's Bell are also Quest Items; the
Rare Peach Stone is also a Piece of Heart; the Market's rings and the Sign Ring are also rings;
Ricky's Gloves are in Items and Animal Companions. Each tracker counts its own.

## What's tracked, and why

- **Main Quest**: the eight dungeons in the usual order, each with its boss, dungeon item, Essence
  and Heart Container. The Hero's Cave and waking the Maku Tree are a value-0 prologue. The summary
  counts Essences.
- **Items & Equipment**: what the Item and Quest Status screens show. Upgrades are tiers: Sword
  (Wooden → Noble → Master), Shield (Wooden → Iron → Mirror), Roc's Feather → Roc's Cape,
  Boomerang → Magical Boomerang, Slingshot → Hyper Slingshot, seed and bomb capacity. Seasons has
  no Power Glove; the Power Bracelet doesn't upgrade (that's Ages). The Ring Box is in Rings.
- **Rod of Seasons**: the Rod from the Temple of Seasons and the four Season Spirits' towers, in
  the order you reach them.
- **Heart Pieces**: 12 pieces and 8 boss containers in every game (all three sources agree), plus
  the one Heart Container a linked secret gives (the Graveyard Secret or the Fairy Secret, whichever
  direction you linked). 3 + 3 + 9 = 15 hearts. Zelda Wiki: an unlinked game reaches 14 hearts, a
  first game using the return secret 15, and a linked or Hero's game 16 because it starts with 4;
  the total can't know that, so the linked container's notes say so.
- **Rings**: one 64-ring collection shared with Ages. `files/ooa/rings.json` uses the **same
  item ids in the same order** (the in-game ring numbers 1–64), so the two can be cross-referenced
  later. Tags: *Vasu*, *Chest*, *Gasha*, *Maple*, *Linked*, *Secret*, *GBA* (the Advance Shop rings)
  and *Not in Seasons* for the 10 rings that can only be found in Ages and brought over with the
  Ring Secret. Tick a ring once Vasu has appraised it.
- **Gasha Spots**: the 16 soft-soil spots; tick one once you've harvested a nut there. Each is
  tagged with the ring classes its nuts give (a Piece of Heart only comes from spots reaching class
  4). The ~30 Gasha Seed sources aren't tracked.
- **Trading Sequence**: optional, but the Mushroom opens Syrup's shop. It ends with the Noble Sword
  (the Master Sword if a secret already gave you the Noble Sword). Biggoron's Sword is not a trade
  reward; it's the linked Biggoron Secret.
- **Animal Companions**: Ricky, Dimitri and Moosh. The flute decides your partner and Natzu's
  shape: the Horon Village Shop flute (150 Rupees) gives Moosh, the Subrosian Dance Hall flute
  Dimitri, neither gives Ricky (all three sources agree). Both are only offered between Snake's
  Remains and helping Ricky.
- **Linked Secrets**: *Linking* (the Secret to Labrynna, starting from the Secret to Holodrum, the
  Ring Secret, the Hero's Secret), *Secrets from Ages* (the ten heard in a linked Ages and told here)
  and *Secrets for Ages* (the ten heard here in a linked Seasons and told on the finished Ages file).
  Each secret has two steps: the reward where it's told, then the return secret told to Farore,
  which gives the same reward in the other game. Secret ids are the same in both games' files.
- **Subrosia**: the Subrosian story (Rosa, the Temple of Seasons, the Strange Brothers, the
  Piratians), the Market's wares, the ores and what the Great Furnace and Smithy make of them, and
  the Dance Hall, buried treasure and Sign-Loving Subrosian.

## Story links

With *Hide what the story hasn't unlocked yet* on (Settings → Progress), each checklist leaves out
what the Main Quest hasn't reached on the save file. It uses each item's `unlockedBy`, steps of
`main_quest.json`; docs/checklist-trackers.md, *Story gating*, explains the rules, and
`scripts/derive-story-links.py` holds OoS's table.

**Counts.** 174 of the 216 items outside the Main Quest are linked. The other 42 can be had
whenever Link likes, or come from the linked Ages game:
- random rings from Gasha Nuts and Maple;
- Vasu's rings;
- the Ages-side secrets;
- Horon Village's shop and Gasha spot.

**How a step was chosen.** An item is gated by the **latest** of the following.

- *Places*, by the step that opens them. A dungeon is gated by the step before it.

  | Places | Step |
  |---|---|
  | Horon Village, the Western Coast | none (open from the start) |
  | North Horon, the Eastern Suburbs, Holodrum Plain | `heros_cave` (the sword) |
  | The Woods of Winter, Subrosia | `gnarled_root_dungeon` (Rosa's portal) |
  | Spool Swamp | `snakes_remains` (Ricky) |
  | Natzu, Sunken City, Mt. Cucco, Goron Mountain, Eyeglass Lake, Samasa Desert | `poison_moths_lair` |
  | The Tarm Ruins | `unicorns_cave` (the jewels) |
  | The Graveyard | `ancient_ruins` |
  | The Temple Remains | `explorers_crypt` |
- *Tags and text.* The gear an item needs, by the step before the dungeon that gives it:

  | Gear | Step |
  |---|---|
  | Power Bracelet | `gnarled_root_dungeon` |
  | Roc's Feather | `snakes_remains` |
  | Slingshot, Magnetic Gloves, Zora's Flippers | `poison_moths_lair` |
  | Roc's Cape | `ancient_ruins` |

  Each season comes from its tower, whose trip the Main Quest step before it opens.
- *`after`.* A dungeon named alone means after it, as in the earlier games. Secrets heard in a
  linked Ages game aren't gated by this game's story.

`OosChecklistDatasetTest` spot-checks the links and counts the start items.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), through its MediaWiki API
  (`/w/api.php?action=parse&page=…&prop=wikitext&format=json`): *Magic Ring* and each ring's page,
  *Ring Box*, *Gasha Seed*, *Gasha Tree*, *Gasha Nut*, *Piece of Heart*, *Heart Container*,
  *Trading Sequence*, *Linked Game*, *Linked Game/Secrets*, *Secret*, *Vasu*, *Saving*, *Items in
  Oracle of Seasons*, *Rod of Seasons*, the Temple and Towers of Seasons, *Subrosia* and its places,
  *Ricky*, *Dimitri*, *Moosh*, *Strange Flute*, *Natzu*, the dungeon and boss pages.
- **Zelda Dungeon** (zeldadungeon.net, via the Wayback Machine): *Oracle of Seasons Heart Pieces*,
  *Oracle of Seasons Trading Sequence*, *Rings*, *Oracle of Seasons Items*, and the walkthrough
  chapters for each dungeon, Onox's Castle and the Room of Rites.
- **GameFAQs**: TourianTourist's *Ring Guide* for Oracle of Seasons (v1.02, 2013, via the Wayback
  Machine): ring classes, Gasha spot classes, random-ring sources and Seasons ring locations.
- **StrategyWiki**: *The Legend of Zelda: Oracle of Seasons* walkthrough chapters, *Heart Pieces*,
  *Gasha Seeds*, *Trade Quest*, *Items* and *Secrets to and from Labrynna*.

## Discrepancies

| Topic | Sources | Followed |
|---|---|---|
| Sword & Shield Maze boss | Zelda Wiki, StrategyWiki: Medusa Head; Zelda Dungeon: "Medelock" | Medusa Head |
| Round Jewel | Zelda Wiki, StrategyWiki: after five Essences; Zelda Dungeon: after all four seasons | five Essences, the other noted |
| First Subrosia portal | Zelda Wiki: North Horon; StrategyWiki, Zelda Dungeon: follow Rosa from the Eastern Suburbs | the walkthroughs |
| Market Gasha Seed price | three different Scent Seed counts | "40 Ore Chunks the first time, then 100, plus Scent Seeds" |
| Market Piece of Heart price | Zelda Wiki (quoting the shopkeeper), Zelda Dungeon: 20 Ore Chunks + 10 Ember Seeds; StrategyWiki: 10 | 20 |
| Dance Hall flute | StrategyWiki: keep dancing; Zelda Wiki: only before helping Ricky | Zelda Wiki |
| Bomb capacity | Zelda Wiki: 10 → 30 (Market) → 50 (secret) | Zelda Wiki |
| Ring #54 | Zelda Wiki, Zelda Dungeon, TourianTourist: Rupee Ring; StrategyWiki: Wealth Ring | Rupee Ring |
| Victory Ring | majority: a game started with the Hero's Secret; StrategyWiki: beating the linked final boss | majority |
| Maple's rings | TourianTourist, Zelda Dungeon: classes 2–3 only; Zelda Wiki's ring pages: more | classes 2–3 tagged *Maple* |
| Snowshoe and Spin Rings | TourianTourist: Ages-only; Zelda Wiki: also from Farore in a linked Seasons | Zelda Wiki (*Linked*, *Secret*) |
| Green Luck / Green Holy Ring | which of the Dance Hall and Treasure Grove gives which | TourianTourist's pairing |
| Subrosian and Green Joy Rings | Mt. Cucco (three sources); StrategyWiki: Goron Mountain | Mt. Cucco |
| Iron Pot giver | Zelda Wiki: Inga; Zelda Dungeon, StrategyWiki: Mrs. Ruul | Mrs. Ruul, Inga noted |
| Megaphone | Zelda Wiki: Eyeglass Pond; Zelda Dungeon, StrategyWiki: North Horon | North Horon |
| Phonograph | Zelda Wiki: Horon Village; Zelda Dungeon: windmill, Eastern Suburbs | Eastern Suburbs |
| Biggoron | Zelda Wiki's secrets page: Mt. Cucco; its other pages and Zelda Dungeon: Goron Mountain | the secret's text says only "atop the mountain" |
| Winter cave Piece of Heart | StrategyWiki: Eastern Suburbs; Zelda Dungeon: Woods of Winter | Eastern Suburbs |
| Moblin's Keep piece | needs the flute (StrategyWiki) or the Power Bracelet (Zelda Dungeon) | both tagged; filed under Natzu |

Unverified, so kept general: the Lost Woods directions to the Noble Sword, what the Goron with the
L-2 Ring Box wants, whether the Red Holy Ring is also a Subrosian prize, how you enter the Room of
Rites, and the Magic Potion (left out).
