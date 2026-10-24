"""Look resolver of the Sosok composer (DESIGN 40.3.4-40.4, module 40K): Sosok + Gaya + inputs -> a resolved look
(16 palette colours, the ordered layer list, the visible cubes). Mirrored line by line by ``RupaResolver.java``; any
change here must be made there too (``cek_sosok.py`` + the scratch harness compare both on hundreds of seeds).

Inputs (``Masukan``): look key, fallback key, realm (-1 unknown, 0-8), Dao (-1, 0-7), sect rank (-1, 0-5),
physique (0-9), uniform colours (robe, trim, embroidery, cut) or None, hat covers the head, life path bit, wound,
special outfit.

RNG: one SplitMix64 per draw group (``rng_for(benih, group)``), so adding a draw in one group never changes another.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sosok_lib import (RESEP, UV_RAMBUT, UV_BUSANA, RAMBUT_KUBUS, BUSANA_KUBUS, rng_for, fnv32, rgb_hex,  # noqa: E402
                       cube_sheet_size, footprint, M64)

LAKI, PEREMPUAN = 0, 1
ANAK, MUDA, DEWASA, TUA = 0, 1, 2, 3
AGE_LETTER = "AMDT"
CUT_PANJANG, CUT_PENDEK, CUT_TEMPUR = 0, 1, 2

EYES = ["phoenix", "almond", "bulat", "sipit", "tajam", "lembut", "tua", "anak", "ganda"]
LASHES = ("phoenix", "almond", "bulat", "lembut")
SHAPES = ["lonjong", "persegi", "hati", "bulat"]

DEF_MATA_L = [["phoenix", 30], ["almond", 35], ["sipit", 10], ["tajam", 15], ["lembut", 10]]
DEF_MATA_P = [["phoenix", 30], ["almond", 30], ["bulat", 20], ["lembut", 20]]
DEF_MATA_P_MUDA = [["phoenix", 20], ["almond", 25], ["bulat", 35], ["lembut", 20]]
DEF_ALIS_L = [["pedang", 45], ["tebal", 25], ["tipis", 20], ["berkerut", 10]]
DEF_ALIS_P = [["daun_willow", 70], ["tipis", 30]]
DEF_MULUT_L = [["datar", 40], ["tegas", 30], ["senyum_tipis", 30]]
DEF_MULUT_P = [["penuh", 45], ["senyum_tipis", 30], ["kecil", 25]]
DEF_BENTUK_L = [["lonjong", 30], ["persegi", 35], ["hati", 10], ["bulat", 25]]
DEF_BENTUK_P = [["lonjong", 35], ["persegi", 5], ["hati", 35], ["bulat", 25]]
DEF_RW_D = [["kumis_tipis", 15], ["jenggot_kambing", 12], ["jenggot_penuh", 13], ["jenggot_panjang", 5]]
DEF_RW_T = [["kumis_tipis", 10], ["jenggot_kambing", 15], ["jenggot_penuh", 20], ["jenggot_panjang", 25]]
DEF_RAMBUT = {
    (LAKI, ANAK): ["A1", "A2", "L8"], (PEREMPUAN, ANAK): ["A1", "P2"],
    (LAKI, MUDA): ["L1", "L2", "L3", "L4", "L7"], (PEREMPUAN, MUDA): ["P2", "P3", "P4", "P5", "P6"],
    (LAKI, DEWASA): ["L1", "L2", "L4", "L7"], (PEREMPUAN, DEWASA): ["P1", "P3", "P4", "P5", "P6"],
    (LAKI, TUA): ["L6"], (PEREMPUAN, TUA): ["P7"],
}
HIASAN_TIER = ["kain", "guan_kayu", "guan_perak", "guan_giok", "guan_emas"]
HIDE_UNDER_HAT = ("sanggul", "sanggul_tinggi", "guan_rendah", "guan_tinggi", "mahkota_teratai", "tusuk_guan",
                  "tusuk_buyao", "manik_buyao", "bunga_rambut", "jambul_anak", "cepol", "pita_rambut")

# variant bits for the model (SosokRupa.varian)
V_KEPANG_DEPAN, V_BAHU_KIRI, V_SELENDANG_MELAYANG, V_ROK_LEBAR, V_SANGGUL_KECIL, V_JAMBUL_SATU, V_PUDAR, \
    V_LENGAN_TELANJANG = (1 << i for i in range(8))

_RESEP = None


def resep():
    global _RESEP
    if _RESEP is None:
        with open(RESEP, encoding="utf-8") as f:
            _RESEP = json.load(f)
    return _RESEP


def masukan(kunci, cadangan=None, realm=-1, dao=-1, pangkat=-1, fisik=0, seragam=None, topi_menutup=False,
            jalan=0, luka=0, busana_khusus=0, sekte=None):
    return {"kunci": kunci, "cadangan": cadangan, "realm": realm, "dao": dao, "pangkat": pangkat, "fisik": fisik,
            "seragam": seragam, "topi_menutup": topi_menutup, "jalan": jalan, "luka": luka,
            "busana_khusus": busana_khusus, "sekte": sekte}


def lengan_ramping(kelamin, usia, postur):
    return kelamin == PEREMPUAN or usia == ANAK or (usia == MUDA and postur <= 1)


def scale_rgb(rgb, r, g, b):
    """rgb * (r, g, b) / 100 per channel, integer, clamped."""
    return (min(255, ((rgb >> 16) & 255) * r // 100) << 16) | (min(255, ((rgb >> 8) & 255) * g // 100) << 8) | \
        min(255, (rgb & 255) * b // 100)


def add_rgb(rgb, r, g, b):
    return (min(255, ((rgb >> 16) & 255) + r) << 16) | (min(255, ((rgb >> 8) & 255) + g) << 8) | min(255, (rgb & 255) + b)


def mix_rgb(a, b, t100):
    """a + (b - a) * t / 100 per channel (integer)."""
    out = 0
    for sh in (16, 8, 0):
        ca, cb = (a >> sh) & 255, (b >> sh) & 255
        out |= (ca + (cb - ca) * t100 // 100 if cb >= ca else ca - (ca - cb) * t100 // 100) << sh
    return out


def resolve(sosok, gaya, m, paksa=None):
    """sosok = (kelamin, usia, postur, benih); gaya = Gaya fields dict or None; m = masukan dict. Returns the look.

    paksa (previews only, not in Java): {"rambut": style, "busana": style} forces a part."""
    paksa = paksa or {}
    R = resep()
    kel, usia, postur, benih = sosok
    fem = kel == PEREMPUAN
    rows = R["peran"]
    row = rows.get("peran." + m["kunci"]) if m["kunci"] else None
    if row is None and m["cadangan"]:
        row = rows.get("peran." + m["cadangan"])
    if row is None:
        row = rows["peran.kelompok.SIPIL"]
    jalan_bit = m["jalan"]
    jalan_ids = [None, "pedang", "alkemis", "formasi", "pertapa", "petualang", "pemburu", "pedagang", "cendekia",
                 "penjaga", "iblis", "tempa", "tabib", "petani_roh", "musisi", "bangsawan"]
    jrow = R["jalan"].get("jalan." + jalan_ids[jalan_bit]) if 0 < jalan_bit < len(jalan_ids) else None
    realm, dao, fisik = m["realm"], m["dao"], m["fisik"]
    pangkat = m["pangkat"] if m["pangkat"] >= 0 else row["pangkat"]
    seragam = m["seragam"] if (row["murid"] or "sekte" in row["kain"] or "sekte_pudar" in row["kain"]) else None
    ramping = lengan_ramping(kel, usia, postur)
    W = "ramping" if ramping else "lebar"
    S = "p" if fem else "l"
    busana_khusus = m["busana_khusus"]
    warna = R["warna"]

    def named(tok):
        if tok.startswith("#"):
            return rgb_hex(tok)
        if tok in warna:
            return rgb_hex(warna[tok])
        if tok in R["logam"]:
            return rgb_hex(R["logam"][tok])
        return None

    # ------------------------------------------------------------ skin
    rk = rng_for(benih, "kulit")
    skin_w = [k["bobot"] for k in R["kulit"]]
    tema = row["tema"]
    if tema == "oasis":
        skin_w = [w * 2 if 3 <= i <= 6 else w for i, w in enumerate(skin_w)]
    elif tema == "gunung":
        skin_w = [w * 3 // 2 if i <= 2 else w for i, w in enumerate(skin_w)]
    skin_id = rk.pick([[i, w] for i, w in enumerate(skin_w)])
    gelap = max(row["kulit_gelap"], 40 if tema == "pelabuhan" else 0, jrow["kulit_gelap"] if jrow else 0)
    if gelap > 0 and rk.chance(gelap):
        skin_id = min(6, skin_id + 1)
    if row["kulit"] == "arwah":
        skin_id = 7
    skin = rgb_hex(R["kulit"][skin_id]["rgb"])
    if fisik == 4:
        skin = scale_rgb(skin, 104, 102, 94)

    # ------------------------------------------------------------ hair colour
    rh = rng_for(benih, "rambut_warna")
    hw = [[k, v["bobot"] * (2 if tema == "oasis" and k == "cokelat_tua" else 1)] for k, v in R["rambut_warna"].items()]
    hair_name = rh.pick(hw)
    if usia == TUA:
        hair_name = "abu" if rh.chance(40) else "putih"
    forced = (jrow["rambut_warna"] if jrow and jrow["rambut_warna"] else "") or row["rambut_warna"]
    if row["rambut_merah"] and rh.chance(row["rambut_merah"]):
        forced = forced or "merah_tua"
    if dao == 1 and realm >= 4:
        forced = forced or "merah_tua"
    if dao == 2 and realm >= 4 and not forced:
        forced = "perak"
    kilap_emas = False
    if forced == "hitam_biru_emas":
        forced, kilap_emas = "hitam_biru", True
    if forced:
        hair_name = forced
    hair = rgb_hex(R["rambut_warna"][hair_name]["rgb"])

    # ------------------------------------------------------------ iris
    ri = rng_for(benih, "iris")
    iris = rgb_hex(ri.pick([[i["rgb"], i["bobot"]] for i in R["iris"]]))
    pupil = 0x15121A
    sclera = 0xF4F1EA if usia == ANAK else (0xE2DACB if usia == TUA else 0xEDE8E0)
    lips_f = 0xB85A5E
    if 0 <= dao < 8 and R["dao"][dao]["iris"] is not None and realm >= R["dao"][dao]["iris"][1]:
        iris = rgb_hex(R["dao"][dao]["iris"][0])
    if realm >= 8:
        iris = 0xF2C94C
    if fisik == 1:
        iris, lips_f = 0x6A8A3A, 0x6A3A7A
    elif fisik == 2:
        iris = 0xC8CCD8
    elif fisik == 3:
        iris = 0xA0702A
    elif fisik == 4:
        iris = 0xD8A83A
    elif fisik == 5 and realm >= 3:
        iris = 0x3A1A4A
    elif fisik == 7:
        iris, pupil = 0xC9A24A, 0x2A0E05
    elif fisik == 9:
        iris = 0xA8A8A8
    if row["iris"]:
        iris = rgb_hex(row["iris"])
    if jrow and jrow["iris"]:
        iris = rgb_hex(jrow["iris"])

    # ------------------------------------------------------------ face parts
    rb = rng_for(benih, "bentuk")
    if usia == ANAK:
        bentuk = "bulat"
    else:
        bentuk = rb.pick(row["bentuk"] or (DEF_BENTUK_P if fem else DEF_BENTUK_L))
    rm = rng_for(benih, "mata")
    if fisik == 7:
        mata = "ganda"
    elif usia == ANAK:
        mata = "anak"
    else:
        lst = (jrow["mata"] if jrow and jrow["mata"] else None) or row["mata"]
        if not lst:
            lst = [["tua", 1]] if usia == TUA else (DEF_MATA_P_MUDA if fem and usia == MUDA else DEF_MATA_P) if fem else DEF_MATA_L
        mata = rm.pick(lst)
        if mata == "anak":
            mata = "almond"
    ra = rng_for(benih, "alis")
    lst = (jrow["alis_p"] if jrow and fem and jrow["alis_p"] else None) or (row["alis_p"] if fem else row["alis_l"])
    if not lst:
        lst = DEF_ALIS_P if fem else DEF_ALIS_L
    alis = ra.pick(lst)
    if mata == "tajam" and alis in ("daun_willow", "tipis") and not fem:
        alis = "berkerut"
    rmu = rng_for(benih, "mulut")
    if usia == ANAK:
        mulut = "kecil"
    elif usia == TUA and not row["mulut"]:
        mulut = "tua"
    else:
        mulut = rmu.pick(row["mulut"] or (DEF_MULUT_P if fem else DEF_MULUT_L))

    # facial hair (men, adult or old)
    rw_name = None
    rr = rng_for(benih, "rambut_wajah")
    if not fem and usia >= DEWASA:
        if row["rw"]:
            items = [[k, v] for k, v in row["rw"].items()]
            rest = 100 - sum(v for _, v in items)
            if rest > 0:
                items.append(["tidak", rest])
        else:
            items = (DEF_RW_T if usia == TUA else DEF_RW_D) + [["tidak", 55]]
        rw_name = rr.pick(items)
        if rw_name == "tidak":
            rw_name = None

    # marks
    rt = rng_for(benih, "tanda")
    marks = []
    tanda = dict(row["tanda"])
    if fem:
        tanda.update(row["tanda_p"])
    if jrow:
        tanda.update(jrow["tanda"])
        if fem:
            tanda.update(jrow["tanda_p"])
    for name in ("bekas_luka", "bekas_cakar", "tahi_lalat", "coreng_jelaga", "huadian", "tinta_jari", "tanda_dao"):
        pct = tanda.get(name, 8 if name == "tahi_lalat" else 0)
        if pct > 0 and rt.chance(pct):
            marks.append(name)
    if (row["murid"] or row["tanda_ranah"]) and realm >= 4 and "tanda_dao" not in marks:
        marks.append("tanda_dao")
    if usia == ANAK:
        marks = [k for k in marks if k in ("tahi_lalat",)]

    # ------------------------------------------------------------ hair style
    rg = rng_for(benih, "gaya_rambut")
    letter = AGE_LETTER[usia]
    opts = (jrow["rambut_p"] if fem else jrow["rambut_l"]) if jrow else []
    opts = [h for h in opts if letter in R["gaya_rambut"][h]["u"]
            and ("P" in R["gaya_rambut"][h]["k"] if fem else "L" in R["gaya_rambut"][h]["k"])]
    if not opts:
        opts = [h for h in (row["rambut_p"] if fem else row["rambut_l"]) if letter in R["gaya_rambut"][h]["u"]
                and ("P" in R["gaya_rambut"][h]["k"] if fem else "L" in R["gaya_rambut"][h]["k"])]
    if not opts:
        opts = DEF_RAMBUT[(kel, usia)]
    if busana_khusus == 5 and usia in (MUDA, DEWASA):
        opts = ["P6"] if fem else ["L4"]          # sleeping: hair let down
    rambut = rg.pick([[h, 1] for h in opts])
    if "rambut" in paksa:
        rambut = paksa["rambut"]

    # ------------------------------------------------------------ garment style
    rs = rng_for(benih, "gaya")
    gl = row["gaya_p"] if fem else row["gaya_l"]
    busana = rs.pick(gl)
    rok_pendek_p = busana == "HANFU_P_PENDEK"
    if rok_pendek_p:
        busana = "HANFU_P"
    if usia == ANAK:
        if row["gaya_anak"]:
            busana = rs.pick(row["gaya_anak"])
        elif busana not in ("COMPANG", "KAFAN", "ANAK"):
            busana = "ANAK"
    if seragam is not None:
        cut = seragam[3]
        busana = ("HANFU_P" if fem else "HANFU_L") if cut == CUT_PANJANG else ("PENDEK" if cut == CUT_PENDEK else "TEMPUR")
    elif jrow and jrow["gaya_tanpa_seragam"] and row["murid"]:
        busana = jrow["gaya_tanpa_seragam"]
    if jrow and jrow["gaya_inti_emas"] and realm >= 3 and seragam is None:
        busana = jrow["gaya_inti_emas"]
    if row["tao_dao"] and seragam is None and dao in (6, 7):
        busana = "TAO"
    if row["murid"] and realm >= 7 and seragam is None:
        busana = "ABADI"
    if "busana" in paksa:
        busana = paksa["busana"]
    gb = R["gaya_busana"][busana]

    # ------------------------------------------------------------ colours
    rc = rng_for(benih, "kain")
    clan_key = m["sekte"] or m["kunci"] or ""
    toks = list(row["kain"])

    def kain_of(tok, slot_index, fallback):
        if tok == "-":
            return fallback
        if tok in ("dao", "dao_kain", "dao_gelap"):
            if 0 <= dao < 8:
                c = rgb_hex(R["dao"][dao]["kain"])
            elif realm == 0:
                c = rgb_hex(warna["kasar"])
            elif realm > 0:
                c = scale_rgb(rgb_hex(R["ranah"][realm]), 60, 60, 60)
            else:
                c = rgb_hex(R["klan"][fnv32(clan_key) % len(R["klan"])])
            return scale_rgb(c, 80, 80, 80) if tok == "dao_gelap" else c
        if tok in ("sekte", "sekte_pudar"):
            if seragam is not None:
                c = seragam[0] if slot_index != 2 else seragam[1]
            elif 0 <= dao < 8:
                c = rgb_hex(R["dao"][dao]["kain"]) if slot_index != 2 else rgb_hex(R["dao"][dao]["sulam"])
            else:
                c = rgb_hex(R["klan"][fnv32(clan_key) % len(R["klan"])])
                if slot_index == 2:
                    c = add_rgb(c, 60, 60, 60)
            return scale_rgb(c, 70, 70, 70) if tok == "sekte_pudar" else c
        if tok in ("klan", "kelompok"):
            return rgb_hex(R["klan"][fnv32(clan_key) % len(R["klan"])])
        if tok == "cerah":
            return rgb_hex(R["cerah"][rc.below(len(R["cerah"]))])
        if tok == "pangkat":
            return rgb_hex(R["tepi_pangkat"][max(0, min(5, pangkat))])
        c = named(tok)
        return c if c is not None else fallback

    default_kain = [rgb_hex(t) if t.startswith("#") else named(t) for t in gb.get("kain", ["#8A7A60", "#EDE6D6", "#6A4A30", "perunggu"])]
    kain1 = kain_of(toks[0], 0, default_kain[0])
    kain2 = kain_of(toks[1], 1, default_kain[1])
    tepi = kain_of(toks[2], 2, scale_rgb(kain1, 70, 70, 70))
    logam = kain_of(toks[3], 3, rgb_hex(R["logam"]["perunggu"]))
    if row["murid"] and seragam is None and toks[2] == "pangkat":
        tepi = rgb_hex(R["tepi_pangkat"][max(0, min(5, pangkat))])
    if jrow and jrow["kain_tepi"]:
        tepi = rgb_hex(jrow["kain_tepi"])
    if jrow and jrow["kain2"]:
        kain2 = named(jrow["kain2"])
    if row["murid"] and realm >= 3:
        logam = rgb_hex(R["logam"]["emas_kusam"])
    # individual variation of the main cloth (uniform and sect colours stay exact)
    bright = rc.below(13) - 6
    tint = rc.below(9) - 4
    if seragam is None and toks[0] not in ("sekte", "sekte_pudar"):
        kain1 = scale_rgb(kain1, 100 + bright + tint, 100 + bright, 100 + bright - tint)
    sulam = tepi
    if 0 <= dao < 8:
        sulam = rgb_hex(R["dao"][dao]["sulam"])
    if seragam is not None:
        sulam = seragam[2]
    # special outfits that recolour
    if busana_khusus == 2:      # mourning: undyed white
        kain1, kain2, tepi = 0xE6E2D6, 0xEDE9DE, 0xCFC9B8

    kilap = add_rgb(scale_rgb(hair, 125, 125, 125), 5, 8, 15)
    if kilap_emas:
        kilap = 0xD8C080
    elif realm >= 6 and 0 <= dao < 8:
        kilap = mix_rgb(kilap, rgb_hex(R["dao"][dao]["sulam"]), 40)
    alis_rgb = hair if usia == TUA else scale_rgb(hair, 80, 80, 80)
    if alis == "putih_panjang":
        alis_rgb = 0xE6E3DC
    bibir = lips_f if fem else scale_rgb(skin, 92, 75, 74)
    if fem and fisik == 1:
        bibir = 0x6A3A7A
    palet = [skin, scale_rgb(skin, 100, 86, 84), hair, kilap, sclera, iris, pupil, alis_rgb, bibir,
             kain1, kain2, tepi, logam, 0x5FA88C, sulam, 0x5A3A22]

    # ------------------------------------------------------------ extras
    tambahan = list(row["tambahan"])
    rx = rng_for(benih, "tambahan")
    for name, pct in row["tambahan_acak"].items():
        if rx.chance(pct):
            tambahan.append(name)
    if jrow:
        tambahan.extend(jrow["tambahan"])
    if busana_khusus == 1:
        tambahan.append("bepergian")
    elif busana_khusus == 3:
        tambahan.append("pesta")
    elif busana_khusus == 4:
        tambahan.extend(["kerja", "celemek"])
    elif busana_khusus == 6:
        tambahan.append("zirah")
    elif busana_khusus == 7:
        tambahan.append("penyamaran")

    # ------------------------------------------------------------ cubes
    hair_cubes = list(R["gaya_rambut"][rambut]["kubus"])
    body_cubes = []
    for c in gb["kubus"]:
        body_cubes.extend(R["kelompok_kubus"].get(c, [c]))
    if rok_pendek_p:
        body_cubes = [c.replace("rok_panjang", "rok_pendek") for c in body_cubes]
    varian = 0
    if gb.get("lebar"):
        varian |= V_ROK_LEBAR
    if R["gaya_rambut"][rambut].get("kecil"):
        varian |= V_SANGGUL_KECIL
    if R["gaya_rambut"][rambut].get("satu"):
        varian |= V_JAMBUL_SATU
    if rambut == "P5" and (tema == "oasis" or (benih >> 3) & 1):
        varian |= V_KEPANG_DEPAN
    sheets_extra = []
    head_cloth = row["kepala"] or gb.get("kepala", "")
    hide = set()
    tepi_naik = 0
    for t in tambahan:
        if t == "sabuk_kubus":
            body_cubes.append("sabuk")
            continue
        if t == "kerah_tinggi_l":
            if not fem:
                body_cubes.append("kerah_tinggi")
            continue
        spec = R["tambahan"][t]
        body_cubes.extend(spec.get("kubus", []))
        if spec.get("lembar"):
            sheets_extra.append(spec["lembar"])
        if spec.get("kepala"):
            head_cloth = spec["kepala"]
        hide.update(spec.get("sembunyi", []))
        tepi_naik += spec.get("tepi_naik", 0)
        if spec.get("varian") == "bahu_kiri":
            varian |= V_BAHU_KIRI
    body_cubes.extend(row["kubus"])
    if fem:
        body_cubes.extend(row["kubus_p"])
    if jrow:
        body_cubes.extend(jrow["kubus"])
        if fem:
            body_cubes.extend(jrow["kubus_p"])
        tepi_naik += jrow["tepi_naik"]
    if row["selendang_p"] and fem:
        body_cubes.extend(["selendang_1", "selendang_2"])
    if row["murid"] and realm >= 3 and "liontin_giok" not in body_cubes:
        body_cubes.append("liontin_giok")
    if row["murid"] and realm >= 7:
        body_cubes.extend(["selendang_1", "selendang_2", "selendang_punggung"])
    if row["melayang"] or (row["murid"] and realm >= 7):
        varian |= V_SELENDANG_MELAYANG
    if row["pudar"]:
        varian |= V_PUDAR
    if row["lengan_telanjang"]:
        varian |= V_LENGAN_TELANJANG
    if row["murid"] and pangkat >= 0:
        body_cubes.append("lencana_giok")
    if busana_khusus == 5:
        body_cubes = []
    # facial hair cubes
    if rw_name in ("jenggot_kambing", "jenggot_penuh"):
        hair_cubes.append("jenggot_pendek")
    elif rw_name == "jenggot_panjang":
        hair_cubes.append("jenggot_panjang")
    elif rw_name == "kumis_naga":
        hair_cubes.append("kumis_naga")
    if alis == "putih_panjang":
        hair_cubes.append("alis_panjang")
    for c in row["kubus"] + (jrow["kubus"] if jrow else []):
        if c in UV_RAMBUT:
            hair_cubes.append(c)

    # ornament (hiasan)
    hiasan = row["hiasan_p"] if fem else row["hiasan_l"]
    if not hiasan:
        hiasan = "guan_kayu" if rambut == "L2" else ("kain" if rambut in ("L1", "L6", "L7") else "")
    if realm >= 5 and hiasan in HIASAN_TIER and hiasan != HIASAN_TIER[-1]:
        hiasan = HIASAN_TIER[HIASAN_TIER.index(hiasan) + 1]
    if row["murid"] and realm >= 7 and pangkat == 5 and not fem:
        hiasan = "mahkota"
    ornament = {}   # cube -> sheet
    has_bun = "sanggul" in hair_cubes
    if hiasan.startswith("guan") or hiasan in ("kain", "kain_tepi"):
        mat = "kain" if hiasan in ("kain", "kain_tepi") else hiasan.split("_")[1]
        if has_bun:
            hair_cubes = [c for c in hair_cubes if c not in ("guan_rendah", "guan_tinggi")]
            if mat == "kain" or rambut in ("L1", "L6", "L7", "P3"):
                hair_cubes.append("guan_rendah")
                ornament["guan_rendah"] = "rambut/kubus/guan_rendah_" + mat
            else:
                hair_cubes.append("guan_tinggi")
                ornament["guan_tinggi"] = "rambut/kubus/guan_tinggi_" + mat
    elif hiasan == "mahkota" and has_bun:
        hair_cubes = [c for c in hair_cubes if c not in ("guan_rendah", "guan_tinggi")]
        hair_cubes.append("mahkota_teratai")
    elif hiasan.startswith("tusuk"):
        mat = hiasan.split("_")[1]
        if has_bun or "sanggul_rendah" in hair_cubes:
            hair_cubes.append("tusuk_guan")
            ornament["tusuk_guan"] = "rambut/kubus/tusuk_guan_" + mat
    elif hiasan.startswith("buyao"):
        mat = "perak" if "perak" in hiasan else "emas"
        if "sanggul_tinggi" in hair_cubes:
            ornament["tusuk_buyao"] = "rambut/kubus/tusuk_buyao_" + mat
            ornament["manik_buyao"] = "rambut/kubus/manik_buyao_" + mat
        if hiasan.endswith("bunga"):
            hair_cubes.append("bunga_rambut")
            ornament["bunga_rambut"] = "rambut/kubus/bunga_rambut_plum"
    elif hiasan.startswith("bunga"):
        hair_cubes.append("bunga_rambut")
        ornament["bunga_rambut"] = "rambut/kubus/bunga_rambut_" + hiasan.split("_")[1]
    if "guan_tinggi" in hair_cubes and "guan_tinggi" not in ornament:
        ornament["guan_tinggi"] = "rambut/kubus/guan_tinggi_kayu"
    if "guan_rendah" in hair_cubes and "guan_rendah" not in ornament:
        ornament["guan_rendah"] = "rambut/kubus/guan_rendah_kain"
    if "tusuk_guan" in hair_cubes and "tusuk_guan" not in ornament:
        ornament["tusuk_guan"] = "rambut/kubus/tusuk_guan_kayu"
    if "tusuk_buyao" in hair_cubes and "tusuk_buyao" not in ornament:
        ornament["tusuk_buyao"] = "rambut/kubus/tusuk_buyao_perak"
        ornament["manik_buyao"] = "rambut/kubus/manik_buyao_perak"
    if "bunga_rambut" in hair_cubes and "bunga_rambut" not in ornament:
        ornament["bunga_rambut"] = "rambut/kubus/bunga_rambut_plum"

    bertopi = bool(m["topi_menutup"]) or head_cloth in ("kerudung", "tudung") or "caping_bambu" in body_cubes
    if bertopi:
        keep_ribbon = "ekor_kuda_1" in hair_cubes
        hair_cubes = [c for c in hair_cubes if c not in HIDE_UNDER_HAT or (c == "pita_rambut" and keep_ribbon)]
    for h in hide:
        body_cubes = [c for c in body_cubes if c != h]

    # ------------------------------------------------------------ layers (40.3.9 order)
    L = []

    def full(sheet):
        L.append(("biasa", sheet, 0, 0, 64, 64, 0, 0))

    def face8(sheet):
        L.append(("biasa", sheet, 0, 0, 8, 8, 8, 8))

    full("kulit/dasar_%s_%d_%s" % (S, usia, W))
    full("wajah/bentuk_%s_%d" % (S, SHAPES.index(bentuk)))
    if usia != ANAK:
        face8("wajah/hidung_" + ("tua" if usia == TUA else S))
    for mk in ("bekas_luka", "tahi_lalat", "coreng_jelaga"):
        if mk in marks:
            face8("wajah/tanda_" + mk)
    if "bekas_cakar" in marks:
        full("wajah/tanda_bekas_cakar")
    if "huadian" in marks and fem:
        face8("wajah/tanda_huadian" + ("_emas" if row["kelas"] == "bangsawan" else ""))
    if "tanda_dao" in marks:
        face8("wajah/tanda_tanda_dao")
    L.append(("alis", "wajah/alis_" + alis, 8, 8))
    L.append(("mata", "wajah/mata_" + mata + ("_p" if fem and mata in LASHES else ""), 8, 8))
    L.append(("mulut", "wajah/mulut_" + mulut, 8, 8))
    if rw_name:
        full("wajah/rambut_" + rw_name)
    if fisik == 8:
        full("wajah/tanda_nadi_sesat")
    full("busana/dalam_%s_%s" % (S, W))
    if busana_khusus != 5:
        full("busana/%s_%s" % (busana, W))
        level = 0 if pangkat < 0 else (0 if pangkat <= 1 else (1 if pangkat <= 3 else 2))
        level = min(2, level + tepi_naik)
        if level > 0:
            full("busana/%s_tepi%d_%s" % (busana, level, W))
        buzi = row["buzi"] or gb.get("buzi", "")
        if busana in ("PEJABAT", "HAKIM_BAWAH") and buzi and busana_khusus != 2:
            full("busana/buzi_" + buzi)
    if fisik == 6:
        full("wajah/tanda_tulang_tertinggi")
    if "tinta_jari" in marks:
        full("wajah/tanda_tinta_jari_" + W)
    for sx in sheets_extra:
        full("busana/tambahan_%s_%s" % (sx, W))
    if busana_khusus == 5:
        full("busana/tambahan_tidur_" + W)
    luka = m["luka"] or row["luka"]
    if luka == 2:
        full("luka/perban_lengan_" + W)
    elif luka == 1:
        full("luka/perban_kaki")
    kaki = row["kaki"] or gb.get("kaki", "kain")
    if busana_khusus == 5:
        kaki = "telanjang"
    if kaki != "telanjang":
        full("busana/kaki_" + kaki)
    full("rambut/%s_dasar" % rambut)
    if rambut not in ("L5", "A1"):
        full("rambut/%s_%s" % (rambut, "bertopi" if bertopi else "topi"))
    if row["buddha"] and rambut == "L5":
        full("rambut/titik_tahbisan")
    if busana_khusus == 2:
        head_cloth = "ikat_putih"
    if head_cloth:
        full("busana/kepala_" + head_cloth)
    # cubes, in table order (bit order)
    hair_set = set(hair_cubes)
    body_set = set(body_cubes)
    kubus_rambut = 0
    kubus_busana = 0
    for i, cid in enumerate(RAMBUT_KUBUS):
        if cid in hair_set:
            kubus_rambut |= 1 << i
            sheet = ornament.get(cid, "rambut/kubus/" + cid)
            w, h = cube_sheet_size(UV_RAMBUT[cid])
            u, v = UV_RAMBUT[cid][3]
            L.append(("biasa", sheet, 0, 0, w, h, u, v))
    for i, cid in enumerate(BUSANA_KUBUS):
        if cid in body_set:
            kubus_busana |= 1 << i
            sheet = "busana/kubus/" + cid
            if cid == "lencana_giok":
                sheet += "_" + R["lencana_pangkat"][max(0, min(5, pangkat))]
            w, h = cube_sheet_size(UV_BUSANA[cid])
            u, v = UV_BUSANA[cid][3]
            L.append(("biasa", sheet, 0, 0, w, h, u, v))

    sikap = row["sikap_p"] if fem else row["sikap_l"]
    if jrow and jrow["sikap"]:
        sikap = jrow["sikap"]
    rp = rng_for(benih, "prop")
    prop = row["prop"]
    for pid, pct in row["prop_acak"].items():
        if rp.chance(pct):
            prop = int(pid)
            break
    if jrow and jrow["prop"]:
        prop = jrow["prop"]
    return {
        "kelamin": kel, "usia": usia, "ramping": ramping, "benih": benih, "palet": palet, "lapisan": L,
        "kubus_rambut": kubus_rambut, "kubus_busana": kubus_busana, "varian": varian, "bertopi": bertopi,
        "kelas": row["kelas"], "busana": busana, "rambut": rambut, "mata": mata, "prop": prop, "sikap": sikap,
        "jalan": row["jalan"], "pudar": row["pudar"],
    }


def kunci(look):
    """64-bit atlas cache key (FNV-1a over every field that changes pixels), same as SosokRupa.kunci()."""
    from sosok_lib import fnv64_ints
    vals = [look["kelamin"], look["usia"], 1 if look["ramping"] else 0, look["benih"] & 0xFFFF]
    vals.extend(look["palet"])
    for layer in look["lapisan"]:
        vals.append(fnv32(layer[0] + ":" + layer[1]))
        vals.extend(layer[2:])
    vals.append(look["kubus_rambut"])
    vals.append(look["kubus_busana"] & 0xFFFFFFFF)
    vals.append((look["kubus_busana"] >> 32) & 0xFFFFFFFF)
    return fnv64_ints(vals)
