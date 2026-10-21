"""Generates the textures and item assets of module K, Kota & NPC (DESIGN 34.5.1-34.5.4, 34.6 K).

World layers (client/dunia/kota/KotaFx, drawn with entityTranslucentEmissive, which cuts texture alpha below 0.1 BEFORE
the vertex colour: every pixel that should show keeps alpha >= 26, fades go into the vertex alpha; the layers are white
or pale and tinted per vertex):
textures/dunia/kota/lentera_glow.png     32x32  a lantern's glow sprite: a warm core, a soft halo, a faint paper ring.
textures/dunia/kota/cahaya_panggung.png  16x64  an auction stage light shaft; u across (soft edges), v = 1 the floor,
                                                v = 0 the top (fades out), slow dust motes in the beam.
textures/dunia/kota/awan_kota.png        64x64  a cloud puff under the Immortal City (tileable noise, round falloff,
                                                a brighter crown and a grey belly).
textures/dunia/kota/jejak_pedang.png     16x64  a rival's sword-descent trail; u across (a hot core line), v = 0 the
                                                head (brightest), v = 1 the tail (thin and faded).

Item icons (16x16, the item atlas):
textures/item/token_pasar_gelap.png      a black jade plaque with gold caps, a red cord and a violet crescent glyph.
textures/item/teh_embun_pagi.png         a white porcelain cup of pale green tea with a dewdrop glint.
textures/item/teh_awan_ungu.png          a violet-glazed cup of lavender tea under a purple wisp.
textures/item/teh_penenang_hati.png      a brown Yixing clay cup of amber tea with a calm, low steam.
textures/item/kantong_saingan.png        a crimson rival pouch tied with a gold cord, a black crest.
textures/item/lencana_pengembara.png     a bronze wanderer's medallion with a bamboo hat emblem on blue ribbons.
textures/item/persik_abadi.png           a pink-gold peach of immortality with two leaves and a golden glint.
textures/item/catatan_pengembara.png     an open travel scroll: wooden rollers, ink columns, a red seal.

Also writes, for every item above, its model (models/item/<id>.json, parent item/generated) and its item definition
(items/<id>.json).

Run with: python tools/gen_dunia_kota_textures.py [preview_dir]   (8x previews of every texture into preview_dir)
Deterministic: the same files on every run.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
ROOT = os.path.join(ASSETS, "textures")
WORLD = os.path.join(ROOT, "dunia", "kota")
ITEM = os.path.join(ROOT, "item")
MIN_A = 26  # the 0.1 alpha cutoff of entityTranslucentEmissive

ITEMS = ["token_pasar_gelap", "teh_embun_pagi", "teh_awan_ungu", "teh_penenang_hati", "kantong_saingan",
         "lencana_pengembara", "persik_abadi", "catatan_pengembara"]

WRITTEN = []


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


class Noise:
    """Tileable value noise on a period grid."""

    def __init__(self, seed, period):
        rnd = random.Random(seed)
        self.period = period
        self.grid = [[rnd.random() for _ in range(period)] for _ in range(period)]

    def at(self, x, y):
        p = self.period
        x0 = int(math.floor(x)) % p
        y0 = int(math.floor(y)) % p
        fx = smooth(x - math.floor(x))
        fy = smooth(y - math.floor(y))
        x1 = (x0 + 1) % p
        y1 = (y0 + 1) % p
        a = self.grid[y0][x0] * (1 - fx) + self.grid[y0][x1] * fx
        b = self.grid[y1][x0] * (1 - fx) + self.grid[y1][x1] * fx
        return a * (1 - fy) + b * fy

    def fbm(self, x, y, octaves=4):
        total = 0.0
        amp = 0.5
        freq = 1.0
        norm = 0.0
        for _ in range(octaves):
            total += amp * self.at(x * freq, y * freq)
            norm += amp
            amp *= 0.5
            freq *= 2.0
        return total / norm


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name)
    img.save(path)
    WRITTEN.append((path, img))


def put(img, x, y, rgb, a):
    """A world-layer pixel: alpha a (0..1); under the cutoff it is left fully transparent."""
    byte = int(round(clamp(a) * 255))
    if byte < MIN_A:
        img.set(x, y, (0, 0, 0, 0))
    else:
        img.set(x, y, (rgb[0], rgb[1], rgb[2], byte))


# ================================================================ world layers

def lentera_glow():
    s = 32
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            if r >= 1.0:
                continue
            core = math.exp(-(r / 0.18) ** 2)
            halo = (1.0 - r) ** 2.2
            ring = math.exp(-((r - 0.42) / 0.07) ** 2) * 0.18  # the paper lantern's faint rim
            a = clamp(0.35 * halo + 0.75 * core + ring)
            col = mix((255, 226, 180), (255, 255, 245), clamp(core * 1.3))
            put(img, x, y, tuple(int(c) for c in col), a)
    save(img, WORLD, "lentera_glow.png")


def cahaya_panggung():
    w, h = 16, 64
    img = Image(w, h)
    rnd = random.Random(53)
    motes = [(rnd.uniform(0.2, 0.8), rnd.uniform(0.05, 0.95), rnd.uniform(0.5, 1.0)) for _ in range(14)]
    for y in range(h):
        v = (y + 0.5) / h  # 0 top .. 1 floor
        along = smooth(v) * 0.85 + 0.15 * v
        for x in range(w):
            u = (x + 0.5) / w
            across = abs(u - 0.5) * 2.0
            edge = math.exp(-(across / 0.62) ** 2.4)
            core = math.exp(-(across / 0.22) ** 2) * 0.35
            a = (edge * 0.7 + core) * along
            for mu, mv, mb in motes:
                d = math.hypot((u - mu) * 4.0, (v - mv) * 16.0)
                if d < 1.0:
                    a += (1.0 - d) * 0.35 * mb * (0.4 + 0.6 * v)
            col = mix((255, 236, 200), (255, 252, 240), clamp(core * 2.5))
            put(img, x, y, tuple(int(c) for c in col), a)
    save(img, WORLD, "cahaya_panggung.png")


def awan_kota():
    s = 64
    img = Image(s, s)
    noise = Noise(66, 8)
    lobes = [(0.0, 0.0, 0.62), (-0.32, 0.18, 0.42), (0.34, 0.12, 0.44), (0.05, -0.3, 0.4), (-0.12, 0.38, 0.34)]
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            shape = 0.0
            for lx, ly, lr in lobes:
                d = math.hypot(dx - lx, dy - ly) / lr
                shape = max(shape, 1.0 - d)
            n = noise.fbm(x / 8.0, y / 8.0, 4)
            density = clamp(shape * 1.6 + (n - 0.5) * 0.9)
            if density <= 0.0:
                continue
            a = smooth(density) * 0.92
            light = clamp(0.55 + 0.45 * (1.0 - (dy + 1.0) / 2.0) + (n - 0.5) * 0.5)
            col = mix((196, 200, 214), (255, 255, 255), light)
            put(img, x, y, tuple(int(c) for c in col), a)
    save(img, WORLD, "awan_kota.png")


def jejak_pedang():
    w, h = 16, 64
    img = Image(w, h)
    noise = Noise(61, 8)
    for y in range(h):
        v = (y + 0.5) / h  # 0 head .. 1 tail
        body = (1.0 - v) ** 1.4
        width = 0.25 + 0.75 * (1.0 - v)
        for x in range(w):
            across = abs((x + 0.5) / w - 0.5) * 2.0
            core = math.exp(-((across / max(0.06, width * 0.22)) ** 2))
            halo = math.exp(-((across / max(0.12, width * 0.8)) ** 2) * 2.0)
            flicker = 0.82 + 0.18 * noise.at(x / 3.0, y / 5.0)
            a = (0.5 * halo + 0.6 * core) * body * flicker
            col = mix((210, 230, 255), (255, 255, 255), clamp(core))
            put(img, x, y, tuple(int(c) for c in col), a)
    save(img, WORLD, "jejak_pedang.png")


# ================================================================ item icons

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


def token_pasar_gelap():
    pal = {
        "o": (14, 12, 20, 255), "K": (30, 30, 44, 255), "k": (46, 44, 66, 255), "h": (92, 90, 124, 255),
        "P": (190, 110, 250, 255), "p": (120, 70, 170, 255), "G": (226, 184, 84, 255), "g": (150, 108, 40, 255),
        "R": (196, 44, 40, 255), "r": (120, 22, 24, 255),
    }
    rows = [
        "......rRRr......",
        ".....r....r.....",
        ".....R....R.....",
        "....ogGGGGgo....",
        "...oKhhkkkkKo...",
        "...oKhkkkkkKo...",
        "...oKkkPPkkKo...",
        "...oKkPpkkkKo...",
        "...oKkPkkPkKo...",
        "...oKkPpkkkKo...",
        "...oKkkPPkkKo...",
        "...oKkkkkkpKo...",
        "...oKKkkkkKKo...",
        "....ogGGGGgo....",
        "......rRRr......",
        ".......Rr.......",
    ]
    save(sprite(rows, pal), ITEM, "token_pasar_gelap.png")


CUP = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..oooooooooooo..",
    ".oWTTTTTTTTTTWo.",
    ".oBtTTTTTTTTtBo.",
    ".oBBWBBBBBBBBbo.",
    "..oBWBBBBBBBbo..",
    "..oBBBBBBBBBbo..",
    "...oBBBBBBBbo...",
    "....obbbbbbo....",
    "..ooSSSSSSSSoo..",
    ".oSSSSSSSSSSSso.",
    "..osssssssssso..",
]


def tea(name, body, shade, rim, tea_top, tea_shade, saucer, saucer_shade, steam, steam_rows, extra):
    pal = {
        "o": (34, 28, 30, 255), "B": body, "b": shade, "W": rim, "T": tea_top, "t": tea_shade,
        "S": saucer, "s": saucer_shade, "M": steam, "m": (steam[0], steam[1], steam[2], steam[3] // 2),
    }
    pal.update({k: v for k, v in extra.items() if len(k) == 1 and isinstance(v, tuple)})
    rows = list(CUP)
    for i, row in enumerate(steam_rows):
        rows[i] = row
    for (x, y), ch in extra.get("pixels", {}).items():
        r = rows[y]
        rows[y] = r[:x] + ch + r[x + 1:]
    save(sprite(rows, pal), ITEM, name + ".png")


def teas():
    tea("teh_embun_pagi",
        body=(236, 240, 238, 255), shade=(176, 188, 186, 255), rim=(255, 255, 255, 255),
        tea_top=(186, 222, 150, 255), tea_shade=(140, 186, 110, 255),
        saucer=(220, 228, 226, 255), saucer_shade=(150, 164, 162, 255), steam=(240, 250, 255, 150),
        steam_rows=["................",
                    ".......M........",
                    "......M..m......",
                    ".......M.m......",
                    "......m..M......"],
        extra={"D": (255, 255, 255, 255), "d": (200, 240, 255, 255), "L": (110, 170, 200, 255),
               "pixels": {(9, 6): "D", (10, 7): "d", (6, 9): "L", (7, 10): "L"}})
    tea("teh_awan_ungu",
        body=(150, 96, 196, 255), shade=(92, 54, 132, 255), rim=(214, 176, 246, 255),
        tea_top=(206, 170, 236, 255), tea_shade=(156, 116, 196, 255),
        saucer=(124, 80, 170, 255), saucer_shade=(74, 42, 108, 255), steam=(196, 130, 255, 190),
        steam_rows=["....mMMm........",
                    "...M....Mm......",
                    "....mM....M.....",
                    "......MMm.M.....",
                    ".........Mm....."],
        extra={"G": (240, 206, 110, 255), "pixels": {(x, 8): "G" for x in range(3, 13)}})
    tea("teh_penenang_hati",
        body=(140, 82, 52, 255), shade=(92, 50, 30, 255), rim=(190, 128, 88, 255),
        tea_top=(222, 160, 70, 255), tea_shade=(176, 110, 40, 255),
        saucer=(118, 70, 44, 255), saucer_shade=(72, 40, 24, 255), steam=(255, 220, 210, 120),
        steam_rows=["................",
                    "................",
                    "................",
                    ".....mMMMm......",
                    "....m.....mM...."],
        extra={"H": (184, 120, 84, 255), "pixels": {(4, 8): "H", (4, 9): "H", (4, 10): "H", (5, 11): "H"}})


def kantong_saingan():
    pal = {
        "o": (30, 10, 12, 255), "R": (168, 30, 36, 255), "r": (110, 18, 24, 255), "h": (226, 90, 90, 255),
        "G": (232, 192, 90, 255), "g": (160, 116, 44, 255), "E": (36, 12, 16, 255),
    }
    rows = [
        "................",
        "......oo.oo.....",
        ".....oGGoGGo....",
        "......oGGGo.....",
        ".....oRRgRRo....",
        "......oRgRo.....",
        "....ooRRRRRoo...",
        "...oRRRRRRRRRo..",
        "..oRRhRRRRRRrro.",
        "..oRhRRREERRRro.",
        "..oRRRREEERRrro.",
        "..oRRRRREERRrro.",
        "..orRRRRRRRRrro.",
        "...orrRRRRRrro..",
        "....ooorrrooo...",
        "................",
    ]
    save(sprite(rows, pal), ITEM, "kantong_saingan.png")


def lencana_pengembara():
    pal = {
        "o": (54, 30, 12, 255), "C": (204, 140, 64, 255), "c": (252, 214, 150, 255), "b": (130, 78, 30, 255),
        "F": (82, 46, 20, 255), "G": (250, 212, 120, 255), "d": (150, 96, 40, 255),
        "L": (56, 100, 190, 255), "l": (30, 54, 112, 255),
    }
    # a bronze medallion, its recessed field carrying the wanderer's bamboo hat (douli) in gold, on blue ribbons
    rows = [
        "................",
        ".....oooooo.....",
        "...ooCCCCCCoo...",
        "..oCcCFFFFCCbo..",
        "..oCcFFFFFFCbo..",
        ".oCcFFFGGFFFCbo.",
        ".oCFFFGGGGFFCbo.",
        ".oCFFGGGGGGFCbo.",
        ".oCFGGGGGGGGCbo.",
        ".oCFFddddddFCbo.",
        "..oCFFFFFFFCbo..",
        "..oCCFFFFFCCbo..",
        "...oobCCCCbboo..",
        "....LoooooooL...",
        "...LlL.....LlL..",
        "...Ll.......lL..",
    ]
    save(sprite(rows, pal), ITEM, "lencana_pengembara.png")


def persik_abadi():
    img = Image(16, 16)
    cx, cy = 7.6, 9.4
    for y in range(16):
        for x in range(16):
            px = x + 0.5
            py = y + 0.5
            # a peach: a round body with a pointed tip at the top right and a cleft
            dx = px - cx
            dy = py - cy
            d = math.hypot(dx * 1.0, dy * 1.05)
            tip = max(0.0, 1.0 - math.hypot(px - 10.2, py - 4.6) / 2.2) * 1.4
            if d > 5.6 + tip:
                continue
            edge = d > 4.7 + tip
            t = clamp((dy + 5.5) / 11.0)
            side = clamp((dx + 5.5) / 11.0)
            col = mix((255, 196, 120), (252, 120, 150), clamp(0.25 + 0.6 * (1.0 - t) + 0.25 * side))
            light = math.hypot(px - 5.6, py - 7.4)
            if light < 1.6:
                col = mix(col, (255, 236, 220), 0.7)
            elif light < 2.6:
                col = mix(col, (255, 220, 210), 0.3)
            if edge:
                col = mix(col, (150, 50, 70), 0.6)
            img.set(x, y, (int(col[0]), int(col[1]), int(col[2]), 255))
    # the cleft from the tip down the side
    for x, y in [(9, 6), (9, 7), (8, 8), (8, 9), (8, 10)]:
        img.set(x, y, (196, 66, 96, 255))
    # leaves and stem
    stem = (110, 70, 34, 255)
    leaf = (92, 170, 70, 255)
    leaf_dark = (48, 110, 44, 255)
    for x, y, c in [(9, 2, stem), (9, 3, stem), (10, 1, leaf_dark), (11, 1, leaf), (12, 1, leaf), (11, 2, leaf), (12, 2, leaf_dark),
                    (13, 2, leaf), (6, 2, leaf), (7, 2, leaf), (7, 3, leaf_dark), (5, 3, leaf_dark), (6, 3, leaf)]:
        img.set(x, y, c)
    # a golden glint of immortality
    img.set(3, 6, (255, 240, 150, 255))
    img.set(13, 11, (255, 226, 120, 255))
    img.set(12, 12, (255, 248, 200, 255))
    save(img, ITEM, "persik_abadi.png")


def catatan_pengembara():
    pal = {
        "o": (40, 24, 14, 255), "d": (122, 74, 38, 255), "D": (170, 110, 58, 255),
        "P": (238, 224, 186, 255), "p": (206, 188, 146, 255), "I": (36, 30, 34, 255), "i": (96, 86, 86, 255),
        "S": (196, 40, 36, 255), "s": (140, 24, 22, 255),
    }
    rows = [".oo..........oo."]
    rows.insert(0, "................")
    rows.insert(0, "................")
    rows.append("odDoooooooooodDo")
    paper = []
    for y in range(4, 12):
        row = list("PPPPPPPPPp")
        paper.append(row)
    # ink columns, read right to left (x index inside the paper 0..9)
    cols = {8: (0, 7), 6: (0, 5), 4: (1, 7), 2: (0, 3)}
    for cx, (y0, y1) in cols.items():
        for yy in range(y0, y1):
            paper[yy][cx] = "I" if (yy + cx) % 3 else "i"
    # the red seal at the bottom left of the text
    for (sx, sy) in [(1, 5), (2, 5), (1, 6), (2, 6)]:
        paper[sy][sx] = "S"
    paper[6][2] = "s"
    for row in paper:
        rows.append("odD" + "".join(row) + "dDo")
    rows.append("odDoooooooooodDo")
    rows.append(".oo..........oo.")
    rows.append("................")
    rows.append("................")
    save(sprite(rows, pal), ITEM, "catatan_pengembara.png")


# ================================================================ models and item definitions

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def item_assets():
    for name in ITEMS:
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}})
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}})


# ================================================================ preview

def preview(folder):
    os.makedirs(folder, exist_ok=True)
    for path, img in WRITTEN:
        scale = 8 if max(img.width, img.height) <= 32 else 4
        big = Image(img.width * scale, img.height * scale)
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = img.get(x, y)
                # over a dark checker so glows and edges read
                for yy in range(scale):
                    for xx in range(scale):
                        bx = x * scale + xx
                        by = y * scale + yy
                        base = 44 if ((bx // 8 + by // 8) % 2) else 60
                        t = a / 255.0
                        big.set(bx, by, (int(base + (r - base) * t), int(base + (g - base) * t), int(base + (b - base) * t), 255))
        big.save(os.path.join(folder, os.path.basename(path)))


def main():
    lentera_glow()
    cahaya_panggung()
    awan_kota()
    jejak_pedang()
    token_pasar_gelap()
    teas()
    kantong_saingan()
    lencana_pengembara()
    persik_abadi()
    catatan_pengembara()
    item_assets()
    for path, img in WRITTEN:
        print("wrote %s (%dx%d)" % (os.path.relpath(path, os.path.join(os.path.dirname(__file__), "..")), img.width, img.height))
    print("models and item definitions: %d" % len(ITEMS))
    if len(sys.argv) > 1:
        preview(sys.argv[1])


if __name__ == "__main__":
    main()
