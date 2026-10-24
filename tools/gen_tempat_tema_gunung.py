"""Kota Gunung world generation (DESIGN 38.T.6, module 38TK-G): the nine terrace districts of a mountain town
("Kota Teras Awan" and its sisters).

Writes every NBT lot (data/kultivator/structure/kota_tema/gunung/**), the template pools (base + one pool per biome
style), the processor lists and the nine kultivator:tata_letak structures (worldgen/structure/kota_tema/gunung/*).
Structure sets, tags and the pack dunia_kotatema are 38TK inti's (tools/gen_tempat_tema_data.py). Run:

    python tools/gen_tempat_tema_gunung.py            (then python tools/cek_tempat.py)
    python tools/gen_tempat_tema_gunung.py --cerita   (story data + lang only: quests, dialogs, rumours, advancements,
                                                      loot tables, schedule file, lang-src/tempat_tema_gunung.*)

Plan (38.T.6.2, before rotation, district cells 96 blocks, lots 48, north = -z = uphill):

             x = -1               x = 0             x = +1
  z = -1   [tebing_papan]     [puncak_emas]     [biara_awan]          top row     (district dy 36)
  z =  0   [teras_hunian]     [aula_herba]      [teras_kebun]         middle row  (district dy 18)
  z = +1   [penginapan_..]    [kaki_gerbang]    [pasar_kaki]          bottom row  (district dy 0)
                              (wall + Gerbang Kaki on the south side of the bottom row)

Terraces. Every district holds two lot rows: the north lots stand 6 higher than the south lots (structure lot dy 6,
template content shifted down 6 so the street floor stays at template y 4); the district dy of RencanaKotaTema adds
18 per row. Six terrace levels therefore climb 0, 6, 18, 24, 36, 42 from the gate to the Aula Emas. The stairs of
every step live in the LOWER lot: a 5-wide stone flight against a retaining wall (stone bricks with moss), a gapura
(paifang) on every landing, stone lanterns at the feet. Rows join east-west through two terrace streets per district
(side code J), north-south through the stairs (side code G); the bottom row is walled (T), the outer cliff edges are
railed (B).

Life points (kultivator_titik) and anchors follow 38.T.2.4: jalur_tangga (pilgrim/porter route, jalur "ziarah"),
stasiun_lift (cable-car and pulley ends), pandang (scene camera spots), meditasi niches, kasur/kursi/kerja points.
Anchors of the theme module "tema_gunung" (38TK-G's own handler) mark the lift stations, the porter post, the nine
pilgrim landings, the twelve cliff herbs, the monkey haunts, the rockfall lanes and every themed service; shared
anchors (kota/*, kotabesar/*) are served by their owners without new code; kota_tema/situs|tiba|zona are 38TK inti's.
Only world generation in NEW chunks places these templates; every moving thing is an entity. Deterministic.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import tempat_tema_lib as ttl  # noqa: E402
import kota_besar_kit as K  # noqa: E402
import hias_tema as HT  # noqa: E402
from kota_besar_kit import FACE, OPP  # noqa: E402

TEMA = "gunung"
F = "kota_tema/gunung"
NS = "kultivator:"
RENCANA = "kota_tema_gunung"
MOD = "tema_gunung"          # anchor module of this theme (dunia/tempat/tema/gunung/GunungAnchors.java)

FS = 4                       # south lot street floor (canvas y)
FN = 10                      # north lot street floor (canvas y); template y = canvas y - SHIFT_N
FU = 22                      # floor of the next row up (north lots' up-stairs end here)
SHIFT_N = 6
ROW_DY = {"bawah": 0, "tengah": 18, "atas": 36}   # district dy RencanaKotaTema must give (kota-TK.md request)
CX0, CX1 = 46, 50            # the central stair flight (5 wide)
COR0, COR1 = 44, 52          # the central corridor kept free of buildings
NST = (18, 22)               # north terrace street (z)
SST = (64, 68)               # south terrace street (z)
H = 72                       # canvas height

# ===================================================================================================== palette
GP = dict(K.PAL)
GP.update({
    "path": "minecraft:stone_bricks", "path_alt": "minecraft:cobblestone", "floor": "minecraft:polished_andesite",
    "floor_alt": "minecraft:andesite", "found": "minecraft:stone_bricks", "found_alt": "minecraft:mossy_stone_bricks",
    "ridge": "minecraft:andesite_slab", "rock": "minecraft:stone", "rock_alt": "minecraft:andesite",
    "rock_moss": "minecraft:mossy_cobblestone", "stilt": "minecraft:spruce_log", "cloth": "minecraft:yellow_wool",
    "cloth_alt": "minecraft:orange_wool", "banner": "yellow", "banner_alt": "orange", "bed": "minecraft:brown_bed",
    "rug": "minecraft:yellow_carpet", "rug_alt": "minecraft:brown_carpet", "bush": "minecraft:azalea",
    "flower": "minecraft:cornflower", "flower_alt": "minecraft:oxeye_daisy", "flower_tall": "minecraft:lilac",
    "flower_tall_alt": "minecraft:peony",
})
PILGRIM = {"kerb": "minecraft:stone_bricks"}

# ===================================================================================================== registry
DISTRIK = {  # id -> (grid x, grid z, row)
    "tebing_papan": (-1, -1, "atas"), "puncak_emas": (0, -1, "atas"), "biara_awan": (1, -1, "atas"),
    "teras_hunian": (-1, 0, "tengah"), "aula_herba": (0, 0, "tengah"), "teras_kebun": (1, 0, "tengah"),
    "penginapan_peziarah": (-1, 1, "bawah"), "kaki_gerbang": (0, 1, "bawah"), "pasar_kaki": (1, 1, "bawah"),
}
ORDER = ["tebing_papan", "puncak_emas", "biara_awan", "teras_hunian", "aula_herba", "teras_kebun",
         "penginapan_peziarah", "kaki_gerbang", "pasar_kaki"]
LOTS = {d: [] for d in ORDER}
POOLS = {}  # pool path -> ([template ids], utuh)
UTUH = set()


def tid(name):
    return "%s%s/%s" % (NS, F, name)


def sisi_lot(distrik, i, j):
    """Side codes (n, e, s, w) of lot (i, j) of a district (38.T.2.4)."""
    gx, _gz, row = DISTRIK[distrik]
    n = "G" if j == 1 else ("B" if row == "atas" else "G")
    s = "G" if j == 0 else ("T" if row == "bawah" else "G")
    w = "J" if i == 1 else ("T" if (gx == -1 and row == "bawah") else ("B" if gx == -1 else "J"))
    e = "J" if i == 0 else ("T" if (gx == 1 and row == "bawah") else ("B" if gx == 1 else "J"))
    return n, e, s, w


def kanvas(distrik, seed_extra=0):
    k = K.Kanvas(96, H, 96, seed=tl._seed(F + "/" + distrik) + seed_extra, palette=GP)
    return k


KANVAS = {}  # name -> canvas (kept for --pratinjau)

# rooms to dress (D-TK1 detail pass): registered while building, dressed in potong() after everything else stands, so
# the dressing only fills what is still empty (hias_tema.ruang never overwrites a placed block)
_RUANG = []


def ruang(k, x0, z0, x1, z1, f, gaya, **kw):
    """Registers one room of canvas k (inner floor box, floor level f, hias_tema style)."""
    _RUANG.append((k, (x0, z0, x1, z1, f, gaya), kw))


def hias(k):
    n = 0
    for entry in [r for r in _RUANG if r[0] is k]:
        n += HT.ruang(k, *entry[1], **entry[2])
        _RUANG.remove(entry)
    return n


def potong(k, distrik, names, utuh=(), pool_names=None, varian=""):
    """Cuts a district canvas into its four lots (north lots shifted down SHIFT_N) and saves them. names[(i, j)] = lot
    template name; pool_names[(i, j)] = shared pool (variants) or None (own pool). Returns {(i, j): Lot}."""
    hias(k)
    K.connect_all(k)
    KANVAS[distrik + varian] = k
    out = {}
    for (i, j), name in names.items():
        shift = SHIFT_N if j == 0 else 0
        x0, z0 = 48 * i, 48 * j
        top = 0
        cells = []
        for (x, y, z), cell in k.cells.items():
            if x0 <= x < x0 + 48 and z0 <= z < z0 + 48 and y - shift >= 0:
                cells.append(((x - x0, y - shift, z - z0), cell))
                if cell[0][0] != "minecraft:air":
                    top = max(top, y - shift)
        h = max(24, top + 2)
        full = "%s/%s" % (distrik, name)
        lot = tl.Lot(F, full, tinggi=h, palet=GP)
        for (x, y, z), cell in cells:
            if y < h:
                lot.cells[(x, y, z)] = cell
        for (x, y, z, nbt) in k.entities:
            if x0 <= x < x0 + 48 and z0 <= z < z0 + 48:
                lot.entities.append((x - x0, y - shift, z - z0, nbt))
                if nbt.get("id") == "kultivator:dunia_anchor":
                    lot.jangkar_n += 1
        for t in k.titik_list:
            px, py, pz = t["pos"]
            if x0 <= px < x0 + 48 and z0 <= pz < z0 + 48 and 0 <= py - shift < h:
                e = dict(t)
                e["pos"] = [px - x0, py - shift, pz - z0]
                if "data" in t:
                    e["data"] = dict(t["data"])
                    if t["jenis"] in tl.TITIK_KOTAK:
                        e["data"]["dx"] = str(min(int(t["data"]["dx"]), x0 + 47 - px))
                        e["data"]["dz"] = str(min(int(t["data"]["dz"]), z0 + 47 - pz))
                lot.titik_list.append(e)
        lot.sisi(*sisi_lot(distrik, i, j))
        lot.simpan()
        pid = (pool_names or {}).get((i, j)) or full
        if pid not in POOLS:
            POOLS[pid] = ([], name in utuh)
            LOTS[distrik].append({"dx": -48 + 48 * i, "dz": -48 + 48 * j, "dy": shift, "pool": pid})
        POOLS[pid][0].append(tid(full))
        out[(i, j)] = lot
    return out


# ===================================================================================================== small kit
def pav(k, x0, z0, x1, z1, y, role="path", alt="path_alt", ch=0.22):
    K.paving(k, min(x0, x1), min(z0, z1), max(x0, x1), max(z0, z1), y, role=role, alt=alt, alt_chance=ch)


def rail(k, x0, z0, x1, z1, y, role="kerb_wall", skip=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if skip and skip(x, z):
                continue
            k.put(x, y, z, role)


def stone_lantern(k, x, y, z):
    K.stone_lantern(k, x, y, z)


def rock(k, cx, cz, y, r, h, moss=0.35):
    """A weathered boulder / outcrop: stone, andesite, tuff and mossy cobble, rounded."""
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = math.hypot(dx, dz)
            if d > r + 0.3:
                continue
            hh = max(1, int(round(h * (1.0 - (d / (r + 0.8)) ** 2))) + k.rng.randint(0, 1))
            for dy in range(hh):
                rr = k.rng.random()
                blk = "rock_moss" if (dy == hh - 1 and rr < moss) else ("rock_alt" if rr < 0.45 else
                                                                         "minecraft:tuff" if rr < 0.6 else "rock")
                k.put(cx + dx, y + 1 + dy, cz + dz, blk)
            if k.rng.random() < 0.25 and k.get(cx + dx, y + hh + 1, cz + dz) is None:
                k.put(cx + dx, y + hh + 1, cz + dz, "minecraft:moss_carpet")


def pine_at(k, x, z, y, h=None):
    K.pine(k, x, y + 1, z, h or k.rng.randint(8, 11))


def planter(k, x, z, y, plant="minecraft:azalea"):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                k.slab(x + dx, y + 1, z + dz, "kerb_slab", "bottom")
    k.put(x, y + 1, z, "minecraft:moss_block")
    k.put(x, y + 2, z, plant)


def bench_row(k, x, z, facing, y, n=3):
    K.bench(k, x, z, facing, y=y, n=n)


def ridge_cap(k, x0, x1, z, y):
    """Stone ridge tiles on a gable (andesite slabs; snow / moss in the snow and forest styles)."""
    for x in range(x0, x1 + 1):
        if k.get(x, y, z) in (None, "minecraft:air"):
            k.slab(x, y, z, "ridge", "bottom")


def gable_roof(k, x0, y, z0, x1, z1, along="x", overhang=1):
    top = K.gable(k, x0, y, z0, x1, z1, along=along, overhang=overhang, pearl=False)
    if along == "x":
        rows = (z1 + overhang) - (z0 - overhang) + 1
        mz = (z0 - overhang) + rows // 2 - (0 if rows % 2 else 1)
        ridge_cap(k, x0 - overhang + 1, x1 + overhang - 1, mz, top + 1)
    return top


def door_at(k, x, y, z, facing):
    K.door(k, x, y, z, facing)


def window_pair(k, x, y, z, facing):
    K.lattice(k, x, y, z, facing)
    K.lattice(k, x, y + 1, z, facing)


def sign_board(k, x, y, z, along="x"):
    K.plaque(k, x, y, z, along)


def jangkar(k, x, y, z, kind, yaw=0.0, **data):
    k.jangkar(x + 0.5, y, z + 0.5, kind, yaw=yaw, **data)


def tangga(k, pts, mulai, y=None):
    """Pilgrim / porter route nodes (titik jalur_tangga, jalur ziarah, urut from `mulai`)."""
    for i, p in enumerate(pts):
        x, z = p[0], p[1]
        yy = p[2] if len(p) > 2 else y
        k.titik("jalur_tangga", x, yy, z, "north", {"urut": mulai + i, "jalur": "ziarah"})


# ===================================================================================================== terrace frame
def frame(k, distrik, n_street=True, s_street=True, n_skip=None, s_skip=None, center_n=True, center_s=True,
          up_stairs=True, bordes=None):
    """Foundation, the two terrace floors, retaining walls, the stair flights, the two terrace streets, the central
    path, edge railings and street lamps of one district canvas. bordes = (first pilgrim landing number) for the
    central column, which carries the Seribu Anak Tangga landings (anchors tema_gunung/bordes)."""
    gx, _gz, row = DISTRIK[distrik]
    # ------------------------------------------------------------------ ground
    # no foundation fill: beard_box raises the ground to the street floor under every lot (38.B.2.3), so only the
    # visible terrace faces (retaining walls below) are built
    pav(k, 0, 48, 95, 95, FS, role="floor", alt="floor_alt", ch=0.08)
    pav(k, 0, 0, 95, 47, FN, role="floor", alt="floor_alt", ch=0.08)
    # ------------------------------------------------------------------ south row -> north row (dy 6)
    for x in range(96):
        if COR0 + 1 <= x <= COR1 - 1:
            continue
        for z in (48, 49):
            for y in range(FS + 1, FN):
                k.put(x, y, z, "found" if k.rng.random() > 0.18 else "found_alt")
            k.put(x, FN, z, "kerb")
        if x % 8 == 3:
            k.put(x, FS + 1, 50, "kerb_chisel")
            k.put(x, FS + 2, 50, "kerb_wall")
            K.lit(k, x, FN - 1, 50, chain=1) if x % 16 == 11 else None
        if k.rng.random() < 0.18:
            k.put(x, k.rng.randint(FS + 2, FN - 1), 50, "minecraft:vine", {"north": "true", "east": "false",
                                                                          "south": "false", "west": "false", "up": "false"})
    for i in range(6):
        z, y = 53 - i, FS + 1 + i
        for x in range(CX0, CX1 + 1):
            k.fill(x, FS + 1, z, x, y - 1, z, "found")
            k.stairs(x, y, z, "kerb_stairs", "north")
        for x in (COR0 + 1, COR1 - 1):
            k.fill(x, FS + 1, z, x, y, z, "kerb")
            k.put(x, y + 1, z, "kerb_wall")
    stone_lantern(k, COR0 + 1, FS + 1, 55)
    stone_lantern(k, COR1 - 1, FS + 1, 55)
    # ------------------------------------------------------------------ north row -> next row (dy 12) or the cliff
    if row != "atas" and up_stairs:
        for x in range(96):
            if COR0 + 1 <= x <= COR1 - 1:
                continue
            for z in (0, 1):
                for y in range(FN + 1, FU):
                    k.put(x, y, z, "found" if k.rng.random() > 0.2 else "found_alt")
                k.put(x, FU, z, "kerb")
            if x % 8 == 3:
                for y in range(FN + 1, FU - 1):
                    k.put(x, y, 2, "kerb" if y % 3 else "kerb_chisel")
                k.stairs(x, FU - 1, 2, "kerb_stairs", "north", half="top")
            if x % 16 == 7:
                K.lit(k, x, FU - 2, 2, chain=1, role="glow")
            if k.rng.random() < 0.22:
                k.put(x, k.rng.randint(FN + 3, FU - 1), 2, "minecraft:vine", {"north": "true", "east": "false",
                                                                             "south": "false", "west": "false", "up": "false"})
        steps = [(14 - i, FN + 1 + i) for i in range(6)] + [(5 - i, FN + 7 + i) for i in range(6)]
        for z, y in steps:
            for x in range(CX0, CX1 + 1):
                k.fill(x, FN + 1, z, x, y - 1, z, "found")
                k.stairs(x, y, z, "kerb_stairs", "north")
            for x in (COR0 + 1, COR1 - 1):
                k.fill(x, FN + 1, z, x, y, z, "kerb")
                k.put(x, y + 1, z, "kerb_wall")
        for z in (6, 7, 8):
            for x in range(COR0 + 1, COR1):
                k.fill(x, FN + 1, z, x, FN + 5, z, "found")
                k.put(x, FN + 6, z, "path" if x not in (COR0 + 1, COR1 - 1) else "kerb")
            for x in (COR0 + 1, COR1 - 1):
                k.put(x, FN + 7, z, "kerb_wall")
        K.paifang(k, 48, 7, y=FN + 6, width=9, height=7, gold=False)
        stone_lantern(k, COR0 + 1, FN + 1, 16)
        stone_lantern(k, COR1 - 1, FN + 1, 16)
    elif row == "atas":
        # the natural cliff behind the top row: rock, tuff, moss and a few pines on its crest
        for x in range(96):
            hh = 10 + int(5 * math.sin(x * 0.21) + 4 * math.sin(x * 0.53 + 1.3)) + k.rng.randint(0, 2)
            for z in range(0, 4):
                top = hh - z * 2 - k.rng.randint(0, 1)
                for y in range(FN + 1, FN + 1 + max(1, top)):
                    rr = k.rng.random()
                    k.put(x, y, z, "rock" if rr < 0.45 else "rock_alt" if rr < 0.7 else "minecraft:tuff" if rr < 0.85
                          else "rock_moss")
                if top > 2 and k.rng.random() < 0.3:
                    k.put(x, FN + 1 + top, z, "minecraft:moss_carpet")
            if x % 13 == 5:
                K.pine(k, x, FN + 1 + hh, 1, k.rng.randint(6, 9))
    # ------------------------------------------------------------------ streets, path, rails, lamps
    if n_street:
        for x in range(96):
            if n_skip and n_skip(x):
                continue
            pav(k, x, NST[0], x, NST[1], FN)
    if s_street:
        for x in range(96):
            if s_skip and s_skip(x):
                continue
            pav(k, x, SST[0], x, SST[1], FS)
    if center_n:
        pav(k, CX0, NST[1] + 1, CX1, 47, FN)
    if center_s:
        pav(k, CX0, 54, CX1, SST[0] - 1, FS)
        pav(k, CX0, SST[1] + 1, CX1, 95, FS)
    # the north row looks down on the south row: balustrade with the stair opening
    rail(k, 0, 47, 95, 47, FN + 1, skip=lambda x, z: COR0 + 1 <= x <= COR1 - 1)
    if row != "bawah":
        rail(k, 0, 95, 95, 95, FS + 1, skip=lambda x, z: COR0 + 1 <= x <= COR1 - 1)
    if row != "bawah":
        if gx == -1:
            rail(k, 0, 2, 0, 46, FN + 1)
            rail(k, 0, 50, 0, 94, FS + 1)
        if gx == 1:
            rail(k, 95, 2, 95, 46, FN + 1)
            rail(k, 95, 50, 95, 94, FS + 1)
    for x in range(6, 96, 12):
        if COR0 <= x <= COR1:
            continue
        if n_street and not (n_skip and n_skip(x)):
            K.post(k, x, FN + 1, NST[0] - 1, height=3)
        if s_street and not (s_skip and s_skip(x)):
            K.post(k, x, FS + 1, SST[1] + 1, height=3)
    if bordes is not None:
        # three pilgrim landings per central district: S-row stair foot, N-row stair foot, the paifang landing
        jangkar(k, 48, FS + 1, 56, MOD + "/bordes", no=bordes, protect=6)
        jangkar(k, 48, FN + 1, 24, MOD + "/bordes", no=bordes + 1, protect=6)
        if row != "atas":
            jangkar(k, 48, FN + 7, 7, MOD + "/bordes", no=bordes + 2, protect=6)


def zone_ok(x0, x1):
    return not (x0 <= COR1 and x1 >= COR0)


# ===================================================================================================== buildings
def rumah_gunung(k, x0, z0, w, d, f, face="south", seed=0, storeys=1, stilts=0, balcony=True, beds=2, kitchen=True,
                 tile=False, sign=False):
    """A mountain house: stone plinth, spruce frame with white plaster panels, lattice windows, a dark-oak gable with a
    stone ridge, an optional balcony and stilts (rumah panggung) of `stilts` blocks under its front part. Furnished:
    beds (kasur points), a hearth corner (kerja:kuali), a table (meja_makan + kursi), shelves. Returns the door spot."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    if stilts:
        for (sx, sz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), ((x0 + x1) // 2, z0), ((x0 + x1) // 2, z1)):
            for y in range(f - stilts, f + 1):
                K.put_ax(k, sx, y, sz, "stilt")
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                k.put(x, f, z, "wood_alt")
        for x in range(x0, x1 + 1, 2):
            k.put(x, f - 1, z0, "minecraft:spruce_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                             "powered": "false", "waterlogged": "false"})
    else:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                k.put(x, f, z, "kerb" if x in (x0, x1) or z in (z0, z1) else "wood")
    hh = 4
    for s in range(storeys):
        fy = f + s * (hh + 1)
        k.walls(x0, fy + 1, z0, x1, fy + hh, z1, role="plaster", pillar="minecraft:spruce_log", windows=0)
        for x in range(x0 + 2, x1 - 1, 3):
            for z, fc in ((z0, "north"), (z1, "south")):
                if not (fc == face and abs(x - (x0 + x1) // 2) <= 1):
                    window_pair(k, x, fy + 2, z, fc)
        for z in range(z0 + 2, z1 - 1, 3):
            for x, fc in ((x0, "west"), (x1, "east")):
                window_pair(k, x, fy + 2, z, fc)
        K.beam_band(k, x0, z0, x1, z1, fy + hh, role="minecraft:stripped_spruce_log")
        if s > 0:
            k.fill(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, "wood_alt")
            k.put(x0 + 1, fy, z0 + 1, "minecraft:air")
            K.ladder(k, x0 + 1, fy - hh, fy, z0 + 1, "south")
    top_f = f + storeys * (hh + 1) - 1
    k.fill(x0 + 1, top_f, z0 + 1, x1 - 1, top_f, z1 - 1, "wood")
    for s in range(storeys):
        ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f + s * (hh + 1), "rumah" if beds or s else "kantor" if sign else "gudang",
              isi=0.45, rug=s == storeys - 1 or storeys == 1)
    along = "x" if w >= d else "z"
    if along == "x":
        gable_roof(k, x0, top_f + 1, z0, x1, z1, along="x", overhang=1)
    else:
        K.gable(k, x0, top_f + 1, z0, x1, z1, along="z", overhang=1, pearl=False)
    # door
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if face == "south":
        dx_, dz_ = cx, z1
    elif face == "north":
        dx_, dz_ = cx, z0
    elif face == "east":
        dx_, dz_ = x1, cz
    else:
        dx_, dz_ = x0, cz
    k.put(dx_, f + 1, dz_, "minecraft:air")
    k.put(dx_, f + 2, dz_, "minecraft:air")
    K.door(k, dx_, f + 1, dz_, face)
    k.titik("pintu", dx_, f + 1, dz_, face)
    if sign:
        sign_board(k, dx_, f + 3, dz_ if face in ("north", "south") else dz_, "x" if face in ("north", "south") else "z")
    # interior: beds against the back wall of the top storey, a hearth corner, a table with two chairs
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    yb = f + 1 + (storeys - 1) * (hh + 1)
    back = "north" if face in ("south", "east", "west") else "south"
    bz = iz0 + 1 if back == "north" else iz1 - 1
    for b in range(beds):
        bx = ix0 + b * 2
        if bx > ix1 - 1:
            break
        K.bed(k, bx, yb, bz, back)
        k.titik("kasur", bx, yb, bz, OPP[back])
    if kitchen and ix1 - ix0 >= 4:
        kz = iz0 if back == "north" else iz1
        k.put(ix1, f + 1, kz, "minecraft:smoker", {"facing": OPP[back], "lit": "false"})
        K.barrel(k, ix1 - 1, f + 1, kz, "up")
        k.put(ix1, f + 3, kz, "minecraft:dark_oak_trapdoor", {"facing": OPP[back], "half": "top", "open": "false",
                                                             "powered": "false", "waterlogged": "false"})
        k.titik("kerja:kuali", ix1, f + 1, kz + (1 if back == "north" else -1), back)
    if ix1 - ix0 >= 3 and iz1 - iz0 >= 4:
        mx, mz = (ix0 + ix1) // 2, (iz0 + iz1) // 2 + (1 if back == "north" else -1)
        K.table(k, mx, f + 1, mz)
        k.titik("meja_makan", mx, f + 1, mz, "south")
        for dd in ("east", "west"):
            ddx, _ddz = FACE[dd]
            if ix0 <= mx + ddx <= ix1:
                K.chair(k, mx + ddx, f + 1, mz, OPP[dd])
                k.titik("kursi", mx + ddx, f + 1, mz, OPP[dd])
    K.lit(k, cx, top_f, cz, chain=1)
    if balcony and storeys > 1 and face in ("south", "north"):
        # a wooden gallery along the front of the upper storey (Chongqing stilt-house balcony)
        yb2 = f + (storeys - 1) * (hh + 1)
        zb = z1 + 1 if face == "south" else z0 - 1
        for x in range(x0, x1 + 1):
            k.slab(x, yb2, zb, "wood_slab", "top")
            k.put(x, yb2 + 1, zb, "rail")
        k.put(cx, yb2 + 1, zb, "minecraft:air")
        k.put(cx, yb2 + 1, z1 if face == "south" else z0, "minecraft:air")
        k.put(cx, yb2 + 2, z1 if face == "south" else z0, "minecraft:air")
    return dx_, dz_


def kios(k, x, z, facing, f, goods, n):
    """A pilgrim market stall (counter, posts, yellow-orange awning) with a lapak point for its seller."""
    sx, sz = K.stall(k, x, z, facing, y=f, w=3, goods=goods)
    k.titik("lapak", sx, f + 1, sz, facing, {"no": n})


def herb_rack(k, x, z, f, along="x", n=5, tiers=3):
    """A drying rack for mountain herbs: spruce posts, three tiers of top trapdoors with leaf litter and bundled
    herbs (dried kelp blocks stand for the bundles, hay for straw)."""
    for i in range(n):
        px, pz = (x + i, z) if along == "x" else (x, z + i)
        for t in range(tiers):
            y = f + 1 + t * 2
            if i in (0, n - 1):
                k.put(px, y, pz, "wood_fence")
                k.put(px, y + 1, pz, "wood_fence")
            k.put(px, y + 1, pz, "minecraft:spruce_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                              "powered": "false", "waterlogged": "false"}) \
                if 0 < i < n - 1 else None
            if 0 < i < n - 1:
                r = k.rng.random()
                if r < 0.5:
                    k.put(px, y + 2, pz, "minecraft:leaf_litter", {"facing": "north", "segment_amount": str(k.rng.randint(2, 4))})
                elif r < 0.7:
                    k.put(px, y + 2, pz, "minecraft:dried_kelp_block")


def incense(k, x, y, z, big=False):
    K.incense_burner(k, x, y, z, big=big)


# ===================================================================================================== walls
def tembok_lurus(name, gate=False, jaga=False):
    """South wall run (48 along x, 8 deep, inside = north z 0): stone brick body with a battered base, walkway at +8,
    crenels, lantern posts; gate=True opens the Gerbang Kaki arch (x 19..29) under a two-storey gate tower."""
    lot = tl.Lot(F, "tembok/" + name, tinggi=30, lebar=48, dalam=8, palet=GP)
    lot.fill(0, 0, 0, 47, FS - 1, 7, "found")
    pav(lot, 0, 0, 47, 0, FS, role="path", alt="path_alt")
    for x in range(48):
        for z in range(1, 7):
            for y in range(FS + 1, FS + 9):
                lot.put(x, y, z, "found" if lot.rng.random() > 0.12 else "found_alt")
        for z in (7,):
            for y in range(FS + 1, FS + 4):
                lot.put(x, y, z, "minecraft:stone_brick_stairs", {"facing": "north", "half": "bottom", "shape": "straight",
                                                                 "waterlogged": "false"}) if y == FS + 1 else \
                    lot.put(x, y, z, "found")
        lot.put(x, FS + 9, 1, "kerb_wall")
        if x % 2 == 0:
            lot.put(x, FS + 9, 6, "kerb")
            lot.slab(x, FS + 10, 6, "kerb_slab")
        else:
            lot.put(x, FS + 9, 6, "kerb_wall")
        for z in range(1, 6):
            lot.put(x, FS + 8, z, "path" if (x + z) % 5 else "path_alt")
        if x % 12 == 6:
            K.post(lot, x, FS + 9, 3, height=2)
    for x in range(2, 46, 6):
        lot.put(x, FS + 4, 6, "minecraft:stone_bricks")
        lot.put(x, FS + 5, 6, "minecraft:iron_bars")
    if gate:
        for x in range(19, 30):
            for z in range(0, 8):
                for y in range(FS + 1, FS + 8):
                    lot.put(x, y, z, "minecraft:air")
                lot.put(x, FS, z, "path")
        for x in (19, 29):
            for z in range(0, 8):
                for y in range(FS + 1, FS + 8):
                    lot.put(x, y, z, "kerb_chisel" if y % 3 == 0 else "kerb")
        for x in range(20, 29):
            for z in range(0, 8):
                lot.put(x, FS + 7, z, "kerb")
            lot.stairs(x, FS + 6, 0, "kerb_stairs", "south", half="top")
            lot.stairs(x, FS + 6, 7, "kerb_stairs", "north", half="top")
        # gate tower: red pillars, plaster, lattice, double eaves
        gx0, gx1 = 16, 32
        for x in range(gx0, gx1 + 1):
            for z in range(0, 8):
                lot.put(x, FS + 9, z, "wood")
        lot.walls(gx0 + 1, FS + 10, 1, gx1 - 1, FS + 13, 6, role="plaster", pillar="red", windows=0)
        for x in range(gx0 + 3, gx1 - 2, 3):
            window_pair(lot, x, FS + 11, 6, "south")
            window_pair(lot, x, FS + 11, 1, "north")
        K.roof_over(lot, gx0 + 1, 1, gx1 - 1, 6, FS + 14, kind="hip", tiers=2, overhang=1, top="pearl")
        K.plaque(lot, 24, FS + 8, 7, "x")
        K.lit(lot, 21, FS + 7, 7, chain=1, role="lamp")
        K.lit(lot, 27, FS + 7, 7, chain=1, role="lamp")
        lot.titik("jaga", 18, FS + 1, 0, "south")
        lot.titik("jaga", 30, FS + 1, 0, "south")
        lot.titik("tiba", 24, FS + 1, 0, "south")
    if jaga:
        lot.walls(30, FS + 9, 1, 37, FS + 12, 6, role="plaster", pillar="red", windows=0)
        door_at(lot, 33, FS + 9, 1, "north")
        gable_roof(lot, 30, FS + 13, 1, 37, 6, along="x")
        lot.titik("jaga", 33, FS + 9, 3, "south")
    lot.sisi("T", "T", "B", "T")
    lot.simpan()
    return lot


def tembok_sisi(name):
    """West wall run (8 along x, 48 deep, outside = west x 0): the same body turned; used rotated 180 on the east."""
    lot = tl.Lot(F, "tembok/" + name, tinggi=26, lebar=8, dalam=48, palet=GP)
    lot.fill(0, 0, 0, 7, FS - 1, 47, "found")
    for z in range(48):
        for x in range(1, 7):
            for y in range(FS, FS + 9):
                lot.put(x, y, z, "found" if lot.rng.random() > 0.12 else "found_alt")
        lot.put(0, FS, z, "minecraft:stone_brick_stairs", {"facing": "east", "half": "bottom", "shape": "straight",
                                                           "waterlogged": "false"})
        lot.put(6, FS + 9, z, "kerb_wall")
        if z % 2 == 0:
            lot.put(1, FS + 9, z, "kerb")
            lot.slab(1, FS + 10, z, "kerb_slab")
        else:
            lot.put(1, FS + 9, z, "kerb_wall")
        for x in range(2, 6):
            lot.put(x, FS + 8, z, "path" if (x + z) % 5 else "path_alt")
        if z % 12 == 6:
            K.post(lot, 4, FS + 9, z, height=2)
        lot.put(7, FS, z, "path")
    for z in range(3, 46, 6):
        lot.put(1, FS + 5, z, "minecraft:iron_bars")
    # an inner stair up to the walkway
    for i in range(8):
        lot.stairs(7, FS + 1 + i, 30 - i, "kerb_stairs", "north") if i < 8 else None
    lot.sisi("T", "T", "T", "B")
    lot.simpan()
    return lot


def tembok_sudut(name):
    """Corner tower 8 x 8: a square bastion one storey over the walkway with a hip roof."""
    lot = tl.Lot(F, "tembok/" + name, tinggi=28, lebar=8, dalam=8, palet=GP)
    lot.fill(0, 0, 0, 7, FS - 1, 7, "found")
    for x in range(8):
        for z in range(8):
            for y in range(FS, FS + 9):
                lot.put(x, y, z, "found" if lot.rng.random() > 0.1 else "found_alt")
    for x in range(8):
        for z in range(8):
            lot.put(x, FS + 9, z, "path")
    lot.walls(1, FS + 10, 1, 6, FS + 13, 6, role="plaster", pillar="red", windows=0)
    for x in (2, 5):
        window_pair(lot, x, FS + 11, 1, "north")
        window_pair(lot, x, FS + 11, 6, "south")
    for x in range(8):
        lot.put(x, FS + 10, 0, "kerb_wall") if x % 2 else lot.put(x, FS + 10, 0, "kerb")
        lot.put(x, FS + 10, 7, "kerb_wall") if x % 2 else lot.put(x, FS + 10, 7, "kerb")
    K.hip(lot, 1, FS + 14, 1, 6, 6, overhang=1, top="pearl")
    K.lit(lot, 3, FS + 13, 3, chain=1)
    lot.titik("jaga", 3, FS + 10, 3, "south")
    lot.sisi("T", "T", "T", "T")
    lot.simpan()
    return lot


def build_walls():
    tembok_lurus("lurus")
    tembok_lurus("lurus_jaga", jaga=True)
    tembok_lurus("gerbang_kaki", gate=True)
    tembok_sisi("sisi")
    tembok_sudut("sudut")
    for name in ("lurus", "lurus_jaga", "gerbang_kaki", "sisi", "sudut"):
        pid = "tembok/" + name
        POOLS[pid] = ([tid(pid)], name == "gerbang_kaki")


def wall_lots(distrik):
    """Wall lots of the bottom row: south runs (dz 48), the west side (dx -56) of penginapan_peziarah, the east side
    (dx 48, turned 180) of pasar_kaki, and the two corner towers."""
    out = []
    if distrik == "kaki_gerbang":
        out += [{"dx": -48, "dz": 48, "dy": 0, "pool": "tembok/gerbang_kaki", "putar": 0},
                {"dx": 0, "dz": 48, "dy": 0, "pool": "tembok/lurus_jaga", "putar": 0}]
    elif distrik == "penginapan_peziarah":
        out += [{"dx": -48, "dz": 48, "dy": 0, "pool": "tembok/lurus", "putar": 0},
                {"dx": 0, "dz": 48, "dy": 0, "pool": "tembok/lurus_jaga", "putar": 0},
                {"dx": -56, "dz": -48, "dy": SHIFT_N, "pool": "tembok/sisi", "putar": 0},
                {"dx": -56, "dz": 0, "dy": 0, "pool": "tembok/sisi", "putar": 0},
                {"dx": -56, "dz": 48, "dy": 0, "pool": "tembok/sudut", "putar": 0}]
    elif distrik == "pasar_kaki":
        out += [{"dx": -48, "dz": 48, "dy": 0, "pool": "tembok/lurus_jaga", "putar": 0},
                {"dx": 0, "dz": 48, "dy": 0, "pool": "tembok/lurus", "putar": 0},
                {"dx": 48, "dz": -48, "dy": SHIFT_N, "pool": "tembok/sisi", "putar": 2},
                {"dx": 48, "dz": 0, "dy": 0, "pool": "tembok/sisi", "putar": 2},
                {"dx": 48, "dz": 48, "dy": 0, "pool": "tembok/sudut", "putar": 0}]
    return out


# ===================================================================================================== kaki_gerbang
def gapura_batu(k, cx, z, f):
    """Gerbang Kaki Gunung: a three-door stone gateway (centre 5, sides 3) with a tiled roof, a gilded name board and
    two stone lions; 19 wide."""
    xs = (cx - 9, cx - 5, cx - 3, cx + 3, cx + 5, cx + 9)
    for px in xs:
        k.put(px, f + 1, z, "kerb_chisel")
        for y in range(f + 2, f + 9):
            k.put(px, y, z, "kerb" if y % 3 else "kerb_chisel")
        for dz in (-1, 1):
            k.stairs(px, f + 1, z + dz, "kerb_stairs", "north" if dz > 0 else "south")
    for x in range(cx - 9, cx + 10):
        if x in xs:
            continue
        side = abs(x - cx) > 3
        k.put(x, f + (6 if side else 8), z, "kerb")
        k.stairs(x, f + (5 if side else 7), z, "kerb_stairs", "east" if x < cx else "west", half="top")
    for x in range(cx - 10, cx + 11):
        k.put(x, f + 9, z, "kerb")
        k.put(x, f + 10, z, "minecraft:stripped_dark_oak_log", {"axis": "x"})
    for x in range(cx - 2, cx + 3):
        k.put(x, f + 11, z, "gold" if abs(x - cx) <= 1 else "red")
    K.gable(k, cx - 10, f + 12, z, cx + 10, z, along="x", overhang=1, pearl=True, role="tile_stairs",
            slab_role="tile_slab", full_role="tile")
    for x in (cx - 7, cx + 7):
        K.gable(k, x - 2, f + 7, z, x + 2, z, along="x", overhang=0, pearl=False, role="tile_stairs",
                slab_role="tile_slab", full_role="tile")
    K.lion(k, cx - 11, f, z + 2, "south")
    K.lion(k, cx + 11, f, z + 2, "south")
    K.lit(k, cx - 4, f + 9, z, chain=1, role="lamp")
    K.lit(k, cx + 4, f + 9, z, chain=1, role="lamp")


def stela_seribu(k, x, z, f):
    """The stela "Seribu Anak Tangga" on a tortoise (bixi): stone stairs for the shell and head, a slab tablet."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            k.put(x + dx, f + 1, z + dz, "minecraft:polished_andesite")
    k.stairs(x, f + 1, z + 2, "kerb_stairs", "north")
    for dz in (-1, 0, 1):
        k.stairs(x - 1, f + 2, z + dz, "kerb_stairs", "east")
        k.stairs(x + 1, f + 2, z + dz, "kerb_stairs", "west")
    for y in range(f + 2, f + 7):
        k.put(x, y, z, "minecraft:polished_andesite" if y % 2 else "minecraft:chiseled_stone_bricks")
    k.slab(x, f + 7, z, "kerb_slab")
    k.titik("pandang", x, f + 1, z + 4, "north", {"nama": "stela"})


def pos_jaga(k, x0, z0, f, face="east"):
    """A small guard house (5 x 6) with a tiled roof, a barrel of spears and a guard point in its doorway."""
    k.walls(x0, f + 1, z0, x0 + 4, f + 4, z0 + 5, role="plaster", pillar="red", windows=0)
    for z in range(z0, z0 + 6):
        k.put(x0, f, z, "kerb")
        k.put(x0 + 4, f, z, "kerb")
    dz = z0 + 2
    dx = x0 + 4 if face == "east" else x0
    k.put(dx, f + 1, dz, "minecraft:air")
    k.put(dx, f + 2, dz, "minecraft:air")
    window_pair(k, dx, f + 2, dz + 2, face)
    K.gable(k, x0, f + 5, z0, x0 + 4, z0 + 5, along="z", overhang=1, pearl=False, role="tile_stairs",
            slab_role="tile_slab", full_role="tile")
    k.put(x0 + 2, f + 1, z0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    k.put(x0 + 1, f + 1, z0 + 4, "minecraft:spruce_fence")
    K.lit(k, x0 + 2, f + 4, z0 + 3, chain=1)
    return dx + (1 if face == "east" else -1), dz


def roda_besar(k, cx, cy, z, r=4):
    """The housing of the great drive wheel (the wheel itself is the roda_katrol entity): two timber A-frames carrying
    the axle bearing, the axle beam and a curved guard rail."""
    for x in (cx - 3, cx + 3):
        for y in range(FN + 1, cy + 1):
            K.put_ax(k, x, y, z - 2, "trunk")
            K.put_ax(k, x, y, z + 2, "trunk")
        for zz in range(z - 2, z + 3):
            k.put(x, cy + 1, zz, "minecraft:stripped_spruce_log", {"axis": "z"})
    for x in range(cx - 3, cx + 4):
        k.put(x, cy, z, "minecraft:stripped_dark_oak_log", {"axis": "x"})
    for a in range(0, 360, 20):
        ax = cx + int(round((r + 1) * math.cos(math.radians(a))))
        ay = cy + int(round((r + 1) * math.sin(math.radians(a))))
        if ay > FN + 1:
            k.put(ax, ay, z - 3, "minecraft:spruce_fence")
    k.titik("stasiun_lift", cx, FN + 1, z + 4, "north", {"pasangan": "gantung", "jenis": "roda", "urut": 0})


def stasiun_gantung(k, x0, z0, f, nama, urut, buka="north", lantai=8):
    """A cable-car station 18 x 18: stone plinth, a timber wheel house open toward the rope (`buka` side), a boarding
    platform at f + lantai with the 5-wide cabin gap (x0 + 7 .. x0 + 11, centre x0 + 9.5 = the rope line), a stair up,
    ticket booth, waiting benches. The cabin and the wheels are entities (tema_gunung/lift, tema_gunung/roda anchors).
    Returns the cabin stop (x, y, z): x the rope line block, y the standing level, z the gap's inner row."""
    x1, z1 = x0 + 17, z0 + 17
    sud = buka == "south"
    zo, zi = (z1, z1 - 1) if sud else (z0, z0 + 1)          # outer / inner row of the gap
    zb = z0 if sud else z1                                    # back wall (booth side)
    st = "south" if sud else "north"                          # stairs climb toward the gap side
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, f, z, "kerb" if x in (x0, x1) or z in (z0, z1) else "paving")
    py = f + lantai
    for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, z0 + 8), (x1, z0 + 8)):
        for y in range(f + 1, py + 8):
            K.put_ax(k, px, y, pz, "trunk")
    for y in (py, py + 7):
        for x in range(x0, x1 + 1):
            k.put(x, y, z0, "minecraft:stripped_spruce_log", {"axis": "x"})
            k.put(x, y, z1, "minecraft:stripped_spruce_log", {"axis": "x"})
        for z in range(z0, z1 + 1):
            k.put(x0, y, z, "minecraft:stripped_spruce_log", {"axis": "z"})
            k.put(x1, y, z, "minecraft:stripped_spruce_log", {"axis": "z"})
    # diagonal braces on the two long sides
    for i in range(1, 7):
        k.put(x0, f + i, z0 + i, "wood_fence")
        k.put(x1, f + i, z1 - i, "wood_fence")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            k.put(x, py, z, "wood_alt")
    for x in range(x0 + 7, x0 + 12):
        k.put(x, py, zo, "minecraft:air")
        k.put(x, py, zi, "minecraft:air")
    for x in range(x0 + 1, x1):
        if not (x0 + 6 <= x <= x0 + 12):
            k.put(x, py + 1, zi, "rail")
    for x in (x0 + 6, x0 + 12):
        k.put(x, py + 1, zi, "kerb_wall")
        k.put(x, py + 2, zi, "glow", {"hanging": "false", "waterlogged": "false"})
    # the stair up to the platform (two wide, against the side wall)
    for i in range(lantai - 1):
        zz = (z0 + 2 + i) if sud else (z1 - 2 - i)
        for x in (x1 - 3, x1 - 2):
            k.stairs(x, f + 1 + i, zz, "wood_stairs", st)
            k.put(x, py, zz, "minecraft:air")
    K.roof_over(k, x0, z0, x1, z1, py + 8, kind="hip", overhang=1, top="pearl")
    # ticket booth under the platform at the back wall
    bz0, bz1 = (z0 + 1, z0 + 6) if sud else (z1 - 6, z1 - 1)
    k.walls(x0 + 1, f + 1, bz0, x0 + 6, f + 4, bz1, role="plaster", pillar="red", windows=0)
    K.door(k, x0 + 3, f + 1, zb, OPP[st])
    cz = (bz0 + 1) if not sud else (bz1 - 1)
    K.counter(k, x0 + 2, x0 + 5, cz, f, st)
    window_pair(k, x0 + 6, f + 2, (bz0 + bz1) // 2, "east")
    k.fill(x0 + 2, f + 4, bz0 + 1, x0 + 5, f + 4, bz1 - 1, "wood")
    k.titik("kerja:meja_tulis", x0 + 3, f + 1, (bz0 + bz1) // 2, st)
    sign_board(k, x0 + 3, f + 3, zb, "x")
    for x in range(x0 + 8, x1 - 4, 2):
        bz = (z0 + 2) if sud else (z1 - 2)
        bench_row(k, x, bz, OPP[st], f, n=1)
        k.titik("kursi", x, f + 1, bz, OPP[st])
    # waiting benches on the platform, a rope drum by the gap, lanterns
    for x in (x0 + 2, x0 + 3, x0 + 14, x0 + 15):
        bench_row(k, x, (z0 + 9), "east" if x < x0 + 9 else "west", py, n=1)
    K.barrel(k, x0 + 5, py + 1, zi, "east")
    K.barrel(k, x0 + 13, py + 1, zi, "west")
    K.lit(k, x0 + 9, py + 7, z0 + 9, chain=2)
    K.lit(k, x0 + 4, py + 7, z0 + 4, chain=2)
    K.lit(k, x0 + 13, py + 7, z1 - 4, chain=2)
    k.titik("stasiun_lift", x0 + 9, py + 1, zi, buka, {"pasangan": "gantung", "jenis": "gantung", "urut": urut,
                                                        "nama": nama})
    k.titik("jaga", x0 + 5, py + 1, zi + (-1 if sud else 1), buka)
    k.titik("obrol", x0 + 3, py + 1, z0 + 9, "east")
    k.titik("obrol", x0 + 15, py + 1, z0 + 9, "west")
    return x0 + 9, py + 1, zi


def rumah_kecil_kuli(k, x0, z0):
    """A porter family hut (4 x 5) against the retaining wall: plaster on spruce, a small gable, one bed."""
    k.walls(x0, FN + 1, z0, x0 + 3, FN + 3, z0 + 4, role="plaster", pillar="minecraft:spruce_log", windows=0)
    K.door(k, x0 + 1, FN + 1, z0 + 4, "south")
    K.gable(k, x0, FN + 4, z0, x0 + 3, z0 + 4, along="z", overhang=0, pearl=False)
    K.bed(k, x0 + 2, FN + 1, z0 + 2, "north")
    k.titik("kasur", x0 + 2, FN + 1, z0 + 2, "south")


def alun_pohon(k, cx, cz, f):
    """The old giant oak of the Alun Kaki on a stone ring seat: thick trunk with buttress roots, six branches, a crown
    of azalea and flowering azalea leaves (DESIGN 38.T.6.2 note: no special colours)."""
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            d = math.hypot(dx - 0.5, dz - 0.5)
            if 3.6 < d <= 4.6:
                k.put(cx + dx, f + 1, cz + dz, "kerb")
                k.slab(cx + dx, f + 2, cz + dz, "kerb_slab")
            elif d <= 3.6:
                k.put(cx + dx, f, cz + dz, "minecraft:rooted_dirt" if d > 1.5 else "minecraft:dirt")
                if d > 1.6 and k.rng.random() < 0.5:
                    k.put(cx + dx, f + 1, cz + dz, "minecraft:moss_carpet")
    for dx in (0, 1):
        for dz in (0, 1):
            for y in range(f + 1, f + 12):
                k.put(cx + dx, y, cz + dz, "minecraft:oak_wood" if y < f + 3 else "minecraft:oak_log", {"axis": "y"})
    for (rx, rz) in ((-1, 0), (2, 1), (0, -1), (1, 2)):
        k.put(cx + rx, f + 1, cz + rz, "minecraft:oak_wood", {"axis": "y"})
    for ang in range(0, 360, 60):
        a = math.radians(ang + 15)
        for t in range(1, 6):
            bx = cx + int(round(math.cos(a) * t))
            bz = cz + int(round(math.sin(a) * t))
            k.put(bx, f + 8 + t // 2, bz, "minecraft:oak_log", {"axis": "x" if abs(math.cos(a)) > 0.7 else "z"})
    for dx in range(-7, 9):
        for dz in range(-7, 9):
            for dy in range(9, 16):
                d = math.hypot(dx - 0.5, dz - 0.5) + abs(dy - 12) * 0.9
                if d <= 7.2 and k.rng.random() < 0.88:
                    leaf = "minecraft:flowering_azalea_leaves" if k.rng.random() < 0.3 else "minecraft:azalea_leaves"
                    K.leaf(k, cx + dx, f + dy, cz + dz, leaf)
    k.titik("meditasi", cx - 6, f + 1, cz, "east")
    k.titik("obrol", cx + 7, f + 1, cz, "west")


def build_kaki_gerbang():
    k = kanvas("kaki_gerbang")
    frame(k, "kaki_gerbang", bordes=1)
    # ---------------------------------------------------------------- (0,1) Gerbang Kaki Gunung (SW)
    pav(k, 18, SST[1] + 1, 30, 95, FS, role="path", alt="kerb_alt", ch=0.15)
    gapura_batu(k, 24, 84, FS)
    gx, gz = pos_jaga(k, 4, 74, FS, "east")
    k.titik("jaga", gx, FS + 1, gz, "east")
    jangkar(k, gx, FS + 1, gz, "kota/penjaga", yaw=270.0, protect=8)
    gx2, gz2 = pos_jaga(k, 39, 74, FS, "west")
    k.titik("jaga", gx2, FS + 1, gz2, "west")
    jangkar(k, gx2, FS + 1, gz2, "kota/penjaga", yaw=90.0, protect=8)
    stela_seribu(k, 12, 88, FS)
    for x in (16, 32):
        for z in (72, 78, 90):
            stone_lantern(k, x, FS + 1, z)
    for (px, pz) in ((6, 92), (42, 92), (8, 58), (38, 58)):
        pine_at(k, px, pz, FS)
    rock(k, 3, 62, FS, 2, 3)
    rock(k, 41, 88, FS, 2, 2)
    planter(k, 14, 60, FS)
    planter(k, 34, 60, FS)
    tangga(k, [(24, 94), (24, 86), (24, 76), (24, SST[0] + 2), (36, SST[0] + 2), (48, SST[0] + 2), (48, 56)], 0, FS + 1)
    jangkar(k, 24, FS + 1, 70, MOD + "/kuli", jenis="kaki", protect=6)
    # ---------------------------------------------------------------- (0,0) Stasiun Bawah Kereta Gantung (NW)
    px, py, pz = stasiun_gantung(k, 15, 24, FN, "bawah", 0)
    roda_besar(k, 24, FN + 13, 32)
    jangkar(k, px, py, pz, MOD + "/lift", yaw=180.0, stasiun="bawah", protect=12, peran="PENJAGA_LIFT", tokoh="tie_suo")
    jangkar(k, 24, FN + 13, 32, MOD + "/roda", besar=1, protect=4)
    for y in range(FN + 1, FU + 5):
        k.put(22, y, 4, "kerb")
        k.put(26, y, 4, "kerb")
    for x in range(22, 27):
        k.put(x, FU + 5, 4, "minecraft:stripped_dark_oak_log", {"axis": "x"})
    for (bx, bz) in ((4, 30), (4, 36), (36, 44)):
        bench_row(k, bx, bz, "east", FN, n=2)
    K.notice_board(k, 6, 12, "south", y=FN, w=5)
    k.titik("obrol", 9, FN + 1, 14, "south")
    for x in (3, 41):
        pine_at(k, x, 8, FN)
    tangga(k, [(48, 46), (48, 30), (48, NST[0] + 1), (48, 15)], 7, FN + 1)
    # ---------------------------------------------------------------- (1,0) Pos Kuli (NE)
    x0, z0, x1, z1 = 56, 26, 90, 42
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, FN, z, "kerb" if x in (x0, x1) or z in (z0, z1) else "wood_alt")
    K.hall(k, x0, z0, x1, z1, FN, 6, door_sides=("north",), door_w=5, windows=4, wall_role="plaster",
           pillar_role="minecraft:spruce_log")
    top = K.roof_over(k, x0, z0, x1, z1, FN + 7, kind="gable", along="x", overhang=1, top=None)
    ridge_cap(k, x0 - 1, x1 + 1, (z0 + z1) // 2, top + 1)
    for x in range(x0 + 2, x0 + 14):
        k.put(x, FN + 1, z1 - 1, "minecraft:spruce_fence")
        k.put(x, FN + 2, z1 - 1, "minecraft:spruce_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                              "powered": "false", "waterlogged": "false"})
    K.crates(k, x0 + 16, z0 + 8, x0 + 24, z1 - 2, FN + 1, h=3)
    for x in range(x0 + 2, x0 + 18, 2):
        K.bed(k, x, FN + 1, z0 + 2, "north")
        k.titik("kasur", x, FN + 1, z0 + 2, "south")
    for x in range(x0 + 26, x1 - 1, 2):
        k.put(x, FN + 1, z1 - 1, "minecraft:spruce_fence")
        k.put(x, FN + 2, z1 - 1, "minecraft:hay_block", {"axis": "y"})
        k.titik("kerja:rak", x, FN + 1, z1 - 2, "south")
    K.notice_board(k, 70, z0 - 2, "north", y=FN, w=5)
    jangkar(k, 72, FN + 1, z0 - 3, MOD + "/kuli", jenis="pos", protect=10, peran="KULI_GUNUNG", tokoh="lao_dan")
    k.titik("obrol", 66, FN + 1, z0 - 3, "north")
    k.titik("obrol", 78, FN + 1, z0 - 3, "north")
    for x in (60, 84):
        K.barrel(k, x, FN + 1, z0 - 2, "up")
    K.crates(k, 58, 4, 70, 12, FN + 1, h=2)
    for x in range(74, 92, 5):
        rumah_kecil_kuli(k, x, 4)
    # ---------------------------------------------------------------- (1,1) Alun Kaki (SE)
    pav(k, 54, 70, 93, 93, FS, role="paving", alt="paving_alt", ch=0.12)
    alun_pohon(k, 74, 82, FS)
    for (bx, bz, fc) in ((60, 74, "south"), (86, 74, "south"), (60, 91, "north"), (86, 91, "north")):
        bench_row(k, bx, bz, fc, FS, n=3)
        k.titik("kursi", bx + 1, FS + 1, bz, fc)
    K.notice_board(k, 62, 56, "south", y=FS, w=5)
    jangkar(k, 64, FS + 1, 58, "kota/papan", protect=6)
    jangkar(k, 74, FS + 1, 71, "kota/situs", situs="kota/kota_tema", tema=TEMA, protect=100)
    jangkar(k, 70, FS + 1, 71, "kota_tema/situs", tema=TEMA, protect=100)
    jangkar(k, 78, FS + 1, 71, "kota/alun", protect=24)
    jangkar(k, 74, FS + 1, 93, "kota_tema/tiba", tema=TEMA)
    jangkar(k, 30, FS + 1, 60, "kota_tema/zona", tema=TEMA, distrik="kaki_gerbang", protect=72)
    for x in (56, 92):
        for z in (72, 92):
            stone_lantern(k, x, FS + 1, z)
    for x in range(56, 93, 6):
        k.titik("obrol", x, FS + 1, 80 if x % 12 else 86, "north")
    k.titik("main", 66, FS + 1, 84, "south")
    k.titik("main", 82, FS + 1, 84, "south")
    incense(k, 88, FS + 1, 56)
    potong(k, "kaki_gerbang", {(0, 0): "stasiun_bawah", (1, 0): "pos_kuli", (0, 1): "gerbang", (1, 1): "alun"},
           utuh=("gerbang",))


# ===================================================================================================== pasar_kaki
GOODS_DUPA = ["minecraft:candle", "minecraft:orange_candle", "minecraft:flower_pot"]
GOODS_TASBIH = ["minecraft:decorated_pot", "minecraft:brown_candle", "minecraft:flower_pot"]
GOODS_TONGKAT = ["minecraft:bamboo_block", "minecraft:stripped_bamboo_block", "minecraft:bamboo_block"]
GOODS_PAYUNG = ["minecraft:red_wool", "minecraft:yellow_wool", "minecraft:white_wool"]
GOODS_MAKAN = ["minecraft:dried_kelp_block", "minecraft:hay_block", "minecraft:melon"]
GOODS_ALL = [GOODS_DUPA, GOODS_TASBIH, GOODS_TONGKAT, GOODS_MAKAN, GOODS_DUPA, GOODS_PAYUNG, GOODS_MAKAN]


def kedai_bakpao(k, x0, z0, f):
    """Kedai Bakpao Kukus: an open-front shop 17 x 13 with bamboo steamer towers (smoker, barrel basket, bamboo lid;
    the steam is a client effect over the kuali points), dough tables, flour sacks, three dining tables outside."""
    x1, z1 = x0 + 16, z0 + 12
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, f, z, "kerb" if x in (x0, x1) or z in (z0, z1) else "wood_alt")
    k.walls(x0, f + 1, z0, x1, f + 4, z1, role="plaster", pillar="minecraft:spruce_log", windows=0)
    for x in range(x0 + 2, x1 - 1):     # open front (north) with posts every 4
        for y in (f + 1, f + 2, f + 3):
            if (x - x0) % 4:
                k.put(x, y, z0, "minecraft:air")
            else:
                k.put(x, y, z0, "minecraft:spruce_log", {"axis": "y"})
    K.awning(k, x0 - 1, x1 + 1, z0 - 3, z0 - 1, f + 4, a="cloth", b="cloth_alt")
    for x in (x0 - 1, x1 + 1):
        for y in range(f + 1, f + 4):
            k.put(x, y, z0 - 3, "wood_fence")
    K.beam_band(k, x0, z0, x1, z1, f + 4, role="minecraft:stripped_spruce_log")
    k.fill(x0 + 1, f + 5, z0 + 1, x1 - 1, f + 5, z1 - 1, "wood")
    gable_roof(k, x0, f + 6, z0, x1, z1, along="x", overhang=1)
    for i, x in enumerate(range(x0 + 2, x1 - 1, 3)):
        k.put(x, f + 1, z1 - 1, "minecraft:smoker", {"facing": "north", "lit": "true"})
        K.barrel(k, x, f + 2, z1 - 1, "up")
        k.put(x, f + 3, z1 - 1, "minecraft:stripped_bamboo_block", {"axis": "y"})
        k.titik("kerja:kuali", x, f + 1, z1 - 2, "south")
        if i % 2:
            k.put(x + 1, f + 1, z1 - 1, "minecraft:cauldron")
    for x in range(x0 + 3, x1 - 2, 4):  # dough tables
        for dx in (0, 1):
            k.put(x + dx, f + 1, z0 + 6, "minecraft:spruce_fence")
            k.put(x + dx, f + 2, z0 + 6, "minecraft:smooth_stone_slab", {"type": "bottom", "waterlogged": "false"})
        k.titik("kerja:lesung", x, f + 1, z0 + 7, "north")
    for (x, h) in ((x0 + 1, 2), (x1 - 1, 1)):
        for y in range(f + 1, f + 1 + h):
            k.put(x, y, z0 + 1, "minecraft:white_wool")
    for x in range(x0 + 4, x1 - 2, 4):
        K.lit(k, x, f + 5, z0 + 4, chain=1, role="lamp")
    sign_board(k, (x0 + x1) // 2, f + 4, z0 - 1, "x")
    for i, x in enumerate((x0 + 2, x0 + 8, x0 + 14)):
        K.furnish_dining(k, x, z0 - 6, f, n=3 if i == 1 else 2)


def bank_cabang(k, x0, z0, f):
    """Cabang Rumah Uang: a solid stone-and-plaster hall 26 x 16 on a podium with a tiled roof, a long counter with
    three tellers behind iron bars, a strong room with an iron door, chests and gold, ledgers on the side walls."""
    a = K.aula(k, x0, z0, x0 + 25, z0 + 15, h=6, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("north",),
               door_w=3, tile=True, y=f)
    ruang(k, *a["inner"], a["f"], "kantor", isi=0.4)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    K.counter(k, ix0 + 2, ix1 - 2, iz0 + 5, fl, "north")
    for x in range(ix0 + 2, ix1 - 1):
        if (x - ix0) % 3 == 0:
            k.put(x, fl + 2, iz0 + 5, "minecraft:iron_bars")
    for x in (ix0 + 4, (ix0 + ix1) // 2, ix1 - 4):
        k.titik("kerja:timbangan", x, fl + 1, iz0 + 6, "north")
    for x in range(ix0 + 1, ix1):
        for y in (fl + 1, fl + 2, fl + 3):
            k.put(x, y, iz1 - 4, "minecraft:iron_bars")
    cx = (ix0 + ix1) // 2
    k.put(cx, fl + 1, iz1 - 4, "minecraft:iron_door", {"facing": "north", "half": "lower", "hinge": "left", "open": "false",
                                                      "powered": "false"})
    k.put(cx, fl + 2, iz1 - 4, "minecraft:iron_door", {"facing": "north", "half": "upper", "hinge": "left", "open": "false",
                                                      "powered": "false"})
    for x in range(ix0 + 1, ix1, 2):
        K.chest(k, x, fl + 1, iz1, facing="north")
        K.barrel(k, x + 1, fl + 1, iz1, "north")
        if x % 6 == 1:
            k.put(x, fl + 1, iz1 - 2, "minecraft:gold_block")
    K.bookshelf_wall(k, ix0, iz0 + 1, ix0, iz0 + 3, fl + 1, fl + 3, face="east")
    K.bookshelf_wall(k, ix1, iz0 + 1, ix1, iz0 + 3, fl + 1, fl + 3, face="west")
    K.rug(k, ix0 + 3, iz0 + 1, ix1 - 3, iz0 + 3, fl + 1)
    for x in (ix0 + 2, ix1 - 2):
        k.titik("jaga", x, fl + 1, iz0 + 1, "north")
    return a


def papan_gilda(k, x0, z0, f):
    """Papan Gilda: three notice boards under one tiled lean-to roof, the clerk's booth beside, benches."""
    for i in range(3):
        K.notice_board(k, x0 + i * 6, z0, "north", y=f, w=5)
    for x in range(x0 - 1, x0 + 18):
        for z in (z0, z0 + 1):
            k.slab(x, f + 6, z, "tile_slab", "bottom")
    pos_jaga(k, x0 + 19, z0 - 3, f, face="west")
    for x in (x0 + 2, x0 + 8, x0 + 14):
        bench_row(k, x, z0 - 4, "south", f, n=2)


def kedai_teh_kabut(k, x0, z0, f):
    """Kedai Teh Kabut: a two-storey teahouse 22 x 15 on a low podium, tea counter with kettle jars, round tables below,
    an upper floor of cushions around low tea tables looking over the valley, potted bamboo in every corner."""
    x1, z1 = x0 + 21, z0 + 14
    a = K.aula(k, x0, z0, x1, z1, h=5, podium_h=1, teras=1, roof="hip", tiers=2, door_sides=("north",), door_w=3, y=f)
    ruang(k, *a["inner"], a["f"], "teh", isi=0.4)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    K.counter(k, ix0 + 1, ix0 + 7, iz1 - 2, fl, "north")
    for x in (ix0 + 1, ix0 + 4, ix0 + 7):
        k.put(x, fl + 2, iz1 - 2, "minecraft:decorated_pot", {"cracked": "false", "facing": "north", "waterlogged": "false"})
    k.titik("kerja:teko", ix0 + 4, fl + 1, iz1 - 1, "north")
    for (cx, cz) in ((ix0 + 11, iz0 + 2), (ix0 + 15, iz0 + 2), (ix0 + 11, iz0 + 7), (ix0 + 15, iz0 + 7)):
        K.furnish_dining(k, cx, cz, fl, n=4)
    for x in (ix0, ix1):
        for z in (iz0, iz1):
            K.pot(k, x, fl + 1, z, "minecraft:potted_bamboo")
    up = fl + 5
    K.upper_floor(k, ix0, iz0, ix1, iz1, up, hole=(ix1 - 1, iz1 - 1, ix1 - 1, iz1 - 1))
    K.ladder(k, ix1 - 1, fl + 1, up, iz1 - 1, "north")
    for (cx, cz) in ((ix0 + 3, iz0 + 2), (ix0 + 8, iz0 + 2), (ix0 + 13, iz0 + 2), (ix0 + 3, iz0 + 7), (ix0 + 8, iz0 + 7)):
        K.low_table(k, cx, up + 1, cz)
        for d, (dx, dz) in FACE.items():
            k.put(cx + dx, up + 1, cz + dz, "rug")
            k.titik("kursi_lantai", cx + dx, up + 1, cz + dz, OPP[d])
    return a


def build_pasar_kaki():
    k = kanvas("pasar_kaki")
    frame(k, "pasar_kaki")
    # ---------------------------------------------------------------- (0,0)+(1,0) Pasar Peziarah: 14 stalls along the
    # north terrace street, merchant houses behind, the bakpao shop and the pilgrim goods store
    n = 1
    for i, x in enumerate((6, 14, 22, 30, 62, 70, 78, 86)):          # north side, facing the street (south)
        kios(k, x, 15, "south", FN, GOODS_ALL[i % len(GOODS_ALL)], n)
        n += 1
    for i, x in enumerate((4, 12, 20, 28, 58, 66)):                  # south side, facing the street (north)
        kios(k, x, 24, "north", FN, GOODS_ALL[(i + 3) % len(GOODS_ALL)], n)
        n += 1
    # Musim Ziarah: four extra pitches (mats and crates, empty most of the year)
    for i, (x, z) in enumerate(((36, 26), (40, 30), (56, 14), (60, 30))):
        K.rug(k, x - 1, z - 1, x + 1, z + 1, FN + 1, role="minecraft:yellow_carpet", border="minecraft:brown_carpet")
        K.crates(k, x + 1, z + 1, x + 1, z + 1, FN + 1, h=1)
        k.titik("lapak", x, FN + 1, z, "north", {"no": 15 + i, "musim": "ziarah"})
    for i, x in enumerate((2, 13, 24, 56, 67, 78)):
        rumah_gunung(k, x, 3, 9, 8, FN, face="south", seed=i, storeys=2, beds=2)
    kedai_bakpao(k, 74, 30, FN)
    jangkar(k, 82, FN + 1, 34, MOD + "/peran", yaw=0.0, peran="KOKI", protect=6)
    # Toko Perlengkapan Peziarah: incense rolling and bamboo staff workshop
    x0, z0 = 6, 30
    rumah_gunung(k, x0, z0, 15, 12, FN, face="north", seed=7, storeys=1, beds=1, kitchen=False, sign=True)
    for x in range(x0 + 2, x0 + 13, 3):
        k.put(x, FN + 1, z0 + 9, "minecraft:spruce_fence")
        k.put(x, FN + 2, z0 + 9, "minecraft:bamboo_block", {"axis": "y"})
        k.titik("kerja:dupa", x, FN + 1, z0 + 8, "south")
    herb_rack(k, x0 + 2, z0 + 4, FN, along="x", n=6, tiers=2)
    jangkar(k, x0 + 7, FN + 1, z0 + 6, MOD + "/peran", yaw=180.0, peran="PEDAGANG_KELILING", protect=6)
    K.crates(k, 24, 34, 30, 42, FN + 1, h=2)
    for x in (34, 58, 92):
        pine_at(k, x, 44, FN)
    # ---------------------------------------------------------------- (0,1) Cabang Rumah Uang + Papan Gilda, pill shop
    bank_cabang(k, 6, 73, FS)
    jangkar(k, 19, FS + 2, 80, "kotabesar/layanan", yaw=180.0, layanan="bank_cabang", peran="BANKIR", protect=8)
    papan_gilda(k, 6, 60, FS)
    jangkar(k, 14, FS + 1, 57, "kotabesar/layanan", yaw=180.0, layanan="papan_gilda", peran="PETUGAS_GILDA", protect=6)
    rumah_gunung(k, 30, 51, 12, 9, FS, face="south", seed=21, storeys=1, beds=0, kitchen=False, sign=True)
    K.bookshelf_wall(k, 31, 52, 40, 52, FS + 1, FS + 2, face="south")
    for x in (33, 36, 39):
        k.put(x, FS + 1, 55, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false",
                                                         "has_bottle_2": "true"})
    jangkar(k, 36, FS + 1, 57, "kota/pil", yaw=180.0, protect=6)
    planter(k, 2, 92, FS)
    planter(k, 40, 92, FS)
    # ---------------------------------------------------------------- (1,1) Kedai Teh Kabut and its garden
    a = kedai_teh_kabut(k, 62, 73, FS)
    ix0, iz0, _ix1, _iz1 = a["inner"]
    jangkar(k, ix0 + 4, a["f"] + 1, iz0 + 9, "kota/teh", yaw=180.0, protect=8)
    jangkar(k, ix0 + 11, a["f"] + 1, iz0 + 4, "kota/kursi_pengembara", yaw=90.0)
    jangkar(k, ix0 + 15, a["f"] + 1, iz0 + 9, "kota/kursi_pengembara", yaw=270.0)
    K.garden(k, 56, 52, 92, 62, y=FS, trees=("pine", "cherry"), pond_too=True)
    for x in range(57, 92, 7):
        stone_lantern(k, x, FS + 1, 70)
    jangkar(k, 74, FS + 1, 63, "kota_tema/zona", tema=TEMA, distrik="pasar_kaki", protect=72)
    tangga(k, [(48, 56, FS + 1), (48, 40), (48, 24), (48, 15)], 30, FN + 1)
    potong(k, "pasar_kaki", {(0, 0): "pasar_barat", (1, 0): "pasar_timur", (0, 1): "rumah_uang", (1, 1): "kedai_teh"})


# ===================================================================================================== penginapan_peziarah
def patung_gajah(k, cx, cz, f, tinggi=6, menghadap="south"):
    """Samantabhadra (Puxian) on the white six-tusked elephant: a white-concrete elephant (body, four legs, head, trunk,
    tusks of end rods), a lotus seat (cherry stairs for the petals) and the seated figure of quartz and calcite with a
    golden crown; tinggi >= 10 doubles the scale (the Aula Emas statue). Returns the top y."""
    s = 2 if tinggi >= 10 else 1
    bl, bw, bh = 4 * s, 2 * s, 2 * s
    leg = 2 * s
    for lx in (-bw + s - 1, bw - s + 1):
        for lz in (-bl + s, bl - s):
            for y in range(f + 1, f + 1 + leg):
                for dx in range(s):
                    for dz in range(s):
                        k.put(cx + lx + dx - s // 2, y, cz + lz + dz - s // 2, "minecraft:white_concrete")
    yb = f + 1 + leg
    for dx in range(-bw, bw + 1):
        for dz in range(-bl, bl + 1):
            for dy in range(bh + s):
                r = (dx / (bw + 0.6)) ** 2 + (dz / (bl + 0.6)) ** 2 + ((dy - bh / 2.0) / (bh + 0.9)) ** 2
                if r <= 1.0:
                    k.put(cx + dx, yb + dy, cz + dz, "minecraft:white_concrete" if r < 0.75 else "minecraft:calcite")
    sgn = 1 if menghadap == "south" else -1
    hz = cz + sgn * (bl + 1)
    for dx in range(-s, s + 1):
        for dy in range(bh + s):
            for dz in range(s + 1):
                k.put(cx + dx, yb + dy, hz + sgn * dz, "minecraft:white_concrete")
    for dy in range(-leg, bh):
        k.put(cx, yb + dy, hz + sgn * (s + 1), "minecraft:white_concrete" if dy >= -1 else "minecraft:calcite")
    for tx in ((-s, 0, s) if s > 1 else (-1, 1)):
        for i in range(3 if s > 1 else 2):
            k.put(cx + tx, yb + (1 if tx == 0 else 0), hz + sgn * (s + 2 + i), "minecraft:end_rod",
                  {"facing": "south" if sgn > 0 else "north"})
    top = yb + bh + s
    for dx in range(-bw + 1, bw):
        for dz in range(-2 * s, 2 * s + 1):
            k.put(cx + dx, top - 1, cz + dz, "minecraft:red_wool" if abs(dz) < 2 * s and abs(dx) < bw - 1
                  else "minecraft:yellow_wool")
    for d, (dx, dz) in FACE.items():
        for w in range(-s, s + 1):
            px = cx + dx * (s + 1) + (w if dx == 0 else 0)
            pz = cz + dz * (s + 1) + (w if dz == 0 else 0)
            k.put(px, top - 1, pz, "minecraft:pink_concrete")
            k.stairs(px, top, pz, "minecraft:cherry_stairs", OPP[d])
    fh = tinggi - 1 - s
    for dy in range(fh):
        w = (s if dy < fh // 2 else s - 1) if s > 1 else (1 if dy == 0 else 0)
        for dx in range(-w, w + 1):
            for dz in range(-w, w + 1):
                blk = "minecraft:yellow_terracotta" if dy < s else ("minecraft:quartz_block" if dy < fh - s
                                                                    else "minecraft:calcite")
                k.put(cx + dx, top + dy, cz + dz, blk)
    k.put(cx, top + fh, cz, "minecraft:gold_block")
    if s > 1:
        for d, (dx, dz) in FACE.items():
            k.put(cx + dx, top + fh, cz + dz, "minecraft:raw_gold_block")
            k.put(cx + 2 * dx, top + fh - 3, cz + 2 * dz, "minecraft:calcite")     # hands and knees
        k.put(cx, top + fh + 1, cz, "minecraft:gold_block")
        return top + fh + 2
    return top + fh + 1


def kuil_kaki(k, x0, z0, f):
    """Kuil Kaki Gunung (Baoguo inspired): the Samantabhadra hall 26 x 18 facing the north terrace street, the statue
    inside on its altar platform, offering tables with candles, prayer cushions, drum and bell, huabiao pillars."""
    a = K.aula(k, x0, z0, x0 + 25, z0 + 17, h=7, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("north",),
               door_w=5, y=f)
    ruang(k, *a["inner"], a["f"], "kuil", isi=0.4, rug=False)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    cx = (ix0 + ix1) // 2
    for x in range(cx - 3, cx + 4):
        for z in range(iz1 - 4, iz1 + 1):
            k.put(x, fl, z, "minecraft:polished_andesite")
    patung_gajah(k, cx, iz1 - 2, fl, tinggi=5, menghadap="north")
    for x in range(cx - 3, cx + 4):
        if x % 2:
            K.table(k, x, fl + 1, iz1 - 8, "minecraft:dark_oak_pressure_plate")
        else:
            K.candle(k, x, fl + 1, iz1 - 8, n=3, color="yellow")
    incense(k, cx, fl + 1, iz1 - 10)
    for x in range(ix0 + 2, ix1 - 1, 2):
        for z in range(iz0 + 1, iz1 - 11, 2):
            if abs(x - cx) > 1:
                k.put(x, fl + 1, z, "minecraft:yellow_carpet")
                k.titik("kursi_lantai", x, fl + 1, z, "south")
    K.drum(k, ix0 + 1, fl + 1, iz0 + 1, "east")
    k.put(ix1 - 1, fl + 4, iz0 + 1, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    k.titik("kerja:dupa", cx - 2, fl + 1, iz1 - 9, "south")
    k.titik("kerja:dupa", cx + 2, fl + 1, iz1 - 9, "south")
    return a


def build_penginapan_peziarah():
    k = kanvas("penginapan_peziarah")
    frame(k, "penginapan_peziarah")
    # ---------------------------------------------------------------- (0,0)+(1,0) Kuil Kaki Gunung
    a = kuil_kaki(k, 9, 24, FN)
    jangkar(k, 22, a["f"] + 1, 30, MOD + "/peran", yaw=0.0, peran="BIKSU_GUNUNG", protect=8)
    pav(k, 4, 3, 40, 16, FN, role="paving", alt="paving_alt", ch=0.15)
    incense(k, 22, FN + 1, 10, big=True)
    for x in (12, 32):
        K.huabiao(k, x, FN + 1, 13, height=7)
    for x in (2, 41):
        pine_at(k, x, 14, FN)
    for (px, nama) in ((4, "lonceng"), (34, "genderang")):    # bell and drum pavilions (zhonglou / gulou)
        for (cx_, cz_) in ((px, 4), (px + 4, 4), (px, 8), (px + 4, 8)):
            k.put(cx_, FN + 1, cz_, "kerb_chisel")
            for y in range(FN + 2, FN + 7):
                k.put(cx_, y, cz_, "red")
        K.beam_band(k, px, 4, px + 4, 8, FN + 7)
        K.hip(k, px, FN + 8, 4, px + 4, 8, overhang=1, top="pearl")
        if nama == "lonceng":
            k.put(px + 2, FN + 6, 6, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
        else:
            K.drum(k, px + 2, FN + 2, 6, "south")
    # Ruang Daftar Ziarah: ledger counter, pilgrim staffs, the start anchor of the pilgrimage
    rumah_gunung(k, 56, 26, 17, 13, FN, face="north", seed=31, storeys=1, beds=0, kitchen=False, sign=True)
    K.counter(k, 59, 69, 31, FN, "north")
    K.bookshelf_wall(k, 57, 37, 71, 37, FN + 1, FN + 2, face="north")
    for x in range(58, 72, 2):
        k.put(x, FN + 1, 35, "minecraft:bamboo", {"age": "1", "leaves": "small", "stage": "0"})
    jangkar(k, 64, FN + 1, 33, MOD + "/ziarah", yaw=0.0, peran="BIKSU_GUNUNG", protect=10)
    k.titik("kerja:meja_tulis", 64, FN + 1, 32, "north")
    for i, x in enumerate((56, 68, 80)):     # monks' quarters
        rumah_gunung(k, x, 3, 10, 9, FN, face="south", seed=40 + i, storeys=1, beds=3, kitchen=i == 1)
    for x in range(56, 94):                   # prayer flag line
        if x % 2:
            k.put(x, FN + 6, 15, "minecraft:yellow_wool" if x % 4 == 1 else "minecraft:red_wool")
    for x in (56, 93):
        for y in range(FN + 1, FN + 6):
            k.put(x, y, 15, "wood_fence")
    for x in (84, 92):
        pine_at(k, x, 30, FN)
        pine_at(k, x, 42, FN)
    K.well(k, 80, 34, FN)
    # ---------------------------------------------------------------- (0,1) Penginapan Pondok Pinus
    a = K.aula(k, 3, 71, 40, 93, h=5, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("north",), door_w=3, y=FS)
    ruang(k, *a["inner"], a["f"], "inap", isi=0.5)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    for i in range(8):                        # shared hall: 16 beds, screens between pairs
        x = ix0 + 1 + i * 2
        K.bed(k, x, fl + 1, iz1 - 1, "south")
        k.titik("kasur", x, fl + 1, iz1 - 1, "north")
        K.bed(k, x, fl + 1, iz1 - 5, "north")
        k.titik("kasur", x, fl + 1, iz1 - 5, "south")
    for r in range(4):                        # four private rooms along the east side
        rz0 = iz0 + r * 5
        k.fill(ix1 - 10, fl + 1, rz0, ix1 - 10, fl + 4, min(rz0 + 4, iz1), "plaster")
        if r < 3:
            k.fill(ix1 - 9, fl + 1, rz0 + 4, ix1, fl + 4, rz0 + 4, "plaster")
        K.door(k, ix1 - 10, fl + 1, rz0 + 2, "west")
        K.bed(k, ix1 - 2, fl + 1, rz0 + 1, "east")
        k.titik("kasur", ix1 - 2, fl + 1, rz0 + 1, "west")
        K.chest(k, ix1 - 1, fl + 1, rz0 + 3, facing="west")
        K.lit(k, ix1 - 5, fl + 4, rz0 + 2, chain=1)
    K.counter(k, ix0 + 1, ix0 + 6, iz0 + 3, fl, "north")
    k.titik("kerja:meja_tulis", ix0 + 3, fl + 1, iz0 + 4, "north")
    jangkar(k, ix0 + 3, fl + 1, iz0 + 5, "kotabesar/layanan", yaw=180.0, layanan="penginapan", peran="PEMILIK_PENGINAPAN",
            protect=8)
    for x in (ix0 + 9, ix0 + 13):
        K.furnish_dining(k, x, iz0 + 3, fl, n=4)
    for (px, pz) in ((5, 53), (15, 58), (27, 54), (37, 59)):
        pine_at(k, px, pz, FS, 10)
    for x in range(8, 36, 3):
        k.put(x, FS + 1, 62, "minecraft:spruce_fence")
        if x % 2:
            k.put(x, FS + 2, 62, "minecraft:bamboo_block", {"axis": "y"})
    # ---------------------------------------------------------------- (1,1) Rumah Makan Vegetarian + kitchen garden
    a = K.aula(k, 56, 72, 92, 92, h=5, podium_h=1, teras=2, roof="hip", tiers=2, door_sides=("north",), door_w=3, y=FS)
    ruang(k, *a["inner"], a["f"], "kedai", isi=0.4, rug=False)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    K.furnish_kitchen(k, ix0 + 1, iz1 - 3, ix0 + 10, fl, iz1, "north")
    for x in range(ix0 + 1, ix0 + 11):
        k.put(x, fl + 1, iz1 - 3, "minecraft:spruce_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                              "powered": "false", "waterlogged": "false"})
    for (cx, cz) in ((ix0 + 14, iz0 + 2), (ix0 + 20, iz0 + 2), (ix0 + 26, iz0 + 2), (ix0 + 14, iz0 + 8),
                     (ix0 + 20, iz0 + 8), (ix0 + 26, iz0 + 8)):
        K.furnish_dining(k, cx, cz, fl, n=4)
    jangkar(k, ix0 + 5, fl + 1, iz1 - 4, "kotabesar/layanan", yaw=0.0, layanan="rumah_makan", peran="KOKI", protect=8)
    for x in range(56, 94):
        for z in range(52, 63):
            if x in (56, 93) or z in (52, 62):
                k.slab(x, FS + 1, z, "kerb_slab", "bottom")
            elif (x - 56) % 4 == 0:
                k.put(x, FS, z, "minecraft:water", {"level": "0"})
            else:
                k.put(x, FS, z, "minecraft:farmland", {"moisture": "7"})
                crop = ("minecraft:beetroots", "minecraft:carrots", "minecraft:potatoes")[(x // 4) % 3]
                k.put(x, FS + 1, z, crop, {"age": "3" if crop == "minecraft:beetroots" else "7"})
    k.titik("kerja:cangkul", 66, FS + 1, 63, "north")
    k.titik("kerja:cangkul", 80, FS + 1, 63, "north")
    k.put(74, FS + 2, 57, "minecraft:spruce_fence")
    k.put(74, FS + 3, 57, "minecraft:carved_pumpkin", {"facing": "south"})
    jangkar(k, 30, FS + 1, 66, "kota_tema/zona", tema=TEMA, distrik="penginapan_peziarah", protect=72)
    tangga(k, [(48, 56, FS + 1), (48, 40), (48, 24)], 40, FN + 1)
    potong(k, "penginapan_peziarah", {(0, 0): "kuil_barat", (1, 0): "daftar_ziarah", (0, 1): "pondok_pinus",
                                      (1, 1): "rumah_makan"}, utuh=("kuil_barat",))


# ===================================================================================================== teras_hunian
def rumah_panggung(k, x0, z0, w, d, seed, storeys=2, face="north", menggantung=4):
    """A Chongqing stilt house on the north terrace's south edge: the back part stands on the terrace (floor FN), the
    front `menggantung` rows hang over the lower terrace on spruce stilts braced with fences (rumah panggung). The
    house's own floor closes the balustrade gap; under the overhang the family keeps firewood, jars and a bench."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    for x in range(x0, x1 + 1):
        k.put(x, FN + 1, 47, "minecraft:air")
    door = rumah_gunung(k, x0, z0, w, d, FN, face=face, seed=seed, storeys=storeys, beds=2 + storeys, balcony=False)
    zs = z1 - menggantung + 1
    for (sx, sz) in ((x0, z1), (x1, z1), ((x0 + x1) // 2, z1), (x0, zs), (x1, zs)):
        if sz < 50:
            continue
        for y in range(FS + 1, FN):
            K.put_ax(k, sx, y, sz, "stilt")
        k.put(sx, FS, sz, "kerb_chisel")
    for x in range(x0 + 1, x1):          # fence braces under the hanging floor
        if (x - x0) % 2:
            k.put(x, FN - 1, z1, "wood_fence")
    for z in range(max(50, zs), z1):
        k.put(x0, FN - 1, z, "wood_fence")
        k.put(x1, FN - 1, z, "wood_fence")
    # life under the overhang (south terrace)
    for x in range(x0 + 1, x1, 3):
        r = k.rng.random()
        if r < 0.35:
            K.barrel(k, x, FS + 1, 50, "up")
        elif r < 0.65:
            k.put(x, FS + 1, 50, "minecraft:stripped_spruce_log", {"axis": "x"})
            k.put(x, FS + 2, 50, "minecraft:stripped_spruce_log", {"axis": "x"})
        else:
            k.put(x, FS + 1, 50, "minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})
    bench_row(k, x0 + 2, z1 - 1, "south", FS, n=2)
    K.lit(k, (x0 + x1) // 2, FN, z1 - 1, chain=1)
    # a gallery along the valley side of the upper storey
    if storeys > 1:
        yb = FN + 5
        for x in range(x0, x1 + 1):
            k.slab(x, yb, z1 + 1, "wood_slab", "top")
            k.put(x, yb + 1, z1 + 1, "rail")
        for x in (x0, x1):
            for y in range(FS + 1, yb):
                K.put_ax(k, x, y, z1 + 1, "stilt")
        k.put((x0 + x1) // 2, yb + 1, z1, "minecraft:air")
        k.put((x0 + x1) // 2, yb + 2, z1, "minecraft:air")
        for x in range(x0 + 1, x1, 2):
            k.put(x, yb + 4, z1 + 1, "minecraft:red_wool" if (x // 2) % 2 else "minecraft:white_wool") \
                if k.rng.random() < 0.5 else None
    return door


def sumur_bambu(k, x, z, f, wall_z, wall_top):
    """A bamboo spring: a bamboo pipe down the retaining wall from a crack in the terrace above, a spout over a stone
    basin of water, ladles on the rim (water sits still in the template: no flowing source)."""
    for y in range(f + 3, wall_top):
        k.put(x, y, wall_z, "minecraft:stripped_bamboo_block", {"axis": "y"})
    for dz in range(1, 3):
        k.put(x, f + 3, wall_z + dz, "minecraft:bamboo_block", {"axis": "z"})
    for dx in (-1, 0, 1):
        for dz in (2, 3, 4):
            if dx == 0 and dz == 3:
                k.put(x + dx, f + 1, wall_z + dz, "minecraft:water", {"level": "0"})
                k.put(x + dx, f, wall_z + dz, "minecraft:water", {"level": "0"})
            else:
                k.put(x + dx, f + 1, wall_z + dz, "kerb" if dx == 0 or dz == 3 else "kerb_wall")
    k.put(x + 2, f + 1, wall_z + 3, "minecraft:cauldron")
    k.titik("obrol", x - 2, f + 1, wall_z + 4, "east")
    k.titik("obrol", x + 2, f + 1, wall_z + 5, "west")


def bengkel_kayu(k, x0, z0, f):
    """The terrace carpenters' workshop (kotabesar/tukang): an open timber shed, saw horse, log pile, workbenches,
    grindstone, tool racks."""
    x1, z1 = x0 + 15, z0 + 10
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, f, z, "kerb" if x in (x0, x1) or z in (z0, z1) else "minecraft:coarse_dirt")
    for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0 + 7, z0), (x0 + 7, z1)):
        for y in range(f + 1, f + 5):
            K.put_ax(k, px, y, pz, "trunk")
    k.walls(x0, f + 1, z0, x1, f + 3, z0, role="wood_alt", pillar="minecraft:spruce_log", windows=0)
    gable_roof(k, x0, f + 5, z0, x1, z1, along="x", overhang=1)
    for x in range(x0 + 1, x0 + 7):
        k.put(x, f + 1, z0 + 1, "minecraft:crafting_table" if x % 3 == 0 else "minecraft:spruce_planks")
    k.put(x0 + 8, f + 1, z0 + 1, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    k.put(x0 + 9, f + 1, z0 + 1, "minecraft:smithing_table")
    for x in range(x0 + 10, x1):
        k.put(x, f + 1, z0 + 1, "minecraft:spruce_log", {"axis": "x"})
        k.put(x, f + 2, z0 + 1, "minecraft:spruce_log", {"axis": "x"})
        if x % 2:
            k.put(x, f + 3, z0 + 1, "minecraft:spruce_log", {"axis": "x"})
    for x in (x0 + 3, x0 + 4):
        k.put(x, f + 1, z0 + 5, "minecraft:spruce_fence")
        k.put(x, f + 2, z0 + 5, "minecraft:stripped_spruce_log", {"axis": "x"})
    k.titik("kerja:gergaji", x0 + 3, f + 1, z0 + 6, "north")
    k.titik("kerja:pahat", x0 + 6, f + 1, z0 + 2, "north")
    k.titik("kerja:palu", x0 + 9, f + 1, z0 + 2, "north")
    K.crates(k, x1 - 3, z1 - 3, x1 - 1, z1 - 1, f + 1, h=2)
    K.lit(k, x0 + 4, f + 5, z0 + 5, chain=1)
    K.lit(k, x0 + 11, f + 5, z0 + 5, chain=1)


def kebun_teras_kecil(k, x0, z0, x1, z1, f):
    """A family vegetable terrace with a mossy kerb, crops and a pumpkin."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                k.put(x, f + 1, z, "minecraft:mossy_cobblestone_wall" if (x + z) % 5 == 0 else "minecraft:mossy_cobblestone")
            else:
                k.put(x, f + 1, z, "minecraft:farmland", {"moisture": "7"})
                c = k.rng.choice(("minecraft:carrots", "minecraft:potatoes", "minecraft:wheat"))
                k.put(x, f + 2, z, c, {"age": str(k.rng.randint(4, 7))})
    k.put((x0 + x1) // 2, f + 1, (z0 + z1) // 2, "minecraft:water", {"level": "0"})
    k.put((x0 + x1) // 2, f + 2, (z0 + z1) // 2, "minecraft:lily_pad")


def build_teras_hunian():
    for var in ("a", "b"):
        _teras_hunian(var)


def _teras_hunian(var):
    k = kanvas("teras_hunian", seed_extra=0 if var == "a" else 977)
    frame(k, "teras_hunian")
    alt = var == "b"
    # ---------------------------------------------------------------- north lots: band A cottages against the upper
    # retaining wall, band B stilt houses hanging over the lower terrace (Chongqing look)
    xs_a = (2, 13, 24, 34) if not alt else (3, 16, 29)
    for i, x in enumerate(xs_a):
        w = 9 if not alt else 11
        if x + w > 43:
            w = 43 - x
        rumah_gunung(k, x, 3, w, 9, FN, face="south", seed=100 + i + (50 if alt else 0), storeys=1 + (i % 2), beds=2)
    for i, x in enumerate((55, 66, 77, 87) if not alt else (55, 68, 81)):
        w = 9 if not alt else 11
        if x + w > 95:
            w = 95 - x
        rumah_gunung(k, x, 3, w, 9, FN, face="south", seed=110 + i + (50 if alt else 0), storeys=2 - (i % 2), beds=2)
    for i, (x, w) in enumerate(((2, 12), (17, 11), (31, 12), (56, 11), (70, 12), (85, 10)) if not alt
                               else ((3, 15), (22, 13), (58, 13), (75, 15))):
        rumah_panggung(k, x, 40, w, 14, seed=120 + i + (50 if alt else 0), storeys=2 if (i + alt) % 3 else 1,
                       face="north", menggantung=4)
    # small yards between the street and the stilt houses
    for x in range(2, 94, 7):
        if COR0 - 1 <= x <= COR1 + 1:
            continue
        K.pot(k, x, FN + 1, 25)
        if k.rng.random() < 0.5:
            k.put(x + 1, FN + 1, 25, "minecraft:composter", {"level": str(k.rng.randint(1, 7))})
    for x in (8, 26, 64, 88) if not alt else (12, 40, 68, 90):
        K.well(k, x, 31, FN) if x in (26, 68) else pine_at(k, x, 31, FN, 8)
    # ---------------------------------------------------------------- south lots: band C under the stilts (stores,
    # the bamboo spring), band D houses, gardens and the carpenters' workshop
    sumur_bambu(k, 40 if not alt else 6, 50, FS, 49, FN)
    sumur_bambu(k, 92 if not alt else 56, 50, FS, 49, FN)
    for i, x in enumerate((3, 14, 25) if not alt else (4, 17, 30)):
        rumah_gunung(k, x, 72, 9 if not alt else 11, 10, FS, face="north", seed=140 + i + (50 if alt else 0),
                     storeys=2 if i != 1 else 1, beds=3)
    kebun_teras_kecil(k, 3, 84, 13 if not alt else 18, 93, FS)
    kebun_teras_kecil(k, 26 if not alt else 24, 85, 40, 93, FS)
    bengkel_kayu(k, 56 if not alt else 74, 72, FS)
    jangkar(k, 63 if not alt else 81, FS + 1, 78, "kotabesar/tukang", yaw=0.0, kampung="teras")
    for i, x in enumerate((76, 86) if not alt else (55, 64)):
        rumah_gunung(k, x, 72, 9, 10, FS, face="north", seed=160 + i + (50 if alt else 0), storeys=1, beds=2)
    kebun_teras_kecil(k, 56, 86, 72 if not alt else 62, 93, FS)
    for (px, pz) in ((80, 89), (90, 89)) if not alt else ((70, 90), (88, 88)):
        pine_at(k, px, pz, FS, 9)
    for x in (10, 30, 62, 84):
        k.titik("main", x, FS + 1, 70, "north")
    tangga(k, [(48, 94), (48, 70), (48, 56), (48, 40, FN + 1), (48, 24, FN + 1), (48, 7, FN + 7), (48, 1, FU + 1)], 60, FS + 1)
    jangkar(k, 48, FS + 1, 60, "kota_tema/zona", tema=TEMA, distrik="teras_hunian", protect=72)
    names = {(i, j): "rumah_%d%d_%s" % (i, j, var) for i in (0, 1) for j in (0, 1)}
    pools = {(i, j): "teras_hunian/rumah_%d%d" % (i, j) for i in (0, 1) for j in (0, 1)}
    potong(k, "teras_hunian", names, pool_names=pools, varian="_" + var)


# ===================================================================================================== aula_herba
def rak_jemur_luar(k, x0, z0, f, n=5):
    """Outdoor sun-drying frames: bamboo poles, woven trays (birch and spruce trapdoors) in three tiers, baskets."""
    for i in range(n):
        x = x0 + i * 4
        for dz in (0, 4):
            for y in range(f + 1, f + 5):
                k.put(x, y, z0 + dz, "minecraft:bamboo_fence")
            for y in range(f + 1, f + 5):
                k.put(x + 2, y, z0 + dz, "minecraft:bamboo_fence")
        for t in range(3):
            y = f + 2 + t
            for xx in range(x, x + 3):
                for zz in range(z0 + 1, z0 + 4):
                    k.put(xx, y, zz, "minecraft:birch_trapdoor" if (xx + zz + t) % 2 else "minecraft:bamboo_trapdoor",
                          {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
            if t < 2:
                k.put(x + 1, y + 1, z0 + 2, "minecraft:leaf_litter",
                      {"facing": "east", "segment_amount": str(1 + (i + t) % 4)}) if (i + t) % 2 else None
        k.put(x + 3, f + 1, z0 + 2, "minecraft:composter", {"level": str((i * 3) % 8)})
        k.titik("kerja:rak", x + 1, f + 1, z0 + 5, "north")


def aula_jemur(k, x0, z0, x1, z1, f):
    """Aula Herba (drying hall): a tall timber hall with louvred clerestory, multi-tier racks along both long walls
    (leaf litter, dried kelp, hay), sorting tables, sacks, a herb-smoke brazier corner (soul lantern), six rack points."""
    a = K.aula(k, x0, z0, x1, z1, h=8, podium_h=1, teras=1, roof="gable", tiers=1, door_sides=("south", "north"),
               door_w=3, along="x", y=f)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    for z, fc in ((iz0, "south"), (iz1, "north")):
        for x in range(ix0 + 1, ix1, 7):
            herb_rack(k, x, z, fl, along="x", n=6, tiers=3)
    for x in range(ix0 + 3, ix1 - 2, 6):
        for dz in (-1, 0, 1):
            k.put(x, fl + 1, (iz0 + iz1) // 2 + dz, "minecraft:spruce_fence")
            k.put(x, fl + 2, (iz0 + iz1) // 2 + dz, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
        k.put(x, fl + 3, (iz0 + iz1) // 2, "minecraft:leaf_litter", {"facing": "north", "segment_amount": "3"})
    pts = 0
    for x in range(ix0 + 2, ix1 - 1, 5):
        if pts < 6:
            k.titik("kerja:rak", x, fl + 1, iz0 + 2 if pts % 2 == 0 else iz1 - 2, "north" if pts % 2 == 0 else "south")
            pts += 1
    k.put(ix1 - 1, fl + 1, iz0 + 1, "minecraft:cauldron")
    k.put(ix1 - 1, fl + 2, iz0 + 1, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    K.crates(k, ix0, (iz0 + iz1) // 2 - 1, ix0 + 1, (iz0 + iz1) // 2 + 1, fl + 1, h=2)
    return a


def toko_herba(k, x0, z0, x1, z1, f):
    """The herb shop and the appraisal room: shelves of jars and potted herbs, a counter, the appraiser's lectern and
    balance (chains and trapdoor pans), a ledger shelf, a cushion corner where pickers wait."""
    a = K.aula(k, x0, z0, x1, z1, h=6, podium_h=1, teras=1, roof="hip", tiers=2, door_sides=("north",), door_w=3, y=f)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    mid = (ix0 + ix1) // 2
    for x in range(ix0, mid):
        for y in (fl + 1, fl + 2, fl + 3):
            r = (x + y) % 4
            if r == 0:
                k.put(x, y, iz1, "minecraft:decorated_pot", {"cracked": "false", "facing": "north", "waterlogged": "false"})
            elif r == 1:
                K.pot(k, x, y, iz1)
            else:
                K.shelf(k, x, y, iz1, "north")
    K.counter(k, ix0 + 1, mid - 2, iz1 - 3, fl, "north")
    k.titik("kerja:timbangan", ix0 + 4, fl + 1, iz1 - 2, "north")
    # appraisal room (east half) behind a lattice screen
    for z in range(iz0, iz1 + 1):
        if z != (iz0 + iz1) // 2:
            k.put(mid + 1, fl + 1, z, "minecraft:spruce_trapdoor", {"facing": "east", "half": "bottom", "open": "true",
                                                                    "powered": "false", "waterlogged": "false"})
    k.put(mid + 4, fl + 1, iz1 - 2, "minecraft:lectern", {"facing": "north", "has_book": "true", "powered": "false"})
    for (bx, bz) in ((mid + 7, iz1 - 2),):
        k.put(bx, fl + 1, bz, "minecraft:spruce_fence")
        k.put(bx, fl + 2, bz, "minecraft:spruce_fence")
        for dx in (-1, 1):
            k.put(bx + dx, fl + 2, bz, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
            k.put(bx + dx * 2, fl + 2, bz, "minecraft:spruce_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                                        "powered": "false", "waterlogged": "false"})
    k.titik("kerja:timbangan", mid + 7, fl + 1, iz1 - 3, "north")
    K.bookshelf_wall(k, ix1, iz0, ix1, iz1 - 4, fl + 1, fl + 3, face="west")
    for (cx, cz) in ((mid + 4, iz0 + 2), (mid + 8, iz0 + 2)):
        K.low_table(k, cx, fl + 1, cz)
        for d, (dx, dz) in FACE.items():
            k.put(cx + dx, fl + 1, cz + dz, "rug")
            k.titik("kursi_lantai", cx + dx, fl + 1, cz + dz, OPP[d])
    return a


def menara_tiang(k, x0, z0, f, tinggi=30):
    """Tiang Tengah of the cable car: a stone base 9 x 9 x 8 with an arched passage, then a timber lattice tower (corner
    logs, X braces of fences, landings every 6 with ladder holes) to a roofed wheel deck; the guide wheel is the
    roda_katrol entity (anchor tema_gunung/roda) over the rope line x0 + 4.5. Returns the wheel centre (x, y, z)."""
    x1, z1 = x0 + 8, z0 + 8
    base = 8
    for y in range(f + 1, f + 1 + base):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if edge:
                    k.put(x, y, z, "found" if k.rng.random() > 0.15 else "found_alt")
    for z in range(z0, z1 + 1):              # arched passage east-west
        for y in range(f + 1, f + 5):
            for x in (x0, x1):
                if z0 + 3 <= z <= z0 + 5:
                    k.put(x, y, z, "minecraft:air")
        for x in (x0, x1):
            k.stairs(x, f + 5, z0 + 3, "kerb_stairs", "south", half="top")
            k.stairs(x, f + 5, z0 + 5, "kerb_stairs", "north", half="top")
    k.fill(x0 + 1, f + base, z0 + 1, x1 - 1, f + base, z1 - 1, "wood_alt")
    top = f + tinggi
    for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(f + base + 1, top + 1):
            K.put_ax(k, px, y, pz, "trunk")
    for y0 in range(f + base, top, 6):
        for i in range(1, 6):
            if y0 + i > top:
                break
            for (ax, az, bx, bz) in ((x0, z0, x1, z0), (x0, z1, x1, z1)):
                k.put(ax + i + 1, y0 + i, az, "wood_fence")
                k.put(bx - i - 1, y0 + i, bz, "wood_fence")
            for (ax, az, bx, bz) in ((x0, z0, x0, z1), (x1, z0, x1, z1)):
                k.put(ax, y0 + i, az + i + 1, "wood_fence")
                k.put(bx, y0 + i, bz - i - 1, "wood_fence")
        for x in range(x0, x1 + 1):
            k.put(x, y0, z0, "minecraft:stripped_spruce_log", {"axis": "x"})
            k.put(x, y0, z1, "minecraft:stripped_spruce_log", {"axis": "x"})
        for z in range(z0, z1 + 1):
            k.put(x0, y0, z, "minecraft:stripped_spruce_log", {"axis": "z"})
            k.put(x1, y0, z, "minecraft:stripped_spruce_log", {"axis": "z"})
        if y0 > f + base:
            for x in range(x0 + 1, x1):
                for z in range(z0 + 1, z1):
                    if not (x == x0 + 1 and z == z0 + 1):
                        k.put(x, y0, z, "wood_slab", {"type": "top", "waterlogged": "false"})
    K.ladder(k, x0 + 1, f + 1, top, z0 + 1, "south")
    for y in range(f + base + 1, top):
        k.put(x0 + 1, y, z0, "wood_alt")
    # wheel deck: a solid floor, rails, a small roofed housing open north-south for the rope
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            k.put(x, top, z, "wood_alt" if (x - x0) % 2 else "wood")
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                if z in (z0 - 1, z1 + 1) and abs(x - (x0 + 4)) <= 2:
                    continue          # the cabin passes through here (GunungTata.KABIN_TIANG)
                k.put(x, top + 1, z, "rail")
    k.put(x0 + 1, top, z0 + 1, "minecraft:air")
    for (px, pz) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        for y in range(top + 1, top + 7):
            k.put(px, y, pz, "red")
    K.beam_band(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, top + 7)
    K.hip(k, x0 + 1, top + 8, z0 + 1, x1 - 1, z1 - 1, overhang=1, top="pearl")
    cx = x0 + 4
    for y in range(top + 1, top + 4):                  # wheel bearings (the wheel itself is an entity)
        k.put(cx - 2, y, z0 + 4, "minecraft:stripped_dark_oak_log", {"axis": "y"})
        k.put(cx + 2, y, z0 + 4, "minecraft:stripped_dark_oak_log", {"axis": "y"})
    for x in (cx - 2, cx + 2):
        k.put(x, top + 4, z0 + 4, "minecraft:stripped_dark_oak_log", {"axis": "x"})
    K.lit(k, x0 + 2, top + 7, z1 - 2, chain=1, role="lamp")
    k.titik("stasiun_lift", cx, top + 1, z0 + 6, "north", {"pasangan": "gantung", "jenis": "tiang", "urut": 1})
    k.titik("jaga", cx + 2, top + 1, z1 - 1, "south")
    return cx, top + 3, z0 + 4


def peta_tebing(k, x0, y0, z, w=9, h=5):
    """A painted map of the cliffs on a wall (terracotta mosaic): grey rock, green ledges, a red path, white niches."""
    for x in range(w):
        for y in range(h):
            gx, gy = x / (w - 1.0), y / (h - 1.0)
            ridge = 0.5 + 0.35 * math.sin(gx * 5.0 + 1.0)
            if abs(gy - ridge) < 0.12 or (x + 2 * y) % 7 == 0 and gy > ridge:
                blk = "minecraft:red_terracotta"
            elif gy > ridge + 0.25:
                blk = "minecraft:green_terracotta"
            elif (x * 3 + y) % 5 == 0:
                blk = "minecraft:white_terracotta"
            else:
                blk = "minecraft:light_gray_terracotta" if (x + y) % 2 else "minecraft:gray_terracotta"
            k.put(x0 + x, y0 + h - 1 - y, z, blk)
    for x in range(-1, w + 1):
        k.put(x0 + x, y0 - 1, z, "minecraft:stripped_spruce_log", {"axis": "x"})
        k.put(x0 + x, y0 + h, z, "minecraft:stripped_spruce_log", {"axis": "x"})


def build_aula_herba():
    k = kanvas("aula_herba")
    frame(k, "aula_herba", bordes=4)
    # ---------------------------------------------------------------- (0,0) drying hall + sun racks
    aula_jemur(k, 4, 25, 40, 44, FN)
    rak_jemur_luar(k, 3, 4, FN, n=5)
    rak_jemur_luar(k, 24, 4, FN, n=4)
    jangkar(k, 22, FN + 2, 34, MOD + "/peran", yaw=0.0, peran="PEMETIK_HERBA", protect=6)
    # ---------------------------------------------------------------- (1,0) herb shop + appraisal (Aula Herba service)
    a = toko_herba(k, 55, 25, 91, 44, FN)
    ix0, iz0, ix1, iz1 = a["inner"]
    jangkar(k, ix0 + 5, a["f"] + 1, iz1 - 2, MOD + "/aula_herba", yaw=0.0, peran="TABIB", tokoh="yao_po", protect=10)
    for x in range(56, 94, 6):          # basket and sack yard
        K.barrel(k, x, FN + 1, 4, "up")
        k.put(x + 1, FN + 1, 4, "minecraft:composter", {"level": "6"})
        k.put(x + 2, FN + 1, 4, "minecraft:hay_block", {"axis": "y"})
    rak_jemur_luar(k, 58, 8, FN, n=8)
    # ---------------------------------------------------------------- (0,1) Tiang Tengah of the cable car
    pav(k, 14, 70, 34, 92, FS, role="paving", alt="paving_alt", ch=0.15)
    wx, wy, wz = menara_tiang(k, 20, 76, FS, tinggi=30)
    jangkar(k, wx, wy, wz, MOD + "/roda", besar=0, protect=6)
    jangkar(k, wx + 2, FS + 1, 74, MOD + "/tiang", yaw=180.0, peran="PENJAGA_LIFT", protect=10)
    k.titik("jaga", 18, FS + 1, 80, "west")
    for (px, pz) in ((5, 74), (10, 90), (38, 75), (40, 90)):
        pine_at(k, px, pz, FS, 10)
    rock(k, 4, 86, FS, 2, 3)
    rock(k, 36, 84, FS, 2, 2)
    for x in (16, 32):
        stone_lantern(k, x, FS + 1, 71)
    K.garden(k, 2, 52, 40, 62, y=FS, trees=("pine",), pond_too=False)
    # ---------------------------------------------------------------- (1,1) Rumah Pemetik: ropes, baskets, cliff map
    rumah_gunung(k, 58, 74, 22, 14, FS, face="north", seed=201, storeys=2, beds=4, sign=True)
    peta_tebing(k, 60, FS + 2, 75, w=9, h=4)
    for x in range(62, 78, 3):
        k.put(x, FS + 1, 86, "minecraft:barrel", {"facing": "up", "open": "false"})
        k.put(x + 1, FS + 1, 86, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    jangkar(k, 68, FS + 1, 80, MOD + "/peran", yaw=0.0, peran="PEMETIK_HERBA", protect=6)
    # the climbing practice face on the retaining wall: holds, chains and ladders
    for x in range(56, 94):
        for y in range(FS + 1, FN):
            if (x * 7 + y * 3) % 11 == 0:
                k.put(x, y, 50, "minecraft:stone_button", {"face": "wall", "facing": "south", "powered": "false"})
        if x % 9 == 4:
            for y in range(FS + 1, FN + 1):
                k.put(x, y, 50, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        if x % 9 == 8:
            K.ladder(k, x, FS + 1, FN - 1, 50, "south")
    for x in range(58, 92, 5):
        k.put(x, FS + 1, 52, "minecraft:hay_block", {"axis": "y"})
    k.titik("latihan", 70, FS + 1, 53, "north")
    k.titik("latihan", 80, FS + 1, 53, "north")
    for (px, pz) in ((86, 90), (92, 74)):
        pine_at(k, px, pz, FS, 9)
    tangga(k, [(48, 94), (48, 70), (48, 56), (48, 40, FN + 1), (48, 24, FN + 1), (48, 15, FN + 1), (48, 7, FN + 7),
            (48, 1, FU + 1)], 12, FS + 1)
    jangkar(k, 48, FS + 1, 61, "kota_tema/zona", tema=TEMA, distrik="aula_herba", protect=72)
    potong(k, "aula_herba", {(0, 0): "aula_jemur", (1, 0): "toko_herba", (0, 1): "tiang_tengah", (1, 1): "rumah_pemetik"},
           utuh=("tiang_tengah",))


# ===================================================================================================== teras_kebun
def teras_sempit(k, x0, x1, z0, z1, y, tanaman, saluran=True):
    """One narrow farm terrace: a mossy kerb on the downhill side, farmland or grass, a crop or tea (azalea) row
    pattern, and a bamboo-lined water channel along its uphill edge (still water in the template)."""
    for x in range(x0, x1 + 1):
        if COR0 <= x <= COR1:
            continue
        for z in range(z0, z1 + 1):
            for yy in range(FN + 1, y):
                k.put(x, yy, z, "minecraft:dirt" if z != z1 else ("minecraft:mossy_stone_bricks" if (x + yy) % 3
                                                                   else "minecraft:stone_bricks"))
            if saluran and z == z0:
                k.put(x, y, z, "minecraft:water", {"level": "0"})
                k.put(x, y - 1, z, "minecraft:stripped_bamboo_block", {"axis": "x"})
                continue
            if z == z1:
                k.put(x, y, z, "minecraft:mossy_cobblestone" if x % 4 else "minecraft:mossy_stone_bricks")
                if x % 6 == 0:
                    k.put(x, y + 1, z, "minecraft:mossy_cobblestone_wall")
                continue
            if tanaman == "teh":
                k.put(x, y, z, "minecraft:grass_block", {"snowy": "false"})
                if (z - z0) % 2 == 1:
                    k.put(x, y + 1, z, "minecraft:azalea" if (x + z) % 3 else "minecraft:flowering_azalea")
            elif tanaman == "beri":
                k.put(x, y, z, "minecraft:grass_block", {"snowy": "false"})
                if (x + z) % 2:
                    k.put(x, y + 1, z, "minecraft:sweet_berry_bush", {"age": str(2 + (x % 2))})
            else:
                k.put(x, y, z, "minecraft:farmland", {"moisture": "7"})
                age = {"minecraft:beetroots": "3"}.get(tanaman, str(5 + (x + z) % 3))
                k.put(x, y + 1, z, tanaman, {"age": age})
    if saluran:
        for x in range(x0, x1 + 1, 6):
            if not (COR0 <= x <= COR1):
                k.put(x, y + 1, z0, "minecraft:bamboo_fence")


def katrol_rangka(k, x, z, y_bawah, y_atas, keluar="north", masuk="south", tinggi_atas=6):
    """A pulley gantry (Katrol): two spruce posts beside the basket column (x, z), a top beam with the pulley housing
    under a small tiled roof (the wheel is the roda_katrol entity, the basket the katrol entity), the windlass (engkol)
    on the upper landing, a 3-long plank bridge toward `keluar` at the top, a gate on the `masuk` side at the bottom.
    y_bawah = basket floor (standing) at the bottom, y_atas = landing floor block at the top. Returns (x, z)."""
    dx, dz = FACE[keluar]
    px, pz = (2, 0) if dx == 0 else (0, 2)        # post offsets (perpendicular to the exit)
    top = y_atas + tinggi_atas
    for s_ in (-1, 1):
        cx_, cz_ = x + s_ * px, z + s_ * pz
        for y in range(y_bawah, top + 1):
            K.put_ax(k, cx_, y, cz_, "trunk")
        k.put(cx_, y_bawah - 1, cz_, "kerb_chisel")
        for y in range(y_bawah + 2, top, 4):
            k.put(x + s_ * px // 2, y, z + s_ * pz // 2, "wood_fence")
    for i in range(-3, 4):
        k.put(x + i * (1 if px else 0), top + 1, z + i * (1 if pz else 0), "minecraft:stripped_dark_oak_log",
              {"axis": "x" if px else "z"})
    if px:
        K.gable(k, x - 3, top + 2, z - 1, x + 3, z + 1, along="x", overhang=0, pearl=False, role="tile_stairs",
                slab_role="tile_slab", full_role="tile")
    else:
        K.gable(k, x - 1, top + 2, z - 3, x + 1, z + 3, along="z", overhang=0, pearl=False, role="tile_stairs",
                slab_role="tile_slab", full_role="tile")
    # rope chains beside the basket (the basket's own rope is drawn by the client)
    for y in range(y_atas + 3, top + 1):
        k.put(x + px // 2, y, z + pz // 2, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    # the bridge at the top and the windlass beside it
    for i in range(1, 4):
        bx, bz = x + i * dx, z + i * dz
        k.put(bx, y_atas, bz, "wood_alt")
        k.put(bx + pz // 2, y_atas + 1, bz + px // 2, "rail") if False else None
        for s_ in (-1, 1):
            k.put(bx + s_ * (px // 2), y_atas + 1, bz + s_ * (pz // 2), "rail")
    wx, wz = x + dx + px, z + dz + pz
    k.put(wx, y_atas, wz, "wood_alt")
    for i in (-1, 0, 1):
        k.put(wx + i * (1 if px else 0) * 0, y_atas + 1, wz, "minecraft:stripped_spruce_log", {"axis": "x" if px else "z"})             if i == 0 else None
    k.put(wx, y_atas + 2, wz, "wood_fence")
    k.put(wx + (1 if px else 0), y_atas + 1, wz + (1 if pz else 0), "minecraft:lever",
          {"face": "floor", "facing": keluar, "powered": "false"})
    k.titik("kerja:dayung", wx - dx, y_atas + 1, wz - dz, keluar)
    k.titik("stasiun_lift", x, y_bawah, z, keluar, {"pasangan": "katrol", "jenis": "bawah"})
    k.titik("stasiun_lift", x + 2 * dx, y_atas + 1, z + 2 * dz, keluar, {"pasangan": "katrol", "jenis": "atas"})
    mx, mz = FACE[masuk]
    k.put(x + mx, y_bawah, z + mz, "minecraft:spruce_fence_gate",
          {"facing": masuk, "in_wall": "false", "open": "false", "powered": "false"})
    return x, z


def rumah_teh(k, x0, z0, f):
    """Rumah Olah Teh: pan-firing woks over smokers, rolling mats, withering trays, tasting table, tea chests."""
    rumah_gunung(k, x0, z0, 20, 13, f, face="north", seed=301, storeys=1, beds=0, kitchen=False, sign=True)
    for x in range(x0 + 2, x0 + 17, 4):
        k.put(x, f + 1, z0 + 11, "minecraft:smoker", {"facing": "north", "lit": "true"})
        k.put(x, f + 2, z0 + 11, "minecraft:cauldron")
        k.titik("kerja:kuali", x, f + 1, z0 + 10, "south")
    for x in range(x0 + 3, x0 + 16, 5):
        for dz in (0, 1):
            k.put(x, f + 1, z0 + 5 + dz, "minecraft:bamboo_mosaic_slab", {"type": "top", "waterlogged": "false"})
        k.titik("kerja:lesung", x, f + 1, z0 + 4, "south")
    herb_rack(k, x0 + 2, z0 + 2, f, along="x", n=8, tiers=2)
    for x in range(x0 + 12, x0 + 18):
        K.barrel(k, x, f + 1, z0 + 2, "north")
    K.furnish_dining(k, x0 + 16, z0 + 7, f, n=3)


def build_teras_kebun():
    k = kanvas("teras_kebun")
    frame(k, "teras_kebun")
    # ---------------------------------------------------------------- (0,0)+(1,0) five narrow terraces of herbs and tea
    teras_sempit(k, 2, 95, 3, 9, FN + 1, "teh")
    teras_sempit(k, 2, 95, 10, 16, FN + 2, "minecraft:carrots")
    teras_sempit(k, 0, 95, 24, 30, FN + 1, "beri", saluran=False)
    teras_sempit(k, 0, 95, 31, 38, FN + 2, "minecraft:beetroots")
    teras_sempit(k, 0, 95, 39, 46, FN + 3, "teh")
    for x in range(4, 92, 11):         # pickers' points and baskets along the rows
        if COR0 - 2 <= x <= COR1 + 2:
            continue
        k.titik("kerja:sabit", x, FN + 2, 6, "north")
        k.titik("kerja:sabit", x + 3, FN + 4, 42, "north")
        K.barrel(k, x + 1, FN + 2, 25, "up")
    # the Katrol Teras: a basket lift from this terrace up the retaining wall to the Biara Awan terrace
    jx, jz = katrol_rangka(k, 38, 3, FN + 2, FU, keluar="north", masuk="south", tinggi_atas=6)
    for x in range(36, 41):
        for z in range(2, 5):
            k.put(x, FN + 1, z, "kerb")
    jangkar(k, jx, FN + 2, jz, MOD + "/katrol", nama="teras", naik=FU - FN - 1, keluar="north", protect=8,
            peran="PENJAGA_LIFT")
    jangkar(k, jx, FU + 7, jz, MOD + "/roda", besar=0, katrol="teras", protect=4)
    # ---------------------------------------------------------------- (0,1) tea house
    rumah_teh(k, 4, 74, FS)
    jangkar(k, 14, FS + 1, 80, MOD + "/peran", yaw=0.0, peran="PETANI", protect=6)
    K.garden(k, 2, 52, 40, 62, y=FS, trees=("pine",), pond_too=True)
    # ---------------------------------------------------------------- (1,1) fertiliser shed and farmers' houses
    x0, z0 = 56, 52
    for x in range(x0, x0 + 18):
        for z in range(z0, z0 + 10):
            k.put(x, FS, z, "minecraft:coarse_dirt" if (x + z) % 3 else "minecraft:podzol")
    for (px, pz) in ((x0, z0), (x0 + 17, z0), (x0, z0 + 9), (x0 + 17, z0 + 9)):
        for y in range(FS + 1, FS + 5):
            K.put_ax(k, px, y, pz, "trunk")
    gable_roof(k, x0, FS + 5, z0, x0 + 17, z0 + 9, along="x", overhang=1)
    for x in range(x0 + 1, x0 + 17, 2):
        k.put(x, FS + 1, z0 + 1, "minecraft:composter", {"level": str(1 + (x % 7))})
        K.barrel(k, x + 1, FS + 1, z0 + 1, "up")
    K.crates(k, x0 + 2, z0 + 4, x0 + 8, z0 + 8, FS + 1, h=3)
    for x in range(x0 + 10, x0 + 16):
        k.put(x, FS + 1, z0 + 7, "minecraft:bone_block", {"axis": "x"}) if x % 2 else \
            k.put(x, FS + 1, z0 + 7, "minecraft:hay_block", {"axis": "y"})
    k.titik("kerja:cangkul", x0 + 9, FS + 1, z0 + 5, "south")
    for i, x in enumerate((56, 68, 80)):
        rumah_gunung(k, x, 74, 11, 10, FS, face="north", seed=320 + i, storeys=1 + (i % 2), beds=3)
    for x in (62, 74, 86):
        k.titik("obrol", x, FS + 1, 70, "north")
    kebun_teras_kecil(k, 58, 86, 92, 93, FS)
    tangga(k, [(48, 94), (48, 70), (48, 56), (48, 40, FN + 1), (48, 24, FN + 1)], 70, FS + 1)
    jangkar(k, 48, FS + 1, 60, "kota_tema/zona", tema=TEMA, distrik="teras_kebun", protect=72)
    potong(k, "teras_kebun", {(0, 0): "kebun_barat", (1, 0): "kebun_timur", (0, 1): "rumah_teh", (1, 1): "gudang_pupuk"})


# ===================================================================================================== tebing_papan
TEBING_X = 9            # east face of the artificial cliff (rock x 0..9)
TEBING_TOP = 40         # canvas y of the cliff crest
PAPAN_Z0, PAPAN_Z1 = 92, 10   # the plank path climbs from z 92 (foot) to z 10 (top platform)
CERUK_Z = (84, 72, 60, 40, 28, 18)     # the six meditation niches along the path (ceruk 1..6)
HERBA_TEBING = ((90, 4), (70, 5), (34, 1), (22, 2), (12, 3))   # (z, herb no) on the path edge


def papan_y(z):
    """Surface height (canvas, half blocks allowed) of the plank path at z."""
    t = (PAPAN_Z0 - z) / float(PAPAN_Z0 - PAPAN_Z1)
    return FS + 1.0 + t * (TEBING_TOP - 8 - FS - 1)


def papan_blok(z):
    """(block y, slab kind) of the path slab at z."""
    s = papan_y(z) - 0.5
    y = int(math.floor(s))
    return (y, "bottom") if s - y < 0.5 else (y, "top")


def tebing_buatan(k):
    """The artificial cliff on the west edge: layered stone, andesite, tuff and calcite streaks, moss and vines on the
    face, rock relief, a few pines and azaleas on the crest; the plank path (spruce slab on spruce fence posts set in the
    rock), a chain handrail on posts, ladders up two steep stretches, six meditation niches and a top platform."""
    for z in range(3, 96):
        crest = TEBING_TOP + int(round(2.0 * math.sin(z * 0.23) + 1.5 * math.sin(z * 0.61 + 0.7)))
        base = FS if z >= 48 else FN
        for x in range(0, TEBING_X + 1):
            top = crest - max(0, (x - 5)) * 2 + k.rng.randint(0, 1)
            for y in range(base + 1, top + 1):
                band = (y + (z // 7)) % 9
                r = k.rng.random()
                if x == TEBING_X and r < 0.1:
                    blk = "minecraft:mossy_cobblestone"
                elif band == 0:
                    blk = "minecraft:calcite"
                elif band in (3, 4):
                    blk = "minecraft:tuff" if r < 0.7 else "minecraft:andesite"
                else:
                    blk = "rock" if r < 0.55 else "rock_alt"
                k.put(x, y, z, blk)
            if x == TEBING_X - 1 and top > base + 4 and k.rng.random() < 0.25:
                k.put(x, top + 1, z, "minecraft:moss_carpet")
        if z % 9 == 4:
            K.pine(k, 3, crest + 1, z, k.rng.randint(6, 9))
        elif z % 9 == 0:
            k.put(4, crest + 1, z, "minecraft:azalea")
        # vines down the face
        if k.rng.random() < 0.35:
            vy = crest - k.rng.randint(2, 6)
            for y in range(vy - k.rng.randint(3, 9), vy):
                if y > base + 1:
                    k.put(TEBING_X + 1, y, z, "minecraft:vine", {"north": "false", "east": "false", "south": "false",
                                                              "west": "true", "up": "false"})
    # the plank path
    posts = 0
    for z in range(PAPAN_Z1, PAPAN_Z0 + 1):
        y, kind = papan_blok(z)
        for x in (TEBING_X + 1,):
            k.slab(x, y, z, "minecraft:spruce_slab", kind)
            k.put(x, y - 1, z, "minecraft:spruce_fence")
            k.put(x, y + 1, z, "minecraft:air")
            k.put(x, y + 2, z, "minecraft:air")
            if k.get(x + 1, y + 1, z) not in (None,) and k.get(x + 1, y + 1, z) == "minecraft:vine":
                k.put(x + 1, y + 1, z, "minecraft:air")
        if z % 5 == 0:
            k.slab(TEBING_X + 2, y, z, "minecraft:spruce_slab", kind)
            k.put(TEBING_X + 2, y - 1, z, "minecraft:spruce_fence")
            k.put(TEBING_X + 2, y + 1, z, "minecraft:spruce_fence")
            posts += 1
        else:
            k.put(TEBING_X + 2, y + 1, z, "minecraft:iron_chain", {"axis": "z", "waterlogged": "false"})
        if z % 12 == 6:
            k.put(TEBING_X, y + 3, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
            k.put(TEBING_X, y + 2, z, "rock")
    # six niches
    for n, z in enumerate(CERUK_Z, start=1):
        y, kind = papan_blok(z)
        fy = y + (1 if kind == "top" else 0)
        for x in range(TEBING_X - 3, TEBING_X + 1):
            for dz in (-1, 0, 1):
                for dy in range(0, 4):
                    k.put(x, fy + dy, z + dz, "minecraft:air")
                k.put(x, fy - 1, z + dz, "rock_alt")
        k.put(TEBING_X - 2, fy, z, "minecraft:yellow_carpet")
        k.put(TEBING_X - 3, fy, z - 1, "minecraft:candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
        k.put(TEBING_X - 3, fy, z + 1, "minecraft:moss_carpet")
        k.put(TEBING_X - 3, fy + 3, z, "minecraft:hanging_roots")
        k.titik("meditasi", TEBING_X - 2, fy, z, "east", {"ceruk": n})
        jangkar(k, TEBING_X - 1, fy, z, MOD + "/ceruk", no=n, protect=4)
    for z, no in HERBA_TEBING:
        y, kind = papan_blok(z)
        fy = y + (1 if kind == "top" else 0)
        k.put(TEBING_X, fy, z, "minecraft:air")
        k.put(TEBING_X, fy - 1, z, "minecraft:moss_block")
        jangkar(k, TEBING_X, fy, z, MOD + "/herba", no=no, protect=3)
    # the top platform with a small rest pavilion and a viewing point
    y, kind = papan_blok(PAPAN_Z1)
    ty = y + 1
    for x in range(TEBING_X - 1, TEBING_X + 8):
        for z in range(4, 12):
            k.put(x, ty - 1, z, "wood_alt" if (x + z) % 2 else "wood")
            for yy in range(FN + 1, ty - 1):
                if x in (TEBING_X - 1, TEBING_X + 7) and z in (4, 11):
                    K.put_ax(k, x, yy, z, "stilt")
            if x == TEBING_X + 7 or z in (4,):
                k.put(x, ty, z, "rail")
    for (px, pz) in ((TEBING_X, 5), (TEBING_X + 6, 5), (TEBING_X, 10), (TEBING_X + 6, 10)):
        for yy in range(ty, ty + 4):
            k.put(px, yy, pz, "red")
    K.hip(k, TEBING_X, ty + 4, 5, TEBING_X + 6, 10, overhang=1, top="pearl")
    k.titik("pandang", TEBING_X + 5, ty, 7, "east", {"nama": "tebing"})
    k.titik("meditasi", TEBING_X + 3, ty, 8, "east")
    return ty


def gua_pertapa(k, x0, z0, f):
    """Gua Pertapa Tebing: a rock mass merged with the north cliff, the hermit's cave 7 x 5 x 7 inside (straw bed,
    meditation mats, a candle shrine, scrolls), a ledge outside with a viewing point."""
    for x in range(x0 - 4, x0 + 12):
        for z in range(z0 - 3, z0 + 11):
            d = max(abs(x - (x0 + 3.5)) / 8.0, abs(z - (z0 + 3.5)) / 7.5)
            if d > 1.0:
                continue
            hh = int(12 * (1.0 - d * d)) + k.rng.randint(0, 2)
            for y in range(f + 1, f + 2 + hh):
                r = k.rng.random()
                k.put(x, y, z, "rock" if r < 0.5 else "rock_alt" if r < 0.75 else "minecraft:tuff")
            if k.rng.random() < 0.3:
                k.put(x, f + 2 + hh, z, "minecraft:moss_carpet")
    k.air(x0, f + 1, z0, x0 + 6, f + 5, z0 + 6)
    for x in range(x0, x0 + 7):
        for z in range(z0, z0 + 7):
            k.put(x, f, z, "minecraft:rooted_dirt" if (x + z) % 3 else "minecraft:moss_block")
    k.air(x0 + 2, f + 1, z0 + 7, x0 + 4, f + 3, z0 + 10)
    k.put(x0 + 1, f + 1, z0 + 1, "minecraft:hay_block", {"axis": "x"})
    k.put(x0 + 2, f + 1, z0 + 1, "minecraft:hay_block", {"axis": "x"})
    k.titik("kasur", x0 + 1, f + 2, z0 + 1, "south")
    for (mx, mz) in ((x0 + 2, z0 + 4), (x0 + 5, z0 + 4)):
        k.put(mx, f + 1, mz, "minecraft:yellow_carpet")
        k.titik("meditasi", mx, f + 1, mz, "north")
    k.put(x0 + 3, f + 1, z0, "minecraft:polished_andesite")
    K.candle(k, x0 + 3, f + 2, z0, n=4, color="white")
    k.put(x0 + 6, f + 1, z0, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    k.put(x0 + 6, f + 1, z0 + 6, "minecraft:decorated_pot", {"cracked": "true", "facing": "west", "waterlogged": "false"})
    k.put(x0 + 3, f + 5, z0 + 3, "minecraft:hanging_roots")
    k.put(x0, f + 4, z0 + 6, "minecraft:glow_lichen", {"down": "false", "east": "false", "north": "false", "south": "false",
                                                       "up": "false", "waterlogged": "false", "west": "true"})
    k.titik("pandang", x0 + 3, f + 1, z0 + 12, "south", {"nama": "gua"})


def puncak_karang(k, x0, z0, f, tinggi):
    """A tall rock crag with a hidden niche on top (the Kera Putih's nest of root fibres: hay, roots, dead bushes,
    a small stone shrine) reached only by the Katrol Tebing; returns the top floor y."""
    cx, cz = x0 + 7, z0 + 8
    for x in range(x0, x0 + 15):
        for z in range(z0, z0 + 17):
            d = math.hypot((x - cx) / 7.5, (z - cz) / 8.5)
            if d > 1.0:
                continue
            hh = int(tinggi * (1.0 - 0.35 * d * d)) - (2 if d > 0.85 else 0) + k.rng.randint(0, 1)
            for y in range(f + 1, f + 1 + hh):
                r = k.rng.random()
                band = (y // 4) % 3
                k.put(x, y, z, ("rock" if r < 0.5 else "rock_alt") if band else ("minecraft:tuff" if r < 0.6 else "rock"))
            if hh > 4 and k.rng.random() < 0.2:
                k.put(x, f + 1 + hh, z, "minecraft:moss_carpet")
    ty = f + tinggi - 6
    k.air(cx - 3, ty, cz - 3, cx + 3, ty + 4, cz + 3)
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            k.put(x, ty - 1, z, "minecraft:rooted_dirt")
            if math.hypot(x - cx, z - cz) < 2.6:
                k.put(x, ty, z, "minecraft:hay_block", {"axis": "y"}) if (x + z) % 3 == 0 else \
                    k.put(x, ty, z, "minecraft:dead_bush") if (x + z) % 3 == 1 else None
    for (hx, hz) in ((cx - 3, cz), (cx + 3, cz - 2), (cx, cz + 3)):
        k.put(hx, ty + 4, hz, "minecraft:hanging_roots")
    k.put(cx + 2, ty, cz + 2, "kerb_chisel")
    K.candle(k, cx + 2, ty + 1, cz + 2, n=1, color="white")
    k.air(x0 - 1, ty, cz - 1, cx - 3, ty + 3, cz + 1)          # the opening on the west side (katrol landing)
    K.pine(k, cx + 4, f + tinggi + 1, cz - 5, 6)
    return ty, cx, cz


def isi_tebing_barat(k):
    """The west lots of the cliff district beside the plank path: the pickers' lodge, rope and basket yard, the old
    rockfall field with its memorial stela, the cliff watch hut with the rain barrier pole, herb racks, pines."""
    # SW band D: Pondok Pemetik Tebing
    rumah_gunung(k, 16, 74, 16, 12, FS, face="north", seed=401, storeys=2, beds=5, sign=True)
    for x in range(34, 42, 2):
        k.put(x, FS + 1, 76, "minecraft:spruce_fence")
        k.put(x, FS + 2, 76, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
        K.barrel(k, x, FS + 1, 79, "up")
        k.put(x + 1, FS + 1, 79, "minecraft:composter", {"level": "3"})
    k.titik("kerja:rak", 37, FS + 1, 78, "north")
    for (px, pz) in ((14, 92), (40, 90), (36, 86)):
        pine_at(k, px, pz, FS, 9)
    # SW band C: old rockfall field and the memorial stela of the fallen pickers
    for (bx, bz, r, h) in ((16, 55, 2, 3), (22, 59, 1, 2), (29, 54, 2, 2), (35, 58, 1, 3), (40, 54, 1, 1)):
        rock(k, bx, bz, FS, r, h, moss=0.5)
    stela_seribu(k, 26, 60, FS)
    k.titik("dupa", 26, FS + 1, 63, "north")
    # NW band B: Pos Jaga Tebing (watch hut and the barrier store), a meditation platform among pines
    gx, gz = pos_jaga(k, 16, 26, FN, "east")
    k.titik("jaga", gx, FN + 1, gz, "east")
    for z in range(24, 32, 2):
        k.put(14, FN + 1, z, "minecraft:spruce_fence")
        k.put(14, FN + 2, z, "minecraft:iron_chain", {"axis": "z", "waterlogged": "false"})
    for x in range(26, 36):
        for z in range(34, 42):
            k.put(x, FN + 1, z, "kerb" if x in (26, 35) or z in (34, 41) else "paving")
    for (mx, mz) in ((28, 36), (31, 36), (34, 36), (28, 39), (31, 39), (34, 39)):
        k.put(mx, FN + 2, mz, "minecraft:yellow_carpet")
        k.titik("meditasi", mx, FN + 2, mz, "north")
    for (px, pz) in ((24, 30), (38, 28), (40, 44), (18, 42)):
        pine_at(k, px, pz, FN, 10)
    rock(k, 38, 36, FN, 2, 3)
    # NW band A: herb racks of the pickers in the sun under the cliff
    rak_jemur_luar(k, 18, 5, FN, n=6)


def build_tebing_papan():
    k = kanvas("tebing_papan")
    frame(k, "tebing_papan")
    tebing_buatan(k)
    isi_tebing_barat(k)
    jangkar(k, TEBING_X + 2, FS + 1, 93, MOD + "/palang", protect=4)
    # ---------------------------------------------------------------- (1,0) Gua Pertapa Tebing
    gua_pertapa(k, 62, 4, FN)
    jangkar(k, 65, FN + 1, 8, MOD + "/peran", yaw=180.0, peran="PERTAPA", protect=6)
    jangkar(k, 70, FN + 1, 30, MOD + "/tokoh", yaw=270.0, tokoh="shan_ling", peran="PEMETIK_HERBA", protect=6)
    jangkar(k, 84, FN + 4, 6, MOD + "/herba", no=6, protect=3)
    jangkar(k, 88, FN + 1, 36, MOD + "/kera", jumlah=3, protect=4)
    for (px, pz) in ((56, 30), (78, 40), (92, 26)):
        pine_at(k, px, pz, FN, 9)
    rock(k, 84, 30, FN, 3, 4)
    rock(k, 60, 42, FN, 2, 3)
    for x in range(54, 92, 6):
        k.titik("obrol", x, FN + 1, 24, "south")
    # ---------------------------------------------------------------- (1,1) Katrol Tebing to the crag niche
    ty, cx, cz = puncak_karang(k, 72, 70, FS, 24)
    kx, kz = katrol_rangka(k, 68, cz, FS + 1, ty - 1, keluar="east", masuk="west", tinggi_atas=5)
    jangkar(k, kx, FS + 1, kz, MOD + "/katrol", nama="tebing", naik=ty - 1 - FS, keluar="east", protect=8,
            peran="PENJAGA_LIFT")
    jangkar(k, cx, ty, cz, MOD + "/sarang", protect=6)
    jangkar(k, cx - 2, ty, cz - 2, MOD + "/herba", no=7, protect=3)
    # the mountain spirit shrine (tudigong) and benches in band C
    x0, z0 = 60, 53
    k.walls(x0, FS + 1, z0, x0 + 4, FS + 3, z0 + 3, role="kerb", pillar="kerb_chisel", windows=0)
    k.air(x0 + 1, FS + 1, z0 + 3, x0 + 3, FS + 2, z0 + 3)
    K.gable(k, x0, FS + 4, z0, x0 + 4, z0 + 3, along="x", overhang=1, pearl=False, role="tile_stairs",
            slab_role="tile_slab", full_role="tile")
    k.put(x0 + 2, FS + 1, z0 + 1, "minecraft:chiseled_stone_bricks")
    K.candle(k, x0 + 2, FS + 2, z0 + 1, n=3, color="red")
    incense(k, x0 + 2, FS + 1, z0 + 6)
    for x in (70, 80, 88):
        bench_row(k, x, 58, "south", FS, n=2)
    jangkar(k, 84, FS + 1, 64, MOD + "/kera", jumlah=2, protect=4)
    jangkar(k, 60, FN + 1, 26, "kota_tema/zona", tema=TEMA, distrik="tebing_papan", protect=72)
    tangga(k, [(48, 94), (48, 70), (48, 56), (48, 40, FN + 1), (48, 24, FN + 1)], 80, FS + 1)
    potong(k, "tebing_papan", {(0, 0): "papan_atas", (1, 0): "gua_pertapa", (0, 1): "papan_bawah",
                               (1, 1): "katrol_tebing"}, utuh=("papan_atas", "papan_bawah"))


# ===================================================================================================== puncak_emas
EMAS = {"roof": "minecraft:smooth_sandstone_stairs", "roof_slab": "minecraft:smooth_sandstone_slab",
        "roof_full": "minecraft:gold_block", "red": "minecraft:gold_block", "plaster": "minecraft:yellow_terracotta",
        "plaster_alt": "minecraft:yellow_terracotta", "beam": "minecraft:stripped_dark_oak_log",
        "tile": "minecraft:gold_block", "tile_stairs": "minecraft:smooth_sandstone_stairs",
        "tile_slab": "minecraft:smooth_sandstone_slab"}


class pal_sementara:
    """with pal_sementara(k, {...}): temporarily overrides palette roles of a canvas (cells keep resolved ids)."""

    def __init__(self, k, roles):
        self.k, self.roles, self.old = k, roles, {}

    def __enter__(self):
        for r, v in self.roles.items():
            self.old[r] = self.k.pal.get(r)
            self.k.pal[r] = v
        return self.k

    def __exit__(self, *exc):
        for r, v in self.old.items():
            if v is None:
                self.k.pal.pop(r, None)
            else:
                self.k.pal[r] = v
        return False


def sepuh_atap(k, x0, z0, x1, z1, y_dari):
    """Gilds the topmost roof course of a hall: every highest roof block in the box above y_dari becomes raw gold
    (ridge and finials) - Jinding's golden tiles."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            top = None
            for y in range(H - 1, y_dari, -1):
                b = k.get(x, y, z)
                if b not in (None, "minecraft:air"):
                    top = (y, b)
                    break
            if top and "sandstone" in top[1] and top[1].endswith("_slab"):
                k.put(x, top[0], z, "minecraft:raw_gold_block")


def aula_emas(k, x0, z0, f):
    """Aula Emas (Jinding): 24 x 18 on a three-step podium, gilded pillars, yellow plaster, a double-eave hip roof of
    pale stone tiles with a raw-gold ridge, golden ridge beasts; inside three gilded Buddhas on a lotus altar, offering
    tables, a great incense ding, prayer cushions in rows, banners and lamps."""
    with pal_sementara(k, EMAS):
        a = K.aula(k, x0, z0, x0 + 23, z0 + 17, h=9, podium_h=3, teras=2, roof="hip", tiers=2, door_sides=("south",),
                   door_w=5, y=f)
    sepuh_atap(k, x0 - 2, z0 - 2, x0 + 25, z0 + 19, a["f"] + 9)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    cx = (ix0 + ix1) // 2
    for x in range(ix0 + 1, ix1):
        for z in range(iz0, iz0 + 4):
            k.put(x, fl + 1, z, "minecraft:polished_andesite")
    for i, bx in enumerate((cx - 5, cx, cx + 5)):
        h = 5 if i == 1 else 4
        for dy in range(h):
            w = 1 if dy < h - 2 else 0
            for dx in range(-w, w + 1):
                for dz in range(-w, w + 1):
                    k.put(bx + dx, fl + 2 + dy, iz0 + 1 + dz, "minecraft:gold_block" if dy >= 1 else "minecraft:yellow_terracotta")
        k.put(bx, fl + 2 + h, iz0 + 1, "minecraft:raw_gold_block")
        for d, (dx, dz) in FACE.items():
            if d != "north":
                k.stairs(bx + 2 * dx, fl + 2, iz0 + 1 + 2 * dz, "minecraft:cherry_stairs", OPP[d])
    for x in range(ix0 + 2, ix1 - 1):
        if x % 2:
            K.table(k, x, fl + 1, iz0 + 5, "minecraft:dark_oak_pressure_plate")
        else:
            K.candle(k, x, fl + 1, iz0 + 5, n=4, color="yellow")
    incense(k, cx, fl + 1, iz0 + 7, big=True)
    for x in range(ix0 + 1, ix1, 2):
        for z in range(iz0 + 9, iz1, 2):
            if abs(x - cx) > 1:
                k.put(x, fl + 1, z, "minecraft:yellow_carpet")
                k.titik("kursi_lantai", x, fl + 1, z, "north")
    for x in (ix0, ix1):
        for z in range(iz0 + 2, iz1, 4):
            K.wall_banner(k, x, fl + 5, z, "east" if x == ix0 else "west", "yellow")
    k.titik("kerja:dupa", cx - 2, fl + 1, iz0 + 6, "north")
    k.titik("kerja:dupa", cx + 2, fl + 1, iz0 + 6, "north")
    return a


def lonceng_raksasa(k, cx, cz, y_atas):
    """The great Lonceng Awan: a bronze bell of waxed copper (cut copper bands, a chiseled lip) 5 wide, hung by chains
    from the beam at y_atas, a log striker on two chains beside it; a real bell block inside rings (sound)."""
    prof = [(1, 0), (1, 1), (2, 2), (2, 3), (2, 4), (3, 5)]   # (radius, drop below the beam)
    for r, d in prof:
        y = y_atas - 2 - d
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) == r or d in (0,):
                    blk = "minecraft:waxed_cut_copper" if d in (2, 4) else "minecraft:waxed_copper_block"
                    if d == 5:
                        blk = "minecraft:waxed_chiseled_copper"
                    k.put(cx + dx, y, cz + dz, blk)
    k.put(cx, y_atas - 1, cz, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    k.put(cx, y_atas - 4, cz, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    for x in range(cx + 3, cx + 5):
        k.put(x, y_atas - 4, cz, "minecraft:stripped_spruce_log", {"axis": "x"})
    for y in range(y_atas - 3, y_atas):
        k.put(cx + 4, y, cz, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def menara_lonceng(k, x0, z0, f):
    """Menara Lonceng Awan: a two-storey open timber bell pavilion 13 x 13 on a stone base with stairs, red columns,
    a beam grid carrying the great bell, double eaves; returns (cx, floor y, cz)."""
    x1, z1 = x0 + 12, z0 + 12
    fl = K.podium(k, x0, z0, x1, z1, f, 3, steps=("west",), step_w=5)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for (px, pz) in [(x, z) for x in (x0 + 1, cx, x1 - 1) for z in (z0 + 1, cz, z1 - 1) if (x, z) != (cx, cz)]:
        for y in range(fl + 1, fl + 13):
            k.put(px, y, pz, "red")
    K.beam_band(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, fl + 13)
    for x in range(x0 + 1, x1):
        k.put(x, fl + 13, cz, "beam", {"axis": "x"})
    for z in range(z0 + 1, z1):
        k.put(cx, fl + 13, z, "beam", {"axis": "z"})
    K.brackets(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, fl + 13)
    K.hip(k, x0, fl + 14, z0, x1, z1, overhang=1, tiers=2, top="pearl")
    lonceng_raksasa(k, cx, cz, fl + 13)
    for x in range(x0 + 1, x1):
        for z in (z0 + 1, z1 - 1):
            if (x - x0) % 2:
                k.put(x, fl + 1, z, "kerb_wall")
    k.titik("kerja:dupa", cx - 3, fl + 1, cz + 3, "north")
    k.titik("penonton", cx + 3, fl + 1, cz - 4, "south")
    k.titik("penonton", cx - 3, fl + 1, cz - 4, "south")
    return cx, fl, cz


def build_puncak_emas():
    k = kanvas("puncak_emas")
    frame(k, "puncak_emas", n_street=False, center_n=False)
    # ---------------------------------------------------------------- (0,0)+(1,0) the summit plaza and the Aula Emas
    for x in range(2, 94):
        for z in range(4, 47):
            ring = math.hypot(x - 48, z - 32)
            blk = "paving" if int(ring) % 6 else "kerb"
            if abs(x - 48) <= 2:
                blk = "kerb_chisel" if z % 4 == 0 else "path"
            k.put(x, FN, z, blk)
    a = aula_emas(k, 36, 3, FN)
    jangkar(k, 48, a["f"] + 1, a["inner"][1] + 9, MOD + "/aula_emas", yaw=180.0, tokoh="jing_yue", peran="BIKSU_GUNUNG",
            protect=16)
    jangkar(k, 48, FN + 1, 23, MOD + "/bordes", no=9, protect=6)
    # the outdoor Samantabhadra statue on a lotus podium in the plaza centre
    for x in range(40, 57):
        for z in range(26, 43):
            d = math.hypot(x - 48, z - 34)
            if d <= 7.5:
                k.put(x, FN + 1, z, "minecraft:polished_andesite" if d < 6.5 else "kerb")
                if 6.5 <= d <= 7.5:
                    k.slab(x, FN + 2, z, "kerb_slab")
    patung_gajah(k, 48, 33, FN + 1, tinggi=10, menghadap="south")
    for (lx, lz) in ((39, 26), (57, 26), (39, 42), (57, 42)):
        stone_lantern(k, lx, FN + 1, lz)
    incense(k, 48, FN + 1, 44, big=True)
    # viewing terraces at the south balustrade and meditation decks on both sides
    for n, x in enumerate((10, 30, 66, 86)):
        k.titik("pandang", x, FN + 1, 45, "south", {"nama": "aula_emas", "no": n + 1})
        k.put(x, FN + 1, 46, "minecraft:polished_andesite")
    for (dx0, dx1) in ((6, 24), (72, 90)):
        for x in range(dx0, dx1 + 1):
            for z in range(28, 41):
                k.put(x, FN + 1, z, "wood" if (x + z) % 2 else "wood_alt")
                if x in (dx0, dx1) or z in (28, 40):
                    k.put(x, FN + 2, z, "rail")
        for i in range(4):
            mx = dx0 + 3 + i * 4
            for mz in (31, 37):
                k.put(mx, FN + 2, mz, "minecraft:yellow_carpet")
                k.titik("meditasi", mx, FN + 2, mz, "south", {"aula_emas": "1"})
    jangkar(k, 6, FN + 1, 8, MOD + "/herba", no=8, protect=3)
    jangkar(k, 90, FN + 1, 8, MOD + "/herba", no=9, protect=3)
    jangkar(k, 20, FN + 1, 18, MOD + "/kera", jumlah=2, protect=4)
    # the titipan pavilion (porter deliveries) east of the hall
    rumah_gunung(k, 66, 6, 13, 10, FN, face="south", seed=501, storeys=1, beds=0, kitchen=False, sign=True)
    for x in range(67, 78, 2):
        K.chest(k, x, FN + 1, 7, facing="south")
    jangkar(k, 72, FN + 1, 13, MOD + "/titipan", tempat="aula_emas", protect=6)
    rumah_gunung(k, 16, 6, 13, 10, FN, face="south", seed=502, storeys=1, beds=4, kitchen=True)
    # ---------------------------------------------------------------- (0,1) Stasiun Atas of the cable car
    px, py, pz = stasiun_gantung(k, 15, 74, FS, "atas", 2, buka="south")
    jangkar(k, px, py, pz, MOD + "/lift", yaw=0.0, stasiun="atas", protect=12, peran="PENJAGA_LIFT")
    jangkar(k, 24, FS + 1, 70, MOD + "/roda", besar=1, protect=4)
    for (bx, bz) in ((4, 56), (4, 62), (36, 56)):
        bench_row(k, bx, bz, "east", FS, n=2)
    pine_at(k, 4, 90, FS, 8)
    pine_at(k, 40, 92, FS, 8)
    K.garden(k, 2, 52, 30, 61, y=FS, trees=("pine",), pond_too=False)
    # ---------------------------------------------------------------- (1,1) Menara Lonceng Awan
    cx, fl, cz = menara_lonceng(k, 64, 73, FS)
    jangkar(k, cx - 3, fl + 1, cz + 3, MOD + "/lonceng_awan", protect=12, peran="BIKSU_GUNUNG")
    jangkar(k, 86, FS + 1, 60, MOD + "/kera", jumlah=2, protect=4)
    for (px_, pz_) in ((58, 56), (90, 90), (90, 72)):
        pine_at(k, px_, pz_, FS, 9)
    rock(k, 60, 92, FS, 2, 2)
    jangkar(k, 48, FS + 1, 56, MOD + "/bordes", no=7, protect=6)
    jangkar(k, 48, FN + 1, 46, MOD + "/bordes", no=8, protect=6)
    jangkar(k, 70, FS + 1, 64, "kota_tema/zona", tema=TEMA, distrik="puncak_emas", protect=72)
    tangga(k, [(48, 94), (48, 70), (48, 56), (48, 46, FN + 1), (44, 34, FN + 2), (48, 23, FN + 1)], 20, FS + 1)
    potong(k, "puncak_emas", {(0, 0): "aula_emas_barat", (1, 0): "aula_emas_timur", (0, 1): "stasiun_atas",
                              (1, 1): "menara_lonceng"}, utuh=("aula_emas_barat", "aula_emas_timur"))


# ===================================================================================================== biara_awan
def aula_dharma(k, x0, z0, f):
    """Aula Dharma of the Biara Awan: 34 x 20 hall, altar with a seated Buddha, 24 cushions in rows, the wooden-fish
    and bell stands, a preaching seat, sutra banners."""
    a = K.aula(k, x0, z0, x0 + 33, z0 + 19, h=7, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("north",),
               door_w=5, tile=True, y=f)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    cx = (ix0 + ix1) // 2
    for x in range(cx - 3, cx + 4):
        for z in range(iz1 - 3, iz1 + 1):
            k.put(x, fl + 1, z, "minecraft:polished_andesite")
    for dy in range(4):
        w = 1 if dy < 2 else 0
        for dx in range(-w, w + 1):
            for dz in range(-w, w + 1):
                k.put(cx + dx, fl + 2 + dy, iz1 - 1 + dz, "minecraft:gold_block" if dy else "minecraft:yellow_terracotta")
    k.put(cx, fl + 6, iz1 - 1, "minecraft:raw_gold_block")
    n = 0
    for z in range(iz0 + 2, iz1 - 5, 2):
        for x in range(ix0 + 2, ix1 - 1, 3):
            if abs(x - cx) <= 1 or n >= 24:
                continue
            k.put(x, fl + 1, z, "minecraft:orange_carpet")
            k.titik("kursi_lantai", x, fl + 1, z, "south")
            n += 1
    K.drum(k, ix0 + 1, fl + 1, iz1 - 2, "east")
    k.put(ix1 - 1, fl + 4, iz1 - 2, "minecraft:bell", {"attachment": "ceiling", "facing": "west", "powered": "false"})
    k.titik("kerja:dupa", ix0 + 2, fl + 1, iz1 - 2, "west")
    k.titik("kerja:dupa", ix1 - 2, fl + 1, iz1 - 2, "east")
    k.put(cx, fl + 1, iz1 - 5, "minecraft:orange_carpet")
    k.titik("panggung", cx - 2, fl + 1, iz1 - 6, "north", {"dx": "4", "dy": "3", "dz": "2", "acara": "ceramah"})
    incense(k, cx, fl + 1, iz0 + 1)
    for x in (ix0, ix1):
        for z in range(iz0 + 2, iz1, 4):
            K.wall_banner(k, x, fl + 5, z, "east" if x == ix0 else "west", "orange")
    return a


def perpustakaan_sutra(k, x0, z0, f):
    """Perpustakaan Sutra: a long hall lined with bookshelves and chiseled shelves, lecterns, reading tables."""
    a = K.aula(k, x0, z0, x0 + 33, z0 + 12, h=5, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("south",),
               door_w=3, tile=True, y=f)
    ruang(k, *a["inner"], a["f"], "kantor", isi=0.4)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    K.bookshelf_wall(k, ix0, iz0, ix1, iz0, fl + 1, fl + 4, face="south")
    for x in range(ix0 + 2, ix1 - 1, 4):
        K.bookshelf_wall(k, x, iz0 + 2, x, iz0 + 5, fl + 1, fl + 3, face="east")
    for x in range(ix0 + 4, ix1 - 1, 8):
        k.put(x, fl + 1, iz1 - 2, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
        k.titik("meja_tulis", x, fl + 1, iz1 - 1, "north")
    return a


def asrama_biksu(k, x0, z0, f):
    """The monks' dormitory: 12 plain beds in two rows, chests, a washing corner, a stair to a loft of robes."""
    a = K.aula(k, x0, z0, x0 + 35, z0 + 19, h=5, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("north",),
               door_w=3, tile=True, y=f)
    ruang(k, *a["inner"], a["f"], "inap", isi=0.5)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    for i in range(6):
        x = ix0 + 2 + i * 5
        K.bed(k, x, fl + 1, iz1 - 1, "south", role="minecraft:gray_bed")
        k.titik("kasur", x, fl + 1, iz1 - 1, "north")
        K.chest(k, x + 1, fl + 1, iz1, facing="north")
        K.bed(k, x, fl + 1, iz0 + 5, "north", role="minecraft:gray_bed")
        k.titik("kasur", x, fl + 1, iz0 + 5, "south")
    k.put(ix0 + 1, fl + 1, iz0 + 1, "minecraft:cauldron")
    k.put(ix0 + 2, fl + 1, iz0 + 1, "minecraft:cauldron")
    return a


def kolam_pelepasan(k, x0, z0, x1, z1, f):
    """Kolam Pelepasan (release pond): a stone-rimmed pond with lilies, sea pickles, a zigzag bridge, a little
    pavilion over the water; and the raked-gravel rock garden beside it."""
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            d = math.hypot((x - cx) / ((x1 - x0) / 2.0), (z - cz) / ((z1 - z0) / 2.0))
            if d <= 0.82:
                k.put(x, f, z, "minecraft:water", {"level": "0"})
                k.put(x, f - 1, z, "minecraft:water", {"level": "0"})
                k.put(x, f - 2, z, "minecraft:clay" if (x + z) % 3 else "minecraft:gravel")
                r = k.rng.random()
                if r < 0.07:
                    k.put(x, f + 1, z, "minecraft:lily_pad")
                elif r < 0.1:
                    k.put(x, f - 1, z, "minecraft:sea_pickle", {"pickles": "2", "waterlogged": "true"})
            elif d <= 1.0:
                k.put(x, f, z, "kerb" if (x + z) % 4 else "minecraft:mossy_stone_bricks")
                if k.rng.random() < 0.15:
                    k.put(x, f + 1, z, "minecraft:mossy_cobblestone")
    # zigzag bridge
    zz = cz
    for i, x in enumerate(range(x0 + 2, x1 - 1)):
        if i % 5 == 4:
            zz += 1 if (i // 5) % 2 == 0 else -1
        k.slab(x, f + 1, zz, "minecraft:spruce_slab", "bottom")
        k.put(x, f, zz, "minecraft:spruce_fence")
    # pavilion (ting) in the middle
    for (px, pz) in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):
        for y in range(f - 1, f + 5):
            k.put(px, y, pz, "red")
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            k.put(x, f + 1, z, "wood")
    K.hip(k, cx - 2, f + 5, cz - 2, cx + 2, cz + 2, overhang=1, top="pearl")
    k.titik("pandang", cx, f + 2, cz, "south", {"nama": "kolam"})


def build_biara_awan():
    k = kanvas("biara_awan")
    frame(k, "biara_awan")
    # ---------------------------------------------------------------- (0,0) Aula Dharma + library
    a = aula_dharma(k, 5, 23, FN)
    jangkar(k, 22, a["f"] + 1, 37, MOD + "/biara", yaw=180.0, tokoh="jue_yuan", peran="KEPALA_BIARA", protect=16)
    perpustakaan_sutra(k, 5, 4, FN)
    jangkar(k, 42, FN + 1, 5, MOD + "/herba", no=12, protect=3)
    # ---------------------------------------------------------------- (1,0) dormitory, kitchen, titipan room
    asrama_biksu(k, 56, 23, FN)
    rumah_gunung(k, 56, 4, 18, 11, FN, face="south", seed=601, storeys=1, beds=0, kitchen=True, sign=True)
    K.furnish_kitchen(k, 58, 5, 70, FN, 5, "south")
    for x in range(77, 92, 2):
        K.chest(k, x, FN + 1, 6, facing="south")
    for (px, pz) in ((76, 5), (92, 5), (76, 13), (92, 13)):
        for y in range(FN + 1, FN + 5):
            K.put_ax(k, px, y, pz, "trunk")
    gable_roof(k, 76, FN + 5, 5, 92, 13, along="x", overhang=1)
    jangkar(k, 84, FN + 1, 10, MOD + "/titipan", tempat="biara", protect=6)
    jangkar(k, 90, FN + 2, 18, MOD + "/herba", no=10, protect=3)
    # ---------------------------------------------------------------- (0,1) Kolam Pelepasan + rock garden
    kolam_pelepasan(k, 4, 70, 34, 93, FS)
    for x in range(4, 42):
        for z in range(52, 63):
            k.put(x, FS, z, "minecraft:gravel" if (z + (x // 6)) % 3 else "minecraft:white_concrete_powder")
    for (bx, bz, r, h) in ((10, 56, 1, 2), (22, 58, 2, 3), (34, 55, 1, 2)):
        rock(k, bx, bz, FS, r, h, moss=0.6)
    jangkar(k, 22, FS + 1, 62, MOD + "/herba", no=11, protect=3)
    jangkar(k, 38, FS + 1, 88, MOD + "/kera", jumlah=2, protect=4)
    # the landing of the Katrol Teras (from teras_kebun below, its basket column at canvas x 38)
    for x in range(36, 41):
        k.put(x, FS + 1, 95, "minecraft:air")
        k.put(x, FS, 95, "wood_alt")
        k.put(x, FS, 94, "wood_alt")
    for x in (36, 40):
        K.post(k, x, FS + 1, 93, height=2)
    # ---------------------------------------------------------------- (1,1) Rumah Tamu Biara + teleport disc
    a = K.aula(k, 56, 70, 92, 92, h=5, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("north",), door_w=3,
               tile=True, y=FS)
    ruang(k, *a["inner"], a["f"], "inap", isi=0.5)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    for i in range(6):
        x = ix0 + 1 + i * 5
        K.bed(k, x, fl + 1, iz1 - 1, "south")
        k.titik("kasur", x, fl + 1, iz1 - 1, "north")
        K.chest(k, x + 1, fl + 1, iz1, facing="north")
        if i < 5:
            k.fill(x + 3, fl + 1, iz1 - 4, x + 3, fl + 3, iz1, "plaster")
    for x in (ix0 + 6, ix0 + 14, ix0 + 22):
        K.furnish_dining(k, x, iz0 + 3, fl, n=4)
    jangkar(k, ix0 + 3, fl + 1, iz0 + 2, "kotabesar/layanan", yaw=180.0, layanan="penginapan", peran="BIKSU_GUNUNG",
            harga=3, protect=8)
    for x in range(64, 85):
        for z in range(52, 64):
            d = math.hypot(x - 74, z - 58)
            if d <= 5.5:
                k.put(x, FS + 1, z, "minecraft:polished_andesite" if d < 4.5 else "minecraft:prismarine_bricks")
    jangkar(k, 74, FS + 2, 58, "kotabesar/cakram", protect=12)
    jangkar(k, 70, FS + 1, 66, "kota_tema/zona", tema=TEMA, distrik="biara_awan", protect=72)
    tangga(k, [(48, 94), (48, 70), (48, 56), (48, 40, FN + 1), (48, 24, FN + 1)], 90, FS + 1)
    potong(k, "biara_awan", {(0, 0): "aula_dharma", (1, 0): "asrama", (0, 1): "kolam", (1, 1): "rumah_tamu"},
           utuh=("aula_dharma",))


# ===================================================================================================== JSON
# Biomes and style tags follow the inti contract (docs/progres/kota-TK.md sec 5): the structure filter is
# #kultivator:has_structure/kota_tema/gunung and the style tags #kultivator:kota_tema/gaya/gunung_<gaya> (both written
# by gen_tempat_tema_data.py from tempat_tema_lib.GAYA_TEMA biome lists). The block swaps of the gunung styles are this
# generator's own (snow layers on the ridges, spruce for dark oak, moss in the forest) and are written into the
# processors kultivator:kota_tema/gunung/{gaya,utuh}_<gaya> named by tempat_tema_lib.proc_id.
BIOMA = "#kultivator:has_structure/kota_tema/gunung"
# block swaps per style; values starting with "!" drop the block properties (a different block family)
_KAYU = ("_planks", "_stairs", "_slab", "_fence", "_fence_gate", "_log", "_wood", "_door", "_trapdoor", "_button",
         "_pressure_plate", "_sign", "_wall_sign", "_hanging_sign", "_wall_hanging_sign", "_shelf")


def _kayu(dari, ke):
    m = {}
    for s in _KAYU:
        m["minecraft:" + dari + s] = "minecraft:" + ke + s
    m["minecraft:stripped_%s_log" % dari] = "minecraft:stripped_%s_log" % ke
    m["minecraft:stripped_%s_wood" % dari] = "minecraft:stripped_%s_wood" % ke
    return m


GAYA = {
    "batu": {},
    "salju": dict(_kayu("dark_oak", "spruce"), **{
        "minecraft:grass_block": "!minecraft:snow_block", "minecraft:podzol": "!minecraft:snow_block",
        "minecraft:moss_carpet": "!minecraft:snow", "minecraft:andesite_slab": "!minecraft:snow",
        "minecraft:short_grass": "!minecraft:air", "minecraft:azalea_leaves": "minecraft:spruce_leaves",
        "minecraft:flowering_azalea_leaves": "minecraft:spruce_leaves"}),
    "hutan": dict(_kayu("dark_oak", "spruce"), **{
        "minecraft:andesite_slab": "!minecraft:moss_carpet", "minecraft:cobblestone": "minecraft:mossy_cobblestone"}),
}
SNOW_LAYERS = {"minecraft:snow": {"layers": "2"}}
LAPUK = (
    ("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.05),
    ("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.03),
    ("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.08),
    ("minecraft:polished_andesite", "minecraft:andesite", 0.03),
    ("minecraft:white_terracotta", "minecraft:light_gray_terracotta", 0.02),
)
LAPUK_HUTAN = (("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.12),)


def processor(gaya, states, lapuk=True):
    ganti = GAYA[gaya]
    rules = []
    for bid, props in sorted(set(states)):
        to = ganti.get(bid)
        if to is None:
            continue
        inp = {"id": bid}
        if props:
            inp["properties"] = {kk: v for kk, v in props}
        if to.startswith("!"):
            out = {"id": to[1:]}
            if to[1:] in SNOW_LAYERS:
                out["properties"] = dict(SNOW_LAYERS[to[1:]])
        else:
            out = {"id": to}
            if props:
                out["properties"] = {kk: v for kk, v in props}
        rules.append({"input_predicate": {"predicate_type": "minecraft:blockstate_match", "block_state": inp},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": out})
    if lapuk:
        for src, dst, p in LAPUK + (LAPUK_HUTAN if gaya == "hutan" else ()):
            s2 = ganti.get(src, src).lstrip("!")
            d2 = ganti.get(dst, dst).lstrip("!")
            rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": s2,
                                              "probability": p},
                          "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": d2}})
    procs = []
    if rules:
        procs.append({"processor_type": "minecraft:rule", "rules": rules})
    procs.append({"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"})
    return {"processors": procs}


def proc_name(gaya, utuh):
    return ttl.proc_id(TEMA, gaya, utuh)


def write_processors():
    states = tl.semua_state()
    for gaya in GAYA:
        for utuh in (False, True):
            name = proc_name(gaya, utuh).split(":", 1)[1]
            tl.tulis_processor(name, processor(gaya, states, lapuk=not utuh))


def write_pools():
    for pid, (templates, utuh) in sorted(POOLS.items()):
        name = "%s/%s" % (F, pid)
        for gaya in GAYA:
            suffix = "" if gaya == "batu" else "_" + gaya
            tl.tulis_pool(name + suffix, [(tl.elemen(t, proc_name(gaya, utuh)), 1) for t in templates])


def write_structures():
    gaya = ttl.gaya_struktur(TEMA)
    assert sorted(a for _t, a in gaya) == sorted(g for g in GAYA if g != "batu"), gaya
    for d in ORDER:
        lots = []
        for l in LOTS[d] + wall_lots(d):
            e = {"dx": l["dx"], "dz": l["dz"], "pool": "%s%s/%s" % (NS, F, l["pool"]), "putar": l.get("putar", 0)}
            if l.get("dy"):
                e["dy"] = l["dy"]
            lots.append(e)
        s = tl.struktur_tata_letak(RENCANA, d, lots, BIOMA, gaya=gaya)
        tl.tulis_wg("structure", "%s/%s" % (F, d), s)


_WARNA = (  # (substring of the block id, rgb) - first match wins; preview only
    ("gold", (230, 190, 60)), ("water", (60, 110, 200)), ("lava", (255, 90, 0)), ("leaves", (70, 130, 60)),
    ("azalea", (90, 150, 70)), ("grass", (90, 150, 60)), ("moss", (80, 120, 60)), ("farmland", (110, 80, 50)),
    ("wheat", (200, 180, 80)), ("snow", (240, 240, 245)), ("red_terracotta", (170, 70, 50)), ("red_wool", (190, 50, 50)),
    ("yellow", (225, 200, 70)), ("orange", (220, 130, 50)), ("white_terracotta", (215, 200, 185)),
    ("calcite", (225, 225, 220)), ("quartz", (235, 230, 225)), ("white_concrete", (240, 240, 240)),
    ("dark_oak", (70, 50, 30)), ("spruce", (110, 80, 50)), ("oak", (160, 130, 80)), ("bamboo", (180, 190, 80)),
    ("deepslate", (70, 70, 75)), ("tuff", (110, 112, 100)), ("andesite", (135, 135, 135)), ("stone_brick", (125, 125, 125)),
    ("cobble", (115, 115, 115)), ("stone", (128, 128, 128)), ("gravel", (140, 130, 125)), ("dirt", (120, 85, 60)),
    ("lantern", (255, 220, 120)), ("froglight", (255, 210, 120)), ("chain", (60, 60, 70)), ("bed", (150, 90, 60)),
    ("carpet", (180, 140, 90)), ("barrel", (130, 95, 60)), ("hay", (200, 170, 60)), ("book", (140, 100, 60)),
)


def pratinjau(out_dir):
    """Top-down preview of every district canvas (4 px per block, height-shaded; anchors red, titik blue dots)."""
    import png
    os.makedirs(out_dir, exist_ok=True)
    for name, k in sorted(KANVAS.items()):
        sx, sy, sz = k.size
        top = {}
        for (x, y, z), cell in k.cells.items():
            bid = cell[0][0]
            if bid == "minecraft:air":
                continue
            if (x, z) not in top or y > top[(x, z)][0]:
                top[(x, z)] = (y, bid)
        S = 4
        img = png.Image(sx * S, sz * S, (20, 20, 24, 255))
        for (x, z), (y, bid) in top.items():
            c = (160, 160, 160)
            for key, rgb in _WARNA:
                if key in bid:
                    c = rgb
                    break
            f = 0.55 + 0.45 * min(1.0, y / 48.0)
            col = (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)
            for dx in range(S):
                for dz in range(S):
                    img.set(x * S + dx, z * S + dz, col)
        for t in k.titik_list:
            px, _py, pz = t["pos"]
            img.set(px * S + 1, pz * S + 1, (60, 140, 255, 255))
            img.set(px * S + 2, pz * S + 2, (60, 140, 255, 255))
        for (x, y, z, nbt) in k.entities:
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    xx, zz = int(x * S) + dx, int(z * S) + dz
                    if 0 <= xx < sx * S and 0 <= zz < sz * S:
                        img.set(xx, zz, (255, 40, 40, 255))
        img.save(os.path.join(out_dir, "gunung_%s.png" % name))


def bersihkan():
    """Removes this theme's generated JSON/NBT before a full run (renamed lots never linger)."""
    base = os.path.join(structgen.REPO, "src", "main", "resources", "data", "kultivator")
    for sub in (("structure", F), ("worldgen", "template_pool", F), ("worldgen", "processor_list", F),
                ("worldgen", "structure", F)):
        root = os.path.join(base, *sub)
        for dp, _dn, fn in os.walk(root):
            for f in fn:
                if f.endswith((".nbt", ".json")):
                    os.remove(os.path.join(dp, f))


BUILDERS = [("kaki_gerbang", build_kaki_gerbang), ("pasar_kaki", build_pasar_kaki), ("penginapan_peziarah", build_penginapan_peziarah), ("teras_hunian", build_teras_hunian), ("aula_herba", build_aula_herba), ("teras_kebun", build_teras_kebun), ("tebing_papan", build_tebing_papan), ("puncak_emas", build_puncak_emas), ("biara_awan", build_biara_awan)]


# =====================================================================================================================
# 38.T.6.5 story data and lang (mode --cerita, also part of a full run): quests, dialogs, rumours, advancements, loot
# tables, the schedule file and lang-src/tempat_tema_gunung.{id_id,en_us}.json (identical keys by construction).
# Java hooks: dev.kultivator.dunia.tempat.tema.gunung.GunungCerita (effects gunung_*, story marks "tanda gunung_*").
# =====================================================================================================================

LANG = {}          # key -> (id_id, en_us), insertion order kept
DIALOG = {}        # dialog id -> json
QUEST = {}         # quest id -> json
KABAR = []         # rumour templates
ADV = {}           # advancement path -> json
LOOT = {}          # loot table path -> json
CERITA_Q = "tema_gunung_tali"


def L(key, idt, en):
    assert key not in LANG, "duplicate lang key " + key
    assert idt.count("%") == en.count("%"), "placeholder mismatch " + key
    LANG[key] = (idt, en)


def G(suffix, idt, en):
    """A key of the theme: kultivator.kota_tema.gunung.<suffix>."""
    L("kultivator.kota_tema.gunung." + suffix, idt, en)


def N(nid, idt, en, gestur=None, prop=None, akibat=None, suara=None, p=None, isian=None):
    return {"id": nid, "t": None if idt is None else (idt, en), "gestur": gestur, "prop": prop, "akibat": akibat, "suara": suara, "p": p or [],
            "isian": isian}


def C(idt, en, ke=None, akibat=None, syarat=None, tampil=False, alasan=None):
    return {"t": (idt, en), "ke": ke, "akibat": akibat, "syarat": syarat, "tampil": tampil, "alasan": alasan}


def dialog(did, pembicara, simpul, syarat=None, prioritas=0, sekali=False):
    assert did.startswith("tema_gunung_"), did
    assert did not in DIALOG, did
    ids = {n["id"] for n in simpul}
    nodes = []
    for n in simpul:
        node = {"id": n["id"]}
        if n["t"] is None:
            node["teks"] = "kabar"          # the speaker tells a live rumour of the place (KabarTempat)
        else:
            L("kultivator.cerita.%s.%s" % (did, n["id"]), *n["t"])
        for k in ("isian", "gestur", "prop", "suara", "akibat"):
            if n.get(k):
                node[k] = n[k]
        if n["p"]:
            ch = []
            for i, c in enumerate(n["p"], 1):
                L("kultivator.cerita.%s.%s.%d" % (did, n["id"], i), *c["t"])
                cj = {}
                if c["syarat"]:
                    cj["syarat"] = c["syarat"]
                if c["akibat"]:
                    cj["akibat"] = c["akibat"]
                if c["ke"]:
                    assert c["ke"] in ids, (did, c["ke"])
                    cj["ke"] = c["ke"]
                if c["tampil"]:
                    cj["tampil"] = True
                if c["alasan"]:
                    cj["alasan"] = c["alasan"]
                ch.append(cj)
            node["pilihan"] = ch
        nodes.append(node)
    obj = {"pembicara": pembicara if isinstance(pembicara, list) else [pembicara]}
    if prioritas:
        obj["prioritas"] = prioritas
    if syarat:
        obj["syarat"] = syarat
    if sekali:
        obj["sekali"] = True
    obj["mulai"] = simpul[0]["id"]
    obj["simpul"] = nodes
    DIALOG[did] = obj


def S(sid, idt, en, tujuan=None, akibat_masuk=None, akibat=None, cabang=None, ke=None, hasil=None, arah=None):
    return {"id": sid, "t": (idt, en), "tujuan": tujuan, "akibat_masuk": akibat_masuk, "akibat": akibat,
            "cabang": cabang, "ke": ke, "hasil": hasil, "arah": arah}


def quest(qid, pemberi, nama, ringkas, tahap, tempat="kota_tema_gunung"):
    assert qid not in QUEST, qid
    L("kultivator.cerita.quest.%s.nama" % qid, *nama)
    L("kultivator.cerita.quest.%s.ringkas" % qid, *ringkas)
    ids = {t["id"] for t in tahap} | {"selesai", "gagal"}
    out = []
    for t in tahap:
        L("kultivator.cerita.quest.%s.%s" % (qid, t["id"]), *t["t"])
        if t["hasil"]:
            L("kultivator.cerita.quest.%s.hasil.%s" % (qid, t["id"]), *t["hasil"])
        sj = {"id": t["id"]}
        for k in ("tujuan", "akibat_masuk", "akibat"):
            if t[k]:
                sj[k] = t[k]
        if t["cabang"]:
            for c in t["cabang"]:
                assert c["ke"] in ids, (qid, c["ke"])
            sj["cabang"] = t["cabang"]
        if t["ke"]:
            assert t["ke"] in ids, (qid, t["ke"])
            sj["ke"] = t["ke"]
        if t["arah"]:
            sj["arah"] = t["arah"]
        out.append(sj)
    QUEST[qid] = {"pemberi": pemberi, "tempat": tempat, "tahap": out}


def kabar(kid, pemicu, idt, en, bobot=10, umur=3, penyebar=None):
    assert kid.startswith("tema_gunung_"), kid
    L("kultivator.cerita.kabar." + kid, idt, en)
    t = {"id": kid, "pemicu": pemicu, "bobot": bobot, "umur_hari": umur}
    if penyebar:
        t["peran_penyebar"] = penyebar
    KABAR.append(t)


def tahap_q(q, stage):
    return "quest %s tahap %s" % (q, stage)


# ------------------------------------------------------------------------------------------------ system lines (Java)

def lang_sistem():
    G("acara.cahaya", "Fajar Cahaya Budha! Lingkaran pelangi terbit di lautan awan di bawah Aula Emas; berdirilah di teras pandang.",
      "Buddha's Light at dawn! A rainbow halo rises on the sea of clouds below the Golden Hall; stand on the viewing terrace.")
    G("acara.cahaya.restu", "Restu Cahaya", "Blessing of Light")
    G("acara.cahaya.restu.sub", "Bayanganmu berdiri di tengah lingkaran cahaya. Pemahaman Dao +1.",
      "Your shadow stands inside the ring of light. Dao comprehension +1.")
    G("acara.cahaya.sudah", "Cahaya itu tetap indah, tetapi restunya sudah kau terima pekan ini.",
      "The light is as beautiful as ever, but you already received its blessing this week.")
    G("acara.lonceng", "Lonceng Awan berdentang seratus delapan kali; kabut di lereng ikut bergetar.",
      "The Cloud Bell tolls one hundred and eight times; even the mist on the slopes trembles.")
    G("acara.longsor", "Longsor! Batu-batu menggelinding dari tebing; jalan papan dan tangga atas ditutup rantai.",
      "Rockslide! Stones tumble from the cliff; the plank walk and the upper stairs are chained shut.")
    G("acara.ziarah.mulai", "Musim Ziarah dimulai di %s: barisan peziarah mendaki dengan dupa di tangan.",
      "The Pilgrimage Season begins at %s: long lines of pilgrims climb with incense in hand.")
    G("bela.gou_menyerah", "Cukup! Cukup... Cakar Besi menyerah. Jangan pukul lagi.", "Enough! Enough... Iron Claw yields. No more blows.")
    G("bela.judul", "Stasiun Diserbu", "The Station Is Stormed")
    G("bela.kera_bantu", "Kera Putih Tua melompat dari atap stasiun dan berdiri di sisimu.",
      "The Old White Ape leaps from the station roof and stands at your side.")
    G("bela.menyerah", "Gou Tie berlutut di antara gulungan tali. Bicaralah dengannya untuk memutuskan nasibnya.",
      "Gou Tie kneels among the coils of rope. Talk to him to decide his fate.")
    G("bela.sub", "Gou Tie dan para pemburunya datang saat tali baru dipasang", "Gou Tie and his hunters strike as the new rope is fitted")
    for i, (a, b) in enumerate((("menunggu di bawah", "waiting below"), ("naik", "climbing"), ("menunggu di atas", "waiting above"),
                                ("turun", "descending"))):
        G("cmd.fase.%d" % i, a, b)
    G("cmd.gagal", "uji tema lift %s: tidak bisa sekarang.", "test theme lift %s: not possible right now.")
    G("cmd.ok", "uji tema lift %s: dilakukan.", "test theme lift %s: done.")
    G("cmd.stasiun", "Jarak stasiun bawah %s blok, atas %s blok · bawah (%s, %s, %s) · atas (%s, %s, %s)",
      "Lower station %s blocks away, upper %s blocks · lower (%s, %s, %s) · upper (%s, %s, %s)")
    G("cmd.status", "%s: fase %s, kabin %s, penumpang %s/%s, macet %s, kabin ada: %s",
      "%s: phase %s, cabin %s, riders %s/%s, stuck %s, cabin present: %s")
    G("cmd.tidak_ada", "Tidak ada Kota Gunung dalam 320 blok.", "No Mountain Town within 320 blocks.")
    G("cmd.ziarah", "Ziarah: keadaan %s, bordes menyala %s/9, Restu tersisa %s hari, jeda %s hari, poin pemetik %s",
      "Pilgrimage: state %s, landings lit %s/9, Blessing left %s days, cooldown %s days, picker points %s")
    G("gelar.penjaga_tali_awan", "Penjaga Tali Awan", "Keeper of the Cloud Rope")
    G("gelar.penjaga_tali_awan.sub", "Kereta gantung gratis seumur hidup; Lonceng Awan mengingat namamu.",
      "The cable car is free for life; the Cloud Bell remembers your name.")
    L("kultivator.cerita.gelar.penjaga_tali_awan", "Penjaga Tali Awan", "Keeper of the Cloud Rope")
    L("kultivator.cerita.tempat.kota_tema_gunung", "Kota Gunung", "Mountain Town")
    G("herba.beli", "Membeli %s seharga %s NB.", "Bought %s for %s NB.")
    G("herba.habis", "Persediaan itu sudah habis untuk hari ini.", "That stock is sold out for today.")
    G("herba.jual", "Menjual %s seharga %s NB.", "Sold %s for %s NB.")
    G("herba.langka", "Rumput Awan Seribu Tahun! %s, umur %s tahun.", "Thousand-Year Cloud Grass! %s, %s years old.")
    G("herba.nilai", "Nenek Yao menimbang %s di telapak tangannya: umurnya %s tahun.",
      "Granny Yao weighs %s in her palm: it is %s years old.")
    G("herba.nilai.kosong", "Pegang herba di tangan utama untuk dinilai.", "Hold a herb in your main hand to have it appraised.")
    G("herba.pangkat", "Ceruk ini hanya untuk Pemetik Tebing pangkat %s ke atas.", "This niche is only for Cliff Pickers of rank %s or higher.")
    G("herba.petik", "Memetik %s, umur %s tahun.", "Picked %s, %s years old.")
    G("herba.tali_panjat", "Tali Panjat terikat di pinggang: jatuh berikutnya di tebing kota ini tidak melukai.",
      "Climbing Rope tied at the waist: your next fall on this town's cliffs will not hurt.")
    G("herba.tali_selamat", "Tali Panjat menahanmu! Talinya putus, tetapi kau selamat.", "The Climbing Rope catches you! It snaps, but you are safe.")
    G("jaga.datang", "Tiga sosok berkerudung keluar dari kabut, gergaji roh berkilau biru di tangan mereka!",
      "Three hooded figures step out of the mist, spirit saws glowing blue in their hands!")
    G("jaga.kabur", "Para penggergaji kabur ke arah tebing. Jejak kaki mereka masih basah di papan.",
      "The sawyers flee toward the cliffs. Their footprints are still wet on the planks.")
    G("jaga.ke_pos", "Berdirilah di pos jaga Tiang Tengah.", "Stand at the watch post of the Middle Pylon.")
    G("jaga.malam", "Para penyabot datang malam hari. Kembalilah ke Tiang Tengah setelah matahari terbenam.",
      "The saboteurs come at night. Return to the Middle Pylon after sunset.")
    G("jaga.mulai", "Kau berjaga di Tiang Tengah. Angin bersiul di antara tali besi...", "You keep watch at the Middle Pylon. The wind whistles through the iron cables...")
    G("jaga.tangkap", "Satu penggergaji tertangkap hidup-hidup. Paman Tie Suo pasti ingin bicara dengannya.",
      "One sawyer is caught alive. Uncle Tie Suo will want a word with him.")
    G("jerat.bukan", "Jerat roh berdenyut samar. Jangan disentuh sebelum tahu siapa pemasangnya.",
      "A spirit snare pulses faintly. Better not touch it before you know who set it.")
    G("jerat.lepas", "Jerat roh dilepas (%s/%s).", "Spirit snare released (%s/%s).")
    G("katrol.berjalan", "Katrol sedang berjalan.", "The pulley is moving.")
    G("katrol.naik", "Penjaga memutar engkol; keranjang katrol naik.", "The keeper turns the crank; the pulley basket rises.")
    G("katrol.turun", "Penjaga melepas rem; keranjang katrol turun pelan.", "The keeper eases the brake; the pulley basket sinks slowly.")
    G("katrol.penuh", "Keranjang sudah penuh (dua orang).", "The basket is full (two people).")
    G("kera.biksu_marah", "Para biksu memandangmu dengan marah: membunuh kera di wilayah biara. Nama Baik -3.",
      "The monks glare at you: killing a monkey on monastery ground. Good Name -3.")
    G("kera.rebut", "Seekor kera merebut %s dari tanganmu dan kabur ke tebing!", "A monkey snatches %s from your hand and flees to the cliff!")
    G("kera_putih.minta", "Kera Putih Tua menunjuk keranjang buah yang kosong: ia meminta %s buah sebagai tanda hormat.",
      "The Old White Ape points at an empty fruit basket: it asks for %s fruits as a sign of respect.")
    G("kera_putih.muncul", "Kabut turun tebal. Dari dalamnya melangkah seekor kera putih besar, tua dan diam, lalu menunjuk ke Katrol Tebing.",
      "The mist thickens. Out of it steps a great white ape, old and silent, and it points at the Cliff Pulley.")
    G("kera_putih.paksa", "Kau merenggut serat itu dari sarang. Kera Putih Tua menjerit; mulai sekarang ia memusuhimu.",
      "You tear the fibre from the nest. The Old White Ape screams; from now on it is your enemy.")
    G("kera_putih.sarang", "Di ceruk tersembunyi, di antara akar tebing, tergantung gulungan serat perak: Tali Sutra Kera.",
      "In a hidden niche among the cliff roots hangs a coil of silver fibre: the Ape Silk Rope.")
    G("kera_putih.sopan", "Kera Putih Tua menerima buahmu, membungkuk, lalu menyerahkan gulungan Tali Sutra Kera.",
      "The Old White Ape accepts your fruit, bows, and hands you the coil of Ape Silk Rope.")
    G("kuli.ambil", "Menerima %s titipan.", "Received %s deliveries.")
    G("kuli.belum", "Belum ada titipanmu yang tiba di sini.", "None of your deliveries have arrived here yet.")
    G("kuli.kosong", "Pegang barang yang mau dititipkan di tangan utama.", "Hold the goods you want carried in your main hand.")
    G("kuli.muatan", "Muatan Kuli", "Porter's Load")
    G("kuli.muatan_jatuh", "Muatanmu jatuh ke jurang! Kuli tidak terbang dan tidak menunggang.",
      "Your load tumbles into the gorge! Porters neither fly nor ride.")
    G("kuli.muatan_tiba", "Muatan diserahkan di ruang titipan.", "Load handed over at the delivery room.")
    G("kuli.penuh", "Para kuli sudah membawa 27 titipanmu. Ambil dulu yang sudah tiba.",
      "The porters already carry 27 of your parcels. Collect the ones that arrived first.")
    G("kuli.sudah_tiba", "%s titipanmu sudah tiba di sini; mintalah pada penjaga ruang titipan.",
      "%s of your deliveries have arrived here; ask the keeper of the delivery room.")
    G("kuli.titip", "Seorang kuli mengikat barangmu di rangka punggung dan mulai mendaki ke %s.",
      "A porter straps your goods to his back frame and starts climbing to %s.")
    G("kuli.tujuan.biara", "Biara Awan", "the Cloud Monastery")
    G("kuli.tujuan.aula_emas", "Aula Emas", "the Golden Hall")
    G("kuli.uang", "Ongkos kuli 2 NB; uangmu tidak cukup.", "The porter's fee is 2 NB; you cannot afford it.")
    G("kuli.wadah", "Kuli tidak membawa wadah yang berisi.", "The porters do not carry filled containers.")
    G("layar.herba", "Aula Herba Gunung", "Mountain Herb Hall")
    G("layar.herba.catatan", "Beli, jual dan nilai herba tebing", "Buy, sell and appraise cliff herbs")
    G("layar.lift", "Kereta Gantung Tali Besi", "Iron Cable Car")
    G("layar.lift.catatan", "Jadwal, ongkos dan kursi kosong", "Timetable, fare and free seats")
    G("layar.ziarah", "Ziarah Seribu Anak Tangga", "Pilgrimage of a Thousand Steps")
    G("layar.ziarah.catatan", "Daftar, sembilan bordes, Restu Puncak", "Register, nine landings, Summit Blessing")
    G("lift.berjalan", "Kabin sedang berjalan; tunggu di stasiun.", "The cabin is under way; wait at the station.")
    G("lift.dekati", "Dekati loket stasiun kereta gantung.", "Go up to the cable car ticket window.")
    G("lift.lanjut", "Tali bergetar, roda berputar lagi: kabin melanjutkan perjalanan.", "The cable shudders, the wheel turns again: the cabin moves on.")
    G("lift.macet", "Kabin Berhenti!", "The Cabin Stops!")
    G("lift.macet.sub", "Kabin berayun di tengah bentang, jauh di atas jurang", "The cabin sways mid-span, high above the gorge")
    G("lift.macet.tie_suo", "Paman Tie Suo berteriak dari tiang: \"Jangan panik! Aku turunkan tali!\"",
      "Uncle Tie Suo shouts from the pylon: \"Don't panic! I'm lowering a rope!\"")
    G("lift.naik", "Kabin naik ke Puncak Emas. Lihatlah lautan awan di bawah.", "The cabin climbs to the Golden Summit. Look at the sea of clouds below.")
    G("lift.penuh", "Kabin penuh (enam penumpang).", "The cabin is full (six riders).")
    G("lift.petir", "Badai petir: kereta gantung berhenti sampai langit tenang.", "Thunderstorm: the cable car waits until the sky calms.")
    G("lift.tali", "Pegang tali dan turun pelan-pelan; Tie Suo menahanmu dari atas.", "Grip the rope and climb down slowly; Tie Suo holds you from above.")
    G("lift.terkunci", "Pintu kabin terkunci selama berjalan.", "The cabin door stays locked while moving.")
    G("lift.tiba_atas", "Tiba di Stasiun Atas, Puncak Emas.", "Arrived at the Upper Station, Golden Summit.")
    G("lift.tiba_bawah", "Tiba di Stasiun Bawah, Gerbang Kaki.", "Arrived at the Lower Station, Foot Gate.")
    G("lift.tunggu", "Kabin berikutnya berangkat sebentar lagi.", "The next cabin leaves shortly.")
    G("lift.turun", "Kabin turun ke Gerbang Kaki.", "The cabin descends to the Foot Gate.")
    G("lift.tutup", "Kereta gantung hanya jalan pukul 05.00 sampai 18.00.", "The cable car only runs from 05:00 to 18:00.")
    G("lift.uang", "Ongkos naik 1 NB; uangmu tidak cukup.", "The fare up is 1 NB; you cannot afford it.")
    G("musuh.pemburu", "Pemburu Herba Liar", "Wild Herb Hunter")
    G("musuh.penggergaji", "Penggergaji Tali", "Rope Sawyer")
    G("palang.tutup", "Jalan ditutup rantai. Tunggu sampai hujan atau longsor reda.", "The way is chained shut. Wait until the rain or the rockslide passes.")
    G("upacara", "Lonceng Awan berdentang, Cahaya Budha muncul di awan, dan tali baru berkilau perak. Kereta gantung kini gratis untukmu selamanya.",
      "The Cloud Bell tolls, the Buddha's Light appears in the clouds and the new rope shines silver. The cable car is now free for you forever.")
    for k, a, b in (("terbang", "Ziarah batal: kau terbang.", "Pilgrimage void: you flew."),
                    ("kereta", "Ziarah batal: kau naik kereta gantung.", "Pilgrimage void: you rode the cable car."),
                    ("katrol", "Ziarah batal: kau naik katrol.", "Pilgrimage void: you rode a pulley."),
                    ("teleport", "Ziarah batal: kau berpindah tempat dengan ilmu.", "Pilgrimage void: you teleported."),
                    ("waktu", "Ziarah batal: hari sudah berganti sebelum kau tiba.", "Pilgrimage void: the day ended before you arrived."),
                    ("pergi", "Ziarah batal: kau meninggalkan jalan gunung.", "Pilgrimage void: you left the mountain road."),
                    ("berhenti", "Kau menghentikan ziarah. Kuil Kaki selalu terbuka untuk mulai lagi.",
                     "You stopped the pilgrimage. The Foot Temple is always open to begin again.")):
        G("ziarah.batal." + k, a, b)
    for i, (a, b) in enumerate((("Bordes Akar Pinus", "Pine Root Landing"), ("Bordes Batu Bersila", "Cross-Legged Stone Landing"),
                                ("Bordes Mata Air Dingin", "Cold Spring Landing"), ("Bordes Gapura Merah", "Red Gate Landing"),
                                ("Bordes Kera Berbisik", "Whispering Monkey Landing"), ("Bordes Kabut Pertama", "First Mist Landing"),
                                ("Bordes Seribu Napas", "Thousand Breaths Landing"), ("Bordes Lonceng Jauh", "Distant Bell Landing"),
                                ("Bordes Gerbang Emas", "Golden Gate Landing")), 1):
        G("ziarah.bordes.%d" % i, a, b)
    G("ziarah.daftar", "Namamu ditulis di buku ziarah. Daki tanpa terbang, tanpa kereta gantung, tanpa katrol.",
      "Your name is written in the pilgrims' book. Climb without flying, without the cable car, without pulleys.")
    G("ziarah.jauh", "Kau terlalu jauh dari jalan ziarah; kembalilah ke tangga.", "You strayed too far from the pilgrim road; return to the stairs.")
    G("ziarah.kurang", "Masih %s bordes yang dupanya belum kau nyalakan.", "%s landings still wait for your incense.")
    G("ziarah.mulai", "Ziarah Seribu Anak Tangga", "Pilgrimage of a Thousand Steps")
    G("ziarah.mulai.sub", "Nyalakan dupa di sembilan bordes dan tiba di Aula Emas sebelum hari berganti",
      "Light incense at nine landings and reach the Golden Hall before the day ends")
    G("ziarah.nyala", "Dupa menyala di bordes %s (%s/%s).", "Incense burns at landing %s (%s/%s).")
    G("ziarah.restu", "Restu Puncak: tujuh hari meditasimu di Aula Emas lebih dalam.", "Summit Blessing: for seven days your meditation at the Golden Hall runs deeper.")
    G("ziarah.restu_hadiah", "Kepala Biara memberimu satu Restu Puncak tanpa pendakian.", "The Abbot grants you one Summit Blessing without the climb.")
    G("ziarah.restu_jeda", "Ziarah selesai. Restu Puncak berikutnya dapat diterima %s hari lagi.",
      "Pilgrimage complete. The next Summit Blessing can be received in %s days.")
    G("ziarah.sedang", "Kau sudah sedang berziarah.", "You are already on a pilgrimage.")
    G("ziarah.sudah", "Dupa di bordes %s sudah menyala.", "The incense at landing %s already burns.")
    G("ziarah.tiba", "Tiba di Aula Emas", "Arrived at the Golden Hall")
    for tid_, nama, gelar in (("jue_yuan", ("Jue Yuan", "Jue Yuan"), ("Kepala Biara", "Abbot")),
                              ("tie_suo", ("Paman Tie Suo", "Uncle Tie Suo"), ("Penjaga Kereta Gantung", "Cable Car Keeper")),
                              ("shan_ling", ("Shan Ling", "Shan Ling"), ("Pemetik Tebing", "Cliff Picker")),
                              ("lao_dan", ("Lao Dan", "Lao Dan"), ("Kuli Tua", "Old Porter")),
                              ("pei_wenzhi", ("Pei Wenzhi", "Pei Wenzhi"), ("Sastrawan Peziarah", "Pilgrim Scholar")),
                              ("jing_yue", ("Biksuni Jing Yue", "Nun Jing Yue"), ("Penjaga Aula Emas", "Keeper of the Golden Hall")),
                              ("gou_tie", ("Gou Tie", "Gou Tie"), ("Cakar Besi", "Iron Claw")),
                              ("yao_po", ("Nenek Yao", "Granny Yao"), ("Penilai Herba", "Herb Appraiser"))):
        G("tokoh." + tid_, *nama)
        G("tokoh.%s.gelar" % tid_, *gelar)


# ------------------------------------------------------------------------------------------------ chain "Tali yang Terakhir"

def cerita_tali():
    Q = CERITA_Q
    quest(Q, "tokoh:tie_suo", ("Tali yang Terakhir", "The Last Rope"),
          ("Kereta gantung Paman Tie Suo macet di tengah jurang, dan talinya tidak aus: talinya dipotong.",
           "Uncle Tie Suo's cable car stalled above the gorge, and the cable did not wear through: it was cut."), [
        S("macet", "Kabin kereta gantung berhenti di tengah bentang. Turun dengan tali yang dijatuhkan Tie Suo.",
          "The cable car cabin stopped mid-span. Climb down the rope Tie Suo lowers.",
          tujuan=["tanda gunung_lift_macet"]),
        S("tali_putus", "Temui Paman Tie Suo di Stasiun Bawah; ia sedang memeriksa ujung tali besi.",
          "Meet Uncle Tie Suo at the Lower Station; he is examining the end of the iron cable.",
          akibat=["nama_baik 2"]),
        S("lao_dan", "Lao Dan di Pos Kuli melihat sesuatu di Tiang Tengah malam-malam. Tanyai dia.",
          "Lao Dan at the Porters' Post saw something at the Middle Pylon at night. Ask him."),
        S("jaga", "Berjaga semalam di pos Tiang Tengah (Aula Herba) dan hadapi siapa pun yang datang dengan gergaji.",
          "Keep watch one night at the Middle Pylon post (Herb Hall) and face whoever comes with a saw.",
          tujuan=["tanda gunung_jaga_selesai"], akibat=["beri_nb_pita 40"],
          cabang=[{"syarat": ["bendera gunung_tangkap"], "ke": "interogasi"}], ke="jejak"),
        S("interogasi", "Seorang penggergaji tertangkap. Dengarkan pengakuannya bersama Tie Suo.",
          "One sawyer was caught. Hear his confession with Tie Suo.", ke="shan_ling"),
        S("jejak", "Para penggergaji kabur ke tebing. Tanyakan jejak mereka pada Tie Suo.",
          "The sawyers fled to the cliffs. Ask Tie Suo about their tracks."),
        S("shan_ling", "Shan Ling, pemetik tebing, hafal setiap ceruk. Cari dia di Jalan Papan Tebing.",
          "Shan Ling the cliff picker knows every niche. Find her on the Cliff Plank Walk."),
        S("jerat", "Lepaskan enam jerat roh Gou Tie di ceruk-ceruk Jalan Papan Tebing.",
          "Release Gou Tie's six spirit snares in the niches of the Cliff Plank Walk.",
          tujuan=["tanda gunung_jerat x6"], akibat=["gunung_herba 3"]),
        S("kera_putih", "Kera Putih Tua menunggu. Naik Katrol Tebing ke sarangnya dan dapatkan Tali Sutra Kera.",
          "The Old White Ape is waiting. Ride the Cliff Pulley to its nest and obtain the Ape Silk Rope.",
          akibat_masuk=["gunung_kera_putih"], tujuan=["tanda gunung_tali_sutra"]),
        S("pasang", "Bawa Tali Sutra Kera kepada Tie Suo di Stasiun Bawah.", "Bring the Ape Silk Rope to Tie Suo at the Lower Station."),
        S("bela", "Tie Suo memasang tali baru. Pertahankan Stasiun Bawah dari Gou Tie dan para pemburunya.",
          "Tie Suo is fitting the new rope. Defend the Lower Station from Gou Tie and his hunters.",
          tujuan=["tanda gunung_bela_selesai"],
          cabang=[{"syarat": ["bendera gunung_gou_suap"], "ke": "berkat_suap"},
                  {"syarat": ["bendera gunung_paksa"], "ke": "berkat_paksa"}], ke="berkat"),
        S("berkat", "Kepala Biara Jue Yuan menunggumu di Biara Awan untuk memberkati tali.",
          "Abbot Jue Yuan awaits you at the Cloud Monastery to bless the rope.", ke="selesai",
          hasil=("Tali baru diberkati. Kabin pertama naik membawa Shan Ling, Lao Dan dan Tie Suo yang menangis tanpa malu. Di kota ini, "
                 "namamu kini disebut bersama nama tali itu.",
                 "The new rope is blessed. The first cabin rises carrying Shan Ling, Lao Dan and Tie Suo, who weeps without shame. In this "
                 "town your name is now spoken together with the rope's.")),
        S("berkat_paksa", "Kepala Biara Jue Yuan menunggumu di Biara Awan. Shan Ling tidak ikut.",
          "Abbot Jue Yuan awaits you at the Cloud Monastery. Shan Ling does not come.", ke="selesai",
          hasil=("Tali itu kuat, tetapi tiap kali angin menggesek seratnya, Shan Ling memalingkan muka. Kera Putih Tua tidak pernah "
                 "terlihat lagi di tebing.",
                 "The rope is strong, but whenever the wind rubs its fibres Shan Ling looks away. The Old White Ape is never seen on the "
                 "cliffs again.")),
        S("berkat_suap", "Kepala Biara Jue Yuan memanggilmu ke Biara Awan. Kabarnya Shan Ling sudah meninggalkan kota.",
          "Abbot Jue Yuan calls you to the Cloud Monastery. Word is that Shan Ling has left town.", ke="selesai",
          hasil=("Kereta gantung berjalan lagi dan kantongmu berat oleh emas Gou Tie. Harga herba di Aula Herba naik seperlima, "
                 "dan rumah kecil Shan Ling di tebing kini kosong.",
                 "The cable car runs again and your purse is heavy with Gou Tie's gold. Herb prices at the Herb Hall rose by a fifth, "
                 "and Shan Ling's little house on the cliff stands empty.")),
    ])

    # -- 1: the cut cable
    dialog("tema_gunung_tali_putus", "tokoh:tie_suo", [
        N("mulai", "Kau turun dengan selamat? Bagus, bagus. Sekarang lihat ini. Lihat! Tali besi dua puluh tahun, dan ujungnya rata seperti "
                   "dipotong pisau tahu. Ini bukan aus. Ini gergaji.",
          "You got down safe? Good, good. Now look at this. Look! Twenty years of iron cable, and the end is as flat as tofu under a knife. "
          "That's not wear. That's a saw.", gestur="marah_tunjuk", prop="tali", p=[
              C("Siapa yang mau merusak kereta gantungmu?", "Who would want to ruin your cable car?", ke="siapa"),
              C("Kau yakin? Mungkin karat di dalam.", "Are you sure? Maybe rust inside.", ke="karat")]),
        N("karat", "Karat? Aku meminyaki tali ini tiap pagi sebelum lonceng subuh selama dua puluh tahun! Karat tidak meninggalkan bekas "
                   "gigi biru berkilau. Ini gergaji roh. Barang mahal.",
          "Rust? I've oiled this cable every morning before the dawn bell for twenty years! Rust doesn't leave glittering blue tooth "
          "marks. This is a spirit saw. Expensive stuff.", gestur="berdebat", p=[
              C("Lalu siapa yang mampu membelinya?", "Then who can afford one?", ke="siapa")]),
        N("siapa", "Para biksu bilang keretaku jalan malas, tapi mereka tak pernah memotong apa-apa selain lobak. Pedagang? Aku masih "
                   "berutang pada separuh dari mereka; mana mungkin mereka bunuh orang yang berutang. Lao Dan di Pos Kuli tidur paling "
                   "larut. Tanya dia apa yang dilihatnya.",
          "The monks call my car the lazy road, but they've never cut anything but radishes. The merchants? I owe half of them money; "
          "nobody kills a man who owes them. Lao Dan at the Porters' Post sleeps the latest. Ask him what he saw.",
          gestur="sikap_lipat_tangan", p=[
              C("Aku akan bicara dengan Lao Dan.", "I'll talk to Lao Dan.", akibat=["quest_maju tema_gunung_tali"])]),
    ], syarat=[tahap_q(Q, "tali_putus")], prioritas=60)

    # -- 2: Lao Dan and the watch
    dialog("tema_gunung_tali_lao_dan", "tokoh:lao_dan", [
        N("mulai", "Tie Suo mengirimmu? Heh. Kakiku sudah tua tapi mataku belum. Tiga malam lalu aku turun minum air di dekat Tiang Tengah. "
                   "Ada cahaya biru kecil di atas menara, bergoyang seperti kunang-kunang yang mabuk. Kunang-kunang tidak menggergaji besi.",
          "Tie Suo sent you? Heh. My legs are old but my eyes aren't. Three nights ago I went down for water near the Middle Pylon. A "
          "little blue light up on the tower, swaying like a drunk firefly. Fireflies don't saw iron.", gestur="cerita", prop="pikulan", p=[
              C("Mereka akan kembali?", "Will they come back?", ke="kembali"),
              C("Kenapa tidak kau laporkan?", "Why didn't you report it?", ke="lapor")]),
        N("lapor", "Kepada siapa? Kuli melapor, lalu kuli disuruh berjaga, lalu kuli tidak tidur, lalu besok kuli jatuh dari tangga dengan "
                   "dua karung beras. Kuli belajar diam. Tapi kau bukan kuli.",
          "To whom? A porter reports, then the porter is told to keep watch, then the porter doesn't sleep, then tomorrow the porter "
          "falls down the stairs with two sacks of rice. Porters learn to be quiet. But you're no porter.", gestur="tertawa", p=[
              C("Baiklah, aku yang berjaga.", "Fine, I'll keep watch.", ke="kembali")]),
        N("kembali", "Tali yang separuh putus belum putus. Mereka harus menyelesaikan pekerjaannya. Berjagalah di pos Tiang Tengah setelah "
                     "matahari terbenam. Kalau mereka datang, tangkap satu. Orang yang tertangkap biasanya lebih suka bicara daripada jatuh.",
          "A cable half cut isn't cut yet. They have to finish the job. Keep watch at the Middle Pylon post after sunset. If they "
          "come, catch one. A man who's caught usually prefers talking to falling.", gestur="tunjuk", p=[
              C("Aku akan berjaga malam ini.", "I'll keep watch tonight.", akibat=["quest_maju tema_gunung_tali"])]),
    ], syarat=[tahap_q(Q, "lao_dan")], prioritas=60)
    dialog("tema_gunung_tali_cari_lao_dan", "tokoh:tie_suo", [
        N("mulai", "Lao Dan, di Pos Kuli, sebelah timur stasiun. Kakek dengan kaki seperti tiang besi. Jangan tawari dia minum arak; dia "
                   "akan bercerita sampai pagi.",
          "Lao Dan, at the Porters' Post, east of the station. The old man with legs like iron posts. Don't offer him wine; he'll "
          "talk until morning.", gestur="tunjuk"),
    ], syarat=[tahap_q(Q, "lao_dan")], prioritas=40)
    dialog("tema_gunung_tali_jaga_ingat", ["tokoh:tie_suo", "tokoh:lao_dan"], [
        N("mulai", "Tiang Tengah, setelah matahari terbenam. Berdiri di pos jaga di kakinya dan jangan tertidur. Angin di sana bisa "
                   "menyanyikan lagu tidur paling kejam.",
          "The Middle Pylon, after sunset. Stand at the watch post at its foot and don't fall asleep. The wind up there sings the "
          "cruellest lullabies.", gestur="tunjuk"),
    ], syarat=[tahap_q(Q, "jaga")], prioritas=40)

    # -- 2b: the confession / the tracks
    dialog("tema_gunung_tali_interogasi", "tokoh:tie_suo", [
        N("mulai", "Bocah ini menangis seperti kucing tercebur sumur. Katakan lagi, keras-keras, siapa yang membayarmu!",
          "This boy is crying like a cat dropped down a well. Say it again, loudly, who paid you!", gestur="marah_hentak", p=[
              C("(Dengarkan pengakuannya.)", "(Hear his confession.)", ke="aku")]),
        N("aku", "\"Gou Tie... Cakar Besi. Dia bayar tiga keping emas. Katanya kalau kereta gantung jatuh, kuli harus pakai orangnya, dan "
                 "pemetik takut naik ke tebing, jadi herba tebing semua miliknya.\" ... Kau dengar? Herba! Semua ini demi rumput!",
          "\"Gou Tie... Iron Claw. He paid three gold pieces. Said if the cable car falls, the porters must use his men, and the "
          "pickers will be too scared to climb, so all the cliff herbs are his.\" ... You hear that? Herbs! All this for grass!",
          gestur="berdebat", p=[
              C("Siapa yang tahu tebing sebaik Gou Tie?", "Who knows the cliffs as well as Gou Tie?", ke="shan"),
              C("Lepaskan bocah ini; dia cuma alat.", "Let the boy go; he's only a tool.", ke="lepas")]),
        N("lepas", "Hmph. Kau lebih lembut dari biksu. Baiklah, biar dia memikul batu untuk biara sebulan. Itu lebih menakutkan dari penjara.",
          "Hmph. You're softer than a monk. Fine, he can carry stones for the monastery for a month. That's scarier than prison.",
          gestur="sikap_lipat_tangan", akibat=["nama_baik 1"], p=[
              C("Siapa yang tahu tebing sebaik Gou Tie?", "Who knows the cliffs as well as Gou Tie?", ke="shan")]),
        N("shan", "Shan Ling. Gadis pemetik itu hafal setiap ceruk dan bertengkar dengan setiap kera. Kalau Gou Tie menyiapkan sesuatu di "
                  "tebing, dia yang pertama tahu. Cari dia di Jalan Papan Tebing.",
          "Shan Ling. That picker girl knows every niche and quarrels with every monkey. If Gou Tie is setting something up on the "
          "cliffs, she'll know first. Find her on the Cliff Plank Walk.", gestur="tunjuk", p=[
              C("Aku akan menemui Shan Ling.", "I'll go see Shan Ling.", akibat=["quest_maju tema_gunung_tali"])]),
    ], syarat=[tahap_q(Q, "interogasi")], prioritas=60)
    dialog("tema_gunung_tali_jejak", "tokoh:tie_suo", [
        N("mulai", "Kabur? Ke tebing? Hah, bodoh. Tebing itu jalan buntu kecuali kau kera atau Shan Ling. Jejak kaki basah, katamu? Embun "
                   "hanya menempel di papan Jalan Papan Tebing sebelum fajar.",
          "Fled? To the cliffs? Hah, fools. The cliffs are a dead end unless you're a monkey or Shan Ling. Wet footprints, you say? Dew "
          "only clings to the boards of the Cliff Plank Walk before dawn.", gestur="tertawa", p=[
              C("Siapa Shan Ling?", "Who is Shan Ling?", ke="shan")]),
        N("shan", "Pemetik herba, umurnya baru tujuh belas tapi lidahnya sudah seratus tahun. Dia hafal setiap ceruk. Kalau ada orang asing "
                  "di tebing, dia tahu. Tanya dia.",
          "A herb picker, barely seventeen but with a tongue a hundred years old. She knows every niche. If strangers are on the cliffs, "
          "she knows. Ask her.", gestur="tunjuk", p=[
              C("Aku akan menemui Shan Ling.", "I'll go see Shan Ling.", akibat=["quest_maju tema_gunung_tali"])]),
    ], syarat=[tahap_q(Q, "jejak")], prioritas=60)

    # -- 3: Shan Ling and the snares
    dialog("tema_gunung_tali_shan_ling", "tokoh:shan_ling", [
        N("mulai", "Gou Tie? Tentu saja Gou Tie. Minggu lalu dia menawariku dua kali lipat harga Aula Herba asal semua petikanku dijual "
                   "padanya. Aku bilang lebih baik kujual ke kera. Sejak itu ceruk-ceruk berbau aneh.",
          "Gou Tie? Of course Gou Tie. Last week he offered me twice the Herb Hall's price if I sold him everything I pick. I said I'd "
          "rather sell to the monkeys. Since then the niches have smelled strange.", gestur="sikap_berkacak", prop="keranjang", p=[
              C("Bau aneh?", "Strange?", ke="jerat"),
              C("Kenapa dia ingin kereta gantung rusak?", "Why would he want the cable car broken?", ke="rencana")]),
        N("rencana", "Kalau kereta gantung mati, barang naik lewat punggung kuli, dan Gou Tie punya selusin kuli bayaran yang menunggu. Kalau "
                     "pemetik takut, tebing kosong, dan herba berumur tiga ratus tahun cuma bisa dibeli dari dia. Dua burung, satu batu.",
          "If the cable car dies, goods go up on porters' backs, and Gou Tie has a dozen hired porters waiting. If the pickers are scared, "
          "the cliffs are empty, and three-hundred-year herbs can only be bought from him. Two birds, one stone.",
          gestur="bicara_tangan", p=[
              C("Dan ceruk yang berbau aneh?", "And the strange-smelling niches?", ke="jerat")]),
        N("jerat", "Jerat roh. Benang qi tipis yang dipasang di ceruk meditasi; siapa pun yang duduk di sana kakinya terikat dan terjatuh "
                   "saat berdiri. Ada enam ceruk di jalan papan. Lepaskan semuanya. Aku akan ikut, tapi jangan berharap aku memegangi "
                   "tanganmu di atas jurang.",
          "Spirit snares. Thin qi threads set in the meditation niches; whoever sits there gets their feet tied and falls when they stand. "
          "There are six niches on the plank walk. Release them all. I'll come along, but don't expect me to hold your hand over the gorge.",
          gestur="tunjuk", prop="sabit_herba", p=[
              C("Tunjukkan jalannya.", "Show me the way.", akibat=["quest_maju tema_gunung_tali"]),
              C("Tali Panjat dijual di Aula Herba, bukan?", "Climbing Ropes are sold at the Herb Hall, right?", ke="tali")]),
        N("tali", "Lima belas Nilai Batu, sekali pakai, dan menyelamatkan nyawa sekali. Penakut membelinya. Pemetik yang hidup lama juga "
                  "membelinya. Pikirkan sendiri.",
          "Fifteen Spirit Stones, single use, saves your life once. Cowards buy them. Pickers who live long buy them too. Think about it.",
          gestur="tertawa", p=[
              C("Ayo lepaskan jeratnya.", "Let's release the snares.", akibat=["quest_maju tema_gunung_tali"])]),
    ], syarat=[tahap_q(Q, "shan_ling")], prioritas=60)
    dialog("tema_gunung_tali_jerat_ingat", "tokoh:shan_ling", [
        N("mulai", "Enam ceruk di jalan papan, dari bawah sampai atas. Jeratnya berkilau samar kalau kau dekat. Jangan lihat ke bawah. "
                   "Maksudku, boleh lihat, tapi jangan lama-lama.",
          "Six niches on the plank walk, bottom to top. The snares glimmer faintly when you're close. Don't look down. I mean, you can look, "
          "just not for long.", gestur="tunjuk"),
    ], syarat=[tahap_q(Q, "jerat")], prioritas=40)

    # -- 4: the white guardian
    dialog("tema_gunung_tali_kera_putih", "tokoh:shan_ling", [
        N("mulai", "Kau melihatnya? Kera Putih Tua! Nenekku bilang ia sudah tua waktu Aula Emas belum disepuh. Kalau ia menunjuk Katrol "
                   "Tebing, ia ingin kau naik ke sarangnya. Tali Sutra Kera: serat dari akar tebing yang dipintal kera selama seratus tahun. "
                   "Lebih kuat dari besi, tidak bisa digergaji roh.",
          "You saw it? The Old White Ape! My grandmother said it was already old before the Golden Hall was gilded. If it points at the "
          "Cliff Pulley, it wants you up at its nest. Ape Silk Rope: fibre from cliff roots spun by the apes for a hundred years. Stronger "
          "than iron, and no spirit saw can bite it.", gestur="kaget", p=[
              C("Bagaimana memintanya?", "How do I ask for it?", ke="sopan"),
              C("Kenapa tidak diambil saja?", "Why not just take it?", ke="paksa")]),
        N("sopan", "Kera-kera itu suka buah. Bawa sepuluh: beri merah, beri bercahaya, apel, semangka, apa saja. Jangan lempar; letakkan "
                   "di dekatnya, lalu membungkuk. Ia lebih sopan dari separuh pedagang di pasar kaki.",
          "The apes love fruit. Bring ten: sweet berries, glow berries, apples, melon, anything. Don't throw it; set it down near it, then "
          "bow. It has better manners than half the merchants at the foot market.", gestur="bicara_tangan", p=[
              C("Sepuluh buah. Mengerti.", "Ten fruits. Understood.")]),
        N("paksa", "Kau bisa coba. Ia akan melepaskan seratnya kalau kau melukainya cukup parah. Tapi tebing ini punya ingatan, dan aku juga.",
          "You could try. It'll let go of the fibre if you hurt it badly enough. But these cliffs have a memory, and so do I.",
          gestur="sikap_lipat_tangan", p=[
              C("Aku akan membawa buah.", "I'll bring fruit.", ke="sopan"),
              C("Kita lihat nanti.", "We'll see.")]),
    ], syarat=[tahap_q(Q, "kera_putih")], prioritas=60)

    # -- 5: fitting the rope
    dialog("tema_gunung_tali_pasang", "tokoh:tie_suo", [
        N("mulai", "Itu... itu Tali Sutra Kera? Ayahku bilang itu dongeng untuk anak kuli. Berikan, berikan, tanganku gatal!",
          "Is that... is that Ape Silk Rope? My father said it was a fairy tale for porters' children. Give it, give it, my hands itch!",
          gestur="kaget", p=[
              C("(Serahkan Tali Sutra Kera.)", "(Hand over the Ape Silk Rope.)", ke="pasang",
                syarat=["punya kultivator:tema/tali_sutra_kera"], akibat=["ambil kultivator:tema/tali_sutra_kera 1"], tampil=True,
                alasan="kultivator.cerita.tema_gunung.alasan.tali_sutra"),
              C("Nanti dulu.", "Not yet.")]),
        N("pasang", "Lihat bagaimana ia menyerap minyak... seperti hidup. Butuh setengah hari untuk memasangnya di roda. Dan selama setengah "
                    "hari itu stasiun terbuka seperti perut ikan. Kalau Gou Tie pintar, dia datang sekarang. Gou Tie tidak pintar, tapi dia "
                    "rakus, dan rakus sering mirip pintar.",
          "Look how it drinks the oil... as if it were alive. It takes half a day to fit it to the wheel. And for half a day the station is "
          "open like a gutted fish. If Gou Tie is clever, he comes now. Gou Tie isn't clever, but he's greedy, and greed often looks clever.",
          gestur="perbaiki_palu", prop="palu", p=[
              C("Aku akan menjaga stasiun.", "I'll guard the station.", akibat=["quest_maju tema_gunung_tali"])]),
    ], syarat=[tahap_q(Q, "pasang")], prioritas=60)
    L("kultivator.cerita.tema_gunung.alasan.tali_sutra", "Kau belum membawa Tali Sutra Kera.", "You are not carrying the Ape Silk Rope.")
    dialog("tema_gunung_tali_pasang_darah", "tokoh:tie_suo", [
        N("mulai", "Serat ini ada noda darahnya. Darah putih kera. Hmph. Tali tetap tali, dan keretaku harus jalan. Tapi jangan ceritakan "
                   "pada Shan Ling bagaimana kau mendapatkannya.",
          "There's blood on this fibre. White ape blood. Hmph. A rope's a rope, and my car must run. But don't tell Shan Ling how you got it.",
          gestur="sikap_lipat_tangan", p=[
              C("(Serahkan Tali Sutra Kera.)", "(Hand over the Ape Silk Rope.)", syarat=["punya kultivator:tema/tali_sutra_kera"],
                akibat=["ambil kultivator:tema/tali_sutra_kera 1", "quest_maju tema_gunung_tali"], tampil=True,
                alasan="kultivator.cerita.tema_gunung.alasan.tali_sutra"),
              C("Nanti dulu.", "Not yet.")]),
    ], syarat=[tahap_q(Q, "pasang"), "bendera gunung_paksa"], prioritas=65)
    dialog("tema_gunung_tali_bela_ingat", "tokoh:tie_suo", [
        N("mulai", "Tetap di dekat stasiun! Aku tidak bisa memasang tali sambil mengayunkan kunci roda ke kepala orang. Sebenarnya bisa, "
                   "tapi talinya jadi miring.",
          "Stay near the station! I can't fit a rope while swinging a wheel key at people's heads. Well, I can, but the rope ends up crooked.",
          gestur="perbaiki_palu", prop="palu"),
    ], syarat=[tahap_q(Q, "bela"), "!bendera gunung_gou_menyerah"], prioritas=40)

    # -- 5b: the fate of Gou Tie
    dialog("tema_gunung_tali_gou", "tokoh:gou_tie", [
        N("mulai", "Baiklah, baiklah! Aku kalah. Tebing ini memang tidak suka padaku. Dengar, kita bisa bicara seperti orang dagang. "
                   "Herba tebing itu emas yang tumbuh di batu, dan kau tahu itu.",
          "All right, all right! I lost. These cliffs never liked me. Listen, we can talk like traders. Cliff herbs are gold growing on "
          "stone, and you know it.", gestur="berlutut", p=[
              C("Kau akan kuserahkan ke biara. Bayar utangmu dengan memikul.", "I'm handing you to the monastery. Pay your debt by carrying.",
                ke="tobat"),
              C("Pergi dari gunung ini dan jangan kembali.", "Leave this mountain and never come back.", ke="usir"),
              C("Aku mendengarkan. Berapa?", "I'm listening. How much?", ke="suap")]),
        N("tobat", "Memikul? Aku? Cakar Besi memikul karung beras untuk biksu?... Kakekku kuli. Dia mati di tangga ketujuh dengan karung "
                   "di punggung. Mungkin... mungkin itu sebabnya aku membenci tangga ini.",
          "Carrying? Me? Iron Claw hauling rice sacks for monks?... My grandfather was a porter. He died on the seventh landing with a sack "
          "on his back. Maybe... maybe that's why I hate these stairs.", gestur="menangis", p=[
              C("Tangga itu tidak membencimu.", "The stairs don't hate you.",
                akibat=["gunung_gou tobat", "nama_baik 5", "gunung_adv tobat_gou", "kabar tema_gunung_gou_tobat",
                        "kabar tema_gunung_gou_tobat_2"])]),
        N("usir", "Pergi. Hah. Ke mana? Ada seratus gunung lain dan seratus tebing lain. Tapi baiklah. Lain kali aku akan memilih tebing "
                  "tanpa kera dan tanpa orang sepertimu.",
          "Leave. Hah. Where to? There are a hundred other mountains and a hundred other cliffs. But fine. Next time I'll pick a cliff "
          "without monkeys and without people like you.", gestur="sikap_angkuh", p=[
              C("Jalan.", "Go.", akibat=["gunung_gou usir", "nama_baik 5", "kabar tema_gunung_gou_usir",
                                                    "kabar tema_gunung_gou_usir_2"])]),
        N("suap", "Enam batang emas sekarang, dan bagian dari setiap herba yang kujual nanti. Biarkan aku mengatur harga, biarkan gadis "
                  "cerewet itu pergi. Tie Suo dapat keretanya, kau dapat emasnya, aku dapat tebingnya. Semua senang.",
          "Six gold bars now, and a share of every herb I sell later. Let me set the prices, let that chatty girl go. Tie Suo gets his "
          "car, you get the gold, I get the cliffs. Everybody's happy.", gestur="hitung_koin_gelap", prop="kantong_uang", p=[
              C("Sepakat.", "Deal.", akibat=["gunung_gou suap", "kabar tema_gunung_gou_suap", "kabar tema_gunung_gou_suap_2"]),
              C("Tidak. Aku berubah pikiran.", "No. I changed my mind.", ke="mulai")]),
    ], syarat=[tahap_q(Q, "bela"), "bendera gunung_gou_menyerah"], prioritas=70)

    # -- 6: the blessing (three moods)
    berkat = [
        ("tema_gunung_tali_berkat", "berkat", [],
         ("Lonceng sudah kuperintahkan dibunyikan, dan para biksu yang selalu menyebut kereta itu jalan malas sedang membakar dupa untuknya. "
          "Lucu, bukan? Kadang jalan malas membawa orang yang terlalu lelah untuk berdoa sampai ke depan Buddha.",
          "I've ordered the bell rung, and the monks who always called that car the lazy road are burning incense for it. Funny, isn't it? "
          "Sometimes the lazy road carries people too tired to pray right up to the Buddha's feet."),
         ["gunung_upacara", "gunung_gelar", "nama_baik 10"]),
        ("tema_gunung_tali_berkat_paksa", "berkat_paksa", ["bendera gunung_paksa"],
         ("Tali itu akan kuberkati. Tali tidak bersalah. Tapi Shan Ling tidak datang hari ini, dan kera-kera diam di tebing. Kita membangun "
          "jembatan dengan meruntuhkan rumah orang lain. Ingatlah rasanya.",
          "I will bless the rope. A rope has no sin. But Shan Ling did not come today, and the apes are silent on the cliff. We built a "
          "bridge by tearing down someone else's home. Remember how that feels."),
         ["gunung_upacara", "gunung_gelar", "nama_baik 6"]),
        ("tema_gunung_tali_berkat_suap", "berkat_suap", ["bendera gunung_gou_suap"],
         ("Kereta itu akan berjalan, jadi aku akan memberkatinya. Emas di kantongmu tidak perlu kuberkati; ia sudah cukup berat sendiri. "
          "Shan Ling pergi pagi ini. Ia meninggalkan keranjangnya di depan gerbang biara.",
          "That car will run, so I will bless it. The gold in your purse needs no blessing from me; it is heavy enough on its own. Shan Ling "
          "left this morning. She left her basket at the monastery gate."),
         ["gunung_upacara", "gunung_gelar", "nama_baik 3"]),
    ]
    for did, stage, extra, teks, akibat in berkat:
        dialog(did, "tokoh:jue_yuan", [
            N("mulai", teks[0], teks[1], gestur="sikap_doa", prop="tongkat_cincin", suara="kotabesar.temple_bell", p=[
                C("Aku siap untuk upacaranya.", "I am ready for the ceremony.", ke="upacara")]),
            N("upacara", "Naiklah ke kabin pertama bersama mereka. Saat lonceng berhenti, lihatlah ke awan. Kalau cahaya itu datang, "
                         "jangan sujud padanya; ia hanya pelangi yang sopan. Sujudlah pada orang-orang yang memikul tangga ini selama "
                         "seribu tahun.",
              "Ride the first cabin with them. When the bell falls silent, look at the clouds. If the light comes, don't bow to it; it's only "
              "a polite rainbow. Bow to the people who have carried these stairs for a thousand years.", gestur="tatap_langit",
              akibat=akibat + ["quest_maju tema_gunung_tali"]),
        ], syarat=[tahap_q(Q, stage)] + extra, prioritas=60)


# ------------------------------------------------------------------------------------------------ side quests

def cerita_samping():
    # -- Pei Wenzhi climbs for his mother
    quest("tema_ziarah_ibu", "tokoh:pei_wenzhi", ("Dupa untuk Ibu", "Incense for Mother"),
          ("Sastrawan muda Pei Wenzhi ingin mendaki Seribu Anak Tangga untuk mendiang ibunya, tetapi kakinya tidak sekuat lidahnya.",
           "The young scholar Pei Wenzhi wants to climb the Thousand Steps for his late mother, but his legs are weaker than his tongue."), [
        S("daftar", "Daftarkan ziarahmu di Kuil Kaki Gunung.", "Register your pilgrimage at the Foot Temple.", tujuan=["tanda gunung_ziarah_daftar"]),
        S("daki", "Selesaikan Ziarah Seribu Anak Tangga: sembilan bordes, lalu Aula Emas, tanpa terbang.",
          "Complete the Pilgrimage of a Thousand Steps: nine landings, then the Golden Hall, without flying.", tujuan=["ziarah_selesai gunung"]),
        S("cerita", "Kembalilah ke Kuil Kaki dan ceritakan puncak itu pada Pei Wenzhi.", "Return to the Foot Temple and tell Pei Wenzhi about the summit.",
          akibat=["gunung_adv ibu_peziarah", "nama_baik 3", "beri kultivator:tema/dupa_ziarah 4"],
          cabang=[{"syarat": ["situs_dikenal kota_tema/air"], "ke": "lentera"}], ke="selesai",
          hasil=("Pei Wenzhi menyalakan dupa di Kuil Kaki dan untuk pertama kalinya tidak mengutip puisi siapa pun.",
                 "Pei Wenzhi lights incense at the Foot Temple and, for the first time, quotes nobody's poem.")),
        S("lentera", "Ibunya selalu ingin melihat Malam Lentera Air. Bawakan satu Lentera Teratai dari Kota Air untuk Pei Wenzhi.",
          "His mother always wished to see the Night of Water Lanterns. Bring Pei Wenzhi a Lotus Lantern from the Water Town.",
          tujuan=["antar kultivator:tema/lentera_teratai tokoh:pei_wenzhi"]),
        S("lentera_terima", "Pei Wenzhi memegang lentera itu. Dengarkan dia.", "Pei Wenzhi holds the lantern. Listen to him.", ke="selesai",
          hasil=("Malam itu sebuah lentera teratai hanyut di Kolam Pelepasan Biara Awan. Biksu-biksu pura-pura tidak melihat sastrawan "
                 "yang menangis di tepinya.",
                 "That night a lotus lantern drifted on the Release Pond of the Cloud Monastery. The monks pretended not to see the scholar "
                 "weeping at its edge.")),
    ])
    dialog("tema_gunung_pei_tawar", "tokoh:pei_wenzhi", [
        N("mulai", "Kau juga peziarah? Hmph. Kebanyakan orang di sini mendaki karena takut neraka. Aku mendaki karena bakti, yang tentu "
                   "saja jauh lebih mulia. \"Gunung tinggi tak mengenal lelah bagi anak yang berbakti,\" kata pepatah. Pepatah itu jelas "
                   "tidak pernah mendaki.",
          "You're a pilgrim too? Hmph. Most people here climb out of fear of hell. I climb out of filial piety, which is of course far "
          "nobler. \"High mountains know no weariness for a filial son,\" says the proverb. That proverb has clearly never climbed.",
          gestur="sikap_angkuh", prop="tongkat_bambu", p=[
              C("Untuk siapa kau mendaki?", "Who are you climbing for?", ke="ibu"),
              C("Kau terdengar lelah.", "You sound tired.", ke="lelah")]),
        N("lelah", "Lelah? Aku? Aku hanya... beristirahat di bordes ketiga. Empat kali. Dalam dua hari. Itu strategi.",
          "Tired? Me? I was merely... resting at the third landing. Four times. In two days. It is a strategy.", gestur="menguap_peregangan",
          p=[C("Untuk siapa kau mendaki?", "Who are you climbing for?", ke="ibu")]),
        N("ibu", "Ibuku. Dia ingin naik ke Aula Emas sebelum matanya kabur, dan aku selalu bilang tahun depan, karena ujian negara. Tahun "
                 "lalu dia meninggal, dan ujian negaranya kuluuskan. Lucu, bukan? Temani aku. Maksudku, mendakilah juga, dan nanti "
                 "ceritakan padaku seperti apa puncaknya, kalau-kalau aku... beristirahat lagi.",
          "My mother. She wanted to reach the Golden Hall before her eyes clouded over, and I always said next year, because of the state "
          "examination. Last year she died, and I passed the examination. Funny, isn't it? Keep me company. I mean, climb too, and later "
          "tell me what the summit is like, in case I... rest again.", gestur="menangis", p=[
              C("Aku akan mendaki untuk ibumu.", "I'll climb for your mother.", akibat=["quest_mulai tema_ziarah_ibu"]),
              C("Mungkin lain kali.", "Maybe another time.")]),
    ], syarat=["quest tema_ziarah_ibu baru"], prioritas=25)
    dialog("tema_gunung_pei_daki", "tokoh:pei_wenzhi", [
        N("mulai", "Daftar di meja biksu di sebelah, lalu naik. Jangan pakai kereta gantung, jangan terbang, jangan katrol. Ya, aku sudah "
                   "bertanya apakah tandu boleh. Tidak boleh.",
          "Register at the monk's desk next door, then climb. No cable car, no flying, no pulleys. Yes, I already asked whether a sedan "
          "chair counts. It does not.", gestur="tunjuk"),
    ], syarat=["quest tema_ziarah_ibu tahap daftar"], prioritas=20)
    dialog("tema_gunung_pei_naik", "tokoh:pei_wenzhi", [
        N("mulai", "Masih di sini? Sembilan bordes, sembilan batang dupa. Kalau kakimu gemetar di bordes ketujuh, itu normal. Kalau kau "
                   "melihat aku tertidur di bordes ketiga, itu juga normal.",
          "Still here? Nine landings, nine sticks of incense. If your legs shake at the seventh landing, that's normal. If you see me asleep "
          "at the third landing, that's normal too.", gestur="sikap_tua", prop="tongkat_bambu"),
    ], syarat=["quest tema_ziarah_ibu tahap daki"], prioritas=20)
    dialog("tema_gunung_pei_lentera_ingat", "tokoh:pei_wenzhi", [
        N("mulai", "Lentera Teratai, dari Kota Air. Aku tahu, aku tahu, katanya lupakan. Tapi kalau kau kebetulan lewat sana...",
          "A Lotus Lantern, from the Water Town. I know, I know, I said forget it. But if you happen to pass that way...",
          gestur="sikap_tunduk"),
    ], syarat=["quest tema_ziarah_ibu tahap lentera"], prioritas=20)
    dialog("tema_gunung_pei_cerita", "tokoh:pei_wenzhi", [
        N("mulai", "Kau sampai! Benar sampai? Ceritakan. Jangan pakai bahasa indah; aku sudah muak dengan bahasa indah. Seperti apa?",
          "You made it! Truly? Tell me. No fine words; I'm sick of fine words. What was it like?", gestur="kaget", p=[
              C("Awan di bawah kaki seperti lautan susu, dan atapnya emas.", "Clouds under your feet like a sea of milk, and the roof is gold.",
                ke="awan"),
              C("Sejujurnya, kakiku sakit dan ada kera mencuri bakpaoku.", "Honestly, my legs hurt and a monkey stole my bun.", ke="jujur")]),
        N("awan", "Lautan susu... Ibu selalu bilang awan itu seperti kapas yang dijemur langit. Dia benar dan aku menertawakannya.",
          "A sea of milk... Mother always said clouds were cotton hung out to dry by the sky. She was right and I laughed at her.",
          gestur="tatap_langit", p=[C("Dia pasti senang kau mencoba.", "She would be glad you tried.", ke="akhir")]),
        N("jujur", "Hah! Kera! Ibu akan tertawa sampai batuk. Dia selalu bilang orang suci pun harus menjaga bakpaonya.",
          "Hah! A monkey! Mother would have laughed until she coughed. She always said even saints must guard their buns.",
          gestur="tertawa", p=[C("Dia pasti senang kau mencoba.", "She would be glad you tried.", ke="akhir")]),
        N("akhir", "Ambil dupa ini; ini dupa yang kubeli untuknya dan tak pernah kuberikan. Ah, satu lagi. Ibu selalu ingin melihat Malam "
                   "Lentera Air di Kota Air. Kalau kau pernah ke sana... tidak, lupakan. Terima kasih.",
          "Take this incense; I bought it for her and never gave it. Ah, one more thing. Mother always wanted to see the Night of Water "
          "Lanterns in the Water Town. If you've ever been there... no, forget it. Thank you.", gestur="hormat_dalam", prop="dupa", p=[
              C("(Terima dupa.)", "(Accept the incense.)", akibat=["quest_maju tema_ziarah_ibu"])]),
    ], syarat=["quest tema_ziarah_ibu tahap cerita"], prioritas=50)
    dialog("tema_gunung_pei_lentera", "tokoh:pei_wenzhi", [
        N("mulai", "Lentera teratai... dari Kota Air, sungguh? Kertasnya harum seperti kamar ibu. Malam ini akan kuhanyutkan di Kolam "
                   "Pelepasan. Kau tahu, aku menulis tiga ratus puisi untuk ujian negara, dan tak satu pun untuknya. Malam ini satu.",
          "A lotus lantern... from the Water Town, truly? The paper smells like Mother's room. Tonight I'll set it adrift on the Release "
          "Pond. You know, I wrote three hundred poems for the state examination and not one for her. Tonight, one.",
          gestur="hormat_dalam", prop="lentera_teratai", p=[
              C("Tulislah yang sederhana.", "Write a simple one.",
                akibat=["nama_baik 3", "beri kultivator:tema/dupa_ziarah 4", "efek minecraft:luck 6000 0", "quest_maju tema_ziarah_ibu"])]),
    ], syarat=["quest tema_ziarah_ibu tahap lentera_terima"], prioritas=50)
    dialog("tema_gunung_pei_sapa", "tokoh:pei_wenzhi", [
        N("mulai", "Ah, kau. Aku sudah sampai bordes keenam kemarin. Tanpa istirahat strategis. Yah, dua kali.",
          "Ah, you. I reached the sixth landing yesterday. Without strategic rests. Well, twice.", gestur="lambai", prop="tongkat_bambu", p=[
              C("Ada kabar di kota?", "Any news in town?", ke="kabar"),
              C("Teruslah mendaki.", "Keep climbing.")]),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=["quest tema_ziarah_ibu selesai"], prioritas=5)

    # -- a porter's day
    quest("tema_kuli_sehari", "tokoh:lao_dan", ("Kuli Sehari", "Porter for a Day"),
          ("Lao Dan menantangmu memikul tiga muatan ke ruang titipan di atas, tanpa terbang dan tanpa kereta.",
           "Lao Dan dares you to carry three loads up to the delivery rooms above, without flying and without the cable car."), [
        S("pikul", "Pikul muatan kuli ke ruang titipan Biara Awan atau Aula Emas (tiga kali). Muatan baru dari Lao Dan.",
          "Carry porter's loads to the delivery room of the Cloud Monastery or the Golden Hall (three times). New loads from Lao Dan.",
          akibat_masuk=["gunung_pikul"], tujuan=["tanda gunung_muatan x3"]),
        S("upah", "Kembali ke Lao Dan di Pos Kuli untuk upahmu.", "Return to Lao Dan at the Porters' Post for your wages.", ke="selesai",
          hasil=("Lao Dan memberimu tongkat kuli tua miliknya. Bahumu pegal tiga hari, dan setiap kuli di tangga kini mengangguk padamu.",
                 "Lao Dan gives you his old porter's staff. Your shoulders ache for three days, and every porter on the stairs now nods to you.")),
    ])
    dialog("tema_gunung_kuli_tawar", "tokoh:lao_dan", [
        N("mulai", "Kau naik-turun tangga seperti kambing muda. Tapi kambing tidak membawa apa-apa. Mau tahu rasanya jadi kuli sehari? "
                   "Tiga muatan, dari sini ke ruang titipan di atas. Tanpa terbang, tanpa kereta.",
          "You go up and down the stairs like a young goat. But goats carry nothing. Want to know what being a porter feels like for a "
          "day? Three loads, from here to the delivery rooms above. No flying, no cable car.", gestur="tepuk_bahu", prop="pikulan", p=[
              C("Berikan muatan pertama.", "Give me the first load.", akibat=["quest_mulai tema_kuli_sehari"]),
              C("Kenapa tidak terbang saja?", "Why not just fly?", ke="terbang"),
              C("Bahuku menolak.", "My shoulders refuse.")]),
        N("terbang", "Karena gunung tidak bisa diajak bicara dari atas. Kalau kau terbang, kau melihat gunung. Kalau kau memikul, gunung "
                     "melihatmu.",
          "Because you can't talk to a mountain from above. When you fly, you look at the mountain. When you carry, the mountain looks at you.",
          gestur="cerita", p=[
              C("Baiklah, berikan muatannya.", "Fine, give me the load.", akibat=["quest_mulai tema_kuli_sehari"]),
              C("Lain kali.", "Another time.")]),
    ], syarat=["quest tema_kuli_sehari baru"], prioritas=20)
    dialog("tema_gunung_kuli_pikul", "tokoh:lao_dan", [
        N("mulai", "Masih kuat? Ruang titipan ada di Biara Awan dan di Aula Emas. Muatanmu jatuh? Ambil lagi, kuli tidak menangisi karung.",
          "Still standing? The delivery rooms are at the Cloud Monastery and the Golden Hall. Dropped your load? Take another; porters don't "
          "weep over sacks.", gestur="ikat_tali", prop="tali", p=[
              C("Berikan satu muatan lagi.", "Give me another load.", akibat=["gunung_pikul"]),
              C("Aku masih membawanya.", "I'm still carrying it.")]),
    ], syarat=["quest tema_kuli_sehari tahap pikul"], prioritas=30)
    dialog("tema_gunung_kuli_upah", "tokoh:lao_dan", [
        N("mulai", "Tiga muatan, dan kakimu masih menempel di badan. Ini upahmu, tiga puluh Nilai Batu, tarif kuli muda. Dan ini, tongkatku "
                   "yang lama. Empat puluh tahun di tangga. Sekarang dia pensiun bersamamu.",
          "Three loads, and your legs are still attached. Here are your wages, thirty Spirit Stones, a young porter's rate. And this, my old "
          "staff. Forty years on the stairs. Now it retires with you.", gestur="tertawa", prop="tongkat", p=[
              C("Terima kasih, Lao Dan.", "Thank you, Lao Dan.",
                akibat=["beri_nb 30", "beri kultivator:tema/tongkat_kuli", "gunung_hadiah bekal_kuli", "gunung_adv kuli_sehari",
                        "quest_maju tema_kuli_sehari"])]),
    ], syarat=["quest tema_kuli_sehari tahap upah"], prioritas=50)

    # -- the naughty monkeys
    quest("tema_kera_nakal", "peran:peziarah", ("Kera Nakal", "Naughty Monkeys"),
          ("Kera-kera gunung merampas bekal dan dupa para peziarah. Rebut kembali lima barang curian.",
           "The mountain monkeys snatch the pilgrims' food and incense. Win back five stolen things."), [
        S("rebut", "Buat kera menjatuhkan curiannya: pukul pelan atau lempar umpan buah ke tanah (lima kali).",
          "Make monkeys drop their loot: a light blow, or toss fruit bait on the ground (five times).", tujuan=["tanda gunung_kera_kembali x5"]),
        S("kembalikan", "Kembalikan barang-barang itu kepada seorang peziarah.", "Return the things to a pilgrim.", ke="selesai",
          hasil=("Para peziarah mengikat bekal mereka dengan tali dan menamai seekor kera paling gemuk dengan namamu.",
                 "The pilgrims tie their food with string and name the fattest monkey after you.")),
    ])
    dialog("tema_gunung_kera_tawar", "peran:peziarah", [
        N("mulai", "Aduh, aduh! Kera itu mengambil bakpao dan dupaku sekaligus! Dupa! Apa yang mau dia sembah? Nak, kau tampak gesit. "
                   "Kera-kera itu suka merampas di bordes atas.",
          "Oh, oh! That monkey took my bun and my incense at once! Incense! What is it going to worship? Child, you look nimble. Those "
          "monkeys love to rob people on the upper landings.", gestur="marah_tunjuk", prop="tas_dupa", p=[
              C("Aku akan merebutnya kembali.", "I'll get them back.", akibat=["quest_mulai tema_kera_nakal"]),
              C("Kenapa tidak dibunuh saja?", "Why not kill them?", ke="bunuh")]),
        N("bunuh", "Di gunung suci? Para biksu akan mengusirmu sampai ke laut! Pukul pelan saja, atau lempar buah ke tanah; kera rakus "
                   "selalu menjatuhkan barang curian demi buah.",
          "On a holy mountain? The monks would chase you all the way to the sea! A light blow, or toss fruit on the ground; a greedy monkey "
          "always drops its loot for fruit.", gestur="kaget", p=[
              C("Baiklah, aku akan merebutnya kembali.", "All right, I'll get them back.", akibat=["quest_mulai tema_kera_nakal"])]),
    ], syarat=["quest tema_kera_nakal baru", "kota_tema gunung"], prioritas=15)
    dialog("tema_gunung_kera_kembalikan", "peran:peziarah", [
        N("mulai", "Bakpaoku! Sedikit digigit, tapi masih bakpaoku. Dan dupa, dan sandal siapa ini? Biarlah, nanti kutitipkan di kuil. "
                   "Terimalah dupa ini. Semoga kera-kera di jalanmu selalu kenyang.",
          "My bun! A bit bitten, but still my bun. And the incense, and whose sandal is this? Never mind, I'll leave it at the temple. "
          "Take this incense. May the monkeys on your road always be full.", gestur="hormat", prop="dupa", p=[
              C("Hati-hati di tangga.", "Careful on the stairs.",
                akibat=["nama_baik 3", "beri kultivator:tema/dupa_ziarah 6", "gunung_adv kera_nakal", "quest_maju tema_kera_nakal"])]),
    ], syarat=["quest tema_kera_nakal tahap kembalikan"], prioritas=50)

    # -- the thousand-year cloud grass
    quest("tema_rumput_awan", "tokoh:jue_yuan", ("Rumput Awan Seribu Tahun", "Thousand-Year Cloud Grass"),
          ("Kepala Biara Jue Yuan butuh Rumput Awan Seribu Tahun untuk ramuan yang hanya bisa diseduh sekali dalam satu masa jabatan.",
           "Abbot Jue Yuan needs Thousand-Year Cloud Grass for a brew that can be steeped only once in an abbot's lifetime."), [
        S("cari", "Petik herba tebing sampai menemukan Rumput Awan Seribu Tahun (jarang), lalu bawa kepada Jue Yuan.",
          "Pick cliff herbs until you find Thousand-Year Cloud Grass (rare), then bring it to Jue Yuan.",
          tujuan=["antar kultivator:tema/rumput_awan_seribu_tahun tokoh:jue_yuan"]),
        S("ramuan", "Jue Yuan menyeduh ramuannya. Duduklah bersamanya.", "Jue Yuan steeps his brew. Sit with him.", ke="selesai",
          hasil=("Teh itu rasanya seperti hujan pertama di atas batu panas. Jue Yuan bilang rasanya seperti teh biasa, lalu tertawa lama.",
                 "The tea tastes like the first rain on hot stone. Jue Yuan says it tastes like ordinary tea, then laughs for a long time.")),
    ])
    dialog("tema_gunung_rumput_tawar", "tokoh:jue_yuan", [
        N("mulai", "Setiap kepala biara di sini menyeduh satu ramuan dalam hidupnya: teh dari Rumput Awan Seribu Tahun. Guruku menyeduhnya "
                   "di usia sembilan puluh. Aku sudah delapan puluh tujuh, dan pemetik tebing bilang rumput itu sudah jarang sekali. "
                   "Mungkin langit ingin aku belajar sabar. Atau mungkin langit ingin kau yang memetiknya.",
          "Every abbot here steeps one brew in his life: tea from Thousand-Year Cloud Grass. My master steeped his at ninety. I am "
          "eighty-seven, and the cliff pickers say that grass has grown very rare. Perhaps heaven wants me to learn patience. Or perhaps "
          "heaven wants you to pick it.", gestur="sikap_doa", prop="tongkat_cincin", p=[
              C("Aku akan mencarinya di tebing.", "I'll look for it on the cliffs.", akibat=["quest_mulai tema_rumput_awan"]),
              C("Kenapa tidak menyuruh Shan Ling?", "Why not ask Shan Ling?", ke="shan")]),
        N("shan", "Sudah. Dia bilang rumput itu tidak mau dipetik orang yang mencarinya. Lalu dia bilang aku harus bayar dua kali lipat. "
                  "Anak itu akan jadi pedagang hebat atau orang suci; aku belum bisa menentukan yang mana.",
          "I did. She said the grass refuses to be picked by those who seek it. Then she said I'd have to pay double. That child will become "
          "a great merchant or a saint; I can't yet tell which.", gestur="tertawa", p=[
              C("Aku akan mencarinya.", "I'll look for it.", akibat=["quest_mulai tema_rumput_awan"]),
              C("Mungkin nanti.", "Perhaps later.")]),
    ], syarat=["quest tema_rumput_awan baru"], prioritas=15)
    dialog("tema_gunung_rumput_yao", "tokoh:yao_po", [
        N("mulai", "Rumput Awan Seribu Tahun? Satu dari dua puluh petikan, kalau langit sedang baik. Tumbuhnya di ceruk yang kena kabut "
                   "pagi. Naikkan pangkat pemetikmu, Nak, ceruk-ceruk tinggi lebih murah hati.",
          "Thousand-Year Cloud Grass? One in twenty pickings, if heaven is in a good mood. It grows in niches the morning mist touches. Raise "
          "your picker's rank, child; the high niches are more generous.", gestur="tumbuk_herba", prop="herba"),
    ], syarat=["quest tema_rumput_awan tahap cari"], prioritas=40)
    dialog("tema_gunung_rumput_seduh", "tokoh:jue_yuan", [
        N("mulai", "Ah. Kau tahu, aku berlatih menyeduh teh ini dalam kepalaku selama enam puluh tahun, dan sekarang tanganku gemetar seperti "
                   "biksu muda. Duduklah. Air pertama untuk gunung, air kedua untuk guruku, air ketiga untuk kita.",
          "Ah. You know, I have practised steeping this tea in my head for sixty years, and now my hands shake like a young monk's. Sit. The "
          "first water is for the mountain, the second for my master, the third for us.", gestur="seduh_teh", prop="teko", p=[
              C("(Minum air ketiga.)", "(Drink the third water.)",
                akibat=["beri kultivator:tema/teh_embun_gunung 4", "efek minecraft:regeneration 600 1", "beri_nb_pita 20", "nama_baik 4",
                        "gunung_adv rumput_awan", "quest_maju tema_rumput_awan"])]),
    ], syarat=["quest tema_rumput_awan tahap ramuan"], prioritas=50)


# ------------------------------------------------------------------------------------------------ everyday talk and lore

def cerita_sapa():
    G_ = "kota_tema gunung"
    dialog("tema_gunung_jue_yuan_sapa", "tokoh:jue_yuan", [
        N("mulai", "Selamat datang di Biara Awan. Duduklah kalau kakimu masih ingat caranya. Para biksu muda bilang tangga itu ujian; aku "
                   "bilang tangga itu cuma tangga, dan ujiannya adalah tidak mengeluh di depan kuli.",
          "Welcome to the Cloud Monastery. Sit, if your legs still remember how. The young monks say the stairs are a trial; I say stairs are "
          "just stairs, and the trial is not complaining in front of the porters.", gestur="sikap_doa", prop="tongkat_cincin", p=[
              C("Ceritakan tentang Lonceng Awan.", "Tell me about the Cloud Bell.", ke="lonceng"),
              C("Kenapa Anda tidak pernah bertarung?", "Why have you never fought?", ke="tarung"),
              C("Apa yang terjadi di kota akhir-akhir ini?", "What has been happening in town?", ke="kabar"),
              C("Bagaimana Gou Tie di biara?", "How does Gou Tie fare at the monastery?", ke="gou_tobat", syarat=["bendera gunung_gou_tobat"]),
              C("Apakah aku masih diterima di sini?", "Am I still welcome here?", ke="emas", syarat=["bendera gunung_gou_suap"]),
              C("Terima kasih, Kepala Biara.", "Thank you, Abbot.")]),
        N("lonceng", "Lonceng itu dicor dari perunggu yang dibawa naik oleh tiga ratus kuli, satu genggam setiap orang, selama tiga musim "
                     "ziarah. Karena itu bunyinya tidak pernah sama dua kali. Seratus delapan pukulan setiap siang di Musim Ziarah: satu "
                     "untuk setiap kerisauan yang dibawa orang ke puncak. Dibunyikan cepat, karena kerisauan tidak suka menunggu.",
          "The bell was cast from bronze carried up by three hundred porters, one handful each, over three pilgrimage seasons. That's why it "
          "never sounds the same twice. One hundred and eight strokes every noon in the Pilgrimage Season: one for each worry people carry "
          "to the summit. Rung quickly, because worries hate to wait.", gestur="cerita", p=[
              C("Dan Aula Emas?", "And the Golden Hall?", ke="aula"),
              C("Indah sekali.", "How beautiful.")]),
        N("aula", "Para peziarah menyepuh atapnya, genteng demi genteng, selama dua ratus tahun. Di dalamnya Bodhisattva duduk di atas gajah "
                  "putih bergading enam. Kalau kau tiba di sana tanpa terbang, langit menitipkan restunya di napasmu selama tujuh hari. Kalau "
                  "kau terbang, kau cuma mendapat pemandangan. Pemandangan juga bagus, sih.",
          "Pilgrims gilded its roof tile by tile for two hundred years. Inside, the Bodhisattva sits on a white elephant with six tusks. If you "
          "arrive without flying, heaven leaves its blessing in your breath for seven days. If you fly, you only get the view. The view is "
          "nice too, mind you.", gestur="tatap_langit"),
        N("tarung", "Aku berlatih Penyatuan Dao selama enam puluh tahun. Setiap kali seseorang menantangku, aku menawarinya teh. Kebanyakan "
                    "orang lupa kenapa mereka marah sebelum tehnya dingin. Sisanya kalah melawan tangga sebelum sampai ke sini.",
          "I have practised Dao Union for sixty years. Whenever someone challenges me, I offer them tea. Most forget why they were angry before "
          "the tea cools. The rest lose to the stairs before they get here.", gestur="tertawa"),
        N("gou_tobat", "Dia memikul dua karung sehari dan menggerutu tiga ratus kali. Kemarin dia memperbaiki atap gudang tanpa disuruh, "
                       "lalu marah waktu biksu muda berterima kasih. Pertobatan jarang terlihat seperti orang suci. Biasanya terlihat "
                       "seperti orang yang kesal tapi tetap datang.",
          "He carries two sacks a day and grumbles three hundred times. Yesterday he fixed the storehouse roof unasked, then got angry when "
          "a young monk thanked him. Repentance rarely looks like a saint. Usually it looks like someone annoyed who keeps showing up.",
          gestur="tertawa"),
        N("emas", "Gerbang biara tidak pernah dikunci, bahkan untuk orang yang membawa emas. Duduklah. Keranjang Shan Ling masih di depan "
                  "gerbang; para biksu tidak berani memindahkannya. Kalau suatu hari kau ingin meletakkan sesuatu di sampingnya, tidak "
                  "ada yang akan bertanya apa.",
          "The monastery gate is never locked, not even for those who carry gold. Sit. Shan Ling's basket is still in front of the gate; the "
          "monks dare not move it. If one day you wish to set something beside it, nobody will ask what.", gestur="sikap_doa",
          prop="tongkat_cincin"),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_tie_suo_sapa", "tokoh:tie_suo", [
        N("mulai", "Kereta Gantung Tali Besi! Dua puluh tahun, satu tali, nol penumpang jatuh! Naik satu Nilai Batu, turun gratis, karena "
                   "turun itu urusan gravitasi dan gravitasi tidak kubayar.",
          "The Iron Cable Car! Twenty years, one cable, zero riders dropped! One Spirit Stone up, free down, because going down is gravity's "
          "business and I don't pay gravity.", gestur="seru_dagang", prop="palu", p=[
              C("Bagaimana kau membangunnya?", "How did you build it?", ke="bangun"),
              C("Kata biksu ini jalan malas.", "The monks call it the lazy road.", ke="malas"),
              C("Bagaimana tali barunya, Paman?", "How is the new rope, Uncle?", ke="nyanyi", syarat=["quest tema_gunung_tali hasil berkat"]),
              C("Bagaimana tali barunya, Paman?", "How is the new rope, Uncle?", ke="uban", syarat=["quest tema_gunung_tali hasil berkat_paksa"]),
              C("Bagaimana tali barunya, Paman?", "How is the new rope, Uncle?", ke="muatan", syarat=["quest tema_gunung_tali hasil berkat_suap"]),
              C("Ada kabar?", "Any news?", ke="kabar")]),
        N("bangun", "Dengan utang! Tali besi ditempa di kota pandai besi tiga gunung dari sini; aku membayar dengan janji, dan sampai sekarang "
                    "masih membayar janji itu. Roda besarnya dari pohon pinus yang tumbang kena petir. Biksu bilang itu pertanda buruk. "
                    "Aku bilang itu kayu gratis.",
          "With debt! The iron cable was forged in a smithing town three mountains away; I paid with promises, and I'm still paying those "
          "promises. The big wheel is from a pine felled by lightning. The monks called it a bad omen. I called it free timber.",
          gestur="tertawa"),
        N("malas", "Malas! Kuil mereka penuh beras dan kertas sutra, dan siapa yang membawanya naik? Keretaku! Kepala Biara diam-diam "
                   "naik kereta saat lututnya kambuh. Jangan bilang aku yang cerita.",
          "Lazy! Their temple is full of rice and silk paper, and who carries it up? My car! The Abbot secretly rides it when his knee acts "
          "up. Don't say I told you.", gestur="bisik"),
        N("nyanyi", "Tali Sutra Kera bernyanyi kalau angin lewat! Sungguh, dengarkan sore nanti: nadanya rendah, seperti biksu yang mengantuk. "
                    "Shan Ling naik kabin pertama tiap pagi, dan Kera Putih Tua duduk di Tiang Tengah menonton kami seperti mandor. "
                    "Aku melemparinya kesemek. Dia melempar balik kulitnya. Itu persahabatan, di gunung ini.",
          "The Ape Silk Rope sings when the wind passes! Truly, listen this evening: a low note, like a sleepy monk. Shan Ling rides the "
          "first cabin every morning, and the Old White Ape sits on the Middle Pylon watching us like a foreman. I toss it persimmons. It "
          "throws the peels back. That is friendship, on this mountain.", gestur="tertawa", prop="palu"),
        N("uban", "Kuat. Lebih kuat dari tali besi. Tapi kemarin aku menemukan sehelai rambut putih terjepit di roda besar, dan sejak itu "
                  "aku meminyaki tali malam-malam, waktu tidak ada yang melihat. Entah kenapa. Mungkin supaya serat itu tahu ada yang "
                  "merawatnya, walau caranya didapat... yah. Kau tahu caranya.",
          "Strong. Stronger than the iron cable. But yesterday I found a single white hair caught in the big wheel, and since then I oil the "
          "rope at night, when nobody is watching. I don't know why. Maybe so the fibre knows someone cares for it, even if the way it was "
          "got... well. You know the way.", gestur="sikap_lipat_tangan", prop="palu"),
        N("muatan", "Keretanya jalan. Tiap pagi kabin pertama penuh karung herba orang-orang Gou Tie; peziarah menunggu kabin kedua. "
                    "Mereka bayar, tentu, tepat satu Nilai Batu, sambil tersenyum seperti pemilik. Dua puluh tahun aku bilang keretaku "
                    "untuk kaki yang lelah. Sekarang keretaku untuk kantong yang lapar.",
          "The car runs. Every morning the first cabin is full of Gou Tie's people's herb sacks; the pilgrims wait for the second. They pay, "
          "of course, exactly one Spirit Stone, smiling like owners. Twenty years I said my car was for tired legs. Now my car is for hungry "
          "purses.", gestur="marah_tunjuk", prop="palu"),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_shan_ling_sapa", "tokoh:shan_ling", [
        N("mulai", "Kalau kau mau memetik di tebing, ada tiga aturan: jangan lihat ke bawah, jangan bawa makanan di tangan, dan jangan "
                   "pernah percaya papan yang berbunyi \"krek\" dua kali.",
          "If you want to pick on the cliffs, there are three rules: don't look down, don't carry food in your hand, and never trust a board "
          "that goes \"creak\" twice.", gestur="sikap_berkacak", prop="sabit_herba", p=[
              C("Kenapa tidak boleh membawa makanan?", "Why no food?", ke="kera"),
              C("Bagaimana naik pangkat pemetik?", "How do I rise as a picker?", ke="pangkat"),
              C("Herba apa yang tumbuh di sini?", "What herbs grow here?", ke="herba"),
              C("Kau masih menemui Kera Putih Tua?", "Do you still visit the Old White Ape?", ke="kera_putih", syarat=["quest tema_gunung_tali hasil berkat"]),
              C("Shan Ling... soal Kera Putih itu.", "Shan Ling... about the White Ape.", ke="dingin", syarat=["bendera gunung_paksa"]),
              C("Kudengar Gou Tie memikul karung sekarang.", "I hear Gou Tie carries sacks now.", ke="gou",
                syarat=["bendera gunung_gou_tobat"])]),
        N("kera", "Kera! Mereka bukan pencuri, mereka pemungut pajak. Pajak makanan. Pegang bakpao di tangan, dan bakpao itu jadi milik "
                  "negara kera. Pukul pelan, atau lempar buah ke tanah, mereka akan menjatuhkan curiannya. Aku berteman dengan mereka. "
                  "Kebanyakan.",
          "Monkeys! They're not thieves, they're tax collectors. A food tax. Hold a bun in your hand, and that bun belongs to the monkey "
          "state. A light tap, or toss fruit on the ground, and they drop their loot. I'm friends with them. Mostly.", gestur="tertawa"),
        N("pangkat", "Setiap petikan memberi poin di Aula Herba. Sepuluh poin, pangkat dua; tiga puluh, pangkat tiga; tujuh puluh, empat; "
                     "seratus lima puluh, lima. Pangkat tinggi membuka ceruk tinggi dan potongan harga. Rumput Awan memberi lima poin "
                     "sekaligus, kalau kau seberuntung itu.",
          "Every picking earns points at the Herb Hall. Ten points, rank two; thirty, rank three; seventy, four; a hundred and fifty, five. "
          "High ranks open the high niches and a discount. Cloud Grass gives five points at once, if you're that lucky.",
          gestur="hitung_sempoa"),
        N("herba", "Herba kayu dan herba angin, umurnya enam puluh sampai tiga ratus tahun, tergantung seberapa sabar tebingnya. Kadang, "
                   "kalau kabut pagi sedang murah hati, Rumput Awan Seribu Tahun. Aku baru menemukannya dua kali seumur hidup. Dua kali!",
          "Wood herbs and wind herbs, sixty to three hundred years old, depending on how patient the cliff is. Sometimes, when the morning "
          "mist is generous, Thousand-Year Cloud Grass. I've only found it twice in my life. Twice!", gestur="petik_herba_tebing",
          prop="keranjang"),
        N("kera_putih", "Tiap bulan purnama aku membawakannya tiga kesemek dan satu bakpao kacang merah. Dia memakan kesemeknya dan "
                        "menyimpan bakpaonya; aku tidak tahu untuk siapa. Kemarin dia menunjukkan padaku ceruk yang belum pernah kulihat. "
                        "Dua puluh tahun memetik, dan tebing ini masih menyimpan rahasia. Itu gara-gara kau, tahu.",
          "Every full moon I bring it three persimmons and one red bean bun. It eats the persimmons and keeps the bun; I don't know who for. "
          "Yesterday it showed me a niche I had never seen. Twenty years of picking, and this cliff still keeps secrets. That's because of "
          "you, you know.", gestur="tertawa", prop="keranjang"),
        N("dingin", "Tidak usah. Tali itu kuat, kereta itu jalan, kota ini senang. Aku memetik, kau lewat. Kera-kera tidak turun lagi ke "
                    "jalan papan, dan bakpao di tanganku tidak dicuri siapa pun. Ternyata aku rindu dicuri.",
          "Don't. The rope is strong, the car runs, the town is happy. I pick, you pass. The monkeys don't come down to the plank walk any "
          "more, and nobody steals the bun from my hand. Turns out I miss being robbed.", gestur="sikap_lipat_tangan", prop="sabit_herba"),
        N("gou", "Aku melihatnya di tangga keempat ratus, karung beras di punggung, dan seekor kera kecil duduk di atas karungnya. Dia tidak "
                 "mengusirnya. Aku hampir jatuh dari tebing karena tertawa. Mungkin orang memang bisa berubah. Pelan-pelan. Seperti umur herba.",
          "I saw him on the four hundredth step, a rice sack on his back, and a little monkey sitting on the sack. He didn't shoo it away. I "
          "nearly fell off the cliff laughing. Maybe people can change after all. Slowly. Like a herb's age.", gestur="tertawa"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_lao_dan_sapa", "tokoh:lao_dan", [
        N("mulai", "Mau titip barang ke atas? Dua Nilai Batu per tumpuk, sampai dalam kira-kira tiga menit. Kuli kami tidak secepat kereta, "
                   "tapi kuli kami tidak pernah macet.",
          "Want goods carried up? Two Spirit Stones a stack, there in about three minutes. Our porters aren't as fast as the car, but our "
          "porters never stall.", gestur="tepuk_bahu", prop="rangka_punggung", p=[
              C("Titipkan barang di tanganku ke Aula Emas.", "Send what's in my hand to the Golden Hall.", akibat=["gunung_titip aula_emas"]),
              C("Titipkan barang di tanganku ke Biara Awan.", "Send what's in my hand to the Cloud Monastery.", akibat=["gunung_titip biara"]),
              C("Berapa lama kau jadi kuli?", "How long have you been a porter?", ke="lama"),
              C("Gou Tie kuli yang baik?", "Is Gou Tie a good porter?", ke="murid_kuli", syarat=["bendera gunung_gou_tobat"]),
              C("Masih ada kabar tentang Gou Tie?", "Any word of Gou Tie?", ke="gunung_lain", syarat=["bendera gunung_gou_usir"]),
              C("Ada kabar dari tangga?", "News from the stairs?", ke="kabar")]),
        N("lama", "Lima puluh dua tahun. Seribu anak tangga, dua kali sehari, kecuali hari aku menikah dan hari istriku meninggal. Orang "
                  "bilang kakiku besi. Bukan besi. Cuma kebiasaan yang tidak tahu cara berhenti.",
          "Fifty-two years. A thousand steps, twice a day, except the day I married and the day my wife died. People say my legs are iron. "
          "Not iron. Just a habit that doesn't know how to stop.", gestur="cerita"),
        N("murid_kuli", "Pundaknya bagus, napasnya jelek. Minggu pertama dia muntah di bordes ketiga. Minggu kedua di bordes keenam. "
                        "Minggu ini dia sampai puncak dan masih sempat memaki. Kakeknya dulu kuli di regu yang sama denganku. Aku tidak "
                        "pernah bilang padanya. Belum.",
          "Good shoulders, bad breath. The first week he threw up on the third landing. The second week on the sixth. This week he reached "
          "the top and still had wind to curse. His grandfather was a porter in the same crew as me. I've never told him. Not yet.",
          gestur="tepuk_bahu"),
        N("gunung_lain", "Kuli dari gunung sebelah bilang ada orang bercakar besi menawarkan emas untuk tebing mereka. Kepala kampung "
                         "mereka menawarinya teh dan menunjukkan jalan pulang. Kabar cepat naik tangga, kawan. Lebih cepat dari kuli.",
          "Porters from the next mountain say a man with an iron claw offered gold for their cliffs. Their headman offered him tea and showed "
          "him the road home. News climbs stairs fast, friend. Faster than porters.", gestur="bisik"),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_jing_yue_sapa", "tokoh:jing_yue", [
        N("mulai", "Lepaskan alas kakimu di teras, rendahkan suaramu di dalam aula. Puncak ini mendengar segalanya, terutama yang kita "
                   "pikir diam-diam.",
          "Leave your shoes on the terrace, lower your voice inside the hall. This summit hears everything, especially what we think in "
          "secret.", gestur="hormat_dalam", prop="tasbih_tangan", p=[
              C("Apa itu Restu Puncak?", "What is the Summit Blessing?", ke="restu"),
              C("Kapan Cahaya Budha muncul?", "When does the Buddha's Light appear?", ke="cahaya"),
              C("Patung gajah itu siapa?", "Who is the statue on the elephant?", ke="gajah")]),
        N("restu", "Daftarlah di Kuil Kaki, nyalakan dupa di sembilan bordes, sampailah ke sini sebelum hari berganti, tanpa terbang dan "
                   "tanpa kereta. Tujuh hari setelahnya, meditasimu di teras ini lebih dalam, dan di mana pun napasmu sedikit lebih "
                   "jernih. Sekali setiap tujuh hari. Langit tidak suka diborong.",
          "Register at the Foot Temple, light incense at the nine landings, reach this place before the day ends, without flying and without "
          "the car. For seven days after, your meditation on this terrace runs deeper, and anywhere your breath is a little clearer. Once "
          "every seven days. Heaven dislikes being bought in bulk.", gestur="bicara_tangan"),
        N("cahaya", "Pada fajar yang cerah, saat awan di bawah kita rata seperti kain, kadang muncul lingkaran pelangi dengan bayanganmu di "
                    "tengahnya. Berdirilah di teras pandang dan diamlah sepuluh tarikan napas. Orang dulu melompat ke dalamnya karena "
                    "mengira itu jalan ke surga. Jangan lompat.",
          "On clear dawns, when the clouds below lie flat as cloth, a rainbow ring sometimes appears with your shadow at its heart. Stand on "
          "the viewing terrace and be still for ten breaths. People once leapt into it, thinking it the road to paradise. Do not leap.",
          gestur="tatap_langit"),
        N("gajah", "Samantabhadra, Bodhisattva Laku Agung. Gajahnya bergading enam: enam indra yang dijinakkan. Patungnya batu putih, "
                   "dipahat empat generasi pemahat. Generasi ketiga salah memahat telinga kiri gajah, dan generasi keempat tidak berani "
                   "memperbaikinya.",
          "Samantabhadra, the Bodhisattva of Great Practice. His elephant has six tusks: the six senses tamed. The statue is white stone, "
          "carved by four generations of carvers. The third got the elephant's left ear wrong, and the fourth did not dare fix it.",
          gestur="cerita"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_yao_po_sapa", "tokoh:yao_po", [
        N("mulai", "Herba, herba, herba! Bawa ke sini, Nak, biar nenek timbang umurnya. Penilaian gratis. Nasihat juga gratis, tapi biasanya "
                   "lebih mahal akibatnya.",
          "Herbs, herbs, herbs! Bring them here, child, let Granny weigh their age. Appraisal is free. Advice is free too, but it usually "
          "costs more in the end.", gestur="tumbuk_herba", prop="herba", p=[
              C("Bagaimana menilai umur herba?", "How do you judge a herb's age?", ke="umur"),
              C("Apa itu Tali Panjat?", "What is a Climbing Rope?", ke="tali"),
              C("Kenapa harga herba naik, Nek?", "Why have herb prices gone up, Granny?", ke="harga", syarat=["bendera gunung_gou_suap"]),
              C("Ada kabar?", "Any news?", ke="kabar")]),
        N("umur", "Lihat ruas akarnya, cium getahnya, dengarkan bunyinya kalau dipatahkan. Herba seratus tahun bunyinya seperti ranting; "
                  "herba tiga ratus tahun bunyinya seperti orang tua yang batuk. Aula ini membeli separuh nilai, karena kami harus "
                  "mengeringkan dan memberi makan kucing.",
          "Look at the root joints, smell the sap, listen to it snap. A hundred-year herb sounds like a twig; a three-hundred-year herb sounds "
          "like an old man coughing. This hall buys at half value, because we must dry them and feed the cat.", gestur="lihat_barang"),
        N("tali", "Lima belas Nilai Batu, sekali pakai. Ikat di pinggang sebelum memetik: kalau kau jatuh dari tebing kota ini, tali itu "
                  "menahanmu sekali lalu putus. Lebih murah dari peti mati, lebih murah lagi dari tabib.",
          "Fifteen Spirit Stones, single use. Tie it at your waist before picking: if you fall from this town's cliffs, it catches you once "
          "and snaps. Cheaper than a coffin, and cheaper still than a physician.", gestur="tertawa"),
        N("harga", "Karena ada orang bertangan besi yang berdiri di samping timbanganku tiap pagi dan mengangguk atau menggeleng. Seperlima "
                   "untuk dia, Nak. Pemetik muda sekarang memanjat lebih tinggi dan lebih nekat untuk upah yang sama. Dua jatuh bulan ini. "
                   "Tali Panjat terjual habis. Nenek tidak senang menjualnya.",
          "Because a man with an iron hand stands beside my scale every morning and nods or shakes his head. A fifth for him, child. Young "
          "pickers now climb higher and more recklessly for the same pay. Two fell this month. The Climbing Ropes are sold out. Granny is "
          "not happy to sell them.", gestur="sikap_lipat_tangan", prop="herba"),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_gou_kuli", "tokoh:gou_tie", [
        N("mulai", "Jangan menatapku begitu. Ya, Cakar Besi memikul karung. Dua kali sehari. Lao Dan bilang dalam sepuluh tahun aku akan "
                   "berhenti membenci tangga. Aku bilang lima.",
          "Don't look at me like that. Yes, Iron Claw carries sacks. Twice a day. Lao Dan says in ten years I'll stop hating the stairs. I "
          "say five.", gestur="angkat_bawa", prop="rangka_punggung", p=[
              C("Kau terlihat lebih tenang.", "You look calmer.", ke="tenang"),
              C("Selamat bekerja.", "Good work.")]),
        N("tenang", "Tidur nyenyak itu aneh. Dulu aku menghitung emas sampai pagi. Sekarang aku menghitung anak tangga dan tertidur di "
                    "tangga ke-tiga ratus.",
          "Sleeping soundly is strange. I used to count gold until morning. Now I count steps and fall asleep around the three hundredth.",
          gestur="tertawa"),
    ], syarat=["bendera gunung_gou_tobat"], prioritas=5)
    # delivery-room keepers (JasaKuli condition gunung_titipan)
    for did, peran, tempat, teks in (
            ("tema_gunung_titipan_biara", "peran:biksu_gunung", "biara",
             ("Ada titipan atas namamu, dibawa naik oleh kuli pagi ini. Biara menjaganya dengan baik; kucing biara hanya mengendusnya.",
              "There are parcels in your name, carried up by a porter this morning. The monastery kept them well; the monastery cat only "
              "sniffed them.")),
            ("tema_gunung_titipan_aula", "peran:pelayan", "aula_emas",
             ("Titipanmu sudah tiba di Aula Emas. Kuli itu naik sambil bernyanyi; suaranya jelek, tapi barangmu utuh.",
              "Your parcels have arrived at the Golden Hall. The porter sang all the way up; his voice was awful, but your goods are whole."))):
        dialog(did, peran, [
            N("mulai", teks[0], teks[1], gestur="angkat_bawa", prop="peti", p=[
                C("Ambil titipanku.", "Collect my parcels.", akibat=["gunung_ambil " + tempat]),
                C("Nanti saja.", "Later.")]),
        ], syarat=[G_, "gunung_titipan " + tempat], prioritas=30)
    dialog("tema_gunung_peziarah", "peran:peziarah", [
        N("mulai", "Amitabha. Kau juga mendaki? Kakiku sudah tidak muda, tapi hatiku masih ingin melihat atap emas itu sekali lagi.",
          "Amitabha. Are you climbing too? My legs are not young, but my heart still wants to see that golden roof once more.",
          gestur="sujud_dupa", prop="tas_dupa", p=[
              C("Apa ada nasihat untuk pendakian?", "Any advice for the climb?", ke="nasihat"),
              C("Ada kabar di jalan?", "Any news on the road?", ke="kabar"),
              C("Semoga selamat sampai puncak.", "May you reach the summit safely.")]),
        N("nasihat", "Tiga langkah, satu sujud, kalau kau ingin rendah hati. Satu langkah, satu napas, kalau kau ingin sampai. Dan sembunyikan "
                     "bakpaomu dari kera. Itu bukan ajaran Buddha, itu pengalaman.",
          "Three steps, one prostration, if you want humility. One step, one breath, if you want to arrive. And hide your buns from the "
          "monkeys. That's not the Buddha's teaching, that's experience.", gestur="tertawa"),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_kuli", "peran:kuli_gunung", [
        N("mulai", "Minggir sedikit, kawan, muatan lewat! ... Hah. Seratus anak tangga lagi sampai bordes, lalu sembilan ratus sisanya. "
                   "Kuli menghitung mundur; peziarah menghitung maju. Makanya peziarah lebih cepat lelah.",
          "Step aside a little, friend, load coming through! ... Hah. A hundred steps to the landing, then nine hundred more. Porters count "
          "down; pilgrims count up. That's why pilgrims tire faster.", gestur="daki_pikul", prop="rangka_punggung", p=[
              C("Ada kabar dari tangga?", "News from the stairs?", ke="kabar"),
              C("Semangat!", "Keep it up!")]),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=[G_], prioritas=1)
    dialog("tema_gunung_biksu", "peran:biksu_gunung", [
        N("mulai", "Amitabha. Kalau kau datang untuk berziarah, daftarlah di Kuil Kaki. Kalau kau datang untuk lari dari sesuatu, tangga "
                   "ini juga menerima pelarian; mereka biasanya berhenti lari di bordes kelima.",
          "Amitabha. If you came on pilgrimage, register at the Foot Temple. If you came to run from something, these stairs take runaways "
          "too; they usually stop running at the fifth landing.", gestur="hormat_dalam", prop="tasbih_tangan", p=[
              C("Apa yang dilakukan biksu sepanjang hari?", "What do the monks do all day?", ke="hari"),
              C("Ada kabar?", "Any news?", ke="kabar")]),
        N("hari", "Lonceng subuh, ikan kayu, bubur, menyapu daun yang tidak pernah habis, makan siang tanpa bicara, ceramah Kepala Biara, "
                  "doa senja, meditasi malam. Lalu besok daunnya jatuh lagi. Itulah ajarannya.",
          "The dawn bell, the wooden fish, porridge, sweeping leaves that never run out, a silent lunch, the Abbot's lecture, evening "
          "prayers, night meditation. Then tomorrow the leaves fall again. That is the teaching.", gestur="sapu", prop="sapu"),
        N("kabar", None, None, gestur="bisik"),
    ], syarat=[G_], prioritas=1)


# ------------------------------------------------------------------------------------------------ rumours

def cerita_kabar():
    kabar("tema_gunung_ziarah_1", "acara_tema_ziarah", "Musim Ziarah sudah mulai di Kota Gunung. Katanya barisan dupa di tangga bisa terlihat dari tiga lembah.",
          "The Pilgrimage Season has begun at the Mountain Town. They say the line of incense on the stairs can be seen from three valleys.")
    kabar("tema_gunung_ziarah_2", "acara_tema_ziarah", "Kios-kios di Pasar Peziarah menggandakan dupa dan tongkat bambu. Lonceng Awan dibunyikan setiap siang.",
          "The stalls of the Pilgrims' Market doubled their incense and bamboo staffs. The Cloud Bell rings every noon.")
    kabar("tema_gunung_cahaya_1", "acara_tema_cahaya_budha", "Pagi tadi ada lingkaran pelangi di atas awan Puncak Emas. Para biksu bersujud semua.",
          "This morning a rainbow ring stood on the clouds of the Golden Summit. All the monks bowed down.")
    kabar("tema_gunung_cahaya_2", "acara_tema_cahaya_budha", "Seorang peziarah bilang ia melihat bayangannya sendiri di tengah Cahaya Budha, dan bayangannya tersenyum.",
          "A pilgrim says he saw his own shadow inside the Buddha's Light, and the shadow smiled.")
    kabar("tema_gunung_longsor_1", "acara_tema_longsor", "Longsor di Kota Gunung! Jalan papan ditutup rantai; jangan naik ke tebing selama hujan.",
          "A rockslide at the Mountain Town! The plank walk is chained shut; stay off the cliffs while it rains.")
    kabar("tema_gunung_longsor_2", "acara_tema_longsor", "Batu sebesar kerbau menggelinding di tangga atas tadi malam. Untung para kuli sedang tidur.",
          "A stone as big as a buffalo rolled down the upper stairs last night. Lucky the porters were asleep.")
    kabar("tema_gunung_macet", "acara_tema_lift_macet", "Kereta gantung Paman Tie Suo macet di tengah jurang. Ia bersumpah ini bukan salah talinya.",
          "Uncle Tie Suo's cable car stalled above the gorge. He swears it isn't the cable's fault.")
    kabar("tema_gunung_tali_1", "quest_tema_gunung_tali", "%s memasang tali baru di kereta gantung Kota Gunung. Lonceng Awan berbunyi untuknya.",
          "%s fitted a new cable to the Mountain Town's cable car. The Cloud Bell rang for them.", umur=7)
    kabar("tema_gunung_tali_2", "quest_tema_gunung_tali", "Konon tali kereta gantung yang baru dipintal kera putih. %s yang membawanya turun dari tebing.",
          "They say the cable car's new rope was spun by a white ape. %s brought it down from the cliff.", umur=7)
    kabar("tema_gunung_ibu", "quest_tema_ziarah_ibu", "Sastrawan sombong di Kuil Kaki akhirnya menyalakan dupa untuk ibunya. %s mendaki untuknya.",
          "The haughty scholar at the Foot Temple finally lit incense for his mother. %s climbed for him.", umur=4)
    kabar("tema_gunung_kuli", "quest_tema_kuli_sehari", "%s memikul tiga muatan sampai ke Aula Emas tanpa terbang. Lao Dan bilang masih kurang cepat.",
          "%s carried three loads all the way to the Golden Hall without flying. Lao Dan says still not fast enough.", umur=3)
    kabar("tema_gunung_kera", "quest_tema_kera_nakal", "Kera-kera Kota Gunung kehilangan lima rampasan gara-gara %s. Mereka sedang merencanakan balas dendam.",
          "The Mountain Town monkeys lost five prizes because of %s. They are plotting revenge.", umur=3)
    kabar("tema_gunung_rumput", "quest_tema_rumput_awan", "Kepala Biara Jue Yuan akhirnya menyeduh teh Rumput Awan Seribu Tahun. %s yang memetiknya.",
          "Abbot Jue Yuan finally steeped his Thousand-Year Cloud Grass tea. %s picked it.", umur=5)
    kabar("tema_gunung_gou_tobat", "tema_gunung_gou_tobat", "Gou Tie si Cakar Besi sekarang memikul karung untuk biara. Ada yang bilang ia bersiul di tangga.",
          "Gou Tie the Iron Claw now hauls sacks for the monastery. Some say he whistles on the stairs.", umur=7)
    kabar("tema_gunung_gou_usir", "tema_gunung_gou_usir", "Gou Tie diusir dari Kota Gunung. Para pemetik tebing berpesta dengan bakpao sayur.",
          "Gou Tie was driven out of the Mountain Town. The cliff pickers feasted on vegetable buns.", umur=5)
    kabar("tema_gunung_gou_suap", "tema_gunung_gou_suap", "Harga herba di Kota Gunung naik seperlima. Orang berbisik Gou Tie membeli seseorang.",
          "Herb prices at the Mountain Town rose by a fifth. People whisper that Gou Tie bought someone.", umur=7)
    kabar("tema_gunung_gou_tobat_2", "tema_gunung_gou_tobat_2",
          "Para biksu muda bertaruh berapa lama Cakar Besi bertahan memikul. Lao Dan bertaruh seumur hidup, dan dia belum pernah kalah taruhan.",
          "The young monks are betting how long the Iron Claw lasts as a porter. Lao Dan bet a lifetime, and he has never lost a bet.",
          umur=7, penyebar=["biksu_gunung", "kuli_gunung", "kepala_biara"])
    kabar("tema_gunung_gou_usir_2", "tema_gunung_gou_usir_2",
          "Jerat-jerat roh terakhir Gou Tie dibakar di halaman Aula Herba. Asapnya berbau getah pinus, dan kera-kera menonton dari atap.",
          "The last of Gou Tie's spirit snares were burned in the Herb Hall yard. The smoke smelled of pine sap, and the monkeys watched "
          "from the roof.", umur=5, penyebar=["pemetik_herba", "kuli_gunung"])
    kabar("tema_gunung_gou_suap_2", "tema_gunung_gou_suap_2",
          "Shan Ling meninggalkan Kota Gunung sebelum fajar. Keranjangnya tertinggal di depan gerbang Biara Awan, dan tidak ada biksu yang "
          "berani memindahkannya.",
          "Shan Ling left the Mountain Town before dawn. Her basket was left at the Cloud Monastery gate, and no monk dares move it.",
          umur=10, penyebar=["pemetik_herba", "biksu_gunung", "peziarah"])
    # background talk of the town's own people
    for i, (a, b, who) in enumerate((
            ("Kereta gantung itu memang cepat, tapi kuli tidak pernah macet di tengah jurang.", "The cable car is fast, sure, but porters never stall above a gorge.", ["kuli_gunung"]),
            ("Lututku berbunyi di setiap bordes. Mungkin itu caranya berdoa.", "My knees creak at every landing. Perhaps that's how they pray.", ["kuli_gunung", "peziarah"]),
            ("Kera-kera di bordes kelima paling jahat. Mereka mengincar dupa yang menyala.", "The monkeys at the fifth landing are the worst. They go after lit incense.", ["peziarah"]),
            ("Aku datang dari kota pelabuhan. Laut tidak setinggi ini, tapi lebih sabar.", "I came from the harbour town. The sea is not this high, but it is more patient.", ["peziarah"]),
            ("Daun di halaman biara tidak pernah habis. Kepala Biara bilang itu bagus untuk kami.", "The leaves in the monastery yard never run out. The Abbot says that's good for us.", ["biksu_gunung"]),
            ("Lonceng Awan berbunyi paling jernih saat kabut tebal. Aneh, tapi benar.", "The Cloud Bell sounds clearest in thick mist. Strange, but true.", ["biksu_gunung", "kepala_biara"]),
            ("Ceruk ketiga dari atas paling murah hati sesudah hujan.", "The third niche from the top is most generous after rain.", ["pemetik_herba"]),
            ("Jangan pernah memetik dengan tangan kiri memegang bakpao. Kera menunggu itu.", "Never pick with a bun in your left hand. The monkeys wait for that.", ["pemetik_herba"]),
            ("Roda besar perlu minyak lagi. Tie Suo bilang roda itu lebih tua dari istrinya, dan lebih setia.", "The great wheel needs oil again. Tie Suo says it's older than his wife, and more faithful.", ["penjaga_lift"]),
            ("Badai petir? Kereta berhenti. Itu satu-satunya aturan yang tidak pernah dilanggar Tie Suo.", "Thunderstorm? The car stops. That's the one rule Tie Suo never breaks.", ["penjaga_lift", "kuli_gunung"]),
            ("Sastrawan di Kuil Kaki menghafal tiga ratus puisi, tapi belum pernah sampai bordes kedua tanpa duduk.", "The scholar at the Foot Temple knows three hundred poems by heart, but has never reached the second landing without sitting down.", ["biksu_gunung", "kuli_gunung", "peziarah"]),
            ("Kera Putih Tua hanya turun kalau kabut setinggi pinggang. Yang melihatnya, katanya, tidak akan jatuh dari tebing tahun itu.", "The Old White Ape only comes down when the mist is waist-high. Whoever sees it, they say, won't fall from the cliff that year.", ["pemetik_herba", "peziarah"]),
            ("Bakpao kacang merah di Pasar Kaki paling enak dimakan di bordes keempat. Jangan tanya kenapa; begitu kata nenekku.", "The red bean buns at the Foot Market taste best eaten on the fourth landing. Don't ask why; my grandmother said so.", ["kuli_gunung", "peziarah", "pelayan"]),
            ("Perpustakaan Sutra menyimpan satu gulungan yang tidak boleh dibuka sebelum lonceng keseratus delapan. Belum ada yang tahu kenapa.", "The Sutra Library keeps one scroll that may not be opened before the hundred-and-eighth bell. Nobody knows why yet.", ["biksu_gunung", "kepala_biara"]),
    )):
        kabar("tema_gunung_umum_%d" % i, "umum", a, b, bobot=6, umur=1, penyebar=who)


# ------------------------------------------------------------------------------------------------ advancements and loot

def cerita_adv_loot():
    for aid, icon, frame, title, desc in (
            ("cahaya_budha", "minecraft:glowstone_dust", "goal", ("Bayangan di Pelangi", "A Shadow in the Rainbow"),
             ("Terima Restu Cahaya saat Fajar Cahaya Budha di Aula Emas.", "Receive the Blessing of Light during the Buddha's Light at the Golden Hall.")),
            ("tujuh_ziarah", "minecraft:candle", "challenge", ("Tujuh Kali Seribu", "Seven Times a Thousand"),
             ("Selesaikan Ziarah Seribu Anak Tangga tujuh kali.", "Complete the Pilgrimage of a Thousand Steps seven times.")),
            ("ibu_peziarah", "kultivator:tema/dupa_ziarah", "task", ("Dupa untuk Ibu", "Incense for Mother"),
             ("Mendaki untuk ibu Pei Wenzhi dan ceritakan puncaknya padanya.", "Climb for Pei Wenzhi's mother and tell him about the summit.")),
            ("kuli_sehari", "kultivator:tema/tongkat_kuli", "task", ("Kuli Sehari", "Porter for a Day"),
             ("Pikul tiga muatan kuli ke atas tanpa terbang.", "Carry three porter's loads up without flying.")),
            ("kera_nakal", "minecraft:sweet_berries", "task", ("Pajak Kera Dibatalkan", "Monkey Tax Repealed"),
             ("Rebut kembali lima barang curian kera untuk para peziarah.", "Win back five things the monkeys stole from the pilgrims.")),
            ("rumput_awan", "kultivator:tema/rumput_awan_seribu_tahun", "goal", ("Teh Seumur Hidup", "A Lifetime's Tea"),
             ("Bawakan Rumput Awan Seribu Tahun untuk ramuan Kepala Biara.", "Bring Thousand-Year Cloud Grass for the Abbot's brew.")),
            ("tobat_gou", "minecraft:lead", "goal", ("Cakar yang Memikul", "The Claw That Carries"),
             ("Serahkan Gou Tie ke biara supaya ia menebus dosanya sebagai kuli.", "Hand Gou Tie to the monastery to atone as a porter."))):
        path = "tema_gunung_" + aid
        ADV[path] = {"parent": "kultivator:tempat/tema_gunung",
                     "criteria": {"done": {"trigger": "minecraft:impossible"}},
                     "display": {"description": {"translate": "advancement.kultivator.tempat.%s.description" % path},
                                 "icon": {"id": icon},
                                 "title": {"translate": "advancement.kultivator.tempat.%s.title" % path},
                                 "frame": frame},
                     "requirements": [["done"]]}
        L("advancement.kultivator.tempat.%s.title" % path, *title)
        L("advancement.kultivator.tempat.%s.description" % path, *desc)

    def item(name, weight, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": name, "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e

    def tabel(tid_, rolls, entries, extra=None):
        pools = [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}]
        if extra:
            pools.append({"rolls": 1, "entries": extra})
        LOOT["tema/gunung/" + tid_] = {"type": "minecraft:chest", "random_sequence": "kultivator:tema/gunung/" + tid_, "pools": pools}

    # the Old White Ape's nest: trinkets the apes "collected" over a century, and a root of the cliff
    tabel("sarang_kera", (2, 4), [
        item("minecraft:gold_nugget", 12, 2, 6), item("minecraft:amethyst_shard", 8, 1, 3), item("minecraft:emerald", 4, 1, 2),
        item("kultivator:tema/dupa_ziarah", 8, 2, 5), item("minecraft:glow_berries", 6, 2, 6), item("minecraft:copper_ingot", 6, 1, 3),
        item("kultivator:dagang/teh_awan_gunung", 4, 1, 2)],
        extra=[item("kultivator:dagang/lingzhi_gunung", 1)])
    # the abbey's gift basket at the first finished pilgrimage
    tabel("persembahan_ziarah", (2, 3), [
        item("kultivator:tema/dupa_ziarah", 10, 4, 8), item("kultivator:dagang/dupa_gunung", 8, 2, 4), item("minecraft:candle", 6, 1, 3),
        item("kultivator:tema/daun_teh_embun", 6, 2, 5), item("minecraft:gold_nugget", 4, 2, 5)],
        extra=[item("kultivator:tema/teh_embun_gunung", 1)])
    # Lao Dan's porter's lunch
    tabel("bekal_kuli", (2, 3), [
        item("minecraft:bread", 10, 2, 4), item("minecraft:baked_potato", 8, 2, 4), item("kultivator:dagang/teh_awan_gunung", 4, 1, 2),
        item("minecraft:string", 6, 2, 5), item("minecraft:lead", 3)],
        extra=[item("kultivator:tema/tali_panjat", 1)])
    # what Gou Tie's hunters carried
    tabel("rampasan_pemburu", (2, 4), [
        item("minecraft:iron_nugget", 10, 3, 8), item("minecraft:lead", 6, 1, 2), item("kultivator:tema/tali_panjat", 6, 1, 2),
        item("minecraft:shears", 3), item("minecraft:emerald", 3, 1, 2), item("minecraft:gold_ingot", 2)],
        extra=[item("kultivator:dagang/lingzhi_gunung", 1)])


# ------------------------------------------------------------------------------------------------ schedule file

JADWAL = {
    "jenis": "kota_tema/gunung",
    "acara": {
        # the Buddha's Light (a short dawn event, 05:00-07:00): merchants and children stop to look as well; the built-in
        # table (JadwalTema) answers the monks, pilgrims and everyone else. Multi-day events (Musim Ziarah) stay built-in
        # because entries here have no hour gate.
        "tema.cahaya_budha": {
            "pedagang": {"kegiatan": "cahaya_budha", "tujuan": "pandang", "klip": "tatap_langit"},
            "anak": {"kegiatan": "cahaya_budha", "tujuan": "pandang", "klip": "tunjuk"},
        },
    },
}


# ------------------------------------------------------------------------------------------------ writer

def tulis_cerita():
    lang_sistem()
    cerita_tali()
    cerita_samping()
    cerita_sapa()
    cerita_kabar()
    cerita_adv_loot()
    tempat = os.path.join(structgen.DATA, "tempat")
    for did, obj in DIALOG.items():
        structgen.write_json(os.path.join(tempat, "dialog", did + ".json"), obj)
    for qid, obj in QUEST.items():
        structgen.write_json(os.path.join(tempat, "quest", qid + ".json"), obj)
    structgen.write_json(os.path.join(tempat, "kabar", "tema_gunung.json"), {"templat": KABAR})
    structgen.write_json(os.path.join(tempat, "jadwal", "kota_tema_gunung.json"), JADWAL)
    for path, obj in ADV.items():
        structgen.write_json(os.path.join(structgen.DATA, "advancement", "tempat", path + ".json"), obj)
    for path, obj in LOOT.items():
        structgen.write_json(os.path.join(structgen.DATA, "loot_table", path + ".json"), obj)
    lang_dir = os.path.join(structgen.REPO, "lang-src")
    for idx, code in ((0, "id_id"), (1, "en_us")):
        structgen.write_json(os.path.join(lang_dir, "tempat_tema_gunung.%s.json" % code), {k: v[idx] for k, v in LANG.items()})
    print("kota_tema/gunung cerita: %d quests, %d dialogs, %d rumours, %d advancements, %d loot tables, %d lang keys"
          % (len(QUEST), len(DIALOG), len(KABAR), len(ADV), len(LOOT), len(LANG)))


def main(argv):
    if "--cerita" in argv:          # story data and lang only (no NBT)
        tulis_cerita()
        return
    only = [a for i, a in enumerate(argv) if not a.startswith("-") and (i == 0 or argv[i - 1] != "--pratinjau")]
    if not only:
        bersihkan()
    build_walls()
    for name, fn in BUILDERS:
        if not only or name in only:
            fn()
    write_processors()
    write_pools()
    write_structures()
    tl.cek_semua()
    if "--pratinjau" in argv:
        pratinjau(argv[argv.index("--pratinjau") + 1])
    if not only:
        tulis_cerita()
    total = sum(os.path.getsize(p) for p, _ in structgen._SAVED)
    print("kota_tema/gunung: %d templates, %d pools, gzip %d bytes" % (len(structgen._SAVED), len(POOLS), total))


if __name__ == "__main__":
    main(sys.argv[1:])
