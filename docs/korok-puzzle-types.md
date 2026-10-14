# BotW Korok Seeds: puzzle types and master list

Two datasets back the Breath of the Wild Korok Seed feature:

- `shared/src/commonMain/composeResources/files/botw/koroks.json`: all 900 seeds, with position, tower region,
  a location hint and puzzle type.
- `shared/src/commonMain/composeResources/files/botw/korok_puzzle_types.json`: the 29 puzzle types seeds are
  sorted into, with player-facing names, descriptions and the rules that tell them apart.

`BotwKorokDatasetTest` checks both files.

## How many seeds

BotW has **900 Korok Seeds, one per Korok puzzle**, and that is the total the game counts toward Hestu's reward. Hestu
gives no seed of his own. Returning all 900 earns *Hestu's Gift*, an item (golden poop) and not a seed, so it isn't in
the list. objmap, Zelda Wiki and Game8 all count 900. (TotK is the game with 1,000 seeds, because its "Traveling
Korok Duo" puzzles give two seeds each.)

## Sources

- **objmap.zeldamods.org** (`korok_ids.json` in the [objmap repo](https://github.com/zeldamods/objmap)): the 900
  Koroks, each with objmap's id (`A01`…`Z62`), the game's `HashId` for the hidden-Korok actor, its map section and
  its world position.
- **radar.zeldamods.org** (`/obj/MainField/<section>/<hashId>` and `/gen_group`), objmap's backend: each Korok's
  `korok_type` and the actors in its puzzle group. radar derives `korok_type` in
  [`build.ts` → `korokGetType`](https://github.com/zeldamods/radar/blob/master/build.ts) from which actors are in the
  hidden Korok's generation group, so it is a datamined classification and not a label the game ships. This
  doc calls it the **internal type**.
- **objmap `game_files/ecosystem/MapTower.json`**: the game's own tower-region polygons, used to check regions.
- **objmap `map_summary/MainField/static.json`** plus the `LocationMarker` text: named places for location hints.
- **Zelda Wiki** (Korok Seed Puzzle, Circle, Natural Hiding Spot, Leaves with Fairy Dust, Offering Spot, Flower Trail,
  Horse Hurdle, Tree Pattern…) for the player-facing names and descriptions and to cross-check counts. Zelda Fandom
  and Game8 refused automated requests (HTTP 402/404), so they weren't used directly.

Nothing is fetched at runtime; the JSON was built offline by scripts that are not committed.

## `koroks.json`

```json
{"id": "P01", "hashId": 20346760, "mapSection": "E-6", "region": "Great Plateau",
 "x": -822.64, "y": 171.23, "z": 1546.92, "nearestPlace": "Oman Au Shrine",
 "internalType": "Rock Lift", "actors": ["Obj_LiftRockWhite_A_01"],
 "puzzleType": "lift_rock", "typeSource": "internal"}
```

| Field | Meaning |
|---|---|
| `id` | objmap's Korok id, kept as the stable id. The letter is the tower region (`A` Akkala, `C` Central, `D` Dueling Peaks, `E` Eldin, `F` Faron, `G` Gerudo, `H` Hebra, `K` Woodland, `L` Lake, `N` Hateno, `P` Great Plateau, `R` Ridgeland, `T` Tabantha, `W` Wasteland, `X` Hyrule Castle, `Z` Lanayru); the number has no meaning beyond uniqueness. |
| `hashId` | The game's `HashId` for the `Npc_HiddenKorokGround`/`Npc_HiddenKorokFly` actor: the game's own identifier. Unique across the 900. |
| `mapSection` | Map section (`A-1`…`J-8`) whose map file holds the actor. It is usually the 1000×1000 square the seed is in, but 35 seeds belong to a neighbouring section, up to about 200 units past its edge, so don't derive it from x/z. With `hashId` it gives the radar URL: `https://radar.zeldamods.org/obj/MainField/<mapSection>/<hashId>/gen_group`. |
| `region` | One of the 15 Sheikah Tower regions, spelled as in `shrines.json` (`Great Plateau`, `Dueling Peaks`, `Hateno`, `Lanayru`, `Faron`, `Lake`, `Central`, `Ridgeland`, `Tabantha`, `Hebra`, `Woodland`, `Eldin`, `Akkala`, `Wasteland`, `Gerudo`). |
| `x`, `y`, `z` | World position (see below). |
| `nearestPlace` | Location hint: the closest named map location, shrine, tower or settlement, measured flat on the map. Inside Hyrule Castle it is the castle room name from radar instead (`Lockup`, `Zelda's Room`…). It names what is closest, not what the puzzle is "at", so it may name a shrine or a place across a river. |
| `internalType` | radar's `korok_type` (33 values; see the report). For seeds of type `other` it is the only name that says what the puzzle is (e.g. `Take the Stick`), so the UI can show it as a subtitle. |
| `actors` | The puzzle's game actors, without the Korok itself and the logic tags (`Area`, `LinkTag*`, `ActorObserverTag`…). Evidence for classifying; e.g. `WallCrack` means a bombable wall. |
| `puzzleType` | A type id from `korok_puzzle_types.json`. Never null: every seed's type comes from its objmap `korok_type` (see [Puzzle types](#puzzle-types)). |
| `typeSource` | `"internal"` on every seed: the type comes from objmap's datamined `korok_type`. A later override from another source should use a different value. |

Besides `koroks`, the file has `regions` (tower order) and `hestu`: each pouch Hestu upgrades, its starting slots and
the seed cost of each upgrade in turn (weapons 208, bows 73, shields 160: 441 in all, from Zelda Wiki's Korok Seed
page). The Korok tracker's Hestu card reads it.

The file is ordered by region (tower order, as in `shrines.json`) then id.

### Coordinates

`x`, `y`, `z` are objmap's coordinates, which are the game's own world units:

- **x** grows to the east and **z** grows to the south, so north on the in-game map is −z. **y** is height.
- The origin is the centre of the map. The playable map spans **x −5000…5000** and **z −4000…4000**: the map is
  divided into sections `A`–`J` across by rows `1`–`8` down, 1000 units each, with `A-1` in the north-west corner.
- objmap's "Go to coordinates" box takes the same numbers, so any seed can be checked there.

To match a screenshot of the in-game map, a point maps linearly: `u = (x + 5000) / 10000`, `v = (z + 4000) / 8000`
across the full map, with (0, 0) at the top-left. Calibrate the screenshot to the map's corners or to two known
landmarks, such as tower positions from `static.json`.

### Regions

Each seed's region comes from objmap's id letter, with `X` (Hyrule Castle) mapped to **Central** because the castle is
inside the Central tower's region. Every seed was also checked against the game's MapTower polygons, and **899 of
900 agree**. The one exception is `L03`, a cube puzzle near Lake Hylia. It is within 1 unit of the Lake/Dueling Peaks
border, and the polygons are stored on a 2-unit grid, so the test is inconclusive there. It stays in Lake, as objmap and
the usual guide counts have it (Lake 92, Dueling Peaks 59).

## Puzzle types

**objmap is the source of truth.** Every seed's type comes from its objmap `korok_type` through the fixed mapping
below, with no judgement calls. objmap's types come from the game files, so where Zelda Wiki disagrees, objmap wins.
Our 29 types are a presentation layer on top: friendlier names, player-facing descriptions and grouping. The only
place they refine objmap is splitting `Rock Lift (Rock Pile)` by actor, and that split also comes from game data.

How the 29 types relate to radar's 33:

- **Split:** `Rock Lift (Rock Pile)` → `rock_behind_cracked_wall` when the puzzle includes a `WallCrack` (a bombable
  wall), and `rock_under_rubble` when it includes an `Obj_RockBroken_A_02` (a breakable rock pile). Every Rock Pile
  seed has exactly one of the two.
- **Merged:** `Shoot the Crest` + `Shoot the Targets` → `shoot_target`. objmap's five one-of-a-kind types (Light Torch,
  Take the Stick, Remove Luminous Stone, Burn the Leaves (Goatee), Take Apple from Palm Tree) → `other`, keeping
  objmap's name in `internalType`.
- **Renamed** to what the player sees (Zelda Wiki's names where it has them): `Rock Lift` → *Lone rock*,
  `Stationary Lights` → *Sparkle* (Zelda Wiki: "Fairy Dust (Fixed Position)"), `Cube Puzzle` → *Cube pattern*,
  `Rock Lift (Door)` → *Rock under a metal plate*, `Acorn in a Hole` → *Acorn in a hollow*, and so on.

A note on rock circles vs rock patterns: in BotW the "circle of rocks" puzzle is **not** "complete the ring". It is a
ring of boulders standing *in water*, and you throw any rock into the middle (Zelda Wiki "Circle"; 20 seeds, the
same as radar's `Circle of Rocks`). A ring of rocks on dry land with one gap is a `rock_pattern`. A lone rock with
nothing around it is `lift_rock`.

The rules under each type explain what the type means and how it differs from look-alikes. They are for describing
puzzles to players and for checking objmap's data. They don't override it.

| id | Name | Group | objmap `korok_type` | Seeds |
|---|---|---|---|---|
| `lift_rock` | Lone rock | Hidden rocks | Rock Lift | 174 |
| `rock_under_boulder` | Rock under a boulder | Hidden rocks | Rock Lift (Boulder) | 6 |
| `rock_under_slab` | Rock under a stone slab | Hidden rocks | Rock Lift (Slab) | 12 |
| `rock_under_metal_plate` | Rock under a metal plate | Hidden rocks | Rock Lift (Door) | 8 |
| `rock_under_leaves` | Rock under a pile of leaves | Hidden rocks | Rock Lift (Leaves) | 19 |
| `rock_under_rubble` | Rock under breakable rocks | Hidden rocks | Rock Lift (Rock Pile) with Obj_RockBroken_A_02 | 16 |
| `rock_behind_cracked_wall` | Rock behind a cracked wall | Hidden rocks | Rock Lift (Rock Pile) with WallCrack | 25 |
| `rock_pattern` | Rock pattern | Patterns | Rock Pattern | 73 |
| `rock_circle` | Rock circle in water | Patterns | Circle of Rocks | 20 |
| `cube_pattern` | Cube pattern | Patterns | Cube Puzzle | 66 |
| `boulder_in_hole` | Boulder into a hole | Patterns | Roll a Boulder | 31 |
| `ball_and_chain` | Ball and chain | Patterns | Ball and Chain | 16 |
| `offering` | Offering | Patterns | Offering Plate | 28 |
| `matching_trees` | Matching trees | Patterns | Matching Trees | 20 |
| `sparkle` | Sparkle | Sparkles | Stationary Lights | 51 |
| `moving_sparkle` | Moving sparkle | Sparkles | Moving Lights | 39 |
| `melt_ice` | Melt the ice block | Sparkles | Melt Ice Block | 18 |
| `flower_trail` | Flower trail | Flowers | Flower Trail | 46 |
| `flower_order` | Flower groups in order | Flowers | Flower Order | 11 |
| `balloon` | Hidden balloon | Archery | Stationary Balloon | 26 |
| `pinwheel_balloons` | Pinwheel balloons | Archery | Pinwheel Balloons | 44 |
| `pinwheel_acorns` | Pinwheel acorns | Archery | Pinwheel Acorns | 10 |
| `acorn_in_hollow` | Acorn in a hollow | Archery | Acorn in a Hole | 29 |
| `hanging_acorn` | Hanging acorn | Archery | Hanging Acorn | 14 |
| `shoot_target` | Shoot the target | Archery | Shoot the Crest, Shoot the Targets | 5 |
| `race` | Race to the ring | Movement | Goal Ring (Race) | 51 |
| `dive` | Dive through the ring | Movement | Dive | 35 |
| `horse_hurdles` | Horse hurdles | Movement | Jump the Fences | 2 |
| `other` | Other | Other | Light Torch, Take the Stick, Remove Luminous Stone, Burn the Leaves (Goatee), Take Apple from Palm Tree | 5 |

### Hidden rocks

#### `lift_rock`: Lone rock

A single small grey rock sits somewhere it doesn't belong: on a peak, in a tree, on a ledge, under a bridge, at the end of a tunnel. Pick it up (or knock it off with Stasis, a bomb or Magnesis-carried metal) and the Korok is underneath.

- Counts: one liftable rock on its own, with nothing covering it and no other rocks arranged around it. The hard part is getting to it, not uncovering it.
- Not this if the rock is under a boulder, slab, metal plate, leaf pile, breakable rubble or behind a cracked wall: use the matching rock_under_* / rock_behind_cracked_wall type.
- Not this if other rocks form a shape with a gap the rock belongs in: that is rock_pattern.
- Not this if you throw a rock into a ring of rocks standing in water: that is rock_circle.

Examples: P17 (Great Plateau, near Mount Hylia); D51 (Dueling Peaks, near Dueling Peaks Stable).

#### `rock_under_boulder`: Rock under a boulder

A large round boulder sits in a small dip or hole. Move the boulder (Stasis and hit it, an Octo Balloon, a bomb, or just push it) and lift the rock that was hidden beneath.

- Counts: a pushable boulder covering a liftable rock; the seed comes from lifting the rock.
- Not this if the boulder itself has to go into a hole: that is boulder_in_hole.
- Not this if the cover is a flat stone slab (rock_under_slab) or breakable rocks (rock_under_rubble).

Examples: D22 (Dueling Peaks, near Owlan Bridge); A03 (Akkala, near Akkala Wilds).

#### `rock_under_slab`: Rock under a stone slab

A large flat stone slab lies over a hollow, often on top of a pillar or ruin. Knock it off with Stasis (freeze, then hit) or lift it with Octo Balloons, then pick up the rock beneath.

- Counts: a flat stone cover (game actor Obj_RockCover) over a liftable rock.
- Not this if the cover is metal (rock_under_metal_plate), round (rock_under_boulder) or can be smashed (rock_under_rubble).

Examples: P16 (Great Plateau, near Mount Hylia); L14 (Lake, near Bridge of Hylia).

#### `rock_under_metal_plate`: Rock under a metal plate

A rusty iron plate or hatch covers a small pit. Pull it away with Magnesis and lift the rock inside.

- Counts: a metal board (Obj_BoardIron) covering a liftable rock; Magnesis is the intended tool.
- Not this if a metal ball on a chain has to be put somewhere (ball_and_chain) or metal crates form a pattern (rock_pattern).

Examples: P12 (Great Plateau, near Ja Baij Shrine); P13 (Great Plateau, near Ja Baij Shrine).

#### `rock_under_leaves`: Rock under a pile of leaves

A heap of leaves, usually in a tree hollow or a corner, hides a rock. Burn the leaves (or blow them away with a Korok Leaf or bomb) and lift the rock.

- Counts: burnable leaves (Obj_Plant_IvyBurn) on top of a liftable rock.
- Not this if burning leaves reveals the Korok directly with no rock to lift: objmap calls that one Burn the Leaves (Goatee), and it is other.

Examples: C76 (Central, near Kolomo Garrison Ruins).

#### `rock_under_rubble`: Rock under breakable rocks

A pile of cracked, breakable boulders hides a rock. Smash them with a bomb or a blunt weapon, then lift the rock.

- Counts: a breakable rock pile (Obj_RockBroken) covering a liftable rock.
- Not this if the rock is behind a cracked cliff face or cave wall: that is rock_behind_cracked_wall.
- Not this for ore deposits, which never hide Koroks.

Examples: Z28 (Lanayru, near Tal Tal Peak); N27 (Hateno, near Necluda).

#### `rock_behind_cracked_wall`: Rock behind a cracked wall

A cracked patch of rock wall seals a small alcove. Blow it open with a bomb and lift the rock inside.

- Counts: a bombable cracked wall (WallCrack) in front of a liftable rock, including the one that also hides a treasure chest.
- Not this if the rocks are a loose breakable pile rather than part of a wall: that is rock_under_rubble.

Examples: F18 (Faron, near Keya Pond); L13 (Lake, near Shae Katha Shrine).

### Patterns

#### `rock_pattern`: Rock pattern

Rocks are laid out on the ground in a deliberate shape (a ring, a line, matching shapes on both sides of something) and one spot is empty. Find the spare rock nearby, usually in the direction the gap points, and put it in the gap.

- Counts: a land-based arrangement of liftable rocks with one missing; you place a rock to complete it. A ring of rocks on land with one gap is a rock_pattern.
- Also counts: the same idea with other objects, e.g. a pattern of metal crates with one missing.
- Not this if the rocks stand in water and you throw a rock into the middle of the ring: that is rock_circle.
- Not this if the shape is built from stone cubes that slot together: that is cube_pattern.
- Not this if fruit on trees is the pattern: that is matching_trees.

Examples: P15 (Great Plateau, near Mount Hylia); F36 (Faron, near Lurelin Village).

#### `rock_circle`: Rock circle in water

A ring of boulders juts out of a lake or river with open water in the middle. Throw (or Magnesis or Stasis-launch) any rock into the centre of the ring.

- Counts: a ring of rocks standing in water; the target is the water inside the ring, and any rock dropped there works.
- Not this if the ring is on dry land with one rock missing: that is rock_pattern.
- Not this if the ring is made of lily pads or floating plants and you dive through it: that is dive.

Examples: D20 (Dueling Peaks, near Kakariko Bridge); L09 (Lake, near Lake Hylia).

#### `cube_pattern`: Cube pattern

Two shapes built from mossy stone cubes stand near each other; one has a cube the other lacks, or a loose cube lies nearby. Use Magnesis to move the loose cube so the two shapes match.

- Counts: Korok stone cubes (FldObj_KorokStone) that you rearrange with Magnesis.
- Not this if the pieces are ordinary liftable rocks or metal crates: that is rock_pattern.

Examples: P14 (Great Plateau, near Keh Namut Shrine); L38 (Lake, near Lake Hylia).

#### `boulder_in_hole`: Boulder into a hole

A boulder (sometimes a snowball or a metal ball) rests near a hole or hollow. Roll, push, Stasis-launch or bomb it so it drops into the hole.

- Counts: the solution is getting a large round object into a specific hole or depression.
- Not this if the boulder hides a rock you then lift: that is rock_under_boulder.
- Not this if the object is a chained metal ball: that is ball_and_chain.

Examples: L16 (Lake, near Lake Hylia).

#### `ball_and_chain`: Ball and chain

An iron ball is chained to a post next to a stone cup or hole. Lift the ball with Magnesis and drop it into the hole.

- Counts: a chained metal ball (Obj_KorokIronRock) that has to be placed in a hole.
- Not this if the thing on the chain is an acorn you shoot: that is hanging_acorn.

Examples: P02 (Great Plateau, near Oman Au Shrine).

#### `offering`: Offering

Several small statues or plates each hold an item (usually an apple) but one is empty. Drop the same item into the empty spot.

- Counts: a set of offering spots where one is missing an item you supply, whatever the item (apple, banana, pepper, egg, rusty shield).
- Not this if you remove fruit from trees so they match: that is matching_trees.

Examples: D03 (Dueling Peaks, near Kakariko Village); D30 (Dueling Peaks, near Dueling Peaks).

#### `matching_trees`: Matching trees

Three trees of the same kind stand together, carrying fruit in the same places except one, which has extra. Knock off or pick the extra fruit so all the trees match.

- Counts: a group of fruit trees (apple, Voltfruit cactus, durian) where you remove fruit to make them identical.
- Not this if you put an item down: that is offering.

Examples: D46 (Dueling Peaks, near Deya Lake); N50 (Hateno, near Marblod Plain).

### Sparkles

#### `sparkle`: Sparkle

A small cluster of glittering leaves (fairy dust) hangs in one place, often on a summit, a flagpole, a treetop or behind a waterfall. Reach it and press A to examine it.

- Counts: a stationary sparkle you touch. The challenge is reaching it.
- Not this if the sparkle moves along a path: that is moving_sparkle.
- Not this if you first have to melt ice to free it: that is melt_ice.

Examples: P11 (Great Plateau, near Temple of Time); L27 (Lake, near Bridge of Hylia).

#### `moving_sparkle`: Moving sparkle

A sparkle flits along a set route, often looping around a hill, pond or ruin. Chase or intercept it and examine it.

- Counts: a sparkle that travels along a fixed route (it has a rail in the game data).
- Not this if it stays put (sparkle) or appears only after a race start (race).

Examples: C82 (Central, near Oman Au Shrine).

#### `melt_ice`: Melt the ice block

In a cold area, a large block of ice sits somewhere conspicuous. Melt it with fire (Fire Arrow, a torch, a fire weapon, a lit campfire) to reveal the Korok.

- Counts: a meltable ice block (IceWall) hiding the Korok.
- Not this for ice you merely have to cross or climb to reach a rock or sparkle; classify by what you finally touch.

Examples: P07 (Great Plateau, near Keh Namut Shrine).

### Flowers

#### `flower_trail`: Flower trail

A lone yellow flower vanishes when you approach and reappears a little way off. Keep following it until you touch the last (white) flower.

- Counts: one flower at a time that hops ahead of you.
- Not this if several groups of flowers are all visible at once and you touch them in order of size: that is flower_order.

Examples: F45 (Faron, near Lurelin Village); A38 (Akkala, near Tarrey Town).

#### `flower_order`: Flower groups in order

Fifteen flowers grow in five clumps of one, two, three, four and five. Touch the clumps in rising order (1, 2, 3, 4, 5).

- Counts: several flower clumps visible at once that must be touched in order of size.
- Not this if a single flower leads you along: that is flower_trail.

Examples: D43 (Dueling Peaks, near Outpost Ruins); R46 (Ridgeland, near Lake Illumeni).

### Archery

#### `balloon`: Hidden balloon

A single Korok balloon floats somewhere tucked away: under a bridge, inside a cave, behind rocks, high on a cliff. Pop it with an arrow.

- Counts: one balloon that is always there, with no pinwheel nearby.
- Not this if the balloons appear when you stand by a pinwheel: that is pinwheel_balloons.

Examples: T14 (Tabantha, near Rito Village); L15 (Lake, near Bridge of Hylia).

#### `pinwheel_balloons`: Pinwheel balloons

Stand next to a lone pinwheel and one or more balloons appear, some moving. Shoot them all before the timer runs out without leaving the pinwheel.

- Counts: a pinwheel that summons balloons.
- Not this if the pinwheel summons acorns: that is pinwheel_acorns.
- Not this if there is a pinwheel but no targets (a sparkle by a pinwheel): classify by what you touch.

Examples: L33 (Lake, near Lake Hylia); C83 (Central, near Oman Au Shrine).

#### `pinwheel_acorns`: Pinwheel acorns

Stand next to a pinwheel and a ring of acorns appears around a nearby tree or pillar. Shoot every acorn before time runs out.

- Counts: a pinwheel that summons acorns (Obj_KorokPot) on a timer.
- Not this if the acorn is always there: that is acorn_in_hollow or hanging_acorn.

Examples: N34 (Hateno, near Fort Hateno); D58 (Dueling Peaks, near Mount Rozudo).

#### `acorn_in_hollow`: Acorn in a hollow

An acorn jar is wedged into a tree hollow, a hollow log, a rock crevice or a chimney. Shoot or bomb it.

- Counts: a single acorn jar fixed in place (not hanging).
- Not this if it dangles from a chain: that is hanging_acorn. Not this if a pinwheel spawns it: that is pinwheel_acorns.

Examples: L40 (Lake, near Lake Hylia); C16 (Central, near Hyrule Castle).

#### `hanging_acorn`: Hanging acorn

An acorn jar hangs on a chain from a branch, bridge or overhang. Shoot it down.

- Counts: an acorn jar on a chain.
- Not this if the chained object is an iron ball you place: that is ball_and_chain.

Examples: C49 (Central, near Hyrule Field).

#### `shoot_target`: Shoot the target

Shoot a fixed mark that isn't a balloon or an acorn: a crest or emblem painted high on a wall or structure, or a set of archery targets.

- Counts: an arrow hit on a fixed emblem or target board triggers the Korok.
- Not this for balloons (balloon, pinwheel_balloons) or acorns (acorn_in_hollow, hanging_acorn, pinwheel_acorns).

Examples: D05 (Dueling Peaks, near Kakariko Village); N45 (Hateno, near Hateno Tower).

### Movement

#### `race`: Race to the ring

Step on a tree stump with a leaf symbol; a ring of light appears some distance away and a timer starts. Reach the ring (run, glide, shield-surf) before it disappears.

- Counts: a starting stump plus a timed goal ring.
- Not this if you dive into a ring of plants on water: that is dive.

Examples: L08 (Lake, near Lake Hylia); L47 (Lake, near Faron Grasslands).

#### `dive`: Dive through the ring

A ring of lily pads or floating plants sits on water below a ledge or platform. Jump off the ledge and drop into the water through the middle of the ring; paragliding down into it doesn't count.

- Counts: a floating plant ring you enter by diving.
- Not this if the ring is made of rocks and you throw something in: that is rock_circle.

Examples: D02 (Dueling Peaks, near Kakariko Village); L10 (Lake, near Lake Hylia).

#### `horse_hurdles`: Horse hurdles

A course of wooden fences stands in an open field. Ride a horse over every fence in sequence without missing one.

- Counts: a line of jumpable fences cleared on horseback. Only two exist.

Examples: K17 (Woodland, near Rowan Plain); D25 (Dueling Peaks, near South Nabi Lake).

### Other

#### `other`: Other

One-off puzzles that don't fit any other type, such as lighting an unlit torch, pulling out a stick, taking a Luminous Stone or burning leaves off a Korok face.

- Counts: the five seeds objmap gives a one-off type (Light Torch, Take the Stick, Remove Luminous Stone, Burn the Leaves (Goatee), Take Apple from Palm Tree). Each seed keeps objmap's name in internalType, which the UI can show as a subtitle.
- Not this for anything objmap puts in a shared type, however unusual the setting: lifting a rock, shooting a balloon or acorn, touching a sparkle, placing an item or filling a gap in a pattern all have their own types.

Examples: L26 (Lake, near Bridge of Hylia); D54 (Dueling Peaks, near Lake Hylia).

## Report

### Seeds per region

| Region | Seeds |
|---|---:|
| Central (incl. 25 in Hyrule Castle) | 114 |
| Lake | 92 |
| Ridgeland | 80 |
| Hebra | 73 |
| Wasteland | 68 |
| Hateno | 66 |
| Lanayru | 62 |
| Dueling Peaks | 59 |
| Faron | 58 |
| Akkala | 57 |
| Eldin | 45 |
| Tabantha | 37 |
| Gerudo | 36 |
| Woodland | 35 |
| Great Plateau | 18 |
| **Total** | **900** |

### Seeds per internal type

| objmap `korok_type` | Seeds | → puzzle type |
|---|---:|---|
| Rock Lift | 174 | `lift_rock` |
| Rock Pattern | 73 | `rock_pattern` |
| Cube Puzzle | 66 | `cube_pattern` |
| Stationary Lights | 51 | `sparkle` |
| Goal Ring (Race) | 51 | `race` |
| Flower Trail | 46 | `flower_trail` |
| Pinwheel Balloons | 44 | `pinwheel_balloons` |
| Rock Lift (Rock Pile) | 41 | `rock_behind_cracked_wall` (25), `rock_under_rubble` (16) |
| Moving Lights | 39 | `moving_sparkle` |
| Dive | 35 | `dive` |
| Roll a Boulder | 31 | `boulder_in_hole` |
| Acorn in a Hole | 29 | `acorn_in_hollow` |
| Offering Plate | 28 | `offering` |
| Stationary Balloon | 26 | `balloon` |
| Matching Trees | 20 | `matching_trees` |
| Circle of Rocks | 20 | `rock_circle` |
| Rock Lift (Leaves) | 19 | `rock_under_leaves` |
| Melt Ice Block | 18 | `melt_ice` |
| Ball and Chain | 16 | `ball_and_chain` |
| Hanging Acorn | 14 | `hanging_acorn` |
| Rock Lift (Slab) | 12 | `rock_under_slab` |
| Flower Order | 11 | `flower_order` |
| Pinwheel Acorns | 10 | `pinwheel_acorns` |
| Rock Lift (Door) | 8 | `rock_under_metal_plate` |
| Rock Lift (Boulder) | 6 | `rock_under_boulder` |
| Shoot the Crest | 4 | `shoot_target` |
| Jump the Fences | 2 | `horse_hurdles` |
| Shoot the Targets | 1 | `shoot_target` |
| Light Torch, Take the Stick, Remove Luminous Stone, Burn the Leaves (Goatee), Take Apple from Palm Tree | 1 each | `other` |

**All 900 seeds have a `puzzleType`** with `typeSource: "internal"`. None are left open, so no further classification
pass is needed.

### How strongly each type is backed

This doesn't change any data. It records how radar reached each type, in case a seed ever looks wrong in the game.

- **A puzzle-specific object is in the puzzle's group**, which covers most seeds: cube, race, flowers, pinwheels,
  balloons, acorns, ice, all hidden-rock types, matching trees, hurdles, archery targets, and nearly all rock
  patterns, boulders, balls and offerings.
- **Only the shape of the logic tags**, with no puzzle object in the group: most `sparkle` (51), `moving_sparkle` (39,
  which do have a movement rail), `dive` (35), `rock_circle` (20) and the 4 crests in `shoot_target`. radar's
  authors matched these patterns by hand. Zelda Wiki lists exactly 35 dives and 20 rock circles.
- **Seeds where the expected object is missing from the group**, though objmap's type stands:
  - 6 `Roll a Boulder` with no boulder (D24, D31, L42, L44, L74, E26) and 1 `Ball and Chain` with no ball (R66).
  - 6 `Stationary Lights` that are ground Koroks (D45, N47, E16, W09) or sit by a pinwheel with nothing to shoot
    (D42, W18).
  - 4 `Offering Plate` without a Korok plate (F25, F39, F48, X13). Zelda Wiki lists all four as offerings.
  - L32, a `Rock Pattern` made of metal crates under water.

### Differences from Zelda Wiki

objmap comes from the game files, so where the two disagree the data follows objmap and is unchanged:

- **Rock patterns:** objmap has 73 (`Rock Pattern`, including the metal-crate one), Zelda Wiki lists 69.
- **Flowers:** objmap has 57 (46 `Flower Trail` + 11 `Flower Order`), Zelda Wiki's Flower Trail page lists 56.
- **Fairy dust:** Zelda Wiki lists 68 fixed (ice-melting ones included) against objmap's 51 `Stationary Lights` + 18
  `Melt Ice Block` = 69, and 37 moving against objmap's 39 `Moving Lights`.
- **Matches:** cube patterns 66 = 66, offerings 28 = 28, dives 35 = 35, rock circles 20 = 20.

### Region weights for later per-seed work

Classification is finished. If a later pass writes something for every seed (directions, screenshots), these groups of
neighbouring regions are balanced:

| Worker | Regions | Seeds |
|---|---|---:|
| 1 | Central, Ridgeland, Great Plateau | 212 |
| 2 | Lake, Dueling Peaks, Faron | 209 |
| 3 | Hateno, Lanayru, Akkala, Eldin | 230 |
| 4 | Hebra, Tabantha, Woodland, Wasteland, Gerudo | 249 |
