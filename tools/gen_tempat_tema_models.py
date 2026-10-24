"""Generates the item side of the themed towns (DESIGN 38.T.8.1 props 41-62, 38.T.3-38.T.7 items, 38.T.10.1 trade goods;
module 38TV-M). Everything is ours: cuboid item models, procedural textures and hand-placed pixel icons, nothing is
downloaded.

Writes (assets/kultivator/...):
  textures/item/fx/tempat/tema/*.png      32 x 32 material textures of the cuboid models below
  models/item/fx/tempat/tema/<id>.json    hand props 41-62 (PropId / PropKatalog 40G: kultivator:fx/tempat/tema/<id>)
  items/fx/tempat/tema/<id>.json          their item definitions
  textures/item/tema/<id>.png             16 x 16 icons of the kultivator:tema/* items (TemaItems, 38TK)
  textures/item/dagang/<id>.png           16 x 16 icons of the kultivator:dagang/* trade goods (DagangItems, 38TK)
  models/item/{tema,dagang}/<id>.json     item/generated models, or cuboid models for the sealed trade chest
                                          (dagang/peti) and the two head items (tema/kerudung_pasir, tema/topeng_tanpa_wajah)
  items/{tema,dagang}/<id>.json           item definitions

Prop rules are 38MD's (tools/gen_tempat_models.py, validate()): anchor (8, 8, 8) = the grip, +y runs away from the fist
along the handle, the front faces north (-z), elements within -16..32, uv within 0..16, at most 40 elements, a rotation
is one axis by +-22.5 or +-45 degrees. Head items: identity "head" display; CustomHeadLayer centres the model on the
head and scales it 0.625, so a shell 14 units wide sits just outside the 8 px head (8 px = 12.8 units).

Icons: 16 x 16 at vanilla density, light from the top left, 1 px dark outline, highlights; each is drawn from
primitives below by its own small function (no placeholders).

Run with: python tools/gen_tempat_tema_models.py [preview_dir]   (validates, writes; 8x icon sheets into preview_dir)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tempat_models as g  # noqa: E402  (38MD's builder, painters and validator; read-only reuse)
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
NS = "kultivator"
MAT_DIR = os.path.join(ROOT, "textures", "item", "fx", "tempat", "tema")
PROP_MODEL_DIR = os.path.join(ROOT, "models", "item", "fx", "tempat", "tema")
PROP_ITEM_DIR = os.path.join(ROOT, "items", "fx", "tempat", "tema")

rgb, mix, shade = g.rgb, g.mix, g.shade

# ================================================================================================ materials (32 x 32)

MAT = {}


def mat(name, fn, seed):
    g.tex(name, fn, seed, store=MAT)


def net_tex(seed):
    def p(x, y, rng, n):
        c = shade(rgb("4A4A3A"), 0.6 + 0.4 * n.at(x, y, 8))
        if (x + y) % 4 == 0 or (x - y) % 4 == 0:
            c = shade(rgb("B8B090"), 0.8 + 0.3 * n.at(x, y, 4))
        return c
    return p


def fish_tex(seed):
    def p(x, y, rng, n):
        c = mix(rgb("8AA0B0"), rgb("D8E4EC"), n.at(x, y, 8))
        if (x + (y // 4) * 2) % 4 == 0:
            c = shade(c, 0.78)
        if y % 32 < 6:
            c = mix(c, rgb("4A5A6A"), 0.6)
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def petal_tex(seed):
    def p(x, y, rng, n):
        c = mix(rgb("F0A0B8"), rgb("FFF0F4"), (y % 32) / 31.0)
        if x % 8 == 4:
            c = shade(c, 0.88)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def poison_tex(seed):
    def p(x, y, rng, n):
        c = mix(rgb("1E3A1E"), rgb("5AC048"), n.at(x, y, 4))
        if rng.random() < 0.05:
            c = (200, 255, 160)
        return c
    return p


def chain_tex(seed):
    def p(x, y, rng, n):
        k, v = x % 8, y % 4
        face = (x // 8) % 2 == 0
        b = rgb("5A5C62")
        if face:
            ring = (k in (0, 7) and v in (1, 2)) or (v in (0, 3) and 1 <= k <= 6)
            return shade(b, 1.25 if ring else 0.35)
        return shade(b, 1.1 if v in (1, 2) else 0.4)
    return p


def silk_tex(base, figure, seed):
    def p(x, y, rng, n):
        c = shade(rgb(base), 0.9 + 0.12 * ((x + y) % 3) / 2.0)
        u, v = x % 16, y % 16
        if abs(math.hypot(u - 8, v - 8) - 4) < 0.8 and u < 11:
            c = rgb(figure)
        return c
    return p


def palette_paint(seed):
    def p(x, y, rng, n):
        cols = ("C81E1E", "2E5E8C", "E0B040", "2E7A4A")
        return shade(rgb(cols[(x // 8 + y // 8) % 4]), 0.9 + 0.15 * n.at(x, y, 4))
    return p


def build_materials():
    mat("kayu_tua", g.wood("5A3A22", 1101), 1101)
    mat("kayu_muda", g.wood("9C7448", 1102), 1102)
    mat("kayu_merah", g.wood("7A2E1E", 1103), 1103)
    mat("bambu", g.bamboo(1104), 1104)
    mat("lak_merah", g.lacquer("8E1F1F", 1105), 1105)
    mat("lak_hitam", g.lacquer("1E1A1E", 1106), 1106)
    mat("besi", g.metal("7C8288", 1107), 1107)
    mat("besi_gelap", g.metal("44484E", 1108), 1108)
    mat("perunggu", g.bronze(1109), 1109)
    mat("emas", g.gold(1110), 1110)
    mat("giok", g.jade(1111), 1111)
    mat("kertas", g.paper(1112), 1112)
    mat("kain_merah", g.cloth("A82828", 1113), 1113)
    mat("kain_kasar", g.cloth("8A7A60", 1114), 1114)
    mat("kain_putih", g.cloth("E8E4DA", 1115), 1115)
    mat("kain_pasir", g.cloth("C8A878", 1116), 1116)
    mat("kain_hitam", g.cloth("2A2426", 1117), 1117)
    mat("jerami", g.straw(1118), 1118)
    mat("ijuk", g.fibre(1119), 1119)
    mat("kulit", g.leather(1120), 1120)
    mat("tali", g.rope(1121), 1121)
    mat("daun", g.leaf(1122), 1122)
    mat("teh", g.tea(1123), 1123)
    mat("porselen", g.porcelain(1124), 1124)
    mat("dupa", g.incense(1125), 1125)
    mat("api", g.glow("FF9A30", 1126), 1126)
    mat("lilin", g.glow("FFF0C0", 1127), 1127)
    mat("tanah_liat", g.wood("A0603A", 1128, rings=0.3), 1128)
    mat("jala", net_tex(1129), 1129)
    mat("ikan", fish_tex(1130), 1130)
    mat("kelopak", petal_tex(1131), 1131)
    mat("racun", poison_tex(1132), 1132)
    mat("rantai", chain_tex(1133), 1133)
    mat("sutra_biru", silk_tex("3A5A8C", "EDE8DC", 1134), 1134)
    mat("sutra_merah", silk_tex("A82020", "E0B040", 1135), 1135)
    mat("cat_palet", palette_paint(1136), 1136)
    mat("tulang", g.stone(1137, "D8CFB8"), 1137)


# ================================================================================================ props 41-62 (38.T.8.1)

MODELS = []


def prop(name):
    m = g.Model(name, "tempat/tema")
    MODELS.append(m)
    return m


def build_props():
    # 41 dayung_yuloh: the stern scull, a 40 px pole with the hand peg at the grip and the 12 px blade at the far end
    m = prop("dayung_yuloh")
    m.dy = -8.0
    m.c(0, 12.0, 0, 1.0, 40.0, 1.0, "kayu_muda")
    m.c(0, 26.0, 0, 0.8, 12.0, 6.0, "kayu_tua")
    m.c(0, 26.0, 0, 1.2, 12.0, 0.8, "kayu_tua")
    m.c(0, 19.6, 0, 1.4, 1.0, 2.0, "tali")
    m.c(0, -1.0, 0, 3.0, 1.0, 1.0, "kayu_tua")
    m.c(0, 1.4, 0.8, 0.4, 3.0, 0.4, "tali")
    m.c(0, 31.6, 0, 0.6, 0.8, 5.6, "kayu_muda")

    # 42 jala: a gathered fishing net hanging from the fist, cork floats, lead sinkers, the throw cord
    m = prop("jala")
    m.c(0, 0.6, 0, 1.0, 1.6, 1.0, "tali")
    m.c(0, -1.2, 0, 3.0, 2.0, 3.0, "jala")
    m.c(0, -4.2, 0, 6.0, 4.0, 6.0, "jala")
    m.c(0.6, -7.0, -0.4, 5.0, 2.0, 5.0, "jala")
    m.c(-0.4, -8.8, 0.4, 3.6, 1.6, 3.6, "jala")
    for i, (x, z) in enumerate(((2.8, 0), (-2.8, 0), (0, 2.8), (0, -2.8))):
        m.c(x, -3.0, z, 0.9, 1.2, 0.9, "kayu_muda")
    for x, z in ((1.6, 1.6), (-1.6, -1.6), (1.6, -1.6), (-1.6, 1.6)):
        m.c(x, -9.6, z, 0.6, 0.6, 0.6, "besi_gelap")
    m.c(1.0, 1.6, 0.6, 0.3, 2.0, 0.3, "tali")

    # 43 ikan_tali: three salted fish hanging from a carrying cord and cross stick
    m = prop("ikan_tali")
    m.c(0, -1.0, 0, 0.4, 3.0, 0.4, "tali")
    m.c(0, -2.7, 0, 6.4, 0.5, 0.5, "bambu")
    for x in (-2.4, 0.0, 2.4):
        m.c(x, -3.6, 0, 0.3, 1.4, 0.3, "tali")
        m.c(x, -7.2, 0, 1.0, 6.0, 2.0, "ikan")
        m.c(x, -10.6, 0, 0.5, 1.0, 2.6, "ikan")
        m.c(x, -4.6, -0.6, 1.1, 0.8, 0.6, "ikan")

    # 44 tali: a coil of rope (5 x 2 x 5) with the loose end
    m = prop("tali")
    m.ring(-1.0, 5.0, 2.0, 5.0, 1.2, "tali")
    m.ring(-1.0, 3.2, 1.6, 3.2, 0.6, "tali")
    m.c(0, -0.2, 0, 1.4, 0.4, 5.2, "tali")
    m.c(2.0, -2.4, -2.0, 0.8, 3.0, 0.8, "tali", rot=("x", 22.5))

    # 45 palu_dempul: caulking mallet, wooden head 3 x 3 x 4 with iron rings, ash handle
    m = prop("palu_dempul")
    m.c(0, 3.0, 0, 1.0, 9.0, 1.0, "kayu_muda")
    m.c(0, -1.8, 0, 1.3, 1.0, 1.3, "kulit")
    m.c(0, 8.4, 0, 3.0, 3.0, 4.0, "kayu_tua")
    m.c(0, 8.4, -2.1, 3.2, 3.2, 0.4, "besi")
    m.c(0, 8.4, 2.1, 3.2, 3.2, 0.4, "besi")
    m.c(0, 6.8, 0, 1.4, 0.4, 1.4, "besi")

    # 46 kuas_besar: big wall brush, 12 px handle, ferrule, 2 x 3 x 2 bristles wet with paint
    m = prop("kuas_besar")
    m.c(0, 4.0, 0, 1.0, 12.0, 1.0, "bambu")
    m.c(0, 10.6, 0, 1.6, 1.2, 1.6, "tali")
    m.c(0, 12.6, 0, 2.0, 3.0, 2.0, "ijuk")
    m.c(0, 14.4, 0, 1.6, 0.6, 1.6, "kain_merah")
    m.c(0, -2.4, 0, 1.2, 0.6, 1.2, "lak_merah")

    # 47 palet: painter's palette board with four paint dabs and the thumb hole edge
    m = prop("palet")
    m.c(0, 0, -2.0, 6.0, 0.5, 5.0, "kayu_muda")
    m.c(0, 0, 0.8, 2.0, 0.5, 1.2, "kayu_tua")
    for x, z, col in ((-1.8, -3.4, "kain_merah"), (0.2, -3.8, "sutra_biru"), (2.0, -3.0, "emas"), (-1.0, -1.6, "daun")):
        m.c(x, 0.4, z, 1.0, 0.3, 1.0, col)
    m.c(2.4, 0.3, -0.8, 1.2, 0.2, 1.4, "cat_palet")

    # 48 kendi: earthen water jug held by its neck, body 4 x 5 x 4, spout, foot ring
    m = prop("kendi")
    m.c(0, 0.6, 0, 1.6, 1.6, 1.6, "tanah_liat")
    m.c(0, -0.8, 0, 2.2, 1.0, 2.2, "tanah_liat")
    m.c(0, -3.8, 0, 4.0, 5.0, 4.0, "tanah_liat")
    m.c(0, -3.8, 0, 4.6, 3.0, 4.6, "tanah_liat")
    m.c(0, -6.6, 0, 2.8, 0.6, 2.8, "kayu_tua")
    m.c(0, -3.0, -2.8, 0.8, 0.8, 2.0, "tanah_liat", rot=("x", -22.5))
    m.c(0, -3.0, 2.4, 4.8, 0.4, 0.3, "kain_merah")

    # 49 keranjang_teh: tea-picking basket (5 x 4 x 5) hanging from a strap, heaped leaves
    m = prop("keranjang_teh")
    m.ring(-6.0, 5.0, 4.0, 5.0, 0.5, "jerami")
    m.c(0, -7.8, 0, 4.4, 0.4, 4.4, "jerami")
    m.c(0, -3.8, 0, 5.4, 0.4, 5.4, "bambu")
    m.c(0, -3.6, 0, 4.2, 0.8, 4.2, "teh")
    m.c(0.6, -3.0, -0.4, 2.4, 0.8, 2.0, "daun")
    m.c(-2.4, -1.6, 0, 0.4, 4.0, 0.4, "tali")
    m.c(2.4, -1.6, 0, 0.4, 4.0, 0.4, "tali")
    m.c(0, 0.4, 0, 5.2, 0.4, 0.4, "tali")

    # 50 lentera_teratai: lotus lantern on the palms: six petals turned around a glowing candle, a leaf base
    m = prop("lentera_teratai")
    m.c(0, 0.2, 0, 4.0, 0.4, 4.0, "daun")
    m.c(0, 0.2, 0, 5.6, 0.3, 2.0, "daun", rot=("y", 45))
    for i in range(6):
        ang = i * 60.0
        r = 1.8
        x, z = r * math.cos(math.radians(ang)), r * math.sin(math.radians(ang))
        rot = ("y", 45) if i % 3 == 1 else (("y", -45) if i % 3 == 2 else None)
        m.c(x, 1.6, z, 2.0, 2.4, 0.5, "kelopak", rot=rot)
    m.c(0, 2.6, 0, 3.0, 0.5, 3.0, "kelopak")
    m.c(0, 2.4, 0, 1.0, 2.0, 1.0, "lilin", glow=12)
    m.c(0, 3.8, 0, 0.5, 0.8, 0.5, "api", glow=15)

    # 51 pemukul_kecil: the small striker of the wooden fish: stick with a cloth-wrapped ball
    m = prop("pemukul_kecil")
    m.c(0, 2.6, 0, 0.6, 6.0, 0.6, "kayu_tua")
    m.c(0, 6.2, 0, 1.4, 1.4, 1.4, "kain_merah")
    m.c(0, -0.6, 0, 0.8, 0.6, 0.8, "lak_merah")

    # 52 ikan_kayu: the temple wooden fish (6 x 5 x 5), hollow with the slit mouth, scale carving, a cushion
    m = prop("ikan_kayu")
    m.c(0, -2.0, 0, 7.0, 0.6, 6.0, "kain_merah")
    m.c(0, 0.8, 0, 6.0, 5.0, 5.0, "kayu_merah")
    m.c(0, 0.8, 0, 6.6, 3.6, 4.4, "kayu_merah")
    m.c(0, 1.0, -2.6, 4.0, 0.6, 0.4, "lak_hitam")
    m.c(-2.0, 2.0, -2.55, 0.8, 0.8, 0.2, "emas")
    m.c(2.0, 2.0, -2.55, 0.8, 0.8, 0.2, "emas")
    m.c(0, 3.4, 1.0, 4.4, 0.4, 2.0, "emas")

    # 53 tongkat_cincin: the monk's ring staff, 20 px shaft, a 4 x 4 head loop with six iron rings
    m = prop("tongkat_cincin")
    m.dy = -4.0
    m.c(0, 6.0, 0, 1.0, 20.0, 1.0, "kayu_tua")
    m.c(0, 16.6, 0, 1.4, 1.2, 1.4, "perunggu")
    m.c(0, 18.4, 0, 4.0, 0.6, 1.0, "perunggu")
    m.c(0, 21.6, 0, 4.0, 0.6, 1.0, "perunggu")
    m.c(-1.8, 20.0, 0, 0.6, 3.8, 1.0, "perunggu")
    m.c(1.8, 20.0, 0, 0.6, 3.8, 1.0, "perunggu")
    m.c(0, 22.6, 0, 0.8, 1.6, 0.8, "perunggu")
    for i, (x, y) in enumerate(((-2.4, 18.4), (-2.6, 20.0), (-2.4, 21.6), (2.4, 18.4), (2.6, 20.0), (2.4, 21.6))):
        m.c(x, y, 0, 0.4, 1.4, 1.4, "besi")
    m.c(0, -4.2, 0, 1.2, 0.8, 1.2, "besi")

    # 54 tongkat_bambu: walking staff of mountain bamboo with nodes and a cord grip
    m = prop("tongkat_bambu")
    m.dy = -4.0
    m.c(0, 6.0, 0, 1.2, 22.0, 1.2, "bambu")
    for y in (-2.0, 4.0, 10.0, 16.0):
        m.c(0, y, 0, 1.5, 0.5, 1.5, "kayu_muda")
    m.c(0, 1.0, 0, 1.4, 3.0, 1.4, "tali")
    m.c(0, -5.2, 0, 1.0, 0.6, 1.0, "besi_gelap")

    # 55 tas_dupa: the pilgrim's incense bag (3 x 4 x 2) on its strap, incense sticks poking out
    m = prop("tas_dupa")
    m.c(0, -3.0, 0, 3.0, 4.0, 2.0, "kain_kasar")
    m.c(0, -1.2, -1.05, 3.1, 1.2, 0.2, "kain_merah")
    m.c(0, 0.4, 0, 2.6, 0.4, 0.4, "tali")
    for x in (-0.8, 0.0, 0.8):
        m.c(x, -0.4, 0.2, 0.3, 2.2, 0.3, "dupa")
    m.c(-1.6, 1.4, 0, 0.3, 3.6, 0.3, "tali", rot=("z", 22.5))
    m.c(1.6, 1.4, 0, 0.3, 3.6, 0.3, "tali", rot=("z", -22.5))

    # 56 rangka_punggung: the porter's back frame (8 x 16 x 2) and its load (8 x 8 x 6): bundled firewood, a sack
    m = prop("rangka_punggung")
    m.dy = -2.0
    m.c(-3.5, 2.0, 0, 1.0, 16.0, 1.0, "kayu_tua")
    m.c(3.5, 2.0, 0, 1.0, 16.0, 1.0, "kayu_tua")
    for y in (-4.0, 1.0, 6.0, 9.5):
        m.c(0, y, 0, 8.0, 0.8, 0.8, "kayu_muda")
    m.c(0, -5.6, -1.6, 8.0, 0.8, 3.6, "kayu_tua")
    m.c(0, 0.0, -3.8, 7.6, 6.0, 5.0, "kain_kasar")
    for x in (-2.4, -0.8, 0.8, 2.4):
        m.c(x, 6.0, -3.0, 1.2, 6.0, 1.2, "kayu_muda")
    m.c(0, 6.0, -3.0, 7.4, 0.6, 2.0, "tali")
    m.c(0, 0.6, -6.4, 7.8, 0.6, 0.4, "tali")
    m.c(-3.5, -1.0, 1.2, 0.6, 8.0, 0.6, "kulit")
    m.c(3.5, -1.0, 1.2, 0.6, 8.0, 0.6, "kulit")

    # 57 pisau_ganda: the arena butcher's long knife (twin-edged): bone grip, guard, tapering blade
    m = prop("pisau_ganda")
    m.c(0, 0.0, 0, 1.0, 3.4, 1.0, "tulang")
    m.c(0, 2.0, 0, 1.2, 0.5, 2.4, "besi_gelap")
    m.c(0, 5.6, 0, 0.3, 6.8, 1.6, "besi")
    m.c(0, 9.6, 0, 0.3, 1.4, 1.0, "besi")
    m.c(0, 10.6, 0, 0.3, 0.8, 0.5, "besi")
    m.c(0, -2.0, 0, 1.2, 0.6, 1.2, "besi_gelap")

    # 58 rantai_tangan: hand shackles: two iron cuffs joined by a short chain (worn behind the back)
    m = prop("rantai_tangan")
    m.ring(0.0, 2.0, 1.2, 2.0, 0.5, "besi_gelap")
    m.ring(0.0, 2.0, 1.2, 2.0, 0.5, "besi_gelap", cx=5.0)
    m.c(2.5, 0.0, 0, 2.6, 0.6, 0.6, "rantai")
    m.c(1.4, 0.0, 0, 0.6, 0.8, 0.3, "besi")
    m.c(3.6, 0.0, 0, 0.6, 0.8, 0.3, "besi")

    # 59 cawan_racun: a dark green poison cup (3 x 3 x 3) on a little foot, the venom inside
    m = prop("cawan_racun")
    m.c(0, 0.2, 0, 1.6, 0.4, 1.6, "lak_hitam")
    m.c(0, 0.7, 0, 0.8, 0.6, 0.8, "lak_hitam")
    m.c(0, 1.2, 0, 2.0, 0.4, 2.0, "giok")
    m.ring(1.9, 2.6, 1.0, 2.6, 0.4, "giok")
    m.ring(3.0, 3.4, 1.2, 3.4, 0.4, "giok")
    m.ring(3.7, 3.8, 0.3, 3.8, 0.3, "emas")
    m.c(0, 2.9, 0, 2.6, 0.2, 2.6, "racun", glow=6)
    m.c(0.6, 4.4, -0.3, 0.6, 0.6, 0.6, "racun", glow=6)
    m.c(-0.4, 5.2, 0.3, 0.4, 0.4, 0.4, "racun", glow=4)

    # 60 cambuk_kafilah: the caravan whip: leather grip with brass ferrules, the braided lash arching forward and
    # down in five tapering segments, a red cracker at the tip
    m = prop("cambuk_kafilah")
    m.c(0, 2.0, 0, 1.0, 6.0, 1.0, "kulit")
    m.c(0, -1.4, 0, 1.2, 0.8, 1.2, "perunggu")
    m.c(0, 5.4, 0, 1.2, 0.8, 1.2, "perunggu")
    m.c(0, 6.6, 0, 0.6, 1.6, 0.6, "tali")
    m.c(0, 8.0, -1.0, 0.6, 2.4, 0.6, "tali", rot=("x", -45))
    m.c(0, 8.6, -3.2, 0.55, 0.55, 2.8, "tali")
    m.c(0, 7.8, -5.4, 0.5, 2.4, 0.5, "tali", rot=("x", 45))
    m.c(0, 6.0, -6.4, 0.45, 2.6, 0.45, "tali", rot=("x", 22.5))
    m.c(0, 3.8, -6.8, 0.4, 2.4, 0.4, "tali")
    m.c(0, 2.2, -6.8, 0.35, 1.0, 0.8, "kain_merah")

    # 61 sabit_herba: the cliff herb sickle: short handle, a hooked blade in three angled parts
    m = prop("sabit_herba")
    m.c(0, 1.4, 0, 1.0, 5.0, 1.0, "kayu_muda")
    m.c(0, 4.2, 0, 1.2, 0.6, 1.2, "besi_gelap")
    m.c(0, 5.6, -1.0, 0.3, 2.6, 1.2, "besi", rot=("x", -22.5))
    m.c(0, 6.6, -3.2, 0.3, 1.0, 3.2, "besi")
    m.c(0, 5.8, -5.2, 0.3, 1.6, 1.0, "besi", rot=("x", 45))
    m.c(0, -1.4, 0, 0.6, 0.6, 0.6, "tali")

    # 62 kipas_sutra: an open silk fan: the pivot rivet, seven ribs and seven silk panels radiating from it (each panel
    # placed along its own direction and turned about its centre), the lacquered guards
    m = prop("kipas_sutra")
    m.c(0, 0, 0, 0.8, 0.8, 0.8, "emas")
    for k, ang in enumerate((-45.0, -22.5, 0.0, 22.5, 45.0)):
        t = math.radians(ang)
        rx, ry = -math.sin(t), math.cos(t)
        rot = ("z", ang) if ang else None
        m.c(rx * 2.4, ry * 2.4, 0, 0.3, 4.6, 0.4, "kayu_merah", rot=rot)
        m.c(rx * 5.6, ry * 5.6, 0.25, 2.6, 3.4, 0.2, "sutra_biru", rot=rot)
    m.c(-0.0, 7.6, 0.25, 2.8, 0.3, 0.3, "kayu_merah")
    for ang in (-56.0, 56.0):
        t = math.radians(ang)
        m.c(-math.sin(t) * 4.0, math.cos(t) * 4.0, 0, 0.5, 8.0, 0.6, "lak_merah", rot=("z", 45 if ang > 0 else -45))
    m.c(0, -1.0, 0, 0.3, 1.6, 0.3, "kain_merah")
    m.c(0, -2.0, 0, 0.6, 0.6, 0.6, "giok")


PROP_ORDER = ["dayung_yuloh", "jala", "ikan_tali", "tali", "palu_dempul", "kuas_besar", "palet", "kendi", "keranjang_teh",
              "lentera_teratai", "pemukul_kecil", "ikan_kayu", "tongkat_cincin", "tongkat_bambu", "tas_dupa", "rangka_punggung",
              "pisau_ganda", "rantai_tangan", "cawan_racun", "cambuk_kafilah", "sabit_herba", "kipas_sutra"]


# ================================================================================================ 3D items

ITEM3D = []


def item3d(folder, name):
    m = g.Model(name, folder)
    m.item_folder = folder
    ITEM3D.append(m)
    return m


def build_items3d():
    # dagang/peti: the sealed trade chest (8 units of one good), iron corners, the 1 NB wax seal over the lid seam
    m = item3d("dagang", "peti")
    m.c(0, -1.0, 0, 10.0, 7.0, 7.0, "kayu_muda")
    m.c(0, 3.4, 0, 10.4, 1.8, 7.4, "kayu_tua")
    for x in (-4.8, 4.8):
        for z in (-3.3, 3.3):
            m.c(x, 0.2, z, 1.0, 9.6, 1.0, "besi_gelap")
    m.c(0, 0.0, -3.6, 10.2, 1.0, 0.3, "besi_gelap")
    m.c(0, 2.6, -3.75, 2.0, 2.0, 0.4, "lak_merah")
    m.c(0, 1.0, -3.8, 1.0, 1.6, 0.3, "kertas")
    m.c(-3.0, -1.4, -3.6, 3.0, 2.0, 0.2, "kertas")
    m.c(-6.0, 0.6, 0, 1.0, 1.0, 3.0, "besi")
    m.c(6.0, 0.6, 0, 1.0, 1.0, 3.0, "besi")

    # tema/kerudung_pasir: the sand veil worn on the head: a hood shell, the face cloth leaving the eyes free, the rope
    # circlet and the trailing tail at the back (14 units = just outside the head)
    m = item3d("tema", "kerudung_pasir")
    m.c(0, 3.6, 0, 14.0, 7.2, 14.0, "kain_pasir")
    m.c(-6.6, -1.4, 0, 0.8, 7.0, 14.0, "kain_pasir")
    m.c(6.6, -1.4, 0, 0.8, 7.0, 14.0, "kain_pasir")
    m.c(0, -1.4, 6.6, 14.0, 7.0, 0.8, "kain_pasir")
    m.c(0, -3.2, -6.6, 14.0, 3.6, 0.8, "kain_pasir")
    m.c(0, 2.4, -7.1, 14.4, 1.2, 0.6, "tali")
    m.c(0, 2.4, 7.1, 14.4, 1.2, 0.6, "tali")
    m.c(-7.1, 2.4, 0, 0.6, 1.2, 14.4, "tali")
    m.c(7.1, 2.4, 0, 0.6, 1.2, 14.4, "tali")
    m.c(0, -6.0, 7.4, 6.0, 9.0, 0.8, "kain_pasir", rot=("x", 22.5))
    m.c(-2.6, -4.8, -7.2, 1.2, 1.2, 0.6, "kain_merah")

    # tema/topeng_tanpa_wajah: the faceless mask: a smooth lacquered oval front with two eye slits, a forehead band
    # and the black ties around the head
    m = item3d("tema", "topeng_tanpa_wajah")
    m.c(0, 0.0, -7.2, 11.0, 13.0, 0.8, "porselen")
    m.c(0, 0.0, -7.6, 9.0, 11.0, 0.4, "porselen")
    m.c(0, 6.0, -7.4, 8.0, 1.0, 0.6, "porselen")
    m.c(-2.6, 1.4, -8.0, 2.4, 0.6, 0.2, "lak_hitam")
    m.c(2.6, 1.4, -8.0, 2.4, 0.6, 0.2, "lak_hitam")
    m.c(0, 4.6, -7.5, 11.4, 1.2, 0.6, "lak_merah")
    m.c(-6.6, 3.0, 0, 0.6, 1.0, 14.0, "kain_hitam")
    m.c(6.6, 3.0, 0, 0.6, 1.0, 14.0, "kain_hitam")
    m.c(0, 3.0, 6.8, 13.8, 1.0, 0.6, "kain_hitam")
    m.c(0, 2.0, 7.6, 1.0, 4.0, 0.4, "kain_hitam")


# ================================================================================================ 16 x 16 icons

class Icon:
    """A 16 x 16 pixel-art canvas: primitives with built-in top-left lighting; finish() adds the outline."""

    def __init__(self, name):
        self.name = name
        self.px = [[None] * 16 for _ in range(16)]

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = tuple(int(max(0, min(255, v))) for v in c[:3])

    def get(self, x, y):
        if 0 <= x < 16 and 0 <= y < 16:
            return self.px[y][x]
        return None

    def rect(self, x0, y0, x1, y1, col, light=True):
        b = rgb(col)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                k = 1.0
                if light:
                    if x == x0 or y == y0:
                        k = 1.18
                    elif x == x1 or y == y1:
                        k = 0.78
                self.put(x, y, shade(b, k))

    def disc(self, cx, cy, r, col, light=True, ry=None):
        b = rgb(col)
        ry = r if ry is None else ry
        for y in range(16):
            for x in range(16):
                dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d <= 1.0:
                    k = 1.0
                    if light:
                        k = 1.0 + 0.35 * (-dx - dy) / 1.4 - 0.15 * d
                    self.put(x, y, shade(b, k))

    def line(self, x0, y0, x1, y1, col, k=1.0):
        b = shade(rgb(col), k)
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(1, n)
            self.put(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), b)

    def poly(self, pts, col, light=True):
        b = rgb(col)
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        for y in range(int(min(ys)), int(max(ys)) + 1):
            for x in range(int(min(xs)), int(max(xs)) + 1):
                if _inside(x + 0.5, y + 0.5, pts):
                    k = 1.0
                    if light:
                        k = 1.08 - 0.25 * ((x - min(xs)) + (y - min(ys))) / max(1.0, (max(xs) - min(xs)) + (max(ys) - min(ys)))
                    self.put(x, y, shade(b, k))

    def hi(self, x, y, k=1.35):
        c = self.get(x, y)
        if c is not None:
            self.put(x, y, shade(c, k))

    def dot(self, x, y, col):
        self.put(x, y, rgb(col))

    def finish(self, outline=0.42):
        """A 1 px outline of the shape's own colour, darkened, into the transparent neighbours (4-neighbourhood)."""
        add = []
        for y in range(16):
            for x in range(16):
                if self.px[y][x] is not None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = self.get(x + dx, y + dy)
                    if n is not None:
                        add.append((x, y, shade(n, outline)))
                        break
        for x, y, c in add:
            if x == 0 or y == 0 or x == 15 or y == 15 or True:
                self.put(x, y, c)
        img = Image(16, 16)
        for y in range(16):
            for x in range(16):
                c = self.px[y][x]
                if c is not None:
                    img.set(x, y, (c[0], c[1], c[2], 255))
        return img


def _inside(x, y, pts):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
            inside = not inside
        j = i
    return inside


ICONS = {}


def icon(folder, name):
    def deco(fn):
        ICONS[(folder, name)] = fn
        return fn
    return deco


# ---------------------------------------------------------------- shared shapes

def fish(c, body, belly, fin, eye="101418", glow=None, x0=2, y0=6):
    c.poly([(x0, y0 + 3), (x0 + 3, y0), (x0 + 9, y0), (x0 + 11, y0 + 3), (x0 + 9, y0 + 6), (x0 + 3, y0 + 6)], body)
    c.poly([(x0 + 10, y0 + 3), (x0 + 13, y0), (x0 + 13, y0 + 6)], fin)
    for x in range(x0 + 3, x0 + 9):
        c.put(x, y0 + 5, rgb(belly))
    c.line(x0 + 5, y0 - 1, x0 + 8, y0 - 1, fin)
    for x in range(x0 + 4, x0 + 10, 2):
        c.hi(x, y0 + 2, 0.82)
    c.dot(x0 + 2, y0 + 2, eye)
    c.hi(x0 + 4, y0 + 1, 1.4)
    if glow:
        for x, y in ((x0 + 13, y0), (x0 + 13, y0 + 6), (x0 + 6, y0 - 1)):
            c.dot(x, y, glow)


def bottle(c, glass, liquid, cork="8A6A42", label=None):
    c.rect(6, 1, 9, 2, cork)
    c.rect(6, 3, 9, 4, glass)
    c.disc(8, 10, 5.0, glass, ry=4.6)
    for y in range(8, 15):
        for x in range(3, 13):
            if c.get(x, y) is not None and y >= 9:
                c.put(x, y, shade(rgb(liquid), 1.0 - (y - 9) * 0.05))
    c.hi(5, 7, 1.6)
    c.hi(5, 8, 1.5)
    c.dot(6, 6, "F4FAFF")
    if label:
        c.rect(6, 10, 10, 12, label)


def scroll(c, paper="E8DDC0", rod="8A1F1F", seal=None, ribbon=None, writing="3A2A20"):
    c.rect(3, 3, 12, 12, paper)
    c.rect(2, 2, 13, 3, rod)
    c.rect(2, 12, 13, 13, rod)
    c.dot(1, 2, "E0B040")
    c.dot(14, 2, "E0B040")
    c.dot(1, 13, "E0B040")
    c.dot(14, 13, "E0B040")
    for y in (5, 7, 9):
        for x in range(5, 11):
            if (x + y) % 3 != 0:
                c.dot(x, y, writing)
    if ribbon:
        c.rect(7, 3, 8, 12, ribbon)
    if seal:
        c.disc(10.5, 10.5, 2.0, seal)


def vial(c, glass, liquid, mark=None):
    c.rect(7, 1, 8, 2, "5A3A22")
    c.rect(6, 3, 9, 3, glass)
    c.rect(5, 4, 10, 13, glass)
    for y in range(6, 14):
        for x in range(5, 11):
            c.put(x, y, shade(rgb(liquid), 1.05 - (y - 6) * 0.04))
    c.rect(5, 14, 10, 14, glass)
    c.hi(6, 5, 1.5)
    c.hi(6, 7, 1.3)
    if mark:
        mark(c)


def skull_mark(c, col="E8E0CC"):
    for x, y in ((7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)):
        c.dot(x, y, col)
    c.dot(7, 9, "101010")
    c.dot(8, 9, "101010")


def pearl(c, col, rim, glow=None):
    c.disc(8, 8.5, 5.0, col)
    c.disc(6.5, 7.0, 1.6, "FFFFFF", light=False)
    if glow:
        for x, y in ((8, 2), (2, 8), (14, 9), (8, 15)):
            c.dot(x, y, glow)
    c.dot(10, 11, rim)


# ---------------------------------------------------------------- tema items (TemaItems)

@icon("tema", "ikan_roh")
def _(c):
    fish(c, "6A9AC8", "D8ECF8", "8AE0F0", glow="C8FFFF")


@icon("tema", "sup_ikan_pagi")
def _(c):
    c.disc(8, 10, 6.5, "E8E0D0", ry=4.0)
    c.disc(8, 8.5, 5.5, "D8A050", ry=2.2, light=False)
    c.rect(4, 8, 7, 8, "EDE8DC")
    c.dot(10, 8, "5A8A3A")
    c.dot(11, 9, "5A8A3A")
    c.dot(6, 9, "E07A40")
    c.rect(6, 13, 10, 14, "B8B0A0")
    for x, y in ((6, 4), (7, 3), (9, 4), (10, 2)):
        c.dot(x, y, "F0F0F0")
    c.line(11, 3, 14, 7, "8A6A42")


@icon("tema", "kompas_pasang")
def _(c):
    c.disc(8, 8, 6.5, "8C6A3A")
    c.disc(8, 8, 5.0, "E8DDC0")
    c.line(8, 4, 8, 12, "C9A24A")
    c.line(4, 8, 12, 8, "C9A24A")
    c.line(5, 11, 11, 5, "2E5E8C")
    c.dot(11, 5, "6AB8E0")
    c.dot(5, 11, "A82828")
    c.dot(8, 8, "E0B040")
    c.dot(8, 1, "E0B040")


@icon("tema", "kaca_garam_laut")
def _(c):
    c.poly([(8, 1), (12, 6), (11, 13), (5, 14), (3, 7)], "B8E0F0")
    c.line(8, 1, 7, 14, "E8F8FF")
    c.line(3, 7, 12, 6, "88C0E0")
    for x, y in ((6, 4), (9, 9), (5, 11)):
        c.dot(x, y, "FFFFFF")


@icon("tema", "mutiara_pelita")
def _(c):
    pearl(c, "F8E8B8", "C8A060", glow="FFE080")


@icon("tema", "mutiara_fajar")
def _(c):
    c.disc(8, 8.5, 5.5, "9AC0F0")
    c.disc(9.5, 10.0, 3.0, "E0B040", light=False)
    c.disc(6.5, 6.5, 1.6, "FFFFFF", light=False)
    for x, y in ((8, 1), (1, 8), (15, 8), (8, 15), (3, 3), (13, 13)):
        c.dot(x, y, "FFE8A0")


@icon("tema", "jimat_ombak")
def _(c):
    c.line(8, 0, 8, 3, "A82828")
    c.disc(8, 9, 5.5, "5FA88C")
    for x in range(4, 13):
        y = int(round(9 + 1.5 * math.sin(x * 1.1)))
        c.dot(x, y, "D8F0E8")
        c.dot(x, y + 2, "2E7A5A")
    c.dot(8, 4, "E0B040")
    c.line(7, 14, 9, 14, "A82828")


@icon("tema", "astrolab_kecil")
def _(c):
    c.disc(8, 8, 6.5, "B08A4A")
    c.disc(8, 8, 5.2, "3A2A20", light=False)
    c.disc(8, 8, 4.0, "D8AE58")
    c.disc(8, 8, 2.8, "2E3A6A", light=False)
    for x, y in ((7, 6), (10, 7), (8, 10), (6, 9)):
        c.dot(x, y, "F0F0FF")
    c.line(3, 13, 13, 3, "E0B040")
    c.rect(7, 0, 8, 1, "8C6A3A")


@icon("tema", "kerudung_pasir")
def _(c):
    c.poly([(3, 6), (6, 2), (10, 2), (13, 6), (13, 13), (3, 13)], "C8A878")
    c.rect(5, 7, 11, 8, "3A2A20", light=False)
    c.dot(6, 7, "E8E0CC")
    c.dot(10, 7, "E8E0CC")
    c.line(3, 5, 13, 5, "8A6A48")
    c.poly([(11, 10), (15, 12), (14, 15), (11, 13)], "B8986A")


@icon("tema", "air_suci")
def _(c):
    bottle(c, "C8E8F0", "6AB8E0", cork="E0B040")
    c.dot(8, 11, "FFFFFF")
    c.dot(7, 12, "E0F8FF")


@icon("tema", "surat_jalan_gurun")
def _(c):
    scroll(c, paper="E4D0A0", rod="A86A2A", seal="A82828")
    c.line(4, 11, 7, 11, "8A6A48")


@icon("tema", "pecahan_mural")
def _(c):
    c.poly([(2, 4), (9, 2), (14, 5), (13, 12), (6, 14), (2, 10)], "D8B878")
    c.rect(4, 9, 12, 11, "2E5E8C", light=False)
    c.rect(6, 4, 7, 8, "A82A1E", light=False)
    c.dot(6, 3, "3A2A20")
    c.rect(8, 6, 9, 7, "7A5A38", light=False)
    c.line(9, 3, 11, 10, "8A6A48")


@icon("tema", "koin_kuno")
def _(c):
    c.disc(8, 8, 6.0, "C8A040")
    c.disc(8, 8, 4.6, "A87A24", light=False)
    c.rect(7, 7, 8, 8, "3A2A14", light=False)
    for x, y in ((8, 4), (11, 8), (8, 11), (4, 8)):
        c.dot(x, y, "F0D070")
    c.hi(5, 5, 1.5)


@icon("tema", "lempeng_peta")
def _(c):
    c.poly([(2, 4), (14, 2), (14, 12), (2, 14)], "A8804A")
    c.line(4, 11, 7, 8, "5A3A1A")
    c.line(7, 8, 9, 9, "5A3A1A")
    c.line(9, 9, 12, 5, "5A3A1A")
    c.dot(12, 5, "E0B040")
    c.dot(4, 11, "A82828")
    c.dot(5, 5, "5FA88C")
    c.dot(11, 12, "5FA88C")


@icon("tema", "ekor_racun_kalajengking")
def _(c):
    for i, (x, y, r) in enumerate(((4, 12, 2.4), (6, 9, 2.2), (8, 6, 2.0), (11, 4, 1.8))):
        c.disc(x, y, r, "2A1A1C")
        c.dot(int(x) - 1, int(y) - 1, "7A1020")
    c.disc(12.5, 7, 2.2, "8A1020")
    c.line(13, 9, 11, 12, "E07A2A")
    c.dot(11, 13, "FFC070")


@icon("tema", "selendang_penari")
def _(c):
    for x in range(1, 15):
        y = int(round(7 + 3.5 * math.sin(x * 0.55)))
        for k in range(3):
            c.put(x, y + k, shade(rgb("C81E1E"), 1.1 - k * 0.12))
        c.put(x, y + 3, rgb("E0B040"))
    c.dot(2, 4, "E0B040")
    c.dot(13, 12, "E0B040")


@icon("tema", "kipas_sutra")
def _(c):
    for i in range(7):
        a = math.radians(200 + i * 23)
        c.line(8, 13, 8 + 7 * math.cos(a), 13 + 7 * math.sin(a), "3A5A8C")
    c.disc(8, 13, 7.0, "3A5A8C", ry=7.0)
    for y in range(13, 16):
        for x in range(16):
            c.px[y][x] = None
    c.disc(8, 13, 3.0, "EDE8DC", light=False)
    for y in range(13, 16):
        for x in range(16):
            c.px[y][x] = None
    for i in range(7):
        a = math.radians(196 + i * 25)
        c.line(8, 13, 8 + 6.5 * math.cos(a), 13 + 6.5 * math.sin(a), "7A2E1E")
    c.rect(7, 13, 8, 15, "7A2E1E")
    c.dot(5, 8, "EDE8DC")
    c.dot(11, 8, "EDE8DC")


def robe(c, main, trim, figure=None):
    c.poly([(3, 3), (6, 1), (10, 1), (13, 3), (15, 8), (12, 9), (12, 15), (4, 15), (4, 9), (1, 8)], main)
    c.line(6, 1, 9, 8, trim)
    c.line(10, 1, 9, 8, trim)
    c.rect(4, 9, 12, 9, trim)
    if figure:
        for x, y in ((6, 12), (9, 11), (10, 13)):
            c.dot(x, y, figure)


@icon("tema", "jubah_sutra")
def _(c):
    robe(c, "3A5A8C", "EDE8DC", "D8E8F8")


@icon("tema", "jubah_benang_merah")
def _(c):
    robe(c, "8E1F1F", "E0B040", "FF5A4A")
    c.line(5, 14, 11, 10, "E03A2A")


def tea_pack(c, paper, band, leaf):
    c.poly([(4, 3), (12, 3), (13, 14), (3, 14)], paper)
    c.rect(4, 3, 12, 4, band)
    c.line(7, 2, 9, 2, "8A6A42")
    c.disc(8, 9.5, 2.5, leaf)
    c.line(8, 7, 8, 12, shade_hex(leaf, 0.6))


def shade_hex(h, k):
    s = shade(rgb(h), k)
    return "%02X%02X%02X" % tuple(int(v) for v in s)


@icon("tema", "teh_bi_luo")
def _(c):
    tea_pack(c, "E8DDC0", "2E7A4A", "5A9A3A")


@icon("tema", "teh_embun_gunung")
def _(c):
    tea_pack(c, "D8E0E8", "6A8AA8", "8AB858")
    c.dot(10, 7, "F0FAFF")


@icon("tema", "teh_seribu_tahun")
def _(c):
    c.disc(8, 9, 6.5, "5A3A22", ry=4.5)
    c.disc(8, 8, 6.0, "7A5232", ry=3.6)
    c.disc(8, 8, 2.0, "A82828", ry=1.4)
    for x, y in ((4, 8), (11, 7), (9, 10), (6, 10)):
        c.dot(x, y, "4A2E1A")
    c.dot(8, 8, "E0B040")


@icon("tema", "daun_teh_embun")
def _(c):
    for cx, cy, a in ((6, 9, -0.6), (10, 7, 0.5), (8, 11, 0.1)):
        for t in range(-4, 5):
            x = cx + t * math.cos(a)
            y = cy + t * math.sin(a)
            w = 1.6 * (1 - abs(t) / 5.0)
            c.disc(x, y, max(0.6, w), "5A9A3A", light=False)
        c.line(cx - 3 * math.cos(a), cy - 3 * math.sin(a), cx + 3 * math.cos(a), cy + 3 * math.sin(a), "3A6A24")
    c.dot(11, 5, "E8F8FF")
    c.dot(5, 7, "E8F8FF")


@icon("tema", "lentera_teratai")
def _(c):
    c.disc(8, 13, 6.0, "4E7A34", ry=2.0)
    for i, x in enumerate((3, 5, 8, 11, 13)):
        h = 6 if x in (5, 11) else (7 if x == 8 else 4)
        c.poly([(x - 1.8, 12), (x, 12 - h), (x + 1.8, 12)], "F0A0B8")
    c.rect(7, 6, 8, 10, "FFF0C0")
    c.dot(7, 4, "FFB040")
    c.dot(8, 5, "FFE080")


@icon("tema", "pita_naga")
def _(c):
    for x in range(1, 15):
        y = int(round(8 + 3 * math.sin(x * 0.7)))
        c.put(x, y - 1, rgb("E0B040"))
        c.put(x, y, rgb("C81E1E"))
        c.put(x, y + 1, shade(rgb("C81E1E"), 0.8))
    c.disc(13, 4, 2.0, "C81E1E")
    c.dot(13, 3, "FFE080")
    c.dot(14, 2, "E0B040")


@icon("tema", "kain_xiu_niang")
def _(c):
    c.rect(2, 3, 13, 13, "EDE8DC")
    c.rect(2, 3, 13, 4, "A82828")
    c.rect(2, 12, 13, 13, "A82828")
    c.disc(8, 8.5, 3.0, "C81E1E", light=False)
    c.disc(8, 8.5, 1.6, "E0B040", light=False)
    for x, y in ((4, 6), (12, 7), (4, 10), (11, 10)):
        c.dot(x, y, "2E7A4A")
    c.line(5, 7, 6, 8, "2E7A4A")


@icon("tema", "tali_panjat")
def _(c):
    c.disc(8, 9, 6.0, "B89A62")
    c.disc(8, 9, 3.6, "8A7040")
    c.disc(8, 9, 2.0, "B89A62", light=False)
    for a in range(0, 360, 40):
        r = math.radians(a)
        c.dot(int(8 + 4.8 * math.cos(r)), int(9 + 4.8 * math.sin(r)), "7A6034")
    c.line(12, 4, 14, 1, "7C8288")
    c.line(14, 1, 15, 3, "7C8288")


@icon("tema", "dupa_ziarah")
def _(c):
    for x in (5, 7, 9, 11):
        c.line(x, 2, x - 1, 13, "A84A2A")
        c.dot(x, 2, "FF7A30")
    c.rect(3, 9, 12, 11, "E0B040")
    c.rect(3, 10, 12, 10, "A82828", light=False)
    for x, y in ((6, 0), (9, 1), (5, 1)):
        c.dot(x, y, "C8C8C8")


@icon("tema", "rumput_awan_seribu_tahun")
def _(c):
    for i, (x, h) in enumerate(((4, 9), (6, 12), (8, 13), (10, 11), (12, 8))):
        c.line(8, 14, x, 14 - h, "B8C8D0")
        c.dot(x, 14 - h, "F0FAFF")
    c.disc(8, 4, 2.0, "E8F4FF")
    c.dot(5, 6, "8ACCF0")
    c.dot(11, 5, "8ACCF0")


@icon("tema", "tali_sutra_kera")
def _(c):
    c.disc(8, 9, 6.0, "E0B040")
    c.disc(8, 9, 3.4, "C89A28")
    c.disc(8, 9, 1.8, "E0B040", light=False)
    for a in range(10, 360, 45):
        r = math.radians(a)
        c.dot(int(8 + 4.6 * math.cos(r)), int(9 + 4.6 * math.sin(r)), "FFF0A0")
    c.line(11, 3, 13, 1, "E0B040")
    c.dot(14, 1, "A82828")


@icon("tema", "tongkat_kuli")
def _(c):
    c.line(2, 14, 13, 3, "8A6A42")
    c.line(3, 14, 14, 3, "6A4A2E")
    c.rect(12, 1, 14, 3, "7C8288")
    c.line(5, 10, 7, 12, "B8A070")
    c.line(6, 9, 8, 11, "B8A070")


@icon("tema", "pil_darah")
def _(c):
    c.disc(8, 8.5, 4.5, "8A1020")
    c.disc(6.8, 7.0, 1.4, "FF6A6A", light=False)
    c.dot(10, 11, "4A0A10")
    for x, y in ((3, 3), (12, 3), (13, 12)):
        c.dot(x, y, "C81E1E")


@icon("tema", "racun_lemah")
def _(c):
    vial(c, "C8D8C0", "8AC858", skull_mark)


@icon("tema", "racun_sedang")
def _(c):
    vial(c, "B8C8B0", "4A9A3A", skull_mark)
    c.dot(4, 3, "8AC858")


@icon("tema", "racun_kuat")
def _(c):
    vial(c, "9AA898", "1E5A1E", lambda cc: skull_mark(cc, "C8FFC0"))
    for x, y in ((4, 2), (11, 1), (12, 4)):
        c.dot(x, y, "5AC048")


@icon("tema", "topeng_tanpa_wajah")
def _(c):
    c.disc(8, 8, 5.5, "F0ECE4", ry=7.0)
    c.rect(5, 6, 6, 6, "1C1418", light=False)
    c.rect(10, 6, 11, 6, "1C1418", light=False)
    c.rect(3, 3, 13, 3, "8E1F1F", light=False)
    c.line(1, 4, 3, 3, "2A2426")
    c.line(13, 3, 15, 4, "2A2426")


@icon("tema", "peta_sarang")
def _(c):
    scroll(c, paper="D8C8A0", rod="3A2A20", writing="6A5A40")
    c.line(4, 10, 8, 6, "A82828")
    c.line(8, 6, 11, 9, "A82828")
    c.dot(11, 9, "1C1418")
    c.dot(12, 8, "C81E1E")


@icon("tema", "kontrak_kepala")
def _(c):
    c.rect(3, 2, 12, 14, "E8DDC0")
    c.rect(3, 2, 12, 2, "7A1020")
    c.disc(7.5, 6.5, 2.5, "3A2A20", light=False)
    c.rect(6, 8, 9, 9, "3A2A20", light=False)
    for x in range(4, 12, 2):
        c.dot(x, 11, "3A2A20")
    c.disc(10.5, 12.5, 2.0, "C81E1E")


@icon("tema", "peta_gerbang_rahasia")
def _(c):
    scroll(c, paper="C8B890", rod="1C1418", seal="7A1020", writing="4A3A30")
    c.rect(6, 6, 9, 9, "2A2224", light=False)
    c.dot(7, 7, "FF3A2A")
    c.dot(8, 7, "FF3A2A")


def forbidden_scroll(c, seal, glow):
    c.rect(3, 3, 12, 12, "2A2224")
    c.rect(2, 2, 13, 3, "1C1418")
    c.rect(2, 12, 13, 13, "1C1418")
    for y in (5, 7, 9):
        for x in range(5, 11):
            if (x * 3 + y) % 4 != 0:
                c.dot(x, y, glow)
    c.rect(7, 3, 8, 12, seal)
    c.disc(8, 8, 2.2, seal)
    c.dot(8, 8, "FFF0E0")


@icon("tema", "gulungan_bakar_darah")
def _(c):
    forbidden_scroll(c, "A81818", "FF5A3A")


@icon("tema", "gulungan_langkah_hantu")
def _(c):
    forbidden_scroll(c, "4A3A8A", "A89AFF")


@icon("tema", "gulungan_rantai_jiwa")
def _(c):
    forbidden_scroll(c, "3A3C40", "C8C8D8")
    for x in range(2, 14, 2):
        c.dot(x, 14, "7C8288")


@icon("tema", "gulungan_topeng_iblis_hati")
def _(c):
    forbidden_scroll(c, "7A1020", "E0306A")
    c.dot(6, 8, "FFE080")
    c.dot(10, 8, "FFE080")


# ---------------------------------------------------------------- trade goods (DagangItems)

@icon("dagang", "ikan_asin_roh")
def _(c):
    fish(c, "B8A078", "E8DCC0", "8A7450", eye="3A2A20", y0=5)
    c.line(1, 3, 14, 3, "B8A070")
    c.line(8, 3, 8, 5, "B8A070")
    for x, y in ((5, 9), (9, 8), (11, 10)):
        c.dot(x, y, "F8F8F0")


@icon("dagang", "mutiara_laut")
def _(c):
    c.disc(8, 10, 6.5, "8A7A6A", ry=4.0)
    c.disc(8, 9, 5.5, "E8D8E8", ry=3.0)
    c.disc(8, 8.5, 2.6, "F8F4FF")
    c.dot(7, 7, "FFFFFF")
    c.line(2, 10, 14, 10, "6A5A4A")


@icon("dagang", "rempah_seberang")
def _(c):
    c.poly([(3, 6), (13, 6), (14, 14), (2, 14)], "A89060")
    c.disc(8, 6, 5.0, "C8642A", ry=2.4)
    for x, y in ((6, 5), (9, 6), (11, 5), (7, 7)):
        c.dot(x, y, "8A2A14")
    c.line(3, 8, 13, 8, "6A5034")
    c.dot(6, 11, "6A1418")
    c.dot(10, 11, "6A1418")


@icon("dagang", "porselen_hijau")
def _(c):
    c.rect(6, 1, 9, 2, "8ABCA0")
    c.rect(7, 3, 8, 4, "9ACCB0")
    c.disc(8, 9.5, 5.0, "8ABCA0", ry=5.0)
    c.rect(6, 14, 9, 14, "6A9A80")
    c.line(4, 9, 12, 9, "5A8A70")
    c.dot(6, 7, "D8F0E0")
    c.dot(5, 8, "C8E8D8")


@icon("dagang", "kurma_emas")
def _(c):
    for cx, cy in ((5, 9), (9, 7), (11, 11), (7, 12)):
        c.disc(cx, cy, 2.6, "B8781E", ry=1.8)
        c.dot(int(cx) - 1, int(cy) - 1, "F0C060")
    c.line(8, 2, 9, 6, "6A4A2E")
    c.line(9, 2, 12, 3, "5A7A34")


@icon("dagang", "permadani_gurun")
def _(c):
    c.rect(1, 5, 14, 11, "8E1F1F")
    c.rect(1, 5, 14, 5, "E0B040")
    c.rect(1, 11, 14, 11, "E0B040")
    for x in range(2, 14, 3):
        c.dot(x, 8, "2E3A6A")
        c.dot(x + 1, 7, "EDE8DC")
        c.dot(x + 1, 9, "EDE8DC")
    c.disc(14, 8, 3.0, "6A1418")
    c.dot(14, 8, "E0B040")


@icon("dagang", "giok_barat")
def _(c):
    c.poly([(3, 5), (8, 2), (13, 5), (13, 11), (8, 14), (3, 11)], "5FA88C")
    c.poly([(3, 5), (8, 2), (13, 5), (8, 8)], "8AD0B0")
    c.line(8, 8, 8, 14, "3A7A60")
    c.dot(6, 4, "E8FFF4")


@icon("dagang", "kaca_warna")
def _(c):
    c.poly([(2, 12), (6, 3), (9, 12)], "C82828")
    c.poly([(7, 13), (10, 4), (14, 13)], "2E6AC8")
    c.poly([(4, 14), (8, 8), (11, 14)], "E0B040")
    c.dot(6, 5, "FFD0D0")
    c.dot(10, 6, "D0E0FF")


@icon("dagang", "sutra_awan")
def _(c):
    c.disc(4, 8, 3.0, "D8E0F0")
    c.rect(4, 5, 13, 11, "D8E0F0")
    c.disc(13, 8, 3.0, "B8C8E0")
    c.disc(13, 8, 1.2, "8A9AB8", light=False)
    for x in range(5, 13, 3):
        c.line(x, 5, x + 1, 11, "F4F8FF")
    c.line(7, 4, 7, 12, "A82828")


@icon("dagang", "teh_bi_luo")
def _(c):
    c.rect(3, 5, 12, 14, "7A4A2A")
    c.rect(2, 3, 13, 5, "5A3A22")
    c.rect(6, 8, 9, 11, "E8DDC0")
    c.dot(7, 9, "2E7A4A")
    c.dot(8, 10, "2E7A4A")
    c.rect(3, 13, 12, 13, "3A2A20", light=False)


@icon("dagang", "arak_beras")
def _(c):
    c.disc(8, 10, 5.5, "A0603A", ry=5.0)
    c.rect(6, 2, 9, 4, "A0603A")
    c.rect(5, 1, 10, 2, "C82828")
    c.rect(5, 8, 10, 11, "C82828")
    c.dot(7, 9, "E0B040")
    c.dot(8, 10, "E0B040")
    c.dot(5, 6, "D8905A")


@icon("dagang", "herba_tebing_kering")
def _(c):
    for x, top in ((5, 3), (7, 2), (9, 2), (11, 4)):
        c.line(8, 14, x, top, "8A9A5A")
        c.dot(x, top, "C8A040")
    c.rect(6, 9, 10, 10, "B8A070")
    c.dot(4, 5, "A8783A")
    c.dot(12, 6, "A8783A")


@icon("dagang", "dupa_gunung")
def _(c):
    c.rect(2, 7, 13, 13, "8E1F1F")
    c.rect(2, 6, 13, 7, "A82828")
    c.rect(6, 9, 9, 11, "E0B040")
    for x in (4, 6, 8, 10, 12):
        c.line(x, 2, x, 6, "A84A2A")
    c.dot(6, 1, "C8C8C8")


@icon("dagang", "teh_awan_gunung")
def _(c):
    c.disc(8, 9, 6.0, "6A7A4A", ry=4.4)
    c.disc(8, 8, 5.5, "8A9A5A", ry=3.6)
    c.disc(8, 8, 2.5, "E8E4D8", ry=1.6)
    c.dot(8, 8, "2E5E8C")
    for x, y in ((4, 8), (12, 8), (7, 11)):
        c.dot(x, y, "5A6A3A")


@icon("dagang", "lingzhi_gunung")
def _(c):
    c.disc(8, 7, 6.5, "8A3A1E", ry=3.6)
    c.disc(8, 6, 5.5, "B85A2A", ry=2.6)
    for r in (2.0, 3.8):
        for a in range(200, 340, 20):
            t = math.radians(a)
            c.dot(int(8 + r * 1.3 * math.cos(t)), int(7 + r * 0.7 * math.sin(t)), "E0A040")
    c.rect(7, 10, 8, 14, "C8A070")
    c.rect(5, 14, 10, 14, "6A4A2E")


@icon("dagang", "racun_ular_hitam")
def _(c):
    vial(c, "8A8A98", "1C1418")
    for x in range(5, 11):
        y = int(round(9 + math.sin(x * 1.2)))
        c.dot(x, y, "5A9A3A")
    c.dot(9, 8, "E0E040")


@icon("dagang", "minyak_bayangan")
def _(c):
    bottle(c, "3A3448", "141018", cork="1C1418")
    for x, y in ((6, 11), (9, 10), (10, 12)):
        c.dot(x, y, "8A4AC8")
    c.dot(4, 9, "3A6AC8")


# ================================================================================================ output

def write_icon_item(folder, name, img):
    tex_dir = os.path.join(ROOT, "textures", "item", folder)
    os.makedirs(tex_dir, exist_ok=True)
    img.save(os.path.join(tex_dir, name + ".png"))
    g.write_json(os.path.join(ROOT, "models", "item", folder, name + ".json"),
                 {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s/%s" % (NS, folder, name)}})
    g.write_json(os.path.join(ROOT, "items", folder, name + ".json"),
                 {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, folder, name)}})


def model3d_json(m):
    textures = {key: "%s:item/fx/tempat/tema/%s" % (NS, name) for name, key in m.tex.items()}
    textures["particle"] = textures[next(iter(m.tex.values()))]
    return {"textures": textures, "elements": m.els, "display": g.display(m)}


def expected_items():
    """The registered ids, read from TemaItems / DagangItems so a missing icon is an error here, not in game."""
    import re
    base = os.path.join(HERE, "..", "src", "main", "java", "dev", "kultivator", "dunia", "tempat", "tema")
    tema = re.findall(r'daftar\("([a-z_]*[a-z])"', open(os.path.join(base, "TemaItems.java"), encoding="utf-8").read())
    jenis = re.findall(r'^\s+([A-Z_]+)\("([a-z_]+)"', open(os.path.join(base, "iblis", "GulunganTerlarangItem.java"), encoding="utf-8").read(), re.M)
    tema += ["gulungan_" + j[1] for j in jenis]
    src = open(os.path.join(base, "bersama", "DagangItems.java"), encoding="utf-8").read()
    dagang = re.findall(r'= barang\("([a-z_]+)"', src) + re.findall(r'= daftar\("([a-z_]+)"', src)
    return tema, dagang


def sheet(images, path, factor=8, cols=8):
    names = sorted(images)
    rows = (len(names) + cols - 1) // cols
    big = Image(cols * 16 * factor, rows * 16 * factor)
    for i, n in enumerate(names):
        img = images[n]
        ox, oy = (i % cols) * 16 * factor, (i // cols) * 16 * factor
        for y in range(16):
            for x in range(16):
                r, gg, b, a = img.get(x, y)
                bg = 54 if ((x + y) % 2) else 70
                k = a / 255.0
                cc = (r * k + bg * (1 - k), gg * k + bg * (1 - k), b * k + bg * (1 - k), 255)
                for yy in range(factor):
                    for xx in range(factor):
                        big.set(ox + x * factor + xx, oy + y * factor + yy, cc)
    big.save(path)


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    build_materials()
    build_props()
    build_items3d()
    errors = 0
    used = set()
    for m in MODELS + ITEM3D:
        problems = g.validate(m, MAT)
        if problems:
            errors += 1
            print("MODEL %s/%s:" % (m.folder, m.id))
            for p in problems:
                print("   ", p)
        used.update(m.tex)
    for m in MODELS:
        g.write_json(os.path.join(PROP_MODEL_DIR, m.id + ".json"), model3d_json(m))
        g.write_json(os.path.join(PROP_ITEM_DIR, m.id + ".json"),
                     {"model": {"type": "minecraft:model", "model": "%s:item/fx/tempat/tema/%s" % (NS, m.id)}})
    for name in PROP_ORDER:
        if name not in [m.id for m in MODELS]:
            errors += 1
            print("missing prop", name)
    os.makedirs(MAT_DIR, exist_ok=True)
    for name in sorted(used):
        MAT[name].save(os.path.join(MAT_DIR, name + ".png"))
    three_d = {(m.item_folder, m.id): m for m in ITEM3D}
    for (folder, name), m in three_d.items():
        g.write_json(os.path.join(ROOT, "models", "item", folder, name + ".json"), model3d_json(m))
        g.write_json(os.path.join(ROOT, "items", folder, name + ".json"),
                     {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, folder, name)}})
    tema, dagang = expected_items()
    rendered = {}
    for folder, ids in (("tema", tema), ("dagang", dagang)):
        for name in ids:
            if (folder, name) in three_d:
                if (folder, name) in ICONS:
                    # the head items keep a flat icon for previews only; their item model is the cuboid one
                    c = Icon(name)
                    ICONS[(folder, name)](c)
                    rendered[folder + "/" + name] = c.finish()
                continue
            fn = ICONS.get((folder, name))
            if fn is None:
                errors += 1
                print("missing icon %s/%s" % (folder, name))
                continue
            c = Icon(name)
            fn(c)
            img = c.finish()
            rendered[folder + "/" + name] = img
            write_icon_item(folder, name, img)
    extra = sorted(set("%s/%s" % k for k in ICONS) - set("%s/%s" % (f, n) for f, ids in (("tema", tema), ("dagang", dagang)) for n in ids))
    if extra:
        print("icons without an item (not written):", ", ".join(extra))
    print("props %d, 3D items %d, materials %d, icons %d (tema %d, dagang %d), errors %d"
          % (len(MODELS), len(ITEM3D), len(used), len(rendered), len(tema), len(dagang), errors))
    if preview:
        os.makedirs(preview, exist_ok=True)
        sheet({k: v for k, v in rendered.items() if k.startswith("tema/")}, os.path.join(preview, "tema_icons_tema.png"))
        sheet({k: v for k, v in rendered.items() if k.startswith("dagang/")}, os.path.join(preview, "tema_icons_dagang.png"))
        g.sheet({n: MAT[n] for n in used}, os.path.join(preview, "tema_materials.png"))
    if errors:
        sys.exit(1)


if __name__ == "__main__":
    main()
