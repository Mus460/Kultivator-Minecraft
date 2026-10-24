"""Hand props 63-88 of DESIGN 40.5.7 (module 40K): cuboid item models kultivator:fx/sosok/<name> and their 32 px
material textures. Everything is ours (no downloads). Names and indexes are exactly dev.kultivator.sosok.PropId 63-88
(read from PropId.java, never hand-numbered here).

Writes (assets/kultivator/...):
  textures/item/fx/sosok/*.png      materials (32 x 32)
  models/item/fx/sosok/<name>.json  one cuboid model per prop (at most 40 elements)
  items/fx/sosok/<name>.json        item definitions (DataComponents.ITEM_MODEL; PropKatalog 40G, SosokPropLayer)

Rules (shared with 38MD, tools/gen_tempat_models.py validate()): anchor (8, 8, 8) = the grip; +y runs away from the
fist along the handle; the front faces north (-z); carried things stand upright with their grip at the anchor; element
coordinates -16..32, uv 0..16, one rotation axis of -45/-22.5/22.5/45 degrees. Display: thirdperson/fixed/head identity
(SosokPropLayer places them), gui 3/4 view fitted to the slot, ground half size.

Run: python -I tools/gen_sosok_prop.py [preview_dir]
"""
import math
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tempat_models as T  # noqa: E402
from gen_tempat_models import (rgb, mix, shade, tex, wood, bamboo, lacquer, metal, bronze, gold, jade, paper, cloth,  # noqa: E402
                               porcelain, straw, leather, rope, glow, plain, horsetail_tex, Model, validate, model_json,
                               write_json, sheet)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
PROPID = os.path.join(HERE, "..", "src", "main", "java", "dev", "kultivator", "sosok", "PropId.java")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx", "sosok")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx", "sosok")
ITEM_DIR = os.path.join(ROOT, "items", "fx", "sosok")
FOLDER = "sosok"

MAT = {}   # this module's textures (kept apart from 38MD's)


def mat(name, fn, seed):
    tex(name, fn, seed, store=MAT)


def prop_names():
    """{id: name} of PropId 63-88, parsed from PropId.java (constants + put(CONST, "name"))."""
    src = open(PROPID, encoding="utf-8").read()
    consts = {m.group(1): int(m.group(2)) for m in re.finditer(r"public static final int ([A-Z_]+) = (\d+);", src)}
    out = {}
    for m in re.finditer(r'put\(([A-Z_]+), "([a-z_]+)"\)', src):
        pid = consts.get(m.group(1))
        if pid is not None and 63 <= pid <= 88:
            out[pid] = m.group(2)
    return out


def uv(e, side, u0, v0, u1, v1):
    """Overrides one face's uv (whole-face artwork such as glyphs, strings, trigram faces)."""
    if side in e["faces"]:
        e["faces"][side]["uv"] = [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]


def faces_uv(e, sides, box):
    for s in sides:
        uv(e, s, *box)


# ================================================================================================ materials


def talisman():
    """Yellow talisman paper with a vermilion border, a seal circle and a column of command strokes (12 x 32 artwork,
    mapped on a 3 x 8 face)."""
    def p(x, y, rng, n):
        v = 0.88 + 0.14 * n.at(x, y, 4) + rng.random() * 0.05
        c = shade(rgb("E8D070"), v)
        red = rgb("B4231C")
        if x < 12:
            if x in (0, 11) or y in (0, 31) or (x in (1, 10) and 2 <= y <= 29):
                c = red if (x in (0, 11) or y in (0, 31)) else shade(rgb("D8B850"), v)
            # the seal circle at the top
            d = math.hypot(x - 5.5, y - 5.5)
            if 2.6 < d < 4.1:
                c = red
            if d < 1.2:
                c = red
            # the command column: a long stroke with crossbars and hooks
            if 5 <= x <= 6 and 10 <= y <= 27:
                c = red
            if y in (12, 16, 20, 24) and 3 <= x <= 8:
                c = red
            if y == 27 and 3 <= x <= 9:
                c = red
            if (x, y) in ((3, 13), (8, 13), (2, 21), (9, 21), (3, 25), (8, 25), (9, 28), (10, 29)):
                c = red
            if rng.random() < 0.02:
                c = shade(c, 0.85)
        return c
    return p


def woven():
    """Plaited palm leaf of the stove fan: diagonal weave with a darker rim."""
    def p(x, y, rng, n):
        band = ((x + y) // 3 + (x - y) // 3) % 2
        c = mix(rgb("B89A58"), rgb("DCC488"), 0.35 + 0.4 * band + 0.2 * n.at(x, y, 4))
        if (x + y) % 3 == 0:
            c = shade(c, 0.86)
        r = math.hypot(x - 15.5, y - 15.5)
        if r > 14:
            c = shade(rgb("6E5228"), 1.0 + 0.1 * rng.random())
        return shade(c, 0.97 + rng.random() * 0.05)
    return p


def edict_silk():
    """Imperial yellow silk with woven cloud scrolls (the edict scroll)."""
    def p(x, y, rng, n):
        c = shade(rgb("E0B83A"), 0.9 + 0.15 * n.at(x, y, 8))
        cloud = math.sin(x * 0.55 + math.sin(y * 0.4) * 1.6) * math.cos(y * 0.5)
        if 0.55 < cloud < 0.75:
            c = shade(rgb("F4D872"), 1.0)
        if y % 16 in (0, 15):
            c = shade(rgb("A07820"), 1.0)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def ivory_tablet():
    """Ivory hu tablet: warm grain lengthwise, faint ink notes near the top."""
    def p(x, y, rng, n):
        c = mix(rgb("D8CCAA"), rgb("F2EAD2"), 0.5 + 0.5 * math.sin(x * 0.9 + n.at(x, y, 8) * 3))
        if 2 <= y <= 12 and x % 5 == 2 and (x * 3 + y) % 4 != 0:
            c = rgb("3A322A")
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def luopan():
    """Face of the formation disc: concentric bronze rings, the eight trigrams and a black centre pool (whole face)."""
    tris = [(1, 1, 1), (0, 0, 0), (0, 1, 1), (1, 0, 0), (1, 0, 1), (0, 1, 0), (1, 1, 0), (0, 0, 1)]

    def p(x, y, rng, n):
        dx, dy = x - 15.5, y - 15.5
        r = math.hypot(dx, dy)
        c = mix(rgb("8C6A3A"), rgb("C89A58"), n.at(x, y, 4))
        if r < 4.0:
            c = rgb("1A1A20") if r < 3.0 else rgb("E8DDC0")
        elif 7.2 < r < 8.0 or 12.6 < r < 13.4:
            c = rgb("4A3418")
        elif 8.4 <= r <= 12.2:
            ang = (math.degrees(math.atan2(dy, dx)) + 360 + 22.5) % 360
            k = int(ang // 45)
            within = (ang % 45) - 22.5
            if abs(within) < 9:
                line = int((r - 8.4) / 1.27)
                if 0 <= line < 3:
                    solid = tris[k][line]
                    if solid or abs(within) > 2.5:
                        c = rgb("1E1A18")
            if (ang % 45) < 1.2:
                c = rgb("6A4A20")
        elif r >= 14.2:
            c = rgb("5A3E1C")
        elif 4.0 <= r <= 7.2 and int(math.degrees(math.atan2(dy, dx)) + 360) % 30 < 3:
            c = rgb("B4231C")
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def qin_top():
    """Black-lacquered qin top: seven silk strings lengthwise and the thirteen mother-of-pearl hui dots."""
    def p(x, y, rng, n):
        gloss = max(0.0, math.sin((x * 0.3 + y * 0.12))) ** 10
        c = mix(rgb("1A1416"), rgb("5A3A30"), 0.15 * n.at(x, y, 8))
        c = mix(c, (220, 200, 180), gloss * 0.25)
        if x in (5, 8, 11, 14, 17, 20, 23):
            c = rgb("E8DCC0")
        if x == 2 and y % 3 == 1 and 2 <= y <= 30:
            c = rgb("F0F4F8")
        if y in (1, 2) and 3 <= x <= 25:
            c = rgb("C9A24A")   # the bridge (yueshan)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def bone():
    def p(x, y, rng, n):
        c = mix(rgb("C8BC9C"), rgb("EEE6D2"), n.at(x, y, 4))
        if (x * 5 + y * 3) % 23 == 0:
            c = shade(c, 0.8)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def seal_jade():
    """Pale jade carving with vermilion seal paste on the bottom rows."""
    def p(x, y, rng, n):
        c = mix(rgb("6EB896"), rgb("D6F2E2"), n.at(x, y, 8))
        if y >= 28:
            c = rgb("B4231C") if (x + y) % 5 else rgb("D04030")
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def flag_cloth():
    """Formation banner: white-blue silk, red border, a black trigram (kan, water) in the middle."""
    def p(x, y, rng, n):
        c = shade(rgb("D8E2EE"), 0.92 + 0.1 * n.at(x, y, 8))
        if x < 2 or x > 29 or y < 2 or y > 29:
            c = rgb("B4231C")
        elif 10 <= x <= 21:
            if y in (9, 10) or y in (21, 22):
                if not (14 <= x <= 17):
                    c = rgb("1E1A18")
            if y in (15, 16):
                c = rgb("1E1A18")
        if y > 26 and x % 4 == 1 and 2 <= x <= 29:
            c = rgb("C9A24A")  # gold fringe ticks
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def pouch():
    """Qiankun pouch: deep violet brocade with gold cloud embroidery."""
    def p(x, y, rng, n):
        c = shade(rgb("4A2A6A"), 0.88 + 0.2 * n.at(x, y, 8))
        cloud = math.sin(x * 0.7) + math.cos(y * 0.6 + x * 0.2)
        if 1.2 < cloud < 1.5:
            c = rgb("E8C860")
        if (x + y) % 2 == 0:
            c = shade(c, 0.94)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def mirror_face():
    """Polished bronze mirror face: a bright sheen with a soft highlight."""
    def p(x, y, rng, n):
        r = math.hypot(x - 11, y - 10)
        c = mix(rgb("D8C08A"), rgb("FFF4D8"), max(0.0, 1.0 - r / 12.0))
        if math.hypot(x - 15.5, y - 15.5) > 14.5:
            c = rgb("7A5A2A")
        return shade(c, 0.98 + rng.random() * 0.03)
    return p


def mirror_back():
    """Mirror back: cast bronze with a ring of the four beasts' scroll pattern and verdigris."""
    def p(x, y, rng, n):
        dx, dy = x - 15.5, y - 15.5
        r = math.hypot(dx, dy)
        c = mix(rgb("6E4E26"), rgb("B08040"), n.at(x, y, 4))
        if 9 < r < 10.2 or 13.4 < r < 14.6:
            c = rgb("4A3418")
        elif 10.2 <= r <= 13.4 and int(math.degrees(math.atan2(dy, dx)) + 360) % 24 < 8:
            c = rgb("C89A58")
        if n.at((x * 3) % 32, (y * 5) % 32, 4) > 0.82:
            c = mix(c, rgb("5A9C80"), 0.6)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def beads():
    """Bodhi-seed beads: dark brown with a pale pore pattern."""
    def p(x, y, rng, n):
        c = mix(rgb("4A2A18"), rgb("7A4A28"), n.at(x, y, 4))
        if (x * 7 + y * 11) % 9 == 0:
            c = rgb("C8A070")
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def fang():
    def p(x, y, rng, n):
        c = mix(rgb("E8DCC0"), rgb("FFF8E8"), n.at(x, y, 8))
        if y > 24:
            c = mix(c, rgb("8A6A3A"), (y - 24) / 10.0)   # the root stained by age
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def herb_leaf():
    def p(x, y, rng, n):
        c = mix(rgb("3E7A3A"), rgb("8CC060"), n.at(x, y, 8))
        if x == 15 or x == 16:
            c = shade(rgb("C8E098"), 1.0)   # the midrib
        elif abs((y - 16) - abs(x - 15.5) * 0.8) < 0.6:
            c = shade(c, 1.2)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def herb_stem():
    def p(x, y, rng, n):
        c = mix(rgb("5A6A2A"), rgb("9AA850"), n.at(x, y, 4))
        if y % 9 == 0:
            c = shade(c, 0.75)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def blue_white():
    """Blue-and-white porcelain of the pill bottle: a cobalt lotus panel and rim lines."""
    def p(x, y, rng, n):
        c = (240, 242, 246)
        if y in (1, 2, 29, 30):
            c = (40, 66, 150)
        elif 8 <= y <= 22:
            dx, dy = (x % 16) - 7.5, y - 15
            if abs(math.hypot(dx * 0.8, dy) - 5) < 0.7:
                c = (52, 84, 168)
            if abs(dx) < 0.8 and -4 < dy < 4:
                c = (60, 92, 176)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def label_red():
    """Red paper label with a black character block (pill bottle, wine gourd)."""
    def p(x, y, rng, n):
        c = shade(rgb("B8282A"), 0.9 + 0.15 * n.at(x, y, 8))
        if 10 <= x <= 21 and 8 <= y <= 23 and (x % 3 != 0) and (y % 4 != 1):
            c = rgb("1E1A18")
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def spirit_stone():
    """Spirit stone: green-blue crystal glowing from inside, with facet lines."""
    def p(x, y, rng, n):
        c = mix(rgb("2E8C8A"), rgb("9EF4E0"), n.at(x, y, 8))
        if (x + y) % 9 == 0 or (x - y) % 11 == 0:
            c = mix(c, (240, 255, 250), 0.6)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def pill():
    def p(x, y, rng, n):
        r = math.hypot(x - 12, y - 12)
        c = mix(rgb("F2D06A"), rgb("FFF6D0"), max(0.0, 1.0 - r / 14.0))
        if n.at(x, y, 4) > 0.78:
            c = mix(c, rgb("D8902A"), 0.5)   # the cloud marks of a refined pill
        return c
    return p


def red_cord():
    def p(x, y, rng, n):
        c = rgb("B4231C")
        if (x + y * 2) % 4 == 0:
            c = shade(c, 0.75)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def build_materials():
    mat("jimat", talisman(), 101)
    mat("anyaman", woven(), 102)
    mat("bambu", bamboo(103), 103)
    mat("kayu_tua", wood("5A3A22", 104), 104)
    mat("kayu_muda", wood("9C7448", 105), 105)
    mat("kayu_merah", wood("7A2E1E", 106), 106)
    mat("lak_hitam", lacquer("1E1A1E", 107), 107)
    mat("lak_merah", lacquer("8E1F1F", 108), 108)
    mat("ekor_kuda", horsetail_tex(109), 109)
    mat("sutra_titah", edict_silk(), 110)
    mat("sutra_ungu", cloth("5A2A7A", 111), 111)
    mat("gading", ivory_tablet(), 112)
    mat("kulit", leather(113), 113)
    mat("perunggu", bronze(114), 114)
    mat("luopan", luopan(), 115)
    mat("besi", metal("7C8288", 116), 116)
    mat("kuningan", gold(117), 117)
    mat("emas", gold(118), 118)
    mat("kertas", paper(119, lines=True), 119)
    mat("porselen", porcelain(120), 120)
    mat("biru_putih", blue_white(), 121)
    mat("label", label_red(), 122)
    mat("arak", plain("6A2A1E", 0.2), 123)
    mat("labu", plain("C8A040", 0.25), 124)
    mat("batu_roh", spirit_stone(), 125)
    mat("pil", pill(), 126)
    mat("daun", herb_leaf(), 127)
    mat("batang", herb_stem(), 128)
    mat("tali", rope(129), 129)
    mat("tali_merah", red_cord(), 130)
    mat("senar", qin_top(), 131)
    mat("kayu_pedang", wood("C8A070", 132, rings=0.5), 132)
    mat("tulang", bone(), 133)
    mat("giok", jade(134), 134)
    mat("giok_cap", seal_jade(), 135)
    mat("bendera", flag_cloth(), 136)
    mat("kantong", pouch(), 137)
    mat("cermin", mirror_face(), 138)
    mat("cermin_belakang", mirror_back(), 139)
    mat("manik", beads(), 140)
    mat("taring", fang(), 141)
    mat("jerami", straw(142), 142)
    mat("cahaya_hijau", glow("6ED8B0", 143), 143)
    mat("kain_kuning", cloth("D8B040", 144), 144)


# ================================================================================================ props 63-88


def P(name):
    return Model(name, FOLDER)


def build_props():
    # 63 kertas_jimat: a yellow talisman held at its lower end, glyph on both faces, a red cord at the top
    m = P("kertas_jimat")
    e = m.c(0, 3.4, 0, 3.0, 8.0, 0.2, "jimat")
    faces_uv(e, ("north", "south"), (0, 0, 6, 16))
    uv(e, "east", 6.5, 0, 6.7, 16)
    uv(e, "west", 6.5, 0, 6.7, 16)
    m.c(0, 7.6, 0.15, 0.6, 0.6, 0.3, "tali_merah")
    m.c(0, 8.4, 0.15, 0.3, 1.2, 0.3, "tali_merah")
    e = m.c(0.9, 3.0, -0.25, 3.0, 8.0, 0.2, "jimat", rot=("y", 22.5))      # a second sheet fanned behind
    faces_uv(e, ("north", "south"), (0, 0, 6, 16))
    m.c(0, -0.6, 0, 3.2, 0.3, 0.4, "kertas")                                 # the pinch of the fingers

    # 64 kipas_tungku: plaited round fan (16-gon of four squares) on a bamboo handle, rib and binding
    m = P("kipas_tungku")
    for ang in (None, 22.5, 45, -22.5):
        e = m.c(0, 7.5, 0, 5.2, 5.2, 0.4, "anyaman", rot=("z", ang) if ang else None)
        faces_uv(e, ("north", "south"), (1, 1, 15, 15))
    m.c(0, 1.5, 0, 1.0, 5.0, 1.0, "bambu")
    m.c(0, -1.2, 0, 1.3, 0.6, 1.3, "kayu_tua")
    m.c(0, 7.5, -0.3, 0.5, 7.0, 0.3, "bambu")
    m.c(0, 7.5, 0.3, 0.5, 7.0, 0.3, "bambu")
    m.c(0, 4.3, 0, 1.4, 1.0, 1.0, "tali")
    m.c(0, 7.5, -0.35, 6.6, 0.4, 0.25, "bambu")

    # 65 kebut: the Taoist horsetail whisk: lacquered handle, gold rings, a red cord, three tiers of white hair
    m = P("kebut")
    m.c(0, 2.0, 0, 1.0, 8.0, 1.0, "lak_merah")
    m.c(0, -2.3, 0, 1.3, 0.6, 1.3, "emas")
    m.c(0, -2.9, 0, 0.7, 0.7, 0.7, "giok")
    m.c(0, 6.2, 0, 1.4, 0.6, 1.4, "emas")
    m.c(0, 6.9, 0, 1.8, 0.8, 1.8, "kuningan")
    m.c(0, 10.4, 0, 3.0, 6.0, 1.0, "ekor_kuda")
    m.c(0, 14.6, 0, 2.0, 5.0, 1.0, "ekor_kuda")
    m.c(0, 18.2, 0, 1.0, 4.0, 1.0, "ekor_kuda")
    m.c(-1.0, 11.0, 0.4, 1.0, 5.0, 0.6, "ekor_kuda", rot=("z", 22.5))
    m.c(1.0, 11.0, 0.4, 1.0, 5.0, 0.6, "ekor_kuda", rot=("z", -22.5))
    m.c(0, -3.6, 0, 0.3, 1.4, 0.3, "tali_merah")
    m.c(0, -4.6, 0, 0.7, 0.9, 0.7, "tali_merah")

    # 66 papan_titah: the imperial edict, a yellow silk roll with violet brocade caps and a red seal cord
    m = P("papan_titah")
    m.c(0, 0, 0, 2.0, 8.0, 2.0, "sutra_titah")
    m.c(0, 0, 0, 2.2, 0.6, 2.2, "kain_kuning")
    for y in (-4.2, 4.2):
        m.c(0, y, 0, 2.4, 0.4, 2.4, "sutra_ungu")
        m.c(0, y + (0.5 if y > 0 else -0.5), 0, 1.2, 0.6, 1.2, "emas")
        m.c(0, y + (1.0 if y > 0 else -1.0), 0, 0.6, 0.4, 0.6, "giok")
    m.c(0, 1.2, -1.15, 1.2, 1.2, 0.2, "label")
    m.c(1.1, -1.2, 0, 0.3, 2.0, 0.3, "tali_merah")
    m.c(1.1, -2.6, 0, 0.5, 0.8, 0.5, "tali_merah")

    # 67 papan_catatan: the ivory hu tablet of officials, rounded top, a jade-green cord loop at the grip
    m = P("papan_catatan")
    e = m.c(0, 3.5, 0, 2.5, 8.0, 1.0, "gading")
    faces_uv(e, ("north", "south"), (0, 0, 5, 16))
    m.c(0, 8.0, 0, 2.0, 1.0, 1.0, "gading")
    m.c(0, 8.6, 0, 1.2, 0.4, 1.0, "gading")
    m.c(0, -0.8, 0, 2.2, 0.6, 1.0, "gading")
    m.c(0, 0.6, -0.55, 2.6, 0.4, 0.15, "giok")
    m.c(0, 0.6, 0.55, 2.6, 0.4, 0.15, "giok")

    # 68 tali_kekang: a coiled leather leash, a second loop, the strap end with a bronze buckle and a ring
    m = P("tali_kekang")
    m.ring(0, 4.0, 1.0, 4.0, 0.6, "kulit")
    m.ring(1.0, 3.4, 0.8, 3.4, 0.5, "kulit", cz=0.2, cx=0.3)
    m.c(2.2, -2.4, 0, 0.5, 5.0, 1.0, "kulit")
    m.c(2.2, -5.2, 0, 0.8, 0.8, 1.4, "perunggu")
    m.c(2.2, -5.9, 0, 1.2, 0.3, 1.2, "perunggu")
    m.c(-2.0, 0.4, 0, 0.6, 0.6, 0.6, "perunggu")

    # 69 piringan_formasi: the octagonal formation compass (luopan): bronze body, trigram face, pivot and needle
    m = P("piringan_formasi")
    for ang in (None, 45):
        e = m.c(0, 0, 0, 5.0, 0.6, 5.0, "perunggu", rot=("y", ang) if ang else None, overrides={"up": "luopan"})
        uv(e, "up", 0, 0, 16, 16)
    m.c(0, -0.45, 0, 3.6, 0.3, 3.6, "kayu_merah")
    e = m.c(0, 0.32, 0, 4.4, 0.05, 4.4, "luopan")
    uv(e, "up", 1, 1, 15, 15)
    m.c(0, 0.6, 0, 0.6, 0.6, 0.6, "kuningan")
    m.c(0, 0.75, -0.6, 0.4, 0.4, 3.0, "besi", rot=("y", 22.5))
    m.c(0, 0.75, -1.9, 0.25, 0.25, 0.6, "lak_merah", rot=("y", 22.5, (0, 0.75, -0.6)))
    m.c(0, -1.0, 0, 1.0, 1.0, 1.0, "kayu_tua")

    # 70 kunci_kamar: an iron ring with two bronze keys and a wooden room tag
    m = P("kunci_kamar")
    m.ring(0, 3.0, 0.4, 3.0, 0.4, "besi")
    m.c(-0.6, -2.2, -1.2, 0.5, 3.0, 1.0, "kuningan")
    m.c(-0.6, -3.5, -1.5, 0.5, 0.5, 0.6, "kuningan")
    m.c(-0.6, -2.9, -1.5, 0.5, 0.4, 0.6, "kuningan")
    m.c(0.7, -2.0, -1.2, 0.5, 2.6, 1.0, "perunggu", rot=("z", -22.5))
    m.c(1.1, -3.1, -1.5, 0.5, 0.5, 0.5, "perunggu")
    e = m.c(0.4, -2.6, 1.3, 1.6, 2.4, 0.3, "kayu_muda")
    m.c(0.4, -1.1, 1.3, 0.3, 0.8, 0.3, "tali_merah")

    # 71 kotak_obat: the physician's medicine chest, drawers with brass pulls, lid, carried by a leather strap
    m = P("kotak_obat")
    m.c(0, -4.5, 0, 5.0, 4.0, 3.0, "kayu_merah")
    m.c(0, -2.3, 0, 5.4, 0.4, 3.4, "kayu_tua")
    m.c(0, -6.6, 0, 5.4, 0.3, 3.4, "kayu_tua")
    for row in (-3.6, -5.4):
        for col in (-1.2, 1.2):
            m.c(col, row, -1.55, 2.1, 1.5, 0.1, "kayu_muda")
            m.c(col, row, -1.7, 0.5, 0.3, 0.2, "kuningan")
    m.c(-2.2, -0.6, 0, 0.5, 3.0, 0.5, "kulit")
    m.c(2.2, -0.6, 0, 0.5, 3.0, 0.5, "kulit")
    m.c(0, 1.0, 0, 4.9, 0.5, 0.5, "kulit")
    m.c(0, -4.5, 1.55, 1.6, 1.6, 0.1, "label")

    # 72 gulungan: a hand scroll: rolled paper with dark wood roller knobs and a blue silk ribbon
    m = P("gulungan")
    m.c(0, 0, 0, 1.5, 7.0, 1.5, "kertas")
    m.c(0, 2.6, 0, 1.6, 1.2, 1.6, "sutra_ungu")
    for y in (-3.8, 3.8):
        m.c(0, y, 0, 1.0, 0.6, 1.0, "kayu_tua")
        m.c(0, y + (0.45 if y > 0 else -0.45), 0, 0.6, 0.3, 0.6, "giok")
    m.c(0.8, -0.5, 0, 0.25, 1.6, 0.6, "kain_kuning")
    m.c(0.8, -1.8, -0.2, 0.25, 1.4, 0.4, "kain_kuning", rot=("x", 22.5))

    # 73 cawan_arak: a bronze wine cup (jue-like) on a short foot, wine inside
    m = P("cawan_arak")
    m.c(0, 0.5, 0, 1.0, 1.0, 1.0, "perunggu")
    m.c(0, 1.1, 0, 1.6, 0.3, 1.6, "perunggu")
    m.ring(2.0, 2.0, 1.5, 2.0, 0.3, "perunggu")
    m.c(0, 1.35, 0, 1.6, 0.2, 1.6, "perunggu")
    m.c(0, 2.4, 0, 1.4, 0.1, 1.4, "arak")
    m.c(0, 2.85, -1.1, 0.6, 0.3, 0.4, "perunggu")

    # 74 labu_arak: the wine gourd: two bulbs, waist cord, stopper and a red tassel
    m = P("labu_arak")
    m.c(0, -1.5, 0, 3.0, 3.0, 3.0, "labu")
    m.c(0, -1.5, 0, 3.4, 1.6, 2.6, "labu")
    m.c(0, -1.5, 0, 2.6, 1.6, 3.4, "labu")
    m.c(0, 1.0, 0, 1.4, 0.6, 1.4, "labu")
    m.c(0, 2.2, 0, 2.0, 2.0, 2.0, "labu")
    m.c(0, 3.5, 0, 0.8, 0.6, 0.8, "labu")
    m.c(0, 4.0, 0, 0.9, 0.5, 0.9, "kayu_merah")
    m.c(0, 0.9, 0, 1.6, 0.4, 1.6, "tali_merah")
    m.c(0.9, 0.2, -0.6, 0.3, 1.4, 0.3, "tali_merah")
    m.c(0.9, -0.8, -0.6, 0.6, 0.7, 0.6, "tali_merah")
    m.c(0, -1.3, -1.75, 1.4, 1.4, 0.1, "label")

    # 75 pil: a glowing refined pill with a faint halo shell
    m = P("pil")
    m.c(0, 0.6, 0, 1.0, 1.0, 1.0, "pil", glow=12)
    m.c(0, 0.6, 0, 0.7, 1.2, 0.7, "pil", glow=12)
    m.c(0, 0.6, 0, 1.2, 0.7, 0.7, "pil", glow=12)

    # 76 botol_pil: blue-and-white porcelain pill bottle, shoulder, neck, red stopper, a label
    m = P("botol_pil")
    m.c(0, 0.5, 0, 2.0, 3.0, 2.0, "biru_putih")
    m.c(0, 2.2, 0, 1.6, 0.4, 1.6, "porselen")
    m.c(0, 2.6, 0, 0.8, 0.5, 0.8, "porselen")
    m.c(0, 3.1, 0, 1.0, 0.3, 1.0, "porselen")
    m.c(0, 3.5, 0, 0.7, 0.6, 0.7, "lak_merah")
    m.c(0, 3.95, 0, 1.0, 0.3, 1.0, "tali_merah")
    m.c(0, -1.05, 0, 1.6, 0.1, 1.6, "porselen")
    m.c(0, 0.6, -1.05, 1.0, 1.4, 0.1, "label")

    # 77 batu_roh: a spirit stone: two crossed crystal lumps and a glowing core
    m = P("batu_roh")
    m.c(0, 0.6, 0, 2.0, 2.0, 1.5, "batu_roh", glow=9)
    m.c(0, 0.6, 0, 1.6, 1.6, 2.0, "batu_roh", rot=("y", 45), glow=9)
    m.c(0, 1.4, 0, 1.2, 1.4, 1.2, "batu_roh", rot=("z", 45), glow=11)
    m.c(0, 0.6, 0, 0.8, 0.8, 0.8, "cahaya_hijau", glow=15)

    # 78 herba: a bundle of three stems with leaves at the top, tied with straw
    m = P("herba")
    for dx, dz, rz in ((0, 0, None), (-0.5, 0.3, 22.5), (0.5, -0.3, -22.5)):
        m.c(dx, 2.5, dz, 0.5, 5.0, 0.5, "batang", rot=("z", rz) if rz else None)
    for dx, dy, dz, ry in ((0, 5.6, 0, None), (-1.4, 5.0, 0.4, 45), (1.4, 5.2, -0.4, -45), (0, 6.6, 0.3, 22.5)):
        e = m.c(dx, dy, dz, 2.0, 2.0, 0.2, "daun", rot=("y", ry) if ry else None)
        faces_uv(e, ("north", "south"), (2, 2, 14, 14))
    m.c(0, 1.2, 0, 1.4, 0.5, 1.4, "jerami")
    m.c(0, 6.0, 0, 0.6, 0.6, 0.6, "lak_merah")

    # 79 seruling: a bamboo flute: three dark nodes, the blow hole and six finger holes, a red tassel
    m = P("seruling")
    m.c(0, 2.0, 0, 0.8, 12.0, 0.8, "bambu")
    for y in (-2.8, 2.4, 7.2):
        m.c(0, y, 0, 0.9, 0.3, 0.9, "kayu_tua")
    m.c(0, 6.2, -0.42, 0.35, 0.35, 0.05, "lak_hitam")
    for i in range(6):
        m.c(0, -1.6 + i * 0.7, -0.42, 0.25, 0.25, 0.05, "lak_hitam")
    m.c(0, -4.2, 0, 0.3, 0.8, 0.3, "tali_merah")
    m.c(0, -5.0, 0, 0.6, 1.0, 0.6, "tali_merah")
    m.c(0, -4.0, 0, 0.5, 0.5, 0.5, "giok")

    # 80 guqin: the seven-string zither laid across the lap: lacquered board, strings, bridge, pegs and legs
    m = P("guqin")
    e = m.c(0, 0, 0, 4.0, 1.5, 14.0, "lak_hitam", overrides={"up": "senar"})
    uv(e, "up", 0, 0, 14.5, 16)
    m.c(0, -0.1, -6.2, 4.4, 1.3, 1.6, "lak_hitam")       # the head (wider)
    m.c(0, -0.1, 6.4, 3.6, 1.3, 1.2, "lak_hitam")        # the tail
    m.c(-2.2, -0.2, -3.0, 0.4, 1.0, 2.0, "lak_hitam")    # the waist notches
    m.c(2.2, -0.2, -3.0, 0.4, 1.0, 2.0, "lak_hitam")
    m.c(0, 0.85, -5.9, 3.4, 0.25, 0.3, "kuningan")       # bridge
    m.c(0, 0.85, 6.2, 3.0, 0.2, 0.3, "kayu_tua")         # tail nut
    for i in range(7):
        m.c(-1.5 + i * 0.5, 0.78, 0.2, 0.08, 0.08, 12.0, "kertas")
    m.c(-1.2, -1.2, -5.4, 1.0, 1.0, 1.0, "kayu_tua")
    m.c(1.2, -1.2, -5.4, 1.0, 1.0, 1.0, "kayu_tua")
    m.c(0, -1.0, -6.8, 0.4, 0.8, 0.4, "tali_merah")
    m.c(0, -1.8, -6.8, 0.7, 0.8, 0.7, "tali_merah")

    # 81 pedang_kayu: the training sword of peach wood: blade with a ridge and tip, guard, wrapped grip, pommel
    m = P("pedang_kayu")
    m.c(0, 2.0, 0, 1.0, 4.0, 1.0, "tali")
    m.c(0, -0.4, 0, 1.3, 0.6, 1.3, "kayu_tua")
    m.c(0, 4.4, 0, 4.0, 1.0, 2.0, "kayu_tua")
    m.c(0, 11.9, 0, 2.0, 14.0, 0.6, "kayu_pedang")
    m.c(0, 11.4, 0, 0.6, 13.0, 0.8, "kayu_pedang")
    m.c(0, 19.4, 0, 1.4, 1.0, 0.6, "kayu_pedang")
    m.c(0, 20.2, 0, 0.7, 0.8, 0.6, "kayu_pedang")
    m.c(0, -1.0, 0, 0.3, 0.8, 0.3, "tali_merah")
    m.c(0, -1.8, 0, 0.6, 0.9, 0.6, "tali_merah")

    # 82 peluit: a carved bone whistle with a mouthpiece, a sound window and a leather cord
    m = P("peluit")
    m.c(0, 1.0, 0, 0.8, 3.0, 0.8, "tulang")
    m.c(0, 2.8, 0, 0.6, 0.6, 0.5, "tulang")
    m.c(0, 1.6, -0.42, 0.4, 0.4, 0.05, "lak_hitam")
    m.c(0, -0.6, 0, 0.9, 0.3, 0.9, "tali")
    m.c(0, -1.4, 0, 0.25, 1.4, 0.25, "kulit")

    # 83 cap_sekte: the sect seal: jade block with a crouching beast knob, red cord tassel, vermilion on its face
    m = P("cap_sekte")
    m.c(0, 0.5, 0, 2.0, 3.0, 2.0, "giok_cap")
    m.c(0, 2.2, 0, 2.2, 0.4, 2.2, "giok")
    m.c(0, 2.8, 0.1, 1.4, 0.8, 1.8, "giok")        # beast body
    m.c(0, 3.3, -0.7, 0.9, 0.8, 0.8, "giok")       # head
    m.c(-0.35, 3.8, -0.8, 0.2, 0.3, 0.2, "giok")   # ears
    m.c(0.35, 3.8, -0.8, 0.2, 0.3, 0.2, "giok")
    m.c(0, 3.0, 1.1, 0.4, 0.4, 0.6, "giok")        # tail
    m.c(0, 2.7, 0, 2.3, 0.2, 0.3, "tali_merah")
    m.c(1.2, 1.6, 0, 0.3, 1.8, 0.3, "tali_merah")
    m.c(1.2, 0.4, 0, 0.6, 0.8, 0.6, "tali_merah")
    m.c(0, -1.05, 0, 1.8, 0.1, 1.8, "label")

    # 84 bendera_formasi: a formation banner: pole, cloth with the trigram, gold finial and tassels
    m = P("bendera_formasi")
    m.c(0, 3.0, 0, 0.6, 10.0, 0.6, "kayu_merah")
    m.c(0, -2.2, 0, 0.8, 0.4, 0.8, "kuningan")
    e = m.c(2.8, 5.8, 0, 5.0, 4.0, 0.2, "bendera")
    faces_uv(e, ("north", "south"), (0, 0, 16, 16))
    m.c(2.8, 8.0, 0, 5.4, 0.3, 0.3, "kayu_merah")
    m.c(0, 8.6, 0, 1.0, 0.8, 1.0, "emas")
    m.c(0, 9.3, 0, 0.5, 0.6, 0.5, "emas")
    for x in (1.0, 2.8, 4.6):
        m.c(x, 3.4, 0, 0.3, 0.8, 0.3, "tali_merah")

    # 85 kantong_qiankun: the qiankun pouch: violet brocade, gathered neck, gold drawstring and a glowing knot
    m = P("kantong_qiankun")
    m.c(0, -1.6, 0, 2.0, 2.5, 2.0, "kantong")
    m.c(0, -1.8, 0, 2.4, 1.6, 1.6, "kantong")
    m.c(0, -1.8, 0, 1.6, 1.6, 2.4, "kantong")
    m.c(0, -0.1, 0, 1.2, 0.6, 1.2, "kantong")
    m.c(0, 0.3, 0, 1.6, 0.3, 1.6, "emas")
    m.c(0, 0.9, 0, 0.3, 1.0, 0.3, "emas")
    m.c(0, -1.6, -1.25, 0.7, 0.7, 0.1, "cahaya_hijau", glow=10)
    m.c(0.9, -0.4, -0.8, 0.25, 1.2, 0.25, "emas")

    # 86 cermin_perunggu: a bronze hand mirror: round polished face, cast back, knob, lacquered handle
    m = P("cermin_perunggu")
    for ang in (None, 45):
        e = m.c(0, 6.0, 0, 3.8, 3.8, 0.5, "perunggu", rot=("z", ang) if ang else None,
                overrides={"north": "cermin", "south": "cermin_belakang"})
        faces_uv(e, ("north", "south"), (0, 0, 16, 16))
    e = m.c(0, 6.0, 0, 4.9, 4.9, 0.4, "perunggu", rot=("z", 22.5), overrides={"north": "cermin", "south": "cermin_belakang"})
    faces_uv(e, ("north", "south"), (1, 1, 15, 15))
    m.c(0, 6.0, 0.45, 0.8, 0.8, 0.4, "perunggu")
    m.c(0, 1.6, 0, 0.8, 4.0, 0.6, "lak_merah")
    m.c(0, 3.6, 0, 1.2, 0.6, 0.8, "perunggu")
    m.c(0, -0.6, 0, 1.0, 0.5, 0.8, "perunggu")

    # 87 tasbih_tangan: 12 bodhi beads in a loop, the guru bead and a red tassel (held at the guru bead)
    m = P("tasbih_tangan")
    for i in range(12):
        a = 2 * math.pi * i / 12
        m.c(round(2.4 * math.sin(a), 3), round(2.6 + 2.4 * math.cos(a), 3), 0, 1.0, 1.0, 1.0, "manik")
    m.c(0, 0.0, 0, 1.3, 1.3, 1.3, "giok")
    m.c(0, -0.9, 0, 0.3, 0.8, 0.3, "tali_merah")
    m.c(0, -1.8, 0, 0.7, 1.0, 0.7, "tali_merah")

    # 88 trofi_taring: three beast fangs on a leather cord with bone spacers
    m = P("trofi_taring")
    m.c(0, 0, 0, 6.0, 0.4, 0.4, "kulit")
    for x, rz in ((-1.8, 22.5), (0, None), (1.8, -22.5)):
        m.c(x, -1.4, 0, 0.8, 2.5, 0.8, "taring", rot=("z", rz) if rz else None)
        m.c(x, -2.8, 0, 0.4, 0.6, 0.4, "taring", rot=("z", rz, (x, -1.4, 0)) if rz else None)
    for x in (-0.9, 0.9):
        m.c(x, 0, 0, 0.6, 0.6, 0.6, "tulang")
    m.c(-3.2, 0, 0, 0.5, 0.5, 0.5, "perunggu")
    m.c(3.2, 0, 0, 0.5, 0.5, 0.5, "perunggu")


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    names = prop_names()
    if len(names) != 26:
        raise SystemExit("PropId 63-88: expected 26 names, found %d" % len(names))
    build_materials()
    build_props()
    mine = {mid: m for (folder, mid), m in T.MODELS.items() if folder == FOLDER}
    errors = 0
    for pid, name in sorted(names.items()):
        if name not in mine:
            print("MISSING model for prop %d %s" % (pid, name))
            errors += 1
    for name in sorted(mine):
        if name not in names.values():
            print("EXTRA model %s (no PropId)" % name)
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
        write_json(os.path.join(MODEL_DIR, name + ".json"), model_json(m, "item/fx/sosok"))
        write_json(os.path.join(ITEM_DIR, name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "kultivator:item/fx/sosok/%s" % name}})
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        MAT[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(MAT) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    print("props %d, textures %d, elements max %d, errors %d"
          % (len(mine), len(used), max(len(m.els) for m in mine.values()), errors))
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        sheet({n: MAT[n] for n in used}, os.path.join(preview_dir, "prop_bahan.png"))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
