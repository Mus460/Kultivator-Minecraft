"""Generates the textures of module C, disciple powers (16x16 pixel art, plus one 64x64 entity texture).

heavenly_flame_seed_<kind>.png (5): Benih Api Surgawi, a teardrop seed of living flame inside a thin crystal skin, in
the colours of its Heavenly Flame (BTTH): green_lotus sits on a small green lotus, bone_chilling is a pale cold flame
with a bone-white rim, sea_heart is deep blue over a wave, fallen_heart is an orange-red flame with a dark heart, and
pure_lotus is a violet-black demon flame on a dark lotus.

natal_parasol.png / natal_parasol_gold.png: the natal parasol (A Will Eternal's Eternal Parasol), an open canopy with
ribs on a long handle; the gold one is the treasure after nine folds.

natal_wok.png / natal_wok_gold.png: Bai Xiaochun's turtle-shell wok seen from behind, black iron with a hexagon shell
pattern and two handles; the gold one after nine folds.

textures/entity/robe_rank_trim.png: the robe trims of textures/entity/robe_trim.png turned to greyscale (brightest =
white), so the disciple renderer can tint them with the sect rank's colour.

Run with: python tools/gen_dpower_textures.py
"""
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
ITEM = os.path.join(ROOT, "textures", "item")
ENTITY = os.path.join(ROOT, "textures", "entity")


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (255,)


# ---------------------------------------------------------------------------------------------- flame seeds

FLAMES = {
    # core (bright), body, edge (dark), base ornament colours
    "green_lotus": ((230, 255, 200), (90, 220, 110), (20, 90, 40), "lotus", (70, 170, 80), (30, 100, 50)),
    "bone_chilling": ((255, 255, 255), (190, 235, 255), (90, 130, 170), "bone", (235, 228, 205), (150, 140, 120)),
    "sea_heart": ((200, 240, 255), (60, 140, 255), (15, 40, 120), "wave", (40, 110, 220), (20, 60, 150)),
    "fallen_heart": ((255, 245, 180), (255, 120, 40), (130, 30, 10), "heart", (200, 30, 45), (120, 25, 10)),
    "pure_lotus": ((240, 200, 255), (170, 70, 240), (40, 10, 60), "lotus", (80, 30, 110), (35, 10, 50)),
}


def flame_mask(x, y):
    """Teardrop: 0 outside, else a 0..1 'heat' (1 = hottest core)."""
    cx = 7.5
    # Height 3..13: tip at the top, round belly at y ~ 10.
    if y < 2 or y > 13:
        return 0.0
    t = (y - 2) / 11.0  # 0 at tip, 1 at bottom
    half = 0.6 + 5.0 * (t ** 0.8) * (1.0 - max(0.0, t - 0.8) * 2.2)
    dx = abs(x - cx)
    if dx > half:
        return 0.0
    edge = 1.0 - dx / max(0.5, half)
    vertical = 1.0 - abs(t - 0.7) * 1.3
    return max(0.05, min(1.0, edge * 0.7 + vertical * 0.5))


def flame_seed(kind):
    core, body, dark, ornament, orn_light, orn_dark = FLAMES[kind]
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            heat = flame_mask(x, y)
            if heat <= 0.0:
                continue
            # The crystal skin: outline pixels darker.
            border = flame_mask(x - 1, y) <= 0 or flame_mask(x + 1, y) <= 0 or flame_mask(x, y - 1) <= 0 or flame_mask(x, y + 1) <= 0
            if border:
                img.set(x, y, mix(dark, body, 0.25))
            elif heat > 0.78:
                img.set(x, y, mix(body, core, (heat - 0.78) / 0.22))
            else:
                img.set(x, y, mix(dark, body, heat / 0.78))
    # A glint on the crystal.
    img.blend(6, 6, (255, 255, 255, 170))
    img.blend(6, 7, (255, 255, 255, 90))
    # The base ornament (rows 12-15).
    if ornament == "lotus":
        for x, y in [(4, 13), (5, 12), (5, 13), (6, 13), (9, 13), (10, 12), (10, 13), (11, 13), (7, 14), (8, 14), (6, 14), (9, 14)]:
            img.set(x, y, orn_light)
        for x in range(4, 12):
            img.set(x, 15, orn_dark)
        img.set(3, 14, orn_dark)
        img.set(12, 14, orn_dark)
    elif ornament == "bone":
        for x in range(4, 12):
            img.set(x, 14, orn_light)
        for x in (3, 12):
            img.set(x, 13, orn_light)
            img.set(x, 15, orn_dark)
        img.set(3, 14, orn_dark)
        img.set(12, 14, orn_dark)
    elif ornament == "wave":
        for x in range(3, 13):
            y = 14 if (x // 2) % 2 == 0 else 15
            img.set(x, y, orn_light)
            img.set(x, 15 if y == 14 else 14, orn_dark)
    elif ornament == "heart":
        # A small crimson heart in the flame (the Fallen Heart Flame).
        heart = [(6, 8), (9, 8), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10), (7, 11), (8, 11)]
        for x, y in heart:
            img.set(x, y, orn_light)
        img.set(6, 9, (255, 140, 120, 255))
        for x in range(5, 11):
            img.set(x, 14, orn_dark)
    return img


# ---------------------------------------------------------------------------------------------- natal parasol

PARASOL = [
    "................",
    ".......y........",
    ".....CCcCC......",
    "...CCcCCCcCC....",
    "..CcCCCcCCCcC...",
    ".CcCCCCcCCCCcC..",
    ".ddddddddddddd..",
    "..r...r.r...r...",
    ".......H........",
    ".......H........",
    ".......H........",
    ".......H........",
    ".......H........",
    ".......H........",
    "......hH........",
    "......h.........",
]


def parasol(gold):
    canopy = (220, 180, 60) if gold else (170, 40, 40)
    canopy_light = (255, 230, 130) if gold else (220, 80, 70)
    rib = (255, 250, 220) if gold else (200, 205, 215)
    trim = (140, 100, 20) if gold else (90, 20, 25)
    handle = (180, 130, 40) if gold else (110, 70, 40)
    handle_dark = (110, 70, 20) if gold else (60, 35, 20)
    palette = {
        "C": canopy + (255,), "c": rib + (255,), "d": trim + (255,), "r": rib + (255,), "y": canopy_light + (255,),
        "H": handle + (255,), "h": handle_dark + (255,),
    }
    img = Image(16, 16)
    for y, row in enumerate(PARASOL):
        for x, ch in enumerate(row):
            if ch in palette:
                img.set(x, y, palette[ch])
    # Light on the upper canopy.
    for x, y in [(5, 3), (6, 3), (4, 4), (5, 4)]:
        img.blend(x, y, canopy_light + (150,))
    return img


# ---------------------------------------------------------------------------------------------- natal wok

def line(img, x0, y0, x1, y1, color, inside=lambda x, y: True):
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        if inside(x, y):
            img.set(x, y, color)


def wok(gold):
    shell = (60, 60, 66) if not gold else (205, 160, 50)
    shell_light = (110, 112, 120) if not gold else (255, 225, 120)
    seam = (25, 25, 30) if not gold else (130, 90, 20)
    rim = (35, 35, 40) if not gold else (160, 110, 25)
    img = Image(16, 16)
    cx, cy, r = 7.5, 8.0, 6.6
    for y in range(16):
        for x in range(16):
            dx = x - cx
            dy = (y - cy) * 1.12
            d = (dx * dx + dy * dy) ** 0.5
            if d > r:
                continue
            if d > r - 1.1:
                img.set(x, y, rim + (255,))
                continue
            light = max(0.0, 1.0 - ((x - 5.0) ** 2 + (y - 5.0) ** 2) ** 0.5 / 9.0)
            img.set(x, y, mix(shell, shell_light, light * 0.8))
    # Hexagon shell plates: a central hexagon and seams from its corners to the rim.
    corners = [(5.0, 8.0), (6.5, 5.5), (8.5, 5.5), (10.0, 8.0), (8.5, 10.5), (6.5, 10.5)]
    for i, (ax, ay) in enumerate(corners):
        bx, by = corners[(i + 1) % len(corners)]
        line(img, ax, ay, bx, by, seam + (255,))
        ox = cx + (ax - cx) * 2.1
        oy = cy + (ay - cy) * 2.1
        line(img, ax, ay, ox, oy, seam + (255,), lambda x, y: ((x - cx) ** 2 + ((y - cy) * 1.12) ** 2) ** 0.5 < r - 1.1)
    # Two handles.
    for x, y in [(0, 7), (0, 8), (1, 7), (15, 7), (15, 8), (14, 7)]:
        img.set(x, y, rim + (255,))
    return img


# ---------------------------------------------------------------------------------------------- rank trim

def read_png(path):
    """Reads an 8-bit RGBA PNG written with filter 0 (as tools/png.py writes them)."""
    data = open(path, "rb").read()
    pos = 8
    width = height = 0
    idat = b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height = struct.unpack(">II", body[:8])
            if body[8] != 8 or body[9] != 6:
                raise ValueError("only 8-bit RGBA")
        elif kind == b"IDAT":
            idat += body
    raw = zlib.decompress(idat)
    stride = width * 4 + 1
    img = Image(width, height)
    for y in range(height):
        if raw[y * stride] != 0:
            raise ValueError("filtered PNG rows are not supported")
        for x in range(width):
            o = y * stride + 1 + x * 4
            img.set(x, y, tuple(raw[o:o + 4]))
    return img


def rank_trim():
    source = read_png(os.path.join(ENTITY, "robe_trim.png"))
    brightest = 1.0
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = source.get(x, y)
            if a > 0:
                brightest = max(brightest, 0.299 * r + 0.587 * g + 0.114 * b)
    out = Image(source.width, source.height)
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = source.get(x, y)
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / brightest
            v = int(round(min(1.0, lum) * 255))
            out.set(x, y, (v, v, v, a))
    return out


def main():
    os.makedirs(ITEM, exist_ok=True)
    for kind in FLAMES:
        flame_seed(kind).save(os.path.join(ITEM, "heavenly_flame_seed_" + kind + ".png"))
    parasol(False).save(os.path.join(ITEM, "natal_parasol.png"))
    parasol(True).save(os.path.join(ITEM, "natal_parasol_gold.png"))
    wok(False).save(os.path.join(ITEM, "natal_wok.png"))
    wok(True).save(os.path.join(ITEM, "natal_wok_gold.png"))
    rank_trim().save(os.path.join(ENTITY, "robe_rank_trim.png"))
    print("dpower textures written")


if __name__ == "__main__":
    main()
