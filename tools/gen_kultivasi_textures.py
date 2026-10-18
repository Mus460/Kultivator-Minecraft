"""Generates the item textures of phase-7 module P7-kultivasi (16x16 pixel art) and the vein compass models.

  blank_talisman.png              Kertas Jimat Kosong: a tall strip of yellow talisman paper with a faint red border
  thunder_talisman.png ...        the eight talismans: the same paper strip with a red seal at the bottom and a glyph in
                                  the talisman's colour (thunder zigzag, fire cloud, shield, chain links, homeward circle,
                                  warded bolt, wind swirls, a fading eye)
  vein_compass_00..31.png         Kompas Urat Roh: a round jade disc with a gold rim, four gold ticks and a gold-and-jade
                                  needle at 32 angles (index 16 points up, angles clockwise like the vanilla compass)
  models/item/vein_compass_XX     one generated model per needle angle

Run with: python tools/gen_kultivasi_textures.py
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
ITEM = os.path.join(ROOT, "textures", "item")
MODELS = os.path.join(ROOT, "models", "item")


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def noise(x, y, seed):
    """Deterministic 0..1 noise per pixel."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


PAPER_LIGHT = (252, 236, 168)
PAPER = (238, 210, 122)
PAPER_DARK = (196, 160, 80)
PAPER_EDGE = (150, 112, 50)
RED = (196, 34, 30)
RED_DARK = (120, 18, 18)
RED_LIGHT = (232, 84, 64)
GOLD_LIGHT = (255, 236, 150)
GOLD = (226, 178, 64)
GOLD_DARK = (140, 100, 28)
JADE_PALE = (196, 244, 214)
JADE_LIGHT = (140, 222, 176)
JADE = (78, 180, 132)
JADE_DARK = (34, 110, 80)
JADE_DEEP = (18, 60, 44)

# Paper strip bounds (inclusive).
X0, X1 = 4, 11
Y0, Y1 = 1, 14


def paper(seed, border_alpha=0):
    img = Image(16, 16)
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            t = (x - X0) / (X1 - X0)
            c = mix(PAPER_LIGHT, PAPER, 0.2 + 0.6 * t)
            if noise(x, y, seed) > 0.85:
                c = mix(c, PAPER_DARK, 0.35)
            img.set(x, y, c)
        img.set(X0 - 1, y, PAPER_EDGE + (200,))
        img.set(X1 + 1, y, PAPER_EDGE + (230,))
    for x in range(X0, X1 + 1):
        img.set(x, Y0 - 1, PAPER_EDGE + (190,))
        img.set(x, Y1 + 1, PAPER_EDGE + (230,))
    # A slightly curled bottom-right corner and a bright top-left corner.
    img.set(X1, Y1, PAPER_DARK)
    img.set(X0, Y0, mix(PAPER_LIGHT, (255, 255, 255), 0.4))
    if border_alpha:
        for y in range(Y0 + 1, Y1):
            img.blend(X0 + 1, y, RED + (border_alpha,))
            img.blend(X1 - 1, y, RED + (border_alpha,))
        for x in range(X0 + 1, X1):
            img.blend(x, Y0 + 1, RED + (border_alpha,))
            img.blend(x, Y1 - 1, RED + (border_alpha,))
    return img


def seal(img):
    """The red seal stamp near the bottom of the strip."""
    for y in (11, 12, 13):
        for x in (6, 7, 8, 9):
            c = RED if (x + y) % 3 else RED_DARK
            img.set(x, y, c)
    img.set(6, 11, RED_LIGHT)
    img.set(7, 12, mix(RED_LIGHT, PAPER_LIGHT, 0.4))
    img.set(8, 12, RED_DARK)


def strokes(img, points, color, shade=None):
    for (x, y) in points:
        img.set(x, y, color)
        if shade is not None:
            img.blend(x + 1, y + 1, shade + (110,))


def thunder(img):
    color = (150, 80, 230)
    light = (210, 170, 255)
    pts = [(8, 2), (7, 3), (6, 4), (7, 5), (8, 5), (9, 6), (8, 7), (7, 8), (6, 9)]
    strokes(img, pts, color, (70, 30, 120))
    img.set(8, 2, light)
    img.set(9, 6, light)
    # Two short red strokes either side (the inked rune).
    strokes(img, [(5, 3), (10, 7)], RED)


def fire_cloud(img):
    flame = (240, 110, 40)
    core = (255, 200, 90)
    pts = [(7, 2), (8, 3), (6, 4), (7, 4), (8, 4), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6), (5, 7), (10, 7)]
    strokes(img, pts, flame, (130, 50, 20))
    for p in [(7, 5), (8, 5), (7, 7), (8, 7)]:
        img.set(p[0], p[1], core)
    # Cloud curls below the flame.
    strokes(img, [(5, 9), (6, 8), (7, 9), (8, 8), (9, 9), (10, 8)], RED)


def shield(img):
    gold = GOLD
    pts = []
    for y in range(3, 9):
        half = 2 if y < 7 else (1 if y == 7 else 0)
        pts.append((7 - half, y))
        pts.append((8 + half, y))
    pts += [(6, 3), (7, 3), (8, 3), (9, 3)]
    strokes(img, pts, GOLD_DARK)
    for y in range(4, 8):
        for x in range(7 - (1 if y < 7 else 0), 9 + (1 if y < 7 else 0) - 1 + 1):
            if (x, y) not in pts:
                img.set(x, y, gold)
    img.set(7, 4, GOLD_LIGHT)
    strokes(img, [(5, 9), (10, 9)], RED)


def binding(img):
    ice = (120, 220, 240)
    dark = (40, 110, 140)
    links = [(6, 2), (7, 2), (6, 3), (7, 4), (8, 4), (9, 5), (8, 6), (9, 6), (7, 7), (6, 8), (7, 8), (8, 9)]
    strokes(img, links, ice, dark)
    img.set(6, 2, (220, 250, 255))
    strokes(img, [(9, 2), (5, 6)], RED)


def homeward(img):
    gold = (230, 150, 40)
    ring = []
    for y in range(2, 10):
        for x in range(X0, X1 + 1):
            d = math.hypot(x - 7.5, y - 5.5)
            if 2.0 <= d <= 3.0:
                ring.append((x, y))
    strokes(img, ring, gold)
    for (x, y) in ring:
        if y <= 4 and x <= 7:
            img.set(x, y, GOLD_LIGHT)
    # The hearth at the centre: home.
    for p in [(7, 5), (8, 5), (7, 6), (8, 6)]:
        img.set(p[0], p[1], RED)
    img.set(7, 5, RED_LIGHT)


def ward(img):
    blue = (70, 150, 240)
    pale = (180, 220, 255)
    bolt = [(8, 2), (7, 3), (8, 4), (7, 5), (8, 6)]
    strokes(img, bolt, blue, (20, 60, 120))
    # The warding bar across the bolt.
    for x in range(5, 11):
        img.set(x, 7, RED if x in (5, 10) else pale)
    img.set(8, 2, pale)
    strokes(img, [(6, 9), (9, 9)], RED)


def wind(img):
    green = (90, 200, 110)
    dark = (30, 100, 50)
    swirl = [(6, 3), (7, 2), (8, 2), (9, 3), (8, 4), (5, 6), (6, 5), (7, 5), (8, 6), (9, 7), (10, 6), (6, 8), (7, 9), (8, 9)]
    strokes(img, swirl, green, dark)
    img.set(7, 2, (190, 255, 190))
    img.set(10, 6, (190, 255, 190))


def limpidity(img):
    lilac = (160, 130, 230)
    pale = (220, 210, 255)
    eye = [(5, 5), (6, 4), (7, 4), (8, 4), (9, 4), (10, 5), (6, 6), (7, 6), (8, 6), (9, 6)]
    strokes(img, eye, lilac, (70, 50, 120))
    img.set(7, 5, RED_DARK)
    img.set(8, 5, pale)
    # Fading dots below: the user vanishes.
    for p, a in [((6, 8), 200), ((8, 8), 150), ((7, 9), 110), ((9, 9), 70)]:
        img.blend(p[0], p[1], lilac + (a,))


GLYPHS = {
    "thunder_talisman": thunder,
    "fire_cloud_talisman": fire_cloud,
    "shield_talisman": shield,
    "binding_talisman": binding,
    "return_talisman": homeward,
    "tribulation_ward_talisman": ward,
    "thousand_mile_talisman": wind,
    "limpidity_talisman": limpidity,
}


def talisman(name, seed):
    img = paper(seed)
    GLYPHS[name](img)
    seal(img)
    return img


def blank():
    img = paper(3, border_alpha=110)
    return img


def compass(index):
    img = Image(16, 16)
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 7.0:
                if d > 6.0:
                    c = mix(GOLD, GOLD_DARK, 0.5 + 0.5 * ((x + y) / 30.0))
                    if y < cy and x < cx:
                        c = mix(c, GOLD_LIGHT, 0.4)
                elif d > 5.2:
                    c = mix(JADE_DARK, JADE_DEEP, 0.4)
                else:
                    t = d / 5.2
                    c = mix(JADE, JADE_DARK, 0.3 + 0.5 * t)
                    if noise(x, y, 11) > 0.9:
                        c = mix(c, JADE_LIGHT, 0.4)
                img.set(x, y, c)
            elif d <= 7.6:
                img.set(x, y, GOLD_DARK + (150,))
    # Four gold ticks (cardinal marks).
    for (x, y) in [(7, 2), (8, 2), (13, 7), (13, 8), (7, 13), (8, 13), (2, 7), (2, 8)]:
        img.set(x, y, GOLD_LIGHT)
    # The needle: clockwise from up; index 16 points up like the vanilla compass.
    angle = ((index - 16) % 32) / 32.0 * 2.0 * math.pi
    dx, dy = math.sin(angle), -math.cos(angle)
    for step in range(-8, 9):
        t = step / 2.0
        x = int(round(cx + dx * t))
        y = int(round(cy + dy * t))
        if math.hypot(x - cx, y - cy) > 5.0:
            continue
        if t > 0.6:
            img.set(x, y, (255, 214, 90) if t < 3.5 else GOLD_LIGHT)
        elif t < -0.6:
            img.set(x, y, JADE_PALE)
    img.set(7, 7, RED)
    img.set(8, 8, RED_DARK)
    return img


ITEMS_DIR = os.path.join(ROOT, "items")


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def flat_item(name):
    """A generated model and a plain item definition for a flat item."""
    write_json(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}})
    write_json(os.path.join(ITEMS_DIR, name + ".json"), {"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}})


def compass_dispatch(target):
    """The vanilla compass range_dispatch (33 entries, index 16 at threshold 0) pointing at {@code target}."""
    entries = []
    for i in range(33):
        entries.append({
            "model": {"type": "minecraft:model", "model": "kultivator:item/vein_compass_%02d" % ((16 + i) % 32)},
            "threshold": 0.0 if i == 0 else i - 0.5,
        })
    return {"type": "minecraft:range_dispatch", "entries": entries, "property": "minecraft:compass", "scale": 32.0, "target": target}


def main():
    os.makedirs(ITEM, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(ITEMS_DIR, exist_ok=True)
    blank().save(os.path.join(ITEM, "blank_talisman.png"))
    flat_item("blank_talisman")
    for seed, name in enumerate(GLYPHS):
        talisman(name, 20 + seed).save(os.path.join(ITEM, name + ".png"))
        flat_item(name)
    for index in range(32):
        name = "vein_compass_%02d" % index
        compass(index).save(os.path.join(ITEM, name + ".png"))
        write_json(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}})
    # Needle at the found vein (lodestone tracker), spinning while the compass has not searched yet.
    write_json(os.path.join(ITEMS_DIR, "vein_compass.json"), {"model": {
        "type": "minecraft:condition",
        "component": "minecraft:lodestone_tracker",
        "on_false": compass_dispatch("none"),
        "on_true": compass_dispatch("lodestone"),
        "property": "minecraft:has_component",
    }})
    print("done")


if __name__ == "__main__":
    main()
