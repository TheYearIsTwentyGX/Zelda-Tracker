# The Minish Cap: Carlov's Figurines

`shared/src/commonMain/composeResources/files/mc/figurines.json` backs the Minish Cap *Carlov's
Figurines* tracker. `McFigurineDatasetTest` checks it, and `FigurineOddsTest` checks the odds
maths described below.

Nothing is fetched at runtime. `scripts/build-mc-figurines.py` rebuilds the JSON from two
sources, caches the downloads in `build/figurines-cache`, and exits non-zero if a check fails:

1. **Zelda Wiki**, [Figurines in The Minish Cap](https://zeldawiki.wiki/wiki/Figurines_in_The_Minish_Cap),
   read through the MediaWiki API: number, name, the gallery's description and who is pictured.
2. **The zeldaret/tmc decompilation**, `src/fileselect.c` (`gUnk_080FC3E4`), pinned to commit
   `6fb6dfb4`. It gives one row per figurine: the egg colour, and the story level, dungeon flag or
   Kinstone fusion that adds the figurine to Carlov's machine. `src/object/figurineDevice.c` holds
   the code that reads the table and works out the odds.

## Counts

- **136 figurines.** 130 can be won before the final battle. Carlov's "final series" (#131–136)
  only appears after the credits. Zelda Wiki, StrategyWiki and Zeldapedia all agree.
- **Categories** come from the egg colour in the game's table: 49 Characters (blue), 21 Scenes
  (red), 49 Enemies (green) and 17 Bosses (gold). Zelda Wiki's Figurine Gallery page names the
  colours, and its tables give the same split.
- **11 figurines need a side task** as well as the story (Zelda Wiki's "Optional" table): Marcy,
  Beedle, the Crenel Hermit, the Joy Butterfly, Gina, Goron, the Goron Merchant, Spookter &
  Spekter, and the three golden enemies.

## Checkpoints

The game decides which figurines are in the machine each time Link walks up to it
(`sub_0808804C`, `sub_08088160`). The tracker's checkpoints follow the game's own levels, in story
order. The *Adds* column counts figurines with no side task, and it matches Zelda Wiki's Figurine
Gallery "U" column exactly.

| Checkpoint id | Game check | Adds | Pool |
|---|---|---|---|
| `gallery_opens` | none (level 0) | 48 | 48 |
| `cave_of_flames` | `LV2_CLEAR` | 3 | 51 |
| `king_possessed` | `SOUGEN_08_TORITSUKI`: Vaati takes over King Daltus | 9 | 60 |
| `fortress_of_winds` | `LV3_CLEAR` | 25 | 85 |
| `big_blue_chuchu` | dungeon flag `LV4_10_BOSSDIE` | 1 | 86 |
| `temple_of_droplets` | `LV4_CLEAR` | 12 | 98 |
| `royal_crypt` | `OUBO_KAKERA`: spoke to King Gustaf | 5 | 103 |
| `palace_of_winds_entered` | `LV5_31_CAP_0` | 6 | 109 |
| `palace_of_winds` | `LV5_CLEAR` | 10 | 119 |
| `vaati_defeated` | `saw_staffroll`: the credits | 6 | 125 + 11 side tasks = 136 |

A figurine with a side task sits at the earliest checkpoint the game also requires. That is the
first checkpoint for all of them except two:

- **Beedle (#22)** needs `king_possessed` as well as the dusty carpet, in the US build only. The
  EU and JP builds check the dust flag alone.
- **Spookter & Spekter (#67)** needs `royal_crypt` as well as the fusion.

A save file records the furthest checkpoint reached and which side tasks are done. A figurine is
*available* when both hold, or when it is owned. The pool is what the odds use.

## The odds

From `figurineDevice.c`, with **U** the figurines available to the save file and **C** the number
owned:

- **Shown chance** for one shell: ⌊100 × (U − C) / U⌋ %, raised to 1 % while any are left
  (`sub_0808826C`). Each extra shell adds 1 %, up to 100 % (`FigurineDevice_ChangeShellAmount`).
  This is Zelda Wiki's formula, P = (99 + S − 100C/U) % rounded down, apart from the 1 % floor.
  `FigurineOddsTest` checks the two agree for every U ≤ 136.
- **Hidden minimum.** Just before the roll, `FigurineDevice_GetChanceBasedOffFigurineCount`
  raises the chance to at least **15 %** while fewer than 50 are owned, **12 %** below 80, **9 %**
  below 110 and **6 %** after that. The machine still shows the lower number. The roll is a
  uniform 0–99 compared against that chance. So the last figurine shows 1 %, but each one-shell
  draw really has a 6 % chance.
- **One shell a draw is always cheapest.** An extra shell adds 1 % but costs a whole draw's
  worth. The card shows the expected shells for the next new figurine (100 / chance) and for every
  figurine available now (that sum, step by step). It also shows how many shells make the machine
  show 100 % (101 − the one-shell chance).

## Text

Descriptions are the **North American** gallery text, as Zelda Wiki gives it. `{{Player Name}}`
reads "Link", and line breaks are joined with spaces.

## Discrepancies

- **StrategyWiki's descriptions mix in European text.** It disagrees with Zelda Wiki on about 30
  descriptions. Zeldapedia lists both versions for most of them, and in each case StrategyWiki
  has the European one. Examples: Percy "lives in Trilby Highlands" (EU) vs "Western Wood" (NA),
  Mazaal "Appears in the Palace of Winds", and Gleerok called a "Gleeok". The rest are small
  punctuation differences.
- **Names.** StrategyWiki writes "Ezlo", "Town Minish", "Armos Statue" and "Acro Bandits". Zelda
  Wiki and Zeldapedia write "Ezlo (Cap)", "Town Picori", "Armos" and "Acro-Bandits"; we use those.
- **When the gallery opens.** Zelda Wiki's Figurine Gallery page puts the first 48 under
  "Obtain the Earth Element", and its Carlov page says the gallery opens with the White Sword. The
  sword is the Picori Blade with the Earth Element infused, so the two agree. The game's table has
  no condition on these 48, so the gate is the gallery itself.
- **The Palace of Winds' first six.** Zelda Wiki says they come with defeating the Palace of
  Winds' sub-boss. The game checks `LV5_31_CAP_0`, which the decomp annotates as Ezlo's talk at
  the Palace of Winds entrance. We follow the game and call the checkpoint "Palace of Winds
  reached". The decomp's annotation is a community note, so this one is less certain than the
  others.
- **Golden enemies.** Zelda Wiki names one fusion for each golden Octorok, Tektite and Rope. The
  game accepts any of three fusions for each (`sub_08088160`, cases 2–4). The requirement text
  gives the wiki's fusion as an example.
- **After the credits**, the game's level 9 also satisfies every figurine gated by a dungeon flag:
  Big Blue Chuchu, the Crenel Hermit and Goron. The tracker doesn't model this. Those three show
  as available once their checkpoint or side task is ticked, and they are easy to reach anyway.
- **European build.** The EU table differs for a few figurines: Sorcerer Vaati and Eenie & Meenie
  are in the first set, Gorman comes with `king_possessed`, and the levels of Rock Chuchu and
  Moldorm swap. The tracker follows the North American build, like its text.
- **Zelda Dungeon** couldn't be checked: its wiki sits behind a Cloudflare challenge that blocks
  scripted reads. StrategyWiki stood in as the second guide. Zeldapedia (zelda.fandom.com) was the
  tiebreaker on text, and the decompiled game was the tiebreaker on unlocks and categories.
