"""Repository validator of every DESIGN 38 place (38.D.2, 38.T.2.4; owner 38W). Run after each generator and at the
finish step:   python tools/cek_tempat.py [--soft]

Checks (each prints ERROR lines; exit code 1 when any error, unless --soft):
  1. Templates under data/kultivator/structure/{38 folders}: size <= 48 x 48, anchors <= 6, titik <= 96 with legal
     types (tempat_lib.JENIS_TITIK / kerja:<ALAT>), box titik carry dx/dy/dz, kultivator_sisi codes legal, forbidden
     blocks absent (fire, soul_fire, lava, tnt, spawner, lightning_rod, lit campfires outside the kitchen list).
  2. tata_letak structures: every lot pool exists (also every <pool>_<akhiran> style pool or a base fallback), lot
     grid of a district has no hole and no overlap, anchors per district <= 40, empty monster spawn_overrides.
  3. Structure sets: salts unique across data/** and resourcepacks/**; 38 salts inside the 38 ranges; every set of a
     38 structure uses a 38 placement (cincin_tempat with min_chunks, rencana_tempat or jalan).
  4. Themed cities (38.T.2.4): neighbouring lots' side codes match after rotation; a canal side (K) never ends on a
     non-canal side except at lots named kolam/danau.
  5. Budgets (38.C.10, 38.T.2.4): gzip bytes per place family; points per template and anchors per district by family
     (capital 96/40, compound 64/24, village 32/12, cave and site 24/16, post 24/6, themed city 96/24).
  6. Themed cities (38.T.2.3): every kota_tema/<tema>/<distrik> set uses the salt of Tema.java (one source of truth,
     parsed from the Java enum), satellites 380451/380452; every themed structure is in #kultivator:kota_tema.
  7. Tags (38.D.1, 38.R.1, owner 38W): every inhabited 38 set present is in #kultivator:tempat_berpenghuni and every
     cared-for 38 structure in #kultivator:tempat_dirawat (directly, through a tag, or as an optional entry).
  8. Titik of DESIGN 41.11.2 (owner 41W-G): every kompleks/<what> anchor names a known compound spot (38.C.3.3 or the
     41 list, cross-checked against TitikGabung.java); every compound district holds its 41 titik exactly once (the
     Iblis court two altar_sumpah, one marked aliran=iblis); every sect gate template (structure/sekte/gerbang_*) holds
     sektenpc/ujian_bawah, ujian_atas, loket, bilik_0, bilik_1 and altar exactly once. A compound lot may carry up to
     tempat_lib.maks_jangkar("kompleks_sekte") anchors and a compound district up to 32.
Templates are read with tools/nbt.py; only the repo is read, nothing is written.
"""
import gzip
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
import tempat_lib as tl  # noqa: E402
import kota_hidup_kit  # noqa: E402,F401  (registers the DESIGN 42 titik types and kavling as a box titik)

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(REPO, "src", "main", "resources")
DATA = os.path.join(RES, "data", "kultivator")
PACKS = os.path.join(RES, "resourcepacks")

# Structure folders that belong to DESIGN 38 (structure/<folder>/**) and their gzip budget in bytes (38.C.10).
FOLDERS = {
    "kota_besar": 3_600_000,     # 42.12.1: 3.0 -> 3.6 MB (night district, kantor_rekrut, gudang_kota, kavling, makam)
    "kompleks_sekte": 1_200_000,
    "kampung": 1_800_000,        # 42.12.1: 1.5 -> 1.8 MB (kavling, makam desa, kandang)
    "gua": 760_000,     # 38.C.10: gua + situs share 1 MB (D-GU: gua near the old 700 KB, situs uses ~128 KB)
    "situs": 240_000,
    "jalan": 200_000,
    "pos": 600_000,         # 38W: 0.4 MB in 38.C.10; the 40 x 40 inns with rooms and the caravanserai need 0.43 MB,
                            # D-W's extra stelae, shrines, fortified bandit camp, the pond rest post (singgah_b, 90 KB)
                            # and interior dressing bring it to about 0.55 MB (jar size only, no runtime cost)
    "kota_tumbuh": 2_400_000,    # 42.12.1 / 42.9.4: ~110 growth plans x 5 cumulative stages (read by the server only)
    "kota_tema": 7_200_000,  # 42.12.1: 6.2 -> 7.2 MB (jalan_malam, kavling, makam, kantor_rekrut). F2: 5 themes x TEMA_BUDGET 1.2 MB + satellites 0.2 MB (38.T budgets cannot fit 5.5 MB)
}
TEMA_BUDGET = 1_400_000          # per theme folder kota_tema/<tema> (42.12.1: +0.2 MB per theme)
# 38.C.10 / 38.T.2.4: (points per template, anchors per district or start) by structure folder.
BATAS = {
    "kota_besar": (96, 40),
    "kompleks_sekte": (64, 32),   # 41.11.2: + the 41 titik (a small court with a sky pier reaches 24 + 4)
    "kampung": (32, 12),
    "gua": (24, 16),
    "situs": (24, 16),
    "jalan": (0, 0),
    "pos": (48, 6),       # 24 in 38.C.10; an inn lot has a bed point per guest bed and table seats (38W, <= 39)
    "kota_tema": (96, 24),
    "kota_tumbuh": (96, 0),     # growth stages carry titik (stage 5 interior) but never anchors
}
# Inhabited set folders (tempat_berpenghuni) and cared-for structure folders (tempat_dirawat, 38.R.1).
BERPENGHUNI = ("kota_besar", "kampung", "kompleks_sekte", "kota_tema")
BERPENGHUNI_POS = ("pos/singgah", "pos/singgah_besar", "pos/dagang", "kota_tema/pos_pemburu")
BUKAN_BERPENGHUNI = ("kota_tema/reruntuhan_gurun",)
DIRAWAT = ("kota_besar", "kampung", "kompleks_sekte", "kota_tema")
TEMA_JAVA = os.path.join(REPO, "src", "main", "java", "dev", "kultivator", "dunia", "tempat", "tema", "Tema.java")
PLACEMENTS_38 = ("kultivator:cincin_tempat", "kultivator:rencana_tempat", "kultivator:jalan")
# Salt ranges reserved for 38 (38.B.2.3, 38.C.2.4, 38.C.3.1, 38.C.6.2, 38.T.2.3; gua/situs/pos 380501-380599).
SALT_38 = ((380001, 380011), (380101, 380104), (380201, 380203), (380301, 380399), (380401, 380452),
           (380501, 380599))
FORBIDDEN = ("minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:tnt", "minecraft:spawner",
             "minecraft:lightning_rod")
# Lit campfires are allowed only in these template path fragments (kitchens and alchemy stations).
CAMPFIRE_OK = ("dapur", "alkimia", "alkemis", "warung", "rumah_makan", "kuali")

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def rel(path):
    return os.path.relpath(path, REPO).replace("\\", "/")


# --------------------------------------------------------------------------------------------------- templates
def template_id_of(path):
    sub = os.path.relpath(path, os.path.join(DATA, "structure")).replace("\\", "/")
    return "kultivator:" + sub[:-4]


def load_templates():
    """{template id: (path, root compound)} for every 38 folder."""
    out = {}
    for folder in FOLDERS:
        base = os.path.join(DATA, "structure", folder)
        if not os.path.isdir(base):
            continue
        for d, _, files in os.walk(base):
            for f in files:
                if f.endswith(".nbt"):
                    p = os.path.join(d, f)
                    try:
                        _, root = nbt.read_file(p)
                    except Exception as e:  # noqa: BLE001
                        err("%s: unreadable NBT (%s)" % (rel(p), e))
                        continue
                    out[template_id_of(p)] = (p, root)
    return out


def check_template(tid, path, root):
    where = rel(path)
    size = [int(v) for v in root.get("size", [0, 0, 0])]
    if size[0] > tl.LOT or size[2] > tl.LOT:
        err("%s: size %s exceeds %dx%d" % (where, size, tl.LOT, tl.LOT))
    ents = root.get("entities", [])
    anchors = [e for e in ents if (e.get("nbt") or {}).get("id") == "kultivator:dunia_anchor"]
    if len(anchors) > tl.maks_jangkar(tid):
        err("%s: %d anchors > %d" % (where, len(anchors), tl.maks_jangkar(tid)))
    pts = root.get("kultivator_titik", [])
    if len(pts) > tl.MAKS_TITIK:
        err("%s: %d titik > %d" % (where, len(pts), tl.MAKS_TITIK))
    family = tid.split(":", 1)[1].split("/")[0]
    limit = BATAS.get(family, (tl.MAKS_TITIK, 0))[0]
    if family != "jalan" and len(pts) > limit:
        err("%s: %d titik > %d for %s" % (where, len(pts), limit, family))
    for p in pts:
        j = p.get("jenis", "")
        if not tl.titik_sah(j):
            err("%s: unknown titik %r" % (where, j))
        pos = [int(v) for v in p.get("pos", [-1, -1, -1])]
        if not all(0 <= pos[i] < size[i] for i in range(3)):
            err("%s: titik %s at %s outside size %s" % (where, j, pos, size))
        if p.get("hadap") not in ("north", "east", "south", "west"):
            err("%s: titik %s bad hadap %r" % (where, j, p.get("hadap")))
        if j in tl.TITIK_KOTAK and not all(k in (p.get("data") or {}) for k in ("dx", "dy", "dz")):
            err("%s: box titik %s without dx/dy/dz" % (where, j))
    sisi = root.get("kultivator_sisi")
    if sisi is not None and (set(sisi) != set(tl.SISI) or any(v not in tl.KODE_SISI for v in sisi.values())):
        err("%s: bad kultivator_sisi %r" % (where, sisi))
    palette = root.get("palette") or (root.get("palettes") or [[]])[0]
    used = {}
    for b in root.get("blocks", []):
        st = palette[int(b["state"])]
        used.setdefault(int(b["state"]), st)
    for st in used.values():
        bid = st.get("id") or st.get("Name")
        props = st.get("properties") or st.get("Properties") or {}
        if bid in FORBIDDEN:
            err("%s: forbidden block %s" % (where, bid))
        if bid == "minecraft:respawn_anchor" and str(props.get("charges", "0")) != "0":
            err("%s: charged respawn_anchor" % where)
        if bid in ("minecraft:campfire", "minecraft:soul_campfire") and str(props.get("lit", "true")) == "true":
            if not any(k in tid for k in CAMPFIRE_OK):
                err("%s: lit %s outside a kitchen/alchemy template" % (where, bid))
    return len(anchors)


# --------------------------------------------------------------------------------------------------------- JSON
def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def walk_json(base):
    if not os.path.isdir(base):
        return
    for d, _, files in os.walk(base):
        for f in files:
            if f.endswith(".json"):
                yield os.path.join(d, f)


def id_of(path, kind_root):
    sub = os.path.relpath(path, kind_root).replace("\\", "/")
    return "kultivator:" + sub[:-5]


def all_worldgen(kind):
    """{id: (path, json)} for a worldgen kind from data/kultivator and every resourcepack's data/kultivator."""
    out = {}
    roots = [os.path.join(DATA, "worldgen", kind)]
    if os.path.isdir(PACKS):
        for pk in sorted(os.listdir(PACKS)):
            roots.append(os.path.join(PACKS, pk, "data", "kultivator", "worldgen", kind))
    for r in roots:
        for p in walk_json(r):
            try:
                out[id_of(p, r)] = (p, read_json(p))
            except Exception as e:  # noqa: BLE001
                err("%s: bad JSON (%s)" % (rel(p), e))
    return out


def all_salts():
    """[(salt, path)] across data/** and resourcepacks/** structure sets of every namespace."""
    out = []
    for top in [os.path.join(RES, "data")] + ([os.path.join(PACKS, p, "data") for p in os.listdir(PACKS)]
                                               if os.path.isdir(PACKS) else []):
        for p in walk_json(top):
            if "/worldgen/structure_set/" not in p.replace("\\", "/"):
                continue
            try:
                j = read_json(p)
            except Exception:  # noqa: BLE001
                continue
            salt = (j.get("placement") or {}).get("salt")
            if salt is not None:
                out.append((int(salt), p))
    return out


def in_38_range(salt):
    return any(a <= salt <= b for a, b in SALT_38)


def is_38_structure(sid):
    path = sid.split(":", 1)[1]
    return path.split("/", 1)[0] in FOLDERS


# ------------------------------------------------------------------------------------------------- tata_letak
def pool_templates(pools, pid):
    entry = pools.get(pid)
    if entry is None:
        return None
    out = []
    for e in entry[1].get("elements", []):
        el = e.get("element", {})
        loc = el.get("location")
        if isinstance(loc, str):
            out.append(loc)
        for sub in el.get("elements", []) if el.get("element_type") == "minecraft:list_pool_element" else []:
            if isinstance(sub.get("location"), str):
                out.append(sub["location"])
    return out


def check_tata_letak(sid, path, s, pools, templates):
    where = rel(path)
    spawn = (s.get("spawn_overrides") or {}).get("monster")
    if not spawn or spawn.get("spawns"):
        err("%s: inhabited place without empty monster spawn_overrides" % where)
    lots = s.get("lot", [])
    if not lots:
        err("%s: tata_letak without lots" % where)
    seen = set()
    anchors = 0
    xs, zs = set(), set()
    for i, lot in enumerate(lots):
        pid = lot.get("pool", "")
        tids = pool_templates(pools, pid)
        if tids is None:
            err("%s: lot %d pool %s missing" % (where, i, pid))
            continue
        for g in s.get("gaya", []):
            if pool_templates(pools, "%s_%s" % (pid, g.get("akhiran"))) is None:
                warn("%s: lot %d has no style pool %s_%s (falls back to base)" % (where, i, pid, g.get("akhiran")))
        key = (int(lot.get("dx", 0)), int(lot.get("dz", 0)), int(lot.get("dy", 0)))
        if key in seen:
            err("%s: two lots at dx=%d dz=%d" % (where, key[0], key[1]))
        seen.add(key)
        best = 0
        for t in tids:
            if t not in templates:
                err("%s: lot %d template %s missing" % (where, i, t))
                continue
            root = templates[t][1]
            n = len([e for e in root.get("entities", []) if (e.get("nbt") or {}).get("id") == "kultivator:dunia_anchor"])
            best = max(best, n)
        anchors += best
        if abs(key[0]) % tl.LOT == 0 and abs(key[1]) % tl.LOT == 0:
            xs.add(key[0])
            zs.add(key[1])
    family = sid.split(":", 1)[1].split("/")[0]
    limit = min(tl.MAKS_JANGKAR_DISTRIK, BATAS.get(family, (0, tl.MAKS_JANGKAR_DISTRIK))[1])
    if anchors > limit:
        err("%s: up to %d anchors in one district > %d (%s)" % (where, anchors, limit, family))
    # grid holes: every (x, z) of the 48-grid spanned by the grid lots must be used (wall lots off-grid are free)
    grid = {(k[0], k[1]) for k in seen if abs(k[0]) % tl.LOT == 0 and abs(k[1]) % tl.LOT == 0}
    if grid:
        for x in range(min(xs), max(xs) + 1, tl.LOT):
            for z in range(min(zs), max(zs) + 1, tl.LOT):
                if (x, z) not in grid:
                    err("%s: hole in the lot grid at dx=%d dz=%d" % (where, x, z))


def check_tema_sides(sid, path, s, pools, templates):
    """38.T.2.4: neighbouring lots' side codes must match after each lot's rotation (putar)."""
    where = rel(path)
    cells = {}
    for lot in s.get("lot", []):
        tids = pool_templates(pools, lot.get("pool", "")) or []
        sisi = None
        for t in tids:
            if t in templates and templates[t][1].get("kultivator_sisi"):
                cand = tl.putar_sisi(templates[t][1]["kultivator_sisi"], int(lot.get("putar", 0)) % 4)
                if sisi is not None and cand != sisi:
                    err("%s: pool %s mixes templates with different side codes" % (where, lot.get("pool")))
                sisi = cand
        if sisi is None:
            continue
        cx, cz = int(lot.get("dx", 0)) // tl.LOT, int(lot.get("dz", 0)) // tl.LOT
        cells[(cx, cz)] = (sisi, lot.get("pool", ""))
    for (cx, cz), (sisi, pid) in cells.items():
        for dx, dz, mine, theirs in ((1, 0, "e", "w"), (0, 1, "s", "n")):
            other = cells.get((cx + dx, cz + dz))
            if other is None:
                continue
            if sisi[mine] != other[0][theirs]:
                err("%s: side %s of %s (%s) meets %s (%s)" % (where, mine, pid, sisi[mine], other[1], other[0][theirs]))
        for side, (dx, dz) in (("n", (0, -1)), ("e", (1, 0)), ("s", (0, 1)), ("w", (-1, 0))):
            if sisi[side] == "K" and (cx + dx, cz + dz) not in cells and not any(k in pid for k in ("kolam", "danau")):
                warn("%s: canal side %s of %s ends at the district edge" % (where, side, pid))


# ---------------------------------------------------------------------------------------------- themed salts
def tema_salts():
    """{"kota_tema/<tema>/<distrik>": salt} parsed from Tema.java (enum constants and their Distrik lists)."""
    import re
    out = {}
    if not os.path.exists(TEMA_JAVA):
        return out
    src = open(TEMA_JAVA, encoding="utf-8").read()
    for m in re.finditer(r'^\s*[A-Z]+\("([a-z_]+)",(.*?)\)\)\s*[,;]', src, re.S | re.M):
        tema = m.group(1)
        for d in re.finditer(r'new Distrik\("([a-z_]+)",\s*-?\d+,\s*-?\d+,\s*(\d+)', m.group(2)):
            out["kota_tema/%s/%s" % (tema, d.group(1))] = int(d.group(2))
    sat = {"SALT_RERUNTUHAN_GURUN": "kota_tema/reruntuhan_gurun", "SALT_POS_PEMBURU": "kota_tema/pos_pemburu"}
    for const, sid in sat.items():
        m = re.search(const + r"\s*=\s*(\d+)", src)
        if m:
            out[sid] = int(m.group(1))
    return out


def check_tema(sets, structures):
    expect = tema_salts()
    if not expect:
        warn("Tema.java not found or unparsable: themed salts not checked")
        return
    for sid, (path, st) in sorted(sets.items()):
        key = sid.split(":", 1)[1]
        if not key.startswith("kota_tema/"):
            continue
        salt = (st.get("placement") or {}).get("salt")
        if key not in expect:
            err("%s: themed set %s is not a district of Tema.java" % (rel(path), key))
        elif salt != expect[key]:
            err("%s: salt %s, Tema.java says %d" % (rel(path), salt, expect[key]))
    tag = os.path.join(DATA, "tags", "worldgen", "structure", "kota_tema.json")
    members = tag_values(tag)
    for sid in structures:
        if sid.startswith("kultivator:kota_tema/") and members is not None and sid not in members:
            err("%s missing from #kultivator:kota_tema" % sid)


# ------------------------------------------------------------------------------------------------------- tags
def tag_values(path):
    """The ids of a tag file (plain strings and {"id": ...} entries), or None when the file is missing."""
    if not os.path.exists(path):
        return None
    out = set()
    for v in read_json(path).get("values", []):
        out.add(v["id"] if isinstance(v, dict) else v)
    return out


def tag_cover(tag_dir, name, wanted_id, seen=None):
    """True when wanted_id is in tag `name` directly or through a nested #tag (kultivator namespace only)."""
    seen = seen or set()
    if name in seen:
        return False
    seen.add(name)
    vals = tag_values(os.path.join(tag_dir, name.split(":", 1)[1] + ".json"))
    if not vals:
        return False
    if wanted_id in vals:
        return True
    return any(v.startswith("#kultivator:") and tag_cover(tag_dir, v[1:], wanted_id, seen) for v in vals)


def check_tags(sets, structures):
    tags = os.path.join(DATA, "tags", "worldgen")
    for sid, (path, st) in sorted(sets.items()):
        key = sid.split(":", 1)[1]
        if key in BUKAN_BERPENGHUNI:
            continue
        if key.split("/")[0] in BERPENGHUNI or key in BERPENGHUNI_POS:
            if not tag_cover(os.path.join(tags, "structure_set"), "kultivator:tempat_berpenghuni", sid):
                err("%s: inhabited set %s missing from #kultivator:tempat_berpenghuni" % (rel(path), sid))
    for sid in sorted(structures):
        key = sid.split(":", 1)[1]
        if key in BUKAN_BERPENGHUNI:
            continue
        if key.split("/")[0] in DIRAWAT or key in BERPENGHUNI_POS:
            if not tag_cover(os.path.join(tags, "structure"), "kultivator:tempat_dirawat", sid):
                err("structure %s missing from #kultivator:tempat_dirawat" % sid)


# ---------------------------------------------------------------------------------------------- DESIGN 41 titik
TITIK_GABUNG_JAVA = os.path.join(REPO, "src", "main", "java", "dev", "kultivator", "sekte", "gabung", "dunia",
                                 "TitikGabung.java")
KOMPLEKS_38 = ("situs", "gerbang", "aula", "arena", "pasar", "dalam", "puncak", "tetua", "dermaga_awan")
T41_PAVILIUN = ("jasa", "jasa_meja", "papan_misi", "pesanan")
T41_AULA = ("altar_sumpah", "hukum")
T41_LAPANGAN = ("ujian_corak", "kandang", "lorong") + tuple("pilar_%d" % i for i in range(5))
T41_KEBUN = ("kerja:sumur", "kerja:tong")
T41_GUA = tuple("kediaman_%d" % i for i in range(6)) + ("gua_renungan",)
T41_DALAM = tuple("kediaman_dalam_%d" % i for i in range(4))
T41_PUNCAK = ("kediaman_puncak_0", "kediaman_puncak_1")
T41_TANGGA = ("ujian_bawah", "ujian_atas")
T41_SEMUA = T41_PAVILIUN + T41_AULA + T41_LAPANGAN + T41_KEBUN + T41_GUA + T41_DALAM + T41_PUNCAK + T41_TANGGA + ("ujian_darah",)
GERBANG_41 = ("ujian_bawah", "ujian_atas", "loket", "bilik_0", "bilik_1", "altar")


def wajib41(aliran, distrik):
    """The 41 titik a compound district of `aliran` must hold exactly once (41.11.2)."""
    if distrik.startswith("halaman"):
        need = T41_PAVILIUN + T41_AULA + T41_LAPANGAN
        if aliran == "lurus":
            need += T41_KEBUN
        if distrik.startswith("halaman_kecil"):
            need += T41_PUNCAK
        return need
    if distrik == "dalam":
        return T41_GUA + T41_DALAM + T41_TANGGA + (("ujian_darah",) if aliran == "iblis" else ())
    if distrik.startswith("puncak_sedang"):
        return T41_PUNCAK + T41_GUA + T41_TANGGA
    if distrik.startswith("puncak"):
        return T41_PUNCAK
    return ()


def _anchors(root):
    return [(e.get("nbt") or {}) for e in root.get("entities", []) if (e.get("nbt") or {}).get("id") == "kultivator:dunia_anchor"]


def check_titik41(structures, pools, templates):
    if os.path.exists(TITIK_GABUNG_JAVA):
        with open(TITIK_GABUNG_JAVA, encoding="utf-8") as f:
            java = f.read()
        for name in T41_SEMUA:
            lit = name if not name[-1].isdigit() else name.rstrip("0123456789")
            if '"%s"' % lit not in java:
                err("TitikGabung.java has no titik %r (41.11.2 list of tools/cek_tempat.py)" % lit)
        for name in GERBANG_41:
            lit = name if not name[-1].isdigit() else name.rstrip("0123456789")
            if '"%s"' % lit not in java and 'GERBANG + "%s"' % lit not in java:
                err("TitikGabung.java has no gate titik %r" % lit)
    else:
        warn("TitikGabung.java missing: 41 titik names not cross-checked")
    known = set(KOMPLEKS_38) | set(T41_SEMUA)
    for tid, (path, root) in sorted(templates.items()):
        if not tid.startswith("kultivator:kompleks_sekte/"):
            continue
        for a in _anchors(root):
            kind = str(a.get("kind", ""))
            if kind.startswith("kompleks/") and kind.split("/", 1)[1] not in known:
                err("%s: unknown compound titik %s" % (rel(path), kind))
    for sid, (path, s_) in sorted(structures.items()):
        if not sid.startswith("kultivator:kompleks_sekte/") or s_.get("type") != "kultivator:tata_letak":
            continue
        aliran, distrik = sid.split("/", 1)[1].split("_", 1)
        count = {}
        marked = 0
        for lot in s_.get("lot", []):
            tids = pool_templates(pools, lot.get("pool", "")) or []
            for t in tids[:1]:
                if t not in templates:
                    continue
                for a in _anchors(templates[t][1]):
                    kind = str(a.get("kind", ""))
                    if not kind.startswith("kompleks/"):
                        continue
                    what = kind.split("/", 1)[1]
                    count[what] = count.get(what, 0) + 1
                    if what == "altar_sumpah" and str((a.get("data") or {}).get("aliran", "")) == "iblis":
                        marked += 1
        for what in wajib41(aliran, distrik):
            n = count.get(what, 0)
            want = 2 if (what == "altar_sumpah" and aliran == "iblis") else 1
            if n != want:
                err("%s: 41 titik %s %d times (want %d)" % (rel(path), what, n, want))
        if aliran == "iblis" and distrik.startswith("halaman") and marked != 1:
            err("%s: the Iblis court needs one altar_sumpah marked aliran=iblis (has %d)" % (rel(path), marked))
        for what, n in count.items():
            if what in T41_SEMUA and n > 1 and not (what == "altar_sumpah" and aliran == "iblis"):
                err("%s: 41 titik %s appears %d times" % (rel(path), what, n))
    gdir = os.path.join(DATA, "structure", "sekte")
    for size in ("kecil", "sedang", "besar"):
        gp = os.path.join(gdir, "gerbang_%s.nbt" % size)
        if not os.path.exists(gp):
            err("%s missing" % rel(gp))
            continue
        _, root = nbt.read_file(gp)
        kinds = [str(a.get("kind", "")) for a in _anchors(root)]
        for what in GERBANG_41:
            n = kinds.count("sektenpc/" + what)
            if n != 1:
                err("%s: gate titik sektenpc/%s %d times (want 1)" % (rel(gp), what, n))


# ------------------------------------------------------------------------------------------------------- budgets
def gz_size(path):
    return os.path.getsize(path)


def check_hidup42(templates):
    """DESIGN 42 (D-42W): every kavling point names a growth plan of rencana.json of its own class and size, every plan has its
    five stage templates, every `dijual` house has its `bangunan` box, and no template still carries a 42-only work tool
    (the mod reads the 38 type, kota_hidup_kit.ALIAS_ALAT). Returns the number of kavling points seen."""
    path = os.path.join(DATA, "structure", "kota_tumbuh", "rencana.json")
    plans = (read_json(path) or {}).get("rencana", {}) if os.path.exists(path) else {}
    for key, plan in sorted(plans.items()):
        for st in plan.get("tahap", []):
            if st.get("template") not in templates:
                err("%s: plan %s names a missing stage template %s" % (rel(path), key, st.get("template")))
    n = 0
    todo = dict(templates)
    for d, _, files in os.walk(os.path.join(DATA, "structure", "kota")):      # kota 34 is not a 38 folder
        for f in files:
            if f.endswith(".nbt"):
                q = os.path.join(d, f)
                todo[template_id_of(q)] = (q, nbt.read_file(q)[1])
    for tid, (p, root) in sorted(todo.items()):
        pts = root.get("kultivator_titik", [])
        boxes = {(q.get("data") or {}).get("nama") for q in pts if q.get("jenis") == "bangunan"}
        for q in pts:
            j, dt = q.get("jenis", ""), q.get("data") or {}
            if j in kota_hidup_kit.ALIAS_ALAT:
                err("%s: titik %s is read by nothing (stored as %s)" % (rel(p), j, kota_hidup_kit.ALIAS_ALAT[j]))
            if j == "dijual" and dt.get("rumah") not in boxes:
                err("%s: dijual house %r has no bangunan box of that name" % (rel(p), dt.get("rumah")))
            if j != "kavling":
                continue
            n += 1
            plan = plans.get(str(dt.get("gaya")) + "/" + str(dt.get("rencana")))
            if plan is None:
                err("%s: kavling %s/%s has no plan in rencana.json" % (rel(p), dt.get("gaya"), dt.get("rencana")))
                continue
            w, _h, d = plan["ukuran"]
            if (int(dt.get("dx", -1)) + 1, int(dt.get("dz", -1)) + 1) != (w, d) or dt.get("kelas") != plan.get("kelas"):
                err("%s: kavling %s/%s box %sx%s class %s differs from the plan %sx%s %s" % (
                    rel(p), dt.get("gaya"), dt.get("rencana"), int(dt.get("dx", -1)) + 1, int(dt.get("dz", -1)) + 1,
                    dt.get("kelas"), w, d, plan.get("kelas")))
    return n


def check_budgets(templates):
    per = {}
    per_tema = {}
    for tid, (path, _root) in templates.items():
        folder = tid.split(":", 1)[1].split("/")
        per[folder[0]] = per.get(folder[0], 0) + gz_size(path)
        if folder[0] == "kota_tema" and len(folder) > 2:
            per_tema[folder[1]] = per_tema.get(folder[1], 0) + gz_size(path)
    for f, n in sorted(per.items()):
        if n > FOLDERS[f]:
            err("structure/%s: %d gzip bytes > budget %d" % (f, n, FOLDERS[f]))
    for t, n in sorted(per_tema.items()):
        if n > TEMA_BUDGET:
            err("structure/kota_tema/%s: %d gzip bytes > budget %d" % (t, n, TEMA_BUDGET))
    return per


# ---------------------------------------------------------------------------------------------------------- main
def main(argv):
    soft = "--soft" in argv
    templates = load_templates()
    for tid, (path, root) in sorted(templates.items()):
        check_template(tid, path, root)
    pools = all_worldgen("template_pool")
    structures = all_worldgen("structure")
    sets = all_worldgen("structure_set")
    n_tl = 0
    for sid, (path, s) in sorted(structures.items()):
        if s.get("type") == "kultivator:tata_letak":
            n_tl += 1
            check_tata_letak(sid, path, s, pools, templates)
            if sid.startswith("kultivator:kota_tema/"):
                check_tema_sides(sid, path, s, pools, templates)
        elif is_38_structure(sid):
            spawn = (s.get("spawn_overrides") or {}).get("monster")
            inhabited = sid.split(":", 1)[1].split("/")[0] in ("kampung", "pos", "kota_besar", "kompleks_sekte", "kota_tema")                 and sid not in ("kultivator:pos/kemah_begal", "kultivator:kota_tema/reruntuhan_gurun")
            if inhabited and (not spawn or spawn.get("spawns")):
                err("%s: inhabited place without empty monster spawn_overrides" % rel(path))
    salts = {}
    for salt, p in all_salts():
        salts.setdefault(salt, []).append(p)
    for salt, ps in sorted(salts.items()):
        if len(ps) > 1:
            err("salt %d used by %d sets: %s" % (salt, len(ps), ", ".join(rel(p) for p in ps)))
    for sid, (path, st) in sorted(sets.items()):
        structs = [e.get("structure", "") for e in st.get("structures", [])]
        if not any(is_38_structure(x) for x in structs):
            continue
        pl = st.get("placement") or {}
        if pl.get("type") not in PLACEMENTS_38:
            err("%s: 38 set with placement %s (use %s)" % (rel(path), pl.get("type"), ", ".join(PLACEMENTS_38)))
        if pl.get("type") == "kultivator:cincin_tempat" and "min_chunks" not in pl:
            err("%s: cincin_tempat without min_chunks" % rel(path))
        salt = pl.get("salt")
        if salt is not None and not in_38_range(int(salt)):
            err("%s: salt %s outside the 38 ranges" % (rel(path), salt))
    check_tema(sets, structures)
    check_tags(sets, structures)
    check_titik41(structures, pools, templates)
    n_kav = check_hidup42(templates)
    per = check_budgets(templates)
    for w in sorted(set(warnings)):
        print("warning:", w)
    for e in errors:
        print("ERROR:", e)
    print("cek_tempat: %d template(s), %d tata_letak structure(s), %d set(s) scanned, %d kavling; gzip %s; %d error(s)"
          % (len(templates), n_tl, len(sets), n_kav, {k: v for k, v in sorted(per.items())}, len(errors)))
    if errors and not soft:
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1:])
