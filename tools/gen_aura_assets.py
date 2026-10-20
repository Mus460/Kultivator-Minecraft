"""Generates the assets of the Aura Kultivasi, realms 1-5 (DESIGN 31, module E). Everything here is ours: procedural
textures and cuboid item models, nothing is downloaded.

Writes:
  textures/misc/aura/<name>.png     quad textures for client/aura (white, tinted by the vertex colour): the soft glow and
                                    band, the Qi Refining vortex and mist ring, the Foundation platform, pillar card and
                                    eight-trigram array, the Golden Core ray fan, halo band and sun array, the Nascent Soul
                                    infant card and lotus decals, the breakthrough-ready crack lines
  textures/item/fx/aura_*.png       textures of the props (prefixes aura_fe_, aura_gc_, aura_ns_)
  models/item/fx/<id>.json          one cuboid model per prop
  items/fx/<id>.json                the item definitions (kultivator:fx/<id>)
  tools/fx_model_ids_aura.txt       this module's id list (tools/fx_model_ids.txt is never touched)

Quad textures (entityTranslucentEmissive): the alpha cutout runs before the vertex colour, so every visible pixel keeps
alpha >= 0.11 (28) and every other pixel is exactly 0; fades go into the vertex alpha. Glows fade to alpha 0 well inside
the border.

Rules kept by every model (checked by validate(), as in gen_fire_ultimate_models.py):
  * the anchor is (8, 8, 8) (the base of a standing prop, the centre of a floating one); the front faces north (-z);
  * at most 40 elements; element coordinates within -16..32, uv within 0..16;
  * an element rotation is a single axis by a multiple of 22.5 between -45 and 45;
  * rotations follow the right hand (Y by +a turns +x towards -z, X by +a turns +y towards +z, Z by +a turns +x
    towards +y).
Light: props that breathe by light (the golden core pearl, the soul infant) carry no light_emission, so the display's
brightness override (ModelHandle.lightCycle) shows; the pillar of light and the lotus seat glow at 15.

Run with: python tools/gen_aura_assets.py   (validates everything and prints a summary)
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
QUAD_DIR = os.path.join(ROOT, "textures", "misc", "aura")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")
IDS_FILE = os.path.join(HERE, "fx_model_ids_aura.txt")
NS = "kultivator"
PREFIXES = ("aura_fe_", "aura_gc_", "aura_ns_")

TEXTURES = {}
MODELS = {}
QUADS = {}


# ================================================================================================ helpers

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


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0.0 if L <= 1e-9 else clamp01(((px - ax) * dx + (py - ay) * dy) / L)
    qx, qy = ax + t * dx, ay + t * dy
    return math.hypot(px - qx, py - qy)


# ================================================================================================ quad textures

def quad(name, w, h, fn, ss=2):
    """fn(x, y) -> (shade 0..1, alpha 0..1) with x, y in -1..1 (y down); supersampled ss x ss. The alpha rule of
    entityTranslucentEmissive: visible pixels >= 0.11, the rest exactly 0."""
    img = Image(w, h)
    for py in range(h):
        for px in range(w):
            sa = 0.0
            ss_shade = 0.0
            for j in range(ss):
                for i in range(ss):
                    x = ((px + (i + 0.5) / ss) / w) * 2.0 - 1.0
                    y = ((py + (j + 0.5) / ss) / h) * 2.0 - 1.0
                    s, a = fn(x, y)
                    a = clamp01(a)
                    sa += a
                    ss_shade += s * a
            a = sa / (ss * ss)
            shade = ss_shade / sa if sa > 1e-6 else 1.0
            if a < 0.07:
                img.set(px, py, (0, 0, 0, 0))
                continue
            a = max(0.11, a)
            c = int(round(255 * clamp01(shade)))
            img.set(px, py, (c, c, c, int(round(a * 255))))
    QUADS[name] = img
    return img


def oct_radius(x, y):
    """The 'radius' of a regular octagon (flat sides facing the axes): max of |x|, |y| and (|x| + |y|) / sqrt 2."""
    return max(abs(x), abs(y), (abs(x) + abs(y)) / math.sqrt(2.0))


def line(d, width, soft):
    """A soft line profile at distance d."""
    return clamp01(1.0 - (abs(d) - width * 0.5) / soft) if abs(d) > width * 0.5 else 1.0


def glow_soft():
    def fn(x, y):
        r = math.hypot(x, y)
        a = (math.exp(-(r * r) / 0.16) * 0.9 + math.exp(-(r * r) / 0.02) * 0.25) * (1.0 - smooth(0.78, 0.98, r))
        return 1.0, a
    quad("glow_soft", 64, 64, fn)


def band_soft():
    """v (y) runs across the band: a hot centre line in a soft halo, clear at both edges."""
    def fn(x, y):
        d = abs(y)
        a = (math.exp(-(d * d) / 0.08) * 0.65 + math.exp(-(d * d) / 0.008) * 0.55) * (1.0 - smooth(0.6, 0.92, d))
        return 0.85 + 0.15 * math.exp(-(d * d) / 0.008), a
    quad("band_soft", 32, 32, fn)


def qi_vortex():
    """A four-arm cloud swirl seen from above: arms winding in, cloudy, a clear eye and a soft rim."""
    n = Noise(311)

    def fn(x, y):
        r = math.hypot(x, y)
        if r > 1.0:
            return 1.0, 0.0
        th = math.atan2(y, x)
        arm = 0.5 + 0.5 * math.cos(4.0 * (th + 3.4 * r))
        cloud = n.fbm(x * 3.0 + 7.0, y * 3.0 + 3.0, 4)
        env = smooth(0.06, 0.28, r) * (1.0 - smooth(0.62, 0.98, r))
        a = env * (0.18 + 0.82 * arm ** 1.6) * (0.65 + 0.55 * cloud)
        eye = math.exp(-((r - 0.16) ** 2) / 0.003) * 0.55
        a = a + eye * (1.0 - smooth(0.0, 0.08, abs(r - 0.16)) * 0.3)
        shade = 0.82 + 0.18 * arm
        return shade, clamp01(a)
    quad("qi_vortex", 128, 128, fn)


def decal_mist_ring():
    """A soft double ring of mist (two misty bands with drifting wisps between them)."""
    n = Noise(312)

    def fn(x, y):
        r = math.hypot(x, y)
        th = math.atan2(y, x)
        wobble = (n.fbm(math.cos(th) * 2.0 + 5.0, math.sin(th) * 2.0 + 5.0, 3) - 0.5) * 0.08
        mist = n.fbm(x * 4.0 + 1.0, y * 4.0 + 9.0, 4)
        b1 = math.exp(-((r - 0.62 - wobble) ** 2) / 0.004)
        b2 = math.exp(-((r - 0.86 + wobble) ** 2) / 0.0025)
        haze = 0.25 * smooth(0.5, 0.65, r) * (1.0 - smooth(0.8, 0.95, r))
        a = (b1 * 0.8 + b2 * 0.65 + haze) * (0.55 + 0.6 * mist) * (1.0 - smooth(0.93, 1.0, r))
        return 0.9 + 0.1 * mist, clamp01(a)
    quad("decal_mist_ring", 128, 128, fn)


def fe_platform():
    """The octagonal platform of light: a soft glowing plate, a bright rim, an inner octagon line, eight spokes and a
    centre circle."""
    def fn(x, y):
        d = oct_radius(x, y)
        r = math.hypot(x, y)
        if d > 1.0:
            return 1.0, 0.0
        fill = 0.32 * (1.0 - smooth(0.80, 0.94, d)) * (0.75 + 0.25 * (1.0 - d))
        rim = line(d - 0.88, 0.03, 0.035) * 0.95
        inner = line(d - 0.58, 0.018, 0.025) * 0.7
        spoke = 0.0
        for k in range(8):
            a = math.pi * 2.0 * k / 8.0 + math.pi / 8.0
            ex, ey = math.cos(a) * 0.95, math.sin(a) * 0.95
            spoke = max(spoke, line(seg_dist(x, y, 0.0, 0.0, ex, ey), 0.012, 0.02) * smooth(0.2, 0.3, r) * (1.0 - smooth(0.82, 0.9, d)))
        centre = line(r - 0.18, 0.02, 0.025) * 0.8
        a = fill + rim + inner + spoke * 0.45 + centre
        return 0.9 + 0.1 * clamp01(rim + inner), clamp01(a) * (1.0 - smooth(0.95, 1.0, d))
    quad("fe_platform", 128, 128, fn)


def fe_pillar():
    """A pillar of light seen from the side (32 x 64): a bright core line in a soft shaft, a wide foot and a bright
    capital, fading out above it."""
    def fn(x, y):
        v = (y + 1.0) * 0.5  # 0 top .. 1 bottom
        core = math.exp(-(x * x) / 0.012)
        shaft = math.exp(-(x * x) / 0.12) * 0.55
        body = (core + shaft) * smooth(0.04, 0.12, v)
        cap = math.exp(-((v - 0.11) ** 2) / 0.0012) * math.exp(-(x * x) / 0.35) * 0.9
        foot = math.exp(-((v - 0.96) ** 2) / 0.0015) * math.exp(-(x * x) / 0.4) * 0.8
        a = (body + cap + foot) * (1.0 - smooth(0.88, 1.0, abs(x)))
        return 0.85 + 0.15 * core, clamp01(a)
    quad("fe_pillar", 32, 64, fn)


# The Earlier Heaven (Fu Xi) order round the circle from the top, lines from the inside (bottom line) outward:
# Qian 111, Dui 110, Li 101, Zhen 100, Kun 000, Gen 001, Kan 010, Xun 011 (1 = solid).
TRIGRAMS = ["111", "110", "101", "100", "000", "001", "010", "011"]


def decal_bagua():
    """The eight trigrams round an octagon, an inner circle and a small taiji ring in the middle."""
    def fn(x, y):
        r = math.hypot(x, y)
        d = oct_radius(x, y)
        if r > 1.0:
            return 1.0, 0.0
        a = 0.0
        a += line(d - 0.86, 0.022, 0.03) * 0.95
        a += line(d - 0.80, 0.010, 0.02) * 0.6
        a += line(r - 0.42, 0.014, 0.02) * 0.65
        a += line(r - 0.16, 0.02, 0.02) * 0.7
        # the taiji's S-curve inside the small ring
        if r < 0.16:
            s = math.hypot(x, y - 0.08) - 0.08 if y > 0 else math.hypot(x, y + 0.08) - 0.08
            a += line(s, 0.012, 0.015) * 0.6 * (1.0 if (x > 0) == (y > 0) else 0.0)
        # trigrams: each in its own 45 degree sector, the bars tangential
        th = math.atan2(x, -y)  # 0 at the top, clockwise
        k = int(round(th / (math.pi / 4.0))) % 8
        centre = k * math.pi / 4.0
        dth = math.atan2(math.sin(th - centre), math.cos(th - centre))
        t = r * math.sin(dth)  # tangential offset
        for i, bit in enumerate(TRIGRAMS[k]):
            rr = 0.55 + i * 0.075
            if abs(r * math.cos(dth) - rr) < 0.022 and abs(t) < 0.11:
                if bit == "1" or abs(t) > 0.025:
                    a += 0.95 * clamp01((0.11 - abs(t)) / 0.015)
        return 1.0, clamp01(a) * (1.0 - smooth(0.92, 1.0, r))
    quad("decal_bagua", 128, 128, fn)


def gc_rays():
    """A fan of twelve rays (long and short in turn) round a soft disc."""
    def fn(x, y):
        r = math.hypot(x, y)
        th = math.atan2(y, x)
        a = 0.0
        for k in range(12):
            c = math.pi * 2.0 * k / 12.0
            dth = math.atan2(math.sin(th - c), math.cos(th - c))
            length = 0.95 if k % 2 == 0 else 0.68
            width = 0.11 * (1.0 - r / length) if r < length else 0.0
            if width > 0.0:
                a = max(a, clamp01(1.0 - abs(dth * r) / max(1e-4, width)) * smooth(0.1, 0.22, r) * (1.0 - smooth(length * 0.6, length, r)))
        disc = math.exp(-(r * r) / 0.03) * 0.8
        return 1.0, clamp01(a * 0.85 + disc) * (1.0 - smooth(0.95, 1.0, r))
    quad("gc_rays", 128, 128, fn)


def gc_halo():
    """The halo band (u along the ring, v across): a bright gold line with fine engraved dashes and one bead per
    segment."""
    def fn(x, y):
        d = abs(y)
        line_core = math.exp(-(d * d) / 0.02)
        halo = math.exp(-(d * d) / 0.18) * 0.45
        dash = 0.8 + 0.2 * math.cos(x * math.pi * 6.0)
        bead = math.exp(-((x * x) + (y * y)) / 0.05)
        a = (line_core * dash + halo + bead * 0.7) * (1.0 - smooth(0.85, 1.0, d))
        return 0.85 + 0.15 * max(line_core, bead), clamp01(a)
    quad("gc_halo", 32, 32, fn)


def decal_sun_array():
    """A sun disc with twelve rays, a ring of nine dots and a thin outer circle."""
    def fn(x, y):
        r = math.hypot(x, y)
        th = math.atan2(y, x)
        if r > 1.0:
            return 1.0, 0.0
        a = math.exp(-(r * r) / 0.012) * 0.9
        a += line(r - 0.2, 0.02, 0.02) * 0.9
        for k in range(12):
            c = math.pi * 2.0 * k / 12.0
            dth = math.atan2(math.sin(th - c), math.cos(th - c))
            if 0.25 < r < 0.62:
                half = 0.05 * (0.62 - r) / 0.37 + 0.006
                a += clamp01(1.0 - (abs(dth * r) - half) / 0.012) * 0.75 if abs(dth * r) < half + 0.012 else 0.0
        a += line(r - 0.68, 0.015, 0.02) * 0.8
        for k in range(9):
            c = math.pi * 2.0 * k / 9.0 - math.pi / 2.0
            dx, dy = x - math.cos(c) * 0.8, y - math.sin(c) * 0.8
            a += clamp01(1.0 - (math.hypot(dx, dy) - 0.035) / 0.015) * 0.95
        a += line(r - 0.9, 0.012, 0.018) * 0.7
        return 1.0, clamp01(a) * (1.0 - smooth(0.94, 1.0, r))
    quad("decal_sun_array", 128, 128, fn)


def petal_half_width(t):
    """Half width (fraction of the petal length) of a lotus petal at t (0 base .. 1 tip)."""
    if t < 0.0 or t > 1.0:
        return -1.0
    if t < 0.45:
        return 0.30 * (0.2 + 0.8 * math.sin(t / 0.45 * math.pi / 2.0))
    return 0.30 * max(0.0, 1.0 - ((t - 0.45) / 0.55) ** 1.4)


def lotus_layer(x, y, count, r0, r1, offset):
    """Outline, faint fill and midrib of {@code count} petals from radius r0 to r1."""
    r = math.hypot(x, y)
    th = math.atan2(y, x)
    a = 0.0
    length = r1 - r0
    for k in range(count):
        c = math.pi * 2.0 * k / count + offset
        dth = math.atan2(math.sin(th - c), math.cos(th - c))
        along = r * math.cos(dth)
        across = r * math.sin(dth)
        t = (along - r0) / length
        hw = petal_half_width(t) * length
        if hw <= 0.0:
            continue
        edge = hw - abs(across)
        if edge > -0.02:
            outline = clamp01(1.0 - abs(edge) / 0.016)
            fill = 0.22 if edge > 0 else 0.0
            rib = clamp01(1.0 - abs(across) / 0.008) * 0.4 * (1.0 - t) if edge > 0 else 0.0
            a = max(a, outline * 0.95 + fill + rib)
    return a


def decal_lotus(name, sixteen):
    def fn(x, y):
        r = math.hypot(x, y)
        if r > 1.0:
            return 1.0, 0.0
        if sixteen:
            a = max(lotus_layer(x, y, 8, 0.16, 0.96, 0.0), lotus_layer(x, y, 8, 0.14, 0.7, math.pi / 8.0))
        else:
            a = lotus_layer(x, y, 8, 0.16, 0.92, 0.0)
        a += line(r - 0.13, 0.02, 0.02) * 0.85
        a += math.exp(-(r * r) / 0.006) * 0.5
        return 1.0, clamp01(a) * (1.0 - smooth(0.95, 1.0, r))
    quad(name, 128, 128, fn)


def decal_crack():
    """Thin branching crack lines running out from the middle (the bottleneck warning)."""
    rnd = random.Random(313)
    segs = []

    def walk(x, y, angle, length, depth):
        steps = 6
        for _ in range(steps):
            step = length / steps
            angle += (rnd.random() - 0.5) * 0.9
            nx, ny = x + math.cos(angle) * step, y + math.sin(angle) * step
            segs.append((x, y, nx, ny, depth))
            if depth < 2 and rnd.random() < 0.28:
                walk(nx, ny, angle + (rnd.random() - 0.5) * 1.8, length * 0.45, depth + 1)
            x, y = nx, ny

    for k in range(9):
        a = math.pi * 2.0 * k / 9.0 + rnd.random() * 0.4
        walk(math.cos(a) * 0.12, math.sin(a) * 0.12, a, 0.78, 0)

    def fn(x, y):
        r = math.hypot(x, y)
        if r > 1.0:
            return 1.0, 0.0
        a = 0.0
        for (ax, ay, bx, by, depth) in segs:
            if abs(x - ax) > 0.2 and abs(x - bx) > 0.2:
                continue
            d = seg_dist(x, y, ax, ay, bx, by)
            w = 0.012 if depth == 0 else 0.008
            a = max(a, clamp01(1.0 - (d - w * 0.5) / 0.012) * (1.0 if depth == 0 else 0.75))
        return 1.0, a * (1.0 - smooth(0.88, 1.0, r))
    quad("decal_crack", 128, 128, fn, ss=2)


def ns_infant():
    """The soul infant's glow card: a small figure of soft light seated in meditation (a round head with a topknot,
    sloping shoulders, hands together in the lap, crossed legs spread wide), inside a faint halo of its own."""
    def sdf(x, y):
        # card space: x -1..1, y -1 (top) .. 1 (bottom)
        head = math.hypot(x, y + 0.46) - 0.21
        knot = math.hypot(x, y + 0.71) - 0.07
        # the torso: a trapezoid widening from the shoulders (y -0.22) to the lap (y 0.34)
        k = clamp01((y + 0.22) / 0.56)
        half = 0.17 + 0.15 * k
        torso = max(abs(x) - half, -0.24 - y, y - 0.36)
        shoulders = math.hypot(x / 1.6, y + 0.17) - 0.13
        legs = (math.hypot(x / 0.66, (y - 0.5) / 0.17) - 1.0) * 0.17
        return min(head, knot, torso, shoulders, legs)

    def fn(x, y):
        d = sdf(x, y)
        inside = clamp01(0.5 - d / 0.03)
        glow = math.exp(-max(0.0, d) / 0.08) * 0.42
        # a brighter heart in the chest and the hands in the lap
        heart = math.exp(-(x * x + (y - 0.05) ** 2) / 0.02) * 0.3 + math.exp(-(x * x + (y - 0.32) ** 2) / 0.006) * 0.25
        r = math.hypot(x, y * 0.95)
        body = 0.62 + 0.25 * clamp01(-d / 0.12) + heart
        a = (inside * body + glow * (1.0 - inside)) * (1.0 - smooth(0.85, 1.0, r))
        return 0.88 + 0.12 * inside, clamp01(a)
    quad("ns_infant", 64, 64, fn)


def build_quads():
    glow_soft()
    band_soft()
    qi_vortex()
    decal_mist_ring()
    fe_platform()
    fe_pillar()
    decal_bagua()
    gc_rays()
    gc_halo()
    decal_sun_array()
    decal_lotus("decal_lotus8", False)
    decal_lotus("decal_lotus16", True)
    decal_crack()
    ns_infant()


# ================================================================================================ prop textures

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


def build_textures():
    # ---- Foundation: the pillar of light
    cyan, cyan_white, deep = rgb("7FE0FF"), rgb("E8FBFF"), rgb("2A8EC8")

    def glass(u, v):
        stripe = 0.5 + 0.5 * math.cos(u * math.tau * 3.0)
        c = mix(cyan, cyan_white, 0.35 + 0.4 * stripe ** 4)
        c = mix(c, deep, 0.18 * v)
        a = 150 + 70 * stripe ** 4
        return (c[0], c[1], c[2], a)
    tex("aura_fe_pillar_glass", 16, glass)

    def core(u, v):
        k = clamp01(1.0 - abs(u - 0.5) * 2.0)
        c = mix(cyan, WHITE, 0.4 + 0.6 * k)
        return (c[0], c[1], c[2], 230)
    tex("aura_fe_pillar_core", 16, core)

    def ring(u, v):
        c = mix(cyan_white, cyan, v)
        if v < 0.15 or v > 0.85:
            c = mix(c, WHITE, 0.6)
        return (c[0], c[1], c[2], 235)
    tex("aura_fe_pillar_ring", 16, ring)

    # ---- Golden Core: the faceted pearl and the great halo
    gold, gold_hi, gold_lo = rgb("FFD54A"), rgb("FFF6C8"), rgb("C88A18")

    def pearl(u, v):
        d = math.hypot(u - 0.36, v - 0.32)
        c = mix(gold_hi, gold, clamp01(d * 1.8))
        c = mix(c, gold_lo, clamp01((d - 0.45) * 2.0))
        # a facet edge
        if abs(u - v) < 0.04 or abs(u + v - 1.0) < 0.04:
            c = mix(c, WHITE, 0.35)
        return (c[0], c[1], c[2], 255)
    tex("aura_gc_core_gold", 16, pearl)

    def shell(u, v):
        e = min(u, v, 1.0 - u, 1.0 - v)
        c = mix(WHITE, gold_hi, 0.4)
        a = 70 + 120 * clamp01(1.0 - e / 0.18)
        return (c[0], c[1], c[2], a)
    tex("aura_gc_core_glass", 16, shell)

    def band(u, v, along_u):
        s = v if along_u else u
        k = clamp01(1.0 - abs(s - 0.5) * 2.0)
        c = mix(gold_lo, gold_hi, k ** 1.5)
        o = u if along_u else v
        c = mix(c, WHITE, 0.25 * (0.5 + 0.5 * math.cos(o * math.tau * 2.0)) ** 6)
        return (c[0], c[1], c[2], 245)
    tex("aura_gc_halo_band", 16, lambda u, v: band(u, v, True))
    tex("aura_gc_halo_band_u", 16, lambda u, v: band(u, v, False))

    def bead(u, v):
        d = math.hypot(u - 0.38, v - 0.36)
        c = mix(WHITE, gold, clamp01(d * 1.7))
        return (c[0], c[1], c[2], 255)
    tex("aura_gc_bead", 16, bead)

    # ---- Nascent Soul: the glass infant and the lotus seat
    soul, violet, lilac = rgb("E9DCFF"), rgb("B98CFF"), rgb("D8C4FF")

    def ns_glass(u, v):
        c = mix(soul, violet, 0.15 + 0.35 * v)
        c = mix(c, WHITE, 0.2 * (1.0 - v))
        return (c[0], c[1], c[2], 185)
    tex("aura_ns_glass", 16, ns_glass)

    def ns_face(u, v):
        c = mix(soul, lilac, 0.2 + 0.2 * v)
        a = 200
        # closed eyes: two short arcs, a small calm mouth
        for ex in (0.32, 0.68):
            dx, dy = u - ex, v - 0.48
            if abs(dx) < 0.12 and abs(dy - 0.06 * (1 - (dx / 0.12) ** 2) + 0.03) < 0.035:
                c = mix(c, rgb("6A4AA8"), 0.85)
        if abs(u - 0.5) < 0.07 and abs(v - 0.72) < 0.025:
            c = mix(c, rgb("C27AB8"), 0.8)
        return (c[0], c[1], c[2], a)
    tex("aura_ns_face", 16, ns_face)

    def ns_hair(u, v):
        c = mix(rgb("4A3A78"), rgb("7A5AC8"), v)
        return (c[0], c[1], c[2], 220)
    tex("aura_ns_hair", 16, ns_hair)

    pink, pink_white, rose = rgb("F6C8E8"), rgb("FFF4FB"), rgb("E890C8")

    def petal(u, v):
        t = 1.0 - v
        hw = petal_half_width(t) * 1.55
        du = abs(u - 0.5)
        if hw <= 0.0 or du > hw:
            return (0, 0, 0, 0)
        edge = (hw - du) * 16.0
        c = mix(pink_white, pink, 0.3 + 0.5 * t)
        c = mix(c, rose, 0.35 * t ** 2)
        if edge < 1.2:
            c = mix(c, WHITE, 0.55)
        a = 205 if edge >= 1.2 else 245
        return (c[0], c[1], c[2], a)
    tex("aura_ns_petal", 16, petal)

    def pod(u, v):
        d = math.hypot(u - 0.5, v - 0.5)
        c = mix(rgb("FFF2C0"), rgb("E8B040"), clamp01(d * 1.6))
        for k in range(6):
            a = math.tau * k / 6.0
            if math.hypot(u - 0.5 - 0.25 * math.cos(a), v - 0.5 - 0.25 * math.sin(a)) < 0.08:
                c = mix(c, WHITE, 0.6)
        return (c[0], c[1], c[2], 255)
    tex("aura_ns_pod", 16, pod)

    def pad(u, v):
        d = math.hypot(u - 0.5, v - 0.5)
        c = mix(pink_white, pink, clamp01(d * 1.4))
        return (c[0], c[1], c[2], 225)
    tex("aura_ns_pad", 16, pad)


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

    def el(self, f, t, faces, rot=None, glow=15):
        f = [round(min(f[i], t[i]), 4) for i in range(3)]
        t2 = [round(max(f[i], t[i]), 4) for i in range(3)]
        e = {"from": f, "to": t2, "faces": {}}
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

    def cube(self, c, size, name, rot=None, glow=15, sides=SIDES):
        f = [c[i] - size[i] / 2.0 for i in range(3)]
        t = [c[i] + size[i] / 2.0 for i in range(3)]
        return self.el(f, t, {s: (name, None) for s in sides}, rot=rot, glow=glow)


def all_faces(name):
    return {s: (name, None) for s in SIDES}


def norm_angle(angle):
    """A box direction angle (degrees) as (angle within -45..45, swapped)."""
    a = ((angle + 90.0) % 180.0) - 90.0
    if a > 45.0:
        return a - 90.0, True
    if a < -45.0:
        return a + 90.0, True
    return a, False


def oct_prism(m, c, r, y0, y1, side, top, glow=15):
    """An octagonal prism: two boxes, the second turned 45 degrees and a hair lower so no caps are coplanar."""
    faces = {"north": (side, None), "south": (side, None), "east": (side, None), "west": (side, None), "up": (top, None),
             "down": (side, None)}
    m.el([c[0] - r, y0, c[2] - r], [c[0] + r, y1, c[2] + r], faces, glow=glow)
    m.el([c[0] - r, y0 + 0.02, c[2] - r], [c[0] + r, y1 - 0.03, c[2] + r], faces, rot=("y", 45.0, list(c)), glow=glow)


# ================================================================================================ models

def fe_pillar_model():
    """A slim octagonal pillar of translucent light (22 px from its base at the anchor): a base ring, a glass shaft with
    a white-hot core, a capital and a jewel of light on top."""
    m = Model("aura_fe_pillar")
    c = (8.0, 8.0, 8.0)
    oct_prism(m, c, 2.6, 8.0, 9.4, "aura_fe_pillar_ring", "aura_fe_pillar_ring")
    oct_prism(m, c, 1.5, 9.4, 26.6, "aura_fe_pillar_glass", "aura_fe_pillar_glass")
    m.el([7.6, 9.6, 7.6], [8.4, 26.4, 8.4], all_faces("aura_fe_pillar_core"))
    oct_prism(m, c, 2.3, 26.6, 27.9, "aura_fe_pillar_ring", "aura_fe_pillar_ring")
    oct_prism(m, c, 1.6, 27.9, 28.9, "aura_fe_pillar_glass", "aura_fe_pillar_ring")
    m.cube((8.0, 29.6, 8.0), (1.1, 1.1, 1.1), "aura_fe_pillar_core", rot=("y", 45.0, [8.0, 29.6, 8.0]))
    return m


def gc_core_model():
    """The golden core: a faceted golden pearl (6 px) in a faint glass rim, centred on the anchor. No light emission:
    the prop breathes by its display light."""
    m = Model("aura_gc_core")
    c = (8.0, 8.0, 8.0)
    s = 4.2
    m.cube(c, (s, s, s), "aura_gc_core_gold", glow=0)
    m.cube(c, (s, s * 0.92, s), "aura_gc_core_gold", rot=("y", 45.0, list(c)), glow=0)
    m.cube(c, (s * 0.92, s, s), "aura_gc_core_gold", rot=("x", 45.0, list(c)), glow=0)
    m.cube(c, (s * 0.9, s * 0.9, s), "aura_gc_core_gold", rot=("z", 45.0, list(c)), glow=0)
    g = 6.0
    m.cube(c, (g, g, g), "aura_gc_core_glass", rot=("y", 22.5, list(c)), glow=0)
    return m


def gc_halo_model():
    """The great golden halo: a thin ring of radius 16 px (scale 1.2 gives r 1.2 b) in the XZ plane, 16 band segments,
    nine bead studs on it."""
    m = Model("aura_gc_halo")
    r, w, th, y = 16.0, 1.4, 0.6, 8.0
    length = 2.0 * math.pi * r / 16.0 * 1.06
    for i in range(16):
        theta = 22.5 * i
        px, pz = 8.0 + r * math.cos(math.radians(theta)), 8.0 + r * math.sin(math.radians(theta))
        a, swapped = norm_angle(-(theta + 90.0))
        k = 0.97 if i % 2 else 1.0
        if not swapped:
            size = (length, th * k, w * k)
            faces = {"up": ("aura_gc_halo_band", None), "down": ("aura_gc_halo_band", None),
                     "north": ("aura_gc_halo_band", (0, 6, 16, 10)), "south": ("aura_gc_halo_band", (0, 6, 16, 10))}
        else:
            size = (w * k, th * k, length)
            faces = {"up": ("aura_gc_halo_band_u", None), "down": ("aura_gc_halo_band_u", None),
                     "east": ("aura_gc_halo_band_u", (6, 0, 10, 16)), "west": ("aura_gc_halo_band_u", (6, 0, 10, 16))}
        f = [px - size[0] / 2, y - size[1] / 2, pz - size[2] / 2]
        t = [px + size[0] / 2, y + size[1] / 2, pz + size[2] / 2]
        m.el(f, t, faces, rot=("y", a, [px, y, pz]))
    for i in range(9):
        theta = math.radians(40.0 * i + 11.25)
        p = (8.0 + r * math.cos(theta), y, 8.0 + r * math.sin(theta))
        m.cube(p, (1.5, 1.5, 1.5), "aura_gc_bead", rot=("y", 45.0, list(p)))
    return m


def ns_infant_model():
    """The Nascent Soul: a small infant of violet-white glass seated cross-legged (10 px tall, its seat at the anchor),
    hands resting on the knees, a calm face with closed eyes, a topknot. No light emission: it breathes by display
    light."""
    m = Model("aura_ns_infant")
    # crossed legs: two shins lying across the front, turned toward each other
    m.el([5.0, 8.0, 6.6], [11.0, 9.6, 8.4], all_faces("aura_ns_glass"), rot=("y", 22.5, [8.0, 8.8, 7.5]), glow=0)
    m.el([5.0, 8.05, 6.65], [11.0, 9.55, 8.35], all_faces("aura_ns_glass"), rot=("y", -22.5, [8.0, 8.8, 7.5]), glow=0)
    # the body
    m.el([6.2, 9.2, 7.0], [9.8, 12.8, 9.2], all_faces("aura_ns_glass"), glow=0)
    # arms reaching forward and down to the knees (turned about the shoulders)
    for x0 in (5.2, 9.8):
        m.el([x0, 9.8, 7.6], [x0 + 1.0, 12.6, 8.6], all_faces("aura_ns_glass"), rot=("x", 22.5, [x0 + 0.5, 12.6, 8.1]), glow=0)
    # hands on the knees
    m.cube((5.6, 9.7, 6.7), (1.1, 0.8, 1.1), "aura_ns_glass", glow=0)
    m.cube((10.4, 9.7, 6.7), (1.1, 0.8, 1.1), "aura_ns_glass", glow=0)
    # the head (big, round) with the face to the north
    head = {"north": ("aura_ns_face", None), "south": ("aura_ns_glass", None), "east": ("aura_ns_glass", None),
            "west": ("aura_ns_glass", None), "up": ("aura_ns_hair", None), "down": ("aura_ns_glass", None)}
    m.el([6.0, 12.8, 6.1], [10.0, 16.8, 10.1], head, glow=0)
    m.el([6.3, 13.1, 6.4], [9.7, 16.5, 9.8], all_faces("aura_ns_glass"), rot=("y", 45.0, [8.0, 14.8, 8.1]), glow=0)
    # the topknot
    m.cube((8.0, 17.3, 8.4), (1.4, 1.0, 1.4), "aura_ns_hair", rot=("y", 45.0, [8.0, 17.3, 8.4]), glow=0)
    return m


def petal(m, theta, r, w, h, th, y0, lean=True):
    """One upright petal at angle theta (degrees from +x toward +z), its base r px out, w wide, h tall; cardinal petals
    lean outward 22.5 degrees (a single-axis turn), the others stand upright."""
    cx = 8.0 + r * math.cos(math.radians(theta))
    cz = 8.0 + r * math.sin(math.radians(theta))
    a, swapped = norm_angle(-theta)
    tex_name = "aura_ns_petal"
    if not swapped:
        size = (th, h, w)
        faces = {"east": (tex_name, None), "west": (tex_name, None)}
    else:
        size = (w, h, th)
        faces = {"north": (tex_name, None), "south": (tex_name, None)}
    f = [cx - size[0] / 2, y0, cz - size[2] / 2]
    t = [cx + size[0] / 2, y0 + h, cz + size[2] / 2]
    rot = None
    if a != 0.0:
        rot = ("y", a, [cx, y0, cz])
    elif lean:
        # radial along x (not swapped) leans about z; along z leans about x
        rx, rz = math.cos(math.radians(theta)), math.sin(math.radians(theta))
        if not swapped:
            rot = ("z", -22.5 if rx > 0 else 22.5, [cx, y0, cz])
        else:
            rot = ("x", 22.5 if rz > 0 else -22.5, [cx, y0, cz])
    m.el(f, t, faces, rot=rot)


def ns_lotus_model(mid, sixteen):
    """The lotus seat (12 px across): a pale pad, a golden seed pod, eight petals (sixteen: a second, inner ring turned
    22.5 degrees)."""
    m = Model(mid)
    c = (8.0, 8.0, 8.0)
    oct_prism(m, c, 3.8, 8.0, 8.5, "aura_ns_pad", "aura_ns_pad")
    oct_prism(m, c, 1.5, 8.5, 9.6, "aura_ns_pod", "aura_ns_pod")
    for k in range(8):
        petal(m, 45.0 * k, 3.6, 3.4, 4.2 if sixteen else 3.8, 0.3, 8.3)
    if sixteen:
        for k in range(8):
            petal(m, 45.0 * k + 22.5, 2.2, 2.6, 3.2, 0.3, 8.5, lean=False)
    return m


def build_models():
    fe_pillar_model()
    gc_core_model()
    gc_halo_model()
    ns_infant_model()
    ns_lotus_model("aura_ns_lotus", False)
    ns_lotus_model("aura_ns_lotus16", True)


# ================================================================================================ checks and output

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
        le = e.get("light_emission", 0)
        if not (0 <= le <= 15):
            problems.append("element %d light_emission %s" % (i, le))
    for name in m.tex:
        if name not in TEXTURES:
            problems.append("texture %s missing" % name)
    return problems


def check_quad(name, img):
    """Quad rule: alpha 0 or >= 28, and a clear border (glows fade out inside it)."""
    problems = []
    w, h = img.width, img.height
    for y in range(h):
        for x in range(w):
            a = img.px[y * w + x][3]
            if 0 < a < 28:
                problems.append("alpha %d at %d,%d" % (a, x, y))
                return problems
    edge = [img.px[x][3] for x in range(w)] + [img.px[(h - 1) * w + x][3] for x in range(w)]
    edge += [img.px[y * w][3] for y in range(h)] + [img.px[y * w + w - 1][3] for y in range(h)]
    if name not in ("band_soft", "gc_halo", "fe_pillar") and max(edge) > 0:
        problems.append("border not clear (max alpha %d)" % max(edge))
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
    build_quads()
    build_textures()
    build_models()
    errors = 0
    os.makedirs(QUAD_DIR, exist_ok=True)
    for name, img in sorted(QUADS.items()):
        problems = check_quad(name, img)
        if problems:
            errors += 1
            print("QUAD %s: %s" % (name, "; ".join(problems)))
        img.save(os.path.join(QUAD_DIR, name + ".png"))
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
        print("  %-20s %2d elements  %s" % (mid, len(m.els), ", ".join(sorted(m.tex))))
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    with open(IDS_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(MODELS)) + "\n")
    print("aura assets: %d quad textures, %d models, %d prop textures, errors: %d" % (len(QUADS), len(MODELS), len(used), errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
