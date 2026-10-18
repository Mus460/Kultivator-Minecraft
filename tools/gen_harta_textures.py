"""Generates the item textures, item definitions and models of phase-7 module P7-harta (16x16 pixel art).

  spirit_stone_low..supreme.png   Batu Roh: a faceted spirit crystal per grade (jade, blue, violet, gold), bigger and
                                  brighter with each grade; the Supreme stone radiates
  beast_core_low..king.png        Inti Binatang: a greyscale orb (layer0, tinted with the core's element colour through
  beast_core_*_overlay.png        minecraft:custom_model_data) plus an untinted overlay (outline, shine, grade marks:
                                  none, a band, glowing cracks, a gold crown rim)
  spirit_grass.png ...            the eight spirit herbs (grass tuft, ninecurl ginseng root, eight-angled ice grass,
                                  purple flame flower, illusory herb, gold lightning bamboo, ginseng fruit, yellow
                                  essence mushroom)
  dao_insight_pill.png ...        the four new pills (teal eye of the Dao, green purging drop, golden lotus, bone pill)

Run with: python tools/gen_harta_textures.py [preview_dir]
With preview_dir, an 8x enlarged copy of every texture is written there too (for checking by eye).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
SIZE = 16
CORE_DEFAULT = 0xC8A060


def rgb(hex_value, alpha=255):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, alpha)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(base, light):
    """light -1..1: darker towards -1, brighter towards 1."""
    if light >= 0:
        return mix(base, (255, 255, 255), light * 0.55)
    return mix(base, (base[0] * 0.25, base[1] * 0.22, base[2] * 0.35), -light * 0.75)


def fill_shape(img, inside, base, center, radius, bands=(0.55, 0.1, -0.45)):
    cx, cy = center
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            dx, dy = (x - cx) / radius, (y - cy) / radius
            light = -dx * 0.55 - dy * 0.75
            if light > bands[0]:
                level = 0.75
            elif light > bands[1]:
                level = 0.2
            elif light > bands[2]:
                level = -0.25
            else:
                level = -0.65
            img.set(x, y, shade(base, level))


def outline(img, inside, color):
    body = [[inside(x, y) for x in range(SIZE)] for y in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if body[y][x]:
                continue
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + ox, y + oy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and body[ny][nx]:
                    img.set(x, y, color)
                    break


def sparkle(img, x, y, color, core=(255, 255, 255, 255)):
    for ox, oy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
        img.blend(x + ox, y + oy, color)
    img.set(x, y, core)


def circle(cx, cy, r):
    return lambda x, y: math.hypot(x - cx, y - cy) <= r


def polygon(points):
    """Point-in-polygon test for pixel centres."""
    def inside(x, y):
        px, py = x + 0.5, y + 0.5
        hit = False
        n = len(points)
        for i in range(n):
            x1, y1 = points[i]
            x2, y2 = points[(i + 1) % n]
            if (y1 > py) != (y2 > py):
                cross = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
                if px < cross:
                    hit = not hit
        return hit
    return inside


def line(img, x0, y0, x1, y1, color, blend=False):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        x = int(round(x0 + (x1 - x0) * i / steps))
        y = int(round(y0 + (y1 - y0) * i / steps))
        if blend:
            img.blend(x, y, color)
        else:
            img.set(x, y, color)


def save(img, name, preview):
    out = os.path.join(ROOT, "textures", "item")
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, name + ".png"))
    if preview:
        big = Image(SIZE * 8, SIZE * 8)
        for y in range(SIZE * 8):
            for x in range(SIZE * 8):
                px = img.get(x // 8, y // 8)
                big.set(x, y, px if px[3] > 0 else (40, 40, 48, 255))
        big.save(os.path.join(preview, name + ".png"))


def write_json(name, layers=None, tints=None):
    items = os.path.join(ROOT, "items")
    models = os.path.join(ROOT, "models", "item")
    os.makedirs(items, exist_ok=True)
    os.makedirs(models, exist_ok=True)
    model = {"type": "minecraft:model", "model": "kultivator:item/" + name}
    if tints:
        model["tints"] = tints
    with open(os.path.join(items, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps({"model": model}, indent=2) + "\n")
    textures = {}
    for i, layer in enumerate(layers or [name]):
        textures["layer%d" % i] = "kultivator:item/" + layer
    with open(os.path.join(models, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps({"parent": "minecraft:item/generated", "textures": textures}, indent=2) + "\n")


# ---------------------------------------------------------------- spirit stones

STONE_COLORS = [0x9FE0C0, 0x6FB8FF, 0xB07CFF, 0xFFE27A]
STONE_DARK = [0x1E4A3A, 0x173A66, 0x34195E, 0x6A4A10]


def stone(grade):
    img = Image(SIZE, SIZE)
    base = rgb(STONE_COLORS[grade])
    dark = rgb(STONE_DARK[grade])
    if grade == 0:
        # A small rough jade crystal, slightly tilted.
        pts = [(6, 4), (10, 5), (12, 9), (10, 13), (5, 13), (3, 9)]
    elif grade == 1:
        # A cut hexagonal gem.
        pts = [(8, 2.5), (13, 5.5), (13, 10.5), (8, 13.5), (3, 10.5), (3, 5.5)]
    elif grade == 2:
        # A tall double-pointed crystal.
        pts = [(8, 1), (12.5, 6), (12.5, 10), (8, 15), (3.5, 10), (3.5, 6)]
    else:
        # A large brilliant.
        pts = [(5, 2), (11, 2), (14.5, 6.5), (8, 15), (1.5, 6.5)]
    inside = polygon(pts)
    fill_shape(img, inside, base, (8, 8), 6.5)
    # Facet lines.
    facet = shade(base, 0.85)
    shadow = shade(base, -0.55)
    if grade == 0:
        line(img, 6, 5, 7, 9, facet)
        line(img, 7, 9, 11, 9, shadow)
        line(img, 7, 9, 6, 12, shadow)
    elif grade == 1:
        line(img, 8, 4, 8, 7, facet)
        line(img, 5, 6, 8, 7, facet)
        line(img, 8, 7, 11, 6, shade(base, 0.4))
        line(img, 8, 8, 8, 12, shadow)
    elif grade == 2:
        line(img, 8, 2, 8, 13, shade(base, 0.6))
        line(img, 4, 7, 12, 7, facet)
        line(img, 9, 8, 11, 10, shadow)
    else:
        line(img, 2, 6, 14, 6, facet)
        line(img, 5, 3, 7, 6, facet)
        line(img, 10, 3, 9, 6, facet)
        line(img, 7, 7, 8, 13, shade(base, 0.3))
        line(img, 10, 7, 9, 12, shadow)
    outline(img, inside, dark)
    # Inner qi glow.
    cx, cy = (8, 8) if grade != 0 else (7, 9)
    img.blend(cx, cy, (255, 255, 255, 170))
    img.blend(cx - 1, cy - 1, (255, 255, 255, 90))
    if grade >= 1:
        sparkle(img, 13 if grade != 3 else 14, 2 if grade != 3 else 1, rgb(STONE_COLORS[grade], 200))
    if grade >= 2:
        img.blend(2, 12, rgb(STONE_COLORS[grade], 200))
    if grade == 3:
        # Rays of the Supreme stone.
        for x, y in ((0, 9), (15, 9), (1, 1), (3, 14), (13, 14)):
            img.blend(x, y, (255, 240, 170, 200))
        sparkle(img, 1, 11, (255, 236, 150, 210))
    return img


# ---------------------------------------------------------------- beast cores

def core_body(grade):
    """Greyscale orb (tinted by the element colour)."""
    img = Image(SIZE, SIZE)
    r = 4.6 + 0.4 * grade
    cx, cy = 8.0, 8.5
    inside = circle(cx, cy, r)
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            dx, dy = (x - cx) / r, (y - cy) / r
            light = -dx * 0.5 - dy * 0.7
            v = 150 + int(90 * max(-1.0, min(1.0, light)))
            # A darker swirl inside the orb.
            angle = math.atan2(dy, dx)
            dist = math.hypot(dx, dy)
            if abs(math.sin(angle * 2 + dist * 5.0)) < 0.18 and dist < 0.85:
                v -= 55
            img.set(x, y, (v, v, v, 255))
    return img, inside, r


def core_overlay(grade, inside, r):
    img = Image(SIZE, SIZE)
    outline(img, inside, (36, 26, 18, 255))
    cx, cy = 8.0, 8.5
    # Shine.
    img.set(int(cx - r * 0.45), int(cy - r * 0.5), (255, 255, 255, 230))
    img.blend(int(cx - r * 0.45) + 1, int(cy - r * 0.5), (255, 255, 255, 120))
    img.blend(int(cx - r * 0.45), int(cy - r * 0.5) + 1, (255, 255, 255, 120))
    if grade == 2:
        # A band around the orb.
        for x in range(SIZE):
            y = int(round(cy + 1 + math.sin((x - cx) / r * 1.2) * 0.8))
            if inside(x, y):
                img.blend(x, y, (255, 250, 230, 120))
    elif grade == 3:
        # Glowing cracks.
        for (x0, y0, x1, y1) in ((8, 9, 11, 6), (8, 9, 5, 12), (8, 9, 10, 12), (11, 6, 12, 5)):
            line(img, x0, y0, x1, y1, (255, 255, 240, 200), blend=True)
        img.set(8, 9, (255, 255, 255, 255))
    elif grade == 4:
        # Gold crown rim on top and a bright heart.
        gold = (255, 205, 70, 255)
        gold_dark = (150, 100, 20, 255)
        for x in range(4, 13):
            img.set(x, 3, gold if x % 2 == 0 else gold_dark)
        for x in (4, 6, 8, 10, 12):
            img.set(x, 2, gold)
        img.set(8, 1, (255, 240, 160, 255))
        img.set(8, 9, (255, 255, 255, 255))
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            img.blend(8 + ox, 9 + oy, (255, 255, 230, 160))
        sparkle(img, 14, 13, (255, 220, 120, 200))
    return img


# ---------------------------------------------------------------- herbs

def spirit_grass():
    img = Image(SIZE, SIZE)
    blades = [(4, 14, 2, 4), (6, 14, 5, 2), (8, 14, 8, 1), (10, 14, 11, 3), (12, 14, 14, 5), (7, 14, 6, 6), (9, 14, 10, 7)]
    for x0, y0, x1, y1 in blades:
        line(img, x0, y0, x1, y1, rgb(0x3E9A5A))
    for x0, y0, x1, y1 in blades[:5]:
        line(img, x0, y0 - 2, (x0 + x1) // 2, (y0 + y1) // 2, rgb(0x63C77A))
    for x0, y0, x1, y1 in blades:
        img.set(x1, y1, rgb(0xC8FFD8))
        img.blend(x1, y1 - 1, rgb(0x8CFFB0, 140))
    for x in range(3, 14):
        img.set(x, 15, rgb(0x2E6B3E))
    sparkle(img, 13, 2, rgb(0x8CFFB0, 180))
    return img


def ninecurl_ginseng():
    img = Image(SIZE, SIZE)
    body = rgb(0xE0B070)
    # Main root.
    inside = polygon([(7, 5), (10, 5), (11, 9), (10, 12), (8, 13), (6, 11), (6, 8)])
    fill_shape(img, inside, body, (8, 8), 4.5)
    outline(img, inside, rgb(0x6A4420))
    # Curling side roots (nine curls, a few visible).
    for pts in (((6, 10), (4, 11), (3, 13), (4, 14)), ((10, 11), (12, 12), (13, 14)), ((8, 13), (8, 15)), ((6, 8), (4, 8), (3, 9))):
        for i in range(len(pts) - 1):
            line(img, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], rgb(0xC08A4A))
    # Ring marks.
    for y in (7, 9, 11):
        img.blend(8, y, rgb(0x8A5A2A, 160))
        img.blend(9, y, rgb(0x8A5A2A, 120))
    # Leaves and red berries on top.
    for x0, y0, x1, y1 in ((8, 5, 5, 1), (9, 5, 12, 1), (8, 5, 8, 0)):
        line(img, x0, y0, x1, y1, rgb(0x4CA04A))
    img.set(5, 1, rgb(0x7CD86A))
    img.set(12, 1, rgb(0x7CD86A))
    img.set(8, 0, rgb(0xE03030))
    img.set(9, 1, rgb(0xE03030))
    return img


def eight_angled_ice_grass():
    img = Image(SIZE, SIZE)
    stem = rgb(0x6FA8C8)
    line(img, 8, 15, 8, 8, stem)
    line(img, 8, 12, 5, 10, stem)
    line(img, 8, 11, 11, 9, stem)
    # An eight-pointed ice star on top.
    cx, cy = 8, 6
    for i in range(8):
        a = i * math.pi / 4
        length = 4 if i % 2 == 0 else 3
        x1 = int(round(cx + math.cos(a) * length))
        y1 = int(round(cy + math.sin(a) * length))
        line(img, cx, cy, x1, y1, rgb(0xA8F0FF))
        img.set(x1, y1, rgb(0xE8FFFF))
    img.set(cx, cy, (255, 255, 255, 255))
    for x, y in ((5, 10), (11, 9)):
        img.set(x, y, rgb(0xC8F8FF))
    img.blend(2, 2, rgb(0xE8FFFF, 180))
    img.blend(13, 13, rgb(0xE8FFFF, 150))
    return img


def purple_flame_flower():
    img = Image(SIZE, SIZE)
    line(img, 8, 15, 8, 9, rgb(0x3E7A3A))
    line(img, 8, 13, 5, 12, rgb(0x4E9A4A))
    line(img, 8, 12, 11, 11, rgb(0x4E9A4A))
    # Flame-shaped petals.
    petal = polygon([(8, 1), (10.5, 4), (12, 3), (12, 7), (10, 9.5), (6, 9.5), (4, 7), (4, 3), (5.5, 4)])
    fill_shape(img, petal, rgb(0xC060FF), (8, 6), 4.5)
    outline(img, petal, rgb(0x4A1470))
    inner = polygon([(8, 3.5), (9.5, 6), (9, 8.5), (7, 8.5), (6.5, 6)])
    for y in range(SIZE):
        for x in range(SIZE):
            if inner(x, y):
                img.set(x, y, rgb(0xFF9AE8) if y > 5 else rgb(0xF0C8FF))
    img.set(8, 7, (255, 255, 255, 255))
    img.blend(13, 1, rgb(0xE0A0FF, 180))
    img.blend(3, 1, rgb(0xE0A0FF, 140))
    return img


def illusory_spirit_herb():
    img = Image(SIZE, SIZE)
    # Translucent wispy leaves.
    for x0, y0, x1, y1, c in ((8, 15, 8, 4, 0xB8A8E8), (8, 13, 4, 7, 0xD8C8FF), (8, 12, 12, 6, 0xD8C8FF), (8, 10, 5, 3, 0xC8B8F8),
                              (8, 9, 11, 2, 0xC8B8F8)):
        line(img, x0, y0, x1, y1, rgb(c, 200), blend=True)
    for x, y in ((4, 7), (12, 6), (5, 3), (11, 2), (8, 4)):
        img.set(x, y, (255, 255, 255, 230))
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            img.blend(x + ox, y + oy, rgb(0xE8E0FF, 110))
    # A small moon above.
    moon = circle(3, 2, 1.6)
    for y in range(SIZE):
        for x in range(SIZE):
            if moon(x, y) and not circle(3.8, 1.6, 1.3)(x, y):
                img.set(x, y, rgb(0xFFF4C8))
    for x in range(5, 12):
        img.blend(x, 15, rgb(0x6A5A98, 200))
    return img


def gold_lightning_bamboo():
    img = Image(SIZE, SIZE)
    gold = rgb(0xFFD54A)
    stalk = polygon([(6, 1), (10, 1), (10, 15), (6, 15)])
    fill_shape(img, stalk, gold, (8, 8), 5.0, bands=(0.4, 0.0, -0.4))
    outline(img, stalk, rgb(0x6A4A08))
    for y in (5, 10):
        for x in range(6, 10):
            img.set(x, y, rgb(0x8A6A10))
        img.set(5, y, rgb(0x6A4A08))
        img.set(10, y, rgb(0x6A4A08))
    # Lightning down the stalk.
    bolt = [(8, 1), (7, 4), (9, 6), (7, 9), (9, 11), (8, 14)]
    for i in range(len(bolt) - 1):
        line(img, bolt[i][0], bolt[i][1], bolt[i + 1][0], bolt[i + 1][1], rgb(0xFFFFF0))
    # A leaf.
    line(img, 10, 6, 13, 3, rgb(0x6CB840))
    img.set(14, 2, rgb(0x9CE070))
    sparkle(img, 3, 3, rgb(0xFFF2A0, 200))
    return img


def ginseng_fruit():
    img = Image(SIZE, SIZE)
    # A pale fruit shaped like a sleeping infant (JTTW ch. 24).
    head = circle(8, 5.5, 2.6)
    body = polygon([(5.5, 7.5), (10.5, 7.5), (11.5, 11), (10, 14), (6, 14), (4.5, 11)])
    both = lambda x, y: head(x, y) or body(x, y)
    fill_shape(img, both, rgb(0xFFE8C0), (8, 8), 5.5)
    outline(img, both, rgb(0x8A6A3A))
    # Closed eyes and little arms.
    img.set(7, 6, rgb(0x8A5A3A))
    img.set(9, 6, rgb(0x8A5A3A))
    img.blend(8, 7, rgb(0xFF9A9A, 140))
    line(img, 6, 9, 8, 10, rgb(0xE8C890))
    line(img, 10, 9, 8, 10, rgb(0xE8C890))
    # Leaves and stem on top.
    line(img, 8, 3, 8, 1, rgb(0x4C8A3A))
    line(img, 8, 2, 5, 1, rgb(0x6CB850))
    line(img, 8, 2, 11, 1, rgb(0x6CB850))
    sparkle(img, 13, 4, rgb(0xFFF4D8, 200))
    img.blend(2, 12, rgb(0xFFF4D8, 160))
    return img


def yellow_essence_mushroom():
    img = Image(SIZE, SIZE)
    cap = polygon([(2, 8), (4, 4), (8, 2.5), (12, 4), (14, 8)])
    fill_shape(img, cap, rgb(0xFFD040), (8, 5), 6.0)
    outline(img, cap, rgb(0x7A5408))
    stem = polygon([(6.5, 8), (9.5, 8), (10, 14), (6, 14)])
    fill_shape(img, stem, rgb(0xF4E8C8), (8, 11), 3.5)
    outline(img, stem, rgb(0x8A7A5A))
    for x, y in ((5, 5), (9, 4), (11, 6), (7, 6)):
        img.set(x, y, rgb(0xFFF4B0))
    img.blend(8, 3, (255, 255, 255, 200))
    for x in range(3, 14):
        img.blend(x, 15, rgb(0x5A4A2A, 200))
    sparkle(img, 13, 11, rgb(0xFFE070, 190))
    return img


# ---------------------------------------------------------------- pills

def dao_insight():
    img = Image(SIZE, SIZE)
    inside = circle(7.5, 8.5, 5.6)
    fill_shape(img, inside, rgb(0x3FD0C8), (7.5, 8.5), 5.6)
    # An eye of the Dao: almond with a yin-yang pupil.
    for x in range(4, 12):
        dy = int(round(2.2 * math.sin((x - 3.5) / 8.0 * math.pi)))
        img.set(x, 8 - dy, rgb(0xE8FFFC))
        img.set(x, 9 + dy, rgb(0xE8FFFC))
    img.set(7, 8, (20, 30, 40, 255))
    img.set(8, 8, (255, 255, 255, 255))
    img.set(7, 9, (255, 255, 255, 255))
    img.set(8, 9, (20, 30, 40, 255))
    outline(img, inside, rgb(0x0E4A48))
    img.set(5, 5, (255, 255, 255, 255))
    sparkle(img, 13, 2, rgb(0x9FFFF6, 200))
    return img


def toxin_purging():
    img = Image(SIZE, SIZE)
    inside = circle(7.5, 8.5, 5.4)
    fill_shape(img, inside, rgb(0xB8F5A0), (7.5, 8.5), 5.4)
    drop = polygon([(7.5, 4.5), (10, 8.5), (9.5, 11), (7.5, 12), (5.5, 11), (5, 8.5)])
    for y in range(SIZE):
        for x in range(SIZE):
            if drop(x, y):
                img.set(x, y, rgb(0xFFFFFF) if y < 9 else rgb(0xE0FFF0))
    img.set(6, 9, rgb(0xC8F0D8))
    outline(img, inside, rgb(0x2E6A2A))
    img.set(5, 5, (255, 255, 255, 255))
    img.blend(13, 3, rgb(0xD8FFC8, 180))
    return img


def enlightenment():
    img = Image(SIZE, SIZE)
    inside = circle(7.5, 8.5, 5.6)
    fill_shape(img, inside, rgb(0xE0A828), (7.5, 8.5), 5.6, bands=(0.75, 0.1, -0.45))
    # A small white lotus: a tall middle petal, two side petals and a base.
    petal = rgb(0xFFFFF0)
    edge = rgb(0xF0C860)
    for x, y in ((7, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8)):
        img.set(x, y, petal)
    for x, y in ((4, 7), (5, 8), (6, 9), (5, 9), (11, 7), (10, 8), (9, 9), (10, 9)):
        img.set(x, y, petal)
    for x in range(5, 11):
        img.set(x, 10, edge)
    img.set(6, 8, edge)
    img.set(9, 8, edge)
    outline(img, inside, rgb(0x7A5408))
    # Rays.
    for x, y in ((1, 3), (14, 3), (0, 9), (15, 9), (7, 0)):
        img.blend(x, y, (255, 240, 170, 210))
    img.set(5, 5, (255, 255, 255, 255))
    return img


def bone_tempering():
    img = Image(SIZE, SIZE)
    inside = circle(7.5, 8.5, 5.5)
    fill_shape(img, inside, rgb(0xE8DCC0), (7.5, 8.5), 5.5)
    # A small bone across it.
    line(img, 5, 11, 10, 6, rgb(0xFFFFFF))
    for x, y in ((4, 11), (5, 12), (10, 5), (11, 6)):
        img.set(x, y, rgb(0xFFFFFF))
    img.set(5, 10, rgb(0xC8B898))
    img.set(9, 7, rgb(0xC8B898))
    outline(img, inside, rgb(0x6A5A3A))
    img.set(5, 5, (255, 255, 255, 255))
    img.blend(13, 2, rgb(0xC08A3E, 200))
    return img


HERBS = {
    "spirit_grass": spirit_grass,
    "ninecurl_ginseng": ninecurl_ginseng,
    "eight_angled_ice_grass": eight_angled_ice_grass,
    "purple_flame_flower": purple_flame_flower,
    "illusory_spirit_herb": illusory_spirit_herb,
    "gold_lightning_bamboo": gold_lightning_bamboo,
    "ginseng_fruit": ginseng_fruit,
    "yellow_essence_mushroom": yellow_essence_mushroom,
}

PILLS = {
    "dao_insight_pill": dao_insight,
    "toxin_purging_pill": toxin_purging,
    "enlightenment_pill": enlightenment,
    "bone_tempering_pill": bone_tempering,
}


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    if preview:
        os.makedirs(preview, exist_ok=True)
    for grade, name in enumerate(["low", "mid", "high", "supreme"]):
        item = "spirit_stone_" + name
        save(stone(grade), item, preview)
        write_json(item)
    for grade, name in enumerate(["low", "mid", "high", "king"], start=1):
        item = "beast_core_" + name
        body, inside, r = core_body(grade)
        save(body, item, preview)
        save(core_overlay(grade, inside, r), item + "_overlay", preview)
        write_json(item, layers=[item, item + "_overlay"], tints=[
            {"type": "minecraft:custom_model_data", "index": 0, "default": CORE_DEFAULT},
            {"type": "minecraft:constant", "value": -1},
        ])
    for name, make in HERBS.items():
        save(make(), name, preview)
        write_json(name)
    for name, make in PILLS.items():
        save(make(), name, preview)
        write_json(name)
    print("ok")


if __name__ == "__main__":
    main()
