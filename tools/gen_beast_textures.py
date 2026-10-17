"""Generates the item texture of the spirit beast module: the Spirit Beast Pill (16x16 pixel art).

A round amber pill (the colour of a fox's fur) with a pale moon-blue paw print in the middle, lit from the top left,
with a small qi sparkle. Run with: python tools/gen_beast_textures.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")

OUTLINE = (58, 30, 20, 255)
SHADOW = (150, 72, 28, 255)
BODY_DARK = (200, 104, 40, 255)
BODY = (232, 146, 62, 255)
BODY_LIGHT = (250, 190, 110, 255)
HIGHLIGHT = (255, 244, 214, 255)
PAW_DARK = (96, 142, 196, 255)
PAW = (168, 210, 255, 255)
PAW_LIGHT = (226, 242, 255, 255)
SPARK = (190, 226, 255, 255)
SPARK_CORE = (255, 255, 255, 255)


def spirit_beast_pill():
    img = Image(16, 16)
    cx, cy, radius = 7.5, 8.0, 5.8

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

    # Paw print: a pad and four toes, moon blue with a darker lower edge.
    pad = [(6, 9), (7, 9), (8, 9), (9, 9),
           (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10),
           (6, 11), (7, 11), (8, 11), (9, 11)]
    for (x, y) in pad:
        img.set(x, y, PAW)
    for (x, y) in [(6, 11), (7, 11), (8, 11), (9, 11), (10, 10)]:
        img.set(x, y, PAW_DARK)
    for (x, y) in [(6, 9), (7, 9), (5, 10)]:
        img.set(x, y, PAW_LIGHT)
    # Four toes in an arc above the pad: two tall inner ones, two outer ones a little lower.
    for (x, y) in [(6, 5), (6, 6), (9, 5), (9, 6), (4, 7), (4, 8), (11, 7), (11, 8)]:
        img.set(x, y, PAW)
    for (x, y) in [(6, 5), (4, 7), (9, 5)]:
        img.set(x, y, PAW_LIGHT)
    for (x, y) in [(11, 8), (9, 6)]:
        img.set(x, y, PAW_DARK)

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
    for (x, y) in [(4, 4), (5, 4), (4, 5)]:
        img.set(x, y, HIGHLIGHT)
    img.set(6, 4, BODY_LIGHT)
    img.set(3, 6, BODY_LIGHT)

    # Small spirit sparkle at the top right.
    img.set(13, 1, SPARK)
    img.set(12, 2, SPARK)
    img.set(14, 2, SPARK)
    img.set(13, 3, SPARK)
    img.set(13, 2, SPARK_CORE)

    out = os.path.join(ROOT, "textures", "item")
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, "spirit_beast_pill.png"))


if __name__ == "__main__":
    spirit_beast_pill()
