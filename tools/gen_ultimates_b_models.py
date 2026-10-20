"""Generates the 3D props of module U2 (DESIGN 30.4.6-30.4.9): the Body, Life, Shadow and Immortal ultimates and the
own arts. Everything here is ours: cuboid item models and procedural textures, nothing is downloaded and no Kenney
sprite is used on a model.

Prefixes (one per spec): cgf_ cosmic_giant_form, agt_ ancient_god_trample, gsd_ guanyin_sweet_dew, ldd_
life_death_domain, bt_ bodhisattva_tree, sts_ stygian_tiger_seal, kp_ kunpeng_art, kd_ karma_domain, nh_ ninth_hex,
fem_ five_elements_mountain, tcd_ tai_chi_diagram, ht_ heaven_trampling, oa_ own_art_1..3.

Writes:
  textures/item/fx/<prefix>*.png   16x16 .. 64x64 textures
  models/item/fx/<id>.json         one cuboid model per prop
  items/fx/<id>.json               the item definitions (kultivator:fx/<id>)
  tools/fx_model_ids_ub.txt        this module's id list (tools/fx_model_ids.txt is never touched)

Rules kept by every model (checked by validate()):
  * the anchor is (8, 8, 8); the front faces north (-z);
  * element coordinates within -16..32, uv within 0..16, at most 40 elements;
  * an element rotation is a single axis by a multiple of 22.5 between -45 and 45;
  * rotations follow the right hand (Y by +a turns +x towards -z, X by +a turns +y towards +z, Z by +a turns +x
    towards +y).

Face uv conventions (vanilla defaults): north u0 at x = to.x, south u0 at x = from.x, west u0 at z = from.z, east u0 at
z = to.z, up v0 at z = from.z, down v0 at z = to.z; on side faces v0 is the top.

Run with: python tools/gen_ultimates_b_models.py   (validates every model and texture and prints a summary with the
lowest point of each model for FxModels' baseY)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")
IDS_FILE = os.path.join(os.path.dirname(__file__), "fx_model_ids_ub.txt")
NS = "kultivator"
PREFIXES = ("cgf_", "agt_", "gsd_", "ldd_", "bt_", "sts_", "kp_", "kd_", "nh_", "fem_", "tcd_", "ht_", "oa_")

TEXTURES = {}
MODELS = {}


# ================================================================================================ colour helpers

def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        if t <= t1:
            return mix(c0, c1, (t - t0) / max(1e-6, t1 - t0))
    return stops[-1][1]


def clamp01(v):
    return max(0.0, min(1.0, v))


def smooth(e0, e1, x):
    t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


WHITE = (255, 255, 255)
BLACK = (0, 0, 0)


class Noise:
    def __init__(self, seed):
        r = random.Random(seed)
        self.p = list(range(256))
        r.shuffle(self.p)
        self.p += self.p
        self.g = [r.random() for _ in range(256)]

    def lattice(self, x, y):
        return self.g[self.p[(self.p[x & 255] + y) & 255]]

    def smooth(self, x, y):
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.lattice(x0, y0) + (self.lattice(x0 + 1, y0) - self.lattice(x0, y0)) * sx
        b = self.lattice(x0, y0 + 1) + (self.lattice(x0 + 1, y0 + 1) - self.lattice(x0, y0 + 1)) * sx
        return a + (b - a) * sy

    def fbm(self, x, y, octaves=3):
        total, amp, norm, f = 0.0, 1.0, 0.0, 1.0
        for _ in range(octaves):
            total += self.smooth(x * f, y * f) * amp
            norm += amp
            amp *= 0.5
            f *= 2
        return total / norm


def tex(name, size, fn):
    """fn(u, v) -> (r, g, b, a) with u, v the pixel centre in 0..1 (v = 0 at the top)."""
    assert name.startswith(PREFIXES), name
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            c = fn((x + 0.5) / size, (y + 0.5) / size)
            img.set(x, y, (c[0], c[1], c[2], int(max(0, min(255, round(c[3] if len(c) > 3 else 255))))))
    TEXTURES[name] = img
    return name


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 <= 0 else clamp01(((px - ax) * dx + (py - ay) * dy) / l2)
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t))


def poly_dist(px, py, pts):
    return min(seg_dist(px, py, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]) for i in range(len(pts) - 1))


# ================================================================================================ texture kinds

def noise_tex(name, c0, c1, seed, scale=5.0, size=32, alpha=255, grain=0.10, c2=None):
    """A two-colour cloudy surface (a third colour for the brightest blotches)."""
    n = Noise(seed)
    a0, a1 = rgb(c0), rgb(c1)
    a2 = rgb(c2) if c2 else None

    def fn(u, v):
        g = n.fbm(u * scale, v * scale)
        c = mix(a0, a1, g)
        if a2 and g > 0.62:
            c = mix(c, a2, (g - 0.62) * 2.5)
        c = mix(c, WHITE, grain * (n.fbm(u * scale * 3 + 7, v * scale * 3) - 0.5))
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def stone_tex(name, base, dark, light, seed, cracks=0.5, vein=None, size=32):
    """Weathered stone: blotchy body, darker cracks (and glowing veins in the cracks when vein is set)."""
    n = Noise(seed)
    m = Noise(seed + 101)
    b, d, l = rgb(base), rgb(dark), rgb(light)
    vc = rgb(vein) if vein else None

    def fn(u, v):
        g = n.fbm(u * 4.0, v * 4.0)
        c = mix(d, b, smooth(0.2, 0.55, g))
        c = mix(c, l, smooth(0.6, 0.85, g) * 0.8)
        k = abs(m.fbm(u * 3.0 + 1.3, v * 3.0, 4) - 0.5)
        if k < 0.035 * cracks:
            c = mix(c, vc, 0.85) if vc else mix(c, d, 0.75)
        speck = n.fbm(u * 19.0, v * 19.0)
        c = mix(c, l, 0.25 * smooth(0.72, 0.8, speck))
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def metal_tex(name, base, hi, lo, seed, size=32, engrave=True):
    """Polished metal: a vertical sheen, a few engraved lines, soft blotches."""
    n = Noise(seed)
    b, h, lo_ = rgb(base), rgb(hi), rgb(lo)

    def fn(u, v):
        sheen = 0.5 + 0.5 * math.cos((u * 1.6 + v * 0.4) * math.tau)
        c = mix(lo_, b, 0.55 + 0.45 * sheen)
        c = mix(c, h, 0.55 * smooth(0.82, 1.0, sheen))
        c = mix(c, lo_, 0.25 * smooth(0.55, 0.8, n.fbm(u * 5, v * 5)))
        if engrave and (abs(v - 0.18) < 0.03 or abs(v - 0.82) < 0.03):
            c = mix(c, lo_, 0.6)
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def band_tex(name, centre, body, alpha, along_u=False):
    """A ring band profile: transparent edges, a hot centre line (across v, or across u when along_u)."""
    c0, c1 = rgb(centre), rgb(body)

    def fn(u, v):
        s = u if along_u else v
        d = abs(s - 0.5) * 2.0
        k = clamp01(1.0 - d)
        c = mix(c1, c0, k ** 2.2)
        return (c[0], c[1], c[2], alpha * (k ** 0.8))
    return tex(name, 32, fn)


def pearl_tex(name, core, rim, alpha=255, size=16):
    c0, c1 = rgb(core), rgb(rim)

    def fn(u, v):
        d = math.hypot(u - 0.38, v - 0.36)
        c = mix(c0, c1, clamp01(d * 1.6))
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def glow_disc_tex(name, core, rim, size=32, alpha=230, power=1.6):
    """A round soft glow (transparent outside the circle)."""
    c0, c1 = rgb(core), rgb(rim)

    def fn(u, v):
        r = math.hypot(u - 0.5, v - 0.5) * 2.0
        if r > 1.0:
            return (0, 0, 0, 0)
        c = mix(c0, c1, r)
        return (c[0], c[1], c[2], alpha * (1.0 - r) ** power)
    return tex(name, size, fn)


def star_tex(name, core, glow, size=32):
    """A four-pointed star with a soft halo."""
    c0, c1 = rgb(core), rgb(glow)

    def fn(u, v):
        dx, dy = abs(u - 0.5) * 2.0, abs(v - 0.5) * 2.0
        r = math.hypot(dx, dy)
        arm = max(clamp01(1.0 - dx / max(1e-3, 0.16 * (1.0 - dy))) * (1.0 - dy),
                  clamp01(1.0 - dy / max(1e-3, 0.16 * (1.0 - dx))) * (1.0 - dx))
        halo = clamp01(1.0 - r) ** 2.5
        a = max(arm ** 0.8, halo * 0.75)
        c = mix(c1, c0, clamp01(arm * 1.4))
        return (c[0], c[1], c[2], 255 * a)
    return tex(name, size, fn)


def glyph_tex(name, rows, fg, bg, size=32, bg_alpha=255, border=None, glow=None):
    """A 16x16 bitmap glyph scaled to the texture, on a plate colour (with an optional border and a soft glow)."""
    f, b = rgb(fg), rgb(bg)
    bc = rgb(border) if border else None
    gc = rgb(glow) if glow else None
    cells = [[ch == "#" for ch in row] for row in rows]

    def at(x, y):
        return 0 <= y < 16 and 0 <= x < 16 and cells[y][x]

    def fn(u, v):
        x, y = int(u * 16), int(v * 16)
        if bc and (u < 0.06 or u > 0.94 or v < 0.06 or v > 0.94):
            return (bc[0], bc[1], bc[2], 255)
        if at(x, y):
            return (f[0], f[1], f[2], 255)
        if gc:
            near = any(at(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near:
                c = mix(b, gc, 0.6)
                return (c[0], c[1], c[2], max(bg_alpha, 160))
        return (b[0], b[1], b[2], bg_alpha)
    return tex(name, size, fn)


def foot_shape(u, v):
    """Signed distance-like value (> 0 inside) of a footprint silhouette, toes at the top (v small)."""
    best = -1.0
    # the sole: an ellipse narrowing to the heel
    cx, cy = 0.5, 0.58
    sx = 0.20 + 0.06 * (1.0 - clamp01((v - 0.3) / 0.6))
    sy = 0.34
    d = 1.0 - math.hypot((u - cx) / sx, (v - cy) / sy)
    best = max(best, d)
    for (tx, ty, r) in ((0.40, 0.18, 0.075), (0.52, 0.15, 0.06), (0.62, 0.17, 0.05), (0.70, 0.21, 0.043), (0.76, 0.27, 0.036)):
        best = max(best, 1.0 - math.hypot(u - tx, v - ty) / r)
    return best


def footprint_tex(name, core, edge, seed, star=None):
    """A flat glowing footprint glyph (transparent outside), a hot outline, faint inside (a star in the heel)."""
    n = Noise(seed)
    c0, c1 = rgb(core), rgb(edge)
    sc = rgb(star) if star else None

    def fn(u, v):
        d = foot_shape(u, v)
        if d < -0.12:
            return (0, 0, 0, 0)
        if d < 0.0:
            k = 1.0 + d / 0.12
            return (c1[0], c1[1], c1[2], 120 * k * k)
        edge_k = clamp01(1.0 - d / 0.22)
        c = mix(c0, c1, edge_k)
        a = 90 + 160 * edge_k ** 1.5 + 30 * (n.fbm(u * 6, v * 6) - 0.5)
        if sc:
            dx, dy = abs(u - 0.5) * 2, abs(v - 0.74) * 2
            arm = max(clamp01(1.0 - dx / max(1e-3, 0.05 * (1.0 - dy * 3))) * (1.0 - dy * 3),
                      clamp01(1.0 - dy * 3 / max(1e-3, 0.18 * (1.0 - dx))) * (1.0 - dx))
            if arm > 0.05:
                c = mix(c, sc, clamp01(arm * 2))
                a = max(a, 255 * clamp01(arm * 2))
        return (c[0], c[1], c[2], a)
    return tex(name, 64, fn)


def flame_tex(name, stops, seed, tongues=3, alpha=235, width=0.40, size=32):
    n = Noise(seed)

    def fn(u, v):
        t = 1.0 - v
        if t < 0.03 or t > 0.98:
            return (0, 0, 0, 0)
        sway = (n.fbm(t * 2.2 + 5.0, 0.5, 3) - 0.5) * 0.30 * t
        x = u - 0.5 - sway
        if t < 0.25:
            env = width * math.sqrt(max(0.0, 1.0 - ((0.25 - t) / 0.24) ** 2))
        else:
            env = width * (1.0 - (t - 0.25) / 0.75) ** 0.85
        phase = (x / max(1e-6, width) + 1.0) * 0.5 * tongues + (n.fbm(t * 3.0, x * 2.0 + 9.0) - 0.5) * 1.2
        lick = 0.5 + 0.5 * math.cos(phase * math.tau)
        env *= 1.0 - smooth(0.30, 0.95, t) * (1.0 - lick) * 0.95
        edge = (env - abs(x)) / max(1e-6, env)
        if env <= 0.0 or edge <= 0.0:
            return (0, 0, 0, 0)
        heat = clamp01(edge * 0.75 + (1.0 - t) * 0.5)
        c = ramp(stops, 1.0 - heat)
        return (c[0], c[1], c[2], alpha * clamp01(edge * 2.5) * clamp01((0.98 - t) * 6.0))
    return tex(name, size, fn)


def petal_tex(name, base, tip, rim, seed, alpha=235, size=32, wilt=False):
    """A petal silhouette (base at the bottom, pointed tip at the top), a gradient to the tip and a light rim."""
    n = Noise(seed)
    b, t_, r = rgb(base), rgb(tip), rgb(rim)

    def fn(u, v):
        t = 1.0 - v
        if wilt:
            u = u + 0.12 * math.sin(t * 2.6) * t
        hw = 0.46 * math.sin(math.pi * min(1.0, t * 0.9 + 0.08)) ** 0.8 * (1.0 - t ** 3)
        du = abs(u - 0.5)
        edge = (hw - du) * size
        if hw <= 0 or edge < -0.5:
            return (0, 0, 0, 0)
        c = mix(b, t_, t ** 1.2)
        c = mix(c, r, 0.5 * clamp01(1.0 - edge / 2.5))
        c = mix(c, WHITE, 0.1 * (n.fbm(u * 6, v * 6) - 0.5))
        if wilt:
            c = mix(c, rgb("5A5A5A"), 0.35 * n.fbm(u * 9, v * 9))
        return (c[0], c[1], c[2], alpha * clamp01(edge + 0.5))
    return tex(name, size, fn)


def leaf_tex(name, light, dark, seed, size=16, tip=None):
    """A narrow leaf (willow, bodhi) pointing up with a midrib."""
    n = Noise(seed)
    l, d = rgb(light), rgb(dark)
    tc = rgb(tip) if tip else None

    def fn(u, v):
        t = 1.0 - v
        hw = 0.42 * math.sin(math.pi * clamp01(t)) ** 0.7
        du = abs(u - 0.5)
        if du > hw:
            return (0, 0, 0, 0)
        c = mix(d, l, 0.5 + 0.5 * (du / max(1e-3, hw)))
        if du < 0.04:
            c = mix(c, l, 0.7)
        if tc:
            c = mix(c, tc, smooth(0.6, 1.0, t))
        c = mix(c, WHITE, 0.08 * (n.fbm(u * 5, v * 5) - 0.5))
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def foliage_tex(name, light, dark, seed, tip=None, under=False, size=32):
    """Leaf masses: overlapping round leaves with light tops (gold tips when tip is set)."""
    n = Noise(seed)
    rnd = random.Random(seed)
    l, d = rgb(light), rgb(dark)
    tc = rgb(tip) if tip else None
    blobs = [(rnd.random(), rnd.random(), 0.08 + rnd.random() * 0.08) for _ in range(22)]

    def fn(u, v):
        c = mix(d, l, 0.3 + 0.4 * n.fbm(u * 4, v * 4))
        for (bx, by, br) in blobs:
            dd = math.hypot(u - bx, v - by) / br
            if dd < 1.0:
                c = mix(c, l, (1.0 - dd) * 0.55)
                if tc and (by - v) > br * 0.3:
                    c = mix(c, tc, 0.5 * (1.0 - dd))
        if under:
            c = mix(c, d, 0.45)
        if tc and v < 0.25:
            c = mix(c, tc, 0.35 * (0.25 - v) * 4)
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def bark_tex(name, base, dark, seed, moss=None, vein=None, size=32):
    """Bark: vertical ridges with dark furrows (moss patches or glowing veins in the furrows when set)."""
    n = Noise(seed)
    b, d = rgb(base), rgb(dark)
    mc = rgb(moss) if moss else None
    vc = rgb(vein) if vein else None

    def fn(u, v):
        w = n.fbm(u * 2.0, v * 6.0) * 0.6
        ridge = 0.5 + 0.5 * math.cos((u * 7.0 + w) * math.tau)
        c = mix(d, b, ridge ** 0.7)
        c = mix(c, WHITE, 0.08 * (n.fbm(u * 9, v * 3) - 0.5))
        if vc and ridge < 0.12:
            c = mix(c, vc, 0.9)
        if mc and n.fbm(u * 3 + 4, v * 3) > 0.6:
            c = mix(c, mc, 0.75)
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def paper_tex(name, paper, ink, seed, script=True, torn=False, size=32, alpha=255):
    """Talisman paper with a vertical line of script (and torn holes when torn)."""
    n = Noise(seed)
    rnd = random.Random(seed)
    p, k = rgb(paper), rgb(ink)
    strokes = []
    y = 0.1
    while y < 0.88:
        h = 0.05 + rnd.random() * 0.07
        kind = rnd.randint(0, 3)
        strokes.append((y, h, kind))
        y += h + 0.04

    def fn(u, v):
        c = mix(p, rgb("C8A860"), 0.25 * n.fbm(u * 6, v * 6))
        if torn and n.fbm(u * 5 + 3, v * 5) > 0.68:
            return (0, 0, 0, 0)
        if u < 0.08 or u > 0.92:
            c = mix(c, k, 0.65)
        if script:
            for (y0, h, kind) in strokes:
                if y0 <= v <= y0 + h:
                    t = (v - y0) / h
                    hit = False
                    if kind == 0:
                        hit = abs(u - 0.5) < 0.05 or abs(t - 0.5) < 0.12 and 0.3 < u < 0.7
                    elif kind == 1:
                        hit = abs(u - (0.32 + 0.36 * t)) < 0.06 or abs(t - 0.2) < 0.12 and 0.28 < u < 0.72
                    elif kind == 2:
                        hit = (abs(u - 0.35) < 0.05 or abs(u - 0.65) < 0.05) and t > 0.15 or abs(t - 0.15) < 0.12 and 0.3 < u < 0.7
                    else:
                        hit = math.hypot(u - 0.5, (t - 0.5) * 0.6) < 0.16 and math.hypot(u - 0.5, (t - 0.5) * 0.6) > 0.09
                    if hit:
                        c = mix(c, k, 0.9)
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def scales_tex(name, base, glint, seed, size=32, belly=None):
    """Fish scales: overlapping arcs with silver glints."""
    n = Noise(seed)
    b, g = rgb(base), rgb(glint)

    def fn(u, v):
        row = v * 8.0
        off = 0.5 if int(row) % 2 else 0.0
        su = (u * 8.0 + off) % 1.0
        sv = row % 1.0
        d = math.hypot(su - 0.5, sv - 0.1)
        c = mix(b, g, 0.45 * smooth(0.35, 0.5, d) * (1.0 - smooth(0.5, 0.58, d)))
        c = mix(c, b, 0.3 * n.fbm(u * 4, v * 4))
        if belly:
            c = mix(c, rgb(belly), smooth(0.65, 1.0, v))
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def feather_tex(name, base, tip, seed, size=32, alpha=255):
    """Feather rows: barbs slanting down to the tips (the bottom of the texture) in the tip colour."""
    n = Noise(seed)
    b, t_ = rgb(base), rgb(tip)

    def fn(u, v):
        barb = 0.5 + 0.5 * math.sin((u * 10.0 + v * 4.0) * math.pi)
        c = mix(b, t_, smooth(0.65, 1.0, v))
        c = mix(c, WHITE, 0.12 * barb * (1.0 - v))
        c = mix(c, b, 0.2 * n.fbm(u * 6, v * 6))
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def disc_alpha(u, v):
    """Distance from the texture centre in units of the inscribed circle's radius."""
    return math.hypot(u - 0.5, v - 0.5) * 2.0


# ================================================================================================ the model DSL

SIDES = ("north", "south", "east", "west", "up", "down")


class Model:
    def __init__(self, mid):
        assert mid.startswith(PREFIXES), mid
        self.id = mid
        self.els = []
        self.tex = {}
        MODELS[mid] = self

    def key(self, name):
        if name not in self.tex:
            self.tex[name] = "t%d" % len(self.tex)
        return self.tex[name]

    def el(self, f, t, faces, rot=None, glow=0):
        f2 = [round(min(f[i], t[i]), 4) for i in range(3)]
        t2 = [round(max(f[i], t[i]), 4) for i in range(3)]
        e = {"from": f2, "to": t2, "faces": {}}
        for side, spec in faces.items():
            name, uv = spec
            e["faces"][side] = {"uv": [round(x, 4) for x in (uv or (0, 0, 16, 16))], "texture": "#" + self.key(name)}
        if rot:
            axis, angle, origin = rot
            if angle != 0:
                e["rotation"] = {"origin": [round(x, 4) for x in origin], "axis": axis, "angle": angle}
        if glow:
            e["light_emission"] = int(glow)
        self.els.append(e)
        return e


def faces_of(tex_spec, sides=SIDES):
    """tex_spec: a texture name, or a dict side -> name ('*' for the rest; None leaves a side out)."""
    out = {}
    for s in sides:
        if isinstance(tex_spec, dict):
            name = tex_spec.get(s, tex_spec.get("*"))
        else:
            name = tex_spec
        if name:
            out[s] = (name, None)
    return out


def box(m, f, t, tex_spec, rot=None, glow=0, sides=SIDES):
    return m.el(f, t, faces_of(tex_spec, sides), rot=rot, glow=glow)


def cbox(m, c, size, tex_spec, rot=None, glow=0, sides=SIDES, rot_axis=None, rot_angle=0.0):
    f = [c[i] - size[i] / 2.0 for i in range(3)]
    t = [c[i] + size[i] / 2.0 for i in range(3)]
    if rot_axis:
        rot = (rot_axis, rot_angle, list(c))
    return m.el(f, t, faces_of(tex_spec, sides), rot=rot, glow=glow)


def octagon(m, cx, cz, r, y0, y1, side, top=None, bottom=None, glow=0):
    """An octagonal prism: two boxes, the second turned 45 degrees and a hair shorter (no coplanar caps)."""
    spec = {"*": side, "up": top or side, "down": bottom or side}
    m.el([cx - r, y0, cz - r], [cx + r, y1, cz + r], faces_of(spec), glow=glow)
    m.el([cx - r, y0 + 0.02, cz - r], [cx + r, y1 - 0.03, cz + r], faces_of(spec), rot=("y", 45.0, [cx, y0, cz]), glow=glow)


def norm_angle(angle):
    a = ((angle + 90.0) % 180.0) - 90.0
    if a > 45.0:
        return a - 90.0, True
    if a < -45.0:
        return a + 90.0, True
    return a, False


def ring(m, c, r, width, th, n, band, band_u, plane="xz", glow=15, sides=None, offset_deg=0.0, overlap=1.06):
    """A ring of n band segments around c, flat in the XZ plane (turned about y) or standing in the XY plane (about z)."""
    length = 2.0 * math.pi * r / n * overlap
    for i in range(n):
        theta = offset_deg + 360.0 * i / n
        k = 0.97 if i % 2 else 1.0
        if plane == "xz":
            px, pz = c[0] + r * math.cos(math.radians(theta)), c[2] + r * math.sin(math.radians(theta))
            a, swapped = norm_angle(-(theta + 90.0))
            fs = sides or ("up", "down")
            if not swapped:
                size = (length, th * k, width * k)
                spec = band
            else:
                size = (width * k, th * k, length)
                spec = band_u
            cbox(m, (px, c[1], pz), size, spec, rot=("y", a, [px, c[1], pz]), glow=glow, sides=fs)
        else:
            px, py = c[0] + r * math.cos(math.radians(theta)), c[1] + r * math.sin(math.radians(theta))
            a, swapped = norm_angle(theta + 90.0)
            fs = sides or ("north", "south")
            if not swapped:
                size = (length, width * k, th * k)
                spec = band
            else:
                size = (width * k, length, th * k)
                spec = band_u
            cbox(m, (px, py, c[2]), size, spec, rot=("z", a, [px, py, c[2]]), glow=glow, sides=fs)


def ring_model(mid, band, band_u, radius=20.0, inner=16.6, width=3.0, inner_width=1.2):
    """A flat shock ring of 16 band segments and a thin inner ring of 16 in the XZ plane, centred on the anchor."""
    m = Model(mid)
    ring(m, (8.0, 8.0, 8.0), radius, width, 0.5, 16, band, band_u)
    ring(m, (8.0, 8.12, 8.0), inner, inner_width, 0.4, 16, band, band_u)
    return m


def mirror_x(src, mid):
    """A copy of {@code src} mirrored about x = 8 (left/right pieces)."""
    m = Model(mid)
    m.tex = dict(src.tex)
    for e in src.els:
        f, t = e["from"], e["to"]
        ne = {"from": [round(16.0 - t[0], 4), f[1], f[2]], "to": [round(16.0 - f[0], 4), t[1], t[2]], "faces": {}}
        for side, face in e["faces"].items():
            ns = {"east": "west", "west": "east"}.get(side, side)
            ne["faces"][ns] = dict(face)
        if "rotation" in e:
            r = e["rotation"]
            o = r["origin"]
            ang = r["angle"] if r["axis"] == "x" else -r["angle"]
            ne["rotation"] = {"origin": [round(16.0 - o[0], 4), o[1], o[2]], "axis": r["axis"], "angle": ang}
        if "light_emission" in e:
            ne["light_emission"] = e["light_emission"]
        m.els.append(ne)
    return m


# ================================================================================================ the 封 glyph (seal)

FENG = [
    "................",
    "...#........#...",
    "...#........#...",
    ".#####......#...",
    "...#...#########",
    "...#........#...",
    "#######.....#...",
    "............#...",
    "...#..##....#...",
    "...#...##...#...",
    ".#####..#...#...",
    "...#........#...",
    "...#........#...",
    "#######.....#...",
    "..........###...",
    "................",
]

# ================================================================================================ Body · cosmic_giant_form


def build_cgf():
    stone_tex("cgf_stone", "C8A040", "6E5422", "E8CC80", 101, cracks=0.8, vein="FFD54A")
    stone_tex("cgf_stone_dark", "A8862E", "4E3A16", "D0B060", 102, cracks=0.6)
    metal_tex("cgf_gold", "FFD54A", "FFF6C8", "B08A20", 103)
    rnd = random.Random(104)
    specks = [(rnd.random(), rnd.random(), rnd.random()) for _ in range(40)]

    def sash(u, v):
        c = mix(rgb("3A3A8A"), rgb("1C1C50"), 0.4 * Noise(105).fbm(u * 4, v * 4))
        for (x, y, b) in specks:
            d = math.hypot(u - x, v - y)
            if d < 0.035:
                c = mix(c, rgb("FFF2B0") if b > 0.6 else WHITE, 1.0 - d / 0.035)
        return (c[0], c[1], c[2], 255)
    tex("cgf_sash", 32, sash)
    n = Noise(106)

    def face(u, v):
        g = n.fbm(u * 4, v * 4)
        c = mix(rgb("8A6A2A"), rgb("C8A040"), smooth(0.2, 0.6, g))
        # brow line, eye sockets, nose ridge, mouth, the third eye mark
        if 0.36 < v < 0.40 and 0.12 < u < 0.88:
            c = mix(c, rgb("4E3A16"), 0.6)
        for ex in (0.3, 0.7):
            if math.hypot((u - ex) / 1.6, v - 0.47) < 0.06:
                c = mix(c, rgb("2A1E0C"), 0.8)
        if abs(u - 0.5) < 0.05 and 0.42 < v < 0.68:
            c = mix(c, rgb("E8CC80"), 0.5)
        if 0.66 < v < 0.72 and abs(u - 0.5) < 0.12:
            c = mix(c, rgb("D8B860"), 0.6)
        if 0.78 < v < 0.81 and abs(u - 0.5) < 0.16:
            c = mix(c, rgb("4E3A16"), 0.7)
        if math.hypot(u - 0.5, (v - 0.27) * 0.6) < 0.035:
            c = mix(c, rgb("FFF2B0"), 0.9)
        return (c[0], c[1], c[2], 255)
    tex("cgf_face", 32, face)
    pearl_tex("cgf_eye", "FFFFFF", "FFC838")

    def halo(u, v):
        r = disc_alpha(u, v)
        ang = math.atan2(v - 0.5, u - 0.5)
        if r > 1.0:
            return (0, 0, 0, 0)
        rays = 0.5 + 0.5 * math.cos(ang * 16)
        ringk = math.exp(-((r - 0.82) / 0.07) ** 2)
        a = 230 * ringk + 110 * rays ** 4 * smooth(0.25, 0.75, r) * (1.0 - r)
        c = mix(rgb("FFF6C8"), rgb("FFD54A"), r)
        return (c[0], c[1], c[2], a)
    tex("cgf_halo", 64, halo)
    footprint_tex("cgf_footprint", "FFE88A", "FFF8D8", 107)
    band_tex("cgf_ring", "FFF8E0", "C8A040", 235)
    band_tex("cgf_ring_u", "FFF8E0", "C8A040", 235, along_u=True)

    st, sd, gd, sh = "cgf_stone", "cgf_stone_dark", "cgf_gold", "cgf_sash"
    # ---- the torso and head (anchor = the pelvis centre; legs hang below it, arms from the shoulders)
    m = Model("cgf_giant")
    box(m, [4.0, 3.5, 4.0], [12.0, 8.6, 5.0], gd)                                  # front tasset
    box(m, [4.0, 3.5, 11.0], [12.0, 8.6, 12.0], gd)                                # back tasset
    box(m, [2.6, 4.0, 5.0], [3.6, 8.6, 11.0], sd)                                  # side tassets
    box(m, [12.4, 4.0, 5.0], [13.4, 8.6, 11.0], sd)
    box(m, [3.0, 7.0, 5.0], [13.0, 10.5, 11.0], st)                                # pelvis
    box(m, [2.8, 10.2, 4.6], [13.2, 12.0, 11.4], gd)                               # belt
    cbox(m, (8.0, 11.1, 4.35), (2.2, 1.6, 0.5), "cgf_eye", glow=12)                # buckle
    box(m, [4.0, 12.0, 5.5], [12.0, 14.2, 10.5], st)                               # waist
    box(m, [1.5, 14.0, 4.0], [14.5, 21.5, 12.0], {"*": st, "down": sd})            # chest
    box(m, [3.0, 15.0, 3.5], [13.0, 20.5, 4.0], gd)                                # chest plate
    cbox(m, (8.0, 18.0, 3.3), (1.4, 1.4, 0.4), "cgf_eye", glow=15)                 # chest gem
    cbox(m, (8.0, 16.5, 3.15), (2.2, 15.0, 0.4), sh, rot_axis="z", rot_angle=45.0)     # sash front
    cbox(m, (8.0, 16.5, 12.35), (2.2, 15.0, 0.5), sh, rot_axis="z", rot_angle=-45.0)   # sash back
    box(m, [7.2, 1.5, 3.7], [8.8, 10.2, 4.2], sh)                                  # sash tail
    box(m, [-1.5, 18.5, 3.8], [3.5, 22.6, 12.2], gd)                               # pauldrons
    box(m, [12.5, 18.5, 3.8], [17.5, 22.6, 12.2], gd)
    box(m, [-1.2, 22.6, 5.0], [3.2, 23.4, 11.0], st)                               # pauldron crests
    box(m, [12.8, 22.6, 5.0], [17.2, 23.4, 11.0], st)
    box(m, [6.3, 21.5, 6.3], [9.7, 23.5, 9.7], st)                                 # neck
    box(m, [4.5, 23.0, 4.5], [11.5, 30.0, 11.5], {"*": st, "north": "cgf_face"})  # head
    box(m, [4.6, 27.1, 4.0], [11.4, 27.7, 4.6], sd)                                # brow ridge
    cbox(m, (6.6, 26.3, 4.25), (1.4, 0.8, 0.5), "cgf_eye", glow=15)                # burning eyes
    cbox(m, (9.4, 26.3, 4.25), (1.4, 0.8, 0.5), "cgf_eye", glow=15)
    cbox(m, (8.0, 28.4, 4.3), (0.8, 1.0, 0.4), "cgf_eye", glow=15)                 # third eye
    box(m, [4.2, 29.2, 4.2], [11.8, 30.6, 11.8], gd)                               # crown band
    cbox(m, (8.0, 31.2, 4.6), (1.4, 1.6, 0.6), gd)                                 # crown points
    cbox(m, (5.6, 31.0, 4.6), (1.0, 1.2, 0.6), gd)
    cbox(m, (10.4, 31.0, 4.6), (1.0, 1.2, 0.6), gd)
    cbox(m, (4.6, 31.0, 8.0), (0.6, 1.2, 1.2), gd)
    cbox(m, (11.4, 31.0, 8.0), (0.6, 1.2, 1.2), gd)
    cbox(m, (8.0, 31.0, 11.4), (1.2, 1.2, 0.6), gd)
    box(m, [2.0, 20.0, 12.6], [14.0, 32.0, 12.8], {"north": "cgf_halo", "south": "cgf_halo"}, glow=15, sides=("north", "south"))
    # ---- a leg (anchor = the hip joint; the foot points north)
    leg = Model("cgf_leg_l")
    box(leg, [5.5, -3.0, 5.5], [10.5, 8.0, 10.5], st)                             # thigh
    box(leg, [5.3, 5.4, 5.3], [10.7, 7.0, 10.7], gd)                               # thigh band
    box(leg, [5.2, -5.5, 4.8], [10.8, -1.5, 7.5], gd)                              # knee guard
    cbox(leg, (4.9, -3.5, 6.2), (0.8, 1.4, 1.4), gd, rot_axis="z", rot_angle=22.5)  # outer spike
    box(leg, [6.0, -13.0, 6.0], [10.0, -3.0, 10.0], st)                            # shin
    box(leg, [5.7, -12.0, 5.4], [10.3, -5.0, 6.2], gd)                             # greave
    box(leg, [5.6, -13.6, 5.6], [10.4, -12.4, 10.4], gd)                           # anklet
    box(leg, [5.4, -16.0, 2.5], [10.6, -13.6, 11.0], {"*": st, "down": sd})        # foot
    cbox(leg, (6.4, -15.2, 1.9), (1.3, 1.5, 1.4), sd)                              # toes
    cbox(leg, (8.0, -15.1, 1.8), (1.5, 1.7, 1.6), sd)
    cbox(leg, (9.6, -15.2, 1.9), (1.3, 1.5, 1.4), sd)
    cbox(leg, (8.0, -9.0, 5.25), (1.0, 1.0, 0.4), "cgf_eye", glow=12)              # greave gem
    mirror_x(leg, "cgf_leg_r")
    # ---- an arm (anchor = the shoulder joint; it hangs down, the knuckles north)
    arm = Model("cgf_arm_l")
    box(arm, [5.5, -2.0, 5.5], [10.5, 8.0, 10.5], st)                             # upper arm
    box(arm, [5.3, 3.0, 5.3], [10.7, 4.4, 10.7], sh)                               # armlet
    box(arm, [5.2, -3.2, 5.2], [10.8, -1.6, 10.8], gd)                             # elbow band
    box(arm, [5.8, -10.0, 5.8], [10.2, -2.0, 10.2], st)                            # forearm
    box(arm, [5.4, -9.4, 5.4], [10.6, -4.6, 10.6], gd)                             # bracer
    cbox(arm, (8.0, -7.0, 5.2), (1.0, 1.0, 0.4), "cgf_eye", glow=12)
    box(arm, [5.0, -15.6, 4.6], [11.0, -10.0, 11.4], {"*": st, "down": sd})       # fist
    box(arm, [5.2, -15.0, 4.2], [10.8, -12.6, 4.7], gd)                            # knuckle plate
    box(arm, [10.8, -13.6, 5.4], [11.9, -11.0, 8.2], sd)                           # thumb (inner side)
    mirror_x(arm, "cgf_arm_r")
    fp = Model("cgf_footprint")
    box(fp, [-8.0, 8.0, -8.0], [24.0, 8.1, 24.0], {"up": "cgf_footprint", "down": "cgf_footprint"}, glow=15, sides=("up", "down"))
    ring_model("cgf_ring", "cgf_ring", "cgf_ring_u")


# ================================================================================================ Body · ancient_god_trample

def foot_model(mid, skin, dark, instep, nail, ring_tex, leg, glow=0):
    """An ancient god's foot standing on the anchor (the sole at y 8), toes north, the leg rising to y 32."""
    m = Model(mid)
    box(m, [2.0, 8.0, 2.0], [14.0, 13.0, 25.0], {"*": skin, "up": instep, "down": dark}, glow=glow)   # foot body
    box(m, [3.0, 8.0, 21.5], [13.0, 12.2, 27.5], {"*": dark, "up": skin}, glow=glow)                 # heel
    box(m, [3.5, 13.0, 14.0], [12.5, 32.0, 24.5], leg, glow=glow)                                     # the leg from the sky
    box(m, [3.2, 17.0, 13.7], [12.8, 18.5, 24.8], ring_tex, glow=glow)                                # anklet
    cbox(m, (3.25, 15.0, 19.5), (0.7, 2.0, 3.0), skin, glow=glow)                                     # ankle bones
    cbox(m, (12.75, 15.0, 19.5), (0.7, 2.0, 3.0), skin, glow=glow)
    toes = ((11.6, 4.0, 4.0, -2.0), (8.1, 2.6, 3.0, -1.5), (5.6, 2.0, 2.6, -0.8), (3.7, 1.8, 2.2, 0.0), (2.2, 1.4, 1.8, 1.0))
    for i, (cx, w, h, z0) in enumerate(toes):
        box(m, [cx - w / 2, 8.0, z0], [cx + w / 2, 8.0 + h, 3.0], {"*": skin, "down": dark}, glow=glow)
        box(m, [cx - w / 2 + 0.3, 8.0 + h, z0 + 0.2], [cx + w / 2 - 0.3, 8.0 + h + 0.25, z0 + 2.2], nail, glow=glow, sides=("up", "north", "east", "west"))
        if i < 2:
            box(m, [cx - w / 2 - 0.2, 9.0, z0 + 2.4], [cx + w / 2 + 0.2, 8.0 + h + 0.2, z0 + 3.3], ring_tex, glow=glow)
    return m


def build_agt():
    noise_tex("agt_bronze", "A86A32", "6E4420", 201, scale=4.0, c2="C8904A")
    noise_tex("agt_bronze_dark", "7A4A22", "4A2C12", 202, scale=4.0)
    rnd = random.Random(203)
    stars = [(0.2 + 0.6 * rnd.random(), 0.15 + 0.7 * rnd.random()) for _ in range(9)]
    stars.sort(key=lambda s: s[1])
    n = Noise(204)

    def instep(u, v):
        c = mix(rgb("6E4420"), rgb("A86A32"), n.fbm(u * 4, v * 4))
        for i in range(8):
            if seg_dist(u, v, stars[i][0], stars[i][1], stars[i + 1][0], stars[i + 1][1]) < 0.012:
                c = mix(c, rgb("C8C8D8"), 0.55)
        for (x, y) in stars:
            d = math.hypot(u - x, v - y)
            if d < 0.04:
                c = mix(c, rgb("F4F6FF"), 1.0 - d / 0.04 * 0.5)
        return (c[0], c[1], c[2], 255)
    tex("agt_instep", 32, instep)
    noise_tex("agt_nail", "E8C890", "C8A060", 205, scale=3.0)
    metal_tex("agt_silver", "C8C8D8", "FFFFFF", "7A7A8A", 206)
    star_tex("agt_star", "FFFFFF", "C8C8D8")
    pearl_tex("agt_star_core", "FFFFFF", "C8D0E8")
    footprint_tex("agt_glyph", "8A6A20", "FFD878", 207, star="E8ECF4")
    band_tex("agt_ring", "FFFFFF", "C8C8D8", 235)
    band_tex("agt_ring_u", "FFFFFF", "C8C8D8", 235, along_u=True)
    foot_model("agt_foot", "agt_bronze", "agt_bronze_dark", "agt_instep", "agt_nail", "agt_silver", "agt_bronze")
    m = Model("agt_brow_star")
    cbox(m, (8.0, 8.0, 8.0), (1.8, 1.8, 1.8), "agt_star_core", glow=15)
    cbox(m, (8.0, 8.0, 8.0), (1.6, 1.6, 1.6), "agt_star_core", glow=15, rot_axis="y", rot_angle=45.0)
    box(m, [1.0, 1.0, 7.95], [15.0, 15.0, 8.05], "agt_star", glow=15, sides=("north", "south"))
    box(m, [7.95, 1.0, 1.0], [8.05, 15.0, 15.0], "agt_star", glow=15, sides=("east", "west"))
    g = Model("agt_glyph")
    box(g, [-8.0, 8.0, -8.0], [24.0, 8.1, 24.0], "agt_glyph", glow=15, sides=("up", "down"))
    ring_model("agt_ring", "agt_ring", "agt_ring_u")


# ================================================================================================ Life · guanyin_sweet_dew

def build_gsd():
    n = Noise(301)

    def jade(u, v):
        c = mix(rgb("EEF2F8"), rgb("D8E8EE"), n.fbm(u * 3, v * 3))
        # a faint cloud scroll
        cloud = math.sin(u * math.tau * 2 + math.sin(v * math.tau * 3) * 1.2)
        if abs(cloud) < 0.08 and 0.3 < v < 0.8:
            c = mix(c, rgb("9FF3FF"), 0.45)
        return (c[0], c[1], c[2], 255)
    tex("gsd_jade", 32, jade)
    metal_tex("gsd_band", "9FF3FF", "FFFFFF", "5AB8C8", 302, engrave=False)
    petal_tex("gsd_lip", "EEF2F8", "FFFFFF", "9FF3FF", 303, alpha=245)
    noise_tex("gsd_mouth", "2A6A78", "103A44", 304)
    noise_tex("gsd_stem", "3FAE3A", "2A7A28", 305, scale=3.0)
    leaf_tex("gsd_leaf", "8AE07A", "3FAE3A", 306)
    petal_tex("gsd_seat_petal", "FF9EC8", "FFF4F8", "FFFFFF", 307, alpha=240)
    noise_tex("gsd_seat_core", "FFE4F0", "FFB8D8", 308, scale=3.0, c2="FFFFFF")
    pearl_tex("gsd_drop", "FFFFFF", "9FE8FF", alpha=220)
    band_tex("gsd_ring", "FFFFFF", "9FF3FF", 200)
    band_tex("gsd_ring_u", "FFFFFF", "9FF3FF", 200, along_u=True)
    # ---- the vase (anchor = the base centre)
    m = Model("gsd_vase")
    octagon(m, 8, 8, 2.6, 8.0, 9.0, "gsd_band", glow=6)
    octagon(m, 8, 8, 3.6, 9.0, 11.0, "gsd_jade")
    octagon(m, 8, 8, 4.4, 11.0, 15.5, "gsd_jade")
    octagon(m, 8, 8, 3.8, 15.5, 17.5, "gsd_jade")
    octagon(m, 8, 8, 2.8, 17.5, 19.0, "gsd_jade")
    octagon(m, 8, 8, 1.5, 19.0, 25.0, "gsd_jade")
    for y in (20.0, 22.0, 24.0):
        octagon(m, 8, 8, 1.75, y, y + 0.6, "gsd_band", glow=8)
    octagon(m, 8, 8, 2.2, 25.0, 25.8, "gsd_band", glow=8)
    box(m, [6.5, 25.82, 6.5], [9.5, 25.9, 9.5], "gsd_mouth", sides=("up",))
    lip = {"north": "gsd_lip", "south": "gsd_lip"}
    box(m, [6.4, 25.4, 5.6], [9.6, 28.6, 5.9], lip, rot=("x", -22.5, [8.0, 25.4, 5.75]), glow=10, sides=("north", "south"))
    box(m, [6.4, 25.4, 10.1], [9.6, 28.6, 10.4], lip, rot=("x", 22.5, [8.0, 25.4, 10.25]), glow=10, sides=("north", "south"))
    lip2 = {"east": "gsd_lip", "west": "gsd_lip"}
    box(m, [10.1, 25.4, 6.4], [10.4, 28.6, 9.6], lip2, rot=("z", -22.5, [10.25, 25.4, 8.0]), glow=10, sides=("east", "west"))
    box(m, [5.6, 25.4, 6.4], [5.9, 28.6, 9.6], lip2, rot=("z", 22.5, [5.75, 25.4, 8.0]), glow=10, sides=("east", "west"))
    # ---- the willow sprig (anchor = the stem base), the upper stem bending forward
    w = Model("gsd_willow")
    box(w, [7.75, 8.0, 7.75], [8.25, 18.0, 8.25], "gsd_stem")
    box(w, [7.8, 18.0, 7.8], [8.2, 26.0, 8.2], "gsd_stem", rot=("x", -22.5, [8.0, 18.0, 8.0]))
    sl = math.sin(math.radians(22.5))
    cl = math.cos(math.radians(22.5))
    leaves = ((11.0, 0.0, 1), (13.5, 0.0, -1), (16.0, 0.0, 1), (0.2, 1.0, -1), (0.45, 1.0, 1), (0.7, 1.0, -1))
    for (pos, upper, side) in leaves:
        if upper:
            y = 18.0 + 8.0 * pos * cl
            z = 8.0 - 8.0 * pos * sl
        else:
            y, z = pos, 8.0
        cx = 8.0 + side * 2.4
        box(w, [cx - 2.4, y - 0.8, z - 0.1], [cx + 2.4, y + 0.8, z + 0.1], "gsd_leaf", rot=("z", -side * 22.5, [8.0, y, z]),
            sides=("north", "south"))
    ty, tz = 18.0 + 8.0 * cl, 8.0 - 8.0 * sl
    box(w, [7.3, ty - 0.5, tz - 0.1], [8.7, ty + 3.8, tz + 0.1], "gsd_leaf", rot=("x", -22.5, [8.0, ty - 0.5, tz]), sides=("north", "south"))
    # ---- the lotus seat (anchor = its centre)
    s = Model("gsd_seat")
    octagon(s, 8, 8, 2.0, 6.0, 7.5, "gsd_seat_core")
    octagon(s, 8, 8, 3.0, 7.5, 9.4, "gsd_seat_core", glow=8)
    pet = {"north": "gsd_seat_petal", "south": "gsd_seat_petal"}
    pet2 = {"east": "gsd_seat_petal", "west": "gsd_seat_petal"}
    for (r, ang, glow) in ((3.4, 22.5, 10), (5.4, 45.0, 6)):
        w0 = 4.2 if r < 4 else 5.0
        box(s, [8 - w0 / 2, 8.0, 8 - r - 0.15], [8 + w0 / 2, 13.0, 8 - r + 0.15], pet, rot=("x", -ang, [8.0, 8.0, 8 - r]), glow=glow, sides=("north", "south"))
        box(s, [8 - w0 / 2, 8.0, 8 + r - 0.15], [8 + w0 / 2, 13.0, 8 + r + 0.15], pet, rot=("x", ang, [8.0, 8.0, 8 + r]), glow=glow, sides=("north", "south"))
        box(s, [8 + r - 0.15, 8.0, 8 - w0 / 2], [8 + r + 0.15, 13.0, 8 + w0 / 2], pet2, rot=("z", -ang, [8 + r, 8.0, 8.0]), glow=glow, sides=("east", "west"))
        box(s, [8 - r - 0.15, 8.0, 8 - w0 / 2], [8 - r + 0.15, 13.0, 8 + w0 / 2], pet2, rot=("z", ang, [8 - r, 8.0, 8.0]), glow=glow, sides=("east", "west"))
    flat = {"up": "gsd_seat_petal", "down": "gsd_seat_petal"}
    for (dx, dz, ang) in ((1, 1, 45.0), (-1, -1, 45.0), (1, -1, -45.0), (-1, 1, -45.0)):
        cx, cz = 8 + dx * 4.4, 8 + dz * 4.4
        cbox(s, (cx, 7.7, cz), (3.4, 0.3, 6.0), flat, rot_axis="y", rot_angle=ang, sides=("up", "down"), glow=6)
    d = Model("gsd_drop")
    cbox(d, (8.0, 8.0, 8.0), (2.0, 2.4, 2.0), "gsd_drop", glow=15)
    cbox(d, (8.0, 8.0, 8.0), (1.8, 2.2, 1.8), "gsd_drop", glow=15, rot_axis="y", rot_angle=45.0)
    cbox(d, (8.0, 9.6, 8.0), (1.0, 1.2, 1.0), "gsd_drop", glow=15)
    ring_model("gsd_ring", "gsd_ring", "gsd_ring_u")


# ================================================================================================ Life · life_death_domain

def build_ldd():
    n = Noise(401)
    m_ = Noise(402)

    def top(u, v):
        r = disc_alpha(u, v)
        if r > 1.0:
            return (0, 0, 0, 0)
        if u >= 0.5:
            # life: green with leaf veins and sprout dots
            c = mix(rgb("6AB040"), rgb("9AE05A"), n.fbm(u * 5, v * 5))
            vein = abs(math.sin((u * 6 + v * 4 + n.fbm(u * 3, v * 3)) * math.pi))
            c = mix(c, rgb("D8FFB0"), 0.35 * smooth(0.92, 1.0, vein))
        else:
            # death: ash grey with cracks and bone sketches
            c = mix(rgb("707070"), rgb("A0A0A0"), m_.fbm(u * 5, v * 5))
            k = abs(m_.fbm(u * 3 + 2, v * 3, 4) - 0.5)
            if k < 0.02:
                c = mix(c, rgb("3A3A3A"), 0.8)
        if abs(u - 0.5) < 0.018:
            c = WHITE
        ringk = math.exp(-((r - 0.94) / 0.025) ** 2)
        c = mix(c, rgb("F0F0E8"), ringk)
        a = 235 * smooth(1.0, 0.97, r)
        return (c[0], c[1], c[2], a)
    tex("ldd_disc_top", 64, top)

    def bottom(u, v):
        r = disc_alpha(u, v)
        if r > 1.0:
            return (0, 0, 0, 0)
        c = mix(rgb("5A5A5A"), rgb("A0A0A0"), m_.fbm(u * 5 + 9, v * 5))
        if abs(m_.fbm(u * 4, v * 4 + 3, 4) - 0.5) < 0.02:
            c = mix(c, rgb("2A2A2A"), 0.8)
        if abs(u - 0.5) < 0.018:
            c = rgb("E0E0E0")
        return (c[0], c[1], c[2], 235 * smooth(1.0, 0.97, r))
    tex("ldd_disc_bottom", 64, bottom)
    leaf_tex("ldd_leaf", "B8FF80", "6AB040", 403)
    noise_tex("ldd_bone", "F0EADC", "C8C0A8", 404, scale=3.0)
    metal_tex("ldd_ribbon", "FFFFFF", "FFFFFF", "D8E0E8", 405, engrave=False)
    noise_tex("ldd_paper", "F4ECD4", "E0C890", 406, alpha=215, scale=3.0, c2="FFF8E8")
    noise_tex("ldd_wire", "3A3030", "1A1414", 407)
    pearl_tex("ldd_soul", "E8F6FF", "80C0FF")
    noise_tex("ldd_tassel", "80C0FF", "3A70C8", 408)
    petal_tex("ldd_petal_life", "F8B8D8", "FFF0F8", "FFFFFF", 409)
    petal_tex("ldd_petal_dead", "8A8A80", "5A5A50", "A8A8A0", 410, wilt=True)
    noise_tex("ldd_pod", "C8E870", "8AB040", 411, scale=3.0)
    noise_tex("ldd_pad", "5A9A40", "3A7028", 412, scale=4.0)
    band_tex("ldd_ring", "FFFFFF", "C8D8C0", 220)
    band_tex("ldd_ring_u", "FFFFFF", "C8D8C0", 220, along_u=True)
    ring_model("ldd_ring", "ldd_ring", "ldd_ring_u")
    # ---- the diagram laid flat (anchor = its centre): life on +x, death on -x, the ribbon along z
    m = Model("ldd_disc")
    box(m, [-16.0, 7.9, -16.0], [32.0, 8.1, 32.0], {"up": "ldd_disc_top", "down": "ldd_disc_bottom"}, glow=10, sides=("up", "down"))
    box(m, [7.6, 8.1, -15.0], [8.4, 8.5, 31.0], "ldd_ribbon", glow=15, sides=("up", "east", "west", "north", "south"))
    octagon(m, 8, 8, 2.5, 8.1, 9.2, "ldd_ribbon", glow=12)
    rnd = random.Random(413)
    for i in range(7):
        a = math.radians(-70 + i * 23.0)
        r = 8.0 + (i % 3) * 5.0
        cx, cz = 8 + r * math.cos(a), 8 + r * math.sin(a)
        h = 3.0 + (i % 2) * 1.5
        ang = 45.0 if i % 2 else -45.0
        cbox(m, (cx, 8.1 + h / 2, cz), (2.4, h, 0.2), "ldd_leaf", rot_axis="y", rot_angle=ang, sides=("north", "south"), glow=6)
    for i in range(5):
        a = math.radians(110 + i * 34.0)
        r = 7.0 + (i % 3) * 5.0
        cx, cz = 8 + r * math.cos(a), 8 + r * math.sin(a)
        ang = (-45.0, -22.5, 0.0, 22.5, 45.0)[i]
        cbox(m, (cx, 8.35, cz), (5.0, 0.5, 0.8), "ldd_bone", rot_axis="y", rot_angle=ang)
        cbox(m, (cx + 2.5 * math.cos(math.radians(-ang)), 8.45, cz + 2.5 * math.sin(math.radians(-ang))), (1.0, 0.7, 1.2), "ldd_bone")
    cbox(m, (-1.0, 8.9, 14.0), (2.4, 1.6, 2.4), "ldd_bone")                         # a skull
    for (x, z) in ((8.0, -15.4), (8.0, 31.4), (-15.4, 8.0), (31.4, 8.0)):
        cbox(m, (x, 8.4, z), (1.2, 0.8, 1.2), "ldd_ribbon", glow=15)
    # ---- the soul lantern (anchor = its body centre)
    lt = Model("ldd_lantern")
    octagon(lt, 8, 8, 3.0, 4.0, 12.0, "ldd_paper", glow=12)
    box(lt, [5.0, 12.0, 5.0], [11.0, 13.0, 11.0], "ldd_wire")
    box(lt, [7.0, 13.0, 7.0], [9.0, 14.5, 9.0], "ldd_wire")
    box(lt, [7.7, 14.5, 7.7], [8.3, 16.5, 8.3], "ldd_wire")
    box(lt, [5.5, 3.0, 5.5], [10.5, 4.0, 10.5], "ldd_wire")
    for (x0, z0) in ((4.85, 7.8), (10.95, 7.8), (7.8, 4.85), (7.8, 10.95)):
        box(lt, [x0, 4.0, z0], [x0 + 0.2, 12.0, z0 + 0.4], "ldd_wire")
    cbox(lt, (8.0, 7.6, 8.0), (2.0, 3.6, 2.0), "ldd_soul", glow=15)
    cbox(lt, (8.0, 7.8, 8.0), (1.6, 3.0, 1.6), "ldd_soul", glow=15, rot_axis="y", rot_angle=45.0)
    box(lt, [7.7, 0.0, 7.7], [8.3, 3.0, 8.3], "ldd_tassel", glow=10)
    # ---- the half-blooming, half-withered lotus (anchor = its base)
    lo = Model("ldd_lotus")
    box(lo, [2.0, 7.9, 2.0], [14.0, 8.1, 14.0], {"up": "ldd_pad", "down": "ldd_pad"}, sides=("up", "down"))
    octagon(lo, 8, 8, 1.6, 9.0, 11.0, "ldd_pod", glow=8)
    box(lo, [7.6, 8.0, 7.6], [8.4, 9.0, 8.4], "ldd_pod")
    lp = {"east": "ldd_petal_life", "west": "ldd_petal_life"}
    box(lo, [10.0, 8.2, 6.0], [10.3, 14.5, 10.0], lp, rot=("z", -45.0, [10.15, 8.2, 8.0]), glow=8, sides=("east", "west"))
    box(lo, [9.6, 8.2, 6.6], [9.9, 13.5, 9.4], lp, rot=("z", -22.5, [9.75, 8.2, 8.0]), glow=8, sides=("east", "west"))
    lpn = {"north": "ldd_petal_life", "south": "ldd_petal_life"}
    box(lo, [8.6, 8.2, 5.6], [12.0, 14.0, 5.9], lpn, rot=("x", -45.0, [10.3, 8.2, 5.75]), glow=8, sides=("north", "south"))
    box(lo, [8.6, 8.2, 10.1], [12.0, 14.0, 10.4], lpn, rot=("x", 45.0, [10.3, 8.2, 10.25]), glow=8, sides=("north", "south"))
    dp = {"east": "ldd_petal_dead", "west": "ldd_petal_dead"}
    box(lo, [5.7, 8.2, 6.0], [6.0, 13.0, 10.0], dp, rot=("z", 45.0, [5.85, 8.2, 8.0]), sides=("east", "west"))
    dpn = {"north": "ldd_petal_dead", "south": "ldd_petal_dead"}
    box(lo, [4.0, 8.2, 5.6], [7.4, 12.5, 5.9], dpn, rot=("x", -45.0, [5.7, 8.2, 5.75]), sides=("north", "south"))
    box(lo, [4.0, 8.2, 10.1], [7.4, 12.5, 10.4], dpn, rot=("x", 45.0, [5.7, 8.2, 10.25]), sides=("north", "south"))


# ================================================================================================ Life · bodhisattva_tree

def build_bt():
    bark_tex("bt_bark", "6A4628", "3A2412", 501)
    bark_tex("bt_bark_moss", "6A4628", "3A2412", 502, moss="3FAE3A")
    bark_tex("bt_vein", "5A3A20", "2A1A0C", 503, vein="9AE05A")
    noise_tex("bt_moss", "3FAE3A", "2A7A28", 504, scale=6.0, c2="8AE07A")
    foliage_tex("bt_leaf", "6AD050", "2A8A2A", 505)
    foliage_tex("bt_leaf_tip", "7AE060", "3FAE3A", 506, tip="FFD54A")
    foliage_tex("bt_leaf_under", "3FAE3A", "1A5A1A", 507, under=True)
    pearl_tex("bt_fruit", "FFFFF0", "FFD860")
    noise_tex("bt_fruit_stem", "5A7A28", "3A5A18", 508)
    leaf_tex("bt_fruit_leaf", "9AE05A", "3FAE3A", 509, tip="FFE070")
    band_tex("bt_ring", "FFF8C0", "9AE05A", 230)
    band_tex("bt_ring_u", "FFF8C0", "9AE05A", 230, along_u=True)
    # ---- the trunk (anchor = the root flare's centre on the ground)
    m = Model("bt_trunk")
    octagon(m, 8, 8, 4.4, 8.0, 11.0, "bt_bark_moss", top="bt_bark")
    for (f, t) in (([6.6, 8.0, 0.8], [9.4, 10.2, 4.0]), ([6.6, 8.0, 12.0], [9.4, 10.2, 15.2]), ([0.8, 8.0, 6.6], [4.0, 10.2, 9.4]), ([12.0, 8.0, 6.6], [15.2, 10.2, 9.4])):
        box(m, f, t, "bt_bark_moss")
    twists = (22.5, 45.0, -22.5, 0.0, 22.5)
    for k in range(5):
        y0 = 11.0 + 4.0 * k
        r = 3.6 - 0.32 * k
        box(m, [8 - r, y0, 8 - r], [8 + r, y0 + 4.3, 8 + r], "bt_bark", rot=("y", twists[k], [8.0, y0, 8.0]))
        box(m, [8 - r * 0.92, y0 + 0.1, 8 - r * 0.92], [8 + r * 0.92, y0 + 4.1, 8 + r * 0.92], "bt_bark",
            rot=("y", norm_angle(twists[k] + 45.0)[0], [8.0, y0, 8.0]))
    box(m, [6.4, 31.0, 6.4], [9.6, 32.0, 9.6], "bt_bark")
    # branches carrying the canopy tiers, leaning out by 45 degrees
    box(m, [7.0, 21.0, 4.0], [9.0, 28.0, 6.0], "bt_bark", rot=("x", -45.0, [8.0, 21.0, 6.0]))
    box(m, [7.0, 21.0, 10.0], [9.0, 28.0, 12.0], "bt_bark", rot=("x", 45.0, [8.0, 21.0, 10.0]))
    box(m, [10.0, 24.0, 7.0], [12.0, 30.0, 9.0], "bt_bark", rot=("z", -45.0, [10.0, 24.0, 8.0]))
    box(m, [4.0, 24.0, 7.0], [6.0, 30.0, 9.0], "bt_bark", rot=("z", 45.0, [6.0, 24.0, 8.0]))
    box(m, [4.3, 13.0, 9.0], [4.6, 17.0, 11.0], "bt_moss", sides=("west", "north", "south"))
    box(m, [11.5, 18.0, 6.0], [11.8, 21.0, 8.5], "bt_moss", sides=("east", "north", "south"))
    # ---- the canopy tiers (anchor = the tier's centre)
    leaf = {"*": "bt_leaf", "up": "bt_leaf_tip", "down": "bt_leaf_under"}

    def canopy(mid, radius, size, upper, centre_size, crown=None):
        c = Model(mid)
        cbox(c, (8.0, 8.0, 8.0), centre_size, leaf)
        for i in range(8):
            a = math.radians(45.0 * i + 22.5)
            px, pz = 8 + radius * math.cos(a), 8 + radius * math.sin(a)
            cbox(c, (px, 8.0 - (i % 2) * 0.6, pz), size, leaf, rot_axis="y", rot_angle=22.5 if i % 2 else -22.5)
        for i in range(upper):
            a = math.radians(360.0 / upper * i)
            px, pz = 8 + radius * 0.6 * math.cos(a), 8 + radius * 0.6 * math.sin(a)
            cbox(c, (px, 8.0 + size[1] * 0.55, pz), (size[0] * 0.8, size[1] * 0.8, size[2] * 0.8), leaf, rot_axis="y", rot_angle=45.0 if i % 2 else 0.0)
        if crown:
            cbox(c, (8.0, 8.0 + crown[1] / 2 + centre_size[1] / 2 - 0.5, 8.0), crown, leaf)
        return c
    canopy("bt_canopy_1", 14.0, (9.0, 6.5, 9.0), 8, (12.0, 6.0, 12.0))
    canopy("bt_canopy_2", 10.0, (8.0, 6.0, 8.0), 4, (10.0, 6.0, 10.0))
    canopy("bt_canopy_3", 6.0, (6.5, 5.5, 6.5), 0, (9.0, 7.0, 9.0), crown=(5.0, 5.0, 5.0))
    # ---- a root arm crawling along the ground (anchor = its start), north
    r_ = Model("bt_root")
    start = (8.0, 8.0)
    heads = (0.0, -22.5, 22.5, -22.5, 0.0)
    widths = (4.0, 3.4, 2.8, 2.2, 1.6)
    length = 4.6
    for i in range(5):
        a = heads[i]
        dx, dz = -math.sin(math.radians(a)), -math.cos(math.radians(a))
        cx, cz = start[0] + dx * length / 2, start[1] + dz * length / 2
        w = widths[i]
        cbox(r_, (cx, 7.0 + w / 2 - 0.6, cz), (w, w * 0.8, length + 0.6), {"*": "bt_vein", "down": "bt_bark"}, rot_axis="y", rot_angle=a, glow=6)
        if i in (1, 3):
            side = 1 if i == 1 else -1
            cbox(r_, (cx + side * (w / 2 + 1.0), 7.2, cz), (2.4, 0.6, 0.6), "bt_vein", rot_axis="y", rot_angle=side * 22.5, glow=6)
        start = (start[0] + dx * length, start[1] + dz * length)
    f = Model("bt_fruit")
    octagon(f, 8, 8, 2.0, 6.0, 10.0, "bt_fruit", glow=15)
    box(f, [7.7, 10.0, 7.7], [8.3, 11.6, 8.3], "bt_fruit_stem")
    box(f, [8.0, 10.6, 7.9], [11.0, 11.2, 8.1], "bt_fruit_leaf", rot=("z", 22.5, [8.0, 10.9, 8.0]), sides=("north", "south", "up"))
    ring_model("bt_ring", "bt_ring", "bt_ring_u")


# ================================================================================================ Shadow · stygian_tiger_seal

def build_sts():
    stone_tex("sts_iron", "2A2A33", "141418", "4A4A58", 601, cracks=0.4)
    n = Noise(602)

    def ember(u, v):
        c = mix(rgb("1E1E26"), rgb("2A2A33"), n.fbm(u * 5, v * 5))
        line = abs(math.sin(u * math.tau * 3.0)) < 0.12 and 0.25 < v < 0.75
        mark = abs(v - 0.5) < 0.06
        if line or mark:
            c = mix(c, rgb("FF6A2A"), 0.9)
        return (c[0], c[1], c[2], 255)
    tex("sts_ember", 32, ember)

    def tiger(u, v):
        c = mix(rgb("22222A"), rgb("34343E"), n.fbm(u * 4, v * 4))
        stripe = math.sin((u * 3.5 + n.fbm(u * 2, v * 2) * 1.2) * math.tau)
        if stripe > 0.75:
            c = mix(c, rgb("802030"), 0.8)
        return (c[0], c[1], c[2], 255)
    tex("sts_tiger", 32, tiger)
    pearl_tex("sts_eye", "FFE0C0", "FF6A2A")
    metal_tex("sts_flute", "1A1A22", "4A4A5A", "0A0A10", 603, engrave=False)
    metal_tex("sts_band", "C0201A", "FF6A50", "6A0A08", 604, engrave=False)
    noise_tex("sts_tassel", "C0201A", "7A1010", 605, scale=8.0)
    metal_tex("sts_knot", "D8A030", "FFF0A0", "8A6010", 606, engrave=False)
    band_tex("sts_ring", "FF8A6A", "802030", 230)
    band_tex("sts_ring_u", "FF8A6A", "802030", 230, along_u=True)

    def tally(mid, half):
        m = Model(mid)

        def b(f, t, tx, rot=None, glow=0):
            x0, x1 = f[0], t[0]
            if half == "l":
                if x0 >= 8.0:
                    return
                x1 = min(x1, 8.0)
            elif half == "r":
                if x1 <= 8.0:
                    return
                x0 = max(x0, 8.0)
            spec = tx
            if half and isinstance(tx, str):
                spec = {"*": tx, ("east" if half == "l" else "west"): "sts_ember"}
            box(m, [x0, f[1], f[2]], [x1, t[1], t[2]], spec, rot=rot, glow=glow)
        b([1.5, 4.0, 4.0], [14.5, 6.0, 12.0], {"*": "sts_iron", "north": "sts_ember", "south": "sts_ember"})
        b([4.5, 6.0, 4.5], [11.5, 9.8, 13.0], "sts_tiger")
        b([4.0, 6.0, 10.0], [12.0, 10.4, 13.5], "sts_tiger")
        b([5.0, 7.0, 1.2], [11.0, 12.0, 5.2], "sts_tiger")
        b([6.2, 7.2, 0.2], [9.8, 9.8, 1.4], "sts_tiger")
        b([5.2, 12.0, 3.0], [6.8, 13.2, 4.4], "sts_tiger")
        b([9.2, 12.0, 3.0], [10.8, 13.2, 4.4], "sts_tiger")
        b([5.9, 10.3, 0.95], [6.9, 10.9, 1.25], "sts_eye", glow=15)
        b([9.1, 10.3, 0.95], [10.1, 10.9, 1.25], "sts_eye", glow=15)
        b([5.0, 6.0, 2.5], [7.0, 7.0, 5.0], "sts_iron")
        b([9.0, 6.0, 2.5], [11.0, 7.0, 5.0], "sts_iron")
        b([7.4, 9.8, 5.0], [8.6, 10.4, 12.5], "sts_ember", glow=10)
        b([7.4, 9.0, 13.0], [8.6, 10.2, 18.0], "sts_tiger", rot=("x", 22.5, [8.0, 9.6, 13.0]))
        return m
    tally("sts_tally", None)
    tally("sts_tally_l", "l")
    tally("sts_tally_r", "r")
    fl = Model("sts_flute")
    box(fl, [-4.0, 7.4, 7.4], [20.0, 8.6, 8.6], "sts_flute")
    for x in (-3.0, 2.0, 13.5, 18.4):
        box(fl, [x, 7.25, 7.25], [x + 0.6, 8.75, 8.75], "sts_band", glow=6)
    for x in (5.0, 7.0, 9.0, 11.0):
        cbox(fl, (x, 8.62, 8.0), (0.6, 0.06, 0.6), "sts_knot", sides=("up",))
    box(fl, [19.2, 4.0, 7.8], [19.8, 7.4, 8.2], "sts_band")
    box(fl, [18.9, 3.2, 7.5], [20.1, 4.4, 8.5], "sts_knot")
    box(fl, [19.0, -1.0, 7.6], [20.0, 3.2, 8.4], "sts_tassel")
    ring_model("sts_ring", "sts_ring", "sts_ring_u")


# ================================================================================================ Shadow · kunpeng_art

def build_kp():
    scales_tex("kp_scale", "1A2A4A", "8AA8C8", 701, belly="2A4A6A")
    noise_tex("kp_belly", "C8D8E8", "9AB0C8", 702, scale=4.0, c2="FFFFFF")
    n = Noise(703)

    def fin(u, v):
        ray = 0.5 + 0.5 * math.cos(u * math.tau * 6)
        c = mix(rgb("3A5A8A"), rgb("C8E0FF"), 0.3 + 0.5 * ray * (1 - v))
        return (c[0], c[1], c[2], 170 + 60 * ray)
    tex("kp_fin", 32, fin)
    noise_tex("kp_whisker", "D8E8FF", "9AB8E0", 704)
    pearl_tex("kp_eye", "FFFFFF", "8AD0FF")
    feather_tex("kp_feather", "3A5A9A", "E8F0FF", 705)
    feather_tex("kp_feather_dark", "2A3E70", "9AB8E8", 706)
    noise_tex("kp_feather_white", "E8F0FF", "C0D0E8", 707, scale=4.0, c2="FFFFFF")
    metal_tex("kp_beak", "F0E8C8", "FFFFFF", "A89870", 708, engrave=False)

    def crescent(u, v):
        d = abs(v - 0.5) * 2
        k = clamp01(1.0 - d)
        c = mix(rgb("C8DCFF"), WHITE, k)
        return (c[0], c[1], c[2], 160 * k ** 0.7)
    tex("kp_crescent", 32, crescent)
    band_tex("kp_ring", "FFFFFF", "C8DCFF", 210)
    band_tex("kp_ring_u", "FFFFFF", "C8DCFF", 210, along_u=True)
    sc = {"*": "kp_scale", "down": "kp_belly"}
    # ---- the Kun (anchor = its middle; the head north)
    k = Model("kp_kun")
    box(k, [3.5, 3.0, -16.0], [12.5, 12.5, -6.0], sc)
    box(k, [2.0, 1.5, -6.0], [14.0, 14.0, 4.0], sc)
    box(k, [2.5, 2.5, 4.0], [13.5, 13.5, 13.0], sc)
    box(k, [4.0, 4.0, 13.0], [12.0, 12.0, 21.0], sc)
    box(k, [5.5, 5.5, 21.0], [10.5, 10.5, 27.0], sc)
    box(k, [3.0, 1.0, -5.0], [13.0, 1.6, 12.0], "kp_belly")
    fn_ew = {"east": "kp_fin", "west": "kp_fin"}
    box(k, [7.7, 3.0, 26.0], [8.3, 13.0, 32.0], fn_ew, sides=("east", "west", "up", "down"))
    box(k, [7.7, 13.0, 27.5], [8.3, 21.0, 32.0], fn_ew, rot=("x", 22.5, [8.0, 13.0, 27.5]), sides=("east", "west"))
    box(k, [7.7, -5.0, 27.5], [8.3, 3.0, 32.0], fn_ew, rot=("x", -22.5, [8.0, 3.0, 27.5]), sides=("east", "west"))
    box(k, [7.7, 13.6, -3.0], [8.3, 19.0, 9.0], fn_ew, rot=("x", 22.5, [8.0, 13.6, -3.0]), sides=("east", "west"))
    fud = {"up": "kp_fin", "down": "kp_fin"}
    box(k, [-4.0, 4.0, -4.0], [2.0, 4.6, 3.0], fud, rot=("z", 22.5, [2.0, 4.3, 0.0]), sides=("up", "down"))
    box(k, [14.0, 4.0, -4.0], [20.0, 4.6, 3.0], fud, rot=("z", -22.5, [14.0, 4.3, 0.0]), sides=("up", "down"))
    box(k, [-8.0, 6.8, -14.0], [3.5, 7.4, -13.4], "kp_whisker", rot=("y", -22.5, [3.5, 7.1, -13.7]), glow=6)
    box(k, [12.5, 6.8, -14.0], [24.0, 7.4, -13.4], "kp_whisker", rot=("y", 22.5, [12.5, 7.1, -13.7]), glow=6)
    cbox(k, (3.4, 9.5, -12.0), (0.5, 1.4, 1.4), "kp_eye", glow=15)
    cbox(k, (12.6, 9.5, -12.0), (0.5, 1.4, 1.4), "kp_eye", glow=15)
    # ---- the Peng body (anchor = its centre; wings and tail are parts)
    p = Model("kp_peng")
    fe = {"*": "kp_feather", "down": "kp_feather_white"}
    box(p, [4.0, 5.0, -2.0], [12.0, 11.0, 16.0], fe)
    box(p, [4.5, 4.6, 0.0], [11.5, 5.2, 14.0], "kp_feather_white")
    box(p, [4.5, 5.5, -4.0], [11.5, 11.5, -1.0], "kp_feather_white")
    box(p, [5.5, 7.0, -8.0], [10.5, 11.5, -3.5], fe)
    box(p, [5.6, 8.5, -12.0], [10.4, 13.0, -7.5], fe)
    box(p, [7.0, 9.0, -16.0], [9.0, 10.6, -12.0], "kp_beak", rot=("x", -22.5, [8.0, 9.8, -12.0]))
    box(p, [7.4, 13.0, -11.0], [8.6, 13.8, -4.0], "kp_feather_white", rot=("x", 22.5, [8.0, 13.0, -11.0]), glow=6)
    box(p, [7.6, 12.5, -9.0], [8.4, 13.2, -2.0], "kp_feather_white", rot=("x", 22.5, [8.0, 12.5, -9.0]), glow=6)
    cbox(p, (5.5, 11.0, -10.0), (0.4, 0.8, 0.8), "kp_eye", glow=15)
    cbox(p, (10.5, 11.0, -10.0), (0.4, 0.8, 0.8), "kp_eye", glow=15)
    box(p, [5.5, 3.5, 6.0], [7.0, 5.0, 9.0], "kp_beak")
    box(p, [9.0, 3.5, 6.0], [10.5, 5.0, 9.0], "kp_beak")
    # ---- a wing (anchor = its root at the shoulder; the right wing reaches +x)
    w = Model("kp_wing_r")
    feath = {"up": "kp_feather", "down": "kp_feather_white", "*": "kp_feather_dark"}
    box(w, [8.0, 7.4, 5.5], [30.0, 8.6, 8.6], {"*": "kp_feather_dark", "up": "kp_feather", "down": "kp_feather_white"})
    for r in range(5):
        x0 = 8.0 + 1.2 * r
        x1 = 31.0 - 1.4 * r
        z0 = 8.4 + 2.5 * r
        y = 7.7 - 0.12 * r
        box(w, [x0, y - 0.3, z0], [x1, y + 0.3, z0 + 3.0], feath, sides=("up", "down", "south"))
    for i in range(4):
        x = 24.0 + i * 2.0
        box(w, [x, 7.4, 8.6], [x + 1.6, 7.9, 22.0 - i * 1.5], {"up": "kp_feather", "down": "kp_feather_white", "*": "kp_feather_dark"})
    mirror_x(w, "kp_wing_l")
    t = Model("kp_tail")
    box(t, [6.5, 7.7, 8.0], [9.5, 8.3, 30.0], feath, sides=("up", "down", "south"))
    for a in (22.5, -22.5):
        box(t, [6.5, 7.6, 8.0], [9.5, 8.2, 28.0], feath, rot=("y", a, [8.0, 8.0, 8.0]), sides=("up", "down", "south"))
    for a in (45.0, -45.0):
        box(t, [6.8, 7.5, 8.0], [9.2, 8.1, 25.0], feath, rot=("y", a, [8.0, 8.0, 8.0]), sides=("up", "down", "south"))
    box(t, [7.6, 7.85, 8.0], [8.4, 8.05, 32.0], "kp_feather_white", glow=8)
    # ---- a wind crescent (flat, the horns sweep back)
    c = Model("kp_crescent")
    cr = {"up": "kp_crescent", "down": "kp_crescent"}
    for i, th in enumerate((-45.0, -22.5, 0.0, 22.5, 45.0)):
        px, pz = 8.0 + 10.0 * math.sin(math.radians(th)), 14.0 - 10.0 * math.cos(math.radians(th))
        width = 2.6 - abs(th) / 45.0 * 1.4
        cbox(c, (px, 8.0, pz), (4.6, 0.3, width), cr, rot_axis="y", rot_angle=-th, glow=15, sides=("up", "down"))
    ring_model("kp_ring", "kp_ring", "kp_ring_u")


# ================================================================================================ Shadow · karma_domain

def build_kd():
    noise_tex("kd_wood", "3A2018", "22120C", 801, scale=3.0)
    n = Noise(802)

    def thread(u, v):
        c = mix(rgb("3A2018"), rgb("4A2A1E"), n.fbm(u * 3, v * 3))
        s = (u * 6.0 + v * 2.0) % 1.0
        if s < 0.45:
            c = mix(rgb("D04020"), rgb("FF7050"), 0.3 * math.sin(s / 0.45 * math.pi))
        return (c[0], c[1], c[2], 255)
    tex("kd_thread", 16, thread)
    noise_tex("kd_red", "D04020", "8A2010", 803, scale=6.0, c2="FF6A40")
    metal_tex("kd_gold", "FFD54A", "FFF6C8", "A87A10", 804, engrave=False)
    m = Model("kd_wheel")
    ring(m, (8.0, 8.0, 8.0), 7.0, 1.0, 1.0, 8, "kd_thread", "kd_thread", sides=SIDES, glow=4, overlap=1.12)
    octagon(m, 8, 8, 1.6, 7.4, 8.6, "kd_wood")
    octagon(m, 8, 8, 1.85, 7.7, 8.3, "kd_red", glow=8)
    for a in (0.0, 45.0, -45.0):
        cbox(m, (8.0, 8.0, 8.0), (13.0, 0.6, 0.8), "kd_thread", rot_axis="y", rot_angle=a, glow=4)
    cbox(m, (8.0, 8.0, 8.0), (0.8, 0.6, 13.0), "kd_thread", glow=4)
    for i in range(8):
        a = math.radians(45.0 * i)
        cbox(m, (8 + 7.6 * math.cos(a), 8.0, 8 + 7.6 * math.sin(a)), (1.2, 1.2, 1.2), "kd_gold", glow=10)
    kn = Model("kd_knot")
    cbox(kn, (8.0, 8.0, 8.0), (4.0, 2.4, 1.6), "kd_red", rot_axis="y", rot_angle=45.0, glow=8)
    cbox(kn, (8.0, 8.0, 8.0), (1.6, 2.4, 4.0), "kd_red", rot_axis="y", rot_angle=45.0, glow=8)
    cbox(kn, (8.0, 8.0, 8.0), (2.0, 4.0, 2.0), "kd_red", rot_axis="x", rot_angle=45.0, glow=8)
    cbox(kn, (8.0, 8.0, 8.0), (1.8, 1.8, 1.8), "kd_gold", rot_axis="y", rot_angle=45.0, glow=15)
    box(kn, [7.6, 3.0, 7.6], [8.4, 6.5, 8.4], "kd_red", rot=("z", 22.5, [8.0, 6.5, 8.0]), glow=8)
    box(kn, [7.6, 3.4, 7.6], [8.4, 6.5, 8.4], "kd_red", rot=("z", -22.5, [8.0, 6.5, 8.0]), glow=8)


# ================================================================================================ Immortal · ninth_hex

def build_nh():
    metal_tex("nh_gold", "E8D8A0", "FFF8E0", "9A8A50", 901)
    metal_tex("nh_violet", "7A6AA0", "C8B8F0", "3A2A60", 902)
    glyph_tex("nh_feng", FENG, "7A3AA0", "E8D8A0", border="9A8A50", glow="FFFFFF")
    n = Noise(903)

    def boss(u, v):
        c = mix(rgb("7A6AA0"), rgb("A898D0"), n.fbm(u * 3, v * 3))
        if abs(disc_alpha(u, v) - 0.7) < 0.08:
            c = mix(c, rgb("E8D8A0"), 0.9)
        return (c[0], c[1], c[2], 255)
    tex("nh_boss", 16, boss)
    stone_tex("nh_stone", "C8BEDC", "8A80A0", "E8E0F4", 904, cracks=0.3)
    stone_tex("nh_stone_cracked", "B0A6C4", "4A4060", "D8D0E8", 905, cracks=2.2, vein="B080FF")
    paper_tex("nh_paper", "F0D890", "C0182A", 906)
    paper_tex("nh_paper_torn", "E0C878", "A01020", 907, torn=True)
    noise_tex("nh_rope", "C0182A", "7A0A14", 908, scale=7.0)
    band_tex("nh_ring", "FFF8E0", "7A6AA0", 235)
    band_tex("nh_ring_u", "FFF8E0", "7A6AA0", 235, along_u=True)
    # ---- the eight-pointed seal (flat; anchor = its centre)
    m = Model("nh_seal")
    h = 14.0
    bars = (([-6.0, 7.6, -7.0], [22.0, 8.4, -5.0]), ([-6.0, 7.6, 21.0], [22.0, 8.4, 23.0]),
            ([-7.0, 7.6, -6.0], [-5.0, 8.4, 22.0]), ([21.0, 7.6, -6.0], [23.0, 8.4, 22.0]))
    for (f, t) in bars:
        box(m, f, t, "nh_gold", glow=10)
    for (f, t) in bars:
        box(m, [f[0], 7.5, f[2]], [t[0], 8.3, t[2]], "nh_violet", rot=("y", 45.0, [8.0, 8.0, 8.0]), glow=8)
    d = h * math.sqrt(2.0)
    points = [(8 - h, 8 - h), (8 + h, 8 - h), (8 - h, 8 + h), (8 + h, 8 + h), (8 - d, 8.0), (8 + d, 8.0), (8.0, 8 - d), (8.0, 8 + d)]
    for (x, z) in points:
        box(m, [x - 3.0, 8.4, z - 3.0], [x + 3.0, 8.9, z + 3.0], {"*": "nh_gold", "up": "nh_feng", "down": "nh_feng"}, glow=12)
    octagon(m, 8, 8, 4.0, 8.0, 9.6, "nh_boss", glow=10)
    box(m, [5.0, 9.62, 5.0], [11.0, 9.8, 11.0], "nh_feng", glow=15, sides=("up",))
    ring(m, (8.0, 8.05, 8.0), 8.5, 0.9, 0.6, 8, "nh_gold", "nh_gold", sides=("up", "down"), glow=10, overlap=1.1)
    # ---- the talisman pillar (anchor = its base) and its cracked copy

    def pillar(mid, stone, paper, cracked):
        p = Model(mid)
        box(p, [5.0, 8.0, 5.0], [11.0, 9.5, 11.0], stone)
        box(p, [6.0, 9.5, 6.0], [10.0, 28.0, 10.0], stone, glow=4 if cracked else 0)
        if cracked:
            box(p, [5.2, 28.0, 5.2], [10.8, 29.5, 10.8], stone, rot=("z", 22.5, [10.8, 28.0, 8.0]))
        else:
            box(p, [5.2, 28.0, 5.2], [10.8, 29.5, 10.8], stone)
            box(p, [6.5, 29.5, 6.5], [9.5, 31.0, 9.5], stone)
            box(p, [7.4, 31.0, 7.4], [8.6, 32.0, 8.6], "nh_gold", glow=10)
        box(p, [5.8, 14.0, 5.8], [10.2, 25.0, 10.2], paper, sides=("north", "south", "east", "west"))
        box(p, [6.8, 10.5, 5.5], [9.2, 24.0, 5.7], paper, sides=("north", "south"))
        box(p, [5.7, 25.0, 5.7], [10.3, 25.8, 10.3], "nh_rope")
    pillar("nh_pillar", "nh_stone", "nh_paper", False)
    pillar("nh_pillar_cracked", "nh_stone_cracked", "nh_paper_torn", True)
    ring_model("nh_ring", "nh_ring", "nh_ring_u")


# ================================================================================================ Immortal · five_elements_mountain

def build_fem():
    noise_tex("fem_gold", "FFD54A", "E8B830", 1001, scale=3.0, c2="FFF2B0")
    n = Noise(1002)

    def under(u, v):
        c = mix(rgb("E8B830"), rgb("FFD54A"), n.fbm(u * 3, v * 3))
        # palm creases
        for (pts) in (((0.1, 0.35), (0.5, 0.42), (0.9, 0.3)), ((0.15, 0.55), (0.55, 0.5), (0.85, 0.62)), ((0.6, 0.15), (0.45, 0.5), (0.5, 0.92))):
            if poly_dist(u, v, pts) < 0.012:
                c = mix(c, rgb("A87A10"), 0.7)
        # the dharma wheel (eight spokes and a rim)
        r = math.hypot(u - 0.5, v - 0.6) * 2.0
        ang = math.atan2(v - 0.6, u - 0.5)
        if abs(r - 0.42) < 0.035 or r < 0.08 or (r < 0.42 and abs(math.sin(ang * 4)) < 0.08):
            c = mix(c, rgb("FFF8D8"), 0.95)
        return (c[0], c[1], c[2], 255)
    tex("fem_palm_under", 64, under)
    noise_tex("fem_light", "FFF2B0", "FFE070", 1003, scale=3.0, c2="FFFFFF")
    stone_tex("fem_metal", "E8E8EE", "A8A8B8", "FFFFFF", 1004, cracks=0.5, vein="FFFFFF")
    foliage_tex("fem_wood", "5AC850", "2A7A28", 1005)
    noise_tex("fem_wood_rock", "3A7A30", "1E4A1A", 1006, scale=5.0)

    def water(u, v):
        c = mix(rgb("2A5AC8"), rgb("3A7AE8"), n.fbm(u * 3 + 5, v * 3))
        wave = math.sin((v * 7.0 + math.sin(u * math.tau * 2) * 0.4) * math.tau)
        if wave > 0.85:
            c = mix(c, rgb("C8E8FF"), 0.7)
        return (c[0], c[1], c[2], 255)
    tex("fem_water", 32, water)
    stone_tex("fem_fire_rock", "B02A14", "4A0E08", "E03A1A", 1007, cracks=1.4, vein="FFB040")
    flame_tex("fem_flame", [(0.0, rgb("FFFFE0")), (0.3, rgb("FFD040")), (0.65, rgb("FF7A1A")), (1.0, rgb("C0200E"))], 1008)

    def earth(u, v):
        c = mix(rgb("B8803A"), rgb("D9A05B"), n.fbm(u * 3 + 9, v * 3))
        strata = math.sin(v * math.tau * 5 + n.fbm(u * 4, v * 2) * 2)
        c = mix(c, rgb("8A5A22") if strata > 0 else rgb("F0C880"), 0.25 * abs(strata))
        return (c[0], c[1], c[2], 255)
    tex("fem_earth", 32, earth)
    noise_tex("fem_earth_top", "E8C070", "C8A050", 1009, scale=4.0)
    paper_tex("fem_paper", "F0D890", "D8A020", 1010)
    noise_tex("fem_tassel", "C0201A", "7A1010", 1011, scale=8.0)
    glow_disc_tex("fem_glow", "FFF8D0", "FFD54A", alpha=170)
    band_tex("fem_ring", "FFFFF0", "FFD54A", 240)
    band_tex("fem_ring_u", "FFFFF0", "FFD54A", 240, along_u=True)
    # ---- the Buddha's palm, palm down (anchor = the palm's centre; fingers north)
    m = Model("fem_palm")
    g, li = "fem_gold", "fem_light"
    box(m, [-2.0, 6.0, 6.0], [18.0, 10.0, 26.0], {"*": g, "down": "fem_palm_under"}, glow=10)
    box(m, [0.0, 5.6, 19.0], [16.0, 7.0, 26.0], {"*": g, "down": "fem_palm_under"}, glow=10)
    box(m, [0.5, 6.5, 26.0], [15.5, 9.5, 32.0], g, glow=10)
    box(m, [0.2, 6.2, 28.0], [15.8, 9.8, 29.5], li, glow=15)
    fingers = ((-1.6, 2.4, (4.5, 4.0, 3.4)), (2.8, 7.0, (6.0, 5.0, 4.2)), (7.4, 11.6, (6.5, 5.5, 4.5)), (12.0, 16.2, (6.0, 5.0, 4.0)))
    for (x0, x1, lens) in fingers:
        z = 6.0
        for i, ln in enumerate(lens):
            shrink = 0.25 * i
            f = [x0 + shrink, 6.6 + shrink * 0.5, z - ln]
            t = [x1 - shrink, 9.4 - shrink * 0.5, z + 0.2]
            if i < 2:
                box(m, f, t, g, glow=10)
            else:
                box(m, f, t, g, rot=("x", -22.5, [(x0 + x1) / 2, 8.0, z]), glow=10)
                box(m, [f[0] + 0.3, t[1], f[2] + 0.3], [t[0] - 0.3, t[1] + 0.25, f[2] + 2.0], li, rot=("x", -22.5, [(x0 + x1) / 2, 8.0, z]), glow=15,
                    sides=("up", "north", "east", "west"))
            z -= ln
    box(m, [17.0, 6.8, 15.0], [21.0, 9.2, 23.0], g, rot=("y", -22.5, [17.0, 8.0, 23.0]), glow=10)
    box(m, [17.6, 7.0, 9.5], [21.2, 9.0, 15.0], g, rot=("y", -22.5, [17.0, 8.0, 15.0]), glow=10)
    box(m, [1.0, 5.85, 9.0], [15.0, 5.95, 23.0], {"down": "fem_glow"}, glow=15, sides=("down",))
    # ---- the five element peaks (anchor = the base centre)
    me = Model("fem_peak_metal")
    for (r, y0, y1) in ((6.0, 8.0, 12.0), (4.6, 12.0, 17.0), (3.4, 17.0, 22.0), (2.3, 22.0, 27.0), (1.3, 27.0, 30.5)):
        octagon(me, 8, 8, r, y0, y1, "fem_metal", glow=6)
    box(me, [7.6, 30.5, 7.6], [8.4, 32.0, 8.4], "fem_metal", glow=12)
    octagon(me, 3.0, 10.5, 1.5, 8.0, 19.0, "fem_metal", glow=6)
    octagon(me, 12.8, 4.8, 1.3, 8.0, 16.0, "fem_metal", glow=6)
    wd = Model("fem_peak_wood")
    box(wd, [1.0, 8.0, 0.0], [15.0, 11.0, 16.0], {"*": "fem_wood_rock", "up": "fem_wood"})
    heights = (10.0, 16.0, 22.0, 15.0, 11.0)
    for j, hgt in enumerate(heights):
        z = 1.5 + 3.2 * j
        w0 = 2.6 - (j % 2) * 0.6
        box(wd, [5.0 + (j % 2), 8.0, z - w0], [11.0 - (j % 2), 8.0 + hgt, z + w0], {"*": "fem_wood_rock", "up": "fem_wood"},
            rot=("x", 22.5 if j % 2 else -22.5, [8.0, 8.0, z]))
    for (x, z, hgt) in ((3.0, 4.0, 6.0), (13.0, 7.0, 7.0), (4.0, 12.0, 5.0), (12.0, 13.0, 6.0)):
        octagon(wd, x, z, 1.2, 10.5, 10.5 + hgt, "fem_wood", glow=4)
    wa = Model("fem_peak_water")
    for (r, y0, y1) in ((6.5, 8.0, 11.0), (6.0, 11.0, 14.0), (5.0, 14.0, 17.0), (3.6, 17.0, 19.5), (2.0, 19.5, 21.2)):
        octagon(wa, 8, 8, r, y0, y1, "fem_water", glow=6)
    box(wa, [6.5, 21.2, 6.5], [9.5, 21.7, 9.5], "fem_water", glow=10)
    fi = Model("fem_peak_fire")
    box(fi, [3.0, 8.0, 3.0], [13.0, 14.0, 13.0], "fem_fire_rock", glow=4)
    box(fi, [4.5, 14.0, 4.0], [11.0, 20.0, 11.5], "fem_fire_rock", rot=("y", 22.5, [8.0, 14.0, 8.0]), glow=4)
    box(fi, [6.0, 20.0, 5.5], [10.0, 25.0, 10.0], "fem_fire_rock", rot=("z", 22.5, [8.0, 20.0, 8.0]), glow=6)
    box(fi, [1.0, 8.0, 8.0], [6.0, 16.0, 14.0], "fem_fire_rock", rot=("x", -22.5, [3.5, 8.0, 11.0]), glow=4)
    box(fi, [10.0, 8.0, 1.0], [15.0, 13.0, 6.0], "fem_fire_rock", rot=("z", -22.5, [12.5, 8.0, 3.5]), glow=4)
    for a in (0.0, 45.0, -45.0):
        box(fi, [3.5, 21.0, 7.95], [12.5, 32.0, 8.05], {"north": "fem_flame", "south": "fem_flame"}, rot=("y", a, [8.0, 21.0, 8.0]), glow=15,
            sides=("north", "south"))
    ea = Model("fem_peak_earth")
    for (r, y0, y1) in ((7.0, 8.0, 11.0), (6.4, 11.0, 14.5), (5.8, 14.5, 18.0), (5.4, 18.0, 20.0)):
        octagon(ea, 8, 8, r, y0, y1, "fem_earth", top="fem_earth_top")
    box(ea, [3.4, 20.0, 3.4], [12.6, 20.4, 12.6], "fem_earth_top", sides=("up", "north", "south", "east", "west"))
    for (x, z, s) in ((1.0, 13.0, 2.0), (14.0, 3.0, 1.6), (13.5, 13.0, 1.4)):
        cbox(ea, (x, 8.0 + s / 2, z), (s, s, s), "fem_earth", rot_axis="y", rot_angle=22.5)
    # ---- the six-syllable talisman (anchor = the strip's centre)
    tm = Model("fem_talisman")
    box(tm, [5.0, -4.0, 7.9], [11.0, 18.0, 8.1], "fem_paper", glow=12, sides=("north", "south", "east", "west"))
    box(tm, [4.6, 18.0, 7.6], [11.4, 19.5, 8.4], "fem_gold", glow=12)
    box(tm, [7.5, -8.0, 7.8], [8.5, -4.0, 8.2], "fem_tassel")
    box(tm, [3.0, -5.0, 8.3], [13.0, 19.0, 8.35], {"north": "fem_glow", "south": "fem_glow"}, glow=15, sides=("north", "south"))
    ring_model("fem_ring", "fem_ring", "fem_ring_u")


# ================================================================================================ Immortal · tai_chi_diagram

TRIGRAMS = ((1, 1, 1), (0, 1, 1), (1, 0, 1), (0, 0, 1), (1, 1, 0), (0, 1, 0), (1, 0, 0), (0, 0, 0))


def trigram_tex(name, lines, rotated=False):
    """Three bars (1 = solid yang, 0 = broken yin) in gold on black, bars along u (or along v when rotated)."""
    gold, bg = rgb("FFD54A"), rgb("16141C")

    def fn(u, v):
        if rotated:
            u, v = v, u
        for i, solid in enumerate(lines):
            y0 = 0.14 + i * 0.27
            if y0 <= v <= y0 + 0.17 and 0.08 <= u <= 0.92:
                if solid or not (0.42 <= u <= 0.58):
                    return (gold[0], gold[1], gold[2], 255)
        return (bg[0], bg[1], bg[2], 235)
    return tex(name, 16, fn)


def build_tcd():
    def yinyang(u, v):
        r = disc_alpha(u, v)
        if r > 1.0:
            return (0, 0, 0, 0)
        top = math.hypot(u - 0.5, v - 0.25) <= 0.25
        bot = math.hypot(u - 0.5, v - 0.75) <= 0.25
        white = top or (u < 0.5 and not bot)
        if math.hypot(u - 0.5, v - 0.25) < 0.07:
            white = False
        if math.hypot(u - 0.5, v - 0.75) < 0.07:
            white = True
        c = rgb("F8F6EC") if white else rgb("16141C")
        if r > 0.95:
            c = rgb("FFD54A")
        return (c[0], c[1], c[2], 255 * smooth(1.0, 0.97, r))
    tex("tcd_yinyang", 64, yinyang)
    metal_tex("tcd_gold", "FFD54A", "FFF6C8", "A87A10", 1101, engrave=False)
    noise_tex("tcd_white", "F8F6EC", "E0DCCC", 1102, scale=3.0)
    noise_tex("tcd_black", "16141C", "2A2630", 1103, scale=3.0)
    band_tex("tcd_band", "FFF6C8", "FFD54A", 255)
    band_tex("tcd_band_u", "FFF6C8", "FFD54A", 255, along_u=True)
    for i, lines in enumerate(TRIGRAMS):
        trigram_tex("tcd_tri_%d" % i, lines)
        trigram_tex("tcd_tri_%d_u" % i, lines, rotated=True)
    n = Noise(1104)

    def plank(dark):
        def fn(u, v):
            base = rgb("16141C") if dark else rgb("F8F6EC")
            alt = rgb("2E2A36") if dark else rgb("E4E0D0")
            c = mix(base, alt, n.fbm(u * 6, v * 2))
            grain = abs(math.sin((v * 9 + n.fbm(u * 2, v * 4)) * math.pi))
            c = mix(c, alt, 0.3 * grain ** 8)
            if v < 0.07 or v > 0.93:
                c = rgb("FFD54A")
            return (c[0], c[1], c[2], 255)
        return fn
    tex("tcd_plank_black", 32, plank(True))
    tex("tcd_plank_white", 32, plank(False))
    # ---- the yin-yang disc standing in the XY plane (anchor = its centre)
    m = Model("tcd_taiji")
    box(m, [0.0, 0.0, 7.6], [16.0, 16.0, 8.4], "tcd_yinyang", glow=12, sides=("north", "south"))
    ring(m, (8.0, 8.0, 8.0), 8.1, 1.0, 1.4, 16, "tcd_gold", "tcd_gold", plane="xy", glow=12, sides=SIDES)
    cbox(m, (8.0, 12.0, 8.0), (2.2, 2.2, 1.3), "tcd_black", glow=4)
    cbox(m, (8.0, 4.0, 8.0), (2.2, 2.2, 1.3), "tcd_white", glow=12)
    # the bagua ring (a part: counter-spins about the disc's axis)
    b = Model("tcd_bagua")
    ring(b, (8.0, 8.0, 8.0), 11.0, 0.5, 0.5, 16, "tcd_band", "tcd_band_u", plane="xy", glow=15)
    for i in range(8):
        theta = 90.0 - 45.0 * i
        px, py = 8.0 + 13.0 * math.cos(math.radians(theta)), 8.0 + 13.0 * math.sin(math.radians(theta))
        a, swapped = norm_angle(theta - 90.0)
        size = (3.6, 6.0, 0.6) if swapped else (6.0, 3.6, 0.6)
        name = "tcd_tri_%d_u" % i if swapped else "tcd_tri_%d" % i
        cbox(b, (px, py, 8.0), size, name, rot_axis="z", rot_angle=a, glow=15, sides=("north", "south"))

    def plank_model(mid, top, bottom):
        p = Model(mid)
        box(p, [-16.0, 7.3, -2.0], [32.0, 8.7, 18.0], {"*": "tcd_gold", "up": top, "down": bottom}, glow=10)
        box(p, [-16.0, 8.7, -2.0], [32.0, 8.9, -1.0], "tcd_gold", glow=14, sides=("up", "north", "south"))
        box(p, [-16.0, 8.7, 17.0], [32.0, 8.9, 18.0], "tcd_gold", glow=14, sides=("up", "north", "south"))
    plank_model("tcd_plank_yin", "tcd_plank_black", "tcd_plank_white")
    plank_model("tcd_plank_yang", "tcd_plank_white", "tcd_plank_black")


# ================================================================================================ Immortal · heaven_trampling

def build_ht():
    noise_tex("ht_light", "FFF4D0", "F0E0A0", 1201, scale=3.0, alpha=200, c2="FFFFFF")
    pearl_tex("ht_node", "FFFFFF", "F0E0A0")
    star_tex("ht_star", "FFFFFF", "F0E0A0")
    band_tex("ht_band", "FFFFFF", "F0E0A0", 240)
    band_tex("ht_band_u", "FFFFFF", "F0E0A0", 240, along_u=True)

    def column(u, v):
        c = mix(rgb("FFFFFF"), rgb("F0E0A0"), abs(u - 0.5) * 2)
        return (c[0], c[1], c[2], 200 * (1.0 - v) ** 0.5 * (1.0 - abs(u - 0.5) * 1.2))
    tex("ht_column", 32, column)
    noise_tex("ht_light_dark", "F0E0A0", "D8C070", 1202, scale=3.0, alpha=210)
    band_tex("ht_ring", "FFFFFF", "F0E0A0", 240)
    band_tex("ht_ring_u", "FFFFFF", "F0E0A0", 240, along_u=True)
    m = Model("ht_step")
    ring(m, (8.0, 8.0, 8.0), 6.0, 1.2, 0.4, 16, "ht_band", "ht_band_u")
    for i in range(9):
        a = math.radians(40.0 * i)
        cbox(m, (8 + 6.0 * math.cos(a), 8.2, 8 + 6.0 * math.sin(a)), (1.4, 1.0, 1.4), "ht_node", rot_axis="y", rot_angle=45.0, glow=15)
    s = Model("ht_star")
    cbox(s, (8.0, 8.0, 8.0), (2.4, 2.4, 2.4), "ht_node", glow=15)
    cbox(s, (8.0, 8.0, 8.0), (2.0, 2.0, 2.0), "ht_node", glow=15, rot_axis="y", rot_angle=45.0)
    box(s, [0.0, 0.0, 7.95], [16.0, 16.0, 8.05], "ht_star", glow=15, sides=("north", "south"))
    box(s, [7.95, 0.0, 0.0], [8.05, 16.0, 16.0], "ht_star", glow=15, sides=("east", "west"))
    box(s, [0.0, 7.95, 0.0], [16.0, 8.05, 16.0], "ht_star", glow=15, sides=("up", "down"))
    foot_model("ht_sky_foot", "ht_light", "ht_light_dark", "ht_light", "ht_node", "ht_node", "ht_column", glow=15)
    ring_model("ht_ring", "ht_ring", "ht_ring_u")


# ================================================================================================ own arts

DAOS = (("sword", "D8E6FF"), ("fire", "FF6A2A"), ("ice", "9FF3FF"), ("lightning", "B57CFF"), ("body", "D9A05B"),
        ("life", "6CFF8A"), ("shadow", "7A6AA0"), ("space", "5A3FD0"), ("none", "FFD54A"))


def emblem(dao, u, v):
    """Coverage 0..1 of the Dao's emblem at (u, v)."""
    x, y = u - 0.5, v - 0.5
    if dao == "sword":
        blade = abs(x) < 0.05 * (1.0 - clamp01((-0.35 - y) / 0.1)) and -0.42 < y < 0.18
        guard = abs(y - 0.2) < 0.035 and abs(x) < 0.2
        grip = abs(x) < 0.035 and 0.2 < y < 0.36
        pommel = math.hypot(x, y - 0.39) < 0.045
        return 1.0 if blade or guard or grip or pommel else 0.0
    if dao == "fire":
        t = 0.5 - y
        if t < 0.05 or t > 0.95:
            return 0.0
        env = 0.28 * math.sqrt(max(0.0, 1.0 - ((0.3 - t) / 0.26) ** 2)) if t < 0.3 else 0.28 * (1.0 - (t - 0.3) / 0.65) ** 0.9
        lick = 0.5 + 0.5 * math.cos((x / 0.28 + 1) * 1.5 * math.tau)
        env *= 1.0 - smooth(0.4, 0.95, t) * (1.0 - lick) * 0.9
        return 1.0 if abs(x) < env else 0.0
    if dao == "ice":
        best = 1.0
        for k in range(6):
            a = math.radians(60 * k)
            ex, ey = 0.4 * math.cos(a), 0.4 * math.sin(a)
            best = min(best, seg_dist(x, y, 0, 0, ex, ey))
            for f in (0.5, 0.75):
                bx, by = ex * f, ey * f
                for s in (-1, 1):
                    b2 = a + s * math.radians(45)
                    best = min(best, seg_dist(x, y, bx, by, bx + 0.12 * math.cos(b2), by + 0.12 * math.sin(b2)))
        return 1.0 if best < 0.03 else 0.0
    if dao == "lightning":
        pts = [(0.12, -0.42), (-0.12, 0.02), (0.08, 0.02), (-0.14, 0.44)]
        return 1.0 if poly_dist(x, y, pts) < 0.055 else 0.0
    if dao == "body":
        # three peaks of a mountain (山)
        base = -0.05 < y < 0.38 and abs(x) < 0.36 and y > 0.3 - 0.0
        strokes = (abs(x) < 0.04 and -0.38 < y < 0.38) or (abs(x - 0.3) < 0.04 and -0.15 < y < 0.38) or (abs(x + 0.3) < 0.04 and -0.15 < y < 0.38)
        floor = abs(y - 0.36) < 0.04 and abs(x) < 0.34
        return 1.0 if strokes or floor or base and False else 0.0
    if dao == "life":
        # a leaf with its midrib cut out
        rx, ry = (x + y) * 0.7071, (y - x) * 0.7071
        inside = (rx / 0.42) ** 2 + (ry / 0.2) ** 2 < 1.0
        rib = abs(ry) < 0.018
        return 1.0 if inside and not rib else 0.0
    if dao == "shadow":
        outer = math.hypot(x, y) < 0.38
        inner = math.hypot(x - 0.16, y - 0.06) < 0.32
        return 1.0 if outer and not inner else 0.0
    if dao == "space":
        r = math.hypot(x, y)
        if r > 0.42 or r < 0.03:
            return 1.0 if r < 0.06 else 0.0
        a = math.atan2(y, x)
        k = (r / 0.42 * 3.0 - a / math.tau) % 1.0
        return 1.0 if k < 0.22 else 0.0
    # none: an eight-pointed star
    r = math.hypot(x, y)
    a = math.atan2(y, x)
    reach = 0.18 + 0.24 * abs(math.cos(a * 4)) ** 6
    return 1.0 if r < reach else 0.0


def build_oa():
    metal_tex("oa_gold", "FFD54A", "FFF6C8", "A87A10", 1301, engrave=False)
    noise_tex("oa_white", "F4F4FF", "D8DCF0", 1302, scale=3.0, c2="FFFFFF")
    for (dao, col) in DAOS:
        c = rgb(col)

        def disc(u, v, c=c):
            r = disc_alpha(u, v)
            if r > 1.0:
                return (0, 0, 0, 0)
            k = mix(mix(c, BLACK, 0.55), c, r ** 1.5)
            if abs(r - 0.92) < 0.04:
                k = rgb("FFD54A")
            return (k[0], k[1], k[2], 210 * smooth(1.0, 0.96, r))
        tex("oa_disc_%s" % dao, 32, disc)

        def emb(u, v, dao=dao, c=c):
            cov = emblem(dao, u, v)
            if cov <= 0.0:
                # a soft glow around the emblem
                near = max(emblem(dao, u + du, v + dv) for du in (-0.06, 0.0, 0.06) for dv in (-0.06, 0.0, 0.06))
                if near > 0:
                    k = mix(c, WHITE, 0.4)
                    return (k[0], k[1], k[2], 110)
                return (0, 0, 0, 0)
            k = mix(WHITE, c, 0.25)
            return (k[0], k[1], k[2], 255)
        tex("oa_emblem_%s" % dao, 32, emb)

        def plate(u, v, c=c):
            k = mix(c, WHITE, 0.2 + 0.3 * (1 - v))
            if u < 0.1 or u > 0.9 or v < 0.12 or v > 0.88:
                k = rgb("FFD54A")
            return (k[0], k[1], k[2], 255)
        tex("oa_plate_%s" % dao, 16, plate)
    glyph_tex("oa_feng", FENG, "FFD54A", "F4F4FF", border="FFD54A", glow="FFF6C8")
    pearl_tex("oa_orb_core", "FFFFFF", "FFE8A0")
    glow_disc_tex("oa_glow", "FFFFFF", "FFD54A", alpha=190)

    def blade(u, v):
        d = abs(u - 0.5) * 2
        c = mix(WHITE, rgb("FFE8A0"), d)
        return (c[0], c[1], c[2], 255 * (1 - d * 0.3))
    tex("oa_blade", 16, blade)
    band_tex("oa_band", "FFFFFF", "FFD54A", 200)
    band_tex("oa_band_u", "FFFFFF", "FFD54A", 200, along_u=True)
    band_tex("oa_trail", "FFFFFF", "FFE8A0", 150)
    # ---- the Dao seal discs (one geometry, one texture set per Dao), standing in the XY plane
    for (dao, _col) in DAOS:
        m = Model("oa_seal_%s" % dao)
        box(m, [-1.0, -1.0, 7.9], [17.0, 17.0, 8.1], "oa_disc_%s" % dao, glow=12, sides=("north", "south"))
        ring(m, (8.0, 8.0, 8.0), 9.2, 0.9, 1.0, 8, "oa_gold", "oa_gold", plane="xy", glow=12, sides=SIDES, offset_deg=22.5, overlap=1.12)
        for i in range(8):
            theta = 90.0 - 45.0 * i
            px, py = 8.0 + 9.2 * math.cos(math.radians(theta)), 8.0 + 9.2 * math.sin(math.radians(theta))
            a, swapped = norm_angle(theta - 90.0)
            size = (1.8, 3.0, 1.4) if swapped else (3.0, 1.8, 1.4)
            cbox(m, (px, py, 8.0), size, "oa_plate_%s" % dao, rot_axis="z", rot_angle=a, glow=12)
        box(m, [2.5, 2.5, 7.6], [13.5, 13.5, 7.75], "oa_emblem_%s" % dao, glow=15, sides=("north",))
        box(m, [2.5, 2.5, 8.25], [13.5, 13.5, 8.4], "oa_emblem_%s" % dao, glow=15, sides=("south",))
    # ---- the six form props, white-gold (tinted by light only)
    b = Model("oa_blade")
    box(b, [7.4, 4.0, 7.4], [8.6, 8.0, 8.6], "oa_gold", glow=12)
    box(b, [5.0, 8.0, 7.2], [11.0, 9.0, 8.8], "oa_gold", glow=12)
    box(b, [7.0, 9.0, 7.7], [9.0, 27.0, 8.3], "oa_blade", glow=15)
    box(b, [7.4, 27.0, 7.75], [8.6, 30.0, 8.25], "oa_blade", glow=15)
    box(b, [7.75, 30.0, 7.85], [8.25, 32.0, 8.15], "oa_blade", glow=15)
    box(b, [6.0, 9.0, 7.98], [10.0, 30.0, 8.02], "oa_trail", glow=15, sides=("north", "south"))
    o = Model("oa_orb")
    cbox(o, (8.0, 8.0, 8.0), (4.0, 4.0, 4.0), "oa_orb_core", glow=15)
    cbox(o, (8.0, 8.0, 8.0), (3.6, 3.6, 3.6), "oa_orb_core", glow=15, rot_axis="y", rot_angle=45.0)
    cbox(o, (8.0, 8.0, 8.0), (3.6, 3.6, 3.6), "oa_orb_core", glow=15, rot_axis="x", rot_angle=45.0)
    box(o, [1.0, 7.95, 1.0], [15.0, 8.05, 15.0], "oa_glow", glow=15, sides=("up", "down"))
    box(o, [1.0, 1.0, 7.95], [15.0, 15.0, 8.05], "oa_glow", glow=15, sides=("north", "south"))
    nd = Model("oa_needle")
    box(nd, [7.6, 7.6, -6.0], [8.4, 8.4, 18.0], "oa_white", glow=15)
    box(nd, [7.8, 7.8, -10.0], [8.2, 8.2, -6.0], "oa_gold", glow=15)
    box(nd, [7.0, 7.98, 4.0], [9.0, 8.02, 26.0], "oa_trail", glow=15, sides=("up", "down"))
    box(nd, [7.98, 7.0, 4.0], [8.02, 9.0, 26.0], "oa_trail", glow=15, sides=("east", "west"))
    cr = Model("oa_crescent")
    for th in (-45.0, -22.5, 0.0, 22.5, 45.0):
        px, py = 8.0 + 12.0 * math.sin(math.radians(th)), 20.0 - 12.0 * math.cos(math.radians(th))
        width = 2.4 - abs(th) / 45.0 * 1.2
        cbox(cr, (px, py, 8.0), (5.2, width, 0.4), {"north": "oa_band", "south": "oa_band", "*": None}, rot_axis="z", rot_angle=th, glow=15,
             sides=("north", "south"))
    rb = Model("oa_rib")
    ca, sa = math.cos(math.radians(22.5)), math.sin(math.radians(22.5))
    c45 = math.cos(math.radians(45.0))
    s1 = (8.0, 8.0, -12.0)
    e1 = (8.0, s1[1] + 8.0 * ca, s1[2] + 8.0 * sa)
    box(rb, [7.0, s1[1], s1[2] - 0.7], [9.0, s1[1] + 8.0, s1[2] + 0.7], "oa_white", rot=("x", 22.5, list(s1)), glow=15)
    e2 = (8.0, e1[1] + 8.0 * c45, e1[2] + 8.0 * c45)
    box(rb, [7.1, e1[1], e1[2] - 0.6], [8.9, e1[1] + 8.0, e1[2] + 0.6], "oa_white", rot=("x", 45.0, list(e1)), glow=15)
    e3 = (8.0, e2[1] + 7.0 * sa, e2[2] + 7.0 * ca)
    box(rb, [7.2, e2[1] - 0.5, e2[2]], [8.8, e2[1] + 0.5, e2[2] + 7.0], "oa_white", rot=("x", -22.5, list(e2)), glow=15)
    box(rb, [7.3, e3[1] - 0.45, e3[2]], [8.7, e3[1] + 0.45, 8.0], "oa_white", glow=15)
    cbox(rb, e1, (1.6, 1.6, 1.6), "oa_gold", glow=15)
    cbox(rb, e2, (1.4, 1.4, 1.4), "oa_gold", glow=15)
    pl = Model("oa_plate")
    box(pl, [1.0, 7.0, 1.0], [15.0, 9.0, 15.0], {"*": "oa_gold", "up": "oa_feng", "down": "oa_feng"}, glow=15)
    for (x, z) in ((1.5, 1.5), (14.5, 1.5), (1.5, 14.5), (14.5, 14.5)):
        cbox(pl, (x, 9.3, z), (1.4, 0.8, 1.4), "oa_gold", glow=15)


# ================================================================================================ output and checks

def validate(m):
    problems = []
    if len(m.els) > 40:
        problems.append("%d elements (max 40)" % len(m.els))
    for i, e in enumerate(m.els):
        for k in ("from", "to"):
            for v in e[k]:
                if v < -16.0 or v > 32.0:
                    problems.append("element %d %s %s out of -16..32" % (i, k, e[k]))
        if any(e["from"][a] > e["to"][a] for a in range(3)):
            problems.append("element %d from > to" % i)
        if not e["faces"]:
            problems.append("element %d has no faces" % i)
        for face in e["faces"].values():
            if any(x < 0 or x > 16 for x in face["uv"]):
                problems.append("element %d uv %s" % (i, face["uv"]))
        rot = e.get("rotation")
        if rot:
            if rot["axis"] not in ("x", "y", "z"):
                problems.append("element %d axis %s" % (i, rot["axis"]))
            if rot["angle"] not in (-45.0, -22.5, 0.0, 22.5, 45.0):
                problems.append("element %d angle %s" % (i, rot["angle"]))
            for v in rot["origin"]:
                if v < -16.0 or v > 32.0:
                    problems.append("element %d origin %s" % (i, rot["origin"]))
        le = e.get("light_emission", 0)
        if not (0 <= le <= 15):
            problems.append("element %d light_emission %s" % (i, le))
    for name in m.tex:
        if name not in TEXTURES:
            problems.append("texture %s missing" % name)
    return problems


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def model_json(m):
    textures = {key: "%s:item/fx/%s" % (NS, name) for name, key in m.tex.items()}
    first = next(iter(m.tex))
    textures["particle"] = "%s:item/fx/%s" % (NS, first)
    return {"textures": textures, "elements": m.els}


def main():
    build_cgf()
    build_agt()
    build_gsd()
    build_ldd()
    build_bt()
    build_sts()
    build_kp()
    build_kd()
    build_nh()
    build_fem()
    build_tcd()
    build_ht()
    build_oa()
    errors = 0
    used = set()
    for mid, m in sorted(MODELS.items()):
        problems = validate(m)
        if problems:
            errors += 1
            print("MODEL %s:" % mid)
            for p in problems:
                print("   ", p)
        used.update(m.tex)
        write_json(os.path.join(MODEL_DIR, mid + ".json"), model_json(m))
        write_json(os.path.join(ITEM_DIR, mid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/fx/%s" % (NS, mid)}})
        low = min(e["from"][1] for e in m.els)
        print("  %-24s %2d elements  baseY %6.3f" % (mid, len(m.els), (low - 8.0) / 16.0))
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    with open(IDS_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(MODELS)) + "\n")
    print("ultimates B models: %d, textures: %d, errors: %d" % (len(MODELS), len(used), errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
