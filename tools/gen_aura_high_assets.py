"""Generates the assets of module H of the Aura Kultivasi (DESIGN 31.4.6-31.4.9): the quad textures of realms 6-9 and
their 3D props. Everything here is ours: procedural textures and cuboid item models, nothing is downloaded.

Prefixes: aura_dt_ Deity Transformation, aura_du_ Dao Union, aura_ia_ Immortal Ascension, aura_ti_ True Immortal.

Writes:
  textures/misc/aura_high/*.png    the quad textures (white / grey with alpha: AuraDraw tints them; every glow fades to
                                   alpha 0 at the border)
  textures/item/fx/aura_*.png      the textures of the props
  models/item/fx/<id>.json         one cuboid model per prop
  items/fx/<id>.json               the item definitions (kultivator:fx/<id>)
  tools/fx_model_ids_aura_high.txt this module's id list (tools/fx_model_ids.txt is never touched)

Rules kept by every model (checked by validate(), as in gen_fire_ultimate_models.py):
  * the anchor is (8, 8, 8); the front faces north (-z);
  * element coordinates within -16..32, uv within 0..16, at most 40 elements;
  * an element rotation is a single axis by a multiple of 22.5 between -45 and 45;
  * rotations follow the right hand (Y by +a turns +x towards -z, X by +a turns +y towards +z, Z by +a turns +x
    towards +y).

Run with: python tools/gen_aura_high_assets.py   (validates every model and texture, prints a summary, and writes a
preview sheet of the quad textures to the path given with --preview)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image  # noqa: E402

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
MISC_DIR = os.path.join(ROOT, "textures", "misc", "aura_high")
TEX_DIR = os.path.join(ROOT, "textures", "item", "fx")
MODEL_DIR = os.path.join(ROOT, "models", "item", "fx")
ITEM_DIR = os.path.join(ROOT, "items", "fx")
IDS_FILE = os.path.join(HERE, "fx_model_ids_aura_high.txt")
NS = "kultivator"
PREFIXES = ("aura_dt_", "aura_du_", "aura_ia_", "aura_ti_")

MISC = {}
TEXTURES = {}
MODELS = {}


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


def gauss(x, w):
    return math.exp(-(x / w) ** 2)


WHITE = (255, 255, 255)


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


def render(size, fn, ss=2):
    """fn(u, v) -> (r, g, b, a) with u, v in 0..1 (v = 0 at the top); ss x ss supersampling for clean edges."""
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            acc = [0.0, 0.0, 0.0, 0.0]
            for sy in range(ss):
                for sx in range(ss):
                    c = fn((x + (sx + 0.5) / ss) / size, (y + (sy + 0.5) / ss) / size)
                    a = c[3] if len(c) > 3 else 255
                    acc[0] += c[0] * a
                    acc[1] += c[1] * a
                    acc[2] += c[2] * a
                    acc[3] += a
            n = ss * ss
            a = acc[3] / n
            if a <= 0.0:
                img.set(x, y, (0, 0, 0, 0))
            else:
                img.set(x, y, (acc[0] / acc[3], acc[1] / acc[3], acc[2] / acc[3], a))
    return img


def misc(name, size, fn, ss=2):
    """A quad texture (textures/misc/aura_high/<name>.png); its alpha always reaches 0 at the border."""
    def edged(u, v):
        c = fn(u, v)
        return (c[0], c[1], c[2], c[3] * border(u, v, 0.025))
    MISC[name] = render(size, edged, ss)
    return name


def tex(name, size, fn, ss=1):
    """A prop texture (textures/item/fx/<name>.png)."""
    assert name.startswith(PREFIXES), name
    TEXTURES[name] = render(size, fn, ss)
    return name


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 <= 0 else clamp01(((px - ax) * dx + (py - ay) * dy) / l2)
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t))


def poly_dist(px, py, pts):
    return min(seg_dist(px, py, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]) for i in range(len(pts) - 1))


def ellipse_sd(u, v, cx, cy, rx, ry):
    """An approximate signed distance to an ellipse (< 0 inside)."""
    k = math.hypot((u - cx) / rx, (v - cy) / ry)
    return (k - 1.0) * min(rx, ry)


def polar(u, v):
    """(r, angle): r = 1 at the inscribed circle, angle from +u, counter-clockwise on screen (v up)."""
    return math.hypot(u - 0.5, v - 0.5) * 2.0, math.atan2(0.5 - v, u - 0.5)


def border(u, v, margin=0.03):
    """1 inside, falling to 0 at the texture's edge (every glow ends at alpha 0)."""
    m = min(u, v, 1.0 - u, 1.0 - v)
    return smooth(0.0, margin, m)


def lum(level, alpha):
    """A grey (tinted later by AuraDraw) with an alpha in 0..1."""
    g = 255 * clamp01(level)
    return (g, g, g, 255 * clamp01(alpha))


# ================================================================================================ quad textures

def idol_sd(u, v):
    """The seated Dharma Idol (head, topknot, shoulders, arms, hands, crossed legs, lotus seat): < 0 inside."""
    parts = [
        ellipse_sd(u, v, 0.5, 0.255, 0.068, 0.078),                # head
        ellipse_sd(u, v, 0.5, 0.168, 0.032, 0.03),                 # topknot (ushnisha)
        ellipse_sd(u, v, 0.425, 0.262, 0.016, 0.042),              # long ear lobes
        ellipse_sd(u, v, 0.575, 0.262, 0.016, 0.042),
        seg_dist(u, v, 0.5, 0.31, 0.5, 0.36) - 0.034,              # neck
        ellipse_sd(u, v, 0.5, 0.47, 0.165, 0.14),                  # chest and shoulders
        ellipse_sd(u, v, 0.5, 0.56, 0.135, 0.11),                  # belly
        poly_dist(u, v, [(0.355, 0.39), (0.325, 0.52), (0.37, 0.655), (0.45, 0.7)]) - 0.042,   # arms down to the lap
        poly_dist(u, v, [(0.645, 0.39), (0.675, 0.52), (0.63, 0.655), (0.55, 0.7)]) - 0.042,
        ellipse_sd(u, v, 0.5, 0.69, 0.11, 0.04),                   # hands in the lap
        ellipse_sd(u, v, 0.5, 0.775, 0.31, 0.085),                 # crossed legs
        ellipse_sd(u, v, 0.5, 0.875, 0.36, 0.06),                  # the lotus seat
    ]
    return min(parts)


def halo_r(angle, base, flames, depth):
    """The radius of a flaming halo rim: tongues licking outward (deterministic)."""
    lick = (0.5 + 0.5 * math.cos(angle * flames)) ** 2.2
    return base + depth * lick


def dt_idol(u, v, rim_only=False):
    sd = idol_sd(u, v)
    # the flaming head halo and the body aureole behind the figure
    hr, ha = math.hypot(u - 0.5, v - 0.25), math.atan2(0.25 - v, u - 0.5)
    head_rim = halo_r(ha, 0.155, 12, 0.045)
    br, ba = math.hypot((u - 0.5) / 0.4, (v - 0.56) / 0.44), math.atan2(0.56 - v, u - 0.5)
    body_rim = halo_r(ba, 0.96, 18, 0.07)
    a = 0.0
    lvl = 1.0
    # aureole (faint fill, flame rim)
    if br < body_rim and v < 0.9:
        a = max(a, 0.0 if rim_only else 0.10 + 0.06 * smooth(0.5, 1.0, br / body_rim))
    a = max(a, 0.55 * gauss(br - body_rim, 0.025) * smooth(0.95, 0.88, v))
    # head halo
    if hr < head_rim:
        a = max(a, 0.0 if rim_only else 0.16 + 0.12 * smooth(0.6, 1.0, hr / head_rim))
    a = max(a, 0.85 * gauss(hr - head_rim, 0.008) + 0.35 * gauss(hr - 0.11, 0.006))
    # the figure: translucent fill, a bright rim, robe folds a little darker, the third eye bright
    if sd < 0.0:
        if not rim_only:
            fill = 0.46 + 0.14 * smooth(-0.05, 0.0, sd)
            folds = 0.0
            for (x0, y0, x1, y1) in ((0.39, 0.37, 0.47, 0.6), (0.61, 0.37, 0.53, 0.6), (0.3, 0.78, 0.46, 0.74),
                                     (0.7, 0.78, 0.54, 0.74), (0.5, 0.42, 0.5, 0.62)):
                folds = max(folds, gauss(seg_dist(u, v, x0, y0, x1, y1), 0.006))
            fill -= 0.16 * folds
            a = max(a, fill)
            lvl = 0.92 - 0.1 * folds
        if math.hypot(u - 0.5, v - 0.232) < 0.011:
            a, lvl = 1.0, 1.0
    a = max(a, gauss(sd, 0.007))
    if sd > 0.0 and not rim_only:
        a = max(a, 0.3 * math.exp(-sd / 0.02))
    return lum(lvl, a * border(u, v, 0.04))


def dt_ripple(u, v):
    r, _ = polar(u, v)
    a = 0.95 * gauss(r - 0.9, 0.035) + 0.22 * smooth(0.55, 0.9, r) * (1.0 - smooth(0.9, 0.93, r))
    return lum(1.0, a * smooth(1.0, 0.97, r))


def dt_flame_halo(u, v):
    r, ang = polar(u, v)
    rim = halo_r(ang, 0.62, 12, 0.24)
    a = 0.0
    if r < rim:
        tongue = smooth(0.62, rim, r) if rim > 0.63 else 0.0
        a = 0.12 + 0.45 * tongue * (1.0 - smooth(0.8, 1.0, r / rim))
    a = max(a, 0.95 * gauss(r - 0.6, 0.035))
    a = max(a, 0.6 * gauss(r - rim, 0.03))
    return lum(1.0, a * smooth(1.0, 0.95, r))


def dharma_wheel(u, v):
    r, ang = polar(u, v)
    a = 0.0
    a = max(a, 0.75 * gauss(r - 0.94, 0.012))                        # the ripple ring
    a = max(a, 0.35 * gauss(r - 0.86, 0.008))
    a = max(a, 0.9 * (smooth(0.6, 0.62, r) - smooth(0.68, 0.7, r)))  # the rim
    a = max(a, 0.55 * gauss(r - 0.56, 0.006))
    for k in range(8):                                                # spokes, tapering out, and knobs past the rim
        th = k * math.pi / 4.0
        dx, dy = math.cos(th), math.sin(th)
        x, y = (u - 0.5) * 2.0, (0.5 - v) * 2.0
        along = x * dx + y * dy
        across = abs(-x * dy + y * dx)
        if 0.12 < along < 0.62:
            w = 0.035 - 0.015 * (along - 0.12) / 0.5
            a = max(a, 0.85 * smooth(w + 0.012, w, across))
        kx, ky = 0.76 * dx, 0.76 * dy
        a = max(a, 0.9 * smooth(0.05, 0.035, math.hypot(x - kx, y - ky)))
        a = max(a, 0.6 * smooth(0.025, 0.012, seg_dist(x, y, 0.69 * dx, 0.69 * dy, kx, ky)))
    a = max(a, 0.95 * (1.0 - smooth(0.1, 0.13, r)))                   # the hub
    a = max(a, 0.7 * gauss(r - 0.2, 0.012))
    # the three swirls of the hub (gankyil), darker inside the bright hub
    if r < 0.11:
        swirl = 0.5 + 0.5 * math.cos(ang * 3.0 + r * 40.0)
        return lum(1.0 - 0.35 * swirl, a)
    a = max(a, 0.1 * (1.0 - smooth(0.6, 0.94, r)))                    # faint fill
    return lum(1.0, a * smooth(1.0, 0.975, r))


# ---- glyphs as strokes in 0..1 (v down), for the Dao runes 道 一 二 三 天 地 人 合 and the plaque 仙
GLYPHS = {
    "yi": [[(0.16, 0.5), (0.84, 0.5)]],
    "er": [[(0.26, 0.36), (0.74, 0.36)], [(0.14, 0.66), (0.86, 0.66)]],
    "san": [[(0.24, 0.27), (0.76, 0.27)], [(0.3, 0.5), (0.7, 0.5)], [(0.14, 0.75), (0.86, 0.75)]],
    "tian": [[(0.26, 0.27), (0.74, 0.27)], [(0.14, 0.47), (0.86, 0.47)], [(0.5, 0.27), (0.5, 0.47), (0.36, 0.7), (0.18, 0.86)],
             [(0.52, 0.52), (0.68, 0.72), (0.84, 0.86)]],
    "di": [[(0.08, 0.42), (0.36, 0.42)], [(0.22, 0.2), (0.22, 0.72)], [(0.06, 0.78), (0.38, 0.68)],
           [(0.44, 0.44), (0.88, 0.36), (0.86, 0.58)], [(0.6, 0.18), (0.6, 0.66)], [(0.45, 0.28), (0.45, 0.82), (0.92, 0.84), (0.92, 0.72)]],
    "ren": [[(0.52, 0.12), (0.48, 0.42), (0.36, 0.66), (0.16, 0.86)], [(0.5, 0.44), (0.66, 0.68), (0.86, 0.86)]],
    "he": [[(0.5, 0.1), (0.3, 0.3), (0.12, 0.42)], [(0.5, 0.1), (0.7, 0.3), (0.88, 0.42)], [(0.33, 0.47), (0.67, 0.47)],
           [(0.3, 0.58), (0.7, 0.58), (0.7, 0.88), (0.3, 0.88), (0.3, 0.58)]],
    "dao": [[(0.42, 0.06), (0.46, 0.14)], [(0.68, 0.06), (0.63, 0.14)], [(0.32, 0.19), (0.84, 0.19)], [(0.58, 0.19), (0.54, 0.27)],
            [(0.4, 0.29), (0.76, 0.29), (0.76, 0.66), (0.4, 0.66), (0.4, 0.29)], [(0.4, 0.41), (0.76, 0.41)], [(0.4, 0.53), (0.76, 0.53)],
            [(0.12, 0.12), (0.2, 0.22)], [(0.08, 0.36), (0.22, 0.36), (0.2, 0.6), (0.1, 0.74)], [(0.1, 0.74), (0.32, 0.82), (0.94, 0.86)]],
    "xian": [[(0.3, 0.1), (0.14, 0.44)], [(0.24, 0.3), (0.24, 0.9)], [(0.62, 0.1), (0.62, 0.84)], [(0.42, 0.34), (0.42, 0.84), (0.84, 0.84),
             (0.84, 0.34)]],
}
RUNES = ("dao", "yi", "er", "san", "tian", "di", "ren", "he")


def glyph_dist(name, u, v):
    return min(poly_dist(u, v, stroke) for stroke in GLYPHS[name])


def rune_card(name):
    def fn(u, v):
        gu, gv = 0.5 + (u - 0.5) / 0.74, 0.5 + (v - 0.5) / 0.74     # the glyph inside the seal circle
        d = glyph_dist(name, gu, gv) * 0.74
        r, _ = polar(u, v)
        a = max(smooth(0.042, 0.03, d), 0.5 * math.exp(-max(0.0, d - 0.03) / 0.04))
        a = max(a, 0.4 * gauss(r - 0.9, 0.02))                      # a faint seal circle
        a = max(a, 0.08 * (1.0 - smooth(0.6, 0.9, r)))
        lvl = 1.0 if d < 0.04 else 0.85
        return lum(lvl, a * smooth(1.0, 0.95, r) * border(u, v))
    return fn


def wuxing(u, v):
    r, ang = polar(u, v)
    x, y = (u - 0.5) * 2.0, (0.5 - v) * 2.0
    verts = [(0.62 * math.cos(math.radians(90 + 72 * k)), 0.62 * math.sin(math.radians(90 + 72 * k))) for k in range(5)]
    a = 0.0
    a = max(a, 0.6 * gauss(r - 0.93, 0.01) + 0.45 * gauss(r - 0.87, 0.008))   # the outer double ring
    for k in range(24):                                                         # tick marks
        th = k * math.pi / 12.0
        d = seg_dist(x, y, 0.87 * math.cos(th), 0.87 * math.sin(th), 0.93 * math.cos(th), 0.93 * math.sin(th))
        a = max(a, 0.5 * smooth(0.012, 0.004, d))
    # the generating cycle: the circle through the five elements, with an arrowhead past each one
    a = max(a, 0.7 * gauss(r - 0.62, 0.009))
    for k in range(5):
        th = math.radians(90 + 72 * k + 36)
        px, py = 0.62 * math.cos(th), 0.62 * math.sin(th)
        tx, ty = -math.sin(th), math.cos(th)                      # counter-clockwise tangent
        for side in (-1, 1):
            ex, ey = px - tx * 0.06 + side * math.cos(th) * 0.04, py - ty * 0.06 + side * math.sin(th) * 0.04
            a = max(a, 0.75 * smooth(0.014, 0.006, seg_dist(x, y, px, py, ex, ey)))
    # the controlling cycle (faint pentagram)
    for k in range(5):
        a1, a2 = verts[k], verts[(k + 2) % 5]
        a = max(a, 0.22 * smooth(0.012, 0.005, seg_dist(x, y, a1[0], a1[1], a2[0], a2[1])))
    # the five element seats
    for (vx, vy) in verts:
        d = math.hypot(x - vx, y - vy)
        a = max(a, 0.95 * gauss(d - 0.12, 0.012), 0.35 * (1.0 - smooth(0.08, 0.12, d)))
    # the taiji at the centre: yang bright, yin dim, with the two eyes
    if r < 0.26:
        rr = 0.24
        yang = x > 0.0
        if math.hypot(x, y - rr / 2) < rr / 2:
            yang = True
        if math.hypot(x, y + rr / 2) < rr / 2:
            yang = False
        if math.hypot(x, y - rr / 2) < rr / 7:
            yang = False
        if math.hypot(x, y + rr / 2) < rr / 7:
            yang = True
        inside = smooth(rr + 0.008, rr - 0.004, r)
        rim = gauss(r - rr, 0.01)
        return lum(1.0 if yang else 0.55, max(rim * 0.95, inside * (0.92 if yang else 0.3)) * smooth(1.0, 0.97, r))
    return lum(1.0, a * smooth(1.0, 0.97, r))


def ia_gate(u, v):
    r, ang = polar(u, v)
    a = 0.0
    a = max(a, 0.95 * gauss(r - 0.7, 0.018) + 0.5 * gauss(r - 0.62, 0.01))
    rays = max(0.0, math.cos(ang * 12.0)) ** 6
    a = max(a, 0.4 * rays * smooth(0.72, 0.76, r) * (1.0 - smooth(0.78, 0.98, r)))
    a = max(a, 0.12 * (1.0 - smooth(0.0, 0.62, r)) + 0.06)
    # eight auspicious cloud curls on the ring
    for k in range(8):
        th = k * math.pi / 4.0 + math.pi / 8.0
        cx, cy = 0.5 + 0.35 * math.cos(th), 0.5 - 0.35 * math.sin(th)
        d = math.hypot(u - cx, v - cy)
        curl = 0.5 + 0.5 * math.cos(math.atan2(v - cy, u - cx) + d * 90.0)
        a = max(a, 0.75 * smooth(0.045, 0.03, d) * (0.55 + 0.45 * curl))
    return lum(1.0, a * smooth(1.0, 0.95, r) * border(u, v))


def cloud_seal(u, v):
    r, ang = polar(u, v)
    x, y = (u - 0.5) * 2.0, (0.5 - v) * 2.0
    a = 0.0
    a = max(a, 0.55 * gauss(r - 0.7, 0.01))
    dash = 0.5 + 0.5 * math.cos(ang * 36.0)
    a = max(a, 0.4 * gauss(r - 0.42, 0.01) * smooth(0.3, 0.7, dash))
    for k in range(9):                                           # nine ruyi clouds: three lobes and a curl
        th = math.radians(90 + 40 * k)
        cx, cy = 0.7 * math.cos(th), 0.7 * math.sin(th)
        ox, oy = math.cos(th), math.sin(th)
        tx, ty = -oy, ox
        best = 9.0
        for (s_, o_, rad) in ((0.0, 0.05, 0.085), (-0.09, -0.01, 0.065), (0.09, -0.01, 0.065)):
            lx, ly = cx + tx * s_ + ox * o_, cy + ty * s_ + oy * o_
            best = min(best, math.hypot(x - lx, y - ly) - rad)
        a = max(a, 0.9 * gauss(best, 0.012), 0.3 * smooth(0.0, -0.02, best))
        d = math.hypot(x - (cx + ox * 0.05), y - (cy + oy * 0.05))
        spiral = 0.5 + 0.5 * math.cos(math.atan2(y - cy, x - cx) - d * 70.0)
        if d < 0.07:
            a = max(a, 0.6 * spiral * smooth(0.07, 0.03, d))
    a = max(a, 0.6 * gauss(r - 0.95, 0.01))
    a = max(a, 0.07 * (1.0 - smooth(0.4, 0.95, r)))
    return lum(1.0, a * smooth(1.0, 0.975, r))


def ti_star(u, v):
    dx, dy = abs(u - 0.5) * 2.0, abs(v - 0.5) * 2.0
    r = math.hypot(dx, dy)
    arm = max(clamp01(1.0 - dx / max(1e-3, 0.14 * (1.0 - dy))) * (1.0 - dy),
              clamp01(1.0 - dy / max(1e-3, 0.14 * (1.0 - dx))) * (1.0 - dx))
    halo = clamp01(1.0 - r) ** 2.4
    a = max(arm ** 0.9, halo * 0.7)
    return lum(1.0, a * smooth(1.0, 0.92, r))


def ti_domain(u, v):
    r, ang = polar(u, v)
    rays = 0.5 + 0.5 * math.cos(ang * 16.0)
    a = 0.42 * (1.0 - r * r) ** 0.8 if r < 1.0 else 0.0
    a *= 0.85 + 0.15 * rays
    a = max(a, 0.6 * gauss(r - 0.94, 0.02))
    return lum(1.0, a * smooth(1.0, 0.985, r))


DIPPER = [(-0.34, 0.12), (-0.18, 0.15), (-0.06, 0.08), (0.06, 0.02), (0.08, -0.12), (0.3, -0.14), (0.3, 0.02)]


def star_chart(u, v):
    r, ang = polar(u, v)
    x, y = (u - 0.5) * 2.0, (0.5 - v) * 2.0
    a = 0.0
    a = max(a, 0.7 * gauss(r - 0.95, 0.008) + 0.55 * gauss(r - 0.8, 0.007) + 0.35 * gauss(r - 0.48, 0.006))
    rnd = random.Random(28)
    for k in range(28):                                         # the 28 lunar mansions: dividers and star groups
        th = 2.0 * math.pi * k / 28.0
        d = seg_dist(x, y, 0.8 * math.cos(th), 0.8 * math.sin(th), 0.95 * math.cos(th), 0.95 * math.sin(th))
        a = max(a, 0.45 * smooth(0.008, 0.003, d))
        n = 1 + rnd.randint(0, 3)
        pts = []
        for i in range(n):
            t = th + (0.25 + 0.5 * (i + rnd.random() * 0.5) / n) * 2.0 * math.pi / 28.0
            rr = 0.85 + rnd.random() * 0.07
            pts.append((rr * math.cos(t), rr * math.sin(t)))
        for i in range(len(pts)):
            a = max(a, 0.95 * smooth(0.022, 0.008, math.hypot(x - pts[i][0], y - pts[i][1])))
            if i > 0:
                a = max(a, 0.35 * smooth(0.007, 0.002, seg_dist(x, y, pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1])))
    for i, (sx, sy) in enumerate(DIPPER):                        # the Northern Dipper (Beidou) at the centre
        a = max(a, smooth(0.04, 0.012, math.hypot(x - sx, y - sy)), 0.35 * math.exp(-math.hypot(x - sx, y - sy) / 0.03))
        if i > 0:
            px, py = DIPPER[i - 1]
            a = max(a, 0.5 * smooth(0.01, 0.003, seg_dist(x, y, px, py, sx, sy)))
    a = max(a, 0.5 * smooth(0.03, 0.01, seg_dist(x, y, DIPPER[3][0], DIPPER[3][1], DIPPER[6][0], DIPPER[6][1])) * 0.6)
    a = max(a, 0.8 * smooth(0.03, 0.01, math.hypot(x - 0.12, y - 0.36)))   # the pole star the dipper points to
    a = max(a, 0.05 * (1.0 - smooth(0.5, 0.95, r)))
    return lum(1.0, a * smooth(1.0, 0.98, r))


def build_misc():
    misc("dt_idol", 128, lambda u, v: dt_idol(u, v))
    misc("dt_idol_rim", 128, lambda u, v: dt_idol(u, v, rim_only=True))
    misc("dt_ripple", 64, dt_ripple)
    misc("dt_flame_halo", 64, dt_flame_halo)
    misc("decal_dharma_wheel", 128, dharma_wheel)
    for name in RUNES:
        misc("du_rune_" + name, 64, rune_card(name))
    misc("decal_wuxing", 128, wuxing)
    misc("ia_gate", 128, ia_gate)
    misc("decal_cloud_seal", 128, cloud_seal)
    misc("ti_star", 32, ti_star)
    misc("ti_domain", 128, ti_domain)
    misc("decal_star_chart", 128, star_chart)


# ================================================================================================ prop textures

def band_tex(name, centre, body, alpha, along_u=False):
    c0, c1 = rgb(centre), rgb(body)

    def fn(u, v):
        s = u if along_u else v
        k = clamp01(1.0 - abs(s - 0.5) * 2.0)
        c = mix(c1, c0, k ** 2.2)
        return (c[0], c[1], c[2], alpha * (0.35 + 0.65 * k ** 0.8))
    return tex(name, 32, fn)


def metal_tex(name, base, hi, lo, seed, size=32, patina=None):
    n = Noise(seed)
    b, h, l_ = rgb(base), rgb(hi), rgb(lo)
    pc = rgb(patina) if patina else None

    def fn(u, v):
        sheen = 0.5 + 0.5 * math.cos((u * 1.6 + v * 0.4) * math.tau)
        c = mix(l_, b, 0.55 + 0.45 * sheen)
        c = mix(c, h, 0.55 * smooth(0.82, 1.0, sheen))
        g = n.fbm(u * 5, v * 5)
        c = mix(c, l_, 0.25 * smooth(0.55, 0.8, g))
        if pc:
            c = mix(c, pc, 0.55 * smooth(0.6, 0.78, n.fbm(u * 4 + 3, v * 4)))
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def glass_tex(name, core, rim, alpha=200, size=16, seed=1):
    n = Noise(seed)
    c0, c1 = rgb(core), rgb(rim)

    def fn(u, v):
        d = max(abs(u - 0.5), abs(v - 0.5)) * 2.0
        c = mix(c0, c1, smooth(0.5, 1.0, d))
        c = mix(c, WHITE, 0.15 * (n.fbm(u * 4, v * 4) - 0.4))
        return (c[0], c[1], c[2], alpha * (0.75 + 0.25 * d))
    return tex(name, size, fn)


def halo_wheel_tex(name):
    """The flaming halo wheel of the Dharma Idol: a hot ring, twelve flame tongues, a faint inner disc."""
    core, rose, deep = rgb("FFF4FB"), rgb("FF9ADF"), rgb("D0409A")

    def fn(u, v):
        r, ang = polar(u, v)
        rim = halo_r(ang, 0.62, 12, 0.3)
        if r > 1.0:
            return (0, 0, 0, 0)
        if r < rim:
            k = smooth(0.62, rim, r) if rim > 0.63 else 0.0
            c = mix(core, rose, k) if k < 0.6 else mix(rose, deep, (k - 0.6) / 0.4)
            a = 70 + 170 * k * (1.0 - smooth(0.85, 1.0, r / rim)) if r > 0.58 else 60 + 40 * (1.0 - r / 0.58)
        else:
            c, a = deep, 0
        ring = gauss(r - 0.6, 0.04)
        c = mix(c, core, ring)
        a = max(a, 250 * ring)
        return (c[0], c[1], c[2], a)
    return tex(name, 64, fn, ss=2)


def taiji_tex(name):
    yang, yin, jade = rgb("F6FFF9"), rgb("16201C"), rgb("7CFFB2")

    def fn(u, v):
        x, y = (u - 0.5) * 2.0, (0.5 - v) * 2.0
        r = math.hypot(x, y)
        if r > 1.0:
            return (0, 0, 0, 0)
        light = x > 0.0
        if math.hypot(x, y - 0.5) < 0.5:
            light = True
        if math.hypot(x, y + 0.5) < 0.5:
            light = False
        if math.hypot(x, y - 0.5) < 0.14:
            light = False
        if math.hypot(x, y + 0.5) < 0.14:
            light = True
        c = yang if light else yin
        c = mix(c, jade, 0.9 * smooth(0.86, 0.97, r))
        return (c[0], c[1], c[2], 255)
    return tex(name, 64, fn, ss=2)


def plate_tex(name, glyph, plate, glow, size=32):
    p, g = rgb(plate), rgb(glow)
    n = Noise(len(name) * 7 + 3)

    def fn(u, v):
        c = mix(p, rgb("3A2A14"), 0.3 * n.fbm(u * 5, v * 5))
        if u < 0.08 or u > 0.92 or v < 0.08 or v > 0.92:
            c = mix(c, rgb("E8C878"), 0.6)
        gu, gv = 0.5 + (u - 0.5) / 0.78, 0.5 + (v - 0.5) / 0.78
        d = glyph_dist(glyph, gu, gv) * 0.78
        c = mix(c, g, smooth(0.07, 0.03, d))
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn, ss=2)


def jade_tex(name, base, vein, seed, size=32, carve=None):
    n = Noise(seed)
    b, vc = rgb(base), rgb(vein)
    cc = rgb(carve) if carve else None

    def fn(u, v):
        g = n.fbm(u * 3, v * 3)
        c = mix(b, WHITE, 0.25 * g)
        k = abs(n.fbm(u * 2 + 5, v * 6) - 0.5)
        c = mix(c, vc, 0.5 * smooth(0.04, 0.0, k))
        if cc:
            curl = math.sin(u * 14.0 + math.sin(v * 9.0) * 2.0) * math.cos(v * 11.0)
            c = mix(c, cc, 0.45 * smooth(0.75, 0.95, abs(curl)))
        return (c[0], c[1], c[2], 255)
    return tex(name, size, fn)


def petal_tex(name, base, tip, rim, seed, rims=0, rim_glow=None, size=32):
    """A petal (base at the bottom, tip up) with a light rim; rims > 0 adds that many nested gold outlines (the grades)."""
    n = Noise(seed)
    b, t_, r = rgb(base), rgb(tip), rgb(rim)
    gc = rgb(rim_glow) if rim_glow else None

    def width(t):
        return 0.47 * math.sin(math.pi * min(1.0, t * 0.88 + 0.1)) ** 0.75 * (1.0 - t ** 4)

    def fn(u, v):
        t = 1.0 - v
        hw = width(t)
        du = abs(u - 0.5)
        edge = (hw - du) * size
        if hw <= 0 or edge < -0.5:
            return (0, 0, 0, 0)
        c = mix(b, t_, t ** 1.1)
        c = mix(c, r, 0.55 * clamp01(1.0 - edge / 2.2))
        c = mix(c, WHITE, 0.1 * (n.fbm(u * 6, v * 6) - 0.5))
        if du < 0.03 and t < 0.8:
            c = mix(c, t_, 0.35)
        if gc:
            for k in range(rims):
                inset = 0.06 + 0.075 * k
                ek = (hw - du) - inset * hw * 2.2
                # nested outlines, brighter from the outside in
                if abs(ek) < 0.022 and t > 0.08 + 0.1 * k:
                    c = mix(c, gc, 0.55 + 0.15 * k)
        return (c[0], c[1], c[2], 255 * clamp01(edge + 0.5))
    return tex(name, size, fn, ss=2)


def cloud_tex(name, seed, top=False):
    n = Noise(seed)
    w, gold = rgb("FFFFFF"), rgb("FFE9A8")

    def fn(u, v):
        g = n.fbm(u * 3, v * 3)
        c = mix(rgb("EEF2FF"), w, g)
        if top:
            d = math.hypot(u - 0.5, v - 0.5)
            spiral = math.cos(math.atan2(v - 0.5, u - 0.5) - d * 30.0)
            c = mix(c, gold, 0.6 * smooth(0.85, 1.0, spiral) * smooth(0.5, 0.15, d))
        else:
            c = mix(c, gold, 0.5 * smooth(0.9, 1.0, math.sin(u * 12.0 + v * 4.0)))
        return (c[0], c[1], c[2], 245)
    return tex(name, 32, fn)


def light_tex(name, alpha=200):
    core, rim = rgb("FFFFFF"), rgb("FFE9A8")

    def fn(u, v):
        d = abs(u - 0.5) * 2.0
        c = mix(core, rim, d)
        rays = 0.5 + 0.5 * math.cos(u * 30.0)
        return (c[0], c[1], c[2], alpha * (0.6 + 0.3 * (1.0 - d) + 0.1 * rays))
    return tex(name, 32, fn)


def door_tex(name):
    n = Noise(77)
    jade, gold = rgb("E8F4EC"), rgb("FFD54A")

    def fn(u, v):
        c = mix(jade, rgb("B8D8C4"), 0.4 * n.fbm(u * 4, v * 6))
        if u < 0.07 or u > 0.93 or v < 0.05 or v > 0.95 or abs(v - 0.5) < 0.025:
            c = mix(c, gold, 0.8)
        gx, gy = (u * 4.0) % 1.0, (v * 8.0) % 1.0
        if math.hypot(gx - 0.5, gy - 0.5) < 0.16 and 0.1 < v < 0.9:
            c = mix(c, gold, 0.9)
        return (c[0], c[1], c[2], 255)
    return tex(name, 32, fn)


def stud_tex(name, lit):
    def fn(u, v):
        d = math.hypot(u - 0.4, v - 0.38) * 2.0
        if lit:
            c = mix(rgb("FFFFFF"), rgb("FFC93C"), clamp01(d))
        else:
            c = mix(rgb("8A7A5A"), rgb("4A3A22"), clamp01(d))
        return (c[0], c[1], c[2], 255)
    return tex(name, 16, fn)


def plaque_tex(name):
    navy, gold = rgb("1C2A5A"), rgb("FFD54A")

    def fn(u, v):
        c = navy
        if u < 0.08 or u > 0.92 or v < 0.08 or v > 0.92:
            c = gold
        gu, gv = 0.5 + (u - 0.5) / 0.7, 0.5 + (v - 0.5) / 0.7
        d = glyph_dist("xian", gu, gv) * 0.7
        c = mix(c, gold, smooth(0.06, 0.03, d))
        return (c[0], c[1], c[2], 255)
    return tex(name, 32, fn, ss=2)


def seat_tex(name):
    """The lotus seat's top: a golden seed pod with nine seeds in a ring around one."""
    pod, seed_c, rim = rgb("FFE9A8"), rgb("FFFFFF"), rgb("E0A020")

    def fn(u, v):
        x, y = (u - 0.5) * 2.0, (v - 0.5) * 2.0
        r = math.hypot(x, y)
        c = mix(pod, rim, smooth(0.7, 1.0, r))
        seeds = [(0.0, 0.0)] + [(0.55 * math.cos(k * math.tau / 9), 0.55 * math.sin(k * math.tau / 9)) for k in range(9)]
        for (sx, sy) in seeds:
            if math.hypot(x - sx, y - sy) < 0.13:
                c = mix(c, seed_c, 0.85)
        return (c[0], c[1], c[2], 255)
    return tex(name, 32, fn, ss=2)


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

    def copy(self, mid, retex=None):
        """A copy under another id, with textures replaced by {@code retex} (old name -> new name)."""
        m = Model(mid)
        retex = retex or {}
        names = {key: name for name, key in self.tex.items()}
        for e in self.els:
            ne = json.loads(json.dumps(e))
            for face in ne["faces"].values():
                old = names[face["texture"][1:]]
                face["texture"] = "#" + m.key(retex.get(old, old))
            m.els.append(ne)
        return m


def faces_of(tex_spec, sides=SIDES):
    out = {}
    for s in sides:
        name = tex_spec.get(s, tex_spec.get("*")) if isinstance(tex_spec, dict) else tex_spec
        if name:
            out[s] = (name, None)
    return out


def box(m, f, t, tex_spec, rot=None, glow=0, sides=SIDES):
    return m.el(f, t, faces_of(tex_spec, sides), rot=rot, glow=glow)


def cbox(m, c, size, tex_spec, glow=0, sides=SIDES, rot_axis=None, rot_angle=0.0):
    f = [c[i] - size[i] / 2.0 for i in range(3)]
    t = [c[i] + size[i] / 2.0 for i in range(3)]
    rot = (rot_axis, rot_angle, list(c)) if rot_axis else None
    return m.el(f, t, faces_of(tex_spec, sides), rot=rot, glow=glow)


def snap(angle):
    """An angle folded to -45..45 (swapped = turned by 90 degrees on the way) and snapped to 22.5 degree steps."""
    a = ((angle + 90.0) % 180.0) - 90.0
    swapped = False
    if a > 45.0:
        a, swapped = a - 90.0, True
    elif a < -45.0:
        a, swapped = a + 90.0, True
    a = round(a / 22.5) * 22.5
    return a, swapped


def ring(m, c, r, width, th, n, band, band_u, plane="xz", glow=15, sides=None, offset_deg=0.0, overlap=1.06):
    """A ring of n band segments around c (n a divisor of 16 so every turn is a 22.5 degree step)."""
    assert 16 % n == 0 or n == 16, n
    length = 2.0 * math.pi * r / n * overlap
    for i in range(n):
        theta = offset_deg + 360.0 * i / n
        k = 0.97 if i % 2 else 1.0
        if plane == "xz":
            px, pz = c[0] + r * math.cos(math.radians(theta)), c[2] + r * math.sin(math.radians(theta))
            a, swapped = snap(-(theta + 90.0))
            fs = sides or ("up", "down")
            size, spec = ((width * k, th * k, length), band_u) if swapped else ((length, th * k, width * k), band)
            cbox(m, (px, c[1], pz), size, spec, glow=glow, sides=fs, rot_axis="y", rot_angle=a)
        else:
            px, py = c[0] + r * math.cos(math.radians(theta)), c[1] + r * math.sin(math.radians(theta))
            a, swapped = snap(theta + 90.0)
            fs = sides or ("north", "south")
            size, spec = ((width * k, length, th * k), band_u) if swapped else ((length, width * k, th * k), band)
            cbox(m, (px, py, c[2]), size, spec, glow=glow, sides=fs, rot_axis="z", rot_angle=a)


def octagon(m, cx, cz, r, y0, y1, side, top=None, bottom=None, glow=0):
    spec = {"*": side, "up": top or side, "down": bottom or side}
    m.el([cx - r, y0, cz - r], [cx + r, y1, cz + r], faces_of(spec), glow=glow)
    m.el([cx - r, y0 + 0.02, cz - r], [cx + r, y1 - 0.03, cz + r], faces_of(spec), rot=("y", 45.0, [cx, y0, cz]), glow=glow)


def petal_ring(m, n, radius, y0, height, width, thick, offset_deg, texname, glow=12):
    """n upright petal plates round the y axis through (8, 8): each faces out, turned to the nearest 22.5 degree step."""
    for i in range(n):
        theta = offset_deg + 360.0 * i / n
        px, pz = 8.0 + radius * math.cos(math.radians(theta)), 8.0 + radius * math.sin(math.radians(theta))
        a, swapped = snap(-(theta + 90.0))
        size = (thick, height, width) if swapped else (width, height, thick)
        sides = ("east", "west") if swapped else ("north", "south")
        cbox(m, (px, y0 + height / 2.0, pz), size, texname, glow=glow, sides=sides, rot_axis="y", rot_angle=a)


# ================================================================================================ the props

def build_dt():
    halo_wheel_tex("aura_dt_halo_wheel")
    band_tex("aura_dt_band", "FFF4FB", "FF7AD9", 240)
    band_tex("aura_dt_band_u", "FFF4FB", "FF7AD9", 240, along_u=True)
    glass_tex("aura_dt_gem", "FFFFFF", "FF9ADF", alpha=255, seed=3)
    glass_tex("aura_dt_glass", "FFE6F6", "FF7AD9", alpha=150, seed=4)
    glass_tex("aura_dt_cuff", "FFB8E8", "C0408E", alpha=190, seed=5)

    # ---- the flaming halo wheel behind the idol's head (standing in the XY plane, facing north)
    m = Model("aura_dt_halo")
    box(m, [-4.0, -4.0, 7.95], [20.0, 20.0, 8.05], "aura_dt_halo_wheel", glow=15, sides=("north", "south"))
    ring(m, (8.0, 8.0, 8.0), 7.2, 1.1, 0.8, 16, "aura_dt_band", "aura_dt_band_u", plane="xy", glow=15, sides=SIDES)
    for k in range(12):
        th = math.radians(90.0 + 30.0 * k)
        cbox(m, (8.0 + 7.2 * math.cos(th), 8.0 + 7.2 * math.sin(th), 8.0), (1.1, 1.1, 1.3), "aura_dt_gem", glow=15)

    # ---- the idol's hands in the meditation mudra (palms up, right over left, thumb tips touching)
    h = Model("aura_dt_hands")
    gl, cu = "aura_dt_glass", "aura_dt_cuff"
    box(h, [1.0, 6.0, 4.0], [13.0, 7.4, 10.0], gl, glow=12)                  # the lower hand
    box(h, [3.0, 7.4, 4.4], [15.0, 8.8, 10.4], gl, glow=12)                  # the upper hand
    box(h, [-2.0, 5.8, 4.6], [1.2, 7.8, 9.6], cu, glow=12)                   # wrists and sleeve cuffs
    box(h, [14.8, 7.2, 4.8], [18.0, 9.2, 9.8], cu, glow=12)
    for z in (5.0, 6.4, 7.8):                                                # finger grooves of the upper hand
        box(h, [9.0, 8.8, z], [14.6, 9.1, z + 1.0], gl, glow=12, sides=("up", "north", "south"))
    cbox(h, (6.0, 9.8, 4.0), (1.4, 4.0, 1.2), gl, glow=12, rot_axis="z", rot_angle=-22.5)   # the thumbs, meeting above
    cbox(h, (10.0, 10.2, 4.0), (1.4, 3.6, 1.2), gl, glow=12, rot_axis="z", rot_angle=22.5)
    cbox(h, (8.0, 11.6, 4.0), (1.6, 1.0, 1.2), "aura_dt_gem", glow=15)      # the point where the thumbs touch


def build_du():
    taiji_tex("aura_du_taiji")
    band_tex("aura_du_jade", "F6FFF9", "7CFFB2", 245)
    band_tex("aura_du_jade_u", "F6FFF9", "7CFFB2", 245, along_u=True)
    metal_tex("aura_du_bronze", "B08A4A", "F0D898", "5A4220", 41, patina="4FA88A")
    for g in RUNES:
        plate_tex("aura_du_plate_" + g, g, "6A4E26", "B6FFD8")

    # ---- the taiji disc (flat in the XZ plane) with a raised jade rim
    m = Model("aura_du_taiji")
    box(m, [0.2, 7.8, 0.2], [15.8, 8.2, 15.8], "aura_du_taiji", glow=15, sides=("up", "down"))
    ring(m, (8.0, 8.3, 8.0), 7.9, 0.8, 1.0, 16, "aura_du_jade", "aura_du_jade_u", glow=15, sides=SIDES)

    # ---- the bronze rune ring with eight engraved plates standing on it, facing out
    r = Model("aura_du_rune_ring")
    ring(r, (8.0, 8.0, 8.0), 12.0, 1.4, 0.8, 16, "aura_du_bronze", "aura_du_bronze", glow=10, sides=SIDES)
    for k, g in enumerate(RUNES):
        theta = 90.0 + 45.0 * k
        px, pz = 8.0 + 12.0 * math.cos(math.radians(theta)), 8.0 + 12.0 * math.sin(math.radians(theta))
        a, swapped = snap(-(theta + 90.0))
        size = (0.6, 3.6, 3.2) if swapped else (3.2, 3.6, 0.6)
        cbox(r, (px, 10.2, pz), size, {"*": "aura_du_plate_" + g, "up": "aura_du_bronze", "down": "aura_du_bronze"}, glow=12,
             rot_axis="y", rot_angle=a)


def build_ia():
    jade_tex("aura_ia_post", "F2F6F0", "C8D8C0", 51, carve="FFE9A8")
    metal_tex("aura_ia_gold", "FFD54A", "FFF6C8", "B08A20", 52)
    stud_tex("aura_ia_stud_lit", True)
    stud_tex("aura_ia_stud_dim", False)
    plaque_tex("aura_ia_plaque")
    door_tex("aura_ia_door")
    light_tex("aura_ia_door_light", 200)
    light_tex("aura_ia_light", 150)
    cloud_tex("aura_ia_cloud", 53)
    cloud_tex("aura_ia_cloud_top", 54, top=True)

    def gate(mid, lit, open_):
        m = Model(mid)
        post, gold = "aura_ia_post", "aura_ia_gold"
        box(m, [-4.0, -4.0, 6.2], [-0.6, 20.0, 9.8], post, glow=12)            # the posts
        box(m, [16.6, -4.0, 6.2], [20.0, 20.0, 9.8], post, glow=12)
        box(m, [-5.0, -5.0, 5.6], [0.4, -3.0, 10.4], gold, glow=12)            # bases
        box(m, [15.6, -5.0, 5.6], [21.0, -3.0, 10.4], gold, glow=12)
        box(m, [-5.0, 19.6, 5.6], [0.4, 21.2, 10.4], post, glow=12)            # cloud-carved capitals
        box(m, [15.6, 19.6, 5.6], [21.0, 21.2, 10.4], post, glow=12)
        box(m, [-2.0, 21.2, 6.4], [18.0, 23.4, 9.6], gold, glow=12)            # the lintel
        box(m, [-7.4, 21.6, 6.6], [-1.6, 23.2, 9.4], gold, glow=12, rot=("z", -22.5, [-1.6, 22.4, 8.0]))   # upturned ends
        box(m, [17.6, 21.6, 6.6], [23.4, 23.2, 9.4], gold, glow=12, rot=("z", 22.5, [17.6, 22.4, 8.0]))
        box(m, [-1.0, 23.4, 7.0], [17.0, 24.8, 9.0], post, glow=12)            # the roof beam
        box(m, [4.8, 15.4, 7.2], [11.2, 20.8, 8.8], {"*": gold, "north": "aura_ia_plaque", "south": "aura_ia_plaque"}, glow=15)
        for i in range(9):                                                      # the nine studs (one lit per layer)
            x = 0.4 + 1.9 * i
            name = "aura_ia_stud_lit" if i < lit else "aura_ia_stud_dim"
            box(m, [x, 21.6, 5.8], [x + 1.2, 22.8, 6.4], name, glow=15 if i < lit else 8)
            box(m, [x, 21.6, 9.6], [x + 1.2, 22.8, 10.2], name, glow=15 if i < lit else 8)
        if open_:
            # the doors have become light and stand open towards the front; the doorway is a sheet of immortal light
            box(m, [-0.6, -4.0, 7.9], [16.6, 15.4, 8.1], "aura_ia_light", glow=15, sides=("north", "south"))
            box(m, [-0.6, -4.0, 7.6], [7.6, 15.4, 8.4], "aura_ia_door_light", glow=15, rot=("y", 45.0, [-0.6, 5.0, 8.0]))
            box(m, [8.4, -4.0, 7.6], [16.6, 15.4, 8.4], "aura_ia_door_light", glow=15, rot=("y", -45.0, [16.6, 5.0, 8.0]))
        else:
            box(m, [-0.6, -4.0, 7.6], [8.0, 15.4, 8.4], "aura_ia_door", glow=10)
            box(m, [8.0, -4.0, 7.6], [16.6, 15.4, 8.4], "aura_ia_door", glow=10)
            cbox(m, (7.0, 6.0, 7.3), (0.8, 0.8, 0.6), "aura_ia_stud_lit", glow=15)   # the door rings
            cbox(m, (9.0, 6.0, 7.3), (0.8, 0.8, 0.6), "aura_ia_stud_lit", glow=15)
        return m

    gate("aura_ia_gate", 0, False)
    gate("aura_ia_gate_open", 9, True)
    for layer in range(1, 10):
        gate("aura_ia_gate_l%d" % layer, layer, False)
        gate("aura_ia_gate_open_l%d" % layer, layer, True)

    # ---- the auspicious cloud scroll under the gate (flat, XZ)
    c = Model("aura_ia_cloud")
    cl, top = "aura_ia_cloud", {"*": "aura_ia_cloud", "up": "aura_ia_cloud_top"}
    box(c, [1.0, 7.0, 4.0], [15.0, 8.8, 12.0], top, glow=12)
    box(c, [3.0, 7.2, 2.0], [13.0, 8.6, 14.0], top, glow=12)
    box(c, [2.0, 8.8, 5.0], [7.0, 10.2, 10.0], top, glow=12)
    box(c, [6.0, 8.8, 4.0], [11.0, 10.8, 11.0], top, glow=12)
    box(c, [10.0, 8.8, 6.0], [14.4, 10.0, 11.0], top, glow=12)
    box(c, [-2.6, 7.4, 6.2], [1.6, 9.2, 9.8], cl, glow=12, rot=("y", 22.5, [-0.5, 8.3, 8.0]))   # the curled ends
    box(c, [14.4, 7.4, 6.2], [18.6, 9.2, 9.8], cl, glow=12, rot=("y", 22.5, [16.5, 8.3, 8.0]))
    cbox(c, (-2.6, 8.3, 8.0), (1.6, 2.2, 2.2), top, glow=12)
    cbox(c, (18.6, 8.3, 8.0), (1.6, 2.2, 2.2), top, glow=12)


def build_ti():
    petal_tex("aura_ti_petal_1", "FFFFFF", "FFF4D8", "FFFFFF", 61)
    petal_tex("aura_ti_petal_2", "FFF8E8", "FFE9A8", "FFFFFF", 62)
    petal_tex("aura_ti_petal_3", "FFF0C8", "FFD54A", "FFF8E0", 63)
    petal_tex("aura_ti_grade_1", "FFFFFF", "FFF4D8", "FFFFFF", 61, rims=3, rim_glow="E0C080")
    petal_tex("aura_ti_grade_2", "FFF8E8", "FFE9A8", "FFFFFF", 62, rims=3, rim_glow="FFC93C")
    petal_tex("aura_ti_grade_3", "FFF0C8", "FFD54A", "FFF8E0", 63, rims=3, rim_glow="FFFFFF")
    metal_tex("aura_ti_gold", "FFD54A", "FFF6C8", "B08A20", 64)
    jade_tex("aura_ti_jade", "FFFFFF", "E8E0C8", 65)
    seat_tex("aura_ti_seat")
    band_tex("aura_ti_band", "FFFFFF", "A8C0FF", 235)
    band_tex("aura_ti_band_u", "FFFFFF", "A8C0FF", 235, along_u=True)
    glass_tex("aura_ti_star", "FFFFFF", "E8F0FF", alpha=255, seed=6)

    def lotus(mid, petals):
        m = Model(mid)
        octagon(m, 8.0, 8.0, 5.0, 0.0, 3.0, "aura_ti_gold", glow=12)                               # the pedestal
        octagon(m, 8.0, 8.0, 7.4, 3.0, 4.2, "aura_ti_jade", top="aura_ti_seat", glow=12)          # the seat
        petal_ring(m, 9, 9.6, 2.8, 4.4, 5.8, 0.6, 0.0, petals[0], glow=12)                          # three tiers of nine
        petal_ring(m, 9, 8.3, 3.4, 5.0, 5.0, 0.6, 360.0 / 27.0, petals[1], glow=13)
        petal_ring(m, 9, 7.0, 4.0, 5.4, 4.4, 0.6, 720.0 / 27.0, petals[2], glow=15)
        return m

    lotus("aura_ti_lotus", ("aura_ti_petal_1", "aura_ti_petal_2", "aura_ti_petal_3"))
    lotus("aura_ti_lotus9", ("aura_ti_grade_1", "aura_ti_grade_2", "aura_ti_grade_3"))

    # ---- the armillary ring of the star wheel (XZ plane; radius 16 px = 2.4 b at scale 2.4) with star studs
    w = Model("aura_ti_wheel")
    ring(w, (8.0, 8.0, 8.0), 16.0, 0.7, 0.5, 16, "aura_ti_band", "aura_ti_band_u", glow=15, sides=SIDES)
    for k in range(8):
        th = math.radians(22.5 * k * 2 + 11.25)
        cbox(w, (8.0 + 16.0 * math.cos(th), 8.0, 8.0 + 16.0 * math.sin(th)), (1.3, 1.3, 1.3), "aura_ti_star", glow=15)
    for k in range(4):
        th = math.radians(90.0 * k)
        cbox(w, (8.0 + 16.0 * math.cos(th), 8.0, 8.0 + 16.0 * math.sin(th)), (0.9, 2.4, 0.9), "aura_ti_gold", glow=15)


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


def check_misc(name, img):
    """Glows must end at alpha 0 on the border, and no magenta."""
    problems = []
    for x in range(img.width):
        for y in (0, img.height - 1):
            if img.get(x, y)[3] > 0:
                problems.append("border alpha at %d,%d" % (x, y))
                break
    for y in range(img.height):
        for x in (0, img.width - 1):
            if img.get(x, y)[3] > 0:
                problems.append("border alpha at %d,%d" % (x, y))
                break
    for p in img.px:
        if p[3] > 0 and p[0] > 200 and p[2] > 200 and p[1] < 80:
            problems.append("magenta pixel")
            break
    return problems[:2]


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


def preview(path):
    """A contact sheet of the quad textures on a dark ground (x2), for checking by eye."""
    names = sorted(MISC)
    cell = 260
    cols = 5
    rows = (len(names) + cols - 1) // cols
    sheet = Image(cols * cell, rows * cell, (18, 14, 26, 255))
    for i, name in enumerate(names):
        img = MISC[name]
        k = max(1, 256 // img.width)
        ox, oy = (i % cols) * cell + 2, (i // cols) * cell + 2
        for y in range(img.height * k):
            for x in range(img.width * k):
                p = img.get(x // k, y // k)
                if p[3] > 0:
                    sheet.blend(ox + x, oy + y, (p[0], p[1] * 0.85, p[2] * 0.7, p[3]))
    sheet.save(path)


def main():
    build_misc()
    build_dt()
    build_du()
    build_ia()
    build_ti()
    errors = 0
    os.makedirs(MISC_DIR, exist_ok=True)
    for name, img in sorted(MISC.items()):
        problems = check_misc(name, img)
        if problems:
            errors += 1
            print("TEXTURE %s: %s" % (name, "; ".join(problems)))
        img.save(os.path.join(MISC_DIR, name + ".png"))
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
        high = max(e["to"][1] for e in m.els)
        print("  %-24s %2d elements  y %6.2f..%6.2f px" % (mid, len(m.els), low, high))
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused prop textures (not written):", ", ".join(unused))
    with open(IDS_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(MODELS)) + "\n")
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    print("aura high: %d quad textures, %d models, %d prop textures, errors: %d" % (len(MISC), len(MODELS), len(used), errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
