"""Generates the qi particle sprites (polish pass, VFX primitive S5): python tools/gen_fx_particle_textures.py

Every sprite is white (or light grey shading) with a soft alpha shape, so the particle's vertex colour tints it: one
texture serves every Dao colour. 32 x 32 each, written to
src/main/resources/assets/kultivator/textures/particle/qi_*.png, plus the particle definitions in
src/main/resources/assets/kultivator/particles/qi*.json (the vanilla particle atlas lists textures/particle of every
namespace through its directory source, so no atlas file is needed).
"""
import json
import math
import os
import random

from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
TEX = os.path.join(ROOT, "textures", "particle")
DEF = os.path.join(ROOT, "particles")
N = 32


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0)) if e1 != e0 else (1.0 if x >= e1 else 0.0)
    return t * t * (3.0 - 2.0 * t)


def put(img, x, y, shade, alpha):
    """A white pixel scaled by shade (0..1) with alpha (0..1)."""
    s = int(round(255 * clamp(shade)))
    img.set(x, y, (s, s, s, int(round(255 * clamp(alpha)))))


def coords():
    for y in range(N):
        for x in range(N):
            yield x, y, (x + 0.5) / N * 2.0 - 1.0, (y + 0.5) / N * 2.0 - 1.0


class Noise:
    """Seeded value noise on a 2D lattice, tiled over the sprite."""

    def __init__(self, seed, cells):
        rnd = random.Random(seed)
        self.cells = cells
        self.v = [[rnd.random() for _ in range(cells + 1)] for _ in range(cells + 1)]

    def at(self, u, v):
        x = clamp(u) * self.cells
        y = clamp(v) * self.cells
        x0, y0 = min(int(x), self.cells - 1), min(int(y), self.cells - 1)
        fx, fy = x - x0, y - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.v[y0][x0] * (1 - fx) + self.v[y0][x0 + 1] * fx
        b = self.v[y0 + 1][x0] * (1 - fx) + self.v[y0 + 1][x0 + 1] * fx
        return a * (1 - fy) + b * fy


def soft():
    """The main qi mote: a bright plateau with a long soft falloff (emissive look)."""
    img = Image(N, N)
    for x, y, u, v in coords():
        r = math.sqrt(u * u + v * v)
        core = 1.0 - smooth(0.0, 0.32, r)
        halo = math.exp(-(r * r) / 0.18)
        a = clamp(0.95 * halo + 0.3 * core) * (1.0 - smooth(0.82, 1.0, r))
        put(img, x, y, 0.82 + 0.18 * core, a)
    return img


def glint():
    """A four-ray star glint (sparkle accents, starfields)."""
    img = Image(N, N)
    for x, y, u, v in coords():
        r = math.sqrt(u * u + v * v)
        ray = max(math.exp(-abs(u) * 22.0) * (1 - smooth(0.2, 1.0, abs(v))), math.exp(-abs(v) * 22.0) * (1 - smooth(0.2, 1.0, abs(u))))
        diag = max(math.exp(-abs(u - v) * 18.0), math.exp(-abs(u + v) * 18.0)) * (1 - smooth(0.0, 0.6, r)) * 0.45
        disc = math.exp(-(r * r) / 0.03)
        a = clamp(ray + diag + disc) * (1.0 - smooth(0.9, 1.0, r))
        put(img, x, y, 1.0, a)
    return img


def spark():
    """A horizontal streak, brightest a little ahead of the centre (motion along +x)."""
    img = Image(N, N)
    for x, y, u, v in coords():
        along = math.exp(-((u - 0.15) ** 2) / 0.32) * (1.0 - smooth(0.85, 1.0, abs(u)))
        across = math.exp(-(v * v) / 0.006)
        glow = math.exp(-(v * v) / 0.05) * 0.35 * along
        a = clamp(along * across + glow)
        put(img, x, y, 0.9 + 0.1 * across, a)
    return img


def ring():
    """A thin luminous annulus with an inner haze (ground rings, shock fronts, lens rings)."""
    img = Image(N, N)
    for x, y, u, v in coords():
        r = math.sqrt(u * u + v * v)
        band = math.exp(-((r - 0.78) ** 2) / 0.006)
        inner = 0.18 * (1.0 - smooth(0.2, 0.78, r)) * smooth(0.0, 0.5, r)
        a = clamp(band + inner) * (1.0 - smooth(0.93, 1.0, r))
        put(img, x, y, 1.0, a)
    return img


def smoke(seed):
    """A noisy puff: soft round mass with darker lumps (tinted grey/ink, not emissive)."""
    img = Image(N, N)
    n1 = Noise(seed, 4)
    n2 = Noise(seed + 17, 8)
    for x, y, u, v in coords():
        r = math.sqrt(u * u + v * v)
        uu, vv = (u + 1) / 2, (v + 1) / 2
        lump = 0.6 * n1.at(uu, vv) + 0.4 * n2.at(uu, vv)
        edge = 0.62 + 0.3 * lump
        a = clamp((1.0 - smooth(edge - 0.35, edge, r)) * (0.55 + 0.45 * lump))
        put(img, x, y, 0.72 + 0.28 * lump, a * 0.85)
    return img


def flame(frame):
    """A flame tongue pointing up (frame 0 full, 3 a guttering wisp); white core, softer body."""
    img = Image(N, N)
    noise = Noise(31 + frame, 6)
    k = 1.0 - frame * 0.2
    for x, y, u, v in coords():
        # v: -1 top, +1 bottom. A teardrop: wide at the base, a tip at the top that sways with the frame.
        t = (1.0 - v) / 2.0
        sway = math.sin(t * 3.1 + frame * 1.7) * 0.12 * t
        width = (0.86 * (0.55 + 0.45 * k)) * math.sqrt(max(0.0, 1.0 - t)) * (0.55 + 0.45 * smooth(0.0, 0.35, t)) * (1.0 - smooth(0.88 * k, 1.0, t)) + 0.001
        d = abs(u - sway) / width
        wob = noise.at((u + 1) / 2, t) * 0.35
        body = 1.0 - smooth(0.55 - wob * 0.3, 1.0, d)
        core = (1.0 - smooth(0.0, 0.55, d)) * (1.0 - smooth(0.25, 0.7 * k, t)) * smooth(0.0, 0.12, t)
        a = clamp(body * (0.75 + 0.25 * core) * (1.0 - smooth(0.92, 1.0, (v + 1) / 2 + 0.02)))
        put(img, x, y, 0.78 + 0.22 * core, a)
    return img


def polygon_alpha(u, v, pts):
    """Signed-ish inside test with a soft edge for a convex polygon (pts in order)."""
    inside = True
    best = 9.0
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % n]
        ex, ey = bx - ax, by - ay
        length = math.hypot(ex, ey)
        dist = ((u - ax) * ey - (v - ay) * ex) / length
        best = min(best, -dist)
        if dist > 0:
            inside = False
    return inside, best


def shard(seed):
    """An angular crystal sliver with a bright facet line and edge highlight."""
    img = Image(N, N)
    rnd = random.Random(seed)
    tilt = rnd.uniform(-0.6, 0.6)
    pts = []
    for ang, rad in ((-1.57, 0.95), (-0.5, 0.42), (0.9, 0.5), (1.6, 0.8), (2.6, 0.4)):
        a = ang + tilt + rnd.uniform(-0.15, 0.15)
        pts.append((math.cos(a) * rad * rnd.uniform(0.9, 1.05), math.sin(a) * rad * rnd.uniform(0.9, 1.05)))
    # make convex order by angle around the centroid
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    pts.sort(key=lambda p: math.atan2(p[1] - cy, p[0] - cx))
    for x, y, u, v in coords():
        inside, d = polygon_alpha(u, v, pts)
        if not inside:
            # anti-aliased rim
            a = clamp(1.0 + d * 14.0) * 0.6 if d > -0.07 else 0.0
            put(img, x, y, 1.0, a)
            continue
        facet = math.exp(-((u - cx) * math.sin(tilt + 0.7) - (v - cy) * math.cos(tilt + 0.7)) ** 2 / 0.004)
        edge = math.exp(-(d * d) / 0.004)
        shade = 0.7 + 0.3 * clamp(facet + edge)
        put(img, x, y, shade, 0.78 + 0.22 * clamp(facet + edge))
    return img


def petal(seed):
    """A lotus / blossom petal: a pointed ellipse with a soft vein, darker at the base."""
    img = Image(N, N)
    rnd = random.Random(seed)
    bend = rnd.uniform(-0.25, 0.25)
    fat = rnd.uniform(0.38, 0.5)
    for x, y, u, v in coords():
        t = (v + 1) / 2  # 0 top (tip) .. 1 base
        cu = u - bend * (1 - t) ** 2
        w = fat * math.sin(math.pi * clamp(t * 0.95 + 0.03)) ** 0.8 + 0.001
        d = abs(cu) / w
        a = 1.0 - smooth(0.82, 1.0, d)
        if t > 0.97:
            a = 0.0
        vein = math.exp(-(cu * cu) / 0.002) * 0.25
        shade = 0.82 + 0.18 * (1 - t) - vein * 0.3 + 0.08 * (1 - d)
        put(img, x, y, shade, a)
    return img


def snow(seed):
    """A six-armed snowflake with side twigs."""
    img = Image(N, N)
    rnd = random.Random(seed)
    twig = rnd.uniform(0.35, 0.6)
    turn = rnd.uniform(0, math.pi / 3)
    for x, y, u, v in coords():
        r = math.hypot(u, v)
        ang = (math.atan2(v, u) + turn) % (math.pi / 3)
        ang = min(ang, math.pi / 3 - ang)
        dist_arm = r * math.sin(ang)
        along = r * math.cos(ang)
        arm = math.exp(-(dist_arm ** 2) / 0.0035) * (1 - smooth(0.78, 0.92, along))
        # twigs at 60 deg leaving the arm at `twig`
        tw = 0.0
        if along > twig:
            off = along - twig
            tw = math.exp(-((dist_arm - off * 0.9) ** 2) / 0.0025) * (1 - smooth(0.22, 0.3, off))
        hub = math.exp(-(r * r) / 0.01)
        a = clamp(arm + tw + hub)
        put(img, x, y, 1.0, a)
    return img


def feather(seed):
    """A feather: a curved shaft with vaned sides fading at the tips (phoenix and crane arts)."""
    img = Image(N, N)
    rnd = random.Random(seed)
    bend = rnd.uniform(0.15, 0.3)
    for x, y, u, v in coords():
        t = (v + 1) / 2  # 0 tip .. 1 quill
        cx = bend * (t - 0.5) ** 2 * 2 - bend * 0.25
        du = u - cx
        width = 0.42 * math.sin(math.pi * clamp(t * 0.9 + 0.02)) ** 0.7 + 0.001
        d = abs(du) / width
        if t > 0.86:
            d = 9.0
        barbs = 0.85 + 0.15 * math.sin((t * 26.0 + abs(du) * 9.0))
        vane = (1 - smooth(0.75, 1.0, d)) * barbs
        shaft = math.exp(-(du * du) / 0.0015) * (1 - smooth(0.96, 1.0, t))
        a = clamp(vane * 0.85 + shaft)
        put(img, x, y, 0.8 + 0.2 * clamp(shaft + (1 - d) * 0.3), a)
    return img


QUAD = os.path.join(ROOT, "textures", "misc", "qi")


def put_quad(img, x, y, shade, alpha):
    """Quad textures for entityTranslucentEmissive: the alpha cutout runs before the vertex colour, so every visible pixel
    keeps alpha >= 0.1 (26) and fully clear pixels are 0 (fades go into the vertex alpha)."""
    a = clamp(alpha)
    put(img, x, y, shade, 0.0 if a < 0.02 else max(0.11, a))


def glow_disc(size=64):
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2 - 1
            v = (y + 0.5) / size * 2 - 1
            r = math.sqrt(u * u + v * v)
            a = (math.exp(-(r * r) / 0.12) * 0.85 + (1 - smooth(0.0, 0.25, r)) * 0.3) * (1 - smooth(0.85, 1.0, r))
            put_quad(img, x, y, 1.0, a)
    return img


def ring_flat(size=64):
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2 - 1
            v = (y + 0.5) / size * 2 - 1
            r = math.sqrt(u * u + v * v)
            band = math.exp(-((r - 0.86) ** 2) / 0.0035)
            haze = 0.22 * smooth(0.45, 0.86, r) * (1 - smooth(0.86, 0.95, r))
            a = clamp(band + haze) * (1 - smooth(0.95, 1.0, r))
            put_quad(img, x, y, 0.85 + 0.15 * band, a)
    return img


def beam_core(w=32, h=32):
    """u runs along the strip, v across it: a white-hot centre line in a soft halo."""
    img = Image(w, h)
    for y in range(h):
        v = (y + 0.5) / h * 2 - 1
        for x in range(w):
            core = math.exp(-(v * v) / 0.012)
            halo = math.exp(-(v * v) / 0.18) * 0.55
            a = clamp(core + halo) * (1 - smooth(0.9, 1.0, abs(v)))
            put_quad(img, x, y, 0.8 + 0.2 * core, a)
    return img


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(DEF, exist_ok=True)
    os.makedirs(QUAD, exist_ok=True)
    glow_disc().save(os.path.join(QUAD, "glow_disc.png"))
    ring_flat().save(os.path.join(QUAD, "ring_flat.png"))
    beam_core().save(os.path.join(QUAD, "beam_core.png"))
    sprites = {
        "qi_soft": soft(),
        "qi_glint": glint(),
        "qi_spark": spark(),
        "qi_ring": ring(),
    }
    for i in range(4):
        sprites["qi_smoke_%d" % i] = smoke(101 + i * 13)
        sprites["qi_flame_%d" % i] = flame(i)
    for i in range(3):
        sprites["qi_shard_%d" % i] = shard(201 + i * 7)
        sprites["qi_petal_%d" % i] = petal(301 + i * 5)
        sprites["qi_snow_%d" % i] = snow(401 + i * 3)
    for i in range(2):
        sprites["qi_feather_%d" % i] = feather(501 + i * 11)
    for name, img in sprites.items():
        img.save(os.path.join(TEX, name + ".png"))
    defs = {
        "qi": ["qi_soft", "qi_glint"],
        "qi_spark": ["qi_spark"],
        "qi_smoke": ["qi_smoke_%d" % i for i in range(4)],
        "qi_ring": ["qi_ring"],
        "qi_flame": ["qi_flame_%d" % i for i in range(4)],
        "qi_shard": ["qi_shard_%d" % i for i in range(3)],
        "qi_petal": ["qi_petal_%d" % i for i in range(3)],
        "qi_snow": ["qi_snow_%d" % i for i in range(3)],
        "qi_feather": ["qi_feather_%d" % i for i in range(2)],
    }
    for name, textures in defs.items():
        with open(os.path.join(DEF, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"textures": ["kultivator:" + t for t in textures]}, f, indent=2)
            f.write("\n")
    # A contact sheet (scratch preview, not a mod asset) when a path is given: python ... --sheet out.png
    import sys
    if "--sheet" in sys.argv:
        out = sys.argv[sys.argv.index("--sheet") + 1]
        names = list(sprites.keys())
        cols = 6
        scale = 4
        rows = (len(names) + cols - 1) // cols
        sheet = Image(cols * N * scale, rows * N * scale, (24, 20, 40, 255))
        for k, name in enumerate(names):
            img = sprites[name]
            ox, oy = (k % cols) * N * scale, (k // cols) * N * scale
            for y in range(N):
                for x in range(N):
                    r, g, b, a = img.get(x, y)
                    # preview tinted gold so the shading reads
                    col = (int(r * 1.0), int(g * 0.85), int(b * 0.45), a)
                    for dy in range(scale):
                        for dx in range(scale):
                            sheet.blend(ox + x * scale + dx, oy + y * scale + dy, col)
        sheet.save(out)
    print("wrote %d sprites and %d particle definitions" % (len(sprites), len(defs)))


if __name__ == "__main__":
    main()
