"""Generates the textures of module S, Seragam, Lambang & Bendera (DESIGN 36.2.8).

textures/sekte/seragam/lambang_atlas.png  512x384 greyscale masks the client colours and stacks into each hall's emblem:
    row 0 (y 0-63)    the 8 shape fills  (round, square seal, octagon, lozenge, cloud scroll, flame, shield,
                      swallow-tail banner); white with alpha = coverage, a soft silk sheen in the grey
    row 1 (y 64-127)  the 8 shape borders (a 5 px band inside the edge, bevelled light top-left, dark bottom-right)
    rows 2-5          the 32 symbols, 8 per row (sword, lotus, crane, dragon, phoenix, tiger, tortoise, mountain,
                      cloud, flame, waves, crescent moon, sun, taiji, qian trigram, pine, plum blossom, bamboo,
                      bell, gourd, fan, pagoda, Northern Dipper, thunder, bi jade disc, crossed swords, feather,
                      koi, ginkgo leaf, ding cauldron, scroll, heaven's eye), each centred in a 64x64 cell with
                      an embossed top edge
textures/sekte/seragam/kain_tenun.png     32x32 silk weave (grey, multiplied over the banner cloth)
textures/sekte/seragam/bendera_hiasan.png 64x64 banner fittings: gilt spear-tip finial (0,0,16,32), red silk tassel
                      (16,0,16,32), lacquered arm (32,0,32,8), lacquered pole (32,8,8,32), stone foot (40,8,16,16)
textures/sekte/seragam/decal_cincin.png   64x64 the ring under a formation decal: a gold double ring with
                      eight trigram ticks and a soft glow (white, tinted in game)
textures/entity/robe_base_pendek.png      the robe base (greyscale, player-skin layout) for the Jubah Pendek cut:
                      leggings bound with cross straps below the knee, the trouser hem layer cleared
textures/entity/robe_base_tempur.png      the Jubah Tempur cut: sleeves rolled to the elbow with leather bracers,
                      a wide plated belt, quilted shoulder guards, bound shins, a slit skirt front

Robe variants start from robe_base.png (read with the small PNG decoder below), so run tools/gen_visual_textures.py
first if robe_base.png changes. Run with: python tools/gen_sekte_seragam_textures.py
"""
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")
OUT = os.path.join(ROOT, "sekte", "seragam")
ENTITY = os.path.join(ROOT, "entity")

SS = 3  # supersamples per axis


# ======================================================================================== png reading

def read_png(path):
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    pos = 8
    idat = b""
    width = height = 0
    color = depth = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, color = struct.unpack(">IIBB", body[:10])
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
    assert depth == 8 and color in (2, 6), (path, depth, color)
    bpp = 4 if color == 6 else 3
    raw = zlib.decompress(idat)
    stride = width * bpp
    rows = []
    prev = bytearray(stride)
    i = 0
    for _ in range(height):
        filt = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if filt == 1:
                line[x] = (line[x] + a) & 255
            elif filt == 2:
                line[x] = (line[x] + b) & 255
            elif filt == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + pred) & 255
        rows.append(line)
        prev = line
    img = Image(width, height)
    for y in range(height):
        for x in range(width):
            o = x * bpp
            px = rows[y][o:o + bpp]
            img.set(x, y, (px[0], px[1], px[2], px[3] if bpp == 4 else 255))
    return img


# ======================================================================================== geometry (unit square, y down)

def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ellipse(cx, cy, rx, ry, angle=0.0):
    ca, sa = math.cos(angle), math.sin(angle)

    def f(x, y):
        dx, dy = x - cx, y - cy
        u = dx * ca + dy * sa
        v = -dx * sa + dy * ca
        return (u / rx) ** 2 + (v / ry) ** 2 <= 1.0
    return f


def rect(x0, y0, x1, y1):
    return lambda x, y: x0 <= x <= x1 and y0 <= y <= y1


def poly(points):
    pts = list(points)

    def f(x, y):
        inside = False
        j = len(pts) - 1
        for i in range(len(pts)):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi:
                inside = not inside
            j = i
        return inside
    return f


def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    t = max(0.0, min(1.0, (wx * vx + wy * vy) / (vx * vx + vy * vy + 1e-12)))
    dx, dy = px - (ax + t * vx), py - (ay + t * vy)
    return math.sqrt(dx * dx + dy * dy)


def stroke(points, w):
    pts = list(points)
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    bx0, bx1, by0, by1 = min(xs) - w, max(xs) + w, min(ys) - w, max(ys) + w

    def f(x, y):
        if x < bx0 or x > bx1 or y < by0 or y > by1:
            return False
        for i in range(len(pts) - 1):
            if seg_dist(x, y, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]) <= w:
                return True
        return False
    return f


def tapered(points, w0, w1):
    """A stroke whose half width runs from w0 at the first point to w1 at the last."""
    pts = list(points)
    n = len(pts) - 1

    def f(x, y):
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[i + 1]
            vx, vy = bx - ax, by - ay
            t = max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy + 1e-12)))
            dx, dy = x - (ax + t * vx), y - (ay + t * vy)
            w = w0 + (w1 - w0) * ((i + t) / n)
            if dx * dx + dy * dy <= w * w:
                return True
        return False
    return f


def bezier(p0, p1, p2, p3=None, steps=24):
    out = []
    for i in range(steps + 1):
        t = i / steps
        if p3 is None:
            x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
            y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        else:
            x = (1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t ** 3 * p3[0]
            y = (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t ** 3 * p3[1]
        out.append((x, y))
    return out


def arc_pts(cx, cy, r, a0, a1, steps=24):
    return [(cx + r * math.cos(a0 + (a1 - a0) * i / steps), cy + r * math.sin(a0 + (a1 - a0) * i / steps)) for i in range(steps + 1)]


def ring(cx, cy, r0, r1):
    return lambda x, y: r0 * r0 <= (x - cx) ** 2 + (y - cy) ** 2 <= r1 * r1


def union(*fs):
    return lambda x, y: any(f(x, y) for f in fs)


def minus(a, *bs):
    return lambda x, y: a(x, y) and not any(b(x, y) for b in bs)


def rotated(f, angle, cx=0.5, cy=0.5):
    ca, sa = math.cos(-angle), math.sin(-angle)

    def g(x, y):
        dx, dy = x - cx, y - cy
        return f(cx + dx * ca - dy * sa, cy + dx * sa + dy * ca)
    return g


def regular(cx, cy, r, n, start=-math.pi / 2):
    return [(cx + r * math.cos(start + 2 * math.pi * i / n), cy + r * math.sin(start + 2 * math.pi * i / n)) for i in range(n)]


def coverage(f, px, py, size=64):
    hit = 0
    for sy in range(SS):
        for sx in range(SS):
            x = (px + (sx + 0.5) / SS) / size
            y = (py + (sy + 0.5) / SS) / size
            if f(x, y):
                hit += 1
    return hit / (SS * SS)


# ======================================================================================== shapes (fill, border)

def cloud_scroll():
    body = union(circle(0.5, 0.5, 0.36), circle(0.24, 0.42, 0.2), circle(0.76, 0.42, 0.2), circle(0.34, 0.24, 0.17),
                 circle(0.66, 0.24, 0.17), circle(0.5, 0.18, 0.16), circle(0.3, 0.7, 0.18), circle(0.7, 0.7, 0.18), circle(0.5, 0.8, 0.14))
    return body


SHAPES = [
    circle(0.5, 0.5, 0.47),                                                      # round
    poly([(0.05, 0.05), (0.95, 0.05), (0.95, 0.95), (0.05, 0.95)]),              # square seal
    poly(regular(0.5, 0.5, 0.48, 8, -math.pi / 2 + math.pi / 8)),                # octagon (bagua)
    poly([(0.5, 0.02), (0.98, 0.5), (0.5, 0.98), (0.02, 0.5)]),                  # lozenge
    cloud_scroll(),                                                              # cloud scroll
    poly(bezier((0.5, 0.02), (0.62, 0.22), (0.92, 0.42), (0.86, 0.68)) + bezier((0.86, 0.68), (0.8, 0.98), (0.2, 0.98), (0.14, 0.68))
         + bezier((0.14, 0.68), (0.08, 0.42), (0.38, 0.22), (0.5, 0.02))),        # flame
    poly([(0.08, 0.06), (0.92, 0.06), (0.92, 0.48)] + bezier((0.92, 0.48), (0.9, 0.78), (0.6, 0.9), (0.5, 0.98))
         + bezier((0.5, 0.98), (0.4, 0.9), (0.1, 0.78), (0.08, 0.48))),           # shield
    poly([(0.1, 0.04), (0.9, 0.04), (0.9, 0.96), (0.5, 0.72), (0.1, 0.96)]),     # swallow-tail banner tongue
]


def border_of(fill, width):
    """Cells of the shape whose distance to the outside is below {@code width} (unit)."""
    offsets = [(width * math.cos(a), width * math.sin(a)) for a in [i * math.pi / 8 for i in range(16)]]

    def f(x, y):
        if not fill(x, y):
            return False
        for dx, dy in offsets:
            if not fill(x + dx, y + dy):
                return True
        return False
    return f


# ======================================================================================== symbols

def sword(angle=0.0, cx=0.5):
    blade = poly([(cx, 0.06), (cx + 0.07, 0.18), (cx + 0.06, 0.66), (cx - 0.06, 0.66), (cx - 0.07, 0.18)])
    guard = poly([(cx - 0.22, 0.66), (cx + 0.22, 0.66), (cx + 0.17, 0.73), (cx - 0.17, 0.73)])
    grip = rect(cx - 0.035, 0.73, cx + 0.035, 0.88)
    pommel = circle(cx, 0.91, 0.055)
    fuller = rect(cx - 0.012, 0.2, cx + 0.012, 0.62)
    f = minus(union(blade, guard, grip, pommel), fuller)
    return rotated(f, angle) if angle else f


def lotus():
    pivot = (0.5, 0.74)
    petals = []
    for a, length, width in ((0.0, 0.3, 0.1), (0.42, 0.27, 0.095), (-0.42, 0.27, 0.095), (0.85, 0.23, 0.085), (-0.85, 0.23, 0.085)):
        petals.append(rotated(ellipse(0.5, pivot[1] - length, width, length), a, pivot[0], pivot[1]))
    veins = union(*[rotated(stroke([(0.5, pivot[1] - 0.08), (0.5, pivot[1] - 0.46)], 0.011), a, pivot[0], pivot[1]) for a in (0.0, 0.42, -0.42)])
    base = minus(ellipse(0.5, 0.8, 0.4, 0.1), ellipse(0.5, 0.72, 0.36, 0.08))
    stem = stroke(bezier((0.5, 0.88), (0.46, 0.93), (0.52, 0.97)), 0.03)
    return union(minus(union(*petals), veins), base, stem)


def crane():
    body = ellipse(0.5, 0.56, 0.2, 0.12, -0.25)
    neck = tapered(bezier((0.62, 0.48), (0.72, 0.3), (0.62, 0.2), (0.72, 0.12)), 0.035, 0.025)
    head = circle(0.74, 0.11, 0.04)
    beak = poly([(0.76, 0.09), (0.94, 0.14), (0.76, 0.14)])
    wing = poly([(0.36, 0.5), (0.08, 0.28), (0.18, 0.36), (0.06, 0.42), (0.2, 0.48), (0.1, 0.56), (0.42, 0.6)])
    tail = poly([(0.3, 0.6), (0.12, 0.7), (0.32, 0.66)])
    legs = union(stroke([(0.48, 0.66), (0.44, 0.92)], 0.014), stroke([(0.54, 0.66), (0.58, 0.92)], 0.014),
                 stroke([(0.44, 0.92), (0.36, 0.94)], 0.012), stroke([(0.58, 0.92), (0.66, 0.94)], 0.012))
    return union(body, neck, head, beak, wing, tail, legs)


def dragon():
    path = bezier((0.14, 0.9), (-0.06, 0.48), (0.98, 0.66), (0.66, 0.3)) + bezier((0.66, 0.3), (0.54, 0.14), (0.32, 0.16), (0.36, 0.3))[1:]
    body = tapered(path, 0.035, 0.09)
    head = union(ellipse(0.38, 0.3, 0.13, 0.085, 0.4), poly([(0.28, 0.24), (0.08, 0.2), (0.26, 0.33)]))
    horns = union(stroke([(0.43, 0.22), (0.54, 0.05)], 0.02), stroke([(0.37, 0.22), (0.39, 0.04)], 0.02))
    whisker = union(stroke(bezier((0.26, 0.32), (0.12, 0.42), (0.06, 0.32)), 0.014), stroke(bezier((0.3, 0.36), (0.22, 0.5), (0.1, 0.48)), 0.012))
    spikes = []
    for i in range(3, len(path) - 8, 4):
        x, y = path[i]
        nx, ny = path[i + 1][0] - x, path[i + 1][1] - y
        ln = math.sqrt(nx * nx + ny * ny) + 1e-9
        px, py = -ny / ln, nx / ln
        w = 0.035 + 0.055 * (i / len(path))
        spikes.append(poly([(x, y), (x + px * (w + 0.06) + nx * 0.5, y + py * (w + 0.06) + ny * 0.5), (x + nx * 1.8, y + ny * 1.8)]))
    pearl = circle(0.18, 0.12, 0.07)
    eye = circle(0.39, 0.27, 0.02)
    scales = union(*[stroke([(path[i][0] - 0.02, path[i][1]), (path[i][0] + 0.02, path[i][1] + 0.02)], 0.008) for i in range(6, len(path) - 10, 3)])
    return union(minus(union(body, head, horns, *spikes), eye, scales), whisker, minus(pearl, circle(0.16, 0.1, 0.025)))


def phoenix():
    body = ellipse(0.46, 0.46, 0.17, 0.11, -0.45)
    neck = tapered(bezier((0.56, 0.4), (0.64, 0.3), (0.58, 0.22)), 0.06, 0.045)
    head = circle(0.6, 0.2, 0.065)
    beak = poly([(0.64, 0.17), (0.78, 0.21), (0.64, 0.24)])
    crest = union(*[tapered(bezier((0.58, 0.15), (0.56 - 0.04 * k, 0.06), (0.66 + 0.06 * k, 0.03)), 0.018, 0.008) for k in range(3)])
    shoulder = (0.44, 0.4)
    wing = union(*[rotated(ellipse(shoulder[0], shoulder[1] - 0.2 - 0.02 * k, 0.045, 0.2 + 0.03 * k), -0.5 - 0.28 * k, shoulder[0], shoulder[1])
                   for k in range(4)])
    tails = union(tapered(bezier((0.36, 0.52), (0.24, 0.72), (0.52, 0.8), (0.34, 0.96)), 0.06, 0.02),
                  tapered(bezier((0.4, 0.54), (0.56, 0.7), (0.82, 0.68), (0.9, 0.9)), 0.055, 0.02),
                  tapered(bezier((0.32, 0.5), (0.1, 0.56), (0.16, 0.82), (0.06, 0.9)), 0.045, 0.016))
    eyes = union(circle(0.34, 0.95, 0.045), circle(0.9, 0.9, 0.04), circle(0.06, 0.9, 0.035))
    eye_holes = union(circle(0.34, 0.95, 0.018), circle(0.9, 0.9, 0.016), circle(0.06, 0.9, 0.014), circle(0.61, 0.19, 0.016))
    return minus(union(body, neck, head, beak, crest, wing, tails, eyes), eye_holes)


def tiger():
    head = union(ellipse(0.5, 0.56, 0.36, 0.32), circle(0.22, 0.26, 0.11), circle(0.78, 0.26, 0.11))
    ears_in = union(circle(0.22, 0.27, 0.05), circle(0.78, 0.27, 0.05))
    eyes = union(poly([(0.28, 0.5), (0.42, 0.46), (0.4, 0.54)]), poly([(0.72, 0.5), (0.58, 0.46), (0.6, 0.54)]))
    wang = union(rect(0.42, 0.3, 0.58, 0.325), rect(0.44, 0.36, 0.56, 0.38), rect(0.4, 0.415, 0.6, 0.44), rect(0.488, 0.3, 0.512, 0.44))
    stripes = union(stroke([(0.15, 0.5), (0.27, 0.58)], 0.02), stroke([(0.85, 0.5), (0.73, 0.58)], 0.02),
                    stroke([(0.17, 0.64), (0.3, 0.68)], 0.02), stroke([(0.83, 0.64), (0.7, 0.68)], 0.02))
    nose = poly([(0.44, 0.62), (0.56, 0.62), (0.5, 0.69)])
    mouth = union(stroke(bezier((0.5, 0.69), (0.46, 0.78), (0.38, 0.76)), 0.014), stroke(bezier((0.5, 0.69), (0.54, 0.78), (0.62, 0.76)), 0.014))
    return union(minus(head, ears_in, eyes, wang, stripes, nose, mouth))


def tortoise():
    shell = ellipse(0.5, 0.52, 0.3, 0.24)
    lines = union(poly(regular(0.5, 0.52, 0.11, 6, 0)), )
    hexes = union(ring(0.5, 0.52, 0.0, 0.0), stroke(regular(0.5, 0.52, 0.12, 6, 0) + [regular(0.5, 0.52, 0.12, 6, 0)[0]], 0.012))
    spokes = union(*[stroke([(0.5 + 0.12 * math.cos(a), 0.52 + 0.12 * math.sin(a)), (0.5 + 0.3 * math.cos(a), 0.52 + 0.24 * math.sin(a))], 0.012)
                     for a in [i * math.pi / 3 for i in range(6)]])
    head = ellipse(0.5, 0.2, 0.07, 0.08)
    legs = union(ellipse(0.24, 0.32, 0.08, 0.05, 0.6), ellipse(0.76, 0.32, 0.08, 0.05, -0.6), ellipse(0.24, 0.74, 0.08, 0.05, -0.6),
                 ellipse(0.76, 0.74, 0.08, 0.05, 0.6))
    tail = poly([(0.47, 0.75), (0.53, 0.75), (0.5, 0.86)])
    snake = tapered(bezier((0.5, 0.86), (0.72, 0.98), (0.92, 0.7), (0.84, 0.5)), 0.02, 0.012)
    del lines
    return union(minus(shell, hexes, spokes), head, legs, tail, snake)


def mountain():
    peaks = poly([(0.04, 0.86), (0.26, 0.42), (0.36, 0.56), (0.52, 0.14), (0.68, 0.5), (0.76, 0.36), (0.96, 0.86)])
    snow = union(poly([(0.52, 0.14), (0.6, 0.32), (0.55, 0.28), (0.5, 0.34), (0.45, 0.28)]))
    base = rect(0.04, 0.86, 0.96, 0.92)
    return union(minus(peaks, snow, stroke([(0.52, 0.36), (0.44, 0.6)], 0.012), stroke([(0.27, 0.5), (0.22, 0.66)], 0.01)), base)


def cloud():
    body = union(circle(0.3, 0.56, 0.16), circle(0.52, 0.44, 0.22), circle(0.74, 0.56, 0.15), rect(0.16, 0.56, 0.88, 0.72),
                 circle(0.16, 0.64, 0.08), circle(0.88, 0.64, 0.08))
    spiral = stroke([(0.52 + (0.03 + 0.12 * t / 30) * math.cos(t * 0.42), 0.46 + (0.03 + 0.12 * t / 30) * math.sin(t * 0.42)) for t in range(31)], 0.018)
    spiral2 = stroke([(0.3 + (0.02 + 0.08 * t / 24) * math.cos(-t * 0.45), 0.58 + (0.02 + 0.08 * t / 24) * math.sin(-t * 0.45)) for t in range(25)], 0.014)
    base = stroke([(0.12, 0.72), (0.9, 0.72)], 0.016)
    tail = tapered(bezier((0.86, 0.72), (0.98, 0.84), (0.8, 0.92), (0.9, 0.98)), 0.035, 0.012)
    return union(minus(body, spiral, spiral2, base), tail)


def flame():
    outer = poly(bezier((0.5, 0.04), (0.66, 0.3), (0.9, 0.46), (0.78, 0.76)) + bezier((0.78, 0.76), (0.7, 0.96), (0.3, 0.96), (0.22, 0.76))
                 + bezier((0.22, 0.76), (0.12, 0.52), (0.34, 0.36), (0.5, 0.04)))
    side = union(poly(bezier((0.2, 0.3), (0.3, 0.46), (0.3, 0.6), (0.22, 0.7)) + [(0.16, 0.56)]),
                 poly(bezier((0.82, 0.24), (0.72, 0.42), (0.74, 0.58), (0.8, 0.68)) + [(0.88, 0.5)]))
    inner = poly(bezier((0.5, 0.42), (0.6, 0.56), (0.66, 0.66), (0.6, 0.78)) + bezier((0.6, 0.78), (0.54, 0.86), (0.46, 0.86), (0.4, 0.78))
                 + bezier((0.4, 0.78), (0.34, 0.66), (0.42, 0.56), (0.5, 0.42)))
    return minus(union(outer, side), inner)


def waves():
    out = []
    for i, y in enumerate((0.3, 0.52, 0.74)):
        pts = []
        for k in range(41):
            t = k / 40
            pts.append((0.06 + 0.88 * t, y + 0.06 * math.sin(t * math.pi * 4 + i)))
        out.append(stroke(pts, 0.045))
    crest = union(ring(0.3, 0.2, 0.05, 0.08), ring(0.72, 0.42, 0.04, 0.07))
    return union(*out, minus(union(circle(0.3, 0.2, 0.08), circle(0.72, 0.42, 0.07)), circle(0.33, 0.22, 0.05), circle(0.74, 0.44, 0.04)), crest)


def moon():
    crescent = minus(circle(0.5, 0.5, 0.36), circle(0.64, 0.42, 0.3))
    stars = union(*[poly(regular(x, y, r, 4, 0) + []) for x, y, r in ((0.78, 0.76, 0.04), (0.84, 0.2, 0.03))])
    return union(crescent, stars)


def sun():
    disc = circle(0.5, 0.5, 0.22)
    rays = []
    for i in range(12):
        a = i * math.pi / 6
        ax, ay = 0.5 + 0.27 * math.cos(a), 0.5 + 0.27 * math.sin(a)
        rays.append(poly([(0.5 + 0.44 * math.cos(a), 0.5 + 0.44 * math.sin(a)), (ax + 0.05 * math.cos(a + math.pi / 2), ay + 0.05 * math.sin(a + math.pi / 2)),
                          (ax - 0.05 * math.cos(a + math.pi / 2), ay - 0.05 * math.sin(a + math.pi / 2))]))
    return union(minus(disc, ring(0.5, 0.5, 0.15, 0.17)), *rays)


def taiji():
    outer = ring(0.5, 0.5, 0.4, 0.46)
    yin = union(minus(lambda x, y: x >= 0.5 and (x - 0.5) ** 2 + (y - 0.5) ** 2 <= 0.4 * 0.4, circle(0.5, 0.3, 0.2)), circle(0.5, 0.7, 0.2))
    return union(outer, minus(yin, circle(0.5, 0.7, 0.06)), circle(0.5, 0.3, 0.06))


def trigram():
    bars = [rect(0.14, y, 0.86, y + 0.12) for y in (0.18, 0.44, 0.7)]
    return union(*bars)


def pine():
    tiers = [poly([(0.5, 0.06), (0.74, 0.32), (0.26, 0.32)]), poly([(0.5, 0.2), (0.82, 0.52), (0.18, 0.52)]),
             poly([(0.5, 0.38), (0.9, 0.74), (0.1, 0.74)])]
    trunk = rect(0.45, 0.72, 0.55, 0.94)
    return union(minus(union(*tiers), stroke([(0.3, 0.3), (0.5, 0.26), (0.7, 0.3)], 0.012), stroke([(0.22, 0.5), (0.5, 0.45), (0.78, 0.5)], 0.012)), trunk)


def plum():
    petals = [circle(0.5 + 0.22 * math.cos(a), 0.5 + 0.22 * math.sin(a), 0.17) for a in [-math.pi / 2 + i * 2 * math.pi / 5 for i in range(5)]]
    heart = circle(0.5, 0.5, 0.09)
    stamens = union(*[stroke([(0.5, 0.5), (0.5 + 0.15 * math.cos(a), 0.5 + 0.15 * math.sin(a))], 0.01)
                      for a in [-math.pi / 2 + math.pi / 5 + i * 2 * math.pi / 5 for i in range(5)]])
    return union(minus(union(*petals), heart, stamens), circle(0.5, 0.5, 0.04))


def bamboo():
    stalks = []
    for x, top in ((0.38, 0.06), (0.62, 0.18)):
        s = rect(x - 0.06, top, x + 0.06, 0.96)
        nodes = union(*[rect(x - 0.07, y, x + 0.07, y + 0.02) for y in (top + 0.24, top + 0.5, top + 0.76) if y < 0.94])
        gaps = union(*[rect(x - 0.06, y + 0.025, x + 0.06, y + 0.035) for y in (top + 0.24, top + 0.5, top + 0.76) if y < 0.94])
        stalks.append(minus(union(s, nodes), gaps))
    leaves = union(ellipse(0.2, 0.3, 0.16, 0.04, -0.5), ellipse(0.8, 0.44, 0.16, 0.04, 0.4), ellipse(0.82, 0.6, 0.14, 0.035, -0.3),
                   ellipse(0.18, 0.5, 0.14, 0.035, 0.3))
    return union(*stalks, leaves)


def bell():
    body = poly([(0.32, 0.22), (0.68, 0.22)] + bezier((0.68, 0.22), (0.72, 0.5), (0.86, 0.72), (0.88, 0.78)) + [(0.12, 0.78)]
                + bezier((0.12, 0.78), (0.14, 0.72), (0.28, 0.5), (0.32, 0.22)))
    loop = ring(0.5, 0.15, 0.04, 0.08)
    bands = union(rect(0.14, 0.66, 0.86, 0.69), rect(0.28, 0.36, 0.72, 0.39))
    clapper = circle(0.5, 0.86, 0.06)
    studs = union(*[circle(x, 0.52, 0.025) for x in (0.36, 0.5, 0.64)])
    return union(minus(body, bands, studs), loop, clapper)


def gourd():
    body = union(circle(0.5, 0.66, 0.24), circle(0.5, 0.34, 0.15), rect(0.42, 0.4, 0.58, 0.5))
    waist = union(stroke([(0.36, 0.48), (0.64, 0.48)], 0.02))
    stem = stroke(bezier((0.5, 0.2), (0.5, 0.1), (0.58, 0.06)), 0.025)
    leaf = ellipse(0.66, 0.12, 0.1, 0.045, -0.4)
    cord = stroke(bezier((0.36, 0.48), (0.22, 0.6), (0.3, 0.8)), 0.015)
    return union(minus(body, waist), stem, leaf, cord)


def fan():
    sector = lambda x, y: (x - 0.5) ** 2 + (y - 0.82) ** 2 <= 0.44 ** 2 and (x - 0.5) ** 2 + (y - 0.82) ** 2 >= 0.12 ** 2 and y <= 0.82 - abs(x - 0.5) * 0.25
    ribs = union(*[stroke([(0.5 + 0.13 * math.cos(a), 0.82 - 0.13 * math.sin(a)), (0.5 + 0.43 * math.cos(a), 0.82 - 0.43 * math.sin(a))], 0.01)
                   for a in [math.pi * (0.16 + 0.68 * i / 7) for i in range(8)]])
    handle = union(circle(0.5, 0.82, 0.05), rect(0.48, 0.82, 0.52, 0.96))
    return union(minus(sector, ribs), handle)


def pagoda():
    parts = [rect(0.47, 0.04, 0.53, 0.16)]
    y = 0.16
    for i, w in enumerate((0.16, 0.22, 0.28, 0.34)):
        parts.append(poly([(0.5 - w - 0.06, y + 0.06), (0.5 + w + 0.06, y + 0.06), (0.5 + w, y), (0.5 - w, y)]))
        parts.append(rect(0.5 - w * 0.7, y + 0.06, 0.5 + w * 0.7, y + 0.18))
        y += 0.18
    door = rect(0.46, 0.84, 0.54, 0.9)
    return minus(union(*parts, rect(0.18, 0.88, 0.82, 0.94)), door)


def dipper():
    stars = [(0.1, 0.2), (0.3, 0.26), (0.44, 0.38), (0.58, 0.5), (0.62, 0.76), (0.86, 0.82), (0.9, 0.56)]
    lines = stroke(stars + [stars[3]], 0.012)
    dots = union(*[poly(regular(x, y, 0.06, 5, -math.pi / 2)[0:5]) for x, y in stars], *[circle(x, y, 0.035) for x, y in stars])
    return union(lines, dots)


def thunder():
    bolt = poly([(0.56, 0.04), (0.26, 0.52), (0.46, 0.52), (0.34, 0.96), (0.76, 0.4), (0.54, 0.4), (0.7, 0.04)])
    spiral = stroke([(0.12, 0.2), (0.24, 0.2), (0.24, 0.32), (0.16, 0.32), (0.16, 0.26)], 0.018)
    spiral2 = stroke([(0.88, 0.72), (0.76, 0.72), (0.76, 0.84), (0.84, 0.84), (0.84, 0.78)], 0.018)
    return union(bolt, spiral, spiral2)


def bi_disc():
    disc = ring(0.5, 0.5, 0.15, 0.44)
    grains = union(*[circle(0.5 + r * math.cos(a), 0.5 + r * math.sin(a), 0.028) for r, n in ((0.25, 10), (0.36, 16))
                     for a in [i * 2 * math.pi / n + (0.2 if n == 16 else 0) for i in range(n)]])
    return minus(disc, grains)


def crossed_swords():
    return union(sword(math.pi / 4.5), sword(-math.pi / 4.5))


def feather():
    vane = poly(bezier((0.5, 0.04), (0.86, 0.3), (0.74, 0.7), (0.52, 0.82)) + bezier((0.48, 0.82), (0.26, 0.7), (0.14, 0.3), (0.5, 0.04)))
    shaft = stroke([(0.5, 0.1), (0.5, 0.96)], 0.016)
    barbs = union(*[stroke([(0.5, y), (0.5 + s * 0.24, y - 0.1)], 0.009) for y in (0.3, 0.44, 0.58, 0.7) for s in (-1, 1)])
    eye = union(ring(0.5, 0.3, 0.05, 0.08))
    return union(minus(vane, barbs, eye), shaft)


def koi():
    body = ellipse(0.44, 0.48, 0.3, 0.16, 0.35)
    tail = poly([(0.7, 0.62), (0.96, 0.56), (0.86, 0.72), (0.94, 0.9)])
    fins = union(poly([(0.42, 0.34), (0.5, 0.16), (0.58, 0.38)]), poly([(0.38, 0.6), (0.34, 0.76), (0.48, 0.64)]))
    eye = circle(0.24, 0.38, 0.03)
    gill = stroke(bezier((0.3, 0.32), (0.34, 0.42), (0.3, 0.52)), 0.01)
    whisker = stroke(bezier((0.16, 0.44), (0.08, 0.5), (0.1, 0.58)), 0.01)
    scales = union(*[ring(x, y, 0.035, 0.045) for x, y in ((0.42, 0.44), (0.52, 0.5), (0.48, 0.58), (0.6, 0.56))])
    return union(minus(union(body, tail, fins), eye, gill, scales), whisker)


def ginkgo():
    lobes = union(rotated(ellipse(0.5, 0.36, 0.2, 0.3), 0.5, 0.5, 0.74), rotated(ellipse(0.5, 0.36, 0.2, 0.3), -0.5, 0.5, 0.74),
                  ellipse(0.5, 0.5, 0.12, 0.22))
    leaf = minus(lobes, poly([(0.5, 0.5), (0.43, 0.04), (0.57, 0.04)]))
    veins = union(*[rotated(stroke([(0.5, 0.7), (0.5, 0.12)], 0.007), a, 0.5, 0.74) for a in (-0.75, -0.45, -0.15, 0.15, 0.45, 0.75)])
    stem = stroke(bezier((0.5, 0.68), (0.5, 0.86), (0.6, 0.97)), 0.022)
    return union(minus(leaf, veins), stem)


def ding():
    bowl = poly([(0.16, 0.36), (0.84, 0.36)] + bezier((0.84, 0.36), (0.84, 0.66), (0.66, 0.74), (0.5, 0.74)) + bezier((0.5, 0.74), (0.34, 0.74), (0.16, 0.66), (0.16, 0.36)))
    rim = rect(0.12, 0.3, 0.88, 0.37)
    handles = union(minus(rect(0.22, 0.1, 0.34, 0.31), rect(0.26, 0.15, 0.3, 0.31)), minus(rect(0.66, 0.1, 0.78, 0.31), rect(0.7, 0.15, 0.74, 0.31)))
    legs = union(poly([(0.24, 0.66), (0.32, 0.7), (0.26, 0.94), (0.2, 0.94)]), poly([(0.76, 0.66), (0.68, 0.7), (0.74, 0.94), (0.8, 0.94)]),
                 rect(0.46, 0.72, 0.54, 0.94))
    pattern = union(stroke([(0.26, 0.48), (0.36, 0.42), (0.46, 0.48), (0.54, 0.42), (0.64, 0.48), (0.74, 0.42)], 0.012), circle(0.5, 0.58, 0.035))
    return union(minus(union(bowl, rim), pattern), handles, legs)


def scroll():
    paper = rect(0.2, 0.2, 0.8, 0.8)
    rods = union(rect(0.12, 0.14, 0.88, 0.22), rect(0.12, 0.78, 0.88, 0.86), circle(0.12, 0.18, 0.05), circle(0.88, 0.18, 0.05),
                 circle(0.12, 0.82, 0.05), circle(0.88, 0.82, 0.05))
    text = union(*[rect(x, 0.3, x + 0.04, 0.7) for x in (0.32, 0.46, 0.6)], rect(0.28, 0.48, 0.72, 0.51))
    seal = rect(0.66, 0.62, 0.74, 0.7)
    return union(minus(paper, text, seal), rods)


def eye():
    almond = poly(bezier((0.06, 0.5), (0.3, 0.18), (0.7, 0.18), (0.94, 0.5)) + bezier((0.94, 0.5), (0.7, 0.82), (0.3, 0.82), (0.06, 0.5)))
    iris = circle(0.5, 0.5, 0.18)
    pupil = ellipse(0.5, 0.5, 0.05, 0.14)
    rays = union(*[stroke([(0.5 + 0.34 * math.cos(a), 0.5 + 0.34 * math.sin(a)), (0.5 + 0.46 * math.cos(a), 0.5 + 0.46 * math.sin(a))], 0.014)
                   for a in [-math.pi / 2 + (i - 2) * 0.32 for i in range(5)]])
    return union(minus(almond, ring(0.5, 0.5, 0.18, 0.24)), minus(iris, pupil, circle(0.44, 0.44, 0.04)), rays)


SYMBOLS = [sword(), lotus(), crane(), dragon(), phoenix(), tiger(), tortoise(), mountain(),
           cloud(), flame(), waves(), moon(), sun(), taiji(), trigram(), pine(),
           plum(), bamboo(), bell(), gourd(), fan(), pagoda(), dipper(), thunder(),
           bi_disc(), crossed_swords(), feather(), koi(), ginkgo(), ding(), scroll(), eye()]


# ======================================================================================== atlas

def paint_cell(img, ox, oy, f, shade, size=64):
    """Coverage of f into a 64x64 cell; shade(u, v, edge) -> grey 0..255 (edge: how near the top-left lit edge)."""
    cov = [[coverage(f, x, y, size) for x in range(size)] for y in range(size)]
    for y in range(size):
        for x in range(size):
            a = cov[y][x]
            if a <= 0.0:
                continue
            # emboss: lit when the pixel up-left is outside, shaded when the pixel down-right is outside
            up = cov[y - 1][x - 1] if x > 0 and y > 0 else 0.0
            down = cov[y + 1][x + 1] if x < size - 1 and y < size - 1 else 0.0
            edge = (a - up) - (a - down)
            g = shade(x / size, y / size, edge)
            img.set(ox + x, oy + y, (g, g, g, int(round(a * 255))))


def atlas():
    img = Image(512, 384)
    for i, fill in enumerate(SHAPES):
        # fills: a soft silk sheen from the top-left
        paint_cell(img, i * 64, 0, fill, lambda u, v, e: int(214 + 34 * (1.0 - min(1.0, math.hypot(u - 0.32, v - 0.28) * 1.6)) + 6 * e))
        border = border_of(fill, 0.075)
        paint_cell(img, i * 64, 64, border, lambda u, v, e: int(max(120, min(255, 205 + 50 * e + 20 * (0.5 - v)))))
        print("shape", i)
    for i, sym in enumerate(SYMBOLS):
        # symbols drawn at 74% inside their cell so they sit inside every shape's border
        def scaled(x, y, sym=sym):
            return sym((x - 0.13) / 0.74, (y - 0.13) / 0.74)
        paint_cell(img, (i % 8) * 64, 128 + (i // 8) * 64, scaled, lambda u, v, e: int(max(150, min(255, 232 + 40 * e))))
        print("symbol", i)
    return img


def weave():
    img = Image(32, 32)
    for y in range(32):
        for x in range(32):
            warp = (x % 4 in (0, 1)) ^ (y % 4 in (0, 1))
            g = 236 if warp else 214
            g += int(8 * math.sin(x * 0.9) * math.sin(y * 0.35))
            if x % 8 == 0:
                g -= 10
            img.set(x, y, (g, g, g, 255))
    return img


GOLD = (255, 206, 72, 255)
GOLD_LIGHT = (255, 238, 150, 255)
GOLD_DARK = (176, 120, 30, 255)
RED = (176, 32, 28, 255)
RED_LIGHT = (226, 64, 52, 255)
RED_DARK = (110, 18, 16, 255)
LACQUER = (150, 30, 24, 255)
LACQUER_LIGHT = (196, 56, 40, 255)
LACQUER_DARK = (96, 16, 12, 255)


def fittings():
    img = Image(64, 64)
    # spear-tip finial (0,0,16,32): a leaf blade over a gilt collar and a ball
    for y in range(32):
        for x in range(16):
            u = (x + 0.5) / 16.0
            v = (y + 0.5) / 32.0
            col = None
            half = 0.0
            if v < 0.5:
                half = 0.42 * math.sin(math.pi * v / 0.5) ** 0.8 * (1.0 - v * 0.6)
                if abs(u - 0.5) <= half:
                    col = GOLD_LIGHT if u < 0.5 else GOLD
                    if abs(u - 0.5) < 0.04:
                        col = (255, 250, 210, 255)
            elif v < 0.58:
                if abs(u - 0.5) <= 0.3:
                    col = GOLD_DARK
            elif v < 0.74:
                if (u - 0.5) ** 2 + ((v - 0.66) * 2) ** 2 <= 0.3 ** 2:
                    col = GOLD if u < 0.55 else GOLD_DARK
            elif abs(u - 0.5) <= 0.14:
                col = GOLD_DARK
            if col:
                img.set(x, y, col)
    # red silk tassel (16,0,16,32): a knot then long threads
    for y in range(32):
        for x in range(16):
            u = (x + 0.5) / 16.0
            v = (y + 0.5) / 32.0
            col = None
            if v < 0.18:
                if (u - 0.5) ** 2 + ((v - 0.09) * 1.4) ** 2 <= 0.16 ** 2:
                    col = GOLD
            elif v < 0.26:
                if abs(u - 0.5) <= 0.2:
                    col = RED_DARK
            else:
                spread = 0.18 + 0.22 * (v - 0.26) / 0.74
                if abs(u - 0.5) <= spread:
                    col = RED_LIGHT if x % 2 == 0 else RED
                    if v > 0.92:
                        col = (RED[0], RED[1], RED[2], 200)
            if col:
                img.set(16 + x, y, col)
    # lacquered arm (32,0,32,8) with gilt end caps
    for y in range(8):
        for x in range(32):
            col = LACQUER_LIGHT if y in (1, 2) else (LACQUER_DARK if y in (6, 7) else LACQUER)
            if x < 3 or x > 28:
                col = GOLD if y < 5 else GOLD_DARK
            img.set(32 + x, y, col)
    # lacquered pole (32,8,8,32)
    for y in range(32):
        for x in range(8):
            col = LACQUER_LIGHT if x in (2, 3) else (LACQUER_DARK if x in (6, 7) else LACQUER)
            if y % 10 == 0:
                col = GOLD_DARK
            img.set(32 + x, 8 + y, col)
    # stone foot (40,8,16,16)
    for y in range(16):
        for x in range(16):
            g = 150 + ((x * 7 + y * 13) % 11) * 3
            if y in (0, 15) or x in (0, 15):
                g -= 30
            if y == 4 or y == 11:
                g -= 18
            img.set(40 + x, 8 + y, (g, g - 4, g - 10, 255))
    return img


def decal_ring():
    img = Image(64, 64)
    for y in range(64):
        for x in range(64):
            dx = (x + 0.5) / 32.0 - 1.0
            dy = (y + 0.5) / 32.0 - 1.0
            r = math.hypot(dx, dy)
            a = 0.0
            if 0.9 <= r <= 0.98 or 0.8 <= r <= 0.84:
                a = 1.0
            elif r < 0.98:
                a = 0.18 * max(0.0, 1.0 - abs(r - 0.88) / 0.1)
            ang = math.atan2(dy, dx)
            k = (ang / (2 * math.pi) * 8) % 1.0
            if 0.84 < r < 0.9 and (k < 0.06 or k > 0.94):
                a = 1.0
            if r > 1.0:
                a = 0.0
            if a > 0:
                g = 255
                img.set(x, y, (g, g, g, int(a * 255)))
    return img


# ======================================================================================== robe cuts

def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def grey(v, a=255):
    v = max(0, min(255, int(v)))
    return (v, v, v, a)


def copy(img):
    out = Image(img.width, img.height)
    out.px = [list(p) for p in img.px]
    return out


def clear_rect(img, rect):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            img.set(x, y, (0, 0, 0, 0))


def robe_pendek(base):
    img = copy(base)
    # legs: bound leggings from the knee down (rows 5..9): cross straps over a darker cloth
    for (u, v) in ((0, 16), (16, 48)):
        leg = box_faces(u, v, 4, 12, 4)
        for name in ("right", "front", "left", "back"):
            x0, y0, w, h = leg[name]
            for row in range(5, 10):
                for col in range(w):
                    shade = 150 if (col + row) % 3 == 0 else 176
                    img.set(x0 + col, y0 + row, grey(shade))
            for col in range(w):
                img.set(x0 + col, y0 + 5, grey(120))
                img.set(x0 + col, y0 + 9, grey(120))
    # the flaring trouser hems are not worn with a short robe
    for (u, v) in ((0, 32), (0, 48)):
        pants = box_faces(u, v, 4, 12, 4)
        for name in pants:
            clear_rect(img, pants[name])
    return img


def robe_tempur(base):
    img = copy(base)
    LEATHER = 104
    LEATHER_LIGHT = 132
    STITCH = 196
    # sleeves rolled to the elbow: bracers on the forearm (rows 4..8) of both arms
    for (u, v) in ((40, 16), (32, 48)):
        arm = box_faces(u, v, 4, 12, 4)
        for name in ("right", "front", "left", "back"):
            x0, y0, w, h = arm[name]
            for row in range(3, 4):
                for col in range(w):
                    img.set(x0 + col, y0 + row, grey(242))
            for row in range(4, 9):
                for col in range(w):
                    shade = LEATHER_LIGHT if row in (4, 8) else LEATHER
                    if col % 2 == 0 and row in (5, 7):
                        shade = STITCH
                    img.set(x0 + col, y0 + row, grey(shade))
    # no wide cuffs on a battle robe
    for (u, v) in ((40, 32), (48, 48)):
        sleeve = box_faces(u, v, 4, 12, 4)
        for name in sleeve:
            clear_rect(img, sleeve[name])
    # quilted shoulder guards on the body top and the upper rows of the sides
    body = box_faces(16, 16, 8, 12, 4)
    for name in ("top",):
        x0, y0, w, h = body[name]
        for row in range(h):
            for col in range(w):
                img.set(x0 + col, y0 + row, grey(LEATHER_LIGHT if (col + row) % 2 == 0 else LEATHER))
    for name in ("right", "left"):
        x0, y0, w, h = body[name]
        for row in range(0, 3):
            for col in range(w):
                img.set(x0 + col, y0 + row, grey(LEATHER if row < 2 else LEATHER_LIGHT))
    # a wide plated belt over the sash (rows 6..10) on the jacket layer
    jacket = box_faces(16, 32, 8, 12, 4)
    for name in ("right", "front", "left", "back"):
        x0, y0, w, h = jacket[name]
        for row in range(6, 11):
            for col in range(w):
                shade = LEATHER if row in (6, 10) else (LEATHER_LIGHT if col % 3 else 168)
                img.set(x0 + col, y0 + row, grey(shade))
    jx, jy, _, _ = jacket["front"]
    for row in range(7, 10):
        for col in range(3, 5):
            img.set(jx + col, jy + row, grey(228))
    # bound shins (rows 5..9) and no trouser hems
    for (u, v) in ((0, 16), (16, 48)):
        leg = box_faces(u, v, 4, 12, 4)
        for name in ("right", "front", "left", "back"):
            x0, y0, w, h = leg[name]
            for row in range(5, 10):
                for col in range(w):
                    img.set(x0 + col, y0 + row, grey(LEATHER_LIGHT if (row + col) % 2 else LEATHER))
    for (u, v) in ((0, 32), (0, 48)):
        pants = box_faces(u, v, 4, 12, 4)
        for name in pants:
            clear_rect(img, pants[name])
    # the slit skirt: the front panel's middle three columns open from the hem up
    skirt = box_faces(0, 0, 9, 9, 5)
    sx, sy, sw, sh = skirt["front"]
    for row in range(3, sh):
        for col in range(3, 6):
            img.set(sx + col, sy + row, (0, 0, 0, 0))
    for row in range(3, sh):
        img.set(sx + 2, sy + row, grey(240))
        img.set(sx + 6, sy + row, grey(240))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    base = read_png(os.path.join(ENTITY, "robe_base.png"))
    outputs = [
        (os.path.join(OUT, "kain_tenun.png"), weave()),
        (os.path.join(OUT, "bendera_hiasan.png"), fittings()),
        (os.path.join(OUT, "decal_cincin.png"), decal_ring()),
        (os.path.join(ENTITY, "robe_base_pendek.png"), robe_pendek(base)),
        (os.path.join(ENTITY, "robe_base_tempur.png"), robe_tempur(base)),
    ]
    for path, img in outputs:
        img.save(path)
        print("wrote", os.path.relpath(path))
    img = atlas()
    path = os.path.join(OUT, "lambang_atlas.png")
    img.save(path)
    print("wrote", os.path.relpath(path))


if __name__ == "__main__":
    main()
