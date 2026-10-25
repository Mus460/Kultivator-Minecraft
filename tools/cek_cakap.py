"""Checker of the conversation-engine data (DESIGN 42.5A.12; owner 42K-T) and python mirror of the Java realiser.

    python tools/cek_cakap.py                 check data + run every golden file (exit 1 on any error)
    python tools/cek_cakap.py --golden-tulis  rewrite the expectations of every golden file, then check
    python tools/cek_cakap.py --kombinasi     also print the per-intent combination report
    python tools/cek_cakap.py --uji           self-test of the mirror (random generator, seeds, rendering)
    options: --repo DIR

Checks: every grammar key (frames and variants) exists in id_id and en_us; the kota_hidup_cakap* fragments have identical
key sets; a frame string uses exactly its slots as %1$s..%n$s (variants a subset); slot references resolve; no orphan
fragment, no cycle, depth <= 4; syarat keys and values known; seru and gesture names known; dialect table 7 x 60 with every
lexicon key present; 36 tics with texts; every lexicon key the typed slots can produce exists; golden files (>= 40) give
byte-identical keys/arguments and id/en text through the mirror below.

The mirror (class Mesin) follows dev.kultivator.dunia.kota42.cakap.{TataBahasa, Realisasi, TipeSlot, Dialek, Tik,
AntiUlang} line for line: same file order, same weights, same draws (SplittableRandom.nextLong >>> 11 x 2^-53), same
seed (Cakap.benih). Change both together.
"""
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = "src/main/resources/data/kultivator/tempat/percakapan"
K = "kultivator.cakap."
KL = K + "lex."
KT = K + "tik."
REGISTER = ("sopan", "biasa", "kasar", "kuno")
DIALEK = ("ibukota", "pelabuhan", "oasis", "air", "gunung", "iblis", "kampung")
TIPE = ("sebutan_pendengar", "nama_warga", "ganti", "barang", "tempat", "arah", "jarak", "angka", "harga", "waktu_lalu",
        "waktu_depan", "cuaca", "musim", "kata_emosi", "seru", "tik", "pekerjaan", "hubungan")
ALIAS_TIPE = {"sebutan": "sebutan_pendengar", "nama": "nama_warga"}
SYARAT_SATU = ("peran", "kelompok", "tahap", "kelamin", "watak", "jam", "cuaca", "musim", "wajah", "label_emosi", "kesan", "tempat",
               "dialek", "register")
SYARAT_BANYAK = ("kebiasaan", "acara", "kenangan", "hubungan")
SYARAT_LAIN = ("sifat", "tahu_nama", "fakta", "ranah_selisih")
SERU_KELAMIN = ("ya", "eh", "hmm", "oh", "tawa", "aduh", "terima_kasih", "maaf")
SERU_TETAP = {"bisik": "bisik_p", "takut": "takut_p", "setuju": "setuju_l"}
SERU_SEMUA = set(SERU_KELAMIN) | {"cih", "wah_anak", "tetua_hmm", "tetua_marah", "mabuk", "bisik", "takut", "setuju"}
GESTUR_ALIAS = {"angguk": "angguk_dengar", "doa": "sikap_doa", "berkacak": "sikap_berkacak", "geleng": "menepuk_jidat"}
ARAH = ("utara", "timur_laut", "timur", "tenggara", "selatan", "barat_daya", "barat", "barat_laut", "sini")
CUACA = ("cerah", "hujan", "badai", "salju", "kabut_qi", "gerhana", "meteor")
MUSIM = ("semi", "panas", "gugur", "dingin")
HUB = ("sahabat", "kekasih", "pasangan", "pasangan.l", "pasangan.p", "musuh", "saingan", "guru", "murid", "kenalan", "tetangga",
       "ayah", "ibu", "anak", "anak.l", "anak.p", "saudara", "saudara.l", "saudara.p", "cucu", "kakek", "nenek", "mertua",
       "mertua.l", "mertua.p")
BERNAMA = {"tuan", "nona", "kakak", "adik", "senior", "rekan_daois", "paman", "bibi", "kakek", "nenek", "bapak", "ibu", "tuan_abadi"}
FORMAT = re.compile(r"%(\d+)\$s|%%")
MASK = (1 << 64) - 1


# ============================================================================ java primitives

def jhash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= 1 << 31 else h


def campur(z):
    z &= MASK
    z = ((z ^ (z >> 33)) * 0xFF51AFD7ED558CCD) & MASK
    z = ((z ^ (z >> 33)) * 0xC4CEB9FE1A85EC53) & MASK
    return z ^ (z >> 33)


def benih(dunia, warga, lawan, hari, giliran, garam):
    """Cakap.benih (signed long result)."""
    h = campur((dunia & MASK) ^ 0x6A09E667F3BCC909)
    h = campur(h ^ (warga & MASK))
    h = campur(h ^ (((lawan << 32) & MASK) | (giliran & 0xFFFFFFFF)))
    h = campur(h ^ (hari & MASK))
    h = campur(h ^ ((jhash(garam) if garam is not None else 0) & MASK))
    return h - (1 << 64) if h >= 1 << 63 else h


class Acak:
    """java.util.SplittableRandom, nextLong only."""
    GAMMA = 0x9E3779B97F4A7C15

    def __init__(self, seed):
        self.seed = seed & MASK

    def next_long(self):
        self.seed = (self.seed + self.GAMMA) & MASK
        z = self.seed
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
        return z ^ (z >> 31)

    def pecahan(self):
        return (self.next_long() >> 11) * (2.0 ** -53)


def banding(expr, value):
    t = str(expr).strip()
    try:
        for op in (">=", "<=", ">", "<", "="):
            if t.startswith(op):
                x = float(t[len(op):])
                return {">=": value >= x, "<=": value <= x, ">": value > x, "<": value < x, "=": value == x}[op]
        return value == float(t)
    except ValueError:
        return False


# ============================================================================ data

def baca_json(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def muat_raw(repo):
    """Raw files by relative path without .json, in Identifier order (CakapReload)."""
    akar = os.path.join(repo, DATA)
    out = []
    for d, _, ns in os.walk(akar):
        for n in ns:
            if n.endswith(".json"):
                full = os.path.join(d, n)
                rel = os.path.relpath(full, akar).replace("\\", "/")
                out.append((rel, full))
    out.sort(key=lambda x: x[0])
    raw = {}
    for rel, full in out:
        o = baca_json(full)
        if isinstance(o, dict):
            raw[rel[:-5]] = o
    return raw


def muat_lang(repo, kode):
    d = os.path.join(repo, "lang-src")
    out = {}
    for n in sorted(os.listdir(d)):
        if n.endswith("." + kode + ".json"):
            out.update(baca_json(os.path.join(d, n)))
    return out


def teks_list(o, key):
    v = o.get(key) if isinstance(o, dict) else None
    if isinstance(v, list):
        return [str(x) if not isinstance(x, bool) else ("true" if x else "false") for x in v if not isinstance(x, (dict, list))]
    if v is None or isinstance(v, (dict, list)):
        return []
    return [jstr(v)]


def jstr(v):
    if isinstance(v, bool):
        return "true" if v else "false"
    return str(v)


class Tabel:
    """TataBahasa.parse + Dialek.Tabel.parse + Tik.Tabel.parse."""

    def __init__(self, raw):
        self.aturan = {}
        for path, o in raw.items():
            if not path.startswith("tata/"):
                continue
            kel = o.get("kelompok", path[5:])
            for r in o.get("isi", []):
                if not isinstance(r, dict) or not r.get("aturan"):
                    continue
                bingkai = []
                for b in r.get("bingkai", []):
                    if b.get("kunci"):
                        bingkai.append({"kunci": K + b["kunci"], "slot": teks_list(b, "slot"), "bobot": max(1, int(b.get("bobot", 1))),
                                        "register": set(teks_list(b, "register")), "dialek": set(teks_list(b, "dialek")),
                                        "varian": teks_list(b, "varian")})
                if not bingkai:
                    continue
                slot = {}
                for n, s in (r.get("slot") or {}).items():
                    if isinstance(s, dict):
                        t = s.get("tipe")
                        slot[n] = {"tipe": ALIAS_TIPE.get(t, t) if t in TIPE or t in ALIAS_TIPE else None, "aturan": s.get("aturan"),
                                   "lex": s.get("lex"), "dari": s.get("dari", ""), "fitur": teks_list(s, "fitur"),
                                   "opsional": float(s.get("opsional", 1.0))}
                self.aturan[r["aturan"]] = {"id": r["aturan"], "kelompok": kel, "niat": r.get("niat"), "topik": r.get("topik"),
                                            "syarat": r.get("syarat") or {}, "bingkai": bingkai, "slot": slot,
                                            "lanjut": teks_list(r, "lanjut"), "gestur": r.get("gestur"), "seru": r.get("seru")}
        self.niat = {}
        for a in self.aturan.values():
            if a["niat"]:
                self.niat.setdefault(a["niat"], []).append(a)
        d = raw.get("dialek") or {}
        self.bawaan = d.get("bawaan", "kampung")
        self.berkelamin = set(teks_list(d, "berkelamin"))
        self.seru_label = d.get("seru_label") or {}
        self.seru_niat = d.get("seru_niat") or {}
        self.dialek = {}
        for x in d.get("dialek", []):
            if x.get("id"):
                self.dialek[x["id"]] = {"kata": set(teks_list(x, "kata")), "kelamin": set(teks_list(x, "kelamin")),
                                        "tanpa_nama": set(teks_list(x, "tanpa_nama")), "raw": x}
        t = raw.get("tik") or {}
        self.tik_peluang = float(t.get("peluang", 0.25))
        self.tik_pengali = float(t.get("pengali_A", 1.5))
        self.tik_ambang = float(t.get("ambang_A", 0.4))
        self.tik_maks = max(1, int(t.get("maks_per_warga", 2)))
        self.tik = [{"id": x["id"], "posisi": x.get("posisi", "awal"), "bobot": max(1, int(x.get("bobot", 1))), "syarat": x.get("syarat") or {}}
                    for x in t.get("tik", []) if x.get("id")]

    def lex(self, kata, dialek, kelamin):
        d = self.dialek.get(dialek)
        p = kelamin == "p"
        if d is not None and kata in d["kata"]:
            return KL + kata + "." + dialek + (".p" if p and kata in d["kelamin"] else "")
        return KL + kata + (".p" if p and kata in self.berkelamin else "")

    def lex_bernama(self, kata, dialek, kelamin):
        d = self.dialek.get(dialek)
        if d is not None and kata in d["tanpa_nama"]:
            return KL + kata + (".p" if kelamin == "p" and kata in self.berkelamin else "")
        return self.lex(kata, dialek, kelamin)

    def seru(self, niat, label):
        w = self.seru_niat.get(niat) if niat else None
        if w is None and label is not None:
            w = self.seru_label.get(label)
        return w or "oh"


# ============================================================================ the mirror

KOSONG = {"t": ""}


def node(k, *a):
    return {"k": k, "a": list(a)}


def lit(t):
    return {"t": t}


def cocok_json(syarat, s):
    if not syarat:
        return 0
    n = 0
    for key, v in syarat.items():
        if key.startswith("_"):
            continue
        if key in SYARAT_SATU:
            l = teks_list(s, key)
            ok = ada(v, l[0] if l else "")
        elif key in SYARAT_BANYAK:
            ok = ada_salah(v, teks_list(s, key))
        elif key == "sifat":
            ok = True
            if isinstance(v, dict):
                sf = s.get("sifat") if isinstance(s.get("sifat"), dict) else {}
                for t, e in v.items():
                    if isinstance(e, (dict, list)) or not banding(jstr(e), int(sf.get(t, 0))):
                        ok = False
                        break
        elif key == "tahu_nama":
            ok = not isinstance(v, (dict, list)) and bool(v) == bool(s.get("tahu_nama", False)) if isinstance(v, bool) else \
                (not isinstance(v, (dict, list)) and (jstr(v).lower() == "true") == bool(s.get("tahu_nama", False)))
        elif key == "fakta":
            ok = "fakta" in s and ada(v, (teks_list(s, "fakta") or [""])[0])
        elif key == "ranah_selisih":
            ok = not isinstance(v, (dict, list)) and banding(jstr(v), int(s.get("ranah_selisih", 0)))
        else:
            continue
        if not ok:
            return -1
        n += 1
    return n


def jbool(v):
    """JsonPrimitive.getAsBoolean."""
    return v if isinstance(v, bool) else jstr(v).lower() == "true"


def ada(v, value):
    if isinstance(v, list):
        for e in v:
            if not isinstance(e, (dict, list)) and jstr(e).lower() == value.lower():
                return True
        return len(v) == 0
    return not isinstance(v, dict) and jstr(v).lower() == value.lower()


def ada_salah(v, values):
    if isinstance(v, list) and not v:
        return True
    return any(ada(v, x) for x in values)


class Cincin:
    def __init__(self, ukuran=256):
        self.ukuran = ukuran
        self.isi = []

    def catat(self, kunci, pembicara, now):
        self.isi.append((jhash(kunci), pembicara, now))
        if len(self.isi) > self.ukuran:
            self.isi.pop(0)

    def faktor(self, kunci, pembicara, now):
        h = jhash(kunci)
        sama = baru = False
        for k, p, w in self.isi:
            if k != h:
                continue
            umur = now - w
            if umur < 0:
                continue
            sama |= p == pembicara and umur <= 24000
            baru |= umur <= 1000
        return (0.1 if sama else 1.0) * (0.5 if baru else 1.0)


class Mesin:
    def __init__(self, tabel):
        self.t = tabel

    # ---------------------------------------------------------------- Realisasi.ujar
    def ujar(self, m, r):
        semua = self.t.niat.get(m["niat"], [])
        rules = semua
        if m["topik"] is not None:
            rules = [a for a in semua if a["topik"] == m["topik"]]
            if not rules:
                rules = [a for a in semua if a["topik"] is None]
            if not rules:
                rules = semua
        pa, pb, pw = [], [], []
        for a in rules:
            s = m["cocok"](a["syarat"])
            if s < 0:
                continue
            for b in a["bingkai"]:
                if self.boleh(b, m) and bisa(a, b, m):
                    pa.append(a)
                    pb.append(b)
                    pw.append((1.0 + 2.0 * s) * b["bobot"] * self.faktor(m, b["kunci"]))
        if not pw:
            return None
        i = pilih(pw, r)
        a = pa[i]
        dipakai = []
        akar = self.bingkai(a, pb[i], 1, m, r, dipakai)
        tik = None
        if m["tik"]:
            d = self.sisip(m, r)
            if d is not None:
                tik = d["id"]
                akar = self.bungkus(d, akar, r)
        gestur = a["gestur"] if a["gestur"] is not None else m["gestur"]
        if m["dengar"] is not None:
            for k in dipakai:
                m["dengar"].catat(k, m["pembicara"], m["now"])
        return {"harap": akar, "aturan": a["id"], "gestur": gestur, "seru": seru_kunci(a["seru"], m["kelamin"]), "tik": tik}

    @staticmethod
    def boleh(b, m):
        return (not b["register"] or m["register"] in b["register"]) and (not b["dialek"] or m["dialek"] in b["dialek"])

    @staticmethod
    def faktor(m, kunci):
        return 1.0 if m["dengar"] is None else m["dengar"].faktor(kunci, m["pembicara"], m["now"])

    def fragmen(self, rid, depth, m, r, dipakai):
        a = self.t.aturan.get(rid)
        if a is None or m["cocok"](a["syarat"]) < 0:
            return KOSONG
        pb, pw = [], []
        for b in a["bingkai"]:
            if self.boleh(b, m) and bisa(a, b, m):
                pb.append(b)
                pw.append(b["bobot"] * self.faktor(m, b["kunci"]))
        if not pw:
            return KOSONG
        return self.bingkai(a, pb[pilih(pw, r)], depth, m, r, dipakai)

    def bingkai(self, a, b, depth, m, r, dipakai):
        dipakai.append(b["kunci"])
        jamak = False
        args = []
        for nama in b["slot"]:
            s = a["slot"].get(nama)
            if s is None:
                args.append(KOSONG)
                continue
            if s["opsional"] < 1.0 and r.pecahan() >= s["opsional"]:
                args.append(KOSONG)
                continue
            if s["aturan"] is not None:
                args.append(KOSONG if depth >= 4 else self.fragmen(s["aturan"], depth + 1, m, r, dipakai))
            elif s["lex"] is not None:
                args.append(node(self.t.lex(s["lex"], m["dialek"], "")))
            elif s["tipe"] is not None:
                args.append(self.isi(s["tipe"], nilai(m, nama, s["dari"]), s["dari"], m))
                j = m["nilai"].get(polos(nama) + ".jumlah")
                if "jamak" in s["fitur"] and isinstance(j, int) and not isinstance(j, bool) and j > 1:
                    jamak = True
            else:
                args.append(KOSONG)
        return {"k": varian(b, m, jamak), "a": args}

    def sisip(self, m, r):
        calon = []
        for tid in m["tik"]:
            d = next((x for x in self.t.tik if x["id"] == tid), None)
            if d is not None and d["id"] != m["tik_terakhir"] and m["cocok"](d["syarat"]) >= 0:
                calon.append(d)
        if not calon:
            return None
        p = (self.t.tik_peluang if m["tik_peluang"] < 0.0 else m["tik_peluang"]) * (self.t.tik_pengali if m["arousal"] > self.t.tik_ambang else 1.0)
        if r.pecahan() >= p:
            return None
        return calon[0] if len(calon) == 1 else calon[pilih([float(x["bobot"]) for x in calon], r)]

    def milik(self, seed, cocok):
        calon = [d for d in self.t.tik if cocok({k: v for k, v in d["syarat"].items()
                                                  if k not in ("jam", "cuaca", "musim", "acara", "wajah", "label_emosi")}) >= 0]
        out = []
        if not calon:
            return out
        r = Acak(seed)
        n = 1 if self.t.tik_maks <= 1 else (1 if r.pecahan() < 0.5 else 2)
        for _ in range(n):
            if not calon:
                break
            out.append(calon.pop(pilih([float(x["bobot"]) for x in calon], r))["id"])
        return out

    @staticmethod
    def bungkus(d, akar, r):
        b = K + "bungkus."
        p = d["posisi"]
        if p == "akhir":
            return node(b + "akhir", akar, node(KT + d["id"]))
        if p == "sisip":
            return node(b + "sisip", akar, node(KT + d["id"]))
        if p == "gagap":
            return node(b + "gagap", node(KL + "gagap." + str(int(r.pecahan() * 6))), akar)
        return node(b + "awal", node(KT + d["id"]), akar)

    # ---------------------------------------------------------------- TipeSlot.isi
    def isi(self, tipe, v, dari, m):
        t = self.t
        if is_node(v) and tipe != "sebutan_pendengar":
            return v
        if tipe == "sebutan_pendengar":
            if is_orang(v):
                return self.sebutan(v["orang"], m)
            if isinstance(v, str):
                return node(t.lex(v, m["dialek"], ""))
            if is_node(v):
                return v
            return self.sebutan(m["pendengar"], m) if m["pendengar"] is not None else KOSONG
        if tipe == "nama_warga":
            if is_orang(v) and v["orang"]["nama"] is not None:
                return v["orang"]["nama"]
            return node(t.lex(v, m["dialek"], "")) if isinstance(v, str) else KOSONG
        if tipe == "ganti":
            if dari == "pembicara":
                return node(t.lex("aku", m["dialek"], m["kelamin"]))
            if dari == "pendengar":
                return node(t.lex("kamu", m["dialek"], ""))
            kel = v["orang"]["kelamin"] if is_orang(v) else ""
            return node(KL + "dia" + ("." + kel if kel else ""))
        if tipe == "barang":
            if isinstance(v, str):
                c = v.find(":")
                return node("item.minecraft." + v if c < 0 else "item." + v[:c] + "." + v[c + 1:].replace("/", "."))
            return KOSONG
        if tipe == "tempat":
            return node(t.lex(v, m["dialek"], "")) if isinstance(v, str) else KOSONG
        if tipe == "arah":
            return node(KL + "arah." + v) if isinstance(v, str) else KOSONG
        if tipe == "jarak":
            if is_num(v):
                if v <= 48:
                    return node(t.lex("dekat", m["dialek"], ""))
                if v <= 160:
                    return node(KL + "tidak_jauh")
                return node(t.lex("jauh", m["dialek"], "")) if v <= 600 else node(KL + "jauh_sekali")
            return node(t.lex(v, m["dialek"], "")) if isinstance(v, str) else KOSONG
        if tipe == "angka":
            return angka(v) if is_num(v) else KOSONG
        if tipe == "harga":
            return node("kultivator.kota.nb", lit(str(v))) if is_num(v) else KOSONG
        if tipe == "waktu_lalu":
            if is_num(v):
                return self.lalu(m["hari"] - v, m)
            return node(t.lex(v, m["dialek"], "")) if isinstance(v, str) else KOSONG
        if tipe == "waktu_depan":
            if is_num(v):
                return self.depan(v - m["hari"], m)
            return node(t.lex(v, m["dialek"], "")) if isinstance(v, str) else KOSONG
        if tipe == "cuaca":
            w = v if isinstance(v, str) else m["cuaca"]
            return node(KL + "cuaca." + w) if w else KOSONG
        if tipe == "musim":
            w = v if isinstance(v, str) else m["musim"]
            return node(KL + "musim." + w) if w else KOSONG
        if tipe == "kata_emosi":
            label = v if isinstance(v, str) else m["label"]
            if not label:
                return KOSONG
            kata = node(t.lex(label, m["dialek"], ""))
            i = m["intensitas"]
            return kata if label == "netral" or not i else node(KL + "emosi." + i, kata)
        if tipe == "seru":
            return node(t.lex(v if isinstance(v, str) else t.seru(m["niat"], m["label"]), m["dialek"], ""))
        if tipe == "tik":
            if isinstance(v, str):
                return node(KT + v)
            for tid in m["tik"]:
                if tid != "gagap":
                    return node(KT + tid)
            return KOSONG
        if tipe == "pekerjaan":
            if isinstance(v, str):
                return node("kultivator.kota.peran." + v)
            return node("kultivator.kota.peran." + m["peran"]) if dari == "pembicara" and m["peran"] else KOSONG
        if tipe == "hubungan":
            return node(KL + "hub." + v) if isinstance(v, str) else KOSONG
        return KOSONG

    def sebutan(self, o, m):
        l = o["kelamin"] != "p"
        lt = "dewasa" if o["pemain"] else o["tahap"]
        reg = m["register"]
        if reg == "kuno":
            r = m["ranah_selisih"]
            w = "leluhur" if r >= 6 else "tuan_abadi" if r >= 4 else "senior" if r >= 1 else "anak_muda" if r <= -2 else "rekan_daois"
        elif reg == "kasar":
            w = "kawan"
        elif reg == "sopan":
            w = ("bapak" if l else "ibu") if lt == "tua" else ("tuan" if l else "nona")
        else:
            sp = m["tahap"]
            if sp in ("anak", "remaja"):
                w = ("kakek" if l else "nenek") if lt == "tua" else ("paman" if l else "bibi") if lt == "dewasa" else "kakak"
            elif sp == "tua":
                w = "kawan" if lt == "tua" else "anak_muda"
            else:
                w = ("bapak" if l else "ibu") if lt == "tua" else "adik" if lt == "anak" else \
                    "kawan" if m["kesan"] in ("akrab", "sahabat") else "kakak"
        if m["tahu_nama"] and o["nama"] is not None and w in BERNAMA:
            return node(KL + "sebutan_nama", node(self.t.lex_bernama(w, m["dialek"], o["kelamin"])), o["nama"])
        return node(self.t.lex(w, m["dialek"], o["kelamin"]))

    def lalu(self, hari, m):
        t = self.t
        if hari <= 0:
            return node(t.lex("tadi", m["dialek"], ""))
        if hari == 1:
            return node(t.lex("kemarin", m["dialek"], ""))
        if hari == 2:
            return node(t.lex("kemarin_lusa", m["dialek"], ""))
        if hari < 7:
            return node(KL + "hari_lalu", angka(hari))
        if hari < 14:
            return node(t.lex("minggu_lalu", m["dialek"], ""))
        return node(KL + "minggu_lalu_n", angka(jdiv(hari, 7))) if hari < 60 else node(t.lex("lama_sekali", m["dialek"], ""))

    def depan(self, hari, m):
        t = self.t
        if hari <= 0:
            return node(t.lex("nanti", m["dialek"], ""))
        if hari == 1:
            return node(t.lex("besok", m["dialek"], ""))
        return node(t.lex("lusa", m["dialek"], "")) if hari == 2 else node(KL + "hari_lagi", angka(hari))


def jdiv(a, b):
    q = abs(a) // abs(b)
    return q if (a >= 0) == (b >= 0) else -q


def angka(n):
    return node(KL + "angka." + str(n)) if 0 <= n <= 20 else lit(str(n))


def is_node(v):
    return isinstance(v, dict) and ("k" in v or "t" in v)


def is_orang(v):
    return isinstance(v, dict) and "orang" in v


def is_num(v):
    return isinstance(v, int) and not isinstance(v, bool)


def polos(nama):
    return nama[1:] if nama.startswith("$") else nama


def nilai(m, nama, dari):
    key = polos(nama)
    if key in m["nilai"]:
        return m["nilai"][key]
    if dari and dari in m["nilai"]:
        return m["nilai"][dari]
    if dari == "pendengar" and m["pendengar"] is not None:
        return {"orang": m["pendengar"]}
    return None


PERLU_NILAI = ("nama_warga", "barang", "tempat", "arah", "jarak", "angka", "harga", "waktu_lalu", "waktu_depan", "hubungan")


def bisa(a, b, m):
    """Realisasi.bisa."""
    for nama in b["slot"]:
        s = a["slot"].get(nama)
        if s is None or s["tipe"] is None or s["opsional"] < 1.0 or nilai(m, nama, s["dari"]) is not None:
            continue
        t = s["tipe"]
        if t in PERLU_NILAI:
            return False
        if t == "sebutan_pendengar" and m["pendengar"] is None:
            return False
        if t == "pekerjaan" and not (s["dari"] == "pembicara" and m["peran"]):
            return False
    return True


def varian(b, m, jamak):
    if not b["varian"]:
        return b["kunci"]
    aktif = [m["register"], m["dialek"], m["kelamin"], "j" if jamak else "s"]
    best, best_n, best_p = None, 0, 1 << 30
    for v in b["varian"]:
        parts = v.split(".")
        if not all(p in aktif for p in parts):
            continue
        p = min(aktif.index(x) for x in parts)
        if len(parts) > best_n or (len(parts) == best_n and p < best_p):
            best, best_n, best_p = v, len(parts), p
    return b["kunci"] if best is None else b["kunci"] + "." + best


def pilih(w, r):
    total = 0.0
    for x in w:
        total += x
    u = r.pecahan() * total
    for i, x in enumerate(w):
        u -= x
        if u < 0.0:
            return i
    return len(w) - 1


def seru_kunci(seru, kelamin):
    if not seru:
        return None
    if seru in SERU_KELAMIN:
        return "kota42.seru." + seru + ("_p" if kelamin == "p" else "_l")
    return "kota42.seru." + SERU_TETAP.get(seru, seru)


def rangkai(s, lang):
    if "t" in s:
        return s["t"]
    if not s.get("k"):
        return ""
    fmt = lang.get(s["k"], s["k"])
    args = [rangkai(a, lang) for a in s.get("a", [])]

    def rep(mt):
        if mt.group(1) is None:
            return "%"
        i = int(mt.group(1)) - 1
        return args[i] if 0 <= i < len(args) else ""
    return FORMAT.sub(rep, fmt)


def orang_json(p):
    nama = p.get("nama")
    if isinstance(nama, str):
        nama = lit(nama) if nama else KOSONG
    elif isinstance(nama, dict):
        nama = norm_node(nama)
    return {"nama": nama, "kelamin": p.get("kelamin", "l"), "tahap": p.get("tahap", "dewasa"), "pemain": bool(p.get("pemain", False))}


def norm_node(e):
    """Simpul.dariGolden + golden(): {"t": ""} and missing parts normalised."""
    if not isinstance(e, dict):
        return KOSONG
    if "t" in e:
        return lit(e["t"]) if e["t"] else KOSONG
    return {"k": e.get("k", ""), "a": [norm_node(x) for x in e.get("a", [])]} if e.get("k") else KOSONG


def nilai_json(e):
    if isinstance(e, bool):
        return jstr(e)
    if isinstance(e, (int, str)):
        return e
    if isinstance(e, float):
        return int(e)
    if isinstance(e, dict) and "orang" in e:
        return {"orang": orang_json(e["orang"])}
    return norm_node(e)


def dari_json(o, dengar, now):
    """Realisasi.dariJson."""
    situasi = json.loads(json.dumps(o.get("situasi") or {}))
    m = {"niat": o.get("niat", "SAPA"), "topik": o.get("topik"), "register": o.get("register", "biasa"), "dialek": o.get("dialek", "kampung"),
         "kelamin": o.get("kelamin", "l"), "tahap": o.get("tahap", "dewasa"), "peran": o.get("peran", ""), "pembicara": int(o.get("pembicara", 0)),
         "label": o.get("label", "netral"), "intensitas": o.get("intensitas", ""), "arousal": float(o.get("arousal", 0.0)),
         "kesan": o.get("kesan", ""), "ranah_selisih": int(o.get("ranah_selisih", 0)), "tahu_nama": bool(o.get("tahu_nama", False)),
         "hari": int(o.get("hari", 0)), "cuaca": o.get("cuaca", ""), "musim": o.get("musim", ""), "tik": teks_list(o, "tik"),
         "tik_terakhir": o.get("tik_terakhir"), "tik_peluang": float(o.get("tik_peluang", -1.0)), "gestur": o.get("gestur"),
         "dengar": dengar, "now": now}
    for k, v in (("register", m["register"]), ("dialek", m["dialek"]), ("kelamin", m["kelamin"]), ("tahap", m["tahap"]),
                 ("label_emosi", m["label"]), ("kesan", m["kesan"]), ("peran", m["peran"]), ("ranah_selisih", m["ranah_selisih"]),
                 ("tahu_nama", m["tahu_nama"])):
        situasi.setdefault(k, v)
    m["nilai"] = {k: nilai_json(v) for k, v in (o.get("nilai") or {}).items()}
    p = o.get("pendengar")
    m["pendengar"] = orang_json(p) if isinstance(p, dict) else None
    m["cocok"] = lambda s, _s=situasi: cocok_json(s, _s)
    return m


def jalankan_golden(g, mesin, lid, len_, tulis):
    """Realisasi.ujiGolden."""
    beda = []
    nama = g.get("id", "?")
    dasar = g.get("masukan") or {}
    dunia = int(g.get("benih_dunia", 0))
    dengar = Cincin(256)
    for x in dasar.get("dengar", []):
        dengar.catat(x[0], int(x[1]), int(x[2]))
    for i, step in enumerate(g.get("langkah", [])):
        mj = json.loads(json.dumps(dasar))
        mj.update(step.get("ubah") or {})
        now = int(mj.get("now", 0)) + 200 * i
        m = dari_json(mj, dengar, now)
        r = Acak(benih(dunia, m["pembicara"], 0, m["hari"], i, "cakap"))
        k = mesin.ujar(m, r)
        if k is None:
            hasil = {"harap": KOSONG, "aturan": None, "gestur": None, "seru": None, "tik": None}
        else:
            hasil = k
        hasil = dict(hasil)
        hasil["id_id"] = rangkai(hasil["harap"], lid)
        hasil["en_us"] = rangkai(hasil["harap"], len_)
        for key in ("harap", "aturan", "gestur", "seru", "tik", "id_id", "en_us"):
            if tulis:
                step[key] = hasil[key]
            elif step.get(key, "<missing>") != hasil[key]:
                beda.append(f"{nama} step {i} {key}: expected {json.dumps(step.get(key, '<missing>'), ensure_ascii=False)}, "
                            f"got {json.dumps(hasil[key], ensure_ascii=False)}")
    return beda


def golden_berkas(repo):
    d = os.path.join(repo, DATA, "golden")
    if not os.path.isdir(d):
        return []
    return [os.path.join(d, n) for n in sorted(os.listdir(d)) if n.endswith(".json")]


def tulis_json(p, o):
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(o, ensure_ascii=False, indent=2) + "\n")


def golden_tulis(repo):
    raw = muat_raw(repo)
    mesin = Mesin(Tabel(raw))
    lid, len_ = muat_lang(repo, "id_id"), muat_lang(repo, "en_us")
    n = 0
    for p in golden_berkas(repo):
        g = baca_json(p)
        if g.get("jenis") != "realisasi":
            continue
        jalankan_golden(g, mesin, lid, len_, True)
        tulis_json(p, g)
        n += 1
    print(f"golden: {n} files rewritten")


# ============================================================================ checks

def nama_gestur(repo):
    names = set(GESTUR_ALIAS)
    src = os.path.join(repo, "src/main/java/dev/kultivator")
    for rel, pat in (("sosok/GerakId.java", r'put\([A-Z_0-9]+, "([a-z0-9_]+)"'), ("sosok/GerakId.java", r'BY_NAME\.put\("([a-z0-9_]+)"')):
        try:
            with open(os.path.join(src, rel), encoding="utf-8") as f:
                names |= set(re.findall(pat, f.read()))
        except OSError:
            pass
    try:
        with open(os.path.join(src, "dunia/kota42/api/KotaGerakId.java"), encoding="utf-8") as f:
            t = f.read()
        a = t.find("KLIP_NAMA = {")
        if a >= 0:
            names |= set(re.findall(r'"([a-z0-9_]+)"', t[a:t.find("};", a)]))
    except OSError:
        pass
    return names


def placeholders(s):
    return [int(x) for x in re.findall(r"%(\d+)\$s", s)]


def cek(repo, laporan_kombinasi=False):
    err, warn = [], []
    raw = muat_raw(repo)
    tab = Tabel(raw)
    lid, len_ = muat_lang(repo, "id_id"), muat_lang(repo, "en_us")
    lang_dir = os.path.join(repo, "lang-src")
    # identical keys in the cakap fragments
    for n in sorted(os.listdir(lang_dir)):
        if n.startswith("kota_hidup_cakap") and n.endswith(".id_id.json"):
            a = baca_json(os.path.join(lang_dir, n))
            pe = os.path.join(lang_dir, n.replace(".id_id.", ".en_us."))
            if not os.path.exists(pe):
                err.append(f"{n}: en_us twin missing")
                continue
            b = baca_json(pe)
            if set(a) != set(b):
                err.append(f"{n}: keys differ id/en: {sorted(set(a) ^ set(b))[:6]}")
            for k in a:
                if k in b and sorted(set(placeholders(a[k]))) != sorted(set(placeholders(b[k]))):
                    err.append(f"{n}: {k} uses different %n$s in id and en")
    niat_ids = {x.get("id") for x in (raw.get("niat") or {}).get("niat", [])}
    topik_ids = {x.get("id") for x in (raw.get("topik") or {}).get("topik", [])}
    gestur = nama_gestur(repo)
    rujuk = set()
    frames = 0
    asing = {}
    for rid, a in tab.aturan.items():
        if a["niat"] and niat_ids and a["niat"] not in niat_ids:
            asing.setdefault("niat " + a["niat"] + " (niat.json)", []).append(rid)
        if a["topik"] and topik_ids and a["topik"] not in topik_ids:
            asing.setdefault("topik " + a["topik"] + " (topik.json)", []).append(rid)
        for k in a["syarat"]:
            if not k.startswith("_") and k not in SYARAT_SATU + SYARAT_BANYAK + SYARAT_LAIN:
                err.append(f"rule {rid}: unknown syarat key {k}")
        if a["seru"] and a["seru"] not in SERU_SEMUA:
            err.append(f"rule {rid}: unknown seru {a['seru']}")
        g = a["gestur"]
        if g and g not in gestur and "sikap_" + g not in gestur:
            asing.setdefault("gesture " + g + " (GerakId/KotaGerakId, plays nothing)", []).append(rid)
        for n, s in a["slot"].items():
            if s["aturan"] is not None:
                rujuk.add(s["aturan"])
                if s["aturan"] not in tab.aturan:
                    err.append(f"rule {rid}: slot {n} refers to unknown rule {s['aturan']}")
            elif s["tipe"] is None and s["lex"] is None:
                err.append(f"rule {rid}: slot {n} has no valid tipe/aturan/lex")
            if s["lex"] is not None and KL + s["lex"] not in lid:
                err.append(f"rule {rid}: lexicon word {s['lex']} has no key")
        for b in a["bingkai"]:
            frames += 1
            for n in b["slot"]:
                if n not in a["slot"]:
                    err.append(f"{b['kunci']}: slot {n} not declared in the rule")
            for lang, nm in ((lid, "id_id"), (len_, "en_us")):
                dipakai = set()
                for k in [b["kunci"]] + [b["kunci"] + "." + v for v in b["varian"]]:
                    if k not in lang:
                        err.append(f"{k}: missing in {nm}")
                        continue
                    ph = placeholders(lang[k])
                    dipakai |= set(ph)
                    if any(x > len(b["slot"]) or x < 1 for x in ph):
                        err.append(f"{k} ({nm}): placeholder beyond {len(b['slot'])} slots")
                # a variant may add slots after the frame's own; every slot must be used by some text of the frame
                if sorted(dipakai) != list(range(1, len(b["slot"]) + 1)):
                    err.append(f"{b['kunci']} ({nm}): slots used {sorted(dipakai)} != 1..{len(b['slot'])}")
            for reg in b["register"]:
                if reg not in REGISTER:
                    err.append(f"{b['kunci']}: unknown register {reg}")
            for v in b["varian"]:
                for f in v.split("."):
                    if f not in REGISTER + DIALEK + ("l", "p", "s", "j"):
                        err.append(f"{b['kunci']}: unknown variant feature {f}")
    for what, rules in asing.items():
        warn.append(f"{what} unknown, used by {len(rules)} rules (e.g. {rules[0]})")
    for rid, a in tab.aturan.items():
        if not a["niat"] and rid not in rujuk:
            err.append(f"orphan fragment {rid} (no niat, never referenced)")
    memo = {}

    def depth(rid, stack):
        if rid in stack:
            err.append(f"cycle: {' -> '.join(stack + (rid,))}")
            return 99
        if rid not in memo:
            a = tab.aturan.get(rid)
            memo[rid] = 1 + max([depth(s["aturan"], stack + (rid,)) for s in a["slot"].values() if s["aturan"] in tab.aturan],
                                default=0) if a else 0
        return memo[rid]
    for rid in tab.aturan:
        if depth(rid, ()) > 4:
            err.append(f"rule {rid} expands deeper than 4 (deeper slots realise empty)")
    # dialects, lexicon, tics
    if len(tab.dialek) != 7:
        err.append(f"dialek.json: {len(tab.dialek)} dialects, expected 7")
    for d, x in tab.dialek.items():
        if len(x["kata"]) < 60:
            err.append(f"dialek {d}: {len(x['kata'])} words, expected >= 60")
        for w in x["kata"]:
            for k in (KL + w, KL + w + "." + d):
                if k not in lid:
                    err.append(f"dialek {d}: missing {k}")
        for w in x["kelamin"]:
            if KL + w + "." + d + ".p" not in lid:
                err.append(f"dialek {d}: missing {KL}{w}.{d}.p")
    for w in tab.berkelamin:
        if KL + w + ".p" not in lid:
            err.append(f"berkelamin: missing {KL}{w}.p")
    for w in list(tab.seru_label.values()) + list(tab.seru_niat.values()):
        if KL + w not in lid:
            err.append(f"seru word {w} has no lexicon key")
    if len(tab.tik) != 36:
        err.append(f"tik.json: {len(tab.tik)} tics, expected 36")
    for x in tab.tik:
        if x["posisi"] not in ("awal", "akhir", "sisip", "gagap"):
            err.append(f"tik {x['id']}: bad posisi {x['posisi']}")
        if x["posisi"] != "gagap" and KT + x["id"] not in lid:
            err.append(f"tik {x['id']}: missing text {KT}{x['id']}")
    need = [KL + "angka." + str(i) for i in range(21)] + [KL + "arah." + x for x in ARAH] + [KL + "cuaca." + x for x in CUACA] + \
           [KL + "musim." + x for x in MUSIM] + [KL + "hub." + x for x in HUB] + [KL + "gagap." + str(i) for i in range(6)] + \
           [KL + x for x in ("dia", "dia.l", "dia.p", "sebutan_nama", "hari_lalu", "minggu_lalu_n", "hari_lagi", "tidak_jauh", "jauh_sekali",
                             "netral")] + \
           [K + "bungkus." + x for x in ("awal", "akhir", "sisip", "gagap", "gabung")] + ["kultivator.kota.nb"]
    for x in (raw.get("emosi") or {}).get("intensitas", []):
        need += [KL + x["kunci"], KL + "emosi." + x["kunci"]]
    for x in (raw.get("emosi") or {}).get("label", []):
        need.append(KL + x["id"])
    for k in need:
        if k not in lid or k not in len_:
            err.append(f"typed-slot key missing: {k}")
    # combinations and targets
    komb_memo = {}

    def komb(rid, stack=()):
        if rid in stack or rid not in tab.aturan:
            return 1
        if rid not in komb_memo:
            a = tab.aturan[rid]
            tot = 0
            for b in a["bingkai"]:
                k = 1 + len(b["varian"])
                for n in b["slot"]:
                    s = a["slot"].get(n)
                    if s and s["aturan"]:
                        k *= komb(s["aturan"], stack + (rid,)) + (1 if s["opsional"] < 1.0 else 0)
                    elif s and s["opsional"] < 1.0:
                        k *= 2
                tot += k
            komb_memo[rid] = tot
        return komb_memo[rid]
    per_niat = {}
    for n, rules in tab.niat.items():
        per_niat[n] = sum(komb(a["id"]) for a in rules)
    terminal = sum(1 for k in lid if k.startswith(K))
    if frames < 900 or terminal < 2400:
        warn.append(f"TARGET not reached yet: {frames} frames (>= 900), {terminal} terminals (>= 2400)")
    # golden
    gold = [p for p in golden_berkas(repo)]
    if len(gold) < 40:
        warn.append(f"golden: {len(gold)} files, expected >= 40")
    mesin = Mesin(tab)
    for p in gold:
        g = baca_json(p)
        if g.get("jenis") == "realisasi":
            err.extend(jalankan_golden(g, mesin, lid, len_, False))
    print(f"cek_cakap: {len(tab.aturan)} rules, {frames} frames, {terminal} terminals, {len(tab.niat)} intents, "
          f"{len(tab.dialek)} dialects, {len(tab.tik)} tics, {len(gold)} golden")
    if laporan_kombinasi:
        for n in sorted(per_niat, key=lambda x: -per_niat[x]):
            print(f"  kombinasi {n}: {per_niat[n]} frame/fragment paths")
        for n, beda_teks, contoh in sampel(mesin, lid):
            print(f"  sampel {n}: {beda_teks} different id_id texts in {contoh} random contexts (target >= 10000 for SAPA, "
                  f"BERI_KABAR, KELUH)")
    for w in warn:
        print("WARN", w)
    for e in err:
        print("ERROR", e)
    print("cek_cakap", "OK" if not err else f"{len(err)} errors")
    return 0 if not err else 1


UJI_ACAK = [(0, -2152535657050944081), (42, -4767286540954276203), (-7, 7790691224305936752),
            (123456789012345, -2855823898702881541)]
UJI_BENIH = [((42, 7, 0, 120, 3, "cakap"), 8783813767602610778), ((-5, -1, -2, 9, 0, "golden"), -1123931248436019047),
             ((0, 0, 0, 0, 0, None), -5436739637030897931)]


NAMA = ("Bibi Wang", "Paman Li", "Lao Wu", "Ah Ling", "Xiao Hu", "Nyonya Lin", "Kakek Sun", "Mei", "Han", "Tuan Gao", "Pak Gu")


def sampel(mesin, lid, n=25000, niat=("SAPA", "BERI_KABAR", "KELUH", "BASA_BASI")):
    """Distinct texts of the common intents over random contexts (report of 42.5A.8 'sasaran keragaman')."""
    import random
    rnd = random.Random(1)
    preds = sorted({a["syarat"].get("fakta") for a in mesin.t.aturan.values() if isinstance(a["syarat"].get("fakta"), str)})
    out = []
    for ni in niat:
        if ni not in mesin.t.niat:
            continue
        seen = set()
        for i in range(n):
            o = lambda: {"orang": {"nama": rnd.choice(NAMA), "kelamin": rnd.choice("lp"), "tahap": rnd.choice(("anak", "dewasa", "tua"))}}
            situ = {"jam": rnd.choice(("subuh", "pagi", "siang", "sore", "malam", "larut")),
                    "cuaca": rnd.choice(("cerah", "cerah", "hujan", "badai", "salju", "kabut_qi")),
                    "musim": rnd.choice(("semi", "panas", "gugur", "dingin")),
                    "kelompok": rnd.choice(("pekerja", "pedagang", "penjaga", "anak", "tetua", "kultivator", "cendekia", "rahib", "pejabat")),
                    "watak": rnd.choice(("ramah", "curiga", "serakah", "bijak", "penakut", "sombong", "jujur", "licik")),
                    "sifat": {t: rnd.randint(-80, 80) for t in "HEXACO"}, "wajah": rnd.choice(("senang", "netral", "sedih", "marah")),
                    "kenangan": rnd.sample(("DIBANTU", "DIBERI_HADIAH", "DISELAMATKAN", "HADIR_NIKAH", "DIPUKUL", "UTANG_MANGKIR"), 1),
                    "hubungan": rnd.sample(("pasangan", "anak", "sahabat", "musuh", "kekasih", "guru_murid"), 2),
                    "fakta": rnd.choice(preds) if preds else ""}
            mj = {"niat": ni, "register": rnd.choice(REGISTER), "dialek": rnd.choice(DIALEK), "kelamin": rnd.choice("lp"),
                  "tahap": rnd.choice(("remaja", "dewasa", "tua")), "pembicara": rnd.randint(1, 3000),
                  "label": rnd.choice(("gembira", "puas", "kagum", "lega", "marah", "cemas", "jemu", "sedih", "netral")),
                  "intensitas": rnd.choice(("", "agak", "sangat", "bukan_main")), "arousal": rnd.random(),
                  "pendengar": rnd.choice((HAN_S, MEI_S)), "kesan": rnd.choice(("asing", "kenal", "akrab", "sahabat", "musuh")),
                  "tahu_nama": rnd.random() < 0.5, "ranah_selisih": rnd.randint(-2, 7), "hari": 120, "cuaca": situ["cuaca"],
                  "musim": situ["musim"], "situasi": situ, "tik": rnd.sample([t["id"] for t in mesin.t.tik], 2) if mesin.t.tik else [],
                  "peran": "petani",
                  "nilai": {"fakta.subjek": o(), "fakta.objek": o(), "fakta.nilai": rnd.randint(1, 40), "fakta.hariKejadian": rnd.randint(60, 120),
                            "lawan": o(), "konteks.tempat": {"t": "Kota Awan"}, "konteks.distrik": {"t": "Pasar Timur"},
                            "konteks.arah": rnd.choice(ARAH[:8]), "konteks.jarak": rnd.randint(10, 900), "konteks.pasangan": o(),
                            "konteks.anak": o(), "konteks.almarhum": o(), "konteks.sahabat": o(), "konteks.musuh": o(), "konteks.kekasih": o(),
                            "konteks.guru": o(), "konteks.murid": o(), "konteks.tetangga": o(), "konteks.barang": {"t": "beras"},
                            "konteks.barang_murah": {"t": "garam"}, "konteks.barang_mahal": {"t": "teh giok"}, "konteks.janji": {"t": "obat"},
                            "konteks.utang": rnd.randint(5, 300), "konteks.batas": rnd.randint(115, 125), "konteks.terakhir_jumpa": rnd.randint(90, 119),
                            "konteks.harga": rnd.randint(5, 60), "konteks.harga_tawar": rnd.randint(3, 40)}}
            m = dari_json(mj, None, 0)
            k = mesin.ujar(m, Acak(rnd.getrandbits(63)))
            if k is not None:
                seen.add(rangkai(k["harap"], lid))
        out.append((ni, len(seen), n))
    return out


HAN_S = {"nama": "Han", "kelamin": "l", "tahap": "dewasa", "pemain": True}
MEI_S = {"nama": "Mei", "kelamin": "p", "tahap": "dewasa", "pemain": True}


def uji():
    # reference values printed by the JDK (java.util.SplittableRandom, Cakap.benih, String.hashCode)
    for seed, want in UJI_ACAK:
        assert Acak(seed).next_long() == want & MASK, (seed, Acak(seed).next_long())
    for args, want in UJI_BENIH:
        assert benih(*args) == want, (args, benih(*args))
    assert jhash("ab") == 3105
    assert rangkai(node("x", lit("A"), KOSONG), {"x": "%2$s-%1$s %%"}) == "-A %"
    assert cocok_json({"jam": ["pagi"], "sifat": {"E": ">60"}}, {"jam": "pagi", "sifat": {"E": 70}}) == 2
    assert cocok_json({"jam": ["pagi"]}, {"jam": "malam"}) == -1
    assert cocok_json({"tahu_nama": True}, {"tahu_nama": True}) == 1
    print("uji OK")
    return 0


def main(argv):
    repo = REPO
    if "--repo" in argv:
        repo = argv[argv.index("--repo") + 1]
    if "--uji" in argv:
        return uji()
    if "--golden-tulis" in argv:
        golden_tulis(repo)
    return cek(repo, "--kombinasi" in argv)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
