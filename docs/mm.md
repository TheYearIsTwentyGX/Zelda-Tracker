# Majora's Mask

Majora's Mask is built entirely from checklists (`TrackerKind.CHECKLIST`, see
`docs/checklist-trackers.md`): nine JSON files in
`shared/src/commonMain/composeResources/files/mm/` plus the tracker lines in `core/Games.kt`.
`MmChecklistDatasetTest` checks the counts and cross-references below, and `ChecklistDatasetsTest`
checks the format.

The data is for the **original Nintendo 64 release**. Where Majora's Mask 3D differs, the item's
`notes` say so; there are no separate 3DS trackers.

Nothing is fetched at runtime. The files were compiled by hand from the sources below. The scripts
that drafted the Stray Fairy and Spider House lists aren't committed, as with the TotK shrines.

## Save files

Two files, `file_1` and `file_2` ("File 1", "File 2"), as on the cartridge. An owl save (quitting
at an Owl Statue) is a temporary save on one of those files, not a third file (Zelda Wiki,
*Owl Statue*). The ids are the ones the coming-soon placeholder already had.

## The three-day cycle

The trackers record **permanent** completion: what survives the Song of Time. That's masks,
songs, items and upgrades (not the Razor Sword), Pieces of Heart, Heart Containers, Remains, Owl
Statues, Tingle's maps, bank deposits, and the rewards for side quests. Quest items (Moon's Tear,
Title Deeds, Room Key, letters), Rupees in the wallet, Stray Fairies, Gold Skulltula tokens and
Bombers' Notebook promise stickers do not survive it.

So every item says when it happens (Day 1–3, Night 1–3, hours where the sources give them), and
anything whose steps all have to fall between one Dawn of the First Day and the next is tagged
**One cycle**. Stray Fairies and Gold Skulltulas are listed one by one so you can tick them off
during a run; the section notes say they reset, and the reward rows (Great Fairies, Spider House
rewards) are what's kept.

## Trackers and counts

| File | Tracker | Sections (items) |
|---|---|---|
| `main_quest.json` | Main Quest | Clock Town 1, Temples 4, The Moon 1. Summary: Remains of 4. |
| `masks.json` | Masks | Transformation Masks 4, Masks 20 (24 in all) |
| `heart_pieces.json` | Heart Pieces | Pieces of Heart 52, Heart Containers 4. Summary: hearts = 3 + ⌊pieces / 4⌋ + containers, 20 at most. |
| `bombers_notebook.json` | Bombers' Notebook | 20 people |
| `stray_fairies.json` | Stray Fairies | Clock Town 1, Woodfall 15, Snowhead 15, Great Bay 15, Stone Tower 15, Great Fairies 6 |
| `spider_houses.json` | Spider Houses | Swamp 30, Oceanside 30, Rewards 2 |
| `songs.json` | Songs | Songs 10, Other songs 2 |
| `items.json` | Items & Equipment | Items 13, Equipment & Upgrades 6 rows (12 steps), Bottles 6 |
| `world.json` | Owls, Maps & Bank | Owl Statues 10, Tingle's Maps 6, Bank 3 |

Cross-references (`requires`), all checked by the test:
- The Fierce Deity's Mask requires exactly the 20 masks of the `masks` section, the ones the Moon
  Children take.
- *Defeat Majora's Mask* requires the four temples.
- Each temple's Great Fairy requires exactly that temple's 15 fairies; both Clock Town rewards
  (magic as a Deku, the Great Fairy's Mask as human) require the Clock Town fairy.
- Each Spider House reward requires its house's 30 tokens.
- The Title Deed chain: Clock Tower ledge → Swamp Tourist Center roof → Goron Village ledge → Zora
  Hall ledge → Ikana Canyon ledge, plus the Business Scrub's hole after the first trade.

Some things appear in two trackers because a completionist looks for them in both places: the bank's
Piece of Heart (Heart Pieces and Bank), the Great Fairy rewards (Stray Fairies, and Magic and the
Great Fairy's Sword in Items & Equipment), and masks given by notebook people. Each tracker counts
its own.

## What's tracked, and why

- **Main Quest** is short because almost nothing in the story persists: the temples' Remains are
  the milestones, and each temple item names what it takes to get in (masks, songs, Hookshot,
  Elegy). The Giant each boss frees is in its reward.
- **Masks**: 24, which Zelda Wiki, Zelda Dungeon and StrategyWiki agree on. The 20 non-transformation
  masks are their own section because that's the Moon Children's requirement.
- **Heart Pieces**: 52 pieces and 4 containers (Zelda Wiki: "There are 52 Pieces of Heart in
  Majora's Mask"; Zelda Dungeon and StrategyWiki list the same 52 for the N64). 3 + 13 + 4 = 20
  hearts.
- **Bombers' Notebook**: the N64 notebook's 20 entries in their in-game order (Zelda Wiki's
  notebook table). One row per person, ticked when their Happiness Seal is earned; each row's text
  has every event with its day and time and the rewards. Rewards are tracked per item in the other
  trackers. The 3DS notebook has more than 50 entries and an event list; that's noted on the Bombers
  row, not tracked.
- **Stray Fairies**: 1 + 4 × 15, and the Great Fairy rewards. N64 rewards: Clock Town gives magic
  (as a Deku) then the Great Fairy's Mask (as human), Woodfall gives the Great Spin Attack, Snowhead
  double magic, Great Bay double defence and Ikana the Great Fairy's Sword.
- **Spider Houses**: 30 + 30 tokens. Swamp: Mask of Truth. Oceanside: the Giant's Wallet if finished
  on Day 1.
- **Songs**: the ten on the Quest Status screen, plus the Inverted Song of Time and the Song of
  Double Time, which work once you know them but are never listed. The Scarecrow's Song isn't
  tracked: you have to teach it to Pierre again every cycle (Zelda Wiki), so it's never permanent.
  The two Pieces of Heart that need it say so.
- **Items & Equipment**: the item screen's items (except the Ocarina, which is Main Quest, and the
  masks and Bottles), the upgrades as tiered rows, and the six Bottles (Zelda Wiki: six in MM, seven
  in MM3D).
- **Owls, Maps & Bank**: the ten Owl Statues and Tingle's six maps persist and are commonly
  checked off. The bank's three rewards are 200 Rupees (Adult Wallet), 1,000 (a Blue Rupee of
  interest) and 5,000 (Piece of Heart).

Not tracked: Gossip Stones, the Keaton quiz answers, Rupee-only minigames (lottery, Zora Cape pots),
the Deku Scrub trade items themselves, and the ending's scenes.

## Sources

| Key | Source | Used for |
|---|---|---|
| ZW | Zelda Wiki (zeldawiki.wiki), through its MediaWiki API: *Mask*, *Piece of Heart*, *Bombers' Notebook*, *Bottle*, *Stray Fairy*, *Great Fairy*, *Clock Town Bank*, the song pages, *Swamp* and *Town Shooting Gallery*, *Big* and *Biggest Bomb Bag*, *Razor* and *Gilded Sword*, *Giant's Wallet*, *Magic Meter*, *Owl Statue*, *Swamp* and *Oceanside Spider House*, *Gold Skulltula* and more | Counts, rewards, version differences, conditions |
| ZD | Zelda Dungeon: its *Majora's Mask Heart Pieces* guide (covers N64 and 3DS), *Bombers' Notebook*, *Majora's Mask Stray Fairies* and *Skulltula Houses* wiki pages, and walkthrough chapters, read through the Wayback Machine | The Heart Piece list, event times, fairy and token lists, walkthrough order |
| SW | StrategyWiki's *The Legend of Zelda: Majora's Mask* guide: *Heart Pieces*, *Masks*, *Songs*, *Items and Equipment*, the four temples, *Skulltula houses*, *The Bomber's Notebook* | The second list for Heart Pieces, fairies and tokens |
| ZP | Zelda's Palace, *Majora's Mask: Bombers' Notebook* (Wayback) | Notebook events and hours |
| FD | Zelda Fandom, through its API | A weak check only, since it mostly mirrors Zelda Wiki |

GameFAQs and Zelda Dungeon's live pages are behind a Cloudflare check that blocks scripted reads.
StrategyWiki stands in for the GameFAQs FAQ as the second full guide.

Per list:
- **Heart Pieces**: ZD and SW list the same 52 N64 pieces, one-to-one. Conditions are from ZW where it
  has them (shooting gallery scores, the Room Key, the Curiosity Shop's hours).
- **Masks**: ZW's mask table, checked against SW's *Masks* page and the ZD walkthrough.
- **Bombers' Notebook**: names and order from ZW; events and hours from ZD and ZP.
- **Stray Fairies and Spider Houses**: ZD's N64 fairy guide and its walkthrough, against SW's temple
  and Skulltula house pages; every fairy and token matches one-to-one. ZW's temple pages only list
  chests, so ZW is used here for the rewards and the rules. Per room, the Swamp Spider House has
  main room 8, west 5, east 7, golden room 4 and tree room 6, and Oceanside has entrance 3, main
  room 9, library 7, storage 5 and dining room 6. ZD and SW agree room by room.
- **Songs, items, owls, maps, bank**: ZW, with the ZD walkthrough for Tingle's prices (Clock Town
  5 Rupees, Woodfall and Romani Ranch 20 from their own Tingle).

## Disagreements

- **Swamp Shooting Gallery.** SW gives 2,120 points for the Piece of Heart. ZW's prize table and ZD
  give 2,120 (a perfect run) for the quiver and 2,180 for the Piece of Heart afterwards; we use that.
- **Anju's Grandmother.** SW gives the first story's answer as "On the Eve of the Carnival"; ZD
  says pick the top answers. Same answer; the row gives SW's.
- **Times.** Guides round differently: the Pendant for Anju is "4:10–9 p.m." (ZD) or "5–8 p.m."
  (ZP), and Kafei's Mask meeting "2:18–7:50 p.m." (ZD) or "3–8 p.m." (ZP). Rows give the plain
  window ("Day 2 afternoon or evening") rather than a minute.
- **Curiosity Shop back room on the Final Day.** ZW says it unlocks at 1 p.m.; ZD gives 6 a.m.–10
  p.m. The Keaton Mask row cites ZW.
- **The Big Bomb Bag at the Curiosity Shop** if Sakon gets away: ZW's *Big Bomb Bag* page says the
  Final Day for 100 Rupees, its *Bomb Bag* page "the night of the Final Day". The shop only opens at
  night, so the row says the Final Night.
- **Oceanside Spider House on Days 2 and 3 (N64).** ZW and FD: Day 1 gives the Giant's Wallet (a
  Silver Rupee if you have it), Day 2 a Purple Rupee, Day 3 a Red Rupee. SW only says "20 rupees"; ZD
  says no prize after Day 1. We follow ZW.
- **Woodfall Temple, 3DS.** Three fairies differ (a beehive moved, the bubble under the bridge
  replaced by a floating fairy, one beehive popped with a bubble instead of an arrow), in the items'
  notes. Snowhead's second fairy is elsewhere in 3DS too.
- **Great Bay fairies in the waterwheel room.** ZD reaches one chest from a torch in a hidden alcove
  and one from the frozen wheel's blade; SW has both from the wheel. We follow ZD (two texts agree,
  and it's more detailed).
- **Stone Tower's underwater switch.** ZD's walkthrough says it makes a small-key chest; ZD's fairy
  guide and SW say a Stray Fairy chest. We follow the fairy guide; the walkthrough gets its small key
  elsewhere in the same room.
- **Oceanside's big jars.** ZD says roll into them, SW says punch them as a Goron; the rows say
  either.
- **Tatl's colour** over a hidden scarecrow: ZD says green, ZW says blue. The rows just say she
  changes colour.

## Majora's Mask 3D differences in the notes

- Koume's boat archery gives a Bottle instead of a Piece of Heart, and Dampé's chest on the Final
  Night a Piece of Heart instead of a Bottle. 3DS adds a seventh Bottle (Gorman's Mystery Milk).
- The Woodfall and Snowhead Great Fairies' rewards are swapped.
- Shiro (Stone Mask) is in the Pirates' Fortress, not on the Road to Ikana.
- The Giant's Mask drops from the first Twinmold, not an Eyegore.
- The Song of Soaring is learned at the swamp's entrance; the Inverted Song of Time slows time to half
  speed rather than a third; the Song of Double Time picks an hour.
- The Zora trial on the Moon takes a different path, and the Oceanside wallet works on any day.
- The Bank is in South Clock Town; the Bombers' Notebook comes from the Happy Mask Salesman and
  has more than 50 entries.
- Fishing holes are new in 3DS and aren't tracked.
