"""Generates the 3D models and textures of module B, Naga dan Binatang (DESIGN 34.5.11 Naga Liar, 34.5.12 Evolusi Binatang
Roh). Everything here is ours: cuboid item models and procedural textures, nothing is downloaded.

Writes (assets/kultivator/...):
  textures/item/fx/naga/*.png        scale, belly, fin, eye, horn, mane, feather, fur and glow textures (32x32 / 64x64)
  models/item/fx/naga/<id>.json      one cuboid model per body part (kultivator:item/fx/naga/<id>)
  items/fx/naga/<id>.json            the item definitions (kultivator:fx/naga/<id>, shown through DataComponents.ITEM_MODEL)
  textures/dunia/naga/*.png          the star-silver / moon / gold overlays drawn over tier 4-6 wolves and foxes
  textures/item/<item>.png, models/item/<item>.json, items/<item>.json
                                     the four inventory items (selendang, sisik, mutiara, mutiara jinak)
  tools/fx_model_ids_naga.txt        this module's id list (tools/fx_model_ids.txt is never touched)

The Jiao (three kinds: air = azure water Jiao, awan = white-jade cloud Jiao, petir = purple-gold thunder Jiao) is drawn by
the client renderer as a chain: head + jaw, 6-20 body segments (every third with a dorsal sail), two pairs of claws, a
tail fin, two whiskers of five strands each; tier 4 adds antlers, tier 5 a mane, tier 6 (Yinglong) two feathered wings.
Spirit foxes of tier 4-6 carry nine model tails (moon silver, sky white-gold, gold); wolves get a moon halo (tier 5) or an
eclipse halo (tier 6) and every tier 4-6 beast an emissive overlay texture.

Rules kept by every model (checked by validate()):
  * the anchor is (8, 8, 8) (head centre, segment centre, shoulder, tail joint, horn base, halo centre);
  * the front faces north (-z): a head's snout and a talon point to -z, a tail, whisker or feather trails to +z;
  * element coordinates within -16..32, uv within 0..16, at most 40 elements;
  * an element rotation is a single axis by -45, -22.5, 22.5 or 45 degrees
    (Y by +a turns +x towards -z, X by +a turns +y towards +z, Z by +a turns +x towards +y);
  * face uv is scaled to the face size (2 texels per model pixel on a 32 px texture), so small parts are never smeared.
Left parts (l) sit on the creature's left, which is west (-x) when it faces north; right parts are exact mirrors.

Run with: python tools/gen_dunia_naga_models.py [preview_dir]
  (validates every model and texture, prints a summary; with preview_dir it also writes 8x texture previews there)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx", "naga")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx", "naga")
ITEM_DIR = os.path.join(ROOT, "items", "fx", "naga")
OVERLAY_DIR = os.path.join(ROOT, "textures", "dunia", "naga")
ICON_TEX_DIR = os.path.join(ROOT, "textures", "item")
ICON_MODEL_DIR = os.path.join(ROOT, "models", "item")
ICON_ITEM_DIR = os.path.join(ROOT, "items")
IDS_FILE = os.path.join(HERE, "fx_model_ids_naga.txt")
NS = "kultivator"

TEXTURES = {}
MODELS = {}
OVERLAYS = {}
ICONS = {}


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


def make(size_w, size_h, fn):
    img = Image(size_w, size_h)
    for y in range(size_h):
        for x in range(size_w):
            c = fn((x + 0.5) / size_w, (y + 0.5) / size_h)
            img.set(x, y, (c[0], c[1], c[2], int(max(0, min(255, round(c[3]))))))
    return img


def tex(name, size, fn):
    """A model texture: fn(u, v) -> (r, g, b, a), u, v the pixel centre in 0..1 (v = 0 at the top)."""
    TEXTURES[name] = make(size, size, fn)
    return name


# ================================================================================================ the three Jiao kinds

KINDS = {
    # base, dark, highlight (scale rim), belly, belly dark, fin, fin rim, eye, mane, whisker
    "air": dict(base="2A6FC9", dark="123A78", rim="8FE0FF", belly="CDEFF4", belly_dark="7FB8C8",
                fin="3FD8D0", fin_rim="E0FFFF", eye="FFD54A", mane="E6FFFF", whisker="D8FFFF", seed=11),
    "awan": dict(base="EEF5EC", dark="8FBFA6", rim="FFFFFF", belly="FFF8E8", belly_dark="D6C8A8",
                 fin="BFE8D6", fin_rim="FFFFFF", eye="6FD0FF", mane="F6FFF8", whisker="F0FFF6", seed=23),
    "petir": dict(base="5A2D9E", dark="2A1050", rim="FFCF4A", belly="E9C25A", belly_dark="9C7420",
                  fin="A060FF", fin_rim="FFE08A", eye="D8F4FF", mane="FFD86A", whisker="FFE48A", seed=37),
}


def scale_texture(name, k):
    """Overlapping dragon scales: staggered rows of rounded scales, each lower row overlapping the one above, a soft
    light centre, a bright rim just inside the edge and a dark shadow line where the next scale lies over it."""
    base, dark, rim = rgb(k["base"]), rgb(k["dark"]), rgb(k["rim"])
    n = Noise(k["seed"])
    cols, rows = 4, 4
    cw, rh = 1.0 / cols, 1.0 / rows
    radius = 0.80 * cw

    def fn(u, v):
        best = None
        row0 = int(math.floor(v / rh))
        for r in range(row0 + 1, row0 - 2, -1):
            cy = r * rh
            off = 0.5 * cw if r % 2 else 0.0
            for c in range(-1, cols + 1):
                cx = c * cw + off + cw * 0.5
                # wrap horizontally so the texture tiles
                dx = (u - cx + 0.5) % 1.0 - 0.5
                dy = v - cy
                if dy < -0.25 * rh:
                    continue
                d = math.hypot(dx, dy * 1.55)
                if d < radius:
                    best = (d / radius, dx / radius, dy / radius)
                    break
            if best:
                break
        if best is None:
            col = mix(base, dark, 0.6)
        else:
            t, sx, sy = best
            col = mix(base, rim, 0.30 * clamp01(1.0 - math.hypot(sx + 0.15, sy + 0.1) * 1.6))
            col = mix(col, dark, 0.45 * t ** 2.2)
            col = mix(col, rim, 0.55 * smooth(0.70, 0.84, t) * (1.0 - smooth(0.86, 0.92, t)))
            col = mix(col, mix(dark, (0, 0, 0), 0.35), smooth(0.90, 0.99, t))
        col = mix(col, (0, 0, 0), 0.10 * (n.fbm(u * 6.0, v * 6.0) - 0.45))
        return (col[0], col[1], col[2], 255)
    return tex(name, 32, fn)


def belly_texture(name, k):
    """Belly plates: wide horizontal bands with darker seams and a soft sheen."""
    belly, seam = rgb(k["belly"]), rgb(k["belly_dark"])
    n = Noise(k["seed"] + 1)

    def fn(u, v):
        band = (v * 5.0) % 1.0
        c = mix(belly, seam, smooth(0.78, 0.98, band) + smooth(0.10, 0.0, band) * 0.5)
        c = mix(c, (255, 255, 255), 0.18 * math.exp(-((u - 0.5) / 0.22) ** 2) * (1.0 - band))
        c = mix(c, seam, 0.15 * (n.fbm(u * 8.0, v * 3.0) - 0.4))
        return (c[0], c[1], c[2], 255)
    return tex(name, 32, fn)


def fin_texture(name, k):
    """A fin membrane: translucent, with bright ribs running from the base (bottom) to the frayed tip (top)."""
    fin, rimc = rgb(k["fin"]), rgb(k["fin_rim"])
    n = Noise(k["seed"] + 2)

    def fn(u, v):
        t = 1.0 - v  # 0 at the base, 1 at the tip
        ribs = abs(math.sin(u * math.pi * 5.0))
        rib = smooth(0.92, 1.0, ribs)
        c = mix(fin, rimc, 0.25 * t + 0.6 * rib)
        a = 165.0 + 70.0 * rib - 70.0 * t
        fray = n.fbm(u * 10.0, 3.0) * 0.25
        if t > 0.82 + fray:
            a *= 0.25
        return (c[0], c[1], c[2], a)
    return tex(name, 32, fn)


def eye_texture(name, k):
    """A glowing iris with a vertical slit pupil."""
    iris = rgb(k["eye"])

    def fn(u, v):
        d = math.hypot(u - 0.5, v - 0.5)
        c = mix((255, 255, 240), iris, clamp01(d * 2.4))
        c = mix(c, (iris[0] * 0.55, iris[1] * 0.45, iris[2] * 0.3), smooth(0.36, 0.5, d))
        if abs(u - 0.5) < 0.07 + 0.05 * (1.0 - abs(v - 0.5) * 2.0) and abs(v - 0.5) < 0.42:
            c = (20, 10, 8)
        return (c[0], c[1], c[2], 255)
    return tex(name, 16, fn)


def mane_texture(name, k):
    """Long hair strands (alpha falls off toward the tips, at the top)."""
    mane = rgb(k["mane"])
    deep = mix(mane, rgb(k["dark"]), 0.35)
    r = random.Random(k["seed"] + 3)
    strands = [(r.random(), 0.55 + 0.45 * r.random(), r.random()) for _ in range(18)]

    def fn(u, v):
        t = 1.0 - v
        best = 0.0
        shade = 0.0
        for su, length, tone in strands:
            w = 0.035 * (1.0 - 0.6 * t)
            du = abs(u - su - 0.04 * math.sin(t * 5.0 + tone * 6.0))
            if du < w and t < length:
                k2 = (1.0 - du / w) * (1.0 - smooth(length * 0.7, length, t))
                if k2 > best:
                    best = k2
                    shade = tone
        c = mix(deep, mane, 0.4 + 0.6 * shade)
        return (c[0], c[1], c[2], 255.0 * best)
    return tex(name, 32, fn)


def whisker_texture(name, k):
    col = rgb(k["whisker"])

    def fn(u, v):
        a = 255.0 * math.exp(-((u - 0.5) / 0.32) ** 2)
        c = mix((255, 255, 255), col, abs(u - 0.5) * 2.0)
        return (c[0], c[1], c[2], a)
    return tex(name, 16, fn)


def feather_texture(name, k):
    """One long feather: white rachis, vanes from white to the kind colour, a gold rim."""
    white, body, gold = (255, 255, 250), mix(rgb(k["fin"]), rgb(k["base"]), 0.4), rgb("FFD86A")
    n = Noise(k["seed"] + 4)

    def fn(u, v):
        du = abs(u - 0.5)
        t = 1.0 - v
        half = 0.5 * (1.0 - 0.35 * t ** 2)
        if du > half:
            return (0, 0, 0, 0)
        c = mix(white, body, smooth(0.04, half * 0.9, du))
        c = mix(c, gold, smooth(half * 0.8, half, du))
        barbs = abs(math.sin((du * 3.0 + t) * 22.0))
        c = mix(c, (0, 0, 0), 0.08 * barbs + 0.06 * (n.fbm(u * 6, v * 6) - 0.4))
        if du < 0.035:
            c = white
        return (c[0], c[1], c[2], 245.0)
    return tex(name, 32, fn)


def build_kind_textures():
    for key, k in KINDS.items():
        scale_texture("scale_" + key, k)
        belly_texture("belly_" + key, k)
        fin_texture("fin_" + key, k)
        eye_texture("eye_" + key, k)
        mane_texture("mane_" + key, k)
        whisker_texture("whisker_" + key, k)
        feather_texture("feather_" + key, k)
        # the lateral line: the scale texture a little darker
        dk = dict(k)
        dk["base"] = "%02X%02X%02X" % tuple(int(c) for c in mix(rgb(k["base"]), rgb(k["dark"]), 0.45))
        scale_texture("scale_dark_" + key, dk)


def build_shared_textures():
    n = Noise(5)

    def dark(u, v):
        c = mix(rgb("3A0A0E"), rgb("120406"), v)
        return (c[0], c[1], c[2], 255)
    tex("mouth", 16, dark)

    def tongue(u, v):
        c = mix(rgb("E0506A"), rgb("8A1A30"), abs(u - 0.5) * 2.0)
        return (c[0], c[1], c[2], 255)
    tex("tongue", 16, tongue)

    def tooth(u, v):
        c = mix(rgb("FFFBEA"), rgb("D8CBA0"), v * 0.8)
        return (c[0], c[1], c[2], 255)
    tex("tooth", 16, tooth)

    def horn(u, v):
        t = 1.0 - v
        ring = smooth(0.85, 1.0, abs(math.sin(t * math.pi * 6.0)))
        c = mix(rgb("F4E8CC"), rgb("B89A68"), 0.35 * ring + 0.25 * (n.fbm(u * 4, v * 8) - 0.3))
        c = mix(c, rgb("FFD54A"), smooth(0.75, 1.0, t))
        return (c[0], c[1], c[2], 255)
    tex("horn", 32, horn)

    def horn_gold(u, v):
        c = mix(rgb("FFF4C0"), rgb("FFB830"), math.hypot(u - 0.4, v - 0.4) * 1.6)
        return (c[0], c[1], c[2], 255)
    tex("horn_gold", 16, horn_gold)

    def pearl(u, v):
        d = math.hypot(u - 0.42, v - 0.38)
        c = mix(rgb("FFFFFF"), rgb("8FD8FF"), clamp01(d * 1.7))
        c = mix(c, rgb("FFE9A0"), 0.25 * clamp01(1.0 - abs(u + v - 1.0) * 3.0))
        return (c[0], c[1], c[2], 255)
    tex("pearl", 32, pearl)

    def pearl_aura(u, v):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        a = 200.0 * math.exp(-((d - 0.72) / 0.16) ** 2) + 60.0 * clamp01(1.0 - d)
        c = mix(rgb("FFFFFF"), rgb("9FE0FF"), d)
        return (c[0], c[1], c[2], a if d < 1.0 else 0.0)
    tex("pearl_aura", 32, pearl_aura)

    def halo(core, edge, inner_alpha):
        c0, c1 = rgb(core), rgb(edge)

        def fn(u, v):
            d = math.hypot(u - 0.5, v - 0.5) * 2.0
            if d >= 1.0:
                return (0, 0, 0, 0)
            ring = math.exp(-((d - 0.80) / 0.07) ** 2)
            glow = math.exp(-((d - 0.80) / 0.22) ** 2)
            ang = math.atan2(v - 0.5, u - 0.5)
            ticks = smooth(0.94, 1.0, abs(math.cos(ang * 6.0))) * smooth(0.86, 0.9, d) * (1.0 - smooth(0.95, 1.0, d))
            c = mix(c1, c0, ring)
            a = 250.0 * ring + 110.0 * glow + inner_alpha * (1.0 - d) + 180.0 * ticks
            return (c[0], c[1], c[2], min(255.0, a))
        return fn
    tex("halo_moon", 64, halo("FFFFFF", "BFD8FF", 30.0))
    tex("halo_gold", 64, halo("FFF6D0", "FFB840", 10.0))

    def eclipse(u, v):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        if d >= 1.0:
            return (0, 0, 0, 0)
        # the dark moon is being swallowed: a black disc, offset, leaves a burning crescent of the moon behind
        dd = math.hypot(u - 0.42, v - 0.46) * 2.0
        moon = d < 0.70
        if moon:
            if dd < 0.62:
                c = mix(rgb("120A20"), rgb("2A1848"), dd)
                return (c[0], c[1], c[2], 245)
            c = mix(rgb("FFF4D0"), rgb("FFB030"), clamp01((0.70 - d) * 6.0))
            return (c[0], c[1], c[2], 255)
        corona = math.exp(-((d - 0.72) / 0.12) ** 2)
        c = mix(rgb("FF9A30"), rgb("FFE8A0"), corona)
        return (c[0], c[1], c[2], 220.0 * corona)
    tex("eclipse_disc", 64, eclipse)

    def fur(top, mid, tip_col, seed):
        nn = Noise(seed)
        c0, c1, c2 = rgb(top), rgb(mid), rgb(tip_col)

        def fn(u, v):
            strands = nn.fbm(u * 12.0, v * 2.5)
            c = mix(c0, c1, v)
            c = mix(c, c2, smooth(0.55, 0.75, strands) * 0.4)
            c = mix(c, (0, 0, 0), 0.10 * (0.5 - strands))
            return (c[0], c[1], c[2], 255)
        return fn

    def fur_tip(core, edge):
        c0, c1 = rgb(core), rgb(edge)

        def fn(u, v):
            d = math.hypot(u - 0.5, v - 0.5) * 2.0
            c = mix(c0, c1, d)
            return (c[0], c[1], c[2], 255)
        return fn
    tex("fur_moon", 32, fur("DDE8FF", "8FA8E0", "FFFFFF", 41))
    tex("fur_moon_tip", 16, fur_tip("FFFFFF", "C8E0FF"))
    tex("fur_sky", 32, fur("FFF8EA", "F2D8A0", "FFFFFF", 43))
    tex("fur_sky_tip", 16, fur_tip("FFFFFF", "FFF0B0"))
    tex("fur_gold", 32, fur("FFE07A", "E8A020", "FFF6C8", 47))
    tex("fur_gold_tip", 16, fur_tip("FFFFF0", "FFD040"))

    # detail pass: the burning eyes of Amukan Naga, the moon in the Tiangou's jaws, the star-silver ruff, the great antler
    def eye_rage(u, v):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        c = mix(rgb("FFF4C0"), rgb("FF3A20"), clamp01(d * 1.3))
        a = 255.0 * clamp01(1.25 - d)
        return (c[0], c[1], c[2], a)
    tex("eye_rage", 16, eye_rage)

    def flame(u, v):
        t = 1.0 - v
        w = 0.42 * (1.0 - t) + 0.05
        du = abs(u - 0.5 - 0.08 * math.sin(t * 7.0))
        if du > w:
            return (0, 0, 0, 0)
        c = mix(rgb("FFE08A"), rgb("FF2A10"), clamp01(t * 1.2 + du * 1.5))
        return (c[0], c[1], c[2], 230.0 * (1.0 - t) * (1.0 - du / w))
    tex("rage_flame", 16, flame)

    nm = Noise(59)

    def moon(u, v):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        c = mix(rgb("FFF8DC"), rgb("E8D8A8"), d * 0.6)
        craters = nm.fbm(u * 5.0, v * 5.0)
        c = mix(c, rgb("B8A878"), smooth(0.55, 0.7, craters) * 0.6)
        # the bite of the Moon-Devourer: a dark notch on one side
        if math.hypot(u - 0.95, v - 0.3) < 0.32:
            c = mix(c, rgb("20142E"), 0.85)
        return (c[0], c[1], c[2], 255)
    tex("moon_orb", 32, moon)

    ns = Noise(61)
    rs = random.Random(61)
    stars = [(rs.random(), rs.random()) for _ in range(9)]

    def fur_star(u, v):
        strands = ns.fbm(u * 12.0, v * 2.5)
        c = mix(rgb("EEF2FF"), rgb("8C9CD8"), v * 0.8)
        c = mix(c, rgb("FFFFFF"), smooth(0.55, 0.75, strands) * 0.35)
        for sx, sy in stars:
            dd = abs(u - sx) + abs(v - sy)
            if dd < 0.07:
                c = mix(c, rgb("FFFFFF"), 1.0 - dd / 0.07)
        return (c[0], c[1], c[2], 255)
    tex("fur_star", 32, fur_star)

    def horn_great(u, v):
        t = 1.0 - v
        ring = smooth(0.85, 1.0, abs(math.sin(t * math.pi * 8.0)))
        c = mix(rgb("FFF0C8"), rgb("C89A50"), 0.4 * ring + 0.3 * (n.fbm(u * 4, v * 8) - 0.3))
        c = mix(c, rgb("FFD54A"), smooth(0.55, 1.0, t))
        return (c[0], c[1], c[2], 255)
    tex("horn_great", 32, horn_great)


# ================================================================================================ the model DSL

SIDES = ("north", "south", "east", "west", "up", "down")
OPPOSITE_X = {"east": "west", "west": "east"}


class Model:
    def __init__(self, mid):
        self.id = mid
        self.els = []
        self.tex = {}
        MODELS[mid] = self

    def key(self, name):
        if name not in self.tex:
            self.tex[name] = "t%d" % len(self.tex)
        return self.tex[name]

    def box(self, f, t, skin, rot=None, glow=0, sides=SIDES, overrides=None):
        """A cuboid from f to t (model pixels) with texture skin on every side in {sides}; overrides: side -> texture.
        rot: (axis, angle, origin). The face uv covers as many texture pixels as the face is wide (2 texels per pixel
        on a 32 px texture), offset by the element index so neighbouring parts do not repeat the same patch."""
        f = [round(min(f[i], t[i]), 4) for i in range(3)]
        t2 = [round(max(f[i], t[i]), 4) for i in range(3)]
        e = {"from": f, "to": t2, "faces": {}}
        idx = len(self.els)
        dims = {"north": (t2[0] - f[0], t2[1] - f[1]), "south": (t2[0] - f[0], t2[1] - f[1]),
                "east": (t2[2] - f[2], t2[1] - f[1]), "west": (t2[2] - f[2], t2[1] - f[1]),
                "up": (t2[0] - f[0], t2[2] - f[2]), "down": (t2[0] - f[0], t2[2] - f[2])}
        for side in sides:
            name = (overrides or {}).get(side, skin)
            if name is None:
                continue
            w, h = dims[side]
            w = max(0.5, min(16.0, w))
            h = max(0.5, min(16.0, h))
            u0 = ((idx * 5 + SIDES.index(side) * 3) % 16) * (16.0 - w) / 16.0
            v0 = ((idx * 3 + SIDES.index(side) * 7) % 16) * (16.0 - h) / 16.0
            e["faces"][side] = {"uv": [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)],
                                "texture": "#" + self.key(name)}
        if rot:
            axis, angle, origin = rot
            if angle != 0:
                e["rotation"] = {"origin": [round(x, 4) for x in origin], "axis": axis, "angle": angle}
        if glow:
            e["light_emission"] = int(glow)
        self.els.append(e)
        return e

    def full(self, f, t, skin, rot=None, glow=0, sides=SIDES):
        """Like box, but every face shows the whole texture (discs, halos, feathers: textures drawn to fit)."""
        e = self.box(f, t, skin, rot=rot, glow=glow, sides=sides)
        for face in e["faces"].values():
            face["uv"] = [0, 0, 16, 16]
        return e

    def mirrored(self, mid):
        """A copy mirrored across x = 8 (left part -> right part)."""
        m = Model(mid)
        for e in self.els:
            f, t = e["from"], e["to"]
            ne = {"from": [round(16.0 - t[0], 4), f[1], f[2]], "to": [round(16.0 - f[0], 4), t[1], t[2]], "faces": {}}
            for side, face in e["faces"].items():
                side2 = OPPOSITE_X.get(side, side)
                name = next(n for n, k in self.tex.items() if "#" + k == face["texture"])
                uv = face["uv"]
                if side in ("north", "south", "up", "down"):
                    uv = [uv[2], uv[1], uv[0], uv[3]]
                ne["faces"][side2] = {"uv": uv, "texture": "#" + m.key(name)}
            rot = e.get("rotation")
            if rot:
                o = rot["origin"]
                angle = rot["angle"] if rot["axis"] == "x" else -rot["angle"]
                ne["rotation"] = {"origin": [round(16.0 - o[0], 4), o[1], o[2]], "axis": rot["axis"], "angle": angle}
            if "light_emission" in e:
                ne["light_emission"] = e["light_emission"]
            m.els.append(ne)
        return m


# ================================================================================================ Jiao models

def head_model(key):
    m = Model("head_" + key)
    s, b, fin, eye = "scale_" + key, "belly_" + key, "fin_" + key, "eye_" + key
    # cranium and the back of the skull
    m.box((3.0, 6.0, 2.0), (13.0, 12.5, 11.0), s, overrides={"down": b})
    m.box((4.0, 6.5, 11.0), (12.0, 12.0, 13.5), s)
    # upper snout (its underside is the roof of the mouth) and the raised bridge
    m.box((4.2, 6.5, -6.0), (11.8, 10.5, 2.0), s, overrides={"down": "mouth"})
    m.box((6.0, 10.5, -5.0), (10.0, 11.3, 3.0), s)
    # nose pad and nostrils
    m.box((4.6, 7.0, -8.2), (11.4, 10.3, -6.0), s, overrides={"down": "mouth"})
    m.box((5.0, 9.6, -8.4), (6.6, 10.4, -7.4), "mouth")
    m.box((9.4, 9.6, -8.4), (11.0, 10.4, -7.4), "mouth")
    # brow ridges sloping down to the snout
    m.box((2.6, 11.2, 1.0), (6.8, 12.8, 5.5), s, rot=("x", -22.5, (4.7, 12.0, 5.5)))
    m.box((9.2, 11.2, 1.0), (13.4, 12.8, 5.5), s, rot=("x", -22.5, (11.3, 12.0, 5.5)))
    # eyes (glowing)
    m.box((2.4, 9.1, 2.4), (3.4, 10.7, 5.2), eye, glow=12)
    m.box((12.6, 9.1, 2.4), (13.6, 10.7, 5.2), eye, glow=12)
    # cheek frills flaring back
    m.box((1.2, 6.4, 5.5), (3.0, 10.6, 11.5), fin, rot=("y", 22.5, (3.0, 8.0, 5.5)))
    m.box((13.0, 6.4, 5.5), (14.8, 10.6, 11.5), fin, rot=("y", -22.5, (13.0, 8.0, 5.5)))
    # upper teeth and fangs
    m.box((4.7, 5.9, -7.6), (11.3, 6.5, 0.5), "tooth")
    m.box((4.8, 4.6, -6.8), (5.6, 6.0, -6.0), "tooth")
    m.box((10.4, 4.6, -6.8), (11.2, 6.0, -6.0), "tooth")
    # horn nubs (a Jiao is a dragon without true horns yet)
    m.box((4.4, 12.3, 6.5), (5.9, 15.3, 8.0), "horn", rot=("x", 22.5, (5.15, 12.3, 7.25)))
    m.box((10.1, 12.3, 6.5), (11.6, 15.3, 8.0), "horn", rot=("x", 22.5, (10.85, 12.3, 7.25)))
    # dorsal crest spines leaning back
    m.box((7.4, 12.4, 4.0), (8.6, 14.4, 6.5), fin, rot=("x", 22.5, (8.0, 12.4, 5.25)))
    m.box((7.4, 12.4, 7.5), (8.6, 15.4, 10.0), fin, rot=("x", 22.5, (8.0, 12.4, 8.75)))
    m.box((7.4, 12.0, 10.5), (8.6, 14.6, 13.0), fin, rot=("x", 22.5, (8.0, 12.0, 11.75)))
    # tufts of mane behind the jaw
    m.box((2.6, 6.8, 12.0), (4.0, 11.5, 15.5), "mane_" + key, rot=("y", 22.5, (4.0, 9.0, 12.0)))
    m.box((12.0, 6.8, 12.0), (13.4, 11.5, 15.5), "mane_" + key, rot=("y", -22.5, (12.0, 9.0, 12.0)))
    return m


def jaw_model(key):
    """The lower jaw, hinged at (8, 8, 8): the renderer opens it about x for the breath and the roar."""
    m = Model("jaw_" + key)
    s, b = "scale_" + key, "belly_" + key
    m.box((4.6, 5.6, -6.0), (11.4, 8.0, 9.0), s, overrides={"down": b, "up": "mouth"})
    m.box((5.2, 5.0, -6.6), (10.8, 6.6, -3.0), s, overrides={"down": b})
    m.box((4.9, 8.0, -5.6), (11.1, 8.6, 1.5), "tooth")
    m.box((5.0, 8.0, -5.4), (5.8, 9.3, -4.6), "tooth")
    m.box((10.2, 8.0, -5.4), (11.0, 9.3, -4.6), "tooth")
    m.box((6.8, 8.0, -4.0), (9.2, 8.3, 6.0), "tongue")
    # the chin beard
    m.box((6.5, 2.0, -4.5), (9.5, 5.0, -1.5), "mane_" + key, rot=("x", 22.5, (8.0, 5.0, -3.0)))
    m.box((5.0, 2.8, -2.0), (6.6, 5.2, 1.5), "mane_" + key, rot=("x", 22.5, (5.8, 5.2, -0.25)))
    m.box((9.4, 2.8, -2.0), (11.0, 5.2, 1.5), "mane_" + key, rot=("x", 22.5, (10.2, 5.2, -0.25)))
    return m


def body_model(key, sail):
    """One body segment, centred on (8, 8, 8), 12 px long along z: rounded by two bulges, belly plates below, a dorsal
    spine (or a tall sail on every third segment) on top and a darker lateral line on both flanks."""
    m = Model(("sail_" if sail else "body_") + key)
    s, b, fin, sd = "scale_" + key, "belly_" + key, "fin_" + key, "scale_dark_" + key
    m.box((3.5, 3.5, 2.0), (12.5, 12.5, 14.0), s, overrides={"down": b})
    m.box((2.5, 5.0, 2.5), (13.5, 11.0, 13.5), s, overrides={"down": b})
    m.box((5.0, 2.5, 2.5), (11.0, 13.5, 13.5), s, overrides={"down": b})
    m.box((4.5, 2.2, 2.6), (11.5, 3.0, 13.4), b)
    m.box((2.2, 7.6, 3.0), (2.6, 8.8, 13.0), sd)
    m.box((13.4, 7.6, 3.0), (13.8, 8.8, 13.0), sd)
    m.box((5.5, 13.4, 3.0), (10.5, 13.8, 13.0), s)
    if sail:
        m.box((7.6, 13.2, 3.0), (8.4, 19.0, 12.0), fin, rot=("x", 22.5, (8.0, 13.2, 7.5)))
        m.box((7.4, 13.2, 9.5), (8.6, 16.0, 13.5), fin, rot=("x", 22.5, (8.0, 13.2, 11.5)))
    else:
        m.box((7.3, 13.3, 5.0), (8.7, 16.5, 9.0), fin, rot=("x", 22.5, (8.0, 13.3, 7.0)))
    return m


def claw_model(key):
    """A left foreleg, shoulder at (8, 8, 8): the arm reaches out (west) and down, four talons point forward (north)."""
    m = Model("claw_l_" + key)
    s, b, fin = "scale_" + key, "belly_" + key, "fin_" + key
    m.box((5.0, 6.0, 6.0), (9.0, 10.0, 10.0), s)
    m.box((1.5, 3.5, 6.5), (5.5, 7.0, 9.5), s, rot=("z", 22.5, (5.0, 7.0, 8.0)))
    m.box((0.5, -2.0, 6.8), (3.0, 4.0, 9.2), s)
    m.box((-0.5, 1.0, 6.0), (1.0, 3.5, 10.0), fin)
    m.box((-0.2, -3.2, 4.5), (3.6, -1.6, 9.6), s, overrides={"down": b})
    for i, x in enumerate((-0.2, 0.8, 1.8, 2.8)):
        reach = 1.0 if i in (1, 2) else 1.8
        m.box((x, -3.6, reach), (x + 0.8, -2.6, 4.8), "tooth", rot=("x", -22.5, (x + 0.4, -3.1, 4.8)))
    m.box((1.0, -3.4, 9.6), (1.8, -2.6, 11.5), "tooth")
    return m


def tail_model(key):
    """The tail fin, joint at (8, 8, 8): an upper and a lower lobe, two side flukes and a hair tuft, trailing south."""
    m = Model("tail_" + key)
    s, fin = "scale_" + key, "fin_" + key
    m.box((5.5, 5.5, 2.0), (10.5, 10.5, 10.0), s, overrides={"down": "belly_" + key})
    m.full((7.7, 10.0, 4.0), (8.3, 20.0, 18.0), fin, rot=("x", 22.5, (8.0, 10.0, 6.0)))
    m.full((7.7, -2.0, 6.0), (8.3, 6.0, 17.0), fin, rot=("x", -22.5, (8.0, 6.0, 8.0)))
    m.full((-4.0, 7.7, 5.0), (6.0, 8.3, 19.0), fin, rot=("y", -22.5, (6.0, 8.0, 6.0)))
    m.full((10.0, 7.7, 5.0), (20.0, 8.3, 19.0), fin, rot=("y", 22.5, (10.0, 8.0, 6.0)))
    m.full((7.7, 4.0, 9.0), (8.3, 12.0, 24.0), fin)
    m.box((6.5, 6.5, 9.0), (9.5, 9.5, 13.0), "mane_" + key)
    return m


def whisker_model(key):
    """One whisker strand, 5 px long trailing south from (8, 8, 8); the renderer chains five and bends them."""
    m = Model("whisker_" + key)
    m.full((7.6, 7.6, 8.0), (8.4, 8.4, 13.0), "whisker_" + key, glow=10)
    return m


def mane_model(key):
    """The neck mane of a true dragon (tier 5+): a fringe of hair sheets along the spine plus side tufts."""
    m = Model("mane_" + key)
    hair = "mane_" + key
    for i, z in enumerate((2.0, 4.5, 7.0, 9.5, 12.0)):
        height = 7.0 + (1.5 if i % 2 else 0.0)
        m.full((7.7, 8.0, z), (8.3, 8.0 + height, z + 3.0), hair, rot=("x", 22.5, (8.0, 8.0, z + 1.5)))
    m.full((3.0, 7.0, 3.0), (5.0, 13.0, 12.0), hair, rot=("z", 22.5, (5.0, 8.0, 7.5)))
    m.full((11.0, 7.0, 3.0), (13.0, 13.0, 12.0), hair, rot=("z", -22.5, (11.0, 8.0, 7.5)))
    m.full((5.0, 7.5, 4.0), (7.0, 14.0, 13.0), hair, rot=("z", 22.5, (7.0, 8.0, 8.5)))
    m.full((9.0, 7.5, 4.0), (11.0, 14.0, 13.0), hair, rot=("z", -22.5, (9.0, 8.0, 8.5)))
    return m


def wing_model(key):
    """A left feathered wing of Yinglong (tier 6), shoulder at (8, 8, 8): the arm rises and reaches west, the feathers
    hang back (south) from it, longest at the tip."""
    m = Model("wing_l_" + key)
    s, f = "scale_" + key, "feather_" + key
    m.box((-1.0, 7.0, 7.0), (8.0, 9.0, 9.0), s, rot=("z", -22.5, (8.0, 8.0, 8.0)))
    m.box((-14.0, 10.5, 7.2), (-1.0, 12.5, 8.8), s)
    m.box((-16.0, 11.0, 7.4), (-13.0, 12.0, 8.6), "horn_gold", glow=6)
    feathers = [(-15.5, 27.0, -22.5), (-12.5, 29.0, -22.5), (-9.5, 30.0, 0.0), (-6.5, 28.0, 0.0), (-3.5, 25.0, 0.0),
                (-0.5, 21.0, 22.5), (2.5, 17.0, 22.5)]
    for x, length, angle in feathers:
        y = 10.0 if x < -1.0 else 9.0
        rot = ("y", angle, (x + 1.6, y, 8.5)) if angle else None
        m.full((x, y, 8.5), (x + 3.2, y + 0.5, min(32.0, length)), f, rot=rot)
    m.full((-10.0, 9.4, 8.0), (2.0, 9.9, 16.0), f)
    return m


def horn_model():
    """A left antler of a true dragon (tier 4+), base at (8, 8, 8): a beam sweeping back with two tines, gold tip."""
    m = Model("horn_l")
    m.box((6.8, 8.0, 6.8), (9.2, 11.0, 9.2), "horn")
    m.box((7.1, 10.0, 7.0), (8.9, 17.0, 8.8), "horn", rot=("x", 22.5, (8.0, 10.0, 8.0)))
    m.box((7.3, 15.0, 9.5), (8.7, 22.0, 11.0), "horn", rot=("x", 45.0, (8.0, 15.0, 10.0)))
    m.box((7.4, 13.0, 4.5), (8.6, 18.0, 5.9), "horn", rot=("x", -22.5, (8.0, 13.0, 5.2)))
    m.box((4.0, 14.5, 8.0), (5.4, 19.0, 9.4), "horn", rot=("z", 22.5, (5.4, 14.5, 8.7)))
    m.box((7.4, 21.0, 15.0), (8.6, 23.0, 16.2), "horn_gold", glow=6, rot=("x", 45.0, (8.0, 15.0, 10.0)))
    return m


def eyes_rage_model():
    """Amukan Naga: the eyes burn (drawn full bright over the head, a little larger than the eyes) and trail two short
    flames back along the skull. Same frame as the head model."""
    m = Model("eyes_rage")
    m.box((2.15, 8.9, 2.2), (3.55, 10.9, 5.4), "eye_rage", glow=15)
    m.box((12.45, 8.9, 2.2), (13.85, 10.9, 5.4), "eye_rage", glow=15)
    m.full((2.6, 9.0, 5.4), (2.8, 11.6, 10.5), "rage_flame", glow=15, rot=("x", 22.5, (2.7, 9.9, 5.4)))
    m.full((13.2, 9.0, 5.4), (13.4, 11.6, 10.5), "rage_flame", glow=15, rot=("x", 22.5, (13.3, 9.9, 5.4)))
    return m


def horn_great_model():
    """A left antler of a Naga Sejati and Yinglong (tier 5+), base at (8, 8, 8): a long ringed beam sweeping back, four
    tines and a gold crown at the tip; replaces the tier 4 antler."""
    m = Model("horn_great_l")
    m.box((6.6, 8.0, 6.6), (9.4, 11.5, 9.4), "horn_great")
    m.box((6.9, 10.5, 6.9), (9.1, 18.5, 9.1), "horn_great", rot=("x", 22.5, (8.0, 10.5, 8.0)))
    m.box((7.1, 16.5, 10.0), (8.9, 25.0, 11.8), "horn_great", rot=("x", 45.0, (8.0, 16.5, 10.9)))
    m.box((7.3, 22.0, 18.5), (8.7, 27.0, 19.9), "horn_great", rot=("x", 45.0, (8.0, 16.5, 10.9)))
    m.box((7.3, 13.0, 4.0), (8.7, 19.5, 5.4), "horn_great", rot=("x", -22.5, (8.0, 13.0, 4.7)))
    m.box((7.4, 18.0, 6.0), (8.6, 23.0, 7.2), "horn_great", rot=("x", -22.5, (8.0, 18.0, 6.6)))
    m.box((3.2, 14.5, 8.0), (4.8, 20.0, 9.6), "horn_great", rot=("z", 22.5, (4.8, 14.5, 8.8)))
    m.box((3.6, 19.5, 11.0), (5.0, 24.0, 12.4), "horn_great", rot=("z", 22.5, (5.0, 19.5, 11.7)))
    m.box((7.2, 25.5, 23.0), (8.8, 27.5, 24.6), "horn_gold", glow=10, rot=("x", 45.0, (8.0, 16.5, 10.9)))
    m.box((3.0, 23.5, 11.0), (4.4, 25.0, 12.4), "horn_gold", glow=8, rot=("z", 22.5, (5.0, 19.5, 11.7)))
    return m


def moon_orb_model():
    """The moon the Moon-Devouring Tiangou (wolf tier 6) carries in its jaws: a bitten pale-gold moon, glowing."""
    m = Model("moon_orb")
    m.box((5.0, 4.5, 4.5), (11.0, 11.5, 11.5), "moon_orb", glow=15)
    m.box((4.5, 5.0, 4.5), (11.5, 11.0, 11.5), "moon_orb", glow=15)
    m.box((4.5, 4.5, 5.0), (11.5, 11.5, 11.0), "moon_orb", glow=15)
    m.full((1.0, 1.0, 7.95), (15.0, 15.0, 8.05), "pearl_aura", glow=15, sides=("north", "south"))
    return m


def star_ruff_model():
    """The star-silver ruff of a tier 4+ spirit wolf, worn behind the head: eight tufts fanned in a ring around (8, 8, 8),
    swept back (+z), four with a glowing star tip, on a collar band."""
    m = Model("star_ruff")
    fur = "fur_star"
    # up, down, left, right
    m.box((7.0, 9.0, 7.0), (9.0, 15.0, 11.0), fur, rot=("x", 22.5, (8.0, 9.0, 9.0)))
    m.box((7.0, 1.5, 7.0), (9.0, 7.0, 11.0), fur, rot=("x", -22.5, (8.0, 7.0, 9.0)))
    m.box((1.0, 7.0, 7.0), (7.0, 9.0, 11.0), fur, rot=("y", 22.5, (7.0, 8.0, 9.0)))
    m.box((9.0, 7.0, 7.0), (15.0, 9.0, 11.0), fur, rot=("y", -22.5, (9.0, 8.0, 9.0)))
    # the diagonals
    for x0, x1 in ((2.5, 7.5), (8.5, 13.5)):
        m.box((x0, 7.0, 7.5), (x1, 9.0, 10.5), fur, rot=("z", 45.0, (8.0, 8.0, 9.0)))
        m.box((x0, 7.0, 7.5), (x1, 9.0, 10.5), fur, rot=("z", -45.0, (8.0, 8.0, 9.0)))
    # star tips
    m.box((7.4, 14.5, 9.5), (8.6, 15.7, 10.7), "fur_moon_tip", glow=13, rot=("x", 22.5, (8.0, 9.0, 9.0)))
    m.box((0.6, 7.4, 9.5), (1.8, 8.6, 10.7), "fur_moon_tip", glow=13, rot=("y", 22.5, (7.0, 8.0, 9.0)))
    m.box((14.2, 7.4, 9.5), (15.4, 8.6, 10.7), "fur_moon_tip", glow=13, rot=("y", -22.5, (9.0, 8.0, 9.0)))
    m.box((7.4, 1.2, 9.5), (8.6, 2.4, 10.7), "fur_moon_tip", glow=13, rot=("x", -22.5, (8.0, 7.0, 9.0)))
    # the collar band
    m.box((5.0, 5.0, 8.0), (11.0, 11.0, 10.0), fur)
    return m


def pearl_model():
    """The dragon pearl a tamed Jiao carries in front of its head (and the summoning effect): a round glowing pearl
    inside a circular aura disc on each axis."""
    m = Model("pearl")
    m.box((5.5, 5.0, 5.0), (10.5, 11.0, 11.0), "pearl", glow=15)
    m.box((5.0, 5.5, 5.0), (11.0, 10.5, 11.0), "pearl", glow=15)
    m.box((5.0, 5.0, 5.5), (11.0, 11.0, 10.5), "pearl", glow=15)
    m.full((1.0, 1.0, 7.95), (15.0, 15.0, 8.05), "pearl_aura", glow=15, sides=("north", "south"))
    m.full((7.95, 1.0, 1.0), (8.05, 15.0, 15.0), "pearl_aura", glow=15, sides=("east", "west"))
    return m


# ================================================================================================ beast layer models

def fox_tail_models(variant):
    """A spirit fox tail in two pieces (base and tip), each with its joint at (8, 8, 8), trailing south."""
    fur, tip = "fur_" + variant, "fur_" + variant + "_tip"
    base = Model("fox_tail_" + variant)
    base.box((6.2, 6.2, 8.0), (9.8, 9.8, 13.0), fur)
    base.box((5.6, 5.6, 11.5), (10.4, 10.4, 16.5), fur)
    tipm = Model("fox_tail_tip_" + variant)
    tipm.box((5.8, 5.8, 8.0), (10.2, 10.2, 12.0), fur)
    tipm.box((6.2, 6.2, 11.5), (9.8, 9.8, 14.5), fur)
    tipm.box((6.7, 6.7, 14.0), (9.3, 9.3, 16.5), tip, glow=11)


def halo_models():
    """Wolf halos behind the head: tier 5 a silver moon ring, tier 6 the eclipse (a black moon swallowing the light)."""
    m = Model("halo_moon")
    m.full((0.0, 0.0, 7.9), (16.0, 16.0, 8.1), "halo_moon", glow=15, sides=("north", "south"))
    m = Model("halo_eclipse")
    m.full((-2.0, -2.0, 7.9), (18.0, 18.0, 8.1), "halo_gold", glow=15, sides=("north", "south"))
    m.full((2.0, 2.0, 7.7), (14.0, 14.0, 7.85), "eclipse_disc", glow=15, sides=("north", "south"))


def build_models():
    for key in KINDS:
        head_model(key)
        jaw_model(key)
        body_model(key, False)
        body_model(key, True)
        claw_model(key).mirrored("claw_r_" + key)
        tail_model(key)
        whisker_model(key)
        mane_model(key)
        wing_model(key).mirrored("wing_r_" + key)
    horn_model().mirrored("horn_r")
    horn_great_model().mirrored("horn_great_r")
    eyes_rage_model()
    moon_orb_model()
    star_ruff_model()
    pearl_model()
    for variant in ("moon", "sky", "gold"):
        fox_tail_models(variant)
    halo_models()


# ================================================================================================ overlays (entity textures)

def overlay(name, w, h, sheen, star, star_alpha, density, seed, gold=None):
    """An emissive overlay drawn over the beast's own model (any uv layout): a faint sheen plus four-point star glints."""
    r = random.Random(seed)
    stars = [(r.random(), r.random(), 0.4 + 0.6 * r.random()) for _ in range(density)]
    sc, st = rgb(sheen), rgb(star)
    gc = rgb(gold) if gold else None
    img = Image(w, h)
    for y in range(h):
        for x in range(w):
            img.set(x, y, (sc[0], sc[1], sc[2], 26))
    for sx, sy, size in stars:
        cx, cy = int(sx * w), int(sy * h)
        col = gc if gc and r.random() < 0.35 else st
        arm = 1 + int(size * 2.4)
        img.set(cx, cy, (255, 255, 255, star_alpha))
        for i in range(1, arm + 1):
            a = int(star_alpha * (1.0 - i / (arm + 1.0)))
            for dx, dy in ((i, 0), (-i, 0), (0, i), (0, -i)):
                img.set((cx + dx) % w, (cy + dy) % h, (col[0], col[1], col[2], a))
    OVERLAYS[name] = img


def build_overlays():
    overlay("wolf_bintang_perak", 128, 64, "B8C8FF", "E8F0FF", 235, 70, 101)
    overlay("wolf_tiangou", 128, 64, "C8D8FF", "FFFFFF", 245, 90, 103, gold="FFE9A0")
    overlay("wolf_penelan_bulan", 128, 64, "3A2A70", "FFD86A", 250, 110, 107, gold="FFB040")
    overlay("fox_bulan", 96, 64, "C8D8FF", "F0F6FF", 235, 60, 109)
    overlay("fox_langit", 96, 64, "FFF2D0", "FFFFFF", 240, 80, 113, gold="FFE9A0")
    overlay("fox_emas", 96, 64, "FFC850", "FFF6C8", 250, 100, 127, gold="FFB030")
    build_far()


def build_far():
    """The far silhouette of a wild Jiao (sky-projected beyond the fog, NagaFar.java): white, tinted per vertex.

    far_body: a ribbon texture, u along the body (repeats), v across it: soft edges, a darker spine line, scale rows
    and a ragged dorsal edge on the top half; every visible pixel keeps alpha >= 26 (the emissive pipeline's cutout).
    far_glow: a round soft dot for the eyes, the pearl glint and the glow core.
    """
    n = Noise(211)

    def body(u, v):
        across = abs(v - 0.5) * 2.0                      # 0 on the spine, 1 at the edges
        ragged = 0.08 * math.sin(u * math.pi * 8.0) if v < 0.5 else 0.0
        edge = 1.0 - smooth(0.62 + ragged, 1.0, across)
        if edge <= 0.0:
            return (255, 255, 255, 0)
        rows = 0.82 + 0.18 * abs(math.sin(u * math.pi * 6.0 + (0.5 if v > 0.5 else 0.0)))
        grain = 0.9 + 0.1 * n.fbm(u * 8.0, v * 4.0)
        spine = 1.0 - 0.22 * (1.0 - smooth(0.0, 0.18, across))
        shade = 255 * rows * grain * spine
        a = 26 + 229 * edge
        return (shade, shade, shade, a)
    OVERLAYS["far_body"] = make(32, 16, body)

    def glow(u, v):
        d = math.hypot(u - 0.5, v - 0.5) * 2.0
        if d >= 1.0:
            return (255, 255, 255, 0)
        a = (1.0 - d) ** 1.6
        a = 26 + 229 * a if a > 0.02 else 0
        return (255, 255, 255, a)
    OVERLAYS["far_glow"] = make(16, 16, glow)


# ================================================================================================ inventory icons (16 x 16)

def icon(name, fn):
    ICONS[name] = make(16, 16, fn)


# icons drawn from several layers (item/generated layer N = tint index N) and their tint sources; extra layer textures
ICON_LAYERS = {}
ICON_TINTS = {}
EXTRA_ICON_TEX = {}


def build_icons():
    def sash(u, v):
        # a red silk sash with gold hems, curling in an S from the top left to a knotted loop and two trailing ends
        x, y = u * 16.0, v * 16.0
        best = 9.0
        along = 0.0
        for i in range(60):
            s_ = i / 59.0
            cx = 3.0 + 10.0 * s_ + 2.4 * math.sin(s_ * math.pi * 2.0)
            cy = 2.0 + 11.0 * s_
            d = math.hypot(x - cx, y - cy)
            if d < best:
                best, along = d, s_
        loop = abs(math.hypot(x - 11.0, y - 4.5) - 2.4)
        width = 1.7 - 0.5 * along
        if best < width or loop < 0.9:
            edge = best > width - 0.55 if best < width else loop > 0.45
            c = rgb("FFD54A") if edge else mix(rgb("F04A30"), rgb("8E1A12"), along)
            if not edge and int(x + y) % 5 == 0:
                c = mix(c, rgb("FFB0A0"), 0.35)
            return (c[0], c[1], c[2], 255)
        return (0, 0, 0, 0)
    icon("selendang_pengikat_naga", sash)

    def scale(u, v):
        x, y = u * 2.0 - 1.0, v * 2.0 - 1.0
        d = (x / 0.8) ** 2 + ((y + 0.25) / 1.0) ** 2
        if d > 1.0 or y < -0.85:
            return (0, 0, 0, 0)
        c = mix(rgb("9FE8FF"), rgb("1E4C9A"), clamp01(d + y * 0.3))
        if d > 0.78:
            c = rgb("0E2A60")
        if abs(x) < 0.06 and y > -0.7:
            c = mix(c, rgb("E0FFFF"), 0.6)
        return (c[0], c[1], c[2], 255)
    icon("sisik_jiao", scale)

    def pearl(core, rim, glow):
        def fn(u, v):
            d = math.hypot(u - 0.5, v - 0.5) * 2.0
            if d > 0.82:
                g = math.exp(-((d - 0.86) / 0.07) ** 2)
                c = rgb(glow)
                return (c[0], c[1], c[2], 200.0 * g) if d < 1.0 else (0, 0, 0, 0)
            dd = math.hypot(u - 0.40, v - 0.36) * 2.0
            c = mix(rgb(core), rgb(rim), clamp01(dd * 0.9))
            if dd < 0.18:
                c = (255, 255, 255)
            return (c[0], c[1], c[2], 255)
        return fn
    icon("mutiara_jiao", pearl("E8FAFF", "3A86D0", "9FE0FF"))
    base = pearl("FFF8D8", "E0A020", "FFE080")

    def tamed(u, v):
        c = base(u, v)
        x, y = u * 16.0, v * 16.0
        # a tiny coiled dragon swirl inside the pearl
        ang = math.atan2(y - 8.0, x - 8.0)
        rr = math.hypot(x - 8.0, y - 8.0)
        if 1.5 < rr < 5.2 and abs(((ang + rr * 0.9) % (math.pi)) - 1.2) < 0.32 and c[3] > 200:
            return (150, 40, 30, 255)
        return c
    icon("mutiara_naga_jinak", tamed)

    def vial(u, v):
        # a jade vial: a round belly, a narrow neck, a red seal; the blood inside glows with a blue-violet sheen
        x, y = u * 16.0, v * 16.0
        if 6.0 <= x < 10.0 and 1.0 <= y < 3.0:
            c = rgb("C02A20") if y < 2.0 else rgb("8E1A12")
            return (c[0], c[1], c[2], 255)
        if 6.5 <= x < 9.5 and 3.0 <= y < 6.0:
            edge = x < 7.2 or x >= 8.8
            c = rgb("2E8A6A") if edge else rgb("9FE8D0")
            return (c[0], c[1], c[2], 255)
        d = math.hypot((x - 8.0) / 5.2, (y - 10.5) / 4.8)
        if d < 1.0:
            if d > 0.82:
                c = rgb("1E6A52")
            elif y > 8.0:
                # the blood itself is layer1 (tinted by the kind); the glass behind it stays clear jade
                return (0, 0, 0, 0)
            else:
                c = rgb("BFF0E0")
            return (c[0], c[1], c[2], 255)
        return (0, 0, 0, 0)
    icon("darah_jiao", vial)

    def blood(u, v):
        # layer1 of the vial: the blood in grey (the item model tints it by the Jiao kind, custom_model_data colour 0),
        # darker toward the bottom, a meniscus line, a glint on the left and a few bright motes of Qi
        x, y = u * 16.0, v * 16.0
        d = math.hypot((x - 8.0) / 5.2, (y - 10.5) / 4.8)
        if d >= 0.82 or y <= 8.0:
            return (0, 0, 0, 0)
        g = 235.0 - 120.0 * clamp01((y - 8.0) / 7.0) - 40.0 * clamp01((d - 0.5) / 0.32)
        if y < 9.0:
            g = 255.0
        if math.hypot(x - 6.2, y - 9.8) < 1.1:
            g = 255.0
        if (int(x) * 7 + int(y) * 13) % 23 == 0:
            g = min(255.0, g + 70.0)
        return (g, g, g, 255)
    ICON_LAYERS["darah_jiao"] = ["darah_jiao", "darah_jiao_blood"]
    ICON_TINTS["darah_jiao"] = [{"type": "minecraft:constant", "value": -1},
                                {"type": "minecraft:custom_model_data", "index": 0, "default": 0xD0303A}]
    EXTRA_ICON_TEX["darah_jiao_blood"] = make(16, 16, blood)


# ================================================================================================ validation and output

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
            if rot["angle"] not in (-45.0, -22.5, 22.5, 45.0):
                problems.append("element %d angle %s" % (i, rot["angle"]))
            if any(o < -16.0 or o > 32.0 for o in rot["origin"]):
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
    textures = {key: "%s:item/fx/naga/%s" % (NS, name) for name, key in m.tex.items()}
    first = next(iter(m.tex))
    textures["particle"] = "%s:item/fx/naga/%s" % (NS, first)
    return {"textures": textures, "elements": m.els}


def preview(img, path, factor=8):
    big = Image(img.width * factor, img.height * factor)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.get(x, y)
            # over a dark checker so alpha shows
            bg = 40 if ((x + y) % 2) else 60
            k = a / 255.0
            c = (r * k + bg * (1 - k), g * k + bg * (1 - k), b * k + bg * (1 - k), 255)
            for yy in range(factor):
                for xx in range(factor):
                    big.set(x * factor + xx, y * factor + yy, c)
    big.save(path)


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    build_kind_textures()
    build_shared_textures()
    build_models()
    build_overlays()
    build_icons()
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
        write_json(os.path.join(ITEM_DIR, mid + ".json"),
                   {"model": {"type": "minecraft:model", "model": "%s:item/fx/naga/%s" % (NS, mid)}})
        print("  %-22s %2d elements  %s" % (mid, len(m.els), ", ".join(sorted(m.tex))))
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    os.makedirs(OVERLAY_DIR, exist_ok=True)
    for name, img in sorted(OVERLAYS.items()):
        img.save(os.path.join(OVERLAY_DIR, name + ".png"))
    os.makedirs(ICON_TEX_DIR, exist_ok=True)
    for name, img in sorted(EXTRA_ICON_TEX.items()):
        img.save(os.path.join(ICON_TEX_DIR, name + ".png"))
    for name, img in sorted(ICONS.items()):
        img.save(os.path.join(ICON_TEX_DIR, name + ".png"))
        layers = ICON_LAYERS.get(name, [name])
        write_json(os.path.join(ICON_MODEL_DIR, name + ".json"),
                   {"parent": "minecraft:item/generated",
                    "textures": {"layer%d" % i: "%s:item/%s" % (NS, tex) for i, tex in enumerate(layers)}})
        item_model = {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}
        if name in ICON_TINTS:
            item_model["tints"] = ICON_TINTS[name]
        write_json(os.path.join(ICON_ITEM_DIR, name + ".json"), {"model": item_model})
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        for name in sorted(used):
            preview(TEXTURES[name], os.path.join(preview_dir, "tex_" + name + ".png"), 8 if TEXTURES[name].width <= 32 else 4)
        for name, img in OVERLAYS.items():
            preview(img, os.path.join(preview_dir, "overlay_" + name + ".png"), 4)
        for name, img in ICONS.items():
            preview(img, os.path.join(preview_dir, "icon_" + name + ".png"), 16)
    with open(IDS_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join("naga/" + mid for mid in sorted(MODELS)) + "\n")
    print("naga models: %d, textures: %d, overlays: %d, icons: %d, errors: %d"
          % (len(MODELS), len(used), len(OVERLAYS), len(ICONS), errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
