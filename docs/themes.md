# Game themes

Inside a game, every screen wears that game's theme: its trackers, the overview, detail pages,
scans and the map. A theme has five parts:

- **Colours:** a light and a dark palette, picked from the game's menus.
- **Display font:** header lettering in the style of the game's era (`docs/fonts.md`).
- **Corner shape.**
- **Ornament:** a rule under each region's header.
- **Progress style:** how its progress bars are drawn.

Home, Settings and the menu keep the app's neutral look. Each themed game's accent colour appears
on its own card, row and switch there.

## How it works

- **Where themes are defined.** `GameThemes` (`theme/GameThemes.kt`) holds each theme by game id.
  `GameThemes.of(id)` falls back to `Neutral` for a game without a theme yet, for `"hwde"` until
  its batch, and for no game at all.
- **How a screen gets its theme.** Every route in `App.kt` goes through a private `composable<T>`
  wrapper. The wrapper reads the route's `gameId` argument and wraps the destination in
  `ZeldaTheme(GameThemes.of(gameId))`, so a new game route is themed without any extra code. Home
  and Settings have no `gameId`, so they stay neutral. The theme is applied per destination, so a
  screen fading out during navigation keeps its own colours.
- **What `ZeldaTheme` provides.** `MaterialTheme` (colour scheme, typography, shapes) plus
  `LocalGameDecor`, which carries the ornament, the progress style and the star colour. Bespoke
  screens get the colours, lettering and corners automatically through `MaterialTheme`. The decor
  shows through shared components: `GameProgressBar`, `RegionHeader`'s `OrnamentDivider` and
  `Stars`.
- **Settings.** `AppTheme` reads two choices. `ThemeMode` (System/Light/Dark) resolves light or
  dark; before game themes the app was always dark. `gameThemes` set to off makes every
  `ZeldaTheme` neutral. Both are passed down through `LocalAppearance`.
- **Palettes.** A palette is about 18 hand-picked colours. `GamePalette.toColorScheme` derives the
  rest: surface containers step from the surface toward the text colour, and the inverse roles come
  from the opposite side of the same theme. The error colour defaults to Material's.
- **Fixed colours.** Colours that carry meaning stay the same in every theme: map markers, Kinstone
  colours and scan overlays.

## Checks

- **`GameThemeContrastTest`.** For every theme, light and dark, it checks WCAG 2.1 AA:
  - 4.5:1 for text on whatever it sits on: on/X pairs; onSurface, onSurfaceVariant, primary and
    error on every surface and container; snackbars; and the star colour, which "Fully upgraded."
    is set in.
  - 3:1 for outlines, progress blocks and stars.
- **`GameThemesTest`.** Every game resolves to a theme, unknown ids fall back to Neutral, every theme
  belongs to a game, and the neutral dark scheme is the app's original.
- **`ThemeContactSheetTest`.** Renders a sample tracker in every theme, light beside dark, plus the
  real home screen, to `shared/build/reports/theme-sheets/*.png`. CI uploads them with the test
  reports. It checks only that the screens render; a person has to look at the pictures.

## Briefs

Light is the game's daylight or parchment side; dark is its menu or night side. ✓ marks the themes
built so far. The hex values are where each palette starts. A theme's final tones are whatever passes
the contrast test, so text colours are often a step darker (light) or lighter (dark) than the game's
own.

**Sources:**
- Fonts: the in-game fonts were checked against Zelda Universe's font list,
  https://zeldauniverse.net/media/fonts/.
- NES colours: entries of the NES palette (hues as in https://www.nesdev.org/wiki/PPU_palettes).
- Breath of the Wild's "Zelda White": https://thefamicast.com/2017/12/part-2-immersive-impressionable-ui.html.
  Its colour names (Sheikah Slate Blue, Orange) are from Zelda Wiki's `Template:Color_BotW`.
- The Wind Waker's menu change between the GameCube and HD versions:
  https://zeldauniverse.net/2013/08/14/the-wind-waker-hd-gets-a-new-and-improved-item-menu/.
- Tears of the Kingdom's Purah Pad gold and crimson: Zelda Wiki's Purah Pad page.
- Sampled: the themes marked ✓ were sampled from the game's own menu sheets (see **Sampled
  sources** below), except The Wind Waker, whose colours are still **from memory, unverified**
  apart from the GameCube/HD menu change: no UI sheet for it is archived.
- Unverified: the unmarked games' hex values are approximate starting points, to be sampled before
  their batch.

| | Game | Brief | Key colours | Font · shape · ornament · progress |
|---|---|---|---|---|
| ✓ | The Legend of Zelda | The NES pause screen: black, heart red, item-box blue, Triforce orange, mini-map grey. Light is the overworld: tan ground, forest green | #000000, #F83800, #2038EC, #FC9838, #747474; #FCE0A8, #005800 | 8-bit pixel · pixel · pixel rule · segmented |
| ✓ | Zelda II | File select and HUD: black, white lettering, the window's blue border, LIFE red, dark gold. Light is palace stone | #000000, #FCFCFC, #2038EC, #D82800, #887000; #F0E8D8 | 8-bit pixel · pixel · bricks · segmented |
| ✓ | A Link to the Past | The status screen: black windows with double borders in Triforce gold, item green and the "DO" box's red. Light is the Light World's pale green | #000400, #F8F8F8, #E0A800, #20C028, #C00000; #F2F6E8 | 16-bit pixel · pixel · Triforces · segmented |
| ✓ | Link's Awakening | Dark: the Game Boy's four greens. Light: the DX colours, cream with heart red, the island's green and the item screen's blue | #0F380F, #306230, #8BAC0F, #9BBC0F; #D80000, #10A840, #4040C0 | 8-bit pixel · pixel · pixel rule · segmented |
| | Ocarina of Time | The N64 pause cube: stone-grey panels, gold page headers, C-button yellow, A-button blue | #3A3A40, #E0B040, #5A5AFF, #00C832 | serif · rounded · crest · solid |
| | Majora's Mask | Doomsday: moon indigo, clock-arc amber, mask magenta | #2A1F4A, #F0B000, #C0306A | serif · rounded · clock arc · solid |
| ✓ | Oracle of Seasons | The title screen's day (cream, logo red, Maku Tree green, gold) on the HUD's peach; dark is the horseback title's night purple. Accents: the four seasons | #FFFBEF, #A50000, #428A31, #D6B200, #FFD68C; #1F103C, #351B65 | 16-bit pixel · pixel · seasons · segmented |
| ✓ | Oracle of Ages | Nayru's blues, with Din's red as the third colour; light is her palest blue, dark is night water | #358BF1, #4EC8F5, #ADE2F9, #CCF3FC, #F25656 | 16-bit pixel · pixel · waves · segmented |
| ✓ | Four Swords | The GBA game-select panel: amber-to-brown gold, the logo's red. Accents: the four Links in the Force Gems' shades | #703800, #A86000, #F8D800, #BF0000; gems #299429, #AD2929, #2929FF, #AD29E7 | 16-bit pixel · pixel · colour bands · segmented |
| ✓ | The Wind Waker | Cel-shaded sea: sea-chart parchment with deep-sea blue (light), the GameCube item menu's forest green with cerulean (dark), wind-curl waves | #F7EFD9, #E8D8A8, #00639B; #0F2419, #7CC8F8 | rounded · soft · waves · rounded |
| ✓ | Four Swords Adventures | The logo's deep greens and the Force Gems; accents are the gems | #0D7F41, #175A2F, #162719, #80C486; gems as Four Swords | rounded · rounded · colour bands · segmented |
| ✓ | The Minish Cap | The file select: navy windows, white lettering, blue banners, teal-green buttons, the crystal-green backdrop. Light is crystal-green paper | #000030, #000850, #F0F8F8, #0818A8, #10B090, #30F898 | 16-bit pixel · rounded · vine · segmented |
| | Twilight Princess | Dusk realism: charcoal and gold filigree, twilight orange, Twili teal | #1A1410, #C8A050, #E06A20, #30D0C0 | serif · cut · Twili geometric · solid |
| | Phantom Hourglass | A sailor's chart: parchment, hourglass-sand gold, ocean blue | #E8D8A8, #E8C060, #2A70B8 | rounded · soft · rope · rounded |
| | Spirit Tracks | Railway: engine green and red, track lines, spirit pink | #2E8B57, #C83030, #F0A0C0 | rounded · soft · rail ties · segmented |
| | Skyward Sword | Watercolour sky blue, cloud white, goddess gold, Loftwing crimson | #8CC8F0, #E0C060, #C03030 | serif · soft · cloud scallop · rounded |
| | A Link Between Worlds | A Link to the Past remixed: purple energy gauge, Lorule red-purple (dark) | #A050D0, #6A2040 | rounded · rounded · inverted Triforce · rounded |
| | Tri Force Heroes | Three Links on boutique pink | #38B838, #3878F8, #E83838, #F070A8 | rounded · soft · three-colour rule · rounded |
| ✓ | Breath of the Wild | Minimal and ancient: Zelda White lettering on the Sheikah Slate's dark glass, the map's Sheikah blue and shrine orange. Light turns it round: Zelda White paper, deep slate blue | #0E1214, #F4EEDC, #3DB2E5, #F5A524; #F6F1E3, #00677F | Sheikah · cut · glyph line · thin |
| | Tears of the Kingdom | Zonai relics: green glow, Purah Pad gold and crimson, Gloom red | #6BE89A, #D4A84A, #8A1020 | Sheikah · cut · Zonai spiral · thin |
| | Echoes of Wisdom | Toy diorama pastels, Tri's gold sparkle, rift purple-black (dark) | #F8D848, #2A1840 | rounded · soft · sparkles · rounded |
| | Hyrule Warriors: DE | Musou battlefield: charcoal panels, gold trim, war-banner red | #1E1E22, #C8A040, #B02020 | serif · cut · triangle row · segmented |

## Sampled sources

Sheets are The Spriters Resource's, fetched through the Wayback Machine (the site itself refuses
scripted requests). Each was opened, the menu parts picked out by eye (sheet backgrounds and
placeholder colours such as #008000, #FF00FF and cyan blocks are not the game's), and the regions
counted with `tools/themes/SamplePalette.java`. Where the contrast test needed it, only text
colours were moved: lifted on dark, darkened on light. Backgrounds stay as sampled.

- **The Legend of Zelda**: [HUD & Pause Screen](https://web.archive.org/web/20240910120850im_/https://www.spriters-resource.com/resources/sheets/116/119278.png),
  [Title Screen & Story](https://web.archive.org/web/20250704102007im_/https://www.spriters-resource.com/resources/sheets/29/32134.png).
  Black #000000 → dark surface; heart red #D82800 → dark primary, lifted to #F83800 for 4.5:1;
  item-box blue #2038EC → dark secondary container; Triforce/map orange #FC9838 → dark tertiary
  and stars; mini-map grey #747474 → dark outline; title tan #FCD8A8 → light surface (one step
  lighter, #FCE0A8).
- **Zelda II**: [Title Screens & HUD](https://web.archive.org/web/20250417173317im_/https://www.spriters-resource.com/resources/sheets/2/1621.png)
  (file-select window and HUD). Black #000000 → dark surface; #FCFCFC → text; window blue #2038EC →
  dark primary container and light primary; LIFE red #D82800 → secondary (lifted/darkened); gold
  #887000 → tertiary container.
- **A Link to the Past**: [Status Screen (JPN/USA)](https://web.archive.org/web/20250809153305/https://www.spriters-resource.com/resources/sheets/37/39608.png).
  Window black #000400 → dark surface; #F8F8F8 → text; border gold #E0A800 → dark primary and
  stars; item-window green #20C028 → dark secondary; "DO" red #C00000 → dark tertiary container.
  The navy in the original brief was wrong: the windows are black.
- **Link's Awakening**: DX [File Menu](https://web.archive.org/web/20250618015959im_/https://www.spriters-resource.com/resources/sheets/24/26780.png)
  and [Weapons, Items & HUD](https://web.archive.org/web/20250420134851im_/https://www.spriters-resource.com/resources/sheets/9/9450.png).
  Hearts #D80000 → light primary (#C00000); green #10A840 → light secondary (darkened); item-screen
  blue #4040C0 → light tertiary; brown #805000 → light stars. The dark side is the Game Boy's four
  greens, #0F380F/#306230/#8BAC0F/#9BBC0F, the standard DMG emulator values (the original game's
  sheets aren't archived), with text lifted to #C8E08C.
- **Oracle of Seasons**: [Title Screen](https://web.archive.org/web/20240715175249im_/https://www.spriters-resource.com/resources/sheets/9/8994.gif),
  [Title Screen Horseback](https://web.archive.org/web/20241229145849im_/https://www.spriters-resource.com/resources/sheets/47/49610.png),
  [HUD & Gear](https://web.archive.org/web/20200831103729im_/https://www.spriters-resource.com/resources/sheets/135/138116.png?updated=1598136088).
  Cream #FFFBEF → light surface; logo red #A50000 → light primary and dark primary container;
  tree green #428A31 → secondary; gold #D6B200 → tertiary (darkened/lifted); HUD peach #FFD68C →
  light surface variant (lightened); night purples #1F103C/#351B65 → dark surface and variant.
  The season accents' hues are read by eye.
- **Oracle of Ages**: [Din & Nayru](https://web.archive.org/web/20190415002449im_/https://www.spriters-resource.com/resources/sheets/9/9101.png).
  Nayru's #358BF1 → primary (darkened on light); #4EC8F5 → dark primary; #ADE2F9 → light primary
  container and dark secondary; #CCF3FC → light surface variant; Din's #F25656 → dark tertiary.
  The sepia "past" from the brief isn't sampled, so it isn't used.
- **Four Swords**: [Game Selection](https://web.archive.org/web/20241229150127im_/https://www.spriters-resource.com/resources/sheets/19/20702.png).
  Panel browns #703800/#985000/#A86000 → dark surface variant, light primary, dark primary
  container; golds #F8D800/#F8A800 → dark primary and tertiary; logo reds #F80000/#BF0000 →
  secondary. The Links' accents are the Force Gems' shades from Four Swords Adventures.
- **Four Swords Adventures**: [Force Gems](https://web.archive.org/web/20220915122113im_/https://www.spriters-resource.com/resources/sheets/27/29127.png?updated=1460956014).
  Gems: green #299429/#29E739, blue #2929FF/#5263FF, red #AD2929/#FF2929, purple #AD29E7/#DE29FF
  → accents (dark shades on light, bright on dark) and the blue → tertiary; logo greens
  #0D7F41/#175A2F/#162719/#80C486 → primary container, dark surface variant, dark surface, dark
  primary (lifted to #A0DCA4). The gold is read by eye.
- **The Minish Cap**: [File Select](https://web.archive.org/web/20230801141021im_/https://www.spriters-resource.com/resources/sheets/6/6436.png?updated=1460953633).
  Window navy #000030/#000850 → dark surface and variant; #F0F8F8 → dark text; banner blues
  #0818A8/#0870F0 → secondary; button teal #10B090 → dark primary container (darkened to #086A4E
  for light primary); crystal green #30F898 → dark primary. Phase 1's leaf green, gold and wood
  were from memory, and the real menu is navy and teal, so the theme changed.
- **Breath of the Wild**: the map's shrine glyphs in `composeResources/drawable/map_shrine*.xml`
  (traced from the game's icons, `docs/map-icons.md`): done #3DB2E5 → dark primary, not done
  #F5A524 → dark secondary. "Zelda White" per the Famicast article above.

## Adding a theme

1. Sample the palette from the game's menu sheets (`java tools/themes/SamplePalette.java <png> [x y w h]`,
   on PNGs downloaded to a scratch folder, never committed) and add them to **Sampled sources**.
2. Add a `GameTheme` to `GameThemes` and to `byId`.
3. Run `:shared:jvmTest`. Fix any pair the contrast test names by moving the text colour, not the
   background, so the game's look survives.
4. Look at `shared/build/reports/theme-sheets/<id>.png` in light and dark.
5. A new font must stay inside the budget in `docs/fonts.md`.
