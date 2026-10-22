"""Generates the world-layer textures of the sect tournament's Lingkar Lei Tai (DESIGN 36.2.3, module T).

All four are drawn with entityTranslucentEmissive, which drops texture alpha below 0.1 BEFORE the vertex colour, so every
pixel that should show keeps alpha >= 26 and every fade goes into the vertex alpha. They are white or pale grey and are
tinted per vertex (cinnabar for a normal season, gold for the Elite and the final, jade for an inter-sect tournament).

textures/sekte/turnamen/leitai_ring.png     128x16  the ring band laid flat round the stage, tileable along u: two thin
                                                    rails at the edges, a key-fret (huiwen) meander between them and a
                                                    soft glowing core line; v across the band (0 inner .. 1 outer).
textures/sekte/turnamen/leitai_tirai.png    32x64   the rising qi curtain of a bout: bright at the foot (v = 1), fading up
                                                    in vertical streaks of uneven length, tileable along u.
textures/sekte/turnamen/leitai_lentera.png  32x32   a spirit lamp: a soft round glow round a small paper-lantern core
                                                    with ribs and a cap.
textures/sekte/turnamen/leitai_bunga.png    16x16   one plum petal (five-lobed blossom seen side-on), for the champion's
                                                    falling petals.

Run with: python tools/gen_sekte_turnamen_textures.py
Deterministic: the same files on every run.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
OUT = os.path.join(ASSETS, "textures", "sekte", "turnamen")
MIN_A = 26


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def put(img, x, y, grey, alpha):
    if alpha <= 0:
        return
    a = int(round(max(MIN_A, min(255, alpha))))
    g = int(round(clamp(grey / 255.0) * 255))
    old = img.get(x % img.width, y % img.height)
    if old[3] >= a:
        return
    img.set(x % img.width, y % img.height, (g, g, g, a))


def ring():
    w, h = 128, 16
    img = Image(w, h)
    # the two rails
    for x in range(w):
        for y in (1, 2, h - 3, h - 2):
            put(img, x, y, 245, 230 if y in (1, h - 2) else 170)
    # the glowing core: a soft band in the middle, brightest at the centre line
    for x in range(w):
        for y in range(3, h - 3):
            d = abs(y - (h - 1) / 2.0) / ((h - 6) / 2.0)
            a = 90 * (1 - smooth(d))
            if a > 0:
                put(img, x, y, 255, a + 26)
    # the key fret (huiwen): a square spiral hook every 16 px, alternating up and down
    for cell in range(w // 16):
        ox = cell * 16
        up = cell % 2 == 0
        pts = []
        # outline of one hook in a 12x8 box: a meander path, 1 px wide
        path = [(0, 7), (0, 0), (11, 0), (11, 7), (3, 7), (3, 3), (8, 3), (8, 5)]
        for (ax, ay), (bx, by) in zip(path, path[1:]):
            steps = max(abs(bx - ax), abs(by - ay))
            for s in range(steps + 1):
                px = ax + (bx - ax) * s // max(1, steps)
                py = ay + (by - ay) * s // max(1, steps)
                pts.append((px, py))
        for px, py in pts:
            y = 4 + (py if up else 7 - py)
            put(img, ox + 2 + px, y, 255, 235)
        # the link to the next hook along the bottom/top
        for s in range(4):
            put(img, ox + 13 + s, 4 + (7 if up else 0), 240, 200)
    return img


def curtain():
    w, h = 32, 64
    img = Image(w, h)
    rnd = random.Random(0x1E17A1)
    heights = [0.35 + 0.65 * rnd.random() for _ in range(w)]
    # smooth neighbouring streaks a little so they read as flowing qi, not noise
    heights = [(heights[i - 1] + 2 * heights[i] + heights[(i + 1) % w]) / 4.0 for i in range(w)]
    for x in range(w):
        top = 1.0 - heights[x]
        bright = 0.6 + 0.4 * rnd.random()
        for y in range(h):
            v = y / (h - 1.0)
            if v < top:
                continue
            t = (v - top) / max(0.001, 1.0 - top)
            a = 255 * bright * (0.15 + 0.85 * t ** 1.6)
            grey = 200 + 55 * t
            put(img, x, y, grey, a)
    # a bright foot line
    for x in range(w):
        put(img, x, h - 1, 255, 255)
        put(img, x, h - 2, 255, 220)
    return img


def lantern():
    s = 32
    img = Image(s, s)
    c = (s - 1) / 2.0
    for y in range(s):
        for x in range(s):
            d = math.hypot(x - c, y - c) / (s / 2.0)
            a = 255 * (1 - smooth(d)) ** 1.8
            if a >= MIN_A:
                put(img, x, y, 235, a)
    # the paper lantern: an oval body with ribs, a cap and a tassel
    for y in range(9, 23):
        for x in range(10, 22):
            dx = (x - c) / 6.0
            dy = (y - 15.5) / 7.0
            if dx * dx + dy * dy <= 1.0:
                rib = (x - 10) % 3 == 0
                put(img, x, y, 255 if not rib else 215, 255)
    for x in range(13, 19):
        put(img, x, 8, 160, 255)
        put(img, x, 23, 160, 255)
    for y in range(24, 28):
        put(img, 16, y, 200, 230)
    return img


def petal():
    s = 16
    img = Image(s, s)
    c = (s - 1) / 2.0
    for y in range(s):
        for x in range(s):
            ang = math.atan2(y - c, x - c)
            r = math.hypot(x - c, y - c)
            edge = 7.0 * (0.62 + 0.38 * abs(math.cos(2.5 * ang)) ** 0.6)
            if r <= edge:
                t = r / edge
                put(img, x, y, 255 - 40 * t, 255 * (1 - 0.35 * t))
    # the stamens: a few darker dots round the heart
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.3
        put(img, int(round(c + 2 * math.cos(a))), int(round(c + 2 * math.sin(a))), 150, 255)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    ring().save(os.path.join(OUT, "leitai_ring.png"))
    curtain().save(os.path.join(OUT, "leitai_tirai.png"))
    lantern().save(os.path.join(OUT, "leitai_lentera.png"))
    petal().save(os.path.join(OUT, "leitai_bunga.png"))
    print("wrote 4 textures to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
