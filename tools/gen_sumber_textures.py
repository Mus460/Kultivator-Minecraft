"""Generates the item textures of the phase-6c acquisition module P6c-seni-A (16x16 pixel art).

  jade_slip.png     Slip Giok: three thin jade strips side by side (like bamboo slips) bound by two gold threads, with
                    engraved glyph marks; pale green translucent jade with a bright edge (A Record of a Mortal's Journey:
                    jade slips store arts)
  giok_warisan.png  Giok Warisan: a round white-green jade disc with a hole in the middle, a gold glyph ring on its face, a
                    faint golden halo and a red tassel hanging below (an inheritance token)

Run with: python tools/gen_sumber_textures.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
ITEM = os.path.join(ROOT, "item")


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def noise(x, y, seed):
    """Deterministic 0..1 noise per pixel."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


JADE_PALE = (196, 244, 214)
JADE_LIGHT = (140, 222, 176)
JADE = (78, 180, 132)
JADE_DARK = (34, 110, 80)
JADE_DEEP = (20, 66, 48)
WHITE_JADE = (226, 246, 232)
WHITE_JADE_MID = (180, 222, 196)
WHITE_JADE_DARK = (110, 160, 132)
GOLD_LIGHT = (255, 236, 150)
GOLD = (226, 178, 64)
GOLD_DARK = (140, 100, 28)
RED = (196, 40, 34)
RED_DARK = (120, 20, 20)
RED_LIGHT = (236, 92, 70)


def jade_slip():
    img = Image(16, 16)
    strips = [(3, 4), (7, 8), (11, 12)]
    for index, (x0, x1) in enumerate(strips):
        top = 1 + (index % 2)
        bottom = 14 - ((index + 1) % 2)
        for y in range(top, bottom + 1):
            for x in (x0, x1):
                t = (y - top) / max(1, bottom - top)
                c = mix(JADE_LIGHT, JADE, 0.15 + 0.7 * t)
                if x == x1:
                    c = mix(c, JADE_DARK, 0.35)
                if noise(x, y, 7 + index) > 0.88:
                    c = mix(c, JADE_PALE, 0.5)
                img.set(x, y, c)
            # A dark outline only on the outer sides of the bundle; the gaps between strips stay open.
            if index == 0:
                img.set(x0 - 1, y, (JADE_DEEP[0], JADE_DEEP[1], JADE_DEEP[2], 210))
            if index == len(strips) - 1:
                img.set(x1 + 1, y, (JADE_DEEP[0], JADE_DEEP[1], JADE_DEEP[2], 230))
            else:
                img.set(x1 + 1, y, (JADE_DEEP[0], JADE_DEEP[1], JADE_DEEP[2], 120))
        # Rounded ends and a bright top edge.
        img.set(x0, top, mix(JADE_PALE, (255, 255, 255), 0.3))
        img.set(x1, top, JADE_LIGHT)
        img.set(x0, bottom, JADE_DARK)
        img.set(x1, bottom, JADE_DEEP)
        # Engraved glyph marks down the strip (deep jade with a pale lower lip).
        for y in range(top + 3, bottom - 2, 2):
            if (y + index) % 4 != 1:
                img.set(x0, y, JADE_DEEP)
                img.set(x0, y + 1, mix(img.get(x0, y + 1), JADE_PALE, 0.4))
            else:
                img.set(x1, y, JADE_DEEP)
    # Two gold threads binding the strips.
    for y in (4, 11):
        for x in range(2, 14):
            c = GOLD if x % 2 == 0 else GOLD_LIGHT
            if img.get(x, y)[3] == 0:
                c = GOLD_DARK
            img.set(x, y, c)
    # A small knot and tail of the thread on the right.
    img.set(14, 11, GOLD)
    img.set(14, 12, GOLD_DARK)
    img.set(15, 13, GOLD_DARK)
    img.save(os.path.join(ITEM, "jade_slip.png"))


def giok_warisan():
    img = Image(16, 16)
    cx, cy = 7.5, 6.5
    outer, inner = 6.2, 2.0
    # A faint golden halo first.
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if outer < d <= outer + 1.3 and y < 13:
                img.blend(x, y, (GOLD_LIGHT[0], GOLD_LIGHT[1], GOLD_LIGHT[2], 70))
    # The disc: white-green jade, lit from the top-left, with a dark rim and an inner hole.
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if inner < d <= outer:
                shade = ((x - cx) + (y - cy)) / (2 * outer)
                c = mix(WHITE_JADE, WHITE_JADE_MID, 0.45 + 0.55 * shade)
                if noise(x, y, 31) > 0.86:
                    c = mix(c, (255, 255, 255), 0.4)
                elif noise(x, y, 53) < 0.10:
                    c = mix(c, WHITE_JADE_DARK, 0.35)
                if d > outer - 0.9:
                    c = mix(c, WHITE_JADE_DARK, 0.6)
                if d < inner + 0.9:
                    c = mix(c, WHITE_JADE_DARK, 0.5)
                img.set(x, y, c)
    # A gold glyph ring on the face: eight marks around the hole.
    for k in range(8):
        a = k * math.pi / 4
        r = (outer + inner) / 2
        x = int(round(cx + math.cos(a) * r))
        y = int(round(cy + math.sin(a) * r))
        img.set(x, y, GOLD if k % 2 == 0 else GOLD_DARK)
    img.set(int(cx - 2), int(cy - 3), GOLD_LIGHT)
    # Highlight at the top-left of the disc.
    img.set(4, 2, (255, 255, 255, 255))
    img.set(5, 2, mix(WHITE_JADE, (255, 255, 255), 0.6))
    img.set(3, 3, mix(WHITE_JADE, (255, 255, 255), 0.5))
    # The red tassel below: a cord, a knot and the fringe.
    for y in range(13, 16):
        img.set(7, y, RED if y < 15 else RED_DARK)
    img.set(8, 13, RED_DARK)
    img.set(6, 14, RED_LIGHT)
    img.set(8, 14, RED)
    img.set(6, 15, RED_DARK)
    img.set(8, 15, RED_DARK)
    img.save(os.path.join(ITEM, "giok_warisan.png"))


if __name__ == "__main__":
    os.makedirs(ITEM, exist_ok=True)
    jade_slip()
    giok_warisan()
    print("sumber textures written")
