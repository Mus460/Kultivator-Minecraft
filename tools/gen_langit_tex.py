"""Generates the textures of module 37V, Sekte Langit visuals (DESIGN 37.6, docs/rancangan/37-sekte-langit.md).

World layers (drawn with entityTranslucentEmissive or the additive energySwirl type; entityTranslucentEmissive cuts texture
alpha below 0.1 BEFORE the vertex colour, so every pixel that should show keeps alpha >= 26 and the fades go into the
vertex alpha; white or grey layers are tinted per vertex):
textures/sekte/langit/halo.png           128x128 Cahaya Buddha (Emei glory): the observer's faint shadow ringed by a
                                                  rainbow glory (inner white-gold, violet to red outwards, a second faint
                                                  outer bow).
textures/sekte/langit/bangau.png         64x32   a red-crowned crane in flight, side view facing +u, two frames (u 0-0.5
                                                  wings up, 0.5-1 wings down): white body, black flight feathers and neck,
                                                  red crown, trailing legs.
textures/sekte/langit/sungai_bintang.png 512x128 Sungai Bintang (the Milky Way seen above the clouds): a dense star band
                                                  with dust lanes and a warm core; u tiles along the arc, v across.
textures/sekte/langit/lentera.png        32x32   a sky lantern (kongming): warm paper body with ribs, a bright flame
                                                  glow at the open bottom, a soft halo.
textures/sekte/langit/pilar.png          16x64   the tether pillar's band: a white core with soft edges and rising
                                                  streaks; u across, v up (tiles).
textures/sekte/langit/pusaran.png        128x128 the tribulation vortex seen from above the cloud sea: dark storm arms
                                                  spiralling into a violet-lit eye.
textures/sekte/langit/kilat.png          32x128  an upward lightning bolt with branches (white core, violet glow); v = 0
                                                  the top.
textures/sekte/langit/jaring.png         64x64   the Cloud Net: a hexagonal lattice of light with knots (white, tinted),
                                                  round falloff.
textures/sekte/langit/pedang.png         64x16   a patrol disciple's qi sword-light: a bright blade with a hilt guard,
                                                  the tip at u = 1, a soft glow around it (white-jade).

Run with: python tools/gen_langit_tex.py [preview_dir]   (4x previews of every texture into preview_dir)
Deterministic: the same files on every run. No dependency besides the bundled tools/png.py (zlib, struct).
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
OUT = os.path.join(ROOT, "sekte", "langit")
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
        norm = 0.0
        for i in range(octaves):
            f = 2 ** i
            total += amp * self.at(x * f, y * f)
            norm += amp
            amp *= 0.5
        return total / norm


def put(img, x, y, rgb, a):
    """Sets a pixel, dropping alpha below the cutout to fully clear (no grey fringe)."""
    a = int(clamp(a) * 255)
    if a < MIN_A:
        img.set(x, y, (0, 0, 0, 0))
    else:
        img.set(x, y, (rgb[0], rgb[1], rgb[2], a))


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    img.save(path)
    WRITTEN.append((path, img))


def save_blur_meta(name):
    with open(os.path.join(OUT, name + ".mcmeta"), "w", newline="\n") as f:
        f.write('{\n  "texture": {\n    "blur": true\n  }\n}\n')


# ------------------------------------------------------------------ halo (Cahaya Buddha)

def rainbow(t):
    """t 0 (inner, violet) .. 1 (outer, red), like the primary order of a glory (red outside)."""
    stops = [(0.0, (150, 90, 255)), (0.18, (70, 120, 255)), (0.36, (60, 220, 230)), (0.52, (90, 235, 110)),
             (0.68, (250, 240, 90)), (0.84, (255, 160, 60)), (1.0, (255, 70, 70))]
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        if t <= t1:
            return mix(c0, c1, (t - t0) / (t1 - t0))
    return stops[-1][1]


def halo():
    n = 128
    img = Image(n, n)
    c = (n - 1) / 2.0
    for y in range(n):
        for x in range(n):
            dx = (x - c) / c
            dy = (y - c) / c
            r = math.sqrt(dx * dx + dy * dy)
            col = (0, 0, 0)
            a = 0.0
            # the bright white-gold heart of the glory
            core = math.exp(-(r / 0.22) ** 2)
            if core > 0.02:
                col = (255, 244, 214)
                a = 0.55 * core
            # the main glory: r 0.30 .. 0.72
            if 0.26 < r < 0.76:
                t = (r - 0.28) / 0.44
                band = smooth(t / 0.12) * smooth((1 - t) / 0.12)
                rc = rainbow(clamp(t))
                ca = 0.85 * band
                if ca > a:
                    col = mix(col, rc, 0.85) if a > 0 else rc
                    a = max(a, ca)
            # the faint secondary bow: r 0.80 .. 0.98
            if 0.78 < r < 0.99:
                t = (r - 0.80) / 0.18
                band = smooth(t / 0.25) * smooth((1 - t) / 0.25)
                rc = rainbow(clamp(t))
                ca = 0.38 * band
                if ca > a:
                    col = rc
                    a = ca
            # the observer's shadow: a small dark figure (head and shoulders) in the heart
            hx = dx
            hy = dy + 0.02
            head = (hx * hx) / (0.055 ** 2) + ((hy + 0.07) ** 2) / (0.06 ** 2) < 1.0
            body = abs(hx) < 0.09 - 0.25 * max(0.0, -(hy - 0.02)) and 0.0 < hy < 0.22 and abs(hx) < 0.06 + 0.35 * hy
            if head or body:
                fade = 1.0 - smooth((hy - 0.12) / 0.10)
                col = mix(col, (40, 44, 60), 0.75)
                a = max(a, 0.45 * fade + 0.2)
            put(img, x, y, tuple(int(v) for v in col), a)
    save(img, "halo.png")
    save_blur_meta("halo.png")


# ------------------------------------------------------------------ bangau (crane, 2 frames)

def bangau():
    w, h = 64, 32
    img = Image(w, h)
    white = (246, 246, 240)
    shade = (206, 210, 214)
    black = (28, 28, 34)
    red = (214, 30, 36)
    beak = (196, 176, 120)
    leg = (60, 58, 56)

    def ell(img, ox, cx, cy, rx, ry, col, ang=0.0):
        ca, sa = math.cos(ang), math.sin(ang)
        for y in range(h):
            for x in range(32):
                px = x - cx
                py = y - cy
                u = px * ca + py * sa
                v = -px * sa + py * ca
                if (u * u) / (rx * rx) + (v * v) / (ry * ry) <= 1.0:
                    img.set(ox + x, y, col + (255,))

    def line(img, ox, x0, y0, x1, y1, col, width=1.0):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
        for i in range(steps + 1):
            t = i / steps
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            r = width / 2.0
            for yy in range(int(y - r - 1), int(y + r + 2)):
                for xx in range(int(x - r - 1), int(x + r + 2)):
                    if (xx + 0.5 - x) ** 2 + (yy + 0.5 - y) ** 2 <= r * r + 0.25 and 0 <= xx < 32:
                        img.set(ox + xx, yy, col + (255,))

    def inside(px, py, poly):
        hit = False
        j = len(poly) - 1
        for i in range(len(poly)):
            xi, yi = poly[i]
            xj, yj = poly[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit

    def seg_dist(px, py, a, b):
        ax, ay = a
        bx, by = b
        dx, dy = bx - ax, by - ay
        t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy))
        return math.hypot(px - ax - dx * t, py - ay - dy * t), t

    def wing(img, ox, up):
        # a broad wing from the shoulder: white coverts and primaries, the black secondaries and tertials along the
        # inner trailing edge (the red-crowned crane's pattern), the tip feathers splayed like fingers
        s = -1.0 if up else 1.0
        front_root = (18.5, 15.0)
        tip = (12.0, 15.0 + s * 13.0)
        tip_back = (6.5, 15.0 + s * 11.0)
        back_root = (9.5, 15.0)
        poly = [front_root, tip, tip_back, back_root]
        for y in range(h):
            for x in range(32):
                px, py = x + 0.5, y + 0.5
                if not inside(px, py, poly):
                    continue
                d, t = seg_dist(px, py, tip_back, back_root)
                col = white
                if d < 2.6 and t > 0.35:
                    col = black
                elif d < 3.6 and t > 0.35:
                    col = shade
                # the leading edge a little shaded, giving the wing its curve
                lead, _ = seg_dist(px, py, front_root, tip)
                if lead < 1.0 and col == white:
                    col = shade
                img.set(ox + x, y, col + (255,))
        # splayed primaries at the tip
        for k in range(3):
            fx = tip[0] - 1.5 - k * 1.8
            fy = tip[1] - s * (0.5 + k * 0.6)
            line(img, ox, fx + 1.0, fy - s * 1.5, fx - 0.6, fy + s * 0.8, white, 1.2)

    for frame, up in ((0, True), (1, False)):
        ox = frame * 32
        if not up:
            wing(img, ox, up)
        # the trailing legs
        line(img, ox, 7.0, 16.5, 0.5, 18.5, leg, 1.1)
        line(img, ox, 7.0, 17.2, 0.8, 19.6, leg, 1.1)
        # body and the white tail with the black "bustle" (tertials) over it
        ell(img, ox, 13.5, 16.0, 7.0, 3.0, white, -0.05)
        ell(img, ox, 7.5, 16.2, 3.0, 2.0, shade)
        ell(img, ox, 8.5, 15.0, 3.2, 1.6, black, 0.2)
        # the long black neck, the white nape, the head and the red crown
        line(img, ox, 19.0, 15.0, 25.5, 12.5, black, 2.2)
        line(img, ox, 21.5, 13.4, 25.0, 12.0, white, 0.9)
        ell(img, ox, 26.5, 12.0, 1.9, 1.5, black)
        img.set(ox + 26, 11, red + (255,))
        img.set(ox + 27, 11, red + (255,))
        img.set(ox + 26, 10, red + (255,))
        line(img, ox, 28.0, 12.2, 31.5, 12.8, beak, 0.9)
        if up:
            wing(img, ox, up)
        # a soft grey outline so the white reads against the white clouds
        src = [img.get(ox + x, y) for y in range(h) for x in range(32)]
        for y in range(h):
            for x in range(32):
                if src[y * 32 + x][3] == 0:
                    near = False
                    for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        xx, yy = x + ddx, y + ddy
                        if 0 <= xx < 32 and 0 <= yy < h and src[yy * 32 + xx][3] > 0:
                            near = True
                    if near:
                        img.set(ox + x, y, (120, 124, 134, 110))
    save(img, "bangau.png")


# ------------------------------------------------------------------ sungai bintang (Milky Way band)

def sungai_bintang():
    w, h = 512, 128
    img = Image(w, h)
    dust = Noise(37061, 32)
    lane = Noise(37062, 16)
    rnd = random.Random(37063)
    base = [[0.0] * w for _ in range(h)]
    cols = [[(0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        across = (y + 0.5) / h - 0.5
        for x in range(w):
            u = x / w
            # the band wanders a little along u (tileable)
            centre = 0.06 * math.sin(u * math.pi * 4.0) + 0.03 * math.sin(u * math.pi * 10.0 + 1.0)
            d = (across - centre) / 0.24
            band = math.exp(-d * d * 2.2)
            glow = dust.fbm(x / 16.0, y / 16.0, 4)
            lanes = lane.fbm(x / 32.0 * 2, y / 32.0 * 2, 3)
            dark = smooth((lanes - 0.48) / 0.18) * math.exp(-((across - centre) / 0.08) ** 2)
            core = 0.5 + 0.5 * math.cos(u * math.pi * 2.0)  # the warm galactic core at u = 0 / 1
            v = band * (0.35 + 0.75 * glow) * (1.0 - 0.75 * dark)
            col = mix((150, 170, 255), (255, 214, 170), core * 0.8 * band)
            base[y][x] = v
            cols[y][x] = col
    # star dust: thousands of small stars, denser in the band
    stars = []
    for i in range(5200):
        x = rnd.randrange(w)
        y = rnd.randrange(h)
        if rnd.random() < base[y][x] * 1.4 + 0.06:
            stars.append((x, y, rnd.random()))
    for y in range(h):
        edge = smooth(min(y, h - 1 - y) / 14.0)
        for x in range(w):
            v = base[y][x] * edge
            col = cols[y][x]
            dim = min(1.0, v * 5.0)
            put(img, x, y, tuple(int(c * dim) for c in col), 0.1 + 0.6 * v if v > 0.012 else 0.0)
    for x, y, b in stars:
        bright = 0.55 + 0.45 * b
        tint = (255, 255, 255) if b > 0.4 else ((200, 214, 255) if b > 0.15 else (255, 226, 190))
        a = clamp(0.5 + 0.5 * bright)
        img.set(x, y, tint + (int(a * 255),))
        if b > 0.93:
            for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = (x + ddx) % w, y + ddy
                if 0 <= yy < h:
                    img.blend(xx, yy, tint + (150,))
    save(img, "sungai_bintang.png")
    save_blur_meta("sungai_bintang.png")


# ------------------------------------------------------------------ lentera (sky lantern)

def lentera():
    n = 32
    img = Image(n, n)
    for y in range(n):
        for x in range(n):
            cx = (x + 0.5 - 16.0)
            cy = (y + 0.5)
            col = (0, 0, 0)
            a = 0.0
            # the halo
            hr = math.sqrt(cx * cx + (cy - 17.0) ** 2) / 15.5
            if hr < 1.0:
                col = (255, 170, 80)
                a = 0.32 * (1.0 - hr) ** 1.5
            # the paper body: a tapered bag, top y 5 (width 7) to bottom y 25 (width 10)
            if 5 <= cy <= 25:
                t = (cy - 5) / 20.0
                half = 7.0 + 3.2 * math.sin(t * math.pi * 0.62)
                if abs(cx) <= half:
                    glow = 0.55 + 0.45 * t  # brighter towards the flame
                    edge = abs(cx) / half
                    body = mix((255, 196, 110), (255, 120, 50), edge * 0.6)
                    body = mix(body, (255, 236, 180), max(0.0, t - 0.6) * 1.5)
                    # the bamboo ribs and the paper seams
                    if abs(cx) % 4.5 < 0.7 or int(cy) in (5, 15):
                        body = mix(body, (170, 70, 30), 0.55)
                    col = body
                    a = 0.75 + 0.25 * glow
                    if cy < 6.5:
                        col = mix(col, (230, 90, 40), 0.5)
            # the flame at the open bottom
            fr = math.sqrt((cx / 2.4) ** 2 + ((cy - 26.5) / 2.0) ** 2)
            if fr < 1.0:
                col = mix((255, 250, 220), (255, 190, 80), fr)
                a = 1.0
            put(img, x, y, tuple(int(c) for c in col), a)
    save(img, "lentera.png")
    save_blur_meta("lentera.png")


# ------------------------------------------------------------------ pilar (tether pillar band)

def pilar():
    w, h = 16, 64
    img = Image(w, h)
    noise = Noise(37071, 8)
    for y in range(h):
        for x in range(w):
            across = abs((x + 0.5) / w - 0.5) * 2.0
            core = math.exp(-(across / 0.28) ** 2)
            soft = math.exp(-(across / 0.75) ** 2)
            streak = noise.at(x / 2.0, y / 8.0)
            v = 0.85 * core + 0.4 * soft * (0.6 + 0.4 * streak)
            col = mix((190, 225, 255), (255, 255, 255), core)
            put(img, x, y, tuple(int(c) for c in col), clamp(v))
    save(img, "pilar.png")
    save_blur_meta("pilar.png")


# ------------------------------------------------------------------ pusaran (tribulation vortex from above)

def pusaran():
    n = 128
    img = Image(n, n)
    noise = Noise(37081, 16)
    c = (n - 1) / 2.0
    for y in range(n):
        for x in range(n):
            dx = (x - c) / c
            dy = (y - c) / c
            r = math.sqrt(dx * dx + dy * dy)
            if r >= 1.0:
                continue
            ang = math.atan2(dy, dx)
            # four storm arms winding in
            swirl = ang * 4.0 + math.log(max(r, 0.02)) * 5.5
            arms = 0.5 + 0.5 * math.cos(swirl)
            turb = noise.fbm((math.cos(ang) * r + 1) * 6.0, (math.sin(ang) * r + 1) * 6.0, 4)
            edge = 1.0 - smooth((r - 0.55) / 0.45)
            dark = clamp((0.35 + 0.55 * arms * (0.6 + 0.6 * turb)) * edge)
            col = mix((36, 30, 52), (84, 70, 110), arms * turb)
            # the violet-lit eye
            eye = math.exp(-(r / 0.16) ** 2)
            col = mix(col, (200, 150, 255), eye * 0.9)
            a = max(dark, eye * 0.9)
            put(img, x, y, tuple(int(v) for v in col), a)
    save(img, "pusaran.png")
    save_blur_meta("pusaran.png")


# ------------------------------------------------------------------ kilat (upward lightning)

def kilat():
    w, h = 32, 128
    img = Image(w, h)
    rnd = random.Random(37091)
    glow = [[0.0] * w for _ in range(h)]
    core = [[0.0] * w for _ in range(h)]

    def bolt(x, y0, y1, width, depth):
        y = y0
        while y < y1:
            nx = clamp(x + rnd.uniform(-2.6, 2.6), 3.0, w - 4.0)
            seg = rnd.randint(5, 10)
            for k in range(seg):
                t = k / seg
                px = x + (nx - x) * t
                py = y + k
                if py >= h:
                    break
                for xx in range(w):
                    d = abs(xx + 0.5 - px)
                    if d < width:
                        core[py][xx] = max(core[py][xx], 1.0 - d / width * 0.5)
                    glow[py][xx] = max(glow[py][xx], math.exp(-(d / (width * 3.2)) ** 2) * (0.9 if depth == 0 else 0.55))
                if depth < 2 and rnd.random() < 0.035:
                    bolt(px, py, min(h, py + rnd.randint(14, 30)), width * 0.6, depth + 1)
            x = nx
            y += seg

    bolt(16.0, 0, h, 1.6, 0)
    for y in range(h):
        fade = 1.0 - smooth((y - h * 0.82) / (h * 0.18))
        for x in range(w):
            c = core[y][x]
            g = glow[y][x]
            col = mix((170, 120, 255), (255, 255, 255), c)
            put(img, x, y, tuple(int(v) for v in col), max(c, g * 0.8) * fade)
    save(img, "kilat.png")


# ------------------------------------------------------------------ jaring (Cloud Net lattice)

def jaring():
    n = 64
    img = Image(n, n)
    c = (n - 1) / 2.0
    s = 7.5  # hex cell size in pixels
    for y in range(n):
        for x in range(n):
            dx = (x - c)
            dy = (y - c)
            r = math.sqrt(dx * dx + dy * dy) / c
            if r >= 1.0:
                continue
            # distance to the nearest line of a triangular lattice (three line families at 60 degrees)
            near = []
            for k in range(3):
                a = k * math.pi / 3.0 + 0.2
                p = dx * math.cos(a) + dy * math.sin(a)
                m = (p / s) % 1.0
                near.append(min(m, 1.0 - m) * s)
            near.sort()
            line = math.exp(-(near[0] / 0.8) ** 2)
            # knots where two lines cross
            knot = 0.6 * math.exp(-(near[1] / 1.1) ** 2) * line
            fall = 1.0 - smooth((r - 0.6) / 0.4)
            v = clamp(line * 0.9 + knot) * fall
            a = v
            col = mix((200, 235, 255), (255, 255, 255), v)
            put(img, x, y, tuple(int(cc) for cc in col), a)
    save(img, "jaring.png")
    save_blur_meta("jaring.png")


# ------------------------------------------------------------------ pedang (qi sword-light)

def pedang():
    w, h = 64, 16
    img = Image(w, h)
    for y in range(h):
        for x in range(w):
            u = (x + 0.5) / w
            v = abs((y + 0.5) / h - 0.5) * 2.0
            col = (0, 0, 0)
            a = 0.0
            # the soft glow around everything
            g = math.exp(-(v / 0.75) ** 2) * (0.35 + 0.25 * u)
            if g > 0.1:
                col = (150, 240, 210)
                a = g
            # the blade: from u 0.30 to the tip at u 0.98, half-width 0.22 narrowing to 0
            if 0.30 <= u <= 0.98:
                t = (u - 0.30) / 0.68
                half = 0.24 * (1.0 - t ** 2.2) + 0.02
                if v <= half:
                    edge = v / half
                    col = mix((255, 255, 255), (170, 245, 225), edge)
                    a = 1.0
            # the guard
            if 0.26 <= u < 0.30 and v < 0.62:
                col = (240, 200, 110)
                a = 1.0
            # the grip and the pommel
            if 0.06 <= u < 0.26 and v < 0.14:
                col = (120, 70, 50) if int(u * 64) % 3 else (90, 50, 36)
                a = 1.0
            if 0.02 <= u < 0.06 and v < 0.26:
                col = (240, 200, 110)
                a = 1.0
            put(img, x, y, tuple(int(c) for c in col), a)
    save(img, "pedang.png")


def preview(directory, scale=4):
    os.makedirs(directory, exist_ok=True)
    for path, img in WRITTEN:
        big = Image(img.width * scale, img.height * scale, (40, 44, 60, 255))
        for y in range(img.height):
            for x in range(img.width):
                p = img.get(x, y)
                for yy in range(scale):
                    for xx in range(scale):
                        big.blend(x * scale + xx, y * scale + yy, p)
        big.save(os.path.join(directory, os.path.basename(path)))


def main():
    halo()
    bangau()
    sungai_bintang()
    lentera()
    pilar()
    pusaran()
    kilat()
    jaring()
    pedang()
    for path, _ in WRITTEN:
        print("wrote", os.path.relpath(path))
    if len(sys.argv) > 1:
        preview(sys.argv[1])


if __name__ == "__main__":
    main()
