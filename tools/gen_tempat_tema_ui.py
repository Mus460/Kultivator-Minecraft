"""Generates the GUI textures of the themed-town screens (DESIGN 38.T.13.3, module 38TV-U).

Our own art, drawn from primitives with the helpers of tools/gen_tempat_ui.py (Canvas, paste, colours) and written with
tools/png.py. Everything lands in textures/gui/tempat/tema/ and is read by client/tempat/ui/tema/TemaUi.java:

ikon_tema.png    256x80  16 px icon atlas, slot = row * 16 + column, order = TemaUi.I_* constants:
                         row 0 theme emblems and meters, row 1 the first 16 trade goods (dagang/*), row 2 services and
                         mini-game things, row 3 demonic-city things and workshop tools, row 4 the 17th good
                         (minyak bayangan) and state glyphs
mural.png        256x256 16 cave-mural panels 64x64 on sandstone (Tebing Seribu Lukisan): slots 0-11 the twelve tales
                         (sheet key gambar), 12 the second frame of tale 7 ("Pendeta yang Diusir" moves), 13 the empty
                         niche of the player's own mural, 14-15 weathered blanks
irama.png        128x96  rhythm game: row 0 arrow notes 16x16 (up, right, down, left) plain then lit; 0,16 hit ring
                         32x32; 32,16 drum skin; 64,16 loom; 96,16 shuttle 16x16 and 96,32 drumstick 16x16; row 48 the
                         whirling dancer in 4 frames 32x48
jubah.png        256x64  robe on a stand, 32x64 cells: 0 stand, 32 robe body (light grey, tinted by the dye), 64 folds,
                         collar and sash (untinted), 96 awan, 128 bangau, 160 teratai, 192 naga motifs (white, tinted)
menara.png       64x160  Menara Kitab Terlarang: spire 0-16, then floors 9 (top) .. 1 (bottom) 16 px each
latar_gunung.png 160x80  ink-wash mountain backdrop with mist (cable car schema, pilgrimage path)
kartu.png        128x96  Arena Darah: fighter card 64x96 (portrait window 48x48 at 8,12), 64,0 "VS" blood seal 32x32,
                         96,0 odds chip 32x16, 64,32 bet plate 64x16, 64,48 ladder rung 64x16
tenda.png        64x16   striped bazaar awning with scalloped hem (tiles horizontally)
surat.png        32x32   cargo manifest paper, nine-slice (corner 8) with red rules and a torn edge
kandang.png      32x32   iron cell bars, nine-slice, for the captive cards

Previews (x4) go to tools/keluaran/tempat_tema_ui/ for inspection with Read; they are not shipped.

Run with: python tools/gen_tempat_tema_ui.py
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
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "gui", "tempat", "tema")
PREVIEW = os.path.join(HERE, "keluaran", "tempat_tema_ui")

OUTLINE = ui.OUTLINE
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
SAND = hexc("E0B060")
SAND_L = hexc("F4D898")
SAND_D = hexc("9A6A30")
SEA = hexc("3E86B0")
SEA_L = hexc("9AD0EE")
SEA_D = hexc("1E4A6E")
STONE = hexc("A0A8B8")
STONE_D = hexc("5A6270")
BLOOD = hexc("8A1020")
BLOOD_L = hexc("D0303A")
BLOOD_D = hexc("40060C")
BONE = hexc("E8DEC8")
BONE_D = hexc("A89A80")
SILK = hexc("E8A0B8")
TEA = hexc("6A9A3A")


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


def new(w=16, h=16, ss=4):
    return Canvas(w, h, ss)


def put(img, c, ox, oy, outline=True):
    paste(img, c.resolve(), ox, oy, OUTLINE if outline else None)


def a(c, alpha):
    return (c[0], c[1], c[2], alpha)


# ===================================================================================================== icons, row 0

def e_pelabuhan(c):
    """Anchor over a wave: Kota Pelabuhan."""
    c.ring(8, 3.2, 1.0, 2.0, IRON_L)
    c.line(8, 4.5, 8, 13, 1.6, IRON)
    c.line(5, 6.5, 11, 6.5, 1.4, IRON)
    c.path([(3, 9.5), (4, 12), (6.5, 13.6), (8, 13.8), (9.5, 13.6), (12, 12), (13, 9.5)], 1.5, IRON)
    c.poly([(2.2, 9), (4.4, 8.6), (3.6, 10.6)], IRON_L)
    c.poly([(13.8, 9), (11.6, 8.6), (12.4, 10.6)], IRON_L)
    c.line(8.6, 5, 8.6, 12.6, 0.5, shade(IRON_L, 0.3))
    c.path([(1, 15), (3, 14), (5, 15), (7, 14), (9, 15), (11, 14), (13, 15), (15, 14)], 1.0, SEA_L)


def e_oasis(c):
    """Palm over a crescent pool between dunes."""
    c.poly([(0, 16), (0, 12), (5, 10.5), (10, 12.5), (16, 11), (16, 16)], SAND)
    c.poly([(0, 16), (0, 14), (6, 12.6), (16, 14.2), (16, 16)], SAND_D)
    c.ellipse(9, 13.2, 4.2, 1.3, SEA)
    c.ellipse(9.6, 12.9, 3.0, 0.8, SEA_L)
    c.path([(6, 13), (6.4, 9), (7.2, 5.6), (8, 3.8)], 1.2, WOOD)
    for dx, dy in ((-4.5, 1.5), (-3.5, -1.2), (4.5, 1.6), (3.6, -1.4), (0.5, -2.8)):
        c.path([(8, 3.8), (8 + dx * 0.55, 3.8 + dy * 0.5 - 0.6), (8 + dx, 3.8 + dy)], 1.3, GREEN)
    c.circle(7.4, 4.6, 0.7, AMBER)
    c.circle(8.6, 4.8, 0.7, AMBER)


def e_air(c):
    """Round moon bridge over a canal with a lotus: Kota Air."""
    c.rect(0, 11.5, 16, 16, SEA)
    c.path([(0, 13), (3, 12.4), (6, 13), (9, 12.4), (12, 13), (16, 12.4)], 0.6, SEA_L)
    c.ring(8, 11.5, 4.2, 6.2, STONE)
    c.rect(1, 10.6, 15, 11.6, (0, 0, 0, 0))
    c.rect(1.6, 5.4, 14.4, 6.4, shade(STONE, 0.2))
    c.ellipse(8, 11.6, 4.0, 3.2, a(SEA_D, 200), 0, math.pi)
    c.circle(3.5, 14, 1.2, SILK)
    c.circle(3.5, 14, 0.5, GOLD_L)
    c.ellipse(12.5, 14.4, 1.8, 0.6, GREEN)


def e_gunung(c):
    """Twin peaks with snow and a pagoda on the shoulder."""
    c.poly([(0, 15), (5.5, 3), (9, 9), (11, 6), (16, 15)], STONE_D)
    c.poly([(5.5, 3), (7.5, 7), (6.6, 6.5), (5.5, 7.6), (4.4, 6.2), (3.8, 6.8)], WHITE)
    c.poly([(11, 6), (12.6, 9), (11.6, 8.6), (10.4, 9.4), (9.8, 8.4)], WHITE)
    c.poly([(5.5, 3), (16, 15), (9, 15)], a(shade(STONE_D, -0.3), 160))
    c.rect(12, 11, 14.4, 14, WOOD)
    c.poly([(11, 11.2), (15.4, 11.2), (13.2, 9.4)], RED)
    c.poly([(11.6, 9.6), (14.8, 9.6), (13.2, 8.2)], RED_D)
    c.path([(0, 13.5), (4, 12.8), (8, 13.6), (12, 13)], 1.2, a(WHITE, 150))


def e_iblis(c):
    """Horned skull: Kota Iblis."""
    c.path([(4, 6.5), (2.2, 4), (2.4, 1.4)], 1.4, BLOOD)
    c.path([(12, 6.5), (13.8, 4), (13.6, 1.4)], 1.4, BLOOD)
    c.ellipse(8, 7.4, 5, 4.8, BONE)
    c.rect(5.4, 10, 10.6, 13.6, BONE)
    c.ellipse(6, 7.6, 1.5, 1.7, BLOOD_D)
    c.ellipse(10, 7.6, 1.5, 1.7, BLOOD_D)
    c.circle(6, 7.8, 0.5, BLOOD_L)
    c.circle(10, 7.8, 0.5, BLOOD_L)
    c.poly([(8, 9.2), (7.2, 11), (8.8, 11)], BLOOD_D)
    for x in (6.4, 8, 9.6):
        c.line(x, 12, x, 13.6, 0.5, BONE_D)
    c.line(5.4, 12, 10.6, 12, 0.4, BONE_D)


def e_ibu_kota(c):
    """Palace roof with gold ridge: the capital."""
    c.rect(3, 9, 13, 14.5, RED_D)
    c.rect(4, 10, 12, 14.5, RED)
    c.rect(7, 11, 9, 14.5, GOLD_D)
    c.poly([(0.5, 9.4), (15.5, 9.4), (13, 6.6), (3, 6.6)], hexc("2C4A6A"))
    c.poly([(0.5, 9.4), (1.6, 8.0), (3, 8.6)], GOLD)
    c.poly([(15.5, 9.4), (14.4, 8.0), (13, 8.6)], GOLD)
    c.poly([(2.5, 6.8), (13.5, 6.8), (11.6, 4.4), (4.4, 4.4)], hexc("3C6A8A"))
    c.line(4.4, 4.4, 11.6, 4.4, 0.8, GOLD_L)
    c.circle(8, 3.2, 1.0, GOLD_L)
    c.rect(1.5, 14.5, 14.5, 15.5, STONE)


def m_infami(c):
    """Blood drop with a crack: Infami."""
    c.poly([(8, 1.5), (12.4, 9), (12.6, 11), (11, 13.6), (8, 14.6), (5, 13.6), (3.4, 11), (3.6, 9)], BLOOD)
    c.ellipse(8, 10.6, 4.4, 3.8, BLOOD)
    c.ellipse(6.4, 8.4, 1.1, 2.0, a(BLOOD_L, 200))
    c.path([(9.4, 6), (8.6, 8.4), (10, 9.6), (9, 12)], 0.6, BLOOD_D)


def m_gentar(c):
    """A wide staring eye with tremble marks: Gentar (dread)."""
    c.ellipse(8, 8, 6.2, 3.8, BONE)
    c.circle(8, 8, 2.6, BLOOD)
    c.circle(8, 8, 1.2, (10, 4, 6, 255))
    c.circle(7.2, 7.2, 0.6, WHITE)
    c.path([(1.8, 8), (4, 4.4), (8, 3.2), (12, 4.4), (14.2, 8)], 0.8, BLOOD_D)
    c.path([(1.8, 8), (4, 11.6), (8, 12.8), (12, 11.6), (14.2, 8)], 0.8, BLOOD_D)
    for x in (2.5, 13.5):
        c.line(x, 1.5, x + (1 if x < 8 else -1), 3, 0.6, BLOOD_L)
        c.line(x, 14.5, x + (1 if x < 8 else -1), 13, 0.6, BLOOD_L)


def m_suasana(c):
    """A trader's mood: half smiling, half frowning mask."""
    c.circle(8, 8, 6.4, SAND_L)
    c.ellipse(8, 8, 6.4, 6.4, a(SAND_D, 255), -math.pi / 2, math.pi / 2)
    c.circle(8, 8, 5.6, SAND_L)
    c.circle(5.6, 6.6, 0.9, ui.INK)
    c.circle(10.4, 6.6, 0.9, ui.INK)
    c.path([(4.6, 9.8), (6.4, 11.4), (8, 11.6)], 0.9, ui.INK)
    c.path([(8, 11.6), (9.6, 10.8), (11.4, 11.4)], 0.9, ui.INK)
    c.ellipse(4.6, 9, 1.2, 0.7, a(RED_L, 140))


def m_badai(c):
    """Sand swirl."""
    for i, r in enumerate((6.4, 4.6, 2.8)):
        pts = []
        for k in range(22):
            t = k / 21.0 * math.pi * 1.6 + i * 1.4
            rr = r * (0.55 + 0.45 * k / 21.0)
            pts.append((8 + math.cos(t) * rr, 8 + math.sin(t) * rr * 0.8))
        c.path(pts, 1.3 - i * 0.2, [SAND, SAND_D, SAND_L][i])
    for x, y in ((2, 3), (13, 12), (3, 13), (14, 4)):
        c.circle(x, y, 0.6, SAND_D)


def m_kontrak(c):
    """Scroll pierced by a dagger: contract."""
    c.rect(3, 3.5, 12, 13, PAPER)
    c.rect(2, 2.5, 13, 4, PAPER_D)
    c.rect(2, 12.5, 13, 14, PAPER_D)
    for y in (6, 8, 10):
        c.line(4.4, y, 10, y, 0.5, a(ui.INK, 160))
    c.line(14.5, 1.5, 6.5, 11.5, 1.2, IRON_L)
    c.line(14.5, 1.5, 12.5, 4, 1.4, BLOOD)
    c.line(11.4, 3.4, 13.6, 5.4, 1.0, GOLD)
    c.circle(10.6, 11.6, 1.0, BLOOD_L)


def m_asal(c):
    """Square origin marker (house of origin)."""
    c.rect(3, 3, 13, 13, GOLD_D)
    c.rect(4, 4, 12, 12, GOLD)
    c.rect(6, 6, 10, 10, GOLD_L)


def m_terbaik(c):
    """Gold up-triangle: best price."""
    c.poly([(8, 2), (14.5, 13), (1.5, 13)], GOLD)
    c.poly([(8, 4.6), (12, 11.8), (4, 11.8)], GOLD_L)
    c.poly([(8, 2), (8, 13), (1.5, 13)], a(GOLD_D, 120))


def m_jenuh(c):
    """Sagging scale with a down arrow: saturated market."""
    c.line(8, 3, 8, 13, 1.0, WOOD)
    c.line(2.5, 4.4, 13.5, 6.4, 1.0, WOOD_L)
    c.path([(2.5, 4.4), (1.6, 8), (3.4, 8)], 0.5, IRON)
    c.path([(13.5, 6.4), (12.6, 10), (14.4, 10)], 0.5, IRON)
    c.ellipse(2.5, 8.2, 1.8, 0.7, IRON_L)
    c.ellipse(13.5, 10.2, 1.8, 0.7, IRON_L)
    c.rect(5, 13, 11, 14.4, WOOD_D)
    c.poly([(13.5, 11.4), (15.6, 13.4), (11.4, 13.4)], ui.RED)


def m_kapal(c):
    """Junk ship with batten sails."""
    c.poly([(1.5, 10), (14.5, 10), (12.6, 13.4), (3.4, 13.4)], WOOD)
    c.rect(1.5, 9.4, 14.5, 10.4, WOOD_D)
    c.line(8, 2, 8, 10, 0.8, WOOD_D)
    c.poly([(8.4, 2.4), (13.2, 3.6), (12.6, 9), (8.4, 9)], hexc("C8643A"))
    c.poly([(7.6, 3.4), (3.6, 4.4), (4.2, 9), (7.6, 9)], hexc("D87A4A"))
    for y in (4.6, 6.2, 7.8):
        c.line(8.4, y, 13, y, 0.4, hexc("7A3018"))
        c.line(3.8, y, 7.6, y, 0.4, hexc("7A3018"))
    c.path([(0, 14.6), (4, 13.8), (8, 14.6), (12, 13.8), (16, 14.6)], 0.9, SEA_L)


def m_unta(c):
    """Bactrian camel."""
    c.ellipse(8, 9, 5, 2.8, SAND)
    c.circle(6.2, 6.4, 1.8, SAND_D)
    c.circle(9.8, 6.4, 1.8, SAND_D)
    c.path([(12.6, 8.6), (14, 5.6), (14.6, 4)], 1.4, SAND)
    c.ellipse(15, 3.8, 1.2, 0.8, SAND)
    for x in (4.4, 6.2, 10, 11.8):
        c.line(x, 10.4, x, 14.6, 0.9, SAND_D)
    c.rect(6, 7.6, 10, 9, RED)
    c.circle(14.9, 3.4, 0.3, ui.INK)


# ===================================================================================================== row 1: trade goods

def g_ikan(c):
    c.ellipse(8, 8, 6, 2.4, hexc("B8A078"))
    c.poly([(13.4, 8), (15.6, 5.4), (15.6, 10.6)], hexc("8A7050"))
    c.circle(4, 7.4, 0.6, ui.INK)
    for x in (6, 8, 10):
        c.line(x, 6.2, x - 0.8, 9.8, 0.4, hexc("8A7050"))
    c.line(1, 3, 15, 3, 0.6, WOOD)
    c.line(8, 3, 8, 5.8, 0.5, hexc("C8B890"))


def g_mutiara(c):
    c.ellipse(8, 10.5, 6.5, 3.4, hexc("8A9AA8"))
    c.ellipse(8, 10, 5.4, 2.4, hexc("E8E0F0"))
    c.circle(8, 8.4, 3.0, hexc("F4F0FA"))
    c.circle(7, 7.4, 1.0, WHITE)
    c.ring(8, 8.4, 2.6, 3.0, a(hexc("B8C8E8"), 200))


def g_rempah(c):
    c.ellipse(8, 11.6, 6, 2.6, WOOD)
    c.ellipse(8, 10.8, 5.4, 1.8, hexc("C8641A"))
    for i in range(9):
        x = 4 + (i * 37 % 8)
        y = 9.6 + (i * 13 % 3) * 0.6
        c.circle(x, y, 0.8, [hexc("E8A020"), hexc("A83A10"), hexc("6A2A08")][i % 3])
    c.poly([(9, 3), (12.6, 7.4), (8.6, 8.6)], hexc("7A2A10"))
    c.line(10.4, 3.4, 10.6, 8.2, 0.4, hexc("E8A020"))


def g_porselen(c):
    c.poly([(5.4, 3), (10.6, 3), (10, 5), (12.8, 9), (11.6, 13.4), (4.4, 13.4), (3.2, 9), (6, 5)], hexc("BFE0D0"))
    c.rect(5.4, 2.4, 10.6, 3.4, hexc("8AC0A8"))
    c.path([(4, 9), (6, 7.6), (8, 9), (10, 7.6), (12, 9)], 0.7, hexc("2A6A8A"))
    c.circle(8, 11, 1.2, hexc("2A6A8A"))
    c.ellipse(5.6, 8, 0.6, 2.2, a(WHITE, 200))


def g_kurma(c):
    c.path([(8, 2), (8, 6), (6, 9), (10, 9)], 0.6, WOOD_D)
    for x, y in ((5.4, 9.6), (8, 10.4), (10.6, 9.6), (6.6, 12.6), (9.4, 12.6)):
        c.ellipse(x, y, 1.6, 2.2, hexc("B8641A"))
        c.ellipse(x - 0.5, y - 0.8, 0.5, 0.8, GOLD_L)


def g_permadani(c):
    c.rect(2, 3, 14, 13, hexc("8E1F2A"))
    c.rect(3, 4, 13, 12, hexc("C8641A"))
    c.rect(4.4, 5.4, 11.6, 10.6, hexc("2A3A6A"))
    c.poly([(8, 5.8), (11, 8), (8, 10.2), (5, 8)], GOLD)
    c.circle(8, 8, 0.8, hexc("8E1F2A"))
    for x in range(2, 15, 2):
        c.line(x, 13, x, 14.6, 0.5, GOLD_L)
        c.line(x, 1.4, x, 3, 0.5, GOLD_L)


def g_giok(c):
    c.poly([(3, 6), (6, 2.6), (12, 3.4), (14, 8), (11, 13.4), (5, 13), (2.2, 10)], hexc("3E9A6A"))
    c.poly([(6, 2.6), (12, 3.4), (10, 7), (6.4, 6.4)], hexc("8AD0A8"))
    c.poly([(14, 8), (11, 13.4), (9.6, 9.6)], hexc("1E5A3A"))
    c.circle(6.4, 5, 0.8, a(WHITE, 220))
    c.path([(4.4, 10), (7, 9), (8.8, 11)], 0.4, a(hexc("D8F0E0"), 180))


def g_kaca(c):
    cols = [hexc("D84A4A", 220), hexc("4A8AD8", 220), hexc("E8C04A", 220), hexc("4AB88A", 220)]
    for i, (x, y) in enumerate(((3, 3), (8.4, 3), (3, 8.4), (8.4, 8.4))):
        c.rect(x, y, x + 4.6, y + 4.6, cols[i])
        c.line(x + 0.6, y + 0.8, x + 2.6, y + 0.8, 0.5, a(WHITE, 220))
    c.rect(2, 7.6, 14, 8.4, IRON_D)
    c.rect(7.6, 2, 8.4, 14, IRON_D)
    ui_frame(c, 2, 2, 14, 14, IRON_D)


def ui_frame(c, x0, y0, x1, y1, col, w=0.8):
    c.line(x0, y0, x1, y0, w, col, False)
    c.line(x1, y0, x1, y1, w, col, False)
    c.line(x1, y1, x0, y1, w, col, False)
    c.line(x0, y1, x0, y0, w, col, False)


def g_sutra(c):
    c.poly([(2, 4), (11, 2.4), (14, 6), (5, 7.6)], hexc("E8B8D0"))
    c.poly([(2, 4), (5, 7.6), (5, 13.6), (2, 10)], hexc("C890B0"))
    c.poly([(5, 7.6), (14, 6), (14, 12), (5, 13.6)], hexc("F4D0E0"))
    c.path([(6, 9), (9, 8.4), (12, 9)], 0.5, GOLD)
    c.path([(6, 11.4), (9, 10.8), (12, 11.4)], 0.5, GOLD)
    c.line(6, 8, 13.4, 6.8, 0.4, a(WHITE, 200))


def g_teh(c):
    c.poly([(4, 6), (12, 6), (11, 13.6), (5, 13.6)], hexc("C8A060"))
    c.rect(3.4, 5, 12.6, 6.6, hexc("8A6A30"))
    c.rect(6, 8.6, 10, 11.6, RED)
    c.line(7, 9.6, 9, 9.6, 0.4, GOLD_L)
    c.line(7, 10.6, 9, 10.6, 0.4, GOLD_L)
    c.ellipse(6.4, 3.6, 1.6, 0.9, TEA)
    c.ellipse(9.4, 3.2, 1.6, 0.9, shade(TEA, 0.2))


def g_arak(c):
    c.ellipse(8, 10, 5, 4.2, hexc("6A4A2A"))
    c.ellipse(8, 9.4, 4.2, 3.4, hexc("8A6A3A"))
    c.rect(6.4, 2.6, 9.6, 6.4, hexc("6A4A2A"))
    c.rect(5.8, 2, 10.2, 3.2, RED)
    c.rect(5.4, 8, 10.6, 11.6, PAPER)
    c.line(8, 8.6, 8, 11, 0.8, ui.INK)
    c.ellipse(6, 8.4, 0.6, 1.6, a(WHITE, 120))


def g_herba_kering(c):
    c.line(8, 14.6, 8, 4, 0.7, hexc("8A7A4A"))
    for i, (dx, y) in enumerate(((-3.6, 6), (3.4, 7.4), (-3, 9.4), (3.2, 10.8), (0, 3.4))):
        c.ellipse(8 + dx * 0.55, y, 2.2 if dx else 1.2, 1.0 if dx else 1.8, [hexc("8A9A4A"), hexc("A8984A"), hexc("6A7A3A")][i % 3])
    c.rect(6.4, 12, 9.6, 13.4, RED)


def g_dupa(c):
    for i, x in enumerate((5, 8, 11)):
        c.line(x, 13.6, x + (i - 1) * 0.6, 4.6, 0.8, hexc("A8341A"))
        c.circle(x + (i - 1) * 0.6, 4.4, 0.6, hexc("FF8A3A"))
    c.path([(8.2, 4), (7, 2.6), (8.6, 1.4), (7.6, 0.4)], 0.5, a(WHITE, 170))
    c.ellipse(8, 13.8, 5.4, 1.6, hexc("8A7A6A"))
    c.ellipse(8, 13.2, 4.6, 1.0, hexc("C8B8A0"))


def g_teh_awan(c):
    c.ellipse(8, 11, 6, 3.2, hexc("E8E8E0"))
    c.ellipse(8, 10.4, 5, 2.2, hexc("5A8A3A"))
    c.ellipse(8, 10.2, 3.6, 1.4, hexc("8ABA5A"))
    c.path([(5, 7), (4.4, 5), (6, 3.6)], 0.8, a(WHITE, 200))
    c.path([(9, 6.6), (10.4, 4.6), (9.4, 2.8)], 0.8, a(WHITE, 200))
    c.ellipse(7.6, 3.2, 2.6, 1.0, a(WHITE, 140))


def g_lingzhi(c):
    c.line(8, 14.6, 8, 8, 1.2, hexc("8A4A1A"))
    c.ellipse(8, 7, 6.2, 3.6, hexc("8A2A10"), -math.pi, 0)
    c.ellipse(8, 7, 6.2, 1.2, hexc("6A1A08"))
    for r in (2.0, 3.6, 5.0):
        c.ellipse(8, 7, r, r * 0.55, a(hexc("D8783A"), 160), -math.pi, 0)
    c.ellipse(8, 6, 5.4, 2.4, a(hexc("C84A1A"), 120), -math.pi, 0)
    c.ellipse(3.4, 12.6, 2.4, 1.2, hexc("8A3A10"), -math.pi, 0)


def g_racun(c):
    c.ellipse(8, 10.6, 4.6, 4.0, hexc("2A3A2A"))
    c.ellipse(8, 10.6, 3.6, 3.0, hexc("4AA838"))
    c.rect(6.6, 3.4, 9.4, 7.4, hexc("2A3A2A"))
    c.rect(6.2, 2.4, 9.8, 3.6, WOOD)
    c.circle(8, 10.4, 1.4, BONE)
    c.circle(7.5, 10.2, 0.4, ui.INK)
    c.circle(8.5, 10.2, 0.4, ui.INK)
    c.circle(6.6, 9.2, 0.6, a(hexc("D8FF8A"), 200))


def g_minyak(c):
    c.poly([(5, 5), (11, 5), (12.6, 9), (11, 14), (5, 14), (3.4, 9)], hexc("1A1424"))
    c.poly([(5.6, 6), (10.4, 6), (11.6, 9), (10.4, 13), (5.6, 13), (4.4, 9)], hexc("4A2A6A"))
    c.rect(6.6, 2, 9.4, 5.2, hexc("2A2034"))
    c.path([(6, 9), (8, 7.6), (10, 9.6), (8, 11.6)], 0.7, a(PURPLE_L, 200))
    c.circle(6, 7.4, 0.6, a(WHITE, 180))


# ===================================================================================================== row 2: services and mini-games

def s_cawan(c):
    """Tea bowl with steam."""
    c.ellipse(8, 12, 5.6, 2.4, hexc("4A6A8A"))
    c.ellipse(8, 9.6, 6, 1.8, hexc("E8F0F4"))
    c.poly([(2, 9.6), (14, 9.6), (12, 13), (4, 13)], hexc("E8F0F4"))
    c.ellipse(8, 9.6, 5, 1.2, hexc("8ABA5A"))
    c.path([(4, 11), (6, 12), (8, 11), (10, 12), (12, 11)], 0.5, hexc("4A6A8A"))
    c.path([(6, 7), (5, 5), (6.4, 3.4)], 0.7, a(WHITE, 190))
    c.path([(9.6, 7), (10.6, 5), (9.6, 3)], 0.7, a(WHITE, 190))


def s_lensa(c):
    c.circle(7, 7, 5, hexc("B8D8E8", 200))
    c.ring(7, 7, 4.4, 5.4, GOLD)
    c.line(10.6, 10.6, 14.6, 14.6, 1.8, WOOD)
    c.ellipse(5.4, 5.2, 1.6, 0.8, a(WHITE, 230))
    c.circle(7, 7, 1.0, a(WHITE, 120))


def s_surat(c):
    c.rect(3, 2, 13, 14, PAPER)
    c.rect(3, 2, 13, 3.4, PAPER_D)
    for y in (5, 7, 9):
        c.line(4.4, y, 11.6, y, 0.5, a(ui.INK, 150))
    c.line(4.4, 11, 8, 11, 0.5, a(ui.INK, 150))
    c.circle(10.6, 11.4, 1.8, RED)
    c.ring(10.6, 11.4, 1.2, 1.5, RED_D)


def s_cap(c):
    """Customs seal: square chop with characters."""
    c.rect(2.5, 2.5, 13.5, 13.5, RED)
    c.rect(3.6, 3.6, 12.4, 12.4, hexc("D84A3A"))
    c.line(5, 5.4, 11, 5.4, 0.9, PAPER)
    c.line(8, 5.4, 8, 11, 0.9, PAPER)
    c.line(5, 8, 7, 10.6, 0.8, PAPER)
    c.line(11, 8, 9, 10.6, 0.8, PAPER)
    c.line(5, 11.2, 11, 11.2, 0.8, PAPER)


def s_tungku(c):
    """Small furnace with fire."""
    c.rect(3, 6, 13, 14, STONE_D)
    c.rect(4, 7, 12, 14, STONE)
    c.ellipse(8, 11.4, 3, 2.4, hexc("2A1A10"))
    c.poly([(6, 13), (7, 9.4), (8, 11), (9, 8.6), (10.4, 13)], hexc("FF8A2A"))
    c.poly([(7.2, 13), (8, 10.6), (9, 13)], hexc("FFE07A"))
    c.rect(2.4, 4.6, 13.6, 6.2, IRON_D)
    c.rect(6.4, 1.4, 9.6, 4.6, IRON)


def s_tenun(c):
    """Loom with a shuttle."""
    c.rect(2, 2, 3.2, 14, WOOD)
    c.rect(12.8, 2, 14, 14, WOOD)
    c.rect(2, 2, 14, 3.2, WOOD_L)
    for x in range(4, 13, 1):
        c.line(x, 3.2, x, 12, 0.35, a(hexc("F4D0E0"), 220))
    c.rect(3.2, 8, 12.8, 12, a(hexc("C8507A"), 230))
    c.path([(3.2, 9), (12.8, 9)], 0.4, GOLD)
    c.ellipse(8, 6.4, 3.4, 0.9, WOOD_D)
    c.ellipse(8, 6.4, 2.4, 0.5, WOOD_L)


def s_genderang(c):
    """Dragon-boat drum."""
    c.ellipse(8, 5.4, 6, 2.2, hexc("E8D8B0"))
    c.poly([(2, 5.4), (14, 5.4), (13, 12.6), (3, 12.6)], RED)
    c.ellipse(8, 12.6, 5, 1.8, RED_D)
    for x in (3.4, 5.6, 8, 10.4, 12.6):
        c.circle(x, 6.6, 0.4, GOLD_L)
    c.line(4, 9, 12, 9, 0.6, GOLD)
    c.line(11, 1, 6, 4.6, 0.8, WOOD_L)
    c.circle(6, 4.6, 0.8, RED_L)


def s_penari(c):
    """Whirling dancer, skirt flared."""
    c.circle(8, 3, 1.4, hexc("D8A880"))
    c.poly([(7, 4.4), (9, 4.4), (9.4, 8), (6.6, 8)], hexc("C8343A"))
    c.poly([(6.6, 8), (9.4, 8), (14, 12.4), (8, 13.6), (2, 12.4)], hexc("E8A020"))
    c.path([(2, 12.4), (5, 13.2), (8, 13.6), (11, 13.2), (14, 12.4)], 0.6, hexc("C8343A"))
    c.path([(7, 5), (3, 3.4), (1.4, 5)], 0.6, hexc("D8A880"))
    c.path([(9, 5), (13, 6.4), (14.6, 4.6)], 0.6, hexc("D8A880"))
    c.path([(1.4, 5), (3, 8), (2, 11)], 0.5, a(GOLD_L, 200))


def s_kabin(c):
    """Cable car cabin under the rope."""
    c.line(0, 3, 16, 1.6, 0.8, IRON_D)
    c.circle(8, 2.3, 1.0, IRON)
    c.line(8, 2.3, 8, 5, 0.7, IRON)
    c.rect(3, 5, 13, 13.4, WOOD)
    c.poly([(2.2, 5.6), (13.8, 5.6), (12, 4), (4, 4)], RED)
    c.rect(4.2, 6.6, 7.4, 9.6, hexc("B8D8E8"))
    c.rect(8.6, 6.6, 11.8, 9.6, hexc("B8D8E8"))
    c.rect(3, 11, 13, 12, WOOD_D)


def s_bordes(c):
    """A stair landing with a stone lantern."""
    for i in range(4):
        c.rect(1 + i * 2, 13 - i * 2.4, 15, 14.6 - i * 2.4, shade(STONE, -0.1 * i))
    c.rect(10, 2.6, 13, 4, STONE_D)
    c.rect(10.6, 4, 12.4, 6.4, hexc("FFD27A"))
    c.rect(9.6, 6.4, 13.4, 7.2, STONE_D)
    c.rect(10.8, 7.2, 12.2, 9.6, STONE)


def s_kuil(c):
    """Summit temple with sun halo (Cahaya Budha)."""
    c.ring(8, 6.4, 5.2, 6.4, a(GOLD_L, 200))
    c.ring(8, 6.4, 3.6, 4.2, a(hexc("A8D8F8"), 180))
    c.rect(4.6, 9, 11.4, 14, RED)
    c.poly([(2.6, 9.4), (13.4, 9.4), (11.4, 7.4), (4.6, 7.4)], GOLD_D)
    c.rect(7, 11, 9, 14, GOLD)
    c.path([(0, 14.6), (5, 13.8), (11, 14.6), (16, 13.8)], 1.2, a(WHITE, 170))


def s_topeng(c):
    c.ellipse(8, 7.6, 5.6, 6, hexc("D8C8A8"))
    c.ellipse(5.6, 6.6, 1.8, 1.1, ui.INK)
    c.ellipse(10.4, 6.6, 1.8, 1.1, ui.INK)
    c.path([(3.4, 4), (6, 5), (7.6, 4.6)], 0.6, BLOOD)
    c.path([(12.6, 4), (10, 5), (8.4, 4.6)], 0.6, BLOOD)
    c.path([(5.4, 11), (8, 12.2), (10.6, 11)], 0.6, BLOOD)
    c.path([(2.4, 7.6), (0.8, 9), (1.6, 11)], 0.5, PURPLE)
    c.path([(13.6, 7.6), (15.2, 9), (14.4, 11)], 0.5, PURPLE)


def s_peta(c):
    c.poly([(1.5, 3), (5.5, 2), (10.5, 3.4), (14.5, 2.4), (14.5, 13), (10.5, 14), (5.5, 12.6), (1.5, 13.6)], hexc("5A4A3A"))
    c.poly([(2.4, 3.8), (5.5, 3), (10.5, 4.4), (13.6, 3.4), (13.6, 12.2), (10.5, 13), (5.5, 11.6), (2.4, 12.6)], hexc("C8B890"))
    c.path([(3.4, 11), (6, 8.4), (8.4, 9.6), (11.4, 6)], 0.6, a(BLOOD, 220))
    c.line(10.4, 4.8, 12.4, 6.8, 0.6, BLOOD)
    c.line(12.4, 4.8, 10.4, 6.8, 0.6, BLOOD)
    c.line(5.5, 3, 5.5, 11.6, 0.3, a(ui.INK, 120))
    c.line(10.5, 4.4, 10.5, 13, 0.3, a(ui.INK, 120))


def s_pil_hitam(c):
    c.circle(8, 8.4, 4.6, hexc("1A1418"))
    c.ring(8, 8.4, 4.0, 4.6, hexc("6A1A2A"))
    c.path([(5.4, 7), (7, 9.6), (9.4, 6.4), (10.6, 9.4)], 0.6, BLOOD_L)
    c.circle(6.4, 6.4, 0.9, a(WHITE, 140))


def s_cakar(c):
    c.ellipse(8, 10.4, 3.6, 3.0, hexc("4A2A2A"))
    for x, y in ((3.6, 6.4), (6.4, 4), (9.6, 4), (12.4, 6.4)):
        c.ellipse(x, y, 1.4, 1.8, hexc("4A2A2A"))
        c.poly([(x - 0.5, y - 1.6), (x + 0.5, y - 1.6), (x, y - 3)], BONE)


def s_karung(c):
    c.poly([(4, 6), (12, 6), (13.6, 11), (12, 14.4), (4, 14.4), (2.4, 11)], hexc("8A6A4A"))
    c.poly([(6, 6), (10, 6), (9, 3.4), (7, 3.4)], hexc("A8885A"))
    c.line(5.6, 6, 10.4, 6, 0.8, WOOD_D)
    c.circle(11.6, 12, 1.4, GOLD)
    c.circle(11.6, 12, 0.6, GOLD_D)
    c.path([(5, 9), (7, 12), (9, 10)], 0.4, a(WOOD_D, 160))


# ===================================================================================================== row 3: demonic city and workshops

def d_koin_darah(c):
    c.circle(8, 8, 6.2, BLOOD_D)
    c.circle(8, 8, 5.4, BLOOD)
    c.rect(6.6, 6.6, 9.4, 9.4, BLOOD_D)
    c.ring(8, 8, 4.4, 5.0, a(GOLD, 220))
    c.circle(6, 5.6, 0.8, a(BLOOD_L, 200))


def d_arena(c):
    c.line(2.4, 2.4, 13.6, 13.6, 1.3, IRON_L)
    c.line(13.6, 2.4, 2.4, 13.6, 1.3, IRON_L)
    c.line(2, 12, 4, 14, 1.6, BLOOD)
    c.line(14, 12, 12, 14, 1.6, BLOOD)
    c.line(3.4, 10.6, 5.4, 12.6, 0.9, GOLD)
    c.line(12.6, 10.6, 10.6, 12.6, 0.9, GOLD)
    c.circle(8, 8, 1.4, BLOOD_L)


def d_tangga(c):
    c.line(4, 15, 5.4, 1, 1.0, WOOD)
    c.line(12, 15, 10.6, 1, 1.0, WOOD)
    for i, y in enumerate((3, 5.6, 8.2, 10.8, 13.4)):
        c.line(4.6, y, 11.4, y, 0.8, BLOOD if i == 0 else WOOD_L)
    c.circle(8, 3, 0.8, BLOOD_L)


def d_rantai(c):
    c.ellipse(4, 8, 2.8, 1.8, IRON)
    c.ellipse(4, 8, 1.6, 0.8, (0, 0, 0, 0))
    c.ring(4, 8, 1.4, 2.4, IRON_L)
    c.ring(12, 8, 1.4, 2.4, IRON_L)
    c.line(6.6, 8, 7.4, 6, 0.8, IRON_L)
    c.line(9.4, 8, 8.6, 10, 0.8, IRON_L)
    for x, y in ((8, 3), (8.6, 13), (6, 12)):
        c.circle(x, y, 0.5, GOLD_L)


def d_sel(c):
    c.rect(2, 2, 14, 14, IRON_D)
    c.rect(3, 3, 13, 13, hexc("2A2228"))
    for x in (5, 8, 11):
        c.line(x, 2.4, x, 13.6, 0.9, IRON_L)
    c.line(2.4, 5.6, 13.6, 5.6, 0.8, IRON)
    c.rect(10, 9, 13.4, 11.6, GOLD_D)
    c.circle(11.7, 10, 0.5, ui.INK)


def d_gulungan(c):
    c.rect(3, 4, 13, 12, hexc("3A2A2A"))
    c.rect(4, 5, 12, 11, hexc("8A6A5A"))
    c.rect(1.6, 3, 4, 13, BLOOD_D)
    c.rect(12, 3, 14.4, 13, BLOOD_D)
    c.path([(5, 7), (7, 6.4), (9, 7.6), (11, 7)], 0.5, BLOOD_L)
    c.path([(5, 9.4), (8, 9), (11, 9.6)], 0.5, BLOOD_L)
    c.circle(8, 8, 1.6, a(PURPLE, 160))


def d_lantai(c):
    """A pagoda floor with a lock."""
    c.poly([(1, 6), (15, 6), (13, 3.4), (3, 3.4)], BLOOD_D)
    c.rect(3, 6, 13, 13.6, hexc("2A1A1A"))
    c.rect(4, 7, 12, 13.6, hexc("4A2A2A"))
    c.rect(6, 9.6, 10, 13, GOLD_D)
    c.ring(8, 9.6, 0.9, 1.5, GOLD)
    c.circle(8, 11.2, 0.5, ui.INK)


def d_jubah(c):
    c.poly([(5.4, 2), (10.6, 2), (14, 5), (12.6, 7), (11.6, 6.4), (12.4, 14.4), (3.6, 14.4), (4.4, 6.4), (3.4, 7), (2, 5)], hexc("C8507A"))
    c.poly([(6.4, 2), (8, 6), (9.6, 2)], hexc("F4E8D8"))
    c.line(8, 6, 8, 14.4, 0.5, a(hexc("7A2A4A"), 200))
    c.rect(4.4, 8.4, 11.6, 9.6, GOLD)
    c.path([(5, 11.6), (6.4, 10.8), (7.4, 12)], 0.5, a(GOLD_L, 220))


def d_kuas(c):
    c.line(3, 13, 11.6, 4.4, 1.2, WOOD_L)
    c.line(11.6, 4.4, 13.6, 2.4, 1.6, hexc("2A2A2A"))
    c.circle(13.8, 2.2, 0.8, RED)
    c.ellipse(5, 13, 3.4, 1.6, a(RED, 200))
    c.ellipse(3, 12, 1.2, 0.8, a(SAND_D, 220))


def d_bait(c):
    c.rect(2.4, 1.6, 6.6, 14.4, PAPER)
    c.rect(9.4, 1.6, 13.6, 14.4, PAPER)
    for x in (4.5, 11.5):
        for y in (3.6, 6.4, 9.2, 12):
            c.line(x - 1, y, x + 1, y + 0.4, 0.7, ui.INK)
    c.rect(2, 1, 7, 2, RED)
    c.rect(9, 1, 14, 2, RED)


def d_tali(c):
    pts = []
    for k in range(17):
        t = k / 16.0
        pts.append((1 + 14 * t, 3 + 9 * math.sin(t * math.pi) * 0.9))
    c.path(pts, 1.2, hexc("A8885A"))
    for k in range(1, 16, 2):
        x, y = pts[k]
        c.line(x - 0.5, y - 0.6, x + 0.5, y + 0.6, 0.4, hexc("6A4A2A"))
    c.circle(1, 3, 1.2, WOOD_D)
    c.circle(15, 3, 1.2, WOOD_D)


def d_katrol(c):
    c.circle(8, 7, 5.2, WOOD)
    c.ring(8, 7, 4.0, 5.2, WOOD_D)
    for k in range(6):
        t = k * math.pi / 3
        c.line(8, 7, 8 + math.cos(t) * 4, 7 + math.sin(t) * 4, 0.6, WOOD_L)
    c.circle(8, 7, 1.0, IRON)
    c.line(2.8, 7, 2.8, 15, 0.7, hexc("A8885A"))
    c.line(13.2, 7, 13.2, 12, 0.7, hexc("A8885A"))
    c.rect(11.6, 12, 14.8, 15, STONE_D)


def d_galangan(c):
    c.poly([(2, 9), (14, 9), (12, 13), (4, 13)], WOOD)
    for x in (4, 6.4, 8.8, 11.2):
        c.line(x, 9, x + 0.6, 4, 0.5, WOOD_L)
    c.line(3, 4, 13, 4, 0.5, WOOD_L)
    c.line(9, 2, 14, 7, 1.0, IRON_L)
    c.rect(12.6, 5.6, 15.6, 8.4, IRON)
    c.rect(1, 13, 15, 14.4, STONE_D)


def d_layar(c):
    c.line(8, 1, 8, 15, 0.8, WOOD_D)
    c.poly([(8.4, 1.6), (14.6, 3), (13.6, 12.4), (8.4, 12.4)], hexc("D87A4A"))
    for y in (4, 6.6, 9.2):
        c.line(8.4, y, 14, y + 0.4, 0.5, hexc("7A3018"))
    c.path([(9.4, 7.6), (11.4, 6.2), (13, 7.8)], 0.5, GOLD_L)
    c.rect(4, 13, 12, 14.4, WOOD)


def d_sewa(c):
    """Rental tag with a key."""
    c.poly([(2, 5), (9, 5), (11.6, 8), (9, 11), (2, 11)], PAPER)
    c.circle(9.4, 8, 0.8, PAPER_D)
    c.line(3.6, 7, 7, 7, 0.5, a(ui.INK, 160))
    c.line(3.6, 9, 6, 9, 0.5, a(ui.INK, 160))
    c.ring(12.6, 4, 1.0, 1.8, GOLD)
    c.line(12.6, 5.8, 12.6, 14, 1.0, GOLD)
    c.line(12.6, 11.4, 14.4, 11.4, 0.8, GOLD)
    c.line(12.6, 13.2, 14, 13.2, 0.8, GOLD)


def d_orang(c):
    c.circle(8, 4.4, 2.4, hexc("D8A880"))
    c.ellipse(8, 3.4, 2.6, 1.4, hexc("2A1A10"), -math.pi, 0)
    c.poly([(4, 15), (4.8, 9), (8, 7.4), (11.2, 9), (12, 15)], JADE)
    c.poly([(7, 7.6), (8, 10), (9, 7.6)], PAPER)


ICONS = [
    # row 0: themes and meters (TemaUi.I_PELABUHAN .. I_UNTA)
    e_pelabuhan, e_oasis, e_air, e_gunung, e_iblis, e_ibu_kota, m_infami, m_gentar,
    m_suasana, m_badai, m_kontrak, m_asal, m_terbaik, m_jenuh, m_kapal, m_unta,
    # row 1: the 16 goods of DagangItems in registration order
    g_ikan, g_mutiara, g_rempah, g_porselen, g_kurma, g_permadani, g_giok, g_kaca,
    g_sutra, g_teh, g_arak, g_herba_kering, g_dupa, g_teh_awan, g_lingzhi, g_racun,
    # row 2: services and mini-games
    s_cawan, s_lensa, s_surat, s_cap, s_tungku, s_tenun, s_genderang, s_penari,
    s_kabin, s_bordes, s_kuil, s_topeng, s_peta, s_pil_hitam, s_cakar, s_karung,
    # row 3
    d_koin_darah, d_arena, d_tangga, d_rantai, d_sel, d_gulungan, d_lantai, d_jubah,
    d_kuas, d_bait, d_tali, d_katrol, d_galangan, d_layar, d_sewa, d_orang,
]


def x_restu(c):
    """Restu: a lotus inside a halo (blessing active)."""
    c.ring(8, 8, 5.8, 6.8, a(GOLD_L, 220))
    for k in range(5):
        t = -math.pi / 2 + (k - 2) * 0.55
        c.ellipse(8 + math.cos(t) * 2.6, 9 + math.sin(t) * 2.6, 1.2, 2.2, SILK)
    c.ellipse(8, 11.4, 4.4, 1.0, GREEN)
    c.circle(8, 8.6, 1.0, GOLD_L)


def x_air_suci(c):
    c.poly([(6, 4), (10, 4), (10, 6.4), (12.6, 9), (12, 14), (4, 14), (3.4, 9), (6, 6.4)], hexc("B8D8E8", 220))
    c.poly([(4.2, 10), (11.8, 10), (11.6, 13.6), (4.4, 13.6)], SEA_L)
    c.rect(5.6, 2.6, 10.4, 4.2, WOOD)
    c.rect(5.4, 10.6, 10.6, 12.4, RED)
    c.circle(8, 11.5, 0.6, GOLD_L)
    c.ellipse(5.4, 7.6, 0.5, 1.4, a(WHITE, 200))


def x_bingkai_mural(c):
    c.rect(2, 2, 14, 14, SAND_D)
    c.rect(3.4, 3.4, 12.6, 12.6, SAND_L)
    figure(c, 8, 12, 0.8, OCHRE, arms=3)
    c.circle(11, 5.6, 1.0, OCHRE_L)


def x_suar(c):
    c.poly([(6, 14.6), (10, 14.6), (9.2, 5), (6.8, 5)], hexc("E8E0D0"))
    for y in (7.4, 10, 12.6):
        c.rect(6.2, y, 9.8, y + 1.0, RED)
    c.rect(6, 3.2, 10, 5, IRON_D)
    c.circle(8, 4, 1.0, hexc("FFE07A"))
    c.poly([(9, 3.4), (16, 0.6), (16, 6.4)], a(hexc("FFE07A"), 120))
    c.poly([(7, 3.4), (0, 0.6), (0, 6.4)], a(hexc("FFE07A"), 80))
    c.poly([(5.4, 3.2), (10.6, 3.2), (8, 1.4)], RED_D)


def x_ombak(c):
    c.path([(1, 12), (3, 7), (6.4, 4.4), (10, 4.4), (12, 6.6), (10.4, 8), (8.4, 7)], 1.8, SEA)
    c.path([(6.4, 4.4), (9, 3.4), (11.6, 4.6)], 0.8, SEA_L)
    c.path([(0, 14), (4, 12.6), (8, 14), (12, 12.6), (16, 14)], 1.2, SEA_D)
    for x, y in ((11, 9), (13, 7.6), (12.6, 10.4)):
        c.circle(x, y, 0.6, a(WHITE, 220))


def x_macet(c):
    c.circle(8, 8, 5.2, IRON)
    for k in range(8):
        t = k * math.pi / 4
        c.rect(8 + math.cos(t) * 5 - 1, 8 + math.sin(t) * 5 - 1, 8 + math.cos(t) * 5 + 1, 8 + math.sin(t) * 5 + 1, IRON)
    c.circle(8, 8, 2.0, IRON_D)
    c.path([(10, 2), (8.4, 6), (10.4, 8.6), (8, 14)], 1.0, ui.RED)


def x_malam(c):
    c.circle(8, 8, 5.6, hexc("F4E8C0"))
    c.circle(10.4, 6.4, 5.0, (0, 0, 0, 0))
    c.circle(10.4, 6.4, 5.0, hexc("2A3A5C"))
    c.circle(13, 12.6, 0.6, hexc("F4E8C0"))
    c.circle(3, 3, 0.5, hexc("F4E8C0"))
    c.circle(13.4, 2.6, 0.4, hexc("F4E8C0"))


EXTRA = [g_minyak, x_restu, x_air_suci, x_bingkai_mural, x_suar, x_ombak, x_macet, x_malam]


def build_icons():
    img = Image(256, 80, (0, 0, 0, 0))
    for slot, fn in enumerate(ICONS + EXTRA):
        c = new()
        fn(c)
        put(img, c, (slot % 16) * 16, (slot // 16) * 16)
    return img


# ===================================================================================================== murals

def sandstone(img, ox, oy, w, h, seed):
    for y in range(h):
        for x in range(w):
            n = fbm(x / 9.0, y / 9.0, seed, None, 4)
            n2 = hnoise(x / 2.2, y / 2.2, seed + 7)
            strata = 0.5 + 0.5 * math.sin((y + n * 9) / 3.4)
            base = mix(hexc("C89A62"), hexc("E4C08A"), n * 0.8 + strata * 0.25)
            base = shade(base, (n2 - 0.5) * 0.12)
            img.set(ox + x, oy + y, base)
    # edge darkening (the niche)
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            if d < 5:
                img.blend(ox + x, oy + y, (60, 34, 16, int(150 * (1 - d / 5.0) ** 1.6)))


def cracks(img, ox, oy, w, h, seed, n=3):
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.uniform(4, w - 4), rnd.uniform(4, h - 4)
        ang = rnd.uniform(0, math.pi * 2)
        for _ in range(rnd.randint(10, 22)):
            img.blend(ox + int(x), oy + int(y), (70, 40, 20, 150))
            ang += rnd.uniform(-0.7, 0.7)
            x += math.cos(ang)
            y += math.sin(ang)
            if not (2 <= x < w - 2 and 2 <= y < h - 2):
                break


def flaking(img, ox, oy, w, h, seed):
    """Pigment flaked off: lighten random blotches back towards bare stone."""
    for y in range(h):
        for x in range(w):
            if fbm(x / 4.0, y / 4.0, seed + 99, None, 2) > 0.72:
                p = img.get(ox + x, oy + y)
                img.set(ox + x, oy + y, mix(p, hexc("E4C08A"), 0.55))


OCHRE = hexc("A8441C", 235)
OCHRE_L = hexc("C8703A", 225)
CHAR = hexc("3A2A1E", 230)
CHALK = hexc("F4EAD8", 225)
TEAL = hexc("3E8A86", 220)


def figure(c, x, y, s, col, arms=0.0, walk=0.0):
    """A stick-and-blob cave figure standing at feet (x, y) of height 10*s."""
    c.circle(x, y - 9 * s, 1.3 * s, col)
    c.line(x, y - 7.8 * s, x, y - 3.6 * s, 1.2 * s, col)
    c.line(x, y - 3.6 * s, x - 1.6 * s - walk, y, 1.0 * s, col)
    c.line(x, y - 3.6 * s, x + 1.6 * s + walk, y, 1.0 * s, col)
    c.line(x, y - 6.8 * s, x - 2.6 * s, y - 6.8 * s - arms * s, 0.9 * s, col)
    c.line(x, y - 6.8 * s, x + 2.6 * s, y - 6.8 * s - arms * s, 0.9 * s, col)


def camel(c, x, y, s, col):
    c.ellipse(x, y - 4 * s, 4 * s, 1.8 * s, col)
    c.circle(x - 1.6 * s, y - 5.6 * s, 1.4 * s, col)
    c.circle(x + 1.4 * s, y - 5.6 * s, 1.4 * s, col)
    c.path([(x + 3.6 * s, y - 4.4 * s), (x + 5 * s, y - 7 * s), (x + 6 * s, y - 7.2 * s)], 1.0 * s, col)
    for dx in (-2.6, -1.2, 1.4, 2.8):
        c.line(x + dx * s, y - 3 * s, x + dx * s, y, 0.7 * s, col)


def crescent(cx, cy, r, dx, dy, k):
    """A crescent polygon: the outer circle minus a circle offset by (dx, dy) with radius r * (1 - k * 0.3)."""
    ri = r * (1.0 - k * 0.3)
    pts = []
    for i in range(41):
        t = i / 40.0 * math.pi * 2
        x, y = cx + math.cos(t) * r, cy + math.sin(t) * r
        if math.hypot(x - (cx + dx), y - (cy + dy)) >= ri:
            pts.append((x, y))
    # walk back along the inner circle between the two outer end points
    if len(pts) < 3:
        return pts
    ax = math.atan2(pts[-1][1] - (cy + dy), pts[-1][0] - (cx + dx))
    bx = math.atan2(pts[0][1] - (cy + dy), pts[0][0] - (cx + dx))
    # order points so the outer arc is continuous: rotate list at the largest gap
    gaps = [math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]) for i in range(len(pts))]
    g = max(range(len(pts)), key=lambda i: gaps[i])
    pts = pts[g:] + pts[:g]
    ax = math.atan2(pts[-1][1] - (cy + dy), pts[-1][0] - (cx + dx))
    bx = math.atan2(pts[0][1] - (cy + dy), pts[0][0] - (cx + dx))
    span = (bx - ax) % (math.pi * 2)
    if span > math.pi:
        span -= math.pi * 2
    inner = []
    for i in range(1, 24):
        t = ax + span * i / 24.0
        inner.append((cx + dx + math.cos(t) * ri, cy + dy + math.sin(t) * ri))
    return pts + inner


def mural_scene(n):
    """One 64x64 mural panel (pigment layer only, drawn over sandstone)."""
    c = Canvas(64, 64, 3)
    if n == 0:  # Mata Air Pertama: a falling star opens the spring
        c.line(44, 6, 30, 30, 2.0, CHALK)
        c.circle(44, 6, 3.4, CHALK)
        for k in range(8):
            t = k * math.pi / 4
            c.line(44, 6, 44 + math.cos(t) * 6, 6 + math.sin(t) * 6, 0.8, CHALK)
        c.ellipse(30, 44, 16, 5, TEAL)
        c.ellipse(30, 43, 11, 3, a(CHALK, 160))
        for x in (10, 50):
            figure(c, x, 56, 1.1, OCHRE, arms=4)
    elif n == 1:  # Kafilah Pertama: camels under the guiding star
        for k, x in enumerate((12, 28, 44)):
            camel(c, x, 52 - k, 1.4, OCHRE if k != 1 else OCHRE_L)
        c.path([(14, 46), (26, 45), (42, 44)], 0.6, CHAR)
        figure(c, 56, 52, 1.0, CHAR, walk=0.6)
        c.circle(48, 10, 2.4, CHALK)
        for k in range(4):
            t = k * math.pi / 2
            c.line(48, 10, 48 + math.cos(t) * 5, 10 + math.sin(t) * 5, 0.8, CHALK)
        c.path([(0, 58), (16, 55), (32, 58), (48, 55), (64, 58)], 1.0, OCHRE_L)
    elif n == 2:  # Danau Bulan Sabit: the crescent lake under the crescent moon
        c.poly(crescent(30, 42, 18, 8, -6, 0.25), TEAL)
        c.poly(crescent(28, 44, 13, 7, -6, 0.2), a(hexc("6AB0A8"), 200))
        c.poly(crescent(47, 12, 7, 3.6, -2.4, 0.5), CHALK)
        for x, y in ((12, 52), (54, 54)):
            c.path([(x, y), (x + 1, y - 12)], 1.0, CHAR)
            for dx in (-4, 4, -3, 3):
                c.path([(x + 1, y - 12), (x + 1 + dx, y - 12 + abs(dx) * 0.6)], 0.9, CHAR)
        figure(c, 40, 58, 0.8, OCHRE, arms=2)
    elif n == 3:  # Kota Terkubur: towers swallowed by a dune
        for x, h in ((14, 26), (24, 34), (36, 30), (48, 22)):
            c.rect(x - 4, 60 - h, x + 4, 60, OCHRE)
            c.poly([(x - 5, 60 - h), (x + 5, 60 - h), (x, 54 - h)], OCHRE_L)
            c.rect(x - 1, 50 - h + 14, x + 1, 50 - h + 18, CHAR)
        c.poly([(0, 64), (0, 44), (18, 40), (40, 46), (64, 38), (64, 64)], a(hexc("E4C08A"), 255))
        c.path([(0, 44), (18, 40), (40, 46), (64, 38)], 1.0, CHAR)
        c.path([(4, 20), (12, 16), (20, 22), (30, 14)], 0.8, a(CHAR, 160))
    elif n == 4:  # Raja Kalajengking
        c.ellipse(32, 40, 12, 7, CHAR)
        for side in (-1, 1):
            for k in range(3):
                c.path([(32 + side * 8, 40 + k * 2), (32 + side * 16, 44 + k * 3), (32 + side * 18, 50 + k * 3)], 1.0, CHAR)
            c.path([(32 + side * 10, 36), (32 + side * 18, 30), (32 + side * 20, 24)], 1.6, CHAR)
            c.poly([(32 + side * 20, 24), (32 + side * 24, 20), (32 + side * 18, 20)], CHAR)
        c.path([(32, 33), (30, 22), (34, 12), (42, 10)], 2.0, OCHRE)
        c.poly([(42, 10), (47, 13), (43, 5)], OCHRE)
        c.circle(28, 38, 1.2, CHALK)
        c.circle(36, 38, 1.2, CHALK)
        figure(c, 8, 60, 0.9, OCHRE_L, arms=-2)
    elif n == 5:  # Penari Angin: the whirling dancer and the wind
        c.circle(32, 14, 3, OCHRE)
        c.poly([(29, 17), (35, 17), (36, 30), (28, 30)], OCHRE)
        c.poly([(28, 30), (36, 30), (50, 46), (32, 50), (14, 46)], OCHRE_L)
        c.line(32, 20, 16, 14, 1.2, OCHRE)
        c.line(32, 20, 48, 26, 1.2, OCHRE)
        for r in (20, 26):
            pts = [(32 + math.cos(t / 10.0 * math.pi) * r, 36 + math.sin(t / 10.0 * math.pi) * r * 0.5) for t in range(-2, 13)]
            c.path(pts, 0.8, CHALK)
        for x in (6, 58):
            c.circle(x, 56, 2, CHAR)
    elif n in (6, 12):  # Pendeta yang Diusir (12 = second frame: he has turned and walks out)
        c.rect(40, 14, 58, 60, OCHRE)
        c.rect(44, 28, 54, 60, CHAR)
        c.poly([(38, 14), (60, 14), (49, 6)], OCHRE_L)
        c.ellipse(49, 22, 3, 3, TEAL)
        for x in (44, 50, 56):
            figure(c, x - 2, 60, 0.8, CHALK, arms=3)
        if n == 6:
            figure(c, 26, 60, 1.3, OCHRE, arms=-3)
            c.line(26, 44, 34, 38, 1.0, TEAL)
        else:
            figure(c, 14, 60, 1.3, OCHRE, arms=-1, walk=1.4)
            c.path([(18, 60), (26, 61), (34, 60)], 0.6, a(CHAR, 160))
            c.line(14, 44, 10, 52, 1.0, TEAL)
        c.path([(0, 62), (64, 62)], 1.0, CHAR)
    elif n == 7:  # Badai Seribu Hari: the sand wall over a kneeling caravan
        c.poly([(0, 6), (64, 2), (64, 34), (0, 40)], a(OCHRE_L, 190))
        for y in range(8, 36, 5):
            c.path([(0, y + 2), (20, y - 1), (40, y + 2), (64, y - 2)], 0.7, a(CHAR, 150))
        for x in (14, 32, 50):
            c.ellipse(x, 54, 6, 2.4, OCHRE)
            c.circle(x - 2, 51, 1.8, OCHRE)
            c.circle(x + 2, 51, 1.8, OCHRE)
            c.path([(x + 5, 54), (x + 8, 56)], 1.0, OCHRE)
        c.path([(0, 58), (64, 58)], 1.0, CHAR)
    elif n == 8:  # Giok Barat: traders exchange jade under an awning
        c.poly([(10, 20), (54, 20), (58, 28), (6, 28)], OCHRE)
        for x in range(8, 58, 6):
            c.line(x, 20, x + 2, 28, 0.8, CHALK)
        figure(c, 18, 58, 1.4, CHAR, arms=2)
        figure(c, 46, 58, 1.4, OCHRE, arms=2)
        c.poly([(28, 42), (32, 38), (36, 42), (32, 46)], TEAL)
        c.circle(32, 42, 1.2, CHALK)
        c.rect(24, 50, 40, 58, OCHRE_L)
    elif n == 9:  # Gerbang Giok: the jade gate with guardians
        c.rect(12, 18, 20, 60, OCHRE)
        c.rect(44, 18, 52, 60, OCHRE)
        c.poly([(6, 18), (58, 18), (54, 10), (10, 10)], TEAL)
        c.rect(10, 22, 54, 26, OCHRE_L)
        c.circle(32, 14, 3, CHALK)
        for x in (6, 58):
            c.ellipse(x, 56, 4, 3, CHAR)
            c.circle(x, 50, 3, CHAR)
        c.path([(22, 60), (32, 40), (42, 60)], 0.8, a(CHALK, 160))
    elif n == 10:  # Burung Pasir: the sand phoenix rising
        c.ellipse(32, 30, 6, 10, OCHRE)
        c.circle(32, 18, 3, OCHRE)
        c.poly([(32, 15), (36, 13), (33, 17)], CHALK)
        for side in (-1, 1):
            pts = [(32 + side * 4, 26)]
            for k in range(1, 6):
                pts.append((32 + side * (4 + k * 4.4), 26 - k * 3 + (k * k) * 0.3))
            c.path(pts, 2.4, OCHRE_L)
            for k in range(1, 6):
                x, y = pts[k]
                c.line(x, y, x - side * 1.4, y + 6, 1.0, OCHRE)
        for k in range(5):
            c.path([(30 + k, 40), (26 + k * 3, 52), (28 + k * 2, 62)], 1.0, a(OCHRE_L, 200))
        c.circle(32, 30, 2, CHALK)
    elif n == 11:  # Mata Air Kembali: the water marker rises, people dance
        c.rect(28, 12, 36, 58, CHAR)
        for k, y in enumerate((18, 26, 34, 42, 50)):
            c.line(24, y, 40, y, 1.0, TEAL if k < 3 else CHALK)
        c.poly([(32, 8), (36, 12), (28, 12)], TEAL)
        for x in (10, 18, 46, 54):
            figure(c, x, 60, 1.0, OCHRE, arms=5)
        c.ellipse(32, 60, 20, 2.4, TEAL)
    elif n == 13:  # empty niche, a faint grid the painter sketched
        for x in range(12, 56, 8):
            c.line(x, 10, x, 54, 0.4, a(CHAR, 50))
        for y in range(10, 56, 8):
            c.line(12, y, 52, y, 0.4, a(CHAR, 50))
        c.ring(32, 32, 14, 15, a(OCHRE, 90))
    else:  # weathered blanks: faded hand prints
        rnd = random.Random(n)
        for _ in range(4):
            x, y = rnd.uniform(14, 50), rnd.uniform(14, 50)
            col = a(OCHRE, 110)
            c.ellipse(x, y, 3.4, 4, col)
            for k in range(5):
                t = -math.pi / 2 + (k - 2) * 0.42
                c.line(x, y, x + math.cos(t) * 7, y + math.sin(t) * 7, 1.2, col)
    return c


def build_murals():
    img = Image(256, 256, (0, 0, 0, 0))
    for n in range(16):
        ox, oy = (n % 4) * 64, (n // 4) * 64
        sandstone(img, ox, oy, 64, 64, 400 + n)
        layer = mural_scene(n).resolve()
        paste(img, layer, ox, oy, None)
        flaking(img, ox, oy, 64, 64, 900 + n)
        cracks(img, ox, oy, 64, 64, 700 + n, 2 + n % 3)
    return img


# ===================================================================================================== rhythm atlas

def arrow(c, ox, oy, d, lit):
    col = [hexc("4AB8E8"), hexc("E8C04A"), hexc("E86A4A"), hexc("6AD07A")][d]
    rim = shade(col, -0.45)
    cx, cy = ox + 8, oy + 8
    pts = [(0, -6.4), (6.2, 0), (2.6, 0), (2.6, 6), (-2.6, 6), (-2.6, 0), (-6.2, 0)]
    ang = d * math.pi / 2
    P = [(cx + x * math.cos(ang) - y * math.sin(ang), cy + x * math.sin(ang) + y * math.cos(ang)) for x, y in pts]
    if lit:
        c.circle(cx, cy, 7.8, a(WHITE, 90))
    c.poly(P, rim)
    inner = [(cx + (x * 0.72) * math.cos(ang) - (y * 0.72 - 0.4) * math.sin(ang),
              cy + (x * 0.72) * math.sin(ang) + (y * 0.72 - 0.4) * math.cos(ang)) for x, y in pts]
    c.poly(inner, shade(col, 0.35) if lit else col)
    c.circle(cx + math.sin(ang) * 2, cy - math.cos(ang) * 2, 1.0, a(WHITE, 200))


def build_irama():
    img = Image(128, 96, (0, 0, 0, 0))
    for lit in (0, 1):
        for d in range(4):
            c = new()
            arrow(c, 0, 0, d, lit)
            put(img, c, (lit * 4 + d) * 16, 0, lit == 0)
    # hit ring
    c = Canvas(32, 32, 4)
    c.ring(16, 16, 12, 14.6, GOLD)
    c.ring(16, 16, 10.4, 11.4, a(GOLD_L, 200))
    for k in range(8):
        t = k * math.pi / 4
        c.line(16 + math.cos(t) * 12, 16 + math.sin(t) * 12, 16 + math.cos(t) * 15.4, 16 + math.sin(t) * 15.4, 1.0, GOLD_D)
    put(img, c, 0, 16, False)
    # drum skin
    c = Canvas(32, 32, 4)
    c.circle(16, 16, 14.6, RED_D)
    c.circle(16, 16, 13, hexc("E8D8B0"))
    c.ring(16, 16, 10, 10.6, a(RED, 150))
    for k in range(12):
        t = k * math.pi / 6
        c.circle(16 + math.cos(t) * 13.8, 16 + math.sin(t) * 13.8, 0.8, GOLD_L)
    c.path([(10, 14), (13, 11), (17, 12), (21, 10), (22, 15), (18, 19), (12, 19)], 1.0, a(RED, 200))
    c.circle(19, 13, 1.0, a(RED_D, 220))
    put(img, c, 32, 16, False)
    # loom
    c = Canvas(32, 32, 4)
    c.rect(2, 2, 5, 30, WOOD)
    c.rect(27, 2, 30, 30, WOOD)
    c.rect(2, 2, 30, 5, WOOD_L)
    c.rect(2, 27, 30, 30, WOOD_D)
    for x in range(6, 27):
        c.line(x + 0.3, 5, x + 0.3, 27, 0.32, a(hexc("F4D0E0"), 230))
    for k, y in enumerate(range(17, 27, 2)):
        c.rect(5, y, 27, y + 2, a([hexc("C8507A"), hexc("E8A0B8"), hexc("8A3A6A")][k % 3], 235))
    c.line(5, 16, 27, 16, 0.8, GOLD)
    put(img, c, 64, 16, False)
    # shuttle and drumstick
    c = new()
    c.ellipse(8, 8, 7, 2.6, WOOD_L)
    c.ellipse(8, 8, 4.4, 1.2, WOOD_D)
    c.line(4, 8, 12, 8, 0.6, hexc("E8A0B8"))
    put(img, c, 96, 16)
    c = new()
    c.line(2, 14, 12.6, 3.4, 1.4, WOOD_L)
    c.circle(13, 3, 2.2, RED)
    put(img, c, 96, 32)
    # dancer frames 32x48 (whirl: skirt flare and arm angle per frame)
    for f in range(4):
        c = Canvas(32, 48, 3)
        sw = math.sin(f * math.pi / 2)
        cw = math.cos(f * math.pi / 2)
        c.circle(16, 7, 3, hexc("D8A880"))
        c.ellipse(16, 5.6, 3.4, 2.0, hexc("2A1A10"), -math.pi, 0)
        c.poly([(13.4, 10), (18.6, 10), (19, 20), (13, 20)], hexc("C8343A"))
        flare = 12 + 3 * abs(cw)
        c.poly([(13, 20), (19, 20), (16 + flare, 38 + 2 * sw), (16, 41), (16 - flare, 38 - 2 * sw)], hexc("E8A020"))
        c.path([(16 - flare, 38 - 2 * sw), (16, 41), (16 + flare, 38 + 2 * sw)], 1.0, hexc("C8343A"))
        for k in range(-2, 3):
            c.line(16 + k * 2, 22, 16 + k * flare / 2.2, 38, 0.4, a(hexc("A86A10"), 200))
        ay = 6 * sw
        c.path([(14, 12), (8, 10 - ay), (3, 12 - ay * 1.4)], 1.0, hexc("D8A880"))
        c.path([(18, 12), (24, 14 + ay), (29, 12 + ay * 1.4)], 1.0, hexc("D8A880"))
        c.path([(3, 12 - ay * 1.4), (6, 20), (4, 28)], 0.8, a(GOLD_L, 200))
        c.path([(29, 12 + ay * 1.4), (26, 22), (28, 30)], 0.8, a(GOLD_L, 200))
        c.line(15, 41, 14, 46, 1.0, hexc("D8A880"))
        c.line(17, 41, 18, 46, 1.0, hexc("D8A880"))
        put(img, c, f * 32, 48, False)
    return img


# ===================================================================================================== robe on a stand

def robe_shape(c, col):
    c.poly([(11, 6), (21, 6), (28, 12), (27, 26), (24, 26), (23, 16), (24, 60), (8, 60), (9, 16), (8, 26), (5, 26), (4, 12)], col)


def build_jubah():
    img = Image(256, 64, (0, 0, 0, 0))
    # 0: stand
    c = Canvas(32, 64, 3)
    c.line(16, 4, 16, 62, 1.6, WOOD)
    c.line(4, 8, 28, 8, 1.4, WOOD_L)
    c.circle(16, 4, 1.6, GOLD)
    c.rect(8, 60, 24, 63, WOOD_D)
    put(img, c, 0, 0, False)
    # 32: robe body, light grey for tinting
    c = Canvas(32, 64, 3)
    robe_shape(c, hexc("F0F0F0"))
    put(img, c, 32, 0, True)
    # 64: folds, collar, sash, cuffs (untinted overlay)
    c = Canvas(32, 64, 3)
    for x0, x1 in ((12, 11), (16, 16.4), (20, 21)):
        c.line(x0, 24, x1, 58, 0.7, (0, 0, 0, 60))
    c.poly([(9, 16), (11, 6), (13, 6), (12, 18)], (0, 0, 0, 40))
    c.poly([(23, 16), (21, 6), (19, 6), (20, 18)], (255, 255, 255, 50))
    c.poly([(13, 6), (16, 16), (19, 6), (17.4, 6), (16, 12), (14.6, 6)], hexc("F4E8D8"))
    c.path([(13, 6), (16, 16), (19, 6)], 0.8, GOLD)
    c.rect(9, 26, 23, 30, GOLD_D)
    c.rect(9, 26.6, 23, 29.4, GOLD)
    c.rect(15, 29, 17, 38, GOLD_D)
    c.rect(4.6, 24, 8.4, 26, GOLD)
    c.rect(23.6, 24, 27.4, 26, GOLD)
    c.rect(8, 58, 24, 60, a(GOLD_D, 200))
    put(img, c, 64, 0, False)
    # motifs (white, tinted gold/silver by the screen), clipped to the robe
    motifs = []
    c = Canvas(32, 64, 3)  # awan: cloud scrolls
    for cx, cy in ((12, 40), (20, 48), (14, 54), (21, 36)):
        c.ring(cx, cy, 1.6, 2.4, WHITE)
        c.ring(cx + 2.6, cy - 0.6, 1.0, 1.7, WHITE)
        c.line(cx - 3, cy + 1.6, cx + 5, cy + 1.6, 0.6, WHITE)
    motifs.append(c)
    c = Canvas(32, 64, 3)  # bangau: two cranes
    for cx, cy, s in ((13, 42, 1.0), (20, 52, 0.8)):
        c.ellipse(cx, cy, 2.4 * s, 1.2 * s, WHITE)
        c.path([(cx + 2 * s, cy), (cx + 3.6 * s, cy - 3 * s), (cx + 4.6 * s, cy - 3.2 * s)], 0.6, WHITE)
        c.path([(cx - 1, cy), (cx - 5 * s, cy - 4 * s), (cx - 7 * s, cy - 2 * s)], 1.0, WHITE)
        c.path([(cx, cy), (cx + 1, cy - 4.4 * s), (cx + 4 * s, cy - 6 * s)], 1.0, WHITE)
        c.line(cx, cy + 1, cx - 1, cy + 4 * s, 0.4, WHITE)
    motifs.append(c)
    c = Canvas(32, 64, 3)  # teratai: lotus flowers and pads
    for cx, cy in ((12, 50), (20, 42)):
        for k in range(5):
            t = -math.pi / 2 + (k - 2) * 0.5
            c.ellipse(cx + math.cos(t) * 2.4, cy + math.sin(t) * 2.4, 1.0, 1.8, WHITE)
        c.ellipse(cx, cy + 2.6, 4, 1.0, WHITE)
    c.path([(10, 56), (14, 54), (22, 57)], 0.6, WHITE)
    motifs.append(c)
    c = Canvas(32, 64, 3)  # naga: a dragon coiling up the robe
    pts = [(16 + math.sin(t / 5.0) * 6, 58 - t * 1.4) for t in range(0, 26)]
    c.path(pts, 2.0, WHITE)
    c.circle(pts[-1][0], pts[-1][1], 2.0, WHITE)
    for k in range(2, 24, 3):
        x, y = pts[k]
        c.line(x, y, x + (1.6 if k % 2 else -1.6), y - 1.6, 0.6, WHITE)
    c.path([(pts[-1][0], pts[-1][1]), (pts[-1][0] + 3, pts[-1][1] - 2.4)], 0.6, WHITE)
    motifs.append(c)
    body = Canvas(32, 64, 1)
    robe_shape(body, WHITE)
    mask = body.resolve()
    for k, m in enumerate(motifs):
        px = m.resolve()
        for y in range(64):
            for x in range(32):
                p = px[y][x]
                if p[3] > 0 and mask[y][x][3] > 200:
                    img.set(96 + k * 32 + x, y, (p[0], p[1], p[2], int(p[3] * 0.9)))
    # 224: hanger tag
    c = Canvas(32, 64, 3)
    c.poly([(8, 20), (24, 20), (24, 34), (16, 40), (8, 34)], PAPER)
    c.circle(16, 24, 1.4, PAPER_D)
    c.line(16, 8, 16, 22, 0.6, hexc("C8343A"))
    c.line(11, 28, 21, 28, 0.6, a(ui.INK, 170))
    c.line(11, 31, 18, 31, 0.6, a(ui.INK, 170))
    put(img, c, 224, 0, True)
    return img


# ===================================================================================================== forbidden tower

def build_menara():
    img = Image(64, 160, (0, 0, 0, 0))
    c = Canvas(64, 160, 3)
    # spire
    c.line(32, 1, 32, 14, 1.2, GOLD)
    for k, y in enumerate((4, 7, 10)):
        c.ellipse(32, y, 3.4 - k * 0.6, 0.9, GOLD_D)
    c.circle(32, 2, 1.4, BLOOD_L)
    for f in range(9):  # f = 0 top floor (9) .. 8 bottom (1)
        top = 16 + f * 16
        half = 12 + f * 1.6
        # wall
        c.rect(32 - half + 3, top + 5, 32 + half - 3, top + 16, hexc("2A1A1E"))
        c.rect(32 - half + 4, top + 6, 32 + half - 4, top + 16, hexc("4A2A30"))
        # windows / door
        if f == 8:
            c.rect(28, top + 8, 36, top + 16, hexc("120808"))
            c.ellipse(32, top + 8, 4, 2, hexc("120808"), -math.pi, 0)
        else:
            for k in (-1, 1):
                c.rect(32 + k * half * 0.45 - 2, top + 8, 32 + k * half * 0.45 + 2, top + 13, hexc("FF7A3A", 220))
                c.line(32 + k * half * 0.45, top + 8, 32 + k * half * 0.45, top + 13, 0.4, hexc("2A1A1E"))
        # pillars
        for k in (-1, 1):
            c.rect(32 + k * (half - 4) - 1, top + 5, 32 + k * (half - 4) + 1, top + 16, BLOOD_D)
        # roof with upturned eaves
        c.poly([(32 - half - 4, top + 6), (32 + half + 4, top + 6), (32 + half - 2, top + 2), (32 - half + 2, top + 2)], hexc("1A1418"))
        c.poly([(32 - half - 4, top + 6), (32 - half - 6, top + 3), (32 - half, top + 4.6)], hexc("1A1418"))
        c.poly([(32 + half + 4, top + 6), (32 + half + 6, top + 3), (32 + half, top + 4.6)], hexc("1A1418"))
        c.line(32 - half - 3, top + 6, 32 + half + 3, top + 6, 0.8, BLOOD)
        c.circle(32 - half - 5.6, top + 3, 0.8, GOLD_D)
        c.circle(32 + half + 5.6, top + 3, 0.8, GOLD_D)
    put(img, c, 0, 0, True)
    return img


# ===================================================================================================== mountain backdrop

def build_gunung():
    w, h = 160, 80
    img = Image(w, h, (0, 0, 0, 0))
    for y in range(h):
        t = y / (h - 1.0)
        sky = mix(hexc("C8D8E0"), hexc("F0EAD8"), t)
        for x in range(w):
            img.set(x, y, sky)
    ranges = [
        (0.32, hexc("A8B4BC"), 5, 0.55),
        (0.46, hexc("7E8C98"), 11, 0.7),
        (0.62, hexc("4E5A66"), 23, 0.85),
    ]
    for base, col, seed, amp in ranges:
        for x in range(w):
            n = fbm(x / 26.0, seed, seed, None, 4)
            ridge = base * h + (0.5 - n) * h * 0.55 * amp - math.sin(x / 160.0 * math.pi) * h * 0.25 * amp
            for y in range(int(max(0, ridge)), h):
                d = (y - ridge) / (h * 0.5)
                fade = clamp(d)
                cc = mix(shade(col, 0.15), col, fade)
                n2 = hnoise(x / 1.6, y / 3.0, seed + 3)
                cc = shade(cc, (n2 - 0.5) * 0.12)
                img.set(x, y, cc)
        # mist band under each range
        my = int(base * h + h * 0.2)
        for y in range(max(0, my - 4), min(h, my + 6)):
            for x in range(w):
                m = fbm(x / 18.0, y / 4.0, seed + 40, None, 2)
                img.blend(x, y, (250, 248, 240, int(150 * clamp((m - 0.35) * 2.2) * (1 - abs(y - my) / 6.0))))
    # pines on the near ridge
    rnd = random.Random(8)
    for _ in range(26):
        x = rnd.randint(2, w - 3)
        y = rnd.randint(int(h * 0.7), h - 3)
        for k in range(4):
            for dx in range(-k, k + 1):
                img.blend(x + dx, y - 3 + k, (36, 54, 46, 220))
        img.blend(x, y + 1, (60, 40, 30, 220))
    return img


# ===================================================================================================== arena card

def build_kartu():
    img = Image(128, 96, (0, 0, 0, 0))
    c = Canvas(64, 96, 3)
    c.rect(0, 0, 64, 96, hexc("1A0E10"))
    c.rect(2, 2, 62, 94, hexc("3A1418"))
    c.rect(4, 4, 60, 92, hexc("5A1A20"))
    for y in range(6, 92, 6):
        c.line(5, y, 59, y - 3, 0.3, a(BLOOD_D, 160))
    c.rect(6, 10, 58, 62, hexc("1A0E10"))
    c.rect(8, 12, 56, 60, (0, 0, 0, 0))
    for (x, y) in ((4, 4), (60, 4), (4, 92), (60, 92)):
        c.circle(x, y, 2.6, IRON)
        c.circle(x, y, 1.2, IRON_L)
    c.rect(6, 64, 58, 76, hexc("2A0C10"))
    c.line(6, 64, 58, 64, 0.8, GOLD_D)
    c.line(6, 76, 58, 76, 0.8, GOLD_D)
    c.poly([(26, 2), (38, 2), (35, 8), (29, 8)], BLOOD)
    c.circle(32, 4.4, 1.6, GOLD)
    c.path([(10, 86), (20, 80), (32, 88), (44, 80), (54, 86)], 0.8, a(BLOOD_L, 120))
    res = c.resolve()
    paste(img, res, 0, 0, None)
    for y in range(12, 60):
        for x in range(8, 56):
            img.set(x, y, (0, 0, 0, 0))
    # VS seal
    c = Canvas(32, 32, 4)
    c.circle(16, 16, 14.6, BLOOD_D)
    c.circle(16, 16, 13, BLOOD)
    c.ring(16, 16, 11, 12, a(GOLD, 220))
    c.line(7, 9, 12, 22, 2.0, BONE)
    c.line(12, 22, 16, 9, 2.0, BONE)
    c.path([(25, 10), (19, 10), (19, 15.4), (25, 16.6), (25, 22), (18.6, 22)], 1.8, BONE)
    put(img, c, 64, 0, True)
    # odds chip
    c = Canvas(32, 16, 4)
    c.rect(1, 2, 31, 14, GOLD_D)
    c.rect(2, 3, 30, 13, hexc("2A0C10"))
    c.line(2, 3, 30, 3, 0.6, GOLD_L)
    put(img, c, 96, 0, False)
    # bet plate
    c = Canvas(64, 16, 4)
    c.rect(0, 1, 64, 15, IRON_D)
    c.rect(1, 2, 63, 14, hexc("3A3036"))
    for x in (3, 61):
        c.circle(x, 8, 1.2, IRON_L)
    c.line(1, 2, 63, 2, 0.5, a(WHITE, 90))
    put(img, c, 64, 32, False)
    # ladder rung
    c = Canvas(64, 16, 4)
    c.rect(0, 5, 64, 11, WOOD_D)
    c.rect(0, 6, 64, 10, WOOD)
    c.line(0, 6.4, 64, 6.4, 0.5, WOOD_L)
    for x in (2, 62):
        c.rect(x - 2, 0, x + 2, 16, hexc("2A1A10"))
    c.path([(14, 10), (16, 13), (15, 15)], 0.8, a(BLOOD, 220))
    put(img, c, 64, 48, False)
    return img


# ===================================================================================================== awning, manifest paper, cell bars

def build_tenda():
    img = Image(64, 16, (0, 0, 0, 0))
    cols = [hexc("C8343A"), hexc("F4E0B8")]
    for y in range(16):
        for x in range(64):
            stripe = (x // 8) % 2
            hem = 11 + 3 * math.sin((x % 8) / 8.0 * math.pi)
            if y > hem:
                continue
            col = cols[stripe]
            col = shade(col, -0.25 * (y / 12.0) + (0.08 if y < 2 else 0))
            n = hnoise(x / 1.5, y / 1.5, 31)
            col = shade(col, (n - 0.5) * 0.1)
            img.set(x, y, col)
        if y == 1:
            for x in range(64):
                img.set(x, y, GOLD)
    for x in range(64):
        hem = int(11 + 3 * math.sin((x % 8) / 8.0 * math.pi))
        img.set(x, min(15, hem + 1), (60, 20, 16, 160))
        if x % 8 == 4:
            img.set(x, min(15, hem + 2), GOLD_L)
    return img


def build_surat():
    img = Image(32, 32, (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            n = fbm(x / 5.0, y / 5.0, 51, 32 / 5.0, 3)
            col = mix(hexc("E8DCC0"), hexc("C8B48A"), n * 0.9)
            img.set(x, y, col)
    for y in range(32):
        for x in range(32):
            d = min(x, y, 31 - x, 31 - y)
            if d < 3:
                img.blend(x, y, (120, 80, 40, int(110 * (1 - d / 3.0))))
    for x in range(32):  # torn top edge
        cut = int(1.6 * hnoise(x / 2.0, 0, 61) + 0.4)
        for y in range(cut):
            img.set(x, y, (0, 0, 0, 0))
    for y in (10, 16, 22):  # red rules across the centre (tile every 16 inside the slice)
        for x in range(8, 24):
            img.blend(x, y, (176, 48, 36, 70))
    for y in range(8, 24):
        img.blend(8, y, (176, 48, 36, 80))
    return img


def build_kandang():
    img = Image(32, 32, (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            img.set(x, y, (28, 22, 26, 235))
            n = hnoise(x / 3.0, y / 3.0, 71)
            img.blend(x, y, (90, 70, 70, int(40 * n)))
    for y in range(32):
        for x in range(32):
            if x < 3 or x > 28 or y < 3 or y > 28:
                img.set(x, y, mix(IRON_D, IRON, 0.3 + 0.4 * hnoise(x, y, 72)))
    for bx in (8, 13, 18, 23):
        for y in range(3, 29):
            img.set(bx, y, IRON)
            img.set(bx + 1, y, IRON_L)
            img.set(bx + 2, y, IRON_D)
    for x in range(3, 29):
        img.set(x, 12, IRON_D)
        img.set(x, 13, IRON)
    for (x, y) in ((1, 1), (30, 1), (1, 30), (30, 30)):
        img.set(x, y, IRON_L)
    return img


# ===================================================================================================== main

def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = [
        ("ikon_tema", build_icons, 4, (40, 44, 52, 255)),
        ("mural", build_murals, 2, (40, 44, 52, 255)),
        ("irama", build_irama, 4, (40, 44, 52, 255)),
        ("jubah", build_jubah, 3, (90, 70, 60, 255)),
        ("menara", build_menara, 3, (90, 90, 96, 255)),
        ("latar_gunung", build_gunung, 3, (40, 44, 52, 255)),
        ("kartu", build_kartu, 3, (40, 44, 52, 255)),
        ("tenda", build_tenda, 6, (40, 44, 52, 255)),
        ("surat", build_surat, 8, (40, 44, 52, 255)),
        ("kandang", build_kandang, 8, (200, 190, 170, 255)),
    ]
    for name, fn, k, bg in jobs:
        img = fn()
        img.save(os.path.join(OUT, name + ".png"))
        preview(img, "%s_x%d.png" % (name, k), k, bg)
    print("gen_tempat_tema_ui: wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
