"""Shared library of the Sosok skin composer (DESIGN 40.3, module 40K).

Everything here is mirrored 1:1 by the Java classes in ``src/client/java/dev/kultivator/client/sosok/kulit``:

* ``SplitMix64`` / ``fnv32`` / ``fnv64``     -> ``SplitMix64.java``
* ``tangga`` (integer colour ramp, 40.3.2)    -> ``Palet.tangga``
* ``susun`` (layer composer, 40.3.9)          -> ``KulitPenyusun.susunPiksel``
* ``UV_RAMBUT`` / ``UV_BUSANA`` (40.3.6/7)    -> ``BusanaKatalog`` (checked by ``cek_sosok.py`` with a regex)
* ``resolve`` (look resolver, 40.3.8-40.4)    -> ``RupaResolver.dari``

All colour maths is integer (lookup tables LIN/SRGB are written into ``resep.json`` and read by Java), so an atlas made
by ``gen_sosok_pratinjau.py --benih`` is pixel-identical to ``/sosok dump`` in game.

Sheet encoding (40.3.2): A = coverage, G = palette slot * 16, R = tone (128 = base colour), B = flags
(1 dark seam, 2 cloth noise, 4 shine, 8 fixed colour from the paired ``_tetap`` sheet).
"""
import math
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "kultivator")
BAGIAN = os.path.join(ASSETS, "textures", "sosok", "bagian")
RESEP = os.path.join(ASSETS, "sosok", "resep.json")
KELUARAN = os.path.join(HERE, "keluaran", "sosok")

M64 = 0xFFFFFFFFFFFFFFFF
M32 = 0xFFFFFFFF

# ====================================================================== palette slots (40.3.3)

KULIT, KULIT_RONA, RAMBUT, RAMBUT_KILAP, MATA_PUTIH, IRIS, PUPIL, ALIS, BIBIR, KAIN1, KAIN2, TEPI, LOGAM, GIOK, SULAM, SAMAK = range(16)
SLOT_NAMES = ["KULIT", "KULIT_RONA", "RAMBUT", "RAMBUT_KILAP", "MATA_PUTIH", "IRIS", "PUPIL", "ALIS", "BIBIR", "KAIN1",
              "KAIN2", "TEPI", "LOGAM", "GIOK", "SULAM", "SAMAK"]

F_SEAM, F_NOISE, F_SHINE, F_TETAP = 1, 2, 4, 8
STEP = 24          # one "tingkat" of tone
ATLAS = 128

# ====================================================================== RNG and hashes (40.3.10)


class SplitMix64:
    """SplitMix64 exactly as 40.3.10 (64-bit wrap at every step)."""

    def __init__(self, seed):
        self.state = seed & M64

    def next_long(self):
        self.state = (self.state + 0x9E3779B97F4A7C15) & M64
        z = self.state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M64
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M64
        return z ^ (z >> 31)

    def next01(self):
        return (self.next_long() >> 11) / 9007199254740992.0

    def pick(self, items):
        """Weighted pick from [(value, weight int), ...]; weight <= 0 entries never come out. None for an empty list."""
        total = 0
        for _, w in items:
            if w > 0:
                total += w
        r = self.next01() * total
        if total <= 0:
            return None
        acc = 0
        last = None
        for v, w in items:
            if w <= 0:
                continue
            acc += w
            last = v
            if r < acc:
                return v
        return last

    def chance(self, percent):
        return self.next01() * 100.0 < percent

    def below(self, n):
        return int(self.next01() * n) if n > 0 else 0


def fnv32(text):
    h = 0x811C9DC5
    for b in text.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & M32
    return h


def fnv64_ints(values):
    """FNV-1a 64 over a list of ints (each fed as 4 little-endian bytes), the atlas cache key."""
    h = 0xCBF29CE484222325
    for v in values:
        v &= M32
        for i in range(4):
            h ^= (v >> (8 * i)) & 0xFF
            h = (h * 0x100000001B3) & M64
    return h


def rng_for(benih, group):
    """The RNG of one draw group: SplitMix64(benih * 0x100000001B3 ^ fnv32(group))."""
    return SplitMix64(((benih * 0x100000001B3) & M64) ^ fnv32(group))


# ====================================================================== PNG io


def read_png(path):
    """Reads an 8-bit RGBA/RGB PNG (all filter types). Returns (w, h, list of (r,g,b,a))."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a png: " + path)
    pos = 8
    w = h = 0
    ctype = 6
    idat = bytearray()
    plte = None
    trns = None
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if kind == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
            if depth != 8:
                raise ValueError("only 8-bit png: " + path)
        elif kind == b"PLTE":
            plte = body
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat.extend(body)
        elif kind == b"IEND":
            break
    raw = zlib.decompress(bytes(idat))
    bpp = {6: 4, 2: 3, 3: 1, 0: 1, 4: 2}[ctype]
    stride = w * bpp
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ft = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if ft == 1:
                line[i] = (line[i] + a) & 255
            elif ft == 2:
                line[i] = (line[i] + b) & 255
            elif ft == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 255
            elif ft == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    px = []
    for i in range(w * h):
        if ctype == 6:
            px.append(tuple(out[i * 4:i * 4 + 4]))
        elif ctype == 2:
            px.append(tuple(out[i * 3:i * 3 + 3]) + (255,))
        elif ctype == 3:
            idx = out[i]
            r, g, b = plte[idx * 3:idx * 3 + 3]
            a = trns[idx] if trns is not None and idx < len(trns) else 255
            px.append((r, g, b, a))
        elif ctype == 0:
            v = out[i]
            px.append((v, v, v, 255))
        else:
            v = out[i * 2]
            px.append((v, v, v, out[i * 2 + 1]))
    return w, h, px


def write_png(path, w, h, px):
    """Writes RGBA pixels (list of 4-tuples, row-major) as an 8-bit RGBA PNG (filter 0, deterministic)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = px[y * w + x]
            raw.extend((r & 255, g & 255, b & 255, a & 255))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & M32)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def argb_to_rgba(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255)


def write_argb(path, w, h, ints):
    write_png(path, w, h, [argb_to_rgba(v) for v in ints])


# ====================================================================== encoded sheets


class Lembar:
    """An encoded part sheet (40.3.2) with painting helpers. Coordinates outside the sheet are ignored."""

    def __init__(self, w, h):
        self.w = w
        self.h = h
        self.px = [(0, 0, 0, 0)] * (w * h)
        self.tetap = None  # RGBA list of the paired fixed-colour sheet, made on first use

    def put(self, x, y, slot, tone=128, a=255, flags=0):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = (max(0, min(255, int(tone))), slot * 16, flags & 15, max(0, min(255, int(a))))

    def fixed(self, x, y, rgb, a=255):
        """A fixed-colour pixel (flag 8): colour stored in the _tetap sheet."""
        if 0 <= x < self.w and 0 <= y < self.h:
            if self.tetap is None:
                self.tetap = [(0, 0, 0, 0)] * (self.w * self.h)
            self.px[y * self.w + x] = (128, 0, F_TETAP, a)
            self.tetap[y * self.w + x] = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y * self.w + x]
        return (0, 0, 0, 0)

    def slot_at(self, x, y):
        p = self.get(x, y)
        return None if p[3] == 0 else (p[1] + 8) >> 4

    def tone_at(self, x, y):
        return self.get(x, y)[0]

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = (0, 0, 0, 0)
            if self.tetap is not None:
                self.tetap[y * self.w + x] = (0, 0, 0, 0)

    def rect(self, x0, y0, w, h, slot, tone=128, a=255, flags=0):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.put(x, y, slot, tone, a, flags)

    def shade(self, x, y, delta):
        """Adds delta to the tone of an existing pixel."""
        p = self.get(x, y)
        if p[3]:
            self.px[y * self.w + x] = (max(0, min(255, p[0] + delta)), p[1], p[2], p[3])

    def flag(self, x, y, f):
        p = self.get(x, y)
        if p[3]:
            self.px[y * self.w + x] = (p[0], p[1], p[2] | f, p[3])

    def blit(self, other, dx=0, dy=0):
        for y in range(other.h):
            for x in range(other.w):
                p = other.px[y * other.w + x]
                if p[3]:
                    if 0 <= x + dx < self.w and 0 <= y + dy < self.h:
                        self.px[(y + dy) * self.w + x + dx] = p
                        if other.tetap is not None and p[2] & F_TETAP:
                            if self.tetap is None:
                                self.tetap = [(0, 0, 0, 0)] * (self.w * self.h)
                            self.tetap[(y + dy) * self.w + x + dx] = other.tetap[y * other.w + x]

    def empty(self):
        return all(p[3] == 0 for p in self.px)

    def save(self, rel):
        """Writes textures/sosok/bagian/<rel>.png (and <rel>_tetap.png when fixed colours were used)."""
        write_png(os.path.join(BAGIAN, rel + ".png"), self.w, self.h, self.px)
        tpath = os.path.join(BAGIAN, rel + "_tetap.png")
        if self.tetap is not None and any(p[3] for p in self.tetap):
            write_png(tpath, self.w, self.h, self.tetap)
        elif os.path.exists(tpath):
            os.remove(tpath)


def load_sheet(rel):
    """(w, h, pixels, tetap pixels or None) of textures/sosok/bagian/<rel>.png."""
    w, h, px = read_png(os.path.join(BAGIAN, rel + ".png"))
    tpath = os.path.join(BAGIAN, rel + "_tetap.png")
    tet = read_png(tpath)[2] if os.path.exists(tpath) else None
    return w, h, px, tet


# ====================================================================== player skin layout (region A)

# part -> (base uv, outer uv, w, h, d); arms take w from the slim flag
_PARTS = {
    "kepala": ((0, 0), (32, 0), 8, 8, 8),
    "badan": ((16, 16), (16, 32), 8, 12, 4),
    "lengan_kanan": ((40, 16), (40, 32), 4, 12, 4),
    "lengan_kiri": ((32, 48), (48, 48), 4, 12, 4),
    "kaki_kanan": ((0, 16), (0, 32), 4, 12, 4),
    "kaki_kiri": ((16, 48), (0, 48), 4, 12, 4),
}
PARTS = list(_PARTS)


def part_dims(part, slim):
    base, outer, w, h, d = _PARTS[part]
    if slim and part.startswith("lengan"):
        w = 3
    return w, h, d


def face_rect(part, layer, face, slim=False):
    """(x, y, w, h) of one face of a body part on the 64x64 skin. layer 0 base, 1 outer. faces: atas bawah kanan depan
    kiri belakang (kanan = the wearer's right side)."""
    base, outer, _, _, _ = _PARTS[part]
    w, h, d = part_dims(part, slim)
    u, v = base if layer == 0 else outer
    return cube_face(u, v, w, h, d, face)


def cube_face(u, v, w, h, d, face):
    """Face rectangle of a vanilla cube UV unwrap (integer footprint; fractional sizes round up)."""
    w, h, d = int(math.ceil(w - 1e-6)), int(math.ceil(h - 1e-6)), int(math.ceil(d - 1e-6))
    return {
        "atas": (u + d, v, w, d),
        "bawah": (u + d + w, v, w, d),
        "kanan": (u, v + d, d, h),
        "depan": (u + d, v + d, w, h),
        "kiri": (u + d + w, v + d, d, h),
        "belakang": (u + 2 * d + w, v + d, w, h),
    }[face]


class Badan:
    """Paints garments on a 64x64 sheet in body coordinates.

    * ``face(part, layer, face)`` -> a FacePainter with local (x, y) on that face (front: x 0 = wearer's right).
    * ``wrap(part, layer)``       -> a WrapPainter: horizontal band around the part, s = 0 at the back edge of the
      wearer's right side, then right side (d), front (w), left side (d), back (w) - continuous like the unwrap.
    """

    def __init__(self, sheet, slim):
        self.s = sheet
        self.slim = slim

    def face(self, part, layer, face):
        return FacePainter(self.s, face_rect(part, layer, face, self.slim))

    def wrap(self, part, layer):
        w, h, d = part_dims(part, self.slim)
        rects = [face_rect(part, layer, f, self.slim) for f in ("kanan", "depan", "kiri", "belakang")]
        return WrapPainter(self.s, rects, h)

    def dims(self, part):
        return part_dims(part, self.slim)


class FacePainter:
    def __init__(self, sheet, rect):
        self.s = sheet
        self.x0, self.y0, self.w, self.h = rect

    def put(self, x, y, slot, tone=128, a=255, flags=0):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.s.put(self.x0 + x, self.y0 + y, slot, tone, a, flags)

    def fixed(self, x, y, rgb, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.s.fixed(self.x0 + x, self.y0 + y, rgb, a)

    def fill(self, slot, tone=128, a=255, flags=0, y0=0, y1=None, x0=0, x1=None):
        y1 = self.h if y1 is None else y1
        x1 = self.w if x1 is None else x1
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, slot, tone, a, flags)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.s.get(self.x0 + x, self.y0 + y)
        return (0, 0, 0, 0)

    def shade(self, x, y, d):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.s.shade(self.x0 + x, self.y0 + y, d)

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.s.clear(self.x0 + x, self.y0 + y)

    def mirror_x(self, x):
        return self.w - 1 - x


class WrapPainter:
    def __init__(self, sheet, rects, h):
        self.s = sheet
        self.rects = rects
        self.h = h
        self.len = sum(r[2] for r in rects)
        self.starts = []
        acc = 0
        for r in rects:
            self.starts.append(acc)
            acc += r[2]

    def _xy(self, s):
        s %= self.len
        for start, r in zip(self.starts, self.rects):
            if start <= s < start + r[2]:
                return r[0] + (s - start), r[1]
        return None

    def put(self, s, y, slot, tone=128, a=255, flags=0):
        if 0 <= y < self.h:
            xy = self._xy(s)
            self.s.put(xy[0], xy[1] + y, slot, tone, a, flags)

    def fixed(self, s, y, rgb, a=255):
        if 0 <= y < self.h:
            xy = self._xy(s)
            self.s.fixed(xy[0], xy[1] + y, rgb, a)

    def get(self, s, y):
        if 0 <= y < self.h:
            xy = self._xy(s)
            return self.s.get(xy[0], xy[1] + y)
        return (0, 0, 0, 0)

    def shade(self, s, y, d):
        if 0 <= y < self.h:
            xy = self._xy(s)
            self.s.shade(xy[0], xy[1] + y, d)

    def clear(self, s, y):
        if 0 <= y < self.h:
            xy = self._xy(s)
            self.s.clear(xy[0], xy[1] + y)

    def row(self, y, slot, tone=128, a=255, flags=0):
        for s in range(self.len):
            self.put(s, y, slot, tone, a, flags)

    def band(self, y0, y1, slot, tone=128, a=255, flags=0):
        for y in range(y0, y1):
            self.row(y, slot, tone, a, flags)

    def front_range(self):
        """(s0, s1) of the front face in wrap coordinates."""
        return self.starts[1], self.starts[1] + self.rects[1][2]

    def back_range(self):
        return self.starts[3], self.starts[3] + self.rects[3][2]


# ====================================================================== cube tables (40.3.6, 40.3.7)
# id: (parent, pivot (x, y, z), box (x0, y0, z0, w, h, d), uv (u, v), spring chain or None, extra)
# extra: dict with "rot" (xRot, yRot, zRot degrees), "pasangan" (mirrored pair pivot sign on x), "lebar_ramping" box.

KEPALA, BADAN, LENGAN = "kepala", "badan", "lengan"

UV_RAMBUT = {
    "sanggul": ("kepala", (0, -8, 0.5), (-1.5, -3, -1.5, 3, 3, 3), (98, 47), None, {}),
    "guan_rendah": ("sanggul", (0, -1, 0), (-2, -1.2, -2, 4, 2, 4), (110, 47), None, {}),
    "tusuk_guan": ("guan_rendah", (0, -0.4, 0), (-3.5, -0.3, -0.3, 7, 0.6, 0.6), (70, 60), None, {}),
    "guan_tinggi": ("sanggul", (0, -1.5, 0), (-2, -2.5, -2, 4, 4, 4), (64, 39), None, {}),
    "mahkota_teratai": ("sanggul", (0, -1.5, 0), (-2.5, -1.2, -2.5, 5, 2, 5), (104, 39), None, {}),
    "rambut_atas": ("kepala", (0, -8, 4.6), (-4, 0, 0, 8, 8, 1), (96, 24), "RAMBUT", {}),
    "rambut_bawah": ("badan", (0, 0.3, 2.6), (-3.5, 0, 0, 7, 10, 1), (80, 24), "RAMBUT", {}),
    "rambut_bawah_panjang": ("badan", (0, 0.3, 2.6), (-3.5, 0, 0, 7, 14, 1), (64, 24), "RAMBUT", {}),
    "ekor_kuda_1": ("kepala", (0, -8.4, 3.2), (-1, 0, -1, 2, 5, 2), (64, 47), "EKOR", {"rot": (25, 0, 0)}),
    "ekor_kuda_2": ("ekor_kuda_1", (0, 5, 0), (-0.9, 0, -0.9, 1.8, 6, 1.8), (80, 39), "EKOR", {}),
    "cepol": ("kepala", (2.6, -8.2, 0.4), (-1.5, -3, -1.5, 3, 3, 3), (64, 54), None, {"pasangan": True}),
    "sanggul_tinggi": ("kepala", (0, -8, 1), (-2.5, -3, -2, 5, 3, 4), (72, 47), None, {}),
    "sanggul_rendah": ("kepala", (0, -3.5, 4.2), (-2, -1.5, 0, 4, 3, 3), (76, 54), None, {}),
    "kepang_1": ("badan", (0, 0.5, 2.4), (-1, 0, -1, 2, 6, 2), (88, 39), "KEPANG", {}),
    "kepang_2": ("kepang_1", (0, 6, 0), (-0.9, 0, -0.9, 1.8, 6, 1.8), (96, 39), "KEPANG", {}),
    "pita_rambut": ("sanggul", (1, -0.5, 1.5), (-0.5, 0, 0, 1, 6, 0.3), (90, 47), "PITA", {"pasangan": True}),
    "tusuk_buyao": ("sanggul_tinggi", (1.5, -1.5, 0), (-0.5, -0.5, -0.5, 5, 1, 1), (86, 60), None, {"rot": (0, -20, 0)}),
    "manik_buyao": ("tusuk_buyao", (4.2, 0.4, 0), (-0.4, 0, -0.4, 0.8, 3, 0.8), (108, 54), "BUYAO", {"tiga": 0.8}),
    "bunga_rambut": ("kepala", (3.2, -7.4, 0), (-1, -1, -0.5, 2, 2, 1), (64, 60), None, {}),
    "jenggot_pendek": ("kepala", (0, -0.6, -4.2), (-2, 0, -0.6, 4, 4, 1), (94, 54), "JENGGOT", {}),
    "jenggot_panjang": ("kepala", (0, -0.6, -4.2), (-2, 0, -0.6, 4, 8, 1), (114, 24), "JENGGOT", {}),
    "kumis_naga": ("kepala", (1.8, -2.2, -4.3), (-0.5, 0, -0.5, 1, 4, 1), (104, 54), "KUMIS", {"pasangan": True}),
    "alis_panjang": ("kepala", (3.4, -5.6, -4.2), (-0.4, 0, -0.4, 0.8, 3, 0.8), (112, 54), "ALIS", {"pasangan": True}),
    "jambul_anak": ("kepala", (2, -8, 0), (-1, -2, -1, 2, 2, 2), (116, 54), None, {"pasangan": True}),
    "poni_samping": ("kepala", (3.6, -6.5, -2.6), (-0.5, 0, -0.5, 1, 6, 1), (94, 47), "PONI", {"pasangan": True}),
    "ikat_dahi_ekor": ("kepala", (0.8, -6.2, 4.4), (-0.5, 0, 0, 1, 5, 0.3), (90, 54), "PITA", {"pasangan": True}),
}

UV_BUSANA = {
    "rok_panjang_depan": ("badan", (0, 12, -2.1), (-4.5, 0, -0.8, 9, 11.5, 0.8), (80, 64), "ROK_DEPAN", {}),
    "rok_panjang_belakang": ("badan", (0, 12, 2.1), (-4.5, 0, 0, 9, 11.5, 0.8), (100, 64), "ROK_BELAKANG", {}),
    "rok_panjang_sisi": ("badan", (4.2, 12, 0), (-0.4, 0, -2.5, 0.8, 11.5, 5), (20, 64), "ROK_SISI", {"pasangan": True}),
    "rok_pendek_depan": ("badan", (0, 12, -2.1), (-4.5, 0, -0.8, 9, 6, 0.8), (24, 102), "ROK_DEPAN", {}),
    "rok_pendek_belakang": ("badan", (0, 12, 2.1), (-4.5, 0, 0, 9, 6, 0.8), (44, 102), "ROK_BELAKANG", {}),
    "rok_pendek_sisi": ("badan", (4.2, 12, 0), (-0.4, 0, -2.5, 0.8, 6, 5), (0, 82), "ROK_SISI", {"pasangan": True}),
    "lengan_lebar_manset": ("lengan", (-1, 6, 0), (-3, 0, -3, 6, 4, 6), (28, 82), None,
                            {"pasangan": True, "ramping": (-2.5, 0, -3, 5, 4, 6)}),
    "lengan_lebar_kantong": ("lengan_lebar_manset", (0, 2, 2.2), (-2.5, 0, 0, 5, 7, 2), (70, 82), "LENGAN", {"pasangan": True}),
    "pelindung_lengan": ("lengan", (-0.5, 6, 0), (-2.5, 0, -2.5, 5, 4, 5), (84, 82), None,
                         {"pasangan": True, "ramping": (-2, 0, -2.5, 4, 4, 5)}),
    "jubah_luar": ("badan", (0, 0.2, 2.4), (-4.5, 0, 0, 9, 17, 1), (0, 64), "JUBAH", {}),
    "jubah_luar_bahu": ("badan", (0, 0, 0), (-5, -0.5, -3, 10, 3, 6), (0, 93), None, {}),
    "kerah_tinggi": ("badan", (0, 0, 0), (-4.5, -2.5, -2.5, 9, 3, 5), (44, 93), None, {}),
    "sabuk": ("badan", (0, 6.5, 0), (-4.5, 0, -2.5, 9, 2, 5), (64, 102), None, {}),
    "pita_sabuk": ("sabuk", (1, 1.6, -2.6), (-0.5, 0, -0.3, 1, 6, 0.3), (92, 102), "PITA", {"pasangan": True}),
    "liontin_giok": ("sabuk", (2.2, 1.8, -2.7), (-1, 0, -0.5, 2, 4, 1), (6, 110), "RUMBAI", {}),
    "kantong": ("sabuk", (-2.8, 1.5, -2.6), (-1, 0, -1, 2, 3, 2), (12, 110), "RUMBAI", {}),
    "labu": ("sabuk", (-4.6, 1.2, 0.4), (-1.5, 0, -1.5, 3, 4, 3), (96, 102), "RUMBAI", {}),
    "bahu_zirah": ("lengan", (-0.5, -2, 0), (-2.5, -0.5, -2.5, 5, 3, 5), (72, 93), None, {"pasangan": True}),
    "rok_zirah": ("badan", (0, 11, -2.6), (-4.5, 0, -0.5, 9, 6, 1), (108, 102), "ROK_ZIRAH", {"depan_belakang": 2.6}),
    "selendang_1": ("lengan", (-1, 7, 2), (-1, 0, -0.25, 2, 8, 0.5), (32, 93), "SELENDANG", {"pasangan": True}),
    "selendang_2": ("selendang_1", (0, 8, 0), (-1, 0, -0.25, 2, 8, 0.5), (38, 93), "SELENDANG", {"pasangan": True}),
    "selendang_punggung": ("badan", (0, 4, 3), (-6, 0, -0.25, 12, 3, 0.5), (20, 110), "SELENDANG", {"rot": (30, 0, 0)}),
    "celemek": ("badan", (0, 4, -2.4), (-3.5, 0, -0.5, 7, 10, 1), (12, 82), "ROK_DEPAN", {}),
    "lencana_giok": ("sabuk", (-1.6, 1.8, -2.7), (-1, 0, -0.5, 2, 3, 1), (46, 110), "RUMBAI", {}),
    "cincin_kasaya": ("badan", (2, 3, -2.6), (-1, -1, -0.5, 2, 2, 1), (52, 110), None, {}),
    "buntalan": ("badan", (0, 1, 2.2), (-3, 0, 0, 6, 7, 3), (52, 82), None, {"rot": (0, 0, 20)}),
    "rumbai_iblis": ("badan", (0, 12, 2.2), (-1, 0, -0.5, 2, 6, 1), (0, 110), "RUMBAI", {"tiga_x": (-3, 0, 3)}),
    "kerah_bulu": ("badan", (0, -0.5, 0), (-5, -1, -3, 10, 2, 6), (92, 93), None, {}),
    "caping_bambu": ("kepala", (0, -8.2, 0), (-6, -1, -6, 12, 1, 12), (32, 64), None, {}),
    "caping_puncak": ("caping_bambu", (0, -1, 0), (-3, -2, -3, 6, 2, 6), (0, 102), None, {}),
}

RAMBUT_KUBUS = list(UV_RAMBUT)       # bit i of SosokRupa.kubusRambut
BUSANA_KUBUS = list(UV_BUSANA)       # bit i of SosokRupa.kubusBusana

REGION_C = (64, 24, 128, 64)
REGION_D = (0, 64, 128, 128)
RESERVED = [(64, 14, 128, 24), (58, 110, 128, 128)]


def footprint(entry):
    box, uv = entry[2], entry[3]
    w, h, d = box[3], box[4], box[5]
    if "ramping" in entry[5]:
        pass
    return (uv[0], uv[1], uv[0] + int(math.ceil(2 * (d + w) - 1e-6)), uv[1] + int(math.ceil(d + h - 1e-6)))


def cek_uv():
    """List of problems of the cube UV tables (empty = fine): out of region or overlapping footprints."""
    problems = []
    for table, region, name in ((UV_RAMBUT, REGION_C, "C"), (UV_BUSANA, REGION_D, "D")):
        rects = [(k, footprint(v)) for k, v in table.items()]
        for k, (x0, y0, x1, y1) in rects:
            if x0 < region[0] or y0 < region[1] or x1 > region[2] or y1 > region[3]:
                problems.append("%s: %s outside region %s" % (name, k, region))
            for rx0, ry0, rx1, ry1 in RESERVED:
                if x0 < rx1 and rx0 < x1 and y0 < ry1 and ry0 < y1:
                    problems.append("%s: %s in reserved area" % (name, k))
        for i in range(len(rects)):
            for j in range(i + 1, len(rects)):
                a, b = rects[i][1], rects[j][1]
                if a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]:
                    problems.append("%s: %s overlaps %s" % (name, rects[i][0], rects[j][0]))
    return problems


def cube_painter(sheet, entry):
    """Painters for the six faces of a cube footprint drawn on a sheet whose origin is the cube's UV."""
    box = entry[2]
    w, h, d = box[3], box[4], box[5]
    out = {}
    for f in ("atas", "bawah", "kanan", "depan", "kiri", "belakang"):
        out[f] = FacePainter(sheet, cube_face(0, 0, w, h, d, f))
    return out


def cube_sheet_size(entry):
    x0, y0, x1, y1 = footprint(entry)
    return x1 - x0, y1 - y0


# ====================================================================== colour ramp (40.3.2), integer form

OFF_DINGIN = (2621, 1966, 5898)    # (0.04, 0.03, 0.09) * 65535
OFF_KULIT = (3932, 655, 0)         # (0.06, 0.01, 0.00) * 65535
OFF_HANGAT = (3277, 2621, 0)       # (0.05, 0.04, 0.00) * 65535


def make_tables():
    lin = [int(round(((i / 255.0) ** 2.2) * 65535)) for i in range(256)]
    srgb = [int(round(255.0 * ((j / 4095.0) ** (1 / 2.2)))) for j in range(4096)]
    return lin, srgb


LIN, SRGB = make_tables()


def tangga(rgb, tone, kulit=False):
    """RGB int of slot colour ``rgb`` at ``tone`` 0-255 (128 = rgb itself up to table rounding). Integer only."""
    tone = max(0, min(255, tone))
    out = 0
    for i, sh in enumerate((16, 8, 0)):
        c = (rgb >> sh) & 255
        L = LIN[c]
        if kulit:
            gelap = L * 62 // 100 + OFF_KULIT[i]
        else:
            gelap = L * 42 // 100 + OFF_DINGIN[i]
        if gelap > 65535:
            gelap = 65535
        terang = L * 118 // 100 + OFF_HANGAT[i]
        if terang > 65535:
            terang = 65535
        if tone < 128:
            v = (gelap * (128 - tone) + L * tone) // 128
        else:
            v = (L * (256 - tone) + terang * (tone - 128)) // 128
        out |= SRGB[v >> 4] << sh
    return out


def ramp_table(slots):
    """16 x 256 RGB table for a palette of 16 slot colours."""
    table = []
    for i, rgb in enumerate(slots):
        kulit = i <= KULIT_RONA
        table.append([tangga(rgb, t, kulit) for t in range(256)])
    return table


def noise(x, y, seed):
    """Cloth noise -4..4 (flag 2), same integer hash as Palet.derau."""
    h = ((x * 73856093) & M32) ^ ((y * 19349663) & M32) ^ ((seed * 83492791) & M32)
    return ((h >> 16) & 0xFFFF) % 9 - 4


def blend(dst, rgb, a):
    """Integer 'over' of colour rgb with coverage a onto ARGB dst (same as KulitPenyusun.campur)."""
    if a >= 255:
        return 0xFF000000 | rgb
    da = (dst >> 24) & 255
    if da == 0:
        return (a << 24) | rgb
    inv = 255 - a
    oa = a + (da * inv + 127) // 255
    den = oa * 255
    out = oa << 24
    for sh in (16, 8, 0):
        s = (rgb >> sh) & 255
        d = (dst >> sh) & 255
        c = (s * a * 255 + d * da * inv + den // 2) // den
        out |= min(255, c) << sh
    return out


def paint_layer(atlas, ramp, sheet, layer, seed, clip=None, out_w=ATLAS):
    """Composites one layer onto ``atlas`` (list of ARGB ints, width out_w).

    layer = (sx, sy, w, h, dx, dy): source rect in the sheet and destination; clip = (x0, y0, x1, y1) in atlas space.
    sheet = (sw, sh, pixels, tetap pixels or None)."""
    sw, sh, px, tet = sheet
    sx, sy, w, h, dx, dy = layer
    x0, y0, x1, y1 = 0, 0, w, h
    if clip is not None:
        x0 = max(0, clip[0] - dx)
        y0 = max(0, clip[1] - dy)
        x1 = min(w, clip[2] - dx)
        y1 = min(h, clip[3] - dy)
    for y in range(y0, y1):
        ty = dy + y
        for x in range(x0, x1):
            tx = dx + x
            r, g, b, a = px[(sy + y) * sw + sx + x]
            if a == 0:
                continue
            if b & F_TETAP and tet is not None:
                tr, tg, tb, _ = tet[(sy + y) * sw + sx + x]
                rgb = (tr << 16) | (tg << 8) | tb
            else:
                slot = min(15, (g + 8) >> 4)
                t = r
                if b & F_SEAM:
                    t -= STEP
                if b & F_SHINE:
                    t += STEP
                if b & F_NOISE:
                    t += noise(tx, ty, seed)
                t = 0 if t < 0 else (255 if t > 255 else t)
                rgb = ramp[slot][t]
            i = ty * out_w + tx
            atlas[i] = blend(atlas[i], rgb, a)


# ====================================================================== atlas composition (40.3.9)

# Layer kinds in a resolved look: ("biasa", sheet, sx, sy, w, h, dx, dy) or ("mata"/"alis"/"mulut", sheet, dx, dy)
# where the state picks sx = 8 * state.

EYE_STRIP = (64, 0)      # B1 origin, strip i at (64 + 8*(i%8), 4*(i//8)), 8 x 4, rows fy 2-5
MOUTH_STRIP = (64, 8)    # B2 origin, strip i at (64 + 8*(i%8), 8 + 3*(i//8)), 8 x 3, rows fy 5-7
FACE = (8, 8)            # head front, base layer


def _layer_rect(layer, mata, mulut):
    kind = layer[0]
    if kind == "biasa":
        return layer[2:8]
    state = mata if kind in ("mata", "alis") else mulut
    return (8 * state, 0, 8, 8, layer[2], layer[3])


def susun(look, loader):
    """Composes the 128x128 atlas of a resolved look. ``loader(rel)`` returns a sheet tuple (cached by the caller).

    look: dict with "palet" (16 RGB), "lapisan" (layer list), "benih" (noise seed). Returns a list of ARGB ints."""
    ramp = ramp_table(look["palet"])
    seed = look["benih"] & 0xFFFF
    atlas = [0] * (ATLAS * ATLAS)
    for layer in look["lapisan"]:
        sheet = loader(layer[1])
        if sheet is None:
            continue
        paint_layer(atlas, ramp, sheet, _layer_rect(layer, 0, 0), seed)
    # face strips: recompose the head front with each eye / mouth state
    clip = (FACE[0], FACE[1], FACE[0] + 8, FACE[1] + 8)
    base_mouth_row = None
    for kind in ("mata", "mulut"):
        for state in range(16):
            face = [0] * (ATLAS * ATLAS)
            for layer in look["lapisan"]:
                sheet = loader(layer[1])
                if sheet is None:
                    continue
                m = state if kind == "mata" else 0
                u = state if kind == "mulut" else 0
                paint_layer(face, ramp, sheet, _layer_rect(layer, m, u), seed, clip)
            if kind == "mata":
                ox, oy = EYE_STRIP[0] + 8 * (state % 8), EYE_STRIP[1] + 4 * (state // 8)
                for fy in range(4):
                    for fx in range(8):
                        atlas[(oy + fy) * ATLAS + ox + fx] = face[(FACE[1] + 2 + fy) * ATLAS + FACE[0] + fx]
            else:
                ox, oy = MOUTH_STRIP[0] + 8 * (state % 8), MOUTH_STRIP[1] + 3 * (state // 8)
                row5 = [face[(FACE[1] + 5) * ATLAS + FACE[0] + fx] for fx in range(8)]
                if state == 0:
                    base_mouth_row = row5
                for fy in range(3):
                    for fx in range(8):
                        v = face[(FACE[1] + 5 + fy) * ATLAS + FACE[0] + fx]
                        if fy == 0 and state > 0 and v == base_mouth_row[fx]:
                            v = 0  # keep the eye strip's row 5 (blush, tears) visible where the mouth adds nothing
                        if fy == 0 and state == 0:
                            v = 0
                        atlas[(oy + fy) * ATLAS + ox + fx] = v
    return atlas


class SheetCache:
    """Loads sheets once (textures/sosok/bagian/<rel>.png); missing sheets give None (logged once)."""

    def __init__(self):
        self.cache = {}
        self.missing = set()

    def __call__(self, rel):
        if rel in self.cache:
            return self.cache[rel]
        path = os.path.join(BAGIAN, rel + ".png")
        if not os.path.exists(path):
            if rel not in self.missing:
                print("  ! missing sheet", rel)
                self.missing.add(rel)
            self.cache[rel] = None
            return None
        self.cache[rel] = load_sheet(rel)
        return self.cache[rel]


# ====================================================================== preview helpers


def scale_up(w, h, ints, k, bg=None):
    """Nearest-neighbour enlargement; bg (ARGB) fills transparent pixels with a checker for reading."""
    out = []
    for y in range(h * k):
        for x in range(w * k):
            v = ints[(y // k) * w + x // k]
            if bg is not None and (v >> 24) & 255 < 255:
                chk = 0xFF3A3A44 if ((x // (k * 2) + y // (k * 2)) & 1) else 0xFF30303A
                v = blend(chk, v & 0xFFFFFF, (v >> 24) & 255) if (v >> 24) & 255 else chk
            out.append(v)
    return out


def rgb_hex(s):
    s = s.lstrip("#")
    return int(s, 16)


def figure(atlas, slim, view="depan", w_atlas=ATLAS):
    """A flat 2D figure (16 x 32) seen from the front, back or side: base faces with the outer layers over them."""
    aw = 3 if slim else 4
    W, H = 16, 32
    out = [0] * (W * H)

    def put_face(part, face, ox, oy, flip=False):
        for layer in (0, 1):
            x0, y0, w, h = face_rect(part, layer, face, slim)
            for y in range(h):
                for x in range(w):
                    sx = (w - 1 - x) if flip else x
                    v = atlas[(y0 + y) * w_atlas + x0 + sx]
                    a = (v >> 24) & 255
                    if a == 0:
                        continue
                    i = (oy + y) * W + ox + x
                    if 0 <= ox + x < W:
                        out[i] = blend(out[i], v & 0xFFFFFF, a)

    if view == "depan":
        put_face("kepala", "depan", 4, 0)
        put_face("badan", "depan", 4, 8)
        put_face("lengan_kanan", "depan", 4 - aw, 8)
        put_face("lengan_kiri", "depan", 12, 8)
        put_face("kaki_kanan", "depan", 4, 20)
        put_face("kaki_kiri", "depan", 8, 20)
    elif view == "belakang":
        put_face("kepala", "belakang", 4, 0)
        put_face("badan", "belakang", 4, 8)
        put_face("lengan_kiri", "belakang", 4 - aw, 8)
        put_face("lengan_kanan", "belakang", 12, 8)
        put_face("kaki_kiri", "belakang", 4, 20)
        put_face("kaki_kanan", "belakang", 8, 20)
    else:   # the wearer's right side
        put_face("kepala", "kanan", 4, 0)
        put_face("badan", "kanan", 6, 8)
        put_face("kaki_kanan", "kanan", 6, 20)
        put_face("lengan_kanan", "kanan", 6, 8)
    return W, H, out


def sheet_compose(rows, scale, gap=2, bg=0xFF26262E):
    """Lays images [(w, h, ints)] out in rows (list of lists), scaled, on a dark background."""
    imgs = [[(w * scale, h * scale, scale_up(w, h, px, scale)) for (w, h, px) in row] for row in rows]
    width = max(sum(i[0] for i in row) + gap * scale * (len(row) + 1) for row in imgs)
    height = sum(max(i[1] for i in row) for row in imgs) + gap * scale * (len(imgs) + 1)
    canvas = [bg] * (width * height)
    y = gap * scale
    for row in imgs:
        x = gap * scale
        for w, h, px in row:
            for yy in range(h):
                for xx in range(w):
                    v = px[yy * w + xx]
                    a = (v >> 24) & 255
                    if a:
                        i = (y + yy) * width + x + xx
                        canvas[i] = blend(canvas[i], v & 0xFFFFFF, a)
            x += w + gap * scale
        y += max(i[1] for i in row) + gap * scale
    return width, height, canvas
