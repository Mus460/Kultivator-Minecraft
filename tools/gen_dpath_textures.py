"""Generates the item textures of module D, the disciple's path (16x16 pixel art).

soul_seal_jade.png: Giok Segel Jiwa, the Soul Seal jade of Tales of Demons and Gods: a pale green jade disc with a
carved seal glyph (a square knot inside a ring), a darker rim, a highlight at the top left and a red cord with a
tassel hanging from its top.

master_soul_ring.png: Cincin Jiwa Guru, the ring in which the master's soul lives (Yao Lao's ring in Battle Through the
Heavens, Doehring Cowart's Coiling Dragon Ring): a dark gold band seen at an angle with a black stone, and a faint pale
wisp of the old soul rising from the stone.

Run with: python tools/gen_dpath_textures.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
ITEM = os.path.join(ROOT, "textures", "item")


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


# ---------------------------------------------------------------------------------------------- soul seal jade

def soul_seal_jade():
    img = Image(16, 16)
    rim = (38, 112, 78)
    body = (112, 200, 150)
    light = (196, 240, 214)
    carve = (44, 120, 84)
    cx, cy, r = 7.5, 9.0, 5.6
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r + 0.35:
                if d > r - 0.9:
                    img.set(x, y, rim + (255,))
                else:
                    # Soft light from the top left.
                    t = max(0.0, min(1.0, ((x - cx) + (y - cy)) / (2.0 * r) + 0.5))
                    img.set(x, y, mix(light, body, 0.35 + 0.65 * t))
    # The carved glyph: a small ring and a square knot inside it.
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 2.9 <= d <= 3.6:
                img.set(x, y, carve + (255,))
    for x in range(6, 10):
        img.set(x, 9, carve + (255,))
    for y in range(7, 11):
        img.set(7, y, carve + (255,))
        img.set(8, y, carve + (255,)) if y in (8, 9) else None
    # Highlight.
    img.set(4, 6, (235, 255, 242, 255))
    img.set(5, 5, (235, 255, 242, 255))
    img.set(4, 7, (215, 250, 228, 255))
    # The red cord through the top and its tassel to the right.
    cord = (196, 34, 28)
    cord_dark = (120, 16, 14)
    img.set(7, 2, cord + (255,))
    img.set(8, 2, cord + (255,))
    img.set(7, 3, cord_dark + (255,))
    img.set(8, 3, cord + (255,))
    img.set(9, 1, cord + (255,))
    img.set(10, 1, cord + (255,))
    img.set(11, 2, cord + (255,))
    img.set(12, 3, cord_dark + (255,))
    img.set(12, 4, cord + (255,))
    img.set(13, 5, cord + (255,))
    img.set(12, 5, cord_dark + (255,))
    img.set(13, 6, cord_dark + (255,))
    img.set(12, 6, cord + (255,))
    img.set(6, 1, cord + (255,))
    img.set(5, 1, cord_dark + (255,))
    img.save(os.path.join(ITEM, "soul_seal_jade.png"))


# ---------------------------------------------------------------------------------------------- master soul ring

def master_soul_ring():
    img = Image(16, 16)
    gold = (214, 166, 55)
    gold_dark = (128, 92, 26)
    gold_light = (250, 222, 130)
    cx, cy = 7.5, 10.0
    # An ellipse band (the ring seen at an angle).
    for y in range(16):
        for x in range(16):
            ex = (x - cx) / 5.6
            ey = (y - cy) / 3.4
            d = (ex * ex + ey * ey) ** 0.5
            if 0.62 <= d <= 1.06:
                front = y >= cy
                t = max(0.0, min(1.0, (d - 0.62) / 0.44))
                color = mix(gold_light, gold, t) if not front else mix(gold, gold_dark, t * 0.8)
                if not front and d > 0.95:
                    color = gold_dark + (255,)
                img.set(x, y, color)
    # The black stone on the front of the band.
    for (x, y) in ((7, 12), (8, 12), (7, 13), (8, 13), (6, 12), (9, 12)):
        img.set(x, y, (24, 18, 30, 255))
    img.set(7, 12, (90, 80, 120, 255))
    img.set(6, 13, gold_dark + (255,))
    img.set(9, 13, gold_dark + (255,))
    # The old soul's wisp rising from the stone.
    wisp = [(8, 5, 200), (7, 5, 120), (7, 4, 190), (8, 3, 170), (8, 2, 150), (7, 2, 90), (7, 1, 120), (6, 1, 60),
            (9, 3, 70), (6, 4, 60)]
    for (x, y, a) in wisp:
        img.blend(x, y, (226, 244, 252, a))
    # Glints.
    img.set(3, 8, gold_light + (255,))
    img.set(11, 7, (255, 244, 200, 255))
    img.save(os.path.join(ITEM, "master_soul_ring.png"))


if __name__ == "__main__":
    os.makedirs(ITEM, exist_ok=True)
    soul_seal_jade()
    master_soul_ring()
    print("module D textures written")
