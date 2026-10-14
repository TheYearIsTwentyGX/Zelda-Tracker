"""Shrine-state detection from BotW Switch map screenshots (1920x1080, min zoom).

Pipeline:
  1. detect_diamonds(): find shrine-diamond outlines by template matching a
     ring template against a "icon blue" mask.
  2. register(): fit u = s*x + tx, v = s*z + ty (world -> screen) by Hough
     voting over translation for a range of scales, then least squares on
     inliers. With a known scale only translation is searched.
  3. classify_spot(): look at each projected shrine position and decide
     Completed / Not Done / Unsure / Not present.
  4. merge(): combine per-screenshot results.
"""
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np

# --- categories -------------------------------------------------------------
COMPLETED = "Completed"
NOT_DONE = "Not Done"
UNSURE = "Unsure"
ABSENT = "Not present"

# Half-diagonal of the shrine diamond, ring thickness (px at minimum zoom).
R_OUTER = 12.5
R_INNER = 9.0

# Shrine silhouette template (see build_template.py), radius in px.
TEMPLATE_R = 16
TEMPLATE_PATH = str(Path(__file__).parent / "data" / "shrine_shape.png")
TOWER_TEMPLATE_PATH = str(Path(__file__).parent / "data" / "tower_shape.png")
_template = None

# Map viewport: inside the frame, below the top glyph strip, above the bottom
# button bar. Anything projected outside is "not in view".
VIEW = (236, 126, 1704, 920)  # x0, y0, x1, y1 (icons dim past these y)
# A spot is only judged if its whole icon footprint is inside VIEW.
VIEW_MARGIN = 14
# Fixed HUD elements that sit over the map. (The cursor and its
# "Stamp/Map pin" label move; they are caught as white/blue clutter instead.)
HUD_RECTS = [
    (60, 770, 240, 950),     # completion %, clock, temperature
    (1540, 880, 1800, 950),  # Sheikah Sensor widget
]


def hsv(img):
    return cv2.cvtColor(img, cv2.COLOR_BGR2HSV)


def masks(img):
    """Per-pixel colour classes used throughout."""
    h, s, v = cv2.split(hsv(img))
    h = h.astype(np.int16)
    s = s.astype(np.int16)
    v = v.astype(np.int16)
    m = {}
    # Saturated light-blue of icon outlines / glyphs (shrines, towers, beasts).
    m["blue"] = (h >= 88) & (h <= 112) & (s > 110) & (v > 170)
    # Tower icons are paler; how pale varies between saves (S ~105..150).
    m["tower"] = (h >= 88) & (h <= 112) & (s > 60) & (v > 200)
    # Softer blue: glow halo around icons. Water is darker and greyer.
    m["glow"] = (h >= 88) & (h <= 115) & (s > 90) & (v > 120)
    # Orange glyph fill of an unfinished shrine.
    m["orange"] = (h >= 8) & (h <= 30) & (s > 80) & (v > 170)
    # Cream/tan location icons (stables, villages, castle, labs).
    m["tan"] = (h >= 12) & (h <= 30) & (s > 45) & (s < 150) & (v > 215)
    # White / near-white: tower glyphs, cursor label, pin glyphs, horse.
    m["white"] = (s < 60) & (v > 235)
    # Purple pins and yellow stamps / target rings.
    m["purple"] = (h >= 120) & (h <= 160) & (s > 90) & (v > 150)
    m["yellow"] = (h >= 22) & (h <= 38) & (s > 150) & (v > 200)
    # Red map pins; brighter than Eldin's lava overlay.
    m["red"] = ((h <= 6) | (h >= 172)) & (s > 150) & (v > 230)
    return m


def l1_grid(r):
    y, x = np.mgrid[-r:r + 1, -r:r + 1]
    return np.abs(x) + np.abs(y)


def ring_template(r=16):
    d = l1_grid(r)
    t = np.zeros(d.shape, np.float32)
    t[(d >= R_INNER) & (d <= R_OUTER)] = 1.0
    return t


@dataclass
class Detection:
    x: float
    y: float
    score: float


def detect_diamonds(img, thresh=0.55):
    """Candidate shrine diamonds (both colours share the blue outline)."""
    blue = masks(img)["blue"].astype(np.float32)
    t = ring_template()
    res = cv2.matchTemplate(blue, t, cv2.TM_CCOEFF_NORMED)
    r = t.shape[0] // 2
    # Non-maximum suppression.
    dil = cv2.dilate(res, np.ones((15, 15), np.uint8))
    ys, xs = np.where((res >= dil) & (res >= thresh))
    dets = [Detection(x + r, y + r, float(res[y, x])) for y, x in zip(ys, xs)]
    return [d for d in dets if in_view(d.x, d.y)]


def detect_towers(img, thresh=0.6):
    """Sheikah Tower icons. The template is centred on the tower's world
    position, so a match location is directly comparable to a projection."""
    t = cv2.imread(TOWER_TEMPLATE_PATH, cv2.IMREAD_GRAYSCALE)
    if t is None:
        return []
    t = t.astype(np.float32) / 255
    blue = masks(img)["tower"].astype(np.float32)
    res = cv2.matchTemplate(blue, t, cv2.TM_CCOEFF_NORMED)
    r = t.shape[0] // 2
    dil = cv2.dilate(res, np.ones((15, 15), np.uint8))
    ys, xs = np.where((res >= dil) & (res >= thresh))
    dets = [Detection(x + r, y + r, float(res[y, x])) for y, x in zip(ys, xs)]
    return [d for d in dets if in_view(d.x, d.y)]


def in_view(u, v, margin=0):
    x0, y0, x1, y1 = VIEW
    if not (x0 + margin <= u <= x1 - margin and y0 + margin <= v <= y1 - margin):
        return False
    return True


def under_hud(u, v, pad=14):
    for x0, y0, x1, y1 in HUD_RECTS:
        if x0 - pad <= u <= x1 + pad and y0 - pad <= v <= y1 + pad:
            return True
    return False


# --- registration -----------------------------------------------------------
@dataclass
class Registration:
    s: float
    tx: float
    ty: float
    inliers: int
    residuals: np.ndarray = field(repr=False)

    def project(self, x, z):
        return self.s * x + self.tx, self.s * z + self.ty

    @property
    def rms(self):
        return float(np.sqrt(np.mean(self.residuals ** 2))) if len(self.residuals) else float("nan")


def _vote(groups, s, bin_px=4):
    ts = []
    for dets, world in groups:
        if not len(dets) or not len(world):
            continue
        d = np.array([[p.x, p.y] for p in dets])
        w = np.asarray(world) * s
        ts.append((d[:, None, :] - w[None, :, :]).reshape(-1, 2))
    if not ts:
        return 0, np.zeros(2)
    t = np.concatenate(ts)
    q = np.round(t / bin_px).astype(np.int64)
    keys, counts = np.unique(q, axis=0, return_counts=True)
    # Smooth by summing the 3x3 neighbourhood of bins.
    lut = {tuple(k): c for k, c in zip(keys, counts)}
    best, best_k = -1, None
    for k, c in zip(keys, counts):
        tot = sum(lut.get((k[0] + i, k[1] + j), 0) for i in (-1, 0, 1) for j in (-1, 0, 1))
        if tot > best:
            best, best_k = tot, k
    return best, best_k * bin_px


def _match(groups, s, t, tol):
    """Nearest-neighbour pairs (world, detection) within tol, per class."""
    W, D = [], []
    for dets, world in groups:
        if not len(dets) or not len(world):
            continue
        d = np.array([[p.x, p.y] for p in dets])
        w = np.asarray(world)
        dist = np.linalg.norm(d[:, None, :] - (w * s + t)[None, :, :], axis=2)
        j = dist.argmin(axis=1)
        dmin = dist[np.arange(len(d)), j]
        pairs = {}  # one detection per world point
        for i in np.where(dmin < tol)[0]:
            if j[i] not in pairs or dmin[i] < dmin[pairs[j[i]]]:
                pairs[j[i]] = i
        W += [w[k] for k in pairs]
        D += [d[i] for i in pairs.values()]
    return np.array(W).reshape(-1, 2), np.array(D).reshape(-1, 2)


def _refine(groups, s, t, fix_scale, tol=6.0, iters=4):
    for _ in range(iters):
        W, D = _match(groups, s, t, tol)
        if len(W) < 2:
            break
        if fix_scale:
            t = (D - W * s).mean(axis=0)
        else:
            # Least squares for shared s and per-axis t.
            A = np.zeros((2 * len(W), 3))
            A[0::2, 0] = W[:, 0]; A[0::2, 1] = 1
            A[1::2, 0] = W[:, 1]; A[1::2, 2] = 1
            b = D.reshape(-1)
            sol, *_ = np.linalg.lstsq(A, b, rcond=None)
            s, t = sol[0], sol[1:]
    W, D = _match(groups, s, t, tol)
    res = np.linalg.norm(D - (W * s + t), axis=1) if len(W) else np.zeros(0)
    return s, t, res


MIN_ANCHORS = 3


def register(groups, scale=None, scale_range=(0.12, 0.16), step=0.0005):
    """groups: [(detections, world_xz), ...], one per icon class (shrines,
    towers) so a shrine detection is only ever paired with a shrine.
    Returns None if fewer than MIN_ANCHORS icons agree."""
    groups = [(d, np.asarray(w, float)) for d, w in groups]
    scales = [scale] if scale else np.arange(scale_range[0], scale_range[1], step)
    best = (-1, None, None)
    for s in scales:
        votes, t = _vote(groups, s)
        if votes > best[0]:
            best = (votes, s, t)
    votes, s, t = best
    if votes is None or votes < MIN_ANCHORS:
        return None
    s, t, res = _refine(groups, s, t.astype(float), fix_scale=scale is not None)
    if len(res) < MIN_ANCHORS:
        return None
    return Registration(float(s), float(t[0]), float(t[1]), len(res), res)


def register_image(img, shrines, towers, scale=None):
    """Detect icons in a screenshot and register it. Returns (reg, shrine
    detections, tower detections); reg is None when it cannot be placed."""
    sd = detect_diamonds(img)
    td = detect_towers(img)
    groups = [(sd, [(s["x"], s["z"]) for s in shrines]),
              (td, [(t["x"], t["z"]) for t in towers])]
    return register(groups, scale=scale), sd, td


# --- classification ---------------------------------------------------------
@dataclass
class SpotResult:
    category: str
    confidence: float
    reason: str
    ring: float = 0.0
    u: float = 0.0
    v: float = 0.0


def ring_score(m, u, v, search=3):
    """Best fraction of ring pixels that are icon-blue near (u, v), and where."""
    blue = m["blue"]
    r = 13
    d = l1_grid(r)
    ring = (d >= R_INNER) & (d <= R_OUTER)
    best = (0.0, int(round(u)), int(round(v)))
    H, W = blue.shape
    for dy in range(-search, search + 1):
        for dx in range(-search, search + 1):
            cx, cy = int(round(u)) + dx, int(round(v)) + dy
            if cx - r < 0 or cy - r < 0 or cx + r >= W or cy + r >= H:
                continue
            patch = blue[cy - r:cy + r + 1, cx - r:cx + r + 1]
            f = patch[ring].mean()
            if f > best[0]:
                best = (float(f), cx, cy)
    return best


def band(mask, cx, cy, lo, hi):
    """Fraction of mask pixels at L1 distance lo..hi from (cx, cy)."""
    r = int(hi) + 1
    d = l1_grid(r)
    sel = (d >= lo) & (d <= hi)
    return float(mask[cy - r:cy + r + 1, cx - r:cx + r + 1][sel].mean())


def shape_score(m, cx, cy, search=2):
    """NCC of the icon-coloured mask against the shrine silhouette template."""
    global _template
    if _template is None:
        _template = cv2.imread(TEMPLATE_PATH, cv2.IMREAD_GRAYSCALE).astype(np.float32) / 255
    r = TEMPLATE_R
    icon = (m["blue"] | m["orange"]).astype(np.float32)
    k = r + search
    patch = icon[cy - k:cy + k + 1, cx - k:cx + k + 1]
    if patch.shape != (2 * k + 1, 2 * k + 1):
        return 0.0
    res = cv2.matchTemplate(patch, _template, cv2.TM_CCOEFF_NORMED)
    return float(np.nan_to_num(res).max())


def glyph_colour(m, cx, cy):
    """(blue fraction, orange fraction) inside the diamond, inside the ring."""
    r = 8
    d = l1_grid(r)
    inner = d <= 7
    sl = (slice(cy - r, cy + r + 1), slice(cx - r, cx + r + 1))
    return float(m["blue"][sl][inner].mean()), float(m["orange"][sl][inner].mean())


def clutter(m, u, v, r=14):
    """Fraction of the icon footprint covered by non-terrain colours."""
    cx, cy = int(round(u)), int(round(v))
    d = l1_grid(r)
    foot = d <= r
    sl = (slice(cy - r, cy + r + 1), slice(cx - r, cx + r + 1))
    out = {}
    for k in ("glow", "tan", "white", "purple", "yellow", "red"):
        out[k] = float(m[k][sl][foot].mean())
    return out


SHAPE_HI = 0.60     # silhouette clearly a shrine icon
SHAPE_LO = 0.35     # below this there is no shrine-like shape
RING_LO = 0.25      # some outline visible
OUTER_BLUE_MAX = 0.40  # more blue than this around it: a bigger blue icon on top
CLUTTER_MAX = 0.03
STACK_PX = 8        # shrines closer than this draw on top of each other


def classify_spot(m, u, v, stacked_with=None):
    if not in_view(u, v):
        return SpotResult(UNSURE, 0.0, "not in view", u=u, v=v)
    if not in_view(u, v, margin=VIEW_MARGIN):
        return SpotResult(UNSURE, 0.0, "at screen edge", u=u, v=v)
    if under_hud(u, v):
        return SpotResult(UNSURE, 0.0, "under HUD", u=u, v=v)
    ring, cx, cy = ring_score(m, u, v)
    shape = shape_score(m, cx, cy)
    outer = band(m["blue"], cx, cy, 14, 18)
    if shape >= SHAPE_HI and outer <= OUTER_BLUE_MAX:
        b, o = glyph_colour(m, cx, cy)
        conf = min(1.0, 0.5 + (shape - SHAPE_HI) / (2 * (0.85 - SHAPE_HI)))
        detail = f"shape {shape:.2f}; glyph blue {b:.2f} orange {o:.2f}"
        if o > 0.2 and o > 2 * b:
            cat = NOT_DONE
        elif b > 0.2 and b > 2 * o:
            cat = COMPLETED
        else:
            return SpotResult(UNSURE, 0.3, "icon present, glyph colour unclear; " + detail, ring, u, v)
        if stacked_with:
            return SpotResult(UNSURE, 0.3, f"{cat} icon, but stacked with {stacked_with}; " + detail, ring, u, v)
        return SpotResult(cat, round(conf, 2), detail, ring, u, v)
    if shape >= SHAPE_LO or ring >= RING_LO:
        why = "bigger blue icon on top" if outer > OUTER_BLUE_MAX else "partial outline"
        return SpotResult(UNSURE, 0.4, f"{why} (shape {shape:.2f}, ring {ring:.2f}, outer blue {outer:.2f})",
                          ring, u, v)
    c = clutter(m, u, v)
    worst = max(c, key=c.get)
    if c[worst] > CLUTTER_MAX:
        return SpotResult(UNSURE, round(min(1.0, c[worst] / 0.2), 2),
                          f"covered: {worst} {c[worst]:.2f}", ring, u, v)
    conf = 1.0 - c[worst] / CLUTTER_MAX
    return SpotResult(ABSENT, round(0.5 + 0.5 * conf, 2),
                      f"clear spot (max clutter {worst} {c[worst]:.3f})", ring, u, v)


RANK = {COMPLETED: 3, NOT_DONE: 3, ABSENT: 2, UNSURE: 1}


def merge(results):
    """Combine one shrine's per-screenshot SpotResults."""
    seen = [r for r in results if r.reason not in ("not in view", "at screen edge", "under HUD")]
    if not seen:
        why = "at screen edge" if any(r.reason == "at screen edge" for r in results) else "not visible"
        return SpotResult(UNSURE, 0.0, f"{why} in every screenshot")
    icons = [r for r in seen if r.category in (COMPLETED, NOT_DONE)]
    if icons:
        cats = {r.category for r in icons}
        best = max(icons, key=lambda r: r.confidence)
        if len(cats) > 1:
            return SpotResult(UNSURE, 0.2, "screenshots disagree on icon colour")
        return best
    absent = [r for r in seen if r.category == ABSENT]
    if absent and not [r for r in seen if r.category == UNSURE and r.ring >= RING_LO]:
        return max(absent, key=lambda r: r.confidence)
    return max(seen, key=lambda r: r.confidence)
