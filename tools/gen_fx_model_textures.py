"""Generates the 3D technique props of the polish pass (DESIGN 27, module M3 "anim+models").

Everything here is ours: cuboid item models (Minecraft item-model JSON with "elements", free Euler rotations and
"light_emission") and the procedural textures they use. Nothing is downloaded.

Writes:
  textures/item/fx/<name>.png     the material library (32x32, value noise, edge darkening, rim highlight per material)
                                  plus unique sheets (taiji, clock face, bagua, glyph plates, mirror faces, rune band...)
  models/item/fx/<id>.json        one cuboid model per prop (<= 40 elements, extents -16..32)
  items/fx/<id>.json              the item definitions the client points DataComponents.ITEM_MODEL at
                                  (kultivator:fx/<id>); no item is registered
  models/item/natal_*.json        the disciple natal treasures as real 3D models (sword, parasol, wok, bell)

Model conventions (read by client/anim/model/FxModels and form/FormModels):
  * The anchor of every model is (8, 8, 8): the centre of floating props, the base centre of standing props (they rise
    from y = 8 up to y = 32), the grip of weapons (blade towards +y) and the joint of form pieces.
  * The front faces north (-z). Projectiles point north. Player-form pieces use: up = +y, front = -z, the wearer's
    right = +x (the form layer turns the part space by 180 degrees about z).
  * No "display" block: the FIXED context uses the identity transform, so (8, 8, 8) lands on the display origin.

Run with: python tools/gen_fx_model_textures.py   (validates every model and texture and prints a summary)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")
NS = "kultivator"

S = 32  # material size


# ================================================================================================ colour helpers

def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp(core, body, rim, t):
    """t = 0 rim (dark) .. 0.5 body .. 1 core (bright)."""
    if t < 0.5:
        return mix(rim, body, t / 0.5)
    return mix(body, core, (t - 0.5) / 0.5)


def clamp01(v):
    return max(0.0, min(1.0, v))


# ================================================================================================ noise

class Noise:
    def __init__(self, seed):
        r = random.Random(seed)
        self.p = list(range(256))
        r.shuffle(self.p)
        self.p += self.p
        self.g = [r.random() for _ in range(256)]

    def lattice(self, x, y):
        return self.g[self.p[(self.p[x & 255] + y) & 255]]

    def smooth(self, x, y, period=None):
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

        def w(ix, iy):
            if period:
                ix %= period
                iy %= period
            return self.lattice(ix, iy)
        a = w(x0, y0) + (w(x0 + 1, y0) - w(x0, y0)) * sx
        b = w(x0, y0 + 1) + (w(x0 + 1, y0 + 1) - w(x0, y0 + 1)) * sx
        return a + (b - a) * sy

    def fbm(self, x, y, octaves=3, period=None):
        total, amp, norm, f = 0.0, 1.0, 0.0, 1.0
        for _ in range(octaves):
            total += self.smooth(x * f, y * f, period * int(f) if period else None) * amp
            norm += amp
            amp *= 0.5
            f *= 2
        return total / norm


# ================================================================================================ material library

TEXTURES = {}  # name -> Image


def put(name, img):
    TEXTURES[name] = img


def finish(img, core, body, rim, alpha, edge=True, bevel=True):
    """Edge darkening (2 px towards the rim colour) and a top-left bevel highlight towards the core colour."""
    w, h = img.width, img.height
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.get(x, y)
            if a == 0:
                continue
            c = (r, g, b)
            if edge:
                d = min(x, y, w - 1 - x, h - 1 - y)
                if d == 0:
                    c = mix(c, rim, 0.55)
                elif d == 1:
                    c = mix(c, rim, 0.22)
            if bevel and (x == 1 or y == 1) and x < w - 2 and y < h - 2:
                c = mix(c, core, 0.35)
            img.set(x, y, (c[0], c[1], c[2], a))
    return img


def material(name, core, body, rim, pattern, alpha=255, seed=1, extra=None):
    core, body, rim = rgb(core), rgb(body), rgb(rim)
    n = Noise(seed * 7919 + len(name))
    rnd = random.Random(seed)
    img = Image(S, S)
    for y in range(S):
        for x in range(S):
            u, v = x / S, y / S
            base = n.fbm(x / 8.0, y / 8.0, 3, 4)
            a = alpha
            if pattern == "metal":
                streak = n.smooth(x / 16.0, y * 1.3, None)
                t = 0.35 + 0.35 * base + 0.25 * streak
            elif pattern == "gold":
                streak = n.smooth(x / 12.0, y * 1.1, None)
                t = 0.42 + 0.3 * base + 0.25 * streak
                if (x + 2 * y) % 23 == 0:
                    t = 0.95
            elif pattern == "jade":
                vein = math.sin((x * 0.35 + y * 0.18) + n.fbm(x / 6.0, y / 6.0, 3, 6) * 6.0)
                t = 0.45 + 0.2 * base + 0.3 * (vein ** 9 if vein > 0 else 0)
            elif pattern == "stone":
                t = 0.3 + 0.45 * base + 0.1 * n.smooth(x / 2.0, y / 2.0)
            elif pattern == "ice":
                # facets: nearest of a few seeds, bright facet edges
                t = 0.55 + 0.25 * base
            elif pattern == "flame":
                tongue = n.fbm(x / 5.0, (y + x * 0.2) / 3.0, 3)
                t = clamp01(1.05 - v * 0.85 + (tongue - 0.5) * 0.7)
                a = int(alpha * clamp01(0.55 + t))
            elif pattern == "blade":
                cx = abs(u - 0.5) * 2.0
                t = clamp01(0.95 - cx * 0.55 + (base - 0.5) * 0.15)
            elif pattern == "energy":
                t = 0.35 + 0.3 * base
            elif pattern == "blood":
                t = 0.3 + 0.4 * base
            elif pattern == "wood":
                grain = math.sin(x * 0.9 + n.fbm(x / 10.0, y / 3.0, 2) * 7.0)
                t = 0.4 + 0.18 * grain + 0.2 * base
            elif pattern == "leaf":
                vein = 1.0 if abs(x - 16) < 1 or (abs((y - 16) - abs(x - 16) * 0.8) < 0.8 and y % 6 < 4) else 0.0
                t = 0.42 + 0.25 * base + 0.3 * vein
            elif pattern == "cloth":
                weave = 0.08 if (x + y) % 2 == 0 else -0.04
                fold = 0.12 * math.sin(x * 0.4 + base * 3.0)
                t = 0.48 + 0.18 * base + weave + fold
            elif pattern == "porcelain":
                t = 0.62 + 0.25 * base
            elif pattern == "lotus":
                t = clamp01(0.95 - v * 0.75 + 0.12 * (base - 0.5)) if (x // 4) % 2 == 0 else clamp01(0.85 - v * 0.7 + 0.1 * base)
            elif pattern == "feather":
                barb = 0.18 if ((x + y // 2) % 4 == 0) else 0.0
                shaft = 0.35 if abs(x - 16) < 1 else 0.0
                t = clamp01(0.85 - v * 0.55 + barb + shaft + 0.1 * (base - 0.5))
            elif pattern == "smoke":
                t = 0.25 + 0.55 * n.fbm(x / 6.0, y / 6.0, 4, 6)
                a = int(alpha * clamp01(0.5 + t))
            elif pattern == "cloud":
                t = 0.55 + 0.4 * n.fbm(x / 7.0, y / 7.0, 3, 5)
                a = int(alpha * clamp01(0.65 + 0.5 * t))
            elif pattern == "hair":
                strand = math.sin(x * 1.6 + n.smooth(x / 3.0, y / 9.0) * 4.0)
                t = 0.5 + 0.3 * strand + 0.15 * base
            elif pattern == "fur":
                strand = n.smooth(x / 1.5, y / 4.0)
                t = 0.4 + 0.35 * strand + 0.2 * base
            elif pattern == "scale":
                row = y // 5
                ox = (x + (row % 2) * 4) % 8
                oy = y % 5
                d = math.hypot(ox - 4, oy - 5) / 4.5
                t = clamp01(0.85 - d * 0.55 + 0.1 * base)
                if 0.85 < d < 1.05:
                    t = 0.12
            elif pattern == "lacquer":
                t = 0.42 + 0.18 * base + (0.35 if 6 <= y <= 8 else 0.0)
            elif pattern == "paper":
                t = 0.55 + 0.2 * base + (0.15 if rnd.random() < 0.04 else 0.0)
            elif pattern == "straw":
                t = 0.4 + 0.35 * math.sin(x * 2.1 + n.smooth(x, y / 6.0) * 3.0) * 0.5 + 0.25 * base
            elif pattern == "tiles":
                tile = 0.25 if y % 6 == 0 or (x + (y // 6) * 3) % 8 == 0 else 0.0
                t = 0.55 + 0.2 * base - tile
            else:
                t = 0.5 + 0.3 * base
            c = ramp(core, body, rim, clamp01(t))
            img.set(x, y, (c[0], c[1], c[2], max(0, min(255, a))))
    # pattern overlays
    if pattern == "ice":
        pts = [(rnd.randrange(S), rnd.randrange(S)) for _ in range(9)]
        for y in range(S):
            for x in range(S):
                ds = sorted((math.hypot(x - px, y - py), i) for i, (px, py) in enumerate(pts))
                if ds[1][0] - ds[0][0] < 1.2:
                    img.set(x, y, core + (min(255, alpha + 60),))
                else:
                    r, g, b, a = img.get(x, y)
                    k = 0.85 + 0.3 * (ds[0][1] % 3) / 3
                    img.set(x, y, (r * k, g * k, b * k, a))
    if pattern == "stone":
        for _ in range(3):
            x, y = rnd.randrange(S), rnd.randrange(S)
            for _ in range(18):
                img.set(x, y, rim + (alpha,))
                x += rnd.choice((-1, 0, 1))
                y += rnd.choice((0, 1))
    if pattern in ("energy", "blood"):
        for _ in range(4 if pattern == "energy" else 3):
            x, y = rnd.randrange(S), 0 if pattern == "blood" else rnd.randrange(S)
            for _ in range(28):
                img.set(x, y, (core if pattern == "energy" else rim) + (alpha,))
                if pattern == "energy":
                    x += rnd.choice((-1, 1))
                    y += rnd.choice((-1, 0, 1, 1))
                else:
                    y += 1
                    if rnd.random() < 0.2:
                        x += rnd.choice((-1, 1))
    if pattern == "energy" and extra == "stars":
        for _ in range(14):
            x, y = rnd.randrange(S), rnd.randrange(S)
            img.set(x, y, (255, 255, 255, 255))
    if extra == "patina":
        for _ in range(26):
            x, y = rnd.randrange(S), rnd.randrange(S)
            img.blend(x, y, (90, 150, 120, 150))
    if extra == "stripes":
        for y in range(S):
            for x in range(S):
                w = math.sin((x * 0.55 + y * 0.12) + n.smooth(x / 4.0, y / 4.0) * 2.5)
                if w > 0.72:
                    img.set(x, y, (20, 18, 22, 255))
    if extra == "bee":
        for y in range(S):
            if (y // 4) % 2 == 0:
                for x in range(S):
                    img.set(x, y, (30, 24, 16, 255))
    smooth_edges = pattern not in ("flame", "smoke", "cloud")
    put(name, finish(img, core, body, rim, alpha, edge=smooth_edges, bevel=smooth_edges))


def build_materials():
    m = material
    m("mat_gold", "FFF2B0", "F2B33D", "8A5A12", "gold", seed=2)
    m("mat_jade", "D8FFEA", "5FD6A0", "1F6E52", "jade", alpha=210, seed=3)
    m("mat_bronze", "F0C080", "A86A32", "4A2A10", "metal", seed=4, extra="patina")
    m("mat_iron_black", "6A6A78", "2A2A33", "0E0E14", "metal", seed=5)
    m("mat_silver", "FFFFFF", "C8D0DC", "6A7484", "metal", seed=6)
    m("mat_stone", "C8C0B0", "7D7566", "3E3A33", "stone", seed=7)
    m("mat_white_stone", "FFFFFF", "E8E4DA", "A8A090", "stone", seed=8)
    m("mat_ice", "F8FFFF", "9FF3FF", "3FA6C8", "ice", alpha=175, seed=9)
    m("mat_ice_solid", "F8FFFF", "BFF6FF", "4FA6D0", "ice", alpha=235, seed=10)
    m("mat_flame", "FFF6C0", "FF9A2A", "C2280A", "flame", alpha=235, seed=11)
    m("mat_flame_blue", "E8FFFF", "5AB8FF", "1A3AB0", "flame", alpha=230, seed=12)
    m("mat_flame_white", "FFFFFF", "FFF0B0", "E0A040", "flame", alpha=230, seed=13)
    m("mat_flame_violet", "F8E8FF", "B060FF", "4A108A", "flame", alpha=230, seed=14)
    m("mat_flame_green", "F0FFE0", "6CFF6A", "1A8A2A", "flame", alpha=230, seed=15)
    m("mat_spirit_blade", "FFFFFF", "D8E6FF", "7C98D8", "blade", alpha=165, seed=16)
    m("mat_blade_steel", "FFFFFF", "D0D8E4", "5A6478", "blade", seed=17)
    m("mat_thunder", "FFFFFF", "C7A2FF", "5B2DB0", "energy", seed=18)
    m("mat_space", "E8DCFF", "5A3FD0", "14082E", "energy", seed=19, extra="stars")
    m("mat_soul_blue", "E0F8FF", "4AA8FF", "0A3A8A", "energy", alpha=220, seed=20)
    m("mat_blood", "FFA0A0", "C0182A", "4A0008", "blood", seed=21)
    m("mat_wood", "B8844A", "6E4A26", "3A2510", "wood", seed=22)
    m("mat_charred", "6A5A48", "2A2018", "0A0806", "wood", seed=23)
    m("mat_leaf", "B8FFA0", "3FAE3A", "1B5A1E", "leaf", seed=24)
    m("mat_leaf_gold", "FFF8C0", "E8C040", "8A6A10", "leaf", seed=25)
    m("mat_blue_silver", "F0F8FF", "7AA8E8", "2A4A8A", "leaf", alpha=235, seed=26)
    m("mat_cloth_yellow", "FFF6A0", "E0B830", "7A5A10", "cloth", seed=27)
    m("mat_cloth_black", "5A5266", "24202C", "0A080E", "cloth", seed=28)
    m("mat_cloth_red", "FF9A8A", "C02A20", "5A0A08", "cloth", seed=29)
    m("mat_cloth_white", "FFFFFF", "E8E8F0", "9A9AA8", "cloth", seed=30)
    m("mat_porcelain", "FFFFFF", "EEF2F8", "AAB6C2", "porcelain", seed=31)
    m("mat_lotus_pink", "FFF4F8", "FF9EC8", "C8508A", "lotus", seed=32)
    m("mat_lotus_white", "FFFFFF", "F4F0FF", "C8B8E8", "lotus", seed=33)
    m("mat_feather_fire", "FFF6B0", "FF7A1A", "A0200A", "feather", seed=34)
    m("mat_feather_ice", "FFFFFF", "BFF6FF", "5AB0E0", "feather", alpha=225, seed=35)
    m("mat_feather_white", "FFFFFF", "E8ECF4", "8A94A8", "feather", seed=36)
    m("mat_feather_brown", "E8C8A0", "8A5A30", "3A2410", "feather", seed=37)
    m("mat_feather_thunder", "FFFFFF", "C7A2FF", "4A2090", "feather", alpha=230, seed=38)
    m("mat_feather_gold", "FFFAD0", "F0C040", "8A5A10", "feather", seed=39)
    m("mat_feather_storm", "F0FFFF", "8AE0E8", "2A6A8A", "feather", alpha=230, seed=40)
    m("mat_feather_kun", "E8F0FF", "3A5A9A", "0E1A3A", "feather", seed=41)
    m("mat_shadow", "9A8AC0", "4A3A6A", "120A20", "smoke", alpha=185, seed=42)
    m("mat_cloud", "FFFFFF", "F2F4FA", "C2CAD6", "cloud", alpha=230, seed=43)
    m("mat_hair_gold", "FFF6B0", "F0B820", "9A6A08", "hair", seed=44)
    m("mat_fur_white", "FFFFFF", "E8E8EE", "9A9AA6", "fur", seed=45)
    m("mat_fur_white_striped", "FFFFFF", "E8E8EE", "9A9AA6", "fur", seed=46, extra="stripes")
    m("mat_fur_gold", "FFE88A", "D8A030", "7A4A10", "fur", seed=47)
    m("mat_fur_orange_striped", "FFD0A0", "E07820", "7A3A08", "fur", seed=48, extra="stripes")
    m("mat_fur_grey", "E0E8F8", "8A96B0", "3A4258", "fur", seed=49)
    m("mat_lacquer_red", "FF9A7A", "B0201A", "4A0806", "lacquer", seed=50)
    m("mat_gourd_yellow", "F8F8B0", "C0BC40", "5A5A10", "lacquer", seed=51)
    m("mat_purple_gold", "F0D8FF", "8A3AC8", "3A0A5A", "lacquer", seed=52)
    m("mat_black_glaze", "7A7A8A", "1A1A22", "050508", "lacquer", seed=53)
    m("mat_paper", "FFFAE0", "F0D890", "A08A40", "paper", seed=54)
    m("mat_straw", "F8E8A8", "C8A850", "6A5420", "straw", seed=55)
    m("mat_bone", "FFF8EA", "E0D4BC", "8A7C60", "stone", seed=56)
    m("mat_dragon_red", "FFB080", "C8281A", "4A0806", "scale", seed=57)
    m("mat_dragon_purple", "E8C8FF", "7A3AC8", "2A0A4A", "scale", seed=58)
    m("mat_dragon_gold", "FFF0A0", "E0A830", "6A4008", "scale", seed=59)
    m("mat_dragon_green", "C8FFB0", "3A9A4A", "0E3A18", "scale", seed=60)
    m("mat_dragon_lavender", "F4E8FF", "B898E8", "5A3A8A", "scale", seed=61)
    m("mat_koi", "FFF0E0", "FF7A2A", "A03A0A", "scale", seed=62)
    m("mat_bee", "FFF080", "E8C020", "6A4A08", "fur", seed=63, extra="bee")
    m("mat_glass_orange", "FFF0C0", "FF8A20", "8A2A00", "energy", alpha=200, seed=64)
    m("mat_void", "3A3448", "08060C", "000000", "metal", seed=65)
    m("mat_drum_skin", "FFD8C0", "D03A2A", "6A0A08", "cloth", seed=66)
    m("mat_tiles_gold", "FFF0A0", "E0A830", "6A4008", "tiles", seed=67)
    m("mat_tiles_green", "C8FFD0", "3A9A6A", "0E3A28", "tiles", seed=68)
    m("mat_moon", "FFFDF0", "F4E6A8", "A08A40", "porcelain", seed=69)
    m("mat_sun", "FFFFFF", "FFD54A", "FF6A10", "flame", alpha=255, seed=70)
    m("mat_star", "FFFFFF", "E8F0FF", "6A8AD8", "blade", seed=71)
    m("mat_glow_white", "FFFFFF", "FFFFFF", "D8E0FF", "porcelain", seed=72)
    m("mat_glow_gold", "FFFFFF", "FFE88A", "F0B030", "porcelain", seed=73)
    m("mat_rope_gold", "FFF4B0", "E8B840", "7A5410", "straw", seed=74)
    # eight Dao energies (qi palms, glyph rings, fists): translucent, glowing
    for dao, (c, b, r) in DAO_RAMP.items():
        m("mat_qi_" + dao, c, b, r, "energy", alpha=170, seed=80 + len(dao))
    # six paths of reincarnation (phantom fists, wheel segments)
    for path, (c, b, r) in SIX_PATHS.items():
        m("mat_path_" + path, c, b, r, "energy", alpha=200, seed=100 + len(path))


DAO_RAMP = {
    "sword": ("FFFFFF", "D8E6FF", "7C98D8"),
    "fire": ("FFF6C0", "FF6A2A", "C2280A"),
    "ice": ("FFFFFF", "9FF3FF", "3FA6C8"),
    "lightning": ("FFFFFF", "C7A2FF", "5B2DB0"),
    "body": ("FFF0C8", "D9A05B", "7A4A1A"),
    "life": ("F0FFE8", "6CFF8A", "2E8A3A"),
    "shadow": ("E8E0FF", "7A6AA0", "2A1A40"),
    "space": ("E8E0FF", "5A3FD0", "14082E"),
    "gold": ("FFFFFF", "FFD54A", "A8781A"),
}

SIX_PATHS = {
    "deva": ("FFFFFF", "FFE88A", "C89A20"),
    "human": ("FFFFFF", "F0E8D8", "9A8A70"),
    "asura": ("FFE0D0", "FF4A2A", "8A1008"),
    "animal": ("F0FFE0", "7AD05A", "2A6A1A"),
    "ghost": ("F4E8FF", "A070E8", "3A1A6A"),
    "hell": ("D8D0E8", "4A3A6A", "100818"),
}


# ================================================================================================ unique sheets

def line(img, x0, y0, x1, y1, color, width=2.2):
    """Antialiased thick line in pixel coordinates."""
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    r = width / 2.0
    cover = {}
    for i in range(steps + 1):
        t = i / steps
        cx, cy = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r + 0.5 and 0 <= x < img.width and 0 <= y < img.height:
                    cover[(x, y)] = max(cover.get((x, y), 0.0), clamp01(r + 0.5 - d))
    # one blend per pixel, so overlapping samples of the same stroke do not darken it twice
    for (x, y), a in cover.items():
        img.blend(x, y, (color[0], color[1], color[2], int(color[3] * a)))


def disc(img, cx, cy, r, color):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r + 0.5:
                a = clamp01(r + 0.5 - d)
                img.blend(x, y, (color[0], color[1], color[2], int(color[3] * a)))


# Glyph strokes on a 16 x 16 design grid (x right, y down).
GLYPHS = {
    "di": [(8, 0.8, 8, 2.2), (3, 3, 13, 3), (5.5, 4, 6.5, 5.5), (10.5, 4, 9.5, 5.5), (2, 6.2, 14, 6.2), (2.5, 6.2, 2.5, 8.2),
           (13.5, 6.2, 13.5, 8.2), (4, 9.5, 12, 9.5), (4, 9.5, 4, 13.5), (12, 9.5, 12, 13.5), (12, 13.5, 11, 13), (8, 7, 8, 15.3)],
    "feng": [(1.8, 3, 6.4, 3), (4.1, 1, 4.1, 6.2), (1.3, 6.2, 6.8, 6.2), (1.8, 9.2, 6.4, 9.2), (4.1, 7.2, 4.1, 13.3),
             (1, 13.4, 7.2, 13.4), (8.2, 5.2, 15, 5.2), (12.6, 1, 12.6, 14.6), (12.6, 14.6, 11, 13.8), (9.6, 8, 10.6, 10.2)],
    "lei": [(2, 1.2, 14, 1.2), (8, 1.2, 8, 6.4), (2.2, 3, 2.2, 6.2), (2.2, 3, 13.8, 3), (13.8, 3, 13.8, 6.2), (4.4, 4.2, 5.8, 4.6),
            (4.4, 5.6, 5.8, 6), (10.2, 4.2, 11.6, 4.6), (10.2, 5.6, 11.6, 6), (3, 8, 13, 8), (3, 8, 3, 15), (13, 8, 13, 15),
            (3, 15, 13, 15), (8, 8, 8, 15), (3, 11.5, 13, 11.5)],
    "ling": [(8, 1, 1.8, 6.4), (8, 1, 14.2, 6.4), (7.6, 5, 8.6, 6.6), (4, 8.6, 11.4, 8.6), (11.4, 8.6, 11.4, 11.8), (11.4, 11.8, 9.8, 11.2),
             (7, 8.6, 7, 15.4)],
    "jin": [(1, 3, 7, 3), (4, 1, 4, 8.4), (4, 3.4, 1.4, 7.2), (4, 3.4, 6.6, 6.2), (9, 3, 15, 3), (12, 1, 12, 8.4), (12, 3.4, 9.4, 7.2),
            (12, 3.4, 14.6, 7.2), (4, 9.4, 12, 9.4), (2, 11.4, 14, 11.4), (8, 11.4, 8, 15.4), (5, 12.8, 3.4, 14.8), (11, 12.8, 12.6, 14.8)],
    "wan": [(8, 2, 8, 14), (2, 8, 14, 8), (8, 2, 3, 2), (2, 8, 2, 13), (8, 14, 13, 14), (14, 8, 14, 3)],
    "tian": [(3, 2.6, 13, 2.6), (1.4, 7.2, 14.6, 7.2), (8, 2.6, 8, 7.2), (8, 7.2, 2, 15), (8, 7.2, 14, 15)],
    "xian": [(5.4, 1, 2, 7), (4, 4.4, 4, 15.2), (11, 2, 11, 14), (7, 7, 7, 14), (7, 14, 15, 14), (15, 14, 15, 7)],
}


def glyph_plate(name, glyph, paper, ink, frame, glow=False, alpha=255):
    img = Image(S, S, (0, 0, 0, 0))
    p, k, f = rgb(paper), rgb(ink), rgb(frame)
    n = Noise(len(name) * 31)
    for y in range(S):
        for x in range(S):
            t = 0.45 + 0.25 * n.fbm(x / 6.0, y / 6.0, 3)
            c = mix(p, (255, 255, 255), t * 0.3)
            img.set(x, y, (c[0], c[1], c[2], alpha))
    for i in range(S):
        for j in (0, 1, S - 2, S - 1):
            img.set(i, j, f + (255,))
            img.set(j, i, f + (255,))
    for (x0, y0, x1, y1) in GLYPHS[glyph]:
        sc = (S - 6) / 16.0
        line(img, 3 + x0 * sc, 3 + y0 * sc, 3 + x1 * sc, 3 + y1 * sc, k + (255,), 2.3)
    put(name, img)


def build_sheets():
    # taiji: yin-yang disc on a 64 sheet
    img = Image(64, 64, (0, 0, 0, 0))
    R = 31.0
    for y in range(64):
        for x in range(64):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            d = math.hypot(dx, dy)
            if d > R + 0.5:
                continue
            a = int(255 * clamp01(R + 0.5 - d))
            white = dx < 0
            if math.hypot(dx, dy + R / 2) < R / 2:
                white = True
            if math.hypot(dx, dy - R / 2) < R / 2:
                white = False
            if math.hypot(dx, dy + R / 2) < R / 7:
                white = False
            if math.hypot(dx, dy - R / 2) < R / 7:
                white = True
            c = (248, 246, 236) if white else (22, 20, 28)
            if d > R - 2:
                c = (232, 190, 80)
            img.set(x, y, c + (a,))
    put("taiji_64", img)

    # hex disc: grey yin-yang with violet ring
    img2 = Image(S, S, (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            src = img.get(x * 2, y * 2)
            if src[3]:
                g = (src[0] + src[1] + src[2]) / 3
                c = mix((70, 50, 90), (200, 190, 220), g / 255.0)
                img2.set(x, y, (c[0], c[1], c[2], src[3]))
    put("hex_face", img2)

    # clock face: gold disc, twelve marks, inner ring
    img = Image(S, S, (0, 0, 0, 0))
    gold, deep = rgb("F2C24A"), rgb("6A4008")
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if d <= 15.5:
                c = mix(rgb("FFF4C8"), rgb("E8C878"), d / 15.5)
                if 13.5 < d <= 15.5:
                    c = gold
                if 8.5 < d < 9.5:
                    c = deep
                img.set(x, y, c + (255,))
    for i in range(12):
        a = math.tau * i / 12
        line(img, 16 + math.cos(a) * 10.5, 16 + math.sin(a) * 10.5, 16 + math.cos(a) * 13, 16 + math.sin(a) * 13, deep + (255,), 1.6)
    put("clock_face", img)

    # bagua: eight trigrams round an octagon on bronze
    img = Image(S, S, (0, 0, 0, 0))
    bronze = rgb("C08A40")
    for y in range(S):
        for x in range(S):
            img.set(x, y, mix(bronze, rgb("6A3A10"), Noise(5).fbm(x / 5.0, y / 5.0) * 0.6) + (255,))
    trig = ["111", "011", "101", "001", "110", "010", "100", "000"]
    for i, t in enumerate(trig):
        a = math.tau * i / 8 - math.pi / 2
        cx, cy = 16 + math.cos(a) * 11, 16 + math.sin(a) * 11
        tx, ty = -math.sin(a), math.cos(a)
        for j, bit in enumerate(t):
            ox, oy = cx - math.cos(a) * (j - 1) * 1.8, cy - math.sin(a) * (j - 1) * 1.8
            if bit == "1":
                line(img, ox - tx * 2.4, oy - ty * 2.4, ox + tx * 2.4, oy + ty * 2.4, (30, 18, 8, 255), 1.1)
            else:
                line(img, ox - tx * 2.4, oy - ty * 2.4, ox - tx * 0.6, oy - ty * 0.6, (30, 18, 8, 255), 1.1)
                line(img, ox + tx * 0.6, oy + ty * 0.6, ox + tx * 2.4, oy + ty * 2.4, (30, 18, 8, 255), 1.1)
    disc(img, 16, 16, 4, (255, 210, 120, 255))
    put("bagua_face", img)

    # glyph plates
    glyph_plate("glyph_di", "di", "B8FFD8", "1F4E3A", "E8B840")
    glyph_plate("glyph_feng", "feng", "FFE0D8", "C0182A", "C0182A")
    glyph_plate("glyph_lei", "lei", "E8DCFF", "FFFFFF", "7A4AD8")
    glyph_plate("glyph_ling", "ling", "FFF0A0", "C0182A", "C08A20")
    glyph_plate("glyph_jin", "jin", "E8D8FF", "6A2AB0", "A060FF")
    glyph_plate("glyph_wan", "wan", "FFE88A", "FFFFFF", "F0B020")
    glyph_plate("glyph_tian", "tian", "F0C080", "4A2A10", "E8B840")
    glyph_plate("glyph_xian", "xian", "FFFFFF", "C89A20", "E8D080")

    # glowing glyph faces (thunder and space symbols): the glyph bright on a dark field
    for name, glyph, field, ink in (("rune_lei_glow", "lei", "2A1060", "F4E8FF"), ("rune_space_glow", "tian", "140830", "C8B8FF")):
        img = Image(S, S, rgb(field) + (220,))
        for (x0, y0, x1, y1) in GLYPHS[glyph]:
            sc = (S - 6) / 16.0
            line(img, 3 + x0 * sc, 3 + y0 * sc, 3 + x1 * sc, 3 + y1 * sc, rgb(ink) + (255,), 2.4)
        put(name, img)

    # seal-script face (a square spiral maze) for the Fantian and Mountain-Sea seals
    img = Image(S, S, rgb("C08A40") + (255,))
    ink = (70, 30, 10, 255)
    x0, y0, x1, y1 = 3, 3, 28, 28
    while x1 - x0 > 4:
        line(img, x0, y0, x1, y0, ink, 1.6)
        line(img, x1, y0, x1, y1, ink, 1.6)
        line(img, x1, y1, x0 + 3, y1, ink, 1.6)
        line(img, x0, y1, x0, y0 + 3, ink, 1.6)
        x0, y0, x1, y1 = x0 + 3, y0 + 3, x1 - 3, y1 - 3
    put("seal_face", img)

    # void mirror face: a black glass with a violet swirl and a bright heart
    img = Image(S, S, (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - 16, y + 0.5 - 16
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            sw = 0.5 + 0.5 * math.sin(ang * 3 + d * 0.6)
            c = mix(rgb("05040C"), rgb("7A4AFF"), sw * clamp01(1 - d / 16) * 0.9)
            c = mix(c, (255, 255, 255), clamp01(1 - d / 3.5))
            img.set(x, y, c + (245,))
    put("mirror_void_face", img)

    # rune band: bronze with glowing cyan-gold rune marks
    img = Image(S, S, (0, 0, 0, 0))
    rnd = random.Random(77)
    for y in range(S):
        for x in range(S):
            img.set(x, y, mix(rgb("8A5A22"), rgb("4A2A10"), Noise(9).fbm(x / 4.0, y / 4.0)) + (255,))
    for i in range(6):
        cx = 3 + i * 5
        for _ in range(3):
            xa, ya = cx + rnd.uniform(-1.5, 1.5), rnd.uniform(6, 26)
            line(img, xa, ya, xa + rnd.uniform(-2, 2), ya + rnd.uniform(-4, 4), (255, 226, 140, 255), 1.3)
    put("rune_band", img)

    # creature faces: fur with two glowing eyes and a dark muzzle
    def face(name, fur, eye, muzzle, stripes=False):
        base = TEXTURES[fur]
        img = Image(S, S)
        for y in range(S):
            for x in range(S):
                img.set(x, y, base.get(x, y))
        if stripes:
            for k in (6, 10, 22, 26):
                line(img, k, 2, 16 + (k - 16) * 0.3, 9, (20, 18, 22, 255), 1.6)
        disc(img, 10, 13, 2.6, rgb(eye) + (255,))
        disc(img, 22, 13, 2.6, rgb(eye) + (255,))
        disc(img, 10, 13, 1.0, (255, 255, 255, 255))
        disc(img, 22, 13, 1.0, (255, 255, 255, 255))
        disc(img, 16, 22, 3.0, rgb(muzzle) + (255,))
        line(img, 12, 27, 20, 27, (40, 20, 20, 255), 1.4)
        put(name, img)

    face("face_white_tiger", "mat_fur_white_striped", "B060FF", "3A3040", True)
    face("face_orange_tiger", "mat_fur_orange_striped", "FFE040", "3A2010", True)
    face("face_wolf", "mat_fur_grey", "8AF0FF", "2A2A38")
    face("face_ape", "mat_fur_gold", "FF5A2A", "8A4A20")
    face("face_qilin", "mat_jade", "FFE040", "1F6E52")
    face("face_dragon_red", "mat_dragon_red", "FFE040", "4A0806")
    face("face_dragon_purple", "mat_dragon_purple", "FFE8FF", "2A0A4A")
    face("face_dragon_gold", "mat_dragon_gold", "FF4A2A", "6A4008")

    # butterfly wing (silver, see-through veins)
    img = Image(S, S, (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            d = math.hypot((x - 4) / 28.0, (y - 16) / 15.0)
            if d < 1.0:
                c = mix(rgb("FFFFFF"), rgb("A8B8E0"), d)
                a = 200 if ((x + y) % 7) else 255
                img.set(x, y, c + (a,))
    for k in range(5):
        line(img, 4, 16, 30, 4 + k * 6, (90, 100, 140, 255), 1.0)
    put("butterfly_wing", img)

    # beetle shell (gold, a centre seam and spots)
    img = Image(S, S)
    for y in range(S):
        for x in range(S):
            img.set(x, y, TEXTURES["mat_gold"].get(x, y))
    line(img, 16, 0, 16, 31, (90, 60, 10, 255), 1.4)
    for (sx, sy) in ((9, 9), (23, 9), (9, 22), (23, 22)):
        disc(img, sx, sy, 2.2, (120, 70, 10, 255))
    put("beetle_shell", img)

    # six-path wheel segments sheet is the six path materials; the hub uses gold


# ================================================================================================ model DSL

SIDES = ("north", "south", "east", "west", "up", "down")
MODELS = {}       # id -> model json dict
MODEL_TEX = {}    # id -> set of texture names


class Model:
    def __init__(self, mid):
        self.id = mid
        self.els = []
        self.tex = {}
        self.k = 0

    def key(self, texname):
        if texname not in self.tex:
            self.tex[texname] = "t%d" % len(self.tex)
        return self.tex[texname]

    def box(self, f, t, tex, rot=None, glow=0, faces=None, full=(), skip=()):
        f = [round(min(f[i], t[i]), 3) for i in range(3)]
        t2 = [round(max(f[i], t[i]), 3) for i in range(3)]
        el = {"from": f, "to": t2, "faces": {}}
        dims = [t2[i] - f[i] for i in range(3)]
        for side in SIDES:
            if side in skip:
                continue
            texname = (faces or {}).get(side, tex)
            if side in ("north", "south"):
                w, h = dims[0], dims[1]
            elif side in ("east", "west"):
                w, h = dims[2], dims[1]
            else:
                w, h = dims[0], dims[2]
            if side in full or texname in FULL_SHEETS:
                uv = [0, 0, 16, 16]
            else:
                su, sv = max(0.5, min(16.0, w)), max(0.5, min(16.0, h))
                seed = (len(self.els) * 7 + SIDES.index(side) * 3) % 13
                ou = (seed * 1.37) % max(0.001, 16.0 - su)
                ov = (seed * 2.11) % max(0.001, 16.0 - sv)
                uv = [round(ou, 2), round(ov, 2), round(ou + su, 2), round(ov + sv, 2)]
            el["faces"][side] = {"uv": uv, "texture": "#" + self.key(texname)}
        if rot:
            r = dict(rot)
            if "origin" not in r:
                r["origin"] = [round((f[i] + t2[i]) / 2.0, 3) for i in range(3)]
            el["rotation"] = {"origin": [round(v, 3) for v in r["origin"]], "x": round(r.get("x", 0.0), 3),
                              "y": round(r.get("y", 0.0), 3), "z": round(r.get("z", 0.0), 3)}
        if glow:
            el["light_emission"] = int(glow)
        self.els.append(el)
        return self

    # centred box
    def cbox(self, c, size, tex, **kw):
        return self.box([c[i] - size[i] / 2.0 for i in range(3)], [c[i] + size[i] / 2.0 for i in range(3)], tex, **kw)


FULL_SHEETS = set()


def model(mid):
    m = Model(mid)
    MODELS[mid] = m
    return m


# ---------------------------------------------------------------------------------------------- shape helpers

def oct_prism(m, c, r, h, axis, tex, glow=0, rot45=True):
    """Octagonal prism (2 boxes) centred at c, apothem r, length h along axis."""
    if axis == "y":
        size = (2 * r, h, 2 * r)
    elif axis == "x":
        size = (h, 2 * r, 2 * r)
    else:
        size = (2 * r, 2 * r, h)
    m.cbox(c, size, tex, glow=glow)
    if rot45:
        # the turned copy is a hair shorter, so the two caps are never coplanar (no z-fighting)
        k = {"x": 0, "y": 1, "z": 2}[axis]
        short = list(size)
        short[k] = size[k] - min(0.12, size[k] * 0.02)
        m.cbox(c, tuple(short), tex, glow=glow, rot={axis: 45.0, "origin": list(c)})


def sphere(m, c, r, tex, glow=0, boxes=3):
    s = 2 * r * 0.82
    m.cbox(c, (s, s, s), tex, glow=glow)
    if boxes >= 2:
        m.cbox(c, (s, s * 0.9, s), tex, glow=glow, rot={"y": 45.0, "origin": list(c)})
    if boxes >= 3:
        m.cbox(c, (s * 0.9, s, s), tex, glow=glow, rot={"x": 45.0, "origin": list(c)})


def ring(m, c, R, n, thick, width, axis, tex, glow=0, phase=0.0, arc=360.0, length_scale=1.12):
    """n segments on a circle of radius R around c in the plane normal to axis (y: XZ, z: XY, x: YZ)."""
    L0 = 2 * math.pi * R * (arc / 360.0) / n * length_scale
    thick0, width0 = thick, width
    for i in range(n):
        # neighbours overlap at their ends: every other one is a hair thinner so no faces are coplanar
        k = 0.96 if i % 2 else 1.0
        L, thick, width = L0, thick0 * k, width0 * k
        th = math.radians(phase + arc * (i + 0.5) / n) if arc < 360 else math.radians(phase + 360.0 * i / n)
        if axis == "y":
            p = (c[0] + R * math.cos(th), c[1], c[2] + R * math.sin(th))
            m.cbox(p, (L, width, thick), tex, glow=glow, rot={"y": -(math.degrees(th) + 90.0), "origin": list(p)})
        elif axis == "z":
            p = (c[0] + R * math.cos(th), c[1] + R * math.sin(th), c[2])
            m.cbox(p, (L, thick, width), tex, glow=glow, rot={"z": math.degrees(th) + 90.0, "origin": list(p)})
        else:
            p = (c[0], c[1] + R * math.cos(th), c[2] + R * math.sin(th))
            m.cbox(p, (width, thick, L), tex, glow=glow, rot={"x": math.degrees(th), "origin": list(p)})


def taper(m, c, y0, y1, w0, w1, d0, d1, steps, tex, glow=0):
    """Stacked boxes from y0 (w0 x d0) to y1 (w1 x d1)."""
    for i in range(steps):
        a, b = y0 + (y1 - y0) * i / steps, y0 + (y1 - y0) * (i + 1) / steps
        t = (i + 0.5) / steps
        w, d = w0 + (w1 - w0) * t, d0 + (d1 - d0) * t
        m.box([c[0] - w / 2, a, c[2] - d / 2], [c[0] + w / 2, b, c[2] + d / 2], tex, glow=glow)


def petal(m, base, length, width, thick, heading, tilt, tex, glow=0, curl=12.0, pieces=3):
    """A curved petal plate rising from base: heading (deg about y), tilt (deg from vertical towards outside)."""
    seg = length / pieces
    for i in range(pieces):
        w = width * (1.0 - 0.28 * i)
        y0 = base[1] + seg * i
        tl = tilt + curl * i
        m.box([base[0] - w / 2, y0, base[2] - thick / 2], [base[0] + w / 2, y0 + seg * 1.08, base[2] + thick / 2], tex, glow=glow,
              rot={"x": tl, "y": heading, "origin": [base[0], base[1], base[2]]})


# ================================================================================================ the models

def blade(m, w0, w1, y0, y1, tex, thick=0.7, steps=3, glow=0):
    c = (8, 0, 8)
    for i in range(steps):
        a = y0 + (y1 - y0) * i / steps
        b = y0 + (y1 - y0) * (i + 1) / steps
        w = w0 + (w1 - w0) * (i + 0.5) / steps
        m.box([c[0] - w / 2, a, c[2] - thick / 2], [c[0] + w / 2, b + 0.01, c[2] + thick / 2], tex, glow=glow)
    tip = max(0.6, w1 * 0.72)
    m.cbox((8, y1, 8), (tip, tip, thick * 0.9), tex, glow=glow, rot={"z": 45.0, "origin": [8, y1, 8]})


def sword(mid, blade_tex, guard_tex, grip_tex="mat_cloth_black", w0=3.0, w1=1.6, length=30.5, guard_w=8.0, edge=None, ridge=None,
          serrate=False, star_guard=False, coil=None, runes=None, gem="mat_jade", thick=0.7, tassel=False):
    m = model(mid)
    # pommel and grip
    sphere(m, (8, 3.6, 8), 1.2, guard_tex, boxes=2)
    m.box([7.35, 4.6, 7.35], [8.65, 10.4, 8.65], grip_tex)
    for y in (6.0, 8.6):
        m.box([7.15, y, 7.15], [8.85, y + 0.5, 8.85], guard_tex)
    # guard
    if star_guard:
        m.cbox((8, 11.1, 8), (5.0, 5.0, 1.2), guard_tex, glow=12, rot={"z": 45.0, "origin": [8, 11.1, 8]})
        m.cbox((8, 11.1, 8), (6.4, 1.6, 1.4), guard_tex, glow=12)
    else:
        m.box([8 - guard_w / 2, 10.4, 7.1], [8 + guard_w / 2, 11.8, 8.9], guard_tex)
        m.cbox((8 - guard_w / 2 - 0.7, 11.6, 8), (1.6, 1.0, 1.6), guard_tex, rot={"z": 25.0, "origin": [8 - guard_w / 2, 11.1, 8]})
        m.cbox((8 + guard_w / 2 + 0.7, 11.6, 8), (1.6, 1.0, 1.6), guard_tex, rot={"z": -25.0, "origin": [8 + guard_w / 2, 11.1, 8]})
        m.box([7.3, 10.1, 6.7], [8.7, 12.1, 9.3], gem, glow=10)
    if coil:
        ring(m, (8, 9.0, 8), 1.9, 6, 0.8, 1.0, "y", coil, glow=6)
        m.cbox((8, 12.6, 6.4), (1.6, 1.4, 1.6), coil, glow=8)
    # blade
    blade(m, w0, w1, 11.8, length, blade_tex, thick=thick)
    if ridge:
        m.box([7.8, 12.2, 8 - thick / 2 - 0.12], [8.2, length - 2.2, 8 + thick / 2 + 0.12], ridge, glow=15)
    if edge:
        m.box([8 - w0 / 2 - 0.25, 12.0, 7.85], [8 - w0 / 2 + 0.05, length - 6, 8.15], edge, glow=15)
        m.box([8 + w0 / 2 - 0.05, 12.0, 7.85], [8 + w0 / 2 + 0.25, length - 6, 8.15], edge, glow=15)
    if serrate:
        for i, y in enumerate((14.5, 18.0, 21.5, 25.0)):
            sx = 8 - w0 / 2 - 0.2 if i % 2 == 0 else 8 + w0 / 2 + 0.2
            m.cbox((sx, y, 8), (1.4, 1.4, thick * 0.8), blade_tex, rot={"z": 45.0, "origin": [sx, y, 8]})
    if runes:
        for y in (15.0, 19.5, 24.0):
            m.box([7.45, y, 8 - thick / 2 - 0.15], [8.55, y + 1.6, 8 + thick / 2 + 0.15], runes, glow=15)
    if tassel:
        m.box([7.8, 0.6, 7.8], [8.2, 3.0, 8.2], "mat_cloth_red")
        m.cbox((8, 0.4, 8), (1.0, 1.4, 1.0), "mat_cloth_red")
    return m


def build_weapons():
    sword("giant_sword", "mat_spirit_blade", "mat_gold", ridge="mat_glow_white", edge="mat_qi_sword", w0=3.2, w1=1.8)
    s = model("light_sword")
    s.box([7.6, 5.4, 7.6], [8.4, 10.2, 8.4], "mat_cloth_white")
    s.box([6.2, 10.2, 7.5], [9.8, 11.0, 8.5], "mat_silver", glow=8)
    blade(s, 1.3, 0.9, 11.0, 30.0, "mat_spirit_blade", thick=0.35, glow=12)
    s.box([7.9, 11.4, 7.78], [8.1, 27.0, 8.22], "mat_glow_white", glow=15)
    s.box([7.85, 1.8, 7.85], [8.15, 5.4, 8.15], "mat_cloth_red")
    s.cbox((8, 1.6, 8), (0.9, 1.2, 0.9), "mat_cloth_red")
    sword("bloodmoon_sword", "mat_blood", "mat_iron_black", grip_tex="mat_cloth_red", w0=3.4, w1=1.8, serrate=True, gem="mat_blood",
          ridge="mat_qi_fire")
    sword("star_sword", "mat_star", "mat_star", grip_tex="mat_cloth_white", w0=1.8, w1=1.0, length=29.0, star_guard=True,
          ridge="mat_glow_white", thick=0.5)
    # grass blade sword: curved leaf blade from four rotated boxes
    g = model("grass_blade_sword")
    g.box([7.5, 4.0, 7.5], [8.5, 10.4, 8.5], "mat_wood")
    g.cbox((8, 10.8, 8), (4.0, 0.8, 1.2), "mat_leaf")
    y, x, ang = 11.2, 8.0, 0.0
    for i, (w, h) in enumerate(((2.6, 5.2), (2.3, 5.0), (1.9, 4.6), (1.3, 4.0))):
        g.box([x - w / 2, y, 7.75], [x + w / 2, y + h, 8.25], "mat_leaf", glow=4 + i * 3, rot={"z": -ang, "origin": [x, y, 8]})
        x += math.sin(math.radians(ang)) * h
        y += math.cos(math.radians(ang)) * h * 0.98
        ang += 8.0
    g.cbox((x, y + 0.6, 8), (0.9, 1.6, 0.4), "mat_glow_white", glow=15, rot={"z": -ang, "origin": [x, y, 8]})
    # crystal blade: a hexagonal prism of ice
    c = model("crystal_blade")
    c.box([7.4, 4.0, 7.4], [8.6, 10.0, 8.6], "mat_silver")
    c.cbox((8, 10.5, 8), (5.0, 1.0, 1.6), "mat_ice_solid", glow=8)
    for a in (0, 60, 120):
        c.cbox((8, 20.0, 8), (2.4, 18.0, 1.4), "mat_ice", glow=10, rot={"y": a, "origin": [8, 20, 8]})
    c.cbox((8, 29.4, 8), (1.6, 1.6, 1.0), "mat_ice_solid", glow=14, rot={"z": 45.0, "origin": [8, 29.4, 8]})
    sword("heaven_punishing_sword", "mat_blade_steel", "mat_gold", w0=4.6, w1=2.6, guard_w=11.0, edge="mat_thunder",
          ridge="mat_thunder", runes="mat_glow_gold")
    for mid, col in (("zhuxian_sword", "mat_qi_fire"), ("luxian_sword", "mat_qi_life"), ("xianxian_sword", "mat_qi_ice"),
                     ("juexian_sword", "mat_qi_lightning")):
        sword(mid, "mat_blade_steel", "mat_iron_black", w0=3.0, w1=1.6, edge=col, ridge=col, coil=col, gem=col)
    sword("xuantian_sword", "mat_iron_black", "mat_gold", w0=4.8, w1=3.0, guard_w=11.5, runes="mat_glow_gold",
          edge="mat_glow_gold", thick=1.1)
    # natal sword: azure bamboo jian with node rings
    for mid, blade_tex, trim in (("natal_sword", "mat_qi_life", "mat_jade"), ("natal_sword_silver", "mat_silver", "mat_silver"),
                                 ("natal_sword_gold", "mat_gold", "mat_gold")):
        n = model(mid)
        n.box([7.5, 4.6, 7.5], [8.5, 10.2, 8.5], "mat_cloth_black")
        n.box([6.0, 10.2, 7.3], [10.0, 11.2, 8.7], trim, glow=4)
        for i in range(5):
            y0 = 11.2 + i * 3.8
            w = 1.9 - i * 0.18
            n.box([8 - w / 2, y0, 7.7], [8 + w / 2, y0 + 3.6, 8.3], blade_tex, glow=6)
            n.box([8 - w / 2 - 0.15, y0 + 3.4, 7.6], [8 + w / 2 + 0.15, y0 + 3.8, 8.4], trim, glow=8)
        n.cbox((8, 30.6, 8), (1.0, 1.0, 0.5), blade_tex, glow=10, rot={"z": 45.0, "origin": [8, 30.6, 8]})
        n.box([7.85, 1.6, 7.85], [8.15, 4.6, 8.15], "mat_cloth_red")
    # thunder sword: zig-zag edges
    t = sword("thunder_sword", "mat_thunder", "mat_iron_black", w0=2.6, w1=1.4, ridge="mat_glow_white", gem="mat_thunder")
    for i, y in enumerate((13.5, 16.5, 19.5, 22.5, 25.5)):
        sx = 6.4 if i % 2 == 0 else 9.6
        t.cbox((sx, y, 8), (2.0, 0.6, 0.5), "mat_thunder", glow=15, rot={"z": 35.0 if i % 2 == 0 else -35.0, "origin": [sx, y, 8]})
    # ice spear (points north)
    sp = model("ice_spear")
    oct_prism(sp, (8, 8, 14.0), 0.7, 14.0, "z", "mat_ice_solid", glow=6)
    oct_prism(sp, (8, 8, 4.0), 1.1, 6.0, "z", "mat_ice", glow=10)
    sp.cbox((8, 8, 0.4), (1.2, 1.2, 1.6), "mat_ice", glow=14, rot={"x": 45.0, "y": 45.0, "origin": [8, 8, 0.4]})
    ring(sp, (8, 8, 7.4), 1.6, 6, 0.6, 0.8, "z", "mat_ice_solid", glow=8)
    # needle (points north)
    nd = model("needle")
    nd.box([7.85, 7.85, 0.0], [8.15, 8.15, 15.0], "mat_silver", glow=12)
    nd.cbox((8, 8, 0.0), (0.4, 0.4, 0.6), "mat_glow_white", glow=15, rot={"x": 45, "origin": [8, 8, 0]})
    ring(nd, (8, 8, 15.4), 0.5, 4, 0.2, 0.3, "z", "mat_silver", glow=12)
    # phoenix arrow (points north)
    ar = model("phoenix_arrow")
    ar.box([7.7, 7.7, 2.0], [8.3, 8.3, 18.0], "mat_wood")
    ar.cbox((8, 8, 1.0), (1.6, 1.6, 2.6), "mat_flame", glow=15, rot={"z": 45.0, "origin": [8, 8, 1.0]})
    for a in (0, 120, 240):
        ar.cbox((8, 8 + 1.2, 16.0), (0.3, 2.2, 4.0), "mat_feather_fire", glow=12, rot={"z": a, "origin": [8, 8, 16]})
    # ruyi staff: long rod with gold caps (grip at the middle), red lacquer; and the gold cudgel variant
    for mid, rod in (("ruyi_staff", "mat_lacquer_red"), ("ruyi_staff_gold", "mat_gold")):
        r = model(mid)
        oct_prism(r, (8, 8, 8), 1.0, 40.0, "y", rod)
        oct_prism(r, (8, -10.5, 8), 1.5, 3.0, "y", "mat_gold", glow=8)
        oct_prism(r, (8, 26.5, 8), 1.5, 3.0, "y", "mat_gold", glow=8)
        r.box([6.9, 6.0, 6.9], [9.1, 10.0, 9.1], "rune_band", glow=10)
    # heavy ruler
    h = model("heavy_ruler")
    h.box([6.5, -14.0, 7.5], [9.5, 30.0, 8.5], "mat_iron_black")
    for i in range(10):
        y = -12 + i * 4.2
        h.box([6.4, y, 7.4], [7.6 if i % 2 else 8.2, y + 0.4, 8.6], "mat_gold", glow=8)
    h.box([6.3, -15.0, 7.3], [9.7, -13.6, 8.7], "mat_gold")
    # wind blade: a small white sickle crescent
    wb = model("wind_blade")
    ring(wb, (8, 8, 8), 6.0, 6, 1.0, 0.4, "z", "mat_qi_sword", glow=12, phase=200.0, arc=140.0)
    # hammers
    for mid, head, haft in (("sumeru_hammer", "mat_gold", "mat_iron_black"), ("dharma_hammer", "mat_bronze", "mat_wood")):
        hm = model(mid)
        oct_prism(hm, (8, 6.0, 8), 1.0, 26.0, "y", haft)
        oct_prism(hm, (8, 24.0, 8), 5.6, 14.0, "x", head)
        hm.box([7.0, 17.8, 1.8], [9.0, 30.2, 14.2], "rune_band", glow=12)
        oct_prism(hm, (0.4, 24.0, 8), 4.6, 1.2, "x", "mat_glow_gold", glow=15)
        oct_prism(hm, (15.6, 24.0, 8), 4.6, 1.2, "x", "mat_glow_gold", glow=15)
        oct_prism(hm, (8, -7.4, 8), 1.6, 2.0, "y", head)


# ---------------------------------------------------------------------------------------------- hands and feet

def hand(mid, tex, accent=None, spread=0.0, curl=10.0, glow=0, plate=None, closed=False, long_fingers=False, veins=None):
    """An open palm facing north (the palm surface is the north face), fingers up; centre (8,8,8)."""
    m = model(mid)
    m.box([3.0, 2.0, 6.5], [13.0, 14.0, 9.5], tex, glow=glow)                     # palm
    m.box([4.0, -4.0, 6.8], [12.0, 2.0, 9.2], tex, glow=glow)                     # wrist
    if accent:
        m.box([3.6, -5.0, 6.4], [12.4, -2.0, 9.6], accent, glow=max(glow, 6))   # cuff
    xs = (3.6, 5.8, 8.0, 10.2)
    lengths = (6.0, 7.2, 7.6, 6.6) if not long_fingers else (9.0, 11.0, 11.5, 10.0)
    for i, (x, L) in enumerate(zip(xs, lengths)):
        a = (i - 1.5) * spread
        seg = L / 3.0
        y = 14.0
        for j in range(3):
            c = curl * (j + 1) * (3.2 if closed else 1.0)
            m.box([x, y, 7.0], [x + 2.0, y + seg + 0.2, 9.0], tex, glow=glow, rot={"x": -c, "z": -a, "origin": [x + 1.0, 14.0, 8.0]})
            y += seg
    # thumb at 35 degrees from the palm edge
    m.box([12.4, 5.0, 7.0], [14.4, 11.0, 9.0], tex, glow=glow, rot={"z": -35.0 if not closed else -70.0, "origin": [13.0, 5.0, 8.0]})
    if plate:
        m.box([5.0, 4.5, 6.2], [11.0, 10.5, 6.5], plate, glow=15, faces={"north": plate}, full=("north",))
    if veins:
        for x in (5.0, 8.0, 11.0):
            m.box([x - 0.2, 2.5, 6.35], [x + 0.2, 13.5, 6.5], veins, glow=15)
    return m


def fist(mid, tex, cuff, glow=0):
    m = model(mid)
    m.box([3.5, 3.0, 4.0], [12.5, 12.0, 12.0], tex, glow=glow)                         # back of the hand
    for i in range(4):                                                                 # knuckles at the front (north)
        x = 3.7 + i * 2.2
        m.box([x, 9.0, 2.6], [x + 2.1, 12.4, 6.0], tex, glow=glow)
        m.box([x, 5.4, 2.4], [x + 2.1, 9.0, 4.4], tex, glow=glow)
    m.box([4.4, 3.2, 2.2], [12.0, 5.6, 4.8], tex, glow=glow)                          # thumb across
    m.box([4.5, -2.0, 5.0], [11.5, 3.0, 11.0], tex, glow=glow)                        # wrist
    m.box([4.0, -3.5, 4.5], [12.0, 0.0, 11.5], cuff, glow=max(6, glow))               # cuff
    return m


def build_hands():
    hand("buddha_palm", "mat_gold", accent="mat_glow_gold", spread=3.0, curl=6.0, glow=6, plate="glyph_wan")
    hand("qi_palm", "mat_qi_gold", spread=4.0, curl=8.0, glow=12)
    for dao in DAO_RAMP:
        hand("qi_palm_" + dao, "mat_qi_" + dao, spread=4.0, curl=8.0, glow=12)
    hand("jade_palm", "mat_jade", spread=9.0, curl=4.0, glow=10)
    hand("thunder_palm", "mat_gold", accent="mat_thunder", spread=3.0, curl=6.0, glow=8, veins="mat_thunder")
    hand("prison_hand_open", "mat_bronze", accent="rune_band", spread=8.0, curl=14.0, long_fingers=True, glow=4)
    hand("prison_hand_closed", "mat_bronze", accent="rune_band", spread=1.0, curl=16.0, long_fingers=True, closed=True, glow=4)
    hand("prison_hand", "mat_bronze", accent="rune_band", spread=8.0, curl=14.0, long_fingers=True, glow=4)
    fist("golden_fist", "mat_gold", "mat_glow_gold", glow=8)
    fist("emperor_fist", "mat_gold", "mat_cloth_red", glow=10)
    fist("phantom_fist", "mat_path_deva", "mat_glow_gold", glow=12)
    for i, path in enumerate(SIX_PATHS):
        fist("phantom_fist_" + path, "mat_path_" + path, "mat_glow_white", glow=12)
    # fate finger: one giant index finger pointing down with a cloud cuff (tip at y = -16)
    f = model("fate_finger")
    oct_prism(f, (8, 4.0, 8), 3.2, 24.0, "y", "mat_porcelain", glow=8)
    sphere(f, (8, -8.0, 8), 3.1, "mat_porcelain", glow=10)
    f.box([6.6, -4.0, 4.6], [9.4, 2.0, 4.9], "mat_glow_white", glow=15)              # the nail-side glow line
    for a in range(0, 360, 60):
        p = (8 + 4.4 * math.cos(math.radians(a)), 16.5, 8 + 4.4 * math.sin(math.radians(a)))
        sphere(f, p, 2.4, "mat_cloud", boxes=1)
    # god foot: sole down (y = 8 is the sole), toes north
    g = model("god_foot")
    g.box([2.0, 8.0, -2.0], [14.0, 12.0, 20.0], "mat_bronze")
    g.box([3.0, 12.0, 4.0], [13.0, 18.0, 18.0], "mat_bronze")
    for i in range(5):
        x = 2.4 + i * 2.3
        g.box([x, 8.0, -5.0 + (abs(i - 1.5) * 0.6)], [x + 2.1, 11.0, -1.8], "mat_bronze")
    g.box([4.0, 18.0, 9.0], [12.0, 30.0, 17.0], "mat_bronze")                        # ankle
    for y in (19.0, 23.0, 27.0):
        g.box([3.6, y, 8.6], [12.4, y + 1.4, 17.4], "mat_cloth_red")
    g.box([2.0, 7.8, -2.0], [14.0, 8.0, 20.0], "mat_glow_gold", glow=15, skip=("up",))


# ---------------------------------------------------------------------------------------------- beasts

def wing(mid, tex, bone, side=1, feathers=6, span=22.0, glow=0, membrane=None):
    """A feathered wing hinged at (8,8,8), spreading to +x (side 1) or -x (side -1), feathers trailing south."""
    m = model(mid)
    s = side
    x0 = 8.0
    # bones: three segments along the leading edge
    m.box([min(x0, x0 + s * 9), 7.2, 7.2], [max(x0, x0 + s * 9), 8.8, 8.8], bone, glow=glow)
    m.box([min(x0 + s * 8.5, x0 + s * 17), 7.4, 7.4], [max(x0 + s * 8.5, x0 + s * 17), 8.6, 8.6], bone, glow=glow,
          rot={"y": s * 12.0, "origin": [x0 + s * 8.5, 8, 8]})
    for i in range(feathers):
        t = i / max(1, feathers - 1)
        px = x0 + s * (2.0 + t * (span - 6.0))
        length = 10.0 + 6.0 * math.sin(t * math.pi) + (4.0 if i == feathers - 1 else 0.0)
        ang = s * (8.0 + t * 30.0)
        m.box([px - 1.3, 7.6, 8.0], [px + 1.3, 8.4, 8.0 + length], tex, glow=glow, rot={"y": ang, "origin": [px, 8, 8]})
    if membrane:
        m.box([min(x0, x0 + s * 14), 7.9, 8.0], [max(x0, x0 + s * 14), 8.1, 15.0], membrane, glow=glow)
    return m


def build_beasts():
    # white tiger (front north): body, head with jaw, legs mid-stride, tail chain
    def tiger(mid, fur, face):
        t = model(mid)
        t.box([4.0, 9.0, 0.0], [12.0, 16.0, 18.0], fur)
        t.box([4.5, 10.0, -6.5], [11.5, 16.5, 0.5], fur, faces={"north": face}, full=("north",))
        t.box([5.0, 8.0, -7.0], [11.0, 10.4, -1.0], fur)                                   # jaw
        t.box([5.2, 16.5, -3.0], [6.8, 18.0, -1.5], fur)                                    # ears
        t.box([9.2, 16.5, -3.0], [10.8, 18.0, -1.5], fur)
        for (x, z, a) in ((4.2, 1.5, -25.0), (9.8, 1.5, 20.0), (4.2, 14.0, 22.0), (9.8, 14.0, -28.0)):
            t.box([x, 1.0, z], [x + 2.2, 10.0, z + 2.6], fur, rot={"x": a, "origin": [x + 1.1, 10.0, z + 1.3]})
        for i, (z, y) in enumerate(((18.0, 14.0), (21.5, 15.6), (25.0, 18.0))):
            t.box([7.2, y, z], [8.8, y + 1.6, z + 3.8], fur, rot={"x": -18.0 * (i + 1), "origin": [8, y + 0.8, z]})
        return t
    tiger("white_tiger", "mat_fur_white_striped", "face_white_tiger")
    tiger("tiger_orange", "mat_fur_orange_striped", "face_orange_tiger")
    # sky wolf: lunging, forelegs forward, open jaw
    w = model("sky_wolf")
    w.box([4.6, 9.0, 1.0], [11.4, 15.0, 17.0], "mat_fur_grey", rot={"x": 10.0, "origin": [8, 12, 9]})
    w.box([5.0, 11.0, -6.0], [11.0, 16.4, 1.2], "mat_fur_grey", faces={"north": "face_wolf"}, full=("north",))
    w.box([6.0, 9.0, -9.0], [10.0, 11.5, -5.0], "mat_fur_grey", rot={"x": 18.0, "origin": [8, 11, -5]})
    w.box([6.0, 12.0, -9.0], [10.0, 13.8, -5.0], "mat_fur_grey")
    w.box([5.2, 16.4, -2.0], [6.8, 19.4, -0.6], "mat_fur_grey")
    w.box([9.2, 16.4, -2.0], [10.8, 19.4, -0.6], "mat_fur_grey")
    for x in (4.8, 9.2):
        w.box([x, 4.0, -3.0], [x + 2.0, 12.0, -0.6], "mat_fur_grey", rot={"x": -55.0, "origin": [x + 1, 12, -1.8]})
        w.box([x, 2.0, 14.0], [x + 2.0, 10.0, 16.4], "mat_fur_grey", rot={"x": 40.0, "origin": [x + 1, 10, 15]})
    w.box([7.0, 13.0, 17.0], [9.0, 15.0, 27.0], "mat_fur_grey", rot={"x": -20.0, "origin": [8, 14, 17]})
    w.box([5.6, 12.6, -4.6], [6.4, 13.4, -4.4], "mat_qi_ice", glow=15)
    w.box([9.6, 12.6, -4.6], [10.4, 13.4, -4.4], "mat_qi_ice", glow=15)

    # phoenixes: body, neck, crested head, five tail plumes; wings are separate (flapping) models
    def phoenix(mid, feather, glow):
        p = model(mid)
        p.box([5.0, 5.5, 2.0], [11.0, 10.5, 14.0], feather, glow=glow)                   # body
        p.box([6.2, 8.0, -3.5], [9.8, 11.0, 3.0], feather, glow=glow, rot={"x": -25.0, "origin": [8, 9, 2]})   # neck
        p.box([6.0, 10.5, -7.5], [10.0, 14.0, -3.0], feather, glow=glow)                 # head
        p.cbox((8, 11.8, -8.6), (1.4, 1.4, 2.8), "mat_gold", glow=glow, rot={"x": 20.0, "origin": [8, 12, -7.5]})  # beak
        for i in range(3):
            p.box([7.4, 13.8, -6.0 + i * 1.8], [8.6, 18.0 - i, -4.6 + i * 1.8], feather, glow=15, rot={"x": 25.0 + 12 * i,
                                                                                                    "origin": [8, 14, -5 + i * 1.8]})
        for i in range(5):
            a = (i - 2) * 14.0
            p.box([7.0, 6.6, 13.0], [9.0, 7.6, 31.0 - abs(i - 2) * 3.0], feather, glow=glow,
                  rot={"y": a, "x": -10.0, "origin": [8, 7, 13]})
        p.box([6.6, 12.0, -7.6], [7.2, 12.8, -7.4], "mat_glow_white", glow=15)
        p.box([8.8, 12.0, -7.6], [9.4, 12.8, -7.4], "mat_glow_white", glow=15)
        wing(mid + "_wing_r", feather, "mat_gold" if "fire" in mid else "mat_ice_solid", side=1, glow=glow)
        wing(mid + "_wing_l", feather, "mat_gold" if "fire" in mid else "mat_ice_solid", side=-1, glow=glow)
        return p
    phoenix("fire_phoenix", "mat_feather_fire", 12)
    phoenix("ice_phoenix", "mat_feather_ice", 10)
    model_alias("phoenix_wing", "fire_phoenix_wing_r")

    # dragon head and body segment (red, purple, gold variants)
    for var in ("red", "purple", "gold"):
        scale_tex, face = "mat_dragon_" + var, "face_dragon_" + var
        h = model("dragon_head" + ("" if var == "red" else "_" + var))
        h.box([3.5, 5.0, 0.0], [12.5, 13.0, 9.0], scale_tex, faces={"north": face}, full=("north",))  # skull
        h.box([4.5, 5.5, -8.0], [11.5, 10.0, 0.5], scale_tex)                                         # snout
        h.box([5.0, 2.5, -7.0], [11.0, 5.2, 2.0], scale_tex, rot={"x": 14.0, "origin": [8, 5, 2]})    # jaw
        for x, s in ((4.0, -1), (12.0, 1)):
            h.box([x - 0.8, 12.0, 4.0], [x + 0.8, 13.6, 14.0], "mat_gold", rot={"x": -30.0, "z": s * 18.0, "origin": [x, 12.6, 5.0]})
            h.box([x - 0.3, 7.0, -7.0], [x + 0.3, 7.6, -1.0], "mat_gold", rot={"y": s * 40.0, "z": s * 20.0, "origin": [x, 7.3, -6.0]})
        for i in range(4):
            h.box([7.4, 12.5, 1.0 + i * 2.2], [8.6, 15.0 - i * 0.4, 2.6 + i * 2.2], "mat_gold")
        h.box([5.2, 9.8, -0.6], [6.6, 11.0, -0.2], "mat_glow_gold", glow=15)
        h.box([9.4, 9.8, -0.6], [10.8, 11.0, -0.2], "mat_glow_gold", glow=15)
        h.box([5.4, 5.4, -7.6], [10.6, 5.8, -1.0], "mat_flame", glow=15)                               # fire in the mouth
        sg = model("dragon_segment" + ("" if var == "red" else "_" + var))
        sg.box([5.0, 5.0, 2.0], [11.0, 11.0, 14.0], scale_tex)
        sg.box([7.4, 11.0, 3.0], [8.6, 14.0, 6.0], "mat_gold", rot={"x": -20.0, "origin": [8, 11, 4.5]})
        sg.box([7.4, 11.0, 8.0], [8.6, 13.4, 11.0], "mat_gold", rot={"x": -20.0, "origin": [8, 11, 9.5]})
        sg.box([5.4, 4.6, 2.4], [10.6, 5.0, 13.6], "mat_gold")

    # fire crow
    c = model("fire_crow")
    c.box([6.0, 6.0, 4.0], [10.0, 10.0, 12.0], "mat_black_glaze")
    c.box([6.4, 7.4, 1.0], [9.6, 10.6, 4.4], "mat_black_glaze")
    c.cbox((8, 8.6, 0.2), (1.0, 1.0, 2.0), "mat_gold")
    c.box([10.0, 8.0, 5.0], [20.0, 8.6, 11.0], "mat_flame", glow=15, rot={"z": -12.0, "origin": [10, 8.3, 8]})
    c.box([-4.0, 8.0, 5.0], [6.0, 8.6, 11.0], "mat_flame", glow=15, rot={"z": 12.0, "origin": [6, 8.3, 8]})
    c.box([7.0, 7.0, 12.0], [9.0, 8.0, 18.0], "mat_flame", glow=15)
    c.box([6.8, 9.4, 1.0], [7.4, 10.0, 0.8], "mat_glow_gold", glow=15)
    c.box([8.6, 9.4, 1.0], [9.2, 10.0, 0.8], "mat_glow_gold", glow=15)
    # qilin bust (head and chest), front north
    q = model("qilin")
    q.box([3.0, 2.0, 2.0], [13.0, 14.0, 12.0], "mat_jade")
    q.box([4.5, 12.0, -6.0], [11.5, 19.0, 3.0], "mat_jade", faces={"north": "face_qilin"}, full=("north",))
    q.box([5.5, 12.4, -10.0], [10.5, 16.4, -5.6], "mat_jade")
    for x, s in ((6.0, -1), (10.0, 1)):
        q.box([x - 0.6, 18.5, -2.0], [x + 0.6, 26.0, -0.8], "mat_gold", glow=8, rot={"x": -25.0, "z": s * 12.0, "origin": [x, 19, -1.4]})
    for i in range(5):
        q.box([3.0 + i * 2.2, 14.0, 3.0], [4.8 + i * 2.2, 20.0 - abs(i - 2), 10.0], "mat_flame", glow=12)
    q.box([2.6, 1.6, 1.6], [13.4, 3.0, 12.4], "mat_gold")
    # kunpeng: fish-bird body with tail fin; the wings are separate flapping models
    k = model("kunpeng")
    k.box([2.0, 4.0, -4.0], [14.0, 12.0, 20.0], "mat_feather_kun")
    k.box([3.5, 5.0, -12.0], [12.5, 11.0, -3.6], "mat_feather_kun")
    k.box([4.5, 5.5, -14.5], [11.5, 9.5, -11.5], "mat_feather_kun")
    k.box([6.4, 12.0, -2.0], [9.6, 18.0, 8.0], "mat_feather_kun", rot={"x": -25.0, "origin": [8, 12, 3]})
    k.box([1.0, 6.0, 20.0], [15.0, 8.0, 30.0], "mat_feather_white", rot={"x": 8.0, "origin": [8, 7, 20]})
    k.box([3.6, 8.6, -12.2], [5.0, 10.0, -11.8], "mat_glow_gold", glow=15)
    k.box([11.0, 8.6, -12.2], [12.4, 10.0, -11.8], "mat_glow_gold", glow=15)
    k.box([2.4, 3.8, -4.0], [13.6, 4.2, 20.0], "mat_feather_white")
    wing("kunpeng_wing_r", "mat_feather_kun", "mat_feather_white", side=1, feathers=7, span=24.0)
    wing("kunpeng_wing_l", "mat_feather_kun", "mat_feather_white", side=-1, feathers=7, span=24.0)
    # ice scorpion
    sc = model("ice_scorpion")
    sc.box([5.0, 6.0, 2.0], [11.0, 9.0, 12.0], "mat_ice_solid", glow=6)
    sc.box([5.6, 6.2, -1.0], [10.4, 8.6, 2.2], "mat_ice_solid", glow=6)
    for x, s in ((3.0, -1), (13.0, 1)):
        sc.box([x - 0.8, 6.6, -6.0], [x + 0.8, 8.2, 0.0], "mat_ice", glow=8, rot={"y": s * 20.0, "origin": [x, 7.4, 0]})
        sc.box([x - 1.6, 6.4, -10.0], [x + 1.6, 8.4, -5.6], "mat_ice_solid", glow=10)
    for i in range(3):
        for x in (4.0, 12.0):
            sc.box([x - 2.2, 5.0, 3.0 + i * 3.0], [x + 2.2, 5.8, 4.0 + i * 3.0], "mat_ice", glow=6)
    for i, (y, z, a) in enumerate(((9.0, 12.0, -30.0), (12.0, 14.6, -65.0), (16.0, 15.0, -110.0), (19.0, 12.6, -150.0))):
        sc.box([7.0, y, z], [9.0, y + 1.8, z + 3.6], "mat_ice_solid", glow=8, rot={"x": a, "origin": [8, y, z]})
    sc.cbox((8, 19.6, 10.4), (1.2, 2.8, 1.2), "mat_glow_white", glow=15, rot={"x": 30.0, "origin": [8, 19, 11]})
    # silver butterfly (wings at +-30 degrees)
    b = model("silver_butterfly")
    b.box([7.6, 6.0, 7.6], [8.4, 10.0, 8.4], "mat_silver", glow=10)
    b.box([8.4, 8.0, 4.0], [16.0, 8.2, 12.0], "butterfly_wing", glow=12, rot={"z": 30.0, "origin": [8.4, 8.1, 8]}, full=("up", "down"))
    b.box([0.0, 8.0, 4.0], [7.6, 8.2, 12.0], "butterfly_wing", glow=12, rot={"z": -30.0, "origin": [7.6, 8.1, 8]}, full=("up", "down"))
    FULL_SHEETS.add("butterfly_wing")
    # gold beetle
    gb = model("gold_beetle")
    gb.box([4.0, 6.0, 3.0], [8.0, 10.0, 13.0], "beetle_shell", glow=6, rot={"z": 10.0, "origin": [8, 6, 8]})
    gb.box([8.0, 6.0, 3.0], [12.0, 10.0, 13.0], "beetle_shell", glow=6, rot={"z": -10.0, "origin": [8, 6, 8]})
    gb.box([5.6, 6.4, 0.6], [10.4, 9.0, 3.4], "mat_iron_black")
    for z in (4.0, 7.5, 11.0):
        gb.box([2.0, 5.4, z], [14.0, 6.0, z + 0.6], "mat_iron_black")
    # sleep bug: a small soft green moth
    sb = model("sleep_bug")
    sb.box([7.4, 7.0, 6.0], [8.6, 8.4, 11.0], "mat_qi_life", glow=12)
    sb.box([8.6, 7.6, 6.4], [13.0, 7.8, 10.6], "mat_qi_life", glow=15, rot={"z": 22.0, "origin": [8.6, 7.7, 8.5]})
    sb.box([3.0, 7.6, 6.4], [7.4, 7.8, 10.6], "mat_qi_life", glow=15, rot={"z": -22.0, "origin": [7.4, 7.7, 8.5]})
    # back wings worn by players and disciples (both sides as separate models, hinge at (8,8,8))
    for kind, tex, bone, mem in (("kunpeng_wings", "mat_feather_kun", "mat_feather_white", None),
                                 ("lightning_wings", "mat_feather_thunder", "mat_thunder", None),
                                 ("flame_wings", "mat_feather_fire", "mat_gold", None),
                                 ("wind_thunder_wings", "mat_feather_storm", "mat_thunder", None),
                                 ("lei_zhenzi_wings", "mat_feather_gold", "mat_gold", None),
                                 ("dragon_wings", "mat_dragon_lavender", "mat_dragon_lavender", "mat_dragon_lavender"),
                                 ("dragon_wings_green", "mat_dragon_green", "mat_dragon_green", "mat_dragon_green")):
        g = 12 if kind in ("lightning_wings", "flame_wings", "wind_thunder_wings") else 4
        wing(kind + "_r", tex, bone, side=1, feathers=6, span=18.0, glow=g, membrane=mem)
        wing(kind + "_l", tex, bone, side=-1, feathers=6, span=18.0, glow=g, membrane=mem)


# ---------------------------------------------------------------------------------------------- form pieces (part space)
# Part space in item coordinates: the part pivot is (8, 8, 8); up +y, front -z, the wearer's right +x.
# Head part: the head cube spans x 4..12, y 8..16, z 4..12. Body part: x 4..12, y -4..8, z 6..10.
# Arm part (right): x 7..11, y -2..10, z 6..10 (pivot at the shoulder); left arm x 5..9.

def build_forms():
    # golden ape
    a = model("ape_head")
    a.box([3.4, 7.6, 3.0], [12.6, 17.0, 12.6], "mat_fur_gold", faces={"north": "face_ape"}, full=("north",))
    a.box([3.0, 14.0, 2.4], [13.0, 15.6, 5.0], "mat_fur_gold")                      # brow ridge
    a.box([4.6, 7.0, 1.6], [11.4, 10.6, 4.0], "mat_fur_gold")                       # jaw
    a.box([2.0, 11.0, 6.0], [3.4, 14.0, 9.0], "mat_fur_gold")
    a.box([12.6, 11.0, 6.0], [14.0, 14.0, 9.0], "mat_fur_gold")
    a.box([5.0, 17.0, 5.0], [11.0, 18.4, 11.0], "mat_gold", glow=8)                 # golden fillet
    t = model("ape_torso")
    t.box([2.6, -5.0, 4.6], [13.4, 8.6, 11.6], "mat_fur_gold")
    t.box([2.0, 4.0, 7.0], [14.0, 10.6, 13.6], "mat_fur_gold")                      # shoulder hump
    t.box([4.4, -4.0, 4.2], [11.6, 5.0, 4.8], "mat_fur_white")                      # chest
    for side, mid in ((1, "ape_arm_r"), (-1, "ape_arm_l")):
        ar = model(mid)
        x0 = 6.6 if side == 1 else 4.6
        ar.box([x0 - 0.4, -0.6, 5.4], [x0 + 5.0, 10.6, 10.6], "mat_fur_gold")
        ar.box([x0 - 0.2, -7.0, 5.6], [x0 + 4.8, -0.4, 10.4], "mat_fur_gold")       # long forearm past the hand
        ar.box([x0 - 0.6, -9.0, 5.0], [x0 + 5.2, -6.6, 11.0], "mat_fur_gold")       # fist
        ar.box([x0 - 0.7, -1.4, 5.0], [x0 + 5.3, 0.6, 11.0], "mat_gold", glow=8)    # arm ring
    sk = model("ape_fur_skirt")
    for i in range(6):
        a2 = i * 60.0
        p = (8 + 4.6 * math.cos(math.radians(a2)), -5.5, 8 + 2.6 * math.sin(math.radians(a2)))
        sk.cbox(p, (4.0, 6.0, 1.2), "mat_fur_gold", rot={"y": -(a2 + 90.0), "x": 8.0, "origin": [p[0], -2.5, p[2]]})
    sk.box([3.6, -3.4, 5.6], [12.4, -2.0, 10.4], "mat_cloth_red")

    # three heads, six arms
    eh = model("extra_head")
    eh.box([4.0, 8.0, 4.0], [12.0, 16.0, 12.0], "mat_fur_gold", faces={"north": "face_ape"}, full=("north",))
    eh.box([3.8, 15.0, 3.8], [12.2, 16.6, 12.2], "mat_gold", glow=10)
    eh.box([7.0, 16.6, 5.0], [9.0, 19.0, 6.0], "mat_gold", glow=12)
    ea = model("extra_arm")
    ea.box([6.0, -2.0, 6.0], [10.0, 10.0, 10.0], "mat_gold", glow=4)
    ea.box([5.6, 7.0, 5.6], [10.4, 10.4, 10.4], "mat_cloth_red")
    ea.box([5.8, -3.2, 5.8], [10.2, -1.0, 10.2], "mat_glow_gold", glow=10)

    # dragonblood and dragon form pieces
    for suffix, tex in (("", "mat_dragon_green"), ("_lavender", "mat_dragon_lavender")):
        h = model("dragon_horns" + suffix)
        for x, s in ((5.0, -1), (11.0, 1)):
            h.box([x - 0.7, 15.5, 7.0], [x + 0.7, 21.0, 8.4], tex, rot={"x": 30.0, "z": s * 20.0, "origin": [x, 15.5, 7.7]})
            h.box([x - 0.5, 20.0, 9.4], [x + 0.5, 23.0, 10.4], "mat_gold", rot={"x": 55.0, "z": s * 25.0, "origin": [x, 20.0, 9.9]})
        st = model("dragon_wings_stub" + suffix)
        for x, s in ((6.0, -1), (10.0, 1)):
            st.box([x - 0.8, 2.0, 10.0], [x + 0.8, 8.0, 12.0], tex, rot={"z": s * 35.0, "origin": [x, 4.0, 11.0]})
            st.box([min(x, x + s * 7), 6.0, 11.2], [max(x, x + s * 7), 6.4, 13.0], tex, rot={"z": s * 25.0, "origin": [x, 6, 12]})
        tl = model("dragon_tail" + suffix)
        tl.box([6.0, 6.0, 8.0], [10.0, 10.0, 16.0], tex)
        tl.box([6.5, 6.5, 15.0], [9.5, 9.5, 22.0], tex, rot={"x": -14.0, "origin": [8, 8, 15]})
        tl.box([7.0, 7.0, 21.0], [9.0, 9.0, 28.0], tex, rot={"x": -28.0, "origin": [8, 8, 21]})
        tl.box([7.4, 9.0, 9.0], [8.6, 11.0, 20.0], "mat_gold")
    # ancient god: brow stars (one small glowing cube; the layer draws up to nine)
    bs = model("brow_star")
    bs.cbox((8, 8, 8), (1.2, 1.2, 0.6), "mat_glow_gold", glow=15)
    # dharma armour
    hm = model("dharma_helm")
    hm.box([3.4, 13.0, 3.4], [12.6, 17.6, 12.6], "mat_gold")
    hm.box([3.2, 9.0, 9.0], [12.8, 13.4, 12.8], "mat_gold")
    hm.box([7.2, 17.6, 3.0], [8.8, 22.0, 6.0], "mat_gold", glow=8)
    for x, s in ((3.0, -1), (13.0, 1)):
        hm.box([x - 0.5, 15.0, 5.0], [x + 0.5, 21.0, 9.0], "mat_gold", rot={"z": s * 30.0, "origin": [x, 15, 7]})
    hm.box([6.4, 16.0, 3.0], [9.6, 17.2, 3.4], "mat_jade", glow=12)
    for side, mid in ((1, "dharma_pauldron_r"), (-1, "dharma_pauldron_l")):
        pd = model(mid)
        x0 = 6.4 if side == 1 else 4.4
        pd.box([x0, 7.0, 5.0], [x0 + 5.6, 10.6, 11.0], "mat_gold", rot={"z": -side * 12.0, "origin": [x0 + 2.8, 9, 8]})
        pd.box([x0 - 0.2, 5.4, 4.8], [x0 + 5.8, 7.2, 11.2], "mat_cloth_red", rot={"z": -side * 12.0, "origin": [x0 + 2.8, 9, 8]})
        pd.box([x0 + 0.4, -1.0, 5.4], [x0 + 5.0, 2.0, 10.6], "mat_gold")
    bp = model("dharma_breastplate")
    bp.box([3.4, -3.0, 5.4], [12.6, 8.4, 10.6], "mat_gold")
    bp.box([5.4, 0.0, 5.0], [10.6, 5.2, 5.4], "mat_glow_white", glow=15)            # mirror disc
    bp.box([3.2, -4.4, 5.2], [12.8, -2.6, 10.8], "mat_cloth_red")
    hl = model("dharma_halo")
    ring(hl, (8, 14, 14.0), 7.0, 16, 0.8, 0.5, "z", "mat_glow_gold", glow=15)
    ring(hl, (8, 14, 14.2), 4.6, 12, 0.4, 0.4, "z", "mat_flame_white", glow=15)
    # heaven fire mantles (orange, blue soul fire, white gold) and shoulder flames
    for i, tex in enumerate(("mat_flame", "mat_flame_blue", "mat_flame_white"), start=1):
        fm = model("flame_mantle_%d" % i)
        fm.box([3.0, -4.6, 4.6], [13.0, 9.0, 11.4], tex, glow=15)
        for j in range(5):
            x = 3.4 + j * 2.1
            fm.box([x, 9.0, 9.0], [x + 1.8, 13.0 + (j % 2) * 3.0, 11.0], tex, glow=15, rot={"x": 18.0, "origin": [x, 9, 10]})
        fs = model("flame_shoulder_%d" % i)
        fs.box([6.2, 7.0, 5.4], [11.8, 11.0, 10.6], tex, glow=15)
        fs.box([7.0, 11.0, 6.4], [9.0, 14.6, 8.4], tex, glow=15, rot={"z": -10.0, "origin": [8, 11, 7.4]})
        fs.box([9.4, 11.0, 7.4], [11.0, 13.4, 9.4], tex, glow=15, rot={"z": 14.0, "origin": [10, 11, 8.4]})
    # thunder drum (a ring of these behind the venerable's back is drawn by the layer)
    d = model("thunder_drum")
    oct_prism(d, (8, 8, 8), 4.4, 5.0, "z", "mat_lacquer_red")
    d.cbox((8, 8, 5.4), (7.6, 7.6, 0.4), "mat_drum_skin", rot={"z": 22.5, "origin": [8, 8, 5.4]})
    d.cbox((8, 8, 10.6), (7.6, 7.6, 0.4), "mat_drum_skin", rot={"z": 22.5, "origin": [8, 8, 10.6]})
    for a in range(0, 360, 45):
        p = (8 + 4.6 * math.cos(math.radians(a)), 8 + 4.6 * math.sin(math.radians(a)), 8)
        d.cbox(p, (0.8, 0.8, 5.4), "mat_gold", glow=8)
    # seventy-two transformation bodies (front north, standing on y = 8)
    hk = model("hawk")
    hk.box([5.6, 13.0, 4.0], [10.4, 17.0, 12.0], "mat_feather_brown")
    hk.box([6.2, 15.0, 1.0], [9.8, 18.4, 4.6], "mat_feather_white")
    hk.cbox((8, 16.2, 0.4), (1.2, 1.2, 1.8), "mat_gold", rot={"x": 25.0, "origin": [8, 16.6, 1]})
    hk.box([10.4, 15.0, 5.0], [24.0, 15.6, 11.0], "mat_feather_brown", rot={"z": -10.0, "origin": [10.4, 15.3, 8]})
    hk.box([-8.0, 15.0, 5.0], [5.6, 15.6, 11.0], "mat_feather_brown", rot={"z": 10.0, "origin": [5.6, 15.3, 8]})
    hk.box([6.6, 13.6, 12.0], [9.4, 14.4, 18.0], "mat_feather_brown")
    hk.box([6.6, 8.0, 7.0], [7.4, 13.0, 8.0], "mat_gold")
    hk.box([8.6, 8.0, 7.0], [9.4, 13.0, 8.0], "mat_gold")
    ko = model("koi")
    ko.box([5.0, 10.0, 0.0], [11.0, 16.0, 12.0], "mat_koi")
    ko.box([5.8, 10.6, -3.0], [10.2, 15.0, 0.4], "mat_koi")
    ko.box([7.6, 9.0, 12.0], [8.4, 17.0, 18.0], "mat_koi", rot={"x": 0.0})
    ko.box([7.7, 15.6, 3.0], [8.3, 19.0, 9.0], "mat_koi")
    ko.box([5.4, 13.0, -2.6], [6.0, 13.8, -2.2], "mat_glow_white", glow=12)
    ko.box([10.0, 13.0, -2.6], [10.6, 13.8, -2.2], "mat_glow_white", glow=12)
    be = model("bee_small")
    be.box([6.0, 10.0, 4.0], [10.0, 14.0, 13.0], "mat_bee")
    be.box([6.4, 10.4, 1.4], [9.6, 13.6, 4.2], "mat_iron_black")
    be.box([10.0, 13.6, 5.0], [16.0, 13.8, 9.0], "mat_cloud", glow=6, rot={"z": 20.0, "origin": [10, 13.7, 7]})
    be.box([0.0, 13.6, 5.0], [6.0, 13.8, 9.0], "mat_cloud", glow=6, rot={"z": -20.0, "origin": [6, 13.7, 7]})
    be.box([7.6, 10.6, 13.0], [8.4, 11.4, 15.0], "mat_iron_black")
    tp = model("small_temple")
    tp.box([2.0, 8.0, 2.0], [14.0, 9.6, 14.0], "mat_white_stone")
    tp.box([3.4, 9.6, 3.4], [12.6, 18.0, 12.6], "mat_lacquer_red")
    for x in (2.6, 12.0):
        tp.box([x, 9.6, 1.6], [x + 1.4, 18.0, 3.0], "mat_lacquer_red")
    tp.box([5.6, 9.6, 3.2], [10.4, 15.0, 3.6], "mat_iron_black")
    tp.box([0.0, 18.0, 0.0], [16.0, 19.6, 16.0], "mat_tiles_gold", rot={"x": 0.0})
    tp.box([1.4, 19.6, 1.4], [14.6, 21.6, 14.6], "mat_tiles_gold")
    tp.box([4.0, 21.6, 4.0], [12.0, 23.0, 12.0], "mat_tiles_gold")
    tp.cbox((8, 24.0, 8), (1.6, 2.4, 1.6), "mat_gold", glow=10)
    for x, z in ((0.0, 0.0), (16.0, 0.0), (0.0, 16.0), (16.0, 16.0)):
        tp.cbox((x, 19.0, z), (1.6, 0.8, 1.6), "mat_gold", rot={"x": 20.0 if z == 0 else -20.0, "origin": [x, 19, z]})
    # iron plate (IRON_BODY): a curved armour plate
    ip = model("iron_plate")
    for i, a in enumerate((-30.0, 0.0, 30.0)):
        ip.box([4.4 + i * 2.4, 3.0, 7.4], [6.8 + i * 2.4, 13.0, 8.6], "mat_iron_black", rot={"y": a, "origin": [8, 8, 11]})
    ip.box([5.0, 12.4, 6.8], [11.0, 13.4, 9.2], "mat_gold", glow=6)
    # ice claw gauntlet (hand layer)
    ic = model("ice_claw")
    ic.box([5.6, -3.0, 5.6], [10.4, 3.0, 10.4], "mat_ice_solid", glow=8)
    for i, x in enumerate((6.0, 8.0, 10.0)):
        ic.box([x - 0.5, -10.0, 5.2], [x + 0.5, -3.0, 6.4], "mat_ice", glow=14, rot={"x": -18.0, "origin": [x, -3, 5.8]})
    ic.box([5.2, 2.0, 5.2], [10.8, 3.4, 10.8], "mat_glow_white", glow=12)


# ---------------------------------------------------------------------------------------------- treasures and architecture

def cauldron(m, body, band):
    oct_prism(m, (8, 15.0, 8), 7.0, 9.0, "y", body)
    oct_prism(m, (8, 19.6, 8), 7.6, 1.4, "y", body)
    m.box([1.0, 14.0, 1.0], [15.0, 16.0, 15.0], band, glow=12)
    for a in (90, 210, 330):
        p = (8 + 5.0 * math.cos(math.radians(a)), 9.4, 8 + 5.0 * math.sin(math.radians(a)))
        m.cbox(p, (1.8, 3.0, 1.8), body)
    for x in (2.0, 14.0):
        m.box([x - 0.7, 20.0, 6.4], [x + 0.7, 25.0, 9.6], body)
        m.box([x - 0.7, 24.0, 5.6], [x + 0.7, 25.4, 10.4], body)
    m.box([2.4, 19.4, 2.4], [13.6, 20.4, 13.6], "mat_flame", glow=15, skip=("down",))


def build_treasures():
    # emperor seal: jade block with a coiled-beast knob and the 帝 face on the bottom (base at y 8)
    e = model("emperor_seal")
    e.box([2.0, 8.0, 2.0], [14.0, 16.0, 14.0], "mat_jade", faces={"down": "glyph_di"}, full=("down",))
    e.box([1.6, 8.0, 1.6], [14.4, 9.0, 14.4], "mat_gold", glow=8, skip=("down",))
    e.box([4.0, 16.0, 4.0], [12.0, 19.0, 12.0], "mat_jade")
    e.box([5.0, 19.0, 3.0], [11.0, 23.0, 9.0], "mat_gold", glow=6)
    e.box([6.0, 20.0, 9.0], [10.0, 22.0, 13.0], "mat_gold", glow=6, rot={"x": -20.0, "origin": [8, 21, 9]})
    e.box([6.0, 22.0, 1.4], [10.0, 25.0, 5.0], "mat_gold", glow=8)
    FULL_SHEETS.update({"glyph_di", "glyph_feng", "glyph_lei", "glyph_ling", "glyph_jin", "glyph_wan", "glyph_tian", "glyph_xian",
                        "rune_lei_glow", "rune_space_glow", "seal_face", "mirror_void_face", "taiji_64", "hex_face", "clock_face",
                        "bagua_face", "face_white_tiger", "face_orange_tiger", "face_wolf", "face_ape", "face_qilin",
                        "face_dragon_red", "face_dragon_purple", "face_dragon_gold", "beetle_shell"})
    f = model("fantian_seal")
    f.box([1.0, 8.0, 1.0], [15.0, 15.0, 15.0], "mat_bronze", faces={"down": "seal_face"}, full=("down",))
    f.box([0.6, 8.0, 0.6], [15.4, 9.2, 15.4], "mat_gold", glow=8, skip=("down",))
    oct_prism(f, (8, 20.0, 8), 2.0, 10.0, "y", "mat_bronze")
    sphere(f, (8, 26.5, 8), 3.0, "mat_gold", glow=6)
    f.box([1.4, 13.4, 1.4], [14.6, 14.4, 14.6], "rune_band", glow=12)
    ms = model("mountain_sea_seal")
    ms.box([1.0, 8.0, 1.0], [15.0, 13.0, 15.0], "mat_stone", faces={"down": "seal_face"}, full=("down",))
    ms.box([2.6, 13.0, 2.6], [13.4, 18.0, 13.4], "mat_stone")
    ms.box([4.4, 18.0, 4.0], [11.6, 23.0, 12.0], "mat_stone", rot={"y": 18.0, "origin": [8, 20, 8]})
    ms.box([6.0, 23.0, 6.0], [10.0, 27.0, 10.0], "mat_white_stone", rot={"y": 40.0, "origin": [8, 25, 8]})
    ms.box([0.8, 10.4, 0.8], [15.2, 11.4, 15.2], "mat_qi_body", glow=12)
    # void mirror: bronze rim of 12 segments, black glass face (front north), centre (8,8,8)
    v = model("void_mirror")
    ring(v, (8, 8, 8), 9.0, 12, 1.6, 2.4, "z", "mat_bronze")
    v.cbox((8, 8, 8), (12.6, 12.6, 0.8), "mirror_void_face", glow=15, rot={"z": 0.0}, full=("north", "south"))
    v.cbox((8, 8, 8), (12.6, 12.6, 0.7), "mirror_void_face", glow=15, rot={"z": 45.0, "origin": [8, 8, 8]}, full=("north", "south"))
    v.box([7.0, -4.0, 7.2], [9.0, -0.6, 8.8], "mat_bronze")
    v.cbox((8, -4.6, 8), (3.4, 1.4, 3.4), "mat_gold")
    for a in (0, 90, 180, 270):
        p = (8 + 9.6 * math.cos(math.radians(a)), 8 + 9.6 * math.sin(math.radians(a)), 8)
        v.cbox(p, (1.6, 1.6, 2.8), "mat_gold", glow=10, rot={"z": 45.0, "origin": list(p)})
    gm = model("golden_mirror")
    ring(gm, (8, 16, 8), 5.0, 10, 1.0, 1.4, "z", "mat_gold", glow=8)
    gm.cbox((8, 16, 8), (8.0, 8.0, 0.5), "mat_glow_gold", glow=15)
    gm.cbox((8, 16, 8), (8.0, 8.0, 0.44), "mat_glow_gold", glow=15, rot={"z": 45.0, "origin": [8, 16, 8]})
    gm.box([7.2, 8.0, 7.2], [8.8, 11.0, 8.8], "mat_bronze")
    gm.box([5.0, 8.0, 5.0], [11.0, 9.0, 11.0], "mat_bronze")
    # natal bells (plain bronze, silver, gold): body widening downwards, lip ring, hanger
    for mid, tex in (("natal_bell", "mat_bronze"), ("natal_bell_silver", "mat_silver"), ("natal_bell_gold", "mat_gold")):
        b = model(mid)
        taper(b, (8, 0, 8), 8.0, 22.0, 12.0, 6.0, 12.0, 6.0, 4, tex)
        oct_prism(b, (8, 8.6, 8), 6.6, 1.4, "y", tex)
        b.box([4.6, 14.0, 1.6], [11.4, 16.0, 14.4], "rune_band", glow=10)
        b.box([7.0, 22.0, 7.0], [9.0, 25.0, 9.0], tex)
        ring(b, (8, 26.0, 8), 1.6, 6, 0.6, 0.8, "z", tex)
        b.box([7.2, 6.0, 7.2], [8.8, 9.0, 8.8], "mat_gold", glow=10)
    # natal woks (iron / gold): turtle-shell bowl of angled plates, two lugs
    for mid, tex in (("natal_wok", "mat_iron_black"), ("natal_wok_gold", "mat_gold")):
        w = model(mid)
        w.box([3.0, 8.0, 3.0], [13.0, 9.0, 13.0], tex)
        for i in range(8):
            a = i * 45.0
            p = (8 + 6.0 * math.cos(math.radians(a)), 11.0, 8 + 6.0 * math.sin(math.radians(a)))
            w.cbox(p, (5.4, 5.0, 1.0), tex, rot={"y": -(a + 90.0), "x": -35.0, "origin": list(p)})
        for x in (-1.0, 17.0):
            w.cbox((x, 13.4, 8), (2.4, 1.0, 3.0), "mat_wood")
        hexs = "mat_gold" if tex == "mat_iron_black" else "mat_bronze"
        w.box([5.0, 8.0, 5.0], [11.0, 8.4, 11.0], hexs, glow=6, rot={"y": 30.0, "origin": [8, 8, 8]}, skip=("up",))
    # natal parasols: closed (shaft + folded canopy cone) and open (16 ribs + canopy plates); plain red and gold
    for suffix, cloth, trim in (("", "mat_cloth_red", "mat_wood"), ("_gold", "mat_gold", "mat_gold")):
        c = model("natal_parasol" + suffix)
        c.box([7.5, -14.0, 7.5], [8.5, 22.0, 8.5], trim)
        taper(c, (8, 0, 8), 4.0, 26.0, 3.6, 1.0, 3.6, 1.0, 4, cloth)
        c.box([7.3, -15.6, 7.3], [8.7, -13.6, 8.7], "mat_gold")
        c.box([7.6, 26.0, 7.6], [8.4, 29.0, 8.4], "mat_gold", glow=8)
        model_alias("natal_parasol_closed" + suffix, "natal_parasol" + suffix)
        o = model("natal_parasol_open" + suffix)
        o.box([7.5, -10.0, 7.5], [8.5, 20.0, 8.5], trim)
        o.box([7.3, -11.6, 7.3], [8.7, -9.6, 8.7], "mat_gold")
        # radial canopy plates: authored along +z from the shaft, drooped about x first, then turned to their heading
        for i in range(12):
            a = i * 30.0 + 15.0
            o.cbox((8, 19.4, 8 + 6.2), (6.6, 0.6, 12.4), cloth, rot={"x": 16.0, "y": 90.0 - a, "origin": [8, 19.7, 8]})
        for i in range(12):
            a = i * 30.0
            o.cbox((8, 19.0, 8 + 6.0), (0.5, 0.5, 12.6), trim, rot={"x": 16.0, "y": 90.0 - a, "origin": [8, 19.5, 8]})
        ring(o, (8, 16.3, 8), 11.6, 12, 0.8, 0.6, "y", trim)
        o.box([7.4, 20.0, 7.4], [8.6, 23.0, 8.6], "mat_gold", glow=10)
    # gourds: two stacked bulbs, neck, stopper, cord
    for mid, tex, cap in (("lu_ya_gourd", "mat_gourd_yellow", "mat_cloth_red"), ("zijin_gourd", "mat_purple_gold", "mat_gold")):
        g = model(mid)
        sphere(g, (8, 13.0, 8), 5.0, tex)
        sphere(g, (8, 20.6, 8), 3.4, tex)
        oct_prism(g, (8, 25.2, 8), 1.2, 2.4, "y", tex)
        g.box([6.6, 26.2, 6.6], [9.4, 28.0, 9.4], cap, glow=6 if cap == "mat_gold" else 0)
        g.box([4.4, 16.6, 4.4], [11.6, 17.6, 11.6], "mat_cloth_red")
        g.box([10.8, 13.0, 7.4], [11.6, 17.0, 8.6], "mat_cloth_red")
    st = model("gourd_stopper")
    st.box([6.6, 6.0, 6.6], [9.4, 9.0, 9.4], "mat_gold", glow=10)
    st.box([7.4, 9.0, 7.4], [8.6, 10.0, 8.6], "mat_gold", glow=10)
    # crow vase: tall black vase with a flared mouth and red flames
    cv = model("crow_vase")
    oct_prism(cv, (8, 12.0, 8), 4.6, 8.0, "y", "mat_black_glaze")
    oct_prism(cv, (8, 19.0, 8), 2.4, 6.0, "y", "mat_black_glaze")
    oct_prism(cv, (8, 23.0, 8), 3.6, 2.0, "y", "mat_black_glaze")
    cv.box([3.0, 11.0, 3.0], [13.0, 13.0, 13.0], "mat_flame", glow=14)
    cv.box([5.0, 23.8, 5.0], [11.0, 24.2, 11.0], "mat_flame", glow=15, skip=("down",))
    # guanyin vase and willow sprig
    gv = model("guanyin_vase")
    sphere(gv, (8, 13.0, 8), 4.6, "mat_porcelain")
    oct_prism(gv, (8, 20.0, 8), 1.6, 6.0, "y", "mat_porcelain")
    oct_prism(gv, (8, 23.6, 8), 2.6, 1.4, "y", "mat_porcelain")
    gv.box([4.0, 12.6, 4.0], [12.0, 13.6, 12.0], "mat_qi_ice", glow=10)
    oct_prism(gv, (8, 8.6, 8), 2.8, 1.2, "y", "mat_porcelain")
    wsp = model("willow_sprig")
    wsp.box([7.7, 8.0, 7.7], [8.3, 24.0, 8.3], "mat_wood")
    for i in range(5):
        y = 12.0 + i * 2.6
        s = 1 if i % 2 == 0 else -1
        wsp.box([8.0, y, 7.8], [8.0 + s * 5.0, y + 1.0, 8.2], "mat_leaf", rot={"z": -s * 40.0, "origin": [8, y, 8]})
    # banana leaf fan: six leaf plates fanning out, gold handle (grip at 8,8,8)
    bf = model("banana_fan")
    bf.box([7.4, 0.0, 7.4], [8.6, 11.0, 8.6], "mat_gold")
    for i in range(6):
        a = (i - 2.5) * 16.0
        bf.box([5.6, 11.0, 7.8], [10.4, 30.0, 8.2], "mat_leaf", rot={"z": a, "origin": [8, 11, 8]})
    bf.box([6.6, 10.4, 7.2], [9.4, 12.0, 8.8], "mat_gold", glow=8)
    # qiankun ring: a torus of 12 boxes with a glowing inner band
    qr = model("qiankun_ring")
    ring(qr, (8, 8, 8), 7.0, 12, 2.2, 2.2, "z", "mat_gold", glow=6)
    ring(qr, (8, 8, 8), 5.6, 12, 0.6, 1.0, "z", "mat_glow_gold", glow=15)
    # seed bag and cymbal
    sbg = model("seed_bag")
    sphere(sbg, (8, 15.0, 8), 6.0, "mat_cloth_yellow")
    oct_prism(sbg, (8, 22.0, 8), 2.0, 3.0, "y", "mat_cloth_yellow")
    sbg.box([5.4, 21.0, 5.4], [10.6, 22.0, 10.6], "mat_cloth_red")
    sbg.box([6.0, 24.0, 6.0], [10.0, 24.4, 10.0], "mat_shadow", skip=("down",))
    cy = model("cymbal")
    ring(cy, (8, 8, 8), 6.0, 12, 3.4, 0.6, "y", "mat_gold", length_scale=1.2)
    oct_prism(cy, (8, 8.0, 8), 5.0, 0.8, "y", "mat_gold")
    sphere(cy, (8, 9.4, 8), 1.8, "mat_gold", glow=8, boxes=2)
    # cauldron, furnace
    cauldron(model("mother_qi_cauldron"), "mat_bronze", "rune_band")
    bfn = model("bagua_furnace")
    oct_prism(bfn, (8, 16.0, 8), 7.0, 12.0, "y", "mat_bronze")
    bfn.box([1.4, 14.0, 0.6], [14.6, 18.0, 15.4], "bagua_face", glow=10, full=("north", "south", "east", "west"), skip=("up", "down"))
    taper(bfn, (8, 0, 8), 22.0, 27.0, 14.0, 6.0, 14.0, 6.0, 3, "mat_bronze")
    sphere(bfn, (8, 28.0, 8), 1.6, "mat_gold", glow=10)
    bfn.box([5.4, 13.0, 0.6], [10.6, 17.0, 1.2], "mat_flame", glow=15)
    for a in (90, 210, 330):
        p = (8 + 5.0 * math.cos(math.radians(a)), 9.0, 8 + 5.0 * math.sin(math.radians(a)))
        bfn.cbox(p, (2.0, 2.0, 2.0), "mat_bronze")
    # sun: layered cuboid sphere plus a corona of flame plates
    sn = model("sun")
    sphere(sn, (8, 8, 8), 8.0, "mat_sun", glow=15)
    sphere(sn, (8, 8, 8), 9.6, "mat_flame", glow=15, boxes=2)
    for a in range(0, 360, 30):
        p = (8 + 13.0 * math.cos(math.radians(a)), 8 + 13.0 * math.sin(math.radians(a)), 8)
        sn.cbox(p, (2.0, 4.0, 1.0), "mat_flame", glow=15, rot={"z": a - 90.0, "origin": list(p)})
    # black hole: black core, a thin bright rim shell, and an accretion disk
    bh = model("black_hole_core")
    sphere(bh, (8, 8, 8), 6.0, "mat_void")
    sphere(bh, (8, 8, 8), 6.8, "mat_glass_orange", glow=15, boxes=2)
    sphere(bh, (8, 8, 8), 5.8, "mat_void", boxes=3)
    seg = model("accretion_segment")
    seg.box([2.0, 7.6, 6.0], [14.0, 8.4, 10.0], "mat_glass_orange", glow=15, rot={"y": 0.0})
    disk = model("accretion_disk")
    ring(disk, (8, 8, 8), 14.0, 16, 4.0, 0.6, "y", "mat_glass_orange", glow=15)
    ring(disk, (8, 8, 8), 19.0, 16, 3.0, 0.4, "y", "mat_flame", glow=15, phase=11.25)
    # taiji disc, hex disc
    tj = model("taiji_disc")
    tj.cbox((8, 8, 8), (16.0, 0.8, 16.0), "taiji_64", glow=12, full=("up", "down"))
    ring(tj, (8, 8, 8), 8.4, 16, 1.0, 1.0, "y", "mat_gold", glow=10)
    hd = model("hex_disc")
    hd.cbox((8, 8, 8), (14.0, 0.8, 14.0), "hex_face", glow=12, full=("up", "down"))
    ring(hd, (8, 8, 8), 7.4, 12, 0.8, 1.0, "y", "mat_qi_shadow", glow=14)
    # time clock: face (front north) plus separate hands
    tc = model("time_clock")
    tc.cbox((8, 8, 8), (16.0, 16.0, 1.0), "clock_face", glow=12, full=("north", "south"))
    ring(tc, (8, 8, 8), 8.6, 16, 1.4, 1.6, "z", "mat_gold", glow=10)
    for a in range(0, 360, 90):
        p = (8 + 10.4 * math.cos(math.radians(a)), 8 + 10.4 * math.sin(math.radians(a)), 8)
        tc.cbox(p, (1.6, 1.6, 1.6), "mat_glow_gold", glow=15, rot={"z": 45, "origin": list(p)})
    hl = model("clock_hand")
    hl.box([7.4, 7.4, 6.6], [8.6, 15.4, 7.2], "mat_iron_black")
    hl.cbox((8, 8, 6.9), (1.6, 1.6, 0.6), "mat_gold")
    hs = model("clock_hand_short")
    hs.box([7.3, 7.4, 6.4], [8.7, 12.6, 7.0], "mat_iron_black")
    # six path wheel: rim, 6 spokes, 6 lit segment plates (front north)
    sw = model("six_path_wheel")
    ring(sw, (8, 8, 8), 12.0, 12, 1.6, 1.6, "z", "mat_gold", glow=8)
    for i, path in enumerate(SIX_PATHS):
        a = i * 60.0
        p = (8 + 6.0 * math.cos(math.radians(a)), 8 + 6.0 * math.sin(math.radians(a)), 8)
        sw.cbox(p, (12.0, 1.0, 1.0), "mat_gold", rot={"z": a, "origin": list(p)})
        q = (8 + 9.0 * math.cos(math.radians(a + 30)), 8 + 9.0 * math.sin(math.radians(a + 30)), 8)
        sw.cbox(q, (4.6, 3.0, 0.5), "mat_path_" + path, glow=15, rot={"z": a + 30 + 90, "origin": list(q)})
    sphere(sw, (8, 8, 8), 2.6, "mat_glow_gold", glow=15, boxes=2)
    # crescent moon (front north)
    cm = model("crescent_moon")
    ring(cm, (8, 8, 8), 9.0, 7, 2.6, 1.6, "z", "mat_moon", glow=15, phase=120.0, arc=200.0, length_scale=1.2)
    ring(cm, (8, 8, 8), 7.8, 5, 1.2, 1.2, "z", "mat_glow_gold", glow=15, phase=140.0, arc=160.0, length_scale=1.2)
    # nimbus cloud (somersault cloud) and a small cloud puff
    nc = model("nimbus_cloud")
    for (x, y, z, r) in ((8, 8, 8, 5.0), (2.0, 7.6, 9.0, 4.0), (14.0, 7.6, 7.0, 4.0), (5.0, 9.6, 3.0, 3.6), (11.0, 9.6, 13.0, 3.6),
                         (-3.0, 7.0, 10.0, 3.0), (19.0, 7.0, 6.0, 3.0)):
        sphere(nc, (x, y, z), r, "mat_cloud", glow=6, boxes=2)
    nc.box([0.0, 5.6, 4.0], [16.0, 6.2, 12.0], "mat_glow_gold", glow=15)
    nc.box([-2.0, 6.0, 7.4], [18.0, 6.4, 8.6], "mat_glow_gold", glow=15)
    cp = model("cloud_puff")
    for (x, y, z, r) in ((8, 8, 8, 3.4), (4.6, 7.4, 9.0, 2.6), (11.4, 7.6, 7.0, 2.6)):
        sphere(cp, (x, y, z), r, "mat_cloud", glow=6, boxes=2)
    # lotuses
    def lotus(mid, tex, inner, n_outer=8, n_inner=6, pod=None, size=1.0):
        lm = model(mid)
        for i in range(n_outer):
            petal(lm, (8, 8.0, 8), 9.0 * size, 5.0 * size, 0.7, i * 360.0 / n_outer, 55.0, tex, glow=10)
        for i in range(n_inner):
            petal(lm, (8, 8.4, 8), 7.0 * size, 4.0 * size, 0.6, i * 360.0 / n_inner + 30.0, 22.0, inner, glow=12, pieces=2)
        if pod:
            oct_prism(lm, (8, 10.0, 8), 2.6 * size, 2.4, "y", pod, glow=12)
        return lm
    lotus("lotus_pedestal", "mat_lotus_pink", "mat_lotus_white", pod="mat_gold")
    lotus("tang_lotus", "mat_jade", "mat_qi_life", pod="mat_glow_gold")
    lotus("fury_lotus", "mat_flame_blue", "mat_flame", pod="mat_flame_white")
    lotus("ice_lotus", "mat_ice_solid", "mat_ice", pod="mat_glow_white")
    lp = model("lotus_petal")
    petal(lp, (8, 8.0, 8), 14.0, 7.0, 0.8, 0.0, 15.0, "mat_lotus_pink", glow=10, pieces=3)
    lv = model("lotus_petal_violet")
    petal(lv, (8, 8.0, 8), 14.0, 7.0, 0.8, 0.0, 15.0, "mat_flame_violet", glow=15, pieces=3)
    # flame cart: two spoked wheels and a fire basket
    fc = model("flame_cart")
    for z in (2.0, 14.0):
        ring(fc, (8, 13.0, z), 5.0, 8, 0.8, 1.0, "x", "mat_wood")
        for a in (0, 45, 90, 135):
            fc.cbox((8, 13.0, z), (1.0, 9.6, 0.5), "mat_wood", rot={"x": a, "origin": [8, 13, z]})
    fc.box([7.4, 12.4, 2.0], [8.6, 13.6, 14.0], "mat_iron_black")
    fc.box([3.0, 15.0, 3.0], [13.0, 19.0, 13.0], "mat_bronze")
    fc.box([3.6, 19.0, 3.6], [12.4, 24.0, 12.4], "mat_flame", glow=15)
    fc.box([5.0, 24.0, 5.0], [11.0, 28.0, 11.0], "mat_flame_white", glow=15, rot={"y": 45.0, "origin": [8, 26, 8]})
    # ice mountain, mountain peaks (five elements) and a frozen block
    im = model("ice_mountain")
    for (x, z, w, h, a) in ((8, 8, 14, 14, 0), (3, 9, 8, 9, 20), (13, 7, 8, 10, -15), (8, 3, 7, 8, 35), (9, 13, 7, 8, -30)):
        im.box([x - w / 2, 8.0, z - w / 2], [x + w / 2, 8.0 + h, z + w / 2], "mat_ice_solid", glow=4, rot={"y": a, "origin": [x, 8, z]})
    im.cbox((8, 24.0, 8), (6.0, 6.0, 6.0), "mat_ice", glow=8, rot={"x": 45.0, "z": 45.0, "origin": [8, 24, 8]})
    im.cbox((3.0, 17.6, 9.0), (3.6, 3.6, 3.6), "mat_ice", glow=8, rot={"x": 45.0, "z": 45.0, "origin": [3, 17.6, 9]})
    im.cbox((13.0, 18.6, 7.0), (3.6, 3.6, 3.6), "mat_ice", glow=8, rot={"x": 45.0, "z": 45.0, "origin": [13, 18.6, 7]})
    ib = model("ice_block")
    ib.box([2.0, 8.0, 2.0], [14.0, 26.0, 14.0], "mat_ice", glow=6)
    ib.cbox((8, 26.0, 8), (8.0, 4.0, 8.0), "mat_ice_solid", glow=8, rot={"y": 45.0, "origin": [8, 26, 8]})
    peaks = (("metal", "mat_silver", "mat_glow_white"), ("wood", "mat_leaf", "mat_leaf_gold"), ("water", "mat_ice_solid", "mat_glow_white"),
             ("fire", "mat_lacquer_red", "mat_flame"), ("earth", "mat_stone", "mat_white_stone"))
    for i, (el, tex, cap) in enumerate(peaks):
        for mid in (["mountain_peak_" + el] + (["mountain_peak"] if el == "earth" else [])):
            mp = model(mid)
            mp.box([0.0, 8.0, 0.0], [16.0, 14.0, 16.0], tex)
            mp.box([2.0, 14.0, 1.0], [13.0, 20.0, 14.0], tex, rot={"y": 12.0, "origin": [8, 14, 8]})
            mp.box([4.0, 20.0, 4.0], [11.0, 26.0, 11.0], tex, rot={"y": -18.0, "origin": [8, 20, 8]})
            mp.box([5.6, 26.0, 5.6], [9.6, 30.0, 9.6], cap, glow=8, rot={"y": 30.0, "origin": [8, 26, 8]})
            mp.box([-2.0, 8.0, 4.0], [4.0, 12.0, 12.0], tex, rot={"y": 25.0, "origin": [1, 8, 8]})
            mp.box([12.0, 8.0, 3.0], [18.0, 13.0, 11.0], tex, rot={"y": -20.0, "origin": [15, 8, 7]})
    # pagoda tiers (seven glazed colours)
    glazes = ("mat_lacquer_red", "mat_cloth_yellow", "mat_qi_life", "mat_tiles_green", "mat_qi_ice", "mat_qi_space", "mat_purple_gold")
    for i, tex in enumerate(glazes, start=1):
        pt = model("pagoda_tier_%d" % i)
        pt.box([3.0, 8.0, 3.0], [13.0, 17.0, 13.0], tex)
        for (fx, fz, tx, tz) in ((5.4, 2.6, 10.6, 3.0), (5.4, 13.0, 10.6, 13.4), (2.6, 5.4, 3.0, 10.6), (13.0, 5.4, 13.4, 10.6)):
            pt.box([fx, 10.0, fz], [tx, 15.4, tz], "mat_gold", glow=10)
        pt.box([3.4, 8.0, 3.4], [12.6, 8.8, 12.6], "mat_white_stone")
        pt.box([0.0, 17.0, 0.0], [16.0, 18.4, 16.0], "mat_tiles_gold")
        pt.box([2.0, 18.4, 2.0], [14.0, 19.4, 14.0], "mat_tiles_gold")
        for x, z, rx, rz in ((0.0, 8.0, 0.0, 22.5), (16.0, 8.0, 0.0, -22.5), (8.0, 0.0, -22.5, 0.0), (8.0, 16.0, 22.5, 0.0)):
            w = (16.0, 1.0, 4.0) if rx else (4.0, 1.0, 16.0)
            pt.cbox((x, 17.4, z), w, "mat_tiles_gold", rot={"x": rx, "z": rz, "origin": [x, 17.4, z]})
        for x, z in ((0.0, 0.0), (16.0, 0.0), (0.0, 16.0), (16.0, 16.0)):
            pt.cbox((x, 18.0, z), (1.4, 1.4, 1.4), "mat_gold", glow=10, rot={"y": 45.0, "origin": [x, 18, z]})
        if i == 7:
            oct_prism(pt, (8, 23.0, 8), 1.2, 7.0, "y", "mat_gold", glow=12)
            sphere(pt, (8, 27.6, 8), 1.6, "mat_glow_gold", glow=15, boxes=2)
    model_alias("pagoda_tier", "pagoda_tier_1")
    # stupa
    su = model("stupa")
    su.box([1.0, 8.0, 1.0], [15.0, 10.0, 15.0], "mat_white_stone")
    su.box([2.6, 10.0, 2.6], [13.4, 12.0, 13.4], "mat_white_stone")
    sphere(su, (8, 16.0, 8), 5.4, "mat_white_stone")
    su.box([5.4, 20.0, 5.4], [10.6, 22.0, 10.6], "mat_gold", glow=8)
    oct_prism(su, (8, 26.0, 8), 1.0, 8.0, "y", "mat_gold", glow=8)
    for y in (23.4, 25.6, 27.8):
        oct_prism(su, (8, y, 8), 2.2 - (y - 23.4) * 0.25, 0.6, "y", "mat_gold", glow=10)
    sphere(su, (8, 30.6, 8), 1.2, "mat_glow_gold", glow=15, boxes=2)
    # qilin palace
    qp = model("qilin_palace")
    qp.box([-6.0, 8.0, -4.0], [22.0, 9.6, 20.0], "mat_white_stone")
    for x in (-4.0, 4.0, 12.0, 20.0):
        oct_prism(qp, (x, 15.6, -2.0), 0.9, 12.0, "y", "mat_lacquer_red")
        oct_prism(qp, (x, 15.6, 18.0), 0.9, 12.0, "y", "mat_lacquer_red")
    qp.box([-8.0, 21.6, -6.0], [24.0, 23.2, 22.0], "mat_tiles_gold")
    qp.box([-6.0, 23.2, -4.0], [22.0, 25.6, 20.0], "mat_tiles_gold")
    qp.box([-2.0, 25.6, 2.0], [18.0, 27.6, 14.0], "mat_tiles_gold")
    qp.box([3.0, 18.4, -2.8], [13.0, 21.0, -2.2], "mat_glow_gold", glow=12)
    for x, z in ((-8.0, -6.0), (24.0, -6.0), (-8.0, 22.0), (24.0, 22.0)):
        qp.cbox((x, 23.0, z), (2.0, 1.0, 2.0), "mat_gold", rot={"x": 20.0 if z < 0 else -20.0, "origin": [x, 23, z]})
    # bodhi tree: flared trunk, roots, three branch tiers, leaf clumps, a glowing heart-fruit
    bt = model("bodhi_tree")
    bt.box([6.0, 8.0, 6.0], [10.0, 18.0, 10.0], "mat_wood")
    bt.box([6.6, 18.0, 6.6], [9.4, 24.0, 9.4], "mat_wood")
    for a in (0, 90, 180, 270):
        bt.box([7.0, 8.0, 9.0], [9.0, 10.0, 14.0], "mat_wood", rot={"y": a, "x": 20.0, "origin": [8, 9, 8]})
    for i, (y, L) in enumerate(((16.0, 9.0), (20.0, 8.0), (23.0, 6.0))):
        for a in (30 + i * 40, 150 + i * 40, 270 + i * 40):
            bt.box([7.4, y, 8.0], [8.6, y + 1.2, 8.0 + L], "mat_wood", rot={"y": a, "x": -25.0, "origin": [8, y, 8]})
    for (x, y, z, r) in ((8, 27.0, 8, 6.0), (0.0, 23.0, 6.0, 5.0), (16.0, 23.4, 10.0, 5.0), (5.0, 25.0, 15.0, 4.6), (11.0, 25.0, 1.0, 4.6),
                         (-4.0, 20.0, 12.0, 3.6), (20.0, 20.0, 3.0, 3.6)):
        sphere(bt, (x, y, z), r, "mat_leaf", boxes=2)
    sphere(bt, (8, 21.0, 4.6), 1.6, "mat_glow_gold", glow=15, boxes=2)
    # willow god: a trunk with drooping branch chains, charred and green
    wg = model("willow_god")
    wg.box([5.0, 8.0, 5.0], [11.0, 22.0, 11.0], "mat_charred")
    wg.box([6.0, 22.0, 6.0], [10.0, 26.0, 10.0], "mat_charred")
    for i in range(8):
        a = i * 45.0
        wg.cbox((8, 25.0, 8 + 4.5), (1.0, 1.0, 9.0), "mat_charred", rot={"x": -20.0, "y": 90.0 - a, "origin": [8, 25, 8]})
        q = (8 + 9.0 * math.cos(math.radians(a)), 18.0, 8 + 9.0 * math.sin(math.radians(a)))
        wg.cbox(q, (0.8, 12.0, 0.8), "mat_leaf", glow=4)
        wg.cbox((q[0], q[1] - 3.0, q[2]), (2.0, 4.0, 0.4), "mat_leaf_gold", glow=8, rot={"y": -a, "origin": [q[0], q[1], q[2]]})
    wl = model("willow_leaf")
    wl.box([6.0, 8.0, 7.8], [10.0, 22.0, 8.2], "mat_leaf_gold", glow=12)
    wl.box([7.0, 22.0, 7.8], [9.0, 25.0, 8.2], "mat_leaf_gold", glow=12)
    # blue silver grass: five thin rotated blades rising from a base
    bg = model("blue_silver_grass")
    bg.box([5.0, 8.0, 5.0], [11.0, 9.0, 11.0], "mat_blue_silver")
    for i in range(5):
        a = i * 72.0
        bg.box([7.4, 8.0, 7.6], [8.6, 28.0 - 3.0 * (i % 2), 8.4], "mat_blue_silver", glow=6,
               rot={"x": 14.0 + 6.0 * (i % 2), "y": a, "origin": [8, 8, 8]})
    # straw figure with 落魂 talisman plates (glyph plates read as talismans)
    sf = model("straw_figure")
    sf.box([5.0, 14.0, 6.0], [11.0, 22.0, 10.0], "mat_straw")
    sf.box([5.6, 22.0, 6.6], [10.4, 27.0, 9.4], "mat_straw")
    sf.box([1.0, 19.0, 7.0], [5.0, 20.6, 9.0], "mat_straw")
    sf.box([11.0, 19.0, 7.0], [15.0, 20.6, 9.0], "mat_straw")
    sf.box([5.4, 8.0, 7.0], [7.2, 14.0, 9.0], "mat_straw")
    sf.box([8.8, 8.0, 7.0], [10.6, 14.0, 9.0], "mat_straw")
    sf.box([5.6, 15.0, 5.6], [10.4, 21.0, 5.9], "glyph_ling", full=("north",))
    sf.box([6.0, 22.6, 6.2], [10.0, 26.4, 6.5], "glyph_jin", full=("north",))
    sf.box([4.8, 18.0, 5.8], [11.2, 18.8, 10.2], "mat_cloth_red")
    # soul flag: pole, black cloth in three angled plates, soul-blue glyph
    fl = model("soul_flag")
    fl.box([7.4, 8.0, 7.4], [8.6, 32.0, 8.6], "mat_iron_black")
    fl.cbox((8, 31.0, 8), (2.0, 2.0, 2.0), "mat_soul_blue", glow=15, rot={"y": 45.0, "origin": [8, 31, 8]})
    for i in range(3):
        fl.box([8.6 + i * 4.0, 14.0, 7.8], [12.8 + i * 4.0, 29.0 - i * 1.5, 8.2], "mat_cloth_black",
               rot={"y": -8.0 * (i + 1), "origin": [8.6 + i * 4.0, 20, 8]})
    fl.box([10.0, 18.0, 7.6], [16.0, 26.0, 7.8], "rune_space_glow", glow=15, full=("north", "south"))
    # formation banners (red / white / black / violet)
    for mid, tex in (("formation_banner", "mat_cloth_red"), ("formation_banner_red", "mat_cloth_red"),
                     ("formation_banner_white", "mat_cloth_white"), ("formation_banner_black", "mat_cloth_black"),
                     ("formation_banner_violet", "mat_qi_space")):
        fb = model(mid)
        fb.box([7.5, 8.0, 7.5], [8.5, 30.4, 8.5], "mat_wood")
        fb.box([8.5, 18.0, 7.8], [17.0, 30.0, 8.2], tex, glow=4)
        fb.box([8.5, 29.6, 7.6], [17.0, 30.6, 8.4], "mat_gold")
        fb.cbox((8, 31.0, 8), (1.6, 1.6, 1.6), "mat_gold", glow=10, rot={"y": 45.0, "origin": [8, 31, 8]})
        for i in range(3):
            fb.box([9.0 + i * 2.8, 16.0, 7.9], [10.6 + i * 2.8, 18.0, 8.1], tex, glow=4)
    # yin tiger seal: black iron crouched tiger amulet with red eyes
    yt = model("yin_tiger_seal")
    yt.box([4.0, 8.0, 3.0], [12.0, 11.0, 13.0], "mat_iron_black")
    yt.box([5.0, 11.0, 4.0], [11.0, 14.0, 12.0], "mat_iron_black")
    yt.box([5.4, 11.0, 0.6], [10.6, 16.0, 5.0], "mat_iron_black")
    yt.box([6.0, 14.0, 0.4], [7.2, 14.8, 0.6], "mat_qi_fire", glow=15)
    yt.box([8.8, 14.0, 0.4], [10.0, 14.8, 0.6], "mat_qi_fire", glow=15)
    yt.box([7.4, 9.0, 13.0], [8.6, 10.4, 18.0], "mat_iron_black", rot={"x": -30.0, "origin": [8, 9.7, 13]})
    yt.box([3.6, 8.0, 2.6], [12.4, 8.6, 13.4], "mat_gold", glow=6)
    # talisman 令, thunder 雷 and space symbols, restriction seal 禁, feng ring 封
    tl = model("talisman_ling")
    tl.cbox((8, 8, 8), (8.0, 16.0, 0.3), "glyph_ling", full=("north", "south"), glow=8)
    t2 = model("thunder_ancestral_symbol")
    t2.cbox((8, 8, 8), (16.0, 16.0, 0.6), "rune_lei_glow", glow=15, full=("north", "south"))
    for (cx, cy, sx, sy) in ((8, -0.6, 18.0, 1.4), (8, 16.6, 18.0, 1.4), (-0.6, 8, 1.4, 18.0), (16.6, 8, 1.4, 18.0)):
        t2.cbox((cx, cy, 8), (sx, sy, 1.4), "mat_thunder", glow=15)
    t3 = model("space_symbol")
    t3.cbox((8, 8, 8), (16.0, 16.0, 0.6), "rune_space_glow", glow=15, full=("north", "south"))
    ring(t3, (8, 8, 8), 11.0, 16, 1.0, 1.0, "z", "mat_space", glow=15)
    rs = model("restriction_seal")
    rs.cbox((8, 8, 8), (16.0, 16.0, 0.4), "glyph_jin", glow=15, full=("north", "south"))
    rf = model("seal_ring_feng")
    ring(rf, (8, 8, 8), 9.0, 16, 2.0, 0.5, "y", "mat_qi_fire", glow=15)
    rf.cbox((8, 8, 8), (8.0, 0.4, 8.0), "glyph_feng", glow=15, full=("up", "down"))
    # glyph rings per Dao (accent over particle rune circles)
    for dao in list(DAO_RAMP) + ["default"]:
        mid = "glyph_ring" if dao == "default" else "glyph_ring_" + dao
        gr = model(mid)
        tex = "mat_qi_gold" if dao == "default" else "mat_qi_" + dao
        ring(gr, (8, 8, 8), 15.0, 16, 1.6, 0.3, "y", tex, glow=15)
        ring(gr, (8, 8, 8), 11.0, 12, 0.6, 0.3, "y", tex, glow=15, phase=15.0)
        for i in range(8):
            a = i * 45.0
            p = (8 + 13.0 * math.cos(math.radians(a)), 8, 8 + 13.0 * math.sin(math.radians(a)))
            gr.cbox(p, (2.2, 0.4, 2.2), "rune_band", glow=15, rot={"y": -a, "origin": list(p)})
    # golden hair strands (three curls)
    gh = model("golden_hair")
    for i in range(3):
        # one continuous curl: a rising spiral of short segments, each laid along the step to the next point
        pts = []
        for j in range(8):
            ang = math.radians(i * 120.0 + j * 38.0)
            rad = 1.2 + j * 0.55
            pts.append((8 + rad * math.cos(ang), 4.0 + j * 1.3, 8 + rad * math.sin(ang)))
        for j in range(7):
            a0, b0 = pts[j], pts[j + 1]
            mid = tuple((a0[k] + b0[k]) / 2 for k in range(3))
            dx, dy, dz = b0[0] - a0[0], b0[1] - a0[1], b0[2] - a0[2]
            length = math.sqrt(dx * dx + dy * dy + dz * dz) + 0.35
            yaw = math.degrees(math.atan2(dx, dz))
            pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
            gh.cbox(mid, (0.45, 0.45, length), "mat_hair_gold", glow=12, rot={"x": pitch, "y": yaw, "origin": list(mid)})
    # fusion orb: two interlocked spheres (the client tints with two displays when needed)
    fo = model("fusion_orb")
    sphere(fo, (6.0, 8, 8), 4.0, "mat_qi_fire", glow=15)
    sphere(fo, (10.0, 8, 8), 4.0, "mat_qi_ice", glow=15)
    ring(fo, (8, 8, 8), 6.4, 12, 0.5, 0.5, "x", "mat_glow_white", glow=15)
    # heavenly flame orbs (lotus-flame shape) in five colours and the flame seed
    for mid, tex in (("heavenly_flame_orb", "mat_flame"), ("heavenly_flame_orb_red", "mat_flame"), ("heavenly_flame_orb_blue", "mat_flame_blue"),
                     ("heavenly_flame_orb_green", "mat_flame_green"), ("heavenly_flame_orb_violet", "mat_flame_violet"),
                     ("heavenly_flame_orb_white", "mat_flame_white")):
        ho = model(mid)
        sphere(ho, (8, 8, 8), 2.0, "mat_glow_white", glow=15, boxes=2)
        for a in (0, 120, 240):
            petal(ho, (8, 6.0, 8), 9.0, 4.0, 0.6, a, 20.0, tex, glow=15, pieces=3)
    hs2 = model("heavenly_flame_seed")
    sphere(hs2, (8, 7.0, 8), 3.4, "mat_flame", glow=15)
    hs2.cbox((8, 11.4, 8), (2.2, 4.0, 2.2), "mat_flame_white", glow=15, rot={"y": 45.0, "origin": [8, 11.4, 8]})
    sphere(hs2, (8, 7.0, 8), 4.0, "mat_ice", glow=8, boxes=2)
    # chain links (gold, iron, karmic red)
    for mid, tex in (("chain_link", "mat_gold"), ("chain_link_gold", "mat_gold"), ("chain_link_iron", "mat_iron_black"), ("chain_link_red", "mat_qi_fire")):
        cl = model(mid)
        ring(cl, (8, 8, 4.0), 3.0, 8, 1.0, 1.0, "x", tex, glow=8 if tex != "mat_iron_black" else 0, length_scale=1.15)
        ring(cl, (8, 8, 11.0), 3.0, 8, 1.0, 1.0, "y", tex, glow=8 if tex != "mat_iron_black" else 0, length_scale=1.15)
    # duty props
    cpb = model("carrying_pole_buckets")
    cpb.box([-14.0, 7.4, 7.4], [30.0, 8.6, 8.6], "mat_wood")
    for x in (-11.0, 27.0):
        cpb.box([x - 0.3, 0.0, 7.7], [x + 0.3, 7.4, 8.3], "mat_straw")
        oct_prism(cpb, (x, -3.0, 8), 3.0, 6.0, "y", "mat_wood")
        cpb.box([x - 2.6, -0.4, 5.4], [x + 2.6, 0.0, 10.6], "mat_qi_ice", glow=6, skip=("down",))
    wk = model("wok_ladle")
    wk.box([7.5, -6.0, 7.5], [8.5, 14.0, 8.5], "mat_wood")
    sphere(wk, (8, -8.0, 8), 2.4, "mat_iron_black", boxes=2)
    fbk = model("feed_basket")
    oct_prism(fbk, (8, 8, 8), 5.0, 6.0, "y", "mat_straw")
    fbk.box([4.0, 10.6, 4.0], [12.0, 11.0, 12.0], "mat_cloth_yellow", skip=("down",))
    ring(fbk, (8, 13.0, 8), 4.6, 8, 0.6, 0.8, "x", "mat_straw")
    hbk = model("herb_basket")
    oct_prism(hbk, (8, 8, 8), 4.4, 5.0, "y", "mat_straw")
    for (x, z) in ((6.0, 6.0), (10.0, 7.0), (8.0, 10.0)):
        hbk.box([x - 1.0, 10.4, z - 0.2], [x + 1.0, 14.0, z + 0.2], "mat_leaf", rot={"y": x * 20, "origin": [x, 10.4, z]})
    sk = model("sickle")
    sk.box([7.5, 0.0, 7.5], [8.5, 10.0, 8.5], "mat_wood")
    ring(sk, (11.0, 12.0, 8), 3.4, 5, 0.8, 0.4, "z", "mat_blade_steel", phase=90.0, arc=180.0)
    # the fallback cube (unknown ids)
    ms2 = model("missing")
    ms2.cbox((8, 8, 8), (8.0, 8.0, 8.0), "mat_qi_gold", glow=12)
    ms2.cbox((8, 8, 8), (7.6, 7.6, 7.6), "mat_qi_gold", glow=12, rot={"y": 45.0, "x": 35.0, "origin": [8, 8, 8]})


ALIASES = {}  # id -> target model id


def model_alias(alias, target):
    ALIASES[alias] = target


# ================================================================================================ output and checks

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def model_json(m):
    tex = {key: "%s:item/fx/%s" % (NS, name) for name, key in m.tex.items()}
    first = next(iter(m.tex)) if m.tex else "mat_gold"
    tex["particle"] = "%s:item/fx/%s" % (NS, first)
    return {"textures": tex, "elements": m.els}


def validate(m):
    problems = []
    if len(m.els) > 40:
        problems.append("%d elements (max 40)" % len(m.els))
    for i, el in enumerate(m.els):
        for k in ("from", "to"):
            for v in el[k]:
                if v < -16.0 or v > 32.0:
                    problems.append("element %d %s %s out of -16..32" % (i, k, el[k]))
        for a in range(3):
            if el["from"][a] > el["to"][a]:
                problems.append("element %d from > to" % i)
        le = el.get("light_emission", 0)
        if not (0 <= le <= 15):
            problems.append("element %d light_emission %s" % (i, le))
        if not el["faces"]:
            problems.append("element %d has no faces" % i)
        for face in el["faces"].values():
            uv = face["uv"]
            if any(v < 0 or v > 16 for v in uv):
                problems.append("element %d uv %s" % (i, uv))
    for name in m.tex:
        if name not in TEXTURES:
            problems.append("texture %s missing" % name)
    return problems


def main():
    build_materials()
    build_sheets()
    build_weapons()
    build_hands()
    build_beasts()
    build_forms()
    build_treasures()

    os.makedirs(TEX_DIR, exist_ok=True)
    used = set()
    errors = 0
    for mid, m in sorted(MODELS.items()):
        problems = validate(m)
        if problems:
            errors += 1
            print("MODEL %s:" % mid)
            for p in problems:
                print("   ", p)
        used.update(m.tex)
        write_json(os.path.join(MODEL_DIR, mid + ".json"), model_json(m))
        write_json(os.path.join(ITEM_DIR, mid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/fx/%s" % (NS, mid)}})
    for alias, target in sorted(ALIASES.items()):
        if target not in MODELS:
            errors += 1
            print("ALIAS %s -> unknown %s" % (alias, target))
        write_json(os.path.join(ITEM_DIR, alias + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/fx/%s" % (NS, target)}})
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    # the disciple natal treasures: their old item ids now point at the 3D models
    natal = {"natal_parasol": "natal_parasol", "natal_parasol_gold": "natal_parasol_gold", "natal_wok": "natal_wok",
             "natal_wok_gold": "natal_wok_gold"}
    for item, target in natal.items():
        write_json(os.path.join(ROOT, "models", "item", item + ".json"), {"parent": "%s:item/fx/%s" % (NS, target)})
    ids = sorted(list(MODELS) + list(ALIASES))
    with open(os.path.join(os.path.dirname(__file__), "fx_model_ids.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(ids) + "\n")
    print("models: %d, aliases: %d, textures: %d, errors: %d" % (len(MODELS), len(ALIASES), len(used), errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
