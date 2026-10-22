#!/usr/bin/env python3
"""Lists likely `sameAs` link candidates: rows in two checklist files of one game that may be the same thing.

A heuristic, for a person to check, not a list to apply: a dungeon step and its Heart Container, say,
are found but aren't the same event. It finds
- the same name (or tier name) in two files;
- a row's reward (or a tier's) naming a row or tier of another file;
- a Piece of Heart or Heart Container row and a row of another file that gives one, in the same area or
  sharing most of its name.

Usage: scripts/find-link-candidates.py [files dir] [games to skip, comma-separated]
The defaults are the app's bundled files and the games already linked or with no checklists. Prints
Markdown, one list per game, as docs/checklist-trackers.md shows it.
"""
import json, glob, os, re, sys, collections
root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'shared/src/commonMain/composeResources/files')
skip = set((sys.argv[2] if len(sys.argv) > 2 else 'ss,botw,totk,hwde,mc').split(','))
GENERIC = {'piece of heart', 'heart container', 'rupees', 'gold rupee', 'silver rupee', 'red rupee', 'blue rupee', 'green rupee', 'empty bottle', 'bottle'}
STOP = set('the a an of in on at to and with by from for its it his her one two first second third level piece heart container quest'.split())
def norm(s): return re.sub(r'[^a-z0-9 ]+', ' ', (s or '').lower()).strip()
def words(s): return {w for w in norm(s).split() if w not in STOP and len(w) > 2}
for gdir in sorted(glob.glob(f'{root}/*/')):
    game = os.path.basename(gdir.rstrip('/'))
    if game in skip: continue
    rows = []
    for f in sorted(glob.glob(gdir + '*.json')):
        d = json.load(open(f))
        if 'sections' not in d: continue
        stem = os.path.basename(f)[:-5]
        for s in d['sections']:
            for it in s['items']:
                rows.append((stem, it))
    if len({r[0] for r in rows}) < 2: continue
    cands = []
    byname = collections.defaultdict(list)
    for stem, it in rows:
        byname[norm(it['name'])].append((stem, it['id']))
        for t in it.get('tiers', []): byname[norm(t['name'])].append((stem, f"{it['id']}@{it['tiers'].index(t)+1}"))
    for name, refs in byname.items():
        files = {r[0] for r in refs}
        if len(files) > 1 and name not in GENERIC and name and not re.fullmatch(r'(lv|level|tier|stage)\s*\d+', name):
            cands.append(('same name', name, refs))
    # reward naming another file's row
    for stem, it in rows:
        rewards = [(it['id'], it.get('reward'))] + [(f"{it['id']}@{i+1}", t.get('reward')) for i, t in enumerate(it.get('tiers', []))]
        for ref, rw in rewards:
            parts = re.split(r',| and |;', rw or '')
            if len(parts) > 3: continue  # a list of what's unlocked, not one thing given
            for part in parts:
                n = norm(re.sub(r'\(.*?\)', '', part))
                n = re.sub(r'^(a|an|the|\d+)\s+', '', n)
                if not n or n in GENERIC or len(n) < 4: continue
                for s2, id2 in byname.get(n, []):
                    if s2 != stem: cands.append(('reward names a row', n, [(stem, ref), (s2, id2)]))
    # heart pieces / containers
    heart_files = [s for s in {r[0] for r in rows} if 'heart' in s]
    for hstem in heart_files:
        for _, h in [r for r in rows if r[0] == hstem]:
            hw = words(h['name']) | words(h.get('area'))
            kind = 'heart container' if re.search(r'container|^hc_', (h.get('reward') or '') + ' ' + h['id'], re.I) else 'piece of heart|heart piece'
            best = None
            for stem, it in rows:
                if stem == hstem: continue
                texts = [(it['id'], it.get('reward'), it['name'])] + [(f"{it['id']}@{i+1}", t.get('reward'), t['name']) for i, t in enumerate(it.get('tiers', []))]
                for ref, rw, nm in texts:
                    if not rw or not re.search(kind, rw.lower()): continue
                    ow = words(it['name']) | words(it.get('area')) | words(nm)
                    same_area = bool(h.get('area') and it.get('area') and norm(h['area']) == norm(it['area']))
                    shared = hw & ow
                    score = (2 if same_area else 0) + len(shared) / max(1, len(hw | ow))
                    if (same_area or (len(shared) >= 2 and score >= 0.4)) and (best is None or score > best[0]):
                        best = (score, f"{h['name']} ~ {it['name']}", [(hstem, h['id']), (stem, ref)])
            if best: cands.append(('heart reward', best[1], best[2]))
    # One line per set of rows, with every reason found for it.
    merged = {}
    for kind, what, refs in cands:
        refs = [r for r in refs if not any(o != r and o[0] == r[0] and o[1].split('@')[0] == r[1] for o in refs)]
        if len({a for a, _ in refs}) < 2: continue
        key = tuple(sorted(f"{a}:{b}" for a, b in refs))
        merged.setdefault(key, []).append(kind)
    # An untiered ref beside the same item's tier for the same other side is the same candidate.
    for key in list(merged):
        for other in list(merged):
            if other != key and len(other) == len(key) and all(a == b or b.startswith(a + '@') for a, b in zip(key, other)):
                merged[other] = merged[other] + merged.pop(key, [])
                break
    if merged:
        print(f'**{game.upper()}** ({len(merged)})')
        print()
        for key, kinds in merged.items():
            print(f"- {' ↔ '.join(f'`{k}`' for k in key)} ({', '.join(dict.fromkeys(kinds))})")
        print()
