# Checklist trackers

A **checklist** is a tracker made only of data: one JSON file and one line in `core/Games.kt`. A
checklist has a tab per section, region cards with counts, spoiler-safe search, a *Not done*
filter, a page per item and a Progress Overview row. Nothing else needs writing, so new games are
built from checklists. The bespoke trackers (BotW, TotK and The Minish Cap) keep their own
packages.

The code is in `shared/src/commonMain/kotlin/gx/zelda/checklist/`:

| File | What it does |
|---|---|
| `model/ChecklistCatalog.kt` | The format below: counts, summaries, the next-step rule and `problems()`, which validates a file |
| `model/ChecklistProgress.kt` | One save file's progress (`levels`, by item id) and the viewing prefs |
| `model/Formula.kt` | The summary formula parser, and `formatAmount` ("5¼") |
| `data/ChecklistStorage.kt` | DataStore keys, per save file; reads and writes linked items' levels where they're stored |
| `data/ChecklistProgressProvider.kt` | The overview row |
| `model/StoryGate.kt`, `data/StoryProgress.kt` | [Story gating](#story-gating): what the game's Main Quest has unlocked on a save file |
| `model/SameAs.kt`, `data/ChecklistLinks.kt`, `data/ChecklistLinkMigration.kt` | [Linked items](#linked-items): one item that's the same as another's, or one of its tiers, in another file; their validation and the merge of old saves |
| `ui/` | The list, tabs and filters (`ChecklistViewModel`/`ChecklistScreen`) and the item page |

## Adding a checklist to a game

1. Write the JSON file at `shared/src/commonMain/composeResources/files/<game id>/<name>.json`.
2. List it in the game's `trackers` in `core/Games.kt`:

   ```kotlin
   Tracker(TrackerKind.CHECKLIST, "Heart Pieces", "All 40 pieces and 8 containers, with your hearts", dataFile = "eow/heart_pieces.json"),
   ```

   `dataFile` is relative to `files/`. Only a checklist has one, and every checklist must have one.
3. To give the game a Progress Overview, list `Tracker(TrackerKind.OVERVIEW, …)` first. Each
   checklist then gets a row, in the order the game lists them.
4. Run `./gradlew :shared:jvmTest`. `ChecklistDatasetsTest` loads every checklist of every game
   with unknown keys rejected, so a misspelt field fails. It also checks `problems()` is empty.
   Then add a dataset test for the game's own counts and the links between its files, and write
   the sources up in `docs/<game>.md`. Echoes of Wisdom is the worked example: `EowDatasetTest`
   and `docs/eow.md`.

Progress is saved under `<game id>_checklist_<file name without .json>@<save file id>`, and the
prefs under `<game id>_checklist_<file name>_prefs`. **Renaming a file or an item id loses saved
progress**, so ids are permanent once shipped. Remove an item by deleting it; its stored id is
ignored.

## Format

```json
{
  "regions": ["Hyrule Field", "Jabul Waters"],
  "noun": "heart", "plural": "hearts",
  "hiddenLabel": "Uncollected heart",
  "summaries": [{"label": "Hearts", "formula": "3 + floor(pieces / 4) + containers"}],
  "sections": [
    {
      "id": "pieces",
      "title": "Pieces of Heart",
      "noun": "piece",
      "hiddenLabel": "Uncollected Piece of Heart",
      "note": "Every four make a heart.",
      "items": [
        {
          "id": "zora_cove_ledge",
          "name": "Zora Cove ledge",
          "region": "Jabul Waters",
          "area": "Zora Cove",
          "howTo": "Swim north from the cove and climb the ledge with a Water Block echo.",
          "requires": ["jabul_ruins"],
          "after": "Jabul Waters rift",
          "missable": false,
          "reward": "Piece of Heart",
          "notes": "Also reachable later with the Bind ability.",
          "tags": ["Water"]
        }
      ]
    },
    {
      "id": "upgrades", "title": "Upgrades", "noun": "upgrade",
      "summary": {"label": "Might Crystals spent", "formula": "upgrades.value"},
      "items": [
        {"id": "sword", "name": "Sword", "value": 5,
         "tiers": [{"name": "Level 2"}, {"name": "Level 3", "value": 10}, {"name": "Level 4", "value": 20, "reward": "…"}]}
      ]
    }
  ]
}
```

Leave out an optional field rather than writing `null` or `""`.

### Top level

| Field | Required | Meaning |
|---|---|---|
| `sections` | yes | The tabs, in order. A file with one section shows no tabs. |
| `regions` | no | Region names in display order. Leave it out (and `region` off every item) for a list with no geography. |
| `noun`, `plural` | no | What one item is called, for the overview's lines ("3 echoes still to do in Suthorn"). The default is "entry". `plural` defaults to the regular plural ("quest" → "quests", "entry" → "entries"); spell out irregular ones ("echo" → "echoes"). |
| `hiddenLabel` | no | What a spoiler-hidden row shows instead of its name. Default "Unchecked <noun>". |
| `summaries` | no | Totals shown above the list and on the overview row; see [Formulas](#formulas). |

### Section

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | `[a-z0-9_]+`, unique among sections and items. Formulas refer to it. |
| `title` | yes | The tab's name. It is also the heading for any of its items without a region. |
| `items` | yes | At least one. |
| `noun`, `plural`, `hiddenLabel` | no | Override the top-level ones for this section. |
| `note` | no | One line shown on the section's summary card. |
| `summary` | no | A total shown only on this section's tab, e.g. crystals spent. |
| `separate` | no | `true` to count the section on its own: another release's version of a list, like Link's Awakening's Switch-remake seashells beside the Game Boy ones. Its tab, its count on the overview's detail line and formulas that name it work as usual, but it's left out of the file's total (the tracker's and overview's bar), the *All* tab and *Next*. At least one section must not be separate. |

### Item

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | `[a-z0-9_]+`, unique across the whole file. Progress is saved by it, so never change it. |
| `name` | yes | The row's label, as the game names it. |
| `region` | no | One of `regions`; the item is listed under it. Without one, it's listed under its section's title. |
| `area` | no | The exact spot: a cave, a house, a dungeon. |
| `howTo` | no | How to get it, precise enough to find it. The row shows two lines; the page shows it all. |
| `requires` | no | Ids of items **in the same file** to do first. The row says *Needs …* until they're ticked. Must not loop. |
| `after` | no | A story point, as text: "After Jabul Ruins". Use `requires` instead when the story point is an item in the file. |
| `unlockedBy` | no | Ids of steps in the game's Main Quest (its story file), all of which must be done before this can be had. While *Hide what the story hasn't unlocked yet* is on, the item is hidden until then; see [Story gating](#story-gating). Leave it out for anything available from the start. |
| `missable` | no | `true` if it can be missed for good. It gets a tag, and the *Missable* filter chip appears for the file. |
| `value` | no | What one step is worth to `<section>.value` in formulas. Default 1. Use it for e.g. a chest of 5 Might Crystals. |
| `reward` | no | What it gives. |
| `notes` | no | Anything else; shown on the page. |
| `tags` | no | Short labels shown on the row and searchable, e.g. an echo's category. |
| `tiers` | no | Stages of one thing on one row (upgrade levels). Each tier is a step; see below. |
| `sameAs` | no | The same real thing as another item of the game, or one tier of it: `[file:]item[@tier]`, e.g. `items:wallet@2`. The two share one progress; see [Linked items](#linked-items). Only an item without tiers can have one. |

**Tiers.** An item with tiers is done when all of them are. Its row has a "2 / 4" button instead of a
checkbox, and each tap does the next tier (wrapping back to none). On the page each tier can be
ticked. Ticking one ticks all the tiers before it, and unticking one clears it and all the tiers
after. Each tier has a `name` and may have a `howTo`, a `reward` and a `value`, which overrides
the item's for that step. Counts are in steps, so an item with 3 tiers counts 3 toward its section.

## Formulas

A formula combines numbers, `+ - * /`, parentheses, `floor(x)`, `min(a, b, …)`, `max(a, b, …)` and
names. Each name is a section id or an item id:

- `pieces` is how many steps of section `pieces` are done (with no tiers, that's items ticked).
- `pieces.value` is the sum of `value` over those steps.
- An item id works the same for that one item: `bomb_capacity` is its tiers done and
  `bomb_capacity.value` their values. This gives two upgrades in one section a total each, as A
  Link to the Past's bomb and arrow capacities have.

The maximum is worked out by running the same formula with everything done, so "Hearts 5¼ / 20"
needs no separate total. Values print like the game shows them: `12`, `5¼`, `⅔`, otherwise up to
two decimals. Dividing by zero gives 0. Examples:

- Hearts from quarter pieces and containers, in whole hearts: `3 + floor(pieces / 4) + containers`
- The same, showing the quarters: `3 + pieces / 4 + containers`
- Crystals collected where some chests hold several: `crystals.value`
- A capacity that starts at 10 and grows with each tier's `value`: `10 + bomb_capacity.value`

`problems()` rejects a formula that doesn't parse or that names an unknown section or item.

## Behaviour

- **Save files.** Progress is per save file and follows the game's active file, which every
  tracker of the game shares. Viewing prefs (*Hide unchecked*) are per checklist and shared by
  every file.
- **Spoilers.** With *Hide unchecked* on, a row with nothing done shows only its `hiddenLabel` and
  heading until tapped to reveal it or ticked. A partly done tiered item counts as started. While a
  row is hidden:
  - search only matches its heading;
  - the *Missable* chip never matches it;
  - an item that `requires` it names it by its `hiddenLabel`.
- **Search** reads the name, area, region, howTo, after, reward, notes, tags, tier names and section
  title of every row that isn't hidden.
- **Mark all complete.** A row under the filters toggles the whole tab, and a tri-state box on each
  region card toggles that card; see the README for how it looks. The engine's side:
  - `ChecklistUiState.bulkItems(group)` is what a toggle acts on: the rows the filters leave on the
    tab (spoiler-hidden ones too), or those under one heading. With a search or chip on it acts on
    those rows only and says so ("Mark 3 shown complete"), rather than being disabled: that's what
    the rows on screen suggest. *All* leaves out `separate` sections, so they change only from their
    own tab, and its dialog says which tabs it leaves.
  - `bulkScope(group)` gives the [`BulkScope`](#mark-all-complete-in-another-tracker) for it. Its
    counts are items: a tiered item is done only at its top tier.
  - `ChecklistViewModel.setAllDone(group, done)` sets every tier (or clears it to 0) in one
    DataStore edit through `ChecklistStorage.setLevels`, which returns a `MapSnapshot` of the levels
    it replaced. `undoBulkChange` restores that snapshot, on the file it was made on, again in one edit.
- **Counts.** Tabs and region headers count every item on that tab, whatever the filters leave
  showing. The file's total, and the *All* tab, leave out `separate` sections. Story gating
  never changes a count either.
- **Overview row** (`ChecklistProgressProvider`):
  - the bar is steps done of all, less `separate` sections;
  - the detail line lists each summary, then each section's count (when there's more than one
    section);
  - *Next* is the first item, in file order, that isn't done and whose `requires` are all done.
    Items in `separate` sections are never suggested, nor ones story gating hides. So **order
    items in the order you'd suggest doing them**. With *Hide unchecked* on and that
    item not started, it says only how many are left there ("2 pieces still to do in Hyrule
    Field").

## Story gating

*Hide what the story hasn't unlocked yet*, in Settings → Progress, is one switch for every game,
off by default. It makes a checklist follow the game's Main Quest on the active save file.

- **The story file** is the game's `<game id>/main_quest.json`, when the game lists it
  (`storyFileOf` in `data/StoryProgress.kt`). A game without one, such as LoZ, AoL, the
  stage-select games or the bespoke trackers, gates nothing. The Main Quest itself is never gated.
- **Locked** (`StoryGate.isLocked`): the switch is on, a step in the item's `unlockedBy` isn't done
  on the save file, and nothing on the item is done. So anything ticked, or partly done, is never
  hidden, even when it was ticked ahead of the story. A step id the story file doesn't have gates
  nothing.
- **A ticked step implies what it requires.** For gating, a ticked step counts every step it
  `requires`, however far back, as done. So a save that ticked a temple but never its opening
  step still unlocks what that step gates. This is gating only: the Main Quest's rows and counts
  still show exactly what was ticked.
- **A locked item** has no row:
  - search and the *Missable* chip can't find it, even by its heading;
  - mark-all leaves it alone, and its dialog says how many it left;
  - *Next* never suggests it;
  - an item that `requires` it names it by its `hiddenLabel`.
- **Counts keep their true totals.** Each region card ends with "3 more unlock later in the story".
  The list ends with the same line for headings with nothing unlocked. The overview's detail line
  adds "· 3 unlock later", and once everything unlocked is done, *Next* says so.
- **Spoilers.** *Hide unchecked* works as before on the unlocked rows, and nothing locked is ever
  named.
- **The item page** shows its steps' names as "After …" when it has no `after` text.

Writing links:

- Point `unlockedBy` at the step that **opens** the item. When it's opened by optional content (a
  side quest, a shop), use the step that opens that content, and keep the finer prerequisite in
  `after` or `requires`.
- Several ids mean all of them. There's no "either of". **When in doubt, gate earlier**: hiding
  something that's already available is the mistake to avoid.
- `scripts/derive-story-links.py <game>` proposes a link for every item from its `after`, `area`
  and `region`, using that game's table in the script, and prints a table to review. It raises an
  item's gate to that of anything it `requires` that comes later in the story. With
  `--apply`, it writes the links into the files as text, so hand-made layouts survive.
- Steps must be untiered. `StoryLinksDatasetTest` checks every link in every game, and that no
  item unlocks before something it `requires` (by `storyOrder`). The game's own dataset test
  spot-checks a few links.

## Linked items

Some games list one real thing in two checklists: Skyward Sword's Medium Wallet is a tier of Items &
Equipment's Wallet and Batreaux's 5-crystal reward in Gratitude Crystals. A **`sameAs` link** makes
the two rows one progress, so ticking either ticks both, on every screen at once.

```json
{"id": "batreaux_5", "name": "5 Gratitude Crystals", "sameAs": "items:wallet@1", "reward": "Medium Wallet"}
```

**The format** is `[file:]item[@tier]` (`SameAs` in `model/SameAs.kt`):

- `file` is another of the game's checklists, by its file name without `.json` (`items` for
  `ss/items.json`). Leave it out for an item in the same file: `"sameAs": "goddess_sword@2"`.
- `@tier` (1-based) names one tier of an item with tiers, and is required for one; an item without
  tiers takes none.
- Only an item **without tiers** can have a link. It's the *linked* row; the item it names is the
  *canonical* one.

**Which side holds the progress (the rule).** The canonical item's level is the one stored; a linked
row keeps no level of its own and reads and writes the canonical one. So, when you write a link:

- **An item with tiers is always canonical**, since one tier count can hold every tier and a plain
  tick can't. Link the plain row to its tier: the reward to `items:wallet@2`, never the other way.
- **Between two items without tiers**, the one in the file the game lists **first** (in `Games.kt`)
  is canonical, so the link goes on the one in the later file (Gratitude Crystals' Batreaux reward →
  `heart_pieces:batreaux_heart_piece`). Within one file, the later item links to the earlier.
- **Declare each link once**, on the linked row. The other side needs nothing, and a link back is
  refused (it would be a loop). Several rows may link to one item or tier.

**How a tier link behaves.** "Batreaux's 30-crystal reward is Wallet tier 2":

- the reward shows ticked while the Wallet is at tier 2 or more;
- ticking the reward raises the Wallet to at least tier 2 (so tier 1 comes too, as ticking a tier on
  the Wallet's own page does), and keeps a higher tier;
- unticking it lowers the Wallet to below tier 2 (so tiers 3 and 4 go too), and keeps a lower one.

A link to an item without tiers is the same with tier 1: ticked is ticked.

**Behaviour through links:**

- **Storage** (`ChecklistStorage`). Progress is read with the game's other checklists' levels on the
  same save file (`ChecklistProgress.linked`), only again when one of them changes. Every change,
  a tick or a mark-all, is **one DataStore edit** writing back whichever files it touched. Undo
  snapshots the stored levels the change replaced (a `MapSnapshot` of `LevelRef`s: file and item),
  so it puts back exactly those, in one edit.
- **Counts.** Each file counts **every row it lists**, linked or not, so a file's count means the
  same as before: Heart Pieces counts its 24 pieces, though 6 are also Goddess Cubes' chests. There's
  no total across files, so nothing is counted twice in one number. Two rows of **one** file that
  are linked (SS's Main Quest lists the Sacred Flames and the sword's forms) both count there, as
  they did before. Summaries and their maximums read through links.
- **Mark all complete** marks a linked row by its link, so it changes the canonical item, and its
  dialog adds "Rows shared with another tracker change there too." Clearing a tier link lowers the
  canonical item below that tier, so clearing Batreaux's rewards takes the Wallet back to the Small
  one.
- **Spoilers and story gating** go by the linked level: a row ticked in another tracker isn't
  spoiler-hidden, and it's never locked (ticked is never locked). A Main Quest step that's a linked
  row (SS's Kikwis step is the Slingshot) counts as done from the item it names, so what it gates
  unlocks when either is ticked.
- **The item page** says where a linked row is shared: "Shared with Items & Equipment: ticking it in
  either ticks it in both", or the item on the same list.
- **The overview's** rows and *Next* read through links, like the list.
- **Another tracker** that reads checklist levels directly must read the canonical place: SS's Item
  Upgrades reads the Hylian Shield from the Lightning Round's tier (`ChecklistLink.via`).

**The migration** (`data/ChecklistLinkMigration.kt`, a DataStore migration after Skyward Sword's
`SharedProgressMigration`). Saves from before a link was added can have one side ticked and not the
other. On each save file on its own, once, before anything reads the store: a linked row's old tick
raises its canonical item to at least the linked tier (ticked on either side is ticked, and the
higher tier wins), then the row's own entry is removed. It moves rather than copies, so it's
idempotent: nothing writes a linked row's own entry any more. A link added in a later release is
merged the same way on the next start. A store that can't be read is left alone. It loads the
checklists only of the games in `ChecklistLinks.GAMES`, and only when the save has progress there.

**Validation.** `problems()` checks links within a file, and `ChecklistLinks.problems(game,
catalogs)` those into another file: the file and item exist, the file isn't the item's own (write
it without the name), the tier is given only for an item with tiers and is in range, the target
isn't itself linked (no chains, so no loops either way), the item with a link has no tiers, and the
canonical rule above. `ChecklistLinksDatasetTest` runs it on every game, checks
`ChecklistLinks.GAMES` is exactly the games with links, and pins each game's links.

**Adding links to a game:** write `"sameAs"` on each linked row (as text, after its `name`, so the
file's layout survives), add the game to `ChecklistLinks.GAMES`, list the links in its
`docs/<game>.md` and its dataset test, and run `./gradlew :shared:jvmTest`. **Only link the same
event**: a dungeon and its Heart Container, a quest and a bottle you get halfway through it, or a
shop item that a chest also gives are related, not the same, and a link would tick one for the
other.

### Candidates in other games

Only Skyward Sword is linked so far (`docs/ss.md`, *Linked items*). For the rest,
`scripts/find-link-candidates.py` lists rows in two files of a game that may be the same thing: the
same name, a reward naming another file's row or tier, or a heart row and another file's row giving
a Piece of Heart or Heart Container in the same area or with most of its name. It's a heuristic,
run on 2026-10-22: each pair needs checking against the rules above before it's linked. Many are
the related-not-same kind (a dungeon step and its Heart Container, a Main Quest step and an item
found during it).

350 candidates in 13 games; the reason for each is in brackets.

<details><summary>A Link Between Worlds (albw): 27</summary>

- `heart_pieces:poh_rupee_rush_hyrule` ↔ `minigames:rupee_rush_hyrule` (same name, heart reward)
- `heart_pieces:poh_rupee_rush_lorule` ↔ `minigames:rupee_rush_lorule` (same name, heart reward)
- `heart_pieces:poh_octoball_derby` ↔ `minigames:octoball_derby` (same name, heart reward)
- `items:sword@1` ↔ `main_quest:captains_sword` (reward names a row)
- `items:lamp@1` ↔ `main_quest:captains_sword` (reward names a row)
- `items:zoras_flippers` ↔ `main_quest:smooth_gem` (reward names a row)
- `items:pegasus_boots` ↔ `main_quest:smooth_gem` (reward names a row)
- `items:bell` ↔ `main_quest:smooth_gem` (reward names a row)
- `items:gloves@1` ↔ `main_quest:power_glove` (reward names a row)
- `items:sword@2` ↔ `main_quest:master_sword` (reward names a row)
- `items:mail@2` ↔ `main_quest:lorule_castle` (reward names a row)
- `items:lamp@2` ↔ `minigames:treacherous_tower@3` (reward names a row)
- `items:net@2` ↔ `minigames:treacherous_tower@4` (reward names a row)
- `heart_pieces:poh_cucco_ranch` ↔ `minigames:cucco_ranch@3` (heart reward)
- `heart_pieces:poh_hyrule_hotfoot` ↔ `minigames:hyrule_hotfoot@2` (heart reward, same name)
- `heart_pieces:poh_fortunes_choice` ↔ `minigames:fortunes_choice` (heart reward)
- `heart_pieces:poh_treacherous_tower` ↔ `minigames:treacherous_tower@2` (heart reward)
- `heart_pieces:hc_eastern_palace` ↔ `main_quest:eastern_palace` (heart reward)
- `heart_pieces:hc_house_of_gales` ↔ `main_quest:house_of_gales` (heart reward)
- `heart_pieces:hc_tower_of_hera` ↔ `main_quest:tower_of_hera` (heart reward)
- `heart_pieces:hc_thieves_hideout` ↔ `main_quest:thieves_hideout` (heart reward)
- `heart_pieces:hc_desert_palace` ↔ `main_quest:desert_palace` (heart reward)
- `heart_pieces:hc_dark_palace` ↔ `main_quest:dark_palace` (heart reward)
- `heart_pieces:hc_swamp_palace` ↔ `main_quest:swamp_palace` (heart reward)
- `heart_pieces:hc_skull_woods` ↔ `main_quest:skull_woods` (heart reward)
- `heart_pieces:hc_turtle_rock` ↔ `main_quest:turtle_rock` (heart reward)
- `heart_pieces:hc_ice_ruins` ↔ `main_quest:ice_ruins` (heart reward)

</details>

<details><summary>A Link to the Past (alttp): 14</summary>

- `items:sword@1` ↔ `main_quest:uncle` (reward names a row)
- `items:shield@1` ↔ `main_quest:uncle` (reward names a row)
- `items:sword@2` ↔ `main_quest:master_sword` (reward names a row)
- `heart_pieces:hc_sanctuary` ↔ `main_quest:sanctuary` (heart reward)
- `heart_pieces:hc_armos_knights` ↔ `main_quest:eastern_palace` (heart reward)
- `heart_pieces:hc_lanmolas` ↔ `main_quest:desert_palace` (heart reward)
- `heart_pieces:hc_moldorm` ↔ `main_quest:tower_of_hera` (heart reward)
- `heart_pieces:hc_helmasaur_king` ↔ `main_quest:palace_of_darkness` (heart reward)
- `heart_pieces:hc_arrghus` ↔ `main_quest:swamp_palace` (heart reward)
- `heart_pieces:hc_mothula` ↔ `main_quest:skull_woods` (heart reward)
- `heart_pieces:hc_blind` ↔ `main_quest:thieves_town` (heart reward)
- `heart_pieces:hc_kholdstare` ↔ `main_quest:ice_palace` (heart reward)
- `heart_pieces:hc_vitreous` ↔ `main_quest:misery_mire` (heart reward)
- `heart_pieces:hc_trinexx` ↔ `main_quest:turtle_rock` (heart reward)

</details>

<details><summary>Zelda II: The Adventure of Link (aol): 9</summary>

- `levels:life` ↔ `spells:life` (same name)
- `levels:doll_three_eye_rock_palace` ↔ `palaces:three_eye_rock_palace` (same name)
- `levels:doll_great_palace` ↔ `palaces:great_palace` (same name)
- `items:candle` ↔ `palaces:parapa_palace` (reward names a row)
- `items:handy_glove` ↔ `palaces:midoro_palace` (reward names a row)
- `items:raft` ↔ `palaces:island_palace` (reward names a row)
- `items:boots` ↔ `palaces:maze_island_palace` (reward names a row)
- `items:flute` ↔ `palaces:palace_on_the_sea` (reward names a row)
- `items:cross` ↔ `palaces:three_eye_rock_palace` (reward names a row)

</details>

<details><summary>Echoes of Wisdom (eow): 42</summary>

- `collectibles:bottle_cuccos` ↔ `side_quests:cuccos_on_the_loose` (same name)
- `collectibles:bottle_zappy_shipwreck` ↔ `heart_pieces:poh_zappy_shipwreck` ↔ `side_quests:the_zappy_shipwreck` (same name)
- `collectibles:acorn_castle_west` ↔ `might_crystals:mc_acorn_gathering_west` (same name)
- `heart_pieces:poh_out_there_zol` ↔ `side_quests:an_out_there_zol` (same name, heart reward)
- `heart_pieces:poh_volcanic_talus` ↔ `side_quests:a_mountainous_mystery` (same name)
- `heart_pieces:poh_snowball_magic` ↔ `side_quests:snowball_magic` (same name, heart reward)
- `heart_pieces:poh_cotton_candy_hunt` ↔ `side_quests:cotton_candy_hunt` (same name)
- `main_quest:bow_of_might` ↔ `might_crystals:up_bow` (same name)
- `main_quest:bombs_of_might` ↔ `might_crystals:up_bombs` (same name)
- `might_crystals:mc_finding_the_flying_plant` ↔ `side_quests:finding_the_flying_plant` (same name)
- `might_crystals:mc_a_curious_child` ↔ `side_quests:a_curious_child` (same name)
- `might_crystals:mc_elusive_tumbleweeds` ↔ `side_quests:elusive_tumbleweeds` (same name)
- `might_crystals:mc_a_treat_for_my_person` ↔ `side_quests:a_treat_for_my_person` (same name)
- `might_crystals:mc_flames_of_fortune` ↔ `side_quests:the_flames_of_fortune` (same name)
- `might_crystals:mc_glide_path_trailblazer` ↔ `side_quests:glide_path_trailblazer` (same name)
- `might_crystals:mc_mobbing_mothulas` ↔ `side_quests:mobbing_mothulas` (same name)
- `accessories:charging_horn` ↔ `collectibles:flag_race@6` (reward names a row)
- `main_quest:gerudo_sanctum` ↔ `might_crystals:up_bow` (reward names a row)
- `accessories:heart_barrette` ↔ `side_quests:beetle_ballyhoo` (reward names a row)
- `accessories:gold_sash` ↔ `side_quests:wild_sandstorms` (reward names a row)
- `collectibles:silk_pajamas` ↔ `side_quests:dohnas_challenge` (reward names a row)
- `accessories:zora_scale` ↔ `side_quests:the_zora_childs_fate` (reward names a row)
- `accessories:gold_brooch` ↔ `side_quests:secret_chief_talks` (reward names a row)
- `accessories:ancient_charm` ↔ `side_quests:lets_play_a_game` (reward names a row)
- `accessories:might_bell` ↔ `side_quests:the_great_fairys_request` (reward names a row)
- `collectibles:customary_attire` ↔ `side_quests:from_the_heart` (reward names a row)
- `collectibles:cat_clothes` ↔ `side_quests:questioning_the_local_cats` (reward names a row)
- `accessories:gorons_bracelet` ↔ `side_quests:a_mountainous_mystery` (reward names a row)
- `accessories:fairy_fragrance` ↔ `side_quests:looking_for_bempu` (reward names a row)
- `accessories:curious_charm` ↔ `side_quests:cotton_candy_hunt` (reward names a row)
- `accessories:survey_scope` ↔ `side_quests:recipes_please@1` (reward names a row)
- `accessories:survey_binoculars` ↔ `side_quests:recipes_please@2` (reward names a row)
- `collectibles:green_tunic` ↔ `slumber_dojo:the_final_titans_gathering` (reward names a row)
- `accessories:first_mastery` ↔ `slumber_dojo:dojo_4` (reward names a row)
- `accessories:second_mastery` ↔ `slumber_dojo:dojo_8` (reward names a row)
- `accessories:final_mastery` ↔ `slumber_dojo:dojo_14` (reward names a row)
- `collectibles:stamp_suit` ↔ `stamp_stands:card_final` (reward names a row)
- `collectibles:acorn_kakariko_northeast` ↔ `heart_pieces:poh_acorn_gathering` (heart reward)
- `heart_pieces:poh_slumber_dojo_two` ↔ `slumber_dojo:dojo_2` (heart reward)
- `heart_pieces:poh_slumber_dojo_eleven` ↔ `slumber_dojo:dojo_2` (heart reward)
- `collectibles:flag_race@4` ↔ `heart_pieces:poh_flag_race_medium` (heart reward)
- `collectibles:mango_rush@5` ↔ `heart_pieces:poh_mango_rush_ultimate` (heart reward)

</details>

<details><summary>Link's Awakening (la): 5</summary>

- `heart_pieces:switch_rapids_race` ↔ `seashells:switch_rapids_race_under_35` (same name)
- `items:sword@1` ↔ `main_quest:find_sword` (reward names a row)
- `items:magic_powder` ↔ `main_quest:tail_key` (reward names a row)
- `items:ocarina` ↔ `main_quest:dream_shrine` (reward names a row)
- `heart_pieces:switch_seashell_mansion` ↔ `seashells:switch_mansion_piece_of_heart` (heart reward)

</details>

<details><summary>Majora's Mask (mm): 28</summary>

- `bombers_notebook:anju` ↔ `masks:couples_mask` (reward names a row)
- `bombers_notebook:kafei` ↔ `masks:couples_mask` (reward names a row)
- `bombers_notebook:curiosity_shop_man` ↔ `masks:keaton_mask` (reward names a row)
- `bombers_notebook:curiosity_shop_man` ↔ `masks:all_night_mask` (reward names a row)
- `bombers_notebook:bomb_shop_old_lady` ↔ `masks:blast_mask` (reward names a row)
- `bombers_notebook:romani` ↔ `songs:eponas_song` (reward names a row)
- `bombers_notebook:cremia` ↔ `masks:romanis_mask` (reward names a row)
- `bombers_notebook:madame_aroma` ↔ `masks:kafei_mask` (reward names a row)
- `bombers_notebook:toto` ↔ `masks:circus_leaders_mask` (reward names a row)
- `bombers_notebook:gorman` ↔ `masks:circus_leaders_mask` (reward names a row)
- `bombers_notebook:postman` ↔ `masks:postmans_hat` (reward names a row)
- `bombers_notebook:kamaro` ↔ `masks:kamaros_mask` (reward names a row)
- `bombers_notebook:grog` ↔ `masks:bunny_hood` (reward names a row)
- `bombers_notebook:gorman_brothers` ↔ `masks:garos_mask` (reward names a row)
- `bombers_notebook:shiro` ↔ `masks:stone_mask` (reward names a row)
- `bombers_notebook:guru_guru` ↔ `masks:bremen_mask` (reward names a row)
- `masks:mask_of_truth` ↔ `spider_houses:swamp_sh_reward` (reward names a row)
- `masks:great_fairy_mask` ↔ `stray_fairies:great_fairy_clock_town_mask` (reward names a row)
- `items:great_fairys_sword` ↔ `stray_fairies:great_fairy_ikana` (reward names a row)
- `bombers_notebook:postman` ↔ `heart_pieces:postman_counting_game` (heart reward)
- `bombers_notebook:toilet_hand` ↔ `heart_pieces:inn_toilet_hand` (heart reward)
- `bombers_notebook:rosa_sisters` ↔ `heart_pieces:rosa_sisters` (heart reward)
- `bombers_notebook:mayor_dotour` ↔ `heart_pieces:mayor_couples_mask` (heart reward)
- `heart_pieces:bank_5000` ↔ `world:bank_heart_piece` (heart reward)
- `heart_pieces:dodongo_grotto` ↔ `masks:kamaros_mask` (heart reward)
- `heart_pieces:odolwa_container` ↔ `main_quest:woodfall_temple` (heart reward)
- `heart_pieces:gyorg_container` ↔ `main_quest:great_bay_temple` (heart reward)
- `heart_pieces:twinmold_container` ↔ `main_quest:stone_tower_temple` (heart reward)

</details>

<details><summary>Oracle of Ages (ooa): 18</summary>

- `items:sword@2` ↔ `trading:noble_sword` (same name, reward names a row)
- `animal_companions:ricky_gloves` ↔ `items:ricky_gloves` (reward names a row)
- `items:bombchus` ↔ `linked_secrets:troy_secret@1` (reward names a row)
- `linked_secrets:mamamu_secret@1` ↔ `rings:snowshoe_ring` (reward names a row)
- `linked_secrets:plen_secret@1` ↔ `rings:spin_ring` (reward names a row)
- `items:biggorons_sword` ↔ `linked_secrets:elder_secret@1` (reward names a row)
- `linked_secrets:temple_secret@2` ↔ `rings:heart_ring_l1` (reward names a row)
- `items:biggorons_sword` ↔ `linked_secrets:biggoron_secret@2` (reward names a row)
- `items:bombchus` ↔ `linked_secrets:subrosian_secret@2` (reward names a row)
- `linked_secrets:diver_secret@2` ↔ `rings:swimmers_ring` (reward names a row)
- `items:sword@1` ↔ `main_quest:nayru_possessed` (reward names a row)
- `items:seed_satchel` ↔ `main_quest:maku_tree_rescue` (reward names a row)
- `harp_songs:tune_of_ages` ↔ `main_quest:rescue_nayru` (reward names a row)
- `linked_secrets:secret_to_holodrum` ↔ `main_quest:black_tower` (reward names a row)
- `heart_pieces:skull_dungeon` ↔ `main_quest:skull_dungeon` (heart reward)
- `heart_pieces:crown_dungeon` ↔ `main_quest:crown_dungeon` (heart reward)
- `heart_pieces:jabu_jabus_belly` ↔ `main_quest:jabu_jabus_belly` (heart reward)
- `heart_pieces:ancient_tomb` ↔ `main_quest:ancient_tomb` (heart reward)

</details>

<details><summary>Oracle of Seasons (oos): 38</summary>

- `items:rod_of_seasons` ↔ `seasons:rod_of_seasons` (same name, reward names a row)
- `items:sword@2` ↔ `trading:noble_sword` (same name, reward names a row)
- `items:shield@1` ↔ `subrosia:market_wooden_shield` (same name)
- `items:shield@2` ↔ `subrosia:iron_shield` (same name)
- `items:star_shaped_ore` ↔ `subrosia:star_shaped_ore` (same name)
- `items:ribbon` ↔ `subrosia:ribbon` (same name)
- `items:members_card` ↔ `subrosia:members_card` (same name)
- `items:red_ore` ↔ `subrosia:red_ore` (same name)
- `items:blue_ore` ↔ `subrosia:blue_ore` (same name)
- `items:hard_ore` ↔ `subrosia:hard_ore` (same name)
- `items:pirates_bell@2` ↔ `subrosia:pirates_bell@2` (same name)
- `items:pirates_bell@1` ↔ `subrosia:pirates_bell@1` (same name)
- `animal_companions:ricky_gloves` ↔ `items:ricky_gloves` (reward names a row)
- `linked_secrets:temple_secret@1` ↔ `rings:heart_ring_l1` (reward names a row)
- `items:biggorons_sword` ↔ `linked_secrets:biggoron_secret@1` (reward names a row)
- `items:bombchus` ↔ `linked_secrets:subrosian_secret@1` (reward names a row)
- `linked_secrets:diver_secret@1` ↔ `rings:swimmers_ring` (reward names a row)
- `items:bombchus` ↔ `linked_secrets:troy_secret@2` (reward names a row)
- `linked_secrets:mamamu_secret@2` ↔ `rings:snowshoe_ring` (reward names a row)
- `linked_secrets:plen_secret@2` ↔ `rings:spin_ring` (reward names a row)
- `items:biggorons_sword` ↔ `linked_secrets:elder_secret@2` (reward names a row)
- `items:sword@1` ↔ `main_quest:heros_cave` (reward names a row)
- `items:gnarled_key` ↔ `main_quest:maku_tree` (reward names a row)
- `linked_secrets:secret_to_labrynna` ↔ `main_quest:onoxs_castle` (reward names a row)
- `items:rod_of_seasons` ↔ `subrosia:temple_of_seasons` (reward names a row)
- `seasons:rod_of_seasons` ↔ `subrosia:temple_of_seasons` (reward names a row)
- `items:boomerang@1` ↔ `subrosia:dance_boomerang` (reward names a row)
- `items:strange_flute` ↔ `subrosia:dance_strange_flute` (reward names a row)
- `rings:sign_ring` ↔ `subrosia:sign_loving_subrosian` (reward names a row)
- `heart_pieces:subrosia_market_rare_peach_stone` ↔ `subrosia:rare_peach_stone` (heart reward)
- `heart_pieces:gnarled_root_dungeon` ↔ `main_quest:gnarled_root_dungeon` (heart reward)
- `heart_pieces:snakes_remains` ↔ `main_quest:snakes_remains` (heart reward)
- `heart_pieces:poison_moths_lair` ↔ `main_quest:poison_moths_lair` (heart reward)
- `heart_pieces:dancing_dragon_dungeon` ↔ `main_quest:dancing_dragon_dungeon` (heart reward)
- `heart_pieces:unicorns_cave` ↔ `main_quest:unicorns_cave` (heart reward)
- `heart_pieces:ancient_ruins` ↔ `main_quest:ancient_ruins` (heart reward)
- `heart_pieces:explorers_crypt` ↔ `main_quest:explorers_crypt` (heart reward)
- `heart_pieces:sword_and_shield_maze` ↔ `main_quest:sword_and_shield_maze` (heart reward)

</details>

<details><summary>Ocarina of Time (oot): 23</summary>

- `items:biggorons_sword` ↔ `trading:biggorons_sword` (same name)
- `gold_skulltulas:tokens_20` ↔ `items:stone_of_agony` (reward names a row)
- `gold_skulltulas:tokens_40` ↔ `items:bombchus` (reward names a row)
- `great_fairies:hyrule_castle` ↔ `items:dins_fire` (reward names a row)
- `great_fairies:zoras_fountain` ↔ `items:farores_wind` (reward names a row)
- `great_fairies:desert_colossus` ↔ `items:nayrus_love` (reward names a row)
- `main_quest:meet_zelda` ↔ `trading:zeldas_letter` (reward names a row)
- `main_quest:meet_zelda` ↔ `songs:zeldas_lullaby` (reward names a row)
- `items:ocarina@2` ↔ `main_quest:ocarina_of_time` (reward names a row)
- `main_quest:ocarina_of_time` ↔ `songs:song_of_time` (reward names a row)
- `items:master_sword` ↔ `main_quest:master_sword` (reward names a row)
- `items:iron_boots` ↔ `main_quest:ice_cavern` (reward names a row)
- `main_quest:ice_cavern` ↔ `songs:serenade_of_water` (reward names a row)
- `items:lens_of_truth` ↔ `main_quest:bottom_of_the_well` (reward names a row)
- `items:gerudos_card` ↔ `main_quest:gerudo_fortress` (reward names a row)
- `items:ice_arrows` ↔ `main_quest:gerudo_training_ground` (reward names a row)
- `items:strength@3` ↔ `main_quest:ganons_castle` (reward names a row)
- `gold_skulltulas:tokens_50` ↔ `heart_pieces:kakariko_skulltula_house_50` (heart reward)
- `heart_pieces:gohma` ↔ `main_quest:deku_tree` (heart reward)
- `heart_pieces:barinade` ↔ `main_quest:jabu_jabu` (heart reward)
- `heart_pieces:morpha` ↔ `main_quest:water_temple` (heart reward)
- `heart_pieces:bongo_bongo` ↔ `main_quest:shadow_temple` (heart reward)
- `heart_pieces:twinrova` ↔ `main_quest:spirit_temple` (heart reward)

</details>

<details><summary>Phantom Hourglass (ph): 22</summary>

- `heart_containers:nyeve_heart` ↔ `sidequests:nyeve_challenge` (same name, heart reward)
- `heart_containers:masked_ship_heart` ↔ `spirit_gems:courage_masked_ship` (same name)
- `items:sword@1` ↔ `main_quest:oshus_sword` (same name, reward names a row)
- `sea_chart:island_spirit` ↔ `spirit_gems:courage_spirit_island_dock` (same name)
- `items:phantom_hourglass` ↔ `main_quest:totok_b1_b3` (reward names a row)
- `items:cannon` ↔ `main_quest:eddos_cannon` (reward names a row)
- `items:shovel` ↔ `main_quest:molida_fog_route` (reward names a row)
- `items:salvage_arm` ↔ `main_quest:buy_salvage_arm` (reward names a row)
- `items:sun_key` ↔ `main_quest:salvage_sun_key` (reward names a row)
- `items:ghost_key` ↔ `main_quest:ghost_ship` (reward names a row)
- `items:regal_necklace` ↔ `main_quest:isle_of_the_dead` (reward names a row)
- `items:sword@2` ↔ `main_quest:phantom_sword` (reward names a row)
- `items:fishing_rod` ↔ `sidequests:fishing_rod_joanne` (reward names a row)
- `sidequests:treasure_maze@2` ↔ `treasure_maps:map_maze_normal` (reward names a row)
- `heart_containers:blaaz_heart` ↔ `main_quest:temple_of_fire` (heart reward)
- `heart_containers:cyclok_heart` ↔ `main_quest:temple_of_wind` (heart reward)
- `heart_containers:crayk_heart` ↔ `main_quest:temple_of_courage` (heart reward)
- `heart_containers:dongorongo_heart` ↔ `main_quest:goron_temple` (heart reward)
- `heart_containers:gleeok_heart` ↔ `main_quest:temple_of_ice` (heart reward)
- `heart_containers:eox_heart` ↔ `main_quest:mutohs_temple` (heart reward)
- `heart_containers:shooting_range_heart` ↔ `sidequests:shooting_range@2` (heart reward, same name)
- `heart_containers:treasure_maze_heart` ↔ `sidequests:treasure_maze@3` (heart reward)

</details>

<details><summary>Spirit Tracks (st): 38</summary>

- `deliveries:ride_carben` ↔ `force_gems:gem_carben` (same name)
- `deliveries:ride_mona` ↔ `force_gems:gem_mona` (same name)
- `deliveries:ride_joe` ↔ `force_gems:gem_joe` (same name)
- `deliveries:ride_noko` ↔ `force_gems:gem_noko` (same name)
- `deliveries:ride_wadatsumi` ↔ `force_gems:gem_wadatsumi` (same name)
- `heart_containers:take_em_all_on_heart` ↔ `minigames:take_em_all_on_1` (same name)
- `heart_containers:pirate_hideout_heart` ↔ `stamps:stamp_pirate_hideout` ↔ `stations:station_pirate_hideout` (same name)
- `main_quest:forest_temple` ↔ `stamps:stamp_forest_temple` (same name)
- `main_quest:snow_temple` ↔ `stamps:stamp_snow_temple` (same name)
- `main_quest:ocean_temple` ↔ `stamps:stamp_ocean_temple` (same name)
- `main_quest:fire_temple` ↔ `stamps:stamp_fire_temple` (same name)
- `main_quest:sand_temple` ↔ `stamps:stamp_sand_temple` (same name)
- `minigames:snowdrift_station_dungeon` ↔ `stations:station_snowdrift_station` (same name)
- `minigames:goron_target_range` ↔ `stations:station_goron_target_range` (same name)
- `minigames:disorientation_station_cave` ↔ `stations:station_disorientation_station` (same name)
- `minigames:lost_at_sea_station_dungeon` ↔ `stations:station_lost_at_sea_station` (same name)
- `stamps:stamp_aboda_village` ↔ `stations:station_aboda_village` (same name)
- `stamps:stamp_forest_sanctuary` ↔ `stations:station_forest_sanctuary` (same name)
- `stamps:stamp_whittleton` ↔ `stations:station_whittleton` (same name)
- `stamps:stamp_anouki_village` ↔ `stations:station_anouki_village` (same name)
- `stamps:stamp_snow_sanctuary` ↔ `stations:station_snow_sanctuary` (same name)
- `stamps:stamp_wellspring_station` ↔ `stations:station_wellspring_station` (same name)
- `stamps:stamp_castle_town` ↔ `stations:station_castle_town` (same name)
- `stamps:stamp_trading_post` ↔ `stations:station_trading_post` (same name)
- `stamps:stamp_ocean_sanctuary` ↔ `stations:station_ocean_sanctuary` (same name)
- `stamps:stamp_papuchia_village` ↔ `stations:station_papuchia_village` (same name)
- `stamps:stamp_goron_village` ↔ `stations:station_goron_village` (same name)
- `stamps:stamp_sand_sanctuary` ↔ `stations:station_sand_sanctuary` (same name)
- `items:sword@1` ↔ `main_quest:recruits_sword` (reward names a row)
- `items:spirit_flute` ↔ `main_quest:recruits_sword` (reward names a row)
- `items:whirlwind` ↔ `main_quest:forest_temple` (reward names a row)
- `items:stamp_book` ↔ `main_quest:alfonzo_cannon` (reward names a row)
- `items:boomerang` ↔ `main_quest:snow_temple` (reward names a row)
- `main_quest:trading_post_bridge` ↔ `songs:song_of_light` (reward names a row)
- `items:whip` ↔ `main_quest:ocean_temple` (reward names a row)
- `main_quest:kagoron_mega_ice` ↔ `train_parts:trusty_freight_car` (reward names a row)
- `items:shield_of_antiquity` ↔ `stamps:niko_ten_stamps` (reward names a row)
- `items:engineers_clothes` ↔ `stamps:niko_fifteen_stamps` (reward names a row)

</details>

<details><summary>Twilight Princess (tp): 28</summary>

- `heart_pieces:kakariko_gorge_cavern` ↔ `poe_souls:kakariko_gorge_cavern_poe` (same name)
- `heart_pieces:tot_entrance_owl` ↔ `poe_souls:tot_entrance_owl_poe` (same name)
- `heart_pieces:zant` ↔ `sidequests:stamp_zant` (same name)
- `items:ooccoo` ↔ `sidequests:stamp_ooccoo` (same name)
- `items:master_sword@1` ↔ `main_quest:draw_master_sword` (same name, reward names a row)
- `items:fishing_rod@1` ↔ `main_quest:ordon_days` (reward names a row)
- `items:slingshot` ↔ `main_quest:ordon_days` (reward names a row)
- `items:ordon_sword` ↔ `main_quest:ordon_sword_shield` (reward names a row)
- `items:ordon_shield` ↔ `main_quest:ordon_sword_shield` (reward names a row)
- `items:heros_clothes` ↔ `main_quest:faron_twilight` (reward names a row)
- `items:iron_boots` ↔ `main_quest:wrestle_bo` (reward names a row)
- `items:zora_armor` ↔ `main_quest:rutela_gift` (reward names a row)
- `items:fishing_rod@2` ↔ `main_quest:reekfish_scent` (reward names a row)
- `items:horse_call` ↔ `main_quest:ilia_memory` (reward names a row)
- `items:sky_book@1` ↔ `main_quest:ilia_memory` (reward names a row)
- `items:sky_book@2` ↔ `main_quest:sky_characters` (reward names a row)
- `items:giant_bomb_bag` ↔ `sidequests:iza_rapids_ride` (reward names a row)
- `items:quiver@1` ↔ `sidequests:star_game_big_quiver` (reward names a row)
- `items:quiver@2` ↔ `sidequests:star_game_giant_quiver` (reward names a row)
- `heart_pieces:charlo_donation` ↔ `sidequests:charlo_donations` (heart reward)
- `heart_pieces:goron_springwater_rush` ↔ `sidequests:hot_springwater` (heart reward)
- `heart_pieces:snowboard_race_yeta` ↔ `sidequests:snowpeak_races@2` (heart reward)
- `heart_pieces:diababa` ↔ `main_quest:forest_temple` (heart reward)
- `heart_pieces:fyrus` ↔ `main_quest:goron_mines` (heart reward)
- `heart_pieces:morpheel` ↔ `main_quest:lakebed_temple` (heart reward)
- `heart_pieces:stallord` ↔ `main_quest:arbiters_grounds` (heart reward)
- `heart_pieces:blizzeta` ↔ `main_quest:snowpeak_ruins` (heart reward)
- `heart_pieces:armogohma` ↔ `main_quest:temple_of_time` (heart reward)

</details>

<details><summary>The Wind Waker (ww): 58</summary>

- `figurines:ganondorf` ↔ `main_quest:ganondorf` (same name)
- `figurines:queen_of_fairies` ↔ `great_fairies:queen_of_fairies` (same name)
- `figurines:traveling_merchants` ↔ `side_quests:traveling_merchants` (same name)
- `heart_pieces:pawprint_chuchu_cave` ↔ `sea_secrets:pawprint_chuchu_cave` (same name, heart reward)
- `heart_pieces:five_star_isles_submarine` ↔ `sea_secrets:five_star_isles_submarine` (same name, heart reward)
- `heart_pieces:six_eye_reef_submarine` ↔ `sea_secrets:six_eye_reef_submarine` (same name, heart reward)
- `heart_pieces:stone_watcher_lookout` ↔ `sea_secrets:stone_watcher_island_lookout` (same name, heart reward)
- `heart_pieces:tingle_island_big_octo` ↔ `sea_secrets:tingle_island_big_octo` (same name, heart reward)
- `heart_pieces:seven_star_big_octo` ↔ `sea_secrets:seven_star_isles_big_octo` (same name, heart reward)
- `heart_pieces:bomb_island_cave` ↔ `sea_secrets:bomb_island_cave` (same name, heart reward)
- `heart_pieces:star_island_cave` ↔ `sea_secrets:star_island_cave` (same name, heart reward)
- `items:picto_box@1` ↔ `side_quests:lenzo_pictographs@1` (same name)
- `items:picto_box@2` ↔ `side_quests:lenzo_pictographs@3` (same name, reward names a row)
- `great_fairies:queen_of_fairies` ↔ `items:heros_bow@2` (reward names a row)
- `great_fairies:two_eye_reef` ↔ `items:magic_meter@2` (reward names a row)
- `items:telescope` ↔ `main_quest:outset_island` (reward names a row)
- `items:sword@1` ↔ `main_quest:outset_island` (reward names a row)
- `items:shield@1` ↔ `main_quest:outset_island` (reward names a row)
- `items:spoils_bag` ↔ `main_quest:forsaken_fortress_first` (reward names a row)
- `items:pirates_charm` ↔ `main_quest:forsaken_fortress_first` (reward names a row)
- `items:sail` ↔ `main_quest:windfall_sail` (reward names a row)
- `items:deku_leaf` ↔ `main_quest:forest_haven` (reward names a row)
- `items:magic_meter@1` ↔ `main_quest:forest_haven` (reward names a row)
- `items:bombs` ↔ `main_quest:windfall_bombs` (reward names a row)
- `items:sword@2` ↔ `main_quest:hyrule_castle` (reward names a row)
- `items:sword@2` ↔ `main_quest:master_sword_restored` (reward names a row)
- `items:heros_bow@3` ↔ `main_quest:ganons_tower` (reward names a row)
- `charts:triforce_chart_8` ↔ `sea_secrets:overlook_island_cave` (reward names a row)
- `charts:treasure_chart_37` ↔ `sea_secrets:rock_spire_isle_cave` (reward names a row)
- `items:power_bracelets` ↔ `sea_secrets:fire_mountain_cave` (reward names a row)
- `charts:triforce_chart_7` ↔ `sea_secrets:stone_watcher_island_cave` (reward names a row)
- `charts:triforce_chart_2` ↔ `sea_secrets:private_oasis_cabana` (reward names a row)
- `charts:triforce_chart_3` ↔ `sea_secrets:birds_peak_rock_cave` (reward names a row)
- `charts:ghost_ship_chart` ↔ `sea_secrets:diamond_steppe_island_cave` (reward names a row)
- `items:iron_boots` ↔ `sea_secrets:ice_ring_isle_cave` (reward names a row)
- `charts:treasure_chart_8` ↔ `sea_secrets:horseshoe_island_cave` (reward names a row)
- `charts:triforce_chart_6` ↔ `sea_secrets:savage_labyrinth` (reward names a row)
- `charts:submarine_chart` ↔ `sea_secrets:boating_course_cave` (reward names a row)
- `charts:treasure_chart_22` ↔ `sea_secrets:northern_fairy_island_submarine` (reward names a row)
- `charts:treasure_chart_9` ↔ `sea_secrets:crescent_moon_island_submarine` (reward names a row)
- `charts:platform_chart` ↔ `sea_secrets:flight_control_platform_submarine` (reward names a row)
- `charts:treasure_chart_14` ↔ `sea_secrets:headstone_island_submarine` (reward names a row)
- `charts:treasure_chart_16` ↔ `sea_secrets:seven_star_isles_lookout` (reward names a row)
- `charts:treasure_chart_40` ↔ `sea_secrets:southern_fairy_island_lookout` (reward names a row)
- `items:heros_charm` ↔ `side_quests:joy_pendants@3` (reward names a row)
- `charts:treasure_chart_2` ↔ `side_quests:skull_necklaces` (reward names a row)
- `items:hurricane_spin` ↔ `side_quests:knights_crests` (reward names a row)
- `items:magic_armor` ↔ `side_quests:traveling_merchants` (reward names a row)
- `heart_pieces:baitos_mother` ↔ `side_quests:mail_sorting@3` (heart reward)
- `charts:treasure_chart_4` ↔ `heart_pieces:rock_spire_ships` (heart reward)
- `charts:treasure_chart_46` ↔ `heart_pieces:pawprint_sunken` (heart reward)
- `charts:treasure_chart_4` ↔ `heart_pieces:rock_spire_sunken` (heart reward)
- `heart_pieces:dragon_roost_cavern_container` ↔ `main_quest:dragon_roost_cavern` (heart reward)
- `heart_pieces:forbidden_woods_container` ↔ `main_quest:forbidden_woods` (heart reward)
- `heart_pieces:tower_of_the_gods_container` ↔ `main_quest:tower_of_the_gods` (heart reward)
- `heart_pieces:forsaken_fortress_container` ↔ `main_quest:forsaken_fortress_second` (heart reward)
- `heart_pieces:earth_temple_container` ↔ `main_quest:earth_temple` (heart reward)
- `heart_pieces:wind_temple_container` ↔ `main_quest:wind_temple` (heart reward)

</details>

## Mark all complete in another tracker

The control is shared, so a bespoke tracker (BotW, TotK, The Minish Cap) gets the same toggle,
dialog and undo as the checklists. The pieces:

| Where | What |
|---|---|
| `core/BulkComplete.kt` | `BulkScope` (what one toggle covers, its action, labels and dialog text), `BulkChange` (a finished change and its snapshot, with the snackbar line), and `MapSnapshot` / `SetSnapshot` to undo exactly |
| `core/JsonStore.kt` | `swap`, an `update` that returns the value it replaced, read inside the same edit |
| `core/ui/BulkCompleteControl.kt` | `BulkCompleteCheckbox` (a header's tri-state box), `BulkCompleteRow` (a labelled row for a tab), `BulkCompleteState` + `BulkConfirmDialog` (confirm when needed) and `BulkUndoSnackbar` |
| `core/ui/TrackerList.kt` | `RegionHeader(…, leading = { BulkCompleteCheckbox(…) })` puts the box on a region card |

Steps, with the checklist as the worked example (`ChecklistViewModel`, `ChecklistScreen`):

1. **Scope.** In the UI state, list the entries a toggle acts on: the ones the filters leave
   showing on the tab, or under one region. Build its scope with `BulkScope.of(label, entries,
   noun, plural, filtered, isDone = …, isStarted = …)`. `label` names the tab or region, never an
   entry, so the dialog can't give away a spoiler-hidden one; add a `note` for anything the
   toggle leaves out (DLC, a Master Mode list) or does to levels.
2. **Write once.** In storage, change every entry in one `JsonStore.swap` (or one `dataStore.edit`
   for several keys) and return a snapshot of what it replaced, taken from the value `swap`
   returns: `MapSnapshot.of(before.levels, ids)` for a map of levels, `SetSnapshot.of(before.completed, ids)`
   for each set of ids (a shrine's *completed* and *found*). Add a `restore` that applies it in one
   write, so later edits outside the scope survive the undo.
3. **View model.** A `setAllDone(scope…, done): Job?` that reads the state on screen, writes to
   that state's save file, counts what actually changed and keeps a `BulkChange(action, changed,
   noun, plural, saveFile, snapshot)` in a `StateFlow`; plus `undoBulkChange(change): Job?` and
   `dismissBulkChange(change)`. Return the write `Job`s, as every view model does.
4. **Screen.** `val bulk = rememberBulkCompleteState()`, a `SnackbarHost` in the `Scaffold`,
   `BulkUndoSnackbar(change, snackbar, onUndo, onDismiss)`, `BulkConfirmDialog(bulk, saveFile.label)`,
   and a `BulkCompleteCheckbox` or `BulkCompleteRow` per scope whose `onApply` calls the view
   model with `it == BulkAction.MARK`.
5. **Tests.** As `ChecklistBulkViewModelTest`: per tab, region and *All*; levels; what's left out;
   filters; exact undo; only the active file; and one write, counted with the jvmTest
   `CountingDataStore`. Wait for the first state, and after a save-file switch for that file's
   progress, before writing.

The bespoke trackers, and what their toggles mean:

| Tracker | Toggles | Marks | Clear |
|---|---|---|---|
| Shrines (BotW, TotK) | base-game shrines; the Champions' Ballad's on their own row; each region card | completed, so found too | completed only: each stays found, as unticking a row does |
| Lightroots (TotK) | the tab; each region card | lit, so found too | lit only: each stays found |
| Korok Seeds | the list; each region card | collected, so found too | collected only: each stays found |
| Hyrule Compendium | each tab (All included); each header | photographed | photographed |
| Armor (BotW, TotK) | the list; each set header | collected, levels untouched | collected, levels untouched |
| Main Quest (BotW, MC) | each tab; MC's item groups and *Technique upgrades* | done; the Tiger Scrolls tab's row takes the upgrades too | done |
| Great Fairy Fountains (BotW, TotK) | the list | open; BotW's note says what the costs left come to | open |
| Side Quests (BotW) | each tab; each region card | completed, so started too | to not started, in progress ones too |
| Overworld Bosses (BotW) | each tab; each region card | defeated, so found too; the active file's spawns only | to not found, found ones too |
| Heart Pieces, Kinstone Fusions (MC) | the list; each region card | collected / fused | collected / fused |
| Carlov's Figurines (MC) | the list; each checkpoint card | owned, even if not yet available (the note counts those) | owned; checkpoint, side tasks and shells never change |

They follow the same steps: `ShrineStorage.setAllCompleted` / `setAllLit` (a `DiscoverySnapshot`
of *completed* and *found*, from `DiscoverySets.setsWithAll`), `KorokStorage.setAllCompleted`,
`CompendiumStorage.setAllPhotographed` (a `SetSnapshot`) and `ArmorStorage.setAllOwned` (a
`MapSnapshot` of whole pieces, so the undo puts levels back too). Each view model now pairs the
active file with its progress inside one `flatMapLatest`, so a toggle's counts and its write are
always on the same file. A region card's box acts on what that card shows: in BotW that takes in
its DLC shrines while the DLC chip shows them; with the chip off they're out of every toggle.
Armor leaves levels alone because its only bulk upgrade control, the set header's stars, already
exists and steps one level at a time; a toggle that also maxed levels would fight it.

The rest store a set of ids per file (`SetSnapshot`), except Side Quests and Overworld Bosses, which
have three states: a quest is not started, in progress or completed, a boss not found, found or
defeated. Unlike shrines and Koroks, their clear goes back to the *first* state (`withStates(ids,
UNDISCOVERED)`), because a list of quests "in progress" or bosses "found" that you never touched
would be wrong; `BulkScope`'s `started` counts the middle state so the dialog says how many change.
Both keep a `DiscoverySnapshot`, so the undo puts back each one's exact state, the middle one too.
Main Quest, Fairies and Bosses also pair the active file with its progress in one `flatMapLatest`.

The rules the shared pieces apply: a toggle marks everything complete, or clears it all once it all
is; a clear always asks first, and a mark asks once it changes more than `BulkScope.CONFIRM_ABOVE`
(3) entries; the checkbox and the row are at least 48dp to tap and describe themselves for a
screen reader ("Mark all complete in Hyrule Field, 2 of 5 done").
