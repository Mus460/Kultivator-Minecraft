"""Generates the textures of module D, Dimensi baru (DESIGN 34.5.13 Dunia Bawah, 34.5.14 Laut Tanpa Batas, 34.6 D).

Entity textures (textures/dunia/dimensi/entity/):
    headwear.png        64 x 512  the head pieces of D's people (client Headwear): 16 material bands of 32 px
                                  (0 black gauze, 1 gold, 2 horn, 3 ox fur, 4 white hair, 5 straw, 6 jade, 7 crimson,
                                  8 dark fur, 9 glow, 10 teal lacquer, 11 pearl); every cube's unwrap starts at the
                                  band's top-left, so each band is a seamless material.
    kepiting_batu.png  128 x 512  the rock crab (KepitingRenderer): 0 carapace, 1 barnacle rocks, 2 moss, 3 belly,
                                  4 eyes, 5 mouth, 6 pincer, 7 leg tips.
    penyu_ao.png       256 x 512  the turtle (PenyuRenderer), bands of 64: 0 plastron, 1 shell base, 2 shell middle,
                                  3 mossy shell top, 4 horn scutes, 5 skin, 6 beak, 7 eyes.
    ular_laut.png      128 x 512  the sea serpent (UlarLautRenderer): 0 scales, 1 belly, 2 fin, 3 horn, 4 eyes,
                                  6 teeth, 7 gold whiskers, 8 pearl.
    kepala_lembu.png    64 x 64   Ox-Head's player skin: fur, lamellar iron, bronze mirror, red sash, hooves.
    muka_kuda.png       64 x 64   Horse-Face's player skin: dark fur, blue-black lamellar, teal sash.
    hantu_lapar.png     64 x 64   a hungry ghost: grey-violet, ribs, a swollen belly, rags, long nails.
    raja_naga.png       64 x 64   the Dragon King: jade scales, gold eyes, the golden dragon robe with sea-wave hems.
    yaksha.png          64 x 64   a yaksha: blue skin, red eyes, tusks, tiger-skin loincloth, gold rings.

Item icons (textures/item/, the item atlas): dupa_arwah, lentera_arwah, giok_arwah, surat_arwah,
mutiara_penunjuk_laut, mutiara_penghindar_air, mutiara_naga, air_mata_air_penglai.

Run with: python tools/gen_dunia_dimensi_textures.py [preview_dir]   (8x previews into preview_dir)
Deterministic: the same bytes on every run. Writes only module D's files.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator",
                    "textures")
ENTITY = os.path.join(ROOT, "dunia", "dimensi", "entity")
ITEM = os.path.join(ROOT, "item")
WRITTEN = []


# ================================================================================================== helpers
def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def mix(a, b, t):
    t = clamp(t)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def shade(c, f):
    return tuple(int(clamp(c[i] * f, 0, 255)) for i in range(3))


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


class Noise:
    """Tileable value noise (period cells), smooth interpolation, fbm."""

    def __init__(self, seed, period=8):
        rnd = random.Random(seed)
        self.p = period
        self.g = [[rnd.random() for _ in range(period)] for _ in range(period)]

    def at(self, x, y):
        p = self.p
        x0, y0 = int(math.floor(x)), int(math.floor(y))
        fx, fy = x - x0, y - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.g[y0 % p][x0 % p]
        b = self.g[y0 % p][(x0 + 1) % p]
        c = self.g[(y0 + 1) % p][x0 % p]
        d = self.g[(y0 + 1) % p][(x0 + 1) % p]
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy

    def fbm(self, x, y, octaves=3):
        v, amp, tot, f = 0.0, 1.0, 0.0, 1.0
        for _ in range(octaves):
            v += self.at(x * f, y * f) * amp
            tot += amp
            amp *= 0.5
            f *= 2.0
        return v / tot


def hash01(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name)
    img.save(path)
    WRITTEN.append((path, img))
    return path


def band(img, index, height, fn):
    """Fills band `index` (rows index*height ..) with fn(x, y) -> rgb or rgba."""
    for y in range(height):
        for x in range(img.width):
            c = fn(x, y)
            img.set(x, index * height + y, c if len(c) == 4 else rgba(c))


# ================================================================================================== materials
def m_black(x, y):
    n = NZ[0].fbm(x / 6.0, y / 6.0)
    weave = 0.06 if (x + y) % 3 == 0 else (0.02 if (x - y) % 3 == 0 else 0.0)
    sheen = 0.10 * max(0.0, math.sin((x * 0.35 + y * 0.18)))
    return shade((26, 24, 32), 0.85 + 0.35 * n + weave * 4 + sheen)


def m_gold(x, y):
    n = NZ[1].fbm(x / 5.0, y / 5.0)
    base = mix((176, 120, 30), (250, 214, 100), 0.35 + 0.65 * n)
    if y % 8 in (0,):
        base = shade(base, 0.72)
    if (x + 2 * (y // 8)) % 6 == 0 and y % 8 in (3, 4):
        base = (255, 240, 170)
    if hash01(x, y, 11) > 0.97:
        base = (255, 250, 220)
    return base


def m_horn(x, y):
    n = NZ[2].fbm(x / 7.0, y / 4.0)
    t = 0.5 + 0.5 * math.sin(y * 0.9 + n * 3.0)
    return mix((150, 120, 84), (236, 222, 190), 0.25 + 0.6 * t + 0.15 * n)


def m_fur(base, dark, light, seed):
    nz = Noise(seed, 16)

    def f(x, y):
        streak = nz.fbm(x / 1.6, y / 7.0)
        n = nz.fbm(x / 6.0, y / 6.0)
        c = mix(dark, base, 0.35 + 0.65 * n)
        if streak > 0.62:
            c = mix(c, light, (streak - 0.62) * 2.4)
        elif streak < 0.3:
            c = mix(c, dark, (0.3 - streak) * 2.0)
        return c
    return f


def m_white(x, y):
    strand = NZ[3].fbm(x / 1.4, y / 9.0)
    c = mix((196, 196, 204), (250, 250, 246), 0.4 + 0.6 * strand)
    if strand < 0.32:
        c = shade(c, 0.86)
    return c


def m_straw(x, y):
    cell = ((x // 3) + (y // 3)) % 2
    u, v = x % 3, y % 3
    if cell:
        t = 1.0 - abs(u - 1) * 0.25
    else:
        t = 1.0 - abs(v - 1) * 0.25
    n = NZ[4].at(x / 3.0, y / 3.0)
    c = mix((150, 112, 50), (232, 196, 110), t * (0.7 + 0.3 * n))
    if (u == 0 and cell) or (v == 0 and not cell):
        c = shade(c, 0.78)
    return c


def m_jade(x, y):
    n = NZ[5].fbm(x / 9.0, y / 9.0, 4)
    vein = abs(math.sin((x * 0.21 + y * 0.13) * 3.1 + n * 7.0))
    c = mix((46, 120, 80), (150, 220, 170), 0.3 + 0.7 * n)
    if vein < 0.12:
        c = mix(c, (220, 248, 226), 0.55)
    return c


def m_crimson(x, y):
    n = NZ[6].fbm(x / 4.0, y / 8.0)
    flick = 0.5 + 0.5 * math.sin(x * 0.7 + n * 6.0 - y * 0.25)
    c = mix((120, 14, 18), (214, 40, 30), n)
    if flick > 0.82:
        c = mix(c, (255, 140, 50), (flick - 0.82) * 4.0)
    return c


def m_glow(x, y):
    n = NZ[7].fbm(x / 3.0, y / 3.0)
    return mix((255, 70, 30), (255, 220, 120), 0.25 + 0.6 * n)


def m_teal(x, y):
    n = NZ[8].fbm(x / 5.0, y / 5.0)
    c = mix((22, 74, 82), (64, 156, 160), 0.3 + 0.7 * n)
    row = y % 4
    off = 2 if (y // 4) % 2 else 0
    if row == 0:
        c = shade(c, 0.66)
    elif row == 1:
        c = shade(c, 1.22)
    if (x + off) % 4 == 0 and row > 0:
        c = shade(c, 0.8)
    return c


def m_pearl(x, y):
    n = NZ[9].fbm(x / 6.0, y / 6.0)
    hue = (x * 0.07 + y * 0.05 + n) * math.tau
    tint = (int(225 + 25 * math.sin(hue)), int(228 + 22 * math.sin(hue + 2.1)), int(236 + 19 * math.sin(hue + 4.2)))
    return mix((210, 214, 222), tint, 0.6 + 0.4 * n)


NZ = [Noise(1000 + i, 16) for i in range(12)]


# ================================================================================================== headwear
def headwear():
    img = Image(64, 512, (0, 0, 0, 0))
    mats = [m_black, m_gold, m_horn, m_fur((122, 66, 38), (60, 30, 18), (176, 112, 70), 301), m_white, m_straw, m_jade,
            m_crimson, m_fur((52, 46, 48), (20, 18, 20), (100, 88, 80), 302), m_glow, m_teal, m_pearl]
    for i, fn in enumerate(mats):
        band(img, i, 32, fn)
    return save(img, ENTITY, "headwear.png")


# ================================================================================================== crab
def crab():
    img = Image(128, 512, (0, 0, 0, 0))
    nz = Noise(2001, 32)

    def carapace(x, y):
        n = nz.fbm(x / 9.0, y / 9.0, 4)
        c = mix((52, 46, 44), (124, 108, 96), n)
        crack = abs(nz.fbm(x / 4.0 + 7, y / 4.0 + 3) - 0.5)
        if crack < 0.03:
            c = shade(c, 0.55)
        spot = hash01(x // 3, y // 3, 5)
        if spot > 0.9:
            c = mix(c, (160, 84, 60), 0.5)
        if hash01(x, y, 6) > 0.985:
            c = (190, 180, 168)
        return c

    def barnacle(x, y):
        n = nz.fbm(x / 6.0, y / 6.0)
        c = mix((90, 90, 92), (150, 148, 140), n)
        cx, cy = (x // 6) * 6 + 3, (y // 6) * 6 + 3
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        if hash01(x // 6, y // 6, 9) > 0.35:
            if 1.2 < d < 2.6:
                c = (226, 222, 206)
            elif d <= 1.2:
                c = (54, 50, 52)
        return c

    def moss(x, y):
        n = nz.fbm(x / 3.0, y / 3.0)
        return mix((40, 84, 30), (120, 168, 60), n)

    def belly(x, y):
        n = nz.fbm(x / 8.0, y / 8.0)
        c = mix((176, 150, 116), (232, 214, 180), n)
        if y % 5 == 0:
            c = shade(c, 0.74)
        return c

    def eyes(x, y):
        c = (18, 16, 18)
        if (x % 4, y % 4) in ((1, 1),):
            c = (240, 236, 200)
        elif (x + y) % 5 == 0:
            c = (220, 160, 40)
        return c

    def mouth(x, y):
        c = mix((70, 24, 20), (130, 50, 40), nz.at(x / 2.0, y / 2.0))
        if x % 3 == 0:
            c = shade(c, 0.6)
        return c

    def pincer(x, y):
        n = nz.fbm(x / 7.0, y / 7.0, 4)
        c = mix((110, 44, 34), (196, 96, 64), n)
        if nz.fbm(x / 3.0 + 9, y / 3.0) > 0.7:
            c = mix(c, (60, 52, 50), 0.6)
        if y % 11 == 0:
            c = shade(c, 0.7)
        return c

    def foot(x, y):
        n = nz.fbm(x / 5.0, y / 5.0)
        c = mix((60, 34, 28), (150, 80, 56), n)
        if x % 7 == 0:
            c = (210, 190, 160)
        return c

    for i, fn in enumerate([carapace, barnacle, moss, belly, eyes, mouth, pincer, foot]):
        band(img, i, 32, fn)
    return save(img, ENTITY, "kepiting_batu.png")


# ================================================================================================== turtle
def turtle():
    img = Image(256, 512, (0, 0, 0, 0))
    nz = Noise(3001, 32)

    def hexcell(x, y, size):
        """Scutes as Voronoi cells of a jittered hexagonal lattice: (half the gap between the two nearest centres,
        the nearest centre). The lattice tiles the band horizontally (256 is a multiple of every size used)."""
        px, py = x + 0.5, y + 0.5
        best, second, centre = 1e9, 1e9, (0.0, 0.0)
        row0 = int(py // (size * 0.87))
        for row in range(row0 - 1, row0 + 2):
            cy0 = row * size * 0.87
            off = size / 2.0 if row % 2 else 0.0
            col0 = int((px - off) // size)
            for col in range(col0 - 1, col0 + 2):
                jx = (hash01(col % (256 // size), row, 91) - 0.5) * size * 0.3
                jy = (hash01(col % (256 // size), row, 92) - 0.5) * size * 0.3
                cx, cy = col * size + off + size / 2.0 + jx, cy0 + size / 2.0 + jy
                d = math.hypot(px - cx, py - cy)
                if d < best:
                    second, best, centre = best, d, (cx, cy)
                elif d < second:
                    second = d
        return (second - best) / 2.0, centre

    def plastron(x, y):
        n = nz.fbm(x / 10.0, y / 10.0)
        c = mix((70, 66, 40), (150, 138, 84), n)
        if y % 12 == 0 or x % 22 == 0:
            c = shade(c, 0.6)
        return c

    def shell(base, light, edge, size, seed):
        def f(x, y):
            d, (cx, cy) = hexcell(x, y, size)
            n = nz.fbm(x / 8.0 + seed, y / 8.0)
            c = mix(base, light, 0.25 + 0.5 * n)
            # growth rings inside each scute
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if int(r) % 3 == 0:
                c = shade(c, 0.88)
            if d < 1.0:
                c = edge
            elif d < 2.0:
                c = shade(c, 0.8)
            return c
        return f

    def mossy(x, y):
        n = nz.fbm(x / 6.0, y / 6.0, 4)
        m = nz.fbm(x / 14.0 + 3, y / 14.0)
        c = mix((80, 64, 36), (130, 104, 60), n)
        if m > 0.45:
            c = mix((44, 96, 34), (124, 176, 70), n)
        if hash01(x, y, 77) > 0.993:
            c = (230, 230, 120)
        return c

    def horn(x, y):
        n = nz.fbm(x / 5.0, y / 9.0)
        t = 0.5 + 0.5 * math.sin(y * 0.6 + n * 4.0)
        return mix((96, 62, 26), (196, 140, 64), 0.3 + 0.6 * t)

    def skin(x, y):
        n = nz.fbm(x / 7.0, y / 7.0, 4)
        c = mix((70, 92, 74), (140, 160, 120), n)
        sx, sy = x % 4, y % 4
        if sx == 0 or sy == 0:
            c = shade(c, 0.82)
        if hash01(x // 4, y // 4, 33) > 0.86:
            c = mix(c, (210, 200, 150), 0.5)
        return c

    def beak(x, y):
        n = nz.fbm(x / 6.0, y / 4.0)
        return mix((70, 64, 50), (176, 160, 110), n * 0.9)

    def eye(x, y):
        c = (12, 12, 14)
        if (x % 5) in (1, 2) and (y % 5) == 1:
            c = (250, 240, 200)
        elif (x + y) % 6 == 0:
            c = (200, 150, 40)
        return c

    fns = [plastron, shell((92, 64, 34), (168, 118, 62), (40, 26, 14), 16, 1.0),
           shell((120, 82, 40), (206, 150, 72), (46, 30, 14), 20, 4.0), mossy, horn, skin, beak, eye]
    for i, fn in enumerate(fns):
        band(img, i, 64, fn)
    return save(img, ENTITY, "penyu_ao.png")


# ================================================================================================== sea serpent
def serpent():
    img = Image(128, 512, (0, 0, 0, 0))
    nz = Noise(4001, 32)

    def scales(x, y):
        row = y // 3
        off = 2 if row % 2 else 0
        u, v = (x + off) % 4, y % 3
        n = nz.fbm(x / 10.0, y / 10.0, 4)
        c = mix((16, 64, 96), (40, 150, 170), 0.3 + 0.7 * n)
        if v == 0:
            c = shade(c, 0.55)
        elif v == 1 and u in (1, 2):
            c = mix(c, (150, 230, 230), 0.35)
        if u == 0:
            c = shade(c, 0.75)
        return c

    def belly(x, y):
        c = mix((196, 186, 150), (246, 238, 208), nz.at(x / 6.0, y / 6.0))
        if y % 4 == 0:
            c = shade(c, 0.7)
        return c

    def fin(x, y):
        t = (y % 32) / 31.0
        c = mix((40, 150, 160), (150, 70, 170), t)
        if x % 4 == 0:
            c = shade(c, 0.55)
        return c

    def horn(x, y):
        return m_horn(x, y)

    def eyes(x, y):
        c = (255, 210, 40)
        if x % 3 == 1:
            c = (20, 10, 0)
        return c

    def teeth(x, y):
        return mix((220, 216, 200), (255, 255, 248), nz.at(x / 2.0, y / 2.0))

    fns = {0: scales, 1: belly, 2: fin, 3: horn, 4: eyes, 6: teeth, 7: m_gold, 8: m_pearl}
    for i, fn in fns.items():
        band(img, i, 32, fn)
    return save(img, ENTITY, "ular_laut.png")


# ================================================================================================== skins
# Box UV of the 64 x 64 player skin: part -> (u, v, w, h, d); overlays -> (u, v) of the same part's second layer.
PARTS = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "rarm": (40, 16, 4, 12, 4), "larm": (32, 48, 4, 12, 4),
         "rleg": (0, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4)}
OVER = {"head": (32, 0), "body": (16, 32), "rarm": (40, 32), "larm": (48, 48), "rleg": (0, 32), "lleg": (0, 48)}


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def paint(img, part, fn, overlay=False):
    """fn(face, x, y, w, h) -> rgb / rgba / None for every pixel of the part's six faces (x 0 = the face's left)."""
    u, v, w, h, d = PARTS[part]
    if overlay:
        u, v = OVER[part]
    for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(face, x, y, fw, fh)
                if c is None:
                    continue
                img.set(fx + x, fy + y, c if len(c) == 4 else rgba(c))


def lamellar(x, y, base, edge, rivet, seed):
    """Rows of small lamellar plates (2 high, 2 wide, offset each row) with dark laces and bright rivets."""
    row = y // 2
    off = row % 2
    c = shade(base, 1.0 + 0.18 * (hash01((x + off) // 2, row, seed) - 0.5))
    if y % 2 == 1:
        c = shade(c, 0.78)
    if (x + off) % 2 == 0 and y % 2 == 0:
        c = shade(c, 1.15)
    if (x + off) % 4 == 0 and y % 2 == 1:
        c = rivet
    if (x + off) % 2 == 1 and y % 2 == 1 and hash01(x, y, seed + 1) > 0.7:
        c = edge
    return c


def guard_skin(name, fur, fur_dark, fur_light, armor, armor_edge, rivet, sash, eye, seed):
    """Ox-Head / Horse-Face: beast fur, lamellar coat, bronze mirror, sash, bracers, hooves."""
    img = Image(64, 64, (0, 0, 0, 0))
    furf = m_fur(fur, fur_dark, fur_light, seed)

    def head(face, x, y, w, h):
        c = furf(x + 8 * len(face), y)
        if face == "front":
            if y in (3,) and x in (1, 2, 5, 6):
                return eye
            if y >= 5:
                return mix(c, fur_light, 0.35)
        return c
    paint(img, "head", head)

    def body(face, x, y, w, h):
        if face in ("top", "bottom"):
            return shade(armor, 0.7)
        if y == 8 or y == 9:
            c = sash if not (face == "front" and x in (3, 4)) else (210, 170, 70)
            return shade(c, 0.85) if y == 9 else c
        if y < 2 and face in ("front", "back"):
            return furf(x, y)
        c = lamellar(x, y, armor, armor_edge, rivet, seed)
        if face == "front" and 3 <= y <= 6 and 2 <= x <= 5:
            d = math.hypot(x - 3.5, y - 4.5)
            if d <= 2.2:
                return (210, 170, 80) if d > 1.4 else (250, 230, 160) if (x, y) == (3, 4) else (190, 150, 60)
        return c
    paint(img, "body", body)

    def arm(face, x, y, w, h):
        if face == "top":
            return furf(x, y)
        if face == "bottom":
            return shade(fur_dark, 0.6)
        if y < 3:
            return furf(x + 3, y)
        if y < 9:
            c = mix((70, 46, 30), (110, 74, 46), hash01(x, y, seed + 3))
            if y in (4, 7):
                c = (190, 150, 70)
            return c
        c = furf(x, y + 9)
        if y == 11:
            c = shade(fur_dark, 0.7)
        return c
    paint(img, "rarm", arm)
    paint(img, "larm", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return shade(armor, 0.8)
        if face == "bottom":
            return (26, 20, 16)
        if y < 4:
            return lamellar(x, y + 1, armor, armor_edge, rivet, seed + 5)
        if y >= 10:
            return (34, 26, 20) if y == 10 else (22, 16, 12)
        c = (52, 44, 50)
        if y in (6, 8):
            c = (84, 58, 36)
        return c
    paint(img, "rleg", leg)
    paint(img, "lleg", leg)

    # overlays: pauldrons on the sleeves, a lamellar skirt on the pants
    def pauldron(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "top":
            return rgba(lamellar(x, y, armor, armor_edge, rivet, seed + 7))
        if y < 4:
            c = lamellar(x, y, armor, armor_edge, rivet, seed + 7)
            if y == 3:
                c = (176, 134, 60)
            return rgba(c)
        return None
    paint(img, "rarm", pauldron, overlay=True)
    paint(img, "larm", pauldron, overlay=True)

    def skirt(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if y < 5:
            c = lamellar(x, y, armor, armor_edge, rivet, seed + 9)
            if y == 4:
                c = shade(sash, 0.8)
            return rgba(c)
        return None
    paint(img, "rleg", skirt, overlay=True)
    paint(img, "lleg", skirt, overlay=True)
    return save(img, ENTITY, name)


def ghost_skin():
    img = Image(64, 64, (0, 0, 0, 0))
    skin = (138, 128, 152)
    dark = (70, 62, 84)
    rag = (92, 88, 100)
    nz = Noise(5001, 16)

    def tone(x, y, f=1.0):
        return shade(mix(dark, skin, 0.55 + 0.45 * nz.fbm(x / 4.0, y / 4.0)), f)

    def head(face, x, y, w, h):
        c = tone(x + 10 * len(face), y)
        if face == "front":
            if y in (2, 3, 4) and x in (1, 2, 5, 6):
                return (20, 14, 26) if y > 2 else shade(dark, 0.7)
            if y in (5, 6) and x in (0, 7):
                return shade(c, 0.75)
            if y == 6 and 2 <= x <= 5:
                return (30, 18, 30)
            if y == 7 and x in (2, 5):
                return (200, 196, 180)
        return c
    paint(img, "head", head)

    def body(face, x, y, w, h):
        c = tone(x, y + 8)
        if face == "front":
            if 1 <= y <= 5 and y % 2 == 1 and 1 <= x <= 6:
                c = shade(c, 0.72)
            if 6 <= y <= 9:
                d = math.hypot(x - 3.5, (y - 7.5) * 1.3)
                if d < 3.2:
                    c = mix(c, (190, 176, 200), 0.5 * (1 - d / 3.2))
        if y >= 9:
            jag = hash01(x, 0, 71 + len(face)) > 0.45
            if y < 11 or jag:
                c = mix(rag, (60, 56, 66), hash01(x, y, 72))
        return c
    paint(img, "body", body)

    def arm(face, x, y, w, h):
        c = tone(x + 4, y)
        if face in ("front", "back", "left", "right") and y % 3 == 1:
            c = shade(c, 0.9)
        if y >= 10 or face == "bottom":
            c = (38, 30, 46)
        if y < 2 and face != "top":
            c = mix(rag, dark, 0.3)
        return c
    paint(img, "rarm", arm)
    paint(img, "larm", arm)

    def leg(face, x, y, w, h):
        c = tone(x + 9, y + 3, 0.95)
        if y < 3:
            c = mix(rag, (60, 56, 66), hash01(x, y, 73))
        if y == 11 or face == "bottom":
            c = (38, 30, 46)
        return c
    paint(img, "rleg", leg)
    paint(img, "lleg", leg)

    def shreds(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if y < 2 or (y < 7 and hash01(x, y // 2, 74 + len(face)) > 0.5):
            return rgba(mix(rag, (50, 46, 58), hash01(x, y, 75)), 230)
        return None
    paint(img, "body", shreds, overlay=True)
    paint(img, "rleg", shreds, overlay=True)
    paint(img, "lleg", shreds, overlay=True)
    return save(img, ENTITY, "hantu_lapar.png")


def king_skin():
    img = Image(64, 64, (0, 0, 0, 0))
    scale_a, scale_b = (112, 176, 136), (60, 122, 92)
    gold, gold_d = (226, 178, 62), (150, 104, 30)
    teal, wave = (30, 112, 130), (180, 230, 236)

    def scales(x, y):
        off = 1 if (y // 2) % 2 else 0
        c = scale_a if ((x + off) // 2 + y // 2) % 2 else mix(scale_a, scale_b, 0.45)
        if y % 2 == 1:
            c = shade(c, 0.84)
        return c

    def head(face, x, y, w, h):
        c = scales(x, y)
        if face == "front":
            if y == 2 and x in (0, 1, 2, 5, 6, 7):
                return (34, 70, 46)
            if y == 3 and x in (1, 2, 5, 6):
                return (255, 210, 60) if x in (1, 6) else (40, 24, 0)
            if y == 5 and x in (3, 4):
                return (40, 80, 56)
            if y == 6 and 2 <= x <= 5:
                return (96, 30, 30)
            if y == 7 and x in (2, 5):
                return (240, 240, 220)
        if face == "top":
            return mix(scale_b, (30, 70, 50), 0.4)
        return c
    paint(img, "head", head)

    def robe(face, x, y, w, h, hem_from):
        if face in ("top", "bottom"):
            return gold_d
        c = gold if (x + y) % 5 else mix(gold, (255, 226, 130), 0.5)
        if y >= hem_from:
            k = y - hem_from
            c = teal if (x + k) % 3 else wave
            if k == 0:
                c = gold_d
        return c

    def body(face, x, y, w, h):
        c = robe(face, x, y, w, h, 10)
        if face == "front":
            # the coiled dragon on the chest: an S of red-gold scales
            if (y, x) in ((1, 2), (1, 3), (1, 4), (2, 5), (3, 4), (3, 3), (4, 2), (5, 3), (5, 4), (6, 5)):
                c = (196, 46, 30)
            if (y, x) in ((1, 5), (2, 6)):
                c = (255, 236, 150)
            if y == 0 and x in (3, 4):
                c = teal
        if y in (7, 8) and face != "top":
            c = (70, 170, 120) if (x % 3) else (230, 200, 90)
        return c
    paint(img, "body", body)

    def arm(face, x, y, w, h):
        if y >= 10:
            c = scales(x, y)
            if y == 11 and face == "front" and x % 2 == 0:
                c = (40, 40, 30)
            return c
        c = robe(face, x, y, w, h, 7)
        return c
    paint(img, "rarm", arm)
    paint(img, "larm", arm)

    def leg(face, x, y, w, h):
        if y >= 10 or face == "bottom":
            return (26, 22, 20) if y != 10 else gold_d
        return robe(face, x, y, w, h, 7)
    paint(img, "rleg", leg)
    paint(img, "lleg", leg)

    def collar(face, x, y, w, h):
        if face == "front" and y < 2 and x not in (3, 4):
            return rgba(teal if y == 0 else wave)
        if face in ("back", "left", "right") and y < 2:
            return rgba(teal)
        return None
    paint(img, "body", collar, overlay=True)
    return save(img, ENTITY, "raja_naga.png")


def yaksha_skin():
    img = Image(64, 64, (0, 0, 0, 0))
    blue, blue_d, blue_l = (60, 110, 170), (30, 58, 108), (120, 170, 220)
    gold = (232, 186, 70)
    nz = Noise(6001, 16)

    def tone(x, y):
        return mix(blue_d, blue, 0.5 + 0.5 * nz.fbm(x / 4.0, y / 4.0))

    def tiger(x, y):
        c = (220, 132, 40)
        if (x * 2 + y * 3 + int(3 * math.sin(y * 0.9))) % 7 < 2:
            c = (40, 24, 16)
        return c

    def head(face, x, y, w, h):
        c = tone(x + 9 * len(face), y)
        if face == "front":
            if y == 2 and x in (0, 1, 2, 5, 6, 7):
                return (20, 30, 60)
            if y == 3 and x in (1, 2, 5, 6):
                return (255, 60, 40) if x in (2, 5) else (255, 220, 120)
            if y == 5 and x in (3, 4):
                return blue_d
            if y == 6:
                return (110, 20, 30) if 1 <= x <= 6 else c
            if y == 7 and x in (1, 6):
                return (246, 240, 220)
            if y == 7 and 2 <= x <= 5:
                return (90, 16, 24)
        return c
    paint(img, "head", head)

    def body(face, x, y, w, h):
        c = tone(x, y + 8)
        if face == "front":
            if y in (2, 3) and x in (0, 1, 2, 5, 6, 7):
                c = mix(c, blue_l, 0.4) if y == 2 else shade(c, 0.8)
            if 4 <= y <= 7 and x in (3, 4):
                c = shade(c, 0.75)
            if 4 <= y <= 7 and y % 2 == 1 and x not in (3, 4):
                c = shade(c, 0.85)
            if y == 1 and x % 2 == 0:
                c = (236, 228, 200)
        if y >= 8:
            c = tiger(x + len(face) * 3, y)
            if y == 8:
                c = gold
        return c
    paint(img, "body", body)

    def arm(face, x, y, w, h):
        c = tone(x + 5, y)
        if y in (2, 9):
            c = gold
        if y >= 11 or face == "bottom":
            c = (20, 30, 50)
        return c
    paint(img, "rarm", arm)
    paint(img, "larm", arm)

    def leg(face, x, y, w, h):
        c = tone(x + 11, y + 2)
        if y < 2:
            c = tiger(x, y + 5)
        if y == 9:
            c = gold
        if y >= 11 or face == "bottom":
            c = (20, 26, 44)
        return c
    paint(img, "rleg", leg)
    paint(img, "lleg", leg)

    def loincloth(face, x, y, w, h):
        if face in ("front", "back") and y < 6 and (y < 4 or 1 <= x <= 2):
            return rgba(tiger(x, y + 2))
        return None
    paint(img, "rleg", loincloth, overlay=True)
    paint(img, "lleg", loincloth, overlay=True)
    return save(img, ENTITY, "yaksha.png")


# ================================================================================================== item icons
def icon():
    return Image(16, 16, (0, 0, 0, 0))


def outline(img, color=(24, 18, 30, 255)):
    """A 1 px dark outline around every opaque pixel (classic item style)."""
    solid = [[img.get(x, y)[3] > 0 for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if solid[y][x]:
                continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and solid[y + dy][x + dx]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.set(x, y, color)


def orb(img, cx, cy, r, dark, mid, light, highlight=(255, 255, 255)):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                lx, ly = (x + 0.5 - (cx - r * 0.35)), (y + 0.5 - (cy - r * 0.35))
                t = clamp(1.0 - math.hypot(lx, ly) / (r * 1.6))
                c = mix(dark, mid, clamp(1.0 - d / r + 0.35)) if t < 0.4 else mix(mid, light, (t - 0.4) / 0.6)
                img.set(x, y, rgba(c))
    hx, hy = int(cx - r * 0.45), int(cy - r * 0.45)
    img.set(hx, hy, rgba(highlight))
    img.set(hx + 1, hy, rgba(mix(highlight, light, 0.5)))


def item_dupa():
    img = icon()
    # three incense sticks leaning in a red paper band, glowing tips, a curl of pale soul smoke
    sticks = [(5, 14, 7, 5), (7, 14, 8, 4), (9, 14, 10, 5)]
    for (x0, y0, x1, y1) in sticks:
        steps = y0 - y1
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = y0 - i
            img.set(x, y, rgba((126, 74, 44) if i % 2 else (150, 92, 54)))
        img.set(x1, y1 - 1, rgba((255, 120, 50)))
        img.set(x1, y1 - 2, rgba((255, 214, 140)))
    for x in range(5, 11):
        img.set(x, 11, rgba((190, 34, 30)))
        img.set(x, 12, rgba((150, 24, 24)))
    img.set(7, 11, rgba((236, 196, 80)))
    for (x, y, a) in ((8, 1, 150), (9, 2, 170), (10, 1, 120), (7, 0, 90), (11, 2, 110), (6, 2, 120)):
        img.set(x, y, (200, 210, 255, a))
    outline(img)
    return save(img, ITEM, "dupa_arwah.png")


def item_lentera():
    img = icon()
    # a pale paper lantern with a soul flame, black frame, a hook
    for x in (7, 8):
        img.set(x, 0, rgba((40, 36, 44)))
    img.set(7, 1, rgba((60, 54, 66)))
    for x in range(5, 11):
        img.set(x, 2, rgba((34, 30, 40)))
        img.set(x, 13, rgba((34, 30, 40)))
    for y in range(3, 13):
        half = 4 if 4 <= y <= 11 else 3
        for x in range(8 - half, 8 + half):
            d = math.hypot(x + 0.5 - 8, (y + 0.5 - 8) * 0.8)
            c = mix((150, 160, 220), (236, 240, 255), clamp(1.0 - d / 5.0))
            if y in (5, 10):
                c = shade(c, 0.86)
            img.set(x, y, rgba(c))
    for (x, y, c) in ((7, 7, (140, 220, 255)), (8, 7, (140, 220, 255)), (7, 8, (220, 250, 255)), (8, 8, (255, 255, 255)),
                      (8, 6, (100, 170, 240))):
        img.set(x, y, rgba(c))
    img.set(7, 14, rgba((180, 30, 30)))
    img.set(8, 14, rgba((180, 30, 30)))
    img.set(8, 15, rgba((140, 20, 20)))
    outline(img)
    return save(img, ITEM, "lentera_arwah.png")


def item_giok():
    img = icon()
    # a bi disc of pale soul jade with a wisp in its hole
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 2.4 < d <= 6.6:
                n = NZ[5].fbm(x / 3.0, y / 3.0)
                c = mix((112, 176, 170), (214, 244, 236), 0.35 + 0.65 * n)
                if d > 5.8:
                    c = shade(c, 0.82)
                if (x + y) % 5 == 0 and 3.2 < d < 5.6:
                    c = mix(c, (255, 255, 255), 0.35)
                img.set(x, y, rgba(c))
    img.set(7, 7, (200, 230, 255, 200))
    img.set(8, 8, (160, 210, 255, 160))
    img.set(5, 4, rgba((255, 255, 255)))
    outline(img, (30, 60, 60, 255))
    return save(img, ITEM, "giok_arwah.png")


def item_surat():
    img = icon()
    # a folded letter of yellowed paper with ink lines and a cinnabar seal, its edges fading like smoke
    for y in range(3, 13):
        for x in range(2, 14):
            c = mix((206, 190, 150), (236, 224, 190), NZ[3].at(x / 3.0, y / 3.0))
            if x == 2 or x == 13 or y == 12:
                c = shade(c, 0.84)
            img.set(x, y, rgba(c))
    for y in range(3, 8):
        for x in range(2 + (y - 3), 14 - (y - 3)):
            img.set(x, y, rgba(shade(img.get(x, y), 0.9)))
    for y in (8, 10):
        for x in range(4, 11):
            if hash01(x, y, 5) > 0.25:
                img.set(x, y, rgba((60, 50, 60)))
    for (x, y) in ((10, 9), (11, 9), (10, 10), (11, 10)):
        img.set(x, y, rgba((190, 40, 30)))
    for (x, y, a) in ((1, 4, 110), (14, 6, 90), (3, 13, 100), (12, 2, 80)):
        img.set(x, y, (200, 210, 255, a))
    outline(img, (60, 50, 40, 255))
    return save(img, ITEM, "surat_arwah.png")


def item_penunjuk():
    img = icon()
    orb(img, 8, 8, 5.6, (14, 60, 90), (40, 150, 190), (190, 245, 250))
    # the compass needle of light across the pearl
    for i in range(-3, 4):
        x, y = 8 + i, 8 - i
        if 0 <= x < 16 and 0 <= y < 16:
            img.set(x, y, rgba((255, 236, 150) if i > 0 else (120, 210, 230)))
    img.set(11, 5, rgba((255, 255, 220)))
    outline(img, (10, 30, 50, 255))
    return save(img, ITEM, "mutiara_penunjuk_laut.png")


def item_penghindar():
    img = icon()
    # a white pearl inside a ring of bubbles (the water-parting pearl)
    for k in range(10):
        a = math.tau * k / 10
        x, y = int(round(8 + 6.4 * math.cos(a) - 0.5)), int(round(8 + 6.4 * math.sin(a) - 0.5))
        img.set(x, y, (170, 230, 255, 200))
        if k % 2 == 0:
            img.set(x, y - 1 if y > 0 else y, (230, 250, 255, 160))
    orb(img, 8, 8, 4.2, (120, 130, 160), (214, 220, 236), (255, 255, 255))
    outline(img, (30, 40, 70, 255))
    return save(img, ITEM, "mutiara_penghindar_air.png")


def item_naga():
    img = icon()
    # a golden dragon pearl wreathed in flame tongues
    for (x, y) in ((8, 1), (7, 2), (12, 3), (13, 4), (3, 4), (2, 5), (13, 10), (3, 11), (12, 12), (4, 13)):
        img.set(x, y, rgba((255, 120, 40)))
    for (x, y) in ((8, 2), (12, 4), (3, 5), (13, 11), (4, 12)):
        img.set(x, y, rgba((255, 200, 80)))
    orb(img, 8, 8, 5.0, (170, 90, 20), (236, 182, 70), (255, 246, 200))
    img.set(9, 9, rgba((255, 220, 140)))
    outline(img, (70, 30, 10, 255))
    return save(img, ITEM, "mutiara_naga.png")


def item_penglai():
    img = icon()
    # a white-jade gourd of Penglai spring water with a peach blossom on its cord
    def blob(cx, cy, r):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    t = clamp(1.0 - math.hypot(x + 0.5 - (cx - r * 0.4), y + 0.5 - (cy - r * 0.4)) / (r * 1.5))
                    img.set(x, y, rgba(mix((150, 170, 168), (250, 252, 244), 0.2 + 0.8 * t)))
    blob(8, 11, 4.2)
    blob(8, 5.6, 2.6)
    for x in (7, 8):
        img.set(x, 2, rgba((120, 80, 40)))
        img.set(x, 1, rgba((150, 100, 50)))
    img.set(8, 3, rgba((200, 40, 40)))
    for (x, y, c) in ((11, 3, (255, 170, 200)), (12, 3, (255, 200, 220)), (11, 4, (255, 200, 220)), (12, 4, (255, 140, 180)),
                      (10, 3, (200, 40, 40))):
        img.set(x, y, rgba(c))
    for (x, y) in ((6, 10), (9, 12), (7, 13)):
        img.set(x, y, rgba((150, 230, 255)))
    outline(img, (40, 60, 60, 255))
    return save(img, ITEM, "air_mata_air_penglai.png")


# ================================================================================================== main
def preview(path, img, out_dir, scale=8):
    big = Image(img.width * scale, img.height * scale, (40, 40, 48, 255))
    for y in range(img.height):
        for x in range(img.width):
            c = img.get(x, y)
            if c[3] == 0:
                continue
            for yy in range(scale):
                for xx in range(scale):
                    big.blend(x * scale + xx, y * scale + yy, c)
    os.makedirs(out_dir, exist_ok=True)
    big.save(os.path.join(out_dir, os.path.basename(path)))


def main():
    headwear()
    crab()
    turtle()
    serpent()
    guard_skin("kepala_lembu.png", (124, 64, 36), (64, 30, 16), (178, 112, 66), (64, 64, 72), (30, 28, 34),
               (196, 150, 70), (156, 30, 28), (255, 80, 40), 401)
    guard_skin("muka_kuda.png", (56, 50, 54), (22, 20, 24), (112, 100, 98), (44, 52, 72), (20, 22, 32),
               (196, 200, 210), (30, 96, 96), (120, 220, 255), 402)
    ghost_skin()
    king_skin()
    yaksha_skin()
    item_dupa()
    item_lentera()
    item_giok()
    item_surat()
    item_penunjuk()
    item_penghindar()
    item_naga()
    item_penglai()
    out = sys.argv[1] if len(sys.argv) > 1 else None
    for path, img in WRITTEN:
        if out:
            preview(path, img, out, 8 if img.width <= 64 and img.height <= 64 else 2)
    print("gen_dunia_dimensi_textures: %d texture(s) written" % len(WRITTEN))


if __name__ == "__main__":
    main()
