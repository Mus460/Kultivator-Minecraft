"""Generates the inner world (P9-batin) item textures and model JSON: the Mutiara Esensi Abadi (a green, grape-like pearl,
RevI ch. 409) and the Token Perluasan Tanah (a jade token carved with a field of four plots).

Run with: python tools/gen_batin_textures.py [preview_dir]
With preview_dir, an 8x enlarged copy of every texture is written there too (for checking by eye).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
PREVIEW = sys.argv[1] if len(sys.argv) > 1 else None


def rgb(hex_value, alpha=255):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, alpha)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def save(img, folder, name):
    path = os.path.join(ROOT, "textures", folder, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        big = Image(img.width * 8, img.height * 8)
        for y in range(img.height * 8):
            for x in range(img.width * 8):
                big.set(x, y, img.get(x // 8, y // 8))
        big.save(os.path.join(PREVIEW, folder + "_" + name + ".png"))


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


GREEN_DARK = rgb(0x1E6B2E)
GREEN = rgb(0x3FBF5A)
GREEN_LIGHT = rgb(0x9CF5A8)
GLOW = rgb(0xE8FFE8)
STEM = rgb(0x6B4A22)
JADE_DARK = rgb(0x184B36)
JADE = rgb(0x2E8963)
JADE_LIGHT = rgb(0x67C99B)
JADE_PALE = rgb(0xB6E3CB)
GOLD = rgb(0xE2B33C)
GOLD_DARK = rgb(0x9A6E1C)
SOIL = rgb(0x6E4E2C)
SPROUT = rgb(0x8CFFB0)


def ball(img, cx, cy, r):
    """A shaded green berry: dark rim, mid body, a light upper-left and a white glint."""
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > r:
                continue
            light = ((cx - (x + 0.5)) + (cy - (y + 0.5))) / (2.0 * r)
            if d > r - 0.9:
                c = GREEN_DARK
            elif light > 0.35:
                c = GREEN_LIGHT
            elif light > -0.15:
                c = GREEN
            else:
                c = mix(GREEN, GREEN_DARK, 0.5)
            img.set(x, y, c)
    gx, gy = int(cx - r * 0.45), int(cy - r * 0.45)
    img.set(gx, gy, GLOW)


def pearl():
    img = Image(16, 16)
    # A little stem and leaf at the top.
    for y in range(1, 4):
        img.set(8, y, STEM)
    img.set(9, 2, SPROUT)
    img.set(10, 2, SPROUT)
    img.set(10, 1, SPROUT)
    # Grape-like: three berries in a cluster, a faint aura around them.
    for (cx, cy, r) in ((8.0, 6.5, 3.3), (5.8, 10.2, 3.2), (10.4, 10.4, 3.2)):
        ball(img, cx, cy, r)
    for y in range(16):
        for x in range(16):
            if img.get(x, y)[3] == 0:
                near = any(0 <= x + dx < 16 and 0 <= y + dy < 16 and img.get(x + dx, y + dy)[3] == 255 and img.get(x + dx, y + dy) != (*STEM[:3], 255)
                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if near and y > 3:
                    img.set(x, y, (156, 245, 168, 70))
    return img


def token():
    img = Image(16, 16)
    # A rounded jade tablet with a gold rim.
    for y in range(1, 15):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (1, 14))
            if corner:
                continue
            edge = x in (2, 13) or y in (1, 14)
            if edge:
                img.set(x, y, GOLD_DARK if (x + y) % 3 == 0 else GOLD)
            else:
                shade = JADE_LIGHT if x < 6 and y < 6 else JADE
                img.set(x, y, shade)
    # Four carved plots (a 2x2 field) with sprouts, and a cord hole on top.
    for (px, py) in ((4, 5), (8, 5), (4, 9), (8, 9)):
        for y in range(py, py + 3):
            for x in range(px, px + 3):
                img.set(x, y, SOIL)
        img.set(px + 1, py, SPROUT)
        img.set(px + 1, py + 1, rgb(0x3FBF5A))
    for x in range(3, 13):
        img.set(x, 8, JADE_DARK)
    for y in range(4, 13):
        img.set(7, y, JADE_DARK)
    img.set(7, 2, JADE_DARK)
    img.set(8, 2, JADE_DARK)
    img.set(3, 2, JADE_PALE)
    return img


def item_json(name):
    write_json(os.path.join("models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}})
    write_json(os.path.join("items", name + ".json"), {"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}})


save(pearl(), "item", "immortal_essence_pearl")
item_json("immortal_essence_pearl")
save(token(), "item", "land_expansion_token")
item_json("land_expansion_token")
print("ok")
