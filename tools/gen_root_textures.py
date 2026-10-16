"""Generates the item texture of the spiritual-roots module: the Marrow Cleansing Pill (16x16 pixel art).

Run with: python tools/gen_root_textures.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")

OUTLINE = (46, 38, 58, 255)
SHADOW = (150, 136, 118, 255)
BODY_DARK = (196, 184, 160, 255)
BODY = (232, 224, 204, 255)
BODY_LIGHT = (248, 244, 232, 255)
HIGHLIGHT = (255, 255, 255, 255)
JADE_DARK = (58, 150, 128, 255)
JADE = (110, 210, 178, 255)
JADE_LIGHT = (170, 240, 214, 255)
IMPURITY = (52, 46, 42, 255)
IMPURITY_LIGHT = (92, 84, 76, 255)
SPARK = (255, 236, 160, 255)
SPARK_CORE = (255, 255, 236, 255)


def marrow_cleansing_pill():
    img = Image(16, 16)
    cx, cy, radius = 7.5, 8.0, 5.7

    def inside(x, y):
        return math.hypot(x - cx, y - cy) <= radius

    # Body: a round pill lit from the top left.
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            dx, dy = (x - cx) / radius, (y - cy) / radius
            light = -dx * 0.55 - dy * 0.75
            if light > 0.55:
                color = BODY_LIGHT
            elif light > -0.15:
                color = BODY
            elif light > -0.6:
                color = BODY_DARK
            else:
                color = SHADOW
            img.set(x, y, color)

    # A jade band of purified marrow curling across the pill (an S like the yin-yang line).
    for x in range(16):
        t = (x - cx) / radius
        band = cy - 0.5 + math.sin(t * math.pi) * 1.3
        for y in range(16):
            if not inside(x, y):
                continue
            d = y - band
            if -0.5 <= d < 0.5:
                img.set(x, y, JADE_LIGHT if x < cx - 1 else JADE)
            elif 0.5 <= d < 1.5:
                img.set(x, y, JADE if x < cx - 1 else JADE_DARK)

    # Dark impurities being pushed out at the lower right.
    for (x, y, c) in [(10, 11, IMPURITY), (11, 10, IMPURITY_LIGHT), (8, 12, IMPURITY), (11, 12, IMPURITY_LIGHT), (9, 11, IMPURITY_LIGHT)]:
        if inside(x, y):
            img.set(x, y, c)

    # Outline: every transparent pixel next to the pill.
    body = [[inside(x, y) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if body[y][x]:
                continue
            near = False
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + ox, y + oy
                if 0 <= nx < 16 and 0 <= ny < 16 and body[ny][nx]:
                    near = True
            if near:
                img.set(x, y, OUTLINE)

    # Shine on the upper left.
    for (x, y) in [(5, 4), (4, 5), (5, 5), (6, 4)]:
        img.set(x, y, HIGHLIGHT)
    img.set(4, 6, BODY_LIGHT)
    img.set(7, 4, BODY_LIGHT)

    # Small qi sparkle at the top right.
    img.set(13, 1, SPARK)
    img.set(12, 2, SPARK)
    img.set(14, 2, SPARK)
    img.set(13, 3, SPARK)
    img.set(13, 2, SPARK_CORE)

    out = os.path.join(ROOT, "textures", "item")
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, "marrow_cleansing_pill.png"))


if __name__ == "__main__":
    marrow_cleansing_pill()
