"""Turn a live scan's debug session (the app's "Save frames") into a test clip for KorokClipGoldenTest.

A session holds every 5th analysed frame as grey JPEGs (frame-NNNN.jpg), state.jsonl and session.json.
This copies the frames, renumbered, to shared/src/jvmTest/resources/korok-clips/<name>/frames/ and writes
clip.json; write expected.json by hand afterwards (see README.md).

Usage: python3 from_session.py SESSION_DIR NAME
"""
import json
import os
import shutil
import sys

session, name = sys.argv[1], sys.argv[2]
out = os.path.join(os.path.dirname(__file__), "../../shared/src/jvmTest/resources/korok-clips", name)
os.makedirs(os.path.join(out, "frames"), exist_ok=True)
frames = sorted(f for f in os.listdir(session) if f.startswith("frame-") and f.endswith(".jpg"))
for i, f in enumerate(frames):
    shutil.copy(os.path.join(session, f), os.path.join(out, "frames", f"{i:04d}.jpg"))
camera = {}
if os.path.exists(os.path.join(session, "session.json")):
    camera = json.load(open(os.path.join(session, "session.json")))
meta = {"source": os.path.basename(os.path.normpath(session)), "sourceFps": 0, "step": 5, "frames": len(frames), "width": 0, "camera": camera.get("camera", "")}
with open(os.path.join(out, "clip.json"), "w") as fh:
    json.dump(meta, fh, indent=2)
    fh.write("\n")
print(f"{len(frames)} frames -> {out}")
