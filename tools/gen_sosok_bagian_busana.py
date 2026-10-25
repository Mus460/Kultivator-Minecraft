"""Garment part sheets of the Sosok composer (DESIGN 40.3.7, module 40K). Called by gen_sosok_bagian.py.

* ``busana/dalam_<l|p>_<w>``          underwear (fixed undyed linen, never naked under see-through cloth)
* ``busana/<GAYA>_<w>``               base layer (inner shirt, trousers) + outer layer (robe) of one of the 29 styles
                                     (22 of DESIGN 40 + 7 of DESIGN 42.12.4: wedding, mourning, winter, troupe, dancer, pickpocket)
* ``busana/<GAYA>_tepi1_<w>`` / ``_tepi2_<w>``  rank trim levels 1 and 2 (40.3.8), derived from the style's TEPI pixels
* ``busana/buzi_<kind>``              rank badge of the official robes (4 x 4 on the chest)
* ``busana/tambahan_<id>_<w>``        extras (travel strap, work ropes, duty lamellar, sleep clothes)
* ``busana/kepala_<id>``              head cloths drawn after the hair (desert veil, hood, mourning band, pilgrim band)
* ``busana/kaki_<id>``                footwear
* ``busana/kubus/<cube>[_<material>]`` cube sheets of 40.3.7
* ``luka/perban_lengan_<w>``, ``luka/perban_kaki``

Collars of the living close right over left (youren: the wearer's LEFT panel on top, its edge running from the wearer's
left collarbone down to the right waist); KAFAN is the only zuoren (reversed) style.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sosok_lib import (KULIT, KAIN1, KAIN2, TEPI, LOGAM, GIOK, SULAM, SAMAK, F_SEAM, F_NOISE, F_SHINE,  # noqa: E402
                       Lembar, Badan, FacePainter, UV_BUSANA, cube_face, cube_sheet_size, face_rect, tangga)

WRITTEN = []
WIDTHS = (("lebar", False), ("ramping", True))

GAYA = ["HANFU_L", "HANFU_P", "PENDEK", "TEMPUR", "TAO", "RAHIB", "KASAYA", "IBLIS", "SAUDAGAR", "PETANI", "BANGSAWAN",
        "ABADI", "PENJAGA", "PEJABAT", "SARJANA", "COMPANG", "ANAK", "PELAYAN", "LAUT", "GURUN", "KAFAN", "HAKIM_BAWAH"]
# the seven garment styles of 42: the groom's and the bride's red robes, coarse white mourning hemp, the padded winter
# coat, the troupe's tight red-yellow costume, the dancer's high-waisted gown, the pickpocket's dark patched jacket
KOTA42_GAYA = ["PENGANTIN_L", "PENGANTIN_P", "BERKABUNG", "MANTEL_KAPAS", "AKROBAT", "PENARI", "PENCOPET"]
GAYA = GAYA + KOTA42_GAYA

LINEN = 0xE4DCCB
LINEN_D = 0xC9C0AE
GOLD = 0xD8B04A


def save(sheet, rel):
    sheet.save(rel)
    WRITTEN.append(rel)


# ====================================================================== helpers


def cloth(face, slot, tone=128, y0=0, y1=None, x0=0, x1=None, flags=F_NOISE):
    face.fill(slot, tone, 255, flags, y0, y1, x0, x1)


def folds(face, xs, y0, y1, d=-9):
    for x in xs:
        for y in range(y0, y1):
            face.shade(x, y, d)


def hline(face, y, slot, tone=128, x0=0, x1=None, flags=0):
    x1 = face.w if x1 is None else x1
    for x in range(x0, x1):
        face.put(x, y, slot, tone, 255, flags)


def wrap_cloth(b, part, layer, slot, tone, y0, y1, side_d=-12, back_d=-6, flags=F_NOISE):
    """Cloth all round a part between rows y0..y1, sides and back a little darker."""
    w = b.wrap(part, layer)
    for s in range(w.len):
        for y in range(y0, y1):
            t = tone
            seg = 0
            for i, st in enumerate(w.starts):
                if s >= st:
                    seg = i
            if seg in (0, 2):
                t += side_d
            elif seg == 3:
                t += back_d
            w.put(s, y, slot, t, 255, flags)
    return w


def top_face(b, part, layer, slot, tone=134):
    b.face(part, layer, "atas").fill(slot, tone, 255, F_NOISE)


def bottom_face(b, part, layer, slot, tone=96):
    b.face(part, layer, "bawah").fill(slot, tone, 255, 0)


def arms(b):
    return ("lengan_kanan", "lengan_kiri")


def legs(b):
    return ("kaki_kanan", "kaki_kiri")


# ---------------------------------------------------------------------- collars


def collar_cross(front, edge=TEPI, inner=KAIN2, reverse=False, depth=6, double=False, layers=1):
    """Cross collar on the jacket front (8 x 12). youren: the edge runs from x 5-6 (wearer's left, viewer's right) at the
    top down to x 0-1 (wearer's right) at row ``depth``; the V shows the inner shirt and the throat."""
    def X(x):
        return 7 - x if reverse else x
    # V neck opening: transparent jacket so the base layer (inner collar + skin) shows
    for y, xs in ((0, (2, 3, 4)), (1, (3,))):
        for x in xs:
            front.clear(X(x), y)
    # the overlapping panel edge (diagonal), 1 px trim plus a shadow under it
    path = [(5, 0), (5, 1), (4, 2), (3, 3), (2, 4), (1, 5), (0, 6)][:depth + 1]
    for i, (x, y) in enumerate(path):
        front.put(X(x), y, edge, 132 - i * 2)
        if layers >= 2:
            front.put(X(x) + (1 if not reverse else -1), y, inner, 140)
        if layers >= 3 and X(x) + 2 < 8:
            front.put(X(x) + (2 if not reverse else -2), y, edge, 112)
        if double:
            front.put(X(x) - (1 if not reverse else -1), y, edge, 110)
        under = X(x) - 1 if not reverse else X(x) + 1
        if 0 <= under < 8:
            front.shade(under, y, -14)
    # the under panel's short edge (wearer's right neck) visible inside the V
    front.put(X(2), 0, edge, 116)
    front.put(X(1), 0, edge, 108)


def inner_collar(base_front, slot=KAIN2, reverse=False):
    """The inner shirt's collar on the base layer (seen through the V)."""
    for y in range(0, 3):
        for x in range(1, 7):
            base_front.put(x, y, slot, 140 - 4 * y, 255, F_NOISE)
    base_front.put(3, 0, KULIT, 112)
    base_front.put(4, 0, KULIT, 116)
    base_front.put(3 if not reverse else 4, 1, KULIT, 122)
    x = 2 if not reverse else 5
    base_front.put(x, 1, slot, 108)
    base_front.put(x + (1 if not reverse else -1), 2, slot, 112)


def collar_round(front, edge=TEPI):
    for x in (2, 3, 4, 5):
        front.clear(x, 0)
    for x, y in ((1, 0), (2, 1), (3, 1), (4, 1), (5, 1), (6, 0)):
        front.put(x, y, edge, 128)
    front.put(3, 1, edge, 120)
    front.put(4, 1, edge, 120)


def collar_square(front, slot=KAIN2):
    """Tao robe: square white collar band (2 px), straight down to the belt."""
    for y in range(0, 6):
        front.put(2, y, slot, 146, 255, F_NOISE)
        front.put(5, y, slot, 146, 255, F_NOISE)
    for x in (3, 4):
        front.clear(x, 0)
        front.put(x, 1, slot, 140)
    for y in range(2, 6):
        front.put(3, y, KAIN1, 104)
        front.put(4, y, KAIN1, 110)


def collar_straight(front, edge=TEPI):
    """Scholar's straight collar: two parallel dark edges meeting at the belt."""
    for y in range(0, 7):
        front.put(3, y, edge, 112 + (y % 2) * 6)
        front.put(4, y, edge, 104)
    front.clear(3, 0)
    front.clear(4, 0)
    front.put(2, 0, edge, 120)
    front.put(5, 0, edge, 120)


# ---------------------------------------------------------------------- sleeves and legs


def sleeve(b, arm, slot=KAIN1, tone=128, rows=10, cuff=TEPI, cuff_rows=1, inner=None):
    """Robe sleeve on the outer arm layer, cuff band at the bottom; inner sleeve (base layer) shows at the wrist."""
    w = wrap_cloth(b, arm, 1, slot, tone, 0, rows)
    if cuff is not None:
        for y in range(rows - cuff_rows, rows):
            w.row(y, cuff, 126 - 6 * (y - rows + cuff_rows))
    top_face(b, arm, 1, slot, tone + 6)
    fr = b.face(arm, 1, "depan")
    folds(fr, (1,), 2, rows - 1, -7)
    bk = b.face(arm, 1, "belakang")
    for y in range(3, rows - 1, 3):
        bk.shade(1, y, -10)
    if inner is not None:
        wrap_cloth(b, arm, 0, inner, 134, rows - 1, min(12, rows + 1))


def narrow_sleeve(b, arm, slot=KAIN1, tone=124, wrap=SAMAK):
    """Fighting sleeve: tight, bound with leather straps on the forearm."""
    wrap_cloth(b, arm, 1, slot, tone, 0, 10)
    top_face(b, arm, 1, slot)
    w = b.wrap(arm, 1)
    for y in (6, 8):
        w.row(y, wrap, 112)
    for s in range(0, w.len, 2):
        w.put(s, 7, wrap, 100)
    w.row(9, wrap, 104)


def short_sleeve(b, arm, slot=KAIN1, tone=126, rows=5, edge=None):
    wrap_cloth(b, arm, 1, slot, tone, 0, rows)
    top_face(b, arm, 1, slot)
    if edge is not None:
        b.wrap(arm, 1).row(rows - 1, edge, 118)


def trousers(b, slot=KAIN1, tone=100, rows=10, layer=0):
    for leg in legs(b):
        wrap_cloth(b, leg, layer, slot, tone, 0, rows)
        top_face(b, leg, layer, slot, tone + 8)
        fr = b.face(leg, layer, "depan")
        folds(fr, (1,), 2, rows, -6)
        fr.shade(2, 5, 8)


def robe_skirt_on_legs(b, slot=KAIN1, tone=118, rows=11, hem=TEPI, split=False):
    """The robe's lower half on the outer leg layer (under the skirt cubes it keeps legs dressed while striding)."""
    for leg in legs(b):
        w = wrap_cloth(b, leg, 1, slot, tone, 0, rows)
        if hem is not None:
            w.row(rows - 1, hem, 122)
        fr = b.face(leg, 1, "depan")
        folds(fr, (2,), 0, rows - 1, -8)
        if split:
            inner_x = 3 if leg == "kaki_kanan" else 0
            for y in range(2, rows):
                fr.put(inner_x, y, hem if hem is not None else slot, 116)


def calf_wraps(b, rows=(7, 11)):
    for leg in legs(b):
        w = b.wrap(leg, 1)
        for y in range(rows[0], rows[1]):
            for s in range(w.len):
                if (s + y) % 4 in (0, 1):
                    w.put(s, y, SAMAK, 120 if (s + y) % 4 == 0 else 100)
                else:
                    w.put(s, y, KAIN2, 116, 255, F_NOISE)


def belt(front_wrap, slot=KAIN2, rows=(6, 8), buckle=None, front_range=None):
    for y in range(*rows):
        front_wrap.row(y, slot, 126 - (8 if y == rows[1] - 1 else 0), F_NOISE)
    if buckle is not None and front_range is not None:
        s0, s1 = front_range
        mid = (s0 + s1) // 2
        for s in (mid - 1, mid):
            for y in range(*rows):
                front_wrap.put(s, y, buckle, 150, 255, F_SHINE)


def hem(front_wrap, y, slot=TEPI):
    front_wrap.row(y, slot, 124)


def body_jacket(b, slot=KAIN1, tone=128, rows=12):
    w = wrap_cloth(b, "badan", 1, slot, tone, 0, rows)
    top_face(b, "badan", 1, slot, tone + 8)
    bottom_face(b, "badan", 1, slot, tone - 30)
    fr = b.face("badan", 1, "depan")
    folds(fr, (2, 5), 8, rows, -8)
    bk = b.face("badan", 1, "belakang")
    folds(bk, (3,), 1, 6, -6)
    folds(bk, (1, 6), 8, rows, -7)
    return w


def inner_shirt(b, slot=KAIN2, sleeves_rows=10):
    wrap_cloth(b, "badan", 0, slot, 136, 0, 12, -10, -4)
    top_face(b, "badan", 0, slot, 140)
    for arm in arms(b):
        wrap_cloth(b, arm, 0, slot, 134, 0, sleeves_rows, -10, -4)
        top_face(b, arm, 0, slot, 140)


# ---------------------------------------------------------------------- fixed motifs


def yin_yang(face, x0, y0):
    """6x6 taiji in fixed black and white (right side light, the dots opposite)."""
    pat = ["..WW..",
           ".WWWB.",
           "WWbWBB",
           "WWBwBB",
           ".WBBB.",
           "..BB.."]
    for y, row in enumerate(pat):
        for x, c in enumerate(row):
            if c == "W":
                face.fixed(x0 + x, y0 + y, 0xEDEAE0)
            elif c == "B":
                face.fixed(x0 + x, y0 + y, 0x15141A)
            elif c == "b":
                face.fixed(x0 + x, y0 + y, 0x15141A)
            elif c == "w":
                face.fixed(x0 + x, y0 + y, 0xEDEAE0)


def gold_embroidery(face, y0, y1, step=2, rgb=GOLD):
    for y in range(y0, y1):
        for x in range(face.w):
            if (x + y) % step == 0:
                face.fixed(x, y, tangga(rgb, 128 + 18 * ((x + y) % 4 == 0)))


def clouds(face, y0, rgb=GOLD):
    """A row of xiangyun cloud curls (gold) along a hem."""
    pat = [".XX.", "X..X", "X.XX", ".X.."]
    for y, row in enumerate(pat):
        for x in range(face.w):
            c = row[x % 4]
            if c == "X":
                face.fixed(x, y0 + y, tangga(rgb, 136 if y < 2 else 118))


def bones(face, x0, y0):
    """Rib-cage motif of the demonic robe."""
    for y in range(5):
        for x in range(6):
            if x in (2, 3) or (y % 2 == 0 and x not in (2, 3)):
                face.fixed(x0 + x, y0 + y, 0xD8D0C0 if (x + y) % 3 else 0xB8B0A0)


def coins(face, y0, y1):
    """Copper-cash pattern of the merchant brocade, in the LOGAM slot."""
    for y in range(y0, y1):
        for x in range(face.w):
            if (x % 4 == 1 and y % 4 == 1):
                face.put(x, y, LOGAM, 150, 255, F_SHINE)
            elif (x % 4 in (0, 2) and y % 4 == 1) or (x % 4 == 1 and y % 4 in (0, 2)):
                face.put(x, y, LOGAM, 112)


def patches(face, seed):
    cols = [0x6A5A44, 0x8A7050, 0x4E4A40]
    for i in range(3):
        px = (seed * 7 + i * 3) % max(1, face.w - 2)
        py = (seed * 5 + i * 4) % max(1, face.h - 3)
        for y in range(3):
            for x in range(2):
                face.fixed(px + x, py + y, tangga(cols[i], 128 - 10 * (y == 2)))
        face.fixed(px, py, tangga(cols[i], 96))


# ====================================================================== underwear


def underwear(sex, slim):
    s = Lembar(64, 64)
    b = Badan(s, slim)
    for leg in legs(b):
        w = b.wrap(leg, 0)
        for st in range(w.len):
            for y in range(0, 9):
                w.fixed(st, y, LINEN if (st + y) % 5 else LINEN_D)
    bw = b.wrap("badan", 0)
    for st in range(bw.len):
        for y in range(9, 12):
            bw.fixed(st, y, LINEN_D if y == 9 else LINEN)
    if sex == 1:
        fr = b.face("badan", 0, "depan")
        for y in range(2, 7):
            for x in range(1, 7):
                fr.fixed(x, y, LINEN if y > 2 else LINEN_D)
        bk = b.face("badan", 0, "belakang")
        for x in range(8):
            bk.fixed(x, 4, LINEN_D)
    return s


# ====================================================================== styles


def paint_style(gaya, slim):
    s = Lembar(64, 64)
    b = Badan(s, slim)
    jf = b.face("badan", 1, "depan")
    jb = b.face("badan", 1, "belakang")
    base_front = b.face("badan", 0, "depan")
    reverse = gaya == "KAFAN"

    if gaya in ("HANFU_L", "BANGSAWAN", "KAFAN", "RAHIB", "KASAYA", "HAKIM_BAWAH", "ABADI"):
        inner_shirt(b, KAIN2)
        inner_collar(base_front, KAIN2, reverse)
        trousers(b, KAIN1, 96)
        jw = body_jacket(b, KAIN1, 128)
        collar_cross(jf, TEPI, KAIN2, reverse, 6, layers=3 if gaya == "BANGSAWAN" else 1)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 128, 10, TEPI, 1 if gaya != "BANGSAWAN" else 2, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 118, 11, TEPI)
        if gaya != "KAFAN":
            belt(jw, KAIN2 if gaya != "HAKIM_BAWAH" else TEPI, (6, 8))
        if gaya == "BANGSAWAN":
            for arm in arms(b):
                fr = b.face(arm, 1, "depan")
                gold_embroidery(fr, 6, 8)
            top = b.face("badan", 1, "atas")
            for x in range(8):
                top.fixed(x, 1, tangga(GOLD, 132))
            for x in (0, 1, 6, 7):
                jf.fixed(x, 1, tangga(GOLD, 120))
                jb.fixed(x, 1, tangga(GOLD, 120))
        if gaya == "ABADI":
            for arm in arms(b):
                w = b.wrap(arm, 1)
                for st in range(w.len):
                    if st % 3 == 0:
                        w.fixed(st, 8, tangga(GOLD, 140))
            for leg in legs(b):
                clouds(b.face(leg, 1, "depan"), 6)
                clouds(b.face(leg, 1, "belakang"), 6)
        if gaya == "KASAYA":
            # the patched red-and-gold kasaya over the left shoulder, down to the right hip, front and back
            red, gold = 0x8A2A1E, GOLD
            for face, mirror in ((jf, False), (jb, True)):
                for y in range(12):
                    for x in range(8):
                        d = (7 - x) - y if not mirror else x - y
                        if -6 <= d <= 1:
                            line = (x + y) % 4 == 0 or y % 4 == 3
                            face.fixed(x, y, tangga(gold if line else red, 132 if line else 118 - 4 * (y > 7)))
            top = b.face("badan", 1, "atas")
            for x in range(4, 8):
                for y in range(4):
                    top.fixed(x, y, tangga(red, 130))
            wl = b.wrap("lengan_kiri", 1)
            for st in range(wl.len):
                for y in range(0, 3):
                    wl.fixed(st, y, tangga(red if (st + y) % 4 else gold, 124))
        if gaya == "KAFAN":
            # burial white: no belt, loose, a thin tie
            for x in range(8):
                jf.put(x, 7, KAIN1, 116)
    elif gaya == "HANFU_P":
        # qixiong ruqun: short jacket with wide sleeves, the skirt tied high at the chest
        inner_shirt(b, KAIN2)
        inner_collar(base_front, KAIN2)
        trousers(b, KAIN1, 110)
        jw = body_jacket(b, KAIN1, 126)
        top = b.face("badan", 1, "atas")
        top.fill(KAIN2, 138, 255, F_NOISE)
        for y in range(0, 2):
            jw.row(y, KAIN2, 136, F_NOISE)
        collar_cross(jf, TEPI, KAIN2, False, 2)
        jw.row(2, TEPI, 136, F_SHINE)         # the chest ribbon
        jw.row(3, TEPI, 112)
        for y in range(4, 12):
            for x in range(8):
                if x % 3 == 1:
                    jf.shade(x, y, -8)        # skirt pleats
        for arm in arms(b):
            sleeve(b, arm, KAIN2, 136, 10, TEPI, 1, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 124, 12, TEPI)
        for leg in legs(b):
            fr = b.face(leg, 1, "depan")
            for y in range(12):
                fr.shade(1, y, -6)
    elif gaya in ("PENDEK", "PELAYAN"):
        inner_shirt(b, KAIN2)
        inner_collar(base_front, KAIN2)
        trousers(b, KAIN2, 112)
        jw = body_jacket(b, KAIN1, 126)
        collar_cross(jf, TEPI if gaya == "PENDEK" else KAIN2, KAIN2, False, 5)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 126, 10, TEPI if gaya == "PENDEK" else KAIN1, 1, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 118, 6, TEPI if gaya == "PENDEK" else None)
        belt(jw, SAMAK if gaya == "PENDEK" else KAIN2, (6, 8))
        if gaya == "PENDEK":
            calf_wraps(b)
        else:
            # apron: front panel from the chest to the knee
            for y in range(4, 12):
                for x in range(1, 7):
                    jf.put(x, y, KAIN2, 142 - (4 if x in (1, 6) else 0), 255, F_NOISE)
            for x in range(1, 7):
                jf.put(x, 4, KAIN2, 150)
            for leg in legs(b):
                fr = b.face(leg, 1, "depan")
                xs = (1, 2, 3) if leg == "kaki_kanan" else (0, 1, 2)
                for y in range(0, 4):
                    for x in xs:
                        fr.put(x, y, KAIN2, 138, 255, F_NOISE)
    elif gaya == "TEMPUR":
        inner_shirt(b, KAIN2)
        inner_collar(base_front, KAIN2)
        trousers(b, KAIN2, 92)
        jw = body_jacket(b, KAIN1, 122)
        collar_cross(jf, TEPI, KAIN2, False, 5)
        for arm in arms(b):
            narrow_sleeve(b, arm, KAIN1, 122)
        robe_skirt_on_legs(b, KAIN1, 114, 7, TEPI, split=True)
        belt(jw, SAMAK, (6, 8), LOGAM, jw.front_range())
        for leg in legs(b):
            w = b.wrap(leg, 1)
            for y in range(7, 12):
                w.row(y, SAMAK, 104 + (8 if y == 7 else 0))
    elif gaya == "TAO":
        inner_shirt(b, KAIN2)
        trousers(b, KAIN1, 96)
        jw = body_jacket(b, KAIN1, 124)
        collar_square(jf, KAIN2)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 124, 10, KAIN2, 2, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 116, 11, KAIN2)
        belt(jw, SAMAK, (6, 7))
        jw.row(7, KAIN1, 110)
        yin_yang(jb, 1, 1)
        for y in range(12):
            jf.put(0, y, KAIN2, 132)
            jf.put(7, y, KAIN2, 132)
    elif gaya == "IBLIS":
        inner_shirt(b, KAIN2)
        trousers(b, KAIN1, 84)
        jw = body_jacket(b, KAIN1, 118)
        collar_cross(jf, KAIN2, KAIN2, False, 5, double=True)
        bones(jf, 1, 2)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 118, 10, KAIN2, 1, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 110, 11, KAIN2)
        belt(jw, TEPI, (7, 8))
        jw.row(6, KAIN2, 100)
        top = b.face("badan", 1, "atas")
        for x in range(8):
            top.put(x, 0, KAIN1, 100)
            top.put(x, 3, KAIN1, 100)
    elif gaya == "SAUDAGAR":
        inner_shirt(b, KAIN2)
        trousers(b, KAIN1, 92)
        jw = body_jacket(b, KAIN1, 128)
        coins(jf, 3, 12)
        coins(jb, 0, 12)
        collar_round(jf, TEPI)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 128, 10, TEPI, 1, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 120, 11, TEPI)
        # the fur-edged vest opening
        for y in range(1, 12):
            for x in (1, 6):
                jf.put(x, y, SAMAK, 140 + (8 if y % 2 else -6), 255, F_NOISE)
        belt(jw, LOGAM, (7, 8))
    elif gaya == "PETANI":
        # coarse short shirt on the base layer, rolled trousers, short sleeves, rope belt
        bw = wrap_cloth(b, "badan", 0, KAIN1, 126, 0, 12)
        top_face(b, "badan", 0, KAIN1)
        fr = b.face("badan", 0, "depan")
        for x in (2, 3, 4):
            fr.clear(x, 0)
        fr.put(3, 0, KULIT, 110)
        fr.put(4, 0, KULIT, 112)
        fr.put(2, 0, KULIT, 116)
        for i, (x, y) in enumerate(((5, 0), (5, 1), (4, 2), (3, 3), (2, 4))):
            fr.put(x, y, KAIN1, 100)
        for y in range(6, 8):
            bw.row(y, SAMAK, 110 + 10 * (y == 6))
        for arm in arms(b):
            w = wrap_cloth(b, arm, 0, KAIN1, 124, 0, 5)
            w.row(4, KAIN1, 104)
            top_face(b, arm, 0, KAIN1)
        for leg in legs(b):
            w = wrap_cloth(b, leg, 0, KAIN2 if False else KAIN1, 104, 0, 7)
            w.row(6, KAIN1, 132)
            w.row(7, KAIN1, 118)
            top_face(b, leg, 0, KAIN1, 110)
        folds(fr, (2, 5), 8, 12, -8)
    elif gaya == "PENJAGA":
        inner_shirt(b, KAIN2)
        trousers(b, KAIN1, 96)
        jw = body_jacket(b, KAIN1, 120)
        # quilted padding rows
        for y in range(1, 12, 2):
            for x in range(8):
                jf.shade(x, y, -10)
                jb.shade(x, y, -10)
        collar_cross(jf, TEPI, KAIN2, False, 4)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 120, 10, TEPI, 1, KAIN2)
            w = b.wrap(arm, 1)
            for y in range(1, 10, 2):
                for st in range(w.len):
                    w.shade(st, y, -8)
        belt(jw, SAMAK, (6, 8), LOGAM, jw.front_range())
        robe_skirt_on_legs(b, KAIN1, 112, 5, TEPI, split=True)
        for leg in legs(b):
            w = b.wrap(leg, 1)
            for y in range(5, 12):
                w.row(y, SAMAK, 104 + (14 if y == 5 else 0) - (6 if y == 11 else 0))
    elif gaya in ("PEJABAT", "SARJANA"):
        inner_shirt(b, KAIN2)
        trousers(b, KAIN1, 96)
        jw = body_jacket(b, KAIN1, 126)
        if gaya == "PEJABAT":
            collar_round(jf, TEPI)
            belt(jw, TEPI, (7, 8))
            fw = b.wrap("badan", 1)
            s0, s1 = fw.front_range()
            for st in range(s0, s1, 2):
                fw.put(st, 7, GIOK, 150, 255, F_SHINE)
        else:
            collar_straight(jf, TEPI)
            for y in range(12):
                jf.put(0, y, TEPI, 104)
                jf.put(7, y, TEPI, 104)
            belt(jw, KAIN2, (6, 7))
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 126, 10, TEPI, 2 if gaya == "SARJANA" else 1, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 118, 11, TEPI)
    elif gaya == "COMPANG":
        trousers(b, KAIN1, 96, 9)
        jw = body_jacket(b, KAIN1, 116)
        collar_cross(jf, KAIN1, KAIN2, False, 5)
        patches(jf, 3)
        patches(jb, 5)
        for arm in arms(b):
            w = wrap_cloth(b, arm, 1, KAIN1, 114, 0, 8)
            for st in range(w.len):
                if st % 3 == 0:
                    w.clear(st, 7)
        for leg in legs(b):
            w = wrap_cloth(b, leg, 1, KAIN1, 108, 0, 7)
            for st in range(w.len):
                if st % 2:
                    w.clear(st, 6)
            patches(b.face(leg, 1, "depan"), 2 if leg == "kaki_kanan" else 4)
        for x in range(8):
            if x % 2:
                jf.clear(x, 11)
                jb.clear(x, 11)
        belt(jw, SAMAK, (6, 7))
    elif gaya == "ANAK":
        inner_shirt(b, KAIN2)
        trousers(b, KAIN2, 118, 9)
        jw = body_jacket(b, KAIN1, 134, 9)
        # side-buttoned jacket: buttons down the wearer's right
        for y in (1, 3, 5, 7):
            jf.put(1, y, LOGAM, 150, 255, F_SHINE)
        for y in range(9):
            jf.put(2, y, TEPI, 120)
        for x in (3, 4):
            jf.clear(x, 0)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 134, 9, TEPI, 1, KAIN2)
        for leg in legs(b):
            w = b.wrap(leg, 0)
            w.row(8, TEPI, 120)
    elif gaya == "LAUT":
        # striped sailor vest, bare arms, rolled trousers, rope belt
        bw = wrap_cloth(b, "badan", 0, KAIN2, 140, 0, 12)
        for y in range(0, 12):
            if y % 2 == 0:
                bw.row(y, KAIN1, 120, F_NOISE)
        fr = b.face("badan", 0, "depan")
        for x in (3, 4):
            fr.clear(x, 0)
            fr.put(x, 0, KULIT, 112)
        for y in (6, 7):
            bw.row(y, SAMAK, 120 - 12 * (y == 7))
        for leg in legs(b):
            w = wrap_cloth(b, leg, 0, KAIN1, 100, 0, 8)
            w.row(7, KAIN1, 126)
            top_face(b, leg, 0, KAIN1, 108)
    elif gaya == "GURUN":
        inner_shirt(b, KAIN2)
        trousers(b, KAIN2, 116)
        jw = body_jacket(b, KAIN1, 130)
        collar_round(jf, KAIN2)
        # the sash across from the left shoulder to the right hip
        for face, mirror in ((jf, False), (jb, True)):
            for y in range(12):
                x = 7 - y if not mirror else y
                for dx in (0, 1):
                    xx = x + dx if not mirror else x - dx
                    if 0 <= xx < 8:
                        face.put(xx, y, KAIN2, 124 - 6 * dx, 255, F_NOISE)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 130, 10, KAIN2, 1, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 124, 11, KAIN2)
        belt(jw, TEPI, (7, 8))
    elif gaya in KOTA42_GAYA:
        paint_style_42(b, gaya, jf, jb, base_front)
    return s


# ---------------------------------------------------------------------- DESIGN 42.12.4 styles (Kota yang Hidup, 42V-B)

PHOENIX = [  # 6 x 7 phoenix in gold over the bride's chest: crest, head, spread wings, the long tail sweeping down
    "..X...",
    ".XX..X",
    "XXXXX.",
    ".XXXXX",
    "..XX..",
    "..X.X.",
    ".X...X",
]
DRAGON_ROUNDEL = [  # 4 x 4 gold roundel on the groom's chest and back (a coiled dragon in brief)
    ".XX.",
    "X.oX",
    "XX.X",
    ".XX.",
]


def motif(face, pat, x0, y0, rgb=GOLD, eye=None):
    for y, row in enumerate(pat):
        for x, c in enumerate(row):
            if c == "X":
                face.fixed(x0 + x, y0 + y, tangga(rgb, 140 - 8 * (y % 2)))
            elif c == "o" and eye is not None:
                face.fixed(x0 + x, y0 + y, eye)


def frayed_hem(b, part, layer, y, every=2):
    """Unhemmed cloth: every other pixel of row y is torn away (mourning hemp is worn raw)."""
    w = b.wrap(part, layer)
    for s in range(w.len):
        if s % every == 0:
            w.clear(s, y)


def paint_style_42(b, gaya, jf, jb, base_front):
    if gaya == "PENGANTIN_L":
        # the groom: a long red robe with a round collar, gold dragon roundels on chest and back, gold-cloud cuffs and hem,
        # a red belt with jade plaques
        inner_shirt(b, KAIN2)
        trousers(b, KAIN1, 96)
        jw = body_jacket(b, KAIN1, 130)
        collar_round(jf, TEPI)
        motif(jf, DRAGON_ROUNDEL, 2, 2, GOLD, 0x2A1A10)
        motif(jb, DRAGON_ROUNDEL, 2, 2, GOLD, 0x2A1A10)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 130, 10, TEPI, 2, KAIN2)
            clouds(b.face(arm, 1, "depan"), 6)
        robe_skirt_on_legs(b, KAIN1, 122, 11, TEPI)
        for leg in legs(b):
            clouds(b.face(leg, 1, "depan"), 7)
            clouds(b.face(leg, 1, "belakang"), 7)
        belt(jw, TEPI, (7, 8))
        fw = b.wrap("badan", 1)
        s0, s1 = fw.front_range()
        for st in range(s0, s1, 2):
            fw.put(st, 7, GIOK, 150, 255, F_SHINE)
    elif gaya == "PENGANTIN_P":
        # the bride: a red cross-collared jacket with a gold phoenix over the chest, peony sprays on the back, wide sleeves
        # with gold cuffs, a long red skirt with gold wave-and-cloud bands at the hem
        inner_shirt(b, KAIN2)
        inner_collar(base_front, KAIN2)
        trousers(b, KAIN1, 110)
        jw = body_jacket(b, KAIN1, 132)
        collar_cross(jf, TEPI, KAIN2, False, 4, layers=2)
        motif(jf, PHOENIX, 1, 3, GOLD)
        for y0 in (2, 7):
            for x in (1, 5):
                jb.fixed(x, y0, tangga(0xE85A6A, 136))
                jb.fixed(x + 1, y0, tangga(0xE85A6A, 120))
                jb.fixed(x, y0 + 1, tangga(GOLD, 130))
                jb.fixed(x + 1, y0 + 1, tangga(0x3E8A4E, 120))
        jw.row(8, TEPI, 136, 255, F_SHINE)  # the high skirt band
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 132, 10, TEPI, 2, KAIN2)
            gold_embroidery(b.face(arm, 1, "depan"), 7, 8)
        robe_skirt_on_legs(b, KAIN1, 126, 12, TEPI)
        for leg in legs(b):
            for face in ("depan", "belakang", "kanan", "kiri"):
                clouds(b.face(leg, 1, face), 7)
                f = b.face(leg, 1, face)
                for x in range(f.w):
                    f.fixed(x, 11, tangga(GOLD, 120 + 12 * (x % 2)))
    elif gaya == "BERKABUNG":
        # mourning: a loose robe of coarse undyed hemp, the collar crossed, no dye, no trim; the hem left raw and torn
        # (the closer the kin, the coarser the cloth), a hemp rope belt
        inner_shirt(b, KAIN2)
        inner_collar(base_front, KAIN2)
        trousers(b, KAIN2, 110)
        jw = body_jacket(b, KAIN1, 132)
        collar_cross(jf, KAIN2, KAIN2, False, 5)
        for face in (jf, jb):
            for y in range(12):
                for x in range(8):
                    if (x * 5 + y * 3) % 7 == 0:
                        face.shade(x, y, -10)  # slubs of the coarse weave
        for arm in arms(b):
            wrap_cloth(b, arm, 1, KAIN1, 130, 0, 10)
            top_face(b, arm, 1, KAIN1, 136)
            frayed_hem(b, arm, 1, 9)
        robe_skirt_on_legs(b, KAIN1, 124, 11, None)
        for leg in legs(b):
            frayed_hem(b, leg, 1, 10)
        for y in (6, 7):
            for st in range(jw.len):
                jw.fixed(st, y, tangga(0xB8AA88 if (st + y) % 2 else 0x9A8C6C, 128))
        for x in (3, 4):
            jf.fixed(x, 8, tangga(0xB8AA88, 116))  # the knot ends of the hemp rope
            jf.fixed(x, 9, tangga(0x9A8C6C, 110))
    elif gaya == "MANTEL_KAPAS":
        # winter: a knee-length cotton-padded coat quilted in rows, a round collar, frog buttons down the wearer's right,
        # puffed sleeves with turned-back cuffs, padded trousers bound at the ankle
        inner_shirt(b, KAIN2)
        trousers(b, KAIN2, 104)
        for leg in legs(b):
            w = b.wrap(leg, 0)
            for y in range(1, 10, 2):
                for st in range(w.len):
                    w.shade(st, y, -8)
            w.row(10, SAMAK, 108)
        jw = body_jacket(b, KAIN1, 130)
        for y in range(1, 12, 2):
            for x in range(8):
                jf.shade(x, y, -11)
                jb.shade(x, y, -11)
        collar_round(jf, TEPI)
        for y in range(2, 11):
            jf.put(5, y, TEPI, 118)
        for y in (2, 4, 6, 8):
            jf.put(4, y, LOGAM, 150, 255, F_SHINE)
            jf.put(5, y, LOGAM, 130)
        for arm in arms(b):
            sleeve(b, arm, KAIN1, 132, 10, KAIN2, 2, KAIN2)
            w = b.wrap(arm, 1)
            for y in range(1, 8, 2):
                for st in range(w.len):
                    w.shade(st, y, -9)
        robe_skirt_on_legs(b, KAIN1, 124, 6, TEPI)
        for leg in legs(b):
            w = b.wrap(leg, 1)
            for y in range(1, 5, 2):
                for st in range(w.len):
                    w.shade(st, y, -9)
    elif gaya == "AKROBAT":
        # the troupe: tight silk in diagonal bands of the two colours, narrow sleeves and leggings, a broad sash, calf wraps
        jw = wrap_cloth(b, "badan", 1, KAIN1, 128, 0, 12)
        top_face(b, "badan", 1, KAIN1, 136)
        for st in range(jw.len):
            for y in range(12):
                if (st + y) % 6 < 3:
                    jw.put(st, y, KAIN2, 136, 255, F_NOISE)
        collar_round(jf, TEPI)
        for arm in arms(b):
            w = wrap_cloth(b, arm, 1, KAIN1, 128, 0, 10)
            top_face(b, arm, 1, KAIN1)
            for st in range(w.len):
                for y in range(10):
                    if (st + y) % 6 < 3:
                        w.put(st, y, KAIN2, 134, 255, F_NOISE)
            w.row(9, TEPI, 120)
        for leg in legs(b):
            w = wrap_cloth(b, leg, 0, KAIN1, 120, 0, 12)
            top_face(b, leg, 0, KAIN1, 128)
            for st in range(w.len):
                for y in range(8):
                    if (st - y) % 6 < 3:
                        w.put(st, y, KAIN2, 128, 255, F_NOISE)
        belt(jw, TEPI, (6, 8))
        calf_wraps(b, (8, 11))
    elif gaya == "PENARI":
        # the dancer: a short silk jacket and a skirt tied high under the arms, flower sprays embroidered in pink, long
        # sleeves of the second colour that fall past the wrist, a pleated skirt to the ankle
        inner_shirt(b, KAIN2)
        inner_collar(base_front, KAIN2)
        trousers(b, KAIN1, 116)
        jw = body_jacket(b, KAIN1, 130)
        collar_cross(jf, TEPI, KAIN2, False, 2)
        jw.row(2, TEPI, 140, 255, F_SHINE)
        jw.row(3, TEPI, 116)
        for y in range(4, 12):
            for x in range(8):
                if x % 2 == 1:
                    jf.shade(x, y, -7)  # skirt pleats
        for (x, y) in ((1, 6), (5, 8), (2, 10), (6, 5)):
            jf.fixed(x, y, tangga(0xF08AA8, 140))
            jf.fixed(x + 1, y, tangga(0xD8607E, 124))
            jf.fixed(x, y + 1, tangga(0x5A9A5A, 116))
        for arm in arms(b):
            sleeve(b, arm, KAIN2, 138, 12, None, 0, KAIN2)
        robe_skirt_on_legs(b, KAIN1, 128, 12, TEPI)
        for leg in legs(b):
            fr = b.face(leg, 1, "depan")
            for y in range(12):
                for x in range(fr.w):
                    if x % 2 == 0:
                        fr.shade(x, y, -6)
    elif gaya == "PENCOPET":
        # the pickpocket: a dark short jacket with patches and a torn hem, sleeves rolled to the forearm, dark trousers
        # tied at the calf, a rope belt with a hidden inner pocket seam
        inner_shirt(b, KAIN2, 6)
        inner_collar(base_front, KAIN2)
        trousers(b, KAIN1, 92)
        jw = body_jacket(b, KAIN1, 112)
        collar_cross(jf, KAIN2, KAIN2, False, 5)
        patches(jf, 2)
        patches(jb, 6)
        for arm in arms(b):
            w = wrap_cloth(b, arm, 1, KAIN1, 112, 0, 6)
            top_face(b, arm, 1, KAIN1)
            w.row(5, KAIN1, 132)  # the rolled cuff
            w.row(4, KAIN1, 98)
        robe_skirt_on_legs(b, KAIN1, 104, 5, None)
        frayed_hem(b, "badan", 1, 11, 3)
        belt(jw, SAMAK, (6, 7))
        for y in range(3, 6):
            jf.put(6, y, KAIN1, 92)  # the inner pocket seam
        calf_wraps(b, (8, 11))


# ---------------------------------------------------------------------- drape (detail pass)

# cloth classes of the drape pass: patterned brocade, coarse homespun, smooth silk (others get light and folds only)
BROKAT = {"SAUDAGAR", "BANGSAWAN", "PEJABAT", "HAKIM_BAWAH", "PENGANTIN_L"}
KASAR = {"PETANI", "COMPANG", "RAHIB", "KASAYA", "LAUT", "GURUN", "PENDEK", "PELAYAN", "ANAK", "PENJAGA", "BERKABUNG", "MANTEL_KAPAS",
         "PENCOPET"}
SUTRA = {"HANFU_L", "HANFU_P", "TAO", "ABADI", "SARJANA", "KAFAN", "IBLIS", "TEMPUR", "PENGANTIN_P", "AKROBAT", "PENARI"}
DRAPE_PARTS = ("badan", "lengan_kanan", "lengan_kiri", "kaki_kanan", "kaki_kiri")


def _falloff(part, y, h):
    """Light from above: shoulders and hip tops catch light, hems and the hollow under the torso fall into shade."""
    if part == "badan":
        return (24, 18, 11, 6, 2, 0, -2, -4, -8, -13, -18, -26)[min(y, 11)]
    if part.startswith("lengan"):
        return (26, 18, 9, 3, -10, -12, 0, -2, -6, -10, -15, -21)[min(y, 11)]
    return (-30, -18, -6, 4, 9, 9, 5, 0, -6, -12, -18, -26)[min(y, 11)]


def _weave(gaya, layer, slot, gx, gy):
    """Tone offset of the cloth class at sheet pixel (gx, gy)."""
    if gaya in BROKAT and layer == 1 and slot == KAIN1:
        # lattice of small lozenge flowers on a 6 px diagonal grid
        if (gx + gy) % 6 == 0 and (gx - gy) % 6 == 0:
            return 32
        if ((gx + gy) % 6 == 0 and (gx - gy) % 6 in (1, 5)) or ((gx - gy) % 6 == 0 and (gx + gy) % 6 in (1, 5)):
            return 14
        return -3
    if gaya in KASAR:
        t = -9 if gy % 3 == 2 else 0        # weft rows
        if (gx * 7 + gy * 13) % 17 == 0:
            t -= 26                         # a slub
        elif (gx * 5 + gy * 3) % 23 == 0:
            t += 18                         # a lighter thread
        return t
    return 0


def drape(s, gaya, slim):
    """Detail pass over the dyed cloth (KAIN1/KAIN2, not fixed colours) of a style sheet: light falloff, a highlight on
    the ridge beside every fold line, rounder silhouettes, the weave of the cloth class and a silk sheen line."""
    for part in DRAPE_PARTS:
        for layer in (0, 1):
            for face in ("depan", "kanan", "kiri", "belakang"):
                x0, y0, fw, fh = face_rect(part, layer, face, slim)
                tones = [[s.get(x0 + x, y0 + y) for x in range(fw)] for y in range(fh)]
                for y in range(fh):
                    for x in range(fw):
                        p = tones[y][x]
                        if not p[3] or p[2] & 8:
                            continue
                        slot = (p[1] + 8) >> 4
                        if slot not in (KAIN1, KAIN2):
                            continue
                        dt = _falloff(part, y, fh) + _weave(gaya, layer, slot, x0 + x, y0 + y)
                        if face in ("depan", "belakang"):
                            if x == 0 or x == fw - 1:
                                dt -= 14
                            # a fold line (darker than both neighbours) deepens
                            if 1 <= x < fw - 1:
                                l, r = tones[y][x - 1], tones[y][x + 1]
                                if l[3] and r[3] and p[0] <= l[0] - 5 and p[0] <= r[0] - 5:
                                    dt -= 14
                            # ridge: the pixel right of a fold line (darker than both neighbours) catches the light
                            if x >= 2:
                                l2, l1 = tones[y][x - 2], tones[y][x - 1]
                                if l1[3] and l2[3] and not l1[2] & 8 and l1[0] <= p[0] - 6 and l1[0] <= l2[0] - 6:
                                    dt += 22
                            if gaya in SUTRA and layer == 1 and slot == KAIN1 and x == fw // 2 - 1 and y > 1:
                                dt += 14
                        if dt:
                            s.shade(x0 + x, y0 + y, dt)
    return s


# ---------------------------------------------------------------------- trim levels (40.3.8)


def trim_level(main, level, slim):
    """tepi1: an embroidered second line one pixel inside the TEPI pixels of the outer layer; tepi2: a 2 px trim with a
    cloud stitch ring (SULAM) - drawn over the style sheet."""
    out = Lembar(64, 64)
    outer_rects = []
    for part in ("badan", "lengan_kanan", "lengan_kiri", "kaki_kanan", "kaki_kiri"):
        for face in ("depan", "kanan", "kiri", "belakang"):
            outer_rects.append(face_rect(part, 1, face, slim))
    def in_outer(x, y):
        return any(r[0] <= x < r[0] + r[2] and r[1] <= y < r[1] + r[3] for r in outer_rects)
    for y in range(64):
        for x in range(64):
            if not in_outer(x, y) or main.slot_at(x, y) != TEPI:
                continue
            for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                nx, ny = x + dx, y + dy
                if not in_outer(nx, ny) or main.slot_at(nx, ny) not in (KAIN1,):
                    continue
                if level == 1:
                    if (nx + ny) % 2 == 0:
                        out.put(nx, ny, SULAM, 140)
                else:
                    out.put(nx, ny, TEPI, 118)
                    for ex, ey in ((dx * 2, dy * 2),):
                        fx, fy = x + ex, y + ey
                        if in_outer(fx, fy) and main.slot_at(fx, fy) == KAIN1 and (fx + fy) % 3 == 0:
                            out.put(fx, fy, SULAM, 150, 255, F_SHINE)
    return out


# ---------------------------------------------------------------------- badges, extras, head cloths, footwear


def buzi(kind):
    """4x4 rank badge on the chest (jacket front at x 2-5, y 3-6): crane (civil), tiger (military), coin, black tiger."""
    s = Lembar(64, 64)
    b = Badan(s, False)
    f = b.face("badan", 1, "depan")
    ground = {"bangau": 0x2A3A6A, "harimau": 0x6A1E1E, "koin": 0x5A3A1A, "harimau_hitam": 0x1A1618}[kind]
    rim = GOLD
    for y in range(4):
        for x in range(4):
            edge = x in (0, 3) or y in (0, 3)
            f.fixed(2 + x, 3 + y, tangga(rim if edge else ground, 130 if edge else 120))
    if kind == "bangau":
        f.fixed(3, 4, 0xF4F0E8)
        f.fixed(4, 5, 0xF4F0E8)
        f.fixed(4, 4, 0xC0303A)
    elif kind in ("harimau", "harimau_hitam"):
        f.fixed(3, 4, 0xE8A040 if kind == "harimau" else 0x2A2420)
        f.fixed(4, 4, 0x1A1410 if kind == "harimau" else 0xC0A060)
        f.fixed(3, 5, 0x1A1410 if kind == "harimau" else 0xC0A060)
        f.fixed(4, 5, 0xE8A040 if kind == "harimau" else 0x2A2420)
    else:
        f.fixed(3, 4, tangga(GOLD, 150))
        f.fixed(4, 5, tangga(GOLD, 150))
        f.fixed(4, 4, 0x2A1A0A)
        f.fixed(3, 5, 0x2A1A0A)
    return s


def extra(kind, slim):
    s = Lembar(64, 64)
    b = Badan(s, slim)
    jf = b.face("badan", 1, "depan")
    jb = b.face("badan", 1, "belakang")
    if kind == "bepergian":
        # the bundle strap from the right shoulder to the left hip, front and back
        for face, mirror in ((jf, True), (jb, False)):
            for y in range(10):
                x = y * 7 // 9 if mirror else 7 - y * 7 // 9
                face.put(x, y, SAMAK, 118)
                face.put(x + (1 if mirror else -1), y, SAMAK, 100)
    elif kind == "kerja":
        # rope tied crosswise over the back to hold the sleeves up, rope bands on the upper arms
        for y in range(12):
            for x in range(8):
                if x == y * 7 // 11 or x == 7 - y * 7 // 11:
                    jb.put(x, y, SAMAK, 120 if y % 2 else 104)
        for arm in ("lengan_kanan", "lengan_kiri"):
            w = b.wrap(arm, 1)
            w.row(2, SAMAK, 116)
            w.row(3, SAMAK, 100)
    elif kind == "zirah":
        # duty lamellar on the chest and back (LOGAM rows with SAMAK lacing)
        for face in (jf, jb):
            for y in range(1, 7):
                for x in range(8):
                    lace = (x + (y % 2) * 2) % 4 == 0
                    face.put(x, y, SAMAK if lace else LOGAM, 104 if lace else 132 - 6 * (y % 2), 255,
                             0 if lace else F_SHINE * (x % 4 == 1))
        for x in range(8):
            jf.put(x, 7, SAMAK, 100)
            jb.put(x, 7, SAMAK, 100)
    elif kind == "tidur":
        # loose white sleeping robe and trousers over everything
        for part, rows in (("badan", 12), ("lengan_kanan", 11), ("lengan_kiri", 11), ("kaki_kanan", 10), ("kaki_kiri", 10)):
            w = b.wrap(part, 1)
            for st in range(w.len):
                for y in range(rows):
                    w.fixed(st, y, tangga(0xEEEAE0, 128 - 10 * ((st + y // 3) % 3 == 0)))
        for x in (2, 3, 4):
            jf.clear(x, 0)
        jf.clear(3, 1)
    return s


def head_cloth(kind):
    """Head cloths drawn after the hair (hat layer, plus a veil over the mouth for the disguise)."""
    s = Lembar(64, 64)
    hf = {f: FacePainter(s, face_rect("kepala", 1, f)) for f in ("atas", "depan", "kanan", "kiri", "belakang")}
    if kind in ("kerudung", "tudung"):
        slot = KAIN2 if kind == "kerudung" else KAIN1
        tone = 132 if kind == "kerudung" else 96
        hf["atas"].fill(slot, tone + 6, 255, F_NOISE)
        for face in ("kanan", "kiri", "belakang"):
            hf[face].fill(slot, tone - 6, 255, F_NOISE)
            for x in range(8):
                hf[face].put(x, 7, slot, tone - 16)
        for x in range(8):
            hf["depan"].put(x, 0, slot, tone)
            hf["depan"].put(x, 1, slot, tone - 10)
        for y in range(2, 8):
            hf["depan"].put(0, y, slot, tone - 8)
            hf["depan"].put(7, y, slot, tone - 8)
        if kind == "tudung":
            # the veil (cadar) over nose and mouth
            for y in range(5, 8):
                for x in range(1, 7):
                    hf["depan"].put(x, y, KAIN1, 84 + 4 * (y == 5), 255, F_NOISE)
    elif kind in ("ikat_putih", "ikat_kuning"):
        rgb = 0xEEEAE0 if kind == "ikat_putih" else 0xD8B040
        for face in ("depan", "kanan", "kiri", "belakang"):
            for x in range(8):
                hf[face].fixed(x, 1, tangga(rgb, 128))
                hf[face].fixed(x, 2, tangga(rgb, 110))
        hf["belakang"].fixed(3, 3, tangga(rgb, 120))
        hf["belakang"].fixed(4, 3, tangga(rgb, 104))
        hf["belakang"].fixed(4, 4, tangga(rgb, 96))
    return s


def footwear(kind):
    s = Lembar(64, 64)
    b = Badan(s, False)
    for leg in ("kaki_kanan", "kaki_kiri"):
        w0 = b.wrap(leg, 0)
        if kind == "kain":
            for st in range(w0.len):
                w0.put(st, 10, SAMAK, 74)
                w0.put(st, 11, SAMAK, 62)
            b.face(leg, 0, "bawah").fill(KAIN2, 132)
            fr = b.face(leg, 0, "depan")
            fr.put(1, 10, SAMAK, 92)
            fr.put(2, 10, SAMAK, 92)
        elif kind == "bot":
            w1 = b.wrap(leg, 1)
            for st in range(w1.len):
                for y in range(5, 12):
                    w1.put(st, y, SAMAK, 100 - (8 if y == 11 else 0) + (18 if y == 5 else 0))
            for st in range(w0.len):
                w0.put(st, 11, SAMAK, 70)
            b.face(leg, 0, "bawah").fill(SAMAK, 60)
            b.face(leg, 1, "bawah").fill(SAMAK, 60)
        elif kind in ("jerami", "rahib"):
            straw = 0xC8A860
            if kind == "rahib":
                for st in range(w0.len):
                    for y in range(7, 11):
                        w0.put(st, y, KAIN2, 146 - 4 * (y == 10), 255, F_NOISE)
            fr = b.face(leg, 0, "depan")
            for x in range(4):
                fr.fixed(x, 11, tangga(straw, 124))
            fr.fixed(1, 10, tangga(straw, 108))
            fr.fixed(2, 10, tangga(straw, 108))
            for side in ("kanan", "kiri"):
                f = b.face(leg, 0, side)
                f.fixed(1, 10, tangga(straw, 100))
                f.fixed(2, 9, tangga(straw, 96))
            b.face(leg, 0, "bawah").fill(KULIT, 100)
            bot = b.face(leg, 0, "bawah")
            for x in range(4):
                for y in range(4):
                    bot.fixed(x, y, tangga(straw, 116 - 8 * ((x + y) % 2)))
    return s


def bandage(kind, slim):
    s = Lembar(64, 64)
    b = Badan(s, slim)
    part = "lengan_kiri" if kind == "lengan" else "kaki_kiri"
    w = b.wrap(part, 1)
    rows = range(3, 9) if kind == "lengan" else range(4, 9)
    for y in rows:
        for st in range(w.len):
            w.fixed(st, y, tangga(0xEEEAE0, 132 - 14 * ((st + y) % 3 == 0)))
    w.fixed(1, rows[0] + 1, 0x9A3030, 200)
    return s


# ====================================================================== cube sheets (40.3.7)


def cube_sheet(cid):
    w, h = cube_sheet_size(UV_BUSANA[cid])
    return Lembar(w, h)


def faces_of(cid):
    box = UV_BUSANA[cid][2]
    return [(f, cube_face(0, 0, box[3], box[4], box[5], f)) for f in ("atas", "bawah", "kanan", "depan", "kiri", "belakang")]


SIDE_TONE = {"atas": 136, "bawah": 92, "kanan": 116, "kiri": 116, "depan": 128, "belakang": 120}


def paint_cloth(cid, slot=KAIN1, hem_slot=TEPI, hem_rows=1, pleats=0, tone_d=0, ragged=False, lining=None, opening=False,
                stitch=False):
    """Cloth cube. pleats: a dark pleat line every n px with a lit ridge after it; lining: face showing the KAIN2 lining
    (the body side of a cape); opening: the bottom face is a dark sleeve mouth with a 1 px rim; stitch: a dotted
    embroidery row (TEPI) just above the hem."""
    s = cube_sheet(cid)
    for f, (x0, y0, w, h) in faces_of(cid):
        for y in range(h):
            for x in range(w):
                t = SIDE_TONE[f] + tone_d
                slot_here = slot
                if f == lining:
                    slot_here = KAIN2
                    t = 104 - y // 3
                elif f == "bawah" and opening:
                    rim = x == 0 or y == 0 or x == w - 1 or y == h - 1
                    slot_here = (hem_slot if hem_slot is not None else slot) if rim else KAIN2
                    t = 112 if rim else 78
                else:
                    if pleats and f in ("depan", "belakang"):
                        if x % pleats == pleats - 1:
                            t -= 22
                        elif x % pleats == 0 and x > 0:
                            t += 14
                    if f not in ("atas", "bawah"):
                        t -= (y * 24) // max(1, h)
                        if y == 0 and h > 4:
                            t += 12         # the fold where the cloth turns over the belt or shoulder
                    if hem_slot is not None and f not in ("atas", "bawah") and y >= h - hem_rows:
                        slot_here = hem_slot
                        t = 122 - 6 * (y - (h - hem_rows))
                    elif stitch and hem_slot is not None and f not in ("atas", "bawah") and y == h - hem_rows - 1 and x % 2 == 0:
                        slot_here = hem_slot
                        t = 134
                if ragged and f not in ("atas", "bawah") and y == h - 1 and (x % 3 == 1):
                    continue
                s.put(x0 + x, y0 + y, slot_here, t, 255, F_NOISE if slot_here == slot else 0)
    return s


def paint_metal(cid, slot=LOGAM, lacing=True):
    s = cube_sheet(cid)
    for f, (x0, y0, w, h) in faces_of(cid):
        for y in range(h):
            for x in range(w):
                t = SIDE_TONE[f]
                if lacing and f not in ("atas", "bawah") and y % 2 == 1:
                    s.put(x0 + x, y0 + y, SAMAK, 100)
                    continue
                if f not in ("atas", "bawah") and x % 3 == 0:
                    t -= 12
                s.put(x0 + x, y0 + y, slot, t, 255, F_SHINE if (x + y) % 4 == 0 else 0)
    return s


def paint_fixed_cube(cid, rgb, pattern=None):
    s = cube_sheet(cid)
    for f, (x0, y0, w, h) in faces_of(cid):
        for y in range(h):
            for x in range(w):
                t = SIDE_TONE[f]
                if pattern == "anyam" and (x + y) % 2:
                    t -= 14
                if pattern == "gelang" and y % 2:
                    t -= 12
                if pattern == "bulu":
                    t += (8 if (x * 3 + y) % 4 == 0 else -6 if (x + y) % 3 == 0 else 0)
                s.fixed(x0 + x, y0 + y, tangga(rgb, t))
    return s


MATERIAL_BADGE = {"kayu": 0x7A5434, "perunggu": 0x8C6A3A, "perak": 0xC8CCD4, "giok": 0x5FA88C, "ungu": 0x8A6AC8,
                  "emas": 0xD8B04A}


def paint_pendant(cid, material=None):
    """liontin_giok / lencana_giok: jade or badge on top, a tassel (TEPI = the rank trim colour) below."""
    s = cube_sheet(cid)
    for f, (x0, y0, w, h) in faces_of(cid):
        for y in range(h):
            for x in range(w):
                head = y < max(1, h // 2) or f in ("atas",)
                if head:
                    if material is None:
                        s.put(x0 + x, y0 + y, GIOK, SIDE_TONE[f] + 6, 255, F_SHINE if (x + y) % 2 == 0 else 0)
                    else:
                        s.fixed(x0 + x, y0 + y, tangga(MATERIAL_BADGE[material], SIDE_TONE[f] + (10 if (x + y) % 3 == 0 else 0)))
                elif f == "bawah":
                    s.put(x0 + x, y0 + y, TEPI, 100)
                else:
                    s.put(x0 + x, y0 + y, TEPI, 124 - (10 if x % 2 else 0) - 4 * (y == h - 1))
    return s


def gen_cubes():
    for cid in ("rok_panjang_depan", "rok_panjang_belakang", "rok_pendek_depan", "rok_pendek_belakang"):
        save(paint_cloth(cid, KAIN1, TEPI, 2 if cid.startswith("rok_panjang") else 1, pleats=3, stitch=True),
             "busana/kubus/" + cid)
    for cid in ("rok_panjang_sisi", "rok_pendek_sisi"):
        save(paint_cloth(cid, KAIN1, TEPI, 1, pleats=2, tone_d=-6, stitch=True), "busana/kubus/" + cid)
    save(paint_cloth("lengan_lebar_manset", KAIN1, TEPI, 1, opening=True), "busana/kubus/lengan_lebar_manset")
    save(paint_cloth("lengan_lebar_kantong", KAIN1, TEPI, 1, pleats=2, tone_d=-4), "busana/kubus/lengan_lebar_kantong")
    save(paint_cloth("jubah_luar", KAIN1, TEPI, 1, pleats=3, tone_d=-14, lining="depan"), "busana/kubus/jubah_luar")
    save(paint_cloth("jubah_luar_bahu", KAIN1, TEPI, 1, tone_d=-10), "busana/kubus/jubah_luar_bahu")
    save(paint_cloth("kerah_tinggi", KAIN1, TEPI, 1, tone_d=-8), "busana/kubus/kerah_tinggi")
    save(paint_cloth("sabuk", KAIN2, None), "busana/kubus/sabuk")
    save(paint_cloth("pita_sabuk", TEPI, None, pleats=0), "busana/kubus/pita_sabuk")
    save(paint_pendant("liontin_giok"), "busana/kubus/liontin_giok")
    for m in MATERIAL_BADGE:
        save(paint_pendant("lencana_giok", m), "busana/kubus/lencana_giok_" + m)
    save(paint_cloth("kantong", SAMAK, SULAM, 1), "busana/kubus/kantong")
    save(paint_fixed_cube("labu", 0xC89838), "busana/kubus/labu")
    save(paint_metal("pelindung_lengan", SAMAK, lacing=False), "busana/kubus/pelindung_lengan")
    save(paint_metal("bahu_zirah", LOGAM), "busana/kubus/bahu_zirah")
    save(paint_metal("rok_zirah", LOGAM), "busana/kubus/rok_zirah")
    for cid in ("selendang_1", "selendang_2", "selendang_punggung"):
        s = paint_cloth(cid, SULAM, None, tone_d=10)
        for f, (x0, y0, w, h) in faces_of(cid):
            for y in range(h):
                for x in range(w):
                    p = s.get(x0 + x, y0 + y)
                    if p[3]:
                        s.put(x0 + x, y0 + y, SULAM, p[0] + 10, 210)
        save(s, "busana/kubus/" + cid)
    save(paint_cloth("celemek", KAIN2, None, tone_d=10), "busana/kubus/celemek")
    save(paint_fixed_cube("cincin_kasaya", GOLD, "gelang"), "busana/kubus/cincin_kasaya")
    bun = paint_cloth("buntalan", KAIN2, None, tone_d=-4)
    for f, (x0, y0, w, h) in faces_of("buntalan"):
        if f == "belakang":
            for y in range(h):
                bun.put(x0 + w // 2, y0 + y, SAMAK, 110)
            for x in range(w):
                bun.put(x0 + x, y0 + h // 2, SAMAK, 104)
    save(bun, "busana/kubus/buntalan")
    save(paint_cloth("rumbai_iblis", KAIN1, KAIN2, 2, ragged=True), "busana/kubus/rumbai_iblis")
    save(paint_fixed_cube("kerah_bulu", 0x8A6A48, "bulu"), "busana/kubus/kerah_bulu")
    save(paint_fixed_cube("caping_bambu", 0xC8A860, "anyam"), "busana/kubus/caping_bambu")
    save(paint_fixed_cube("caping_puncak", 0xB89850, "anyam"), "busana/kubus/caping_puncak")


def generate():
    for sname, sex in (("l", 0), ("p", 1)):
        for wname, slim in WIDTHS:
            save(underwear(sex, slim), "busana/dalam_%s_%s" % (sname, wname))
    for gaya in GAYA:
        for wname, slim in WIDTHS:
            main = drape(paint_style(gaya, slim), gaya, slim)
            save(main, "busana/%s_%s" % (gaya, wname))
            save(trim_level(main, 1, slim), "busana/%s_tepi1_%s" % (gaya, wname))
            save(trim_level(main, 2, slim), "busana/%s_tepi2_%s" % (gaya, wname))
    for kind in ("bangau", "harimau", "koin", "harimau_hitam"):
        save(buzi(kind), "busana/buzi_" + kind)
    for kind in ("bepergian", "kerja", "zirah", "tidur"):
        for wname, slim in WIDTHS:
            save(extra(kind, slim), "busana/tambahan_%s_%s" % (kind, wname))
    for kind in ("kerudung", "tudung", "ikat_putih", "ikat_kuning"):
        save(head_cloth(kind), "busana/kepala_" + kind)
    for kind in ("kain", "bot", "jerami", "rahib"):
        save(footwear(kind), "busana/kaki_" + kind)
    for wname, slim in WIDTHS:
        save(bandage("lengan", slim), "luka/perban_lengan_" + wname)
    save(bandage("kaki", False), "luka/perban_kaki")
    gen_cubes()
    return WRITTEN
