"""Generates the GUI textures of the Gabung Sekte screens (DESIGN 41.10, module 41U-1; every icon of the 41.10 screen list,
so 41U-2 only uses them).

Our own art, drawn from primitives with the helpers of tools/gen_tempat_ui.py (Canvas, paste, colours) and written with
tools/png.py. Everything lands in textures/gui/gabung/ and is read by client/sekte/gabung/ui/IkonGabung.java:

ikon.png     256x64  16 px icon atlas, slot = row * 16 + column, order = IkonGabung constants:
                     row 0  the ten corak (0-9), the five class plaques Kecil..Abadi (10-14), cloud palace (15)
                     row 1  the five doors (16-20), alignments Lurus/Netral/Iblis (21-23), MEROSOT (24), rumour (25),
                            Shifu (26), Balai Hukum (27), wards (28), leaving (29), weekly allowance (30), seat (31)
                     row 2  test HUD: Mountain Pressure (32), illusion (33), meditation (34), dummy (35), slash (36),
                            bronze monk (37), beast paw (38), formation eye (39), Qi pillar (40), furnace (41),
                            carrying stone (42), blood pool (43), cup (44), guard (45), heart (46), oath incense (47)
                     row 3  rank seals Pelayan..Ketua + Tamu (48-57), Papan Jasa (58), ranking (59), unknown sect (60),
                            member badge (61), bearing needle (62), master's crown (63)
cap.png      96x32   rank-tree node: 0 jade cap in greys (tinted with the rank colour), 32 gold rim and gloss (untinted),
                     64 the soft halo of the player's own rank
kartu.png    64x64   the member card: nine-slice jade slab, corner 12, gold cloud corners, faint veins
hud.png      128x64  test HUD: 0,0 pressure bar frame 128x14; 0,16 bar fill 120x6 (light, tinted); 0,24 Lima Pilar dial
                     64x36; 64,24 timer ring 32x32; 96,24 stage seal 32x32
stempel.png  128x48  verdict stamps (41U-2 gabung_putusan): 0,0 red "accepted" frame 128x24, 0,24 grey "refused" 128x24

Previews (x4) go to tools/keluaran/gabung_ui/ for inspection with Read; they are not shipped.

Run with: python tools/gen_gabung_ui.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402
import gen_tempat_ui as ui  # noqa: E402
from gen_tempat_ui import Canvas, paste, hexc, mix, shade, fbm, hnoise, clamp  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "gui", "gabung")
PREVIEW = os.path.join(HERE, "keluaran", "gabung_ui")

OUTLINE = ui.OUTLINE
INK = ui.INK
GOLD, GOLD_L, GOLD_D = ui.GOLD, ui.GOLD_L, ui.GOLD_D
JADE, JADE_L, JADE_D, JADE_P = ui.JADE, ui.JADE_L, ui.JADE_D, ui.JADE_P
RED, RED_L, RED_D = ui.RED, ui.RED_L, ui.RED_D
IRON, IRON_L, IRON_D = ui.IRON, ui.IRON_L, ui.IRON_D
WOOD, WOOD_L, WOOD_D = ui.WOOD, ui.WOOD_L, ui.WOOD_D
PAPER, PAPER_D = ui.PAPER, ui.PAPER_D
WHITE = ui.WHITE
BLUE, BLUE_L, BLUE_D = ui.BLUE, ui.BLUE_L, ui.BLUE_D
PURPLE, PURPLE_L = ui.PURPLE, ui.PURPLE_L
GREEN, GREEN_L, GREEN_D = ui.GREEN, ui.GREEN_L, ui.GREEN_D
AMBER = ui.AMBER
GREY = ui.GREY
BRONZE = hexc("B07A3A")
BRONZE_L = hexc("E0B070")
BRONZE_D = hexc("6A4218")
BLOOD = hexc("8A1020")
BLOOD_L = hexc("D0303A")
POISON = hexc("7A3FA0")
POISON_L = hexc("B88AE0")
SHADOW = hexc("2E2A3A")
SHADOW_L = hexc("6A6488")
STONE = hexc("8C8A84")
STONE_L = hexc("C4C0B6")
STONE_D = hexc("55524C")
FIRE = hexc("FF8A3D")
FIRE_L = hexc("FFD27A")
SKY = ui.SKY
CLOUD = hexc("F2F6FA")


def new(w=16, h=16, ss=4):
    return Canvas(w, h, ss)


def put(img, c, ox, oy, outline=True):
    paste(img, c.resolve(), ox, oy, OUTLINE if outline else None)


def a(c, alpha):
    return (c[0], c[1], c[2], alpha)


def preview(img, name, k=4, bg=(40, 44, 52, 255)):
    out = Image(img.width * k, img.height * k, (0, 0, 0, 255))
    for y in range(out.height):
        for x in range(out.width):
            cb = ((x // 8) + (y // 8)) % 2
            out.set(x, y, shade(bg, 0.06) if cb else bg)
    for y in range(img.height):
        for x in range(img.width):
            p = img.get(x, y)
            if p[3] == 0:
                continue
            for yy in range(k):
                for xx in range(k):
                    out.blend(x * k + xx, y * k + yy, p)
    os.makedirs(PREVIEW, exist_ok=True)
    out.save(os.path.join(PREVIEW, name))


GLYPH = dict(ui.GLYPH)
GLYPH["0"] = ["01110", "10001", "10011", "10101", "11001", "10001", "01110"]
GLYPH["T"] = ["11111", "00100", "00100", "00100", "00100", "00100", "00100"]
GLYPH["?"] = ["01110", "10001", "00001", "00110", "00100", "00000", "00100"]


def glyph(c, ch, x, y, px, col):
    for r, row in enumerate(GLYPH[ch]):
        for k, bit in enumerate(row):
            if bit == "1":
                c.rect(x + k * px, y + r * px, x + (k + 1) * px, y + (r + 1) * px, col)


def star(c, cx, cy, r0, r1, col, n=5, rot=-math.pi / 2):
    pts = []
    for k in range(n * 2):
        r = r1 if k % 2 == 0 else r0
        ang = rot + k * math.pi / n
        pts.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
    c.poly(pts, col)


def shadow(c, cx=8, cy=14.4, rx=5.4, ry=1.2):
    c.ellipse(cx, cy, rx, ry, (0, 0, 0, 70))


# ===================================================================================================== row 0: corak

def k_pedang(c):
    """A straight jian, point up, with a red tassel: the sword sects."""
    c.poly([(8, 0.8), (9.3, 2.8), (9.0, 10.5), (7.0, 10.5), (6.7, 2.8)], IRON_L)
    c.poly([(8, 0.8), (9.3, 2.8), (9.0, 10.5), (8, 10.5)], IRON)
    c.line(8, 2.4, 8, 10, 0.4, WHITE)
    c.rect(4.6, 10.4, 11.4, 11.6, GOLD)
    c.rect(5.2, 10.8, 10.8, 11.2, GOLD_L)
    c.rect(7.2, 11.6, 8.8, 14.4, WOOD)
    c.circle(8, 14.9, 1.0, GOLD)
    c.path([(8, 15.4), (6.8, 15.8), (6.2, 15.9)], 0.8, RED_L)


def k_buddha(c):
    """A lotus of eight petals with a gold heart."""
    for k in range(5):
        ang = math.pi + k * math.pi / 4
        cx, cy = 8 + math.cos(ang) * 3.2, 10 + math.sin(ang) * 3.2
        c.ellipse(cx, cy, 2.0, 3.6 if k == 2 else 3.0, hexc("F2A8C0"))
    c.ellipse(8, 7.4, 2.0, 4.0, hexc("F8C8D8"))
    c.ellipse(4.6, 9.4, 2.2, 1.6, hexc("E68AA8"))
    c.ellipse(11.4, 9.4, 2.2, 1.6, hexc("E68AA8"))
    c.ellipse(8, 12.6, 6.4, 1.8, GREEN)
    c.ellipse(8, 12.2, 5.6, 1.2, GREEN_L)
    c.circle(8, 9.6, 1.3, GOLD_L)


def k_binatang(c):
    """A crane's head and neck over a paw print: beasts tamed and ridden."""
    c.path([(5, 15), (5.4, 11), (6.6, 7.6), (8.8, 5.4)], 2.0, WHITE)
    c.circle(9.6, 4.6, 2.0, WHITE)
    c.circle(10.2, 3.6, 0.9, RED)
    c.poly([(11.2, 4.4), (15, 5.4), (11.2, 5.6)], AMBER)
    c.circle(9.9, 4.6, 0.45, INK)
    c.path([(5.4, 11), (5.2, 13)], 1.0, INK)
    c.circle(12, 12.4, 1.8, GREEN)
    for dx, dy in ((-1.9, -2.2), (0, -2.9), (1.9, -2.2)):
        c.circle(12 + dx, 12.4 + dy, 0.8, GREEN)


def k_formasi(c):
    """The eight trigrams around a jade disc: formations and talismans."""
    c.circle(8, 8, 7.4, JADE_D)
    c.circle(8, 8, 6.6, hexc("2A3A5C"))
    for k in range(8):
        ang = k * math.pi / 4
        cx, cy = 8 + math.cos(ang) * 5.0, 8 + math.sin(ang) * 5.0
        for bar in range(3):
            off = (bar - 1) * 0.85
            px, py = cx + math.cos(ang) * off, cy + math.sin(ang) * off
            tx, ty = -math.sin(ang) * 1.2, math.cos(ang) * 1.2
            broken = (k + bar) % 3 == 0
            if broken:
                c.line(px - tx, py - ty, px - tx * 0.25, py - ty * 0.25, 0.45, GOLD_L, False)
                c.line(px + tx * 0.25, py + ty * 0.25, px + tx, py + ty, 0.45, GOLD_L, False)
            else:
                c.line(px - tx, py - ty, px + tx, py + ty, 0.45, GOLD_L, False)
    c.circle(8, 8, 2.2, JADE_L)
    c.circle(8, 8, 1.0, JADE_P)


def k_unsur(c):
    """Five coloured Qi motes circling: control of the elements."""
    cols = [hexc("9FD8FF"), hexc("FF6A2A"), hexc("7CD67C"), hexc("E2B33C"), hexc("D8E0E6")]
    for k in range(5):
        ang = -math.pi / 2 + k * 2 * math.pi / 5
        cx, cy = 8 + math.cos(ang) * 4.6, 8 + math.sin(ang) * 4.6
        c.path([(cx, cy), (8 + math.cos(ang + 0.7) * 3.0, 8 + math.sin(ang + 0.7) * 3.0)], 0.9, a(cols[k], 160))
        c.circle(cx, cy, 1.9, cols[k])
        c.circle(cx - 0.5, cy - 0.5, 0.6, (255, 255, 255, 200))
    c.circle(8, 8, 1.6, WHITE)


def k_alkimia(c):
    """A three-legged bronze cauldron with fire below: pills and furnaces."""
    c.poly([(5, 15.4), (6.2, 12.8), (7, 12.8), (6.4, 15.4)], BRONZE_D)
    c.poly([(11, 15.4), (9.8, 12.8), (9, 12.8), (9.6, 15.4)], BRONZE_D)
    c.ellipse(8, 9.8, 5.6, 3.8, BRONZE)
    c.ellipse(8, 8.6, 5.0, 2.4, BRONZE_L)
    c.ellipse(8, 6.6, 5.4, 1.4, BRONZE_D)
    c.ellipse(8, 6.4, 4.4, 0.9, hexc("3A220E"))
    c.rect(2.0, 6.0, 3.2, 8.0, BRONZE_D)
    c.rect(12.8, 6.0, 14.0, 8.0, BRONZE_D)
    c.path([(6.4, 5.4), (6.0, 3.6), (6.8, 1.8)], 0.8, a(WHITE, 150))
    c.path([(9.4, 5.4), (9.8, 3.4), (9.2, 1.4)], 0.8, a(WHITE, 120))
    c.ring(8, 10, 2.0, 2.6, GOLD_D)


def k_tubuh(c):
    """A clenched fist wrapped in cloth: steel bodies."""
    c.rect(5, 10.6, 11, 15.4, hexc("E8DEC8"))
    c.line(5, 12.2, 11, 11.6, 0.5, hexc("A89A80"))
    c.line(5, 13.8, 11, 13.2, 0.5, hexc("A89A80"))
    c.poly([(3.6, 5.8), (12.6, 5.0), (13.2, 10.8), (4.2, 11.4)], hexc("D89A6A"))
    for k in range(4):
        x = 4.2 + k * 2.2
        c.ellipse(x + 1.0, 5.6, 1.1, 1.2, hexc("E8B080"))
        c.line(x + 2.1, 5.0, x + 2.2, 8.2, 0.45, hexc("8A5030"))
    c.poly([(3.6, 8.8), (7.6, 8.0), (8.0, 10.4), (3.8, 11.2)], hexc("C88A5A"))
    c.line(3.8, 9.0, 7.4, 8.4, 0.4, hexc("8A5030"))


def k_darah(c):
    """A drop of blood with a dark rim: blood offerings."""
    c.poly([(8, 1.0), (12.2, 8.0), (12.6, 10.4), (11.0, 13.6), (8, 14.8), (5.0, 13.6), (3.4, 10.4), (3.8, 8.0)], BLOOD)
    c.poly([(8, 2.6), (11.2, 8.4), (11.4, 10.4), (10.0, 12.8), (8, 13.6), (8, 2.6)], hexc("6A0A16"))
    c.ellipse(6.2, 9.2, 1.0, 2.0, (255, 140, 140, 190))
    c.circle(6.6, 11.8, 0.6, (255, 200, 200, 170))


def k_racun(c):
    """A stoppered vial of violet brew with a skull mark."""
    c.rect(6.6, 1.2, 9.4, 3.2, WOOD)
    c.rect(7.0, 3.0, 9.0, 5.0, hexc("C8D8E0"))
    c.circle(8, 10.4, 4.8, hexc("C8D8E0"))
    c.circle(8, 10.8, 4.0, POISON)
    c.ellipse(8, 8.4, 3.4, 1.0, POISON_L)
    c.circle(8, 11.0, 1.7, hexc("F0E8F8"))
    c.rect(7.2, 12.2, 8.8, 13.4, hexc("F0E8F8"))
    c.circle(7.4, 10.8, 0.45, POISON)
    c.circle(8.6, 10.8, 0.45, POISON)
    c.ellipse(5.6, 9.4, 0.6, 1.4, (255, 255, 255, 140))


def k_bayangan(c):
    """A crescent moon behind a veil: silence and infiltration."""
    c.circle(8, 7, 6.0, hexc("D8DCF0"))
    c.circle(10.4, 5.4, 5.0, SHADOW)
    c.poly([(1.5, 9.5), (14.5, 9.5), (13.4, 15.4), (2.6, 15.4)], SHADOW_L)
    c.poly([(1.5, 9.5), (14.5, 9.5), (14.0, 10.6), (2.0, 10.6)], SHADOW)
    for k in range(4):
        x = 3.4 + k * 3.0
        c.line(x, 10.6, x - 0.4, 15.2, 0.4, SHADOW)
    c.circle(6.0, 12.4, 0.7, hexc("C83A30"))
    c.circle(9.6, 12.4, 0.7, hexc("C83A30"))


CORAK = [k_pedang, k_buddha, k_binatang, k_formasi, k_unsur, k_alkimia, k_tubuh, k_darah, k_racun, k_bayangan]

KELAS_COL = [(STONE, STONE_L, STONE_D), (GREEN, GREEN_L, GREEN_D), (BLUE, BLUE_L, BLUE_D), (PURPLE, PURPLE_L, hexc("4B2280")),
             (GOLD, GOLD_L, GOLD_D)]


def i_kelas(n):
    def draw(c):
        base, light, dark = KELAS_COL[n - 1]
        c.poly([(3, 1.2), (13, 1.2), (14.4, 3.2), (14.4, 13.0), (8, 15.4), (1.6, 13.0), (1.6, 3.2)], dark)
        c.poly([(3.6, 2.2), (12.4, 2.2), (13.4, 3.6), (13.4, 12.4), (8, 14.4), (2.6, 12.4), (2.6, 3.6)], base)
        c.poly([(3.6, 2.2), (12.4, 2.2), (13.4, 3.6), (2.6, 3.6)], light)
        # pips: the class number
        for k in range(n):
            row, col = divmod(k, 3)
            count = min(3, n - row * 3)
            x = 8 - (count - 1) * 1.6 + col * 3.2
            y = 6.6 + row * 3.6
            c.circle(x, y, 1.2, GOLD_D)
            c.circle(x, y, 0.85, GOLD_L if n < 5 else WHITE)
        c.line(4.0, 12.0, 12.0, 12.0, 0.5, a(light, 200))
    return draw


def i_langit(c):
    """A palace roof on a cloud: owns a cloud palace (37)."""
    c.ellipse(8, 12.4, 6.8, 2.4, CLOUD)
    c.ellipse(4.4, 11.4, 2.6, 2.0, CLOUD)
    c.ellipse(11.6, 11.2, 2.8, 2.2, CLOUD)
    c.ellipse(8, 13.4, 6.0, 1.2, hexc("C8D8E8"))
    c.rect(5.0, 7.0, 11.0, 10.6, RED)
    c.rect(7.2, 8.0, 8.8, 10.6, RED_D)
    c.poly([(2.6, 7.4), (8, 3.2), (13.4, 7.4), (12.2, 7.8), (8, 5.0), (3.8, 7.8)], hexc("2A5A8A"))
    c.poly([(1.6, 7.6), (3.4, 6.8), (3.8, 7.8)], hexc("2A5A8A"))
    c.poly([(14.4, 7.6), (12.6, 6.8), (12.2, 7.8)], hexc("2A5A8A"))
    c.circle(8, 2.6, 0.8, GOLD_L)


# ===================================================================================================== row 1

def p_gerbang(c):
    """A sect gate arch (paifang) on two pillars."""
    c.rect(3.0, 6.0, 4.6, 15.2, RED)
    c.rect(11.4, 6.0, 13.0, 15.2, RED)
    c.rect(1.4, 4.4, 14.6, 6.2, hexc("2A5A8A"))
    c.poly([(0.6, 4.6), (8, 1.2), (15.4, 4.6)], hexc("1E446A"))
    c.rect(2.6, 7.4, 13.4, 8.6, RED_D)
    c.rect(6.2, 6.4, 9.8, 9.4, GOLD)
    c.rect(6.8, 7.0, 9.2, 8.8, hexc("2A1A10"))
    c.rect(2.6, 14.4, 5.0, 15.4, STONE_D)
    c.rect(11.0, 14.4, 13.4, 15.4, STONE_D)


def p_token(c):
    """A jade token on a red cord: Token Naik Sekte."""
    c.path([(8, 0.6), (8, 3.4)], 0.8, RED_L)
    c.circle(8, 3.6, 0.9, GOLD)
    c.poly([(4.2, 4.4), (11.8, 4.4), (12.6, 6.0), (12.6, 13.0), (8, 15.4), (3.4, 13.0), (3.4, 6.0)], JADE_D)
    c.poly([(4.8, 5.2), (11.2, 5.2), (11.8, 6.4), (11.8, 12.6), (8, 14.6), (4.2, 12.6), (4.2, 6.4)], JADE)
    c.poly([(4.8, 5.2), (11.2, 5.2), (11.8, 6.4), (4.2, 6.4)], JADE_L)
    c.rect(6.0, 7.6, 10.0, 11.4, a(JADE_D, 200))
    c.line(6.6, 8.6, 9.4, 8.6, 0.5, GOLD_L)
    c.line(8.0, 8.6, 8.0, 10.8, 0.5, GOLD_L)
    c.line(6.6, 10.6, 9.4, 10.6, 0.5, GOLD_L)


def p_undangan(c):
    """A folded letter with a red wax seal: an invitation."""
    c.poly([(1.6, 4.0), (14.4, 4.0), (14.4, 13.4), (1.6, 13.4)], PAPER)
    c.poly([(1.6, 4.0), (8, 9.0), (14.4, 4.0)], PAPER_D)
    c.line(1.6, 13.4, 6.4, 8.6, 0.5, PAPER_D)
    c.line(14.4, 13.4, 9.6, 8.6, 0.5, PAPER_D)
    c.circle(8, 9.0, 2.4, RED)
    c.circle(8, 9.0, 1.6, RED_L)
    c.rect(7.4, 8.4, 8.6, 9.6, RED_D)


def p_pelayan(c):
    """A bamboo broom leaning on a wooden bucket: the servant door."""
    shadow(c)
    c.poly([(3.2, 8.6), (9.4, 8.6), (8.8, 14.6), (3.8, 14.6)], WOOD_L)
    c.rect(3.0, 8.0, 9.6, 9.2, WOOD_D)
    c.rect(3.4, 11.2, 9.2, 12.0, WOOD_D)
    c.ellipse(6.3, 8.6, 3.0, 0.7, hexc("6FA8C8"))
    c.line(13.6, 1.0, 10.4, 11.0, 0.9, hexc("DCC783"))
    c.poly([(9.0, 10.4), (11.6, 11.2), (12.6, 15.4), (7.8, 15.0)], hexc("C8A85A"))
    for k in range(4):
        c.line(8.8 + k * 0.9, 11.0, 8.4 + k * 1.2, 15.0, 0.35, hexc("8A6A2A"))


def p_tamu(c):
    """A tea bowl with rising steam: the guest elder."""
    c.ellipse(8, 14.0, 5.6, 1.4, (0, 0, 0, 60))
    c.poly([(2.6, 7.6), (13.4, 7.6), (12.0, 12.6), (4.0, 12.6)], hexc("E8EEF0"))
    c.poly([(2.6, 7.6), (13.4, 7.6), (13.0, 9.0), (3.0, 9.0)], hexc("C8D8E0"))
    c.ellipse(8, 7.6, 5.4, 1.2, hexc("6A8A3A"))
    c.rect(5.6, 12.6, 10.4, 13.6, hexc("C8D8E0"))
    c.path([(2.8, 10.0), (4.6, 11.0), (6.0, 10.4)], 0.5, BLUE)
    c.path([(10.0, 10.4), (11.4, 11.0), (13.0, 10.2)], 0.5, BLUE)
    c.path([(6.6, 6.0), (6.0, 4.0), (6.8, 2.0)], 0.7, a(WHITE, 170))
    c.path([(9.4, 6.0), (10.0, 3.8), (9.2, 1.6)], 0.7, a(WHITE, 140))


def al_lurus(c):
    """A white sun with a sword through it: righteous sects."""
    for k in range(8):
        ang = k * math.pi / 4
        c.line(8 + math.cos(ang) * 5.0, 8 + math.sin(ang) * 5.0, 8 + math.cos(ang) * 7.2, 8 + math.sin(ang) * 7.2, 0.9, GOLD_L)
    c.circle(8, 8, 4.6, hexc("F4F8FC"))
    c.ring(8, 8, 3.8, 4.6, hexc("C8D6E8"))
    c.line(8, 2.6, 8, 13.4, 1.0, IRON)
    c.rect(6.0, 10.2, 10.0, 11.0, GOLD)


def al_netral(c):
    """The taiji: neutral sects."""
    c.circle(8, 8, 7.0, INK)
    c.circle(8, 8, 6.4, hexc("F4F0E4"))
    c.ellipse(8, 8, 6.4, 6.4, INK, -math.pi / 2, math.pi / 2)
    c.circle(8, 4.8, 3.2, INK)
    c.circle(8, 11.2, 3.2, hexc("F4F0E4"))
    c.circle(8, 4.8, 1.0, hexc("F4F0E4"))
    c.circle(8, 11.2, 1.0, INK)


def al_iblis(c):
    """A horned crimson flame: demonic sects."""
    c.poly([(8, 1.0), (11.2, 5.4), (12.6, 3.0), (13.4, 8.6), (12.0, 13.4), (8, 15.2), (4.0, 13.4), (2.6, 8.6), (3.4, 3.0),
            (4.8, 5.4)], hexc("6A1420"))
    c.poly([(8, 3.8), (10.4, 7.6), (11.2, 10.6), (9.8, 13.4), (8, 14.0), (6.2, 13.4), (4.8, 10.6), (5.6, 7.6)], BLOOD_L)
    c.poly([(8, 7.0), (9.6, 10.0), (9.2, 12.6), (8, 13.2), (6.8, 12.6), (6.4, 10.0)], hexc("FF9A6A"))
    c.circle(6.6, 9.6, 0.6, hexc("FFE0A0"))
    c.circle(9.4, 9.6, 0.6, hexc("FFE0A0"))


def i_merosot(c):
    """A cracked plaque and a falling arrow: MEROSOT (41.4.7)."""
    c.poly([(2, 2), (11, 2), (11, 9.4), (2, 9.4)], STONE)
    c.poly([(2, 2), (11, 2), (11, 3.2), (2, 3.2)], STONE_L)
    c.path([(6.4, 2.0), (5.4, 4.6), (7.0, 6.0), (5.8, 9.4)], 0.7, STONE_D)
    c.line(12.6, 4.0, 12.6, 12.4, 1.4, RED)
    c.poly([(10.0, 11.2), (15.2, 11.2), (12.6, 15.2)], RED)
    c.line(3.4, 11.6, 8.4, 11.6, 0.6, a(STONE_D, 200))
    c.line(4.4, 13.4, 7.4, 13.4, 0.6, a(STONE_D, 160))


def i_kabar(c):
    """A teapot pouring: rumours of the tea house."""
    c.ellipse(7, 10.4, 5.0, 4.0, hexc("8A5A3A"))
    c.ellipse(7, 9.0, 4.0, 2.0, hexc("B07A48"))
    c.rect(5.8, 4.6, 8.2, 6.6, hexc("6A3A1A"))
    c.circle(7, 4.4, 1.0, hexc("B07A48"))
    c.path([(11.6, 9.4), (13.4, 7.4), (14.8, 6.6)], 1.1, hexc("8A5A3A"))
    c.path([(2.2, 8.4), (1.0, 10.0), (2.4, 12.4)], 0.8, hexc("6A3A1A"))
    c.path([(14.8, 7.6), (15.0, 10.0), (14.4, 13.0)], 0.5, a(hexc("9AC060"), 220))
    c.path([(3.6, 4.2), (3.0, 2.4), (3.8, 0.8)], 0.6, a(WHITE, 140))


def i_shifu(c):
    """An old master's head with a long white beard and topknot."""
    c.circle(8, 6.4, 3.8, hexc("E8C8A0"))
    c.circle(8, 2.6, 1.6, hexc("D8D8D0"))
    c.rect(7.0, 3.2, 9.0, 4.0, GOLD)
    c.poly([(4.6, 5.4), (5.4, 3.6), (8, 3.0), (10.6, 3.6), (11.4, 5.4), (10.6, 4.6), (8, 4.2), (5.4, 4.6)], hexc("D8D8D0"))
    c.poly([(5.0, 7.8), (11.0, 7.8), (10.2, 11.4), (8, 15.4), (5.8, 11.4)], hexc("F0F0EA"))
    c.line(6.4, 6.0, 7.4, 6.2, 0.5, INK)
    c.line(8.6, 6.2, 9.6, 6.0, 0.5, INK)
    c.path([(6.0, 8.4), (8, 9.2), (10.0, 8.4)], 0.6, hexc("C8C8C0"))
    c.poly([(2.0, 15.6), (4.6, 10.4), (6.0, 15.6)], hexc("4F6A8A"))
    c.poly([(14.0, 15.6), (11.4, 10.4), (10.0, 15.6)], hexc("4F6A8A"))


def i_hukum(c):
    """A black plaque with a gold rule line and a vermilion seal: Balai Hukum."""
    c.poly([(2.4, 2.0), (13.6, 2.0), (13.6, 12.4), (2.4, 12.4)], hexc("1E1A18"))
    c.rect(3.2, 2.8, 12.8, 11.6, hexc("2E2824"))
    for k in range(3):
        y = 4.6 + k * 2.4
        c.line(4.4, y, 9.4, y, 0.6, GOLD_L)
    c.rect(9.8, 7.8, 12.4, 10.6, RED)
    c.rect(10.3, 8.3, 11.9, 10.1, RED_L)
    c.rect(4.0, 12.4, 5.4, 15.4, WOOD_D)
    c.rect(10.6, 12.4, 12.0, 15.4, WOOD_D)


def i_murid(c):
    """Two small disciples, one behind the other: wards (41.8.7)."""
    c.circle(10.6, 5.0, 2.0, hexc("D8B890"))
    c.poly([(7.8, 15.4), (8.6, 8.0), (12.6, 8.0), (13.6, 15.4)], hexc("4F7A9A"))
    c.circle(5.6, 6.6, 2.2, hexc("E8C8A0"))
    c.circle(5.6, 4.2, 1.0, INK)
    c.poly([(2.2, 15.4), (3.2, 9.6), (8.0, 9.6), (9.0, 15.4)], hexc("6FA8C8"))
    c.line(5.6, 9.6, 5.6, 15.4, 0.5, hexc("4F7A9A"))


def i_keluar(c):
    """An open door with a step leading out."""
    c.rect(2.0, 1.6, 10.4, 14.6, WOOD_D)
    c.rect(3.0, 2.6, 9.4, 14.6, hexc("1A140E"))
    c.poly([(3.0, 2.6), (6.6, 3.8), (6.6, 15.0), (3.0, 14.6)], WOOD_L)
    c.circle(5.8, 9.2, 0.5, GOLD)
    c.line(9.0, 9.0, 14.0, 9.0, 1.2, GOLD_L)
    c.poly([(12.4, 6.6), (15.4, 9.0), (12.4, 11.4)], GOLD_L)
    c.rect(1.0, 14.6, 11.4, 15.6, STONE)


def i_jatah(c):
    """A rice bowl crowned with two pills: the weekly allowance."""
    shadow(c, 8, 14.6, 5.6, 1.0)
    c.poly([(2.0, 8.6), (14.0, 8.6), (12.4, 13.0), (3.6, 13.0)], hexc("E8EEF0"))
    c.rect(5.6, 13.0, 10.4, 14.2, hexc("C8D8E0"))
    c.line(2.4, 10.4, 13.6, 10.4, 0.6, BLUE)
    c.ellipse(8, 8.6, 6.0, 1.6, WHITE)
    c.circle(6.4, 6.6, 1.8, hexc("E05A3A"))
    c.circle(9.8, 6.4, 1.8, GOLD)
    c.circle(5.9, 6.0, 0.6, (255, 255, 255, 190))
    c.circle(9.3, 5.8, 0.6, (255, 255, 255, 190))


def i_kursi(c):
    """A carved seat with a high back: a limited rank seat."""
    c.rect(3.6, 1.6, 12.4, 3.2, RED_D)
    c.rect(4.4, 3.2, 11.6, 9.4, RED)
    c.rect(6.0, 4.6, 10.0, 8.0, GOLD_D)
    c.rect(6.6, 5.2, 9.4, 7.4, GOLD)
    c.rect(2.6, 9.4, 13.4, 11.0, RED_D)
    c.rect(3.2, 11.0, 4.4, 15.4, WOOD_D)
    c.rect(11.6, 11.0, 12.8, 15.4, WOOD_D)
    c.rect(2.0, 7.4, 3.6, 9.6, RED_D)
    c.rect(12.4, 7.4, 14.0, 9.6, RED_D)


# ===================================================================================================== row 2: test HUD

def h_tekanan(c):
    """A mountain pressing down: Mountain Pressure."""
    c.poly([(0.6, 14.6), (6.2, 3.0), (9.0, 7.4), (10.8, 5.2), (15.4, 14.6)], STONE)
    c.poly([(6.2, 3.0), (8.0, 6.6), (6.6, 6.2), (5.4, 7.6), (4.6, 6.4)], WHITE)
    c.poly([(10.8, 5.2), (12.0, 7.6), (10.6, 7.2)], WHITE)
    c.poly([(6.2, 3.0), (9.0, 7.4), (9.4, 14.6), (6.2, 14.6)], STONE_D)
    c.line(8, 0.4, 8, 2.2, 0.6, RED_L)
    c.poly([(6.6, 1.6), (9.4, 1.6), (8, 3.0)], RED_L)


def h_ilusi(c):
    """A heart-demon's eye in purple smoke: an illusion."""
    c.ellipse(8, 8, 7.2, 4.6, a(PURPLE, 170))
    c.ellipse(8, 8, 5.6, 3.2, hexc("F0D8F8"))
    c.circle(8, 8, 2.6, BLOOD_L)
    c.ellipse(8, 8, 0.8, 2.2, INK)
    c.path([(1.0, 4.0), (3.0, 2.6), (5.0, 3.6)], 0.6, a(PURPLE_L, 180))
    c.path([(11.0, 12.6), (13.0, 13.4), (15.0, 12.0)], 0.6, a(PURPLE_L, 180))


def h_meditasi(c):
    """A figure sitting cross-legged in meditation with a halo."""
    c.ring(8, 5.2, 3.2, 3.8, a(GOLD_L, 170))
    c.circle(8, 5.2, 2.2, hexc("E8C8A0"))
    c.poly([(5.0, 8.0), (11.0, 8.0), (12.0, 12.0), (4.0, 12.0)], hexc("4F7A9A"))
    c.ellipse(8, 13.0, 6.4, 2.0, hexc("3F6A8A"))
    c.circle(4.6, 12.0, 1.0, hexc("E8C8A0"))
    c.circle(11.4, 12.0, 1.0, hexc("E8C8A0"))


def h_boneka(c):
    """A wooden training dummy with straw arms."""
    shadow(c)
    c.rect(7.0, 9.0, 9.0, 15.0, WOOD_D)
    c.ellipse(8, 7.6, 3.2, 4.0, hexc("C8A85A"))
    c.circle(8, 2.8, 2.2, hexc("C8A85A"))
    c.line(2.0, 6.6, 14.0, 6.6, 1.2, WOOD)
    c.line(4.6, 5.8, 4.6, 8.0, 0.4, hexc("8A6A2A"))
    c.line(11.4, 5.8, 11.4, 8.0, 0.4, hexc("8A6A2A"))
    c.ring(8, 7.8, 1.2, 1.8, RED)
    c.circle(8, 7.8, 0.6, RED)


def h_tebasan(c):
    """A bright crescent of a sword slash over a blade."""
    arc = [(1.2 + 13.6 * t, 10.0 - 8.6 * math.sin(math.pi * t)) for t in [k / 12.0 for k in range(13)]]
    c.path(arc, 2.2, a(SKY, 150))
    c.path(arc, 1.2, WHITE)
    inner = [(3.0 + 10.0 * t, 10.6 - 6.0 * math.sin(math.pi * t)) for t in [k / 10.0 for k in range(11)]]
    c.path(inner, 0.6, a(SKY, 220))
    c.line(9.6, 9.0, 14.6, 14.6, 1.2, IRON_L)
    c.line(9.0, 10.2, 10.8, 8.4, 1.0, GOLD)


def h_perunggu(c):
    """A bronze monk's head with a lotus mark: the bronze-men corridor."""
    c.circle(8, 7.4, 5.6, BRONZE)
    c.circle(6.4, 5.4, 2.0, BRONZE_L)
    c.circle(8, 4.8, 0.7, GOLD_L)
    c.line(5.4, 7.6, 7.0, 7.6, 0.6, BRONZE_D)
    c.line(9.0, 7.6, 10.6, 7.6, 0.6, BRONZE_D)
    c.line(6.8, 10.6, 9.2, 10.6, 0.5, BRONZE_D)
    c.ellipse(8, 14.2, 6.2, 1.8, hexc("C87A30"))
    for k in range(5):
        c.circle(4.0 + k * 2.0, 13.6, 0.7, hexc("6A3A10"))


def h_cakar(c):
    """A beast's paw with claws."""
    c.ellipse(8, 10.6, 3.6, 3.2, BRONZE_D)
    for dx, dy in ((-4.2, -2.4), (-1.6, -4.8), (1.6, -4.8), (4.2, -2.4)):
        c.ellipse(8 + dx, 10.6 + dy, 1.5, 1.8, BRONZE_D)
        c.poly([(8 + dx - 0.5, 10.6 + dy - 1.6), (8 + dx + 0.5, 10.6 + dy - 1.6), (8 + dx, 10.6 + dy - 3.2)], WHITE)
    c.ellipse(8, 11.2, 2.4, 1.8, hexc("D8A880"))


def h_mata_formasi(c):
    """A formation eye: a glowing ring on a stone with trigram ticks."""
    c.circle(8, 8, 7.0, STONE_D)
    c.circle(8, 8, 6.2, STONE)
    for k in range(8):
        ang = k * math.pi / 4
        c.line(8 + math.cos(ang) * 4.6, 8 + math.sin(ang) * 4.6, 8 + math.cos(ang) * 5.8, 8 + math.sin(ang) * 5.8, 0.6, INK)
    c.ring(8, 8, 2.6, 3.6, hexc("6FE0FF"))
    c.circle(8, 8, 1.6, WHITE)


def h_pilar(c):
    """A stone pillar with a glowing Qi band."""
    shadow(c)
    c.rect(5.0, 2.4, 11.0, 14.4, STONE)
    c.rect(5.0, 2.4, 6.6, 14.4, STONE_L)
    c.rect(4.0, 1.0, 12.0, 2.6, STONE_D)
    c.rect(4.0, 13.6, 12.0, 15.0, STONE_D)
    c.rect(5.0, 7.0, 11.0, 9.4, hexc("9FD8FF"))
    c.rect(5.0, 7.6, 11.0, 8.6, WHITE)
    c.ring(8, 8.2, 4.8, 5.6, a(hexc("9FD8FF"), 120))


def h_tungku(c):
    """A furnace flame."""
    c.poly([(8, 0.8), (11.6, 6.0), (12.6, 10.0), (11.0, 13.6), (8, 15.0), (5.0, 13.6), (3.4, 10.0), (4.4, 6.0), (6.2, 7.4)], FIRE)
    c.poly([(8, 5.0), (10.2, 8.8), (10.6, 11.4), (9.4, 13.4), (8, 14.0), (6.6, 13.4), (5.4, 11.4), (5.8, 8.8)], FIRE_L)
    c.ellipse(8, 12.0, 1.6, 2.0, WHITE)


def h_batu_pikul(c):
    """A boulder lashed with rope: the carrying stone."""
    shadow(c, 8, 14.6, 6.0, 1.2)
    c.poly([(2.0, 11.0), (3.0, 5.6), (7.4, 3.0), (12.4, 4.2), (14.4, 9.2), (12.6, 13.6), (5.0, 14.0)], STONE)
    c.poly([(3.0, 5.6), (7.4, 3.0), (12.4, 4.2), (8.4, 6.0)], STONE_L)
    c.poly([(12.6, 13.6), (14.4, 9.2), (12.4, 4.2), (10.0, 9.6)], STONE_D)
    c.path([(2.6, 8.2), (8.0, 9.6), (14.0, 7.8)], 0.8, hexc("C8A85A"))
    c.path([(7.4, 3.0), (8.0, 9.6), (7.6, 14.0)], 0.8, hexc("C8A85A"))


def h_kolam(c):
    """A pool of blood with ripples."""
    c.ellipse(8, 10.6, 7.4, 4.6, STONE_D)
    c.ellipse(8, 10.4, 6.6, 3.8, BLOOD)
    c.ring(8, 10.4, 2.0, 2.6, a(BLOOD_L, 220))
    c.ring(8, 10.4, 4.0, 4.5, a(BLOOD_L, 150))
    c.poly([(8, 1.6), (9.6, 4.8), (8, 6.6), (6.4, 4.8)], BLOOD_L)


def h_cawan(c):
    """A small cup with a wisp of poison."""
    shadow(c, 8, 14.6, 4.8, 1.0)
    c.poly([(3.0, 6.6), (13.0, 6.6), (11.4, 12.4), (4.6, 12.4)], hexc("E0D8C8"))
    c.ellipse(8, 6.6, 5.0, 1.2, POISON)
    c.rect(6.4, 12.4, 9.6, 14.2, hexc("C8B8A0"))
    c.path([(7.0, 5.6), (6.2, 3.6), (7.6, 1.6)], 0.7, a(POISON_L, 200))
    c.path([(9.4, 5.4), (10.2, 3.4), (9.2, 1.4)], 0.6, a(POISON_L, 160))


def h_penjaga(c):
    """A guard's lantern with a watching eye: be unseen."""
    c.line(8, 0.6, 8, 2.6, 0.6, WOOD_D)
    c.rect(5.6, 2.4, 10.4, 3.4, WOOD_D)
    c.ellipse(8, 8.4, 4.6, 5.2, hexc("E05A3A"))
    c.ellipse(8, 8.4, 3.6, 4.4, hexc("FF9A5A"))
    c.rect(5.6, 13.2, 10.4, 14.2, WOOD_D)
    c.ellipse(8, 8.4, 2.6, 1.5, WHITE)
    c.circle(8, 8.4, 1.0, INK)


def h_hati(c):
    """A heart on a scroll: the Ujian Hati."""
    c.rect(2.0, 3.0, 14.0, 13.0, PAPER)
    c.rect(1.4, 2.4, 14.6, 3.6, WOOD)
    c.rect(1.4, 12.4, 14.6, 13.6, WOOD)
    c.circle(6.4, 7.0, 1.9, RED)
    c.circle(9.6, 7.0, 1.9, RED)
    c.poly([(4.6, 7.6), (11.4, 7.6), (8, 11.2)], RED)
    c.circle(5.9, 6.4, 0.6, (255, 200, 200, 200))


def h_sumpah(c):
    """Three incense sticks in a burner: the oath."""
    c.ellipse(8, 12.6, 5.4, 2.2, BRONZE_D)
    c.ellipse(8, 11.6, 5.0, 1.6, BRONZE)
    c.ellipse(8, 11.4, 4.0, 1.0, hexc("8A8070"))
    for k, x in enumerate((6.0, 8.0, 10.0)):
        c.line(x, 11.2, x + (k - 1) * 0.4, 4.0, 0.5, hexc("C0402A"))
        c.circle(x + (k - 1) * 0.4, 3.8, 0.55, FIRE_L)
        c.path([(x + (k - 1) * 0.4, 3.2), (x + (k - 1) * 0.4 - 0.6, 1.8), (x + (k - 1) * 0.4 + 0.2, 0.6)], 0.4, a(WHITE, 130))


# ===================================================================================================== row 3

PANGKAT_COL = ["6E6A64", "7A8C83", "4FA04A", "4F86C8", "8B4FC8", "FFB347", "D4A637", "C83A30", "FFD24A", "7FD6C8"]
PANGKAT_CH = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "T"]


def i_pangkat(i):
    def draw(c):
        col = hexc(PANGKAT_COL[i])
        rim = GOLD if i >= 5 else GOLD_D
        c.poly([(8, 0.6), (14.8, 4.2), (14.8, 11.8), (8, 15.4), (1.2, 11.8), (1.2, 4.2)], rim)
        c.poly([(8, 1.8), (13.6, 4.8), (13.6, 11.2), (8, 14.2), (2.4, 11.2), (2.4, 4.8)], col)
        c.poly([(8, 1.8), (13.6, 4.8), (8, 7.6), (2.4, 4.8)], shade(col, 0.28))
        c.poly([(8, 14.2), (13.6, 11.2), (13.6, 9.0), (8, 12.0), (2.4, 9.0), (2.4, 11.2)], shade(col, -0.22))
        glyph(c, PANGKAT_CH[i], 5.5, 4.5, 1.0, hexc("4A2A08") if i in (5, 6, 8, 9) else hexc("FFF6DE"))
        if i == 8:
            for k in (-1, 1):
                star(c, 8 + k * 4.6, 2.8, 0.5, 1.2, WHITE, 4)
    return draw


def i_papan_jasa(c):
    """A ranking scroll with three gold lines."""
    c.rect(3.0, 1.6, 13.0, 14.4, PAPER)
    c.rect(2.0, 1.0, 14.0, 2.4, RED_D)
    c.rect(2.0, 13.6, 14.0, 15.0, RED_D)
    for k in range(4):
        y = 4.4 + k * 2.4
        c.circle(4.6, y, 0.7, GOLD if k == 0 else GOLD_D)
        c.line(6.0, y, 11.8 - k * 1.2, y, 0.6, INK)


def i_peringkat(c):
    """A medal on a ribbon: the ranking board."""
    c.poly([(4.0, 0.6), (7.4, 0.6), (9.0, 7.0), (6.6, 7.6)], RED)
    c.poly([(12.0, 0.6), (8.6, 0.6), (7.0, 7.0), (9.4, 7.6)], BLUE)
    c.circle(8, 10.6, 4.8, GOLD_D)
    c.circle(8, 10.6, 4.0, GOLD)
    star(c, 8, 10.6, 1.4, 3.0, GOLD_L)


def i_tanya(c):
    """A grey seal with a question mark: a sect not yet heard of."""
    c.circle(8, 8, 7.0, STONE_D)
    c.circle(8, 8, 6.2, STONE)
    c.ring(8, 8, 5.0, 5.6, STONE_L)
    glyph(c, "?", 5.5, 4.5, 1.0, WHITE)


def i_lencana(c):
    """The member badge: a jade disc with a gold rim on a cord."""
    c.path([(4.0, 0.6), (8, 4.0), (12.0, 0.6)], 0.7, RED_L)
    c.circle(8, 9.6, 5.8, GOLD_D)
    c.circle(8, 9.6, 5.0, JADE)
    c.ring(8, 9.6, 3.6, 4.2, JADE_L)
    c.circle(8, 9.6, 1.6, GOLD)
    c.ellipse(6.0, 7.4, 1.4, 0.8, (255, 255, 255, 140))


def i_arah(c):
    """A compass needle: bearing to the gate."""
    c.circle(8, 8, 7.0, WOOD_D)
    c.circle(8, 8, 6.2, PAPER)
    c.poly([(8, 1.8), (9.6, 8), (8, 9.2), (6.4, 8)], RED)
    c.poly([(8, 14.2), (9.6, 8), (8, 6.8), (6.4, 8)], IRON_D)
    c.circle(8, 8, 1.0, GOLD)


def i_ketua(c):
    """A master's crown (guan) with a jade pin."""
    c.poly([(3.0, 13.4), (3.6, 6.0), (6.0, 8.0), (8, 3.6), (10.0, 8.0), (12.4, 6.0), (13.0, 13.4)], GOLD)
    c.poly([(3.0, 13.4), (13.0, 13.4), (13.0, 11.6), (3.0, 11.6)], GOLD_D)
    c.line(1.0, 9.6, 15.0, 9.6, 0.8, JADE)
    c.circle(1.2, 9.6, 1.0, JADE_L)
    c.circle(14.8, 9.6, 1.0, JADE_L)
    c.circle(8, 3.4, 1.0, RED_L)


ICONS = (CORAK + [i_kelas(n) for n in range(1, 6)] + [i_langit]
         + [p_gerbang, p_token, p_undangan, p_pelayan, p_tamu, al_lurus, al_netral, al_iblis, i_merosot, i_kabar, i_shifu, i_hukum,
            i_murid, i_keluar, i_jatah, i_kursi]
         + [h_tekanan, h_ilusi, h_meditasi, h_boneka, h_tebasan, h_perunggu, h_cakar, h_mata_formasi, h_pilar, h_tungku, h_batu_pikul,
            h_kolam, h_cawan, h_penjaga, h_hati, h_sumpah]
         + [i_pangkat(i) for i in range(10)] + [i_papan_jasa, i_peringkat, i_tanya, i_lencana, i_arah, i_ketua])
assert len(ICONS) == 64, len(ICONS)


def build_icons():
    img = Image(256, 64, (0, 0, 0, 0))
    for slot, fn in enumerate(ICONS):
        c = new()
        fn(c)
        put(img, c, (slot % 16) * 16, (slot // 16) * 16)
    return img


# ===================================================================================================== rank cap

def build_cap():
    img = Image(96, 32, (0, 0, 0, 0))
    # 0: the jade cap in greys (tinted with the rank colour by the client)
    c = Canvas(32, 32, 4)
    hexagon = [(16, 1.5), (29.5, 8.8), (29.5, 23.2), (16, 30.5), (2.5, 23.2), (2.5, 8.8)]
    c.poly(hexagon, hexc("8A8A8A"))
    inner = [(16, 4.0), (27.2, 10.0), (27.2, 22.0), (16, 28.0), (4.8, 22.0), (4.8, 10.0)]
    c.poly(inner, hexc("C8C8C8"))
    c.poly([(16, 4.0), (27.2, 10.0), (16, 15.6), (4.8, 10.0)], hexc("ECECEC"))
    c.poly([(16, 28.0), (27.2, 22.0), (27.2, 18.4), (16, 24.2), (4.8, 18.4), (4.8, 22.0)], hexc("9A9A9A"))
    c.ring(16, 16, 6.4, 7.4, hexc("A8A8A8"))
    put(img, c, 0, 0, outline=False)
    # veins in the jade
    rnd = random.Random(41)
    for _ in range(3):
        x, y = rnd.uniform(8, 24), rnd.uniform(8, 24)
        ang = rnd.uniform(0, math.pi * 2)
        for _ in range(14):
            if img.get(int(x), int(y))[3] > 200:
                img.blend(int(x), int(y), (255, 255, 255, 50))
            ang += rnd.uniform(-0.5, 0.5)
            x += math.cos(ang) * 0.9
            y += math.sin(ang) * 0.9
    # 32: the gold rim and gloss
    c = Canvas(32, 32, 4)
    c.poly(hexagon, GOLD_D)
    c.poly([(16, 2.6), (28.4, 9.4), (28.4, 22.6), (16, 29.4), (3.6, 22.6), (3.6, 9.4)], (0, 0, 0, 0))
    rim = Canvas(32, 32, 4)
    rim.poly(hexagon, GOLD)
    put(img, rim, 32, 0, outline=False)
    hole = Canvas(32, 32, 4)
    hole.poly([(16, 3.4), (27.8, 9.7), (27.8, 22.3), (16, 28.6), (4.2, 22.3), (4.2, 9.7)], (255, 255, 255, 255))
    hp = hole.resolve()
    for y in range(32):
        for x in range(32):
            if hp[y][x][3] > 0:
                p = img.get(32 + x, y)
                img.set(32 + x, y, (p[0], p[1], p[2], int(p[3] * (1 - hp[y][x][3] / 255.0))))
    gloss = Canvas(32, 32, 4)
    gloss.ellipse(11.0, 8.6, 5.0, 2.2, (255, 255, 255, 120))
    gloss.ellipse(9.6, 8.0, 2.0, 0.9, (255, 255, 255, 190))
    for k in range(6):
        ang = math.pi / 6 + k * math.pi / 3
        x, y = 16 + math.cos(ang) * 13.6, 16 + math.sin(ang) * 13.6
        gloss.circle(x, y, 0.9, GOLD_L)
    put(img, gloss, 32, 0, outline=False)
    # 64: the halo of the player's own rank
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if 11.0 <= d <= 16.0:
                t = 1.0 - abs(d - 13.5) / 2.5
                img.set(64 + x, y, (255, 240, 190, int(200 * clamp(t) ** 1.5)))
    return img


# ===================================================================================================== member card

def build_kartu():
    img = Image(64, 64, (0, 0, 0, 0))
    for y in range(64):
        for x in range(64):
            n = fbm(x / 11.0, y / 11.0, 41, 64 / 11.0, 3)
            v = hnoise(x / 3.0, y / 3.0, 7)
            base = mix(hexc("D6EBDF"), hexc("BCDCCB"), n)
            base = shade(base, (v - 0.5) * 0.05)
            img.set(x, y, base)
    # veins (seamless-ish in the middle, the nine-slice stretches the centre)
    rnd = random.Random(4110)
    for _ in range(3):
        x, y = rnd.uniform(14, 50), rnd.uniform(14, 50)
        ang = rnd.uniform(0, math.pi * 2)
        for _ in range(22):
            img.blend(int(x), int(y), (255, 255, 255, 38))
            img.blend(int(x) + 1, int(y), (120, 170, 140, 22))
            ang += rnd.uniform(-0.45, 0.45)
            x += math.cos(ang)
            y += math.sin(ang)
            if not (12 <= x < 52 and 12 <= y < 52):
                break
    # border: dark jade bevel
    for y in range(64):
        for x in range(64):
            d = min(x, y, 63 - x, 63 - y)
            if d < 2:
                img.set(x, y, hexc("184B36"))
            elif d < 4:
                img.set(x, y, hexc("2E8963") if (x < 32) == (y < 32) else hexc("246E50"))
            elif d < 5:
                img.set(x, y, hexc("8FD0AE"))
    # gold cloud corners
    for cx, cy, sx, sy in ((0, 0, 1, 1), (63, 0, -1, 1), (0, 63, 1, -1), (63, 63, -1, -1)):
        c = Canvas(12, 12, 4)
        c.poly([(0, 0), (11, 0), (11, 2.2), (2.2, 2.2), (2.2, 11), (0, 11)], GOLD_D)
        c.circle(5.4, 5.4, 2.6, GOLD)
        c.circle(5.4, 5.4, 1.4, GOLD_L)
        c.path([(2.2, 8.4), (4.0, 9.6), (5.6, 8.0)], 0.7, GOLD)
        c.path([(8.4, 2.2), (9.6, 4.0), (8.0, 5.6)], 0.7, GOLD)
        px = c.resolve()
        for yy in range(12):
            for xx in range(12):
                p = px[yy][xx]
                if p[3] > 0:
                    img.blend(cx + sx * xx, cy + sy * yy, p)
    return img


# ===================================================================================================== test HUD

def build_hud():
    img = Image(128, 64, (0, 0, 0, 0))
    # 0,0 bar frame 128x14: black lacquer with a gold rim and a seal cap on the left
    c = Canvas(128, 14, 4)
    c.rect(10, 2, 127, 12, GOLD_D)
    c.rect(11, 3, 126, 11, hexc("14100E"))
    c.rect(12, 4, 125, 10, hexc("241C18"))
    c.circle(7, 7, 6.6, GOLD_D)
    c.circle(7, 7, 5.6, RED_D)
    c.ring(7, 7, 4.2, 4.9, GOLD)
    for k in range(1, 4):
        x = 12 + k * (113 / 4.0)
        c.line(x, 3.2, x, 4.6, 0.6, GOLD)
        c.line(x, 9.4, x, 10.8, 0.6, GOLD)
    put(img, c, 0, 0, outline=False)
    # 0,16 fill 120x6: a light glassy gradient (tinted by the client)
    for y in range(6):
        for x in range(120):
            t = y / 5.0
            v = 255 - int(70 * t) + (14 if y == 1 else 0)
            wave = 8 * math.sin(x / 6.0)
            img.set(x, 16 + y, (int(clamp((v + wave) / 255.0) * 255), int(clamp((v + wave) / 255.0) * 255),
                                int(clamp((v + wave) / 255.0) * 255), 255))
    # 0,24 Lima Pilar dial 64x36: half disc with ticks
    c = Canvas(64, 36, 4)
    c.ellipse(32, 33, 31, 31, GOLD_D, -math.pi, 0)
    c.ellipse(32, 33, 29.6, 29.6, hexc("1A1E26"), -math.pi, 0)
    c.ellipse(32, 33, 27, 27, hexc("262C38"), -math.pi, 0)
    for k in range(21):
        ang = -math.pi + k * math.pi / 20
        r0 = 23.0 if k % 5 else 20.0
        c.line(32 + math.cos(ang) * r0, 33 + math.sin(ang) * r0, 32 + math.cos(ang) * 26.0, 33 + math.sin(ang) * 26.0,
               0.7 if k % 5 else 1.1, GOLD_L if k % 5 == 0 else a(GOLD, 200), False)
    c.rect(1, 33, 63, 35.6, GOLD_D)
    c.circle(32, 33, 3.0, GOLD)
    c.circle(32, 33, 1.6, RED_D)
    put(img, c, 0, 24, outline=False)
    # 64,24 timer ring 32x32
    c = Canvas(32, 32, 4)
    c.ring(16, 16, 12.4, 15.0, GOLD_D)
    c.ring(16, 16, 12.8, 14.4, hexc("241C18"))
    for k in range(12):
        ang = k * math.pi / 6
        c.line(16 + math.cos(ang) * 12.8, 16 + math.sin(ang) * 12.8, 16 + math.cos(ang) * 14.4, 16 + math.sin(ang) * 14.4,
               0.8 if k % 3 else 1.3, GOLD_L, False)
    c.circle(16, 16, 11.8, a(hexc("101418"), 200))
    put(img, c, 64, 24, outline=False)
    # 96,24 stage seal 32x32: a vermilion round seal
    c = Canvas(32, 32, 4)
    c.circle(16, 16, 14.6, RED_D)
    c.circle(16, 16, 13.4, RED)
    c.ring(16, 16, 10.6, 11.8, hexc("F0C8A8"))
    put(img, c, 96, 24, outline=False)
    rnd = random.Random(410)
    for _ in range(90):
        x, y = rnd.randint(98, 125), rnd.randint(26, 53)
        if math.hypot(x - 112, y - 40) < 13.4:
            img.blend(x, y, (255, 230, 210, rnd.randint(20, 60)))
    return img


# ===================================================================================================== verdict stamps

def stamp(img, oy, ink, paper_hole):
    c = Canvas(128, 24, 4)
    c.rect(2, 1, 126, 23, ink)
    c.rect(4, 3, 124, 21, (0, 0, 0, 0))
    inner = Canvas(128, 24, 4)
    inner.rect(2, 1, 126, 23, ink)
    px = inner.resolve()
    hole = Canvas(128, 24, 4)
    hole.rect(4.2, 3.2, 123.8, 20.8, WHITE)
    hp = hole.resolve()
    hole2 = Canvas(128, 24, 4)
    hole2.rect(6.0, 5.0, 122.0, 19.0, ink)
    hole3 = Canvas(128, 24, 4)
    hole3.rect(7.2, 6.2, 120.8, 17.8, WHITE)
    p2 = hole2.resolve()
    p3 = hole3.resolve()
    rnd = random.Random(oy * 7 + 41)
    for y in range(24):
        for x in range(128):
            outer = px[y][x][3] / 255.0 * (1 - hp[y][x][3] / 255.0)
            second = p2[y][x][3] / 255.0 * (1 - p3[y][x][3] / 255.0)
            cov = max(outer, second * 0.85)
            if cov <= 0:
                continue
            worn = hnoise(x / 2.3, y / 2.3, oy + 3)
            if worn < 0.28 + paper_hole:
                cov *= 0.25
            img.blend(x, oy + y, (ink[0], ink[1], ink[2], int(255 * cov * (0.8 + 0.2 * rnd.random()))))
    # corner flourishes
    for cx in (12, 116):
        for k in range(3):
            img.blend(cx + k - 1, oy + 12, (ink[0], ink[1], ink[2], 200))
            img.blend(cx, oy + 11 + k, (ink[0], ink[1], ink[2], 200))


def build_stempel():
    img = Image(128, 48, (0, 0, 0, 0))
    stamp(img, 0, hexc("C0281C"), 0.0)
    stamp(img, 24, hexc("7A7E80"), 0.08)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = [
        ("ikon", build_icons, 4, (40, 44, 52, 255)),
        ("cap", build_cap, 6, (40, 44, 52, 255)),
        ("kartu", build_kartu, 6, (40, 44, 52, 255)),
        ("hud", build_hud, 4, (90, 110, 90, 255)),
        ("stempel", build_stempel, 4, (230, 222, 200, 255)),
    ]
    for name, fn, k, bg in jobs:
        img = fn()
        img.save(os.path.join(OUT, name + ".png"))
        preview(img, "%s_x%d.png" % (name, k), k, bg)
    print("gen_gabung_ui: wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
