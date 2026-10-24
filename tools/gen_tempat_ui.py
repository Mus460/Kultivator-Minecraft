"""Generates the GUI textures of the "Pasar Giok" kit (DESIGN 38.U.2, module 38UI).

All drawn here from primitives (our own art, nothing copied), written with tools/png.py:

textures/gui/tempat/panel_<gaya>.png  32x32 nine-slice frames (corner 8 px, edges and centre tile every 16 px):
    kertas  parchment with a double gold border and gold corner knots (hub style)
    kayu    red lacquered board with brass corner brackets (shops, markets)
    batu    grey carved stone with an inset groove and iron rivets (Yamen, guilds)
    kain    plum silk with a woven diagonal and gold thread border (palace, auction)
    giok    pale jade with marbling and a deep jade rim with cloud corners (stations, temples)
textures/gui/tempat/awan.png          64x64 seamless faint cloud-scroll lines (white + alpha, tinted per style)
textures/gui/tempat/ikon.png          256x256 icon atlas, 16 px cells, slot = row * 16 + column, in exactly the order of
                                      dev.kultivator.dunia.tempat.ui.LembarKunci.Ikon (slots 112+ are 38UI additions,
                                      listed in EXTRA below and mirrored in client/tempat/ui/kit/IkonTempat.java)
textures/gui/tempat/lentera.png       128x32 lantern tablets 24x28: 0 unlit, 24 hover, 48 lit (active), 72 locked,
                                      96..128 a 32x32 soft glow
textures/gui/tempat/potret.png        64x64 portrait frame 56x56 (48x48 window at 4,4) with cloud corners
textures/gui/tempat/stempel.png       128x64 stamps: 0,0 round seal 48x48; 48,0 square seal 48x48; 96,0 padlock wax
                                      32x32; 96,32 star seal 32x32; 0,48 ribbon 48x16 grey (locked band, 3-slice ends 6 px);
                                      48,48 ribbon gold 48x16

Previews (8x / 4x) go to tools/keluaran/tempat_ui/ for inspection with Read; they are not shipped.

Run with: python tools/gen_tempat_ui.py
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "gui", "tempat")
PREVIEW = os.path.join(HERE, "keluaran", "tempat_ui")


# ===================================================================================================== helpers

def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(c0, c1, t):
    t = clamp(t)
    return tuple(int(round(c0[i] + (c1[i] - c0[i]) * t)) for i in range(4))


def shade(c, f):
    """f > 0 lightens towards white, f < 0 darkens towards black."""
    if f >= 0:
        return mix(c, (255, 255, 255, c[3]), f)
    return mix(c, (0, 0, 0, c[3]), -f)


def hnoise(x, y, seed=0, period=None):
    """Deterministic value noise in 0..1; periodic when {@code period} is given (seamless tiles)."""
    def h(ix, iy):
        if period:
            ix %= period
            iy %= period
        n = (ix * 374761393 + iy * 668265263 + seed * 2246822519) & 0xFFFFFFFF
        n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * sx
    b = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * sx
    return a + (b - a) * sy


def fbm(x, y, seed=0, period=None, octaves=3):
    total, amp, norm, f = 0.0, 1.0, 0.0, 1.0
    for o in range(octaves):
        p = None if period is None else int(period * f)
        total += hnoise(x * f, y * f, seed + o * 17, p) * amp
        norm += amp
        amp *= 0.5
        f *= 2.0
    return total / norm


class Canvas:
    """A supersampled RGBA canvas in 'unit' coordinates (one unit = one output pixel, SS samples per unit)."""

    def __init__(self, w, h, ss=4):
        self.w, self.h, self.ss = w, h, ss
        self.W, self.H = w * ss, h * ss
        self.px = [[0.0, 0.0, 0.0, 0.0] for _ in range(self.W * self.H)]

    def _blend(self, X, Y, c, cov=1.0):
        if not (0 <= X < self.W and 0 <= Y < self.H):
            return
        a = c[3] / 255.0 * cov
        if a <= 0:
            return
        d = self.px[Y * self.W + X]
        oa = a + d[3] * (1 - a)
        if oa <= 0:
            return
        for i in range(3):
            d[i] = (c[i] * a + d[i] * d[3] * (1 - a)) / oa
        d[3] = oa

    def poly(self, pts, c):
        ss = self.ss
        P = [(x * ss, y * ss) for x, y in pts]
        ys = [p[1] for p in P]
        y0, y1 = max(0, int(math.floor(min(ys)))), min(self.H - 1, int(math.ceil(max(ys))))
        n = len(P)
        for Y in range(y0, y1 + 1):
            sy = Y + 0.5
            xs = []
            for i in range(n):
                ax, ay = P[i]
                bx, by = P[(i + 1) % n]
                if (ay <= sy < by) or (by <= sy < ay):
                    xs.append(ax + (sy - ay) * (bx - ax) / (by - ay))
            xs.sort()
            for k in range(0, len(xs) - 1, 2):
                xa, xb = xs[k], xs[k + 1]
                for X in range(max(0, int(math.ceil(xa - 0.5))), min(self.W - 1, int(math.floor(xb - 0.5))) + 1):
                    self._blend(X, Y, c)

    def rect(self, x0, y0, x1, y1, c):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], c)

    def ellipse(self, cx, cy, rx, ry, c, a0=None, a1=None):
        ss = self.ss
        for Y in range(max(0, int((cy - ry) * ss)), min(self.H, int((cy + ry) * ss) + 1)):
            for X in range(max(0, int((cx - rx) * ss)), min(self.W, int((cx + rx) * ss) + 1)):
                dx = (X + 0.5) / ss - cx
                dy = (Y + 0.5) / ss - cy
                if (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1.0:
                    if a0 is not None:
                        ang = math.atan2(dy, dx)
                        if not (a0 <= ang <= a1):
                            continue
                    self._blend(X, Y, c)

    def circle(self, cx, cy, r, c):
        self.ellipse(cx, cy, r, r, c)

    def ring(self, cx, cy, r0, r1, c):
        ss = self.ss
        for Y in range(max(0, int((cy - r1) * ss)), min(self.H, int((cy + r1) * ss) + 1)):
            for X in range(max(0, int((cx - r1) * ss)), min(self.W, int((cx + r1) * ss) + 1)):
                d = math.hypot((X + 0.5) / ss - cx, (Y + 0.5) / ss - cy)
                if r0 <= d <= r1:
                    self._blend(X, Y, c)

    def line(self, x0, y0, x1, y1, wd, c, cap=True):
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy) or 1e-6
        nx, ny = -dy / L * wd / 2, dx / L * wd / 2
        self.poly([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)], c)
        if cap:
            self.circle(x0, y0, wd / 2, c)
            self.circle(x1, y1, wd / 2, c)

    def path(self, pts, wd, c):
        for i in range(len(pts) - 1):
            self.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], wd, c)

    def resolve(self):
        """Box-filters down to w x h RGBA tuples."""
        ss = self.ss
        out = []
        for y in range(self.h):
            row = []
            for x in range(self.w):
                r = g = b = a = 0.0
                for sy in range(ss):
                    for sx in range(ss):
                        p = self.px[(y * ss + sy) * self.W + x * ss + sx]
                        r += p[0] * p[3]
                        g += p[1] * p[3]
                        b += p[2] * p[3]
                        a += p[3]
                if a <= 0:
                    row.append((0, 0, 0, 0))
                else:
                    row.append((int(round(r / a)), int(round(g / a)), int(round(b / a)), int(round(a / (ss * ss) * 255))))
            out.append(row)
        return out


def paste(img, pixels, ox, oy, outline=None):
    """Pastes resolved pixels; with {@code outline} adds a 1 px dark rim around the shape (icon legibility)."""
    h, w = len(pixels), len(pixels[0])
    if outline is not None:
        for y in range(h):
            for x in range(w):
                if pixels[y][x][3] >= 90:
                    continue
                best = 0
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h:
                        best = max(best, pixels[ny][nx][3])
                if best >= 140:
                    img.blend(ox + x, oy + y, (outline[0], outline[1], outline[2], int(outline[3] * best / 255)))
    for y in range(h):
        for x in range(w):
            p = pixels[y][x]
            if p[3] > 0:
                img.blend(ox + x, oy + y, p)


def upscale(img, k):
    out = Image(img.width * k, img.height * k, (0, 0, 0, 0))
    for y in range(img.height):
        for x in range(img.width):
            p = img.get(x, y)
            for yy in range(k):
                for xx in range(k):
                    out.set(x * k + xx, y * k + yy, p)
    return out


def preview(img, name, k, bg=(40, 44, 52, 255)):
    """Writes a k-times preview over a checker so translucent pixels can be judged."""
    out = Image(img.width * k, img.height * k, (0, 0, 0, 255))
    for y in range(out.height):
        for x in range(out.width):
            cb = ((x // 8) + (y // 8)) % 2
            out.set(x, y, shade(bg, 0.06) if cb else bg)
    for y in range(img.height):
        for x in range(img.width):
            p = img.get(x, y)
            if p[3] == 0:
                continue
            for yy in range(k):
                for xx in range(k):
                    out.blend(x * k + xx, y * k + yy, p)
    os.makedirs(PREVIEW, exist_ok=True)
    out.save(os.path.join(PREVIEW, name))


# ===================================================================================================== colours

INK = hexc("1B2521")
OUTLINE = hexc("15100C", 235)
GOLD = hexc("D4A637")
GOLD_L = hexc("F7DC7E")
GOLD_D = hexc("8A671B")
JADE = hexc("2E8963")
JADE_L = hexc("67C99B")
JADE_P = hexc("B6E3CB")
JADE_D = hexc("184B36")
RED = hexc("B3281D")
RED_L = hexc("FF5B3A")
RED_D = hexc("6E160F")
IRON = hexc("8C949A")
IRON_L = hexc("D8E0E6")
IRON_D = hexc("4A5258")
WOOD = hexc("7E4A27")
WOOD_L = hexc("B07A48")
WOOD_D = hexc("45230F")
PAPER = hexc("EDE3C8")
PAPER_D = hexc("C9B48A")
WHITE = hexc("FFFFFF")
BLUE = hexc("4F86C8")
BLUE_L = hexc("A8D2FF")
BLUE_D = hexc("23466F")
PURPLE = hexc("8B4FC8")
PURPLE_L = hexc("D7B4FF")
GREEN = hexc("4FA04A")
GREEN_L = hexc("A6E08A")
GREEN_D = hexc("23561F")
AMBER = hexc("D08A2A")
GREY = hexc("8D928E")
SKY = hexc("6FA8C8")

# District tokens (38.U.2, in LembarKunci.SEMUA_DISTRIK order)
DISTRICT = {
    "istana": (hexc("C9A24A"), hexc("8E1F1F")),
    "cendekia": (hexc("2A3A5C"), hexc("D8E4F0")),
    "awan": (hexc("6FA8C8"), hexc("FFFFFF")),
    "gilda": (hexc("8C6A3A"), hexc("D8C29A")),
    "pusat": (hexc("5FA88C"), hexc("D4F0E2")),
    "pasar": (hexc("D08A2A"), hexc("8E1F1F")),
    "hunian": (hexc("7A5A3A"), hexc("D9B98C")),
    "arena": (hexc("7A1A1A"), hexc("E0C070")),
    "kumuh": (hexc("6A6A60"), hexc("A89A80")),
    "gerbang": (hexc("8E1F1F"), hexc("C9A24A")),
    "luar": (hexc("4F8A3A"), hexc("C9D88A")),
}


# ===================================================================================================== icons

def new():
    return Canvas(16, 16, 4)


def gem(c, cx, cy, s, base, light, dark):
    """A faceted spirit-stone crystal."""
    top = (cx, cy - s)
    l = (cx - s * 0.7, cy - s * 0.25)
    r = (cx + s * 0.7, cy - s * 0.25)
    bl = (cx - s * 0.45, cy + s * 0.75)
    br = (cx + s * 0.45, cy + s * 0.75)
    bot = (cx, cy + s)
    c.poly([top, r, br, bot, bl, l], base)
    c.poly([top, l, (cx - s * 0.15, cy)], light)
    c.poly([top, (cx - s * 0.15, cy), (cx + s * 0.2, cy - s * 0.05), r], shade(base, 0.25))
    c.poly([r, br, bot, (cx + s * 0.2, cy - s * 0.05)], dark)
    c.poly([(cx - s * 0.15, cy), bl, bot, (cx + s * 0.2, cy - s * 0.05)], shade(base, -0.1))
    c.circle(cx - s * 0.3, cy - s * 0.45, s * 0.14, (255, 255, 255, 220))


def i_batu(level):
    def draw(c):
        cols = [(hexc("C8D0CC"), hexc("F4FAF6"), hexc("7C8A84")), (JADE_L, JADE_P, JADE),
                (BLUE, BLUE_L, BLUE_D), (PURPLE, PURPLE_L, hexc("4B2280"))][level]
        c.ellipse(8, 14.3, 5.5, 1.3, (0, 0, 0, 70))
        gem(c, 8, 8, 6.2, *cols)
        if level == 3:
            for a in range(4):
                ang = a * math.pi / 2 + 0.4
                c.line(8 + math.cos(ang) * 6.5, 8 + math.sin(ang) * 6.5, 8 + math.cos(ang) * 7.6, 8 + math.sin(ang) * 7.6, 0.7, GOLD_L)
    return draw


def i_nb(c):
    c.circle(8, 8, 6.6, GOLD_D)
    c.circle(8, 8, 6.0, JADE)
    c.ring(8, 8, 4.4, 5.2, JADE_L)
    c.rect(6.4, 6.4, 9.6, 9.6, GOLD_D)
    c.rect(6.9, 6.9, 9.1, 9.1, (0, 0, 0, 0))
    c.ellipse(5.6, 5.0, 1.6, 0.9, (255, 255, 255, 120))
    c.rect(7.0, 7.0, 9.0, 9.0, (30, 40, 34, 255))


def i_pk(c):
    # sect contribution tally: a bamboo slip with a red sect seal
    c.poly([(5, 1.5), (11, 1.5), (11.5, 14.5), (4.5, 14.5)], hexc("DCC783"))
    c.rect(5.2, 4.5, 11.1, 5.1, hexc("94783A"))
    c.rect(5.2, 10.5, 11.3, 11.1, hexc("94783A"))
    c.rect(6.2, 6.2, 9.8, 9.8, RED)
    c.rect(6.9, 6.9, 9.1, 9.1, RED_L)
    c.rect(7.6, 7.6, 8.4, 8.4, RED)
    c.line(6.4, 12.5, 9.6, 12.5, 0.6, INK)
    c.line(6.4, 2.8, 9.6, 2.8, 0.6, INK)


def i_batu_asal(c):
    c.ellipse(8, 14.2, 6, 1.3, (0, 0, 0, 70))
    c.poly([(2, 10), (3.5, 4.5), (8, 2), (12.8, 4), (14.2, 9.5), (11, 13.6), (5, 13.8)], hexc("6E665C"))
    c.poly([(3.5, 4.5), (8, 2), (12.8, 4), (8.5, 6)], hexc("958B7E"))
    c.poly([(11, 13.6), (14.2, 9.5), (12.8, 4), (10, 9)], hexc("4D463E"))
    c.path([(4.5, 11), (7, 8), (9.2, 9.5), (12, 6)], 1.1, hexc("FFB24A"))
    c.path([(4.5, 11), (7, 8), (9.2, 9.5), (12, 6)], 0.45, hexc("FFF0C0"))
    c.circle(9.2, 9.5, 1.2, (255, 200, 90, 160))


def i_tren(kind):
    def draw(c):
        col = {0: GREEN, 1: hexc("C83A30"), 2: GOLD}[kind]
        lite = shade(col, 0.4)
        if kind == 0:
            c.path([(2, 12), (6, 8), (9, 10), (13, 5)], 1.8, col)
            c.poly([(14.5, 3), (14.5, 8), (10, 4)], col)
            c.path([(2, 12), (6, 8), (9, 10), (12.5, 5.5)], 0.6, lite)
        elif kind == 1:
            c.path([(2, 4), (6, 8), (9, 6), (13, 11)], 1.8, col)
            c.poly([(14.5, 13), (14.5, 8), (10, 12)], col)
            c.path([(2, 4), (6, 8), (9, 6), (12.5, 10.5)], 0.6, lite)
        else:
            c.line(2, 8, 12, 8, 1.8, col)
            c.poly([(14.5, 8), (10.5, 4.5), (10.5, 11.5)], col)
            c.line(2.5, 7.6, 11, 7.6, 0.5, lite)
    return draw


def i_gembok(c):
    c.ring(8, 6.5, 2.6, 4.0, IRON)
    c.rect(4.2, 6.2, 5.6, 8.5, IRON)
    c.rect(10.4, 6.2, 11.8, 8.5, IRON)
    c.rect(3, 7.5, 13, 14.5, GOLD)
    c.rect(3, 7.5, 13, 8.5, GOLD_L)
    c.rect(3, 13.5, 13, 14.5, GOLD_D)
    c.circle(8, 10.4, 1.3, INK)
    c.poly([(7.4, 10.6), (8.6, 10.6), (9, 13), (7, 13)], INK)


def i_jam_pasir(c):
    c.rect(3, 1.5, 13, 3, WOOD)
    c.rect(3, 13, 13, 14.5, WOOD)
    c.line(3.8, 3, 3.8, 13, 0.8, WOOD_D)
    c.line(12.2, 3, 12.2, 13, 0.8, WOOD_D)
    c.poly([(5, 3), (11, 3), (8.6, 8), (11, 13), (5, 13), (7.4, 8)], (200, 230, 255, 120))
    c.poly([(6, 4), (10, 4), (8, 7.2)], hexc("E8C070"))
    c.poly([(8, 9.5), (10.4, 12.6), (5.6, 12.6)], hexc("E8C070"))
    c.line(8, 7.2, 8, 12, 0.35, hexc("E8C070"))


def i_lonceng(c):
    c.rect(7.3, 1, 8.7, 3, GOLD_D)
    c.poly([(8, 2.5), (11.5, 5), (12.3, 11), (14, 12.5), (2, 12.5), (3.7, 11), (4.5, 5)], GOLD)
    c.poly([(8, 2.5), (6, 4), (5.5, 11), (4, 12.5), (2, 12.5), (3.7, 11), (4.5, 5)], GOLD_L)
    c.rect(2, 11.8, 14, 12.8, GOLD_D)
    c.circle(8, 13.8, 1.3, GOLD_D)
    c.line(5, 8, 11, 8, 0.4, GOLD_D)


def i_cap_merah(c):
    c.rect(2, 2, 14, 14, RED)
    c.rect(2, 2, 14, 3, RED_L)
    c.rect(3.2, 3.2, 12.8, 12.8, (0, 0, 0, 0))
    c.rect(3.2, 3.2, 12.8, 12.8, hexc("D03A28"))
    c.ring(8, 8, 3.6, 4.6, hexc("FFE0C0", 230))
    c.line(5.5, 8, 10.5, 8, 0.8, hexc("FFE0C0", 230))
    c.line(8, 5.5, 8, 10.5, 0.8, hexc("FFE0C0", 230))


def i_mata(c):
    c.poly([(1, 8), (4, 4.5), (8, 3.5), (12, 4.5), (15, 8), (12, 11.5), (8, 12.5), (4, 11.5)], hexc("F4EEE0"))
    c.circle(8, 8, 3.6, hexc("6A3FB0"))
    c.circle(8, 8, 2.6, hexc("B48CFF"))
    c.circle(8, 8, 1.3, INK)
    c.circle(7, 7, 0.7, WHITE)
    c.path([(1, 8), (4, 4.5), (8, 3.5), (12, 4.5), (15, 8)], 0.9, INK)
    c.line(8, 0.5, 8, 2.2, 0.7, hexc("B48CFF"))
    c.line(3, 1.8, 4.2, 3.2, 0.7, hexc("B48CFF"))
    c.line(13, 1.8, 11.8, 3.2, 0.7, hexc("B48CFF"))


# --- tiny pixel font for rank letters and numerals (5x7 grid)
GLYPH = {
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00110", "01000", "10000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
}


def glyph(c, ch, x, y, px, col):
    for r, row in enumerate(GLYPH[ch]):
        for k, bit in enumerate(row):
            if bit == "1":
                c.rect(x + k * px, y + r * px, x + (k + 1) * px, y + (r + 1) * px, col)


RANK_COL = {"E": hexc("8D928E"), "D": hexc("4FA04A"), "C": hexc("4F86C8"), "B": hexc("8B4FC8"), "A": hexc("D08A2A"), "S": hexc("C83A30")}


def i_pangkat(letter):
    def draw(c):
        col = RANK_COL[letter]
        c.poly([(8, 0.8), (14.6, 4.2), (14.6, 11.8), (8, 15.2), (1.4, 11.8), (1.4, 4.2)], GOLD_D if letter != "S" else GOLD)
        c.poly([(8, 2), (13.4, 4.8), (13.4, 11.2), (8, 14), (2.6, 11.2), (2.6, 4.8)], col)
        c.poly([(8, 2), (13.4, 4.8), (8, 7.5), (2.6, 4.8)], shade(col, 0.25))
        glyph(c, letter, 5.5, 4.5, 1.0, hexc("FFF6DE"))
    return draw


def star(c, cx, cy, r0, r1, col, rot=-math.pi / 2):
    pts = []
    for k in range(10):
        r = r1 if k % 2 == 0 else r0
        a = rot + k * math.pi / 5
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    c.poly(pts, col)


def i_timbangan(c):
    c.rect(7.3, 2.5, 8.7, 13.5, WOOD)
    c.rect(4, 13, 12, 14.5, WOOD_D)
    c.line(2.5, 4, 13.5, 4, 1.0, GOLD)
    c.circle(8, 3, 1.1, GOLD_L)
    for cx in (3.5, 12.5):
        c.line(cx, 4, cx - 2, 8.5, 0.4, GOLD_D)
        c.line(cx, 4, cx + 2, 8.5, 0.4, GOLD_D)
        c.ellipse(cx, 9, 2.6, 1.2, GOLD, 0, math.pi)
        c.rect(cx - 2.6, 8.4, cx + 2.6, 9.1, GOLD_L)


def i_kafilah(c):
    # covered caravan cart
    c.poly([(2, 9), (3.5, 3.5), (12.5, 3.5), (14, 9)], hexc("D9C7A0"))
    c.line(5, 3.6, 4.5, 9, 0.5, hexc("A08A60"))
    c.line(8, 3.6, 8, 9, 0.5, hexc("A08A60"))
    c.line(11, 3.6, 11.5, 9, 0.5, hexc("A08A60"))
    c.rect(1.5, 9, 14.5, 10.6, WOOD)
    c.circle(4.5, 12.2, 2.3, WOOD_D)
    c.circle(4.5, 12.2, 1.3, WOOD_L)
    c.circle(11.5, 12.2, 2.3, WOOD_D)
    c.circle(11.5, 12.2, 1.3, WOOD_L)
    c.rect(7, 2.2, 9, 3.6, RED)


def i_bintang(n):
    def draw(c):
        star(c, 8, 8.6, 3.4, 7.8, GOLD_D)
        star(c, 8, 8.6, 2.8, 6.8, GOLD)
        star(c, 8, 8.2, 1.4, 3.4, GOLD_L)
        glyph(c, str(n), 5.5, 5.2, 1.0, hexc("7A1408"))


    return draw


def blade_shine(c, x0, y0, x1, y1):
    c.line(x0, y0, x1, y1, 0.45, (255, 255, 255, 200))


def i_jian(c):
    c.poly([(13.8, 1.2), (14.8, 2.2), (5.6, 11.6), (4.4, 10.4)], IRON_L)
    c.poly([(14.8, 2.2), (5.6, 11.6), (5.2, 11.2), (14.3, 1.7)], IRON)
    c.line(3.0, 9.0, 7.0, 13.0, 1.3, GOLD)
    c.line(2.0, 14.0, 4.6, 11.4, 1.3, hexc("5B2A1A"))
    c.circle(1.6, 14.4, 0.9, GOLD)
    c.path([(1.6, 14.4), (0.8, 15.6)], 0.5, RED)
    blade_shine(c, 13.4, 2.4, 6.4, 9.6)


def i_dao(c):
    c.poly([(14.5, 1.5), (13.5, 5), (7, 11.8), (4.6, 9.6), (11, 3.6)], IRON_L)
    c.poly([(14.5, 1.5), (13.5, 5), (7, 11.8), (6.4, 11.2), (12.8, 4.6)], IRON)
    c.ellipse(5.4, 10.6, 1.8, 1.8, GOLD)
    c.line(1.8, 14.2, 4.6, 11.4, 1.5, hexc("5B2A1A"))
    c.circle(1.5, 14.5, 0.9, GOLD)
    blade_shine(c, 13.4, 2.6, 6.6, 9.4)


def i_qiang(c):
    c.line(1.5, 14.5, 11.5, 4.5, 1.0, WOOD_L)
    c.poly([(15, 1), (12.5, 6.5), (11.2, 5.2), (9.5, 3.5)], IRON_L)
    c.poly([(15, 1), (12.5, 6.5), (11.9, 5.9)], IRON)
    c.circle(11, 5.2, 1.4, RED)
    c.path([(11, 5.2), (9.5, 8), (8.2, 8.6)], 0.7, RED)
    c.path([(11, 5.2), (10.2, 8.8)], 0.6, RED_L)


def i_kapak(c):
    c.line(3, 15, 11, 2.5, 1.3, WOOD_L)
    c.poly([(8.5, 3.5), (14.5, 2.5), (15, 9.5), (10.5, 8)], IRON)
    c.poly([(14.5, 2.5), (15, 9.5), (14.0, 9.0), (13.7, 3.0)], IRON_L)
    c.poly([(8.5, 3.5), (6.5, 2.5), (7, 6.5)], IRON_D)
    c.rect(9.0, 3.6, 10.8, 6.2, GOLD_D)


def i_gada(c):
    c.line(2.5, 14.5, 8.5, 8.5, 1.4, WOOD)
    c.circle(10.5, 6.0, 4.0, IRON)
    c.circle(9.6, 5.0, 2.0, IRON_L)
    for k in range(8):
        a = k * math.pi / 4
        c.poly([(10.5 + math.cos(a) * 3.6, 6 + math.sin(a) * 3.6), (10.5 + math.cos(a + 0.25) * 3.6, 6 + math.sin(a + 0.25) * 3.6),
                (10.5 + math.cos(a + 0.12) * 5.4, 6 + math.sin(a + 0.12) * 5.4)], IRON_D)
    c.rect(1.5, 14.2, 3.0, 15.4, GOLD)


def i_kipas(c):
    for k in range(9):
        a0 = math.pi * (1.05 + k * 0.1)
        a1 = a0 + 0.1
        c.poly([(8, 13.5), (8 + math.cos(a0) * 7, 13.5 + math.sin(a0) * 7), (8 + math.cos(a1) * 7, 13.5 + math.sin(a1) * 7)],
               IRON if k % 2 == 0 else IRON_L)
    c.ring(8, 13.5, 6.4, 7.2, GOLD)
    c.circle(8, 13.5, 1.2, GOLD_D)
    for k in range(10):
        a = math.pi * (1.05 + k * 0.1)
        c.line(8, 13.5, 8 + math.cos(a) * 6.6, 13.5 + math.sin(a) * 6.6, 0.25, IRON_D)


def i_kebut(c):
    c.line(13.5, 2.5, 7.0, 9.0, 1.1, WOOD_L)
    c.rect(12.6, 1.4, 14.6, 3.6, GOLD)
    for k in range(9):
        off = (k - 4) * 0.55
        c.path([(7.0, 9.0), (4.5 + off, 12 + abs(off) * 0.3), (2.0 + off * 1.6, 15.2)], 0.55, hexc("F2F0E8") if k % 2 else hexc("D8D4C8"))
    c.circle(7.0, 9.0, 1.0, RED)


def i_zirah(c):
    c.poly([(3, 3), (6, 1.5), (10, 1.5), (13, 3), (14, 7), (12.5, 14.5), (3.5, 14.5), (2, 7)], IRON)
    c.poly([(6, 1.5), (10, 1.5), (8, 4)], IRON_D)
    for row in range(4):
        y = 5 + row * 2.3
        for k in range(5):
            x = 4.2 + k * 1.9 + (row % 2) * 0.9
            c.ellipse(x, y, 0.95, 0.9, IRON_L, 0, math.pi)
    c.rect(3.5, 9.6, 12.5, 10.6, RED)
    c.circle(8, 10.1, 1.0, GOLD)


def i_pil(c):
    c.ellipse(8, 14, 5, 1.2, (0, 0, 0, 60))
    c.circle(8, 8, 5.4, hexc("C86A2A"))
    c.circle(7, 7, 4.0, hexc("E89A4A"))
    c.circle(6, 5.8, 1.6, hexc("FFE0B0"))
    c.ring(8, 8, 6.0, 6.8, (255, 210, 120, 110))
    c.path([(5, 10), (8, 11), (11, 9.5)], 0.5, hexc("8A3A10"))


def i_herba(c):
    c.path([(8, 15), (8, 7)], 0.8, GREEN_D)
    for side, y in ((-1, 10), (1, 8.5), (-1, 6.5), (1, 5)):
        c.poly([(8, y + 0.8), (8 + side * 6, y - 1.5), (8 + side * 3.5, y + 1.8)], GREEN if side < 0 else GREEN_L)
        c.line(8, y + 0.6, 8 + side * 4.5, y - 0.7, 0.3, GREEN_D)
    c.circle(8, 3.5, 2.2, hexc("E05A8A"))
    c.circle(8, 3.5, 1.0, hexc("FFE07A"))


def i_jimat(c):
    c.poly([(4, 1), (12, 1), (12, 13), (10, 15), (8, 13.6), (6, 15), (4, 13)], hexc("F1D57E"))
    c.poly([(4, 1), (12, 1), (12, 2.2), (4, 2.2)], hexc("B98F34"))
    c.rect(5.5, 3.5, 10.5, 8.5, RED)
    c.rect(6.3, 4.3, 9.7, 7.7, hexc("F1D57E"))
    c.rect(7.3, 5.3, 8.7, 6.7, RED)
    c.path([(6, 10), (8, 9.5), (10, 10.5), (7, 12)], 0.6, RED_D)


def roof(c, x0, x1, y, h, col, curl=1.2):
    c.poly([(x0 - curl, y - 0.6), (x0, y), (x1, y), (x1 + curl, y - 0.6), (x1 - 1.2, y - h), (x0 + 1.2, y - h)], col)


def d_istana(c, base, acc):
    c.rect(2, 12, 14, 14.5, hexc("D8D0C0"))
    c.rect(3.5, 7.5, 12.5, 12, acc)
    for x in (4.5, 7.2, 9.8, 12):
        c.rect(x - 0.4, 7.5, x + 0.4, 12, shade(acc, -0.35))
    roof(c, 2.5, 13.5, 7.8, 2.4, base)
    c.rect(5, 4.6, 11, 5.6, acc)
    roof(c, 4, 12, 4.8, 2.4, base)
    c.circle(8, 1.9, 0.9, GOLD_L)


def d_cendekia(c, base, acc):
    c.poly([(1.5, 4), (8, 5.5), (8, 14), (1.5, 12.5)], acc)
    c.poly([(14.5, 4), (8, 5.5), (8, 14), (14.5, 12.5)], shade(acc, -0.08))
    for k in range(3):
        c.line(3, 7 + k * 1.8, 7, 7.9 + k * 1.8, 0.35, base)
        c.line(9, 7.9 + k * 1.8, 13, 7 + k * 1.8, 0.35, base)
    c.line(11.5, 1, 6.5, 9.5, 0.9, WOOD_D)
    c.poly([(6.5, 9.5), (5.4, 11.8), (7.2, 10.3)], INK)


def d_awan(c, base, acc):
    for cx, cy, r in ((5, 9, 3.4), (9, 7, 4.2), (12, 9.6, 2.8), (7.5, 10.5, 3.0)):
        c.circle(cx, cy, r, acc)
    c.path([(3, 11), (6, 10.5), (8, 12), (11, 11), (13.5, 12)], 0.7, base)
    c.ring(9, 7, 1.8, 2.5, base)
    c.ring(5, 9, 1.0, 1.6, base)


def d_gilda(c, base, acc):
    c.poly([(3, 2), (13, 2), (13, 8), (8, 14.5), (3, 8)], base)
    c.poly([(4.2, 3.2), (11.8, 3.2), (11.8, 7.6), (8, 12.8), (4.2, 7.6)], acc)
    c.line(5.5, 10, 10.5, 4.5, 1.1, WOOD_D)
    c.rect(9.0, 3.6, 12.2, 6.2, IRON)


def d_pusat(c, base, acc):
    c.rect(4, 12.5, 12, 14.5, shade(base, -0.3))
    for k, (w, y) in enumerate(((5.5, 12.5), (4.4, 9.3), (3.4, 6.3))):
        c.rect(8 - w * 0.6, y - 2.4, 8 + w * 0.6, y, acc)
        roof(c, 8 - w, 8 + w, y - 2.2, 1.5, base, 0.9)
    c.line(8, 2.6, 8, 0.6, 0.6, GOLD)


def d_pasar(c, base, acc):
    c.rect(2.5, 6.5, 13.5, 14, WOOD)
    c.rect(3.5, 9.5, 12.5, 14, WOOD_D)
    for k in range(6):
        col = base if k % 2 == 0 else acc
        c.poly([(1.5 + k * 2.2, 3), (3.7 + k * 2.2, 3), (3.7 + k * 2.2, 7), (2.6 + k * 2.2, 8), (1.5 + k * 2.2, 7)], col)
    c.circle(5.5, 11.5, 1.2, hexc("E05A2A"))
    c.circle(8.5, 11.5, 1.2, GOLD)
    c.circle(11, 11.5, 1.2, GREEN)


def d_hunian(c, base, acc):
    c.rect(3, 8, 13, 14.5, acc)
    c.rect(6.8, 10.5, 9.2, 14.5, base)
    c.rect(3.8, 9.2, 5.8, 11.2, hexc("FFE8A0"))
    c.rect(10.2, 9.2, 12.2, 11.2, hexc("FFE8A0"))
    roof(c, 2, 14, 8.3, 4.5, base, 1.2)
    c.rect(10.5, 2.5, 11.8, 5, shade(base, -0.3))


def d_arena(c, base, acc):
    c.ring(8, 8, 5.5, 7.0, acc)
    c.circle(8, 8, 5.5, shade(base, 0.15))
    c.line(4.5, 4.5, 11.5, 11.5, 1.1, IRON_L)
    c.line(11.5, 4.5, 4.5, 11.5, 1.1, IRON_L)
    c.circle(8, 8, 1.3, acc)


def d_kumuh(c, base, acc):
    c.poly([(2.5, 14.5), (2.5, 8), (8.5, 5.5), (13.5, 8.5), (13.5, 14.5)], acc)
    c.poly([(1.5, 8.6), (8.5, 4.3), (14.5, 8.8), (13.5, 9.6), (8.5, 6.4), (2.5, 9.6)], base)
    c.rect(6, 10.5, 9, 14.5, shade(base, -0.4))
    c.line(10.5, 10, 12.5, 12, 0.5, shade(base, -0.4))
    c.line(12.5, 10, 10.5, 12, 0.5, shade(base, -0.4))
    c.line(3.5, 11, 5.5, 10.5, 0.4, shade(acc, -0.3))


def d_gerbang(c, base, acc):
    c.rect(2.5, 5, 4.5, 15, base)
    c.rect(11.5, 5, 13.5, 15, base)
    c.rect(1.5, 4, 14.5, 5.6, base)
    c.rect(3, 6.8, 13, 7.8, acc)
    roof(c, 1.5, 14.5, 4.2, 2.6, shade(base, -0.25), 1.4)
    c.rect(6.5, 2.2, 9.5, 3.4, acc)


def d_luar(c, base, acc):
    c.rect(1, 9, 15, 15, base)
    for k in range(4):
        c.line(1, 10 + k * 1.4, 15, 9.4 + k * 1.4, 0.4, shade(base, -0.25))
    c.poly([(2, 9.2), (6, 4), (10, 9.2)], hexc("6A7A6A"))
    c.poly([(7, 9.2), (11.5, 3), (15, 9.2)], hexc("7E8E7E"))
    c.poly([(10.4, 4.5), (11.5, 3), (12.6, 4.5)], WHITE)
    c.circle(4, 2.8, 1.6, hexc("FFD27A"))


DISTRICT_DRAW = {"istana": d_istana, "cendekia": d_cendekia, "awan": d_awan, "gilda": d_gilda, "pusat": d_pusat,
                 "pasar": d_pasar, "hunian": d_hunian, "arena": d_arena, "kumuh": d_kumuh, "gerbang": d_gerbang, "luar": d_luar}
DISTRICT_ORDER = ["istana", "cendekia", "awan", "gilda", "pusat", "pasar", "hunian", "arena", "kumuh", "gerbang", "luar"]


def i_distrik(name):
    def draw(c):
        base, acc = DISTRICT[name]
        DISTRICT_DRAW[name](c, base, acc)
    return draw


# --- services (slots 80-99)

def s_penginapan(c):
    c.rect(2, 9, 14, 12, WOOD)
    c.rect(2, 7, 4, 14, WOOD_D)
    c.rect(12, 9.5, 14, 14, WOOD_D)
    c.rect(4, 7.6, 7, 9, WHITE)
    c.rect(6.5, 7.8, 13.5, 9.2, hexc("8E2A2A"))
    c.circle(11.5, 3.5, 2.4, hexc("FFE28A"))
    c.circle(12.5, 2.8, 2.0, (0, 0, 0, 0))
    c.circle(12.6, 2.7, 1.9, hexc("1A2A44", 0))


def s_rumah_makan(c):
    c.ellipse(8, 10.5, 6.5, 3.6, hexc("E6E0D0"), 0, math.pi)
    c.rect(1.5, 9.6, 14.5, 10.6, hexc("2E6AA8"))
    c.ellipse(8, 9.8, 6.4, 1.3, hexc("F5F0E0"))
    c.circle(6, 8.6, 1.4, hexc("F5F5F0"))
    c.circle(8.6, 8.3, 1.5, hexc("F5F5F0"))
    c.circle(10.6, 8.8, 1.2, hexc("E89A4A"))
    c.line(10, 1, 6, 8, 0.6, WOOD_L)
    c.line(12, 1.5, 7.5, 8, 0.6, WOOD_L)
    for x in (4.5, 7.5, 10.5):
        c.path([(x, 6.5), (x - 0.6, 5), (x + 0.4, 3.5)], 0.35, (255, 255, 255, 140))


def s_toko_senjata(c):
    c.rect(1.5, 12.5, 14.5, 14.5, WOOD_D)
    c.rect(2.5, 3, 3.5, 13, WOOD)
    c.rect(12.5, 3, 13.5, 13, WOOD)
    c.rect(2, 4, 14, 5, WOOD_L)
    c.line(5.5, 12, 5.5, 3, 0.8, IRON_L)
    c.line(8, 12, 8, 2, 0.8, IRON)
    c.line(10.5, 12, 10.5, 3.5, 0.8, IRON_L)
    c.rect(4.5, 9, 6.5, 9.7, GOLD)
    c.rect(7, 8.4, 9, 9.1, GOLD)
    c.rect(9.5, 9, 11.5, 9.7, GOLD)


def s_lelang(c):
    c.line(3, 13, 10, 6, 1.2, WOOD_L)
    c.poly([(8, 2), (14, 8), (12, 10), (6, 4)], WOOD)
    c.poly([(8, 2), (14, 8), (13.2, 8.8), (7.2, 2.8)], WOOD_L)
    c.rect(1, 13.5, 9, 15, WOOD_D)
    c.circle(13, 13, 1.6, GOLD)
    c.line(11, 11, 12, 12, 0.4, GOLD_L)


def s_gilda_misi(c):
    c.rect(2, 2, 14, 14, WOOD)
    c.rect(3, 3, 13, 13, WOOD_D)
    c.poly([(4, 4), (8.5, 4), (8.5, 10), (4, 10.5)], PAPER)
    c.poly([(9.5, 5), (12.5, 5), (12.3, 9), (9.6, 9.2)], PAPER)
    c.poly([(5, 10.5), (11, 10.5), (11, 12.5), (5, 12.5)], PAPER_D)
    for k in range(3):
        c.line(4.8, 5.5 + k * 1.4, 7.8, 5.5 + k * 1.4, 0.35, INK)
    c.circle(6.2, 4.2, 0.6, RED)
    c.circle(11, 5.2, 0.6, RED)


def s_bank(c):
    c.poly([(1.5, 5.5), (8, 1.5), (14.5, 5.5)], GOLD_D)
    c.rect(2.5, 5.5, 13.5, 6.6, GOLD)
    for x in (4, 7, 10, 12.8):
        c.rect(x - 0.6, 6.6, x + 0.6, 12.5, hexc("E4DCC8"))
    c.rect(1.5, 12.5, 14.5, 14.5, GOLD_D)
    c.circle(8, 4.3, 1.0, JADE_L)


def s_teleport(c):
    c.ellipse(8, 11.5, 6.8, 3.0, JADE_D)
    c.ellipse(8, 11, 6.0, 2.4, JADE)
    c.ellipse(8, 11, 4.0, 1.5, JADE_L)
    for k in range(5):
        a = k * 2 * math.pi / 5
        c.circle(8 + math.cos(a) * 5.0, 11 + math.sin(a) * 2.0, 0.5, GOLD_L)
    c.poly([(5, 10.5), (6.2, 1.5), (9.8, 1.5), (11, 10.5)], (150, 255, 220, 90))
    c.line(8, 10.5, 8, 2, 0.6, (230, 255, 245, 200))


def s_dermaga(c):
    c.poly([(1.5, 8), (14.5, 8), (12, 11.5), (4, 11.5)], WOOD)
    c.rect(4, 8.5, 12, 9.2, GOLD)
    c.line(8, 8, 8, 1.5, 0.6, WOOD_D)
    c.poly([(8.4, 2), (13, 6.8), (8.4, 6.8)], hexc("F0E8D8"))
    c.poly([(7.6, 2.8), (3.8, 6.8), (7.6, 6.8)], hexc("E0D4C0"))
    for x in (3, 7, 11):
        c.ellipse(x, 13.2, 2.4, 0.9, (255, 255, 255, 170))


def s_pasar_binatang(c):
    c.poly([(3, 10), (5, 5), (10.5, 5.5), (13.5, 9.5), (12, 13), (4.5, 13)], hexc("E08A3A"))
    c.poly([(4, 5.5), (3, 2), (6.3, 4.6)], hexc("E08A3A"))
    c.poly([(9.3, 5), (10.8, 1.8), (11.5, 5.8)], hexc("E08A3A"))
    c.poly([(4.2, 5.1), (3.7, 3.2), (5.5, 4.8)], hexc("FFD0A0"))
    c.ellipse(8, 11, 3, 2, hexc("FFF0DE"))
    c.circle(6.2, 8, 0.8, INK)
    c.circle(10, 8, 0.8, INK)
    c.circle(8.1, 10.2, 0.6, INK)
    c.path([(12.5, 11), (15, 8.5), (14.5, 5.5)], 1.2, hexc("E08A3A"))


def s_judi_batu(c):
    c.poly([(1.5, 10), (3, 4), (8, 2), (12, 4), (8.5, 9), (6.5, 14)], hexc("7E766A"))
    c.poly([(3, 4), (8, 2), (12, 4), (8.5, 9)], hexc("A0968A"))
    c.poly([(8.5, 9), (12, 4), (14.5, 9), (11, 14.5), (7, 14.5)], JADE)
    c.poly([(8.5, 9), (12, 4), (13, 7), (10, 10)], JADE_L)
    c.line(8, 2.5, 6.6, 14, 0.4, INK)
    c.circle(11.5, 9.3, 0.8, WHITE)


def s_yamen(c):
    c.circle(8, 8, 6.8, RED_D)
    c.circle(8, 8, 6.0, RED)
    c.ring(8, 8, 4.8, 5.4, GOLD)
    c.line(4.5, 11.5, 11.5, 4.5, 1.1, WOOD_L)
    c.circle(11.6, 4.4, 1.6, WOOD_L)
    c.circle(8, 8, 1.4, GOLD_L)


def s_kuil(c):
    c.rect(3, 9, 13, 11, GOLD_D)
    c.poly([(2, 11), (14, 11), (12.5, 14.5), (3.5, 14.5)], GOLD)
    for x in (5.5, 8, 10.5):
        c.line(x, 9, x, 3.5, 0.55, hexc("C04020"))
        c.circle(x, 3.4, 0.55, hexc("FF8040"))
        c.path([(x, 3), (x - 0.6, 1.8), (x + 0.4, 0.6)], 0.3, (220, 220, 230, 150))


def s_perpustakaan(c):
    cols = [hexc("2A3A5C"), hexc("8E1F1F"), hexc("2E8963"), hexc("8C6A3A"), hexc("5A3A8A")]
    x = 2
    for k, wd in enumerate((2.2, 1.8, 2.6, 2.0, 2.4)):
        h = 9 + (k % 3)
        c.rect(x, 14 - h, x + wd, 14, cols[k])
        c.rect(x, 14 - h + 1.5, x + wd, 14 - h + 2.2, GOLD)
        x += wd + 0.3
    c.rect(1, 14, 15, 15, WOOD)


def s_tabib(c):
    c.poly([(5, 2.5), (11, 2.5), (10, 5), (13, 9), (12, 14), (4, 14), (3, 9), (6, 5)], hexc("C8A070"))
    c.rect(6, 1.2, 10, 2.8, WOOD_D)
    c.rect(7.2, 6.5, 8.8, 12.5, RED)
    c.rect(5, 8.7, 11, 10.3, RED)
    c.line(4, 9, 4.6, 12, 0.4, hexc("E8C898"))


def s_menara(c):
    for k, (w, y) in enumerate(((5.5, 15), (4.5, 11.5), (3.6, 8.2), (2.8, 5.2))):
        c.rect(8 - w * 0.55, y - 3.2, 8 + w * 0.55, y, hexc("D8C8A8"))
        roof(c, 8 - w, 8 + w, y - 3.0, 1.2, hexc("2A3A5C"), 0.8)
    c.line(8, 2, 8, 0.3, 0.6, GOLD)


def s_gilda_alkemis(c):
    c.ellipse(8, 13.5, 6, 1.2, (0, 0, 0, 60))
    c.ellipse(8, 9.5, 5.6, 4.6, IRON_D)
    c.ellipse(8, 9.0, 5.0, 4.0, hexc("6E7A80"))
    c.rect(2.5, 6, 13.5, 7.2, GOLD_D)
    c.line(4.5, 13, 3.5, 15, 0.7, IRON_D)
    c.line(11.5, 13, 12.5, 15, 0.7, IRON_D)
    c.ellipse(8, 9.6, 2.2, 1.6, hexc("FF9A3A"))
    for k, x in enumerate((6.4, 8, 9.6)):
        c.path([(x, 5.5), (x - 0.6 + k * 0.3, 3.5), (x + 0.3, 1.2)], 0.45, (180, 255, 210, 150))


def s_gilda_tempa(c):
    c.poly([(2, 9), (13, 9), (14.5, 7.5), (14.5, 10.5), (12, 12), (4, 12)], IRON_D)
    c.rect(5.5, 12, 10.5, 14.5, IRON_D)
    c.poly([(2, 9), (13, 9), (14.5, 7.5), (3.5, 7.5)], IRON)
    c.line(11, 1.5, 7.5, 6, 0.9, WOOD_L)
    c.rect(9.5, 0.6, 13.5, 3.4, IRON_L)
    for k in range(3):
        c.circle(6 + k * 1.8, 6.5 - k * 0.6, 0.45, hexc("FFB040"))


def s_lapak_sekte(c):
    c.rect(2, 7, 14, 14.5, WOOD)
    c.rect(2, 7, 14, 8.2, WOOD_L)
    c.line(2.6, 7, 2.6, 2, 0.6, WOOD_D)
    c.line(13.4, 7, 13.4, 2, 0.6, WOOD_D)
    c.poly([(1.5, 2), (14.5, 2), (14.5, 4.5), (1.5, 4.5)], JADE)
    for k in range(4):
        c.poly([(1.5 + k * 3.25, 4.5), (4.75 + k * 3.25, 4.5), (3.1 + k * 3.25, 5.8)], JADE_D)
    c.rect(6, 9.5, 10, 13, hexc("F1D57E"))
    c.rect(7.2, 10.3, 8.8, 12.2, RED)


def s_istana(c):
    d_istana(c, *DISTRICT["istana"])


def s_arena(c):
    c.ellipse(8, 11, 7, 3.5, hexc("B89A6A"))
    c.ellipse(8, 10.6, 5.5, 2.4, hexc("D8C090"))
    c.line(4.5, 3, 10, 9, 1.0, IRON_L)
    c.line(11.5, 3, 6, 9, 1.0, IRON_L)
    c.rect(3.2, 2.2, 5.2, 3.6, GOLD)
    c.rect(10.8, 2.2, 12.8, 3.6, GOLD)


SERVICES = [s_penginapan, s_rumah_makan, s_toko_senjata, s_lelang, s_gilda_misi, s_bank, s_teleport, s_dermaga,
            s_pasar_binatang, s_judi_batu, s_yamen, s_kuil, s_perpustakaan, s_tabib, s_menara, s_gilda_alkemis,
            s_gilda_tempa, s_lapak_sekte, s_istana, s_arena]


def fix_inn(c):
    # inn: a bed under a crescent moon (redrawn here so the moon cut-out is clean)
    c.rect(2, 9, 14, 12, WOOD)
    c.rect(2, 6.5, 3.8, 14, WOOD_D)
    c.rect(12.2, 9.5, 14, 14, WOOD_D)
    c.rect(4, 7.6, 7, 9, WHITE)
    c.rect(6.5, 7.8, 12.2, 9.2, hexc("8E2A2A"))
    c.ellipse(11.4, 3.6, 2.6, 2.6, hexc("FFE28A"), -math.pi, math.pi)
    for X in range(c.W):
        for Y in range(c.H):
            dx = (X + 0.5) / c.ss - 12.6
            dy = (Y + 0.5) / c.ss - 2.6
            if dx * dx + dy * dy <= 2.2 * 2.2:
                c.px[Y * c.W + X] = [0.0, 0.0, 0.0, 0.0]


SERVICES[0] = fix_inn


# --- 38UI extra icons (slots 112+; mirrored in IkonTempat)

def x_centang(c):
    c.circle(8, 8, 6.6, JADE_D)
    c.circle(8, 8, 5.8, JADE)
    c.path([(4.6, 8.2), (7.1, 10.8), (11.6, 5.4)], 1.5, WHITE)


def x_silang(c):
    c.circle(8, 8, 6.6, RED_D)
    c.circle(8, 8, 5.8, RED)
    c.line(5.4, 5.4, 10.6, 10.6, 1.4, WHITE)
    c.line(10.6, 5.4, 5.4, 10.6, 1.4, WHITE)


def x_surat(c):
    c.rect(1.5, 4, 14.5, 12.5, PAPER)
    c.poly([(1.5, 4), (14.5, 4), (8, 9)], PAPER_D)
    c.path([(1.5, 4), (8, 9), (14.5, 4)], 0.5, hexc("8A7A5A"))
    c.circle(8, 8.6, 1.6, RED)
    c.circle(8, 8.6, 0.7, RED_L)


def x_kompas(c):
    c.circle(8, 8, 6.8, WOOD_D)
    c.circle(8, 8, 6.0, PAPER)
    c.ring(8, 8, 4.8, 5.2, WOOD_L)
    c.poly([(8, 2.6), (9.2, 8), (6.8, 8)], RED)
    c.poly([(8, 13.4), (9.2, 8), (6.8, 8)], INK)
    c.circle(8, 8, 0.8, GOLD)


def x_piala(c):
    c.poly([(3.5, 2), (12.5, 2), (11.5, 7), (8, 9.5), (4.5, 7)], GOLD)
    c.poly([(3.5, 2), (6.5, 2), (6.0, 7.6), (4.5, 7)], GOLD_L)
    c.ring(3.6, 5, 1.4, 2.2, GOLD_D)
    c.ring(12.4, 5, 1.4, 2.2, GOLD_D)
    c.rect(7.2, 9.3, 8.8, 12, GOLD_D)
    c.rect(4.5, 12, 11.5, 14.5, WOOD)
    star(c, 8, 5, 0.8, 1.8, WHITE)


def x_tanya(c):
    c.circle(8, 8, 6.6, hexc("2A3A5C"))
    c.circle(8, 8, 5.8, hexc("4F6A9C"))
    c.path([(5.8, 6.2), (6.4, 4.4), (8.2, 3.8), (10, 4.6), (10.2, 6.4), (8, 8), (8, 9.6)], 1.3, WHITE)
    c.circle(8, 11.8, 0.9, WHITE)


def x_bintang_kosong(c):
    star(c, 8, 8.4, 2.6, 6.6, GOLD_D)
    star(c, 8, 8.4, 1.6, 4.6, hexc("3A3020", 200))


def x_bintang_penuh(c):
    star(c, 8, 8.4, 2.6, 6.6, GOLD_D)
    star(c, 8, 8.4, 2.1, 5.6, GOLD)
    star(c, 8, 8.0, 1.0, 2.6, GOLD_L)


def x_dupa(c):
    s_kuil(c)


def x_buku(c):
    c.poly([(2, 3), (8, 4.2), (8, 14), (2, 12.8)], hexc("8E1F1F"))
    c.poly([(14, 3), (8, 4.2), (8, 14), (14, 12.8)], hexc("A8302A"))
    c.poly([(2.8, 3.6), (7.6, 4.6), (7.6, 12.8), (2.8, 11.9)], PAPER)
    c.poly([(13.2, 3.6), (8.4, 4.6), (8.4, 12.8), (13.2, 11.9)], PAPER)
    for k in range(4):
        c.line(3.6, 6 + k * 1.5, 7, 6.6 + k * 1.5, 0.3, INK)


def x_gulungan(c):
    c.rect(3, 3.5, 13, 12.5, PAPER)
    c.rect(1.5, 2, 14.5, 3.8, WOOD)
    c.rect(1.5, 12.2, 14.5, 14, WOOD)
    c.circle(1.6, 2.9, 1.0, GOLD)
    c.circle(14.4, 2.9, 1.0, GOLD)
    c.circle(1.6, 13.1, 1.0, GOLD)
    c.circle(14.4, 13.1, 1.0, GOLD)
    for k in range(4):
        c.line(4.5, 5.5 + k * 1.7, 11.5, 5.5 + k * 1.7, 0.35, INK)


def x_kunci(c):
    c.ring(5, 8, 1.4, 3.2, GOLD)
    c.rect(7.6, 7.2, 14.5, 8.8, GOLD)
    c.rect(11, 8.8, 12.2, 11, GOLD)
    c.rect(13.2, 8.8, 14.4, 10.3, GOLD)
    c.line(8, 7.5, 14, 7.5, 0.35, GOLD_L)


def x_panah(direction):
    def draw(c):
        pts = [(4, 3), (11.5, 8), (4, 13)] if direction > 0 else [(12, 3), (4.5, 8), (12, 13)]
        c.poly(pts, GOLD_D)
        c.poly([(p[0] * 0.8 + 8 * 0.2, p[1] * 0.8 + 8 * 0.2) for p in pts], GOLD)
    return draw


def x_kuda(c):
    # road post: a stone milestone with a red cap
    c.poly([(5, 14.5), (5.5, 4.5), (8, 2.5), (10.5, 4.5), (11, 14.5)], hexc("A0988A"))
    c.poly([(5.5, 4.5), (8, 2.5), (10.5, 4.5), (8, 5)], RED)
    c.line(6.5, 7.5, 9.5, 7.5, 0.5, INK)
    c.line(6.5, 9.5, 9.5, 9.5, 0.5, INK)
    c.line(6.5, 11.5, 8.5, 11.5, 0.5, INK)
    c.rect(3.5, 14, 12.5, 15, hexc("6A6458"))


def x_peta(c):
    c.poly([(1.5, 3), (5.5, 2), (10.5, 3.5), (14.5, 2.5), (14.5, 13), (10.5, 14), (5.5, 12.5), (1.5, 13.5)], PAPER)
    c.line(5.5, 2, 5.5, 12.5, 0.35, PAPER_D)
    c.line(10.5, 3.5, 10.5, 14, 0.35, PAPER_D)
    c.path([(3, 11), (5, 8), (8, 9), (10, 6), (12.5, 5)], 0.5, RED)
    c.line(11.6, 4.2, 13.4, 5.8, 0.5, RED)
    c.line(13.4, 4.2, 11.6, 5.8, 0.5, RED)
    c.circle(4, 5, 1.0, JADE)


def x_hati(c):
    c.circle(5.6, 6, 3.4, hexc("D03A4A"))
    c.circle(10.4, 6, 3.4, hexc("D03A4A"))
    c.poly([(2.4, 7.2), (13.6, 7.2), (8, 14)], hexc("D03A4A"))
    c.circle(5, 5, 1.2, hexc("FF9AA8"))


def x_wajah_tutup(c):
    # a shuttered stall / "closed" shutter
    c.rect(2, 3, 14, 14, WOOD_D)
    for k in range(5):
        c.rect(2.6, 3.8 + k * 2, 13.4, 5.2 + k * 2, WOOD)
    c.rect(6, 7.5, 10, 9.5, hexc("F1D57E"))
    c.line(6.6, 8.5, 9.4, 8.5, 0.4, RED)


def x_rantai(c):
    # three interlocked oval links on a diagonal: open links face the viewer, the middle one is seen edge-on
    ang = -math.pi / 4
    ca, sa = math.cos(ang), math.sin(ang)

    def oval(cx, cy, rx, ry, wd, col):
        pts = []
        for k in range(25):
            t = 2 * math.pi * k / 24
            x, y = rx * math.cos(t), ry * math.sin(t)
            pts.append((cx + x * ca - y * sa, cy + x * sa + y * ca))
        c.path(pts, wd, col)

    oval(4.2, 11.8, 3.2, 1.9, 1.5, IRON_D)
    oval(4.2, 11.8, 3.2, 1.9, 0.8, IRON_L)
    oval(11.8, 4.2, 3.2, 1.9, 1.5, IRON_D)
    oval(11.8, 4.2, 3.2, 1.9, 0.8, IRON_L)
    c.line(5.6, 10.4, 10.4, 5.6, 2.6, IRON_D)
    c.line(5.9, 10.1, 10.1, 5.9, 1.5, IRON)
    c.line(6.6, 8.8, 8.8, 6.6, 0.5, IRON_L)


def x_obor(c):
    c.line(8, 15, 8, 7, 1.4, WOOD)
    c.ellipse(8, 5.5, 2.6, 4.2, hexc("FF8A2A"))
    c.ellipse(8, 6.5, 1.5, 2.6, hexc("FFE07A"))


EXTRA = [("centang", x_centang), ("silang", x_silang), ("surat", x_surat), ("kompas", x_kompas), ("piala", x_piala),
         ("tanya", x_tanya), ("bintang_kosong", x_bintang_kosong), ("bintang_penuh", x_bintang_penuh), ("dupa", x_dupa),
         ("buku", x_buku), ("gulungan", x_gulungan), ("kunci", x_kunci), ("panah_kanan", x_panah(1)),
         ("panah_kiri", x_panah(-1)), ("tonggak", x_kuda), ("peta", x_peta), ("hati", x_hati), ("tutup", x_wajah_tutup),
         ("rantai", x_rantai), ("obor", x_obor)]


def build_icons():
    img = Image(256, 256, (0, 0, 0, 0))
    slots = {}
    for k in range(4):
        slots[k] = i_batu(k)
    slots[4] = i_nb
    slots[5] = i_pk
    slots[6] = i_batu_asal
    slots[8] = i_tren(0)
    slots[9] = i_tren(1)
    slots[10] = i_tren(2)
    slots[11] = i_gembok
    slots[12] = i_jam_pasir
    slots[13] = i_lonceng
    slots[14] = i_cap_merah
    slots[15] = i_mata
    for k, letter in enumerate("EDCBAS"):
        slots[16 + k] = i_pangkat(letter)
    slots[22] = i_timbangan
    slots[23] = i_kafilah
    for n in range(1, 10):
        slots[32 + n - 1] = i_bintang(n)
    for k, f in enumerate([i_jian, i_dao, i_qiang, i_kapak, i_gada, i_kipas, i_kebut, i_zirah, i_pil, i_herba, i_jimat]):
        slots[48 + k] = f
    for k, name in enumerate(DISTRICT_ORDER):
        slots[64 + k] = i_distrik(name)
    for k, f in enumerate(SERVICES):
        slots[80 + k] = f
    for k, (_, f) in enumerate(EXTRA):
        slots[112 + k] = f
    for slot, draw in sorted(slots.items()):
        c = new()
        draw(c)
        paste(img, c.resolve(), (slot % 16) * 16, (slot // 16) * 16, OUTLINE)
    return img


# ===================================================================================================== panels

def panel(style):
    img = Image(32, 32, (0, 0, 0, 0))
    P = 16  # tile period of edges and centre

    def base_at(x, y):
        if style == "kertas":
            n = fbm((x % P) / 4.0, (y % P) / 4.0, 3, period=4)
            col = mix(hexc("EEE4C6"), hexc("D9C9A0"), n * 0.9)
            if hnoise(x % P * 3.1, y % P * 3.1, 5, 48) > 0.93:
                col = shade(col, -0.08)
            # long paper fibres: faint diagonal strands that wrap with the 16 px tile
            if (x % P + 2 * (y % P)) % 16 == 0 and hnoise(x % P, y % P, 19, 16) > 0.55:
                col = shade(col, -0.05)
            return col
        if style == "kayu":
            g = math.sin((y % P) * 0.9 + fbm((x % P) / 4.0, (y % P) / 4.0, 7, period=4) * 3.0)
            col = mix(hexc("7A2418"), hexc("9A3424"), 0.5 + 0.5 * g)
            if (y % 8) == 0:
                col = shade(col, -0.15)
            elif (y % 8) == 1:  # lacquer sheen on the upper lip of each board
                col = shade(col, 0.07)
            if hnoise((x % P) * 2.9, (y % P) * 2.9, 29, 46) > 0.95:  # rare gold-leaf fleck
                col = mix(col, hexc("C9A24A"), 0.55)
            return col
        if style == "batu":
            n = fbm((x % P) / 4.0, (y % P) / 4.0, 11, period=4)
            col = mix(hexc("9C9C94"), hexc("B8B8AE"), n)
            if hnoise((x % P) * 2.3, (y % P) * 2.3, 13, 37) > 0.88:
                col = shade(col, -0.18)
            return col
        if style == "kain":
            w = ((x + y) % 4 == 0) * 0.08 + ((x - y) % 4 == 0) * 0.05
            n = fbm((x % P) / 4.0, (y % P) / 4.0, 17, period=4)
            return shade(mix(hexc("4A1E3C"), hexc("5E2A4C"), n), w)
        if style == "giok":
            n = fbm((x % P) / 4.0, (y % P) / 4.0, 23, period=4)
            vein = abs(math.sin((x % P) * 0.45 + n * 5.0 + (y % P) * 0.2))
            col = mix(hexc("CDE6D8"), hexc("B2D6C2"), n)
            if vein < 0.12:
                col = mix(col, hexc("8EC4A8"), 0.6)
            return col
        raise ValueError(style)

    for y in range(32):
        for x in range(32):
            img.set(x, y, base_at(x, y))

    def border(c0, c1, inset):
        for t in range(32 - inset * 2):
            img.set(inset + t, inset, c0)
            img.set(inset + t, 31 - inset, c1)
            img.set(inset, inset + t, c0)
            img.set(31 - inset, inset + t, c1)

    if style == "kertas":
        border(hexc("8A671B"), hexc("6A4F14"), 0)
        border(hexc("D4A637"), hexc("B08A2A"), 1)
        border(hexc("F0E4C4"), hexc("E0D2AE"), 2)
        border(hexc("C9A24A"), hexc("B08A2A"), 3)
        for cx, cy in ((4, 4), (27, 4), (4, 27), (27, 27)):
            c = Canvas(8, 8, 4)
            c.rect(1, 1, 7, 7, GOLD_D)
            c.rect(1.8, 1.8, 6.2, 6.2, GOLD)
            c.line(2.5, 4, 5.5, 4, 0.6, GOLD_D)
            c.line(4, 2.5, 4, 5.5, 0.6, GOLD_D)
            c.circle(4, 4, 0.8, RED)
            paste(img, c.resolve(), cx - 4, cy - 4)
    elif style == "kayu":
        border(hexc("2A0E08"), hexc("1E0905"), 0)
        border(hexc("C9A24A"), hexc("8A671B"), 1)
        border(hexc("5A1810"), hexc("4A140C"), 2)
        for cx, cy, sx, sy in ((0, 0, 1, 1), (31, 0, -1, 1), (0, 31, 1, -1), (31, 31, -1, -1)):
            for t in range(7):
                img.set(cx + sx * t, cy + sy * 1, hexc("E0BC5A"))
                img.set(cx + sx * 1, cy + sy * t, hexc("E0BC5A"))
                img.set(cx + sx * t, cy + sy * 2, hexc("8A671B"))
                img.set(cx + sx * 2, cy + sy * t, hexc("8A671B"))
            img.set(cx + sx * 4, cy + sy * 4, hexc("F7DC7E"))
            img.set(cx + sx * 3, cy + sy * 4, hexc("8A671B"))
            img.set(cx + sx * 4, cy + sy * 3, hexc("8A671B"))
    elif style == "batu":
        border(hexc("3A3A36"), hexc("2A2A26"), 0)
        border(hexc("D0D0C6"), hexc("6A6A62"), 1)
        border(hexc("7A7A72"), hexc("C4C4BA"), 4)
        for cx, cy in ((3, 3), (28, 3), (3, 28), (28, 28)):
            c = Canvas(6, 6, 4)
            c.circle(3, 3, 1.9, hexc("3A3E42"))
            c.circle(2.6, 2.6, 1.0, hexc("8A9298"))
            paste(img, c.resolve(), cx - 3, cy - 3)
    elif style == "kain":
        border(hexc("1E0814"), hexc("16060E"), 0)
        border(hexc("D4A637"), hexc("A07A26"), 1)
        border(hexc("6A2E52"), hexc("5A2444"), 2)
        border(hexc("B08A2A"), hexc("8A671B"), 3)
        for cx, cy in ((4, 4), (27, 4), (4, 27), (27, 27)):
            c = Canvas(8, 8, 4)
            star(c, 4, 4, 1.0, 3.0, GOLD, math.pi / 4)
            c.circle(4, 4, 0.9, GOLD_L)
            paste(img, c.resolve(), cx - 4, cy - 4)
    elif style == "giok":
        border(hexc("12382A"), hexc("0C281E"), 0)
        border(hexc("2E8963"), hexc("1E6A4A"), 1)
        border(hexc("67C99B"), hexc("3E9A70"), 2)
        border(hexc("A8D8C0"), hexc("8EC4A8"), 3)
        for cx, cy, sx, sy in ((4, 4, 1, 1), (27, 4, -1, 1), (4, 27, 1, -1), (27, 27, -1, -1)):
            c = Canvas(8, 8, 4)
            ox, oy = 4 - sx * 2.0, 4 - sy * 2.0
            c.ring(ox + sx * 2.2, oy + sy * 2.2, 1.0, 1.8, JADE_D)
            c.ring(ox + sx * 3.8, oy + sy * 1.2, 0.6, 1.3, JADE_D)
            c.circle(ox + sx * 2.2, oy + sy * 2.2, 0.6, GOLD)
            paste(img, c.resolve(), cx - 4, cy - 4)
    return img


def awan():
    """Seamless faint cloud-scroll lines (white + alpha), period 64."""
    img = Image(64, 64, (0, 0, 0, 0))
    c = Canvas(64, 64, 3)

    def scroll(cx, cy, r, turns, wd, a):
        pts = []
        steps = int(36 * turns)
        for k in range(steps + 1):
            t = k / steps
            ang = t * turns * 2 * math.pi
            rr = r * (1 - t * 0.8)
            pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
        for dx in (-64, 0, 64):
            for dy in (-64, 0, 64):
                c.path([(p[0] + dx, p[1] + dy) for p in pts], wd, (255, 255, 255, a))

    def wave(y, amp, a):
        pts = [(x, y + math.sin(x / 64 * 2 * math.pi * 2) * amp) for x in range(-2, 67, 2)]
        for dy in (-64, 0, 64):
            c.path([(p[0], p[1] + dy) for p in pts], 0.8, (255, 255, 255, a))

    scroll(14, 16, 7, 1.4, 0.9, 120)
    scroll(46, 40, 9, 1.5, 0.9, 110)
    scroll(30, 56, 5, 1.2, 0.8, 90)
    wave(24, 2.5, 70)
    wave(50, 2.0, 60)
    paste(img, c.resolve(), 0, 0)
    return img


# ===================================================================================================== lantern tabs, portrait, stamps

def lentera():
    img = Image(128, 32, (0, 0, 0, 0))
    states = [  # body, light, cap
        (hexc("7A1E18"), hexc("A83A2A"), GOLD_D),
        (hexc("9A2A20"), hexc("D05A3A"), GOLD),
        (hexc("D04A2A"), hexc("FFD07A"), GOLD_L),
        (hexc("5A5A56"), hexc("7A7A74"), hexc("6A6A62")),
    ]
    for k, (body, light, cap) in enumerate(states):
        c = Canvas(24, 32, 4)
        cx = 12
        c.line(cx, 0, cx, 3, 0.7, cap)
        c.rect(cx - 5, 3, cx + 5, 5, cap)
        c.ellipse(cx, 15, 10.5, 10.5, body)
        c.ellipse(cx, 15, 8.0, 9.6, light if k == 2 else shade(body, 0.12))
        if k == 2:
            c.ellipse(cx, 15, 5.0, 7.0, hexc("FFF2C0"))
        for rib in (-6.5, -3.2, 0.0, 3.2, 6.5):
            pts = []
            for t in range(21):
                yy = 5.5 + t * 0.95
                xx = cx + rib * math.sin(math.pi * (yy - 4.5) / 21.0)
                pts.append((xx, yy))
            c.path(pts, 0.45, shade(body, -0.35) if k != 2 else hexc("C04A1A", 160))
        c.rect(cx - 5, 25, cx + 5, 27, cap)
        c.line(cx, 27, cx, 30.5, 0.6, cap)
        c.ellipse(cx, 31, 1.4, 1.0, RED if k != 3 else GREY)
        paste(img, c.resolve(), k * 24, 0)
    g = Canvas(32, 32, 2)
    for r in range(15, 0, -1):
        g.circle(16, 16, r, (255, 210, 120, int(14 * (1 - r / 16) ** 0.7 * 3)))
    paste(img, g.resolve(), 96, 0)
    return img


def potret():
    img = Image(64, 64, (0, 0, 0, 0))
    c = Canvas(56, 56, 4)
    c.rect(0, 0, 56, 56, GOLD_D)
    c.rect(1, 1, 55, 55, GOLD)
    c.rect(2, 2, 54, 54, hexc("6E160F"))
    c.rect(3, 3, 53, 53, GOLD_L)
    c.rect(4, 4, 52, 52, (0, 0, 0, 0))
    res = c.resolve()
    for y in range(56):
        for x in range(56):
            if 4 <= x < 52 and 4 <= y < 52:
                res[y][x] = (0, 0, 0, 0)
    paste(img, res, 0, 0)
    for cx, cy, sx, sy in ((0, 0, 1, 1), (55, 0, -1, 1), (0, 55, 1, -1), (55, 55, -1, -1)):
        k = Canvas(12, 12, 4)
        ox, oy = (0, 0)
        k.ring(6 + sx * -1.0, 6 + sy * -1.0, 2.0, 3.4, GOLD_D)
        k.ring(6 + sx * -1.0, 6 + sy * -1.0, 2.4, 3.0, GOLD_L)
        k.circle(6 + sx * -1.0, 6 + sy * -1.0, 1.2, RED)
        paste(img, k.resolve(), cx - 6 + sx * 4, cy - 6 + sy * 4)
    return img


def stempel():
    img = Image(128, 64, (0, 0, 0, 0))
    rnd = random.Random(38)
    # round seal with a rough worn edge
    c = Canvas(48, 48, 3)
    c.ring(24, 24, 19.5, 23.0, RED)
    c.ring(24, 24, 16.0, 17.2, RED)
    for k in range(40):
        a = rnd.random() * 2 * math.pi
        r = 19.5 + rnd.random() * 3.5
        c.circle(24 + math.cos(a) * r, 24 + math.sin(a) * r, 0.5 + rnd.random() * 0.6, (0, 0, 0, 0))
    for k in range(24):  # beaded ring between the two bands
        a = k * 2 * math.pi / 24
        c.circle(24 + math.cos(a) * 18.4, 24 + math.sin(a) * 18.4, 0.55, RED)
    res = c.resolve()
    for k in range(60):
        x, y = rnd.randrange(48), rnd.randrange(48)
        p = res[y][x]
        if p[3]:
            res[y][x] = (p[0], p[1], p[2], int(p[3] * 0.45))
    paste(img, res, 0, 0)
    # square seal: thick outer frame with chipped corners, a fret (key-pattern) band, a thin inner frame and a faint
    # mottled ink field; the centre stays light because the code writes the seal text over it
    c = Canvas(48, 48, 3)
    c.rect(2, 2, 46, 46, RED)
    res = c.resolve()
    for y in range(48):
        for x in range(48):
            if 5 <= x < 43 and 5 <= y < 43:
                res[y][x] = (0, 0, 0, 0)
    for cx, cy in ((2, 2), (45, 2), (2, 45), (45, 45)):  # chipped stone corners
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                if abs(dx) + abs(dy) <= 1 and 0 <= cx + dx < 48 and 0 <= cy + dy < 48:
                    res[cy + dy][cx + dx] = (0, 0, 0, 0)
    for k in range(70):  # worn ink on the frame
        x, y = rnd.randrange(2, 46), rnd.randrange(2, 46)
        p = res[y][x]
        if p[3]:
            res[y][x] = (p[0], p[1], p[2], int(p[3] * (0.25 if k % 3 == 0 else 0.55)))
    fret = (179, 40, 29, 150)
    for t in range(11, 36, 4):  # key pattern: a hook every 4 px along each side of the band
        for (x, y) in ((t, 7), (t + 1, 7), (t + 2, 7), (t + 2, 8), (t, 9), (t + 1, 9), (t + 2, 9),
                       (t, 40), (t + 1, 40), (t + 2, 40), (t, 39), (t, 38), (t + 1, 38), (t + 2, 38)):
            if 6 <= x < 42:
                res[y][x] = fret
        for (y, x) in ((t, 7), (t + 1, 7), (t + 2, 7), (t + 2, 8), (t, 9), (t + 1, 9), (t + 2, 9),
                       (t, 40), (t + 1, 40), (t + 2, 40), (t, 39), (t, 38), (t + 1, 38), (t + 2, 38)):
            if 6 <= y < 42:
                res[y][x] = fret
    for t in range(11, 37):  # thin inner frame
        for (x, y) in ((t, 11), (t, 36), (11, t), (36, t)):
            res[y][x] = (179, 40, 29, 200)
    for y in range(12, 36):  # faint mottled field
        for x in range(12, 36):
            a = int(34 + 30 * hnoise(x * 0.6, y * 0.6, 41, None))
            res[y][x] = (179, 40, 29, a)
    paste(img, res, 48, 0)
    # padlock wax
    c = Canvas(32, 32, 4)
    for k in range(9):
        a = k * 2 * math.pi / 9
        c.circle(16 + math.cos(a) * 11, 16 + math.sin(a) * 11, 4.2, RED_D)
    c.circle(16, 16, 12.5, RED)
    c.circle(14, 13, 6, hexc("D04030"))
    c.ring(16, 13.2, 3.0, 4.4, hexc("FFD8C0", 220))
    c.rect(10.5, 14, 21.5, 22.5, hexc("FFD8C0", 220))
    c.circle(16, 17.6, 1.4, RED)
    paste(img, c.resolve(), 96, 0)
    # star seal
    c = Canvas(32, 32, 4)
    c.circle(16, 16, 14, GOLD_D)
    c.circle(16, 16, 12.6, GOLD)
    star(c, 16, 16.5, 4.0, 10.0, GOLD_L)
    star(c, 16, 16.5, 2.4, 6.0, GOLD_D)
    paste(img, c.resolve(), 96, 32)
    # ribbons
    for k, (col, edge) in enumerate(((hexc("6A6A64"), hexc("3A3A36")), (GOLD, GOLD_D))):
        c = Canvas(48, 16, 4)
        c.poly([(0, 2), (48, 2), (44, 8), (48, 14), (0, 14), (4, 8)], edge)
        c.poly([(1.5, 3.2), (46, 3.2), (42.5, 8), (46, 12.8), (1.5, 12.8), (5.2, 8)], col)
        c.line(6, 4.4, 42, 4.4, 0.35, shade(col, 0.25))
        paste(img, c.resolve(), k * 48, 48)
    return img


# ===================================================================================================== main

def main():
    os.makedirs(OUT, exist_ok=True)
    icons = build_icons()
    icons.save(os.path.join(OUT, "ikon.png"))
    preview(icons, "ikon_x4.png", 4)
    for style in ("kertas", "kayu", "batu", "kain", "giok"):
        p = panel(style)
        p.save(os.path.join(OUT, "panel_%s.png" % style))
        preview(p, "panel_%s_x8.png" % style, 8)
    a = awan()
    a.save(os.path.join(OUT, "awan.png"))
    preview(a, "awan_x4.png", 4, (90, 70, 50, 255))
    lt = lentera()
    lt.save(os.path.join(OUT, "lentera.png"))
    preview(lt, "lentera_x6.png", 6)
    pt = potret()
    pt.save(os.path.join(OUT, "potret.png"))
    preview(pt, "potret_x4.png", 4)
    st = stempel()
    st.save(os.path.join(OUT, "stempel.png"))
    preview(st, "stempel_x4.png", 4, (220, 210, 190, 255))
    print("gen_tempat_ui: wrote", OUT)


if __name__ == "__main__":
    main()
