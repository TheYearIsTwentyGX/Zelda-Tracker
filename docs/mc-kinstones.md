# Minish Cap Kinstone Fusions

`shared/src/commonMain/composeResources/files/mc/kinstones.json` backs The Minish Cap's *Kinstone
Fusions* tracker (`KinstoneCatalog`). `McKinstoneDatasetTest` checks it.

Nothing is fetched at runtime. The JSON was built offline from two independent research passes,
joined record by record by scripts that aren't committed (as with the TotK shrines). The text of
every location and result was then written for this app from those sources.

## Sources

| Source | What it gave |
|---|---|
| **Zelda Wiki**: [Kinstone Fusion](https://zeldawiki.wiki/wiki/Kinstone_Fusion) (the 100-row table), the Green/Blue/Red/Gold Kinstone pages, and about 70 character and place pages | Partner, area, piece, story stage (1–6) and result for all 100; prerequisites, partners that move or arrive late, the random-fuser rules. Read as raw wikitext through the wiki's API, since the site blocks plain fetches. |
| **GameFAQs**: [Kinstone Fusion Guide v1.15 by silktail](https://gamefaqs.gamespot.com/gba/920670-the-legend-of-zelda-the-minish-cap/faqs/34507) (2005, written on the European version) | All 100, grouped by where the result appears; rupee amounts; random, shared and "fickle" fusers; the Goron Cave order rule; missables, including the European Eenie bug. |
| **Thonky**: [Kinstone Fusion Guide](https://www.thonky.com/zelda-minish-cap/kinstone-fusion) | All 100, numbered 001–100 in story order: the `number` field. Also the shape names (`shape`). |
| **Zelda Dungeon**: [walkthrough](https://www.zeldadungeon.net/the-minish-cap-walkthrough/), chapters 1–6, and its Heart Piece list | A partial check. It has no fusion list, but its walkthrough covers every red, blue and Gold fusion and the fixed green ones it passes, about 57 of the 100. |
| **zeldaret/tmc** decompilation (`gKinstoneWorldEvents`, `SharedFusions`) | The game's own ids 0x01–0x64 (`gameId`), colour and shape per id, and the exact 18 shared fusions. Used only to settle disagreements. |

Zelda Universe and ZeldaSpeedRuns' 100% route were read for side notes only.

## Counts

All four complete sources agree on these:

- **100 fusions:** 49 green, 18 blue, 24 red and 9 Gold.
- **18 random fusions** (Thonky #4–19, #21, #22): 16 green, plus the blue North Hyrule Field fairy tree and
  the red Hyrule Castle Garden Piece of Heart.
  - Any random fuser offers one at random from those not yet done. That's most townsfolk, animals and
    Minish, plus the Hurdy-Gurdy Man after his tutorial.
  - A fuser who leaves the story hands theirs on, so these can't be missed.
  - They have no fixed partner, so they're listed by result under a **Random fusers** heading.
- **82 fixed fusions.** Each partner works through their own list, and Link has to leave and re-enter
  between fusions.
- **Partners:** 86 characters and 14 objects. The objects are 3 Mysterious Statues, 5 Mysterious
  Clouds, the Source of the Flow and 5 Mysterious Walls.
- **9 Gold fusions:**
  - The **three statues** in south-west Castor Wilds. Together they break the boulder in front of
    the Wind Ruins.
  - The **Source of the Flow** at Veil Falls. Its piece comes from Gustaf in the Royal Crypt, and it
    opens the way up to Veil Springs.
  - The **five clouds** in the Cloud Tops. Together their pinwheels raise the tornado.
  - These and the Hurdy-Gurdy tutorial are the 10 fusions the story forces.
- **Hearts:** 9 Pieces of Heart and Librari's Heart Container. The second Castor Wilds lily pad's
  Piece of Heart is counted (see below).
- **9 Golden monsters:** 3 Octoroks, 3 Ropes and 3 Tektites.

## Fields

```json
{"id": "mayor_hagen", "number": 2, "gameId": "0x1E", "partner": "Mayor Hagen", "region": "Hyrule Town",
 "location": "His house in the north-east of town", "color": "red", "shape": "long teeth",
 "result": "A puddle in the Lon Lon Ranch pasture dries up, …", "resultRegion": "Lon Lon Ranch",
 "rewards": ["item"], "stage": 1, "note": "…"}
```

| Field | Notes |
|---|---|
| `region` | Where the **partner** is, so the list reads as where to go. `resultRegion` is where the result appears. The random fusions take the pseudo-region `Random fusers`, first in `regions`. Zelda Wiki's areas are folded into regions: Melari's Mine under Mt. Crenel, Minish Village under Minish Woods, the Home of the Wind Tribe under Cloud Tops. |
| `shape` | The half Link supplies, as Thonky names it. GameFAQs' letter codes are next to each name below. Null for Gold, whose pieces only fit their own statue, cloud or slab. |
| `stage` | Zelda Wiki's story stage, the earliest point the fusion can come up; `stages` holds the label for each. |
| `rewards` | Tags for the result filter. A fusion can carry several, e.g. a beanstalk with a Piece of Heart and rupees. |
| `requires` | What has to be done first, beyond the stage. Written not to give away another fusion's result. |
| `missable` | Shown even with results hidden, so it's written without spoilers too. |

### Shapes

Zelda Wiki only shows the sprites, with descriptions; it gives the shapes no names. Every
non-Goron fusion's shape agrees across Zelda Wiki, Thonky and the decomp once the names are matched:

| Colour | Thonky | GameFAQs | Zelda Wiki's sprite description |
|---|---|---|---|
| Green | triangle wedge | G< | triangular divot |
| Green | square tooth | G[ | square divot |
| Green | slant | GP | tapered bottom |
| Blue | squarish | BL | L-shaped |
| Blue | S-curve | B6 | droplet-shaped |
| Red | jagged teeth | R3 | jagged interior |
| Red | long teeth | RE | wavy interior |
| Red | triangle wedge | R> | sector-shaped |

**The Goron Cave six** (Eenie and the five Mysterious Walls) are `squarish or S-curve`. GameFAQs
says the piece goes by how many have been done, BL, B6, BL, B6, BL, B6, whichever partner is used,
and the decomp's six Goron ids follow that pattern. So the per-wall shapes in Zelda Wiki and Thonky
are just the order their authors did them in; the two disagree on the Mt. Crenel and Trilby walls.

## Missable and time-limited

- **Eenie, European version:** the only fusion that can be lost for good. Starting his fusion,
  cancelling and leaving makes him never offer it again. Zelda Wiki, GameFAQs and Zelda Universe
  all report this. That also loses the rest of the Goron Cave and Biggoron's Mirror Shield.
- **Strato:** the fusion can't be missed, but the Gregal side quest it leads to is lost if Link
  reaches the Home of the Wind Tribe through the Cloud Tops first (GameFAQs).
- **Librari:** Zelda Wiki doesn't say what happens when the Library closes late in the story.
  GameFAQs and Thonky say he can then be fused with at his Lake Hylia home, which the #100 fusion
  opens. So he isn't marked missable; a note says so.
- **Tina** only sometimes wants to fuse. The tracker notes it; GameFAQs and ZSR call her the usual
  last fusion.
- **Biggoron's** Mirror Shield only comes after the game is beaten and saved.
- **Partners that move or arrive late** (Candy, Zill, Melari, the Postman, Bremor and the Wind
  Tribe) say so in `location` or `requires`.

## Discrepancies and how they were settled

| What | Sources | Used |
|---|---|---|
| Ghost brothers | Zelda Wiki: fuse with **Spekter** (Royal Valley), and **Spookter** leaves Anju's house. Zelda Dungeon swaps the names. | Zelda Wiki; noted on the fusion. |
| Strato | Zelda Wiki calls him Strato; the guides say "Stranger" (his house's name). | Strato. |
| Gorman (#45) | Farore (Zelda Wiki, Thonky, Zelda Dungeon) vs any Oracle (GameFAQs). | Farore; noted. |
| Mt. Crenel Golden Tektite (#20) | Thonky: random. Zelda Wiki, GameFAQs and the decomp: the kitchen Mountain Minish's own fusion. | Fixed. |
| North Hyrule Field fairy tree (#21) | Zelda Dungeon says every blue fusion is fixed. The decomp, GameFAQs and Zelda Wiki list it as random. | Random. |
| Trilby Forest Minish (#60) | Zelda Dungeon: random. The others: fixed. | Fixed. |
| Second lily pad (#50) | Zelda Wiki: just a lily. GameFAQs and Zelda Dungeon's Heart Piece list: it leads to an underwater cave with a Piece of Heart. | Counted as a heart, with a note. |
| Lily pad partners | Zelda Wiki: a Minish Village Picori or Zill. GameFAQs adds the Town Minish in the new house. | All three named. |
| Candy | Inn upstairs (Zelda Wiki, Thonky) vs downstairs (GameFAQs). | Upstairs. |
| Hailey, Gale | In the open Cloud Tops (Thonky, Zelda Dungeon) vs in the Wind Tribe tower (Zelda Wiki, GameFAQs). | Both, "sources differ". |
| Tingle's Golden Tektite | 200 Rupees (GameFAQs, Zelda Dungeon). Other golden monsters: 100 (GameFAQs only). | 200 is in Tingle's result; the others give no amount. |
| Rupee amounts Thonky leaves blank | #57 320 (GameFAQs, Zelda Dungeon), #60 75, #62 400, #81 300 (GameFAQs, and Zelda Wiki's main table for #62 and #81). | As given. |
| Cloud and statue numbering | Thonky gives the 3 statues and the 5 clouds one entry each. | Split by the decomp's left/centre/right and cloud-position names. Which of #41–43 and #86–90 is which is arbitrary. |
| GameFAQs typos | Its main list has wrong pieces for #1, #70, #73 and #100. Its own fuser list, Thonky and the decomp agree on the right ones. | The corrected pieces. |

## Grouping and order

Regions run from Hyrule Town outwards, roughly in the order the story opens them up. Random
fusers come first. Within a region, fusions are sorted by `stage`, then by Thonky's number, so a
partner's fusions come in the order they're offered.
