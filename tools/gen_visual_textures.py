"""Generates the cultivator look textures (P4-visuals module).

textures/entity/robe_base.png    64x64 robe (Jubah) in the player-skin UV layout, greyscale so the renderer can tint it
                                 with the Dao (or realm) colour: long robe over body, arms and legs, a skirt hanging from
                                 the waist (drawn in the unused head area, see CultivatorModels), a dark sash, a raised
                                 crossed collar on the jacket layer, wide cuffs on the sleeve layer, a hem on the trousers
                                 layer. Hands, feet and the head stay bare.
textures/entity/robe_trim.png    the gold trims drawn untinted over the robe from Golden Core (collar, sash borders,
                                 cuffs, hems, the front panel edge of the skirt).
textures/entity/robe_emblem.png  the embroidered emblem on the back from Nascent Soul (a yin-yang medallion with rays and
                                 cloud curls on the skirt).
textures/entity/cultivator_eyes.png  64x64 emissive overlay: only the two standard eye pixel pairs of the head front face
                                 (and faintly on the hat layer), white so the renderer tints them.
textures/entity/dharma_glow.png  64x64 shell for the Dharma form: soft white flame streaks on the base layer UVs only.
textures/misc/dao_circle.png     128x128 rune array: concentric rings, a band of runes, the eight trigrams, an eight
                                 pointed star and a small yin-yang in the middle; white on transparent, tinted in game.
textures/misc/dao_circle_inner.png  128x128 inner ring: an opening lotus of eight petals, a dotted ring and small runes
                                 (counter-rotates over the main circle).

Run with: python tools/gen_visual_textures.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from png import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "kultivator", "textures")


def grey(v, a=255):
    return (v, v, v, a)


# ======================================================================================== robe

CLOTH = 222
CLOTH_FOLD = 196
CLOTH_LIGHT = 240
INNER = 252
SASH = 92
SASH_LIGHT = 120
HEM = 250
COLLAR = 255
EDGE = 168

GOLD = (255, 206, 72, 255)
GOLD_LIGHT = (255, 238, 150, 255)
GOLD_DARK = (196, 136, 34, 255)


def fill(img, x0, y0, w, h, color):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            img.set(x, y, color)


def cloth_column(x, y, base=CLOTH):
    """Vertical folds: every third column a shade darker, a lighter one next to it."""
    phase = x % 3
    if phase == 0:
        return grey(CLOTH_FOLD if base == CLOTH else base - 22)
    if phase == 1:
        return grey(min(255, base + 10))
    return grey(base)


def box_faces(u, v, w, h, d):
    """The six face rectangles of a model cube at texOffs(u, v): name -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def paint_cloth(img, rect, rows=None):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        row = y - y0
        if rows is not None and row not in rows:
            continue
        for x in range(x0, x0 + w):
            img.set(x, y, cloth_column(x, y))


def row_band(img, rect, row, color):
    x0, y0, w, h = rect
    if 0 <= row < h:
        for x in range(x0, x0 + w):
            img.set(x, y0 + row, color)


def robe_base():
    img = Image(64, 64)

    # ---- body (base layer, 8x12x4 at 16,16)
    body = box_faces(16, 16, 8, 12, 4)
    for name in ("top", "bottom", "right", "front", "left", "back"):
        paint_cloth(img, body[name])
    # Inner robe showing in the V of the collar (front face, wearer's left lapel crosses over).
    fx, fy, _, _ = body["front"]
    for row in range(0, 6):
        for col in range(0, 8):
            # V opening between the two lapel lines (see jacket collar below).
            left_line = 1 + row * 0.5
            right_line = 6 - row * 0.9
            if left_line < col < right_line:
                img.set(fx + col, fy + row, grey(INNER))
    # Sash around the waist on every side face (rows 7..9).
    for name in ("right", "front", "left", "back"):
        for row in (7, 8, 9):
            row_band(img, body[name], row, grey(SASH if row == 8 else SASH_LIGHT))
    # Hem of the upper robe below the sash.
    for name in ("right", "front", "left", "back"):
        row_band(img, body[name], 11, grey(CLOTH_LIGHT))

    # ---- jacket layer (16,32): the raised crossed collar on the front, the sash with its knot, nothing else.
    jacket = box_faces(16, 32, 8, 12, 4)
    jx, jy, _, _ = jacket["front"]
    for row in range(0, 8):
        # The lapel crossing over: from the wearer's left shoulder (viewer's right) down to the wearer's right hip.
        col = round(6 - row * 0.9)
        for c in (col, col + 1):
            if 0 <= c < 8:
                img.set(jx + c, jy + row, grey(COLLAR))
        if 0 <= col - 1 < 8:
            img.set(jx + col - 1, jy + row, grey(EDGE))
    for row in range(0, 4):
        # The lapel underneath, from the wearer's right shoulder to the middle.
        col = round(1 + row * 0.5)
        img.set(jx + col, jy + row, grey(COLLAR))
        img.set(jx + col - 1 if col > 0 else jx, jy + row, grey(EDGE))
    # Collar band round the back of the neck.
    bx, by, _, _ = jacket["back"]
    for col in range(1, 7):
        img.set(bx + col, by, grey(COLLAR))
    for name in ("right", "front", "left", "back"):
        x0, y0, w, h = jacket[name]
        for row in (7, 8, 9):
            for x in range(x0, x0 + w):
                img.set(x, y0 + row, grey(SASH if row == 8 else SASH_LIGHT))
    # Sash knot with two hanging ends on the front, a little off-centre.
    for row in range(7, 12):
        img.set(jx + 2, jy + row, grey(SASH_LIGHT if row < 10 else SASH))
    img.set(jx + 3, jy + 8, grey(SASH_LIGHT))
    img.set(jx + 1, jy + 8, grey(SASH_LIGHT))
    img.set(jx + 3, jy + 10, grey(SASH))
    img.set(jx + 3, jy + 11, grey(SASH_LIGHT))

    # ---- arms: sleeves down to just above the hands, hands and the arm bottom stay bare.
    for (u, v) in ((40, 16), (32, 48)):
        arm = box_faces(u, v, 4, 12, 4)
        paint_cloth(img, arm["top"])
        for name in ("right", "front", "left", "back"):
            paint_cloth(img, arm[name], rows=range(0, 9))
            row_band(img, arm[name], 8, grey(HEM))
    # Sleeve layer: the wide cuff (rows 5..9), flaring a little out from the arm.
    for (u, v) in ((40, 32), (48, 48)):
        sleeve = box_faces(u, v, 4, 12, 4)
        for name in ("right", "front", "left", "back"):
            paint_cloth(img, sleeve[name], rows=range(5, 10))
            row_band(img, sleeve[name], 9, grey(HEM))
            row_band(img, sleeve[name], 5, grey(CLOTH_LIGHT))

    # ---- legs: the robe down to the ankles, shoes stay bare.
    for (u, v) in ((0, 16), (16, 48)):
        leg = box_faces(u, v, 4, 12, 4)
        paint_cloth(img, leg["top"])
        for name in ("right", "front", "left", "back"):
            paint_cloth(img, leg[name], rows=range(0, 10))
            row_band(img, leg[name], 9, grey(HEM))
    # Trousers layer: the hem flaring at the ankles.
    for (u, v) in ((0, 32), (0, 48)):
        pants = box_faces(u, v, 4, 12, 4)
        for name in ("right", "front", "left", "back"):
            paint_cloth(img, pants[name], rows=range(7, 10))
            row_band(img, pants[name], 9, grey(HEM))

    # ---- skirt (9x9x5 at 0,0): long folds, hem, the front panel edge.
    skirt = box_faces(0, 0, 9, 9, 5)
    for name in ("right", "front", "left", "back"):
        x0, y0, w, h = skirt[name]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                phase = (x + (y - y0) // 4) % 3
                shade = CLOTH_FOLD if phase == 0 else (CLOTH + 12 if phase == 1 else CLOTH)
                img.set(x, y, grey(shade))
        row_band(img, skirt[name], h - 1, grey(HEM))
        row_band(img, skirt[name], h - 2, grey(CLOTH_LIGHT))
    sx, sy, sw, sh = skirt["front"]
    for row in range(0, sh - 1):
        img.set(sx + 2, sy + row, grey(COLLAR))
        img.set(sx + 1, sy + row, grey(EDGE))
    return img


def robe_trim():
    img = Image(64, 64)
    jacket = box_faces(16, 32, 8, 12, 4)
    jx, jy, _, _ = jacket["front"]
    # Gold edge along the crossing lapel and the one underneath.
    for row in range(0, 8):
        col = round(6 - row * 0.9)
        if 0 <= col + 1 < 8:
            img.set(jx + col + 1, jy + row, GOLD)
        if 0 <= col < 8:
            img.set(jx + col, jy + row, GOLD_LIGHT if row % 3 == 0 else GOLD)
    for row in range(0, 4):
        col = round(1 + row * 0.5)
        img.set(jx + col, jy + row, GOLD)
    bx, by, _, _ = jacket["back"]
    for col in range(1, 7):
        img.set(bx + col, by, GOLD)
    # Sash borders on every side and a jade-gold buckle.
    for name in ("right", "front", "left", "back"):
        x0, y0, w, h = jacket[name]
        for x in range(x0, x0 + w):
            img.set(x, y0 + 7, GOLD_DARK if (x % 2) else GOLD)
            img.set(x, y0 + 9, GOLD_DARK if (x % 2) else GOLD)
    img.set(jx + 2, jy + 8, GOLD_LIGHT)
    img.set(jx + 3, jy + 8, GOLD)
    img.set(jx + 1, jy + 8, GOLD)
    img.set(jx + 2, jy + 11, GOLD)
    # Cuffs.
    for (u, v) in ((40, 32), (48, 48)):
        sleeve = box_faces(u, v, 4, 12, 4)
        for name in ("right", "front", "left", "back"):
            row_band(img, sleeve[name], 9, GOLD)
            row_band(img, sleeve[name], 5, GOLD_DARK)
    # Trouser hems.
    for (u, v) in ((0, 32), (0, 48)):
        pants = box_faces(u, v, 4, 12, 4)
        for name in ("right", "front", "left", "back"):
            row_band(img, pants[name], 9, GOLD)
    # Skirt hem with a little meander, and the front panel edge.
    skirt = box_faces(0, 0, 9, 9, 5)
    for name in ("right", "front", "left", "back"):
        x0, y0, w, h = skirt[name]
        for x in range(x0, x0 + w):
            img.set(x, y0 + h - 1, GOLD)
            if (x - x0) % 3 == 1:
                img.set(x, y0 + h - 2, GOLD_DARK)
    sx, sy, sw, sh = skirt["front"]
    for row in range(0, sh - 1):
        img.set(sx + 2, sy + row, GOLD_LIGHT if row % 4 == 0 else GOLD)
    return img


def robe_emblem():
    img = Image(64, 64)
    body = box_faces(16, 16, 8, 12, 4)
    bx, by, _, _ = body["back"]
    # A round medallion (rows 0..6 of the back face, 8 columns) holding a yin-yang, with rays above.
    medallion = [
        "..GGGG..",
        ".GLLDDG.",
        "GLLLDDDG",
        "GLWLDBDG",
        "GLLLDDDG",
        ".GLLDDG.",
        "..GGGG..",
    ]
    # G gold ring, L light half, D dark half, W light dot in the dark half... (B dark dot in the light half side)
    colors = {
        "G": GOLD,
        "L": (255, 246, 214, 255),
        "D": (120, 40, 36, 255),
        "W": (120, 40, 36, 255),
        "B": (255, 246, 214, 255),
    }
    for row, line in enumerate(medallion):
        for col, ch in enumerate(line):
            if ch in colors:
                img.set(bx + col, by + row, colors[ch])
    # Little gold rays left and right of the medallion.
    img.set(bx, by, GOLD_DARK)
    img.set(bx + 7, by, GOLD_DARK)
    img.set(bx, by + 6, GOLD_DARK)
    img.set(bx + 7, by + 6, GOLD_DARK)
    # Cloud curls on the back of the skirt.
    skirt = box_faces(0, 0, 9, 9, 5)
    sx, sy, sw, sh = skirt["back"]
    clouds = [
        ".........",
        "..GG.....",
        ".G..G.GG.",
        ".G.GG.G.G",
        "..G..G..G",
        "......GG.",
        ".........",
    ]
    for row, line in enumerate(clouds):
        for col, ch in enumerate(line):
            if ch == "G":
                img.set(sx + col, sy + 1 + row, GOLD_LIGHT if row < 3 else GOLD)
    return img


# ======================================================================================== eyes and glow

def cultivator_eyes():
    img = Image(64, 64)
    face_x, face_y = 8, 8
    row = face_y + 4
    for start in (face_x + 1, face_x + 5):
        for x in (start, start + 1):
            img.set(x, row, (255, 255, 255, 255))
            img.blend(x, row - 1, (255, 255, 255, 90))
            img.blend(x, row + 1, (255, 255, 255, 60))
    # The same eyes faintly on the hat layer (u + 32), so a hat overlay on the skin does not hide them completely.
    for start in (face_x + 33, face_x + 37):
        for x in (start, start + 1):
            img.set(x, row, (255, 255, 255, 120))
    return img


def dharma_glow():
    img = Image(64, 64)
    regions = []
    regions.append((0, 0, 32, 16))      # head
    regions.append((16, 16, 24, 16))    # body
    regions.append((40, 16, 16, 16))    # right arm
    regions.append((32, 48, 16, 16))    # left arm
    regions.append((0, 16, 16, 16))     # right leg
    regions.append((16, 48, 16, 16))    # left leg
    for (x0, y0, w, h) in regions:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                # Flame-like streaks: each column flickers on its own phase, brighter towards the top of each face.
                streak = 0.5 + 0.5 * math.sin(x * 1.7 + math.sin(y * 0.6 + x) * 1.3)
                rise = 1.0 - ((y - y0) % 16) / 16.0 * 0.35
                alpha = int(70 + 150 * streak * rise)
                img.set(x, y, (255, 255, 255, max(40, min(230, alpha))))
    return img


# ======================================================================================== Dao circle

SIZE = 128
CENTER = (SIZE - 1) / 2.0
SUPER = 4

# The eight trigrams in the Earlier Heaven order (from the inside out: True = solid line, False = broken).
TRIGRAMS = [
    (True, True, True),     # Qian, heaven
    (True, True, False),    # Dui, lake
    (True, False, True),    # Li, fire
    (True, False, False),   # Zhen, thunder
    (False, True, True),    # Xun, wind
    (False, True, False),   # Kan, water
    (False, False, True),   # Gen, mountain
    (False, False, False),  # Kun, earth
]

# Little rune glyphs (5 wide, 7 tall), invented seal-script-like marks.
RUNES = [
    ["#####", "..#..", ".###.", "..#..", "#.#.#", "#...#", "#####"],
    ["#...#", "##.##", "..#..", "#####", "..#..", ".#.#.", "#...#"],
    [".###.", "#...#", "#.#.#", "#.#.#", "..#..", "#####", "..#.."],
    ["#.#.#", "#.#.#", "#####", "..#..", ".###.", ".#.#.", "##.##"],
    ["..#..", "#####", "#...#", ".###.", "#...#", "#####", "..#.."],
    ["###..", "..#..", "..###", "#...#", "###.#", "#...#", "#####"],
    ["#...#", ".#.#.", "..#..", "#####", "#.#.#", "#.#.#", "#...#"],
    ["#####", "#...#", "#.#.#", "#.#.#", "#...#", "..#..", "#####"],
]


def ring_cov(r, radius, half_width):
    return 1.0 if abs(r - radius) <= half_width else 0.0


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    cx, cy = ax + dx * t, ay + dy * t
    return math.hypot(px - cx, py - cy)


def dao_circle_alpha(x, y):
    """0..1 coverage of the main circle at a sub-pixel position (texture coordinates)."""
    dx, dy = x - CENTER, y - CENTER
    r = math.hypot(dx, dy)
    if r > 63.6:
        return 0.0
    angle = math.atan2(dy, dx)
    a = 0.0
    # Outer double ring.
    a = max(a, ring_cov(r, 62.0, 1.3))
    a = max(a, ring_cov(r, 57.5, 0.6) * 0.9)
    # Tick marks between the outer rings.
    ticks = 64
    tick_angle = (angle / (2 * math.pi) * ticks) % 1.0
    if 58.5 < r < 61.0 and (tick_angle < 0.12 or tick_angle > 0.88):
        a = max(a, 0.85)
    # Rune band 47..56.
    a = max(a, rune_band(r, angle))
    a = max(a, ring_cov(r, 46.0, 0.7))
    # Trigrams between r 33 and 44 at the eight directions.
    a = max(a, trigrams(dx, dy))
    # Inner ring and an eight pointed star (two overlapping squares) inside it.
    a = max(a, ring_cov(r, 31.0, 1.0))
    a = max(a, ring_cov(r, 28.0, 0.5) * 0.7)
    for k in range(8):
        p0 = (k * math.pi / 4.0)
        p1 = ((k + 3) * math.pi / 4.0)
        ax, ay = math.cos(p0) * 27.5, math.sin(p0) * 27.5
        bx, by = math.cos(p1) * 27.5, math.sin(p1) * 27.5
        if seg_dist(dx, dy, ax, ay, bx, by) < 0.7:
            a = max(a, 0.75)
    # Yin-yang in the middle.
    a = max(a, yin_yang(dx, dy, 9.5))
    return a


def rune_band(r, angle):
    if not (47.5 < r < 56.0):
        return 0.0
    count = 24
    slot = angle / (2 * math.pi) * count
    index = int(math.floor(slot)) % count
    frac = slot - math.floor(slot)
    # Local glyph coordinates: along the band (tangent) and across it (radius).
    tangent = (frac - 0.5) * (2 * math.pi * r / count)
    radial = 55.0 - r
    gx = int(math.floor(tangent / 1.15 + 2.5))
    gy = int(math.floor(radial / 1.15))
    if 0 <= gx < 5 and 0 <= gy < 7:
        glyph = RUNES[(index * 5 + 3) % len(RUNES)]
        if glyph[gy][gx] == "#":
            return 0.92
    return 0.0


def trigrams(dx, dy):
    best = 0.0
    for k, lines in enumerate(TRIGRAMS):
        theta = -math.pi / 2 + k * math.pi / 4.0
        ux, uy = math.cos(theta), math.sin(theta)        # outward
        tx, ty = -uy, ux                                  # along the bar
        radial = dx * ux + dy * uy
        along = dx * tx + dy * ty
        for i, solid in enumerate(lines):
            center = 35.5 + i * 3.6
            if abs(radial - center) > 1.05:
                continue
            if abs(along) > 6.0:
                continue
            if not solid and abs(along) < 1.4:
                continue
            best = 1.0
    return best


def yin_yang(dx, dy, radius):
    r = math.hypot(dx, dy)
    if r > radius + 1.0:
        return 0.0
    if abs(r - radius) <= 0.8:
        return 1.0
    if r > radius:
        return 0.0
    # Light half drawn (the dark half stays transparent), with the two dots.
    half = radius / 2.0
    top = math.hypot(dx, dy + half)
    bottom = math.hypot(dx, dy - half)
    light = dx > 0
    if top < half:
        light = True
    if bottom < half:
        light = False
    if top < radius / 6.0:
        light = False
    if bottom < radius / 6.0:
        light = True
    return 0.8 if light else 0.0


def inner_alpha(x, y):
    dx, dy = x - CENTER, y - CENTER
    r = math.hypot(dx, dy)
    if r > 63.6:
        return 0.0
    angle = math.atan2(dy, dx)
    a = 0.0
    a = max(a, ring_cov(r, 61.0, 1.0))
    # Dotted ring.
    dots = 36
    slot = (angle / (2 * math.pi) * dots) % 1.0
    if abs(r - 56.0) < 1.4 and abs(slot - 0.5) < 0.18:
        a = max(a, 0.9)
    # Eight lotus petals opening from r 24 to r 53, outlined, with a faint vein.
    for k in range(8):
        theta = -math.pi / 2 + k * math.pi / 4
        ux, uy = math.cos(theta), math.sin(theta)
        radial = dx * ux + dy * uy
        along = dx * -uy + dy * ux
        if 24.0 <= radial <= 53.0:
            s = (radial - 24.0) / 29.0
            width = 10.5 * math.pow(max(0.0, math.sin(math.pi * s)), 0.75)
            if abs(abs(along) - width) < 0.85:
                a = max(a, 0.95)
            elif abs(along) < 0.5 and s < 0.8:
                a = max(a, 0.5)
    # Inner circle the petals grow from, with a small rune between every other petal pair.
    a = max(a, ring_cov(r, 24.0, 0.8))
    for k in range(4):
        theta = -math.pi / 2 + math.pi / 8 + k * math.pi / 2
        cx, cy = math.cos(theta) * 44.0, math.sin(theta) * 44.0
        lx, ly = dx - cx, dy - cy
        gx = int(math.floor(lx / 1.2 + 2.5))
        gy = int(math.floor(ly / 1.2 + 3.5))
        if 0 <= gx < 5 and 0 <= gy < 7 and RUNES[(k * 3 + 1) % len(RUNES)][gy][gx] == "#":
            a = max(a, 0.85)
    return a


def render_circle(fn):
    img = Image(SIZE, SIZE)
    for py in range(SIZE):
        for px in range(SIZE):
            total = 0.0
            for sy in range(SUPER):
                for sx in range(SUPER):
                    total += fn(px + (sx + 0.5) / SUPER - 0.5, py + (sy + 0.5) / SUPER - 0.5)
            cov = total / (SUPER * SUPER)
            if cov > 0.0:
                img.set(px, py, (255, 255, 255, int(round(cov * 255))))
    return img


def main():
    entity = os.path.join(ROOT, "entity")
    misc = os.path.join(ROOT, "misc")
    os.makedirs(entity, exist_ok=True)
    os.makedirs(misc, exist_ok=True)
    outputs = [
        (os.path.join(entity, "robe_base.png"), robe_base()),
        (os.path.join(entity, "robe_trim.png"), robe_trim()),
        (os.path.join(entity, "robe_emblem.png"), robe_emblem()),
        (os.path.join(entity, "cultivator_eyes.png"), cultivator_eyes()),
        (os.path.join(entity, "dharma_glow.png"), dharma_glow()),
        (os.path.join(misc, "dao_circle.png"), render_circle(dao_circle_alpha)),
        (os.path.join(misc, "dao_circle_inner.png"), render_circle(inner_alpha)),
    ]
    for path, img in outputs:
        img.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
