"""Generates the world-layer textures of the sword duel's visuals (DESIGN 35.4, module V).

Every texture is drawn with entityTranslucentEmissive or entityTranslucent, which cut texture alpha below 0.1 BEFORE the
vertex colour: every pixel that should show keeps alpha >= 26, fades go into the vertex alpha. The layers are white or
pale grey and are tinted per vertex (steel, qi colour, glowing orange, char black, rope red):

textures/misc/duel/trail_gradient.png  64x32  a blade trail sheet. u = age along the trail (0 the head .. 1 the tail),
                                              v = across the blade (0 the grip .. 1 the tip). Bright and dense at the
                                              tip edge, thin motion streaks running along u, a soft feather at the grip.
textures/misc/duel/scorch_slash.png    64x16  a scorched slash mark (u along the cut, tapered at both ends, a dark
                                              core with ragged ink edges and spatter). Pale; the decal tints it.
textures/misc/duel/scorch_ring.png     64x16  one brush stroke of the ring's ink circle, tileable along u, dry-brush
                                              gaps and bristle lines; v across the stroke.
textures/misc/duel/shock_ring.png      64x64  a shock-wave annulus: a sharp bright front edge, a soft wake inside,
                                              faint radial streaks.
textures/misc/duel/ink_arrow.png       32x32  an ink chevron pointing to +u (the GATHER arrow toward one's mark).
textures/misc/duel/rope.png            32x8   a twisted rope strand, tileable along u (two plies, dark grooves).

Run with: python tools/gen_duel_fx_textures.py
Deterministic: the same files on every run.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator")
OUT = os.path.join(ASSETS, "textures", "misc", "duel")
MIN_A = 26  # the 0.1 alpha cutoff of entityTranslucentEmissive


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def put(img, x, y, rgb, a):
    """A world-layer pixel: alpha a (0..1); under the cutoff it is left fully transparent."""
    byte = int(round(clamp(a) * 255))
    if byte < MIN_A:
        img.set(x, y, (0, 0, 0, 0))
    else:
        img.set(x, y, (int(rgb[0]), int(rgb[1]), int(rgb[2]), byte))


class Noise:
    """Tileable value noise on a period grid."""

    def __init__(self, seed, px, py=None):
        rnd = random.Random(seed)
        self.px = px
        self.py = py or px
        self.grid = [[rnd.random() for _ in range(self.px)] for _ in range(self.py)]

    def at(self, x, y):
        x0 = int(math.floor(x)) % self.px
        y0 = int(math.floor(y)) % self.py
        fx = smooth(x - math.floor(x))
        fy = smooth(y - math.floor(y))
        x1 = (x0 + 1) % self.px
        y1 = (y0 + 1) % self.py
        a = self.grid[y0][x0] * (1 - fx) + self.grid[y0][x1] * fx
        b = self.grid[y1][x0] * (1 - fx) + self.grid[y1][x1] * fx
        return a * (1 - fy) + b * fy


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name))
    print("wrote", name)


def trail_gradient():
    w, h = 64, 32
    img = Image(w, h)
    streaks = Noise(351, 1, 32)  # constant along u, varying across v: motion streaks
    grain = Noise(352, 16, 8)
    for y in range(h):
        v = (y + 0.5) / h  # 0 grip .. 1 tip
        for x in range(w):
            u = (x + 0.5) / w  # 0 head .. 1 tail
            # density rises toward the tip edge, with a bright rim right at the edge
            body = smooth((v - 0.05) / 0.85) ** 1.4
            rim = math.exp(-((v - 0.93) / 0.05) ** 2)
            streak = 0.65 + 0.35 * streaks.at(0.0, v * 32.0)
            tail = (1.0 - u) ** 0.7
            head = math.exp(-(u / 0.06) ** 2) * 0.6
            a = (0.55 * body * streak + 0.55 * rim) * (0.35 + 0.65 * tail) + head * body
            a *= 0.85 + 0.15 * grain.at(u * 16.0, v * 8.0)
            # keep the whole sheet above the cutoff so the vertex alpha does every fade
            a = max(a, 0.12)
            c = 235 + 20 * clamp(rim + head)
            put(img, x, y, (c, c, 255), a)
    save(img, "trail_gradient.png")


def scorch_slash():
    w, h = 64, 16
    img = Image(w, h)
    rnd = random.Random(353)
    edge = Noise(354, 16, 4)
    for y in range(h):
        v = (y + 0.5) / h * 2 - 1  # -1..1 across
        for x in range(w):
            u = (x + 0.5) / w  # 0..1 along
            taper = math.sin(math.pi * clamp(u)) ** 0.6
            half = 0.78 * taper * (0.8 + 0.4 * edge.at(u * 16.0, (v + 1) * 2.0))
            d = abs(v) / max(0.05, half)
            if d > 1.0:
                continue
            core = 1.0 - d ** 2.2
            a = 0.25 + 0.75 * core
            # a pale core line (the cut) inside the dark char
            c = 200 + 55 * math.exp(-(v / 0.12) ** 2) * taper
            put(img, x, y, (c, c, c), a)
    # ink spatter around the slash
    for _ in range(26):
        cx = rnd.uniform(4, w - 4)
        cy = rnd.uniform(1, h - 1)
        r = rnd.uniform(0.5, 1.3)
        for y in range(h):
            for x in range(w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    old = img.get(x, y)
                    a = max(old[3] / 255.0, 0.55 * (1 - d / r) + 0.2)
                    put(img, x, y, (190, 190, 190), a)
    save(img, "scorch_slash.png")


def scorch_ring():
    w, h = 64, 16
    img = Image(w, h)
    gaps = Noise(355, 8, 4)
    bristle = Noise(356, 1, 16)
    for y in range(h):
        v = (y + 0.5) / h * 2 - 1
        for x in range(w):
            u = (x + 0.5) / w
            width = 0.8 + 0.15 * math.sin(u * math.pi * 4)
            d = abs(v) / width
            if d > 1.0:
                continue
            dry = gaps.at(u * 8.0, (v + 1) * 2.0)
            lines = bristle.at(0.0, (v + 1) * 8.0)
            a = (1.0 - d ** 3) * (0.55 + 0.45 * lines)
            if dry > 0.72 and abs(v) > 0.3:
                a *= 0.25  # dry-brush gaps on the edges
            put(img, x, y, (230, 230, 230), a)
    save(img, "scorch_ring.png")


def shock_ring():
    s = 64
    img = Image(s, s)
    radial = Noise(357, 32, 1)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            if r > 1.0:
                continue
            ang = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            front = math.exp(-((r - 0.9) / 0.045) ** 2)
            wake = smooth((r - 0.45) / 0.45) * (1.0 - smooth((r - 0.9) / 0.1)) * 0.45
            streak = 0.75 + 0.25 * radial.at(ang * 32.0, 0.0)
            a = (front + wake * streak)
            if a < 0.1:
                continue
            c = 225 + 30 * front
            put(img, x, y, (c, c, c), a)
    save(img, "shock_ring.png")


def ink_arrow():
    s = 32
    img = Image(s, s)
    rough = Noise(358, 8)
    for y in range(s):
        for x in range(s):
            u = (x + 0.5) / s  # 0..1, the arrow points to +u
            v = (y + 0.5) / s * 2 - 1
            # a chevron: two strokes meeting at u = 0.85
            tip = 0.85
            arm = abs(v)
            target = tip - arm * 0.55
            d = abs(u - target) / 0.11
            ok = arm <= 0.85 and u <= tip + 0.08
            shaft = abs(v) < 0.09 and 0.08 < u < tip - 0.02
            a = 0.0
            if ok and d < 1.0:
                a = (1.0 - d ** 2) * (0.75 + 0.25 * rough.at(u * 8, (v + 1) * 4))
            if shaft:
                a = max(a, (1.0 - (abs(v) / 0.09) ** 2) * 0.8 * (0.4 + 0.6 * u))
            if a <= 0.0:
                continue
            put(img, x, y, (235, 235, 235), a)
    save(img, "ink_arrow.png")


def rope():
    w, h = 32, 8
    img = Image(w, h)
    for y in range(h):
        v = (y + 0.5) / h * 2 - 1
        for x in range(w):
            u = (x + 0.5) / w
            d = abs(v)
            if d > 0.95:
                continue
            # two plies twisting: a diagonal groove pattern that tiles along u
            phase = (u * 4.0 + v * 0.9) % 1.0
            groove = math.exp(-((phase - 0.5) / 0.12) ** 2)
            round_ = math.sqrt(max(0.0, 1.0 - d * d))
            c = 120 + 135 * round_ * (1.0 - 0.55 * groove)
            put(img, x, y, (c, c, c), 1.0 if d < 0.85 else 0.6)
    save(img, "rope.png")


if __name__ == "__main__":
    trail_gradient()
    scorch_slash()
    scorch_ring()
    shock_ring()
    ink_arrow()
    rope()
