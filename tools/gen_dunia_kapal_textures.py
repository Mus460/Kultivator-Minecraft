"""Textures of the flying ships (DESIGN 34.5.15, module T), all procedural and seeded (same output every run).

  textures/dunia/kapal/sail.png       64 x 128  a battened junk sail (cream canvas, bamboo battens, sheet lines, a seal);
                                                the renderer tints it a little toward the sect colour
  textures/dunia/kapal/banner.png     32 x 64   a swallow-tailed sect pennant, white (tinted by the banner colour) with an
                                                ink border and the 宗 glyph knocked out
  textures/dunia/kapal/glow.png       32 x 32   a soft round glow (lantern halos, the engine), alpha >= 0.1 in the disc
  textures/dunia/kapal/ring.png       64 x 64   the qi engine ring (a thin bright ring with eight rune ticks)
  textures/dunia/kapal/cloud.png      64 x 64   a cloud puff for the wake (soft lobes, white)
  textures/dunia/kapal/rope.png       8 x 32    a twisted hemp rigging line (cutout; drawn as a thin camera-facing strip)
  textures/dunia/kapal/oar.png        16 x 64   a qi oar: a pale shaft and a leaf blade of light (emissive, translucent)
  textures/dunia/kapal/formation.png  128 x 128 the levitation formation under the keel: three rune rings, a bagua of
                                                trigram bars and a star (emissive, translucent, turned by the renderer)
  textures/dunia/kapal/sail_bajak.png 64 x 128  the sky pirates' ragged sail: tarred cloth, torn holes, a red claw sigil
  textures/item/lunas_perahu_awan.png ...       the three keel items (a small ship of each class on a jade disc)
  textures/item/peta_dunia.png                  the world map scroll
  textures/item/gulungan_penunjuk.png           the pointer scroll (a scroll with a compass seal)
  items/<id>.json, models/item/<id>.json        item definitions and generated models

Run: python tools/gen_dunia_kapal_textures.py [preview_dir]   (preview: 8x copies of every texture)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator")
TEX = os.path.join(ROOT, "textures")


def save(img, rel, preview):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    if preview:
        scale = 8 if img.width <= 32 else 4 if img.width <= 64 else 2
        big = Image(img.width * scale, img.height * scale)
        for y in range(img.height):
            for x in range(img.width):
                c = img.get(x, y)
                for yy in range(scale):
                    for xx in range(scale):
                        big.set(x * scale + xx, y * scale + yy, c)
        out = os.path.join(preview, rel.replace("/", "_"))
        os.makedirs(os.path.dirname(out), exist_ok=True)
        big.save(out)
    return path


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c0, c1, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(lerp(c0[i], c1[i], t))) for i in range(3))


def noise(rng, w, h, cells=8):
    """Smooth value noise in 0..1 (bilinear over a random lattice, tileable)."""
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            gx, gy = x / w * cells, y / h * cells
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            fx, fy = gx - int(gx), gy - int(gy)
            fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            a = lerp(grid[y0][x0], grid[y0][x1], fx)
            b = lerp(grid[y1][x0], grid[y1][x1], fx)
            out[y][x] = lerp(a, b, fy)
    return out


# ----------------------------------------------------------------------------------------------------------- world
def sail():
    w, h = 64, 128
    img = Image(w, h)
    rng = random.Random(56101)
    n1 = noise(rng, w, h, 8)
    n2 = noise(rng, w, h, 32)
    canvas = (236, 224, 196)
    shadow = (196, 176, 140)
    battens = [int(h * k / 7) for k in range(1, 7)]
    for y in range(h):
        # the panel between two battens bellies out: lighter in its middle, darker near the battens
        prev = max([b for b in [0] + battens if b <= y])
        nxt = min([b for b in battens + [h] if b > y] or [h])
        t = (y - prev) / max(1, nxt - prev)
        belly = math.sin(t * math.pi)
        for x in range(w):
            edge = min(x, w - 1 - x) / (w * 0.5)
            light = 0.55 + 0.35 * belly + 0.12 * (n1[y][x] - 0.5) + 0.06 * (n2[y][x] - 0.5) - 0.18 * (1 - min(1, edge * 3))
            c = mix(shadow, canvas, light)
            # canvas weave: faint vertical seams every 8 px
            if x % 8 == 0:
                c = mix(c, shadow, 0.35)
            img.set(x, y, c + (255,))
    # bamboo battens with knots and a dark lashing at both ends
    for b in battens:
        for x in range(w):
            for dy in (-1, 0, 1):
                yy = b + dy
                if 0 <= yy < h:
                    base = (150, 118, 60) if dy == 0 else (116, 88, 44)
                    if x % 13 == 0:
                        base = (92, 70, 34)
                    img.set(x, yy, base + (255,))
        for x in (0, 1, w - 2, w - 1):
            for dy in (-2, -1, 0, 1, 2):
                if 0 <= b + dy < h:
                    img.set(x, b + dy, (48, 34, 22, 255))
    # the luff and leech ropes
    for y in range(h):
        for x in (0, w - 1):
            img.set(x, y, (88, 64, 40, 255))
    # a faded red seal in the middle panel (the sect stamp)
    cx, cy, r = w // 2, int(h * 2.5 / 7), 9
    for y in range(cy - r - 2, cy + r + 3):
        for x in range(cx - r - 2, cx + r + 3):
            d = max(abs(x - cx), abs(y - cy))
            if d <= r and (d >= r - 2 or (abs(x - cx) <= 1 and abs(y - cy) <= r - 4)
                           or (abs(y - cy) <= 1 and abs(x - cx) <= r - 4)
                           or (abs(x - cx - 5) <= 0 and abs(y - cy) <= 6) or (abs(x - cx + 5) <= 0 and abs(y - cy) <= 6)):
                img.blend(x, y, (178, 40, 29, 170))
    # sheet lines fanning from the leech
    for k in range(6):
        y0 = battens[k]
        for i in range(10):
            x = w - 1 - i
            y = y0 + i // 2
            if 0 <= y < h:
                img.blend(x, y, (60, 44, 30, 120))
    return img


def banner():
    w, h = 32, 64
    img = Image(w, h)
    cut = 14  # the swallow tail's notch depth
    for y in range(h):
        for x in range(w):
            inside = True
            if y > h - cut:
                notch = (y - (h - cut)) / cut * (w / 2.0)
                if abs(x + 0.5 - w / 2.0) < notch:
                    inside = False
            if not inside:
                continue
            border = x < 2 or x >= w - 2 or y < 3
            if y > h - cut - 2:
                notch = max(0.0, (y + 2 - (h - cut)) / cut * (w / 2.0))
                if abs(x + 0.5 - w / 2.0) < notch + 1.5 and abs(x + 0.5 - w / 2.0) >= notch - 0.5 and y > h - cut:
                    border = True
            if border:
                img.set(x, y, (210, 180, 90, 255))  # gold edging (multiplied by the tint, reads as a dark trim)
            else:
                shade = 255 - int(18 * math.sin(y / h * math.pi * 3) ** 2)
                img.set(x, y, (shade, shade, shade, 255))
    # the glyph 宗 (roof, a cross bar and the 示 radical), knocked out to a soft grey
    g = (150, 150, 150, 255)
    ox, oy = 8, 12
    pix = []
    pix += [(15 - ox + ox, oy - 1)]
    for x in range(9, 23):
        pix.append((x, oy + 1))
    pix += [(9, oy + 2), (9, oy + 3), (22, oy + 2), (22, oy + 3)]
    for x in range(12, 20):
        pix.append((x, oy + 6))
    for x in range(10, 22):
        pix.append((x, oy + 9))
    for y in range(oy + 9, oy + 20):
        pix.append((16, y))
    pix += [(15, oy + 19), (14, oy + 19)]
    pix += [(12, oy + 13), (11, oy + 14), (10, oy + 15), (20, oy + 13), (21, oy + 14), (22, oy + 15)]
    for (x, y) in pix:
        for dx in (0, 1):
            if 0 <= x + dx < w:
                img.set(x + dx, y, g)
    return img


def glow():
    s = 32
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            d = math.hypot(x + 0.5 - s / 2.0, y + 0.5 - s / 2.0) / (s / 2.0)
            if d >= 1.0:
                continue
            a = (1.0 - d) ** 1.8
            img.set(x, y, (255, 255, 255, int(26 + 229 * a)))
    return img


def ring():
    s = 64
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            dx, dy = x + 0.5 - s / 2.0, y + 0.5 - s / 2.0
            d = math.hypot(dx, dy) / (s / 2.0)
            if d >= 1.0:
                continue
            band = math.exp(-((d - 0.78) / 0.06) ** 2)
            inner = math.exp(-((d - 0.55) / 0.03) ** 2) * 0.6
            ang = math.atan2(dy, dx)
            tick = 0.0
            for k in range(8):
                a = k * math.pi / 4
                da = math.atan2(math.sin(ang - a), math.cos(ang - a))
                if abs(da) < 0.07 and 0.58 < d < 0.74:
                    tick = 0.9
            a = max(band, inner, tick)
            if a < 0.04:
                continue
            img.set(x, y, (255, 255, 255, int(26 + 229 * min(1.0, a))))
    return img


def cloud():
    s = 64
    img = Image(s, s)
    rng = random.Random(56105)
    lobes = [(32, 36, 16), (20, 38, 11), (44, 38, 12), (26, 28, 10), (39, 27, 11), (32, 22, 8)]
    n = noise(rng, s, s, 8)
    for y in range(s):
        for x in range(s):
            best = 0.0
            for (cx, cy, r) in lobes:
                d = math.hypot(x - cx, y - cy) / r
                best = max(best, 1.0 - d)
            fade = min(1.0, max(0.0, best * 1.6)) * (0.8 + 0.4 * (n[y][x] - 0.5))
            # vertical fade so the puff's bottom is flat-ish and soft
            fade *= min(1.0, max(0.0, (52 - y) / 14.0))
            if fade <= 0.02:
                continue
            light = 230 + int(25 * (1 - y / s))
            img.set(x, y, (light, light, min(255, light + 6), int(26 + 229 * min(1.0, fade))))
    return img


def rope():
    w, h = 8, 32
    img = Image(w, h)
    for y in range(h):
        for x in range(1, w - 1):
            # two strands twisting: a diagonal light band every 6 px
            t = ((x + y) % 6) / 6.0
            light = 0.55 + 0.35 * math.sin(t * math.pi)
            edge = 1.0 - abs(x + 0.5 - w / 2.0) / (w / 2.0 - 1)
            c = mix((70, 52, 32), (176, 146, 98), light * (0.6 + 0.4 * edge))
            img.set(x, y, c + (255,))
    return img


def oar():
    w, h = 16, 64
    img = Image(w, h)
    for y in range(h):
        v = y / (h - 1.0)  # 0 at the rowlock (top), 1 at the blade tip
        if v < 0.55:
            half = 1.2
            core = 1.0
        else:
            t = (v - 0.55) / 0.45
            half = 1.2 + 5.6 * math.sin(min(1.0, t * 1.15) * math.pi * 0.62)
            core = 1.0 - 0.35 * t
        for x in range(w):
            d = abs(x + 0.5 - w / 2.0)
            if d > half + 1.0:
                continue
            a = max(0.0, min(1.0, half + 1.0 - d)) * (0.35 + 0.65 * core)
            if v >= 0.55:
                # the blade glows brighter along its spine and its rim
                spine = math.exp(-(d / 1.2) ** 2)
                rim = math.exp(-((d - half) / 0.9) ** 2)
                a *= 0.45 + 0.4 * spine + 0.5 * rim
            img.set(x, y, (255, 255, 255, int(26 + 229 * min(1.0, a))))
    return img


def formation():
    s = 128
    img = Image(s, s)
    c = s / 2.0
    for y in range(s):
        for x in range(s):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = math.hypot(dx, dy) / c
            if d >= 1.0:
                continue
            ang = math.atan2(dy, dx)
            a = 0.0
            # three rings
            for r, wd, k in ((0.94, 0.018, 1.0), (0.80, 0.012, 0.8), (0.42, 0.014, 0.9)):
                a = max(a, k * math.exp(-((d - r) / wd) ** 2))
            # rune ticks between the outer rings (36 marks, every third longer)
            for i in range(36):
                aa = i * math.pi / 18
                da = abs(math.atan2(math.sin(ang - aa), math.cos(ang - aa)))
                lim = 0.90 if i % 3 == 0 else 0.86
                if da * d * c < 0.9 and 0.83 < d < lim:
                    a = max(a, 0.85)
            # bagua: eight trigram bars between the middle and the inner ring
            sector = (ang + math.pi / 8) % (math.pi / 4)
            if abs(sector - math.pi / 8) < math.pi / 14:
                k = int(((ang + math.pi / 8) % (2 * math.pi)) / (math.pi / 4))
                for line in range(3):
                    rr = 0.52 + line * 0.075
                    if abs(d - rr) < 0.018:
                        broken = (k >> line) & 1
                        if not (broken and abs(sector - math.pi / 8) < math.pi / 60):
                            a = max(a, 0.75)
            # a six-pointed star in the heart
            star = 0.0
            for j in range(6):
                aa = j * math.pi / 3
                da = abs(math.atan2(math.sin(ang - aa), math.cos(ang - aa)))
                if d < 0.36 and da < 0.5 * (1.0 - d / 0.36) + 0.03:
                    star = 0.5
            a = max(a, star)
            a = max(a, 0.12 * (1.0 - d))  # a faint wash so the disc reads as one light
            if a < 0.04:
                continue
            img.set(x, y, (255, 255, 255, int(26 + 229 * min(1.0, a))))
    return img


def sail_bajak():
    w, h = 64, 128
    img = Image(w, h)
    rng = random.Random(56106)
    n1 = noise(rng, w, h, 8)
    n2 = noise(rng, w, h, 32)
    tar = (44, 36, 34)
    worn = (92, 74, 62)
    battens = [int(h * k / 6) for k in range(1, 6)]
    holes = [(rng.randint(8, w - 9), rng.randint(10, h - 12), rng.uniform(2.5, 5.5)) for _ in range(5)]
    for y in range(h):
        for x in range(w):
            hole = any(math.hypot((x - hx) * 1.0, (y - hy) * 0.8) < hr * (0.75 + 0.5 * n2[y][x]) for (hx, hy, hr) in holes)
            # the foot is torn into a ragged fringe
            fringe = y > h - 10 and n2[y][x] * 12 < (y - (h - 10)) + (x % 7) * 0.6
            if hole or fringe:
                continue
            light = 0.35 + 0.45 * n1[y][x] + 0.2 * (n2[y][x] - 0.5)
            c = mix(tar, worn, light)
            if x % 9 == 0:
                c = mix(c, (20, 16, 14), 0.4)
            img.set(x, y, c + (255,))
    for b in battens:
        for x in range(w):
            if img.get(x, b)[3] > 0:
                img.set(x, b, (30, 22, 18, 255))
    # the red claw sigil: three curved slashes
    cx, cy = w // 2, int(h * 0.42)
    for k in (-1, 0, 1):
        for t in range(-16, 17):
            x = cx + k * 8 + int(3 * math.sin(t / 16.0 * math.pi / 2))
            y = cy + t
            for dx in (-1, 0, 1):
                if abs(t) < 15 - abs(dx) * 4 and 0 <= x + dx < w and img.get(x + dx, y)[3] > 0:
                    img.set(x + dx, y, (168, 28, 22, 255))
    for y in range(h):
        for x in (0, w - 1):
            if img.get(x, y)[3] > 0:
                img.set(x, y, (24, 18, 14, 255))
    return img


# ----------------------------------------------------------------------------------------------------------- items
OUTLINE = (34, 24, 16, 255)


def item_ship(kind):
    """A little ship over a jade disc: hull colour per class, sails, one lantern dot."""
    img = Image(16, 16)
    # jade disc behind (the keel's formation)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 9.0)
            if 6.2 <= d <= 7.4 and y >= 3:
                img.set(x, y, (82, 186, 140, 200))
    hull = {"perahu": (92, 60, 36), "sekte": (78, 50, 30), "bahtera": (62, 64, 70)}[kind]
    trim = {"perahu": (168, 52, 44), "sekte": (150, 70, 40), "bahtera": (226, 182, 64)}[kind]
    sails = {"perahu": 1, "sekte": 2, "bahtera": 3}[kind]
    # hull: a crescent from x 2..13 at y 10..12
    rows = [(10, 1, 14), (11, 2, 13), (12, 4, 11)]
    for (y, x0, x1) in rows:
        for x in range(x0, x1 + 1):
            c = hull if y > 10 else trim
            img.set(x, y, c + (255,))
        img.set(x0 - 1, y, OUTLINE)
        img.set(x1 + 1, y, OUTLINE)
    for x in range(4, 12):
        img.set(x, 13, OUTLINE)
    img.set(1, 9, OUTLINE)
    img.set(14, 9, (226, 182, 64, 255))  # the gilt prow
    img.set(14, 8, OUTLINE)
    # masts and sails
    xs = {1: [8], 2: [6, 10], 3: [4, 8, 11]}[sails]
    for i, mx in enumerate(xs):
        top = 3 if i == len(xs) // 2 else 5
        for y in range(top, 10):
            img.set(mx, y, (58, 38, 24, 255))
        for y in range(top + 1, 9):
            for dx in range(1, 3):
                if mx + dx < 15:
                    shade = 238 - 10 * ((y - top) % 2)
                    img.set(mx + dx, y, (shade, shade - 12, shade - 38, 255))
        img.set(mx + 1, top + 1, (178, 40, 29, 255))
    # a lantern glow at the stern
    img.set(2, 8, (255, 210, 110, 255))
    if kind == "bahtera":
        for x in (3, 6, 9, 12):
            img.set(x, 11, (226, 182, 64, 255))
    return img


def item_map():
    img = Image(16, 16)
    paper = (226, 210, 168)
    for y in range(3, 13):
        for x in range(2, 14):
            c = paper
            if (x * 7 + y * 3) % 11 == 0:
                c = mix(paper, (180, 160, 120), 0.5)
            img.set(x, y, c + (255,))
    # ink mountains and a river
    for (x, y) in ((4, 8), (5, 7), (6, 6), (7, 7), (8, 8), (9, 7), (10, 6), (11, 7)):
        img.set(x, y, (40, 46, 44, 255))
    for (x, y) in ((3, 11), (4, 10), (5, 10), (6, 11), (7, 11), (8, 10), (9, 10), (10, 11), (11, 11), (12, 10)):
        img.set(x, y, (70, 120, 150, 255))
    img.set(11, 4, (178, 40, 29, 255))
    img.set(12, 4, (178, 40, 29, 255))
    img.set(11, 5, (178, 40, 29, 255))
    # the two rollers
    for y in range(2, 14):
        img.set(1, y, (110, 70, 40, 255))
        img.set(14, y, (110, 70, 40, 255))
    for x in (1, 14):
        img.set(x, 1, (226, 182, 64, 255))
        img.set(x, 14, (226, 182, 64, 255))
    for x in range(2, 14):
        img.set(x, 2, OUTLINE)
        img.set(x, 13, OUTLINE)
    return img


def item_pointer():
    img = Image(16, 16)
    # a rolled scroll diagonally with a round compass seal
    for i in range(12):
        x, y = 2 + i, 12 - i
        for d in (-1, 0, 1):
            c = (228, 214, 172) if d == 0 else (196, 176, 132)
            img.set(x + d, y, c + (255,))
        img.set(x, y + 2, OUTLINE) if i < 11 else None
    for (x, y) in ((1, 13), (2, 14), (14, 1), (13, 0)):
        img.set(x, y, (226, 182, 64, 255))
    cx, cy = 8, 8
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 3.2:
                img.set(x, y, (178, 40, 29, 255) if d > 2.2 else (240, 220, 150, 255))
    img.set(cx, cy - 1, (40, 46, 44, 255))
    img.set(cx, cy - 2, (40, 46, 44, 255))
    img.set(cx + 1, cy, (40, 46, 44, 255))
    img.set(cx, cy, (40, 46, 44, 255))
    return img


ITEMS = {
    "lunas_perahu_awan": lambda: item_ship("perahu"),
    "lunas_kapal_sekte": lambda: item_ship("sekte"),
    "lunas_bahtera_langit": lambda: item_ship("bahtera"),
    "peta_dunia": item_map,
    "gulungan_penunjuk": item_pointer,
}


def write_item_json(name):
    for rel, obj in ((os.path.join("items", name + ".json"),
                      {"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}}),
                     (os.path.join("models", "item", name + ".json"),
                      {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}})):
        path = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(obj, f, indent=2)
            f.write("\n")


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    if preview:
        os.makedirs(preview, exist_ok=True)
    save(sail(), "dunia/kapal/sail.png", preview)
    save(banner(), "dunia/kapal/banner.png", preview)
    save(glow(), "dunia/kapal/glow.png", preview)
    save(ring(), "dunia/kapal/ring.png", preview)
    save(cloud(), "dunia/kapal/cloud.png", preview)
    save(rope(), "dunia/kapal/rope.png", preview)
    save(oar(), "dunia/kapal/oar.png", preview)
    save(formation(), "dunia/kapal/formation.png", preview)
    save(sail_bajak(), "dunia/kapal/sail_bajak.png", preview)
    for name, make in ITEMS.items():
        save(make(), "item/" + name + ".png", preview)
        write_item_json(name)
    print("gen_dunia_kapal_textures: 9 world textures, %d items" % len(ITEMS))


if __name__ == "__main__":
    main()
