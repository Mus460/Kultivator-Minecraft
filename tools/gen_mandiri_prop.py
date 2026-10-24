"""Hand and ground props 100-123 of DESIGN 39 (module 39E, DESIGN 40.7.4): cuboid item models
kultivator:fx/sekte_mandiri_<name> and their 32 px material textures, used by client/sekte/mandiri/MandiriPoses through
40G's PropKatalog/SosokPropLayer. Everything is ours (no downloads). Names and order are MandiriPoses.NAMA (checked here
against the Java file, never hand-numbered twice).

Writes (assets/kultivator/...):
  textures/sekte/mandiri/prop/*.png          materials (32 x 32)
  models/item/fx/sekte_mandiri_<name>.json   one cuboid model per prop (at most 40 elements)
  items/fx/sekte_mandiri_<name>.json         item definitions (DataComponents.ITEM_MODEL)

Rules (gen_tempat_models.validate, DESIGN 38.O.0): anchor (8, 8, 8) = the grip; +y runs away from the fist along the
handle; the front faces north (-z); ground pieces (cauldron, low table, incense burner) stand on the anchor (their feet at
y 0) because SosokPropLayer puts them on the ground before the feet; element coordinates -16..32, uv 0..16, one rotation
axis of -45/-22.5/22.5/45 degrees.

Run: python -I tools/gen_mandiri_prop.py [preview_dir]   (preview: material sheet + a 3/4 view of every model)
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tempat_models as T  # noqa: E402
from gen_tempat_models import (rgb, mix, shade, tex, wood, bamboo, lacquer, metal, bronze, gold, jade, paper, cloth, porcelain,  # noqa: E402
                               rice, straw, leather, rope, glow, ember, incense, plain, Model, validate, model_json, write_json, sheet)
from png import Image  # noqa: E402
import gen_sosok_prop as S  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
POSES = os.path.join(HERE, "..", "src", "client", "java", "dev", "kultivator", "client", "sekte", "mandiri", "MandiriPoses.java")
TEX_DIR = os.path.join(ROOT, "textures", "sekte", "mandiri", "prop")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")
FOLDER = "sekte_mandiri"
TEX_FOLDER = "sekte/mandiri/prop"

MAT = {}


def mat(name, fn, seed):
    tex(name, fn, seed, store=MAT)


def names_from_java():
    src = open(POSES, encoding="utf-8").read()
    m = re.search(r"static final String\[\] NAMA = \{(.*?)\};", src, re.S)
    return re.findall(r'"([a-z_]+)"', m.group(1))


# ================================================================================================ materials


def crystal(base, light):
    """A spirit stone: facets with bright edges and a glowing heart."""
    b, l = rgb(base), rgb(light)

    def p(x, y, rng, n):
        r = math.hypot(x - 15.5, y - 15.5) / 22.0
        c = mix(l, b, r + 0.25 * n.at(x, y, 8))
        if (x + y) % 10 == 0 or (x - y) % 13 == 0:
            c = mix(c, (255, 255, 255), 0.55)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def map_paper():
    def p(x, y, rng, n):
        c = shade(rgb("E8D8A8"), 0.9 + 0.15 * n.at(x, y, 8))
        if abs(math.sin(x * 0.45) * 5 + 14 - y) < 0.8:
            c = rgb("3A6AB0")          # a river
        if (x * 3 + y * 5) % 37 == 0:
            c = rgb("6E4A2A")          # mountain marks
        if abs(x - 22) < 1 and abs(y - 8) < 1:
            c = rgb("B4231C")          # the destination
        return c
    return p


def palm_leaf():
    def p(x, y, rng, n):
        c = shade(rgb("C8B060"), 0.85 + 0.25 * n.at(x, y, 4))
        a = math.atan2(y - 31, x - 15.5)
        if int(a * 18) % 2 == 0:
            c = shade(c, 0.88)
        return c
    return p


def build_materials():
    mat("besi_latih", metal("8A9098", 201), 201)
    mat("baja_roh", metal("C8D8E8", 202, glow="9FE0FF"), 202)
    mat("tepi_qi", glow("8FD8FF", 203), 203)
    mat("kayu_tua", wood("5A3A22", 204), 204)
    mat("kayu_muda", wood("9C7448", 205), 205)
    mat("bambu", bamboo(206), 206)
    mat("tali", rope(207), 207)
    mat("tali_merah", S.red_cord(), 208)
    mat("perunggu", bronze(209), 209)
    mat("emas", gold(210), 210)
    mat("giok", jade(211), 211)
    mat("kertas", paper(212, lines=True), 212)
    mat("jimat", S.talisman(), 213)
    mat("kain_biru", cloth("3A5A8A", 214), 214)
    mat("kain_kuning", cloth("D8B040", 215), 215)
    mat("kain_merah", cloth("A02828", 216), 216)
    mat("porselen", porcelain(217), 217)
    mat("bubur", rice(218), 218)
    mat("kulit", leather(219), 219)
    mat("lak_merah", lacquer("8E1F1F", 220), 220)
    mat("lak_hitam", lacquer("1E1A1E", 221), 221)
    mat("abu", plain("8A8680", 0.3), 222)
    mat("dupa", incense(223), 223)
    mat("bara", ember(224), 224)
    mat("tinta", plain("141418", 0.1), 225)
    mat("peta", map_paper(), 226)
    mat("daun_palem", palm_leaf(), 227)
    mat("cahaya_emas", glow("FFE070", 228), 228)
    mat("rambut_kuas", plain("EAE4D8", 0.2), 229)
    mat("vermilion", plain("C8301E", 0.2), 230)
    mat("batu_hijau", crystal("1E8A5A", "C8FFE0"), 231)
    mat("batu_biru", crystal("1E5A9A", "C8E8FF"), 232)
    mat("batu_merah", crystal("9A2A2A", "FFD0C8"), 233)
    mat("batu_emas", crystal("A07A1A", "FFF4C0"), 234)
    mat("labu", plain("C8A040", 0.25), 235)
    mat("gabus", plain("A07850", 0.3), 236)
    mat("pil", S.pill(), 237)


def P(name):
    return Model(name, FOLDER)


def ring_cross(m, cx, cy, cz, w, h, d, skin, glow_lvl=0):
    """A rounded body from two crossed boxes plus a diagonal pair (an octagon from above)."""
    m.c(cx, cy, cz, w, h, d * 0.72, skin, glow=glow_lvl)
    m.c(cx, cy, cz, w * 0.72, h, d, skin, glow=glow_lvl)
    m.c(cx, cy, cz, w * 0.62, h, d * 0.62, skin, rot=("y", 45), glow=glow_lvl)


def stone(m, skin, s=1.0, glow_lvl=8):
    m.c(0, 1.6 * s, 0, 1.8 * s, 3.2 * s, 1.8 * s, skin, glow=glow_lvl)
    m.c(0, 1.6 * s, 0, 1.6 * s, 2.2 * s, 1.6 * s, skin, rot=("y", 45), glow=glow_lvl)
    m.c(0, 3.4 * s, 0, 1.0 * s, 0.8 * s, 1.0 * s, skin, rot=("y", 45), glow=glow_lvl)
    m.c(0, -0.2 * s, 0, 1.0 * s, 0.8 * s, 1.0 * s, skin, rot=("y", 45), glow=glow_lvl)
    m.c(0, 1.6 * s, 0, 2.2 * s, 1.0 * s, 1.2 * s, skin, glow=glow_lvl)


def build_props():
    # 100 pedang_latih: the iron practice sword: blunt rounded tip, fuller, cloud guard, cord-wrapped grip, ring pommel, tassel
    m = P("pedang_latih")
    m.c(0, 2.0, 0, 1.0, 4.0, 1.0, "tali")
    m.c(0, 0.6, 0, 1.2, 0.3, 1.2, "perunggu")
    m.c(0, 3.4, 0, 1.2, 0.3, 1.2, "perunggu")
    m.c(0, -0.5, 0, 1.4, 0.8, 0.6, "perunggu")
    m.c(0, -1.1, 0, 0.8, 0.5, 0.6, "perunggu")
    m.c(0, 4.4, 0, 3.6, 0.8, 1.6, "perunggu")
    m.c(-1.9, 4.8, 0, 0.8, 0.8, 1.2, "perunggu")
    m.c(1.9, 4.8, 0, 0.8, 0.8, 1.2, "perunggu")
    m.c(0, 11.8, 0, 1.8, 13.6, 0.5, "besi_latih")
    m.c(0, 11.6, -0.2, 0.4, 12.0, 0.2, "abu")
    m.c(0, 11.6, 0.2, 0.4, 12.0, 0.2, "abu")
    m.c(0, 18.9, 0, 1.4, 0.8, 0.5, "besi_latih")
    m.c(0, 19.5, 0, 0.8, 0.5, 0.5, "besi_latih")
    m.c(0, -1.8, 0, 0.3, 0.8, 0.3, "tali_merah")
    m.c(0, -2.8, 0, 0.7, 1.2, 0.7, "tali_merah")
    m.c(0, -3.6, 0, 0.9, 0.4, 0.9, "tali_merah")

    # 101 kuali_kecil: the little bronze ding: three legs, round belly with a band, rim, two loop handles, domed lid,
    # coals glowing under it
    m = P("kuali_kecil")
    for a in (0, 120, 240):
        x, z = 2.2 * math.sin(math.radians(a)), 2.2 * math.cos(math.radians(a))
        m.c(round(x, 2), 0.9, round(z, 2), 0.8, 1.8, 0.8, "perunggu")
    ring_cross(m, 0, 3.6, 0, 6.0, 3.6, 6.0, "perunggu")
    m.c(0, 3.4, 0, 6.3, 0.5, 4.4, "emas")
    m.c(0, 3.4, 0, 4.4, 0.5, 6.3, "emas")
    ring_cross(m, 0, 5.6, 0, 6.6, 0.5, 6.6, "perunggu")
    m.c(0, 6.1, 0, 4.0, 0.6, 4.0, "perunggu")
    m.c(0, 6.6, 0, 2.8, 0.5, 2.8, "perunggu")
    m.c(0, 7.1, 0, 0.9, 0.7, 0.9, "emas")
    for x in (-3.5, 3.5):
        m.c(x, 6.6, 0, 0.4, 1.6, 0.4, "perunggu")
        m.c(x, 7.4, 0, 0.4, 0.4, 1.6, "perunggu")
        m.c(x, 6.6, -0.6, 0.4, 1.6, 0.4, "perunggu")
        m.c(x, 6.6, 0.6, 0.4, 1.6, 0.4, "perunggu")
    m.c(0, 0.3, 0, 2.6, 0.4, 2.6, "bara", glow=12)
    m.c(0.6, 0.6, 0.4, 0.8, 0.5, 0.8, "bara", glow=12)
    m.c(-0.7, 0.55, -0.5, 0.7, 0.4, 0.7, "bara", glow=12)

    # 102 kipas_tungku: the round palm-leaf fan: bamboo handle, bound neck, an octagonal leaf, ribs, rim binding
    m = P("kipas_tungku")
    m.c(0, 2.4, 0, 0.6, 5.0, 0.6, "bambu")
    m.c(0, 0.0, 0, 0.8, 0.4, 0.8, "kayu_tua")
    m.c(0, 5.2, 0, 1.0, 0.8, 0.7, "tali")
    for rot in (None, ("z", 45)):
        e = m.c(0, 9.4, 0, 6.4, 6.4, 0.25, "daun_palem", rot=rot, full=True)
    m.c(0, 9.4, 0, 7.4, 4.6, 0.22, "daun_palem", full=True)
    m.c(0, 9.4, 0, 4.6, 7.4, 0.22, "daun_palem", full=True)
    for ang in (-45, -22.5, 0, 22.5, 45):
        m.c(0, 8.6, -0.18, 0.18, 6.4, 0.1, "bambu", rot=("z", ang, (0, 5.6, -0.18)) if ang else None)
    m.c(0, 12.8, 0, 3.4, 0.35, 0.35, "kain_merah")
    m.c(0, 6.0, 0, 3.4, 0.35, 0.35, "kain_merah")

    # 103 meja_jimat: the low talisman table (ground): top, carved apron, four legs, the paper being written, an ink
    # stone with ink, a brush rest with a spare brush, the cinnabar dish and a finished talisman under a paperweight
    m = P("meja_jimat")
    m.c(0, 3.3, 0, 11.0, 0.7, 6.4, "lak_merah")
    m.c(0, 2.7, -3.0, 10.4, 0.6, 0.3, "kayu_tua")
    m.c(0, 2.7, 3.0, 10.4, 0.6, 0.3, "kayu_tua")
    for x in (-5.0, 5.0):
        for z in (-2.7, 2.7):
            m.c(x, 1.4, z, 0.7, 2.8, 0.7, "kayu_tua")
        m.c(x, 0.25, 0, 0.7, 0.5, 6.0, "kayu_tua")
    m.c(0, 3.72, 0.4, 3.0, 0.08, 5.0, "jimat", full=True)
    m.c(-3.6, 3.85, -0.6, 2.4, 0.4, 3.0, "lak_hitam")
    m.c(-3.6, 4.1, -0.6, 1.6, 0.1, 2.0, "tinta")
    m.c(3.4, 3.8, -1.6, 2.0, 0.3, 0.5, "kayu_muda")
    m.c(3.4, 4.1, -1.6, 2.6, 0.3, 0.3, "bambu")
    m.c(4.9, 4.1, -1.6, 0.7, 0.3, 0.3, "rambut_kuas")
    m.c(3.6, 3.75, 1.4, 1.4, 0.3, 1.4, "porselen")
    m.c(3.6, 3.95, 1.4, 1.0, 0.1, 1.0, "vermilion")
    m.c(-1.4, 3.75, 2.0, 2.4, 0.1, 1.6, "jimat", full=True)
    m.c(-1.4, 3.95, 2.0, 1.6, 0.4, 0.5, "giok")

    # 104 kuas_jimat: the talisman brush: bamboo shaft with a ring and a hanging loop, the white tuft dipped vermilion
    m = P("kuas_jimat")
    m.c(0, 2.6, 0, 0.6, 7.0, 0.6, "bambu")
    m.c(0, 6.4, 0, 0.8, 0.6, 0.8, "kayu_tua")
    m.c(0, 7.3, 0, 0.8, 1.4, 0.8, "rambut_kuas")
    m.c(0, 8.3, 0, 0.5, 0.8, 0.5, "vermilion")
    m.c(0, 8.9, 0, 0.25, 0.5, 0.25, "vermilion")
    m.c(0, -0.9, 0, 0.75, 0.3, 0.75, "emas")
    m.c(0, -1.5, 0, 0.2, 0.9, 0.2, "tali_merah")
    m.c(0, -2.2, 0, 0.6, 0.5, 0.2, "tali_merah")

    # 105 buntalan: the travel bundle: a blue cloth bundle with its top knot, crossing bands, a bamboo flute poking out
    m = P("buntalan")
    ring_cross(m, 0, 0.0, 0, 5.4, 4.2, 4.8, "kain_biru")
    m.c(0, 2.5, 0, 1.6, 0.9, 1.6, "kain_biru")
    m.c(-0.9, 3.2, 0, 1.0, 1.0, 0.6, "kain_biru", rot=("z", 22.5))
    m.c(0.9, 3.2, 0, 1.0, 1.0, 0.6, "kain_biru", rot=("z", -22.5))
    m.c(0, 0.0, 0, 5.6, 0.5, 4.0, "kain_kuning")
    m.c(0, 0.0, 0, 0.5, 4.4, 5.0, "kain_kuning")
    m.c(1.6, 2.6, 1.2, 0.5, 4.0, 0.5, "bambu", rot=("z", -22.5))
    m.c(2.2, 4.4, 1.2, 0.6, 0.3, 0.6, "kayu_tua")

    # 106 kotak_punggung: the scholar's bamboo back chest: frame, slatted sides, a little awning, scrolls inside, straps
    m = P("kotak_punggung")
    m.c(0, 0.0, 0, 6.0, 8.0, 4.0, "bambu")
    for x in (-3.1, 3.1):
        m.c(x, 0.0, -2.1, 0.5, 8.6, 0.5, "kayu_tua")
        m.c(x, 0.0, 2.1, 0.5, 8.6, 0.5, "kayu_tua")
    for y in (-3.8, 0.0, 3.8):
        m.c(0, y, -2.1, 6.4, 0.4, 0.4, "kayu_tua")
    m.c(0, 5.2, -0.6, 7.4, 0.4, 5.6, "lak_hitam", rot=("x", -22.5))
    m.c(-1.4, 4.6, 0.4, 1.0, 2.4, 1.0, "kertas")
    m.c(0.2, 4.8, 0.2, 1.0, 2.8, 1.0, "kertas")
    m.c(-1.4, 5.9, 0.4, 1.2, 0.3, 1.2, "kayu_tua")
    m.c(0.2, 6.3, 0.2, 1.2, 0.3, 1.2, "kayu_tua")
    m.c(1.6, 4.4, 0.6, 1.2, 1.6, 1.2, "giok")
    for x in (-2.0, 2.0):
        m.c(x, 1.0, 2.4, 0.8, 7.0, 0.3, "kulit")

    # 107 mangkuk_bubur: a blue-rimmed porcelain bowl of rice porridge with a spoon
    m = P("mangkuk_bubur")
    m.c(0, -0.6, 0, 2.0, 0.4, 2.0, "porselen")
    ring_cross(m, 0, 0.6, 0, 3.6, 2.0, 3.6, "porselen")
    m.c(0, 1.7, 0, 3.0, 0.2, 3.0, "bubur")
    m.c(0, 1.4, -1.85, 3.6, 0.3, 0.12, "kain_biru")
    m.c(0, 1.4, 1.85, 3.6, 0.3, 0.12, "kain_biru")
    m.c(0.6, 2.2, 0.2, 0.5, 0.25, 3.4, "porselen", rot=("x", -22.5))

    # 108 kantong_batu: the leather stone pouch: round body, gathered neck, drawstring, three glowing stones peeking out
    m = P("kantong_batu")
    ring_cross(m, 0, -1.2, 0, 3.2, 3.0, 3.2, "kulit")
    m.c(0, 0.6, 0, 2.0, 0.8, 2.0, "kulit")
    m.c(0, 1.0, 0, 2.4, 0.3, 2.4, "tali")
    m.c(1.4, 0.4, 0, 0.3, 1.2, 0.3, "tali")
    m.c(1.4, -0.4, 0, 0.6, 0.6, 0.6, "emas")
    m.c(-0.4, 1.5, -0.2, 0.8, 0.9, 0.8, "batu_hijau", glow=10, rot=("y", 45))
    m.c(0.4, 1.4, 0.3, 0.7, 0.8, 0.7, "batu_biru", glow=10, rot=("y", 45))
    m.c(0.1, 1.7, 0.5, 0.6, 0.7, 0.6, "batu_emas", glow=10)

    # 109 kertas_segel: a seal talisman strip with a soft glow behind it
    m = P("kertas_segel")
    e = m.c(0, 3.0, 0, 3.0, 8.0, 0.12, "jimat", full=True)
    m.c(0, 3.0, 0.1, 3.4, 8.4, 0.05, "cahaya_emas", glow=8, full=True)
    m.c(0, -1.3, 0, 0.3, 0.7, 0.1, "tali_merah")

    # 110-113 four spirit stones: the faceted crystal and its glowing heart
    for name, skin in (("batu_hijau", "batu_hijau"), ("batu_biru", "batu_biru"), ("batu_merah", "batu_merah"), ("batu_emas", "batu_emas")):
        m = P(name)
        stone(m, skin)

    # 114 pedang_terbang: the flying spirit sword: long blade with a glowing qi edge inlay, jade guard, silk-wrapped grip,
    # gold pommel and a long red tassel
    m = P("pedang_terbang")
    m.c(0, 2.2, 0, 1.0, 4.4, 1.0, "kain_biru")
    m.c(0, 0.8, 0, 1.2, 0.3, 1.2, "emas")
    m.c(0, 3.8, 0, 1.2, 0.3, 1.2, "emas")
    m.c(0, -0.4, 0, 1.3, 0.8, 1.3, "emas")
    m.c(0, 4.8, 0, 4.2, 0.9, 1.6, "giok")
    m.c(0, 5.3, 0, 2.0, 0.6, 1.2, "emas")
    m.c(0, 13.6, 0, 2.2, 16.0, 0.5, "baja_roh")
    m.c(-1.18, 13.4, 0, 0.18, 15.0, 0.4, "tepi_qi", glow=13)
    m.c(1.18, 13.4, 0, 0.18, 15.0, 0.4, "tepi_qi", glow=13)
    m.c(0, 13.4, -0.27, 0.35, 14.6, 0.06, "tepi_qi", glow=10)
    m.c(0, 22.1, 0, 1.6, 1.0, 0.5, "baja_roh")
    m.c(0, 23.0, 0, 0.9, 0.9, 0.5, "baja_roh")
    m.c(0, 23.7, 0, 0.4, 0.6, 0.4, "tepi_qi", glow=13)
    m.c(0, -1.4, 0, 0.3, 1.2, 0.3, "tali_merah")
    m.c(0, -2.8, 0, 0.8, 1.6, 0.8, "tali_merah")
    m.c(0, -3.9, 0, 1.0, 0.6, 1.0, "tali_merah")

    # 115 gulungan_baca: an open scroll held across the hands: paper with columns of text and a seal, two rollers
    m = P("gulungan_baca")
    m.c(0, 0, 0, 8.6, 5.8, 0.15, "kertas", full=True)
    for i in range(6):
        m.c(-3.0 + i * 1.2, 0.3 - (i % 2) * 0.4, -0.1, 0.25, 3.8 - (i % 3) * 0.8, 0.05, "tinta")
    m.c(3.0, -2.0, -0.1, 0.8, 0.8, 0.05, "vermilion")
    for x in (-4.6, 4.6):
        m.c(x, 0, 0, 0.8, 6.8, 0.8, "kayu_tua")
        m.c(x, 3.6, 0, 1.0, 0.4, 1.0, "giok")
        m.c(x, -3.6, 0, 1.0, 0.4, 1.0, "giok")
    m.c(0, 3.0, 0, 8.6, 0.3, 0.2, "kain_kuning")
    m.c(0, -3.0, 0, 8.6, 0.3, 0.2, "kain_kuning")

    # 116 tungku_dupa: the incense burner (ground): three legs, a round bronze bowl with ears, ash, three sticks with
    # glowing tips
    m = P("tungku_dupa")
    for a in (60, 180, 300):
        x, z = 1.6 * math.sin(math.radians(a)), 1.6 * math.cos(math.radians(a))
        m.c(round(x, 2), 0.6, round(z, 2), 0.6, 1.2, 0.6, "perunggu")
    ring_cross(m, 0, 2.2, 0, 4.4, 2.2, 4.4, "perunggu")
    ring_cross(m, 0, 3.4, 0, 4.8, 0.4, 4.8, "perunggu")
    m.c(0, 3.5, 0, 3.6, 0.2, 3.6, "abu")
    for x in (-2.6, 2.6):
        m.c(x, 4.1, 0, 0.4, 1.2, 0.8, "perunggu")
        m.c(x, 4.8, 0, 0.4, 0.4, 1.4, "perunggu")
    m.c(0, 2.2, -2.3, 1.2, 0.8, 0.15, "emas")
    for x, rz in ((-0.8, 22.5), (0.0, None), (0.8, -22.5)):
        m.c(x, 5.8, 0, 0.2, 4.6, 0.2, "dupa", rot=("z", rz, (x, 3.6, 0)) if rz else None)
    m.c(0.0, 8.15, 0, 0.3, 0.3, 0.3, "bara", glow=14)
    m.c(-1.65, 7.75, 0, 0.3, 0.3, 0.3, "bara", glow=14)
    m.c(1.65, 7.75, 0, 0.3, 0.3, 0.3, "bara", glow=14)

    # 117 tongkat_jalan: the traveller's staff: a knotted wood staff, a gourd tied below the crook, a cloth tie
    m = P("tongkat_jalan")
    m.c(0, 6.0, 0, 0.8, 24.0, 0.8, "kayu_muda")
    for y in (-2.0, 4.0, 10.0):
        m.c(0, y, 0, 1.0, 0.5, 1.0, "kayu_tua")
    m.c(0.8, 18.4, 0, 1.4, 0.8, 0.8, "kayu_muda", rot=("z", -45))
    m.c(0, 16.0, 0, 1.1, 0.6, 1.1, "tali")
    m.c(1.2, 14.6, 0, 0.3, 1.6, 0.3, "tali")
    ring_cross(m, 1.2, 12.6, 0, 2.0, 1.8, 2.0, "labu")
    ring_cross(m, 1.2, 10.6, 0, 2.6, 2.4, 2.6, "labu")
    m.c(1.2, 13.6, 0, 0.6, 0.4, 0.6, "gabus")
    m.c(-0.6, 15.4, 0, 0.6, 2.4, 0.3, "kain_merah", rot=("z", 22.5))

    # 118 labu_air: the water gourd at the waist: two lobes, a cork, a red cord loop
    m = P("labu_air")
    ring_cross(m, 0, 2.2, 0, 1.8, 1.6, 1.8, "labu")
    ring_cross(m, 0, 0.0, 0, 2.6, 2.6, 2.6, "labu")
    m.c(0, 1.2, 0, 1.2, 0.4, 1.2, "tali_merah")
    m.c(0, 3.2, 0, 0.7, 0.5, 0.7, "gabus")
    m.c(0, 3.9, 0, 0.25, 1.0, 0.25, "tali_merah")

    # 119 peta_gulung: a rolled map tied with a ribbon, the end flap open on rivers and mountains
    m = P("peta_gulung")
    ring_cross(m, 0, 2.6, 0, 1.6, 7.0, 1.6, "peta")
    m.c(0, 6.3, 0, 1.8, 0.4, 1.8, "kayu_tua")
    m.c(0, -1.1, 0, 1.8, 0.4, 1.8, "kayu_tua")
    m.c(0, 2.6, 0, 1.8, 0.5, 1.8, "kain_merah")
    m.c(1.2, 2.1, 0, 0.3, 1.2, 0.2, "kain_merah")
    e = m.c(1.9, 4.2, -0.9, 3.0, 3.6, 0.1, "peta", full=True)

    # 120 jimat_jadi: the finished talisman, glowing gold at its edges
    m = P("jimat_jadi")
    m.c(0, 3.0, 0, 3.0, 8.0, 0.12, "jimat", full=True, glow=6)
    m.c(0, 3.0, 0.08, 3.6, 8.6, 0.05, "cahaya_emas", glow=12, full=True)
    m.c(0, 3.0, -0.08, 3.6, 8.6, 0.05, "cahaya_emas", glow=12, full=True)

    # 121 pil_tangan: a refined pill between the fingers, glowing
    m = P("pil_tangan")
    ring_cross(m, 0, 0.8, 0, 1.4, 1.4, 1.4, "pil", glow_lvl=10)
    m.c(0, 0.8, 0, 1.0, 1.7, 1.0, "pil", glow=10)

    # 122 kotak_hadiah: the gift box: red lacquer with gold corners, a yellow silk wrap knotted on top, a jade tag
    m = P("kotak_hadiah")
    m.c(0, 0, 0, 5.0, 3.6, 4.0, "lak_merah")
    for x in (-2.4, 2.4):
        for z in (-1.9, 1.9):
            m.c(x, 1.6, z, 0.5, 0.5, 0.5, "emas")
            m.c(x, -1.6, z, 0.5, 0.5, 0.5, "emas")
    m.c(0, 0, 0, 5.2, 0.6, 4.2, "kain_kuning")
    m.c(0, 0, 0, 0.8, 3.8, 4.2, "kain_kuning")
    m.c(0, 2.2, 0, 1.4, 0.8, 1.0, "kain_kuning")
    m.c(-0.9, 2.5, 0, 1.2, 0.9, 0.5, "kain_kuning", rot=("z", 22.5))
    m.c(0.9, 2.5, 0, 1.2, 0.9, 0.5, "kain_kuning", rot=("z", -22.5))
    m.c(1.6, 0.6, -2.2, 1.0, 1.4, 0.15, "giok")

    # 123 panji_sekte: the sect pennant: a red lacquer pole, a stepped triangular banner with the gold emblem, tassels
    m = P("panji_sekte")
    m.c(0, 6.0, 0, 0.6, 18.0, 0.6, "lak_merah")
    m.c(0, 15.4, 0, 0.9, 0.8, 0.9, "emas")
    m.c(0, 16.2, 0, 0.5, 0.8, 0.5, "emas")
    for i, w in enumerate((6.0, 5.0, 4.0, 3.0, 2.0, 1.0)):
        m.c(0.3 + w / 2, 13.6 - i * 1.0, 0, w, 1.0, 0.15, "kain_merah", full=True)
    m.c(2.4, 12.6, -0.1, 1.6, 1.6, 0.1, "emas")
    m.c(2.4, 12.6, -0.15, 0.8, 0.8, 0.05, "kain_merah")
    m.c(0.3, 14.3, 0, 6.2, 0.4, 0.3, "emas")
    for x in (1.4, 3.4, 5.4):
        m.c(x, 13.2, 0, 0.3, 0.5, 0.3, "tali_merah")


# ================================================================================================ preview


def iso(models, path, cell=96):
    """A tiny painter's-algorithm 3/4 view of every model (face colour = mean texel of its material, shaded per side)."""
    names = list(models)
    cols = 6
    rows = (len(names) + cols - 1) // cols
    img = Image(cols * cell, rows * cell, (58, 64, 70, 255))
    mean = {}
    for k, t in MAT.items():
        r = g = b = n = 0
        for y in range(t.height):
            for x in range(t.width):
                p = t.get(x, y)
                r, g, b, n = r + p[0], g + p[1], b + p[2], n + 1
        mean[k] = (r / n, g / n, b / n)
    yaw, pitch = math.radians(35), math.radians(28)

    def proj(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x1 = x * math.cos(yaw) - z * math.sin(yaw)
        z1 = x * math.sin(yaw) + z * math.cos(yaw)
        y1 = y * math.cos(pitch) - z1 * math.sin(pitch)
        z2 = y * math.sin(pitch) + z1 * math.cos(pitch)
        return x1, -y1, z2

    for idx, name in enumerate(names):
        m = models[name]
        ox, oy = (idx % cols) * cell + cell / 2, (idx // cols) * cell + cell / 2
        lo, hi = m.bounds()
        size = max(hi[i] - lo[i] for i in range(3)) or 1
        s = cell * 0.7 / size
        mid = [(lo[i] + hi[i]) / 2 for i in range(3)]
        polys = []
        for e in m.els:
            f, t = e["from"], e["to"]
            corners = [(x, y, z) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
            rot = e.get("rotation")
            if rot:
                a = math.radians(rot["angle"])
                o = rot["origin"]
                ax = rot["axis"]
                out = []
                for (x, y, z) in corners:
                    x, y, z = x - o[0], y - o[1], z - o[2]
                    if ax == "x":
                        y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
                    elif ax == "y":
                        x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
                    else:
                        x, y = x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)
                    out.append((x + o[0], y + o[1], z + o[2]))
                corners = out
            faces = {"down": (0, 4, 5, 1), "up": (2, 3, 7, 6), "north": (0, 2, 6, 4), "south": (1, 5, 7, 3), "west": (0, 1, 3, 2),
                     "east": (4, 6, 7, 5)}
            light = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.7, "east": 0.85, "west": 0.65}
            for side, ids in faces.items():
                face = e["faces"].get(side)
                if not face:
                    continue
                key = face["texture"][1:]
                matname = next((n for n, k in m.tex.items() if k == key), None)
                col = mean.get(matname, (200, 0, 200))
                glow_lvl = e.get("light_emission", 0)
                f2 = light[side] if not glow_lvl else 1.1
                pts = [proj(((corners[i][0] - mid[0]) + 8, (corners[i][1] - mid[1]) + 8, (corners[i][2] - mid[2]) + 8)) for i in ids]
                depth = sum(p[2] for p in pts) / 4
                polys.append((depth, [(ox + p[0] * s, oy + p[1] * s) for p in pts], tuple(min(255, c * f2) for c in col)))
        polys.sort(key=lambda q: -q[0])
        for _, pts, col in polys:
            fillpoly(img, pts, col)
    img.save(path)


def fillpoly(img, pts, col):
    ys = [p[1] for p in pts]
    for y in range(max(0, int(min(ys))), min(img.height, int(max(ys)) + 1)):
        xs = []
        n = len(pts)
        for i in range(n):
            (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
            if (y0 <= y + 0.5 < y1) or (y1 <= y + 0.5 < y0):
                xs.append(x0 + (y + 0.5 - y0) * (x1 - x0) / (y1 - y0))
        xs.sort()
        for a, b in zip(xs[0::2], xs[1::2]):
            for x in range(max(0, int(a + 0.5)), min(img.width, int(b + 0.5))):
                img.set(x, y, (col[0], col[1], col[2], 255))


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    names = names_from_java()
    if len(names) != 24:
        raise SystemExit("MandiriPoses.NAMA: expected 24 names, found %d" % len(names))
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
        write_json(os.path.join(MODEL_DIR, "sekte_mandiri_%s.json" % name), model_json(m, TEX_FOLDER))
        write_json(os.path.join(ITEM_DIR, "sekte_mandiri_%s.json" % name),
                   {"model": {"type": "minecraft:model", "model": "kultivator:item/fx/sekte_mandiri_%s" % name}})
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        MAT[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(MAT) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    print("props %d, textures %d, elements max %d, errors %d" % (len(mine), len(used), max(len(m.els) for m in mine.values()), errors))
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        sheet({n: MAT[n] for n in used}, os.path.join(preview_dir, "mandiri_prop_bahan.png"))
        iso({n: mine[n] for n in names}, os.path.join(preview_dir, "mandiri_prop_model.png"))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
