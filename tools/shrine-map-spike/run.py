"""Classify every shrine from one or more minimum-zoom map screenshots.

    python run.py --out out/all-complete shot1.jpg shot2.jpg
    python run.py --out out/x --scale 0.135 shot.jpg   # translation-only fit

Writes, into --out:
  <shot>-annotated.jpg  every projected shrine, coloured by category
  results.md / results.csv  one row per shrine (136)
  registration.json  fit + residuals per screenshot
"""
import argparse
import csv
import json
from pathlib import Path

import cv2
import numpy as np

import shrinemap as sm

HERE = Path(__file__).parent
COLOURS = {  # BGR
    sm.COMPLETED: (60, 200, 60),
    sm.NOT_DONE: (0, 140, 255),
    sm.UNSURE: (0, 230, 255),
    sm.ABSENT: (80, 80, 230),
}


def load_coords():
    c = json.load(open(HERE / "data" / "coords.json"))
    return c["shrines"], c["towers"]


def stacked_pairs(shrines, scale=0.135):
    """Shrines whose icons overlap almost exactly at minimum zoom."""
    out = {}
    for a in shrines:
        for b in shrines:
            if a is not b and np.hypot(a["x"] - b["x"], a["z"] - b["z"]) * scale < sm.STACK_PX:
                out[a["name"]] = b["name"]
    return out


def annotate(img, shrines, spots, reg, dets):
    out = img.copy()
    for d in dets:
        cv2.circle(out, (int(d.x), int(d.y)), 2, (255, 255, 255), -1)
    for s, r in zip(shrines, spots):
        if r.reason == "not in view":
            continue
        u, v = int(round(r.u)), int(round(r.v))
        col = COLOURS[r.category]
        cv2.circle(out, (u, v), 15, col, 2)
        label = s["name"]
        cv2.putText(out, label, (u + 16, v + 4), cv2.FONT_HERSHEY_SIMPLEX, 0.38, (0, 0, 0), 3, cv2.LINE_AA)
        cv2.putText(out, label, (u + 16, v + 4), cv2.FONT_HERSHEY_SIMPLEX, 0.38, col, 1, cv2.LINE_AA)
    y = 1040
    x = 20
    for cat, col in COLOURS.items():
        cv2.circle(out, (x + 8, y - 5), 8, col, 2)
        cv2.putText(out, cat, (x + 22, y), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA)
        x += 190
    cv2.putText(out, f"s={reg.s:.5f} t=({reg.tx:.1f},{reg.ty:.1f}) anchors={reg.inliers} rms={reg.rms:.2f}px",
                (x + 20, y), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA)
    return out


def contact_sheet(img, shrines, spots, per_row=12, r=26, zoom=2):
    """Zoomed crop of every in-view spot, grouped by category, for eyeballing."""
    order = [sm.NOT_DONE, sm.UNSURE, sm.ABSENT, sm.COMPLETED]
    items = [(order.index(sp.category), s["name"], sp) for s, sp in zip(shrines, spots)
             if sp.reason not in ("not in view", "at screen edge", "under HUD")]
    items.sort(key=lambda t: (t[0], t[1]))
    cell = 2 * r * zoom
    pad = cv2.copyMakeBorder(img, r, r, r, r, cv2.BORDER_CONSTANT)
    rows = (len(items) + per_row - 1) // per_row
    sheet = np.zeros((rows * (cell + 16), per_row * cell, 3), np.uint8)
    for i, (_, name, sp) in enumerate(items):
        u, v = int(round(sp.u)) + r, int(round(sp.v)) + r
        c = cv2.resize(pad[v - r:v + r, u - r:u + r], (cell, cell), interpolation=cv2.INTER_NEAREST)
        col = COLOURS[sp.category]
        cv2.rectangle(c, (0, 0), (cell - 1, cell - 1), col, 3)
        y, x = (i // per_row) * (cell + 16), (i % per_row) * cell
        sheet[y:y + cell, x:x + cell] = c
        cv2.putText(sheet, name[:16], (x + 2, y + cell + 12), cv2.FONT_HERSHEY_SIMPLEX, 0.4, col, 1, cv2.LINE_AA)
    return sheet


def run(paths, out_dir, scale=None, write=True):
    shrines, towers = load_coords()
    stacked = stacked_pairs(shrines)
    out_dir = Path(out_dir)
    if write:
        out_dir.mkdir(parents=True, exist_ok=True)
    per_shot = []
    regs = {}
    for p in paths:
        img = cv2.imread(str(p))
        if img is None:
            raise SystemExit(f"cannot read {p}")
        reg, dets, tdets = sm.register_image(img, shrines, towers, scale=scale)
        if reg is None:
            raise SystemExit(f"{p}: could not register (too few shrine/tower icons)")
        m = sm.masks(img)
        spots = []
        for s in shrines:
            u, v = reg.project(s["x"], s["z"])
            spots.append(sm.classify_spot(m, u, v, stacked.get(s["name"])))
        per_shot.append(spots)
        regs[Path(p).name] = {
            "scale": reg.s, "tx": reg.tx, "ty": reg.ty,
            "shrine_detections": len(dets), "tower_detections": len(tdets),
            "anchors_matched": reg.inliers, "rms_px": reg.rms,
            "max_px": float(reg.residuals.max()) if len(reg.residuals) else None,
        }
        if write:
            ann = annotate(img, shrines, spots, reg, dets + tdets)
            cv2.imwrite(str(out_dir / f"{Path(p).stem}-annotated.jpg"), ann,
                        [cv2.IMWRITE_JPEG_QUALITY, 80])
            cv2.imwrite(str(out_dir / f"{Path(p).stem}-spots.jpg"), contact_sheet(img, shrines, spots),
                        [cv2.IMWRITE_JPEG_QUALITY, 80])
    final = [sm.merge([shot[i] for shot in per_shot]) for i in range(len(shrines))]
    rows = []
    for i, (s, r) in enumerate(zip(shrines, final)):
        rows.append({
            "shrine": s["name"], "region": s["region"], "dlc": s["dlc"],
            "category": r.category, "confidence": r.confidence, "reason": r.reason,
            "per_shot": " | ".join(f"{shot[i].category} ({shot[i].reason})" for shot in per_shot),
        })
    if write:
        with open(out_dir / "results.csv", "w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0]))
            w.writeheader()
            w.writerows(rows)
        with open(out_dir / "results.md", "w") as f:
            counts = {}
            for r in rows:
                counts[r["category"]] = counts.get(r["category"], 0) + 1
            f.write("# Results\n\n" + ", ".join(f"**{k}**: {v}" for k, v in counts.items()) + "\n\n")
            f.write("| Shrine | Region | DLC | Category | Conf. | Reason |\n|---|---|---|---|---|---|\n")
            for r in rows:
                f.write(f"| {r['shrine']} | {r['region']} | {'yes' if r['dlc'] else ''} | {r['category']} "
                        f"| {r['confidence']:.2f} | {r['reason']} |\n")
        json.dump(regs, open(out_dir / "registration.json", "w"), indent=1)
    return rows, regs, per_shot


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("shots", nargs="+")
    ap.add_argument("--out", required=True)
    ap.add_argument("--scale", type=float, default=None,
                    help="fixed px per world unit (min zoom is 0.135); omit to fit it")
    a = ap.parse_args()
    rows, regs, _ = run(a.shots, a.out, a.scale)
    for k, v in regs.items():
        print(k, json.dumps(v))
    counts = {}
    for r in rows:
        counts[r["category"]] = counts.get(r["category"], 0) + 1
    print(counts)


if __name__ == "__main__":
    main()
