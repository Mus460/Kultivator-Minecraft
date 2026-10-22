"""Module A (Paviliun Kitab & Pemakaman / Altar Leluhur, DESIGN 36.2.5) assets: run `python tools/gen_sekte_paviliun_assets.py`.

Writes, all our own designs:
  * world art of the renderer (textures/sekte/paviliun/):
      smoke.png  - a soft curling wisp of incense smoke (32 x 32, alpha only, tinted in code)
      glow.png   - a round soft glow (32 x 32) for embers, lantern light, tablet halos and the rite ring
      tablet.png - a spirit tablet (16 x 32): black lacquer, a rounded crown, a gilt frame and a gilt column of characters
      segel.png  - the screen's vermilion seal (32 x 32, the glyph is drawn on top in code)
  * two FormModels pieces (assets/kultivator/items/fx + models/item/fx):
      sekte_paviliun_scroll  - an opened sutra handscroll: cream paper with ink columns between indigo silk borders, jade
                               rollers with gold caps (the reader's prop and the sutra floating before a seated reader)
      sekte_paviliun_incense - three incense sticks fanned in a red paper wrap, glowing tips (the mourner's prop)
    and their textures (textures/item/fx/sekte_paviliun_sutra.png, sekte_paviliun_stick.png, sekte_paviliun_ember.png);
  * the item Dupa (kultivator:dupa): items/dupa.json, models/item/dupa.json, textures/item/dupa.png (a bundle of incense
    sticks with a red paper band), its recipe and the two advancements of the module.
Everything stays within the piece rules of gen_fx_model_textures.py (<= 40 elements, -16..32, uv 0..16).
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

BASE = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ROOT = os.path.join(BASE, "assets", "kultivator")
DATA = os.path.join(BASE, "data", "kultivator")
NS = "kultivator"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write("\n")


def clamp(v):
    return max(0, min(255, int(round(v))))


# ------------------------------------------------------------------------------------------------ world textures

def smoke():
    """A wisp: a soft core stretched upward and bent into a curl, ragged at the edges (white, alpha carries it)."""
    rnd = random.Random(11)
    img = Image(32, 32)
    noise = [[rnd.uniform(-1, 1) for _ in range(32)] for _ in range(32)]
    for y in range(32):
        for x in range(32):
            # the curl: the centre line swings with height
            t = y / 31.0
            cx = 16 + math.sin(t * math.pi * 1.6) * 5.0
            dx = (x - cx) / (6.5 + 3.0 * (1 - t))
            dy = (y - 16) / 15.0
            d = math.sqrt(dx * dx + dy * dy)
            n = (noise[y][x] + noise[(y + 1) % 32][x] + noise[y][(x + 1) % 32]) / 3.0
            a = max(0.0, 1.0 - d) ** 1.6 * (0.8 + 0.35 * n)
            a *= min(1.0, (31 - y) / 6.0 + 0.2) * min(1.0, y / 5.0 + 0.3)
            if a > 0.01:
                img.set(x, y, (255, 255, 255, clamp(a * 235)))
    return img


def glow():
    img = Image(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.sqrt((x - 15.5) ** 2 + (y - 15.5) ** 2) / 15.5
            a = max(0.0, 1.0 - d) ** 2.2
            if a > 0.004:
                img.set(x, y, (255, 255, 255, clamp(a * 255)))
    return img


def tablet():
    """16 x 32 spirit tablet: a rounded crown, black lacquer with a red-brown depth, a gilt frame, a gilt column."""
    rnd = random.Random(5)
    img = Image(16, 32)
    for y in range(32):
        for x in range(16):
            # the crown: a half-round top over rows 0-5
            if y < 6:
                r = 7.5
                if (x - 7.5) ** 2 + (6 - y) ** 2 > r * r:
                    continue
            # the base: a wider plinth row 27-31
            if y >= 27 and (x == 0 or x == 15) and y < 28:
                continue
            k = rnd.uniform(-5, 5)
            img.set(x, y, (clamp(30 + k + y * 0.4), clamp(16 + k * 0.6), clamp(14 + k * 0.5), 255))
    gold = (214, 168, 64, 255)
    gold_dim = (150, 112, 40, 255)
    # the frame
    for y in range(4, 27):
        img.set(1, y, gold_dim)
        img.set(14, y, gold_dim)
    for x in range(1, 15):
        img.set(x, 26, gold_dim)
    for x in range(16):
        for y in range(0, 6):
            r_out, r_in = 7.6, 6.4
            d = math.sqrt((x - 7.5) ** 2 + (6 - y) ** 2)
            if r_in <= d <= r_out:
                img.set(x, y, gold_dim)
    # the plinth
    for y in range(27, 32):
        for x in range(16):
            if img.get(x, y)[3] > 0:
                img.set(x, y, (clamp(70 - (y - 27) * 6), 22, 16, 255))
    for x in range(16):
        img.set(x, 27, gold)
    # the column of characters, top to bottom
    y = 6
    while y < 24:
        h = rnd.choice((2, 2, 3))
        for k in range(h):
            if y + k >= 25:
                break
            img.set(7, y + k, gold)
            img.set(8, y + k, gold if rnd.random() < 0.7 else gold_dim)
            if rnd.random() < 0.5:
                img.set(rnd.choice((6, 9)), y + k, gold_dim)
        y += h + 1
    # a little crown ornament
    img.set(7, 2, gold)
    img.set(8, 2, gold)
    img.set(7, 3, gold_dim)
    img.set(8, 3, gold_dim)
    return img


def segel():
    """32 x 32 vermilion seal with softly rounded corners, a rough print and a double inner border."""
    rnd = random.Random(141)
    img = Image(32, 32)
    for y in range(32):
        for x in range(32):
            cx = min(x, 31 - x)
            cy = min(y, 31 - y)
            if cx < 4 and cy < 4 and (4 - cx) ** 2 + (4 - cy) ** 2 > 18:
                continue
            edge = min(cx, cy)
            if edge < 2 and rnd.random() < 0.3:
                continue
            k = rnd.uniform(-16, 12)
            alpha = 255 if rnd.random() > 0.05 else 160
            img.set(x, y, (clamp(184 + k), clamp(36 + k * 0.4), clamp(28 + k * 0.3), alpha))
    for i in range(5, 27):
        for (x, y) in ((i, 5), (i, 26), (5, i), (26, i)):
            if rnd.random() > 0.1:
                img.set(x, y, (246, 228, 204, 255))
    return img


# ------------------------------------------------------------------------------------------------ piece textures

def sutra_piece():
    """16 x 16: cream paper, indigo silk borders top and bottom, five columns of ink, a red seal at the end."""
    rnd = random.Random(77)
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            n = rnd.uniform(-6, 6)
            img.set(x, y, (clamp(238 + n), clamp(228 + n), clamp(196 + n), 255))
    for x in range(16):
        for y in (0, 1, 14, 15):
            n = rnd.uniform(-8, 8)
            img.set(x, y, (clamp(44 + n), clamp(58 + n), clamp(120 + n), 255))
        img.set(x, 2, (196, 160, 70, 255))
        img.set(x, 13, (196, 160, 70, 255))
    for x in (13, 11, 9, 7, 5, 3):
        y = 4
        while y < 12:
            h = rnd.choice((1, 2, 2))
            for k in range(h):
                if y + k < 12:
                    ink = rnd.randint(18, 50)
                    img.set(x, y + k, (ink, ink, ink + 10, 255))
            y += h + 1
    for (x, y) in ((1, 10), (2, 10), (1, 11), (2, 11)):
        img.set(x, y, (186, 38, 30, 255))
    return img


def stick_piece():
    """16 x 16: the incense stick, red-brown aromatic paste over a pale bamboo core (vertical grain)."""
    rnd = random.Random(23)
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            n = rnd.uniform(-10, 10) + (6 if x % 4 == 0 else 0)
            img.set(x, y, (clamp(140 + n), clamp(62 + n * 0.5), clamp(44 + n * 0.4), 255))
    return img


def ember_piece():
    """16 x 16: a glowing ember, white-gold heart to deep orange rim, grey ash flecks."""
    rnd = random.Random(31)
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2) / 10.6
            heat = max(0.0, 1.0 - d)
            r = 200 + 55 * heat
            g = 70 + 160 * heat ** 1.5
            b = 30 + 120 * heat ** 3
            if rnd.random() < 0.08:
                r, g, b = 120, 110, 104
            img.set(x, y, (clamp(r), clamp(g), clamp(b), 255))
    return img


def dupa_item():
    """16 x 16 item: five incense sticks fanned out of a red paper band, bamboo feet, dark unlit tips, a dark outline."""
    img = Image(16, 16)
    # (foot x, tip x, tip y): the sticks spread from the band like a small fan
    sticks = [(6, 2, 2), (7, 5, 1), (8, 8, 0), (9, 11, 1), (10, 14, 2)]
    for i, (fx, tx, ty) in enumerate(sticks):
        for y in range(ty, 16):
            t = (y - ty) / float(15 - ty)
            x = int(round(tx + (fx - tx) * t))
            if y >= 13:
                img.set(x, y, (214, 192, 122, 255) if i % 2 == 0 else (186, 160, 98, 255))
            elif y == ty:
                img.set(x, y, (64, 30, 24, 255))
            else:
                shade = -12 if (y + i) % 3 == 0 else 0
                img.set(x, y, (clamp(156 + shade), clamp(64 + shade * 0.4), clamp(42 + shade * 0.3), 255))
    # the red paper band with a gold line
    for x in range(5, 12):
        for y in (9, 10, 11):
            img.set(x, y, (204, 44, 34, 255) if y != 10 else (226, 64, 46, 255))
        img.set(x, 12, (124, 24, 18, 255))
    for x in range(6, 11):
        img.set(x, 10, (236, 194, 84, 255))
    # outline
    out = Image(16, 16)
    for y in range(16):
        for x in range(16):
            px = img.get(x, y)
            if px[3] > 0:
                out.set(x, y, px)
            else:
                for (ax, ay) in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    if 0 <= ax < 16 and 0 <= ay < 16 and img.get(ax, ay)[3] > 0 and (ay >= 9 or ax == x):
                        out.set(x, y, (44, 22, 16, 255))
                        break
    return out


# ------------------------------------------------------------------------------------------------ models

class Model:
    def __init__(self):
        self.tex = {}
        self.els = []

    def key(self, name):
        if name not in self.tex:
            self.tex[name] = "t%d" % len(self.tex)
        return "#" + self.tex[name]

    def box(self, a, b, tex, rot=None, full=False):
        faces = {}
        dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        sizes = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
        for face, (w, h) in sizes.items():
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


def scroll():
    m = Model()
    # the unrolled paper, wide and low like a handscroll being read
    m.box([2.2, 4.0, 7.85], [13.8, 12.0, 8.15], "sekte_paviliun_sutra", full=True)
    for x in (1.2, 14.8):
        # the jade rollers with the paper wound round them, gold caps above and below
        m.box([x - 1.0, 3.4, 7.0], [x + 1.0, 12.6, 9.0], "sekte_paviliun_sutra")
        m.box([x - 0.55, 2.2, 7.45], [x + 0.55, 3.4, 8.55], "mat_jade")
        m.box([x - 0.55, 12.6, 7.45], [x + 0.55, 13.8, 8.55], "mat_jade")
        m.box([x - 0.7, 1.6, 7.3], [x + 0.7, 2.2, 8.7], "mat_gold")
        m.box([x - 0.7, 13.8, 7.3], [x + 0.7, 14.4, 8.7], "mat_gold")
    # the silk tie ribbon hanging from the left roller
    m.box([0.6, 6.0, 8.9], [1.0, 9.6, 9.3], "mat_cloth_red")
    return m


def incense():
    m = Model()
    # three sticks fanned from one grip: the middle upright, the others at 22.5 degrees
    for angle, ox in ((-22.5, 7.0), (0.0, 7.75), (22.5, 8.5)):
        rot = ("z", angle, [8.0, 2.0, 8.0]) if angle else None
        m.box([ox, 2.0, 7.75], [ox + 0.5, 14.0, 8.25], "sekte_paviliun_stick", rot=rot)
        m.box([ox - 0.05, 14.0, 7.7], [ox + 0.55, 15.0, 8.3], "sekte_paviliun_ember", rot=rot, full=True)
    # the red paper wrap the hands hold
    m.box([6.6, 1.0, 7.3], [9.4, 4.0, 8.7], "mat_cloth_red")
    m.box([6.5, 2.2, 7.2], [9.5, 2.8, 8.8], "mat_gold")
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
        rot = el.get("rotation")
        if rot and rot["angle"] not in (-45.0, -22.5, 0.0, 22.5, 45.0):
            problems.append("element %d angle %s" % (i, rot["angle"]))
    for tex in m.tex:
        if not os.path.exists(os.path.join(ROOT, "textures", "item", "fx", tex + ".png")):
            problems.append("texture %s missing" % tex)
    for p in problems:
        print("MODEL %s: %s" % (name, p))
    return len(problems)


def advancement(name, icon, parent):
    return {
        "parent": parent,
        "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": {
            "description": {"translate": "advancement.kultivator.sekte.%s.description" % name},
            "icon": {"id": icon},
            "title": {"translate": "advancement.kultivator.sekte.%s.title" % name},
            "frame": "task",
        },
        "requirements": [["done"]],
    }


def main():
    world = os.path.join(ROOT, "textures", "sekte", "paviliun")
    os.makedirs(world, exist_ok=True)
    smoke().save(os.path.join(world, "smoke.png"))
    glow().save(os.path.join(world, "glow.png"))
    tablet().save(os.path.join(world, "tablet.png"))
    segel().save(os.path.join(world, "segel.png"))
    fx = os.path.join(ROOT, "textures", "item", "fx")
    os.makedirs(fx, exist_ok=True)
    sutra_piece().save(os.path.join(fx, "sekte_paviliun_sutra.png"))
    stick_piece().save(os.path.join(fx, "sekte_paviliun_stick.png"))
    ember_piece().save(os.path.join(fx, "sekte_paviliun_ember.png"))
    item = os.path.join(ROOT, "textures", "item")
    dupa_item().save(os.path.join(item, "dupa.png"))
    errors = 0
    for name, m in (("sekte_paviliun_scroll", scroll()), ("sekte_paviliun_incense", incense())):
        errors += validate(name, m)
        write_json(os.path.join(ROOT, "models", "item", "fx", name + ".json"), m.json())
        write_json(os.path.join(ROOT, "items", "fx", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "%s:item/fx/%s" % (NS, name)}})
    write_json(os.path.join(ROOT, "items", "dupa.json"), {"model": {"type": "minecraft:model", "model": "%s:item/dupa" % NS}})
    write_json(os.path.join(ROOT, "models", "item", "dupa.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/dupa" % NS}})
    write_json(os.path.join(DATA, "recipe", "dupa.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:stick", "minecraft:charcoal", "minecraft:sweet_berries"],
        "result": {"count": 4, "id": "%s:dupa" % NS},
    })
    write_json(os.path.join(DATA, "advancement", "sekte", "paviliun_belajar.json"),
               advancement("paviliun_belajar", "minecraft:enchanted_book", "kultivator:disciple_taught"))
    write_json(os.path.join(DATA, "advancement", "sekte", "paviliun_upacara.json"),
               advancement("paviliun_upacara", "%s:dupa" % NS, "kultivator:first_disciple"))
    print("sekte paviliun assets written, errors: %d" % errors)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
