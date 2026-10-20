"""Generates the GUI textures of phase 10b Titah Langit (M2 client cinema and GUI, DESIGN 29.2.2).

All white with alpha, tinted at blit time (our own drawings; nothing is taken from any other mod):
textures/gui/titah/decree_icons.png 128x32 four 32x32 silhouettes behind the decree cards of the Titah Langit menu:
                                    0 the Heaven-Splitting Sword point-down, 1 the sea-fixing iron pillar with its gold
                                    hoops, 2 the seven stars of the Northern Dipper joined in order, 3 a square seal with a
                                    mountain knob.
textures/gui/titah/seal_wash.png    128x128 a faint ink wash of a square jade seal (a double border, a coiled knob and
                                    abstract carved strokes) drawn behind the selected decree's panel.

Run with: python tools/gen_titah_gui_textures.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
GUI = os.path.join(ROOT, "gui", "titah")


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def put(img, x, y, a):
    """Adds white coverage {@code a} (0..1) at a pixel, keeping the strongest."""
    if not (0 <= x < img.width and 0 <= y < img.height):
        return
    old = img.get(x, y)[3] / 255.0
    img.set(x, y, (255, 255, 255, int(clamp(max(old, a)) * 255)))


def segment(img, ox, x0, y0, x1, y1, width):
    """An anti-aliased stroke from (x0, y0) to (x1, y1) inside the cell starting at column {@code ox}."""
    lo_x = int(min(x0, x1) - width - 1)
    hi_x = int(max(x0, x1) + width + 2)
    lo_y = int(min(y0, y1) - width - 1)
    hi_y = int(max(y0, y1) + width + 2)
    dx = x1 - x0
    dy = y1 - y0
    length2 = dx * dx + dy * dy
    for y in range(max(0, lo_y), min(img.height, hi_y)):
        for x in range(max(0, lo_x), min(32, hi_x)):
            px = x + 0.5
            py = y + 0.5
            t = 0.0 if length2 == 0 else clamp(((px - x0) * dx + (py - y0) * dy) / length2)
            cx = x0 + dx * t
            cy = y0 + dy * t
            d = math.sqrt((px - cx) ** 2 + (py - cy) ** 2)
            put(img, ox + x, y, clamp(width / 2.0 + 0.5 - d))


def disc(img, ox, cx, cy, r, strength=1.0):
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            if 0 <= x < 32:
                d = math.sqrt((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2)
                put(img, ox + x, y, clamp(r + 0.5 - d) * strength)


def sword(img, ox):
    # a long straight blade point-down, a wide guard, a grip and a round pommel
    for y in range(4, 28):
        half = 2.2 if y < 24 else 2.2 * (28 - y) / 4.0
        for x in range(32):
            d = abs(x + 0.5 - 16.0)
            put(img, ox + x, y, clamp(half + 0.5 - d))
    segment(img, ox, 16.0, 6.0, 16.0, 24.0, 0.8)
    segment(img, ox, 8.0, 4.5, 24.0, 4.5, 2.4)
    segment(img, ox, 16.0, 1.0, 16.0, 4.0, 2.0)
    disc(img, ox, 16.0, 1.5, 1.6)


def pillar(img, ox):
    # the iron pillar with a hoop at each end and one in the middle
    for y in range(3, 30):
        for x in range(32):
            d = abs(x + 0.5 - 16.0)
            put(img, ox + x, y, clamp(3.0 + 0.5 - d) * 0.85)
    for hy in (4.5, 16.0, 27.5):
        segment(img, ox, 11.0, hy, 21.0, hy, 2.6)


def dipper(img, ox):
    # the seven stars of the Northern Dipper, Tianshu to Yaoguang, joined in order
    stars = [(4.0, 10.0), (5.0, 19.0), (13.0, 21.0), (14.0, 13.0), (20.0, 12.0), (25.0, 11.0), (29.0, 15.0)]
    for i in range(len(stars) - 1):
        segment(img, ox, stars[i][0], stars[i][1], stars[i + 1][0], stars[i + 1][1], 0.9)
    segment(img, ox, stars[3][0], stars[3][1], stars[0][0], stars[0][1], 0.9)
    for (x, y) in stars:
        disc(img, ox, x, y, 2.0)
        segment(img, ox, x - 3.5, y, x + 3.5, y, 0.6)
        segment(img, ox, x, y - 3.5, x, y + 3.5, 0.6)


def mountain_seal(img, ox):
    # a square seal body with a mountain-shaped knob (three peaks)
    for y in range(17, 30):
        for x in range(6, 26):
            edge = min(x - 6, 25 - x, y - 17, 29 - y)
            put(img, ox + x, y, 0.95 if edge >= 1 else 0.7)
    segment(img, ox, 6.0, 17.0, 11.0, 8.0, 1.6)
    segment(img, ox, 11.0, 8.0, 16.0, 3.0, 1.6)
    segment(img, ox, 16.0, 3.0, 21.0, 8.0, 1.6)
    segment(img, ox, 21.0, 8.0, 26.0, 17.0, 1.6)
    segment(img, ox, 11.0, 8.0, 13.0, 17.0, 1.0)
    segment(img, ox, 21.0, 8.0, 19.0, 17.0, 1.0)


def decree_icons():
    img = Image(128, 32)
    sword(img, 0)
    pillar(img, 32)
    dipper(img, 64)
    mountain_seal(img, 96)
    img.save(os.path.join(GUI, "decree_icons.png"))


def seal_wash():
    size = 128
    img = Image(size, size)
    rnd = random.Random(2901)
    grain = [[rnd.random() for _ in range(size)] for _ in range(size)]
    for y in range(size):
        for x in range(size):
            # a double square border with dry-brush grain
            e = min(x, size - 1 - x, y, size - 1 - y)
            a = 0.0
            if 10 <= e <= 16:
                a = 0.75
            elif 20 <= e <= 22:
                a = 0.5
            if a > 0.0:
                a *= 0.65 + 0.35 * grain[y][x]
                put(img, x, y, a)
    # abstract carved strokes inside (seal-script feel, not a real character)
    strokes = [((36, 40), (92, 40)), ((40, 40), (40, 92)), ((40, 66), (88, 66)), ((64, 30), (64, 98)), ((88, 40), (88, 92)),
               ((44, 92), (84, 92)), ((52, 50), (76, 78)), ((76, 50), (52, 78))]
    for (a, b) in strokes:
        x0, y0 = a
        x1, y1 = b
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps):
            t = i / max(1, steps - 1)
            cx = x0 + (x1 - x0) * t
            cy = y0 + (y1 - y0) * t
            for oy in range(-3, 4):
                for ox in range(-3, 4):
                    d = math.sqrt(ox * ox + oy * oy)
                    if d <= 3.0:
                        gx = int(cx + ox)
                        gy = int(cy + oy)
                        if 0 <= gx < size and 0 <= gy < size:
                            put(img, gx, gy, clamp(3.2 - d) * (0.55 + 0.35 * grain[gy][gx]))
    img.save(os.path.join(GUI, "seal_wash.png"))


def main():
    os.makedirs(GUI, exist_ok=True)
    decree_icons()
    seal_wash()


if __name__ == "__main__":
    main()
