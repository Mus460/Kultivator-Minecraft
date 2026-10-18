"""Generates the textures of phase 7b Momen Epik (P7b-sinema, DESIGN 23.10, 23.8, 23.11).

World layers (white or grey, tinted per vertex in game; alpha >= 26 wherever the layer should show, because
entityTranslucentEmissive cuts texture alpha below 0.1 before the vertex colour):
textures/misc/epic/cloud_swirl.png   128x128 tileable storm-cloud swirl; u runs around the vortex, v outwards.
textures/misc/epic/glow_strip.png    32x32 soft glowing strip; u along the strip, v across (bright centre line).
textures/misc/epic/ray_fan.png       128x32 one god ray; u along (fades towards the far end), v across.
textures/misc/epic/ring_soft.png     64x16 soft ring band; u around the ring (tiles), v across the band.
textures/misc/epic/thunder_avatar.png  64x64 player-skin layout: white-violet thunder deity with glowing eyes.
textures/misc/epic/immortal_avatar.png 64x64 player-skin layout: gold-jade immortal figure.

HUD layers (white, tinted at blit time):
textures/gui/epic/ink_band.png       256x32 a dry brush stroke (the title-card reveal band).
textures/gui/epic/seal.png           32x32 a red seal square with a worn border (paper glyph drawn over it).
textures/gui/epic/vignette.png       256x256 radial edge darkening.
textures/gui/epic/ink_edge.png       256x256 mottled ink-splatter frame.
textures/gui/epic/bar_trim.png       64x4 the gold inner line of the letterbox bars.

Run with: python tools/gen_epic_textures.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
MISC = os.path.join(ROOT, "misc", "epic")
GUI = os.path.join(ROOT, "gui", "epic")


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


def cloud_swirl():
    size = 128
    img = Image(size, size)
    noise = Noise(7, 16)
    for y in range(size):
        for x in range(size):
            u = x / size
            v = y / size
            # Streaks along u (the swirl direction), twisted by v.
            n = noise.fbm(u * 16 + v * 5.0, v * 4.0, 5)
            streak = 0.5 + 0.5 * math.sin((u * 6 + v * 2.5) * math.pi * 2 + n * 4.0)
            d = clamp(0.35 + 0.65 * n * (0.6 + 0.4 * streak))
            shade = int(150 + 105 * d)
            alpha = int(clamp(0.12 + 0.88 * smooth((d - 0.25) / 0.6)) * 255)
            img.set(x, y, (shade, shade, shade, max(28, alpha)))
    img.save(os.path.join(MISC, "cloud_swirl.png"))


def glow_strip():
    size = 32
    img = Image(size, size)
    for y in range(size):
        across = abs((y + 0.5) / size - 0.5) * 2.0
        core = math.exp(-(across * 3.2) ** 2)
        halo = math.exp(-(across * 1.4) ** 2)
        a = clamp(0.1 + 0.55 * halo + 0.35 * core)
        white = int(200 + 55 * core)
        for x in range(size):
            img.set(x, y, (white, white, 255, int(a * 255)))
    img.save(os.path.join(MISC, "glow_strip.png"))


def ray_fan():
    w, h = 128, 32
    img = Image(w, h)
    noise = Noise(11, 8)
    for x in range(w):
        along = x / (w - 1)
        fade = (1.0 - along) ** 1.2
        for y in range(h):
            across = abs((y + 0.5) / h - 0.5) * 2.0
            soft = math.exp(-(across * 2.0) ** 2)
            flicker = 0.75 + 0.25 * noise.at(x / 16.0, y / 4.0)
            a = clamp(0.1 + 0.9 * soft * fade * flicker)
            img.set(x, y, (255, 255, 255, int(a * 255)))
    img.save(os.path.join(MISC, "ray_fan.png"))


def ring_soft():
    w, h = 64, 16
    img = Image(w, h)
    noise = Noise(5, 8)
    for y in range(h):
        across = abs((y + 0.5) / h - 0.5) * 2.0
        band = math.exp(-(across * 2.2) ** 2)
        for x in range(w):
            ripple = 0.85 + 0.15 * noise.at(x / 8.0 * 8 / 8, y / 4.0)
            a = clamp(0.1 + 0.9 * band * ripple)
            img.set(x, y, (255, 255, 255, int(a * 255)))
    img.save(os.path.join(MISC, "ring_soft.png"))


# Player skin layout (64x64): every box face of the base layer.
SKIN_BOXES = [
    # (u, v, w, h, d) for head, body, right arm, left arm, right leg, left leg
    (0, 0, 8, 8, 8),
    (16, 16, 8, 12, 4),
    (40, 16, 4, 12, 4),
    (32, 48, 4, 12, 4),
    (0, 16, 4, 12, 4),
    (16, 48, 4, 12, 4),
]


def box_faces(u, v, w, h, d):
    """Rectangles (x0, y0, x1, y1) of the six faces of a box in the skin layout."""
    return [
        (u + d, v, u + d + w, v + d),
        (u + d + w, v, u + d + 2 * w, v + d),
        (u, v + d, u + d, v + d + h),
        (u + d, v + d, u + d + w, v + d + h),
        (u + d + w, v + d, u + 2 * d + w, v + d + h),
        (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    ]


def avatar(name, top, bottom, accent, eye):
    img = Image(64, 64)
    noise = Noise(3, 8)
    for (u, v, w, h, d) in SKIN_BOXES:
        for (x0, y0, x1, y1) in box_faces(u, v, w, h, d):
            for y in range(y0, y1):
                t = (y - y0) / max(1, y1 - y0 - 1)
                for x in range(x0, x1):
                    n = noise.at(x / 3.0, y / 3.0)
                    c = [top[i] * (1 - t) + bottom[i] * t for i in range(3)]
                    c = [cc * (0.85 + 0.25 * n) for cc in c]
                    if n > 0.78:
                        c = [accent[i] for i in range(3)]
                    img.set(x, y, (c[0], c[1], c[2], 255))
    # Robe line down the body front and glowing eyes on the head front (8..16, 8..16).
    for y in range(20, 32):
        img.set(19, y, accent + (255,))
        img.set(20, y, accent + (255,))
    for x in (9, 10, 13, 14):
        img.set(x, 12, eye + (255,))
        img.set(x, 13, tuple(int(c * 0.8) for c in eye) + (255,))
    img.save(os.path.join(MISC, name))


def ink_band():
    w, h = 256, 32
    img = Image(w, h)
    noise = Noise(21, 16)
    rnd = random.Random(4)
    bristles = [rnd.random() for _ in range(h)]
    for y in range(h):
        across = abs((y + 0.5) / h - 0.5) * 2.0
        for x in range(w):
            along = x / (w - 1)
            # Dry brush: thinner and broken towards the tail.
            edge = 0.92 - 0.25 * along + 0.1 * noise.at(x / 12.0, y / 3.0)
            if across > edge:
                continue
            dry = bristles[y] < 0.12 + 0.35 * along and noise.at(x / 6.0, y) > 0.55
            if dry:
                continue
            a = clamp(0.75 + 0.25 * noise.fbm(x / 10.0, y / 4.0, 3))
            img.set(x, y, (255, 255, 255, int(a * 255)))
    img.save(os.path.join(GUI, "ink_band.png"))


def seal():
    size = 32
    img = Image(size, size)
    noise = Noise(9, 8)
    for y in range(size):
        for x in range(size):
            border = min(x, y, size - 1 - x, size - 1 - y)
            worn = noise.at(x / 3.0, y / 3.0)
            if border < 1 and worn < 0.45:
                continue
            a = 1.0
            if border < 3 and worn > 0.7:
                a = 0.75
            if 2 <= border <= 3:
                # Inner engraved frame line.
                img.set(x, y, (255, 238, 225, int(0.85 * 255)))
                continue
            shade = int(235 + 20 * worn)
            img.set(x, y, (shade, shade, shade, int(a * 255)))
    img.save(os.path.join(GUI, "seal.png"))


def vignette():
    size = 256
    img = Image(size, size)
    for y in range(size):
        for x in range(size):
            dx = (x + 0.5) / size * 2 - 1
            dy = (y + 0.5) / size * 2 - 1
            r = math.sqrt(dx * dx * 0.85 + dy * dy)
            a = smooth((r - 0.55) / 0.75)
            img.set(x, y, (255, 255, 255, int(clamp(a) * 255)))
    img.save(os.path.join(GUI, "vignette.png"))


def ink_edge():
    size = 256
    img = Image(size, size)
    noise = Noise(31, 16)
    for y in range(size):
        for x in range(size):
            edge = min(x, y, size - 1 - x, size - 1 - y) / (size * 0.5)
            n = noise.fbm(x / 16.0, y / 16.0, 4)
            splat = edge - 0.25 * n
            a = clamp(1.0 - splat / 0.22)
            if a < 0.05:
                continue
            # Mottled: drops of darker ink inside the band.
            a *= 0.7 + 0.3 * noise.at(x / 5.0, y / 5.0)
            img.set(x, y, (255, 255, 255, int(clamp(a) * 255)))
    img.save(os.path.join(GUI, "ink_edge.png"))


def bar_trim():
    w, h = 64, 4
    img = Image(w, h)
    for x in range(w):
        glint = 0.8 + 0.2 * math.sin(x / w * math.pi * 6)
        img.set(x, 0, (int(150 * glint), int(110 * glint), 30, 160))
        img.set(x, 1, (int(255 * glint), int(214 * glint), int(110 * glint), 255))
        img.set(x, 2, (int(190 * glint), int(140 * glint), 40, 200))
        img.set(x, 3, (60, 40, 10, 90))
    img.save(os.path.join(GUI, "bar_trim.png"))


def main():
    os.makedirs(MISC, exist_ok=True)
    os.makedirs(GUI, exist_ok=True)
    cloud_swirl()
    glow_strip()
    ray_fan()
    ring_soft()
    avatar("thunder_avatar.png", (238, 232, 255), (150, 120, 235), (255, 255, 255), (190, 240, 255))
    avatar("immortal_avatar.png", (255, 236, 160), (110, 200, 150), (255, 250, 220), (255, 255, 230))
    ink_band()
    seal()
    vignette()
    ink_edge()
    bar_trim()


if __name__ == "__main__":
    main()
