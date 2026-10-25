"""Textures of the Gabung Sekte client effects (DESIGN 41.4.6, 41.5.4; module 41V-P): python tools/gen_gabung_fx.py

Writes src/main/resources/assets/kultivator/textures/sekte/gabung_fx/fx.png, 256 x 256, 4 x 4 cells of 64 px
(column, row) = the {@code Sel} constants of client/sekte/gabung/fx/GabungFxClient.java:

  (0,0) glow      soft round glow, white (tinted by vertex colour)
  (1,0) lentera   spirit lantern, side view: lacquered cap and foot with gilt rims, glowing paper ribs, red tassel
                  (full colour, drawn untinted)
  (2,0) kisi      formation lattice, tileable: hexagon grid of thin light lines, rune knots on the vertices (white)
  (3,0) awan      auspicious cloud (xiangyun): three curling lobes with a spiral tail, rim light (white-gold)
  (0,1) tebasan   sword slash crescent, hot edge fading to the inside (white)
  (1,1) kabut     soft mist puff, uneven (white)
  (2,1) percik    four-ray spark star with a small halo (white)
  (3,1) cincin    rune ring: annulus with 24 ticks and 8 glyph knots (white)
  (0,2) segel     Buddha vow seal: lotus of eight petals in a double ring (gold)
  (1,2) retak     the same seal broken: cracks, a missing chip, dimmed (gold-grey)
  (2,2) daun      falling leaf with veins (green; the calm of a subdued beast)
  (3,2) tetes     blood drop with a highlight (crimson)
  (0,3) asap      poison vapour curl (white, tinted green or violet)
  (1,3) mata      a watching eye glyph: almond, iris, slit pupil (white; Ujian Senyap alert)
  (2,3) api       furnace flame, teardrop with a hot core (orange-gold; Satu Tungku)
  (3,3) gunung    ink-wash mountain with mist at its foot (grey; Pikul Gunung)

Previews (not shipped): tools/keluaran/gabung_fx/fx_x2.png, opened with Read.
Deterministic: the same code always gives the same bytes. Canvas, Noise and colour helpers are imported from
tools/gen_tempat_tema_fx.py (read only).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from png import Image  # noqa: E402
from gen_tempat_tema_fx import Canvas, Noise, hexc, mix, smooth  # noqa: E402

OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "kultivator", "textures", "sekte", "gabung_fx")
PREVIEW = os.path.join(HERE, "keluaran", "gabung_fx")
CELL = 64


def cell(cv, col, row, painter):
    sub = Canvas(CELL, CELL)
    painter(sub)
    for y in range(CELL):
        for x in range(CELL):
            p = sub.get(x, y)
            cv.put(col * CELL + x, row * CELL + y, p)


# ====================================================================== cells

def glow(c):
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 31.0
            a = max(0.0, 1.0 - d) ** 2.2
            c.put(x, y, (255, 255, 255, 255 * a))


def lentera(c):
    n = Noise(4101, 4, 4)
    cx = 32.0
    # hanging cord
    c.line(cx, 0, cx, 9, 1.2, hexc("3a2418"))
    # glow behind the paper
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot((x + 0.5 - cx) / 26.0, (y + 0.5 - 31) / 24.0)
            a = max(0.0, 1.0 - d) ** 2
            c.over(x, y, (255, 190, 110, 150 * a))
    # paper body: an ellipse, lit from the inside (hot centre, deeper amber at the rim)
    for y in range(12, 50):
        for x in range(8, 57):
            u = (x + 0.5 - cx) / 21.0
            v = (y + 0.5 - 31) / 18.5
            k = u * u + v * v
            if k > 1.06:
                continue
            cov = max(0.0, min(1.0, (1.0 - k) * 18.0 + 0.5))
            heat = 1.0 - min(1.0, k)
            base = mix(hexc("c8501e"), hexc("ffd890"), heat ** 0.8)
            # paper fibre
            f = 0.92 + 0.1 * n.fbm(x / 64.0, y / 64.0, 3)
            col = (base[0] * f, base[1] * f, base[2] * f, 255)
            c.over(x, y, col, cov)
    # bamboo ribs (vertical arcs following the bulge)
    for i in range(-3, 4):
        pts = []
        for j in range(21):
            t = j / 20.0
            yy = 13.5 + t * 35.0
            v = (yy - 31) / 18.5
            half = 21.0 * math.sqrt(max(0.0, 1.0 - v * v))
            pts.append((cx + half * i / 3.4, yy))
        c.polyline(pts, 0.9, (140, 52, 20, 150))
    # horizontal rings
    for yy in (19.0, 31.0, 43.0):
        v = (yy - 31) / 18.5
        half = 21.0 * math.sqrt(max(0.0, 1.0 - v * v))
        c.line(cx - half + 1, yy, cx + half - 1, yy, 0.8, (150, 60, 24, 120))
    # a painted character mark in the middle (a jade-ink 道 suggestion: three strokes)
    c.line(cx - 4, 27, cx + 4, 27, 1.4, (120, 30, 18, 200))
    c.line(cx, 24, cx, 36, 1.4, (120, 30, 18, 200))
    c.line(cx - 5, 33, cx + 5, 36, 1.4, (120, 30, 18, 200))
    # cap and foot: black lacquer with gilt rims
    for (y0, y1) in ((9.0, 14.0), (48.0, 53.0)):
        c.rect(cx - 10, y0, cx + 10, y1, hexc("1c1410"))
        c.line(cx - 10, y0 + 0.5, cx + 10, y0 + 0.5, 1.0, hexc("e8c060"))
        c.line(cx - 10, y1 - 0.5, cx + 10, y1 - 0.5, 1.0, hexc("e8c060"))
        c.line(cx - 6, (y0 + y1) / 2, cx + 6, (y0 + y1) / 2, 0.8, hexc("a88030"))
    # red silk tassel with a jade bead
    c.disc(cx, 55.5, 1.8, hexc("5fe3a0"))
    for i in range(-2, 3):
        c.line(cx + i * 0.8, 56.5, cx + i * 1.3, 63.5, 1.0, hexc("c42020") if i % 2 == 0 else hexc("9a1414"))


def kisi(c):
    """Pointy-top hexagons tiling the 64 px cell: 4 columns of 16 px, rows every 16 px (R = 10.67, odd rows shifted)."""
    w, R = 16.0, 16.0 / 1.5
    centres = []
    for row in range(-1, 6):
        for col in range(-1, 6):
            centres.append((col * w + (w / 2 if row % 2 else 0.0), row * 16.0))
    edges = set()
    for (cx, cy) in centres:
        pts = [(cx, cy - R), (cx + w / 2, cy - R / 2), (cx + w / 2, cy + R / 2), (cx, cy + R), (cx - w / 2, cy + R / 2),
               (cx - w / 2, cy - R / 2)]
        for k in range(6):
            x0, y0 = pts[k]
            x1, y1 = pts[(k + 1) % 6]
            key = tuple(sorted(((round(x0 % 64, 2), round(y0 % 64, 2)), (round(x1 % 64, 2), round(y1 % 64, 2)))))
            if key in edges:
                continue
            edges.add(key)
            for ox in (-64, 0, 64):
                for oy in (-64, 0, 64):
                    c.line(x0 + ox, y0 + oy, x1 + ox, y1 + oy, 0.9, (255, 255, 255, 150))
    # rune knots on every other vertex (the lattice "nodes" where the formation's lines cross)
    for (cx, cy) in centres:
        for (x, y) in ((cx, cy - R), (cx + w / 2, cy + R / 2), (cx - w / 2, cy + R / 2)):
            for ox in (-64, 0, 64):
                for oy in (-64, 0, 64):
                    c.disc(x + ox, y + oy, 1.4, (255, 255, 255, 230))


def awan(c):
    gold = hexc("fff2c8")
    rim = hexc("ffffff")
    shade_c = hexc("e8b860")
    lobes = [(22, 36, 13), (35, 28, 15), (47, 37, 11), (33, 41, 12)]
    for (x, y, r) in lobes:
        c.disc(x, y, r + 1.5, (shade_c[0], shade_c[1], shade_c[2], 200))
    for (x, y, r) in lobes:
        c.disc(x, y - 1, r, gold)
    # rim light curls: the classic spiral of a xiangyun lobe
    for (x, y, r) in lobes[:3]:
        pts = []
        for j in range(40):
            t = j / 39.0
            a = math.pi * 0.8 + t * math.pi * 2.2
            rr = r * (0.85 - 0.6 * t)
            pts.append((x + rr * math.cos(a), y - 1 + rr * math.sin(a)))
        c.polyline(pts, 1.6, (200, 140, 50, 220), 0.8)
    # the tail flicking out to the right
    tail = []
    for j in range(30):
        t = j / 29.0
        tail.append((52 + t * 10, 40 + math.sin(t * math.pi * 1.3) * 5 - t * 6))
    c.polyline(tail, 3.0, gold, 0.6)
    for (x, y, r) in lobes:
        c.ring(x, y - 1, r - 1.2, r, (rim[0], rim[1], rim[2], 160))


def tebasan(c):
    cx, cy = 10.0, 58.0
    for y in range(CELL):
        for x in range(CELL):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            ang = math.atan2(-dy, dx)
            if ang < -0.15 or ang > math.pi / 2 + 0.15:
                continue
            t = (ang + 0.15) / (math.pi / 2 + 0.3)
            # the blade's edge is the outer radius; light fades inward and at both tips
            outer = 50.0
            width = 3.0 + 13.0 * math.sin(t * math.pi)
            inner = outer - width
            if d > outer + 1 or d < inner - 1:
                continue
            k = (d - inner) / max(0.01, outer - inner)
            edge = smooth(0.0, 1.0, k) ** 2.4
            tips = math.sin(t * math.pi) ** 0.6
            cov = min(1.0, (outer + 0.5 - d)) * min(1.0, (d - inner + 0.5))
            a = 255 * edge * tips * max(0.0, cov)
            c.over(x, y, (255, 255, 255, a))


def kabut(c):
    n = Noise(4105, 4, 4)
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - 32, (y + 0.5 - 34) * 1.25) / 30.0
            base = max(0.0, 1.0 - d) ** 1.6
            f = n.fbm(x / 64.0, y / 64.0, 4)
            a = base * (0.35 + 0.9 * f)
            c.put(x, y, (255, 255, 255, min(255.0, 210 * a)))


def percik(c):
    glow_r = 30.0
    for y in range(CELL):
        for x in range(CELL):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            d = math.hypot(dx, dy)
            halo = max(0.0, 1.0 - d / glow_r) ** 3 * 0.55
            rays = 0.0
            for (ax, ay, ln) in ((1, 0, 30), (0, 1, 30), (0.7071, 0.7071, 15), (0.7071, -0.7071, 15)):
                along = abs(dx * ax + dy * ay)
                across = abs(-dx * ay + dy * ax)
                if along < ln:
                    rays = max(rays, max(0.0, 1.0 - across / (2.2 * (1.0 - along / ln) + 0.4)) * (1.0 - along / ln) ** 1.3)
            core = max(0.0, 1.0 - d / 5.0)
            a = min(1.0, halo + rays + core)
            c.put(x, y, (255, 255, 255, 255 * a))


def cincin(c):
    cx = cy = 32.0
    c.ring(cx, cy, 25.0, 27.5, (255, 255, 255, 230))
    c.ring(cx, cy, 20.0, 21.0, (255, 255, 255, 170))
    for k in range(24):
        a = k * math.tau / 24
        r0, r1 = (21.5, 25.0) if k % 3 else (19.0, 25.0)
        c.line(cx + r0 * math.cos(a), cy + r0 * math.sin(a), cx + r1 * math.cos(a), cy + r1 * math.sin(a), 0.9, (255, 255, 255, 200))
    for k in range(8):
        a = k * math.tau / 8 + math.tau / 16
        x, y = cx + 29.5 * math.cos(a), cy + 29.5 * math.sin(a)
        c.disc(x, y, 1.8, (255, 255, 255, 240))
    # inner soft glow
    for y in range(CELL):
        for x in range(CELL):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 21 < d < 25:
                c.over(x, y, (255, 255, 255, 50))


def segel_lotus(c, broken):
    gold = hexc("ffd56a") if not broken else hexc("b8a070")
    deep = hexc("c08a20") if not broken else hexc("6e6250")
    cx = cy = 32.0
    c.ring(cx, cy, 27.0, 29.5, gold)
    c.ring(cx, cy, 23.5, 24.5, deep)
    for k in range(8):
        a = k * math.tau / 8 - math.pi / 2
        tip = (cx + 20.5 * math.cos(a), cy + 20.5 * math.sin(a))
        l = (cx + 9.0 * math.cos(a - 0.38), cy + 9.0 * math.sin(a - 0.38))
        r = (cx + 9.0 * math.cos(a + 0.38), cy + 9.0 * math.sin(a + 0.38))
        mid = (cx + 6.0 * math.cos(a), cy + 6.0 * math.sin(a))
        c.poly([mid, l, tip, r], (gold[0], gold[1], gold[2], 210))
        c.line(mid[0], mid[1], tip[0] * 0.85 + mid[0] * 0.15, tip[1] * 0.85 + mid[1] * 0.15, 0.8, deep)
    c.disc(cx, cy, 5.0, deep)
    c.disc(cx, cy, 3.2, gold)
    if broken:
        # three cracks from a strike point and a missing chip at the rim
        sx, sy = 40.0, 22.0
        for pts in ([(sx, sy), (34, 30), (36, 37), (30, 46)], [(sx, sy), (47, 30), (55, 31)], [(sx, sy), (44, 15), (49, 8)]):
            c.polyline(pts, 1.6, (40, 30, 24, 255))
            c.polyline(pts, 0.6, (255, 240, 200, 140))
        for y in range(CELL):
            for x in range(CELL):
                if math.hypot(x + 0.5 - 52, y + 0.5 - 13) < 6.5:
                    p = c.get(x, y)
                    c.put(x, y, (p[0], p[1], p[2], 0))


def segel(c):
    segel_lotus(c, False)


def retak(c):
    segel_lotus(c, True)


def daun(c):
    body = []
    for j in range(41):
        t = j / 40.0
        a = t * math.pi
        body.append((10 + t * 44, 34 - math.sin(a) * 13 * (1 - 0.25 * t)))
    for j in range(40, -1, -1):
        t = j / 40.0
        a = t * math.pi
        body.append((10 + t * 44, 34 + math.sin(a) * 11 * (1 - 0.25 * t)))
    c.poly(body, hexc("5fbf58"))
    c.poly([(p[0], p[1] - 1.5) for p in body[:41]] + [(p[0], 34) for p in reversed(body[:41])], (140, 220, 120, 120))
    c.line(6, 37, 54, 33, 1.3, hexc("2f7a30"))
    for k in range(1, 7):
        x = 10 + k * 6.5
        c.line(x, 34.5 - k * 0.06, x + 6, 26 + k * 0.4, 0.8, (47, 122, 48, 200))
        c.line(x, 34.5, x + 5.5, 41 - k * 0.3, 0.8, (47, 122, 48, 200))


def tetes(c):
    pts = []
    for j in range(60):
        t = j / 59.0 * math.tau
        # teardrop: r grows towards the bottom; tip at the top
        x = 32 + 13 * math.sin(t) * (1 - math.cos(t)) * 0.55
        y = 14 + 36 * (1 - math.cos(t)) / 2
        pts.append((x, y))
    c.poly(pts, hexc("9a0c18"))
    c.ellipse(32, 38, 9, 8, (190, 20, 34, 220))
    c.ellipse(28, 35, 2.4, 4.2, (255, 200, 200, 210), rot=-0.4)


def asap(c):
    n = Noise(4109, 4, 4)
    pts = []
    for j in range(80):
        t = j / 79.0
        a = t * math.pi * 3.0
        r = 4 + t * 20
        pts.append((32 + r * math.cos(a) * 0.9, 46 - t * 30 + r * math.sin(a) * 0.35))
    for i, (x, y) in enumerate(pts):
        rr = 3.0 + i / 79.0 * 6.0
        a = 150 * (1.0 - i / 100.0)
        c.disc(x, y, rr, (255, 255, 255, a * 0.35), soft=rr)
    for y in range(CELL):
        for x in range(CELL):
            p = c.get(x, y)
            if p[3] > 0:
                f = 0.6 + 0.6 * n.fbm(x / 64.0, y / 64.0, 3)
                c.put(x, y, (255, 255, 255, min(255.0, p[3] * f)))


def mata(c):
    cx, cy = 32.0, 32.0
    top = [(cx + 26 * math.cos(t), cy - 13 * math.sin(t)) for t in [k * math.pi / 30 for k in range(31)]]
    bottom = [(cx + 26 * math.cos(t), cy + 11 * math.sin(t)) for t in [math.pi - k * math.pi / 30 for k in range(31)]]
    c.poly(top + bottom, (255, 255, 255, 70))
    c.polyline(top, 2.0, (255, 255, 255, 240))
    c.polyline(bottom, 1.6, (255, 255, 255, 220))
    c.ring(cx, cy, 8.0, 10.5, (255, 255, 255, 235))
    c.ellipse(cx, cy, 2.0, 7.5, (255, 255, 255, 255))
    for k in range(5):
        a = math.pi + 0.35 + k * 0.6
        c.line(cx + 28 * math.cos(a), cy + 15 * math.sin(a), cx + 33 * math.cos(a), cy + 21 * math.sin(a), 1.2, (255, 255, 255, 180))


def api(c):
    pts = []
    for j in range(60):
        t = j / 59.0 * math.tau
        x = 32 + 15 * math.sin(t) * (1 - math.cos(t)) * 0.5
        y = 6 + 52 * (1 - math.cos(t)) / 2
        pts.append((x, y))
    c.poly(pts, (240, 110, 30, 230))
    inner = [(32 + (x - 32) * 0.62, 58 - (58 - y) * 0.7) for (x, y) in pts]
    c.poly(inner, (255, 190, 70, 240))
    core = [(32 + (x - 32) * 0.3, 58 - (58 - y) * 0.38) for (x, y) in pts]
    c.poly(core, (255, 250, 220, 255))


def gunung(c):
    n = Noise(4113, 4, 4)
    ridge = []
    for x in range(65):
        t = x / 64.0
        h = 46 - 34 * math.exp(-((t - 0.45) / 0.2) ** 2) - 14 * math.exp(-((t - 0.78) / 0.12) ** 2)
        h += (n.fbm(t, 0.3, 3) - 0.5) * 5
        ridge.append((x, h))
    for x, h in ridge:
        for y in range(int(h), 64):
            depth = (y - h) / 30.0
            ink = mix(hexc("2a2e34"), hexc("8a929a"), min(1.0, depth * 1.4))
            a = 255 * (1.0 - smooth(0.55, 1.0, (y - 34) / 30.0))
            c.over(x, y, (ink[0], ink[1], ink[2], a), 1.0)
    c.polyline([(x, h) for x, h in ridge], 1.4, hexc("15181c"))
    for y in range(44, 64):
        a = smooth(44, 60, y) * 200
        for x in range(64):
            c.over(x, y, (235, 238, 240, a * (0.6 + 0.4 * n.fbm(x / 64.0, y / 64.0, 2))))


CELLS = [
    (0, 0, glow), (1, 0, lentera), (2, 0, kisi), (3, 0, awan),
    (0, 1, tebasan), (1, 1, kabut), (2, 1, percik), (3, 1, cincin),
    (0, 2, segel), (1, 2, retak), (2, 2, daun), (3, 2, tetes),
    (0, 3, asap), (1, 3, mata), (2, 3, api), (3, 3, gunung),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    cv = Canvas(256, 256)
    for col, row, painter in CELLS:
        cell(cv, col, row, painter)
    cv.save(os.path.join(OUT, "fx.png"))
    # preview: 2x on a dark checker so white sprites show
    big = Image(512, 512)
    for y in range(512):
        for x in range(512):
            p = cv.get(x // 2, y // 2)
            bg = 40 if ((x // 32) + (y // 32)) % 2 else 58
            a = p[3] / 255.0
            big.set(x, y, (bg + (p[0] - bg) * a, bg + (p[1] - bg) * a, bg + (p[2] - bg) * a, 255))
    big.save(os.path.join(PREVIEW, "fx_x2.png"))
    print("wrote", os.path.join(OUT, "fx.png"))


if __name__ == "__main__":
    main()
