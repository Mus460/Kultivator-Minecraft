"""Item textures, models and item definitions of 38EK, the money loop (DESIGN 38.M).

python tools/gen_tempat_ekonomi.py [--preview <png>]

Writes (32 x 32 textures, hand-shaded pixel art drawn from primitives, no external files):
  textures/item/ekonomi/<name>.png
  models/item/<registry path>.json      (parent minecraft:item/generated)
  items/<registry path>.json            (26.x item definitions)
for the four capital dishes (hidangan/*), the four guild robes (jubah_kultivator_*), the Surat Wesel, the four raw gambling
stones (batu_mentah/*) and the Grand Auction's Lot Misteri. The city weapons are 38MD's (gen_tempat_models.py).
--preview writes an 8x enlarged contact sheet of every texture to check by eye.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator")
TEX = os.path.join(ROOT, "textures", "item", "ekonomi")
N = 32


# ------------------------------------------------------------------ drawing helpers

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + ((a[3] if len(a) > 3 else 255),)


def shade(c, f):
    return tuple(int(max(0, min(255, c[i] * f))) for i in range(3)) + ((c[3] if len(c) > 3 else 255),)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def ellipse(img, cx, cy, rx, ry, colour_fn):
    """Fills an ellipse; colour_fn(nx, ny) gets the normalised position (-1..1) and returns a colour or None."""
    for y in range(N):
        for x in range(N):
            nx = (x + 0.5 - cx) / rx
            ny = (y + 0.5 - cy) / ry
            if nx * nx + ny * ny <= 1.0:
                c = colour_fn(nx, ny)
                if c is not None:
                    img.blend(x, y, c)


def rect(img, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.blend(x, y, colour)


def line(img, x0, y0, x1, y1, colour):
    steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(steps + 1):
        t = i / max(1, steps)
        img.blend(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), colour)


def outline(img, colour):
    """A one-pixel dark rim around every opaque area (the readable item silhouette)."""
    solid = [[img.get(x, y)[3] > 40 for x in range(N)] for y in range(N)]
    for y in range(N):
        for x in range(N):
            if solid[y][x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < N and 0 <= yy < N and solid[yy][xx]:
                    img.set(x, y, colour)
                    break


def under(img, draw):
    """Runs {@code draw} but keeps only what lands on transparent pixels (a ground shadow behind the outlined item)."""
    before = [img.get(x, y) for y in range(N) for x in range(N)]
    draw()
    for y in range(N):
        for x in range(N):
            if before[y * N + x][3] > 0:
                img.set(x, y, before[y * N + x])


def lit(nx, ny, base, light=(-0.55, -0.65), amount=0.38):
    """Simple sphere lighting from the upper left."""
    nz = math.sqrt(max(0.0, 1.0 - nx * nx - ny * ny))
    d = -(nx * light[0] + ny * light[1]) * 0.8 + nz * 0.55
    return shade(base, 0.72 + d * amount)


def steam(img, xs, top, colour, rnd):
    for x0 in xs:
        x = float(x0)
        for y in range(top + 7, top - 1, -1):
            x += rnd.uniform(-0.7, 0.7)
            a = int(colour[3] * (y - top + 1) / 8.0)
            img.blend(int(round(x)), y, colour[:3] + (a,))


# ------------------------------------------------------------------ dishes

def bakpao():
    img = Image(N, N)
    rnd = random.Random(1)
    dough = (248, 242, 228, 255)

    def bun(nx, ny):
        c = lit(nx, ny, dough, amount=0.30)
        # pleats radiating from the top knot
        ang = math.atan2(ny, nx)
        r = math.sqrt(nx * nx + ny * ny)
        if ny < 0.1 and 0.25 < r < 0.95 and (int((ang + 3.2) * 4.6) % 2 == 0):
            c = shade(c, 0.92)
        return c
    ellipse(img, 16, 18, 11.5, 8.5, bun)
    # bottom flat edge
    for x in range(6, 27):
        img.blend(x, 25, shade(dough, 0.78))
    # top knot and red good-luck dot
    ellipse(img, 16, 11.5, 2.4, 1.6, lambda nx, ny: lit(nx, ny, (236, 226, 206, 255)))
    img.set(16, 15, (214, 38, 38, 255))
    img.set(17, 15, (190, 28, 28, 255))
    img.set(16, 16, (176, 22, 22, 255))
    # qi glow specks
    for _ in range(6):
        x = rnd.randint(7, 25)
        y = rnd.randint(13, 22)
        img.blend(x, y, (150, 230, 255, 70))
    outline(img, (60, 48, 40, 255))
    under(img, lambda: ellipse(img, 16, 27, 12, 2.4, lambda nx, ny: (30, 24, 24, 80)))
    steam(img, (11, 16, 21), 2, (240, 250, 255, 150), rnd)
    return img


def bowl(img, cx, top, rim_rx, depth, body, rim, pattern=None):
    """A ceramic bowl seen from the front-top: rim ellipse and a round body."""
    # body
    for y in range(top, top + depth + 1):
        t = (y - top) / max(1, depth)
        half = rim_rx * math.sqrt(max(0.0, 1.0 - (t * 0.95) ** 2))
        for x in range(int(cx - half), int(cx + half) + 1):
            nx = (x + 0.5 - cx) / max(1.0, half)
            c = shade(body, 1.08 - 0.30 * (nx + 1) / 2 - 0.25 * t)
            if pattern is not None:
                c = pattern(x, y, c, nx, t)
            img.blend(x, y, c)
    # foot
    rect(img, int(cx - 3), top + depth + 1, int(cx + 3), top + depth + 1, shade(body, 0.6))
    # rim ring
    ellipse(img, cx, top, rim_rx, 2.6, lambda nx, ny: shade(rim, 1.05 - 0.2 * (nx + 1) / 2))


def sup():
    img = Image(N, N)
    rnd = random.Random(2)

    def porcelain(x, y, c, nx, t):
        # blue-and-white band of clouds
        if 0.25 < t < 0.55 and (x + y) % 5 in (0, 1):
            return mix(c, (40, 80, 170, 255), 0.75)
        if 0.6 < t < 0.68:
            return mix(c, (50, 90, 180, 255), 0.6)
        return c
    bowl(img, 16, 15, 12, 11, (238, 240, 244, 255), (250, 252, 255, 255), porcelain)
    # golden broth surface
    ellipse(img, 16, 15, 10.4, 1.9, lambda nx, ny: lit(nx, ny, (226, 168, 66, 255), amount=0.25))
    # ginseng root rising from the broth
    root = (214, 176, 120, 255)
    for i, (x, y) in enumerate([(13, 14), (14, 13), (14, 12), (15, 11), (15, 10), (16, 9), (16, 8), (17, 7)]):
        img.set(x, y, shade(root, 1.0 - i * 0.03))
        img.set(x + 1, y, shade(root, 0.8))
    for x, y in [(12, 15), (11, 15), (17, 8), (18, 7), (15, 12), (16, 12)]:
        img.set(x, y, shade(root, 0.9))
    # two green leaves on top
    for x, y in [(17, 5), (18, 5), (18, 4), (16, 6), (19, 6)]:
        img.set(x, y, (88, 168, 70, 255))
    img.set(19, 5, (62, 130, 52, 255))
    # red goji berries
    img.set(19, 15, (200, 40, 30, 255))
    img.set(11, 16, (200, 40, 30, 255))
    outline(img, (52, 52, 64, 255))
    under(img, lambda: ellipse(img, 16, 29, 11, 2.0, lambda nx, ny: (30, 24, 24, 80)))
    steam(img, (9, 22), 2, (240, 250, 255, 140), rnd)
    return img


def mi():
    img = Image(N, N)
    rnd = random.Random(3)

    def red_lacquer(x, y, c, nx, t):
        if 0.45 < t < 0.55:
            return mix(c, (240, 196, 60, 255), 0.8)
        return c
    bowl(img, 16, 15, 12, 11, (188, 34, 30, 255), (214, 52, 40, 255), red_lacquer)
    # broth
    ellipse(img, 16, 15, 10.4, 1.9, lambda nx, ny: (236, 200, 140, 255))
    # noodles: long wavy strands piled high (longevity: never cut)
    noodle = (250, 232, 170, 255)
    for k in range(5):
        y0 = 13 - k * 0.6
        for x in range(7, 26):
            y = y0 + math.sin((x + k * 2) * 0.9) * 0.8 - (1.2 if 11 < x < 21 else 0.0)
            img.blend(x, int(round(y)), shade(noodle, 0.85 + 0.03 * k))
    # half egg and green onion
    ellipse(img, 21, 12, 2.2, 1.6, lambda nx, ny: (250, 250, 240, 255))
    ellipse(img, 21, 12, 1.1, 0.9, lambda nx, ny: (250, 176, 30, 255))
    for x, y in [(11, 11), (12, 12), (14, 11), (10, 13)]:
        img.set(x, y, (90, 180, 60, 255))
    # chopsticks resting across the rim
    line(img, 4, 9, 25, 15, (120, 70, 40, 255))
    line(img, 5, 8, 26, 14, (150, 92, 52, 255))
    outline(img, (60, 28, 24, 255))
    under(img, lambda: ellipse(img, 16, 29, 11, 2.0, lambda nx, ny: (30, 24, 24, 80)))
    steam(img, (10, 17), 1, (240, 250, 255, 130), rnd)
    return img


def arak():
    img = Image(N, N)
    rnd = random.Random(4)
    glaze = (226, 236, 220, 255)
    # gourd: lower and upper bulbs (hulu)
    ellipse(img, 16, 21, 8, 7.5, lambda nx, ny: lit(nx, ny, glaze, amount=0.32))
    ellipse(img, 16, 11.5, 5, 4.6, lambda nx, ny: lit(nx, ny, glaze, amount=0.32))
    rect(img, 14, 14, 18, 15, shade(glaze, 0.9))
    # pink glaze wash at the bottom
    ellipse(img, 16, 24, 7.2, 3.4, lambda nx, ny: (236, 150, 170, 70))
    # red cord at the waist
    for x in range(12, 21):
        img.set(x, 15, (190, 36, 40, 255))
    img.set(19, 16, (190, 36, 40, 255))
    img.set(20, 17, (170, 26, 32, 255))
    # neck, red cloth stopper tied with string
    rect(img, 15, 5, 17, 7, shade(glaze, 0.85))
    ellipse(img, 16, 4, 3.2, 2.0, lambda nx, ny: lit(nx, ny, (206, 44, 44, 255)))
    img.set(13, 5, (180, 30, 30, 255))
    img.set(19, 5, (180, 30, 30, 255))
    # peach blossoms painted on the body
    for bx, by in [(12, 20), (19, 23), (17, 18)]:
        for dx, dy in [(0, -1), (1, 0), (0, 1), (-1, 0)]:
            img.set(bx + dx, by + dy, (244, 150, 180, 255))
        img.set(bx, by, (250, 220, 120, 255))
    line(img, 10, 23, 14, 19, (110, 70, 50, 255))
    # falling petal
    img.set(25, 9, (246, 170, 196, 255))
    img.set(26, 10, (236, 140, 170, 255))
    outline(img, (58, 64, 54, 255))
    under(img, lambda: ellipse(img, 16, 29.5, 9, 1.6, lambda nx, ny: (30, 24, 24, 80)))
    return img


# ------------------------------------------------------------------ guild robes

def jubah(main, trim, accent):
    img = Image(N, N)
    # folded robe: body with crossed collar (jiaoling), wide sleeves folded behind
    for y in range(6, 29):
        for x in range(4, 28):
            t = (y - 6) / 22.0
            half = 7.0 + t * 4.0
            in_body = abs(x + 0.5 - 16) <= half
            sleeve = 8 <= y <= 17 and (x < 9 or x > 22) and abs(x + 0.5 - 16) <= 12.5
            if in_body or sleeve:
                f = 1.0 - 0.22 * ((x - 4) / 23.0) - (0.12 if sleeve and not in_body else 0.0)
                c = shade(main, f)
                # cloth folds
                if (x * 3 + y) % 11 == 0:
                    c = shade(c, 0.9)
                img.set(x, y, c)
    # sleeve cuffs
    for y in range(13, 18):
        img.set(4, y, trim)
        img.set(27, y, trim)
        img.set(5, y, shade(trim, 0.85))
        img.set(26, y, shade(trim, 0.85))
    # crossed collar: left over right
    for i in range(10):
        img.set(11 + i, 6 + i, trim)
        img.set(11 + i, 7 + i, shade(trim, 0.85))
        img.set(21 - i, 6 + i // 2, trim)
    # sash
    for x in range(9, 24):
        img.set(x, 19, accent)
        img.set(x, 20, shade(accent, 0.8))
    img.set(17, 21, accent)
    img.set(18, 22, accent)
    img.set(16, 22, shade(accent, 0.8))
    # guild emblem roundel on the chest
    ellipse(img, 19.5, 12.5, 1.8, 1.8, lambda nx, ny: trim)
    img.set(19, 12, accent)
    # hem band
    for x in range(5, 28):
        if img.get(x, 28)[3] > 0:
            img.set(x, 28, trim)
    outline(img, shade(main, 0.35))
    return img


# ------------------------------------------------------------------ bank note

def wesel():
    img = Image(N, N)
    paper = (236, 222, 184, 255)
    for y in range(4, 29):
        for x in range(7, 25):
            c = shade(paper, 1.0 - 0.10 * ((y - 4) / 24.0) - (0.05 if (x + y * 7) % 13 == 0 else 0.0))
            img.set(x, y, c)
    # folded corner
    for i in range(4):
        for j in range(4 - i):
            img.set(24 - j, 4 + i, (0, 0, 0, 0))
    line(img, 21, 4, 24, 7, shade(paper, 0.72))
    # border frame
    for x in range(8, 24):
        img.set(x, 5, (120, 70, 40, 255))
        img.set(x, 27, (120, 70, 40, 255))
    for y in range(5, 28):
        img.set(8, y, (120, 70, 40, 255))
        img.set(23, y, (120, 70, 40, 255))
    # vertical lines of writing (brush strokes)
    rnd = random.Random(9)
    for col in (11, 14, 17, 20):
        y = 8
        while y < 24:
            ln = rnd.randint(1, 3)
            for yy in range(y, min(24, y + ln)):
                img.set(col, yy, (40, 34, 30, 255))
            if rnd.random() < 0.5:
                img.set(col + 1, y, (40, 34, 30, 200))
            y += ln + 1
    # red seals
    for sx, sy, s in ((18, 20, 3), (10, 22, 2)):
        rect(img, sx, sy, sx + s, sy + s, (200, 36, 36, 255))
        img.set(sx + 1, sy + 1, (240, 120, 110, 255))
    # coin emblem at the top
    ellipse(img, 16, 8, 2.2, 2.2, lambda nx, ny: (210, 170, 60, 255))
    img.set(16, 8, (120, 80, 30, 255))
    outline(img, (70, 52, 34, 255))
    return img


# ------------------------------------------------------------------ raw gambling stones

def stone(seed, rx, ry, base, skin, vein=None, glow=None, moss=False):
    img = Image(N, N)
    rnd = random.Random(seed)
    cx, cy = 16, 18
    bumps = [rnd.uniform(-0.12, 0.12) for _ in range(12)]

    def rock(nx, ny):
        ang = math.atan2(ny, nx)
        i = int((ang + math.pi) / (2 * math.pi) * 12) % 12
        r = math.sqrt(nx * nx + ny * ny)
        if r > 0.95 + bumps[i]:
            return None
        c = lit(nx, ny, base, amount=0.45)
        n = rnd.random()
        if n < 0.22:
            c = mix(c, skin, 0.55)
        elif n < 0.30:
            c = shade(c, 0.85)
        return c
    ellipse(img, cx, cy, rx, ry, rock)
    if vein is not None:
        # a crack showing the inner colour
        x = cx - rx * 0.5
        y = cy - ry * 0.2
        for _ in range(int(rx * 1.2)):
            x += 1
            y += rnd.choice((-1, 0, 0, 1)) * 0.6
            img.blend(int(x), int(y), vein)
            img.blend(int(x), int(y) + 1, vein[:3] + (vein[3] // 2,))
    if glow is not None:
        for _ in range(int(rx * 0.8)):
            gx = rnd.randint(int(cx - rx * 0.6), int(cx + rx * 0.6))
            gy = rnd.randint(int(cy - ry * 0.5), int(cy + ry * 0.5))
            if img.get(gx, gy)[3] > 0:
                img.blend(gx, gy, glow)
    if moss:
        for _ in range(18):
            mx = rnd.randint(int(cx - rx), int(cx + rx))
            my = rnd.randint(int(cy - ry), int(cy - ry * 0.3))
            if img.get(mx, my)[3] > 0:
                img.set(mx, my, rnd.choice(((70, 120, 50, 255), (90, 140, 60, 255))))
    outline(img, shade(base, 0.32))
    return img


def kerikil():
    return stone(11, 6.5, 5.5, (150, 146, 140, 255), (120, 112, 100, 255))


def bongkah():
    return stone(12, 9.5, 8.0, (138, 128, 118, 255), (168, 140, 110, 255), vein=(220, 214, 200, 120))


def gunung():
    return stone(13, 12.0, 10.0, (110, 112, 116, 255), (90, 96, 104, 255), vein=(110, 220, 150, 230), glow=(150, 255, 190, 120))


def purba():
    return stone(14, 13.5, 11.5, (64, 58, 66, 255), (90, 70, 60, 255), vein=(255, 200, 70, 255), glow=(255, 230, 140, 160), moss=True)


# ------------------------------------------------------------------ mystery lot

def misteri():
    img = Image(N, N)
    lacquer = (150, 24, 28, 255)
    # box body
    for y in range(13, 28):
        for x in range(6, 26):
            c = shade(lacquer, 1.0 - 0.25 * ((y - 13) / 14.0) - 0.15 * ((x - 6) / 19.0))
            img.set(x, y, c)
    # lid: wider, lighter, bevelled
    for y in range(8, 13):
        for x in range(5, 27):
            c = shade(lacquer, 1.25 - 0.06 * (y - 8) - 0.12 * ((x - 5) / 21.0))
            img.set(x, y, c)
    for x in range(5, 27):
        img.set(x, 8, shade(lacquer, 1.45))
        img.set(x, 12, shade(lacquer, 0.7))
    # gold bands crossing over lid and body
    for y in range(8, 28):
        img.set(15, y, (246, 210, 100, 255) if y < 13 else (232, 186, 70, 255))
        img.set(16, y, (214, 166, 56, 255) if y < 13 else (190, 140, 40, 255))
    for x in range(6, 26):
        img.set(x, 20, (232, 186, 70, 255))
        img.set(x, 21, (190, 140, 40, 255))
    # silk ribbon bow on the lid
    for x, y in ((12, 7), (13, 6), (14, 7), (17, 7), (18, 6), (19, 7), (13, 7), (18, 7)):
        img.set(x, y, (240, 214, 110, 255))
    img.set(15, 7, (255, 236, 150, 255))
    img.set(16, 7, (220, 180, 70, 255))
    # brass lock plate
    rect(img, 14, 17, 17, 23, (214, 168, 60, 255))
    img.set(15, 19, (60, 40, 20, 255))
    img.set(15, 20, (60, 40, 20, 255))
    img.set(16, 19, (60, 40, 20, 255))
    # paper seal tag hanging from the lock
    rect(img, 18, 22, 20, 27, (240, 226, 190, 255))
    img.set(19, 24, (200, 36, 36, 255))
    img.set(19, 25, (200, 36, 36, 255))
    # gold corner fittings
    for x, y in ((5, 8), (26, 8), (6, 27), (25, 27), (6, 13), (25, 13)):
        img.set(x, y, (246, 210, 100, 255))
    outline(img, (50, 12, 14, 255))
    # a sparkle: something precious inside
    for x, y, a in ((28, 5, 255), (28, 4, 170), (28, 6, 170), (27, 5, 170), (29, 5, 170)):
        img.blend(x, y, (255, 246, 190, a))
    return img


# ------------------------------------------------------------------ outputs

ITEMS = [
    ("hidangan/bakpao_roh", "bakpao_roh", bakpao),
    ("hidangan/sup_ginseng_roh", "sup_ginseng_roh", sup),
    ("hidangan/mi_panjang_umur", "mi_panjang_umur", mi),
    ("hidangan/arak_bunga_persik", "arak_bunga_persik", arak),
    ("jubah_kultivator_merah", "jubah_merah", lambda: jubah((176, 36, 40, 255), (236, 206, 120, 255), (40, 30, 30, 255))),
    ("jubah_kultivator_biru", "jubah_biru", lambda: jubah((44, 76, 150, 255), (230, 232, 240, 255), (200, 170, 70, 255))),
    ("jubah_kultivator_hijau", "jubah_hijau", lambda: jubah((52, 120, 76, 255), (236, 226, 190, 255), (120, 60, 40, 255))),
    ("jubah_kultivator_emas", "jubah_emas", lambda: jubah((212, 164, 52, 255), (250, 244, 220, 255), (150, 30, 30, 255))),
    ("surat_wesel", "surat_wesel", wesel),
    ("batu_mentah/kerikil", "batu_kerikil", kerikil),
    ("batu_mentah/bongkah", "batu_bongkah", bongkah),
    ("batu_mentah/gunung", "batu_gunung", gunung),
    ("batu_mentah/purba", "batu_purba", purba),
    ("lot_misteri", "lot_misteri", misteri),
]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(obj, indent=2) + "\n")


def validate(img, name):
    opaque = sum(1 for y in range(N) for x in range(N) if img.get(x, y)[3] > 0)
    if opaque < 60:
        raise SystemExit("texture %s is almost empty (%d px)" % (name, opaque))
    colours = len({img.get(x, y) for y in range(N) for x in range(N) if img.get(x, y)[3] > 0})
    if colours < 8:
        raise SystemExit("texture %s is flat (%d colours)" % (name, colours))


def main():
    os.makedirs(TEX, exist_ok=True)
    made = []
    for path, tex, draw in ITEMS:
        img = draw()
        validate(img, tex)
        img.save(os.path.join(TEX, tex + ".png"))
        write_json(os.path.join(ROOT, "models", "item", path + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/ekonomi/" + tex}})
        write_json(os.path.join(ROOT, "items", path + ".json"),
                   {"model": {"type": "minecraft:model", "model": "kultivator:item/" + path}})
        made.append(img)
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        scale = 8
        cols = 7
        rows = (len(made) + cols - 1) // cols
        sheet = Image(cols * (N + 2) * scale, rows * (N + 2) * scale, (60, 60, 70, 255))
        for i, img in enumerate(made):
            ox = (i % cols) * (N + 2) * scale + scale
            oy = (i // cols) * (N + 2) * scale + scale
            for y in range(N):
                for x in range(N):
                    c = img.get(x, y)
                    if c[3] == 0:
                        continue
                    for yy in range(scale):
                        for xx in range(scale):
                            sheet.blend(ox + x * scale + xx, oy + y * scale + yy, c)
        sheet.save(out)
    print("38EK items: %d textures, models and item definitions" % len(made))


if __name__ == "__main__":
    main()
