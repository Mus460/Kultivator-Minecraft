"""Previews of the Sosok composer (DESIGN 40.3.11, module 40K). Not shipped in the jar.

    python -I tools/gen_sosok_pratinjau.py                 all sheets below into tools/keluaran/sosok/
    python -I tools/gen_sosok_pratinjau.py --benih <n> <rupa> [kelamin usia postur]
                                                           one atlas of a seed (default man, adult, build 3)
    python -I tools/gen_sosok_pratinjau.py --paket <Sosok.pack()> <rupa key text>
                                                           the same inputs as "/sosok dump benih <packed> <rupa>" in game
                                                           (realm 1, Dao/rank/sect from the key text): identical pixels

(a) rambut_<L|P|A>.png   every hair style on a head (3/4 front, back, side views)
(b) busana_<n>.png       every garment style on a body: flat front + back and the 3D model with its cubes
(c) peran_<n>.png        8 random people per look row (flat front views), 12 rows per sheet
(d) galeri.png           contact sheet of 16 x 8 random people (3D)
(e) contoh.png           the worked examples of 40.4.4 in 3D at 8x
    wajah.png            16 eye and 16 mouth strips of four faces at 16x
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sosok_rupa as R  # noqa: E402
from sosok_lib import KELUARAN, SheetCache, susun, write_argb, figure, sheet_compose, SplitMix64, scale_up, fnv32  # noqa: E402
from sosok_iso import render_look  # noqa: E402
from gen_sosok_bagian_busana import GAYA  # noqa: E402
from gen_sosok_bagian_rambut import HAIR_STYLES  # noqa: E402

CACHE = SheetCache()
BG = 0xFF26262E


def atlas_of(look):
    return susun(look, CACHE)


def iso(look, px=5, yaw=-28, pitch=14):
    return render_look(atlas_of(look), look, yaw, pitch, px)


def out(name, w, h, px):
    path = os.path.join(KELUARAN, name)
    write_argb(path, w, h, px)
    print("  wrote", os.path.relpath(path))


def hair_sheets():
    for group in ("L", "P", "A"):
        styles = [h for h in HAIR_STYLES if h.startswith(group)]
        rows = []
        for st in styles:
            spec = R.resep()["gaya_rambut"][st]
            kel = 1 if "P" in spec["k"] and group != "L" else 0
            usia = {"A": 0, "M": 1, "D": 2, "T": 3}[spec["u"][-1] if group != "A" else "A"]
            if "D" in spec["u"]:
                usia = 2
            key = "murid.2" if group != "A" else "anak"
            look = R.resolve((kel, usia, 3, 0x1234 + len(rows)), None, R.masukan(key, realm=2, pangkat=2),
                             {"rambut": st})
            rows.append([iso(look, 5, -30, 10), iso(look, 5, 150, 10), iso(look, 5, -90, 10)])
        w, h, px = sheet_compose(rows, 1, 2, BG)
        out("rambut_%s.png" % group, w, h, px)


def busana_sheets():
    per = 6
    for n in range(0, len(GAYA), per):
        rows = []
        for gaya in GAYA[n:n + per]:
            cells = []
            for kel in (0, 1):
                look = R.resolve((kel, 2, 3, 0xB05A + n), None, R.masukan("kelompok.KHUSUS"), {"busana": gaya})
                at = atlas_of(look)
                f1 = figure(at, look["ramping"], "depan")
                f2 = figure(at, look["ramping"], "belakang")
                cells.append((f1[0] * 5, f1[1] * 5, scale_up(f1[0], f1[1], f1[2], 5)))
                cells.append((f2[0] * 5, f2[1] * 5, scale_up(f2[0], f2[1], f2[2], 5)))
                cells.append(render_look(at, look, -28, 14, 5))
            rows.append(cells)
        w, h, px = sheet_compose(rows, 1, 2, BG)
        out("busana_%d.png" % (n // per + 1), w, h, px)


def role_sheets():
    keys = [k[6:] for k in R.resep()["peran"]]
    per = 12
    for n in range(0, len(keys), per):
        rows = []
        for key in keys[n:n + per]:
            row = R.resep()["peran"]["peran." + key]
            rng = SplitMix64(fnv32(key))
            cells = []
            for i in range(8):
                kel = 1 if (row["k"] == "P" or (row["k"].startswith("C") and rng.below(100) < int(row["k"][1:]))) else 0
                ages = [[a, w] for a, w in row["u"].items()]
                usia = "AMDT".index(rng.pick(ages))
                look = R.resolve((kel, usia, rng.below(8), rng.below(1 << 26)), None,
                                 R.masukan(key, realm=rng.below(6), dao=rng.below(8), pangkat=rng.below(6)))
                f = figure(atlas_of(look), look["ramping"], "depan")
                cells.append(f)
            rows.append(cells)
        w, h, px = sheet_compose(rows, 4, 2, BG)
        out("peran_%02d.png" % (n // per + 1), w, h, px)


def gallery():
    keys = [k[6:] for k in R.resep()["peran"]]
    rng = SplitMix64(40)
    rows = []
    for r in range(8):
        cells = []
        for c in range(16):
            key = keys[rng.below(len(keys))]
            row = R.resep()["peran"]["peran." + key]
            kel = 1 if (row["k"] == "P" or (row["k"].startswith("C") and rng.below(100) < int(row["k"][1:]))) else 0
            usia = "AMDT".index(rng.pick([[a, w] for a, w in row["u"].items()]))
            look = R.resolve((kel, usia, rng.below(8), rng.below(1 << 26)), None, R.masukan(key, realm=rng.below(7),
                             dao=rng.below(8), pangkat=rng.below(6)))
            cells.append(iso(look, 3))
        rows.append(cells)
    w, h, px = sheet_compose(rows, 1, 2, BG)
    out("galeri.png", w, h, px)


EXAMPLES = [
    # (look key, sosok, inputs, extra gaya)  - 40.4.4
    ("murid.1", (1, 1, 3, 0x2A51C3), dict(realm=2, dao=2, pangkat=1)),
    ("murid.4", (0, 2, 4, 0x3B11A7), dict(realm=4, dao=1, pangkat=4)),
    ("penjaga", (1, 2, 3, 0x0C0FFE), dict(topi_menutup=True)),
    ("anak", (0, 0, 3, 0x00A1A1), {}),
    ("tetua_desa", (1, 3, 3, 0x7E7A), {}),
    ("murid.0", (0, 1, 2, 0x1234), dict(realm=1, pangkat=0, fisik=7)),
    ("ketua_sekte", (0, 3, 5, 0x5EC7), dict(realm=6, dao=7, pangkat=5)),
    ("d.arwah", (1, 2, 3, 0xA3A3), {}),
]


def examples():
    cells = []
    for key, sosok, kw in EXAMPLES:
        look = R.resolve(sosok, None, R.masukan(key, **kw))
        cells.append(iso(look, 8))
    w, h, px = sheet_compose([cells[:4], cells[4:]], 1, 2, BG)
    out("contoh.png", w, h, px)


def faces():
    rows = []
    for key, sosok, kw in EXAMPLES[:4]:
        look = R.resolve(sosok, None, R.masukan(key, **kw))
        at = atlas_of(look)
        eyes = [(8, 4, [at[(4 * (i // 8) + y) * 128 + 64 + 8 * (i % 8) + x] for y in range(4) for x in range(8)])
                for i in range(16)]
        mouths = [(8, 3, [at[(8 + 3 * (i // 8) + y) * 128 + 64 + 8 * (i % 8) + x] for y in range(3) for x in range(8)])
                  for i in range(16)]
        face = (8, 8, [at[(8 + y) * 128 + 8 + x] for y in range(8) for x in range(8)])
        rows.append([face] + eyes)
        rows.append([face] + mouths)
    w, h, px = sheet_compose(rows, 12, 1, BG)
    out("wajah.png", w, h, px)


def one(benih, key, kel=0, usia=2, postur=3):
    look = R.resolve((kel, usia, postur, benih), None, R.masukan(key))
    at = atlas_of(look)
    path = os.path.join(KELUARAN, "benih_%07x_%s.png" % (benih, key.replace(".", "_")))
    write_argb(path, 128, 128, at)
    print(path, "key %016x" % R.kunci(look))


def paket(packed, teks):
    """Mirror of 40R's /sosok dump benih: Sosok.unpack, RupaKunci text, realm 1, neutral Gaya."""
    parts = teks.split("|")
    key, sekte, dao, pangkat = parts[0], None, -1, -1
    for p in parts[1:]:
        k, _, v = p.partition("=")
        if k == "sekte":
            sekte = v
        elif k == "dao":
            dao = int(v)
        elif k == "pangkat":
            pangkat = int(v)
    kel, usia, postur, benih = packed & 1, (packed >> 1) & 3, (packed >> 3) & 7, (packed >> 6) & ((1 << 26) - 1)
    look = R.resolve((kel, usia, postur, benih), None, R.masukan(key, realm=1, dao=dao, pangkat=pangkat, sekte=sekte))
    at = atlas_of(look)
    path = os.path.join(KELUARAN, "paket_%08x_%s.png" % (packed & 0xFFFFFFFF, key.replace(".", "_")))
    write_argb(path, 128, 128, at)
    print(path, "key %016x" % R.kunci(look))


def main(argv):
    if len(argv) >= 3 and argv[0] == "--paket":
        paket(int(argv[1], 0), argv[2])
        return
    if len(argv) >= 3 and argv[0] == "--benih":
        extra = [int(v) for v in argv[3:6]]
        one(int(argv[1], 0), argv[2], *extra)
        return
    hair_sheets()
    busana_sheets()
    role_sheets()
    gallery()
    examples()
    faces()
    if CACHE.missing:
        print("missing sheets:", sorted(CACHE.missing))


if __name__ == "__main__":
    main(sys.argv[1:])
