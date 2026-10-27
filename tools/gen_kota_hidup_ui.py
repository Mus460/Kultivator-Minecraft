"""Generates the icon atlas of the 42 Kota Hidup screens (DESIGN 42.11, module 42U-1; every icon of the 42.11 screen list,
so 42U-2 only uses them).

Our own art, drawn from primitives with the helpers of tools/gen_tempat_ui.py (Canvas, paste, colours) and written with
tools/png.py. Lands in textures/gui/kota42/ikon.png and is read by client/dunia/kota42/ui/layar/IkonKota.java:

ikon.png     256x112  16 px icon atlas, slot = row * 16 + column, order = the ICONS list below = IkonKota constants:
             row 0  mood faces senang..sakit (0-6), hearts full/broken/empty (7-9), root star, untested root (10-11),
                    office seal (12), name tag (13), debt ledger (14), gift (15)
             row 1  the eight zhuazhou objects (16-23), memories diselamatkan..rumah_rusak (24-31)
             row 2  memories utang_lunas, utang_mangkir, cerita_orang_tua (32-34), family ayah..saudara, kakek (35-40),
                    bonds sahabat, kekasih, musuh, saingan, guru_murid (41-45), grave (46), moved away (47)
             row 3  the 14 event kinds (48-61), calendar (62), clock (63)
             row 4  houses kecil..paviliun (64-68), warehouse, contract, caravan, shop, drawer, investment, withdraw,
                    opportunity, tax, treasury, business sign (69-79)
             row 5  the 12 price categories pangan..bangunan (80-91), trend up/down/flat (92-94), warning (95)
             row 6  Ghost Market mask, moon, dice, bamboo cup, gavel, scales, pact shield, branch banner, plot, stage,
                    guard, office, root test, raid, stolen, fence (96-111)

Previews (x4) go to tools/keluaran/kota_hidup_ui/ for inspection with Read; they are not shipped.

Run with: python tools/gen_kota_hidup_ui.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402
import gen_tempat_ui as ui  # noqa: E402
from gen_tempat_ui import Canvas, paste, hexc, shade  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "gui", "kota42")
PREVIEW = os.path.join(HERE, "keluaran", "kota_hidup_ui")

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
GREEN, GREEN_L, GREEN_D = ui.GREEN, ui.GREEN_L, ui.GREEN_D
AMBER = ui.AMBER
GREY = ui.GREY
PURPLE, PURPLE_L = ui.PURPLE, ui.PURPLE_L
SKIN = hexc("F0C8A0")
SKIN_D = hexc("C89870")
STONE = hexc("8C8A84")
STONE_L = hexc("C4C0B6")
STONE_D = hexc("55524C")
BRONZE = hexc("B07A3A")
BRONZE_L = hexc("E0B070")
TILE = hexc("3A4048")
TILE_L = hexc("5A6470")
WALL = hexc("EDE6D2")
NIGHT = hexc("1C2236")
MINT = hexc("3FA86A")
MINT_L = hexc("7DE0A4")
CLOTH_R = hexc("B04050")


def a(c, alpha):
    return (c[0], c[1], c[2], alpha)


def new():
    return Canvas(16, 16, 4)


def put(img, c, ox, oy):
    paste(img, c.resolve(), ox, oy, OUTLINE)


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


def star(c, cx, cy, r0, r1, col, n=5, rot=-math.pi / 2):
    pts = []
    for k in range(n * 2):
        r = r1 if k % 2 == 0 else r0
        ang = rot + k * math.pi / n
        pts.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
    c.poly(pts, col)


def heart(c, cx, cy, s, col):
    c.circle(cx - s * 0.5, cy - s * 0.25, s * 0.55, col)
    c.circle(cx + s * 0.5, cy - s * 0.25, s * 0.55, col)
    c.poly([(cx - s * 1.03, cy - s * 0.05), (cx + s * 1.03, cy - s * 0.05), (cx, cy + s * 1.1)], col)


def paper(c, x0, y0, x1, y1, col=PAPER, edge=PAPER_D):
    c.rect(x0, y0, x1, y1, edge)
    c.rect(x0 + 0.6, y0 + 0.6, x1 - 0.6, y1 - 0.6, col)


def head(c, cx, cy, r, hair=INK, skin=SKIN):
    c.circle(cx, cy, r, skin)
    c.ellipse(cx, cy - r * 0.45, r * 1.05, r * 0.75, hair)
    c.circle(cx - r * 0.35, cy + r * 0.05, r * 0.1, INK)
    c.circle(cx + r * 0.35, cy + r * 0.05, r * 0.1, INK)


def body(c, cx, top, w, h, col):
    c.poly([(cx - w * 0.5, top + h), (cx - w * 0.38, top), (cx + w * 0.38, top), (cx + w * 0.5, top + h)], col)


# ===================================================================================================== row 0

def face(c, tone, mouth, brow=0.0, extra=None):
    c.circle(8, 8, 7.2, GOLD_D)
    c.circle(8, 8, 6.4, tone)
    c.ellipse(6.2, 5.0, 3.0, 1.8, (255, 255, 255, 90))
    for dx in (-2.4, 2.4):
        c.circle(8 + dx, 6.8, 0.95, INK)
        c.circle(8 + dx + 0.3, 6.5, 0.3, WHITE)
    if brow:
        c.line(4.6, 4.9 - brow, 7.0, 5.5 + brow, 0.7, INK, False)
        c.line(11.4, 4.9 - brow, 9.0, 5.5 + brow, 0.7, INK, False)
    mouth(c)
    if extra:
        extra(c)


def i_senang(c):
    face(c, hexc("FFD25C"), lambda c: c.path([(4.8, 9.6), (6.4, 11.6), (9.6, 11.6), (11.2, 9.6)], 0.9, INK))


def i_netral(c):
    face(c, hexc("E8D8A0"), lambda c: c.line(5.6, 11.2, 10.4, 11.2, 0.9, INK))


def i_sedih(c):
    face(c, hexc("A8C0E8"), lambda c: c.path([(5.2, 12.0), (6.6, 10.4), (9.4, 10.4), (10.8, 12.0)], 0.9, INK), -0.6,
         lambda c: c.ellipse(11.8, 9.2, 0.7, 1.3, hexc("5AA0FF")))


def i_takut(c):
    face(c, hexc("E8DC90"), lambda c: c.ellipse(8, 11.2, 1.2, 1.7, INK), 0.8,
         lambda c: (c.ellipse(13, 3.8, 0.5, 1.2, hexc("8AD0FF")), c.ellipse(3, 4.4, 0.4, 1.0, hexc("8AD0FF"))))


def i_marah(c):
    face(c, hexc("F07A5A"), lambda c: c.path([(5.0, 12.0), (6.8, 10.6), (9.2, 10.6), (11.0, 12.0)], 1.0, INK), -1.0,
         lambda c: (c.line(12.2, 1.4, 13.6, 2.8, 0.7, RED_D, False), c.line(13.6, 1.4, 12.2, 2.8, 0.7, RED_D, False)))


def i_mabuk(c):
    def mouth(c):
        c.path([(5.0, 10.6), (6.4, 12.0), (8.0, 10.8), (9.6, 12.0), (11.0, 10.6)], 0.8, INK)
    face(c, hexc("F0A0A8"), mouth, 0.0,
         lambda c: (c.ellipse(4.2, 9.4, 1.4, 0.8, a(RED_L, 150)), c.ellipse(11.8, 9.4, 1.4, 0.8, a(RED_L, 150))))


def i_sakit(c):
    face(c, hexc("B8D88A"), lambda c: c.path([(5.4, 11.4), (7.0, 10.6), (9.0, 12.0), (10.6, 11.0)], 0.8, INK), 0.4,
         lambda c: (c.rect(1.6, 0.4, 5.0, 2.0, WHITE), c.rect(2.6, -0.4, 4.0, 2.8, RED_L)))


def i_hati(c):
    heart(c, 8, 5.2, 4.1, RED)
    c.ellipse(5.4, 4.2, 1.5, 1.0, (255, 255, 255, 130))


def i_hati_patah(c):
    heart(c, 8, 5.2, 4.1, hexc("5A1A22"))
    c.poly([(8.6, 2.2), (7.0, 6.0), (9.0, 8.2), (7.4, 14.0), (9.2, 9.0), (7.4, 6.2)], (0, 0, 0, 0))
    c.path([(8.8, 1.6), (7.2, 5.4), (9.2, 8.0), (7.6, 13.6)], 0.9, hexc("C8B0A0"))


def i_hati_kosong(c):
    heart(c, 8, 5.2, 4.1, hexc("8A8A8A"))
    heart(c, 8, 5.4, 3.0, hexc("C4C4C4"))


def i_akar(c):
    star(c, 8, 8.6, 3.0, 7.2, GOLD)
    star(c, 8, 8.6, 2.0, 4.6, GOLD_L)
    c.circle(8, 8.6, 1.2, JADE_L)


def i_akar_tanya(c):
    star(c, 8, 8.6, 3.0, 7.2, hexc("9A9A9A"))
    star(c, 8, 8.6, 2.0, 4.6, hexc("C8C8C8"))
    c.path([(6.6, 6.8), (7.0, 5.6), (9.0, 5.6), (9.4, 7.0), (8.0, 8.2), (8.0, 9.4)], 0.9, INK)
    c.circle(8, 11.0, 0.7, INK)


def i_segel_jabatan(c):
    c.circle(8, 8, 7.0, RED_D)
    c.circle(8, 8, 6.2, RED)
    c.ring(8, 8, 4.0, 4.8, hexc("F0C8A8"))
    c.rect(5.8, 5.6, 10.2, 6.4, hexc("F0C8A8"))
    c.rect(5.8, 7.6, 10.2, 8.4, hexc("F0C8A8"))
    c.rect(7.6, 5.6, 8.4, 10.4, hexc("F0C8A8"))


def i_nama(c):
    c.rect(2.5, 4.0, 13.5, 12.5, PAPER_D)
    c.rect(3.1, 4.6, 12.9, 11.9, PAPER)
    c.circle(8, 3.6, 1.1, GOLD_D)
    c.circle(8, 3.6, 0.5, (0, 0, 0, 0))
    c.line(4.6, 7.2, 11.4, 7.2, 0.8, INK, False)
    c.line(4.6, 9.2, 9.4, 9.2, 0.8, a(INK, 150), False)
    c.circle(11.4, 10.4, 0.9, RED)


def i_utang(c):
    paper(c, 2.8, 2.0, 11.6, 14.0)
    for k in range(4):
        c.line(4.2, 4.6 + k * 2.2, 10.2, 4.6 + k * 2.2, 0.6, a(INK, 150), False)
    c.circle(11.6, 11.6, 3.6, GOLD_D)
    c.circle(11.6, 11.6, 2.9, GOLD)
    c.line(10.2, 11.6, 13.0, 11.6, 0.9, RED_D, False)


def i_hadiah(c):
    c.rect(2.5, 6.6, 13.5, 14.0, RED_D)
    c.rect(3.0, 7.1, 13.0, 13.5, RED)
    c.rect(2.0, 4.8, 14.0, 7.6, RED_L)
    c.rect(7.2, 4.8, 8.8, 14.0, GOLD)
    c.ellipse(5.6, 3.6, 2.2, 1.4, GOLD)
    c.ellipse(10.4, 3.6, 2.2, 1.4, GOLD)
    c.circle(8, 4.4, 1.0, GOLD_L)


# ===================================================================================================== row 1: zhuazhou

def i_kuas(c):
    c.line(3.4, 14.2, 11.4, 3.0, 1.6, WOOD)
    c.line(3.4, 14.2, 11.4, 3.0, 0.5, WOOD_L, False)
    c.poly([(10.6, 4.2), (13.8, 0.8), (14.6, 1.8), (11.6, 5.6)], INK)
    c.poly([(2.4, 15.0), (4.2, 12.6), (5.0, 13.6), (3.0, 15.6)], hexc("2E2420"))
    c.line(10.8, 4.6, 11.8, 5.2, 0.8, hexc("E8D8B0"), False)


def i_sempoa(c):
    c.rect(1.5, 2.5, 14.5, 13.5, WOOD_D)
    c.rect(2.1, 3.1, 13.9, 12.9, hexc("E0C690"))
    c.line(2.1, 6.6, 13.9, 6.6, 0.8, WOOD_D, False)
    for k in range(5):
        x = 3.6 + k * 2.2
        c.line(x, 3.1, x, 12.9, 0.5, WOOD_D, False)
        c.ellipse(x, 5.0 if k % 2 else 4.2, 0.95, 0.85, RED_D)
        c.ellipse(x, 8.6 if k % 3 else 9.6, 0.95, 0.85, RED)
        c.ellipse(x, 11.0 if k % 2 else 10.6, 0.95, 0.85, RED)


def i_pedang_kayu(c):
    c.poly([(8, 0.8), (9.4, 3.0), (9.0, 11.2), (7.0, 11.2), (6.6, 3.0)], WOOD_L)
    c.poly([(8, 0.8), (9.4, 3.0), (9.0, 11.2), (8, 11.2)], WOOD)
    c.rect(4.8, 11.0, 11.2, 12.2, WOOD_D)
    c.rect(7.0, 12.2, 9.0, 15.0, WOOD)
    c.line(8, 3.0, 8, 10.4, 0.4, a(WHITE, 120), False)


def i_cap(c):
    c.rect(5.0, 8.0, 11.0, 14.5, RED_D)
    c.rect(5.0, 8.0, 11.0, 10.0, RED)
    c.rect(6.0, 3.6, 10.0, 8.4, WOOD)
    c.ellipse(8, 3.6, 2.0, 1.6, WOOD_L)
    c.rect(6.3, 11.2, 9.7, 11.8, WHITE)
    c.rect(7.7, 10.4, 8.3, 13.0, WHITE)


def i_kitab(c):
    c.rect(2.5, 2.0, 13.0, 14.0, BLUE_D)
    c.rect(3.1, 2.6, 12.4, 13.4, BLUE)
    c.rect(2.5, 2.0, 4.0, 14.0, BLUE_D)
    c.rect(5.0, 4.4, 11.0, 7.2, PAPER)
    for k in range(3):
        c.line(5.6, 9.0 + k * 1.4, 10.4, 9.0 + k * 1.4, 0.4, a(PAPER, 160), False)
    c.line(2.8, 4.0, 3.8, 4.0, 0.6, GOLD, False)
    c.line(2.8, 12.0, 3.8, 12.0, 0.6, GOLD, False)


def i_palu(c):
    c.line(4.0, 14.0, 10.0, 6.6, 1.8, WOOD)
    c.rect(7.6, 1.8, 14.0, 6.0, IRON)
    c.rect(7.6, 1.8, 14.0, 3.2, IRON_L)
    c.rect(11.6, 1.8, 14.0, 6.0, IRON_D)


def i_tasbih(c):
    for k in range(10):
        ang = math.pi * 0.9 + k * (math.pi * 1.2) / 9
        cx, cy = 8 + math.cos(ang) * 5.6, 8.2 + math.sin(ang) * 5.2
        c.circle(cx, cy, 1.3, WOOD_D)
        c.circle(cx - 0.3, cy - 0.3, 0.6, WOOD_L)
    c.line(8, 13.0, 8, 15.2, 0.6, GOLD, False)
    c.circle(8, 14.4, 1.2, RED)


def i_koin_zh(c):
    c.circle(8, 8, 7.0, BRONZE)
    c.circle(8, 8, 6.2, BRONZE_L)
    c.rect(5.8, 5.8, 10.2, 10.2, BRONZE)
    c.rect(6.6, 6.6, 9.4, 9.4, (0, 0, 0, 0))
    c.rect(6.4, 6.4, 9.6, 9.6, hexc("6A4A20"))
    c.ring(8, 8, 5.3, 6.0, BRONZE)


# ===================================================================================================== row 1-2: memories

def i_selamat(c):
    c.circle(8, 8, 6.6, BLUE_L)
    c.ring(8, 8, 4.4, 6.0, WHITE)
    c.ring(8, 8, 2.0, 3.2, RED)
    c.line(2.4, 2.4, 13.6, 13.6, 0.9, RED, False)
    c.line(13.6, 2.4, 2.4, 13.6, 0.9, a(RED, 200), False)


def i_dibantu(c):
    c.poly([(1.5, 11.0), (4.0, 7.0), (8.0, 8.0), (8.0, 12.0), (4.0, 13.5)], SKIN)
    c.poly([(14.5, 11.0), (12.0, 7.0), (8.0, 8.0), (8.0, 12.0), (12.0, 13.5)], SKIN_D)
    c.circle(8, 4.4, 2.0, GOLD)
    c.circle(8, 4.4, 1.2, GOLD_L)


def i_nikah(c):
    c.circle(6, 9, 4.2, GOLD_D)
    c.circle(6, 9, 3.2, (0, 0, 0, 0))
    c.ring(6, 9, 2.8, 4.2, GOLD)
    c.ring(10.4, 9, 2.8, 4.2, GOLD_L)
    c.circle(8.2, 3.2, 1.7, RED)
    c.poly([(6.6, 2.6), (8.2, 4.2), (9.8, 2.6), (8.2, 1.4)], RED_L)


def i_makam(c):
    c.rect(2.0, 10.0, 14.0, 14.5, STONE_D)
    c.poly([(4.0, 10.0), (4.0, 3.5), (6.0, 1.8), (10.0, 1.8), (12.0, 3.5), (12.0, 10.0)], STONE)
    c.poly([(5.0, 10.0), (5.0, 4.0), (6.4, 2.8), (9.6, 2.8)], STONE_L)
    c.line(8, 4.4, 8, 8.4, 0.7, STONE_D, False)
    c.line(6.6, 5.8, 9.4, 5.8, 0.7, STONE_D, False)


def i_anak_sekte(c):
    head(c, 6.0, 8.0, 3.0)
    body(c, 6.0, 10.6, 5.0, 4.6, JADE)
    c.ellipse(11.4, 3.4, 3.0, 1.9, WHITE)
    c.ellipse(13.2, 4.2, 2.2, 1.5, hexc("DCE6F0"))
    c.ellipse(9.8, 4.4, 2.2, 1.4, hexc("DCE6F0"))


def i_dipukul(c):
    c.poly([(3.0, 5.0), (11.0, 4.0), (13.0, 8.0), (10.0, 12.4), (3.6, 11.4)], SKIN)
    for k in range(4):
        c.line(6.0 + k * 1.6, 4.6 - k * 0.2, 6.0 + k * 1.6, 8.0, 0.5, SKIN_D, False)
    c.line(0.8, 2.6, 3.0, 4.4, 0.8, RED, False)
    c.line(0.8, 12.8, 3.0, 11.0, 0.8, RED, False)
    star(c, 13.4, 3.6, 0.8, 2.0, GOLD_L, 6)


def i_dicuri(c):
    c.poly([(3.2, 6.0), (10.0, 6.0), (11.0, 14.0), (2.4, 14.0)], hexc("7A5A3A"))
    c.ellipse(6.6, 6.0, 3.6, 1.2, hexc("5A3E22"))
    c.circle(6.6, 10.4, 1.5, GOLD)
    c.poly([(10.4, 4.0), (14.8, 6.0), (13.6, 8.0), (9.6, 6.6)], SKIN_D)
    c.line(11.0, 4.6, 12.0, 6.4, 0.5, INK, False)


def i_rumah_rusak(c):
    c.rect(3.0, 7.0, 13.0, 14.0, WALL)
    c.poly([(1.4, 7.4), (8, 1.6), (14.6, 7.4)], RED_D)
    c.poly([(3.0, 8.0), (5.0, 11.0), (4.0, 14.0), (3.0, 14.0)], (0, 0, 0, 0))
    c.path([(8.6, 7.4), (7.4, 10.0), (9.0, 11.6), (7.8, 14.0)], 0.9, INK)
    c.poly([(11.0, 2.8), (13.0, 4.4), (12.0, 6.0)], hexc("FF8A3D"))


def i_utang_lunas(c):
    paper(c, 2.8, 2.0, 11.8, 14.0)
    for k in range(3):
        c.line(4.2, 4.4 + k * 2.4, 10.2, 4.4 + k * 2.4, 0.6, a(INK, 130), False)
    c.circle(11.6, 11.4, 3.6, GREEN_D)
    c.path([(9.6, 11.4), (11.0, 12.8), (13.6, 9.8)], 1.1, WHITE)


def i_utang_mangkir(c):
    c.rect(2.0, 5.0, 7.4, 11.0, WOOD)
    c.rect(8.6, 5.0, 14.0, 11.0, WOOD_D)
    c.poly([(7.4, 5.0), (8.6, 5.0), (8.0, 8.0), (8.6, 11.0), (7.4, 11.0), (7.9, 8.0)], (0, 0, 0, 0))
    for k in range(3):
        c.line(3.0, 6.4 + k * 1.8, 6.6, 6.4 + k * 1.8, 0.5, WOOD_L, False)
    c.line(4.0, 2.4, 12.0, 13.6, 1.0, RED, False)


def i_cerita_ortu(c):
    c.rect(2.0, 3.0, 14.0, 13.0, PAPER_D)
    c.rect(2.6, 3.6, 13.4, 12.4, PAPER)
    head(c, 5.4, 7.0, 1.7)
    head(c, 10.6, 7.0, 1.7, hair=hexc("9A9A9A"))
    body(c, 5.4, 8.8, 3.4, 3.2, GREEN)
    body(c, 10.6, 8.8, 3.4, 3.2, BLUE)


# ===================================================================================================== family and bonds

def person(c, hair, cloth, skin=SKIN, scale=1.0):
    head(c, 8, 4.8, 2.8 * scale, hair, skin)
    body(c, 8, 8.0, 8.0 * scale, 6.8, cloth)


def i_ayah(c):
    person(c, INK, BLUE)
    c.rect(6.0, 6.4, 10.0, 7.0, INK)


def i_ibu(c):
    person(c, INK, hexc("D870A0"))
    c.circle(8, 1.8, 1.3, INK)
    c.rect(5.0, 3.4, 11.0, 4.6, INK)


def i_pasangan(c):
    c.ring(5.8, 8, 2.8, 4.2, GOLD)
    c.ring(10.2, 8, 2.8, 4.2, GOLD_L)
    heart(c, 8, 3.2, 1.6, RED)


def i_anak(c):
    head(c, 8, 7.0, 3.6)
    body(c, 8, 10.4, 6.0, 4.8, hexc("F0C050"))
    c.circle(4.4, 4.6, 1.2, INK)
    c.circle(11.6, 4.6, 1.2, INK)


def i_saudara(c):
    head(c, 5.2, 6.0, 2.4)
    body(c, 5.2, 8.6, 5.4, 5.4, GREEN)
    head(c, 10.8, 6.8, 2.2, hair=hexc("5A3E22"))
    body(c, 10.8, 9.2, 5.0, 4.8, hexc("E0803A"))


def i_kakek(c):
    person(c, hexc("D8D8D8"), hexc("7A5A3A"))
    c.poly([(6.2, 6.8), (9.8, 6.8), (8, 11.2)], hexc("EEEEEE"))
    c.line(12.6, 6.0, 13.4, 14.6, 0.9, WOOD, False)


def i_sahabat(c):
    c.poly([(1.5, 10.5), (4.0, 6.0), (8.0, 7.0), (8.0, 11.0), (4.0, 12.6)], SKIN)
    c.poly([(14.5, 10.5), (12.0, 6.0), (8.0, 7.0), (8.0, 11.0), (12.0, 12.6)], SKIN_D)
    star(c, 8, 3.2, 0.9, 2.4, GOLD_L)


def i_kekasih(c):
    heart(c, 5.2, 7.0, 3.0, RED_L)
    heart(c, 10.8, 8.0, 3.0, hexc("F08AA8"))


def i_musuh(c):
    c.line(2.6, 2.6, 13.4, 13.4, 1.4, IRON_L)
    c.line(13.4, 2.6, 2.6, 13.4, 1.4, IRON)
    c.rect(1.6, 11.6, 4.6, 12.6, GOLD_D)
    c.rect(11.4, 11.6, 14.4, 12.6, GOLD_D)
    c.ring(8, 8, 1.2, 2.0, RED)


def i_saingan(c):
    c.line(3.0, 13.0, 3.0, 4.0, 1.2, WOOD_D)
    c.poly([(3.6, 3.6), (9.0, 4.8), (3.6, 7.6)], RED)
    c.line(13.0, 13.0, 13.0, 4.0, 1.2, WOOD_D)
    c.poly([(12.4, 3.6), (7.0, 4.8), (12.4, 7.6)], BLUE)
    c.rect(6.4, 12.4, 9.6, 13.6, STONE)


def i_guru(c):
    head(c, 5.0, 4.6, 2.2, hair=hexc("D8D8D8"))
    body(c, 5.0, 7.0, 6.0, 7.6, hexc("3E5A8A"))
    head(c, 11.4, 7.4, 1.8)
    body(c, 11.4, 9.4, 4.2, 4.8, JADE)
    c.line(13.6, 5.0, 14.4, 12.0, 0.8, WOOD, False)


def i_makam2(c):
    c.rect(1.6, 12.0, 14.4, 14.4, hexc("4A6A3A"))
    c.poly([(5.0, 12.0), (5.0, 5.0), (6.6, 3.4), (9.4, 3.4), (11.0, 5.0), (11.0, 12.0)], STONE_L)
    c.poly([(5.0, 12.0), (5.0, 5.0), (6.6, 3.4), (8, 3.4), (8, 12.0)], STONE)
    c.line(8, 5.6, 8, 9.0, 0.6, STONE_D, False)
    c.line(6.8, 6.8, 9.2, 6.8, 0.6, STONE_D, False)
    c.line(2.6, 4.0, 2.6, 8.0, 0.6, a(WHITE, 140), False)


def i_pindah(c):
    c.rect(2.0, 6.0, 11.0, 11.0, WOOD)
    c.rect(2.0, 6.0, 11.0, 7.4, WOOD_L)
    c.circle(4.6, 12.6, 2.0, WOOD_D)
    c.circle(4.6, 12.6, 0.8, WOOD_L)
    c.circle(9.4, 12.6, 2.0, WOOD_D)
    c.circle(9.4, 12.6, 0.8, WOOD_L)
    c.path([(11.0, 8.0), (13.0, 8.0), (14.4, 6.0)], 0.9, WOOD_D)
    c.poly([(12.6, 3.0), (15.0, 4.6), (12.6, 6.0)], BLUE_L)


# ===================================================================================================== row 3: events

def i_ac_nikah(c):
    c.circle(8, 8, 7.0, RED_D)
    c.circle(8, 8, 6.2, RED)
    for dx in (-1.8, 1.8):
        c.rect(8 + dx - 1.4, 4.4, 8 + dx + 1.4, 5.0, GOLD_L)
        c.rect(8 + dx - 1.4, 7.0, 8 + dx + 1.4, 7.6, GOLD_L)
        c.rect(8 + dx - 1.4, 9.6, 8 + dx + 1.4, 10.2, GOLD_L)
        c.line(8 + dx - 0.9, 4.4, 8 + dx - 0.9, 10.2, 0.5, GOLD_L, False)
        c.line(8 + dx + 0.9, 4.4, 8 + dx + 0.9, 10.2, 0.5, GOLD_L, False)
    c.rect(4.8, 11.6, 11.2, 12.2, GOLD_L)


def i_ac_makam(c):
    c.circle(8, 8, 7.0, hexc("D0D0D0"))
    c.circle(8, 8, 6.2, hexc("F2F2F2"))
    c.rect(5.0, 4.6, 11.0, 5.2, INK)
    c.rect(7.7, 4.0, 8.3, 11.0, INK)
    c.rect(5.6, 7.2, 10.4, 7.8, INK)
    c.rect(5.2, 9.6, 10.8, 10.2, INK)
    c.line(7.0, 12.4, 9.0, 12.4, 0.7, STONE, False)


def i_ac_lahir(c):
    c.ellipse(8, 8.6, 4.8, 6.0, hexc("F2C0A8"))
    c.ellipse(6.6, 6.0, 1.6, 2.4, (255, 255, 255, 120))
    c.ring(8, 8.6, 3.6, 4.0, RED)
    c.poly([(3.4, 8.6), (12.6, 8.6), (12.0, 10.4), (4.0, 10.4)], RED)
    star(c, 13.0, 3.0, 0.7, 1.8, GOLD_L, 4)


def i_ac_sebulan(c):
    c.circle(8, 8, 7.0, NIGHT)
    c.circle(8, 8, 5.4, hexc("F4E8B0"))
    c.circle(10.4, 6.8, 4.6, NIGHT)
    star(c, 12.4, 12.0, 0.5, 1.4, GOLD_L, 4)
    c.circle(4.6, 4.6, 0.6, WHITE)


def i_ac_zhuazhou(c):
    c.ellipse(8, 10.0, 6.8, 3.6, WOOD_D)
    c.ellipse(8, 9.4, 6.0, 3.0, WOOD_L)
    c.circle(4.8, 8.6, 1.2, RED)
    c.rect(7.0, 7.4, 9.6, 9.6, GOLD)
    c.line(10.6, 5.4, 12.6, 9.4, 0.8, INK, False)
    c.circle(8, 3.6, 1.6, SKIN)


def i_ac_rombongan(c):
    c.ellipse(8, 8.4, 6.2, 5.6, RED_D)
    c.ellipse(8, 7.6, 6.2, 5.0, RED)
    c.ellipse(8, 7.6, 4.6, 3.6, hexc("E8D2A0"))
    c.line(2.0, 13.6, 6.0, 9.0, 0.9, WOOD, False)
    c.line(14.0, 13.6, 10.0, 9.0, 0.9, WOOD, False)


def i_ac_hari_raya(c):
    c.line(8, 0.4, 8, 3.0, 0.7, WOOD_D, False)
    c.ellipse(8, 8.4, 5.4, 5.0, RED_D)
    c.ellipse(8, 8.0, 5.0, 4.6, RED)
    c.ellipse(6.4, 6.0, 1.6, 1.4, (255, 255, 255, 110))
    c.rect(5.6, 2.6, 10.4, 3.8, GOLD)
    c.rect(5.6, 12.6, 10.4, 13.6, GOLD)
    c.line(8, 13.6, 8, 15.6, 0.6, GOLD, False)
    c.line(6.4, 13.6, 6.4, 15.0, 0.5, GOLD_L, False)
    c.line(9.6, 13.6, 9.6, 15.0, 0.5, GOLD_L, False)


def i_ac_ronda(c):
    c.circle(8, 8.4, 6.4, BRONZE_D := hexc("6A4218"))
    c.circle(8, 8.4, 5.6, BRONZE)
    c.circle(8, 8.4, 3.2, BRONZE_L)
    c.circle(8, 8.4, 1.4, BRONZE)
    c.line(8, 1.2, 8, 2.6, 0.6, WOOD_D, False)
    c.line(5.0, 14.8, 6.4, 13.6, 0.7, WOOD, False)


def i_ac_ujian(c):
    c.poly([(3.0, 14.0), (3.0, 8.0), (5.0, 6.4), (11.0, 6.4), (13.0, 8.0), (13.0, 14.0)], STONE)
    c.poly([(3.0, 14.0), (3.0, 8.0), (5.0, 6.4), (8, 6.4), (8, 14.0)], STONE_L)
    c.circle(8, 4.0, 2.0, GOLD)
    for k in range(5):
        ang = -math.pi / 2 + k * 2 * math.pi / 5
        c.line(8 + math.cos(ang) * 2.4, 4.0 + math.sin(ang) * 2.4, 8 + math.cos(ang) * 3.8, 4.0 + math.sin(ang) * 3.8, 0.6, GOLD_L, False)
    c.line(5.6, 9.6, 10.4, 9.6, 0.7, STONE_D, False)


def i_ac_pasar(c):
    c.poly([(1.4, 6.0), (3.6, 1.8), (12.4, 1.8), (14.6, 6.0)], RED)
    for k in range(4):
        c.poly([(1.4 + k * 3.3, 6.0), (3.6 + k * 2.2, 1.8), (5.8 + k * 1.1, 1.8), (4.7 + k * 3.3, 6.0)], WHITE if k % 2 else RED_L)
    c.rect(2.4, 6.0, 3.2, 14.0, WOOD_D)
    c.rect(12.8, 6.0, 13.6, 14.0, WOOD_D)
    c.rect(2.4, 10.0, 13.6, 11.4, WOOD)
    c.circle(5.2, 9.0, 1.1, hexc("E0803A"))
    c.circle(8.0, 9.0, 1.1, GREEN)
    c.circle(10.8, 9.0, 1.1, hexc("F0C050"))


def i_ac_wayang(c):
    c.rect(1.5, 2.0, 14.5, 14.0, hexc("E8D2A0"))
    c.rect(1.5, 2.0, 14.5, 3.0, WOOD_D)
    c.rect(1.5, 13.0, 14.5, 14.0, WOOD_D)
    c.poly([(8, 5.0), (9.2, 6.0), (9.0, 8.6), (11.0, 11.8), (5.0, 11.8), (7.0, 8.6), (6.8, 6.0)], INK)
    c.line(11.0, 6.0, 12.6, 3.6, 0.6, INK, False)


def i_ac_serbuan(c):
    c.line(2.6, 13.4, 9.6, 3.0, 1.2, IRON_L)
    c.line(13.4, 13.4, 6.4, 3.0, 1.2, IRON)
    c.poly([(7.0, 0.8), (9.0, 0.8), (8.0, 3.2)], RED)
    c.poly([(1.0, 4.0), (3.0, 4.0), (2.0, 6.4)], RED)
    c.poly([(13.0, 4.0), (15.0, 4.0), (14.0, 6.4)], RED)


def i_ac_peresmian(c):
    c.rect(1.0, 9.0, 15.0, 11.0, RED)
    c.poly([(1.0, 9.0), (4.0, 7.0), (4.0, 13.0), (1.0, 11.0)], RED_D)
    c.poly([(15.0, 9.0), (12.0, 7.0), (12.0, 13.0), (15.0, 11.0)], RED_D)
    c.line(6.0, 3.0, 10.0, 15.0, 1.0, IRON_L, False)
    c.line(10.0, 3.0, 6.0, 15.0, 0.0, (0, 0, 0, 0), False)
    star(c, 8, 4.6, 1.0, 2.6, GOLD_L, 6)


def i_ac_sidang(c):
    c.rect(2.0, 11.6, 14.0, 14.4, WOOD_D)
    c.rect(2.0, 11.6, 14.0, 12.8, WOOD)
    c.line(4.0, 9.4, 11.0, 3.6, 1.6, WOOD)
    c.rect(8.6, 1.6, 13.6, 5.2, WOOD_D)
    c.rect(9.0, 1.8, 13.2, 3.0, WOOD_L)


def i_kalender(c):
    c.rect(1.8, 3.0, 14.2, 14.2, PAPER_D)
    c.rect(2.4, 3.6, 13.6, 13.6, PAPER)
    c.rect(1.8, 3.0, 14.2, 6.0, RED)
    c.rect(4.4, 1.2, 5.4, 4.4, IRON)
    c.rect(10.6, 1.2, 11.6, 4.4, IRON)
    for r in range(3):
        for k in range(4):
            c.rect(3.2 + k * 2.6, 7.4 + r * 2.2, 4.8 + k * 2.6, 8.8 + r * 2.2, a(INK, 90) if (r, k) != (1, 2) else RED)


def i_jam(c):
    c.circle(8, 8, 7.0, GOLD_D)
    c.circle(8, 8, 6.0, hexc("F4ECD0"))
    for k in range(12):
        ang = k * math.pi / 6
        c.line(8 + math.cos(ang) * 4.6, 8 + math.sin(ang) * 4.6, 8 + math.cos(ang) * 5.4, 8 + math.sin(ang) * 5.4, 0.5, INK, False)
    c.line(8, 8, 8, 4.0, 0.8, INK, False)
    c.line(8, 8, 11.2, 9.4, 0.7, RED_D, False)
    c.circle(8, 8, 0.8, INK)


# ===================================================================================================== row 4: houses, economy

def house(c, w0, w1, top, roof, wall=WALL, floors=1):
    c.rect(w0, top, w1, 14.0, wall)
    c.rect(w0, top, w1, 14.0, wall)
    c.poly([(w0 - 1.4, top + 0.4), ((w0 + w1) / 2, top - 4.6), (w1 + 1.4, top + 0.4)], roof)
    c.poly([(w0 - 1.4, top + 0.4), ((w0 + w1) / 2, top - 4.6), ((w0 + w1) / 2, top + 0.4)], shade(roof, 0.16))
    c.rect((w0 + w1) / 2 - 1.0, 10.2, (w0 + w1) / 2 + 1.0, 14.0, WOOD_D)
    c.rect(w0 + 1.0, top + 2.0, w0 + 2.8, top + 3.8, hexc("F0C050"))


def i_rumah_kecil(c):
    house(c, 4.2, 11.8, 8.6, RED_D)


def i_rumah_sedang(c):
    house(c, 2.6, 13.4, 7.8, RED_D)
    c.rect(10.0, 9.2, 11.8, 11.0, hexc("F0C050"))


def i_rumah_besar(c):
    house(c, 1.6, 14.4, 7.0, TILE)
    c.rect(1.6, 5.4, 14.4, 7.0, WALL)
    c.poly([(0.4, 5.6), (8, 1.0), (15.6, 5.6)], TILE_L)
    c.rect(3.0, 8.4, 4.6, 10.0, hexc("F0C050"))
    c.rect(11.4, 8.4, 13.0, 10.0, hexc("F0C050"))


def i_rumah_toko(c):
    house(c, 2.0, 14.0, 7.2, RED_D)
    c.rect(2.0, 9.6, 14.0, 11.0, CLOTH_R)
    for k in range(4):
        c.poly([(2.0 + k * 3.0, 11.0), (3.5 + k * 3.0, 12.4), (5.0 + k * 3.0, 11.0)], WHITE if k % 2 else CLOTH_R)
    c.rect(6.4, 11.6, 9.6, 14.0, WOOD_D)


def i_paviliun(c):
    c.poly([(0.6, 6.0), (8, 1.4), (15.4, 6.0), (13.0, 6.0), (8, 3.4), (3.0, 6.0)], RED_D)
    c.rect(2.4, 6.0, 3.4, 14.0, RED)
    c.rect(12.6, 6.0, 13.6, 14.0, RED)
    c.rect(2.4, 6.0, 13.6, 7.2, RED_D)
    c.rect(3.4, 7.2, 12.6, 14.0, a(WALL, 200))
    c.rect(1.6, 13.4, 14.4, 14.8, STONE)


def i_gudang(c):
    c.rect(1.5, 8.4, 8.0, 14.6, WOOD_D)
    c.rect(2.1, 9.0, 7.4, 14.0, WOOD)
    c.line(2.1, 11.5, 7.4, 11.5, 0.6, WOOD_D, False)
    c.rect(8.4, 4.6, 14.6, 14.6, WOOD)
    c.rect(8.4, 4.6, 14.6, 5.8, WOOD_L)
    c.line(8.4, 9.6, 14.6, 9.6, 0.6, WOOD_D, False)
    c.line(11.5, 4.6, 11.5, 14.6, 0.6, WOOD_D, False)
    c.rect(2.8, 5.8, 6.6, 8.4, WOOD_L)


def i_kontrak(c):
    paper(c, 2.4, 1.8, 13.4, 14.4)
    for k in range(4):
        c.line(4.0, 4.0 + k * 1.8, 11.8, 4.0 + k * 1.8, 0.5, a(INK, 150), False)
    c.circle(10.8, 11.4, 2.6, RED_D)
    c.circle(10.8, 11.4, 2.0, RED)
    c.rect(9.8, 10.6, 11.8, 12.2, hexc("F0C8A8"))
    c.line(3.8, 12.6, 7.4, 12.6, 0.7, a(INK, 200), False)


def i_kafilah(c):
    c.rect(1.5, 5.4, 10.4, 11.4, WOOD)
    c.poly([(1.0, 5.4), (5.6, 1.6), (11.0, 5.4)], hexc("E8D2A0"))
    c.circle(4.2, 12.8, 2.2, WOOD_D)
    c.circle(4.2, 12.8, 0.9, WOOD_L)
    c.circle(8.6, 12.8, 2.2, WOOD_D)
    c.circle(8.6, 12.8, 0.9, WOOD_L)
    c.ellipse(13.0, 9.0, 1.8, 2.4, hexc("B08A5A"))
    c.rect(11.8, 10.6, 13.8, 14.2, hexc("B08A5A"))


def i_toko_ikon(c):
    c.rect(2.0, 6.0, 14.0, 14.0, WOOD_D)
    c.rect(2.0, 6.0, 14.0, 7.6, CLOTH_R)
    for k in range(4):
        c.poly([(2.0 + k * 3.0, 7.6), (3.5 + k * 3.0, 9.2), (5.0 + k * 3.0, 7.6)], WHITE if k % 2 else CLOTH_R)
    c.rect(2.6, 11.0, 13.4, 12.4, WOOD_L)
    c.circle(5.4, 10.0, 1.1, GREEN)
    c.circle(8.2, 10.0, 1.1, hexc("E0803A"))
    c.rect(10.0, 8.8, 12.4, 11.0, hexc("D8E0E6"))
    c.rect(6.0, 1.0, 10.0, 5.0, WOOD)
    c.rect(6.4, 1.4, 9.6, 4.6, RED)


def i_laci(c):
    c.rect(1.6, 5.0, 14.4, 14.0, WOOD_D)
    c.rect(2.2, 5.6, 13.8, 9.4, WOOD)
    c.rect(2.2, 9.8, 13.8, 13.4, WOOD)
    c.rect(6.4, 7.0, 9.6, 8.0, GOLD)
    c.rect(6.4, 11.2, 9.6, 12.2, GOLD)
    c.circle(8, 2.8, 2.2, GOLD_D)
    c.circle(8, 2.8, 1.5, GOLD)


def i_investasi(c):
    c.ellipse(8, 14.0, 6.0, 1.2, a(INK, 80))
    c.path([(8, 14.0), (8, 8.4)], 1.0, GREEN_D)
    c.ellipse(5.2, 7.6, 3.0, 1.6, GREEN)
    c.ellipse(10.8, 5.6, 3.0, 1.6, GREEN_L)
    c.circle(8, 4.0, 2.6, GOLD_D)
    c.circle(8, 4.0, 1.9, GOLD)
    c.line(7.0, 4.0, 9.0, 4.0, 0.6, GOLD_D, False)


def i_tarik(c):
    c.circle(6.4, 8.0, 4.6, GOLD_D)
    c.circle(6.4, 8.0, 3.8, GOLD)
    c.circle(6.4, 8.0, 1.6, GOLD_D)
    c.poly([(10.0, 5.0), (15.0, 8.0), (10.0, 11.0), (10.0, 9.2), (8.0, 9.2), (8.0, 6.8), (10.0, 6.8)], hexc("D8E0E6"))


def i_peluang(c):
    c.circle(8, 8, 7.0, JADE_D)
    c.circle(8, 8, 6.2, JADE)
    star(c, 8, 8.4, 2.0, 5.4, GOLD_L)
    c.circle(8, 8.4, 1.0, WHITE)


def i_pajak(c):
    c.line(8, 2.0, 8, 13.6, 0.9, IRON_D, False)
    c.rect(4.6, 13.0, 11.4, 14.6, IRON)
    c.line(2.6, 4.6, 13.4, 4.6, 0.9, IRON, False)
    c.path([(2.6, 4.6), (1.4, 9.4)], 0.5, IRON_L)
    c.path([(2.6, 4.6), (3.8, 9.4)], 0.5, IRON_L)
    c.ellipse(2.6, 9.8, 2.4, 0.8, GOLD)
    c.path([(13.4, 4.6), (12.2, 8.4)], 0.5, IRON_L)
    c.path([(13.4, 4.6), (14.6, 8.4)], 0.5, IRON_L)
    c.ellipse(13.4, 8.8, 2.4, 0.8, GOLD)
    c.circle(8, 2.4, 1.2, GOLD)


def i_kas(c):
    c.rect(1.6, 7.0, 14.4, 14.4, WOOD_D)
    c.rect(2.2, 7.6, 13.8, 13.8, WOOD)
    c.poly([(1.6, 7.0), (3.0, 3.4), (13.0, 3.4), (14.4, 7.0)], WOOD_L)
    c.rect(1.6, 7.0, 14.4, 8.4, IRON)
    c.rect(6.8, 7.8, 9.2, 10.6, GOLD)
    c.circle(8, 9.2, 0.6, INK)
    c.circle(5.0, 2.8, 1.2, GOLD)


def i_usaha(c):
    c.rect(7.4, 0.6, 8.6, 5.0, WOOD_D)
    c.rect(1.6, 4.4, 14.4, 11.6, RED_D)
    c.rect(2.2, 5.0, 13.8, 11.0, RED)
    c.rect(4.0, 6.4, 5.0, 9.6, GOLD_L)
    c.rect(6.0, 6.4, 10.0, 7.0, GOLD_L)
    c.rect(6.0, 8.4, 11.4, 9.0, GOLD_L)
    c.rect(11.6, 6.4, 12.4, 9.6, GOLD_L)
    c.line(5, 11.6, 5, 14.4, 0.6, WOOD_D, False)
    c.line(11, 11.6, 11, 14.4, 0.6, WOOD_D, False)


# ===================================================================================================== row 5: categories

def i_pangan(c):
    c.ellipse(8, 9.6, 6.4, 5.0, hexc("F2E2B8"))
    c.ellipse(8, 9.0, 5.6, 4.0, hexc("FFF4D8"))
    c.line(5.0, 7.4, 6.6, 6.2, 0.5, hexc("D8C090"), False)
    c.line(9.6, 6.2, 11.0, 7.4, 0.5, hexc("D8C090"), False)
    c.path([(5.0, 2.4), (5.6, 4.0), (5.0, 5.2)], 0.5, a(WHITE, 180))
    c.path([(8.0, 1.6), (8.6, 3.2), (8.0, 4.6)], 0.5, a(WHITE, 180))
    c.path([(11.0, 2.4), (11.6, 4.0), (11.0, 5.2)], 0.5, a(WHITE, 180))


def i_obat(c):
    c.ellipse(8, 10.6, 5.0, 4.4, hexc("E8F0F8"))
    c.ellipse(8, 10.6, 4.4, 3.8, hexc("E04050"))
    c.rect(6.0, 2.4, 10.0, 6.4, hexc("E8F0F8"))
    c.rect(5.4, 1.4, 10.6, 2.8, WOOD)
    c.ellipse(6.4, 9.4, 1.2, 1.6, (255, 255, 255, 140))


def i_herba(c):
    c.path([(8, 14.4), (8, 7.0)], 1.0, GREEN_D)
    c.ellipse(5.0, 6.6, 3.2, 1.7, GREEN)
    c.ellipse(11.0, 4.8, 3.2, 1.7, GREEN_L)
    c.ellipse(5.4, 10.6, 2.8, 1.4, GREEN_L)
    c.ellipse(10.8, 9.6, 2.8, 1.4, GREEN)
    c.circle(8, 3.4, 1.4, hexc("F0E060"))


def i_senjata(c):
    c.poly([(8, 0.8), (9.2, 2.4), (9.0, 9.6), (7.0, 9.6), (6.8, 2.4)], IRON_L)
    c.poly([(8, 0.8), (9.2, 2.4), (9.0, 9.6), (8, 9.6)], IRON)
    c.rect(4.6, 9.6, 11.4, 10.8, GOLD_D)
    c.rect(7.0, 10.8, 9.0, 14.4, WOOD)
    c.circle(8, 14.8, 0.9, GOLD)


def i_zirah(c):
    c.poly([(2.0, 3.6), (5.4, 2.0), (6.6, 3.4), (9.4, 3.4), (10.6, 2.0), (14.0, 3.6), (13.0, 7.0), (11.4, 6.6), (11.4, 14.0), (4.6, 14.0), (4.6, 6.6), (3.0, 7.0)], IRON)
    c.poly([(2.0, 3.6), (5.4, 2.0), (6.6, 3.4), (8, 3.4), (8, 14.0), (4.6, 14.0), (4.6, 6.6), (3.0, 7.0)], IRON_L)
    c.line(8, 4.0, 8, 13.4, 0.5, IRON_D, False)
    c.circle(8, 8.0, 1.2, GOLD)


def i_jimat(c):
    c.rect(4.0, 1.6, 12.0, 14.4, hexc("F2D060"))
    c.rect(4.0, 1.6, 12.0, 14.4, hexc("F2D060"))
    c.rect(4.6, 2.2, 11.4, 13.8, hexc("FFE890"))
    c.line(8, 3.4, 8, 12.6, 0.8, RED, False)
    c.line(5.8, 5.6, 10.2, 5.6, 0.8, RED, False)
    c.line(5.8, 8.0, 10.2, 8.0, 0.8, RED, False)
    c.line(6.4, 10.6, 9.6, 10.6, 0.8, RED, False)
    c.path([(5.4, 12.2), (6.4, 11.4)], 0.6, RED)


def i_bahan(c):
    c.rect(1.6, 9.0, 8.0, 14.0, hexc("A07A4A"))
    c.rect(1.6, 9.0, 8.0, 10.4, hexc("C09A6A"))
    c.rect(8.4, 9.0, 14.4, 14.0, STONE)
    c.rect(8.4, 9.0, 14.4, 10.4, STONE_L)
    c.rect(4.6, 3.6, 11.0, 8.6, hexc("8A6A3A"))
    c.rect(4.6, 3.6, 11.0, 4.8, hexc("B08A5A"))
    c.line(7.8, 3.6, 7.8, 8.6, 0.4, WOOD_D, False)


def i_kain(c):
    c.poly([(2.0, 3.0), (14.0, 3.0), (13.0, 14.0), (3.0, 14.0)], CLOTH_R)
    for k in range(5):
        c.line(2.6 + k * 2.6, 3.0, 3.2 + k * 2.2, 14.0, 0.5, a(WHITE, 90), False)
    c.rect(2.0, 3.0, 14.0, 4.6, shade(CLOTH_R, 0.22))
    c.ellipse(11.0, 9.0, 1.6, 1.6, GOLD_L)


def i_mewah(c):
    c.poly([(8, 1.6), (13.6, 6.0), (8, 14.4), (2.4, 6.0)], hexc("7DE0C4"))
    c.poly([(8, 1.6), (13.6, 6.0), (8, 7.4)], hexc("C0F4E4"))
    c.poly([(8, 1.6), (2.4, 6.0), (8, 7.4)], hexc("A0ECD4"))
    c.poly([(2.4, 6.0), (8, 7.4), (8, 14.4)], hexc("3FB48C"))
    c.line(5.6, 3.8, 6.8, 5.0, 0.5, WHITE, False)


def i_dagang(c):
    c.rect(1.8, 6.0, 14.2, 14.0, hexc("A07A4A"))
    c.rect(1.8, 6.0, 14.2, 7.6, hexc("C09A6A"))
    c.line(1.8, 10.0, 14.2, 10.0, 0.6, WOOD_D, False)
    c.line(8, 6.0, 8, 14.0, 0.6, WOOD_D, False)
    c.circle(8, 3.8, 2.4, GOLD_D)
    c.circle(8, 3.8, 1.7, GOLD)


def i_jasa(c):
    c.circle(8, 8, 7.0, BLUE_D)
    c.circle(8, 8, 6.2, BLUE)
    c.path([(5.0, 8.4), (7.2, 10.8), (11.4, 5.4)], 1.4, WHITE)


def i_bangunan(c):
    c.rect(2.0, 12.0, 14.0, 14.4, STONE)
    c.line(4.0, 12.0, 4.0, 3.0, 1.0, WOOD)
    c.line(12.0, 12.0, 12.0, 3.0, 1.0, WOOD)
    c.line(3.0, 6.0, 13.0, 6.0, 0.8, WOOD_L, False)
    c.line(3.0, 9.0, 13.0, 9.0, 0.8, WOOD_L, False)
    c.poly([(6.0, 12.0), (6.0, 7.4), (10.0, 7.4), (10.0, 12.0)], hexc("E0C690"))


def i_naik(c):
    c.poly([(8, 1.6), (14.6, 9.0), (10.4, 9.0), (10.4, 14.4), (5.6, 14.4), (5.6, 9.0), (1.4, 9.0)], GREEN_L)
    c.poly([(8, 1.6), (14.6, 9.0), (8, 9.0)], GREEN)


def i_turun(c):
    c.poly([(8, 14.4), (14.6, 7.0), (10.4, 7.0), (10.4, 1.6), (5.6, 1.6), (5.6, 7.0), (1.4, 7.0)], RED_L)
    c.poly([(8, 14.4), (14.6, 7.0), (8, 7.0)], RED)


def i_datar(c):
    c.poly([(14.6, 8), (8.6, 2.6), (8.6, 5.8), (1.4, 5.8), (1.4, 10.2), (8.6, 10.2), (8.6, 13.4)], hexc("C8C8C8"))
    c.poly([(14.6, 8), (8.6, 8), (8.6, 13.4)], hexc("9A9A9A"))


def i_waspada(c):
    c.poly([(8, 1.0), (15.2, 14.2), (0.8, 14.2)], AMBER)
    c.poly([(8, 3.4), (13.2, 12.8), (2.8, 12.8)], hexc("FFD25C"))
    c.rect(7.2, 5.6, 8.8, 10.0, INK)
    c.circle(8, 11.6, 0.9, INK)


# ===================================================================================================== row 6: 42U-2 icons

def i_topeng(c):
    c.poly([(8, 1.6), (13.0, 3.6), (13.4, 9.0), (10.6, 13.6), (8, 14.4), (5.4, 13.6), (2.6, 9.0), (3.0, 3.6)], hexc("EFEFEF"))
    c.poly([(8, 1.6), (3.0, 3.6), (2.6, 9.0), (5.4, 13.6), (8, 14.4)], hexc("D4D4D4"))
    c.ellipse(5.8, 6.8, 1.6, 0.9, INK)
    c.ellipse(10.2, 6.8, 1.6, 0.9, INK)
    c.path([(5.6, 10.4), (8, 11.8), (10.4, 10.4)], 0.7, RED)
    c.circle(8, 4.0, 0.9, MINT)


def i_bulan_hantu(c):
    c.circle(8, 8, 7.0, NIGHT)
    c.circle(8, 8, 5.6, MINT_L)
    c.circle(10.6, 6.6, 4.8, NIGHT)
    c.circle(12.6, 12.4, 0.8, MINT)
    c.circle(3.8, 4.0, 0.6, MINT_L)


def i_dadu(c):
    c.poly([(2.4, 6.0), (9.4, 6.0), (9.4, 13.4), (2.4, 13.4)], WHITE)
    c.poly([(2.4, 6.0), (5.0, 3.0), (12.0, 3.0), (9.4, 6.0)], hexc("E8E4D4"))
    c.poly([(9.4, 6.0), (12.0, 3.0), (12.0, 10.4), (9.4, 13.4)], hexc("C8C4B4"))
    for dx, dy in ((4.0, 8.0), (7.8, 11.6), (4.0, 11.6), (7.8, 8.0), (5.9, 9.8)):
        c.circle(dx, dy, 0.7, RED_D if (dx, dy) == (5.9, 9.8) else INK)
    c.circle(8.2, 4.6, 0.55, INK)
    c.circle(10.6, 5.6, 0.45, INK)


def i_cawan(c):
    c.poly([(3.0, 4.0), (13.0, 4.0), (11.6, 14.2), (4.4, 14.2)], hexc("B8A060"))
    c.poly([(3.0, 4.0), (8, 4.0), (8, 14.2), (4.4, 14.2)], hexc("D8C080"))
    c.ellipse(8, 4.0, 5.0, 1.4, hexc("8A7440"))
    c.ellipse(8, 4.0, 4.2, 0.9, INK)
    for k in range(4):
        c.line(4.8 + k * 1.8, 5.0, 5.2 + k * 1.5, 13.6, 0.4, a(WOOD_D, 140), False)


def i_palu_hakim(c):
    c.rect(1.5, 12.2, 14.5, 14.6, WOOD_D)
    c.ellipse(8, 12.2, 4.0, 1.2, WOOD)
    c.line(4.0, 11.0, 9.4, 5.0, 1.4, WOOD)
    c.poly([(8.0, 2.4), (12.6, 1.2), (14.0, 4.6), (9.4, 6.0)], WOOD_L)
    c.poly([(8.0, 2.4), (9.4, 6.0), (10.6, 5.6), (9.4, 2.0)], WOOD_D)


def i_timbangan(c):
    c.line(8, 2.4, 8, 13.8, 0.9, GOLD_D, False)
    c.rect(4.8, 13.2, 11.2, 14.8, GOLD)
    c.line(2.4, 4.4, 13.6, 4.4, 0.8, GOLD, False)
    for cx in (2.4, 13.6):
        c.path([(cx, 4.4), (cx - 1.8, 9.6)], 0.4, GOLD_L)
        c.path([(cx, 4.4), (cx + 1.8, 9.6)], 0.4, GOLD_L)
        c.ellipse(cx, 10.0, 2.4, 0.9, GOLD)
    c.circle(8, 2.6, 1.2, GOLD_L)


def i_perisai_pakta(c):
    c.poly([(2.4, 2.4), (13.6, 2.4), (13.6, 8.4), (8, 15.0), (2.4, 8.4)], hexc("2E5A8A"))
    c.poly([(2.4, 2.4), (8, 2.4), (8, 15.0), (2.4, 8.4)], hexc("4A7AB4"))
    c.ring(8, 7.6, 2.2, 3.0, GOLD_L)
    c.circle(8, 7.6, 1.2, GOLD)
    c.rect(2.4, 2.4, 13.6, 3.6, GOLD_D)


def i_panji(c):
    c.line(3.0, 0.8, 3.0, 15.0, 1.0, WOOD_D)
    c.poly([(3.6, 1.6), (14.0, 1.6), (14.0, 9.0), (8.8, 11.4), (3.6, 9.0)], JADE)
    c.poly([(3.6, 1.6), (8.8, 1.6), (8.8, 11.4), (3.6, 9.0)], JADE_L)
    c.ellipse(9.0, 5.0, 2.8, 1.6, WHITE)
    c.circle(3.0, 0.8, 1.0, GOLD)


def i_kavling(c):
    c.poly([(1.2, 8.4), (8, 4.4), (14.8, 8.4), (8, 12.8)], hexc("8AB060"))
    c.poly([(1.2, 8.4), (8, 12.8), (8, 14.4), (1.2, 10.0)], hexc("6A5036"))
    c.poly([(14.8, 8.4), (8, 12.8), (8, 14.4), (14.8, 10.0)], hexc("52402A"))
    for cx, cy in ((1.4, 8.4), (8, 4.6), (14.6, 8.4), (8, 12.6)):
        c.line(cx, cy, cx, cy - 3.4, 0.7, WOOD, False)
        c.poly([(cx, cy - 3.4), (cx + 2.0, cy - 2.6), (cx, cy - 1.8)], RED)


def i_tahap(c):
    c.rect(2.0, 12.0, 14.0, 14.4, STONE)
    c.rect(3.0, 8.6, 13.0, 12.0, WOOD)
    c.line(3.0, 10.0, 13.0, 10.0, 0.4, WOOD_D, False)
    c.rect(4.4, 5.4, 11.6, 8.6, WALL)
    c.poly([(3.4, 5.6), (8, 1.8), (12.6, 5.6)], TILE)


def i_penjaga(c):
    head(c, 8, 4.6, 2.6)
    c.poly([(5.0, 3.6), (8, 0.8), (11.0, 3.6)], IRON)
    body(c, 8, 7.6, 8.0, 7.0, hexc("3E5A8A"))
    c.line(12.4, 14.4, 12.4, 1.6, 0.9, WOOD, False)
    c.poly([(12.4, 1.0), (13.2, 3.0), (11.6, 3.0)], IRON_L)


def i_kantor(c):
    c.rect(2.0, 5.0, 14.0, 14.4, WALL)
    c.poly([(0.6, 5.4), (8, 0.8), (15.4, 5.4)], hexc("2E5A8A"))
    c.rect(4.0, 7.0, 6.4, 10.0, hexc("F0C050"))
    c.rect(9.6, 7.0, 12.0, 10.0, hexc("F0C050"))
    c.rect(6.8, 10.4, 9.2, 14.4, WOOD_D)
    c.poly([(7.0, 3.2), (9.0, 3.2), (8.0, 4.8)], JADE_L)


def i_uji_akar(c):
    c.poly([(3.0, 14.0), (3.0, 8.0), (5.0, 6.4), (11.0, 6.4), (13.0, 8.0), (13.0, 14.0)], hexc("5A7A9A"))
    c.poly([(3.0, 14.0), (3.0, 8.0), (5.0, 6.4), (8, 6.4), (8, 14.0)], hexc("8AAAC8"))
    star(c, 8, 3.6, 1.4, 3.4, GOLD_L)
    c.line(5.6, 9.6, 10.4, 9.6, 0.6, hexc("C0E0FF"), False)


def i_razia(c):
    c.circle(8, 8, 7.0, RED_D)
    c.circle(8, 8, 6.2, RED)
    c.rect(4.0, 7.2, 12.0, 8.8, WHITE)
    c.poly([(8, 2.6), (9.0, 4.4), (7.0, 4.4)], WHITE)
    c.poly([(8, 13.4), (9.0, 11.6), (7.0, 11.6)], WHITE)


def i_curian(c):
    c.poly([(3.2, 6.0), (10.0, 6.0), (11.0, 14.0), (2.4, 14.0)], hexc("7A5A3A"))
    c.ellipse(6.6, 6.0, 3.6, 1.2, hexc("5A3E22"))
    c.circle(6.6, 10.4, 1.5, GOLD)
    star(c, 12.6, 3.4, 0.8, 2.2, RED_L, 5)


def i_penadah(c):
    c.ellipse(8, 8.4, 6.6, 5.0, hexc("5A3E5A"))
    c.ellipse(8, 7.6, 6.0, 4.4, hexc("7A5A7A"))
    c.circle(5.4, 6.6, 1.0, GOLD_L)
    c.circle(8.2, 8.6, 1.0, GREEN_L)
    c.circle(10.8, 6.8, 1.0, RED_L)
    c.rect(6.8, 12.4, 9.2, 14.4, hexc("5A3E5A"))


ICONS = [
    # row 0
    i_senang, i_netral, i_sedih, i_takut, i_marah, i_mabuk, i_sakit, i_hati, i_hati_patah, i_hati_kosong, i_akar, i_akar_tanya,
    i_segel_jabatan, i_nama, i_utang, i_hadiah,
    # row 1
    i_kuas, i_sempoa, i_pedang_kayu, i_cap, i_kitab, i_palu, i_tasbih, i_koin_zh, i_selamat, i_dibantu, i_nikah, i_makam,
    i_anak_sekte, i_dipukul, i_dicuri, i_rumah_rusak,
    # row 2
    i_utang_lunas, i_utang_mangkir, i_cerita_ortu, i_ayah, i_ibu, i_pasangan, i_anak, i_saudara, i_kakek, i_sahabat, i_kekasih,
    i_musuh, i_saingan, i_guru, i_makam2, i_pindah,
    # row 3
    i_ac_nikah, i_ac_makam, i_ac_lahir, i_ac_sebulan, i_ac_zhuazhou, i_ac_rombongan, i_ac_hari_raya, i_ac_ronda, i_ac_ujian,
    i_ac_pasar, i_ac_wayang, i_ac_serbuan, i_ac_peresmian, i_ac_sidang, i_kalender, i_jam,
    # row 4
    i_rumah_kecil, i_rumah_sedang, i_rumah_besar, i_rumah_toko, i_paviliun, i_gudang, i_kontrak, i_kafilah, i_toko_ikon, i_laci,
    i_investasi, i_tarik, i_peluang, i_pajak, i_kas, i_usaha,
    # row 5
    i_pangan, i_obat, i_herba, i_senjata, i_zirah, i_jimat, i_bahan, i_kain, i_mewah, i_dagang, i_jasa, i_bangunan, i_naik,
    i_turun, i_datar, i_waspada,
    # row 6
    i_topeng, i_bulan_hantu, i_dadu, i_cawan, i_palu_hakim, i_timbangan, i_perisai_pakta, i_panji, i_kavling, i_tahap, i_penjaga,
    i_kantor, i_uji_akar, i_razia, i_curian, i_penadah,
]


def build_icons():
    rows = (len(ICONS) + 15) // 16
    img = Image(256, rows * 16, (0, 0, 0, 0))
    for slot, fn in enumerate(ICONS):
        c = new()
        fn(c)
        put(img, c, (slot % 16) * 16, (slot // 16) * 16)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    assert len(ICONS) == 112, len(ICONS)
    img = build_icons()
    img.save(os.path.join(OUT, "ikon.png"))
    preview(img, "ikon_x4.png", 4)
    print("gen_kota_hidup_ui: wrote", os.path.normpath(OUT), "icons:", len(ICONS))


if __name__ == "__main__":
    main()
