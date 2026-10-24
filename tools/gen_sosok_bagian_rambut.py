"""Hair part sheets of the Sosok composer (DESIGN 40.3.5-40.3.6, module 40K). Called by gen_sosok_bagian.py.

Painted styles (``rambut/<id>_dasar`` on the base head, ``rambut/<id>_topi`` on the hat layer, ``rambut/<id>_bertopi``
= the hat-layer sheet flattened for a hat that covers the head) and cube sheets ``rambut/kubus/<cube>[_<material>]``.
Hair uses slots RAMBUT / RAMBUT_KILAP / ALIS; ornaments of wood, silver, jade and gold are fixed colours shaded through
the same tone ramp (so they keep their material whatever the palette), cloth ties and ribbons use KAIN2 / TEPI.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sosok_lib import (KULIT, RAMBUT, RAMBUT_KILAP, ALIS, KAIN1, KAIN2, TEPI, Lembar, FacePainter, UV_RAMBUT,  # noqa: E402
                       cube_face, cube_sheet_size, face_rect, tangga, F_SHINE, F_NOISE)

WRITTEN = []
HAIR_STYLES = ["L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "A1", "A2"]

MATERIAL = {
    "kayu": 0x7A5434,
    "perak": 0xC8CCD4,
    "giok": 0x5FA88C,
    "emas": 0xD8B04A,
    "perunggu": 0x8C6A3A,
}


def save(sheet, rel):
    sheet.save(rel)
    WRITTEN.append(rel)


def faces(sheet, layer):
    """Head face painters of one layer (0 base, 1 hat)."""
    return {f: FacePainter(sheet, face_rect("kepala", layer, f)) for f in
            ("atas", "bawah", "kanan", "depan", "kiri", "belakang")}


def strand_tone(x, y, base=128):
    """Hair strands: alternate columns darker, a soft vertical gradient."""
    t = base - y * 2
    if (x * 3 + y // 2) % 5 == 0:
        t -= 9          # a darker strand now and then
    elif (x * 3 + y // 2) % 5 == 2:
        t += 6          # and a lighter one
    return t


def paint_top(f, part="tengah", toward="belakang", cover=255):
    """Head top (y 0 = back, y 7 = front; x 0 = wearer's right). part: tengah / samping / sisir (combed to a bun)."""
    for y in range(8):
        for x in range(8):
            if part == "sisir":
                t = 120 + (8 if (x + (y // 2)) % 3 == 0 else 0) - abs(x - 3.5) * 3
            else:
                t = strand_tone(y, x, 126)
            f.put(x, y, RAMBUT, int(t), cover)
    if part == "tengah":
        for y in range(8):
            f.put(3, y, RAMBUT, 84, cover)
            f.put(4, y, RAMBUT, 96, cover)
    elif part == "samping":
        for y in range(8):
            f.put(2, y, RAMBUT, 84, cover)
    elif part == "sisir":
        for y in range(2, 7):
            f.put(3, y, RAMBUT, 98, cover)
            f.put(4, y, RAMBUT, 106, cover)
    # shine across the crown front (slot 3)
    for x in (2, 3, 4, 5):
        if f.get(x, 6)[3]:
            f.put(x, 6, RAMBUT_KILAP, 120 if x in (3, 4) else 108, cover)


def paint_back(f, rows, cover=255):
    for y in range(rows):
        for x in range(8):
            f.put(x, y, RAMBUT, strand_tone(x, y, 124) - (8 if y == rows - 1 else 0), cover)
    shine_ring(f, 2, rows, cover)


def shine_ring(f, y, rows, cover=255):
    """The broken shine band across the back of the head (slot 3), skipping every third strand."""
    if rows > y + 1:
        for x in range(8):
            if x % 3 != 1:
                f.put(x, y, RAMBUT_KILAP, 190 if x in (3, 4) else 172, cover)


def paint_sides(fs, rows, behind_ear=0, cover=255):
    """Sides: rows 0..rows-1 covered; behind_ear extra rows down to 7 on the back half (x 0-2 right side, 5-7 left)."""
    for side in ("kanan", "kiri"):
        f = fs[side]
        for y in range(8):
            for x in range(8):
                back = x <= 2 if side == "kanan" else x >= 5
                if y < rows or (back and y < rows + behind_ear):
                    f.put(x, y, RAMBUT, strand_tone(x, y, 120), cover)
        shine_ring(f, 1, rows, cover)
        # sideburn just in front of the ear
        fx = 6 if side == "kanan" else 1
        if rows >= 3:
            f.put(fx, rows, RAMBUT, 104, 200)


def hairline(f, kind):
    """Front hairline on the base head front."""
    if kind == "lurus":
        for x in range(8):
            f.put(x, 0, RAMBUT, 112)
        f.put(0, 1, RAMBUT, 108)
        f.put(7, 1, RAMBUT, 108)
    elif kind == "lengkung":
        for x in range(8):
            f.put(x, 0, RAMBUT, 112)
        for x in (0, 1, 6, 7):
            f.put(x, 1, RAMBUT, 110)
        f.put(0, 2, RAMBUT, 104)
        f.put(7, 2, RAMBUT, 104)
    elif kind == "mundur":
        for x in (0, 1, 6, 7):
            f.put(x, 0, RAMBUT, 110)
        f.put(0, 1, RAMBUT, 104)
        f.put(7, 1, RAMBUT, 104)
    elif kind == "belah":
        for x in range(8):
            f.put(x, 0, RAMBUT, 108 if x not in (3, 4) else 96)
        for x in (0, 1, 6, 7):
            f.put(x, 1, RAMBUT, 110)


def style_sheets(style):
    """Returns (dasar, topi) sheets of one painted hair style."""
    d = Lembar(64, 64)
    t = Lembar(64, 64)
    bf = faces(d, 0)
    hf = faces(t, 1)
    if style == "L5":   # shaved monk: shining scalp, ordination dots (Buddhist), stubble shadow
        bf["atas"].fill(KULIT, 122)
        for x in (2, 3, 4):
            bf["atas"].put(x, 5, KULIT, 146, 255, F_SHINE)
        bf["atas"].put(3, 4, KULIT, 140)
        for face in ("kanan", "kiri", "belakang"):
            for y in range(6):
                for x in range(8):
                    bf[face].put(x, y, RAMBUT, 120, 46)
        return d, t
    if style in ("A1", "A2"):   # child: thin, almost shaved hair
        paint_top(bf["atas"], "tengah" if style == "A1" else "samping", cover=170)
        paint_back(bf["belakang"], 6, 170)
        paint_sides(bf, 2, 2, 170)
        if style == "A2":
            for x in (3, 4):
                bf["depan"].put(x, 0, RAMBUT, 116)
                bf["depan"].put(x, 1, RAMBUT, 108)
                hf["depan"].put(x, 0, RAMBUT, 124)
        return d, t
    old_female = style == "P7"
    part = {"L1": "sisir", "L2": "sisir", "L3": "sisir", "L4": "tengah", "L6": "sisir", "L7": "samping", "L8": "samping",
            "P1": "sisir", "P2": "tengah", "P3": "samping", "P4": "sisir", "P5": "tengah", "P6": "tengah",
            "P7": "sisir"}[style]
    paint_top(bf["atas"], part)
    back_rows = 8 if style not in ("L8", "L7") else 6
    paint_back(bf["belakang"], back_rows)
    side_rows = {"L1": 3, "L2": 3, "L3": 3, "L4": 4, "L6": 3, "L7": 3, "L8": 3, "P1": 3, "P2": 3, "P3": 4, "P4": 3,
                 "P5": 3, "P6": 5, "P7": 3}[style]
    paint_sides(bf, side_rows, 3 if style not in ("L7", "L8") else 1)
    hairline(bf["depan"], {"L6": "mundur", "P1": "lengkung", "P7": "lengkung", "L4": "belah", "P6": "belah",
                           "P2": "belah", "P5": "belah"}.get(style, "lurus"))
    # ---- hat layer: volume, fringe, locks
    top = hf["atas"]
    for y in range(8):
        for x in range(8):
            if part == "tengah" and x in (3, 4):
                continue
            if style in ("L1", "L2", "L6", "P1", "P7", "L3", "P4"):
                # combed back: strands converge toward the bun / tail, keep the crown edges only
                if 2 <= x <= 5 and 1 <= y <= 6:
                    continue
            top.put(x, y, RAMBUT, strand_tone(y, x, 132), 255, F_SHINE if y >= 6 and 2 <= x <= 5 else 0)
    front = hf["depan"]
    if style in ("L1", "L6", "P1", "P7"):
        for x in range(8):
            front.put(x, 0, RAMBUT, 122)
    elif style == "L2":
        for x in range(8):
            front.put(x, 0, RAMBUT, 122)
        front.put(0, 1, RAMBUT, 116)
        front.put(7, 1, RAMBUT, 116)
    elif style in ("L3", "P4"):
        for x in range(8):
            front.put(x, 0, RAMBUT, 124)
        for x in (1, 6):                # two loose strands
            for y in (1, 2):
                front.put(x, y, RAMBUT, 118 - 6 * y)
        if style == "P4":
            for x in (0, 7):
                for y in range(1, 5):
                    front.put(x, y, RAMBUT, 116 - 3 * y)
    elif style in ("L4", "P6"):
        for x in range(8):
            if x not in (3, 4):
                front.put(x, 0, RAMBUT, 122)
        for x in (0, 1, 6, 7):
            for y in range(1, 4):
                if x in (0, 7) or y < 3:
                    front.put(x, y, RAMBUT, 118 - 4 * y)
    elif style == "L7":
        # work headcloth wrapped round the head, rows 0-2 (KAIN2), knot at the back
        for face in ("depan", "kanan", "kiri", "belakang"):
            for x in range(8):
                for y in range(3):
                    hf[face].put(x, y, KAIN2, 128 - 8 * (y == 2) - (6 if (x + y) % 3 == 0 else 0), 255, F_NOISE)
        for x in range(8):
            hf["atas"].put(x, 7, KAIN2, 122)
            hf["atas"].put(x, 0, KAIN2, 116)
        hf["belakang"].put(3, 3, KAIN2, 110)
        hf["belakang"].put(4, 3, KAIN2, 116)
        hf["belakang"].put(3, 4, KAIN2, 104)
    elif style == "L8":
        for x in range(8):
            front.put(x, 0, RAMBUT, 120)
            if x % 2 == 0:
                front.put(x, 1, RAMBUT, 112)
            if x in (2, 5):
                front.put(x, 2, RAMBUT, 104)
        for face in ("kanan", "kiri"):
            for x in range(8):
                hf[face].put(x, 0, RAMBUT, 118)
                if x % 2:
                    hf[face].put(x, 1, RAMBUT, 110)
                    hf[face].put(x, 2, RAMBUT, 102)
        for x in range(8):
            hf["belakang"].put(x, 5, RAMBUT, 108)
            if x % 2 == 0:
                hf["belakang"].put(x, 6, RAMBUT, 100)
    elif style == "P2":
        for x in range(8):
            if x not in (3, 4):
                front.put(x, 0, RAMBUT, 122)
        for x in (1, 2, 5, 6):
            front.put(x, 1, RAMBUT, 114)
    elif style == "P3":
        for x in range(8):
            if x != 2:
                front.put(x, 0, RAMBUT, 122)
        for x, rows in ((3, 2), (4, 3), (6, 2)):   # three thin fringe strands
            for y in range(1, rows + 1):
                front.put(x, y, RAMBUT, 116 - 6 * y, 220)
    elif style == "P5":
        for x in range(8):
            if x not in (3, 4):
                front.put(x, 0, RAMBUT, 122)
        # the braid painted down the back of the hat layer
        back = hf["belakang"]
        for y in range(1, 8):
            off = y % 2
            back.put(3, y, RAMBUT, 120 - 14 * off)
            back.put(4, y, RAMBUT, 106 + 14 * off)
        back.put(3, 7, KAIN2, 128)
        back.put(4, 7, KAIN2, 120)
    # long hair down the back for loose / half-up styles
    if style in ("L2", "L4", "P3", "P6"):
        back = hf["belakang"]
        for y in range(8):
            for x in range(8):
                back.put(x, y, RAMBUT, strand_tone(x, y, 120) - (6 if y > 5 else 0))
        for side in ("kanan", "kiri"):
            for y in range(1, 8):
                for x in range(8):
                    behind = x <= 2 if side == "kanan" else x >= 5
                    if behind or y < 2:
                        hf[side].put(x, y, RAMBUT, strand_tone(x, y, 116))
    if old_female:
        for x in range(8):
            hf["belakang"].put(x, 6, RAMBUT, 104)
            hf["belakang"].put(x, 7, RAMBUT, 98)
    return d, t


def flatten_for_hat(topi):
    """The hat-layer sheet under a covering hat: no crown volume, upper rows of the sides gone."""
    out = Lembar(64, 64)
    out.blit(topi)
    x0, y0, w, h = face_rect("kepala", 1, "atas")
    for y in range(h):
        for x in range(w):
            out.clear(x0 + x, y0 + y)
    for face in ("depan", "kanan", "kiri", "belakang"):
        x0, y0, w, h = face_rect("kepala", 1, face)
        for y in range(3):
            for x in range(w):
                out.clear(x0 + x, y0 + y)
    return out


# ====================================================================== cube sheets (40.3.6)


def cube_sheet(entry):
    w, h = cube_sheet_size(entry)
    return Lembar(w, h)


def each_face(entry):
    box = entry[2]
    for f in ("atas", "bawah", "kanan", "depan", "kiri", "belakang"):
        yield f, cube_face(0, 0, box[3], box[4], box[5], f)


def paint_hair_cube(cid, slot=RAMBUT, base=124, ends=True, wrap_band=None, ring=True):
    """A hair volume cube: strands down every side face, lit top, darker bottom, a broken shine band (slot 3) a quarter
    down the sides (not on beards and brows); optional tie band rows (slot, rows)."""
    entry = UV_RAMBUT[cid]
    s = cube_sheet(entry)
    for f, (x0, y0, w, h) in each_face(entry):
        for y in range(h):
            for x in range(w):
                if f == "atas":
                    tone = base + 10 - (x % 2) * 6
                    flags = F_SHINE if (x + y) % 3 == 0 else 0
                elif f == "bawah":
                    tone, flags = base - 22, 0
                else:
                    tone = base - (8 if (x + (y // 3)) % 2 else 0) - y * (14 // max(1, h))
                    flags = 0
                    if f in ("kanan", "kiri"):
                        tone -= 6
                    if f == "belakang":
                        tone -= 4
                    if ends and y == h - 1 and h > 3:
                        tone -= 10
                if ring and slot == RAMBUT and h >= 4 and f not in ("atas", "bawah") and y == h // 4 and x % 3 != 2:
                    s.put(x0 + x, y0 + y, RAMBUT_KILAP, 184 - (14 if f in ("kanan", "kiri") else 0), 255, 0)
                    continue
                s.put(x0 + x, y0 + y, slot, tone, 255, flags)
                if wrap_band is not None and f not in ("atas", "bawah") and y in wrap_band[1]:
                    s.put(x0 + x, y0 + y, wrap_band[0], 124 - 6 * (y == wrap_band[1][-1]))
    return s


def paint_material_cube(cid, material, style="polos"):
    """An ornament cube in a fixed material (wood, silver, jade, gold), shaded through the ramp."""
    entry = UV_RAMBUT[cid]
    s = cube_sheet(entry)
    rgb = MATERIAL[material]
    for f, (x0, y0, w, h) in each_face(entry):
        for y in range(h):
            for x in range(w):
                tone = {"atas": 150, "bawah": 92, "kanan": 112, "kiri": 112, "depan": 128, "belakang": 118}[f]
                if style == "berukir" and f in ("depan", "belakang", "kanan", "kiri") and (x + y) % 3 == 0:
                    tone -= 18
                if style == "berukir" and f in ("depan",) and y == 0:
                    tone += 16
                s.fixed(x0 + x, y0 + y, tangga(rgb, tone))
    return s


def paint_crown():
    """mahkota_teratai: gold lotus crown with jade petals."""
    entry = UV_RAMBUT["mahkota_teratai"]
    s = cube_sheet(entry)
    gold, jade = MATERIAL["emas"], MATERIAL["giok"]
    for f, (x0, y0, w, h) in each_face(entry):
        for y in range(h):
            for x in range(w):
                if f == "atas":
                    petal = (x + y) % 2 == 0 and 0 < x < w - 1 and 0 < y < h - 1
                    s.fixed(x0 + x, y0 + y, tangga(jade if petal else gold, 150 if petal else 136))
                elif f == "bawah":
                    s.fixed(x0 + x, y0 + y, tangga(gold, 90))
                else:
                    if y == 0 and x % 2 == 0:
                        s.fixed(x0 + x, y0 + y, tangga(jade, 140))      # petal tips
                    elif y == 0:
                        s.clear(x0 + x, y0 + y)
                    else:
                        s.fixed(x0 + x, y0 + y, tangga(gold, 126 - (8 if x % 2 else 0)))
    return s


def paint_cloth_cube(cid, slot, base=128, stripes=False):
    entry = UV_RAMBUT[cid]
    s = cube_sheet(entry)
    for f, (x0, y0, w, h) in each_face(entry):
        for y in range(h):
            for x in range(w):
                tone = base - (10 if f in ("kanan", "kiri") else 0) - (20 if f == "bawah" else 0)
                if stripes and y % 3 == 2:
                    tone -= 14
                if y == h - 1 and h > 3:
                    tone -= 12
                s.put(x0 + x, y0 + y, slot, tone, 255, F_NOISE)
    return s


def paint_flower(kind):
    entry = UV_RAMBUT["bunga_rambut"]
    s = cube_sheet(entry)
    petal, heart = {"plum": (0xC0303A, 0xF2D27A), "peony": (0xE8A0B8, 0xF8E0A0), "teratai": (0xF4F0F0, 0xE8C860)}[kind]
    for f, (x0, y0, w, h) in each_face(entry):
        for y in range(h):
            for x in range(w):
                if f == "depan" and w >= 2 and h >= 2:
                    centre = (x, y) in ((0, 0), (1, 1)) if kind != "teratai" else y == 1
                    s.fixed(x0 + x, y0 + y, tangga(heart if centre and (x + y) % 2 == 0 else petal, 140))
                else:
                    s.fixed(x0 + x, y0 + y, tangga(petal, 118 if f != "bawah" else 96))
    return s


def paint_beads(material):
    """manik_buyao: three tiny dangling beads with a gold thread (one sheet, the cube is drawn three times)."""
    entry = UV_RAMBUT["manik_buyao"]
    s = cube_sheet(entry)
    rgb = MATERIAL[material]
    for f, (x0, y0, w, h) in each_face(entry):
        for y in range(h):
            for x in range(w):
                if f in ("atas", "bawah"):
                    s.fixed(x0 + x, y0 + y, tangga(rgb, 120))
                elif y < h - 1:
                    s.fixed(x0 + x, y0 + y, tangga(MATERIAL["emas"], 118 + 12 * (y % 2)))
                else:
                    s.fixed(x0 + x, y0 + y, tangga(rgb, 150))
    return s


def gen_cubes():
    for cid in ("sanggul", "rambut_atas", "rambut_bawah", "rambut_bawah_panjang", "ekor_kuda_2", "cepol",
                "sanggul_tinggi", "sanggul_rendah", "kepang_2", "jambul_anak", "poni_samping"):
        save(paint_hair_cube(cid), "rambut/kubus/" + cid)
    save(paint_hair_cube("ekor_kuda_1", wrap_band=(KAIN2, (0,))), "rambut/kubus/ekor_kuda_1")
    save(paint_hair_cube("kepang_1", wrap_band=(KAIN2, (0,))), "rambut/kubus/kepang_1")
    # braids: woven look
    kp = paint_hair_cube("kepang_2")
    entry = UV_RAMBUT["kepang_2"]
    for f, (x0, y0, w, h) in each_face(entry):
        if f in ("atas", "bawah"):
            continue
        for y in range(h):
            for x in range(w):
                if (x + y) % 2:
                    kp.shade(x0 + x, y0 + y, -14)
        for x in range(w):
            kp.put(x0 + x, y0 + h - 1, TEPI, 120)
    save(kp, "rambut/kubus/kepang_2")
    for cid in ("jenggot_pendek", "jenggot_panjang", "kumis_naga"):
        save(paint_hair_cube(cid, RAMBUT, 118, ring=False), "rambut/kubus/" + cid)
    save(paint_hair_cube("alis_panjang", ALIS, 140), "rambut/kubus/alis_panjang")
    # ornaments
    save(paint_cloth_cube("guan_rendah", KAIN2), "rambut/kubus/guan_rendah_kain")
    for m in ("kayu", "perak", "giok", "emas"):
        save(paint_material_cube("guan_rendah", m, "berukir"), "rambut/kubus/guan_rendah_" + m)
        save(paint_material_cube("guan_tinggi", m, "berukir"), "rambut/kubus/guan_tinggi_" + m)
        save(paint_material_cube("tusuk_guan", m), "rambut/kubus/tusuk_guan_" + m)
    for m in ("perak", "emas"):
        save(paint_material_cube("tusuk_buyao", m, "berukir"), "rambut/kubus/tusuk_buyao_" + m)
    for m in ("perak", "emas", "giok"):
        save(paint_beads(m), "rambut/kubus/manik_buyao_" + m)
    save(paint_crown(), "rambut/kubus/mahkota_teratai")
    save(paint_cloth_cube("pita_rambut", TEPI, 132, stripes=True), "rambut/kubus/pita_rambut")
    save(paint_cloth_cube("ikat_dahi_ekor", TEPI, 126), "rambut/kubus/ikat_dahi_ekor")
    for kind in ("plum", "peony", "teratai"):
        save(paint_flower(kind), "rambut/kubus/bunga_rambut_" + kind)


def ordination_dots():
    """rambut/titik_tahbisan: six incense ordination dots on a Buddhist monk's shaved crown (2 columns x 3 rows)."""
    s = Lembar(64, 64)
    top = FacePainter(s, face_rect("kepala", 0, "atas"))
    for x in (2, 5):
        for y in (2, 4, 6):
            top.fixed(x, y, 0x8A5A3A)
    return s


def generate():
    for style in HAIR_STYLES:
        d, t = style_sheets(style)
        save(d, "rambut/%s_dasar" % style)
        if not t.empty():
            save(t, "rambut/%s_topi" % style)
            save(flatten_for_hat(t), "rambut/%s_bertopi" % style)
    save(ordination_dots(), "rambut/titik_tahbisan")
    gen_cubes()
    return WRITTEN
