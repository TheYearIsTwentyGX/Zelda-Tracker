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
   Then add a dataset test for the game's own counts, and write the sources up in
   `docs/<game>.md`.

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
names. Each name is a section id:

- `pieces` is how many steps of section `pieces` are done (with no tiers, that's items ticked).
- `pieces.value` is the sum of `value` over those steps.

The maximum is worked out by running the same formula with everything done, so "Hearts 5¼ / 20"
needs no separate total. Values print like the game shows them: `12`, `5¼`, `⅔`, otherwise up to
two decimals. Dividing by zero gives 0. Examples:

- Hearts from quarter pieces and containers, in whole hearts: `3 + floor(pieces / 4) + containers`
- The same, showing the quarters: `3 + pieces / 4 + containers`
- Crystals collected where some chests hold several: `crystals.value`

`problems()` rejects a formula that doesn't parse or that names an unknown section.

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
- **Counts.** Tabs and region headers count every item on that tab, whatever the filters leave
  showing.
- **Overview row** (`ChecklistProgressProvider`):
  - the bar is steps done of all;
  - the detail line lists each summary, then each section's count (when there's more than one
    section);
  - *Next* is the first item, in file order, that isn't done and whose `requires` are all done.
    So **order items in the order you'd suggest doing them**. With *Hide unchecked* on and that
    item not started, it says only how many are left there ("2 pieces still to do in Hyrule
    Field").
