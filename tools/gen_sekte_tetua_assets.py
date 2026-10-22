"""Module E (Tetua & Pewaris, DESIGN 36.2.6) assets: run `python tools/gen_sekte_tetua_assets.py`.

Writes, all our own designs:
  * the two desk props of an elder on duty (FormModels pieces, assets/kultivator/items/fx + models/item/fx):
      sekte_tetua_brush  - a calligraphy brush (bamboo handle, jade cap ring, wolf-hair tip)
      sekte_tetua_scroll - an opened handscroll of the sect's records (lacquer rods, gold caps, inked paper)
    and their two textures (textures/item/fx/sekte_tetua_paper.png, sekte_tetua_hair.png); the rest reuse mat_* materials.
  * the GUI art of the Balai Tetua and the morning report (textures/sekte/tetua/):
      kertas.png - rice paper with fibres and a faint ruled frame (tiles 128 x 128)
      segel.png  - a vermilion seal stamp with a rough, uneven print edge (32 x 32, white glyph drawn on top in code)
Everything stays within the piece rules of gen_fx_model_textures.py (<= 40 elements, -16..32, uv 0..16).
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
NS = "kultivator"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


# ------------------------------------------------------------------------------------------------ textures

def clamp(v):
    return max(0, min(255, int(round(v))))


def paper_piece():
    """16 x 16: warm paper, three vertical columns of ink strokes, a tiny red seal at the lower left."""
    rnd = random.Random(42)
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            n = rnd.uniform(-6, 6)
            img.set(x, y, (clamp(236 + n), clamp(224 + n), clamp(188 + n), 255))
    # the border of the mounting silk
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            img.set(x, y, (150, 112, 62, 255))
    # columns of characters, written top to bottom, right to left
    for col, x in enumerate((12, 9, 6, 3)):
        y = 2
        while y < 13 - col % 2:
            h = rnd.choice((1, 2, 2))
            for k in range(h):
                ink = rnd.randint(20, 55)
                img.set(x, y + k, (ink, ink - 5 if ink > 5 else ink, ink + 8, 255))
                if rnd.random() < 0.45:
                    img.set(x + rnd.choice((-1, 1)), y + k, (ink + 40, ink + 34, ink + 40, 255))
            y += h + 1
    # the seal
    for (x, y) in ((2, 12), (3, 12), (2, 13), (3, 13)):
        img.set(x, y, (186, 38, 30, 255))
    return img


def hair_piece():
    """16 x 16: a wolf-hair brush tip, ink-black at the bottom fading to pale hair at the ferrule."""
    rnd = random.Random(7)
    img = Image(16, 16)
    for y in range(16):
        t = y / 15.0  # 0 top (ferrule) .. 1 bottom (tip)
        for x in range(16):
            streak = rnd.uniform(-14, 14) + (8 if x % 3 == 0 else 0)
            base = (226 - 200 * t ** 1.4 + streak)
            img.set(x, y, (clamp(base + 6), clamp(base), clamp(base - 10 + 14 * t), 255))
    return img


def kertas():
    """128 x 128 rice paper for the report card and the screen panels: fibres, mottling, a ruled frame."""
    rnd = random.Random(1987)
    img = Image(128, 128)
    for y in range(128):
        for x in range(128):
            m = 6.0 * math.sin(x * 0.11 + y * 0.07) + 4.0 * math.sin(x * 0.031 - y * 0.05) + rnd.uniform(-5, 5)
            img.set(x, y, (clamp(238 + m), clamp(229 + m), clamp(203 + m * 0.8), 255))
    # fibres: short curved strands slightly darker or lighter than the sheet
    for _ in range(170):
        x, y = rnd.uniform(0, 128), rnd.uniform(0, 128)
        a = rnd.uniform(0, math.pi)
        length = rnd.uniform(3, 9)
        shade = rnd.choice((-13, -9, 10))
        for s in range(int(length * 2)):
            px = int(x + math.cos(a) * s * 0.5) % 128
            py = int(y + math.sin(a) * s * 0.5) % 128
            a += rnd.uniform(-0.15, 0.15)
            r, g, b, al = img.get(px, py)
            img.set(px, py, (clamp(r + shade), clamp(g + shade), clamp(b + shade), 255))
    # a faint ruled frame two pixels in (an official's register)
    for i in range(4, 124):
        for (x, y) in ((i, 4), (i, 123), (4, i), (123, i)):
            r, g, b, al = img.get(x, y)
            img.set(x, y, (clamp(r - 60), clamp(g - 52), clamp(b - 30), 255))
        for (x, y) in ((i, 6), (i, 121), (6, i), (121, i)):
            r, g, b, al = img.get(x, y)
            img.set(x, y, (clamp(r - 26), clamp(g - 24), clamp(b - 14), 255))
    return img


def segel():
    """32 x 32 vermilion seal: a square stamp with a rough print, an inner border and paper showing through."""
    rnd = random.Random(29)
    img = Image(32, 32)
    for y in range(32):
        for x in range(32):
            edge = min(x, y, 31 - x, 31 - y)
            if edge < 1:
                continue
            if edge < 3 and rnd.random() < 0.35:
                continue  # the rough edge of a pressed seal
            k = rnd.uniform(-18, 12)
            alpha = 255 if rnd.random() > 0.06 else 150  # the paper grain showing through
            img.set(x, y, (clamp(190 + k), clamp(40 + k * 0.4), clamp(30 + k * 0.3), alpha))
    # the carved inner border (paper colour)
    for i in range(4, 28):
        for (x, y) in ((i, 4), (i, 27), (4, i), (27, i)):
            if rnd.random() > 0.12:
                img.set(x, y, (244, 226, 200, 255))
    return img


# ------------------------------------------------------------------------------------------------ models

class Model:
    def __init__(self):
        self.tex = {}
        self.els = []

    def key(self, name):
        if name not in self.tex:
            self.tex[name] = "t%d" % len(self.tex)
        return "#" + self.tex[name]

    def box(self, a, b, tex, rot=None, full=False, skip=()):
        faces = {}
        dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        sizes = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
        for face, (w, h) in sizes.items():
            if face in skip:
                continue
            uv = [0.0, 0.0, 16.0, 16.0] if full else [0.0, 0.0, round(min(16.0, max(0.5, w)), 2), round(min(16.0, max(0.5, h)), 2)]
            faces[face] = {"uv": uv, "texture": self.key(tex)}
        el = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b], "faces": faces}
        if rot:
            axis, angle, origin = rot
            el["rotation"] = {"angle": angle, "axis": axis, "origin": [round(v, 3) for v in origin]}
        self.els.append(el)

    def json(self):
        tex = {k: "%s:item/fx/%s" % (NS, name) for name, k in self.tex.items()}
        tex["particle"] = "%s:item/fx/%s" % (NS, next(iter(self.tex)))
        return {"textures": tex, "elements": self.els}


def brush():
    m = Model()
    # bamboo handle (two knots), the hair tip below, a jade ring at the ferrule, a hanging loop on top
    m.box([7.4, -1.0, 7.4], [8.6, 14.0, 8.6], "mat_wood")
    m.box([7.25, 4.0, 7.25], [8.75, 4.6, 8.75], "mat_wood")
    m.box([7.25, 9.5, 7.25], [8.75, 10.1, 8.75], "mat_wood")
    m.box([7.1, -1.6, 7.1], [8.9, -0.4, 8.9], "mat_jade")
    m.box([7.2, -4.0, 7.2], [8.8, -1.6, 8.8], "sekte_tetua_hair", full=True)
    m.box([7.5, -5.6, 7.5], [8.5, -4.0, 8.5], "sekte_tetua_hair", full=True)
    m.box([7.75, -6.4, 7.75], [8.25, -5.6, 8.25], "mat_cloth_black")
    m.box([7.8, 14.0, 7.95], [8.2, 15.6, 8.05], "mat_cloth_red")
    return m


def scroll():
    m = Model()
    # the opened paper between two rods, held upright like a register being read
    m.box([2.5, 2.0, 7.85], [13.5, 13.0, 8.15], "sekte_tetua_paper", full=True)
    for x in (1.6, 14.4):
        m.box([x - 0.8, 1.0, 7.2], [x + 0.8, 14.0, 8.8], "mat_lacquer_red")
        m.box([x - 1.0, 0.2, 7.0], [x + 1.0, 1.0, 9.0], "mat_gold")
        m.box([x - 1.0, 14.0, 7.0], [x + 1.0, 14.8, 9.0], "mat_gold")
    # the silk ribbon tie hanging from the right rod
    m.box([14.2, 5.0, 8.8], [14.6, 9.0, 9.2], "mat_cloth_yellow")
    return m


def validate(name, m):
    problems = []
    if len(m.els) > 40:
        problems.append("%d elements" % len(m.els))
    for i, el in enumerate(m.els):
        for k in ("from", "to"):
            if any(v < -16.0 or v > 32.0 for v in el[k]):
                problems.append("element %d %s out of range" % (i, k))
        for face in el["faces"].values():
            if any(v < 0 or v > 16 for v in face["uv"]):
                problems.append("element %d uv %s" % (i, face["uv"]))
    for tex in m.tex:
        if not os.path.exists(os.path.join(ROOT, "textures", "item", "fx", tex + ".png")):
            problems.append("texture %s missing" % tex)
    for p in problems:
        print("MODEL %s: %s" % (name, p))
    return len(problems)


def main():
    fx = os.path.join(ROOT, "textures", "item", "fx")
    os.makedirs(fx, exist_ok=True)
    paper_piece().save(os.path.join(fx, "sekte_tetua_paper.png"))
    hair_piece().save(os.path.join(fx, "sekte_tetua_hair.png"))
    gui = os.path.join(ROOT, "textures", "sekte", "tetua")
    os.makedirs(gui, exist_ok=True)
    kertas().save(os.path.join(gui, "kertas.png"))
    segel().save(os.path.join(gui, "segel.png"))
    errors = 0
    for name, m in (("sekte_tetua_brush", brush()), ("sekte_tetua_scroll", scroll())):
        errors += validate(name, m)
        write_json(os.path.join(ROOT, "models", "item", "fx", name + ".json"), m.json())
        write_json(os.path.join(ROOT, "items", "fx", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "%s:item/fx/%s" % (NS, name)}})
    print("sekte tetua assets written, errors: %d" % errors)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
