# HW:DE map builder

`build_maps.py` writes each Hyrule Warriors: Definitive Edition Adventure Mode map the app has:

- `files/hwde/<map>.json`;
- `files/hwde/<map>/` (a picture per square and the preview);
- `drawable/hwde_card_*.png` and `hwde_reward_*.png`.

It uses only the Python standard library.

```bash
python3 -I tools/hwde/build_maps.py --self-test                    # the parser and id mapping
python3 -I tools/hwde/build_maps.py --map adventure_map --check    # the cross-check report only
python3 -I tools/hwde/build_maps.py --map adventure_map            # write the map
```

- **Sources:** pinned in the script. They are the Hyrule Warriors Helper's `v2` data and art at
  `HWH_SHA`, SBAllen's GameFAQs pages at fixed Wayback timestamps, and Zelda Wiki's Item Card
  table. Downloads are cached in `.cache/` (git-ignored); delete it to fetch again.
- **`corrections.json`:** fixes on top of the Helper's data, each with the source that settles it:
  `removeReward`, `addReward`, `addCard`, `note` (shown on the square), and `accept` (a report line
  that's been looked into and is written up in docs/hwde.md).
- **`ids/<map>.txt`:** every id the map has ever had. Progress is stored under them, so the script
  stops if one would disappear; `--allow-removed` overrides that, for a real removal only.

**Adding a map:**
1. Add it to `MAPS` (the Helper's id, its name, SBAllen's page and a Wayback timestamp, and its
   word in Zelda Wiki's card table).
2. Run `--check`, and settle each report line in `corrections.json`.
3. Write it.
4. Add a line to `Games.kt`, and its counts to `HwdeDatasetTest`.

docs/hwde.md has the sources, the licences and how the art lines up.
