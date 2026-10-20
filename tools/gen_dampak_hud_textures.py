"""Generates the HUD textures of phase 10 Dampak Langit (M2 client cinema, DESIGN 28.3, 28.4, 28.2).

All white (or grey) with alpha, tinted at blit time:
textures/gui/dampak/cloud_wipe.png     256x256 a vertically tileable sea of clouds; dense in the middle rows, so a
                                       scrolling blit covers the whole screen at full alpha (the masked cut of the sky ride).
textures/gui/dampak/dampak_streaks.png 256x256 radial impact streaks around a clear centre (the impact frame's tick 1).
textures/gui/dampak/seal_bracket.png   64x64 one corner of the crosshair seal bracket (top-left; mirrored by the blit).

Run with: python tools/gen_dampak_hud_textures.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
GUI = os.path.join(ROOT, "gui", "dampak")


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


def cloud_wipe():
    size = 256
    img = Image(size, size)
    noise = Noise(1010, 16)
    for y in range(size):
        for x in range(size):
            n = noise.fbm(x / 16.0, y / 16.0, 5)
            # billows: soft white puffs over a pale body, always mostly opaque (the wipe must hide the cut)
            puff = smooth((n - 0.30) / 0.35)
            a = 0.78 + 0.22 * puff
            shade = 205 + int(50 * puff)
            img.set(x, y, (shade, shade, min(255, shade + 8), int(clamp(a) * 255)))
    img.save(os.path.join(GUI, "cloud_wipe.png"))


def streaks():
    size = 256
    img = Image(size, size)
    rnd = random.Random(1011)
    c = size / 2.0
    rays = []
    for _ in range(46):
        rays.append((rnd.random() * math.pi * 2, 0.004 + rnd.random() * 0.016, 0.35 + rnd.random() * 0.3, 0.5 + rnd.random() * 0.5))
    for y in range(size):
        for x in range(size):
            dx = (x + 0.5) - c
            dy = (y + 0.5) - c
            r = math.sqrt(dx * dx + dy * dy) / c
            if r < 0.2:
                continue
            ang = math.atan2(dy, dx)
            best = 0.0
            for (a0, width, start, strength) in rays:
                d = abs(math.atan2(math.sin(ang - a0), math.cos(ang - a0)))
                if d > width * 3:
                    continue
                across = clamp(1.0 - d / width)
                along = smooth((r - start) / 0.25) * clamp(1.4 - r * 0.5)
                best = max(best, across * along * strength)
            if best <= 0.02:
                continue
            img.set(x, y, (255, 255, 255, int(clamp(best) * 255)))
    img.save(os.path.join(GUI, "dampak_streaks.png"))


def seal_bracket():
    size = 64
    img = Image(size, size)
    # an L-shaped brush bracket with a small seal dot at the corner, top-left quarter of the crosshair frame
    for y in range(size):
        for x in range(size):
            arm_h = 6 <= y <= 11 and 6 <= x <= 52
            arm_v = 6 <= x <= 11 and 6 <= y <= 52
            if arm_h or arm_v:
                # dry-brush taper toward the arm's end
                along = (x if arm_h else y) / 52.0
                a = 1.0 - smooth((along - 0.65) / 0.35) * 0.85
                img.set(x, y, (255, 255, 255, int(clamp(a) * 255)))
    for y in range(0, 18):
        for x in range(0, 18):
            d = math.sqrt((x - 8.5) ** 2 + (y - 8.5) ** 2)
            if d <= 6.5:
                img.set(x, y, (255, 255, 255, 255))
            elif d <= 8.0:
                img.blend(x, y, (255, 255, 255, 120))
    img.save(os.path.join(GUI, "seal_bracket.png"))


def main():
    os.makedirs(GUI, exist_ok=True)
    cloud_wipe()
    streaks()
    seal_bracket()


if __name__ == "__main__":
    main()
