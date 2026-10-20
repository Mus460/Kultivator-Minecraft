"""Generates the Titah Langit world textures (DESIGN 29.6.3, 29.2; module M3 of phase 10b). Everything here is ours.

Writes:
  textures/block/batu_pusaka.png        Batu Pusaka: dark jade-black stone of the block monument (16 x 16)
  textures/block/urat_pusaka.png        Urat Pusaka: the same stone with glowing gold veins (16 x 16)
  textures/item/heaven_decree_seal.png  Segel Titah Langit: a white-jade square seal, coiled-dragon knob, gold cord, red 敕 mark

The block models, blockstates and the item definition are plain JSON files next to them (no generator needed).

Run with: python tools/gen_titah_world_textures.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
SIZE = 16


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def value_noise(seed):
    r = random.Random(seed)
    grid = [[r.random() for _ in range(5)] for _ in range(5)]

    def at(x, y):
        # tileable 4 x 4 lattice over the 16 px tile
        gx, gy = x / 4.0, y / 4.0
        x0, y0 = int(math.floor(gx)), int(math.floor(gy))
        fx, fy = gx - x0, gy - y0
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

        def g(ix, iy):
            return grid[iy % 4][ix % 4]
        a = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * sx
        b = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * sx
        return a + (b - a) * sy
    return at


def stone(seed):
    """Jade-black stone: deep green-black with a faint jade sheen and a carved bevel (tileable)."""
    img = Image(SIZE, SIZE)
    n = value_noise(seed)
    r = random.Random(seed)
    dark, mid, sheen = rgb("0C1210"), rgb("1E2C27"), rgb("3E5E52")
    for y in range(SIZE):
        for x in range(SIZE):
            t = 0.25 + 0.6 * n(x, y) + 0.1 * (r.random() - 0.5)
            c = mix(dark, mid, t)
            if (x + y * 2) % 11 == 0 and t > 0.55:
                c = mix(c, sheen, 0.45)
            img.set(x, y, c + (255,))
    # a carved border: darker groove, then a thin jade rim inside it
    for i in range(SIZE):
        for (x, y) in ((i, 0), (0, i)):
            img.set(x, y, mix(img.get(x, y)[:3], rgb("050807"), 0.6) + (255,))
        for (x, y) in ((i, SIZE - 1), (SIZE - 1, i)):
            img.set(x, y, mix(img.get(x, y)[:3], rgb("2E4A40"), 0.4) + (255,))
    return img


def vein_path(r, x, y, steps):
    pts = []
    for _ in range(steps):
        pts.append((x, y))
        x = (x + r.choice((-1, 0, 1))) % SIZE
        y = (y + r.choice((0, 1, 1))) % SIZE
    return pts


def batu_pusaka():
    return stone(41)


def urat_pusaka():
    img = stone(42)
    r = random.Random(7)
    core, body, halo = rgb("FFF6C8"), rgb("FFD54A"), rgb("B07818")
    paths = [vein_path(r, 2, 0, 18), vein_path(r, 9, 3, 16), vein_path(r, 13, 8, 12), vein_path(r, 5, 10, 10)]
    for path in paths:
        for (x, y) in path:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                px, py = (x + dx) % SIZE, (y + dy) % SIZE
                img.set(px, py, mix(img.get(px, py)[:3], halo, 0.55) + (255,))
        for i, (x, y) in enumerate(path):
            img.set(x, y, (core if i % 3 == 0 else body) + (255,))
    return img


def heaven_decree_seal():
    """A square white-jade seal seen slightly from above: body, top face, coiled gold dragon knob, cord, red 敕 mark."""
    img = Image(SIZE, SIZE, (0, 0, 0, 0))
    jade_light, jade, jade_dark = rgb("F8FFF4"), rgb("DCEBDD"), rgb("8FAA98")
    gold, gold_dark, red = rgb("FFD54A"), rgb("9A6A12"), rgb("C0201A")
    # body (front face) x 3..12, y 8..14
    for y in range(8, 15):
        for x in range(3, 13):
            t = (x - 3) / 9.0
            c = mix(jade, jade_dark, t * 0.6 + (y - 8) / 14.0)
            img.set(x, y, c + (255,))
    # top face (a parallelogram) y 6..7
    for y in (6, 7):
        for x in range(4 - (7 - y), 14 - (7 - y)):
            img.set(x, y, jade_light + (255,))
    # gold band at the bottom and outline
    for x in range(3, 13):
        img.set(x, 14, gold_dark + (255,))
        img.set(x, 13, gold + (255,))
    for y in range(8, 15):
        img.set(3, y, mix(jade_dark, jade_light, 0.2) + (255,))
        img.set(12, y, jade_dark + (255,))
    # red 敕 seal mark on the front (a tiny square with the glyph's two halves)
    for (x, y) in ((6, 9), (7, 9), (8, 9), (9, 9), (6, 10), (9, 10), (6, 11), (7, 11), (8, 11), (9, 11), (7, 10)):
        img.set(x, y, red + (255,))
    # coiled dragon knob: a gold coil with a head
    for (x, y) in ((6, 5), (7, 5), (8, 5), (9, 5), (5, 4), (6, 3), (7, 3), (8, 3), (9, 4), (10, 4), (10, 3), (11, 2)):
        img.set(x, y, gold + (255,))
    for (x, y) in ((7, 4), (8, 4)):
        img.set(x, y, gold_dark + (255,))
    img.set(11, 2, rgb("FFF6C8") + (255,))
    img.set(12, 2, red + (255,))
    # gold cord with a red tassel hanging on the right
    for (x, y) in ((13, 6), (14, 7), (14, 8), (14, 9)):
        img.set(x, y, gold + (255,))
    for (x, y) in ((14, 10), (13, 11), (14, 11), (15, 11), (14, 12)):
        img.set(x, y, red + (255,))
    return img


def save(img, folder, name):
    path = os.path.join(ROOT, folder, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    save(batu_pusaka(), "block", "batu_pusaka")
    save(urat_pusaka(), "block", "urat_pusaka")
    save(heaven_decree_seal(), "item", "heaven_decree_seal")
    print("titah world textures: 3")
    return 0


if __name__ == "__main__":
    sys.exit(main())
