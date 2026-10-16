# The Minish Cap Pieces of Heart and Heart Containers

`shared/src/commonMain/composeResources/files/mc/heart_pieces.json` backs The Minish Cap
*Heart Pieces* tracker (`HeartCatalog`). `McHeartDatasetTest` checks it.

Nothing is fetched at runtime. The JSON was compiled by hand from the guides below. The
scripts that joined and checked them aren't committed, as with the TotK shrines.

## Counts

- **44 Pieces of Heart**, which make 11 hearts. Zelda Wiki, Zelda Dungeon, Zelda Universe and Zelda's
  Palace all list 44, and agree on how many are in each region once Zelda Dungeon's two mislabels
  are fixed (see [Disagreements](#disagreements)).
- **6 Heart Containers.** Five are dropped by bosses:
  - Big Green ChuChu, in Deepwood Shrine
  - Gleerok, in the Cave of Flames
  - Mazaal, in the Fortress of Winds
  - Big Octorok, in the Temple of Droplets
  - the Gyorg Pair, in the Palace of Winds

  The sixth is **Librari's**. After Hyrule Castle turns dark, he hides on the island in the middle of
  Lake Hylia. A Kinstone fusion with the Forest Picori in the far north-east house of Minish Village
  opens a crack by the Lake Hylia Wind Crest, and the tunnel inside leads to him. Vaati gives no
  Heart Container, and neither does anything else in Dark Hyrule Castle.
- Link starts with **3 hearts**, so a full file has 3 + 6 + 11 = **20 hearts**. Zelda Wiki and Zelda
  Dungeon both give 20 as the maximum.

## Fields

```json
{"id": "veil_falls_gale_waterfall", "kind": "piece", "name": "Waterfall opened by Gale fusion",
 "region": "Veil Falls", "area": "Cave behind the waterfall near the top of Veil Falls",
 "howTo": "…", "kinstone": true, "fusionNote": "Gale in the Cloud Tops.", "items": ["Flippers"]}
```

| Field | Notes |
|---|---|
| `id` | Stable snake_case id; progress is stored under it, so don't rename one. |
| `kind` | `piece` or `container`. |
| `region` | Zelda Wiki / in-game map names; see [Regions](#regions). |
| `area` | The exact spot: a cave, a house, a minigame or a dungeon room. |
| `howTo` | Written for this app from the guides. |
| `kinstone`, `fusionNote` | True when a Kinstone fusion opens the way. `fusionNote` says which partner, if the guides agree. Partners and stages come from Zelda Wiki's Kinstone Fusion table. |
| `minish` | Link has to be Minish-sized at the spot. That covers all of Deepwood Shrine and Minish Village, and the Big Octorok and Mazaal fights. |
| `items` | Items you use at the spot, with the game's names. Story unlocks that you don't use there (the Kinstone Bag, the Spare Key) are left out. Sword splitting is written as `White Sword (2 elements)`, `White Sword (3 elements)` and `Four Sword` (four copies). |
| `note` | Kept only where it changes what you do, e.g. when guides name different items. |

### Regions

There are 19 regions, in rough story order. Four dungeons have a Piece of Heart inside, so each
is its own region, and its boss container is listed there: Deepwood Shrine, the Cave of Flames, the
Fortress of Winds and the Palace of Winds. The Temple of Droplets has no piece, so its Big Octorok
container is under **Lake Hylia**, with the temple named in `area`.

"Hyrule Castle Garden" is used rather than "Hyrule Castle", because all four sources call it that.
Melari's Mine and the Cloud Tops have no entries of their own:
- Melari's fusion grows a beanstalk on Mount Crenel.
- Gale's fusion opens a cave in Veil Falls.

## Sources

| Source | URL | Notes |
|---|---|---|
| Zelda Wiki | https://zeldawiki.wiki/wiki/Piece_of_Heart, `/Heart_Container`, `/Kinstone_Fusion`, the boss and NPC pages | Read through the MediaWiki API (`api.php?action=parse&prop=wikitext`); the rendered pages return a Cloudflare 403. It is the primary source for the counts, fusion partners and stages, and the boss strategies. |
| Zelda Dungeon Wiki | https://www.zeldadungeon.net/wiki/The_Minish_Cap_Heart_Pieces | The live site returns a 403, so this used the Wayback snapshot from 2026-09-13. It is a numbered list #1–#44, plus "a single, special, full Heart Container" from Librari. |
| Zelda Universe | https://zeldauniverse.net/guides/the-minish-cap/sidequests/where-to-find-every-piece-of-heart-in-the-minish-cap/ | All 44 by region, plus Librari's container. |
| Zelda's Palace | https://www.zeldaspalace.com/theminishcap/pieces_of_heart.php | All 44 by region, plus Librari's container. It numbers the fusions. |

Every entry is in at least two of these, and all 44 pieces are in all four. Zelda Fandom has the
same table as Zelda Wiki, since it was the wiki before the fork, so it wasn't counted as a separate
source. StrategyWiki's page is an unfinished stub, and GameFAQs could not be reached.

The boss strategies were checked against Zelda Wiki's boss pages:
- **Mazaal:** shoot arrows at its hands, then go into its head at Minish size. Later rounds need the
  Mole Mitts.
- **Big Octorok:** a Minish-sized fight. Deflect its rocks, then light the flower on its back with the
  Flame Lantern.
- **Gyorg Pair:** jump between the two with Roc's Cape and split with the White Sword.

## Disagreements

Where the guides differ, the majority is used. If what they disagree on changes what you do, the
entry's `note` says so.

- **Hyrule Town fountain piece.** Zelda Wiki and Zelda Dungeon say to swim with the Flippers.
  Zelda's Palace says to glide west from the footbridge with Roc's Cape. Zelda Universe lists both.
  The entry lists the Flippers, and its note mentions the cape.
- **Minish Woods piece past the Witch's Hut.** Zelda Dungeon and Zelda Universe say the Cane of
  Pacci, and Zelda Dungeon adds that you can swim in with the Flippers instead. Zelda's Palace says
  the Spare Key. The entry lists the Cane of Pacci, which you will have by the time this part of the
  woods opens.
- **Castor Wilds lily cave fusion.** Zelda Wiki lists three Stage 3 fusions that each add a lily, with
  a Forest Picori in Minish Village or with Zill in Hyrule Town. Each other guide names a different
  Minish. The fusion note gives both of Zelda Wiki's partners.
- **Hyrule Castle Garden pool.** Zelda Wiki and Zelda Dungeon say a random fusion drains it. Zelda
  Universe says the Hurdy-Gurdy Man's second fusion. Random is used, and the note mentions the
  other.
- **Lake Hylia beanstalk fusion.** Zelda Wiki and Zelda Dungeon say the Forest Picori on the lake's
  east edge. Zelda Universe says a Minish in Minish Village, which looks mixed up with the Veil Falls
  shoal fusion.
- **Librari.** Zelda Universe says Librari gives the container after every fusion is done. The other
  three tie it to the one Minish Village fusion that comes right after the Veil Falls shoal fusion.
  Zelda Wiki's Librari article calls it the Picori's 2nd fusion, while Zelda Dungeon counts it as the
  3rd.
- **Zelda Dungeon region labels.** Zelda Dungeon files two pieces under "Veil Falls": #43, the Funday
  School yard, and #44, the house you open after collecting 130 figurines. Both descriptions are of
  Hyrule Town, and every other source puts them there.
- **Missable pieces.** No source says any piece or container can be missed for good, so the dataset
  has no missable flag.
