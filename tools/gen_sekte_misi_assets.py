"""Generates the assets of Sekte yang Hidup module X, Misi luar dan kepulangan (DESIGN 36.2.7) under assets/kultivator/.

  textures/item/sekte/misi/gulungan_laporan.png  16x16  the mission report scroll: a rolled sheet of pale bamboo paper on
                                                        the item diagonal, lacquered red-brown rod ends with a gold rim,
                                                        the paper's spiral edge showing at the upper end, a red cord tied
                                                        round the middle with a small vermilion seal hanging from it
  textures/item/fx/sekte_misi_bundle.png        32x32  the atlas of the travel bundle carried at the gate line-up: a
                                                        bamboo stick with nodes, an indigo cloth printed with white wave
                                                        crests (seigaiha), its darker knot, a straw tie
  models/item/gulungan_laporan.json                     parent minecraft:item/generated
  models/item/fx/sekte_misi_bundle.json                 a cuboid piece (FormModels convention: (8, 8, 8) at the hand):
                                                        the stick through the fist, the bundle beyond the hand with a
                                                        straw band, the knot and its two cloth ears
  items/gulungan_laporan.json, items/fx/sekte_misi_bundle.json

Run with: python tools/gen_sekte_misi_assets.py [preview_dir]
Deterministic (seeded noise); no dependencies besides tools/png.py. A preview dir gets upscaled copies.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")


def rgb(hex_value, alpha=255):
    return ((hex_value >> 16) & 0xFF, (hex_value >> 8) & 0xFF, hex_value & 0xFF, alpha)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(c, k):
    return (int(max(0, min(255, c[0] * k))), int(max(0, min(255, c[1] * k))), int(max(0, min(255, c[2] * k))), c[3])


# ============================================================================================ the report scroll

def gulungan():
    img = Image(16, 16)
    rnd = random.Random(3607)
    paper_hi = rgb(0xF6ECCF)
    paper = rgb(0xE6D6AE)
    paper_lo = rgb(0xC2AD80)
    spiral = rgb(0x9C8558)
    rod = rgb(0x7A2E1E)
    rod_hi = rgb(0xA8462C)
    gold = rgb(0xD9B45A)
    cord = rgb(0xC0302A)
    cord_dark = rgb(0x7E1A16)
    seal = rgb(0xD23A2A)
    seal_mark = rgb(0xF6D9A0)
    outline = rgb(0x3B2614)

    filled = {}
    # The roll lies on the diagonal from (3, 12.5) to (12.5, 3), about four pixels thick: each pixel near the axis is
    # shaded by its side (upper-left lit, lower-right in shadow) like a cylinder.
    ax, ay, bx, by = 3.2, 12.8, 12.8, 3.2
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - ax, y + 0.5 - ay
            along = px * ux + py * uy
            across = -px * uy + py * ux  # > 0 below-right of the axis
            if -0.4 <= along <= length + 0.4 and abs(across) <= 2.05:
                t = (across + 2.05) / 4.1
                c = mix(paper_hi, paper, t * 1.6) if t < 0.6 else mix(paper, paper_lo, (t - 0.6) / 0.4)
                filled[(x, y)] = shade(c, 1.0 + rnd.uniform(-0.04, 0.04))
                # the cord: a band across the middle
                if abs(along - length * 0.5) < 0.75:
                    filled[(x, y)] = cord if across < 0.8 else cord_dark
                # faint brush strokes showing through the outer turn
                elif abs(across) < 0.6 and int(along) in (3, 4, 9, 10) and int(along * 3) % 2 == 0:
                    filled[(x, y)] = mix(filled[(x, y)], spiral, 0.4)
    # the lacquered rod caps with a gold rim at both ends
    for (cx, cy) in ((ax - 0.6, ay + 0.6), (bx + 0.6, by - 0.6)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= 1.9:
                    filled[(x, y)] = rod_hi if d < 0.8 else rod
                elif d <= 2.5 and (x, y) in filled and filled[(x, y)] not in (cord, cord_dark):
                    filled[(x, y)] = gold
    # the paper's spiral edge at the upper end
    filled[(11, 3)] = spiral
    filled[(12, 4)] = mix(paper, spiral, 0.6)
    # the tail of the cord and the small vermilion seal hanging from it
    filled[(9, 9)] = cord_dark
    filled[(9, 10)] = cord
    filled[(9, 11)] = cord_dark
    for (x, y) in ((8, 12), (9, 12), (8, 13), (9, 13)):
        filled[(x, y)] = seal
    filled[(8, 12)] = seal_mark
    for (x, y), c in filled.items():
        if 0 <= x < 16 and 0 <= y < 16:
            img.set(x, y, c)
    for y in range(16):
        for x in range(16):
            if (x, y) in filled:
                continue
            if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.set(x, y, outline)
    return img


# ============================================================================================ the bundle atlas
# uv units of 16 over the 32 px texture: one uv unit is 2 px.

REGIONS = {
    "stick": (0, 0, 2, 16),
    "cloth": (2, 0, 10, 8),
    "knot": (10, 0, 14, 4),
    "rope": (10, 4, 14, 5),
    "cloth_end": (2, 8, 8, 14),
    "stick_end": (14, 0, 15, 1),
}


def region_px(name):
    u0, v0, u1, v1 = REGIONS[name]
    return u0 * 2, v0 * 2, u1 * 2, v1 * 2


def bundle_atlas():
    img = Image(32, 32)
    rnd = random.Random(3608)

    def fill(name, painter):
        x0, y0, x1, y1 = region_px(name)
        for y in range(y0, y1):
            for x in range(x0, x1):
                img.set(x, y, painter(x - x0, y - y0, x1 - x0, y1 - y0))

    bamboo = rgb(0xB8A060)
    bamboo_lo = rgb(0x8C7440)
    node = rgb(0x6E5A2E)

    def stick(x, y, w, h):
        c = mix(bamboo, bamboo_lo, x / max(1, w - 1) * 0.6)
        if y % 11 == 0:
            c = node
        elif y % 11 == 1:
            c = shade(c, 1.12)
        return shade(c, 1.0 + rnd.uniform(-0.04, 0.04))

    indigo = rgb(0x24386E)
    indigo_lo = rgb(0x182650)
    crest = rgb(0xE8EEF6)

    def wave(x, y, w, h):
        # seigaiha: rows of overlapping half circles; their rims drawn in white on the indigo cloth
        c = mix(indigo, indigo_lo, y / max(1, h - 1) * 0.5)
        # small wave crests: one 5 px arc per 8 x 6 cell, every other row shifted half a cell
        row = int(y // 6)
        ox = 4.0 if row % 2 else 0.0
        cx = math.floor((x + ox) / 8.0) * 8.0 + 4.0 - ox
        cy = row * 6 + 4.5
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        if y + 0.5 <= cy and abs(d - 2.5) < 0.5:
            return mix(c, crest, 0.85)
        if y + 0.5 <= cy and d < 0.8:
            return mix(c, crest, 0.4)
        return shade(c, 1.0 + rnd.uniform(-0.05, 0.05))

    def knot(x, y, w, h):
        c = mix(indigo_lo, rgb(0x101A38), (x + y) / float(w + h))
        if (x + 2 * y) % 5 == 0:
            c = shade(c, 1.25)
        return c

    straw = rgb(0xD8C078)

    def rope(x, y, w, h):
        return shade(straw if (x + y) % 3 else rgb(0xA88E48), 1.0 + rnd.uniform(-0.05, 0.05))

    def cloth_end(x, y, w, h):
        cxx, cyy = (w - 1) / 2.0, (h - 1) / 2.0
        d = math.hypot(x - cxx, y - cyy) / max(cxx, cyy)
        c = mix(indigo, indigo_lo, d)
        if abs(d - 0.55) < 0.12:
            c = mix(c, crest, 0.6)
        return c

    def stick_end(x, y, w, h):
        return rgb(0xD8C690)

    fill("stick", stick)
    fill("cloth", wave)
    fill("knot", knot)
    fill("rope", rope)
    fill("cloth_end", cloth_end)
    fill("stick_end", stick_end)
    return img


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), top=None):
    u0, v0, u1, v1 = REGIONS[tex]
    face_map = {}
    for f in faces:
        if f in ("up", "down") and top is not None:
            tu0, tv0, tu1, tv1 = REGIONS[top]
            face_map[f] = {"uv": [tu0, tv0, tu1, tv1], "texture": "#t"}
        else:
            face_map[f] = {"uv": [u0, v0, u1, v1], "texture": "#t"}
    return {"from": list(frm), "to": list(to), "faces": face_map}


def bundle_model():
    # (8, 8, 8) is the fist: the stick runs through it, the bundle hangs beyond the hand (towards -y, the part's down,
    # which is "above the head" while the arm carries the stick over the shoulder).
    elements = [
        box((7.4, -4, 7.4), (8.6, 14, 8.6), "stick", top="stick_end"),
        box((3.5, -11, 4.0), (12.5, -3, 12.0), "cloth", top="cloth_end"),
        box((3.3, -6.6, 3.8), (12.7, -5.8, 12.2), "rope", faces=("north", "south", "east", "west")),
        box((6.2, -13, 6.4), (9.8, -11, 9.6), "knot"),
        box((5.0, -14.5, 7.4), (7.0, -12.6, 8.6), "knot"),
        box((9.0, -14.0, 7.4), (11.0, -12.6, 8.6), "knot"),
    ]
    return {
        "textures": {"t": "kultivator:item/fx/sekte_misi_bundle", "particle": "kultivator:item/fx/sekte_misi_bundle"},
        "elements": elements,
    }


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def upscale(img, k):
    out = Image(img.width * k, img.height * k)
    for y in range(img.height):
        for x in range(img.width):
            c = img.get(x, y)
            for yy in range(k):
                for xx in range(k):
                    out.set(x * k + xx, y * k + yy, c)
    return out


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    textures = {
        os.path.join("textures", "item", "sekte", "misi", "gulungan_laporan.png"): gulungan(),
        os.path.join("textures", "item", "fx", "sekte_misi_bundle.png"): bundle_atlas(),
    }
    for rel, img in textures.items():
        path = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        if preview:
            os.makedirs(preview, exist_ok=True)
            name = os.path.splitext(os.path.basename(rel))[0]
            upscale(img, 16 if img.width <= 16 else 8).save(os.path.join(preview, name + "_x.png"))
    write_json("models/item/gulungan_laporan.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "kultivator:item/sekte/misi/gulungan_laporan"},
    })
    write_json("items/gulungan_laporan.json", {"model": {"type": "minecraft:model", "model": "kultivator:item/gulungan_laporan"}})
    write_json("models/item/fx/sekte_misi_bundle.json", bundle_model())
    write_json("items/fx/sekte_misi_bundle.json", {"model": {"type": "minecraft:model", "model": "kultivator:item/fx/sekte_misi_bundle"}})
    print("sekte misi assets written")


if __name__ == "__main__":
    main()
