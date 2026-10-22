"""Generates the textures of Sekte yang Hidup module G, Ujian Tangga Seribu (DESIGN 36.2.4).

All are white or near white (tinted per vertex in game) and drawn with entityTranslucentEmissive, which cuts texture
alpha below 0.1 before the vertex colour: alpha >= 26 wherever the layer should show, 0 where it should not. The look of
a "heavy shader" (a jade stair of light in the sky) is faked with these layered additive-looking quads (DESIGN 36.0 rule 5).

textures/sekte/tangga/step.png   64x64 one tread of the illusory stair: a bright nosing line at the front edge (v 0),
                                 rails on both sides (u 0 and 63), a translucent jade body with faint carved cloud bands
                                 and a soft glow that thins toward the back.
textures/sekte/tangga/riser.png  64x32 the riser under a tread: brightest at the top edge, a row of carved notches, fading
                                 to nothing at the bottom.
textures/sekte/tangga/mist.png   64x64 a soft round cloud puff (the mist under the stair), mottled, fading at the edge.
textures/sekte/tangga/peak.png   128x128 the gate at the summit ("South Gate of Heaven"): a three-bay gate silhouette with
                                 tiled roofs drawn in light, a radial halo behind it.
textures/sekte/tangga/face.png   64x64 a heart demon's face (the illusions at steps 300, 600, 900): a hollow mask, glowing
                                 eye slits and a crooked grin, smoky edge.
textures/sekte/tangga/ring.png   64x64 the projector's ring: two circles and eight trigram dashes between them.

Run with: python tools/gen_sekte_tangga_assets.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
OUT = os.path.join(ROOT, "sekte", "tangga")


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


class Noise:
    """Tileable value noise on a period grid."""

    def __init__(self, seed, period):
        rnd = random.Random(seed)
        self.period = period
        self.grid = [[rnd.random() for _ in range(period)] for _ in range(period)]

    def at(self, x, y):
        p = self.period
        x0 = int(math.floor(x)) % p
        y0 = int(math.floor(y)) % p
        x1 = (x0 + 1) % p
        y1 = (y0 + 1) % p
        fx = smooth(x - math.floor(x))
        fy = smooth(y - math.floor(y))
        a = self.grid[y0][x0] + (self.grid[y0][x1] - self.grid[y0][x0]) * fx
        b = self.grid[y1][x0] + (self.grid[y1][x1] - self.grid[y1][x0]) * fx
        return a + (b - a) * fy

    def fbm(self, x, y, octaves=3):
        total = 0.0
        amp = 0.5
        freq = 1.0
        for _ in range(octaves):
            total += self.at(x * freq, y * freq) * amp
            amp *= 0.5
            freq *= 2.0
        return total


def put(img, x, y, white, alpha):
    a = int(round(clamp(alpha) * 255))
    if a < 26:
        a = 0
    w = int(round(clamp(white) * 255))
    img.set(x, y, (w, w, w, a))


def step():
    size = 64
    img = Image(size, size)
    noise = Noise(11, 8)
    for y in range(size):
        v = y / (size - 1)
        for x in range(size):
            u = x / (size - 1)
            nosing = math.exp(-((v - 0.03) / 0.045) ** 2)
            rail = max(math.exp(-((u - 0.02) / 0.03) ** 2), math.exp(-((u - 0.98) / 0.03) ** 2))
            body = 0.34 + 0.22 * (1.0 - v)
            # faint carved cloud bands (auspicious cloud scrolls) across the tread
            band = 0.5 + 0.5 * math.sin((u * 3.0 + noise.fbm(u * 4, v * 4) * 0.8) * math.pi * 2)
            carve = 0.10 * smooth((band - 0.82) / 0.18) * (1.0 - nosing)
            alpha = clamp(body + carve + 0.65 * nosing + 0.45 * rail)
            white = clamp(0.80 + 0.2 * max(nosing, rail) + 0.1 * noise.at(u * 8, v * 8))
            put(img, x, y, white, alpha)
    return img


def riser():
    w, h = 64, 32
    img = Image(w, h)
    for y in range(h):
        v = y / (h - 1)
        for x in range(w):
            u = x / (w - 1)
            top = math.exp(-(v / 0.12) ** 2)
            fade = (1.0 - v) ** 1.6
            notch = 0.0
            # a row of small carved notches a third of the way down
            cell = (u * 16) % 1.0
            if 0.38 < v < 0.52 and 0.35 < cell < 0.65:
                notch = 0.25
            rail = max(math.exp(-((u - 0.02) / 0.03) ** 2), math.exp(-((u - 0.98) / 0.03) ** 2))
            alpha = clamp(0.20 * fade + 0.75 * top + notch * fade + 0.35 * rail * fade)
            put(img, x, y, 0.85 + 0.15 * top, alpha)
    return img


def mist():
    size = 64
    img = Image(size, size)
    noise = Noise(23, 8)
    for y in range(size):
        for x in range(size):
            dx = (x + 0.5) / size - 0.5
            dy = (y + 0.5) / size - 0.5
            r = math.sqrt(dx * dx + dy * dy) * 2.0
            n = noise.fbm(x / 8.0, y / 8.0, 4)
            edge = smooth((1.0 - r) / 0.55)
            alpha = clamp(edge * (0.35 + 0.65 * n) * 0.85)
            put(img, x, y, 0.92 + 0.08 * n, alpha)
    return img


def peak():
    size = 128
    img = Image(size, size)
    c = size / 2.0
    lines = []

    def rect(x0, y0, x1, y1):
        lines.append((x0, y0, x1, y1))

    # three-bay gate: four pillars, a lintel, a central roof and two lower side roofs
    for px in (30, 50, 78, 98):
        rect(px - 2, 52, px + 2, 112)
    rect(26, 62, 102, 66)
    rect(26, 74, 102, 77)
    rect(58, 46, 70, 60)  # the plaque

    # curved roofs with upturned eaves (centre x, ridge y, half width, thickness)
    roofs = [(64, 42, 34, 3), (64, 34, 22, 2), (37, 56, 18, 2), (91, 56, 18, 2)]

    def inside(x, y):
        for x0, y0, x1, y1 in lines:
            if x0 <= x <= x1 and y0 <= y <= y1:
                return True
        for cx, ry, half, thick in roofs:
            dx = (x - cx) / half
            if abs(dx) <= 1.0:
                curve = ry - 5.0 * dx ** 4
                if curve - thick <= y <= curve + thick * 0.5:
                    return True
        return False

    # distance field to the drawn shapes (coarse, small image)
    solid = [[inside(x, y) for x in range(size)] for y in range(size)]
    dist = [[99.0] * size for _ in range(size)]
    pts = [(x, y) for y in range(size) for x in range(size) if solid[y][x]]
    for y in range(size):
        for x in range(size):
            if solid[y][x]:
                dist[y][x] = 0.0
    for (sx, sy) in pts[::3]:
        for y in range(max(0, sy - 6), min(size, sy + 7)):
            for x in range(max(0, sx - 6), min(size, sx + 7)):
                d = math.hypot(x - sx, y - sy)
                if d < dist[y][x]:
                    dist[y][x] = d
    for y in range(size):
        for x in range(size):
            r = math.hypot(x + 0.5 - c, y + 0.5 - c * 1.05) / c
            halo = 0.55 * smooth((1.0 - r) / 0.9) ** 1.4
            line = 1.0 if dist[y][x] == 0.0 else 0.6 * math.exp(-(dist[y][x] / 2.2) ** 2)
            alpha = clamp(max(line, halo))
            put(img, x, y, 0.85 + 0.15 * line, alpha)
    return img


def face():
    size = 64
    img = Image(size, size)
    noise = Noise(37, 8)
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size - 0.5
            v = (y + 0.5) / size - 0.5
            # an oval mask, narrower at the chin
            oval = (u / (0.36 - 0.08 * max(0.0, v))) ** 2 + (v / 0.44) ** 2
            smoke = noise.fbm(x / 6.0, y / 6.0, 3)
            edge = smooth((1.0 - oval) / 0.35) * (0.55 + 0.45 * smoke)
            alpha = 0.32 * edge
            white = 0.7
            # slanted eye slits
            for side in (-1, 1):
                ex = u - side * 0.14
                ey = v + 0.08 + side * ex * 0.25
                eye = (ex / 0.09) ** 2 + (ey / 0.028) ** 2
                if eye < 1.0:
                    alpha = max(alpha, 0.95 * (1.0 - eye) + 0.4)
                    white = 1.0
            # a crooked grin
            gy = v - 0.17 - 0.12 * u * u - 0.03 * u
            if abs(u) < 0.2 and abs(gy) < 0.018 + 0.01 * (1 - abs(u) / 0.2):
                alpha = max(alpha, 0.85)
                white = 1.0
            put(img, x, y, white, alpha)
    return img


def ring():
    size = 64
    img = Image(size, size)
    c = size / 2.0
    for y in range(size):
        for x in range(size):
            dx = x + 0.5 - c
            dy = y + 0.5 - c
            r = math.hypot(dx, dy) / c
            a = math.atan2(dy, dx)
            outer = math.exp(-((r - 0.92) / 0.035) ** 2)
            inner = math.exp(-((r - 0.62) / 0.03) ** 2)
            # eight trigram dashes between the circles: three bars each, broken by the sector index
            sector = (a / (math.pi * 2) * 8) % 1.0
            index = int(((a / (math.pi * 2)) % 1.0) * 8)
            dash = 0.0
            if 0.2 < sector < 0.8:
                for bar in range(3):
                    rr = 0.70 + bar * 0.07
                    broken = (index >> bar) & 1
                    if abs(r - rr) < 0.018 and not (broken and 0.42 < sector < 0.58):
                        dash = 1.0
            glow = 0.25 * math.exp(-((r - 0.77) / 0.2) ** 2)
            alpha = clamp(max(outer, inner * 0.9, dash * 0.9, glow) if r < 1.0 else 0.0)
            put(img, x, y, 0.9 + 0.1 * max(outer, dash), alpha)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, image in (("step", step()), ("riser", riser()), ("mist", mist()), ("peak", peak()), ("face", face()), ("ring", ring())):
        path = os.path.join(OUT, name + ".png")
        image.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
