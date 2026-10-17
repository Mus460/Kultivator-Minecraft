"""Generates the textures of module A, the Sect Hall (16x16 pixel art).

Block textures of Prasasti Aula Sekte (the Xiao clan's testing stone monument of Battle Through the Heavens: a jade
tablet with a gold cap on a stone foot). The block model maps them like this:
  sect_hall_stele_front.png  the jade tablet's front and back (uv 4,2 .. 12,13): a gold frame, a round seal at the top and
                             a column of engraved glyphs that glows faintly gold
  sect_hall_stele_side.png   the tablet's narrow sides (uv 6,2 .. 10,13): jade with a dark edge and a vein of light
  sect_hall_stele_cap.png    the gold cap (rim rows 0-1, top face 3,5 .. 13,11 with a small jade gem)
  sect_hall_stele_base.png   the carved stone foot (sides rows 13-15, top 2,3 .. 14,13)

Item textures:
  immortal_ascension_token.png  Token Kenaikan Abadi: an upright gold-rimmed jade command token with a glyph and a red tassel
  lost_scripture_page.png       Lembar Kitab yang Hilang: a torn, yellowed page with violet ink and a small plum blossom

Run with: python tools/gen_hall_textures.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
BLOCK = os.path.join(ROOT, "block")
ITEM = os.path.join(ROOT, "item")


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (255,)


def noise(x, y, seed):
    """Deterministic 0..1 noise per pixel."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


JADE_LIGHT = (150, 226, 190)
JADE = (70, 170, 128)
JADE_DARK = (30, 96, 70)
JADE_DEEP = (18, 60, 44)
GOLD_LIGHT = (255, 232, 140)
GOLD = (222, 172, 60)
GOLD_DARK = (138, 98, 26)
STONE_LIGHT = (176, 176, 168)
STONE = (134, 134, 128)
STONE_DARK = (88, 88, 84)
SEAL_RED = (178, 40, 30)


def jade_fill(img, seed):
    """Jade with soft clouds: lighter towards the top-left, flecks of pale and deep green."""
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            base = mix(JADE_LIGHT, JADE, 0.35 + 0.5 * t)
            n = noise(x, y, seed)
            if n > 0.86:
                base = mix(base, JADE_LIGHT, 0.45)
            elif n < 0.12:
                base = mix(base, JADE_DARK, 0.35)
            img.set(x, y, base)


def front():
    img = Image(16, 16)
    jade_fill(img, 11)
    # The visible face is x 4..11, y 2..12: a gold frame around it.
    for x in range(4, 12):
        img.set(x, 2, GOLD if x not in (4, 11) else GOLD_DARK)
        img.set(x, 12, GOLD_DARK)
    for y in range(2, 13):
        img.set(4, y, GOLD_DARK if y in (2, 12) else GOLD)
        img.set(11, y, GOLD_DARK)
    # Inner shadow under the top rim.
    for x in range(5, 11):
        img.set(x, 3, mix(img.get(x, 3), JADE_DARK, 0.35))
    # A round red seal near the top.
    for (x, y) in ((7, 4), (8, 4), (7, 5), (8, 5)):
        img.set(x, y, SEAL_RED)
    img.set(7, 4, shade(SEAL_RED, 1.25))
    # A column of engraved glyphs (deep jade cut, gold-lit lower edge), testing-stone style.
    glyphs = [
        "##.##",
        "..#..",
        "#####",
        ".....",
        "#.#.#",
        ".###.",
        ".....",
    ]
    for gy, row in enumerate(glyphs):
        for gx, ch in enumerate(row):
            if ch == "#":
                px, py = 5 + gx, 6 + gy
                if px < 11 and py < 12:
                    img.set(px, py, JADE_DEEP)
                    if py + 1 < 12 and row is not None:
                        below = glyphs[gy + 1][gx] if gy + 1 < len(glyphs) else "."
                        if below != "#":
                            img.set(px, py + 1, mix(img.get(px, py + 1), GOLD_LIGHT, 0.45))
    # Highlights at the top-left of the face.
    img.set(5, 3, mix(JADE_LIGHT, (255, 255, 255), 0.4))
    img.set(6, 3, mix(JADE_LIGHT, (255, 255, 255), 0.2))
    img.save(os.path.join(BLOCK, "sect_hall_stele_front.png"))


def side():
    img = Image(16, 16)
    jade_fill(img, 23)
    # Visible strip x 6..9, y 2..12: dark edges, a pale vein in the middle.
    for y in range(16):
        img.set(6, y, mix(img.get(6, y), JADE_DARK, 0.45))
        img.set(9, y, mix(img.get(9, y), JADE_DEEP, 0.5))
        if y % 5 != 4:
            img.set(7, y, mix(img.get(7, y), JADE_LIGHT, 0.5))
    img.save(os.path.join(BLOCK, "sect_hall_stele_side.png"))


def cap():
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            c = mix(GOLD_LIGHT, GOLD, 0.3 + 0.6 * t)
            if noise(x, y, 37) > 0.88:
                c = mix(c, GOLD_LIGHT, 0.6)
            img.set(x, y, c)
    # Rim rows 0-1: a bright upper line and a dark lower line.
    for x in range(16):
        img.set(x, 0, GOLD_LIGHT)
        img.set(x, 1, GOLD_DARK)
    # Top face 3..12 x 5..10: an engraved border and a jade gem in the middle.
    for x in range(3, 13):
        img.set(x, 5, GOLD_DARK)
        img.set(x, 10, GOLD_DARK)
    for y in range(5, 11):
        img.set(3, y, GOLD_DARK)
        img.set(12, y, GOLD_DARK)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        img.set(x, y, JADE)
    img.set(7, 7, JADE_LIGHT)
    img.set(8, 8, JADE_DARK)
    img.save(os.path.join(BLOCK, "sect_hall_stele_cap.png"))


def base():
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            c = mix(STONE_LIGHT, STONE, 0.4 + 0.4 * noise(x, y, 51))
            img.set(x, y, c)
    # Carved blocks: mortar lines.
    for x in range(16):
        img.set(x, 7, STONE_DARK)
        img.set(x, 15, STONE_DARK)
    for y in range(16):
        if y < 7:
            img.set(5, y, STONE_DARK)
            img.set(11, y, STONE_DARK)
        else:
            img.set(2, y, STONE_DARK)
            img.set(8, y, STONE_DARK)
            img.set(14, y, STONE_DARK)
    # The foot's side rows 13-15: a bright top edge and a carved cloud band.
    for x in range(16):
        img.set(x, 13, STONE_LIGHT)
        img.set(x, 14, mix(STONE, STONE_DARK, 0.3) if x % 4 in (1, 2) else STONE)
    # A jade inlay ring on the top face where the tablet stands.
    for x in range(3, 13):
        img.set(x, 5, mix(STONE_DARK, JADE_DARK, 0.4))
        img.set(x, 10, mix(STONE_DARK, JADE_DARK, 0.4))
    img.save(os.path.join(BLOCK, "sect_hall_stele_base.png"))


def token():
    img = Image(16, 16)
    # An upright command token, rounded at the top: x 4..11, y 1..12.
    for y in range(1, 13):
        for x in range(4, 12):
            if y == 1 and x in (4, 5, 10, 11):
                continue
            if y == 2 and x in (4, 11):
                continue
            edge = x in (4, 11) or y in (1, 12) or (y == 2 and x in (5, 10))
            if edge:
                img.set(x, y, GOLD_DARK if x == 11 or y == 12 else GOLD)
            elif x in (5, 10) or y in (2, 11):
                img.set(x, y, GOLD_LIGHT if x == 5 or y == 2 else GOLD)
            else:
                t = (x + y) / 22.0
                img.set(x, y, mix(JADE_LIGHT, JADE, 0.2 + 0.8 * t))
    # The glyph: a gold "ascend" mark.
    for (x, y) in ((7, 4), (8, 4), (6, 5), (9, 5), (7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9)):
        img.set(x, y, GOLD_DARK)
    img.set(7, 4, GOLD_LIGHT)
    # A red cord and tassel below.
    img.set(7, 13, SEAL_RED)
    img.set(8, 13, shade(SEAL_RED, 0.8))
    for y in (14, 15):
        for x in (6, 7, 8, 9):
            if (x + y) % 2 == 0 or y == 14:
                img.set(x, y, SEAL_RED if x < 8 else shade(SEAL_RED, 0.75))
    img.save(os.path.join(ITEM, "immortal_ascension_token.png"))


def page():
    img = Image(16, 16)
    paper = (232, 214, 160)
    paper_dark = (190, 164, 104)
    ink = (110, 60, 150)
    # A slightly skewed page with torn edges: x from 3..12 shifting one pixel to the right halfway down.
    for y in range(1, 15):
        shift = 1 if y >= 8 else 0
        left = 3 + shift + (1 if noise(0, y, 71) > 0.7 else 0)
        right = 12 + shift - (1 if noise(1, y, 71) > 0.75 else 0)
        for x in range(left, right + 1):
            c = mix(paper, paper_dark, 0.15 + 0.35 * noise(x, y, 73))
            if x in (left, right) or y in (1, 14):
                c = mix(c, paper_dark, 0.6)
            img.set(x, y, c)
    # Torn bottom-right corner.
    for (x, y) in ((13, 14), (13, 13), (12, 14)):
        img.set(x, y, (0, 0, 0, 0))
    # Violet ink lines (columns of script) and a plum blossom.
    for y in (3, 5, 7, 9, 11):
        shift = 1 if y >= 8 else 0
        for x in range(5 + shift, 10 + shift):
            if noise(x, y, 79) > 0.25:
                img.set(x, y, ink)
    for (x, y) in ((10, 10), (11, 11), (9, 11), (10, 12), (10, 11)):
        img.set(x, y, (240, 130, 170) if (x, y) != (10, 11) else (255, 220, 120))
    img.save(os.path.join(ITEM, "lost_scripture_page.png"))


def main():
    os.makedirs(BLOCK, exist_ok=True)
    os.makedirs(ITEM, exist_ok=True)
    front()
    side()
    cap()
    base()
    token()
    page()
    print("hall textures written")


if __name__ == "__main__":
    main()
