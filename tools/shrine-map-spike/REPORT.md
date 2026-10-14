# Feasibility report: shrine state from map screenshots

**Verdict: feasible.** At minimum zoom the map is a pure scale-plus-translation
of world coordinates, with a constant scale. Registration is sub-pixel, and the
shrine icon is large and distinctive enough to classify from colour masks and
a single 33×33 template. On the all-complete save, nothing was put in a wrong
category: every non-Completed result is a deliberate Unsure. On the partial
save, every "Not present" spot was checked against the all-complete shot at the
identical pan, and an icon really does appear there when the shrine is
discovered.

Samples: `samples/all-complete/1000002225.jpg` (north) and `…2224.jpg` (south),
both from a save with every shrine done, and `samples/partial/1000002226.jpg`
(north only) from an early/mid-game save.

## 1. Registration

The model is `u = s·x + tx`, `v = s·z + ty` (world x/z to screen px), with no
rotation. Anchors come from two detectors: shrine diamonds, by template-matching
a ring against an icon-blue mask, and Sheikah Towers, which have their own
template. Each class is paired only with its own world points. The fit is a
Hough vote over translation for each candidate scale, followed by least squares
on the inliers.

| Shot | Scale (px / world unit) | tx | ty | Anchors (shrines + towers) | RMS | Max |
|---|---|---|---|---|---|---|
| all-complete north | 0.134984 | 959.48 | 674.38 | 82 (72 + 10) | 0.47 px | 0.87 px |
| all-complete south | 0.134988 | 959.51 | −0.60 | 48 (41 + 7) | 0.44 px | 0.83 px |
| partial north | 0.134956 | 959.57 | 674.41 | 34 (24 + 10) | 0.44 px | 0.82 px |

- The scale agrees to 0.02% across saves, so **minimum zoom is a constant
  0.135 px per unit** (1 px ≈ 7.4 world units, and the whole map is
  1350 × 1080 px). Running with `--scale 0.135` fits translation only,
  registers in 0.13 s instead of 0.94 s, and gives identical results.
- `tx` was 959.5 in all three shots. The user only panned vertically
  (675 px between the halves). Horizontal panning may be locked at minimum
  zoom because the map is narrower than the viewport. That is unconfirmed, but
  the fit handles it either way (see §5).
- The residual (< 1 px) is mostly JPEG noise and the template peak landing on
  a whole pixel. It is far below the 25 px icon size.

## 2. Results on the all-complete save (ground truth: all 136 done)

**132 Completed, 4 Unsure, 0 wrong.** Full table:
[`out/all-complete/results.md`](out/all-complete/results.md).

| Shrine | Result | Why |
|---|---|---|
| Hawa Koth | Unsure ✔ expected | Fully under the Travel Medallion. The ring test fires, but the silhouette doesn't match (0.25) and the area around it is solid blue, so it is read as "bigger blue icon on top". |
| Akh Va'quot | Unsure (ideal: Completed) | At minimum zoom the Vah Medoh bird covers essentially the whole diamond (see the `-spots.jpg` crop). What blue is visible belongs to the bird, so the silhouette is 0.56 against a 0.60 threshold and the area around it is 48% blue. I think Unsure is the honest answer. Calling it Completed would mean special-casing "Medoh is here". |
| Ree Dahee, Shee Venath | Unsure (ideal: Completed) | Their world positions are **3.9 px apart** at minimum zoom: both are on the northern Dueling Peak, one on a low ledge and one just below the summit. So one icon is drawn exactly over the other. The pixels can't say whether one or both is discovered, so the pair is flagged as "stacked". The app could ask for a zoomed-in screenshot, or ask the user. |

Other notes:
- Shrines drawn over stable/village icons (tan squares) classify fine, because
  the shrine icon is always drawn on top.
- Region labels don't hide icons: Tutsuwa Nima sits on the "Akkala" label and
  still reads as Completed.
- Korgu Chideh is partly under the map cursor's corner bracket, and a star
  stamp sits on Sasa Kai's glyph. Both still read as Completed.
- Every shrine was read from at least one shot. Icons dim near the top and
  bottom of the view, so only spots with y between 140 and 906 are judged.
  Anything outside that band is "at screen edge" and gets taken from the other
  shot. The two halves overlap by about 90 px of usable
  band.

## 3. Results on the partial save (no full ground truth)

**26 Completed, 3 Not Done, 60 Not present, 47 Unsure.** Of the 47 Unsure, 42
are simply south of this north-only shot, and 5 are covered. Table:
[`out/partial/results.md`](out/partial/results.md). Eyeball sheet:
`out/partial/1000002226-spots.jpg`.

**The Not Done icon:** the normal blue diamond outline with the Sheikah-eye
glyph filled **orange** (hue ≈ 23/180, S ≈ 113, V ≈ 206) instead of blue. The
outline is identical, so one silhouette template covers both states, and glyph
colour decides between them. Three shrines have it:

- **Tena Ko'sah**, user-confirmed. Note that Tena Ko'sah is among the Ancient
  Columns south of Tabantha Tower (next to the horse icon on this shot), not
  at Hyrule Castle. The castle shrine is **Saas Ko'sah**, which reads blue
  (Completed) on this save.
- **Qua Raym** (Goronbi Lake) and **Shae Mo'sah** (north Goron City, on the
  lava overlay). These are the "orange-tinted icons in Eldin". Both are blue at the
  identical pixel in the all-complete shot. Unless the game has another orange
  variant, they are also unfinished on this save: worth asking the user.

**The other "tinted" icons** are completed (blue-glyph) shrines drawn over a
tan stable/village icon, which makes them look warm at a glance: Rin Oyaa,
Keo Ruug, Kaya Wan, Mo'a Keet, Mirro Shaz, Ta'loh Naeg, Ne'ez Yohma. The
yellow-looking one in west Hebra is Sha Warvo with a **yellow star stamp** over
its top half.

**Checking "Not present":** all 60 Not-present spots were judged at pixel
positions where the all-complete shot, at the same pan, shows a shrine icon.
So each spot can show an icon, and on this save it doesn't. All 60 have a
confidence of 0.85 or more, and the contact sheet confirms they are empty
terrain. They include the two labyrinth shrines, Qaza Tokki (North Lomei) and
Tu Ka'loh (Lomei Labyrinth Island), where the labyrinth artwork is visible but
there is no icon.

**Unsure in view (the least confident results), each for a real reason:**

| Shrine | Cause |
|---|---|
| Akh Va'quot | Vah Medoh icon (as above) |
| Sha Warvo | Yellow star stamp over the top of the icon (bottom half visible, glyph blue) |
| Noe Rajee (DLC) | Same stamp. 14 px from Sha Warvo, centred under the stamp |
| Bareeda Naag | Rito Village tan icon over the spot |
| Dah Hesho | Red map pin and its glyph over the spot |

Lowest-confidence definite calls: Shae Mo'sah (Not Done, 0.69, on the lava
overlay and beside a tan icon), Soh Kofi (0.72), and Rota Ooh and
Shae Loya (0.77, each next to a tan icon). The clutter you listed was all
handled correctly. Stamps, the yellow target ring, purple and red pins with
beams, the horse, and the Sheikah Sensor widget gave no false icons. The
purple beam passes close to Goma Asaagh but leaves its spot clear, so that one
correctly reads Not present.

**Registration with few icons:** this save gave 24 shrine and 10 tower
anchors, and registration was as accurate as on the full save. See §5 for
what happens with far fewer.

## 4. Icon size and crowded areas

- Shrine diamond: **25 × 25 px** (half-diagonal 12.5 px, outline ~3 px), with a
  ~9 px glyph inside. Towers are ~26 × 30 px, drawn a few px above their world
  point (the template absorbs this).
- Closest shrine pairs at minimum zoom: Ree Dahee–Shee Venath 3.9 px
  (inseparable), Shee Vaneer–(either) 12.3 px, Sha Warvo–Noe Rajee 14.0 px,
  Kayra Mah–Rinu Honika 23.2 px, Owa Daim–Ruvo Korbah 26.7 px, Dagah
  Keek–Ne'ez Yohma 27.2 px, Ja Baij–Rohta Chigah 27.5 px, Lakna
  Rokee–Ta'loh Naeg 28.9 px. Everything above ~12 px was resolved correctly.
- **Great Plateau** (4 base + 4 DLC shrines): all Completed on the
  all-complete save. It is near the bottom of the north shot, so it is read
  from the south shot.
- **Kakariko** (Ta'loh Naeg, Lakna Rokee) and **Akkala**, including the
  labyrinth and the Akkala label area: all correct.
- **Dueling Peaks** is the only place the method gives up, for the stacked
  pair. Shee Vaneer, 12 px away, resolves fine.

## 5. Robustness (`robustness.py`, output in `out/robustness.txt`)

**Too few anchors (an early-game player).** Starting from the partial shot,
keep only k random true shrine icons, plus every spurious detection. Success
means projection error under 2 px everywhere, over 200 trials:

| k shrine icons | no towers, free scale | no towers, fixed scale | + the 10 visible towers |
|---|---|---|---|
| 1–2 | 0% (below the 3-anchor minimum) | 0% | 100% |
| 3 | 52% | 89% | 100% |
| 4 | 92% | 98% | 100% |
| 6 | 99% | 100% | 100% |
| 10 | 100% | 100% | 100% |

Fixing the scale at 0.135 matters most when anchors are scarce. Towers make
registration close to certain even for a player with a single shrine. Towers
are detected with their own mask, because their pale blue (S ≈ 105–150) varies
between saves: the first attempt found 1 of 10 on the partial save. A failed
registration returns None, never a wrong fit.

**Simulated panning.** The map content was shifted under the fixed HUD frame by
seven offsets, up to (−233, 101) px. Translation was recovered to within
0.03–0.14 px, both free and fixed scale, and **every shrine judged in both the
original and the shifted image got the same category** (66–92 shrines per
offset, 100% agreement).

**Real pan.** The two all-complete shots differ by a 675 px vertical pan.
They gave the same scale (to 4×10⁻⁶) and the same tx (within 0.04 px). All 13
shrines judged in both shots agree.

**Decoys.** Korok-seed positions (never drawn on the map), at least 25 px from
any shrine or tower, were classified as if they were shrines:

| Shot | Not present | Unsure | False icon |
|---|---|---|---|
| all-complete north | 455 | 12 | 0 |
| all-complete south | 277 | 9 | 1 |
| partial north | 454 | 13 | 0 |

The single false icon is a korok spot sitting on the Shrine of Resurrection
icon (see §7). The Unsure results are korok spots under pins, stamps, labels or
other icons, which is the intended behaviour.

## 6. Recommended path on-device

The whole pipeline is: an HSV threshold to a few binary masks, a vote over
translation, and normalised cross-correlation of a 33×33 template at about 150
known positions. With the scale fixed at 0.135, nothing scale- or
rotation-invariant is needed.

1. **Pure Kotlin in `shared/commonMain` (recommended).** Decode the bitmap
   per platform, then run everything on an `IntArray` of pixels:
   - masks: one pass over 2 Mpx;
   - candidates: connected components of the blue mask, keeping blobs about an
     icon in size, rather than full-frame template matching;
   - registration: translation votes with fixed scale (about 30 candidates ×
     151 anchors, so trivial);
   - classification: 136 spots × a 37×37 window.

   This should run well under a second on a phone, needs no native
   dependency, and is testable with the three samples as golden tests. Ship
   `shrine_shape.png`, `tower_shape.png` and `coords.json` as resources.
2. **OpenCV for Android** would be a straight port of this Python, but it
   adds a large native dependency (tens of MB of `.so` per ABI) for about
   three functions. It is only worth it if more CV features are planned.
3. **ML Kit** has nothing off the shelf for this. A custom detector would need
   labelled training data, and still wouldn't give the geometric reasoning
   (projection, occlusion) that does the real work here. Not recommended.

Product shape: results should be suggestions the user confirms. Pre-tick
Completed and Not Done, leave Not present alone, and list Unsure shrines by
name for manual review, with the stacked pair and "Medoh / Medallion covers it"
as known reasons. Accept any number of minimum-zoom shots and merge them. A
shot that fails to register (fewer than 3 matching icons) should be rejected
with a clear message. Reject anything that isn't 1920×1080 or isn't at minimum
zoom: a scale fit far from 0.135 is a good tell.

## 7. Still needed to confirm

- **More Not Done samples.** The orange-glyph rule rests on one user-confirmed
  icon (Tena Ko'sah) plus two inferred ones (Qua Raym, Shae Mo'sah). Ask the
  user whether those two are really unfinished on the partial save. The ideal
  next sample is a south-half shot of the same save, plus any Not Done shrine
  sitting on a tan icon or near a screen edge.
- The **south half of the partial save**, to test Not present in the desert,
  Faron and the Great Plateau, and to check the dark area below the map.
- A **horizontal pan**, if the game allows one at minimum zoom (it is
  simulated here, not observed).
- **Hero's Path mode on**, which draws a trail over the map. It is untested
  and could cause false Unsure results.
- Screenshots must come straight from the Switch album. A photo of the TV
  would break the fixed-scale assumption entirely.
- One lookalike is known: the Shrine of Resurrection icon (Great Plateau) is a
  diamond with an arch and matches the silhouette (it showed up in the decoy
  test). It isn't near any real shrine, so it can't cause a misread today.
