"""Generates the assets of module R, Hubungan antar-murid (DESIGN 36.2.2, 36.4 R). Everything is drawn here; nothing is
downloaded.

Writes (under src/main/resources/assets/kultivator/):
  textures/sekte/hubungan/ikon.png          128x64 atlas of eight thought bubbles (4 x 2 cells of 32x32), the order of
                                            HubunganPayloads.ICON_*: tea, heart, lightning, broken heart, crossed swords,
                                            red knot (sworn kin), lotus (Dao companions), sparkle
  textures/sekte/hubungan/cahaya.png        32x32 soft round glow (the bubble's halo, additive)
  textures/item/fx/sekte_hubungan_celadon.png   16x16 celadon glaze with a faint crackle (the tea cup)
  textures/item/fx/sekte_hubungan_tea.png       16x16 green-gold tea with a bright ring
  models/item/fx/sekte_hubungan_teacup.json the cup (FormModels piece; anchor (8, 8, 8) = the cup's foot, it rises up)
  items/fx/sekte_hubungan_teacup.json       its item definition (kultivator:fx/sekte_hubungan_teacup, no item registered)

Run: python tools/gen_sekte_hubungan_assets.py   (prints a summary; check the PNGs by eye)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
SEKTE_DIR = os.path.join(ROOT, "textures", "sekte", "hubungan")
FX_TEX_DIR = os.path.join(ROOT, "textures", "item", "fx")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")

SUB = 4  # supersampling per axis

INK = (58, 42, 38)
PAPER = (252, 249, 240)
PAPER_SHADE = (233, 226, 212)


# ================================================================================================ helpers

def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def mix(a, b, t):
    t = clamp(t)
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def coverage(fn, x, y):
    """Fraction of the pixel (x, y) inside fn(px, py) (cell pixel coordinates, centre of pixel = +0.5)."""
    n = 0
    for i in range(SUB):
        for j in range(SUB):
            if fn(x + (i + 0.5) / SUB, y + (j + 0.5) / SUB):
                n += 1
    return n / float(SUB * SUB)


def paint(img, ox, oy, size, fn, color, alpha=1.0):
    """Blends shape fn (cell coordinates) with color = (r, g, b) or a function (px, py) -> (r, g, b)."""
    for y in range(size):
        for x in range(size):
            c = coverage(fn, x, y)
            if c <= 0.0:
                continue
            rgb = color(x + 0.5, y + 0.5) if callable(color) else color
            img.blend(ox + x, oy + y, (rgb[0], rgb[1], rgb[2], int(round(255 * c * alpha))))


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def rotated_ellipse(cx, cy, rx, ry, angle):
    ca, sa = math.cos(angle), math.sin(angle)

    def fn(x, y):
        dx, dy = x - cx, y - cy
        u = dx * ca + dy * sa
        v = -dx * sa + dy * ca
        return (u / rx) ** 2 + (v / ry) ** 2 <= 1.0
    return fn


def polygon(points):
    def fn(x, y):
        inside = False
        n = len(points)
        j = n - 1
        for i in range(n):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
                inside = not inside
            j = i
        return inside
    return fn


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy + 1e-9))
    qx, qy = ax + t * dx, ay + t * dy
    return math.hypot(px - qx, py - qy)


def segment(ax, ay, bx, by, w):
    return lambda x, y: seg_dist(x, y, ax, ay, bx, by) <= w / 2.0


def union(*fns):
    return lambda x, y: any(f(x, y) for f in fns)


def minus(a, b):
    return lambda x, y: a(x, y) and not b(x, y)


def grow(fn, r):
    """Outline helper: true near fn (a ring of radius r sampled on 8 directions)."""
    dirs = [(math.cos(k * math.pi / 4) * r, math.sin(k * math.pi / 4) * r) for k in range(8)]
    return lambda x, y: fn(x, y) or any(fn(x + dx, y + dy) for dx, dy in dirs)


def heart(cx, cy, s):
    def fn(x, y):
        u = (x - cx) / s
        v = -(y - cy) / s + 0.15
        return (u * u + v * v - 1.0) ** 3 - u * u * v ** 3 <= 0.0
    return fn


# ================================================================================================ the bubble atlas

def bubble(img, ox, oy, tint=(0, 0, 0), tint_amount=0.0):
    body = ellipse(16.0, 13.0, 14.2, 11.6)
    tail1 = circle(8.6, 26.4, 2.5)
    tail2 = circle(5.4, 29.8, 1.4)
    shape = union(body, tail1, tail2)
    outline = grow(shape, 1.1)

    def fill(x, y):
        base = mix(PAPER, PAPER_SHADE, (y - 3.0) / 22.0)
        return mix(base, tint, tint_amount)
    paint(img, ox, oy, 32, minus(outline, shape), INK, 0.92)
    paint(img, ox, oy, 32, shape, fill)
    # a soft light on the upper left of the cloud
    paint(img, ox, oy, 32, ellipse(10.5, 7.0, 5.5, 2.6), (255, 255, 255), 0.55)


def icon_tea(img, ox, oy):
    celadon = (138, 196, 168)
    celadon_dark = (88, 146, 122)
    saucer = ellipse(16.0, 19.6, 9.0, 1.9)
    paint(img, ox, oy, 32, grow(saucer, 0.7), INK, 0.8)
    paint(img, ox, oy, 32, saucer, lambda x, y: mix(celadon, celadon_dark, (y - 18.0) / 3.0))
    bowl = lambda x, y: 10.5 <= y <= 18.6 and abs(x - 16.0) <= 6.6 * math.sqrt(clamp(1.0 - ((y - 10.5) / 8.4) ** 2 * 0.72))
    paint(img, ox, oy, 32, grow(bowl, 0.8), INK, 0.85)
    paint(img, ox, oy, 32, bowl, lambda x, y: mix((178, 222, 196), celadon_dark, (x - 9.0) / 15.0))
    paint(img, ox, oy, 32, lambda x, y: bowl(x, y) and 13.0 <= y <= 14.0, (60, 110, 150), 0.85)
    paint(img, ox, oy, 32, ellipse(16.0, 10.6, 6.6, 1.3), (176, 150, 70))
    paint(img, ox, oy, 32, ellipse(14.4, 10.4, 2.4, 0.5), (240, 220, 140), 0.8)
    for k, sx in enumerate((12.5, 16.0, 19.5)):
        steam = lambda x, y, sx=sx, k=k: 2.5 <= y <= 9.0 and abs(x - (sx + 1.1 * math.sin((y + k * 2.0) * 0.9))) <= 0.55
        paint(img, ox, oy, 32, steam, (150, 150, 158), 0.75)


def icon_heart(img, ox, oy):
    shape = heart(16.0, 13.6, 7.0)
    paint(img, ox, oy, 32, grow(shape, 0.8), INK, 0.85)
    paint(img, ox, oy, 32, shape, lambda x, y: mix((255, 128, 164), (206, 34, 72), (x + y - 18.0) / 18.0))
    paint(img, ox, oy, 32, rotated_ellipse(12.4, 10.6, 2.2, 1.1, -0.7), (255, 236, 242), 0.85)


def icon_lightning(img, ox, oy):
    pts = [(19.0, 3.2), (10.0, 14.6), (15.4, 14.6), (11.6, 23.6), (22.6, 10.8), (17.0, 10.8), (20.6, 3.2)]
    shape = polygon(pts)
    paint(img, ox, oy, 32, grow(shape, 0.9), (110, 40, 24), 0.9)
    paint(img, ox, oy, 32, shape, lambda x, y: mix((255, 246, 170), (255, 170, 30), (y - 4.0) / 18.0))
    paint(img, ox, oy, 32, segment(18.6, 5.0, 13.4, 12.4, 0.9), (255, 255, 230), 0.8)


def icon_broken(img, ox, oy):
    crack = [(16.0, 6.0), (14.4, 9.6), (17.4, 12.6), (14.8, 16.0), (16.6, 19.4), (16.0, 22.0)]

    def left_of_crack(x, y):
        # x position of the crack at height y
        for i in range(len(crack) - 1):
            (ax, ay), (bx, by) = crack[i], crack[i + 1]
            if ay <= y <= by:
                t = (y - ay) / (by - ay + 1e-9)
                return x < ax + (bx - ax) * t
        return x < 16.0
    base = heart(16.0, 13.6, 7.0)
    left = lambda x, y: base(x + 0.9, y - 0.4) and left_of_crack(x + 0.9, y - 0.4)
    right = lambda x, y: base(x - 0.9, y + 0.4) and not left_of_crack(x - 0.9, y + 0.4)
    for half, shade in ((left, (150, 92, 120)), (right, (128, 74, 104))):
        paint(img, ox, oy, 32, grow(half, 0.7), INK, 0.85)
        paint(img, ox, oy, 32, half, lambda x, y, s=shade: mix((200, 140, 166), s, (y - 6.0) / 16.0))


def icon_swords(img, ox, oy):
    steel = lambda x, y: mix((240, 246, 252), (150, 164, 182), (x - 8.0) / 16.0)
    for (ax, ay, bx, by, gx0, gy0, gx1, gy1, hx, hy) in (
            (21.8, 4.4, 10.8, 17.6, 9.2, 15.0, 13.0, 19.8, 7.6, 21.4),
            (10.2, 4.4, 21.2, 17.6, 19.0, 19.8, 22.8, 15.0, 24.4, 21.4)):
        blade = segment(ax, ay, bx, by, 2.0)
        paint(img, ox, oy, 32, grow(blade, 0.7), INK, 0.85)
        paint(img, ox, oy, 32, blade, steel)
        guard = segment(gx0, gy0, gx1, gy1, 1.5)
        paint(img, ox, oy, 32, grow(guard, 0.5), INK, 0.85)
        paint(img, ox, oy, 32, guard, (222, 176, 60))
        hilt = segment(bx, by, hx, hy, 1.6)
        paint(img, ox, oy, 32, hilt, (120, 62, 30))
        paint(img, ox, oy, 32, circle(hx, hy, 1.1), (222, 176, 60))


def icon_knot(img, ox, oy):
    red = (204, 30, 42)
    deep = (130, 14, 22)
    diamond = polygon([(16.0, 3.4), (22.6, 10.0), (16.0, 16.6), (9.4, 10.0)])
    paint(img, ox, oy, 32, grow(diamond, 0.8), INK, 0.85)
    paint(img, ox, oy, 32, diamond, lambda x, y: mix((240, 70, 70), deep, (y - 3.0) / 14.0))
    # the woven lattice of the knot
    for k in range(-2, 3):
        d = k * 2.3
        paint(img, ox, oy, 32, lambda x, y, d=d: diamond(x, y) and abs((x - 16.0) + (y - 10.0) - d) <= 0.45, deep, 0.75)
        paint(img, ox, oy, 32, lambda x, y, d=d: diamond(x, y) and abs((x - 16.0) - (y - 10.0) - d) <= 0.45, (255, 150, 140), 0.6)
    # loops at the four corners
    for (cx, cy) in ((16.0, 2.6), (23.4, 10.0), (8.6, 10.0)):
        loop = minus(circle(cx, cy, 1.9), circle(cx, cy, 0.9))
        paint(img, ox, oy, 32, loop, red)
    # the gold bead and tassel
    paint(img, ox, oy, 32, circle(16.0, 18.4, 1.4), (240, 196, 70))
    for dx in (-1.6, -0.8, 0.0, 0.8, 1.6):
        paint(img, ox, oy, 32, segment(16.0, 19.4, 16.0 + dx * 1.1, 24.0, 0.7), red)


def icon_lotus(img, ox, oy):
    leaf = ellipse(16.0, 19.2, 9.4, 2.4)
    paint(img, ox, oy, 32, grow(leaf, 0.6), INK, 0.7)
    paint(img, ox, oy, 32, leaf, lambda x, y: mix((120, 196, 110), (50, 120, 60), (y - 17.0) / 4.0))
    angles = (-1.05, 1.05, -0.55, 0.55, 0.0)
    for a in angles:
        cx = 16.0 + math.sin(a) * 4.6
        cy = 15.6 - math.cos(a) * 4.6
        petal = rotated_ellipse(cx, cy, 2.5, 5.2, a)
        paint(img, ox, oy, 32, grow(petal, 0.6), (120, 40, 70), 0.75)
        paint(img, ox, oy, 32, petal, lambda x, y, cy=cy: mix((255, 236, 244), (238, 110, 160), (y - (cy - 5.0)) / 9.0))
    paint(img, ox, oy, 32, circle(16.0, 14.6, 1.3), (255, 220, 110))


def icon_sparkle(img, ox, oy):
    def star(cx, cy, r):
        return lambda x, y: math.sqrt(abs(x - cx) / r) + math.sqrt(abs(y - cy) / r) <= 1.0
    big = star(16.0, 12.6, 8.2)
    paint(img, ox, oy, 32, grow(big, 0.7), (120, 84, 20), 0.8)
    paint(img, ox, oy, 32, big, lambda x, y: mix((255, 252, 220), (240, 176, 40), math.hypot(x - 16.0, y - 12.6) / 7.0))
    for (cx, cy, r) in ((7.6, 6.4, 3.0), (24.6, 18.4, 2.6), (23.6, 5.4, 1.8)):
        paint(img, ox, oy, 32, star(cx, cy, r), (255, 230, 120), 0.95)


def make_atlas():
    img = Image(128, 64)
    icons = [
        (icon_tea, (0, 0, 0), 0.0),
        (icon_heart, (255, 200, 216), 0.18),
        (icon_lightning, (150, 150, 160), 0.22),
        (icon_broken, (180, 170, 190), 0.2),
        (icon_swords, (230, 190, 170), 0.15),
        (icon_knot, (255, 222, 160), 0.18),
        (icon_lotus, (255, 210, 230), 0.2),
        (icon_sparkle, (255, 240, 190), 0.18),
    ]
    for i, (draw, tint, amount) in enumerate(icons):
        ox = (i % 4) * 32
        oy = (i // 4) * 32
        bubble(img, ox, oy, tint, amount)
        draw(img, ox, oy)
    return img


def make_glow():
    img = Image(32, 32)
    for y in range(32):
        for x in range(32):
            r = math.hypot(x + 0.5 - 16.0, y + 0.5 - 16.0) / 16.0
            a = clamp(1.0 - r) ** 2.2
            img.set(x, y, (255, 255, 255, int(255 * a)))
    return img


# ================================================================================================ the tea cup

def make_celadon():
    rnd = random.Random(37)
    img = Image(16, 16)
    base = (150, 204, 176)
    for y in range(16):
        for x in range(16):
            n = rnd.random() * 0.08
            c = mix(base, (196, 232, 210), 0.25 + n - y / 64.0)
            img.set(x, y, (int(c[0]), int(c[1]), int(c[2]), 255))
    # the crackle of a Ge-ware glaze: a few thin darker veins
    for _ in range(7):
        x, y = rnd.uniform(0, 16), rnd.uniform(0, 16)
        a = rnd.uniform(0, math.pi)
        for _ in range(rnd.randint(4, 9)):
            px, py = int(x) % 16, int(y) % 16
            r, g, b, al = img.get(px, py)
            img.set(px, py, (int(r * 0.82), int(g * 0.86), int(b * 0.84), 255))
            a += rnd.uniform(-0.7, 0.7)
            x += math.cos(a)
            y += math.sin(a)
    return img


def make_tea():
    rnd = random.Random(11)
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8.0, y + 0.5 - 8.0) / 8.0
            c = mix((196, 170, 70), (120, 132, 40), r)
            c = mix(c, (255, 240, 170), clamp(1.0 - abs(r - 0.55) * 8.0) * 0.35)
            n = rnd.random() * 10
            img.set(x, y, (int(c[0] + n), int(c[1] + n), int(c[2]), 255))
    return img


def face_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("north", "south"):
        u0, u1, v0, v1 = x0, x1, 16 - y1, 16 - y0
    elif face in ("east", "west"):
        u0, u1, v0, v1 = z0, z1, 16 - y1, 16 - y0
    else:
        u0, u1, v0, v1 = x0, x1, z0, z1
    vals = [max(0.0, min(16.0, round(v, 3))) for v in (u0, v0, u1, v1)]
    if vals[2] - vals[0] < 0.5:
        vals[2] = min(16.0, vals[0] + 0.5)
    if vals[3] - vals[1] < 0.5:
        vals[3] = min(16.0, vals[1] + 0.5)
    return vals


def element(f, t, tex, rot=None):
    e = {"from": list(f), "to": list(t), "faces": {}}
    for face in ("north", "south", "east", "west", "up", "down"):
        e["faces"][face] = {"uv": face_uv(face, f, t), "texture": tex}
    if rot is not None:
        e["rotation"] = rot
    return e


def make_cup():
    y45 = {"origin": [8, 8, 8], "x": 0.0, "y": 45.0, "z": 0.0}
    elems = [
        # the foot ring (square and turned: an octagon)
        element((6.6, 8.0, 6.6), (9.4, 8.5, 9.4), "#c"),
        element((6.8, 8.0, 6.8), (9.2, 8.5, 9.2), "#c", y45),
        # the lower bowl
        element((5.9, 8.5, 5.9), (10.1, 9.4, 10.1), "#c"),
        element((6.1, 8.5, 6.1), (9.9, 9.4, 9.9), "#c", y45),
        # the bowl
        element((5.3, 9.4, 5.3), (10.7, 10.6, 10.7), "#c"),
        element((5.5, 9.4, 5.5), (10.5, 10.6, 10.5), "#c", y45),
        # the flared rim, four thin walls (open at the top)
        element((5.0, 10.6, 5.0), (11.0, 11.5, 5.5), "#c"),
        element((5.0, 10.6, 10.5), (11.0, 11.5, 11.0), "#c"),
        element((5.0, 10.6, 5.5), (5.5, 11.5, 10.5), "#c"),
        element((10.5, 10.6, 5.5), (11.0, 11.5, 10.5), "#c"),
        # the tea inside
        element((5.5, 10.95, 5.5), (10.5, 11.05, 10.5), "#t"),
        # a cobalt band around the belly
        element((5.25, 9.85, 5.25), (10.75, 10.15, 10.75), "#b"),
    ]
    return {
        "textures": {
            "c": "kultivator:item/fx/sekte_hubungan_celadon",
            "t": "kultivator:item/fx/sekte_hubungan_tea",
            "b": "kultivator:item/fx/mat_blue_silver",
            "particle": "kultivator:item/fx/sekte_hubungan_celadon",
        },
        "elements": elems,
    }


def main():
    for d in (SEKTE_DIR, FX_TEX_DIR, MODEL_DIR, ITEM_DIR):
        os.makedirs(d, exist_ok=True)
    make_atlas().save(os.path.join(SEKTE_DIR, "ikon.png"))
    make_glow().save(os.path.join(SEKTE_DIR, "cahaya.png"))
    make_celadon().save(os.path.join(FX_TEX_DIR, "sekte_hubungan_celadon.png"))
    make_tea().save(os.path.join(FX_TEX_DIR, "sekte_hubungan_tea.png"))
    blue = os.path.join(FX_TEX_DIR, "mat_blue_silver.png")
    if not os.path.exists(blue):
        raise SystemExit("missing shared material " + blue)
    model = make_cup()
    for e in model["elements"]:
        for v in e["from"] + e["to"]:
            assert -16.0 <= v <= 32.0
    with open(os.path.join(MODEL_DIR, "sekte_hubungan_teacup.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=1)
        f.write("\n")
    with open(os.path.join(ITEM_DIR, "sekte_hubungan_teacup.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "kultivator:item/fx/sekte_hubungan_teacup"}}, f, indent=1)
        f.write("\n")
    print("ikon.png 128x64 (8 bubbles), cahaya.png 32x32, celadon + tea 16x16, teacup model with %d elements" % len(model["elements"]))


if __name__ == "__main__":
    main()
