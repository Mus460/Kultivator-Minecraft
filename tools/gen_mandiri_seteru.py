"""Generates the assets of module 39F, Perseteruan antar-sekte (DESIGN 39.F.9). Everything is drawn here; nothing is
downloaded. Deterministic (fixed seeds): a second run writes byte-identical files.

Writes (under src/main/resources/assets/kultivator/):
  textures/sekte/mandiri/seteru/ikon.png     128x64 icon atlas, 8 x 4 cells of 16x16 (the screen's icons):
      row 0  causes 0-7: tea-house insult (cracked cup), treasure (glowing herb), guild (notice board), tournament
             (lei tai stage), love rival (two hearts), stolen scroll, market bully (fist over a stall), village (hut)
      row 1  8 D event (sect banner torn), 9 active feud (crossed swords), 10 peace (lotus), 11 vendetta (clenched
             fist with a red knot), 12 lifelong rivals (two blades meeting), 13 forbidden love (red thread heart),
             14 parted (broken heart), 15 elopement (two figures under a moon)
      row 2  the master's actions 0-7: letter of peace, elder sent, punishment rod, scales of compensation, a fist
             raised in support, crossed swords before a gate, lock and chain (forbid), open hand (let be)
      row 3  24 blessing (two cups), 25 parting (a single cup), 26 patrol banner, 27 ambush (eyes in grass), 28 bandage,
             29 heat flame, 30 lock, 31 handshake (friend from another sect)
  textures/sekte/mandiri/seteru/kartu.png    256x128 title-card sheet: two torn ink-band halves (0,0 and 128,0, 128x48),
      the red crossed-swords seal (0,48 64x64), a soft glow (64,48 64x64, tinted at run time), heat wisps
      (128,48 128x32)
  textures/item/fx/sekte_mandiri_seteru_*.png  16x16 materials (linen bandage, three banner silks, pole wood, bronze,
      scroll paper, wax seal, red silk cord, leather, gold, celadon, rope)
  models/item/fx/sekte_mandiri_seteru_<name>.json + items/fx/sekte_mandiri_seteru_<name>.json  props 130-140 in the order
      of SeteruIds.PROP_NAMA (anchor (8, 8, 8), front to the north, +y up; 38.O.0)
And a preview sheet (props unwrapped + atlas x4) to the path given as the first argument, if any.

Run: python tools/gen_mandiri_seteru.py [preview.png]
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
SETERU_DIR = os.path.join(ROOT, "textures", "sekte", "mandiri", "seteru")
FX_TEX_DIR = os.path.join(ROOT, "textures", "item", "fx")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")
PREFIX = "sekte_mandiri_seteru_"

SUB = 4

PROP_NAMA = ["perban", "panji_lurus", "panji_netral", "panji_iblis", "gulungan_curian", "surat_tantangan", "kantong_ganti_rugi",
             "cawan_pecah", "tali_merah", "kruk", "tiang_panji"]

# palette
INK = (34, 22, 20)
PAPER = (244, 232, 204)
GOLD = (222, 176, 70)
GOLD_D = (150, 104, 30)
RED = (186, 38, 30)
RED_D = (110, 18, 14)
JADE = (70, 170, 120)
JADE_D = (24, 90, 60)
WOOD = (110, 70, 40)
WOOD_D = (64, 38, 22)
STEEL = (196, 206, 218)
STEEL_D = (110, 120, 136)
SKIN = (236, 196, 160)
PINK = (240, 120, 150)
WHITE = (250, 250, 246)
BLUE = (90, 140, 210)
GREEN = (110, 190, 90)


# ================================================================================================ helpers

def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def mix(a, b, t):
    t = clamp(t)
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def coverage(fn, x, y):
    n = 0
    for i in range(SUB):
        for j in range(SUB):
            if fn(x + (i + 0.5) / SUB, y + (j + 0.5) / SUB):
                n += 1
    return n / float(SUB * SUB)


def paint(img, ox, oy, w, h, fn, color, alpha=1.0):
    """Blends shape fn (local coordinates) over the w x h box at (ox, oy); color = rgb or fn(x, y) -> rgb."""
    for y in range(h):
        for x in range(w):
            c = coverage(fn, x, y)
            if c <= 0.0:
                continue
            rgb = color(x + 0.5, y + 0.5) if callable(color) else color
            img.blend(ox + x, oy + y, (rgb[0], rgb[1], rgb[2], int(round(255 * c * alpha))))


def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ring(cx, cy, r0, r1):
    return lambda x, y: r0 * r0 <= (x - cx) ** 2 + (y - cy) ** 2 <= r1 * r1


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def rect(x0, y0, x1, y1):
    return lambda x, y: x0 <= x <= x1 and y0 <= y <= y1


def seg(x0, y0, x1, y1, w):
    dx, dy = x1 - x0, y1 - y0
    ll = dx * dx + dy * dy

    def f(x, y):
        t = 0.0 if ll == 0 else clamp(((x - x0) * dx + (y - y0) * dy) / ll)
        px, py = x0 + t * dx, y0 + t * dy
        return (x - px) ** 2 + (y - py) ** 2 <= (w / 2.0) ** 2
    return f


def poly(points):
    def f(x, y):
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
    return f


def union(*fs):
    return lambda x, y: any(f(x, y) for f in fs)


def minus(a, b):
    return lambda x, y: a(x, y) and not b(x, y)


def heart(cx, cy, s):
    def f(x, y):
        u = (x - cx) / s
        v = -(y - cy) / s + 0.25
        return (u * u + v * v - 0.35) ** 3 - u * u * v * v * v <= 0.0
    return f


def outline(img, ox, oy, w, h, fn, color, width=0.9):
    """A dark rim: the shape grown by width minus the shape."""
    def grown(x, y):
        for dx, dy in ((width, 0), (-width, 0), (0, width), (0, -width), (width * 0.7, width * 0.7), (-width * 0.7, width * 0.7),
                       (width * 0.7, -width * 0.7), (-width * 0.7, -width * 0.7)):
            if fn(x + dx, y + dy):
                return True
        return False
    paint(img, ox, oy, w, h, minus(grown, fn), color, 0.85)


def shaded(base, dark, light, cx, cy, r):
    """A soft top-left light over a disc-like shape."""
    def col(x, y):
        t = clamp(((x - cx) + (y - cy)) / (2.2 * r) + 0.5)
        c = mix(light, base, clamp(t * 1.6))
        return mix(c, dark, clamp((t - 0.6) * 2.0))
    return col


# ================================================================================================ the icon atlas

def icon(img, cell, draw):
    ox = (cell % 8) * 16
    oy = (cell // 8) * 16
    draw(img, ox, oy)


def i_hinaan(img, ox, oy):  # a cracked teacup tipped over, tea spilling
    cup = union(ellipse(8, 7, 5.2, 1.6), poly([(2.8, 7), (13.2, 7), (11.2, 12.6), (4.8, 12.6)]))
    outline(img, ox, oy, 16, 16, cup, INK)
    paint(img, ox, oy, 16, 16, cup, shaded((160, 206, 182), (90, 140, 120), (220, 240, 228), 8, 9, 5))
    paint(img, ox, oy, 16, 16, ellipse(8, 7, 4.4, 1.0), (120, 132, 40))
    paint(img, ox, oy, 16, 16, union(seg(7, 8, 9.2, 10.2, 0.7), seg(9.2, 10.2, 8.2, 12.2, 0.7)), INK)
    paint(img, ox, oy, 16, 16, union(ellipse(12.6, 13.6, 2.6, 0.9), circle(14.4, 12.4, 0.7)), (150, 160, 60), 0.8)


def i_harta(img, ox, oy):  # a glowing spirit herb in a beam of light
    paint(img, ox, oy, 16, 16, ellipse(8, 7, 6.5, 6.5), (255, 236, 150), 0.35)
    paint(img, ox, oy, 16, 16, seg(8, 14.5, 8, 6, 1.0), (60, 130, 50))
    for a, l in ((-0.9, 4.2), (0.9, 4.2), (-0.4, 3.2), (0.4, 3.2)):
        paint(img, ox, oy, 16, 16, poly([(8, 11), (8 + math.sin(a) * l, 11 - math.cos(a) * l), (8 + math.sin(a) * l * 0.4 + 0.8, 10.4)]),
              (100, 200, 80))
    paint(img, ox, oy, 16, 16, circle(8, 5, 2.0), shaded((255, 120, 90), (180, 40, 40), (255, 220, 200), 8, 5, 2))
    paint(img, ox, oy, 16, 16, ellipse(8, 14.6, 4.0, 0.9), (90, 70, 50))


def i_gilda(img, ox, oy):  # a notice board with two papers pinned, one torn away
    board = rect(2, 3, 14, 11)
    outline(img, ox, oy, 16, 16, board, INK)
    paint(img, ox, oy, 16, 16, board, WOOD)
    paint(img, ox, oy, 16, 16, union(rect(3, 11, 4, 15), rect(12, 11, 13, 15)), WOOD_D)
    paint(img, ox, oy, 16, 16, rect(3.5, 4, 7.5, 9.5), PAPER)
    paint(img, ox, oy, 16, 16, poly([(9, 4), (13, 4), (13, 7), (11.6, 8.4), (10.4, 6.8), (9, 8)]), PAPER)
    for y in (5.5, 6.8, 8.1):
        paint(img, ox, oy, 16, 16, rect(4.2, y, 6.8, y + 0.4), INK, 0.7)
    paint(img, ox, oy, 16, 16, union(circle(5.5, 4.1, 0.6), circle(11, 4.1, 0.6)), RED)


def i_turnamen(img, ox, oy):  # the lei tai stage with two banners
    stage = poly([(1.5, 11), (14.5, 11), (13, 14), (3, 14)])
    outline(img, ox, oy, 16, 16, stage, INK)
    paint(img, ox, oy, 16, 16, stage, (170, 60, 40))
    paint(img, ox, oy, 16, 16, rect(1.5, 10, 14.5, 11), GOLD)
    paint(img, ox, oy, 16, 16, union(seg(3, 10, 3, 2, 0.8), seg(13, 10, 13, 2, 0.8)), WOOD_D)
    paint(img, ox, oy, 16, 16, poly([(3.4, 2.4), (7, 3.2), (3.4, 6.4)]), JADE)
    paint(img, ox, oy, 16, 16, poly([(12.6, 2.4), (9, 3.2), (12.6, 6.4)]), RED)
    paint(img, ox, oy, 16, 16, union(seg(6.4, 9.6, 9.6, 6.4, 0.7), seg(6.4, 6.4, 9.6, 9.6, 0.7)), STEEL)


def i_asmara(img, ox, oy):  # two hearts, one cracked, a rival's shadow
    paint(img, ox, oy, 16, 16, heart(6, 7.5, 4.2), (90, 40, 60), 0.8)
    h = heart(10, 8.5, 4.6)
    outline(img, ox, oy, 16, 16, h, INK)
    paint(img, ox, oy, 16, 16, h, shaded(PINK, (180, 50, 80), (255, 200, 210), 10, 8.5, 4))
    paint(img, ox, oy, 16, 16, union(seg(10, 6, 9, 8.4, 0.6), seg(9, 8.4, 10.6, 10.4, 0.6)), INK)


def i_gulungan(img, ox, oy):  # a scroll snatched by a hand (stolen)
    body = rect(3, 6, 12, 10)
    outline(img, ox, oy, 16, 16, body, INK)
    paint(img, ox, oy, 16, 16, body, PAPER)
    paint(img, ox, oy, 16, 16, union(rect(2, 5.4, 3.4, 10.6), rect(11.6, 5.4, 13, 10.6)), WOOD)
    paint(img, ox, oy, 16, 16, rect(6.8, 6, 8.2, 10), RED)
    paint(img, ox, oy, 16, 16, union(ellipse(13.2, 12.4, 2.4, 1.8), seg(12, 11, 9, 9.6, 1.2)), SKIN)
    paint(img, ox, oy, 16, 16, union(seg(1, 3, 3, 4.4, 0.5), seg(1, 5, 2.6, 5.4, 0.5)), INK, 0.6)


def i_perundung(img, ox, oy):  # a fist over a small stall
    stall = union(rect(2, 10, 14, 14), poly([(1, 10), (15, 10), (13, 7.6), (3, 7.6)]))
    paint(img, ox, oy, 16, 16, stall, mix(WOOD, PAPER, 0.3))
    paint(img, ox, oy, 16, 16, poly([(1, 10), (15, 10), (13, 7.6), (3, 7.6)]), (200, 80, 60))
    fist = union(ellipse(9, 5, 3.6, 2.8), rect(7.6, 6.6, 10.6, 9))
    outline(img, ox, oy, 16, 16, fist, INK)
    paint(img, ox, oy, 16, 16, fist, SKIN)
    for x in (7.4, 8.8, 10.2):
        paint(img, ox, oy, 16, 16, seg(x, 3.6, x, 5.2, 0.4), INK, 0.6)
    paint(img, ox, oy, 16, 16, union(seg(3, 2, 4.6, 3.6, 0.6), seg(14, 2, 12.6, 3.4, 0.6)), RED)


def i_desa(img, ox, oy):  # a village hut, a shadow looming
    hut = union(rect(3, 8, 11, 14), poly([(2, 8.4), (7, 3.6), (12, 8.4)]))
    outline(img, ox, oy, 16, 16, hut, INK)
    paint(img, ox, oy, 16, 16, rect(3, 8, 11, 14), (200, 170, 120))
    paint(img, ox, oy, 16, 16, poly([(2, 8.4), (7, 3.6), (12, 8.4)]), (160, 120, 60))
    paint(img, ox, oy, 16, 16, rect(6, 10.4, 8, 14), WOOD_D)
    paint(img, ox, oy, 16, 16, union(circle(13, 6, 1.6), rect(11.6, 7.4, 14.4, 14)), (40, 30, 50), 0.85)
    paint(img, ox, oy, 16, 16, circle(13.4, 5.8, 0.4), RED)


def i_peristiwa(img, ox, oy):  # a torn sect banner on a broken pole
    paint(img, ox, oy, 16, 16, seg(4, 15, 4, 2, 1.0), WOOD_D)
    flag = poly([(4.5, 2.4), (13, 3.4), (11.4, 6), (13.4, 8.6), (9.6, 8), (8, 10), (4.5, 9)])
    outline(img, ox, oy, 16, 16, flag, INK)
    paint(img, ox, oy, 16, 16, flag, (60, 50, 70))
    paint(img, ox, oy, 16, 16, circle(8, 5.6, 1.6), RED)


def i_seteru(img, ox, oy):  # crossed swords
    for a, b in (((2.6, 13.4), (13, 3)), ((13.4, 13.4), (3, 3))):
        blade = seg(a[0], a[1], b[0], b[1], 1.5)
        outline(img, ox, oy, 16, 16, blade, INK, 0.7)
        paint(img, ox, oy, 16, 16, blade, shaded(STEEL, STEEL_D, WHITE, 8, 8, 6))
    paint(img, ox, oy, 16, 16, union(seg(3, 11, 5.6, 13.6, 1.1), seg(13, 11, 10.4, 13.6, 1.1)), GOLD)
    paint(img, ox, oy, 16, 16, union(circle(2.4, 14, 1.0), circle(13.6, 14, 1.0)), RED)


def i_damai(img, ox, oy):  # a lotus on water
    paint(img, ox, oy, 16, 16, ellipse(8, 13, 6.5, 1.5), (90, 150, 190), 0.8)
    for a in (-1.1, -0.55, 0.0, 0.55, 1.1):
        petal = ellipse(8 + math.sin(a) * 2.6, 9 - math.cos(a) * 2.2, 1.6, 3.4)
        paint(img, ox, oy, 16, 16, petal, shaded((250, 190, 210), (210, 110, 150), (255, 240, 245), 8, 7, 4))
    paint(img, ox, oy, 16, 16, circle(8, 10, 1.4), GOLD)


def i_dendam(img, ox, oy):  # a clenched fist (four knuckles, thumb across) bound with a red knot
    fist = union(rect(4.2, 5.6, 11.8, 11.6), circle(5.2, 5.8, 1.3), circle(7.6, 5.2, 1.4), circle(10.0, 5.2, 1.4),
                 circle(11.2, 6.2, 1.1), rect(5.0, 11.0, 11.0, 15.0))
    outline(img, ox, oy, 16, 16, fist, INK)
    paint(img, ox, oy, 16, 16, fist, shaded(mix(SKIN, (200, 140, 120), 0.25), (170, 110, 90), (250, 220, 200), 8, 8, 5))
    for x in (6.4, 8.8, 10.8):
        paint(img, ox, oy, 16, 16, seg(x, 4.6, x, 7.6, 0.45), INK, 0.55)
    paint(img, ox, oy, 16, 16, seg(4.6, 9.2, 9.6, 8.4, 1.3), mix(SKIN, (200, 140, 120), 0.45))
    paint(img, ox, oy, 16, 16, rect(4.8, 11.4, 11.2, 12.6), RED)
    paint(img, ox, oy, 16, 16, union(seg(11.2, 12, 14.2, 14.2, 0.8), seg(11.2, 12, 14.0, 10.2, 0.8)), RED_D)


def i_saingan(img, ox, oy):  # two blades meeting in a spark
    paint(img, ox, oy, 16, 16, seg(1.5, 12, 8.5, 6, 1.4), shaded(STEEL, STEEL_D, WHITE, 5, 9, 5))
    paint(img, ox, oy, 16, 16, seg(14.5, 12, 7.5, 6, 1.4), shaded(GOLD, GOLD_D, (255, 240, 180), 11, 9, 5))
    paint(img, ox, oy, 16, 16, poly([(8, 2.4), (8.8, 5), (11.4, 5.8), (8.8, 6.6), (8, 9.2), (7.2, 6.6), (4.6, 5.8), (7.2, 5)]),
          (255, 240, 170))


def i_terlarang(img, ox, oy):  # a heart tied by a red thread to a far point
    h = heart(6.5, 8.5, 4.2)
    outline(img, ox, oy, 16, 16, h, INK)
    paint(img, ox, oy, 16, 16, h, shaded(PINK, (180, 50, 80), (255, 210, 220), 6.5, 8.5, 4))
    paint(img, ox, oy, 16, 16, union(seg(9.6, 9, 12, 6, 0.6), seg(12, 6, 13.6, 9, 0.6), seg(13.6, 9, 14.6, 4, 0.6)), RED)
    paint(img, ox, oy, 16, 16, circle(14.6, 3.6, 1.0), RED)


def i_pisah(img, ox, oy):  # a broken heart, halves apart
    left = minus(heart(8, 8.5, 4.6), lambda x, y: x > 8 + 0.6 * math.sin(y * 1.8))
    right = minus(heart(8, 8.5, 4.6), lambda x, y: x <= 8 + 0.6 * math.sin(y * 1.8))
    paint(img, ox - 1, oy, 16, 16, left, shaded((200, 90, 110), (130, 40, 60), (240, 170, 180), 6, 8, 4))
    paint(img, ox + 1, oy + 1, 16, 16, right, shaded((200, 90, 110), (130, 40, 60), (240, 170, 180), 10, 9, 4))


def i_kawin_lari(img, ox, oy):  # two figures under a moon, hand in hand
    paint(img, ox, oy, 16, 16, minus(circle(12, 4, 2.6), circle(13.2, 3.4, 2.2)), (250, 230, 150))
    for x, c in ((5.6, (80, 110, 170)), (9.4, (190, 70, 100))):
        body = union(circle(x, 7, 1.5), poly([(x - 1.8, 14.6), (x + 1.8, 14.6), (x + 1.0, 8.6), (x - 1.0, 8.6)]))
        paint(img, ox, oy, 16, 16, body, c)
    paint(img, ox, oy, 16, 16, seg(6.4, 11, 8.6, 11, 0.7), SKIN)
    paint(img, ox, oy, 16, 16, seg(6.8, 11.4, 8.2, 11.4, 0.4), RED)


def i_aksi_damaikan(img, ox, oy):  # a folded letter with a jade seal
    letter = poly([(2, 4), (14, 4), (14, 12), (2, 12)])
    outline(img, ox, oy, 16, 16, letter, INK)
    paint(img, ox, oy, 16, 16, letter, PAPER)
    paint(img, ox, oy, 16, 16, union(seg(2, 4, 8, 9, 0.6), seg(14, 4, 8, 9, 0.6)), (170, 150, 120))
    paint(img, ox, oy, 16, 16, circle(8, 9.4, 1.8), JADE)
    paint(img, ox, oy, 16, 16, circle(8, 9.4, 0.8), JADE_D)


def i_aksi_utus(img, ox, oy):  # an elder with a staff walking
    paint(img, ox, oy, 16, 16, seg(11.6, 3, 11, 15, 0.8), WOOD)
    body = union(circle(7, 4.4, 1.9), poly([(4, 15), (10, 15), (9, 7), (5, 7)]))
    outline(img, ox, oy, 16, 16, body, INK)
    paint(img, ox, oy, 16, 16, poly([(4, 15), (10, 15), (9, 7), (5, 7)]), (90, 80, 140))
    paint(img, ox, oy, 16, 16, circle(7, 4.4, 1.9), SKIN)
    paint(img, ox, oy, 16, 16, poly([(5.6, 5.4), (8.4, 5.4), (7, 8.6)]), WHITE)
    paint(img, ox, oy, 16, 16, seg(9, 9, 11.4, 9.6, 0.8), (90, 80, 140))


def i_aksi_hukum(img, ox, oy):  # the discipline rod
    rod = seg(3, 13, 13, 3, 1.8)
    outline(img, ox, oy, 16, 16, rod, INK)
    paint(img, ox, oy, 16, 16, rod, (130, 90, 50))
    for t in (0.25, 0.5, 0.75):
        x, y = 3 + 10 * t, 13 - 10 * t
        paint(img, ox, oy, 16, 16, seg(x - 1, y - 1, x + 1, y + 1, 0.6), GOLD)
    paint(img, ox, oy, 16, 16, circle(3, 13, 1.4), RED)


def i_aksi_tuntut(img, ox, oy):  # scales with coins
    paint(img, ox, oy, 16, 16, union(seg(8, 3, 8, 13.4, 0.9), seg(3, 5, 13, 5, 0.8), rect(5, 13.2, 11, 14.4)), GOLD_D)
    for x in (3.6, 12.4):
        paint(img, ox, oy, 16, 16, union(seg(x, 5, x - 1.6, 9, 0.4), seg(x, 5, x + 1.6, 9, 0.4)), GOLD_D, 0.8)
        paint(img, ox, oy, 16, 16, ellipse(x, 9.4, 2.4, 0.9), GOLD)
    paint(img, ox, oy, 16, 16, union(circle(3.6, 8.2, 0.9), circle(4.4, 7.6, 0.9)), (255, 220, 100))


def i_aksi_dukung(img, ox, oy):  # a raised fist on a sleeved arm, a golden aura
    paint(img, ox, oy, 16, 16, circle(8, 5.4, 5.6), (255, 220, 120), 0.3)
    paint(img, ox, oy, 16, 16, poly([(5.6, 15.6), (10.4, 15.6), (9.8, 9.6), (6.2, 9.6)]), (180, 60, 40))
    paint(img, ox, oy, 16, 16, rect(6.0, 9.2, 10.0, 10.2), GOLD)
    fist = union(rect(5.2, 3.8, 10.8, 9.4), circle(6.2, 4.0, 1.1), circle(8.0, 3.6, 1.1), circle(9.8, 3.8, 1.1))
    outline(img, ox, oy, 16, 16, fist, INK)
    paint(img, ox, oy, 16, 16, fist, SKIN)
    for x in (7.1, 8.9):
        paint(img, ox, oy, 16, 16, seg(x, 3.2, x, 5.6, 0.4), INK, 0.55)
    paint(img, ox, oy, 16, 16, seg(5.4, 7.2, 9.0, 6.6, 1.1), mix(SKIN, (200, 140, 120), 0.35))


def i_aksi_tantang(img, ox, oy):  # crossed swords before a gate
    paint(img, ox, oy, 16, 16, union(rect(2, 4, 14, 5.4), rect(3, 5.4, 4.6, 15), rect(11.4, 5.4, 13, 15)), (150, 50, 40))
    paint(img, ox, oy, 16, 16, rect(1.4, 3, 14.6, 4), INK)
    paint(img, ox, oy, 16, 16, seg(4.6, 14, 11.6, 6.6, 1.1), STEEL)
    paint(img, ox, oy, 16, 16, seg(11.4, 14, 4.4, 6.6, 1.1), STEEL)


def i_aksi_larang(img, ox, oy):  # a lock on a chain
    paint(img, ox, oy, 16, 16, ring(8, 6, 2.2, 3.4), STEEL_D)
    lock = rect(4, 7, 12, 14)
    outline(img, ox, oy, 16, 16, lock, INK)
    paint(img, ox, oy, 16, 16, lock, shaded(GOLD, GOLD_D, (255, 240, 170), 8, 10, 4))
    paint(img, ox, oy, 16, 16, union(circle(8, 10, 1.1), rect(7.6, 10, 8.4, 12.4)), INK)


def i_aksi_biarkan(img, ox, oy):  # an open hand, palm up
    hand = union(ellipse(8, 10.4, 4.8, 2.8), rect(3.4, 6, 4.8, 10), rect(5.4, 5, 6.8, 9.6), rect(7.4, 4.6, 8.8, 9.6),
                 rect(9.4, 5.2, 10.8, 9.6), rect(11.4, 7, 12.8, 10.4))
    outline(img, ox, oy, 16, 16, hand, INK)
    paint(img, ox, oy, 16, 16, hand, SKIN)


def i_restui(img, ox, oy):  # two wine cups raised together
    for x, c in ((5.2, JADE), (10.8, RED)):
        cup = poly([(x - 2.6, 6), (x + 2.6, 6), (x + 1.2, 10), (x - 1.2, 10)])
        outline(img, ox, oy, 16, 16, cup, INK)
        paint(img, ox, oy, 16, 16, cup, shaded(GOLD, GOLD_D, (255, 240, 180), x, 8, 3))
        paint(img, ox, oy, 16, 16, union(rect(x - 0.4, 10, x + 0.4, 12.6), rect(x - 1.6, 12.6, x + 1.6, 13.4)), c)
    paint(img, ox, oy, 16, 16, poly([(8, 2), (8.6, 3.6), (10, 4), (8.6, 4.4), (8, 6), (7.4, 4.4), (6, 4), (7.4, 3.6)]), (255, 240, 170))


def i_pisahkan(img, ox, oy):  # a single cup, a fallen petal
    cup = poly([(5.4, 5), (10.6, 5), (9.2, 9.4), (6.8, 9.4)])
    outline(img, ox, oy, 16, 16, cup, INK)
    paint(img, ox, oy, 16, 16, cup, shaded((200, 200, 210), (120, 120, 140), WHITE, 8, 7, 3))
    paint(img, ox, oy, 16, 16, union(rect(7.6, 9.4, 8.4, 12.4), rect(6.2, 12.4, 9.8, 13.2)), (120, 120, 140))
    paint(img, ox, oy, 16, 16, ellipse(12.6, 13.2, 1.6, 0.8), PINK)


def i_patroli(img, ox, oy):  # a patrol banner and a road
    paint(img, ox, oy, 16, 16, poly([(0, 15), (16, 15), (11, 10), (5, 10)]), (150, 130, 100), 0.7)
    paint(img, ox, oy, 16, 16, seg(5, 14, 5, 1.6, 0.9), WOOD_D)
    flag = poly([(5.4, 2), (13, 2), (13, 8), (10.6, 6.6), (8.2, 8), (5.4, 8)])
    outline(img, ox, oy, 16, 16, flag, INK)
    paint(img, ox, oy, 16, 16, flag, (150, 30, 30))
    paint(img, ox, oy, 16, 16, circle(9, 4.6, 1.4), (20, 20, 20))


def i_sergap(img, ox, oy):  # eyes in the tall grass
    for x in range(1, 16, 2):
        paint(img, ox, oy, 16, 16, seg(x, 15, x + (0.8 if x % 4 == 1 else -0.8), 7 + (x * 7) % 4, 0.9), GREEN)
    for x in (5.4, 10.6):
        paint(img, ox, oy, 16, 16, ellipse(x, 9, 1.6, 0.8), (255, 230, 120))
        paint(img, ox, oy, 16, 16, ellipse(x, 9, 0.4, 0.7), INK)


def i_luka(img, ox, oy):  # a bandage roll with a red stain
    roll = ellipse(8, 8, 5.4, 4.2)
    outline(img, ox, oy, 16, 16, roll, INK)
    paint(img, ox, oy, 16, 16, roll, (238, 232, 216))
    paint(img, ox, oy, 16, 16, ring(8, 8, 1.4, 2.4), (200, 190, 170))
    paint(img, ox, oy, 16, 16, ellipse(10, 6.6, 1.6, 1.2), RED, 0.85)
    paint(img, ox, oy, 16, 16, poly([(12.6, 9), (15, 13.4), (13.4, 14), (11.6, 10)]), (238, 232, 216))


def i_api(img, ox, oy):  # the heat flame
    outer = poly([(8, 1), (11.6, 6), (12.6, 10), (10.6, 14), (5.4, 14), (3.4, 10), (5, 6.6), (6.6, 8.2)])
    paint(img, ox, oy, 16, 16, outer, lambda x, y: mix((255, 220, 120), (220, 50, 20), y / 14.0))
    paint(img, ox, oy, 16, 16, poly([(8, 6), (10, 9.6), (9.6, 13), (6.4, 13), (6.2, 10)]), (255, 245, 200))


def i_kunci(img, ox, oy):  # a small padlock (locked rung)
    paint(img, ox, oy, 16, 16, ring(8, 6.4, 2.0, 3.0), (150, 150, 160))
    paint(img, ox, oy, 16, 16, rect(4.4, 7.4, 11.6, 13.6), (120, 110, 100))
    paint(img, ox, oy, 16, 16, circle(8, 10.2, 1.0), INK)


def i_sahabat(img, ox, oy):  # a handshake of two sleeves (jade and crimson)
    paint(img, ox, oy, 16, 16, poly([(0, 9), (5.4, 6.4), (7, 9.4), (1, 12.6)]), JADE)
    paint(img, ox, oy, 16, 16, poly([(16, 9), (10.6, 6.4), (9, 9.4), (15, 12.6)]), RED)
    hands = union(ellipse(8, 8.4, 3.2, 1.9), ellipse(7.2, 9.4, 2.4, 1.4))
    outline(img, ox, oy, 16, 16, hands, INK)
    paint(img, ox, oy, 16, 16, hands, SKIN)


ICONS = [i_hinaan, i_harta, i_gilda, i_turnamen, i_asmara, i_gulungan, i_perundung, i_desa,
         i_peristiwa, i_seteru, i_damai, i_dendam, i_saingan, i_terlarang, i_pisah, i_kawin_lari,
         i_aksi_damaikan, i_aksi_utus, i_aksi_hukum, i_aksi_tuntut, i_aksi_dukung, i_aksi_tantang, i_aksi_larang, i_aksi_biarkan,
         i_restui, i_pisahkan, i_patroli, i_sergap, i_luka, i_api, i_kunci, i_sahabat]


def make_ikon():
    img = Image(128, 64)
    for cell, draw in enumerate(ICONS):
        icon(img, cell, draw)
    return img


# ================================================================================================ the title-card sheet

def make_kartu():
    img = Image(256, 128)
    rnd = random.Random(390601)
    # two torn ink-band halves (left fades to the right edge, right mirrors it); ragged outer edge, brush grain
    for half in (0, 1):
        ox = half * 128
        edge = [rnd.uniform(0, 7) for _ in range(48)]
        for y in range(48):
            vy = abs(y - 23.5) / 24.0
            for x in range(128):
                lx = x if half == 0 else 127 - x
                tear = edge[y] + 3.0 * math.sin(y * 0.7 + half)
                if lx < tear:
                    continue
                inner = clamp((lx - tear) / 18.0)
                fade = 1.0 - clamp((lx - 100) / 28.0) * 0.55
                edge_v = 1.0 - clamp((vy - 0.62) / 0.38)
                grain = 0.88 + 0.12 * math.sin(x * 0.9 + y * 0.25 + rnd.random() * 0.4)
                a = inner * fade * edge_v * grain
                if a <= 0.02:
                    continue
                base = mix((18, 10, 10), (40, 18, 14), y / 48.0)
                streak = (52, 150, 110) if half == 0 else (170, 34, 26)
                s = clamp(1.0 - abs(y - 38) / 3.0) * clamp((lx - tear - 6) / 30.0)
                c = mix(base, streak, s * 0.85)
                img.set(ox + x, y, (int(c[0]), int(c[1]), int(c[2]), int(255 * clamp(a * 0.95))))
        # ink splatter dots near the torn edge
        for _ in range(18):
            lx = rnd.uniform(0, 14)
            y = rnd.uniform(4, 44)
            x = lx if half == 0 else 127 - lx
            paint(img, ox, 0, 128, 48, circle(x, y, rnd.uniform(0.5, 1.4)), (22, 12, 12), 0.8)
    # the seal: a red rounded square, paper rim, crossed swords in paper colour
    sx, sy = 0, 48
    body = lambda x, y: max(abs(x - 32) - 22, 0) ** 2 + max(abs(y - 32) - 22, 0) ** 2 <= 36 and abs(x - 32) <= 28 and abs(y - 32) <= 28
    paint(img, sx, sy, 64, 64, body, lambda x, y: mix((214, 52, 36), (140, 22, 16), (x + y) / 128.0))
    rim = lambda x, y: body(x, y) and not (abs(x - 32) <= 24 and abs(y - 32) <= 24)
    paint(img, sx, sy, 64, 64, rim, (110, 16, 12), 0.6)
    paint(img, sx, sy, 64, 64, union(rect(9, 9, 55, 10.4), rect(9, 53.6, 55, 55), rect(9, 9, 10.4, 55), rect(53.6, 9, 55, 55)), PAPER, 0.9)
    for a, b in (((15, 49), (49, 15)), ((49, 49), (15, 15))):
        # the blade tapering to a point, a cross guard, a wrapped grip and a pommel
        dx, dy = (b[0] - a[0]) / 34.0, (b[1] - a[1]) / 34.0
        gx, gy = a[0] + dx * 9.0, a[1] + dy * 9.0
        paint(img, sx, sy, 64, 64, poly([(gx - dy * 2.4, gy + dx * 2.4), (gx + dy * 2.4, gy - dx * 2.4), (b[0], b[1])]), PAPER)
        paint(img, sx, sy, 64, 64, seg(gx - dy * 5.0, gy + dx * 5.0, gx + dy * 5.0, gy - dx * 5.0, 2.2), (250, 226, 170))
        paint(img, sx, sy, 64, 64, seg(a[0] + dx * 1.5, a[1] + dy * 1.5, gx, gy, 3.0), (250, 226, 170))
        paint(img, sx, sy, 64, 64, circle(a[0], a[1], 2.4), PAPER)
    paint(img, sx, sy, 64, 64, circle(32, 32, 5.0), (140, 22, 16))
    paint(img, sx, sy, 64, 64, circle(32, 32, 2.6), PAPER)
    # worn seal texture: a few paler specks
    for _ in range(60):
        x, y = rnd.uniform(8, 56), rnd.uniform(8, 56)
        if body(x, y):
            img.blend(sx + int(x), sy + int(y), (255, 210, 190, 50))
    # the glow (white, alpha falloff; tinted at run time)
    for y in range(64):
        for x in range(64):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32.0
            a = clamp(1.0 - d) ** 2.2
            if a > 0.004:
                img.set(64 + x, 48 + y, (255, 255, 255, int(255 * a)))
    # heat wisps: soft rising tongues
    for k in range(7):
        cx = 10 + k * 18 + rnd.uniform(-4, 4)
        for y in range(32):
            t = y / 31.0
            w = 5.0 * (0.3 + t) * (1.0 + 0.2 * math.sin(k + y * 0.5))
            off = 3.0 * math.sin(y * 0.35 + k * 1.3)
            for x in range(128):
                d = abs(x + 0.5 - (cx + off)) / max(0.5, w)
                a = clamp(1.0 - d) * clamp(t * 1.6) * clamp((1.0 - t) * 3.0 + 0.2)
                if a > 0.01:
                    img.blend(128 + x, 48 + 31 - y, (255, 255, 255, int(160 * a)))
    return img


# ================================================================================================ materials (16x16)

def tex(fn, seed):
    rnd = random.Random(seed)
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            c = fn(x, y, rnd)
            img.set(x, y, (int(c[0]), int(c[1]), int(c[2]), 255 if len(c) < 4 else int(c[3])))
    return img


def m_linen(x, y, rnd):
    weave = 8 if (x + y) % 2 == 0 else 0
    c = mix((236, 230, 214), (214, 206, 186), rnd.random() * 0.4)
    c = tuple(v - weave for v in c)
    if math.hypot(x - 11, y - 5) < 2.6:
        c = mix(c, (150, 30, 26), 0.7 - math.hypot(x - 11, y - 5) / 5.0)
    return c


def m_silk(base, light, motif):
    def f(x, y, rnd):
        c = mix(base, light, 0.15 + 0.15 * math.sin((x + y) * 0.8) + rnd.random() * 0.08)
        if motif(x, y):
            c = mix(c, light, 0.75)
        if y in (0, 15):
            c = mix(c, GOLD, 0.8)
        return c
    return f


def motif_awan(x, y):  # righteous: a cloud scroll
    return 3.0 <= math.hypot(x - 8, y - 8) <= 4.2 and not (x > 8 and y > 9) or 1.6 <= math.hypot(x - 10.5, y - 10) <= 2.4


def motif_cincin(x, y):  # neutral: a ring with a bar
    return 3.2 <= math.hypot(x - 8, y - 8) <= 4.4 or (abs(y - 8) < 0.8 and 4 <= x <= 12)


def motif_tengkorak(x, y):  # demonic: a skull-ish mark
    return (math.hypot(x - 8, y - 7) <= 3.6 and not (math.hypot(x - 6.6, y - 7) < 1.0 or math.hypot(x - 9.4, y - 7) < 1.0)) or (
            6.4 <= x <= 9.6 and 9.6 <= y <= 11.6 and x % 2 == 0)


def motif_perang(x, y):  # the planted banner: a black diagonal brush stroke
    return abs((x - 8) - (y - 8) * 0.8) < 1.2 and 3 <= y <= 13 or abs((x - 8) + (y - 8) * 0.8) < 1.0 and 4 <= y <= 12


def m_wood(x, y, rnd):
    g = math.sin(x * 1.9 + math.sin(y * 0.6) * 1.4) * 0.5 + 0.5
    return mix(WOOD_D, WOOD, 0.35 + g * 0.5 + rnd.random() * 0.08)


def m_bronze(x, y, rnd):
    c = mix((168, 120, 54), (220, 178, 96), 0.4 + 0.4 * math.sin(x * 0.7 + y * 0.4) + rnd.random() * 0.15)
    if rnd.random() < 0.08:
        c = mix(c, (90, 140, 110), 0.5)
    return c


def m_paper(x, y, rnd):
    c = mix(PAPER, (222, 206, 170), rnd.random() * 0.35 + y / 40.0)
    if y in (4, 7, 10) and 3 <= x <= 12 and rnd.random() < 0.7:
        c = mix(c, INK, 0.75)
    return c


def m_seal(x, y, rnd):
    d = math.hypot(x - 7.5, y - 7.5) / 8.0
    return mix((214, 50, 40), (120, 18, 14), d + rnd.random() * 0.1)


def m_cord(x, y, rnd):
    twist = 0.5 + 0.5 * math.sin((x + y * 1.5) * 1.6)
    return mix((150, 20, 26), (236, 60, 60), twist * 0.7 + rnd.random() * 0.1)


def m_leather(x, y, rnd):
    c = mix((110, 70, 40), (150, 100, 58), rnd.random() * 0.5)
    if (x * 7 + y * 3) % 11 == 0:
        c = mix(c, (60, 36, 20), 0.6)
    return c


def m_gold(x, y, rnd):
    return mix((200, 150, 50), (255, 224, 120), 0.4 + 0.5 * math.sin(x * 0.9 - y * 0.5) * 0.5 + rnd.random() * 0.2)


def m_celadon(x, y, rnd):
    c = mix((150, 204, 176), (196, 232, 210), 0.25 + rnd.random() * 0.1 - y / 64.0)
    if (x + 2 * y) % 9 == 0:
        c = mix(c, (100, 140, 120), 0.4)
    return c


def m_rope(x, y, rnd):
    twist = 0.5 + 0.5 * math.sin((x * 1.3 + y) * 1.2)
    return mix((150, 120, 70), (210, 180, 120), twist * 0.7 + rnd.random() * 0.1)


MATERIALS = {
    "linen": (m_linen, 1),
    "kain_lurus": (m_silk((230, 236, 244), (90, 150, 220), motif_awan), 2),
    "kain_netral": (m_silk((196, 150, 70), (250, 220, 140), motif_cincin), 3),
    "kain_iblis": (m_silk((30, 18, 24), (196, 32, 36), motif_tengkorak), 4),
    "kain_perang": (m_silk((180, 40, 32), (30, 16, 16), motif_perang), 5),
    "kayu": (m_wood, 6),
    "perunggu": (m_bronze, 7),
    "kertas": (m_paper, 8),
    "segel": (m_seal, 9),
    "sutra_merah": (m_cord, 10),
    "kulit": (m_leather, 11),
    "emas": (m_gold, 12),
    "seladon": (m_celadon, 13),
    "tali": (m_rope, 14),
}


# ================================================================================================ models

def face_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("north", "south"):
        u0, u1, v0, v1 = x0, x1, 16 - y1, 16 - y0
    elif face in ("east", "west"):
        u0, u1, v0, v1 = z0, z1, 16 - y1, 16 - y0
    else:
        u0, u1, v0, v1 = x0, x1, z0, z1
    vals = [round(((v % 16) + 16) % 16, 3) for v in (u0, v0, u1, v1)]
    if vals[2] <= vals[0]:
        vals[2] = min(16.0, vals[0] + max(0.5, abs(u1 - u0)))
    if vals[3] <= vals[1]:
        vals[3] = min(16.0, vals[1] + max(0.5, abs(v1 - v0)))
    vals = [max(0.0, min(16.0, v)) for v in vals]
    if vals[2] - vals[0] < 0.5:
        vals[0] = max(0.0, vals[2] - 0.5)
    if vals[3] - vals[1] < 0.5:
        vals[1] = max(0.0, vals[3] - 0.5)
    return vals


def el(f, t, tex_key, rot=None):
    e = {"from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": {}}
    for face in ("north", "south", "east", "west", "up", "down"):
        e["faces"][face] = {"uv": face_uv(face, f, t), "texture": "#" + tex_key}
    if rot is not None:
        e["rotation"] = rot
    return e


def rot(axis, angle, origin):
    return {"origin": list(origin), "axis": axis, "angle": angle}


def model(textures, elements):
    tx = {k: "kultivator:item/fx/" + PREFIX + v for k, v in textures.items()}
    tx["particle"] = tx[next(iter(textures))]
    return {"textures": tx, "elements": elements}


def m_perban():
    # a linen band around the head (inner 8.4 px), a diagonal strip over the brow, the knot and two tails at the back
    e = []
    y0, y1 = 9.6, 11.2
    e.append(el((3.6, y0, 3.5), (12.4, y1, 3.9), "l"))
    e.append(el((3.6, y0, 12.1), (12.4, y1, 12.5), "l"))
    e.append(el((3.5, y0, 3.6), (3.9, y1, 12.4), "l"))
    e.append(el((12.1, y0, 3.6), (12.5, y1, 12.4), "l"))
    e.append(el((5.0, 10.6, 3.3), (9.0, 11.6, 3.7), "l", rot("z", 22.5, (7, 11, 3.5))))
    e.append(el((7.0, 9.2, 12.4), (9.0, 11.6, 13.4), "l"))
    e.append(el((7.2, 6.4, 12.9), (8.0, 9.4, 13.3), "l", rot("z", 22.5, (7.6, 9.2, 13.1))))
    e.append(el((8.2, 6.8, 12.9), (9.0, 9.4, 13.3), "l", rot("z", -22.5, (8.6, 9.2, 13.1))))
    e.append(el((10.4, 10.0, 3.2), (11.4, 10.8, 3.6), "s"))
    return model({"l": "linen", "s": "segel"}, e)


def m_panji(silk):
    # pole, a bronze spear finial, a cross bar, the banner with a swallowtail and two tassels
    e = []
    e.append(el((7.5, -4.0, 7.5), (8.5, 28.0, 8.5), "k"))
    e.append(el((7.3, -4.4, 7.3), (8.7, -3.4, 8.7), "p"))
    e.append(el((7.6, 28.0, 7.6), (8.4, 31.0, 8.4), "p"))
    e.append(el((7.75, 31.0, 7.75), (8.25, 32.0, 8.25), "p", rot("y", 45, (8, 31.5, 8))))
    e.append(el((7.0, 27.4, 7.0), (9.0, 28.0, 9.0), "p"))
    e.append(el((8.5, 26.2, 7.6), (16.0, 26.9, 8.4), "k"))
    e.append(el((8.6, 14.0, 7.9), (15.8, 26.2, 8.1), "c"))
    e.append(el((8.6, 11.0, 7.9), (11.8, 14.0, 8.1), "c"))
    e.append(el((12.6, 11.0, 7.9), (15.8, 14.0, 8.1), "c"))
    e.append(el((8.4, 26.0, 7.8), (16.0, 26.3, 8.2), "p"))
    e.append(el((15.6, 22.6, 7.85), (16.2, 26.2, 8.15), "s"))
    e.append(el((15.5, 21.4, 7.75), (16.3, 22.6, 8.25), "s"))
    e.append(el((7.4, 10.0, 7.4), (8.6, 11.0, 8.6), "s"))
    return model({"k": "kayu", "p": "perunggu", "c": silk, "s": "sutra_merah"}, e)


def m_gulungan():
    # a rolled scroll along x: two crossed boxes (octagon), wooden knobs, a red seal band
    e = []
    e.append(el((5.0, 7.2, 7.2), (11.0, 8.8, 8.8), "k"))
    e.append(el((5.0, 7.3, 7.3), (11.0, 8.7, 8.7), "k", rot("x", 45, (8, 8, 8))))
    e.append(el((4.2, 7.4, 7.4), (5.0, 8.6, 8.6), "w"))
    e.append(el((11.0, 7.4, 7.4), (11.8, 8.6, 8.6), "w"))
    e.append(el((7.5, 7.0, 7.0), (8.5, 9.0, 9.0), "s"))
    e.append(el((7.8, 6.6, 7.8), (8.2, 7.0, 8.2), "s"))
    return model({"k": "kertas", "w": "kayu", "s": "segel"}, e)


def m_surat():
    # a folded challenge letter with a red wax seal and a tied cord
    e = []
    e.append(el((5.0, 4.5, 7.8), (11.0, 11.5, 8.2), "k"))
    e.append(el((5.0, 10.5, 7.7), (11.0, 11.5, 7.8), "k"))
    e.append(el((7.0, 6.5, 7.6), (9.0, 8.5, 7.8), "s"))
    e.append(el((4.9, 7.3, 7.65), (11.1, 7.7, 8.35), "c"))
    return model({"k": "kertas", "s": "segel", "c": "sutra_merah"}, e)


def m_kantong():
    # a leather pouch, its gathered neck, a red drawstring with a tassel and gold coins showing
    e = []
    e.append(el((5.5, 6.0, 5.5), (10.5, 10.5, 10.5), "l"))
    e.append(el((6.0, 5.5, 6.0), (10.0, 6.0, 10.0), "l"))
    e.append(el((6.5, 10.5, 6.5), (9.5, 12.0, 9.5), "l"))
    e.append(el((6.0, 11.4, 6.0), (10.0, 12.6, 10.0), "l"))
    e.append(el((6.3, 10.6, 6.3), (9.7, 11.2, 9.7), "c"))
    e.append(el((9.7, 8.6, 7.8), (10.3, 10.8, 8.2), "c"))
    e.append(el((9.6, 7.8, 7.6), (10.4, 8.6, 8.4), "c"))
    e.append(el((6.8, 12.2, 6.8), (8.2, 12.9, 8.2), "g"))
    e.append(el((7.6, 12.1, 7.4), (9.2, 12.7, 9.0), "g"))
    return model({"l": "kulit", "c": "sutra_merah", "g": "emas"}, e)


def m_cawan():
    # a celadon teacup with a broken wedge missing from its rim, and the shard
    e = []
    e.append(el((6.6, 8.0, 6.6), (9.4, 8.5, 9.4), "c"))
    e.append(el((5.9, 8.5, 5.9), (10.1, 9.4, 10.1), "c"))
    e.append(el((5.3, 9.4, 5.3), (10.7, 10.6, 10.7), "c"))
    e.append(el((5.0, 10.6, 5.0), (11.0, 11.5, 5.5), "c"))
    e.append(el((5.0, 10.6, 5.5), (5.5, 11.5, 10.5), "c"))
    e.append(el((10.5, 10.6, 5.5), (11.0, 11.5, 8.0), "c"))
    e.append(el((5.0, 10.6, 10.5), (7.4, 11.2, 11.0), "c"))
    e.append(el((11.6, 8.0, 10.0), (13.2, 8.4, 11.4), "c", rot("y", 22.5, (12.4, 8.2, 10.7))))
    return model({"c": "seladon"}, e)


def m_tali():
    # a red silk cord in a loose flat coil (eight short turns), a knot on the rim and a dangling end with a gold bead
    e = []
    for k in range(8):
        phi = math.radians(k * 45.0)
        cx, cz = 8.0 + 3.0 * math.cos(phi), 8.0 + 3.0 * math.sin(phi)
        # a y rotation by theta turns +x toward -z: the tangent at phi needs theta = -(phi + 90)
        theta = (-(k * 45.0 + 90.0)) % 180.0
        if theta > 90.0:
            theta -= 180.0
        y = 8.0 + (0.25 if k % 2 else 0.0)
        e.append(el((cx - 1.35, y, cz - 0.3), (cx + 1.35, y + 0.6, cz + 0.3), "c", rot("y", theta, (cx, y + 0.3, cz))))
    e.append(el((10.4, 7.8, 7.4), (11.6, 9.0, 8.6), "c"))
    e.append(el((11.0, 4.6, 7.8), (11.4, 7.8, 8.2), "c", rot("z", -22.5, (11.2, 7.8, 8.0))))
    e.append(el((9.4, 3.7, 7.5), (10.4, 4.7, 8.5), "g"))
    return model({"c": "sutra_merah", "g": "emas"}, e)


def m_kruk():
    # a wooden crutch: the shaft, a padded arm rest, a hand grip and a rope-bound joint
    e = []
    e.append(el((7.5, -6.0, 7.5), (8.5, 17.0, 8.5), "k"))
    e.append(el((5.5, 17.0, 7.3), (10.5, 18.2, 8.7), "k"))
    e.append(el((5.4, 18.2, 7.2), (10.6, 18.9, 8.8), "l"))
    e.append(el((6.0, 8.0, 7.4), (10.0, 9.0, 8.6), "k"))
    e.append(el((7.4, 9.4, 7.4), (8.6, 10.8, 8.6), "t"))
    e.append(el((7.3, -6.6, 7.3), (8.7, -5.8, 8.7), "t"))
    return model({"k": "kayu", "l": "linen", "t": "tali"}, e)


MODELS = {
    "perban": m_perban(),
    "panji_lurus": m_panji("kain_lurus"),
    "panji_netral": m_panji("kain_netral"),
    "panji_iblis": m_panji("kain_iblis"),
    "gulungan_curian": m_gulungan(),
    "surat_tantangan": m_surat(),
    "kantong_ganti_rugi": m_kantong(),
    "cawan_pecah": m_cawan(),
    "tali_merah": m_tali(),
    "kruk": m_kruk(),
    "tiang_panji": m_panji("kain_perang"),
}


# ================================================================================================ preview

def preview(path, atlas, kartu, mats):
    img = Image(512 + 16, 64 * 4 + 128 * 2 + 40, (40, 30, 28, 255))
    for y in range(64 * 4):
        for x in range(128 * 4):
            img.set(8 + x, 8 + y, atlas.get(x // 4, y // 4))
    for y in range(128 * 2):
        for x in range(256 * 2):
            p = kartu.get(x // 2, y // 2)
            if p[3] > 0:
                img.blend(8 + x, 16 + 256 + y, p)
    # the materials as a strip of 16x16 at x2
    x = 8
    for name, m in mats.items():
        for yy in range(32):
            for xx in range(32):
                img.set(x + xx, 16 + 256 + 256 + 4 + yy - 32 + 30, m.get(xx // 2, yy // 2))
        x += 36
        if x > 500:
            break
    img.save(path)


def main():
    for d in (SETERU_DIR, FX_TEX_DIR, MODEL_DIR, ITEM_DIR):
        os.makedirs(d, exist_ok=True)
    atlas = make_ikon()
    atlas.save(os.path.join(SETERU_DIR, "ikon.png"))
    kartu = make_kartu()
    kartu.save(os.path.join(SETERU_DIR, "kartu.png"))
    mats = {}
    for name, (fn, seed) in MATERIALS.items():
        img = tex(fn, 390600 + seed)
        img.save(os.path.join(FX_TEX_DIR, PREFIX + name + ".png"))
        mats[name] = img
    count = 0
    for name in PROP_NAMA:
        m = MODELS[name]
        for e in m["elements"]:
            for v in e["from"] + e["to"]:
                assert -16.0 <= v <= 32.0, (name, e)
            for key in m["textures"]:
                pass
        for e in m["elements"]:
            for face in e["faces"].values():
                assert face["texture"][1:] in m["textures"], (name, face)
        with open(os.path.join(MODEL_DIR, PREFIX + name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(m, f, indent=1)
            f.write("\n")
        with open(os.path.join(ITEM_DIR, PREFIX + name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kultivator:item/fx/" + PREFIX + name}}, f, indent=1)
            f.write("\n")
        count += len(m["elements"])
    if len(sys.argv) > 1:
        preview(sys.argv[1], atlas, kartu, mats)
    print("ikon.png 128x64 (%d icons), kartu.png 256x128, %d materials, %d props with %d elements"
          % (len(ICONS), len(MATERIALS), len(PROP_NAMA), count))


if __name__ == "__main__":
    main()
