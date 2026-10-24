"""Widens the gear atlas of the town people for DESIGN 38 (38.O.1 and 38.T.8.2, module 38MD): hats, helmets, crowns,
turbans, veils, masks, goggles, gloves, beads, baskets, belts, badges and chains of the capital, the villages, the
themed cities and the demonic city, drawn by client/dunia/kota/KotaGearLayer over KotaGear meshes.

textures/dunia/kota/entity/perlengkapan.png   64 x 1536: forty-eight material bands of 32 px (KotaGear.BAND_*).
   bands 0-15   copied pixel for pixel from the existing 64 x 512 atlas (tools/gen_dunia_kota_gear.py is not edited)
   bands 16-31  DESIGN 38.O.1 materials (capital, guilds, villages)
   bands 32-47  DESIGN 38.T.8.2 materials (themed cities, Kota Iblis)
Every cube of a gear model takes its UV from texOffs(0, band * 32): its unwrap (2 (w + d) wide, d + h tall) stays inside
its band (w + d <= 32, d + h <= 32), so every band is a tiling material and any part of it reads as that material.

  16 black lacquered gauze (official hats)           32 navy silk with gold border lines (harbour master)
  17 red-laced steel lamellar (palace helmets)       33 shark tooth enamel (sailor's necklace)
  18 painted shield hide (tiger mask, brass nails)   34 wound turban cloth, white with a green stripe
  19 coin brocade, dark red with gold coins          35 sea silver with engraved waves (sea priest)
  20 scorched smith's leather (gloves)               36 gold brocade with red lozenges (fortress lord)
  21 smoky quartz lens (stone-cutter goggles)        37 sand gauze, dusty weave (camel leader's veil)
  22 purple silk with gold cloud scrolls (alchemy)   38 desert bronze with etched spirals (spiked helm)
  23 tarred dark straw (captain's hat)               39 indigo straw and blue cloth (boatman's hat)
  24 starched white linen (chef's cap)               40 yellow cloth with red sutra script (pilgrim)
  25 polished sandalwood beads (prayer beads)        41 twisted hemp rope (herb picker, cords)
  26 bamboo wicker (back basket)                     42 pitted black iron (masks, chains)
  27 brown fur with strands (beast trader)           43 black-to-blood horn with growth rings (demon crown)
  28 gold embossed with stars (guild badges)         44 green-black snake scales (broker's snake crown)
  29 engraved silver (rims, pins, crown points)      45 charcoal hood cloth (assassin)
  30 old bone and ivory (beads, tusks, combs)        46 black cloth with smouldering red runes (blindfold)
  31 crimson horsehair plume                         47 black-red lacquer with gold flecks (demonic trims)

Run with: python tools/gen_tempat_gear.py [preview_dir]   (8x preview of bands 16-47 into preview_dir). Deterministic.
The script checks that bands 0-15 of the written atlas equal the old atlas pixel for pixel before it writes.
"""
import math
import os
import random
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "dunia", "kota", "entity")
ATLAS = os.path.join(ROOT, "perlengkapan.png")
W, BAND = 64, 32
OLD_BANDS = 16
BANDS = 48


# ================================================================================================ PNG reading

def read_png(path):
    """Reads an 8-bit RGBA (colour type 6) or RGB (type 2) non-interlaced PNG into an Image."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG: " + path)
    pos, idat, width, height, ctype = 8, bytearray(), 0, 0, 6
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if depth != 8 or ctype not in (2, 6) or interlace != 0:
                raise ValueError("unsupported PNG format in " + path)
        elif kind == b"IDAT":
            idat.extend(body)
        elif kind == b"IEND":
            break
    raw = zlib.decompress(bytes(idat))
    bpp = 4 if ctype == 6 else 3
    stride = width * bpp
    img = Image(width, height)
    prev = bytearray(stride)
    i = 0
    for y in range(height):
        ft = raw[i]
        i += 1
        line = bytearray(raw[i:i + stride])
        i += stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if ft == 1:
                line[x] = (line[x] + a) & 255
            elif ft == 2:
                line[x] = (line[x] + b) & 255
            elif ft == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 255
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + pred) & 255
        for x in range(width):
            px = line[x * bpp:(x + 1) * bpp]
            img.px[y * width + x] = [px[0], px[1], px[2], px[3] if bpp == 4 else 255]
        prev = line
    return img


# ================================================================================================ colour helpers

def clamp(v, lo=0.0, hi=255.0):
    return max(lo, min(hi, v))


def shade(rgb, f):
    return tuple(clamp(c * f) for c in rgb[:3])


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def hexrgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def noise2(rng, w, h, cell):
    """Smooth value noise in 0..1 (bilinear over a coarse grid), tileable over w x h."""
    gw, gh = max(1, w // cell), max(1, h // cell)
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]

    def at(x, y):
        fx, fy = (x % w) / cell, (y % h) / cell
        x0, y0 = int(fx) % gw, int(fy) % gh
        x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] + (grid[y0][x1] - grid[y0][x0]) * tx
        b = grid[y1][x0] + (grid[y1][x1] - grid[y1][x0]) * tx
        return a + (b - a) * ty

    return at


def wrapd(dx, period):
    """Signed distance on a ring of the given period."""
    return (dx + period / 2) % period - period / 2


# ================================================================================================ painters (bands 16-47)

def lacquer_gauze(seed):
    """Black lacquered gauze: a fine diagonal mesh, the lacquer's long glossy streaks and stiffened seams."""
    n = noise2(random.Random(seed), W, BAND, 8)

    def p(x, y, rng):
        base = (22, 20, 26)
        mesh = ((x + y) % 3 == 0) or ((x - y) % 3 == 0)
        c = shade(base, 1.18 if mesh else 0.9)
        gloss = max(0.0, math.sin((x * 0.55 - y * 0.35) * 0.6 + n(x, y) * 3.0)) ** 8
        c = mix(c, (120, 118, 136), gloss * 0.55)
        if y % 16 == 15:
            c = shade(c, 0.6)  # a stitched seam
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def lamellar_red(seed):
    """Lamellar plates: rows of small rounded steel scales laced together with red cord through two holes each."""
    def p(x, y, rng):
        lx, ly = x % 4, y % 8
        c = (156, 162, 172)
        c = shade(c, 1.18 - 0.06 * ly)
        if lx == 0:
            c = (88, 92, 104)  # the gap between plates
        if ly == 7:
            c = (70, 72, 82)
        if ly in (2, 3) and lx in (1, 2):
            c = (178, 34, 38) if ly == 2 else (128, 20, 26)  # the red lacing
        if lx == 3 and ly == 1:
            c = (220, 226, 232)  # a polished corner
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def painted_hide(seed):
    """A painted leather shield face: dark red ground, a black and gold tiger mask motif tiled every 16 px, brass nails
    along the band edges."""
    n = noise2(random.Random(seed), W, BAND, 4)

    def p(x, y, rng):
        c = mix((104, 28, 24), (140, 44, 34), n(x, y))
        tx, ty = x % 16 - 7.5, y % 16 - 7.5
        r = math.hypot(tx, ty)
        if r < 6.5:
            if r > 5.5:
                c = (200, 160, 70)  # the gold ring
            elif abs(tx) > 1.5 and -3.5 < ty < -1.0 and abs(abs(tx) - 3.0) < 1.6:
                c = (236, 210, 120)  # eyes
            elif ty > 1.0 and abs(tx) < 3.5 and (int(tx + 8) % 2 == 0):
                c = (230, 224, 210)  # fangs
            elif abs(tx) < 0.8 and ty < 2:
                c = (24, 18, 18)  # the brow stripe
            elif (int(abs(tx)) + int(ty + 8)) % 4 == 0:
                c = (30, 22, 20)  # tiger stripes
        if y in (1, 30) and x % 6 == 2:
            c = (232, 196, 110)  # brass nail heads
        return shade(c, 0.93 + rng.random() * 0.1)
    return p


def coin_brocade(seed):
    """Dark red brocade woven with round gold coins (square hole), small gold diamonds between, a twill sheen."""
    def p(x, y, rng):
        c = (110, 24, 30) if (x + y) % 2 == 0 else (96, 18, 26)
        cx, cy = wrapd(x - 4, 8), wrapd(y - 4 - (4 if (x // 8) % 2 else 0), 8)
        r = math.hypot(cx, cy)
        if r < 3.2:
            c = (214, 170, 64) if r > 1.2 else c
            if abs(cx) < 1.0 and abs(cy) < 1.0:
                c = (60, 12, 16)  # the square hole
            elif r > 2.6:
                c = (162, 120, 40)
        elif abs(cx) + abs(cy) > 6.2:
            c = (190, 150, 60)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def scorched(seed):
    """Thick smith's leather: dark tan, scorch blotches, burnt pits, a double-stitched cuff line every 16 px."""
    n = noise2(random.Random(seed), W, BAND, 4)
    m = noise2(random.Random(seed + 7), W, BAND, 6)

    def p(x, y, rng):
        c = mix((92, 58, 34), (134, 90, 52), n(x, y))
        burn = m(x, y)
        if burn > 0.62:
            c = mix(c, (34, 22, 16), (burn - 0.62) * 2.4)
        if rng.random() < 0.02:
            c = (24, 16, 12)
        if y % 16 in (2, 4) and x % 3 != 2:
            c = (204, 180, 128)  # stitches
        return shade(c, 0.93 + rng.random() * 0.1)
    return p


def lens(seed):
    """Smoky quartz lens glass: a brown-grey gradient with a bright highlight streak and a dark rim every 8 px."""
    def p(x, y, rng):
        lx, ly = x % 8, y % 8
        c = mix((74, 62, 54), (138, 120, 104), ly / 7.0)
        if lx in (0, 7) or ly in (0, 7):
            c = (40, 30, 24)
        elif lx + ly in (4, 5):
            c = (226, 224, 214)  # the highlight
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def purple_cloud(seed):
    """Purple silk with gold cloud scrolls (xiangyun): spirals on a 16 px grid, a silk twill."""
    n = noise2(random.Random(seed), W, BAND, 5)

    def p(x, y, rng):
        c = shade((84, 44, 112), 0.85 + 0.25 * n(x, y))
        if (x + y) % 5 == 0:
            c = shade(c, 1.12)
        cx, cy = wrapd(x - 8, 16), wrapd(y - 8, 16)
        r = math.hypot(cx, cy)
        a = math.atan2(cy, cx)
        spiral = (r - 1.1 * a) % 3.2
        if 1.0 < r < 6.0 and spiral < 0.8:
            c = (214, 178, 84)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def dark_straw(seed):
    """Tarred straw: a tight weave in near-black browns with a glossy tar sheen."""
    def p(x, y, rng):
        weave = ((x // 2) + (y // 2)) % 2
        c = (74, 58, 34) if weave else (52, 40, 24)
        if x % 2 == (y // 2) % 2:
            c = shade(c, 0.82)
        sheen = max(0.0, math.sin(x * 0.4 + y * 0.9)) ** 10
        c = mix(c, (150, 130, 96), sheen * 0.5)
        return shade(c, 0.92 + rng.random() * 0.12)
    return p


def linen(seed):
    """Starched white linen: soft vertical pleats every 4 px, a faint weave, a crisp shadow under each fold."""
    def p(x, y, rng):
        f = x % 4
        v = (244, 242, 236) if f in (1, 2) else ((224, 222, 214) if f == 0 else (206, 204, 198))
        c = v
        if (x + 2 * y) % 7 == 0:
            c = shade(c, 0.96)
        if y % 16 == 0:
            c = shade(c, 0.86)
        return shade(c, 0.98 + rng.random() * 0.03)
    return p


def sandalwood_beads(seed):
    """Polished sandalwood beads 4 px apart: dark red-brown spheres with a bright spot and a thin cord between."""
    def p(x, y, rng):
        cx, cy = x % 4 - 1.5, y % 4 - 1.5
        r = math.hypot(cx, cy)
        if r < 1.9:
            c = mix((150, 70, 44), (70, 28, 18), r / 1.9)
            if cx < -0.4 and cy < -0.4:
                c = (232, 190, 150)
        else:
            c = (40, 22, 16)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def wicker(seed):
    """Bamboo wicker: wide horizontal splints woven over thin uprights, green-gold bamboo with nodes."""
    def p(x, y, rng):
        row, col = y // 3, x // 4
        over = (row + col) % 2 == 0
        if over:
            c = mix((196, 170, 96), (160, 136, 70), (y % 3) / 2.0)
        else:
            c = (118, 96, 50) if x % 4 in (1, 2) else (84, 66, 34)
        if x % 16 == 0 and over:
            c = shade(c, 0.78)  # a bamboo node
        return shade(c, 0.93 + rng.random() * 0.1)
    return p


def fur(seed):
    """Brown fur: wavy strands with darker roots and light tips, a soft fbm of thickness."""
    rng0 = random.Random(seed)
    phase = [rng0.random() * 6.28 for _ in range(W)]
    n = noise2(random.Random(seed + 3), W, BAND, 6)

    def p(x, y, rng):
        g = 0.5 + 0.5 * math.sin(y * 0.7 + phase[x] + math.sin(x * 0.5) * 1.5)
        c = mix((70, 44, 24), (168, 124, 76), g * 0.8 + n(x, y) * 0.2)
        if x % 2 == 0 and g > 0.85:
            c = mix(c, (232, 210, 170), 0.5)
        return shade(c, 0.92 + rng.random() * 0.12)
    return p


def star_gold(seed):
    """Embossed gold: a five-pointed star every 8 px with a raised light edge, gold grain around."""
    n = noise2(random.Random(seed), W, BAND, 4)

    def star(px, py):
        a = math.atan2(py, px)
        r = math.hypot(px, py)
        k = 0.5 + 0.5 * math.cos(5 * (a + math.pi / 2))
        return r < 1.4 + 1.8 * k

    def p(x, y, rng):
        c = mix((168, 120, 34), (236, 196, 88), n(x, y))
        cx, cy = x % 8 - 3.5, y % 8 - 3.5
        if star(cx, cy):
            c = (255, 232, 140)
            if not star(cx + 0.7, cy + 0.7):
                c = (130, 90, 24)  # the emboss shadow
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def silver(seed):
    """Polished silver: cool grey gradient, engraved scroll lines, sparkle points."""
    n = noise2(random.Random(seed), W, BAND, 6)

    def p(x, y, rng):
        c = mix((150, 156, 168), (230, 234, 240), n(x, y))
        if (x * 2 + y * 3) % 11 == 0 or (x * 3 - y * 2) % 13 == 0:
            c = shade(c, 0.72)  # engraving
        if rng.random() < 0.015:
            c = (255, 255, 255)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def bone(seed):
    """Old bone and ivory: warm off-white, hairline cracks, darker pores."""
    n = noise2(random.Random(seed), W, BAND, 5)

    def p(x, y, rng):
        c = mix((196, 184, 156), (236, 228, 206), n(x, y))
        if abs(math.sin(x * 0.9 + y * 0.3 + n(x, y) * 4)) < 0.05:
            c = shade(c, 0.7)
        if rng.random() < 0.03:
            c = shade(c, 0.8)
        return shade(c, 0.97 + rng.random() * 0.05)
    return p


def plume(seed):
    """Crimson horsehair: long vertical strands, deep red roots to bright tips, separated by dark gaps."""
    rng0 = random.Random(seed)
    phase = [rng0.random() * 6.28 for _ in range(W)]

    def p(x, y, rng):
        g = 0.5 + 0.5 * math.sin(phase[x] + y * 0.15)
        c = mix((110, 10, 18), (226, 48, 52), g)
        if x % 3 == 2:
            c = shade(c, 0.7)
        if y % 16 > 13:
            c = mix(c, (255, 120, 110), 0.3)  # the light tips
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def navy_gold(seed):
    """Navy silk with a gold double border line every 16 px and small gold anchor-dots, a twill sheen."""
    n = noise2(random.Random(seed), W, BAND, 5)

    def p(x, y, rng):
        c = shade((30, 44, 86), 0.88 + 0.22 * n(x, y))
        if (x + y) % 4 == 0:
            c = shade(c, 1.1)
        if y % 16 in (12, 14):
            c = (214, 176, 80)
        if y % 16 == 6 and x % 8 == 4:
            c = (236, 204, 110)
        return shade(c, 0.96 + rng.random() * 0.06)
    return p


def shark_tooth(seed):
    """Tooth enamel: a cream root fading to a white glossy tip, serrated edge marks, a root stain."""
    def p(x, y, rng):
        t = (y % 8) / 7.0
        c = mix((252, 250, 240), (196, 178, 140), t)
        if x % 4 == 0 and y % 2 == 0:
            c = shade(c, 0.82)  # serrations
        if y % 8 == 7:
            c = (120, 96, 70)
        return shade(c, 0.97 + rng.random() * 0.04)
    return p


def turban(seed):
    """Wound turban cloth: diagonal wrap folds in white with shadowed overlaps and one green stripe per wrap."""
    def p(x, y, rng):
        d = (x + 2 * y) % 12
        c = (244, 240, 230)
        if d < 2:
            c = (176, 170, 160)  # the overlap shadow
        elif d == 2:
            c = (214, 208, 196)
        elif d in (7, 8):
            c = (60, 134, 84)  # the green stripe
        return shade(c, 0.96 + rng.random() * 0.06)
    return p


def sea_silver(seed):
    """Silver tinged sea blue, engraved with rolling waves (curling crests every 8 px)."""
    n = noise2(random.Random(seed), W, BAND, 6)

    def p(x, y, rng):
        c = mix((120, 150, 176), (214, 230, 240), n(x, y))
        wave = y % 8 - (3.0 + 2.2 * math.sin(x * 0.785))
        if abs(wave) < 0.6:
            c = (64, 92, 130)
        elif -1.6 < wave < -0.6:
            c = (240, 248, 255)
        return shade(c, 0.96 + rng.random() * 0.05)
    return p


def gold_brocade(seed):
    """Gold silk brocade: a lozenge lattice with red centres, raised gold threads."""
    n = noise2(random.Random(seed), W, BAND, 4)

    def p(x, y, rng):
        c = mix((190, 140, 44), (246, 206, 104), n(x, y))
        dx, dy = wrapd(x, 8), wrapd(y, 8)
        k = abs(dx) + abs(dy)
        if k < 1.6:
            c = (170, 30, 34)
        elif 3.2 < k < 4.2:
            c = (150, 100, 26)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def sand_cloth(seed):
    """Sand gauze: a loose open weave in dune beige with dust streaks and darker crossing threads."""
    n = noise2(random.Random(seed), W, BAND, 7)

    def p(x, y, rng):
        c = mix((186, 160, 116), (222, 200, 158), n(x, y))
        if x % 3 == 0 or y % 3 == 0:
            c = shade(c, 0.88)
        if abs(math.sin(y * 0.4 + x * 0.05)) < 0.08:
            c = mix(c, (150, 120, 80), 0.4)  # a dust streak
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def desert_bronze(seed):
    """Desert bronze: warm metal with etched spiral bands and a green patina in the hollows."""
    n = noise2(random.Random(seed), W, BAND, 4)

    def p(x, y, rng):
        c = mix((146, 100, 46), (214, 160, 84), n(x, y))
        cx, cy = wrapd(x - 4, 8), wrapd(y - 4, 8)
        r = math.hypot(cx, cy)
        a = math.atan2(cy, cx)
        if (r - 0.6 * a) % 2.4 < 0.6 and r < 3.6:
            c = (92, 60, 26)
        if n((x * 5) % W, (y * 3) % BAND) > 0.86:
            c = mix(c, (86, 152, 126), 0.6)
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def blue_straw(seed):
    """Straw dyed indigo: a weave in two blues with light straw showing at the crossings."""
    def p(x, y, rng):
        weave = ((x // 2) + (y // 2)) % 2
        c = (54, 82, 138) if weave else (36, 58, 104)
        if x % 2 == (y // 2) % 2:
            c = mix(c, (190, 170, 110), 0.25)
        return shade(c, 0.92 + rng.random() * 0.1)
    return p


def sutra_cloth(seed):
    """Yellow cotton with red brushed sutra glyphs every 8 px in columns."""
    def p(x, y, rng):
        c = (232, 196, 74) if (x + y) % 2 else (220, 184, 64)
        gx, gy = x % 8, y % 8
        stroke = (gx == 3 and 1 <= gy <= 6) or (gy == 2 and 1 <= gx <= 5) or (gy == 5 and 2 <= gx <= 4) or \
                 (gx == 5 and gy in (4, 6))
        if stroke:
            c = (168, 26, 22)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def rope(seed):
    """Twisted hemp rope: diagonal strands with dark lay lines and loose fibres."""
    def p(x, y, rng):
        d = (x + y) % 4
        c = (190, 162, 112) if d in (1, 2) else ((150, 124, 80) if d == 0 else (110, 88, 56))
        if rng.random() < 0.04:
            c = (222, 200, 150)
        return shade(c, 0.94 + rng.random() * 0.08)
    return p


def black_iron(seed):
    """Pitted black iron: dark blued metal, hammer dents, rust in the pits, a dull edge highlight."""
    n = noise2(random.Random(seed), W, BAND, 4)
    m = noise2(random.Random(seed + 5), W, BAND, 3)

    def p(x, y, rng):
        c = mix((34, 36, 42), (78, 82, 92), n(x, y))
        if m(x, y) > 0.78:
            c = mix(c, (120, 56, 30), 0.7)  # rust
        if rng.random() < 0.04:
            c = shade(c, 0.6)  # pits
        if y % 8 == 0:
            c = shade(c, 1.35)
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def horn(seed):
    """Horn: black at the root shading into dark blood red at the tip, growth rings every 4 px."""
    n = noise2(random.Random(seed), W, BAND, 6)

    def p(x, y, rng):
        t = (y % 16) / 15.0
        c = mix((24, 18, 20), (120, 22, 30), 1.0 - t)
        c = shade(c, 0.85 + 0.3 * n(x, y))
        if y % 4 == 0:
            c = shade(c, 0.6)
        if x % 7 == 0:
            c = shade(c, 1.2)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


def snake(seed):
    """Snake scales: staggered rhombic scales in green-black with a yellow-green keel and dark seams."""
    def p(x, y, rng):
        row = y // 3
        sx = (x + (2 if row % 2 else 0)) % 4
        sy = y % 3
        c = mix((30, 52, 32), (70, 104, 48), 1.0 - abs(sx - 1.5) / 1.5)
        if sx == 0 or sy == 2:
            c = (14, 22, 16)
        elif sx == 2 and sy == 0:
            c = (170, 190, 70)  # the keel glint
        return shade(c, 0.94 + rng.random() * 0.1)
    return p


def charcoal(seed):
    """Charcoal hood cloth: near-black wool with a faint herringbone and soft folds."""
    n = noise2(random.Random(seed), W, BAND, 8)

    def p(x, y, rng):
        v = 34 + 18 * n(x, y)
        if (x + (y // 2) * 2) % 4 < 2 if (y // 4) % 2 else (x - (y // 2) * 2) % 4 < 2:
            v *= 1.12
        c = (v, v, v * 1.08)
        return shade(c, 0.95 + rng.random() * 0.08)
    return p


def rune_cloth(seed):
    """Black cloth stitched with smouldering red runes (a ring with a cross stroke) and an ember fringe line."""
    def p(x, y, rng):
        c = (26, 22, 26) if (x + y) % 2 else (32, 26, 30)
        gx, gy = wrapd(x - 8 - (8 if (y // 16) % 2 else 0), 16), wrapd(y - 8, 16)
        r = math.hypot(gx, gy)
        if 3.4 < r < 4.3 or (abs(gx) < 0.5 and abs(gy) < 3.2) or (abs(gy + gx) < 0.6 and r < 3.2):
            c = (150, 30, 24) if r > 3.6 else (214, 84, 44)
        if y % 16 == 15:
            c = (120, 30, 24)
        return shade(c, 0.95 + rng.random() * 0.06)
    return p


def blood_lacquer(seed):
    """Black-red lacquer: a deep sheen gradient, scattered gold leaf flecks, a fine crackle."""
    n = noise2(random.Random(seed), W, BAND, 6)

    def p(x, y, rng):
        c = mix((30, 8, 12), (112, 18, 26), n(x, y))
        gloss = max(0.0, math.sin((x + y * 0.7) * 0.3)) ** 10
        c = mix(c, (200, 90, 90), gloss * 0.4)
        if rng.random() < 0.025:
            c = (236, 196, 96)
        if abs(math.sin(x * 1.3 + y * 0.7 + n(x, y) * 5)) < 0.04:
            c = shade(c, 0.6)
        return shade(c, 0.95 + rng.random() * 0.07)
    return p


NEW_PAINTERS = [
    lacquer_gauze(16), lamellar_red(17), painted_hide(18), coin_brocade(19), scorched(20), lens(21), purple_cloud(22),
    dark_straw(23), linen(24), sandalwood_beads(25), wicker(26), fur(27), star_gold(28), silver(29), bone(30), plume(31),
    navy_gold(32), shark_tooth(33), turban(34), sea_silver(35), gold_brocade(36), sand_cloth(37), desert_bronze(38),
    blue_straw(39), sutra_cloth(40), rope(41), black_iron(42), horn(43), snake(44), charcoal(45), rune_cloth(46),
    blood_lacquer(47),
]
assert len(NEW_PAINTERS) == BANDS - OLD_BANDS


def paint_band(img, index, painter, seed):
    rng = random.Random(seed)
    oy = index * BAND
    for y in range(BAND):
        for x in range(W):
            c = painter(x, y, rng)
            a = 255
            if len(c) == 4:
                a = c[3]
            img.set(x, oy + y, (c[0], c[1], c[2], a))


def preview(img, path, y0, y1, factor=8):
    big = Image(img.width * factor, (y1 - y0) * factor)
    for y in range(y0, y1):
        for x in range(img.width):
            r, g, b, a = img.get(x, y)
            for yy in range(factor):
                for xx in range(factor):
                    big.set(x * factor + xx, (y - y0) * factor + yy, (r, g, b, 255))
    big.save(path)


def main():
    old = read_png(ATLAS)
    if old.width != W or old.height not in (BAND * OLD_BANDS, BAND * BANDS):
        raise SystemExit("unexpected atlas size %dx%d" % (old.width, old.height))
    img = Image(W, BAND * BANDS)
    for y in range(BAND * OLD_BANDS):
        for x in range(W):
            img.px[y * W + x] = list(old.px[y * W + x])
    for i, painter in enumerate(NEW_PAINTERS):
        paint_band(img, OLD_BANDS + i, painter, 3800 + OLD_BANDS + i)
    # bands 0-15 must be the old pixels exactly
    for y in range(BAND * OLD_BANDS):
        for x in range(W):
            if img.px[y * W + x] != list(old.px[y * W + x]):
                raise SystemExit("band copy mismatch at %d,%d" % (x, y))
    img.save(ATLAS)
    print("wrote", os.path.normpath(ATLAS), "%dx%d (%d bands)" % (W, BAND * BANDS, BANDS))
    if len(sys.argv) > 1:
        os.makedirs(sys.argv[1], exist_ok=True)
        preview(img, os.path.join(sys.argv[1], "perlengkapan_16_31_x8.png"), 16 * BAND, 32 * BAND, 4)
        preview(img, os.path.join(sys.argv[1], "perlengkapan_32_47_x8.png"), 32 * BAND, 48 * BAND, 4)
        print("previews in", sys.argv[1])


if __name__ == "__main__":
    main()
