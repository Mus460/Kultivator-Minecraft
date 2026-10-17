"""Generates the alchemy module's pill items: 16x16 pixel-art textures plus the item and model JSON files.

Run with: python tools/gen_alchemy_textures.py [preview_dir]
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


def rgb(hex_value, alpha=255):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, alpha)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(base, light):
    """light -1..1: darker towards -1 (mixed with a deep shadow), brighter towards 1 (mixed with white)."""
    if light >= 0:
        return mix(base, (255, 255, 255), light * 0.55)
    return mix(base, (base[0] * 0.25, base[1] * 0.22, base[2] * 0.35), -light * 0.75)


def fill_shape(img, inside, base, center, radius, bands=(0.55, 0.1, -0.45)):
    """Fills the mask with banded sphere-like shading lit from the top left."""
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
        img.set(x + ox, y + oy, color)
    img.set(x, y, core)


def circle(cx, cy, r):
    return lambda x, y: math.hypot(x - cx, y - cy) <= r


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


def write_json(name):
    items = os.path.join(ROOT, "items")
    models = os.path.join(ROOT, "models", "item")
    os.makedirs(items, exist_ok=True)
    os.makedirs(models, exist_ok=True)
    with open(os.path.join(items, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps({"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}}, indent=2) + "\n")
    with open(os.path.join(models, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}}, indent=2) + "\n")


# ---------------------------------------------------------------- the pills

def qi_gathering():
    """Sky-blue pearl with a white qi spiral drawn into its centre."""
    img = Image(SIZE, SIZE)
    inside = circle(7.5, 8.5, 5.6)
    fill_shape(img, inside, rgb(0x6FD3F5), (7.5, 8.5), 5.6)
    # Spiral of gathered qi.
    for i in range(26):
        t = i / 25.0
        angle = t * math.pi * 2.6
        r = 4.6 * (1 - t) + 0.4
        x = int(round(7.5 + math.cos(angle) * r))
        y = int(round(8.5 + math.sin(angle) * r))
        if inside(x, y):
            img.set(x, y, mix(rgb(0xFFFFFF), rgb(0xBDF2FF), t * 0.6))
    img.set(7, 8, (255, 255, 255, 255))
    outline(img, inside, rgb(0x163A5C))
    img.set(5, 5, (255, 255, 255, 255))
    sparkle(img, 13, 2, rgb(0xBDF2FF))
    img.set(2, 3, rgb(0x9FE8FF))
    return img


def qi_recovery():
    """Deep blue capsule lying diagonally, one half lighter, with a water-drop shine."""
    img = Image(SIZE, SIZE)
    ax, ay, bx, by, r = 4.5, 11.0, 11.0, 4.5, 3.2

    def dist(x, y):
        vx, vy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)))
        px, py = ax + vx * t, ay + vy * t
        return math.hypot(x - px, y - py), t

    def inside(x, y):
        return dist(x, y)[0] <= r

    for y in range(SIZE):
        for x in range(SIZE):
            d, t = dist(x, y)
            if d > r:
                continue
            # Side of the axis: light on the upper-left edge.
            side = ((x - ax) * (by - ay) - (y - ay) * (bx - ax)) / math.hypot(bx - ax, by - ay)
            base = rgb(0x3E7BEA) if t < 0.5 else rgb(0x8FC8FF)
            level = -side / r * 0.8
            level = 0.7 if level > 0.45 else 0.15 if level > -0.1 else -0.3 if level > -0.55 else -0.65
            img.set(x, y, shade(base, level))
    # Seam between the halves.
    for (x, y) in [(6, 6), (7, 7), (8, 8), (9, 9), (7, 6), (8, 7), (9, 8)]:
        if inside(x, y):
            img.set(x, y, rgb(0x1F3F8C))
    outline(img, inside, rgb(0x0E1F45))
    for (x, y) in [(10, 3), (11, 3), (12, 4)]:
        img.set(x, y, (255, 255, 255, 255))
    # A falling drop.
    img.set(3, 3, rgb(0xBFE3FF))
    img.set(3, 4, rgb(0x6FB0FF))
    img.set(2, 4, rgb(0x2E5FC0))
    img.set(4, 4, rgb(0x2E5FC0))
    img.set(3, 5, rgb(0x2E5FC0))
    return img


def foundation():
    """Octagonal earth-gold pill engraved with two foundation-stone lines."""
    img = Image(SIZE, SIZE)
    cx, cy = 7.5, 8.0

    def inside(x, y):
        dx, dy = abs(x - cx), abs(y - cy)
        return dx <= 5.6 and dy <= 5.1 and dx + dy <= 7.6

    fill_shape(img, inside, rgb(0xD9A85A), (cx, cy), 6.0)
    # Engraved courses of a foundation wall.
    for x in range(3, 13):
        if inside(x, 7):
            img.set(x, 7, rgb(0x7A5424))
        if inside(x, 10):
            img.set(x, 10, rgb(0x7A5424))
    for (x, y) in [(6, 8), (6, 9), (10, 8), (10, 9), (8, 11), (8, 12), (4, 11), (4, 12), (12, 11)]:
        if inside(x, y):
            img.set(x, y, rgb(0x8C6430))
    for (x, y) in [(7, 4), (8, 4), (9, 5), (7, 5)]:
        img.set(x, y, rgb(0xFFF0C8))
    outline(img, inside, rgb(0x3E2A12))
    img.set(5, 4, (255, 250, 230, 255))
    sparkle(img, 13, 2, rgb(0xFFE6A0))
    return img


def golden_core():
    """A blazing gold sphere with a white-hot core inside a thin golden ring."""
    img = Image(SIZE, SIZE)
    cx, cy, r = 7.5, 7.5, 4.6
    ring = lambda x, y: 5.6 <= math.hypot(x - cx, y - cy) <= 6.6
    for y in range(SIZE):
        for x in range(SIZE):
            if ring(x, y):
                angle = math.atan2(y - cy, x - cx)
                img.set(x, y, rgb(0xFFE27A) if math.sin(angle * 3) > -0.2 else rgb(0xC98A12))
    inside = circle(cx, cy, r)
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            d = math.hypot(x - cx + 0.8, y - cy + 0.8) / r
            if d < 0.35:
                c = (255, 255, 236, 255)
            elif d < 0.65:
                c = rgb(0xFFE066)
            elif d < 0.95:
                c = rgb(0xFFB81C)
            else:
                c = rgb(0xC97A06)
            img.set(x, y, c)
    outline(img, inside, rgb(0x5A3404))
    # Little rays between ring and core.
    for (x, y) in [(7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)]:
        img.set(x, y, rgb(0xFFF2B0))
    return img


def heart_calming():
    """A pink heart-shaped pill with a small white lotus on it."""
    img = Image(SIZE, SIZE)

    def inside(x, y):
        # Two round lobes on top of a triangle pointing down.
        if math.hypot(x - 4.8, y - 5.5) <= 3.0 or math.hypot(x - 10.2, y - 5.5) <= 3.0:
            return True
        if 6.0 <= y <= 13.6:
            half = 6.2 * (13.6 - y) / 7.6
            return abs(x - 7.5) <= half
        return False

    fill_shape(img, inside, rgb(0xF29AC0), (7.5, 7.5), 6.0)
    # Lotus: three petals and a golden heart.
    for (x, y, c) in [(7, 6, 0xFFFFFF), (8, 6, 0xFFFFFF), (6, 7, 0xFCE6F1), (9, 7, 0xFCE6F1), (7, 7, 0xFFFFFF), (8, 7, 0xFFFFFF),
                      (5, 8, 0xFCE6F1), (10, 8, 0xFCE6F1), (6, 8, 0xFFFFFF), (9, 8, 0xFFFFFF), (7, 8, 0xFFD36E), (8, 8, 0xFFD36E),
                      (6, 9, 0xD9709E), (7, 9, 0xD9709E), (8, 9, 0xD9709E), (9, 9, 0xD9709E)]:
        if inside(x, y):
            img.set(x, y, rgb(c))
    outline(img, inside, rgb(0x5A1E3A))
    img.set(4, 4, (255, 255, 255, 255))
    img.set(5, 4, rgb(0xFFE0EE))
    sparkle(img, 13, 13, rgb(0xFFC8E0))
    return img


def healing():
    """A green pill with a white healing cross."""
    img = Image(SIZE, SIZE)
    inside = circle(7.5, 8.0, 5.7)
    fill_shape(img, inside, rgb(0x4FD872), (7.5, 8.0), 5.7)
    for y in range(5, 12):
        for x in range(7, 9):
            img.set(x, y, (255, 255, 255, 255) if y > 5 else rgb(0xE8FFF0))
    for x in range(4, 12):
        for y in range(7, 9):
            img.set(x, y, (255, 255, 255, 255) if x > 4 else rgb(0xE8FFF0))
    for (x, y) in [(9, 9), (9, 10), (9, 11), (10, 9), (11, 9), (7, 12), (8, 12)]:
        img.set(x, y, rgb(0x2E9E4E))
    outline(img, inside, rgb(0x0F3E1E))
    img.set(4, 4, (255, 255, 255, 255))
    img.set(5, 3, rgb(0xC8FFD8))
    # A leaf sprouting at the top right.
    for (x, y, c) in [(12, 2, 0x8CFF9E), (13, 2, 0x5FD872), (13, 1, 0x8CFF9E), (12, 3, 0x3FA855)]:
        img.set(x, y, rgb(c))
    return img


def tribulation_warding():
    """A violet storm pill split by a yellow lightning bolt, with a faint copper rim."""
    img = Image(SIZE, SIZE)
    inside = circle(7.5, 8.0, 5.8)
    fill_shape(img, inside, rgb(0x7A5CE0), (7.5, 8.0), 5.8)
    bolt = [(9, 2), (8, 3), (8, 4), (7, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (8, 9), (7, 10), (7, 11), (6, 12), (6, 13)]
    for (x, y) in bolt:
        img.set(x, y, rgb(0xFFF07A))
    for (x, y) in [(9, 3), (8, 5), (7, 8), (9, 8), (8, 10), (7, 12)]:
        if inside(x, y):
            img.set(x, y, rgb(0xFFC83C))
    outline(img, inside, rgb(0xB8642E))
    # Copper rim is the outline; storm specks.
    img.set(4, 5, rgb(0xD8C8FF))
    img.set(11, 11, rgb(0x4A3296))
    img.set(10, 5, rgb(0xC0A8FF))
    sparkle(img, 2, 2, rgb(0xFFF07A))
    return img


def soul_nurturing():
    """A pale lavender teardrop pill holding a small glowing soul wisp."""
    img = Image(SIZE, SIZE)

    def inside(x, y):
        # Teardrop: a circle at the bottom drawn up into a point.
        cx, cy, r = 7.5, 9.5, 4.7
        if y >= cy:
            return math.hypot(x - cx, y - cy) <= r
        h = (cy - y) / 8.0
        if h > 1.0:
            return False
        return abs(x - cx) <= r * (1.0 - h) ** 1.15 + 0.15

    fill_shape(img, inside, rgb(0x9C78E0), (7.5, 9.0), 5.6)
    # Inner glow around the soul.
    for y in range(7, 14):
        for x in range(4, 12):
            if inside(x, y) and math.hypot(x - 7.5, y - 10.0) <= 2.6:
                img.blend(x, y, (236, 224, 255, 110))
    # The wisp: a small soul with a curling tail.
    for (x, y, c) in [(7, 8, 0xFFFFFF), (8, 8, 0xFFFFFF), (6, 9, 0xF4ECFF), (7, 9, 0xFFFFFF), (8, 9, 0xFFFFFF), (9, 9, 0xF4ECFF),
                      (7, 10, 0xF4ECFF), (8, 10, 0xFFFFFF), (8, 11, 0xE6D8FF), (9, 11, 0xE6D8FF), (9, 12, 0xD2BEFA), (10, 12, 0xC2A6F5)]:
        img.set(x, y, rgb(c))
    outline(img, inside, rgb(0x3A2660))
    img.set(6, 4, (255, 255, 255, 255))
    img.set(5, 6, rgb(0xF0E6FF))
    sparkle(img, 13, 3, rgb(0xE6D8FF))
    img.set(2, 12, rgb(0xBFA8F0))
    return img


def immortal():
    """A radiant pearl of white gold inside a jade-gold halo, with four star rays and rainbow glints."""
    img = Image(SIZE, SIZE)
    cx, cy = 7.5, 7.5
    # Star rays.
    for i in range(1, 8):
        a = max(0, 255 - i * 28)
        for (x, y) in [(7, 7 - i), (8, 7 - i), (7, 8 + i), (8, 8 + i), (7 - i, 7), (7 - i, 8), (8 + i, 7), (8 + i, 8)]:
            img.blend(x, y, (255, 244, 200, a))
    for i in range(1, 5):
        a = max(0, 200 - i * 45)
        for (x, y) in [(7 - i - 1, 7 - i - 1), (8 + i + 1, 7 - i - 1), (7 - i - 1, 8 + i + 1), (8 + i + 1, 8 + i + 1)]:
            img.blend(x, y, (255, 230, 160, a))
    # Halo ring.
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - cx, y - cy)
            if 4.9 <= d <= 5.8:
                angle = math.atan2(y - cy, x - cx)
                hue = (math.sin(angle * 2) + 1) / 2
                img.set(x, y, mix(rgb(0xFFD54A), rgb(0x7CFFB2), hue * 0.5))
    inside = circle(cx, cy, 4.0)
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            d = math.hypot(x - cx + 1.0, y - cy + 1.0) / 4.0
            if d < 0.45:
                c = (255, 255, 255, 255)
            elif d < 0.8:
                c = rgb(0xFFF6D8)
            elif d < 1.1:
                c = rgb(0xF7E2A0)
            else:
                c = rgb(0xE0B85A)
            img.set(x, y, c)
    # Rainbow glints on the pearl.
    img.set(9, 9, rgb(0xFFB0E0))
    img.set(10, 8, rgb(0xB0E8FF))
    img.set(8, 10, rgb(0xC8FFB0))
    outline(img, inside, rgb(0x8A6418))
    img.set(6, 5, (255, 255, 255, 255))
    return img


PILLS = {
    "qi_gathering_pill": qi_gathering,
    "qi_recovery_pill": qi_recovery,
    "foundation_pill": foundation,
    "golden_core_pill": golden_core,
    "heart_calming_pill": heart_calming,
    "healing_pill": healing,
    "tribulation_warding_pill": tribulation_warding,
    "soul_nurturing_pill": soul_nurturing,
    "immortal_pill": immortal,
}


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    if preview:
        os.makedirs(preview, exist_ok=True)
    for name, make in PILLS.items():
        save(make(), name, preview)
        write_json(name)
    print("wrote", len(PILLS), "pill textures")


if __name__ == "__main__":
    main()
