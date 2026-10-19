"""Generates the item textures, item definitions and models of phase-9 module P9-purba (16x16 pixel art).

  shan_hai_scroll.png      Gulungan Gunung dan Laut: an old silk scroll with jade rods and a mountain-and-wave seal
  kun_scale.png            Sisik Kun: a deep-sea blue scale with a pale rim
  peng_feather.png         Bulu Peng: a long sky-blue feather with cloud-white tips
  zhulong_scale.png        Sisik Zhulong: a scarlet scale with a candle-gold glint
  taotie_fang.png          Taring Taotie: a curved bronze-dark fang
  zhulong_candle_eye.png   Mata Lilin Zhulong: a golden eye with a slit pupil and a candle flame
  heaven_seal_shard.png    Serpihan Segel Langit: a white-gold shard with a seal line

Run with: python tools/gen_purba_textures.py [preview_dir]
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


def polygon(pts):
    def inside(x, y):
        px, py = x + 0.5, y + 0.5
        n = len(pts)
        hit = False
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % n]
            if (ay > py) != (by > py) and px < (bx - ax) * (py - ay) / (by - ay) + ax:
                hit = not hit
        return hit
    return inside


def disc(cx, cy, r):
    return lambda x, y: (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r


def scroll():
    img = Image(SIZE, SIZE)
    paper = lambda x, y: 3 <= x <= 12 and 3 <= y <= 12
    fill(img, paper, rgb(0xE8D8A8), 8, 8, 7)
    outline(img, paper, rgb(0x5A4428))
    for x in range(2, 14):
        img.set(x, 2, rgb(0x2E8963))
        img.set(x, 13, rgb(0x2E8963))
    img.set(1, 2, rgb(0x184B36))
    img.set(14, 2, rgb(0x184B36))
    img.set(1, 13, rgb(0x184B36))
    img.set(14, 13, rgb(0x184B36))
    # mountain peaks and a wave line
    for x, y in ((4, 8), (5, 7), (6, 6), (7, 7), (8, 6), (9, 5), (10, 6), (11, 7)):
        img.set(x, y, rgb(0x3A3A4A))
    for x in range(4, 12):
        img.set(x, 10 if x % 2 == 0 else 9, rgb(0x3A7BD5))
    img.set(10, 4, rgb(0xB3281D))
    img.set(11, 4, rgb(0xB3281D))
    img.set(10, 5, rgb(0xB3281D))
    return img


def kun_scale():
    img = Image(SIZE, SIZE)
    inside = polygon([(8, 1.5), (13.5, 5), (13, 11), (8, 14.5), (3, 11), (2.5, 5)])
    fill(img, inside, rgb(0x2A5FB0), 7, 6, 7)
    outline(img, inside, rgb(0x0E1E3A))
    for y in range(4, 13):
        if inside(8, y):
            img.set(8, y, rgb(0x6FA8F0))
    for x, y in ((5, 6), (11, 6), (5, 10), (11, 10)):
        img.set(x, y, rgb(0x9AD8FF))
    sparkle(img, 6, 4, (200, 230, 255, 170))
    return img


def peng_feather():
    img = Image(SIZE, SIZE)
    for i in range(13):
        x, y = 2 + i, 14 - i
        img.set(x, y, rgb(0xF0F6FF))
    vane = lambda x, y: abs((x - 2) - (14 - y)) <= 3 and 2 <= x <= 14 and 1 <= y <= 13 and (x + y) >= 8
    for y in range(SIZE):
        for x in range(SIZE):
            if vane(x, y) and img.get(x, y)[3] == 0:
                d = abs((x - 2) - (14 - y))
                img.set(x, y, rgb(0x9AD8FF) if d <= 1 else rgb(0x5E9ED8))
    outline(img, lambda x, y: img.get(x, y)[3] > 0 and not (x == 0 and y == 0), rgb(0x1E3A5C))
    for x, y in ((11, 2), (12, 3), (13, 2)):
        img.set(x, y, rgb(0xFFFFFF))
    return img


def zhulong_scale():
    img = Image(SIZE, SIZE)
    inside = polygon([(8, 1.5), (13.5, 5), (13, 11), (8, 14.5), (3, 11), (2.5, 5)])
    fill(img, inside, rgb(0xD8401E), 7, 6, 7)
    outline(img, inside, rgb(0x3A0A04))
    for y in range(4, 13):
        if inside(8, y):
            img.set(8, y, rgb(0xFF8A40))
    img.set(8, 7, rgb(0xFFD27A))
    img.set(8, 8, rgb(0xFFF0B0))
    sparkle(img, 10, 5, (255, 220, 140, 170))
    return img


def taotie_fang():
    img = Image(SIZE, SIZE)
    inside = polygon([(4, 2), (10, 2.5), (12.5, 6), (12, 10), (9, 14), (8, 9.5), (6, 6)])
    fill(img, inside, rgb(0xC9B58A), 9, 6, 7)
    outline(img, inside, rgb(0x3A2A14))
    for x in range(4, 11):
        img.set(x, 2, rgb(0x7A5A2A))
        img.set(x, 3, rgb(0x9A7A3A))
    img.set(9, 13, rgb(0xF0E6C8))
    img.set(10, 11, rgb(0xF0E6C8))
    return img


def candle_eye():
    img = Image(SIZE, SIZE)
    eye = lambda x, y: ((x + 0.5 - 8) / 6.5) ** 2 + ((y + 0.5 - 9) / 3.8) ** 2 <= 1.0
    fill(img, eye, rgb(0xFFC040), 8, 9, 6)
    outline(img, eye, rgb(0x4A1A04))
    for y in range(6, 13):
        if eye(8, y):
            img.set(8, y, rgb(0x2A0A04))
    img.set(7, 8, rgb(0x2A0A04))
    img.set(9, 10, rgb(0x2A0A04))
    for x, y, c in ((8, 1, 0xFFF0B0), (8, 2, 0xFFD060), (7, 3, 0xFF9A30), (8, 3, 0xFFD060), (9, 3, 0xFF9A30), (8, 4, 0xFF7A20)):
        img.set(x, y, rgb(c))
    sparkle(img, 5, 8, (255, 255, 220, 190))
    return img


def seal_shard():
    img = Image(SIZE, SIZE)
    inside = polygon([(7, 1.5), (12.5, 5), (11, 12), (6, 14.5), (3.5, 8)])
    fill(img, inside, rgb(0xF4E8C0), 8, 7, 7)
    outline(img, inside, rgb(0x6A5418))
    for i in range(7):
        x, y = 5 + i, 11 - i
        if inside(x, y):
            img.set(x, y, rgb(0xD4A637))
    img.set(8, 8, rgb(0xB3281D))
    sparkle(img, 10, 4, (255, 250, 220, 200))
    return img


TEXTURES = {
    "shan_hai_scroll": scroll,
    "kun_scale": kun_scale,
    "peng_feather": peng_feather,
    "zhulong_scale": zhulong_scale,
    "taotie_fang": taotie_fang,
    "zhulong_candle_eye": candle_eye,
    "heaven_seal_shard": seal_shard,
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
