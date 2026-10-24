"""Isometric preview renderer for cuboid item models (DESIGN 38.O.0, module 38MD): projects every element of a block/item
model JSON (with its single-axis rotation) into a z-buffered isometric image, sampling the real textures, so a checker
can open the result with Read and see the prop as it is in game.

Usage:
  python tools/tempat_iso.py <out.png> <model.json> [<model.json> ...]     one sheet, one cell per model
  python tools/tempat_iso.py --props <out_dir>                             sheets of every fx/tempat and senjata model
The model's "kultivator:<path>" textures are read from src/main/resources/assets/kultivator/textures/<path>.png.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402
from gen_tempat_gear import read_png  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
CELL = 220
SCALE = 10.0  # max image pixels per model pixel
_TEX = {}


def texture(ref):
    path = ref.split(":", 1)[1]
    if path not in _TEX:
        f = os.path.join(ASSETS, "textures", path + ".png")
        _TEX[path] = read_png(f) if os.path.exists(f) else None
    return _TEX[path]


def rotate(p, rot):
    if not rot:
        return p
    a = math.radians(rot["angle"])
    o = rot["origin"]
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def project(p):
    """Isometric view from the south-east above: screen x, screen y, depth (bigger = nearer)."""
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    sx = (x - z) * 0.866
    sy = -y + (x + z) * 0.5
    depth = x + z + y * 0.6
    return sx, sy, depth


FACES = {
    # side: (corner function from (from, to), u axis, v axis, light)
    "up": lambda f, t: ((f[0], t[1], f[2]), (t[0], t[1], f[2]), (f[0], t[1], t[2])),
    "down": lambda f, t: ((f[0], f[1], t[2]), (t[0], f[1], t[2]), (f[0], f[1], f[2])),
    "north": lambda f, t: ((t[0], t[1], f[2]), (f[0], t[1], f[2]), (t[0], f[1], f[2])),
    "south": lambda f, t: ((f[0], t[1], t[2]), (t[0], t[1], t[2]), (f[0], f[1], t[2])),
    "west": lambda f, t: ((f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], f[2])),
    "east": lambda f, t: ((t[0], t[1], t[2]), (t[0], t[1], f[2]), (t[0], f[1], t[2])),
}
LIGHT = {"up": 1.0, "down": 0.5, "north": 0.7, "south": 0.85, "west": 0.6, "east": 0.8}


def render(model, img, ox, oy, zbuf, scale=SCALE):
    textures = model.get("textures", {})
    for e in model.get("elements", []):
        f, t = e["from"], e["to"]
        rot = e.get("rotation")
        glow = e.get("light_emission", 0) > 0
        for side, face in e.get("faces", {}).items():
            o, pu, pv = FACES[side](f, t)
            ref = face.get("texture", "#t0")
            ref = textures.get(ref.lstrip("#"), "")
            tx = texture(ref) if ":" in ref else None
            u0, v0, u1, v1 = face.get("uv", [0, 0, 16, 16])
            du = [pu[i] - o[i] for i in range(3)]
            dv = [pv[i] - o[i] for i in range(3)]
            lu = math.sqrt(sum(c * c for c in du))
            lv = math.sqrt(sum(c * c for c in dv))
            nu = max(2, int(lu * scale * 1.2))
            nv = max(2, int(lv * scale * 1.2))
            light = 1.0 if glow else LIGHT[side]
            for j in range(nv):
                for i in range(nu):
                    a, b = (i + 0.5) / nu, (j + 0.5) / nv
                    p = [o[k] + du[k] * a + dv[k] * b for k in range(3)]
                    sx, sy, depth = project(rotate(p, rot))
                    px, py = int(ox + sx * scale), int(oy + sy * scale)
                    if tx is None:
                        col = (255, 0, 255, 255)
                    else:
                        tu = (u0 + (u1 - u0) * a) / 16.0
                        tv = (v0 + (v1 - v0) * b) / 16.0
                        col = tx.get(min(tx.width - 1, max(0, int(tu * tx.width))), min(tx.height - 1, max(0, int(tv * tx.height))))
                    if col[3] < 20:
                        continue
                    for yy in (0, 1):
                        for xx in (0, 1):
                            qx, qy = px + xx, py + yy
                            if 0 <= qx < img.width and 0 <= qy < img.height:
                                k = qy * img.width + qx
                                if depth > zbuf.get(k, -1e9):
                                    zbuf[k] = depth
                                    img.set(qx, qy, (col[0] * light, col[1] * light, col[2] * light, 255))


def sheet(paths, out, cols=6):
    rows = (len(paths) + cols - 1) // cols
    img = Image(cols * CELL, rows * CELL, (34, 36, 42, 255))
    zbuf = {}
    for n, path in enumerate(paths):
        with open(path, encoding="utf-8") as fh:
            model = json.load(fh)
        lo = [min(e["from"][i] for e in model["elements"]) for i in range(3)]
        hi = [max(e["to"][i] for e in model["elements"]) for i in range(3)]
        size = max(hi[i] - lo[i] for i in range(3))
        scale = min(SCALE, (CELL * 0.62) / max(1.0, size))
        cx, cy, _ = project(((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2))
        ox = (n % cols) * CELL + CELL / 2 - cx * scale
        oy = (n // cols) * CELL + CELL / 2 - cy * scale
        render(model, img, ox, oy, zbuf, scale)
        # cell frame
        for x in range((n % cols) * CELL, (n % cols + 1) * CELL):
            img.set(x, (n // cols) * CELL, (70, 72, 80, 255))
        for y in range((n // cols) * CELL, (n // cols + 1) * CELL):
            img.set((n % cols) * CELL, y, (70, 72, 80, 255))
    img.save(out)
    print("wrote", out, len(paths), "models")


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "--props":
        out = sys.argv[2]
        os.makedirs(out, exist_ok=True)
        pdir = os.path.join(ASSETS, "models", "item", "fx", "tempat")
        props = [os.path.join(pdir, f) for f in sorted(os.listdir(pdir)) if f.endswith(".json")]
        for i in range(0, len(props), 16):
            sheet(props[i:i + 16], os.path.join(out, "iso_props_%d.png" % (i // 16 + 1)), cols=4)
        wdir = os.path.join(ASSETS, "models", "item", "senjata")
        if os.path.isdir(wdir):
            weapons = [os.path.join(wdir, f) for f in sorted(os.listdir(wdir)) if f.endswith(".json")]
            sheet(weapons, os.path.join(out, "iso_senjata.png"), cols=7)
        return
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    sheet(sys.argv[2:], sys.argv[1])


if __name__ == "__main__":
    main()
