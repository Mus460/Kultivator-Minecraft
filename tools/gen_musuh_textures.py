"""Generates the textures of module P8-musuh, rogue cultivators and sect raids (DESIGN 25.4).

Item textures (16x16):
  loot_pouch.png          Kantong Rampasan: a worn leather drawstring pouch with a frayed cord and a red paper tag
  demonic_loot_pouch.png  Kantong Rampasan Sekte Iblis: a black silk pouch with a blood-red seal and a gold cord
  demonic_banner.png      Panji Sekte Iblis: a black war banner on a dark pole with a blood moon and torn tails

Entity texture (64x64, laid out like a skin's head, used by RogueMaskLayer's head shell):
  rogue_mask.png          a greyscale cloth mask over nose and mouth with straps round the head and a knot at the back;
                          the renderer tints it with the tier's trim colour

Run with: python tools/gen_musuh_textures.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
ITEM = os.path.join(ROOT, "item")
ENTITY = os.path.join(ROOT, "entity")


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (255,)


OUTLINE = (30, 18, 12)


def pouch(path, body, light, dark, cord, tag, tag_dark, seed):
    img = Image(16, 16)
    # Body: a rounded sack from row 6 to row 14, widest at row 11.
    widths = {5: 3, 6: 4, 7: 5, 8: 6, 9: 6, 10: 7, 11: 7, 12: 7, 13: 6, 14: 5}
    for y, half in widths.items():
        for x in range(8 - half, 8 + half):
            t = (x - (8 - half)) / max(1, 2 * half - 1)
            f = 1.15 - 0.45 * t + (noise(x, y, seed) - 0.5) * 0.18
            if y >= 13:
                f *= 0.85
            img.set(x, y, shade(body, f))
        img.set(8 - half - 1, y, OUTLINE + (255,))
        img.set(8 + half, y, OUTLINE + (255,))
    for x in range(3, 13):
        img.set(x, 15, OUTLINE + (255,))
    # Neck gathered by the cord, a puffed mouth above.
    for x in range(6, 10):
        img.set(x, 4, shade(light, 1.0))
        img.set(x, 3, shade(body, 0.9))
    img.set(5, 3, OUTLINE + (255,))
    img.set(10, 3, OUTLINE + (255,))
    img.set(5, 4, OUTLINE + (255,))
    img.set(10, 4, OUTLINE + (255,))
    for x in range(6, 10):
        img.set(x, 2, OUTLINE + (255,))
    for x in range(5, 11):
        img.set(x, 5, shade(cord, 1.0 if x % 2 else 0.8))
    # Cord tails hanging to the right.
    img.set(11, 6, shade(cord, 1.0))
    img.set(12, 7, shade(cord, 0.85))
    img.set(12, 8, shade(cord, 1.0))
    # Folds and a highlight.
    for y in range(8, 13):
        img.set(5, y, shade(light, 1.0 - 0.05 * (y - 8)))
    img.set(9, 9, shade(dark, 1.0))
    img.set(10, 10, shade(dark, 1.0))
    img.set(9, 12, shade(dark, 1.0))
    # A paper tag (or seal) on the front.
    for y in range(9, 13):
        for x in range(6, 9):
            img.set(x, y, shade(tag, 1.05 - 0.05 * (y - 9)))
    img.set(7, 10, shade(tag_dark, 1.0))
    img.set(7, 11, shade(tag_dark, 1.0))
    img.save(path)


def banner(path):
    img = Image(16, 16)
    pole = (58, 40, 30)
    cloth = (24, 20, 26)
    moon = (186, 22, 26)
    gold = (210, 168, 70)
    # The pole: a diagonal-free upright at x 3, with a gold finial and a cross bar.
    for y in range(1, 16):
        img.set(3, y, shade(pole, 1.1 if y % 3 else 0.9))
        img.set(2, y, OUTLINE + (255,))
    img.set(3, 0, gold + (255,))
    img.set(2, 0, shade(gold, 0.7))
    for x in range(3, 14):
        img.set(x, 2, shade(pole, 1.0))
    # The cloth hanging from the bar with two torn tails.
    for y in range(3, 13):
        for x in range(4, 14):
            if y >= 11 and x in (8, 9):
                continue
            if y == 12 and x in (4, 13):
                continue
            f = 1.0 + (noise(x, y, 7) - 0.5) * 0.3 - 0.03 * (y - 3)
            img.set(x, y, shade(cloth, f))
    for y in range(3, 13):
        img.set(14, y, OUTLINE + (255,))
    for x in range(4, 14):
        if x not in (8, 9):
            img.set(x, 13, OUTLINE + (255,))
    # The blood moon with a dark eclipse bite.
    cx, cy = 9.0, 7.0
    for y in range(4, 11):
        for x in range(6, 13):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 2.6:
                bite = ((x - 10.2) ** 2 + (y - 6.2) ** 2) ** 0.5
                if bite > 1.6:
                    img.set(x, y, shade(moon, 1.15 - 0.12 * d))
    # Gold trim along the top.
    for x in range(4, 14):
        img.set(x, 3, shade(gold, 0.85 if x % 2 else 1.0))
    img.save(path)


def mask(path):
    img = Image(64, 64)
    white = (236, 236, 236)

    def cloth(x, y, f):
        g = f + (noise(x, y, 3) - 0.5) * 0.12
        img.set(x, y, shade(white, g))

    # Head box at texOffs(0, 0), 8x8x8: right (0..8, 8..16), front (8..16, 8..16), left (16..24), back (24..32).
    # Front: the cloth from the nose down (rows 12..15), a folded top edge on row 12.
    for x in range(8, 16):
        for y in range(12, 16):
            cloth(x, y, 0.95 if y == 12 else 0.85 - 0.04 * (y - 13))
        img.set(x, 12, shade(white, 1.0))
    # A dark seam down the middle of the front.
    for y in range(13, 16):
        img.set(11, y, shade(white, 0.62))
        img.set(12, y, shade(white, 0.7))
    # Sides: the cloth narrows into a strap (rows 12..14 near the face, row 13 further back).
    for x in range(0, 8):
        rows = (12, 13, 14) if x >= 5 else (13,)
        for y in rows:
            cloth(x, y, 0.8)
    for x in range(16, 24):
        rows = (12, 13, 14) if x <= 18 else (13,)
        for y in rows:
            cloth(x, y, 0.8)
    # Back: the strap and a knot with two hanging ends.
    for x in range(24, 32):
        cloth(x, 13, 0.75)
    for x, y in ((27, 12), (28, 12), (27, 13), (28, 13), (27, 14), (28, 14), (26, 15), (29, 15)):
        cloth(x, y, 0.65)
    # Bottom face: the cloth's lower edge under the chin (16..24, 0..8): only the front rows.
    for x in range(16, 24):
        for y in range(6, 8):
            cloth(x, y, 0.7)
    img.save(path)


def main():
    os.makedirs(ITEM, exist_ok=True)
    os.makedirs(ENTITY, exist_ok=True)
    pouch(os.path.join(ITEM, "loot_pouch.png"), (132, 92, 58), (170, 128, 86), (92, 62, 38), (200, 180, 130), (196, 52, 40), (120, 24, 18), 11)
    pouch(os.path.join(ITEM, "demonic_loot_pouch.png"), (40, 30, 40), (76, 58, 78), (20, 14, 22), (214, 170, 72), (170, 18, 22), (240, 200, 90), 23)
    banner(os.path.join(ITEM, "demonic_banner.png"))
    mask(os.path.join(ENTITY, "rogue_mask.png"))
    print("done")


if __name__ == "__main__":
    main()
