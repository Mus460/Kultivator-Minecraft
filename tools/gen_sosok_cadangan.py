"""Fallback atlases of the Sosok composer (DESIGN 40.3.9, module 40K).

    python -I tools/gen_sosok_cadangan.py

Writes ``assets/kultivator/textures/sosok/cadangan/<l|p>_<usia 0-3>_<kelas>.png`` (48 ready 128 x 128 atlases: two sexes,
four age groups, six classes). They are drawn for bodies whose own atlas is not composed yet (frame budget), beyond the
silhouette distance and for the tournament crowd, and by DESIGN 38 impostors (``KulitCadangan.untuk``) - so no body
ever shows a vanilla skin or a plain box. Each is a real composed look of a fixed sample (role row + seed below).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sosok_rupa as R  # noqa: E402
from sosok_lib import ASSETS, SheetCache, susun, write_argb  # noqa: E402

KELAS = ["sipil", "kultivator", "penjaga", "rahib", "bangsawan", "iblis"]
OUT = os.path.join(ASSETS, "textures", "sosok", "cadangan")

# class -> (look key for men, for women, for children), realm, dao
SAMPLE = {
    "sipil": ("warga", "warga", "anak", -1, -1),
    "kultivator": ("murid.2", "murid.2", "murid.calon", 3, 0),
    "penjaga": ("penjaga", "penjaga", "anak", -1, -1),
    "rahib": ("rahib", "pendeta_tao", "anak", -1, -1),
    "bangsawan": ("menteri", "nyonya_sutra", "tuan_muda", -1, -1),
    "iblis": ("tetua_iblis", "tetua_iblis", "warga_iblis", 4, 6),
}
SEED = 0x51A7E


def look_for(kel, usia, kelas):
    men, women, child, realm, dao = SAMPLE[kelas]
    key = child if usia == R.ANAK else (women if kel else men)
    return R.resolve((kel, usia, 3, SEED + 97 * usia + 13 * kel + 7 * KELAS.index(kelas)), None,
                     R.masukan(key, realm=realm, dao=dao, pangkat=2 if kelas == "kultivator" else -1))


def main():
    cache = SheetCache()
    n = 0
    for kel, s in ((0, "l"), (1, "p")):
        for usia in range(4):
            for kelas in KELAS:
                atlas = susun(look_for(kel, usia, kelas), cache)
                write_argb(os.path.join(OUT, "%s_%d_%s.png" % (s, usia, kelas)), 128, 128, atlas)
                n += 1
    if cache.missing:
        print("missing sheets:", sorted(cache.missing))
        sys.exit(1)
    print("fallback atlases:", n)


if __name__ == "__main__":
    main()
