"""Generates the gear atlas of module K's people (DESIGN 34.5.1-34.5.4, detail pass): hats, crowns, helmets, armour,
aprons, sashes, scabbards, gourds, fans, whisks, baskets and flower crowns, drawn by client/dunia/kota/KotaGearLayer.

textures/dunia/kota/entity/perlengkapan.png   64 x 512: sixteen material bands of 32 px (KotaGear.BAND_*). Every cube of
a gear model takes its UV from texOffs(0, band * 32), so its unwrap (2 (w + d) wide, d + h tall) stays inside its band
(w + d <= 32, d + h <= 32). The materials tile, so any part of a band reads as the material:
   0 black lacquer (caps, the official's hat)       8 lacquered wood (scabbards, hilts, handles)
   1 gold (trims, crowns, pins, guards)              9 cloth, near white (tinted per NPC: sashes, cloaks, headbands)
   2 jade (guan crowns, pendants, leaves)           10 peach blossom (the garden keeper's crown, peaches)
   3 crimson silk (plumes, tassels, cords)          11 gourd ochre (wine and pill gourds)
   4 white silk and hair (beards, towels, whisks)   12 bronze (an abacus frame, bells, buckles)
   5 woven straw (douli and sun hats, baskets)      13 paper (fans)
   6 leather (aprons, belts, bracers, straps)       14 black hair (buns, topknots, long hair)
   7 steel lamellar (helmets, plates, pauldrons)    15 dark gauze (a wanderer's hat veil)

Run with: python tools/gen_dunia_kota_gear.py [preview_dir]   (8x preview into preview_dir). Deterministic.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures", "dunia", "kota",
                    "entity")
W, BAND = 64, 32
BANDS = 16


def clamp(v, lo=0.0, hi=255.0):
    return max(lo, min(hi, v))


def shade(rgb, f):
    return tuple(clamp(c * f) for c in rgb)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def noise2(rng, w, h, cell):
    """Smooth value noise in 0..1 (bilinear over a coarse grid), tileable."""
    gw, gh = max(1, w // cell), max(1, h // cell)
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]

    def at(x, y):
        fx, fy = x / cell, y / cell
        x0, y0 = int(fx) % gw, int(fy) % gh
        x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] + (grid[y0][x1] - grid[y0][x0]) * tx
        b = grid[y1][x0] + (grid[y1][x1] - grid[y1][x0]) * tx
        return a + (b - a) * ty

    return at


def band(img, index, painter, seed):
    rng = random.Random(seed)
    oy = index * BAND
    for y in range(BAND):
        for x in range(W):
            rgb = painter(x, y, rng)
            a = 255
            if len(rgb) == 4:
                a = rgb[3]
                rgb = rgb[:3]
            img.set(x, oy + y, (rgb[0], rgb[1], rgb[2], a))


def lacquer(seed):
    n = noise2(random.Random(seed), W, BAND, 8)

    def p(x, y, rng):
        base = (24, 22, 26)
        sheen = 1.0 + 0.35 * max(0.0, math.sin((x + y * 0.6) * 0.35)) ** 6 + 0.12 * n(x, y)
        if y % 16 == 15:
            sheen *= 0.7
        return shade(base, sheen + rng.random() * 0.06)
    return p


def gold(seed):
    n = noise2(random.Random(seed), W, BAND, 4)

    def p(x, y, rng):
        t = n(x, y)
        c = mix((168, 118, 30), (255, 214, 96), t)
        if (x + y) % 9 == 0:
            c = mix(c, (255, 244, 190), 0.6)
        if y % 8 == 7:
            c = shade(c, 0.78)
        return shade(c, 0.95 + rng.random() * 0.1)
    return p


def jade(seed):
    n = noise2(random.Random(seed), W, BAND, 6)
    m = noise2(random.Random(seed + 1), W, BAND, 3)

    def p(x, y, rng):
        t = n(x, y)
        c = mix((46, 132, 96), (150, 226, 178), t)
        if m(x, y) > 0.78:
            c = mix(c, (232, 252, 236), 0.5)  # cloudy inclusions
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def silk(rgb, seed, stripe=6):
    n = noise2(random.Random(seed), W, BAND, 5)

    def p(x, y, rng):
        c = shade(rgb, 0.85 + 0.25 * n(x, y))
        if (x + y) % stripe == 0:
            c = shade(c, 1.12)
        if y % 2 == 0 and x % 2 == 0:
            c = shade(c, 0.95)
        return shade(c, 0.96 + rng.random() * 0.06)
    return p


def straw(seed):
    def p(x, y, rng):
        weave = ((x // 2) + (y // 2)) % 2
        base = (206, 170, 98) if weave else (178, 140, 72)
        c = shade(base, 0.9 + 0.15 * math.sin(x * 1.7 + y * 0.3) * 0.5 + rng.random() * 0.1)
        if x % 2 == (y // 2) % 2:
            c = shade(c, 0.88)
        return c
    return p


def leather(seed):
    n = noise2(random.Random(seed), W, BAND, 4)

    def p(x, y, rng):
        c = mix((82, 52, 30), (138, 92, 54), n(x, y))
        if y % 16 in (1, 14) and x % 4 < 2:
            c = (196, 170, 120)  # stitches
        return shade(c, 0.92 + rng.random() * 0.12)
    return p


def lamellar(seed):
    def p(x, y, rng):
        lx, ly = x % 4, y % 6
        c = (150, 156, 168)
        if ly == 0:
            c = (70, 74, 84)  # the lace row
        elif lx == 0:
            c = (98, 102, 114)
        else:
            c = shade(c, 1.15 - 0.08 * ly)
        if lx == 2 and ly == 2:
            c = (226, 210, 150)  # a brass rivet
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def wood(seed):
    rng0 = random.Random(seed)
    phase = [rng0.random() * 6.28 for _ in range(BAND)]

    def p(x, y, rng):
        g = math.sin(y * 0.9 + math.sin(x * 0.15 + phase[y]) * 2.0)
        c = mix((64, 22, 18), (118, 46, 32), 0.5 + 0.5 * g)
        if (x * 7 + y * 3) % 23 == 0:
            c = shade(c, 1.3)
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def cloth(seed):
    n = noise2(random.Random(seed), W, BAND, 6)

    def p(x, y, rng):
        v = 214 + 30 * n(x, y)
        if (x + 2 * y) % 5 == 0:
            v *= 0.93
        if y % 16 == 0:
            v *= 0.86  # a hem line
        v *= 0.97 + rng.random() * 0.05
        return (v, v, v)
    return p


def peach(seed):
    rng0 = random.Random(seed)
    blossoms = [(rng0.randrange(W), rng0.randrange(BAND)) for _ in range(70)]

    def p(x, y, rng):
        c = (236, 150, 172)
        best = 99.0
        for bx, by in blossoms:
            d = math.hypot((x - bx + W / 2) % W - W / 2, (y - by + BAND / 2) % BAND - BAND / 2)
            best = min(best, d)
        if best < 0.8:
            c = (255, 230, 140)  # the heart of a flower
        elif best < 2.2:
            c = (255, 206, 222)
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def gourd(seed):
    n = noise2(random.Random(seed), W, BAND, 7)

    def p(x, y, rng):
        c = mix((176, 120, 44), (226, 176, 82), n(x, y))
        if x % 8 == 0:
            c = shade(c, 0.86)  # ribs
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def bronze(seed):
    n = noise2(random.Random(seed), W, BAND, 3)

    def p(x, y, rng):
        c = mix((120, 78, 36), (196, 138, 70), n(x, y))
        if n((x * 3) % W, (y * 5) % BAND) > 0.82:
            c = mix(c, (86, 150, 120), 0.6)  # verdigris
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def paper(seed):
    def p(x, y, rng):
        v = 232 + rng.random() * 16
        c = (v, v * 0.97, v * 0.9)
        if x % 6 == 0:
            c = shade(c, 0.86)  # the folds of a fan
        if (x // 6) % 3 == 1 and 10 < y < 22 and (x + y) % 3 == 0:
            c = (60, 56, 60)  # a brushed stroke
        return c
    return p


def hair(seed):
    rng0 = random.Random(seed)
    phase = [rng0.random() * 6.28 for _ in range(W)]

    def p(x, y, rng):
        g = 0.5 + 0.5 * math.sin(y * 0.4 + phase[x])
        c = mix((14, 12, 16), (52, 44, 50), g * 0.7)
        if x % 3 == 0:
            c = shade(c, 1.25)
        return shade(c, 0.95 + rng.random() * 0.1)
    return p


def gauze(seed):
    def p(x, y, rng):
        if (x + y) % 2 == 0:
            return (38, 36, 44)
        return (64, 62, 72)
    return p


PAINTERS = [lacquer(1), gold(2), jade(3), silk((176, 26, 34), 4), silk((236, 234, 228), 5, stripe=9), straw(6), leather(7), lamellar(8),
            wood(9), cloth(10), peach(11), gourd(12), bronze(13), paper(14), hair(15), gauze(16)]


def main():
    img = Image(W, BAND * BANDS)
    for i, painter in enumerate(PAINTERS):
        band(img, i, painter, 1000 + i)
    os.makedirs(ROOT, exist_ok=True)
    out = os.path.join(ROOT, "perlengkapan.png")
    img.save(out)
    print("wrote", os.path.normpath(out))
    if len(sys.argv) > 1:
        os.makedirs(sys.argv[1], exist_ok=True)
        big = Image(W * 4, BAND * BANDS * 4)
        for y in range(BAND * BANDS * 4):
            for x in range(W * 4):
                big.set(x, y, img.get(x // 4, y // 4))
        big.save(os.path.join(sys.argv[1], "perlengkapan_x4.png"))


if __name__ == "__main__":
    main()
