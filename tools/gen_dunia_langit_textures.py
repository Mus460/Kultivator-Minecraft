"""Generates the textures of module L, Langit & Cuaca (DESIGN 34.5.8-34.5.10, 34.6 L).

World layers (drawn with entityTranslucentEmissive, which cuts texture alpha below 0.1 BEFORE the vertex colour: every
pixel that should show keeps alpha >= 26, fades go into the vertex alpha; white or grey layers are tinted per vertex):
textures/dunia/langit/meteor_streak.png  64x16  a meteor trail; u along (head at u = 1), v across (hot core line).
textures/dunia/langit/meteor_head.png    32x32  the burning head: a round core with a four-point flare.
textures/dunia/langit/moon_blood.png     64x64  Gerhana Darah: the eclipsed moon, dark crimson with a copper rim.
textures/dunia/langit/moon_twin.png      64x64  Bulan Kembar: the pale silver second moon with its own maria.
textures/dunia/langit/moon_halo.png      64x64  a soft ring halo around a moon (white, tinted).
textures/dunia/langit/pillar_band.png    16x64  one band of the rainbow pillar; u across (soft edges), v up (tiles).
textures/dunia/langit/pillar_crown.png   64x64  the crown halo at the pillar's top: a ring with sixteen rays.
textures/dunia/langit/mist_plane.png     64x64  a ground-hugging mist puff (tileable noise, round falloff).
textures/dunia/langit/lotus_petal.png    16x32  one jade lotus petal (v = 0 the tip, v = 1 the base), veins.
textures/dunia/langit/meteor_rock.png    32x32  the meteor rock's faces: dark stone with glowing cyan cracks.
textures/dunia/langit/glow_dot.png       32x32  a round soft glow (impact flash, pillar base, motes).
textures/dunia/langit/moon_shadow.png    64x64  Tiangou's bite: the dark disc that slides over the moon as the eclipse
                                                  begins and leaves, a soft penumbra edge (dark, drawn alpha-blended).
textures/dunia/langit/crater_decal.png   64x64  a meteor's crater glow on the ground: scorched bowl, raised rim, ejecta
                                                  rays and glowing cyan cracks (never a block).
textures/dunia/langit/rune_ring.png      64x64  the turning seal ring under the treasure lotus: sixteen seal-script-like
                                                  glyphs between two thin circles (white, tinted).
textures/dunia/langit/light_shaft.png    16x64  a sunbeam through dense Qi mist; u across (soft), v along (tiles).
textures/dunia/langit/qi_wisp.png        64x16  a long wisp of drifting Qi (Fajar Ungu streamers, the eastern band).

Item icons (16x16, the item atlas):
textures/item/batu_meteor_roh.png        Batu Meteor Roh: a dark star-iron lump with cyan glowing seams.
textures/item/embun_roh.png              Embun Roh: a small bottle of pale violet dew with a sparkle.
textures/item/inti_meteor_agung.png      Inti Meteor Agung: a faceted star-iron heart with a white-hot core.
textures/item/kristal_gerhana.png        Kristal Gerhana: a crimson eclipse shard with a dark moon notch.

Run with: python tools/gen_dunia_langit_textures.py [preview_dir]   (8x previews of every texture into preview_dir)
Deterministic: the same files on every run.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
WORLD = os.path.join(ROOT, "dunia", "langit")
ITEM = os.path.join(ROOT, "item")
MIN_A = 26  # 0.1 alpha cutoff of entityTranslucentEmissive

WRITTEN = []


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


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
        norm = 0.0
        for _ in range(octaves):
            total += amp * self.at(x * freq, y * freq)
            norm += amp
            amp *= 0.5
            freq *= 2.0
        return total / norm


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name)
    img.save(path)
    WRITTEN.append((path, img))


def alpha_of(v):
    """A visible layer alpha (0..1) as a byte that never falls under the cutoff."""
    return max(MIN_A, int(round(clamp(v) * 255)))


# ================================================================ world layers

def meteor_streak():
    w, h = 64, 16
    img = Image(w, h)
    noise = Noise(31, 8)
    for x in range(w):
        along = (x + 0.5) / w  # 0 tail .. 1 head
        body = along ** 1.6
        for y in range(h):
            across = abs((y + 0.5) / h - 0.5) * 2.0
            width = 0.35 + 0.65 * along
            core = math.exp(-((across / max(0.08, width * 0.35)) ** 2))
            halo = math.exp(-((across / max(0.15, width)) ** 2) * 2.2)
            sparkle = 0.8 + 0.2 * noise.at(x / 4.0, y / 3.0)
            a = (0.55 * halo + 0.45 * core) * body * sparkle
            if a < 0.06:
                img.set(x, y, (0, 0, 0, 0))
                continue
            hot = clamp(core * (0.4 + 0.6 * along))
            r, g, b = mix((150, 200, 255), (255, 255, 255), hot)
            img.set(x, y, (r, g, b, alpha_of(a)))
    save(img, WORLD, "meteor_streak.png")


def meteor_head():
    s = 32
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            core = math.exp(-(r / 0.22) ** 2)
            glow = math.exp(-(r / 0.55) ** 2) * 0.6
            ang = math.atan2(dy, dx)
            flare = (abs(math.cos(ang * 2)) ** 18) * math.exp(-(r / 0.9) ** 2) * 0.7
            a = clamp(core + glow + flare)
            if a < 0.08 or r > 1.0:
                img.set(x, y, (0, 0, 0, 0))
                continue
            t = clamp(core * 1.2)
            col = mix((170, 210, 255), (255, 255, 255), t)
            img.set(x, y, (col[0], col[1], col[2], alpha_of(a)))
    save(img, WORLD, "meteor_head.png")


def moon(name, seed, base, dark, rim, rim_width, shade_dir, glow_rgb, umbra=0.0):
    s = 64
    img = Image(s, s)
    noise = Noise(seed, 8)
    rnd = random.Random(seed)
    craters = []
    for _ in range(14):
        craters.append((rnd.uniform(-0.65, 0.65), rnd.uniform(-0.65, 0.65), rnd.uniform(0.05, 0.17)))
    radius = 0.86
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            if r > 1.0:
                img.set(x, y, (0, 0, 0, 0))
                continue
            if r > radius:
                # thin outer glow ring, fading out
                t = (r - radius) / (1.0 - radius)
                a = (1.0 - t) ** 2 * 0.75
                if a < 0.1:
                    img.set(x, y, (0, 0, 0, 0))
                    continue
                img.set(x, y, (glow_rgb[0], glow_rgb[1], glow_rgb[2], alpha_of(a)))
                continue
            # maria (large dark patches) from noise, craters as rings
            m = noise.fbm(dx * 2.2 + 3.0, dy * 2.2 + 5.0, 4)
            maria = smooth((m - 0.48) / 0.18)
            col = mix(base, dark, maria * 0.75)
            for cx, cy, cr in craters:
                ox = (dx - cx) / cr
                oy = (dy - cy) / cr
                d = math.hypot(ox, oy)
                if d < 1.15:
                    # a soft bowl: darker floor, the wall facing the light brighter, the other wall in shadow
                    bowl = smooth(1.0 - d / 1.15)
                    facing = -(ox * shade_dir[0] + oy * shade_dir[1]) / max(0.2, d)
                    col = mix(col, dark, 0.22 * bowl)
                    if d > 0.55:
                        wall = smooth((d - 0.55) / 0.45) * smooth((1.15 - d) / 0.25)
                        col = mix(col, (255, 255, 255) if facing > 0 else dark, 0.10 * wall * abs(facing))
            # limb darkening and a light direction
            limb = math.sqrt(max(0.0, 1.0 - (r / radius) ** 2))
            light = clamp(0.55 + 0.45 * (dx * shade_dir[0] + dy * shade_dir[1]) + 0.35 * limb)
            col = tuple(c * (0.55 + 0.45 * light) for c in col)
            # the umbra of an eclipse: one side sinks into shadow, the far limb keeps a copper glow
            if umbra > 0.0:
                side = clamp(0.5 + 0.5 * (dx * shade_dir[0] + dy * shade_dir[1]) / radius)
                col = mix(col, dark, umbra * (1.0 - side) * 0.7)
            # bright rim inside the edge
            edge = clamp((r - (radius - rim_width)) / rim_width)
            col = mix(col, rim, edge * 0.85)
            aa = clamp((radius - r) * s / 2.0 + 0.5)
            img.set(x, y, (col[0], col[1], col[2], max(MIN_A, int(255 * (0.5 + 0.5 * aa)))))
    save(img, WORLD, name)


def moon_halo():
    s = 64
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            ring = math.exp(-((r - 0.62) / 0.16) ** 2)
            inner = math.exp(-(r / 0.5) ** 2) * 0.35
            a = clamp(ring * 0.9 + inner) * clamp((1.0 - r) / 0.12)
            if a < 0.1:
                img.set(x, y, (0, 0, 0, 0))
                continue
            img.set(x, y, (255, 255, 255, alpha_of(a)))
    save(img, WORLD, "moon_halo.png")


def pillar_band():
    w, h = 16, 64
    img = Image(w, h)
    noise = Noise(17, 8)
    for y in range(h):
        v = y / h
        for x in range(w):
            across = abs((x + 0.5) / w - 0.5) * 2.0
            soft = math.exp(-(across * 1.9) ** 2)
            core = math.exp(-(across * 5.0) ** 2)
            # rising streaks: tile vertically (period 64) with sinus bands + noise
            streak = 0.72 + 0.18 * math.sin((v * 3.0 + across * 0.7) * math.pi * 2) + 0.1 * noise.at(x / 2.0, y / 8.0)
            a = clamp(soft * streak * 0.85 + core * 0.4)
            shade = int(215 + 40 * core)
            img.set(x, y, (shade, shade, shade, alpha_of(a)))
    save(img, WORLD, "pillar_band.png")


def pillar_crown():
    s = 64
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            ring = math.exp(-((r - 0.46) / 0.07) ** 2)
            ring2 = math.exp(-((r - 0.70) / 0.035) ** 2) * 0.55
            rays = (abs(math.cos(ang * 8)) ** 10) * clamp(1.0 - r) * (r > 0.3) * 0.8
            center = math.exp(-(r / 0.25) ** 2) * 0.5
            a = clamp(ring + ring2 + rays + center) * clamp((1.0 - r) / 0.1)
            if a < 0.1:
                img.set(x, y, (0, 0, 0, 0))
                continue
            img.set(x, y, (255, 255, 255, alpha_of(a)))
    save(img, WORLD, "pillar_crown.png")


def mist_plane():
    s = 64
    img = Image(s, s)
    noise = Noise(23, 8)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            fall = smooth(1.0 - r)
            n = noise.fbm(x / 8.0, y / 8.0, 4)
            a = clamp(fall * (0.35 + 0.75 * smooth((n - 0.25) / 0.55)))
            if a < 0.1:
                img.set(x, y, (0, 0, 0, 0))
                continue
            shade = int(225 + 30 * n)
            img.set(x, y, (shade, shade, shade, alpha_of(a * 0.8)))
    save(img, WORLD, "mist_plane.png")


def lotus_petal():
    w, h = 16, 32
    img = Image(w, h)
    for y in range(h):
        v = (y + 0.5) / h  # 0 tip .. 1 base
        # petal half width along v: pointed tip, round belly, narrow base
        half = math.sin(math.pi * min(1.0, v * 1.05)) ** 0.7 * (0.95 - 0.35 * v)
        for x in range(w):
            u = (x + 0.5) / w * 2 - 1
            if abs(u) > half:
                img.set(x, y, (0, 0, 0, 0))
                continue
            t = abs(u) / max(0.01, half)
            vein = math.exp(-((u / 0.08) ** 2)) * 0.5 + 0.18 * (abs(math.sin((u * 3.5 + v * 0.8) * math.pi)) ** 8)
            base_col = mix((180, 255, 220), (60, 190, 150), v * 0.8)
            col = mix(base_col, (235, 255, 245), clamp(vein))
            col = mix(col, (40, 140, 110), t ** 3 * 0.6)
            # the tip glows pink-gold like a real lotus tip
            col = mix(col, (255, 210, 200), clamp(1.0 - v * 4.0) * 0.6)
            a = 0.62 + 0.38 * (1.0 - t ** 2)
            img.set(x, y, (col[0], col[1], col[2], alpha_of(a)))
    save(img, WORLD, "lotus_petal.png")


def meteor_rock():
    s = 32
    img = Image(s, s)
    noise = Noise(41, 8)
    rnd = random.Random(47)
    # glowing seams: a few random walks (tileable: wrapped), their distance field drives the glow
    seam = [[0.0] * s for _ in range(s)]
    for _ in range(5):
        x = rnd.uniform(0, s)
        y = rnd.uniform(0, s)
        ang = rnd.uniform(0, math.pi * 2)
        for _ in range(rnd.randint(14, 26)):
            ang += rnd.uniform(-0.6, 0.6)
            x = (x + math.cos(ang)) % s
            y = (y + math.sin(ang)) % s
            for oy in range(-2, 3):
                for ox in range(-2, 3):
                    px = int(x + ox) % s
                    py = int(y + oy) % s
                    d = math.hypot(px + 0.5 - x if abs(px + 0.5 - x) < s / 2 else 0, py + 0.5 - y if abs(py + 0.5 - y) < s / 2 else 0)
                    seam[py][px] = max(seam[py][px], math.exp(-(d / 0.7) ** 2))
    for y in range(s):
        for x in range(s):
            n = noise.fbm(x / 6.0, y / 6.0, 4)
            stone = mix((34, 30, 44), (96, 90, 110), n)
            # pitted surface: small dark speckles
            if noise.at(x * 1.7 + 11, y * 1.7 + 3) > 0.82:
                stone = mix(stone, (18, 14, 26), 0.6)
            g = seam[y][x]
            col = mix(stone, (110, 225, 255), clamp(g * 1.1))
            if g > 0.75:
                col = mix(col, (235, 255, 255), (g - 0.75) / 0.25)
            img.set(x, y, (col[0], col[1], col[2], 255))
    save(img, WORLD, "meteor_rock.png")


def glow_dot():
    s = 32
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            a = math.exp(-(r / 0.42) ** 2) * clamp((1.0 - r) / 0.15)
            if a < 0.1:
                img.set(x, y, (0, 0, 0, 0))
                continue
            img.set(x, y, (255, 255, 255, alpha_of(a)))
    save(img, WORLD, "glow_dot.png")


def moon_shadow():
    s = 64
    img = Image(s, s)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            # the umbra: a hard dark disc a little larger than the moon disc, a short penumbra outside it
            a = 0.94 if r < 0.88 else 0.94 * (1.0 - smooth((r - 0.88) / 0.1))
            if a < 0.1:
                img.set(x, y, (0, 0, 0, 0))
                continue
            # a faint copper rim of refracted light right at the penumbra (Earth's sunsets)
            rim = math.exp(-((r - 0.9) / 0.03) ** 2) * 0.5
            col = mix((14, 6, 8), (120, 40, 20), rim)
            img.set(x, y, (col[0], col[1], col[2], alpha_of(a)))
    save(img, WORLD, "moon_shadow.png")


def crater_decal():
    s = 64
    img = Image(s, s)
    noise = Noise(71, 8)
    rnd = random.Random(73)
    rays = [rnd.uniform(0, math.pi * 2) for _ in range(11)]
    cracks = []
    for i in range(7):
        ang = math.pi * 2 * i / 7 + rnd.uniform(-0.3, 0.3)
        pts = []
        r = 0.08
        while r < 0.78:
            pts.append((math.cos(ang) * r, math.sin(ang) * r))
            ang += rnd.uniform(-0.1, 0.1)
            r += 0.05
        cracks.append(pts)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            if r > 1.0:
                img.set(x, y, (0, 0, 0, 0))
                continue
            n = noise.fbm(x / 7.0, y / 7.0, 3)
            ang = math.atan2(dy, dx)
            bowl = smooth(1.0 - r / 0.62)
            rim = math.exp(-((r - 0.66) / 0.08) ** 2)
            ray = 0.0
            for ra in rays:
                d = abs((ang - ra + math.pi) % (math.pi * 2) - math.pi)
                ray = max(ray, math.exp(-(d / 0.06) ** 2) * smooth((1.0 - r) / 0.35) * (r > 0.6))
            a = clamp(0.62 * bowl + 0.78 * rim + 0.42 * ray) * (0.75 + 0.25 * n)
            # glowing cracks: distance to the polylines
            glow = 0.0
            for pts in cracks:
                for (ax, ay), (bx, by) in zip(pts, pts[1:]):
                    vx, vy = bx - ax, by - ay
                    t = clamp(((dx - ax) * vx + (dy - ay) * vy) / max(1e-6, vx * vx + vy * vy))
                    d = math.hypot(dx - (ax + vx * t), dy - (ay + vy * t))
                    glow = max(glow, math.exp(-(d / 0.022) ** 2) * (1.0 - r / 0.8))
            glow = clamp(glow) * smooth((0.8 - r) / 0.2)
            if a < 0.1 and glow < 0.12:
                img.set(x, y, (0, 0, 0, 0))
                continue
            scorch = mix((52, 44, 50), (18, 14, 20), clamp(bowl * 0.8 + 0.2 * n))
            scorch = mix(scorch, (70, 60, 62), rim * 0.5)
            col = mix(scorch, (120, 230, 255), glow)
            if glow > 0.7:
                col = mix(col, (235, 255, 255), (glow - 0.7) / 0.3)
            img.set(x, y, (col[0], col[1], col[2], alpha_of(max(a, glow))))
    save(img, WORLD, "crater_decal.png")


def rune_ring():
    s = 64
    img = Image(s, s)
    rnd = random.Random(79)
    glyphs = []
    for _ in range(16):
        strokes = []
        for _ in range(rnd.randint(3, 5)):
            x0, y0 = rnd.randint(0, 4), rnd.randint(0, 4)
            if rnd.random() < 0.5:
                x1, y1 = rnd.randint(0, 4), y0
            else:
                x1, y1 = x0, rnd.randint(0, 4)
            strokes.append((x0, y0, x1, y1))
        glyphs.append(strokes)
    for y in range(s):
        for x in range(s):
            dx = (x + 0.5) / s * 2 - 1
            dy = (y + 0.5) / s * 2 - 1
            r = math.hypot(dx, dy)
            ang = (math.atan2(dy, dx) + math.pi * 2) % (math.pi * 2)
            a = 0.0
            a = max(a, math.exp(-((r - 0.6) / 0.018) ** 2))
            a = max(a, math.exp(-((r - 0.95) / 0.02) ** 2) * 0.9)
            a = max(a, math.exp(-((r - 0.9) / 0.012) ** 2) * 0.55)
            if 0.64 < r < 0.88:
                cell = ang / (math.pi * 2) * 16
                i = int(cell) % 16
                gu = (cell - math.floor(cell)) * 1.25 - 0.125  # 0..1 inside the cell, margins
                gv = (r - 0.66) / 0.2
                if 0.0 <= gu <= 1.0 and 0.0 <= gv <= 1.0:
                    px, py = gu * 4.0, gv * 4.0
                    for x0, y0, x1, y1 in glyphs[i]:
                        tx = clamp((px - x0) * (x1 - x0) / max(1e-6, (x1 - x0) ** 2)) if x1 != x0 else 0.0
                        ty = clamp((py - y0) * (y1 - y0) / max(1e-6, (y1 - y0) ** 2)) if y1 != y0 else 0.0
                        cx = x0 + (x1 - x0) * tx
                        cy = y0 + (y1 - y0) * ty
                        d = math.hypot(px - cx, py - cy)
                        a = max(a, math.exp(-(d / 0.28) ** 2))
                # a dot between the glyphs
                if abs(gu - 1.06) < 0.05 and abs(gv - 0.5) < 0.12:
                    a = max(a, 0.8)
            a = clamp(a)
            if a < 0.12:
                img.set(x, y, (0, 0, 0, 0))
                continue
            img.set(x, y, (255, 255, 255, alpha_of(a)))
    save(img, WORLD, "rune_ring.png")


def light_shaft():
    w, h = 16, 64
    img = Image(w, h)
    noise = Noise(83, 8)
    for y in range(h):
        for x in range(w):
            across = abs((x + 0.5) / w - 0.5) * 2.0
            soft = math.exp(-(across * 1.6) ** 2)
            dust = 0.75 + 0.25 * noise.at(x / 3.0, y / 6.0)
            a = clamp(soft * dust * 0.9)
            if a < 0.1:
                img.set(x, y, (0, 0, 0, 0))
                continue
            img.set(x, y, (255, 255, 255, alpha_of(a)))
    save(img, WORLD, "light_shaft.png")


def qi_wisp():
    w, h = 64, 16
    img = Image(w, h)
    noise = Noise(89, 8)
    for y in range(h):
        for x in range(w):
            along = (x + 0.5) / w
            across = abs((y + 0.5) / h - 0.5) * 2.0
            taper = math.sin(math.pi * along) ** 0.8
            width = 0.35 + 0.65 * taper
            body = math.exp(-((across / width) ** 2) * 2.4) * taper
            curl = 0.65 + 0.35 * noise.fbm(x / 6.0, y / 3.0 + along * 2.0, 3)
            a = clamp(body * curl)
            if a < 0.1:
                img.set(x, y, (0, 0, 0, 0))
                continue
            img.set(x, y, (255, 255, 255, alpha_of(a)))
    save(img, WORLD, "qi_wisp.png")


# ================================================================ item icons

def batu_meteor_roh():
    img = Image(16, 16)
    # a rough lump outline (pixel art): rows of (x0, x1)
    rows = {
        2: (6, 9), 3: (4, 11), 4: (3, 12), 5: (2, 12), 6: (2, 13), 7: (2, 13), 8: (2, 13), 9: (2, 13),
        10: (3, 13), 11: (3, 12), 12: (4, 11), 13: (6, 9),
    }
    noise = Noise(53, 4)
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            n = noise.at(x / 2.0, y / 2.0)
            light = clamp(0.45 + 0.35 * n - 0.04 * (x - 4) - 0.05 * (y - 3) + 0.25)
            col = mix((26, 22, 36), (104, 96, 122), light)
            edge = x == x0 or x == x1 or y in (2, 13) or (y == 3 and (x < 6 or x > 9)) or (y == 12 and (x < 6 or x > 9))
            if edge:
                col = mix(col, (14, 10, 22), 0.6)
            img.set(x, y, (col[0], col[1], col[2], 255))
    # glowing seams (a branching crack) and two star sparks
    seam = [(5, 5), (6, 6), (7, 6), (8, 7), (9, 8), (10, 8), (8, 8), (7, 9), (6, 10), (9, 9), (10, 10), (11, 10), (6, 4), (5, 7)]
    for i, (x, y) in enumerate(seam):
        hot = (120, 235, 255) if i % 3 else (220, 255, 255)
        img.set(x, y, (hot[0], hot[1], hot[2], 255))
    for x, y in ((4, 9), (11, 5)):
        img.set(x, y, (255, 245, 200, 255))
    img.set(12, 2, (200, 240, 255, 255))
    img.set(13, 1, (150, 210, 255, 200))
    img.set(11, 1, (150, 210, 255, 200))
    img.set(12, 0, (150, 210, 255, 200))
    save(img, ITEM, "batu_meteor_roh.png")


def embun_roh():
    img = Image(16, 16)
    glass_edge = (196, 214, 236, 255)
    glass = (210, 226, 246, 120)
    dew = (200, 160, 255, 230)
    dew_light = (236, 214, 255, 255)
    # cork and neck
    for x in range(7, 9):
        img.set(x, 2, (150, 104, 64, 255))
        img.set(x, 3, (122, 82, 50, 255))
    for y in (4, 5):
        img.set(6, y, glass_edge)
        img.set(9, y, glass_edge)
        img.set(7, y, glass)
        img.set(8, y, glass)
    # round body (rows y = 6..13)
    body = {6: (5, 10), 7: (4, 11), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (3, 12), 12: (4, 11), 13: (5, 10)}
    for y, (x0, x1) in body.items():
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y in (6, 13):
                img.set(x, y, glass_edge)
            elif y >= 8:
                t = (x - x0) / max(1, x1 - x0)
                col = mix(dew_light, dew, t)
                img.set(x, y, (col[0], col[1], col[2], 235))
            else:
                img.set(x, y, glass)
    # highlight and the drifting qi sparkle
    img.set(5, 9, (255, 255, 255, 255))
    img.set(5, 10, (245, 240, 255, 255))
    img.set(12, 4, (240, 220, 255, 255))
    img.set(13, 3, (200, 170, 255, 200))
    img.set(11, 3, (200, 170, 255, 200))
    img.set(12, 2, (200, 170, 255, 200))
    save(img, ITEM, "embun_roh.png")


def inti_meteor_agung():
    img = Image(16, 16)
    # a faceted heart: an octagon of star iron, the facets lit from the top left, a white-hot core in the middle
    rows = {2: (6, 9), 3: (5, 10), 4: (4, 11), 5: (3, 12), 6: (3, 12), 7: (2, 13), 8: (2, 13), 9: (3, 12), 10: (3, 12),
            11: (4, 11), 12: (5, 10), 13: (6, 9)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            cx, cy = x - 7.5, y - 7.5
            r = math.hypot(cx, cy)
            facet = ((x + y) // 3 + (x - y) // 4) % 3
            light = clamp(0.55 - 0.05 * cx * 0.8 - 0.06 * cy + 0.12 * facet)
            col = mix((22, 26, 44), (96, 120, 150), light)
            # glowing veins converging to the core
            if abs(cx) < 0.6 or abs(cy) < 0.6 or abs(abs(cx) - abs(cy)) < 0.6:
                col = mix(col, (110, 225, 255), clamp(1.0 - r / 7.0))
            core = clamp(1.0 - r / 2.6)
            col = mix(col, (240, 255, 255), core)
            if x in (x0, x1) or y in (2, 13):
                col = mix(col, (10, 12, 24), 0.6)
            img.set(x, y, (col[0], col[1], col[2], 255))
    for x, y, a in ((13, 2, 255), (14, 1, 170), (12, 1, 170), (13, 0, 170), (2, 13, 220), (1, 14, 140), (3, 14, 140)):
        img.set(x, y, (200, 245, 255, a))
    save(img, ITEM, "inti_meteor_agung.png")


def kristal_gerhana():
    img = Image(16, 16)
    # a cluster of three crimson shards (tall middle, two leaning side shards) rising from a dark stone,
    # each lit from the left with a bright ridge, and a dark moon notch bitten out of the tall one
    shards = (  # tip x, tip y, base x, base y, half width
        (13.2, 5.0, 10.4, 13.4, 1.35),
        (2.4, 6.2, 5.4, 13.4, 1.35),
        (7.8, 0.6, 7.8, 13.4, 2.1),
    )
    for tx, ty, bx, by, hw in shards:
        ax, ay = bx - tx, by - ty
        length = math.hypot(ax, ay)
        ux, uy = ax / length, ay / length
        for y in range(16):
            for x in range(16):
                px, py = x + 0.5 - tx, y + 0.5 - ty
                t = (px * ux + py * uy) / length
                d = px * uy - py * ux  # signed: negative on the left of the axis
                if t < 0.0 or t > 1.0:
                    continue
                width = hw * min(1.0, t * 4.0) * (1.0 - 0.15 * t)
                if abs(d) > width + 0.15:
                    continue
                col = (205, 44, 36) if d < 0 else (118, 14, 18)
                if abs(d) < 0.5:
                    col = (255, 128, 96)
                if abs(d) > width - 0.7:
                    col = mix(col, (38, 4, 6), 0.55)
                if t < 0.12:
                    col = mix(col, (255, 190, 160), 0.5)
                img.set(x, y, (int(col[0]), int(col[1]), int(col[2]), 255))
    # the moon notch on the tall shard
    for y in range(2, 7):
        for x in range(7, 11):
            if math.hypot(x + 0.5 - 9.6, y + 0.5 - 4.2) < 1.3 and img.get(x, y)[3] > 0:
                img.set(x, y, (26, 6, 10, 255))
    # the dark stone the cluster grows from
    for y in range(12, 16):
        for x in range(2, 14):
            r = math.hypot((x + 0.5 - 8.0) / 6.0, (y + 0.5 - 14.0) / 2.2)
            if r < 1.0:
                shade = clamp(0.55 - 0.35 * r + (0.12 if (x * 7 + y * 3) % 5 == 0 else 0.0))
                col = mix((14, 10, 16), (70, 52, 60), shade)
                if y == 12 and img.get(x, y)[3] > 0:
                    continue
                img.set(x, y, (int(col[0]), int(col[1]), int(col[2]), 255))
    # glints and a red mote
    img.set(6, 5, (255, 220, 200, 255))
    img.set(3, 7, (255, 200, 180, 230))
    img.set(1, 3, (255, 90, 70, 190))
    img.set(14, 2, (255, 90, 70, 190))
    img.set(13, 9, (255, 120, 90, 140))
    save(img, ITEM, "kristal_gerhana.png")


def preview(folder):
    os.makedirs(folder, exist_ok=True)
    for path, img in WRITTEN:
        scale = 8 if img.width <= 32 else 4
        big = Image(img.width * scale, img.height * scale)
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = img.get(x, y)
                # over a dark checker so alpha shows
                check = 40 if ((x // 2 + y // 2) % 2) else 24
                t = a / 255.0
                c = (r * t + check * (1 - t), g * t + check * (1 - t), b * t + check * (1 - t), 255)
                for yy in range(scale):
                    for xx in range(scale):
                        big.set(x * scale + xx, y * scale + yy, c)
        big.save(os.path.join(folder, "preview_" + os.path.basename(path)))


def main():
    meteor_streak()
    meteor_head()
    moon("moon_blood.png", 61, (150, 26, 18), (60, 6, 6), (255, 120, 60), 0.10, (-0.5, -0.6), (255, 70, 40), 0.8)
    moon("moon_twin.png", 67, (222, 230, 244), (150, 160, 186), (245, 250, 255), 0.06, (0.6, -0.4), (200, 220, 255))
    moon_halo()
    pillar_band()
    pillar_crown()
    mist_plane()
    lotus_petal()
    meteor_rock()
    glow_dot()
    moon_shadow()
    crater_decal()
    rune_ring()
    light_shaft()
    qi_wisp()
    batu_meteor_roh()
    embun_roh()
    inti_meteor_agung()
    kristal_gerhana()
    if len(sys.argv) > 1:
        preview(sys.argv[1])
    for path, img in WRITTEN:
        print("wrote %s (%dx%d)" % (os.path.relpath(path, os.path.join(os.path.dirname(__file__), "..")), img.width, img.height))


if __name__ == "__main__":
    main()
