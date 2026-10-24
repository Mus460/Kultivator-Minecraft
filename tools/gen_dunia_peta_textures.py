"""Textures of the world map (DESIGN 34.5.16, module T), procedural and seeded (the same bytes every run).

  textures/dunia/peta/parchment.png  256 x 256  tileable aged paper: fibres, foxing, faint water stains
  textures/dunia/peta/fog.png        64 x 64    tileable mist wisps (white, alpha) painted over unexplored land
  textures/dunia/peta/brushes.png    128 x 64   ink stamps the map painter presses onto the paper (alpha = ink):
                                                row 0 mountains (3), snow peaks (2), hills (2), a volcano;
                                                row 1 broadleaf trees (2), pines (2), a jungle palm, a cherry, a dead
                                                tree, a mushroom; row 2 waves (2), dunes (2), reeds (2), snow ticks,
                                                rocks; row 3 cloud scrolls (2 of 32 x 16), a seal frame, a wave crest
  textures/dunia/peta/markers.png    128 x 64   the marker atlas of DESIGN 34.4.8 (16 x 16 cells, 8 per row, the cell
                                                index of DuniaMarkers), plus T's own: 23 waypoint, 25 return point,
                                                26 soul's body, 27 domain seal, 28 broken seal, 29 sky event,
                                                30 destination flag, 31 unknown
  textures/dunia/peta/compass.png    64 x 64    the compass rose (ink and seal red)
  textures/dunia/peta/markers_tempat.png 128 x 64 the second marker atlas (DESIGN 38.D.12 + 38.T, module 38W): icon index
                                                = 32 + cell (markers.png cells 23-31 already belong to module T), so
                                                DuniaMarkers icons 32-60: capital, villages, clan, sect compound, caves,
                                                natural sites, road posts, caravan, event, the five themed cities, desert
                                                ruins, hunters' post, merchant ship, road shrine, ferry

Run: python tools/gen_dunia_peta_textures.py [preview_dir]   (preview: enlarged copies)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator",
                    "textures", "dunia", "peta")

INK = (30, 30, 27)


def save(img, name, preview):
    os.makedirs(ROOT, exist_ok=True)
    img.save(os.path.join(ROOT, name))
    if preview:
        os.makedirs(preview, exist_ok=True)
        scale = 8 if img.width <= 64 else 4 if img.width <= 128 else 2
        big = Image(img.width * scale, img.height * scale, (200, 190, 160, 255))
        for y in range(img.height):
            for x in range(img.width):
                c = img.get(x, y)
                for yy in range(scale):
                    for xx in range(scale):
                        big.blend(x * scale + xx, y * scale + yy, c)
        big.save(os.path.join(preview, "peta_" + name))


def lerp(a, b, t):
    return a + (b - a) * t


def smooth(t):
    return t * t * (3 - 2 * t)


class Noise:
    """Tileable value noise of a given period (cells)."""

    def __init__(self, seed, cells):
        r = random.Random(seed)
        self.cells = cells
        self.grid = [[r.random() for _ in range(cells)] for _ in range(cells)]

    def at(self, u, v):
        c = self.cells
        gx, gy = u * c, v * c
        x0, y0 = int(math.floor(gx)) % c, int(math.floor(gy)) % c
        x1, y1 = (x0 + 1) % c, (y0 + 1) % c
        fx, fy = smooth(gx - math.floor(gx)), smooth(gy - math.floor(gy))
        a = lerp(self.grid[y0][x0], self.grid[y0][x1], fx)
        b = lerp(self.grid[y1][x0], self.grid[y1][x1], fx)
        return lerp(a, b, fy)


def fbm(noises, u, v):
    total, amp, norm = 0.0, 1.0, 0.0
    for n in noises:
        total += n.at(u, v) * amp
        norm += amp
        amp *= 0.5
    return total / norm


# ----------------------------------------------------------------------------------------------------------- paper
def parchment():
    s = 256
    img = Image(s, s)
    octaves = [Noise(67001 + i, 4 * (2 ** i)) for i in range(5)]
    stains = Noise(67011, 3)
    fibres = Noise(67021, 64)
    base = (230, 218, 182)
    dark = (198, 178, 136)
    for y in range(s):
        for x in range(s):
            u, v = x / s, y / s
            n = fbm(octaves, u, v)
            st = stains.at(u, v)
            f = fibres.at(u * 1.0, v * 0.25)
            t = 0.30 + 0.45 * (n - 0.5) + 0.25 * max(0.0, st - 0.62) * 3.0
            t = max(0.0, min(1.0, t))
            c = [lerp(base[i], dark[i], t) for i in range(3)]
            # fibres: long faint streaks
            c = [ci - 6.0 * max(0.0, f - 0.7) * 3.0 for ci in c]
            img.set(x, y, (int(c[0]), int(c[1]), int(c[2]), 255))
    # foxing: small brown spots (tile-safe, wrapped)
    r = random.Random(67031)
    for _ in range(70):
        cx, cy, rad = r.randrange(s), r.randrange(s), r.uniform(0.6, 2.2)
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d = math.hypot(dx, dy)
                if d <= rad:
                    a = int(60 * (1 - d / (rad + 0.01)))
                    img.blend((cx + dx) % s, (cy + dy) % s, (150, 110, 60, a))
    return img


def fog():
    s = 64
    img = Image(s, s)
    n1 = Noise(67041, 4)
    n2 = Noise(67042, 8)
    n3 = Noise(67043, 16)
    for y in range(s):
        for x in range(s):
            u, v = x / s, y / s
            n = 0.55 * n1.at(u, v) + 0.3 * n2.at(u, v) + 0.15 * n3.at(u, v)
            # wisps: bands of high value
            w = max(0.0, 1.0 - abs(n - 0.55) * 6.0)
            a = int(255 * min(1.0, 0.25 + 0.75 * w))
            img.set(x, y, (246, 244, 236, a))
    return img


# ----------------------------------------------------------------------------------------------------------- brushes
def ink(img, x, y, a):
    if a > 0:
        img.blend(x, y, INK + (int(max(0, min(255, a))),))


def stroke(img, ox, oy, pts, a=255, width=1):
    """A brush line through local points."""
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
        for i in range(steps + 1):
            t = i / steps
            x, y = lerp(x0, x1, t), lerp(y0, y1, t)
            for w in range(width):
                ink(img, ox + int(round(x)) + (w if abs(y1 - y0) > abs(x1 - x0) else 0),
                    oy + int(round(y)) + (0 if abs(y1 - y0) > abs(x1 - x0) else w), a)


def mountain(img, ox, oy, peak, left, right, base, snow=False, hatch=True):
    px, py = peak
    stroke(img, ox, oy, [(left, base), (px, py), (right, base)], 255)
    # shaded right flank: hatching
    if hatch:
        for k in range(1, 6):
            t = k / 6.0
            x0 = lerp(px, right, t)
            y0 = lerp(py, base, t)
            stroke(img, ox, oy, [(x0, y0), (x0 - 1.2, min(base, y0 + 3.0))], 120)
    # a faint wash inside
    for y in range(int(py) + 1, int(base) + 1):
        t = (y - py) / max(1.0, base - py)
        xl = lerp(px, left, t)
        xr = lerp(px, right, t)
        for x in range(int(math.ceil(xl)), int(xr) + 1):
            ink(img, ox + x, oy + y, 28 + int(30 * (x - xl) / max(1.0, xr - xl)))
    if snow:
        for x in range(int(px) - 2, int(px) + 3):
            img.blend(ox + x, oy + int(py) + 1 + abs(x - int(px)) // 2, (255, 255, 255, 230))


def tree(img, ox, oy, kind):
    if kind == "broad":
        cx, cy = 8, 7
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - cx) / 4.2, (y - cy) / 3.6)
                if d <= 1.0:
                    ink(img, ox + x, oy + y, 70 + int(110 * (1 - d)) if d > 0.75 else 60)
        stroke(img, ox, oy, [(8, 10), (8, 14)], 230)
    elif kind == "broad2":
        for (cx, cy, r) in ((6, 8, 3.0), (10, 7, 3.3), (8, 5, 2.6)):
            for y in range(16):
                for x in range(16):
                    d = math.hypot(x - cx, y - cy) / r
                    if d <= 1.0:
                        ink(img, ox + x, oy + y, 55 if d < 0.8 else 170)
        stroke(img, ox, oy, [(8, 10), (8, 14)], 230)
    elif kind == "pine":
        for i, (y, hw) in enumerate(((3, 1), (5, 2), (7, 3), (9, 4), (11, 5))):
            stroke(img, ox, oy, [(8 - hw, y + 1), (8, y - 1), (8 + hw, y + 1)], 230)
        stroke(img, ox, oy, [(8, 11), (8, 14)], 230)
    elif kind == "pine2":
        for y in range(2, 13):
            hw = (y - 2) * 0.45
            stroke(img, ox, oy, [(8 - hw, y), (8 + hw, y)], 60 if y % 3 else 200)
        stroke(img, ox, oy, [(8, 12), (8, 14)], 230)
    elif kind == "palm":
        stroke(img, ox, oy, [(7, 14), (8, 9), (8.5, 5)], 230)
        for dx, dy in ((-5, 1), (-3, -1), (4, 0), (5, 2), (1, -3)):
            stroke(img, ox, oy, [(8.5, 5), (8.5 + dx * 0.6, 5 + dy * 0.6 - 1), (8.5 + dx, 5 + dy + 1)], 200)
    elif kind == "cherry":
        for (cx, cy, r) in ((7, 7, 3.2), (10, 6, 2.8)):
            for y in range(16):
                for x in range(16):
                    d = math.hypot(x - cx, y - cy) / r
                    if d <= 1.0 and (x * 7 + y * 3) % 3:
                        img.blend(ox + x, oy + y, (186, 64, 90, 150 if d < 0.8 else 220))
        stroke(img, ox, oy, [(8, 9), (8, 14)], 230)
    elif kind == "dead":
        stroke(img, ox, oy, [(8, 14), (8, 6), (5, 3)], 230)
        stroke(img, ox, oy, [(8, 8), (11, 4)], 200)
        stroke(img, ox, oy, [(8, 10), (5, 8)], 170)
    elif kind == "mushroom":
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - 8) / 5.0, (y - 7) / 3.0)
                if d <= 1.0 and y <= 8:
                    img.blend(ox + x, oy + y, (150, 50, 56, 210 if d > 0.75 else 160))
        stroke(img, ox, oy, [(8, 8), (8, 13)], 230)


def waves(img, ox, oy, variant):
    for row in range(2):
        y = 6 + row * 5 + variant
        pts = [(1 + i, y + 1.6 * math.sin((i + variant * 2) * 0.9)) for i in range(0, 14)]
        stroke(img, ox, oy, pts, 200 - row * 60)


def dunes(img, ox, oy, variant):
    for row in range(3):
        y = 5 + row * 4
        pts = [(1 + i, y + 1.2 * math.sin(i * 0.45 + row + variant)) for i in range(0, 14)]
        stroke(img, ox, oy, pts, 170 - row * 30)


def reeds(img, ox, oy, variant):
    for k in range(5):
        x = 3 + k * 2.4 + variant * 0.5
        h = 5 + (k * 3 + variant) % 4
        stroke(img, ox, oy, [(x, 14), (x + 0.6 * (k % 2 * 2 - 1), 14 - h)], 210)
    stroke(img, ox, oy, [(1, 14), (15, 14)], 90)


def snow_ticks(img, ox, oy):
    r = random.Random(67051)
    for _ in range(7):
        x, y = r.randrange(2, 14), r.randrange(3, 14)
        stroke(img, ox, oy, [(x - 1, y), (x + 1, y)], 160)
        stroke(img, ox, oy, [(x, y - 1), (x, y + 1)], 160)


def rocks(img, ox, oy):
    for (cx, cy, r) in ((5, 11, 2.6), (10, 10, 3.2), (12, 13, 1.8)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, (y - cy) * 1.3) / r
                if d <= 1.0:
                    ink(img, ox + x, oy + y, 190 if d > 0.7 else 60 + int(60 * ((x - cx + r) / (2 * r))))


def cloud_scroll(img, ox, oy, variant):
    """A ruyi cloud scroll, 32 x 16."""
    for (cx, cy, r) in ((9, 9, 5.0), (17, 7, 6.0), (25, 9, 4.6)):
        for t in range(0, 300):
            a = t / 300.0 * math.pi * 1.6 + (0.4 if variant else 0.0)
            rr = r * (1.0 - t / 300.0 * 0.55)
            x = cx + math.cos(a) * rr
            y = cy - math.sin(a) * rr * 0.8
            ink(img, ox + int(round(x)), oy + int(round(y)), 150)
    stroke(img, ox, oy, [(3, 13), (29, 13)], 110)


def seal_frame(img, ox, oy):
    for i in range(16):
        for t in (0, 15):
            img.blend(ox + i, oy + t, (179, 40, 29, 230))
            img.blend(ox + t, oy + i, (179, 40, 29, 230))
        for t in (2, 13):
            if 2 <= i <= 13:
                img.blend(ox + i, oy + t, (179, 40, 29, 120))
                img.blend(ox + t, oy + i, (179, 40, 29, 120))


def brushes():
    img = Image(128, 64)
    # row 0
    mountain(img, 0, 0, (8, 2), 1, 15, 14)
    mountain(img, 16, 0, (6, 3), 0, 13, 14)
    mountain(img, 16, 0, (11, 6), 7, 15, 14, hatch=False)
    mountain(img, 32, 0, (9, 4), 2, 15, 14)
    mountain(img, 32, 0, (4, 8), 0, 8, 14, hatch=False)
    mountain(img, 48, 0, (8, 1), 1, 15, 14, snow=True)
    mountain(img, 64, 0, (7, 3), 1, 14, 14, snow=True)
    mountain(img, 64, 0, (12, 6), 9, 16, 14, snow=True, hatch=False)
    for k, ox in enumerate((80, 96)):
        pts = [(1 + i, 13 - 4.0 * math.sin(i / 14.0 * math.pi) * (0.8 + 0.2 * k)) for i in range(0, 15)]
        stroke(img, ox, 0, pts, 220)
        stroke(img, ox, 0, [(5, 12), (6, 13)], 120)
    mountain(img, 112, 0, (8, 4), 1, 15, 14)
    for x in range(6, 11):
        img.blend(112 + x, 3, (190, 60, 30, 230))
    img.blend(120, 1, (190, 60, 30, 150))
    img.blend(119, 0, (120, 120, 120, 120))
    # row 1
    for i, kind in enumerate(("broad", "broad2", "pine", "pine2", "palm", "cherry", "dead", "mushroom")):
        tree(img, i * 16, 16, kind)
    # row 2
    waves(img, 0, 32, 0)
    waves(img, 16, 32, 1)
    dunes(img, 32, 32, 0)
    dunes(img, 48, 32, 1.5)
    reeds(img, 64, 32, 0)
    reeds(img, 80, 32, 1)
    snow_ticks(img, 96, 32)
    rocks(img, 112, 32)
    # row 3
    cloud_scroll(img, 0, 48, 0)
    cloud_scroll(img, 32, 48, 1)
    seal_frame(img, 64, 48)
    stroke(img, 80, 48, [(1, 10), (4, 6), (8, 5), (11, 7), (12, 10), (10, 11), (9, 9)], 220)
    return img


# ----------------------------------------------------------------------------------------------------------- markers
OUT = (24, 20, 16, 255)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


class Cell:
    """Drawing into one 16 x 16 marker cell."""

    def __init__(self, img, index):
        self.img = img
        self.ox = (index % 8) * 16
        self.oy = (index // 8) * 16

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.set(self.ox + x, self.oy + y, c)

    def blend(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.blend(self.ox + x, self.oy + y, c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def disc(self, cx, cy, r, c, ring=None):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    self.px(x, y, c)
                elif ring and d <= r + 1.0:
                    self.px(x, y, ring)

    def line(self, pts, c):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
            for i in range(n + 1):
                t = i / n
                self.px(int(round(lerp(x0, x1, t))), int(round(lerp(y0, y1, t))), c)

    def poly(self, pts, c):
        ys = [p[1] for p in pts]
        for y in range(int(min(ys)), int(max(ys)) + 1):
            xs = []
            for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]):
                if (y0 <= y + 0.5 < y1) or (y1 <= y + 0.5 < y0):
                    xs.append(x0 + (y + 0.5 - y0) * (x1 - x0) / (y1 - y0))
            xs.sort()
            for a, b in zip(xs[::2], xs[1::2]):
                for x in range(int(math.ceil(a - 0.5)), int(math.floor(b - 0.5)) + 1):
                    self.px(x, y, c)

    def outline(self):
        """A dark 1-px outline around every opaque pixel (marker legibility on any paper)."""
        solid = set()
        for y in range(16):
            for x in range(16):
                if self.img.get(self.ox + x, self.oy + y)[3] > 120:
                    solid.add((x, y))
        for (x, y) in list(solid):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in solid and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    self.px(q[0], q[1], OUT)


def roof(c, x0, x1, y, color, dark):
    c.line([(x0 - 1, y + 1), (x0, y), (x1, y), (x1 + 1, y - 1)], dark)
    c.rect(x0, y + 1, x1, y + 1, color)


def markers():
    img = Image(128, 64)
    gold, gold_d = rgb(0xE0B050), rgb(0x8A671B)
    red, red_d = rgb(0xB3281D), rgb(0x6E160F)
    jade, jade_d = rgb(0x3FAE84), rgb(0x184B36)
    stone, stone_d = rgb(0x9AA890), rgb(0x55604C)
    blue, blue_d = rgb(0x40A8E0), rgb(0x1E5A80)
    white, cream = rgb(0xF4F0E0), rgb(0xE8DCC0)
    violet = rgb(0xC890FF)
    teal = rgb(0x6FA0A0)
    # 0 kota: a paifang gate
    c = Cell(img, 0)
    c.rect(3, 6, 4, 13, red)
    c.rect(11, 6, 12, 13, red)
    roof(c, 2, 13, 4, gold, gold_d)
    c.rect(4, 7, 11, 7, red_d)
    c.rect(7, 2, 8, 3, gold)
    c.outline()
    # 1 kota abadi: a floating hall on a cloud
    c = Cell(img, 1)
    c.disc(5, 12, 3, white)
    c.disc(9, 11.5, 3.5, white)
    c.disc(12, 12.5, 2.5, white)
    c.rect(5, 6, 10, 9, cream)
    roof(c, 4, 11, 4, gold, gold_d)
    c.px(7, 8, gold_d)
    c.px(8, 8, gold_d)
    c.outline()
    # 2 gua kecil: a cave mouth
    c = Cell(img, 2)
    c.poly([(1, 14), (5, 5), (9, 3), (14, 8), (15, 14)], stone)
    c.poly([(6, 14), (7, 10), (9, 9), (11, 11), (11, 14)], rgb(0x1E1A16))
    c.outline()
    # 3 gua agung: a great cave with pillars
    c = Cell(img, 3)
    c.poly([(0, 14), (3, 4), (8, 1), (13, 4), (15, 14)], rgb(0x7FA0B8))
    c.poly([(4, 14), (5, 8), (8, 6), (11, 8), (12, 14)], rgb(0x14181E))
    c.rect(6, 9, 6, 14, stone)
    c.rect(10, 9, 10, 14, stone)
    c.outline()
    # 4 gua kuno: an ancient cave, violet light
    c = Cell(img, 4)
    c.poly([(0, 14), (3, 5), (8, 2), (13, 5), (15, 14)], rgb(0x6A5A78))
    c.poly([(5, 14), (6, 9), (8, 8), (10, 9), (11, 14)], violet)
    c.px(8, 11, white)
    c.outline()
    # 5 penjara: a chained seal talisman
    c = Cell(img, 5)
    c.rect(5, 2, 10, 13, rgb(0xF1D57E))
    c.rect(7, 4, 8, 11, red)
    c.line([(0, 4), (15, 11)], rgb(0x707070))
    c.line([(0, 11), (15, 4)], rgb(0x707070))
    c.outline()
    # 6 menara: a tall pagoda
    c = Cell(img, 6)
    for i, y in enumerate((12, 9, 6, 3)):
        hw = 5 - i
        c.rect(8 - hw + 1, y, 7 + hw - 1, y + 2, white)
        roof(c, 8 - hw, 7 + hw, y - 1, rgb(0x2F6E6A), rgb(0x173836))
    c.px(7, 0, gold)
    c.px(8, 0, gold)
    c.outline()
    # 7 bawah/kuil: a ghost gate shrine
    c = Cell(img, 7)
    c.rect(3, 6, 4, 14, teal)
    c.rect(11, 6, 12, 14, teal)
    roof(c, 2, 13, 4, rgb(0x2A3A3A), rgb(0x101818))
    c.disc(8, 10, 1.8, rgb(0xB8FFF0))
    c.outline()
    # 8 istana yama: a dark palace with red pillars
    c = Cell(img, 8)
    c.rect(3, 7, 12, 13, rgb(0x2A1414))
    for x in (4, 7, 10):
        c.rect(x, 8, x, 13, red)
    roof(c, 2, 13, 5, rgb(0x5A1E1E), rgb(0x2A0A0A))
    roof(c, 4, 11, 2, rgb(0x5A1E1E), rgb(0x2A0A0A))
    c.outline()
    # 9 desa arwah: ghost lanterns over huts
    c = Cell(img, 9)
    c.rect(2, 10, 6, 14, rgb(0x7A8C9A))
    c.rect(9, 9, 14, 14, rgb(0x7A8C9A))
    roof(c, 2, 6, 9, rgb(0x404850), rgb(0x20242A))
    roof(c, 9, 14, 8, rgb(0x404850), rgb(0x20242A))
    c.disc(5, 4, 1.6, rgb(0xB8FFF0))
    c.disc(11, 3, 1.6, rgb(0xB8FFF0))
    c.outline()
    # 10 pusaran: a whirlpool
    c = Cell(img, 10)
    for t in range(240):
        a = t / 240.0 * math.pi * 5.0
        r = 0.6 + t / 240.0 * 6.5
        c.px(int(round(8 + math.cos(a) * r)), int(round(8 + math.sin(a) * r * 0.8)), blue if t % 30 > 6 else white)
    c.outline()
    # 11 pulau: an island with a tree
    c = Cell(img, 11)
    c.poly([(1, 12), (4, 9), (11, 9), (15, 12)], rgb(0xD8C890))
    c.rect(0, 13, 15, 14, blue)
    c.rect(7, 4, 7, 9, rgb(0x6B4A2A))
    c.disc(7, 4, 2.6, rgb(0x60C080))
    c.outline()
    # 12 istana naga: a coral palace under waves
    c = Cell(img, 12)
    c.rect(0, 2, 15, 3, blue)
    c.rect(3, 8, 12, 14, rgb(0x30D0C0))
    roof(c, 2, 13, 6, gold, gold_d)
    c.rect(7, 10, 8, 14, rgb(0x0E4A46))
    c.outline()
    # 13 penglai: peaks over clouds, gold
    c = Cell(img, 13)
    c.poly([(2, 12), (6, 3), (9, 8), (11, 5), (15, 12)], rgb(0x7FB090))
    c.disc(4, 12, 2.5, white)
    c.disc(9, 12.5, 3, white)
    c.disc(13, 12, 2.3, white)
    c.px(6, 2, gold)
    c.outline()
    # 14 langit/harta: a rainbow treasure pillar
    c = Cell(img, 14)
    colors = [0xFF5A5A, 0xFFB050, 0xFFF070, 0x70E090, 0x60B0FF, 0xB080FF]
    for i, col in enumerate(colors):
        c.rect(5 + i, 1, 5 + i, 12, rgb(col))
    c.disc(8, 13, 2.2, rgb(0xFFF4C0))
    c.outline()
    # 15 meteor: a streak
    c = Cell(img, 15)
    c.line([(1, 1), (10, 10)], rgb(0x90C0FF))
    c.line([(2, 1), (11, 10)], rgb(0xC8E0FF))
    c.disc(11.5, 11.5, 2.6, rgb(0xFFE8B0))
    c.outline()
    # 16 naga: a dragon head
    c = Cell(img, 16)
    c.poly([(2, 9), (7, 4), (13, 5), (15, 8), (12, 11), (6, 12)], rgb(0x60E0A0))
    c.line([(7, 4), (5, 1)], gold)
    c.line([(10, 4), (10, 1)], gold)
    c.px(11, 7, rgb(0xFF4030))
    c.line([(15, 9), (13, 13)], white)
    c.outline()
    # 17 kapal: a junk
    c = Cell(img, 17)
    c.poly([(1, 10), (15, 10), (12, 13), (4, 13)], rgb(0x6A4428))
    c.rect(7, 2, 7, 10, rgb(0x3A2614))
    c.rect(8, 3, 12, 8, cream)
    c.rect(3, 5, 6, 9, cream)
    c.px(10, 5, red)
    c.outline()
    # 18 dermaga: a pier
    c = Cell(img, 18)
    c.rect(1, 8, 14, 9, rgb(0xA08060))
    for x in (2, 6, 10, 13):
        c.rect(x, 10, x, 13, rgb(0x6A5038))
    c.rect(0, 12, 15, 14, blue)
    c.outline()
    # 19 sekte/aula: a hall
    c = Cell(img, 19)
    c.rect(3, 8, 12, 13, rgb(0xFFF0D0))
    c.rect(7, 10, 8, 13, red)
    roof(c, 2, 13, 6, rgb(0xFFD27A), gold_d)
    roof(c, 4, 11, 3, rgb(0xFFD27A), gold_d)
    c.outline()
    # 20 sekte/wilayah: a banner
    c = Cell(img, 20)
    c.rect(3, 1, 3, 14, rgb(0x6A4428))
    c.poly([(4, 2), (13, 2), (11, 5), (13, 8), (4, 8)], rgb(0xFFD27A))
    c.outline()
    # 21 desa: village houses
    c = Cell(img, 21)
    c.rect(2, 9, 7, 14, rgb(0xE0D0A0))
    c.rect(9, 8, 14, 14, rgb(0xE0D0A0))
    roof(c, 2, 7, 8, rgb(0x8A5A3A), rgb(0x4A2A1A))
    roof(c, 9, 14, 7, rgb(0x8A5A3A), rgb(0x4A2A1A))
    c.outline()
    # 22 kota/wilayah: a town crest
    c = Cell(img, 22)
    c.poly([(3, 2), (12, 2), (12, 8), (8, 13), (3, 8)], rgb(0xC0A070))
    c.rect(7, 4, 8, 9, red)
    c.outline()
    # 23 waypoint: a pin
    c = Cell(img, 23)
    c.disc(8, 6, 4, white)
    c.poly([(5, 8), (11, 8), (8, 14)], white)
    c.disc(8, 6, 1.6, rgb(0x404040))
    c.outline()
    # 24 player (drawn as a vector arrow; a fallback dot here)
    c = Cell(img, 24)
    c.poly([(8, 1), (13, 13), (8, 10), (3, 13)], white)
    c.outline()
    # 25 return point: a portal ring
    c = Cell(img, 25)
    c.disc(8, 8, 6, rgb(0x9A70E0))
    c.disc(8, 8, 3.5, rgb(0x2A1840))
    c.outline()
    # 26 soul's body: a lying figure with a spirit wisp
    c = Cell(img, 26)
    c.rect(2, 11, 12, 13, rgb(0xC8C8D8))
    c.disc(13, 12, 1.8, rgb(0xE8D8C0))
    c.disc(8, 5, 2.4, rgb(0xB8FFF0))
    c.line([(8, 7), (7, 10)], rgb(0x90E0D0))
    c.outline()
    # 27 domain seal: a square seal
    c = Cell(img, 27)
    c.rect(2, 2, 13, 13, red)
    c.rect(4, 4, 11, 11, rgb(0xE84A3A))
    c.rect(7, 5, 8, 10, rgb(0xF4E0D0))
    c.rect(5, 7, 10, 8, rgb(0xF4E0D0))
    c.outline()
    # 28 broken seal: a cracked talisman
    c = Cell(img, 28)
    c.rect(5, 2, 10, 13, rgb(0xF1D57E))
    c.line([(5, 6), (8, 8), (6, 10), (10, 12)], rgb(0x1A0A08))
    c.rect(7, 3, 8, 5, red)
    c.outline()
    # 29 sky event: a star burst
    c = Cell(img, 29)
    for a in range(8):
        ang = a * math.pi / 4
        r = 7 if a % 2 == 0 else 4
        c.line([(8, 8), (8 + math.cos(ang) * r, 8 + math.sin(ang) * r)], rgb(0xFFE070))
    c.disc(8, 8, 2, white)
    c.outline()
    # 30 destination flag
    c = Cell(img, 30)
    c.rect(4, 1, 4, 14, rgb(0x3A2614))
    c.poly([(5, 2), (14, 4), (5, 7)], red)
    c.outline()
    # 31 unknown: a question seal
    c = Cell(img, 31)
    c.disc(8, 8, 6, rgb(0xC8C8C8))
    c.line([(6, 5), (7, 4), (9, 4), (10, 5), (10, 6), (8, 8), (8, 9)], rgb(0x303030))
    c.px(8, 11, rgb(0x303030))
    c.outline()
    return img


def markers_tempat():
    """DESIGN 38 markers (icon = 32 + cell). Every cell is outlined like the first atlas."""
    img = Image(128, 64)
    gold, gold_d = rgb(0xFFD27A), rgb(0x8A671B)
    red, red_d = rgb(0xB3281D), rgb(0x6E160F)
    white, cream = rgb(0xF4F0E0), rgb(0xE8DCC0)
    wood, wood_d = rgb(0x8A5A3A), rgb(0x4A2A1A)
    stone, stone_d = rgb(0x9AA890), rgb(0x55604C)
    dark = rgb(0x1E1A16)
    blue = rgb(0x40A8E0)
    # 0 (32) kota/kota_besar: a walled capital, gate tower and a tall palace roof
    c = Cell(img, 0)
    c.rect(1, 9, 14, 14, rgb(0xC8B890))
    for x in (1, 4, 7, 10, 13):
        c.px(x, 8, rgb(0xC8B890))
    c.rect(6, 11, 9, 14, dark)
    c.rect(4, 4, 11, 8, rgb(0xFFF0D0))
    roof(c, 3, 12, 3, gold, gold_d)
    roof(c, 5, 10, 1, gold, gold_d)
    c.px(7, 6, red)
    c.px(8, 6, red)
    c.outline()
    # 1 (33) kampung: three thatched huts around a well
    c = Cell(img, 1)
    hut = rgb(0xE0D0A0)
    thatch, thatch_d = rgb(0xC8A860), rgb(0x6E5A2A)
    c.rect(1, 10, 5, 14, hut)
    roof(c, 1, 5, 9, thatch, thatch_d)
    c.rect(10, 10, 14, 14, hut)
    roof(c, 10, 14, 9, thatch, thatch_d)
    c.rect(5, 4, 10, 8, hut)
    roof(c, 5, 10, 3, thatch, thatch_d)
    c.disc(8, 12.5, 1.6, rgb(0x3A6A90), ring=stone)
    c.outline()
    # 2 (34) kampung/klan: an ancestral hall with a clan banner
    c = Cell(img, 2)
    c.rect(3, 8, 12, 14, rgb(0xE8D8B8))
    c.rect(6, 10, 9, 14, wood_d)
    roof(c, 2, 13, 6, rgb(0x6A3A2A), rgb(0x3A1A10))
    roof(c, 4, 11, 4, rgb(0x6A3A2A), rgb(0x3A1A10))
    c.rect(14, 1, 14, 14, wood)
    c.poly([(9, 1), (14, 1), (14, 5), (9, 5)], rgb(0xC08060))
    c.px(11, 3, white)
    c.outline()
    # 3 (35) sekte/kompleks: three terraces climbing to a peak hall
    c = Cell(img, 3)
    c.rect(0, 13, 15, 14, stone)
    c.rect(2, 10, 13, 12, stone_d)
    c.rect(4, 7, 11, 9, stone)
    c.rect(5, 4, 10, 6, rgb(0xFFF0D0))
    roof(c, 4, 11, 3, gold, gold_d)
    c.rect(7, 10, 8, 14, rgb(0xD8C8A0))
    c.px(7, 5, red)
    c.px(8, 5, red)
    c.outline()
    # 4 (36) gua/pertapa: a cliff cave with a meditating hermit and a lamp
    c = Cell(img, 4)
    c.poly([(0, 15), (2, 4), (7, 1), (13, 3), (15, 15)], rgb(0x8A8A7A))
    c.poly([(4, 15), (5, 8), (8, 6), (11, 8), (12, 15)], dark)
    c.disc(8, 10, 1.3, rgb(0xE8D8C0))
    c.poly([(6, 14), (8, 11), (10, 14)], rgb(0x7A6A9A))
    c.px(11, 12, rgb(0xFFD070))
    c.outline()
    # 5 (37) gua/makam: an inheritance tomb door with a seal
    c = Cell(img, 5)
    c.poly([(1, 15), (2, 6), (8, 2), (14, 6), (15, 15)], rgb(0x6E6458))
    c.rect(5, 7, 10, 15, rgb(0x3A3028))
    c.line([(8, 7), (8, 15)], rgb(0x1A1410))
    c.rect(6, 9, 9, 11, rgb(0xF1D57E))
    c.px(7, 10, red)
    c.px(8, 10, red)
    c.outline()
    # 6 (38) gua/sarang_binatang: a den mouth with bones and two eyes
    c = Cell(img, 6)
    c.poly([(0, 15), (3, 6), (8, 3), (13, 6), (15, 15)], rgb(0x7A6A4A))
    c.poly([(4, 15), (5, 9), (8, 7), (11, 9), (12, 15)], dark)
    c.px(6, 11, rgb(0xFFC030))
    c.px(10, 11, rgb(0xFFC030))
    c.line([(1, 14), (4, 13)], white)
    c.line([(12, 14), (15, 13)], white)
    c.outline()
    # 7 (39) gua/sarang_iblis: a horned skull over a blood-red maw
    c = Cell(img, 7)
    c.poly([(0, 15), (3, 7), (8, 5), (13, 7), (15, 15)], rgb(0x4A1A20))
    c.poly([(5, 15), (6, 11), (8, 10), (10, 11), (11, 15)], rgb(0xC02030))
    c.disc(8, 6, 3, rgb(0xE8E0D0))
    c.px(7, 6, dark)
    c.px(9, 6, dark)
    c.line([(5, 4), (3, 1)], rgb(0x8A2030))
    c.line([(11, 4), (13, 1)], rgb(0x8A2030))
    c.outline()
    # 8 (40) gua/urat_roh: violet spirit crystals in rock
    c = Cell(img, 8)
    c.poly([(0, 15), (1, 9), (15, 9), (15, 15)], rgb(0x5A5060))
    lav, lav_d = rgb(0xB8A6F0), rgb(0x6A50B0)
    c.poly([(3, 12), (4, 4), (6, 12)], lav)
    c.poly([(7, 12), (9, 1), (11, 12)], lav)
    c.poly([(11, 12), (13, 6), (14, 12)], lav_d)
    c.px(9, 4, white)
    c.px(4, 7, white)
    c.outline()
    # 9 (41) gua/cermin_hati: a round mirror in a stone ring, a heart glint
    c = Cell(img, 9)
    c.disc(8, 8, 6, rgb(0xA8C8D8), ring=rgb(0x6A7078))
    c.disc(8, 8, 4, rgb(0xD8F0FF))
    c.disc(7, 7.5, 1.3, rgb(0xF07080))
    c.disc(9, 7.5, 1.3, rgb(0xF07080))
    c.poly([(5.8, 8), (10.2, 8), (8, 11)], rgb(0xF07080))
    c.outline()
    # 10 (42) situs/puncak_petir: a peak struck by a bolt
    c = Cell(img, 10)
    c.poly([(0, 15), (6, 6), (9, 9), (11, 7), (15, 15)], rgb(0x7A8090))
    c.poly([(5, 7), (6, 6), (7, 7)], white)
    c.line([(12, 0), (9, 4), (12, 4), (8, 8)], rgb(0xC8E8FF))
    c.line([(11, 0), (8, 4)], rgb(0x80B0FF))
    c.outline()
    # 11 (43) situs/panggung_bintang: a stone platform under stars
    c = Cell(img, 11)
    c.rect(2, 11, 13, 13, rgb(0xB0A890))
    c.rect(4, 9, 11, 10, rgb(0xC8C0A8))
    c.rect(3, 14, 3, 15, rgb(0x8A8270))
    c.rect(12, 14, 12, 15, rgb(0x8A8270))
    star = rgb(0xFFF0A0)
    for (x, y) in ((3, 3), (8, 1), (13, 4), (6, 6), (11, 7)):
        c.px(x, y, star)
        c.blend(x - 1, y, rgb(0xFFF0A0, 110))
        c.blend(x + 1, y, rgb(0xFFF0A0, 110))
    c.outline()
    # 12 (44) situs/mata_air_roh: a spring pool with rising qi
    c = Cell(img, 12)
    c.disc(8, 11.5, 3.5, rgb(0x60C8D8), ring=stone)
    c.disc(8, 11.5, 1.8, rgb(0xB0F0F0))
    c.line([(6, 8), (5, 5), (6, 2)], rgb(0xC0FFF0))
    c.line([(10, 8), (11, 5), (10, 2)], rgb(0xC0FFF0))
    c.outline()
    # 13 (45) pos/singgah: a roadside inn with a hanging lantern
    c = Cell(img, 13)
    c.rect(2, 8, 13, 14, rgb(0xE8D8B0))
    c.rect(6, 10, 9, 14, wood_d)
    roof(c, 1, 14, 6, rgb(0x5A6A5A), rgb(0x2A3A2A))
    c.rect(4, 3, 11, 5, rgb(0xE8D8B0))
    roof(c, 3, 12, 2, rgb(0x5A6A5A), rgb(0x2A3A2A))
    c.rect(13, 9, 13, 10, red)
    c.px(13, 11, rgb(0xFFD070))
    c.outline()
    # 14 (46) pos/dagang: a trading post, crates and a coin banner
    c = Cell(img, 14)
    c.rect(1, 9, 9, 14, rgb(0xE0C890))
    roof(c, 1, 9, 8, wood, wood_d)
    c.rect(11, 11, 14, 14, rgb(0xB08050))
    c.line([(11, 11), (14, 14)], wood_d)
    c.rect(12, 2, 12, 10, wood_d)
    c.disc(9.5, 4, 2.4, gold)
    c.rect(9, 3, 10, 4, dark)
    c.outline()
    # 15 (47) pos/tonggak: a carved milestone
    c = Cell(img, 15)
    c.rect(6, 3, 10, 13, rgb(0xB8B0A0))
    c.rect(5, 13, 11, 14, rgb(0x7A7468))
    c.poly([(6, 3), (8, 1), (10, 3)], rgb(0xB8B0A0))
    for y in (5, 7, 9, 11):
        c.rect(7, y, 9, y, rgb(0x4A4438))
    c.outline()
    # 16 (48) pos/kemah_begal: crossed blades over a tent
    c = Cell(img, 16)
    c.poly([(1, 14), (8, 5), (15, 14)], rgb(0x8A6A4A))
    c.poly([(6, 14), (8, 10), (10, 14)], dark)
    c.line([(2, 1), (11, 10)], rgb(0xD8D8E0))
    c.line([(13, 1), (4, 10)], rgb(0xD8D8E0))
    c.px(10, 9, red)
    c.px(5, 9, red)
    c.outline()
    # 17 (49) tempat/kafilah: an ox cart with cargo
    c = Cell(img, 17)
    c.rect(4, 6, 14, 10, rgb(0xC8A070))
    c.rect(5, 4, 13, 5, rgb(0xE8D8B0))
    c.disc(7, 12, 2.2, wood_d)
    c.disc(12, 12, 2.2, wood_d)
    c.px(7, 12, gold)
    c.px(12, 12, gold)
    c.poly([(0, 8), (3, 7), (4, 11), (1, 11)], rgb(0x6A5040))
    c.outline()
    # 18 (50) tempat/acara: a festival lantern string and a firework
    c = Cell(img, 18)
    c.line([(0, 6), (4, 8), (8, 9), (12, 8), (15, 6)], wood_d)
    for x, y in ((3, 9), (8, 10), (13, 9)):
        c.rect(x - 1, y, x + 1, y + 3, red)
        c.px(x, y + 1, rgb(0xFFD070))
    for a in range(8):
        ang = a * math.pi / 4
        c.px(int(round(8 + math.cos(ang) * 3)), int(round(3 + math.sin(ang) * 2.5)), rgb(0xFFE070))
    c.px(8, 3, white)
    c.outline()
    # 19 (51) kota_tema/pelabuhan: a lighthouse on a breakwater, a sail on the waves
    c = Cell(img, 19)
    c.rect(0, 13, 15, 15, rgb(0x5FA8C8))
    c.rect(1, 11, 9, 12, rgb(0xA08070))
    c.poly([(4, 11), (5, 3), (7, 3), (8, 11)], rgb(0xC85040))
    c.rect(5, 6, 7, 6, white)
    c.rect(4, 1, 8, 2, rgb(0xFFE070))
    c.poly([(10, 12), (12, 6), (14, 12)], cream)
    c.outline()
    # 20 (52) kota_tema/oasis: a palm over a crescent lake and a dome
    c = Cell(img, 20)
    c.rect(0, 12, 15, 15, rgb(0xE0B060))
    c.disc(6, 13, 3, rgb(0x40A0C0))
    c.disc(6, 11.5, 2.4, rgb(0xE0B060))
    c.line([(11, 13), (11, 5), (10, 3)], wood)
    c.poly([(6, 4), (10, 2), (14, 3), (11, 4)], rgb(0x4A9A50))
    c.poly([(8, 7), (11, 4), (15, 6)], rgb(0x3A7A40))
    c.disc(3, 9, 2.4, cream)
    c.outline()
    # 21 (53) kota_tema/air: a moon bridge over a canal
    c = Cell(img, 21)
    c.rect(0, 12, 15, 15, rgb(0x7FC8B0))
    for x in range(1, 15):
        y = 10 - int(round(4 * math.sin((x - 1) / 13.0 * math.pi)))
        c.rect(x, y, x, y + 1, white)
    c.disc(8, 12, 3, rgb(0x2A5A50))
    c.rect(0, 8, 2, 11, rgb(0x6A6A6A))
    c.rect(13, 8, 15, 11, rgb(0x6A6A6A))
    c.outline()
    # 22 (54) kota_tema/gunung: terraces, a stair and a golden hall on the peak
    c = Cell(img, 22)
    c.poly([(0, 15), (8, 2), (15, 15)], rgb(0xA0A8B8))
    c.rect(3, 12, 12, 12, rgb(0x6A7280))
    c.rect(5, 9, 10, 9, rgb(0x6A7280))
    c.line([(8, 14), (8, 6)], cream)
    c.rect(6, 3, 9, 5, gold)
    roof(c, 5, 10, 2, rgb(0xE0A030), gold_d)
    c.outline()
    # 23 (55) kota_tema/iblis: the skull gate with blood-lit eyes
    c = Cell(img, 23)
    c.disc(8, 7, 6, rgb(0xD8D0C0))
    c.disc(5.5, 6, 1.6, dark)
    c.disc(10.5, 6, 1.6, dark)
    c.px(5, 6, rgb(0xFF3030))
    c.px(10, 6, rgb(0xFF3030))
    c.rect(4, 10, 11, 14, rgb(0x8A1020))
    c.rect(6, 11, 9, 14, dark)
    for x in (5, 7, 9, 11):
        c.px(x - 1, 10, white)
    c.outline()
    # 24 (56) kota_tema/reruntuhan_gurun: broken columns in sand
    c = Cell(img, 24)
    c.rect(0, 12, 15, 15, rgb(0xE0B880))
    c.rect(2, 4, 4, 12, rgb(0xC09050))
    c.rect(1, 3, 5, 3, rgb(0xA07840))
    c.rect(7, 7, 9, 12, rgb(0xC09050))
    c.line([(7, 7), (9, 6)], rgb(0xA07840))
    c.rect(11, 10, 14, 12, rgb(0xA07840))
    c.outline()
    # 25 (57) kota_tema/pos_pemburu: a watchtower with a white banner
    c = Cell(img, 25)
    c.rect(4, 5, 4, 15, wood_d)
    c.rect(10, 5, 10, 15, wood_d)
    c.line([(4, 15), (10, 9)], wood)
    c.line([(10, 15), (4, 9)], wood)
    c.rect(3, 3, 11, 5, wood)
    roof(c, 3, 11, 2, rgb(0x5A6A7A), rgb(0x2A3A4A))
    c.rect(13, 0, 13, 9, wood_d)
    c.poly([(14, 0), (15, 1), (15, 4), (14, 5)], rgb(0xD8E4F0))
    c.outline()
    # 26 (58) tema/kapal_dagang: a three-masted merchant ship
    c = Cell(img, 26)
    c.poly([(0, 10), (15, 10), (13, 13), (2, 13)], rgb(0x7A4A28))
    c.rect(0, 14, 15, 15, blue)
    for x, h in ((4, 4), (8, 2), (12, 5)):
        c.rect(x, h, x, 10, wood_d)
        c.rect(x - 2, h + 1, x + 1, h + 4, rgb(0xE8C890))
    c.px(8, 1, red)
    c.outline()
    # 27 (59) pos/kuil_jalan: a small road shrine with incense
    c = Cell(img, 27)
    c.rect(4, 8, 11, 13, rgb(0xC8B898))
    c.rect(6, 9, 9, 13, rgb(0x5A2A1A))
    roof(c, 3, 12, 6, red, red_d)
    c.rect(3, 14, 12, 14, rgb(0x8A8270))
    c.px(7, 11, rgb(0xFFD070))
    c.line([(8, 5), (7, 3), (8, 1)], rgb(0xD8D8D8))
    c.outline()
    # 28 (60) pos/penyeberangan: a raft on a rope across a river
    c = Cell(img, 28)
    c.rect(0, 6, 15, 14, rgb(0x4A90C0))
    c.line([(0, 4), (15, 4)], rgb(0xC8A070))
    c.rect(3, 9, 12, 11, rgb(0xB08050))
    for x in (4, 7, 10):
        c.px(x, 10, wood_d)
    c.rect(8, 5, 8, 8, wood_d)
    c.rect(0, 2, 1, 15, rgb(0x6A8A50))
    c.rect(14, 2, 15, 15, rgb(0x6A8A50))
    c.outline()
    return img


def compass():
    s = 64
    img = Image(s, s)
    cx = cy = s / 2.0
    for y in range(s):
        for x in range(s):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 27.0 <= d <= 28.2:
                img.set(x, y, INK + (220,))
            elif 24.0 <= d <= 24.8:
                img.set(x, y, INK + (140,))
    # eight points, north in seal red
    for k in range(8):
        ang = -math.pi / 2 + k * math.pi / 4
        length = 26 if k % 2 == 0 else 15
        width = 5.0 if k % 2 == 0 else 3.0
        tip = (cx + math.cos(ang) * length, cy + math.sin(ang) * length)
        left = (cx + math.cos(ang - math.pi / 2) * width, cy + math.sin(ang - math.pi / 2) * width)
        right = (cx + math.cos(ang + math.pi / 2) * width, cy + math.sin(ang + math.pi / 2) * width)
        color = (179, 40, 29) if k == 0 else INK
        for y in range(s):
            for x in range(s):
                px, py = x + 0.5, y + 0.5

                def side(a, b):
                    return (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0])

                s1, s2, s3 = side(tip, left), side(left, right), side(right, tip)
                inside = (s1 >= 0 and s2 >= 0 and s3 >= 0) or (s1 <= 0 and s2 <= 0 and s3 <= 0)
                if inside:
                    # one half dark, one half light: the classic two-tone rose
                    light = side((cx, cy), tip) > 0
                    a = 235 if light else 140
                    img.blend(x, y, color + (a,))
    for y in range(s):
        for x in range(s):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= 2.2:
                img.set(x, y, (230, 200, 120, 255))
    return img


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    save(parchment(), "parchment.png", preview)
    save(fog(), "fog.png", preview)
    save(brushes(), "brushes.png", preview)
    save(markers(), "markers.png", preview)
    save(markers_tempat(), "markers_tempat.png", preview)
    save(compass(), "compass.png", preview)
    print("gen_dunia_peta_textures: parchment, fog, brushes, markers, markers_tempat, compass")


if __name__ == "__main__":
    main()
