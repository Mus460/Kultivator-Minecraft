"""Generates the phase 8 forging assets (DESIGN 25.5, 25.7): the Earthfire Forge block textures, the four artifact forms
(a base layer plus a tinted accent layer), the storage pouch and the four storage rings, with their blockstate, models,
item definitions, loot table, recipes, the ring_immune damage-type tag and the forging advancements.

Run with: python tools/gen_tempa_textures.py [preview_dir]
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

DEEP_DARK = (30, 30, 36)
DEEP = (52, 52, 60)
DEEP_LIGHT = (80, 80, 90)
BRICK_DARK = (26, 22, 28)
BRICK = (44, 38, 46)
BRICK_LIGHT = (64, 56, 66)
IRON_DARK = (70, 74, 82)
IRON = (130, 136, 146)
IRON_LIGHT = (196, 202, 210)
EMBER_DARK = (150, 30, 10)
EMBER = (240, 96, 24)
EMBER_LIGHT = (255, 214, 110)
GOLD_DARK = (150, 104, 30)
GOLD = (214, 166, 58)
GOLD_LIGHT = (250, 222, 130)
WOOD_DARK = (70, 40, 20)
WOOD = (110, 66, 34)
WHITE = (255, 255, 255)
OUTLINE = (24, 20, 22)


def noise(x, y, seed):
    """Deterministic value noise in 0..1."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def grey(v):
    return (v, v, v, 255)


# ------------------------------------------------------------------ forge block

def stone_fill(img, seed, dark, mid, light):
    for y in range(SIZE):
        for x in range(SIZE):
            n = noise(x, y, seed)
            c = mix(dark, mid, 0.35 + 0.45 * n)
            if x == 0 or y == 0:
                c = mix(c, light, 0.5)
            if x == SIZE - 1 or y == SIZE - 1:
                c = mix(c, dark, 0.6)
            img.set(x, y, c)


def forge_top():
    img = Image(SIZE, SIZE)
    stone_fill(img, 5, DEEP_DARK, DEEP, DEEP_LIGHT)
    # Iron rim around a glowing ember grate.
    for y in range(3, 13):
        for x in range(3, 13):
            if x in (3, 12) or y in (3, 12):
                img.set(x, y, mix(IRON_DARK, IRON, 0.6 if y < 8 else 0.3))
            else:
                d = math.hypot(x - 7.5, y - 7.5) / 4.5
                c = mix(EMBER_LIGHT, EMBER, d * 1.2)
                c = mix(c, EMBER_DARK, max(0.0, d - 0.6) * 1.5)
                if (x + y) % 3 == 0:
                    c = mix(c, (60, 20, 10), 0.75)
                img.set(x, y, c)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img.set(x, y, GOLD)
        img.set(x + (1 if x < 8 else -1), y, GOLD_DARK)
    return img


def forge_side():
    img = Image(SIZE, SIZE)
    # Blackstone bricks at the top.
    for y in range(0, 5):
        for x in range(SIZE):
            n = noise(x, y, 17)
            c = mix(BRICK_DARK, BRICK, 0.4 + 0.4 * n)
            if y == 2 or (x + (4 if y < 2 else 0)) % 8 == 0:
                c = mix(BRICK_DARK, OUTLINE, 0.5)
            if y == 0:
                c = mix(c, BRICK_LIGHT, 0.5)
            img.set(x, y, c)
    # Iron plates with the fire mouth.
    for y in range(5, 12):
        for x in range(SIZE):
            c = mix(IRON_DARK, IRON, 0.35 + 0.25 * noise(x, y, 23))
            if y == 5:
                c = mix(c, IRON_LIGHT, 0.5)
            if x in (0, 15):
                c = mix(c, IRON_DARK, 0.6)
            img.set(x, y, c)
    for y in range(7, 12):
        for x in range(5, 11):
            arch = y > 7 or 6 <= x <= 9
            if arch:
                t = (y - 7) / 4.0
                c = mix(EMBER_LIGHT, EMBER, 1.0 - t)
                if y == 11:
                    c = mix(EMBER_DARK, EMBER, 0.5)
                img.set(x, y, c)
    for x in (4, 11):
        for y in range(7, 12):
            img.set(x, y, mix(IRON_DARK, OUTLINE, 0.4))
    img.set(3, 8, GOLD)
    img.set(12, 8, GOLD)
    # Polished deepslate foot.
    for y in range(12, SIZE):
        for x in range(SIZE):
            c = mix(DEEP_DARK, DEEP, 0.4 + 0.4 * noise(x, y, 31))
            if y == 12:
                c = mix(c, DEEP_LIGHT, 0.6)
            img.set(x, y, c)
    return img


def forge_bottom():
    img = Image(SIZE, SIZE)
    stone_fill(img, 41, DEEP_DARK, DEEP, DEEP_LIGHT)
    return img


# ------------------------------------------------------------------ artifacts (base + accent)

def line(img, x0, y0, x1, y1, color):
    steps = max(abs(x1 - x0), abs(y1 - y0)) * 2 + 1
    for i in range(steps + 1):
        t = i / steps
        img.set(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), color)


def pedang():
    base = Image(SIZE, SIZE)
    accent = Image(SIZE, SIZE)
    # Blade from the lower left hilt to the upper right tip.
    for i in range(0, 10):
        x = 5 + i
        y = 10 - i
        base.set(x, y, mix(IRON, IRON_LIGHT, 0.4 + 0.06 * i))
        base.set(x - 1, y, mix(IRON_DARK, IRON, 0.6))
        base.set(x, y + 1, mix(IRON_DARK, IRON, 0.4))
        accent.set(x, y - 1 if i < 9 else y, mix(grey(200), WHITE, 0.6))
    base.set(15, 0, IRON_LIGHT)
    # Guard (crossbar) and hilt.
    for x, y in ((2, 9), (3, 10), (4, 11), (6, 13), (7, 14), (5, 10), (6, 11)):
        base.set(x, y, GOLD_DARK)
    line(base, 2, 9, 7, 14, GOLD)
    for x, y in ((3, 12), (2, 13), (1, 14)):
        base.set(x, y, WOOD)
        base.set(x + 1, y, WOOD_DARK)
    base.set(0, 15, GOLD)
    accent.set(4, 11, WHITE)
    accent.set(5, 12, mix(grey(220), WHITE, 0.5))
    accent.set(0, 15, grey(230))
    return base, accent


def payung():
    base = Image(SIZE, SIZE)
    accent = Image(SIZE, SIZE)
    cx = 7.5
    # Canopy: a flat dome.
    for y in range(1, 8):
        half = 7.0 * math.sqrt(max(0.0, 1.0 - ((7.5 - y) / 7.0) ** 2))
        for x in range(SIZE):
            if abs(x - cx) <= half:
                t = (y - 1) / 6.0
                c = mix((200, 60, 50), (130, 30, 30), t)
                if int(abs(x - cx)) % 3 == 0:
                    c = mix(c, (90, 20, 20), 0.5)
                base.set(x, y, c)
                if y == 7 or abs(x - cx) > half - 1:
                    accent.set(x, y, grey(230))
    for x in range(1, 15):
        if x % 3 == 1:
            accent.set(x, 8, WHITE)
    accent.set(7, 0, WHITE)
    accent.set(8, 0, WHITE)
    # Shaft and handle.
    for y in range(8, 15):
        base.set(8, y, WOOD)
        base.set(7, y, WOOD_DARK)
    base.set(6, 14, WOOD)
    base.set(6, 15, WOOD_DARK)
    base.set(5, 14, WOOD_DARK)
    return base, accent


def wajan():
    base = Image(SIZE, SIZE)
    accent = Image(SIZE, SIZE)
    cx, cy = 7.5, 6.0
    for y in range(5, 14):
        for x in range(1, 15):
            nx = (x - cx) / 6.5
            ny = (y - cy) / 7.0
            if ny >= 0 and nx * nx + ny * ny <= 1.0:
                d = nx * nx + ny * ny
                c = mix(IRON_DARK, (40, 40, 46), d)
                if x < 6 and y < 10:
                    c = mix(c, IRON, 0.35)
                base.set(x, y, c)
    for x in range(1, 15):
        base.set(x, 5, mix(IRON, IRON_LIGHT, 0.3))
        accent.set(x, 5, mix(grey(210), WHITE, 0.5))
        accent.set(x, 6, grey(170))
    # Two ear handles.
    for x, y in ((0, 6), (0, 7), (15, 6), (15, 7)):
        base.set(x, y, OUTLINE)
        accent.set(x, y, grey(200))
    # A turtle-shell hex on the bowl.
    for x, y in ((7, 9), (8, 9), (6, 10), (9, 10), (7, 11), (8, 11)):
        accent.set(x, y, WHITE)
    return base, accent


def lonceng():
    base = Image(SIZE, SIZE)
    accent = Image(SIZE, SIZE)
    cx = 7.5
    for y in range(2, 13):
        t = (y - 2) / 10.0
        half = 2.2 + 3.2 * math.sqrt(t) + (1.2 if y >= 11 else 0.0)
        for x in range(SIZE):
            if abs(x - cx) <= half:
                t = abs(x - cx) / max(1.0, half)
                c = mix(GOLD_LIGHT, GOLD_DARK, t * 0.9 + (y - 2) * 0.02)
                base.set(x, y, c)
    for x in range(1, 15):
        base.set(x, 13, GOLD_DARK)
        accent.set(x, 13, grey(220))
    for x in range(4, 12):
        accent.set(x, 8, WHITE if x % 2 == 0 else grey(200))
    # Hanging loop and clapper.
    for x, y in ((6, 1), (7, 0), (8, 0), (9, 1)):
        base.set(x, y, GOLD_DARK)
    base.set(7, 14, OUTLINE)
    base.set(8, 14, OUTLINE)
    accent.set(7, 15, grey(230))
    accent.set(8, 15, grey(230))
    accent.set(6, 4, WHITE)
    return base, accent


# ------------------------------------------------------------------ rings

def pouch():
    img = Image(SIZE, SIZE)
    leather_dark = (80, 48, 26)
    leather = (132, 86, 48)
    leather_light = (172, 120, 72)
    for y in range(5, 15):
        half = 4.0 + 2.5 * math.sin(math.pi * (y - 5) / 10.0)
        for x in range(SIZE):
            if abs(x - 7.5) <= half:
                t = (abs(x - 7.5) / half) * 0.7 + (y - 5) / 30.0
                c = mix(leather_light, leather_dark, t)
                if noise(x, y, 7) > 0.85:
                    c = mix(c, leather_dark, 0.4)
                img.set(x, y, c)
    for x in range(4, 12):
        img.set(x, 4, leather)
        img.set(x, 3, leather_light if x % 2 else leather)
    # Drawstring and the jade bead.
    for x in range(4, 12):
        img.set(x, 5, (210, 196, 150))
    img.set(11, 6, (210, 196, 150))
    img.set(12, 7, (90, 200, 140))
    img.set(12, 8, (50, 140, 90))
    return img


RING_TIERS = {
    "storage_ring_low": ((150, 100, 50), (200, 150, 90), (90, 210, 150)),
    "storage_ring_mid": ((130, 136, 150), (210, 216, 226), (90, 160, 255)),
    "storage_ring_high": ((170, 120, 30), (250, 214, 110), (176, 110, 255)),
    "storage_ring_supreme": ((200, 190, 160), (255, 250, 230), (255, 120, 200)),
}


def ring(band_dark, band_light, gem):
    img = Image(SIZE, SIZE)
    cx, cy = 7.5, 9.0
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot((x - cx) / 1.0, (y - cy) / 0.85)
            if 3.4 <= d <= 5.6:
                angle = math.atan2(y - cy, x - cx)
                t = 0.5 + 0.5 * math.sin(angle + 2.2)
                c = mix(band_dark, band_light, t)
                if d < 3.9 or d > 5.2:
                    c = mix(c, band_dark, 0.5)
                img.set(x, y, c)
    # The gem setting on top.
    for y in range(1, 6):
        for x in range(5, 11):
            d = abs(x - 7.5) + abs(y - 3.2)
            if d <= 2.8:
                c = mix(mix(gem, WHITE, 0.45), gem, d / 2.8)
                img.set(x, y, c)
    img.set(6, 2, WHITE)
    for x in (4, 11):
        img.set(x, 5, band_light)
    return img


# ------------------------------------------------------------------ output

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


def save(img, folder, name, preview):
    path = os.path.join(ASSETS, "textures", folder)
    os.makedirs(path, exist_ok=True)
    img.save(os.path.join(path, name + ".png"))
    if preview:
        os.makedirs(preview, exist_ok=True)
        enlarge(img, 8).save(os.path.join(preview, name + ".png"))


def advancement(name, parent, icon, frame="task"):
    write_json(os.path.join(DATA, "advancement", name + ".json"), {
        "parent": "kultivator:" + parent,
        "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": {
            "description": {"translate": "advancement.kultivator." + name + ".description"},
            "icon": {"id": icon},
            "title": {"translate": "advancement.kultivator." + name + ".title"},
            "frame": frame,
        },
        "requirements": [["done"]],
    })


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    save(forge_top(), "block", "earthfire_forge_top", preview)
    save(forge_side(), "block", "earthfire_forge_side", preview)
    save(forge_bottom(), "block", "earthfire_forge_bottom", preview)
    write_json(os.path.join(ASSETS, "blockstates", "earthfire_forge.json"),
               {"variants": {"": {"model": "kultivator:block/earthfire_forge"}}})
    write_json(os.path.join(ASSETS, "models", "block", "earthfire_forge.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "kultivator:block/earthfire_forge_top",
            "side": "kultivator:block/earthfire_forge_side",
            "bottom": "kultivator:block/earthfire_forge_bottom",
        },
    })
    write_json(os.path.join(ASSETS, "items", "earthfire_forge.json"),
               {"model": {"type": "minecraft:model", "model": "kultivator:block/earthfire_forge"}})

    forms = {"pedang": pedang(), "payung": payung(), "wajan": wajan(), "lonceng": lonceng()}
    cases = []
    for form, (base, accent) in forms.items():
        save(base, "item", "artifact_" + form, preview)
        save(accent, "item", "artifact_" + form + "_accent", preview)
        write_json(os.path.join(ASSETS, "models", "item", "artifact_" + form + ".json"), {
            "parent": "minecraft:item/handheld" if form == "pedang" else "minecraft:item/generated",
            "textures": {
                "layer0": "kultivator:item/artifact_" + form,
                "layer1": "kultivator:item/artifact_" + form + "_accent",
            },
        })
        cases.append({"when": form, "model": {
            "type": "minecraft:model",
            "model": "kultivator:item/artifact_" + form,
            "tints": [
                {"type": "minecraft:constant", "value": -1},
                {"type": "minecraft:custom_model_data", "index": 0, "default": -1},
            ],
        }})
    write_json(os.path.join(ASSETS, "items", "forged_artifact.json"), {"model": {
        "type": "minecraft:select",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "cases": cases,
        "fallback": cases[0]["model"],
    }})

    rings = {"storage_pouch": pouch()}
    for name, colors in RING_TIERS.items():
        rings[name] = ring(*colors)
    for name, img in rings.items():
        save(img, "item", name, preview)
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}})
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}})

    write_json(os.path.join(DATA, "loot_table", "blocks", "earthfire_forge.json"), {
        "type": "minecraft:block",
        "pools": [{
            "condition": {"type": "minecraft:survives_explosion"},
            "entries": [{"type": "minecraft:item", "name": "kultivator:earthfire_forge"}],
            "rolls": 1,
        }],
        "random_sequence": "kultivator:blocks/earthfire_forge",
    })
    write_json(os.path.join(DATA, "recipe", "earthfire_forge.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {
            "B": "minecraft:polished_blackstone_bricks",
            "I": "minecraft:iron_block",
            "C": "minecraft:cauldron",
            "D": "minecraft:polished_deepslate",
        },
        "pattern": ["BIB", "ICI", "DDD"],
        "result": {"id": "kultivator:earthfire_forge"},
    })
    write_json(os.path.join(DATA, "recipe", "storage_pouch.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {
            "S": "minecraft:string",
            "L": "minecraft:leather",
            "K": "kultivator:spirit_stone_low",
        },
        "pattern": [" S ", "LKL", " L "],
        "result": {"id": "kultivator:storage_pouch"},
    })
    write_json(os.path.join(DATA, "tags", "damage_type", "ring_immune.json"), {
        "values": ["#minecraft:is_fire", "#minecraft:is_explosion", "minecraft:cactus"],
    })

    advancement("first_forge", "first_alchemy", "kultivator:earthfire_forge")
    advancement("artifact_langit", "first_forge", "kultivator:forged_artifact", "goal")
    advancement("artifact_dewa", "artifact_langit", "minecraft:nether_star", "challenge")
    advancement("refine_9", "first_forge", "minecraft:anvil", "goal")
    advancement("natal_bond", "first_forge", "minecraft:diamond_sword", "goal")
    advancement("spirit_elementary", "natal_bond", "minecraft:amethyst_cluster", "goal")
    advancement("spirit_complete", "spirit_elementary", "minecraft:echo_shard", "challenge")
    advancement("first_ring", "root_tested", "kultivator:storage_ring_low")
    advancement("ring_supreme", "first_ring", "kultivator:storage_ring_supreme", "challenge")


if __name__ == "__main__":
    main()
