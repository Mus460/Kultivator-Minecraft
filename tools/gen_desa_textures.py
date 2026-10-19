"""Generates the vassal village (P8-desa) textures and model JSON: the Plakat Pelindung Sekte block, the Surat Permohonan
letter with a compass seal (32 needle frames for the vanilla minecraft:compass range dispatch) and the Pil Mata Terang.

Run with: python tools/gen_desa_textures.py [preview_dir]
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
FRAMES = 32


def rgb(hex_value, alpha=255):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, alpha)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def noise(x, y, seed):
    v = math.sin(x * 12.9898 + y * 78.233 + seed * 37.719) * 43758.5453
    return v - math.floor(v)


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


STONE = rgb(0x8E918C)
STONE_DARK = rgb(0x5E625E)
STONE_LIGHT = rgb(0xB4B7B1)
GOLD = rgb(0xE2B33C)
GOLD_DARK = rgb(0x9A6E1C)
GOLD_LIGHT = rgb(0xFBE38A)
SEAL = rgb(0xB3281D)
SEAL_DARK = rgb(0x6E160F)
SEAL_LIGHT = rgb(0xE5523E)
PAPER = rgb(0xEADBB0)
PAPER_DARK = rgb(0xBFA772)
PAPER_LIGHT = rgb(0xFBF1D2)
JADE = rgb(0x2E8963)
JADE_LIGHT = rgb(0x8FE3C8)
INK = rgb(0x2A221A)


def stone_fill(img, seed, base=STONE):
    for y in range(16):
        for x in range(16):
            n = noise(x, y, seed)
            c = mix(base, STONE_LIGHT, 0.35) if n > 0.82 else mix(base, STONE_DARK, 0.4) if n < 0.18 else base
            img.set(x, y, c)


def plaque_front():
    img = Image(16, 16)
    stone_fill(img, 1)
    # Gold double frame of the tablet face (the slab is 8 px wide, u 4..12).
    for y in range(2, 14):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (2, 13)
            if edge:
                img.set(x, y, GOLD if (x + y) % 3 else GOLD_DARK)
    # The sect's red seal: a square stamp with a pale glyph.
    for y in range(4, 10):
        for x in range(6, 10):
            img.set(x, y, SEAL if 4 < y < 9 and 6 < x < 9 else SEAL_DARK)
    for (x, y) in ((7, 5), (8, 6), (7, 7), (8, 7), (7, 8)):
        img.set(x, y, SEAL_LIGHT)
    # Two carved ink lines under the seal (the village name).
    for x in range(6, 10):
        img.set(x, 11, mix(STONE_DARK, INK, 0.6))
    img.set(5, 12, mix(STONE_DARK, INK, 0.4))
    img.set(10, 12, mix(STONE_DARK, INK, 0.4))
    save(img, "block", "sect_patron_plaque_front")


def plaque_side():
    img = Image(16, 16)
    stone_fill(img, 2)
    for y in range(16):
        img.set(6, y, mix(STONE, STONE_DARK, 0.5))
        img.set(9, y, mix(STONE, STONE_LIGHT, 0.4))
    save(img, "block", "sect_patron_plaque_side")


def plaque_cap():
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 3)
            img.set(x, y, GOLD_LIGHT if n > 0.85 else GOLD_DARK if n < 0.15 else GOLD)
    for x in range(16):
        img.set(x, 0, GOLD_LIGHT)
        img.set(x, 15, GOLD_DARK)
    save(img, "block", "sect_patron_plaque_cap")


def plaque_base():
    img = Image(16, 16)
    stone_fill(img, 4, base=mix(STONE, STONE_DARK, 0.3))
    for x in range(16):
        img.set(x, 7, STONE_DARK)
        img.set(x, 15, STONE_DARK)
    for y in range(8):
        img.set(4, y, STONE_DARK)
        img.set(12, y + 8, STONE_DARK)
    save(img, "block", "sect_patron_plaque_base")


def letter(frame):
    """A folded letter with a red wax seal; the seal carries a gold needle pointing frame/32 of a turn clockwise from up."""
    img = Image(16, 16)
    # Paper body (a slightly tilted rectangle look through shading).
    for y in range(3, 14):
        for x in range(1, 15):
            c = PAPER
            if y == 3 or x == 1:
                c = PAPER_LIGHT
            if y == 13 or x == 14:
                c = PAPER_DARK
            img.set(x, y, c)
    # The fold: a V from the top corners to the middle.
    for x in range(1, 15):
        fy = 3 + int(round(abs(x - 7.5) * -0.55 + 4.2))
        img.set(x, fy, PAPER_DARK)
    # Outline.
    for x in range(1, 15):
        img.blend(x, 2, (60, 45, 25, 120))
        img.blend(x, 14, (60, 45, 25, 160))
    for y in range(3, 14):
        img.blend(0, y, (60, 45, 25, 120))
        img.blend(15, y, (60, 45, 25, 160))
    # Wax seal disc.
    cx, cy, r = 8.0, 9.0, 3.6
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                c = SEAL if d < r - 0.9 else SEAL_DARK
                if x + 0.5 < cx - 1 and y + 0.5 < cy - 1:
                    c = mix(c, SEAL_LIGHT, 0.4)
                img.set(x, y, c)
    # The needle (gold tip, jade tail).
    angle = frame / FRAMES * 2.0 * math.pi
    dx, dy = math.sin(angle), -math.cos(angle)
    for i in range(1, 9):
        t = i / 8.0
        px, py = cx + dx * 3.2 * t, cy + dy * 3.2 * t
        img.set(int(math.floor(px)), int(math.floor(py)), GOLD_LIGHT if t > 0.6 else GOLD)
    for i in range(1, 5):
        t = i / 4.0
        px, py = cx - dx * 2.0 * t, cy - dy * 2.0 * t
        img.set(int(math.floor(px)), int(math.floor(py)), JADE_LIGHT)
    img.set(int(cx), int(cy), GOLD_DARK)
    name = "village_letter_%02d" % frame
    save(img, "item", name)
    write_json("models/item/%s.json" % name, {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}})


def mata_terang_pill():
    img = Image(16, 16)
    base = rgb(0x5FA8DC)
    deep = rgb(0x1E4F80)
    cx, cy, r = 7.5, 8.0, 5.2
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy)
            if d <= r:
                light = -(x - cx) * 0.12 - (y - cy) * 0.16
                c = mix(base, (255, 255, 255), max(0.0, light) * 0.8) if light > 0 else mix(base, deep, min(1.0, -light * 0.9))
                if d > r - 0.9:
                    c = mix(c, deep, 0.6)
                img.set(x, y, c)
    # An eye glyph: white almond with a jade iris and a gold pupil glint.
    for x in range(5, 11):
        img.set(x, 7, (250, 250, 255, 255))
        img.set(x, 9, (250, 250, 255, 255))
    for x in range(4, 12):
        img.set(x, 8, (250, 250, 255, 255))
    img.set(7, 8, JADE)
    img.set(8, 8, JADE)
    img.set(7, 7, JADE)
    img.set(8, 9, JADE)
    img.set(8, 7, GOLD_LIGHT)
    img.set(4, 4, (255, 255, 255, 230))
    img.set(5, 4, (255, 255, 255, 180))
    save(img, "item", "mata_terang_pill")
    write_json("models/item/mata_terang_pill.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/mata_terang_pill"}})
    write_json("items/mata_terang_pill.json", {"model": {"type": "minecraft:model", "model": "kultivator:item/mata_terang_pill"}})


def plaque_models():
    def face(uv, tex, cull=None):
        f = {"uv": uv, "texture": tex}
        if cull:
            f["cullface"] = cull
        return f

    model = {
        "parent": "minecraft:block/block",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]}
        },
        "textures": {
            "particle": "kultivator:block/sect_patron_plaque_side",
            "front": "kultivator:block/sect_patron_plaque_front",
            "side": "kultivator:block/sect_patron_plaque_side",
            "cap": "kultivator:block/sect_patron_plaque_cap",
            "base": "kultivator:block/sect_patron_plaque_base"
        },
        "elements": [
            {"from": [3, 0, 5], "to": [13, 2, 11], "faces": {
                "north": face([3, 14, 13, 16], "#base"), "south": face([3, 14, 13, 16], "#base"),
                "east": face([5, 14, 11, 16], "#base"), "west": face([5, 14, 11, 16], "#base"),
                "up": face([3, 5, 13, 11], "#base"), "down": face([3, 5, 13, 11], "#base", "down")}},
            {"from": [4, 2, 7], "to": [12, 13, 9], "faces": {
                "north": face([4, 2, 12, 13], "#front"), "south": face([4, 2, 12, 13], "#front"),
                "east": face([7, 2, 9, 13], "#side"), "west": face([7, 2, 9, 13], "#side")}},
            {"from": [3.5, 13, 6.5], "to": [12.5, 15, 9.5], "faces": {
                "north": face([3.5, 1, 12.5, 3], "#cap"), "south": face([3.5, 1, 12.5, 3], "#cap"),
                "east": face([6.5, 1, 9.5, 3], "#cap"), "west": face([6.5, 1, 9.5, 3], "#cap"),
                "up": face([3.5, 6.5, 12.5, 9.5], "#cap"), "down": face([3.5, 6.5, 12.5, 9.5], "#cap")}}
        ]
    }
    write_json("models/block/sect_patron_plaque.json", model)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": "kultivator:block/sect_patron_plaque"}
        if y:
            v["y"] = y
        variants["facing=" + facing] = v
    write_json("blockstates/sect_patron_plaque.json", {"variants": variants})
    write_json("items/sect_patron_plaque.json", {"model": {"type": "minecraft:model", "model": "kultivator:block/sect_patron_plaque"}})


def letter_item():
    entries = [{"threshold": 0.0, "model": {"type": "minecraft:model", "model": "kultivator:item/village_letter_00"}}]
    for k in range(1, FRAMES):
        entries.append({"threshold": k / FRAMES - 1.0 / (2 * FRAMES),
                        "model": {"type": "minecraft:model", "model": "kultivator:item/village_letter_%02d" % k}})
    entries.append({"threshold": 1.0 - 1.0 / (2 * FRAMES), "model": {"type": "minecraft:model", "model": "kultivator:item/village_letter_00"}})
    write_json("items/village_letter.json", {"model": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:compass",
        "target": "lodestone",
        "entries": entries,
        "fallback": {"type": "minecraft:model", "model": "kultivator:item/village_letter_00"}
    }})


if __name__ == "__main__":
    plaque_front()
    plaque_side()
    plaque_cap()
    plaque_base()
    plaque_models()
    for k in range(FRAMES):
        letter(k)
    letter_item()
    mata_terang_pill()
    print("ok")
