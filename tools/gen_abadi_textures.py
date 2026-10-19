"""Generates the item textures, item definitions and models of phase-9 module P9-langit (16x16 pixel art).

  immortal_origin_stone.png    Batu Asal Abadi: a pale gold-white faceted origin stone with an inner glow
  realm_crossing_talisman.png  Jimat Lintas Alam: a jade talisman strip with a gold cloud gate glyph

Run with: python tools/gen_abadi_textures.py [preview_dir]
"""
import json
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
    if light >= 0:
        return mix(base, (255, 255, 255), light * 0.55)
    return mix(base, (base[0] * 0.25, base[1] * 0.22, base[2] * 0.35), -light * 0.75)


def fill(img, inside, base, cx, cy, radius):
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            dx, dy = (x - cx) / radius, (y - cy) / radius
            light = -dx * 0.55 - dy * 0.75
            level = 0.7 if light > 0.55 else 0.2 if light > 0.1 else -0.25 if light > -0.45 else -0.6
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


def origin_stone():
    img = Image(SIZE, SIZE)
    # A hexagonal faceted stone: |dx| + |dy| * 0.6 inside a rounded hexagon.
    def inside(x, y):
        dx = abs(x + 0.5 - 8.0)
        dy = abs(y + 0.5 - 8.5)
        return dy <= 6.0 and dx <= 5.5 and dx * 0.55 + dy <= 7.2
    fill(img, inside, rgb(0xE8D9A0), 8, 8.5, 6.5)
    outline(img, inside, rgb(0x5A4A20))
    # Facet lines.
    for y in range(4, 13):
        if inside(8, y):
            img.blend(8, y, (255, 255, 240, 90))
    for x in range(4, 13):
        if inside(x, 8):
            img.blend(x, 8, (120, 100, 50, 70))
    # The inner glow of immortal essence.
    for (x, y, a) in ((7, 7, 200), (8, 7, 230), (7, 8, 170), (8, 9, 140), (9, 8, 150)):
        img.blend(x, y, (255, 250, 220, a))
    for (x, y) in ((6, 10), (10, 6)):
        img.blend(x, y, (190, 230, 255, 150))
    sparkle(img, 11, 4, (255, 245, 200, 180))
    sparkle(img, 4, 12, (200, 235, 255, 140))
    return img


def talisman():
    img = Image(SIZE, SIZE)
    inside = lambda x, y: 4 <= x <= 11 and 1 <= y <= 14
    for y in range(SIZE):
        for x in range(SIZE):
            if inside(x, y):
                t = (y - 1) / 13.0
                img.set(x, y, mix(rgb(0x7FE0B0), rgb(0x2E8963), t))
    outline(img, inside, rgb(0x123A28))
    # Gold borders top and bottom.
    for x in range(4, 12):
        img.set(x, 1, rgb(0xF1D57E))
        img.set(x, 14, rgb(0xD4A637))
    # A cloud gate glyph: two pillars and a lintel with a cloud arc.
    for y in range(5, 12):
        img.set(6, y, rgb(0xF7DC7E))
        img.set(9, y, rgb(0xF7DC7E))
    for x in range(5, 11):
        img.set(x, 4, rgb(0xFFE9A8))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        img.set(x, y, rgb(0xE8F0FF))
    for (x, y) in ((7, 10), (8, 10)):
        img.set(x, y, rgb(0xB0D8FF))
    # A red seal dot.
    img.set(10, 12, rgb(0xB3281D))
    sparkle(img, 13, 3, (190, 255, 220, 170))
    return img


TEXTURES = {
    "immortal_origin_stone": origin_stone,
    "realm_crossing_talisman": talisman,
}


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    tex_dir = os.path.join(ROOT, "textures", "item")
    items_dir = os.path.join(ROOT, "items")
    models_dir = os.path.join(ROOT, "models", "item")
    for d in (tex_dir, items_dir, models_dir):
        os.makedirs(d, exist_ok=True)
    if preview:
        os.makedirs(preview, exist_ok=True)
    for name, make in TEXTURES.items():
        img = make()
        img.save(os.path.join(tex_dir, name + ".png"))
        with open(os.path.join(items_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}}, f, indent=2)
            f.write("\n")
        with open(os.path.join(models_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}}, f, indent=2)
            f.write("\n")
        if preview:
            big = Image(SIZE * 8, SIZE * 8)
            for y in range(SIZE * 8):
                for x in range(SIZE * 8):
                    px = img.get(x // 8, y // 8)
                    big.set(x, y, px if px[3] > 0 else (40, 44, 52, 255))
            big.save(os.path.join(preview, name + ".png"))
    print("wrote", len(TEXTURES), "items")


if __name__ == "__main__":
    main()
