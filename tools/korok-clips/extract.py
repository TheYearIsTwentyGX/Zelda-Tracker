"""Extract a Korok-scan test clip from a phone video of the max-zoom BotW map.

Writes every STEP-th frame, scaled to WIDTH px wide and converted to grey (the scanner only uses
luma, like the camera's Y plane), as JPEGs plus clip.json, into
shared/src/jvmTest/resources/korok-clips/<name>/. Write expected.json by hand afterwards.

Usage: python3 extract.py VIDEO NAME [--step 6] [--width 960] [--quality 60]
Needs opencv-python-headless.
"""
import argparse
import json
import os

import cv2

ap = argparse.ArgumentParser()
ap.add_argument("video")
ap.add_argument("name")
ap.add_argument("--step", type=int, default=6)
ap.add_argument("--width", type=int, default=960)
ap.add_argument("--quality", type=int, default=60)
ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "../../shared/src/jvmTest/resources/korok-clips"))
a = ap.parse_args()

out = os.path.join(a.out, a.name)
frames = os.path.join(out, "frames")
os.makedirs(frames, exist_ok=True)
cap = cv2.VideoCapture(a.video)
fps = cap.get(cv2.CAP_PROP_FPS)
i = n = 0
total = 0
while True:
    ok, f = cap.read()
    if not ok:
        break
    if i % a.step == 0:
        g = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)
        g = cv2.resize(g, (a.width, round(g.shape[0] * a.width / g.shape[1])), interpolation=cv2.INTER_AREA)
        path = os.path.join(frames, f"{n:04d}.jpg")
        cv2.imwrite(path, g, [cv2.IMWRITE_JPEG_QUALITY, a.quality])
        total += os.path.getsize(path)
        n += 1
    i += 1
meta = {"source": os.path.basename(a.video), "sourceFps": round(fps, 3), "step": a.step, "frames": n, "width": a.width}
with open(os.path.join(out, "clip.json"), "w") as fh:
    json.dump(meta, fh, indent=2)
    fh.write("\n")
print(f"{n} frames, {total / 1e6:.2f} MB -> {out}")
