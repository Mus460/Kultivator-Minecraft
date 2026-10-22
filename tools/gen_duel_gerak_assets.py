"""Generates the item assets of the sword duel (DESIGN 35.5.7, module A) under assets/kultivator/.

  textures/item/pedang_latih.png  16x16  the wooden training sword: a straight jian of pale ash wood (light edge on the
                                         upper side, a darker grain line and a shadowed lower side), a dark lacquered
                                         guard, a red cord wrap on the grip, a brass pommel and a short red tassel;
                                         drawn on the vanilla sword diagonal (guard at pixel (5, 11), point at (15, 1))
                                         so module A's BladeShape.PEDANG matches it and the sparks sit on the blade
  textures/item/duel_post.png     32x32  the atlas of the ring post: red lacquer with a soft vertical sheen, gold bands,
                                         grey stone, black lacquer, a red paper lantern with ribs and a warm glow, the
                                         lantern's dark wooden caps, a yellow talisman with vermilion script, red rope
  models/item/pedang_latih.json          parent minecraft:item/handheld (the vanilla third-person grip, contract C35.A2)
  models/item/duel_post.json             a 3D model in the 16 px cube: stone foot, red pole with two gold bands, a rope
                                         knot at the rope height (12 px = 1.2 blocks at the ring's scale 1.6), a black
                                         cap, an arm reaching outward (-z) with a hanging paper lantern (light_emission
                                         12) and a talisman on the face that looks at the ring centre (+z)
  items/pedang_latih.json, items/duel_post.json   the item model definitions

Run with: python tools/gen_duel_gerak_assets.py [preview_dir]
Deterministic (seeded noise); no dependencies besides tools/png.py. A preview dir gets 16x upscaled copies.
"""
import json
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


# ============================================================================================ the training sword

def pedang_latih():
    img = Image(16, 16)
    rnd = random.Random(3501)
    edge = rgb(0xEBCB93)
    wood = rgb(0xCDA067)
    grain = rgb(0xA97A45)
    under = rgb(0x8C5E31)
    tip = rgb(0xF2D9A8)
    guard = rgb(0x3B2416)
    guard_hi = rgb(0x6B4426)
    cord = rgb(0x9E2620)
    cord_dark = rgb(0x5E1310)
    brass = rgb(0xC39A45)
    brass_dark = rgb(0x7E5E22)
    tassel = rgb(0xC0302A)
    tassel_dark = rgb(0x7A1A16)
    outline = rgb(0x3A2210)

    filled = {}
    # the blade: three pixels across the diagonal from the guard at (5, 11) to the point at (15, 1)
    for i in range(1, 10):
        x, y = 5 + i, 11 - i
        jitter = rnd.uniform(-0.06, 0.06)
        filled[(x, y)] = shade(wood, 1.0 + jitter)  # the core
        if i <= 8:
            filled[(x, y - 1)] = shade(edge, 1.0 + jitter * 0.5)  # the upper edge catches the light
            filled[(x + 1, y)] = shade(under if i % 3 else grain, 1.0 + jitter)  # the lower side, a grain knot now and then
    filled[(14, 1)] = tip
    filled[(15, 1)] = tip
    filled[(15, 2)] = shade(wood, 0.95)
    # a thin darker grain line along the core near the guard (worn practice wood)
    for (x, y) in ((7, 9), (9, 7)):
        filled[(x, y)] = mix(filled[(x, y)], grain, 0.55)
    # the guard: a short lacquered bar across the diagonal, a lighter top
    for (x, y) in ((3, 9), (4, 10), (5, 11), (6, 12), (7, 13)):
        filled[(x, y)] = guard
    filled[(4, 9)] = guard_hi
    filled[(5, 10)] = guard_hi
    filled[(6, 11)] = guard_hi
    # the grip: red cord wrapped in diamonds
    for k, (x, y) in enumerate(((4, 12), (3, 13), (2, 14))):
        filled[(x, y)] = cord if k % 2 == 0 else cord_dark
        filled[(x + 1, y)] = cord_dark if k % 2 == 0 else cord
        filled[(x, y - 1)] = cord_dark if k % 2 else cord
    # the pommel and the tassel
    filled[(1, 15)] = brass
    filled[(1, 14)] = brass_dark
    filled[(2, 15)] = brass_dark
    filled[(0, 15)] = tassel
    filled[(0, 14)] = tassel_dark

    for (x, y), c in filled.items():
        img.set(x, y, c)
    # the vanilla-like dark outline around the shape
    for y in range(16):
        for x in range(16):
            if (x, y) in filled:
                continue
            near = any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near:
                img.set(x, y, outline)
    return img


# ============================================================================================ the ring post atlas
# uv units of 16 over the 32 px texture: one uv unit is 2 px.

REGIONS = {
    "pole": (0, 0, 2, 13),
    "gold": (2, 0, 4, 2),
    "stone": (4, 0, 8, 4),
    "black": (8, 0, 12, 2),
    "rope": (8, 2, 12, 3),
    "paper": (12, 0, 16, 5),
    "cap": (12, 5, 14, 7),
    "talisman": (4, 4, 6, 10),
    "tassel": (6, 4, 7, 8),
    "string": (7, 4, 8, 8),
    "pole_end": (2, 2, 4, 4),
}


def region_px(name):
    u0, v0, u1, v1 = REGIONS[name]
    return u0 * 2, v0 * 2, u1 * 2, v1 * 2


def duel_post():
    img = Image(32, 32)
    rnd = random.Random(3502)

    def fill(name, painter):
        x0, y0, x1, y1 = region_px(name)
        w, h = x1 - x0, y1 - y0
        for yy in range(h):
            for xx in range(w):
                img.set(x0 + xx, y0 + yy, painter(xx, yy, w, h))

    lacquer = rgb(0xB0241C)
    lacquer_hi = rgb(0xE0503A)
    lacquer_lo = rgb(0x6E120E)

    def pole(xx, yy, w, h):
        # a soft vertical sheen down the left third, darker to the right, a few faint grain marks
        t = xx / max(1, w - 1)
        c = mix(lacquer_hi, lacquer, min(1.0, t * 2.2)) if t < 0.5 else mix(lacquer, lacquer_lo, (t - 0.5) * 2.0)
        if rnd.random() < 0.08:
            c = shade(c, 0.88)
        return c

    def gold(xx, yy, w, h):
        base = rgb(0xD9AE45)
        if yy == 0:
            return rgb(0xF4D27A)
        if yy == h - 1:
            return rgb(0x8E6A1E)
        return shade(base, 1.0 - 0.06 * (xx % 2))

    def stone(xx, yy, w, h):
        g = 0x6E + rnd.randint(-10, 10)
        c = (g, g, g + 4, 255)
        if xx == 0 or yy == 0:
            c = shade(c, 1.15)
        if (xx + yy * 3) % 7 == 0:
            c = shade(c, 0.8)
        return c

    def black(xx, yy, w, h):
        c = rgb(0x1E1612)
        if yy == 0:
            c = rgb(0x3E302A)
        return c

    def rope(xx, yy, w, h):
        return rgb(0xC0302A) if (xx + yy) % 3 else rgb(0x7A1A16)

    def paper(xx, yy, w, h):
        # warm red paper lit from inside: brightest in the middle, bamboo ribs every 3 px, gold rims top and bottom
        cx = abs(xx - (w - 1) / 2.0) / (w / 2.0)
        cy = abs(yy - (h - 1) / 2.0) / (h / 2.0)
        glow = max(0.0, 1.0 - (cx * cx + cy * cy) * 0.8)
        c = mix(rgb(0xA8221A), rgb(0xFF9A50), glow)
        if yy % 3 == 0:
            c = shade(c, 0.72)
        if yy == 0 or yy == h - 1:
            c = rgb(0xD9AE45)
        # a dark ink character in the centre (a simple stroke pattern, readable as script at this size)
        if 2 <= xx <= w - 3 and 3 <= yy <= h - 3:
            if (xx == w // 2 and yy % 3 != 0) or (yy == h // 2 and 2 < xx < w - 3):
                c = rgb(0x2A0A08)
        return c

    def cap(xx, yy, w, h):
        return rgb(0x2A1A10) if yy else rgb(0x4A3020)

    def talisman(xx, yy, w, h):
        c = rgb(0xEED98A)
        if xx == 0 or xx == w - 1:
            c = rgb(0xD4BC66)
        # vermilion script down the middle and a seal at the bottom
        if xx in (1, 2) and 2 <= yy <= h - 5 and (yy % 3 != 1):
            c = rgb(0xC0302A)
        if yy >= h - 4 and 1 <= xx <= 2 and yy <= h - 2:
            c = rgb(0xA01E18)
        return c

    def tassel(xx, yy, w, h):
        return rgb(0xC0302A) if yy < h - 2 or xx == 0 else rgb(0x7A1A16)

    def string(xx, yy, w, h):
        return rgb(0x5A3A20)

    def pole_end(xx, yy, w, h):
        c = mix(lacquer, lacquer_lo, 0.3)
        if xx in (0, w - 1) or yy in (0, h - 1):
            c = lacquer_lo
        return c

    fill("pole", pole)
    fill("gold", gold)
    fill("stone", stone)
    fill("black", black)
    fill("rope", rope)
    fill("paper", paper)
    fill("cap", cap)
    fill("talisman", talisman)
    fill("tassel", tassel)
    fill("string", string)
    fill("pole_end", pole_end)
    return img


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), uv=None, top_uv=None, light=0, shade_faces=True):
    """One element: every face maps the whole region of {tex} (or {uv}); up/down use {top_uv} when given."""
    u0, v0, u1, v1 = REGIONS[tex] if uv is None else uv
    face_map = {}
    for f in faces:
        if f in ("up", "down") and top_uv is not None:
            face_map[f] = {"uv": list(top_uv), "texture": "#t"}
        else:
            face_map[f] = {"uv": [u0, v0, u1, v1], "texture": "#t"}
    el = {"from": list(frm), "to": list(to), "faces": face_map}
    if light:
        el["light_emission"] = light
    if not shade_faces:
        el["shade"] = False
    return el


def post_model():
    elements = [
        # the stone foot and its step
        box((5, 0, 5), (11, 2, 11), "stone"),
        box((6, 2, 6), (10, 2.5, 10), "stone"),
        # the red lacquered pole
        box((7, 2.5, 7), (9, 15, 9), "pole", top_uv=REGIONS["pole_end"]),
        # two gold bands
        box((6.75, 4, 6.75), (9.25, 4.75, 9.25), "gold"),
        box((6.75, 13, 6.75), (9.25, 13.75, 9.25), "gold"),
        # the rope knot where the ring ropes are tied (12 px = 1.2 blocks at scale 1.6)
        box((6.6, 11.5, 6.6), (9.4, 12.5, 9.4), "rope"),
        # the black lacquer cap
        box((6.5, 15, 6.5), (9.5, 16, 9.5), "black"),
        box((7.25, 16, 7.25), (8.75, 16, 8.75), "gold", faces=("up",)),
        # the arm reaching outward (-z) and the hanging lantern
        box((7.5, 14, 2.5), (8.5, 15, 7), "black"),
        box((7.75, 12.5, 3.75), (8.25, 14, 4.25), "string"),
        box((6.75, 12, 2.75), (9.25, 12.5, 5.25), "cap"),
        box((6.5, 8.5, 2.5), (9.5, 12, 5.5), "paper", light=12, faces=("north", "south", "east", "west")),
        box((6.75, 8, 2.75), (9.25, 8.5, 5.25), "cap"),
        box((7.75, 6, 3.75), (8.25, 8, 4.25), "tassel"),
        # the talisman pasted on the face towards the ring centre (+z)
        box((7.25, 6.5, 9.0), (8.75, 10.5, 9.1), "talisman", faces=("south", "north")),
    ]
    return {
        "textures": {"t": "kultivator:item/duel_post", "particle": "kultivator:item/duel_post"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.7, 0.7, 0.7]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 2], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
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
    textures = {"pedang_latih": pedang_latih(), "duel_post": duel_post()}
    for name, img in textures.items():
        path = os.path.join(ROOT, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        if preview:
            os.makedirs(preview, exist_ok=True)
            upscale(img, 16 if img.width <= 16 else 8).save(os.path.join(preview, name + "_x.png"))
    write_json("models/item/pedang_latih.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": "kultivator:item/pedang_latih"},
    })
    write_json("models/item/duel_post.json", post_model())
    for name in ("pedang_latih", "duel_post"):
        write_json("items/" + name + ".json", {"model": {"type": "minecraft:model", "model": "kultivator:item/" + name}})
    print("duel item assets written")


if __name__ == "__main__":
    main()
