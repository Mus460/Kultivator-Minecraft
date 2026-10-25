"""Hand props 170-239 of DESIGN 42 "Kota yang Hidup" (42.12.4 prop table, 42.12.5 clips, module 42V-G): cuboid item models
kultivator:fx/kota42/<name> and their 32 px material textures, drawn through 40G's PropKatalog/SosokPropLayer by the clips of
client/dunia/kota42/gerak/KotaPoses. Everything is ours (hand-placed cuboids, procedural textures, pixel glyphs drawn here as
matrices; no font, no download). Names and order are KotaGerakId.PROP_NAMA (read from the Java file, never numbered twice).

Writes (assets/kultivator/...):
  textures/item/fx/kota42/*.png          materials (32 x 32)
  models/item/fx/kota42/<name>.json      one cuboid model per prop (at most 40 elements)
  items/fx/kota42/<name>.json            item definitions (DataComponents.ITEM_MODEL)

Rules (gen_tempat_models.validate, DESIGN 38.O.0): anchor (8, 8, 8) = the grip; for props held forward (the default grip)
+y runs away from the fist along the handle; props KotaPoses holds upright (PropKatalog.TEGAK: jars, cups, cages, baskets,
lanterns, trays) are modelled with +y up and the hand at the anchor, so a hanging thing lies below it; ground pieces
(tikar_zhuazhou, papan_patok) stand with their feet at y 0; tempayan_air sits with its foot ring at y 0 on the head; the front
faces north (-z); element coordinates -16..32, uv 0..16, one rotation axis of -45/-22.5/22.5/45 degrees.

Run: python -I tools/gen_kota_hidup_prop.py [preview_dir]   (preview: material sheet + a 3/4 view of every model)
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tempat_models as T  # noqa: E402
from gen_tempat_models import (rgb, mix, shade, tex, wood, bamboo, lacquer, metal, bronze, gold, jade, paper, cloth, porcelain,  # noqa: E402
                               rice, straw, leather, rope, glow, plain, leaf, water, Model, validate, model_json,
                               write_json, sheet)
import gen_mandiri_prop as GM  # noqa: E402  (its iso() preview reads GM.MAT at call time)

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "..")
ROOT = os.path.join(REPO, "src", "main", "resources", "assets", "kultivator")
GERAK_ID = os.path.join(REPO, "src", "main", "java", "dev", "kultivator", "dunia", "kota42", "api", "KotaGerakId.java")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx", "kota42")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx", "kota42")
ITEM_DIR = os.path.join(ROOT, "items", "fx", "kota42")
FOLDER = "kota42"
TEX_FOLDER = "item/fx/kota42"

MAT = {}
EVERY = ("north", "south", "east", "west", "up", "down")


def mat(name, fn, seed):
    tex(name, fn, seed, store=MAT)


def names_from_java():
    src = open(GERAK_ID, encoding="utf-8").read()
    m = re.search(r"String\[\] PROP_NAMA = \{(.*?)\};", src, re.S)
    return re.findall(r'"([a-z_]+)"', m.group(1))


# ================================================================================================ pixel glyphs (12 x 12)

GLYPH = {
    # wine: water radical + the jar
    "jiu": ["#...########",
            ".#.....#.#..",
            "......######",
            "#.....#.#..#",
            ".#....#.#..#",
            "......##.###",
            "..#...#....#",
            ".#....######",
            ".#....#....#",
            "#.....######",
            "#.....#....#",
            "......######"],
    # fortune
    "fu": [".#...#######",
           "#####.......",
           "...#..#####.",
           "..#...#...#.",
           ".###..#####.",
           "#.#.#.......",
           "..#..#######",
           "..#..#..#..#",
           "..#..#######",
           "..#..#..#..#",
           "..#..#######",
           "..#.........", ],
    # rice
    "mi": [".#...#...#..",
           "..#..#..#...",
           "...#.#.#....",
           ".....#......",
           "############",
           ".....#......",
           "....###.....",
           "...#.#.#....",
           "..#..#..#...",
           ".#...#...#..",
           "#....#....#.",
           ".....#......"],
    # double happiness
    "xi": ["..#....#....",
           "#####.#####.",
           ".###...###..",
           ".#.#...#.#..",
           ".###...###..",
           "############",
           ".#.#...#.#..",
           "#####.#####.",
           ".###...###..",
           ".#.#...#.#..",
           ".###...###..",
           "............"],
    # spring (the couplet)
    "chun": ["....##......",
             "############",
             "....##......",
             "..########..",
             "....##......",
             "############",
             "...#..#.....",
             "..#.##.#....",
             ".#.#..#.#...",
             "#..####..#..",
             "...#..#.....",
             "...####....."],
}


def glyph_at(name, x, y, ox, oy, scale=2):
    rows = GLYPH[name]
    gx, gy = (x - ox) // scale, (y - oy) // scale
    return 0 <= gy < len(rows) and 0 <= gx < len(rows[0]) and x >= ox and y >= oy and rows[gy][gx] == "#"


# ================================================================================================ materials

def glazed(base, dark, seed, drip=True):
    """Stoneware with a running glaze: darker at the foot, a lighter shoulder, drips and pinholes."""
    b, d = rgb(base), rgb(dark)

    def p(x, y, rng, n):
        c = mix(b, d, y / 31.0 * 0.7 + 0.3 * n.at(x, y, 8))
        if drip and y > 8 and (x * 5 + int(4 * math.sin(x))) % 9 == 0 and y < 14 + 10 * n.at(x, 0, 4):
            c = shade(c, 1.22)
        if (x * 13 + y * 7) % 37 == 0:
            c = shade(c, 0.7)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def label_glyph(bg, ink, name, border=None):
    """A square paper label with a brushed glyph in the middle (and an optional border line)."""
    b, k = rgb(bg), rgb(ink)

    def p(x, y, rng, n):
        c = shade(b, 0.9 + 0.12 * n.at(x, y, 8))
        if border and (x in (1, 30) or y in (1, 30)):
            c = rgb(border)
        if glyph_at(name, x, y, 4, 4, 2):
            c = k
        return c
    return p


def snake_skin(seed):
    """The erhu's python-skin face: dark scales outlined in cream."""
    def p(x, y, rng, n):
        sx, sy = (x + (y // 4) * 2) % 5, y % 4
        c = rgb("3A3020") if sx and sy else rgb("C8B888")
        if n.at(x, y, 8) > 0.6 and sx and sy:
            c = rgb("6A5A38")
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def silk_embroidery(seed):
    """Cream silk stretched on the hoop with a half-finished red peony and green leaves."""
    def p(x, y, rng, n):
        c = shade(rgb("F0E8D8"), 0.95 + 0.06 * n.at(x, y, 4))
        r = math.hypot(x - 16, y - 14)
        a = math.atan2(y - 14, x - 16)
        if r < 7 + 2 * math.sin(a * 5) and x < 22:
            c = mix(rgb("C02838"), rgb("F07888"), r / 9.0)
        elif 8 < r < 13 and (a > 1.2 or a < -2.4) and abs(math.sin(a * 3)) > 0.6:
            c = rgb("3E8A4A")
        if x >= 22 and abs(y - 14 - (x - 22) * 0.4) < 0.6:
            c = rgb("C02838")  # the running thread
        return c
    return p


def mirror_face(seed):
    def p(x, y, rng, n):
        r = math.hypot(x - 15.5, y - 15.5)
        c = mix(rgb("E8F0F4"), rgb("8CA2B0"), r / 22.0 + 0.2 * n.at(x, y, 8))
        if abs(x - y) < 2 and r < 12:
            c = shade(c, 1.15)  # a streak of reflected light
        return c
    return p


def dice_face(seed):
    """Bone dice: ivory, red one and four, black pips elsewhere (one face per 8 px cell)."""
    pips = {0: ((4, 4),), 1: ((2, 2), (5, 5)), 2: ((2, 2), (4, 4), (6, 6)), 3: ((2, 2), (6, 2), (2, 6), (6, 6)),
            4: ((2, 2), (6, 2), (4, 4), (2, 6), (6, 6)), 5: ((2, 2), (6, 2), (2, 4), (6, 4), (2, 6), (6, 6))}

    def p(x, y, rng, n):
        cell = (x // 8 + y // 8 * 4) % 6
        lx, ly = x % 8, y % 8
        c = shade(rgb("EEE6D2"), 0.95 + 0.06 * rng.random())
        if lx == 0 or ly == 0:
            c = shade(c, 0.85)
        for px, py in pips[cell]:
            if abs(lx - px) <= 0 and abs(ly - py) <= 0 or (abs(lx - px) + abs(ly - py) == 1 and cell in (0, 3)):
                c = rgb("B81C1C") if cell in (0, 3) else rgb("1E1A18")
        return c
    return p


def silk_gradient(a, b_, seed):
    """Sheer silk shading from one colour to another along the length, with a sheen line."""
    ca, cb = rgb(a), rgb(b_)

    def p(x, y, rng, n):
        c = mix(ca, cb, y / 31.0)
        if (x + y // 3) % 8 == 0:
            c = shade(c, 1.18)
        return shade(c, 0.96 + 0.06 * n.at(x, y, 8))
    return p


def amber_sugar(seed):
    def p(x, y, rng, n):
        c = mix(rgb("C8781A"), rgb("FFD27A"), n.at(x, y, 4))
        if (x + y) % 7 == 0:
            c = rgb("FFF0C0")
        return c
    return p


def fish_scales(seed):
    def p(x, y, rng, n):
        sx, sy = (x + (y // 3) * 2) % 4, y % 3
        back = y < 12
        c = mix(rgb("4E6E58") if back else rgb("C8D2CC"), rgb("8EA89A"), 0.3 * n.at(x, y, 4))
        if sy == 0 and sx:
            c = shade(c, 1.2)
        return c
    return p


def pastry(seed):
    """The mooncake top: golden crust, a stamped rim of petals and a square medallion."""
    def p(x, y, rng, n):
        r = math.hypot(x - 15.5, y - 15.5)
        c = mix(rgb("C88A3A"), rgb("E8B868"), n.at(x, y, 4))
        if 12 < r < 14.5 and int(math.degrees(math.atan2(y - 15.5, x - 15.5)) / 30) % 2 == 0:
            c = shade(c, 0.75)
        if abs(x - 15.5) < 6 and abs(y - 15.5) < 6 and (abs(x - 15.5) > 4.5 or abs(y - 15.5) > 4.5 or (x + y) % 4 == 0):
            c = shade(c, 0.72)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def blue_white_plate(seed):
    def p(x, y, rng, n):
        r = math.hypot(x - 15.5, y - 15.5)
        c = (240, 242, 246)
        if 13 < r < 15 or 6 < r < 7:
            c = (40, 70, 160)
        elif r < 5 and (abs(x - 15.5) < 1 or abs(y - 15.5) < 1 or abs(x - y) < 1):
            c = (60, 90, 180)
        elif 8 < r < 12 and int(math.degrees(math.atan2(y - 15.5, x - 15.5)) / 20) % 3 == 0:
            c = (70, 100, 180)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def gong_face(seed):
    def p(x, y, rng, n):
        r = math.hypot(x - 15.5, y - 15.5)
        c = mix(rgb("B08A3A"), rgb("E8C878"), 0.5 + 0.5 * math.sin(r * 1.3))
        if r < 4:
            c = shade(rgb("D8B060"), 1.1)
        if r > 14:
            c = shade(c, 0.7)
        return shade(c, 0.94 + 0.08 * n.at(x, y, 4))
    return p


def portrait(seed):
    """A painted memorial portrait: dark robe, a calm old face, white hair, a pale silk ground."""
    def p(x, y, rng, n):
        c = shade(rgb("E6DEC8"), 0.95 + 0.05 * n.at(x, y, 8))
        fx, fy = x - 15.5, y - 11
        if fx * fx / 30 + fy * fy / 42 < 1:
            c = rgb("E8C8A0")
            if fy < -3.5:
                c = rgb("DCDCD8")  # white hair
            if abs(fy) < 0.6 and abs(abs(fx) - 2.5) < 1:
                c = rgb("2A2018")  # eyes
            if abs(fy - 4) < 0.5 and abs(fx) < 1.5:
                c = rgb("8A4A3A")
        elif y > 17 and abs(fx) < 4 + (y - 17) * 0.9:
            c = rgb("2A2830") if abs(fx) > 1 else rgb("E8E4DA")
        return c
    return p


def baby_face(seed):
    def p(x, y, rng, n):
        c = rgb("F2CDB0")
        if y in (13, 14) and x in (11, 12, 19, 20):
            c = rgb("3A2A20")
        if y == 19 and 14 <= x <= 17:
            c = rgb("D07070")
        if y in (16, 17) and x in (9, 22):
            c = rgb("F4A8A0")  # rosy cheeks
        return c
    return p


def red_gold_paper(glyph, seed):
    """Red paper with a gold glyph and a gold rim (angpao, couplet, mat, cake paper)."""
    def p(x, y, rng, n):
        c = shade(rgb("C0201C"), 0.88 + 0.16 * n.at(x, y, 8))
        if x in (1, 30) or y in (1, 30):
            c = rgb("E8B848")
        if glyph and glyph_at(glyph, x, y, 4, 4, 2):
            c = mix(rgb("E8B848"), rgb("FFE8A0"), rng.random() * 0.4)
        return c
    return p


def joss_paper(seed):
    """Spirit money: coarse yellow paper with a red square stamp and a foil patch."""
    def p(x, y, rng, n):
        c = shade(rgb("D8B04A"), 0.85 + 0.2 * n.at(x, y, 4))
        if 10 <= x <= 21 and 10 <= y <= 21:
            c = rgb("E8D8A0") if 12 <= x <= 19 and 12 <= y <= 19 else c
            if 12 <= x <= 19 and 12 <= y <= 19 and (x + y) % 3 == 0:
                c = rgb("D8C070")
        if (x in (4, 27) or y in (4, 27)) and 4 <= x <= 27 and 4 <= y <= 27:
            c = rgb("B02018")
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def rabbit_paper(seed):
    def p(x, y, rng, n):
        c = mix((255, 250, 236), rgb("E8E0D0"), abs(y - 15.5) / 18.0)
        if y % 8 == 0:
            c = rgb("C8BCA8")
        if (x * 3 + y) % 11 == 0 and y > 18:
            c = rgb("E86A7A")  # painted pink speckles
        return c
    return p


def dragon_scales(seed):
    def p(x, y, rng, n):
        sx, sy = (x + (y // 4) * 2) % 4, y % 4
        c = rgb("C82020") if sy and sx else rgb("E8B848")
        if y > 24:
            c = rgb("2E8A4A") if sx else rgb("E8B848")
        return shade(c, 0.94 + 0.08 * n.at(x, y, 4))
    return p


def butterfly_wing(seed):
    def p(x, y, rng, n):
        r = math.hypot(x - 10, y - 12)
        c = rgb("E86A1A")
        if r > 13:
            c = rgb("1E1A18")
        if 14 < r < 16 and (x + y) % 4 == 0:
            c = rgb("F8F0E0")
        if abs(r - 7) < 1.2:
            c = rgb("F8D040")
        if math.hypot(x - 22, y - 22) < 4:
            c = rgb("3A6AC8")
        return c
    return p


def porridge(seed):
    def p(x, y, rng, n):
        c = mix(rgb("EEE2C8"), rgb("D8C4A0"), n.at(x, y, 4))
        if (x * 7 + y * 3) % 13 == 0:
            c = rgb("8A3A2A")  # red beans
        if (x * 5 + y * 11) % 17 == 0:
            c = rgb("C89A4A")  # lotus seeds
        return c
    return p


def soup(seed):
    def p(x, y, rng, n):
        c = mix(rgb("B8823A"), rgb("D8A858"), n.at(x, y, 4))
        if (x * 3 + y * 7) % 11 == 0:
            c = rgb("4E8A3A")  # scallion
        if math.hypot(x - 8, y - 20) < 2 or math.hypot(x - 22, y - 9) < 1.5:
            c = rgb("F0D890")  # fat beads
        return c
    return p


def twig_broom(seed):
    def p(x, y, rng, n):
        c = mix(rgb("8A6A3A"), rgb("C8A868"), 0.5 + 0.5 * math.sin(x * 2.1 + y * 0.1))
        if x % 3 == 0:
            c = shade(c, 0.7)
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def shadow_leather(base, seed):
    """Translucent puppet leather: a saturated ground cut with rows of tiny punched holes (alpha) and a dark outline."""
    b = rgb(base)

    def p(x, y, rng, n):
        c = shade(b, 0.85 + 0.25 * n.at(x, y, 8))
        if x in (0, 31) or y in (0, 31):
            return (40, 24, 16, 255)
        if (x % 4 == 2 and y % 4 == 2) or ((x + y) % 9 == 0 and y % 2 == 0):
            return (c[0], c[1], c[2], 0)
        return c
    return p


def roof_tile(seed):
    def p(x, y, rng, n):
        c = shade(rgb("5E636A"), 0.85 + 0.25 * n.at(x, y, 8))
        if y % 8 == 0:
            c = shade(c, 0.7)
        if x % 16 in (0, 15):
            c = shade(c, 1.2)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def brick(seed):
    def p(x, y, rng, n):
        c = mix(rgb("8E4A34"), rgb("B0644A"), n.at(x, y, 8))
        if y % 8 == 0 or (x + (y // 8) * 8) % 16 == 0:
            c = rgb("B8A890")
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def stitched_leather(seed):
    def p(x, y, rng, n):
        c = mix(rgb("7A4A2A"), rgb("A86E40"), n.at(x, y, 8))
        if x % 16 == 0 or y % 16 == 0:
            c = rgb("E8D8B0") if (x + y) % 2 == 0 else shade(c, 0.6)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def net_mesh(seed):
    """Hemp netting: knotted cords on a 4 px grid, the holes transparent."""
    def p(x, y, rng, n):
        on = (x + y) % 6 == 0 or (x - y) % 6 == 0
        if not on:
            return (0, 0, 0, 0)
        c = mix(rgb("8A7A58"), rgb("B8A878"), n.at(x, y, 4))
        if (x + y) % 6 == 0 and (x - y) % 6 == 0:
            c = shade(c, 0.75)  # the knot
        return c
    return p


def haw_glaze(seed):
    def p(x, y, rng, n):
        c = mix(rgb("A8101A"), rgb("E83040"), n.at(x, y, 4))
        if (x + 2 * y) % 9 == 0:
            c = rgb("FFE8D0")  # the sugar glaze catching light
        return c
    return p


def red_egg(seed):
    def p(x, y, rng, n):
        return shade(rgb("D0281C"), 0.88 + 0.2 * n.at(x, y, 8) + rng.random() * 0.04)
    return p


def nian_gao(seed):
    def p(x, y, rng, n):
        c = mix(rgb("6A3A1A"), rgb("9A5A2A"), n.at(x, y, 8))
        if (x + y) % 9 == 0:
            c = shade(c, 1.25)  # the sticky sheen
        return c
    return p


def brown_bag(seed):
    def p(x, y, rng, n):
        c = shade(rgb("B89468"), 0.9 + 0.15 * n.at(x, y, 4))
        if 10 <= x <= 21 and 12 <= y <= 23 and (x in (10, 21) or y in (12, 23)):
            c = rgb("B02018")
        if 13 <= x <= 18 and 15 <= y <= 20 and (x + y) % 2 == 0:
            c = rgb("B02018")
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def feather(base, seed):
    b = rgb(base)

    def p(x, y, rng, n):
        c = shade(b, 0.8 + 0.3 * n.at(x, y, 4))
        if (x + y * 2) % 5 == 0:
            c = shade(c, 0.75)
        return c
    return p


def tiger_tin(seed):
    def p(x, y, rng, n):
        c = rgb("E8A020")
        if (x + int(3 * math.sin(y * 0.5))) % 6 < 2:
            c = rgb("2A1A10")  # tiger stripes
        if x in (0, 31) or y in (0, 31):
            c = rgb("B02018")
        return c
    return p


def burlap(seed):
    def p(x, y, rng, n):
        c = shade(rgb("B8A27A"), 0.85 + 0.2 * n.at(x, y, 8))
        if x % 2 == 0 and y % 2 == 0:
            c = shade(c, 0.86)
        if glyph_at("mi", x, y, 4, 4, 2):
            c = rgb("A8281C")
        return c
    return p


def indigo_print(seed):
    """Indigo resist-dyed cotton: white plum blossoms on a deep blue ground."""
    def p(x, y, rng, n):
        c = shade(rgb("1E3A6A"), 0.85 + 0.2 * n.at(x, y, 8))
        cx, cy = x % 16 - 8, y % 16 - 8
        r = math.hypot(cx, cy)
        if r < 4 and abs(math.sin(math.atan2(cy, cx) * 2.5)) > 0.4:
            c = rgb("E8ECF2")
        if r < 1:
            c = rgb("1E3A6A")
        return c
    return p


def build_materials():
    mat("kayu_tua", wood("5A3A22", 401), 401)
    mat("kayu_muda", wood("9C7448", 402), 402)
    mat("kayu_merah", wood("6E2A1A", 403), 403)
    mat("kayu_hitam", wood("2A1E18", 404), 404)
    mat("bambu", bamboo(405), 405)
    mat("lak_merah", lacquer("8E1F1F", 406), 406)
    mat("lak_hitam", lacquer("1E1A1E", 407), 407)
    mat("emas", gold(408), 408)
    mat("perunggu", bronze(409), 409)
    mat("kuningan", metal("C8A048", 410), 410)
    mat("besi", metal("6A7078", 411), 411)
    mat("giok", jade(412), 412)
    mat("porselen", porcelain(413), 413)
    mat("porselen_putih", plain("F0F0EC", 0.06), 414)
    mat("piring_bw", blue_white_plate(415), 415)
    mat("tembikar_coklat", glazed("7A4A26", "3E2414", 416), 416)
    mat("tembikar_hijau", glazed("5E7A5A", "2E3E2A", 417), 417)
    mat("tanah_liat", glazed("A8683E", "7A4A2A", 418, drip=False), 418)
    mat("label_jiu", label_glyph("D8281C", "1E1A18", "jiu"), 419)
    mat("kain_merah", cloth("A82828", 420), 420)
    mat("kain_putih", cloth("E8E4DA", 421), 421)
    mat("kain_kasar", cloth("8A7A60", 422), 422)
    mat("kain_biru", cloth("2E4A7A", 423), 423)
    mat("kain_hitam", cloth("26242A", 424), 424)
    mat("kain_batik", indigo_print(425), 425)
    mat("tali", rope(426), 426)
    mat("tali_merah", plain("B4231C", 0.2), 427)
    mat("kertas", paper(428), 428)
    mat("kertas_tulis", paper(429, lines=True), 429)
    mat("kertas_kuning", joss_paper(430), 430)
    mat("kertas_fu", red_gold_paper("fu", 431), 431)
    mat("kertas_chun", red_gold_paper("chun", 432), 432)
    mat("kertas_xi", red_gold_paper("xi", 433), 433)
    mat("kertas_merah", red_gold_paper(None, 434), 434)
    mat("kulit_ular", snake_skin(435), 435)
    mat("sutra_sulam", silk_embroidery(436), 436)
    mat("cermin", mirror_face(437), 437)
    mat("dadu", dice_face(438), 438)
    mat("sutra_merah_muda", silk_gradient("E8506A", "F8B8C8", 439), 439)
    mat("sutra_merah", silk_gradient("B81820", "E85048", 440), 440)
    mat("gula", amber_sugar(441), 441)
    mat("sisik_ikan", fish_scales(442), 442)
    mat("kue", pastry(443), 443)
    mat("gong", gong_face(444), 444)
    mat("potret", portrait(445), 445)
    mat("wajah_bayi", baby_face(446), 446)
    mat("lentera_kelinci", rabbit_paper(448), 448)
    mat("sisik_naga", dragon_scales(449), 449)
    mat("sayap_kupu", butterfly_wing(450), 450)
    mat("bubur", porridge(451), 451)
    mat("sup", soup(452), 452)
    mat("lidi", twig_broom(453), 453)
    mat("wayang_merah", shadow_leather("C8401C", 454), 454)
    mat("wayang_hijau", shadow_leather("3E8A3A", 455), 455)
    mat("wayang_emas", shadow_leather("E8B040", 456), 456)
    mat("genteng", roof_tile(457), 457)
    mat("bata", brick(458), 458)
    mat("kulit_bola", stitched_leather(459), 459)
    mat("jaring", net_mesh(460), 460)
    mat("haw", haw_glaze(461), 461)
    mat("telur_merah", red_egg(462), 462)
    mat("nian_gao", nian_gao(463), 463)
    mat("kantong_kertas", brown_bag(464), 464)
    mat("bulu_ayam", feather("C86A2A", 465), 465)
    mat("bulu_burung", feather("8AB040", 466), 466)
    mat("jengger", plain("D0201C", 0.2), 467)
    mat("kuning_paruh", plain("E8B030", 0.2), 468)
    mat("kaleng_harimau", tiger_tin(469), 469)
    mat("karung", burlap(470), 470)
    mat("api", glow("FF9A30", 471), 471)
    mat("air", water(473), 473)
    mat("daun_dedalu", leaf(474), 474)
    mat("kelopak", plain("E8607A", 0.25), 475)
    mat("kelopak_muda", plain("F8C0CC", 0.2), 476)
    mat("tulang", plain("E8E0C8", 0.2), 477)
    mat("nasi", rice(478), 478)
    mat("jerami", straw(479), 479)
    mat("kulit", leather(480), 480)
    mat("teh", plain("8A5A2A", 0.2), 481)
    mat("hitam", plain("1E1A18", 0.1), 482)
    mat("mata_merah", plain("D01828", 0.1), 483)
    mat("biji_hitam", plain("2A2420", 0.15), 484)
    mat("hijau_tua", plain("2E5A3A", 0.2), 485)
    mat("tinta", plain("141210", 0.05), 486)


# ================================================================================================ shapes

def P(name):
    return Model(name, FOLDER)


def disc(m, cx, cy, cz, w, h, d, skin, glow_lvl=0, rot=None):
    """An octagonal round cuboid (a cross of two boxes plus one turned 45 degrees about y)."""
    m.c(cx, cy, cz, w, h, d * 0.7, skin, glow=glow_lvl)
    m.c(cx, cy, cz, w * 0.7, h, d, skin, glow=glow_lvl)
    m.c(cx, cy, cz, w * 0.66, h, d * 0.66, skin, rot=("y", 45), glow=glow_lvl)


def disc_z(m, cx, cy, cz, w, h, d, skin, glow_lvl=0, full=False):
    """A round plate standing in the xy plane (a gong, a mirror, a drum face): a box and the same box turned 45 about z."""
    m.c(cx, cy, cz, w * 0.82, h * 0.82, d, skin, glow=glow_lvl, full=full)
    m.c(cx, cy, cz, w * 0.82, h * 0.82, d, skin, rot=("z", 45), glow=glow_lvl, full=full)


def front(skin):
    """Overrides that put a motif on the north face only (the other faces use the element's own skin)."""
    return {"north": skin}


def only_north(skin):
    o = {s: None for s in EVERY}
    o["north"] = skin
    return o


def hoop_z(m, cx, cy, cz, dia, t, d, skin):
    """A ring standing in the xy plane: four straight bars and four 45-degree bars (eight-sided)."""
    s = dia * 0.414  # side of the octagon
    r = dia / 2 - t / 2
    m.c(cx, cy + r, cz, s, t, d, skin)
    m.c(cx, cy - r, cz, s, t, d, skin)
    m.c(cx + r, cy, cz, t, s, d, skin)
    m.c(cx - r, cy, cz, t, s, d, skin)
    k = r * 0.7071
    for sx, sy, a in ((1, 1, -45), (-1, 1, 45), (1, -1, 45), (-1, -1, -45)):
        m.c(cx + sx * k, cy + sy * k, cz, s, t, d, skin, rot=("z", a))


def hoop_y(m, cx, cy, cz, dia, h, t, skin):
    """A ring lying flat in the xz plane (the rim of a basket or a bowl, a lantern rib)."""
    s = dia * 0.414
    r = dia / 2 - t / 2
    m.c(cx, cy, cz + r, s, h, t, skin)
    m.c(cx, cy, cz - r, s, h, t, skin)
    m.c(cx + r, cy, cz, t, h, s, skin)
    m.c(cx - r, cy, cz, t, h, s, skin)
    k = r * 0.7071
    for sx, sz, a in ((1, 1, 45), (-1, 1, -45), (1, -1, -45), (-1, -1, 45)):
        m.c(cx + sx * k, cy, cz + sz * k, s, h, t, skin, rot=("y", a))


# ================================================================================================ props 170-239

def props_kebiasaan():
    # 170 guci_arak: the stoneware wine jar, held by its rope at the neck: a brown glazed belly, shoulder and neck, the red
    # cloth stopper tied with cord, a red paper label with 酒, the rope loop the fingers hook through
    m = P("guci_arak")
    disc(m, 0, -5.2, 0, 5.6, 5.0, 5.6, "tembikar_coklat")
    disc(m, 0, -8.0, 0, 4.4, 0.8, 4.4, "tembikar_coklat")
    disc(m, 0, -2.2, 0, 4.4, 1.2, 4.4, "tembikar_coklat")
    m.c(0, -1.1, 0, 2.2, 1.2, 2.2, "tembikar_coklat")
    m.c(0, -0.3, 0, 2.6, 0.5, 2.6, "tembikar_coklat")
    m.c(0, 0.3, 0, 2.8, 0.8, 2.8, "kain_merah")
    m.c(0, 0.0, 0, 3.0, 0.3, 3.0, "tali")
    m.c(0, 0.9, 0, 1.4, 0.6, 1.4, "kain_merah")
    m.c(0, -5.0, -2.85, 2.6, 2.6, 0.1, "label_jiu", rot=("z", 45), overrides=only_north("label_jiu"), full=True)
    m.c(-1.2, 1.0, 0, 0.3, 1.8, 0.3, "tali", rot=("z", -22.5))
    m.c(1.2, 1.0, 0, 0.3, 1.8, 0.3, "tali", rot=("z", 22.5))
    m.c(0, 1.8, 0, 2.0, 0.3, 0.3, "tali")

    # 171 cawan_arak: a small blue-and-white wine cup with a foot ring and the wine in it
    m = P("cawan_arak")
    m.ring(0.8, 2.4, 1.4, 2.4, 0.3, "porselen")
    m.c(0, 0.15, 0, 1.6, 0.3, 1.6, "porselen")
    m.c(0, -0.15, 0, 1.2, 0.3, 1.2, "porselen_putih")
    m.c(0, 1.05, 0, 1.8, 0.1, 1.8, "teh")

    # 172 sangkar_burung: the songbird cage carried to the park: a bamboo base, eight bars under a domed top, the hook,
    # a perch with a green-yellow white-eye and its two little food cups
    m = P("sangkar_burung")
    hoop_y(m, 0, -9.6, 0, 6.6, 0.6, 0.5, "kayu_merah")
    m.c(0, -9.9, 0, 5.4, 0.2, 5.4, "kayu_merah")
    for i in range(8):
        a = i * math.pi / 4
        m.c(math.cos(a) * 2.95, -6.2, math.sin(a) * 2.95, 0.22, 6.6, 0.22, "bambu")
    hoop_y(m, 0, -2.9, 0, 6.4, 0.4, 0.4, "bambu")
    disc(m, 0, -2.3, 0, 4.4, 0.6, 4.4, "bambu")
    disc(m, 0, -1.7, 0, 2.6, 0.6, 2.6, "kayu_merah")
    m.c(0, -0.8, 0, 0.5, 1.4, 0.5, "kuningan")
    m.c(0, 0.0, 0, 0.9, 0.3, 0.3, "kuningan")
    m.c(0, -7.0, 0, 5.4, 0.25, 0.25, "kayu_tua")
    m.c(0.4, -6.2, 0, 1.0, 1.1, 1.6, "bulu_burung")
    m.c(0.4, -5.4, -0.9, 0.8, 0.8, 0.8, "bulu_burung")
    m.c(0.4, -5.4, -1.4, 0.2, 0.2, 0.3, "kuning_paruh")
    m.c(0.4, -6.3, 1.3, 0.4, 0.3, 1.2, "bulu_burung", rot=("x", 22.5))
    m.c(-2.2, -9.2, 0, 0.8, 0.6, 0.8, "porselen")
    m.c(2.2, -9.2, 0, 0.8, 0.6, 0.8, "porselen")

    # 173 guci_jangkrik: the cricket-fighting pot: a squat dark clay jar with a fitted lid, a carved rim band and a vent
    m = P("guci_jangkrik")
    disc(m, 0, -0.6, 0, 3.6, 2.6, 3.6, "tembikar_hijau")
    disc(m, 0, 0.9, 0, 3.8, 0.4, 3.8, "tanah_liat")
    disc(m, 0, 1.3, 0, 3.4, 0.5, 3.4, "tembikar_hijau")
    m.c(0, 1.8, 0, 0.8, 0.5, 0.8, "tanah_liat")
    m.c(0, -0.2, -1.85, 1.2, 0.5, 0.1, "hitam")

    # 174 erhu: the two-string fiddle, held upright by the neck: a hexagonal sound box with the python-skin face, the long
    # neck, the curved scroll head with two pegs, the strings, and the bow threaded between them (an erhu's bow never leaves)
    m = P("erhu")
    m.c(0, -3.6, 0, 2.6, 2.6, 3.4, "kayu_hitam")
    m.c(0, -3.6, 0, 2.6, 2.6, 3.4, "kayu_hitam", rot=("z", 45))
    m.c(0, -3.6, -1.75, 2.0, 2.0, 0.1, "kulit_ular", full=True)
    m.c(0, 4.0, 0.2, 0.5, 15.0, 0.5, "kayu_hitam")
    m.c(0, 11.6, 0.6, 0.7, 1.4, 0.8, "kayu_hitam", rot=("x", -22.5))
    m.c(0, 12.6, 1.1, 0.6, 1.0, 0.6, "kayu_hitam", rot=("x", -45))
    m.c(0.7, 9.6, 0.2, 1.4, 0.3, 0.3, "kayu_muda")
    m.c(0.7, 8.6, 0.2, 1.4, 0.3, 0.3, "kayu_muda")
    m.c(-0.12, 3.4, -0.25, 0.06, 12.0, 0.06, "kertas")
    m.c(0.12, 3.4, -0.25, 0.06, 12.0, 0.06, "kertas")
    m.c(0, 3.0, -0.2, 0.7, 0.3, 0.5, "tali")
    m.c(0, -2.0, -0.6, 0.6, 0.5, 0.4, "kayu_muda")
    m.c(2.6, -1.9, -0.6, 10.0, 0.3, 0.3, "bambu")
    m.c(2.6, -2.2, -0.6, 9.6, 0.08, 0.2, "kain_putih")
    m.c(7.6, -1.9, -0.6, 0.5, 0.5, 0.5, "kayu_hitam")

    # 175 bingkai_sulam: the embroidery hoop held level before the lap: the round bamboo hoop with its screw tab, the
    # stretched silk with the half-worked peony, the needle standing in it with its red thread
    m = P("bingkai_sulam")
    hoop_y(m, 0, 0, -3, 7.4, 0.8, 0.5, "bambu")
    m.c(0, 0, -3, 6.6, 0.1, 6.6, "sutra_sulam", full=True)
    m.c(3.9, 0, -3, 0.8, 0.6, 0.6, "kayu_tua")
    m.c(1.2, 0.8, -3.6, 0.08, 1.8, 0.08, "besi")
    m.c(1.6, 0.6, -2.8, 0.06, 1.4, 0.06, "tali_merah", rot=("x", 45))

    # 176 cermin_tangan: a bronze hand mirror: the turned handle, the polished face toward the holder, the back cast with
    # a phoenix boss and a red cord tassel
    m = P("cermin_tangan")
    m.c(0, 1.6, 0, 0.8, 3.6, 0.8, "kayu_merah")
    m.c(0, 3.6, 0, 1.2, 0.6, 0.9, "perunggu")
    m.c(0, -0.4, 0, 1.0, 0.5, 1.0, "perunggu")
    disc_z(m, 0, 7.2, 0, 5.8, 5.8, 0.6, "perunggu")
    disc_z(m, 0, 7.2, 0.33, 5.0, 5.0, 0.06, "cermin", full=True)
    m.c(0, 7.2, -0.4, 1.2, 1.2, 0.3, "perunggu")
    m.c(0, -1.1, 0, 0.3, 0.8, 0.3, "tali_merah")
    m.c(0, -1.9, 0, 0.7, 1.0, 0.7, "kain_merah")

    # 177 almanak_merah: the red almanac (tong shu): sewn spine, gold title strip, dog-eared pages
    m = P("almanak_merah")
    m.c(0, 2.6, 0, 4.0, 5.6, 0.9, "kertas")
    m.c(0, 2.6, -0.5, 4.2, 5.8, 0.12, "kertas_merah", full=True)
    m.c(0, 2.6, 0.5, 4.2, 5.8, 0.12, "kertas_merah")
    m.c(-2.1, 2.6, 0, 0.12, 5.8, 1.1, "kain_merah")
    m.c(0.6, 3.4, -0.58, 1.0, 3.6, 0.05, "emas")
    for y in (0.6, 2.0, 3.4, 4.8):
        m.c(-1.6, y, -0.6, 0.12, 0.3, 0.06, "tali")

    # 178 dadu_cawan: the gambling bowl: a white bowl with three bone dice in it (sic bo)
    m = P("dadu_cawan")
    disc(m, 0, 0.0, 0, 3.0, 0.6, 3.0, "porselen")
    m.ring(1.0, 4.4, 1.4, 4.4, 0.35, "porselen")
    m.c(0, 0.4, 0, 3.8, 0.2, 3.8, "porselen_putih")
    m.c(-0.8, 1.0, -0.6, 0.9, 0.9, 0.9, "dadu", full=True)
    m.c(0.8, 1.0, -0.3, 0.9, 0.9, 0.9, "dadu", rot=("y", 22.5), full=True)
    m.c(0.0, 1.0, 0.9, 0.9, 0.9, 0.9, "dadu", rot=("y", -45), full=True)

    # 179 selendang: the dancer's long silk sash, hanging from the hand in rippling folds, a second tail behind
    m = P("selendang")
    m.c(0, 0, 0, 1.2, 1.2, 1.2, "sutra_merah_muda")
    y = -0.6
    for i, a in enumerate((22.5, -22.5, 22.5, -22.5, 22.5, -22.5, 22.5)):
        m.c(0.6 * (1 if i % 2 else -1), y - 1.6, -0.2, 1.8, 3.4, 0.12, "sutra_merah_muda", rot=("z", a))
        y -= 3.0
    y = -0.6
    for i, a in enumerate((-22.5, 22.5, -22.5, 22.5, -22.5)):
        m.c(0.5 * (1 if i % 2 else -1), y - 1.5, 0.4, 1.4, 3.2, 0.1, "sutra_merah", rot=("x", a))
        y -= 2.8

    # 180 sendok_gula: the sugar painter's copper ladle: long handle, the deep spout bowl, a thread of molten sugar falling
    m = P("sendok_gula")
    m.c(0, 2.4, 0, 0.5, 6.0, 0.5, "kayu_tua")
    m.c(0, 5.6, 0, 0.6, 0.8, 0.6, "kuningan")
    m.ring(7.0, 2.4, 1.4, 2.4, 0.3, "kuningan")
    m.c(0, 6.4, 0, 1.8, 0.2, 1.8, "kuningan")
    m.c(0, 7.5, 0, 1.8, 0.2, 1.8, "gula", glow=6)
    m.c(1.4, 7.4, 0, 0.6, 0.4, 0.6, "kuningan")
    m.c(1.7, 7.8, 0, 0.12, 0.12, 2.4, "gula", glow=8)

    # 181 permen_gula: a sugar painting on its bamboo stick: an amber dragon drawn in one line, head, coil, claws, whiskers
    m = P("permen_gula")
    m.c(0, 3.2, 0, 0.4, 8.0, 0.4, "bambu")
    m.c(0, 8.0, 0, 5.0, 0.4, 0.2, "gula", glow=5)
    m.c(-2.3, 9.8, 0, 0.4, 3.8, 0.2, "gula", glow=5)
    m.c(2.3, 9.6, 0, 0.4, 3.4, 0.2, "gula", glow=5)
    m.c(0, 11.6, 0, 4.2, 0.4, 0.2, "gula", glow=5)
    m.c(-0.6, 10.0, 0, 3.0, 0.4, 0.2, "gula", rot=("z", 45), glow=5)
    m.c(1.8, 12.6, 0, 1.8, 1.6, 0.2, "gula", glow=5)
    m.c(2.9, 13.4, 0, 1.4, 0.3, 0.2, "gula", rot=("z", 22.5), glow=5)
    m.c(2.6, 12.0, 0, 1.6, 0.25, 0.2, "gula", rot=("z", -22.5), glow=5)
    m.c(-2.8, 7.4, 0, 1.2, 0.3, 0.2, "gula", rot=("z", -45), glow=5)
    m.c(2.6, 7.2, 0, 1.2, 0.3, 0.2, "gula", rot=("z", 45), glow=5)
    m.c(1.4, 12.8, -0.15, 0.3, 0.3, 0.1, "hitam")

    # 182 bunga: a peony on its stem: three rings of petals, a pale heart, two leaves
    m = P("bunga")
    m.c(0, 3.0, 0, 0.35, 6.0, 0.35, "hijau_tua")
    m.c(0.9, 2.8, 0, 1.6, 0.2, 0.9, "daun_dedalu", rot=("z", 22.5))
    m.c(-0.9, 4.2, 0, 1.6, 0.2, 0.9, "daun_dedalu", rot=("z", -22.5))
    disc(m, 0, 6.4, 0, 3.2, 0.8, 3.2, "hijau_tua")
    disc(m, 0, 7.0, 0, 3.6, 1.0, 3.6, "kelopak")
    disc(m, 0, 7.8, 0, 2.8, 1.0, 2.8, "kelopak")
    disc(m, 0, 8.5, 0, 1.6, 0.6, 1.6, "kelopak_muda")

    # 183 buntelan: the traveller's bundle: indigo-printed cotton knotted at the top, its two ears up for the fingers
    m = P("buntelan")
    m.c(0, -3.4, 0, 5.4, 4.4, 4.6, "kain_batik")
    m.c(0, -3.4, 0, 4.6, 5.0, 3.8, "kain_batik")
    m.c(0, -0.9, 0, 2.4, 1.2, 2.0, "kain_batik")
    m.c(0, -0.2, 0, 1.2, 0.9, 1.2, "kain_biru")
    m.c(-0.9, 0.6, 0, 0.6, 1.6, 0.9, "kain_batik", rot=("z", 22.5))
    m.c(0.9, 0.6, 0, 0.6, 1.6, 0.9, "kain_batik", rot=("z", -22.5))

    # 184 galah_lentera: the lamplighter's bamboo pole: a long cane, a brass cup with the burning taper, the iron hook
    # that lifts a lantern down, a grip wrap
    m = P("galah_lentera")
    m.c(0, 4.0, 0, 0.7, 34.0, 0.7, "bambu")
    m.c(0, -0.5, 0, 0.9, 3.0, 0.9, "tali")
    m.c(0, 21.4, 0, 1.4, 0.8, 1.4, "kuningan")
    m.c(0, 22.2, 0, 0.3, 1.0, 0.3, "kertas")
    m.c(0, 23.1, 0, 0.6, 1.0, 0.6, "api", glow=15)
    m.c(0.8, 20.2, 0, 0.3, 2.4, 0.3, "besi")
    m.c(1.3, 19.2, 0, 1.0, 0.3, 0.3, "besi")
    m.c(1.7, 19.6, 0, 0.3, 0.9, 0.3, "besi")


def props_tukang():
    # 185 karung_beras: a sack of rice carried by its tied neck: burlap stamped 米, the twisted neck and cord
    m = P("karung_beras")
    m.c(0, -5.0, 0, 5.4, 6.4, 4.2, "karung", overrides=front("karung"), full=True)
    m.c(0, -8.4, 0, 4.8, 0.8, 3.6, "karung")
    m.c(0, -1.4, 0, 3.6, 1.2, 2.8, "karung")
    m.c(0, -0.4, 0, 1.6, 1.0, 1.4, "karung")
    m.c(0, -0.6, 0, 1.9, 0.3, 1.7, "tali")
    m.c(0, 0.4, 0, 1.0, 0.8, 1.0, "karung")

    # 186 ikan: a carp hung on a grass string through the gill: scaled body, tail, dorsal and pectoral fins, an eye
    m = P("ikan")
    m.c(0, -0.6, 0, 0.12, 1.6, 0.12, "jerami")
    m.c(0, -4.8, 0, 1.4, 6.6, 2.8, "sisik_ikan")
    m.c(0, -1.4, 0.2, 1.2, 1.4, 2.0, "sisik_ikan")
    m.c(0, -8.6, 0, 0.9, 1.4, 1.6, "sisik_ikan")
    m.c(0, -10.0, 0.7, 0.3, 2.0, 1.0, "sisik_ikan", rot=("x", -22.5))
    m.c(0, -10.0, -0.7, 0.3, 2.0, 1.0, "sisik_ikan", rot=("x", 22.5))
    m.c(0, -4.6, 1.8, 0.2, 4.0, 0.9, "sisik_ikan")
    m.c(0.7, -3.0, -0.8, 0.2, 1.0, 0.8, "sisik_ikan", rot=("x", 22.5))
    m.c(0.72, -1.6, -0.6, 0.06, 0.4, 0.4, "hitam")
    m.c(-0.72, -1.6, -0.6, 0.06, 0.4, 0.4, "hitam")

    # 187 tulang: a meaty soup bone for the dog: shaft with knuckle ends
    m = P("tulang")
    m.c(0, 2.4, 0, 0.9, 4.4, 0.9, "tulang")
    for y in (0.0, 4.8):
        m.c(-0.6, y, 0, 1.0, 1.0, 1.0, "tulang")
        m.c(0.6, y, 0, 1.0, 1.0, 1.0, "tulang")
    m.c(0, 2.0, 0.3, 1.0, 1.4, 0.6, "jengger")

    # 188 kue_bulan: a mooncake held flat: the stamped golden top, ribbed sides, a bite out of one edge showing the lotus
    # paste and the salted yolk
    m = P("kue_bulan")
    disc(m, 0, 0.6, -1.2, 3.6, 1.2, 3.6, "kue")
    m.c(0, 1.23, -1.2, 3.0, 0.06, 3.0, "kue", full=True)
    m.c(1.4, 0.6, -2.4, 0.9, 1.0, 0.9, "teh")
    m.c(1.3, 0.6, -2.2, 0.5, 0.5, 0.5, "kuning_paruh")

    # 189 piring_akrobat: a spinning plate on a thin bamboo stick, the plate's foot ring on the tip
    m = P("piring_akrobat")
    m.c(0, 5.0, 0, 0.35, 14.0, 0.35, "bambu")
    m.c(0, -1.5, 0, 0.6, 1.0, 0.6, "tali_merah")
    m.c(0, 12.2, 0, 1.2, 0.3, 1.2, "porselen")
    disc(m, 0, 12.5, 0, 5.4, 0.3, 5.4, "porselen_putih")
    m.c(0, 12.68, 0, 4.6, 0.06, 4.6, "piring_bw", full=True)

    # 190 gong_tangan: the night watch's hand gong hanging from its cord: the bronze face with the raised boss, the turned
    # rim, the cord loop
    m = P("gong_tangan")
    disc_z(m, 0, -4.4, 0, 6.6, 6.6, 0.5, "perunggu")
    disc_z(m, 0, -4.4, -0.28, 5.6, 5.6, 0.06, "gong", full=True)
    disc_z(m, 0, -4.4, -0.45, 1.6, 1.6, 0.4, "kuningan")
    m.c(-1.6, -0.6, 0, 0.25, 1.8, 0.25, "tali_merah", rot=("z", -22.5))
    m.c(1.6, -0.6, 0, 0.25, 1.8, 0.25, "tali_merah", rot=("z", 22.5))
    m.c(0, 0.2, 0, 1.4, 0.3, 0.3, "tali_merah")

    # 191 kentongan: the bamboo watch drum on its cord: a thick cane section with the long slit, bound ends, cord loop
    m = P("kentongan")
    m.c(0, -4.4, 0, 2.6, 7.6, 2.6, "bambu")
    m.c(0, -4.4, 0, 2.2, 7.6, 2.8, "bambu")
    m.c(0, -4.4, -1.42, 0.5, 5.2, 0.1, "hitam")
    m.c(0, -0.8, 0, 2.9, 0.5, 2.9, "tali")
    m.c(0, -8.0, 0, 2.9, 0.5, 2.9, "tali")
    m.c(0, 0.0, 0, 0.3, 1.2, 0.3, "tali")

    # 192 suona: the wedding horn: reed and brass mouthpiece at the lips, the wooden body with finger holes, the flared
    # brass bell
    m = P("suona")
    m.c(0, -4.2, 0, 0.3, 1.2, 0.3, "jerami")
    m.c(0, -3.3, 0, 0.6, 0.8, 0.6, "kuningan")
    m.c(0, -2.6, 0, 1.0, 0.4, 1.0, "kuningan")
    m.c(0, 1.0, 0, 0.9, 7.0, 0.9, "kayu_merah")
    for y in (-1.2, 0.0, 1.2, 2.4, 3.6):
        m.c(0, y, -0.46, 0.3, 0.3, 0.06, "hitam")
    disc(m, 0, 5.0, 0, 1.6, 1.0, 1.6, "kuningan")
    disc(m, 0, 6.0, 0, 2.6, 1.0, 2.6, "kuningan")
    disc(m, 0, 6.9, 0, 3.8, 0.8, 3.8, "kuningan")

    # 193 balok_kayu: the storyteller's xingmu: a dark hardwood block with a bevelled top
    m = P("balok_kayu")
    m.c(0, 0.6, 0, 3.2, 1.2, 1.6, "kayu_hitam")
    m.c(0, 1.4, 0, 2.6, 0.4, 1.2, "kayu_hitam")
    m.c(0, 1.65, 0, 1.2, 0.1, 0.6, "emas")

    # 194 genderang_kecil: the waist drum of the wedding band: a red lacquer barrel with brass studs and two skin heads,
    # a yellow strap
    m = P("genderang_kecil")
    disc(m, 0, 0, 0, 4.6, 2.6, 4.6, "lak_merah")
    disc(m, 0, 1.35, 0, 4.0, 0.1, 4.0, "kulit")
    disc(m, 0, -1.35, 0, 4.0, 0.1, 4.0, "kulit")
    hoop_y(m, 0, 1.0, 0, 4.7, 0.3, 0.2, "kuningan")
    hoop_y(m, 0, -1.0, 0, 4.7, 0.3, 0.2, "kuningan")
    m.c(2.4, 0, 0, 0.4, 0.8, 2.0, "kain_kasar")

    # 195 potret_almarhum: the memorial portrait carried at the head of the procession: a black frame, the painted face,
    # a white silk rosette and black sash over the top corners
    m = P("potret_almarhum")
    m.c(0, 0, 0, 6.0, 8.0, 0.5, "kayu_hitam")
    m.c(0, 0, -0.28, 5.0, 7.0, 0.06, "potret", full=True)
    m.c(0, 4.3, -0.4, 1.8, 1.2, 0.8, "kain_putih")
    m.c(-1.6, 3.6, -0.4, 2.4, 0.6, 0.2, "kain_hitam", rot=("z", 22.5))
    m.c(1.6, 3.6, -0.4, 2.4, 0.6, 0.2, "kain_hitam", rot=("z", -22.5))
    m.c(0, -4.2, 0, 4.0, 0.4, 0.9, "kayu_hitam")

    # 196 bayi_bedong: a newborn swaddled in red silk lying in the arms: the wrapped body, the gold-trimmed hood, the face
    m = P("bayi_bedong")
    m.c(1.0, 0.6, -2.4, 6.0, 2.8, 2.8, "kain_merah")
    m.c(1.0, 0.6, -2.4, 5.0, 3.2, 3.2, "kain_merah")
    m.c(-2.6, 0.9, -2.4, 2.0, 2.4, 2.4, "wajah_bayi", overrides={"west": "wajah_bayi"})
    m.c(-2.7, 2.0, -2.4, 2.2, 0.8, 2.8, "kain_merah")
    m.c(-1.7, 0.9, -2.4, 0.3, 3.0, 3.0, "emas")
    m.c(1.0, 0.6, -2.4, 0.4, 3.3, 3.3, "tali_merah")

    # 197 payung_merah: the red oiled-paper umbrella of a wedding or a funeral: the long shaft, the ribbed canopy, the
    # top knob, the runner
    m = P("payung_merah")
    m.c(0, 4.0, 0, 0.5, 22.0, 0.5, "bambu")
    disc(m, 0, 14.6, 0, 14.0, 0.5, 14.0, "lak_merah")
    disc(m, 0, 15.3, 0, 10.0, 0.8, 10.0, "lak_merah")
    disc(m, 0, 15.9, 0, 5.0, 0.6, 5.0, "lak_merah")
    m.c(0, 16.6, 0, 1.0, 1.0, 1.0, "kayu_hitam")
    m.c(0, 11.4, 0, 1.2, 1.4, 1.2, "kayu_tua")
    # the stretcher ribs under the canopy, from the runner out to the canopy's rim
    m.c(0, 13.0, -2.4, 0.18, 0.18, 5.0, "bambu", rot=("x", -22.5))
    m.c(0, 13.0, 2.4, 0.18, 0.18, 5.0, "bambu", rot=("x", 22.5))
    m.c(-2.4, 13.0, 0, 5.0, 0.18, 0.18, "bambu", rot=("z", 22.5))
    m.c(2.4, 13.0, 0, 5.0, 0.18, 0.18, "bambu", rot=("z", -22.5))
    m.c(0, -6.6, 0, 0.8, 1.0, 0.8, "tali_merah")

    # 198 angpao: the red envelope with a gold 福
    m = P("angpao")
    m.c(0, 1.6, 0, 2.2, 3.4, 0.2, "kertas_merah", overrides=front("kertas_fu"), full=True)
    m.c(0, 3.1, -0.12, 2.2, 0.4, 0.06, "emas")

    # 199 uang_kertas: a sheaf of spirit money: yellow joss sheets with the red stamp and the foil square, fanned
    m = P("uang_kertas")
    for i, r in enumerate((None, ("y", 22.5), ("y", -22.5))):
        m.c(0, 0.1 + i * 0.12, -1.6, 3.4, 0.08, 3.4, "kertas_kuning", rot=r, full=True)
    m.c(0, 0.5, -1.6, 3.4, 0.4, 3.4, "kertas_kuning")
    m.c(0, 0.75, -1.6, 0.4, 0.1, 3.6, "tali_merah")


def props_rombongan():
    # 200 simpai: the acrobat's hoop: an eight-sided cane ring wound with red cloth, ribbon streamers
    m = P("simpai")
    hoop_z(m, 0, 6.0, 0, 12.0, 0.7, 0.7, "bambu")
    for a in (0, 90):
        m.c(0, 0.0 if a == 0 else 12.0, 0, 1.2, 0.8, 0.8, "kain_merah")
    m.c(-5.6, 6.0, 0, 0.8, 1.2, 0.8, "kain_merah")
    m.c(5.6, 6.0, 0, 0.8, 1.2, 0.8, "kain_merah")
    m.c(0.6, -1.6, 0, 0.6, 2.4, 0.08, "sutra_merah", rot=("z", 22.5))
    m.c(-0.6, -1.6, 0, 0.6, 2.4, 0.08, "sutra_merah_muda", rot=("z", -22.5))

    # 201 kipas_tari: the yangge dance fan: red silk opened in a half circle on bamboo ribs, a gold edge, a pink silk fringe
    m = P("kipas_tari")
    m.c(0, 0.6, 0, 0.5, 2.0, 0.5, "bambu")
    m.c(0, -0.6, 0, 0.6, 0.6, 0.6, "kuningan")
    for a in (-45, -22.5, 0, 22.5, 45):
        m.c(0, 4.0, 0, 0.25, 7.0, 0.2, "bambu", rot=("z", a, (0, 0.6, 0)) if a else None)
    m.c(0, 5.0, -0.12, 8.0, 5.0, 0.06, "sutra_merah", full=True)
    m.c(-3.4, 4.4, -0.12, 3.6, 3.6, 0.06, "sutra_merah", rot=("z", 45), full=True)
    m.c(3.4, 4.4, -0.12, 3.6, 3.6, 0.06, "sutra_merah", rot=("z", -45), full=True)
    m.c(0, 7.6, -0.14, 8.0, 0.4, 0.08, "emas")
    m.c(0, 8.0, -0.16, 7.6, 0.6, 0.06, "sutra_merah_muda")

    # 202 pipa: the pear-shaped lute: a paulownia face with the sound holes and frets, the bent-back peg box, four pegs
    m = P("pipa")
    m.c(0, -3.4, 0, 4.6, 6.0, 1.2, "kayu_muda")
    m.c(0, -5.8, 0, 3.6, 2.0, 1.2, "kayu_muda")
    m.c(0, -0.4, 0, 3.0, 2.0, 1.2, "kayu_muda")
    m.c(0, 2.8, 0, 1.2, 5.0, 0.9, "kayu_merah")
    m.c(0, 5.8, 0.5, 1.2, 1.4, 0.9, "kayu_merah", rot=("x", -22.5))
    for y in (5.2, 6.0):
        m.c(0, y, 0.5, 2.4, 0.3, 0.3, "kayu_hitam")
    for y in (0.6, 1.6, 2.6, 3.6):
        m.c(0, y, -0.5, 1.0, 0.25, 0.2, "tulang")
    m.c(0, -5.2, -0.62, 1.8, 0.4, 0.1, "kayu_hitam")
    m.c(0, -1.5, -0.62, 0.06, 9.0, 0.06, "kertas")

    # 203 dizi: the bamboo flute with its finger holes, blow hole, membrane hole and a red tassel
    m = P("dizi")
    m.c(0, 4.0, 0, 0.7, 12.0, 0.7, "bambu")
    for y in (0.4, 1.4, 2.4, 4.0, 5.0, 6.0):
        m.c(0, y, -0.36, 0.3, 0.3, 0.06, "hitam")
    m.c(0, 8.6, -0.36, 0.4, 0.4, 0.06, "hitam")
    m.c(0, 7.6, -0.36, 0.3, 0.3, 0.06, "kertas")
    m.c(0, 10.1, 0, 0.9, 0.3, 0.9, "tali_merah")
    m.c(0, -2.0, 0, 0.3, 0.8, 0.3, "tali_merah")
    m.c(0, -3.0, 0, 0.7, 1.4, 0.7, "kain_merah")

    # 204 dacing: the market steelyard: the graduated beam hung from a cord at the fulcrum, the brass pan on three
    # strings at the short end, the iron counterweight sliding along the long end
    m = P("dacing")
    m.c(3.0, -1.4, 0, 14.0, 0.5, 0.5, "kayu_merah")
    for x in (1.4, 3.0, 4.6, 6.2, 7.8):
        m.c(x, -1.12, 0, 0.12, 0.08, 0.52, "kuningan")
    m.c(0, -0.4, 0, 0.2, 1.6, 0.2, "tali")
    m.c(-3.6, -1.4, 0, 0.6, 0.7, 0.7, "kuningan")
    m.c(-3.6, -3.8, -0.6, 0.12, 4.6, 0.12, "tali", rot=("x", 22.5))
    m.c(-3.6, -3.8, 0.6, 0.12, 4.6, 0.12, "tali", rot=("x", -22.5))
    disc(m, -3.6, -6.0, 0, 3.4, 0.4, 3.4, "kuningan")
    m.c(-3.6, -5.6, 0, 2.6, 0.6, 2.6, "nasi")
    m.c(7.0, -2.6, 0, 0.12, 2.0, 0.12, "tali")
    disc(m, 7.0, -4.0, 0, 1.4, 1.6, 1.4, "besi")

    # 205 kuas_besar: the great calligraphy brush: a thick bamboo handle, a horn ferrule, a wolf-hair tuft dipped in ink,
    # a hanging loop at the end
    m = P("kuas_besar")
    m.c(0, 3.0, 0, 1.0, 9.0, 1.0, "bambu")
    m.c(0, 7.8, 0, 1.3, 1.0, 1.3, "kayu_hitam")
    m.c(0, 9.4, 0, 1.6, 2.4, 1.6, "kulit")
    m.c(0, 11.2, 0, 1.0, 1.4, 1.0, "tinta")
    m.c(0, 12.2, 0, 0.5, 0.8, 0.5, "tinta")
    m.c(0, -1.8, 0, 0.3, 0.8, 0.3, "tali_merah")

    # 206 kuplet_merah: one Spring Festival couplet hanging from the fingers: a long red strip, gold characters, a pasted
    # paper head
    m = P("kuplet_merah")
    m.c(0, -6.0, 0, 2.2, 12.0, 0.1, "kertas_merah", overrides=front("kertas_chun"), full=True)
    m.c(0, -0.2, 0, 2.4, 0.5, 0.2, "kertas_merah")
    for y in (-3.2, -6.0, -8.8):
        m.c(0, y, -0.07, 1.6, 1.6, 0.04, "kertas_chun", full=True)

    # 207 ranting_dedalu: a willow switch for the Qingming door: a slender twig with drooping leaf strands
    m = P("ranting_dedalu")
    m.c(0, 3.0, 0, 0.3, 7.0, 0.3, "kayu_muda")
    m.c(0.6, 6.0, 0, 0.25, 3.0, 0.25, "kayu_muda", rot=("z", -22.5))
    for x, y, a in ((0.3, 3.4, 22.5), (-0.3, 4.6, -22.5), (0.9, 6.4, 22.5), (-0.4, 6.8, -22.5), (1.6, 7.0, 45), (0.2, 2.2, -22.5)):
        m.c(x, y - 1.4, 0, 0.5, 2.8, 0.1, "daun_dedalu", rot=("z", a))

    # 208 layang_naga: the centipede dragon kite: the big red-gold head with eyes, horns and whiskers, then a chain of
    # round scaled segments with little balance feathers, ending in a streamer tail (flown far off the hand)
    m = P("layang_naga")
    m.c(0, 0, 0, 4.4, 3.6, 2.4, "sisik_naga")
    m.c(0, -0.6, -1.4, 3.6, 1.6, 1.2, "lak_merah")
    m.c(-1.2, 0.8, -1.25, 0.8, 0.8, 0.1, "kertas")
    m.c(1.2, 0.8, -1.25, 0.8, 0.8, 0.1, "kertas")
    m.c(-1.2, 0.8, -1.32, 0.4, 0.4, 0.06, "hitam")
    m.c(1.2, 0.8, -1.32, 0.4, 0.4, 0.06, "hitam")
    m.c(-1.6, 2.6, 0.4, 0.4, 2.0, 0.4, "emas", rot=("z", 22.5))
    m.c(1.6, 2.6, 0.4, 0.4, 2.0, 0.4, "emas", rot=("z", -22.5))
    m.c(-2.8, -1.0, -1.0, 2.4, 0.15, 0.15, "emas", rot=("z", -22.5))
    m.c(2.8, -1.0, -1.0, 2.4, 0.15, 0.15, "emas", rot=("z", 22.5))
    for i in range(7):
        z = 2.4 + i * 2.2
        s = 3.4 - i * 0.25
        disc_z(m, 0, -0.2 * i, z, s, s, 0.3, "sisik_naga", full=True)
        m.c(0, -0.2 * i, z, s + 2.0, 0.15, 0.15, "bulu_burung")
    m.c(0, -1.6, 18.6, 0.5, 0.1, 3.4, "sutra_merah")
    m.c(0, -1.6, 21.6, 0.4, 0.1, 2.8, "sutra_merah_muda")

    # 209 ember_api: the fire-brigade bucket: a stitched leather pail with iron hoops, the water, the rope bail
    m = P("ember_api")
    m.ring(-4.4, 4.2, 4.4, 4.2, 0.45, "kulit")
    m.c(0, -6.5, 0, 3.6, 0.3, 3.6, "kulit")
    m.c(0, -2.8, 0, 4.4, 0.4, 4.4, "besi")
    m.c(0, -6.0, 0, 4.4, 0.4, 4.4, "besi")
    m.c(0, -2.8, 0, 3.4, 0.2, 3.4, "air")
    m.c(-1.6, -1.0, 0, 0.25, 3.0, 0.25, "tali", rot=("z", -22.5))
    m.c(1.6, -1.0, 0, 0.25, 3.0, 0.25, "tali", rot=("z", 22.5))
    m.c(0, 0.3, 0, 1.6, 0.3, 0.3, "tali")
    m.c(0, -4.4, -2.12, 1.4, 1.4, 0.06, "kertas_merah", overrides=only_north("kertas_merah"))

    # 210 keranjang_telur_merah: the basket of red eggs a father hands out after a birth: woven bamboo, a red cloth lining,
    # a heap of red eggs, the arched handle
    m = P("keranjang_telur_merah")
    m.ring(-3.0, 5.6, 2.6, 4.4, 0.4, "jerami")
    m.c(0, -4.2, 0, 5.0, 0.3, 3.8, "jerami")
    m.c(0, -1.8, 0, 4.8, 0.2, 3.6, "kain_merah")
    for x, y, z in ((-1.4, -1.2, -0.8), (0.0, -1.2, -0.9), (1.4, -1.2, -0.7), (-0.8, -1.2, 0.8), (0.7, -1.2, 0.9), (0, -0.4, 0)):
        m.c(x, y, z, 1.2, 1.4, 1.1, "telur_merah")
    m.c(-2.6, 0.0, 0, 0.35, 3.2, 0.35, "bambu")
    m.c(2.6, 0.0, 0, 0.35, 3.2, 0.35, "bambu")
    m.c(0, 1.6, 0, 5.6, 0.35, 0.35, "bambu")


def props_upacara():
    # 211 mangkuk_bubur: a coarse bowl of Laba porridge: red beans and lotus seeds, a porcelain spoon resting in it
    m = P("mangkuk_bubur")
    disc(m, 0, -0.1, -1.2, 2.4, 0.4, 2.4, "porselen_putih")
    m.ring(0.7, 3.8, 1.4, 3.8, 0.35, "porselen", cz=-1.2)
    m.c(0, 0.2, -1.2, 3.2, 0.2, 3.2, "porselen_putih")
    m.c(0, 1.1, -1.2, 3.1, 0.2, 3.1, "bubur")
    m.c(0.8, 1.5, -1.6, 0.5, 0.2, 2.6, "porselen_putih", rot=("x", -22.5))

    # 212 kendi_sup: Granny Tang's soup crock: a squat clay pot with two ear handles, the lid lifted to show the broth,
    # a curl of steam
    m = P("kendi_sup")
    disc(m, 0, -2.4, 0, 4.8, 3.4, 4.8, "tanah_liat")
    disc(m, 0, -0.5, 0, 4.0, 0.5, 4.0, "tanah_liat")
    m.c(0, -0.3, 0, 3.4, 0.1, 3.4, "sup")
    m.c(-2.8, -1.4, 0, 0.8, 1.2, 0.5, "tanah_liat")
    m.c(2.8, -1.4, 0, 0.8, 1.2, 0.5, "tanah_liat")
    m.c(1.0, 0.4, 0.8, 2.6, 0.4, 2.6, "tanah_liat", rot=("z", -22.5))
    m.c(1.0, 0.8, 0.8, 0.6, 0.4, 0.6, "tanah_liat")
    m.c(0, -4.2, 0, 3.6, 0.4, 3.6, "tembikar_coklat")

    # 213 sapu_lidi: the grave-sweeping broom: a short cane handle and a long fan of bound twigs
    m = P("sapu_lidi")
    m.c(0, 1.0, 0, 0.6, 8.0, 0.6, "bambu")
    m.c(0, 5.4, 0, 1.4, 1.0, 1.4, "tali")
    m.c(0, 8.6, 0, 2.2, 5.6, 1.4, "lidi")
    m.c(-1.0, 9.0, 0, 1.4, 5.0, 1.0, "lidi", rot=("z", 22.5))
    m.c(1.0, 9.0, 0, 1.4, 5.0, 1.0, "lidi", rot=("z", -22.5))
    m.c(0, 6.6, 0, 1.8, 0.4, 1.6, "tali")

    # 214 kotak_obat_keliling: the travelling doctor's medicine chest: black lacquer drawers with brass pulls, a carved top
    # rail, the carrying handle, a cloth flag tied on
    m = P("kotak_obat_keliling")
    m.c(0, -3.6, 0, 5.0, 6.0, 3.4, "lak_hitam")
    for y in (-1.8, -3.6, -5.4):
        m.c(0, y, -1.75, 4.4, 1.6, 0.1, "kayu_merah")
        m.c(0, y, -1.85, 0.6, 0.4, 0.2, "kuningan")
    m.c(0, -0.4, 0, 5.4, 0.5, 3.8, "kayu_merah")
    m.c(-2.2, 0.6, 0, 0.4, 1.6, 0.4, "kuningan")
    m.c(2.2, 0.6, 0, 0.4, 1.6, 0.4, "kuningan")
    m.c(0, 1.3, 0, 4.8, 0.4, 0.4, "kuningan")
    m.c(2.7, -2.0, 0, 0.1, 2.6, 1.6, "kain_putih")

    # 215 plester_harimau: a tin of tiger balm: the round orange lid with the striped tiger, a red rim
    m = P("plester_harimau")
    disc(m, 0, 0.3, -0.6, 2.4, 0.6, 2.4, "kaleng_harimau")
    m.c(0, 0.64, -0.6, 2.0, 0.06, 2.0, "kaleng_harimau", full=True)
    disc(m, 0, -0.1, -0.6, 2.2, 0.3, 2.2, "lak_merah")

    # 216 wayang_tongkat: a shadow puppet on its control rod: the punched leather general (headdress, robed body, jointed
    # arm with its own thin rod), coloured leather showing through
    m = P("wayang_tongkat")
    m.c(0, 3.0, 0, 0.3, 9.0, 0.3, "bambu")
    m.c(0, 9.0, 0, 2.0, 4.8, 0.1, "wayang_merah", full=True)
    m.c(0, 12.4, 0, 1.6, 1.8, 0.1, "wayang_emas", full=True)
    m.c(0, 13.8, 0, 2.2, 1.0, 0.1, "wayang_hijau", full=True)
    m.c(0.6, 14.6, 0, 0.3, 1.2, 0.1, "wayang_emas", rot=("z", -22.5))
    m.c(0, 6.4, 0, 2.8, 1.2, 0.1, "wayang_hijau", full=True)
    m.c(-1.6, 9.6, 0, 0.6, 3.0, 0.1, "wayang_emas", rot=("z", 22.5), full=True)
    m.c(1.8, 8.6, 0, 0.6, 3.0, 0.1, "wayang_emas", rot=("z", -45), full=True)
    m.c(3.2, 6.2, -0.2, 0.12, 6.0, 0.12, "kayu_muda", rot=("z", 22.5))

    # 217 cawan_bambu: the dice shaker: a lacquered bamboo cup with a red rim band
    m = P("cawan_bambu")
    m.ring(0.4, 2.8, 3.6, 2.8, 0.35, "bambu")
    m.c(0, 2.2, 0, 2.8, 0.2, 2.8, "bambu")
    m.c(0, -1.0, 0, 3.0, 0.5, 3.0, "lak_merah")
    m.c(0, 1.2, 0, 3.0, 0.3, 3.0, "kuningan")

    # 218 surat_utang: a folded IOU with brushed columns and the debtor's red thumbprint
    m = P("surat_utang")
    m.c(0, 1.8, 0, 3.0, 3.8, 0.1, "kertas_tulis", full=True)
    m.c(0, 0.2, -0.05, 3.0, 0.6, 0.1, "kertas")
    m.c(0.8, 0.9, -0.08, 0.6, 0.7, 0.04, "mata_merah")

    # 219 kwitansi: a small receipt slip with a square red seal
    m = P("kwitansi")
    m.c(0, 1.2, 0, 2.0, 2.6, 0.08, "kertas_tulis", full=True)
    m.c(0.4, 0.4, -0.06, 0.7, 0.7, 0.04, "mata_merah")

    # 220 cap_pejabat: the official's seal: a square jade base, a crouching lion knob, a red silk tassel
    m = P("cap_pejabat")
    m.c(0, 0.6, -0.8, 2.4, 1.6, 2.4, "giok")
    m.c(0, -0.3, -0.8, 2.5, 0.2, 2.5, "mata_merah")
    m.c(0, 1.8, -0.8, 1.6, 0.9, 2.0, "giok")
    m.c(0, 2.6, -1.4, 1.0, 1.0, 0.9, "giok")
    m.c(0, 2.4, 0.2, 0.5, 0.8, 0.4, "giok")
    m.c(0, 1.8, 0.5, 0.3, 1.6, 0.3, "tali_merah")
    m.c(0, 0.6, 0.6, 0.6, 1.2, 0.6, "kain_merah")

    # 221 gulungan_perkara: the rolled case file: a scroll on ivory rollers tied with a red silk cord, a hanging case tag
    m = P("gulungan_perkara")
    m.c(0, 2.8, 0, 1.4, 6.6, 1.4, "kertas_tulis")
    m.c(0, -0.7, 0, 1.0, 0.6, 1.0, "tulang")
    m.c(0, 6.3, 0, 1.0, 0.6, 1.0, "tulang")
    m.c(0, 2.8, 0, 1.6, 0.4, 1.6, "tali_merah")
    m.c(0.6, 1.6, -0.9, 0.8, 1.4, 0.06, "kertas")

    # 222 pita_merah: the ribbon-cutting rosette: a big red silk flower ball with ribbon tails to both sides
    m = P("pita_merah")
    disc(m, 0, 0, 0, 3.2, 3.0, 3.2, "sutra_merah")
    m.c(0, 0, 0, 3.6, 1.4, 1.4, "sutra_merah")
    m.c(0, 0, 0, 1.4, 1.4, 3.6, "sutra_merah")
    m.c(0, 0, -1.7, 1.0, 1.0, 0.3, "emas")
    m.c(-4.0, -0.6, 0, 5.0, 0.8, 0.1, "sutra_merah", rot=("z", 22.5))
    m.c(4.0, -0.6, 0, 5.0, 0.8, 0.1, "sutra_merah", rot=("z", -22.5))

    # 223 gunting_besar: the ceremonial shears: long polished blades, a brass pivot, gilded loop handles bound in red
    m = P("gunting_besar")
    m.c(-0.35, 6.0, 0, 0.5, 8.0, 0.25, "besi", rot=("z", -22.5, (0, 2.0, 0)))
    m.c(0.35, 6.0, 0, 0.5, 8.0, 0.25, "besi", rot=("z", 22.5, (0, 2.0, 0)))
    m.c(0, 2.0, 0, 0.6, 0.6, 0.6, "kuningan")
    m.c(-0.9, 0.4, 0, 1.6, 1.6, 0.3, "emas", rot=("z", 45))
    m.c(0.9, 0.4, 0, 1.6, 1.6, 0.3, "emas", rot=("z", 45))
    m.c(-0.9, 0.4, 0, 0.8, 0.8, 0.32, "tali_merah", rot=("z", 45))
    m.c(0.9, 0.4, 0, 0.8, 0.8, 0.32, "tali_merah", rot=("z", 45))


def props_bangun():
    # 224 papan_patok: a surveyor's stake driven into the lot: a pointed post standing in the ground, a white board with
    # the plot number, a red rag tied on top
    m = P("papan_patok")
    m.c(0, 3.0, 0, 1.0, 6.0, 1.0, "kayu_muda")
    m.c(0, -0.2, 0, 0.6, 0.6, 0.6, "kayu_muda")
    m.c(0, 6.6, -0.6, 3.6, 2.2, 0.3, "kayu_muda")
    m.c(0, 6.6, -0.78, 3.0, 1.6, 0.06, "kertas_tulis", full=True)
    m.c(0, 6.2, 0, 1.2, 0.6, 1.2, "kayu_tua")
    m.c(0.4, 7.9, 0.4, 0.8, 1.4, 0.1, "kain_merah", rot=("z", -22.5))
    m.c(0, 0.15, 0, 2.4, 0.3, 2.4, "tali")

    # 225 balok_pikul: a squared pine beam carried front to back on the shoulder, axe-marked, a rope sling
    m = P("balok_pikul")
    m.c(0, 0, 8.0, 2.4, 2.4, 24.0, "kayu_muda")
    m.c(0, 1.21, 8.0, 2.0, 0.06, 23.0, "kayu_tua")
    m.c(0, 0, -3.95, 2.0, 2.0, 0.1, "kayu_tua")
    m.c(0, 0, 19.95, 2.0, 2.0, 0.1, "kayu_tua")
    m.c(0, 0, 4.0, 2.6, 2.6, 0.5, "tali")

    # 226 genteng_tumpuk: a stack of grey roof tiles carried in one arm: alternating pan tiles shown as two tilted halves,
    # a cover tile on top, a tie of straw rope
    m = P("genteng_tumpuk")
    for i in range(4):
        y = -2.6 + i * 0.9
        m.c(-0.9, y, -2.0, 2.0, 0.5, 4.2, "genteng", rot=("z", -22.5))
        m.c(0.9, y, -2.0, 2.0, 0.5, 4.2, "genteng", rot=("z", 22.5))
    m.c(0, 1.0, -2.0, 1.6, 0.8, 4.2, "genteng")
    m.c(0, -1.0, -2.0, 3.8, 3.8, 0.4, "jerami")

    # 227 bata_tumpuk: a hod of fired bricks: six bricks in two layers, a wooden carrying board under them
    m = P("bata_tumpuk")
    m.c(0, -3.6, -1.8, 5.0, 0.4, 3.0, "kayu_tua")
    for i, (x, y, z) in enumerate(((-1.3, -2.8, -2.5), (1.3, -2.8, -2.5), (-1.3, -2.8, -1.1), (1.3, -2.8, -1.1),
                                   (0, -1.6, -2.5), (0, -1.6, -1.1))):
        m.c(x, y, z, 2.4, 1.1, 1.3, "bata", rot=("y", 22.5) if i == 5 else None)
    m.c(-2.4, -1.4, -1.8, 0.3, 4.6, 0.3, "kayu_tua")

    # 228 bola_cuju: the cuju ball: stitched leather panels round a feather bladder, a seam cross
    m = P("bola_cuju")
    disc(m, 0, 1.6, 0, 3.2, 3.2, 3.2, "kulit_bola")
    m.c(0, 1.6, 0, 3.4, 0.3, 3.4, "kulit_bola")
    m.c(0, 1.6, 0, 0.3, 3.4, 3.4, "kertas")
    m.c(0, 1.6, 0, 3.4, 3.4, 0.3, "kulit_bola")

    # 229 kerincing_bayi: the rattle drum (bolang gu): a little red drum on a stick, two beads on cords that strike it
    m = P("kerincing_bayi")
    m.c(0, 2.0, 0, 0.35, 4.0, 0.35, "kayu_merah")
    disc_z(m, 0, 5.0, 0, 2.6, 2.6, 1.2, "lak_merah")
    disc_z(m, 0, 5.0, -0.62, 2.2, 2.2, 0.06, "kulit", full=True)
    disc_z(m, 0, 5.0, 0.62, 2.2, 2.2, 0.06, "kertas_fu", full=True)
    m.c(-1.9, 5.0, 0, 1.4, 0.1, 0.1, "tali_merah")
    m.c(1.9, 5.0, 0, 1.4, 0.1, 0.1, "tali_merah")
    m.c(-2.7, 5.0, 0, 0.5, 0.5, 0.5, "biji_hitam")
    m.c(2.7, 5.0, 0, 0.5, 0.5, 0.5, "biji_hitam")
    m.c(0, 6.5, 0, 0.6, 0.6, 0.6, "emas")

    # 230 tikar_zhuazhou: the first-birthday choosing mat laid before the baby: a red mat with a gold border and the
    # traditional objects: book, brush, abacus, coin, seal, little sword, ruler and a spring onion
    m = P("tikar_zhuazhou")
    m.c(0, 0.15, 0, 14.0, 0.3, 10.0, "kertas_merah", full=True)
    m.c(0, 0.32, 0, 12.0, 0.06, 8.0, "kertas_xi", full=True)
    m.c(-4.6, 0.6, -2.6, 2.4, 0.5, 3.0, "kertas_merah")
    m.c(-4.6, 0.6, -2.6, 2.2, 0.55, 2.8, "kertas")
    m.c(-1.4, 0.5, -3.0, 3.6, 0.3, 0.3, "bambu", rot=("y", 22.5))
    m.c(-1.0, 0.5, -1.7, 0.6, 0.35, 0.6, "tinta")
    m.c(2.6, 0.6, -2.8, 3.4, 0.5, 1.8, "kayu_merah")
    for x in (1.4, 2.2, 3.0, 3.8):
        m.c(x, 0.92, -2.8, 0.4, 0.2, 1.4, "kayu_hitam")
    disc(m, 5.2, 0.45, 0.6, 1.4, 0.3, 1.4, "kuningan")
    m.c(5.2, 0.62, 0.6, 0.4, 0.06, 0.4, "hitam")
    m.c(-4.8, 0.9, 2.4, 1.4, 1.2, 1.4, "giok")
    m.c(-4.8, 1.7, 2.4, 0.6, 0.5, 0.6, "giok")
    m.c(1.0, 0.5, 2.8, 5.0, 0.3, 0.5, "kayu_muda")
    m.c(-1.6, 0.5, 2.8, 0.4, 0.6, 1.2, "kuningan")
    m.c(3.6, 0.5, 0.8, 0.3, 0.3, 3.0, "daun_dedalu", rot=("y", -22.5))

    # 231 lentera_kelinci: the rabbit lantern of the Mid-Autumn night, hung from a short stick: white paper body lit from
    # inside, ears, red eyes, a tail puff, little wheels, a candle glow
    m = P("lentera_kelinci")
    m.c(0, 0.0, 0, 0.3, 1.6, 0.3, "tali")
    m.c(0, -3.8, 0, 3.4, 3.4, 5.4, "lentera_kelinci", glow=12)
    m.c(0, -3.8, 0, 3.8, 2.8, 4.6, "lentera_kelinci", glow=12)
    m.c(0, -2.6, -2.8, 2.4, 2.4, 2.0, "lentera_kelinci", glow=12)
    m.c(-0.6, -0.6, -2.4, 0.6, 2.6, 0.3, "lentera_kelinci", rot=("x", -22.5), glow=10)
    m.c(0.6, -0.6, -2.4, 0.6, 2.6, 0.3, "lentera_kelinci", rot=("x", -22.5), glow=10)
    m.c(-0.6, -0.6, -2.55, 0.3, 2.0, 0.06, "kelopak", rot=("x", -22.5))
    m.c(0.6, -0.6, -2.55, 0.3, 2.0, 0.06, "kelopak", rot=("x", -22.5))
    m.c(-0.9, -2.4, -3.85, 0.4, 0.4, 0.06, "mata_merah", glow=12)
    m.c(0.9, -2.4, -3.85, 0.4, 0.4, 0.06, "mata_merah", glow=12)
    m.c(0, -3.4, 2.9, 1.0, 1.0, 0.6, "kain_putih")
    for z in (-1.8, 1.8):
        m.c(-1.95, -5.8, z, 0.3, 1.0, 1.0, "kayu_merah")
        m.c(1.95, -5.8, z, 0.3, 1.0, 1.0, "kayu_merah")
    m.c(0, -1.0, 0, 0.6, 0.4, 0.6, "kayu_tua")

    # 232 layang_kupu: a butterfly kite: two painted silk wing pairs on a bamboo cross, the body, antennae, tail strings
    m = P("layang_kupu")
    m.c(0, 0, 0, 0.6, 6.0, 0.6, "kayu_hitam")
    m.c(-3.2, 1.2, 0.1, 6.0, 4.0, 0.1, "sayap_kupu", full=True)
    m.c(3.2, 1.2, 0.1, 6.0, 4.0, 0.1, "sayap_kupu", full=True)
    m.c(-2.2, -2.0, 0.1, 3.6, 3.0, 0.1, "sayap_kupu", rot=("z", 22.5), full=True)
    m.c(2.2, -2.0, 0.1, 3.6, 3.0, 0.1, "sayap_kupu", rot=("z", -22.5), full=True)
    m.c(0, 1.0, 0.25, 12.0, 0.25, 0.2, "bambu")
    m.c(-0.6, 3.8, 0, 0.12, 2.0, 0.12, "hitam", rot=("z", 22.5))
    m.c(0.6, 3.8, 0, 0.12, 2.0, 0.12, "hitam", rot=("z", -22.5))
    m.c(-0.8, -5.4, 0, 0.3, 4.0, 0.06, "sutra_merah", rot=("z", 22.5))
    m.c(0.8, -5.4, 0, 0.3, 4.0, 0.06, "sutra_merah", rot=("z", -22.5))

    # 233 kue_keranjang: a New Year sticky cake (nian gao) on red paper stamped 福, a jujube on top
    m = P("kue_keranjang")
    disc(m, 0, 0.3, -1.4, 4.2, 0.3, 4.2, "kertas_merah")
    disc(m, 0, 1.2, -1.4, 3.6, 1.6, 3.6, "nian_gao")
    m.c(0, 2.04, -1.4, 2.0, 0.06, 2.0, "kertas_fu", full=True)
    m.c(0.6, 2.3, -1.0, 0.6, 0.5, 0.6, "lak_merah")

    # 234 tanghulu: candied haws on a bamboo skewer: six glazed red fruits, a sesame-flecked crown of sugar
    m = P("tanghulu")
    m.c(0, 4.0, 0, 0.3, 10.0, 0.3, "bambu")
    for i in range(6):
        y = 3.8 + i * 1.3
        m.c(0, y, 0, 1.3, 1.2, 1.3, "haw", rot=("y", 45) if i % 2 else None)
    m.c(0, 11.0, 0, 0.9, 0.5, 0.9, "gula", glow=3)

    # 235 kantong_kue: a paper bag of pastries from the cake shop: the folded top, the red shop stamp, a string
    m = P("kantong_kue")
    m.c(0, -2.4, 0, 3.4, 4.4, 2.2, "kantong_kertas", overrides=front("kantong_kertas"), full=True)
    m.c(0, -0.1, 0, 3.0, 0.5, 1.6, "kantong_kertas")
    m.c(0, 0.3, 0, 0.3, 0.6, 2.4, "tali_merah")
    m.c(0, -2.4, 0, 3.5, 0.2, 2.3, "tali_merah")

    # 236 tempayan_air: the water jar a village woman carries on her head: a straw ring cushion, the round-bellied jar,
    # the water at its mouth
    m = P("tempayan_air")
    hoop_y(m, 0, 0.3, 0, 4.4, 0.6, 0.8, "jerami")
    disc(m, 0, 1.0, 0, 3.0, 0.8, 3.0, "tanah_liat")
    disc(m, 0, 3.6, 0, 6.2, 4.6, 6.2, "tanah_liat")
    disc(m, 0, 6.3, 0, 4.4, 1.0, 4.4, "tanah_liat")
    disc(m, 0, 7.2, 0, 3.0, 0.8, 3.0, "tanah_liat")
    m.c(0, 7.5, 0, 2.2, 0.2, 2.2, "air")
    hoop_y(m, 0, 4.4, 0, 6.6, 0.4, 0.2, "tembikar_coklat")

    # 237 jaring_ikan: the cast net gathered over the arm: a cone of knotted netting, the lead-weighted skirt, the hand line
    m = P("jaring_ikan")
    m.c(0, 0, 0, 0.6, 1.2, 0.6, "tali")
    m.c(0, -3.0, 0, 2.6, 5.0, 2.6, "jaring", full=True)
    m.c(0, -5.4, 0, 4.0, 2.0, 4.0, "jaring", full=True)
    m.c(0, -5.4, 0, 3.2, 2.0, 4.6, "jaring", rot=("y", 45), full=True)
    for i in range(8):
        a = i * math.pi / 4
        m.c(math.cos(a) * 2.2, -6.6, math.sin(a) * 2.2, 0.5, 0.5, 0.5, "besi")
    m.c(0, -0.8, 0, 0.3, 1.0, 0.3, "tali")

    # 238 sangkar_ayam: a woven bamboo hen basket carried to market: the domed cage, its carrying loop, a brown hen
    # peering out with her red comb
    m = P("sangkar_ayam")
    m.ring(-5.0, 7.0, 5.0, 6.0, 0.4, "jerami")
    m.c(0, -7.6, 0, 6.6, 0.3, 5.6, "jerami")
    disc(m, 0, -2.1, 0, 6.2, 1.0, 5.4, "jerami")
    disc(m, 0, -1.3, 0, 3.6, 0.8, 3.0, "jerami")
    m.c(-1.0, 0.2, 0, 0.4, 2.6, 0.4, "bambu")
    m.c(1.0, 0.2, 0, 0.4, 2.6, 0.4, "bambu")
    m.c(0, 1.4, 0, 2.4, 0.4, 0.4, "bambu")
    m.c(0, -4.8, -0.4, 3.6, 3.0, 3.6, "bulu_ayam")
    m.c(0, -3.4, -2.7, 1.4, 1.6, 1.4, "bulu_ayam")
    m.c(0, -2.4, -2.7, 0.3, 0.8, 1.0, "jengger")
    m.c(0, -3.4, -3.6, 0.4, 0.3, 0.6, "kuning_paruh")
    m.c(0, -4.1, -3.4, 0.3, 0.6, 0.3, "jengger")
    m.c(0, -4.2, 2.0, 2.0, 2.4, 1.0, "bulu_ayam", rot=("x", -22.5))
    for x in (-2.2, 0.0, 2.2):
        m.c(x, -5.0, -3.05, 0.4, 5.0, 0.2, "bambu")

    # 239 nampan_teh_besar: the tea house's big tray carried in both hands: black lacquer with a red border, a teapot, four
    # cups, a plate of melon seeds
    m = P("nampan_teh_besar")
    m.c(0, 0, 0, 10.0, 0.4, 7.0, "lak_hitam")
    m.c(0, 0.4, -3.35, 10.0, 0.5, 0.3, "lak_merah")
    m.c(0, 0.4, 3.35, 10.0, 0.5, 0.3, "lak_merah")
    m.c(-4.85, 0.4, 0, 0.3, 0.5, 6.4, "lak_merah")
    m.c(4.85, 0.4, 0, 0.3, 0.5, 6.4, "lak_merah")
    disc(m, -2.4, 1.5, 0.4, 2.8, 2.2, 2.8, "porselen")
    m.c(-2.4, 2.8, 0.4, 1.4, 0.4, 1.4, "porselen")
    m.c(-2.4, 3.2, 0.4, 0.5, 0.4, 0.5, "porselen_putih")
    m.c(-4.0, 1.9, 0.4, 0.4, 0.4, 1.6, "porselen", rot=("z", 45))
    m.c(-0.8, 1.6, 0.4, 0.6, 1.2, 0.4, "porselen")
    for x, z in ((1.2, -1.6), (2.8, -1.6), (1.2, 1.6), (2.8, 1.6)):
        m.c(x, 0.85, z, 1.2, 1.0, 1.2, "porselen")
        m.c(x, 1.32, z, 0.9, 0.06, 0.9, "teh")
    disc(m, -2.6, 0.35, -2.2, 2.0, 0.3, 2.0, "porselen_putih")
    m.c(-2.6, 0.6, -2.2, 1.4, 0.3, 1.4, "biji_hitam")


def build_props():
    props_kebiasaan()
    props_tukang()
    props_rombongan()
    props_upacara()
    props_bangun()


# ================================================================================================ output

def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    names = names_from_java()
    if len(names) != 70:
        raise SystemExit("KotaGerakId.PROP_NAMA: expected 70 names, found %d" % len(names))
    build_materials()
    build_props()
    mine = {mid: m for (folder, mid), m in T.MODELS.items() if folder == FOLDER}
    errors = 0
    for name in names:
        if name not in mine:
            print("MISSING model", name)
            errors += 1
    for name in mine:
        if name not in names:
            print("EXTRA model", name)
            errors += 1
    used = set()
    for name, m in sorted(mine.items()):
        problems = validate(m, MAT)
        if problems:
            errors += 1
            print("MODEL %s:" % name)
            for p in problems:
                print("   ", p)
        used.update(m.tex)
        write_json(os.path.join(MODEL_DIR, name + ".json"), model_json(m, TEX_FOLDER))
        write_json(os.path.join(ITEM_DIR, name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "kultivator:item/fx/kota42/%s" % name}})
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        MAT[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(MAT) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    print("props %d, textures %d, elements max %d, errors %d" % (len(mine), len(used), max(len(m.els) for m in mine.values()), errors))
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        sheet({n: MAT[n] for n in used}, os.path.join(preview_dir, "kota42_prop_bahan.png"))
        GM.MAT = MAT
        GM.iso({n: mine[n] for n in names}, os.path.join(preview_dir, "kota42_prop_model.png"))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
