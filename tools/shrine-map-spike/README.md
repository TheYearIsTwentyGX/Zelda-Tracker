# Shrine map spike

A Python feasibility prototype. It reads screenshots of the Breath of the Wild
in-game map (Switch, 1920×1080, **minimum zoom**) and sorts every shrine
(all 136, including the Champions' Ballad ones) into one of four categories:

| Category | Meaning |
|---|---|
| **Completed** | Shrine icon visible, blue glyph |
| **Not Done** | Shrine icon visible, orange glyph (found, not finished) |
| **Unsure** | Spot covered (another icon, a pin, a stamp, label text, the screen edge) or stacked with another shrine |
| **Not present** | Spot clearly visible and empty, so the shrine is undiscovered |

There is no app code here. See [REPORT.md](REPORT.md) for the findings.

## Run

```bash
uv venv .venv && VIRTUAL_ENV=.venv uv pip install -r requirements.txt   # or python -m venv + pip
. .venv/bin/activate

# Classify (any number of min-zoom shots; results are merged)
python run.py --out out/all-complete samples/all-complete/1000002225.jpg samples/all-complete/1000002224.jpg
python run.py --out out/partial samples/partial/1000002226.jpg

# Translation-only fit with the calibrated min-zoom scale (7x faster to register)
python run.py --scale 0.135 --out out/x samples/partial/1000002226.jpg

# Robustness experiments (takes ~6 min; free-scale registration is run thousands of times)
python robustness.py samples/all-complete/1000002225.jpg samples/all-complete/1000002224.jpg samples/partial/1000002226.jpg
```

Each `run.py` output folder contains:

- `<shot>-annotated.jpg`: every projected shrine position, circled in its
  category's colour and labelled. Green is Completed, orange Not Done, yellow
  Unsure, red Not present. White dots are icon detections. The fit is
  printed along the bottom.
- `<shot>-spots.jpg`: a zoomed crop of every judged spot, grouped by
  category. Use this to check the result by eye.
- `results.md` / `results.csv`: one row per shrine with category, confidence,
  reason, and the verdict from each screenshot.
- `registration.json`: scale, translation, anchor count and pixel residuals
  per screenshot.

## Files

| File | What |
|---|---|
| `shrinemap.py` | The pipeline: colour masks, icon detection, registration, spot classification, merging |
| `run.py` | Command-line entry point; writes the outputs above |
| `robustness.py` | Anchor-starvation, simulated-pan, real-pan and decoy experiments |
| `fetch_coords.py` | Regenerates `data/coords.json` and `data/koroks.json` from objmap.zeldamods.org |
| `build_template.py` | Regenerates `data/shrine_shape.png` and `data/tower_shape.png` from the all-complete shots |
| `data/coords.json` | World (x, z) for the 136 shrines (keyed by the app's shrine `id`/`name`) and the 15 towers |
| `data/koroks.json` | Korok seed positions, used only as decoy spots in `robustness.py` |
| `samples/` | The three source screenshots (all under 1 MB) |
| `out/` | Committed outputs for the samples, plus `robustness.txt` |

`fetch_coords.py` takes the app's `shrines.json` (currently on branch
`worktree-botw-shrines-bebbc6`) and matches it by name to objmap's marker
summary (`game_files/map_summary/MainField/static.json` plus the
`StaticMsg/LocationMarker` and `StaticMsg/Dungeon` name tables):

```bash
git show origin/worktree-botw-shrines-bebbc6:shared/src/commonMain/composeResources/files/botw/shrines.json > /tmp/shrines.json
python fetch_coords.py /tmp/shrines.json
```
