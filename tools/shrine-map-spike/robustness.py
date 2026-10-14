"""Robustness experiments. Prints a plain-text report.

    python robustness.py ALL_NORTH.jpg ALL_SOUTH.jpg PARTIAL_NORTH.jpg

1. Anchor starvation: register the partial-save shot using only k randomly
   chosen shrine icons (plus every spurious detection), with and without
   towers, free scale vs fixed scale.
2. Simulated panning: shift the map content under the fixed HUD frame and
   check registration and classification still agree.
3. Real pan: shrines judged in both all-complete shots must agree.
4. Decoys: korok seed spots (never drawn on the map) must not come out
   Completed / Not Done.
"""
import json
import sys
from pathlib import Path

import cv2
import numpy as np

import shrinemap as sm

HERE = Path(__file__).parent
FIXED_SCALE = 0.135
rng = np.random.default_rng(0)


def proj_err(reg, ref, pts):
    a = np.array([reg.project(x, z) for x, z in pts])
    b = np.array([ref.project(x, z) for x, z in pts])
    return float(np.abs(a - b).max())


def starvation(img, shrines, towers):
    sw = [(s["x"], s["z"]) for s in shrines]
    tw = [(t["x"], t["z"]) for t in towers]
    ref, sd, td = sm.register_image(img, shrines, towers)
    # Split detections into true shrine icons and spurious ones.
    P = np.array([ref.project(x, z) for x, z in sw])
    true, false = [], []
    for d in sd:
        (true if np.hypot(*(P - [d.x, d.y]).T).min() < 3 else false).append(d)
    print(f"partial shot: {len(true)} true shrine detections, {len(false)} spurious, {len(td)} towers")
    allpts = sw + tw
    print("  k shrines | towers | scale | success (proj. error < 2 px everywhere, 200 trials)")
    for use_towers in (False, True):
        for scale in (None, FIXED_SCALE):
            for k in (1, 2, 3, 4, 6, 10):
                ok = 0
                for _ in range(200):
                    pick = [true[i] for i in rng.choice(len(true), k, replace=False)]
                    groups = [(pick + false, sw), (td if use_towers else [], tw)]
                    reg = sm.register(groups, scale=scale)
                    ok += reg is not None and proj_err(reg, ref, allpts) < 2
                print(f"  {k:>9} | {'yes' if use_towers else 'no':>6} | {'fixed' if scale else 'free':>5} | {ok / 2:.0f}%")


def shift_view(img, dx, dy):
    """Move map content under the static frame; uncovered area goes dark."""
    x0, y0, x1, y1 = 200, 90, 1720, 990
    out = img.copy()
    region = img[y0:y1, x0:x1]
    M = np.float32([[1, 0, dx], [0, 1, dy]])
    out[y0:y1, x0:x1] = cv2.warpAffine(region, M, (x1 - x0, y1 - y0), borderValue=(40, 25, 10))
    return out


def panning(img, shrines, towers):
    m0 = sm.masks(img)
    ref, *_ = sm.register_image(img, shrines, towers)
    base = [sm.classify_spot(m0, *ref.project(s["x"], s["z"])) for s in shrines]
    print("\nsimulated pan of partial shot (content shifted under the HUD):")
    print("  dx,dy | scale | dt error px | judged in both | category agreement")
    for dx, dy in [(0, 37), (61, -23), (-90, 50), (150, 0), (-150, -120), (7, 233), (-233, 101)]:
        img2 = shift_view(img, dx, dy)
        m2 = sm.masks(img2)
        for scale in (None, FIXED_SCALE):
            reg, *_ = sm.register_image(img2, shrines, towers, scale=scale)
            if reg is None:
                print(f"  {dx},{dy} | {'fixed' if scale else 'free'} | FAILED")
                continue
            err = np.hypot(reg.tx - (ref.tx + dx), reg.ty - (ref.ty + dy))
            both = agree = 0
            for s, b in zip(shrines, base):
                u, v = reg.project(s["x"], s["z"])
                r = sm.classify_spot(m2, u, v)
                skip = ("not in view", "at screen edge", "under HUD")
                # ignore spots whose footprint now overlaps the dark fill
                filled = not (200 + max(dx, 0) + 20 < u < 1720 + min(dx, 0) - 20
                              and 90 + max(dy, 0) + 20 < v < 990 + min(dy, 0) - 20)
                if b.reason in skip or r.reason in skip or filled:
                    continue
                both += 1
                agree += b.category == r.category
            print(f"  {dx},{dy} | {'fixed' if scale else 'free'} | {err:.2f} | {both} | {agree}/{both}")


def real_pan(north, south, shrines, towers):
    res = []
    for img in (north, south):
        reg, *_ = sm.register_image(img, shrines, towers)
        m = sm.masks(img)
        res.append([sm.classify_spot(m, *reg.project(s["x"], s["z"])) for s in shrines])
    skip = ("not in view", "at screen edge", "under HUD")
    both = [(s["name"], a, b) for s, a, b in zip(shrines, *res) if a.reason not in skip and b.reason not in skip]
    agree = sum(a.category == b.category for _, a, b in both)
    print(f"\nreal pan (all-complete north vs south, {abs(675):d} px apart): {agree}/{len(both)} "
          f"shrines judged in both shots agree")
    for n, a, b in both:
        if a.category != b.category:
            print(f"  {n}: {a.category} ({a.reason}) vs {b.category} ({b.reason})")


def decoys(imgs, shrines, towers):
    koroks = json.load(open(HERE / "data" / "koroks.json"))
    print("\ndecoys: korok seed spots >= 25 px from any shrine/tower")
    for name, img in imgs:
        reg, *_ = sm.register_image(img, shrines, towers)
        m = sm.masks(img)
        icons = np.array([reg.project(s["x"], s["z"]) for s in shrines] +
                         [reg.project(t["x"], t["z"]) for t in towers])
        counts = {}
        bad = []
        for x, z in koroks:
            u, v = reg.project(x, z)
            if np.hypot(*(icons - [u, v]).T).min() < 25:
                continue
            r = sm.classify_spot(m, u, v)
            if r.reason in ("not in view", "at screen edge", "under HUD"):
                continue
            counts[r.category] = counts.get(r.category, 0) + 1
            if r.category in (sm.COMPLETED, sm.NOT_DONE):
                bad.append((round(u), round(v), r.reason))
        print(f"  {name}: {counts}; false icons: {bad[:5]}")


def main(all_north, all_south, partial):
    c = json.load(open(HERE / "data" / "coords.json"))
    shrines, towers = c["shrines"], c["towers"]
    n, s, p = (cv2.imread(x) for x in (all_north, all_south, partial))
    starvation(p, shrines, towers)
    panning(p, shrines, towers)
    real_pan(n, s, shrines, towers)
    decoys([("all-complete north", n), ("all-complete south", s), ("partial north", p)], shrines, towers)


if __name__ == "__main__":
    main(*sys.argv[1:])
