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
| `data/ChecklistStorage.kt` | DataStore keys, per save file |
| `data/ChecklistProgressProvider.kt` | The overview row |
| `model/StoryGate.kt`, `data/StoryProgress.kt` | [Story gating](#story-gating): what the game's Main Quest has unlocked on a save file |
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
- Steps must be untiered. `StoryLinksDatasetTest` checks every link in every game, and that no
  item unlocks before something it `requires` (by `storyOrder`). The game's own dataset test
  spot-checks a few links.

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
