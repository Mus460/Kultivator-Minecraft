"""Module W (Kebun Obat & Tambang Roh, DESIGN 36.2.10) assets: run `python tools/gen_sekte_kebun_assets.py`.

Writes, all our own designs:
  * the two work props (FormModels pieces, assets/kultivator/items/fx + models/item/fx):
      sekte_kebun_pick  - a miner's pick: spruce haft wrapped in gold cord, a black-iron head with a jade wedge
      sekte_kebun_gourd - a gardener's watering gourd: two bulbs, a red cord at the waist, a bamboo spout
  * the herbs of the spirit beds, drawn by the client (never blocks), one piece per stage:
      sekte_kebun_tunas (stage 1, every herb), sekte_kebun_muda (stage 2, every herb),
      sekte_kebun_<herb>_kuncup (stage 3) and sekte_kebun_<herb>_mekar (stage 4, ripe) for the nine seeds:
      rumput (Rumput Roh), ginseng (Ginseng Sembilan Lilit), es (Rumput Es Delapan Sudut), api (Bunga Api Ungu),
      jamur (Jamur Sari Kuning), ilusi (Herba Roh Ilusi), bambu (Bambu Petir Emas), buah (Buah Ginseng),
      fondasi (Herba Fondasi)
    and their textures (textures/item/fx/sekte_kebun_*.png); the rest reuse mat_* materials.
  * the Inti Roh Gunung item (items/, models/item/, textures/item/inti_roh_gunung.png).
  * the world glow art (textures/sekte/kebun/): glow.png (soft radial), debu.png (a four-point sparkle of gold dust),
    sinar.png (a vertical ray for a rare bloom).
A piece's base sits at y = 0 and its centre at x = z = 8 (the client puts y = 0 on the bed's soil). Everything stays
within the piece rules of gen_fx_model_textures.py (<= 40 elements, -16..32, uv 0..16).
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
NS = "kultivator"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def clamp(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(lerp(c1[i], c2[i], t) for i in range(3))


# ------------------------------------------------------------------------------------------------ textures

def shaped(seed, width_at, base, edge, rib=None, veins=True, noise=8):
    """16 x 16 leaf-like mask: base of the shape at row 0, tip at row 15; width_at(t) half width in pixels (0..8)."""
    rnd = random.Random(seed)
    img = Image(16, 16)
    for y in range(16):
        t = (y + 0.5) / 16.0
        half = width_at(t)
        for x in range(16):
            d = abs(x + 0.5 - 8.0)
            if d > half:
                continue
            k = d / max(0.5, half)
            col = mix(base, edge, k ** 1.6)
            n = rnd.uniform(-noise, noise)
            col = tuple(c + n for c in col)
            if rib is not None and d < 0.75:
                col = rib
            elif veins and rib is not None and half > 2.0:
                # side veins running out and toward the tip
                if abs((d * 0.9) - ((y * 1.0) % 4.0)) < 0.45 and d > 1.0:
                    col = mix(col, rib, 0.55)
            img.set(x, y, (clamp(col[0]), clamp(col[1]), clamp(col[2]), 255))
    return img


def leaf_tex():
    return shaped(11, lambda t: 7.2 * math.sin(math.pi * min(1.0, t ** 0.75)) + (0.6 if t < 0.1 else 0.0),
                  (70, 150, 62), (34, 92, 40), rib=(150, 205, 120))


def blade_tex():
    return shaped(12, lambda t: 3.2 * (1.0 - t) ** 0.7 + 0.3, (96, 196, 104), (40, 120, 60), rib=(170, 236, 170), veins=False)


def petal_violet():
    return shaped(13, lambda t: 6.8 * math.sin(math.pi * min(1.0, 0.25 + t * 0.75)) ** 0.8,
                  (238, 168, 255), (138, 52, 210), rib=(255, 220, 255), veins=False, noise=6)


def petal_white():
    img = shaped(14, lambda t: 6.5 * math.sin(math.pi * min(1.0, 0.2 + t * 0.8)) ** 0.7,
                 (255, 252, 240), (226, 230, 214), rib=None, veins=False, noise=4)
    for y in range(3):
        for x in range(6, 10):
            if img.get(x, y)[3]:
                img.set(x, y, (250, 214, 92, 255))
    return img


def leaf_illusory():
    return shaped(15, lambda t: 4.6 * math.sin(math.pi * min(1.0, t ** 0.6)),
                  (226, 214, 255), (150, 128, 222), rib=(255, 255, 255), noise=7)


def full(seed, fn):
    rnd = random.Random(seed)
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            r, g, b = fn(x, y, rnd)
            img.set(x, y, (clamp(r), clamp(g), clamp(b), 255))
    return img


def stem_tex():
    return full(21, lambda x, y, r: tuple(c + r.uniform(-5, 5) for c in mix((58, 128, 52), (126, 196, 96), 0.5 + 0.5 * math.sin(x * 0.9))))


def cap_yellow():
    def f(x, y, r):
        base = mix((255, 214, 64), (196, 128, 24), y / 15.0)
        spot = ((x - 4) ** 2 + (y - 5) ** 2 < 3) or ((x - 11) ** 2 + (y - 9) ** 2 < 4) or ((x - 6) ** 2 + (y - 12) ** 2 < 2)
        if spot:
            return (255, 246, 206)
        n = r.uniform(-10, 10)
        return tuple(c + n for c in base)
    return full(22, f)


def mushroom_stem():
    return full(23, lambda x, y, r: tuple(c + r.uniform(-8, 8) for c in mix((246, 236, 206), (214, 196, 160), (x % 5) / 5.0)))


def berry_red():
    def f(x, y, r):
        d = math.hypot(x - 5.5, y - 5.0)
        base = mix((255, 150, 140), (176, 22, 30), min(1.0, d / 9.0))
        return tuple(c + r.uniform(-6, 6) for c in base)
    return full(24, f)


def root_tan():
    def f(x, y, r):
        ring = 18 if y % 3 == 0 else 0
        base = (222 - ring, 184 - ring, 128 - ring * 0.6)
        return tuple(c + r.uniform(-9, 9) for c in base)
    return full(25, f)


def orb_illusory():
    def f(x, y, r):
        d = math.hypot(x - 7.5, y - 7.5)
        return mix((255, 255, 255), (178, 150, 255), min(1.0, d / 9.5))
    return full(26, f)


def bamboo_gold():
    def f(x, y, r):
        base = mix((255, 230, 120), (204, 150, 34), abs(x - 7.5) / 8.0)
        if y in (0, 15):
            base = (150, 102, 24)
        # a thin lightning streak down the cane
        if x == (7 + int(2 * math.sin(y * 1.3))) and r.random() < 0.85:
            base = (255, 252, 222)
        return tuple(c + r.uniform(-6, 6) for c in base)
    return full(27, f)


def fruit_ginseng():
    def f(x, y, r):
        d = math.hypot(x - 7.5, y - 6.0)
        base = mix((255, 246, 222), (236, 206, 160), min(1.0, d / 10.0))
        if 4 <= x <= 6 and 7 <= y <= 8 or 9 <= x <= 11 and 7 <= y <= 8:
            base = mix(base, (255, 170, 160), 0.45)  # the blush of the "child-shaped" fruit
        return tuple(c + r.uniform(-4, 4) for c in base)
    return full(28, f)


def seed_mint():
    def f(x, y, r):
        d = math.hypot(x - 7.5, y - 7.5)
        return mix((236, 255, 236), (110, 230, 150), min(1.0, d / 9.0))
    return full(29, f)


def foliage():
    def f(x, y, r):
        n = r.random()
        base = (52, 124, 54) if n < 0.45 else (78, 160, 70) if n < 0.85 else (124, 200, 104)
        return tuple(c + r.uniform(-6, 6) for c in base)
    return full(30, f)


def glow_tex():
    img = Image(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 15.5
            a = max(0.0, 1.0 - d) ** 2.2
            img.set(x, y, (255, 255, 255, clamp(a * 255)))
    return img


def dust_tex():
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            core = max(0.0, 1.0 - math.hypot(dx, dy) / 3.2)
            cross = max(0.0, 1.0 - min(dx, dy) / 0.9) * max(0.0, 1.0 - max(dx, dy) / 7.5)
            a = min(1.0, core + cross * 0.85)
            img.set(x, y, (255, 255, 255, clamp(a * 255)))
    return img


def ray_tex():
    img = Image(16, 64)
    for y in range(64):
        t = y / 63.0  # 0 top .. 1 bottom
        for x in range(16):
            side = max(0.0, 1.0 - abs(x - 7.5) / 7.5) ** 1.5
            a = side * (t ** 1.3) * (0.85 + 0.15 * math.sin(y * 0.7))
            img.set(x, y, (255, 255, 255, clamp(a * 255)))
    return img


def inti_icon():
    """16 x 16 inventory icon of the Inti Roh Gunung: a faceted core in Nuwa's five colours (azure, crimson, gold, white,
    black-jade) round a white-hot heart, inside a rim of mountain rock, with a few sparks."""
    rnd = random.Random(52)
    img = Image(16, 16)
    five = [(84, 170, 255), (232, 64, 58), (255, 206, 72), (244, 244, 236), (40, 96, 78)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            # an octagonal outline
            d = max(abs(dx), abs(dy), (abs(dx) + abs(dy)) * 0.72)
            if d > 6.6:
                continue
            if d > 5.4:
                g = rnd.randint(70, 110)
                img.set(x, y, (g, g - 4, g - 10, 255))  # the rock rim
                continue
            sector = int(((math.atan2(dy, dx) + math.pi) / (2 * math.pi)) * 5) % 5
            base = five[sector]
            t = d / 5.4
            core = (255, 250, 228)
            col = mix(core, base, min(1.0, t ** 0.8 * 1.2))
            shade = 1.0 - 0.25 * (dx + dy) / 10.0  # light from the upper left
            img.set(x, y, (clamp(col[0] * shade), clamp(col[1] * shade), clamp(col[2] * shade), 255))
    for (x, y) in ((5, 4), (4, 5), (6, 5)):
        img.set(x, y, (255, 255, 255, 255))
    for (x, y) in ((1, 2), (14, 3), (13, 13), (2, 12)):
        img.set(x, y, (255, 232, 150, 220))
    return img


# ------------------------------------------------------------------------------------------------ models

class Model:
    def __init__(self):
        self.tex = {}
        self.els = []

    def key(self, name):
        if name not in self.tex:
            self.tex[name] = "t%d" % len(self.tex)
        return "#" + self.tex[name]

    def box(self, a, b, tex, euler=None, origin=None, full=False):
        faces = {}
        dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        sizes = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
        for face, (w, h) in sizes.items():
            uv = [0.0, 0.0, 16.0, 16.0] if full else [0.0, 0.0, round(min(16.0, max(0.5, w)), 2), round(min(16.0, max(0.5, h)), 2)]
            faces[face] = {"uv": uv, "texture": self.key(tex)}
        self._add(a, b, faces, euler, origin)

    def flat(self, a, b, tex, euler, origin):
        """A thin sheet (leaf, petal, blade) whose shaped texture runs from its base (z from) to its tip (z to)."""
        k = self.key(tex)
        faces = {
            "up": {"uv": [0.0, 0.0, 16.0, 16.0], "texture": k},
            "down": {"uv": [0.0, 16.0, 16.0, 0.0], "texture": k},
            "east": {"uv": [7.5, 0.0, 8.5, 16.0], "texture": k},
            "west": {"uv": [7.5, 0.0, 8.5, 16.0], "texture": k},
        }
        self._add(a, b, faces, euler, origin)

    def _add(self, a, b, faces, euler, origin):
        el = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b], "faces": faces}
        if euler:
            ex, ey, ez = euler
            o = origin if origin else [(a[i] + b[i]) / 2.0 for i in range(3)]
            el["rotation"] = {"origin": [round(v, 3) for v in o], "x": round(ex, 2), "y": round(ey, 2), "z": round(ez, 2)}
        self.els.append(el)

    def json(self):
        tex = {k: "%s:item/fx/%s" % (NS, name) for name, k in self.tex.items()}
        tex["particle"] = "%s:item/fx/%s" % (NS, next(iter(self.tex)))
        return {"textures": tex, "elements": self.els}

    # -------------------------------------------------------------------------------- plant helpers

    def stem(self, x, z, y0, h, w, tex="sekte_kebun_batang", lean=None):
        a = [x - w / 2.0, y0, z - w / 2.0]
        b = [x + w / 2.0, y0 + h, z + w / 2.0]
        if lean:
            self.box(a, b, tex, euler=lean, origin=[x, y0, z])
        else:
            self.box(a, b, tex)

    def leaf(self, x, y, z, length, width, yaw, rise, tex="sekte_kebun_daun", thick=0.12):
        """A sheet from (x, y, z) outward by length, lifted by rise degrees above flat, turned yaw degrees about y."""
        self.flat([x - width / 2.0, y, z], [x + width / 2.0, y + thick, z + length], tex, (-rise, yaw, 0.0), [x, y, z])


def rosette(m, n, y, length, width, rise, tex="sekte_kebun_daun", phase=0.0, r=0.4):
    for i in range(n):
        yaw = phase + 360.0 * i / n
        ox = 8.0 + r * math.sin(math.radians(yaw))
        oz = 8.0 + r * math.cos(math.radians(yaw))
        m.leaf(ox, y, oz, length, width, yaw, rise, tex)


# ---- props

def pick():
    m = Model()
    # the haft (spruce), wrapped in gold cord near the grip and below the head
    m.box([7.45, 0.0, 7.45], [8.55, 13.4, 8.55], "mat_wood")
    m.box([7.3, 2.0, 7.3], [8.7, 3.0, 8.7], "mat_rope_gold")
    m.box([7.3, 4.2, 7.3], [8.7, 4.8, 8.7], "mat_rope_gold")
    m.box([7.3, 11.4, 7.3], [8.7, 12.2, 8.7], "mat_rope_gold")
    m.box([7.6, -0.6, 7.6], [8.4, 0.0, 8.4], "mat_iron_black")
    # the head: a socket, two tapering black-iron arms curving down, a jade wedge on the socket
    m.box([7.0, 12.6, 7.0], [9.0, 15.0, 9.0], "mat_iron_black")
    m.box([9.0, 13.2, 7.3], [11.4, 14.6, 8.7], "mat_iron_black")
    m.box([11.2, 12.6, 7.45], [13.2, 13.9, 8.55], "mat_iron_black", euler=(0.0, 0.0, -18.0), origin=[11.2, 13.4, 8.0])
    m.box([12.9, 11.2, 7.6], [14.1, 12.8, 8.4], "mat_blade_steel", euler=(0.0, 0.0, -34.0), origin=[13.2, 12.4, 8.0])
    m.box([4.6, 13.2, 7.3], [7.0, 14.6, 8.7], "mat_iron_black")
    m.box([2.8, 12.6, 7.45], [4.8, 13.9, 8.55], "mat_iron_black", euler=(0.0, 0.0, 18.0), origin=[4.8, 13.4, 8.0])
    m.box([1.9, 11.2, 7.6], [3.1, 12.8, 8.4], "mat_blade_steel", euler=(0.0, 0.0, 34.0), origin=[2.8, 12.4, 8.0])
    m.box([7.55, 15.0, 7.55], [8.45, 15.5, 8.45], "mat_jade")
    m.box([6.9, 13.5, 8.95], [9.1, 14.3, 9.1], "mat_jade")
    return m


def gourd():
    m = Model()
    # lower bulb (two crossed boxes for roundness), the waist with a red cord, the upper bulb, a wooden stopper
    m.box([4.4, 2.0, 4.4], [11.6, 8.4, 11.6], "mat_gourd_yellow")
    m.box([5.2, 1.2, 5.2], [10.8, 9.2, 10.8], "mat_gourd_yellow", euler=(0.0, 45.0, 0.0), origin=[8.0, 5.2, 8.0])
    m.box([6.6, 8.4, 6.6], [9.4, 9.8, 9.4], "mat_gourd_yellow")
    m.box([6.3, 9.0, 6.3], [9.7, 9.8, 9.7], "mat_cloth_red")
    m.box([5.6, 9.8, 5.6], [10.4, 13.2, 10.4], "mat_gourd_yellow")
    m.box([6.1, 9.4, 6.1], [9.9, 13.8, 9.9], "mat_gourd_yellow", euler=(0.0, 45.0, 0.0), origin=[8.0, 11.6, 8.0])
    m.box([7.2, 13.8, 7.2], [8.8, 15.2, 8.8], "mat_wood")
    # the red cord's tassel and a bamboo spout leaning out of the upper bulb
    m.box([9.6, 6.4, 7.8], [10.2, 9.2, 8.2], "mat_cloth_red")
    m.box([9.4, 5.6, 7.6], [10.4, 6.4, 8.4], "mat_rope_gold")
    m.box([7.4, 11.0, 7.4], [8.6, 18.0, 8.6], "sekte_kebun_batang", euler=(0.0, 0.0, -55.0), origin=[8.0, 11.0, 8.0])
    m.box([13.1, 14.6, 7.3], [14.5, 15.6, 8.7], "sekte_kebun_batang", euler=(0.0, 0.0, -55.0), origin=[13.8, 15.1, 8.0])
    return m


# ---- generic stages

def tunas():
    m = Model()
    m.stem(8.0, 8.0, 0.0, 2.6, 0.5)
    m.leaf(8.0, 2.4, 8.0, 3.2, 2.4, 90.0, 28.0)
    m.leaf(8.0, 2.4, 8.0, 3.2, 2.4, 270.0, 28.0)
    m.leaf(8.0, 2.6, 8.0, 1.6, 1.2, 0.0, 70.0)
    # a crumb of fresh soil round the sprout
    m.box([6.8, 0.0, 6.8], [9.2, 0.3, 9.2], "mat_charred")
    return m


def muda():
    m = Model()
    m.stem(8.0, 8.0, 0.0, 5.0, 0.7)
    rosette(m, 4, 1.6, 4.4, 3.0, 22.0, phase=20.0)
    rosette(m, 3, 3.6, 3.4, 2.4, 40.0, phase=80.0)
    m.leaf(8.0, 4.8, 8.0, 2.2, 1.6, 200.0, 70.0)
    m.leaf(8.0, 4.8, 8.0, 2.2, 1.6, 20.0, 70.0)
    return m


# ---- the nine herbs, bud (False) and bloom (True)

def rumput(bloom):
    m = Model()
    n = 9 if bloom else 7
    for i in range(n):
        yaw = 360.0 * i / n + (i % 2) * 17.0
        length = (8.5 if bloom else 6.5) + (i % 3) * 1.3
        m.leaf(8.0 + 0.6 * math.sin(math.radians(yaw)), 0.0, 8.0 + 0.6 * math.cos(math.radians(yaw)), length, 1.5, yaw,
               62.0 + (i % 3) * 7.0, "sekte_kebun_bilah")
    heads = 5 if bloom else 3
    for i in range(heads):
        a = math.radians(70.0 * i + 15.0)
        x, z = 8.0 + 1.6 * math.sin(a), 8.0 + 1.6 * math.cos(a)
        h = (9.5 if bloom else 7.0) + (i % 2) * 1.6
        m.stem(x, z, 0.0, h, 0.35)
        s = 0.55 if bloom else 0.4
        m.box([x - s, h - 0.2, z - s], [x + s, h + (2.6 if bloom else 1.8), z + s], "sekte_kebun_biji" if bloom else "sekte_kebun_batang")
    return m


def ginseng(bloom):
    m = Model()
    # the crown of the root showing above the soil, three petioles, each with a palm of five leaflets
    m.box([6.6, 0.0, 6.6], [9.4, 1.4, 9.4], "sekte_kebun_akar")
    m.box([7.1, 1.4, 7.1], [8.9, 2.0, 8.9], "sekte_kebun_akar")
    h = 9.0 if bloom else 7.5
    m.stem(8.0, 8.0, 0.0, h, 0.7)
    for p in range(3):
        yaw = 120.0 * p + 30.0
        ry = math.radians(yaw)
        px, pz = 8.0 + 2.6 * math.sin(ry), 8.0 + 2.6 * math.cos(ry)
        py = h - 2.2
        m.leaf(8.0, py - 0.1, 8.0, 2.8, 0.5, yaw, 25.0, "sekte_kebun_batang", thick=0.4)
        for k in range(5):
            m.leaf(px, py + 1.0, pz, 3.6 - abs(k - 2) * 0.5, 1.9, yaw + (k - 2) * 34.0, 14.0)
    if bloom:
        # the scarlet berry cluster on the central stalk
        for i in range(7):
            a = math.radians(51.4 * i)
            r = 0.0 if i == 6 else 0.9
            x, z = 8.0 + r * math.sin(a), 8.0 + r * math.cos(a)
            y = h + (0.9 if i == 6 else 0.2)
            m.box([x - 0.65, y, z - 0.65], [x + 0.65, y + 1.3, z + 0.65], "sekte_kebun_buah_merah", full=True)
    else:
        m.box([7.3, h, 7.3], [8.7, h + 1.2, 8.7], "sekte_kebun_batang")
    return m


def es(bloom):
    m = Model()
    for i in range(8):
        yaw = 45.0 * i + 22.5
        length = (8.5 if bloom else 6.0) + (i % 2) * 1.8
        m.box([7.4, 0.0, 7.6], [8.6, 0.7, 7.6 + length], "mat_ice", euler=(-40.0 - (i % 2) * 12.0, yaw, 0.0), origin=[8.0, 0.0, 8.0])
    for i in range(4):
        yaw = 90.0 * i
        m.box([7.5, 0.0, 7.7], [8.5, 0.6, 7.7 + (6.0 if bloom else 4.0)], "mat_ice_solid", euler=(-70.0, yaw, 0.0), origin=[8.0, 0.0, 8.0])
    if bloom:
        # the crystal heart, an octahedron faked by two turned cubes, and frost tips
        m.box([6.6, 6.0, 6.6], [9.4, 8.8, 9.4], "mat_ice", euler=(0.0, 45.0, 0.0), origin=[8.0, 7.4, 8.0])
        m.box([6.9, 6.3, 6.9], [9.1, 8.5, 9.1], "mat_glow_white", euler=(45.0, 0.0, 45.0), origin=[8.0, 7.4, 8.0])
        m.box([7.6, 8.8, 7.6], [8.4, 10.6, 8.4], "mat_ice_solid")
    else:
        m.box([7.2, 3.0, 7.2], [8.8, 5.4, 8.8], "mat_ice", euler=(0.0, 45.0, 0.0), origin=[8.0, 4.2, 8.0])
    return m


def api(bloom):
    m = Model()
    h = 8.5 if bloom else 7.5
    m.stem(8.0, 8.0, 0.0, h, 0.7)
    rosette(m, 4, 1.0, 4.2, 2.6, 25.0, phase=45.0)
    m.leaf(8.0, 4.2, 8.0, 3.2, 2.0, 160.0, 45.0)
    m.leaf(8.0, 5.0, 8.0, 3.0, 1.8, 340.0, 45.0)
    if bloom:
        # six violet petals open round a burning core, two inner petals standing up
        for i in range(6):
            m.leaf(8.0, h, 8.0, 4.6, 3.4, 60.0 * i, 32.0, "sekte_kebun_kelopak_ungu")
        for i in range(3):
            m.leaf(8.0, h + 0.2, 8.0, 3.0, 2.4, 120.0 * i + 30.0, 64.0, "sekte_kebun_kelopak_ungu")
        m.box([7.2, h, 7.2], [8.8, h + 2.6, 8.8], "mat_flame_violet")
        m.box([7.6, h + 2.6, 7.6], [8.4, h + 3.8, 8.4], "mat_flame_violet", euler=(0.0, 45.0, 0.0), origin=[8.0, h + 3.2, 8.0])
    else:
        # the closed bud: sepals clasping a violet tip
        m.box([7.0, h, 7.0], [9.0, h + 2.4, 9.0], "sekte_kebun_kelopak_ungu", full=True)
        m.box([7.4, h + 2.4, 7.4], [8.6, h + 3.4, 8.6], "sekte_kebun_kelopak_ungu", full=True)
        for i in range(4):
            m.leaf(8.0, h - 0.3, 8.0, 2.4, 1.4, 90.0 * i, 70.0)
    return m


def jamur(bloom):
    m = Model()
    caps = [(8.0, 8.0, 6.5, 2.8), (5.4, 10.2, 4.2, 2.0), (10.6, 5.8, 3.4, 1.7)] if bloom else \
        [(8.0, 8.0, 3.6, 1.8), (5.8, 9.8, 2.4, 1.3), (10.2, 6.2, 2.0, 1.1)]
    for (x, z, h, r) in caps:
        w = 0.45 * r + 0.25
        m.box([x - w, 0.0, z - w], [x + w, h, z + w], "sekte_kebun_batang_jamur")
        m.box([x - r, h, z - r], [x + r, h + 0.9, z + r], "sekte_kebun_tudung", full=True)
        m.box([x - r * 0.62, h + 0.9, z - r * 0.62], [x + r * 0.62, h + 1.6, z + r * 0.62], "sekte_kebun_tudung", full=True)
        if bloom:
            # the gills' ring under the cap
            m.box([x - r * 0.8, h - 0.25, z - r * 0.8], [x + r * 0.8, h, z + r * 0.8], "sekte_kebun_batang_jamur")
    if bloom:
        for (x, z) in ((11.6, 10.8), (4.6, 5.2)):
            m.box([x - 0.3, 0.0, z - 0.3], [x + 0.3, 1.2, z + 0.3], "sekte_kebun_batang_jamur")
            m.box([x - 0.8, 1.2, z - 0.8], [x + 0.8, 1.8, z + 0.8], "sekte_kebun_tudung", full=True)
    # moss under the cluster
    m.box([4.0, 0.0, 4.0], [12.0, 0.25, 12.0], "sekte_kebun_rimbun", full=True)
    return m


def ilusi(bloom):
    m = Model()
    for i in range(6):
        yaw = 60.0 * i + 10.0
        m.leaf(8.0, 0.0, 8.0, (7.0 if bloom else 5.5) - (i % 2), 2.4, yaw, 68.0 + (i % 2) * 8.0, "sekte_kebun_daun_ilusi")
    m.stem(8.0, 8.0, 0.0, 6.0 if bloom else 4.0, 0.4, "sekte_kebun_daun_ilusi")
    if bloom:
        # three orbs of illusion floating free above the leaves on hair-thin threads
        for i, (dx, dz, y, s) in enumerate(((0.0, 0.0, 9.5, 1.3), (2.6, 1.2, 7.6, 0.9), (-2.2, -1.6, 8.4, 1.0))):
            x, z = 8.0 + dx, 8.0 + dz
            m.stem(x, z, 3.0, y - 3.0, 0.15, "sekte_kebun_daun_ilusi")
            m.box([x - s, y, z - s], [x + s, y + 2 * s, z + s], "sekte_kebun_bola", full=True)
            m.box([x - s * 0.8, y + 0.2, z - s * 0.8], [x + s * 0.8, y + 2 * s - 0.2, z + s * 0.8], "sekte_kebun_bola", full=True,
                  euler=(0.0, 45.0, 0.0), origin=[x, y + s, z])
    else:
        m.box([7.3, 4.0, 7.3], [8.7, 5.6, 8.7], "sekte_kebun_bola", full=True)
    return m


def bambu(bloom):
    m = Model()
    canes = [(8.0, 8.0, 26.0), (5.6, 9.6, 19.0), (10.4, 6.2, 22.5)] if bloom else [(8.0, 8.0, 12.0), (5.8, 9.4, 9.0)]
    for (x, z, h) in canes:
        w = 0.6
        m.box([x - w, 0.0, z - w], [x + w, h, z + w], "sekte_kebun_bambu")
        y = 4.5
        while y < h - 1.0:
            m.box([x - w - 0.2, y, z - w - 0.2], [x + w + 0.2, y + 0.45, z + w + 0.2], "sekte_kebun_bambu")
            y += 5.0
        # two narrow leaves at the top knot of every cane
        m.leaf(x, h - 2.2, z, 4.0, 1.2, 40.0 + x * 20.0, 18.0, "sekte_kebun_bilah")
        m.leaf(x, h - 3.0, z, 3.6, 1.1, 220.0 + x * 20.0, 22.0, "sekte_kebun_bilah")
    # the young shoot at the foot
    m.box([9.6, 0.0, 9.6], [10.8, 2.0, 10.8], "sekte_kebun_bambu")
    return m


def buah(bloom):
    m = Model()
    trunk = 9.0 if bloom else 7.0
    m.box([7.1, 0.0, 7.1], [8.9, trunk, 8.9], "mat_wood")
    m.box([6.6, 0.0, 6.6], [9.4, 1.0, 9.4], "mat_wood")
    # branches and four cloud-like canopy clusters
    for i in range(3):
        yaw = 120.0 * i + 15.0
        m.box([7.5, trunk - 3.0, 7.5], [8.5, trunk - 2.0, 12.0], "mat_wood", euler=(-30.0, yaw, 0.0), origin=[8.0, trunk - 2.5, 8.0])
    spots = [(8.0, 8.0, trunk + 1.0, 3.4), (5.0, 9.6, trunk - 0.6, 2.6), (10.8, 9.2, trunk - 0.2, 2.6), (8.6, 4.8, trunk - 0.4, 2.5)]
    for (x, z, y, r) in spots:
        m.box([x - r, y - 1.4, z - r], [x + r, y + 1.4, z + r], "sekte_kebun_rimbun", full=True)
        m.box([x - r * 0.7, y + 1.4, z - r * 0.7], [x + r * 0.7, y + 2.2, z + r * 0.7], "sekte_kebun_rimbun", full=True)
    if bloom:
        # five pale, child-shaped fruits hanging under the canopy (the Ginseng Fruit of Journey to the West, ch. 24)
        for (x, z, y) in ((5.0, 12.2, trunk - 4.6), (11.8, 10.6, trunk - 4.2), (9.4, 2.8, trunk - 4.4), (3.0, 7.4, trunk - 4.8),
                          (12.8, 6.2, trunk - 5.0)):
            m.box([x - 0.8, y, z - 0.8], [x + 0.8, y + 2.4, z + 0.8], "sekte_kebun_buah_ginseng", full=True)
            m.box([x - 0.5, y + 2.4, z - 0.5], [x + 0.5, y + 3.0, z + 0.5], "sekte_kebun_rimbun", full=True)
            m.box([x - 0.12, y + 3.0, z - 0.12], [x + 0.12, y + 4.6, z + 0.12], "mat_wood")
    else:
        for (x, z, y) in ((5.0, 11.8, trunk - 2.8), (11.6, 10.4, trunk - 2.6)):
            m.box([x - 0.6, y, z - 0.6], [x + 0.6, y + 1.0, z + 0.6], "sekte_kebun_rimbun", full=True)
    return m


def fondasi(bloom):
    m = Model()
    rosette(m, 6, 0.4, 4.8, 2.6, 18.0, phase=15.0)
    rosette(m, 4, 1.6, 3.6, 2.2, 38.0, phase=60.0)
    h = 8.0 if bloom else 6.5
    m.stem(8.0, 8.0, 0.0, h, 0.6)
    for i in range(3):
        a = math.radians(120.0 * i)
        x, z = 8.0 + 1.6 * math.sin(a), 8.0 + 1.6 * math.cos(a)
        m.stem(x, z, h - 2.6, 2.4, 0.3)
        for k in range(3):
            b = math.radians(120.0 * k + 30.0)
            fx, fz = x + 0.7 * math.sin(b), z + 0.7 * math.cos(b)
            s = 0.6 if bloom else 0.45
            m.box([fx - s, h - 0.2, fz - s], [fx + s, h + 2 * s - 0.2, fz + s], "sekte_kebun_kelopak_putih" if bloom else "sekte_kebun_batang",
                  full=True)
    return m


HERBS = (("rumput", rumput), ("ginseng", ginseng), ("es", es), ("api", api), ("jamur", jamur), ("ilusi", ilusi),
         ("bambu", bambu), ("buah", buah), ("fondasi", fondasi))


def validate(name, m):
    problems = []
    if len(m.els) > 40:
        problems.append("%d elements" % len(m.els))
    for i, el in enumerate(m.els):
        for k in ("from", "to"):
            if any(v < -16.0 or v > 32.0 for v in el[k]):
                problems.append("element %d %s out of range %s" % (i, k, el[k]))
        if any(el["to"][j] < el["from"][j] for j in range(3)):
            problems.append("element %d inverted" % i)
        for face in el["faces"].values():
            if any(v < 0 or v > 16 for v in face["uv"]):
                problems.append("element %d uv %s" % (i, face["uv"]))
    for tex in m.tex:
        if not os.path.exists(os.path.join(ROOT, "textures", "item", "fx", tex + ".png")):
            problems.append("texture %s missing" % tex)
    for p in problems:
        print("MODEL %s: %s" % (name, p))
    return len(problems)


def main():
    fx = os.path.join(ROOT, "textures", "item", "fx")
    os.makedirs(fx, exist_ok=True)
    for name, img in (("daun", leaf_tex()), ("bilah", blade_tex()), ("batang", stem_tex()), ("kelopak_ungu", petal_violet()),
                      ("kelopak_putih", petal_white()), ("daun_ilusi", leaf_illusory()), ("tudung", cap_yellow()),
                      ("batang_jamur", mushroom_stem()), ("buah_merah", berry_red()), ("akar", root_tan()), ("bola", orb_illusory()),
                      ("bambu", bamboo_gold()), ("buah_ginseng", fruit_ginseng()), ("biji", seed_mint()), ("rimbun", foliage())):
        img.save(os.path.join(fx, "sekte_kebun_%s.png" % name))
    item = os.path.join(ROOT, "textures", "item")
    inti_icon().save(os.path.join(item, "inti_roh_gunung.png"))
    write_json(os.path.join(ROOT, "models", "item", "inti_roh_gunung.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/inti_roh_gunung" % NS}})
    write_json(os.path.join(ROOT, "items", "inti_roh_gunung.json"),
               {"model": {"type": "minecraft:model", "model": "%s:item/inti_roh_gunung" % NS}})
    world = os.path.join(ROOT, "textures", "sekte", "kebun")
    os.makedirs(world, exist_ok=True)
    glow_tex().save(os.path.join(world, "glow.png"))
    dust_tex().save(os.path.join(world, "debu.png"))
    ray_tex().save(os.path.join(world, "sinar.png"))
    pieces = [("sekte_kebun_pick", pick()), ("sekte_kebun_gourd", gourd()), ("sekte_kebun_tunas", tunas()), ("sekte_kebun_muda", muda())]
    for herb, fn in HERBS:
        pieces.append(("sekte_kebun_%s_kuncup" % herb, fn(False)))
        pieces.append(("sekte_kebun_%s_mekar" % herb, fn(True)))
    errors = 0
    for name, m in pieces:
        errors += validate(name, m)
        write_json(os.path.join(ROOT, "models", "item", "fx", name + ".json"), m.json())
        write_json(os.path.join(ROOT, "items", "fx", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "%s:item/fx/%s" % (NS, name)}})
    print("sekte kebun assets written: %d pieces, errors: %d" % (len(pieces), errors))
    for name, m in pieces:
        print("  %-28s %2d elements" % (name, len(m.els)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
