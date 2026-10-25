"""Models and textures of DESIGN 41 "Gabung Sekte" (41.11.3-41.11.5, module 41V-M). Everything is ours: hand-placed pixel
sprites, cuboid item models and procedural textures; nothing is downloaded.

Writes (assets/kultivator/...):
  textures/item/gabung/*.png                 the 41 item sprites (16 x 16): Lencana Anggota (three frames by rank group +
                                             a greyscale jade face tinted with the sect colour + the carved emblem),
                                             Tanda Calon, Token Naik Sekte, Surat Undangan, Surat Restu, Batu Pikul
  models/item/gabung/*.json                  item/generated models of those sprites
  items/<id>.json                            item definitions; lencana_anggota dispatches on kultivator:pangkat_anggota
                                             and tints layer 1 with kultivator:warna_anggota (client/sekte/gabung/model)
  textures/sekte/gabung/prop/*.png           prop materials (32 x 32)
  models/item/fx/sekte_gabung_<name>.json    props 150-156 (GabungGerakId.PROP_NAMA, frozen model path
  items/fx/sekte_gabung_<name>.json          GabungGerakId.propModel), drawn through PropKatalog/SosokPropLayer
  textures/entity/gabung/*.png               band atlases (32 px bands) of the four test entities and their glow sheets,
                                             the illusion wall sheet, and the member robe's three atlases (cloth, trims,
                                             style ornaments) painted on the UV footprints read from JubahSekteLayer.java

Prop rules are 38.O.0 (gen_tempat_models.validate): anchor (8, 8, 8) = the grip, +y away from the fist, front north,
coordinates -16..32, one rotation axis of +-22.5/45 degrees, at most 40 elements.

Run: python -I tools/gen_gabung_assets.py [preview_dir]   (preview: sprite sheet, prop materials, a 3/4 view of every prop,
the entity and robe atlases at 4x)
"""
import json
import math
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_tempat_models as T  # noqa: E402
from gen_tempat_models import (rgb, mix, shade, tex, wood, bamboo, lacquer, bronze, gold, jade, paper, cloth, rope, stone,  # noqa: E402
                               ember, incense, plain, Model, validate, model_json, write_json, sheet, Noise, stable)
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "..")
ROOT = os.path.join(REPO, "src", "main", "resources", "assets", "kultivator")
GERAK_ID = os.path.join(REPO, "src", "main", "java", "dev", "kultivator", "sekte", "gabung", "api", "GabungGerakId.java")
JUBAH_JAVA = os.path.join(REPO, "src", "client", "java", "dev", "kultivator", "client", "sekte", "gabung", "JubahSekteLayer.java")

ITEM_TEX = os.path.join(ROOT, "textures", "item", "gabung")
ITEM_MODEL = os.path.join(ROOT, "models", "item", "gabung")
ITEM_DEF = os.path.join(ROOT, "items")
PROP_TEX = os.path.join(ROOT, "textures", "sekte", "gabung", "prop")
PROP_MODEL = os.path.join(ROOT, "models", "item", "fx")
PROP_DEF = os.path.join(ROOT, "items", "fx")
ENT_TEX = os.path.join(ROOT, "textures", "entity", "gabung")
FOLDER = "sekte_gabung"
PROP_TEX_FOLDER = "sekte/gabung/prop"

WRITTEN = []
SPRITES = {}
ENTITY = {}
MAT = {}


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    WRITTEN.append(os.path.relpath(path, REPO).replace("\\", "/"))


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, v))


# ================================================================================================ item sprites (16 x 16)

def sprite(rows, palette):
    assert len(rows) == 16, len(rows)
    img = Image(16, 16)
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row, len(row))
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            img.set(x, y, palette[ch])
    return img


# The badge: a square jade plaque with notched corners in a metal frame, hung from a red cord knot, a short tassel below.
# Layer 0 = frame, cord and tassel (untinted); layer 1 = the jade face in greyscale (tinted with the sect colour);
# layer 2 = the carved emblem (a peak over a sword: the sect gate), pale gold, untinted.
LENCANA_FRAME = [
    "......rRRr......",
    ".....r....r.....",
    "......rkkr......",
    "....oFFFFFFo....",
    "...oFf....fdo...",
    "...oF......do...",
    "...oF......do...",
    "...oF......do...",
    "...oF......do...",
    "...oF......do...",
    "...oF......do...",
    "...oFf....fdo...",
    "....oddddddo....",
    "......rkkr......",
    "......RrrR......",
    ".......RR.......",
]
LENCANA_GIOK = [
    "................",
    "................",
    "................",
    "................",
    "......hhhh......",
    ".....hHHHhm.....",
    ".....hHmmmm.....",
    ".....hmmmms.....",
    ".....hmmmms.....",
    ".....mmmmss.....",
    ".....mmmsss.....",
    "......ssss......",
    "................",
    "................",
    "................",
    "................",
]
LENCANA_LAMBANG = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......g........",
    "......gGg.......",
    ".....g.G.g......",
    ".......G........",
    "......ggg.......",
    ".......G........",
    "................",
    "................",
    "................",
    "................",
    "................",
]
FRAME_PALETTES = {
    # Pelayan .. Luar: dark peach-wood frame with bronze studs feel
    "kayu": {"o": (40, 24, 12), "F": (122, 78, 42), "f": (168, 116, 66), "d": (82, 50, 26)},
    # Dalam, Inti: silver frame
    "perak": {"o": (46, 50, 58), "F": (176, 184, 196), "f": (236, 240, 246), "d": (110, 116, 128)},
    # Pewaris and above, Tetua Tamu: gold frame
    "emas": {"o": (70, 44, 10), "F": (214, 164, 58), "f": (255, 230, 140), "d": (150, 102, 28)},
}
CORD = {"r": (150, 26, 24), "R": (198, 46, 36), "k": (110, 16, 18)}


def lencana_sprites():
    for name, pal in FRAME_PALETTES.items():
        p = dict(CORD)
        p.update({k: v + (255,) for k, v in pal.items()})
        p = {k: (v if len(v) == 4 else v + (255,)) for k, v in p.items()}
        SPRITES["lencana_bingkai_" + name] = sprite(LENCANA_FRAME, p)
    SPRITES["lencana_giok"] = sprite(LENCANA_GIOK, {"h": (255, 255, 255, 255), "H": (244, 244, 244, 255), "m": (214, 214, 214, 255),
                                                     "s": (168, 168, 168, 255)})
    SPRITES["lencana_lambang"] = sprite(LENCANA_LAMBANG, {"g": (255, 236, 168, 255), "G": (230, 196, 96, 255)})


def tanda_calon():
    # a small plain pale jade disc with a hole, on a hemp cord: the candidate token
    pal = {"o": (52, 80, 66, 255), "j": (156, 206, 176, 255), "J": (196, 232, 206, 255), "s": (110, 160, 128, 255),
           "c": (150, 126, 86, 255), "C": (196, 170, 120, 255)}
    rows = [
        "................",
        "......cCCc......",
        ".....c....c.....",
        "......cCCc......",
        ".......cc.......",
        ".....oooooo.....",
        "....oJJjjjso....",
        "...oJJjjjjjso...",
        "...oJjj..jjso...",
        "...oJjj..jjso...",
        "...ojjjjjjsso...",
        "....ojjjjsso....",
        ".....oooooo.....",
        "................",
        "................",
        "................",
    ]
    SPRITES["tanda_calon"] = sprite(rows, pal)


def token_naik():
    # Han Li's Ascension Token: a golden plate shaped like a cloud, the character carved dark, light glinting at the rim
    pal = {"o": (92, 54, 8, 255), "G": (230, 178, 52, 255), "g": (255, 226, 120, 255), "d": (168, 116, 24, 255),
           "k": (110, 66, 10, 255), "w": (255, 250, 220, 255)}
    rows = [
        "................",
        ".....oo..oo.....",
        "....ogGooGgo....",
        "...ogGGGGGGdo...",
        "..ogGGGkkGGGdo..",
        "..ogGGkGGkGGdo..",
        ".ogGGGGkkGGGGdo.",
        ".ogGGkkkkkkGGdo.",
        ".ogGGGGkkGGGGdo.",
        ".ogGGGkGGkGGGdo.",
        "..ogGkGGGGkGdo..",
        "..owGGGGGGGGdo..",
        "...odGGddGGdo...",
        "....oddoodddo...",
        ".....oo..ooo....",
        "................",
    ]
    SPRITES["token_naik"] = sprite(rows, pal)


def surat_undangan():
    # a sealed envoy's scroll lying diagonally: cream paper rolled on dark rods, a gold silk band, a vermilion wax seal
    pal = {"o": (60, 44, 30, 255), "p": (236, 222, 186, 255), "P": (250, 242, 214, 255), "s": (196, 178, 140, 255),
           "r": (70, 42, 26, 255), "R": (112, 72, 42, 255), "b": (214, 168, 60, 255), "B": (246, 210, 110, 255),
           "v": (182, 34, 26, 255), "V": (226, 70, 48, 255)}
    rows = [
        "................",
        "...........rR...",
        "..........rRRo..",
        ".........oPPro..",
        "........oPpso...",
        ".......oPpso....",
        "......obBbo.....",
        ".....oBvVbo.....",
        "....oPvvVo......",
        "...oPpbbo.......",
        "..oPpso.........",
        ".rRpso..........",
        "rRRso...........",
        "oRro............",
        ".oo.............",
        "................",
    ]
    SPRITES["surat_undangan"] = sprite(rows, pal)


def surat_restu():
    # a teacher's blessing: a red scroll half open, gold-flecked, a square vermilion seal and two ink columns
    pal = {"o": (54, 14, 12, 255), "r": (168, 30, 26, 255), "R": (210, 56, 40, 255), "d": (120, 18, 18, 255),
           "g": (240, 200, 90, 255), "k": (24, 16, 14, 255), "w": (110, 72, 40, 255), "W": (150, 104, 60, 255),
           "s": (250, 120, 70, 255)}
    rows = [
        "................",
        "..wWWWWWWWWWWw..",
        "..oooooooooooo..",
        "...oRRRRRRRRo...",
        "...oRkRRkRgRo...",
        "...oRkRRkRRRo...",
        "...oRkRgkRRRo...",
        "...oRRRRkRRRo...",
        "...oRkRRRRssso..",
        "...oRkRRRRsrso..",
        "...oRRRgRRssso..",
        "...odddddddddo..",
        "..oooooooooooo..",
        "..wWWWWWWWWWWw..",
        "................",
        "................",
    ]
    SPRITES["surat_restu"] = sprite(rows, pal)


def batu_pikul():
    # the carrying stone of "Pikul Gunung": a rough grey boulder bound in a cross of thick hemp rope with a carry loop
    pal = {"o": (40, 40, 44, 255), "S": (140, 138, 132, 255), "s": (104, 102, 98, 255), "l": (178, 176, 168, 255),
           "c": (150, 120, 70, 255), "C": (196, 166, 104, 255), "k": (96, 72, 40, 255)}
    rows = [
        "................",
        "......cCCc......",
        ".....c....c.....",
        ".....k....k.....",
        "......kCCk......",
        "....oooCCooo....",
        "...olSSCCSSso...",
        "..olSSSCCSSsso..",
        "..oCCCCCCCCCCo..",
        "..oCCCCkkCCCCo..",
        "..oSSSSCCSSsso..",
        "..olSSSCCSssso..",
        "...osSSCCssso...",
        "....oosCCsoo....",
        "......oooo......",
        "................",
    ]
    SPRITES["batu_pikul"] = sprite(rows, pal)


ITEMS = ["lencana_anggota", "tanda_calon", "token_naik", "surat_undangan", "surat_restu", "batu_pikul"]
# rank group thresholds on the Pangkat ordinal (kultivator:pangkat_anggota): 0-2 wood, 3-4 silver, 5+ (and Tamu 9) gold
LENCANA_AMBANG = [("kayu", None), ("perak", 3.0), ("emas", 5.0)]


def item_assets():
    for name in ("tanda_calon", "token_naik", "surat_undangan", "surat_restu", "batu_pikul"):
        save(SPRITES[name], os.path.join(ITEM_TEX, name + ".png"))
        write_json(os.path.join(ITEM_MODEL, name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/gabung/" + name}})
        write_json(os.path.join(ITEM_DEF, name + ".json"), {"model": {"type": "minecraft:model", "model": "kultivator:item/gabung/" + name}})
    for name in ("lencana_giok", "lencana_lambang") + tuple("lencana_bingkai_" + k for k in FRAME_PALETTES):
        save(SPRITES[name], os.path.join(ITEM_TEX, name + ".png"))
    models = {}
    for group, _ in LENCANA_AMBANG:
        mid = "lencana_anggota_" + group
        write_json(os.path.join(ITEM_MODEL, mid + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "kultivator:item/gabung/lencana_bingkai_" + group, "layer1": "kultivator:item/gabung/lencana_giok",
                         "layer2": "kultivator:item/gabung/lencana_lambang"}})
        models[group] = {"type": "minecraft:model", "model": "kultivator:item/gabung/" + mid,
                         "tints": [{"type": "minecraft:constant", "value": -1},
                                   {"type": "kultivator:warna_anggota", "default": 0x5FA88C},
                                   {"type": "minecraft:constant", "value": -1}]}
    write_json(os.path.join(ITEM_DEF, "lencana_anggota.json"), {"model": {
        "type": "minecraft:range_dispatch", "property": "kultivator:pangkat_anggota",
        "entries": [{"threshold": th, "model": models[g]} for g, th in LENCANA_AMBANG if th is not None],
        "fallback": models["kayu"]}})


# ================================================================================================ props 150-156

def mat(name, fn, seed):
    tex(name, fn, seed, store=MAT)


def taotie_bronze(seed):
    """Bronze with a band of taotie scrolls and green patina in the recesses."""
    b = rgb("8C6A3A")

    def p(x, y, rng, n):
        c = shade(b, 0.82 + 0.3 * n.at(x, y, 8))
        band = 10 <= y % 32 <= 21
        if band:
            sx, sy = x % 8, (y - 10) % 12
            spiral = (sx in (1, 6) and 2 <= sy <= 9) or (sy in (2, 9) and 1 <= sx <= 6) or (sx in (3, 4) and sy in (5, 6))
            c = shade(c, 1.18) if spiral else shade(c, 0.66)
            if not spiral and n.at(x + 5, y, 4) > 0.55:
                c = mix(c, rgb("4E8A6E"), 0.55)
        elif y % 32 in (9, 22):
            c = shade(c, 1.25)
        elif n.at(x, y + 9, 4) > 0.72:
            c = mix(c, rgb("5A9A7A"), 0.45)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def water_tex(seed):
    def p(x, y, rng, n):
        c = mix(rgb("2E5A7A"), rgb("6EA8C8"), n.at(x, y, 8))
        if (x + 2 * y) % 11 == 0 and n.at(x, y, 4) > 0.5:
            c = rgb("CDE8F4")
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def rules_paper(seed):
    """The sect rules: cream paper, eight ink columns of characters, a red title column and a square seal."""
    def p(x, y, rng, n):
        c = shade(rgb("EADCB4"), 0.9 + 0.12 * n.at(x, y, 8))
        col = x % 4
        if col == 1 and 2 <= y <= 29 and (y * 7 + x) % 5 != 0:
            c = rgb("2A2018") if x > 4 else rgb("A8231C")
        if 22 <= x <= 28 and 22 <= y <= 28 and (x in (22, 28) or y in (22, 28) or (x + y) % 3 == 0):
            c = rgb("B4231C")
        return c
    return p


def kasaya_cloth(seed):
    """The succession robe folded: patched saffron-red kasaya squares with gold seams."""
    def p(x, y, rng, n):
        cell = ((x // 8) + (y // 8)) % 2
        c = rgb("A8341E") if cell else rgb("C24A24")
        if x % 8 == 0 or y % 8 == 0:
            c = rgb("D8A84A")
        return shade(c, 0.9 + 0.12 * n.at(x, y, 4) + rng.random() * 0.04)
    return p


def peach_wood(seed):
    """Peach wood of a practice sword: warm pink-brown with fine grain and a polished sheen."""
    def p(x, y, rng, n):
        g = 0.5 + 0.5 * math.sin(x * 0.9 + math.sin(y * 0.15) * 2.0)
        c = mix(rgb("9A5A3A"), rgb("C88A62"), g * 0.7 + 0.3 * n.at(x, y, 8))
        if x % 16 == 7:
            c = shade(c, 1.15)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def woven_bamboo(seed):
    def p(x, y, rng, n):
        over = ((x // 4) + (y // 4)) % 2
        c = rgb("C8A860") if over else rgb("A88A46")
        if (over and y % 4 == 0) or (not over and x % 4 == 0):
            c = shade(c, 0.78)
        return shade(c, 0.93 + rng.random() * 0.1)
    return p


def carved_jade(seed):
    """Jade with a carved peak-and-sword emblem in relief (the badge face)."""
    base = jade(seed)

    def p(x, y, rng, n):
        c = base(x, y, rng, n)
        lx, ly = x % 16, y % 16
        mark = (ly == 3 and lx == 8) or (ly == 4 and 7 <= lx <= 9) or (ly == 5 and lx in (6, 8, 10)) or \
               (lx == 8 and 5 <= ly <= 12) or (ly == 9 and 6 <= lx <= 10)
        if mark:
            c = rgb("F2DC8A")
        return c
    return p


def build_materials():
    mat("dupa", incense(301), 301)
    mat("bara", ember(302), 302)
    mat("kertas_merah", plain("B8281E", 0.25), 303)
    mat("abu", plain("8E8A84", 0.3), 304)
    mat("giok_ukir", carved_jade(305), 305)
    mat("emas", gold(306), 306)
    mat("tali_merah", plain("B4231C", 0.2), 307)
    mat("kayu_ember", wood("8A5E36", 308), 308)
    mat("besi_hitam", plain("3A3A40", 0.25), 309)
    mat("air", water_tex(310), 310)
    mat("bambu", bamboo(311), 311)
    mat("tali", rope(312), 312)
    mat("lak_hitam", lacquer("1E1A1E", 314), 314)
    mat("kasaya", kasaya_cloth(315), 315)
    mat("anyaman_bambu", woven_bamboo(316), 316)
    mat("kain_merah", cloth("9A2020", 317), 317)
    mat("kertas_aturan", rules_paper(318), 318)
    mat("kayu_tua", wood("4A3020", 319), 319)
    mat("kayu_persik", peach_wood(320), 320)
    mat("kain_biru", cloth("2E4A7A", 321), 321)


def P(name):
    return Model(name, FOLDER)


def ring_cross(m, cx, cy, cz, w, h, d, skin, glow_lvl=0):
    m.c(cx, cy, cz, w, h, d * 0.72, skin, glow=glow_lvl)
    m.c(cx, cy, cz, w * 0.72, h, d, skin, glow=glow_lvl)
    m.c(cx, cy, cz, w * 0.62, h, d * 0.62, skin, rot=("y", 45), glow=glow_lvl)


def build_props():
    # 150 dupa_sumpah: three oath incense sticks held as one at the grip: red paper band, fanned sticks, glowing tips,
    # a curl of ash on the middle one
    m = P("dupa_sumpah")
    for x, rz in ((-0.7, 22.5), (0.0, None), (0.7, -22.5)):
        m.c(x, 4.6, 0, 0.32, 10.0, 0.32, "dupa", rot=("z", rz, (x, 0, 0)) if rz else None)
        m.c(x, -1.4, 0, 0.36, 2.4, 0.36, "kayu_tua", rot=("z", rz, (x, 0, 0)) if rz else None)
    m.c(-2.6, 9.4, 0, 0.42, 0.42, 0.42, "bara", glow=14)
    m.c(0.0, 9.9, 0, 0.42, 0.6, 0.42, "abu")
    m.c(0.0, 10.4, 0, 0.42, 0.42, 0.42, "bara", glow=14)
    m.c(2.6, 9.4, 0, 0.42, 0.42, 0.42, "bara", glow=14)
    m.c(0, 0.6, 0, 2.2, 1.6, 0.7, "kertas_merah")
    m.c(0, 0.6, -0.38, 0.8, 1.0, 0.06, "emas")

    # 151 lencana_anggota: the member badge offered on both palms: a square jade plaque with notched corners carved with
    # the gate emblem, a gold frame, the red cord knot above and the tassel below
    m = P("lencana_anggota")
    m.c(0, 3.0, 0, 4.4, 5.0, 0.7, "giok_ukir", full=True)
    m.c(0, 3.0, 0, 5.2, 4.2, 0.7, "giok_ukir", full=True)
    m.c(0, 5.65, 0, 4.6, 0.4, 0.9, "emas")
    m.c(0, 0.35, 0, 4.6, 0.4, 0.9, "emas")
    m.c(-2.65, 3.0, 0, 0.4, 4.4, 0.9, "emas")
    m.c(2.65, 3.0, 0, 0.4, 4.4, 0.9, "emas")
    m.c(0, 6.4, 0, 0.9, 0.9, 0.6, "tali_merah")
    m.c(-0.6, 7.3, 0, 0.3, 1.2, 0.3, "tali_merah", rot=("z", 22.5))
    m.c(0.6, 7.3, 0, 0.3, 1.2, 0.3, "tali_merah", rot=("z", -22.5))
    m.c(0, -0.4, 0, 0.8, 0.7, 0.6, "tali_merah")
    m.c(0, -1.6, 0, 0.9, 1.8, 0.9, "kain_merah")
    m.c(0, -2.7, 0, 1.1, 0.5, 1.1, "tali_merah")

    # 152 ember_pikul: the water-carrying pole across the shoulder: a bamboo pole, rope bridles, two banded wooden buckets
    # filled with water (one sloshing a little higher)
    m = P("ember_pikul")
    m.c(0, 0, 0, 23.0, 0.9, 0.9, "bambu")
    for x in (-10.4, 10.4):
        m.c(x, 0, 0, 1.2, 1.1, 1.2, "tali")
        m.c(x - 1.3, -2.6, 0, 0.25, 5.0, 0.25, "tali", rot=("z", -22.5))
        m.c(x + 1.3, -2.6, 0, 0.25, 5.0, 0.25, "tali", rot=("z", 22.5))
        m.ring(-7.6, 4.6, 4.4, 4.6, 0.5, "kayu_ember", cx=x)
        m.c(x, -9.95, 0, 4.0, 0.3, 4.0, "kayu_ember")
        m.c(x, -6.2, 0, 4.9, 0.45, 4.9, "besi_hitam")
        m.c(x, -8.9, 0, 4.9, 0.45, 4.9, "besi_hitam")
        m.c(x, -5.75 if x < 0 else -6.0, 0, 3.6, 0.2, 3.6, "air")
        m.c(x, -5.0, 0, 5.0, 0.3, 0.5, "kayu_ember")

    # 153 mangkuk_ketua: robe and bowl of the succession: the folded kasaya robe as a cushion, a black lacquer alms bowl
    # with a gold rim and lotus foot on top
    m = P("mangkuk_ketua")
    m.c(0, 0.0, 0, 8.0, 1.0, 6.0, "kasaya")
    m.c(0, 1.0, 0.2, 7.4, 1.0, 5.6, "kasaya")
    m.c(0, 1.8, 0.4, 6.6, 0.6, 5.0, "kasaya")
    m.c(-3.9, 0.9, 0.2, 0.3, 2.4, 5.4, "emas")
    m.c(0, 2.4, 0, 2.4, 0.6, 2.4, "emas")
    ring_cross(m, 0, 3.6, 0, 4.6, 1.8, 4.6, "lak_hitam")
    ring_cross(m, 0, 4.7, 0, 5.2, 0.4, 5.2, "emas")
    m.c(0, 4.55, 0, 3.6, 0.2, 3.6, "lak_hitam")

    # 154 kipas_bambu: the furnace fan: a woven bamboo blade, rim splints, a split-bamboo handle bound with red cord
    m = P("kipas_bambu")
    m.c(0, 2.2, 0, 0.7, 4.8, 0.7, "bambu")
    m.c(0, 0.0, 0, 0.9, 0.5, 0.9, "tali_merah")
    m.c(0, 4.6, 0, 1.2, 0.8, 0.8, "tali_merah")
    m.c(0, 8.6, 0, 6.4, 7.2, 0.25, "anyaman_bambu", full=True)
    m.c(0, 8.6, 0, 7.2, 5.6, 0.22, "anyaman_bambu", full=True)
    m.c(0, 12.3, 0, 6.6, 0.4, 0.4, "bambu")
    m.c(-3.5, 8.6, 0, 0.4, 5.8, 0.4, "bambu")
    m.c(3.5, 8.6, 0, 0.4, 5.8, 0.4, "bambu")
    m.c(0, 8.4, -0.15, 0.3, 7.2, 0.2, "bambu")
    m.c(0, 4.9, 0, 6.6, 0.4, 0.4, "bambu")

    # 155 gulungan_aturan: the rules scroll of the Tetua Hukum, opened downward: the top roller in the hand with jade end
    # knobs, the paper with the ink columns and red seal, the hanging bottom roller and a gold silk tie
    m = P("gulungan_aturan")
    m.c(0, 0, 0, 7.4, 0.9, 0.9, "kayu_tua")
    m.c(-4.0, 0, 0, 0.8, 1.2, 1.2, "giok_ukir")
    m.c(4.0, 0, 0, 0.8, 1.2, 1.2, "giok_ukir")
    m.c(0, -4.8, -0.1, 6.6, 9.0, 0.12, "kertas_aturan", full=True)
    m.c(0, -9.6, 0, 7.4, 0.9, 0.9, "kayu_tua")
    m.c(-4.0, -9.6, 0, 0.8, 1.2, 1.2, "giok_ukir")
    m.c(4.0, -9.6, 0, 0.8, 1.2, 1.2, "giok_ukir")
    m.c(0, 0.9, 0, 6.6, 0.25, 0.25, "kain_biru")
    m.c(2.2, -1.2, -0.3, 0.3, 2.6, 0.12, "emas")

    # 156 pedang_kayu: the peach-wood practice sword: a ridged blade with a rounded point, a disc guard, a cord-wrapped
    # grip, a ring pommel and a red tassel
    m = P("pedang_kayu")
    m.c(0, 2.0, 0, 1.0, 4.0, 1.0, "tali")
    m.c(0, 0.0, 0, 1.4, 0.5, 1.4, "kayu_tua")
    m.c(0, -0.7, 0, 1.0, 0.8, 0.5, "kayu_tua")
    m.c(0, 4.4, 0, 3.0, 0.7, 3.0, "kayu_tua")
    m.c(0, 4.4, 0, 2.2, 0.75, 2.2, "kayu_tua", rot=("y", 45))
    m.c(0, 11.4, 0, 1.6, 13.0, 0.6, "kayu_persik")
    m.c(0, 11.2, -0.32, 0.4, 12.0, 0.12, "kayu_tua")
    m.c(0, 11.2, 0.32, 0.4, 12.0, 0.12, "kayu_tua")
    m.c(0, 18.2, 0, 1.2, 0.8, 0.6, "kayu_persik")
    m.c(0, 18.8, 0, 0.6, 0.5, 0.6, "kayu_persik")
    m.c(0, -1.5, 0, 0.3, 0.9, 0.3, "tali_merah")
    m.c(0, -2.6, 0, 0.8, 1.4, 0.8, "kain_merah")


# ================================================================================================ entity band atlases

def band_atlas(name, width, painters):
    """len(painters) bands of 32 px; a painter gets (x, y, rng, noise) with x in 0..width-1 if it is marked wide."""
    img = Image(width, 32 * len(painters))
    for i, pnt in enumerate(painters):
        if pnt is None:
            continue
        rng = random.Random(stable(name, i))
        n = Noise(1300 + i * 17 + len(name), 32)
        for y in range(32):
            for x in range(width):
                c = pnt(x if getattr(pnt, "wide", False) else x % 32, y, rng, n)
                if c is None:
                    continue
                a = c[3] if len(c) > 3 else 255
                img.set(x, i * 32 + y, (c[0], c[1], c[2], a))
    ENTITY[name] = img
    return img


def wide(fn):
    fn.wide = True
    return fn


def straw_strands(seed):
    """Bundled straw: long golden strands along y, darker gaps, a few broken ends."""
    ph = random.Random(seed)
    phase = [ph.random() * 6.28 for _ in range(32)]

    def p(x, y, rng, n):
        g = 0.5 + 0.5 * math.sin(phase[x] + y * 0.35)
        c = mix(rgb("A27C38"), rgb("E2BE72"), g)
        if x % 3 == 0:
            c = shade(c, 0.78)
        if rng.random() < 0.03:
            c = rgb("F4DC9C")
        return shade(c, 0.94 + 0.12 * n.at(x, y, 8))
    return p


def sack_face():
    """Burlap with a coarse weave; on the head's front face (6..12, 6..12) a face daubed in ink: slanted brows, two
    X eyes and a grim mouth, and a red talisman dot on the brow."""
    def p(x, y, rng, n):
        weave = (x + y) % 2
        c = rgb("A08A62") if weave else rgb("8A764E")
        if x % 4 == 0 or y % 4 == 0:
            c = shade(c, 0.86)
        c = shade(c, 0.92 + 0.14 * n.at(x, y, 4))
        fx, fy = x - 6, y - 6
        if 0 <= fx < 6 and 0 <= fy < 6:
            ink = (26, 20, 18)
            if fy == 1 and fx in (0, 1, 4, 5):
                c = ink
            if fy in (2, 3) and fx in (1, 4):
                c = (150, 24, 22)  # the eyes, lit by the glow sheet
            if fy == 5 and 1 <= fx <= 4:
                c = ink
            if fy == 0 and fx in (2, 3):
                c = (196, 40, 30)
        return c
    return wide(p)


def talisman_paper():
    """Yellow talisman paper; on the chest plaque front (1..5, 1..7) a red command glyph with a seal at its foot."""
    def p(x, y, rng, n):
        c = shade(rgb("E8C860"), 0.9 + 0.12 * n.at(x, y, 4))
        fx, fy = x - 1, y - 1
        if 0 <= fx < 4 and 0 <= fy < 6:
            glyph = [".rr.", "rrrr", ".r..", "rrr.", ".r.r", "rrrr"]
            if glyph[fy][fx] == "r":
                c = (176, 28, 22)
        elif y in (0, 7) or x in (0, 5):
            c = (196, 60, 40)  # the red border line around the plaque face
        return c
    return wide(p)


def grey_cloth(seed, lo=176, hi=236):
    def p(x, y, rng, n):
        v = lo + (hi - lo) * (0.55 + 0.45 * n.at(x, y, 4))
        if (x + y) % 2 == 0:
            v *= 0.95
        if y % 8 == 0:
            v *= 0.86
        v = clamp(v)
        return (v, v, v)
    return p


def transparent():
    def p(x, y, rng, n):
        return (0, 0, 0, 0)
    return p


def glow_ring():
    def p(x, y, rng, n):
        v = 0.5 + 0.5 * math.sin(x * 0.6)
        return mix((255, 220, 120), (255, 255, 230), v) + (255,)
    return p


def iron(seed):
    def p(x, y, rng, n):
        c = shade(rgb("4A4A50"), 0.8 + 0.4 * n.at(x, y, 4))
        if (x * 5 + y * 3) % 13 == 0:
            c = rgb("7A4A2A")
        return c
    return p


def boneka_textures():
    # 0 old post wood, 1 straw, 2 rope, 3 sack head (face), 4 talisman, 5 greyscale cloth band, 6 glow ring (alpha 0 in the
    # base sheet), 7 iron nails and straps
    band_atlas("boneka_ujian", 64, [wood("4A3020", 401), straw_strands(402), rope(403), sack_face(), talisman_paper(),
                                    grey_cloth(404), transparent(), iron(405)])
    base = ENTITY["boneka_ujian"]
    glow = Image(base.width, base.height)
    for y in range(base.height):
        band = y // 32
        for x in range(base.width):
            r, g, b, a = base.get(x, y)
            if band == 4 and r > 150 and g < 90:
                glow.set(x, y, (255, 214, 120, 255))       # the talisman glyph burns gold
            elif band == 3 and r > 130 and g < 50:
                glow.set(x, y, (255, 120, 60, 255))        # the eyes light red-orange
            elif band == 6:
                v = 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.2)
                glow.set(x, y, (255, int(200 + 50 * v), int(110 + 90 * v), int(170 + 85 * (1 - abs(y % 32 - 16) / 16.0))))
    ENTITY["boneka_ujian_pancar"] = glow


GLYPH_UNSUR = {
    # 6 x 8 seal-script forms of the five phases (metal, wood, water, fire, earth)
    0: ["..##..", ".#..#.", "######", "..##..", "######", ".#..#.", "#.##.#", "######"],
    1: ["..#...", "######", "..#...", ".###..", "#.#.#.", "..#...", "..#...", "..#..."],
    2: ["..#...", "..#.#.", "###.#.", "..##..", ".#.#..", "#..#.#", "...#..", "..##.."],
    3: ["..#...", "#.#..#", "#.#.#.", "..#...", ".#.#..", ".#.#..", "#...#.", "#....#"],
    4: ["..#...", "..#...", "######", "..#...", "..#...", "..#...", "..#...", "######"],
}
WARNA_UNSUR = ["E8E8F0", "5FD068", "4FA8F0", "F06A3A", "D8B060"]


def fluted_stone(base):
    b = rgb(base)

    def p(x, y, rng, n):
        c = shade(b, 0.78 + 0.34 * n.at(x, y, 8))
        f = x % 5
        if f == 0:
            c = shade(c, 0.7)
        elif f == 2:
            c = shade(c, 1.1)
        if n.at(x + 11, y + 3, 2) > 0.86:
            c = shade(c, 0.7)
        return shade(c, 0.96 + rng.random() * 0.06)
    return p


def cloud_relief(base):
    b = rgb(base)

    def p(x, y, rng, n):
        c = shade(b, 0.85 + 0.25 * n.at(x, y, 8))
        cx, cy = x % 10, y % 10
        r = math.hypot(cx - 4.5, cy - 4.5)
        if 2.4 < r < 3.6:
            c = shade(c, 1.2)
        elif r <= 2.4 and (cx + cy) % 3 == 0:
            c = shade(c, 0.72)
        return c
    return p


def plaque(unsur):
    glyph = GLYPH_UNSUR[unsur]
    ink = shade(rgb(WARNA_UNSUR[unsur]), 0.8)

    def p(x, y, rng, n):
        c = shade(rgb("4A4640"), 0.85 + 0.3 * n.at(x, y, 4))
        fx, fy = x - 1, y - 1
        if 0 <= fx < 6 and 0 <= fy < 8 and glyph[fy][fx] == "#":
            c = ink
        elif x in (0, 7) or y in (0, 9):
            c = shade(rgb("8C6A3A"), 0.9 + 0.2 * n.at(x, y, 4))
        return c
    return wide(p)


def crystal_unsur(unsur):
    base = rgb(WARNA_UNSUR[unsur])

    def p(x, y, rng, n):
        r = math.hypot(x - 15.5, y - 15.5) / 22.0
        c = mix((255, 255, 250), shade(base, 0.7), r + 0.25 * n.at(x, y, 8))
        if (x + y) % 9 == 0 or (x - y) % 11 == 0:
            c = mix(c, (255, 255, 255), 0.5)
        return c
    return p


def rune_band():
    def p(x, y, rng, n):
        cx = x % 8
        mark = (y % 32 in (14, 17)) or (14 <= y % 32 <= 17 and cx in (2, 5)) or (y % 32 in (15, 16) and cx in (3, 4))
        return (255, 255, 255, 255) if mark else (255, 255, 255, 70)
    return p


def pilar_textures():
    for u in range(5):
        band_atlas("pilar_qi_%d" % u, 64, [stone(410 + u, "B4AEA2"), fluted_stone("A8A296"), cloud_relief("9C968A"), plaque(u),
                                           bronze(420 + u), crystal_unsur(u), transparent()])
    # one white glow sheet for all five (the renderer tints it with the element colour)
    base = ENTITY["pilar_qi_0"]
    glow = Image(base.width, base.height)
    for y in range(base.height):
        band = y // 32
        for x in range(base.width):
            if band == 5:
                r = math.hypot(x % 32 - 15.5, y % 32 - 15.5) / 22.0
                glow.set(x, y, (255, 255, 255, int(clamp(250 - 180 * r))))
            elif band == 6:
                glow.set(x, y, rune_band()(x, y % 32, None, None))
    ENTITY["pilar_qi_pancar"] = glow
    # per element: the plaque strokes of that element glow
    for u in range(5):
        g = Image(base.width, base.height)
        for y in range(base.height):
            for x in range(base.width):
                g.set(x, y, glow.get(x, y))
        for fy in range(8):
            for fx in range(6):
                if GLYPH_UNSUR[u][fy][fx] == "#":
                    g.set(1 + fx, 96 + 1 + fy, (255, 255, 255, 255))
        ENTITY["pilar_qi_%d_pancar" % u] = g
    del ENTITY["pilar_qi_pancar"]


def liquid_white():
    def p(x, y, rng, n):
        v = 200 + 50 * n.at(x, y, 8)
        if (x * 3 + y) % 13 == 0:
            v = 255
        return (v, v, v, 255)
    return p


def steam_wisp():
    """Soft white wisps rising: alpha strongest in the middle columns, fading to the top and the edges."""
    def p(x, y, rng, n):
        cx = x % 16
        sway = math.sin(y * 0.4 + (x // 16) * 2.0) * 2.0
        d = abs(cx - 8 - sway) / 6.0
        fade = min(1.0, (y % 32) / 7.0)  # the wisp plane samples rows 0-7: clear at the top, dense at the rim
        a = clamp(int(230 * max(0.0, 1.0 - d) * (0.25 + 0.75 * fade) * (0.7 + 0.5 * n.at(x, y, 4))))
        return (255, 255, 255, a)
    return p


def lacquer_tray():
    def p(x, y, rng, n):
        c = mix(rgb("2A1214"), rgb("5A1E1C"), n.at(x, y, 8))
        if x % 16 in (1, 14) or y % 16 in (1, 14):
            c = rgb("C9A24A")
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def cawan_textures():
    # 0 bronze with taotie band, 1 dark patina bronze, 2 liquid (white, tinted), 3 steam wisps (alpha, tinted),
    # 4 black-red lacquer tray with gold lines
    band_atlas("cawan_ujian", 64, [taotie_bronze(430), plain("4A3A26", 0.35), liquid_white(), steam_wisp(), lacquer_tray()])


TRIGRAM = [[1, 1, 1], [1, 1, 0], [1, 0, 1], [1, 0, 0], [0, 1, 1], [0, 1, 0], [0, 0, 1], [0, 0, 0]]  # lines bottom -> top


def dinding_texture():
    """128 x 96: mist (0, 0)-(64, 64); formation eye (64, 0)-(96, 32); red cross (96, 0)-(128, 32); the digits 1-3 at
    (64, 32), (80, 32), (96, 32), 16 px each; the eight trigrams (Qian Dui Li Zhen Xun Kan Gen Kun) 16 x 16 in row y 64."""
    img = Image(128, 96)
    n = Noise(441, 64)
    for y in range(64):
        for x in range(64):
            v = 0.5 * n.at(x, y, 16) + 0.3 * n.at(x * 2 % 64, y * 2 % 64, 8) + 0.2 * n.at(x * 4 % 64, y * 4 % 64, 4)
            a = clamp(int(40 + 190 * v))
            c = mix((150, 160, 176), (236, 240, 248), v)
            img.set(x, y, (c[0], c[1], c[2], a))
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 15.5)
            a = 0
            if 12.5 < r < 15.0 or 7.5 < r < 8.8:
                a = 255
            ang = math.atan2(y - 15.5, x - 15.5)
            if 8.8 <= r <= 12.5 and int((ang + math.pi) / (math.pi / 4)) % 2 == 0 and abs(math.sin(ang * 4)) < 0.25:
                a = 230
            if r < 3.0:
                a = 255
            if a:
                img.set(64 + x, y, (255, 236, 170, a))
            d = abs(x - y)
            e = abs(x + y - 31)
            if (d < 2.2 or e < 2.2) and 4 <= x <= 27:
                img.set(96 + x, y, (230, 40, 30, 255))
    digits = {1: ["..##..", ".###..", "..##..", "..##..", "..##..", ".####."],
              2: [".####.", "##..##", "...##.", "..##..", ".##...", "######"],
              3: ["#####.", "....##", ".####.", "....##", "##..##", ".####."]}
    for k, rows in digits.items():
        ox = 64 + (k - 1) * 16
        for fy, row in enumerate(rows):
            for fx, ch in enumerate(row):
                if ch == "#":
                    for sy in range(2):
                        img.set(ox + 2 + fx * 2, 32 + 2 + fy * 2 + sy, (255, 236, 170, 255))
                        img.set(ox + 3 + fx * 2, 32 + 2 + fy * 2 + sy, (255, 236, 170, 255))
    for t, lines in enumerate(TRIGRAM):
        ox, oy = t * 16, 64
        for i, yang in enumerate(lines):
            ly = oy + 12 - i * 4
            for x in range(2, 14):
                if not yang and 6 <= x <= 9:
                    continue
                for yy in (ly, ly + 1):
                    img.set(ox + x, yy, (255, 226, 140, 255))
                img.set(ox + x, ly + 2, (110, 70, 20, 200))
    ENTITY["dinding_ilusi"] = img


# ================================================================================================ the member robe

KUBUS_RE = re.compile(r'^\s*([kh])\("(\w+)", (\w+), (.+?)\),?\s*$')


def robe_cubes():
    """The k(...) (cloth) and h(...) (ornament) cubes of JubahSekteLayer.java."""
    kain, hiasan = [], []
    for line in open(JUBAH_JAVA, encoding="utf-8"):
        m = KUBUS_RE.match(line)
        if not m:
            continue
        vals = [v.strip() for v in m.group(4).split(",")]
        nums = [float(v.rstrip("F")) for v in vals[:11]]
        cube = {"id": m.group(2), "induk": m.group(3), "w": nums[6], "h": nums[7], "d": nums[8], "u": int(nums[9]), "v": int(nums[10]),
                "cermin": vals[11] == "true", "lengan": int(vals[12])}
        if m.group(1) == "h":
            cube["corak"] = int(vals[13])
        (kain if m.group(1) == "k" else hiasan).append(cube)
    if len(kain) < 10 or len(hiasan) < 20:
        raise SystemExit("JubahSekteLayer.java: cube tables not found (%d cloth, %d ornament)" % (len(kain), len(hiasan)))
    return kain, hiasan


def faces(c):
    """Face rectangles (x0, y0, w, h) of a box's UV footprint, by side (vanilla ModelPart layout)."""
    u, v = c["u"], c["v"]
    w, h, d = int(math.ceil(c["w"] - 1e-4)), int(math.ceil(c["h"] - 1e-4)), int(math.ceil(c["d"] - 1e-4))
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}


def check_overlap(cubes, size=64):
    owner = {}
    problems = []
    for c in cubes:
        for side, (x0, y0, w, h) in faces(c).items():
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if not (0 <= x < size and 0 <= y < size):
                        problems.append("%s %s outside the atlas at (%d, %d)" % (c["id"], side, x, y))
                        continue
                    o = owner.get((x, y))
                    key = (c["u"], c["v"], c["w"], c["h"], c["d"])
                    if o is not None and o[0] != key:
                        problems.append("%s overlaps %s at (%d, %d)" % (c["id"], o[1], x, y))
                    owner[(x, y)] = (key, c["id"])
    return sorted(set(problems))[:12]


def robe_textures():
    kain, hiasan = robe_cubes()
    problems = []
    for cubes in (kain, hiasan):
        for cut in (1, 2):  # the wide and the slim arm cuts are separate meshes and may share a footprint
            problems += check_overlap([c for c in cubes if c["lengan"] in (0, cut)])
    cloth_img = Image(64, 64)
    trim_img = Image(64, 64)
    n = Noise(450, 64)
    rng = random.Random(451)
    for c in kain:
        quilt = c["id"] in ("bahu", "kerah")
        belt = c["id"] == "sabuk"
        hang = c["id"].startswith(("punggung", "depan", "ekor", "manset"))
        for side, (x0, y0, w, h) in faces(c).items():
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    lx, ly = x - x0, y - y0
                    v = 206 + 30 * n.at(x, y, 8)
                    if (x + y) % 2 == 0:
                        v -= 6
                    if hang and side in ("north", "south"):
                        # long folds down the panels
                        v *= 0.9 + 0.1 * math.sin(lx * 1.3 + 0.7)
                        if ly > h - 4:
                            v *= 0.94
                    if quilt and (lx + ly) % 4 == 0:
                        v *= 0.86
                    if belt:
                        v = 150 + 40 * n.at(x, y, 4)
                        if ly in (0, h - 1) and side not in ("up", "down"):
                            v = 120
                    v = clamp(v * (0.97 + rng.random() * 0.05))
                    cloth_img.set(x, y, (v, v, v, 255))
                    # trims: hems along every free edge, a double hem at the bottom of hanging pieces, belt piping
                    edge = lx == 0 or ly == 0 or lx == w - 1 or ly == h - 1
                    bottom = hang and side not in ("up", "down") and ly >= h - 2
                    piping = belt and side not in ("up", "down") and ly == h // 2
                    if edge or bottom or piping:
                        t = 236 if (lx + ly) % 3 else 196  # a stitched look
                        trim_img.set(x, y, (t, t, t, 255))
    orn = Image(64, 64)
    colours = {
        "sarung_pedang": ("1E1A1E", "8C6A3A"), "gagang_pedang": ("BCA070", "6E5634"), "pelindung_pedang": ("8C6A3A", "D8B060"),
        "tasbih_kanan": ("6A3A1E", "9A5A30"), "tasbih_kiri": ("6A3A1E", "9A5A30"), "manik_besar": ("5FA88C", "C8F0DC"),
        "bulu": ("6E6458", "A89C8A"), "lempeng_giok": ("5FA88C", "D8F4E0"), "jimat_kanan": ("E8C860", "B01C16"),
        "jimat_kiri": ("E8C860", "B01C16"), "selendang_belakang": ("E8EEF4", "9FD8FF"), "selendang_kanan": ("E8EEF4", "9FD8FF"),
        "selendang_kiri": ("E8EEF4", "9FD8FF"), "celemek": ("2E2622", "6A3A22"), "labu": ("C8A040", "6E4A20"),
        "pelindung_kanan": ("5C3A20", "C4AA78"), "pelindung_kiri": ("5C3A20", "C4AA78"), "pelindung_kanan_r": ("5C3A20", "C4AA78"),
        "pelindung_kiri_r": ("5C3A20", "C4AA78"), "rantai_1": ("4A4A52", "9A9AA6"), "rantai_2": ("4A4A52", "9A9AA6"),
        "rantai_3": ("4A4A52", "9A9AA6"), "rantai_4": ("4A4A52", "9A9AA6"), "rumbai_darah": ("8A1418", "D83A30"),
        "kantong_kanan": ("4A2A5A", "8A5AA0"), "kantong_kiri": ("4A2A5A", "8A5AA0"), "labu_racun": ("C8A040", "6E4A20"),
        "kerudung": ("2E2A3A", "4E4860"),
    }
    for c in hiasan:
        base, accent = colours.get(c["id"], ("FF00FF", "FF00FF"))
        b, a = rgb(base), rgb(accent)
        cid = c["id"]
        for side, (x0, y0, w, h) in faces(c).items():
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    lx, ly = x - x0, y - y0
                    col = shade(b, 0.85 + 0.3 * n.at(x, y, 4))
                    if cid.startswith("sarung") and ly % 4 == 0:
                        col = a                                   # bronze bands on the scabbard
                    elif cid.startswith("gagang") and (lx + ly) % 2 == 0:
                        col = a                                   # cord wrap
                    elif cid.startswith("tasbih") and ly % 2 == 1:
                        col = shade(a, 0.8)                       # bead gaps
                    elif cid in ("bulu",) and (lx * 3 + ly * 5) % 4 == 0:
                        col = a                                   # fur tips
                    elif cid.startswith("jimat") and side in ("north", "south") and lx == w // 2 and 0 < ly < h - 1:
                        col = a                                   # the red glyph stroke
                    elif cid.startswith("selendang") and (lx + 2 * ly) % 7 == 0:
                        col = a                                   # cloud thread
                    elif cid == "celemek" and n.at(x + 7, y, 2) > 0.7:
                        col = shade(a, 0.7)                       # scorch marks
                    elif cid.startswith("labu") and ly == 1:
                        col = a                                   # the cord at the waist of the gourd
                    elif cid.startswith("pelindung") and (lx in (1, w - 2)) and ly % 2 == 0:
                        col = a                                   # studs
                    elif cid.startswith("rantai") and (lx + ly) % 2 == 0:
                        col = a
                    elif cid == "rumbai_darah" and lx % 2 == 0:
                        col = a
                    elif cid.startswith("kantong") and ly == 0:
                        col = a
                    elif cid == "kerudung" and ly % 3 == 0:
                        col = a                                   # folds
                    elif cid in ("manik_besar", "lempeng_giok") and (lx + ly) % 3 == 0:
                        col = a
                    orn.set(x, y, (col[0], col[1], col[2], 255))
    ENTITY["jubah_sekte"] = cloth_img
    ENTITY["jubah_sekte_tepi"] = trim_img
    ENTITY["jubah_sekte_hiasan"] = orn
    return problems


# ================================================================================================ output

def names_from_java():
    src = open(GERAK_ID, encoding="utf-8").read()
    m = re.search(r"String\[\] PROP_NAMA = \{(.*?)\};", src, re.S)
    return re.findall(r'"([a-z_]+)"', m.group(1))


def big(img, factor=4):
    out = Image(img.width * factor, img.height * factor)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.get(x, y)
            bg = 40 if ((x + y) % 2) else 60
            k = a / 255.0
            c = (r * k + bg * (1 - k), g * k + bg * (1 - k), b * k + bg * (1 - k), 255)
            for yy in range(factor):
                for xx in range(factor):
                    out.set(x * factor + xx, y * factor + yy, c)
    return out


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    errors = 0
    # items
    lencana_sprites()
    tanda_calon()
    token_naik()
    surat_undangan()
    surat_restu()
    batu_pikul()
    item_assets()
    # props
    names = names_from_java()
    build_materials()
    build_props()
    mine = {mid: m for (folder, mid), m in T.MODELS.items() if folder == FOLDER}
    for name in names:
        if name not in mine:
            print("MISSING prop", name)
            errors += 1
    for name in mine:
        if name not in names:
            print("EXTRA prop", name)
            errors += 1
    used = set()
    for name, m in sorted(mine.items()):
        problems = validate(m, MAT)
        if problems:
            errors += 1
            print("PROP %s:" % name)
            for p in problems:
                print("   ", p)
        used.update(m.tex)
        write_json(os.path.join(PROP_MODEL, "sekte_gabung_%s.json" % name), model_json(m, PROP_TEX_FOLDER))
        write_json(os.path.join(PROP_DEF, "sekte_gabung_%s.json" % name),
                   {"model": {"type": "minecraft:model", "model": "kultivator:item/fx/sekte_gabung_%s" % name}})
    for name in sorted(used):
        save(MAT[name], os.path.join(PROP_TEX, name + ".png"))
    unused = sorted(set(MAT) - used)
    if unused:
        print("unused prop materials (not written):", ", ".join(unused))
    # entities and robe
    boneka_textures()
    pilar_textures()
    cawan_textures()
    dinding_texture()
    problems = robe_textures()
    for p in problems:
        print("ROBE:", p)
        errors += 1
    for name, img in sorted(ENTITY.items()):
        save(img, os.path.join(ENT_TEX, name + ".png"))
    print("items %d, props %d (elements max %d), prop textures %d, entity textures %d, errors %d"
          % (len(ITEMS), len(mine), max(len(m.els) for m in mine.values()), len(used), len(ENTITY), errors))
    if preview:
        os.makedirs(preview, exist_ok=True)
        sheet(SPRITES, os.path.join(preview, "gabung_item.png"), factor=8, cols=6)
        sheet({n: MAT[n] for n in used}, os.path.join(preview, "gabung_prop_bahan.png"))
        import gen_mandiri_prop as MP
        MP.MAT = MAT
        MP.iso({n: mine[n] for n in names}, os.path.join(preview, "gabung_prop_model.png"))
        for name, img in ENTITY.items():
            big(img).save(os.path.join(preview, "gabung_ent_%s.png" % name))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
