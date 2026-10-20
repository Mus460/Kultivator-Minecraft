"""Generates the 3D props of the Sword, Lightning, Ice and Space Dao ultimates (DESIGN 30.4.2-30.4.5, module U1):

  zx_    Formasi Pedang Zhuxian (zhuxian_sword_formation): the four formation swords, plinths, the formation map, the
         sky sword, the light seams
  hps_   Seni Pedang Hukuman Langit (heaven_punishing_sword): the punishing sword, the Southern Heavenly Gate and its
         doors, the rune halo, the chains of law
  tte_   Formasi Kaisar Petir Kesengsaraan (tribulation_thunder_emperor_array): the palace drums, the Luo Shu grid, the
         bolt, the arcs of the tribulation whirlpool
  nhtv_  Tianzun Guntur Sembilan Langit (nine_heavens_thunder_venerable): the nine sky rings, the black qilin and its
         tail, the gold rod whip, the thunder deity and its hammer
  ipd_   Turunnya Phoenix Es (ice_phoenix_descent): the ice phoenix (body, wings, tail), the frost quill, the six-fold ring
  fmq_   Membekukan Gunung Qi (freeze_mount_qi): the crystal peak (and its cracked variant), the icicle crown, the ice
         coffin, the snowflake sigil
  vm_    Cermin Hampa (void_mirror): the void mirror (and its rippling face), the rune ring, the stored-blow shard
  tsd_   Wilayah Roh Waktu (time_spirit_domain): the great clock, its hands, two gears, the hourglass, a droplet
  sbh_   Lubang Hitam (stellar_black_hole): the young star, the black core and its photon ring, the accretion disc, the
         polar jet, a debris chunk

Everything here is ours: cuboid item models and procedural textures, nothing is downloaded (no Kenney texture on a
model). Writes:
  textures/item/fx/<prefix>*.png     16x16 .. 64x64 textures
  models/item/fx/<id>.json           one cuboid model per prop
  items/fx/<id>.json                 the item definitions (kultivator:fx/<id>)
  tools/fx_model_ids_ua.txt          this module's id list (tools/fx_model_ids.txt is never touched)

Rules kept by every model (checked by validate()):
  * the anchor is (8, 8, 8) (the grip of a weapon, the base of a standing prop, the centre of a floating one); the
    front faces north (-z);
  * at most 40 elements; element coordinates within -16..32, uv within 0..16;
  * an element rotation is a single axis by a multiple of 22.5 between -45 and 45;
  * rotations follow the right hand (Y by +a turns +x towards -z, X by +a turns +y towards +z, Z by +a turns +x
    towards +y).
Light: structure elements carry no light_emission (the display's brightness override lights them, so a prop breathes
by light), glowing cores, edges and runes carry 15.

Run with: python tools/gen_ultimates_a_models.py [--sheet <png>]   (validates everything and prints a summary; --sheet
writes a contact sheet of every texture, 4x scaled on a checkerboard, for checking by eye)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")
IDS_FILE = os.path.join(HERE, "fx_model_ids_ua.txt")
NS = "kultivator"
PREFIXES = ("zx_", "hps_", "tte_", "nhtv_", "ipd_", "fmq_", "vm_", "tsd_", "sbh_")

TEXTURES = {}
MODELS = {}


# ================================================================================================ colour helpers

def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


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
            img.set(x, y, (c[0], c[1], c[2], int(max(0, min(255, round(c[3]))))))
    TEXTURES[name] = img
    return name


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0.0 if L <= 1e-9 else clamp01(((px - ax) * dx + (py - ay) * dy) / L)
    qx, qy = ax + t * dx, ay + t * dy
    return math.hypot(px - qx, py - qy)


def rune_strokes(seed, cols=3, rows=4, strokes=5, box=(0.2, 0.15, 0.8, 0.85)):
    """A procedural seal-script glyph: segments joining nodes of a small grid (deterministic by seed)."""
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    nodes = [(x0 + (x1 - x0) * i / (cols - 1), y0 + (y1 - y0) * j / (rows - 1)) for j in range(rows) for i in range(cols)]
    segs = []
    cur = r.randrange(len(nodes))
    for _ in range(strokes):
        i, j = cur % cols, cur // cols
        options = []
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)):
            ni, nj = i + di, j + dj
            if 0 <= ni < cols and 0 <= nj < rows:
                options.append(nj * cols + ni)
        nxt = r.choice(options)
        segs.append((nodes[cur], nodes[nxt]))
        cur = nxt if r.random() < 0.7 else r.randrange(len(nodes))
    # a frame dot or a hook
    segs.append(((x0, y1), (x1, y1)) if r.random() < 0.5 else ((x0, y0), (x0, y1)))
    return segs


def ink_at(u, v, segs, width):
    d = min(seg_dist(u, v, a[0], a[1], b[0], b[1]) for a, b in segs)
    return clamp01(1.0 - (d - width * 0.5) / 0.02)


# ================================================================================================ texture kinds

def t_metal(name, base, size=16, seed=1, light=0.30, dark=0.35, grain=0.08, bevel=1.0, alpha=255, tint=None):
    """Brushed metal: a top-light gradient, fine grain, a light top/left and dark bottom/right bevel."""
    n = Noise(seed)
    c0 = rgb(base)
    hi = mix(c0, WHITE, light)
    lo = mix(c0, BLACK, dark)
    px = 1.0 / size

    def fn(u, v):
        c = mix(hi, c0, smooth(0.0, 0.6, v))
        c = mix(c, lo, smooth(0.55, 1.0, v) * 0.6)
        g = n.fbm(u * 8.0, v * 1.5) - 0.5
        c = mix(c, WHITE if g > 0 else BLACK, abs(g) * grain * 2.0)
        if tint:
            c = mix(c, rgb(tint), 0.15)
        b = bevel * px
        if u < b or v < b:
            c = mix(c, WHITE, 0.35)
        elif u > 1.0 - b or v > 1.0 - b:
            c = mix(c, BLACK, 0.35)
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def t_flat(name, colour, alpha=255, size=16, seed=0, grain=0.05, colour2=None):
    n = Noise(seed)
    c0 = rgb(colour)
    c1 = rgb(colour2) if colour2 else c0

    def fn(u, v):
        c = mix(c0, c1, v)
        g = n.fbm(u * 5.0, v * 5.0) - 0.5
        c = mix(c, WHITE if g > 0 else BLACK, abs(g) * grain * 2.0)
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def t_glow(name, core, edge, size=16, alpha_core=255, alpha_edge=150, across="u", power=2.0, ends=0.0):
    """A light bar: white-hot along the middle, the edge colour and alpha towards the sides (across u or v); with
    {@code ends} > 0 the bar also fades out over that fraction at both ends of the other axis."""
    c0, c1 = rgb(core), rgb(edge)

    def fn(u, v):
        s = u if across == "u" else v
        o = v if across == "u" else u
        k = clamp01(1.0 - abs(s - 0.5) * 2.0)
        c = mix(c1, c0, k ** power)
        a = alpha_edge + (alpha_core - alpha_edge) * k ** 1.5
        if ends > 0.0:
            a *= smooth(0.0, ends, o) * smooth(0.0, ends, 1.0 - o)
        return (c[0], c[1], c[2], a)
    return tex(name, size, fn)


def t_bands(name, c1, c2, bands=6, size=16, across="v", seed=3, edge=None):
    """Alternating wrap bands (a grip), each band shaded round."""
    a, b = rgb(c1), rgb(c2)
    n = Noise(seed)

    def fn(u, v):
        s = v if across == "v" else u
        o = u if across == "v" else v
        k = (s * bands) % 1.0
        c = a if int(s * bands) % 2 == 0 else b
        c = mix(c, BLACK, 0.35 * abs(k - 0.5) * 2.0)
        c = mix(c, WHITE, 0.18 * (1.0 - abs(o - 0.5) * 2.0))
        c = mix(c, WHITE, 0.08 * (n.fbm(u * 6, v * 6) - 0.5))
        if edge and (k < 0.08 or k > 0.92):
            c = rgb(edge)
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def t_runes(name, base, ink, size=32, seed=1, border=None, width=0.07, glow_ink=False, base2=None, alpha=255, strokes=5):
    """A plate with a procedural seal-script glyph (and an optional border line)."""
    c0 = rgb(base)
    c2 = rgb(base2) if base2 else mix(c0, BLACK, 0.25)
    ci = rgb(ink)
    segs = rune_strokes(seed, strokes=strokes)
    n = Noise(seed + 7)

    def fn(u, v):
        c = mix(c0, c2, v)
        c = mix(c, WHITE, 0.06 * (n.fbm(u * 6, v * 6) - 0.5))
        if border:
            e = min(u, v, 1.0 - u, 1.0 - v)
            if 0.06 < e < 0.06 + 1.6 / size:
                c = rgb(border)
            elif e <= 0.06:
                c = mix(c, BLACK, 0.25)
        k = ink_at(u, v, segs, width)
        if glow_ink and k < 1.0:
            d = min(seg_dist(u, v, a[0], a[1], b[0], b[1]) for a, b in segs)
            c = mix(c, ci, 0.45 * clamp01(1.0 - d / 0.12))
        c = mix(c, ci, k)
        if glow_ink and k > 0.6:
            c = mix(c, WHITE, 0.35)
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def t_stone(name, base, size=32, seed=1, cracks=0, crack_colour="1E1A16", moss=None):
    n = Noise(seed)
    c0 = rgb(base)
    r = random.Random(seed)
    lines = []
    for _ in range(cracks):
        x, y = r.random(), r.random()
        for _ in range(3):
            nx, ny = clamp01(x + (r.random() - 0.5) * 0.5), clamp01(y + (r.random() - 0.5) * 0.5)
            lines.append(((x, y), (nx, ny)))
            x, y = nx, ny
    cc = rgb(crack_colour)

    def fn(u, v):
        g = n.fbm(u * 4.0, v * 4.0, 4)
        c = mix(mix(c0, BLACK, 0.3), mix(c0, WHITE, 0.25), g)
        speck = n.smooth(u * 23.0, v * 23.0)
        if speck > 0.82:
            c = mix(c, WHITE, 0.25)
        if lines:
            d = min(seg_dist(u, v, a[0], a[1], b[0], b[1]) for a, b in lines)
            c = mix(c, cc, clamp01(1.0 - d * size / 1.1))
        if moss:
            c = mix(c, rgb(moss), 0.4 * smooth(0.6, 0.9, n.fbm(u * 3 + 9, v * 3)))
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def t_cloud(name, top, bottom, size=32, seed=1, alpha=240, under=None):
    """Billowing cloud: light tops, shaded bottoms, soft puffs; {@code under} tints the underside (storm light)."""
    n = Noise(seed)
    c0, c1 = rgb(top), rgb(bottom)

    def fn(u, v):
        g = n.fbm(u * 3.0, v * 3.0, 4)
        puff = 0.5 + 0.5 * math.sin(u * math.pi * 4 + g * 3.0)
        c = mix(c0, c1, smooth(0.25, 1.0, v) * (0.7 + 0.3 * (1.0 - puff)))
        c = mix(c, WHITE, 0.25 * smooth(0.55, 0.9, g) * (1.0 - v))
        if under:
            c = mix(c, rgb(under), 0.55 * smooth(0.6, 1.0, v))
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def t_glass(name, tint, alpha=130, size=32, seed=1, stars=0, star_colour="FFFFFF", rim="FFFFFF", deep=None, ripple=False):
    """Translucent glass: a tinted body, a light rim, a diagonal sheen; {@code stars} star specks (void glass); with
    {@code ripple} concentric bright rings (a struck mirror)."""
    n = Noise(seed)
    c0 = rgb(tint)
    cd = rgb(deep) if deep else mix(c0, BLACK, 0.4)
    r = random.Random(seed)
    pts = [(r.random(), r.random(), 0.4 + r.random() * 0.6) for _ in range(stars)]
    sc = rgb(star_colour)
    rc = rgb(rim)

    def fn(u, v):
        d = math.hypot(u - 0.5, v - 0.5)
        c = mix(c0, cd, clamp01(d * 1.6))
        a = alpha
        neb = n.fbm(u * 3.0, v * 3.0, 4)
        c = mix(c, mix(c0, WHITE, 0.3), 0.35 * smooth(0.55, 0.85, neb))
        sheen = abs((u + v) - 0.7)
        if sheen < 0.06:
            c = mix(c, WHITE, 0.35 * (1.0 - sheen / 0.06))
        for (sx, sy, b) in pts:
            k = clamp01(1.0 - math.hypot(u - sx, v - sy) * size / 1.0)
            if k > 0:
                c = mix(c, sc, k * b)
                a = max(a, 255 * k * b)
        if ripple:
            w = 0.5 + 0.5 * math.cos(d * math.pi * 14.0)
            c = mix(c, WHITE, 0.55 * w ** 6 * clamp01(1.0 - d * 1.4))
            a = max(a, alpha + 80 * w ** 6)
        e = min(u, v, 1.0 - u, 1.0 - v)
        if e < 1.5 / size:
            c = mix(c, rc, 0.6)
            a = max(a, 220)
        return (c[0], c[1], c[2], a)
    return tex(name, size, fn)


def t_ice(name, light, dark, size=32, seed=1, alpha=215, cracks=0, crack_colour="FFFFFF", core=None):
    """Faceted ice: angular facets (a few planes of light), frosty grain, bright edges; cracks for the breaking variant."""
    n = Noise(seed)
    c0, c1 = rgb(light), rgb(dark)
    r = random.Random(seed)
    facets = [(r.random() * math.tau, r.random()) for _ in range(5)]
    lines = []
    for _ in range(cracks):
        x, y = 0.3 + r.random() * 0.4, r.random() * 0.3
        for _ in range(4):
            nx, ny = clamp01(x + (r.random() - 0.5) * 0.45), clamp01(y + 0.12 + r.random() * 0.2)
            lines.append(((x, y), (nx, ny)))
            x, y = nx, ny
    cc = rgb(crack_colour)

    def fn(u, v):
        f = 0.0
        for (ang, off) in facets:
            s = math.cos(ang) * u + math.sin(ang) * v + off
            f += (s * 3.0) % 1.0
        f /= len(facets)
        c = mix(c1, c0, 0.45 + 0.55 * f)
        c = mix(c, WHITE, 0.12 * (n.fbm(u * 9, v * 9) - 0.3))
        if core:
            c = mix(c, rgb(core), 0.5 * clamp01(1.0 - abs(u - 0.5) * 3.0))
        e = min(u, v, 1.0 - u, 1.0 - v)
        a = alpha
        if e < 1.2 / size:
            c = mix(c, WHITE, 0.7)
            a = 245
        if lines:
            d = min(seg_dist(u, v, p[0], p[1], q[0], q[1]) for p, q in lines)
            k = clamp01(1.0 - d * size / 1.2)
            c = mix(c, cc, k)
            a = max(a, 255 * k)
        return (c[0], c[1], c[2], a)
    return tex(name, size, fn)


def t_feather(name, base, tip, shadow, size=32, seed=1, alpha=235, eye=None, along="v"):
    """A feather: a quill line along the length, barbs angled out, the tip colour at the end, an optional eye spot."""
    n = Noise(seed)
    c0, ct, cs = rgb(base), rgb(tip), rgb(shadow)
    ce = rgb(eye) if eye else None

    def fn(u, v):
        s = v if along == "v" else u      # 0 root .. 1 tip
        o = u if along == "v" else v      # across
        c = mix(c0, ct, smooth(0.55, 1.0, s))
        barb = 0.5 + 0.5 * math.sin((s * 18.0 - abs(o - 0.5) * 9.0) * math.pi)
        c = mix(c, cs, 0.25 * barb * (1.0 - s * 0.5))
        c = mix(c, WHITE, 0.10 * (n.fbm(u * 6, v * 6) - 0.4))
        q = abs(o - 0.5)
        if q < 0.04:
            c = mix(c, WHITE, 0.7)
        a = alpha
        # rounded tip and soft sides
        if s > 0.85:
            a *= clamp01(1.0 - (q / max(0.05, 0.5 * (1.0 - (s - 0.85) / 0.15))) ** 4)
        if ce is not None:
            d = math.hypot((o - 0.5) * 1.4, s - 0.8)
            if d < 0.16:
                c = mix(ce, WHITE, 0.6 if d < 0.06 else 0.0)
                c = mix(c, cs, 0.4 * smooth(0.11, 0.16, d))
        return (c[0], c[1], c[2], a)
    return tex(name, size, fn)


def t_disc(name, inner, outer, size=32, alpha=255, rim=None, rays=0, ray_colour=None, cut=True):
    """A round face (transparent corners): a radial ramp, an optional rim line and rays."""
    c0, c1 = rgb(inner), rgb(outer)
    rc = rgb(rim) if rim else None
    rr = rgb(ray_colour) if ray_colour else WHITE

    def fn(u, v):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        if cut and d > 1.0:
            return (0, 0, 0, 0)
        c = mix(c0, c1, d ** 1.4)
        if rays:
            ang = math.atan2(v - 0.5, u - 0.5)
            k = 0.5 + 0.5 * math.cos(ang * rays)
            c = mix(c, rr, 0.45 * k ** 8 * smooth(0.25, 0.75, d))
        if rc and d > 0.86:
            c = rc
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def t_scales(name, base, light, size=32, seed=1, glow=None):
    """Overlapping scales (the qilin's hide): arcs in offset rows, light rims, an optional glowing seam."""
    c0, cl = rgb(base), rgb(light)
    cg = rgb(glow) if glow else None
    rows = 6

    def fn(u, v):
        y = v * rows
        row = int(y)
        x = u * rows + (0.5 if row % 2 else 0.0)
        fx, fy = x % 1.0, y % 1.0
        d = math.hypot(fx - 0.5, fy)
        c = mix(cl, c0, smooth(0.0, 0.55, d))
        if 0.45 < d < 0.55:
            c = mix(c, cg if cg else cl, 0.6)
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def t_flame(name, core, body, tip, size=32, seed=1, alpha=230):
    """Flame tufts licking upwards (v = 0 top)."""
    n = Noise(seed)
    c0, c1, c2 = rgb(core), rgb(body), rgb(tip)

    def fn(u, v):
        t = 1.0 - v
        sway = (n.fbm(t * 2.0 + 3.0, u * 2.0) - 0.5) * 0.35
        x = abs(u - 0.5 - sway * t)
        env = 0.46 * (1.0 - t) ** 0.7
        tongues = 0.5 + 0.5 * math.cos(u * math.pi * 6.0 + t * 4.0)
        env *= 0.75 + 0.25 * tongues
        if x > env:
            return (0, 0, 0, 0)
        k = 1.0 - x / max(1e-4, env)
        c = mix(c2, c1, smooth(0.0, 0.6, k))
        c = mix(c, c0, smooth(0.6, 1.0, k) * (1.0 - t * 0.6))
        return (c[0], c[1], c[2], alpha * smooth(0.0, 0.25, k))
    return tex(name, size, fn)


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
        """faces: side -> (texture, uv) ; uv may be None for the full square. rot: (axis, angle, origin)."""
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

    def box(self, f, t, name, rot=None, glow=0, sides=SIDES, uv=None):
        return self.el(f, t, {s: (name, uv) for s in sides}, rot=rot, glow=glow)

    def cube(self, c, size, name, rot=None, glow=0, sides=SIDES):
        f = [c[i] - size[i] / 2.0 for i in range(3)]
        t = [c[i] + size[i] / 2.0 for i in range(3)]
        return self.el(f, t, {s: (name, None) for s in sides}, rot=rot, glow=glow)

    def faced(self, f, t, front, side, rot=None, glow=0, back=None):
        """A box whose north (front) face shows {@code front}, south {@code back} (or front), the rest {@code side}."""
        faces = {s: (side, None) for s in SIDES}
        faces["north"] = (front, None)
        faces["south"] = (back or front, None)
        return self.el(f, t, faces, rot=rot, glow=glow)


def norm_angle(angle):
    """A box direction angle (degrees) as (angle within -45..45, swapped): a box turned by 90 is the same box with two
    sides swapped, and by 180 the same box."""
    a = ((angle + 90.0) % 180.0) - 90.0
    if a > 45.0:
        return a - 90.0, True
    if a < -45.0:
        return a + 90.0, True
    return a, False


def oct_prism(m, c, r, y0, y1, side, top, glow=0, bottom=None):
    """An octagonal prism (two boxes, the second turned 45 degrees and a hair lower so no caps are coplanar)."""
    faces = {"north": (side, None), "south": (side, None), "east": (side, None), "west": (side, None), "up": (top, None),
             "down": (bottom or side, None)}
    m.el([c[0] - r, y0, c[2] - r], [c[0] + r, y1, c[2] + r], faces, glow=glow)
    m.el([c[0] - r, y0 + 0.02, c[2] - r], [c[0] + r, y1 - 0.04, c[2] + r], faces, rot=("y", 45.0, list(c)), glow=glow)


def ring_xz(m, c, r, length, width, th, name, name_u=None, segs=16, glow=0, phase=0.0, thin=0.97):
    """A flat ring in the XZ plane of {@code segs} tangent boxes (every other one a hair thinner: no coplanar faces)."""
    name_u = name_u or name
    for i in range(segs):
        theta = phase + 360.0 * i / segs
        px, pz = c[0] + r * math.cos(math.radians(theta)), c[2] + r * math.sin(math.radians(theta))
        a, swapped = norm_angle(-(theta + 90.0))
        k = thin if i % 2 else 1.0
        if not swapped:
            size, nm = (length, th * k, width * k), name
        else:
            size, nm = (width * k, th * k, length), name_u
        m.cube((px, c[1], pz), size, nm, rot=("y", a, [px, c[1], pz]), glow=glow)


def ring_xy(m, c, r, length, width, depth, name, name_u=None, segs=16, glow=0, phase=0.0, thin=0.97):
    """A standing ring in the XY plane (facing north) of {@code segs} tangent boxes."""
    name_u = name_u or name
    for i in range(segs):
        theta = phase + 360.0 * i / segs
        px, py = c[0] + r * math.cos(math.radians(theta)), c[1] + r * math.sin(math.radians(theta))
        # a box along x turned by b about z points along (cos b, sin b): the tangent at theta is theta + 90
        a, swapped = norm_angle(theta + 90.0)
        k = thin if i % 2 else 1.0
        if not swapped:
            size, nm = (length, width * k, depth * k), name
        else:
            size, nm = (width * k, length, depth * k), name_u
        m.cube((px, py, c[2]), size, nm, rot=("z", a, [px, py, c[2]]), glow=glow)


def ball(m, c, r, name, boxes=3, glow=0):
    s = 2 * r * 0.82
    m.cube(c, (s, s, s), name, glow=glow)
    if boxes >= 2:
        m.cube(c, (s, s * 0.9, s), name, rot=("y", 45.0, list(c)), glow=glow)
    if boxes >= 3:
        m.cube(c, (s * 0.9, s, s), name, rot=("x", 45.0, list(c)), glow=glow)


def flat_plate(m, half, name, y=8.0, th=0.08, glow=0, both=True):
    faces = {"up": (name, None)}
    if both:
        faces["down"] = (name, (0, 16, 16, 0))
    m.el([8 - half, y, 8 - half], [8 + half, y + th, 8 + half], faces, glow=glow)


# ================================================================================================ Sword: Zhuxian

ZX = {
    "zhu": ("B0102A", "FF5A6A"),   # 誅 crimson
    "lu": ("2EB85A", "6CFF8A"),    # 戮 jade
    "xian": ("4AB8D8", "9FF3FF"),  # 陷 frost
    "jue": ("7A3AC8", "B57CFF"),   # 絕 violet
}


def build_zx_textures():
    t_metal("zx_steel", "2A2A33", seed=11, light=0.25)
    t_glow("zx_edge", "FFFFFF", "D8E0F0", alpha_edge=230, power=1.2)
    t_metal("zx_guard", "C89A3A", seed=12, light=0.45, tint="FFD54A")
    t_glow("zx_eye", "FFFFFF", "FF3A3A", alpha_edge=255)
    for k, (deep, glow) in ZX.items():
        n = Noise(20 + len(k))
        c_deep, c_glow = rgb(deep), rgb(glow)

        def blade(u, v, c_deep=c_deep, c_glow=c_glow, n=n):
            steel = mix(rgb("E8ECF4"), rgb("9AA2B4"), abs(u - 0.5) * 1.6)
            c = mix(steel, c_glow, 0.22 + 0.12 * (1.0 - v))
            c = mix(c, WHITE, 0.10 * (n.fbm(u * 2.0, v * 9.0) - 0.4))
            if abs(u - 0.5) > 0.44:
                c = mix(c, c_deep, 0.5)
            return (c[0], c[1], c[2], 255)
        tex("zx_blade_" + k, 16, blade)
        t_glow("zx_fuller_" + k, "FFFFFF", glow, alpha_edge=255, power=1.6)
        t_bands("zx_grip_" + k, deep, "2A2A33", bands=7, seed=30 + len(k), edge="FFD54A")
        t_runes("zx_rune_" + k, "2A2A33", glow, size=32, seed=40 + len(k) * 3, glow_ink=True, width=0.09)
        t_bands("zx_tassel_" + k, glow, deep, bands=1, across="u", seed=50)
    # plinth
    t_stone("zx_stone", "7D7566", seed=61, cracks=3)
    t_stone("zx_stone_top", "8A8272", seed=62, cracks=1)
    t_metal("zx_trim", "FFD54A", seed=63, light=0.4, dark=0.4)
    t_runes("zx_plinth_glyph", "6A6256", "FFD54A", seed=64, border="FFD54A", width=0.08)
    t_flat("zx_socket", "141014", seed=65)
    # formation map: red-black lacquer (translucent) with a faint lattice and cloud scrolls
    n = Noise(70)

    def map_plate(u, v):
        c = mix(rgb("400010"), rgb("1A0006"), n.fbm(u * 4.0, v * 4.0))
        a = 150
        gx, gy = (u * 8.0) % 1.0, (v * 8.0) % 1.0
        if gx < 0.06 or gy < 0.06:
            c = mix(c, rgb("8A1020"), 0.6)
            a = 175
        d = math.hypot(u - 0.5, v - 0.5)
        ring = abs(d - 0.18)
        if ring < 0.012:
            c, a = rgb("FF3A4A"), 230
        diag = min(abs(u - v), abs(u + v - 1.0))
        if diag < 0.008 and d > 0.2:
            c, a = mix(c, rgb("FF6A6A"), 0.7), 200
        e = min(u, v, 1.0 - u, 1.0 - v)
        a *= smooth(0.0, 0.03, e)
        return (c[0], c[1], c[2], a)
    tex("zx_map_plate", 64, map_plate)
    t_glow("zx_map_rail", "FFE0E0", "FF3A4A", alpha_edge=230, power=1.3)
    t_glow("zx_map_rail_u", "FFE0E0", "FF3A4A", alpha_edge=230, power=1.3, across="v")
    t_disc("zx_map_node", "FFFFFF", "FF3A4A", cut=False)
    t_runes("zx_map_seal", "5A0010", "FF6A6A", size=32, seed=71, border="FFD54A", glow_ink=True, width=0.08)
    # sky sword
    n2 = Noise(80)

    def sky_blade(u, v):
        c = mix(rgb("FFFFFF"), rgb("B8C2D8"), abs(u - 0.5) * 1.7)
        c = mix(c, rgb("E8ECF4"), 0.3 + 0.2 * n2.fbm(u * 2, v * 6))
        if abs(v - 0.5) > 0.46:
            c = mix(c, rgb("B0102A"), 0.5)
        return (c[0], c[1], c[2], 255)
    tex("zx_sky_blade", 16, sky_blade)
    t_disc("zx_sky_rune", "FFFFFF", "B0102A", size=16, cut=False, rim="FFD54A")
    t_metal("zx_sky_guard", "FFD54A", seed=81, light=0.4, tint="FFE88A")
    t_runes("zx_sky_inlay", "B0102A", "FFD54A", size=32, seed=82, glow_ink=True)
    t_bands("zx_sky_grip", "B0102A", "5A0010", bands=9, seed=83, edge="FFD54A")
    t_glass("zx_sky_jewel", "FF2A3A", alpha=245, size=16, seed=84, rim="FFB0B0", deep="5A0010")
    t_glow("zx_sky_core", "FFFFFF", "FF8A8A", alpha_edge=200, power=1.5)
    # seams
    t_glow("zx_seam_core", "FFFFFF", "FF6A6A", alpha_edge=255, power=1.4, across="v")
    t_glow("zx_seam_halo", "FF9A9A", "FF3A4A", alpha_core=150, alpha_edge=0, power=1.0, across="v", ends=0.2)


def zx_sword(k):
    """A formation sword: grip at the anchor, blade up. Ring pommel with a jewel, banded grip, a beast-mouth guard with
    two horned wings, a steel blade with a raised glowing fuller, white edge bevels, a stepped point, rune plates on
    both faces, and a tassel that hangs along the grip when the sword stands point-down (as in this formation)."""
    m = Model("zx_sword_" + k)
    deep, glow = ZX[k]
    blade, fuller, grip, rune, tassel = "zx_blade_" + k, "zx_fuller_" + k, "zx_grip_" + k, "zx_rune_" + k, "zx_tassel_" + k
    # ring pommel (XY plane) and jewel
    ring_xy(m, (8.0, 3.2, 8.0), 1.35, 1.2, 0.5, 0.9, "zx_guard", segs=8)
    m.cube((8.0, 3.2, 8.0), (0.8, 0.8, 0.8), "zx_fuller_" + k, rot=("z", 45.0, [8.0, 3.2, 8.0]), glow=15)
    m.box([7.3, 4.5, 7.3], [8.7, 5.2, 8.7], "zx_guard")
    m.box([7.45, 5.2, 7.45], [8.55, 8.6, 8.55], grip)
    # guard: centre block, the beast's face plate, horned wings sweeping up
    m.box([6.2, 8.6, 7.1], [9.8, 10.4, 8.9], "zx_guard")
    m.faced([6.9, 8.9, 6.95], [9.1, 10.1, 7.1], "zx_eye", "zx_guard", glow=15)
    m.box([3.0, 9.0, 7.4], [6.4, 10.0, 8.6], "zx_guard", rot=("z", -22.5, [6.4, 9.5, 8.0]))
    m.box([9.6, 9.0, 7.4], [13.0, 10.0, 8.6], "zx_guard", rot=("z", 22.5, [9.6, 9.5, 8.0]))
    # blade, spine with the glowing fuller, edges, point
    m.box([7.05, 10.4, 7.65], [8.95, 28.5, 8.35], blade)
    m.box([7.7, 10.9, 7.5], [8.3, 27.0, 8.5], fuller, glow=15)
    m.box([6.7, 11.0, 7.88], [7.05, 28.0, 8.12], "zx_edge", glow=15)
    m.box([8.95, 11.0, 7.88], [9.3, 28.0, 8.12], "zx_edge", glow=15)
    m.box([7.35, 28.5, 7.72], [8.65, 30.2, 8.28], blade)
    m.box([7.75, 30.2, 7.8], [8.25, 31.4, 8.2], "zx_edge", glow=15)
    m.cube((8.0, 30.4, 8.0), (0.9, 0.9, 0.5), "zx_edge", rot=("z", 45.0, [8.0, 30.4, 8.0]), glow=15)
    # spine: a dark steel line behind the fuller
    m.box([7.9, 10.6, 8.35], [8.1, 28.0, 8.45], "zx_steel")
    # rune plates (front and back)
    m.el([7.3, 11.4, 7.42], [8.7, 13.8, 7.5], {"north": (rune, None)}, glow=15)
    m.el([7.3, 11.4, 8.5], [8.7, 13.8, 8.58], {"south": (rune, None)}, glow=15)
    # tassel (hangs towards +y: down when the sword stands point-down)
    m.box([9.9, 3.4, 7.9], [10.15, 7.2, 8.1], tassel)
    m.cube((10.02, 7.5, 8.0), (0.7, 0.7, 0.7), "zx_guard", rot=("y", 45.0, [10.02, 7.5, 8.0]))
    m.box([9.7, 7.8, 7.75], [10.35, 9.6, 8.25], tassel)
    return m


def zx_plinth():
    """An octagonal stone plinth: a wide base, a gold trim band, the body with a glyph plate on its front, a cap and a
    dark socket for the sword. Base at the anchor."""
    m = Model("zx_plinth")
    c = (8.0, 8.0, 8.0)
    oct_prism(m, c, 7.0, 8.0, 10.0, "zx_stone", "zx_stone_top")
    oct_prism(m, c, 6.2, 10.0, 10.6, "zx_trim", "zx_trim")
    oct_prism(m, c, 5.4, 10.6, 16.4, "zx_stone", "zx_stone_top")
    oct_prism(m, c, 6.0, 16.4, 17.2, "zx_trim", "zx_trim")
    oct_prism(m, c, 5.6, 17.2, 18.0, "zx_stone", "zx_stone_top")
    m.el([6.6, 18.0, 6.6], [9.4, 18.06, 9.4], {"up": ("zx_socket", None)})
    for z, side in ((2.55, "north"), (13.45, "south")):
        m.el([5.4, 11.4, min(z, z + 0.05)], [10.6, 15.6, max(z, z + 0.05)], {side: ("zx_plinth_glyph", None)}, glow=15)
    # four gold corner studs on the base
    for (dx, dz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        p = (8.0 + dx * 5.0, 10.4, 8.0 + dz * 5.0)
        m.cube(p, (1.0, 0.8, 1.0), "zx_trim", rot=("y", 45.0, list(p)))
    return m


def zx_map():
    """The formation diagram laid flat (32 x 32 px around the anchor): the lacquer plate, four nested square rails,
    corner nodes and the centre seal disc."""
    m = Model("zx_map")
    flat_plate(m, 16.0, "zx_map_plate", y=8.0, th=0.06)
    y = 8.08
    for i, half in enumerate((15.4, 11.4, 7.4)):
        w = 0.5 - i * 0.08
        h = 0.18
        m.el([8 - half, y, 8 - half - w / 2], [8 + half, y + h, 8 - half + w / 2], {"up": ("zx_map_rail", None), "north": ("zx_map_rail", None), "south": ("zx_map_rail", None)}, glow=15)
        m.el([8 - half, y + 0.01, 8 + half - w / 2], [8 + half, y + h + 0.01, 8 + half + w / 2], {"up": ("zx_map_rail", None), "north": ("zx_map_rail", None), "south": ("zx_map_rail", None)}, glow=15)
        m.el([8 - half - w / 2, y + 0.02, 8 - half + w / 2], [8 - half + w / 2, y + h + 0.02, 8 + half - w / 2], {"up": ("zx_map_rail_u", None), "east": ("zx_map_rail", None), "west": ("zx_map_rail", None)}, glow=15)
        m.el([8 + half - w / 2, y + 0.03, 8 - half + w / 2], [8 + half + w / 2, y + h + 0.03, 8 + half - w / 2], {"up": ("zx_map_rail_u", None), "east": ("zx_map_rail", None), "west": ("zx_map_rail", None)}, glow=15)
    for (dx, dz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        p = (8.0 + dx * 15.4, 8.3, 8.0 + dz * 15.4)
        m.cube(p, (1.4, 0.5, 1.4), "zx_map_node", rot=("y", 45.0, list(p)), glow=15)
    oct_prism(m, (8.0, 8.0, 8.0), 2.6, 8.06, 8.3, "zx_map_rail", "zx_map_seal", glow=15)
    return m


def zx_sky_sword():
    """The sword from the clouds: a red jewel pommel, a long crimson grip with gold rings, a cloud-scroll guard with a
    crimson inlay, seven blade segments of alternating width with rune studs alternating left and right, white edges,
    a glowing spine and a diamond point."""
    m = Model("zx_sky_sword")
    m.box([7.2, 0.6, 7.2], [8.8, 2.2, 8.8], "zx_sky_jewel", glow=15)
    m.box([7.25, 0.65, 7.25], [8.75, 2.15, 8.75], "zx_sky_jewel", rot=("y", 45.0, [8.0, 1.4, 8.0]), glow=15)
    m.box([6.8, 2.2, 6.8], [9.2, 3.0, 9.2], "zx_sky_guard")
    m.box([7.3, 3.0, 7.3], [8.7, 8.2, 8.7], "zx_sky_grip")
    for y in (4.2, 5.7, 7.2):
        m.box([7.1, y, 7.1], [8.9, y + 0.4, 8.9], "zx_sky_guard")
    # guard: centre, scroll arms, curls, inlay
    m.box([5.5, 8.2, 6.9], [10.5, 10.2, 9.1], "zx_sky_guard")
    m.box([1.0, 8.8, 7.2], [5.5, 9.8, 8.8], "zx_sky_guard", rot=("z", -22.5, [5.5, 9.3, 8.0]))
    m.box([10.5, 8.8, 7.2], [15.0, 9.8, 8.8], "zx_sky_guard", rot=("z", 22.5, [10.5, 9.3, 8.0]))
    m.cube((1.4, 11.0, 8.0), (1.5, 1.5, 1.4), "zx_sky_guard", rot=("z", 45.0, [1.4, 11.0, 8.0]))
    m.cube((14.6, 11.0, 8.0), (1.5, 1.5, 1.4), "zx_sky_guard", rot=("z", 45.0, [14.6, 11.0, 8.0]))
    m.el([6.5, 8.6, 6.84], [9.5, 9.8, 6.9], {"north": ("zx_sky_inlay", None)}, glow=15)
    # seven segments
    y = 10.2
    for i in range(7):
        w = 1.7 if i % 2 == 0 else 1.5
        m.box([8.0 - w, y, 7.55], [8.0 + w, y + 2.8, 8.45], "zx_sky_blade")
        sx = 7.0 if i % 2 == 0 else 9.0
        m.box([sx - 0.35, y + 1.05, 7.35], [sx + 0.35, y + 1.75, 7.55], "zx_sky_rune", glow=15)
        y += 2.8
    m.box([7.8, 10.4, 7.45], [8.2, 29.6, 8.55], "zx_sky_core", glow=15)
    m.box([5.95, 10.6, 7.85], [6.3, 29.4, 8.15], "zx_edge", glow=15)
    m.box([9.7, 10.6, 7.85], [10.05, 29.4, 8.15], "zx_edge", glow=15)
    m.box([6.9, 29.8, 7.65], [9.1, 31.0, 8.35], "zx_sky_blade")
    m.cube((8.0, 30.9, 8.0), (1.4, 1.4, 0.6), "zx_edge", rot=("z", 45.0, [8.0, 30.9, 8.0]), glow=15)
    return m


def zx_seam():
    """A thin light bar 48 px long (x -16..32) with soft ends: a white-hot core in a red halo."""
    m = Model("zx_seam")
    m.el([-14.0, 7.85, 7.85], [30.0, 8.15, 8.15], {s: ("zx_seam_core", None) for s in ("north", "south", "up", "down")}, glow=15)
    m.el([-16.0, 7.4, 7.4], [32.0, 8.6, 8.6], {s: ("zx_seam_halo", None) for s in ("north", "south", "up", "down")})
    return m


# ================================================================================================ Sword: heaven-punishing sword

def build_hps_textures():
    n = Noise(100)

    def blade(u, v):
        c = mix(rgb("FFF8E0"), rgb("E8D8A0"), abs(u - 0.5) * 1.6)
        c = mix(c, WHITE, 0.15 * (n.fbm(u * 2, v * 8) - 0.3))
        return (c[0], c[1], c[2], 255)
    tex("hps_blade", 16, blade)
    t_glow("hps_edge", "FFFFFF", "C7A2FF", alpha_edge=235, power=1.3)
    t_runes("hps_rune", "C89A3A", "FFFFFF", size=32, seed=101, border="FFF1C0", glow_ink=True, base2="8A6020")
    t_runes("hps_rune_b", "C89A3A", "FFFFFF", size=32, seed=102, border="FFF1C0", glow_ink=True, base2="8A6020")
    t_runes("hps_rune_c", "C89A3A", "FFFFFF", size=32, seed=103, border="FFF1C0", glow_ink=True, base2="8A6020")
    t_metal("hps_gold", "FFD54A", seed=104, light=0.45, dark=0.35)
    t_bands("hps_grip", "FFD54A", "6A4A10", bands=8, seed=105)
    t_disc("hps_sun", "FFFFFF", "FFB020", rays=12, ray_colour="FFF8C0", rim="FFD54A", cut=False)
    t_glow("hps_ridge", "FFFFFF", "FFE8A0", alpha_edge=230)
    # the gate
    t_metal("hps_pillar", "B0201A", seed=110, light=0.25, dark=0.4, grain=0.12)
    t_cloud("hps_cloud", "FFFFFF", "F0D890", seed=111)
    n3 = Noise(112)

    def roof(u, v):
        row = int(v * 6)
        k = (v * 6) % 1.0
        c = mix(rgb("8AF0C0"), rgb("2E8A60"), k)
        if (u * 8 + (0.5 if row % 2 else 0)) % 1.0 < 0.1:
            c = mix(c, rgb("1E5A40"), 0.6)
        c = mix(c, WHITE, 0.08 * (n3.fbm(u * 5, v * 5) - 0.5))
        return (c[0], c[1], c[2], 255)
    tex("hps_roof", 16, roof)
    t_metal("hps_beam", "8A1810", seed=113, light=0.3, tint="FFD54A")
    t_runes("hps_plaque", "1A2A6A", "FFD54A", size=32, seed=114, border="FFD54A", strokes=7, width=0.08)
    n4 = Noise(115)

    def door(u, v):
        c = mix(rgb("C02A20"), rgb("7A1008"), v)
        c = mix(c, BLACK, 0.12 * (n4.fbm(u * 3, v * 8)))
        e = min(u, v, 1.0 - u, 1.0 - v)
        if e < 0.08:
            c = mix(rgb("FFD54A"), rgb("A87A20"), v)
        elif e < 0.12:
            c = mix(c, BLACK, 0.4)
        return (c[0], c[1], c[2], 255)
    tex("hps_door", 32, door)
    t_disc("hps_stud", "FFF4C0", "C8901A", size=16, cut=False)
    t_glow("hps_halo", "FFFFFF", "FFE88A", alpha_edge=200, power=1.2)
    t_runes("hps_halo_rune", "FFE88A", "FFFFFF", size=16, seed=116, glow_ink=True, width=0.12, strokes=3)
    t_metal("hps_chain", "FFF1C0", seed=117, light=0.5, dark=0.3)
    t_runes("hps_seal", "F0D890", "C0182A", size=32, seed=118, border="C0182A", strokes=6, width=0.09)


def hps_sword():
    """The punishing sword: a sun-disc pommel, a long banded grip, a winged double guard, a broad white-gold blade with
    three inlaid rune plates on each face, violet edge glow, a hot central ridge and a stepped point."""
    m = Model("hps_sword")
    m.faced([6.4, 1.2, 7.6], [9.6, 4.4, 8.4], "hps_sun", "hps_gold", glow=15)
    m.faced([6.45, 1.25, 7.62], [9.55, 4.35, 8.38], "hps_sun", "hps_gold", rot=("z", 45.0, [8.0, 2.8, 8.0]), glow=15)
    m.box([7.35, 4.4, 7.35], [8.65, 8.0, 8.65], "hps_grip")
    for y in (5.2, 6.8):
        m.box([7.15, y, 7.15], [8.85, y + 0.4, 8.85], "hps_gold")
    # guard
    m.box([5.0, 8.0, 6.8], [11.0, 10.0, 9.2], "hps_gold")
    m.box([-0.5, 9.6, 7.2], [5.0, 10.6, 8.8], "hps_gold", rot=("z", -22.5, [5.0, 10.1, 8.0]))
    m.box([11.0, 9.6, 7.2], [16.5, 10.6, 8.8], "hps_gold", rot=("z", 22.5, [11.0, 10.1, 8.0]))
    m.box([1.0, 8.0, 7.4], [5.0, 8.9, 8.6], "hps_gold", rot=("z", 22.5, [5.0, 8.45, 8.0]))
    m.box([11.0, 8.0, 7.4], [15.0, 8.9, 8.6], "hps_gold", rot=("z", -22.5, [11.0, 8.45, 8.0]))
    m.cube((8.0, 9.0, 6.7), (1.4, 1.4, 0.3), "hps_edge", rot=("z", 45.0, [8.0, 9.0, 6.7]), glow=15)
    # blade
    m.box([5.5, 10.0, 7.5], [10.5, 28.0, 8.5], "hps_blade")
    m.box([6.3, 28.0, 7.6], [9.7, 30.0, 8.4], "hps_blade")
    m.box([7.1, 30.0, 7.7], [8.9, 31.2, 8.3], "hps_blade")
    m.cube((8.0, 31.1, 8.0), (1.2, 1.2, 0.5), "hps_edge", rot=("z", 45.0, [8.0, 31.1, 8.0]), glow=15)
    m.box([5.1, 10.4, 7.8], [5.5, 28.0, 8.2], "hps_edge", glow=15)
    m.box([10.5, 10.4, 7.8], [10.9, 28.0, 8.2], "hps_edge", glow=15)
    m.box([5.9, 28.0, 7.8], [6.3, 29.8, 8.2], "hps_edge", glow=15)
    m.box([9.7, 28.0, 7.8], [10.1, 29.8, 8.2], "hps_edge", glow=15)
    m.box([7.85, 10.5, 7.4], [8.15, 29.5, 8.6], "hps_ridge", glow=15)
    for y, rune in ((12.0, "hps_rune"), (17.0, "hps_rune_b"), (22.0, "hps_rune_c")):
        m.el([6.6, y, 7.3], [9.4, y + 3.2, 7.5], {"north": (rune, None), "east": ("hps_gold", None), "west": ("hps_gold", None),
                                                  "up": ("hps_gold", None), "down": ("hps_gold", None)}, glow=15)
        m.el([6.6, y, 8.5], [9.4, y + 3.2, 8.7], {"south": (rune, None), "east": ("hps_gold", None), "west": ("hps_gold", None),
                                                  "up": ("hps_gold", None), "down": ("hps_gold", None)}, glow=15)
    return m


def hps_gate():
    """Nantianmen: two red pillars on gold bases standing on clouds, cloud capitals, a lintel and a second beam, a
    double-eaved jade roof with a gold ridge and upturned corners, a name plaque and gold door frames (the doors are
    separate props). Base at the anchor (the doorway's sill)."""
    m = Model("hps_gate")
    for x0, x1 in ((0.5, 3.0), (13.0, 15.5)):
        m.box([x0, 8.0, 6.8], [x1, 26.0, 9.2], "hps_pillar")
        m.box([x0 - 0.8, 8.0, 6.2], [x1 + 0.8, 9.4, 9.8], "hps_gold")
        m.box([x0 - 1.5, 25.0, 6.2], [x1 + 1.5, 27.0, 9.8], "hps_cloud")
        m.cube(((x0 + x1) / 2, 27.4, 8.0), (2.6, 1.2, 2.6), "hps_cloud", rot=("y", 45.0, [(x0 + x1) / 2, 27.4, 8.0]))
    m.box([-1.5, 26.5, 6.6], [17.5, 28.0, 9.4], "hps_beam")
    m.box([-0.5, 24.0, 7.0], [16.5, 25.0, 9.0], "hps_beam")
    # roof: lower eave, upper block, upper eave, ridge, ridge ends, upturned corners
    m.box([-4.0, 28.0, 4.6], [20.0, 28.7, 11.4], "hps_roof")
    m.box([0.0, 28.7, 6.4], [16.0, 30.0, 9.6], "hps_pillar")
    m.box([-2.0, 30.0, 5.4], [18.0, 30.6, 10.6], "hps_roof")
    m.box([2.0, 30.6, 7.4], [14.0, 31.6, 8.6], "hps_gold", glow=15)
    m.cube((1.4, 31.2, 8.0), (1.2, 1.4, 1.2), "hps_gold", rot=("z", -22.5, [1.4, 31.2, 8.0]), glow=15)
    m.cube((14.6, 31.2, 8.0), (1.2, 1.4, 1.2), "hps_gold", rot=("z", 22.5, [14.6, 31.2, 8.0]), glow=15)
    for (x, z) in ((-3.6, 5.0), (19.6, 5.0), (-3.6, 11.0), (19.6, 11.0)):
        m.cube((x, 28.9, z), (1.0, 1.2, 1.0), "hps_gold", rot=("y", 45.0, [x, 28.9, z]))
    # plaque on both faces
    m.faced([5.5, 21.0, 6.4], [10.5, 23.8, 6.8], "hps_plaque", "hps_gold", glow=15)
    m.faced([5.5, 21.0, 9.2], [10.5, 23.8, 9.6], "hps_plaque", "hps_gold", glow=15)
    # door frames
    m.box([3.0, 8.0, 6.6], [3.5, 24.0, 9.4], "hps_gold")
    m.box([12.5, 8.0, 6.6], [13.0, 24.0, 9.4], "hps_gold")
    # clouds under the pillars
    m.box([-3.0, 6.8, 4.0], [5.5, 8.4, 12.0], "hps_cloud")
    m.box([10.5, 6.8, 4.0], [19.0, 8.4, 12.0], "hps_cloud")
    m.cube((-1.5, 6.6, 8.0), (3.6, 2.0, 3.6), "hps_cloud", rot=("y", 45.0, [-1.5, 6.6, 8.0]))
    m.cube((17.5, 6.6, 8.0), (3.6, 2.0, 3.6), "hps_cloud", rot=("y", 45.0, [17.5, 6.6, 8.0]))
    return m


def hps_door(mid, sign):
    """One gate door hinged at the anchor: a red panel with gold frame bars, 3 x 4 gold studs and a ring knocker on the
    front; it extends towards +x (sign 1) or -x (sign -1)."""
    m = Model(mid)

    def x(a):
        return 8.0 + sign * a
    x0, x1 = sorted((x(0.0), x(5.0)))
    m.faced([x0, 8.0, 7.6], [x1, 24.0, 8.4], "hps_door", "hps_gold")
    for i in range(3):
        for j in range(4):
            cx = x(1.2 + i * 1.3)
            cy = 10.4 + j * 3.6
            m.box([cx - 0.3, cy - 0.3, 7.35], [cx + 0.3, cy + 0.3, 7.6], "hps_stud", glow=15)
    k = x(4.2)
    ring_xy(m, (k, 15.2, 7.4), 0.6, 0.6, 0.25, 0.2, "hps_gold", segs=8)
    return m


def hps_halo():
    """A standing ring of 16 segments (eight rune plates) with four bright studs at the cardinal points."""
    m = Model("hps_halo")
    c = (8.0, 8.0, 8.0)
    for i in range(16):
        theta = 22.5 * i
        px, py = c[0] + 7.0 * math.cos(math.radians(theta)), c[1] + 7.0 * math.sin(math.radians(theta))
        a, swapped = norm_angle(theta + 90.0)
        rune = i % 2 == 0
        length, width, depth = (2.9, 1.4 if rune else 0.6, 0.4 if rune else 0.3)
        size = (length, width, depth) if not swapped else (width, length, depth)
        faces = {s: ("hps_halo", None) for s in SIDES}
        if rune:
            faces["north"] = ("hps_halo_rune", None)
            faces["south"] = ("hps_halo_rune", None)
        f = [px - size[0] / 2, py - size[1] / 2, 8.0 - size[2] / 2]
        t = [px + size[0] / 2, py + size[1] / 2, 8.0 + size[2] / 2]
        m.el(f, t, faces, rot=("z", a, [px, py, 8.0]), glow=15)
    for theta in (0, 90, 180, 270):
        px, py = 8.0 + 8.4 * math.cos(math.radians(theta)), 8.0 + 8.4 * math.sin(math.radians(theta))
        m.cube((px, py, 8.0), (1.1, 1.1, 0.6), "hps_halo", rot=("z", 45.0, [px, py, 8.0]), glow=15)
    return m


def hps_chain():
    """A chain of law: a talisman seal weight at the anchor (its base) and four links above it, each turned a quarter
    from the last."""
    m = Model("hps_chain")
    m.faced([6.0, 8.0, 6.0], [10.0, 11.6, 10.0], "hps_seal", "hps_gold", glow=15)
    m.box([7.2, 11.6, 7.2], [8.8, 12.2, 8.8], "hps_chain")
    y = 12.0
    for i in range(4):
        y0, y1 = y, y + 4.2
        if i % 2 == 0:
            m.box([6.8, y0, 7.75], [7.3, y1, 8.25], "hps_chain")
            m.box([8.7, y0, 7.75], [9.2, y1, 8.25], "hps_chain")
            m.box([7.3, y0, 7.75], [8.7, y0 + 0.5, 8.25], "hps_chain")
            m.box([7.3, y1 - 0.5, 7.75], [8.7, y1, 8.25], "hps_chain")
        else:
            m.box([7.75, y0, 6.8], [8.25, y1, 7.3], "hps_chain")
            m.box([7.75, y0, 8.7], [8.25, y1, 9.2], "hps_chain")
            m.box([7.75, y0, 7.3], [8.25, y0 + 0.5, 8.7], "hps_chain")
            m.box([7.75, y1 - 0.5, 7.3], [8.25, y1, 8.7], "hps_chain")
        y += 3.4
    return m


# ================================================================================================ Lightning: tribulation array

LUOSHU = ((4, 9, 2), (3, 5, 7), (8, 1, 6))


def build_tte_textures():
    for lit in (False, True):
        sfx = "_lit" if lit else ""
        n = Noise(200 + lit)

        def hide(u, v, lit=lit, n=n):
            d = math.hypot(u - 0.5, v - 0.5) * 2.0
            if lit:
                c = mix(rgb("FFFFFF"), rgb("B57CFF"), d ** 1.2)
            else:
                c = mix(rgb("D8402A"), rgb("8A1810"), d ** 1.5)
            c = mix(c, WHITE, 0.06 * (n.fbm(u * 5, v * 5) - 0.5))
            if 0.55 < d < 0.62:
                c = mix(c, rgb("FFD54A"), 0.7)
            if d < 0.18:
                c = mix(c, rgb("FFD54A") if not lit else WHITE, 0.6)
            return (c[0], c[1], c[2], 255)
        tex("tte_hide" + sfx, 32, hide)

        def barrel(u, v, lit=lit):
            c = mix(rgb("C02A20"), rgb("7A1008"), abs(v - 0.5) * 1.6)
            if lit:
                c = mix(c, rgb("B57CFF"), 0.35)
            if v < 0.12 or v > 0.88:
                c = rgb("FFD54A")
            if abs(v - 0.5) < 0.05:
                c = mix(rgb("FFD54A"), WHITE, 0.3) if not lit else rgb("E8D8FF")
            # carved cloud motif
            k = 0.5 + 0.5 * math.sin(u * math.pi * 6) * math.cos(v * math.pi * 3)
            if 0.2 < v < 0.8 and k > 0.92:
                c = mix(c, rgb("FFD54A"), 0.5)
            return (c[0], c[1], c[2], 255)
        tex("tte_barrel" + sfx, 32, barrel)
    t_disc("tte_stud", "FFF4C0", "C8901A", size=16, cut=False)
    t_cloud("tte_foot", "FFE88A", "8A6020", seed=203)
    # the Luo Shu grid: dark bronze, violet lines and the number pips of each cell (yaw 180: texture = world)
    n = Noise(210)

    def grid(u, v):
        c = mix(rgb("3A2A50"), rgb("1E1430"), n.fbm(u * 5, v * 5))
        a = 215
        col, row = min(2, int(u * 3)), min(2, int(v * 3))
        num = LUOSHU[row][col]
        cu, cv = (u * 3) % 1.0, (v * 3) % 1.0
        # pips: odd (yang) numbers white, even (yin) dark violet, on a small circle
        pts = [(0.5, 0.5)] if num == 1 else [(0.5 + 0.27 * math.cos(math.tau * i / num - math.pi / 2), 0.5 + 0.27 * math.sin(math.tau * i / num - math.pi / 2)) for i in range(num)]
        if num == 5:
            pts = [(0.5, 0.5), (0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)]
        for (px, py) in pts:
            d = math.hypot(cu - px, cv - py)
            if d < 0.075:
                c = rgb("FFFFFF") if num % 2 else rgb("B57CFF")
                a = 255
            elif d < 0.11:
                c = mix(c, rgb("B57CFF"), 0.5)
        # the magic-square path ring in each cell
        dr = abs(math.hypot(cu - 0.5, cv - 0.5) - 0.42)
        if dr < 0.015:
            c = mix(c, rgb("8A5AE8"), 0.6)
        e = min(u, v, 1.0 - u, 1.0 - v)
        if e < 0.02:
            c = rgb("B57CFF")
        return (c[0], c[1], c[2], a)
    tex("tte_grid_plate", 64, grid)
    t_glow("tte_grid_bar", "FFFFFF", "B57CFF", alpha_edge=230, power=1.3)
    t_glow("tte_grid_bar_u", "FFFFFF", "B57CFF", alpha_edge=230, power=1.3, across="v")
    t_cloud("tte_grid_cloud", "8A7AA8", "3A2A50", seed=211, under="B57CFF")
    t_disc("tte_grid_centre", "FFFFFF", "B57CFF", rays=8, ray_colour="FFFFFF", cut=False)
    t_glow("tte_bolt_core", "FFFFFF", "F0E8FF", alpha_edge=255, power=1.0)
    t_glow("tte_bolt_halo", "E8D8FF", "B57CFF", alpha_core=170, alpha_edge=30, power=1.0)
    t_cloud("tte_rim_cloud", "8A86A0", "4A4660", seed=220, under="B57CFF", alpha=245)
    t_glow("tte_rim_glow", "E8D8FF", "B57CFF", alpha_core=150, alpha_edge=0, power=1.0, across="v")


def tte_drum(mid, lit):
    """A palace war drum standing on three cloud feet, its hide facing up: an octagonal lacquered barrel with a bulge
    band, gold rims and eight gold studs round the hide."""
    m = Model(mid)
    sfx = "_lit" if lit else ""
    c = (8.0, 8.0, 8.0)
    for i in range(3):
        a = math.radians(90.0 + 120.0 * i)
        p = (8.0 + 3.8 * math.cos(a), 9.2, 8.0 + 3.8 * math.sin(a))
        m.cube(p, (1.8, 2.4, 1.8), "tte_foot")
        q = (8.0 + 4.9 * math.cos(a), 8.6, 8.0 + 4.9 * math.sin(a))
        m.cube(q, (1.4, 1.2, 1.4), "tte_foot", rot=("y", 45.0, list(q)))
    oct_prism(m, c, 5.4, 10.2, 10.8, "tte_stud", "tte_stud")
    oct_prism(m, c, 5.0, 10.8, 16.2, "tte_barrel" + sfx, "tte_barrel" + sfx, glow=15 if lit else 0)
    oct_prism(m, c, 5.3, 12.9, 14.1, "tte_barrel" + sfx, "tte_barrel" + sfx, glow=15 if lit else 0)
    oct_prism(m, c, 5.4, 16.2, 16.6, "tte_stud", "tte_stud")
    oct_prism(m, c, 4.7, 16.6, 16.75, "tte_hide" + sfx, "tte_hide" + sfx, glow=15 if lit else 0)
    for i in range(8):
        a = math.radians(22.5 + 45.0 * i)
        p = (8.0 + 5.15 * math.cos(a), 16.8, 8.0 + 5.15 * math.sin(a))
        m.cube(p, (0.6, 0.4, 0.6), "tte_stud", glow=15)
    return m


def tte_grid():
    """The Luo Shu grid laid flat (16 x 16 px): the pip plate, violet frame and cell bars, cloud scrolls round the rim
    and the raised centre (palace 5) disc."""
    m = Model("tte_grid")
    m.el([0.0, 8.0, 0.0], [16.0, 8.08, 16.0], {"up": ("tte_grid_plate", None), "down": ("tte_grid_plate", (0, 16, 16, 0))})
    third = 16.0 / 3.0
    y, h = 8.08, 0.35
    for z in (0.0, 16.0):
        m.el([-0.3, y, z - 0.3], [16.3, y + h, z + 0.3], {s: ("tte_grid_bar", None) for s in ("up", "north", "south")}, glow=15)
    for x in (0.0, 16.0):
        m.el([x - 0.3, y + 0.01, 0.3], [x + 0.3, y + h + 0.01, 15.7], {"up": ("tte_grid_bar_u", None), "east": ("tte_grid_bar", None), "west": ("tte_grid_bar", None)}, glow=15)
    for k in (1, 2):
        z = k * third
        m.el([0.3, y, z - 0.15], [15.7, y + 0.22, z + 0.15], {s: ("tte_grid_bar", None) for s in ("up", "north", "south")}, glow=15)
        x = k * third
        m.el([x - 0.15, y + 0.02, 0.3], [x + 0.15, y + 0.24, 15.7], {"up": ("tte_grid_bar_u", None), "east": ("tte_grid_bar", None), "west": ("tte_grid_bar", None)}, glow=15)
    for (x, z) in ((0.0, 0.0), (16.0, 0.0), (0.0, 16.0), (16.0, 16.0)):
        m.cube((x, 8.6, z), (1.6, 1.2, 1.6), "tte_grid_cloud", rot=("y", 45.0, [x, 8.6, z]))
    for (x, z, sx, sz) in ((8.0, -0.8, 4.0, 1.2), (8.0, 16.8, 4.0, 1.2), (-0.8, 8.0, 1.2, 4.0), (16.8, 8.0, 1.2, 4.0)):
        m.cube((x, 8.45, z), (sx, 0.9, sz), "tte_grid_cloud")
    for (x, z) in ((4.0, -0.9), (12.0, -0.9), (4.0, 16.9), (12.0, 16.9), (-0.9, 4.0), (-0.9, 12.0), (16.9, 4.0), (16.9, 12.0)):
        m.cube((x, 8.35, z), (1.0, 0.7, 1.0), "tte_grid_cloud", rot=("y", 45.0, [x, 8.35, z]))
    oct_prism(m, (8.0, 8.0, 8.0), 1.2, 8.08, 8.5, "tte_grid_bar", "tte_grid_centre", glow=15)
    return m


def tte_bolt():
    """A jagged bolt from the anchor (its strike end) up to y 32: five zigzag segments turned +-22.5 degrees, a white
    core inside a violet halo shell, glowing joints and two short forks."""
    m = Model("tte_bolt")
    x, y = 9.0, 8.0
    L = 5.0
    joints = []
    for i in range(5):
        a = 22.5 if i % 2 == 0 else -22.5
        m.box([x - 0.3, y, 7.7], [x + 0.3, y + L, 8.3], "tte_bolt_core", rot=("z", a, [x, y, 8.0]), glow=15)
        m.box([x - 0.8, y - 0.2, 7.2], [x + 0.8, y + L + 0.2, 8.8], "tte_bolt_halo", rot=("z", a, [x, y, 8.0]), glow=15)
        nx = x - L * math.sin(math.radians(a))
        ny = y + L * math.cos(math.radians(a))
        joints.append((nx, ny))
        x, y = nx, ny
    for (jx, jy) in joints[:4]:
        m.cube((jx, jy, 8.0), (0.9, 0.9, 0.9), "tte_bolt_core", rot=("y", 45.0, [jx, jy, 8.0]), glow=15)
    for idx, a in ((1, 45.0), (3, -45.0)):
        jx, jy = joints[idx]
        m.box([jx - 0.2, jy - 3.0, 7.8], [jx + 0.2, jy, 8.2], "tte_bolt_core", rot=("z", a, [jx, jy, 8.0]), glow=15)
        m.box([jx - 0.6, jy - 3.2, 7.4], [jx + 0.6, jy, 8.6], "tte_bolt_halo", rot=("z", a, [jx, jy, 8.0]), glow=15)
    return m


def tte_vortex_rim():
    """One eighth of the tribulation whirlpool: cloud lumps on a 20 px arc running from north (0 deg) to 45 deg
    clockwise seen from above, with violet underglow plates and puffs on top (eight turned copies make the ring)."""
    m = Model("tte_vortex_rim")

    def at(theta, r):
        return 8.0 + r * math.sin(math.radians(theta)), 8.0 - r * math.cos(math.radians(theta))
    for theta, b, size, dy in ((0.0, 0.0, (6.4, 3.4, 4.8), 0.0), (22.5, -22.5, (6.4, 3.0, 4.8), 0.4)):
        x, z = at(theta, 20.0)
        m.cube((x, 8.0 + dy, z), size, "tte_rim_cloud", rot=("y", b, [x, 8.0 + dy, z]))
        m.el([x - 2.8, 6.0 + dy, z - 2.0], [x + 2.8, 6.2 + dy, z + 2.0], {"down": ("tte_rim_glow", None), "up": ("tte_rim_glow", None)},
             rot=("y", b, [x, 6.1 + dy, z]), glow=15)
        p = (x, 10.2 + dy, z)
        m.cube(p, (2.6, 1.6, 2.6), "tte_rim_cloud", rot=("y", 45.0, list(p)))
    for theta, r, size, dy in ((11.25, 18.6, (4.4, 2.6, 3.6), -0.6), (33.75, 21.2, (4.4, 2.4, 3.6), 0.2)):
        x, z = at(theta, r)
        b = 0.0 if theta < 20 else -22.5
        m.cube((x, 8.0 + dy, z), size, "tte_rim_cloud", rot=("y", b, [x, 8.0 + dy, z]))
    # a stray wisp below the arc, lit from beneath
    x, z = at(16.0, 23.0)
    m.cube((x, 6.6, z), (3.0, 1.0, 2.0), "tte_rim_glow", glow=15)
    return m


# ================================================================================================ Lightning: nine heavens

def build_nhtv_textures():
    t_metal("nhtv_band", "FFD54A", seed=300, light=0.45)
    t_metal("nhtv_band_lit", "D8B8FF", seed=301, light=0.6, dark=0.2, tint="FFFFFF")
    t_cloud("nhtv_cloud", "FFFFFF", "C8CCD8", seed=302)
    t_cloud("nhtv_cloud_lit", "FFFFFF", "C8B0F0", seed=303, under="B57CFF")
    t_disc("nhtv_stud", "FFF4C0", "C8901A", size=16, cut=False, rim="FFFFFF")
    t_disc("nhtv_stud_lit", "FFFFFF", "B57CFF", size=16, cut=False, rim="FFFFFF")
    t_scales("nhtv_qilin_body", "1A1A22", "3A3A50", seed=310, glow="7A4AC8")
    t_flame("nhtv_qilin_flame", "FFFFFF", "B57CFF", "5A2AA8", seed=311)
    t_glow("nhtv_qilin_eye", "FFFFFF", "FFD54A", alpha_edge=255)
    t_metal("nhtv_qilin_horn", "8A6A2A", seed=312, light=0.5, tint="FFD54A")
    t_metal("nhtv_qilin_hoof", "2A2A33", seed=313, light=0.2)
    t_bands("nhtv_whip", "FFD54A", "C8901A", bands=3, seed=320)
    t_metal("nhtv_whip_knuckle", "FFE88A", seed=321, light=0.5)
    t_glow("nhtv_whip_glint", "FFFFFF", "FFF4C0", alpha_edge=200)
    t_metal("nhtv_armor", "5A5A70", seed=330, light=0.3, grain=0.12)
    t_metal("nhtv_trim", "FFD54A", seed=331, light=0.4)
    t_metal("nhtv_mask", "C7A2FF", seed=332, light=0.4, dark=0.4)
    t_feather("nhtv_wing", "5A5A70", "C7A2FF", "2A2A3A", seed=333)
    t_glow("nhtv_glow", "FFFFFF", "C7A2FF", alpha_edge=255)


def nhtv_ring(mid, lit):
    """One of the Nine Heavens: a flat gold band (16 segments, r 14 px), eight cloud lumps riding it and nine rune
    studs at 40 degree steps."""
    m = Model(mid)
    sfx = "_lit" if lit else ""
    c = (8.0, 8.0, 8.0)
    ring_xz(m, c, 14.0, 5.8, 1.6, 0.6, "nhtv_band" + sfx, segs=16, glow=15 if lit else 0)
    for i in range(8):
        a = math.radians(22.5 + 45.0 * i)
        p = (8.0 + 14.0 * math.cos(a), 8.9 + 0.25 * (i % 2), 8.0 + 14.0 * math.sin(a))
        m.cube(p, (3.6, 2.2, 3.6), "nhtv_cloud" + sfx, rot=("y", 45.0, list(p)))
    for i in range(9):
        a = math.radians(40.0 * i)
        p = (8.0 + 14.0 * math.cos(a), 8.55, 8.0 + 14.0 * math.sin(a))
        m.cube(p, (1.1, 0.7, 1.1), "nhtv_stud" + sfx, rot=("y", 45.0, list(p)), glow=15)
    return m


def nhtv_qilin():
    """A rearing black qilin, its hind feet at the anchor, facing north: scaled body, a chest plate, four legs with
    hooves and violet flame tufts, an arched neck, a maned head with a snout, a beard flame, gold eyes and branching
    antlers, flames along the spine. The tail is a separate part."""
    m = Model("nhtv_qilin")
    body, flame = "nhtv_qilin_body", "nhtv_qilin_flame"
    for x0 in (5.4, 9.0):
        m.box([x0, 8.0, 7.2], [x0 + 1.6, 13.5, 9.2], body)
        m.box([x0 - 0.1, 8.0, 7.0], [x0 + 1.7, 8.8, 9.4], "nhtv_qilin_hoof")
        m.box([x0, 9.0, 9.2], [x0 + 1.6, 11.0, 10.6], flame, rot=("x", 22.5, [x0 + 0.8, 9.0, 9.2]), glow=15, sides=("east", "west", "up"))
    m.box([5.2, 12.5, -3.5], [10.8, 17.5, 9.5], body)
    m.box([5.6, 12.0, -5.0], [10.4, 17.0, -3.5], body)
    for x0 in (5.6, 9.0):
        m.box([x0, 8.0, -4.4], [x0 + 1.4, 13.0, -2.6], body)
        m.box([x0 - 0.1, 8.0, -4.6], [x0 + 1.5, 8.8, -2.4], "nhtv_qilin_hoof")
        m.box([x0, 9.0, -2.6], [x0 + 1.4, 11.0, -1.2], flame, rot=("x", 22.5, [x0 + 0.7, 9.0, -2.6]), glow=15, sides=("east", "west", "up"))
    m.box([6.4, 16.0, -6.5], [9.6, 22.0, -3.0], body, rot=("x", -22.5, [8.0, 16.0, -3.0]))
    m.box([6.2, 21.0, -11.5], [9.8, 24.5, -6.0], body)
    m.box([6.8, 21.2, -14.0], [9.2, 23.2, -11.5], body)
    m.box([7.2, 19.6, -13.0], [8.8, 21.2, -10.0], flame, glow=15)
    m.box([6.0, 23.0, -10.6], [6.3, 23.8, -9.6], "nhtv_qilin_eye", glow=15)
    m.box([9.7, 23.0, -10.6], [10.0, 23.8, -9.6], "nhtv_qilin_eye", glow=15)
    for sx, ang in ((6.8, 22.5), (9.2, -22.5)):
        m.box([sx - 0.4, 24.5, -8.5], [sx + 0.4, 29.0, -7.7], "nhtv_qilin_horn", rot=("z", ang, [sx, 24.5, -8.1]), glow=15)
        m.box([sx - 0.3, 26.0, -8.4], [sx + 0.3, 28.0, -7.8], "nhtv_qilin_horn", rot=("x", -45.0, [sx, 26.0, -8.1]), glow=15)
    for i, (y0, z0) in enumerate(((17.5, -2.5), (19.5, -4.2), (21.5, -5.8))):
        m.box([7.0, y0, z0], [9.0, y0 + 3.0, z0 + 2.2], flame, rot=("x", 22.5, [8.0, y0, z0 + 1.1]), glow=15)
    for z0 in (0.5, 5.0):
        m.box([7.2, 17.5, z0], [8.8, 19.6, z0 + 2.4], flame, rot=("x", 22.5, [8.0, 17.5, z0 + 1.2]), glow=15)
    return m


def nhtv_qilin_tail():
    """The qilin's tail from its root at the anchor: a scaled stem rising backwards and a violet flame plume."""
    m = Model("nhtv_qilin_tail")
    m.box([7.2, 7.4, 8.0], [8.8, 8.6, 13.0], "nhtv_qilin_body", rot=("x", -22.5, [8.0, 8.0, 8.0]))
    m.box([6.6, 9.0, 12.0], [9.4, 12.5, 16.0], "nhtv_qilin_flame", rot=("x", -22.5, [8.0, 9.0, 12.0]), glow=15)
    m.box([7.2, 10.2, 15.0], [8.8, 12.4, 18.0], "nhtv_qilin_flame", rot=("x", -45.0, [8.0, 10.2, 15.0]), glow=15)
    return m


def nhtv_whip():
    """A gold rod whip: banded handle, a knob guard, six rod segments with knuckle rings, a white glint and a cap."""
    m = Model("nhtv_whip")
    m.box([7.4, 4.0, 7.4], [8.6, 8.6, 8.6], "nhtv_whip")
    m.box([7.0, 8.6, 7.0], [9.0, 9.4, 9.0], "nhtv_whip_knuckle", glow=15)
    y = 9.4
    for i in range(6):
        m.box([7.55, y, 7.55], [8.45, y + 3.4, 8.45], "nhtv_whip")
        m.box([7.2, y + 3.0, 7.2], [8.8, y + 3.7, 8.8], "nhtv_whip_knuckle", glow=15)
        y += 3.6
    m.box([7.9, 9.6, 7.35], [8.1, 30.8, 7.45], "nhtv_whip_glint", glow=15)
    m.box([7.4, 31.0, 7.4], [8.6, 32.0, 8.6], "nhtv_whip_knuckle", glow=15)
    return m


def nhtv_deity():
    """A thunder deity standing on the anchor, facing north: armoured legs with gold boots, a skirt of plates, the torso
    with a violet chest plate and a gold belt, pauldrons, one arm down and the right arm raised (the hammer is a part),
    the bird-beaked violet mask with glowing eyes and a crest, small wings."""
    m = Model("nhtv_deity")
    for x0 in (6.0, 8.4):
        m.box([x0, 8.0, 7.2], [x0 + 1.6, 13.0, 8.8], "nhtv_armor")
        m.box([x0 - 0.1, 8.0, 7.0], [x0 + 1.7, 9.0, 9.0], "nhtv_trim")
    m.box([5.4, 12.5, 6.8], [10.6, 15.0, 9.2], "nhtv_armor")
    m.box([5.6, 15.0, 6.9], [10.4, 20.5, 9.1], "nhtv_armor")
    m.el([6.2, 16.5, 6.7], [9.8, 19.5, 6.9], {"north": ("nhtv_mask", None)}, glow=15)
    m.box([5.5, 14.6, 6.7], [10.5, 15.4, 9.3], "nhtv_trim")
    m.box([4.0, 19.0, 6.8], [6.0, 21.0, 9.2], "nhtv_trim", rot=("z", 22.5, [6.0, 20.0, 8.0]))
    m.box([10.0, 19.0, 6.8], [12.0, 21.0, 9.2], "nhtv_trim", rot=("z", -22.5, [10.0, 20.0, 8.0]))
    m.box([4.4, 14.5, 7.3], [5.8, 19.5, 8.7], "nhtv_armor")
    m.box([10.2, 19.0, 7.3], [11.6, 24.0, 8.7], "nhtv_armor")
    m.box([6.6, 20.5, 6.8], [9.4, 23.5, 9.2], "nhtv_mask")
    m.box([7.4, 21.0, 4.6], [8.6, 22.4, 6.8], "nhtv_trim", rot=("x", 22.5, [8.0, 21.7, 6.8]))
    m.box([7.6, 23.5, 6.6], [8.4, 25.0, 9.4], "nhtv_trim", glow=15)
    m.el([6.9, 22.4, 6.7], [7.7, 23.0, 6.8], {"north": ("nhtv_glow", None)}, glow=15)
    m.el([8.3, 22.4, 6.7], [9.1, 23.0, 6.8], {"north": ("nhtv_glow", None)}, glow=15)
    m.box([1.5, 17.5, 9.2], [7.0, 21.5, 9.6], "nhtv_wing", rot=("y", 22.5, [7.0, 19.5, 9.4]))
    m.box([9.0, 17.5, 9.2], [14.5, 21.5, 9.6], "nhtv_wing", rot=("y", -22.5, [9.0, 19.5, 9.4]))
    m.box([2.0, 21.0, 9.3], [5.0, 22.0, 9.5], "nhtv_glow", rot=("y", 22.5, [7.0, 19.5, 9.4]), glow=15)
    m.box([11.0, 21.0, 9.3], [14.0, 22.0, 9.5], "nhtv_glow", rot=("y", -22.5, [9.0, 19.5, 9.4]), glow=15)
    return m


def nhtv_deity_hammer():
    """The deity's thunder hammer, its grip at the anchor: a handle, a gold head with violet faces and a spike."""
    m = Model("nhtv_deity_hammer")
    m.box([7.6, 4.0, 7.6], [8.4, 12.0, 8.4], "nhtv_armor")
    m.box([5.5, 12.0, 6.5], [10.5, 15.0, 9.5], "nhtv_trim")
    m.el([5.3, 12.2, 6.7], [5.5, 14.8, 9.3], {"west": ("nhtv_mask", None)}, glow=15)
    m.el([10.5, 12.2, 6.7], [10.7, 14.8, 9.3], {"east": ("nhtv_mask", None)}, glow=15)
    m.cube((8.0, 15.8, 8.0), (1.0, 1.6, 1.0), "nhtv_glow", rot=("y", 45.0, [8.0, 15.8, 8.0]), glow=15)
    return m


# ================================================================================================ Ice: phoenix

def build_ipd_textures():
    t_ice("ipd_ice", "DFF8FF", "7AB0E0", seed=400, alpha=230)
    t_feather("ipd_feather", "BFF6FF", "FFFFFF", "5A8AD8", seed=401)
    t_feather("ipd_feather_u", "BFF6FF", "FFFFFF", "5A8AD8", seed=402, along="u")
    t_feather("ipd_tail_feather", "BFF6FF", "FFFFFF", "5A8AD8", seed=403, eye="9FF3FF")
    t_flat("ipd_white", "FFFFFF", seed=404, colour2="DFF6FF")
    t_flat("ipd_shadow", "5A8AD8", seed=405, colour2="3A6AC8")
    t_glow("ipd_glint", "FFFFFF", "BFF6FF", alpha_edge=255)
    t_glow("ipd_quill_tip", "FFFFFF", "9FF3FF", alpha_edge=230)
    t_feather("ipd_quill_vane", "DFF6FF", "FFFFFF", "7AB0E0", seed=406, along="u")
    t_flat("ipd_quill_shaft", "FFFFFF", seed=407)
    n = Noise(410)

    def hexring(u, v):
        # a six-fold frost ring: a ring band, six arms with side branches, frost noise; transparent elsewhere
        x, y = (u - 0.5) * 2.0, (v - 0.5) * 2.0
        r = math.hypot(x, y)
        ang = math.atan2(y, x)
        a = 0.0
        c = rgb("DFF6FF")
        band = abs(r - 0.78)
        if band < 0.05:
            a = 230 * (1.0 - band / 0.05)
        sector = (ang + math.pi / 6.0) % (math.pi / 3.0) - math.pi / 6.0
        arm = abs(math.sin(sector) * r)
        if arm < 0.035 and r < 0.95:
            a = max(a, 240)
        along, across = r * math.cos(sector), abs(r * math.sin(sector))
        for b in (0.32, 0.52):
            if b < along < b + 0.2 and abs(across - (along - b) * 0.9) < 0.03:
                a = max(a, 220)
        if r < 0.12:
            a = max(a, 200)
            c = WHITE
        a *= 0.75 + 0.25 * n.fbm(u * 8, v * 8)
        if r > 0.98:
            a = 0
        return (c[0], c[1], c[2], a)
    tex("ipd_hex_plate", 64, hexring)
    t_ice("ipd_crystal", "FFFFFF", "9FF3FF", seed=411, alpha=235, size=16)


def ipd_phoenix():
    """The ice phoenix's body, centred on the anchor, facing north: body, breast, back ridge, rump, a three-piece arched
    neck, head, beak with a frost glint, glowing eyes, a crest of five quills, breast feather plates, a frost spine line
    and tucked legs. Wings and tail are separate props."""
    m = Model("ipd_phoenix")
    m.box([6.0, 6.0, 3.0], [10.0, 10.0, 13.0], "ipd_ice")
    m.box([6.4, 5.0, 1.5], [9.6, 8.5, 6.0], "ipd_white")
    m.box([6.8, 9.6, 4.0], [9.2, 10.6, 12.0], "ipd_shadow")
    m.box([6.6, 6.6, 12.5], [9.4, 9.4, 15.0], "ipd_ice")
    m.box([6.8, 8.5, 0.5], [9.2, 12.0, 3.5], "ipd_ice", rot=("x", -22.5, [8.0, 9.0, 3.0]))
    m.box([7.0, 11.5, -1.5], [9.0, 15.0, 1.5], "ipd_ice")
    m.box([7.0, 14.0, -3.5], [9.0, 16.0, 0.0], "ipd_white", rot=("x", 22.5, [8.0, 14.0, -1.5]))
    m.box([6.6, 15.0, -5.5], [9.4, 18.0, -1.0], "ipd_ice")
    m.box([7.4, 15.4, -8.5], [8.6, 16.6, -5.5], "ipd_white")
    m.box([7.7, 15.2, -9.5], [8.3, 15.8, -8.5], "ipd_glint", glow=15)
    m.box([6.45, 16.6, -4.2], [6.65, 17.2, -3.4], "ipd_glint", glow=15)
    m.box([9.35, 16.6, -4.2], [9.55, 17.2, -3.4], "ipd_glint", glow=15)
    for x, ang in ((6.9, -22.5), (7.45, -45.0), (8.0, -45.0), (8.55, -45.0), (9.1, -22.5)):
        m.box([x - 0.2, 17.6, -3.0], [x + 0.2, 18.0, 3.0], "ipd_feather_u", rot=("x", ang, [x, 17.8, -3.0]), glow=15)
    for i, (y0, y1) in enumerate(((5.0, 7.0), (6.2, 8.2), (7.4, 9.0))):
        z = 1.3 - i * 0.12
        m.el([6.2, y0, z - 0.1], [9.8, y1, z], {"north": ("ipd_feather", None), "up": ("ipd_feather", None)})
    m.box([7.8, 10.6, 4.0], [8.2, 10.9, 12.0], "ipd_glint", glow=15)
    m.box([7.0, 4.2, 9.0], [7.6, 6.0, 10.0], "ipd_shadow")
    m.box([8.4, 4.2, 9.0], [9.0, 6.0, 10.0], "ipd_shadow")
    return m


def ipd_wing(mid, sign):
    """One wing from its root at the anchor, extending to model +x (sign 1, the bird's right) or -x: the leading-edge
    arm and its swept tip, the covert row, four secondaries, four long primaries swept back 22.5 degrees, an underside
    shadow plate and a frost glint on the leading edge."""
    m = Model(mid)

    def xs(a, b):
        p, q = 8.0 + sign * a, 8.0 + sign * b
        return min(p, q), max(p, q)
    x0, x1 = xs(0.0, 16.0)
    m.box([x0, 7.4, 6.0], [x1, 8.6, 8.0], "ipd_ice")
    x0, x1 = xs(16.0, 24.0)
    m.box([x0, 7.6, 6.6], [x1, 8.4, 8.4], "ipd_ice", rot=("y", -22.5 * sign, [8.0 + sign * 16.0, 8.0, 7.0]))
    x0, x1 = xs(0.0, 16.0)
    m.el([x0, 7.6, 8.0], [x1, 8.0, 11.0], {"up": ("ipd_feather_u", None), "down": ("ipd_shadow", None)})
    for i in range(4):
        x0, x1 = xs(i * 4.0, i * 4.0 + 4.2)
        m.el([x0, 7.5 - i * 0.02, 9.0], [x1, 7.8 - i * 0.02, 17.0 + i * 0.6], {"up": ("ipd_feather", None), "down": ("ipd_feather", (16, 0, 0, 16)),
                                                                               "east": ("ipd_white", None), "west": ("ipd_white", None)})
    for i in range(4):
        x0, x1 = xs(14.0 + i * 2.4, 16.6 + i * 2.4)
        root = 8.0 + sign * (14.0 + i * 2.4)
        m.el([x0, 7.6 + i * 0.02, 8.0], [x1, 7.9 + i * 0.02, 18.0 + i * 0.8], {"up": ("ipd_feather", None), "down": ("ipd_feather", (16, 0, 0, 16)),
                                                                               "east": ("ipd_white", None), "west": ("ipd_white", None)},
             rot=("y", -22.5 * sign, [root, 7.8, 8.0]))
    x0, x1 = xs(0.0, 16.0)
    m.el([x0, 7.3, 7.0], [x1, 7.5, 14.0], {"down": ("ipd_shadow", None)})
    x0, x1 = xs(0.0, 16.0)
    m.box([x0, 8.6, 6.0], [x1, 8.8, 6.4], "ipd_glint", glow=15)
    return m


def ipd_tail():
    """Seven long tail feathers from the anchor (the rump) trailing to +z: three straight, two at 22.5 and two at 45
    degrees, eye spots at their tips, and a fan base."""
    m = Model("ipd_tail")
    for x in (6.6, 8.0, 9.4):
        m.el([x - 0.8, 7.9, 8.0], [x + 0.8, 8.1, 30.0], {"up": ("ipd_tail_feather", None), "down": ("ipd_tail_feather", (16, 0, 0, 16))}, glow=0)
    for ang in (22.5, -22.5):
        m.el([7.2, 7.85, 8.0], [8.8, 8.05, 27.0], {"up": ("ipd_tail_feather", None), "down": ("ipd_tail_feather", (16, 0, 0, 16))},
             rot=("y", ang, [8.0, 8.0, 8.0]))
    for ang in (45.0, -45.0):
        m.el([7.3, 7.8, 8.0], [8.7, 8.0, 22.0], {"up": ("ipd_tail_feather", None), "down": ("ipd_tail_feather", (16, 0, 0, 16))},
             rot=("y", ang, [8.0, 8.0, 8.0]))
    m.box([6.0, 7.5, 7.0], [10.0, 8.5, 11.0], "ipd_ice")
    return m


def ipd_quill():
    """A frost quill flying north: a white shaft, crossed vanes, a glowing tip with a diamond point."""
    m = Model("ipd_quill")
    m.box([7.8, 7.8, -4.0], [8.2, 8.2, 16.0], "ipd_quill_shaft")
    m.el([7.95, 6.6, 0.0], [8.05, 9.4, 12.0], {"east": ("ipd_quill_vane", None), "west": ("ipd_quill_vane", (16, 0, 0, 16))})
    m.el([6.6, 7.95, 0.0], [9.4, 8.05, 12.0], {"up": ("ipd_quill_vane", None), "down": ("ipd_quill_vane", (16, 0, 0, 16))})
    m.box([7.4, 7.4, -8.0], [8.6, 8.6, -4.0], "ipd_quill_tip", glow=15)
    m.cube((8.0, 8.0, -8.6), (0.9, 0.9, 1.2), "ipd_quill_tip", rot=("z", 45.0, [8.0, 8.0, -8.6]), glow=15)
    return m


def ipd_hexring():
    """A flat six-fold frost ring (32 x 32 px around the anchor): the frost plate and six arms of three standing
    crystals each, shorter outwards."""
    m = Model("ipd_hexring")
    flat_plate(m, 16.0, "ipd_hex_plate", y=8.0, th=0.06)
    for k in range(6):
        a = math.radians(60.0 * k)
        for j, (d, h, s) in enumerate(((5.5, 3.0, 1.3), (9.5, 2.2, 1.05), (13.0, 1.4, 0.8))):
            p = (8.0 + d * math.cos(a), 8.06 + h / 2.0, 8.0 + d * math.sin(a))
            m.cube(p, (s, h, s), "ipd_crystal", rot=("y", 45.0, list(p)), glow=15 if j == 0 else 0)
    return m


# ================================================================================================ Ice: frozen mountain

def build_fmq_textures():
    t_ice("fmq_ice", "CFFAFF", "4A8AD8", seed=500, alpha=210)
    t_ice("fmq_ice_core", "9FD8FF", "3A6AC8", seed=501, alpha=255, core="FFFFFF")
    t_glow("fmq_rim", "FFFFFF", "DFF6FF", alpha_edge=230)
    t_ice("fmq_crack", "CFFAFF", "4A8AD8", seed=502, alpha=220, cracks=5)
    t_ice("fmq_crack_core", "FFFFFF", "6AA8FF", seed=503, alpha=255, cracks=4, core="FFFFFF")
    t_stone("fmq_base", "8AA8C0", seed=504, cracks=4, crack_colour="DFF6FF")
    t_metal("fmq_band", "BFE8FF", seed=505, light=0.5, dark=0.3)
    t_ice("fmq_icicle", "FFFFFF", "7AC8F0", seed=506, alpha=225, size=16)
    t_ice("fmq_coffin", "E8F8FF", "9FD0F0", seed=507, alpha=150, cracks=3)
    t_glow("fmq_coffin_edge", "FFFFFF", "CFEFFF", alpha_edge=230)
    n = Noise(510)

    def flake(u, v):
        x, y = (u - 0.5) * 2.0, (v - 0.5) * 2.0
        r = math.hypot(x, y)
        ang = math.atan2(y, x)
        sector = (ang + math.pi / 6.0) % (math.pi / 3.0) - math.pi / 6.0
        along, across = r * math.cos(sector), abs(r * math.sin(sector))
        a = 0.0
        if across < 0.04 and r < 0.92:
            a = 245
        for b in (0.3, 0.5, 0.68):
            # side branches: lines leaving the arm at 60 degrees
            if along > b and across < (along - b) * 0.9 + 0.035 and across > (along - b) * 0.9 - 0.035 and along < b + 0.22:
                a = max(a, 225)
        if abs(r - 0.18) < 0.03 or r < 0.06:
            a = max(a, 240)
        a *= 0.8 + 0.2 * n.fbm(u * 6, v * 6)
        return (255, 255, 255, a)
    tex("fmq_flake_plate", 64, flake)


def fmq_peak(mid, cracked):
    """A crystal mountain standing on the anchor: a cracked two-box base, a five-tier spire of alternately turned boxes
    with a glowing core inside, four shoulder crystals leaning out 22.5 degrees, white rim edges and four small crystals
    round the foot."""
    m = Model(mid)
    ice = "fmq_crack" if cracked else "fmq_ice"
    core = "fmq_crack_core" if cracked else "fmq_ice_core"
    m.box([3.0, 8.0, 3.0], [13.0, 9.5, 13.0], "fmq_base")
    m.box([3.5, 8.0, 3.5], [12.5, 9.3, 12.5], "fmq_base", rot=("y", 45.0, [8.0, 8.65, 8.0]))
    m.box([7.2, 9.6, 7.2], [8.8, 27.0, 8.8], core, glow=15)
    m.box([5.0, 9.5, 5.0], [11.0, 16.0, 11.0], ice)
    m.box([5.6, 9.6, 5.6], [10.4, 20.0, 10.4], ice, rot=("y", 45.0, [8.0, 14.8, 8.0]))
    m.box([6.3, 16.0, 6.3], [9.7, 25.0, 9.7], ice)
    m.box([6.8, 20.0, 6.8], [9.2, 28.0, 9.2], ice, rot=("y", 45.0, [8.0, 24.0, 8.0]))
    m.box([7.3, 25.0, 7.3], [8.7, 31.0, 8.7], ice)
    m.box([7.6, 29.0, 7.6], [8.4, 32.0, 8.4], "fmq_rim", rot=("y", 45.0, [8.0, 30.5, 8.0]), glow=15)
    m.box([6.5, 9.5, 1.5], [9.5, 19.0, 4.5], ice, rot=("x", -22.5, [8.0, 9.5, 3.0]))
    m.box([6.5, 9.5, 11.5], [9.5, 18.0, 14.5], ice, rot=("x", 22.5, [8.0, 9.5, 13.0]))
    m.box([11.5, 9.5, 6.5], [14.5, 18.5, 9.5], ice, rot=("z", -22.5, [13.0, 9.5, 8.0]))
    m.box([1.5, 9.5, 6.5], [4.5, 17.5, 9.5], ice, rot=("z", 22.5, [3.0, 9.5, 8.0]))
    for (x, z) in ((5.0, 5.0), (11.0, 5.0), (5.0, 11.0), (11.0, 11.0)):
        m.box([x - 0.15, 9.5, z - 0.15], [x + 0.15, 16.0, z + 0.15], "fmq_rim", glow=15)
    for (x, z) in ((3.4, 3.4), (12.6, 3.4), (3.4, 12.6), (12.6, 12.6)):
        m.cube((x, 10.6, z), (1.4, 2.6, 1.4), ice, rot=("y", 45.0, [x, 10.6, z]))
    return m


def fmq_crown():
    """A ring of twelve icicles standing on a frost band (r 10 px) round the anchor; every other icicle tall with a
    glowing tip."""
    m = Model("fmq_crown")
    ring_xz(m, (8.0, 8.6, 8.0), 10.0, 8.4, 1.4, 1.2, "fmq_band", segs=8)
    for i in range(12):
        a = math.radians(30.0 * i)
        tall = i % 2 == 0
        h = 7.0 if tall else 4.5
        p = (8.0 + 10.0 * math.cos(a), 9.2 + h / 2.0, 8.0 + 10.0 * math.sin(a))
        m.cube(p, (1.4, h, 1.4), "fmq_icicle", rot=("y", 45.0, list(p)))
        if tall:
            q = (p[0], 9.2 + h + 0.7, p[2])
            m.cube(q, (0.7, 1.4, 0.7), "fmq_rim", rot=("y", 45.0, list(q)), glow=15)
    return m


def fmq_coffin():
    """An ice coffin: a translucent 16 px block standing on the anchor, inner crack planes, bright bevelled edges."""
    m = Model("fmq_coffin")
    m.box([0.0, 8.0, 0.0], [16.0, 24.0, 16.0], "fmq_coffin")
    m.el([2.0, 9.0, 7.95], [14.0, 23.0, 8.05], {"north": ("fmq_crack", None), "south": ("fmq_crack", None)})
    m.el([7.95, 9.0, 2.0], [8.05, 23.0, 14.0], {"east": ("fmq_crack", None), "west": ("fmq_crack", None)})
    for (x, z) in ((0.0, 0.0), (16.0, 0.0), (0.0, 16.0), (16.0, 16.0)):
        m.box([x - 0.35, 8.0, z - 0.35], [x + 0.35, 24.0, z + 0.35], "fmq_coffin_edge", glow=15)
    for y in (8.0, 24.0):
        m.box([0.35, y - 0.35, -0.35], [15.65, y + 0.35, 0.35], "fmq_coffin_edge", glow=15)
        m.box([0.35, y - 0.35, 15.65], [15.65, y + 0.35, 16.35], "fmq_coffin_edge", glow=15)
        m.box([-0.35, y - 0.34, 0.35], [0.35, y + 0.34, 15.65], "fmq_coffin_edge", glow=15)
        m.box([15.65, y - 0.34, 0.35], [16.35, y + 0.34, 15.65], "fmq_coffin_edge", glow=15)
    return m


def fmq_flake():
    """A flat six-arm snowflake sigil (32 x 32 px) with a standing centre crystal."""
    m = Model("fmq_flake")
    flat_plate(m, 16.0, "fmq_flake_plate", y=8.0, th=0.05)
    m.cube((8.0, 8.8, 8.0), (1.6, 1.6, 1.6), "fmq_rim", rot=("y", 45.0, [8.0, 8.8, 8.0]), glow=15)
    return m


# ================================================================================================ Space: void mirror

def build_vm_textures():
    t_metal("vm_bronze", "A86A32", seed=600, light=0.4, dark=0.4, grain=0.12)
    t_glass("vm_glass", "2A1858", alpha=245, size=32, seed=601, stars=14, star_colour="E8E0FF", rim="7A4AFF", deep="140A28")
    t_glass("vm_glass_ripple", "3A2470", alpha=245, size=32, seed=601, stars=14, star_colour="FFFFFF", rim="C8B8FF", deep="140A28", ripple=True)
    n = Noise(602)

    def trigram(u, v):
        c = mix(rgb("FFD54A"), rgb("A87A20"), v)
        for i, y in enumerate((0.25, 0.5, 0.75)):
            if abs(v - y) < 0.07 and 0.18 < u < 0.82 and not (i == 1 and abs(u - 0.5) < 0.1):
                c = rgb("2A1858")
        return (c[0], c[1], c[2], 255)
    tex("vm_stud", 16, trigram)
    t_bands("vm_cord", "C02A20", "7A1008", bands=6, seed=603)
    t_glow("vm_inner", "FFFFFF", "7A4AFF", alpha_edge=220)
    t_runes("vm_rune", "3A2A70", "C8B8FF", size=16, seed=604, glow_ink=True, width=0.12, strokes=4, alpha=230)
    t_glow("vm_rune_thin", "E8E0FF", "C8B8FF", alpha_edge=180)
    t_glass("vm_shard", "08060C", alpha=240, size=16, seed=605, rim="7A4AFF", deep="08060C")


def vm_mirror(mid, ripple):
    """The void mirror, centred on the anchor and facing north: an octagonal bronze frame of eight tangent segments, a
    void-glass face of two crossed squares with star specks, a violet inner rim, eight trigram studs at the frame's
    corners, a bronze back with a knob, a red cord and a tassel."""
    m = Model(mid)
    glass = "vm_glass_ripple" if ripple else "vm_glass"
    c = (8.0, 8.0, 8.0)
    for i in range(8):
        theta = 45.0 * i
        px, py = 8.0 + 6.0 * math.cos(math.radians(theta)), 8.0 + 6.0 * math.sin(math.radians(theta))
        a, swapped = norm_angle(theta + 90.0)
        size = (5.3, 1.3, 2.0) if not swapped else (1.3, 5.3, 2.0)
        m.cube((px, py, 8.0), size, "vm_bronze", rot=("z", a, [px, py, 8.0]))
    m.el([3.6, 3.6, 7.6], [12.4, 12.4, 7.9], {"north": (glass, None), "south": ("vm_bronze", None)}, glow=15)
    m.el([3.65, 3.65, 7.62], [12.35, 12.35, 7.88], {"north": (glass, None), "south": ("vm_bronze", None)}, rot=("z", 45.0, list(c)), glow=15)
    for i in range(8):
        theta = 22.5 + 45.0 * i
        px, py = 8.0 + 5.0 * math.cos(math.radians(theta)), 8.0 + 5.0 * math.sin(math.radians(theta))
        a, swapped = norm_angle(theta + 90.0)
        size = (3.6, 0.35, 0.3) if not swapped else (0.35, 3.6, 0.3)
        m.cube((px, py, 7.45), size, "vm_inner", rot=("z", a, [px, py, 7.45]), glow=15)
    for i in range(8):
        theta = 22.5 + 45.0 * i
        px, py = 8.0 + 6.5 * math.cos(math.radians(theta)), 8.0 + 6.5 * math.sin(math.radians(theta))
        m.el([px - 0.7, py - 0.7, 6.65], [px + 0.7, py + 0.7, 7.0], {"north": ("vm_stud", None), "east": ("vm_bronze", None), "west": ("vm_bronze", None),
                                                                     "up": ("vm_bronze", None), "down": ("vm_bronze", None)}, glow=15)
    m.box([4.2, 4.2, 8.6], [11.8, 11.8, 9.0], "vm_bronze")
    m.box([7.0, 7.0, 9.0], [9.0, 9.0, 10.4], "vm_bronze")
    m.box([7.8, 8.5, 10.0], [8.2, 13.0, 10.4], "vm_cord")
    m.box([7.4, 13.0, 9.8], [8.6, 15.5, 10.6], "vm_cord")
    return m


def vm_rune_ring():
    """An outer ring of rune plates (16 segments, r 9.5 px, eight of them rune plates) with four cardinal rune blocks,
    standing in the mirror's plane."""
    m = Model("vm_rune_ring")
    for i in range(16):
        theta = 22.5 * i
        px, py = 8.0 + 9.5 * math.cos(math.radians(theta)), 8.0 + 9.5 * math.sin(math.radians(theta))
        a, swapped = norm_angle(theta + 90.0)
        rune = i % 2 == 0
        length, width, depth = 3.6, (1.6 if rune else 0.4), (0.3 if rune else 0.2)
        size = (length, width, depth) if not swapped else (width, length, depth)
        name = "vm_rune" if rune else "vm_rune_thin"
        m.cube((px, py, 8.0), size, name, rot=("z", a, [px, py, 8.0]), glow=15)
    for theta in (0, 90, 180, 270):
        px, py = 8.0 + 11.0 * math.cos(math.radians(theta)), 8.0 + 11.0 * math.sin(math.radians(theta))
        m.cube((px, py, 8.0), (1.6, 1.6, 0.5), "vm_rune", rot=("z", 45.0, [px, py, 8.0]), glow=15)
    return m


def vm_shard():
    """A black glass shard (a stored blow): two crossed slivers with a violet edge."""
    m = Model("vm_shard")
    m.box([7.4, 5.0, 7.6], [8.6, 11.0, 8.4], "vm_shard", rot=("z", 22.5, [8.0, 8.0, 8.0]), glow=15)
    m.box([7.6, 6.0, 7.2], [8.4, 10.0, 8.8], "vm_shard", rot=("y", 45.0, [8.0, 8.0, 8.0]), glow=15)
    m.box([7.9, 5.4, 7.5], [8.1, 10.6, 7.55], "vm_inner", rot=("z", 22.5, [8.0, 8.0, 8.0]), glow=15)
    return m


# ================================================================================================ Space: time domain

def build_tsd_textures():
    t_metal("tsd_gold", "F2C24A", seed=700, light=0.45, dark=0.4)
    n = Noise(701)

    def face(u, v):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        if d > 1.0:
            return (0, 0, 0, 0)
        c = mix(rgb("FFF8E0"), rgb("E8D8A8"), d ** 1.5)
        c = mix(c, WHITE, 0.05 * (n.fbm(u * 6, v * 6) - 0.5))
        ang = math.atan2(v - 0.5, u - 0.5)
        k60 = (ang / math.tau * 60.0) % 1.0
        if 0.82 < d < 0.92 and (k60 < 0.12 or k60 > 0.88):
            c = rgb("2A2A33")
        k12 = (ang / math.tau * 12.0) % 1.0
        if 0.7 < d < 0.92 and (k12 < 0.04 or k12 > 0.96):
            c = rgb("2A2A33")
        if 0.45 < d < 0.47:
            c = mix(c, rgb("C8A040"), 0.7)
        if d > 0.95:
            c = rgb("C8A040")
        return (c[0], c[1], c[2], 255)
    tex("tsd_face", 64, face)
    t_metal("tsd_ink", "2A2A33", seed=702, light=0.3)
    t_metal("tsd_brass", "C8A040", seed=703, light=0.45, dark=0.4)
    t_metal("tsd_wood", "8A5A2A", seed=704, light=0.25, dark=0.4, grain=0.2)
    t_glass("tsd_glass", "E0E8FF", alpha=110, size=16, seed=705, rim="FFFFFF")
    t_flat("tsd_sand", "F0D080", seed=706, grain=0.15, colour2="D8B060")
    t_glass("tsd_drop", "C8D8FF", alpha=210, size=16, seed=707, rim="FFFFFF")
    t_glow("tsd_glow", "FFFFFF", "F2C24A", alpha_edge=230)


def tsd_clock():
    """The great clock, centred on the anchor and facing north: an ivory face (minute ticks and hour marks in its
    texture), a 16-segment gold rim, twelve hour studs, eight gear teeth round the rim and a centre boss (the hands are
    separate props)."""
    m = Model("tsd_clock")
    m.el([1.5, 1.5, 8.3], [14.5, 14.5, 8.5], {"north": ("tsd_face", None), "south": ("tsd_face", (16, 0, 0, 16))})
    ring_xy(m, (8.0, 8.0, 8.2), 6.8, 2.9, 1.0, 1.2, "tsd_gold", segs=16)
    for i in range(12):
        a = math.radians(30.0 * i)
        px, py = 8.0 + 5.6 * math.cos(a), 8.0 + 5.6 * math.sin(a)
        m.cube((px, py, 8.15), (0.8, 0.8, 0.3), "tsd_glow", rot=("z", 45.0, [px, py, 8.15]), glow=15)
    for i in range(8):
        theta = 45.0 * i + 22.5
        px, py = 8.0 + 7.9 * math.cos(math.radians(theta)), 8.0 + 7.9 * math.sin(math.radians(theta))
        a, swapped = norm_angle(theta)
        size = (1.4, 1.2, 0.9) if not swapped else (1.2, 1.4, 0.9)
        m.cube((px, py, 8.2), size, "tsd_brass", rot=("z", a, [px, py, 8.2]))
    m.box([7.3, 7.3, 7.7], [8.7, 8.7, 8.3], "tsd_gold")
    return m


def tsd_hand(mid, length, width):
    """A clock hand pivoting on the anchor, pointing +y: the blade, an arrow head, a counterweight tail, a gold cap."""
    m = Model(mid)
    w = width / 2.0
    m.box([8.0 - w, 8.0, 7.35], [8.0 + w, 8.0 + length, 7.55], "tsd_ink")
    m.cube((8.0, 8.0 + length - 0.2, 7.45), (1.3, 1.3, 0.2), "tsd_ink", rot=("z", 45.0, [8.0, 8.0 + length - 0.2, 7.45]))
    m.box([7.6, 6.2, 7.35], [8.4, 8.0, 7.55], "tsd_ink")
    m.box([7.4, 7.4, 7.15], [8.6, 8.6, 7.35], "tsd_gold", glow=15)
    return m


def tsd_gear(mid, r, teeth, spokes):
    """A gear wheel in the XY plane: a hub (two crossed squares), a rim of tangent segments, teeth round it, spokes."""
    m = Model(mid)
    m.box([6.6, 6.6, 7.4], [9.4, 9.4, 8.6], "tsd_brass")
    m.box([6.7, 6.7, 7.45], [9.3, 9.3, 8.55], "tsd_brass", rot=("z", 45.0, [8.0, 8.0, 8.0]))
    segs = 16 if teeth >= 16 else 8
    length = 2.0 * math.pi * r / segs * 1.08
    ring_xy(m, (8.0, 8.0, 8.0), r, length, 1.1, 0.9, "tsd_brass", segs=segs)
    for i in range(teeth):
        theta = 360.0 * i / teeth
        px, py = 8.0 + (r + 1.2) * math.cos(math.radians(theta)), 8.0 + (r + 1.2) * math.sin(math.radians(theta))
        a, swapped = norm_angle(theta)
        size = (1.3, 1.0, 0.8) if not swapped else (1.0, 1.3, 0.8)
        m.cube((px, py, 8.0), size, "tsd_gold", rot=("z", a, [px, py, 8.0]))
    for i in range(spokes):
        theta = 180.0 * i / spokes
        a, swapped = norm_angle(theta)
        size = (2 * r, 0.7, 0.5) if not swapped else (0.7, 2 * r, 0.5)
        m.cube((8.0, 8.0, 8.0), size, "tsd_brass", rot=("z", a, [8.0, 8.0, 8.0]))
    return m


def tsd_hourglass():
    """An hourglass centred on the anchor: wooden end plates and three posts, two stepped glass bulbs and a neck, sand
    in the upper bulb, a falling column and a pile below."""
    m = Model("tsd_hourglass")
    m.box([4.5, 13.5, 4.5], [11.5, 14.5, 11.5], "tsd_wood")
    m.box([4.5, 1.5, 4.5], [11.5, 2.5, 11.5], "tsd_wood")
    for i in range(3):
        a = math.radians(90.0 + 120.0 * i)
        p = (8.0 + 3.2 * math.cos(a), 8.0, 8.0 + 3.2 * math.sin(a))
        m.cube(p, (0.8, 11.0, 0.8), "tsd_wood")
    m.box([6.0, 11.0, 6.0], [10.0, 12.6, 10.0], "tsd_sand")
    m.box([7.85, 4.0, 7.85], [8.15, 10.8, 8.15], "tsd_sand", glow=15)
    m.box([6.0, 2.6, 6.0], [10.0, 4.2, 10.0], "tsd_sand")
    m.box([7.0, 4.2, 7.0], [9.0, 5.0, 9.0], "tsd_sand")
    m.box([5.6, 10.0, 5.6], [10.4, 13.5, 10.4], "tsd_glass")
    m.box([6.4, 8.8, 6.4], [9.6, 10.0, 9.6], "tsd_glass")
    m.box([7.4, 7.2, 7.4], [8.6, 8.8, 8.6], "tsd_glass")
    m.box([6.4, 6.0, 6.4], [9.6, 7.2, 9.6], "tsd_glass")
    m.box([5.6, 2.5, 5.6], [10.4, 6.0, 10.4], "tsd_glass")
    return m


def tsd_drop():
    """A suspended droplet: a turned bead and a little tip on top."""
    m = Model("tsd_drop")
    m.box([7.4, 7.2, 7.4], [8.6, 8.8, 8.6], "tsd_drop", rot=("y", 45.0, [8.0, 8.0, 8.0]), glow=15)
    m.box([7.7, 8.8, 7.7], [8.3, 9.6, 8.3], "tsd_drop", rot=("y", 45.0, [8.0, 9.2, 8.0]), glow=15)
    return m


# ================================================================================================ Space: black hole

def build_sbh_textures():
    t_disc("sbh_star", "FFFFFF", "FFF0A0", cut=False)
    t_glass("sbh_corona", "FFD54A", alpha=95, size=16, seed=800, rim="FFF0C0")
    t_glow("sbh_ray", "FFFFFF", "FFD54A", alpha_core=240, alpha_edge=0, power=1.0, ends=0.3)
    t_flat("sbh_black", "08060C", seed=801, grain=0.02, colour2="0C0814")
    t_glow("sbh_photon", "FFFFFF", "FFF0C0", alpha_edge=210, power=1.2)
    stops = [("FFB040", "FF8A20"), ("FF8A20", "C04A30"), ("C04A30", "7A3AC8"), ("7A3AC8", "3A1A70")]
    for i, (a, b) in enumerate(stops):
        n = Noise(810 + i)

        def disk(u, v, a=rgb(a), b=rgb(b), n=n):
            c = mix(a, b, u)
            streak = 0.5 + 0.5 * math.sin((v * 7.0 + n.fbm(u * 3, v * 3) * 2.0) * math.pi)
            c = mix(c, WHITE, 0.2 * streak ** 4)
            return (c[0], c[1], c[2], 225)
        tex("sbh_disk_%d" % i, 16, disk)
    t_glow("sbh_disk_hot", "FFFFFF", "FFF0C0", alpha_edge=240, power=1.0)
    t_glow("sbh_jet", "E8F4FF", "6A9AFF", alpha_core=200, alpha_edge=20, power=1.0)
    t_glow("sbh_jet_core", "FFFFFF", "C8E0FF", alpha_edge=230, power=1.0)
    t_stone("sbh_rock", "5A5048", seed=820, cracks=2, crack_colour="FF8A20")


def sbh_star():
    """A bright young star: a stepped white core, a translucent golden corona shell and a cross of four rays."""
    m = Model("sbh_star")
    c = (8.0, 8.0, 8.0)
    ball(m, c, 4.0, "sbh_star", 3, glow=15)
    ball(m, c, 5.6, "sbh_corona", 2, glow=15)
    for a in (0.0, 45.0, -45.0):
        m.el([-2.0, 7.9, 7.9], [18.0, 8.1, 8.1], {s: ("sbh_ray", None) for s in ("north", "south", "up", "down")}, rot=("z", a, list(c)), glow=15)
    m.el([7.9, 7.9, -2.0], [8.1, 8.1, 18.0], {s: ("sbh_ray", None) for s in ("east", "west", "up", "down")}, glow=15)
    return m


def sbh_core():
    """The black core: a matte black stepped sphere (the photon ring is its part)."""
    m = Model("sbh_core")
    ball(m, (8.0, 8.0, 8.0), 4.5, "sbh_black", 3)
    return m


def sbh_photon_ring():
    """The thin bright photon ring standing in the XY plane round the core (r 6.2 px)."""
    m = Model("sbh_photon_ring")
    ring_xy(m, (8.0, 8.0, 8.0), 6.2, 2.6, 0.45, 0.3, "sbh_photon", segs=16, glow=15)
    return m


def sbh_disk():
    """The accretion disc: sixteen plates on a 14 px ring shading from orange to violet round the disc (the
    approaching side brighter) and a white-hot inner ring of sixteen."""
    m = Model("sbh_disk")
    c = (8.0, 8.0, 8.0)
    for i in range(16):
        theta = 22.5 * i
        px, pz = 8.0 + 14.0 * math.cos(math.radians(theta)), 8.0 + 14.0 * math.sin(math.radians(theta))
        a, swapped = norm_angle(-(theta + 90.0))
        shade = int(min(3, abs(((i + 4) % 16) - 8) / 2.1))
        k = 0.96 if i % 2 else 1.0
        size = (5.8, 0.3 * k, 4.2 * k) if not swapped else (4.2 * k, 0.3 * k, 5.8)
        name = "sbh_disk_%d" % shade
        m.cube((px, 8.0, pz), size, name, rot=("y", a, [px, 8.0, pz]), glow=15)
    ring_xz(m, (8.0, 8.04, 8.0), 9.5, 3.9, 2.0, 0.34, "sbh_disk_hot", segs=16, glow=15)
    return m


def sbh_jet():
    """A polar jet from the anchor up to y 32: a tapering translucent column, a white core and a base flare."""
    m = Model("sbh_jet")
    for (h, y0, y1) in ((2.0, 8.0, 12.0), (1.5, 12.0, 18.0), (1.0, 18.0, 25.0), (0.6, 25.0, 32.0)):
        m.box([8 - h, y0, 8 - h], [8 + h, y1, 8 + h], "sbh_jet", glow=15, sides=("north", "south", "east", "west"))
    m.box([7.6, 8.0, 7.6], [8.4, 32.0, 8.4], "sbh_jet_core", glow=15, sides=("north", "south", "east", "west"))
    m.el([4.0, 8.0, 4.0], [12.0, 8.4, 12.0], {"up": ("sbh_jet", None), "down": ("sbh_jet", None)}, glow=15)
    return m


def sbh_debris():
    """A spiralling rock chunk: three turned stone boxes with glowing cracks."""
    m = Model("sbh_debris")
    m.box([6.5, 6.8, 6.8], [9.5, 9.2, 9.6], "sbh_rock", rot=("y", 22.5, [8.0, 8.0, 8.2]))
    m.box([7.0, 8.0, 6.5], [9.4, 10.0, 8.5], "sbh_rock", rot=("x", 22.5, [8.2, 9.0, 7.5]))
    m.box([6.0, 6.5, 8.0], [8.0, 8.5, 10.0], "sbh_rock", rot=("z", -22.5, [7.0, 7.5, 9.0]))
    return m


# ================================================================================================ build everything

def build_textures():
    build_zx_textures()
    build_hps_textures()
    build_tte_textures()
    build_nhtv_textures()
    build_ipd_textures()
    build_fmq_textures()
    build_vm_textures()
    build_tsd_textures()
    build_sbh_textures()


def build_models():
    for k in ZX:
        zx_sword(k)
    zx_plinth()
    zx_map()
    zx_sky_sword()
    zx_seam()
    hps_sword()
    hps_gate()
    hps_door("hps_gate_door_r", 1)
    hps_door("hps_gate_door_l", -1)
    hps_halo()
    hps_chain()
    tte_drum("tte_drum", False)
    tte_drum("tte_drum_lit", True)
    tte_grid()
    tte_bolt()
    tte_vortex_rim()
    nhtv_ring("nhtv_ring", False)
    nhtv_ring("nhtv_ring_lit", True)
    nhtv_qilin()
    nhtv_qilin_tail()
    nhtv_whip()
    nhtv_deity()
    nhtv_deity_hammer()
    ipd_phoenix()
    ipd_wing("ipd_wing_r", 1)
    ipd_wing("ipd_wing_l", -1)
    ipd_tail()
    ipd_quill()
    ipd_hexring()
    fmq_peak("fmq_peak", False)
    fmq_peak("fmq_peak_cracked", True)
    fmq_crown()
    fmq_coffin()
    fmq_flake()
    vm_mirror("vm_mirror", False)
    vm_mirror("vm_mirror_ripple", True)
    vm_rune_ring()
    vm_shard()
    tsd_clock()
    tsd_hand("tsd_hand_long", 6.4, 0.6)
    tsd_hand("tsd_hand_short", 4.2, 0.9)
    tsd_gear("tsd_gear_l", 5.0, 16, 4)
    tsd_gear("tsd_gear_s", 3.2, 8, 2)
    tsd_hourglass()
    tsd_drop()
    sbh_star()
    sbh_core()
    sbh_photon_ring()
    sbh_disk()
    sbh_jet()
    sbh_debris()


# ================================================================================================ output and checks

ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


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
            if rot["angle"] not in ANGLES:
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


def contact_sheet(path, names):
    cell = 68
    cols = 12
    rows = (len(names) + cols - 1) // cols
    sheet = Image(cols * cell, rows * cell, (40, 40, 48, 255))
    for idx, name in enumerate(names):
        img = TEXTURES[name]
        ox, oy = (idx % cols) * cell + 2, (idx // cols) * cell + 2
        for y in range(64):
            for x in range(64):
                sx, sy = x * img.width // 64, y * img.height // 64
                chk = (90, 90, 100, 255) if ((x // 8) + (y // 8)) % 2 else (60, 60, 70, 255)
                sheet.set(ox + x, oy + y, chk)
                sheet.blend(ox + x, oy + y, img.get(sx, sy))
    sheet.save(path)


def main():
    build_textures()
    build_models()
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
        print("  %-22s %2d elements  %s" % (mid, len(m.els), ", ".join(sorted(m.tex))))
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    with open(IDS_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(MODELS)) + "\n")
    if "--sheet" in sys.argv:
        out = sys.argv[sys.argv.index("--sheet") + 1]
        contact_sheet(out, sorted(used))
        print("contact sheet:", out)
    print("ultimates A models: %d, textures: %d, errors: %d" % (len(MODELS), len(used), errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
