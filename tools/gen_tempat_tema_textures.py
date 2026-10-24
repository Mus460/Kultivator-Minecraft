"""Generates the entity textures of the themed towns (DESIGN 38.T.9, module 38TV-M): ships and boats, creatures,
mechanisms and gates of the five themed towns. Everything is ours: procedural textures, nothing is downloaded.

Writes (assets/kultivator/...):
  textures/entity/tempat/tema/<name>.png          band textures of the cuboid models in client/dunia/tempat/tema/model
  textures/entity/tempat/tema/<name>_pancar.png   glow textures (same layout, transparent where nothing glows), drawn
                                                  through the vanilla eyes render type (no post chain)

Layout rule (TemaBendaRenderer javadoc): a texture is a stack of horizontal bands of height B; every band is ONE
material that repeats seamlessly over the full width W (entity textures sample with REPEAT), so a box may overflow
the right edge, but each box's depth + height must stay within one band. A few bands are decals: they are painted at
the exact uv of one plate box (u0 = depth, v0 = depth of that plate; see the comment of each decal painter). The band
lists below are in the same order as the band constants of the matching Java renderer.

Palette 38.T.9: biru laut 2E5E8C, pasir D8B878, lumpur 8A6A48, putih kapur EDE8DC, genteng hitam 2A2A30, emas puncak
E0B040, hitam iblis 1C1418, merah darah 7A1020 (+ the 38.O.0 palette).

Run with: python tools/gen_tempat_tema_textures.py [preview_dir]   (writes; 2x/4x preview sheets into preview_dir)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator")
OUT = os.path.join(ROOT, "textures", "entity", "tempat", "tema")

PAL = {
    "laut": "2E5E8C", "pasir": "D8B878", "lumpur": "8A6A48", "kapur": "EDE8DC", "genteng": "2A2A30", "emas": "E0B040",
    "iblis": "1C1418", "darah": "7A1020", "lak": "8E1F1F", "giok": "5FA88C", "kayu_tua": "5A3A22", "kayu_muda": "9C7448",
    "besi": "7C8288", "perunggu": "8C6A3A", "kertas": "E8DDC0", "tinta": "1E1A18", "sutra_biru": "3A5A8C",
    "kain_kasar": "8A7A60", "tulang": "D8CFB8",
}


# ================================================================================================ colour helpers

def rgb(h):
    h = PAL.get(h, h).lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, f):
    return tuple(max(0.0, min(255.0, v * f)) for v in c[:3])


def stable(*parts):
    h = 2166136261
    for ch in repr(parts):
        h = ((h ^ ord(ch)) * 16777619) & 0xFFFFFFFF
    return h


class Field:
    """Tileable value noise over a W x B band (periodic in both directions) plus a per-pixel jitter source."""

    def __init__(self, seed, w, h):
        self.w, self.h = w, h
        self.r = random.Random(seed)
        self.cache = {}

    def _grid(self, cell):
        key = cell
        if key not in self.cache:
            nx, ny = max(1, self.w // cell), max(1, self.h // cell)
            self.cache[key] = (nx, ny, [[self.r.random() for _ in range(nx)] for _ in range(ny)])
        return self.cache[key]

    def at(self, x, y, cell):
        nx, ny, g = self._grid(cell)
        fx, fy = (x / cell) % nx, (y / cell) % ny
        x0, y0 = int(fx), int(fy)
        x1, y1 = (x0 + 1) % nx, (y0 + 1) % ny
        tx, ty = fx - x0, fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
        b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
        return a + (b - a) * ty

    def fbm(self, x, y, cell=16, octaves=3):
        v, amp, tot = 0.0, 1.0, 0.0
        for _ in range(octaves):
            v += self.at(x, y, max(1, cell)) * amp
            tot += amp
            amp *= 0.5
            cell //= 2
        return v / tot

    def j(self):
        return self.r.random()


def wav(f, x, freq):
    """A phase that is periodic over the band width: about {freq} radians per pixel, rounded to whole cycles over W."""
    k = max(1, round(freq * f.w / (2 * math.pi)))
    return 2 * math.pi * k * x / f.w


# ================================================================================================ atlas

ATLASES = {}


class Atlas:
    """A band texture: W wide, bands of B pixels; painter(x, y, f) gets band-local x in 0..W-1, y in 0..B-1 and a Field."""

    def __init__(self, name, w, b):
        self.name, self.w, self.b = name, w, b
        self.bands = []
        ATLASES[name] = self

    def band(self, label, painter):
        self.bands.append((label, painter))
        return len(self.bands) - 1

    def render(self):
        img = Image(self.w, self.b * len(self.bands))
        for i, (label, p) in enumerate(self.bands):
            f = Field(stable(self.name, i, label), self.w, self.b)
            for y in range(self.b):
                for x in range(self.w):
                    c = p(x, y, f)
                    a = c[3] if len(c) > 3 else 255
                    img.set(x, i * self.b + y, (c[0], c[1], c[2], a))
        return img


def glow_of(img, b, keep=(), glints=(), thresh=600, glint_alpha=190):
    """The glow texture of a rendered atlas: bands in keep copied (their alpha kept), bands in glints keep only their
    brightest pixels, everything else transparent."""
    out = Image(img.width, img.height)
    for y in range(img.height):
        band = y // b
        for x in range(img.width):
            r, g, bb, a = img.get(x, y)
            if band in keep:
                out.set(x, y, (r, g, bb, a))
            elif band in glints and r + g + bb > thresh and a > 0:
                out.set(x, y, (r, g, bb, glint_alpha))
    return out


# ================================================================================================ material painters
# every painter returns (r, g, b) or (r, g, b, a); x wraps over W, so anything periodic must use f.at / x % period

def planks(base, plank=4, seam=0.62, grain=0.9, nails=True, wear=0.0, vertical=False, butt=64):
    """Planks running along u (or v when vertical), seams every {plank} px, staggered butt joints every {butt} px,
    grain lines, nail heads at the joints, optional salt wear."""
    b = rgb(base)

    def p(x, y, f):
        u, v = (y, x) if vertical else (x, y)
        row = v // plank
        off = (row * 29) % butt
        ph = wav(f, u, 0.08 * grain) if not vertical else u * 0.08 * grain
        c = shade(b, 0.84 + 0.18 * f.at(u + row * 7, v * 4, 16) + 0.1 * math.sin(ph + row * 2.1 + 3 * f.at(u, v, 8)))
        c = shade(c, 0.95 + 0.1 * ((row * 37) % 5) / 4.0)
        if v % plank == plank - 1:
            c = shade(c, seam)
        elif v % plank == 0:
            c = shade(c, 1.08)
        if (u + off) % butt == 0:
            c = shade(c, seam + 0.08)
        if nails and (u + off) % butt in (2, butt - 3) and v % plank == plank // 2:
            c = (60, 56, 54)
        if wear and f.at(u + 11, v + 5, 4) > 1.0 - wear:
            c = mix(c, (214, 206, 190), 0.35)
        return shade(c, 0.97 + f.j() * 0.05)
    return p


def grain(base, period=6, dark=0.78, seed_cell=8):
    """End-to-end wood along u (poles, masts, beams): long streaks with knots."""
    b = rgb(base)

    def p(x, y, f):
        s = 0.5 + 0.5 * math.sin(y * 6.283 / period + 2.5 * f.at(x, y, seed_cell))
        c = shade(b, dark + (1.0 - dark) * s + 0.12 * f.at(x, y * 2, 4))
        if f.at(x + 3, y + 1, 2) > 0.9:
            c = shade(c, 0.62)  # knot
        return shade(c, 0.97 + f.j() * 0.05)
    return p


def bamboo(base="A8B05A", light="D8C878", node=32):
    def p(x, y, f):
        c = mix(rgb(base), rgb(light), 0.5 + 0.5 * math.sin(y * 0.9))
        k = x % node
        if k in (0, 1):
            c = shade(rgb("7C7A3A"), 1.0 if k == 0 else 1.25)
        if y % 4 == 0:
            c = shade(c, 0.9)
        return shade(c, 0.95 + f.j() * 0.07)
    return p


def metal(base, rivet=0, brushed=True, rust=0.0, light=1.0):
    b = shade(rgb(base), light)

    def p(x, y, f):
        c = shade(b, 0.78 + 0.36 * f.at(x, y, 4))
        if brushed and (y + x // 8) % 3 == 0:
            c = shade(c, 1.07)
        if rust and f.at(x + 9, y + 3, 4) > 1.0 - rust:
            c = mix(c, (128, 66, 32), 0.7)
        if rivet and x % rivet in (rivet // 2,) and y % 8 == 4:
            c = shade(c, 1.45)
        if f.j() < 0.02:
            c = shade(c, 1.35)
        return shade(c, 0.96 + f.j() * 0.05)
    return p


def chain(base="5A5C62"):
    """Oval links along u: alternating face-on links (ring) and edge-on links (bar)."""
    b = rgb(base)

    def p(x, y, f):
        k, v = x % 8, y % 4
        face = (x // 8) % 2 == 0
        if face:
            ring = (k in (0, 7) and v in (1, 2)) or (v in (0, 3) and 1 <= k <= 6)
            c = shade(b, 1.25 if ring and v == 0 else (1.0 if ring else 0.35))
        else:
            c = shade(b, 1.1 if v in (1, 2) else 0.4)
        return shade(c, 0.95 + f.j() * 0.08)
    return p


def rope(base="BCA070"):
    b = rgb(base)

    def p(x, y, f):
        d = (x + y) % 4
        c = b if d in (1, 2) else (shade(b, 0.74) if d == 0 else shade(b, 0.58))
        return shade(c, 0.94 + f.j() * 0.08)
    return p


def cloth(base, weave=True, fold=16, stripes=None, dark=0.8):
    b = rgb(base)

    def p(x, y, f):
        c = shade(b, 0.85 + 0.25 * f.at(x, y, 8))
        if stripes:
            col, period, width = stripes
            if (x % period) < width:
                c = mix(c, rgb(col), 0.85)
        if weave and (x + y) % 2 == 0:
            c = shade(c, 0.93)
        if fold and y % fold == fold - 1:
            c = shade(c, dark)
        return shade(c, 0.96 + f.j() * 0.06)
    return p


def paper(base="E8DDC0", lines=False):
    b = rgb(base)

    def p(x, y, f):
        c = shade(b, 0.95 + 0.08 * f.at(x, y, 8) + f.j() * 0.04)
        if lines and x % 4 == 1 and 3 < y % 32 < 29 and (y * 7 + x) % 5 != 0:
            c = (40, 34, 30)
        return c
    return p


def stone(base, dark=0.62, pits=0.82, vein=None, moss=None, cell=4, blocks=0):
    b = rgb(base)

    def p(x, y, f):
        c = shade(b, 0.7 + 0.5 * f.fbm(x, y, cell * 4))
        if f.at(x + 3, y + 7, 2) > pits:
            c = shade(c, dark)
        if vein:
            v = abs(math.sin(wav(f, x, 0.35) + y * 0.2 + f.at(x, y, 8) * 4.0))
            if v < 0.09:
                c = mix(c, rgb(vein), 0.8)
        if moss and f.fbm(x + 40, y, 16) > 0.58:
            c = mix(c, rgb(moss), 0.7 * (f.fbm(x + 40, y, 16) - 0.58) / 0.42 + 0.3)
        if blocks and (y % blocks == 0 or (x + (y // blocks) * (blocks // 2 + 3)) % (blocks * 2) == 0):
            c = shade(c, 0.66)
        return shade(c, 0.95 + f.j() * 0.08)
    return p


def plain(base, amt=0.15):
    b = rgb(base)

    def p(x, y, f):
        return shade(b, 1.0 - amt / 2 + amt * f.at(x, y, 4) + f.j() * 0.04)
    return p


def lacquer(base, gloss=0.35):
    b = rgb(base)

    def p(x, y, f):
        g = max(0.0, math.sin(wav(f, x, 0.32) + y * 0.19)) ** 8
        c = mix(shade(b, 0.85 + 0.2 * f.at(x, y, 8)), (255, 236, 220), g * gloss)
        return shade(c, 0.97 + f.j() * 0.05)
    return p


def gold(base="E0B040"):
    def p(x, y, f):
        c = mix(shade(rgb(base), 0.7), shade(rgb(base), 1.15), f.at(x, y, 4))
        if (x + y) % 8 == 0:
            c = mix(c, (255, 246, 200), 0.6)
        return shade(c, 0.95 + f.j() * 0.07)
    return p


def brass():
    def p(x, y, f):
        c = mix(rgb("7A5A22"), rgb("D8AE58"), f.at(x, y, 4))
        if f.at(x * 3 % 64, y * 5 % 64, 4) > 0.84:
            c = mix(c, rgb("5A9C80"), 0.5)
        if y % 8 == 0:
            c = shade(c, 1.2)
        return shade(c, 0.95 + f.j() * 0.06)
    return p


def glow(core, rim, ring=0):
    """Light material: bright core, darker rim noise; ring > 0 draws concentric bands (lenses)."""
    def p(x, y, f):
        t = f.at(x, y, 8)
        c = mix(rgb(rim), rgb(core), 0.4 + 0.6 * t)
        if ring and (x + y) % ring == 0:
            c = shade(c, 1.2)
        return c
    return p


def fur(base, dark, light, flow=1.0, shag=0.0, shag_col=None, cell=8):
    """Fur strands running along v; shag > 0 overlays long dark tufts (camel winter coat)."""
    def p(x, y, f):
        strand = 0.5 + 0.5 * math.sin(wav(f, x, 1.7) + f.at(x, int(y * flow), 4) * 6.0)
        c = mix(rgb(dark), rgb(light), 0.25 + 0.6 * strand * f.at(x, y, cell))
        c = mix(rgb(base), c, 0.55)
        if shag:
            t = f.fbm(x, y * 0.25 + x % 3, 16)
            if t > 1.0 - shag:
                c = mix(c, rgb(shag_col or dark), 0.75)
        return shade(c, 0.94 + f.j() * 0.1)
    return p


def skin(base, amt=0.12):
    return plain(base, amt)


def chitin(base, edge, crack=None, plate=8, gloss=0.3):
    """Armour plates: rows of overlapping plates with a dark edge and a glossy highlight; crack draws glowing fissures."""
    b, e = rgb(base), rgb(edge)

    def p(x, y, f):
        k = y % plate
        c = mix(b, e, 0.15 + 0.6 * (k / plate) ** 2)
        if k == plate - 1:
            c = shade(e, 0.7)
        if k in (1, 2):
            c = mix(c, shade(e, 1.7), gloss * (0.6 if (x // 3) % 2 == 0 else 0.3))
        c = shade(c, 0.85 + 0.25 * f.at(x, y, 8))
        if crack and f.fbm(x + 30, y + 11, 16) > 0.52:
            v = abs(math.sin(wav(f, x, 0.21) + y * 0.37 + 4.0 * f.fbm(x, y, 16)))
            if v < 0.07:
                c = rgb(crack)
            elif v < 0.12:
                c = mix(c, rgb(crack), 0.45)
        return shade(c, 0.96 + f.j() * 0.06)
    return p


def scales(base, edge, glint, size=8, row_shift=True):
    """Fish/dragon scales: rows of arcs."""
    b, e, g = rgb(base), rgb(edge), rgb(glint)

    def p(x, y, f):
        row = y // size
        u = (x + (size // 2 if row_shift and row % 2 else 0)) % size
        v = y % size
        cx = size / 2.0
        d = ((u + 0.5 - cx) ** 2 + (v + 0.5) ** 2 * 0.9) ** 0.5 / (size * 0.62)
        c = mix(b, e, min(1.0, d ** 2))
        if d > 0.92:
            c = shade(e, 0.6)
        if v == 1 and abs(u + 0.5 - cx) < 1.2:
            c = mix(c, g, 0.7)
        return shade(c, 0.92 + 0.12 * f.at(x, y, 8))
    return p


def weave(a, b_col, cell=4):
    """Basket / mat weave: alternating over-under strips."""
    ca, cb = rgb(a), rgb(b_col)

    def p(x, y, f):
        over = ((x // cell) + (y // cell)) % 2 == 0
        k = (y % cell) if over else (x % cell)
        c = ca if over else cb
        c = shade(c, 0.78 + 0.3 * math.sin(k / cell * math.pi))
        return shade(c, 0.94 + 0.08 * f.at(x, y, 4) + f.j() * 0.04)
    return p


def leaves(base="4E7A34", light="8AB858", alpha_holes=True):
    """Crossed-plant leaves: dense blades with transparent gaps (cutout)."""
    def p(x, y, f):
        blade = abs(math.sin(wav(f, x, 0.55) + f.at(x, y, 8) * 3.0))
        if alpha_holes and blade > 0.62 and y < 30:
            return (0, 0, 0, 0)
        c = mix(rgb(base), rgb(light), 0.3 + 0.7 * (1 - blade))
        c = shade(c, 0.8 + 0.3 * (1 - y / 64.0))
        return shade(c, 0.94 + f.j() * 0.08)
    return p


def bone(base="D8CFB8"):
    def p(x, y, f):
        c = shade(rgb(base), 0.82 + 0.22 * f.at(x, y, 8))
        if f.at(x + 5, y + 2, 2) > 0.86:
            c = shade(c, 0.7)
        if abs(math.sin(wav(f, x, 0.12) + y * 0.5)) < 0.05:
            c = shade(c, 0.85)
        return shade(c, 0.96 + f.j() * 0.05)
    return p


def sand(base="D8B878"):
    def p(x, y, f):
        c = shade(rgb(base), 0.82 + 0.26 * f.fbm(x, y, 16))
        if f.j() < 0.08:
            c = shade(c, 1.15 if f.j() < 0.5 else 0.8)
        if abs(math.sin(wav(f, x, 0.3) + y * 0.9 + f.at(x, y, 8) * 2)) < 0.07:
            c = shade(c, 0.9)  # wind ripples
        return c
    return p


def water(base="2E5E8C"):
    def p(x, y, f):
        c = shade(rgb(base), 0.8 + 0.35 * f.fbm(x, y, 16))
        if abs(math.sin(wav(f, x, 0.25) + 3 * f.at(x, y, 8))) < 0.08:
            c = mix(c, (220, 236, 244), 0.5)
        return c
    return p


def transparent():
    def p(x, y, f):
        return (0, 0, 0, 0)
    return p


def over(base_painter, decal):
    """Composites decal(x, y, f) -> None or rgba over a base painter (decal bands)."""
    def p(x, y, f):
        c = base_painter(x, y, f)
        d = decal(x, y, f, c)
        return c if d is None else d
    return p


def in_rect(x, y, u0, v0, w, h):
    return u0 <= x < u0 + w and v0 <= y < v0 + h


def ellipse(x, y, cx, cy, rx, ry):
    return ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2


def glow_pick(img, b, pick):
    """A glow texture keeping only the pixels where pick(band, x, y_in_band, rgba) is true."""
    out = Image(img.width, img.height)
    for y in range(img.height):
        for x in range(img.width):
            c = img.get(x, y)
            if c[3] > 0 and pick(y // b, x, y % b, c):
                out.set(x, y, c)
    return out


def blob(x, y, cx, cy, r, f, rough=0.25):
    """A rough round blob test (clouds, tears)."""
    d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5 / r
    return d < 1.0 - rough + rough * 2 * f.at(x * 2, y * 2, 4)


def line_dist(x, y, x0, y0, x1, y1):
    px, py = x + 0.5, y + 0.5
    dx, dy = x1 - x0, y1 - y0
    ll = dx * dx + dy * dy
    t = 0.0 if ll == 0 else max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / ll))
    qx, qy = x0 + t * dx, y0 + t * dy
    return ((px - qx) ** 2 + (py - qy) ** 2) ** 0.5


# ================================================================================================ 38.T.9.1 ships (JungRenderer)
# bands (B 64, W 256): 0 lambung, 1 geladak, 2 haluan (+bow and cabin decals), 3 layar, 4 tiang, 5 bambu, 6 besi,
# 7 rantai, 8 peti, 9 lentera, 10 tali, 11 cat, 12 panji, 13 buritan (+transom decal)

def bow_decal(pirate):
    """North face of the bow transom: u 8..70, v 8..48 (62 x 40). Cabin front: u 140..224, v 40..64 (84 x 24)."""
    def d(x, y, f, c):
        if in_rect(x, y, 8, 8, 62, 40):
            u, v = x - 8, y - 8
            if pirate:
                col = shade(rgb("2A2224"), 0.85 + 0.2 * f.at(x, y, 4))
                if v < 3:
                    col = rgb("5A1418")
                for ex in (14.0, 48.0):
                    e = ellipse(u, v, ex, 15.0, 7.5, 5.0)
                    if e < 1.0:
                        col = mix(rgb("C81E1E"), rgb("FF8A3A"), 1.0 - e)
                        if ellipse(u, v, ex, 15.0, 2.0, 4.0) < 1.0:
                            col = (20, 10, 10)
                    elif e < 1.35:
                        col = rgb("7A1020")
                if 26 <= v < 34:
                    top = 26 + abs((u % 6) - 3) * 2.5
                    if v >= top:
                        col = rgb("E8E0CC") if v < 32 else rgb("5A1418")
                if v >= 36:
                    col = (20, 16, 18)
                return col
            col = shade(rgb("A82A1E"), 0.9 + 0.15 * f.at(x, y, 4))
            if v < 2 or (v < 4 and u % 4 < 2):
                col = rgb("E0B040")
            if 4 <= v < 6 and (u // 3) % 3 != 1:
                col = rgb("C9A24A")
            for ex in (14.0, 48.0):
                e = ellipse(u, v, ex, 16.0, 8.0, 5.5)
                if e < 1.0:
                    col = (240, 238, 228)
                    if ellipse(u, v, ex + 1.0, 16.0, 3.6, 3.6) < 1.0:
                        col = (24, 22, 26)
                        if ellipse(u, v, ex - 0.2, 14.6, 1.2, 1.2) < 1.0:
                            col = (250, 250, 250)
                elif e < 1.4:
                    col = (30, 24, 20)
                if abs(v - (9.0 + abs(u - ex) * 0.18)) < 0.8 and abs(u - ex) < 9:
                    col = rgb("E0B040")
            if 26 <= v < 32:
                col = shade(rgb("2E7A4A"), 0.9 + 0.15 * f.at(x, y, 4))
                if v in (26, 31):
                    col = rgb("E0B040")
            elif 32 <= v < 36:
                col = (232, 228, 214) if (u // 4) % 2 == 0 else rgb("A82A1E")
            elif v >= 36:
                col = (36, 30, 28)
            return col
        if in_rect(x, y, 140, 40, 84, 24):
            u, v = x - 140, y - 40
            col = shade(rgb("2A2224") if pirate else rgb("8E1F1F"), 0.88 + 0.18 * f.at(x, y, 4))
            if u < 2 or u >= 82 or v < 2:
                col = rgb("5A1418") if pirate else rgb("C9A24A")
            for wx in (8, 58):
                if in_rect(u, v, wx, 5, 18, 12):
                    lu, lv = u - wx, v - 5
                    if lu in (0, 17) or lv in (0, 11):
                        col = (40, 30, 24)
                    elif (lu + lv) % 4 == 0 or (lu - lv) % 4 == 0:
                        col = (58, 40, 28)
                    else:
                        col = rgb("D83A2A") if pirate else rgb("E8D8A8")
            if in_rect(u, v, 34, 4, 16, 20):
                col = (52, 30, 22) if not pirate else (20, 14, 14)
                if u - 34 in (0, 15) or v == 4:
                    col = rgb("C9A24A") if not pirate else rgb("5A1418")
                if (u - 34) in (4, 11) and (v - 4) % 5 == 2:
                    col = rgb("E0B040")
            return col
        return None
    return d


def transom_decal(pirate):
    """South face of the transom: u 104..196, v 6..54 (92 x 48)."""
    def d(x, y, f, c):
        if not in_rect(x, y, 104, 6, 92, 48):
            return None
        u, v = x - 104, y - 6
        if pirate:
            col = shade(rgb("221A1C"), 0.85 + 0.2 * f.at(x, y, 4))
            if u < 3 or u >= 89 or v < 3 or v >= 45:
                col = rgb("7A1020")
            body = ellipse(u, v, 44.0, 24.0, 26.0, 7.0)
            if body < 1.0:
                col = mix(rgb("9A1A20"), rgb("D83A2A"), 1 - body)
            if 10 < v < 18 and 40 < u < 52 and (v - 10) > abs(u - 46) * 1.3:
                col = rgb("B82424")
            if 66 < u < 78 and abs(v - 24) < (u - 66) * 0.9:
                col = rgb("B82424")
            if ellipse(u, v, 26.0, 22.0, 1.5, 1.5) < 1.0:
                col = (250, 240, 220)
            if 18 < u < 34 and v == 27 and u % 2 == 0:
                col = (240, 232, 214)  # teeth
            return col
        col = shade(rgb("8E1F1F"), 0.9 + 0.15 * f.at(x, y, 4))
        if u < 3 or u >= 89 or v < 3 or v >= 45:
            col = rgb("E0B040") if (u + v) % 3 else rgb("A87A24")
        elif in_rect(u, v, 8, 8, 76, 32):
            col = mix(rgb("3A6AA8"), rgb("8AB8E0"), (v - 8) / 32.0)
            for cx, cy, r in ((20, 30, 7), (30, 32, 6), (62, 31, 8), (72, 29, 5), (50, 14, 4)):
                if blob(u, v, cx, cy, r, f, 0.2):
                    col = (240, 240, 236) if blob(u, v, cx, cy - 1, r * 0.8, f, 0.1) else (200, 210, 224)
            sd = ellipse(u, v, 46.0, 20.0, 8.0, 8.0)
            if sd < 1.0:
                col = mix(rgb("D82A1A"), rgb("FF7A3A"), 1 - sd)
            elif sd < 1.25:
                col = rgb("E0B040")
            if ellipse(u, v, 28.0, 15.0, 4.0, 2.2) < 1.0 or line_dist(u, v, 24.5, 15.0, 19.0, 11.0) < 0.8:
                col = (246, 246, 240)
            if ellipse(u, v, 18.6, 10.6, 1.2, 1.2) < 1.0:
                col = rgb("C81E1E")
            if line_dist(u, v, 28.0, 14.0, 34.0, 8.0) < 1.2 or line_dist(u, v, 29.0, 15.0, 36.0, 12.0) < 1.0:
                col = (30, 30, 34) if line_dist(u, v, 33.0, 9.0, 36.0, 8.0) < 2.5 else (238, 238, 234)
        elif v >= 40:
            col = rgb("2E7A4A") if (u // 6) % 2 == 0 else rgb("C9A24A")
        return col
    return d


def sail_tikar():
    """Woven mat sails: tan matting, stitched vertical seams every 32 px, darker edges, weather stains."""
    w = weave("C8A468", "A88448", cell=3)

    def p(x, y, f):
        c = shade(w(x, y, f), 0.9 + 0.16 * f.fbm(x, y, 16))
        if x % 32 in (0, 1):
            c = shade(c, 0.72)
            if y % 3 == 0:
                c = (90, 70, 40)
        if y % 32 in (0, 1, 30, 31):
            c = shade(c, 0.7)
        if f.at(x + 21, y + 5, 8) > 0.78:
            c = mix(c, (112, 84, 52), 0.35)
        return c
    return p


def sail_jung():
    """Blue-white sails: alternating 16 px cloth strips, stitched seams."""
    def p(x, y, f):
        c = rgb("2E5E8C") if (x // 16) % 2 == 1 else rgb("EDE8DC")
        c = shade(c, 0.9 + 0.14 * f.at(x, y, 8))
        if x % 16 == 0:
            c = shade(c, 0.75)
        if (x + y) % 2 == 0:
            c = shade(c, 0.95)
        if y % 32 in (0, 31):
            c = shade(c, 0.72)
        return c
    return p


def sail_torn():
    """Pirate sails: black and dark red patched cloth, ragged tears (transparent), charred edges."""
    def p(x, y, f):
        c = rgb("6A1418") if f.at(x, y, 16) > 0.55 else rgb("2A1E20")
        c = shade(c, 0.85 + 0.25 * f.at(x, y, 4))
        hole = f.fbm(x + 17, y * 2 + 3, 16)
        if hole > 0.7:
            return (0, 0, 0, 0)
        if hole > 0.64:
            c = (70, 40, 26)
        if x % 32 == 0:
            c = shade(c, 0.65)
        return c
    return p


def pennant(pirate):
    """Masthead streamer, or the pirate's black flag with a skull spread over its three segments (u 0..37, v 0..21)."""
    def p(x, y, f):
        if not pirate:
            c = rgb("C81E1E")
            if y in (0, 1, 4, 5) or x % 10 == 0:
                c = rgb("E0B040")
            return shade(c, 0.9 + 0.14 * f.at(x, y, 4))
        c = shade(rgb("161214"), 0.85 + 0.2 * f.at(x, y, 4))
        u, v = x % 64, y
        if u < 38 and v < 22:
            jaw = in_rect(u, v, 14, 11, 8, 4)
            if ellipse(u, v, 18.0, 8.5, 5.5, 5.0) < 1.0 or jaw:
                c = (226, 220, 206)
                if ellipse(u, v, 15.8, 8.5, 1.4, 1.6) < 1.0 or ellipse(u, v, 20.2, 8.5, 1.4, 1.6) < 1.0:
                    c = (20, 16, 16)
                if jaw and u % 2 == 0 and v >= 13:
                    c = (60, 50, 46)
            for a, b in (((8, 14), (28, 20)), ((28, 14), (8, 20))):
                if line_dist(u, v, a[0], a[1], b[0], b[1]) < 1.1:
                    c = (220, 214, 200)
        if v > 14 and f.fbm(x + 3, y + 9, 8) > 0.74:
            return (0, 0, 0, 0)
        return c
    return p


def lantern_paper(base="C8281E"):
    def p(x, y, f):
        c = mix(rgb(base), rgb("FF9A50"), 0.3 + 0.4 * f.at(x, y, 8))
        if x % 6 == 0:
            c = rgb("E0B040")
        if y % 32 in (0, 31):
            c = rgb("5A3A22")
        return c
    return p


def build_ships():
    for name, pirate, jung in (("kapal_dagang", False, False), ("kapal_dagang_jung", False, True), ("kapal_bajak_laut", True, False)):
        a = Atlas(name, 256, 64)
        a.band("lambung", planks("2E2624" if pirate else "7A3A22", plank=4, wear=0.0 if pirate else 0.18))
        a.band("geladak", planks("4A3A30" if pirate else "B8915A", plank=5, seam=0.7))
        a.band("haluan", over(planks("2A2224" if pirate else "9A2A1E", plank=4, nails=False), bow_decal(pirate)))
        a.band("layar", sail_torn() if pirate else (sail_jung() if jung else sail_tikar()))
        a.band("tiang", grain("3A2A20" if pirate else "5A3A22", period=5))
        a.band("bambu", bamboo())
        a.band("besi", metal("44484E", rust=0.3))
        a.band("rantai", chain())
        a.band("peti", planks("8A5A30", plank=5, butt=16))
        a.band("lentera", lantern_paper("8A1018" if pirate else "C8281E"))
        a.band("tali", rope())
        a.band("cat", lacquer("5A1418" if pirate else "8E1F1F"))
        a.band("panji", pennant(pirate))
        a.band("buritan", over(planks("221A1C" if pirate else "6A1E18", plank=4, nails=False), transom_decal(pirate)))


# ================================================================================================ 38.T.9.1 sampan (PerahuNelayanRenderer)
# bands (B 64, W 128): 0 lambung, 1 anyaman, 2 kayu, 3 tali, 4 jala, 5 cat (+race ribbons), 6 lentera

def fishing_net(base="5A5A48", knot="9A9270", mesh=4):
    """A heaped fishing net: knotted diamond mesh over the dark folds below it, cork floats every 16 px."""
    def p(x, y, f):
        fold = 0.55 + 0.45 * f.fbm(x, y, 16)
        c = shade(rgb(base), 0.45 + 0.35 * fold)
        a, b = (x + y) % mesh, (x - y) % mesh
        if a == 0 or b == 0:
            c = shade(rgb(knot), 0.75 + 0.35 * fold)
            if a == 0 and b == 0:
                c = shade(rgb(knot), 1.15)
        if x % 16 in (6, 7, 8) and y % 16 in (3, 4):
            c = rgb("C88A3A") if y % 16 == 3 else rgb("9A6428")
        return shade(c, 0.95 + f.j() * 0.08)
    return p


def boat_band(race):
    """The painted strake: white-green-red bands with a wave scroll; the race boat adds tied red ribbons."""
    def p(x, y, f):
        v = y % 32
        if v < 6:
            c = rgb("EDE8DC")
        elif v < 12:
            c = rgb("2E7A4A")
            if abs(((x % 16) - 8) * 0.5 - (v - 9)) < 0.8:
                c = rgb("EDE8DC")
        elif v < 18:
            c = rgb("A82A1E")
        else:
            c = rgb("5A3A22")
        c = shade(c, 0.88 + 0.18 * f.at(x, y, 4))
        if race and x % 24 in range(10, 14) and v < 20:
            c = mix(rgb("D81E1E"), rgb("FF6A4A"), 0.3 + 0.3 * f.at(x, y, 2))
            if x % 24 in (10, 13):
                c = shade(c, 0.7)
        return c
    return p


def build_sampan():
    for name, race in (("perahu_nelayan", False), ("perahu_nelayan_lomba", True)):
        a = Atlas(name, 128, 64)
        a.band("lambung", planks("6A4A2E" if not race else "7A3A22", plank=4, wear=0.25))
        a.band("anyaman", weave("B89A5A", "8A7040", cell=3))
        a.band("kayu", grain("7A5A38", period=4))
        a.band("tali", rope())
        a.band("jala", fishing_net())
        a.band("cat", boat_band(race))
        a.band("lentera", lantern_paper("D8401E" if not race else "C81E1E"))


# ================================================================================================ 38.T.9.3 lighthouse lamp (ApiSuarRenderer)
# bands (B 32, W 128): 0 kuningan, 1 kaca, 2 lensa, 3 api, 4 atap, 5 batu

def leaded_glass(tint, crack=False):
    """Leaded panes: 5 x 6 px lights in lead cames, a pale tint with sky reflections, optional spider cracks."""
    def p(x, y, f):
        u, v = x % 5, y % 6
        if u == 0 or v == 0:
            return shade(rgb("3A3C40"), 0.9 + 0.2 * f.at(x, y, 2))
        c = mix(rgb(tint), (236, 244, 248), 0.25 + 0.35 * max(0.0, math.sin(wav(f, x, 0.2) - y * 0.3)))
        c = shade(c, 0.9 + 0.12 * f.at(x, y, 4))
        if crack:
            for cx, cy in ((22, 12), (70, 20), (104, 8)):
                dx = ((x - cx + 64) % 128 - 64) + 0.5
                dy = y + 0.5 - cy
                ang = math.atan2(dy, dx)
                dist = math.hypot(dx, dy)
                if dist < 12 and (abs(math.sin(ang * 3.0)) < 0.08 or abs(dist - 5.0) < 0.4):
                    c = (250, 252, 255)
        return c
    return p


def lens(kind):
    """Fresnel lens drum: concentric prism rings (bright edges) around a bull's-eye boss per 16 px face; kind 0 cracked,
    1 new, 2 Mutiara Fajar (blue-gold)."""
    tint = {0: "C8D0C8", 1: "E8F0E8", 2: "9AC0F0"}[kind]
    edge = {0: "8A928A", 1: "B8C8B8", 2: "E0B040"}[kind]

    def p(x, y, f):
        d = math.hypot((x % 16) + 0.5 - 8.0, (y % 16) + 0.5 - 8.0)
        fr = (d % 3.0) / 3.0
        c = mix(rgb(tint), (255, 255, 250), (1.0 - fr) ** 2 * 0.8)
        if fr > 0.8:
            c = shade(rgb(edge), 0.85 + 0.2 * f.at(x, y, 4))
        if d < 1.6:
            c = (255, 252, 232)
        if kind == 2 and (x + y) % 11 == 0:
            c = mix(c, rgb("E0B040"), 0.6)
        if kind == 0 and abs(math.sin(wav(f, x, 0.11) + y * 0.45 + 3 * f.at(x, y, 8))) < 0.06:
            c = (70, 74, 70)
        return shade(c, 0.95 + f.j() * 0.05)
    return p


def flame(core="FFF4C0", mid="FFB040", rim="D8401E"):
    def p(x, y, f):
        t = (y % 32) / 31.0
        n = f.at(x, y, 4)
        c = mix(rgb(core), rgb(mid), min(1.0, t * 1.4 + n * 0.3))
        if t > 0.6:
            c = mix(c, rgb(rim), (t - 0.6) / 0.4)
        return c
    return p


def verdigris():
    """Weathered copper roof: green patina streaks running down over brown copper, seams every 8 px, a gilt drip edge."""
    def p(x, y, f):
        streak = f.at(x, y * 0.25, 4)
        c = mix(rgb("8A5A32"), rgb("5FA88C"), 0.35 + 0.6 * streak)
        if x % 8 == 0:
            c = shade(c, 0.7)
        if y % 32 in (0, 1):
            c = shade(rgb("E0B040"), 0.85)
        return shade(c, 0.9 + 0.15 * f.at(x, y, 2))
    return p


def build_lamp():
    for name, kind in (("api_suar_retak", 0), ("api_suar", 1), ("api_suar_fajar", 2)):
        a = Atlas(name, 128, 32)
        a.band("kuningan", brass() if kind != 2 else gold("D8AE58"))
        a.band("kaca", leaded_glass("C8D8C8" if kind != 2 else "B0C8F0", crack=kind == 0))
        a.band("lensa", lens(kind))
        a.band("api", flame() if kind != 2 else flame("F4FAFF", "8AC8FF", "3A6AC8"))
        a.band("atap", verdigris())
        a.band("batu", stone("8A8478", blocks=8))


# ================================================================================================ 38.T.9.3 treadwheel crane (DerekDermagaRenderer)
# bands (B 64, W 128): 0 kayu, 1 papan, 2 tali, 3 besi, 4 peti, 5 batu

def crate(base="8A5A30", band_col="44484E", stamp=True):
    """A cargo crate face (16 px): 4 planks, a dark frame, corner irons with rivets and a red tally stamp."""
    pl = planks(base, plank=4, butt=16, nails=False)

    def p(x, y, f):
        c = pl(x, y, f)
        u, v = x % 16, y % 16
        if u in (0, 15) or v in (0, 15):
            c = shade(rgb(base), 0.62)
        if (u < 3 or u > 12) and (v < 3 or v > 12):
            c = shade(rgb(band_col), 0.9 + 0.2 * f.at(x, y, 2))
            if (u, v) in ((1, 1), (14, 1), (1, 14), (14, 14)):
                c = (180, 176, 168)
        if stamp and 5 <= u <= 10 and 5 <= v <= 10 and (x // 16) % 3 == 1:
            if u in (5, 10) or v in (5, 10) or u == v or u + v == 15:
                c = mix(c, rgb("A82A1E"), 0.8)
        return c
    return p


def build_crane():
    a = Atlas("derek_dermaga", 128, 64)
    a.band("kayu", grain("6A4A2E", period=5))
    a.band("papan", planks("8A6A42", plank=3, butt=24, wear=0.2))
    a.band("tali", rope("B8A070"))
    a.band("besi", metal("44484E", rust=0.18, rivet=8))
    a.band("peti", crate())
    a.band("batu", stone("8A8478", blocks=10, moss="4E6A3A"))


# ================================================================================================ 38.T.9.3 slipway hull (KapalGalanganRenderer)
# bands (B 64, W 256): 0 kayu (fresh timber), 1 lambung, 2 gading, 3 bambu, 4 papan, 5 batu, 6 tali, 7 layar, 8 tiang,
# 9 cat, 10 besi, 11 hiasan (bow eyes + stern panel decals)

def fresh_timber(base="C89A62"):
    """New-cut timber: pale with long grain, adze marks, chalk lines and the shipwright's charcoal marks."""
    g = grain(base, period=7, dark=0.84)

    def p(x, y, f):
        c = g(x, y, f)
        if (x * 3 + y * 7) % 53 == 0:
            c = shade(c, 0.8)  # adze bite
        if y % 32 == 16 and (x // 4) % 2 == 0:
            c = mix(c, (236, 230, 220), 0.6)  # chalk line
        if x % 64 in (20, 21) and 6 < y % 32 < 14:
            c = (52, 44, 40)  # charcoal mark
        return c
    return p


def hull_paint(look):
    """Painted planking: awan = white with a blue cloud-scroll strake, sekte = red lacquer with a gold line."""
    base = planks("E4E0D6" if look == "awan" else "8E1F1F", plank=4, nails=False, wear=0.08)

    def p(x, y, f):
        c = base(x, y, f)
        v = y % 64
        if look == "awan" and 44 <= v < 56:
            c = mix(rgb("3A6AA8"), rgb("6A9AD0"), f.at(x, y, 4))
            k = (x % 24) - 12
            if abs(math.hypot(k, v - 50) - 4.5) < 0.9 and k < 3:
                c = rgb("EDE8DC")
        if look == "sekte" and v in (44, 45, 54, 55):
            c = rgb("E0B040")
        return c
    return p


def decals(look):
    """Band 11: lacquer with the bow eyes (u 8..70, v 8..48) and the stern panel (u 104..196, v 6..54)."""
    lac = lacquer("2E5E8C" if look == "awan" else "8E1F1F")

    def p(x, y, f):
        c = lac(x, y, f)
        if in_rect(x, y, 8, 8, 62, 40):
            u, v = x - 8, y - 8
            c = shade(rgb("EDE8DC") if look == "awan" else rgb("A82A1E"), 0.9 + 0.12 * f.at(x, y, 4))
            if v < 2:
                c = rgb("E0B040")
            for ex in (14.0, 48.0):
                e = ellipse(u, v, ex, 16.0, 8.0, 5.5)
                if e < 1.0:
                    c = (240, 238, 228)
                    if ellipse(u, v, ex + 1.0, 16.0, 3.6, 3.6) < 1.0:
                        c = (24, 22, 26)
                        if ellipse(u, v, ex - 0.2, 14.6, 1.2, 1.2) < 1.0:
                            c = (250, 250, 250)
                elif e < 1.4:
                    c = rgb("2E5E8C") if look == "awan" else (30, 24, 20)
            if v >= 28:
                c = rgb("3A6AA8") if look == "awan" else rgb("2E7A4A")
                if v in (28, 39):
                    c = rgb("E0B040")
                if look == "awan" and abs(math.hypot((u % 12) - 6, v - 34) - 3.5) < 0.8:
                    c = rgb("EDE8DC")
        elif in_rect(x, y, 104, 6, 92, 48):
            u, v = x - 104, y - 6
            if u < 3 or u >= 89 or v < 3 or v >= 45:
                c = rgb("E0B040") if (u + v) % 3 else rgb("A87A24")
            else:
                sky = look == "awan"
                c = mix(rgb("6A9AD0" if sky else "5A1418"), rgb("CFE0F0" if sky else "8E1F1F"), (v - 3) / 42.0)
                for cx, cy, r in ((22, 30, 8), (36, 33, 7), (60, 30, 9), (74, 27, 6)):
                    if blob(u, v, cx, cy, r, f, 0.2):
                        c = (244, 244, 240) if sky else rgb("C9A24A")
                if sky:
                    # a crane flying over the clouds
                    if line_dist(u, v, 34, 16, 46, 12) < 1.0 or line_dist(u, v, 46, 12, 58, 16) < 1.0 or ellipse(u, v, 46, 13, 3.0, 1.5) < 1.0:
                        c = (250, 250, 246)
                    if ellipse(u, v, 50.5, 12.0, 0.9, 0.9) < 1.0:
                        c = rgb("C81E1E")
                else:
                    # the sect's sun disc
                    sd = ellipse(u, v, 46.0, 18.0, 9.0, 9.0)
                    if sd < 1.0:
                        c = mix(rgb("E0B040"), rgb("FFF0B0"), 1 - sd)
        return c
    return p


def build_galangan():
    for look in ("awan", "sekte"):
        a = Atlas("kapal_galangan_" + look, 256, 64)
        a.band("kayu", fresh_timber())
        a.band("lambung", hull_paint(look))
        a.band("gading", grain("B08050", period=6, dark=0.8))
        a.band("bambu", bamboo())
        a.band("papan", planks("A08458", plank=6, butt=54, wear=0.15))
        a.band("batu", stone("7E786C", blocks=12, moss="5A6A3A"))
        a.band("tali", rope())
        a.band("layar", sail_jung() if look == "awan" else sail_tikar())
        a.band("tiang", grain("8A6A42", period=5))
        a.band("cat", lacquer("2E5E8C" if look == "awan" else "8E1F1F"))
        a.band("besi", metal("44484E", rust=0.15))
        a.band("hiasan", decals(look))


# ================================================================================================ 38.T.9.2 camel (UntaKafilahRenderer)
# bands (B 64, W 128): 0 bulu, 1 lebat, 2 kaki, 3 kepala, 4 kulit, 5 kain, 6 karung, 7 kayu, 8 logam, 9 tali, 10 mata

def shag(base, dark, light, length=10, density=0.55):
    """Long shaggy winter hair: overlapping hanging locks (dark roots, lighter tips) with ragged transparent-free ends."""
    def p(x, y, f):
        lock = (x + int(4 * f.at(x, 0, 8))) % 5
        tip = ((y + (x * 7) % length) % length) / float(length)
        c = mix(rgb(dark), rgb(light), 0.2 + 0.7 * tip * (0.6 + 0.4 * f.at(x, y, 4)))
        c = mix(rgb(base), c, density + 0.3)
        if lock == 0:
            c = shade(c, 0.72)
        if tip > 0.92:
            c = shade(c, 0.85)
        return shade(c, 0.93 + f.j() * 0.1)
    return p


def rug(ground="8E1F1F", field="2E3A6A", border="E0B040"):
    """A desert knotted rug: border with a running zig-zag, medallions in the field, fringe-free (carpet roll and saddle
    blanket)."""
    def p(x, y, f):
        v = y % 32
        u = x % 32
        if v < 4 or v >= 28:
            c = rgb(border) if (u + v) % 4 < 2 else rgb(ground)
        elif v < 7 or v >= 25:
            c = rgb(ground)
            if abs((u % 8) - 4) == (v % 3):
                c = rgb("EDE8DC")
        else:
            c = rgb(field)
            d = abs(u - 16) + abs(v - 16)
            if d < 7:
                c = rgb(ground) if d > 3 else rgb(border)
            elif d == 9 or d == 10:
                c = rgb("5FA88C")
        if (x + y) % 2 == 0:
            c = shade(c, 0.92)
        return shade(c, 0.94 + 0.1 * f.at(x, y, 4))
    return p


def burlap(base="A89060"):
    def p(x, y, f):
        c = shade(rgb(base), 0.85 + 0.2 * f.at(x, y, 8))
        if x % 2 == 0 and y % 2 == 0:
            c = shade(c, 0.85)
        if (x + y * 3) % 7 == 0:
            c = shade(c, 1.1)
        if x % 32 in (30, 31) or y % 32 in (0,):
            c = shade(c, 0.7)  # seams
        if (x // 32) % 2 == 1 and 10 < y % 32 < 20 and 10 < x % 32 < 22 and ((x + y) % 5 == 0):
            c = rgb("6A1418")  # merchant's mark
        return c
    return p


def camel_eye():
    def p(x, y, f):
        c = (26, 20, 18)
        if (x % 4, y % 4) == (1, 0):
            c = (230, 226, 214)
        if y % 4 == 3:
            c = (70, 52, 36)  # heavy lashes
        return c
    return p


def build_camel():
    for name, winter in (("unta_kafilah", False), ("unta_kafilah_dingin", True)):
        a = Atlas(name, 128, 64)
        coat = "B8925E" if not winter else "8E6A44"
        a.band("bulu", fur(coat, "7A5A36", "D8B880", flow=0.5, shag=0.08 if not winter else 0.3, shag_col="5A3E24"))
        a.band("lebat", shag("8A6440" if not winter else "5A3E24", "4A3220", "B08A5A" if not winter else "8A6A44", length=12 if winter else 8,
                             density=0.55 if not winter else 0.7))
        a.band("kaki", fur("C8A270" if not winter else "9A7650", "8A6A44", "E0C898", flow=0.3))
        a.band("kepala", fur("C09A66" if not winter else "967048", "8A6A44", "D8BC8C", flow=0.6))
        a.band("kulit", plain("5A4436", 0.2))
        a.band("kain", rug() if not winter else rug("2E5E8C", "7A1020", "EDE8DC"))
        a.band("karung", burlap())
        a.band("kayu", grain("7A5A38", period=4))
        a.band("logam", brass())
        a.band("tali", rope("A88A58"))
        a.band("mata", camel_eye())


# ================================================================================================ 38.T.9.2 scorpion king (KalajengkingRajaRenderer)
# bands (B 64, W 128): 0 kitin, 1 perut, 2 ekor, 3 capit, 4 kaki, 5 sengat, 6 mata, 7 mulut

def build_scorpion():
    for name, sand in (("kalajengking_raja", False), ("kalajengking_pasir", True)):
        a = Atlas(name, 128, 64)
        if not sand:
            base, edge, crack = "2A1A1C", "7A1020", "FF8A2A"
        else:
            base, edge, crack = "D8C49A", "8A6A48", None
        a.band("kitin", chitin(base, edge, crack=crack, plate=10, gloss=0.35))
        a.band("perut", chitin(base, edge, crack=crack, plate=6, gloss=0.3))
        a.band("ekor", chitin(base, edge, crack=crack, plate=6, gloss=0.4))
        a.band("capit", chitin(shade_hex(base, 1.1), edge, crack=crack, plate=14, gloss=0.5))
        a.band("kaki", chitin(base, shade_hex(edge, 0.8), plate=5, gloss=0.25))
        a.band("sengat", sting(sand))
        a.band("mata", glow("FFE0A0", "FF6A1A") if not sand else glow("C8F0FF", "3AA8E0"))
        a.band("mulut", chitin("3A2224" if not sand else "8A6A48", "1C1418", plate=3, gloss=0.1))


def shade_hex(h, k):
    c = shade(rgb(h), k)
    return "%02X%02X%02X" % tuple(int(v) for v in c)


def sting(sand):
    """The bulb: glossy dark red venom sac with a vein net; the hook (u >= 50) darkens to a bright tip."""
    def p(x, y, f):
        c = mix(rgb("5A0A14" if not sand else "B8885A"), rgb("B81E2A" if not sand else "E0C090"), f.at(x, y, 8))
        if abs(math.sin(wav(f, x, 0.3) + y * 0.6 + 4 * f.at(x, y, 8))) < 0.08:
            c = rgb("FF5A3A") if not sand else rgb("7A5A38")
        if x % 128 >= 70:
            c = mix(c, rgb("FFC070") if not sand else rgb("3A2A20"), 0.5 + 0.5 * (y % 16) / 16.0)
        return shade(c, 0.95 + f.j() * 0.06)
    return p


# ================================================================================================ 38.T.9.3 oasis mechanisms
# timba_sumur (B 32, W 64): 0 kayu, 1 batu, 2 tali, 3 ember, 4 bata
# temuan_pasir (B 16, W 64): 0 emas, 1 mural, 2 perunggu, 3 pasir
# pintu_pasir (B 64, W 128): 0 pelat (+relief decal u 6..38, v 6..54), 1 kusen, 2 pasir
# pilar_rune (B 32, W 64): 0 batu, 1 rune, 2 kepala, 3 segel, 4 cincin
# batu_ukur_air (B 32, W 64): 0 kapur, 1 perunggu, 2 lumut

def glyph_on(x, y, cw=6, ch=8, seed=0):
    """True where an old-script glyph stroke lies: cells of cw x ch with 2-4 strokes chosen by a hash (strokes: top bar,
    mid bar, bottom bar, left stem, right stem, centre stem, two diagonals, a dot)."""
    gx, gy = x // cw, y // ch
    u, v = x % cw, y % ch
    if u == 0 or v == 0:
        return False
    h = stable("glyph", gx, gy, seed)
    strokes = []
    for k in range(9):
        if (h >> k) & 1:
            strokes.append(k)
    if len(strokes) < 2:
        strokes += [3, 1]
    w, hh = cw - 1, ch - 1
    for s in strokes[:4]:
        if s == 0 and v == 1 and 1 <= u <= w - 1:
            return True
        if s == 1 and v == hh // 2 + 1 and 1 <= u <= w - 1:
            return True
        if s == 2 and v == hh - 1 and 1 <= u <= w - 1:
            return True
        if s == 3 and u == 1 and 1 <= v <= hh - 1:
            return True
        if s == 4 and u == w - 1 and 1 <= v <= hh - 1:
            return True
        if s == 5 and u == w // 2 and 2 <= v <= hh - 2:
            return True
        if s == 6 and abs((u - 1) * (hh - 2) - (v - 1) * (w - 2)) < w - 1 and 1 <= u <= w - 1 and 1 <= v <= hh - 1:
            return True
        if s == 7 and u == w // 2 and v == 2:
            return True
    return False


def sandstone(base="C8A878", carve=None, glyphs=False, glyph_col=None):
    st = stone(base, dark=0.75, pits=0.86, cell=4)

    def p(x, y, f):
        c = st(x, y, f)
        # horizontal bedding of sandstone
        c = shade(c, 0.94 + 0.08 * math.sin(y * 0.7 + 3 * f.at(x, y, 16)))
        if glyphs and glyph_on(x, y):
            c = rgb(glyph_col) if glyph_col else shade(c, 0.62)
        return c
    return p


def mud_brick():
    def p(x, y, f):
        row = y // 5
        off = 6 if row % 2 else 0
        c = shade(rgb("A07A52"), 0.85 + 0.22 * f.at(x, y, 4))
        if y % 5 == 4 or (x + off) % 12 == 0:
            c = shade(rgb("C8B090"), 0.9 + 0.1 * f.at(x, y, 2))  # lime mortar
        elif f.j() < 0.05:
            c = shade(c, 1.2)  # straw fleck
        return c
    return p


def bucket():
    v = planks("7A5A38", plank=3, butt=64, nails=False, vertical=True)

    def p(x, y, f):
        c = v(x, y, f)
        if y % 16 in (2, 3, 12, 13):
            c = shade(rgb("44484E"), 0.9 + 0.2 * f.at(x, y, 2))
        return c
    return p


def coin_gold():
    def p(x, y, f):
        c = mix(rgb("A87A24"), rgb("F0D070"), f.at(x, y, 2))
        u, v = x % 8, y % 8
        if u in (3, 4) and v in (3, 4):
            c = (60, 44, 20)  # the square hole
        elif (u in (1, 6) and 1 <= v <= 6) or (v in (1, 6) and 1 <= u <= 6):
            c = shade(c, 0.78)  # rim
        elif (u + v * 3) % 5 == 0:
            c = shade(c, 0.7)  # worn characters
        return shade(c, 0.95 + f.j() * 0.08)
    return p


def mural_paint():
    """A fragment of the old Kuil Air mural: ochre ground, a blue water band, red-robed figures carrying jars, cracks."""
    def p(x, y, f):
        c = shade(rgb("D8B878"), 0.85 + 0.2 * f.at(x, y, 4))
        u = x % 16
        if y % 16 >= 11:
            c = mix(rgb("2E5E8C"), rgb("6A9AD0"), 0.5 + 0.5 * math.sin(x * 0.8 + y))
        elif 3 <= u <= 6 and 2 <= y % 16 <= 10:
            c = rgb("A82A1E") if y % 16 > 4 else rgb("3A2A20")  # a figure: head, robe
            if y % 16 == 3 and u in (4, 5):
                c = rgb("C89A6A")
        elif 7 <= u <= 9 and 4 <= y % 16 <= 7:
            c = rgb("7A5A38")  # the jar
        if abs(math.sin(wav(f, x, 0.4) + y * 0.9 + 3 * f.at(x, y, 4))) < 0.06:
            c = shade(c, 0.55)
        if f.at(x + 7, y, 2) > 0.85:
            c = mix(c, (236, 224, 196), 0.6)  # flaked paint
        return c
    return p


def bronze_map():
    def p(x, y, f):
        c = mix(rgb("7A5A30"), rgb("B08A4A"), f.at(x, y, 4))
        if f.at(x + 3, y + 9, 4) > 0.7:
            c = mix(c, rgb("5FA88C"), 0.6)
        if abs(math.sin(x * 0.35 + 2.0 * math.sin(y * 0.4))) < 0.12:
            c = shade(c, 0.6)  # engraved route
        if (x * 5 + y * 3) % 23 == 0:
            c = rgb("E0B040")  # oasis dots
        return c
    return p


def relief_slab():
    """The hall door relief, on the slab's north face (u 6..38, v 6..54): the spring goddess pouring a jar, water
    flowing down into three lotus, border of glyphs."""
    base = sandstone("C8A878")

    def d(x, y, f, c):
        if not in_rect(x, y, 6, 6, 32, 48):
            return None
        u, v = x - 6, y - 6
        col = base(x, y, f)
        if u < 2 or u >= 30 or v < 2 or v >= 46:
            return shade(col, 0.7)
        if u < 5 or u >= 27:
            return shade(col, 0.6) if glyph_on(u, v, 3, 5, 7) else col
        light = shade(col, 1.3)
        dark = shade(col, 0.48)
        col = shade(col, 0.86)
        # halo and head
        if abs(math.hypot(u - 16, v - 9) - 5.5) < 0.7:
            return dark
        if ellipse(u, v, 16, 9, 2.6, 3.0) < 1.0:
            return light
        # robe body, arms to the jar
        if 11 <= u <= 21 and 12 <= v <= 30 and abs(u - 16) < 3 + (v - 12) * 0.25:
            return light if (u + v) % 7 else dark
        if line_dist(u, v, 18, 15, 23, 18) < 1.0 or line_dist(u, v, 14, 15, 21, 20) < 1.0:
            return light
        if ellipse(u, v, 23, 19, 2.4, 2.8) < 1.0:
            return dark
        # pouring water to the lotus
        if 23 <= u <= 25 and 21 <= v <= 38 and (v + u) % 3 != 0:
            return shade(col, 1.2)
        for lx in (9, 16, 24):
            if ellipse(u, v, lx, 41, 3.5, 2.0) < 1.0:
                return light if v < 41 else dark
        if v in (34, 35) and (u // 2) % 2 == 0:
            return dark
        return col
    return over(base, d)


def carved_jamb():
    base = sandstone("BFA070")

    def d(x, y, f, c):
        u, v = x % 32, y % 64
        if v > 50 and abs(math.hypot(u - 16, v - 57) - 4.5) < 0.8:
            return shade(c, 0.6)  # sun disc
        if 4 < u < 12 and glyph_on(u, v, 4, 6, 3):
            return shade(c, 0.62)
        if u in (0, 1, 30, 31):
            return shade(c, 0.7)
        return None
    return over(base, d)


def water_band():
    def p(x, y, f):
        c = mix(rgb("6AB8E0"), rgb("D8F0FF"), 0.3 + 0.5 * max(0.0, math.sin(wav(f, x, 0.3) + y * 0.8)))
        if (x + y * 2) % 9 == 0:
            c = (250, 252, 255)
        return shade(c, 0.94 + 0.08 * f.at(x, y, 4))
    return p


def rune_bronze():
    def p(x, y, f):
        c = mix(rgb("6A4A22"), rgb("A8804A"), f.at(x, y, 4))
        if glyph_on(x, y, 5, 6, 11):
            c = rgb("6AF0C8")
        return c
    return p


def build_oasis_mech():
    a = Atlas("timba_sumur", 64, 32)
    a.band("kayu", grain("8A6A44", period=4))
    a.band("batu", stone("9A8A70", dark=0.7))
    a.band("tali", rope("B8A070"))
    a.band("ember", bucket())
    a.band("bata", mud_brick())

    a = Atlas("temuan_pasir", 64, 16)
    a.band("emas", coin_gold())
    a.band("mural", mural_paint())
    a.band("perunggu", bronze_map())
    a.band("pasir", sand())

    a = Atlas("pintu_pasir", 128, 64)
    a.band("pelat", relief_slab())
    a.band("kusen", carved_jamb())
    a.band("pasir", sand())

    a = Atlas("pilar_rune", 64, 32)
    a.band("batu", sandstone("A88A62"))
    a.band("rune", sandstone("8A7050", glyphs=True, glyph_col="5AE0B8"))
    a.band("kepala", sandstone("B89A70", glyphs=False))
    a.band("segel", water_band())
    a.band("cincin", rune_bronze())

    a = Atlas("batu_ukur_air", 64, 32)
    a.band("kapur", over(stone("E4DCC8", dark=0.8, pits=0.88), lambda x, y, f, c: shade(c, 0.66) if glyph_on(x, y, 4, 6, 5) and x % 16 < 6 else None))
    a.band("perunggu", bronze_map())
    a.band("lumut", stone("B8B098", moss="4E7A44", dark=0.75))


# ================================================================================================ 38.T.9.1 / 38.T.9.3 Kota Air
# perahu_kanal(_hitam) (B 64, W 128): 0 lambung, 1 atap, 2 kayu, 3 cat, 4 lentera, 5 kain, 6 peti, 7 tali
# perahu_naga_<warna> (B 64, W 128): 0 sisik, 1 lambung, 2 kepala, 3 tanduk, 4 kayu, 5 genderang, 6 mata, 7 baju,
#                                    8 kulit, 9 rambut
# alat_tenun(_merah) (B 32, W 64): 0 kayu, 1 benang (cutout), 2 kain, 3 bambu, 4 tali, 5 sisir (cutout)

def awning(a="2E5E8C", b="EDE8DC", period=8):
    def p(x, y, f):
        c = rgb(a) if (x // period) % 2 == 0 else rgb(b)
        c = shade(c, 0.88 + 0.16 * f.at(x, y, 8))
        if y % 32 == 31:
            c = shade(c, 0.7)
        return c
    return p


def market_goods():
    """Crate sides on the left half, heaped greens and radishes on the right half (u >= 60)."""
    cr = crate("9A6A3A", stamp=False)

    def p(x, y, f):
        if x % 128 < 60:
            return cr(x, y, f)
        c = mix(rgb("3E7A2E"), rgb("8AC858"), f.at(x, y, 2))
        if f.at(x + 5, y + 3, 4) > 0.72:
            c = (232, 226, 210) if f.j() < 0.6 else (200, 60, 80)  # radish / red onion
        return shade(c, 0.9 + f.j() * 0.12)
    return p


def build_canal():
    for name, black in (("perahu_kanal", False), ("perahu_kanal_hitam", True)):
        a = Atlas(name, 128, 64)
        a.band("lambung", planks("2A2426" if black else "5A4430", plank=4, wear=0.0 if black else 0.22, nails=not black))
        a.band("atap", weave("2E2A28", "1A1818", cell=3))
        a.band("kayu", grain("3A302A" if black else "6A4A2E", period=4))
        a.band("cat", boat_band(False) if not black else lacquer("1C1418"))
        a.band("lentera", lantern_paper("C8281E"))
        a.band("kain", awning("A82828", "E8D8A8", 6) if not black else awning("2A2A30", "5A1418", 6))
        a.band("peti", market_goods())
        a.band("tali", rope())


TEAM = {
    "merah": ("C81E1E", "E0B040", "8E1F1F"),
    "hijau": ("2E7A4A", "E0B040", "1E5A36"),
    "biru": ("2E5E8C", "EDE8DC", "1E3E64"),
    "hitam": ("2A2A30", "C81E1E", "141418"),
}


def dragon_drum():
    def p(x, y, f):
        c = lacquer("A82828")(x, y, f)
        if y % 32 in (1, 2, 29, 30) and x % 4 == 0:
            c = rgb("E0B040")  # brass studs
        if 6 < y % 32 < 26 and abs(math.hypot((x % 32) - 16, (y % 32) - 16) - 7) < 0.9:
            c = rgb("E0B040")  # the painted ring on the head
        return c
    return p


def team_shirt(main, trim):
    def p(x, y, f):
        c = shade(rgb(main), 0.86 + 0.2 * f.at(x, y, 8))
        if y % 16 in (0, 15):
            c = rgb(trim)
        if (x + y) % 2 == 0:
            c = shade(c, 0.94)
        return c
    return p


def build_dragon_boat():
    for warna, (main, trim, dark) in TEAM.items():
        a = Atlas("perahu_naga_" + warna, 128, 64)
        a.band("sisik", scales(main, dark, trim, size=8))
        a.band("lambung", over(scales(dark, "141012", main, size=6), lambda x, y, f, c: shade(rgb(trim), 0.9) if y % 64 in (0, 1) else None))
        a.band("kepala", scales(main, dark, trim, size=6))
        a.band("tanduk", gold(trim if trim != "EDE8DC" else "E0D8C0"))
        a.band("kayu", grain("8A6A42", period=4))
        a.band("genderang", dragon_drum())
        a.band("mata", glow("FFF0A0", "FF9A20"))
        a.band("baju", team_shirt(main, trim))
        a.band("kulit", skin("C89A72", 0.18))
        a.band("rambut", team_shirt("1C1A1E", trim))


def warp_threads():
    def p(x, y, f):
        if x % 2 == 1:
            return (0, 0, 0, 0)
        return shade(rgb("E8E0CC"), 0.85 + 0.2 * f.at(x, y, 4))
    return p


def silk(main, figure):
    """Woven silk: a fine twill with a repeating cloud-scroll brocade figure."""
    def p(x, y, f):
        c = shade(rgb(main), 0.9 + 0.12 * ((x + y) % 3) / 2.0)
        u, v = x % 16, y % 16
        if abs(math.hypot(u - 8, v - 8) - 4) < 0.8 and u < 11:
            c = rgb(figure)
        if (u, v) in ((12, 8), (13, 7), (13, 9)):
            c = rgb(figure)
        return shade(c, 0.96 + f.j() * 0.05)
    return p


def reed():
    def p(x, y, f):
        if x % 3 == 2:
            return (0, 0, 0, 0)
        return shade(rgb("B8A060"), 0.85 + 0.25 * f.at(x, y, 2))
    return p


def build_loom():
    for name, red in (("alat_tenun", False), ("alat_tenun_merah", True)):
        a = Atlas(name, 64, 32)
        a.band("kayu", grain("7A4A2A", period=4))
        a.band("benang", warp_threads())
        a.band("kain", silk("2E5E8C", "EDE8DC") if not red else silk("A82020", "E0B040"))
        a.band("bambu", bamboo())
        a.band("tali", rope("D8C8A0"))
        a.band("sisir", reed())


# ================================================================================================ 38.T.9.2 / 38.T.9.3 Kota Gunung
# kereta_gantung (B 64, W 128): 0 kayu, 1 kisi (cutout lattice), 2 genteng, 3 besi, 4 rantai, 5 emas, 6 papan
# katrol (B 64, W 64): 0 anyaman, 1 bambu, 2 tali, 3 kayu, 4 besi, 5 papan
# roda_katrol (B 32, W 128): 0 kayu, 1 besi, 2 pelek, 3 tali, 4 balok, 5 batu
# batu_runtuh_<jenis> (B 64, W 64): 0 batu, 1 pecahan
# herba_tebing(_awan) (B 16, W 64): 0 daun (cutout), 1 tanah, 2 kuncup, 3 batang
# palang (B 32, W 64): 0 kayu, 1 rantai, 2 batu, 3 papan
# tali_turun (B 16, W 32): 0 tali, 1 simpul, 2 karung
# jerat (B 16, W 64): 0 besi (+runes), 1 gigi, 2 jimat, 3 rantai
# kera_gunung(_putih) (B 32, W 64): 0 bulu, 1 bulu_muda, 2 muka, 3 mata, 4 ekor, 5 buntalan, 6 janggut

def lattice(base="6A3A22"):
    """A window lattice of laths (cutout): a 6 px grid with a broken 'ice-ray' diagonal in every other cell."""
    g = grain(base, period=3, dark=0.8)

    def p(x, y, f):
        u, v = x % 6, y % 6
        cell = (x // 6 + y // 6) % 2
        lath = u in (0, 1) or v in (0, 1)
        if cell == 1 and abs(u - v) < 1:
            lath = True
        if not lath:
            return (0, 0, 0, 0)
        return g(x, y, f)
    return p


def roof_tiles(base="3E4A48"):
    """Glazed barrel tiles: vertical rows of half-round tiles with highlights, a darker overlap every 6 px."""
    def p(x, y, f):
        u = x % 5
        c = shade(rgb(base), 0.7 + 0.45 * math.sin(u / 4.0 * math.pi))
        if y % 6 == 5:
            c = shade(c, 0.62)
        if u == 2 and y % 6 in (1, 2):
            c = mix(c, (220, 236, 232), 0.35)
        return shade(c, 0.94 + 0.08 * f.at(x, y, 4))
    return p


def grooved_rim():
    g = grain("7A5A38", period=4)

    def p(x, y, f):
        c = g(x, y, f)
        if y % 32 in (9, 10, 11, 12):
            c = shade(c, 0.55)  # the rope groove
        return c
    return p


def rock(base, vein=None, moss=None, lichen=None):
    s = stone(base, dark=0.6, pits=0.8, vein=vein, moss=moss, cell=4)

    def p(x, y, f):
        c = s(x, y, f)
        if lichen and f.at(x + 13, y + 7, 4) > 0.8:
            c = mix(c, rgb(lichen), 0.6)
        # facets: broad light and shadow planes
        c = shade(c, 0.85 + 0.3 * f.at(x, y, 32))
        return c
    return p


def broken_face(base):
    def p(x, y, f):
        c = shade(rgb(base), 1.15 + 0.2 * f.at(x, y, 4))
        if f.j() < 0.1:
            c = shade(c, 0.8)
        return c
    return p


def herb_leaves(base, light):
    """Crossed-plane leaves (cutout): lanceolate blades fanning from the bottom centre of each 12 px plane, each
    widest at a third of its length, with a midrib (five blades: the gaps read as separate leaves at 12 px)."""
    blades = ((6.0, 0.5, 0.9), (1.5, 2.5, 0.8), (10.5, 2.0, 0.8), (0.0, 8.0, 0.7), (12.0, 7.5, 0.7))

    def p(x, y, f):
        u = (x % 12) + 0.5
        v = (y % 16) + 0.5
        if v > 14.5:
            return (0, 0, 0, 0)
        best = None
        for tx, ty, w in blades:
            bx, by = 6.0, 14.5
            dx, dy = tx - bx, ty - by
            ll = dx * dx + dy * dy
            t = max(0.0, min(1.0, ((u - bx) * dx + (v - by) * dy) / ll))
            qx, qy = bx + t * dx, by + t * dy
            d = math.hypot(u - qx, v - qy)
            half = w * math.sin(min(1.0, t * 1.5) * math.pi * 0.5) * (1.0 - t) * 1.5 + 0.2
            if d < half:
                k = d / half
                if best is None or k < best[0]:
                    best = (k, t)
        if best is None:
            return (0, 0, 0, 0)
        k, t = best
        c = mix(rgb(base), rgb(light), 0.25 + 0.5 * t + 0.2 * f.at(x, y, 2))
        if k < 0.25:
            c = shade(c, 0.78)
        return c
    return p


def buds(core, rim):
    def p(x, y, f):
        c = mix(rgb(rim), rgb(core), 0.4 + 0.6 * f.at(x, y, 2))
        if (x + y) % 4 == 0:
            c = shade(c, 1.15)
        return c
    return p


def warning_tablet():
    """Red lacquer tablet with a gilt border and a bold 'closed' sign (crossed bars in a ring)."""
    lac = lacquer("A82020")

    def p(x, y, f):
        c = lac(x, y, f)
        u, v = x % 32, y % 32
        if u in (1, 12) or v in (1, 8):
            if 1 <= u <= 12 and 1 <= v <= 8:
                c = rgb("E0B040")
        if 2 <= u <= 11 and 2 <= v <= 7:
            if abs(math.hypot(u - 6.5, v - 4.5) - 2.4) < 0.6 or abs((u - 6.5) - (v - 4.5)) < 0.6:
                c = (240, 232, 214)
        return c
    return p


def snare_iron():
    m = metal("2E2C2E", rust=0.2)

    def p(x, y, f):
        c = m(x, y, f)
        if glyph_on(x, y, 4, 5, 9) and (x // 16) % 2 == 0:
            c = rgb("E03A2A")
        return c
    return p


def talisman_paper():
    def p(x, y, f):
        c = shade(rgb("E8C850"), 0.92 + 0.1 * f.at(x, y, 4))
        if x % 8 in (3, 4) and 1 <= y % 16 <= 14 and glyph_on(x, y, 2, 3, 4):
            c = rgb("B81E1E")
        return c
    return p


def bundle_cloth():
    def p(x, y, f):
        if x % 64 >= 30:
            return mix(rgb("A82020"), rgb("D84030"), f.at(x, y, 2))  # the red knot
        c = shade(rgb("E8E0CC"), 0.85 + 0.2 * f.at(x, y, 4))
        if (x + y) % 6 == 0:
            c = rgb("3A6AA8")  # blue cloth pattern
        return c
    return p


def build_gunung():
    a = Atlas("kereta_gantung", 128, 64)
    a.band("kayu", grain("6A3A22", period=5))
    a.band("kisi", lattice())
    a.band("genteng", roof_tiles())
    a.band("besi", metal("3C4046", rust=0.12, rivet=8))
    a.band("rantai", chain("4A4C52"))
    a.band("emas", gold())
    a.band("papan", planks("8A5A34", plank=5, butt=40))

    a = Atlas("katrol", 64, 64)
    a.band("anyaman", weave("B8A060", "8A7440", cell=3))
    a.band("bambu", bamboo())
    a.band("tali", rope("B8A070"))
    a.band("kayu", grain("6A4A2E", period=4))
    a.band("besi", metal("44484E", rust=0.2))
    a.band("papan", planks("8A6A42", plank=4, butt=22))

    a = Atlas("roda_katrol", 128, 32)
    a.band("kayu", grain("7A5A38", period=5))
    a.band("besi", metal("3C4046", rust=0.15, rivet=6))
    a.band("pelek", grooved_rim())
    a.band("tali", rope("A88A58"))
    a.band("balok", grain("5A3E26", period=6, dark=0.78))
    a.band("batu", stone("7E7A72", blocks=10, moss="4E6A3A"))

    for jenis, base, kw in (("andesit", "8A8A86", {}), ("granit", "9A7A6A", {"vein": "D8C8B8"}),
                            ("berlumut", "6E6E66", {"moss": "4E7A34", "lichen": "B8C068"})):
        a = Atlas("batu_runtuh_" + jenis, 64, 64)
        a.band("batu", rock(base, **kw))
        a.band("pecahan", broken_face(base))

    for name, awan in (("herba_tebing", False), ("herba_tebing_awan", True)):
        a = Atlas(name, 64, 16)
        a.band("daun", herb_leaves("4E7A34", "8AC858") if not awan else herb_leaves("A8B8C0", "F0F8FF"))
        a.band("tanah", stone("5A5A44", moss="4E6A3A", dark=0.7))
        a.band("kuncup", buds("FFF0A0", "E07A20") if not awan else buds("F0FAFF", "6AB8F0"))
        a.band("batang", grain("4A6A2E" if not awan else "8A9AA8", period=2))

    a = Atlas("palang", 64, 32)
    a.band("kayu", grain("5A3E26", period=4))
    a.band("rantai", chain())
    a.band("batu", stone("8A8478", dark=0.66))
    a.band("papan", warning_tablet())

    a = Atlas("tali_turun", 32, 16)
    a.band("tali", rope("B89A62"))
    a.band("simpul", over(rope("A88A58"), lambda x, y, f, c: shade(c, 0.8) if (x + y) % 3 == 0 else None))
    a.band("karung", burlap("8A7450"))

    a = Atlas("jerat", 64, 16)
    a.band("besi", snare_iron())
    a.band("gigi", metal("B8BCC0", brushed=False, light=1.1))
    a.band("jimat", talisman_paper())
    a.band("rantai", chain("3A3C40"))

    for name, white in (("kera_gunung", False), ("kera_gunung_putih", True)):
        a = Atlas(name, 64, 32)
        if not white:
            a.band("bulu", fur("8A7A66", "5A4E40", "B0A08A", flow=0.6))
            a.band("bulu_muda", fur("A8987E", "7A6A56", "C8B8A0", flow=0.6))
        else:
            a.band("bulu", fur("E4E0D8", "B8B4AC", "FFFFFF", flow=0.6))
            a.band("bulu_muda", fur("EEEAE4", "C8C4BC", "FFFFFF", flow=0.6))
        a.band("muka", skin("D88A84" if not white else "E0A0A0", 0.16))
        a.band("mata", camel_eye())
        a.band("ekor", fur("7A6A58" if not white else "D8D4CC", "4A4034" if not white else "A8A49C", "A8987E" if not white else "F4F4F0", flow=0.3))
        a.band("buntalan", bundle_cloth())
        a.band("janggut", shag("E8E4DC", "B8B4AC", "FFFFFF", length=6, density=0.6))


# ================================================================================================ 38.T.9.3 Kota Iblis and shared mechanisms
# gerbang_tengkorak(_merah) (B 64, W 256): 0 tulang, 1 gigi, 2 besi, 3 relief_atas (decal u 4..52, v 4..52),
#                                          4 relief_bawah (decal), 5 paku, 6 gusi, 7 rantai
# pintu_arena (B 64, W 64): 0 besi, 1 batu, 2 rantai, 3 karat
# sel_tawanan (B 64, W 64): 0 kayu, 1 besi, 2 papan, 3 jerami
# segel_pintu (B 64, W 128): 0 pelat (decal u 2..34, v 2..50), 1 bingkai, 2 jimat, 3 retak
# tong_minyak (B 32, W 64): 0 papan, 1 besi, 2 segel, 3 minyak
# tuas_gerbang (B 32, W 64): 0 batu, 1 besi, 2 perunggu, 3 tulang
# tandu_<look> (B 64, W 128): 0 lak, 1 kayu, 2 atap, 3 tirai, 4 emas, 5 bantal, 6 galah, 7 rumbai
# pintu_air(_dok) (B 64, W 128): 0 papan, 1 batu, 2 kayu, 3 besi

def demon_face(base_painter, red):
    """Upper leaf relief (48 x 48 at u 4, v 4): the Demon Lord's face, horns sweeping up, heavy brows, glowing eyes,
    a fanged grin, a flame crown; raised parts lit from the top left, recesses dark."""
    def d(x, y, f, c):
        if not in_rect(x, y, 4, 4, 48, 48):
            return None
        u, v = x - 4, y - 4
        col = base_painter(x, y, f)
        hi = shade(col, 2.1) if not red else mix(col, (255, 190, 150), 0.35)
        lo = shade(col, 0.45)
        if u < 2 or u >= 46 or v < 2 or v >= 46:
            return shade(col, 1.25) if (u + v) % 2 else lo
        # horns
        for hx, dirn in ((12, -1), (36, 1)):
            for k in range(14):
                cx = hx + dirn * k * 0.55
                cy = 16 - k * 0.9
                if math.hypot(u - cx, v - cy) < 2.6 - k * 0.13:
                    return hi if (u + v + k) % 3 else col
        # head outline
        e = ellipse(u, v, 24, 26, 15, 17)
        if 0.86 < e < 1.0:
            return lo
        if e < 0.86:
            # brows
            if 15 <= v <= 18 and abs(abs(u - 24) - 7 + (v - 15) * 0.6) < 3.4 and abs(u - 24) > 2:
                return hi
            # eyes (glow red in the pancar texture)
            for ex in (17, 31):
                ee = ellipse(u, v, ex, 21, 3.2, 1.8)
                if ee < 1.0:
                    return (255, 40, 20) if ee < 0.55 else (200, 20, 10)
                if ee < 1.6:
                    return lo
            # nose ridge
            if 22 <= u <= 26 and 22 <= v <= 28:
                return hi if u <= 24 else col
            # the grin: dark mouth with fangs
            if 31 <= v <= 36 and abs(u - 24) < 10 - (v - 31) * 0.8:
                if v <= 33 and (u % 3 == 0) and abs(u - 24) < 9:
                    return (230, 222, 200)
                return (24, 10, 10)
            if (u * 7 + v * 3) % 11 == 0:
                return shade(col, 0.8)
            return col
        # flame crown and smoke background
        if v < 12 and abs(math.sin(u * 0.7 + v * 0.3)) > 0.82:
            return hi
        return shade(col, 0.75)
    return over(base_painter, d)


def chained_souls(base_painter):
    """Lower leaf relief: three wailing faces bound by a looping chain, a border of skulls."""
    def d(x, y, f, c):
        if not in_rect(x, y, 4, 4, 48, 48):
            return None
        u, v = x - 4, y - 4
        col = base_painter(x, y, f)
        hi = shade(col, 2.0)
        lo = shade(col, 0.45)
        if v >= 40:
            # skull border
            sx = u % 8
            if ellipse(sx, v - 40, 4, 3.5, 3.0, 3.0) < 1.0:
                if (sx in (2, 5)) and v - 40 in (3, 4):
                    return lo
                return hi
            return lo
        for cx, cy in ((10, 14), (24, 22), (38, 14)):
            e = ellipse(u, v, cx, cy, 5.5, 7.0)
            if e < 1.0:
                if ellipse(u, v, cx - 2, cy - 2, 1.0, 1.4) < 1.0 or ellipse(u, v, cx + 2, cy - 2, 1.0, 1.4) < 1.0:
                    return lo
                if ellipse(u, v, cx, cy + 3, 1.4, 2.4) < 1.0:
                    return lo
                return hi if u < cx else col
        # the chain: a sine loop of links
        cy = 30 + 6 * math.sin(u * 0.28)
        if abs(v - cy) < 1.6 and (u // 3) % 2 == 0:
            return hi
        if abs(v - cy) < 1.0:
            return lo
        return shade(col, 0.8)
    return over(base_painter, d)


def gum():
    def p(x, y, f):
        c = mix(rgb("4A0A12"), rgb("8A2030"), f.at(x, y, 4))
        if (x * 3 + y) % 13 == 0:
            c = shade(c, 1.3)
        return c
    return p


def teeth():
    def p(x, y, f):
        c = mix(rgb("C8BC98"), rgb("F0E8D0"), f.at(x, y, 4))
        if y % 64 > 50:
            c = mix(c, rgb("6A4A30"), 0.5)
        if abs(math.sin(wav(f, x, 0.2) + y * 0.4)) < 0.05:
            c = shade(c, 0.7)
        return c
    return p


def blood_stone():
    s = stone("5A4E4A", dark=0.6, blocks=10)

    def p(x, y, f):
        c = s(x, y, f)
        if f.fbm(x + 50, y * 0.5, 16) > 0.62:
            c = mix(c, rgb("5A0A10"), 0.55)
        return c
    return p


def tally_board():
    pl = planks("4A3A2E", plank=5, butt=32, nails=False)

    def p(x, y, f):
        c = pl(x, y, f)
        u, v = x % 64, y % 32
        if u < 30 and 1 <= v <= 4 and u % 3 == 0:
            c = (220, 210, 190)  # scratched tallies
        if u >= 30:
            c = mix(rgb("A81818"), rgb("D83030"), f.at(x, y, 2))  # the ransom tag
            if (u + v) % 5 == 0:
                c = rgb("1C1418")
        return c
    return p


def straw():
    def p(x, y, f):
        c = mix(rgb("A88A48"), rgb("D8C080"), abs(math.sin(x * 1.3 + y * 0.4 + 3 * f.at(x, y, 4))))
        return shade(c, 0.85 + 0.25 * f.j())
    return p


def seal_plate():
    """Seal plate decal (32 x 48 at u 2, v 2): dark bronze, a double ring of forbidden script around a sealed eye, the
    four trigrams at the corners; script pixels are the glowing violet-red E0306A."""
    base = metal("3A2E26", brushed=False)

    def d(x, y, f, c):
        if not in_rect(x, y, 2, 2, 32, 48):
            return None
        u, v = x - 2, y - 2
        col = base(x, y, f)
        r = math.hypot(u + 0.5 - 16, v + 0.5 - 24)
        if 11.5 < r < 14.5 and glyph_on(int(math.atan2(v - 24, u - 16) * 12 + 40), int(r * 2), 3, 3, 13):
            return rgb("E0306A")
        if abs(r - 15.2) < 0.6 or abs(r - 10.8) < 0.6:
            return rgb("C02050")
        e = ellipse(u, v, 16, 24, 6.0, 3.4)
        if e < 1.0:
            if ellipse(u, v, 16, 24, 2.0, 2.0) < 1.0:
                return rgb("FF6A8A")
            return shade(col, 1.4)
        if abs(v - 24) < 0.6 and abs(u - 16) < 9:
            return rgb("C02050")
        for cx, cy in ((4, 4), (28, 4), (4, 44), (28, 44)):
            if abs(u - cx) < 3 and abs(v - cy) < 3 and (v - cy + 3) % 2 == 0:
                return shade(col, 1.5)
        return col
    return over(base, d)


def crack_glow():
    def p(x, y, f):
        return mix(rgb("FF7A30"), rgb("FFF0C0"), f.at(x, y, 2))
    return p


def oil_black():
    def p(x, y, f):
        c = shade(rgb("141018"), 0.8 + 0.4 * f.at(x, y, 8))
        sheen = abs(math.sin(wav(f, x, 0.15) + y * 0.3 + 3 * f.at(x, y, 8)))
        if sheen < 0.1:
            c = mix(c, rgb("8A4AC8"), 0.7)
        elif sheen < 0.2:
            c = mix(c, rgb("3A6AC8"), 0.4)
        return c
    return p


def wax_seal():
    def p(x, y, f):
        if x % 64 >= 20:
            return talisman_paper()(x, y, f)
        c = mix(rgb("7A1010"), rgb("C82828"), f.at(x, y, 2))
        if (x % 4, y % 4) == (1, 1):
            c = shade(c, 1.3)
        return c
    return p


LOOK = {
    "dewi_laut": ("2E5E8C", "EDE8DC", "E0B040", "8AB8E0"),
    "pasir": ("A86A2A", "D8B878", "E0B040", "A82828"),
    "hitam": ("1C1418", "7A1020", "C8A060", "3A2A30"),
}


def tassel(col):
    def p(x, y, f):
        c = shade(rgb(col), 0.8 + 0.3 * ((x % 2) * 0.5 + f.at(x, y, 2) * 0.5))
        if y % 64 < 2:
            c = rgb("E0B040")
        return c
    return p


def build_iblis():
    for name, red in (("gerbang_tengkorak", False), ("gerbang_tengkorak_merah", True)):
        a = Atlas(name, 256, 64)
        iron = metal("2A262A", rust=0.12, rivet=12) if not red else lacquer("6A1018", gloss=0.25)
        a.band("tulang", bone("CFC2A0"))
        a.band("gigi", teeth())
        a.band("besi", iron)
        a.band("relief_atas", demon_face(iron, red))
        a.band("relief_bawah", chained_souls(iron))
        a.band("paku", metal("4A4448", brushed=False, light=1.2))
        a.band("gusi", gum())
        a.band("rantai", chain("3A363A"))

    a = Atlas("pintu_arena", 64, 64)
    a.band("besi", metal("34302E", rust=0.1))
    a.band("batu", blood_stone())
    a.band("rantai", chain("3A363A"))
    a.band("karat", metal("4A3A2E", rust=0.55))

    a = Atlas("sel_tawanan", 64, 64)
    a.band("kayu", grain("2E2420", period=4, dark=0.75))
    a.band("besi", metal("3A3632", rust=0.3))
    a.band("papan", tally_board())
    a.band("jerami", straw())

    a = Atlas("segel_pintu", 128, 64)
    a.band("pelat", seal_plate())
    a.band("bingkai", over(grain("2A1E1C", period=4), lambda x, y, f, c: rgb("7A1020") if glyph_on(x, y, 5, 7, 21) and x % 32 < 10 else None))
    a.band("jimat", talisman_paper())
    a.band("retak", crack_glow())

    a = Atlas("tong_minyak", 64, 32)
    a.band("papan", over(planks("2A201C", plank=4, vertical=True, nails=False), lambda x, y, f, c: oil_black()(x, y, f) if f.at(x, y * 0.3, 4) > 0.75 else None))
    a.band("besi", metal("2E2C2E", rust=0.25))
    a.band("segel", wax_seal())
    a.band("minyak", oil_black())

    a = Atlas("tuas_gerbang", 64, 32)
    a.band("batu", stone("3E3638", dark=0.6, blocks=8))
    a.band("besi", metal("2E2C2E", rust=0.15))
    a.band("perunggu", bronze_map())
    a.band("tulang", bone("D8CCB0"))

    for look, (lac, light, gold_c, cloth_c) in LOOK.items():
        a = Atlas("tandu_" + look, 128, 64)
        a.band("lak", lacquer(lac))
        a.band("kayu", over(grain(shade_hex(lac, 0.8) if look != "hitam" else "2A1E1C", period=4),
                            lambda x, y, f, c, g=gold_c: rgb(g) if (x % 16 in (7, 8) and y % 8 < 6) else None))
        a.band("atap", roof_tiles(lac if look != "pasir" else "8A4A22"))
        a.band("tirai", silk(cloth_c, light))
        a.band("emas", gold(gold_c) if look != "dewi_laut" else over(gold(gold_c), lambda x, y, f, c: (236, 240, 248) if x % 128 >= 70 and x % 128 < 90 else None))
        a.band("bantal", silk(light, gold_c))
        a.band("galah", lacquer(lac, gloss=0.5))
        a.band("rumbai", tassel(cloth_c if look != "pasir" else "A82828"))

    for name, dok in (("pintu_air", False), ("pintu_air_dok", True)):
        a = Atlas(name, 128, 64)
        wet = planks("5A4632" if not dok else "4A3A2C", plank=4, butt=56, wear=0.0)

        def leaf(x, y, f, wet=wet):
            c = wet(x, y, f)
            v = y % 64
            if v > 40:
                c = mix(c, rgb("3E5A30"), 0.45 + 0.3 * f.at(x, y, 4))  # slime below the water line
            elif v == 40 or v == 39:
                c = shade(c, 0.6)
            return c
        a.band("papan", leaf)
        a.band("batu", stone("7E786E", blocks=12, moss="4E6A3A"))
        a.band("kayu", grain("5A3E26", period=5))
        a.band("besi", metal("3C4046", rust=0.35 if not dok else 0.2, rivet=8))


GLOW = {
    "kapal_dagang": lambda img, b: glow_of(img, b, keep=(9,)),
    "kapal_bajak_laut": lambda img, b: glow_pick(img, b, lambda band, x, y, c: band == 9 or (
        band == 2 and in_rect(x, y, 8, 8, 62, 26) and c[0] > 150 and c[1] < 140)),
    "perahu_nelayan": lambda img, b: glow_of(img, b, keep=(6,)),
    "api_suar_retak": lambda img, b: glow_of(img, b, keep=(3,), glints=(2,), thresh=690, glint_alpha=120),
    "api_suar": lambda img, b: glow_of(img, b, keep=(3,), glints=(2,), thresh=660, glint_alpha=200),
    "api_suar_fajar": lambda img, b: glow_of(img, b, keep=(3,), glints=(2,), thresh=560, glint_alpha=220),
    "kalajengking_raja": lambda img, b: glow_pick(img, b, lambda band, x, y, c: band == 6 or (
        band in (0, 1, 2, 3, 5) and c[0] > 200 and 80 < c[1] < 200 and c[2] < 110)),
    "kalajengking_pasir": lambda img, b: glow_of(img, b, keep=(6,)),
    "temuan_pasir": lambda img, b: glow_of(img, b, glints=(0, 2), thresh=540, glint_alpha=230),
    "pilar_rune": lambda img, b: glow_pick(img, b, lambda band, x, y, c: band in (1, 4) and c[1] > 200 and c[0] < 140),
    "perahu_kanal": lambda img, b: glow_of(img, b, keep=(4,)),
    "perahu_naga_merah": lambda img, b: glow_of(img, b, keep=(6,)),
    "herba_tebing": lambda img, b: glow_of(img, b, keep=(2,)),
    "herba_tebing_awan": lambda img, b: glow_of(img, b, keep=(2,)),
    "jerat": lambda img, b: glow_pick(img, b, lambda band, x, y, c: band == 0 and c[0] > 190 and c[1] < 90),
    "gerbang_tengkorak": lambda img, b: glow_pick(img, b, lambda band, x, y, c: band == 3 and c[0] > 190 and c[1] < 60),
    "segel_pintu": lambda img, b: glow_pick(img, b, lambda band, x, y, c: band == 3 or (band == 0 and c[0] > 180 and c[1] < 130 and c[2] > 60)),
    "tong_minyak": lambda img, b: glow_pick(img, b, lambda band, x, y, c: band in (0, 3) and c[2] > 150 and c[1] < 140),
}

BUILDERS = [build_ships, build_sampan, build_lamp, build_crane, build_galangan, build_camel, build_scorpion, build_oasis_mech, build_canal, build_dragon_boat, build_loom, build_gunung, build_iblis]


# ================================================================================================ main

def sheet(img, path, factor):
    big = Image(img.width * factor, img.height * factor)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.get(x, y)
            bg = 40 if ((x // 4 + y // 4) % 2) else 70
            k = a / 255.0
            c = (r * k + bg * (1 - k), g * k + bg * (1 - k), b * k + bg * (1 - k), 255)
            for yy in range(factor):
                for xx in range(factor):
                    big.set(x * factor + xx, y * factor + yy, c)
    big.save(path)


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    only = sys.argv[2].split(",") if len(sys.argv) > 2 else None
    for build in BUILDERS:
        build()
    os.makedirs(OUT, exist_ok=True)
    glows = 0
    for name, a in ATLASES.items():
        if only and name not in only:
            continue
        img = a.render()
        img.save(os.path.join(OUT, name + ".png"))
        if preview:
            os.makedirs(preview, exist_ok=True)
            sheet(img, os.path.join(preview, "tema_" + name + ".png"), 2 if img.width >= 256 else 4)
        spec = GLOW.get(name)
        if spec is not None:
            g = spec(img, a.b)
            g.save(os.path.join(OUT, name + "_pancar.png"))
            glows += 1
    print("tema entity textures: %d (+%d glow)" % (len(ATLASES) if not only else len(only), glows))


if __name__ == "__main__":
    main()
