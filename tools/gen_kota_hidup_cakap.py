"""Compiler of the conversation corpus (DESIGN 42.5A.8; owner 42K-T, corpus by 42K-C).

    python tools/gen_kota_hidup_cakap.py              compile tools/kota_cakap_sumber/**/*.txt, then run cek_cakap
    python tools/gen_kota_hidup_cakap.py --golden-tulis  same, then rewrite the expectations of every golden file
    python tools/gen_kota_hidup_cakap.py --uji        self-test on a built-in sample (writes only to a temp folder)
    options: --sumber DIR  --repo DIR  --tanpa-cek

Output (the generator owns these; nothing else writes them):
  src/main/resources/data/kultivator/tempat/percakapan/tata/<kelompok>.json   grammar rules (format: CakapData javadoc)
  lang-src/kota_hidup_cakap.id_id.json + .en_us.json                           every corpus text (identical keys)
A tata/*.json that a source no longer produces is renamed to *.json.deactivation (never deleted).

SOURCE FORMAT (tools/kota_cakap_sumber/**/*.txt, UTF-8, read in sorted relative-path order)
--------------------------------------------------------------------------------------------
Blank lines and lines starting with '#' are ignored. One rule:

  @kelompok kabar                    group of the following rules (output tata/kabar.json); default = the file stem
  == kabar.harga_naik                rule id: lower_snake segments joined by '.', unique over all files
  niat: BERI_KABAR                   speech act (niat.json id); omit it for a fragment (reachable through slots only)
  topik: harga                       topic id (topik.json); optional
  syarat: fakta=HARGA_NAIK label_emosi=jemu,sedih,cemas,netral sifat=E>60,A<-40 tahu_nama=true ranah_selisih=>=3
                                     CakapData syarat keys; a comma list is an array (OR); sifat is trait=comparison
  lanjut: kafilah begal              follow-up topics
  gestur: geleng                     gesture clip name (GerakId / KotaGerakId name or alias angguk, geleng, ...)
  seru: aduh                         short voice (42.5A.8 table base name: ya eh hmm oh tawa cih aduh terima_kasih maaf
                                     wah_anak tetua_hmm tetua_marah mabuk bisik takut setuju)
  $barang: tipe=barang dari=fakta.objek fitur=jamak    a typed slot (TipeSlot ids; dari/fitur/opsional optional)
  $sebab: aturan=sebab.harga opsional=0.5               a fragment slot (rule id; chance it is filled)
  $salam: lex=terima_kasih                              a lexicon slot (kultivator.cakap.lex.<word>, dialect aware)
  - [3 biasa,kasar] {$barang} naik lagi sejak {$waktu_lalu}. {$sebab} || {$barang} went up again {$waktu_lalu}. {$sebab}
    ~ sopan: Maaf, {$barang} naik lagi sejak {$waktu_lalu}. {$sebab} || Forgive me, {$barang} went up {$waktu_lalu}. {$sebab}
    ~ p: ...                         (English after || is optional on a variant: it then copies the frame's English)

Frame line "- [tags] Indonesian || English". Tags (optional): a number = weight (default 1); a comma list of registers
(sopan biasa kasar kuno) = only said in those registers; a comma list of dialect ids = only in those dialects.
Variant line "~ f[.f2]: ..." right below its frame: features l p (speaker gender), a register, a dialect id; the engine
looks up key.f1.f2 -> key.f1 -> key (order register, dialect, gender). Key of frame n of rule R: kultivator.cakap.R.n.
Placeholders: {$name} declared slot, or a typed slot used directly ({$sebutan_pendengar} / alias {$sebutan},
{$waktu_lalu}, {$barang} ...); {@rule.id} inline fragment, {@rule.id?0.4} filled with chance 0.4; {lex:word} inline
lexicon word. Both languages use the same placeholder set (order free: arguments are positional %n$s). Write % as %%.

  @teks                              plain lang keys until the next '@' or '==' line:
  niat.tanya: Bertanya || Ask        -> kultivator.cakap.niat.tanya (labels of niat, nada, opsi, isyarat, obrolan ...)

Reserved prefixes (lex., tik., bungkus. belong to the lex fragment kota_hidup_cakap_lex): no rule or @teks key may use them.
"""
import json
import os
import re
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KUNCI = "kultivator.cakap."
REGISTER = ("sopan", "biasa", "kasar", "kuno")
DIALEK = ("ibukota", "pelabuhan", "oasis", "air", "gunung", "iblis", "kampung")
KELAMIN = ("l", "p")
TIPE = ("sebutan_pendengar", "nama_warga", "ganti", "barang", "tempat", "arah", "jarak", "angka", "harga", "waktu_lalu",
        "waktu_depan", "cuaca", "musim", "kata_emosi", "seru", "tik", "pekerjaan", "hubungan")
ALIAS_TIPE = {"sebutan": "sebutan_pendengar", "nama": "nama_warga"}
DILARANG = ("lex.", "tik.", "bungkus.")
ID_ATURAN = re.compile(r"^[a-z0-9_]+(\.[a-z0-9_]+)*$")
PH = re.compile(r"\{(\$[a-z0-9_]+|@[a-z0-9_.]+(?:\?[0-9.]+)?|lex:[a-z0-9_]+)\}")
SYARAT_LIST = {"peran", "kelompok", "tahap", "watak", "kebiasaan", "jam", "cuaca", "musim", "acara", "wajah", "label_emosi",
               "kesan", "kenangan", "hubungan", "tempat", "dialek", "register"}
SYARAT_TUNGGAL = {"kelamin", "fakta", "ranah_selisih"}
SYARAT_BOOL = {"tahu_nama"}


class Galat(Exception):
    pass


def _bagi_teks(s, tempat):
    if "||" in s:
        a, b = s.split("||", 1)
        return a.strip(), b.strip()
    return s.strip(), None


def _cek_persen(teks, tempat):
    sisa = PH.sub("", teks).replace("%%", "")
    if "%" in sisa:
        raise Galat(f"{tempat}: lone % (write %%)")


def baca_sumber(folder):
    """Parses every .txt below folder. Returns (rules, teks) in source order."""
    berkas = []
    for akar, _, nama in os.walk(folder):
        for n in nama:
            if n.endswith(".txt"):
                berkas.append(os.path.join(akar, n))
    berkas.sort(key=lambda p: os.path.relpath(p, folder).replace("\\", "/"))
    aturan, teks, ids = [], [], {}
    for p in berkas:
        rel = os.path.relpath(p, folder).replace("\\", "/")
        kelompok = os.path.splitext(os.path.basename(p))[0]
        cur, mode = None, None
        with open(p, encoding="utf-8") as f:
            for no, mentah in enumerate(f, 1):
                baris = mentah.rstrip("\r\n")
                s = baris.strip()
                tempat = f"{rel}:{no}"
                if not s or s.startswith("#"):
                    continue
                if s.startswith("@kelompok"):
                    kelompok = s.split(None, 1)[1].strip()
                    if not ID_ATURAN.match(kelompok) or "." in kelompok:
                        raise Galat(f"{tempat}: bad group id {kelompok!r}")
                    cur, mode = None, None
                    continue
                if s == "@teks":
                    cur, mode = None, "teks"
                    continue
                if s.startswith("=="):
                    rid = s[2:].strip()
                    if not ID_ATURAN.match(rid):
                        raise Galat(f"{tempat}: bad rule id {rid!r}")
                    if rid.startswith(DILARANG):
                        raise Galat(f"{tempat}: reserved prefix in {rid!r}")
                    if rid in ids:
                        raise Galat(f"{tempat}: rule {rid} already defined at {ids[rid]}")
                    ids[rid] = tempat
                    cur = {"aturan": rid, "kelompok": kelompok, "niat": None, "topik": None, "syarat": {}, "lanjut": [],
                           "gestur": None, "seru": None, "slot": {}, "bingkai": [], "_tempat": tempat}
                    aturan.append(cur)
                    mode = "aturan"
                    continue
                if mode == "teks":
                    m = re.match(r"^([a-z0-9_.]+)\s*:\s*(.*)$", s)
                    if not m:
                        raise Galat(f"{tempat}: expected 'key: id || en' in @teks")
                    k = m.group(1)
                    if k.startswith(DILARANG):
                        raise Galat(f"{tempat}: reserved prefix in {k!r}")
                    a, b = _bagi_teks(m.group(2), tempat)
                    if not a or b is None or not b:
                        raise Galat(f"{tempat}: @teks needs both languages")
                    if PH.search(a) or PH.search(b):
                        raise Galat(f"{tempat}: placeholders are not allowed in @teks (use %s)")
                    teks.append((k, a, b, tempat))
                    continue
                if cur is None:
                    raise Galat(f"{tempat}: line outside a rule: {s[:40]}")
                if s.startswith("- ") or s == "-":
                    cur["bingkai"].append(_bingkai(s[1:].strip(), tempat))
                    continue
                if s.startswith("~"):
                    if not cur["bingkai"]:
                        raise Galat(f"{tempat}: variant before any frame")
                    m = re.match(r"^~\s*([a-z0-9_.]+)\s*:\s*(.*)$", s)
                    if not m:
                        raise Galat(f"{tempat}: expected '~ feature: id || en'")
                    fit = m.group(1)
                    for f_ in fit.split("."):
                        if f_ not in REGISTER + DIALEK + KELAMIN:
                            raise Galat(f"{tempat}: unknown feature {f_!r}")
                    a, b = _bagi_teks(m.group(2), tempat)
                    bk = cur["bingkai"][-1]
                    if fit in bk["varian"]:
                        raise Galat(f"{tempat}: variant {fit} twice")
                    bk["varian"][fit] = (a, b if b else bk["en"], tempat)
                    continue
                if s.startswith("$"):
                    m = re.match(r"^(\$[a-z0-9_]+)\s*:\s*(.*)$", s)
                    if not m:
                        raise Galat(f"{tempat}: expected '$name: key=value ...'")
                    cur["slot"][m.group(1)] = _slot(m.group(2), tempat)
                    continue
                m = re.match(r"^([a-z_]+)\s*:\s*(.*)$", s)
                if not m:
                    raise Galat(f"{tempat}: cannot read {s[:40]!r}")
                k, v = m.group(1), m.group(2).strip()
                if k == "niat":
                    if not re.match(r"^[A-Z0-9_]+$", v):
                        raise Galat(f"{tempat}: niat must be UPPER_SNAKE")
                    cur["niat"] = v
                elif k == "topik":
                    cur["topik"] = v
                elif k == "syarat":
                    cur["syarat"] = _syarat(v, tempat)
                elif k == "lanjut":
                    cur["lanjut"] = v.split()
                elif k == "gestur":
                    cur["gestur"] = v
                elif k == "seru":
                    cur["seru"] = v
                else:
                    raise Galat(f"{tempat}: unknown header {k!r}")
    return aturan, teks


def _bingkai(s, tempat):
    bobot, reg, dia = 1, None, None
    m = re.match(r"^\[([^\]]*)\]\s*(.*)$", s)
    if m:
        for tok in m.group(1).split():
            if re.match(r"^\d+$", tok):
                bobot = int(tok)
            elif all(x in REGISTER for x in tok.split(",")):
                reg = tok.split(",")
            elif all(x in DIALEK for x in tok.split(",")):
                dia = tok.split(",")
            else:
                raise Galat(f"{tempat}: unknown frame tag {tok!r}")
        s = m.group(2)
    a, b = _bagi_teks(s, tempat)
    if not a or not b:
        raise Galat(f"{tempat}: a frame needs 'Indonesian || English'")
    if bobot < 1:
        raise Galat(f"{tempat}: weight must be >= 1")
    return {"id": a, "en": b, "bobot": bobot, "register": reg, "dialek": dia, "varian": {}, "_tempat": tempat}


def _kv(s, tempat):
    out = {}
    for tok in s.split():
        if "=" not in tok:
            raise Galat(f"{tempat}: expected key=value, got {tok!r}")
        k, v = tok.split("=", 1)
        out[k] = v
    return out


def _slot(s, tempat):
    kv = _kv(s, tempat)
    out = {}
    if "tipe" in kv:
        t = ALIAS_TIPE.get(kv["tipe"], kv["tipe"])
        if t not in TIPE:
            raise Galat(f"{tempat}: unknown slot type {t!r}")
        out["tipe"] = t
        if "dari" in kv:
            out["dari"] = kv["dari"]
        if "fitur" in kv:
            out["fitur"] = kv["fitur"].split(",")
    elif "aturan" in kv:
        out["aturan"] = kv["aturan"]
    elif "lex" in kv:
        out["lex"] = kv["lex"]
    else:
        raise Galat(f"{tempat}: a slot needs tipe=, aturan= or lex=")
    if "opsional" in kv:
        p = float(kv["opsional"])
        if not 0.0 < p < 1.0:
            raise Galat(f"{tempat}: opsional must be in (0, 1)")
        out["opsional"] = p
    extra = set(kv) - {"tipe", "dari", "fitur", "aturan", "lex", "opsional"}
    if extra:
        raise Galat(f"{tempat}: unknown slot keys {sorted(extra)}")
    return out


def _syarat(s, tempat):
    out = {}
    for k, v in _kv(s, tempat).items():
        if k == "sifat":
            d = {}
            for part in v.split(","):
                m = re.match(r"^([HEXACO])((?:>=|<=|>|<|=)-?\d+)$", part)
                if not m:
                    raise Galat(f"{tempat}: bad sifat {part!r}")
                d[m.group(1)] = m.group(2)
            out["sifat"] = d
        elif k in SYARAT_BOOL:
            if v not in ("true", "false"):
                raise Galat(f"{tempat}: {k} must be true/false")
            out[k] = v == "true"
        elif k in SYARAT_TUNGGAL:
            out[k] = v
        elif k in SYARAT_LIST:
            out[k] = v.split(",")
        else:
            raise Galat(f"{tempat}: unknown syarat key {k!r}")
    return out


def _ganti_ph(teks, urutan, tempat):
    def r(m):
        return f"%{urutan.index(m.group(1)) + 1}$s"
    return PH.sub(r, teks)


def _nama_slot(ph):
    if ph.startswith("$"):
        return ph
    if ph.startswith("@"):
        return "$@" + ph[1:].split("?")[0].replace(".", "_")
    return "$lex_" + ph[4:]


def susun(aturan, teks):
    """Compiles parsed rules into (groups {kelompok: [rule json]}, lang_id, lang_en)."""
    kelompok, lid, len_ = {}, {}, {}

    def taruh(k, a, b, tempat):
        if k in lid:
            raise Galat(f"{tempat}: duplicate lang key {k}")
        lid[k], len_[k] = a, b

    for r in aturan:
        if not r["bingkai"]:
            raise Galat(f"{r['_tempat']}: rule {r['aturan']} has no frame")
        slot = dict(r["slot"])
        bingkai = []
        for i, b in enumerate(r["bingkai"]):
            tempat = b["_tempat"]
            ph_id = [m.group(1) for m in PH.finditer(b["id"])]
            ph_en = [m.group(1) for m in PH.finditer(b["en"])]
            if set(ph_id) != set(ph_en):
                raise Galat(f"{tempat}: placeholders differ id {sorted(set(ph_id))} / en {sorted(set(ph_en))}")
            urutan = []
            for ph in ph_id:
                if ph not in urutan:
                    urutan.append(ph)
            # a variant may use extra slots (appended after the frame's own; strings that do not use one ignore it)
            for fit, (a, e, t2) in b["varian"].items():
                va = [m.group(1) for m in PH.finditer(a)]
                if set(va) != set(m.group(1) for m in PH.finditer(e)):
                    raise Galat(f"{t2}: placeholders of variant {fit} differ between id and en")
                for ph in va:
                    if ph not in urutan:
                        urutan.append(ph)
            nama_slot = []
            for ph in urutan:
                n = _nama_slot(ph)
                if ph.startswith("$"):
                    if n not in slot:
                        t = ALIAS_TIPE.get(n[1:], n[1:])
                        if t not in TIPE:
                            raise Galat(f"{tempat}: slot {n} is not declared and is no slot type")
                        slot[n] = {"tipe": t}
                elif ph.startswith("@"):
                    rid = ph[1:].split("?")[0]
                    spec = {"aturan": rid}
                    if "?" in ph:
                        p = float(ph.split("?")[1])
                        if not 0.0 < p < 1.0:
                            raise Galat(f"{tempat}: chance in {ph} must be in (0, 1)")
                        spec["opsional"] = p
                    if n in slot and slot[n] != spec:
                        raise Galat(f"{tempat}: inline {ph} conflicts with an earlier use of the same fragment")
                    slot[n] = spec
                else:
                    slot[n] = {"lex": ph[4:]}
                nama_slot.append(n)
            kunci = f"{r['aturan']}.{i}"
            for t_, txt in (("id", b["id"]), ("en", b["en"])):
                _cek_persen(txt, tempat)
            taruh(KUNCI + kunci, _ganti_ph(b["id"], urutan, tempat), _ganti_ph(b["en"], urutan, tempat), tempat)
            for fit, (a, e, t2) in b["varian"].items():
                _cek_persen(a, t2)
                _cek_persen(e, t2)
                taruh(KUNCI + kunci + "." + fit, _ganti_ph(a, urutan, t2), _ganti_ph(e, urutan, t2), t2)
            bj = {"kunci": kunci, "slot": nama_slot, "bobot": b["bobot"]}
            if b["register"]:
                bj["register"] = b["register"]
            if b["dialek"]:
                bj["dialek"] = b["dialek"]
            if b["varian"]:
                bj["varian"] = list(b["varian"])
            bingkai.append(bj)
        rj = {"aturan": r["aturan"]}
        if r["niat"]:
            rj["niat"] = r["niat"]
        if r["topik"]:
            rj["topik"] = r["topik"]
        if r["syarat"]:
            rj["syarat"] = r["syarat"]
        rj["bingkai"] = bingkai
        rj["slot"] = slot
        if r["lanjut"]:
            rj["lanjut"] = r["lanjut"]
        if r["gestur"]:
            rj["gestur"] = r["gestur"]
        if r["seru"]:
            rj["seru"] = r["seru"]
        kelompok.setdefault(r["kelompok"], []).append(rj)
    for k, a, b, tempat in teks:
        taruh(KUNCI + k, a, b, tempat)
    return kelompok, lid, len_


def tulis_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = json.dumps(obj, ensure_ascii=False, indent=2) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(data)


def tulis(repo, kelompok, lid, len_):
    tata = os.path.join(repo, "src/main/resources/data/kultivator/tempat/percakapan/tata")
    os.makedirs(tata, exist_ok=True)
    for k, isi in kelompok.items():
        tulis_json(os.path.join(tata, k + ".json"),
                   {"_dibuat": "tools/gen_kota_hidup_cakap.py dari tools/kota_cakap_sumber (jangan disunting tangan)",
                    "kelompok": k, "isi": isi})
    for n in sorted(os.listdir(tata)):
        if n.endswith(".json") and n[:-5] not in kelompok:
            dst = os.path.join(tata, n + ".deactivation")
            if os.path.exists(dst):
                os.replace(os.path.join(tata, n), dst)
            else:
                os.rename(os.path.join(tata, n), dst)
            print(f"stale tata/{n} -> {n}.deactivation")
    lang = os.path.join(repo, "lang-src")
    tulis_json(os.path.join(lang, "kota_hidup_cakap.id_id.json"), lid)
    tulis_json(os.path.join(lang, "kota_hidup_cakap.en_us.json"), len_)


def jumlah_terminal(lid):
    return len(lid)


CONTOH = """# self-test sample (not shipped)
@kelompok kabar
== kabar.harga_naik
niat: BERI_KABAR
topik: harga
syarat: fakta=HARGA_NAIK label_emosi=jemu,sedih,cemas,netral
lanjut: kafilah begal
gestur: geleng
seru: aduh
$barang: tipe=barang dari=fakta.objek fitur=jamak
- [3] {$barang} naik lagi sejak {$waktu_lalu}. {@seru.keluh?0.6} || {$barang} went up again {$waktu_lalu}. {@seru.keluh?0.6}
  ~ sopan: Maaf, {$sebutan}... {$barang} naik lagi sejak {$waktu_lalu}. {@seru.keluh?0.6} || Forgive me, {$sebutan}... {$barang} went up again {$waktu_lalu}. {@seru.keluh?0.6}
- [2 biasa,kasar] {$barang} naik, {@sebab.harga?0.5} || {$barang} is up, {@sebab.harga?0.5}
- [1 pelabuhan] Ombak harga, {lex:bung}! {$barang} naik. || Price waves, {lex:bung}! {$barang} went up.
== sebab.harga
- Katanya kafilah tertahan. || They say the caravan is held up.
- Katanya jalan ke {$arah} rusak. || They say the road {$arah} is broken.
== seru.keluh
- Aduh... || Oh dear...
- Mau bagaimana lagi. || What can one do.
@teks
niat.beri_kabar: Beri kabar || Share news
"""


def uji():
    d = tempfile.mkdtemp(prefix="cakap_uji_")
    src = os.path.join(d, "sumber")
    os.makedirs(src)
    with open(os.path.join(src, "kabar.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(CONTOH)
    aturan, teks = baca_sumber(src)
    kel, lid, len_ = susun(aturan, teks)
    assert list(kel) == ["kabar"], kel
    r0 = kel["kabar"][0]
    assert r0["bingkai"][0]["slot"] == ["$barang", "$waktu_lalu", "$@seru_keluh", "$sebutan"], r0["bingkai"][0]
    assert r0["slot"]["$sebutan"] == {"tipe": "sebutan_pendengar"}
    assert r0["slot"]["$@seru_keluh"] == {"aturan": "seru.keluh", "opsional": 0.6}
    assert r0["slot"]["$waktu_lalu"] == {"tipe": "waktu_lalu"}
    assert r0["bingkai"][0]["varian"] == ["sopan"]
    assert r0["bingkai"][1]["register"] == ["biasa", "kasar"]
    assert r0["bingkai"][2]["dialek"] == ["pelabuhan"] and r0["slot"]["$lex_bung"] == {"lex": "bung"}
    assert lid["kultivator.cakap.kabar.harga_naik.0"] == "%1$s naik lagi sejak %2$s. %3$s"
    # the sopan variant adds $sebutan as the 4th argument; the base string simply does not use it
    assert lid["kultivator.cakap.kabar.harga_naik.0.sopan"].startswith("Maaf, %4$s..."), lid
    assert len_["kultivator.cakap.niat.beri_kabar"] == "Share news"
    for bad, why in (("== a\n- x {$nope} || y {$nope}\n", "not declared"), ("== a\n- x {$barang} || y\n", "differ"),
                     ("== lex.a\n- x || y\n", "reserved"), ("== a\n- 50% || 50%\n", "lone %")):
        with open(os.path.join(src, "kabar.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write(bad)
        try:
            susun(*baca_sumber(src))
        except Galat as e:
            assert why.split()[0] in str(e) or why in str(e), (why, str(e))
        else:
            raise AssertionError("accepted bad source: " + why)
    print("uji OK:", d)


def main(argv):
    repo, sumber, cek, golden = REPO, None, True, False
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--uji":
            uji()
            return 0
        if a == "--repo":
            repo = argv[i + 1]
            i += 1
        elif a == "--sumber":
            sumber = argv[i + 1]
            i += 1
        elif a == "--tanpa-cek":
            cek = False
        elif a == "--golden-tulis":
            golden = True
        else:
            print("unknown option", a)
            return 2
        i += 1
    sumber = sumber or os.path.join(repo, "tools", "kota_cakap_sumber")
    if not os.path.isdir(sumber):
        print("no source folder", sumber)
        return 1
    try:
        aturan, teks = baca_sumber(sumber)
        kel, lid, len_ = susun(aturan, teks)
    except Galat as e:
        print("ERROR", e)
        return 1
    tulis(repo, kel, lid, len_)
    nb = sum(len(r["bingkai"]) for v in kel.values() for r in v)
    print(f"{len(aturan)} rules, {nb} frames, {jumlah_terminal(lid)} terminals, {len(kel)} groups")
    if not cek and not golden:
        return 0
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import cek_cakap
    if golden:
        cek_cakap.golden_tulis(repo)
    return cek_cakap.main(["--repo", repo]) if cek else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
