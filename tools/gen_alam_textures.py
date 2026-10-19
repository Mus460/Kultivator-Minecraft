"""Generates the item textures, item definitions and models of phase-8 module P8-alam (16x16 pixel art).

  token_darah_api.png       Token Ujian Darah-Api: a blood-red bronze token with a flame mark
  heavenvoid_fragment.png   Fragmen Peta Hampa Langit: a torn blue-grey map scrap with ink lines
  heavenvoid_map.png        Peta Aula Hampa Langit: a rolled map tied with gold
  cold_ice_pearl.png        Mutiara Es Dingin: a pale icy pearl with a frost glint
  soul_nurturing_wood.png   Kayu Pemelihara Jiwa: a pale wood branch with green soul rings
  inheritance_crystal.png   Kristal Es Warisan: a tall ice crystal with a star inside
  ancient_god_blood.png     Darah Dewa Kuno: a jade vial of gold-flecked crimson blood
  tomb_sigil.png            Lencana Makam Langit: a violet hexagonal badge with a gold eye
  tomb_essence.png          Esensi Energi Makam: a violet energy wisp
  heavenvoid_cauldron.png   Kuali Hampa Langit: a dark cauldron with a sealed lid and blue flame

Run with: python tools/gen_alam_textures.py [preview_dir]
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
SIZE = 16


def rgb(hex_value, alpha=255):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, alpha)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(base, light):
    if light >= 0:
        return mix(base, (255, 255, 255), light * 0.55)
    return mix(base, (base[0] * 0.25, base[1] * 0.22, base[2] * 0.35), -light * 0.75)


def fill(img, inside, base, cx, cy, radius):
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            dx, dy = (x - cx) / radius, (y - cy) / radius
            light = -dx * 0.55 - dy * 0.75
            level = 0.7 if light > 0.55 else 0.2 if light > 0.1 else -0.25 if light > -0.45 else -0.6
            img.set(x, y, shade(base, level))


def outline(img, inside, color):
    body = [[inside(x, y) for x in range(SIZE)] for y in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if body[y][x]:
                continue
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + ox, y + oy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and body[ny][nx]:
                    img.set(x, y, color)
                    break


def sparkle(img, x, y, color, core=(255, 255, 255, 255)):
    for ox, oy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
        img.blend(x + ox, y + oy, color)
    img.set(x, y, core)


def disc(cx, cy, r):
    return lambda x, y: (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r


def token():
    img = Image(SIZE, SIZE)
    inside = disc(8, 8, 6.4)
    fill(img, inside, rgb(0xB0402A), 8, 8, 6.4)
    outline(img, inside, rgb(0x3A1008))
    ring = lambda x, y: 4.6 ** 2 <= (x + 0.5 - 8) ** 2 + (y + 0.5 - 8) ** 2 <= 5.4 ** 2
    for y in range(SIZE):
        for x in range(SIZE):
            if ring(x, y):
                img.set(x, y, rgb(0xE0A040))
    flame = [(8, 4), (7, 5), (8, 5), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (9, 8), (8, 9), (7, 9), (8, 10)]
    for i, (x, y) in enumerate(flame):
        img.set(x, y, rgb(0xFFD040) if y < 7 else rgb(0xFF7020))
    img.set(8, 8, rgb(0xFFF0A0))
    return img


def fragment():
    img = Image(SIZE, SIZE)
    pts = [(2, 4), (6, 2), (8, 4), (12, 2), (14, 7), (12, 9), (13, 13), (7, 14), (5, 11), (2, 12)]

    def inside(x, y):
        px, py = x + 0.5, y + 0.5
        n = len(pts)
        hit = False
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % n]
            if (ay > py) != (by > py) and px < (bx - ax) * (py - ay) / (by - ay) + ax:
                hit = not hit
        return hit
    fill(img, inside, rgb(0xD8CCA8), 8, 8, 7)
    outline(img, inside, rgb(0x4A3A22))
    for x in range(4, 12):
        if inside(x, 6):
            img.set(x, 6, rgb(0x3A5A8A))
    for x in range(4, 12, 2):
        if inside(x, 9):
            img.set(x, 9, rgb(0x3A5A8A))
    img.set(9, 11, rgb(0xD04030))
    img.set(10, 11, rgb(0xD04030))
    return img


def map_item():
    img = Image(SIZE, SIZE)
    inside = lambda x, y: 2 <= x <= 13 and 4 <= y <= 11
    fill(img, inside, rgb(0x6E9AD0), 8, 8, 7)
    outline(img, inside, rgb(0x1A2A44))
    for y in range(4, 12):
        img.set(2, y, rgb(0x3A2A1A))
        img.set(13, y, rgb(0x3A2A1A))
    for x in range(4, 12):
        img.set(x, 6 + (x % 3 == 0), rgb(0xE8F0FF))
    for y in range(3, 13):
        img.set(8, y, rgb(0xE0B040))
    img.set(8, 8, rgb(0xFFF0A0))
    sparkle(img, 11, 9, (255, 230, 150, 160))
    return img


def pearl():
    img = Image(SIZE, SIZE)
    inside = disc(8, 8.5, 5.2)
    fill(img, inside, rgb(0xB8E8FF), 8, 8.5, 5.2)
    outline(img, inside, rgb(0x2A5A7A))
    img.set(6, 6, (255, 255, 255, 255))
    img.set(7, 6, (240, 252, 255, 255))
    img.set(6, 7, (240, 252, 255, 255))
    sparkle(img, 12, 3, (200, 240, 255, 180))
    sparkle(img, 3, 13, (200, 240, 255, 140))
    return img


def wood():
    img = Image(SIZE, SIZE)

    def inside(x, y):
        d = abs((x - 2) - (13 - y))
        return d <= 2 and 2 <= x <= 13 and 2 <= y <= 13
    fill(img, inside, rgb(0xD8C8A0), 8, 8, 7)
    outline(img, inside, rgb(0x4A3A20))
    for i in range(3, 13, 3):
        img.set(i, 15 - i - 1, rgb(0x8A7048))
    for (x, y) in ((4, 4), (11, 11), (12, 5)):
        sparkle(img, x, y, (120, 255, 170, 170), (210, 255, 225, 255))
    return img


def crystal():
    img = Image(SIZE, SIZE)

    def inside(x, y):
        cx = 7.5
        if y < 1 or y > 14:
            return False
        half = (y - 1) * 0.55 if y < 6 else 2.8 if y < 11 else (14 - y) * 0.95
        return abs(x - cx) <= half
    fill(img, inside, rgb(0x8FE6FF), 7.5, 8, 7)
    outline(img, inside, rgb(0x1C4E66))
    for y in range(3, 13):
        img.blend(6, y, (255, 255, 255, 110))
    sparkle(img, 8, 8, (255, 240, 200, 200), (255, 250, 220, 255))
    return img


def blood():
    img = Image(SIZE, SIZE)
    body = disc(8, 10, 4.6)
    neck = lambda x, y: 7 <= x <= 8 and 3 <= y <= 6
    inside = lambda x, y: body(x, y) or neck(x, y)
    fill(img, inside, rgb(0xA8D8C0), 8, 9, 6)
    outline(img, inside, rgb(0x1E3A30))
    liquid = disc(8, 10.5, 3.6)
    for y in range(SIZE):
        for x in range(SIZE):
            if liquid(x, y) and y >= 9:
                img.set(x, y, mix(rgb(0xC01828), rgb(0x500810), (y - 9) / 5.0))
    for (x, y) in ((7, 11), (9, 12), (8, 10)):
        img.set(x, y, rgb(0xFFD040))
    img.set(7, 2, rgb(0x8A5A2A))
    img.set(8, 2, rgb(0x8A5A2A))
    return img


def sigil():
    img = Image(SIZE, SIZE)

    def inside(x, y):
        px, py = x + 0.5 - 8, y + 0.5 - 8
        for k in range(6):
            ang = math.pi / 3 * k
            if px * math.cos(ang) + py * math.sin(ang) > 6.0:
                return False
        return True
    fill(img, inside, rgb(0x8A5AD0), 8, 8, 7)
    outline(img, inside, rgb(0x2A1050))
    eye = disc(8, 8, 2.6)
    for y in range(SIZE):
        for x in range(SIZE):
            if eye(x, y):
                img.set(x, y, rgb(0xE8B840))
    img.set(8, 8, rgb(0x2A1050))
    img.set(7, 8, rgb(0x2A1050))
    return img


def essence():
    img = Image(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - 8, (y + 0.5 - 9) * 1.1)
            wobble = 0.8 * math.sin((y + x * 0.5) * 1.3)
            if d < 5.0 + wobble * 0.3:
                t = d / 5.2
                img.set(x, y, mix(rgb(0xF0D8FF), rgb(0x7A3AC0), t))
    for y in range(1, 6):
        img.blend(8 + (y % 2), y, (210, 160, 255, 200 - y * 25))
    sparkle(img, 7, 8, (255, 255, 255, 160))
    return img


def cauldron():
    img = Image(SIZE, SIZE)
    pot = lambda x, y: (x + 0.5 - 8) ** 2 / 36.0 + (y + 0.5 - 10) ** 2 / 16.0 <= 1.0 and y >= 6
    lid = lambda x, y: 3 <= x <= 12 and 4 <= y <= 5
    knob = lambda x, y: 7 <= x <= 8 and y == 3
    inside = lambda x, y: pot(x, y) or lid(x, y) or knob(x, y)
    fill(img, inside, rgb(0x3A4458), 8, 9, 7)
    outline(img, inside, rgb(0x10141E))
    for x in range(3, 13):
        img.set(x, 5, rgb(0xC8A040))
    for x in (5, 8, 11):
        img.set(x, 10, rgb(0x6FB8FF))
    for (x, y) in ((6, 15), (8, 15), (10, 15), (7, 14), (9, 14)):
        img.set(x, y, rgb(0x4AA8FF))
    sparkle(img, 12, 2, (120, 200, 255, 170))
    return img


TEXTURES = {
    "token_darah_api": token,
    "heavenvoid_fragment": fragment,
    "heavenvoid_map": map_item,
    "cold_ice_pearl": pearl,
    "soul_nurturing_wood": wood,
    "inheritance_crystal": crystal,
    "ancient_god_blood": blood,
    "tomb_sigil": sigil,
    "tomb_essence": essence,
    "heavenvoid_cauldron": cauldron,
}


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    tex_dir = os.path.join(ROOT, "textures", "item")
    items_dir = os.path.join(ROOT, "items")
    models_dir = os.path.join(ROOT, "models", "item")
    for d in (tex_dir, items_dir, models_dir):
        os.makedirs(d, exist_ok=True)
    if preview:
        os.makedirs(preview, exist_ok=True)
    for name, make in TEXTURES.items():
        img = make()
        img.save(os.path.join(tex_dir, name + ".png"))
        with open(os.path.join(items_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}}, f, indent=2)
            f.write("\n")
        with open(os.path.join(models_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}}, f, indent=2)
            f.write("\n")
        if preview:
            big = Image(SIZE * 8, SIZE * 8)
            for y in range(SIZE * 8):
                for x in range(SIZE * 8):
                    px = img.get(x // 8, y // 8)
                    big.set(x, y, px if px[3] > 0 else (40, 44, 52, 255))
            big.save(os.path.join(preview, name + ".png"))
    print("wrote", len(TEXTURES), "items")


if __name__ == "__main__":
    main()
