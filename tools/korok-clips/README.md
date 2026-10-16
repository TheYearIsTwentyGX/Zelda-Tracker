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

Keep each clip to a few MB: raise `--step` or lower `--quality` if it's bigger. Don't commit the
source videos.
