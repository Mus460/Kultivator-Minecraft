"""Generates the item textures of the disciple module (16x16 pixel art).

recruitment_scroll.png: Gulungan Penerimaan Murid, an unrolled parchment scroll between two dark wooden rods with gold
caps, three columns of ink calligraphy, an amethyst bead hanging from the right rod and a red sect seal in the lower
corner.

disciple_tablet.png: Papan Jiwa Murid, an upright ancestral soul tablet of dark red lacquered wood with a gold rim, a
pale inscription panel carrying a glowing jade soul glyph and a small soul flame floating above the arched top.

Run with: python tools/gen_disciple_textures.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")

# Shared palette: one character per colour, "." is transparent.
PALETTE = {
    ".": None,
    # wood
    "W": (58, 32, 22, 255),     # rod / tablet outline
    "w": (104, 58, 36, 255),    # rod body
    "v": (146, 88, 52, 255),    # rod highlight
    # gold
    "G": (150, 102, 24, 255),
    "g": (222, 172, 56, 255),
    "y": (255, 228, 128, 255),
    # parchment
    "P": (170, 136, 86, 255),   # parchment shadow / edge
    "p": (226, 200, 146, 255),
    "q": (246, 228, 186, 255),  # parchment light
    # ink
    "K": (40, 30, 34, 255),
    "k": (92, 74, 70, 255),
    # seal
    "R": (150, 22, 26, 255),
    "r": (214, 44, 40, 255),
    # amethyst bead
    "A": (110, 66, 170, 255),
    "a": (186, 140, 255, 255),
    # lacquer
    "L": (70, 14, 18, 255),
    "l": (118, 26, 28, 255),
    "m": (156, 44, 40, 255),
    # inscription panel
    "S": (196, 170, 120, 255),
    "s": (232, 214, 170, 255),
    # jade soul light
    "J": (40, 150, 140, 255),
    "j": (90, 230, 210, 255),
    "h": (210, 255, 248, 255),
    # soft glow (translucent)
    "o": (120, 255, 230, 120),
    "x": (160, 255, 240, 70),
}

SCROLL = [
    "................",
    "..y.........y...",
    ".gGg.......gGg..",
    ".WvW.......WvW..",
    ".WvWPPPPPPPWvW..",
    ".WvWqqpqqpqWvW..",
    ".WwWqKqKqqpWwW..",
    ".WwWpKqkqKqWwW..",
    ".WwWqkqKqKqWwW..",
    ".WwWpKqqrRpWwW..",
    ".WwWqkqqRrpWwW..",
    ".WwWPPPPPPPWwW..",
    ".WwW.......WwW..",
    ".gGg.......gGg..",
    "..y.........G...",
    "............a...",
]

TABLET = [
    ".......x........",
    "......xox.......",
    "......ojo.......",
    ".......h........",
    ".....gggggg.....",
    "....gmmmmmmg....",
    "....glssssLg....",
    "....glsjjsLg....",
    "....glshjsLg....",
    "....glsJjsLg....",
    "....glssjsLg....",
    "....glsSSsLg....",
    "....gllllLLg....",
    "...GggggggggG...",
    "...GWWWWWWWWG...",
    "................",
]


def draw(rows):
    img = Image(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            color = PALETTE[ch]
            if color is None:
                continue
            if color[3] < 255:
                img.blend(x, y, color)
            else:
                img.set(x, y, color)
    return img


def recruitment_scroll():
    img = draw(SCROLL)
    # The amethyst bead hanging from the right rod gets a darker lower half and a dark tip.
    img.blend(12, 15, (110, 66, 170, 120))
    return img


def disciple_tablet():
    img = draw(TABLET)
    # A faint jade glow around the soul glyph inside the panel.
    for (x, y) in [(6, 7), (9, 8), (6, 9), (9, 10)]:
        img.blend(x, y, (90, 230, 210, 70))
    return img


def main():
    out_dir = os.path.join(ROOT, "textures", "item")
    os.makedirs(out_dir, exist_ok=True)
    for name, image in (("recruitment_scroll", recruitment_scroll()), ("disciple_tablet", disciple_tablet())):
        path = os.path.join(out_dir, name + ".png")
        image.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
