# Theme fonts

Each game theme sets its headers in an openly licensed font. These are look-alikes for the game's
era, not Nintendo's own fonts, which are proprietary and never bundled. Only the display, headline
and `titleLarge` styles use them: top-bar titles, card headings and a game's name on the home
screen. Body text, list rows and labels stay in the system font.

The files are in `shared/src/commonMain/composeResources/font/`, and `DisplayFont`
(`theme/DisplayFont.kt`) maps each one to its slot.

| File | Font | Licence | Slot | Games | Size |
|---|---|---|---|---|---|
| `press_start_2p.ttf` | [Press Start 2P](https://github.com/google/fonts/tree/main/ofl/pressstart2p) Regular, by CodeMan38 | SIL OFL 1.1 ([text](licenses/press-start-2p-OFL.txt)) | `PIXEL_8BIT` | The Legend of Zelda, Zelda II, Link's Awakening | 113 KB |
| `pixelify_sans_semibold.ttf` | [Pixelify Sans](https://github.com/google/fonts/tree/main/ofl/pixelifysans) SemiBold, by Stefie Justprince | SIL OFL 1.1 ([text](licenses/pixelify-sans-OFL.txt)) | `PIXEL_16BIT` | A Link to the Past, Oracle of Seasons, Oracle of Ages, Four Swords, The Minish Cap | 48 KB |
| `fredoka_semibold.ttf` | [Fredoka](https://github.com/google/fonts/tree/main/ofl/fredoka) SemiBold, by Milena Brandão and Hafontia | SIL OFL 1.1 ([text](licenses/fredoka-OFL.txt)) | `ROUNDED` | The Wind Waker, Four Swords Adventures | 48 KB |
| `josefin_sans_regular.ttf` | [Josefin Sans](https://github.com/google/fonts/tree/main/ofl/josefinsans) Regular, by Santiago Orozco | SIL OFL 1.1 ([text](licenses/josefin-sans-OFL.txt)) | `SHEIKAH` | Breath of the Wild | 56 KB |
| `cinzel_bold.ttf` | [Cinzel](https://github.com/google/fonts/tree/main/ofl/cinzel) Bold, by Natanael Gama | SIL OFL 1.1 ([text](licenses/cinzel-OFL.txt)) | `SERIF` | Ocarina of Time, Majora's Mask, Twilight Princess, Skyward Sword, Hyrule Warriors: Definitive Edition | 45 KB |

Total: 310 KB.

## Rules

- **Budget: 400 KB for all theme fonts.** `GameFontsTest` fails past it, and also fails if a file
  in the font folder isn't used by any `DisplayFont`.
- Static single-weight instances only. Variable fonts are 2–3 times the size. Google Fonts' CSS
  API serves the static files (e.g.
  `https://fonts.googleapis.com/css2?family=Fredoka:wght@600` gives the `.ttf` URL).
- Every font must cover every game's title and tracker title, plus é, ü, ’ and ×.
  `GameFontsTest` checks this.
- The files are unmodified. Press Start 2P and Josefin Sans have Reserved Font Names, so they must
  not be subset or edited under the same name.
- Keep each font's `OFL.txt` in `docs/licenses/`, as the licence requires.

## Planned

Every game now has its slot; no more fonts are planned.

The fallback for the 8-bit slot is Silkscreen (31 KB), if Press Start 2P turns out too wide for a
title.
