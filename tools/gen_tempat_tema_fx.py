"""Textures of the themed-town client effects (DESIGN 38.T.9.4, module 38TV-F): python tools/gen_tempat_tema_fx.py

Writes src/main/resources/assets/kultivator/textures/dunia/tempat/tema/:

  fx.png            256 x 256, 4 x 4 cells of 64 px (column, row) = TemaFxSel in the Java code:
                    (0,0) glow      soft round glow (lantern flames, lens halo, Buddha-light core)
                    (1,0) cincin    soft annulus (Buddha-light rings, tinted per layer)
                    (2,0) sinar     lighthouse beam: bright core across u, fading along v (v 0 = lens)
                    (3,0) kilau     six-ray lens flare
                    (0,1) gores     sand streak (long, grainy, wind-blown)
                    (1,1) debu      clumpy dust puff
                    (2,1) teratai   lotus lantern side view (petals, gold rims, leaf collar)
                    (3,1) api       candle flame (teardrop)
                    (0,2) atas      lotus lantern seen from above (petal ring around a candle)
                    (1,2) tali      three-strand hemp rope (tiles along v)
                    (2,2) riak      sharp ripple ring with caustic breaks
                    (3,2) siluet    head-and-shoulders shadow (Brocken spectre)
                    (0,3) petasan   firecracker paper bits and sparks
                    (1,3) kabut     soft mist puff
                    (2,3) pantul    stretched water reflection glow
                    (3,3) pelangi   pre-coloured glory ring (violet inside, red outside)
  awan.png          256 x 256 tileable cloud-sea top (Kota Gunung, Lautan Awan)
  kabut.png         256 x 128 mist band, tileable along u, soft top and bottom (Kabut Gunung)
  pasir.png         256 x 128 sand-haze band, tileable along u (sandstorm shells around the camera)
  dinding_pasir.png 512 x 128 the haboob front on the horizon, tileable along u (sandstorm omen)
  ombak.png         128 x 128 tileable storm-bay water with foam (OmbakFx)
  pendayung.png     64 x 64, 4 x 4 cells of 16 px: the dragon-boat rowers' impostor skin (PendayungImpostor)
  mural.png         512 x 512, 4 x 8 cells of 128 x 64: the cave murals of Tebing Seribu Lukisan (MuralBillboard,
                    also the picture slot of the tema_mural screen): cell 2*i + f = story i (0..11), frame f (0, 1);
                    cells 24-27 the player's own mural (icon 0 crown/title, 1 sword/boss, 2 lightning/tribulation,
                    3 lotus/other); cells 28-31 the half-buried murals of Reruntuhan Gurun (dancers, caravan; 2 frames)

Previews (not shipped): tools/keluaran/tempat_tema_fx/*.png at 2x/4x, opened with Read.

Every picture is drawn procedurally with anti-aliased shapes (signed-distance coverage) and periodic value noise; the
same seed always gives the same bytes. Mural palette after the Mogao caves of Dunhuang: earth red ground, malachite
green, lapis blue, cinnabar, ochre, lead white gone grey and faces darkened by age.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from png import Image  # noqa: E402

OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "dunia", "tempat", "tema")
PREVIEW = os.path.join(HERE, "keluaran", "tempat_tema_fx")


# ====================================================================== canvas

def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def shade(c, f):
    return (c[0] * f, c[1] * f, c[2] * f, c[3] if len(c) > 3 else 255)


def smooth(e0, e1, x):
    if e1 == e0:
        return 1.0 if x >= e1 else 0.0
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


class Canvas:
    """Float RGBA canvas with 'over' compositing; shapes use 1-px anti-aliased coverage."""

    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w = w
        self.h = h
        self.px = [[float(fill[0]), float(fill[1]), float(fill[2]), float(fill[3])] for _ in range(w * h)]

    def get(self, x, y):
        return self.px[y * self.w + x]

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            p = self.px[y * self.w + x]
            p[0], p[1], p[2], p[3] = float(c[0]), float(c[1]), float(c[2]), float(c[3] if len(c) > 3 else 255)

    def over(self, x, y, c, cov=1.0):
        if cov <= 0.0 or not (0 <= x < self.w and 0 <= y < self.h):
            return
        sa = (c[3] if len(c) > 3 else 255) / 255.0 * min(1.0, cov)
        if sa <= 0.0:
            return
        p = self.px[y * self.w + x]
        da = p[3] / 255.0
        oa = sa + da * (1 - sa)
        if oa <= 0:
            return
        for i in range(3):
            p[i] = (c[i] * sa + p[i] * da * (1 - sa)) / oa
        p[3] = oa * 255.0

    def add(self, x, y, c, k=1.0):
        """Adds light: brightens colour and raises alpha (glow sprites on transparent ground)."""
        if not (0 <= x < self.w and 0 <= y < self.h) or k <= 0:
            return
        p = self.px[y * self.w + x]
        a = (c[3] if len(c) > 3 else 255) / 255.0 * k
        for i in range(3):
            p[i] = min(255.0, p[i] * (p[3] / 255.0) + c[i] * a) if p[3] > 0 else min(255.0, c[i])
        p[3] = min(255.0, p[3] + 255.0 * a)
        if p[3] > 0:
            # keep colour as straight (not premultiplied) values
            pass

    # ------------------------------------------------------------ shapes (all coordinates in pixels, float)

    def disc(self, cx, cy, r, c, soft=1.0):
        x0, x1 = int(math.floor(cx - r - 2)), int(math.ceil(cx + r + 2))
        y0, y1 = int(math.floor(cy - r - 2)), int(math.ceil(cy + r + 2))
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r
                self.over(x, y, c, 0.5 - d / soft)

    def ellipse(self, cx, cy, rx, ry, c, rot=0.0, soft=1.0):
        rr = max(rx, ry)
        cs, sn = math.cos(rot), math.sin(rot)
        x0, x1 = int(cx - rr - 2), int(cx + rr + 2)
        y0, y1 = int(cy - rr - 2), int(cy + rr + 2)
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                u = dx * cs + dy * sn
                v = -dx * sn + dy * cs
                k = math.hypot(u / max(rx, 0.01), v / max(ry, 0.01))
                d = (k - 1.0) * min(rx, ry)
                self.over(x, y, c, 0.5 - d / soft)

    def ring(self, cx, cy, r0, r1, c, soft=1.0):
        x0, x1 = int(cx - r1 - 2), int(cx + r1 + 2)
        y0, y1 = int(cy - r1 - 2), int(cy + r1 + 2)
        mid, half = (r0 + r1) / 2.0, (r1 - r0) / 2.0
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                d = abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - mid) - half
                self.over(x, y, c, 0.5 - d / soft)

    def line(self, x0, y0, x1, y1, w, c, soft=1.0):
        r = w / 2.0
        bx0, bx1 = int(min(x0, x1) - r - 2), int(max(x0, x1) + r + 2)
        by0, by1 = int(min(y0, y1) - r - 2), int(max(y0, y1) + r + 2)
        vx, vy = x1 - x0, y1 - y0
        ll = vx * vx + vy * vy
        for y in range(max(0, by0), min(self.h, by1 + 1)):
            for x in range(max(0, bx0), min(self.w, bx1 + 1)):
                px, py = x + 0.5 - x0, y + 0.5 - y0
                t = 0.0 if ll == 0 else max(0.0, min(1.0, (px * vx + py * vy) / ll))
                d = math.hypot(px - vx * t, py - vy * t) - r
                self.over(x, y, c, 0.5 - d / soft)

    def polyline(self, pts, w, c, w1=None):
        n = len(pts) - 1
        for i in range(n):
            ww = w if w1 is None else w + (w1 - w) * (i / max(1, n - 1))
            self.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], ww, c)

    def poly(self, pts, c, soft=1.0):
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        n = len(pts)
        for y in range(max(0, int(min(ys)) - 1), min(self.h, int(max(ys)) + 2)):
            for x in range(max(0, int(min(xs)) - 1), min(self.w, int(max(xs)) + 2)):
                px, py = x + 0.5, y + 0.5
                inside = False
                dmin = 1e9
                j = n - 1
                for i in range(n):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi:
                        inside = not inside
                    vx, vy = xj - xi, yj - yi
                    ll = vx * vx + vy * vy
                    t = 0.0 if ll == 0 else max(0.0, min(1.0, ((px - xi) * vx + (py - yi) * vy) / ll))
                    d = math.hypot(px - xi - vx * t, py - yi - vy * t)
                    dmin = min(dmin, d)
                    j = i
                sd = -dmin if inside else dmin
                self.over(x, y, c, 0.5 - sd / soft)

    def rect(self, x0, y0, x1, y1, c):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], c)

    def save(self, path):
        img = Image(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                p = self.px[y * self.w + x]
                img.set(x, y, (round(p[0]), round(p[1]), round(p[2]), round(p[3])))
        img.save(path)


def bez(p0, p1, p2, p3, n=16):
    out = []
    for i in range(n + 1):
        t = i / n
        a = (1 - t) ** 3
        b = 3 * (1 - t) ** 2 * t
        c = 3 * (1 - t) * t * t
        d = t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


# ====================================================================== periodic value noise

class Noise:
    """Value noise on a wrapping lattice: fbm(x, y) tiles every (px, py) lattice cells at octave 0."""

    def __init__(self, seed, px=8, py=8):
        self.r = random.Random(seed)
        self.px = px
        self.py = py
        self.tab = {}

    def lat(self, o, i, j, pxo, pyo):
        key = (o, i % pxo, j % pyo)
        v = self.tab.get(key)
        if v is None:
            v = self.r.random()
            self.tab[key] = v
        return v

    def value(self, x, y, o=0):
        pxo, pyo = self.px << o, self.py << o
        xi, yi = math.floor(x), math.floor(y)
        fx, fy = x - xi, y - yi
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        a = self.lat(o, xi, yi, pxo, pyo)
        b = self.lat(o, xi + 1, yi, pxo, pyo)
        c = self.lat(o, xi, yi + 1, pxo, pyo)
        d = self.lat(o, xi + 1, yi + 1, pxo, pyo)
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy

    def fbm(self, u, v, octaves=4, gain=0.5):
        """u, v in 0..1 (tile); returns 0..1."""
        s, amp, tot = 0.0, 1.0, 0.0
        for o in range(octaves):
            f = 1 << o
            s += amp * self.value(u * self.px * f, v * self.py * f, o)
            tot += amp
            amp *= gain
        return s / tot


# ====================================================================== fx.png

CELL = 64


def cell_canvas():
    return Canvas(CELL, CELL)


def blit(dst, src, ox, oy):
    for y in range(src.h):
        for x in range(src.w):
            p = src.get(x, y)
            dst.put(ox + x, oy + y, p)


def glow():
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 31.0
            a = max(0.0, 1.0 - d) ** 2.2 * 0.85 + max(0.0, 1.0 - d * 3.2) ** 2 * 0.15
            c.put(x, y, (255, 255, 255, 255 * min(1.0, a)))
    return c


def cincin():
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 31.0
            band = math.exp(-((d - 0.84) / 0.07) ** 2)
            inner = math.exp(-((d - 0.84) / 0.18) ** 2) * 0.25
            c.put(x, y, (255, 255, 255, 255 * min(1.0, band + inner) * (1.0 if d < 1.0 else 0.0)))
    return c


def sinar(n):
    c = cell_canvas()
    for y in range(CELL):
        v = y / (CELL - 1)
        along = (1.0 - v) ** 1.35 * 0.92 + 0.08 * (1.0 - v)
        for x in range(CELL):
            u = (x + 0.5) / CELL * 2 - 1
            core = math.exp(-(u / 0.16) ** 2)
            soft = math.exp(-(u / 0.55) ** 2) * 0.45
            dust = 0.82 + 0.18 * n.fbm(x / CELL, v * 0.5 + 0.13, 3)
            a = (core + soft) * along * dust * (1.0 if abs(u) < 1 else 0)
            col = mix((255, 236, 200), (255, 255, 255), core)
            c.put(x, y, (col[0], col[1], col[2], 255 * min(1.0, a)))
    return c


def kilau():
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            d = math.hypot(dx, dy) / 31.0
            ang = math.atan2(dy, dx)
            rays = max(0.0, math.cos(ang * 3)) ** 28 * max(0.0, 1 - d) ** 1.2
            rays2 = max(0.0, math.cos(ang * 3 + math.pi / 3)) ** 60 * max(0.0, 1 - d * 1.6) * 0.5
            core = max(0.0, 1 - d * 2.6) ** 2
            a = min(1.0, rays + rays2 + core)
            c.put(x, y, (255, 250, 235, 255 * a))
    return c


def gores(n):
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            u = (x + 0.5) / CELL * 2 - 1
            v = (y + 0.5) / CELL * 2 - 1
            body = math.exp(-(v / 0.16) ** 2) * max(0.0, 1 - abs(u) ** 2.2)
            tail = math.exp(-(v / 0.32) ** 2) * max(0.0, 1 - abs(u)) * 0.35
            grain = 0.55 + 0.45 * n.fbm(x / CELL * 4, y / CELL, 3)
            a = min(1.0, (body + tail) * grain)
            col = mix((214, 176, 110), (246, 226, 180), grain)
            c.put(x, y, (col[0], col[1], col[2], 255 * a))
    return c


def debu(n):
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 30.0
            k = n.fbm(x / CELL, y / CELL, 4)
            a = smooth(1.0, 0.25, d + (0.5 - k) * 0.6) * (0.35 + 0.65 * k)
            col = mix((190, 150, 96), (236, 210, 160), k)
            c.put(x, y, (col[0], col[1], col[2], 255 * min(1.0, a)))
    return c


def teratai():
    c = cell_canvas()
    pink = hexc("#E87A9C")
    pale = hexc("#FFEAF2")
    gold = hexc("#E8B850")
    leaf = hexc("#4E8A4A")
    leaf_d = hexc("#2F5E32")
    # leaf collar
    c.ellipse(32, 54, 30, 6, leaf_d)
    c.ellipse(32, 52.5, 28, 5, leaf)
    for i in range(9):
        x = 6 + i * 6.5
        c.line(x, 51, x + 2, 55, 0.8, shade(leaf_d, 0.9))
    # candle glow behind the petals
    for y in range(4, 34):
        for x in range(16, 48):
            d = math.hypot(x + 0.5 - 32, (y + 0.5 - 22) * 1.3) / 15.0
            c.over(x, y, (255, 214, 130, 220), max(0.0, 1 - d) ** 2 * 0.7)
    # petals fan out from the heart (32, 50); back row wider and paler, front row upright
    for row, angs, length, width, br in ((0, (-78, -56, -34, 34, 56, 78), 21, 6.0, 0.82), (1, (-44, -22, 0, 22, 44), 25, 6.8, 1.0)):
        for a in angs:
            ang = math.radians(a - 90)
            cs, sn = math.cos(ang), math.sin(ang)
            for t in range(10):
                k = t / 9.0
                px = 32 + cs * length * (0.18 + 0.62 * k)
                py = 50 + sn * length * (0.18 + 0.62 * k)
                w = width * math.sin(math.pi * (0.15 + 0.8 * k)) + 0.6
                col = shade(mix(pink, pale, 0.15 + 0.75 * k), br)
                c.ellipse(px, py, w * 0.5 + 1.2, w * 0.5 + 1.2, col, 0)
            tip = (32 + cs * length * 0.95, 50 + sn * length * 0.95)
            c.line(32 + cs * 5, 50 + sn * 5, tip[0], tip[1], 0.7, shade(mix(pink, pale, 0.4), br * 0.92))
            c.disc(tip[0], tip[1], 1.0, shade(gold, br))
    return c


def api():
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            u = (x + 0.5 - 32) / 14.0
            v = (y + 0.5) / CELL
            # teardrop: wide at v ~ 0.72, pointed at the top
            width = max(0.0, math.sin(min(1.0, v / 0.78) * math.pi * 0.5)) * (1.0 if v < 0.78 else max(0.0, 1 - (v - 0.78) / 0.2))
            d = abs(u) / max(0.05, width)
            a = smooth(1.0, 0.55, d) * smooth(0.0, 0.12, v)
            core = smooth(0.75, 0.0, d) * smooth(0.35, 0.7, v)
            col = mix(mix((255, 120, 30), (255, 210, 90), v), (255, 255, 235), core)
            c.put(x, y, (col[0], col[1], col[2], 255 * a))
    return c


def atas():
    c = cell_canvas()
    pink = hexc("#F2A0B8")
    pale = hexc("#FFE8F0")
    gold = hexc("#E8B850")
    c.disc(32, 32, 30, hexc("#3F7A40", 230))
    for i in range(12):
        ang = i / 12 * math.tau
        cs, sn = math.cos(ang), math.sin(ang)
        tip = (32 + cs * 27, 32 + sn * 27)
        l = (32 + math.cos(ang - 0.26) * 13, 32 + math.sin(ang - 0.26) * 13)
        r = (32 + math.cos(ang + 0.26) * 13, 32 + math.sin(ang + 0.26) * 13)
        c.poly([tip, r, (32 + cs * 8, 32 + sn * 8), l], mix(pink, pale, 0.3 * (i % 2)))
        c.polyline([l, tip, r], 0.7, gold)
    for i in range(8):
        ang = (i + 0.5) / 8 * math.tau
        cs, sn = math.cos(ang), math.sin(ang)
        tip = (32 + cs * 17, 32 + sn * 17)
        l = (32 + math.cos(ang - 0.35) * 8, 32 + math.sin(ang - 0.35) * 8)
        r = (32 + math.cos(ang + 0.35) * 8, 32 + math.sin(ang + 0.35) * 8)
        c.poly([tip, r, (32, 32), l], mix(pale, pink, 0.2))
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 9.0
            c.over(x, y, (255, 230, 150, 255), max(0.0, 1 - d) ** 1.5)
    return c


def tali():
    c = cell_canvas()
    base = hexc("#8A6A40")
    light = hexc("#C8A470")
    dark = hexc("#4A3620")
    for y in range(CELL):
        for x in range(CELL):
            u = (x + 0.5) / CELL
            # three twisted strands: diagonal stripes, period 64 / 4 so it tiles along v
            s = ((u * 1.0 + y / CELL * 4.0) * 3.0) % 1.0
            ridge = math.sin(s * math.pi)
            edge = math.sin(u * math.pi) ** 0.5
            col = mix(dark, mix(base, light, ridge ** 2), 0.25 + 0.75 * ridge)
            col = shade(col, 0.55 + 0.45 * edge)
            fib = ((x * 7 + y * 13) % 5) / 40.0
            c.put(x, y, (col[0] * (1 - fib), col[1] * (1 - fib), col[2] * (1 - fib), 255))
    return c


def riak(n):
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            d = math.hypot(dx, dy) / 31.0
            ang = (math.atan2(dy, dx) / math.tau) % 1.0
            breaks = 0.55 + 0.45 * n.fbm(ang, 0.37, 3)
            band = math.exp(-((d - 0.9) / 0.035) ** 2) * breaks + math.exp(-((d - 0.8) / 0.05) ** 2) * 0.25
            c.put(x, y, (255, 255, 255, 255 * min(1.0, band) * (1 if d < 1 else 0)))
    return c


def siluet():
    s = Canvas(CELL, CELL)
    col = (10, 12, 20, 255)
    s.disc(32, 21, 9.5, col)              # head
    s.ellipse(32, 31.5, 5, 3.5, col)       # neck
    s.poly([(10, 64), (14, 42), (22, 34), (42, 34), (50, 42), (54, 64)], col)  # shoulders
    s.ellipse(32, 13, 10, 4, col)          # hair knot base
    s.disc(32, 8.5, 4, col)                # top knot
    # soften: box blur twice
    for _ in range(2):
        out = Canvas(CELL, CELL)
        for y in range(CELL):
            for x in range(CELL):
                acc = 0.0
                n = 0
                for oy in (-1, 0, 1):
                    for ox in (-1, 0, 1):
                        xx, yy = x + ox, y + oy
                        if 0 <= xx < CELL and 0 <= yy < CELL:
                            acc += s.get(xx, yy)[3]
                            n += 1
                out.put(x, y, (10, 12, 20, acc / n))
        s = out
    # fade the bottom so the body melts into the cloud
    for y in range(CELL):
        f = smooth(64, 44, y)
        for x in range(CELL):
            p = s.get(x, y)
            p[3] *= f
    return s


def petasan():
    c = cell_canvas()
    r = random.Random(3801)
    for i in range(26):
        x, y = r.uniform(6, 58), r.uniform(6, 58)
        ang = r.uniform(0, math.pi)
        w, h = r.uniform(2, 4.5), r.uniform(1.2, 2.4)
        col = (r.choice([(210, 30, 30), (230, 50, 40), (180, 20, 25), (240, 200, 70)])) + (255,)
        c.ellipse(x, y, w, h, col, ang)
    for i in range(18):
        x, y = r.uniform(10, 54), r.uniform(10, 54)
        c.disc(x, y, r.uniform(0.8, 1.6), (255, 240, 170, 255))
        c.disc(x, y, r.uniform(2.5, 4), (255, 200, 90, 70))
    return c


def kabut_puff(n):
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 31.0
            k = n.fbm(x / CELL, y / CELL, 4)
            a = smooth(1.0, 0.1, d + (0.5 - k) * 0.5) * (0.5 + 0.5 * k)
            c.put(x, y, (255, 255, 255, 255 * min(1.0, a * 0.9)))
    return c


def pantul():
    c = cell_canvas()
    for y in range(CELL):
        for x in range(CELL):
            u = (x + 0.5 - 32) / 31.0
            v = (y + 0.5 - 32) / 31.0
            d = math.hypot(u, v * 2.6)
            wob = 0.85 + 0.15 * math.sin(y * 1.7)
            a = max(0.0, 1 - d) ** 1.8 * wob
            c.put(x, y, (255, 236, 190, 255 * min(1.0, a)))
    return c


def pelangi():
    c = cell_canvas()
    bands = [(0.58, (150, 110, 255)), (0.66, (90, 150, 255)), (0.74, (100, 230, 150)), (0.82, (255, 235, 110)),
             (0.90, (255, 150, 90)), (0.97, (230, 80, 90))]
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 31.0
            r = g = b = 0.0
            a = 0.0
            for pos, col in bands:
                w = math.exp(-((d - pos) / 0.045) ** 2)
                r += col[0] * w
                g += col[1] * w
                b += col[2] * w
                a += w
            if a > 0:
                c.put(x, y, (min(255, r / a), min(255, g / a), min(255, b / a), 255 * min(1.0, a * 0.9) * (1 if d < 1 else 0)))
    return c


def gen_fx():
    n = Noise(380901, 4, 4)
    cells = {
        (0, 0): glow(), (1, 0): cincin(), (2, 0): sinar(n), (3, 0): kilau(),
        (0, 1): gores(Noise(380902, 4, 4)), (1, 1): debu(Noise(380903, 4, 4)), (2, 1): teratai(), (3, 1): api(),
        (0, 2): atas(), (1, 2): tali(), (2, 2): riak(Noise(380904, 8, 2)), (3, 2): siluet(),
        (0, 3): petasan(), (1, 3): kabut_puff(Noise(380905, 4, 4)), (2, 3): pantul(), (3, 3): pelangi(),
    }
    atlas = Canvas(256, 256)
    for (col, row), cv in cells.items():
        blit(atlas, cv, col * CELL, row * CELL)
    return atlas


# ====================================================================== tileable bands

def gen_awan():
    n = Noise(380911, 8, 8)
    n2 = Noise(380912, 16, 16)
    c = Canvas(256, 256)
    top = (255, 255, 255)
    mid = (226, 232, 242)
    low = (168, 182, 206)
    for y in range(256):
        for x in range(256):
            u, v = x / 256.0, y / 256.0
            k = n.fbm(u, v, 5, 0.55)
            puff = n2.fbm(u + 0.31, v + 0.17, 3)
            dens = smooth(0.27, 0.56, k * 0.8 + puff * 0.2)
            light = smooth(0.4, 0.85, k)
            col = mix(low, mix(mid, top, light), 0.35 + 0.65 * light)
            c.put(x, y, (col[0], col[1], col[2], 255 * (0.18 + 0.82 * dens)))
    return c


def band_profile(v, lo=0.12, hi=0.88):
    """Soft vertical bell: 0 at the top and bottom edges, 1 in the middle."""
    return smooth(0.0, lo, v) * smooth(1.0, hi, v)


def gen_kabut():
    n = Noise(380921, 8, 4)
    c = Canvas(256, 128)
    for y in range(128):
        for x in range(256):
            u, v = x / 256.0, y / 128.0
            k = n.fbm(u, v, 5)
            streak = Noise.fbm(n, u * 0.5, v * 2.0 % 1.0, 2)
            a = band_profile(v, 0.28, 0.66) * (0.35 + 0.65 * smooth(0.3, 0.75, k * 0.7 + streak * 0.3))
            col = mix((214, 222, 232), (250, 252, 255), k)
            c.put(x, y, (col[0], col[1], col[2], 255 * a))
    return c


def gen_pasir():
    n = Noise(380931, 8, 4)
    c = Canvas(256, 128)
    for y in range(128):
        for x in range(256):
            u, v = x / 256.0, y / 128.0
            k = n.fbm(u, v, 5)
            streak = 0.5 + 0.5 * math.sin((v * 5 + u + k * 0.9) * math.tau)
            a = band_profile(v, 0.18, 0.8) * (0.45 + 0.55 * smooth(0.2, 0.8, k * 0.75 + streak * 0.25))
            col = mix((176, 128, 70), (232, 196, 136), k * 0.7 + (1 - v) * 0.3)
            c.put(x, y, (col[0], col[1], col[2], 255 * a))
    return c


def gen_dinding():
    n = Noise(380941, 16, 4)
    hn = Noise(380942, 16, 1)
    c = Canvas(512, 128)
    for x in range(512):
        u = x / 512.0
        # billowing top edge between 8 and 44 px from the top
        h = 8 + 36 * (1 - hn.fbm(u, 0.5, 5, 0.6))
        for y in range(128):
            v = y / 128.0
            k = n.fbm(u, v, 5)
            edge = smooth(h - 3, h + 6, y + (k - 0.5) * 10)
            dens = edge * (0.8 + 0.2 * smooth(0.0, 0.6, v))
            rim = math.exp(-((y - h - 4) / 5.0) ** 2) * 0.35
            col = mix((236, 196, 128), (122, 82, 44), smooth(0.1, 1.0, v) * 0.85 + (1 - k) * 0.15)
            col = mix(col, (255, 226, 170), rim)
            # cauliflower shading: lighter knobs
            col = shade(col + (255,), 0.85 + 0.25 * k)
            c.put(x, y, (min(255, col[0]), min(255, col[1]), min(255, col[2]), 255 * min(1.0, dens)))
    return c


def gen_ombak():
    n = Noise(380951, 4, 4)
    c = Canvas(128, 128)
    deep = (22, 58, 92)
    sea = (46, 94, 140)
    foam = (236, 244, 248)
    for y in range(128):
        for x in range(128):
            u, v = x / 128.0, y / 128.0
            k = n.fbm(u, v, 5)
            wave = 0.5 + 0.5 * math.sin((v * 3 + k * 0.9) * math.tau)
            crest = smooth(0.82, 0.97, wave * 0.75 + k * 0.25)
            col = mix(mix(deep, sea, wave), foam, crest)
            c.put(x, y, (col[0], col[1], col[2], 255 * (0.62 + 0.36 * crest)))
    return c


# ====================================================================== pendayung.png (16 px cells)

def gen_pendayung():
    c = Canvas(64, 64)
    skin = hexc("#D9A577")
    skin_d = hexc("#B07A50")
    hair = hexc("#1E1712")
    band = hexc("#C8282A")
    red = hexc("#B82A26")
    red_d = hexc("#7E1A18")
    gold = hexc("#E0B040")
    yel = hexc("#E0A82A")
    yel_d = hexc("#A87618")
    sash = hexc("#F2D36A")
    trou = hexc("#3A3A48")
    wood = hexc("#8A5A30")
    wood_l = hexc("#B07A44")

    def cell(ci, ri, fn):
        for y in range(16):
            for x in range(16):
                col = fn(x, y)
                if col is not None:
                    c.put(ci * 16 + x, ri * 16 + y, col)

    def face(x, y, old=False):
        if y < 3:
            return hair
        if y == 3 or y == 4:
            return band if not old else hexc("#8A6A3A")
        if y == 7 and x in (4, 5, 10, 11):
            return hexc("#1A1410")
        if y == 6 and x in (4, 5, 10, 11):
            return shade(hair, 1.3)
        if y == 8 and x in (7, 8):
            return skin_d
        if y == 11 and 6 <= x <= 9:
            return hexc("#8A3A30")
        if old and y >= 12 and 4 <= x <= 11:
            return hexc("#D8D4CC")
        return skin if (x + y) % 7 else shade(skin, 0.96)

    cell(0, 0, lambda x, y: face(x, y))
    cell(1, 0, lambda x, y: hair if (y < 3 or (x > 9 and y < 13)) else (band if y in (3, 4) else (skin_d if (x == 6 and 6 <= y <= 9) else skin)))
    cell(2, 0, lambda x, y: (band if y in (3, 4) else (band if (7 <= x <= 8 and 5 <= y <= 12) else hair)))
    cell(3, 0, lambda x, y: hair if (x - 7.5) ** 2 + (y - 7.5) ** 2 > 9 else shade(hair, 1.4))

    def tunic(x, y, base, dark, front):
        if y >= 12:
            return trou
        if 9 <= y <= 10:
            return sash
        if front and (y == 0 or (abs(x - 7.5) < 1.5 and y < 9)):
            return gold
        if x in (0, 15):
            return dark
        return base if (x * 3 + y) % 9 else shade(base, 0.9)

    cell(0, 1, lambda x, y: tunic(x, y, red, red_d, True))
    cell(1, 1, lambda x, y: tunic(x, y, red, red_d, False))
    cell(2, 1, lambda x, y: tunic(x, y, red, red_d, False))
    cell(3, 1, lambda x, y: red_d if (x in (0, 15) or y in (0, 15)) else red)
    cell(0, 2, lambda x, y: red if y < 5 else (gold if y == 5 else (skin if y < 14 else skin_d)))
    cell(1, 2, lambda x, y: red_d if y < 5 else (skin_d if y > 13 else skin))
    cell(2, 2, lambda x, y: tunic(x, y, yel, yel_d, True))
    cell(3, 2, lambda x, y: tunic(x, y, yel, yel_d, False))
    cell(0, 3, lambda x, y: face(x, y, True))
    cell(1, 3, lambda x, y: wood_l if (x + y // 3) % 4 else wood)
    cell(2, 3, lambda x, y: yel if y < 5 else (gold if y == 5 else (skin if y < 14 else skin_d)))
    cell(3, 3, lambda x, y: shade(trou, 0.8))
    return c


# ====================================================================== mural.png (128 x 64 cells)

MW, MH = 128, 64
EARTH = hexc("#8E4A2E")
EARTH_L = hexc("#B0683E")
OCHRE = hexc("#C98B4A")
MALA = hexc("#3E8E6E")
MALA_D = hexc("#2A5E4A")
LAPIS = hexc("#2F4F8F")
LAPIS_L = hexc("#5A7AB4")
CINN = hexc("#B23A26")
WHITE = hexc("#D8CCB0")
GREY = hexc("#6E6A66")
INK = hexc("#3A2418")
SKIN = hexc("#C88E62")
SKIN_DARK = hexc("#5E4034")
GOLD = hexc("#D8A848")
NIGHT = hexc("#22283E")


def mural_ground(cv, seed, tone=EARTH, faded=0.0):
    n = Noise(seed, 8, 4)
    for y in range(MH):
        for x in range(MW):
            k = n.fbm(x / MW, y / MH, 4)
            col = mix(shade(tone, 0.86), mix(tone, EARTH_L, 0.4), k)
            col = mix(col, (186, 160, 120, 255), faded * (0.5 + 0.5 * k))
            cv.put(x, y, (col[0], col[1], col[2], 255))


def mural_border(cv):
    for x in range(MW):
        for y in (0, 1, MH - 2, MH - 1):
            cv.put(x, y, INK)
        for y in (2, MH - 3):
            cv.put(x, y, MALA_D if (x // 4) % 2 else CINN)
    for y in range(MH):
        for x in (0, 1, MW - 2, MW - 1):
            cv.put(x, y, INK)
        for x in (2, MW - 3):
            cv.put(x, y, MALA_D if (y // 4) % 2 else CINN)
    for x in range(6, MW - 6, 8):
        cv.disc(x, 3.5, 0.7, WHITE)
        cv.disc(x, MH - 3.5, 0.7, WHITE)


def mural_age(cv, seed, amount=1.0):
    """Cracks, flaked paint, soot: the thousand-year look."""
    r = random.Random(seed)
    n = Noise(seed + 1, 8, 4)
    for y in range(MH):
        for x in range(MW):
            k = n.fbm(x / MW, y / MH, 5)
            if k > 0.72 - 0.1 * amount:
                p = cv.get(x, y)
                f = smooth(0.62, 0.9, k) * 0.45 * amount
                cv.put(x, y, mix(p, (160, 120, 88, 255), f))
    for i in range(int(5 * amount)):
        x, y = r.uniform(8, MW - 8), r.uniform(8, MH - 8)
        pts = [(x, y)]
        for j in range(r.randint(3, 6)):
            x += r.uniform(-7, 7)
            y += r.uniform(-4, 4)
            pts.append((x, y))
        cv.polyline(pts, 0.6, (60, 36, 24, 150))


def cloud_scroll(cv, x, y, s, col):
    """Auspicious cloud: three curls."""
    cv.ellipse(x, y, 5 * s, 2.2 * s, col)
    cv.ellipse(x - 4 * s, y - 1.5 * s, 2.6 * s, 2.0 * s, col)
    cv.ellipse(x + 4 * s, y - 1.2 * s, 2.4 * s, 1.8 * s, col)
    cv.ring(x - 4 * s, y - 1.5 * s, 0.8 * s, 1.5 * s, shade(col, 0.75))
    cv.ring(x + 4 * s, y - 1.2 * s, 0.7 * s, 1.3 * s, shade(col, 0.75))


def halo(cv, x, y, r, col=GOLD):
    cv.ring(x, y, r - 0.9, r, col)
    cv.disc(x, y, r - 1.0, (col[0], col[1], col[2], 60))


def head(cv, x, y, r, skin=SKIN_DARK, hair=INK, knot=True):
    cv.disc(x, y, r + 0.6, INK)
    cv.disc(x, y, r, skin)
    cv.ellipse(x, y - r * 0.55, r * 0.95, r * 0.55, hair)
    if knot:
        cv.disc(x, y - r * 1.25, r * 0.45, hair)


def robe_figure(cv, x, foot, h, robe, sash=None, halo_on=False, arm=0.0, staff=False, facing=1, skin=SKIN_DARK):
    """A standing figure: long robe trapezoid, sleeves, head with knot, optional halo and staff."""
    top = foot - h
    hr = h * 0.11
    if halo_on:
        halo(cv, x, top + hr, hr * 2.0)
    body = [(x - h * 0.12, top + hr * 2.2), (x + h * 0.12, top + hr * 2.2), (x + h * 0.22, foot), (x - h * 0.22, foot)]
    cv.poly([(p[0] - 0.6, p[1]) for p in body], INK)
    cv.poly(body, robe)
    cv.line(x, top + hr * 2.4, x + facing * h * 0.05, foot - 1, 0.7, shade(robe, 0.7))
    if sash:
        cv.line(x - h * 0.15, top + h * 0.45, x + h * 0.15, top + h * 0.42, 1.2, sash)
        cv.polyline(bez((x + h * 0.12, top + h * 0.45), (x + h * 0.3, top + h * 0.6), (x + h * 0.2, top + h * 0.8),
                        (x + h * 0.32, foot - 1)), 0.9, sash)
    sx = x + facing * h * 0.13
    ex = sx + facing * h * (0.18 + 0.1 * arm)
    ey = top + h * (0.48 - 0.25 * arm)
    cv.line(sx, top + h * 0.3, ex, ey, h * 0.08, robe)
    cv.disc(ex, ey, h * 0.035, skin)
    if staff:
        cv.line(ex, ey - h * 0.35, ex + facing * 1, foot, 0.9, INK)
        cv.disc(ex, ey - h * 0.37, 1.2, GOLD)
    head(cv, x, top + hr, hr, skin)


def feitian(cv, x, y, s, robe, ribbon, phase=0.0, facing=1):
    """A flying apsara: horizontal body, legs trailing, long ribbons waving with phase."""
    f = facing
    body = [(x, y), (x - f * 9 * s, y + 1.5 * s), (x - f * 16 * s, y + 4 * s), (x - f * 15 * s, y + 6 * s),
            (x - f * 6 * s, y + 4 * s), (x + f * 2 * s, y + 3 * s)]
    cv.poly([(p[0], p[1] + 0.5) for p in body], INK)
    cv.poly(body, robe)
    for k, off in ((0, 0.0), (1, 1.7)):
        w = math.sin(phase * math.tau + off) * 3 * s
        pts = bez((x - f * 2 * s, y + 1 * s), (x - f * 10 * s, y - 6 * s + w), (x - f * 18 * s, y + 8 * s - w),
                  (x - f * 26 * s, y + 1 * s + w))
        cv.polyline(pts, 1.1 * s, ribbon)
    head(cv, x + f * 2.5 * s, y - 1.2 * s, 2.2 * s)
    halo(cv, x + f * 2.5 * s, y - 1.2 * s, 3.6 * s)
    ax = x + f * (5 + 2 * math.sin(phase * math.tau)) * s
    cv.line(x + f * 1 * s, y + 1.5 * s, ax, y - 3 * s, 1.0 * s, robe)
    cv.disc(ax, y - 3.2 * s, 0.9 * s, SKIN_DARK)
    # flower petals scattered
    cv.disc(ax + f * 2 * s, y - 5 * s, 0.9 * s, CINN)


def camel(cv, x, foot, s, col=OCHRE, facing=1, load=True, step=0.0):
    f = facing
    by = foot - 9 * s
    cv.ellipse(x, by, 8 * s + 0.6, 3.6 * s + 0.6, INK)
    cv.ellipse(x, by, 8 * s, 3.6 * s, col)
    for hx in (-3.2, 2.6):
        cv.ellipse(x + f * hx * s, by - 3.2 * s, 2.4 * s, 2.4 * s, col)
        cv.ellipse(x + f * hx * s, by - 4.6 * s, 2.2 * s, 1.0 * s, shade(col, 0.7))
    # neck and head
    cv.polyline(bez((x + f * 6.5 * s, by - 1 * s), (x + f * 10 * s, by - 2 * s), (x + f * 9 * s, by - 8 * s),
                    (x + f * 12 * s, by - 9 * s)), 2.0 * s, col)
    cv.ellipse(x + f * 13 * s, by - 9 * s, 2.4 * s, 1.3 * s, col)
    cv.disc(x + f * 13.5 * s, by - 9.4 * s, 0.4 * s, INK)
    # legs (pace: same-side legs together)
    for lx, ph in ((-5.5, 0.0), (-4.0, 0.5), (4.5, 0.0), (6.0, 0.5)):
        sw = math.sin((step + ph) * math.tau) * 1.2 * s
        cv.line(x + f * lx * s, by + 2 * s, x + f * lx * s + sw, foot, 1.3 * s, shade(col, 0.85))
    cv.line(x - f * 8 * s, by - 0.5 * s, x - f * 9.5 * s, by + 3 * s, 0.8 * s, shade(col, 0.7))
    if load:
        cv.rect(x - 3 * s, by - 7.5 * s, x + 2 * s, by - 5 * s, CINN)
        cv.ellipse(x - 0.5 * s, by - 8.3 * s, 3.5 * s, 1.0 * s, MALA)
        cv.disc(x + f * 8 * s, by - 2 * s, 0.8 * s, GOLD)


def dunes(cv, base, amp, col, phase=0.0, period=60.0):
    for x in range(MW):
        h = base - amp * (0.6 + 0.4 * math.sin(x / period * math.tau + phase)) - amp * 0.3 * math.sin(x / 17.0 + phase * 2)
        for y in range(int(h), MH):
            p = cv.get(x, y)
            k = smooth(h, h + 8, y)
            cv.put(x, y, mix(mix(col, shade(col, 0.82), k), p, 0.0))
        cv.over(x, int(h), shade(col, 1.15), 0.8)


def crescent_lake(cv, x, y, r, col=LAPIS):
    for yy in range(int(y - r - 2), int(y + r + 2)):
        for xx in range(int(x - r * 1.6 - 2), int(x + r * 1.6 + 2)):
            if not (0 <= xx < MW and 0 <= yy < MH):
                continue
            d1 = math.hypot((xx + 0.5 - x) / 1.6, yy + 0.5 - y) - r
            d2 = math.hypot((xx + 0.5 - x - r * 0.55) / 1.6, yy + 0.5 - y + r * 0.35) - r * 0.85
            sd = max(d1, -d2)
            cv.over(xx, yy, col, 0.5 - sd)
    for i in range(3):
        cv.line(x - r * 1.0 + i * 2, y + r * 0.2 + i * 1.6, x - r * 0.2 + i * 2, y + r * 0.5 + i * 1.6, 0.6, LAPIS_L)


def city(cv, x0, x1, foot, h, col=OCHRE, buried=0.0):
    cv.rect(x0, foot - h, x1, foot, shade(col, 0.95))
    for x in range(int(x0), int(x1), 4):
        cv.rect(x, foot - h - 2, x + 2, foot - h, shade(col, 0.95))
    for tx in (x0 - 2, (x0 + x1) / 2 - 3, x1 - 4):
        cv.rect(tx, foot - h - 7, tx + 6, foot, shade(col, 0.85))
        cv.poly([(tx - 1, foot - h - 7), (tx + 3, foot - h - 12), (tx + 7, foot - h - 7)], MALA_D)
    mid = (x0 + x1) / 2
    cv.ellipse(mid + 10, foot - h - 1, 5, 4, WHITE)
    cv.rect(mid - 3, foot - 7, mid + 3, foot, INK)
    if buried > 0:
        dunes(cv, foot + 2 - buried * h, buried * 6, OCHRE, 0.7, 40.0)


def stars(cv, seed, n=18, y1=30):
    r = random.Random(seed)
    for i in range(n):
        x, y = r.uniform(6, MW - 6), r.uniform(6, y1)
        cv.disc(x, y, r.uniform(0.4, 0.9), WHITE)


def scorpion(cv, x, y, s, col=hexc("#2A1A14"), glow=CINN, tail=0.0):
    cv.ellipse(x, y, 9 * s, 3.4 * s, col)
    for i in range(4):
        cv.ellipse(x - (9 + i * 3.4) * s, y - i * 1.6 * s, 2.4 * s, 2.0 * s, col)
    tx = x - 21 * s
    ty = y - 6 * s
    pts = bez((tx, ty), (tx - 4 * s, ty - 7 * s), (tx + 6 * s, ty - 14 * s + tail * 3), (tx + 12 * s, ty - 9 * s + tail * 4))
    cv.polyline(pts, 2.2 * s, col, 1.2 * s)
    cv.disc(pts[-1][0], pts[-1][1], 1.3 * s, glow)
    for side in (-1, 1):
        cv.line(x + 7 * s, y, x + 13 * s, y + side * 3 * s, 1.4 * s, col)
        cv.ellipse(x + 15 * s, y + side * 3.5 * s, 2.6 * s, 1.3 * s, col)
    for i in range(4):
        lx = x - 5 * s + i * 3.5 * s
        cv.line(lx, y + 1.5 * s, lx - 2 * s, y + 5 * s, 0.8 * s, col)
    cv.disc(x + 6 * s, y - 1.6 * s, 0.6 * s, glow)
    cv.disc(x + 4.5 * s, y - 1.8 * s, 0.6 * s, glow)


def demon(cv, x, foot, s, fallen=True):
    col = hexc("#2C3A48")
    if fallen:
        cv.ellipse(x, foot - 3 * s, 10 * s, 3 * s, col)
        head(cv, x + 10 * s, foot - 5 * s, 2.8 * s, skin=hexc("#4A5A66"), hair=INK, knot=False)
        for side in (-1, 1):
            cv.line(x + 10 * s + side * 1.6 * s, foot - 7.5 * s, x + 10 * s + side * 3 * s, foot - 11 * s, 0.9 * s, hexc("#C8B890"))
        cv.line(x - 4 * s, foot - 4 * s, x - 12 * s, foot - 1, 1.6 * s, col)
    else:
        robe_figure(cv, x, foot, 30 * s, col, CINN, skin=hexc("#4A5A66"))


def gen_mural_cell(i, f):
    cv = Canvas(MW, MH)
    ph = f * 0.5
    if i == 0:  # Pendiri Kota: the first caravan finds the crescent lake
        mural_ground(cv, 3801000, EARTH)
        stars(cv, 3801001, 6, 16)
        dunes(cv, 40, 6, OCHRE, 0.4)
        crescent_lake(cv, 70, 47, 9)
        for k, cx in enumerate((26, 44, 98)):
            camel(cv, cx + f * 1.0, 56, 1.0, OCHRE if k != 1 else EARTH_L, 1 if cx < 70 else -1, k != 2, step=ph + k * 0.3)
        robe_figure(cv, 14, 58, 20, MALA, CINN, arm=0.8 + 0.2 * f, staff=True)
        cloud_scroll(cv, 70, 14, 1.4, WHITE)
    elif i == 1:  # Bidadari Terbang: apsaras over the lake
        mural_ground(cv, 3801010, LAPIS)
        for k, (x, y, fc) in enumerate(((40, 22, 1), (96, 30, -1), (66, 44, 1))):
            feitian(cv, x, y, 1.15, (MALA, CINN, WHITE)[k], (CINN, MALA, GOLD)[k], ph + k * 0.33, fc)
        for k in range(5):
            cloud_scroll(cv, 14 + k * 26, 56 - (k % 2) * 4, 1.0, WHITE)
    elif i == 2:  # Bintang Penunjuk Jalan: the caravan by night
        mural_ground(cv, 3801020, NIGHT)
        stars(cv, 3801021 + f, 28, 34)
        cv.disc(104, 12, 3.4, GOLD)
        for k in range(6):
            cv.line(104, 12, 104 + math.cos(k) * 6, 12 + math.sin(k) * 6, 0.5, GOLD)
        dunes(cv, 50, 4, shade(OCHRE, 0.55), 1.1)
        for k, cx in enumerate((22, 46, 70)):
            camel(cv, cx + f, 58, 0.85, shade(OCHRE, 0.7), 1, True, step=ph + k * 0.25)
        robe_figure(cv, 92, 59, 18, LAPIS, GOLD, arm=1.0, facing=-1)
    elif i == 3:  # Malam Badai: the Buried City vanishes
        mural_ground(cv, 3801030, hexc("#7A4A30"))
        city(cv, 36, 92, 52, 14, OCHRE, buried=0.25 + 0.15 * f)
        for k in range(7):
            y = 10 + k * 6
            pts = [(x, y + 3 * math.sin((x / 18.0) + k + ph * 3)) for x in range(4, MW - 4, 6)]
            cv.polyline(pts, 1.2, (220, 180, 120, 170))
        robe_figure(cv, 16, 60, 16, WHITE, CINN, arm=1.0)
    elif i == 4:  # Batu Ukur Air and the first priest
        mural_ground(cv, 3801040, EARTH)
        cv.rect(58, 14, 70, 58, hexc("#4E8C8A"))
        for k in range(6):
            cv.line(59, 20 + k * 6, 69, 20 + k * 6, 0.7, WHITE)
        for x in range(4, MW - 4):
            y = 48 + math.sin(x / 7.0 + ph * 6) * 1.2
            cv.line(x, y, x + 1, y, 1.0, LAPIS_L)
        cv.rect(4, 49, MW - 4, 60, (47, 79, 143, 200))
        robe_figure(cv, 40, 50, 26, WHITE, MALA, halo_on=True, arm=0.6 + 0.3 * f, facing=1)
        robe_figure(cv, 90, 50, 22, MALA, CINN, arm=0.2, facing=-1)
        cloud_scroll(cv, 104, 14, 1.2, WHITE)
    elif i == 5:  # Tari Putar: whirling dancers under the full moon
        mural_ground(cv, 3801050, hexc("#5A2E2A"))
        cv.disc(64, 14, 8, WHITE)
        cv.disc(64, 14, 8.8, (216, 204, 176, 90))
        for k, cx in enumerate((24, 54, 84, 110)):
            skirt = 9 + 1.5 * math.sin((ph + k * 0.25) * math.tau)
            cv.ellipse(cx, 52, skirt, 4, (MALA, CINN, LAPIS, OCHRE)[k])
            robe_figure(cv, cx, 50, 24, (CINN, MALA, OCHRE, LAPIS)[k], GOLD, arm=1.0 if (k + f) % 2 else 0.2,
                        facing=1 if k % 2 else -1)
            for a in range(3):
                ang = (a / 3 + ph + k * 0.1) * math.tau
                cv.line(cx + math.cos(ang) * 8, 40 + math.sin(ang) * 3, cx + math.cos(ang + 0.6) * 10,
                        40 + math.sin(ang + 0.6) * 3.5, 0.6, GOLD)
    elif i == 6:  # Pendeta yang Diusir (the moving mural)
        mural_ground(cv, 3801060, EARTH)
        cv.rect(6, 18, 34, 58, hexc("#6E3A26"))
        cv.poly([(2, 18), (20, 6), (38, 18)], MALA_D)
        cv.rect(14, 34, 26, 58, INK)
        for k, cx in enumerate((32, 40)):
            robe_figure(cv, cx, 58, 20, MALA if k else CINN, GOLD, arm=1.0, facing=1)
        dunes(cv, 54, 3, OCHRE, 2.0)
        px = 86 + f * 4
        robe_figure(cv, px, 58, 24, GREY, hexc("#4E6A4A"), arm=0.4 + 0.6 * f, staff=True, facing=1 if f == 0 else -1)
        # frame 1: he turns to the viewer, eyes burning green, the sand curls around him
        if f == 1:
            cv.disc(px - 1, 39.2, 0.7, hexc("#7AF0A0"))
            cv.disc(px + 1.2, 39.2, 0.7, hexc("#7AF0A0"))
            for k in range(3):
                pts = bez((px - 10 + k * 2, 58), (px - 14, 46 - k * 3), (px + 12, 40 - k * 2), (px + 8, 30 - k * 3))
                cv.polyline(pts, 0.8, (120, 230, 160, 150))
        scorpion(cv, 112, 58, 0.35, tail=f)
    elif i == 7:  # Raja Kalajengking under the dunes
        mural_ground(cv, 3801070, hexc("#6A3424"))
        dunes(cv, 28, 5, OCHRE, 0.2)
        for x in range(4, MW - 4):
            for y in range(32, MH - 4):
                if (x * 7 + y * 3) % 23 == 0:
                    cv.over(x, y, shade(OCHRE, 0.7), 0.8)
        scorpion(cv, 74, 46, 1.2, tail=f)
        robe_figure(cv, 18, 27, 14, WHITE, CINN, arm=0.8)
    elif i == 8:  # Pedagang Seberang: silk for jade
        mural_ground(cv, 3801080, EARTH)
        cv.poly([(84, 18), (96, 8), (108, 18)], MALA_D)
        cv.rect(88, 18, 104, 30, hexc("#7A3A26"))
        cv.poly([(82, 30), (96, 22), (110, 30)], MALA_D)
        cv.rect(86, 30, 106, 58, hexc("#6E3424"))
        camel(cv, 22, 58, 0.9, OCHRE, 1, True, step=ph)
        robe_figure(cv, 50, 58, 26, LAPIS, GOLD, arm=1.0, facing=1)
        robe_figure(cv, 72, 58, 26, CINN, MALA, arm=1.0, facing=-1)
        sw = 1.5 * f
        cv.rect(56, 36 - sw, 66, 39 - sw, WHITE)
        cv.line(56, 37.5 - sw, 66, 37.5 - sw, 0.6, MALA)
        cv.disc(61, 33 - sw, 1.6, hexc("#5EC8A0"))
    elif i == 9:  # Iblis Pasir dikalahkan
        mural_ground(cv, 3801090, hexc("#5E2E22"))
        robe_figure(cv, 44, 58, 34, CINN, GOLD, halo_on=True, arm=1.0, staff=True)
        cv.line(56, 16 + f, 70, 52 - f, 1.0, GOLD)
        demon(cv, 90, 60, 1.2, True)
        for k in range(4):
            cloud_scroll(cv, 14 + k * 32, 10, 0.8, WHITE)
        cv.disc(108, 30, 6 + f, (200, 40, 30, 60))
    elif i == 10:  # Mata Air yang Menyanyi: musicians of the singing dunes
        mural_ground(cv, 3801100, EARTH)
        dunes(cv, 46, 5, OCHRE, 0.9)
        for k, cx in enumerate((26, 58, 90)):
            robe_figure(cv, cx, 56, 22, (MALA, LAPIS, CINN)[k], GOLD, arm=0.6, facing=1)
            cv.ellipse(cx + 5, 46, 3.2, 4.2, OCHRE, 0.5)
            cv.line(cx + 6, 42, cx + 11, 34, 0.8, INK)
        for k in range(5):
            y = 14 + k * 4
            pts = [(x, y + 2 * math.sin(x / 10.0 + k + ph * 4)) for x in range(10, MW - 10, 4)]
            cv.polyline(pts, 0.7, (232, 210, 150, 160))
    else:  # 11 Pertapa di Gua ke-12
        mural_ground(cv, 3801110, hexc("#4E2A22"))
        cv.ellipse(64, 50, 34, 30, hexc("#2A1814"))
        cv.ellipse(64, 52, 30, 26, hexc("#3A221A"))
        halo(cv, 64, 26, 13, GOLD)
        cv.ellipse(64, 50, 14, 6, GREY)
        robe_figure(cv, 64, 50, 24, GREY, MALA, halo_on=False, arm=0.1)
        stars(cv, 3801111 + f, 10, 20)
        scorpion(cv, 96, 58, 0.3, tail=f)
        cv.disc(64, 26, 2 + f, (255, 230, 160, 90))
    mural_border(cv)
    mural_age(cv, 3801500 + i, 1.0)
    return cv


def gen_player_mural(icon):
    cv = Canvas(MW, MH)
    mural_ground(cv, 3802000 + icon, hexc("#8A5A3A"))
    cv.rect(30, 8, 98, 56, hexc("#A8744A"))
    cloud_scroll(cv, 20, 14, 1.0, WHITE)
    cloud_scroll(cv, 108, 50, 1.0, WHITE)
    cx, cy = 64, 32
    halo(cv, cx, cy, 20, GOLD)
    if icon == 0:  # crown: a title
        cv.poly([(46, 42), (48, 24), (56, 34), (64, 20), (72, 34), (80, 24), (82, 42)], GOLD)
        cv.rect(46, 42, 82, 47, shade(GOLD, 0.8))
        for x in (56, 64, 72):
            cv.disc(x, 44.5, 1.6, CINN)
    elif icon == 1:  # sword over a fallen beast: the strongest boss
        cv.ellipse(64, 46, 18, 4, hexc("#2C3A48"))
        cv.line(64, 14, 64, 44, 2.2, WHITE)
        cv.line(57, 36, 71, 36, 2.0, GOLD)
        cv.line(64, 36, 64, 44, 2.4, INK)
    elif icon == 2:  # tribulation lightning through clouds
        cloud_scroll(cv, 64, 16, 1.6, GREY)
        cv.polyline([(66, 18), (58, 30), (66, 30), (56, 48)], 2.0, hexc("#C8D8FF"))
        cv.polyline([(66, 18), (58, 30), (66, 30), (56, 48)], 0.8, WHITE)
    else:  # lotus: any other feat
        for k in range(7):
            ang = math.pi + (k / 6) * math.pi
            cv.ellipse(64 + math.cos(ang) * 9, 38 + math.sin(ang) * 9, 3.6, 8, CINN if k % 2 else hexc("#E8A0B0"), ang + math.pi / 2)
        cv.ellipse(64, 42, 16, 3, MALA)
    mural_border(cv)
    mural_age(cv, 3802100 + icon, 0.35)
    return cv


def gen_ruin_mural(kind, f):
    cv = Canvas(MW, MH)
    mural_ground(cv, 3803000 + kind, EARTH, faded=0.45)
    if kind == 0:
        for k, cx in enumerate((30, 64, 98)):
            feitian(cv, cx, 26 + (k % 2) * 10, 0.9, (MALA, CINN, LAPIS)[k], GOLD, f * 0.5 + k * 0.2, 1 if k % 2 == 0 else -1)
    else:
        dunes(cv, 50, 4, OCHRE, 0.3)
        for k, cx in enumerate((20, 46, 72, 98)):
            camel(cv, cx + f, 47, 0.8, OCHRE, 1, True, step=f * 0.5 + k * 0.25)
    mural_border(cv)
    mural_age(cv, 3803100 + kind, 1.8)
    # half buried: sand over the lower third
    dunes(cv, 54, 6, hexc("#D8B878"), 1.3 + kind, 50.0)
    return cv


def gen_mural():
    atlas = Canvas(512, 512)
    for i in range(12):
        for f in range(2):
            idx = i * 2 + f
            blit(atlas, gen_mural_cell(i, f), (idx % 4) * MW, (idx // 4) * MH)
    for icon in range(4):
        idx = 24 + icon
        blit(atlas, gen_player_mural(icon), (idx % 4) * MW, (idx // 4) * MH)
    for kind in range(2):
        for f in range(2):
            idx = 28 + kind * 2 + f
            blit(atlas, gen_ruin_mural(kind, f), (idx % 4) * MW, (idx // 4) * MH)
    return atlas


# ====================================================================== previews

def scaled(cv, k, bg=(40, 40, 46)):
    out = Canvas(cv.w * k, cv.h * k)
    for y in range(cv.h):
        for x in range(cv.w):
            p = cv.get(x, y)
            a = p[3] / 255.0
            col = (p[0] * a + bg[0] * (1 - a), p[1] * a + bg[1] * (1 - a), p[2] * a + bg[2] * (1 - a), 255)
            for yy in range(k):
                for xx in range(k):
                    out.put(x * k + xx, y * k + yy, col)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    jobs = [
        ("fx.png", gen_fx, 2),
        ("awan.png", gen_awan, 2),
        ("kabut.png", gen_kabut, 2),
        ("pasir.png", gen_pasir, 2),
        ("dinding_pasir.png", gen_dinding, 1),
        ("ombak.png", gen_ombak, 2),
        ("pendayung.png", gen_pendayung, 6),
        ("mural.png", gen_mural, 2),
    ]
    only = set(sys.argv[1:])
    for name, fn, k in jobs:
        if only and name.split(".")[0] not in only:
            continue
        cv = fn()
        path = os.path.join(OUT, name)
        cv.save(path)
        scaled(cv, k).save(os.path.join(PREVIEW, name.replace(".png", "_x%d.png" % k)))
        print("wrote", os.path.normpath(path), "%dx%d" % (cv.w, cv.h))


if __name__ == "__main__":
    main()
