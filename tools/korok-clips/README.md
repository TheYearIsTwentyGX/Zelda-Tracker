# Korok scan test clips

`extract.py` turns a phone video of the BotW map at max zoom into a test clip for
`KorokClipGoldenTest`. It writes every 6th frame, 960 px wide and grey (the scanner only reads luma,
like a camera's Y plane), as JPEGs plus `clip.json`, into
`shared/src/jvmTest/resources/korok-clips/<name>/`.

```bash
python3 -m pip install opencv-python-headless
python3 tools/korok-clips/extract.py VIDEO.mp4 docked-tv-faron        # --step 6 --width 960 --quality 60
```

Then write `expected.json` beside `clip.json`:

```json
{
  "note": "where it pans, and how it was checked",
  "found": ["F01", "F02"],
  "empty": ["F03"]
}
```

- `found`: Koroks with a marker in the clip; the test needs each to read Found.
- `empty`: Koroks in view with no marker; the test needs none to read Found.

Run the test with `KOROK_SHEET=/some/dir` to get `korok-sheet.png`, a crop of every Korok the scan
saw, and check the lists against it by eye. Rerun with `REGENERATE_KOROK_MARKER=1` to rebuild
`korok_marker.json` from every clip's markers.

## From a phone session

In a debug build, **Save frames** on the live scan screen records a session: every 5th analysed
frame (grey, as the scanner saw it, rotated upright) and one line per analysed frame of the pipeline's
state in `state.jsonl` (phase, reason, scores, the screen's corners, the map origin, stage ms), with
the camera's real output in `session.json`. Pull it and turn it into a clip:

```bash
adb exec-out run-as gx.zelda.zeldagx tar c files/korok-debug > korok-debug.tar
tar xf korok-debug.tar
python3 tools/korok-clips/from_session.py files/korok-debug/<timestamp> phone-docked-tv
```

Keep each clip to a few MB: raise `--step` or lower `--quality` if it's bigger. Don't commit the
source videos.
