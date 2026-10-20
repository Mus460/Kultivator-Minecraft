"""Generates the world textures of Phase 10 Dampak Langit, module M3 (DESIGN 28.8.3, 28.9.2, 28.7).

Both are white (tinted per vertex in game) and drawn with entityTranslucentEmissive, which cuts texture alpha below 0.1
before the vertex colour: alpha >= 26 wherever the layer should show, 0 where it should not.

textures/misc/dampak/ground_glow.png   64x64 a crater-shaped glow: a hot molten rim, a softer bowl and a bright heart,
                                       mottled like cooling rock, fading to nothing at the edge (also the sigil node discs).
textures/misc/dampak/crack_decal.png   64x64 glowing cracks radiating from the centre with cross fractures, a soft halo
                                       around each crack, fading at the edge.

Run with: python tools/gen_dampak_world_textures.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
MISC = os.path.join(ROOT, "misc", "dampak")


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
        fx = smooth(x - math.floor(x))
        fy = smooth(y - math.floor(y))
        x1 = (x0 + 1) % p
        y1 = (y0 + 1) % p
        a = self.grid[y0][x0] * (1 - fx) + self.grid[y0][x1] * fx
        b = self.grid[y1][x0] * (1 - fx) + self.grid[y1][x1] * fx
        return a * (1 - fy) + b * fy

    def fbm(self, x, y, octaves=4):
        total = 0.0
        amp = 0.5
        freq = 1.0
        for _ in range(octaves):
            total += amp * self.at(x * freq, y * freq)
            amp *= 0.5
            freq *= 2.0
        return total


def ground_glow():
    size = 64
    img = Image(size, size)
    noise = Noise(28, 16)
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2.0 - 1.0
            v = (y + 0.5) / size * 2.0 - 1.0
            r = math.sqrt(u * u + v * v)
            if r >= 1.0:
                continue
            mottle = 0.72 + 0.28 * noise.fbm(x / 6.0, y / 6.0)
            rim = math.exp(-((r - 0.66) / 0.14) ** 2)
            bowl = 0.45 * (1.0 - smooth(r / 0.75))
            heart = 0.55 * math.exp(-(r / 0.22) ** 2)
            edge = 1.0 - smooth((r - 0.82) / 0.18)
            a = clamp((0.2 + 0.8 * rim + bowl + heart) * mottle * edge)
            if a < 0.1:
                continue
            white = int(200 + 55 * clamp(rim + heart))
            img.set(x, y, (white, white, white, int(a * 255)))
    img.save(os.path.join(MISC, "ground_glow.png"))


def crack_decal():
    size = 64
    rnd = random.Random(2810)
    strength = [[0.0] * size for _ in range(size)]

    def stamp(px, py, w):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                qx = int(px) + dx
                qy = int(py) + dy
                if 0 <= qx < size and 0 <= qy < size:
                    d = math.sqrt((qx + 0.5 - px) ** 2 + (qy + 0.5 - py) ** 2)
                    core = math.exp(-(d / max(0.35, w)) ** 2)
                    strength[qy][qx] = max(strength[qy][qx], core)

    def crack(x, y, angle, length, width, depth):
        steps = int(length)
        for _ in range(steps):
            stamp(x, y, width)
            angle += rnd.uniform(-0.35, 0.35)
            x += math.cos(angle)
            y += math.sin(angle)
            width = max(0.4, width * 0.985)
            if depth > 0 and rnd.random() < 0.06:
                crack(x, y, angle + rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.2), length * 0.35, width * 0.7, depth - 1)

    centre = size / 2.0
    arms = 9
    for i in range(arms):
        angle = math.pi * 2.0 * i / arms + rnd.uniform(-0.2, 0.2)
        crack(centre, centre, angle, rnd.uniform(20, 30), 1.4, 2)
    # cross fractures: short arcs around the centre
    for ring in (9.0, 17.0):
        for i in range(7):
            start = rnd.uniform(0, math.pi * 2.0)
            span = rnd.uniform(0.3, 0.6)
            for k in range(12):
                a = start + span * k / 11.0
                stamp(centre + math.cos(a) * ring, centre + math.sin(a) * ring, 0.6)

    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2.0 - 1.0
            v = (y + 0.5) / size * 2.0 - 1.0
            r = math.sqrt(u * u + v * v)
            edge = 1.0 - smooth((r - 0.78) / 0.2)
            s = strength[y][x] * edge
            if s < 0.12:
                continue
            a = clamp(0.1 + 0.9 * s)
            white = int(180 + 75 * s)
            img.set(x, y, (white, white, white, int(a * 255)))
    img.save(os.path.join(MISC, "crack_decal.png"))


if __name__ == "__main__":
    os.makedirs(MISC, exist_ok=True)
    ground_glow()
    crack_decal()
    print("Dampak world textures written to", os.path.normpath(MISC))
