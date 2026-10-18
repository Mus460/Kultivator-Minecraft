"""Generates the phase 7c Mata Formasi (Formation Eye): 16x16 block textures plus its blockstate, models, item
definition, loot table and recipe (DESIGN 24.2.1).

Run with: python tools/gen_formasi_textures.py [preview_dir]
With preview_dir, an 8x enlarged copy of every texture is written there too (for checking by eye).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
DATA = os.path.join(HERE, "..", "src", "main", "resources", "data", "kultivator")
SIZE = 16

STONE_DARK = (34, 30, 44)
STONE = (58, 52, 72)
STONE_LIGHT = (82, 76, 98)
GOLD_DARK = (150, 104, 30)
GOLD = (214, 166, 58)
GOLD_LIGHT = (250, 222, 130)
VIOLET_DARK = (88, 46, 150)
VIOLET = (150, 96, 230)
VIOLET_LIGHT = (214, 180, 255)
EYE_WHITE = (246, 238, 255)


def noise(x, y, seed):
    """Deterministic value noise in 0..1."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def stone(img, seed):
    """Polished dark stone with a soft bevel."""
    for y in range(SIZE):
        for x in range(SIZE):
            n = noise(x, y, seed)
            base = mix(STONE_DARK, STONE, 0.45 + 0.35 * n)
            if x == 0 or y == 0:
                base = mix(base, STONE_LIGHT, 0.55)
            if x == SIZE - 1 or y == SIZE - 1:
                base = mix(base, STONE_DARK, 0.7)
            img.set(x, y, base)


def top():
    img = Image(SIZE, SIZE)
    stone(img, 11)
    cx = cy = 7.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - cx, y - cy)
            angle = math.atan2(y - cy, x - cx)
            # Outer gold ring with eight rune notches (the eight trigram directions).
            if 5.6 <= d < 6.9:
                notch = abs(math.sin(angle * 4.0)) < 0.18
                img.set(x, y, mix(GOLD_DARK, GOLD, 0.35) if notch else mix(GOLD, GOLD_LIGHT, 0.25 if y < 8 else 0.0))
            # Violet channel between ring and eye.
            elif 3.6 <= d < 5.6:
                spoke = abs(math.sin(angle * 4.0)) < 0.22
                img.set(x, y, mix(VIOLET_DARK, VIOLET, 0.55) if spoke else mix(STONE_DARK, VIOLET_DARK, 0.35))
            # The eye: amethyst iris with a bright pupil highlight.
            elif d < 3.6:
                t = d / 3.6
                img.set(x, y, mix(VIOLET_LIGHT, VIOLET, t) if d > 1.2 else EYE_WHITE)
    img.set(6, 6, EYE_WHITE)
    # Corner studs.
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img.set(x, y, GOLD)
    return img


def side():
    img = Image(SIZE, SIZE)
    stone(img, 23)
    for x in range(SIZE):
        img.set(x, 2, mix(GOLD, GOLD_LIGHT, 0.3))
        img.set(x, 3, GOLD_DARK)
        img.set(x, 13, GOLD_DARK)
    # A small rune: a violet diamond with a bright core.
    for y in range(5, 12):
        for x in range(4, 12):
            d = abs(x - 7.5) + abs(y - 8.0)
            if d <= 3.6:
                img.set(x, y, mix(VIOLET, VIOLET_DARK, d / 3.6))
    img.set(7, 8, VIOLET_LIGHT)
    img.set(8, 8, VIOLET_LIGHT)
    return img


def bottom():
    img = Image(SIZE, SIZE)
    stone(img, 37)
    return img


def enlarge(img, factor):
    out = Image(img.width * factor, img.height * factor)
    for y in range(out.height):
        for x in range(out.width):
            out.set(x, y, img.get(x // factor, y // factor))
    return out


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    textures = {"formation_eye_top": top(), "formation_eye_side": side(), "formation_eye_bottom": bottom()}
    os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
    for name, img in textures.items():
        img.save(os.path.join(ASSETS, "textures", "block", name + ".png"))
        if preview:
            os.makedirs(preview, exist_ok=True)
            enlarge(img, 8).save(os.path.join(preview, name + ".png"))

    write_json(os.path.join(ASSETS, "blockstates", "formation_eye.json"),
               {"variants": {"": {"model": "kultivator:block/formation_eye"}}})
    write_json(os.path.join(ASSETS, "models", "block", "formation_eye.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "kultivator:block/formation_eye_top",
            "side": "kultivator:block/formation_eye_side",
            "bottom": "kultivator:block/formation_eye_bottom",
        },
    })
    write_json(os.path.join(ASSETS, "items", "formation_eye.json"),
               {"model": {"type": "minecraft:model", "model": "kultivator:block/formation_eye"}})
    write_json(os.path.join(DATA, "loot_table", "blocks", "formation_eye.json"), {
        "type": "minecraft:block",
        "pools": [{
            "condition": {"type": "minecraft:survives_explosion"},
            "entries": [{"type": "minecraft:item", "name": "kultivator:formation_eye"}],
            "rolls": 1,
        }],
        "random_sequence": "kultivator:blocks/formation_eye",
    })
    write_json(os.path.join(DATA, "recipe", "formation_eye.json"), {
        "type": "minecraft:crafting_shaped",
        "key": {
            "A": "minecraft:amethyst_shard",
            "S": "kultivator:spirit_stone_low",
            "G": "minecraft:gold_ingot",
            "L": "minecraft:lodestone",
        },
        "pattern": ["ASA", "GLG", "ASA"],
        "result": {"id": "kultivator:formation_eye"},
    })


if __name__ == "__main__":
    main()
