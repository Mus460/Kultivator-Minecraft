"""Generates every Sosok part sheet (DESIGN 40.3.2-40.3.7, module 40K).

    python -I tools/gen_sosok_bagian.py

Writes ``assets/kultivator/textures/sosok/bagian/<group>/<id>.png`` (+ ``_tetap.png`` for fixed colours). Every sheet is
drawn by a pixel function (folds, seams, collars, strands), never noise. Groups:

* ``kulit/``        body bases ``dasar_<l|p>_<usia 0-3>_<lebar|ramping>`` (slot KULIT/KULIT_RONA)
* ``wajah/``        face shapes, noses, packed eye / brow / mouth state sheets (128 x 8, state i at x 8i), facial hair, marks
* ``rambut/``       17 painted hair styles (``<id>_dasar``, ``<id>_topi``, ``<id>_bertopi``) and ``rambut/kubus/<cube>``
* ``busana/``       underwear, 22 garment styles per arm width (``<gaya>_<w>``, ``<gaya>_tepi1_<w>``, ``<gaya>_tepi2_<w>``),
                    extras ``tambahan_<id>_<w>``, footwear ``kaki_<id>``, cube sheets ``busana/kubus/<cube>``
* ``luka/``         bandages
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sosok_lib import (KULIT, KULIT_RONA, RAMBUT, RAMBUT_KILAP, MATA_PUTIH, IRIS, PUPIL, ALIS, BIBIR, KAIN1, KAIN2,  # noqa: E402
                       TEPI, LOGAM, GIOK, SULAM, SAMAK, F_SEAM, F_NOISE, F_SHINE, Lembar, Badan, FacePainter,
                       UV_RAMBUT, UV_BUSANA, cube_face, cube_sheet_size, face_rect, part_dims, cek_uv)

WIDTHS = (("lebar", False), ("ramping", True))
SEXES = (("l", 0), ("p", 1))
WRITTEN = []


def save(sheet, rel):
    sheet.save(rel)
    WRITTEN.append(rel)


# ====================================================================== body base (kulit/)


def paint_skin_base(sex, age, slim):
    s = Lembar(64, 64)
    b = Badan(s, slim)
    old = age == 3
    child = age == 0
    fem = sex == 1
    # ---- head: every face skin, lit from above
    for face, tone in (("atas", 136), ("bawah", 104), ("kanan", 120), ("depan", 128), ("kiri", 120), ("belakang", 118)):
        b.face("kepala", 0, face).fill(KULIT, tone)
    for side in ("kanan", "kiri"):
        f = b.face("kepala", 0, side)
        # ear near the middle of the side, a little back (x 0 = back edge on the right side, front edge on the left)
        ex = (2, 3) if side == "kanan" else (4, 5)
        for x in ex:
            for y in (3, 4, 5):
                f.put(x, y, KULIT, 112)
        f.put(ex[0] if side == "kanan" else ex[1], 4, KULIT_RONA, 96)
        f.put(ex[1] if side == "kanan" else ex[0], 3, KULIT, 124)
        # cheek / jaw turning away from the light
        for y in (6, 7):
            for x in range(8):
                f.shade(x, y, -6)
        # temple shadow at the front edge
        fx = 7 if side == "kanan" else 0
        f.shade(fx, 2, -4)
    front = b.face("kepala", 0, "depan")
    for x in range(8):
        front.shade(x, 7, -8)           # under the jaw
    front.shade(0, 4, -5)
    front.shade(7, 4, -5)
    # eye sockets: a soft shadow under the brow line so the face reads at 8 px
    for x in (1, 2, 5, 6):
        front.shade(x, 3, -4)
    if old:
        for x in (2, 3, 4, 5):
            front.put(x, 1, KULIT, 112)  # forehead line
        front.shade(0, 5, -10)
        front.shade(7, 5, -10)
    back = b.face("kepala", 0, "belakang")
    for x in range(8):
        back.shade(x, 7, -6)
    # ---- body
    for face, tone in (("atas", 130), ("bawah", 104), ("kanan", 114), ("depan", 128), ("kiri", 114), ("belakang", 120)):
        b.face("badan", 0, face).fill(KULIT, tone)
    bf = b.face("badan", 0, "depan")
    bf.put(3, 0, KULIT, 104)
    bf.put(4, 0, KULIT, 104)            # throat shadow
    for x in (1, 2, 5, 6):
        bf.put(x, 1, KULIT, 116)        # collarbones
    if child:
        for x in range(2, 6):
            bf.put(x, 7, KULIT, 134)    # round tummy
        bf.put(3, 8, KULIT_RONA, 108)
    elif fem:
        for x in (1, 2, 5, 6):
            bf.put(x, 4, KULIT, 112)    # chest shadow
        for y in range(6, 10):
            bf.shade(0, y, -12)
            bf.shade(7, y, -12)         # waist
        bf.put(3, 8, KULIT_RONA, 104)
    else:
        for x in (1, 2, 5, 6):
            bf.put(x, 4, KULIT, 114)    # chest line
        for y in (6, 7, 8):
            bf.put(3, y, KULIT, 120)
            bf.put(4, y, KULIT, 122)
        bf.put(4, 9, KULIT_RONA, 104)
    if old:
        for x in (2, 5):
            bf.put(x, 2, KULIT, 108)
    bk = b.face("badan", 0, "belakang")
    for y in range(2, 11):
        bk.put(3, y, KULIT, 114)
        bk.put(4, y, KULIT, 116)        # spine
    for x in (1, 2, 5, 6):
        bk.put(x, 2, KULIT, 112)        # shoulder blades
    # ---- arms
    for arm in ("lengan_kanan", "lengan_kiri"):
        outer = "kanan" if arm == "lengan_kanan" else "kiri"
        inner = "kiri" if arm == "lengan_kanan" else "kanan"
        for face, tone in (("atas", 132), ("bawah", 112), (outer, 116), ("depan", 126), (inner, 122), ("belakang", 118)):
            b.face(arm, 0, face).fill(KULIT, tone)
        w = 3 if slim else 4
        fr = b.face(arm, 0, "depan")
        for x in range(w):
            fr.put(x, 10, KULIT_RONA, 116)      # knuckles
            fr.put(x, 11, KULIT, 120)
        fr.put(w // 2, 5, KULIT, 120)            # elbow crease
        bkf = b.face(arm, 0, "belakang")
        for x in range(w):
            bkf.put(x, 11, KULIT_RONA, 112)
        if old:
            for x in range(w):
                fr.put(x, 9, KULIT, 110)
        b.face(arm, 0, "bawah").fill(KULIT_RONA, 110)
    # ---- legs
    for leg in ("kaki_kanan", "kaki_kiri"):
        outer = "kanan" if leg == "kaki_kanan" else "kiri"
        inner = "kiri" if leg == "kaki_kanan" else "kanan"
        for face, tone in (("atas", 120), ("bawah", 100), (outer, 116), ("depan", 126), (inner, 114), ("belakang", 118)):
            b.face(leg, 0, face).fill(KULIT, tone)
        fr = b.face(leg, 0, "depan")
        for x in range(4):
            fr.put(x, 5, KULIT_RONA, 118)       # knee
            fr.put(x, 11, KULIT, 116)
        fr.put(0, 11, KULIT_RONA, 108)
        fr.put(3, 11, KULIT_RONA, 108)          # toes
        fr.put(1, 10, KULIT, 122)
        fr.put(2, 10, KULIT, 122)
    return s


def gen_kulit():
    for sname, sex in SEXES:
        for age in range(4):
            for wname, slim in WIDTHS:
                save(paint_skin_base(sex, age, slim), "kulit/dasar_%s_%d_%s" % (sname, age, wname))


# ====================================================================== face (wajah/)

FACE_X, FACE_Y = 8, 8          # head front on the 64x64 sheet
LSIDE = face_rect("kepala", 0, "kanan")     # wearer's right side (0, 8)
RSIDE = face_rect("kepala", 0, "kiri")      # wearer's left side (16, 8)


def face_shape(sex, shape):
    """bentuk_<l|p>_<0 lonjong, 1 persegi, 2 hati, 3 bulat>: jaw, cheek bones, chin on the head front and sides."""
    s = Lembar(64, 64)
    f = FacePainter(s, (FACE_X, FACE_Y, 8, 8))
    rs = FacePainter(s, LSIDE)
    ls = FacePainter(s, RSIDE)
    soft = 112 if sex else 104
    if shape == 0:      # oval: soft shadow at the jaw corners
        for y in (6, 7):
            f.put(0, y, KULIT, soft + 6)
            f.put(7, y, KULIT, soft + 6)
        f.put(1, 7, KULIT, soft + 10)
        f.put(6, 7, KULIT, soft + 10)
    elif shape == 1:    # square: firm jaw
        for y in (6, 7):
            for x in (0, 1, 6, 7):
                f.put(x, y, KULIT, 96 if not sex else 106)
            rs.put(7, y, KULIT, 98)
            rs.put(6, y, KULIT, 104)
            ls.put(0, y, KULIT, 98)
            ls.put(1, y, KULIT, 104)
        f.put(0, 5, KULIT, 112)
        f.put(7, 5, KULIT, 112)
    elif shape == 2:    # heart: pointed chin
        for y in (6, 7):
            for x in (0, 1, 6, 7):
                f.put(x, y, KULIT, 96 if not sex else 104)
        f.put(2, 7, KULIT, 108)
        f.put(5, 7, KULIT, 108)
        f.put(3, 7, KULIT, 104 + 14)
        f.put(4, 7, KULIT, 104 + 14)
        f.put(1, 4, KULIT, 136)
        f.put(6, 4, KULIT, 136)         # high cheek bones catch light
    else:               # round: full cheeks with blush
        f.put(1, 5, KULIT_RONA, 132, 200)
        f.put(6, 5, KULIT_RONA, 132, 200)
        f.put(0, 7, KULIT, 116)
        f.put(7, 7, KULIT, 116)
        f.put(1, 6, KULIT, 134)
        f.put(6, 6, KULIT, 134)
    # cheek bone highlight for every shape, softer on men
    f.put(1, 4, KULIT, f.get(1, 4)[0] if f.get(1, 4)[3] else 132 if sex else 130)
    f.put(6, 4, KULIT, f.get(6, 4)[0] if f.get(6, 4)[3] else 132 if sex else 130)
    return s


def nose(kind):
    """hidung_<l|p|tua>: 8x8 head-front overlay."""
    s = Lembar(8, 8)
    if kind == "l":
        s.put(3, 5, KULIT, 104)
        s.put(4, 5, KULIT, 108)
        s.put(3, 4, KULIT, 136)
    elif kind == "p":
        s.put(4, 5, KULIT, 110)
        s.put(3, 4, KULIT, 138)
    else:
        s.put(3, 5, KULIT, 96)
        s.put(4, 5, KULIT, 100)
        s.put(3, 4, KULIT, 128)
    return s


EYE_NAMES = ["phoenix", "almond", "bulat", "sipit", "tajam", "lembut", "tua", "anak", "ganda"]
EYE_STATES = ["buka", "setengah", "tutup", "lirik_kiri", "lirik_kanan", "lirik_atas", "lirik_bawah", "marah", "sedih",
              "kaget", "senang", "mengantuk", "tutup_tenang", "kilat", "sakit", "malu"]


def _eye(s, ox, shape, state, side, lashes):
    """One eye on an 8x8 frame at x offset ox. side 0 = wearer's right eye (fx 1-2), 1 = left eye (fx 5-6)."""
    outer = 1 if side == 0 else 6       # outer pixel column
    inner = 2 if side == 0 else 5
    corner = 0 if side == 0 else 7      # just outside the eye

    def p(x, y, slot, tone=128, a=255, flags=0):
        s.put(ox + x, y, slot, tone, a, flags)

    lid = lambda x: p(x, 3, PUPIL, 72)
    skin_lid = lambda x, t=118: p(x, 3, KULIT, t)
    big_iris = shape in ("anak", "ganda")
    # iris column for an open eye (pupils toward the nose = looking at the viewer)
    iris_x = inner
    white_x = outer
    if state == "lirik_kiri":       # glance to the wearer's left (viewer's right, larger fx)
        iris_x, white_x = (2, 1) if side == 0 else (6, 5)
    elif state == "lirik_kanan":    # glance to the wearer's right (smaller fx)
        iris_x, white_x = (1, 2) if side == 0 else (5, 6)
    iris_tone = 220 if state == "kilat" else 128
    iris_flags = F_SHINE if state == "kilat" else 0

    def open_eye(upper="lid"):
        if big_iris:
            for x in (1, 2) if side == 0 else (5, 6):
                for y in (3, 4):
                    p(x, y, IRIS, iris_tone - (10 if y == 4 else 0), 255, iris_flags)
            if shape == "ganda":
                # two pupils: (fx1, fy3) and (fx2, fy4) on the right eye, mirrored on the left
                p(1 if side == 0 else 6, 3, PUPIL, 128)
                p(2 if side == 0 else 5, 4, PUPIL, 128)
                p(corner, 3, PUPIL, 80)
            else:
                p(1 if side == 0 else 6, 3, MATA_PUTIH, 176)   # the child's shine
                p(2 if side == 0 else 5, 4, PUPIL, 110)
            return
        if shape == "bulat" or upper == "putih":
            p(white_x, 3, MATA_PUTIH, 136)
            p(iris_x, 3, IRIS, iris_tone + 6, 255, iris_flags)
        elif shape == "sipit":
            skin_lid(outer, 122)
            skin_lid(inner, 122)
        elif upper == "lid":
            lid(outer)
            lid(inner)
            if shape == "tajam":
                p(inner, 3, PUPIL, 60)
            if shape == "phoenix":
                p(corner, 3, PUPIL, 86)         # the lifted outer corner
        if shape == "sipit":
            p(white_x, 4, PUPIL, 80)
            p(iris_x, 4, IRIS, iris_tone - 30, 255, iris_flags)
        else:
            p(white_x, 4, MATA_PUTIH, 128)
            p(iris_x, 4, IRIS, iris_tone - (28 if shape == "tajam" else 0), 255, iris_flags)
        if shape == "lembut":
            p(outer, 5, KULIT_RONA, 112)
        if shape == "tua":
            p(corner, 3, KULIT, 96)
        if lashes and shape in ("phoenix", "almond", "bulat", "lembut"):
            p(corner, 3, PUPIL, 80)

    if state in ("buka", "lirik_kiri", "lirik_kanan", "kilat"):
        open_eye()
    elif state == "setengah":
        skin_lid(outer, 116)
        skin_lid(inner, 116)
        p(outer, 4, PUPIL, 72)
        p(inner, 4, IRIS, 96)
    elif state in ("tutup", "tutup_tenang"):
        skin_lid(outer, 122 if state == "tutup" else 126)
        skin_lid(inner, 122 if state == "tutup" else 126)
        p(outer, 4, PUPIL, 76 if state == "tutup" else 92)
        p(inner, 4, PUPIL, 76 if state == "tutup" else 92)
        if lashes:
            p(corner, 4, PUPIL, 84)
    elif state == "lirik_atas":
        p(outer, 3, MATA_PUTIH, 136)
        p(inner, 3, IRIS, 134)
        p(outer, 4, MATA_PUTIH, 120)
        p(inner, 4, MATA_PUTIH, 120)
    elif state in ("lirik_bawah", "malu"):
        skin_lid(outer, 118)
        skin_lid(inner, 118)
        p(outer, 4, PUPIL, 74)
        p(inner, 4, IRIS, 120)
        if state == "malu":
            p(outer, 5, KULIT_RONA, 150, 210)
            p(corner if side == 1 else corner, 5, KULIT_RONA, 140, 150)
    elif state == "marah":
        p(outer, 3, KULIT, 112)
        p(inner, 3, PUPIL, 58)
        p(white_x, 4, MATA_PUTIH, 124)
        p(iris_x, 4, IRIS, 110)
    elif state == "sedih":
        p(outer, 3, PUPIL, 70)
        p(inner, 3, KULIT, 114)
        p(white_x, 4, MATA_PUTIH, 126)
        p(iris_x, 4, IRIS, 120)
        p(outer, 5, MATA_PUTIH, 150, 120)       # a wet glint under the eye
    elif state == "kaget":
        p(outer, 3, MATA_PUTIH, 140)
        p(inner, 3, MATA_PUTIH, 140)
        p(white_x, 4, MATA_PUTIH, 136)
        p(iris_x, 4, IRIS, 128)
    elif state == "senang":
        p(outer, 3, PUPIL, 82)
        p(inner, 3, PUPIL, 82)
        p(outer, 4, KULIT_RONA, 124)
        p(inner, 4, KULIT, 126)
        p(corner, 4, PUPIL, 96, 180)
    elif state == "mengantuk":
        skin_lid(outer, 112)
        skin_lid(inner, 112)
        p(outer, 4, PUPIL, 84)
        p(inner, 4, IRIS, 92)
    elif state == "sakit":
        p(outer, 3, PUPIL, 70)
        p(inner, 3, PUPIL, 64)
        p(outer, 4, KULIT, 110)
        p(inner, 4, PUPIL, 88)


def eye_sheet(shape, lashes):
    s = Lembar(128, 8)
    for i, state in enumerate(EYE_STATES):
        for side in (0, 1):
            _eye(s, 8 * i, shape, state, side, lashes)
    return s


BROW_NAMES = ["pedang", "daun_willow", "tebal", "tipis", "berkerut", "putih_panjang"]


def brow_sheet(shape):
    s = Lembar(128, 8)
    for i, state in enumerate(EYE_STATES):
        ox = 8 * i
        for side in (0, 1):
            outer = 1 if side == 0 else 6
            inner = 2 if side == 0 else 5
            corner = 0 if side == 0 else 7

            def p(x, y, tone=128, a=255):
                s.put(ox + x, y, ALIS, tone, a)

            if state == "kaget":
                p(outer, 1, 120)
                p(inner, 1, 120)                # lifted out of the strip: only the base row 1 shows them
                continue
            if state in ("marah", "sakit"):
                p(outer, 2, 116)
                p(inner, 2, 90)
                if state == "marah":
                    p(inner, 3, 96, 160)         # knitted down onto the lid
                continue
            if state == "sedih":
                p(inner, 2, 120)
                p(outer, 2, 140, 170)
                p(inner, 1, 116, 140)
                continue
            if state in ("tutup_tenang", "mengantuk"):
                p(outer, 2, 136, 200)
                p(inner, 2, 136, 200)
                continue
            if shape == "pedang":
                p(outer, 1, 100)
                p(outer, 2, 116)
                p(inner, 2, 104)
            elif shape == "daun_willow":
                p(outer, 2, 120, 220)
                p(inner, 2, 112, 230)
                p(corner, 2, 136, 120)
            elif shape == "tebal":
                for x in (outer, inner):
                    p(x, 1, 104)
                    p(x, 2, 98)
            elif shape == "tipis":
                p(outer, 2, 130, 190)
                p(inner, 2, 124, 200)
            elif shape == "berkerut":
                p(outer, 2, 110)
                p(inner, 2, 96)
                p(inner, 3, 104, 150)
            else:   # putih_panjang: white long brows that droop past the eye
                p(corner, 2, 150)
                p(outer, 2, 150)
                p(inner, 2, 140)
                p(corner, 3, 140, 200)
    return s


MOUTH_NAMES = ["datar", "senyum_tipis", "tegas", "penuh", "kecil", "tua"]
MOUTH_STATES = ["diam", "bicara_A", "bicara_O", "bicara_E", "senyum", "tertawa", "cemberut", "marah", "kaget", "sedih",
                "tidur", "meniup", "makan", "batuk_darah", "menyeringai", "menggigit_bibir"]


def mouth_sheet(shape):
    s = Lembar(128, 8)
    for i, state in enumerate(MOUTH_STATES):
        ox = 8 * i

        def p(x, y, slot, tone=128, a=255):
            s.put(ox + x, y, slot, tone, a)

        lip = 128 if shape == "penuh" else 104
        if state == "diam":
            if shape == "datar":
                p(3, 6, BIBIR, 100)
                p(4, 6, BIBIR, 104)
            elif shape == "senyum_tipis":
                p(3, 6, BIBIR, 102)
                p(4, 6, BIBIR, 104)
                p(2, 5, BIBIR, 112, 200)
                p(5, 5, BIBIR, 112, 200)
            elif shape == "tegas":
                for x in (2, 3, 4, 5):
                    p(x, 6, BIBIR, 84, 210)
            elif shape == "penuh":
                p(3, 6, BIBIR, 124)
                p(4, 6, BIBIR, 128)
                p(3, 7, BIBIR, 150)
                p(4, 7, BIBIR, 150)
            elif shape == "kecil":
                p(4, 6, BIBIR, 112)
                p(3, 6, BIBIR, 132, 140)
            else:   # tua
                p(3, 6, BIBIR, 98)
                p(4, 6, BIBIR, 100)
                p(2, 5, KULIT, 98)
                p(5, 5, KULIT, 98)
                p(2, 6, KULIT, 102)
                p(5, 6, KULIT, 102)
        elif state == "bicara_A":
            p(3, 6, BIBIR, 46)
            p(4, 6, BIBIR, 50)
            p(3, 7, BIBIR, lip + 6)
            p(4, 7, BIBIR, lip + 6)
        elif state == "bicara_O":
            p(3, 5, BIBIR, lip + 10)
            p(4, 5, BIBIR, lip + 10)
            p(3, 6, BIBIR, 44)
            p(4, 6, BIBIR, 48)
            p(3, 7, BIBIR, lip + 12)
            p(4, 7, BIBIR, lip + 12)
        elif state == "bicara_E":
            for x in (2, 3, 4, 5):
                p(x, 6, BIBIR, 64 if x in (3, 4) else 88)
        elif state == "senyum":
            p(3, 6, BIBIR, lip)
            p(4, 6, BIBIR, lip + 2)
            p(2, 5, BIBIR, lip + 6)
            p(5, 5, BIBIR, lip + 6)
            p(1, 5, KULIT_RONA, 132, 150)
            p(6, 5, KULIT_RONA, 132, 150)
        elif state == "tertawa":
            p(2, 5, BIBIR, lip + 4)
            p(5, 5, BIBIR, lip + 4)
            p(2, 6, BIBIR, 50)
            p(3, 6, MATA_PUTIH, 140)
            p(4, 6, MATA_PUTIH, 140)
            p(5, 6, BIBIR, 50)
            p(3, 7, BIBIR, lip + 8)
            p(4, 7, BIBIR, lip + 8)
        elif state == "cemberut":
            p(3, 6, BIBIR, lip - 6)
            p(4, 6, BIBIR, lip - 6)
            p(2, 7, BIBIR, lip - 4)
            p(5, 7, BIBIR, lip - 4)
        elif state == "marah":
            p(2, 6, BIBIR, 56)
            p(3, 6, MATA_PUTIH, 132)
            p(4, 6, MATA_PUTIH, 132)
            p(5, 6, BIBIR, 56)
            p(3, 7, BIBIR, lip)
            p(4, 7, BIBIR, lip)
        elif state == "kaget":
            p(3, 5, BIBIR, lip + 8)
            p(4, 5, BIBIR, lip + 8)
            p(3, 6, BIBIR, 40)
            p(4, 6, BIBIR, 40)
            p(3, 7, BIBIR, 54)
            p(4, 7, BIBIR, 54)
        elif state == "sedih":
            p(3, 6, BIBIR, lip - 2)
            p(4, 6, BIBIR, lip - 2)
            p(2, 7, BIBIR, lip + 6, 200)
            p(5, 7, BIBIR, lip + 6, 200)
        elif state == "tidur":
            p(3, 6, BIBIR, 58)
            p(4, 6, BIBIR, lip)
        elif state == "meniup":
            p(3, 6, BIBIR, lip + 18)
            p(4, 6, BIBIR, 50)
            p(5, 6, BIBIR, lip + 18)
            p(4, 5, BIBIR, lip + 14)
            p(4, 7, BIBIR, lip + 14)
        elif state == "makan":
            p(3, 6, BIBIR, lip - 4)
            p(4, 6, BIBIR, lip)
            p(5, 6, KULIT, 146)
            p(6, 6, KULIT, 140)
            p(5, 5, KULIT, 140)
        elif state == "batuk_darah":
            p(3, 6, BIBIR, 46)
            p(4, 6, BIBIR, 50)
            p(3, 7, BIBIR, lip + 6)
            s.fixed(ox + 5, 7, 0x8A1010)
            s.fixed(ox + 5, 6, 0xA81818, 200)
        elif state == "menyeringai":
            p(3, 6, BIBIR, lip)
            p(4, 6, BIBIR, lip)
            p(5, 5, BIBIR, lip + 4)
            p(5, 6, BIBIR, 60)
        elif state == "menggigit_bibir":
            p(3, 6, MATA_PUTIH, 138)
            p(4, 6, MATA_PUTIH, 134)
            p(3, 7, BIBIR, lip + 10)
            p(4, 7, BIBIR, lip + 10)
    return s


def facial_hair(kind):
    """rambut_<kind> on the 64x64 sheet: mustaches and beards painted on the face (cubes add volume)."""
    s = Lembar(64, 64)
    f = FacePainter(s, (FACE_X, FACE_Y, 8, 8))
    rs = FacePainter(s, LSIDE)
    ls = FacePainter(s, RSIDE)
    bottom = FacePainter(s, face_rect("kepala", 0, "bawah"))
    if kind in ("kumis_tipis", "jenggot_panjang", "kumis_naga", "jenggot_penuh"):
        for x in (2, 3, 4, 5):
            f.put(x, 5, RAMBUT, 100 if x in (3, 4) else 112, 230)
    if kind == "kumis_naga":
        f.put(1, 6, RAMBUT, 108)
        f.put(6, 6, RAMBUT, 108)
    if kind == "jenggot_kambing":
        f.put(3, 7, RAMBUT, 104)
        f.put(4, 7, RAMBUT, 110)
        f.put(3, 6, RAMBUT, 120, 140)
        f.put(4, 6, RAMBUT, 120, 140)
        for x in (3, 4):
            bottom.put(x, 0, RAMBUT, 100)
    if kind in ("jenggot_penuh", "jenggot_panjang"):
        for x in range(1, 7):
            f.put(x, 7, RAMBUT, 104 + (6 if x % 2 else 0))
        for y in (5, 6, 7):
            f.put(0, y, RAMBUT, 108)
            f.put(7, y, RAMBUT, 108)
        f.put(1, 6, RAMBUT, 114)
        f.put(6, 6, RAMBUT, 114)
        for y in (5, 6, 7):
            for x in (5, 6, 7):
                rs.put(x, y, RAMBUT, 100 + 4 * (x - 5), 255 if y > 5 else 170)
            for x in (0, 1, 2):
                ls.put(x, y, RAMBUT, 108 - 4 * x, 255 if y > 5 else 170)
        for x in range(8):
            for y in range(3):
                bottom.put(x, y, RAMBUT, 96, 255)
    return s


def mark(kind):
    """tanda_<kind>: face marks (8x8 head-front overlays) or 64x64 body marks."""
    if kind in ("huadian", "huadian_emas", "tanda_dao", "bekas_luka", "tahi_lalat", "coreng_jelaga"):
        s = Lembar(8, 8)
        if kind == "huadian":
            s.fixed(3, 1, 0xC0303A)
            s.fixed(4, 1, 0xC0303A)
            s.fixed(4, 0, 0xD84850, 220)
            s.fixed(3, 2, 0x9A2030, 160)
        elif kind == "huadian_emas":
            s.fixed(3, 1, 0xE0B84A)
            s.fixed(4, 1, 0xC9A24A)
            s.fixed(4, 0, 0xF2D27A, 220)
        elif kind == "tanda_dao":
            s.put(3, 0, SULAM, 170, 230, F_SHINE)
            s.put(4, 1, SULAM, 170, 230, F_SHINE)
            s.put(4, 0, SULAM, 140, 200)
            s.put(3, 1, SULAM, 140, 200)
        elif kind == "bekas_luka":
            s.put(6, 4, KULIT_RONA, 150, 140)
            s.put(6, 5, KULIT_RONA, 150)
            s.put(7, 6, KULIT_RONA, 150)
        elif kind == "tahi_lalat":
            s.fixed(6, 5, 0x3A2418)
        else:
            s.fixed(1, 5, 0x4A4440, 170)
            s.fixed(2, 6, 0x4A4440, 120)
            s.fixed(1, 6, 0x3A3632, 90)
        return s
    s = Lembar(64, 64)
    if kind == "bekas_cakar":
        ls = FacePainter(s, RSIDE)
        f = FacePainter(s, (FACE_X, FACE_Y, 8, 8))
        for i, x in enumerate((0, 2, 4)):
            for y in (3, 4, 5):
                ls.put(x, y + (i % 2), KULIT_RONA, 156, 230)
        f.put(7, 4, KULIT_RONA, 156, 200)
    elif kind == "tulang_tertinggi":
        b = Badan(s, False)
        bf = b.face("badan", 0, "depan")
        bf.fixed(3, 0, 0xE8C860)
        bf.fixed(4, 0, 0xE8C860)
        bf.fixed(3, 1, 0xC9A24A)
        bf.fixed(4, 1, 0xD8B850)
    elif kind == "nadi_sesat":
        for side in (LSIDE, RSIDE):
            p = FacePainter(s, side)
            for x in range(1, 7):
                if x % 2:
                    p.fixed(x, 7, 0x8A2A2A, 120)
                else:
                    p.fixed(x, 6, 0x7A1E24, 90)
        b = Badan(s, False)
        bf = b.face("badan", 0, "depan")
        bf.fixed(1, 0, 0x8A2A2A, 90)
        bf.fixed(6, 0, 0x8A2A2A, 90)
    return s


def ink_fingers(slim):
    s = Lembar(64, 64)
    b = Badan(s, slim)
    w = 3 if slim else 4
    for arm in ("lengan_kanan",):
        f = b.face(arm, 0, "depan")
        for x in range(w):
            f.fixed(x, 11, 0x1E1A18, 200)
        f.fixed(0, 10, 0x2A2420, 120)
        b.face(arm, 0, "bawah").fill(KULIT, 100)
        bot = b.face(arm, 0, "bawah")
        for x in range(w):
            bot.fixed(x, 0, 0x1E1A18, 200)
    return s


def gen_wajah():
    for sname, sex in SEXES:
        for shape in range(4):
            save(face_shape(sex, shape), "wajah/bentuk_%s_%d" % (sname, shape))
    for k in ("l", "p", "tua"):
        save(nose(k), "wajah/hidung_" + k)
    for shape in EYE_NAMES:
        save(eye_sheet(shape, False), "wajah/mata_" + shape)
        if shape in ("phoenix", "almond", "bulat", "lembut"):
            save(eye_sheet(shape, True), "wajah/mata_%s_p" % shape)
    for shape in BROW_NAMES:
        save(brow_sheet(shape), "wajah/alis_" + shape)
    for shape in MOUTH_NAMES:
        save(mouth_sheet(shape), "wajah/mulut_" + shape)
    for kind in ("kumis_tipis", "jenggot_kambing", "jenggot_penuh", "jenggot_panjang", "kumis_naga"):
        save(facial_hair(kind), "wajah/rambut_" + kind)
    for kind in ("huadian", "huadian_emas", "tanda_dao", "bekas_luka", "tahi_lalat", "coreng_jelaga", "bekas_cakar",
                 "tulang_tertinggi", "nadi_sesat"):
        save(mark(kind), "wajah/tanda_" + kind)
    for wname, slim in WIDTHS:
        save(ink_fingers(slim), "wajah/tanda_tinta_jari_" + wname)


if __name__ == "__main__":
    problems = cek_uv()
    if problems:
        print("UV problems:", problems)
        sys.exit(1)
    gen_kulit()
    gen_wajah()
    import gen_sosok_bagian_rambut as rb       # noqa: E402
    import gen_sosok_bagian_busana as bs       # noqa: E402
    WRITTEN.extend(rb.generate())
    WRITTEN.extend(bs.generate())
    print("sheets:", len(WRITTEN))
