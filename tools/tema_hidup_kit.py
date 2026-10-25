"""DESIGN 42 lots of the five themed towns (42.12.1 row tema, 42.9.1, 42.9.4, 42.9.6; module 42W-T, wave 42-G1).

Used only by tools/gen_tempat_tema_{pelabuhan,oasis,air,gunung,iblis}.py. Each town gets free lots OUTSIDE its wall,
inside the keep-out zone of the district that registers them (off the 48-grid, no side code):

    hidup42/jalan_malam     the night street (42.9.6): wine house, gambling house, tea house with a storyteller stage,
                            a talisman shop, two rentable stalls, the dead-end alley of the Ghost Market
    hidup42/kantor_rekrut   the sect recruitment office (42.8.1) with two booths (rekrut bilik 0/1), three rentable
                            stalls on the forecourt, a well, a xiangqi table, a hitching rail
    hidup42/kavling_barat   five growth lots each (42.9.4: 2 besar + 3 kecil, gaya of the theme), the carpenters'
    hidup42/kavling_timur   shed or the empty warehouse of the Ghost Market, the builders' material yard
    hidup42/makam           the themed cemetery (24 graves, gate, paper-money burner, caretaker hut, shrine, two Ghost
                            Market spots dekat_makam): tebing (sea cliff), kubah (domed desert tombs), pulau (island in
                            a moat), teras (mountain terraces)
    hidup42/lorong_hantu    (Kota Iblis instead of the night street, kavling and cemetery) pawnshop, talisman and
                            poison shops, five rentable stalls, an empty warehouse, a dry ditch under a bone bridge

A theme passes a style dict `g` (materials, roof form, kavling/makam gaya, plan lists; see GAYA_KUNCI). The lots are
tl.Lot pieces (48 x H x 48, street floor y = tl.LANTAI_Y over the foundation) written with the theme palette so the
generator's processors (lapuk, style pools) apply to them unchanged. Only vanilla blocks, no fire, lava or lightning
rod; every campfire unlit. Numbered points are unique per town: the caller passes the first number of each kind.
Deterministic: same bytes every run.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tempat_lib as tl  # noqa: E402
import kota_besar_kit as K  # noqa: E402
import kota_hidup_kit as khk  # noqa: E402  (registers the 42 titik types)

LY = tl.LANTAI_Y
N = tl.LOT
OPP = khk.OPP
FACE = khk.FACE
SNOWY = ("minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium")
TANGGA = ("_stairs",)

# keys of a style dict (all block ids are literal "minecraft:..." ids)
GAYA_KUNCI = (
    "tema",          # theme id (pelabuhan, oasis, air, gunung, iblis)
    "pal",           # the generator's palette (roles for kota_besar_kit helpers and the wood family of the kit)
    "pondasi",       # foundation block under the lot
    "tanah",         # ground surface: list of block ids (deterministic mix)
    "jalan",         # street paving: list of block ids
    "alas",          # building plinth / kerb
    "alas_tangga",   # plinth steps (stairs)
    "lantai",        # interior floor: list
    "dinding",       # wall fill: list (first = main)
    "tiang",         # corner / every-4 post (log -> axis y)
    "balok",         # top band (log -> axis along the wall)
    "jendela",       # window block (trapdoor -> open lattice, pane, bars)
    "pintu",         # door block
    "plafon",        # ceiling block
    "atap",          # roof form: pelana | datar | kubah | runcing
    "atap_tangga", "atap_slab", "atap_penuh",
    "parapet",       # flat-roof parapet (wall block)  [datar/kubah]
    "kubah",         # dome block                      [kubah]
    "matou",         # True: stepped white gable walls over the roof ends (Huizhou, Kota Air)
    "kain",          # (wool a, wool b) awnings
    "warna",         # banner / carpet colour
    "lampu",         # lantern block (minecraft:lantern / soul_lantern)
    "pohon",         # tree wood family for khk._pohon, or "palma" (Kota Oasis palm)
    "kavling",       # kavling gaya (khk.GAYA_KAVLING)
    "makam",         # (khk.GAYA_MAKAM key, varian tebing|kubah|pulau|teras)
    "rencana",       # (besar plans, kecil plans) for the two kavling lots
    "hantu",         # Ghost Market place of the kavling lot (khk.TEMPAT_HANTU)
)


# ================================================================================================ small helpers
def _pilih(lst, x, z, salt=0):
    if isinstance(lst, str):
        return lst
    return lst[(x * 7 + z * 13 + (x * z + salt) % 5 + salt) % len(lst)]


def _put(k, x, y, z, bid, props=None):
    if props is None and bid in SNOWY:
        props = {"snowy": "false"}
    k.put(x, y, z, bid, props)


def _ax(bid, ax="y"):
    return {"axis": ax} if any(bid.endswith(t) for t in ("_log", "_wood", "_stem", "_hyphae", "_pillar", "basalt",
                                                         "bone_block", "hay_block")) else None


def tangga(k, x, y, z, bid, facing, half="bottom", shape="straight"):
    k.put(x, y, z, bid, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})


def slab(k, x, y, z, bid, kind="bottom"):
    k.put(x, y, z, bid, {"type": kind, "waterlogged": "false"})


def lot_baru(folder, nama, g, tinggi=30):
    """A new 48 x tinggi x 48 lot in the theme palette with its foundation and ground."""
    pal = dict(K.PAL)
    pal.update(g["pal"])
    k = tl.Lot(folder, nama, tinggi=tinggi, palet=pal)
    k.lights = []
    tanah(k, g, 0, 0, N - 1, N - 1)
    return k


def tanah(k, g, x0, z0, x1, z1, y=LY):
    """Ground row y over one foundation row (like the theme lots: beard_box fills the terrain below the piece)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, y - 1, z, g["pondasi"])
            _put(k, x, y, z, _pilih(g["tanah"], x, z))


def jalan(k, g, x0, z0, x1, z1, y=LY, tepi=None):
    """Street paving; tepi = kerb block on the two long edges."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            _put(k, x, y, z, _pilih(g["jalan"], x, z, 3))
    if tepi:
        if abs(x1 - x0) >= abs(z1 - z0):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for z in (min(z0, z1), max(z0, z1)):
                    k.put(x, y, z, tepi)
        else:
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in (min(x0, x1), max(x0, x1)):
                    k.put(x, y, z, tepi)


def pohon(k, g, x, z, tinggi=5, y=LY):
    """A tree of the theme on ground row y (palms in the desert)."""
    if g["pohon"] == "palma":
        for dy in range(1, tinggi + 2):
            k.put(x + (1 if dy > tinggi - 1 else 0), y + dy, z, "minecraft:jungle_log", {"axis": "y"})
        cx, top = x + 1, y + tinggi + 2
        lp = {"distance": "1", "persistent": "true", "waterlogged": "false"}
        k.put(cx, top, z, "minecraft:jungle_leaves", lp)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for i in (1, 2):
                k.put(cx + dx * i, top - (1 if i == 2 else 0), z + dz * i, "minecraft:jungle_leaves", lp)
        for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            k.put(cx + dx, top - 1, z + dz, "minecraft:jungle_leaves", lp)
        k.put(cx, top - 1, z + 1, "minecraft:cocoa", {"age": "2", "facing": "north"})
        return
    o = khk.Benda(k, x, y, z, "south")
    khk._pohon(o, 0, 0, tinggi, g["pohon"])


def lampu_tiang(k, g, x, z, y=LY, tinggi=3):
    """A lantern on a post (the theme lamp)."""
    m = khk.bahan(khk._kayu_dari(k))
    k.put(x, y + 1, z, g["alas"])
    for dy in range(2, tinggi + 1):
        k.put(x, y + dy, z, m["fence"])
    k.put(x, y + tinggi + 1, z, g["lampu"], {"hanging": "false", "waterlogged": "false"})


def kubah(k, cx, y, cz, r, bid, puncak="minecraft:gold_block"):
    """A dome of radius r on row y (shell one block thick) with a finial."""
    for dy in range(r + 1):
        rr = math.sqrt(max(0.0, r * r - dy * dy)) + 0.35
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                d = math.hypot(x - cx, z - cz)
                if rr - 1.0 < d <= rr:
                    k.put(x, y + dy, z, bid)
    k.put(cx, y + r + 1, cz, puncak)


# ================================================================================================ the building shell
def bangun(k, g, x0, z0, x1, z1, h=4, pintu="south", at=None, lebar=1, f=None, atap=None, nama=None, jendela=True):
    """Walls x0..x1 x z0..z1 (outer) on floor row f (default LY + 1 over a one-step plinth): standing rows f+1..f+h,
    a top band and the ceiling at f+h+1, the roof of the theme above. The entrance is in the `pintu` wall centred at
    world x/z `at` (default the middle), `lebar` wide (1 = a door block). Returns {inner, f, h, top, pintu_xz, w, d}
    where inner = the interior box for kota_hidup_kit.interior_* (x0, z0, w, d; floor y0 = f, clear h)."""
    f = LY + 1 if f is None else f
    if at is None:
        at = (x0 + x1) // 2 if pintu in ("north", "south") else (z0 + z1) // 2
    dinding = g["dinding"] if isinstance(g["dinding"], (list, tuple)) else [g["dinding"]]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for yy in range(LY + 1, f):
                k.put(x, yy, z, g["alas"])
            if f > LY:
                k.put(x, f, z, g["alas"] if edge else _pilih(g["lantai"], x, z, 1))
            else:
                k.put(x, f, z, _pilih(g["lantai"], x, z, 1))
    half = lebar // 2

    def is_door(x, z, y):
        if y > f + (3 if lebar >= 3 else 2):
            return False
        if pintu == "north":
            return z == z0 and abs(x - at) <= half and x0 < x < x1
        if pintu == "south":
            return z == z1 and abs(x - at) <= half and x0 < x < x1
        if pintu == "west":
            return x == x0 and abs(z - at) <= half and z0 < z < z1
        return x == x1 and abs(z - at) <= half and z0 < z < z1

    for y in range(f + 1, f + h + 2):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if is_door(x, z, y):
                    k.put(x, y, z, "minecraft:air")
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                along = (x - x0) if z in (z0, z1) else (z - z0)
                face = ("north" if z == z0 else "south") if z in (z0, z1) else ("west" if x == x0 else "east")
                if corner or along % 4 == 0:
                    k.put(x, y, z, g["tiang"], _ax(g["tiang"]))
                elif y == f + h + 1:
                    k.put(x, y, z, g["balok"], _ax(g["balok"], "x" if z in (z0, z1) else "z"))
                elif jendela and h >= 3 and y in (f + 2, f + 3) and along % 4 == 2 and not _dekat_pintu(x, z, pintu, at,
                                                                                                         half, x0, x1,
                                                                                                         z0, z1):
                    _jendela(k, g, x, y, z, face)
                else:
                    k.put(x, y, z, dinding[0] if y == f + 1 or len(dinding) == 1 else _pilih(dinding, x + y, z, 2))
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + h, z1 - 1)
    k.fill(x0 + 1, f + h + 1, z0 + 1, x1 - 1, f + h + 1, z1 - 1, g["plafon"])
    # the door
    if pintu == "north":
        dxz, out = (at, z0), (at, z0 - 1)
    elif pintu == "south":
        dxz, out = (at, z1), (at, z1 + 1)
    elif pintu == "west":
        dxz, out = (x0, at), (x0 - 1, at)
    else:
        dxz, out = (x1, at), (x1 + 1, at)
    if lebar == 1:
        K.door(k, dxz[0], f + 1, dxz[1], pintu, "left", g["pintu"])
    if f > LY:
        for w in range(-half, lebar - half):
            ox, oz = (out[0] + w, out[1]) if pintu in ("north", "south") else (out[0], out[1] + w)
            for yy in range(LY + 1, f):
                k.put(ox, yy, oz, g["alas"])
            tangga(k, ox, f, oz, g["alas_tangga"], OPP[pintu])
    top = atap_bangunan(k, g, x0, z0, x1, z1, f + h + 2, atap or g["atap"])
    if nama:
        k.kotak("bangunan", x0, LY, z0, x1, min(top + 1, k.size[1] - 1), z1, pintu, nama=nama)
    return {"inner": (x0 + 1, z0 + 1, x1 - 1, z1 - 1), "f": f, "h": h, "top": top, "pintu_xz": dxz,
            "w": x1 - x0 - 1, "d": z1 - z0 - 1, "at": at}


def _dekat_pintu(x, z, pintu, at, half, x0, x1, z0, z1):
    if pintu in ("north", "south") and z == (z0 if pintu == "north" else z1):
        return abs(x - at) <= half + 1
    if pintu in ("west", "east") and x == (x0 if pintu == "west" else x1):
        return abs(z - at) <= half + 1
    return False


def _jendela(k, g, x, y, z, face):
    bid = g["jendela"]
    if bid.endswith("_trapdoor"):
        k.put(x, y, z, bid, {"facing": face, "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    else:
        k.put(x, y, z, bid)


def atap_bangunan(k, g, x0, z0, x1, z1, y, bentuk):
    """The roof over walls x0..x1 x z0..z1 starting on row y; returns the top row."""
    t, s, p = g["atap_tangga"], g["atap_slab"], g["atap_penuh"]
    if bentuk == "pelana":
        along = "x" if (x1 - x0) >= (z1 - z0) else "z"
        top = k.roof_gable(x0, y, z0, x1, z1, role=t, slab_role=s, full_role=p, along=along, overhang=1)
        dinding = g["dinding"] if isinstance(g["dinding"], str) else g["dinding"][0]
        # gable end walls (the attic triangle) and, for Huizhou style, stepped horse-head walls over the roof ends
        layers = ((z1 - z0 + 3) if along == "x" else (x1 - x0 + 3)) // 2
        for i in range(layers):
            if along == "x":
                for z in range(z0 + i, z1 - i + 1):
                    for x in (x0, x1):
                        k.put(x, y + i, z, dinding)
            else:
                for x in range(x0 + i, x1 - i + 1):
                    for z in (z0, z1):
                        k.put(x, y + i, z, dinding)
        if g.get("matou"):
            _matou(k, g, x0, z0, x1, z1, y, along, layers)
        return top + 1
    if bentuk in ("datar", "kubah"):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                k.put(x, y, z, p)
                if edge:
                    k.put(x, y + 1, z, g["parapet"])
                    if (x + z) % 2 == 0 and (x in (x0, x1) and z in (z0, z1) or (x - x0) % 4 == 0 or (z - z0) % 4 == 0):
                        k.put(x, y + 2, z, g["parapet"])
        if bentuk == "kubah":
            r = max(1, min(4, (min(x1 - x0, z1 - z0) - 4) // 2))
            kubah(k, (x0 + x1) // 2, y + 1, (z0 + z1) // 2, r, g["kubah"], g.get("puncak", "minecraft:gold_block"))
            return y + r + 3
        return y + 3
    if bentuk == "runcing":
        top = k.roof_hip(x0, y, z0, x1, z1, role=t, slab_role=s, full_role=p, overhang=1)
        for cx, cz in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
            k.put(cx, y + 2, cz, "minecraft:iron_bars")
            k.put(cx, y + 3, cz, "minecraft:end_rod", {"facing": "up"})
        mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
        k.put(mx, top, mz, "minecraft:polished_blackstone_wall")
        k.put(mx, top + 1, mz, "minecraft:end_rod", {"facing": "up"})
        return top + 2
    raise ValueError("atap %r" % bentuk)


def _matou(k, g, x0, z0, x1, z1, y, along, layers):
    """Stepped white gable walls rising over both roof ends with a dark tile cap (matouqiang)."""
    putih = g["dinding"] if isinstance(g["dinding"], str) else g["dinding"][0]
    cap = g["atap_slab"]
    lo, hi = (z0 - 1, z1 + 1) if along == "x" else (x0 - 1, x1 + 1)
    span = hi - lo + 1
    steps = 3
    for end in ((x0, x1) if along == "x" else (z0, z1)):
        for s in range(steps):
            a = lo + s * span // (2 * steps)
            b = hi - s * span // (2 * steps)
            yy = y + 1 + s * max(1, layers // steps) + (1 if s else 0)
            for v in range(a, b + 1):
                for dy in range(0, 2):
                    x, z = (end, v) if along == "x" else (v, end)
                    if dy == 1 and s < steps - 1 and (lo + (s + 1) * span // (2 * steps)) <= v <= (
                            hi - (s + 1) * span // (2 * steps)):
                        k.put(x, yy + dy, z, putih)
                    else:
                        k.put(x, yy + dy, z, putih if dy == 0 else cap,
                              None if dy == 0 else {"type": "bottom", "waterlogged": "false"})
            for yy2 in range(y, yy):
                for v in range(a, b + 1):
                    x, z = (end, v) if along == "x" else (v, end)
                    if k.get(x, yy2, z) in (None, "minecraft:air"):
                        k.put(x, yy2, z, putih)


def lapak(k, g, n, x, z, hadap, y=LY, barang=None):
    """A rentable stall (42.6.2): the slab pad and lapak_sewa_<n> point of the kit, behind it a counter with goods,
    two posts and a striped awning of the theme cloth (hadap = toward the street)."""
    khk.lapak_sewa(k, n, x, y, z, hadap)
    o = khk.Benda(k, x, y, z, hadap)
    a, b = g["kain"]
    for lx in (-1, 0, 1):
        o.p(lx, 1, 2, "minecraft:barrel" if lx != 0 else "trapdoor",
            {"facing": "up", "open": "false"} if lx != 0 else
            {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
        if barang:
            o.p(lx, 2, 2, barang[(lx + 1) % len(barang)])
    for lx in (-2, 2):
        for dy in (1, 2, 3):
            o.p(lx, dy, 2, "fence")
            o.p(lx, dy, -1, "fence") if dy > 1 else None
    for lx in range(-2, 3):
        for lz in range(-1, 3):
            o.p(lx, 4, lz, a if (lx + lz) % 2 == 0 else b)


def tiang_lentera_baris(k, pts, y=LY, warna="merah", tinggi=4):
    for (x, z, f) in pts:
        khk.tiang_lentera(k, x, y, z, f, tinggi, warna)


def hemat_udara(k, jaga=2):
    """Drops explicit air over open sky higher than `jaga` rows above the street floor (beard_box carves the terrain
    there; 38.C.10 gzip budget): air is kept in the walking rows, below the floor and in every roofed column."""
    atap = {}
    for (x, y, z), cell in k.cells.items():
        if cell[0][0] not in ("minecraft:air", "minecraft:cave_air") and y > LY:
            atap[(x, z)] = max(atap.get((x, z), -1), y)
    buang = [pos for pos, cell in k.cells.items()
             if cell[0][0] == "minecraft:air" and pos[1] > LY + jaga and atap.get((pos[0], pos[2]), -1) < pos[1]]
    for pos in buang:
        del k.cells[pos]
    return len(buang)


def selesai(k, maks=96):
    """Fence connections, open-sky air pruning, the 96-point rule, save."""
    khk.sambung(k)
    hemat_udara(k)
    n = len(k.titik_list)
    if n > maks:
        raise ValueError("%s: %d titik > %d" % (k.id, n, maks))
    k.simpan()
    return k


# ================================================================================================ night street (42.9.6)
def lot_jalan_malam(folder, g, nama="hidup42/jalan_malam", lapak0=0, hantu0=0):
    """The night street: a 9-wide lane north-south through the lot (lantern posts, cross strings, stools, the dice
    table, wine flags at both ends), the outer road along the north edge (toward the town wall). West: Kedai Arak
    (wine house) and Rumah Judi (gambling house); east: Kedai Teh with the storyteller stage, two rentable stalls,
    the talisman shop and the dead-end alley where the Ghost Market sets up. Returns the saved lot."""
    k = lot_baru(folder, nama, g, tinggi=30)
    jalan(k, g, 0, 0, N - 1, 4)
    jalan(k, g, 19, 5, 27, N - 1)
    # Kedai Arak (wine house), west north, door east onto the lane
    a = bangun(k, g, 2, 7, 16, 24, h=4, pintu="east", nama="kedai_arak_malam")
    ix0, iz0, ix1, iz1 = a["inner"]
    khk.interior_kedai_arak(k, ix0, a["f"], iz0, a["w"], a["d"], a["h"], "east", pintu=a["at"], titik_maks=12)
    # Rumah Judi, west south
    b = bangun(k, g, 2, 27, 16, 45, h=4, pintu="east", nama="rumah_judi_malam")
    ix0, iz0, ix1, iz1 = b["inner"]
    khk.interior_rumah_judi(k, ix0, b["f"], iz0, b["w"], b["d"], b["h"], "east", pintu=b["at"], titik_maks=12)
    # Kedai Teh, east north, door west
    c = bangun(k, g, 30, 7, 45, 24, h=4, pintu="west", nama="kedai_teh_malam")
    ix0, iz0, ix1, iz1 = c["inner"]
    khk.interior_kedai_teh(k, ix0, c["f"], iz0, c["w"], c["d"], c["h"], "west", pintu=c["at"], titik_maks=12)
    # two rentable stalls facing the lane
    lapak(k, g, lapak0, 30, 29, "west", barang=("minecraft:decorated_pot", "minecraft:candle", "minecraft:flower_pot"))
    lapak(k, g, lapak0 + 1, 30, 35, "west", barang=("minecraft:melon", "minecraft:dried_kelp_block"))
    # talisman shop, east south (door west)
    d = bangun(k, g, 35, 38, 45, 46, h=3, pintu="west", nama="toko_jimat_malam")
    ix0, iz0, ix1, iz1 = d["inner"]
    khk.interior_toko_jimat(k, ix0, d["f"], iz0, d["w"], d["d"], d["h"], "west", pintu=d["at"], titik_maks=8)
    # the dead-end alley between the stalls and the talisman shop: crates, a broken cart, the Ghost Market spot
    jalan(k, g, 34, 31, 46, 36)
    dind = g["dinding"][0] if isinstance(g["dinding"], (list, tuple)) else g["dinding"]
    for x in range(34, 47):
        k.put(x, LY + 1, 30, dind)
        k.put(x, LY + 2, 30, dind)
        slab(k, x, LY + 3, 30, g["atap_slab"])
    for x in range(34, 47):
        k.put(x, LY + 1, 37, g["alas"])
    K.crates(k, 44, 31, 46, 32, LY + 1, h=2)
    k.put(45, LY + 1, 35, "minecraft:composter", {"level": "3"})
    tangga(k, 41, LY + 1, 33, "minecraft:spruce_stairs", "west", half="top")
    k.put(42, LY + 1, 33, "minecraft:spruce_trapdoor", {"facing": "east", "half": "bottom", "open": "true",
                                                       "powered": "false", "waterlogged": "false"})
    khk.pasar_hantu(k, hantu0, 44, LY + 1, 34, "west", "gang_buntu")
    khk.jongkok(k, 38, LY, 35, "north", 2)
    khk.tiang_lentera(k, 46, LY, 33, "west", 3, "hijau")
    # the lane dressing (lantern posts both edges, cross strings, stools, dice table, wine flags)
    khk.jalan_malam(k, 18, 6, 28, N - 2, LY, sumbu="z", tiang_setiap=5, lentera_maks=12, kursi_maks=2, dadu=True,
                    warna="merah")
    khk.spanduk(k, 18, 2, 28, 2, LY, tinggi=6, pasangan="malam", glif="jiu")
    khk.nama_jalan(k, 17, LY, 5, "north")
    khk.lentera_atap(k, 1, 6, 46, 46, LY, maks=4, jarak=8, warna="merah", salt=11)
    khk.kucing_atap(k, 1, 6, 46, 46, LY, maks=1, salt=5)
    khk.cerobong_dapur(k, 1, 6, 46, 46, LY, maks=2)
    return selesai(k)


# ================================================================================================ recruitment office
BILIK_WARNA = ("red", "blue")


def lot_kantor_rekrut(folder, g, nama="hidup42/kantor_rekrut", lapak0=2):
    """Kantor Rekrutmen Sekte (42.8.1, 42.12.1 'kantor_rekrut 2 bilik'): a 30 x 18 hall on a plinth, door north to the
    outer road. Inside the clerk's counter and archive shelves by the door, the notice board of open places, waiting
    benches on both sides of the runner (kursi antre=rekrut), a tea corner, and at the back two booths between plank
    partitions: desk, the recruiter's chair (`rekrut` bilik 0/1), the candidate's chair (kursi bilik), records chest,
    sect banner, lantern. Forecourt: three rentable stalls along the road, the well, two sect banner poles by the
    steps, the street-name post; behind the hall the xiangqi tree, a hitching rail with trough, squatters' corner."""
    k = lot_baru(folder, nama, g, tinggi=28)
    jalan(k, g, 0, 0, N - 1, 4)
    jalan(k, g, 6, 5, 41, 15)
    jalan(k, g, 22, 5, 25, 16)
    a = bangun(k, g, 9, 17, 38, 34, h=5, pintu="north", at=23, lebar=3, nama="kantor_rekrut")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cx = 23
    for z in range(iz0, iz0 + 10):
        k.put(cx, f + 1, z, "minecraft:red_carpet")
        k.put(cx + 1, f + 1, z, "minecraft:red_carpet")
    # the clerk's counter and the archive
    K.counter(k, ix0 + 2, ix0 + 6, iz0 + 2, f, "north")
    k.titik("kerja:konter", ix0 + 4, f + 1, iz0 + 3, "north", {"peran": "JURU_TULIS"})
    khk.titik42(k, "pajangan:gulungan", ix0 + 3, f + 2, iz0 + 2, "north")
    for z in range(iz0, iz0 + 5):
        for dy in (1, 2, 3):
            if (z + dy) % 3 == 0:
                k.put(ix0, f + dy, z, "minecraft:chiseled_bookshelf",
                      {"facing": "east", "slot_0_occupied": "false", "slot_1_occupied": "false",
                       "slot_2_occupied": "false", "slot_3_occupied": "false", "slot_4_occupied": "false",
                       "slot_5_occupied": "false"})
            else:
                k.put(ix0, f + dy, z, "minecraft:bookshelf")
    K.notice_board(k, ix1 - 3, iz0, "south", y=f, w=5)
    # waiting benches, facing the booths
    for z in (iz0 + 6, iz0 + 8):
        for start in (cx - 2, cx + 6):
            K.bench(k, start, z, "south", y=f, n=4)
            for dx in (0, -2):
                k.titik("kursi", start + dx, f + 1, z, "south", {"antre": "rekrut"})
    # tea corner east
    K.low_table(k, ix1 - 2, f + 1, iz0 + 3)
    for (x, z, fc) in ((ix1 - 3, iz0 + 3, "east"), (ix1 - 1, iz0 + 3, "west")):
        k.put(x, f + 1, z, "minecraft:%s_carpet" % g["warna"])
        k.titik("kursi_lantai", x, f + 1, z, fc)
    khk.titik42(k, "pajangan:teko", ix1 - 2, f + 2, iz0 + 3, "north")
    K.pot(k, ix1, f + 1, iz0, "minecraft:potted_bamboo")
    # the two booths at the back
    zb = iz1 - 4
    for px in (ix0 + 4, cx, cx + 1, ix1 - 4):
        for z in range(zb, iz1 + 1):
            for dy in (1, 2, 3):
                k.put(px, f + dy, z, "minecraft:dark_oak_planks" if z > zb else "minecraft:stripped_dark_oak_log",
                      None if z > zb else {"axis": "y"})
    for i, (u0, u1) in enumerate(((ix0 + 5, cx - 1), (cx + 2, ix1 - 5))):
        bc = (u0 + u1) // 2
        K.counter(k, bc - 1, bc + 1, iz1 - 2, f, "north")
        K.chair(k, bc, f + 1, iz1 - 1, "north")
        khk.rekrut(k, bc, f + 1, iz1 - 1, "north", bilik=i)
        K.chair(k, bc, f + 1, zb + 1, "south")
        k.titik("kursi", bc, f + 1, zb + 1, "south", {"bilik": i})
        K.chest(k, u0, f + 1, iz1, None, "north")
        K.barrel(k, u1, f + 1, iz1, "north")
        k.put(u1, f + 2, iz1, "minecraft:candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
        K.wall_banner(k, bc, f + 3, iz1, "north", g.get("bilik_warna", BILIK_WARNA)[i])
        K.lit(k, bc, f + a["h"] + 1, iz1 - 2, chain=1, role=g["lampu"])
        khk.titik42(k, "pajangan:gulungan", bc, f + 2, iz1 - 2, "north")
    for x, z in ((cx - 6, iz0 + 3), (cx + 7, iz0 + 7), (cx - 6, iz0 + 8)):
        K.lit(k, x, f + a["h"] + 1, z, chain=1, role=g["lampu"])
    # forecourt: stalls on the road, banner poles by the steps, the well, the street name
    lapak(k, g, lapak0, 4, 7, "north", barang=("minecraft:melon", "minecraft:pumpkin"))
    lapak(k, g, lapak0 + 1, 10, 7, "north", barang=("minecraft:decorated_pot", "minecraft:flower_pot"))
    lapak(k, g, lapak0 + 2, 43, 7, "north", barang=("minecraft:hay_block", "minecraft:barrel"))
    for x in (19, 28):
        khk.panji(k, x, LY, 15, "north", "panji", None, tiang=6)
    khk.sumur(k, 35, LY, 11, "west")
    khk.nama_jalan(k, 2, LY, 5, "north")
    tiang_lentera_baris(k, ((16, 5, "north"), (31, 5, "north")))
    k.titik("pintu", cx, f + 1, a["pintu_xz"][1] - 1, "north")
    # behind the hall: xiangqi under a tree, the hitching rail, squatters
    khk.catur(k, 12, LY, 40, "south")
    khk.tambat(k, 38, LY, 41, "north")
    khk.jongkok(k, 25, LY, 42, "north", 2)
    pohon(k, g, 4, 44, 5)
    pohon(k, g, 30, 45, 4)
    khk.lentera_atap(k, 9, 17, 38, 34, LY, maks=3, jarak=8, warna="merah", salt=7)
    return selesai(k)


# ================================================================================================ kavling lots (42.9.4)
def _material_yard(k, g, x0, z0, x1, z1):
    """The builders' yard of a growth district: log stacks, brick/stone pallets, sand heaps, a sawhorse."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            _put(k, x, LY, z, "minecraft:coarse_dirt" if (x * 3 + z) % 4 else "minecraft:gravel")
    for i, z in enumerate(range(z0 + 1, z1, 3)):
        for x in range(x0 + 1, x0 + 5):
            k.put(x, LY + 1, z, "minecraft:stripped_spruce_log" if (x + i) % 2 else "minecraft:spruce_log", {"axis": "x"})
            if x < x0 + 4:
                k.put(x, LY + 2, z, "minecraft:spruce_log", {"axis": "x"})
    for x in range(x1 - 3, x1):
        for z in range(z0 + 1, z0 + 3):
            k.put(x, LY + 1, z, "minecraft:oak_slab", {"type": "bottom", "waterlogged": "false"})
            k.put(x, LY + 2, z, "minecraft:bricks" if (x + z) % 2 else "minecraft:stone_bricks")
    for (sx, sz, bid) in ((x1 - 2, z1 - 2, "minecraft:sand"), (x1 - 5, z1 - 1, "minecraft:gravel")):
        k.put(sx, LY + 1, sz, bid)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if x0 <= sx + dx <= x1 and z0 <= sz + dz <= z1:
                slab(k, sx + dx, LY + 1, sz + dz,
                     "minecraft:sandstone_slab" if bid.endswith("sand") else "minecraft:cobblestone_slab")
    k.put(x0 + 6, LY + 1, z1 - 1, "minecraft:spruce_fence")
    k.put(x0 + 8, LY + 1, z1 - 1, "minecraft:spruce_fence")
    for x in range(x0 + 6, x0 + 9):
        k.put(x, LY + 2, z1 - 1, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    k.put(x0 + 7, LY + 1, z1 - 3, "minecraft:scaffolding", {"bottom": "false", "distance": "0", "waterlogged": "false"})
    k.put(x0 + 7, LY + 2, z1 - 3, "minecraft:scaffolding", {"bottom": "false", "distance": "0", "waterlogged": "false"})


def lot_kavling(folder, g, nama, varian=0, hantu=None):
    """Five growth lots (khk.kavling: 2 besar 13 x 13 + 3 kecil 7 x 7 footprints in fence rings, gaya g['kavling'],
    plans from g['rencana']) along an inner lane off the outer road. varian 0: the carpenters' shed (tukang_kayu
    kit) and the builders' yard; varian 1: three small lots on the road, the two big ones behind, the empty warehouse
    where the Ghost Market sets up (hantu = point number) and the builders' yard."""
    k = lot_baru(folder, nama, g, tinggi=26)
    gaya = g["kavling"]
    besar, kecil = g["rencana"]
    jalan(k, g, 0, 0, N - 1, 4)
    s = 0 if varian == 0 else 2
    if varian == 0:
        jalan(k, g, 20, 5, 26, N - 1)
        khk.kavling(k, 4, LY, 8, besar[s % len(besar)], gaya, "east")              # ring x 3..17, z 7..21
        khk.kavling(k, 7, LY, 27, kecil[s % len(kecil)], gaya, "east")             # ring x 6..14, z 26..34
        khk.kavling(k, 7, LY, 38, kecil[(s + 1) % len(kecil)], gaya, "east")       # ring x 6..14, z 37..45
        khk.kavling(k, 30, LY, 8, besar[(s + 1) % len(besar)], gaya, "west")       # ring x 29..43, z 7..21
        khk.kavling(k, 34, LY, 26, kecil[(s + 2) % len(kecil)], gaya, "west")      # ring x 33..41, z 25..33
        a = bangun(k, g, 30, 36, 44, 46, h=4, pintu="west", nama="bengkel_kayu_kavling")
        ix0, iz0, ix1, iz1 = a["inner"]
        khk.interior_tukang_kayu(k, ix0, a["f"], iz0, a["w"], a["d"], a["h"], "west", pintu=a["at"], titik_maks=6)
        tiang_lentera_baris(k, ((19, 12, "east"), (27, 12, "west"), (19, 30, "east"), (27, 30, "west"),
                                (19, 42, "east")))
        khk.nama_jalan(k, 19, LY, 5, "north")
        khk.kebiasaan(k, 16, 22, 30, 36, LY, "jongkok", 1)
        pohon(k, g, 2, 24, 4)
        pohon(k, g, 44, 25, 5)
    else:
        jalan(k, g, 0, 16, N - 1, 21)
        for i, x in enumerate((3, 14, 25)):                                         # rings x 2..10, 13..21, 24..32
            khk.kavling(k, x, LY, 7, kecil[(s + i) % len(kecil)], gaya, "north")   # z 6..14
        khk.kavling(k, 4, LY, 24, besar[s % len(besar)], gaya, "north")            # ring x 3..17, z 23..37
        khk.kavling(k, 21, LY, 24, besar[(s + 1) % len(besar)], gaya, "north")     # ring x 20..34, z 23..37
        # the empty warehouse: a shut-up shed, cobwebs, broken crates - the Ghost Market's back room
        a = bangun(k, g, 36, 23, 46, 33, h=3, pintu="north", nama="gudang_kosong")
        ix0, iz0, ix1, iz1 = a["inner"]
        f = a["f"]
        for (x, z) in ((ix0, iz0 + 1), (ix1, iz1), (ix0 + 3, iz1)):
            k.put(x, f + 1, z, "minecraft:barrel", {"facing": "up", "open": "true"})
        for (x, z) in ((ix0, iz1), (ix1, iz0 + 2), (ix1 - 1, iz1)):
            k.put(x, f + a["h"], z, "minecraft:cobweb")
        k.put(ix1 - 2, f + 1, iz0 + 3, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
        k.put(ix0 + 2, f + 1, iz1 - 1, "minecraft:decorated_pot", {"facing": "north", "cracked": "true",
                                                                   "waterlogged": "false"})
        if hantu is not None:
            khk.pasar_hantu(k, hantu, (ix0 + ix1) // 2, f + 1, iz0 + 1, "south", "gudang_kosong")
        _material_yard(k, g, 36, 36, 46, 46)
        _material_yard(k, g, 3, 40, 15, 46)
        tiang_lentera_baris(k, ((12, 15, "south"), (23, 15, "south"), (34, 15, "south"), (12, 22, "north"),
                                (35, 22, "north")))
        khk.nama_jalan(k, 1, LY, 15, "south")
        pohon(k, g, 20, 42, 5)
        pohon(k, g, 30, 43, 4)
        khk.kebiasaan(k, 16, 38, 34, 46, LY, "catur", 1)
        khk.sumur(k, 40, LY, 9, "west")
    khk.cerobong_dapur(k, 1, 5, 46, 46, LY, maks=1)
    return selesai(k)


# ================================================================================================ themed cemetery
def lot_makam(folder, g, nama="hidup42/makam", nisan0=0, hantu0=1, n=32):
    """The town cemetery outside the wall (42.4.6, 42.12.1): khk.makam of the theme gaya with `n` graves entered from
    the EAST (the lot is registered west of the town wall, so east faces the town), the gate and the paper-money
    burner at the entrance, the caretaker's hut (altar_leluhur kit), the small shrine (kuil_kecil kit), white lantern
    posts and two Ghost Market spots `dekat_makam`. Variants (g['makam'][1]): tebing = on a sea cliff of rock with
    stairs and a parapet toward the sea; kubah = three domed desert mausolea beside the graves; pulau = an island in a
    moat with a stone bridge and two fishing spots; teras = terraced rows with retaining walls, pines, stone lanterns."""
    gaya, varian = g["makam"]
    k = lot_baru(folder, nama, g, tinggi=34 if varian == "tebing" else 30)
    jalan(k, g, 44, 0, N - 1, N - 1)
    gy = LY
    mx0, mz0, mw, md = 6, 6, 28, 34
    if varian == "pulau":
        mx0, mz0, mw, md = 7, 7, 24, 30
    gz = mz0 + md // 2
    jalan(k, g, 38 if varian == "tebing" else mx0 + mw + (4 if varian == "pulau" else 0), gz - 2, 43, gz + 2)
    if varian == "tebing":
        gy = LY + 3
        rock = ("minecraft:andesite", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone")
        for x in range(mx0 - 2, mx0 + mw + 1):
            for z in range(mz0 - 2, mz0 + md + 2):
                edge = x in (mx0 - 2, mx0 + mw) or z in (mz0 - 2, mz0 + md + 1)
                for yy in range(LY + 1, gy + 1):
                    k.put(x, yy, z, rock[(x * 5 + yy * 3 + z * 7) % 4] if edge else "minecraft:dirt")
                if x == mx0 - 2:
                    k.put(x, gy + 1, z, "minecraft:cobblestone_wall")
        for w in range(-2, 3):
            for yy in range(LY + 1, gy + 1):
                sx = mx0 + mw + 1 + (gy - yy)
                tangga(k, sx, yy, gz + w, "minecraft:stone_brick_stairs", "west")
                for y2 in range(LY + 1, yy):
                    k.put(sx, y2, gz + w, "minecraft:stone_bricks")
        for zz in (gz - 3, gz + 3):
            for sx in range(mx0 + mw, mx0 + mw + 4):
                for y2 in range(LY + 1, gy + 2 - max(0, sx - mx0 - mw - 1)):
                    k.put(sx, y2, zz, "minecraft:stone_bricks" if y2 <= gy + 1 - max(0, sx - mx0 - mw - 1) - 1
                          else "minecraft:stone_brick_wall")
    elif varian == "pulau":
        for x in range(mx0 - 4, mx0 + mw + 4):
            for z in range(mz0 - 4, mz0 + md + 4):
                inner = mx0 - 1 <= x <= mx0 + mw and mz0 - 1 <= z <= mz0 + md
                if not inner:
                    k.put(x, LY - 3, z, "minecraft:clay")
                    for yy in (LY - 2, LY - 1, LY):
                        k.put(x, yy, z, "minecraft:water", {"level": "0"})
                elif x in (mx0 - 1, mx0 + mw) or z in (mz0 - 1, mz0 + md):
                    k.put(x, LY, z, "minecraft:mud_bricks")
        for x in range(mx0 + mw, mx0 + mw + 5):
            for w in (-1, 0, 1):
                k.put(x, LY, gz + w, "minecraft:stone_bricks")
            for zz in (gz - 2, gz + 2):
                k.put(x, LY, zz, "minecraft:stone_brick_slab", {"type": "top", "waterlogged": "true"})
                k.put(x, LY + 1, zz, "minecraft:stone_brick_wall")
        for yy in (LY - 1, LY - 2):
            k.put(mx0 + mw + 2, yy, gz, "minecraft:stone_bricks")
        khk.pancing(k, mx0 + 6, LY, mz0 - 5, "south")
        khk.pancing(k, mx0 + mw - 4, LY, mz0 + md + 4, "north")
    khk.makam(k, mx0, gy, mz0, mw, md, "east", n, mulai=nisan0, gaya=gaya, bertingkat=(varian == "teras"),
              gapura=True, pembakar=True)
    if varian == "kubah":
        for (qx, qz) in ((9, 43), (17, 43), (25, 43)):
            for x in range(qx - 2, qx + 3):
                for z in range(qz - 2, qz + 3):
                    edge = x in (qx - 2, qx + 2) or z in (qz - 2, qz + 2)
                    k.put(x, LY, z, "minecraft:smooth_sandstone")
                    for yy in range(LY + 1, LY + 4):
                        if edge and not (z == qz - 2 and x == qx and yy < LY + 3):
                            k.put(x, yy, z, "minecraft:cut_sandstone" if yy < LY + 3 else "minecraft:chiseled_sandstone")
                        elif not edge:
                            k.put(x, yy, z, "minecraft:air")
                    k.put(x, LY + 4, z, "minecraft:smooth_sandstone")
            kubah(k, qx, LY + 5, qz, 2, "minecraft:cut_sandstone", "minecraft:prismarine_bricks")
            k.put(qx, LY + 1, qz + 1, "minecraft:chiseled_sandstone")
            k.put(qx, LY + 2, qz + 1, "minecraft:candle", {"candles": "3", "lit": "false", "waterlogged": "false"})
            k.put(qx - 1, LY + 1, qz + 1, "minecraft:decorated_pot", {"facing": "north", "cracked": "false",
                                                                      "waterlogged": "false"})
            k.titik("dupa", qx, LY + 1, qz, "south")
        for x in (8, 30):
            pohon(k, g, x, 2, 5)
    elif varian == "teras":
        for (px, pz) in ((2, 10), (2, 30), (37, 13), (38, 33)):
            pohon(k, g, px, pz, 7)
        K.stone_lantern(k, mx0 + mw + 1, LY + 1, gz - 3)
        K.stone_lantern(k, mx0 + mw + 1, LY + 1, gz + 3)
    elif varian == "tebing":
        for (px, pz) in ((39, 13), (40, 33)):
            pohon(k, g, px, pz, 5)
    elif varian == "pulau":
        for (px, pz) in ((mx0 + 1, mz0 + md - 2), (mx0 + mw - 2, mz0 + 1)):
            k.put(px, LY, pz, "minecraft:moss_block")
            pohon(k, g, px, pz, 5)
    # the caretaker's hut (ancestor altar room) north-east, the shrine south-east
    hut = bangun(k, g, 36, 1, 43, 8, h=3, pintu="south", nama="pondok_juru_kunci")
    khk.interior_altar_leluhur(k, hut["inner"][0], hut["f"], hut["inner"][1], hut["w"], hut["d"], hut["h"], "south",
                               pintu=hut["at"])
    sh = bangun(k, g, 35, 37, 43, 46, h=3, pintu="north", nama="kuil_makam")
    khk.interior_kuil_kecil(k, sh["inner"][0], sh["f"], sh["inner"][1], sh["w"], sh["d"], sh["h"], "north",
                            pintu=sh["at"])
    ex = {"tebing": 38, "pulau": mx0 + mw + 4}.get(varian, mx0 + mw + 1)
    tiang_lentera_baris(k, ((ex, gz - 4, "east"), (ex, gz + 4, "east")), warna="putih")
    khk.pasar_hantu(k, hantu0, 40, LY + 1, 14, "west", "dekat_makam")
    khk.pasar_hantu(k, hantu0 + 1, 40, LY + 1, 31, "west", "dekat_makam")
    khk.nama_jalan(k, 45, LY, 15, "east")
    return selesai(k)


# ================================================================================================ Kota Iblis alley
def lot_lorong_hantu(folder, g, nama="hidup42/lorong_hantu", lapak0=0, hantu0=0):
    """Lorong Hantu of Kota Iblis (its night street is the pasar iblis): a lane north-south with five rentable stalls,
    the pawnshop (rumah_gadai kit) and the talisman shop west, the poison apothecary (apotek kit) east, the empty
    warehouse, two dead ends and a dry ditch under a bone bridge - the four Ghost Market spots of the town."""
    k = lot_baru(folder, nama, g, tinggi=28)
    jalan(k, g, 0, 0, N - 1, 4)
    jalan(k, g, 19, 5, 27, N - 1)
    a = bangun(k, g, 2, 7, 13, 20, h=4, pintu="east", nama="rumah_gadai_hantu")
    khk.interior_rumah_gadai(k, a["inner"][0], a["f"], a["inner"][1], a["w"], a["d"], a["h"], "east", pintu=a["at"],
                             titik_maks=8)
    b = bangun(k, g, 2, 24, 12, 34, h=3, pintu="east", nama="toko_jimat_hantu")
    khk.interior_toko_jimat(k, b["inner"][0], b["f"], b["inner"][1], b["w"], b["d"], b["h"], "east", pintu=b["at"],
                            titik_maks=8)
    c = bangun(k, g, 33, 7, 45, 20, h=4, pintu="west", nama="apotek_racun")
    khk.interior_apotek(k, c["inner"][0], c["f"], c["inner"][1], c["w"], c["d"], c["h"], "west", pintu=c["at"],
                        titik_maks=8)
    d = bangun(k, g, 33, 25, 45, 36, h=3, pintu="west", nama="gudang_kosong_hantu")
    f = d["f"]
    ix0, iz0, ix1, iz1 = d["inner"]
    for (x, z) in ((ix0, iz0), (ix1, iz1), (ix1, iz0 + 2)):
        k.put(x, f + d["h"], z, "minecraft:cobweb")
    for (x, z) in ((ix1, iz0), (ix1 - 1, iz1), (ix0 + 1, iz1)):
        k.put(x, f + 1, z, "minecraft:barrel", {"facing": "up", "open": "true"})
    k.put(ix1 - 3, f + 1, iz0 + 1, "minecraft:decorated_pot", {"facing": "north", "cracked": "true",
                                                               "waterlogged": "false"})
    khk.pasar_hantu(k, hantu0, ix0 + 1, f + 1, (iz0 + iz1) // 2, "east", "gudang_kosong")
    for i, (x, z, fc) in enumerate(((17, 9, "east"), (17, 15, "east"), (29, 9, "west"), (29, 15, "west"),
                                    (17, 28, "east"))):
        lapak(k, g, lapak0 + i, x, z, fc, barang=("minecraft:skeleton_skull", "minecraft:candle",
                                                  "minecraft:decorated_pot"))
    # the dry ditch (3 deep) under its bone bridge at the south end, steps down at its west end
    for x in range(14, 33):
        for z in range(40, 45):
            for yy in (LY, LY - 1, LY - 2):
                k.put(x, yy, z, "minecraft:air")
            k.put(x, LY - 3, z, "minecraft:soul_soil" if (x + z) % 3 else "minecraft:bone_block",
                  None if (x + z) % 3 else {"axis": "y"})
    for z in range(39, 46):
        for x in range(21, 26):
            k.put(x, LY, z, "minecraft:bone_block", {"axis": "z"})
        k.put(20, LY + 1, z, "minecraft:polished_blackstone_wall")
        k.put(26, LY + 1, z, "minecraft:polished_blackstone_wall")
    for i in range(3):
        tangga(k, 16 - i, LY - 2 + i, 44, "minecraft:polished_blackstone_brick_stairs", "west")
    khk.pasar_hantu(k, hantu0 + 1, 23, LY - 2, 42, "north", "bawah_jembatan")
    k.put(24, LY - 2, 43, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    jalan(k, g, 3, 37, 18, 38)
    khk.pasar_hantu(k, hantu0 + 2, 4, LY + 1, 37, "east", "gang_buntu")
    jalan(k, g, 28, 37, 45, 38)
    khk.pasar_hantu(k, hantu0 + 3, 44, LY + 1, 38, "west", "gang_buntu")
    tiang_lentera_baris(k, ((18, 6, "east"), (28, 6, "west"), (18, 22, "east"), (28, 22, "west"), (18, 35, "east"),
                            (28, 35, "west")), warna="hijau")
    khk.nama_jalan(k, 17, LY, 5, "north")
    khk.spanduk(k, 18, 2, 28, 2, LY, tinggi=6, pasangan="hantu")
    khk.jongkok(k, 10, LY, 39, "north", 3)
    khk.lentera_atap(k, 1, 6, 46, 46, LY, maks=3, jarak=8, warna="hijau", salt=3)
    return selesai(k)
