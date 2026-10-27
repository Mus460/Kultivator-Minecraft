"""Textures of the client effects of "Kota yang Hidup" (DESIGN 42.9.2-42.9.3, 42.12.6, module 42V-F), drawn with numpy and
written with Pillow (python tools/gen_kota_hidup_fx.py <repo> [<preview_dir>]):

  textures/fx/kota42/partikel.png   128 x 128, 8 x 8 cells of 16 px: the sprites of fx/PartikelKota
      row 0  asap 0-7      kitchen smoke puffs, small and dense to wide and thin (white-grey, tinted by the client)
      row 1  uap 0-3       steam wisps;  kertas 0-3  red paper scraps with a gold fleck
      row 2  kelopak 0-3   petals (peach, pink, cream, white);  daun 0-3  autumn leaves (gold, orange, red, brown)
      row 3  percik 0-3    sparks shrinking to nothing;  uang 0-3  joss paper (square, ingot, foil square, long strip)
      row 4  putih 0-1     white mourning scraps;  abu 0-1  ash flakes;  kapas 0-1  willow fluff;  kilau 0-1  glints
  textures/fx/kota42/cahaya.png     128 x 128, 64 px cells: halo (0,0), ground glow (1,0), soot smears (0,1) and (1,1)
  textures/fx/kota42/papan.png      32 x 32 lacquered board (black, gold rim, studs): the 'papan' cloth of 42.9.3
  textures/fx/kota42/kertas_pintu.png  32 x 32 red door paper diamond with a gold border (the inverted fu of New Year)
  textures/fx/kota42/duka.png       32 x 48 white hemp mourning cloth (frayed, with a faint ink line)
  textures/fx/kota42/tali.png       8 x 8 hemp rope (street banners hang their pennants from it)
  textures/fx/kota42/untai_petasan.png  16 x 64 a string of firecrackers on a twisted red cord, gold foil labels

Everything is flat colour with a little value noise; the client tints the white ones. Deterministic (seeded).
"""
import os
import sys

import numpy as np
from PIL import Image

sys.stdout.reconfigure(encoding='utf-8')
rng = np.random.default_rng(4217)


def noise(h, w, scale, seed):
    """Smooth value noise 0..1 (bilinear over a coarse random grid)."""
    r = np.random.default_rng(seed)
    gh, gw = max(2, int(h / scale) + 2), max(2, int(w / scale) + 2)
    g = r.random((gh, gw))
    ys = np.linspace(0, gh - 1.001, h)
    xs = np.linspace(0, gw - 1.001, w)
    y0 = ys.astype(int)
    x0 = xs.astype(int)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def canvas(w, h):
    return np.zeros((h, w, 4), dtype=np.float64)


def put(img, x0, y0, tile):
    h, w = tile.shape[:2]
    img[y0:y0 + h, x0:x0 + w] = tile


def over(dst, src):
    """Alpha-composites src over dst (both HxWx4 floats 0..255 with alpha 0..255)."""
    sa = src[..., 3:4] / 255.0
    da = dst[..., 3:4] / 255.0
    oa = sa + da * (1 - sa)
    rgb = np.where(oa > 0, (src[..., :3] * sa + dst[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-9), 0)
    return np.concatenate([rgb, oa * 255.0], axis=-1)


def rgba(rgb, alpha):
    h, w = alpha.shape
    out = np.zeros((h, w, 4))
    out[..., :3] = np.array(rgb, dtype=float)
    out[..., 3] = np.clip(alpha, 0, 1) * 255.0
    return out


def grid(n):
    ys, xs = np.mgrid[0:n, 0:n]
    return (xs + 0.5) / n * 2 - 1, (ys + 0.5) / n * 2 - 1  # -1..1


def shade(rgb, k):
    return tuple(max(0, min(255, c * k)) for c in rgb)


# ------------------------------------------------------------------------------------------------ particle sprites (16 px)

def asap(i):
    """Smoke puff frame i of 8: it grows from a dense little ball to a wide wispy cloud, getting thinner and more broken."""
    n = 16
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    size = 0.38 + 0.6 * (i / 7.0)
    nz = noise(n, n, 3.0, 100 + i) * 0.8 + noise(n, n, 1.5, 200 + i) * 0.4
    edge = size * (0.75 + 0.5 * nz)
    a = np.clip((edge - r) / (0.35 * size + 0.05), 0, 1)
    dens = (0.95 - 0.55 * (i / 7.0)) * (0.7 + 0.5 * nz)
    a = a * np.clip(dens, 0, 1)
    shade_k = 0.78 + 0.22 * (0.5 - (y * 0.5 + x * 0.25) * 0.5 + 0.5)  # lit from above-left
    base = np.array([238, 236, 232])
    out = np.zeros((n, n, 4))
    out[..., :3] = base * shade_k[..., None]
    out[..., 3] = a * 255
    return out


def uap(i):
    """Steam wisp: a curling thin trail, frame i of 4 drifts and thins."""
    n = 16
    x, y = grid(n)
    cx = 0.35 * np.sin(y * 3.2 + i * 1.3) * (1 - 0.3 * i / 3)
    d = np.abs(x - cx)
    w = 0.22 + 0.12 * (i / 3.0)
    a = np.clip((w - d) / (w * 0.9), 0, 1) * np.clip(1.0 - np.abs(y) * 0.75, 0, 1)
    a *= (0.8 - 0.25 * i / 3) * (0.8 + 0.4 * noise(n, n, 2.5, 300 + i))
    return rgba((246, 246, 244), a)


def kertas(i):
    """A scrap of red paper (angpao): a skewed rectangle or triangle with a gold fleck and a lighter edge."""
    n = 16
    x, y = grid(n)
    ang = [0.4, -0.35, 0.9, -0.8][i]
    c, s = np.cos(ang), np.sin(ang)
    u = x * c + y * s
    v = -x * s + y * c
    if i % 2 == 0:
        inside = (np.abs(u) < 0.62) & (np.abs(v) < 0.38)
    else:
        inside = (v > -0.45) & (v < 0.5 - np.abs(u) * 0.95) & (np.abs(u) < 0.6)
    a = inside.astype(float)
    rim = inside & ((np.abs(u) > 0.5) | (np.abs(v) > 0.3) | (v < -0.38))
    out = np.zeros((n, n, 4))
    out[..., :3] = (206, 38, 32)
    out[rim, :3] = (232, 80, 56)
    fleck = inside & (np.abs(u - 0.1) < 0.12) & (np.abs(v + 0.02) < 0.12)
    out[fleck, :3] = (244, 200, 90)
    out[..., 3] = a * 255
    return out


def kelopak(i):
    """A petal: two curved lobes (a teardrop), a darker vein."""
    n = 16
    x, y = grid(n)
    ang = [0.5, -0.4, 1.1, -1.0][i]
    c, s = np.cos(ang), np.sin(ang)
    u = x * c + y * s
    v = -x * s + y * c
    w = 0.42 * np.clip(1 - ((v + 0.1) / 0.78) ** 2, 0, 1)
    w = w * (1.0 - 0.35 * np.clip(v, -1, 1))
    inside = (np.abs(u) < w) & (np.abs(v) < 0.78)
    col = [(250, 190, 170), (246, 150, 180), (252, 236, 214), (255, 250, 246)][i]
    out = np.zeros((n, n, 4))
    out[..., :3] = col
    vein = inside & (np.abs(u) < 0.045)
    out[vein, :3] = shade(col, 0.82)
    tip = inside & (v > 0.5)
    out[tip, :3] = shade(col, 0.94)
    out[..., 3] = inside * 255
    return out


def daun(i):
    """An autumn leaf: a pointed oval with a midrib and side veins, gold / orange / red / brown."""
    n = 16
    x, y = grid(n)
    ang = [0.7, -0.5, 1.25, -1.2][i]
    c, s = np.cos(ang), np.sin(ang)
    u = x * c + y * s
    v = -x * s + y * c
    w = 0.46 * np.sin(np.clip((v + 0.85) / 1.7, 0, 1) * np.pi) ** 0.8
    inside = (np.abs(u) < w) & (np.abs(v) < 0.85)
    col = [(226, 178, 52), (222, 118, 36), (186, 52, 36), (132, 84, 44)][i]
    out = np.zeros((n, n, 4))
    out[..., :3] = col
    mid = inside & (np.abs(u) < 0.05)
    side = inside & (np.abs(np.abs(u) - np.abs(v + 0.25) * 0.55) < 0.05) & (v > -0.5)
    out[mid, :3] = shade(col, 0.7)
    out[side, :3] = shade(col, 0.82)
    blot = inside & (noise(n, n, 3.0, 400 + i) > 0.68)
    out[blot, :3] = shade(col, 0.88)
    out[..., 3] = inside * 255
    return out


def percik(i):
    """A spark: a hot white core with four thin rays, shrinking over 4 frames."""
    n = 16
    x, y = grid(n)
    k = [1.0, 0.75, 0.5, 0.3][i]
    r = np.sqrt(x * x + y * y)
    core = np.clip(1 - r / (0.34 * k + 0.05), 0, 1) ** 1.3
    rays = np.clip(1 - np.abs(x) / (0.07 * k + 0.02), 0, 1) * np.clip(1 - np.abs(y) / (0.85 * k), 0, 1)
    rays += np.clip(1 - np.abs(y) / (0.07 * k + 0.02), 0, 1) * np.clip(1 - np.abs(x) / (0.85 * k), 0, 1)
    a = np.clip(core + rays * 0.7, 0, 1)
    out = np.zeros((n, n, 4))
    t = np.clip(core * 1.6, 0, 1)
    out[..., 0] = 255
    out[..., 1] = 190 + 65 * t
    out[..., 2] = 90 + 150 * t
    out[..., 3] = a * 255
    return out


def uang(i):
    """Joss paper: a square with a printed foil mark, an ingot (yuanbao), a foil square, a long strip."""
    n = 16
    x, y = grid(n)
    out = np.zeros((n, n, 4))
    if i == 0:
        inside = (np.abs(x) < 0.62) & (np.abs(y) < 0.62)
        out[..., :3] = (232, 206, 120)
        mark = inside & (np.abs(x) < 0.26) & (np.abs(y) < 0.26)
        out[mark, :3] = (196, 150, 52)
        hole = inside & (np.abs(x) < 0.09) & (np.abs(y) < 0.09)
        out[hole, :3] = (232, 206, 120)
    elif i == 1:
        # yuanbao: a boat-shaped ingot with a raised hump
        boat = (np.abs(x) < 0.7 - np.maximum(0, y) * 0.35) & (y > -0.05) & (y < 0.5)
        hump = (np.abs(x) < 0.34) & (y <= -0.05) & (y > -0.45 + np.abs(x) * 0.9)
        inside = boat | hump
        out[..., :3] = (236, 192, 70)
        out[hump, :3] = (250, 224, 120)
        out[boat & (y > 0.38), :3] = (190, 140, 40)
    elif i == 2:
        inside = (np.abs(x) < 0.6) & (np.abs(y) < 0.6)
        out[..., :3] = (214, 218, 222)
        fold = inside & (np.abs(x - y) < 0.07)
        out[fold, :3] = (170, 176, 182)
        out[inside & (np.abs(x + y) < 0.07), :3] = (240, 244, 246)
    else:
        inside = (np.abs(x) < 0.3) & (np.abs(y) < 0.85)
        out[..., :3] = (244, 232, 190)
        out[inside & (np.abs(y) % 0.34 < 0.07), :3] = (200, 60, 50)
    out[..., 3] = inside * 255
    return out


def putih(i):
    n = 16
    x, y = grid(n)
    ang = [0.6, -0.9][i]
    c, s = np.cos(ang), np.sin(ang)
    u = x * c + y * s
    v = -x * s + y * c
    inside = (np.abs(u) < (0.7 if i == 0 else 0.3)) & (np.abs(v) < (0.3 if i == 0 else 0.75))
    out = np.zeros((n, n, 4))
    out[..., :3] = (246, 244, 238)
    out[inside & (noise(n, n, 2.0, 500 + i) > 0.6), :3] = (222, 218, 210)
    out[..., 3] = inside * 255
    return out


def abu(i):
    n = 16
    x, y = grid(n)
    nz = noise(n, n, 2.2, 600 + i)
    r = np.sqrt(x * x + y * y)
    a = (r < 0.5 * (0.7 + 0.5 * nz)).astype(float) * 0.9
    out = np.zeros((n, n, 4))
    out[..., :3] = (84, 80, 78) if i == 0 else (120, 114, 108)
    out[..., 3] = a * 255
    return out


def kapas(i):
    """Willow fluff: a soft pale tuft with a curved filament."""
    n = 16
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    puff = np.clip(1 - r / 0.4, 0, 1) ** 0.9
    fil = np.clip(1 - np.abs(x - 0.4 * np.sin(y * 2.6 + i * 2.0)) / 0.06, 0, 1) * (y > 0.0) * np.clip(1 - y, 0, 1)
    a = np.clip(puff * 0.9 + fil * 0.6, 0, 1)
    return rgba((250, 250, 244), a)


def kilau(i):
    n = 16
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    d = np.abs(x) * np.abs(y) * 4.0 if i == 0 else np.minimum(np.abs(x), np.abs(y)) * 3.0
    a = np.clip(1 - (r + d) / 0.95, 0, 1) ** 1.6
    return rgba((255, 240, 200), a)


PART = [
    [asap(i) for i in range(8)],
    [uap(i) for i in range(4)] + [kertas(i) for i in range(4)],
    [kelopak(i) for i in range(4)] + [daun(i) for i in range(4)],
    [percik(i) for i in range(4)] + [uang(i) for i in range(4)],
    [putih(0), putih(1), abu(0), abu(1), kapas(0), kapas(1), kilau(0), kilau(1)],
]


def partikel():
    img = canvas(128, 128)
    for row, tiles in enumerate(PART):
        for col, t in enumerate(tiles):
            put(img, col * 16, row * 16, t)
    return img


# ------------------------------------------------------------------------------------------------ light and soot (64 px)

def halo():
    """A soft round glow: a hot centre, a long gentle falloff, a faint star where the lantern's ribs catch the light."""
    n = 64
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    core = np.exp(-(r / 0.22) ** 2)
    mid = np.exp(-(r / 0.55) ** 2) * 0.55
    far = np.clip(1 - r, 0, 1) ** 2.2 * 0.35
    star = (np.exp(-(np.abs(x) / 0.04)) * np.exp(-(np.abs(y) / 0.7) ** 2) + np.exp(-(np.abs(y) / 0.04)) * np.exp(-(np.abs(x) / 0.7) ** 2)) * 0.12
    a = np.clip(core + mid + far + star, 0, 1) * np.clip(1.25 - r, 0, 1)
    out = np.zeros((n, n, 4))
    out[..., 0] = 255
    out[..., 1] = 255 - 90 * (1 - np.clip(core * 1.5, 0, 1)) * np.clip(r * 1.4, 0, 1)
    out[..., 2] = 255 - 190 * np.clip(r * 1.3, 0, 1) * (1 - core)
    out[..., 3] = a * 255
    return out


def bercak():
    """The patch of light a lantern throws on the street: a round pool with a faint lattice (paper ribs) and a dim outer ring."""
    n = 64
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    base = np.clip(1 - r, 0, 1) ** 1.5
    ang = np.arctan2(y, x)
    ribs = 0.82 + 0.18 * np.cos(ang * 8) * np.clip(1 - r * 1.2, 0, 1)
    ring = np.exp(-((r - 0.72) / 0.07) ** 2) * 0.22
    a = np.clip((base * ribs + ring) * 0.95, 0, 1)
    out = np.zeros((n, n, 4))
    out[..., 0] = 255
    out[..., 1] = 205 + 40 * (1 - r)
    out[..., 2] = 150 + 80 * (1 - r)
    out[..., 3] = a * 255
    return out


def jelaga(seed, drip):
    """Soot on a wall: a dark smoky blotch with streaks running up (smoke) or down (a drip), ragged edges, darker core."""
    n = 64
    x, y = grid(n)
    nz = noise(n, n, 6.0, 700 + seed) * 0.7 + noise(n, n, 2.5, 800 + seed) * 0.5
    streak = noise(n, n, 1.5, 900 + seed)
    stretched = np.repeat(noise(1, n, 2.0, 950 + seed), n, axis=0) if False else None
    r = np.sqrt((x * 0.9) ** 2 + ((y + 0.15 * (1 if drip else -1)) * 0.75) ** 2)
    blob = np.clip(1.0 - r / (0.82 * (0.65 + 0.5 * nz)), 0, 1)
    cols = np.abs(np.sin(x * 11 + nz * 4)) * 0.5 + 0.5
    ray = np.clip(cols * (1 - np.abs(x) * 0.7) * np.clip(1 + (y if not drip else -y) * 0.9, 0, 1), 0, 1) * 0.35
    a = np.clip(blob ** 0.8 * (0.55 + 0.45 * streak) + ray * blob, 0, 1) * 0.92
    out = np.zeros((n, n, 4))
    k = 24 + 34 * (1 - blob)
    out[..., 0] = k
    out[..., 1] = k * 0.96
    out[..., 2] = k * 0.92
    out[..., 3] = a * 255
    return out


def cahaya():
    img = canvas(128, 128)
    put(img, 0, 0, halo())
    put(img, 64, 0, bercak())
    put(img, 0, 64, jelaga(1, False))
    put(img, 64, 64, jelaga(2, True))
    return img


# ------------------------------------------------------------------------------------------------ cloth, boards, strings

def papan():
    """The lacquered board: near-black with a deep red undertone, a gold rim with a thin inner line, four brass studs."""
    n = 32
    img = np.zeros((n, n, 4))
    nz = noise(n, n, 3.0, 1000)
    img[..., :3] = np.array([30, 16, 14])[None, None, :] * (0.85 + 0.35 * nz[..., None])
    img[..., 3] = 255
    gold = np.array([214, 168, 62])
    for i in range(n):
        for j in range(n):
            e = min(i, j, n - 1 - i, n - 1 - j)
            if e <= 1:
                img[j, i, :3] = gold * (0.82 + 0.25 * ((i * 7 + j * 3) % 5) / 5)
            elif e == 3:
                img[j, i, :3] = gold * 0.55
    for cx, cy in ((5, 5), (26, 5), (5, 26), (26, 26)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if dx * dx + dy * dy <= 1 or (dx == 0 and dy == 0):
                    img[cy + dy, cx + dx, :3] = (240, 204, 100) if dx + dy <= 0 else (170, 126, 40)
    return img


def kertas_pintu():
    """The diamond of red paper on the door: a gold border and a faint paper grain; the glyph is added by the client."""
    n = 32
    x, y = grid(n)
    d = np.abs(x) + np.abs(y)
    inside = d < 0.97
    img = np.zeros((n, n, 4))
    nz = noise(n, n, 1.5, 1100)
    img[..., :3] = np.array([206, 36, 30])[None, None, :] * (0.92 + 0.12 * nz[..., None])
    border = inside & (d > 0.84)
    img[border, :3] = (232, 184, 72)
    line = inside & (np.abs(d - 0.74) < 0.03)
    img[line, :3] = (190, 128, 40)
    img[..., 3] = inside * 255
    return img


def duka():
    """The white mourning cloth: undyed hemp with a visible weave, a frayed lower edge and a faint ink line."""
    w, h = 32, 48
    img = np.zeros((h, w, 4))
    ys, xs = np.mgrid[0:h, 0:w]
    weave = 0.93 + 0.07 * (((xs // 2 + ys // 2) % 2) * 2 - 1) * 0.5
    nz = noise(h, w, 4.0, 1200)
    img[..., :3] = np.array([240, 236, 226])[None, None, :] * (weave * (0.94 + 0.08 * nz))[..., None]
    # frayed bottom edge: ragged per column
    fr = np.random.default_rng(1201).integers(0, 5, size=w)
    for x in range(w):
        for y in range(h):
            edge = h - 1 - y
            img[y, x, 3] = 255 if edge >= fr[x] else 0
    img[:2, :, :3] = (214, 208, 196)
    img[3, :, :3] = (180, 176, 168)
    return img


def tali():
    n = 8
    ys, xs = np.mgrid[0:n, 0:n]
    img = np.zeros((n, n, 4))
    k = 0.8 + 0.25 * (((xs + ys) % 4) < 2)
    img[..., :3] = np.array([120, 92, 60])[None, None, :] * k[..., None]
    img[..., 3] = 255
    return img


def untai_petasan():
    """A string of firecrackers: a twisted red cord down the middle, red paper tubes (alternating left/right) bound to it,
    each with a gold foil band, a short fuse at the lower end."""
    w, h = 16, 64
    img = np.zeros((h, w, 4))
    img[:, 7:9, :3] = (150, 24, 20)
    img[:, 7:9, 3] = 255
    for y in range(h):
        if y % 4 < 2:
            img[y, 7:9, :3] = (190, 40, 30)
    for k, y0 in enumerate(range(4, h - 8, 7)):
        side = -1 if k % 2 == 0 else 1
        x0 = 8 + side * 1 if side > 0 else 6
        xs = range(9, 14) if side > 0 else range(2, 7)
        for x in xs:
            for y in range(y0, y0 + 6):
                shade_k = 1.0 if y < y0 + 3 else 0.88
                img[y, x, :3] = np.array([214, 40, 32]) * shade_k
                img[y, x, 3] = 255
            img[y0 + 2, x, :3] = (236, 190, 76)
            img[y0 + 3, x, :3] = (200, 150, 50)
        img[y0, min(xs):max(xs) + 1, :3] = (240, 80, 56)
        # the fuse tip
        tip = max(xs) + 1 if side > 0 else min(xs) - 1
        if 0 <= tip < w:
            img[y0 + 1, tip, :3] = (60, 50, 40)
            img[y0 + 1, tip, 3] = 255
    img[h - 8:h - 2, 7:9, :3] = (214, 196, 150)
    img[h - 8:h - 2, 7:9, 3] = 255
    return img


# ------------------------------------------------------------------------------------------------ write

def save(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    im.save(path)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else '.'
    prev = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, 'src/main/resources/assets/kultivator/textures/fx/kota42')
    files = {
        'partikel.png': partikel(), 'cahaya.png': cahaya(), 'papan.png': papan(), 'kertas_pintu.png': kertas_pintu(),
        'duka.png': duka(), 'tali.png': tali(), 'untai_petasan.png': untai_petasan(),
    }
    for name, arr in files.items():
        save(arr, os.path.join(base, name))
        print(name, arr.shape[1], 'x', arr.shape[0])
    if prev:
        os.makedirs(prev, exist_ok=True)
        for name, arr in files.items():
            im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
            s = 6 if im.width <= 16 else (5 if im.width <= 32 else (4 if name == 'partikel.png' else 3))
            im = im.resize((im.width * s, im.height * s), Image.NEAREST)
            bg = Image.new('RGBA', im.size, (74, 92, 84, 255) if name != 'cahaya.png' else (24, 22, 30, 255))
            bg.alpha_composite(im)
            bg.save(os.path.join(prev, 'fx_' + name))


if __name__ == '__main__':
    main()
