#!/usr/bin/env python3
"""Flag sprites whose transparent pixels are not connected to the image border (punched holes).
Usage: sprite_holes.py [--fix] [ids...]   (default: every item sprite in the drawable dir)
--fix makes such interior regions opaque again (alpha 255), keeping the RGB that is stored under them.
Intended see-through gaps are listed in ALLOW with a max interior-hole size."""
import sys, os, numpy as np
from PIL import Image
from scipy import ndimage as nd
D = os.path.join(os.path.dirname(__file__), '..', 'composeApp/src/commonMain/composeResources/drawable')
SKIP = ('room_', 'surf_', 'bg_', 'wallpaper_', 'app_icon', 'splash')
TOL = 40          # px: tiny specks / gaps between legs at 256px are fine
ALLOW = {          # id -> max allowed interior px for intended see-through gaps
    "flower_crown": 9000,                       # the ring's middle
    "wildflower": 1600,                         # gaps between stems and ribbon
    "palette": 2200, "palette_blink": 1300, "palette_eat": 1300, "palette_happy": 1300,
    "palette_sleep": 1300, "palette_walk_left": 1300,  # painter's palette thumb hole
    "cat": 3400, "fox": 5800, "koala": 8400,    # gaps between ears and head
}

def holes(a):
    t = a < 128
    l, n = nd.label(t)
    border = set(np.unique(np.r_[l[0], l[-1], l[:, 0], l[:, -1]]))
    sizes = nd.sum(t, l, range(1, n + 1))
    return l, [(i + 1, int(s)) for i, s in enumerate(sizes) if i + 1 not in border]

# What the punched area really is (clock face / glass / sky); others are inpainted from the surrounding art.
FILL = {"wall_clock": (250, 244, 230), "window": (206, 230, 246), "picture_frame": (204, 228, 242), "fish_tank": (204, 236, 238)}

def fill_holes(arr, sid):
    """Everything not reachable from the border through (near-)transparent pixels becomes opaque.
    The RGB stored under the holes is garbage, so it is replaced by a fill colour or a diffusion inpaint."""
    a = arr[..., 3]
    outside = a < 250
    l, _ = nd.label(outside)
    border = np.unique(np.r_[l[0], l[-1], l[:, 0], l[:, -1]])
    inner = outside & ~np.isin(l, border)
    soft = inner & (a < 128)
    rgb = arr[..., :3].astype(float)
    if sid in FILL:
        yy = np.linspace(1.03, 0.94, arr.shape[0])[:, None, None]
        col = np.clip(np.array(FILL[sid], float)[None, None, :] * yy, 0, 255) * np.ones_like(rgb)
    else:
        col = rgb.copy(); known = ~soft; col[soft] = 0
        for _ in range(400):
            if known.all(): break
            w = nd.uniform_filter(known.astype(float), 3)
            v = np.stack([nd.uniform_filter(col[..., c] * known, 3) for c in range(3)], -1)
            grow = ~known & (w > 1e-6)
            col[grow] = v[grow] / w[grow][:, None]; known = known | grow
        for _ in range(30):
            sm = np.stack([nd.uniform_filter(col[..., c], 5) for c in range(3)], -1)
            col[soft] = sm[soft]
    t = np.where(soft, 0.0, a / 255.0)[..., None]   # soft rim keeps its art, blended over the fill
    out = np.where(inner[..., None], rgb * t + col * (1 - t), rgb)
    arr[..., :3] = np.clip(out, 0, 255).astype(np.uint8)
    arr[..., 3] = np.where(inner, 255, a)

def main():
    fix = '--fix' in sys.argv
    ids = [x for x in sys.argv[1:] if not x.startswith('--')]
    files = [f'{i}.webp' for i in ids] or sorted(f for f in os.listdir(D) if f.endswith('.webp') and not f.startswith(SKIP))
    bad = 0
    for f in files:
        im = Image.open(os.path.join(D, f))
        if im.mode != 'RGBA': continue
        arr = np.array(im)
        l, hs = holes(arr[..., 3])
        big = [(i, s) for i, s in hs if s > TOL]
        tot = sum(s for _, s in big)
        if tot > ALLOW.get(f[:-5], 0):
            bad += 1
            print(f'{f}: {len(big)} interior holes, {tot}px')
            if fix:
                fill_holes(arr, f[:-5])
                Image.fromarray(arr).save(os.path.join(D, f), lossless=True, quality=100)
    print(f'{bad} sprite(s) with interior holes of {len(files)}')
    sys.exit(1 if bad and not fix else 0)

if __name__ == '__main__':
    main()
