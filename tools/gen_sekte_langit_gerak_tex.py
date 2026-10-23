"""Generates the textures of module 37M, Sekte Langit movement (DESIGN 37.5, docs/rancangan/37-sekte-langit.md).

textures/sekte/langit/gerak/feri_awan.png  64x64  the Cloud Ferry's atlas, 16x16 cells (u right, v down):
    row 0: (0) jade lacquer planks with a gold rim line, (1) pale deck boards, (2) gold leaf with an engraved cloud scroll,
           (3) azure glazed roof tiles
    row 1: (0) cinnabar lacquer (pillars), (1) lantern paper with ribs (drawn emissive), (2) white cloud silk (pennant),
           (3) dark keel wood
    row 2: (0) jade side panel with a carved cloud curl (bow and stern), (1) bench cushion (deep blue brocade)
textures/sekte/langit/gerak/awan_puff.png  32x32  one soft cloud puff (white, blue-grey belly); alpha >= 26 where it shows
textures/sekte/langit/gerak/cahaya.png     32x32  a round warm-white glow (lantern halos), alpha >= 26 inside the disc
textures/sekte/langit/gerak/cincin.png     64x64  the levitation ring under the hull: two thin rings, cloud-curl ticks
                                                  and eight small trigram bars, white (tinted per vertex)

Run: python tools/gen_sekte_langit_gerak_tex.py   (deterministic; check the PNGs with an image viewer afterwards)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "kultivator",
                    "textures", "sekte", "langit", "gerak")


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def shade(rgb, f):
    return tuple(clamp(c * f) for c in rgb)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def cell(img, cx, cy, fn):
    for y in range(16):
        for x in range(16):
            rgba = fn(x, y)
            img.set(cx * 16 + x, cy * 16 + y, rgba)


def feri():
    rnd = random.Random(3701)
    img = Image(64, 64)
    jade = (63, 160, 120)
    gold = (214, 168, 58)
    grain = [[rnd.random() for _ in range(16)] for _ in range(16)]

    # (0,0) jade lacquer planks: four boards, dark seams, a soft sheen band, a gold rim on the top row
    def jade_planks(x, y):
        if y == 0:
            return gold + (255,)
        if y == 1:
            return shade(gold, 0.7) + (255,)
        board = (y - 2) % 4
        base = shade(jade, 0.92 + 0.10 * grain[y][x] - (0.25 if board == 3 else 0.0))
        sheen = max(0.0, 1.0 - abs(x - 5) / 4.0) * 0.18
        base = mix(base, (190, 240, 215), sheen)
        return base + (255,)
    cell(img, 0, 0, jade_planks)

    # (1,0) deck boards, vertical, pale oak
    deck = (222, 202, 160)

    def deck_boards(x, y):
        if x % 4 == 3:
            return shade(deck, 0.62) + (255,)
        f = 0.9 + 0.12 * grain[(y + x * 3) % 16][x] - (0.06 if (y + x) % 7 == 0 else 0.0)
        return shade(deck, f) + (255,)
    cell(img, 1, 0, deck_boards)

    # (2,0) gold leaf with an engraved cloud scroll
    def gold_leaf(x, y):
        dx = x - 7.5
        dy = y - 7.5
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        spiral = (r - 1.2 * a) % 4.0
        f = 1.0 + 0.12 * math.cos((x + y) * 0.6)
        if 1.2 < r < 7.2 and spiral < 0.9:
            f = 0.66
        if x in (0, 15) or y in (0, 15):
            f = 0.78
        return shade(gold, f) + (255,)
    cell(img, 2, 0, gold_leaf)

    # (3,0) azure glazed roof tiles: rows of round tile ends with a bright glaze highlight
    azure = (60, 127, 214)

    def tiles(x, y):
        row = y // 4
        ox = (x + (2 if row % 2 else 0)) % 4
        local_y = y % 4
        edge = (ox - 1.5) ** 2 / 2.25 + (local_y - 3.2) ** 2 / 4.0
        f = 1.0
        if edge > 1.0:
            f = 0.72
        if local_y == 0 and ox in (1, 2):
            f = 1.32
        if local_y == 3:
            f *= 0.82
        return shade(azure, f) + (255,)
    cell(img, 3, 0, tiles)

    # (0,1) cinnabar lacquer with a vertical sheen
    cinnabar = (179, 40, 29)

    def lacquer(x, y):
        f = 0.82 + 0.35 * max(0.0, 1.0 - abs(x - 5) / 3.0) + 0.04 * grain[y][x]
        if y in (0, 15):
            return gold + (255,)
        return shade(cinnabar, f) + (255,)
    cell(img, 0, 1, lacquer)

    # (1,1) lantern paper: warm, bright in the middle, darker ribs every 3 rows, gold caps
    paper = (255, 196, 104)

    def lantern(x, y):
        if y < 2 or y > 13:
            return shade(gold, 0.85) + (255,)
        r = abs(x - 7.5) / 7.5
        f = 1.1 - 0.35 * r * r
        if (y - 2) % 3 == 0:
            f *= 0.78
        c = mix(shade(paper, f), (255, 245, 210), max(0.0, 0.5 - r))
        return c + (255,)
    cell(img, 1, 1, lantern)

    # (2,1) white cloud silk with faint blue cloud curls
    silk = (242, 242, 236)

    def silk_cell(x, y):
        wave = math.sin(x * 0.8 + math.sin(y * 0.6) * 1.5)
        c = silk
        if wave > 0.82:
            c = (196, 220, 242)
        f = 0.96 + 0.05 * grain[y][x]
        return shade(c, f) + (255,)
    cell(img, 2, 1, silk_cell)

    # (3,1) dark keel wood
    dark = (74, 48, 32)

    def keel(x, y):
        f = 0.85 + 0.25 * grain[y][(x + y) % 16] - (0.2 if y % 5 == 4 else 0.0)
        return shade(dark, f) + (255,)
    cell(img, 3, 1, keel)

    # (0,2) jade side panel with a carved cloud curl (bow and stern faces)
    def curl_panel(x, y):
        base = shade(jade, 0.95 + 0.06 * grain[y][x])
        if x in (0, 15) or y in (0, 15):
            return gold + (255,)
        dx = x - 7.5
        dy = y - 8.0
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        s = (r - 0.9 * (a + math.pi)) % 3.4
        if 1.0 < r < 6.5 and s < 0.85:
            return shade(gold, 0.95) + (255,)
        return base + (255,)
    cell(img, 0, 2, curl_panel)

    # (1,2) bench cushion: deep blue brocade with small gold lozenges
    brocade = (36, 52, 120)

    def cushion(x, y):
        if (x + y) % 6 == 0 and (x - y) % 6 == 0:
            return shade(gold, 0.9) + (255,)
        f = 0.9 + 0.15 * math.sin(x * 0.9) * math.sin(y * 0.9)
        return shade(brocade, f) + (255,)
    cell(img, 1, 2, cushion)
    return img


def puff():
    rnd = random.Random(3702)
    img = Image(32, 32)
    blobs = [(16, 17, 9.0), (10, 19, 6.5), (22, 19, 6.5), (13, 13, 6.0), (20, 13, 5.5), (16, 21, 7.0)]
    for _ in range(6):
        blobs.append((rnd.uniform(9, 23), rnd.uniform(12, 22), rnd.uniform(3.0, 4.5)))
    for y in range(32):
        for x in range(32):
            d = 0.0
            for bx, by, br in blobs:
                v = 1.0 - math.hypot(x + 0.5 - bx, y + 0.5 - by) / br
                d = max(d, v)
            if d <= 0.0:
                continue
            alpha = min(1.0, d * 2.2)
            a = clamp(26 + 229 * alpha)
            belly = max(0.0, (y - 14) / 14.0)
            c = mix((255, 255, 255), (178, 196, 222), belly * 0.85)
            img.set(x, y, c + (a,))
    return img


def glow():
    img = Image(32, 32)
    for y in range(32):
        for x in range(32):
            r = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16.0
            if r >= 1.0:
                continue
            v = (1.0 - r) ** 1.6
            a = clamp(26 + 229 * v)
            c = mix((255, 214, 150), (255, 255, 240), v)
            img.set(x, y, c + (a,))
    return img


def ring():
    img = Image(64, 64)
    for y in range(64):
        for x in range(64):
            dx = x + 0.5 - 32
            dy = y + 0.5 - 32
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            alpha = 0.0
            for rr, w in ((30.0, 1.3), (25.0, 0.9), (13.0, 0.8)):
                alpha = max(alpha, 1.0 - abs(r - rr) / w)
            # cloud-curl ticks between the outer rings
            sector = (a + math.pi) / (2 * math.pi) * 24.0
            frac = sector - math.floor(sector)
            if 25.8 < r < 29.2 and abs(frac - 0.5) < 0.09:
                alpha = max(alpha, 0.85)
            # eight trigram bars on the inner band
            sector8 = (a + math.pi) / (2 * math.pi) * 8.0
            f8 = sector8 - math.floor(sector8)
            if 0.35 < f8 < 0.65:
                for k, band in enumerate((16.0, 18.5, 21.0)):
                    if abs(r - band) < 0.7:
                        broken = (int(math.floor(sector8)) >> k) & 1
                        if not (broken and 0.46 < f8 < 0.54):
                            alpha = max(alpha, 0.9)
            if alpha <= 0.0:
                continue
            img.set(x, y, (255, 255, 255, clamp(26 + 229 * min(1.0, alpha))))
    return img


def main():
    os.makedirs(ROOT, exist_ok=True)
    out = {
        "feri_awan.png": feri(),
        "awan_puff.png": puff(),
        "cahaya.png": glow(),
        "cincin.png": ring(),
    }
    for name, img in out.items():
        path = os.path.join(ROOT, name)
        img.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
