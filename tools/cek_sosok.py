#!/usr/bin/env python3
"""Checks of the Sosok composer data (DESIGN 40.3.11, module 40K). Read-only; exit 1 on any error.

    python -I tools/cek_sosok.py

1. cube UV tables (sosok_lib.UV_RAMBUT/UV_BUSANA): inside region C / D, no overlap, nothing in the reserved areas;
2. BusanaKatalog.java has exactly the same cubes, order, parents, pivots, boxes, UVs, chains and pairs (regex on k(...));
3. resep.json "kubus_rambut"/"kubus_busana" have the table order (the bit order of SosokRupa);
4. every part sheet has the right size (64 x 64 skin layers, 8 x 8 face marks, 128 x 8 eye/brow/mouth state rows, the
   integer UV footprint for cube sheets) and its _tetap partner the same size; every covered pixel has a slot (G) that
   is a multiple of 16 and at most 15 x 16;
5. the reserved atlas areas (64, 14)-(128, 24) and (58, 110)-(128, 128) are transparent in every fallback atlas and in
   200 random composed looks; region A/B/C/D pixels of those looks only come from their layers;
6. every look row the resolver can reach exists: all NpcRole ids (or their schedule group), murid.0-5, jalan.<id> of
   SosokJalan, the D / rogue / envoy / spectator keys; every sheet any of 200 random looks names exists;
7. the sex/age policy of SosokGen.java (40I, server side) equals the K/U columns of resep.json (warnings: 40I's file).
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sosok_lib as L  # noqa: E402
import sosok_rupa as R  # noqa: E402

ROOT = L.ROOT
JAVA_KATALOG = os.path.join(ROOT, "src", "client", "java", "dev", "kultivator", "client", "sosok", "busana", "BusanaKatalog.java")
NPCROLE = os.path.join(ROOT, "src", "main", "java", "dev", "kultivator", "dunia", "kota", "NpcRole.java")
SOSOKGEN = os.path.join(ROOT, "src", "main", "java", "dev", "kultivator", "sosok", "SosokGen.java")
SOSOKJALAN = os.path.join(ROOT, "src", "main", "java", "dev", "kultivator", "sosok", "SosokJalan.java")
CADANGAN = os.path.join(L.ASSETS, "textures", "sosok", "cadangan")

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def num(s):
    return float(s.strip().rstrip("F").rstrip("f"))


# ---------------------------------------------------------------------------------------------------- 1 UV tables
for p in L.cek_uv():
    err("UV " + p)

# ---------------------------------------------------------------------------------------------------- 2 Java table
src = open(JAVA_KATALOG, encoding="utf-8").read()
pat = re.compile(r'k\("(\w+)", "(\w+)", p\(([^)]*)\), b\(([^)]*)\), (\d+), (\d+), (null|"(\w+)"), (true|false), (NOL|r\(([^)]*)\)), (null|b\(([^)]*)\))\)')
java = [m for m in pat.finditer(src)]
for table, name, start in ((L.UV_RAMBUT, "RAMBUT", src.index("RAMBUT = List.of(")), (L.UV_BUSANA, "BUSANA", src.index("BUSANA = List.of("))):
    end = src.index(");", start)
    rows = [m for m in java if start < m.start() < end]
    if [m.group(1) for m in rows] != list(table):
        err("BusanaKatalog.%s order/ids differ from sosok_lib: %s" % (name, [m.group(1) for m in rows]))
        continue
    for m in rows:
        cid = m.group(1)
        parent, pivot, box, uv, chain, extra = table[cid]
        jp = tuple(num(v) for v in m.group(3).split(","))
        jb = tuple(num(v) for v in m.group(4).split(","))
        if m.group(2) != parent:
            err("%s parent %s vs %s" % (cid, m.group(2), parent))
        if any(abs(a - b) > 1e-4 for a, b in zip(jp, pivot)):
            err("%s pivot %s vs %s" % (cid, jp, pivot))
        if any(abs(a - b) > 1e-4 for a, b in zip(jb, box)):
            err("%s box %s vs %s" % (cid, jb, box))
        if (int(m.group(5)), int(m.group(6))) != tuple(uv):
            err("%s uv %s,%s vs %s" % (cid, m.group(5), m.group(6), uv))
        jchain = m.group(8) or None
        if jchain != chain and not (chain and chain.startswith("ROK") and jchain and jchain.startswith("ROK")):
            err("%s chain %s vs %s" % (cid, jchain, chain))
        if (m.group(9) == "true") != bool(extra.get("pasangan")):
            err("%s pair flag differs" % cid)
        rot = (0.0, 0.0, 0.0) if m.group(10) == "NOL" else tuple(num(v) for v in m.group(11).split(","))
        if any(abs(a - b) > 1e-4 for a, b in zip(rot, extra.get("rot", (0, 0, 0)))):
            err("%s rotation %s vs %s" % (cid, rot, extra.get("rot")))
        if "ramping" in extra:
            if m.group(12) == "null":
                err("%s slim box missing in Java" % cid)
            else:
                jr = tuple(num(v) for v in m.group(13).split(","))
                if any(abs(a - b) > 1e-4 for a, b in zip(jr, extra["ramping"])):
                    err("%s slim box %s vs %s" % (cid, jr, extra["ramping"]))

# ---------------------------------------------------------------------------------------------------- 3 recipe order
resep = R.resep()
if resep["kubus_rambut"] != list(L.UV_RAMBUT):
    err("resep.json kubus_rambut order differs from UV_RAMBUT")
if resep["kubus_busana"] != list(L.UV_BUSANA):
    err("resep.json kubus_busana order differs from UV_BUSANA")

# ---------------------------------------------------------------------------------------------------- 4 sheets
FACE8 = ("hidung_", "tanda_bekas_luka", "tanda_tahi_lalat", "tanda_coreng_jelaga", "tanda_huadian", "tanda_tanda_dao")
STATES = ("mata_", "alis_", "mulut_")


def expected_size(rel):
    group, _, name = rel.partition("/")
    if group == "rambut" and name.startswith("kubus/"):
        cid = max((c for c in L.UV_RAMBUT if name[6:] == c or name[6:].startswith(c + "_")), key=len, default=None)
        return L.cube_sheet_size(L.UV_RAMBUT[cid]) if cid else None
    if group == "busana" and name.startswith("kubus/"):
        cid = max((c for c in L.UV_BUSANA if name[6:] == c or name[6:].startswith(c + "_")), key=len, default=None)
        return L.cube_sheet_size(L.UV_BUSANA[cid]) if cid else None
    if group == "wajah" and name.startswith(STATES):
        return (128, 8)
    if group == "wajah" and name.startswith(FACE8):
        return (8, 8)
    return (64, 64)


sheet_count = 0
for dirpath, _, files in os.walk(L.BAGIAN):
    for f in files:
        if not f.endswith(".png"):
            continue
        full = os.path.join(dirpath, f)
        rel = os.path.relpath(full, L.BAGIAN).replace(os.sep, "/")[:-4]
        w, h, px = L.read_png(full)
        if rel.endswith("_tetap"):
            base = os.path.join(L.BAGIAN, rel[:-6] + ".png")
            if not os.path.exists(base):
                err("%s has no partner sheet" % rel)
            else:
                bw, bh, _ = L.read_png(base)
                if (bw, bh) != (w, h):
                    err("%s size %dx%d vs partner %dx%d" % (rel, w, h, bw, bh))
            continue
        sheet_count += 1
        exp = expected_size(rel)
        if exp is None:
            err("%s: no cube of that name" % rel)
        elif (w, h) != tuple(exp):
            err("%s size %dx%d, expected %dx%d" % (rel, w, h, exp[0], exp[1]))
        for i, (r, g, b, a) in enumerate(px):
            if a and (g % 16 != 0 or g > 240):
                err("%s pixel (%d, %d) slot G=%d not a multiple of 16" % (rel, i % w, i // w, g))
                break

# ---------------------------------------------------------------------------------------------------- 5/6 looks
RESERVED = L.RESERVED


def reserved_clean(atlas, what):
    for x0, y0, x1, y1 in RESERVED:
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (atlas[y * 128 + x] >> 24) & 255:
                    err("%s: pixel in reserved area at (%d, %d)" % (what, x, y))
                    return


for f in sorted(os.listdir(CADANGAN)):
    if f.endswith(".png"):
        w, h, px = L.read_png(os.path.join(CADANGAN, f))
        if (w, h) != (128, 128):
            err("fallback %s size %dx%d" % (f, w, h))
            continue
        reserved_clean([(a << 24) | (r << 16) | (g << 8) | b for r, g, b, a in px], "fallback " + f)
cad = len([f for f in os.listdir(CADANGAN) if f.endswith(".png")])
if cad != 48:
    err("fallback atlases: %d (expected 48 = 2 x 4 x 6)" % cad)

cache = L.SheetCache()
rng = L.SplitMix64(0x5EED40)
keys = [k[6:] for k in resep["peran"]]
for n in range(200):
    key = keys[rng.below(len(keys))]
    sosok = (rng.below(2), rng.below(4), rng.below(8), rng.below(1 << 26))
    m = R.masukan(key, realm=rng.below(10) - 1, dao=rng.below(9) - 1, pangkat=rng.below(7) - 1, fisik=rng.below(10),
                  topi_menutup=rng.below(4) == 0, jalan=rng.below(16), luka=rng.below(3), busana_khusus=rng.below(8))
    look = R.resolve(sosok, None, m)
    for layer in look["lapisan"]:
        if cache(layer[1]) is None:
            err("look %s names missing sheet %s" % (key, layer[1]))
    reserved_clean(L.susun(look, cache), "look %s seed %x" % (key, sosok[3]))

roles = re.findall(r"^\t([A-Z][A-Z0-9_]*)\(", open(NPCROLE, encoding="utf-8").read(), re.M)
groups = set(re.findall(r"Kelompok\.([A-Z_]+)", open(NPCROLE, encoding="utf-8").read()))
for role in roles:
    if "peran." + role.lower() not in resep["peran"]:
        warnings.append("NpcRole %s has no own row (uses its schedule group)" % role)
for g in groups:
    if "peran.kelompok." + g not in resep["peran"]:
        err("no row peran.kelompok.%s" % g)
need = ["murid.%d" % i for i in range(6)] + ["murid.calon", "murid.saingan", "kelompok.SIPIL", "utusan_langit",
                                              "penonton.utusan", "penonton.warga", "ketua_sekte"] + ["liar.%d" % i for i in range(8)]
for k in need:
    if "peran." + k not in resep["peran"]:
        err("no row peran.%s" % k)
jalan_ids = re.findall(r'"([a-z_]+)"', re.search(r"String\[\] ID = \{([^}]*)\}", open(SOSOKJALAN, encoding="utf-8").read()).group(1))
for j in jalan_ids:
    if "jalan." + j not in resep["jalan"]:
        err("no row jalan.%s" % j)

# ---------------------------------------------------------------------------------------------------- 7 SosokGen policy


def spec_pct(spec):
    """'D70T30' / 'MD' / {'D': 70} -> {letter: percent}, missing numbers share the rest equally."""
    if isinstance(spec, dict):
        return {k: v for k, v in spec.items() if v}
    parts = re.findall(r"([AMDT])(\d*)", spec)
    used = sum(int(n) for _, n in parts if n)
    free = [a for a, n in parts if not n]
    out = {a: int(n) for a, n in parts if n}
    for i, a in enumerate(free):
        share = (100 - used) // len(free) + (1 if i < (100 - used) % len(free) else 0)
        out[a] = share
    return out


def k_pct(k):
    if k.startswith("C"):
        return int(k[1:])
    if k == "L":
        return 0
    if k == "P":
        return 100
    m = re.search(r"P(\d+)", k)
    return int(m.group(1)) if m else 50


gen = open(SOSOKGEN, encoding="utf-8").read()
policy = {m.group(2): (int(m.group(3)), m.group(4)) for m in re.finditer(r'(npc|baris)\("([^"]+)", (\d+), "([AMDT0-9]+)"\)', gen)}
for key, (pct, spec) in sorted(policy.items()):
    row = resep["peran"].get("peran." + key)
    if row is None:
        warnings.append("SosokGen row %s has no resep row" % key)
        continue
    if k_pct(row["k"]) != pct:
        warnings.append("%s women %% SosokGen %d vs resep %s" % (key, pct, row["k"]))
    if spec_pct(spec) != spec_pct(row["u"]):
        warnings.append("%s ages SosokGen %s vs resep %s" % (key, spec, row["u"]))

print("cek_sosok: %d sheets, %d cubes (C %d, D %d), %d look rows, %d path rows, %d SosokGen rows"
      % (sheet_count, len(L.UV_RAMBUT) + len(L.UV_BUSANA), len(L.UV_RAMBUT), len(L.UV_BUSANA), len(resep["peran"]),
         len(resep["jalan"]), len(policy)))
for w in warnings:
    print("  warn:", w)
for e in errors:
    print("  ERROR:", e)
print("errors %d, warnings %d" % (len(errors), len(warnings)))
sys.exit(1 if errors else 0)
