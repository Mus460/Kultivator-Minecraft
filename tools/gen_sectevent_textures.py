"""Generates the item textures of module B, sect activities (16x16 pixel art).

spirit_tail_feather.png: Bulu Ekor Roh, a long curved tail feather of a Spirit Tail Chicken (A Will Eternal), pale gold
with a quill, its tip burning in three colours (crimson, gold, azure) like the three-coloured flame of enhancement.

foundation_herb.png: Herba Fondasi from the Blood-Colored Forbidden Ground (A Record of a Mortal's Journey ch. 155-160),
a herb with blood-red leaves on a green stalk and a pale jade bud, a clump of root and soil at the bottom.

spirit_meal.png: Hidangan Roh, a dark earthenware bowl of spirit rice (A Will Eternal ch. 2) heaped high, with a few
glowing jade grains and a curl of steam.

kunpeng_bone_fragment.png: Pecahan Tulang Kunpeng (Perfect World), a broken ivory bone with a knobbed end, dark cracks and
faint azure bone-text runes.

Run with: python tools/gen_sectevent_textures.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
ITEM = os.path.join(ROOT, "textures", "item")


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (255,)


def outline(img, color):
    """Adds a dark outline around every opaque pixel (on transparent neighbours only)."""
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.get(x, y)[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < img.width and 0 <= ny < img.height and img.get(nx, ny)[3] > 200 and img.get(nx, ny)[:3] != color[:3]:
                    marks.append((x, y))
                    break
    for x, y in marks:
        img.set(x, y, color)


# ---------------------------------------------------------------------------------------------- feather

def feather():
    img = Image(16, 16)
    quill = (120, 86, 40, 255)
    vane_light = (255, 240, 190)
    vane_dark = (214, 176, 96)
    crimson = (230, 40, 50)
    gold = (255, 196, 40)
    azure = (70, 170, 255)
    # The shaft curves from the bottom-left (1,14) up to the top-right (13,2).
    shaft = []
    for i in range(40):
        t = i / 39.0
        x = 1.5 + 12.0 * t
        y = 14.5 - 12.5 * t - 2.2 * math.sin(math.pi * t)
        shaft.append((x, y, t))
    for x, y, t in shaft:
        if t < 0.18:
            continue
        # The vane widens toward the middle and narrows to the tip.
        width = 1.7 * math.sin(math.pi * min(1.0, (t - 0.18) / 0.82)) + 0.5
        nx, ny = 0.70, 0.71  # normal to the general direction
        for s in range(-6, 7):
            d = s / 6.0 * width
            px = int(round(x + nx * d))
            py = int(round(y + ny * d))
            # Tip colours: the last 30% burn in three bands across the vane.
            if t > 0.72:
                band = (d / max(0.01, width) + 1.0) / 2.0
                c = crimson if band < 0.34 else gold if band < 0.67 else azure
                c = mix(c, (255, 255, 255), 0.25 if abs(d) < 0.6 else 0.0)
            else:
                side = abs(d) / max(0.01, width)
                c = mix(vane_light, vane_dark, side * 0.9 + (0.15 if d > 0 else 0.0))
                if (int(px + py) % 3 == 0) and side > 0.35:
                    c = shade(c, 0.9)  # barbs
            img.set(px, py, c if len(c) == 4 else c + (255,))
    for x, y, t in shaft:
        c = quill if t < 0.72 else (255, 250, 230, 255)
        img.set(int(round(x)), int(round(y)), c)
    # A spark above the burning tip.
    img.set(14, 1, (255, 230, 120, 220))
    img.set(15, 3, (120, 200, 255, 180))
    outline(img, (60, 40, 20, 255))
    img.save(os.path.join(ITEM, "spirit_tail_feather.png"))


# ---------------------------------------------------------------------------------------------- herb

def herb():
    img = Image(16, 16)
    stem = (70, 140, 60, 255)
    stem_dark = (40, 90, 40, 255)
    leaf = (196, 32, 44)
    leaf_dark = (110, 14, 26)
    leaf_vein = (255, 120, 120, 255)
    soil = (92, 60, 38, 255)
    soil_dark = (60, 38, 24, 255)
    root = (170, 130, 90, 255)
    # Soil clump with roots.
    for x in range(5, 11):
        img.set(x, 14, soil_dark)
    for x in range(4, 12):
        img.set(x, 13, soil if x % 3 else soil_dark)
    img.set(4, 15, root)
    img.set(11, 15, root)
    img.set(7, 15, root)
    # Stalk.
    for y in range(4, 13):
        img.set(8, y, stem if y % 4 else stem_dark)
    img.set(7, 12, stem_dark)

    def leaf_at(x0, y0, direction, length):
        for i in range(length):
            t = i / max(1, length - 1)
            w = int(round(1.1 * math.sin(math.pi * t)))
            cx = x0 + direction * i
            cy = y0 - int(round(i * 0.55))
            for dy in range(-w, w + 1):
                c = mix(leaf, leaf_dark, abs(dy) / 2.0 + (0.2 if dy > 0 else 0.0))
                img.set(cx, cy + dy, c)
            img.set(cx, cy, leaf_vein if 0 < i < length - 1 else mix(leaf, leaf_dark, 0.1))

    leaf_at(7, 11, -1, 6)
    leaf_at(9, 9, 1, 6)
    leaf_at(7, 7, -1, 5)
    leaf_at(9, 5, 1, 4)
    # Pale jade bud on top.
    bud = (190, 255, 220, 255)
    bud_dark = (90, 200, 150, 255)
    img.set(8, 3, bud_dark)
    img.set(8, 2, bud)
    img.set(7, 2, bud_dark)
    img.set(9, 2, bud_dark)
    img.set(8, 1, (240, 255, 245, 255))
    outline(img, (40, 14, 16, 255))
    img.save(os.path.join(ITEM, "foundation_herb.png"))


# ---------------------------------------------------------------------------------------------- meal

def meal():
    img = Image(16, 16)
    bowl_light = (150, 92, 58)
    bowl_dark = (78, 44, 28)
    rim = (196, 140, 92, 255)
    rice = (252, 250, 240)
    rice_shadow = (214, 210, 196)
    jade = (110, 240, 190, 255)
    # Bowl: rows 10-14, widest at the rim.
    for y in range(10, 15):
        half = 7 - (y - 10) if y > 10 else 7
        half = max(3, half)
        for x in range(8 - half, 8 + half):
            t = (x - (8 - half)) / max(1, 2 * half - 1)
            c = mix(bowl_light, bowl_dark, abs(t - 0.35) * 1.6)
            img.set(x, y, c)
    for x in range(1, 15):
        img.set(x, 10, rim)
    for x in range(6, 10):
        img.set(x, 15, (60, 34, 22, 255))
    # Rice heap: a dome over the rim.
    for y in range(4, 10):
        for x in range(2, 14):
            dx = (x - 7.5) / 6.0
            dy = (9.5 - y) / 5.5
            if dx * dx + dy * dy <= 1.0:
                c = mix(rice, rice_shadow, max(0.0, dx) * 0.8 + (0.25 if (x + y) % 4 == 0 else 0.0))
                img.set(x, y, c)
    # Glowing spirit grains.
    for x, y in ((5, 7), (9, 6), (11, 8), (7, 5), (4, 9)):
        img.set(x, y, jade)
    # Steam.
    for x, y, a in ((6, 2, 150), (7, 1, 110), (9, 2, 140), (10, 1, 90), (8, 3, 120)):
        img.set(x, y, (255, 255, 255, a))
    outline(img, (40, 22, 14, 255))
    img.save(os.path.join(ITEM, "spirit_meal.png"))


# ---------------------------------------------------------------------------------------------- bone fragment

def bone():
    img = Image(16, 16)
    ivory = (238, 228, 200)
    ivory_dark = (176, 160, 124)
    crack = (88, 72, 52, 255)
    rune = (90, 190, 255, 255)
    rune_glow = (170, 225, 255, 255)
    # A bone lying diagonally: a double knob at the bottom-left, the shaft up to the top-right where it is broken off.
    def solid(px, py):
        ax, ay, bx, by = 4.0, 12.0, 12.5, 3.5
        vx, vy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
        dx, dy = px - (ax + vx * t), py - (ay + vy * t)
        if dx * dx + dy * dy <= 2.0 * 2.0:
            if t > 0.86:
                # The broken end is jagged.
                zig = (int(px) + int(py)) % 3
                return t < 0.9 + 0.04 * zig
            return True
        for kx, ky in ((2.5, 11.5), (4.5, 13.5)):
            if (px - kx) ** 2 + (py - ky) ** 2 <= 2.2 ** 2:
                return True
        return False

    for y in range(16):
        for x in range(16):
            if solid(x + 0.5, y + 0.5):
                # Light from the top-left: the lower-right side is darker.
                side = ((x - y) + 2.0) / 8.0
                c = mix(ivory, ivory_dark, max(0.0, side) * 0.8 + (0.1 if (x * 7 + y * 3) % 5 == 0 else 0.0))
                img.set(x, y, c)
    # Cracks near the broken end and on the knob.
    for x, y in ((11, 5), (10, 5), (12, 6), (3, 12), (4, 11)):
        if img.get(x, y)[3]:
            img.set(x, y, crack)
    # Bone-text runes along the shaft.
    for x, y in ((6, 9), (7, 9), (7, 8), (8, 8), (9, 7), (9, 6), (8, 6)):
        if img.get(x, y)[3]:
            img.set(x, y, rune)
    for x, y in ((7, 8), (9, 6)):
        if img.get(x, y)[3]:
            img.set(x, y, rune_glow)
    outline(img, (58, 46, 32, 255))
    img.save(os.path.join(ITEM, "kunpeng_bone_fragment.png"))


if __name__ == "__main__":
    os.makedirs(ITEM, exist_ok=True)
    feather()
    herb()
    meal()
    bone()
    print("sect event textures written to", os.path.normpath(ITEM))
