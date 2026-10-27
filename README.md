# ZeldaGX

A progress tracker for Zelda games. Android for now; built with Compose Multiplatform so an
iOS port is mostly a matter of adding the iOS targets.

## Features

**Games, menu and settings**
- Every mainline Zelda game is listed, oldest first by original release date (first release
  anywhere): The Legend of Zelda (1986) through Echoes of Wisdom (2024). Games from the same year
  are ordered by date, so Four Swords Adventures (March 2004) comes before The Minish Cap
  (November 2004), and Oracle of Seasons before Oracle of Ages (same day, 2001).
- The Legend of Zelda, Zelda II, A Link to the Past, Link's Awakening, Ocarina of Time, Majora's
  Mask, Oracle of Seasons, Oracle of Ages, Four Swords, The Wind Waker, Four Swords Adventures, The
  Minish Cap, Twilight Princess, Phantom Hourglass, Spirit Tracks, Skyward Sword, A Link Between
  Worlds, Tri Force Heroes, Breath of the Wild, Tears of the Kingdom and Echoes of Wisdom have
  trackers, so every game does. A game without any would show as *Coming soon*.
- **Spin-offs** come after the mainline games, under a heading of their own, in release order:
  Hyrule Warriors: Definitive Edition (2018), with its Adventure Mode maps.
- **Four Swords** (2002, the Game Boy Advance multiplayer game bundled with A Link to the Past) has
  its own entry. It's counted as mainline because it has its own story (Vaati's first appearance),
  Nintendo's official timeline (Hyrule Historia) places it between The Minish Cap and Ocarina of
  Time, and it was later sold on its own as the Anniversary Edition. Remakes and HD versions aren't
  separate entries.
- **Menu** (☰ in the top bar of the home screen, Settings and every tracker opened from either):
  Home, then each shown game in release order (the spin-offs after a heading), then Settings.
  - A game with trackers is a section that opens to list them; tap one to go there. A coming-soon
    game is a single row.
  - A game's **tools**, helpers for playing that keep no progress (so far Skyward Sword's Thrill
    Digger Solver), come after its trackers under a *Tools* heading, in the menu and on its home card.
  - Each time the menu opens, only the game you're in is open (none on Home and Settings). Sections
    opened or closed by hand stay that way until the menu closes.
  - Trackers opened from inside another screen (the Overview, the map's sheet, a quest's page, a
    page's *Show on map*) and detail pages keep a Back arrow.
- **Settings** (gear on the home screen, or the menu): a switch per game for whether it appears on
  the home screen and in the menu.
  - **Default:** every game with trackers is shown and every coming-soon game is hidden until you
    turn it on. A game you haven't touched follows this default, so a game that gets its first
    tracker in an update appears without you turning it on.
  - At least one game stays shown: the last one's switch is locked.
  - Hiding a game keeps all its progress; turn it back on to pick up where you left off.
- **Hide what the story hasn't unlocked yet** (Settings → Progress, off by default): checklists
  leave out what the game's Main Quest hasn't reached on the active save file, such as a heart
  piece behind a dungeon you haven't finished. This works only where the data links items to Main
  Quest steps (`unlockedBy`, see `docs/checklist-trackers.md`): every game with a Main Quest
  checklist, from Skyward Sword to A Link Between Worlds (each game's doc, *Story links*).
  - Anything you've ticked always shows, even if you ticked it ahead of the story.
  - Counts keep their true totals, and each region card says how many more unlock later in the
    story, without naming them.
  - Search, the *Missable* chip, mark-all and the overview's *Next* leave out locked items.
  - Skyward Sword's Lightning Round Records board isn't a list to hide, so it shows a *Not open
    yet* note instead until Lanayru's part of the Song of the Hero, from the same `unlockedBy`.
- **Game themes**: inside a game (its trackers, overview, detail pages, scans and map) the screens
  take on that game's look: colours from its menus, header lettering in the style of its era, and
  a small ornament under each region's header. Every game has one: the 8- and 16-bit games and Four Swords
  Adventures, each from its own menus (the NES pause screen's black and heart red, Zelda II's
  file select, A Link to the Past's gold-bordered status screen, the Game Boy's four greens and Link's
  Awakening DX, the Oracles' title screens and Nayru's blues, Four Swords' gold panel and Force Gems,
  The Minish Cap's navy file select), the 3D console games (Ocarina of Time's brown pause panels and
  parchment, Majora's Mask's olive-gold stone, Twilight Princess's rust and Twili teal, Skyward Sword's
  silver and goddess gold, Tears of the Kingdom's Zonai jade, Hyrule Warriors' charcoal and gold, in
  Cinzel capitals), the DS and 3DS games (Phantom Hourglass's teal and sand, Spirit Tracks' Tower
  gold on deep green with a rail rule, A Link Between Worlds' Hyrule gold and Lorule black, Tri Force
  Heroes' theatre curtain), Echoes of Wisdom, The Wind Waker and Breath of the Wild. `docs/themes.md`
  has each one's brief and sources, and says which are still from memory.
  - The home screen, the menu and Settings keep the app's own look, with each themed game's accent
    colour on its card, its row in the menu and its switch in Settings, and its name on the home
    screen in its lettering.
  - Every theme has a light and a dark side. **Settings → Appearance** picks *System* (the
    default), *Light* or *Dark*; before themes the app was always dark, so *Dark* brings that back.
  - **Game themes** in Settings (on by default) turns them off: every screen in the app's own look.
  - Every colour pair meets WCAG AA contrast (4.5:1 for text, 3:1 for outlines, bars and stars),
    checked by a test for every theme, light and dark. Body text stays in the system font; only
    headers use a theme's lettering.
- **Mark all complete** (every checklist tracker, each HW:DE Adventure map, Skyward Sword's Item
  Upgrades and Potion Infusions (not its Lightning Round times), and every BotW, TotK and Minish Cap tracker except the map and the overviews): a row under the filters marks everything on the tab complete, and a box on each region
  card marks that region. Once everything there is complete, the same control reads *Clear all*;
  the box shows a dash when some are done. Upgrades go to their top level, or back to none.
  - It acts on the active save file, and on the rows the search and filter chips leave showing
    ("Mark 3 shown complete"). *All* never touches a tab that counts on its own, like a remake's.
  - Marking more than 3, and any clear, asks first, naming how many, where and on which save file,
    but not which items. Afterwards a snackbar offers **Undo**, which puts back exactly what was
    there.

**Breath of the Wild: Progress Overview**
- The first card under BotW: every tracker's progress on the active save file on one screen, each
  with a progress bar and its numbers. Tap a row to open that tracker.
- Shrines (completed of 120, found, and the Champions' Ballad's 16 counted apart), Korok Seeds (of
  900, and Hestu upgrades bought), Hyrule Compendium (of the save file's own total: 389, or 394 in
  Master Mode), Main Quest (towers, Divine Beasts and memories), Armor (collected, and how many are
  fully upgraded; the armor list's DLC and amiibo filters apply), Great Fairy Fountains (open, and
  what the next costs), Hearts & Stamina (Spirit Orb upgrades bought, hearts and stamina wheels) and
  Side Quests (completed across the Side, Shrine and DLC tabs, with each tab's tally) and Overworld
  Bosses (spawns defeated, each kind's count, and the next Medal of Honor to earn).
- Most rows suggest a next step. With a tracker's *Hide undiscovered* on, the suggestion names only
  shrines or Koroks already found or quests already started, as the list does, and otherwise just
  the region.
- **Map completion**: Korok seeds, shrines and Divine Beasts count toward the map's percentage
  (Nintendo Life, Screen Rant), so the overview shows how many of those 1,024 items are done. Named
  locations count too, but sources disagree on how many there are (187 or 226) and no tracker
  records them, so this is a count of items toward 100%, not the percentage.
- The save file picker switches every tracker. A tracker whose data can't be loaded shows as
  unavailable without holding up the rest.

**Breath of the Wild: Armor Progress**
- All 107 armor pieces: base game, The Master Trials, The Champions' Ballad and amiibo.
- In-game inventory icon for every piece, in the list and on its detail page.
- Mark pieces as collected and set each one's upgrade level (★0–★4).
- Defense at every level, piece effects, and set bonuses with live active/inactive status.
- Materials for every remaining upgrade on each piece.
- **Total Materials Required**: the sum of every remaining upgrade, for *Collected Armor* or
  *All Armor (Including Uncollected)*. Tap a material to see which pieces need it.
- DLC and amiibo filters for both the list and the totals.
- **Mark all complete**: a row under the filters collects every piece the list shows, and a box on
  each set's header (and *Other armor*) collects that set. Armor the DLC and amiibo filters hide is
  never touched. Upgrade levels are left as they are, both ways: the set header's stars already
  raise those, and clearing uncollects pieces the way a row's box does.

**Breath of the Wild: Great Fairy Fountains**
- Where to find Cotera, Kaysa, Mija and Tera: region, directions and nearby landmarks.
- Mark each fountain as open. The screen shows what the next one costs (100 / 500 / 1,000 /
  10,000 rupees, by how many are already open) and how far armor can be upgraded.
- **Mark all complete** (a row on the summary card) opens all four at once, or closes them all. With
  every fountain open there are no costs left to pay; the dialog says how much that skips.
- Shares the active save file with the armor tracker.

**Breath of the Wild: Main Quest Progress**
- **Sheikah Towers**: all 15, with where each one stands and what's in the way of the climb.
- **Divine Beasts**: Vah Ruta, Rudania, Medoh and Naboris, with the Champion, the main quest that
  frees each one and how it starts, what to prepare, the Blight boss and the rewards.
- **Recovered Memories**: all 18 in Adventure Log order. The 12 album pictures carry their photo
  number, the location and a hint (including which stable Pikango recognises it at). The final
  picture (#17) unlocks once the 12 are found, and the 5 quest-triggered memories say which quest
  triggers them.
- Mark towers activated, beasts freed and memories recovered. Each tab shows its count, and the
  save file picker shows all three.
- **Mark all complete** per tab (a row on its summary card): every tower, beast or memory, or clear
  them all.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Shrines**
- All 120 shrines plus the 16 from The Champions' Ballad, grouped by tower region with counts per
  region and overall (e.g. 87 / 120).
- Each shrine is Undiscovered, Found or Completed: tick a row to complete it, and use its pin
  to mark it found. Completing implies found; unticking leaves it found.
- **Hide undiscovered shrines** (top-bar menu, off by default, same for every save file): an
  undiscovered shrine's row shows only "Undiscovered shrine" under its region until you tap to
  reveal it for the session or mark it found. Search only matches such rows by region.
- Each shrine's trial, whether it's a Puzzle, Test of Strength or Blessing, its Shrine Quest if it
  has one, and a one-line location hint.
- Search by shrine, trial, quest or region; filter to incomplete shrines or Shrine Quests, and
  hide the DLC.
- **Mark all complete**: a row under the filters completes every base-game shrine showing, and a
  second row, while the DLC is shown, does the Champions' Ballad's; the base row never touches
  them. A box on each region card covers the shrines on that card, DLC ones included while they
  show. Completing marks each shrine found too; *Clear all* takes away completed but leaves each
  one found, as unticking a row does.
- **Spirit Orbs**: orbs earned from completed shrines, what you've spent on Heart Containers and
  Stamina Vessels, and what's left. 120 orbs in the base game; the Champions' Ballad's four Great
  Plateau shrines add 4 more (its other twelve give emblems). Purchases are entered by hand.
  Hearts count 3 to start, one per Divine Beast marked freed in Main Quest Progress on the same
  save file, and those bought; Goddess Statues stop at 30, so each freed beast lowers how many
  can be bought (27 with none, 23 with all four). A number entered before a beast was freed is
  kept and flagged in red if it now goes past 30. All 120 orbs buy 30 upgrades (31 with the DLC),
  short of the 33 that max both hearts and stamina.
- **Scan map screenshots or photos** (top-bar menu): reads shrine states from screenshots of the
  in-game map (2D map, fully zoomed out, 1920×1080 straight from the Switch album; one of the north
  half and one of the south), or from phone photos of the TV or the Switch showing it, at any angle
  and either way up. Each shrine comes out as *Completed* (blue glyph), *Not done* (orange glyph),
  *Not on the map* (spot clearly empty) or *Unsure*, with why: covered by another icon, a pin, a
  stamp or the Hero's Path trail; stacked with another shrine (Ree Dahee and Shee Venath share a
  spot); at the screen edge; or in no screenshot. Any number of screenshots and photos merge: a
  definite reading beats an edge or missing one, and two definite readings that disagree become Unsure.
  Nothing is written until you review it. Completed and Not done are pre-ticked, Unsure shrines are
  listed by name for you to decide, Not on the map is never written, a shrine already completed is
  never downgraded, and Apply asks for confirmation before writing to the active save file. With
  *Hide undiscovered shrines* on, empty spots are only counted and unsure undiscovered shrines stay
  hidden until tapped. Screenshots at the wrong size, photos where the screen can't be found or is too
  small, maps not fully zoomed out, and images with too few icons to line up are rejected with a reason.
  Photos leave a few more shrines unsure than screenshots do.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Hyrule Compendium**
- All 389 entries (385 base game + 4 from The Champions' Ballad) in the in-game order and
  numbering, and Master Mode's 394, with its five extra entries and its own numbers, on the
  Master Mode file.
- A tab per category with photographed counts; Creatures is split into animals and food (048–083,
  the entries that show hearts recovered rather than materials). Search by name, number, location
  or drop, and a *Missing only* filter.
- **Mark all complete**: a row under the filters marks every entry on the tab photographed, and a
  box on each header (All's categories, Creatures' animals and food) marks that header's. Counts
  follow the active save file, so Master Mode's five extra entries are only ever marked on the
  Master Mode file; the dialog says so.
- Each entry's common locations and drops, and how to buy its picture from Symin if you can't
  take it (Elite Enemy Pictures only after Dark Beast Ganon). Entries that can only be
  photographed at certain times — bosses, DLC trials — are marked *Limited*.
- Tracks Save File 1, Save File 2 and Master Mode separately, sharing the active file.

**Breath of the Wild: Korok Seeds**
- All 900 seeds grouped by tower region, with counts per region and overall (e.g. 312 / 900).
- Each row shows the puzzle type (Lone rock, Race to the ring, Pinwheel balloons… 29 in all), the
  nearest named place and objmap's id; one-off puzzles also show objmap's own name for them.
- Undiscovered, Found or Collected per seed, like shrines: tick a row to collect it, use its pin to
  mark it found.
- **Mark all complete**: a row under the filters collects every seed showing, and a box on each
  region card collects that region's. *Clear all* leaves each Korok found, as unticking a row does.
- **Hide undiscovered Koroks** (top-bar menu): the same spoiler-safe mode as shrines, but a
  separate setting, so one can be on without the other. Hidden rows only match a search by region
  and never match a puzzle-type filter.
- **Import from Zelda Notes** (top-bar menu): reads the Navigation map in the Nintendo Switch App's
  Zelda Notes. Turn on **Hide Discovered Destinations**, zoom in until the Korok droplets stand
  apart, then pick a screen recording of you panning across the map (read three frames a second)
  or screenshots that cover it. Each view is placed on the world map by matching its droplets to
  the 900 known Korok positions; a Korok whose droplet is missing is suggested as collected, one
  covered by a shrine icon or crowded beside another droplet is listed as unsure, and views zoomed
  out too far, with the switch off, or showing too few Koroks to place are listed with the reason.
  The review shows a coverage map, lets you pick which save file to import into (Zelda Notes only
  shows the save your Switch last synced), and gives the percentage to compare with Zelda Notes'
  Categories → Hidden Koroks. Apply asks for confirmation and never unmarks anything.
- **Scan the map live** (top-bar menu): point the phone's camera at the TV or Switch showing the 2D
  map zoomed all the way in, and pan around. The game marks every Korok you've found at max zoom,
  and the app reads those marks as you go: green rings for found ones, grey dots for empty spots,
  amber for unsure. **Done** lists what it saw. Found Koroks not yet collected on the active save
  file are pre-ticked, unsure ones are listed for you to tick, empty spots are only counted, and
  Apply asks for confirmation and never unmarks anything. The camera permission is asked for when
  you open the scan, with a way to the app's settings if it was turned off. **Stats** shows the
  analysed frames per second and each stage's milliseconds (also logged to logcat, tag `KorokScan`).
- Search by place, puzzle type, region or id; filter to incomplete seeds or to any mix of puzzle
  types (bottom sheet, with how many seeds each type has).
- Tap › for a seed's page: how to solve its puzzle type, region, nearest place, coordinates, and
  **Open in objmap**, which opens objmap.zeldamods.org centred on that Korok with its game data.
- **Hestu**: seeds collected against the weapon, bow and shield upgrades you've bought from him
  (entered by hand), what the seeds in hand buy cheapest-first, and what's left. Maxing every
  pouch takes 441 seeds; the rest only count toward Hestu's Gift for all 900.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Side Quests**
- All 76 base-game side quests, the 42 Shrine Quests and the 13 DLC side quests ("EX" quests from
  The Master Trials and The Champions' Ballad), a swipeable tab each, grouped by tower region with
  counts per region and per tab.
- Each quest is Not started, In progress or Completed: tick a row to complete it, and use its play
  button to mark it in progress. Completing implies started; unticking leaves it in progress.
- **Hide quests not started** (top-bar menu): the same spoiler-safe mode as shrines and Koroks, but
  its own setting. A hidden quest's row shows only its region until tapped, and search only matches
  it by region.
- Search by quest, giver, place, reward, region or linked shrine; filter to incomplete quests.
- Tap › for a quest's page: giver and where to find them, what has to happen first, a spoiler-light
  objective, the reward and, where it's well sourced, when it can only be done (night, a Blood
  Moon). Reward armor opens in Armor Progress.
- A Shrine Quest's page shows its shrine's state from the shrine tracker on the same save file.
  Completing the quest doesn't complete the shrine, since the trial inside is still to do.
- **Mark all complete** per tab (a row under its count) and per region (a box on the region's card)
  completes every quest the search and filter leave showing. Once they're all complete it reads
  *Clear all*, which puts each back to **Not started**, in progress ones too. **Undo** puts back each
  quest's exact state: completed, in progress or not started. Shrine Quests' shrines aren't changed.
- EX quests are here because the Adventure Log lists them under Side Quests. The DLC's five main
  quests (EX The Champions' Ballad and the four EX Champion … Song quests) are Main Quests in the
  log, so they aren't.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Overworld Bosses**
- Every fixed Hinox, Stalnox, Talus, Molduga and Lynel spawn: 40 Hinoxes, 40 Taluses, 4 Moldugas and 22 Lynels, plus
  The Champions' Ballad's Igneo Talus Titan and Molduking (marked DLC) and Master Mode's extra Lynel (on the Master
  Mode file only).
- A swipeable tab per kind with its count, each grouped by tower region with counts per region.
- Not found, Found or Defeated per spawn (defeated once is enough: bosses return after a Blood Moon). Tick a row to
  mark it defeated, use its pin to mark it found.
- Each tab's headline tracks Kilton's **Medals of Honor**, e.g. "Hinox 12 / 40 · Medal of Honor: Hinox". DLC bosses
  are listed but not counted; Lynels have no medal.
- **Hide unfound bosses** (top-bar menu): the same spoiler-safe mode as shrines and Koroks, with its own setting.
- Search by place, variant, region or id; filter to bosses not yet defeated, or by variant (Blue Hinox, Frost
  Talus…).
- Tap › for a spawn's page: its Compendium entry and number, drops, region, nearest place, coordinates, notes (the
  Coliseum Lynel, the Hyrule Castle bosses, Eventide Island…), **Show on map** and **Open in objmap**. Lynels whose
  colour rises with the world level are tagged *Scales* and listed at their weakest colour.
- **Master Mode:** the Master Mode file lists each boss as Master Mode places it. The game ranks Hinoxes, Stone
  Taluses and Lynels up a tier there (a Luminous Talus is Rare, a Blue Hinox is Black), except five spawns it
  exempts. A spawn's page says what it is in the other mode. Progress is kept per spawn, whatever the mode.
- **Mark all complete** per tab and per region marks every boss shown **Defeated**; *Clear all* puts
  each back to **Not found**, found ones too, and **Undo** puts back each one's exact state. It acts
  on the active file's spawns, so Master Mode's extra Lynel only ever changes on the Master Mode file.
- Shares the active save file with the other BotW trackers.

**Breath of the Wild: Map**
- The map of Hyrule, from the game's own map art. It's bundled with the app, so it works offline.
  Pan, pinch or double-tap to zoom.
- Layers you can switch on and off: **Shrines** (136), **Korok Seeds** (900), **Sheikah Towers** (15) and
  **Bosses** (108, or 109 on the Master Mode file).
  - Each chip shows how many are done on the active save file.
  - Markers use the game's own map icons:
    - A shrine has an orange glyph until it's completed, then turns all blue, as in the game.
      The Champions' Ballad's shrines have their own icon, with a spire.
    - Towers are orange until activated, then blue.
    - Korok seeds sit on a disc: blue collected, orange found, grey undiscovered.
    - Bosses sit on the same disc (blue defeated), each with its kind's icon: Kilton's Medal of Honor for
      Hinoxes, Taluses and Moldugas, and the Lynel Mask for Lynels. The sheet names the variant the active
      file has, so a Master Mode file shows its ranked-up bosses.
    - Undiscovered shrines, Koroks and bosses are greyed out.
    - Markers shrink as you zoom out, so the map stays readable fully zoomed out. Bosses stop at three
      quarters size, so their icons stay readable.
  - At low zoom, nearby Koroks merge into a cluster numbered with the seeds still to collect. Tap
    it to zoom in.
- Tap a marker for its sheet: name, region, state, and buttons to mark it found or done, or to
  open it in its tracker. Changes go to the active save file, the same one the trackers use.
- **Spoilers:** each layer follows its tracker's own setting. With *Hide undiscovered shrines*,
  *Hide undiscovered Koroks* or *Hide unfound bosses* on, undiscovered ones are left off the map, the
  way the game's map leaves off what you haven't found.
  - *Show hidden places* (map menu) shows them as unnamed grey markers instead. A hidden marker
    never shows the place's state, or whether it's a DLC shrine. A hidden boss keeps its kind's icon,
    as the boss tracker's hidden rows name the kind.
  - Tap one to reveal it for the session.
- *Hide completed* (map menu) leaves only what's left to do.
- **Show on map** on a Korok's or a boss's page centres the map on it.

**Tears of the Kingdom: Armor Progress**
- All 135 armor pieces in 35 sets plus single pieces, with the same collection, upgrade, set
  bonus and material-totals features as BotW.
- Inventory icon for every piece; pieces that are also in BotW share its icon.
- Prices in Poes (Bargainer Statues) and Bubbul Gems (Koltin) as well as rupees.
- No DLC, and every amiibo piece can also be found in the world, so there are no source filters.
  The list sorts A–Z only, because the in-game sort orders haven't been verified yet.
- **Mark all complete** per set and for the whole list, as in BotW; upgrade levels are left alone.

**Tears of the Kingdom: Great Fairy Fountains**
- Where to find Tera, Mija, Kaysa and Cotera, and the Stable Trotters side adventure that frees
  each one (Tera first; she unlocks the other three).
- Mark each fairy as freed; the screen shows how far armor can be upgraded and the per-upgrade
  rupee fee (10 / 50 / 200 / 500).
- **Mark all complete** (a row on the summary card) frees all four at once, or none.

**Tears of the Kingdom: Shrines & Lightroots**
- All 152 Shrines of Light and the 120 Lightroots beneath them, as two swipeable tabs with their
  counts (e.g. 87 / 152 and 40 / 120), each grouped by Skyview Tower region with per-region counts.
- Each shrine's trial (Rauru's Blessing, Proving Grounds, Combat Training or a puzzle), whether it
  is on a sky island or in a cave, its Shrine Quest if it has one, its map area and a location hint.
- Each Lightroot points at the shrine above it, and its name is the shrine's spelled backwards
  (Kawumoro under Oromuwak Shrine). Only the 32 sky shrines have none. A shrine's row shows its
  Lightroot, a Lightroot's row shows its shrine, and › opens a page with the pair. Each half is
  Undiscovered, Found or Completed / Lit on its own.
- The same **Hide undiscovered** setting as BotW, covering both tabs, plus search and an
  *Incomplete* / *Unlit* filter. A hidden Lightroot only matches a search by region, and finding a
  shrine doesn't reveal the Lightroot below it.
- **Mark all complete** on each tab and region card, as in BotW: completing shrines or lighting
  Lightroots, each tab leaving the other alone. *Clear all* leaves each one found.
- **Lights of Blessing**: Lights earned from completed shrines, Heart Containers and Stamina
  Vessels bought, the six boss Heart Containers (five temples and Phantom Ganon, entered by hand)
  and A Call from the Depths' free heart or vessel. Maxing both takes 47 upgrades against the 45
  available, so the card says you can't. There are no Lights outside shrines, and Crystallized
  Charges are Zonai currency, so neither counts.

All three TotK trackers track **Save File 1** and **Save File 2** separately and share the active file.

**The Minish Cap: Progress Overview**
- The first card under The Minish Cap, like BotW's: every MC tracker on the active save file, each
  with a progress bar, its numbers and a suggested next step. Tap a row to open that tracker.
- **Heart Pieces**: pieces and containers collected (of 50), the hearts total (of 20), and the next
  one to get. With *Hide uncollected* on, it names only the region and how many are left there.
- **Kinstone Fusions**: fusions done of 100, Gold of 9 and hearts of 10, and the next fixed fusion
  the story allows soonest. It names the partner, never the result, so *Hide results not fused*
  has nothing to hold back.
- **Main Quest**: dungeons cleared, Elements recovered, items and Tiger Scrolls. The next step is
  the next dungeon in story order, then the next Tiger Scroll, then the next item. The tracker
  hides nothing, so these are named.
- **Carlov's Figurines**: owned of 136, shells held and each category's count. The next step is how
  many figurines Carlov has to win now and the odds of a draw, or that you need shells. If nothing
  is left to win, it gives a side task that opens one or what moves the story on. With *Hide
  figurines not yet available* on, it only counts the side tasks and doesn't name the next
  checkpoint.
- The save file picker switches every MC tracker, and a tracker whose data can't be loaded shows as
  unavailable without holding up the rest. No MC row counts toward a map percentage, so there is
  no map completion card.

**The Minish Cap: Heart Pieces**
- All 44 Pieces of Heart and the 6 Heart Containers, grouped by region with per-region counts. The
  containers are the five bosses' plus Librari's, from a Kinstone fusion late in the game.
- A hearts meter drawn like the game's, ten to a row. Link starts with 3 hearts, every four pieces
  fill another (shown in quarters), and a full file has 20.
- Each entry gives its area and how to get it. Tags mark Heart Containers, Kinstone fusions (with who
  to fuse with) and Minish-size routes, and a *Needs* line lists the items required (Mole Mitts,
  Roc's Cape, the White Sword split in two…).
- Search by place, item or region. Filter to *Not collected*, or to Heart Containers, Kinstone
  fusions or Minish-size routes.
- **Hide uncollected** (top-bar menu) is a spoiler-safe mode, like the BotW trackers'. An uncollected
  entry shows only its region until you tap to reveal it, and a search or filter chip can't give it
  away.
- **Mark all complete**: a row under the filters collects every entry they leave showing, and a box
  on each region's card collects that region.
- Tracks **File 1**, **File 2** and **File 3** separately, as on the cartridge. The save file picker
  shows each file's hearts.

**The Minish Cap: Kinstone Fusions**
- All 100 fusions, grouped by where the partner is, with counts per region and overall, e.g. 37 / 100. The
  summary line also counts the 9 **Gold** fusions and the 10 that give a Piece of Heart or the Heart Container.
- Each row shows who or what to fuse with, where to find them and which piece they need. The piece is its
  colour and Thonky's shape name, e.g. *Red jagged teeth*. The row also shows what the fusion does: a
  chest, a beanstalk, a Golden Octorok, a dojo, a Fairy Fountain, the way on.
- Tags mark:
  - the nine Gold story fusions (three statues, five clouds, the Source of the Flow);
  - objects such as the Mysterious Walls;
  - fusions that can be missed;
  - fusions that need something done first.
- The 18 **random** fusions, which any of dozens of characters may offer, are listed by result under
  *Random fusers*.
- The six Goron Cave fusions take a squarish or S-curve piece depending on how many are done, so they
  say so.
- Tick a row once fused. Search by partner, place, piece or result, and filter:
  - to fusions not yet done;
  - by region;
  - by piece colour;
  - by what they give (Hearts, Items & upgrades, Sword techniques, Opens the way, People & shops,
    Fairy Fountains, Beanstalks, Golden monsters, Kinstone Pieces, Rupees, Mysterious Shells).
- **Hide results not fused** (top-bar menu): a fusion not done still shows its partner, place and piece,
  since they're how to find it, but not what it does until it's fused or tapped to reveal.
  - Search doesn't match a hidden result, and the reward filter skips hidden rows.
  - Warnings about missing a fusion stay visible and are written without spoilers.
- Tap › for a fusion's page:
  - the partner and where to find them;
  - the piece;
  - the story point it becomes possible (Zelda Wiki's six stages) and what it needs first;
  - the result, where it appears and notes.
- The only fusion that can be lost for good is Eenie's on the European version. It's marked, as is
  Strato's, whose follow-up side quest can be missed.
- **Mark all complete**: a row above the list marks every fusion the search and filters leave showing,
  and a box on each region's card marks that region. Hidden results stay hidden in its dialog.
- Tracks **File 1**, **File 2** and **File 3** separately and shares the active file with MC's other trackers.

**The Minish Cap: Main Quest & Items**
- **Dungeons**: the six in story order (Deepwood Shrine to Dark Hyrule Castle), with how to reach
  each, the item inside, the boss, the Element (Earth, Fire, Water, Wind; the Fortress of Winds
  has none) and what the Elemental Sanctuary then does to the sword, from the White Sword (Two
  Elements) to the Four Sword. The tab counts Elements recovered as well as dungeons cleared.
- **Items**: all 38 swords, shields, items, equipment, upgrades and bottles, grouped with per-group
  counts, each with where and how to get it, including Rupee costs and Kinstone Fusions. The bomb
  bag, quiver and wallet upgrades are named by where they are found, since they come in any order.
  The Light Arrows are flagged missable and the Mirror Shield post-game.
- **Tiger Scrolls**: the eight techniques from Swiftblade, Grayblade, Waveblade, Grimblade and
  Swiftblade I, with each dojo's location and what the brother asks for first. The three technique
  upgrades from Scarblade, Splitblade and Greatblade are listed under them and counted apart.
- Mark dungeons cleared, items obtained and techniques learned on **File 1**, **File 2** or
  **File 3**. Each tab shows its count, and the save file picker shows all three. Sources and
  known disagreements are in `docs/mc-main-quest.md`.
- **Mark all complete** per tab (a row on its summary card), per item group and for the technique
  upgrades (a box on each heading). The Tiger Scrolls tab's row takes the upgrades too.

**The Minish Cap: Carlov's Figurines**
- All 136 figurines with their gallery numbers, names and in-game descriptions (North American
  text), in four categories by egg colour: Characters, Scenes, Enemies and Bosses.
- Grouped by the story checkpoint that adds them to Carlov's machine: the gallery opening, Cave of
  Flames, Vaati taking the king, and so on up to the six-figurine final series after the credits.
  Eleven also need a side task (a Kinstone fusion, Beedle's dusty stall, the Goron Cave); tick it
  under the figurine.
- Set the save file's story checkpoint and the tracker works out which figurines are **available
  now**. Filter by *Available now*, *Still to win* or *Not yet available*, and by category. Counts
  are shown overall, per category and per checkpoint.
- **Mysterious Shells** per save file, and the odds of a new figurine from the game's own code:
  what Carlov's machine shows, the hidden minimum it actually rolls against (15 % down to 6 %),
  and about how many shells the next one, and the rest of what's available, will cost.
- **Hide figurines not yet available** (top-bar menu): they show only their number until tapped,
  later checkpoints read "Later in the story", and search only matches their number.
- **Mark all complete**: a row above the list marks every figurine the filters leave showing owned,
  and a box on each checkpoint's card marks that checkpoint's. Figurines the story hasn't unlocked
  yet are marked too if you choose (the dialog says how many). The story checkpoint, side tasks and
  Mysterious Shells never change.
- Three save files, **File 1–3**, as on the cartridge.

**The Legend of Zelda**

Every LoZ tracker is a [checklist](docs/checklist-trackers.md), with a **First Quest** and a
**Second Quest** tab: the Second Quest carries on in the same save file once the first is beaten.
Sources and disagreements are in `docs/loz.md`.
- **Progress Overview** (first): a row per tracker with its count, the hearts totals and the next
  thing to do.
- **Dungeons:** Levels 1–9 of each quest (the Eagle to Death Mountain in the First Quest): where the
  entrance is and what opens it, the boss, the items inside and the Old Man's hint. Levels 1–8 give
  a Triforce piece; Level 9 needs all eight.
- **Items & Equipment:** all 27 items in each quest, from the Wooden Sword to the Red Ring, with
  where to find each or what it costs, the two bomb upgrades, and where the Second Quest moves them.
- **Heart Containers:** 13 per quest, 8 from bosses and 5 hidden on the overworld, with each quest's
  hearts (3 to 16). The "Take any one you want" caves are marked missable: take the potion and the
  container is gone.
- Places are a landmark plus the screen, column A–P west to east and row 1–8 north to south (the
  start is H8).
- Three save files, **File 1** to **File 3**, as on the cartridge.

**Zelda II: The Adventure of Link**

Every Zelda II tracker is a [checklist](docs/checklist-trackers.md). Sources, names (the NES, wiki
and guide names differ a lot) and disagreements are in `docs/aol.md`.
- **Progress Overview** (first): a row per tracker with its count, the life and magic meters and
  the next thing to do.
- **Palaces:** the six palaces and the Great Palace, each done when its crystal is set (or the
  Triforce taken), with how to reach it and its item, and the 8 bosses from Horsehead to Dark Link
  with the XP each gives.
- **Items:** the 14 things Link carries: the six palace treasures, the Hammer and the Magic Key, and
  the quest items townsfolk want (Trophy, Bagu's note, Mirror, Water of Life, fountain water, the
  Child), each with the spell it earns.
- **Spells & Techniques:** the 8 spells, one per town, with what each wise man wants, the effect and
  the MP cost; the Downward and Upward Thrusts.
- **Heart & Magic Containers:** 4 of each, with the life and magic meters (4 to 8 bars).
- **Levels & Link Dolls:** Attack, Magic and Life as rows with a tier per level up to 8, showing the
  XP each costs, and the 6 Link Dolls. The two inside palaces are missable.
- Regions are Western Hyrule, Death Mountain and Eastern Hyrule.
- Three save files, **File 1** to **File 3**, as on the cartridge.

**A Link to the Past**

Every ALttP tracker is a [checklist](docs/checklist-trackers.md), built for the original SNES
release; GBA differences are in item notes. Sources and disagreements are in `docs/alttp.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Main Quest:** the Light World (the escape, Sahasrahla, the three Pendant dungeons, the Master
  Sword and Agahnim) and the Dark World (the seven Crystal dungeons, Ganon's Tower and Ganon),
  with each dungeon's boss, reward and treasure. Pendants (of 3) and Crystals (of 7) are totalled.
- **Items & Equipment:** sword, shield, mail and gloves as one row each, a tier per upgrade. It
  also has the 18 item-screen items, including the Silver Arrows, Magical Boomerang, Magic Powder
  and Flute; the 4 Bottles; half magic; and the Pond of Happiness bomb (10 to 50) and arrow (30 to
  70) capacity upgrades.
- **Heart Pieces:** 24 Pieces of Heart (18 Light World, 6 Dark World) and 11 Heart Containers,
  with the hearts total (3 to 20). Each piece names the items it needs.
- Regions are Zelda Wiki's two worlds, with the exact place in each row.
- Three save files, **File 1–3**, as on the SNES cartridge.
**Link's Awakening**

Every LA tracker is a [checklist](docs/checklist-trackers.md), built for the Game Boy original and
DX. Where the 2019 Switch remake differs, the item's notes say so. Where it differs a lot (Heart
Pieces, Secret Seashells, Fairy Bottles), the remake's list is its own tab, counted apart: it has
its own count and totals but stays out of the tracker's bar and the overview's next step. Sources
and disagreements are in `docs/la.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Main Quest:** the Sword, the five keys, the eight dungeons with each boss, dungeon item and
  Instrument of the Sirens, and the Wind Fish's Egg, as one chain in play order. Instruments are
  totalled (of 8).
- **Items & Equipment:** 15 items, with the Sword (Level 2), Shield (Mirror Shield) and Power
  Bracelet (L-2) upgrades as tiers; the Mad Batters' Magic Powder, Bomb and Arrow capacity
  upgrades; the five Golden Leaves; and the Switch remake's three Fairy Bottles.
- **Heart Pieces:** 12 Pieces of Heart and 8 Heart Containers, with the hearts total (3 to 14).
  The Switch tabs add the remake's 32 pieces and Dampé's Heart Container, with their own total (3
  to 20).
- **Secret Seashells:** all 26 and the Seashell Mansion's Level 2 Sword. The two Mansion bonus
  shells, given only for exactly 5 or 10, are marked missable. The Switch tabs add the remake's
  50 and its five Mansion rewards.
- **Trading Sequence** (14, Yoshi Doll to Magnifying Lens), **Songs** (3), **Photographs** (DX's
  12, four of them missable) and the **Color Dungeon** (DX and Switch: the Library's clue, the
  graveyard puzzle, the bosses and the Fairy's red or blue tunic).
- Regions are the island's areas (Mabe Village, Ukuku Prairie, Tal Tal Heights…), with the exact
  place in each row. Tags give the items needed.
- Three save files, **File 1–3**, as on the cartridge.

**Ocarina of Time**
- Built from checklists (`files/oot/*.json`): nine trackers plus a Progress Overview, which lists
  first and gives each one a row with its count, totals and a next step. Data is for the original
  N64 release; where the 3DS version or Master Quest differ, the item's notes say so. Sources and
  disagreements are in `docs/oot.md`.
- **Main Quest**: Childhood (Deku Tree to the Master Sword) and Seven Years Later (the Chamber of
  Sages to Ganon), dungeons and mini-dungeons in the order you'd play them, each with its boss,
  dungeon item and Sage. Totals for the 3 Spiritual Stones and 6 Medallions.
- **Items & Equipment**: the 18 inventory items (the Ocarina and Hookshot with their upgrades as
  tiers), 11 swords, shields, tunics and boots, the capacity and strength upgrades, and the Stone
  of Agony and Gerudo's Card. Upgrades that come in either order are named by capacity, with both
  sources given. The Mask of Truth's Deku Nut upgrade is marked missable (the N64 Poacher's Saw
  bug).
- **Heart Pieces**: all 36 Pieces of Heart by area and the 8 bosses' Heart Containers, with a hearts
  total (3 + pieces ÷ 4 + containers, of 20).
- **Gold Skulltulas**: all 100 by area (56 overworld, 44 in dungeons). Tags give the age, *Night*
  for the night-only ones and the items needed. The House of Skulltula's six rewards (10 to 100
  tokens) are their own tab.
- **Songs**: the 12 songs, the six warp songs on their own tab, with where each is taught and its
  notes on the N64's buttons.
- **Great Fairies** (6), **Bottles** (4) and **Big Poes** (10, for the Poe Collector's bottle).
- **Trading Sequences**: the mask quest from the Weird Egg to the Mask of Truth, and the adult trade
  from the Pocket Egg to Biggoron's Sword, each step needing the one before. Timed steps are noted.
- Every tracker has spoiler hiding, search, a *Not done* filter and an item page (see
  `docs/checklist-trackers.md`). Tracks **File 1**, **File 2** and **File 3**, the cartridge's three
  save slots.

**Majora's Mask**
- Nine checklists and a Progress Overview, all data (`files/mm/`, see `docs/mm.md`), for the
  original Nintendo 64 release. Where Majora's Mask 3D differs (a moved Piece of Heart and Bottle,
  the swapped Woodfall and Snowhead Great Fairies, Shiro's new spot, the bigger notebook), the
  item's notes say so.
- **Main Quest**: the first cycle on the Clock Tower, the four temples with their bosses, Remains
  and the Giant each frees, and Majora.
- **Masks**: all 24, the four transformation masks and the 20 the Moon Children ask for. The
  Fierce Deity's Mask lists all 20 as what it needs.
- **Heart Pieces**: all 52 Pieces of Heart and the 4 Heart Containers by region, with a hearts
  total (3 to start, 20 at most).
- **Bombers' Notebook**: the notebook's 20 people in its order, each with their events by day and
  hour and what they give you. Tick a person once their Happiness Seal is earned.
- **Stray Fairies**: the Clock Town fairy, the 15 in each temple and the five Great Fairies'
  rewards, each of which lists its temple's 15 as what it needs.
- **Spider Houses**: the 30 Gold Skulltulas in each of the Swamp and Oceanside Spider Houses, and
  their rewards (the Mask of Truth; the Giant's Wallet on Day 1).
- **Songs**, **Items & Equipment** (items, the upgrades as tiers: swords, wallets, quivers, bomb
  bags, magic; and the six Bottles) and **Owls, Maps & Bank** (the 10 Owl Statues, Tingle's six
  maps and the bank's three rewards).
- **The three-day cycle.** The trackers record what you keep through the Song of Time: masks,
  hearts, songs, items, Remains and the rewards for finished side quests. Each item says when it
  happens (day, night and hour) and is tagged *One cycle* when every step has to be done between
  one Dawn of the First Day and the next: Stray Fairies, Gold Skulltulas, Anju and Kafei, the milk
  run, and so on.
- Two save files, **File 1** and **File 2**, as on the cartridge. An owl save is a temporary
  quit-save on one of them, not a file of its own.

**Oracle of Seasons**
- Ten checklists and a Progress Overview, all data (`files/oos/`, see `docs/oos.md`), for the Game
  Boy Color original.
- **Linked games** are listed, not hidden: Twinrova and Ganon, Biggoron's Sword, the linked Heart
  Container and linked-only rings are tagged *Linked*, and upgrade steps only a linked game reaches
  say so (*Master Sword (Linked)*).
- **Main Quest**: the eight dungeons with their bosses, items, Essences of Nature and Heart
  Containers, then General Onox, then the linked finale. A total of Essences (of 8).
- **Items & Equipment**: the items and the five Mystical Seeds, the upgrades as tiers (sword,
  shield, Roc's Feather to Cape, Boomerang, Slingshot, seed and bomb capacity) and the Quest Items.
- **Rod of Seasons**: the Rod and the four seasons from the Temple of Seasons' towers.
- **Heart Pieces**: all 12 Pieces of Heart and 9 Heart Containers (the 8 bosses and one from a
  linked secret) by region, with a hearts total (3 to start, 15 at most).
- **Rings**: the Ring Box's three levels and all 64 rings in ring-list order, with where to get each
  in Seasons. The ids match Ages' ring list. Rings only found in Ages are tagged *Not in Seasons*.
- **Gasha Spots** (16, with the ring classes each gives), **Trading Sequence** (the Cuccodex to the
  Noble Sword, each step needing the one before) and **Animal Companions** (Ricky, Dimitri, Moosh,
  and which flute decides your partner).
- **Linked Secrets**: the passwords, and the ten secrets to tell here and ten to take to Ages, each
  with its reward and the return secret for Farore.
- **Subrosia**: the Subrosian story, the Market, the ores and Smithy, and the Dance Hall, buried
  treasure and Sign-Loving Subrosian.
- Three save files, **File 1** to **File 3**, as in the game.

**Oracle of Ages**
- Nine checklists and a Progress Overview, all data (`files/ooa/`, see `docs/ooa.md`), for the Game
  Boy Color original.
- **Linked games** are listed, not hidden: Twinrova and Ganon, Biggoron's Sword, the linked Heart
  Container, the Sea of Storms Gasha spot and linked-only rings are tagged *Linked*, and upgrade
  steps only a linked game reaches say so (*Master Sword (Linked)*).
- **Main Quest**: the eight dungeons with their bosses, items, Essences of Time and Heart
  Containers, rescuing Nayru, then Veran in the Black Tower and the linked finale. A total of
  Essences (of 8).
- **Items & Equipment**: the items and the five Mystical Seeds, the upgrades as tiers (sword,
  shield, Power Bracelet to Glove, Flippers to Mermaid Suit, Switch Hook to Long Hook, seed and
  bomb capacity, with totals for seeds and bombs) and the Quest Items.
- **Harp of Ages**: the Harp and the Tunes of Echoes, Currents and Ages.
- **Heart Pieces**: all 12 Pieces of Heart and 9 Heart Containers (the 8 bosses and one from a
  linked secret) by region, with a hearts total (3 to start, 15 at most).
- **Rings**: the Ring Box's three levels and all 64 rings in ring-list order, with where to get each
  in Ages. The ids are the same as in Oracle of Seasons' ring list. Rings only found in Seasons
  are tagged *Not in Ages*.
- **Gasha Spots** (16, tagged Past or Present, with the ring classes each gives), **Trading
  Sequence** (the Poe Clock to the Noble Sword, each step needing the one before) and **Animal
  Companions** (Ricky, Dimitri, Moosh, and which flute decides your partner).
- **Linked Secrets**: the passwords, and the ten secrets to tell here and ten to take to Seasons,
  each with its reward and the return secret for Farore.
- Three save files, **File 1** to **File 3**, as in the game.

**Four Swords**

One [checklist](docs/checklist-trackers.md), **Stages & Keys**, after a Progress Overview, covering
the GBA original and the Anniversary Edition (DSiWare, 2011). Sources and disagreements are in
`docs/fs.md`.
- **Silver, Golden and Hero's Keys:** a tab per key tier. Each has the key from the Great Fairies of
  the Sea of Trees, Talus Cave and Death Mountain (1,000, 3,000 or 5,000 Rupees), then the Vaati's
  Palace door those keys open. The optional Chambers of Insight come first. Keys (of 9) and palace
  doors (of 3) are totalled.
- **Anniversary Edition tabs:** the Realm of Memories' 9 levels (its A Link to the Past, Link's
  Awakening and NES doors, for the Master Sword) and the Hero's Trial's 9 levels (opened with 5
  medals or 30,000 Rupees, for the Hurricane Spin), each level after the one before.
- **ALttP Link-ups** (GBA only): Sword Beams from ALttP's Master Sword, 10 Medals of Courage, the
  Hurricane Spin from the Lumberjack's Riddles and the Palace of the Four Sword.
- The version-only tabs are `separate`: counted on their own, left out of the total and the next
  step.
- Items and hearts last only for a stage, so they aren't tracked; medals are tracked as the two
  thresholds that unlock something.
- Three save files, **File 1–3**: A Link to the Past's on the GBA, or the Anniversary Edition's own.

**The Wind Waker**

Every Wind Waker tracker is a [checklist](docs/checklist-trackers.md), built for the GameCube
original. Where Wind Waker HD differs (the Swift Sail, three Triforce Charts instead of eight,
Treasure Charts 42–46, the Tingle Bottle, the Hero's Charm and Picto Box changes, Carlov's twelve
figurines a day), the item's notes say so. Sources and disagreements are in `docs/ww.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Main Quest:** the story in order, from Outset Island to Ganondorf: each dungeon with its boss
  and treasure, the three Pearls (totalled), Hyrule Castle, the Master Sword's power, and a tab
  for the 8 Triforce Shards.
- **Items & Equipment:** the sword (Hero's Sword to the full-power Master Sword) and shield as
  tiers, 17 items (the Picto Box and Hero's Bow with their upgrades), the Hero's Charm, the 4
  Bottles, and the wallet (200 to 5,000), bomb bag and quiver (30 to 99), double magic and the
  Hurricane Spin.
- **Heart Pieces:** all 44 Pieces of Heart by island and the 6 bosses' Heart Containers, with the
  hearts total (3 to 20).
- **Charts:** the 41 Treasure Charts (tap once for the chart, again for its salvaged treasure,
  with both counted), the 8 Triforce Charts (found, then deciphered by Tingle) and the 12 Special
  Charts. HD's Treasure Charts 42–46 have a tab of their own, left out of the totals.
- **Sea Chart:** the 49 squares, A1 to G7 by row, charted by feeding each fishman. Each square
  lists what's there.
- **Sea Secrets:** the 20 Secret Caves, 7 Submarines, 20 groups of lookout platforms and 6 Big Octos.
- **Nintendo Gallery:** all 134 figurines by gallery room, with the 16 missable ones flagged and
  Lenzo's Legendary Pictographs by moon phase.
- **Songs** (6), **Great Fairies** (7, plus the Queen of Fairies) and **Side Quests** (Joy
  Pendants, Mail Sorting, Lenzo, Skull Necklaces, Golden Feathers, the Traveling Merchants, the 8
  Withered Trees and the 5 Tingle Statues).
- Regions are the Great Sea's islands, in Sea Chart order.

**Four Swords Adventures**

Every FSA tracker is a [checklist](docs/checklist-trackers.md), for the GameCube's Hyrulean
Adventure and its multiplayer extras. Sources and disagreements are in `docs/fsa.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Hyrulean Adventure:** all 24 stages, 3 in each of the 8 levels (Whereabouts of the Wind to the
  Realm of the Heavens), in order. Each row has the stage's boss, the items found there and what it
  gives. The 6 Shrine Maidens and Princess Zelda (of 7) and the 4 Royal Jewels are totalled. Every
  stage needs 2,000 Force Gems to break its barrier.
- **Tingle's Tower & Shadow Battle** (multiplayer, with Game Boy Advances): the 8 Tingle's Tower
  mini-games, one per level, opened by its second stage, and the 5 Dark World Shadow Battle arenas
  that finishing the game unlocks.
- Items, Heart Containers and the Four Sword's power last only for a stage, and there are no Pieces
  of Heart, so none are tracked. Navi Trackers, the Japan-only mode, isn't tracked either.
- Three save files, **File 1–3**, as on the memory card.

**Twilight Princess**

Every TP tracker is a [checklist](docs/checklist-trackers.md), built for the GameCube original
(the Wii version is mirrored, with the same collectibles). Where Twilight Princess HD differs, the
item's notes say so; HD-only lists (the 50 Miiverse stamps, the Cave of Shadows, the Ghost Lantern
and Colossal Wallet) are their own tabs, counted apart. Sources and disagreements are in
`docs/tp.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Main Quest:** the Ordon days, the three Twilights, the nine dungeons (each with its boss,
  mini-boss and dungeon item) and the story steps between them, as one chain in play order.
  Tears of Light (of 48; 36 in HD), Fused Shadows (of 3) and Mirror Shards (of 4) are totalled.
- **Items & Equipment:** 15 items, with upgrades as tiers (Double Clawshots, the three bomb types,
  the restored Dominion Rod, the Sky Book); swords, shields and clothing; the wallets and quivers
  as tiers, the three Bomb Bags and the Giant Bomb Bag; and the 4 Bottles. Totals for Rupees (300
  to 1,000), arrows (30 to 100), bomb bags and bombs per bag.
- **Heart Pieces:** 45 Pieces of Heart and 8 Heart Containers, with the hearts total (3 to 20,
  five pieces to a heart).
- **Poe Souls:** all 60 by region, tagged *Night* and *Wolf* where they need it, and Jovani's
  rewards (the Bottle at 20, the end of his curse at 60, Gengle's Rupees).
- **Golden Bugs:** all 24, a male and a female of 12 species, and Agitha's two wallets.
- **Hidden Skills:** the 7 skills in the order the Hero's Shade teaches them, each needing the
  Howling Stone that calls the Golden Wolf, and the 7 Howling Stones.
- **Cave of Ordeals:** all 50 floors with their enemies, the item each gate needs and the Great
  Fairy's rewards every tenth floor; HD's 40-floor Cave of Shadows (Wolf Link amiibo) is its own tab.
- **Sidequests & Collections:** 12 sidequests and minigames (Talo, Iza, Rollgoal, Charlo, the STAR
  Game, the Malo Mart donations, the Snowpeak races…), the Postman's 16 letters (two from Ooccoo can
  be missed), the 6 Fishing Journal fish and HD's 50 stamps.
- Regions are the in-game map's provinces, with each dungeon as its own region. Tags give the
  items or form needed.
- Three save files, **File 1–3**, as on the GameCube, the Wii and HD.

**Phantom Hourglass**

Every PH tracker is a [checklist](docs/checklist-trackers.md), listed by the four seas of the World
of the Ocean King (Southwestern, Northwestern, Southeastern, Northeastern) with the island or floor
in each row. Sources and disagreements are in `docs/ph.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Main Quest:** 21 steps in story order, from Oshus's Sword to Bellumbeck: the six temples and
  the Ghost Ship with their bosses, the Temple of the Ocean King's floors (1F, B1–B3, B4–B6, B7–B9,
  B10–B13 and Bellum), the Cannon, the fog route, the Salvage Arm and Sun Key, the Regal Necklace
  and the Phantom Sword. Totals for the 3 Spirits, 4 Sea Charts and 3 Pure Metals.
- **Items & Equipment:** 9 items with their upgrades as tiers (Phantom Sword; Bomb Bag 10/20/30,
  Quiver 20/30/50, Bombchu Bag 10/20/30, with capacity totals), 9 key items, and the Phantom
  Hourglass tab: the five bosses' and five Sunken Treasures' sand, 10 to 25 minutes.
- **Heart Containers:** the 7 bosses' and the 6 from Nyeve, the Shooting Range, the Masked Ship,
  the Neptoona, the island shop and the Treasure Maze, with the hearts total (3 to 16).
- **Spirit Gems:** all 20 Power, Wisdom and Courage Gems, with totals, and each spirit's Level 1
  and Level 2 at Spirit Island.
- **Treasure Maps:** all 31, each with two steps, finding the map and salvaging its treasure, and
  totals for both.
- **Ship Parts & Treasure:** the eight sets of eight parts (Golden included; they're random, so
  each row says where parts come from) and the eight kinds of Treasure.
- **Sea Chart & Islands** (16 islands, hidden ones included, and the 6 Golden Frogs) and
  **Sidequests** (the fishing journal, the five-step trading quest, the mini-games' prizes and
  Beedle's cards).

**Spirit Tracks**

Every ST tracker is a [checklist](docs/checklist-trackers.md), listed by realm (Forest, Snow,
Ocean, Fire, Sand, with the Tower of Spirits and the Dark Realm where they matter) with the exact
place in each row. Force Gems, rabbits and deliveries are named by giver or place, because the
guides number them differently. Sources and disagreements are in `docs/st.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Main Quest:** 27 steps in story order, from the engineer's ceremony to Malladus: the five
  temples and their bosses, the six climbs of the Tower of Spirits, the five Lokomo duets and the
  Demon Train. Totals for the 5 temples, 4 Rail Maps and 5 duets.
- **Items & Equipment:** 12 items (the Lokomo Sword and Bow of Light as tiers), the Bomb Bag
  (10/20/30) and Quiver (20/30/50) with capacity totals, and both Swordsman's Scrolls.
- **Heart Containers:** all 13, five from bosses and eight from minigames, Bunnio, shops and the
  Ends of the Earth, with the hearts total (3 to 16).
- **Force Gems** (20, with the tracks each opens) and **Passengers & Cargo** (25 runs: 15
  passengers and 10 cargo jobs, each Force Gem named as its reward).
- **Rabbits** (50, ten per realm, and Bunnio's 8 prizes), **Stamp Stations** (20 and Niko's three
  rewards) and **Songs** (the five Spirit Flute songs).
- **Stations & Warp Gates:** the 22 stations and Warp Gates A–H.
- **Train Parts:** all 32 parts in 8 sets with each one's Treasure cost, and a complete-sets total.
- **Minigames & Secrets:** Take 'Em All On!, Russell, the Whip Race, Beedle's club, the target
  ranges and the hidden stations' lasting prizes, as tiers where there are several.
- Nothing in the game can be missed for good.
- Two save files, **File 1** and **File 2**, as on the DS card.

**Skyward Sword**

Every SS tracker but Potion Infusions, Item Upgrades and Lightning Round Records is a [checklist](docs/checklist-trackers.md), built for the Wii original. Where
Skyward Sword HD differs (button controls, autosave, the amiibo's fast travel), the item's notes say
so; no count or price changes. Sources and disagreements are in `docs/ss.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step. Item
  Upgrades' row counts items obtained (of 16), upgrades done (of 26) and the treasures, bugs and
  Rupees still to go, and suggests the next upgrade on something you have. Potion Infusions' row
  counts recipe steps made (of 12) and names the next one. Rows that share progress (below) always
  agree: a Quick Beetle counts in Items & Equipment and Item Upgrades alike.
  Lightning Round Records' row counts the bosses with a record of the file's slots: *Records set
  7/11* on a Normal file, *3/12 (Hero Mode)* on a Hero Mode one, with the best times' total.
- **Main Quest:** the story as one chain in play order: the seven dungeons with each boss and
  dungeon item, the Goddess's Harp and the songs, the three battles with the Imprisoned, Levias, the
  Stone of Trials, Sky Keep and Demise. Tabs for the four Silent Realms (and what each gives), the
  four parts of the Song of the Hero, the three Sacred Flames, and the sword's five forms, Goddess
  Sword to True Master Sword, as one row. Dungeons (of 7), Silent Realms (of 4) and Sacred Flames (of
  3) are totalled.
- **Items & Equipment:** the 13 items, with the Scrap Shop upgrades as tiers (Hook, Quick and Tough
  Beetle; Scattershot; Iron and Sacred Bow; Big Bug Net) and the treasures, bugs and Rupees each
  costs; the Seed Satchel, Bomb Bag and Quiver upgrades; the Adventure Pouch (4
  slots and 4 expansions, totalled to 8); the 5 Bottles; and the wallets, with how many Rupees you
  can carry (300 to 9,900).
- **Heart Pieces:** 24 Pieces of Heart by area and the 6 bosses' Heart Containers, with the hearts
  total (6 to 18).
- **Goddess Cubes:** all 27, each a row with two steps: strike the cube, then open its Goddess Chest
  in the Sky. Each chest's prize is on the row, and cubes struck and chests opened are totalled.
- **Gratitude Crystals:** the 12 side quests that give them (65), the 15 loose crystals found at night
  (15) and Batreaux's 7 rewards, with the crystals total (of 80).
- **Side Quests & Minigames:** the 7 minigames with each prize tier (Clean Cut, Thrill Digger, the
  harp, Dodoh's High Dive, the Rickety Coaster, Pumpkin Pull, Bug Heaven), the 3 side quests that give
  no crystals, and the Thunder Dragon's Lightning Round (the boss rush to 11 wins, with the Hylian
  Shield at 8, and the Silent Realm time trials).
- **Shields:** all 10: the Wooden, Iron and Sacred shields with their upgrades as tiers and the
  Hylian Shield, with costs and durability.
- **Bugs & Treasures:** one of each of the 12 bugs and 16 treasures, with where to find it and which
  upgrades and potions use it.
- **Linked items:** a thing listed in two trackers is one tick. Batreaux's wallet rewards are the
  Wallet's tiers (tick the 30-crystal reward and Items & Equipment shows the Big Wallet, and the
  other way round), the Goddess Chest pieces are their cubes' chests, the minigame and side-quest
  pieces are those prizes' tiers, the Hylian Shield is the Lightning Round's 8 wins, the Silent
  Realms' items are the trials, and the Sacred Flames are the sword's forms: 32 links, listed in
  `docs/ss.md` (*Linked items*). Each tracker still counts all its own rows. Saves from before were
  merged once, ticked on either side counting as ticked.
- **Potion Infusions:** a recipe book. The cover shows Luv's five potions (Heart, Stamina, Air,
  Revitalizing, Guardian) as cards with the game's icons and how much of each recipe you've made.
  - Pick one for its recipe, top to bottom: the base potion (Luv's Potion Shop, and its price), then
    each of Bertie's infusions (+, then ++) with its bugs as icons and quantities, its Rupees and what
    it does. Tick each step *Bought* / *Infused*; ticking one ticks those before it, unticking one
    clears those after.
  - *Still needed* adds up the bugs and Rupees left for that potion, and *Where to catch them* lists
    each bug with its regions and how to farm it, from the same data as Item Upgrades.
  - **Total Materials Required** (the card on the cover): every bug the potion steps left take,
    and the Rupees for Luv's base potions and Bertie's infusions, for *Potions you've bought* or
    *All potions* (the default). Tap a bug for where to catch it and which infusions need how many,
    then one to open its recipe. All potions comes to 50 bugs and 500 Rupees (180 for the
    infusions, exactly what the potions add to Item Upgrades' totals, and 320 for the potions).
  - Per save file, with *Mark all complete* (every potion bought and fully infused) and Undo. It's
    the same progress as Item Upgrades' Potions section, so a tick in one shows in the other, and in
    both screens' totals.
- **Item Upgrades:** the BotW Armor tracker's layout for Gondo's Scrap Shop and Bertie's potion
  infusions, with the game's own icons.
  - Four sections: Items (Slingshot, Beetle, Bug Net, Bow), Shields (Wooden, Iron and Sacred, and the
    Hylian Shield, which can't be upgraded), Pouch Items (Seed Satchel, Quiver, Bomb Bag) and Potions
    (Heart, Stamina, Air, Revitalizing, Guardian). 16 items, 26 upgrades.
  - Mark each item collected and set its level with the stars; the row shows the form you have
    (*Quick Beetle*). Each item's page has its forms, where to get it, whether it's sold or missable,
    and the treasures, bugs and Rupees for every upgrade left.
  - **Pouch items:** you can carry several of each, so *Add Seed Satchel #2* tracks another copy with
    its own level. Remove one from its page.
  - **Total Materials Required:** every remaining upgrade summed, with the Rupees, for *Collected
    items* or *All items (including not yet obtained)*. Tap a material for where to farm it and which
    upgrades need how many.
  - **The totals rule:** every item fully upgraded, and exactly one of each pouch item: your most
    upgraded Seed Satchel, Quiver and Bomb Bag (copy #1 if you have none). Other copies are tracked but
    tagged *Not in totals*. All items comes to 195 treasures and bugs and 1,510 Rupees.
  - **Potions** chip (on by default, since the totals mean everything): off leaves the potions out of
    the list, the totals and the overview row (145 treasures and 1,330 Rupees without them).
  - Search, All / Owned / Missing, and *Mark all complete* per section and for the list (collects; levels
    stay as they are).
  - **Shared progress:** the items, shields and pouch items are the Items & Equipment and Shields
    checklists' own rows, so setting the Quick Beetle in either shows in the other at once, on the
    same save file, and either screen's *Mark all* shows on both. The potions are Potion Infusions'.
    Only extra pouch copies (*Seed Satchel #2*…) are Item Upgrades' alone. Unticking a shared item
    clears its upgrades, as unticking its checklist row does. Saves from before this were merged once,
    at the higher level of the two; `docs/ss.md` has the details.
- **Lightning Round Records:** the Thunder Dragon's boss battles in Lanayru Gorge, as the game
  keeps them: **one best time per boss**, per save file (the game has no run-total or streak record).
  - **Hero Mode file** (a switch by the save file picker): Skyward Sword locks the mode to the file,
    so each file is marked once. A Normal file shows 11 bosses, Ghirahim's first battle to his third;
    a Hero Mode file adds Demise, for 12. Switching a file back to Normal never deletes Demise's
    record: it's kept, hidden, and the board says so.
  - Landscape first, like the Thrill Digger: the bosses as cards with their pictures and best times
    on the left; on the right the chosen boss's **personal best**, big, with its date and what it
    beat, the time entry and the times entered before. Portrait puts the boss above the cards.
  - **Times** are the game's `m:ss.cc` (hundredths, up to 59:59.99). Type digits and they fill from
    the right like a stopwatch (`14532` is 1:45.32), or `1:45.32`, `1.45.32` or `105.32`; Enter adds
    it, dated today, and clears the field for the next. The last 20 times per boss are kept, the best
    always among them. Edit or delete any entry; the snackbar's *Undo* puts the boss's times back.
  - Totals: records set of the file's slots, and the sum of the best times.
  - The round's prizes are shown from Side Quests & Minigames' Lightning Round item, where they're
    ticked; nothing is copied. With story gating on, a file before Lanayru's part of the Song of the
    Hero shows the board as locked; times already entered always show and count.
  - No mark-all: times aren't something to mark.
- **Thrill Digger Solver** (a tool, last): enter what each hole showed and every undug hole gets its
  exact Bomb and Rupoor chance, as a heat colour, with the safest hole to dig next starred.
  - It counts every board the game can deal, its re-deal of a 2×2 block of hazards included, on
    Beginner, Intermediate and Expert.
  - **Use the game's RNG** (on for Expert, optional on Intermediate): Skyward Sword's random numbers
    can only deal about 2.6 million boards, so the odds count just those, listed on the phone the
    first time (a few seconds, kept while the app runs). That's much sharper: 32 Expert boards
    cleared in 120 test games against 2. Once a single loop of the RNG fits, it can be locked for
    later rounds. If nothing fits (just after starting the game), the odds fall back to every board.
  - **Export board** copies the board as text and, once every Rupee is entered, says how many RNG
    states deal its Rupees and hazards (undug holes count as either kind), so real games can confirm
    the RNG port. The first real round checked fitted exactly one state.
  - Landscape first: the board on the left, and on the right a picker of the game's own Rupee, Rupoor
    and Bomb pictures with their hazard ranges. The Rupoor carries a purple minus so it can't pass for
    a Silver Rupee. A phone on its side hides the bars, as HW:DE's map does; portrait puts the picker
    under the board.
  - Tap a hole, then what it showed. With nothing tapped, the picker fills the starred hole, so
    following the advice is one tap per dig.
  - When several holes are certainly safe (after a Green Rupee, say), the star goes on the one
    nearest the hole you dug last rather than across the board, and every certainly safe hole gets
    a small green check.
  - Undo, Reset, and a saved board per difficulty. An impossible board outlines the holes that can't
    all be true.
  - **Rupees this round**, big at the top of the panel (above the picker in portrait): Green 1,
    Blue 5, Red 20, Silver 100, Gold 300, Rupoor −10. As in the game it never goes below 0, so it's
    counted in the order the holes were dug: a Rupoor at the start of a round takes nothing.
  - **Skipped**, for a hole you dug without seeing its colour. It counts as safe and dug, gives no
    clue, and shows in the total as "+ ? from N skipped" with the range. **Enter in-game total**
    works the colour back out from the game's count (one skipped hole above 0; several when the sums
    and the board allow) and offers to fill it in, so it becomes a clue again. A skipped hole lost
    to a Rupoor at 0 can't be told (a Green and a Blue both leave 0), and it says so.
  - It keeps no progress, so it isn't in the Progress Overview. `docs/ss.md` has the rules, the maths
    and the sources.
- Regions are the game's areas (Skyloft, The Sky, Thunderhead, Faron Woods, Eldin Volcano, Lanayru
  Desert…), with the exact place in each row. Tags give the items needed. Only one thing is
  missable: Fledge's barrels, before the Wing Ceremony.
- Three save files, **File 1–3**, as on the Wii and in HD.

**A Link Between Worlds**

Every ALBW tracker is a [checklist](docs/checklist-trackers.md). Sources and disagreements are in
`docs/albw.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Main Quest:** Hyrule (the Captain's sword, Zelda's Pendant of Courage, the Eastern Palace, the
  House of Gales and Tower of Hera, the Master Sword and Yuga at Hyrule Castle) and Lorule (the
  seven Sages' dungeons, the Triforce of Courage, Lorule Castle and Yuga Ganon), with each
  dungeon's boss, entry items and treasure. Pendants (of 3) and Sages (of 7) are totalled.
- **Items & Equipment:** Ravio's nine items as one row each, a tier for renting, buying and
  Mother Maiamai's Nice upgrade (Nice upgrades totalled, of 9); 14 pieces of gear with the sword,
  shield, mail, gloves, Lamp and Net upgrades as tiers; the 4 Master Ore; and the 5 Bottles.
- **Lost Maiamai:** all 100, 50 per world, in the Maiamai Map's areas, with the item each needs.
  Mother Maiamai's rewards are totalled (one per 10, of 10: nine Nice upgrades and the Great Spin).
- **Heart Pieces:** 28 Pieces of Heart (18 Hyrule, 10 Lorule) and 10 Heart Containers, with the
  hearts total (3 to 20). The seven won in mini-games are tagged.
- **Mini-games:** the seven prize games worth winning once, with each prize level as a tier:
  Cucco Ranch, both Rupee Rushes, Hyrule Hotfoot, Lorule's Fortune's Choice, Octoball Derby and
  the Treacherous Tower's courses (the Super Lamp and Super Net).
- Regions are Hyrule and Lorule, with the exact place in each row.
- Three save files, **File 1–3**.

**Tri Force Heroes**

Every TFH tracker is a [checklist](docs/checklist-trackers.md), for the game as last patched (Ver.
2.1.0, with the free Ver. 2.0.0 content). Sources and disagreements are in `docs/tfh.md`.
- **Progress Overview** (first): a row per tracker with its count, totals and next step.
- **Adventure:** the 32 Drablands levels by area, with each level's items, boss and materials; the
  96 Drablands Challenges, which open an area at a time when its fourth level is cleared; and King
  Tuft's 9 rewards for finishing an area's challenges and the whole list. The game's Completion
  List (of 128) and areas cleared (of 8) are totalled.
- **Outfits:** all 38, with each one's effect, Madame Couture's materials and price, and when it
  unlocks.
- **Den of Trials:** its eight zones of five floors, in order, down to the Fierce Deity Armor.
- The Coliseum (PvP, nothing one-off to win) isn't tracked.
- One save file, **Save File**: the game keeps only one.

**Echoes of Wisdom**

Every EoW tracker is a [checklist](docs/checklist-trackers.md). Each has tabs per section with
counts, region cards, search, *Not done*, *Hide unchecked* and a page per item. Sources and
disagreements are in `docs/eow.md`.
- **Progress Overview** (first): a row per tracker with its count, its totals (hearts, crystals)
  and the next thing to do. With *Hide unchecked* on, a row only says how many are left where.
- **Map:** the in-game map of Hyrule with every Piece of Heart, Heart Container, Stamp Stand and Might Crystal
  place at the game's own map pins, under a chip each.
  - A marker shows the game's map icon, orange until ticked and blue after; a crystal place shows how many it holds.
  - Tapping it opens a sheet to tick it or open its row. Ticking either side ticks both, mark-all included.
  - The story setting and *Hide unchecked* hide the same places as in the lists.
  - Each item page in those lists has **Show on map**, and each list has a map button.
  - `docs/eow-map.md` has the sources.
- **Main Quest:** the rifts, dungeons and bosses in story order, through Null. It also has Tri Rod,
  Bind and Reverse Bond, Tri's Power Lv. 2–11 (one row, a tier per level) and the Swordfighter
  weapons.
- **Echoes:** all 127 in Notebook order, tagged Object or Monster, with where each is first
  learned and its cost.
- **Heart Pieces:** 40 Pieces of Heart and 7 Heart Containers by region, with the hearts total
  (3 to 20).
- **Might Crystals:** all 150 crystals in 85 places. The *Upgrades* tab has Lueburry's Sword, Bow,
  Bombs and Energy levels as tiers and adds up the crystals spent, also 150.
- **Side Quests** (50), **Accessories** (28, plus the Great Fairy's four slots), **Smoothie
  Recipes** (59 smoothies and 10 potions), **Slumber Dojo** (15 challenges, their hidden goal times
  and Shisho's six rewards) and **Stamp Stands** (25, and the stamp card rewards).
- **Bottles, Automatons & More:** the 4 Fairy Bottles, Dampé's 6 Automatons, 11 outfits (3 amiibo)
  and the mini-games, with each reward tier.
- Regions follow Zelda Wiki's: Suthorn Prairie, Hyrule Castle, Hyrule Field, Eastern Hyrule Field,
  Lake Hylia, Gerudo Desert, Jabul Waters, Eldin Volcano (with Kakariko Village), Hebra Mountain,
  Faron Wetlands and the Eternal Forest.
- Two save files, **File 1** and **File 2**, as the game keeps per Switch user.

**Hyrule Warriors: Definitive Edition** (spin-off)

Each Adventure Mode map is a tracker of its own, drawn as the in-game map: its 16×8 squares with
what's done and what's left marked on each. All ten:
- the **Adventure Map** (128 squares);
- the **Great Sea Map** (98, with its Great Sea Rules and the cards that lift them);
- the **Master Quest Map** (128, every square under a Master Quest Rule);
- the **Master Wind Waker Map** (the Great Sea under Endless Night Rules);
- the **Twilight Map** (93, with the Twilight clouds and the Tears of Light that clear them);
- the **Termina Map** (85, with the masks and the Giants);
- the **Koholint Island Map** (84, with the Nightmares the Instruments of the Sirens banish);
- the **Grand Travels Map** (128, from two starts, with the Phantom Hourglass opening once both
  Giant Bosses fall);
- the **Lorule Map** (128, Hyrule and Lorule joined by Fissures);
- the **Rewards Map** (its 13 illustration battles, drawn as named tiles, since there's no art). The data, its sources and disagreements, and how the art lines up are in
`docs/hwde.md`.
- **Progress Overview** (first): a row per map with everything done of all, squares cleared and
  A-ranked, Heart Containers, Pieces of Heart, Gold Skulltulas and weapons, and the next step.
- **The map**:
  - Pinch to zoom, drag to pan, and tap a square. The + / − / Fit buttons zoom too.
  - Upright, it opens filling the screen's height around the start square.
  - Each square shows a bronze edge when cleared, gold with an "A" when A-ranked and a star when
    complete, with dots for Heart Containers, Pieces of Heart, Gold Skulltulas and searches still
    to do.
  - The lines between squares are drawn as in the game: wavy where a wall stops the way, straight where
    clearing one opens the other (and straight again once a path card like the Hookshot is used).
  - Squares that can't be challenged yet are dimmed. Any clear opens its neighbours here; the game's
    B/A-rank arrows aren't in any source.
  - Row letters and column numbers are drawn on, as are the start and the Dark Ruler.
  - In landscape on a phone, the Map tab hides the top bar, tabs and system bars, and the filter row
    carries the menu, Cards, List and ⋮.
- **A square's details**: a bottom sheet that opens all the way, or, in landscape on a phone, a
  panel down the right with the map still in use beside it. Tapping another square swaps the panel;
  ✕, back or the same square closes it. What's ticked most comes first:
  - the label, battle type and A-rank limits (KOs, time, damage: the guides' findings, since the
    game doesn't show them);
  - Not yet / Cleared / A rank, and *Mark complete*;
  - a tick box for each reward (whose it is and how to earn it: "A rank as Zelda", "Capture East
    Goron Keep as Zelda"), Gold Skulltula and search, two to a row;
  - then the victory condition, any rule and allowed warriors, the item cards a clear gives, both
    Gold Skulltulas' condition and place, quiz answers, and the square's picture under the search
    screen's 16×11 grid with each search's cell outlined.
- **List tab**: the same squares by colour (difficulty), with search over squares, rewards,
  warriors, keeps and cards.
- **Filters** (both tabs): not cleared (squares you can challenge first), not A-ranked, Heart
  Containers left, Pieces of Heart left, Gold Skulltulas left, searches left, *Searchable now* (with
  the cards you have), *Challengeable now* and a warrior ("what's left for Zelda").
- **Cards**: each item card with what it does on the map, the squares that give it, and its Item
  Card Shop price. A card counts as found once a square that gives it is cleared (DE's shop sells
  any card found before), and each can be set by hand.
- **Spoiler-safe** (⋮ menu, off by default):
  - Squares the save file hasn't reached show as *Unexplored* until revealed.
  - Search spots, quiz answers and Skulltula places stay hidden until you *Use the Compass* on the
    square, as the game's Compass card shows a square's secrets.
- **Mark all complete** on the List tab, for the map and for each colour, over the squares the
  filters leave. A complete square is A-ranked, with every reward, Skulltula and search ticked.
- Labels follow Prima's guide and Zelda Wiki: a row letter, then the column (start H-8, Dark Ruler
  A-6). Each sheet also gives SBAllen's form, which puts the column first (F-1).
- One save file, **Save File**: the game keeps one per Switch user.

## Layout

```
shared/       Kotlin Multiplatform module: models, logic, Compose UI, datasets
  src/commonMain/kotlin/gx/zelda/armor/    ← armor tracker, shared by every game
  src/commonMain/kotlin/gx/zelda/fairies/  ← Great Fairy tracker, shared by every game
  src/commonMain/kotlin/gx/zelda/mainquest/ ← main quest tracker (BotW; The Minish Cap with dungeons, items and Tiger Scrolls)
  src/commonMain/kotlin/gx/zelda/shrines/  ← shrine tracker (BotW; TotK with Lightroots) and Spirit Orb / Light of Blessing planner
  src/commonMain/kotlin/gx/zelda/shrines/scan/  ← map-screenshot scanner, photo reader and their review screen
  src/commonMain/kotlin/gx/zelda/screencam/  ← finding and unwarping a game screen in a photo
  src/commonMain/kotlin/gx/zelda/screencam/live/  ← live tracking of the max-zoom map and its Korok markers
  src/commonMain/kotlin/gx/zelda/koroks/scan/  ← the live Korok scan screen and its review
  src/commonMain/kotlin/gx/zelda/zeldanotes/  ← reading Zelda Notes map screenshots and recordings, and the import screen
  src/commonMain/kotlin/gx/zelda/compendium/  ← Hyrule Compendium tracker
  src/commonMain/kotlin/gx/zelda/koroks/   ← Korok Seed tracker and Hestu planner
  src/commonMain/kotlin/gx/zelda/sidequests/  ← side quest tracker (Side, Shrine and DLC quests)
  src/commonMain/kotlin/gx/zelda/bosses/   ← overworld boss tracker (Hinox, Talus, Molduga, Lynel)
  src/commonMain/kotlin/gx/zelda/kinstones/  ← Kinstone fusion tracker (Minish Cap)
  src/commonMain/kotlin/gx/zelda/map/      ← world map, its layers (MapLayerProvider) and marker sheet
  src/commonMain/kotlin/gx/zelda/hearts/   ← Pieces of Heart and Heart Containers (MC) with the hearts meter
  src/commonMain/kotlin/gx/zelda/figurines/  ← Minish Cap figurine tracker and Carlov's odds
  src/commonMain/kotlin/gx/zelda/checklist/  ← the generic, data-driven checklist tracker (docs/checklist-trackers.md)
  src/commonMain/kotlin/gx/zelda/adventure/  ← Hyrule Warriors Adventure Mode maps: the grid view, square sheet and list (docs/hwde.md)
  src/commonMain/kotlin/gx/zelda/thrilldigger/  ← Skyward Sword's Thrill Digger solver: the exact solver, its saved boards and screen (docs/ss.md)
  src/commonMain/kotlin/gx/zelda/upgrades/  ← Skyward Sword's Item Upgrades tracker, built on the armor tracker's model, its progress shared with the checklists (docs/ss.md)
  src/commonMain/kotlin/gx/zelda/potions/   ← Skyward Sword's Potion Infusions recipe book, on Item Upgrades' data and progress (docs/ss.md)
  src/commonMain/kotlin/gx/zelda/core/ui/  ← shared UI, incl. the region-card pieces, the menu (AppDrawer.kt) and Mark all complete (BulkCompleteControl.kt)
  src/commonMain/kotlin/gx/zelda/settings/  ← Settings screen and the app-wide settings view model
  src/commonMain/kotlin/gx/zelda/overview/  ← Progress Overview screen and its provider list
  src/commonMain/composeResources/files/<game>/armor.json    ← per-game armor data
  src/commonMain/composeResources/files/<game>/fairies.json  ← per-game Great Fairies
  src/commonMain/composeResources/files/<game>/shrines.json  ← per-game shrines (TotK's also has the Lightroots)
  src/commonMain/composeResources/files/<game>/shrine_map.json  ← shrine/tower world positions + icon templates
  src/commonMain/composeResources/files/<game>/map_chrome.json  ← the map screen's frame, for reading photos
  src/commonMain/composeResources/files/<game>/map_chrome_track.bin  ← the frame at 240×135, for live tracking
  src/commonMain/composeResources/files/<game>/korok_marker.json  ← the found-Korok marker's grey template
  src/commonMain/composeResources/files/<game>/koroks.json   ← per-game Korok Seeds + Hestu costs
  src/commonMain/composeResources/files/<game>/korok_puzzle_types.json  ← puzzle-type taxonomy
  src/commonMain/composeResources/files/botw/bosses.json     ← overworld boss spawns
  src/commonMain/composeResources/files/botw/main_quest.json ← towers, Divine Beasts, memories
  src/commonMain/composeResources/files/mc/main_quest.json   ← Minish Cap dungeons, items, Tiger Scrolls
  src/commonMain/composeResources/files/botw/compendium.json  ← BotW Hyrule Compendium
  src/commonMain/composeResources/files/botw/side_quests.json ← BotW side, Shrine and DLC quests
  src/commonMain/composeResources/files/mc/kinstones.json   ← Minish Cap Kinstone fusions
  src/commonMain/composeResources/files/botw/map/  ← map tile pyramid (tools/map-tiles/build_tiles.py)
  src/commonMain/composeResources/files/mc/heart_pieces.json  ← MC Pieces of Heart and Heart Containers
  src/commonMain/composeResources/files/mc/figurines.json   ← Minish Cap figurines (scripts/build-mc-figurines.py)
  src/commonMain/composeResources/files/loz/*.json  ← The Legend of Zelda checklists (docs/loz.md)
  src/commonMain/composeResources/files/aol/*.json  ← Zelda II checklists (docs/aol.md)
  src/commonMain/composeResources/files/alttp/*.json  ← A Link to the Past checklists (docs/alttp.md)
  src/commonMain/composeResources/files/la/*.json          ← Link's Awakening checklists (docs/la.md)
  src/commonMain/composeResources/files/oot/*.json         ← Ocarina of Time checklists (docs/oot.md)
  src/commonMain/composeResources/files/fs/stages.json  ← Four Swords checklist (docs/fs.md)
  src/commonMain/composeResources/files/ww/*.json   ← The Wind Waker checklists (docs/ww.md)
  src/commonMain/composeResources/files/fsa/*.json  ← Four Swords Adventures checklists (docs/fsa.md)
  src/commonMain/composeResources/files/tp/*.json   ← Twilight Princess checklists (docs/tp.md)
  src/commonMain/composeResources/files/ph/*.json   ← Phantom Hourglass checklists (docs/ph.md)
  src/commonMain/composeResources/files/st/*.json   ← Spirit Tracks checklists (docs/st.md)
  src/commonMain/composeResources/files/ss/*.json   ← Skyward Sword checklists, and upgrades.json for Item Upgrades and Potion Infusions (docs/ss.md)
  src/commonMain/composeResources/files/albw/*.json        ← A Link Between Worlds checklists (docs/albw.md)
  src/commonMain/composeResources/files/tfh/*.json  ← Tri Force Heroes checklists (docs/tfh.md)
  src/commonMain/composeResources/files/eow/*.json  ← Echoes of Wisdom checklists (docs/eow.md)
  src/commonMain/composeResources/files/hwde/<map>.json  ← an HW:DE Adventure Mode map (tools/hwde/build_maps.py, docs/hwde.md)
  src/commonMain/composeResources/files/hwde/<map>/  ← its picture per square and preview
  src/commonMain/composeResources/drawable/armor_<id>.png    ← armor icons, shared by every game
  src/commonMain/composeResources/drawable/map_*             ← BotW map marker icons (scripts/build-map-icons.py)
  src/commonMain/composeResources/drawable/hwde_*            ← HW:DE item-card and reward icons (tools/hwde/build_maps.py)
  src/commonMain/composeResources/drawable/thrill_*          ← SS Rupee, Rupoor and Bomb pictures (scripts/fetch-thrill-digger-icons.py)
  src/commonMain/composeResources/drawable/ss_up_*, ss_mat_* ← SS item, treasure and bug icons (scripts/fetch-ss-upgrade-icons.py)
androidApp/   Android application shell
```

Each game's trackers are listed in `core/Games.kt`; storage keys are prefixed with the game id.
`Games.all` is sorted by each game's `released` date (`ReleaseDate`, with a `sameDay` tiebreak),
so a new game lands in release order wherever it's added to the list. App-wide settings (shown
games, open menu sections) are one `AppSettings` value under the `app_settings` key
(`core/AppSettings.kt`, `AppSettingsStorage` in `core/AppDataStore.kt`).

### Adding a game: checklist trackers

New games are built from **checklists** (`TrackerKind.CHECKLIST`) rather than a package per
tracker. A checklist is one JSON file under `files/<game id>/` plus one line in `core/Games.kt`:

```kotlin
Tracker(TrackerKind.CHECKLIST, "Heart Pieces", "…", dataFile = "eow/heart_pieces.json")
```

That gives the game a tracker with:
- section tabs, region cards with counts, search that respects spoiler hiding, and *Not done* and
  *Missable* filters;
- a page per item, with tiers (upgrade levels) where an item has them;
- summary formulas such as `3 + floor(pieces / 4) + containers`;
- its own Progress Overview row, so a game made of checklists gets an overview for free.

A game can list any number of checklists. The menu, home screen and overview tell them apart by
their `dataFile`. `docs/checklist-trackers.md` has the JSON format, the formula syntax, the
spoiler rules and how the overview picks a next step. `ChecklistDatasetsTest` validates every
checklist that any game lists.

### Adding a tracker to the Progress Overview

Write a `ProgressProvider` (`core/ProgressSummary.kt`) next to the tracker's storage, e.g.
`koroks/data/KorokProgressProvider.kt`: it maps one save file's progress to a `ProgressSummary`
(title, done / total, an optional detail line, an optional next step, and whether it counts
toward map completion). Then return it for the tracker's `TrackerKind` in
`overview/OverviewProviders.kt`; that `when` is exhaustive, so a new kind won't compile until it
has a branch. The interface's KDoc has the details. Checklists need none of this: the
`CHECKLIST` branch gives every checklist a `ChecklistProgressProvider` row.

Each game's overview is built from its own tracker list in `core/Games.kt`, so a row appears only
for a game that has that tracker. A game gets an overview by listing `TrackerKind.OVERVIEW` (BotW
and The Minish Cap do; TotK doesn't yet, and builds no rows). A provider shared by games that
differ, like Main Quest, works out which game it has from that game's dataset.

Region cards use `core/ui`'s `RegionHeader`. It adds " Tower" to the region name by default, for
BotW's and TotK's tower regions; The Minish Cap's trackers pass `suffix = ""` and `found = null`,
since their regions are places and their entries are only done or not.

## Building

Requires JDK 17+ and the Android SDK (set `sdk.dir` in `local.properties`).

```bash
./gradlew :shared:jvmTest            # unit + dataset validation tests
./gradlew :androidApp:assembleDebug  # APK at androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug   # install on a connected device/emulator
```

### TGXCode buttons

`.tgxcode/commands.json` declares these buttons:

- **Build** / **Rebuild** (`scripts/build.sh`, `--clean` for Rebuild) assemble the debug APK,
  finding the SDK even without a `local.properties`.
- **Install on phone** (`scripts/install-wireless.sh`) finds the phone over Wireless Debugging
  (it uses adb's mDNS discovery and connects if needed), installs the debug build and opens it.
  Pair the phone once by hand first; the script prints the `adb pair` command if it can't find one.
- **Land** (`scripts/land.sh`) merges the current worktree's PR and fast-forwards the main
  checkout. `--status` and `--dry-run` show what it would do.

## How the map scan works

`ShrineMapScanner` is pure Kotlin in `commonMain`, with no OpenCV or ML Kit. Each platform only
decodes the image (`BitmapFactory` on Android, `ImageIO` on the JVM). It is a port of the Python
feasibility prototype in `tools/shrine-map-spike` on branch `worktree-shrine-map-spike` (see its
`REPORT.md`):

1. Colour masks in OpenCV's 8-bit HSV, computed with OpenCV's fixed-point formula so the
   prototype's thresholds carry over.
2. Candidate shrine and tower icons: connected components of the icon-blue masks, each confirmed
   by matching a template around it.
3. Registration. The fully zoomed-out map is world × 0.135 px plus a translation. Candidates vote
   for a translation against the world positions of their own class (shrine or tower), and the
   inliers are averaged. A fit needs at least 3 icons, and at least half of the icons found. A free
   least-squares scale more than 3% from 0.135 means the map isn't fully zoomed out.
4. Each shrine's projected spot is classified from a window around it: silhouette score, outline
   coverage, glyph colour, and other colours covering the spot.

**Hero's Path mode** is detected from its green frame. Trail pixels count as unknown: icons are
matched on the pixels around the trail, and a spot only counts as empty when at least half its
outline is free of trail. In the golden test, pasting a real trail over icons known to be there
leaves every one readable except Shee Vaneer, which already half-overlaps the Dueling Peaks pair.
The timeline slider replaces the usual HUD as the covered area.

Golden tests (`shared/src/jvmTest/.../scan/ShrineMapGoldenTest.kt`) run the scanner on four real
screenshots in `shared/src/jvmTest/resources/map-samples`. The all-complete save gives 132
Completed and 4 Unsure: Hawa Koth (under the Travel Medallion), Akh Va'quot (under Vah Medoh), and
Ree Dahee and Shee Venath (stacked). The partial save, whose south shot is in Hero's Path mode,
reads Tena Ko'sah, Qua Raym and Shae Mo'sah as Not done. The tests also compare every shrine in
every shot with the prototype's CSV output. The only differences are three spots in the Hero's
Path shot, where trail covers most of the outline: the prototype calls them empty and the port
calls them Unsure.

`shrine_map.json` holds the shrine and tower positions from objmap.zeldamods.org, matched to
`shrines.json` by name, and the prototype's 33×33 shrine and tower silhouettes.

### Photos of the screen

Any image that isn't 1920×1080 is taken to be a photo. `PhotoShotReader` (`shrines/scan/`) and
the screen-finding code in `screencam/`, all pure Kotlin, turn it into a screenshot-like frame for
the same scanner:

1. **Find the screen.** `ScreenFinder` thresholds a ~640 px copy of the photo three ways (below
   Otsu, at Otsu, and saturated colours) and takes a four-sided outline of each mask's largest blob
   and of all its blobs together. Each outline is tried with each corner as top left. The winner is
   the one whose frame (header, ornaments and button bar) best matches the grey thumbnail in
   `map_chrome.json`. A lit room or glare scores far below a real screen, and the photo can be at
   any angle and either way up, so EXIF orientation doesn't matter.
2. **Unwarp.** `Rectifier` maps the screen into the screenshot's 1920×1080 frame, averaging several
   samples per pixel where the photo has more pixels than the screen, and colour moiré is smoothed
   out of the chroma.
3. **Calibrate colour.** The frame is the same in every screenshot, so it serves as a colour chart:
   each channel is mapped so that the photo frame's percentiles match the reference frame's.
   Hero's Path mode turns the frame's ornaments green, so both the normal and Hero's Path references
   are tried, and the one that lines up more icons is kept.
4. **Refine.** The scanner's own icon detection and registration pair icons with their world
   positions. A RANSAC homography from world to photo corrects the outline's few pixels of error,
   twice, taking the fit from about 3 px RMS to under 1 px.
5. **Read** with camera thresholds (`ShotSource.CAMERA`):
   - icon detection is looser;
   - blue bloom is allowed around icons;
   - any warm colour inside an icon's outline counts as an unfinished shrine's glyph;
   - a spot is called empty only with no trace of an icon and nothing like a dimmed pin or stamp on it.

   A photo needs at least 8 icons lining up within 2 px RMS.

A screen with fewer than 0.75 photo pixels per screen pixel is rejected as too small. A full-frame
16:9 image smaller than 1920×1080 is a resized screenshot and gets the usual "use the original"
message.

`PhotoScanGoldenTest` covers two kinds of photo:

- **A real phone photo:** HDR, the Switch in handheld mode, on its side, with heavy moiré and bloom.
  It shows the same save and pan as `all-complete/1000002225`.
  - 94 icons line up, with 0.77 px RMS.
  - 91 of the screenshot's 93 Completed shrines read the same; Sha Warvo and Noe Rajee (under a
    stamp) are Unsure.
  - Nothing contradicts the screenshot.
- **`FakeCamera` photos of the four golden screenshots:** keystoned, turned, colour-shifted, shaky,
  and in Hero's Path mode. Its simulated sensor gathers light over each pixel's area, so the
  panel's RGB stripes alias into moiré the way they do in real photos.
  - None contradicts its screenshot.
  - 7–20% of definite spots are left Unsure.
  - Of the partial save's three unfinished shrines, a photo reads Tena Ko'sah. Qua Raym and Shae
    Mo'sah, on Eldin's lava and beside a stable, come out Unsure.

On a desktop JVM a 9 MP photo takes about 2 s. `ScreenChromeReferenceTest` computes `map_chrome.json`
from the golden screenshots.

## How the live Korok scan works

At max zoom the game draws a marker on every Korok you've found and nothing on the rest. A
screenshot of each view would take hundreds, so the scan reads the camera's frames as you pan.
Everything runs on the frame's grey (luma) plane, in pure Kotlin in `screencam/live/`:

1. **Find the screen** (`ScreenAcquirer`), with the whole screen in view. Outlines come from
   `ScreenFinder` on a ~320 px grey copy of the frame, plus centred 16:9 rectangles both ways up (a
   screen filling the frame can split the brightness threshold inside itself). Every outline, in
   every orientation, is scored by how much its frame looks like the map screen's. The tracker then
   refines the best few, and one is accepted when its fit correlates at 0.5 or more, its rescored
   frame likeness is 0.45 or more, and it's a plausible screen: convex, inside the frame, sides and
   corners near a rectangle's. A screen that can't then be placed on the map three times running is
   dropped, and the search starts over.
2. **Follow it** with `ChromeTracker`: Gauss–Newton (Lucas–Kanade) on the screen's homography plus a
   brightness gain and offset, aligning the map screen's frame (ornaments, header, button bar) with
   a 240×135 grey reference, coarse to fine, with Huber weights. It first fits from the last frame's
   pose and only searches around when that fails.
3. **Find the map** with `MapLocator`. The screen's view is cut out at half a pixel per world unit (the
   map's max-zoom scale is 2.985 screenshot pixels per unit) and matched by high-pass correlation
   against the bundled tiles: over the whole map to start, then near where the pan predicts.
4. **Straighten** the screen from the map. A 4×3 grid of blocks of the view is matched on its own,
   and a robust homography through them corrects the tracker's pose. The frame alone can drift when
   the phone moves in and the screen's top leaves the picture; the map fills the screen and can't.
   When the whole-view match fails, the blocks are matched in a wider window to rebuild the pose. A
   fix far from both the predicted pan and the last fix is a false lock and is rejected.
5. **Read markers** at every Korok position in view (away from the readouts and the cursor) with a
   grey template, and **accumulate** across frames. Found needs 3 frames with a marker and 70% of the
   frames seen; empty needs 4 without and none with; anything else is unsure. The two pairs drawn on
   top of each other (X05/X06, L17/L18) are always unsure.

When it can't read a frame, the status line says why: "No screen found: best match 0.31",
"Screen too small: move closer", "Hold steadier: screen match 0.52, fit 0.41", "Can't place this on
the map (best 0.22): zoom all the way in" or "Lost the map: pan slower". A dashed outline shows the
best rejected screen. Logcat (`KorokScan`) logs the same reason once a second with the scores, and
once what the camera actually delivers (size, rotation, crop and strides). In a debug build, **Save
frames** records a session to the app's files (`korok-debug/<timestamp>/`) that
`tools/korok-clips/from_session.py` turns into a test clip.

`KorokClipGoldenTest` runs every clip in `shared/src/jvmTest/resources/korok-clips/`. Each clip is
grey frames extracted from a phone video by `tools/korok-clips/extract.py`, plus a hand-checked
`expected.json`. The handheld clip (the Switch in handheld mode, filmed at close range over
Hebra, Tabantha and Ridgeland):

- Tracks 153 of 154 frames.
- Reads all 12 Koroks with a marker as found and none of the 25 empty ones as found; 8 empty spots
  stay unsure.
- Takes a 25 ms median per frame on a desktop JVM (track 7.5, locate 16, markers 0.2; p90 32). Finding
  the screen takes 12–40 ms per try on 1280-wide frames, and the first whole-map search about 50 ms
  once the map overview is decoded (done while the scan screen opens).

The marker template was built from that same clip's markers, so a docked-TV clip is the real test of
how well it generalises. To add one, run the script on the video, write its `expected.json` (the
test's `KOROK_SHEET=<dir>` option writes a crop of every Korok seen, to check by eye), and rerun the
test with `REGENERATE_KOROK_MARKER=1` to fold its markers into the template.

## Data sources

Armor data was compiled from Zelda Wiki and cross-checked against Zelda Fandom and Game8.
Where sources disagreed, the majority value was used.

BotW main quest data (towers, Divine Beasts and Recovered Memories) comes from Zelda Wiki: the
memory list and Pikango's hints from its Memory and Captured Memories pages, and the beasts from
each Divine Beast quest page. Tower climbing tips are cross-checked against Zelda Fandom, Zelda
Dungeon, GameWith and RankedBoost, and memory directions against RPG Site and Nintendo Insider.

Majora's Mask data (`files/mm/`) was compiled by hand from Zelda Wiki, Zelda Dungeon (its wiki and
walkthrough), StrategyWiki's Majora's Mask guide and Zelda's Palace; `docs/mm.md` lists the sources
for each checklist and where they disagree.

Oracle of Seasons data (`files/oos/`) was compiled by hand from Zelda Wiki, Zelda Dungeon,
TourianTourist's GameFAQs ring guide and StrategyWiki; `docs/oos.md` lists the sources and where
they disagree.

Oracle of Ages data (`files/ooa/`) was compiled by hand from Zelda Wiki, Zelda Dungeon,
TourianTourist's GameFAQs ring guide and StrategyWiki; `docs/ooa.md` lists the sources and where
they disagree.

The Wind Waker data (`files/ww/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback Machine
snapshots) and StrategyWiki. The Wind Waker Randomizer's location list settled the Secret Caves,
and the zeldaret/tww decompilation the save-file count. It was compiled with scripts that aren't
committed. `docs/ww.md` covers each tracker and every disagreement between sources.

Minish Cap main quest data (`files/mc/main_quest.json`: dungeons, items and Tiger Scrolls) comes
from Zelda Wiki, cross-checked against StrategyWiki, Zelda Universe and Zelda Dungeon; see
`docs/mc-main-quest.md`.

TotK armor data comes from Zelda Wiki's TotK armor tables, cross-checked against Game8,
Fextralife, Thonky, Zelda Universe, Gamer Guides and Samurai Gamers. TotK Great Fairy data comes
from Zelda Wiki, PowerPyx, Nintendo Life and Shacknews. The TotK Materials pouch order is from
Zelda Wiki only.

BotW shrine data (names, trials, Shrine Quests, locations) comes from Zelda Wiki and Zelda Fandom.
Shrines are grouped by Sheikah Tower region as Game8 and Thonky group them; the wikis use
geographic regions instead, so a few border shrines (e.g. Ishto Soh, Kah Mael, Rona Kachta) sit
under a different region there. Trial names keep the in-game wording, including the "A" in
"A Minor Test of Strength".

TotK shrine and Lightroot data (`files/totk/shrines.json`) combines objmap's TotK data with the
wikis; see `docs/totk-shrines.md`. Names, positions, sky/surface/cave and Skyview Tower regions
are datamined (radar-totk.zeldamods.org and the objmap-totk repo). Trials, Shrine Quests and
location hints come from Zelda Wiki, cross-checked against Zelda Fandom and Game8. The Light of
Blessing numbers are from Zelda Wiki, Fandom and Game8.

BotW Compendium numbering is from Zelda Wiki's *Hyrule Compendium Items in Breath of the Wild*
(Normal and Master Mode, with and without The Champions' Ballad). Common locations and drops are
the in-game text, from the Hyrule Compendium API (api.hyrule-compendium.com) checked against
Zelda Wiki's `Data:Hyrule Compendium/BotW`, which also supplied the drops the API got wrong.
Locations for entries the game lists as unknown, Symin's picture sales and the *Limited* notes
come from Zelda Wiki and Zelda Fandom.

Armor icons are the in-game inventory icons, downloaded from Zelda Wiki by
`scripts/fetch-armor-icons.py`. Icons are keyed by piece id, so a piece that appears in both games
(same id, e.g. `hylian_hood`) shares one image, taken from BotW (`File:BotW <name> Icon.png`).
Pieces only in TotK use `File:TotK <name> Icon.png`. Run the script after adding a piece to any
game's `armor.json`; it fetches only the icons that are missing (`--force` re-downloads all of
them), and each game's armor dataset test fails if any piece has no icon.

BotW Korok Seed data (`files/botw/koroks.json`, `files/botw/korok_puzzle_types.json`) comes
from objmap.zeldamods.org and its radar backend: ids, positions, tower regions and each seed's
datamined `korok_type`, which decides its puzzle type. The puzzle-type names and descriptions
follow Zelda Wiki. See `docs/korok-puzzle-types.md` for the fields, the coordinate system and how
the types map. Hestu's per-upgrade seed costs are from Zelda Wiki's Korok Seed page
(208 + 73 + 160 = 441).

BotW side quest data (`files/botw/side_quests.json`) comes from Zelda Wiki's *Side Quests in Breath
of the Wild* and *Shrine Quest* pages and each quest's page, cross-checked against Game8's side
quest, Shrine Quest and DLC lists: 76 side quests, 42 Shrine Quests and 13 EX side quests. Zelda
Fandom and Thonky give the same 76. Regions are tower regions, worked out from where the quest giver stands using
objmap's tower-region map data, which Game8's per-quest regions agree with. A Shrine Quest takes its
shrine's region instead, so both trackers group it the same way, and its `shrineId` is the shrine's
id in `shrines.json`. Timing notes are only given where a source states them.

BotW overworld boss data (`files/botw/bosses.json`) comes from objmap.zeldamods.org and its radar backend: every
boss actor's id, position, tower region and parameters, with names and drops from `compendium.json`. Counts and the
Medals of Honor are cross-checked against Zelda Wiki; where they differ, the game data wins.
`scripts/build-botw-bosses.py` rebuilds it, and `docs/overworld-bosses.md` covers the fields, the counts and what's
uncertain (Lynel colours).

MC heart data (`files/mc/heart_pieces.json`) comes from Zelda Wiki's Piece of Heart, Heart Container and
Kinstone Fusion pages, cross-checked against Zelda Dungeon's Heart Pieces list, Zelda Universe and Zelda's Palace:
44 pieces and 6 containers in all four. `docs/mc-heart-pieces.md` covers the fields, regions and where the guides
disagree.

Minish Cap Kinstone fusion data (`files/mc/kinstones.json`) comes from Zelda Wiki's Kinstone Fusion table and
character pages, cross-checked against silktail's GameFAQs Kinstone Fusion Guide, Thonky's numbered fusion list and
Zelda Dungeon's walkthrough. Disagreements were settled with the zeldaret/tmc decompilation's own fusion table, which
also gives each fusion's in-game id. `docs/mc-kinstones.md` covers the counts, the fields, the shape names and every
discrepancy.

The BotW map art is Nintendo's, from the game's `MapTex` files as objmap.zeldamods.org serves them.
`tools/map-tiles/build_tiles.py` fetched it once and turned it into the bundled WebP tile pyramid;
see `tools/map-tiles/README.md` to rebuild it. objmap grants no licence for the art, so the map is
fine for a personal, sideloaded build but would need replacing before any store release. Shrine,
tower and Korok positions are objmap's (see above).

The EoW map art is Zelda Wiki's copy of the game's map of Hyrule, tiled by `tools/map-tiles/build_eow_tiles.py`.
Its markers are at the game's own map pins, datamined by MrCheeze/eow-tools and matched to the checklists by hand
(`tools/eow-map/`). The art and the game's map icons have the same personal-build caveat; `docs/eow-map.md` covers
the sources and how each pin was matched.

The map's shrine and tower markers are objmap's vector tracings of the game's map icons, split by
`scripts/build-map-icons.py` so a shrine's glyph can turn from orange to blue as it does in the game.
The Korok marker is the Korok Seed's inventory icon from Zelda Wiki. `docs/map-icons.md` lists the
source of each icon and has licence notes: objmap's repository is GPL-3.0, so the icons have the same
personal-build caveat as the map art.

A Link to the Past data (`files/alttp/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback
Machine snapshots) and StrategyWiki, with speedrun.com's 100% rules deciding what's tracked.
Every count was checked against all three. `docs/alttp.md` covers each tracker, the save files
and every disagreement between sources.

Link's Awakening data (`files/la/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback Machine
snapshots), Zelda's Palace and Nintendo Life, with StrategyWiki and Zelda Universe for spot
checks. The LADX disassembly settles the capacity upgrades and save slots. Every count was checked
against at least three sources. `docs/la.md` covers each tracker and every disagreement.

Ocarina of Time data (`files/oot/*.json`) comes from Zelda Wiki, StrategyWiki and Zelda Dungeon,
cross-checked against Flying Omelette, Zelda Universe, Zelda's Palace, Jegged and GameWith. The
zeldaret/oot decompilation settles disagreements (fishing weights, Dampé, Big Poe spawns) and the
save-slot count. Sources and every disagreement are in `docs/oot.md`.

Four Swords data (`files/fs/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback Machine
snapshots) and StrategyWiki, with the Anniversary Edition's text dump on GameFAQs settling exact
names and speedrun.com's 100% rules deciding what's tracked. `docs/fs.md` covers each tracker, the
save files and every disagreement between sources.

Four Swords Adventures data (`files/fsa/`) comes from Zelda Wiki, Zelda Dungeon's walkthrough
(via Wayback Machine snapshots) and the North American manual, cross-checked against Zelda Universe,
Zeldapedia, both Wikipedias and Nintendo's Japanese site. `docs/fsa.md` covers each tracker, the
save files, what isn't tracked and every disagreement between sources.

Minish Cap figurine data (`files/mc/figurines.json`) comes from Zelda Wiki's *Figurines in The
Minish Cap* (names, North American descriptions, who's pictured) and the zeldaret/tmc decompilation
(each figurine's egg colour and the story flag or Kinstone fusion that unlocks it, plus the odds
formula). It is cross-checked against Zelda Wiki's Figurine Gallery table, StrategyWiki and
Zeldapedia. `scripts/build-mc-figurines.py` rebuilds it, and `docs/mc-figurines.md` covers the
checkpoints, the odds and where the sources disagree.

The Legend of Zelda data (`files/loz/`) comes from Zelda Wiki, Zelda Dungeon (through the Wayback
Machine) and StrategyWiki, cross-checked against each other. `docs/loz.md` covers the save files and
the two quests, the screen grid, what isn't tracked and why, and every disagreement between sources.

Zelda II data (`files/aol/`) comes from Zelda Wiki, Zelda Dungeon (its old guide and current wiki,
through the Wayback Machine) and StrategyWiki, with TASVideos' 100% definition as a tie-breaker.
`docs/aol.md` covers the save files and replays, what's tracked and why, the name map between
sources and every disagreement.

Twilight Princess data (`files/tp/`) comes from Zelda Wiki, Zelda Dungeon (its HD Normal Mode
walkthrough and wiki, via Wayback Machine snapshots where needed) and StrategyWiki, with Zelda's
Palace for the HD stamps and GameFAQs guides for spot checks. The zeldaret/tp decompilation settles
the letters' triggers. Every count was checked against at least two sources. `docs/tp.md` covers
each tracker, the HD differences and every disagreement.

Phantom Hourglass data (`files/ph/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback Machine
snapshots) and IGN's guide, with StrategyWiki's walkthrough for story order and Nintendo's manual
for the save files. Every count was checked against all three. `docs/ph.md` covers each tracker
and every disagreement, including the Treasure Map numbers the sources don't agree on.

Spirit Tracks data (`files/st/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback Machine
snapshots) and IGN's guide, with StrategyWiki as a fourth source, speedrun.com's 100% rules
deciding what's tracked and Nintendo's manual for the save files. Every count was checked against
all three. `docs/st.md` covers each tracker and every disagreement.

Skyward Sword data (`files/ss/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback Machine
snapshots) and Samurai Gamers, with StrategyWiki and Zelda's Palace for spot checks. The Skyward
Sword randomizer's check list, read from the game's files, settles where each item is and which cube
opens which chest; the zeldaret/ss decompilation gives the save-file count. The Thrill Digger
Solver's rules come from Zelda Wiki and lepelog's port of the game's board generator, and its
pictures are Zelda Wiki's Skyward Sword renders. Item Upgrades' icons are the game's inventory and
Collection icons, from Zelda Wiki. `docs/ss.md` covers each tracker, the solver, the HD
differences and every disagreement.

A Link Between Worlds data (`files/albw/`) comes from Zelda Wiki, Zelda Dungeon (via Wayback
Machine snapshots), IGN and Zelda Universe. Every count was checked against at least three of
them. `docs/albw.md` covers each tracker, the save files and every disagreement.

Tri Force Heroes data (`files/tfh/`) comes from Zelda Wiki, Prima's official eGuide, IGN and Zelda
Dungeon (via Wayback Machine snapshots). Every level's challenges and rewards were compared between
Zelda Wiki and Prima. `docs/tfh.md` covers each tracker, the single save file and every
disagreement.

Echoes of Wisdom data (`files/eow/`) comes from Zelda Wiki, Zelda Dungeon's guides and Game8, with
Nintendo Life as a third source for echo and stamp locations. Every count was checked against all
three. `docs/eow.md` covers each tracker, the save files, the region convention and every
disagreement between sources.

Hyrule Warriors: Definitive Edition data (`files/hwde/`) is built by `tools/hwde/build_maps.py`
from the Hyrule Warriors Helper's DE data and art at a pinned commit. Every square is checked
against SBAllen's GameFAQs guide (archived on the Wayback Machine). Card names and uses come from
Zelda Wiki's Item Card table, and Nintendo Life and RPG Site independently confirm the weapon and
warrior squares. The few fixes are in `tools/hwde/corrections.json`, each with its source.
`docs/hwde.md` covers the cross-check, the open disagreements, the coordinate conventions, the
single save file, how the art lines up and the licences.

The game themes' header fonts are Press Start 2P, Pixelify Sans, Fredoka, Josefin Sans and Cinzel, all
from Google Fonts under the SIL Open Font License 1.1; `docs/fonts.md` lists their sources and
which games use them, and the licences are in `docs/licenses/`. No Nintendo font or art is
bundled for the themes: the colours are picked from the games' menus, and the ornaments are drawn
in code.
