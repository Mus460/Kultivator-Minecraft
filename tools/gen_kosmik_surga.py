"""Film Kosmik 3D, the Heaven stage (DESIGN 32.3.2): textures and cuboid stage models of module H.

Usage (from the project root, stdlib only):
    python -I tools/gen_kosmik_surga.py

Writes, with a fixed seed (the output is the same on every run):
  * assets/kultivator/textures/misc/kosmik/surga/
      surga_atlas.png 256        the palace atlas (vermilion lacquer, gold, imperial and jade glazed tiles, white jade,
                                 dragon scales, xiangyun relief, studded doors, the two plaques as our own strokes,
                                 lantern paper, island rock, jade terrace, crystal, guard armour, peach blossom, ...)
      sky_heaven_128/256/512.png the Heaven dome (equirect 2:1, v = 0 at the zenith): lilac zenith, dawn-gold horizon,
                                 faint stars, silk aurora ribbons, a warm cloud-glow below the horizon
      cloud_sea_256/512.png      tileable cloud sea (white with alpha, lilac shadows, gaps)
      cloud_puff.png 128         a soft cloud puff (alpha fades to 0 at the border)
      shaft.png 64 x 256         golden light shaft (additive: premultiplied colour), u across, v along
      ember_trail.png 128 x 32   the javelin / pillar ember trail (additive), u along (head at 0), v across
      petal.png 128 x 32         four tumbling peach-blossom frames (translucent)
      waterfall.png 64 x 256     the Heavenly River falling light (additive, tileable along v)
      nine_rings.png 512 x 72    nine rows of 8 px (row k-1 = ring k; violet at ring 1 -> gold at ring 9), tileable in u
      gate_glow.png 128          the light behind the opening gate (additive)
      land_below_256.png         tileable neutral land far below (tinted at runtime by the home grass colour)
    plus a .png.mcmeta {"texture": {"blur": true}} for every texture (linear filtering, no mipmaps; the atlas UVs are
    inset by 1 px so no tile bleeds into its neighbour);
  * assets/kultivator/kosmik/surga_models.json in the format of C32.4.

Additive textures are drawn with BlendFunction.ADDITIVE (ONE, ONE) and an alpha cutout of 0.1 on the texture alpha, so
their colour is premultiplied and their alpha is 0 only where the colour is (almost) black. Translucent textures keep
alpha >= 26 wherever they are visible (the cutout would cut softer pixels).

validate() checks every cuboid inside its model's box, every UV inside the atlas, no zero-size cuboid, the per-model
cuboid counts of the table in 32.3.1, the per-quality quad totals, seamless tiles and transparent borders, and prints the
total size.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "kultivator")
OUT = os.path.join(ASSETS, "textures", "misc", "kosmik", "surga")
MODELS_OUT = os.path.join(ASSETS, "kosmik", "surga_models.json")
ATLAS_ID = "kultivator:textures/misc/kosmik/surga/surga_atlas.png"
SEED = 0x5A7A

# Face names follow the stage axes of 32.3 as KosmikMesh reads them (x east, y up, z north): north = +z, south = -z,
# east = +x, west = -x, up = +y, down = -y. Every model is built with its front toward -z (south, where arrivals come
# from), so a front face is "south".
FACES = ("north", "south", "west", "east", "up", "down")


# ---------------------------------------------------------------------------------------------------------------- noise

def h32(*v):
    """A deterministic 32-bit hash of integers."""
    x = SEED & 0xFFFFFFFF
    for n in v:
        x ^= (n * 0x9E3779B1) & 0xFFFFFFFF
        x = ((x ^ (x >> 16)) * 0x85EBCA6B) & 0xFFFFFFFF
        x = ((x ^ (x >> 13)) * 0xC2B2AE35) & 0xFFFFFFFF
        x ^= x >> 16
    return x


def rnd(*v):
    """A deterministic float in [0, 1)."""
    return h32(*v) / 4294967296.0


def fade(t):
    return t * t * (3.0 - 2.0 * t)


def noise2p(x, y, period, salt=0):
    """2D value noise, periodic with an integer period (tileable)."""
    xi = math.floor(x)
    yi = math.floor(y)
    fx = fade(x - xi)
    fy = fade(y - yi)
    x0 = xi % period
    y0 = yi % period
    x1 = (xi + 1) % period
    y1 = (yi + 1) % period
    a = rnd(x0, y0, salt)
    b = rnd(x1, y0, salt)
    c = rnd(x0, y1, salt)
    d = rnd(x1, y1, salt)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy


def fbm2p(x, y, period, octaves, salt=0):
    """Tileable fbm: (x, y) in [0, period) cells at the first octave."""
    total = 0.0
    amp = 0.5
    norm = 0.0
    p = period
    for o in range(octaves):
        total += amp * noise2p(x, y, p, salt + o * 31)
        norm += amp
        amp *= 0.5
        x *= 2.0
        y *= 2.0
        p *= 2
    return total / norm


def noise3(x, y, z, salt=0):
    xi = math.floor(x)
    yi = math.floor(y)
    zi = math.floor(z)
    fx = fade(x - xi)
    fy = fade(y - yi)
    fz = fade(z - zi)

    def lerp(a, b, t):
        return a + (b - a) * t

    c000 = rnd(xi, yi, zi, salt)
    c100 = rnd(xi + 1, yi, zi, salt)
    c010 = rnd(xi, yi + 1, zi, salt)
    c110 = rnd(xi + 1, yi + 1, zi, salt)
    c001 = rnd(xi, yi, zi + 1, salt)
    c101 = rnd(xi + 1, yi, zi + 1, salt)
    c011 = rnd(xi, yi + 1, zi + 1, salt)
    c111 = rnd(xi + 1, yi + 1, zi + 1, salt)
    return lerp(lerp(lerp(c000, c100, fx), lerp(c010, c110, fx), fy), lerp(lerp(c001, c101, fx), lerp(c011, c111, fx), fy), fz)


def fbm3(x, y, z, octaves, salt=0):
    total = 0.0
    amp = 0.5
    norm = 0.0
    for o in range(octaves):
        total += amp * noise3(x, y, z, salt + o * 17)
        norm += amp
        amp *= 0.5
        x *= 2.0
        y *= 2.0
        z *= 2.0
    return total / norm


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def smooth(e0, e1, v):
    if e0 == e1:
        return 0.0 if v < e0 else 1.0
    t = clamp((v - e0) / (e1 - e0))
    return t * t * (3.0 - 2.0 * t)


def hexrgb(s):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def mul(c, k):
    return tuple(v * k for v in c)


def additive(img, x, y, rgb, intensity):
    """Premultiplied additive texel: colour x intensity, alpha cut where it is black."""
    i = clamp(intensity)
    r, g, b = rgb[0] * i, rgb[1] * i, rgb[2] * i
    a = clamp(max(r, g, b) / 255.0 * 4.0) * 255.0
    if a < 26.0:
        img.set(x, y, (0, 0, 0, 0))
    else:
        img.set(x, y, (r, g, b, a))


def translucent(img, x, y, rgb, alpha):
    a = clamp(alpha) * 255.0
    if a < 26.0:
        img.set(x, y, (rgb[0], rgb[1], rgb[2], 0))
    else:
        img.set(x, y, (rgb[0], rgb[1], rgb[2], a))


def downsample(img, factor):
    """Box downsample by an integer factor (alpha-weighted colour)."""
    w = img.width // factor
    h = img.height // factor
    out = Image(w, h)
    n = factor * factor
    for y in range(h):
        for x in range(w):
            sr = sg = sb = sa = 0.0
            for j in range(factor):
                for i in range(factor):
                    r, g, b, a = img.px[(y * factor + j) * img.width + x * factor + i]
                    sr += r * a
                    sg += g * a
                    sb += b * a
                    sa += a
            if sa <= 0:
                out.set(x, y, (0, 0, 0, 0))
            else:
                out.set(x, y, (sr / sa, sg / sa, sb / sa, sa / n))
    return out


WRITTEN = []


def save(img, name, blur=True):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    img.save(path)
    WRITTEN.append(path)
    meta = path + ".mcmeta"
    if blur:
        with open(meta, "w", newline="\n") as f:
            f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')
        WRITTEN.append(meta)
    elif os.path.exists(meta):
        os.remove(meta)
    return img


# ---------------------------------------------------------------------------------------------------------------- atlas

ATLAS = 256
CELL = 32
# name -> (x, y, w, h) in atlas pixels
TILES = {}


def tile(name, cx, cy, cw=1, ch=1):
    TILES[name] = (cx * CELL, cy * CELL, cw * CELL, ch * CELL)


tile("vermilion", 0, 0)
tile("gold", 1, 0)
tile("tile_imperial", 2, 0)
tile("tile_jade", 3, 0)
tile("white_jade", 4, 0)
tile("dragon", 5, 0)
tile("xiangyun", 6, 0)
tile("door", 7, 0)
tile("plaque_nantian", 0, 1, 2, 1)
tile("plaque_lingxiao", 2, 1, 2, 1)
tile("lantern", 4, 1)
tile("rock", 5, 1)
tile("jade_terrace", 6, 1)
tile("crystal", 7, 1)
tile("armour", 0, 2)
tile("bronze", 1, 2)
tile("cloud", 2, 2)
tile("beard", 3, 2)
tile("lattice", 4, 2)
tile("deep_gold", 5, 2)
tile("jade_step", 6, 2)
tile("dark_wood", 7, 2)
tile("plume", 0, 3)
tile("ridge", 1, 3)
tile("vermilion_dark", 2, 3)
tile("stone", 3, 3)
tile("cape", 4, 3)
tile("root", 5, 3)
tile("peach", 6, 3)
tile("gate_light", 7, 3)
tile("door_tall", 0, 4, 1, 4)

VERMILION = hexrgb("B0201A")
VERMILION_SH = hexrgb("7A1410")
GOLD = hexrgb("FFD54A")
DEEP_GOLD = hexrgb("C8961E")
IMPERIAL = hexrgb("E8B030")
JADE_GLAZE = hexrgb("5FD6A0")
WHITE_JADE = hexrgb("EDEBE0")
WHITE_JADE_SH = hexrgb("C9C3B4")
ROCK = hexrgb("6E6488")
JADE_TOP = hexrgb("7FD9B0")
CRYSTAL = hexrgb("B57CFF")
ARMOUR = hexrgb("3A2E1E")
BRONZE = hexrgb("A07040")
RED = hexrgb("D0302A")
PAPER = hexrgb("FF6A4A")
CLOUD_WHITE = hexrgb("F4F6FF")


def fill_tile(img, name, fn):
    x0, y0, w, h = TILES[name]
    for j in range(h):
        for i in range(w):
            c = fn(i, j, w, h)
            img.set(x0 + i, y0 + j, (c[0], c[1], c[2], 255))


def grain(i, j, salt, amp):
    return 1.0 + amp * (fbm2p(i / 8.0, j / 8.0, 4, 3, salt) - 0.5) * 2.0


def tex_vermilion(i, j, w, h):
    # lacquer with a vertical grain and soft vertical highlights
    g = grain(i * 0.25, j * 2.0, 11, 0.08) + 0.04 * math.sin(i / w * math.pi * 2.0)
    return mul(VERMILION, g)


def tex_vermilion_dark(i, j, w, h):
    return mul(VERMILION_SH, grain(i, j, 12, 0.07))


def tex_gold(i, j, w, h):
    # gold trim with an embossed band and fret dots
    c = GOLD
    band = 1.0 if (j % 16 in (0, 15)) else (0.86 if j % 16 in (1, 14) else 1.0)
    fret = 0.82 if ((i // 4 + j // 4) % 2 == 0 and 5 <= j % 16 <= 10) else 1.0
    return mul(mix(c, DEEP_GOLD, 0.15 * (1.0 - band)), band * fret * grain(i, j, 13, 0.05))


def tex_deep_gold(i, j, w, h):
    return mul(DEEP_GOLD, grain(i, j, 14, 0.08))


def glazed(base, i, j):
    # rows of round glazed tiles: vertical ridges every 8 px, courses every 8 px, a highlight on each ridge
    ridge = math.cos((i % 8 - 3.5) / 4.0 * math.pi * 0.5)
    course = 0.78 if j % 8 == 7 else 1.0
    lip = 1.12 if j % 8 == 0 else 1.0
    return mul(base, (0.62 + 0.45 * ridge) * course * lip)


def tex_imperial(i, j, w, h):
    return glazed(IMPERIAL, i, j)


def tex_jade(i, j, w, h):
    return glazed(JADE_GLAZE, i, j)


def tex_white_jade(i, j, w, h):
    vein = fbm2p(i / 10.0, j / 10.0, 4, 4, 21)
    v = 1.0 - 0.10 * smooth(0.55, 0.62, vein) * (1.0 - smooth(0.62, 0.7, vein))
    edge = 0.9 if (i == 0 or j == 0 or i == w - 1 or j == h - 1) else 1.0
    return mul(mix(WHITE_JADE, WHITE_JADE_SH, 0.25 * (1.0 - v) * 4.0), v * edge)


def tex_jade_step(i, j, w, h):
    c = tex_white_jade(i, j, w, h)
    if j < 3:
        return mul(GOLD, 0.95 if j == 1 else 0.8)
    if j > h - 3:
        return mul(c, 0.82)
    return c


def tex_dragon(i, j, w, h):
    # overlapping scales: rows of half circles, offset every other row
    row = j // 6
    off = 3 if row % 2 else 0
    cx = ((i + off) // 6) * 6 + 3 - off
    dx = i - cx
    dy = j - row * 6
    r = math.sqrt(dx * dx + (dy * 1.2) ** 2)
    shade = 1.0 - 0.45 * smooth(1.5, 3.4, r)
    rim = 0.65 if 3.0 <= r <= 3.8 else 1.0
    return mul(mix(GOLD, DEEP_GOLD, 0.5 * (1.0 - shade)), (0.7 + 0.35 * shade) * rim)


def tex_xiangyun(i, j, w, h):
    # white jade with auspicious cloud curls in relief: two spirals per tile
    base = tex_white_jade(i, j, w, h)
    best = 9.0
    for (cx, cy, s) in ((10.0, 11.0, 1.0), (23.0, 22.0, -1.0)):
        dx = i - cx
        dy = j - cy
        r = math.sqrt(dx * dx + dy * dy)
        a = math.atan2(dy, dx) * s
        # an Archimedean spiral r = 1.2 * theta
        k = (r / 1.3 - a / (2.0 * math.pi) * 2.0) % 1.0
        if r < 8.0:
            best = min(best, abs(k - 0.5) * 2.0)
    relief = 1.0
    if best < 0.3:
        relief = 1.12
    elif best < 0.45:
        relief = 0.8
    return mul(base, relief)


def tex_door(i, j, w, h):
    c = tex_vermilion(i, j, w, h)
    # gold studs in a 4 x 4 grid
    sx = (i % 8) - 3.5
    sy = (j % 8) - 3.5
    if sx * sx + sy * sy < 4.0:
        return mul(GOLD, 1.1 - 0.12 * (sx + sy) / 3.5)
    if i == 0 or i == w - 1:
        return mul(GOLD, 0.8)
    return c


def tex_door_tall(i, j, w, h):
    c = tex_vermilion(i % 32, j % 32, 32, 32)
    # 3 columns x 13 rows of gold studs, a gold frame, a knocker ring halfway up near the inner edge
    if i < 2 or i >= w - 2 or j < 2 or j >= h - 2:
        return mul(GOLD, 0.85)
    col = (i - 2) / (w - 4) * 3.0
    row = (j - 4) / (h - 8) * 13.0
    sx = (col % 1.0 - 0.5) * (w - 4) / 3.0
    sy = (row % 1.0 - 0.5) * (h - 8) / 13.0
    if 0 <= row < 13 and sx * sx + sy * sy < 5.0:
        return mul(GOLD, 1.12 - 0.08 * (sx + sy) / 2.2)
    kx = i - (w - 7)
    ky = j - h * 0.52
    kr = math.sqrt(kx * kx + ky * ky)
    if 2.4 <= kr <= 3.6:
        return mul(GOLD, 1.0)
    return c


GLYPHS_NANTIAN = [
    # 南
    [((2, 3), (14, 3)), ((8, 1), (8, 5)), ((3, 6), (3, 15)), ((3, 6), (13, 6)), ((13, 6), (13, 15)), ((13, 15), (11, 14)),
     ((6, 7), (7, 9)), ((10, 7), (9, 9)), ((5, 10), (11, 10)), ((5, 12), (11, 12)), ((8, 10), (8, 15))],
    # 天
    [((3, 4), (13, 4)), ((2, 8), (14, 8)), ((8, 4), (8, 8)), ((8, 8), (3, 14)), ((8, 8), (14, 14))],
    # 門
    [((3, 2), (3, 15)), ((3, 2), (7, 2)), ((7, 2), (7, 7)), ((3, 4.5), (7, 4.5)), ((3, 7), (7, 7)),
     ((13, 2), (13, 15)), ((13, 15), (11, 14)), ((9, 2), (13, 2)), ((9, 2), (9, 7)), ((9, 4.5), (13, 4.5)), ((9, 7), (13, 7))],
]
GLYPHS_LINGXIAO = [
    # 凌
    [((2, 4), (4, 6)), ((2, 12), (4, 9)), ((9, 1), (9, 5)), ((6, 3), (13, 3)), ((5, 6), (15, 6)), ((7, 7), (6, 9)),
     ((12, 7), (13, 9)), ((8, 10), (6, 15)), ((8, 10), (13, 10)), ((13, 10), (7, 15)), ((9, 13), (14, 15))],
    # 霄
    [((3, 2), (13, 2)), ((8, 1), (8, 7)), ((2, 4), (14, 4)), ((2, 4), (2, 7)), ((14, 4), (14, 7)), ((5, 5), (6, 6)),
     ((10, 5), (11, 6)), ((8, 8), (8, 9)), ((5, 8), (6, 9)), ((11, 8), (10, 9)), ((5, 10), (5, 15)), ((5, 10), (11, 10)),
     ((11, 10), (11, 15)), ((5, 12.5), (11, 12.5))],
    # 寶
    [((8, 1), (8, 2)), ((2, 3), (14, 3)), ((2, 3), (2, 5)), ((14, 3), (14, 5)), ((3, 5), (7, 5)), ((3, 7), (7, 7)),
     ((5, 5), (5, 9)), ((3, 9), (7, 9)), ((9, 5), (13, 5)), ((11, 5), (11, 9)), ((9, 7), (13, 7)), ((4, 10), (12, 10)),
     ((4, 10), (4, 14)), ((12, 10), (12, 14)), ((4, 12), (12, 12)), ((4, 14), (12, 14)), ((6, 15), (4, 16)), ((10, 15), (12, 16))],
    # 殿
    [((2, 2), (7, 2)), ((7, 2), (7, 4)), ((2, 4), (7, 4)), ((2, 2), (1, 14)), ((3, 7), (7, 7)), ((5, 5), (5, 11)),
     ((3, 11), (7, 11)), ((4, 12), (7, 15)), ((10, 2), (10, 5)), ((10, 5), (13, 5)), ((13, 2), (13, 5)), ((9, 8), (14, 8)),
     ((10, 9), (14, 15)), ((14, 9), (9, 15))],
]


def seg_dist(px, py, a, b):
    ax, ay = a
    bx, by = b
    dx = bx - ax
    dy = by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else clamp(((px - ax) * dx + (py - ay) * dy) / l2)
    qx = ax + dx * t - px
    qy = ay + dy * t - py
    return math.sqrt(qx * qx + qy * qy)


def plaque(glyphs):
    def fn(i, j, w, h):
        # a deep-blue board with a gold double frame and gold strokes
        if i < 2 or j < 2 or i >= w - 2 or j >= h - 2:
            return mul(GOLD, 0.9 if (i + j) % 2 else 1.0)
        if i < 3 or j < 3 or i >= w - 3 or j >= h - 3:
            return (40, 30, 70)
        board = mul(hexrgb("1E2A6A"), grain(i, j, 31, 0.08))
        n = len(glyphs)
        cw = (w - 8) / n
        k = int((i - 4) // cw)
        if 0 <= k < n:
            gx = (i - 4 - k * cw) / cw * 16.0
            gy = (j - 4) / (h - 8) * 16.0 + 0.0
            d = min(seg_dist(gx, gy, a, b) for (a, b) in glyphs[k])
            if d < 1.15:
                return mul(GOLD, 1.0 - 0.15 * d)
        return board
    return fn


def tex_lantern(i, j, w, h):
    # glowing paper with gold ribs (drawn additive: bright colours)
    rib = (i % 8 == 0) or j < 2 or j >= h - 2
    glow = 0.75 + 0.25 * math.sin(i / w * math.pi)
    if rib:
        return mul(GOLD, 0.9)
    return mul(PAPER, glow * (0.95 + 0.05 * math.sin(j * 0.7)))


def tex_rock(i, j, w, h):
    n = fbm2p(i / 8.0, j / 8.0, 4, 4, 41)
    crack = 0.6 if abs(fbm2p(i / 6.0, j / 6.0, 5, 3, 42) - 0.5) < 0.03 else 1.0
    return mul(ROCK, (0.75 + 0.5 * n) * crack)


def tex_jade_terrace(i, j, w, h):
    n = fbm2p(i / 6.0, j / 6.0, 5, 4, 51)
    tuft = 1.12 if rnd(i, j, 52) > 0.93 else 1.0
    return mul(JADE_TOP, (0.78 + 0.35 * n) * tuft)


def tex_crystal(i, j, w, h):
    facet = ((i * 3 + j * 5) // 9) % 3
    return mul(CRYSTAL, (0.8, 1.0, 1.2)[facet])


def tex_armour(i, j, w, h):
    # lamellar plates with a backlit gold edge on the tile border
    if i < 2 or i >= w - 2 or j < 2:
        return mul(GOLD, 0.95)
    plate = 0.75 if (j % 5 == 0 or (i + (j // 5) * 2) % 6 == 0) else 1.0
    return mul(ARMOUR, plate * grain(i, j, 61, 0.1) * 1.15)


def tex_bronze(i, j, w, h):
    band = 0.75 if j % 10 in (0, 1) else 1.0
    patina = fbm2p(i / 8.0, j / 8.0, 4, 3, 71)
    return mul(mix(BRONZE, hexrgb("5FA088"), 0.25 * smooth(0.6, 0.75, patina)), band * (0.85 + 0.3 * patina))


def tex_cloud(i, j, w, h):
    n = fbm2p(i / 8.0, j / 8.0, 4, 4, 81)
    return mix(hexrgb("C9C0F0"), CLOUD_WHITE, 0.45 + 0.55 * n)


def tex_beard(i, j, w, h):
    streak = 0.75 + 0.25 * math.sin(i * 1.3 + 2.0 * fbm2p(i / 8.0, j / 16.0, 4, 2, 91))
    return mul(RED, streak)


def tex_lattice(i, j, w, h):
    # a gold lattice window: frame, a 4 x 4 grid and diagonal diamonds, dark paper behind
    x = i % 16
    y = j % 16
    on = x in (0, 15) or y in (0, 15) or x == 8 or y == 8 or abs(x - y) == 0 or abs(x + y - 15) == 0
    if on:
        return mul(GOLD, 0.9 + 0.1 * ((i + j) % 2))
    return mul(hexrgb("5A1E14"), grain(i, j, 101, 0.1))


def tex_dark_wood(i, j, w, h):
    return mul(hexrgb("4A2418"), grain(i * 0.3, j * 2.0, 111, 0.12))


def tex_plume(i, j, w, h):
    return mul(hexrgb("E0281E"), 0.8 + 0.25 * math.sin(i * 0.9 + j * 0.2))


def tex_ridge(i, j, w, h):
    return mul(GOLD, 0.82 + 0.25 * math.cos((j % 8 - 3.5) / 4.0 * math.pi * 0.5))


def tex_stone(i, j, w, h):
    return mul(hexrgb("9A94A8"), 0.8 + 0.35 * fbm2p(i / 8.0, j / 8.0, 4, 4, 121))


def tex_cape(i, j, w, h):
    fold = 0.8 + 0.2 * math.sin(i / w * math.pi * 6.0)
    return mul(hexrgb("5A1A3A"), fold)


def tex_root(i, j, w, h):
    return mul(hexrgb("5A4030"), 0.8 + 0.3 * math.sin(i * 1.7) * 0.5 + 0.2 * fbm2p(i / 8.0, j / 8.0, 4, 2, 131))


def tex_peach(i, j, w, h):
    n = fbm2p(i / 5.0, j / 5.0, 6, 4, 141)
    blossom = rnd(i, j, 142) > 0.8
    base = mix(hexrgb("E87AA0"), hexrgb("FFD0E0"), n)
    return mul(base, 1.15 if blossom else 0.9 + 0.2 * n)


def tex_gate_light(i, j, w, h):
    dx = (i - w / 2.0 + 0.5) / (w / 2.0)
    dy = (j - h / 2.0 + 0.5) / (h / 2.0)
    r = math.sqrt(dx * dx + dy * dy)
    return mix(hexrgb("FFF6D8"), hexrgb("FFD54A"), clamp(r))


def build_atlas():
    img = Image(ATLAS, ATLAS, (0, 0, 0, 255))
    table = {
        "vermilion": tex_vermilion, "gold": tex_gold, "tile_imperial": tex_imperial, "tile_jade": tex_jade,
        "white_jade": tex_white_jade, "dragon": tex_dragon, "xiangyun": tex_xiangyun, "door": tex_door,
        "plaque_nantian": plaque(GLYPHS_NANTIAN), "plaque_lingxiao": plaque(GLYPHS_LINGXIAO), "lantern": tex_lantern,
        "rock": tex_rock, "jade_terrace": tex_jade_terrace, "crystal": tex_crystal, "armour": tex_armour,
        "bronze": tex_bronze, "cloud": tex_cloud, "beard": tex_beard, "lattice": tex_lattice, "deep_gold": tex_deep_gold,
        "jade_step": tex_jade_step, "dark_wood": tex_dark_wood, "plume": tex_plume, "ridge": tex_ridge,
        "vermilion_dark": tex_vermilion_dark, "stone": tex_stone, "cape": tex_cape, "root": tex_root, "peach": tex_peach,
        "gate_light": tex_gate_light, "door_tall": tex_door_tall,
    }
    for name, fn in table.items():
        fill_tile(img, name, fn)
    # linear filtering: there are no mipmaps, and far faces would sparkle with nearest sampling; every face UV is inset
    # by 1 px, so the filter never reaches a neighbouring tile
    return save(img, "surga_atlas.png", blur=True)


# ---------------------------------------------------------------------------------------------------------------- sky

def build_sky():
    w, h = 512, 256
    img = Image(w, h)
    zen = hexrgb("3B2E7A")
    mid = hexrgb("8A6CC8")
    rose = hexrgb("E0A0C8")
    hor = hexrgb("FFD9A0")
    haze = hexrgb("F6E8F0")
    low = hexrgb("9A86C8")
    nadir = hexrgb("4A3C8A")
    for y in range(h):
        lat = math.pi / 2.0 - math.pi * (y + 0.5) / h
        e = math.sin(lat)
        cl = math.cos(lat)
        for x in range(w):
            # KosmikMesh's convention: longitude (u - 0.5) * 2 pi, so u = 0.5 faces +z (behind the gate)
            lon = ((x + 0.5) / w - 0.5) * 2.0 * math.pi
            dx = cl * math.sin(lon)
            dy = e
            dz = cl * math.cos(lon)
            if e >= 0.35:
                c = mix(mid, zen, smooth(0.35, 1.0, e))
            elif e >= 0.0:
                t = e / 0.35
                c = mix(rose, mid, smooth(0.35, 1.0, t)) if t > 0.35 else mix(hor, rose, smooth(0.0, 0.35, t))
            elif e >= -0.2:
                c = mix(hor, haze, smooth(0.0, -0.2, e))
            else:
                c = mix(haze, low, smooth(-0.2, -0.6, e))
                c = mix(c, nadir, smooth(-0.6, -1.0, e))
            # the dawn glow behind the gate (+z) and a softer counter-glow
            ang = math.acos(clamp(dz * 0.995 + dy * 0.1, -1.0, 1.0))
            glow = math.exp(-(ang / 0.75) ** 2) * (1.0 - smooth(0.15, 0.7, abs(e)))
            c = mix(c, hexrgb("FFE8B8"), 0.7 * glow)
            # soft cloud texture in the haze band and nebula-like tint above
            n = fbm3(dx * 2.5 + 7.0, dy * 2.5, dz * 2.5, 4, 3)
            c = mul(c, 0.94 + 0.12 * n)
            # silk aurora curtains: a bright lower hem on a wavy line, fading streaks rising above it
            for (base, amp, salt, ta, tb) in ((0.5, 0.12, 5, "FF9ED8", "7EF0E0"), (0.68, 0.08, 6, "C8A0FF", "FFE0A0")):
                band = base + amp * (fbm3(dx * 1.6, dy * 1.6 + 3.0 * salt, dz * 1.6, 3, salt) - 0.5) * 4.0
                above = e - band
                if -0.03 < above < 0.22 and e > 0.2:
                    hem = math.exp(-(above / 0.018) ** 2)
                    curtain = math.exp(-max(0.0, above) / 0.07) * (0.35 + 0.65 * noise3(dx * 60.0, dy * 1.5, dz * 60.0, salt + 9))
                    k = clamp(hem * 0.9 + curtain * 0.6) * smooth(0.2, 0.32, e)
                    tint = mix(hexrgb(ta), hexrgb(tb), smooth(-0.5, 0.5, math.sin(lon * 2.0 + salt)))
                    c = mix(c, tint, 0.42 * k)
            # faint stars above e 0.2
            if e > 0.2:
                sx = int(x / 3)
                sy = int(y / 3)
                hv = rnd(sx, sy, 7)
                if hv > 0.985:
                    k = (hv - 0.985) / 0.015 * smooth(0.2, 0.6, e)
                    c = mix(c, (255, 250, 240), 0.7 * k)
            img.set(x, y, (c[0], c[1], c[2], 255))
    save(img, "sky_heaven_512.png")
    save(downsample(img, 2), "sky_heaven_256.png")
    save(downsample(img, 4), "sky_heaven_128.png")
    return img


# ---------------------------------------------------------------------------------------------------------------- clouds

def cloud_field(size, period, salt):
    dens = [[0.0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            u = x / size * period
            v = y / size * period
            dens[y][x] = fbm2p(u, v, period, 5, salt)
    return dens


def build_cloud_sea():
    size = 512
    dens = cloud_field(size, 4, 201)
    img = Image(size, size)
    shadow = hexrgb("B8AEE8")
    lit = CLOUD_WHITE
    gold = hexrgb("FFE6C0")
    for y in range(size):
        for x in range(size):
            d = dens[y][x]
            a = smooth(0.36, 0.56, d)
            d2 = dens[(y - 3) % size][(x - 3) % size]
            light = clamp(0.78 + (d - d2) * 9.0)
            c = mix(shadow, lit, light)
            c = mix(c, gold, 0.25 * smooth(0.6, 1.0, light))
            translucent(img, x, y, c, 0.92 * a)
    save(img, "cloud_sea_512.png")
    save(downsample(img, 2), "cloud_sea_256.png")


def build_cloud_puff():
    size = 128
    img = Image(size, size)
    blobs = [(0.5, 0.55, 0.32), (0.32, 0.6, 0.22), (0.68, 0.6, 0.24), (0.42, 0.42, 0.22), (0.6, 0.4, 0.2)]
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size
            v = (y + 0.5) / size
            d = 0.0
            for (bx, by, br) in blobs:
                r = math.sqrt((u - bx) ** 2 + (v - by) ** 2) / br
                d = max(d, 1.0 - r)
            n = fbm2p(u * 6.0, v * 6.0, 6, 4, 211)
            dens = d * (1.0 + 0.6 * (n - 0.5)) if d > 0.0 else -1.0
            border = min(u, v, 1.0 - u, 1.0 - v)
            a = smooth(0.0, 0.25, dens) * smooth(0.0, 0.12, border)
            light = clamp(0.6 + (0.55 - v) * 1.2 + 0.2 * (n - 0.5))
            c = mix(hexrgb("B8AEE8"), CLOUD_WHITE, light)
            translucent(img, x, y, c, a)
    save(img, "cloud_puff.png")


def build_shaft():
    w, h = 64, 256
    img = Image(w, h)
    col = hexrgb("FFE8A0")
    for y in range(h):
        v = (y + 0.5) / h
        along = (1.0 - v) ** 1.2 * smooth(0.0, 0.06, v)
        for x in range(w):
            u = (x + 0.5) / w
            across = math.exp(-((u - 0.5) / 0.2) ** 2) - math.exp(-(0.5 / 0.2) ** 2)
            streak = 0.72 + 0.28 * noise2p(u * 12.0, v * 1.5, 12, 221)
            additive(img, x, y, col, across * along * streak * 1.1)
    save(img, "shaft.png")


def build_ember_trail():
    w, h = 128, 32
    img = Image(w, h)
    for y in range(h):
        v = (y + 0.5) / h
        across = math.exp(-((v - 0.5) / 0.22) ** 2) - math.exp(-(0.5 / 0.22) ** 2)
        for x in range(w):
            u = x / (w - 1.0)
            along = (1.0 - u) ** 1.4 * smooth(0.0, 0.03, u)
            if u < 0.15:
                c = mix(hexrgb("FFF6D8"), hexrgb("FFC83A"), u / 0.15)
            elif u < 0.5:
                c = mix(hexrgb("FFC83A"), hexrgb("FF7A1A"), (u - 0.15) / 0.35)
            else:
                c = mix(hexrgb("FF7A1A"), hexrgb("A0200C"), (u - 0.5) / 0.5)
            flick = 0.75 + 0.25 * noise2p(u * 16.0, v * 4.0, 16, 231)
            additive(img, x, y, c, across * along * flick * 1.2)
    save(img, "ember_trail.png")


def build_petal():
    w, h = 128, 32
    img = Image(w, h)
    for k in range(4):
        squash = (1.0, 0.62, 0.18, 0.62)[k]
        rot = (0.3, 1.1, 1.9, 2.6)[k]
        cr = math.cos(rot)
        sr = math.sin(rot)
        for y in range(h):
            for x in range(32):
                # local petal frame
                px = (x + 0.5 - 16.0) / 12.0
                py = (y + 0.5 - 16.0) / 12.0
                lx = px * cr + py * sr
                ly = -px * sr + py * cr
                lx /= max(0.12, squash)
                # an ellipse with a notch at the tip
                inside = (lx / 0.62) ** 2 + (ly / 1.0) ** 2
                notch = ly > 0.7 and abs(lx) < 0.12
                if inside < 1.0 and not notch:
                    t = math.sqrt(inside)
                    c = mix(hexrgb("FFF0F4"), hexrgb("F58AAE"), smooth(0.1, 1.0, t))
                    if squash < 0.3:
                        c = mul(c, 0.85)
                    translucent(img, k * 32 + x, y, c, 1.0 - 0.3 * smooth(0.85, 1.0, t))
                else:
                    img.set(k * 32 + x, y, (245, 138, 174, 0))
    save(img, "petal.png")


def build_waterfall():
    w, h = 64, 256
    img = Image(w, h)
    for y in range(h):
        v = y / h
        for x in range(w):
            u = (x + 0.5) / w
            across = math.exp(-((u - 0.5) / 0.26) ** 2) - math.exp(-(0.5 / 0.26) ** 2)
            col_salt = int(u * 16)
            streak = noise2p(u * 16.0, v * 4.0, 16, 241)
            streak2 = noise2p(u * 32.0, v * 8.0, 32, 242)
            k = across * (0.35 + 0.5 * streak + 0.3 * streak2)
            c = mix(hexrgb("BFE6FF"), hexrgb("FFF2C8"), streak2)
            del col_salt
            additive(img, x, y, c, k)
    save(img, "waterfall.png")


def build_nine_rings():
    w, h = 512, 72
    img = Image(w, h)
    profile = [0.0, 0.35, 0.8, 1.0, 1.0, 0.8, 0.35, 0.0]
    violet = CRYSTAL
    gold = GOLD
    for k in range(9):
        c = mix(violet, gold, k / 8.0)
        for r in range(8):
            for x in range(w):
                p = profile[r]
                # glyph dashes: a mark every 32 px, varied per ring (periodic in 512)
                g = (x + k * 7) % 32
                mark = 0.0
                if 2 <= r <= 5 and 6 <= g <= 18:
                    pattern = h32(k, (x + k * 7) // 32 % 16) & 0xFF
                    bit = (g - 6) // 3
                    if (pattern >> bit) & 1 and (g - 6) % 3 != 2:
                        mark = 0.6
                pulse = 0.8 + 0.2 * math.cos(x / w * math.pi * 2.0 * 8.0)
                additive(img, x, k * 8 + r, mix(c, (255, 255, 240), 0.35 * mark), p * (0.55 * pulse + mark))
    save(img, "nine_rings.png")


def build_gate_glow():
    size = 128
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            dx = (x + 0.5) / size * 2.0 - 1.0
            dy = (y + 0.5) / size * 2.0 - 1.0
            r = math.sqrt(dx * dx + dy * dy)
            core = math.exp(-(r / 0.28) ** 2)
            halo = math.exp(-(r / 0.62) ** 2) * 0.5
            rays = 0.25 * math.exp(-(abs(dx) / 0.08) ** 2) * (1.0 - smooth(0.4, 1.0, abs(dy)))
            k = (core + halo + rays) * (1.0 - smooth(0.8, 1.0, r))
            c = mix(hexrgb("FFD54A"), hexrgb("FFFBEA"), clamp(core * 1.4))
            additive(img, x, y, c, k)
    save(img, "gate_glow.png")


def build_land_below():
    size = 256
    img = Image(size, size)
    # neutral, tinted at runtime: fields (Voronoi-ish patches), woods, river lines
    cells = 8
    for y in range(size):
        for x in range(size):
            u = x / size * cells
            v = y / size * cells
            best = 9.0
            second = 9.0
            bid = 0
            cx0 = int(math.floor(u))
            cy0 = int(math.floor(v))
            for j in (-1, 0, 1):
                for i in (-1, 0, 1):
                    gx = cx0 + i
                    gy = cy0 + j
                    px = gx + rnd(gx % cells, gy % cells, 251)
                    py = gy + rnd(gx % cells, gy % cells, 252)
                    d = (u - px) ** 2 + (v - py) ** 2
                    if d < best:
                        second = best
                        best = d
                        bid = h32(gx % cells, gy % cells, 253)
                    elif d < second:
                        second = d
            field = 0.58 + 0.28 * ((bid & 0xFF) / 255.0)
            edge = math.sqrt(second) - math.sqrt(best)
            if edge < 0.04:
                field *= 0.8
            wood = fbm2p(x / 32.0, y / 32.0, 8, 4, 254)
            if wood > 0.58:
                field *= 0.62
            river = abs(fbm2p(x / 64.0, y / 64.0, 4, 3, 255) - 0.5)
            g = int(255 * clamp(field))
            if river < 0.012:
                img.set(x, y, (70, 90, 120, 255))
            else:
                img.set(x, y, (g, g, int(g * 0.95), 255))
    save(img, "land_below_256.png")


# ---------------------------------------------------------------------------------------------------------------- models

class Model:
    def __init__(self, mid, box, expect):
        self.id = mid
        self.box = box  # (x0, y0, z0, x1, y1, z1) bound of every corner
        self.expect = expect  # the table's cuboid count
        self.parts = {}

    def part(self, name, pivot=(0.0, 0.0, 0.0)):
        if name not in self.parts:
            self.parts[name] = {"pivot": [float(p) for p in pivot], "cuboids": []}
        return self.parts[name]

    def box_(self, part, a, b, tex, lod=0, glow=False, tint="FFFFFF", rot=None, hide=(), keep=()):
        p = self.part(part)
        x0, y0, z0 = (min(a[i], b[i]) for i in range(3))
        x1, y1, z1 = (max(a[i], b[i]) for i in range(3))
        p["cuboids"].append({"from": [x0, y0, z0], "to": [x1, y1, z1], "tex": tex, "lod": lod, "glow": glow, "tint": tint,
                             "rot": rot, "hide": set(hide), "keep": set(keep)})


def face_rect(c, face):
    """(plane axis, plane value, outward sign, (axis u, lo, hi), (axis v, lo, hi)) of a face."""
    f, t = c["from"], c["to"]
    if face == "south":
        return 2, f[2], -1, (0, f[0], t[0]), (1, f[1], t[1])
    if face == "north":
        return 2, t[2], 1, (0, f[0], t[0]), (1, f[1], t[1])
    if face == "west":
        return 0, f[0], -1, (2, f[2], t[2]), (1, f[1], t[1])
    if face == "east":
        return 0, t[0], 1, (2, f[2], t[2]), (1, f[1], t[1])
    if face == "up":
        return 1, t[1], 1, (0, f[0], t[0]), (2, f[2], t[2])
    return 1, f[1], -1, (0, f[0], t[0]), (2, f[2], t[2])


def covered(c, face, others):
    """True when another unrotated cuboid of the same part (drawn whenever c is) covers this face from outside."""
    if c["rot"] is not None or c["glow"]:
        return False
    axis, plane, sign, (ua, u0, u1), (va, v0, v1) = face_rect(c, face)
    for o in others:
        if o is c or o["rot"] is not None or o["glow"] or o["lod"] > c["lod"]:
            continue
        of, ot = o["from"], o["to"]
        if sign < 0:
            beyond = of[axis] < plane - 1e-6 and ot[axis] >= plane - 1e-6
        else:
            beyond = ot[axis] > plane + 1e-6 and of[axis] <= plane + 1e-6
        if not beyond:
            continue
        if of[ua] <= u0 + 1e-6 and ot[ua] >= u1 - 1e-6 and of[va] <= v0 + 1e-6 and ot[va] >= v1 - 1e-6:
            return True
    return False


def face_uv(tex, du, dv):
    """Atlas UVs [u0, v0, u1, v1] of a face of du x dv stage metres: texels kept about square, 1 px inset."""
    tx, ty, tw, th = TILES[tex]
    iw = tw - 2
    ih = th - 2
    if du <= 0 or dv <= 0:
        return [tx + 1, ty + 1, tx + 2, ty + 2]
    if du / dv >= iw / ih:
        su = iw
        sv = max(1, int(round(iw * dv / du)))
    else:
        sv = ih
        su = max(1, int(round(ih * du / dv)))
    u0 = tx + 1 + (iw - su) // 2
    v0 = ty + 1 + (ih - sv) // 2
    return [u0, v0, u0 + su, v0 + sv]


def export(models):
    out = {"atlas": ATLAS_ID, "atlasSize": ATLAS, "models": {}}
    stats = {}
    for m in models:
        parts = {}
        quads = [0, 0, 0]
        cuboids = 0
        for pname, p in m.parts.items():
            items = []
            for c in p["cuboids"]:
                faces = {}
                for face in FACES:
                    if face in c["hide"] and face not in c["keep"]:
                        continue
                    if face not in c["keep"] and covered(c, face, p["cuboids"]):
                        continue
                    _, _, _, (ua, u0, u1), (va, v0, v1) = face_rect(c, face)
                    faces[face] = face_uv(c["tex"], u1 - u0, v1 - v0)
                if not faces:
                    continue
                cuboids += 1
                for q in range(3):
                    if c["lod"] <= q:
                        quads[q] += len(faces)
                item = {"from": [round(v, 3) for v in c["from"]], "to": [round(v, 3) for v in c["to"]],
                        "faces": faces, "lod": c["lod"], "glow": c["glow"], "tint": c["tint"]}
                if c["rot"] is not None:
                    item["rot"] = c["rot"]
                items.append(item)
            parts[pname] = {"pivot": p["pivot"], "cuboids": items}
        out["models"][m.id] = {"parts": parts}
        stats[m.id] = (cuboids, quads, m)
    os.makedirs(os.path.dirname(MODELS_OUT), exist_ok=True)
    with open(MODELS_OUT, "w", newline="\n") as f:
        json.dump(out, f, separators=(",", ":"), sort_keys=False)
        f.write("\n")
    WRITTEN.append(MODELS_OUT)
    return out, stats


def rot_y(angle, origin):
    return {"axis": "y", "angle": round(angle, 3), "origin": [round(v, 3) for v in origin]}


def dragon(m, part, cx, cz, y0, y1, radius, segments, step_deg, lod, thick=2.2, start_deg=0.0):
    """A dragon coiled round a vertical pillar: a band of segments on a helix, each a plate as tall as one step's rise (so
    the coil has no gaps), turned about the pillar axis (either turning sense is a valid coil); the face toward the pillar
    is omitted. Then the head with horns and a red beard."""
    rise = (y1 - y0) / segments
    height = rise * 1.12
    arc = radius * math.radians(abs(step_deg))
    length = arc * 1.25 + thick * 0.5
    for i in range(segments):
        y = y0 + rise * i
        ang = start_deg + step_deg * i
        a = (cx + radius - thick / 2.0, y, cz - length / 2.0)
        b = (cx + radius + thick / 2.0, y + height, cz + length / 2.0)
        m.box_(part, a, b, "dragon", lod=lod, rot=rot_y(ang, (cx, y, cz)), hide=("west",))
    ang = start_deg + step_deg * segments
    hy = y1 + 0.5
    m.box_(part, (cx + radius - 1.8, hy, cz - 2.4), (cx + radius + 1.8, hy + 3.2, cz + 2.4), "dragon", lod=lod,
           rot=rot_y(ang, (cx, hy, cz)), hide=("west",))
    m.box_(part, (cx + radius - 0.4, hy + 3.2, cz - 1.6), (cx + radius + 0.4, hy + 5.0, cz - 0.9), "gold", lod=2,
           rot=rot_y(ang, (cx, hy, cz)), hide=("down",))
    m.box_(part, (cx + radius - 1.2, hy - 2.2, cz - 1.6), (cx + radius + 1.2, hy + 0.2, cz + 1.0), "beard", lod=2,
           rot=rot_y(ang, (cx, hy, cz)), hide=("up",))


def stepped_roof(m, part, y, slabs, tex, tips=None, tip_lod=1):
    """A hip roof as stepped slabs [(half x, half z, thickness, lod, z centre)] (no sloped cuboids, so no rotation sense
    can turn a roof upside down), with upturned eave tips at the corners of the first slab. Returns the top."""
    y0 = y
    for (hx, hz, th, lod, zc) in slabs:
        m.box_(part, (-hx, y, zc - hz), (hx, y + th, zc + hz), tex, lod=lod)
        y += th
    if tips:
        hx, hz, th0, _, zc = slabs[0]
        s = tips
        for sx in (-1, 1):
            for sz in (-1, 1):
                a = (sx * (hx - s * 0.5), y0 + th0 * 0.5, zc + sz * (hz - s * 0.5))
                b = (sx * (hx + s * 0.9), y0 + th0 * 0.5 + s, zc + sz * (hz + s * 0.9))
                m.box_(part, a, b, "ridge", lod=tip_lod)
    return y


def model_gerbang():
    m = Model("gerbang", (-60, -14, -40, 60, 122, 20), 70)
    b = "base"
    m.part(b)
    # base platform (seen from below while the camera rises out of the clouds), a cloud cushion under it
    m.box_(b, (-56, -6, -16), (-18, 0, 16), "white_jade")
    m.box_(b, (-18, -6, -16), (18, 0, 16), "white_jade")
    m.box_(b, (18, -6, -16), (56, 0, 16), "white_jade")
    m.box_(b, (-48, -12, -12), (48, -6, 12), "cloud", lod=1, hide=("up",))
    # five broad white-jade steps down to the cloud stairs
    for i in range(5):
        m.box_(b, (-15, -(i + 1), -16 - (i + 1) * 2.4), (15, -i, -16 - i * 2.4), "jade_step", hide=("down",))
    # pillars: two large inner (opening x -13..13, y 0..60), two outer; plinths and cloud capitals
    for sx in (-1, 1):
        cx = sx * 16.0
        m.box_(b, (cx - 4.5, 0, -4.5), (cx + 4.5, 3, 4.5), "white_jade", lod=1, hide=("down",))
        m.box_(b, (cx - 3, 3, -3), (cx + 3, 33, 3), "vermilion")
        m.box_(b, (cx - 3, 33, -3), (cx + 3, 60, 3), "vermilion")
        m.box_(b, (cx - 5.5, 66, -5.5), (cx + 5.5, 69, 5.5), "xiangyun")
        ox = sx * 37.0
        m.box_(b, (ox - 3.4, 0, -3.4), (ox + 3.4, 2.4, 3.4), "white_jade", lod=1, hide=("down",))
        m.box_(b, (ox - 2.2, 0, -2.2), (ox + 2.2, 66, 2.2), "vermilion", hide=("down",))
        m.box_(b, (ox - 4, 66, -4), (ox + 4, 69, 4), "xiangyun", lod=1)
        # gold dragons with red beards coiled round the inner pillars (Journey to the West, ch. 4)
        dragon(m, b, cx, 0.0, 6.0, 54.0, 4.2, 9, 60.0 * sx, 1, start_deg=90.0 if sx > 0 else 270.0)
        # side bays: carved white-jade dado and a gold lattice screen, a lintel
        x0, x1 = (19.0, 34.8) if sx > 0 else (-34.8, -19.0)
        m.box_(b, (x0, 0, -1.6), (x1, 12, 1.6), "xiangyun", lod=1, hide=("down",))
        m.box_(b, (x0, 12, -1.2), (x1, 44, 1.2), "lattice", lod=1)
        m.box_(b, (x0 - 0.6 if sx < 0 else x0, 44, -2.4), (x1 + 0.6 if sx > 0 else x1, 50, 2.4), "vermilion_dark", lod=1)
        m.box_(b, (x0, 50, -1.2), (x1, 69, 1.2), "vermilion_dark", lod=1)
    # the pillar shafts above the bays: inner shafts continue to the lintel
    m.box_(b, (-19, 60, -3), (-13, 66, 3), "vermilion")
    m.box_(b, (13, 60, -3), (19, 66, 3), "vermilion")
    # lintel over the opening, a gold strip, the frieze and the plaque 南天門
    m.box_(b, (-13, 60, -4), (13, 66, 4), "vermilion_dark")
    m.box_(b, (-40, 62, -4.3), (40, 63, -4.0), "gold", lod=1)
    m.box_(b, (-40, 69, -3.2), (40, 79, 3.2), "vermilion_dark")
    m.box_(b, (-10.5, 68.8, -4.2), (10.5, 79.2, -3.2), "deep_gold", lod=1)
    m.box_(b, (-9, 70, -4.8), (9, 78, -4.2), "deep_gold")
    # the plaque face itself: a thin board in front, its north face carries the strokes
    m.box_(b, (-8.6, 70.4, -5.0), (8.6, 77.6, -4.8), "plaque_nantian")
    # triple-eave roof: tier 1, a body, tier 2, a body, tier 3, ridge with chiwen ends
    y = stepped_roof(m, b, 79.0, [(50, 15, 2, 0, 0.0), (46, 12, 2, 1, 0.0), (42, 9, 2, 1, 0.0)], "tile_imperial", tips=3.0)
    m.box_(b, (-36, y, -6), (36, y + 7, 6), "lattice")
    y += 7
    y = stepped_roof(m, b, y, [(44, 12.5, 2, 0, 0.0), (40, 9.5, 2, 1, 0.0), (36, 6.5, 2, 1, 0.0)], "tile_imperial", tips=2.6,
                     tip_lod=2)
    m.box_(b, (-24, y, -4.5), (24, y + 6, 4.5), "vermilion_dark")
    y += 6
    y = stepped_roof(m, b, y, [(31, 10, 2, 0, 0.0), (27, 7, 2, 1, 0.0), (23, 4, 2, 1, 0.0)], "tile_imperial", tips=2.2,
                     tip_lod=2)
    m.box_(b, (-25, y, -1.5), (25, y + 3, 1.5), "ridge")
    for sx in (-1, 1):
        m.box_(b, (sx * 24.5 - 2, y, -2), (sx * 24.5 + 2, y + 8, 2), "gold", lod=1)
        m.box_(b, (sx * 27.0 - 1.5, y + 6, -1.5), (sx * 27.0 + 1.5, y + 9, 1.5), "gold", lod=2)
    # the doors, on pivots at the inner pillar faces (opened by the stage, 0 -> 100 degrees, toward +z)
    m.part("pintu_kiri", (-13.0, 0.0, 0.0))
    m.box_("pintu_kiri", (-12.9, 0.1, -0.6), (-0.05, 59.9, 0.6), "door_tall")
    m.part("pintu_kanan", (13.0, 0.0, 0.0))
    m.box_("pintu_kanan", (0.05, 0.1, -0.6), (12.9, 59.9, 0.6), "door_tall")
    return m


def model_aula():
    m = Model("aula", (-72, -1, -8, 72, 76, 98), 80)
    b = "base"
    m.part(b)
    # three-tier white-jade terrace
    m.box_(b, (-70, 0, 0), (0, 4, 96), "white_jade", hide=("down",))
    m.box_(b, (0, 0, 0), (70, 4, 96), "white_jade", hide=("down",))
    m.box_(b, (-60, 4, 6), (60, 8, 90), "white_jade")
    m.box_(b, (-50, 8, 12), (50, 12, 84), "white_jade")
    # front steps (non-overlapping blocks) up each tier
    for i, (z0, z1, y1) in enumerate(((-6, -4, 1.3), (-4, -2, 2.7), (-2, 0, 4.0))):
        m.box_(b, (-10, 0, z0), (10, y1, z1), "jade_step", hide=("down",))
    for i, (z0, z1, y1) in enumerate(((2, 4, 5.4), (4, 6, 6.7))):
        m.box_(b, (-9, 4, z0), (9, y1, z1), "jade_step", lod=1, hide=("down",))
    for i, (z0, z1, y1) in enumerate(((8, 10, 9.4), (10, 12, 10.7))):
        m.box_(b, (-8, 8, z0), (8, y1, z1), "jade_step", lod=1, hide=("down",))
    # balustrades
    m.box_(b, (-70, 4, 0), (-10, 5.6, 1), "white_jade", lod=1, hide=("down",))
    m.box_(b, (10, 4, 0), (70, 5.6, 1), "white_jade", lod=1, hide=("down",))
    m.box_(b, (-70, 4, 1), (-69, 5.6, 96), "white_jade", lod=2, hide=("down",))
    m.box_(b, (69, 4, 1), (70, 5.6, 96), "white_jade", lod=2, hide=("down",))
    m.box_(b, (-60, 8, 6), (-9, 9.4, 7), "white_jade", lod=2, hide=("down",))
    m.box_(b, (9, 8, 6), (60, 9.4, 7), "white_jade", lod=2, hide=("down",))
    # twelve columns: eight across the front, two more down each side
    for x in (-35, -25, -15, -5, 5, 15, 25, 35):
        m.box_(b, (x - 1.2, 12, 18), (x + 1.2, 40, 20.4), "vermilion", lod=0 if abs(x) in (5, 25) else 1, hide=("down", "up"))
    for sx in (-1, 1):
        for z in (40, 60):
            m.box_(b, (sx * 40 - 1.2, 12, z), (sx * 40 + 1.2, 40, z + 2.4), "vermilion", lod=2, hide=("down", "up"))
    # walls: gold lattice either side of the great door, side and back walls
    m.box_(b, (-38, 12, 24), (-8, 40, 25), "lattice", hide=("down", "up"))
    m.box_(b, (-8, 12, 24.2), (8, 40, 25), "door", hide=("down", "up"))
    m.box_(b, (8, 12, 24), (38, 40, 25), "lattice", hide=("down", "up"))
    m.box_(b, (-38, 12, 25), (-37, 40, 70), "vermilion_dark", lod=1, hide=("down", "up"))
    m.box_(b, (37, 12, 25), (38, 40, 70), "vermilion_dark", lod=1, hide=("down", "up"))
    m.box_(b, (-37, 12, 69), (37, 40, 70), "vermilion_dark", lod=1, hide=("down", "up"))
    # architrave, gold trim, the plaque 凌霄寶殿 hanging between the front columns
    m.box_(b, (-42, 40, 16), (42, 43, 72), "vermilion_dark")
    m.box_(b, (-42, 40.5, 15.7), (42, 41.5, 16), "gold", lod=1)
    m.box_(b, (-9, 31.2, 17.0), (9, 38.8, 17.6), "deep_gold", lod=1)
    m.box_(b, (-8, 32, 16.6), (8, 38, 17.0), "plaque_lingxiao")
    # double-eave hip roof in gold glaze, the upper body, ridge, chiwen and ridge beasts
    y = stepped_roof(m, b, 43.0, [(48, 34, 2, 0, 44.0), (44, 30, 2, 1, 44.0), (40, 26, 2, 1, 44.0)], "tile_imperial", tips=3.0,
                     tip_lod=2)
    m.box_(b, (-30, y, 26), (30, y + 6, 62), "lattice")
    y += 6
    y = stepped_roof(m, b, y, [(38, 24, 2, 0, 44.0), (33, 19, 2, 1, 44.0), (27, 14, 2, 1, 44.0), (20, 8, 2, 1, 44.0)],
                     "tile_imperial", tips=2.6, tip_lod=2)
    m.box_(b, (-22, y, 42), (22, y + 3, 46), "ridge")
    for sx in (-1, 1):
        m.box_(b, (sx * 21 - 2, y, 41.5), (sx * 21 + 2, y + 8, 46.5), "gold", lod=1)
    for k in range(5):
        x = -44 + k * 2.6
        m.box_(b, (x, 47, 10.5), (x + 1.4, 48.6, 11.9), "gold", lod=2)
    return m


def model_halaman():
    """The heavenly court from the gate (z 16) to the hall (z 400), cut in 32-sm slabs so no face spans too wide a
    distance ratio (32.1.3)."""
    m = Model("halaman", (-42, -42, 14, 42, 3, 402), 30)
    b = "base"
    m.part(b)
    z = 16.0
    while z < 400.0 - 1e-6:
        z1 = min(400.0, z + 32.0)
        m.box_(b, (-40, -5, z), (40, 0, z1), "white_jade")
        z = z1
    for k in range(6):
        z0 = 16.0 + k * 64.0
        m.box_(b, (-6, 0, z0), (6, 0.25, z0 + 64.0), "jade_step", lod=1, hide=("down",))
    for sx in (-1, 1):
        for k in range(4):
            z0 = 16.0 + k * 96.0
            m.box_(b, (sx * 39.5 - 0.5, 0, z0), (sx * 39.5 + 0.5, 2.0, z0 + 96.0), "white_jade", lod=1, hide=("down",))
    for k in range(4):
        z0 = 30.0 + k * 89.0
        m.box_(b, (-34, -14, z0), (34, -5, z0 + 89.0), "rock", hide=("up",))
    for k in range(3):
        z0 = 70.0 + k * 92.0
        m.box_(b, (-22, -26, z0), (22, -14, z0 + 92.0), "rock", lod=1, hide=("up",))
    for k in range(2):
        z0 = 130.0 + k * 78.0
        m.box_(b, (-10, -40, z0), (10, -26, z0 + 78.0), "rock", lod=2, hide=("up",))
    return m


def model_paviliun():
    m = Model("paviliun", (-13, -2, -13, 13, 19, 13), 24)
    b = "base"
    m.part(b)
    m.box_(b, (-9, -1.5, -9), (9, 0, 9), "white_jade")
    for k in range(8):
        a = k * math.pi / 4.0
        x = 7.0 * math.cos(a)
        z = 7.0 * math.sin(a)
        m.box_(b, (x - 0.6, 0, z - 0.6), (x + 0.6, 10, z + 0.6), "vermilion", lod=0 if k % 2 == 0 else 1, hide=("down", "up"))
    m.box_(b, (-8, 10, -8), (8, 11.2, 8), "vermilion_dark")
    y = stepped_roof(m, b, 11.2, [(11, 11, 1.2, 0, 0.0), (8.5, 8.5, 1.2, 0, 0.0), (6, 6, 1.2, 0, 0.0), (3.5, 3.5, 1.2, 1, 0.0)],
                     "tile_jade", tips=1.6, tip_lod=2)
    m.box_(b, (-0.5, y, -0.5), (0.5, y + 2.5, 0.5), "gold", lod=1)
    m.box_(b, (-1.5, 0, -1.5), (1.5, 2, 1.5), "white_jade", lod=2, hide=("down",))
    return m


def model_pagoda():
    m = Model("pagoda", (-12, -2, -12, 12, 76, 12), 40)
    b = "base"
    m.part(b)
    m.box_(b, (-10, -1.5, -10), (10, 0, 10), "white_jade")
    y = 0.0
    for i in range(9):
        w = 7.0 - 0.6 * i
        hgt = 6.0 - 0.25 * i
        m.box_(b, (-w, y, -w), (w, y + hgt, w), "vermilion" if i % 2 == 0 else "lattice")
        y += hgt
        e = w + 2.4
        m.box_(b, (-e, y, -e), (e, y + 0.9, e), "tile_jade" if i % 2 else "tile_imperial")
        m.box_(b, (-(e - 1.4), y + 0.9, -(e - 1.4)), (e - 1.4, y + 1.6, e - 1.4), "tile_jade" if i % 2 else "tile_imperial", lod=1)
        y += 1.6
        if i in (0, 4):
            for sx in (-1, 1):
                for sz in (-1, 1):
                    m.box_(b, (sx * e, y - 1.6, sz * e), (sx * (e + 1.2), y - 0.2, sz * (e + 1.2)), "ridge", lod=2)
    m.box_(b, (-0.6, y, -0.6), (0.6, y + 5, 0.6), "gold")
    m.box_(b, (-1.4, y + 1.5, -1.4), (1.4, y + 2.3, 1.4), "gold", lod=1)
    return m


def model_jembatan():
    m = Model("jembatan", (-1, -3, -5, 81, 12, 5), 20)
    b = "base"
    m.part(b)
    n = 7
    for j in range(n):
        x0 = 80.0 * j / n
        x1 = 80.0 * (j + 1) / n
        top = 8.0 * math.sin(math.pi * (j + 0.5) / n)
        m.box_(b, (x0, top - 2.0, -4), (x1, top, 4), "white_jade")
    for j in range(1, n):
        x = 80.0 * j / n
        top = max(8.0 * math.sin(math.pi * (j - 0.5) / n), 8.0 * math.sin(math.pi * (j + 0.5) / n))
        for sz in (-1, 1):
            m.box_(b, (x - 0.5, top, sz * 4 - 0.5), (x + 0.5, top + 2.2, sz * 4 + 0.5), "jade_step", lod=1 if j % 2 else 2,
                   hide=("down",))
    m.box_(b, (24, -2, -3), (56, 4.5, 3), "white_jade", lod=2, hide=("up",))
    return m


def model_pulau(mid, radius, layers, expect, crystals, roots, trees):
    m = Model(mid, (-radius - 6, -radius * 1.6 - 14, -radius - 6, radius + 6, 16, radius + 6), expect)
    b = "base"
    m.part(b)
    r = radius
    if r >= 24:
        c = r * 0.7
        m.box_(b, (-c, -3, -r), (c, 0, r), "jade_terrace")
        m.box_(b, (-r, -3, -c), (-c, 0, c), "jade_terrace")
        m.box_(b, (c, -3, -c), (r, 0, c), "jade_terrace")
    else:
        m.box_(b, (-r, -3, -r), (r, 0, r), "jade_terrace")
    y = -3.0
    for k in range(layers):
        hs = r * 0.92 * (1.0 - (k + 1) / (layers + 1.0)) ** 1.1
        th = r * 0.32
        ox = (rnd(len(mid), k, 301) - 0.5) * r * 0.15
        oz = (rnd(len(mid), k, 302) - 0.5) * r * 0.15
        m.box_(b, (ox - hs, y - th, oz - hs * 0.85), (ox + hs, y, oz + hs * 0.85), "rock", hide=("up",))
        if k < layers - 1:
            hs2 = hs * 0.7
            ox2 = ox + (rnd(len(mid), k, 303) - 0.5) * hs
            m.box_(b, (ox2 - hs2 * 0.6, y - th * 1.3, oz - hs2), (ox2 + hs2 * 0.6, y - th * 0.4, oz + hs2), "stone", lod=1)
        y -= th
    for k in range(roots):
        a = rnd(len(mid), k, 311) * math.pi * 2.0
        rr = r * (0.3 + 0.4 * rnd(len(mid), k, 312))
        x = math.cos(a) * rr
        z = math.sin(a) * rr
        ln = r * (0.5 + 0.6 * rnd(len(mid), k, 313))
        top = -3.0 - r * 0.32 * (0.5 + 0.8 * rr / r)
        m.box_(b, (x - 0.4, top - ln, z - 0.4), (x + 0.4, top, z + 0.4), "root", lod=2, hide=("up",))
    for k in range(crystals):
        a = rnd(len(mid), k, 321) * math.pi * 2.0
        rr = r * (0.45 + 0.3 * rnd(len(mid), k, 322))
        x = math.cos(a) * rr
        z = math.sin(a) * rr
        yy = -3.0 - r * (0.2 + 0.5 * rnd(len(mid), k, 323))
        s = 1.2 + 1.6 * rnd(len(mid), k, 324)
        m.box_(b, (x - s * 0.5, yy - s * 2.0, z - s * 0.5), (x + s * 0.5, yy, z + s * 0.5), "crystal", lod=1 if k < 2 else 2,
               glow=True)
    for k in range(trees):
        a = (k + 0.3) * math.pi * 2.0 / max(1, trees)
        x = math.cos(a) * r * 0.45
        z = math.sin(a) * r * 0.45
        m.box_(b, (x - 0.6, 0, z - 0.6), (x + 0.6, 5, z + 0.6), "dark_wood", lod=1, hide=("down", "up"))
        m.box_(b, (x - 3.5, 5, z - 3.5), (x + 3.5, 9, z + 3.5), "peach", lod=1)
        m.box_(b, (x - 2.2, 9, z - 2.2), (x + 2.2, 11, z + 2.2), "peach", lod=1, hide=("down",))
    return m


def model_tiang_naga():
    m = Model("tiang_naga", (-12, -41, -12, 12, 98, 12), 18)
    b = "base"
    m.part(b)
    m.box_(b, (-4, -40, -4), (4, 10, 4), "vermilion")
    m.box_(b, (-4, 10, -4), (4, 50, 4), "vermilion")
    m.box_(b, (-4, 50, -4), (4, 90, 4), "vermilion")
    m.box_(b, (-6.5, 90, -6.5), (6.5, 94, 6.5), "xiangyun")
    m.box_(b, (-5, 94, -5), (5, 97, 5), "cloud", lod=1, hide=("down",))
    dragon(m, b, 0.0, 0.0, -6.0, 80.0, 5.4, 9, 80.0, 1, thick=2.6, start_deg=0.0)
    return m


def model_pengawal_raja():
    m = Model("pengawal_raja", (-5, 0, -3, 6, 18, 3), 14)
    b = "base"
    m.part(b)
    m.box_(b, (-2.2, 0, -1), (2.2, 5.5, 1), "armour", hide=("down", "up"))
    m.box_(b, (-2.8, 4.5, -1.6), (2.8, 7.5, 1.6), "armour")
    m.box_(b, (-2.6, 7.5, -1.4), (2.6, 11, 1.4), "armour")
    m.box_(b, (-2.9, 7.3, -1.7), (2.9, 8.0, 1.7), "gold", lod=2, hide=("up", "down"))
    m.box_(b, (-4, 9.8, -1.6), (-2.2, 11.2, 1.6), "armour", lod=1)
    m.box_(b, (2.2, 9.8, -1.6), (4, 11.2, 1.6), "armour", lod=1)
    m.box_(b, (-3.6, 6.5, -0.8), (-2.6, 9.8, 0.8), "armour", lod=2, hide=("up",))
    m.box_(b, (2.6, 6.5, -0.8), (3.6, 9.8, 0.8), "armour", lod=2, hide=("up",))
    m.box_(b, (-1.1, 11, -1.1), (1.1, 12.6, 1.1), "armour", hide=("down",))
    m.box_(b, (-1.4, 12.6, -1.4), (1.4, 13.6, 1.4), "gold")
    m.box_(b, (-0.3, 13.6, -0.6), (0.3, 15.4, 1.6), "plume", lod=1, hide=("down",))
    m.box_(b, (-2.8, 3, 1.4), (2.8, 11, 1.9), "cape", lod=1)
    m.box_(b, (4.0, 0, -1.6), (4.4, 16, -1.2), "dark_wood", hide=("down",))
    m.box_(b, (3.6, 15.4, -1.7), (4.8, 17.6, -1.1), "gold")
    m.box_(b, (4.4, 14.0, -1.6), (5.6, 15.0, -1.2), "gold", lod=2)
    return m


def model_pengawal():
    m = Model("pengawal", (-2.5, 0, -1.5, 3, 7.5, 1.5), 7)
    b = "base"
    m.part(b)
    m.box_(b, (-1.0, 0, -0.5), (1.0, 2.6, 0.5), "armour", hide=("down",))
    m.box_(b, (-1.3, 2.6, -0.7), (1.3, 5.0, 0.7), "armour")
    m.box_(b, (-1.9, 4.3, -0.75), (1.9, 5.0, 0.75), "armour", lod=1)
    m.box_(b, (-0.55, 5.0, -0.55), (0.55, 5.9, 0.55), "armour", hide=("down",))
    m.box_(b, (-0.7, 5.9, -0.7), (0.7, 6.4, 0.7), "gold")
    m.box_(b, (1.9, 0, -0.9), (2.1, 7.0, -0.7), "dark_wood", hide=("down",))
    m.box_(b, (1.8, 7.0, -1.0), (2.2, 7.4, -0.6), "gold")
    return m


def model_menara_lonceng():
    m = Model("menara_lonceng", (-13, -21, -13, 13, 34, 13), 18)
    b = "base"
    m.part(b)
    m.box_(b, (-10, -3, -10), (10, 0, 10), "white_jade")
    m.box_(b, (-8, -12, -8), (8, -3, 8), "rock", hide=("up",))
    m.box_(b, (-5, -20, -5), (5, -12, 5), "rock", lod=1, hide=("up",))
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box_(b, (sx * 6 - 1, 0, sz * 6 - 1), (sx * 6 + 1, 22, sz * 6 + 1), "vermilion", hide=("down", "up"))
    m.box_(b, (-8, 22, -8), (8, 24, 8), "vermilion_dark")
    y = stepped_roof(m, b, 24.0, [(11, 11, 2, 0, 0.0), (8, 8, 2, 0, 0.0), (5, 5, 2, 1, 0.0)], "tile_imperial", tips=1.8, tip_lod=2)
    m.box_(b, (-0.6, y, -0.6), (0.6, y + 3, 0.6), "gold", lod=1, hide=("down",))
    m.box_(b, (-7, 21, -0.6), (7, 22, 0.6), "dark_wood", hide=("up",))
    m.box_(b, (-1, 18, -1), (1, 21, 1), "bronze", lod=2)
    m.box_(b, (-3, 11, -3), (3, 18, 3), "bronze")
    m.box_(b, (-3.6, 10, -3.6), (3.6, 11.2, 3.6), "bronze", lod=2)
    return m


def model_lentera():
    m = Model("lentera", (-1.6, 0, -1.6, 1.6, 10.4, 1.6), 3)
    b = "base"
    m.part(b)
    m.box_(b, (-0.4, 0, -0.4), (0.4, 7, 0.4), "dark_wood", lod=1, hide=("down", "up"))
    m.box_(b, (-1.2, 7, -1.2), (1.2, 9.6, 1.2), "lantern", glow=True)
    m.box_(b, (-1.5, 9.6, -1.5), (1.5, 10.2, 1.5), "gold", lod=1)
    return m


def model_tangga_awan():
    m = Model("tangga_awan", (-20, -44, -122, 20, -2, -26), 12)
    b = "base"
    m.part(b)
    for k in range(10):
        z1 = -28.0 - 9.0 * k
        z0 = z1 - 9.6
        top = -5.0 - 3.5 * (k + 1)
        wob = (rnd(k, 401) - 0.5) * 3.0
        m.box_(b, (-12 + wob, top - 2.2, z0), (12 + wob, top, z1), "cloud", lod=0 if k < 4 else 1)
    for sx in (-1, 1):
        m.box_(b, (sx * 13 - 3, -24, -80), (sx * 13 + 3, -18, -60), "cloud", lod=1)
    return m


def build_models():
    models = [
        model_gerbang(), model_aula(), model_halaman(), model_paviliun(), model_pagoda(), model_jembatan(),
        model_pulau("pulau_s", 18, 3, 10, 2, 2, 0),
        model_pulau("pulau_m", 30, 4, 16, 2, 4, 1),
        model_pulau("pulau_l", 40, 5, 24, 4, 6, 2),
        model_tiang_naga(), model_pengawal_raja(), model_pengawal(), model_menara_lonceng(), model_lentera(),
        model_tangga_awan(),
    ]
    return models


# Instances per quality (Minimal, Normal, Maximal) = SurgaLayout in the Java stage; used for the totals.
INSTANCES = {
    "gerbang": (1, 1, 1), "aula": (1, 1, 1), "halaman": (1, 1, 1), "paviliun": (0, 2, 4), "pagoda": (0, 0, 2),
    "jembatan": (0, 2, 3), "pulau_s": (0, 1, 2), "pulau_m": (1, 1, 2), "pulau_l": (1, 1, 2), "tiang_naga": (0, 4, 8),
    "pengawal_raja": (0, 4, 4), "pengawal": (0, 0, 12), "menara_lonceng": (1, 1, 1), "lentera": (0, 8, 16),
    "tangga_awan": (1, 1, 1),
}
# Model quads per quality if everything were in view at once (the stage culls by view cone and asks the budget per
# instance, so a frame draws far fewer).
MODEL_CAP = (900, 2600, 5200)


# ---------------------------------------------------------------------------------------------------------------- validate

def corners(c):
    f, t = c["from"], c["to"]
    pts = [(x, y, z) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
    r = c.get("rot")
    if not r:
        return pts
    a = math.radians(r["angle"])
    o = r["origin"]
    ca, sa = math.cos(a), math.sin(a)
    out = []
    for (x, y, z) in pts:
        x -= o[0]
        y -= o[1]
        z -= o[2]
        if r["axis"] == "y":
            x, z = x * ca + z * sa, -x * sa + z * ca
        elif r["axis"] == "x":
            y, z = y * ca - z * sa, y * sa + z * ca
        else:
            x, y = x * ca - y * sa, x * sa + y * ca
        out.append((x + o[0], y + o[1], z + o[2]))
    return out


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    return int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big")


def validate(out, stats, images):
    errors = []
    for mid, (count, quads, m) in stats.items():
        exp = m.expect
        if not (exp * 0.6 <= count <= exp * 1.45):
            errors.append("%s: %d cuboids, the table says about %d" % (mid, count, exp))
        bx0, by0, bz0, bx1, by1, bz1 = m.box
        for pname, p in out["models"][mid]["parts"].items():
            for c in p["cuboids"]:
                f, t = c["from"], c["to"]
                if not all(t[i] - f[i] > 1e-6 for i in range(3)):
                    errors.append("%s/%s: zero-size cuboid %s %s" % (mid, pname, f, t))
                for (x, y, z) in corners(c):
                    if not (bx0 - 1e-3 <= x <= bx1 + 1e-3 and by0 - 1e-3 <= y <= by1 + 1e-3 and bz0 - 1e-3 <= z <= bz1 + 1e-3):
                        errors.append("%s/%s: corner %.2f %.2f %.2f outside the box" % (mid, pname, x, y, z))
                        break
                for face, uv in c["faces"].items():
                    if not (0 <= uv[0] < uv[2] <= ATLAS and 0 <= uv[1] < uv[3] <= ATLAS):
                        errors.append("%s/%s: %s UV %s outside the atlas" % (mid, pname, face, uv))
                if c["lod"] not in (0, 1, 2):
                    errors.append("%s/%s: bad lod" % (mid, pname))
    totals = [0, 0, 0]
    for mid, (count, quads, m) in stats.items():
        inst = INSTANCES[mid]
        for q in range(3):
            totals[q] += quads[q] * inst[q]
    for q in range(3):
        if totals[q] > MODEL_CAP[q]:
            errors.append("quality %d: %d model quads with everything in view (cap %d)" % (q, totals[q], MODEL_CAP[q]))
    # seamless tiles: first and last columns / rows of the tileable maps differ little
    for name in ("cloud_sea_512.png", "land_below_256.png", "waterfall.png", "nine_rings.png"):
        img = images.get(name)
        if img is None:
            continue
        diff = 0
        for y in range(img.height):
            a = img.get(0, y)
            b = img.get(img.width - 1, y)
            diff = max(diff, max(abs(a[i] - b[i]) for i in range(4)))
        limit = 120 if name != "nine_rings.png" else 140
        if diff > limit:
            errors.append("%s: the u seam differs by %d" % (name, diff))
        if name != "nine_rings.png":
            diff = 0
            for x in range(img.width):
                a = img.get(x, 0)
                b = img.get(x, img.height - 1)
                diff = max(diff, max(abs(a[i] - b[i]) for i in range(4)))
            if diff > 120:
                errors.append("%s: the v seam differs by %d" % (name, diff))
    # transparent borders on the glow and puff textures
    for name in ("cloud_puff.png", "gate_glow.png", "shaft.png", "ember_trail.png"):
        img = images.get(name)
        if img is None:
            continue
        for x in range(img.width):
            for y in (0, img.height - 1):
                if img.get(x, y)[3] > 0 and name != "shaft.png":
                    errors.append("%s: border pixel %d,%d not transparent" % (name, x, y))
                    break
        for y in range(img.height):
            for x in (0, img.width - 1):
                if img.get(x, y)[3] > 0:
                    errors.append("%s: border pixel %d,%d not transparent" % (name, x, y))
                    break
    expected = {
        "surga_atlas.png": (256, 256), "sky_heaven_128.png": (128, 64), "sky_heaven_256.png": (256, 128),
        "sky_heaven_512.png": (512, 256), "cloud_sea_256.png": (256, 256), "cloud_sea_512.png": (512, 512),
        "cloud_puff.png": (128, 128), "shaft.png": (64, 256), "ember_trail.png": (128, 32), "petal.png": (128, 32),
        "waterfall.png": (64, 256), "nine_rings.png": (512, 72), "gate_glow.png": (128, 128), "land_below_256.png": (256, 256),
    }
    total = 0
    for name, size in expected.items():
        path = os.path.join(OUT, name)
        if not os.path.exists(path):
            errors.append("missing " + name)
            continue
        if png_size(path) != size:
            errors.append("%s: %s, expected %s" % (name, png_size(path), size))
        total += os.path.getsize(path)
    total += os.path.getsize(MODELS_OUT)
    print("model quads with everything in view: Minimal %d, Normal %d, Maximal %d" % tuple(totals))
    for mid, (count, quads, m) in stats.items():
        print("  %-15s %3d cuboids, quads %s" % (mid, count, quads))
    print("total size %.1f KB" % (total / 1024.0))
    if errors:
        for e in errors:
            print("ERROR", e)
        sys.exit(1)
    print("ok")


def main():
    images = {}
    images["surga_atlas.png"] = build_atlas()
    build_sky()
    build_cloud_sea()
    build_cloud_puff()
    build_shaft()
    build_ember_trail()
    build_petal()
    build_waterfall()
    build_nine_rings()
    build_gate_glow()
    build_land_below()
    # reload the outputs the checks read (the builders keep no references)
    for name in ("cloud_sea_512.png", "land_below_256.png", "waterfall.png", "nine_rings.png", "cloud_puff.png",
                 "gate_glow.png", "shaft.png", "ember_trail.png"):
        images[name] = read_png(os.path.join(OUT, name))
    out, stats = export(build_models())
    validate(out, stats, images)


def read_png(path):
    """A minimal reader for our own 8-bit RGBA, non-interlaced files (filter 0 rows, as png.py writes)."""
    import zlib
    with open(path, "rb") as f:
        data = f.read()
    pos = 8
    idat = b""
    w = h = 0
    while pos < len(data):
        n = int.from_bytes(data[pos:pos + 4], "big")
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            w = int.from_bytes(body[0:4], "big")
            h = int.from_bytes(body[4:8], "big")
        elif kind == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    img = Image(w, h)
    stride = w * 4 + 1
    for y in range(h):
        row = raw[y * stride + 1:(y + 1) * stride]
        for x in range(w):
            img.px[y * w + x] = list(row[x * 4:x * 4 + 4])
    return img


if __name__ == "__main__":
    main()
