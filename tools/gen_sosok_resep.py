"""Writes assets/kultivator/sosok/resep.json (DESIGN 40.3.10 + 40.4, module 40K).

    python -I tools/gen_sosok_resep.py

The look tables of 40.4 as data: every NPC role (``peran.<NpcRole.id()>``, 38 roles included), Dimension D people
(``peran.d.*``), Rogue Cultivators (``peran.liar.*``), envoys, tournament crowds, disciples by rank (``peran.murid.<n>``),
schedule-group fallbacks (``peran.kelompok.<GROUP>``) and life paths (``jalan.<id>``), plus the colour tables (skin,
hair, iris, named palette, Dao, realm, rank trims and badges) and the integer colour lookup tables LIN/SRGB used by
``Palet.tangga``. ``RupaResolver`` (Java) and ``sosok_lib.resolve`` (Python) read exactly this file.

Row fields (all optional except k/u/gaya/kain):
  k        sex policy: "L", "P" or "C<p>" (p % women)            u      age weights {"A","M","D","T"}
  gaya     [[style, weight]...]; "HANFU" = HANFU_L / HANFU_P by sex
  tambahan extras: pesta kerja bepergian zirah penyamaran kerudung ikat_kuning
  kain     KAIN1 / KAIN2 / TEPI / LOGAM tokens: named colour, "#hex", "dao", "sekte", "klan", "cerah", "kelompok", "-"
  rambut_l / rambut_p   hair styles (equal weights); hiasan_l / hiasan_p ornament
  mata / alis / mulut / bentuk   [[part, weight]...]       tanda {mark: percent}      rw {facial hair: percent}
  kulit    "arwah" | kulit_gelap percent (+1 tone darker)    rambut_warna named override    iris "#hex"
  topi     informative hat name; topi_menutup true when it hides the bun (40.4.5)
  prop     PropId held when idle; sikap idle clip id (108-121, 0 = by sex); jalan walk profile ("" = by sex)
  kelas    fallback atlas class: sipil kultivator penjaga rahib bangsawan iblis
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sosok_lib import LIN, SRGB, RESEP, ROOT, UV_RAMBUT, UV_BUSANA  # noqa: E402
from gen_sosok_bagian_busana import GAYA  # noqa: E402
from gen_sosok_bagian_rambut import HAIR_STYLES  # noqa: E402

PROPID = os.path.join(ROOT, "src", "main", "java", "dev", "kultivator", "sosok", "PropId.java")


def prop_ids():
    text = open(PROPID, encoding="utf-8").read()
    consts = dict((m.group(1), int(m.group(2))) for m in re.finditer(r"public static final int ([A-Z_]+) = (\d+);", text))
    out = {}
    for m in re.finditer(r"put\(([A-Z_]+), \"([a-z_]+)\"\)", text):
        out[m.group(2)] = consts[m.group(1)]
    return out


PROP = prop_ids()

SIKAP = {"tangan_belakang": 108, "lipat_tangan": 109, "tangan_lengan": 110, "tombak": 111, "gagang": 112,
         "santai_l": 113, "santai_p": 114, "tua": 115, "anak": 116, "berkacak": 117, "tunduk": 118, "angkuh": 119,
         "doa": 120, "tangan_depan": 121, "santai": 0, "": 0}
JALAN = ["", "gagah", "anggun", "santai", "tergesa", "militer", "bangsawan", "pedagang", "rahib", "kuli", "tua_tongkat",
         "tua", "anak_lompat", "penari", "iblis", "arwah"]

WARNA = {
    "lak": "#8E1F1F", "emas": "#C9A24A", "emas_terang": "#E8C860", "giok": "#5FA88C", "kayu": "#5A3A22",
    "kayu_muda": "#9C7448", "besi": "#7C8288", "perunggu": "#8C6A3A", "perak": "#C8CCD4", "kertas": "#E8DDC0",
    "tinta": "#1E1A18", "sutra_biru": "#3A5A8C", "nila": "#2B3A5C", "kasar": "#8A7A60", "gading": "#EDE6D6",
    "putih": "#F4EFE2", "hitam": "#1A1A20", "hijau_teh": "#3E6E4E", "ungu_tua": "#4A2A5A", "darah": "#6A1420",
    "pasir": "#C8A878", "laut": "#2E5A7A", "abu": "#8A8278", "kuning_tanah": "#B08A3A", "merah_muda": "#E8A8B8",
    "biru_langit": "#8EB8D8", "teh": "#6E8E4E", "cokelat": "#6A4A30", "teal": "#2E7A78", "kuning_naga": "#C9A030",
    "mutiara": "#E8E8F0", "tulang": "#D8D0C0", "ter": "#2A2622", "kasar_kotor": "#6E6250", "biru": "#4F8FFF",
}

KULIT = [("porselen", "#F2D7C2", 14), ("gading", "#EAC6A3", 22), ("gandum", "#D8AC83", 22), ("sawo", "#C0906A", 16),
         ("sawo_matang", "#A2734E", 12), ("cokelat", "#835536", 8), ("cokelat_tua", "#5F3B25", 6),
         ("pucat_arwah", "#D9E0E3", 0)]
RAMBUT_WARNA = {"hitam": ("#1B191E", 62), "hitam_biru": ("#1D2333", 18), "cokelat_tua": ("#3A291F", 12),
                "cokelat": ("#57392A", 6), "merah_tua": ("#5E1E1E", 2), "abu": ("#8E8C90", 0), "putih": ("#E6E3DC", 0),
                "perak": ("#C9D6E6", 0), "arwah": ("#7A8690", 0)}
IRIS = [("#3B2418", 60), ("#5A3A22", 25), ("#241812", 15)]

# Dao 0-7 (Dao enum order): KAIN1, SULAM, LOGAM material, iris (Golden Core+ / Nascent Soul+), motif
DAO = [
    {"id": "sword", "kain": "#7C8CA8", "sulam": "#D8E6FF", "logam": "perak", "iris": None, "motif": "pedang"},
    {"id": "fire", "kain": "#8E2E1A", "sulam": "#FF6A2A", "logam": "emas", "iris": ["#E08A2A", 3], "motif": "api"},
    {"id": "ice", "kain": "#5E8EA0", "sulam": "#9FF3FF", "logam": "perak", "iris": ["#8FD8F0", 3], "motif": "salju"},
    {"id": "lightning", "kain": "#4A3A78", "sulam": "#B57CFF", "logam": "perak", "iris": ["#9A6AF0", 3], "motif": "kilat"},
    {"id": "body", "kain": "#7A5A34", "sulam": "#D9A05B", "logam": "perunggu", "iris": None, "motif": "gunung"},
    {"id": "life", "kain": "#3E6E46", "sulam": "#6CFF8A", "logam": "giok", "iris": None, "motif": "daun"},
    {"id": "shadow", "kain": "#2E2A3A", "sulam": "#7A6AA0", "logam": "besi", "iris": ["#6A5A88", 4], "motif": "awan"},
    {"id": "space", "kain": "#2A2060", "sulam": "#5A3FD0", "logam": "perak", "iris": ["#4A35B0", 4], "motif": "bintang"},
]
REALM = ["#B0B0B0", "#E6F4FF", "#7FE0FF", "#FFD54A", "#B98CFF", "#FF7AD9", "#7CFFB2", "#FFFFFF", "#FFE9A8"]
RANK_TRIM = ["#9A9A9A", "#F2F2F2", "#4F8FFF", "#3FBF8A", "#A060E0", "#FFF2C0"]
RANK_BADGE = ["kayu", "perunggu", "perak", "giok", "ungu", "emas"]
LOGAM_MATERIAL = {"emas": "#C9A24A", "perak": "#C8CCD4", "perunggu": "#8C6A3A", "giok": "#5FA88C", "besi": "#4A4E56",
                  "emas_kusam": "#B8963E"}
KLAN = ["#3A5A8C", "#6A2A2A", "#2E5A3E", "#5A3A6A", "#8A6A2A", "#2A4A5A", "#7A3A1E", "#3A3A4A"]
CERAH = ["#C83030", "#E0B030", "#6AB04A", "#5A9AD8", "#D86A9A", "#E07A2A"]
LIAR = ["#3A1A4A", "#4A1E1E", "#1E3A2E", "#2A2A4A", "#4A3A1A", "#1E2E3A", "#3A1E2E", "#2E2E2E"]

# Hair styles: sex, ages, painted hat layer?, cubes, variant notes
GAYA_RAMBUT = {
    "L1": {"k": "L", "u": "MD", "kubus": ["sanggul", "guan_rendah"]},
    "L2": {"k": "L", "u": "MD", "kubus": ["sanggul", "guan_tinggi", "rambut_atas", "rambut_bawah"]},
    "L3": {"k": "LP", "u": "M", "kubus": ["ekor_kuda_1", "ekor_kuda_2"]},
    "L4": {"k": "LP", "u": "MD", "kubus": ["rambut_atas", "rambut_bawah", "poni_samping"]},
    "L5": {"k": "L", "u": "AMDT", "kubus": []},
    "L6": {"k": "L", "u": "T", "kubus": ["sanggul", "guan_rendah"]},
    "L7": {"k": "L", "u": "MD", "kubus": ["sanggul"], "kecil": True},
    "L8": {"k": "LP", "u": "AM", "kubus": []},
    "P1": {"k": "P", "u": "DT", "kubus": ["sanggul_tinggi", "tusuk_buyao", "manik_buyao"]},
    "P2": {"k": "P", "u": "AM", "kubus": ["cepol", "pita_rambut"]},
    "P3": {"k": "P", "u": "MD", "kubus": ["sanggul", "rambut_atas", "rambut_bawah", "pita_rambut"], "kecil": True},
    "P4": {"k": "P", "u": "MD", "kubus": ["ekor_kuda_1", "ekor_kuda_2", "pita_rambut"]},
    "P5": {"k": "P", "u": "AMD", "kubus": ["kepang_1", "kepang_2"]},
    "P6": {"k": "P", "u": "MD", "kubus": ["rambut_atas", "rambut_bawah_panjang", "poni_samping"]},
    "P7": {"k": "P", "u": "T", "kubus": ["sanggul_rendah", "tusuk_guan"]},
    "A1": {"k": "LP", "u": "A", "kubus": ["jambul_anak"]},
    "A2": {"k": "L", "u": "A", "kubus": ["jambul_anak"], "satu": True},
}

# Garment styles: cubes, default footwear, default colours
GAYA_BUSANA = {
    "HANFU_L": {"kubus": ["rok_panjang", "lengan_lebar", "sabuk"], "kaki": "kain"},
    "HANFU_P": {"kubus": ["rok_panjang", "lengan_lebar", "pita_sabuk"], "kaki": "kain"},
    "PENDEK": {"kubus": ["rok_pendek", "sabuk"], "kaki": "kain"},
    "TEMPUR": {"kubus": ["rok_pendek_sisi", "rok_zirah", "pelindung_lengan", "sabuk"], "kaki": "bot"},
    "TAO": {"kubus": ["rok_panjang", "lengan_lebar", "sabuk"], "kaki": "kain", "kain": ["#2B3A5C", "#E8E4D8", "#E8E4D8", "perak"]},
    "RAHIB": {"kubus": ["rok_panjang", "lengan_lebar_manset", "lengan_lebar_kantong"], "kaki": "rahib"},
    "KASAYA": {"kubus": ["rok_panjang", "lengan_lebar", "cincin_kasaya"], "kaki": "rahib", "kain": ["#B08A3A", "#EDE6D6", "#8A2A1E", "#D8B04A"]},
    "IBLIS": {"kubus": ["rok_panjang", "rumbai_iblis", "kerah_tinggi"], "kaki": "bot"},
    "SAUDAGAR": {"kubus": ["rok_panjang", "kantong", "sabuk"], "kaki": "kain", "lebar": True, "kain": ["#7A1E22", "#D8B04A", "#D8B04A", "emas"]},
    "PETANI": {"kubus": [], "kaki": "jerami"},
    "BANGSAWAN": {"kubus": ["rok_panjang", "lengan_lebar", "liontin_giok", "sabuk"], "kaki": "kain"},
    "ABADI": {"kubus": ["rok_panjang", "lengan_lebar", "selendang"], "kaki": "kain", "kain": ["#F4EFE2", "#E8D9A8", "#D8B04A", "emas"]},
    "PENJAGA": {"kubus": ["rok_zirah", "sabuk"], "kaki": "bot"},
    "PEJABAT": {"kubus": ["rok_panjang", "sabuk", "lengan_lebar"], "kaki": "kain", "buzi": "bangau"},
    "SARJANA": {"kubus": ["rok_panjang", "lengan_lebar"], "kaki": "kain", "kain": ["#DDE6EE", "#F4EFE2", "#1E1A18", "perak"]},
    "COMPANG": {"kubus": ["rumbai_iblis"], "kaki": "telanjang"},
    "ANAK": {"kubus": [], "kaki": "kain"},
    "PELAYAN": {"kubus": ["rok_pendek", "celemek"], "kaki": "kain"},
    "LAUT": {"kubus": ["sabuk"], "kaki": "telanjang"},
    "GURUN": {"kubus": ["rok_panjang", "jubah_luar_bahu"], "kaki": "jerami", "kepala": "kerudung"},
    "KAFAN": {"kubus": ["rok_panjang", "lengan_lebar"], "kaki": "telanjang", "kain": ["#E8E6E0", "#D8D4CC", "#C8C4BC", "perak"]},
    "HAKIM_BAWAH": {"kubus": ["rok_panjang", "lengan_lebar", "sabuk"], "kaki": "bot", "buzi": "harimau_hitam", "kain": ["#5A1418", "#1A1618", "#C9A24A", "emas"]},
}

# cube groups -> cube ids (40.3.7)
KELOMPOK_KUBUS = {
    "rok_panjang": ["rok_panjang_depan", "rok_panjang_belakang", "rok_panjang_sisi"],
    "rok_pendek": ["rok_pendek_depan", "rok_pendek_belakang", "rok_pendek_sisi"],
    "rok_pendek_sisi": ["rok_pendek_sisi"],
    "lengan_lebar": ["lengan_lebar_manset", "lengan_lebar_kantong"],
    "selendang": ["selendang_1", "selendang_2", "selendang_punggung"],
}

# Extras (40.3.7 "Tambahan"): cubes, sheet
TAMBAHAN = {
    "bepergian": {"kubus": ["jubah_luar", "jubah_luar_bahu", "caping_bambu", "caping_puncak", "buntalan"], "lembar": "bepergian"},
    "bepergian_kota": {"kubus": ["jubah_luar", "buntalan", "caping_bambu", "caping_puncak"], "lembar": "bepergian"},
    "pesta": {"kubus": ["liontin_giok", "bunga_rambut"], "tepi_naik": 1},
    "kerja": {"kubus": [], "lembar": "kerja", "sembunyi": ["lengan_lebar_manset", "lengan_lebar_kantong"]},
    "celemek": {"kubus": ["celemek"]},
    "zirah": {"kubus": ["bahu_zirah", "rok_zirah"], "lembar": "zirah"},
    "penyamaran": {"kubus": ["jubah_luar"], "kepala": "tudung"},
    "kerudung": {"kepala": "kerudung"},
    "ikat_kuning": {"kepala": "ikat_kuning"},
    "jubah_luar": {"kubus": ["jubah_luar"]},
    "kerah_tinggi": {"kubus": ["kerah_tinggi"]},
    "kerah_bulu": {"kubus": ["kerah_bulu"]},
    "caping": {"kubus": ["caping_bambu", "caping_puncak"]},
    "selendang": {"kubus": ["selendang_1", "selendang_2", "selendang_punggung"]},
    "pelindung_lengan": {"kubus": ["pelindung_lengan"]},
    "labu": {"kubus": ["labu"]},
    "kantong": {"kubus": ["kantong"]},
    "buntalan": {"kubus": ["buntalan"]},
    "liontin_giok": {"kubus": ["liontin_giok"]},
    "rumbai_iblis": {"kubus": ["rumbai_iblis"]},
    "bahu_zirah": {"kubus": ["bahu_zirah"]},
    "bahu_kiri": {"kubus": ["bahu_zirah"], "varian": "bahu_kiri"},
}


def U(spec):
    """'D' -> {D:100}; 'M/D' -> equal; dict passes."""
    if isinstance(spec, dict):
        return spec
    parts = spec.split("/")
    return {p: 100 // len(parts) for p in parts}


def row(k, u, gaya, kain, rl=None, rp=None, **kw):
    if isinstance(gaya, str):
        gaya = [[gaya, 100]]
    r = {"k": k, "u": U(u), "gaya": gaya, "kain": kain}
    if rl:
        r["rambut_l"] = rl
    if rp:
        r["rambut_p"] = rp
    for key, v in kw.items():
        if key == "prop":
            v = PROP[v] if isinstance(v, str) else v
        if key == "sikap":
            v = SIKAP[v]
        if key == "jalan":
            assert v in JALAN, v
        r[key] = v
    r.setdefault("kelas", "sipil")
    return r


P = {}   # peran rows

# ---------------------------------------------------------------------- A. old town roles (15)
P["juru_lelang"] = row("C25", "D", "PEJABAT", ["lak", "gading", "emas", "emas"], ["L1"], ["P1"], hiasan_p="tusuk_emas",
                       mata=[["phoenix", 1]], mulut=[["senyum_tipis", 1]], buzi="koin", topi="FUTOU", topi_menutup=True,
                       prop="kipas_lipat", sikap="tangan_belakang", jalan="bangsawan", kelas="bangsawan")
P["juru_lelang_abadi"] = row("C25", "D", "PEJABAT", ["emas_terang", "putih", "giok", "emas"], ["L2"], ["P1"],
                             hiasan_l="guan_giok", hiasan_p="buyao_emas", tambahan=["pesta"], mata=[["phoenix", 1]],
                             buzi="koin", topi="FUTOU_GOLD", topi_menutup=True, prop="kipas_lipat",
                             sikap="tangan_belakang", jalan="bangsawan", kelas="bangsawan")
P["pemilik_teh"] = row("C55", {"D": 70, "T": 30}, "HANFU", ["hijau_teh", "gading", "kayu_muda", "perunggu"], ["L7"], ["P3"],
                       hiasan_p="tusuk_kayu", tambahan=["celemek"], mata=[["lembut", 1]], mulut=[["senyum_tipis", 1]],
                       topi="TEA", prop="teko", sikap="tangan_lengan", jalan="santai")
P["makelar_gelap"] = row("C30", "D", "HANFU", ["hitam", "ungu_tua", "emas", "emas"], ["L4"], ["P6"],
                         tambahan=["penyamaran"], mata=[["tajam", 1]], mulut=[["tegas", 1]], tanda={"bekas_luka": 40},
                         topi="BROKER", topi_menutup=True, prop="kantong_uang", sikap="lipat_tangan", jalan="iblis",
                         kelas="iblis")
P["pedagang_pil"] = row("C40", "D", "SAUDAGAR", ["teh", "gading", "emas", "perunggu"], ["L1"], ["P3"],
                        mata=[["almond", 1]], mulut=[["senyum_tipis", 1]], topi="MERCHANT", topi_menutup=True,
                        prop="labu_pil", sikap="tangan_lengan", jalan="pedagang")
P["pedagang_abadi"] = row("C40", "D", "SAUDAGAR", ["emas_terang", "putih", "giok", "emas"], ["L2"], ["P1"],
                          tambahan=["pesta"], mata=[["almond", 1]], topi="MERCHANT_GOLD", topi_menutup=True,
                          prop="sempoa", sikap="tangan_lengan", jalan="pedagang", kelas="bangsawan")
P["pandai_pusaka"] = row("C10", "D", "PENDEK", ["kasar", "cokelat", "lak", "besi"], ["L7"], ["P4"], tambahan=["kerja", "celemek"],
                         mata=[["tajam", 1]], tanda={"coreng_jelaga": 100}, rw={"jenggot_penuh": 40}, topi="SMITH",
                         prop="palu", sikap="berkacak", jalan="gagah")
P["penjaga"] = row("C15", {"M": 30, "D": 70}, "PENJAGA", ["nila", "kasar", "lak", "besi"], ["L1"], ["P4"],
                   mata=[["tajam", 1]], tanda={"bekas_luka": 20}, topi="GUARD", topi_menutup=True, prop="tombak",
                   sikap="tombak", jalan="militer", kelas="penjaga")
P["warga"] = row("C50", {"A": 15, "M": 20, "D": 45, "T": 20}, [["PETANI", 40], ["HANFU", 40], ["PENDEK", 20]],
                 ["kasar", "gading", "cokelat", "perunggu"], ["L1", "L7", "L8", "L6"], ["P2", "P3", "P5", "P7"],
                 topi="FOLK_*", sikap="santai", jalan="santai")
P["pengembara"] = row("C35", {"M": 40, "D": 60}, "TEMPUR", ["dao", "gading", "-", "besi"], ["L2", "L3"], ["P4", "P3"],
                      tambahan_acak={"bepergian": 50}, mata=[["tajam", 1]], tanda={"bekas_luka": 30},
                      topi="WANDERER_DOULI/GUAN", prop=0, sikap="gagang", jalan="", kelas="kultivator")
P["saingan"] = row("C30", {"M": 60, "D": 40}, "TEMPUR", ["kelompok", "hitam", "emas", "besi"], ["L3"], ["P4"],
                   mata=[["tajam", 1]], mulut=[["tegas", 1]], topi="RIVAL", sikap="gagang", jalan="", kelas="kultivator")
P["abadi"] = row("C50", {"M": 30, "D": 70}, "ABADI", ["putih", "gading", "emas", "emas"], ["L2"], ["P6"],
                 hiasan_l="guan_giok", mata=[["phoenix", 1]], topi="IMMORTAL", rambut_warna="hitam_biru_emas",
                 melayang=True, sikap="tangan_lengan", jalan="bangsawan", kelas="kultivator")
P["penceramah"] = row("C30", "T", "TAO", ["putih", "gading", "emas", "emas"], ["L6"], ["P7"], mata=[["tua", 1]],
                      alis_l=[["putih_panjang", 1]], tanda={"tanda_dao": 100}, rw={"jenggot_panjang": 100},
                      kubus=["alis_panjang"], topi="SAGE", topi_menutup=True, prop="kebut", sikap="tangan_belakang",
                      jalan="tua", kelas="rahib")
P["utusan"] = row("L", "D", "PEJABAT", ["emas_terang", "putih", "giok", "emas"], ["L1"], None, mulut=[["tegas", 1]],
                  buzi="bangau", topi="ENVOY", topi_menutup=True, prop="papan_titah", sikap="tangan_belakang",
                  jalan="militer", kelas="bangsawan")
P["penjaga_kebun"] = row("C70", {"M": 50, "D": 50}, "HANFU", ["merah_muda", "putih", "giok", "emas"], ["L1"], ["P2"],
                         tambahan=["kerja"], mata=[["lembut", 1]], mulut=[["senyum_tipis", 1]], topi="GARDEN",
                         prop="keranjang", sikap="tangan_depan", jalan="anggun")

# ---------------------------------------------------------------------- B. capital, guild, villages, clans, compounds
P["pangeran_kota"] = row("L", "D", "BANGSAWAN", ["kuning_naga", "putih", "lak", "emas"], ["L2"], None,
                         tambahan=["pesta"], hiasan_l="mahkota", mata=[["phoenix", 1]], mulut=[["tegas", 1]],
                         rw={"kumis_tipis": 100}, topi="mahkota_guan_giok", sikap="tangan_belakang", jalan="bangsawan",
                         kelas="bangsawan")
P["menteri"] = row("C20", {"D": 60, "T": 40}, "PEJABAT", ["lak", "gading", "emas", "emas"], ["L1"], ["P1"],
                   mulut=[["tegas", 1]], rw={"jenggot_kambing": 50}, buzi="bangau", topi="topi_pejabat",
                   topi_menutup=True, prop="papan_catatan", sikap="tangan_lengan", jalan="bangsawan", kelas="bangsawan")
P["pengawal_istana"] = row("C20", "M/D", "PENJAGA", ["lak", "hitam", "emas", "besi"], ["L1"], ["P4"],
                           tambahan=["zirah"], mata=[["tajam", 1]], topi="helm_berumbai", topi_menutup=True,
                           prop="tombak", sikap="tombak", jalan="militer", kelas="penjaga")
P["kapten_penjaga"] = row("C20", "D", "PENJAGA", ["lak", "hitam", "emas", "emas"], ["L1"], ["P4"],
                          tambahan=["zirah", "jubah_luar"], mata=[["tajam", 1]], tanda={"bekas_luka": 60},
                          topi="helm_kapten", topi_menutup=True, sikap="berkacak", jalan="militer", kelas="penjaga")
P["penjaga_gerbang"] = row("C15", "M/D", "PENJAGA", ["nila", "kasar", "lak", "besi"], ["L1"], ["P4"],
                           mata=[["tajam", 1]], topi="helm_berumbai", topi_menutup=True, prop="tombak", sikap="tombak",
                           jalan="militer", kelas="penjaga")
P["hakim"] = row("C15", "D/T", "PEJABAT", ["hitam", "lak", "emas", "emas"], ["L1"], ["P1"], mulut=[["tegas", 1]],
                 rw={"jenggot_penuh": 100}, buzi="harimau", topi="topi_pejabat", topi_menutup=True, prop="palu_kayu",
                 sikap="tangan_belakang", jalan="bangsawan", kelas="bangsawan")
P["bankir"] = row("C35", "D", "SAUDAGAR", ["lak", "emas", "emas", "emas"], ["L1"], ["P1"], mata=[["almond", 1]],
                  tanda={"tahi_lalat": 100}, topi="topi_bulat_pedagang", topi_menutup=True, prop="sempoa",
                  sikap="tangan_lengan", jalan="pedagang", kelas="bangsawan")
P["bandar_batu"] = row("C25", "D", "PENDEK", ["cokelat", "kasar", "emas", "perunggu"], ["L7"], ["P4"],
                       tambahan=["kerja", "celemek"], mata=[["tajam", 1]], topi="celemek_kulit", prop="pahat",
                       sikap="berkacak", jalan="gagah")
P["pemotong_batu"] = row("C20", "M/D", "PENDEK", ["kasar", "gading", "cokelat", "besi"], ["L7"], ["P4"],
                         tambahan=["kerja"], tanda={"coreng_jelaga": 100}, topi="kacamata_batu+kain_kepala",
                         topi_menutup=True, prop="pahat", sikap="santai", jalan="santai")
P["pedagang_binatang"] = row("C30", "D", "SAUDAGAR", ["cokelat", "pasir", "kayu", "perunggu"], ["L4"], ["P5"],
                             tambahan=["kerah_bulu"], mulut=[["tegas", 1]], tanda={"bekas_luka": 30}, topi="topi_bulu",
                             topi_menutup=True, prop="tali_kekang", sikap="berkacak", jalan="gagah")
P["pedagang_senjata"] = row("C30", "D", "TEMPUR", ["hitam", "lak", "emas", "besi"], ["L3"], ["P4"], mata=[["tajam", 1]],
                            sikap="lipat_tangan", jalan="gagah", kelas="kultivator")
P["empu"] = row("C10", "D/T", "PENDEK", ["kasar", "cokelat", "lak", "besi"], ["L7"], ["P4"], tambahan=["kerja", "celemek"],
                mata=[["tajam", 1]], tanda={"coreng_jelaga": 100}, rw={"jenggot_penuh": 50}, topi="celemek_kulit",
                prop="palu", sikap="berkacak", jalan="gagah")
P["alkemis"] = row("C45", "D/T", "TAO", ["ungu_tua", "gading", "emas", "perunggu"], ["L1"], ["P3"], tambahan=["kerja"],
                   mata=[["almond", 1]], tanda={"coreng_jelaga": 30}, topi="topi_alkemis", topi_menutup=True,
                   prop="labu_pil", sikap="tangan_lengan", jalan="santai", kelas="kultivator")
P["ketua_gilda"] = row("C30", "D/T", "BANGSAWAN", ["klan", "gading", "emas", "emas"], ["L2"], ["P1"], hiasan_l="guan_emas",
                       mulut=[["tegas", 1]], topi="lencana_bintang", sikap="tangan_belakang", jalan="bangsawan",
                       kelas="bangsawan")
P["petugas_gilda"] = row("C50", "M/D", "SARJANA", ["sutra_biru", "gading", "tinta", "perunggu"], ["L1"], ["P3"],
                         mata=[["almond", 1]], topi="lencana_bintang", prop="kuas", sikap="tangan_depan", jalan="pedagang")
P["penguji"] = row("C30", "D", "TEMPUR", ["nila", "hitam", "perak", "besi"], ["L3"], ["P4"], mata=[["tajam", 1]],
                   tanda={"bekas_luka": 40}, kubus=["ikat_dahi_ekor"], sikap="lipat_tangan", jalan="militer",
                   kelas="kultivator")
P["penjaga_formasi"] = row("C40", "D", "TAO", ["nila", "gading", "perak", "perak"], ["L1"], ["P3"], mulut=[["tegas", 1]],
                           topi="jubah_trigram (busana 40)", prop="piringan_formasi", sikap="tangan_lengan",
                           jalan="santai", kelas="kultivator")
P["nakhoda"] = row("C20", "D", "PENDEK", ["sutra_biru", "gading", "emas", "perunggu"], ["L4"], ["P4"],
                   tambahan=["jubah_luar"], tanda={"bekas_luka": 30}, topi="topi_nakhoda", topi_menutup=True,
                   prop="teropong", sikap="berkacak", jalan="gagah")
P["kuli"] = row("C10", "M/D", "PETANI", ["kasar", "kasar", "-", "-"], ["L7"], ["P5"], tanda={"coreng_jelaga": 100},
                topi="kain_kepala", topi_menutup=True, prop="pikulan", sikap="santai_l", jalan="kuli")
P["pemilik_penginapan"] = row("C50", "D", "HANFU", ["kayu_muda", "gading", "lak", "perunggu"], ["L1"], ["P3"],
                              tambahan=["kerja", "celemek"], mata=[["lembut", 1]], mulut=[["senyum_tipis", 1]],
                              topi="topi_bulat_pedagang", prop="kunci_kamar", sikap="tangan_depan", jalan="santai")
P["koki"] = row("C35", "D", "PENDEK", ["putih", "gading", "lak", "besi"], ["L7"], ["P2"], tambahan=["kerja", "celemek"],
                bentuk=[["bulat", 1]], mulut=[["senyum_tipis", 1]], topi="topi_koki", topi_menutup=True,
                prop="sendok_sayur", sikap="berkacak", jalan="santai")
P["pelayan"] = row("C65", "M", "PELAYAN", ["kayu_muda", "gading", "lak", "-"], ["L7"], ["P2"], mata=[["lembut", 1]],
                   prop="nampan", sikap="tangan_depan", jalan="tergesa")
P["pustakawan"] = row("C40", "D/T", "SARJANA", ["nila", "gading", "tinta", "perunggu"], ["L1"], ["P1"],
                      mata=[["tua", 40], ["almond", 60]], topi="topi_sarjana", topi_menutup=True, prop="kitab",
                      sikap="tangan_lengan", jalan="santai")
P["penceramah_fana"] = row("C30", "T", "SARJANA", ["putih", "gading", "tinta", "perak"], ["L6"], ["P7"],
                           mata=[["tua", 1]], rw={"jenggot_panjang": 100}, topi="topi_sarjana", topi_menutup=True,
                           prop="kipas_lipat", sikap="tangan_belakang", jalan="tua")
P["rahib"] = row("L", "M/D/T", "RAHIB", ["abu", "gading", "kuning_tanah", "kayu"], ["L5"], None, buddha=True,
                 mata=[["lembut", 1]], mulut=[["senyum_tipis", 1]], topi="tasbih", prop="dupa", sikap="doa",
                 jalan="rahib", kelas="rahib")
P["tabib"] = row("C50", "D/T", "SARJANA", ["gading", "hijau_teh", "giok", "perunggu"], ["L1"], ["P3"],
                 mata=[["lembut", 1]], prop="kotak_obat", sikap="tangan_depan", jalan="santai")
P["tukang"] = row("C10", "M/D", "PENDEK", ["kasar", "cokelat", "-", "besi"], ["L7"], ["P5"], tambahan=["kerja"],
                  tanda={"coreng_jelaga": 100}, topi="kain_kepala+sabuk_alat", topi_menutup=True, prop="palu",
                  sikap="berkacak", jalan="santai")
P["penjaga_teka"] = row("C60", "M/D", "HANFU", ["lak", "gading", "emas", "emas"], ["L1"], ["P2"], tambahan=["pesta"],
                        mulut=[["senyum_tipis", 1]], prop="lentera_tangan", sikap="tangan_depan", jalan="santai")
P["juara_sekte"] = row("C40", "M/D", "TEMPUR", ["sekte", "gading", "sekte", "besi"], ["L3"], ["P4"], mata=[["tajam", 1]],
                       tanda_ranah=True, sikap="gagang", jalan="", kelas="kultivator")
P["kepala_desa"] = row("L", "T", "HANFU", ["kasar", "gading", "cokelat", "perunggu"], ["L6"], None, mata=[["tua", 1]],
                       rw={"jenggot_panjang": 100}, tambahan=["caping"], prop="tongkat", sikap="tua", jalan="tua_tongkat")
P["petani"] = row("C40", "M/D/T", "PETANI", ["kasar", "gading", "-", "-"], ["L7", "L8"], ["P5"],
                  bentuk=[["bulat", 1], ["persegi", 1]], kulit_gelap=50, tambahan=["caping"], prop="cangkul",
                  sikap="santai", jalan="santai")
P["nelayan"] = row("C25", "D/T", "PETANI", ["laut", "kasar", "-", "-"], ["L7"], ["P5"], bentuk=[["persegi", 1]],
                   kulit_gelap=40, tambahan=["caping"], prop="joran", sikap="santai", jalan="santai")
P["pandai_desa"] = row("C10", "D", "PENDEK", ["kasar", "cokelat", "-", "besi"], ["L7"], ["P4"], tambahan=["kerja", "celemek"],
                       tanda={"coreng_jelaga": 100}, topi="celemek_kulit", prop="palu", sikap="berkacak", jalan="gagah")
P["pedagang_keliling"] = row("C35", "D", "PENDEK", ["cokelat", "pasir", "lak", "perunggu"], ["L7"], ["P5"],
                             tambahan=["buntalan"], mulut=[["senyum_tipis", 1]], topi="keranjang_punggung",
                             sikap="tangan_depan", jalan="pedagang")
P["anak"] = row("C50", "A", "ANAK", ["cerah", "gading", "lak", "-"], ["A1", "A2", "L8"], ["A1", "P2"],
                mata=[["anak", 1]], bentuk=[["bulat", 1]], prop_acak={"layang_layang": 10, "gasing": 10},
                sikap="anak", jalan="anak_lompat")
P["tetua_desa"] = row("C50", "T", "HANFU", ["cokelat", "gading", "kayu", "kayu"], ["L6"], ["P7"], mata=[["tua", 1]],
                      rw={"jenggot_panjang": 60}, prop="tongkat", sikap="tua", jalan="tua_tongkat")
P["patriark_klan"] = row("L", "T", "BANGSAWAN", ["klan", "gading", "emas", "emas"], ["L6"], None,
                         tambahan=["jubah_luar"], hiasan_l="mahkota", rw={"kumis_naga": 100}, mata=[["tua", 1]],
                         mulut=[["tegas", 1]], topi="mahkota_guan_giok", sikap="tangan_belakang", jalan="bangsawan",
                         kelas="bangsawan")
P["tuan_muda"] = row("C20", "M", "BANGSAWAN", ["klan", "putih", "emas", "emas"], ["L2"], ["P1"], hiasan_l="guan_giok",
                     hiasan_p="buyao_emas", mata=[["phoenix", 1]], mulut=[["senyum_tipis", 1]], ekspresi={"menyeringai": 30},
                     prop="kipas_lipat", sikap="angkuh", jalan="bangsawan", kelas="bangsawan")
P["tetua_klan"] = row("C30", "T", "HANFU", ["hitam", "klan", "emas", "perak"], ["L6"], ["P7"], tambahan=["jubah_luar"],
                      mata=[["tua", 1]], mulut=[["tegas", 1]], prop_acak={"tongkat": 30}, sikap="tangan_belakang",
                      jalan="tua")
P["murid_klan"] = row("C40", "M", "PENDEK", ["klan", "gading", "klan", "besi"], ["L3"], ["P4"], sikap="santai",
                      jalan="", kelas="kultivator")
P["murid_sekte_luar"] = row("C45", "M", "PENDEK", ["sekte", "gading", "sekte", "perunggu"], ["L1", "L3"], ["P2", "P4"],
                            pangkat=1, sikap="santai", jalan="", kelas="kultivator")
P["murid_sekte_dalam"] = row("C45", "M/D", "HANFU", ["sekte", "gading", "sekte", "perak"], ["L2", "L3"], ["P3", "P4"],
                             pangkat=2, tanda_ranah=True, sikap="tangan_depan", jalan="", kelas="kultivator")
P["tetua_sekte"] = row("C35", "D/T", "HANFU", ["sekte", "gading", "sekte", "emas"], ["L2", "L6"], ["P1", "P7"],
                       pangkat=4, tambahan=["jubah_luar"], hiasan_l="guan_giok", mata=[["tua", 60], ["phoenix", 40]],
                       tanda={"tanda_dao": 100}, prop_acak={"kebut": 40}, sikap="tangan_belakang", jalan="bangsawan",
                       kelas="kultivator")
P["ketua_sekte"] = row("C35", "D/T", "BANGSAWAN", ["sekte", "putih", "sekte", "emas"], ["L2"], ["P1"], pangkat=5,
                       tambahan=["jubah_luar", "pesta"], hiasan_l="mahkota", hiasan_p="buyao_emas", mulut=[["tegas", 1]],
                       tanda={"tanda_dao": 100}, sikap="tangan_belakang", jalan="bangsawan", kelas="kultivator")
P["pelayan_sekte"] = row("C55", {"A": 20, "M": 80}, "PELAYAN", ["sekte_pudar", "gading", "-", "-"], ["L7"], ["P2"],
                         prop="sapu", sikap="tangan_depan", jalan="tergesa")
P["pertapa"] = row("C20", "T", "RAHIB", ["abu", "kasar", "-", "kayu"], ["L4"], ["P6"], rambut_warna="putih",
                   rw={"jenggot_panjang": 100}, kubus=["alis_panjang"], alis_l=[["putih_panjang", 1]],
                   mata=[["tua", 1]], tambalan=True, topi="tasbih", sikap="doa", jalan="tua", kelas="rahib")
P["sisa_jiwa"] = row("C40", "D/T", [["KAFAN", 60], ["BANGSAWAN", 40]], ["putih", "abu", "perak", "perak"], ["L2"], ["P6"],
                     kulit="arwah", rambut_warna="arwah", pudar=True, mata=[["tua", 1]], suasana_mata="tutup_tenang",
                     sikap="", jalan="arwah", kelas="rahib")
P["tetua_iblis"] = row("C30", "D/T", "IBLIS", ["hitam", "darah", "darah", "tulang"], ["L4"], ["P6"],
                       tambahan=["jubah_luar"], rambut_warna="merah_tua", iris="#A01A1A", mata=[["tajam", 1]],
                       topi="jubah_iblis (busana 40)", sikap="lipat_tangan", jalan="iblis", kelas="iblis")
P["tawanan"] = row("C45", "M/D", "COMPANG", ["kasar_kotor", "abu", "-", "-"], ["L8"], ["P5"], luka=2,
                   mulut=[["datar", 1]], ekspresi={"sedih": 100}, prop="rantai_tangan", sikap="tunduk", jalan="tergesa")
P["pengemis"] = row("C40", {"A": 30, "T": 70}, "COMPANG", ["kasar", "abu", "-", "-"], ["L8"], ["P5", "P7"],
                    tanda={"bekas_luka": 30}, prop="mangkuk", sikap="tunduk", jalan="tua_tongkat")
P["pendeta_tao"] = row("C30", "D/T", "TAO", ["nila", "gading", "gading", "perak"], ["L6", "L1"], ["P1"], hiasan_l="guan_kayu",
                       hiasan_p="tusuk_kayu", mata=[["tua", 50], ["almond", 50]], prop="kebut", sikap="tangan_lengan",
                       jalan="rahib", kelas="rahib")

# ---------------------------------------------------------------------- C. themed cities (38.T)
P["syahbandar"] = row("C20", "D", "PEJABAT", ["laut", "gading", "emas", "emas"], ["L1"], ["P1"], buzi="bangau",
                      tema="pelabuhan", topi="topi_syahbandar", topi_menutup=True, prop="papan_catatan",
                      sikap="tangan_belakang", jalan="bangsawan", kelas="bangsawan")
P["petugas_bea"] = row("C30", "M/D", "PEJABAT", ["sutra_biru", "gading", "lak", "perunggu"], ["L1"], ["P3"], buzi="koin",
                       tema="pelabuhan", topi="topi_pejabat", topi_menutup=True, prop="kuas", sikap="tangan_depan",
                       jalan="pedagang")
P["pelaut"] = row("C15", "M/D", "LAUT", ["laut", "putih", "-", "-"], ["L4", "L8"], ["P5"], tema="pelabuhan",
                  topi="rompi_bergaris+kalung_gigi_hiu", prop="tali", sikap="berkacak", jalan="gagah")
P["nakhoda_laut"] = row("C20", "D/T", "PENDEK", ["laut", "gading", "emas", "emas"], ["L4"], ["P4"], tambahan=["jubah_luar"],
                        rw={"jenggot_penuh": 100}, tema="pelabuhan", topi="mantel_nakhoda (busana 40)",
                        prop="teropong", sikap="berkacak", jalan="gagah")
P["penjaga_suar"] = row("C30", "D/T", "PENDEK", ["kasar", "gading", "lak", "perunggu"], ["L7"], ["P5"], tema="pelabuhan",
                        topi="kain_kepala", topi_menutup=True, prop="lentera_tangan", sikap="santai", jalan="tua")
P["tukang_kapal"] = row("C10", "D", "PENDEK", ["kasar", "ter", "-", "besi"], ["L7"], ["P4"], tambahan=["kerja", "celemek"],
                        tema="pelabuhan", topi="celemek_ter (busana 40)", prop="palu_dempul", sikap="berkacak",
                        jalan="santai")
P["penjual_ikan"] = row("C60", "D", "PETANI", ["laut", "gading", "-", "-"], ["L7"], ["P5"], tambahan=["celemek"],
                        tema="pelabuhan", topi="kain_kepala", topi_menutup=True, prop="ikan_tali", sikap="tangan_depan",
                        jalan="pedagang")
P["pedagang_seberang"] = row("C25", "D", "GURUN", ["pasir", "putih", "emas", "emas"], ["L4"], ["P5"],
                             rw={"jenggot_penuh": 100}, tema="pelabuhan", topi="serban_seberang", topi_menutup=True,
                             prop="sempoa", sikap="tangan_lengan", jalan="pedagang")
P["pendeta_laut"] = row("C50", "D/T", "TAO", ["teal", "putih", "perak", "perak"], ["L6"], ["P6"], tema="pelabuhan",
                        topi="mahkota_laut", topi_menutup=True, prop="kebut", sikap="doa", jalan="rahib", kelas="rahib")
P["tuan_benteng"] = row("L", "D", "BANGSAWAN", ["pasir", "lak", "emas", "emas"], ["L1"], None, tambahan=["jubah_luar"],
                        rw={"jenggot_penuh": 100}, tema="oasis", topi="serban_emas", topi_menutup=True,
                        sikap="tangan_belakang", jalan="bangsawan", kelas="bangsawan")
P["kafilah_bashi"] = row("C20", "D", "GURUN", ["pasir", "cokelat", "lak", "perunggu"], ["L4"], ["P5"], tema="oasis",
                         topi="topi_bulu_lebar", topi_menutup=True, prop="cambuk_kafilah", sikap="berkacak", jalan="gagah")
P["penuntun_unta"] = row("C20", "M/D", "GURUN", ["pasir", "gading", "-", "-"], ["L7"], ["P5"], tema="oasis",
                         topi="kerudung_pasir", topi_menutup=True, prop="tali", sikap="santai", jalan="santai")
P["pedagang_bazar"] = row("C45", "D", "SAUDAGAR", ["lak", "pasir", "emas", "emas"], ["L4"], ["P5"], tema="oasis",
                          topi="serban_seberang 50 %", prop="kantong_uang", sikap="tangan_depan", jalan="pedagang")
P["pendeta_air"] = row("C55", "D/T", "TAO", ["biru_langit", "putih", "perak", "perak"], ["L6"], ["P6"], tema="oasis",
                       prop="kendi", sikap="doa", jalan="rahib", kelas="rahib")
P["pelukis_gua"] = row("C40", "M/D", "SARJANA", ["gading", "pasir", "tinta", "-"], ["L4"], ["P3"], tambahan=["kerja"],
                       tema="oasis", prop="kuas_besar", sikap="santai", jalan="santai")
P["penari_gurun"] = row("P", "M", "HANFU_P", ["lak", "emas", "emas", "emas"], None, ["P6"], hiasan_p="bunga_plum",
                        tambahan=["selendang"], tema="oasis", topi="selendang_penari (busana 40)", melayang=False,
                        sikap="tangan_depan", jalan="penari")
P["pengawal_gurun"] = row("C20", "M/D", "PENJAGA", ["pasir", "cokelat", "lak", "perunggu"], ["L1"], ["P4"],
                          tambahan=["zirah"], tema="oasis", topi="helm_runcing", topi_menutup=True, prop="tombak",
                          sikap="tombak", jalan="militer", kelas="penjaga")
P["nyonya_sutra"] = row("P", "D", "BANGSAWAN", ["ungu_tua", "merah_muda", "emas", "emas"], None, ["P1"],
                        hiasan_p="buyao_emas", tema="air", prop="kipas_sutra", sikap="angkuh", jalan="anggun",
                        kelas="bangsawan")
P["penenun_sutra"] = row("C80", "M/D", "HANFU", ["merah_muda", "gading", "lak", "-"], ["L7"], ["P3"], tambahan=["kerja"],
                         tema="air", sikap="tangan_depan", jalan="anggun")
P["pemelihara_ulat"] = row("C70", "M/D", "PETANI", ["hijau_teh", "gading", "-", "-"], ["L7"], ["P5"], tambahan=["caping"],
                           tema="air", prop="keranjang", sikap="santai", jalan="santai")
P["ahli_teh"] = row("C50", "D/T", "SARJANA", ["hijau_teh", "gading", "emas", "perunggu"], ["L1"], ["P1"], tema="air",
                    topi="jubah_teh (busana 40)", prop="teko", sikap="tangan_lengan", jalan="santai")
P["pemetik_teh"] = row("C75", "M/D", "PETANI", ["teh", "gading", "-", "-"], ["L7"], ["P5"], tambahan=["caping"],
                       tema="air", prop="keranjang_teh", sikap="santai", jalan="santai")
P["pendayung"] = row("C30", "M/D", "PETANI", ["sutra_biru", "gading", "-", "-"], ["L7"], ["P5"], tambahan=["caping"],
                     tema="air", prop="dayung_yuloh", sikap="santai", jalan="gagah")
P["sastrawan"] = row("C35", "M/D", "SARJANA", ["biru_langit", "gading", "tinta", "perak"], ["L2"], ["P3"], tema="air",
                     topi="jubah_teh biru (busana 40)", prop="kipas_lipat", sikap="tangan_belakang", jalan="santai")
P["kepala_biara"] = row("L", "T", "KASAYA", ["kuning_tanah", "gading", "lak", "emas"], ["L5"], None, buddha=True,
                        rambut_warna="putih", rw={"jenggot_panjang": 100}, mata=[["tua", 1]], tema="gunung",
                        prop="tongkat_cincin", sikap="doa", jalan="rahib", kelas="rahib")
P["biksu_gunung"] = row("C15", "M/D", "RAHIB", ["abu", "gading", "kuning_tanah", "kayu"], ["L5"], ["L5"], buddha=True,
                        tema="gunung", topi="tasbih", prop="tongkat_bambu", sikap="doa", jalan="rahib", kelas="rahib")
P["penjaga_lift"] = row("C20", "D", "PENDEK", ["kasar", "gading", "lak", "besi"], ["L7"], ["P4"], tema="gunung",
                        topi="kain_kepala", topi_menutup=True, sikap="berkacak", jalan="santai")
P["kuli_gunung"] = row("C10", "M/D", "PETANI", ["kasar", "kasar", "-", "-"], ["L7"], ["P5"], tema="gunung",
                       topi="kain_kepala", topi_menutup=True, prop="rangka_punggung", sikap="santai", jalan="kuli")
P["pemetik_herba"] = row("C45", "M/D", "PENDEK", ["teh", "kasar", "-", "perunggu"], ["L7"], ["P5"], tema="gunung",
                         topi="tali_melingkar", prop="sabit_herba", sikap="santai", jalan="santai")
P["peziarah"] = row("C50", "D/T", "RAHIB", ["kuning_tanah", "gading", "-", "-"], ["L7", "L6"], ["P7"],
                    tambahan=["ikat_kuning", "buntalan"], tema="gunung", prop="tas_dupa", sikap="doa",
                    jalan="tua_tongkat", kelas="rahib")
P["penguasa_iblis"] = row("C30", "D", "IBLIS", ["hitam", "darah", "darah", "emas"], ["L4"], ["P6"],
                          tambahan=["jubah_luar", "kerah_tinggi"], rambut_warna="merah_tua", iris="#A01A1A",
                          mata=[["tajam", 1]], tema="iblis", topi="mahkota_tanduk", topi_menutup=True, sikap="angkuh",
                          jalan="iblis", kelas="iblis")
P["penegak_iblis"] = row("C25", "D", "IBLIS", ["hitam", "darah", "besi", "besi"], ["L1"], ["P4"], tambahan=["zirah"],
                         mata=[["tajam", 1]], tema="iblis", topi="topeng_besi+rantai_bahu", sikap="lipat_tangan",
                         jalan="militer", kelas="iblis")
P["jagal_arena"] = row("C10", "D", "PENDEK", ["darah", "kasar", "-", "besi"], ["L5", "L7"], ["P4"], lengan_telanjang=True,
                       tema="iblis", topi="rantai_bahu", sikap="berkacak", jalan="gagah", kelas="iblis")
P["gladiator_iblis"] = row("C30", "M/D", "TEMPUR", ["hitam", "darah", "besi", "besi"], ["L3"], ["P4"],
                           tambahan=["bahu_kiri"], tema="iblis", topi="zirah_gladiator (busana 40)", sikap="berkacak",
                           jalan="gagah", kelas="iblis")
P["penjaga_kitab_terlarang"] = row("C40", "T", "TAO", ["hitam", "gading", "ungu_tua", "perak"], ["L6"], ["P7"],
                                   mata=[["tua", 1]], tema="iblis", topi="penutup_mata", prop="kitab",
                                   sikap="tangan_lengan", jalan="tua", kelas="iblis")
P["pembunuh_bayaran"] = row("C40", "M/D", "TEMPUR", ["hitam", "abu", "-", "besi"], ["L3"], ["P4"], tambahan=["penyamaran"],
                            mata=[["tajam", 1]], tema="iblis", topi="tudung_pembunuh", topi_menutup=True,
                            prop="pisau_ganda", sikap="lipat_tangan", jalan="iblis", kelas="iblis")
P["warga_iblis"] = row("C50", {"A": 10, "M": 30, "D": 45, "T": 15}, [["HANFU", 60], ["PETANI", 40]],
                       ["ungu_tua", "abu", "darah", "-"], None, None, rambut_merah=10, tema="iblis", sikap="santai",
                       jalan="santai")
P["pemburu_lurus"] = row("C35", "M/D", "TEMPUR", ["putih", "sutra_biru", "perak", "perak"], ["L3"], ["P4"], tema="iblis",
                         topi="jubah_pemburu_lurus (busana 40)", sikap="gagang", jalan="", kelas="kultivator")

# ---------------------------------------------------------------------- D. Dimension D, rogues, envoy, crowd
P["d.meng_po"] = row("P", "T", "HANFU_P", ["cokelat", "gading", "-", "-"], None, ["P7"], tambahan=["kerja"],
                     mata=[["tua", 1]], topi="MENG_PO", topi_menutup=True, sikap="tangan_depan", jalan="tua_tongkat")
P["d.hakim"] = row("L", "D/T", "HAKIM_BAWAH", ["darah", "hitam", "emas", "emas"], ["L1"], None,
                   rw={"jenggot_penuh": 50, "jenggot_panjang": 50}, mulut=[["tegas", 1]], topi="JUDGE",
                   topi_menutup=True, sikap="tangan_belakang", jalan="bangsawan", kelas="bangsawan")
P["d.raja_yama"] = row("L", "D", "HAKIM_BAWAH", ["hitam", "darah", "emas", "emas"], ["L1"], None,
                       tambahan=["jubah_luar", "pesta"], rw={"jenggot_penuh": 100}, alis_l=[["tebal", 1]],
                       mulut=[["tegas", 1]], topi="YAMA", topi_menutup=True, sikap="angkuh", jalan="bangsawan",
                       kelas="bangsawan")
P["d.arwah"] = row("C50", {"A": 15, "M": 30, "D": 35, "T": 20}, "KAFAN", ["putih", "abu", "-", "-"], None, None,
                   kulit="arwah", rambut_warna="arwah", pudar=True, sikap="", jalan="arwah", kelas="rahib")
P["d.penjaga_laut"] = row("C25", "M/D", "PENJAGA", ["teal", "laut", "mutiara", "perak"], ["L1"], ["P4"],
                          tambahan=["zirah"], topi="GUARD", topi_menutup=True, sikap="tombak", jalan="militer",
                          kelas="penjaga")
P["d.pertapa_laut"] = row("L", "T", "TAO", ["laut", "gading", "perak", "perak"], ["L6"], None, rw={"jenggot_panjang": 100},
                          mata=[["tua", 1]], topi="HERMIT", topi_menutup=True, sikap="tangan_lengan", jalan="tua",
                          kelas="rahib")
P["d.dewa_penglai"] = row("C40", "D", "ABADI", ["putih", "biru_langit", "emas", "emas"], ["L2"], ["P6"], melayang=True,
                          topi="PENGLAI", topi_menutup=True, sikap="tangan_lengan", jalan="bangsawan", kelas="kultivator")
P["d.nelayan"] = row("C20", "D/T", "PETANI", ["laut", "kasar", "-", "-"], ["L7"], ["P5"], kulit_gelap=40, topi="FISHER",
                     topi_menutup=True, sikap="santai", jalan="santai")
P["d.pedagang"] = row("C40", "D", "SAUDAGAR", ["teal", "pasir", "emas", "perunggu"], ["L1"], ["P3"], topi="MERCHANT",
                      topi_menutup=True, sikap="tangan_lengan", jalan="pedagang")
for i in range(8):
    P["liar.%d" % i] = row("C30", "M/D", [["IBLIS", 60], ["TEMPUR", 40]], ["hitam", LIAR[i], "darah", "besi"],
                           ["L3", "L4"], ["P4", "P6"], mata=[["tajam", 1]], mulut=[["tegas", 1]], sikap="lipat_tangan",
                           jalan="iblis", kelas="iblis")
P["utusan_langit"] = row("L", "D", "ABADI", ["emas_terang", "putih", "emas", "emas"], ["L2"], None, hiasan_l="guan_emas",
                         tambahan=["zirah"], mulut=[["tegas", 1]], sikap="tangan_belakang", jalan="militer",
                         kelas="kultivator")
P["penonton.utusan"] = row("C40", "M/D", "HANFU", ["sekte", "gading", "sekte", "perunggu"], None, None,
                           sikap="", jalan="", kelas="kultivator")
P["penonton.warga"] = row("C50", {"A": 15, "D": 60, "T": 25}, [["PETANI", 50], ["HANFU", 50]],
                          ["kasar", "gading", "cokelat", "perunggu"], None, None, sikap="", jalan="")

# ---------------------------------------------------------------------- disciples by rank (40.4.2)
P["murid.0"] = row("C50", "M", "PENDEK", ["kasar", "gading", "abu", "perunggu"], ["L7", "L1"], ["P2", "P5"],
                   pangkat=0, hiasan_l="kain", hiasan_p="kain", kaki="jerami", sikap="tunduk", jalan="",
                   kelas="kultivator", murid=True)
P["murid.1"] = row("C50", "M", [["PENDEK", 100]], ["dao", "gading", "pangkat", "perunggu"], ["L1", "L3"], ["P2", "P4"],
                   gaya_p=[["HANFU_P_PENDEK", 100]], pangkat=1, hiasan_l="kain_tepi", hiasan_p="kain_tepi",
                   tambahan=["sabuk_kubus"], sikap="santai", jalan="", kelas="kultivator", murid=True)
P["murid.2"] = row("C50", "M", "HANFU", ["dao", "gading", "pangkat", "perak"], ["L1", "L2"], ["P3", "P4"], pangkat=2,
                   hiasan_l="guan_kayu", hiasan_p="tusuk_perak", tambahan=["liontin_giok"], sikap="santai", jalan="",
                   kelas="kultivator", murid=True)
P["murid.3"] = row("C50", "M/D", "HANFU", ["dao", "gading", "pangkat", "perak"], ["L2"], ["P1", "P3"], pangkat=3,
                   hiasan_l="guan_perak", hiasan_p="buyao_perak", tambahan=["liontin_giok"], selendang_p=True,
                   sikap_l="tangan_belakang", sikap_p="tangan_depan", jalan="", kelas="kultivator", murid=True)
P["murid.4"] = row("C50", "D", "HANFU", ["dao_gelap", "gading", "pangkat", "emas"], ["L2"], ["P1"], pangkat=4,
                   tao_dao=True, hiasan_l="guan_giok", hiasan_p="buyao_emas", tambahan=["jubah_luar", "kerah_tinggi_l"],
                   selendang_p=True, sikap="tangan_belakang", jalan="bangsawan", kelas="kultivator", murid=True)
P["murid.5"] = row("C50", "D", "BANGSAWAN", ["dao", "putih", "pangkat", "emas"], ["L2"], ["P1"], pangkat=5,
                   hiasan_l="mahkota", hiasan_p="buyao_emas_bunga", tambahan=["jubah_luar", "pesta"], selendang_p=True,
                   sikap="tangan_belakang", jalan="bangsawan", kelas="kultivator", murid=True)
P["murid.calon"] = row("C50", "A/M", "PETANI", ["kasar", "gading", "-", "-"], ["L8", "L7"], ["P5", "P2"],
                       gaya_anak=[["ANAK", 100]], tambahan=["buntalan"], hiasan_l="kain", sikap="tunduk", jalan="",
                       kelas="sipil", murid=True)
P["murid.saingan"] = row("C40", "M", "TEMPUR", ["hitam", "dao_kain", "emas", "besi"], ["L3"], ["P1"],
                         tambahan=["jubah_luar"], kubus=["ikat_dahi_ekor"], hiasan_p="buyao_perak", mata=[["tajam", 1]],
                         sikap="lipat_tangan", jalan="", kelas="kultivator", murid=True)

# ---------------------------------------------------------------------- schedule-group fallbacks (38.N.2)
P["kelompok.PEKERJA"] = row("C40", {"M": 30, "D": 50, "T": 20}, [["PENDEK", 50], ["PETANI", 50]],
                            ["kasar", "gading", "cokelat", "besi"], ["L7", "L8"], ["P4", "P5"], tambahan=["kerja"],
                            sikap="santai", jalan="santai")
P["kelompok.PEDAGANG"] = row("C40", "D", "SAUDAGAR", ["teh", "gading", "emas", "perunggu"], ["L1", "L4"], ["P3", "P5"],
                             sikap="tangan_lengan", jalan="pedagang")
P["kelompok.PENJAGA"] = row("C20", "M/D", "PENJAGA", ["nila", "kasar", "lak", "besi"], ["L1"], ["P4"],
                            mata=[["tajam", 1]], prop="tombak", sikap="tombak", jalan="militer", kelas="penjaga")
P["kelompok.ANAK"] = row("C50", "A", "ANAK", ["cerah", "gading", "lak", "-"], ["A1", "A2"], ["A1", "P2"],
                         mata=[["anak", 1]], bentuk=[["bulat", 1]], sikap="anak", jalan="anak_lompat")
P["kelompok.TETUA"] = row("C50", "T", "HANFU", ["cokelat", "gading", "kayu", "kayu"], ["L6"], ["P7"], mata=[["tua", 1]],
                          rw={"jenggot_panjang": 50}, sikap="tua", jalan="tua")
P["kelompok.KULTIVATOR"] = row("C40", "M/D", "TEMPUR", ["dao", "gading", "-", "besi"], ["L2", "L3"], ["P3", "P4"],
                               sikap="gagang", jalan="", kelas="kultivator")
P["kelompok.CENDEKIA"] = row("C40", "D/T", "SARJANA", ["nila", "gading", "tinta", "perunggu"], ["L1"], ["P1", "P3"],
                             prop="kitab", sikap="tangan_lengan", jalan="santai")
P["kelompok.RAHIB"] = row("C20", "M/D/T", "RAHIB", ["abu", "gading", "kuning_tanah", "kayu"], ["L5"], ["L5"],
                          buddha=True, sikap="doa", jalan="rahib", kelas="rahib")
P["kelompok.PEJABAT"] = row("C25", "D/T", "PEJABAT", ["lak", "gading", "emas", "emas"], ["L1"], ["P1"], buzi="bangau",
                            sikap="tangan_lengan", jalan="bangsawan", kelas="bangsawan")
P["kelompok.KHUSUS"] = row("C40", "M/D", "HANFU", ["sutra_biru", "gading", "emas", "perunggu"], ["L2"], ["P3"],
                           sikap="", jalan="")
P["kelompok.SIPIL"] = row("C50", {"M": 25, "D": 55, "T": 20}, [["HANFU", 50], ["PENDEK", 30], ["PETANI", 20]],
                          ["kasar", "gading", "cokelat", "perunggu"], ["L1", "L7"], ["P2", "P3", "P5"], sikap="",
                          jalan="")

# ---------------------------------------------------------------------- life paths (40.4.3), added on top of rank rows
J = {
    "jalan.pedang": {"rambut_l": ["L3"], "rambut_p": ["P4"], "kubus": ["ikat_dahi_ekor", "pelindung_lengan"],
                     "mata": [["tajam", 1]], "tanda": {"bekas_luka": 30}, "gaya_tanpa_seragam": "TEMPUR",
                     "prop": PROP["pedang_kayu"], "sikap": 112},
    "jalan.alkemis": {"tanda": {"coreng_jelaga": 100}, "tambahan": ["kerja"], "kubus": ["labu", "kantong"],
                      "prop": PROP["botol_pil"], "sikap": 110},
    "jalan.formasi": {"tanda": {"tinta_jari": 100}, "tepi_naik": 1, "kubus": ["kantong"], "prop": PROP["piringan_formasi"],
                      "sikap": 110},
    "jalan.pertapa": {"rambut_l": ["L4"], "rambut_p": ["P6"], "tambalan": True, "prop": PROP["tasbih_tangan"], "sikap": 120},
    "jalan.petualang": {"tambahan": ["bepergian_kota"], "kulit_gelap": 100, "tanda": {"bekas_luka": 20},
                        "kubus": ["buntalan", "labu", "jubah_luar"], "sikap": 117},
    "jalan.pemburu": {"kubus": ["kerah_bulu", "kantong"], "tanda": {"bekas_cakar": 100}, "prop": PROP["trofi_taring"],
                      "sikap": 117},
    "jalan.pedagang": {"kubus": ["kantong"], "prop": PROP["sempoa"], "sikap": 110},
    "jalan.cendekia": {"prop": PROP["gulungan"], "sikap": 108},
    "jalan.penjaga": {"tambahan": ["zirah"], "kubus": ["bahu_zirah", "rok_zirah"], "prop": PROP["tombak"], "sikap": 111},
    "jalan.iblis": {"rambut_warna": "merah_tua", "iris": "#A01A1A", "kain_tepi": "#6A1420", "kubus": ["rumbai_iblis"],
                    "gaya_inti_emas": "IBLIS", "sikap": 109},
    "jalan.tempa": {"tanda": {"coreng_jelaga": 100}, "tambahan": ["celemek"], "kubus": ["pelindung_lengan"], "sikap": 117},
    "jalan.tabib": {"mata": [["lembut", 1]], "kain2": "gading", "prop": PROP["kotak_obat"], "sikap": 121},
    "jalan.petani_roh": {"kulit_gelap": 100, "tambahan": ["kerja"], "sikap": 0},
    "jalan.musisi": {"kubus_p": ["bunga_rambut"], "alis_p": [["daun_willow", 1]], "prop": PROP["seruling"], "sikap": 110},
    "jalan.bangsawan": {"gaya_tanpa_seragam": "BANGSAWAN", "tanda_p": {"huadian": 100}, "kubus": ["liontin_giok"],
                        "sikap": 119},
}


HIASAN = ["", "kain", "kain_tepi", "guan_kayu", "guan_perak", "guan_giok", "guan_emas", "mahkota", "tusuk_kayu",
          "tusuk_perak", "tusuk_emas", "buyao_perak", "buyao_emas", "buyao_emas_bunga", "bunga_plum", "bunga_peony",
          "bunga_teratai"]


def expand_cubes(names):
    out = []
    for n in names:
        if n in KELOMPOK_KUBUS:
            out.extend(KELOMPOK_KUBUS[n])
        elif n in UV_BUSANA or n in UV_RAMBUT:
            out.append(n)
        else:
            raise ValueError("unknown cube " + n)
    return out


def weights(v):
    return [[n, int(w)] for n, w in v] if v else []


def canon(key, r):
    """The fixed schema every row is written in (Resep.Baris in Java reads exactly these fields)."""
    def split_gaya(lst, fem):
        out = []
        for name, w in lst:
            if name == "HANFU":
                name = "HANFU_P" if fem else "HANFU_L"
            out.append([name, w])
        return out
    sik = r.get("sikap", 0)
    prop_acak = {}
    for name, pct in r.get("prop_acak", {}).items():
        prop_acak[str(PROP[name])] = pct
    c = {
        "k": r["k"], "u": r["u"],
        "gaya_l": split_gaya(r["gaya"], False),
        "gaya_p": split_gaya(r.get("gaya_p", r["gaya"]), True),
        "gaya_anak": r.get("gaya_anak", []),
        "kain": r["kain"],
        "rambut_l": r.get("rambut_l", []), "rambut_p": r.get("rambut_p", []),
        "hiasan_l": r.get("hiasan_l", ""), "hiasan_p": r.get("hiasan_p", ""),
        "mata": weights(r.get("mata")), "alis_l": weights(r.get("alis_l")), "alis_p": weights(r.get("alis_p")),
        "mulut": weights(r.get("mulut")), "bentuk": weights(r.get("bentuk")),
        "tanda": r.get("tanda", {}), "tanda_p": r.get("tanda_p", {}), "rw": r.get("rw", {}),
        "tambahan": r.get("tambahan", []), "tambahan_acak": r.get("tambahan_acak", {}),
        "kubus": expand_cubes(r.get("kubus", [])), "kubus_p": expand_cubes(r.get("kubus_p", [])),
        "kulit": r.get("kulit", ""), "kulit_gelap": r.get("kulit_gelap", 0),
        "rambut_warna": r.get("rambut_warna", ""), "rambut_merah": r.get("rambut_merah", 0), "iris": r.get("iris", ""),
        "buzi": r.get("buzi", ""), "kaki": r.get("kaki", ""), "kepala": r.get("kepala", ""),
        "melayang": bool(r.get("melayang", False)), "pudar": bool(r.get("pudar", False)),
        "buddha": bool(r.get("buddha", False)), "tambalan": bool(r.get("tambalan", False)),
        "luka": r.get("luka", 0), "lengan_telanjang": bool(r.get("lengan_telanjang", False)),
        "tanda_ranah": bool(r.get("tanda_ranah", False)), "selendang_p": bool(r.get("selendang_p", False)),
        "tao_dao": bool(r.get("tao_dao", False)), "murid": bool(r.get("murid", False)),
        "pangkat": r.get("pangkat", -1), "tema": r.get("tema", ""),
        "topi": r.get("topi", ""), "topi_menutup": bool(r.get("topi_menutup", False)),
        "mata_tetap": r.get("suasana_mata", ""), "ekspresi": r.get("ekspresi", {}),
        "prop": r.get("prop", 0), "prop_acak": prop_acak,
        "sikap_l": SIKAP[r["sikap_l"]] if "sikap_l" in r else sik,
        "sikap_p": SIKAP[r["sikap_p"]] if "sikap_p" in r else sik,
        "jalan": r.get("jalan", ""), "kelas": r.get("kelas", "sipil"),
    }
    for h in (c["hiasan_l"], c["hiasan_p"]):
        assert h in HIASAN, (key, h)
    for t in c["tambahan"] + list(c["tambahan_acak"]):
        assert t in TAMBAHAN or t in ("sabuk_kubus", "kerah_tinggi_l"), (key, t)
    return c


def canon_jalan(key, r):
    c = {
        "rambut_l": r.get("rambut_l", []), "rambut_p": r.get("rambut_p", []),
        "kubus": expand_cubes(r.get("kubus", [])), "kubus_p": expand_cubes(r.get("kubus_p", [])),
        "mata": weights(r.get("mata")), "alis_p": weights(r.get("alis_p")),
        "tanda": r.get("tanda", {}), "tanda_p": r.get("tanda_p", {}),
        "tambahan": r.get("tambahan", []), "kulit_gelap": r.get("kulit_gelap", 0),
        "rambut_warna": r.get("rambut_warna", ""), "iris": r.get("iris", ""), "kain_tepi": r.get("kain_tepi", ""),
        "kain2": r.get("kain2", ""), "tambalan": bool(r.get("tambalan", False)), "tepi_naik": r.get("tepi_naik", 0),
        "gaya_tanpa_seragam": r.get("gaya_tanpa_seragam", ""), "gaya_inti_emas": r.get("gaya_inti_emas", ""),
        "prop": r.get("prop", 0), "sikap": r.get("sikap", 0),
    }
    for t in c["tambahan"]:
        assert t in TAMBAHAN, (key, t)
    return c


def check(rows):
    problems = []
    for key, r in rows.items():
        for g in r["gaya"]:
            name = g[0]
            if name not in GAYA and name not in ("HANFU", "HANFU_P_PENDEK"):
                problems.append("%s: unknown style %s" % (key, name))
        for hk in ("rambut_l", "rambut_p"):
            for h in r.get(hk, []):
                if h not in HAIR_STYLES:
                    problems.append("%s: unknown hair %s" % (key, h))
        for tok in r["kain"]:
            if tok in WARNA or tok.startswith("#") or tok in ("dao", "dao_gelap", "dao_kain", "sekte", "sekte_pudar",
                                                               "klan", "cerah", "kelompok", "-", "pangkat") \
                    or tok in LOGAM_MATERIAL:
                continue
            problems.append("%s: unknown colour token %s" % (key, tok))
    return problems


SOSOKGEN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "java", "dev", "kultivator", "sosok",
                        "SosokGen.java")


def selaras_kebijakan(rows):
    """The K/U columns follow 40I's server table SosokGen.java (the server spawns the people; the recipe only documents
    them for the previews and cek_sosok.py): "C<p>"/"L"/"P" and the age weights in percent."""
    if not os.path.exists(SOSOKGEN):
        return rows
    src = open(SOSOKGEN, encoding="utf-8").read()
    for m in re.finditer(r'(npc|baris)\("([^"]+)", (\d+), "([AMDT0-9]+)"\)', src):
        row = rows.get("peran." + m.group(2))
        if row is None:
            continue
        pct = int(m.group(3))
        row["k"] = "L" if pct == 0 else ("P" if pct == 100 else "C%d" % pct)
        parts = re.findall(r"([AMDT])(\d*)", m.group(4))
        used = sum(int(n) for _, n in parts if n)
        free = [a for a, n in parts if not n]
        u = {}
        for a, n in parts:
            if n:
                u[a] = int(n)
            else:
                i = free.index(a)
                u[a] = (100 - used) // len(free) + (1 if i < (100 - used) % len(free) else 0)
        row["u"] = u
    return rows


def main():
    problems = check(P)
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    data = {
        "versi": 1,
        "tabel": {"lin": LIN, "srgb": SRGB},
        "warna": WARNA,
        "kulit": [{"nama": n, "rgb": c, "bobot": w} for n, c, w in KULIT],
        "rambut_warna": {k: {"rgb": v[0], "bobot": v[1]} for k, v in RAMBUT_WARNA.items()},
        "iris": [{"rgb": c, "bobot": w} for c, w in IRIS],
        "dao": DAO,
        "ranah": REALM,
        "tepi_pangkat": RANK_TRIM,
        "lencana_pangkat": RANK_BADGE,
        "logam": LOGAM_MATERIAL,
        "klan": KLAN,
        "cerah": CERAH,
        "sikap": SIKAP,
        "profil_jalan": JALAN,
        "gaya_rambut": GAYA_RAMBUT,
        "gaya_busana": GAYA_BUSANA,
        "kelompok_kubus": KELOMPOK_KUBUS,
        "tambahan": TAMBAHAN,
        "kubus_rambut": list(UV_RAMBUT),
        "kubus_busana": list(UV_BUSANA),
        "peran": selaras_kebijakan({"peran." + k: canon(k, v) for k, v in P.items()}),
        "jalan": {k: canon_jalan(k, v) for k, v in J.items()},
    }
    os.makedirs(os.path.dirname(RESEP), exist_ok=True)
    with open(RESEP, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"), sort_keys=False)
        f.write("\n")
    print("resep.json: %d look rows, %d path rows, %d bytes" % (len(P), len(J), os.path.getsize(RESEP)))


if __name__ == "__main__":
    main()
