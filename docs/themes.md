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
- Unverified: the other hex values are approximate and **still to be sampled from menu
  screenshots** before each game's batch (Spriters Resource / Textures Resource UI sheets). That
  includes all of The Minish Cap's colours: leaf green, Kinstone gold and wood.

| | Game | Brief | Key colours | Font · shape · ornament · progress |
|---|---|---|---|---|
| ✓ | The Legend of Zelda | The NES subscreen: black void, heart red, item-box blue, Triforce orange. Light is the overworld: tan ground, forest green | #000000, #F83800, #0058F8, #FCA044; #FCE0A8, #005800 | 8-bit pixel · pixel · pixel rule · segmented |
| | Zelda II | A darker RPG cousin: red LIFE and white MAGIC bars on black, palace stone | #881400, #F83800, #7C7C7C | 8-bit pixel · pixel · brick rule · segmented |
| | A Link to the Past | SNES menu: navy windows with pale double borders, Triforce gold; Light World green (light) and Dark World purple-brown (dark) | #102850, #D8E8F8, #F8D878, #40C040 | 16-bit pixel · pixel · Triforce row · segmented |
| | Link's Awakening | Game Boy DMG greens (dark) and the DX colours with red hearts (light) | #0F380F, #306230, #8BAC0F, #9BBC0F | 8-bit pixel · pixel · pixel rule · segmented |
| | Ocarina of Time | The N64 pause cube: stone-grey panels, gold page headers, C-button yellow, A-button blue | #3A3A40, #E0B040, #5A5AFF, #00C832 | serif · rounded · crest · solid |
| | Majora's Mask | Doomsday: moon indigo, clock-arc amber, mask magenta | #2A1F4A, #F0B000, #C0306A | serif · rounded · clock arc · solid |
| | Oracle of Seasons | GBC, Din's red-orange, the four seasons as accents | #E87830, #F8A0C0, #A8D8F8 | 16-bit pixel · pixel · leaf rule · segmented |
| | Oracle of Ages | GBC, Nayru's blue and teal, the sepia past (light) | #3068C8, #40A8B8, #C8A878 | 16-bit pixel · pixel · wave rule · segmented |
| | Four Swords | Four Links on bright GBA colours | #38B838, #E83838, #3878F8, #9858C8 | 16-bit pixel · pixel · four-colour rule · segmented |
| ✓ | The Wind Waker | Cel-shaded sea: sea-chart parchment with deep-sea blue (light), the GameCube item menu's forest green with cerulean (dark), wind-curl waves | #F7EFD9, #E8D8A8, #00639B; #0F2419, #7CC8F8 | rounded · soft · waves · rounded |
| | Four Swords Adventures | 16-bit sprites in a GameCube frame: four colours and Force Gem gold | as Four Swords + #F0C030 | rounded · rounded · four-colour rule · segmented |
| ✓ | The Minish Cap | GBA storybook miniature: Ezlo's leaf green, Kinstone gold, warm wood, on paper (light) or bark (dark) | #2F6E14, #7A5A00, #8A4F24; #1A1610, #8FD86A, #F0C64A | 16-bit pixel · rounded · vine · segmented |
| | Twilight Princess | Dusk realism: charcoal and gold filigree, twilight orange, Twili teal | #1A1410, #C8A050, #E06A20, #30D0C0 | serif · cut · Twili geometric · solid |
| | Phantom Hourglass | A sailor's chart: parchment, hourglass-sand gold, ocean blue | #E8D8A8, #E8C060, #2A70B8 | rounded · soft · rope · rounded |
| | Spirit Tracks | Railway: engine green and red, track lines, spirit pink | #2E8B57, #C83030, #F0A0C0 | rounded · soft · rail ties · segmented |
| | Skyward Sword | Watercolour sky blue, cloud white, goddess gold, Loftwing crimson | #8CC8F0, #E0C060, #C03030 | serif · soft · cloud scallop · rounded |
| | A Link Between Worlds | A Link to the Past remixed: purple energy gauge, Lorule red-purple (dark) | #A050D0, #6A2040 | rounded · rounded · inverted Triforce · rounded |
| | Tri Force Heroes | Three Links on boutique pink | #38B838, #3878F8, #E83838, #F070A8 | rounded · soft · three-colour rule · rounded |
| ✓ | Breath of the Wild | Minimal and ancient: Zelda White lettering on the Sheikah Slate's dark glass, Sheikah blue glow, Guardian orange. Light turns it round: Zelda White paper, deep slate blue | #0E1214, #F4EEDC, #5CD3F5, #FF9A55; #F6F1E3, #00677F | Sheikah · cut · glyph line · thin |
| | Tears of the Kingdom | Zonai relics: green glow, Purah Pad gold and crimson, Gloom red | #6BE89A, #D4A84A, #8A1020 | Sheikah · cut · Zonai spiral · thin |
| | Echoes of Wisdom | Toy diorama pastels, Tri's gold sparkle, rift purple-black (dark) | #F8D848, #2A1840 | rounded · soft · sparkles · rounded |
| | Hyrule Warriors: DE | Musou battlefield: charcoal panels, gold trim, war-banner red | #1E1E22, #C8A040, #B02020 | serif · cut · triangle row · segmented |

## Adding a theme

1. Sample the palette from the game's menu screenshots and note the sources above.
2. Add a `GameTheme` to `GameThemes` and to `byId`.
3. Run `:shared:jvmTest`. Fix any pair the contrast test names by moving the text colour, not the
   background, so the game's look survives.
4. Look at `shared/build/reports/theme-sheets/<id>.png` in light and dark.
5. A new font must stay inside the budget in `docs/fonts.md`.
