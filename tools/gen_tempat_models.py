"""Generates the hand props, displays and city weapons of DESIGN 38 (38.O.4, module 38MD), and the entity textures of the
38 creatures and mechanisms (38.O.2). Everything is ours: cuboid item models and procedural textures, nothing is
downloaded.

Writes (assets/kultivator/...):
  textures/item/fx/tempat/*.png      material textures (32 x 32) shared by the props
  models/item/fx/tempat/<id>.json    one cuboid model per prop 1-40 (+ batu_mentah_s/l/xl and the two cut halves)
  items/fx/tempat/<id>.json          item definitions kultivator:fx/tempat/<id> (DataComponents.ITEM_MODEL; PropKatalog 40G)
  textures/item/senjata/*.png        blade/fitting textures of the four grades
  models/item/senjata/<f>_<g>.json   28 city weapons (7 forms x 4 grades), items kultivator:senjata/<f>_<g> (38EK)
  items/senjata/<f>_<g>.json
  textures/entity/tempat/*.png       band textures (32 px bands) of the 38.O.2 entity models (client/dunia/tempat/model)
  tools/fx_model_ids_tempat.txt      this module's prop id list

Prop names and indexes are exactly dev.kultivator.sosok.PropId 1-40 (DESIGN 38.O.4). Rules (validate()):
  * anchor (8, 8, 8) = the grip: where the hand closes (a cup's foot, a broom's handle top, a spear's balance point);
  * +y runs away from the fist along the handle (a broom's bristles, a hammer's head, a spear's blade are at +y), the
    front faces north (-z); carried objects (bowl, teapot, chest, basket) stand upright with their grip at the anchor;
  * element coordinates within -16..32, uv within 0..16, at most 40 elements;
  * an element rotation is one axis by -45, -22.5, 22.5 or 45 degrees;
  * face uv is the face size (2 texels per model pixel on a 32 px texture).
Display: thirdperson/fixed/head are identity (SosokPropLayer and DisplayKey place them), gui is a 3/4 view fitted to the
slot, ground is half size.

Run with: python tools/gen_tempat_models.py [preview_dir]   (validates, writes; 8x texture sheets into preview_dir)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
NS = "kultivator"
SIDES = ("north", "south", "east", "west", "up", "down")

PROP_TEX_DIR = os.path.join(ROOT, "textures", "item", "fx", "tempat")
PROP_MODEL_DIR = os.path.join(ROOT, "models", "item", "fx", "tempat")
PROP_ITEM_DIR = os.path.join(ROOT, "items", "fx", "tempat")
WEAPON_TEX_DIR = os.path.join(ROOT, "textures", "item", "senjata")
WEAPON_MODEL_DIR = os.path.join(ROOT, "models", "item", "senjata")
WEAPON_ITEM_DIR = os.path.join(ROOT, "items", "senjata")
ENTITY_TEX_DIR = os.path.join(ROOT, "textures", "entity", "tempat")
IDS_FILE = os.path.join(HERE, "fx_model_ids_tempat.txt")

# DESIGN 38.O.0 palette (tools/tempat_palet.py names)
PAL = {
    "lak": "8E1F1F", "emas": "C9A24A", "giok": "5FA88C", "kayu_tua": "5A3A22", "kayu_muda": "9C7448", "besi": "7C8288",
    "perunggu": "8C6A3A", "kertas": "E8DDC0", "tinta": "1E1A18", "sutra_biru": "3A5A8C", "kain_kasar": "8A7A60",
    "kristal": "B8A6F0", "darah": "6A1420", "tulang": "D8CFB8",
}


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, f):
    return tuple(max(0.0, min(255.0, v * f)) for v in c[:3])


class Noise:
    def __init__(self, seed, period=32):
        r = random.Random(seed)
        self.period = period
        self.g = [[r.random() for _ in range(period)] for _ in range(period)]

    def at(self, x, y, cell):
        """Tileable value noise over the texture period."""
        n = max(1, self.period // cell)
        fx, fy = (x / cell) % n, (y / cell) % n
        x0, y0 = int(fx), int(fy)
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        tx, ty = fx - x0, fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        g = self.g
        a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
        b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
        return a + (b - a) * ty


TEXTURES = {}


def tex(name, fn, seed=0, size=32, store=None):
    """A texture painted by fn(x, y, rng, noise) -> (r, g, b[, a]); 4 shade levels are built in by the painters."""
    rng = random.Random(seed or stable(name))
    noise = Noise(seed or 7, size)
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            c = fn(x, y, rng, noise)
            a = c[3] if len(c) > 3 else 255
            img.set(x, y, (c[0], c[1], c[2], a))
    (store if store is not None else TEXTURES)[name] = img
    return name


# ================================================================================================ material textures

def wood(base, seed, rings=0.9):
    b = rgb(base)
    ph = random.Random(seed)
    phase = [ph.random() * 6.28 for _ in range(32)]

    def p(x, y, rng, n):
        g = math.sin(y * rings + math.sin(x * 0.2 + phase[y]) * 1.8)
        c = shade(b, 0.82 + 0.22 * (0.5 + 0.5 * g) + 0.1 * n.at(x, y, 8))
        if (x * 7 + y * 13) % 41 == 0:
            c = shade(c, 0.7)  # a knot pore
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def bamboo(seed):
    def p(x, y, rng, n):
        c = mix(rgb("A8B05A"), rgb("D8C878"), 0.5 + 0.5 * math.sin(x * 0.6))
        if y % 16 in (0, 1):
            c = shade(rgb("7C7A3A"), 1.0 if y % 16 == 0 else 1.25)  # the node
        if x % 4 == 0:
            c = shade(c, 0.9)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def lacquer(base, seed):
    b = rgb(base)

    def p(x, y, rng, n):
        gloss = max(0.0, math.sin((x + y * 0.6) * 0.32)) ** 8
        c = mix(shade(b, 0.85 + 0.2 * n.at(x, y, 8)), (255, 236, 220), gloss * 0.35)
        return shade(c, 0.97 + rng.random() * 0.05)
    return p


def metal(base, seed, brushed=True, glow=None):
    b = rgb(base)

    def p(x, y, rng, n):
        c = shade(b, 0.8 + 0.35 * n.at(x, y, 4))
        if brushed and (y + (x // 8)) % 3 == 0:
            c = shade(c, 1.08)
        if rng.random() < 0.02:
            c = shade(c, 1.4)
        if glow and n.at(x * 2, y * 2, 8) > 0.72:
            c = mix(c, rgb(glow), 0.7)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def bronze(seed):
    def p(x, y, rng, n):
        c = mix(rgb("6E4E26"), rgb("C08C48"), n.at(x, y, 4))
        if n.at((x * 3) % 32, (y * 5) % 32, 4) > 0.8:
            c = mix(c, rgb("5A9C80"), 0.55)  # verdigris
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def gold(seed):
    def p(x, y, rng, n):
        c = mix(rgb("A87A24"), rgb("F2CF6A"), n.at(x, y, 4))
        if (x + y) % 11 == 0:
            c = mix(c, (255, 246, 200), 0.6)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def jade(seed):
    def p(x, y, rng, n):
        c = mix(rgb("2E7E5C"), rgb("9AE0B8"), n.at(x, y, 8))
        if n.at(x + 5, y + 9, 4) > 0.8:
            c = mix(c, (236, 252, 240), 0.5)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def paper(seed, lines=False):
    def p(x, y, rng, n):
        v = 226 + rng.random() * 18 + 8 * n.at(x, y, 8)
        c = (v, v * 0.96, v * 0.86)
        if lines and x % 4 == 1 and 3 < y < 29 and (y * 7 + x) % 5 != 0:
            c = (40, 34, 30)  # a brushed column of characters
        return c
    return p


def cloth(base, seed, weave=True):
    b = rgb(base)

    def p(x, y, rng, n):
        c = shade(b, 0.85 + 0.25 * n.at(x, y, 8))
        if weave and (x + y) % 2 == 0:
            c = shade(c, 0.93)
        if y % 16 == 15:
            c = shade(c, 0.8)
        return shade(c, 0.96 + rng.random() * 0.06)
    return p


def porcelain(seed):
    """White porcelain with a cobalt rim line and a scrolling vine band (blue and white ware)."""
    def p(x, y, rng, n):
        c = (238, 240, 244)
        if y in (1, 2):
            c = (44, 70, 150)
        elif 6 <= y <= 13:
            vine = 9.5 + 2.5 * math.sin(x * 0.4)
            if abs(y - vine) < 0.8 or (abs(y - vine) < 2.2 and x % 8 == 3):
                c = (52, 84, 168)
        elif y in (17, 18):
            c = (70, 96, 170)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def rice(seed):
    def p(x, y, rng, n):
        c = (246, 244, 236)
        if (x * 3 + y * 5) % 4 == 0:
            c = (222, 218, 204)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def tea(seed):
    def p(x, y, rng, n):
        c = mix(rgb("6A7A2A"), rgb("A8A048"), n.at(x, y, 8))
        if n.at(x * 2, y * 2, 4) > 0.85:
            c = (220, 214, 160)  # a light ring
        return c
    return p


def straw(seed):
    def p(x, y, rng, n):
        weave = ((x // 2) + (y // 2)) % 2
        c = rgb("D0AA62") if weave else rgb("A8823E")
        if x % 2 == (y // 2) % 2:
            c = shade(c, 0.88)
        return shade(c, 0.93 + rng.random() * 0.1)
    return p


def fibre(seed):
    """Palm fibre bristles (ijuk): dark brown-black strands along the length."""
    ph = random.Random(seed)
    phase = [ph.random() * 6.28 for _ in range(32)]

    def p(x, y, rng, n):
        g = 0.5 + 0.5 * math.sin(phase[x] + y * 0.2)
        c = mix(rgb("1E1612"), rgb("5A4430"), g)
        if x % 2:
            c = shade(c, 0.8)
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def leather(seed):
    def p(x, y, rng, n):
        c = mix(rgb("5C3A20"), rgb("8E6038"), n.at(x, y, 4))
        if y % 16 == 2 and x % 4 < 2:
            c = (196, 170, 120)
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def rope(seed):
    def p(x, y, rng, n):
        d = (x + y) % 4
        c = rgb("BCA070") if d in (1, 2) else (rgb("8C7448") if d == 0 else rgb("6E5634"))
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def stone(seed, base="8A8278", vein=None):
    b = rgb(base)

    def p(x, y, rng, n):
        c = shade(b, 0.7 + 0.5 * n.at(x, y, 4))
        if n.at(x + 3, y + 7, 2) > 0.82:
            c = shade(c, 0.6)  # pits
        if vein:
            v = abs(math.sin(x * 0.35 + y * 0.2 + n.at(x, y, 8) * 4.0))
            if v < 0.09:
                c = rgb(vein)
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def glow(base, seed):
    b = rgb(base)

    def p(x, y, rng, n):
        r = math.hypot(x - 15.5, y - 15.5) / 22.0
        return mix((255, 255, 240), b, r + 0.2 * n.at(x, y, 4))
    return p


def ember(seed):
    def p(x, y, rng, n):
        c = mix(rgb("FF3010"), rgb("FFD060"), n.at(x, y, 4))
        return shade(c, 0.9 + rng.random() * 0.15)
    return p


def incense(seed):
    def p(x, y, rng, n):
        c = mix(rgb("8A3A20"), rgb("B05A30"), n.at(x, y, 4))
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def plaster(seed):
    def p(x, y, rng, n):
        v = 196 + 34 * n.at(x, y, 4)
        return shade((v, v * 0.98, v * 0.94), 0.96 + rng.random() * 0.06)
    return p


def soil(seed):
    def p(x, y, rng, n):
        c = mix(rgb("3E2A18"), rgb("6E4C2C"), n.at(x, y, 4))
        if rng.random() < 0.05:
            c = (150, 130, 100)
        return c
    return p


def leaf(seed):
    def p(x, y, rng, n):
        c = mix(rgb("3E7A2E"), rgb("8ACB4E"), n.at(x, y, 4))
        if x % 8 == 4:
            c = shade(c, 1.2)  # a vein
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def water(seed):
    def p(x, y, rng, n):
        c = mix(rgb("3A6E9A"), rgb("8EC4E0"), n.at(x, y, 8))
        if abs(math.sin(x * 0.5 + y * 0.2)) < 0.08:
            c = (220, 240, 255)
        return c
    return p


def painted_paper(seed):
    """Oiled paper painted with a misty landscape: mountains, a red sun, pines (fans and umbrellas)."""
    def p(x, y, rng, n):
        c = (236, 226, 196)
        ridge = 20 - 6 * abs(math.sin(x * 0.18)) - 3 * math.sin(x * 0.5 + 1)
        if y > ridge:
            c = mix((92, 110, 120), (170, 180, 170), (y - ridge) / 10.0)
        if math.hypot(x - 23, y - 8) < 3.2:
            c = (196, 48, 40)
        if x % 9 == 4 and 14 < y < ridge + 2 and y % 2 == 0:
            c = (40, 64, 44)
        if x % 4 == 0:
            c = shade(c, 0.9)  # the ribs show through
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def chessboard(seed):
    """Xiangqi board: 9 x 10 ink lines on pale wood, the river band in the middle."""
    def p(x, y, rng, n):
        c = mix(rgb("D8B878"), rgb("E8CC90"), n.at(x, y, 8))
        gx, gy = (x - 1) % 3.5, (y - 1) % 3
        if (x - 2) % 3 == 0 and 2 <= x <= 29 and not (14 <= y <= 17):
            c = (60, 40, 24)
        if (y - 2) % 3 == 0 and 2 <= y <= 29 and 2 <= x <= 29:
            c = (60, 40, 24)
        if 14 <= y <= 17 and 4 < x < 28 and (x + y) % 5 == 0:
            c = (140, 40, 30)  # the river script
        return c
    return p


def abacus_beads(seed):
    def p(x, y, rng, n):
        cx, cy = x % 4 - 1.5, y % 4 - 1.5
        r = math.hypot(cx, cy)
        if r < 1.8:
            c = mix(rgb("9A3A22"), rgb("4A1A10"), r / 1.8)
            if cx < -0.3 and cy < -0.3:
                c = (220, 170, 130)
        else:
            c = rgb("2A1A10")
        return c
    return p


def book_cover(seed):
    """Indigo bound cover with a pale title slip and the four stitch holes of thread binding."""
    def p(x, y, rng, n):
        c = shade(rgb("2E3E6A"), 0.85 + 0.25 * n.at(x, y, 8))
        if 20 <= x <= 25 and 3 <= y <= 24:
            c = (230, 220, 190)
            if x in (22, 23) and y % 3 != 0 and 5 < y < 22:
                c = (40, 30, 28)
        if x == 3 and y % 7 == 3:
            c = (230, 220, 190)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def hair_tip(seed):
    def p(x, y, rng, n):
        c = mix((20, 18, 18), (70, 60, 56), y / 31.0)
        if x % 2:
            c = shade(c, 0.8)
        return c
    return p


def red_paper(seed):
    def p(x, y, rng, n):
        c = mix(rgb("B02020"), rgb("E04030"), n.at(x, y, 8))
        if x % 8 == 0 or y % 8 == 0:
            c = shade(c, 0.7)  # the bamboo frame lines
        if 10 < x < 22 and 12 < y < 20 and (x + y) % 3 == 0:
            c = (240, 200, 90)  # a gold character
        return c
    return p


def build_materials():
    tex("kayu_tua", wood(PAL["kayu_tua"], 11), 11)
    tex("kayu_muda", wood(PAL["kayu_muda"], 12), 12)
    tex("kayu_merah", wood("7A2E1E", 13), 13)
    tex("bambu", bamboo(14), 14)
    tex("lak_merah", lacquer(PAL["lak"], 15), 15)
    tex("lak_hitam", lacquer("1E1A1E", 16), 16)
    tex("besi", metal(PAL["besi"], 17), 17)
    tex("besi_gelap", metal("44484E", 18), 18)
    tex("perunggu", bronze(19), 19)
    tex("emas", gold(20), 20)
    tex("giok", jade(21), 21)
    tex("kertas", paper(22), 22)
    tex("kertas_tulis", paper(23, lines=True), 23)
    tex("kertas_lukis", painted_paper(24), 24)
    tex("kertas_merah", red_paper(25), 25)
    tex("kain_merah", cloth("A82828", 26), 26)
    tex("kain_biru", cloth(PAL["sutra_biru"], 27), 27)
    tex("kain_kasar", cloth(PAL["kain_kasar"], 28), 28)
    tex("kain_putih", cloth("E8E4DA", 29), 29)
    tex("porselen", porcelain(30), 30)
    tex("nasi", rice(31), 31)
    tex("teh", tea(32), 32)
    tex("jerami", straw(33), 33)
    tex("ijuk", fibre(34), 34)
    tex("kulit", leather(35), 35)
    tex("tali", rope(36), 36)
    tex("batu_kasar", stone(37, "8A8278"), 37)
    tex("batu_kulit_s", stone(38, "7E7466", vein="D0F0FF"), 38)
    tex("batu_kulit_m", stone(39, "6E6A62", vein="A0FFD8"), 39)
    tex("batu_kulit_l", stone(40, "8C7C68", vein="FFE0A0"), 40)
    tex("batu_kulit_xl", stone(41, "5A5660", vein="E0B0FF"), 41)
    tex("batu_dalam", glow("6ED8B0", 42), 42)
    tex("bara", ember(43), 43)
    tex("dupa", incense(44), 44)
    tex("plester", plaster(45), 45)
    tex("tanah", soil(46), 46)
    tex("daun", leaf(47), 47)
    tex("air", water(48), 48)
    tex("papan_catur", chessboard(49), 49)
    tex("manik", abacus_beads(50), 50)
    tex("sampul_kitab", book_cover(51), 51)
    tex("bulu_kuas", hair_tip(52), 52)
    tex("api", glow("FF9A30", 53), 53)
    tex("porselen_putih", cloth("F0F0EC", 54, weave=False), 54)
    tex("kaca", glow("9AB8C8", 55), 55)


# ================================================================================================ the model builder

MODELS = {}


class Model:
    def __init__(self, mid, folder):
        self.id = mid
        self.folder = folder
        self.els = []
        self.tex = {}
        self.dy = 0.0  # long items slide along the handle so they fit in -16..32 (the anchor stays on the handle)
        MODELS[(folder, mid)] = self

    def key(self, name):
        if name not in self.tex:
            self.tex[name] = "t%d" % len(self.tex)
        return self.tex[name]

    def box(self, f, t, skin, rot=None, glow=0, overrides=None, full=False):
        f2 = [round(min(f[i], t[i]), 4) for i in range(3)]
        t2 = [round(max(f[i], t[i]), 4) for i in range(3)]
        e = {"from": f2, "to": t2, "faces": {}}
        idx = len(self.els)
        dims = {"north": (t2[0] - f2[0], t2[1] - f2[1]), "south": (t2[0] - f2[0], t2[1] - f2[1]),
                "east": (t2[2] - f2[2], t2[1] - f2[1]), "west": (t2[2] - f2[2], t2[1] - f2[1]),
                "up": (t2[0] - f2[0], t2[2] - f2[2]), "down": (t2[0] - f2[0], t2[2] - f2[2])}
        for side in SIDES:
            name = (overrides or {}).get(side, skin)
            if name is None:
                continue
            if full:
                uv = [0, 0, 16, 16]
            else:
                w, h = dims[side]
                w = max(0.25, min(16.0, w))
                h = max(0.25, min(16.0, h))
                u0 = ((idx * 5 + SIDES.index(side) * 3) % 16) * (16.0 - w) / 16.0
                v0 = ((idx * 3 + SIDES.index(side) * 7) % 16) * (16.0 - h) / 16.0
                uv = [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)]
            e["faces"][side] = {"uv": uv, "texture": "#" + self.key(name)}
        if rot:
            axis, angle, origin = rot
            if angle:
                e["rotation"] = {"origin": [round(v, 4) for v in origin], "axis": axis, "angle": angle}
        if glow:
            e["light_emission"] = int(glow)
        self.els.append(e)
        return e

    def c(self, cx, cy, cz, w, h, d, skin, rot=None, glow=0, overrides=None, full=False):
        """A cuboid centred at anchor + (cx, cy, cz) of size w x h x d; rot = (axis, angle) about that centre, or
        (axis, angle, (ox, oy, oz)) about anchor + that offset."""
        x, y, z = 8 + cx, 8 + cy + self.dy, 8 + cz
        r = None
        if rot:
            if len(rot) == 2:
                r = (rot[0], rot[1], (x, y, z))
            else:
                o = rot[2]
                r = (rot[0], rot[1], (8 + o[0], 8 + o[1] + self.dy, 8 + o[2]))
        return self.box((x - w / 2, y - h / 2, z - d / 2), (x + w / 2, y + h / 2, z + d / 2), skin, r, glow, overrides, full)

    def ring(self, cy, w, h, d, t, skin, cz=0.0, cx=0.0):
        """Four walls of a w x h x d box with wall thickness t (bowls, cups, buckets, baskets)."""
        self.c(cx, cy, cz - d / 2 + t / 2, w, h, t, skin)
        self.c(cx, cy, cz + d / 2 - t / 2, w, h, t, skin)
        self.c(cx - w / 2 + t / 2, cy, cz, t, h, d - 2 * t, skin)
        self.c(cx + w / 2 - t / 2, cy, cz, t, h, d - 2 * t, skin)

    def bounds(self):
        lo = [min(e["from"][i] for e in self.els) for i in range(3)]
        hi = [max(e["to"][i] for e in self.els) for i in range(3)]
        return lo, hi


def prop(name):
    return Model(name, "tempat")


# ================================================================================================ props 1-40 (38.O.4)

def build_props():
    # 1 mangkuk: porcelain bowl, foot ring, heaped rice
    m = prop("mangkuk")
    m.c(0, 0.25, 0, 3.0, 0.5, 3.0, "porselen_putih")
    m.c(0, 0.75, 0, 4.4, 0.5, 4.4, "porselen")
    m.ring(2.2, 6.0, 2.4, 6.0, 0.6, "porselen")
    m.c(0, 2.8, 0, 4.8, 0.8, 4.8, "nasi")
    m.c(0, 3.4, 0, 3.0, 0.6, 3.0, "nasi")
    m.c(0.6, 3.8, -0.4, 1.4, 0.4, 1.2, "nasi")

    # 2 sumpit: a pair of lacquered chopsticks with gold-capped ends, tips forward
    m = prop("sumpit")
    for dx in (-0.45, 0.45):
        m.c(dx, 0, -1.0, 0.5, 0.5, 6.0, "lak_merah")
        m.c(dx, 0, -5.0, 0.35, 0.35, 2.0, "kayu_muda")
        m.c(dx, 0, 2.3, 0.55, 0.55, 0.6, "emas")
    m.c(0, 0, 1.4, 1.5, 0.3, 0.3, "kain_merah")

    # 3 cangkir: handleless tea cup with tea inside
    m = prop("cangkir")
    m.c(0, 0.2, 0, 2.0, 0.4, 2.0, "porselen_putih")
    m.c(0, 0.55, 0, 2.6, 0.3, 2.6, "porselen")
    m.ring(1.85, 3.0, 2.3, 3.0, 0.4, "porselen")
    m.c(0, 2.4, 0, 2.2, 0.2, 2.2, "teh")

    # 4 lepek: saucer with a raised rim
    m = prop("lepek")
    m.c(0, 0.15, 0, 3.0, 0.3, 3.0, "porselen")
    m.ring(0.45, 4.0, 0.3, 4.0, 0.4, "porselen")
    m.c(0, 0.32, 0, 1.6, 0.05, 1.6, "porselen_putih")

    # 5 teko: teapot held by its side handle (anchor), spout forward
    m = prop("teko")
    m.c(0, 0, -3.6, 5.0, 4.0, 5.0, "porselen")
    m.c(0, -2.2, -3.6, 3.6, 0.4, 3.6, "porselen_putih")
    m.c(0, 2.4, -3.6, 3.0, 1.0, 3.0, "porselen")
    m.c(0, 3.2, -3.6, 1.0, 0.8, 1.0, "lak_merah")
    m.c(0, 0.6, -7.2, 1.0, 1.0, 3.0, "porselen", rot=("x", 22.5))
    m.c(0, 1.4, -8.6, 0.8, 0.8, 0.8, "porselen")
    # the side handle loop: top bar, grip, bottom bar
    m.c(0, 1.6, -0.6, 1.0, 0.8, 1.6, "bambu")
    m.c(0, 0, 0.2, 1.0, 3.6, 0.8, "bambu")
    m.c(0, -1.6, -0.6, 1.0, 0.8, 1.6, "bambu")

    # 6 sendok_sayur: ladle, bowl at the far end
    m = prop("sendok_sayur")
    m.c(0, 3.0, 0, 0.5, 9.0, 0.5, "kayu_muda")
    m.c(0, 7.6, -0.6, 2.0, 0.4, 2.0, "besi")
    m.ring(8.1, 2.4, 0.8, 2.4, 0.3, "besi", cz=-0.6)
    m.c(0, -1.7, 0, 0.7, 0.6, 0.7, "besi")

    # 7 sapu: broom, the hand at the top of the handle, bristles at the far end
    m = prop("sapu")
    m.c(0, 6.0, 0, 1.0, 18.0, 1.0, "bambu")
    m.c(0, 15.8, 0, 2.6, 1.2, 1.6, "tali")
    m.c(0, 18.4, 0, 4.0, 4.0, 1.8, "ijuk")
    m.c(0, 21.6, 0, 5.0, 2.6, 2.0, "ijuk")
    m.c(-2.2, 22.4, 0, 1.2, 1.6, 1.6, "ijuk", rot=("z", 22.5))
    m.c(2.2, 22.4, 0, 1.2, 1.6, 1.6, "ijuk", rot=("z", -22.5))
    m.c(0, 17.0, 0, 3.2, 0.5, 1.9, "tali")

    # 8 palu: hammer, head at the far end
    m = prop("palu")
    m.c(0, 3.0, 0, 1.0, 10.0, 1.0, "kayu_tua")
    m.c(0, -2.2, 0, 1.2, 0.8, 1.2, "kulit")
    m.c(0, 8.6, 0, 3.0, 3.0, 6.0, "besi_gelap")
    m.c(0, 8.6, -3.3, 2.4, 2.4, 0.6, "besi")
    m.c(0, 8.6, 3.3, 1.6, 2.0, 0.6, "besi")
    m.c(0, 6.9, 0, 1.4, 0.5, 1.4, "besi")

    # 9 alu: pestle, thicker grinding end
    m = prop("alu")
    m.c(0, 1.0, 0, 1.5, 6.0, 1.5, "kayu_muda")
    m.c(0, 5.0, 0, 2.0, 3.0, 2.0, "kayu_muda")
    m.c(0, 6.7, 0, 1.6, 0.6, 1.6, "batu_kasar")
    m.c(0, -2.2, 0, 1.2, 0.6, 1.2, "kayu_tua")

    # 10 kuas: writing brush
    m = prop("kuas")
    m.c(0, 1.5, 0, 0.6, 7.0, 0.6, "bambu")
    m.c(0, 5.3, 0, 0.8, 0.6, 0.8, "kayu_tua")
    m.c(0, 6.4, 0, 1.0, 1.6, 1.0, "bulu_kuas")
    m.c(0, 7.5, 0, 0.6, 0.6, 0.6, "bulu_kuas")
    m.c(0, -2.2, 0, 0.3, 0.6, 0.3, "kain_merah")

    # 11 kitab: thread-bound book with a title slip
    m = prop("kitab")
    m.c(0, 0, 0, 6.0, 1.5, 8.0, "kertas", overrides={"up": "sampul_kitab", "down": "sampul_kitab"})
    m.c(-3.0, 0, 0, 0.3, 1.6, 8.1, "kain_biru")
    for z in (-2.7, -0.9, 0.9, 2.7):
        m.c(-2.7, 0, z, 0.8, 1.7, 0.25, "tali")

    # 12 sempoa: abacus frame, beam, rods and bead rows
    m = prop("sempoa")
    m.c(0, 0, 0, 9.0, 0.6, 1.0, "kayu_merah")
    m.c(0, 5.0, 0, 9.0, 0.6, 1.0, "kayu_merah")
    m.c(-4.2, 2.5, 0, 0.6, 5.6, 1.0, "kayu_merah")
    m.c(4.2, 2.5, 0, 0.6, 5.6, 1.0, "kayu_merah")
    m.c(0, 3.6, 0, 7.8, 0.4, 0.9, "kayu_merah")
    for i in range(7):
        x = -3.3 + i * 1.1
        m.c(x, 2.5, 0, 0.2, 4.4, 0.2, "bambu")
        m.c(x, 4.3, 0, 0.9, 0.9, 0.9, "manik")
        m.c(x, 1.3, 0, 0.9, 1.9, 0.9, "manik")

    # 13 tongkat: walking stick, crook at the hand, tip at the far end
    m = prop("tongkat")
    m.c(0, 7.0, 0, 1.0, 18.0, 1.0, "kayu_tua")
    m.c(0, -2.4, -1.0, 1.2, 1.2, 3.0, "kayu_tua")
    m.c(0, -1.6, -2.4, 1.0, 2.0, 1.0, "kayu_tua")
    m.c(0, 15.6, 0, 1.2, 0.8, 1.2, "perunggu")
    m.c(0, 2.0, 0, 1.2, 0.6, 1.2, "tali")
    m.c(0, 1.2, 0.0, 0.4, 2.2, 0.4, "kain_merah", rot=("x", 22.5))

    # 14 peti: iron-banded chest, carried by its top handle
    m = prop("peti")
    m.c(0, -4.0, 0, 8.0, 6.0, 6.0, "kayu_merah")
    m.c(0, -0.7, 0, 8.2, 0.8, 6.2, "kayu_merah")
    for x in (-3.0, 3.0):
        m.c(x, -3.7, 0, 0.8, 6.6, 6.4, "besi_gelap")
    m.c(0, -1.8, -3.15, 1.4, 1.6, 0.3, "perunggu")
    m.c(0, 0.0, 0, 3.0, 0.6, 0.8, "besi")
    m.c(-1.3, -0.3, 0, 0.5, 0.8, 0.6, "besi")
    m.c(1.3, -0.3, 0, 0.5, 0.8, 0.6, "besi")

    # 15 pikulan: carrying pole over the shoulder with two baskets hanging on ropes
    m = prop("pikulan")
    m.c(0, 0, 0, 1.0, 1.0, 24.0, "bambu")
    for z in (-10.5, 10.5):
        m.c(-1.0, -2.0, z, 0.3, 4.0, 0.3, "tali")
        m.c(1.0, -2.0, z, 0.3, 4.0, 0.3, "tali")
        m.ring(-6.0, 5.0, 4.0, 5.0, 0.5, "jerami", cz=z)
        m.c(0, -7.8, z, 4.0, 0.4, 4.0, "jerami")
        m.c(0, -4.2, z, 4.0, 0.6, 4.0, "daun" if z < 0 else "nasi")

    # 16 joran: fishing rod in three tapering sections, a line guide and the line
    m = prop("joran")
    m.c(0, 3.0, 0, 0.9, 8.0, 0.9, "bambu")
    m.c(0, 11.0, 0, 0.6, 8.0, 0.6, "bambu")
    m.c(0, 19.0, 0, 0.35, 8.0, 0.35, "bambu")
    m.c(0, -1.4, 0, 1.2, 1.2, 1.2, "tali")
    m.c(0, 22.6, -0.4, 0.15, 0.6, 0.6, "tali")
    m.c(0.5, 0.6, 0, 0.3, 1.6, 1.6, "kayu_tua")

    # 17 cangkul: hoe, iron blade at the far end bent forward
    m = prop("cangkul")
    m.c(0, 5.0, 0, 1.0, 14.0, 1.0, "kayu_muda")
    m.c(0, 12.4, -1.2, 1.4, 1.4, 2.6, "besi_gelap")
    m.c(0, 11.4, -3.6, 4.0, 3.0, 1.0, "besi", rot=("x", -22.5))
    m.c(0, 9.9, -4.3, 4.0, 0.4, 0.6, "besi")

    # 18 keranjang: woven basket with a hoop handle (anchor at the handle top)
    m = prop("keranjang")
    m.ring(-6.5, 6.0, 5.0, 6.0, 0.6, "jerami")
    m.c(0, -8.8, 0, 5.4, 0.5, 5.4, "jerami")
    m.c(0, -4.0, 0, 6.2, 0.4, 6.2, "bambu")
    m.c(-2.8, -1.8, 0, 0.5, 4.0, 0.5, "bambu")
    m.c(2.8, -1.8, 0, 0.5, 4.0, 0.5, "bambu")
    m.c(0, 0.2, 0, 6.0, 0.5, 0.5, "bambu")
    m.c(-1.0, -4.6, -0.6, 2.0, 1.2, 2.0, "daun")
    m.c(1.4, -4.8, 1.0, 1.6, 1.0, 1.6, "kain_merah")

    # 19 ember: wooden bucket with iron hoops, rope handle, water
    m = prop("ember")
    m.ring(-5.0, 4.0, 5.0, 4.0, 0.5, "kayu_muda")
    m.c(0, -7.3, 0, 3.4, 0.4, 3.4, "kayu_muda")
    m.c(0, -3.6, 0, 4.2, 0.4, 4.2, "besi_gelap")
    m.c(0, -6.4, 0, 4.2, 0.4, 4.2, "besi_gelap")
    m.c(0, -3.2, 0, 3.0, 0.2, 3.0, "air")
    m.c(-2.1, -1.2, 0, 0.3, 2.4, 0.3, "tali")
    m.c(2.1, -1.2, 0, 0.3, 2.4, 0.3, "tali")
    m.c(0, 0, 0, 4.0, 0.5, 0.5, "kayu_tua")

    # 20 dupa: three incense sticks with glowing tips
    m = prop("dupa")
    for i, (dx, dz) in enumerate(((-0.6, 0.0), (0.0, -0.4), (0.6, 0.1))):
        m.c(dx, 2.0, dz, 0.4, 6.0, 0.4, "dupa")
        m.c(dx, -1.5, dz, 0.3, 1.0, 0.3, "kayu_merah")
        m.c(dx, 5.2, dz, 0.45, 0.4, 0.45, "bara", glow=10)
    m.c(0, -0.2, 0, 1.8, 0.6, 1.0, "kertas_merah")

    # 21 pemukul: drumstick with a cloth ball
    m = prop("pemukul")
    m.c(0, 2.0, 0, 1.0, 9.0, 1.0, "kayu_merah")
    m.c(0, 7.0, 0, 2.0, 2.0, 2.0, "kain_merah")
    m.c(0, 6.0, 0, 1.4, 0.4, 1.4, "tali")
    m.c(0, -2.2, 0, 0.4, 1.2, 0.4, "kain_merah")

    # 22 palu_kayu: the judge's wooden gavel
    m = prop("palu_kayu")
    m.c(0, 1.5, 0, 1.0, 5.0, 1.0, "kayu_merah")
    m.c(0, 4.8, 0, 2.0, 2.0, 3.0, "kayu_tua")
    m.c(0, 4.8, -1.6, 2.2, 2.2, 0.3, "emas")
    m.c(0, 4.8, 1.6, 2.2, 2.2, 0.3, "emas")

    # 23 kipas_lipat: open folding fan, seven painted leaves spread from the pivot
    m = prop("kipas_lipat")
    angles = (-45.0, -22.5, 0.0, 22.5, 45.0)
    for a in angles:
        m.c(0, 4.0, 0, 3.4, 7.0, 0.2, "kertas_lukis", rot=("z", a, (0, 0.5, 0)))
        m.c(0, 2.0, 0.15, 0.3, 4.0, 0.12, "kayu_tua", rot=("z", a, (0, 0.5, 0)))
    m.c(0, 0.3, 0.2, 0.9, 0.9, 0.6, "emas")
    m.c(0, -0.8, 0.2, 0.4, 1.6, 0.4, "kain_merah")

    # 24 papan_catur: xiangqi board with pieces
    m = prop("papan_catur")
    m.c(0, 0, 0, 8.0, 0.6, 8.0, "kayu_muda")
    m.c(0, 0.32, 0, 7.6, 0.05, 7.6, "papan_catur", full=True)
    pieces = ((-2.5, -3.0, "lak_merah"), (0.6, -2.0, "lak_merah"), (2.6, -0.4, "lak_merah"),
              (-1.4, 1.8, "lak_hitam"), (1.6, 2.6, "lak_hitam"), (-3.0, 3.2, "lak_hitam"))
    for x, z, s in pieces:
        m.c(x, 0.6, z, 1.0, 0.6, 1.0, "porselen_putih", overrides={"up": s})

    # 25 payung_kertas: oiled-paper umbrella, open; the hand on the shaft below the canopy
    m = prop("payung_kertas")
    m.c(0, 5.0, 0, 0.6, 14.0, 0.6, "bambu")
    m.c(0, -1.6, 0, 0.9, 1.2, 0.9, "kayu_tua")
    m.c(0, 12.0, 0, 12.0, 0.5, 12.0, "kertas_lukis")
    m.c(0, 12.0, 0, 12.0, 0.5, 12.0, "kertas_lukis", rot=("y", 45.0))
    m.c(0, 12.5, 0, 9.0, 0.5, 9.0, "kertas_lukis", rot=("y", 22.5))
    m.c(0, 13.0, 0, 6.0, 0.5, 6.0, "kertas_lukis")
    m.c(0, 13.5, 0, 3.0, 0.5, 3.0, "kertas_merah")
    for a in (0.0, 45.0):
        m.c(0, 11.6, 0, 0.3, 0.3, 11.6, "bambu", rot=("y", a))
        m.c(0, 11.6, 0, 11.6, 0.3, 0.3, "bambu", rot=("y", a))
    m.c(0, 9.0, 0, 1.4, 0.6, 1.4, "bambu")
    m.c(0, 14.0, 0, 0.8, 0.6, 0.8, "kayu_tua")

    # 26 gasing: spinning top with a painted band and iron spike
    m = prop("gasing")
    m.c(0, 1.0, 0, 2.0, 2.0, 2.0, "kayu_merah")
    m.c(0, 1.0, 0, 2.4, 0.6, 2.4, "kain_biru")
    m.c(0, -0.5, 0, 0.5, 1.0, 0.5, "besi")
    m.c(0, 2.4, 0, 0.6, 0.8, 0.6, "kayu_tua")
    m.c(0, 1.0, 0, 2.4, 0.4, 2.4, "emas", rot=("y", 45.0))

    # 27 layang_layang: kite: bamboo cross, cloth sail, three tail ribbons, the string at the anchor
    m = prop("layang_layang")
    m.c(0, 5.0, 0, 8.0, 0.2, 10.0, "kertas_merah", rot=("y", 45.0))
    m.c(0, 5.2, 0, 0.3, 0.3, 11.0, "bambu")
    m.c(0, 5.2, -1.0, 9.0, 0.3, 0.3, "bambu")
    for i in range(3):
        m.c(0, 5.0, 6.0 + i * 2.2, 1.6 - i * 0.3, 0.1, 2.0, "kain_biru" if i % 2 else "kain_merah")
    m.c(0, 2.5, 0, 0.12, 5.0, 0.12, "tali")
    m.c(0, 0, 0, 1.2, 1.2, 1.2, "bambu")

    # 28 lentera_tangan: hand lantern: bamboo frame, red paper body, flame, handle pole
    m = prop("lentera_tangan")
    m.c(0, 3.0, 0, 0.5, 8.0, 0.5, "bambu")
    m.c(0, 7.0, -1.6, 0.4, 0.4, 3.6, "bambu")
    m.c(0, 6.0, -3.4, 0.2, 2.0, 0.2, "tali")
    m.c(0, 2.0, -3.4, 4.0, 6.0, 4.0, "kertas_merah", glow=8)
    m.c(0, 5.2, -3.4, 4.4, 0.4, 4.4, "bambu")
    m.c(0, -1.2, -3.4, 4.4, 0.4, 4.4, "bambu")
    m.c(0, 2.0, -3.4, 1.0, 1.6, 1.0, "api", glow=15)
    m.c(0, -2.4, -3.4, 0.6, 2.0, 0.6, "kain_merah")

    # 29 kantong_uang: coin purse with a drawstring and a coin
    m = prop("kantong_uang")
    m.c(0, -1.5, 0, 3.0, 3.0, 3.0, "kain_merah")
    m.c(0, 0.3, 0, 2.0, 0.8, 2.0, "kain_merah")
    m.c(0, 0.5, 0, 2.3, 0.4, 2.3, "emas")
    m.c(0, 1.0, 0, 0.4, 1.0, 0.4, "tali")
    m.c(1.0, -1.6, -1.6, 1.2, 1.2, 0.2, "emas")

    # 30 nampan: serving tray with two cups
    m = prop("nampan")
    m.c(0, 0, 0, 8.0, 0.5, 6.0, "lak_merah")
    m.ring(0.5, 8.0, 0.6, 6.0, 0.4, "lak_hitam")
    for x in (-1.8, 1.8):
        m.c(x, 0.5, 0, 1.4, 0.3, 1.4, "porselen_putih")
        m.ring(1.5, 2.0, 1.8, 2.0, 0.3, "porselen", cx=x)
        m.c(x, 2.0, 0, 1.4, 0.2, 1.4, "teh")

    # 31 sendok_plester: plasterer's trowel with wet plaster
    m = prop("sendok_plester")
    m.c(0, 1.0, 0, 0.9, 4.0, 0.9, "kayu_muda")
    m.c(0, 3.3, 0, 0.4, 0.8, 0.4, "besi_gelap")
    m.c(0, 4.0, -2.0, 4.0, 0.3, 5.0, "besi")
    m.c(0.4, 4.3, -2.6, 2.4, 0.3, 2.6, "plester")

    # 32 papan_kayu: three planks stacked on the shoulder
    m = prop("papan_kayu")
    for i, dx in enumerate((-0.2, 0.3, 0.0)):
        m.c(dx, i * 1.0, 0, 3.0, 1.0, 12.0, "kayu_muda" if i != 1 else "kayu_tua")
    m.c(0, 1.0, -4.0, 3.4, 3.2, 0.3, "tali")
    m.c(0, 1.0, 4.0, 3.4, 3.2, 0.3, "tali")

    # 33 sekop: shovel, blade at the far end
    m = prop("sekop")
    m.c(0, 4.0, 0, 1.0, 12.0, 1.0, "kayu_muda")
    m.c(0, -2.2, 0, 2.6, 0.6, 0.8, "kayu_tua")
    m.c(0, 10.6, 0, 1.4, 1.2, 1.4, "besi_gelap")
    m.c(0, 13.4, -0.4, 4.0, 5.0, 0.5, "besi")
    m.c(0, 13.4, 0.0, 4.2, 4.6, 0.3, "tanah")

    # 34 bibit: seedling in a clay pot
    m = prop("bibit")
    m.c(0, -1.5, 0, 3.0, 3.0, 3.0, "kayu_merah")
    m.c(0, -0.1, 0, 3.4, 0.4, 3.4, "kayu_merah")
    m.c(0, 0.1, 0, 2.4, 0.2, 2.4, "tanah")
    m.c(0, 1.4, 0, 0.3, 2.8, 0.3, "daun")
    m.c(-0.7, 2.4, 0, 1.6, 0.2, 1.0, "daun", rot=("z", 22.5))
    m.c(0.7, 2.9, 0, 1.6, 0.2, 1.0, "daun", rot=("z", -22.5))

    # 35 tombak: guard's spear: butt cap, shaft, red horsehair tassel, leaf blade
    m = prop("tombak")
    m.dy = -5.0
    m.c(0, 6.0, 0, 1.0, 30.0, 1.0, "kayu_merah")
    m.c(0, -9.4, 0, 1.3, 1.0, 1.3, "perunggu")
    m.c(0, 21.4, 0, 1.4, 1.2, 1.4, "perunggu")
    m.c(0, 20.4, 0, 2.2, 1.4, 2.2, "kain_merah")
    m.c(0, 19.2, 0, 1.8, 1.4, 1.8, "kain_merah")
    m.c(0, 24.4, 0, 0.6, 5.0, 2.0, "besi")
    m.c(0, 27.4, 0, 0.5, 1.6, 1.2, "besi")
    m.c(0, 23.6, 0, 0.8, 2.0, 0.6, "besi_gelap")

    # 36 kain: folded cloth for drying (draped over the forearm)
    m = prop("kain")
    m.c(0, 0, 0, 6.0, 0.5, 8.0, "kain_biru")
    m.c(0, 0.5, 0, 6.0, 0.5, 8.0, "kain_putih")
    m.c(0, -1.6, -4.1, 6.0, 3.6, 0.3, "kain_biru")
    m.c(0, -1.0, 4.1, 6.0, 2.4, 0.3, "kain_putih")

    # 37 teropong: brass spyglass, two draws, eyepiece at the anchor
    m = prop("teropong")
    m.c(0, 0, -1.5, 1.6, 1.6, 3.0, "perunggu")
    m.c(0, 0, -5.0, 2.0, 2.0, 4.0, "kulit")
    m.c(0, 0, -7.6, 2.4, 2.4, 1.2, "perunggu")
    m.c(0, 0, -7.9, 1.8, 1.8, 0.2, "kaca", glow=4)
    m.c(0, 0, 0.3, 1.2, 1.2, 0.6, "emas")

    # 38 labu_pil: porcelain pill gourd with a red stopper and cord
    m = prop("labu_pil")
    m.c(0, -1.5, 0, 3.0, 3.0, 3.0, "porselen")
    m.c(0, 0.8, 0, 2.0, 2.0, 2.0, "porselen")
    m.c(0, 0.0, 0, 2.2, 0.4, 2.2, "tali")
    m.c(0, 2.2, 0, 1.2, 0.8, 1.2, "lak_merah")
    m.c(0, 1.0, 1.2, 0.3, 2.4, 0.3, "kain_merah", rot=("x", 22.5))

    # 39 pahat: stone chisel
    m = prop("pahat")
    m.c(0, 0.5, 0, 0.8, 4.0, 0.8, "kayu_tua")
    m.c(0, -1.8, 0, 1.0, 0.6, 1.0, "besi_gelap")
    m.c(0, 3.4, 0, 0.6, 2.0, 1.0, "besi")
    m.c(0, 4.6, 0, 0.3, 0.6, 1.0, "besi")

    # 40 batu_mentah: raw gamble stone, four sizes, rough skin with a thin glowing vein; two halves when cut
    sizes = {"batu_mentah": ("m", 1.0), "batu_mentah_s": ("s", 0.7), "batu_mentah_l": ("l", 1.3), "batu_mentah_xl": ("xl", 1.6)}
    for name, (k, s) in sizes.items():
        m = prop(name)
        skin = "batu_kulit_" + k
        m.c(0, 0, 0, 4.0 * s, 3.4 * s, 3.6 * s, skin)
        m.c(0.6 * s, 0.6 * s, -0.4 * s, 3.2 * s, 3.0 * s, 3.0 * s, skin, rot=("y", 22.5))
        m.c(-0.8 * s, -0.4 * s, 0.6 * s, 2.8 * s, 2.6 * s, 3.0 * s, skin, rot=("z", 22.5))
        m.c(0.2 * s, 1.4 * s, 0.2 * s, 2.4 * s, 1.6 * s, 2.4 * s, skin, rot=("x", -22.5))
        m.c(0, 0.2 * s, -1.9 * s, 2.6 * s, 0.25, 0.1, "batu_dalam", glow=7)
    for side, sign in (("kiri", -1), ("kanan", 1)):
        m = prop("batu_mentah_belah_" + side)
        m.c(sign * 1.0, 0, 0, 2.0, 3.4, 3.6, "batu_kulit_m", overrides={"west" if sign > 0 else "east": "batu_dalam"},
            glow=0)
        m.c(sign * 1.4, 0.6, -0.4, 1.4, 2.8, 2.8, "batu_kulit_m", rot=("y", 22.5))
        m.c(sign * 0.05, 0, 0, 0.1, 2.6, 2.8, "batu_dalam", glow=12)


# ================================================================================================ city weapons (38.O.4)

GRADES = {
    # blade base, edge highlight, fitting, glow (None = no emission)
    "fana": dict(blade="8C9298", edge="D8DEE4", fit="kayu_tua", guard="perunggu", glow=None, seed=61),
    "roh_rendah": dict(blade="6E8EAE", edge="C8E4FF", fit="kayu_merah", guard="besi", glow="9AD0FF", seed=62),
    "roh_menengah": dict(blade="B8BEC8", edge="FFFFFF", fit="lak_hitam", guard="perunggu", glow="E0E8FF", seed=63),
    "roh_tinggi": dict(blade="58B890", edge="D8FFEC", fit="lak_merah", guard="emas", glow="7CFFC8", seed=64),
}
FORMS = ("jian", "dao", "qiang", "kapak", "gada", "kipas_besi", "kebut")
WEAPON_TEX = {}


def blade_tex(g):
    base, edge = rgb(g["blade"]), rgb(g["edge"])
    vein = rgb(g["glow"]) if g["glow"] else None

    def p(x, y, rng, n):
        t = abs(x - 15.5) / 15.5
        c = mix(edge, base, min(1.0, t * 1.6 + 0.2))
        if x in (15, 16):
            c = shade(c, 0.82)  # the ridge line
        c = shade(c, 0.9 + 0.2 * n.at(x, y, 8))
        if vein and abs(math.sin(y * 0.3 + x * 0.15 + n.at(x, y, 8) * 3)) < 0.07:
            c = mix(c, vein, 0.8)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def horsetail_tex(seed):
    ph = random.Random(seed)
    phase = [ph.random() * 6.28 for _ in range(32)]

    def p(x, y, rng, n):
        g = 0.5 + 0.5 * math.sin(phase[x] + y * 0.25)
        return mix((206, 200, 188), (250, 248, 240), g)
    return p


def build_weapon_textures():
    for gid, g in GRADES.items():
        tex("bilah_" + gid, blade_tex(g), g["seed"], store=WEAPON_TEX)
    tex("ekor_kuda", horsetail_tex(65), 65, store=WEAPON_TEX)
    tex("rumbai", cloth("B82020", 66), 66, store=WEAPON_TEX)


def weapon(form, gid):
    g = GRADES[gid]
    m = Model("%s_%s" % (form, gid), "senjata")
    blade, fit, guard = "bilah_" + gid, g["fit"], g["guard"]
    lum = 6 if g["glow"] else 0
    m.dy = {"jian": -4.0, "qiang": -6.0}.get(form, 0.0)
    if form == "jian":
        m.c(0, 0, 0, 1.0, 6.0, 1.2, fit)
        m.c(0, -3.4, 0, 1.6, 0.8, 1.6, guard)
        m.c(0, 3.4, 0, 6.0, 1.0, 2.0, guard)
        m.c(0, 15.0, 0, 0.6, 22.0, 2.0, blade, glow=lum)
        m.c(0, 26.6, 0, 0.5, 1.2, 1.0, blade, glow=lum)
        m.c(0, -4.6, 0.6, 0.4, 2.0, 0.4, "rumbai", rot=("x", 22.5))
    elif form == "dao":
        m.c(0, 0, 0, 1.2, 6.0, 1.4, fit)
        m.c(0, -3.4, 0, 1.6, 0.8, 1.8, guard)
        m.c(0, 3.3, 0, 2.0, 0.8, 3.6, guard)
        m.c(0, 8.4, -0.4, 0.6, 9.0, 2.8, blade, glow=lum)
        m.c(0, 15.6, -1.4, 0.6, 6.0, 3.0, blade, rot=("x", -22.5, (0, 12.6, -0.4)), glow=lum)
        m.c(0, 19.6, -3.2, 0.6, 3.6, 2.6, blade, rot=("x", -45.0, (0, 17.8, -2.0)), glow=lum)
        m.c(0, 6.0, 1.3, 0.7, 4.0, 0.4, guard)
    elif form == "qiang":
        m.c(0, 6.0, 0, 1.0, 30.0, 1.0, fit)
        m.c(0, -9.4, 0, 1.3, 1.0, 1.3, guard)
        m.c(0, 21.6, 0, 1.4, 1.2, 1.4, guard)
        m.c(0, 20.0, 0, 2.4, 2.0, 2.4, "rumbai")
        m.c(0, 25.0, 0, 0.6, 6.0, 2.2, blade, glow=lum)
        m.c(0, 28.6, 0, 0.5, 1.6, 1.0, blade, glow=lum)
    elif form == "kapak":
        m.c(0, 3.0, 0, 1.2, 14.0, 1.2, fit)
        m.c(0, -4.4, 0, 1.5, 0.8, 1.5, guard)
        m.c(0, 8.8, -0.8, 1.6, 2.0, 2.4, guard)
        m.c(0, 8.8, -3.6, 0.7, 6.0, 4.0, blade, glow=lum)
        m.c(0, 8.8, -6.0, 0.5, 7.0, 1.2, blade, glow=lum)
        m.c(0, 8.8, 1.6, 0.8, 2.0, 2.0, guard)
    elif form == "gada":
        m.c(0, 2.0, 0, 1.2, 12.0, 1.2, fit)
        m.c(0, -4.4, 0, 1.6, 0.8, 1.6, guard)
        m.c(0, 9.6, 0, 3.6, 3.6, 3.6, blade, glow=lum)
        for a in (0.0, 45.0):
            m.c(0, 9.6, 0, 5.2, 2.6, 0.8, blade, rot=("y", a), glow=lum)
            m.c(0, 9.6, 0, 0.8, 2.6, 5.2, blade, rot=("y", a), glow=lum)
        m.c(0, 12.0, 0, 1.2, 1.2, 1.2, guard)
    elif form == "kipas_besi":
        for a in (-45.0, -22.5, 0.0, 22.5, 45.0):
            m.c(0, 4.6, 0, 2.4, 8.0, 0.3, blade, rot=("z", a, (0, 0.6, 0)), glow=lum)
        m.c(0, 4.4, 0.25, 0.6, 7.6, 0.3, guard, rot=("z", 45.0, (0, 0.6, 0)))
        m.c(0, 4.4, 0.25, 0.6, 7.6, 0.3, guard, rot=("z", -45.0, (0, 0.6, 0)))
        m.c(0, 0.4, 0.3, 1.0, 1.0, 0.8, guard)
        m.c(0, -0.8, 0.3, 0.4, 2.0, 0.4, "rumbai")
    elif form == "kebut":
        # horsetail whisk: lacquered handle, metal collar, a full tail of hair strands spreading to the tip
        m.c(0, 3.0, 0, 1.0, 10.0, 1.0, fit)
        m.c(0, -2.4, 0, 1.4, 0.8, 1.4, guard)
        m.c(0, 8.4, 0, 1.4, 1.0, 1.4, guard)
        m.c(0, 9.4, 0, 1.8, 1.0, 1.8, "ekor_kuda")
        m.c(0, 15.0, 0, 2.2, 10.0, 1.4, "ekor_kuda")
        for i, (dx, dz, h) in enumerate(((-1.2, -0.5, 11.0), (1.2, -0.4, 12.0), (-0.9, 0.6, 12.5), (1.0, 0.7, 11.5),
                                         (0.0, -0.9, 13.0), (0.2, 0.9, 12.0))):
            m.c(dx, 10.0 + h / 2, dz, 0.5, h, 0.5, "ekor_kuda")
        m.c(0, -3.4, 0, 0.4, 1.4, 0.4, "rumbai")
    return m


def build_weapons():
    for form in FORMS:
        for gid in GRADES:
            weapon(form, gid)


# ================================================================================================ entity band textures (38.O.2)

ENTITY = {}


def band_atlas(name, width, painters):
    """An entity texture of len(painters) bands of 32 px; painters(x, y, rng, n) per band, tileable."""
    img = Image(width, 32 * len(painters))
    for i, pnt in enumerate(painters):
        rng = random.Random(stable(name, i))
        n = Noise(900 + i * 17 + len(name), 32)
        for y in range(32):
            for x in range(width):
                c = pnt(x if getattr(pnt, "wide", False) else x % 32, y, rng, n)
                a = c[3] if len(c) > 3 else 255
                img.set(x, i * 32 + y, (c[0], c[1], c[2], a))
    ENTITY[name] = img


def crystal(base, seed, light=False):
    b = rgb(base)

    def p(x, y, rng, n):
        f = (x + 2 * y) % 12
        c = shade(b, 0.7 + 0.5 * n.at(x, y, 8))
        if f < 2:
            c = mix(c, (255, 255, 255), 0.55)  # a facet edge
        elif f > 9:
            c = shade(c, 0.7)
        if light and n.at(x * 2, y * 2, 4) > 0.7:
            c = mix(c, (255, 240, 255), 0.5)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def crystal_glow(seed):
    def p(x, y, rng, n):
        v = n.at(x, y, 4)
        if v > 0.58:
            t = (v - 0.58) / 0.42
            return mix((150, 100, 255), (255, 230, 255), t) + (int(120 + 135 * t),)
        return (0, 0, 0, 0)
    return p


def mask_wood(seed):
    """The tomb puppet's mask, tiled every 8 px (the mask face of the model is 7 px): a red lacquer brow, black painted
    brows, gold eyes, a white face and a small red mouth."""
    def p(x, y, rng, n):
        lx, ly = x % 8, y % 8
        c = (232, 224, 208)
        if ly <= 1:
            c = (150, 30, 28)
        elif ly == 2 and lx not in (3, 4):
            c = (24, 18, 18)
        elif ly == 3 and lx in (1, 2, 5, 6):
            c = (236, 196, 80)
        elif ly == 5 and 2 <= lx <= 5:
            c = (160, 34, 30)
        elif ly == 4 and lx in (0, 7):
            c = (226, 150, 150)  # painted cheeks
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def iron_rust(seed):
    def p(x, y, rng, n):
        c = mix((54, 52, 56), (104, 100, 102), n.at(x, y, 4))
        if n.at(x + 9, y + 3, 4) > 0.66:
            c = mix(c, (130, 64, 30), 0.75)
        if y % 8 == 0:
            c = shade(c, 1.25)
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def stud_gold(seed):
    def p(x, y, rng, n):
        r = math.hypot(x % 8 - 3.5, y % 8 - 3.5)
        c = (60, 40, 20)
        if r < 3.2:
            c = mix((255, 230, 140), (150, 100, 30), r / 3.2)
        return c
    return p


def glyph_stone(seed):
    """Glyph plates: grey stone with six elemental glyph roundels (fire, water, wood, metal, earth, thunder)."""
    def p(x, y, rng, n):
        c = shade((122, 118, 112), 0.8 + 0.35 * n.at(x, y, 4))
        cx, cy = x % 16 - 7.5, y % 16 - 7.5
        r = math.hypot(cx, cy)
        if 5.5 < r < 6.5:
            c = (70, 66, 62)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def glyph_glow(seed):
    """Six element glyphs in 12 px cells at u = 12 + 12 * col, v = 12 * row (LempengGlifRenderer's glyph parts show one
    cell each on their top face); transparent elsewhere."""
    colours = [(255, 110, 50), (90, 170, 255), (110, 230, 110), (240, 240, 250), (220, 180, 90), (190, 120, 255)]

    def p(x, y, rng, n):
        if x < 12 or y >= 24:
            return (0, 0, 0, 0)
        col, row = (x - 12) // 12, y // 12
        k = col + row * 4
        if k > 5 or col > 3:
            return (0, 0, 0, 0)
        cx, cy = (x - 12) % 12 - 5.5, y % 12 - 5.5
        r = math.hypot(cx, cy)
        if k == 0:  # fire: a rising flame
            on = (abs(cx) < 0.8 and -4 < cy < 4) or (abs(abs(cx) - (cy + 4) * 0.45) < 0.7 and -4 < cy < 3)
        elif k == 1:  # water: a wave
            on = abs(cy - 1.8 * math.sin(cx * 1.0)) < 0.8 and abs(cx) < 4.6
        elif k == 2:  # wood: a sprouting tree
            on = (abs(cx) < 0.8 and r < 4.6) or (abs(abs(cx) - (cy + 3) * 0.7) < 0.7 and -3 < cy < 2)
        elif k == 3:  # metal: a ring with a centre
            on = abs(r - 3.6) < 0.8 or r < 1.0
        elif k == 4:  # earth: two strata
            on = (abs(cy + 1.5) < 0.7 and abs(cx) < 4.6) or (abs(cy - 2.0) < 0.7 and abs(cx) < 3.0)
        else:  # thunder: a zigzag bolt
            on = abs(cx - (cy * 0.7 if cy < 0 else -cy * 0.7) - (1.2 if cy > 1 else 0)) < 0.8 and r < 5.0
        if on:
            c = colours[k]
            return (c[0], c[1], c[2], 255)
        return (0, 0, 0, 0)
    p.wide = True
    return p


def rgb_tuple(c):
    return (c[0], c[1], c[2])


def cracked_stone(seed):
    def p(x, y, rng, n):
        c = shade((125, 125, 125), 0.78 + 0.35 * n.at(x, y, 4))
        if abs(math.sin(x * 0.6 + n.at(x, y, 8) * 5.0)) < 0.06 or abs(math.sin(y * 0.5 + x * 0.1 + n.at(y, x, 8) * 4)) < 0.05:
            c = (56, 56, 56)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def rat_fur(seed):
    ph = random.Random(seed)
    phase = [ph.random() * 6.28 for _ in range(32)]

    def p(x, y, rng, n):
        g = 0.5 + 0.5 * math.sin(phase[x] + y * 0.6)
        return mix((86, 96, 118), (150, 164, 186), g)
    return p


def eye_glow(seed):
    def p(x, y, rng, n):
        r = math.hypot(x % 4 - 1.5, y % 4 - 1.5)
        if r < 1.4:
            return (170, 255, 240, 255)
        return (0, 0, 0, 0)
    return p


def plain(c, noise_amt=0.15):
    b = rgb(c)

    def p(x, y, rng, n):
        return shade(b, 1.0 - noise_amt / 2 + noise_amt * n.at(x, y, 4) + rng.random() * 0.04)
    return p


def lacquer_planks(seed):
    """Red lacquered gate planks 8 px wide with dark seams, worn lighter grain showing through."""
    def p(x, y, rng, n):
        c = shade(rgb("8E1F1F"), 0.75 + 0.3 * n.at(x, y, 8))
        if x % 8 == 0:
            c = shade(c, 0.55)
        if abs(math.sin(y * 0.8 + x * 0.3)) < 0.06:
            c = mix(c, rgb("B05A3A"), 0.5)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def knocker_bronze(seed):
    """Bronze lion-head knockers (pushou): the 5 x 5 roundel faces of GerbangKotaRenderer's knocker box sit at u 1..5
    (north) and 7..11 (south), v 1..5 of the band; a maned lion with gold eyes, a broad nose and an open jaw that holds the
    ring. Elsewhere polished bronze (the ring and the roundel sides)."""
    face = [".MMM.",
            "MeBeM",
            "MBNBM",
            "MjjjM",
            ".MrM."]
    cols = {"M": (92, 64, 30), "e": (255, 214, 96), "B": (176, 132, 64), "N": (206, 160, 82), "j": (40, 24, 14),
            "r": (222, 180, 92), ".": (120, 86, 40)}

    def p(x, y, rng, n):
        base = mix((116, 84, 40), (196, 150, 74), n.at(x, y, 4))
        if 1 <= y <= 5 and (1 <= x <= 5 or 7 <= x <= 11):
            lx = (x - 1) if x <= 5 else (x - 7)
            c = cols[face[y - 1][lx]]
            return shade(c, 0.94 + rng.random() * 0.1)
        if (x + y) % 7 == 0:
            base = mix(base, (240, 210, 130), 0.4)  # a polished glint
        return shade(base, 0.95 + rng.random() * 0.07)
    p.wide = True
    return p


def crack_overlay(seed, cracks, chips):
    """Alpha-cut cracks for a gate panel (the overlay box shows the band at u 4..20, v 4..20 on its faces): {cracks}
    jagged dark splits that branch, with a lighter split-wood rim, and {chips} gouges where raw wood shows."""
    r = random.Random(seed)
    dark = {}
    for _ in range(cracks):
        x, y = r.uniform(4, 20), r.uniform(4, 20)
        ang = r.uniform(0, math.pi * 2)
        for step in range(r.randint(9, 16)):
            ang += r.uniform(-0.7, 0.7)
            x += math.cos(ang)
            y += math.sin(ang)
            dark[(int(x) % 32, int(y) % 32)] = 1
            if r.random() < 0.18:  # a short branch
                bx, by, ba = x, y, ang + r.choice((-1.2, 1.2))
                for _ in range(r.randint(2, 4)):
                    bx += math.cos(ba)
                    by += math.sin(ba)
                    dark.setdefault((int(bx) % 32, int(by) % 32), 2)
    chip = set()
    for _ in range(chips):
        cx, cy, rad = r.uniform(5, 19), r.uniform(5, 19), r.uniform(1.0, 2.2)
        for yy in range(32):
            for xx in range(32):
                if math.hypot(xx - cx, yy - cy) < rad:
                    chip.add((xx, yy))

    def p(x, y, rng, n):
        k = dark.get((x, y))
        if k == 1:
            return (34, 16, 12, 255)
        if k == 2:
            return (58, 30, 22, 255)
        if (x, y) in chip:
            return shade((168, 118, 70), 0.85 + 0.25 * n.at(x, y, 4)) + (255,)
        if dark.get((x - 1, y)) == 1 or dark.get((x, y - 1)) == 1:
            return (178, 96, 66, 255)  # the lacquer lifts along the split
        return (0, 0, 0, 0)
    return p


def cell_iron(seed):
    """Dark wrought iron of a Yamen cell door: hammered, blackened, rust in the seams, a rivet every 8 px."""
    def p(x, y, rng, n):
        c = mix((30, 30, 34), (74, 72, 76), n.at(x, y, 4))
        if n.at(x + 13, y + 5, 8) > 0.72:
            c = mix(c, (112, 56, 28), 0.6)
        if x % 8 == 4 and y % 8 == 4:
            c = (128, 126, 130)
        elif x % 8 == 5 and y % 8 == 5:
            c = (18, 18, 20)
        if y % 16 == 0:
            c = shade(c, 1.2)
        return shade(c, 0.93 + rng.random() * 0.1)
    return p


def jar_glaze(seed):
    """Brown-black temmoku glaze of the wine jars: a glossy body with drips of lighter glaze and a bare clay foot band."""
    def p(x, y, rng, n):
        c = mix((52, 30, 22), (110, 64, 40), n.at(x, y, 8))
        drip = math.sin(x * 1.3 + n.at(x, 0, 8) * 6.0)
        if y < 10 + 6 * drip:
            c = mix(c, (150, 96, 54), 0.35)
        if y > 27:
            c = (160, 120, 86)  # unglazed foot
        if (x * 3 + y * 5) % 23 == 0:
            c = mix(c, (230, 200, 160), 0.6)  # a highlight on the glaze
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def false_stone(seed):
    """The false cliff door: a stone face that matches vanilla stone at a glance (mid grey with 2 x 2 lighter and darker
    flecks), crossed by two hairline cracks that run from edge to edge (the tell a sharp-eyed tester can spot), a faint
    worn seal ring carved low on the slab, and lichen creeping up from the foot. The slab's faces sample u 4..20,
    v 4..20 (front) of the band."""
    r = random.Random(seed)
    crack = set()
    for start in ((4, r.randint(6, 10)), (r.randint(8, 14), 4)):
        x, y = float(start[0]), float(start[1])
        ang = 0.35 if start[0] == 4 else 1.25
        for _ in range(40):
            ang += r.uniform(-0.3, 0.3)
            x += math.cos(ang)
            y += math.sin(ang)
            if not (3 <= x <= 21 and 3 <= y <= 21):
                break
            crack.add((int(x), int(y)))

    def p(x, y, rng, n):
        fleck = n.at(x // 2 * 2, y // 2 * 2, 2)
        c = (126, 126, 126)
        if fleck > 0.72:
            c = (148, 148, 148)
        elif fleck < 0.25:
            c = (104, 104, 104)
        c = shade(c, 0.94 + 0.12 * n.at(x, y, 8))
        if (x, y) in crack:
            c = (64, 64, 64)
        elif (x - 1, y) in crack:
            c = (156, 156, 156)  # the lit lip of the hairline
        ring = math.hypot(x - 12.5, y - 15.5)
        if 3.4 < ring < 4.2:
            c = shade(c, 0.86)  # the worn seal ring
        if 17 <= y <= 21 and n.at(x + 5, y * 3, 4) > 0.74 - (y - 17) * 0.04:
            c = mix(c, (92, 112, 70), 0.55)  # lichen at the foot
        return shade(c, 0.97 + rng.random() * 0.05)
    return p


def roof_tiles(seed):
    """Grey clay roof tiles of the notice board's little roof: rows of 4 px wide barrel tiles with rounded ends, dark
    gaps between the rows, a lighter ridge highlight and a few moss-dark tiles."""
    def p(x, y, rng, n):
        row = y // 4
        lx = (x + (2 if row % 2 else 0)) % 4
        ly = y % 4
        c = (84, 86, 98)
        if lx == 0:
            c = (46, 46, 56)  # the channel between barrels
        elif lx == 2:
            c = (112, 114, 126)  # the barrel's crown catches light
        if ly == 3:
            c = shade(c, 0.6)  # the drip edge shadow
        tile = (x // 4 * 31 + row * 17) % 23
        if tile == 0:
            c = mix(c, (70, 84, 60), 0.4)
        return shade(c, 0.94 + 0.1 * n.at(x, y, 8) + rng.random() * 0.04)
    return p


def stable(*parts):
    h = 2166136261
    for ch in repr(parts):
        h = ((h ^ ord(ch)) * 16777619) & 0xFFFFFFFF
    return h


def emissive_of(name, keep, glints=()):
    """A glow texture with the layout of entity texture {name}: bands in {keep} copied as they are (already alpha),
    bands in {glints} keep only their brightest pixels (sparkles), every other pixel transparent."""
    src = ENTITY[name]
    img = Image(src.width, src.height)
    for y in range(src.height):
        band = y // 32
        for x in range(src.width):
            r, g, b, a = src.get(x, y)
            if band in keep:
                img.set(x, y, (r, g, b, a))
            elif band in glints and (r + g + b) > 600:
                img.set(x, y, (r, g, b, 200))
    ENTITY[name + "_pancar"] = img


def build_entity_textures():
    # penjaga_kristal: 0 deep crystal, 1 light crystal, 2 rock core, 3 emissive glow (alpha), 4 dark crystal
    band_atlas("penjaga_kristal", 128, [crystal("7A5AC8", 1), crystal("C8B4FF", 2, light=True), plain("4A4458", 0.3),
                                         crystal_glow(3), crystal("3E2A78", 4)])
    # boneka_penjaga: 0 old wood, 1 bronze rings and joints, 2 mask, 3 lacquer red cloth, 4 wooden sword
    band_atlas("boneka_penjaga", 128, [wood(PAL["kayu_tua"], 5), bronze(6), mask_wood(7), cloth("8E1F1F", 8), wood(PAL["kayu_muda"], 9)])
    # gerbang_kota: 0 red lacquer planks, 1 iron straps, 2 gold studs, 3 dark beam wood, 4 bronze lion knocker,
    # 5 light cracks (alpha), 6 heavy cracks (alpha), 7 dark cell iron (Yamen cell door)
    band_atlas("gerbang_kota", 128, [lacquer_planks(9), iron_rust(10), stud_gold(11), wood("3A2416", 12), knocker_bronze(30),
                                     crack_overlay(31, 3, 0), crack_overlay(32, 7, 3), cell_iron(33)])
    # gerobak: 0 light wood, 1 iron, 2 cargo sacks, 3 crates, 4 jar glaze, 5 rope, 6 red lantern paper
    band_atlas("gerobak", 128, [wood(PAL["kayu_muda"], 13), metal(PAL["besi"], 14), cloth("B8A070", 15), wood("8A5A30", 16),
                                jar_glaze(34), rope(35), red_paper(36)])
    # rakit: 0 bamboo, 1 rope, 2 dark bamboo pole
    band_atlas("rakit", 64, [bamboo(17), rope(18), wood("6E6A3A", 19)])
    # sangkar: 0 rusted iron, 1 padlock bronze
    band_atlas("sangkar", 64, [iron_rust(20), bronze(21)])
    # lempeng_glif: 0 stone with roundels, 1 frame stone, 2 glyph glow (alpha)
    band_atlas("lempeng_glif", 64, [glyph_stone(22), plain("6A6660", 0.3), glyph_glow(23)])
    # pintu_tebing: 0 false stone (vanilla-like, hairline cracks, worn seal, lichen), 1 darker cracked stone (seam, hold,
    # rubble)
    band_atlas("pintu_tebing", 64, [false_stone(24), cracked_stone(37)])
    # tikus_roh: 0 blue-grey fur, 1 pink skin (ears, tail, paws), 2 glowing eyes (alpha)
    band_atlas("tikus_roh", 64, [rat_fur(25), plain("D89AA0", 0.2), eye_glow(26)])
    # papan_hidup: 0 old wood frame, 1 paper, 2 roof tiles
    band_atlas("papan_hidup", 64, [wood(PAL["kayu_tua"], 27), paper(28, lines=True), roof_tiles(38)])
    emissive_of("penjaga_kristal", keep=(3,), glints=(0, 1, 4))
    emissive_of("tikus_roh", keep=(2,))
    emissive_of("lempeng_glif", keep=(2,))


# ================================================================================================ validation and output

def validate(m, textures):
    problems = []
    if len(m.els) > 40:
        problems.append("%d elements (max 40)" % len(m.els))
    for i, e in enumerate(m.els):
        for k in ("from", "to"):
            for v in e[k]:
                if v < -16.0 or v > 32.0:
                    problems.append("element %d %s %s out of -16..32" % (i, k, e[k]))
        for face in e["faces"].values():
            if any(x < 0 or x > 16 for x in face["uv"]):
                problems.append("element %d uv %s" % (i, face["uv"]))
        rot = e.get("rotation")
        if rot:
            if rot["angle"] not in (-45.0, -22.5, 22.5, 45.0):
                problems.append("element %d angle %s" % (i, rot["angle"]))
            if any(o < -16.0 or o > 32.0 for o in rot["origin"]):
                problems.append("element %d origin %s" % (i, rot["origin"]))
    for name in m.tex:
        if name not in textures:
            problems.append("texture %s missing" % name)
    return problems


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def display(m, handheld=False):
    lo, hi = m.bounds()
    size = max(hi[i] - lo[i] for i in range(3))
    s = round(min(1.0, 15.0 / max(1.0, size)) * 0.9, 3)
    cx = [round(-((lo[i] + hi[i]) / 2 - 8) * s, 2) for i in range(3)]
    ident = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    d = {
        "gui": {"rotation": [30, 225, 0], "translation": [cx[0] * 0.7 + cx[2] * 0.7, cx[1], 0], "scale": [s, s, s]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": ident, "head": ident, "on_shelf": ident,
        "thirdperson_righthand": ident, "thirdperson_lefthand": ident,
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    }
    if handheld:
        # inventory weapons: held like vanilla tools (blade up and forward)
        d["thirdperson_righthand"] = {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]}
        d["thirdperson_lefthand"] = {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]}
        d["gui"] = {"rotation": [0, 90, -45], "translation": [0, 0, 0], "scale": [s, s, s]}
        d["fixed"] = {"rotation": [0, 90, -45], "translation": [0, 0, 0], "scale": [s * 1.4, s * 1.4, s * 1.4]}
    return d


def model_json(m, tex_folder, handheld=False):
    textures = {key: "%s:%s/%s" % (NS, tex_folder, name) for name, key in m.tex.items()}
    first = next(iter(m.tex))
    textures["particle"] = "%s:%s/%s" % (NS, tex_folder, first)
    return {"textures": textures, "elements": m.els, "display": display(m, handheld)}


def sheet(images, path, factor=4, cols=8):
    names = sorted(images)
    w = max(images[n].width for n in names)
    h = max(images[n].height for n in names)
    rows = (len(names) + cols - 1) // cols
    big = Image(cols * w * factor, rows * h * factor)
    for i, n in enumerate(names):
        img = images[n]
        ox, oy = (i % cols) * w * factor, (i // cols) * h * factor
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = img.get(x, y)
                bg = 40 if ((x + y) % 2) else 60
                k = a / 255.0
                c = (r * k + bg * (1 - k), g * k + bg * (1 - k), b * k + bg * (1 - k), 255)
                for yy in range(factor):
                    for xx in range(factor):
                        big.set(ox + x * factor + xx, oy + y * factor + yy, c)
    big.save(path)


PROP_ORDER = ["mangkuk", "sumpit", "cangkir", "lepek", "teko", "sendok_sayur", "sapu", "palu", "alu", "kuas", "kitab", "sempoa",
              "tongkat", "peti", "pikulan", "joran", "cangkul", "keranjang", "ember", "dupa", "pemukul", "palu_kayu", "kipas_lipat",
              "papan_catur", "payung_kertas", "gasing", "layang_layang", "lentera_tangan", "kantong_uang", "nampan",
              "sendok_plester", "papan_kayu", "sekop", "bibit", "tombak", "kain", "teropong", "labu_pil", "pahat", "batu_mentah"]


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    build_materials()
    build_props()
    build_weapon_textures()
    build_weapons()
    build_entity_textures()
    errors = 0
    used = set()
    used_w = set()
    for (folder, mid), m in sorted(MODELS.items()):
        textures = TEXTURES if folder == "tempat" else {**WEAPON_TEX, **TEXTURES}
        problems = validate(m, textures)
        if problems:
            errors += 1
            print("MODEL %s/%s:" % (folder, mid))
            for p in problems:
                print("   ", p)
        if folder == "tempat":
            used.update(m.tex)
            write_json(os.path.join(PROP_MODEL_DIR, mid + ".json"), model_json(m, "item/fx/tempat"))
            write_json(os.path.join(PROP_ITEM_DIR, mid + ".json"),
                       {"model": {"type": "minecraft:model", "model": "%s:item/fx/tempat/%s" % (NS, mid)}})
        else:
            # weapon models reference both their own folder and the prop materials (fittings)
            tj = {}
            for name, key in m.tex.items():
                tj[key] = "%s:item/%s/%s" % (NS, "senjata" if name in WEAPON_TEX else "fx/tempat", name)
                if name in WEAPON_TEX:
                    used_w.add(name)
                else:
                    used.add(name)
            tj["particle"] = tj[next(iter(m.tex.values()))]
            write_json(os.path.join(WEAPON_MODEL_DIR, mid + ".json"), {"textures": tj, "elements": m.els, "display": display(m, True)})
            write_json(os.path.join(WEAPON_ITEM_DIR, mid + ".json"),
                       {"model": {"type": "minecraft:model", "model": "%s:item/senjata/%s" % (NS, mid)}})
    for name in PROP_ORDER:
        if ("tempat", name) not in MODELS:
            errors += 1
            print("missing prop model", name)
    os.makedirs(PROP_TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(PROP_TEX_DIR, name + ".png"))
    os.makedirs(WEAPON_TEX_DIR, exist_ok=True)
    for name in sorted(used_w):
        WEAPON_TEX[name].save(os.path.join(WEAPON_TEX_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused prop textures (not written):", ", ".join(unused))
    os.makedirs(ENTITY_TEX_DIR, exist_ok=True)
    for name, img in sorted(ENTITY.items()):
        img.save(os.path.join(ENTITY_TEX_DIR, name + ".png"))
    with open(IDS_FILE, "w", encoding="utf-8", newline="\n") as f:
        for i, name in enumerate(PROP_ORDER, 1):
            f.write("%d %s\n" % (i, name))
        for (folder, mid) in sorted(MODELS):
            if folder == "tempat" and mid not in PROP_ORDER:
                f.write("- %s\n" % mid)
    props = [k for k in MODELS if k[0] == "tempat"]
    weapons = [k for k in MODELS if k[0] == "senjata"]
    print("props %d, weapons %d, prop textures %d, weapon textures %d, entity textures %d, errors %d"
          % (len(props), len(weapons), len(used), len(used_w), len(ENTITY), errors))
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        sheet({n: TEXTURES[n] for n in used}, os.path.join(preview_dir, "tempat_materials.png"))
        sheet(WEAPON_TEX, os.path.join(preview_dir, "tempat_weapon_tex.png"))
        for name, img in ENTITY.items():
            sheet({name: img}, os.path.join(preview_dir, "entity_" + name + ".png"), factor=2, cols=1)
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
