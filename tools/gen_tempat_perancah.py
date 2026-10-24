"""Generates the repair-scaffold atlas of DESIGN 38.R.3 (module 38PR): bamboo poles, planks, rope lashings, tarps, the
"Mohon Bantuan" notice, dust and soot, drawn by client/dunia/tempat/perbaikan/Perancah as camera-relative custom
geometry (vanilla entity render types only, never blocks).

textures/dunia/tempat/perancah.png   64 x 64, sixteen-pixel cells (column, row) = Perancah.Sel:
  row 0: 0 bamboo pole (u around, v along, nodes)   1 smoke-blackened bamboo   2 plank deck (two boards, nails)
         3 rope lashing (alpha, hemp wraps)
  row 1: 0 woven reed mat (alpha frayed edge)       1 indigo cloth tarp (stitched hem)   2 notice paper (brush columns,
         red seal, torn corners)                    3 dust puff (alpha)
  row 2: 0 ladder (alpha rails and rungs)            1 crew pennant (alpha, red with gold hammer)
         2 soot puff (alpha)                         3 sawdust specks (alpha)
  row 3: 0 charred plank   1 worn grey mat (alpha)   2 red ribbon (progress tag)   3 paper lantern (lit)

Run with: python tools/gen_tempat_perancah.py [preview_dir]   (8x preview into preview_dir). Deterministic.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "dunia", "tempat", "perancah.png")
CELL = 16
CELLS = {}


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, f):
    return tuple(max(0.0, min(255.0, v * f)) for v in c[:3])


def cell(col, row):
    def deco(fn):
        CELLS[(col, row)] = fn
        return fn
    return deco


def bamboo_base(x, y, rng):
    # cylinder shading across u, a light stripe, fibre streaks along v, a node ring every 8 px
    round_ = math.sin((x + 0.5) / 16.0 * math.pi)
    c = mix((116, 120, 56), (206, 192, 110), round_)
    if x in (5, 6):
        c = mix(c, (236, 226, 150), 0.45)
    if (x * 7 + y // 3) % 11 == 0:
        c = shade(c, 0.9)
    if y % 8 in (0, 1):
        c = mix(c, (96, 88, 40), 0.65 if y % 8 == 0 else 0.35)
    if y % 8 == 2:
        c = mix(c, (230, 220, 150), 0.3)
    return shade(c, 0.96 + rng.random() * 0.06)


@cell(0, 0)
def bamboo(x, y, rng):
    return bamboo_base(x, y, rng) + (255,)


@cell(1, 0)
def bamboo_char(x, y, rng):
    c = bamboo_base(x, y, rng)
    burn = 0.55 + 0.35 * math.sin(y * 0.7 + x * 0.3) + rng.random() * 0.2
    c = mix(c, (34, 28, 24), burn)
    if rng.random() < 0.05:
        c = (180, 72, 24)  # a last ember
    return c + (255,)


def plank_base(x, y, rng, burnt=False):
    board = y // 8
    grain = 0.5 + 0.5 * math.sin(x * 0.55 + board * 2.1 + math.sin(y * 1.3 + x * 0.2) * 1.2)
    c = mix((120, 84, 48), (172, 128, 78), grain)
    if board == 1:
        c = shade(c, 0.9)
    if y % 8 == 7:
        c = (64, 42, 24)  # the gap between boards
    if y % 8 == 0:
        c = mix(c, (198, 156, 102), 0.35)
    if x in (2, 13) and y % 8 in (3, 4):
        c = (70, 70, 76) if y % 8 == 3 else (130, 130, 136)  # nails
    if (x * 5 + y * 3) % 23 == 0:
        c = shade(c, 0.8)  # knots
    if burnt:
        c = mix(c, (30, 24, 20), 0.55 + 0.3 * math.sin(x * 0.4 + y))
    return shade(c, 0.95 + rng.random() * 0.08)


@cell(2, 0)
def plank(x, y, rng):
    return plank_base(x, y, rng) + (255,)


@cell(3, 0)
def lashing(x, y, rng):
    # three diagonal hemp wraps across the middle band, frayed ends, transparent elsewhere
    if not 3 <= y <= 12:
        return (0, 0, 0, 0)
    d = (x + y) % 6
    if d >= 4:
        return (0, 0, 0, 0)
    c = mix((150, 118, 70), (214, 184, 124), 0.3 + 0.25 * d)
    if (x * 3 + y) % 5 == 0:
        c = shade(c, 0.82)  # twist of the strands
    if y in (3, 12) and rng.random() < 0.5:
        return (0, 0, 0, 0)
    return c + (255,)


@cell(0, 1)
def reed_mat(x, y, rng):
    if (y == 15 and x % 3 == 0) or (y == 14 and x % 5 == 1):
        return (0, 0, 0, 0)  # frayed lower edge
    over = ((x // 2) + (y // 2)) % 2 == 0
    c = (196, 170, 104) if over else (160, 134, 76)
    if over and x % 2 == 0:
        c = shade(c, 1.08)
    if not over and y % 2 == 1:
        c = shade(c, 0.88)
    if y == 0:
        c = (110, 88, 50)  # the bound top edge
    return shade(c, 0.95 + rng.random() * 0.08) + (255,)


@cell(1, 1)
def indigo_tarp(x, y, rng):
    fold = 0.5 + 0.5 * math.sin(x * 0.75)
    c = mix((26, 40, 88), (58, 80, 140), fold)
    if x in (1, 14) or y in (1, 14):
        if (x + y) % 2 == 0:
            c = (200, 196, 176)  # running stitch of the hem
    if x in (0, 15) or y in (0, 15):
        c = shade(c, 0.7)
    if 6 <= x <= 9 and 6 <= y <= 9 and (x in (6, 9) or y in (6, 9)):
        c = (208, 200, 170)  # a white guild square
    return shade(c, 0.95 + rng.random() * 0.06) + (255,)


@cell(2, 1)
def notice(x, y, rng):
    if (x, y) in ((0, 0), (1, 0), (0, 1), (15, 15), (14, 15), (15, 14), (15, 0)):
        return (0, 0, 0, 0)  # torn corners
    c = mix((236, 222, 184), (214, 196, 150), (x + y) / 30.0)
    if x in (1, 14) or y in (1, 14):
        c = (178, 40, 32)  # red border
    # three vertical columns of brush strokes (right to left)
    for col, x0 in enumerate((11, 8, 5)):
        if x0 <= x <= x0 + 1 and 3 <= y <= 12:
            stroke = (y * 7 + col * 5 + x) % 9
            if stroke < 5:
                c = (28, 24, 22) if stroke < 3 else (70, 60, 52)
    if 2 <= x <= 4 and 10 <= y <= 12:
        c = (196, 36, 30) if (x + y) % 3 else (230, 90, 70)  # the seal
    return shade(c, 0.97 + rng.random() * 0.05) + (255,)


def puff(x, y, rng, rgb, strength):
    dx = (x - 7.5) / 7.5
    dy = (y - 7.5) / 7.5
    d = math.sqrt(dx * dx + dy * dy)
    lump = 0.07 * math.sin(math.atan2(dy, dx) * 7.0) + 0.05 * math.sin(math.atan2(dy, dx) * 3.0 + 1.0)
    a = max(0.0, 1.0 - d / (0.85 + lump))
    a = a * a * strength * (0.85 + rng.random() * 0.3)
    return rgb + (int(max(0, min(255, a * 255))),)


@cell(3, 1)
def dust(x, y, rng):
    return puff(x, y, rng, (196, 180, 150), 0.9)


@cell(0, 2)
def ladder(x, y, rng):
    if x in (2, 3, 12, 13):
        c = mix((120, 90, 52), (176, 140, 88), 0.5 if x in (3, 13) else 0.0)
        return c + (255,)
    if y % 5 == 2 and 3 < x < 12:
        return mix((140, 104, 60), (186, 150, 96), (x % 3) / 3.0) + (255,)
    if y % 5 == 3 and x in (4, 11):
        return (150, 118, 70, 255)  # the rope tying each rung
    return (0, 0, 0, 0)


@cell(1, 2)
def pennant(x, y, rng):
    # a banner hanging from its top edge (v0 at the cross-pole), red with a gold hammer, a swallow tail below
    if not 1 <= x <= 14:
        return (0, 0, 0, 0)
    if y >= 10 and abs(x - 7.5) < (y - 9) * 1.1:
        return (0, 0, 0, 0)
    c = mix((150, 20, 20), (214, 46, 36), 0.5 + 0.5 * math.sin(x * 0.6 + y * 0.15))
    if y in (0, 1):
        c = (110, 84, 46)  # the sleeve round the pole
    elif x in (1, 14):
        c = (232, 190, 92)  # gold trim
    if 4 <= x <= 11 and y in (3, 4):
        c = (246, 208, 100)  # hammer head
    if 7 <= x <= 8 and 5 <= y <= 9:
        c = (222, 178, 82)  # handle
    return c + (255,)


@cell(2, 2)
def soot(x, y, rng):
    return puff(x, y, rng, (40, 36, 34), 0.8)


@cell(3, 2)
def sawdust(x, y, rng):
    if rng.random() < 0.08:
        return mix((210, 170, 110), (246, 220, 170), rng.random()) + (230,)
    return (0, 0, 0, 0)


@cell(0, 3)
def plank_char(x, y, rng):
    return plank_base(x, y, rng, burnt=True) + (255,)


@cell(1, 3)
def worn_mat(x, y, rng):
    if rng.random() < 0.06 or (y > 12 and rng.random() < 0.3):
        return (0, 0, 0, 0)  # holes and a ragged edge
    over = ((x // 2) + (y // 2)) % 2 == 0
    c = (132, 126, 112) if over else (104, 98, 88)
    if (x * 3 + y * 5) % 17 == 0:
        c = (70, 64, 58)  # stains
    return shade(c, 0.95 + rng.random() * 0.08) + (255,)


@cell(2, 3)
def ribbon(x, y, rng):
    if not 2 <= x <= 13:
        return (0, 0, 0, 0)
    c = mix((168, 24, 24), (222, 52, 40), 0.5 + 0.5 * math.sin(y * 0.8))
    if x in (2, 13):
        c = (232, 196, 100)
    return c + (255,)


@cell(3, 3)
def lantern(x, y, rng):
    dx = (x - 7.5) / 6.0
    if y in (0, 1, 14, 15):
        return ((60, 40, 24, 255) if 5 <= x <= 10 else (0, 0, 0, 0))  # wooden caps
    if abs(dx) > 1.0 - 0.15 * abs(y - 7.5) / 7.5:
        return (0, 0, 0, 0)
    glow = 1.0 - abs(dx) * 0.6 - abs(y - 7.5) / 20.0
    c = mix((196, 44, 24), (255, 196, 110), glow)
    if y in (4, 11):
        c = shade(c, 0.75)  # ribs
    return c + (255,)


def main():
    img = Image(64, 64)
    for (col, row), fn in sorted(CELLS.items()):
        rng = random.Random(col * 131 + row * 977 + 38)
        for y in range(CELL):
            for x in range(CELL):
                img.set(col * CELL + x, row * CELL + y, fn(x, y, rng))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    if len(sys.argv) > 1:
        big = Image(64 * 8, 64 * 8)
        for y in range(64 * 8):
            for x in range(64 * 8):
                p = img.get(x // 8, y // 8)
                if p[3] == 0:
                    p = (40, 44, 52, 255) if ((x // 16) + (y // 16)) % 2 else (60, 64, 72, 255)
                big.set(x, y, p)
        os.makedirs(sys.argv[1], exist_ok=True)
        big.save(os.path.join(sys.argv[1], "perancah_8x.png"))
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
