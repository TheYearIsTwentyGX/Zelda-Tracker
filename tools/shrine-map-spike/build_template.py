"""Build the icon silhouette templates from a calibration screenshot in which
the icons are known to be present (the all-complete save):

  data/shrine_shape.png  mean of (blue | orange) masks around clean shrine
                         icons, so it fits both Completed and Not Done
  data/tower_shape.png   mean tower-blue mask centred on each tower's projected
                         world position (the icon sits slightly above it)

    python build_template.py shot.jpg [shot2.jpg ...]

Only icons with nothing else nearby (no blue in the outer band) are used.
Registration here uses shrine diamonds alone, so towers are not needed yet.
"""
import json
import sys
from pathlib import Path

import cv2
import numpy as np

import shrinemap as sm


def main(*shots):
    c = json.load(open(Path(__file__).parent / "data" / "coords.json"))
    shrines, towers = c["shrines"], c["towers"]
    r = sm.TEMPLATE_R
    acc = {"shrine": [], "tower": []}
    for p in shots:
        img = cv2.imread(p)
        m = sm.masks(img)
        icon = (m["blue"] | m["orange"]).astype(np.float32)
        tower = m["tower"].astype(np.float32)
        reg = sm.register([(sm.detect_diamonds(img), [(s["x"], s["z"]) for s in shrines])])
        for s in shrines:
            u, v = reg.project(s["x"], s["z"])
            if not sm.in_view(u, v, margin=30):
                continue
            ring, cx, cy = sm.ring_score(m, u, v)
            if ring < 0.75 or sm.band(m["blue"], cx, cy, 14, 18) > 0.02:
                continue
            acc["shrine"].append(icon[cy - r:cy + r + 1, cx - r:cx + r + 1])
        for t in towers:
            u, v = reg.project(t["x"], t["z"])
            if not sm.in_view(u, v, margin=30):
                continue
            cx, cy = int(round(u)), int(round(v))
            acc["tower"].append(tower[cy - r:cy + r + 1, cx - r:cx + r + 1])
    for name, path in (("shrine", sm.TEMPLATE_PATH), ("tower", sm.TOWER_TEMPLATE_PATH)):
        t = np.mean(acc[name], axis=0)
        cv2.imwrite(path, np.round(t * 255).astype(np.uint8))
        print(f"{name}: averaged {len(acc[name])} icons -> {path}")


if __name__ == "__main__":
    main(*sys.argv[1:])
