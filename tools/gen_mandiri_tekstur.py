"""Textures of module 39E (DESIGN 39.7.3-39.7.5, 39.8): the GUI icons and ornaments of the Jalan Hidup tab, the board,
the diary book and the notice cards, and the thought-bubble atlas drawn in the world. Everything is drawn here as vector
shapes on a 4x supersampled canvas (anti-aliased by the downsample), then given an ink outline so the icons read on the
jade-paper screens. Deterministic (fixed seeds); no downloads.

Writes src/main/resources/assets/kultivator/textures/sekte/mandiri/:
  jalan.png     256 x 64   ten life paths, 8 x 2 cells of 32 px (JalanHidup order)
  kegiatan.png  128 x 64   thirty activities, 8 x 4 cells of 16 px (Kegiatan order)
  suasana.png   128 x 16   seven diary moods (EntriHarian.Suasana order)
  dorongan.png  128 x 16   seven drives (Dorong order)
  pikir.png     256 x 96   sixteen thought icons + the cloud + the violet cloud, 8 x 3 cells of 32 px (MandiriPayloads.PIKIR_*)
  toast.png     160 x 16   nine notice kinds (Kebijakan.Pemberitahuan order) + the "while you were away" letter
  kertas.png     64 x 64   aged diary paper (tiles seamlessly)
  hiasan.png    128 x 64   ribbon, seal, corner cloud, scroll, pouch, ink stone, book (Ikon.java cells)

Run: python -I tools/gen_mandiri_tekstur.py [preview_dir]   (the preview is a 4x sheet of every atlas)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "sekte", "mandiri")
SS = 4


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(c1[i] + (c2[i] - c1[i]) * t for i in range(4))


def shade(c, f):
    return (min(255, c[0] * f), min(255, c[1] * f), min(255, c[2] * f), c[3])


INK = rgb("1B2521")
GOLD = rgb("D4A637")
GOLD_D = rgb("8A671B")
GOLD_L = rgb("F7DC7E")
SEAL = rgb("B3281D")
SEAL_D = rgb("6E160F")
JADE = rgb("2E8963")
JADE_L = rgb("67C99B")
STEEL = rgb("C8D2DC")
STEEL_D = rgb("7C8896")
BRONZE = rgb("A8702E")
BRONZE_D = rgb("5E3A16")
WOOD = rgb("7E4A27")
WOOD_D = rgb("45230F")
PAPER = rgb("F1D57E")
PAPER_W = rgb("F4EEDC")
QI = rgb("47B2FF")
QI_L = rgb("B4E2FF")
VIOLET = rgb("9B4FD1")
VIOLET_D = rgb("4E1F75")
WHITE = (255, 255, 255, 255)
ERASE = (0, 0, 0, -1)  # sentinel: clears the samples (cut-outs)
SKIN = rgb("E8C49A")


class Canvas:
    """RGBA float canvas at SS x the final size; shapes take unit coordinates of one cell."""

    def __init__(self, w, h):
        self.w, self.h = w * SS, h * SS
        self.px = [[0.0, 0.0, 0.0, 0.0] for _ in range(self.w * self.h)]
        self.ox = self.oy = 0
        self.cell = 16

    def at(self, ox, oy, cell):
        self.ox, self.oy, self.cell = ox * SS, oy * SS, cell * SS
        return self

    def _blend(self, x, y, c):
        if not (0 <= x < self.w and 0 <= y < self.h):
            return
        if c[3] < 0:
            self.px[y * self.w + x] = [0.0, 0.0, 0.0, 0.0]
            return
        sa = c[3] / 255.0
        if sa <= 0:
            return
        d = self.px[y * self.w + x]
        da = d[3]
        oa = sa + da * (1 - sa)
        for i in range(3):
            d[i] = (c[i] * sa + d[i] * da * (1 - sa)) / oa
        d[3] = oa

    def fill(self, test, bbox, col):
        """Fills every sample whose unit position passes {@code test(u, v)} inside the unit bbox."""
        u0, v0, u1, v1 = bbox
        x0 = max(0, int(self.ox + u0 * self.cell) - 1)
        x1 = min(self.w, int(self.ox + u1 * self.cell) + 2)
        y0 = max(0, int(self.oy + v0 * self.cell) - 1)
        y1 = min(self.h, int(self.oy + v1 * self.cell) + 2)
        x0 = max(x0, self.ox)
        y0 = max(y0, self.oy)
        x1 = min(x1, self.ox + self.cell)
        y1 = min(y1, self.oy + self.cell)
        for y in range(y0, y1):
            v = (y - self.oy + 0.5) / self.cell
            for x in range(x0, x1):
                u = (x - self.ox + 0.5) / self.cell
                if test(u, v):
                    c = col(u, v) if callable(col) else col
                    self._blend(x, y, c)

    # ---------------------------------------------------------------- shapes (unit coordinates)

    def circle(self, cx, cy, r, col):
        self.fill(lambda u, v: (u - cx) ** 2 + (v - cy) ** 2 <= r * r, (cx - r, cy - r, cx + r, cy + r), col)

    def ellipse(self, cx, cy, rx, ry, col, rot=0.0):
        ca, sa = math.cos(rot), math.sin(rot)

        def t(u, v):
            du, dv = u - cx, v - cy
            a = du * ca + dv * sa
            b = -du * sa + dv * ca
            return (a / rx) ** 2 + (b / ry) ** 2 <= 1.0
        m = max(rx, ry)
        self.fill(t, (cx - m, cy - m, cx + m, cy + m), col)

    def ring(self, cx, cy, r0, r1, col, a0=None, a1=None):
        def t(u, v):
            d = (u - cx) ** 2 + (v - cy) ** 2
            if not (r0 * r0 <= d <= r1 * r1):
                return False
            if a0 is None:
                return True
            a = math.atan2(v - cy, u - cx) % (2 * math.pi)
            lo, hi = a0 % (2 * math.pi), a1 % (2 * math.pi)
            return lo <= a <= hi if lo <= hi else (a >= lo or a <= hi)
        self.fill(t, (cx - r1, cy - r1, cx + r1, cy + r1), col)

    def rect(self, x0, y0, x1, y1, col):
        self.fill(lambda u, v: x0 <= u <= x1 and y0 <= v <= y1, (x0, y0, x1, y1), col)

    def rrect(self, x0, y0, x1, y1, r, col):
        def t(u, v):
            if not (x0 <= u <= x1 and y0 <= v <= y1):
                return False
            cx = min(max(u, x0 + r), x1 - r)
            cy = min(max(v, y0 + r), y1 - r)
            return (u - cx) ** 2 + (v - cy) ** 2 <= r * r
        self.fill(t, (x0, y0, x1, y1), col)

    def poly(self, pts, col):
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]

        def t(u, v):
            inside = False
            n = len(pts)
            j = n - 1
            for i in range(n):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > v) != (yj > v) and u < (xj - xi) * (v - yi) / (yj - yi + 1e-12) + xi:
                    inside = not inside
                j = i
            return inside
        self.fill(t, (min(xs), min(ys), max(xs), max(ys)), col)

    def line(self, x0, y0, x1, y1, w, col):
        dx, dy = x1 - x0, y1 - y0
        ll = dx * dx + dy * dy or 1e-9
        r = w / 2.0

        def t(u, v):
            k = max(0.0, min(1.0, ((u - x0) * dx + (v - y0) * dy) / ll))
            px, py = x0 + k * dx, y0 + k * dy
            return (u - px) ** 2 + (v - py) ** 2 <= r * r
        self.fill(t, (min(x0, x1) - r, min(y0, y1) - r, max(x0, x1) + r, max(y0, y1) + r), col)

    def path(self, pts, w, col):
        for a, b in zip(pts, pts[1:]):
            self.line(a[0], a[1], b[0], b[1], w, col)

    def star(self, cx, cy, r0, r1, n, col, rot=-math.pi / 2):
        pts = []
        for i in range(n * 2):
            r = r1 if i % 2 == 0 else r0
            a = rot + i * math.pi / n
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        self.poly(pts, col)

    # ---------------------------------------------------------------- output

    def image(self, outline=True, shadow=False):
        W, H = self.w // SS, self.h // SS
        img = Image(W, H)
        for y in range(H):
            for x in range(W):
                r = g = b = a = 0.0
                for yy in range(SS):
                    row = (y * SS + yy) * self.w
                    for xx in range(SS):
                        p = self.px[row + x * SS + xx]
                        r += p[0] * p[3]
                        g += p[1] * p[3]
                        b += p[2] * p[3]
                        a += p[3]
                if a > 0:
                    img.set(x, y, (r / a, g / a, b / a, a / (SS * SS) * 255))
        if outline:
            ink(img)
        return img


def ink(img, color=(27, 37, 33), strength=0.9):
    """A soft 1 px ink outline around the opaque shape (only on transparent pixels)."""
    W, H = img.width, img.height
    alpha = [img.get(x, y)[3] for y in range(H) for x in range(W)]
    for y in range(H):
        for x in range(W):
            if alpha[y * W + x] > 40:
                continue
            m = 0
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H:
                    m = max(m, alpha[ny * W + nx])
            for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H:
                    m = max(m, alpha[ny * W + nx] * 0.45)
            if m > 60:
                img.blend(x, y, (color[0], color[1], color[2], min(255, m * strength)))


def grad(c0, c1, vertical=True, lo=0.0, hi=1.0):
    def f(u, v):
        t = (v if vertical else u)
        t = max(0.0, min(1.0, (t - lo) / max(1e-6, hi - lo)))
        return mix(c0, c1, t)
    return f


def radial(cx, cy, r, c0, c1):
    def f(u, v):
        t = min(1.0, math.hypot(u - cx, v - cy) / r)
        return mix(c0, c1, t)
    return f


# ==================================================================================================== icon library
# Every icon draws into the current cell of a canvas, unit square, (0, 0) top left.


def i_pedang(c, accent=STEEL):
    c.line(0.18, 0.85, 0.80, 0.20, 0.10, grad(rgb("F2F6FA"), STEEL_D, False))
    c.line(0.20, 0.83, 0.78, 0.22, 0.03, rgb("FFFFFF", 200))
    c.line(0.82, 0.85, 0.20, 0.20, 0.10, grad(STEEL_D, rgb("F2F6FA"), False))
    for (x, y, d) in ((0.30, 0.72, 1), (0.70, 0.72, -1)):
        c.line(x - 0.10, y - 0.10 * d, x + 0.10, y + 0.10 * d, 0.07, GOLD)
        c.line(x - 0.02, y + 0.04, x - 0.10 * d * 0.0 - 0.04, y + 0.16, 0.06, WOOD_D)
    c.line(0.30, 0.72, 0.16, 0.88, 0.07, WOOD_D)
    c.line(0.70, 0.72, 0.84, 0.88, 0.07, WOOD_D)
    c.circle(0.15, 0.89, 0.05, GOLD)
    c.circle(0.85, 0.89, 0.05, GOLD)
    c.path([(0.14, 0.93), (0.11, 0.99)], 0.04, SEAL)
    c.path([(0.86, 0.93), (0.89, 0.99)], 0.04, SEAL)


def i_kuali(c):
    c.ellipse(0.5, 0.30, 0.20, 0.07, grad(QI_L, JADE_L))
    for x in (0.38, 0.52, 0.62):
        c.path([(x, 0.26), (x - 0.03, 0.16), (x + 0.02, 0.08)], 0.035, rgb("D8D4CC", 170))
    c.line(0.30, 0.78, 0.24, 0.94, 0.07, BRONZE_D)
    c.line(0.70, 0.78, 0.76, 0.94, 0.07, BRONZE_D)
    c.line(0.50, 0.80, 0.50, 0.95, 0.07, BRONZE_D)
    c.ellipse(0.5, 0.58, 0.30, 0.25, radial(0.42, 0.50, 0.36, rgb("E0A050"), BRONZE_D))
    c.ellipse(0.5, 0.36, 0.30, 0.07, BRONZE_D)
    c.ellipse(0.5, 0.35, 0.25, 0.05, rgb("2A1A0A"))
    c.ring(0.18, 0.42, 0.03, 0.065, BRONZE, None, None)
    c.ring(0.82, 0.42, 0.03, 0.065, BRONZE, None, None)
    c.rect(0.30, 0.56, 0.70, 0.60, GOLD_D)
    c.circle(0.5, 0.58, 0.05, GOLD)


def i_bagua(c):
    c.poly([(0.5 + 0.44 * math.cos(a), 0.5 + 0.44 * math.sin(a)) for a in [math.pi / 8 + i * math.pi / 4 for i in range(8)]],
           grad(rgb("2C4A6A"), rgb("142C44")))
    for i in range(8):
        a = i * math.pi / 4 - math.pi / 2
        cx, cy = 0.5 + 0.33 * math.cos(a), 0.5 + 0.33 * math.sin(a)
        tx, ty = -math.sin(a), math.cos(a)
        for k in range(3):
            r = 0.27 + k * 0.05
            px, py = 0.5 + r * math.cos(a), 0.5 + r * math.sin(a)
            broken = (i >> k) & 1
            if broken:
                c.line(px - tx * 0.08, py - ty * 0.08, px - tx * 0.025, py - ty * 0.025, 0.025, GOLD_L)
                c.line(px + tx * 0.025, py + ty * 0.025, px + tx * 0.08, py + ty * 0.08, 0.025, GOLD_L)
            else:
                c.line(px - tx * 0.08, py - ty * 0.08, px + tx * 0.08, py + ty * 0.08, 0.025, GOLD_L)
    # taiji
    c.circle(0.5, 0.5, 0.16, WHITE)
    c.fill(lambda u, v: (u - 0.5) ** 2 + (v - 0.5) ** 2 <= 0.0256 and u < 0.5, (0.34, 0.34, 0.66, 0.66), INK)
    c.circle(0.5, 0.42, 0.08, WHITE)
    c.circle(0.5, 0.58, 0.08, INK)
    c.circle(0.5, 0.42, 0.025, INK)
    c.circle(0.5, 0.58, 0.025, WHITE)


def i_pertapa(c):
    c.circle(0.74, 0.24, 0.12, grad(GOLD_L, rgb("F0A040")))
    c.poly([(0.0, 0.92), (0.30, 0.38), (0.48, 0.62), (0.66, 0.30), (1.0, 0.92)], grad(rgb("9FD7A8"), rgb("3E6E50")))
    c.poly([(0.26, 0.45), (0.30, 0.38), (0.35, 0.47)], rgb("F4F8F4"))
    c.poly([(0.61, 0.38), (0.66, 0.30), (0.71, 0.40)], rgb("F4F8F4"))
    # the seated hermit
    c.ellipse(0.5, 0.86, 0.20, 0.06, rgb("5A3A22"))
    c.poly([(0.36, 0.86), (0.5, 0.60), (0.64, 0.86)], grad(rgb("E8E4D8"), rgb("A8A090")))
    c.circle(0.5, 0.56, 0.06, SKIN)
    c.ring(0.5, 0.60, 0.13, 0.155, rgb("FFF4C0", 160))


def i_petualang(c):
    c.path([(0.15, 0.95), (0.35, 0.80), (0.62, 0.86), (0.85, 0.70)], 0.06, rgb("C9A46A"))
    for (x, y) in ((0.22, 0.90), (0.45, 0.81), (0.70, 0.82)):
        c.circle(x, y, 0.02, WOOD_D)
    c.ellipse(0.5, 0.52, 0.44, 0.10, grad(rgb("F0D890"), rgb("B8954A")))
    c.poly([(0.20, 0.52), (0.5, 0.18), (0.80, 0.52)], grad(rgb("F8E4A0"), rgb("C9A050")))
    for k in range(5):
        y = 0.26 + k * 0.05
        c.line(0.5 - (y - 0.18) * 0.85, y, 0.5 + (y - 0.18) * 0.85, y, 0.012, rgb("9A7838", 180))
    c.rect(0.25, 0.47, 0.75, 0.51, SEAL)
    c.path([(0.73, 0.50), (0.84, 0.64)], 0.03, SEAL)


def i_pemburu(c):
    c.ring(0.62, 0.5, 0.36, 0.42, grad(WOOD, WOOD_D), math.pi * 0.62, math.pi * 1.38)
    c.line(0.33, 0.22, 0.33, 0.78, 0.015, rgb("E8E0D0"))
    c.line(0.12, 0.5, 0.86, 0.5, 0.035, WOOD_D)
    c.poly([(0.86, 0.43), (0.97, 0.5), (0.86, 0.57)], STEEL)
    c.poly([(0.10, 0.44), (0.18, 0.5), (0.10, 0.56), (0.06, 0.5)], rgb("E05555"))
    for k in range(3):
        c.path([(0.55 + k * 0.08, 0.72), (0.60 + k * 0.08, 0.92)], 0.04, rgb("B5764A"))


def i_pedagang(c):
    c.circle(0.42, 0.44, 0.32, radial(0.36, 0.36, 0.38, GOLD_L, GOLD_D))
    c.ring(0.42, 0.44, 0.26, 0.29, GOLD_D)
    c.rect(0.36, 0.38, 0.48, 0.50, rgb("2A1A0A"))
    c.ring(0.42, 0.44, 0.08, 0.10, GOLD_D)
    c.rrect(0.40, 0.70, 0.98, 0.94, 0.04, grad(WOOD, WOOD_D))
    for k in range(5):
        x = 0.47 + k * 0.11
        c.line(x, 0.72, x, 0.92, 0.012, rgb("3A2010"))
        c.circle(x, 0.78 if k % 2 else 0.86, 0.035, rgb("C02020"))


def i_cendekia(c):
    c.rrect(0.10, 0.25, 0.86, 0.80, 0.02, grad(PAPER_W, rgb("D8CCA8")))
    c.rect(0.06, 0.22, 0.14, 0.83, grad(WOOD, WOOD_D, False))
    c.rect(0.82, 0.22, 0.90, 0.83, grad(WOOD, WOOD_D, False))
    for k in range(6):
        x = 0.72 - k * 0.10
        c.line(x, 0.32, x, 0.70 - (k % 3) * 0.06, 0.025, INK)
    c.rect(0.20, 0.33, 0.27, 0.40, SEAL)
    c.line(0.55, 0.92, 0.95, 0.20, 0.05, grad(rgb("C8A060"), WOOD_D))
    c.poly([(0.93, 0.18), (0.99, 0.06), (0.97, 0.24)], INK)


def i_penjaga(c):
    c.poly([(0.5, 0.08), (0.88, 0.20), (0.82, 0.62), (0.5, 0.94), (0.18, 0.62), (0.12, 0.20)], grad(STEEL, STEEL_D))
    c.poly([(0.5, 0.15), (0.80, 0.25), (0.75, 0.60), (0.5, 0.86), (0.25, 0.60), (0.20, 0.25)], grad(rgb("8C9BAD"), rgb("4A5868")))
    c.line(0.5, 0.18, 0.5, 0.84, 0.05, GOLD)
    c.line(0.24, 0.42, 0.76, 0.42, 0.05, GOLD)
    c.circle(0.5, 0.42, 0.10, GOLD)
    c.circle(0.5, 0.42, 0.06, SEAL)


def i_iblis(c):
    c.poly([(0.5, 0.04), (0.66, 0.30), (0.86, 0.22), (0.80, 0.56), (0.92, 0.70), (0.5, 0.96), (0.08, 0.70), (0.20, 0.56), (0.14, 0.22),
            (0.34, 0.30)], radial(0.5, 0.65, 0.6, rgb("E0A0FF"), VIOLET_D))
    c.poly([(0.5, 0.28), (0.62, 0.50), (0.5, 0.86), (0.38, 0.50)], rgb("C890F0", 190))
    c.ellipse(0.5, 0.62, 0.17, 0.08, rgb("200A30"))
    c.ellipse(0.5, 0.62, 0.05, 0.08, rgb("FF3030"))
    c.ellipse(0.5, 0.62, 0.02, 0.05, rgb("FFE0A0"))


def i_figur(c, cx, cy, s, robe, head=SKIN):
    c.poly([(cx - 0.18 * s, cy + 0.30 * s), (cx, cy - 0.05 * s), (cx + 0.18 * s, cy + 0.30 * s)], robe)
    c.circle(cx, cy - 0.12 * s, 0.09 * s, head)
    c.rect(cx - 0.09 * s, cy - 0.24 * s, cx + 0.09 * s, cy - 0.19 * s, INK)


def i_meditasi(c):
    c.ring(0.5, 0.55, 0.36, 0.43, rgb("47B2FF", 200))
    c.ellipse(0.5, 0.82, 0.30, 0.08, grad(rgb("7A8899"), rgb("4A5868")))
    i_figur(c, 0.5, 0.55, 1.4, grad(rgb("E8E4D8"), rgb("8090A0")))


def i_pintu_segel(c):
    c.rect(0.16, 0.10, 0.84, 0.94, grad(WOOD, WOOD_D))
    c.line(0.5, 0.10, 0.5, 0.94, 0.03, WOOD_D)
    for y in (0.25, 0.75):
        c.line(0.20, y, 0.80, y, 0.03, GOLD_D)
    c.rect(0.40, 0.30, 0.60, 0.80, PAPER)
    c.line(0.5, 0.35, 0.5, 0.74, 0.04, SEAL)
    c.ring(0.5, 0.45, 0.05, 0.075, SEAL)


def i_tanding(c):
    c.line(0.12, 0.88, 0.88, 0.12, 0.10, grad(rgb("F2F6FA"), STEEL_D, False))
    c.line(0.88, 0.88, 0.12, 0.12, 0.10, grad(STEEL_D, rgb("F2F6FA"), False))
    c.circle(0.5, 0.5, 0.12, rgb("E07A30"))
    c.star(0.5, 0.5, 0.06, 0.16, 6, rgb("FFE0A0"))


def i_buku(c, col=rgb("9C7BD6")):
    c.poly([(0.08, 0.20), (0.5, 0.28), (0.5, 0.90), (0.08, 0.82)], grad(PAPER_W, rgb("D0C4A0")))
    c.poly([(0.92, 0.20), (0.5, 0.28), (0.5, 0.90), (0.92, 0.82)], grad(rgb("FFF8E8"), rgb("D8CCA8")))
    for k in range(4):
        y = 0.38 + k * 0.10
        c.line(0.16, y, 0.42, y + 0.04, 0.02, rgb("6A6050"))
        c.line(0.58, y + 0.04, 0.84, y, 0.02, rgb("6A6050"))
    c.line(0.5, 0.28, 0.5, 0.90, 0.03, col)


def i_mengajar(c):
    i_buku(c, rgb("B79BE8"))
    c.line(0.70, 0.95, 0.98, 0.08, 0.05, WOOD_D)
    c.circle(0.98, 0.08, 0.04, GOLD)


def i_jimat(c):
    c.rect(0.28, 0.06, 0.72, 0.94, grad(rgb("F4DC88"), rgb("D8B850")))
    c.rect(0.28, 0.06, 0.72, 0.10, SEAL)
    c.rect(0.28, 0.90, 0.72, 0.94, SEAL)
    c.ring(0.5, 0.26, 0.08, 0.11, SEAL)
    c.path([(0.42, 0.42), (0.58, 0.46), (0.42, 0.55), (0.58, 0.60), (0.44, 0.72), (0.56, 0.84)], 0.04, SEAL)


def i_tunas(c):
    c.ellipse(0.5, 0.88, 0.34, 0.08, rgb("6E4A2A"))
    c.path([(0.5, 0.88), (0.5, 0.40)], 0.06, rgb("3E8A3A"))
    c.ellipse(0.32, 0.46, 0.18, 0.09, grad(rgb("9FE080"), rgb("3E8A3A")), rot=-0.5)
    c.ellipse(0.68, 0.36, 0.20, 0.10, grad(rgb("9FE080"), rgb("3E8A3A")), rot=0.5)


def i_beliung(c):
    c.line(0.20, 0.90, 0.72, 0.26, 0.08, grad(WOOD, WOOD_D))
    c.poly([(0.40, 0.10), (0.92, 0.42), (0.86, 0.50), (0.60, 0.30), (0.36, 0.18)], grad(STEEL, STEEL_D))


def i_bendera(c, col=rgb("7FA08C")):
    c.line(0.25, 0.08, 0.25, 0.95, 0.06, WOOD_D)
    c.poly([(0.28, 0.12), (0.88, 0.20), (0.76, 0.34), (0.88, 0.48), (0.28, 0.46)], col)
    c.circle(0.25, 0.08, 0.05, GOLD)


def i_formasi(c):
    c.ring(0.5, 0.5, 0.34, 0.42, rgb("6FB8D8"))
    c.ring(0.5, 0.5, 0.18, 0.22, rgb("6FB8D8"))
    for i in range(8):
        a = i * math.pi / 4
        c.line(0.5 + 0.22 * math.cos(a), 0.5 + 0.22 * math.sin(a), 0.5 + 0.34 * math.cos(a), 0.5 + 0.34 * math.sin(a), 0.03, rgb("B8E8F8"))
    c.circle(0.5, 0.5, 0.08, rgb("E8F8FF"))


def i_gerbang(c):
    c.rect(0.14, 0.30, 0.24, 0.95, grad(SEAL, SEAL_D, False))
    c.rect(0.76, 0.30, 0.86, 0.95, grad(SEAL, SEAL_D, False))
    c.poly([(0.02, 0.18), (0.98, 0.18), (0.92, 0.28), (0.08, 0.28)], grad(rgb("4A4A50"), rgb("202024")))
    c.rect(0.12, 0.36, 0.88, 0.42, SEAL)
    c.rect(0.40, 0.29, 0.60, 0.36, GOLD)


def i_dua_kepala(c):
    i_figur(c, 0.36, 0.55, 1.5, rgb("F29BC1"))
    i_figur(c, 0.66, 0.58, 1.4, rgb("6FB8D8"))


def i_mangkuk(c):
    for x in (0.40, 0.58):
        c.path([(x, 0.36), (x - 0.03, 0.24), (x + 0.02, 0.12)], 0.035, rgb("D8D4CC", 180))
    c.line(0.55, 0.10, 0.92, 0.50, 0.035, WOOD_D)
    c.line(0.62, 0.08, 0.96, 0.46, 0.035, WOOD)
    c.ellipse(0.5, 0.48, 0.36, 0.08, rgb("F8F4EC"))
    c.fill(lambda u, v: v >= 0.48 and ((u - 0.5) / 0.36) ** 2 + ((v - 0.48) / 0.34) ** 2 <= 1, (0.14, 0.48, 0.86, 0.82),
           grad(rgb("F4F4F4"), rgb("A8B8C8")))
    c.rect(0.20, 0.58, 0.80, 0.61, rgb("3A6AB0"))
    c.ellipse(0.5, 0.86, 0.14, 0.04, rgb("8898A8"))


def i_bulan(c, z=True):
    c.circle(0.42, 0.46, 0.30, grad(rgb("FFF4C0"), rgb("E8C860")))
    c.circle(0.56, 0.38, 0.27, ERASE)
    if z:
        zz(c, 0.66, 0.62, 0.16)
        zz(c, 0.82, 0.40, 0.11)


def zz(c, x, y, s):
    c.path([(x - s / 2, y - s / 2), (x + s / 2, y - s / 2), (x - s / 2, y + s / 2), (x + s / 2, y + s / 2)], s * 0.28, rgb("6A7AA0"))


def i_awan_pikir(c):
    for (x, y, r) in ((0.30, 0.55, 0.20), (0.50, 0.42, 0.25), (0.70, 0.55, 0.20), (0.50, 0.62, 0.20)):
        c.circle(x, y, r, grad(WHITE, rgb("D8E0E8")))
    c.circle(0.25, 0.86, 0.06, WHITE)
    c.circle(0.14, 0.95, 0.035, WHITE)


def i_batu_roh(c, cols=(rgb("5FE0A0"), rgb("5FA8E0"), rgb("E05F5F"), rgb("E0C05F"))):
    spots = ((0.30, 0.66, 0.20), (0.70, 0.64, 0.18), (0.50, 0.36, 0.20), (0.50, 0.80, 0.14))
    for (x, y, r), col in zip(spots, cols):
        pts = [(x + r * math.cos(a), y + r * 1.1 * math.sin(a)) for a in [i * math.pi / 3 + 0.2 for i in range(6)]]
        c.poly(pts, radial(x - r * 0.3, y - r * 0.4, r * 1.6, mix(col, WHITE, 0.55), shade(col, 0.55)))
        c.line(x - r * 0.4, y - r * 0.5, x - r * 0.05, y - r * 0.15, r * 0.25, (255, 255, 255, 150))


def i_perisai_petir(c):
    i_penjaga(c)
    c.poly([(0.56, 0.20), (0.36, 0.54), (0.50, 0.54), (0.42, 0.86), (0.66, 0.46), (0.52, 0.46)], GOLD_L)


def i_petir(c):
    c.circle(0.5, 0.5, 0.40, radial(0.5, 0.5, 0.42, rgb("FFF4C0", 160), rgb("FFD84A", 0)))
    c.poly([(0.60, 0.04), (0.28, 0.54), (0.48, 0.54), (0.36, 0.96), (0.74, 0.40), (0.54, 0.40)], grad(rgb("FFFBE0"), rgb("FFC820")))


def i_buntalan(c):
    c.line(0.10, 0.20, 0.80, 0.70, 0.06, WOOD)
    c.circle(0.62, 0.66, 0.26, radial(0.55, 0.58, 0.32, rgb("6FA0D0"), rgb("2A4A78")))
    c.poly([(0.52, 0.38), (0.62, 0.30), (0.72, 0.38), (0.62, 0.46)], rgb("2A4A78"))
    for k in range(4):
        c.circle(0.50 + (k % 2) * 0.22, 0.60 + (k // 2) * 0.14, 0.03, rgb("C8D8F0"))


def i_pedang_terbang(c):
    c.path([(0.04, 0.80), (0.18, 0.70)], 0.04, rgb("B4E2FF", 140))
    c.path([(0.04, 0.62), (0.16, 0.58)], 0.03, rgb("B4E2FF", 120))
    c.line(0.20, 0.70, 0.90, 0.40, 0.10, grad(rgb("F2F6FA"), QI, False))
    c.line(0.22, 0.69, 0.88, 0.41, 0.03, (255, 255, 255, 220))
    c.line(0.22, 0.60, 0.26, 0.80, 0.06, GOLD)
    c.line(0.08, 0.76, 0.22, 0.70, 0.06, WOOD_D)


def i_rumah(c):
    c.rect(0.18, 0.46, 0.82, 0.92, grad(rgb("F0E6D0"), rgb("C8B898")))
    c.poly([(0.02, 0.50), (0.5, 0.12), (0.98, 0.50), (0.86, 0.50), (0.5, 0.24), (0.14, 0.50)], grad(rgb("3E6E50"), rgb("1E3E2A")))
    c.poly([(0.14, 0.50), (0.5, 0.24), (0.86, 0.50)], rgb("2E5040"))
    c.rect(0.42, 0.62, 0.58, 0.92, WOOD_D)
    c.circle(0.5, 0.52, 0.05, rgb("FFD060"))


def i_jejak(c):
    c.path([(0.10, 0.90), (0.40, 0.70), (0.30, 0.45), (0.65, 0.30), (0.90, 0.10)], 0.07, rgb("DDB548", 180))
    for (x, y) in ((0.22, 0.80), (0.38, 0.58), (0.50, 0.38), (0.78, 0.20)):
        c.ellipse(x, y, 0.05, 0.07, WOOD_D)


def i_layang(c):
    c.poly([(0.55, 0.06), (0.92, 0.38), (0.55, 0.70), (0.18, 0.38)], grad(rgb("F5B4D0"), rgb("D05080")))
    c.line(0.55, 0.06, 0.55, 0.70, 0.02, WOOD_D)
    c.line(0.18, 0.38, 0.92, 0.38, 0.02, WOOD_D)
    c.path([(0.55, 0.70), (0.45, 0.80), (0.55, 0.88), (0.40, 0.98)], 0.03, SEAL)


def i_marah(c):
    c.rrect(0.08, 0.14, 0.92, 0.70, 0.12, grad(rgb("F8E0D8"), rgb("E8B8A8")))
    c.poly([(0.30, 0.68), (0.20, 0.92), (0.46, 0.68)], rgb("E8B8A8"))
    c.path([(0.30, 0.30), (0.40, 0.48), (0.50, 0.30), (0.60, 0.48), (0.70, 0.30)], 0.06, rgb("B03030"))


def i_kepalan(c):
    c.rrect(0.22, 0.30, 0.78, 0.80, 0.12, grad(SKIN, rgb("B88A60")))
    for k in range(4):
        c.line(0.30 + k * 0.13, 0.30, 0.30 + k * 0.13, 0.48, 0.10, grad(rgb("F0D0A8"), SKIN))
    c.line(0.22, 0.56, 0.44, 0.50, 0.10, rgb("D8B080"))
    c.rect(0.24, 0.78, 0.76, 0.94, rgb("D04A2A"))
    c.star(0.82, 0.22, 0.04, 0.12, 5, rgb("FFD060"))


def i_perban(c):
    c.rrect(0.10, 0.36, 0.90, 0.64, 0.06, rgb("F8F4EC"), )
    c.line(0.30, 0.10, 0.70, 0.90, 0.26, rgb("F8F4EC"))
    c.rect(0.38, 0.40, 0.62, 0.60, rgb("E05555"))
    c.rect(0.46, 0.32, 0.54, 0.68, rgb("E05555"))


def i_sumpah(c):
    c.rect(0.30, 0.70, 0.70, 0.92, grad(BRONZE, BRONZE_D))
    c.ellipse(0.5, 0.70, 0.22, 0.05, BRONZE_D)
    for x in (0.40, 0.5, 0.60):
        c.line(x, 0.70, x, 0.30, 0.03, rgb("C03070"))
        c.circle(x, 0.29, 0.025, rgb("FF8040"))
        c.path([(x, 0.25), (x - 0.03, 0.15), (x + 0.02, 0.05)], 0.025, rgb("D8D4CC", 170))


def i_kosong(c):
    for i in range(12):
        a = i * math.pi / 6
        c.circle(0.5 + 0.32 * math.cos(a), 0.5 + 0.32 * math.sin(a), 0.045, rgb("7A8C83"))


def i_qi(c):
    for k in range(3):
        a0 = k * 2 * math.pi / 3
        pts = [(0.5 + (0.08 + t * 0.34) * math.cos(a0 + t * 3.2), 0.5 + (0.08 + t * 0.34) * math.sin(a0 + t * 3.2)) for t in
               [i / 10 for i in range(11)]]
        c.path(pts, 0.07, mix(QI, QI_L, k / 3))
    c.circle(0.5, 0.5, 0.09, WHITE)


def i_teh(c):
    for x in (0.40, 0.56):
        c.path([(x, 0.38), (x - 0.04, 0.24), (x + 0.03, 0.10)], 0.04, rgb("D8D4CC", 190))
    c.ellipse(0.5, 0.48, 0.30, 0.06, rgb("6A8A40"))
    c.fill(lambda u, v: v >= 0.46 and ((u - 0.5) / 0.30) ** 2 + ((v - 0.46) / 0.36) ** 2 <= 1, (0.2, 0.46, 0.8, 0.82),
           grad(rgb("D8F0E0"), rgb("7EB8A0")))
    c.ellipse(0.5, 0.86, 0.36, 0.06, grad(rgb("D8F0E0"), rgb("7EB8A0")))


def i_gulungan(c):
    c.rect(0.16, 0.26, 0.84, 0.74, grad(PAPER_W, rgb("D8CCA8")))
    c.rrect(0.08, 0.18, 0.20, 0.82, 0.05, grad(WOOD, WOOD_D, False))
    c.rrect(0.80, 0.18, 0.92, 0.82, 0.05, grad(WOOD, WOOD_D, False))
    for k in range(4):
        c.line(0.30, 0.36 + k * 0.10, 0.70 - (k % 2) * 0.12, 0.36 + k * 0.10, 0.03, INK)
    c.rect(0.60, 0.60, 0.70, 0.70, SEAL)


def i_kota(c):
    c.rect(0.04, 0.40, 0.96, 0.95, grad(rgb("B8B0A0"), rgb("7A7468")))
    for k in range(6):
        c.rect(0.06 + k * 0.16, 0.32, 0.14 + k * 0.16, 0.42, rgb("8A8478"))
    c.fill(lambda u, v: v >= 0.62 and ((u - 0.5) / 0.16) ** 2 + ((v - 0.62) / 0.16) ** 2 <= 1 or (0.34 <= u <= 0.66 and v >= 0.62),
           (0.34, 0.46, 0.66, 0.95), rgb("2A2018"))
    c.poly([(0.30, 0.30), (0.5, 0.06), (0.70, 0.30)], grad(SEAL, SEAL_D))
    c.rect(0.36, 0.30, 0.64, 0.40, rgb("D8C8A0"))


def i_hati(c):
    c.circle(0.36, 0.38, 0.20, grad(rgb("FF9AB0"), rgb("D0305A")))
    c.circle(0.64, 0.38, 0.20, grad(rgb("FF9AB0"), rgb("D0305A")))
    c.poly([(0.17, 0.44), (0.5, 0.90), (0.83, 0.44), (0.5, 0.40)], grad(rgb("F07090"), rgb("B02048")))
    c.ellipse(0.30, 0.32, 0.06, 0.04, (255, 255, 255, 180), rot=-0.6)


def i_bintang(c):
    c.star(0.5, 0.52, 0.18, 0.44, 5, radial(0.5, 0.45, 0.5, rgb("FFFBE0"), rgb("F0B020")))
    c.circle(0.5, 0.52, 0.08, (255, 255, 255, 140))


def i_kertas_segel(c):
    c.poly([(0.30, 0.06), (0.76, 0.12), (0.70, 0.94), (0.24, 0.88)], grad(rgb("F4DC88"), rgb("D8B850")))
    c.ring(0.50, 0.30, 0.08, 0.115, SEAL)
    c.path([(0.44, 0.46), (0.58, 0.50), (0.42, 0.62), (0.56, 0.70)], 0.045, SEAL)
    c.circle(0.62, 0.80, 0.07, (255, 230, 120, 120))


def i_iblis_wisp(c):
    c.poly([(0.5, 0.06), (0.70, 0.36), (0.78, 0.64), (0.5, 0.94), (0.22, 0.64), (0.30, 0.36)], radial(0.5, 0.6, 0.5, rgb("E0B0FF"), VIOLET_D))
    c.ellipse(0.42, 0.58, 0.05, 0.07, rgb("FF4040"))
    c.ellipse(0.58, 0.58, 0.05, 0.07, rgb("FF4040"))
    c.path([(0.40, 0.76), (0.5, 0.72), (0.60, 0.76)], 0.03, rgb("200A30"))


def i_awan(c, tint=WHITE):
    base = mix(WHITE, tint, 1.0)
    low = mix(rgb("C8D0DC"), tint, 0.45)
    for (x, y, r) in ((0.26, 0.58, 0.20), (0.46, 0.44, 0.26), (0.68, 0.48, 0.22), (0.80, 0.62, 0.16), (0.50, 0.66, 0.22)):
        c.circle(x, y, r, radial(x - r * 0.3, y - r * 0.5, r * 1.6, base, low))


def i_kantong(c):
    c.ellipse(0.5, 0.64, 0.34, 0.30, radial(0.42, 0.55, 0.42, rgb("C08A50"), rgb("6A4020")))
    c.poly([(0.36, 0.38), (0.5, 0.30), (0.64, 0.38), (0.58, 0.44), (0.42, 0.44)], rgb("6A4020"))
    c.line(0.34, 0.40, 0.66, 0.40, 0.05, GOLD)
    c.path([(0.62, 0.40), (0.74, 0.30), (0.80, 0.40)], 0.03, GOLD)
    c.circle(0.5, 0.68, 0.10, radial(0.47, 0.65, 0.12, GOLD_L, GOLD_D))
    c.rect(0.475, 0.655, 0.525, 0.705, rgb("3A2010"))


def i_surat(c):
    c.rect(0.10, 0.26, 0.90, 0.80, grad(PAPER_W, rgb("D8CCA8")))
    c.path([(0.10, 0.26), (0.5, 0.58), (0.90, 0.26)], 0.04, rgb("A89878"))
    c.circle(0.5, 0.58, 0.09, SEAL)


def i_rantai(c):
    for k in range(4):
        x = 0.18 + k * 0.21
        c.ring(x, 0.5 + (0.08 if k % 2 else -0.08), 0.07, 0.12, grad(STEEL, STEEL_D))
    c.line(0.10, 0.85, 0.90, 0.85, 0.04, rgb("A03030"))


def i_koin(c):
    for k, (x, y) in enumerate(((0.36, 0.66), (0.62, 0.56), (0.48, 0.36))):
        c.circle(x, y, 0.22, radial(x - 0.06, y - 0.06, 0.26, GOLD_L, GOLD_D))
        c.rect(x - 0.05, y - 0.05, x + 0.05, y + 0.05, rgb("3A2010"))


def i_gulungan_cahaya(c):
    c.circle(0.5, 0.5, 0.46, radial(0.5, 0.5, 0.48, rgb("FFF4C0", 200), rgb("FFD84A", 0)))
    i_gulungan(c)


def i_cabang(c):
    c.path([(0.5, 0.95), (0.5, 0.55), (0.18, 0.15)], 0.08, rgb("C9A46A"))
    c.path([(0.5, 0.55), (0.82, 0.15)], 0.08, rgb("E8C65A"))
    c.poly([(0.74, 0.10), (0.92, 0.06), (0.88, 0.24)], rgb("E8C65A"))


def i_pedang_merah(c):
    c.line(0.12, 0.88, 0.88, 0.12, 0.10, grad(rgb("FFD0C8"), rgb("B03030"), False))
    c.line(0.88, 0.88, 0.12, 0.12, 0.10, grad(rgb("B03030"), rgb("FFD0C8"), False))
    c.circle(0.5, 0.5, 0.09, rgb("FFE0A0"))


def i_api_ungu(c):
    i_iblis(c)


def i_wajah(c, base, mood):
    c.circle(0.5, 0.52, 0.42, radial(0.42, 0.42, 0.5, mix(base, WHITE, 0.45), base))
    e = INK
    if mood == "senang":
        c.ring(0.34, 0.48, 0.05, 0.085, e, math.pi, 2 * math.pi)
        c.ring(0.66, 0.48, 0.05, 0.085, e, math.pi, 2 * math.pi)
        c.ring(0.5, 0.58, 0.14, 0.19, e, 0.15, math.pi - 0.15)
    elif mood == "tenang":
        c.line(0.26, 0.48, 0.42, 0.48, 0.06, e)
        c.line(0.58, 0.48, 0.74, 0.48, 0.06, e)
        c.ring(0.5, 0.62, 0.08, 0.12, e, 0.4, math.pi - 0.4)
    elif mood == "lelah":
        c.line(0.26, 0.50, 0.42, 0.46, 0.06, e)
        c.line(0.58, 0.46, 0.74, 0.50, 0.06, e)
        c.ellipse(0.5, 0.72, 0.07, 0.05, e)
        c.circle(0.80, 0.30, 0.06, rgb("6FB8D8"))
    elif mood == "kesal":
        c.line(0.24, 0.36, 0.42, 0.44, 0.06, e)
        c.line(0.58, 0.44, 0.76, 0.36, 0.06, e)
        c.circle(0.34, 0.52, 0.05, e)
        c.circle(0.66, 0.52, 0.05, e)
        c.line(0.38, 0.74, 0.62, 0.74, 0.06, e)
    elif mood == "sedih":
        c.circle(0.34, 0.50, 0.05, e)
        c.circle(0.66, 0.50, 0.05, e)
        c.ring(0.5, 0.82, 0.12, 0.17, e, math.pi + 0.4, 2 * math.pi - 0.4)
        c.ellipse(0.30, 0.66, 0.04, 0.07, rgb("6FB8D8"))
    elif mood == "bangga":
        c.ring(0.34, 0.50, 0.05, 0.085, e, math.pi, 2 * math.pi)
        c.ring(0.66, 0.50, 0.05, 0.085, e, math.pi, 2 * math.pi)
        c.ring(0.5, 0.60, 0.12, 0.17, e, 0.2, math.pi - 0.2)
        c.star(0.82, 0.18, 0.04, 0.12, 5, GOLD_L)
    elif mood == "tergoda":
        c.ellipse(0.34, 0.50, 0.07, 0.05, rgb("FF3030"))
        c.ellipse(0.66, 0.50, 0.07, 0.05, rgb("FF3030"))
        c.path([(0.36, 0.72), (0.5, 0.66), (0.64, 0.72)], 0.05, e)
        c.poly([(0.18, 0.22), (0.26, 0.04), (0.32, 0.20)], VIOLET_D)
        c.poly([(0.68, 0.20), (0.74, 0.04), (0.82, 0.22)], VIOLET_D)


def i_menguap(c):
    i_awan(c, rgb("B0A27A"))
    zz(c, 0.5, 0.52, 0.22)


def i_tetes(c):
    c.poly([(0.5, 0.08), (0.74, 0.54), (0.5, 0.90), (0.26, 0.54)], grad(rgb("F0B08A"), rgb("C0603A")))
    c.circle(0.5, 0.62, 0.24, grad(rgb("F0B08A"), rgb("C0603A")))
    c.ellipse(0.42, 0.56, 0.05, 0.08, (255, 255, 255, 160))


def i_puncak(c):
    c.poly([(0.04, 0.94), (0.5, 0.20), (0.96, 0.94)], grad(rgb("E8C65A"), rgb("8A671B")))
    c.poly([(0.38, 0.40), (0.5, 0.20), (0.62, 0.40), (0.5, 0.34)], WHITE)
    c.line(0.5, 0.20, 0.5, 0.04, 0.04, WOOD_D)
    c.poly([(0.52, 0.04), (0.80, 0.10), (0.52, 0.16)], SEAL)


def i_tas(c):
    c.rrect(0.16, 0.30, 0.84, 0.92, 0.10, grad(rgb("9FD7A8"), rgb("3E6E50")))
    c.ring(0.5, 0.30, 0.14, 0.20, rgb("3E6E50"), math.pi, 2 * math.pi)
    c.rect(0.30, 0.50, 0.70, 0.58, rgb("2A4A30"))
    c.circle(0.5, 0.54, 0.05, GOLD)


# ---------------------------------------------------------------------------------------------------- atlases

JALAN = [i_pedang, i_kuali, i_bagua, i_pertapa, i_petualang, i_pemburu, i_pedagang, i_cendekia, i_penjaga, i_iblis]

KEGIATAN = [i_kosong, i_meditasi, i_pintu_segel, i_pedang, i_tanding, i_buku, i_mengajar, i_kuali, i_jimat, i_tunas, i_beliung,
            lambda c: i_bendera(c), i_formasi, i_gerbang, i_dua_kepala, i_mangkuk, lambda c: i_bulan(c, True), i_awan_pikir, i_batu_roh,
            i_perisai_petir, i_petir, i_buntalan, i_pedang_terbang, i_rumah, i_jejak, i_layang, i_marah, i_kepalan, i_perban, i_sumpah]

SUASANA = [("senang", rgb("E8A23A")), ("tenang", rgb("3E8F7A")), ("lelah", rgb("7A8899")), ("kesal", rgb("C0502E")),
           ("sedih", rgb("4F6FB0")), ("bangga", rgb("C9961E")), ("tergoda", rgb("8B3FC4"))]

DORONG = [i_qi, i_menguap, i_tetes, i_dua_kepala, i_puncak, i_tas, i_perban]

PIKIR = [i_qi, i_pedang, i_kuali, i_jimat, i_batu_roh, i_teh, i_mangkuk, lambda c: zz3(c), i_gulungan, i_buntalan, i_kota,
         lambda c: i_bulan(c, False), i_iblis_wisp, i_hati, i_bintang, i_kertas_segel]

TOAST = [i_petir, i_rumah, i_perban, i_rantai, i_koin, i_gulungan_cahaya, i_cabang, i_api_ungu, i_pedang_merah, i_surat]


def zz3(c):
    zz(c, 0.30, 0.70, 0.22)
    zz(c, 0.56, 0.46, 0.17)
    zz(c, 0.78, 0.24, 0.13)


def atlas(icons, cols, rows, cell, outline=True):
    cv = Canvas(cols * cell, rows * cell)
    for i, fn in enumerate(icons):
        fn(cv.at((i % cols) * cell, (i // cols) * cell, cell))
    return cv.image(outline)


def pikir_atlas():
    cell = 32
    cv = Canvas(8 * cell, 3 * cell)
    for i, fn in enumerate(PIKIR):
        fn(cv.at((i % 8) * cell, (i // 8) * cell, cell))
    img = cv.image(True)
    # the clouds are drawn with a soft grey rim instead of the ink outline (they sit behind the icons in the world)
    cv2 = Canvas(8 * cell, 3 * cell)
    i_awan(cv2.at(0, 2 * cell, cell))
    i_awan(cv2.at(cell, 2 * cell, cell), rgb("C8A0F0"))
    clouds = cv2.image(False)
    ink(clouds, (90, 100, 115), 0.7)
    for y in range(2 * cell, 3 * cell):
        for x in range(0, 2 * cell):
            img.set(x, y, clouds.get(x, y))
    return img


def suasana_atlas():
    cv = Canvas(128, 16)
    for i, (mood, col) in enumerate(SUASANA):
        i_wajah(cv.at(i * 16, 0, 16), col, mood)
    return cv.image(True)


# ---------------------------------------------------------------------------------------------------- paper and ornaments


def periodic_noise(seed, size, cell):
    rnd = random.Random(seed)
    n = size // cell
    grid = [[rnd.random() for _ in range(n)] for _ in range(n)]

    def at(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx) % n, int(gy) % n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        fx, fy = gx - int(gx), gy - int(gy)
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        a = grid[y0][x0] + (grid[y0][x1] - grid[y0][x0]) * fx
        b = grid[y1][x0] + (grid[y1][x1] - grid[y1][x0]) * fx
        return a + (b - a) * fy
    return at


def kertas():
    size = 64
    img = Image(size, size)
    n1 = periodic_noise(3901, size, 16)
    n2 = periodic_noise(3902, size, 8)
    n3 = periodic_noise(3903, size, 4)
    rnd = random.Random(3904)
    base = rgb("EADBB4")
    for y in range(size):
        for x in range(size):
            v = 0.55 * n1(x, y) + 0.3 * n2(x, y) + 0.15 * n3(x, y)
            c = mix(rgb("F4E8C8"), rgb("D6C192"), v)
            img.set(x, y, c)
    # fibres (wrap around)
    for _ in range(46):
        x, y = rnd.random() * size, rnd.random() * size
        a = rnd.random() * math.pi
        ln = 3 + rnd.random() * 7
        for t in range(int(ln * 2)):
            px = int(x + math.cos(a) * t * 0.5) % size
            py = int(y + math.sin(a) * t * 0.5) % size
            img.blend(px, py, (150, 120, 70, 34))
    # a few foxing spots
    for _ in range(5):
        cx, cy, r = rnd.random() * size, rnd.random() * size, 1.5 + rnd.random() * 2.5
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    img.blend(x % size, y % size, (160, 110, 50, int(40 * (1 - d / r))))
    return img


def hiasan():
    cv = Canvas(128, 64)
    # ribbon 12 x 44 at (0, 0): drawn in a 44 px cell, only the left 12 px used
    c = cv.at(0, 0, 44)
    rib = grad(rgb("D0402E"), rgb("8A1A10"), False, 0.0, 0.27)
    c.rect(0.02, 0.0, 0.25, 0.88, rib)
    c.poly([(0.02, 0.88), (0.135, 0.80), (0.25, 0.88), (0.25, 1.0), (0.135, 0.92), (0.02, 1.0)], rib)
    c.line(0.05, 0.0, 0.05, 0.86, 0.012, rgb("F08070", 160))
    c.rect(0.02, 0.10, 0.25, 0.13, GOLD)
    # seal 32 x 32 at (16, 0)
    c = cv.at(16, 0, 32)
    c.rrect(0.06, 0.06, 0.94, 0.94, 0.10, grad(rgb("D04232"), rgb("8A1A10")))
    w = rgb("F6E4D4")
    c.fill(lambda u, v: 0.12 <= u <= 0.88 and 0.12 <= v <= 0.88 and not (0.155 <= u <= 0.845 and 0.155 <= v <= 0.845),
           (0.1, 0.1, 0.9, 0.9), w)
    # a seal-script "path" glyph (dao): the walking radical on the left and below, the head on the right
    c.path([(0.26, 0.26), (0.36, 0.34)], 0.06, w)
    c.path([(0.22, 0.46), (0.34, 0.46), (0.34, 0.68), (0.24, 0.76), (0.82, 0.78)], 0.06, w)
    c.path([(0.46, 0.24), (0.80, 0.24)], 0.06, w)
    c.path([(0.56, 0.18), (0.60, 0.24)], 0.05, w)
    c.path([(0.70, 0.18), (0.66, 0.24)], 0.05, w)
    c.path([(0.50, 0.32), (0.50, 0.66), (0.76, 0.66), (0.76, 0.32), (0.50, 0.32)], 0.06, w)
    c.path([(0.50, 0.43), (0.76, 0.43)], 0.05, w)
    c.path([(0.50, 0.54), (0.76, 0.54)], 0.05, w)
    # corner cloud 16 x 16 at (48, 0)
    c = cv.at(48, 0, 16)
    c.path([(0.06, 0.94), (0.06, 0.06), (0.94, 0.06)], 0.10, GOLD)
    c.path([(0.20, 0.80), (0.20, 0.20), (0.80, 0.20)], 0.05, GOLD_D)
    c.ring(0.42, 0.42, 0.10, 0.17, GOLD, math.pi * 0.0, math.pi * 1.5)
    c.circle(0.42, 0.42, 0.05, GOLD_L)
    # ink stone 16 x 16 at (48, 16)
    c = cv.at(48, 16, 16)
    c.rrect(0.08, 0.20, 0.92, 0.86, 0.12, grad(rgb("4A4A50"), rgb("1A1A20")))
    c.ellipse(0.5, 0.56, 0.26, 0.18, rgb("0A0A10"))
    c.line(0.62, 0.10, 0.92, 0.40, 0.10, grad(rgb("2A2A30"), rgb("101014")))
    # scroll 32 x 32 at (64, 0), pouch 32 x 32 at (96, 0), book 16 x 16 at (0, 48)
    i_gulungan(cv.at(64, 0, 32))
    i_kantong(cv.at(96, 0, 32))
    i_buku(cv.at(0, 48, 16), SEAL)
    return cv.image(False)


def preview(images, path, factor=4):
    w = max(i.width for i in images) * factor + 8
    h = sum(i.height * factor + 8 for i in images)
    big = Image(w, h, (50, 56, 60, 255))
    y = 4
    for img in images:
        for py in range(img.height * factor):
            for px in range(img.width * factor):
                r, g, b, a = img.get(px // factor, py // factor)
                bg = (200, 190, 160) if ((px // 8 + py // 8) % 2) else (214, 204, 176)
                k = a / 255.0
                big.set(4 + px, y + py, (r * k + bg[0] * (1 - k), g * k + bg[1] * (1 - k), b * k + bg[2] * (1 - k), 255))
        y += img.height * factor + 8
    big.save(path)


def main():
    os.makedirs(OUT, exist_ok=True)
    assert len(JALAN) == 10 and len(KEGIATAN) == 30 and len(DORONG) == 7 and len(PIKIR) == 16 and len(TOAST) == 10
    out = {
        "jalan.png": atlas(JALAN, 8, 2, 32),
        "kegiatan.png": atlas(KEGIATAN, 8, 4, 16),
        "suasana.png": suasana_atlas(),
        "dorongan.png": atlas(DORONG, 8, 1, 16),
        "pikir.png": pikir_atlas(),
        "toast.png": atlas(TOAST, 10, 1, 16),
        "kertas.png": kertas(),
        "hiasan.png": hiasan(),
    }
    for name, img in out.items():
        img.save(os.path.join(OUT, name))
        print("wrote", name, img.width, "x", img.height)
    if len(sys.argv) > 1:
        os.makedirs(sys.argv[1], exist_ok=True)
        preview(list(out.values()), os.path.join(sys.argv[1], "mandiri_tekstur.png"), 4)
        print("preview", os.path.join(sys.argv[1], "mandiri_tekstur.png"))


if __name__ == "__main__":
    main()
