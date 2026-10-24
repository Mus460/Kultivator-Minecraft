"""Generates the decoration atlas of DESIGN 38.O.3 (module 38MD): festival lanterns, sky lanterns, fireworks, incense
smoke, banner and awning cloth, the armillary sphere and the water wheel, drawn by client/dunia/tempat/model/HiasanDunia
as camera-relative custom geometry (vanilla entity render types only).

textures/dunia/tempat/hias.png   128 x 128, sixteen-pixel cells (column, row) = HiasanDunia.Sel:
  row 0: 0 red lantern paper   1 sky-lantern paper   2 bamboo        3 gold          4 engraved bronze
         5 wet paddle wood     6 dark wood           7 flame (alpha)
  row 1: 0 banner silk (pale, tinted by the caller)  1 awning stripes  2 blue cloth  3 crimson tassel
         4-6 incense smoke puffs (alpha, three shapes)                 7 star sparkle (alpha)
  row 2: 0-7 firework burst frames (alpha): a ring of sparks opening, then glittering out
  row 3: 0 jade orb with star points  1 water spray (alpha)  2 lantern gold rim  3 painted cloud motif (alpha)

Run with: python tools/gen_tempat_textures.py [preview_dir]   (8x preview into preview_dir). Deterministic.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "dunia", "tempat", "hias.png")
CELL = 16


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, f):
    return tuple(max(0.0, min(255.0, v * f)) for v in c[:3])


def noise(seed):
    r = random.Random(seed)
    g = [[r.random() for _ in range(17)] for _ in range(17)]

    def at(x, y):
        x0, y0 = int(x) % 16, int(y) % 16
        fx, fy = x - int(x), y - int(y)
        a = g[y0][x0] + (g[y0][x0 + 1] - g[y0][x0]) * fx
        b = g[y0 + 1][x0] + (g[y0 + 1][x0 + 1] - g[y0 + 1][x0]) * fx
        return a + (b - a) * fy
    return at


CELLS = {}


def cell(col, row):
    def deco(fn):
        CELLS[(col, row)] = fn
        return fn
    return deco


@cell(0, 0)
def red_paper(x, y, rng):
    c = mix((176, 26, 26), (232, 66, 44), 0.5 + 0.5 * math.sin(y * 0.4))
    if x in (0, 15) or y in (0, 15):
        c = (90, 60, 30)  # the bamboo frame
    if x in (5, 10):
        c = shade(c, 0.78)  # ribs showing through
    if 5 <= x <= 10 and 5 <= y <= 10 and ((x + y) % 2 == 0 or x in (7, 8) or y in (7, 8)):
        c = (240, 196, 80)  # a gold character
    return c + (255,)


@cell(1, 0)
def sky_paper(x, y, rng):
    c = mix((252, 236, 190), (255, 186, 110), y / 15.0)
    if x % 5 == 0:
        c = shade(c, 0.85)
    return shade(c, 0.97 + rng.random() * 0.04) + (255,)


@cell(2, 0)
def bamboo(x, y, rng):
    c = mix((150, 160, 80), (210, 196, 112), 0.5 + 0.5 * math.sin(x * 0.8))
    if y in (0, 8):
        c = (110, 104, 50)
    return c + (255,)


@cell(3, 0)
def gold(x, y, rng):
    c = mix((168, 120, 34), (246, 210, 110), 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.4))
    if (x + y) % 7 == 0:
        c = (255, 244, 200)
    return c + (255,)


@cell(4, 0)
def bronze_engraved(x, y, rng):
    c = mix((120, 80, 38), (194, 142, 72), 0.5 + 0.5 * math.sin(x * 0.5))
    if x % 4 == 0 and y in (2, 3, 12, 13):
        c = (70, 44, 20)  # degree ticks
    if y in (7, 8) and x % 2 == 0:
        c = (226, 190, 120)
    return c + (255,)


@cell(5, 0)
def paddle_wood(x, y, rng):
    c = mix((86, 60, 36), (126, 92, 56), 0.5 + 0.5 * math.sin(y * 0.9 + x * 0.1))
    if x in (0, 15):
        c = (60, 64, 70)  # iron edge
    if y > 10:
        c = mix(c, (60, 80, 70), 0.4)  # wet, mossy
    return c + (255,)


@cell(6, 0)
def dark_wood(x, y, rng):
    c = mix((52, 32, 20), (86, 56, 34), 0.5 + 0.5 * math.sin(y * 1.1 + math.sin(x)))
    return c + (255,)


@cell(7, 0)
def flame(x, y, rng):
    cx, cy = x - 7.5, y - 9.0
    w = 4.5 * (1.0 - max(0.0, -cy) / 8.0)
    if abs(cx) < w and -8 < cy < 5:
        t = abs(cx) / max(0.1, w)
        c = mix((255, 250, 210), (255, 120, 30), t)
        a = int(255 * (1.0 - t * 0.5))
        return c + (a,)
    return (0, 0, 0, 0)


@cell(0, 1)
def banner_silk(x, y, rng):
    c = (236, 232, 222)
    if x in (0, 1, 14, 15):
        c = (200, 196, 186)  # border, tinted darker by the caller's colour
    if 4 <= x <= 11 and 3 <= y <= 12:
        r = math.hypot(x - 7.5, y - 7.5)
        if 3.0 < r < 4.2:
            c = (176, 170, 160)
        if abs(x - 7.5) < 0.6 and 4 < y < 11:
            c = (150, 144, 136)
    return shade(c, 0.97 + rng.random() * 0.04) + (255,)


@cell(1, 1)
def awning(x, y, rng):
    c = (196, 40, 36) if (x // 4) % 2 == 0 else (240, 230, 214)
    if y == 15 and x % 4 in (1, 2):
        c = (220, 180, 70)  # scalloped gold fringe
    return c + (255,)


@cell(2, 1)
def blue_cloth(x, y, rng):
    c = mix((40, 70, 130), (70, 100, 168), 0.5 + 0.5 * math.sin(x * 0.6 + y * 0.2))
    return c + (255,)


@cell(3, 1)
def tassel(x, y, rng):
    c = mix((120, 14, 20), (226, 52, 50), 0.5 + 0.5 * math.sin(x * 2.1))
    if y < 3:
        c = (230, 190, 80)
    return c + (255,)


def smoke(seed):
    n = noise(seed)

    def p(x, y, rng):
        r = math.hypot(x - 7.5, y - 7.5) / 7.5
        v = n(x * 0.6, y * 0.6)
        a = max(0.0, 1.0 - r * 1.1) * (0.4 + 0.6 * v)
        g = 200 + 40 * v
        return (g, g, g + 6, int(200 * a))
    return p


for i in range(3):
    CELLS[(4 + i, 1)] = smoke(70 + i)


@cell(7, 1)
def star(x, y, rng):
    cx, cy = x - 7.5, y - 7.5
    r = math.hypot(cx, cy)
    k = max(0.0, 1.0 - r / 7.5)
    ray = max(0.0, 1.0 - min(abs(cx), abs(cy)) / 1.2) * max(0.0, 1.0 - r / 7.5)
    a = min(1.0, k * k * 1.5 + ray)
    return (255, 255, 230, int(255 * a))


def firework(frame):
    """Frame 0: the bright shell; 1-4: twelve sparks fly out with fading trails; 5-7: glitter drooping and going out."""
    sparks = []
    r0 = random.Random(9001)
    for k in range(12):
        ang = k * math.pi / 6.0 + r0.uniform(-0.12, 0.12)
        sparks.append((ang, r0.uniform(0.85, 1.0)))
    glitter = []
    r1 = random.Random(9100 + frame)
    for _ in range(26 - frame * 3):
        ang = r1.uniform(0.0, 2.0 * math.pi)
        rad = r1.uniform(3.0, 6.8)
        glitter.append((7.5 + math.cos(ang) * rad, 7.5 + math.sin(ang) * rad + (frame - 4) * 0.7, r1.random()))

    def p(x, y, rng):
        cx, cy = x - 7.5, y - 7.5
        r = math.hypot(cx, cy)
        a = 0.0
        if frame == 0:
            a = max(0.0, 1.0 - r / 2.6)
        elif frame < 5:
            reach = 1.6 + frame * 1.35
            for ang, sp in sparks:
                hx, hy = math.cos(ang) * reach * sp, math.sin(ang) * reach * sp
                d = math.hypot(cx - hx, cy - hy)
                if d < 1.0:
                    a = max(a, 1.0 - d * 0.4)
                # the trail back toward the centre
                t = (cx * math.cos(ang) + cy * math.sin(ang))
                off = abs(-cx * math.sin(ang) + cy * math.cos(ang))
                if 0.0 < t < reach * sp and off < 0.55:
                    a = max(a, 0.55 * (t / (reach * sp)))
            if r < 1.2 and frame < 3:
                a = max(a, 0.8)
        else:
            for gx, gy, w in glitter:
                d = math.hypot(x + 0.5 - gx, y + 0.5 - gy)
                if d < 0.75:
                    a = max(a, (1.0 - (frame - 5) * 0.28) * (0.6 + 0.4 * w))
        return (255, 255, 255, int(255 * max(0.0, min(1.0, a))))
    return p


for f in range(8):
    CELLS[(f, 2)] = firework(f)


@cell(0, 3)
def jade_orb(x, y, rng):
    c = mix((40, 120, 90), (150, 226, 190), 0.5 + 0.4 * math.sin(x * 0.5) * math.cos(y * 0.4))
    if (x * 5 + y * 3) % 17 == 0:
        c = (255, 250, 220)  # star points
    return c + (255,)


@cell(1, 3)
def spray(x, y, rng):
    """Water drops thrown off the wheel: scattered droplets, denser at the top, each with a light core."""
    h = random.Random(x * 131 + y * 977 + 5).random()
    a = 0.0
    if h < 0.30 * (1.0 - y / 18.0):
        a = 0.85
    elif h < 0.42 * (1.0 - y / 18.0):
        a = 0.35
    c = (226, 242, 255) if a > 0.5 else (170, 210, 240)
    return c + (int(255 * a),)


@cell(2, 3)
def rim_gold(x, y, rng):
    c = (200, 150, 60) if y % 4 else (120, 80, 30)
    return c + (255,)


@cell(3, 3)
def cloud_motif(x, y, rng):
    cx, cy = x - 7.5, y - 7.5
    r = math.hypot(cx, cy)
    ang = math.atan2(cy, cx)
    if 1.0 < r < 6.5 and (r - 1.0 * ang) % 3.0 < 0.9:
        return (255, 255, 255, 220)
    return (0, 0, 0, 0)


def main():
    img = Image(128, 128)
    for (col, row), fn in CELLS.items():
        rng = random.Random(col * 31 + row * 7)
        for y in range(CELL):
            for x in range(CELL):
                c = fn(x, y, rng)
                img.set(col * CELL + x, row * CELL + y, (c[0], c[1], c[2], c[3] if len(c) > 3 else 255))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", os.path.normpath(OUT), len(CELLS), "cells")
    if len(sys.argv) > 1:
        os.makedirs(sys.argv[1], exist_ok=True)
        big = Image(128 * 4, 64 * 4)
        for y in range(64 * 4):
            for x in range(128 * 4):
                r, g, b, a = img.get(x // 4, y // 4)
                bg = 40 if ((x // 8 + y // 8) % 2) else 70
                k = a / 255.0
                big.set(x, y, (r * k + bg * (1 - k), g * k + bg * (1 - k), b * k + bg * (1 - k), 255))
        big.save(os.path.join(sys.argv[1], "hias_x4.png"))


if __name__ == "__main__":
    main()
