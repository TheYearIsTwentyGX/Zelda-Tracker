# Oracle of Ages

Oracle of Ages is built entirely from checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): nine JSON files in
`shared/src/commonMain/composeResources/files/ooa/` plus the tracker lines in `core/Games.kt`.
`OoaChecklistDatasetTest` checks the counts and cross-references below, and `ChecklistDatasetsTest`
checks the format.

The data is for the original Game Boy Color release (the 3DS Virtual Console is the same game).
Nothing is fetched at runtime. The files were compiled by hand from the sources below, together with
Oracle of Seasons' (`docs/oos.md`), since the two games share rings, Gasha, secrets and companions.

## Save files

Three files, `file_1` to `file_3` ("File 1" to "File 3"). Zelda Wiki's *Saving* page: "Three save
files are also available for use in the game". These are the ids the coming-soon placeholder
already had.

## Linked games

Finishing Ages gives the *Secret to Holodrum*, which starts Seasons as a **linked game**, and the
*Secret to Labrynna* from a finished Seasons starts a linked Ages. A linked game adds the twenty
secrets, extra upgrades, Biggoron's Sword, a Heart Container and the true final battle against
Twinrova and Ganon. Linked-only content is **listed, not hidden**:

- items that only exist in a linked game are tagged **Linked** (Twinrova, Ganon, Biggoron's Sword,
  Bombchus, the linked Heart Container, the Sea of Storms Gasha spot, linked-only rings);
- upgrade tiers only a linked game reaches say so in their name: *Master Sword (Linked)*,
  *Mirror Shield (Linked)*, *99 of each seed (Linked)*, *50 Bombs (Linked)*;
- the *Linked Secrets* tracker covers the passwords and every secret in both directions.

## Trackers and counts

| File | Tracker | Sections (items) |
|---|---|---|
| `main_quest.json` | Main Quest | Prologue 2 (value 0), Essences of Time 9 (the 8 dungeons and Rescue Nayru, value 0), Finale 3 (Veran, then Twinrova and Ganon tagged Linked). Summary: Essences of Time of 8. |
| `items.json` | Items & Equipment | Items 15 (with the five seeds), Upgrades 7 rows (16 steps), Quest Items 23 rows (24 steps). Summaries: seeds of each (20 + the satchel tiers' values, 99 at most) and bombs (10 + the bomb tiers', 50 at most). |
| `harp_songs.json` | Harp of Ages | The Harp (value 0) and the Tunes of Echoes, Currents and Ages. Summary: Tunes of 3. |
| `heart_pieces.json` | Heart Pieces | Pieces of Heart 12, Heart Containers 9 (8 bosses and 1 from a linked secret). Summary: hearts = 3 + pieces / 4 + containers, 15 at most. |
| `rings.json` | Rings | Ring Box 1 row (L-1, L-2, L-3: 3 steps), Magic Rings 64. Summary: Rings of 64. |
| `gasha.json` | Gasha Spots | 16 soft-soil spots, each tagged with its ring classes and Past or Present |
| `trading.json` | Trading Sequence | 13: the 12 trade items and the Noble Sword |
| `animal_companions.json` | Animal Companions | 6 |
| `linked_secrets.json` | Linked Secrets | Linking 4, Secrets from Seasons 10, Secrets for Seasons 10 (2 steps each) |

Cross-references (`requires`), checked by the test:
- Veran requires all eight dungeons; Twinrova requires Veran and Ganon requires Twinrova.
- Each trade requires the one before it, from the Poe Clock to the Noble Sword.
- Choosing a partner requires meeting Ricky, Dimitri and Moosh.

Ricky's Gloves are in Items and Animal Companions; each tracker counts its own.

## Shared with Oracle of Seasons

- **Rings** are one 64-ring collection in both games. `rings.json` has **the same item ids in the
  same order** as `files/oos/rings.json` (the in-game ring numbers 1–64), so the two can be
  cross-referenced later. Only the `howTo`, `area` and tags differ: each file says where to get the
  ring in its own game, and the 10 rings that can only be found in Seasons are tagged
  *Not in Ages* here (and brought over with the Ring Secret).
- **Linked Secrets** use the same secret ids in both files (`fairy_secret`, `deku_secret` …); a
  secret told here is in this file's *Secrets from Seasons*, and in Seasons' *Secrets for Ages*.
- Items that exist in both games share ids: `sword`, `shield`, `seed_satchel`,
  `seed_satchel_capacity`, `bomb_capacity`, `power_bracelet`, `roc_feather`, `boomerang`, `shovel`,
  `zora_flippers`, `strange_flute`, `bombchus`, `biggorons_sword`, the five `*_seeds`, `ricky`,
  `dimitri`, `moosh`, `ricky_gloves`, `companion_flute`, `bomb_flower`, `huge_maku_seed`,
  `twinrova`, `ganon`.

## What's tracked, and why

- **Main Quest**: the eight dungeons in order, each with its boss, dungeon item, Essence and Heart
  Container. Veran possessing Nayru and saving the Maku Tree are a value-0 prologue, and rescuing
  Nayru in Ambi's Palace (after Mermaid's Cave, for the Tune of Ages) sits between the sixth and
  seventh dungeons, also worth no Essence.
- **Items & Equipment**: what the Item and Quest Status screens show. Upgrades are tiers: Sword
  (Wooden → Noble → Master), Shield (Wooden → Iron → Mirror), Power Bracelet → Power Glove,
  Zora's Flippers → Mermaid Suit, Switch Hook → Long Hook, seed and bomb capacity. Ages has no
  Roc's Cape, no Magical Boomerang and no Slingshot (the Seed Shooter instead); its Boomerang is
  optional. The Harp is in its own tracker and the Ring Box is in Rings.
- **Harp of Ages**: the Harp from Nayru's house and its three tunes, which unlock time travel in
  stages (Echoes opens Time Portals, Currents takes you to the present from anywhere, Ages either
  way).
- **Heart Pieces**: 12 pieces and 8 boss containers (all three sources agree), plus the one Heart
  Container a linked secret gives (the Fairy Secret or the Graveyard Secret, whichever direction you
  linked). 3 + 3 + 9 = 15 hearts; a linked or Hero's game starts with 4, so it reaches 16, which the
  linked container's notes say.
- **Gasha Spots**: the 16 soft-soil spots, tagged *Past* or *Present* and with their ring classes;
  the Sea of Storms spot is linked-only. The ~30 Gasha Seed sources aren't tracked.
- **Trading Sequence**: optional; it ends with Patch reforging the Broken Sword into the Noble
  Sword (the Master Sword if a secret already gave you the Noble Sword). Biggoron's Sword is the
  linked Elder Secret, not a trade reward.
- **Animal Companions**: the flute decides your partner and the shape of the Nuun Highlands: the
  Lynna City shop flute gives Dimitri, the Shooting Gallery flute gives Ricky, neither gives Moosh
  (Zelda Wiki, StrategyWiki and Zelda Dungeon agree). A linked game keeps the first game's partner.
- **Linked Secrets**: as in Seasons, each secret has two steps: the reward where it's told, then the
  return secret told to Farore in the Hall of Secrets, which gives the same reward in the other game.

## Sources

- **Zelda Wiki** (zeldawiki.wiki), through its MediaWiki API
  (`/w/api.php?action=parse&page=…&prop=wikitext&format=json`): *Magic Ring* and each ring's page,
  *Ring Box*, *Gasha Seed*, *Gasha Tree*, *Piece of Heart*, *Heart Container*, *Trading Sequence*,
  *Linked Game*, *Linked Game/Secrets*, *Secret*, *Vasu*, *Saving*, *Items in Oracle of Ages*,
  *Harp of Ages* and the three tunes, *Ricky*, *Dimitri*, *Moosh*, *Strange Flute*, *Nuun
  Highlands*, the dungeon, boss and key item pages.
- **Zelda Dungeon** (zeldadungeon.net, via the Wayback Machine): *Oracle of Ages Heart Pieces*,
  *Oracle of Ages Trading Sequence*, *Rings* (including its random-ring source table, which cites
  TourianTourist's Ages guide), *Oracle of Ages Items* and the walkthrough chapters for each dungeon
  and the Black Tower.
- **GameFAQs**: TourianTourist's *Ring Guide* (the Seasons edition, via the Wayback Machine), whose
  tables cover both games' ring classes and exclusives. The Ages edition couldn't be fetched
  (GameFAQs refuses scripts and it isn't archived), so Ages-specific ring spots rest on Zelda
  Wiki's ring pages, Zelda Dungeon and StrategyWiki.
- **StrategyWiki**: *The Legend of Zelda: Oracle of Ages* walkthrough chapters, *Heart Pieces*,
  *Gasha Seeds*, *Trading Sequence*, *Rings*, *Items*, *Maple*, *Minigames* and *Secrets to and from
  Holodrum*.

## Discrepancies

| Topic | Sources | Followed |
|---|---|---|
| Ring #54 | Zelda Wiki, Zelda Dungeon, TourianTourist: Rupee Ring; StrategyWiki: Wealth Ring | Rupee Ring |
| Victory Ring | majority: a game started with the Hero's Secret; StrategyWiki: beating the linked final boss | majority |
| Maple's rings | TourianTourist, Zelda Dungeon: classes 2–3 only; Zelda Wiki's ring pages: more | classes 2–3 tagged *Maple* |
| Heart Ring L-1 and Swimmer's Ring | TourianTourist: Seasons-only; Zelda Wiki: also from Farore in a linked Ages | Zelda Wiki (*Linked*, *Secret*) |
| Blue Luck Ring | Zelda Wiki: a cave above Zora Village in the present; TourianTourist: Zora Village in the past | "a cave by Zora Village", both in notes |
| Blue Ring | Zelda Wiki: under a statue, with the Power Glove; StrategyWiki: a chest under a coffin by the Ancient Tomb | Zelda Wiki |
| L-2 Ring Box | Zelda Wiki: the Lynna Item Shop's upper floor; Zelda Dungeon's route goes down the hidden stairs | "the hidden part of the shop" |
| Restoration Wall Piece of Heart | StrategyWiki: West Lynna; Zelda Dungeon: Restoration Wall (Past) | filed under Talus Peaks, Zelda Wiki's name for the nearby Gasha spot |
| Talus Peaks currents piece | Zelda Dungeon: Symmetry Village outskirts; StrategyWiki: south of Tokkey's | Talus Peaks |
| Poe Clock giver | Zelda Wiki: Allan; Zelda Dungeon: the Clock Poe | the Clock Poe |
| Tune of Currents | Zelda Wiki: play the Tune of Echoes for Tokkey; StrategyWiki: play the Harp | Tokkey, "play the Harp for him" |
| Ancient Tomb access cave | StrategyWiki: "Moonlit Cave"; Zelda Wiki: Tokay Cave via the Tokay statue | described without a name |
| Bomb capacity | Zelda Wiki: 10 → 30 (Talus Peaks fairy) → 50 (secret); StrategyWiki's sidebar: the secret does 10 → 30 → 50 | Zelda Wiki |
| Moosh | StrategyWiki: Ghini; Zelda Wiki: "monsters" | "monsters" |
| Rolling Ridge Gasha spots | the two sources name them differently | matched by age and ring class; the pairing of the two class 1–3 past spots is inferred |

Unverified, so kept general: exact Ages ring spots beyond Zelda Wiki's one-liners, the Light Ring
L-2's 5% Shooting Gallery chance (Zelda Wiki only), and how you enter the Room of Rites.
