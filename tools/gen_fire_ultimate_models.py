"""Generates the 3D models of the two Fire Dao ultimates (Api Kaisar / emperor_flame and
Murka Langit Sembilan Matahari / nine_suns_heavens_fury). Everything here is ours: cuboid item models and procedural
textures, nothing is downloaded.

Writes:
  textures/item/fx/emperor_*.png, nine_sun_*.png   32x32 / 64x64 textures (glass gradients, flame silhouettes, rings)
  models/item/fx/<id>.json                         one cuboid model per prop
  items/fx/<id>.json                               the item definitions (kultivator:fx/<id>)

Rules kept by every model (checked by validate()):
  * the anchor is (8, 8, 8) (the petal base, the sun centre, the ring centre); the front faces north (-z);
  * element coordinates within -16..32, uv within 0..16;
  * an element rotation is a single axis ("axis"/"angle") by a multiple of 22.5 between -45 and 45;
  * rotations follow the right hand (Y by +a turns +x towards -z, X by +a turns +y towards +z, Z by +a turns +x towards +y).

Face uv conventions used below (vanilla's default face uv): north u0 at x = to.x, south u0 at x = from.x, west u0 at
z = from.z, east u0 at z = to.z, up v0 at z = from.z, down v0 at z = to.z; on side faces v0 is the top.

Run with: python tools/gen_fire_ultimate_models.py   (validates every model and texture and prints a summary)
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
IDS_FILE = os.path.join(os.path.dirname(__file__), "fx_model_ids.txt")
NS = "kultivator"

TEXTURES = {}
MODELS = {}


# ================================================================================================ helpers

def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp(stops, t):
    """stops: [(t, colour)] sorted by t."""
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
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            c = fn((x + 0.5) / size, (y + 0.5) / size)
            img.set(x, y, (c[0], c[1], c[2], int(max(0, min(255, round(c[3]))))))
    TEXTURES[name] = img
    return name


# ================================================================================================ textures: Api Kaisar

WHITE = rgb("FFF8FF")
PEARL = rgb("F4ECFF")
LILAC = rgb("D8B8FF")
VIOLET = rgb("A866FF")
DEEP_VIOLET = rgb("6A2AC8")
MAGENTA = rgb("E040C0")
HOT_PINK = rgb("FF7AD8")
GOLD_WHITE = rgb("FFF2C8")
GOLD = rgb("FFD060")


def petal_hw(t):
    """Half width (fraction of the texture width) of the petal silhouette at height t (0 base .. 1 tip), < 0 outside:
    a narrow base, the widest point a little below the middle and a pointed tip."""
    if t < 0.0 or t > 1.0:
        return -1.0
    if t <= 0.45:
        k = t / 0.45
        return 0.47 * (0.16 + 0.84 * math.sin(k * math.pi / 2.0) ** 1.5)
    k = (t - 0.45) / 0.55
    return 0.47 * max(0.0, 1.0 - k ** 1.3) ** 1.1


def petal_texture(name, stops, rim, alpha_in, alpha_rim, seed, vein=0.10, shrink=1.0):
    """A glass petal: the silhouette of petal_hw, a colour ramp from the base (t = 0) to the tip, hot edges, faint veins."""
    n = Noise(seed)
    size = 64

    def fn(u, v):
        t = 1.0 - v
        hw = petal_hw(t) * shrink
        du = abs(u - 0.5)
        if hw <= 0.0:
            return (0, 0, 0, 0)
        edge = (hw - du) * size  # pixels to the outline
        if edge < -0.5:
            return (0, 0, 0, 0)
        cover = clamp01(edge + 0.5)
        c = ramp(stops, t ** 0.9)
        # fresnel: lighter towards the outline, a faint cloudy body
        fres = 1.0 - clamp01(edge / (hw * size + 1e-6))
        c = mix(c, PEARL, 0.18 * fres ** 2 + 0.10 * (n.fbm(u * 6.0, v * 6.0) - 0.5))
        # veins converging to the tip
        rel = du / max(1e-6, hw)
        stripe = 0.5 + 0.5 * math.cos(rel * math.pi * 7.0)
        c = mix(c, WHITE, vein * stripe ** 6 * (1.0 - t * 0.6))
        # the midrib
        c = mix(c, WHITE, 0.35 * math.exp(-(du * size / 1.4) ** 2) * (1.0 - t * 0.5))
        a = alpha_in + (alpha_rim - alpha_in) * 0.35 * fres
        # hot white rim (2.5 px) with a soft violet inner band
        if edge < 2.5:
            k = 1.0 - edge / 2.5
            c = mix(c, rim, 0.55 + 0.45 * k)
            a = alpha_in + (alpha_rim - alpha_in) * (0.6 + 0.4 * k)
        return (c[0], c[1], c[2], a * cover)
    return tex(name, size, fn)


def halo_texture(name, colour, seed):
    """A soft glow around the petal outline (expanded by 4 px), faint inside; faded out at the top edge (the halo ends
    where the tip curls away)."""
    size = 64
    col = rgb(colour)

    def fn(u, v):
        # the halo plate spans y 7..28 of the petal: map to the petal's t (y 8..32 -> 0..1)
        y = 28.0 - v * 21.0
        t = (y - 8.0) / 24.0
        # halo plate x spans -1..17 -> petal u
        x = -1.0 + u * 18.0
        pu = x / 16.0
        hw = petal_hw(clamp01(t)) if t >= 0.0 else petal_hw(0.0)
        edge = (hw - abs(pu - 0.5)) * 64.0
        if edge > 0.0:
            a = 40.0
        else:
            a = 150.0 * math.exp(-(edge / 3.4) ** 2)
        a *= smooth(0.0, 0.18, v)  # fade at the top cut
        if t < 0.0:
            a *= clamp01(1.0 + t * 20.0)
        return (col[0], col[1], col[2], a)
    return tex(name, size, fn)


def glow_texture(name, core, body, seed):
    """The hot inner core of a petal: a narrow bright lens, strongest at the base."""
    size = 32
    c0, c1 = rgb(core), rgb(body)

    def fn(u, v):
        t = 1.0 - v
        hw = max(0.02, 0.5 * (0.55 * (1.0 - t) ** 0.6 + 0.08))
        du = abs(u - 0.5)
        k = clamp01(1.0 - du / hw)
        c = mix(c1, c0, k ** 0.7)
        a = 230.0 * k ** 1.4 * (1.0 - 0.75 * t)
        return (c[0], c[1], c[2], a)
    return tex(name, size, fn)


def solid_ramp(name, top, bottom, alpha=255, seed=1, size=32, grain=0.08):
    n = Noise(seed)
    c0, c1 = rgb(top), rgb(bottom)

    def fn(u, v):
        c = mix(c0, c1, v)
        c = mix(c, (255, 255, 255), grain * (n.fbm(u * 5.0, v * 5.0) - 0.4))
        return (c[0], c[1], c[2], alpha)
    return tex(name, size, fn)


def pearl_texture(name, core, rim):
    c0, c1 = rgb(core), rgb(rim)

    def fn(u, v):
        d = math.hypot(u - 0.38, v - 0.36)
        c = mix(c0, c1, clamp01(d * 1.6))
        return (c[0], c[1], c[2], 255)
    return tex(name, 32, fn)


def flame_texture(name, stops, seed, tongues=3, alpha=235, width=0.40):
    """Flame tongues rising from a rounded root at the bottom centre: the body sways with height and splits into
    {@code tongues} licking tips; white-hot at the root and the centre line, the ramp's last colours on the tips."""
    n = Noise(seed)
    size = 64

    def fn(u, v):
        t = 1.0 - v  # 0 bottom .. 1 top
        if t < 0.03 or t > 0.98:
            return (0, 0, 0, 0)
        sway = (n.fbm(t * 2.2 + 5.0, 0.5, 3) - 0.5) * 0.30 * t
        x = u - 0.5 - sway
        # the envelope: a rounded root, widest at a quarter of the height, a long taper
        if t < 0.25:
            env = width * math.sqrt(max(0.0, 1.0 - ((0.25 - t) / 0.24) ** 2))
        else:
            env = width * (1.0 - (t - 0.25) / 0.75) ** 0.85
        # the tongues: above a third of the height the body splits into licking tips
        phase = (x / max(1e-6, width) + 1.0) * 0.5 * tongues + (n.fbm(t * 3.0, x * 2.0 + 9.0) - 0.5) * 1.2
        lick = 0.5 + 0.5 * math.cos(phase * math.tau)
        split = smooth(0.30, 0.95, t)
        env *= 1.0 - split * (1.0 - lick) * 0.95
        edge = (env - abs(x)) / max(1e-6, env)
        if env <= 0.0 or edge <= 0.0:
            return (0, 0, 0, 0)
        heat = clamp01(edge * 0.75 + (1.0 - t) * 0.5)
        c = ramp(stops, 1.0 - heat)
        a = alpha * clamp01(edge * 2.5) * clamp01((0.98 - t) * 6.0)
        return (c[0], c[1], c[2], a)
    return tex(name, size, fn)


def band_texture(name, centre, body, alpha, along_u=False):
    """A ring band profile: transparent edges, a hot centre line (across v, or across u when along_u)."""
    c0, c1 = rgb(centre), rgb(body)

    def fn(u, v):
        s = u if along_u else v
        d = abs(s - 0.5) * 2.0
        k = clamp01(1.0 - d)
        c = mix(c1, c0, k ** 2.2)
        a = alpha * (k ** 0.8)
        return (c[0], c[1], c[2], a)
    return tex(name, 32, fn)


def build_emperor_textures():
    petal_texture("emperor_petal_glass", [(0.0, WHITE), (0.22, PEARL), (0.55, VIOLET), (0.85, MAGENTA), (1.0, HOT_PINK)],
                  rim=WHITE, alpha_in=150, alpha_rim=245, seed=31)
    petal_texture("emperor_petal_back", [(0.0, LILAC), (0.35, rgb("8A4AE8")), (0.75, rgb("B02AA8")), (1.0, MAGENTA)],
                  rim=rgb("FFD8F4"), alpha_in=165, alpha_rim=235, seed=32, vein=0.06)
    petal_texture("emperor_petal_glass_inner", [(0.0, WHITE), (0.40, PEARL), (0.72, LILAC), (1.0, VIOLET)],
                  rim=WHITE, alpha_in=160, alpha_rim=250, seed=33)
    petal_texture("emperor_petal_back_inner", [(0.0, PEARL), (0.45, LILAC), (0.85, VIOLET), (1.0, rgb("C050D0"))],
                  rim=WHITE, alpha_in=170, alpha_rim=240, seed=34, vein=0.05)
    halo_texture("emperor_petal_halo", "D8A8FF", 35)
    glow_texture("emperor_petal_glow", "FFFFFF", "FFB8F0", 36)
    solid_ramp("emperor_petal_rib", "E8D0FF", "FFFFFF", alpha=235, seed=37)
    pearl_texture("emperor_pearl", "FFFFFF", "C890FF")
    solid_ramp("emperor_petal_bract", "B02AA8", "5A1A9A", alpha=230, seed=38, grain=0.15)
    # the seed pod and stamens
    n = Noise(40)

    def pod_top(u, v):
        d = math.hypot(u - 0.5, v - 0.5)
        c = mix(GOLD_WHITE, rgb("E8B0FF"), clamp01(d * 1.8))
        # seed hollows on two rings
        for ring_r, count, off in ((0.0, 1, 0.0), (0.2, 6, 0.0), (0.36, 10, 0.3)):
            for i in range(count):
                a = math.tau * i / count + off
                sx, sy = 0.5 + ring_r * math.cos(a), 0.5 + ring_r * math.sin(a)
                dd = math.hypot(u - sx, v - sy)
                if dd < 0.06:
                    c = mix(WHITE, GOLD, dd / 0.06)
        return (c[0], c[1], c[2], 255)
    tex("emperor_pod_top", 32, pod_top)

    def pod_side(u, v):
        c = mix(GOLD_WHITE, rgb("C890FF"), v ** 1.2)
        stripe = 0.5 + 0.5 * math.cos(u * math.tau * 4.0)
        c = mix(c, WHITE, 0.25 * stripe ** 4 * (1.0 - v))
        c = mix(c, (255, 255, 255), 0.08 * (n.fbm(u * 5, v * 5) - 0.5))
        return (c[0], c[1], c[2], 255)
    tex("emperor_pod_side", 32, pod_side)
    solid_ramp("emperor_pod_collar", "E8C8FF", "8A3AE0", alpha=220, seed=41)
    solid_ramp("emperor_stamen", "FFF6D0", "D8A0FF", alpha=255, seed=42)
    pearl_texture("emperor_anther", "FFFFFF", "FFC850")
    flame_texture("emperor_flame", [(0.0, WHITE), (0.25, rgb("F0D8FF")), (0.55, VIOLET), (0.85, MAGENTA), (1.0, rgb("8A1A9A"))], seed=43, tongues=3)
    flame_texture("emperor_flame_core", [(0.0, WHITE), (0.5, rgb("FFF0FF")), (1.0, LILAC)], seed=44, tongues=2, alpha=250, width=0.26)
    band_texture("emperor_ring", "FFFFFF", "B070FF", 235)
    band_texture("emperor_ring_u", "FFFFFF", "B070FF", 235, along_u=True)
    band_texture("emperor_ring_fade", "F0E0FF", "9A5AE8", 95)
    band_texture("emperor_ring_fade_u", "F0E0FF", "9A5AE8", 95, along_u=True)


# ================================================================================================ textures: nine suns

SUN_WHITE = rgb("FFFFF2")
SUN_PALE = rgb("FFF2B0")
SUN_GOLD = rgb("FFC83A")
SUN_ORANGE = rgb("FF8A1A")
SUN_RED = rgb("D8380E")


def build_sun_textures():
    n = Noise(50)

    def core(u, v):
        g = n.fbm(u * 8.0, v * 8.0)
        c = mix(SUN_WHITE, SUN_PALE, clamp01(g * 1.2 - 0.2))
        return (c[0], c[1], c[2], 255)
    tex("nine_sun_core", 32, core)

    def photo(u, v):
        g = n.fbm(u * 10.0 + 3.0, v * 10.0)
        lane = abs(math.sin(u * 23.0 + g * 5.0) * math.sin(v * 21.0 + g * 4.0))
        c = ramp([(0.0, SUN_PALE), (0.5, SUN_GOLD), (1.0, SUN_ORANGE)], g)
        c = mix(c, SUN_WHITE, 0.3 * (1.0 - lane) ** 3)
        return (c[0], c[1], c[2], 150 + 70 * g)
    tex("nine_sun_photo", 32, photo)

    def halo(u, v):
        g = n.fbm(u * 4.0 + 9.0, v * 4.0)
        c = mix(SUN_GOLD, SUN_ORANGE, g)
        return (c[0], c[1], c[2], 55 + 40 * g)
    tex("nine_sun_halo", 32, halo)

    def flare(u, v):
        dx, dy = u - 0.5, v - 0.5
        r = math.hypot(dx, dy) * 2.0
        ang = math.atan2(dy, dx)
        tongue = n.fbm(math.cos(ang) * 2.5 + 7.0, math.sin(ang) * 2.5 + r * 1.5, 3)
        reach = 0.62 + 0.38 * tongue
        if r > reach or r > 0.98:
            return (0, 0, 0, 0)
        k = r / reach
        c = ramp([(0.0, SUN_WHITE), (0.45, SUN_PALE), (0.7, SUN_GOLD), (0.88, SUN_ORANGE), (1.0, SUN_RED)], k)
        a = 230.0 * clamp01((1.0 - k) * 2.5) * smooth(0.18, 0.42, r)
        return (c[0], c[1], c[2], a)
    tex("nine_sun_flare", 64, flare)

    def spike(u, v, along_u=False):
        s, w = (u, v) if along_u else (v, u)
        d = abs(s - 0.5) * 2.0  # 0 centre .. 1 tip
        width = 0.36 * (1.0 - d) ** 1.25
        dw = abs(w - 0.5)
        if dw > width:
            return (0, 0, 0, 0)
        k = 1.0 - dw / max(1e-6, width)
        c = ramp([(0.0, SUN_WHITE), (0.5, SUN_PALE), (0.8, SUN_GOLD), (1.0, SUN_ORANGE)], d)
        a = 235.0 * k ** 0.7 * (1.0 - d ** 1.5)
        return (c[0], c[1], c[2], a)
    tex("nine_sun_spike", 32, lambda u, v: spike(u, v))
    tex("nine_sun_spike_u", 32, lambda u, v: spike(u, v, True))

    def trail(u, v, along_u=False, core_only=False):
        s, w = (u, v) if along_u else (v, u)  # s: 0 head .. 1 tail end
        width = (0.46 if not core_only else 0.2) * (1.0 - s) ** 0.7
        lick = n.fbm(s * 6.0 + 4.0, w * 3.0)
        width *= 0.75 + 0.5 * lick
        dw = abs(w - 0.5)
        if dw > width or width <= 0.0:
            return (0, 0, 0, 0)
        k = 1.0 - dw / width
        if core_only:
            c = mix(SUN_WHITE, SUN_PALE, s)
        else:
            c = ramp([(0.0, SUN_WHITE), (0.2, SUN_PALE), (0.45, SUN_GOLD), (0.75, SUN_ORANGE), (1.0, SUN_RED)], s + (1.0 - k) * 0.25)
        a = 235.0 * k ** 0.6 * (1.0 - s) ** 1.2 * smooth(0.0, 0.08, s + 0.02)
        return (c[0], c[1], c[2], a)
    tex("nine_sun_trail", 64, lambda u, v: trail(u, v))
    tex("nine_sun_trail_u", 64, lambda u, v: trail(u, v, True))
    tex("nine_sun_trail_core", 32, lambda u, v: trail(u, v, False, True))
    tex("nine_sun_trail_core_u", 32, lambda u, v: trail(u, v, True, True))

    def crater(u, v, inner=False):
        dx, dy = u - 0.5, v - 0.5
        r = math.hypot(dx, dy) * 2.0
        ang = math.atan2(dy, dx)
        if r > 1.0:
            return (0, 0, 0, 0)
        if inner:
            c = mix(SUN_WHITE, SUN_GOLD, r ** 1.5)
            return (c[0], c[1], c[2], 230 * clamp01((1.0 - r) * 3.0))
        crack = 0.0
        for i in range(9):
            a0 = math.tau * i / 9 + 0.3 * math.sin(i * 2.3)
            da = abs(math.atan2(math.sin(ang - a0 - r * 0.6 * math.sin(i)), math.cos(ang - a0 - r * 0.6 * math.sin(i))))
            crack = max(crack, clamp01(1.0 - da * r * 9.0))
        g = n.fbm(u * 7.0 + 2.0, v * 7.0)
        base = ramp([(0.0, SUN_PALE), (0.25, SUN_GOLD), (0.5, SUN_ORANGE), (0.75, SUN_RED), (1.0, rgb("3A1208"))], r)
        c = mix(base, rgb("2A1008"), 0.35 * g * r)
        c = mix(c, SUN_PALE, crack * 0.8 * (1.0 - r * 0.5))
        a = 245.0 * smooth(1.0, 0.8, r)
        return (c[0], c[1], c[2], a)
    tex("nine_sun_crater", 64, lambda u, v: crater(u, v))
    tex("nine_sun_crater_core", 32, lambda u, v: crater(u, v, True))
    band_texture("nine_sun_ring", "FFFFF0", "FFB030", 240)
    band_texture("nine_sun_ring_u", "FFFFF0", "FFB030", 240, along_u=True)
    band_texture("nine_sun_ring_fade", "FFF2C0", "FF8A20", 95)
    band_texture("nine_sun_ring_fade_u", "FFF2C0", "FF8A20", 95, along_u=True)
    # the golden crow (gold-white feathers, vermilion edges)
    m = Noise(51)

    def feather(u, v):
        barb = 0.5 + 0.5 * math.sin((u * 9.0 + v * 3.0) * math.pi)
        c = mix(rgb("FFF4C0"), rgb("F2B030"), v * 0.8 + 0.2 * barb)
        c = mix(c, (255, 255, 255), 0.1 * (m.fbm(u * 6, v * 6) - 0.5))
        if v > 0.82:
            c = mix(c, rgb("E0401A"), (v - 0.82) / 0.18)
        return (c[0], c[1], c[2], 255)
    tex("nine_sun_crow", 32, feather)
    solid_ramp("nine_sun_crow_red", "FF7040", "C0200E", seed=52)
    pearl_texture("nine_sun_crow_eye", "FFFFFF", "FF4020")


# ================================================================================================ the model DSL

SIDES = ("north", "south", "east", "west", "up", "down")


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

    def el(self, f, t, faces, rot=None, glow=15):
        """faces: side -> (texture, uv) ; uv may be None for the full square. rot: (axis, angle, origin)."""
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
    """A box direction angle (degrees) as (angle within -45..45, swapped): a box turned by 90 is the same box with two
    sides swapped, and by 180 the same box."""
    a = ((angle + 90.0) % 180.0) - 90.0  # -90..90
    if a > 45.0:
        return a - 90.0, True
    if a < -45.0:
        return a + 90.0, True
    return a, False


# ================================================================================================ models: Api Kaisar

def petal_model(mid, glass, back, with_halo):
    """A glass petal rising from the anchor (8, 8, 8) to y = 32, its front (outer face) north.

    Layers: the cupped glass sheet (flat centre strip, two side strips turned 22.5 deg back about the vertical, a tip
    curled 22.5 deg back), a hot inner lens, a midrib with its curled tip, three glowing pearls of dew, a base bract and
    (outer ring) a soft halo behind the sheet. The silhouette and the hot rim are in the textures (64x64)."""
    m = Model(mid)

    def vrange(y0, y1):
        # petal y 8..32 -> texture v 16..0
        return 16.0 * (32.0 - y1) / 24.0, 16.0 * (32.0 - y0) / 24.0

    def sheet(x0, x1, y0, y1, rot):
        v0, v1 = vrange(y0, y1)
        faces = {"south": (glass, (x0, v0, x1, v1)), "north": (back, (16.0 - x1, v0, 16.0 - x0, v1))}
        m.el([x0, y0, 7.88], [x1, y1, 8.12], faces, rot=rot)

    top = 28.0
    sheet(3.0, 13.0, 8.0, top, None)
    sheet(0.0, 3.0, 8.0, top, ("y", 22.5, [3.0, 8.0, 8.0]))
    sheet(13.0, 16.0, 8.0, top, ("y", -22.5, [13.0, 8.0, 8.0]))
    sheet(3.0, 13.0, top, 32.0, ("x", 22.5, [8.0, top, 8.0]))
    # the hot inner lens on the inner (south) side
    m.el([4.5, 8.5, 8.3], [11.5, 24.0, 8.42], {"south": ("emperor_petal_glow", None), "north": ("emperor_petal_glow", (16, 0, 0, 16))})
    # midrib and its curled tip
    m.el([7.72, 8.0, 8.12], [8.28, top, 8.36], all_faces("emperor_petal_rib"))
    m.el([7.76, top, 8.12], [8.24, 31.4, 8.34], all_faces("emperor_petal_rib"), rot=("x", 22.5, [8.0, top, 8.0]))
    # dew pearls along the rib
    for y in (12.5, 17.5, 22.5):
        m.cube((8.0, y, 8.62), (0.9, 0.9, 0.5), "emperor_pearl", rot=("y", 45.0, [8.0, y, 8.62]))
    # the base bract on the outer side
    m.el([5.6, 8.0, 7.42], [10.4, 10.6, 7.66], {"north": ("emperor_petal_bract", None), "south": ("emperor_petal_bract", None),
                                                 "up": ("emperor_petal_bract", (0, 0, 16, 2))})
    if with_halo:
        m.el([-1.0, 7.0, 7.52], [17.0, 28.0, 7.6], {"north": ("emperor_petal_halo", (16, 0, 0, 16)), "south": ("emperor_petal_halo", None)},
             glow=15)
    return m


def oct_prism(m, c, r, y0, y1, side, top, glow=15):
    """An octagonal prism (two boxes, the second turned 45 degrees and a hair lower so no caps are coplanar)."""
    m.el([c[0] - r, y0, c[2] - r], [c[0] + r, y1, c[2] + r],
         {"north": (side, None), "south": (side, None), "east": (side, None), "west": (side, None), "up": (top, None),
          "down": (side, None)}, glow=glow)
    m.el([c[0] - r, y0 + 0.02, c[2] - r], [c[0] + r, y1 - 0.04, c[2] + r],
         {"north": (side, None), "south": (side, None), "east": (side, None), "west": (side, None), "up": (top, None),
          "down": (side, None)}, rot=("y", 45.0, list(c)), glow=glow)


def core_model():
    """The lotus heart: a golden seed pod with glowing seeds, a violet collar and a crown of eight stamens with
    anthers (the four cardinal ones lean out by 22.5 degrees)."""
    m = Model("emperor_lotus_core")
    c = (8.0, 8.0, 8.0)
    oct_prism(m, c, 5.6, 8.0, 8.7, "emperor_pod_collar", "emperor_pod_collar")
    oct_prism(m, c, 4.2, 8.7, 11.6, "emperor_pod_side", "emperor_pod_top")
    # seeds on the pod top
    for (x, z) in ((8.0, 8.0), (9.6, 8.0), (6.4, 8.0), (8.0, 9.6), (8.0, 6.4)):
        m.cube((x, 11.85, z), (0.9, 0.5, 0.9), "emperor_anther", rot=("y", 45.0, [x, 11.85, z]))
    h = 6.4
    lean = math.radians(22.5)
    # cardinal stamens lean outwards (one-axis turns: +x and -x about z, +z and -z about x)
    for (dx, dz, axis, angle) in ((1, 0, "z", -22.5), (-1, 0, "z", 22.5), (0, 1, "x", 22.5), (0, -1, "x", -22.5)):
        bx, bz = 8.0 + dx * 4.8, 8.0 + dz * 4.8
        m.el([bx - 0.25, 9.0, bz - 0.25], [bx + 0.25, 9.0 + h, bz + 0.25], all_faces("emperor_stamen"), rot=(axis, angle, [bx, 9.0, bz]))
        tx, ty, tz = bx + dx * h * math.sin(lean), 9.0 + h * math.cos(lean), bz + dz * h * math.sin(lean)
        m.cube((tx, ty, tz), (1.1, 1.3, 1.1), "emperor_anther", rot=("y", 45.0, [tx, ty, tz]))
    # diagonal stamens stand straight, a little shorter
    for (dx, dz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        bx, bz = 8.0 + dx * 3.6, 8.0 + dz * 3.6
        m.el([bx - 0.22, 9.0, bz - 0.22], [bx + 0.22, 9.0 + h * 0.8, bz + 0.22], all_faces("emperor_stamen"))
        m.cube((bx, 9.0 + h * 0.8 + 0.5, bz), (1.0, 1.1, 1.0), "emperor_anther", rot=("y", 45.0, [bx, 9.0 + h * 0.8 + 0.5, bz]))
    return m


def flame_model():
    """The flame of the lotus heart: four crossed flame sheets (45 degrees apart) around four narrower white-hot sheets,
    with six embers floating in the flame."""
    m = Model("emperor_lotus_flame")

    def sheet(x0, x1, y0, y1, name, angle):
        a, swapped = norm_angle(angle)
        if not swapped:
            m.el([x0, y0, 7.92], [x1, y1, 8.08], {"north": (name, (16, 0, 0, 16)), "south": (name, None)},
                 rot=("y", a, [8.0, y0, 8.0]))
        else:
            m.el([7.92, y0, x0], [8.08, y1, x1], {"east": (name, (16, 0, 0, 16)), "west": (name, None)},
                 rot=("y", a, [8.0, y0, 8.0]))
    for angle in (0.0, 45.0, 90.0, 135.0):
        sheet(0.0, 16.0, 10.0, 32.0, "emperor_flame", angle)
    for angle in (22.5, 67.5, 112.5, 157.5):
        sheet(4.0, 12.0, 10.5, 27.0, "emperor_flame_core", angle)
    rnd = random.Random(60)
    for i in range(6):
        a = math.tau * i / 6 + 0.4
        r = 3.0 + rnd.random() * 2.5
        y = 17.0 + rnd.random() * 11.0
        p = (8.0 + r * math.cos(a), y, 8.0 + r * math.sin(a))
        m.cube(p, (0.7, 0.7, 0.7), "emperor_anther", rot=("y", 45.0, list(p)))
    return m


def ring_model(mid, band, band_u, radius=20.0, inner=16.6, width=3.0, inner_width=1.2):
    """A flat ring of 16 band segments (and a thin inner ring of 16) in the XZ plane, centred on the anchor."""
    m = Model(mid)
    for (r, w, th, y) in ((radius, width, 0.5, 8.0), (inner, inner_width, 0.4, 8.12)):
        length = 2.0 * math.pi * r / 16.0 * 1.06
        for i in range(16):
            theta = 22.5 * i
            px, pz = 8.0 + r * math.cos(math.radians(theta)), 8.0 + r * math.sin(math.radians(theta))
            # a box along x turned by b about y points along (cos b, -sin b): the tangent at theta needs b = -(theta + 90)
            a, swapped = norm_angle(-(theta + 90.0))
            k = 0.97 if i % 2 else 1.0  # neighbours overlap: every other one a hair thinner (no coplanar faces)
            if not swapped:
                size = (length, th * k, w * k)
                faces = {"up": (band, None), "down": (band, None)}
            else:
                size = (w * k, th * k, length)
                faces = {"up": (band_u, None), "down": (band_u, None)}
            f = [px - size[0] / 2, y - size[1] / 2, pz - size[2] / 2]
            t = [px + size[0] / 2, y + size[1] / 2, pz + size[2] / 2]
            m.el(f, t, faces, rot=("y", a, [px, y, pz]))
    return m


def build_emperor_models():
    petal_model("emperor_lotus_petal_outer", "emperor_petal_glass", "emperor_petal_back", True)
    petal_model("emperor_lotus_petal_inner", "emperor_petal_glass_inner", "emperor_petal_back_inner", False)
    core_model()
    flame_model()
    ring_model("emperor_shock_ring", "emperor_ring", "emperor_ring_u")
    ring_model("emperor_shock_ring_fade", "emperor_ring_fade", "emperor_ring_fade_u")


# ================================================================================================ models: nine suns

def sun_model():
    """A sun: a white-hot core, a translucent granulated photosphere, a faint halo shell, a crown of five crossed corona
    plates and sixteen flame spikes in three planes (all symmetric through the centre, so any turn reads the same)."""
    m = Model("nine_sun_orb")
    c = (8.0, 8.0, 8.0)

    def ball(r, name, boxes):
        s = 2 * r * 0.82
        m.cube(c, (s, s, s), name)
        if boxes >= 2:
            m.cube(c, (s, s * 0.9, s), name, rot=("y", 45.0, list(c)))
        if boxes >= 3:
            m.cube(c, (s * 0.9, s, s), name, rot=("x", 45.0, list(c)))
    ball(5.0, "nine_sun_core", 3)
    ball(6.6, "nine_sun_photo", 3)
    ball(8.4, "nine_sun_halo", 2)
    # corona plates (32 px wide, through the centre): XY, XZ, YZ and the two XY diagonals
    m.el([-8.0, -8.0, 7.96], [24.0, 24.0, 8.04], {"north": ("nine_sun_flare", None), "south": ("nine_sun_flare", None)})
    m.el([-8.0, 7.95, -8.0], [24.0, 8.05, 24.0], {"up": ("nine_sun_flare", None), "down": ("nine_sun_flare", None)})
    m.el([7.94, -8.0, -8.0], [8.06, 24.0, 24.0], {"east": ("nine_sun_flare", None), "west": ("nine_sun_flare", None)})
    for a in (45.0, -45.0):
        m.el([-6.0, -6.0, 7.9], [22.0, 22.0, 7.98], {"north": ("nine_sun_flare", None), "south": ("nine_sun_flare", None)},
             rot=("y", a, list(c)))
    # spikes in the XY plane: eight directions 22.5 deg apart (long ones on the diagonals and axes)
    for i in range(8):
        direction = 22.5 * i  # angle from +x towards +y
        length = 30.0 if i % 2 == 0 else 22.0
        w = 2.2 if i % 2 == 0 else 3.4
        # a box along y turned by b about z points along (-sin b, cos b): direction d needs b = d - 90
        a, swapped = norm_angle(direction - 90.0)
        if not swapped:
            m.el([8 - w / 2, 8 - length / 2, 7.97], [8 + w / 2, 8 + length / 2, 8.03],
                 {"north": ("nine_sun_spike", None), "south": ("nine_sun_spike", None)}, rot=("z", a, list(c)))
        else:
            m.el([8 - length / 2, 8 - w / 2, 7.97], [8 + length / 2, 8 + w / 2, 8.03],
                 {"north": ("nine_sun_spike_u", None), "south": ("nine_sun_spike_u", None)}, rot=("z", a, list(c)))
    # spikes in the XZ plane: four directions 45 deg apart, offset by 22.5 from the XY ones
    for i in range(4):
        direction = 22.5 + 45.0 * i
        a, swapped = norm_angle(direction)
        length, w = 26.0, 2.6
        if not swapped:
            m.el([8 - length / 2, 7.97, 8 - w / 2], [8 + length / 2, 8.03, 8 + w / 2],
                 {"up": ("nine_sun_spike_u", None), "down": ("nine_sun_spike_u", None)}, rot=("y", a, list(c)))
        else:
            m.el([8 - w / 2, 7.97, 8 - length / 2], [8 + w / 2, 8.03, 8 + length / 2],
                 {"up": ("nine_sun_spike", None), "down": ("nine_sun_spike", None)}, rot=("y", a, list(c)))
    # spikes in the YZ plane
    for i in range(4):
        direction = 45.0 * i
        a, swapped = norm_angle(direction)
        length, w = 26.0, 2.6
        if not swapped:
            m.el([7.97, 8 - length / 2, 8 - w / 2], [8.03, 8 + length / 2, 8 + w / 2],
                 {"east": ("nine_sun_spike", None), "west": ("nine_sun_spike", None)}, rot=("x", a, list(c)))
        else:
            m.el([7.97, 8 - w / 2, 8 - length / 2], [8.03, 8 + w / 2, 8 + length / 2],
                 {"east": ("nine_sun_spike_u", None), "west": ("nine_sun_spike_u", None)}, rot=("x", a, list(c)))
    return m


def trail_model():
    """The streak behind a falling sun: eight flame sheets around the travel axis (+z is behind the front), 22.5 deg
    apart, plus two white-hot core sheets. The sheets run from the sun's centre (z = 6) to z = 32."""
    m = Model("nine_sun_trail")
    z0, z1 = 6.0, 32.0

    def x_sheet(half, name, angle):
        # thin in x: west face u0 at z0 (head), east face u0 at z1 (flip)
        m.el([7.94, 8 - half, z0], [8.06, 8 + half, z1], {"west": (name + "_u", None), "east": (name + "_u", (16, 0, 0, 16))},
             rot=("z", angle, [8.0, 8.0, 8.0]))

    def y_sheet(half, name, angle):
        # thin in y: up face v0 at z0 (head), down face v0 at z1 (flip)
        m.el([8 - half, 7.94, z0], [8 + half, 8.06, z1], {"up": (name, None), "down": (name, (0, 16, 16, 0))},
             rot=("z", angle, [8.0, 8.0, 8.0]))
    for a in (-45.0, -22.5, 0.0, 22.5, 45.0):
        x_sheet(8.0, "nine_sun_trail", a)
    for a in (-22.5, 0.0, 22.5):
        y_sheet(8.0, "nine_sun_trail", a)
    x_sheet(3.0, "nine_sun_trail_core", 0.0)
    y_sheet(3.0, "nine_sun_trail_core", 0.0)
    return m


def crater_model():
    """A glowing crater decal: a molten disc with radiating cracks and a white-hot pool on top (flat, at the anchor)."""
    m = Model("nine_sun_crater")
    m.el([-6.0, 8.0, -6.0], [22.0, 8.12, 22.0], {"up": ("nine_sun_crater", None), "down": ("nine_sun_crater", None)})
    m.el([3.0, 8.16, 3.0], [13.0, 8.24, 13.0], {"up": ("nine_sun_crater_core", None)})
    return m


def crow_model():
    """A three-legged golden crow gliding with spread wings (terinspirasi dari Sanzuwu, the sun crow of Chinese myth).
    Front (beak) north."""
    m = Model("nine_sun_crow")
    gold, red, eye = "nine_sun_crow", "nine_sun_crow_red", "nine_sun_crow_eye"
    m.el([6.6, 6.6, 5.0], [9.4, 9.2, 12.0], all_faces(gold))                               # body
    m.el([6.9, 8.4, 2.8], [9.1, 10.8, 5.4], all_faces(gold))                               # head
    m.el([7.5, 8.9, 1.0], [8.5, 9.7, 2.8], all_faces(red))                                 # beak
    m.el([7.7, 10.8, 3.4], [8.3, 12.2, 5.0], all_faces(red))                               # crest
    m.cube((6.85, 9.9, 3.6), (0.3, 0.5, 0.5), eye)                                         # eyes
    m.cube((9.15, 9.9, 3.6), (0.3, 0.5, 0.5), eye)
    # wings: raised 22.5 degrees (left wing towards -x: Z by -22.5 lifts its tip; right wing: Z by +22.5)
    m.el([-4.0, 8.4, 5.6], [6.8, 9.0, 11.0], all_faces(gold), rot=("z", -22.5, [6.8, 8.7, 8.3]))
    m.el([9.2, 8.4, 5.6], [20.0, 9.0, 11.0], all_faces(gold), rot=("z", 22.5, [9.2, 8.7, 8.3]))
    # primaries: the wing tips sweep back
    m.el([-7.0, 8.6, 9.0], [0.0, 9.0, 13.5], all_faces(red), rot=("z", -22.5, [6.8, 8.7, 8.3]))
    m.el([16.0, 8.6, 9.0], [23.0, 9.0, 13.5], all_faces(red), rot=("z", 22.5, [9.2, 8.7, 8.3]))
    # tail fan
    for a in (-22.5, 0.0, 22.5):
        m.el([7.1, 7.8, 12.0], [8.9, 8.2, 18.0], all_faces(gold if a == 0.0 else red), rot=("y", a, [8.0, 8.0, 12.0]))
    # three legs with talons
    for x in (7.0, 8.0, 9.0):
        z = 8.0 if x == 8.0 else 7.2
        m.el([x - 0.2, 4.4, z - 0.2], [x + 0.2, 6.7, z + 0.2], all_faces(red))
        m.el([x - 0.45, 4.1, z - 0.9], [x + 0.45, 4.4, z + 0.3], all_faces(red))
    return m


def build_sun_models():
    sun_model()
    trail_model()
    crater_model()
    ring_model("nine_sun_flash_ring", "nine_sun_ring", "nine_sun_ring_u")
    ring_model("nine_sun_flash_ring_fade", "nine_sun_ring_fade", "nine_sun_ring_fade_u")
    crow_model()


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
            ang = rot["angle"]
            if ang not in (-45.0, -22.5, 0.0, 22.5, 45.0):
                problems.append("element %d angle %s" % (i, ang))
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
    build_emperor_textures()
    build_sun_textures()
    build_emperor_models()
    build_sun_models()
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
        print("  %-28s %2d elements  %s" % (mid, len(m.els), ", ".join(sorted(m.tex))))
    os.makedirs(TEX_DIR, exist_ok=True)
    for name in sorted(used):
        TEXTURES[name].save(os.path.join(TEX_DIR, name + ".png"))
    unused = sorted(set(TEXTURES) - used)
    if unused:
        print("unused textures (not written):", ", ".join(unused))
    # keep the shared id list complete (gen_fx_model_textures.py writes the rest)
    ids = set()
    if os.path.exists(IDS_FILE):
        with open(IDS_FILE, encoding="utf-8") as f:
            ids = {line.strip() for line in f if line.strip()}
    ids.update(MODELS)
    with open(IDS_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(ids)) + "\n")
    print("fire ultimate models: %d, textures: %d, errors: %d" % (len(MODELS), len(used), errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
