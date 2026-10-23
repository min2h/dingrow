"""Generates wallpaper/floor SURFACE overlays for each room art (theme x day/night).

For every room we hand-measure the back-wall planes and the floor plane (corner, baseboard
lines, horizon) in the 864x1152 room_*_3x4 art. A seamless procedural tile is perspective-
warped (homography per plane) into those planes, clipped by antialiased polygons minus the
art's fixtures (window, plant, shelf, built-in rug, moon, string lights, curtains), and lit
by the art's own low-frequency lighting (per-channel ratio vs the day art's mean surface
colour), so sun shafts, lamp glow, corner shading and night tint stay on top.
Output: surf_<theme>_<day|night>_<surfaceId>.webp (RGBA, same size as the room art), drawn
over the room image with the same ContentScale. Re-run: python3 tools/room_surfaces.py
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

D = os.path.join(os.path.dirname(__file__), "../composeApp/src/commonMain/composeResources/drawable")
W, H = 864, 1152
T = 512  # tile px

def line_y(p, q, x):
    return p[1] + (q[1] - p[1]) * (x - p[0]) / (q[0] - p[0])

def inter(p1, p2, p3, p4):
    a = np.array([[p2[0]-p1[0], p3[0]-p4[0]], [p2[1]-p1[1], p3[1]-p4[1]]], float)
    t = np.linalg.solve(a, [p3[0]-p1[0], p3[1]-p1[1]])[0]
    return (p1[0] + t*(p2[0]-p1[0]), p1[1] + t*(p2[1]-p1[1]))

def vp_on_horizon(p, q, hy):
    return (p[0] + (q[0]-p[0]) * (hy-p[1]) / (q[1]-p[1]), hy)

def homography(src, dst):
    A = []
    for (u, v), (x, y) in zip(src, dst):
        A += [[u, v, 1, 0, 0, 0, -x*u, -x*v, -x], [0, 0, 0, u, v, 1, -y*u, -y*v, -y]]
    _, _, vt = np.linalg.svd(np.array(A, float))
    return vt[-1].reshape(3, 3) / vt[-1][-1]

def apply(Hm, u, v):
    w = Hm[2,0]*u + Hm[2,1]*v + Hm[2,2]
    return (Hm[0,0]*u + Hm[0,1]*v + Hm[0,2]) / w, (Hm[1,0]*u + Hm[1,1]*v + Hm[1,2]) / w

def iso_plane(img_pts, ref, tile_px):
    """Homography unit-square->img, then rescale u so world is isotropic at `ref`, tile ~tile_px there."""
    Hm = homography([(0,0),(1,0),(1,1),(0,1)], img_pts)
    Hi = np.linalg.inv(Hm)
    u0, v0 = apply(Hi, *ref)
    e = 1e-4
    du = np.hypot(*np.subtract(apply(Hm, u0+e, v0), apply(Hm, u0, v0))) / e
    dv = np.hypot(*np.subtract(apply(Hm, u0, v0+e), apply(Hm, u0, v0))) / e
    # world coords (tiles): U = u*du/tile_px, V = v*dv/tile_px
    S = np.diag([du/tile_px, dv/tile_px, 1.0])
    return S @ Hi  # img -> tile coords (homogeneous)

# ---------- procedural seamless tiles ----------
def tile_stripe():
    im = Image.new("RGB", (T, T), (250, 241, 222)); d = ImageDraw.Draw(im)
    for i in range(4):
        x = i*T//4; d.rectangle([x, 0, x + T//8 - 1, T], fill=(184, 214, 196))
        d.line([x + T//8 + 6, 0, x + T//8 + 6, T], fill=(214, 228, 212), width=3)
    return im

def tile_starry():
    im = Image.new("RGB", (T, T), (40, 56, 96)); d = ImageDraw.Draw(im)
    rnd = random.Random(7)
    def star(cx, cy, r, col):
        pts = []
        for k in range(10):
            a = -math.pi/2 + k*math.pi/5; rr = r if k % 2 == 0 else r*0.45
            pts.append((cx + rr*math.cos(a), cy + rr*math.sin(a)))
        for ox in (-T, 0, T):
            for oy in (-T, 0, T):
                d.polygon([(x+ox, y+oy) for x, y in pts], fill=col)
    for _ in range(9): star(rnd.uniform(0, T), rnd.uniform(0, T), rnd.uniform(14, 24), (240, 200, 110))
    for _ in range(26): star(rnd.uniform(0, T), rnd.uniform(0, T), rnd.uniform(4, 7), (236, 214, 150))
    return im

def tile_herring():
    """True 45-degree herringbone, planks 4:1, exactly periodic in a T x T tile."""
    Wd = T / (4 * math.sqrt(2)); L = 4 * Wd
    yy, xx = np.mgrid[0:T, 0:T].astype(float) + 0.5
    # rotate pixel coords by -45deg into plank space (lattice v1=(W,W), v2=(L,-L))
    qx = (xx + yy) / math.sqrt(2); qy = (yy - xx) / math.sqrt(2)
    v1 = np.array([Wd, Wd]); v2 = np.array([L, -L]); Minv = np.linalg.inv(np.array([v1, v2]).T)
    a = Minv[0,0]*qx + Minv[0,1]*qy; b = Minv[1,0]*qx + Minv[1,1]*qy
    col = np.zeros(xx.shape + (3,)); edge = np.ones(xx.shape) * 9
    rng = np.random.RandomState(5); tones = rng.uniform(-16, 16, (64, 64, 2)); grain_ph = rng.uniform(0, 6, (64, 64, 2))
    done = np.zeros(xx.shape, bool)
    for di in range(-3, 4):
        for dj in range(-3, 4):
            i = np.floor(a) + di; j = np.floor(b) + dj
            rx = qx - (i*v1[0] + j*v2[0]); ry = qy - (i*v1[1] + j*v2[1])
            for k, (x0, x1, y0, y1) in enumerate([(0, L, 0, Wd), (0, Wd, Wd, Wd + L)]):
                inside = (rx >= x0) & (rx < x1) & (ry >= y0) & (ry < y1) & ~done
                if not inside.any(): continue
                d = np.minimum.reduce([rx - x0, x1 - rx, ry - y0, y1 - ry])
                ii = (i.astype(int) % 4); jj = np.zeros_like(ii)  # tile period = 4*v1 and 1*v2
                t = tones[ii, jj, k]; ph = grain_ph[ii, jj, k]
                along = rx if k == 0 else ry
                across = ry if k == 0 else rx
                g = 5*np.sin(across*0.9 + ph + 0.15*np.sin(along*0.05)) + 3*np.sin(along*0.02 + ph)
                c = np.stack([214 + t + g, 166 + t*0.9 + g*0.9, 112 + t*0.7 + g*0.6], -1)
                col[inside] = c[inside]; edge[inside] = d[inside]; done |= inside
    gap = np.clip((edge - 1.2) / 1.5, 0, 1)[..., None]
    col = col * gap + np.array([150, 104, 62]) * (1 - gap)
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8))

def tile_mint():
    im = Image.new("RGB", (T, T), (210, 214, 200)); d = ImageDraw.Draw(im)
    n = 4; s = T // n; g = 5
    cols = [(248, 238, 218), (178, 210, 192), (196, 222, 204)]
    rnd = random.Random(11)
    for i in range(n):
        for j in range(n):
            c = cols[0] if (i + j) % 2 == 0 else cols[1 + rnd.randint(0, 1)]
            d.rounded_rectangle([i*s + g//2, j*s + g//2, (i+1)*s - g//2 - 1, (j+1)*s - g//2 - 1], radius=6, fill=c)
    return im

TILES = {"wall_stripe": (tile_stripe, "wall", 1.0), "wall_starry": (tile_starry, "wall", 1.0),
         "floor_herringbone": (tile_herring, "floor", 1.0), "floor_mint_tile": (tile_mint, "floor", 1.0)}

# ---------- room geometry (864x1152 coords) ----------
def wall_plane(c_bot, far_bot, hy, h=400):
    vp = vp_on_horizon(c_bot, far_bot, hy) if abs(far_bot[1]-c_bot[1]) > 1 else None
    c_top = (c_bot[0], c_bot[1]-h)
    if vp is None: f_top = (far_bot[0], far_bot[1]-h)
    else: f_top = (far_bot[0], line_y(c_top, vp, far_bot[0]))
    return [c_bot, far_bot, f_top, c_top]

def floor_plane(A, C, B, hy):
    vl = vp_on_horizon(A, C, hy); vr = vp_on_horizon(B, C, hy)
    Dp = inter(A, vr, B, vl)
    return [C, B, Dp, A]

ROOMS = {
  "wood": dict(hy=226, lf_chroma=("wall", "floor"), solid_floor=[("ellipse",(792,498,54,52))], solid_wall=[("ellipse",(678,185,158,180))],
    walls=[  # (polygon, plane pts, ref point)
      ([(0,0),(416,0),(416,399),(0,488)], wall_plane((416,399),(0,488),226), (300,250)),
      ([(415,0),(864,0),(864,463),(415,399)], wall_plane((415,399),(864,463),226), (560,250)),
    ],
    floor=([(0,541),(416,432),(864,501),(864,1152),(0,1152)], floor_plane((0,541),(416,432),(864,501),226), (432,850)),
    keep_wall=[("ellipse",(677,178,160,160)), ("poly",[(712,330),(864,300),(864,470),(712,470)])],
    keep_floor=[("poly",[(426,505),(570,468),(752,510),(700,600),(440,568)]), ("poly",[(730,440),(864,440),(864,560),(730,560)])],
  ),
  "mint": dict(hy=360, solid_wall=[("ellipse",(429,365,173,182))],
    walls=[([(0,0),(864,0),(864,689),(0,689)], [(0,689),(864,689),(864,289),(0,289)], (432,450))],
    floor=([(0,729),(864,729),(864,1152),(0,1152)], floor_plane((0,729),(432.001,729),(864,729.0001),360) if False else
           [(0,729),(864,729),(432+(864-432)*(1152-360)/(729-360),1152),(432-432*(1152-360)/(729-360),1152)], (432,900)),
    keep_wall=[("ellipse",(430,365,178,178)), ("poly",[(12,270),(205,270),(205,482),(12,482)])],
    keep_floor=[("ellipse",(432,940,292,114))],
  ),
  "starry": dict(hy=330, lf_chroma=("wall",),
    walls=[([(0,0),(533,0),(533,619),(0,689)], wall_plane((533,619),(0,689),330), (300,400))],
    floor=([(0,723),(533,646),(864,746),(864,1152),(0,1152)], floor_plane((0,723),(533,646),(864,746),330), (432,900)),
    keep_wall=[("ellipse",(128,368,78,78)),
               ("band",[(0,186),(90,232),(170,256),(250,262),(340,250),(420,226),(480,188),(536,142),(600,104)], 22, 46)],
    keep_floor=[("ellipse",(432,846,362,136))],
  ),
}

SS = 4
KEEP = {}
CH_T = 0.03
LUM_T = 0.85
def poly_mask(polys, keeps):
    m = Image.new("L", (W*SS, H*SS), 0); d = ImageDraw.Draw(m)
    for p in polys: d.polygon([(x*SS, y*SS) for x, y in p], fill=255)
    for k in keeps:
        if k[0] == "ellipse":
            cx, cy, rx, ry = k[1]; d.ellipse([(cx-rx)*SS, (cy-ry)*SS, (cx+rx)*SS, (cy+ry)*SS], fill=0)
        elif k[0] == "poly":
            d.polygon([(x*SS, y*SS) for x, y in k[1]], fill=0)
        elif k[0] == "band":
            pts, up, dn = k[1], k[2], k[3]
            poly = [(x, y-up) for x, y in pts] + [(x, y+dn) for x, y in reversed(pts)]
            d.polygon([(x*SS, y*SS) for x, y in poly], fill=0)
    m = m.resize((W, H), Image.LANCZOS)
    return np.asarray(m, float) / 255.0

def sample_tile(tile, M, mask):
    """Supersampled warp: tile coords = M @ [x,y,1]."""
    arr = np.asarray(tile, float); out = np.zeros((H, W, 3)); n = 0
    ys, xs = np.nonzero(mask > 0)
    acc = np.zeros((len(ys), 3))
    for oy in (0.25, 0.75):
        for ox in (0.25, 0.75):
            x = xs + ox; y = ys + oy
            w = M[2,0]*x + M[2,1]*y + M[2,2]
            u = (M[0,0]*x + M[0,1]*y + M[0,2]) / w; v = (M[1,0]*x + M[1,1]*y + M[1,2]) / w
            px = (u % 1.0) * T; py = (v % 1.0) * T
            x0 = np.floor(px).astype(int); y0 = np.floor(py).astype(int); fx = (px-x0)[:, None]; fy = (py-y0)[:, None]
            x1 = (x0+1) % T; y1 = (y0+1) % T; x0 %= T; y0 %= T
            acc += (arr[y0,x0]*(1-fx)*(1-fy) + arr[y0,x1]*fx*(1-fy) + arr[y1,x0]*(1-fx)*fy + arr[y1,x1]*fx*fy)
    out[ys, xs] = acc / 4
    return out

def lowfreq(room, surf):
    """Low-frequency surface colour: median (kills dots/stars/plank lines) then normalized blur
    over surface-only pixels, which also fills in under fixtures."""
    r = room.copy()
    for c in range(3): r[..., c] = ndimage.median_filter(r[..., c], size=17)
    m = (surf > 0.5).astype(float)
    out = np.zeros_like(r)
    # multi-scale normalized convolution so large fixture holes get a sane fill
    wsum = np.zeros(m.shape)
    for sig in (10, 40, 120):
        den = ndimage.gaussian_filter(m, sig)
        w = np.clip(den / 0.25, 0, 1) * (1 - wsum)
        for c in range(3):
            out[..., c] += w * ndimage.gaussian_filter(r[..., c]*m, sig) / np.maximum(den, 1e-6)
        wsum += w
    return out / np.maximum(wsum, 1e-6)[..., None]

def keep_mask(day, region, hard, fill=False, const_chroma=True, solid=None):
    """Inside the hand-drawn fixture regions keep only pixels that really differ from the surface."""
    lf = lowfreq(day, region * (1 - hard))
    # chromaticity distance: glows and soft shadows only change brightness (-> become lighting on
    # the new surface), real fixtures change hue/chroma; very bright/dark extremes count too.
    ch = day / (day.sum(-1, keepdims=True) + 1)
    if const_chroma:  # surface chroma ~constant (lighting only changes brightness): robust for rugs
        sm = (region * (1 - hard)) > 0.5
        cl = np.median(ch[sm], axis=0)
    else:  # sun shafts tint the surface: compare against the local low-frequency colour
        cl = lf / (lf.sum(-1, keepdims=True) + 1)
    dc = np.sqrt(((ch - cl) ** 2).sum(-1))
    lum = np.log((day.mean(-1) + 8) / (lf.mean(-1) + 8))
    fx = np.maximum(np.clip((dc - CH_T) / 0.03, 0, 1), np.clip((np.abs(lum) - LUM_T) / 0.2, 0, 1))
    fx = ndimage.grey_closing(fx, size=5)
    fx = ndimage.grey_opening(fx, size=3)
    if fill: fx = np.maximum(fx, ndimage.binary_fill_holes(ndimage.binary_closing(fx > 0.5, iterations=12)) * hard)
    if solid is not None: fx = np.maximum(fx, solid)
    fx = ndimage.gaussian_filter(fx.astype(float), 0.8)
    return hard * np.clip(fx, 0, 1)

def shade(tex, S):
    S = np.clip(S, 0.25, 1.9)
    return np.where(S <= 1, tex*S, tex + (255-tex)*(S-1)*0.85)

def main():
    tiles = {k: v[0]() for k, v in TILES.items()}
    for k, t in tiles.items(): t.save(f"/tmp/tile_{k}.png")
    for theme, g in ROOMS.items():
        day = np.asarray(Image.open(f"{D}/room_{theme}_day_3x4.webp").convert("RGB"), float)
        planes = {"wall": [(p, pl, ref) for p, pl, ref in g["walls"]], "floor": [g["floor"]]}
        keeps = {"wall": g["keep_wall"], "floor": g["keep_floor"]}
        for tn in ("day", "night"):
            room = day if tn == "day" else np.asarray(Image.open(f"{D}/room_{theme}_night_3x4.webp").convert("RGB"), float)
            for sid, (_, kind, _) in TILES.items():
                rgb = np.zeros((H, W, 3)); alpha = np.zeros((H, W))
                for poly, plane, ref in planes[kind]:
                    region = poly_mask([poly], [])
                    key = (theme, kind, str(poly))
                    if key not in KEEP:
                        hard = region - poly_mask([poly], keeps[kind])
                        sol = g.get("solid_" + kind)
                        sol = None if sol is None else 1 - poly_mask([[(0,0),(W,0),(W,H),(0,H)]], sol)
                        KEEP[key] = keep_mask(day, region, np.clip(hard, 0, 1), fill=(kind == "floor"),
                                              const_chroma=kind not in g.get("lf_chroma", ()), solid=sol)
                    m = region * (1 - KEEP[key])
                    tile_px = 150 if kind == "wall" else (300 if sid == "floor_herringbone" else 220)
                    M = iso_plane(plane, ref, tile_px)
                    tex = sample_tile(tiles[sid], M, m)
                    surf = region * (1 - KEEP[key])
                    ref_mean = (day*surf[..., None]).sum((0, 1)) / surf.sum()
                    S = lowfreq(room, surf) / ref_mean
                    col = shade(tex, S)
                    rgb = rgb*(1-m[..., None]) + col*m[..., None]; alpha = np.maximum(alpha, m)
                out = np.dstack([np.clip(rgb, 0, 255), alpha*255]).astype(np.uint8)
                Image.fromarray(out, "RGBA").save(f"{D}/surf_{theme}_{tn}_{sid}.webp", "WEBP", quality=88, method=6)
                print(theme, tn, sid)

if __name__ == "__main__":
    main()
