"""Kota Besar world generation (DESIGN 38.B.2-38.B.3, module 38KS): the capital's eleven districts.

Writes every NBT lot (data/kultivator/structure/kota_besar/**), the template pools (base + one pool per biome style),
processor lists, the eleven kultivator:tata_letak structures, the biome tags and, in the built-in pack
resourcepacks/dunia_kotabesar, the eleven structure sets on kultivator:rencana_tempat (salt 380001-380011). Run:

    python tools/gen_tempat_kota_besar.py            (then python tools/cek_tempat.py)

Plan (38.B.2.2, before rotation, district cells 144 blocks, lots 48, north = -z, the Great South Gate faces south):

             x = -1           x = 0          x = +1          outside
  z = -1   [cendekia]       [istana]       [awan]
  z =  0   [gilda]          [pusat]        [pasar]         [kumuh]  (outside the east gate)
  z = +1   [hunian_barat]   [gerbang]      [arena]
  outside                   [luar]  (caravan field outside the Great South Gate)

Every district is drawn on one canvas (kota_besar_kit.Kanvas) and cut into its 3 x 3 lots, so halls that span two or four
lots stay whole. The city wall (22 high, walkway at +16, crenels every 2, arrow slits) runs in an 8-block band just
outside the nine inner cells, with a tower segment in the middle of every outer side, four corner towers, the Great
South Gate (three arches, a three-storey gatehouse) and the east and west gates; all of them are separate wall pieces
(tembok/*) placed by the edge districts with a rotation per side. Only world generation in NEW chunks ever places these
templates (DESIGN 38.0 rule 1); gates, cell doors, lanterns and fireworks at runtime are entities or client pictures.
Deterministic: the same bytes on every run.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import kota_besar_kit as K  # noqa: E402
from kota_besar_kit import LY, FACE, OPP  # noqa: E402

F = "kota_besar"
NS = "kultivator:"
SEL = 144
LUAR = 296
SALT0 = 380001
PACK = "dunia_kotabesar"

DISTRIK = {  # id -> (centre x, centre z) before rotation, salt index
    "cendekia": (-SEL, -SEL), "istana": (0, -SEL), "awan": (SEL, -SEL),
    "gilda": (-SEL, 0), "pusat": (0, 0), "pasar": (SEL, 0),
    "hunian_barat": (-SEL, SEL), "gerbang": (0, SEL), "arena": (SEL, SEL),
    "kumuh": (LUAR, 0), "luar": (0, LUAR),
}
ORDER = ["cendekia", "istana", "awan", "gilda", "pusat", "pasar", "hunian_barat", "gerbang", "arena", "kumuh", "luar"]

# lots per district: list of dicts {dx, dz, pool, putar, utuh}
LOTS = {d: [] for d in ORDER}
# pool id -> (templates, utuh)
POOLS = {}


def dxz(i):
    return -72 + tl.LOT * i


def tid(name):
    return "%s%s/%s" % (NS, F, name)


def daftar_lot(distrik, i, j, name, utuh=False, pool=None):
    """Registers lot (i, j) of a district; pool defaults to the lot's own template."""
    pid = pool or name
    LOTS[distrik].append({"dx": dxz(i), "dz": dxz(j), "pool": pid, "putar": 0})
    if pool is None:
        POOLS.setdefault(pid, ([tid(name)], utuh))


def daftar_bebas(distrik, dx, dz, pool, putar, utuh=True):
    LOTS[distrik].append({"dx": dx, "dz": dz, "pool": pool, "putar": putar})
    POOLS.setdefault(pool, ([tid(pool)], utuh))


def simpan_kanvas(k, distrik, names, utuh=(), tinggi_min=24):
    """Cuts a district canvas into its lots, saves them and registers them. names[(i, j)] = lot name."""
    full = {ij: "%s/%s" % (distrik, n) for ij, n in names.items() if n}
    lots = K.potong(k, full, tinggi_min=tinggi_min)
    for ij, lot in lots.items():
        lot.simpan()
        daftar_lot(distrik, ij[0], ij[1], full[ij], utuh=names[ij] in utuh)
    return lots


def kanvas(distrik, h=64, seed_extra=0, palette=None):
    k = K.Kanvas(SEL, h, SEL, seed=tl._seed(F + "/" + distrik) + seed_extra, palette=palette)
    return k


# ===================================================================================================== city wall
WALL_H = 22      # wall height over the street floor
WALK = 16        # walkway level over the street floor
M = 2            # margin around every wall piece (roof overhangs, steps); only air or nothing is written there
BAND = 8         # wall band depth


def _wall_core(p, x0, x1, z0=M, y=LY, depth=BAND, walk=WALK, slits=True, stairs_at=None):
    """A wall run along x from x0 to x1, outside at z0, inside at z0 + depth - 1: stone brick body with a polished
    andesite plinth outside and a white plaster band inside, a drip course under the walkway, a dark oak and paving
    walkway at +walk, crenellated parapet (merlons every 2, tile caps) with arrow slits on the outside, a low parapet on
    the inside, lantern posts every 8. stairs_at = x where a two-wide flight climbs the inner face to the walkway."""
    zi = z0 + depth - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, zi + 1):
            p.put(x, y - 1, z, "found")
            p.put(x, y, z, "kerb")
        for yy in range(y + 1, y + walk):
            for z in range(z0, zi + 1):
                if z == z0 and yy <= y + 3:
                    role = "minecraft:polished_andesite"
                elif z == zi and yy in (y + walk - 3, y + walk - 2):
                    role = "plaster"
                elif (yy - y) % 5 == 0 and z in (z0, zi):
                    role = "kerb_chisel" if x % 4 == 0 else "kerb"
                else:
                    role = "kerb"
                p.put(x, yy, z, role)
        for z in range(z0 + 1, zi):
            p.put(x, y + walk, z, "wood" if z in (z0 + 2, zi - 2) else "paving")
        p.put(x, y + walk, z0, "kerb")
        p.put(x, y + walk, zi, "kerb")
        p.put(x, y + walk + 1, z0, "kerb")
        if x % 2 == 0:
            p.put(x, y + walk + 2, z0, "kerb")
            p.put(x, y + walk + 3, z0, "kerb")
            p.slab(x, y + walk + 4, z0, "tile_slab")
        else:
            p.slab(x, y + walk + 2, z0, "kerb_slab")
        if slits and x % 6 == 3:
            p.put(x, y + walk - 6, z0, "minecraft:iron_bars")
            p.put(x, y + walk - 7, z0, "minecraft:iron_bars")
        p.put(x, y + walk + 1, zi, "kerb_wall")
        if x % 8 == 4:
            K.post(p, x, y + walk + 1, zi - 1, height=2, post_role="wood_fence")
        if z0 - 1 >= 0:
            p.stairs(x, y + walk - 1, z0 - 1, "kerb_stairs", "south", half="top")
    if stairs_at is not None:
        for i in range(walk):
            x = stairs_at + i
            if x > x1:
                break
            for z in (zi - 1, zi):
                p.stairs(x, y + 1 + i, z, "kerb_stairs", "east")
                for yy in range(y + 2 + i, y + walk + 1):
                    p.put(x, yy, z, "minecraft:air")
        # a rail beside the open flight
        for i in range(walk):
            x = stairs_at + i
            if x <= x1:
                p.put(x, y + walk, zi - 2, "kerb")


def tembok_lurus(name="tembok/lurus", variant=0):
    """48 x 30 x 12 wall run (outside north, band z 2..9). variant 0: a flight up to the walkway; variant 1: a roofed
    watch post on the walkway with a drum."""
    p = tl.Lot(F, name, tinggi=WALL_H + 11, lebar=48, dalam=BAND + 2 * M, palet=K.PAL)
    p.lights = []
    zi = M + BAND - 1
    _wall_core(p, 0, 47, stairs_at=12 if variant == 0 else None)
    for x in range(5, 48, 12):
        p.stairs(x, LY + WALK - 3, M - 1, "kerb_stairs", "north", half="top")
    if variant == 0:
        p.titik("jaga", 32, LY + WALK + 1, M + 3, "north")
        p.titik("jaga", 42, LY + WALK + 1, M + 3, "north")
        p.titik("jalan", 11, LY + 1, zi + 1, "south", {"urut": 0, "jalur": "tembok"})
        p.titik("jalan", 28, LY + WALK + 1, M + 4, "south", {"urut": 1, "jalur": "tembok"})
    else:
        for x, z in ((20, M + 1), (27, M + 1), (20, zi - 1), (27, zi - 1)):
            for dy in range(1, 5):
                p.put(x, LY + WALK + dy, z, "red")
        K.beam_band(p, 20, M + 1, 27, zi - 1, LY + WALK + 5)
        K.gable(p, 20, LY + WALK + 6, M + 1, 27, zi - 1, along="x", overhang=1, pearl=True, role="tile_stairs",
                slab_role="tile_slab", full_role="tile")
        K.lit(p, 23, LY + WALK + 5, M + 3, chain=1)
        K.drum(p, 25, LY + WALK + 1, zi - 2, "north")
        for x in (22, 25):
            p.titik("jaga", x, LY + WALK + 1, M + 2, "north")
        p.titik("jaga", 8, LY + WALK + 1, M + 3, "north")
        p.titik("jaga", 40, LY + WALK + 1, M + 3, "north")
    for x in range(2, 48, 9):
        K.wall_banner(p, x, LY + WALK - 4, zi + 1, "south")
    p.simpan()
    return name


def _square_tower(p, x0, z0, size, y=LY, open_sides=(), walk=WALK, door_side=None):
    """A square wall tower: stone body to the walkway (hollow, slits, a ladder), openings to the wall walk on
    open_sides, a plastered guard room with lattice windows above, a parapet ring, a two-tier hip tile roof."""
    x1, z1 = x0 + size - 1, z0 + size - 1
    for yy in range(y, y + walk + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if yy == y or edge:
                    p.put(x, yy, z, "kerb_chisel" if edge and (yy - y) % 6 == 0 else "kerb")
                elif yy == y + walk:
                    p.put(x, yy, z, "wood")
                else:
                    p.put(x, yy, z, "minecraft:air")
    for yy in (y + 6, y + 11):
        for x in range(x0 + 3, x1 - 1, 4):
            p.put(x, yy, z0, "minecraft:iron_bars")
        for z in range(z0 + 3, z1 - 1, 4):
            p.put(x0, yy, z, "minecraft:iron_bars")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                p.put(x, y + walk + 1, z, "kerb_wall")
    gr = 6
    p.walls(x0 + 2, y + walk + 1, z0 + 2, x1 - 2, y + walk + gr, z1 - 2, role="plaster", pillar="red", windows=0)
    for x in range(x0 + 3, x1 - 2):
        if (x - x0) % 4 in (1, 2):
            K.lattice(p, x, y + walk + 3, z0 + 2, "north")
            K.lattice(p, x, y + walk + 3, z1 - 2, "south")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for side in open_sides:
        for dy in range(1, 4):
            for w in (-1, 0, 1):
                if side == "east":
                    for x in (x1 - 2, x1 - 1, x1):
                        p.put(x, y + walk + dy, cz + w, "minecraft:air")
                elif side == "west":
                    for x in (x0, x0 + 1, x0 + 2):
                        p.put(x, y + walk + dy, cz + w, "minecraft:air")
                elif side == "south":
                    for z in (z1 - 2, z1 - 1, z1):
                        p.put(cx + w, y + walk + dy, z, "minecraft:air")
                else:
                    for z in (z0, z0 + 1, z0 + 2):
                        p.put(cx + w, y + walk + dy, z, "minecraft:air")
    K.beam_band(p, x0 + 2, z0 + 2, x1 - 2, z1 - 2, y + walk + gr)
    p.fill(x0 + 3, y + walk + gr, z0 + 3, x1 - 3, y + walk + gr, z1 - 3, "wood")
    K.brackets(p, x0 + 2, z0 + 2, x1 - 2, z1 - 2, y + walk + gr)
    K.hip(p, x0 + 1, y + walk + gr + 1, z0 + 1, x1 - 1, z1 - 1, overhang=1, tiers=2, tier_gap=2, tier_band=2,
          band_role="plaster", role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for yy in range(y + 1, y + walk + 1):
        p.put(x0 + 1, yy, z0 + 1, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    K.lit(p, cx, y + walk + gr, cz, chain=1)
    K.lit(p, cx, y + walk, cz, chain=2)
    if door_side:
        for dy in range(1, 4):
            for w in (0, 1):
                if door_side == "south":
                    p.put(cx + w - 1, y + dy, z1, "minecraft:air")
                else:
                    p.put(x1, y + dy, cz + w - 1, "minecraft:air")
    return cx, cz


def tembok_menara(name="tembok/menara"):
    """48 x H x 20 (margin 2 each side): the wall run (band z 10..17) with a 16 x 16 tower in the middle projecting 8
    blocks outward (z 2..17); a ground door from the inner lane, walk-through openings east and west."""
    D = 16 + 2 * M
    p = tl.Lot(F, name, tinggi=LY + WALK + 19, lebar=48, dalam=D, palet=K.PAL)
    p.lights = []
    _wall_core(p, 0, 47, z0=M + 8)
    _square_tower(p, 16, M, 16, open_sides=("east", "west"))
    zi = M + 15
    for dy in range(1, 4):
        for x in (23, 24):
            for z in range(M + 9, zi + 1):
                p.put(x, LY + dy, z, "minecraft:air")
    K.door(p, 23, LY + 1, zi, "south", "left")
    K.door(p, 24, LY + 1, zi, "south", "right")
    K.plaque(p, 24, LY + 4, zi, "x")
    K.wall_banner(p, 21, LY + 3, zi + 1, "south")
    K.wall_banner(p, 26, LY + 3, zi + 1, "south")
    p.titik("jaga", 24, LY + WALK + 1, M + 4, "north")
    p.titik("jaga", 19, LY + WALK + 1, M + 3, "west")
    p.titik("jaga", 28, LY + WALK + 1, M + 3, "east")
    p.titik("pintu", 23, LY + 1, zi + 1, "north")
    p.simpan()
    return name


def tembok_sudut(name="tembok/sudut"):
    """20 x H x 20 corner tower (NW: outside north and west; the band corner is x, z 10..17, the tower 2..17)."""
    S = 16 + 2 * M
    p = tl.Lot(F, name, tinggi=LY + WALK + 19, lebar=S, dalam=S, palet=K.PAL)
    p.lights = []
    _square_tower(p, M, M, 16, open_sides=("east", "south"))
    p.titik("jaga", 10, LY + WALK + 1, M + 3, "north")
    p.titik("jaga", M + 3, LY + WALK + 1, 10, "west")
    p.simpan()
    return name


def _gate_arch(p, cx, w, h, y=LY, z0=M, depth=BAND):
    half = w // 2
    for z in range(z0, z0 + depth):
        for x in range(cx - half, cx - half + w):
            for yy in range(y + 1, y + h + 1):
                p.put(x, yy, z, "minecraft:air")
            p.put(x, y, z, "imperial" if 0 < x - (cx - half) < w - 1 else "paving")
        p.stairs(cx - half, y + h, z, "kerb_stairs", "east", half="top")
        p.stairs(cx - half + w - 1, y + h, z, "kerb_stairs", "west", half="top")
        for x in range(cx - half + 1, cx - half + w - 1):
            p.put(x, y + h + 1, z, "kerb_chisel" if z in (z0, z0 + depth - 1) else "kerb")


def gerbang_agung(name="tembok/gerbang_agung"):
    """The Great South Gate, wall band part (48 x 44 x 12): three arches (centre 6 x 8, sides 4 x 6), a gatehouse over the
    walkway (a 30 x 8 hall with a second, smaller storey and a two-tier hip tile roof), name boards over the centre arch
    outside and inside, banners, guard posts, the city gate anchors (GerbangKotaEntity, 38.B.4.1) for each arch."""
    p = tl.Lot(F, name, tinggi=LY + WALK + 24, lebar=48, dalam=BAND + 2 * M, palet=K.PAL)
    p.lights = []
    _wall_core(p, 0, 47, slits=False)
    _gate_arch(p, 24, 6, 8)
    _gate_arch(p, 11, 4, 6)
    _gate_arch(p, 37, 4, 6)
    y = LY + WALK
    zo, zi = M, M + BAND - 1
    K.hall(p, 9, zo, 38, zi, y, 6, door_sides=("east", "west"), door_w=3, windows=3)
    K.brackets(p, 9, zo, 38, zi, y + 6)
    for x in range(8, 40):
        p.stairs(x, y + 7, zo - 1, "tile_stairs", "south")
        p.stairs(x, y + 7, zi + 1, "tile_stairs", "north")
    for z in range(zo, zi + 1):
        p.stairs(8, y + 7, z, "tile_stairs", "east")
        p.stairs(39, y + 7, z, "tile_stairs", "west")
    for cx_, cz_, f_ in ((8, zo - 1, "south"), (39, zo - 1, "south"), (8, zi + 1, "north"), (39, zi + 1, "north")):
        p.stairs(cx_, y + 8, cz_, "tile_stairs", f_, half="top")
    K.hall(p, 13, zo + 1, 34, zi - 1, y + 7, 5, door_sides=(), windows=3, plaque_top=False)
    K.brackets(p, 13, zo + 1, 34, zi - 1, y + 12)
    K.hip(p, 12, y + 13, zo, 35, zi, overhang=1, tiers=2, tier_gap=2, tier_band=1, band_role="plaster",
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for z in (zo, zi):
        for x in range(21, 28):
            p.put(x, LY + 11, z, "red" if x in (21, 27) else "gold" if x in (23, 24, 25) else "wood")
            p.put(x, LY + 12, z, "beam", {"axis": "x"})
    for x in (6, 17, 31, 42):
        K.wall_banner(p, x, LY + 13, zo - 1, "north")
        K.wall_banner(p, x, LY + 13, zi + 1, "south")
    for x in (2, 45):
        K.stone_lantern(p, x, LY + 1, zi + 2)
    p.jangkar(24.0, LY + 1, M + 4.0, "kotabesar/gerbang", yaw=0.0, lebar=6, tinggi=8, nama="selatan_agung")
    p.jangkar(11.0, LY + 1, M + 4.0, "kotabesar/gerbang", yaw=0.0, lebar=4, tinggi=6, nama="selatan_barat")
    p.jangkar(37.0, LY + 1, M + 4.0, "kotabesar/gerbang", yaw=0.0, lebar=4, tinggi=6, nama="selatan_timur")
    p.jangkar(20.5, LY + 1, zi + 1.5, "kota/penjaga", yaw=0.0)
    p.jangkar(28.5, LY + 1, zi + 1.5, "kotabesar/layanan", yaw=0.0, layanan="gerbang", peran="PENJAGA_GERBANG")
    for x in (18, 30):
        p.titik("jaga", x, LY + 1, zo - 1, "south")
    p.titik("jaga", 20, y + 1, M + 3, "south")
    p.titik("jaga", 28, y + 1, M + 3, "south")
    p.titik("tiba", 24, LY + 1, zi + 1, "north")
    p.simpan()
    return name


def gerbang_samping(name="tembok/gerbang_samping"):
    """East / west gate (48 x 40 x 12): one 5 x 7 arch, a gatehouse with a two-tier hip tile roof, a gate anchor."""
    p = tl.Lot(F, name, tinggi=LY + WALK + 20, lebar=48, dalam=BAND + 2 * M, palet=K.PAL)
    p.lights = []
    _wall_core(p, 0, 47, slits=False)
    _gate_arch(p, 24, 5, 7)
    y = LY + WALK
    zo, zi = M, M + BAND - 1
    K.hall(p, 14, zo, 33, zi, y, 6, door_sides=("east", "west"), door_w=3, windows=3)
    K.brackets(p, 14, zo, 33, zi, y + 6)
    K.hip(p, 14, y + 7, zo, 33, zi, overhang=2, tiers=2, tier_gap=2, tier_band=2, band_role="plaster",
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for z in (zo, zi):
        for x in range(21, 27):
            p.put(x, LY + 10, z, "red" if x in (21, 26) else "gold" if x in (23, 24) else "wood")
    for x in (10, 38):
        K.wall_banner(p, x, LY + 12, zo - 1, "north")
        K.wall_banner(p, x, LY + 12, zi + 1, "south")
    p.jangkar(24.0, LY + 1, M + 4.0, "kotabesar/gerbang", yaw=0.0, lebar=5, tinggi=7, nama="samping")
    p.jangkar(20.5, LY + 1, zi + 1.5, "kota/penjaga", yaw=0.0)
    p.titik("jaga", 20, LY + 1, zo - 1, "south")
    p.titik("jaga", 28, LY + 1, zo - 1, "south")
    p.titik("jaga", 24, y + 1, M + 3, "south")
    p.simpan()
    return name


SIDE_PUTAR = {"n": 0, "e": 1, "s": 2, "w": 3}
WALLS = {
    "cendekia": {"n": ["lurus", "menara", "lurus2"], "w": ["lurus2", "menara", "lurus"], "sudut": "nw"},
    "istana": {"n": ["lurus2", "menara", "lurus"]},
    "awan": {"n": ["lurus", "menara", "lurus2"], "e": ["lurus2", "menara", "lurus"], "sudut": "ne"},
    "gilda": {"w": ["lurus", "gerbang_samping", "lurus2"]},
    "pasar": {"e": ["lurus2", "gerbang_samping", "lurus"]},
    "hunian_barat": {"w": ["lurus", "menara", "lurus2"], "s": ["lurus2", "menara", "lurus"], "sudut": "sw"},
    "gerbang": {"s": ["lurus", "gerbang_agung", "lurus2"]},
    "arena": {"e": ["lurus", "menara", "lurus2"], "s": ["lurus", "menara", "lurus2"], "sudut": "se"},
}
WALL_POOL = {"lurus": "tembok/lurus", "lurus2": "tembok/lurus_jaga", "menara": "tembok/menara",
             "gerbang_samping": "tembok/gerbang_samping", "gerbang_agung": "tembok/gerbang_agung"}


def wall_lots():
    """Adds the wall pieces of every edge district (rotation per side, 38.B.2.5). A run piece is 12 deep (band 8 +
    margins), a tower piece 20 (band 8 + tower 8 outward + margins); the band always starts 72 blocks from the district
    centre (inner cell edge) and runs to 79."""
    for d, spec in WALLS.items():
        for side, kinds in spec.items():
            if side == "sudut":
                continue
            for idx, kind in enumerate(kinds):
                deep = (16 if kind == "menara" else BAND) + 2 * M
                pool = WALL_POOL[kind]
                if side == "n":
                    dx, dz = dxz(idx), -72 - deep + M
                elif side == "s":
                    dx, dz = dxz(idx), 72 - M
                elif side == "e":
                    dx, dz = 72 - M, dxz(idx)
                else:
                    dx, dz = -72 - deep + M, dxz(idx)
                daftar_bebas(d, dx, dz, pool, SIDE_PUTAR[side])
        corner = spec.get("sudut")
        if corner:
            putar = {"nw": 0, "ne": 1, "se": 2, "sw": 3}[corner]
            S = 16 + 2 * M
            dx = -72 - S + M if corner in ("nw", "sw") else 72 - M
            dz = -72 - S + M if corner in ("nw", "ne") else 72 - M
            daftar_bebas(d, dx, dz, "tembok/sudut", putar)


def build_walls():
    tembok_lurus("tembok/lurus", 0)
    tembok_lurus("tembok/lurus_jaga", 1)
    tembok_menara()
    tembok_sudut()
    gerbang_agung()
    gerbang_samping()


# ===================================================================================================== shared pieces
def o(i, j):
    """Canvas origin of lot (i, j)."""
    return i * tl.LOT, j * tl.LOT


def tugu_pendiri(k, cx, cz, y=LY):
    """Tugu Pendiri (38.B.3 pusat): the founder of the City Guard Pavilion, 12 blocks over a stepped plinth - a robed
    cultivator, a sword held point-down before him, a hair crown; stone lanterns at the plinth corners."""
    for step, r in enumerate((4, 3, 2)):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                edge = abs(x - cx) == r or abs(z - cz) == r
                k.put(x, y + 1 + step, z, "kerb_chisel" if edge and (x + z) % 2 == 0 else "kerb")
    base = y + 4
    for d, (dx, dz) in FACE.items():
        for w in (-1, 0, 1):
            px = cx + dx * 2 + (w if dz else 0)
            pz = cz + dz * 2 + (w if dx else 0)
            k.stairs(px, base, pz, "minecraft:polished_andesite_stairs", OPP[d])
    for yy in range(base, base + 4):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if yy >= base + 3 and dz != 0 and dx != 0:
                    continue
                k.put(cx + dx, yy, cz + dz, "minecraft:polished_andesite" if yy < base + 3 else "statue")
    for dx in (-1, 0, 1):
        k.put(cx + dx, base + 2, cz + 1, "minecraft:polished_andesite" if dx else "gold")
    k.put(cx, base + 1, cz + 2, "minecraft:iron_bars")
    k.put(cx, base, cz + 2, "minecraft:iron_bars")
    k.put(cx, base + 3, cz + 2, "minecraft:stone_button", {"face": "wall", "facing": "south", "powered": "false"})
    for dx in (-2, 2):
        k.put(cx + dx, base + 4, cz, "minecraft:polished_andesite_slab", {"type": "top", "waterlogged": "false"})
        k.stairs(cx + dx, base + 3, cz + 1, "minecraft:polished_andesite_stairs", "south", half="top")
    for dx in (-1, 0, 1):
        k.put(cx + dx, base + 4, cz, "minecraft:polished_andesite")
    k.put(cx, base + 5, cz, "minecraft:chiseled_stone_bricks")
    k.put(cx, base + 6, cz, "minecraft:chiseled_stone_bricks")
    k.stairs(cx, base + 6, cz + 1, "minecraft:stone_brick_stairs", "north", half="top")
    k.put(cx, base + 7, cz, "gold")
    k.slab(cx, base + 8, cz, "minecraft:stone_brick_slab")
    k.stairs(cx, base + 5, cz - 1, "minecraft:stone_brick_stairs", "south")
    for dx, dz in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        K.stone_lantern(k, cx + dx, y + 2, cz + dz)


def octagon_pool(k, cx, cz, r_out, r_in, y=LY):
    """An eight-cornered pool ring between r_in and r_out, stone rim with a slab coping, lilies, four bridges."""
    for x in range(cx - r_out - 2, cx + r_out + 3):
        for z in range(cz - r_out - 2, cz + r_out + 3):
            dx, dz = abs(x - cx), abs(z - cz)
            d = max(dx, dz, (dx + dz) * 0.72)
            if r_in < d <= r_out:
                k.put(x, y - 1, z, "minecraft:clay")
                k.put(x, y, z, "minecraft:water", {"level": "0"})
            elif r_out < d <= r_out + 1.2:
                k.put(x, y, z, "kerb")
                k.slab(x, y + 1, z, "kerb_slab")
    for (dx, dz) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        for t in range(r_in, r_out + 2):
            for w in (-1, 0, 1):
                x = cx + dx * t + (w if dz else 0)
                z = cz + dz * t + (w if dx else 0)
                k.put(x, y, z, "kerb_chisel" if w == 0 else "kerb")
                if k.get(x, y + 1, z):
                    k.clear(x, y + 1, z)
    for x in range(cx - r_out, cx + r_out + 1):
        for z in range(cz - r_out, cz + r_out + 1):
            if k.get(x, y, z) == "minecraft:water" and (x * 7 + z * 13) % 11 == 0:
                k.put(x, y + 1, z, "minecraft:lily_pad")


def avenue_lot_decor(k, x0, z0, z1, y=LY):
    """Arcades flanking the imperial avenue in a 48-wide lot whose avenue is x0+16..x0+31: red columns every 3, a tile
    roof, paper lamps, stalls and benches under it."""
    for side, sx0 in ((0, x0 + 4), (1, x0 + 35)):
        ax0, ax1 = sx0, sx0 + 8
        for z in range(z0 + 4, z1 - 3):
            for x in (ax0, ax1):
                if (z - z0) % 3 == 1:
                    for dy in range(1, 5):
                        k.put(x, y + dy, z, "red")
        K.beam_band(k, ax0, z0 + 4, ax1, z1 - 4, y + 5)
        K.gable(k, ax0, y + 6, z0 + 4, ax1, z1 - 4, along="z", overhang=1, pearl=False, role="tile_stairs",
                slab_role="tile_slab", full_role="tile")
        for z in range(z0 + 6, z1 - 5, 7):
            K.lit(k, (ax0 + ax1) // 2, y + 5, z, chain=1, role="lamp")
        face = "east" if side == 0 else "west"
        for z in range(z0 + 7, z1 - 6, 10):
            sx, sz = K.stall(k, ax0 + (1 if side else 7), z, face, y=y, w=3,
                             goods=("minecraft:flower_pot", "minecraft:decorated_pot"))
            k.titik("lapak", sx, y + 1, sz, face)
        for z in range(z0 + 12, z1 - 6, 10):
            bx = ax0 + (6 if side == 0 else 2)
            K.bench(k, bx, z, face, y=y, n=2)
            k.titik("kursi", bx, y + 1, z, face)


# ===================================================================================================== pusat
def build_pusat():
    k = kanvas("pusat", h=56)
    skip = {(1, 0, "s"), (1, 1, "n"), (1, 1, "s"), (1, 2, "n")}
    K.street_grid(k, skip=skip)
    K.imperial_avenue(k, 64, 79, 0, 143)

    # ---- (1,1) alun_agung: the Great Square 40 x 40 (canvas 52..91) with rings of paving
    ax, az = o(1, 1)
    c = 71
    for x in range(ax + 3, ax + 45):
        for z in range(az + 3, az + 45):
            d = max(abs(x - c - 0.5), abs(z - c - 0.5))
            ring = int(d) % 6
            role = "kerb_chisel" if ring == 0 and (x + z) % 2 == 0 else "paving" if ring in (1, 2, 3) else "minecraft:stone_bricks"
            k.put(x, LY, z, role)
    octagon_pool(k, c, c, 12, 8)
    tugu_pendiri(k, c, c)
    for x, z in ((ax + 5, az + 5), (ax + 42, az + 5), (ax + 5, az + 42), (ax + 42, az + 42)):
        K.huabiao(k, x, LY, z, height=8)
    for x in range(ax + 9, ax + 40, 6):
        for z in (az + 5, az + 42):
            if abs(x - c) > 9:
                K.stone_lantern(k, x, LY + 1, z)
    for z in range(az + 11, az + 38, 6):
        for x in (ax + 5, ax + 42):
            if abs(z - c) > 9:
                K.stone_lantern(k, x, LY + 1, z)
    benches = [(c - 16, c - 6, "east"), (c - 16, c + 4, "east"), (c + 16, c - 6, "west"), (c + 16, c + 4, "west"),
               (c - 6, c - 16, "south"), (c + 4, c - 16, "south"), (c - 6, c + 16, "north"), (c + 4, c + 16, "north")]
    for x, z, f in benches:
        K.bench(k, x, z, f, n=3)
        k.titik("obrol", x, LY + 1, z, f)
    K.notice_board(k, c - 2, az + 8, "south", w=5)
    k.kotak("panggung", c - 6, LY + 1, az + 10, c + 6, LY + 6, az + 12, "south", acara="festival")
    for x, z in ((c - 11, c - 11), (c + 11, c - 11), (c - 11, c + 11), (c + 11, c + 11)):
        k.titik("main", x, LY + 1, z, "south")
    for i, (x, z) in enumerate(((c, az + 3), (c, c - 14), (c - 14, c), (c + 14, c), (c, c + 14), (c, az + 44))):
        k.titik("jalan", x, LY + 1, z, "south", {"urut": i, "jalur": "alun"})
    for x in range(c - 4, c + 5, 2):
        k.titik("penonton", x, LY + 1, az + 15, "north")
    k.jangkar(c + 0.5, LY + 4, c + 0.5, "kota/situs", situs="kota/kota_besar", tier=5, protect=120)
    k.jangkar(c + 0.5, LY + 1, c - 14.5, "kota/alun", protect=128)
    k.jangkar(c + 0.5, LY + 1, c + 14.5, "kotabesar/zona", distrik="pusat", protect=100)
    k.jangkar(c - 0.5, LY + 1, az + 9.5, "kota/papan")
    k.jangkar(ax + 6.5, LY + 1, c + 0.5, "kota/penjaga", yaw=270.0)
    k.jangkar(c + 0.5, LY + 1, az + 13.5, "kotabesar/panggung", acara="festival")

    menara_genderang(k, *o(0, 0))
    menara_lonceng(k, *o(2, 0))
    for j in (0, 2):
        x0, z0 = o(1, j)
        avenue_lot_decor(k, x0, z0 + (0 if j == 0 else 3), z0 + (45 if j == 0 else 47))
    x0, z0 = o(1, 0)
    K.paifang(k, x0 + 23, z0 + 6, width=17, height=10)
    x0, z0 = o(1, 2)
    K.paifang(k, x0 + 23, z0 + 41, width=17, height=10)
    alun_penerimaan(k, *o(0, 1))
    yamen(k, *o(2, 1))
    taman_kota(k, *o(0, 2))
    kantor_pos(k, *o(2, 2))
    names = {(0, 0): "menara_genderang", (1, 0): "jalan_raya_utara", (2, 0): "menara_lonceng",
             (0, 1): "alun_penerimaan", (1, 1): "alun_agung", (2, 1): "yamen",
             (0, 2): "taman_kota", (1, 2): "jalan_raya_selatan", (2, 2): "kantor_pos"}
    simpan_kanvas(k, "pusat", names, utuh=("alun_agung", "menara_genderang", "menara_lonceng"))


def menara_genderang(k, x0, z0, y=LY):
    """Menara Genderang (38.B.3): a stone base 16 x 10 x 16 pierced by a cross-vaulted passage, two wooden storeys with
    eave rings, the great curfew drum on the upper floor (the beat is the JamMalam sound), a two-tier hip roof - about
    36 blocks to the pearl. A walled forecourt with a drum rack, banners and a guard."""
    tx, tz, s, base_h = x0 + 16, z0 + 14, 16, 10
    for yy in range(y + 1, y + base_h + 1):
        for x in range(tx, tx + s):
            for z in range(tz, tz + s):
                edge = x in (tx, tx + s - 1) or z in (tz, tz + s - 1)
                k.put(x, yy, z, "kerb_chisel" if edge and (yy - y) % 4 == 0 else "kerb")
    for x in range(tx, tx + s):
        for z in range(tz + 6, tz + 10):
            for yy in range(y + 1, y + 7):
                k.put(x, yy, z, "minecraft:air")
    for z in range(tz, tz + s):
        for x in range(tx + 6, tx + 10):
            for yy in range(y + 1, y + 7):
                k.put(x, yy, z, "minecraft:air")
    for x in range(tx + 6, tx + 10):
        for z in range(tz + 6, tz + 10):
            k.put(x, y, z, "kerb_chisel")
    for x in range(tx, tx + s):
        for z in (tz + 6, tz + 9):
            k.stairs(x, y + 6, z, "kerb_stairs", "south" if z == tz + 6 else "north", half="top")
    K.lit(k, tx + 7, y + 7, tz + 7, chain=1)
    # a shaft with a ladder in the north-west corner up to the platform
    for yy in range(y + 1, y + base_h + 1):
        k.put(tx + 1, yy, tz + 1, "minecraft:air")
        k.put(tx + 2, yy, tz + 1, "minecraft:air")
    for yy in range(y + 1, y + base_h + 1):
        k.put(tx + 1, yy, tz + 1, "minecraft:ladder", {"facing": "east", "waterlogged": "false"})
    for dy in range(1, 4):
        k.put(tx + 2, y + dy, tz, "minecraft:air")
    for x in range(tx, tx + s):
        for z in range(tz, tz + s):
            if x in (tx, tx + s - 1) or z in (tz, tz + s - 1):
                k.put(x, y + base_h + 1, z, "red_wall" if (x + z) % 2 else "kerb_wall")
    f1 = y + base_h
    K.hall(k, tx + 2, tz + 2, tx + s - 3, tz + s - 3, f1, 6, door_sides=("south", "north"), door_w=3, windows=4)
    K.brackets(k, tx + 2, tz + 2, tx + s - 3, tz + s - 3, f1 + 6)
    for x in range(tx + 1, tx + s - 1):
        for z in (tz + 1, tz + s - 2):
            k.stairs(x, f1 + 7, z, "roof", "south" if z == tz + 1 else "north")
    for z in range(tz + 2, tz + s - 2):
        for x in (tx + 1, tx + s - 2):
            k.stairs(x, f1 + 7, z, "roof", "east" if x == tx + 1 else "west")
    f2 = f1 + 7
    K.hall(k, tx + 3, tz + 3, tx + s - 4, tz + s - 4, f2, 6, door_sides=(), windows=3, plaque_top=False)
    k.put(tx + 7, f2 + 1, tz + 7, "red")
    for dx in (0, 1):
        for dz in (0, 1):
            k.put(tx + 7 + dx, f2 + 2, tz + 7 + dz, "minecraft:note_block",
                  {"instrument": "basedrum", "note": "0", "powered": "false"})
            k.put(tx + 7 + dx, f2 + 3, tz + 7 + dz, "minecraft:white_wool")
            k.put(tx + 7 + dx, f2 + 1, tz + 7 + dz, "red")
    K.drum(k, tx + 5, f2 + 1, tz + 5, "south")
    K.drum(k, tx + 10, f2 + 1, tz + 10, "north")
    for yy in range(f1 + 1, f2 + 1):
        k.put(tx + 4, yy, tz + 4, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    K.hip(k, tx + 2, f2 + 7, tz + 2, tx + s - 3, tz + s - 3, overhang=2, tiers=2, tier_gap=2, tier_band=2,
          band_role="plaster")
    K.brackets(k, tx + 3, tz + 3, tx + s - 4, tz + s - 4, f2 + 6)
    k.titik("kerja:palu", tx + 7, f2 + 1, tz + 9, "north", {"alat": "pemukul_genderang"})
    k.titik("jaga", tx + 4, f1 + 1, tz + 1, "north")
    k.jangkar(tx + 8.0, f2 + 1, tz + 8.0, "kotabesar/genderang", protect=24)
    K.yard_wall(k, x0 + 4, z0 + 4, x0 + 43, z0 + 43, h=2, gate="east", gate_w=5)
    for x in (x0 + 8, x0 + 39):
        K.banner_pole(k, x, LY + 1, z0 + 8, height=5)
        K.banner_pole(k, x, LY + 1, z0 + 39, height=5)
    K.drum(k, x0 + 10, LY + 1, z0 + 34, "east")
    K.drum(k, x0 + 10, LY + 1, z0 + 38, "east")
    K.potted_pine(k, x0 + 9, LY, z0 + 12)
    K.potted_pine(k, x0 + 38, LY, z0 + 12)
    k.titik("jaga", x0 + 42, LY + 1, z0 + 22, "east")
    k.jangkar(x0 + 41.5, LY + 1, z0 + 25.5, "kota/penjaga", yaw=90.0)


def menara_lonceng(k, x0, z0, y=LY):
    """Menara Lonceng (38.B.3): a bell pavilion over a shrine to the city god on a two-tier terrace; the morning bell
    (minecraft:bell hung from the ridge beam) rings at 1000."""
    tx, tz, s = x0 + 15, z0 + 13, 18
    f = K.podium(k, tx - 2, tz - 2, tx + s + 1, tz + s + 1, y, 1, steps=("south", "west"), step_w=5)
    f = K.podium(k, tx, tz, tx + s - 1, tz + s - 1, f, 3, steps=("south",), step_w=5)
    inner = K.hall(k, tx + 2, tz + 2, tx + s - 3, tz + s - 3, f, 7, door_sides=("south",), door_w=3, windows=4)
    ix0, iz0, ix1, iz1 = inner
    cx = (ix0 + ix1) // 2
    for x in range(cx - 2, cx + 3):
        k.put(x, f + 1, iz0, "kerb_chisel")
        k.slab(x, f + 2, iz0, "minecraft:polished_andesite_slab")
    k.put(cx, f + 2, iz0, "gold")
    k.put(cx, f + 3, iz0, "minecraft:chiseled_stone_bricks")
    K.counter(k, cx - 2, cx + 2, iz0 + 2, f, "south")
    K.candle(k, cx - 1, f + 2, iz0 + 2, 3, "red")
    K.candle(k, cx + 1, f + 2, iz0 + 2, 3, "red")
    k.put(cx, f + 2, iz0 + 2, "minecraft:flower_pot")
    for x in (cx - 1, cx + 1):
        k.put(x, f + 1, iz0 + 4, "minecraft:red_carpet")
        k.titik("dupa", x, f + 1, iz0 + 4, "north")
    K.dandan(k, (ix0, iz0 + 5, ix1, iz1), f, "kuil", lorong="z", titik_maks=1, simpan=[(ix0, iz1 - 1, ix0 + 1, iz1)])
    f2 = f + 7
    for x in range(tx + 1, tx + s - 1):
        for z in (tz + 1, tz + s - 2):
            k.stairs(x, f2 + 1, z, "roof", "south" if z == tz + 1 else "north")
    for z in range(tz + 2, tz + s - 2):
        for x in (tx + 1, tx + s - 2):
            k.stairs(x, f2 + 1, z, "roof", "east" if x == tx + 1 else "west")
    K.brackets(k, tx + 2, tz + 2, tx + s - 3, tz + s - 3, f2)
    b0, b1 = tx + 4, tx + s - 5
    c0, c1 = tz + 4, tz + s - 5
    for x in range(b0, b1 + 1):
        for z in range(c0, c1 + 1):
            k.put(x, f2, z, "wood")
            if x in (b0, b1) or z in (c0, c1):
                k.put(x, f2 + 1, z, "rail")
    for x in (b0, b1):
        for z in (c0, c1):
            for dy in range(1, 8):
                k.put(x, f2 + dy, z, "red")
    K.beam_band(k, b0, c0, b1, c1, f2 + 8)
    bx, bz = (b0 + b1) // 2, (c0 + c1) // 2
    k.fill(b0 + 1, f2 + 8, bz, b1 - 1, f2 + 8, bz, "beam", {"axis": "x"})
    k.put(bx, f2 + 7, bz, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    for yy in range(f + 1, f2 + 1):
        k.put(ix0, yy, iz1, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
    K.hip(k, b0 - 1, f2 + 9, c0 - 1, b1 + 1, c1 + 1, overhang=2, tiers=2, tier_gap=2, tier_band=1, band_role="plaster")
    k.titik("kerja:palu", bx, f2 + 1, bz + 1, "north", {"alat": "pemukul_lonceng"})
    k.jangkar(bx + 0.5, f2 + 1, bz + 0.5, "kotabesar/lonceng", protect=24)
    k.jangkar(cx + 0.5, f + 1, iz0 + 3.5, "kotabesar/layanan", layanan="kuil_dewa_kota", peran="RAHIB")
    K.garden(k, x0 + 4, z0 + 4, x0 + 12, z0 + 43, trees=("pine",), pond_too=False)
    K.incense_burner(k, tx + s // 2, LY + 1, z0 + 40, big=True)
    k.titik("dupa", tx + s // 2, LY + 1, z0 + 38, "south")
    k.titik("obrol", x0 + 40, LY + 1, z0 + 40, "west")
    k.titik("obrol", x0 + 40, LY + 1, z0 + 42, "west")


def alun_penerimaan(k, x0, z0, y=LY):
    """Alun-alun Penerimaan (38.B.3, 38.M.4.3): the recruitment square of Gate Opening Day - a raised stage with red
    columns and a tile roof facing east toward the avenue, eight sect stalls under striped tents in two rows (one per
    NPC sect of DiplomasiService), flag poles with banners, a candidates' waiting bench row."""
    sx0, sz0 = x0 + 5, z0 + 15
    f = K.podium(k, sx0, sz0, sx0 + 9, sz0 + 17, y, 2, steps=("east",), step_w=5)
    for x in (sx0 + 1, sx0 + 8):
        for z in (sz0 + 1, sz0 + 6, sz0 + 11, sz0 + 16):
            for dy in range(1, 6):
                k.put(x, f + dy, z, "red")
    K.beam_band(k, sx0 + 1, sz0 + 1, sx0 + 8, sz0 + 16, f + 6)
    K.brackets(k, sx0 + 1, sz0 + 1, sx0 + 8, sz0 + 16, f + 6)
    K.gable(k, sx0 + 1, f + 7, sz0 + 1, sx0 + 8, sz0 + 16, along="z", overhang=1, role="tile_stairs",
            slab_role="tile_slab", full_role="tile")
    for z in range(sz0 + 3, sz0 + 15):
        if (z - sz0) % 2:
            k.put(sx0 + 1, f + 2, z, "minecraft:red_wool")
    for z in range(sz0 + 2, sz0 + 16, 3):
        K.wall_banner(k, sx0 + 2, f + 4, z, "east")
    K.lit(k, sx0 + 4, f + 6, sz0 + 5, chain=1, role="lamp")
    K.lit(k, sx0 + 4, f + 6, sz0 + 12, chain=1, role="lamp")
    k.kotak("panggung", sx0 + 2, f + 1, sz0 + 2, sx0 + 7, f + 5, sz0 + 15, "east", acara="hari_gerbang")
    # eight tents: two rows of four, each 5 x 5 with a counter facing the aisle
    pts = []
    n = 0
    for row, tz in enumerate((z0 + 6, z0 + 36)):
        for col in range(4):
            tx = x0 + 18 + col * 7
            face = "south" if row == 0 else "north"
            for dx in range(0, 5):
                for dz in range(0, 5):
                    edge_post = dx in (0, 4) and dz in (0, 4)
                    if edge_post:
                        for dy in range(1, 4):
                            k.put(tx + dx, y + dy, tz + dz, "minecraft:oak_fence")
                    k.put(tx + dx, y + 4, tz + dz, "cloth" if (dx + col) % 2 == 0 else "minecraft:yellow_wool")
            for dx in range(1, 4):
                k.put(tx + dx, y + 5, tz + 2, "cloth")
            cz = tz + (4 if row == 0 else 0)
            for dx in range(1, 4):
                k.put(tx + dx, y + 1, cz, "wood_trapdoor", {"facing": face, "half": "top", "open": "false",
                                                            "powered": "false", "waterlogged": "false"})
            k.put(tx + 1, y + 2, cz, "minecraft:flower_pot")
            sz = tz + (2 if row == 0 else 2)
            k.titik("lapak", tx + 2, y + 1, sz, face, {"nomor": n})
            pts += [tx + 2 - (x0 + 24), 1, sz - (z0 + 24)]
            K.banner_pole(k, tx + 2, y + 1, tz + (0 if row == 0 else 4), height=5,
                          color=("red", "blue", "green", "yellow", "purple", "cyan", "black", "white")[n], rotation=0)
            n += 1
    for x in range(x0 + 18, x0 + 45, 3):
        K.bench(k, x, z0 + 24, "west", n=1)
        k.titik("penonton", x, y + 1, z0 + 24, "west")
    for i, (x, z) in enumerate(((x0 + 44, z0 + 24), (x0 + 30, z0 + 24), (x0 + 17, z0 + 24))):
        k.titik("jalan", x, y + 1, z, "south", {"urut": i, "jalur": "penerimaan"})
    k.jangkar(x0 + 24.5, y + 1, z0 + 24.5, "kotabesar/panggung", acara="hari_gerbang", pts=pts)
    k.jangkar(sx0 + 5.5, f + 1, sz0 + 9.5, "kotabesar/layanan", yaw=90.0, layanan="hari_gerbang", peran="PENGUJI")


def yamen(k, x0, z0, y=LY):
    """Kantor Yamen (38.B.3, 38.B.5.2): the magistrate's court facing west to the Great Square - a gate with the
    petition drum, a courtyard, the court hall (judge's dais under a 'Mirror Hung High' board, clerk desks, kneeling
    stones, a rack of staves), and behind it the jail: six cells of iron bars with straw beds, a jailer's desk."""
    # outer wall + gate on the west
    K.yard_wall(k, x0 + 3, z0 + 3, x0 + 44, z0 + 44, h=3, gate="west", gate_w=5)
    K.drum(k, x0 + 4, y + 1, z0 + 19, "west")
    K.drum(k, x0 + 4, y + 1, z0 + 29, "west")
    k.titik("jaga", x0 + 2, y + 1, z0 + 20, "west")
    k.titik("jaga", x0 + 2, y + 1, z0 + 28, "west")
    for z in (z0 + 21, z0 + 27):
        K.lion(k, x0 + 1, y, z, "west")
    K.paving(k, x0 + 4, z0 + 4, x0 + 43, z0 + 43, role="paving", alt="kerb", alt_chance=0.08)
    k.path([(x0 + 4, z0 + 24), (x0 + 15, z0 + 24)], y, width=3, role="imperial", alt="paving", alt_chance=0.1)
    # court hall 18 x 22, door west
    a = K.aula(k, x0 + 14, z0 + 12, x0 + 31, z0 + 36, h=7, podium_h=2, teras=2, roof="hip", door_sides=("west",),
               door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cz = (iz0 + iz1) // 2
    # dais and judge's table at the east end
    for z in range(cz - 3, cz + 4):
        for x in range(ix1 - 3, ix1 + 1):
            k.put(x, f + 1, z, "wood")
    for z in range(cz - 2, cz + 3):
        k.put(ix1 - 3, f + 2, z, "wood_trapdoor", {"facing": "west", "half": "top", "open": "false", "powered": "false",
                                                    "waterlogged": "false"})
    k.stairs(ix1 - 1, f + 2, cz, "wood_stairs", "east")
    k.titik("kursi", ix1 - 1, f + 2, cz, "west", {"peran": "HAKIM"})
    for z in range(cz - 3, cz + 4):
        k.put(ix1 + 1, f + 5, z, "red" if abs(z - cz) == 3 else "gold" if abs(z - cz) <= 1 else "wood")
    K.candle(k, ix1 - 3, f + 3, cz - 2, 2, "red")
    k.put(ix1 - 3, f + 3, cz + 2, "minecraft:lectern", {"facing": "west", "has_book": "true", "powered": "false"})
    # clerk desks, kneeling stones, staves
    for z in (iz0 + 2, iz1 - 2):
        K.table(k, ix1 - 6, f + 1, z, "minecraft:dark_oak_pressure_plate")
        K.chair(k, ix1 - 5, f + 1, z, "west")
        k.titik("meja_tulis", ix1 - 5, f + 1, z, "west")
    for x in (ix0 + 4, ix0 + 6):
        k.put(x, f + 1, cz, "minecraft:polished_andesite_slab", {"type": "bottom", "waterlogged": "false"})
    for z in range(iz0, iz1 + 1, 2):
        if (z - iz0) % 4 == 0:
            K.wall_banner(k, ix0 + 1, f + 4, z, "east")
    for z in range(iz0 + 1, iz1, 3):
        k.put(ix0 + 1, f + 1, z, "minecraft:dark_oak_fence")
        k.put(ix0 + 1, f + 2, z, "minecraft:dark_oak_fence")
        k.titik("jaga", ix0 + 2, f + 1, z, "east")
    # D-KS: petitioners' benches and the court's offering tables along the side walls (the kneeling way stays clear)
    K.dandan(k, (ix0 + 2, iz0, ix1 - 7, iz1), f, "aula", lorong="x", lorong_w=5, titik_maks=2, wall=False)
    # the jail behind the hall: 6 cells, 3 on each side of a corridor, iron bars, straw beds, buckets
    jx0, jz0, jx1, jz1 = x0 + 34, z0 + 8, x0 + 43, z0 + 40
    for x in range(jx0, jx1 + 1):
        for z in range(jz0, jz1 + 1):
            edge = x in (jx0, jx1) or z in (jz0, jz1)
            for dy in range(1, 6):
                k.put(x, y + dy, z, "kerb" if edge else "minecraft:air")
            k.put(x, y + 6, z, "kerb")
            k.put(x, y, z, "minecraft:polished_andesite")
    corridor_x = jx0 + 4
    cells = []
    for side in (0, 1):
        for n in range(3):
            cz0 = jz0 + 2 + n * 10
            if side == 0:
                cx0, cx1 = jx0 + 1, corridor_x - 1
                bars_x = corridor_x - 1
            else:
                cx0, cx1 = corridor_x + 1, jx1 - 1
                bars_x = corridor_x + 1
            for z in range(cz0, cz0 + 8):
                for dy in range(1, 5):
                    if z in (cz0, cz0 + 7):
                        for x in range(cx0, cx1 + 1):
                            k.put(x, y + dy, z, "kerb")
                    else:
                        k.put(bars_x, y + dy, z, "minecraft:iron_bars")
            mid = cz0 + 4
            for dy in (1, 2):
                k.put(bars_x, y + dy, mid, "minecraft:air")
            far_x = cx0 if side == 0 else cx1
            k.put(far_x, y + 1, cz0 + 2, "minecraft:hay_block", {"axis": "x"})
            k.put(far_x, y + 1, cz0 + 3, "minecraft:hay_block", {"axis": "x"})
            k.put(far_x, y + 1, cz0 + 5, "minecraft:cauldron")
            k.put(far_x, y + 3, cz0 + 4, "minecraft:iron_bars")
            inside = (far_x + (1 if side == 0 else -1), mid)
            cells += [inside[0], 1, inside[1], bars_x, 1, mid]
            k.titik("kasur", far_x, y + 1, cz0 + 2, "east" if side == 0 else "west", {"sel": len(cells) // 6})
    K.lit(k, corridor_x, y + 6, jz0 + 8, chain=1)
    K.lit(k, corridor_x, y + 6, jz0 + 24, chain=1)
    for dy in (1, 2, 3):
        k.put(corridor_x, y + dy, jz0, "minecraft:air")
        k.put(corridor_x, y + dy, jz1, "minecraft:air")
    K.table(k, corridor_x, y + 1, jz0 + 1)
    k.titik("jaga", corridor_x, y + 1, jz0 + 2, "south")
    # the cell anchor holds all six cells as relative points: (inside x, y, z, door x, y, z) per cell
    ax, az = corridor_x, jz0 + 16
    rel = []
    for i in range(0, len(cells), 3):
        rel += [cells[i] - ax, cells[i + 1], cells[i + 2] - az]
    k.jangkar(ax + 0.5, y + 1, az + 0.5, "kotabesar/sel", protect=16, pts=rel)
    k.jangkar(ix1 - 0.5, f + 2, cz + 0.5, "kotabesar/layanan", yaw=270.0, layanan="yamen", peran="HAKIM",
              tokoh="bao_qing")
    k.jangkar(x0 + 6.5, y + 1, z0 + 24.5, "kota/penjaga", yaw=90.0)
    k.jangkar(ix0 + 3.5, f + 1, cz + 0.5, "kotabesar/layanan", yaw=90.0, layanan="denda", peran="KAPTEN_PENJAGA",
              tokoh="tie_hu")
    k.titik("pintu", x0 + 3, y + 1, z0 + 24, "west")
    k.kotak("bangunan", x0 + 14, y, z0 + 12, x0 + 31, y + 16, z0 + 36, "west", nama="aula_yamen")
    k.kotak("bangunan", jx0, y, jz0, jx1, y + 6, jz1, "west", nama="penjara")


def taman_kota(k, x0, z0, y=LY):
    """Taman Kota (38.B.3): a city garden - a large pond with an island gazebo on a zigzag bridge, willows and pines,
    rock piles, flowerbeds, benches; obrol x 8, pancing x 3."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 3, z0 + 45):
            k.put(x, y, z, "ground" if k.rng.random() < 0.88 else "ground_alt", {"snowy": "false"})
    K.pond(k, x0 + 24, z0 + 25, 13, 10, y, lilies=14)
    # island + gazebo
    for x in range(x0 + 21, x0 + 28):
        for z in range(z0 + 22, z0 + 29):
            if math.hypot(x - x0 - 24, z - z0 - 25) <= 3.3:
                k.put(x, y, z, "kerb")
                k.put(x, y - 1, z, "kerb")
    for x, z in ((x0 + 22, z0 + 23), (x0 + 26, z0 + 23), (x0 + 22, z0 + 27), (x0 + 26, z0 + 27)):
        for dy in range(1, 5):
            k.put(x, y + dy, z, "red")
    K.beam_band(k, x0 + 22, z0 + 23, x0 + 26, z0 + 27, y + 5)
    K.hip(k, x0 + 22, y + 6, z0 + 23, x0 + 26, z0 + 27, overhang=1, tiers=1)
    K.low_table(k, x0 + 24, y + 1, z0 + 25)
    for (dx, dz, f) in ((0, -1, "south"), (0, 1, "north"), (-1, 0, "east"), (1, 0, "west")):
        k.titik("kursi_lantai", x0 + 24 + dx, y + 1, z0 + 25 + dz, f)
    K.lit(k, x0 + 24, y + 5, z0 + 25, chain=1, role="lamp")
    # zigzag bridge from the south bank
    bridge = [(x0 + 24, z0 + 36), (x0 + 24, z0 + 33), (x0 + 21, z0 + 33), (x0 + 21, z0 + 30), (x0 + 24, z0 + 30),
              (x0 + 24, z0 + 29)]
    for (ax, az), (bx, bz) in zip(bridge, bridge[1:]):
        K.line(k, (ax, az), (bx, bz), y + 1, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    # banks: willows, pines, rocks, flowerbeds, benches, fishing spots
    K.willow(k, x0 + 8, y + 1, z0 + 10)
    K.willow(k, x0 + 39, y + 1, z0 + 38)
    K.pine(k, x0 + 40, y + 1, z0 + 8, 10)
    K.pine(k, x0 + 7, y + 1, z0 + 40, 8)
    for (x, z) in ((x0 + 12, z0 + 6), (x0 + 34, z0 + 42), (x0 + 5, z0 + 26)):
        for dx, dz, h in ((0, 0, 3), (1, 0, 2), (0, 1, 1), (-1, 1, 2)):
            for dy in range(1, h + 1):
                k.put(x + dx, y + dy, z + dz, "minecraft:mossy_cobblestone" if dy < h else "minecraft:andesite")
    K.flowerbed(k, x0 + 30, z0 + 4, x0 + 36, z0 + 8, y)
    K.flowerbed(k, x0 + 4, z0 + 32, x0 + 9, z0 + 37, y)
    for i, (x, z, f) in enumerate(((x0 + 16, z0 + 13, "south"), (x0 + 30, z0 + 13, "south"), (x0 + 38, z0 + 22, "west"),
                                  (x0 + 10, z0 + 30, "east"), (x0 + 16, z0 + 40, "north"), (x0 + 32, z0 + 40, "north"))):
        K.bench(k, x, z, f, n=2)
        k.titik("obrol", x, y + 1, z, f)
    k.titik("obrol", x0 + 36, y + 1, z0 + 30, "west")
    k.titik("obrol", x0 + 12, y + 1, z0 + 20, "east")
    for (x, z, f) in ((x0 + 11, z0 + 25, "east"), (x0 + 37, z0 + 25, "west"), (x0 + 24, z0 + 13, "south")):
        k.titik("kerja:pancing", x, y + 1, z, f)
    for x, z in ((x0 + 5, z0 + 5), (x0 + 42, z0 + 5), (x0 + 5, z0 + 42), (x0 + 42, z0 + 42)):
        K.stone_lantern(k, x, y + 1, z)
    for i, (x, z) in enumerate(((x0 + 3, z0 + 3), (x0 + 24, z0 + 38), (x0 + 44, z0 + 24))):
        k.titik("main", x + (2 if i == 0 else 0), y + 1, z + (2 if i == 0 else 0), "south")


def kantor_pos(k, x0, z0, y=LY):
    """Kantor Pos dan Kabar (38.B.3): the post and news office - a hall with a long counter, pigeonhole shelves full of
    letters, a sorting table, a dovecote tower behind (the pigeons are a client picture), a rumour board outside."""
    a = K.aula(k, x0 + 6, z0 + 8, x0 + 33, z0 + 27, h=6, podium_h=1, teras=2, roof="gable", along="x",
               door_sides=("south",), door_w=3)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.counter(k, ix0 + 2, ix1 - 2, iz0 + 4, f, "south")
    for x in range(ix0, ix1 + 1):
        for yy in (f + 1, f + 2, f + 3):
            K.shelf(k, x, yy, iz0, "south")
    k.titik("kerja:meja_tulis", ix0 + 5, f + 1, iz0 + 3, "south")
    k.titik("kerja:rak", ix1 - 3, f + 1, iz0 + 1, "north")
    K.table(k, ix1 - 2, f + 1, iz1 - 2, "minecraft:white_carpet")
    K.table(k, ix1 - 4, f + 1, iz1 - 2, "minecraft:white_carpet")
    K.chair(k, ix1 - 3, f + 1, iz1 - 1, "north")
    K.crates(k, ix0, iz1 - 2, ix0 + 2, iz1, f + 1, h=2)
    # dovecote tower 7 x 7 x 18 behind the hall
    dx0, dz0 = x0 + 36, z0 + 6
    for yy in range(y + 1, y + 13):
        for x in range(dx0, dx0 + 7):
            for z in range(dz0, dz0 + 7):
                if x in (dx0, dx0 + 6) or z in (dz0, dz0 + 6):
                    k.put(x, yy, z, "kerb" if yy < y + 6 else "plaster")
                else:
                    k.put(x, yy, z, "minecraft:air")
    for yy in (y + 8, y + 10):
        for x in range(dx0 + 1, dx0 + 6, 2):
            k.put(x, yy, dz0, "minecraft:dark_oak_fence")
            k.put(x, yy, dz0 + 6, "minecraft:dark_oak_fence")
        for z in range(dz0 + 1, dz0 + 6, 2):
            k.put(dx0, yy, z, "minecraft:dark_oak_fence")
            k.put(dx0 + 6, yy, z, "minecraft:dark_oak_fence")
    K.beam_band(k, dx0, dz0, dx0 + 6, dz0 + 6, y + 13)
    K.hip(k, dx0, y + 14, dz0, dx0 + 6, dz0 + 6, overhang=1, tiers=1, top="pearl")
    for dy in range(1, 13):
        k.put(dx0 + 1, y + dy, dz0 + 1, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    for dy in (1, 2, 3):
        k.put(dx0 + 3, y + dy, dz0 + 6, "minecraft:air")
    for x in range(dx0 + 1, dx0 + 6):
        k.put(x, y + 9, dz0 + 3, "minecraft:hay_block", {"axis": "x"})
    k.titik("kerja:rak", dx0 + 3, y + 1, dz0 + 4, "north", {"alat": "merpati"})
    # rumour board and bench outside
    K.notice_board(k, x0 + 14, z0 + 36, "north", w=7)
    K.bench(k, x0 + 30, z0 + 38, "north", n=3)
    k.titik("obrol", x0 + 30, y + 1, z0 + 38, "north")
    k.titik("obrol", x0 + 32, y + 1, z0 + 38, "north")
    K.dandan(k, (ix0, iz0 + 6, ix1, iz1), f, "tunggu", titik_maks=2)
    k.titik("pintu", (ix0 + ix1) // 2, f + 1, iz1 + 1, "south")
    k.jangkar((ix0 + ix1) // 2 + 0.5, f + 1, iz0 + 3.5, "kotabesar/layanan", yaw=0.0, layanan="kantor_pos", peran="PELAYAN")
    k.kotak("bangunan", x0 + 6, y, z0 + 8, x0 + 33, y + 14, z0 + 27, "south", nama="kantor_pos")


# ===================================================================================================== shared buildings
# Every builder draws one building on its own scratch canvas facing south (door / gate on the south side, z = d - 1),
# floor at y = LY like a lot, and returns it; place it with K.tempel(k, b, x0, z0, putar).

def kitchen_row(b, x0, x1, wall_z, f, face):
    """A kitchen along a wall: smoker, furnace, cauldron, barrel, crafting table, a shelf of trapdoors over it, and the
    cook's work point in front."""
    items = ("minecraft:smoker", "minecraft:furnace", "minecraft:cauldron", "minecraft:barrel", "minecraft:crafting_table")
    for i, x in enumerate(range(x0, x1 + 1)):
        it = items[i % len(items)]
        if it in ("minecraft:smoker", "minecraft:furnace"):
            b.put(x, f + 1, wall_z, it, {"facing": face, "lit": "false"})
        elif it == "minecraft:barrel":
            K.barrel(b, x, f + 1, wall_z, face)
        else:
            b.put(x, f + 1, wall_z, it)
        b.put(x, f + 3, wall_z, "wood_trapdoor", {"facing": face, "half": "top", "open": "false", "powered": "false",
                                                  "waterlogged": "false"})
    dz = 1 if face == "south" else -1
    b.titik("kerja:kuali", (x0 + x1) // 2, f + 1, wall_z + dz, "north" if face == "south" else "south")


def siheyuan(seed, w=40, d=40, tree="pine", rich=False):
    """A courtyard house (siheyuan): plastered outer wall with a roofed gate, a screen wall behind the gate, the main
    hall (north) with the family altar, a guest table and two bedrooms behind partitions, east and west wings (west:
    bedroom + kitchen with dining table; east: bedroom + study), a paved courtyard with a tree, a fish jar and potted
    plants; washing lines in the rich variant."""
    b = K.sub(w, 30, d, seed)
    y = LY
    b.fill(0, y - 1, 0, w - 1, y - 1, d - 1, "found")
    K.paving(b, 0, 0, w - 1, d - 1, y, role="paving", alt="kerb", alt_chance=0.06)
    K.yard_wall(b, 0, 0, w - 1, d - 1, h=4, gate="south", gate_w=3)
    gx = w // 2
    for x in range(gx - 3, gx + 4):
        for dy in range(1, 4):
            b.put(x, y + dy, d - 5, "plaster" if dy < 3 else "red")
        b.slab(x, y + 4, d - 5, "tile_slab")
    b.put(gx, y + 2, d - 6, "minecraft:flower_pot")
    mh = K.aula(b, 5, 2, w - 6, 13, h=5, podium_h=1, teras=1, roof="gable", along="x", door_sides=("south",),
                door_w=3, tile=not rich)
    ix0, iz0, ix1, iz1 = mh["inner"]
    f = mh["f"]
    cx = (ix0 + ix1) // 2
    for z in range(iz0, iz1 + 1):
        for dy in range(1, 5):
            gap = z == iz1 - 1 and dy <= 2
            b.put(cx - 5, f + dy, z, "minecraft:air" if gap else "wood")
            b.put(cx + 5, f + dy, z, "minecraft:air" if gap else "wood")
    for x in range(cx - 2, cx + 3):
        b.put(x, f + 1, iz0, "wood_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                               "waterlogged": "false"})
    K.candle(b, cx - 1, f + 2, iz0, 2, "red")
    K.candle(b, cx + 1, f + 2, iz0, 2, "red")
    b.put(cx, f + 2, iz0, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    K.wall_banner(b, cx, f + 3, iz0, "south")
    K.table(b, cx, f + 1, iz0 + 3)
    K.chair(b, cx - 1, f + 1, iz0 + 3, "east")
    K.chair(b, cx + 1, f + 1, iz0 + 3, "west")
    b.titik("kursi", cx - 1, f + 1, iz0 + 3, "east")
    b.titik("kursi", cx + 1, f + 1, iz0 + 3, "west")
    b.titik("meja_makan", cx, f + 1, iz0 + 3, "south")
    b.titik("dupa", cx, f + 1, iz0 + 2, "north")
    for bx in (ix0 + 1, ix1 - 1):
        K.bed(b, bx, f + 1, iz0 + 1, "north")
        b.titik("kasur", bx, f + 1, iz0 + 1, "south")
        cxx = bx + (1 if bx < cx else -1)
        K.chest(b, cxx, f + 1, iz0, None, "south")
        b.titik("peti_milik", cxx, f + 1, iz0 + 1, "north")
        K.barrel(b, bx, f + 1, iz1, "north")
    K.dandan(b, (ix0, iz0 + 1, ix1, iz1), f, "rumah", titik_maks=1, wall=False)
    for side in (0, 1):
        wx0 = 2 if side == 0 else w - 12
        door_side = "east" if side == 0 else "west"
        wing = K.aula(b, wx0, 16, wx0 + 9, d - 9, h=4, podium_h=1, teras=1, roof="gable", along="z",
                      door_sides=(door_side,), door_w=1, tile=True)
        jx0, jz0, jx1, jz1 = wing["inner"]
        wf = wing["f"]
        mid = (jz0 + jz1) // 2
        gap_x = jx1 - 1 if side == 0 else jx0 + 1
        for x in range(jx0, jx1 + 1):
            for dy in range(1, 4):
                b.put(x, wf + dy, mid, "minecraft:air" if (x == gap_x and dy <= 2) else "wood")
        for i in range(2 if w >= 36 else 1):
            bx = jx0 + 1 + i * 3
            if bx <= jx1:
                K.bed(b, bx, wf + 1, jz0 + 1, "north")
                b.titik("kasur", bx, wf + 1, jz0 + 1, "south")
        K.chest(b, jx1 if side == 0 else jx0, wf + 1, jz0, None, "south")
        if side == 0:
            kitchen_row(b, jx0, jx1, jz1, wf, "north")
            K.furnish_dining(b, (jx0 + jx1) // 2, mid + 2, wf, n=3)
        else:
            K.bookshelf_wall(b, jx0, jz1, jx1, jz1, wf + 1, wf + 2, face="north")
            K.table(b, (jx0 + jx1) // 2, wf + 1, mid + 2, "minecraft:white_carpet")
            K.chair(b, (jx0 + jx1) // 2, wf + 1, mid + 3, "north")
            b.titik("meja_tulis", (jx0 + jx1) // 2, wf + 1, mid + 3, "north")
        K.dandan(b, (jx0, jz0, jx1, jz1), wf, "rumah", titik_maks=0, tepi=0)
    cz0, cz1 = 15, d - 7
    for x in range(13, w - 13):
        for z in range(cz0, cz1):
            if (x - gx) % 6 == 0 or (z - (cz0 + cz1) // 2) % 6 == 0:
                b.put(x, y, z, "kerb_chisel" if (x + z) % 2 else "paving")
    tx, tz = gx - 5, (cz0 + cz1) // 2
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            b.put(tx + dx, y, tz + dz, "ground", {"snowy": "false"})
    K.tree(b, tx, y + 1, tz, tree, 5 if tree == "cherry" else 8)
    b.put(gx + 5, y + 1, tz, "minecraft:water_cauldron", {"level": "3"})
    for x in (gx + 4, gx + 6):
        K.pot(b, x, y + 1, tz + 2)
    if rich:
        for x in range(gx - 2, gx + 3):
            b.put(x, y + 3, cz0 + 2, "minecraft:white_wool" if x % 2 else "minecraft:light_blue_wool")
        for x in (gx - 3, gx + 3):
            for dy in (1, 2, 3):
                b.put(x, y + dy, cz0 + 2, "wood_fence")
    b.titik("obrol", gx + 2, y + 1, tz - 2, "west")
    b.titik("obrol", gx - 2, y + 1, tz + 2, "east")
    b.titik("main", gx, y + 1, cz1 - 2, "north")
    b.titik("kerja:sapu", gx + 3, y + 1, cz1 - 3, "north")
    b.titik("pintu", gx, y + 1, d - 1, "south")
    b.jalur([(gx, d - 1), (gx, d - 7), (gx, 15)], jalur="rumah")
    b.kotak("bangunan", 0, y, 0, w - 1, y + 12, d - 1, "south", nama="siheyuan")
    return b


def rumah_toko(seed, w=20, d=24, goods=("minecraft:decorated_pot", "minecraft:flower_pot")):
    """A shophouse: open shop front with a counter, goods on shelves and barrels, the family upstairs (two beds, a
    dining table, a balcony with a railing over the street), a gable tile roof, a gold sign board and an awning."""
    b = K.sub(w, 26, d, seed)
    y = LY
    b.fill(0, y - 1, 0, w - 1, y - 1, d - 1, "found")
    K.paving(b, 0, 0, w - 1, d - 1, y)
    x0, z0, x1, z1 = 1, 2, w - 2, d - 4
    K.hall(b, x0, z0, x1, z1, y, 5, door_sides=(), windows=4, lamps=True, ceiling=False)
    for x in range(x0 + 1, x1):
        for dy in range(1, 4):
            b.put(x, y + dy, z1, "minecraft:air")
        b.put(x, y + 4, z1, "wood_trapdoor", {"facing": "south", "half": "top", "open": "true", "powered": "false",
                                              "waterlogged": "false"})
    K.counter(b, x0 + 2, x1 - 2, z1 - 2, y, "south")
    for x in range(x0 + 4, x1):
        if x % 3:
            K.shelf(b, x, y + 2, z0 + 1, "south")
        else:
            K.barrel(b, x, y + 1, z0 + 1, "south")
    for i, x in enumerate(range(x0 + 3, x1 - 1, 3)):
        b.put(x, y + 2, z1 - 2, goods[i % len(goods)], {"facing": "south", "cracked": "false", "waterlogged": "false"}
              if goods[i % len(goods)].endswith("decorated_pot") else None)
    K.dandan(b, (x0 + 3, z0 + 2, x1 - 1, z1 - 4), y, "toko", titik_maks=0, wall=False, tepi=0)
    b.titik("lapak", (x0 + x1) // 2, y + 1, z1 - 3, "south")
    b.titik("kerja:timbangan", (x0 + x1) // 2 + 2, y + 1, z1 - 3, "south")
    f2 = y + 5
    K.upper_floor(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f2, hole=(x0 + 1, z0 + 1, x0 + 2, z0 + 5))
    for i in range(5):
        b.stairs(x0 + 1, y + 1 + i, z0 + 5 - i, "wood_stairs", "north")
        b.stairs(x0 + 2, y + 1 + i, z0 + 5 - i, "wood_stairs", "north")
    b.walls(x0, f2 + 1, z0, x1, f2 + 4, z1, role="plaster", pillar="beam", windows=3)
    for x in range(x0 - 1, x1 + 2):
        b.slab(x, f2, z1 + 1, "wood_slab", "top")
        b.put(x, f2 + 1, z1 + 1, "rail")
    for dy in (1, 2):
        b.put((x0 + x1) // 2, f2 + dy, z1, "minecraft:air")
    for bx in (x0 + 4, x1 - 3):
        K.bed(b, bx, f2 + 1, z0 + 2, "north")
        b.titik("kasur", bx, f2 + 1, z0 + 2, "south")
    K.chest(b, x1 - 1, f2 + 1, z0 + 1, None, "south")
    b.titik("peti_milik", x1 - 1, f2 + 1, z0 + 2, "north")
    K.furnish_dining(b, (x0 + x1) // 2, z1 - 3, f2, n=3)
    K.dandan(b, (x0 + 1, z0 + 1, x1 - 1, z1 - 1), f2, "rumah", titik_maks=0, simpan=[(x0 + 1, z0 + 1, x0 + 3, z0 + 6)])
    K.lit(b, (x0 + x1) // 2, f2 + 4, (z0 + z1) // 2, chain=1)
    K.beam_band(b, x0, z0, x1, z1, f2 + 5)
    b.fill(x0 + 1, f2 + 5, z0 + 1, x1 - 1, f2 + 5, z1 - 1, "wood")
    K.brackets(b, x0, z0, x1, z1, f2 + 5)
    K.gable(b, x0, f2 + 6, z0, x1, z1, along="x", overhang=1, role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for x in range((x0 + x1) // 2 - 2, (x0 + x1) // 2 + 3):
        b.put(x, y + 5, z1 + 1, "gold" if x == (x0 + x1) // 2 else "red")
    K.awning(b, x0, x1, z1 + 2, z1 + 3, y + 4)
    for x in (x0, x1):
        for dy in range(1, 4):
            b.put(x, y + dy, z1 + 3, "wood_fence")
    b.titik("pintu", (x0 + x1) // 2, y + 1, z1 + 1, "south")
    b.kotak("bangunan", x0, y, z0, x1, f2 + 10, z1, "south", nama="rumah_toko")
    return b


def rumah_susun(seed, w=24, d=20):
    """A three-storey timber tenement: stone ground floor, plastered upper floors with outside galleries on the south
    front reached by a two-flight stair at the west end, two flats per floor (beds, chest, table), a hip tile roof."""
    b = K.sub(w, 36, d, seed)
    y = LY
    b.fill(0, y - 1, 0, w - 1, y - 1, d - 1, "found")
    K.paving(b, 0, 0, w - 1, d - 1, y)
    x0, z0, x1, z1 = 4, 1, w - 2, d - 6
    midx = (x0 + x1) // 2
    for storey in range(3):
        fy = y + storey * 5
        b.walls(x0, fy + 1, z0, x1, fy + 5, z1, role="kerb" if storey == 0 else "plaster",
                pillar="red" if storey == 0 else "beam", windows=3)
        if storey > 0:
            b.fill(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, "wood_alt")
            for x in range(2, x1 + 1):
                for z in (z1 + 1, z1 + 2):
                    b.slab(x, fy, z, "wood_slab", "top")
                b.put(x, fy + 1, z1 + 3, "rail")
        for dy in range(1, 5):
            for z in range(z0 + 1, z1):
                b.put(midx, fy + dy, z, "wood")
        for room, rx in enumerate((x0 + 1, midx + 1)):
            door_x = rx + 2
            K.door(b, door_x, fy + 1, z1, "south", "left")
            K.bed(b, rx + 1, fy + 1, z0 + 2, "north")
            b.titik("kasur", rx + 1, fy + 1, z0 + 2, "south")
            if midx - x0 >= 7:
                K.bed(b, rx + 3, fy + 1, z0 + 2, "north")
                b.titik("kasur", rx + 3, fy + 1, z0 + 2, "south")
            K.chest(b, rx, fy + 1, z0 + 1, None, "south")
            b.titik("peti_milik", rx, fy + 1, z0 + 2, "north")
            K.table(b, rx + 4, fy + 1, z1 - 2)
            K.chair(b, rx + 5, fy + 1, z1 - 2, "west")
            b.titik("meja_makan", rx + 4, fy + 1, z1 - 2, "south")
            K.dandan(b, (rx, z0 + 1, rx + (midx - x0) - 2, z1 - 1), fy, "rumah", titik_maks=0, tepi=0)
            K.lit(b, rx + 3, fy + 5, (z0 + z1) // 2, chain=1)
    for x in (x0, x1):
        for dy in range(1, 16):
            b.put(x, y + dy, z1 + 3, "red")
    # stair well at the west end: a ladder against a stone pier, stepping off east onto each gallery
    for dy in range(1, 12):
        b.put(1, y + dy, z1 + 1, "kerb")
        b.put(2, y + dy, z1 + 1, "minecraft:ladder", {"facing": "east", "waterlogged": "false"})
    top = y + 15
    K.beam_band(b, x0, z0, x1, z1, top)
    b.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, "wood")
    K.brackets(b, x0, z0, x1, z1, top)
    K.hip(b, x0, top + 1, z0, x1, z1 + 2, overhang=1, role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for x in range(x0 + 2, x1, 4):
        b.put(x, y + 9, z1 + 3, "minecraft:white_wool")
    b.titik("obrol", x0 + 3, y + 1, z1 + 4, "south")
    b.titik("main", x1 - 2, y + 1, d - 1, "north")
    b.titik("pintu", x0 + 3, y + 1, z1 + 1, "south")
    b.kotak("bangunan", x0, y, z0, x1, top + 6, z1 + 3, "south", nama="rumah_susun")
    return b


# ===================================================================================================== istana
def throne_hall(k, x0, z0, x1, z1, y=LY):
    """Aula takhta of the Istana Pengawal Kota (38.B.3): a three-step marble podium with carved stairs and a central
    dragon ramp, a veranda of red columns, the hall 32 x 20 inside (red 2 x 2 pillars, coffered ceiling of dark oak and
    spruce panels, gold-beamed), the throne on a seven-step dais (quartz stairs, gold back), screens and banners, bronze
    braziers (soul lanterns on cauldrons), a triple hip roof. Returns the interior box and floor."""
    f = K.podium(k, x0, z0, x1, z1, y, 3, steps=("south",), step_w=9, role="kerb", top="imperial")
    cx = (x0 + x1) // 2
    # the dragon ramp in the middle of the steps (no stairs: a carved slab ramp)
    for i in range(3):
        for w in (-1, 0, 1):
            k.put(cx + w, y + 3 - i, z1 + 1 + i, "minecraft:chiseled_quartz_block" if w == 0 else "imperial")
    a = K.aula(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, h=11, podium_h=0, teras=3, roof="hip", tiers=3,
               door_sides=("south",), door_w=5, tile=True, top="spire", y=f, steps=())
    ix0, iz0, ix1, iz1 = a["inner"]
    hf = a["f"]
    # 2 x 2 red pillars in two rows
    for x in range(ix0 + 4, ix1 - 3, 7):
        for z in (iz0 + 5, iz1 - 4):
            for dx in (0, 1):
                for dz in (0, 1):
                    for dy in range(1, 11):
                        k.put(x + dx, hf + dy, z + dz, "red")
                    k.put(x + dx, hf + 1, z + dz, "gold")
    # coffered ceiling
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            cell = (x - ix0) % 4 == 0 or (z - iz0) % 4 == 0
            k.put(x, hf + 11, z, "beam" if cell else ("wood" if (x // 4 + z // 4) % 2 else "light_wood"),
                  {"axis": "x"} if cell else None)
    # the dais (7 steps toward the throne along the north wall)
    for i in range(4):
        for x in range(cx - 6 + i, cx + 7 - i):
            for z in range(iz0, iz0 + 6 - i):
                k.put(x, hf + 1 + i, z, "imperial")
        for x in range(cx - 6 + i, cx + 7 - i):
            k.stairs(x, hf + 1 + i, iz0 + 6 - i, "imperial_stairs", "north")
    tf = hf + 4
    k.stairs(cx, tf + 1, iz0 + 1, "minecraft:quartz_stairs", "north")
    k.put(cx, tf + 1, iz0, "gold")
    k.put(cx, tf + 2, iz0, "gold")
    k.put(cx, tf + 3, iz0, "minecraft:chiseled_quartz_block")
    for dx in (-1, 1):
        k.stairs(cx + dx, tf + 1, iz0 + 1, "minecraft:quartz_stairs", "east" if dx < 0 else "west")
        k.put(cx + dx, tf + 2, iz0, "gold")
    # screen behind the throne: gold and red panels with banners
    for x in range(cx - 5, cx + 6):
        for dy in range(1, 8):
            if abs(x - cx) > 1:
                k.put(x, tf + dy, iz0, "red" if dy % 3 else "gold")
            elif dy > 3:
                k.put(x, tf + dy, iz0, "gold")
    for x in (cx - 4, cx + 4):
        K.wall_banner(k, x, tf + 5, iz0 + 1, "south", "yellow")
    # braziers and candles along the aisle
    for z in range(iz0 + 9, iz1 - 1, 5):
        for x in (cx - 5, cx + 5):
            k.put(x, hf + 1, z, "minecraft:cauldron")
            k.put(x, hf + 2, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    for z in range(iz0 + 7, iz1 + 1):
        for x in (cx - 1, cx, cx + 1):
            k.put(x, hf + 1, z, "minecraft:red_carpet" if x != cx else "minecraft:yellow_carpet")
    for z in range(iz0 + 8, iz1 - 1, 3):
        for x in (ix0 + 1, ix1 - 1):
            K.wall_banner(k, x, hf + 6, z, "east" if x == ix0 + 1 else "west")
    for x in range(ix0 + 2, ix1 - 1, 6):
        K.lit(k, x, hf + 11, (iz0 + iz1) // 2, chain=3, role="lamp")
    k.titik("kursi", cx, tf + 1, iz0 + 1, "south", {"peran": "PANGERAN_KOTA"})
    for dx in (-3, 3):
        k.titik("jaga", cx + dx, tf - 1, iz0 + 7, "south", {"peran": "MENTERI"})
    for x, z in ((ix0 + 2, iz0 + 9), (ix1 - 2, iz0 + 9), (ix0 + 2, iz1 - 2), (ix1 - 2, iz1 - 2)):
        k.titik("jaga", x, hf + 1, z, "east" if x < cx else "west", {"peran": "PENGAWAL_ISTANA"})
    # D-KS: ministers' benches, offering tables and vases in the side aisles behind the pillars (the dais and the
    # carpeted way stay clear)
    K.dandan(k, (ix0, iz0, ix1, iz1), hf, "aula", simpan=[(cx - 6, iz0, cx + 6, iz1), (ix0, iz0, ix1, iz0 + 7)],
             titik_maks=2, wall=False)
    k.titik("pintu", cx, hf + 1, iz1 + 1, "south")
    return a, cx, tf


def build_istana():
    k = kanvas("istana", h=80)
    skip = {(1, 0, "s"), (1, 1, "n")}
    K.street_grid(k, skip=skip)
    K.imperial_avenue(k, 64, 79, 96, 143)
    # ---- palace precinct (lots (1,0)+(1,1)): canvas x 50..93, z 2..93
    px0, pz0, px1, pz1 = 50, 2, 93, 93
    K.yard_wall(k, px0, pz0, px1, pz1, h=5, gate="south", gate_w=7)
    K.paving(k, px0 + 1, pz0 + 1, px1 - 1, pz1 - 1, LY, role="paving", alt="kerb", alt_chance=0.05)
    a, cx, tf = throne_hall(k, 55, 28, 88, 60)
    k.jangkar(cx + 0.5, tf + 1, a["inner"][1] + 2.5, "kotabesar/layanan", yaw=180.0, layanan="audiensi",
              peran="PANGERAN_KOTA", tokoh="yan_wushuang")
    # front court: imperial path, lions, huabiao, incense, flanking office wings
    k.path([(cx, 62), (cx, 92)], LY, width=7, role="imperial", alt="paving", alt_chance=0.08)
    for z in (66, 80):
        K.huabiao(k, cx - 6, LY, z, 9)
        K.huabiao(k, cx + 6, LY, z, 9)
    K.incense_burner(k, cx, LY + 1, 73, big=True)
    K.lion(k, cx - 5, LY, 90, "south")
    K.lion(k, cx + 5, LY, 90, "south")
    for side, wx0 in ((0, 52), (1, 81)):
        wing = K.aula(k, wx0, 64, wx0 + 10, 86, h=6, podium_h=1, teras=1, roof="gable", along="z",
                      door_sides=("east" if side == 0 else "west",), door_w=3, tile=True)
        jx0, jz0, jx1, jz1 = wing["inner"]
        wf = wing["f"]
        for z in range(jz0 + 1, jz1, 4):
            K.table(k, (jx0 + jx1) // 2, wf + 1, z, "minecraft:white_carpet")
            K.chair(k, (jx0 + jx1) // 2 + (1 if side == 0 else -1), wf + 1, z, "west" if side == 0 else "east")
            k.titik("meja_tulis", (jx0 + jx1) // 2 + (1 if side == 0 else -1), wf + 1, z, "west" if side == 0 else "east")
        K.bookshelf_wall(k, jx0 if side == 0 else jx1, jz0, jx0 if side == 0 else jx1, jz1, wf + 1, wf + 3,
                         face="east" if side == 0 else "west")
        if side == 0:
            k.jangkar((jx0 + jx1) / 2.0 + 0.5, wf + 1, (jz0 + jz1) / 2.0 + 0.5, "kotabesar/layanan", yaw=90.0,
                      layanan="perintah_istana", peran="MENTERI", tokoh="gu_lianshan")
        else:
            k.jangkar((jx0 + jx1) / 2.0 + 0.5, wf + 1, (jz0 + jz1) / 2.0 + 0.5, "kotabesar/layanan", yaw=270.0,
                      layanan="istana_ajudan", peran="MENTERI", tokoh="mo_ying")
    # rear residence hall and its garden (lot (1,0) north part)
    r = K.aula(k, 58, 5, 85, 22, h=6, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south",), door_w=3)
    rx0, rz0, rx1, rz1 = r["inner"]
    rf = r["f"]
    rc = (rx0 + rx1) // 2
    for x in (rx0 + 2, rx1 - 2):
        K.bed(k, x, rf + 1, rz0 + 1, "north")
        k.titik("kasur", x, rf + 1, rz0 + 1, "south")
    K.rug(k, rc - 3, rz0 + 3, rc + 3, rz1 - 1, rf + 1)
    K.low_table(k, rc, rf + 1, rz0 + 5)
    k.jangkar(rc + 0.5, rf + 1, rz0 + 8.5, "kotabesar/layanan", yaw=180.0, layanan="istana_pewaris", peran="PANGERAN_KOTA",
              tokoh="yan_ruyi")
    for d_, (dx, dz) in K.FACE.items():
        k.titik("kursi_lantai", rc + dx * 2 if dx else rc, rf + 1, rz0 + 5 + dz * 2 if dz else rz0 + 5, OPP[d_])
    K.dandan(k, (rx0 + 1, rz0, rx1, rz1), rf, "rumah", simpan=[(rc - 3, rz0, rc + 3, rz1)], titik_maks=2)
    K.bookshelf_wall(k, rx0, rz0, rx0, rz1, rf + 1, rf + 3, face="east")
    for x in range(60, 84, 4):
        K.pot(k, x, LY + 1, 25)
    k.jangkar(cx + 0.5, LY + 1, 26.5, "kotabesar/zona", distrik="istana", protect=100)
    for x in (cx - 3, cx + 3):
        k.titik("jaga", x, LY + 1, 92, "south", {"peran": "PENGAWAL_ISTANA"})
    k.jangkar(cx - 3.5, LY + 1, 91.5, "kota/penjaga", yaw=0.0)

    # ---- (0,0) taman_belakang
    taman_belakang(k, *o(0, 0))
    # ---- (2,0) balai_pertemuan
    balai_pertemuan(k, *o(2, 0))
    # ---- (0,1) kantor_arsip
    kantor_arsip(k, *o(0, 1))
    # ---- (2,1) barak_pengawal
    barak_pengawal(k, *o(2, 1))
    # ---- (1,2) gerbang_istana
    gerbang_istana(k, *o(1, 2))
    # ---- (0,2), (2,2) rumah_pejabat
    K.tempel(k, siheyuan(tl._seed("istana/rumah_pejabat_a"), 42, 42, tree="pine", rich=True), 3, 99, 1)
    K.tempel(k, siheyuan(tl._seed("istana/rumah_pejabat_b"), 42, 42, tree="cherry", rich=True), 99, 99, 3)
    names = {(0, 0): "taman_belakang", (1, 0): "istana_a", (2, 0): "balai_pertemuan", (0, 1): "kantor_arsip",
             (1, 1): "istana_b", (2, 1): "barak_pengawal", (0, 2): "rumah_pejabat_a", (1, 2): "gerbang_istana",
             (2, 2): "rumah_pejabat_b"}
    simpan_kanvas(k, "istana", names, utuh=("istana_a", "istana_b", "gerbang_istana", "balai_pertemuan"))


def taman_belakang(k, x0, z0, y=LY):
    """Taman belakang istana (38.B.3): a rock garden and lotus pond, a reading pavilion on the water, bamboo groves,
    six meditation stones."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 3, z0 + 45):
            k.put(x, y, z, "ground" if (x * 3 + z) % 9 else "minecraft:moss_block", {"snowy": "false"}
                  if (x * 3 + z) % 9 else None)
    K.pond(k, x0 + 22, z0 + 24, 12, 8, y, lilies=18)
    # reading pavilion on stilts over the pond, a bridge from the east bank
    for x in range(x0 + 18, x0 + 27):
        for z in range(z0 + 20, z0 + 29):
            k.put(x, y + 1, z, "wood")
    for x, z in ((x0 + 18, z0 + 20), (x0 + 26, z0 + 20), (x0 + 18, z0 + 28), (x0 + 26, z0 + 28)):
        k.put(x, y, z, "kerb")
        for dy in range(2, 6):
            k.put(x, y + dy, z, "red")
    for x in range(x0 + 18, x0 + 27):
        for z in (z0 + 20, z0 + 28):
            if x not in (x0 + 18, x0 + 26):
                k.put(x, y + 2, z, "rail")
    K.beam_band(k, x0 + 18, z0 + 20, x0 + 26, z0 + 28, y + 6)
    K.hip(k, x0 + 18, y + 7, z0 + 20, x0 + 26, z0 + 28, overhang=1, tiers=2, tier_gap=2, tier_band=1, band_role="red")
    K.low_table(k, x0 + 22, y + 2, z0 + 24)
    K.bookshelf_wall(k, x0 + 19, z0 + 21, x0 + 19, z0 + 22, y + 2, y + 3, face="east")
    for i in range(x0 + 27, x0 + 37):
        k.slab(i, y + 1, z0 + 24, "wood_slab", "top")
        k.put(i, y + 2, z0 + 23, "rail")
        k.put(i, y + 2, z0 + 25, "rail")
    k.titik("meja_tulis", x0 + 22, y + 2, z0 + 25, "north")
    # rock piles and bamboo
    for (rx, rz) in ((x0 + 6, z0 + 6), (x0 + 38, z0 + 8), (x0 + 8, z0 + 38), (x0 + 40, z0 + 40)):
        for dx, dz, h in ((0, 0, 4), (1, 0, 3), (0, 1, 2), (-1, 0, 2), (1, 1, 1)):
            for dy in range(1, h + 1):
                k.put(rx + dx, y + dy, rz + dz, "minecraft:tuff" if dy < h else "minecraft:mossy_cobblestone")
    for (bx, bz) in ((x0 + 5, z0 + 20), (x0 + 6, z0 + 22), (x0 + 4, z0 + 24), (x0 + 42, z0 + 30), (x0 + 41, z0 + 32)):
        K.bamboo_clump(k, bx, bz, y + 1, 8 + (bx + bz) % 4)
    K.pine(k, x0 + 36, y + 1, z0 + 6, 9)
    # six meditation stones
    for i, (mx, mz) in enumerate(((x0 + 8, z0 + 12), (x0 + 12, z0 + 40), (x0 + 30, z0 + 40), (x0 + 38, z0 + 18),
                                   (x0 + 14, z0 + 8), (x0 + 30, z0 + 8))):
        k.slab(mx, y + 1, mz, "minecraft:smooth_stone_slab")
        k.titik("meditasi", mx, y + 2, mz, "south" if i % 2 else "east")
    K.stone_lantern(k, x0 + 36, y + 1, z0 + 22)
    K.stone_lantern(k, x0 + 36, y + 1, z0 + 26)


def balai_pertemuan(k, x0, z0, y=LY):
    """Balai Pertemuan Sekte (38.B.3, 38.B.4.2): a round council table of eight seats under a hip roof; along the walls
    eight emblem stands (banners in sect colours; the emblems are client pictures), a scribe's desk."""
    a = K.aula(k, x0 + 6, z0 + 6, x0 + 41, z0 + 40, h=8, podium_h=2, teras=2, roof="hip", tiers=2,
               door_sides=("south", "west"), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    # round table radius 3: dark oak slabs on fences, jade centre
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            if math.hypot(x - cx, z - cz) <= 3.2:
                k.put(x, f + 1, z, "wood_fence")
                k.slab(x, f + 2, z, "wood_slab", "bottom")
    k.put(cx, f + 2, cz, "jade")
    pts = []
    for n in range(8):
        ang = n * math.pi / 4.0
        sx = cx + int(round(math.cos(ang) * 5))
        sz = cz + int(round(math.sin(ang) * 5))
        dx, dz = cx - sx, cz - sz
        face = "east" if abs(dx) >= abs(dz) and dx > 0 else "west" if abs(dx) >= abs(dz) else "south" if dz > 0 else "north"
        K.chair(k, sx, f + 1, sz, face)
        k.titik("kursi", sx, f + 1, sz, face, {"nomor": n})
        pts += [sx - cx, 1, sz - cz]
        # emblem stand on the wall behind the seat
        bx = cx + int(round(math.cos(ang) * 9))
        bz = cz + int(round(math.sin(ang) * 9))
        bx = max(ix0, min(ix1, bx))
        bz = max(iz0, min(iz1, bz))
        k.put(bx, f + 1, bz, "kerb_chisel")
        k.put(bx, f + 2, bz, "minecraft:%s_banner" % ("red", "blue", "green", "yellow", "purple", "cyan", "black", "white")[n],
              {"rotation": str((n * 2 + 12) % 16)})
    K.lit(k, cx, f + 8, cz, chain=2, role="lamp")
    K.table(k, ix1 - 1, f + 1, iz1 - 1, "minecraft:white_carpet")
    k.titik("meja_tulis", ix1 - 1, f + 1, iz1 - 2, "south")
    k.jangkar(cx + 0.5, f + 1, cz + 0.5, "kotabesar/panggung", acara="pertemuan_sekte", pts=pts)
    k.jangkar(ix1 - 0.5, f + 1, iz1 - 2.5, "kotabesar/layanan", yaw=0.0, layanan="pertemuan_sekte", peran="MENTERI")
    # D-KS: delegates' tea corners, folding screens and vases outside the ring of emblem stands; inner columns
    K.dandan(k, (ix0, iz0, ix1, iz1), f, "teh", simpan=[(cx - 10, cz - 10, cx + 10, cz + 10)], pilar=True, titik_maks=4)
    k.kotak("bangunan", x0 + 6, y, z0 + 6, x0 + 41, f + 18, z0 + 40, "south", nama="balai_pertemuan")


def kantor_arsip(k, x0, z0, y=LY):
    """Kantor Catatan Sipil (38.B.3): the records office - a long hall of shelves and archive barrels, six clerk desks,
    a counter for Nama Baik records and stall registrations (38EK), a strong room."""
    a = K.aula(k, x0 + 4, z0 + 8, x0 + 43, z0 + 34, h=6, podium_h=1, teras=2, roof="gable", along="x",
               door_sides=("east",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.counter_z(k, ix1 - 3, iz0 + 3, iz1 - 3, f, "east")
    for x in range(ix0, ix1 - 5, 3):
        for z in (iz0, iz1):
            for dy in (1, 2, 3):
                K.barrel(k, x, f + dy, z, "south" if z == iz0 else "north")
            K.bookshelf_wall(k, x + 1, z, x + 2, z, f + 1, f + 3, face="south" if z == iz0 else "north")
    for i, z in enumerate(range(iz0 + 3, iz1 - 2, 3)):
        for x in (ix0 + 4, ix0 + 10):
            K.table(k, x, f + 1, z, "minecraft:white_carpet")
            K.chair(k, x - 1, f + 1, z, "east")
            k.titik("meja_tulis", x - 1, f + 1, z, "east")
    # strong room (iron door, iron blocks, chest) in the west end
    for z in range(iz0, iz1 + 1):
        for dy in range(1, 5):
            k.put(ix0 + 2, f + dy, z, "minecraft:iron_block" if dy < 4 else "kerb")
    k.put(ix0 + 2, f + 1, (iz0 + iz1) // 2, "minecraft:iron_door", {"facing": "east", "half": "lower", "hinge": "left",
                                                                   "open": "false", "powered": "false"})
    k.put(ix0 + 2, f + 2, (iz0 + iz1) // 2, "minecraft:iron_door", {"facing": "east", "half": "upper", "hinge": "left",
                                                                   "open": "false", "powered": "false"})
    K.chest(k, ix0, f + 1, iz0 + 1, None, "east")
    K.chest(k, ix0, f + 1, iz1 - 1, None, "east")
    # D-KS: free-standing archive cases between the clerks' desks and the counter
    K.dandan(k, (ix0 + 3, iz0 + 1, ix1 - 5, iz1 - 1), f, "arsip", wall=False, titik_maks=2)
    k.jangkar(ix1 - 1.5, f + 1, (iz0 + iz1) / 2.0 + 0.5, "kotabesar/layanan", yaw=270.0, layanan="arsip", peran="PUSTAKAWAN")
    K.garden(k, x0 + 4, z0 + 37, x0 + 43, z0 + 44, trees=("pine", "pine"), pond_too=False)
    k.kotak("bangunan", x0 + 4, y, z0 + 8, x0 + 43, f + 14, z0 + 34, "east", nama="kantor_arsip")


def barak_pengawal(k, x0, z0, y=LY):
    """Barak Pengawal (38.B.3): a barracks of 16 beds in two rows, weapon racks (fences with banners), a mess table,
    and a spear training yard with straw dummies (hay, fence, target) - latihan x 8."""
    a = K.aula(k, x0 + 4, z0 + 4, x0 + 26, z0 + 43, h=6, podium_h=1, teras=1, roof="gable", along="z",
               door_sides=("east",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    n = 0
    for z in range(iz0 + 1, iz1 - 1, 4):
        for x, face in ((ix0, "west"), (ix1, "east")):
            if n >= 16:
                break
            K.bed(k, x + (1 if face == "west" else -1), f + 1, z, face)
            k.titik("kasur", x + (1 if face == "west" else -1), f + 1, z, OPP[face])
            K.chest(k, x, f + 1, z + 1, None, "east" if face == "west" else "west")
            n += 1
    for z in range(iz0 + 2, iz1 - 1, 8):
        K.table(k, (ix0 + ix1) // 2, f + 1, z)
        K.table(k, (ix0 + ix1) // 2, f + 1, z + 1)
        K.chair(k, (ix0 + ix1) // 2 - 1, f + 1, z, "east")
        K.chair(k, (ix0 + ix1) // 2 + 1, f + 1, z + 1, "west")
    for z in range(iz0, iz1 + 1, 3):
        k.put((ix0 + ix1) // 2, f + 1, iz1, "wood_fence")
    # training yard
    for x in range(x0 + 29, x0 + 45):
        for z in range(z0 + 4, z0 + 44):
            k.put(x, y, z, "minecraft:coarse_dirt" if (x + z) % 5 else "minecraft:gravel")
    for i in range(8):
        dx, dz = x0 + 31 + (i % 2) * 8, z0 + 8 + (i // 2) * 9
        k.put(dx, y + 1, dz, "minecraft:oak_fence")
        k.put(dx, y + 2, dz, "minecraft:hay_block", {"axis": "y"})
        k.put(dx, y + 3, dz, "minecraft:target", {"power": "0"})
        k.put(dx - 1, y + 2, dz, "minecraft:oak_fence")
        k.put(dx + 1, y + 2, dz, "minecraft:oak_fence")
        k.titik("latihan", dx, y + 1, dz + 2, "north")
    for z in range(z0 + 6, z0 + 42, 6):
        K.banner_pole(k, x0 + 44, y + 1, z, height=4, color="red", rotation=4)
    for z in range(z0 + 10, z0 + 40, 10):
        for dy in (1, 2, 3):
            k.put(x0 + 28, y + dy, z, "wood_fence")
        k.put(x0 + 28, y + 4, z, "minecraft:iron_bars")
    k.jangkar(x0 + 36.5, y + 1, z0 + 42.5, "kota/penjaga", yaw=0.0)
    K.dandan(k, (ix0 + 2, iz0, ix1 - 2, iz1), f, "barak", wall=False, titik_maks=2)
    k.kotak("bangunan", x0 + 4, y, z0 + 4, x0 + 26, f + 12, z0 + 43, "east", nama="barak")


def gerbang_istana(k, x0, z0, y=LY):
    """Gerbang Istana (38.B.3): the imperial avenue (canvas x 64..79) passes the palace's outer gate - a gatehouse
    spanning the avenue with five bays and a small curfew gate leaf (kotabesar/gerbang, nama=istana), guard rooms on
    both sides, a paifang at the south end, lions, huabiao and gardens beside the avenue."""
    gz = z0 + 10
    # gatehouse across the avenue: x 60..83
    hx0, hx1 = x0 + 12, x0 + 35
    f = K.podium(k, hx0, gz - 3, hx1, gz + 3, y, 1, steps=())
    for x in range(hx0, hx1 + 1):
        for z in range(gz - 3, gz + 4):
            if x in (hx0, hx1) or z in (gz - 3, gz + 3):
                if (x - hx0) % 4 == 0 or x in (hx0, hx1):
                    for dy in range(1, 9):
                        k.put(x, f + dy, z, "red")
    for x in range(hx0, hx1 + 1):
        for z in range(gz - 3, gz + 4):
            if abs(x - (x0 + 23.5)) < 3.5:
                k.put(x, f, z, "imperial")
            elif x in (hx0 + 1, hx0 + 2, hx0 + 3, hx1 - 1, hx1 - 2, hx1 - 3) or abs(x - (x0 + 23.5)) >= 8:
                for dy in range(1, 8):
                    if z in (gz - 3, gz + 3) and (x - hx0) % 4:
                        k.put(x, f + dy, z, "plaster" if dy > 1 else "kerb")
    K.beam_band(k, hx0, gz - 3, hx1, gz + 3, f + 9)
    k.fill(hx0 + 1, f + 9, gz - 2, hx1 - 1, f + 9, gz + 2, "wood")
    K.brackets(k, hx0, gz - 3, hx1, gz + 3, f + 9)
    K.hip(k, hx0, f + 10, gz - 3, hx1, gz + 3, overhang=2, tiers=2, tier_gap=2, tier_band=2,
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for x in range(x0 + 20, x0 + 28):
        k.put(x, f + 8, gz + 3, "gold" if x0 + 22 <= x <= x0 + 25 else "red")
    k.jangkar(x0 + 24.0, f + 1, gz + 0.0, "kotabesar/gerbang", yaw=0.0, lebar=7, tinggi=8, nama="istana")
    for x in (hx0 + 2, hx1 - 2):
        k.titik("jaga", x, f + 1, gz + 4, "south", {"peran": "PENGAWAL_ISTANA"})
    K.lion(k, x0 + 18, y, gz + 6, "south")
    K.lion(k, x0 + 29, y, gz + 6, "south")
    for z in (z0 + 22, z0 + 32):
        K.huabiao(k, x0 + 13, y, z, 8)
        K.huabiao(k, x0 + 34, y, z, 8)
    K.paifang(k, x0 + 23, z0 + 41, width=17, height=10)
    for gxx in (x0 + 3, x0 + 37):
        K.garden(k, gxx, z0 + 16, gxx + 8, z0 + 44, trees=("pine",), pond_too=False)
    k.jangkar(x0 + 16.5, y + 1, gz + 6.5, "kota/penjaga", yaw=0.0)


# ===================================================================================================== cendekia
def build_cendekia():
    k = kanvas("cendekia", h=72)
    skip = {(0, 0, "e"), (1, 0, "w"), (0, 1, "e"), (1, 1, "w")}
    K.street_grid(k, skip=skip)
    perpustakaan(k)
    menara_pengamat(k, *o(2, 0))
    kuil_langit(k)
    sekolah(k, *o(2, 1))
    K.tempel(k, siheyuan(tl._seed("cendekia/rumah_a"), 42, 42, tree="pine"), 3, 99, 0)
    taman_tinta(k, *o(1, 2))
    K.tempel(k, siheyuan(tl._seed("cendekia/rumah_b"), 42, 42, tree="cherry"), 99, 99, 0)
    names = {(0, 0): "perpustakaan_a", (1, 0): "perpustakaan_b", (2, 0): "menara_pengamat", (0, 1): "kuil_langit_a",
             (1, 1): "kuil_langit_b", (2, 1): "sekolah", (0, 2): "rumah_cendekia_a", (1, 2): "taman_tinta",
             (2, 2): "rumah_cendekia_b"}
    simpan_kanvas(k, "cendekia", names, utuh=("menara_pengamat", "kuil_langit_a", "kuil_langit_b"))


def perpustakaan(k, y=LY):
    """Perpustakaan Agung (38.B.3): the Grand Library across lots (0,0)+(1,0) - a three-storey central stack hall
    (bookshelf and chiseled-bookshelf walls, galleries on every floor, reading desks x 12), the Lecture Hall in the west
    wing (20 benches facing the lecturer's platform), the Map Office in the east wing (cartography tables, map cases)."""
    # central stack hall x 32..63
    a = K.aula(k, 31, 5, 64, 42, h=7, podium_h=2, teras=2, roof="hip", tiers=3, door_sides=("south",), door_w=3,
               tile=True, top="spire")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.bookshelf_wall(k, ix0, iz0, ix1, iz0, f + 1, f + 6, face="south")
    K.bookshelf_wall(k, ix0, iz0 + 1, ix0, iz1 - 2, f + 1, f + 6, face="east")
    K.bookshelf_wall(k, ix1, iz0 + 1, ix1, iz1 - 2, f + 1, f + 6, face="west")
    # free-standing stacks in rows
    for x in range(ix0 + 4, ix1 - 3, 5):
        K.bookshelf_wall(k, x, iz0 + 4, x, iz0 + 12, f + 1, f + 4, face="east", chiseled_every=4)
    # galleries at f+4 around the walls with railings and a stair
    for x in range(ix0 + 1, ix1):
        for z in range(iz0 + 1, iz1 - 1):
            edge = x in (ix0 + 1, ix0 + 2, ix1 - 1, ix1 - 2) or z in (iz0 + 1, iz0 + 2)
            if edge:
                k.slab(x, f + 4, z, "wood_slab", "top")
            elif x in (ix0 + 3, ix1 - 3) or z == iz0 + 3:
                if z < iz1 - 3:
                    k.put(x, f + 5, z, "rail")
    for i in range(4):
        k.stairs(ix1 - 2, f + 1 + i, iz1 - 3 - i, "wood_stairs", "north")
        k.stairs(ix1 - 1, f + 1 + i, iz1 - 3 - i, "wood_stairs", "north")
    # reading desks x 12
    n = 0
    for z in range(iz0 + 15, iz1 - 2, 4):
        for x in range(ix0 + 4, ix1 - 3, 6):
            if n >= 12:
                break
            K.table(k, x, f + 1, z, "minecraft:white_carpet")
            K.table(k, x + 1, f + 1, z, "minecraft:white_carpet")
            if n % 3 == 0:
                k.put(x, f + 2, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
            K.chair(k, x, f + 1, z + 1, "north")
            k.titik("meja_tulis", x, f + 1, z + 1, "north")
            n += 1
    k.put((ix0 + ix1) // 2, f + 1, iz1 - 1, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    k.titik("kerja:rak", ix0 + 2, f + 1, iz0 + 2, "north")
    k.titik("kerja:rak", ix1 - 2, f + 1, iz0 + 2, "north")
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz1 - 2.5, "kotabesar/layanan", yaw=0.0, layanan="perpustakaan",
              peran="PUSTAKAWAN", tokoh="tuan_wen")
    # lecture hall, west wing x 4..28
    b = K.aula(k, 4, 9, 28, 40, h=6, podium_h=1, teras=1, roof="gable", along="z", door_sides=("east",), door_w=3,
               tile=True)
    bx0, bz0, bx1, bz1 = b["inner"]
    bf = b["f"]
    for x in range(bx0 + 1, bx1):
        for z in range(bz0, bz0 + 4):
            k.put(x, bf + 1, z, "wood")
    k.put((bx0 + bx1) // 2, bf + 2, bz0 + 2, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    k.titik("kursi", (bx0 + bx1) // 2, bf + 2, bz0 + 1, "south", {"peran": "PENCERAMAH_FANA"})
    seats = 0
    for z in range(bz0 + 7, bz1, 2):
        for x in range(bx0 + 1, bx1, 2):
            if seats >= 20:
                break
            K.chair(k, x, bf + 1, z, "north")
            k.titik("kursi", x, bf + 1, z, "north")
            seats += 1
    K.dandan(k, (bx0, bz0 + 4, bx1, bz1), bf, "kantor", titik_maks=2)
    K.wall_banner(k, (bx0 + bx1) // 2 - 3, bf + 4, bz0, "south")
    K.wall_banner(k, (bx0 + bx1) // 2 + 3, bf + 4, bz0, "south")
    k.jangkar((bx0 + bx1) / 2.0 + 0.5, bf + 2, bz0 + 2.5, "kotabesar/layanan", yaw=180.0, layanan="kuliah",
              peran="PENCERAMAH_FANA")
    # map office, east wing x 67..91
    c = K.aula(k, 67, 9, 91, 40, h=6, podium_h=1, teras=1, roof="gable", along="z", door_sides=("west",), door_w=3,
               tile=True)
    cx0, cz0, cx1, cz1 = c["inner"]
    cf = c["f"]
    for z in range(cz0 + 2, cz1 - 1, 4):
        k.put((cx0 + cx1) // 2, cf + 1, z, "minecraft:cartography_table")
        k.put((cx0 + cx1) // 2 + 1, cf + 1, z, "minecraft:cartography_table")
        k.titik("meja_tulis", (cx0 + cx1) // 2 - 1, cf + 1, z, "east")
    for z in range(cz0, cz1 + 1):
        for dy in (1, 2):
            K.shelf(k, cx1, cf + dy, z, "west")
    K.dandan(k, (cx0, cz0, cx1, cz1), cf, "kantor", titik_maks=3)
    k.jangkar(cx0 + 2.5, cf + 1, (cz0 + cz1) / 2.0 + 0.5, "kotabesar/layanan", yaw=90.0, layanan="kantor_peta",
              peran="PUSTAKAWAN")
    k.jangkar(47.5, LY + 1, 45.5, "kotabesar/zona", distrik="cendekia", protect=100)
    k.kotak("bangunan", 31, y, 5, 64, f + 28, 42, "south", nama="perpustakaan")
    for x in range(30, 66, 6):
        K.pot(k, x, y + 1, 44)


def menara_pengamat(k, x0, z0, y=LY):
    """Menara Pengamat Langit (38.B.3, 38.B.4.4): a seven-storey pagoda 16 x 16 in a garden lot, open viewing deck on
    top (meditasi x 4), an armillary sphere of chains and gold on the deck (the turning rings are a 38MD client model)."""
    K.garden(k, x0 + 4, z0 + 4, x0 + 43, z0 + 43, trees=("pine", "pine", "pine"), pond_too=False)
    top = K.pagoda(k, x0 + 16, z0 + 16, 16, 7, sh=5, door="south", titik_top="meditasi", top="spire",
                   isi=("kantor", "arsip", "teh", "arsip", "kuil", "teh", "kantor"))
    # deck centre: an armillary stand
    k.jangkar(x0 + 24.0, top - 5.0, z0 + 24.0, "kotabesar/pengamat", protect=24)
    k.jangkar(x0 + 24.5, y + 4, z0 + 34.5, "kotabesar/layanan", yaw=0.0, layanan="menara_pengamat", peran="PENCERAMAH_FANA")
    for x, z in ((x0 + 10, z0 + 30), (x0 + 38, z0 + 30)):
        K.bench(k, x, z, "north", n=2)
        k.titik("obrol", x, y + 1, z, "north")


def kuil_langit(k, y=LY):
    """Kuil Langit (38.B.3, 38.B.4.4) across lots (0,1)+(1,1): a walled stone court with the great incense burner (a
    polished andesite tungku under a soul lantern; the smoke is a client particle), the Hall of the Three Pure Ones
    (three robed statues on an altar, candles, kneeling mats), a small bell tower, side cloisters with meditation
    spots; the court is a Spirit Site x1.2 (anchor kotabesar/kuil)."""
    x0, z0, x1, z1 = 4, 52, 91, 91
    K.yard_wall(k, x0, z0, x1, z1, h=4, gate="south", gate_w=5)
    K.paving(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y, role="minecraft:smooth_stone", alt="paving", alt_chance=0.2)
    cx = (x0 + x1) // 2
    # main hall
    a = K.aula(k, cx - 16, z0 + 3, cx + 16, z0 + 20, h=8, podium_h=2, teras=2, roof="hip", tiers=2,
               door_sides=("south",), door_w=5, tile=False, top="spire")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for i, sx in enumerate((cx - 5, cx, cx + 5)):
        for x in range(sx - 1, sx + 2):
            k.put(x, f + 1, iz0, "kerb_chisel")
            k.put(x, f + 2, iz0, "kerb")
        k.put(sx, f + 3, iz0, "minecraft:polished_andesite")
        k.put(sx, f + 4, iz0, "minecraft:polished_andesite")
        k.put(sx, f + 5, iz0, "minecraft:chiseled_stone_bricks")
        k.stairs(sx - 1, f + 3, iz0, "minecraft:polished_andesite_stairs", "east")
        k.stairs(sx + 1, f + 3, iz0, "minecraft:polished_andesite_stairs", "west")
        k.put(sx, f + 6, iz0, "gold")
        K.candle(k, sx - 1, f + 1, iz0 + 2, 3, "white")
        K.candle(k, sx + 1, f + 1, iz0 + 2, 3, "white")
        k.put(sx, f + 1, iz0 + 4, "minecraft:yellow_carpet")
        k.titik("dupa", sx, f + 1, iz0 + 4, "north")
    for x in range(ix0 + 1, ix1, 4):
        K.wall_banner(k, x, f + 6, iz1, "north", "yellow")
    # great incense burner in the court
    bx, bz = cx, z0 + 28
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            k.put(bx + dx, y + 1, bz + dz, "minecraft:polished_andesite" if abs(dx) < 2 and abs(dz) < 2 else "kerb")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            k.put(bx + dx, y + 2, bz + dz, "minecraft:polished_andesite" if (dx or dz) else "minecraft:cauldron")
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        k.put(bx + dx, y + 3, bz + dz, "kerb_wall")
        k.put(bx + dx, y + 4, bz + dz, "kerb_wall")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            k.slab(bx + dx, y + 5, bz + dz, "minecraft:polished_andesite_slab")
    k.put(bx, y + 6, bz, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    for d_, (dx, dz) in K.FACE.items():
        k.titik("dupa", bx + dx * 3, y + 1, bz + dz * 3, OPP[d_])
    k.jangkar(bx + 0.5, y + 1, bz + 3.5, "kotabesar/kuil", protect=24)
    k.jangkar(cx + 0.5, f + 1, iz0 + 6.5, "kotabesar/layanan", yaw=180.0, layanan="kuil", peran="RAHIB",
              tokoh="jing_xin")
    # bell tower (west) and drum stand (east)
    tx = x0 + 6
    for xx in (tx, tx + 4):
        for zz in (z0 + 26, z0 + 30):
            for dy in range(1, 9):
                k.put(xx, y + dy, zz, "red")
    for xx in range(tx, tx + 5):
        for zz in range(z0 + 26, z0 + 31):
            k.put(xx, y + 9, zz, "wood")
    k.put(tx + 2, y + 8, z0 + 28, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    K.hip(k, tx, y + 10, z0 + 26, tx + 4, z0 + 30, overhang=1)
    K.drum(k, x1 - 8, y + 1, z0 + 28, "west")
    # cloisters with meditation spots along the south of the court
    for x in range(x0 + 4, x1 - 3):
        if abs(x - cx) < 6:
            continue
        for z in (z1 - 6, z1 - 2):
            if (x - x0) % 3 == 1:
                for dy in range(1, 4):
                    k.put(x, y + dy, z, "red")
        k.slab(x, y + 4, z1 - 4, "tile_slab")
        k.slab(x, y + 4, z1 - 5, "tile_slab")
        k.slab(x, y + 4, z1 - 3, "tile_slab")
    for x in range(x0 + 6, x1 - 4, 8):
        if abs(x - cx) >= 6:
            k.put(x, y + 1, z1 - 4, "minecraft:white_carpet")
            k.titik("meditasi", x, y + 1, z1 - 4, "north")
    for z in (z0 + 24, z0 + 33):
        K.stone_lantern(k, cx - 6, y + 1, z)
        K.stone_lantern(k, cx + 6, y + 1, z)
    K.pine(k, x0 + 18, y + 1, z0 + 33, 9)
    K.pine(k, x1 - 18, y + 1, z0 + 33, 9)
    K.dandan(k, (ix0, iz0, ix1, iz1), f, "kuil", lorong="z", lorong_w=5, simpan=[(ix0, iz0, ix1, iz0 + 5)], titik_maks=3)
    k.kotak("bangunan", cx - 16, y, z0 + 3, cx + 16, f + 20, z0 + 20, "south", nama="kuil_langit")


def sekolah(k, x0, z0, y=LY):
    """Sekolah (38.B.3): the children's school - a hall with 12 small desks facing the teacher, a slate (black wool board),
    shelves; a playground with a swing frame, a sandbox and a kite post; main x 6."""
    a = K.aula(k, x0 + 5, z0 + 5, x0 + 32, z0 + 24, h=5, podium_h=1, teras=1, roof="gable", along="x",
               door_sides=("south",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for x in range(ix0 + 4, ix1 - 3):
        k.put(x, f + 2, iz0, "minecraft:black_wool")
        k.put(x, f + 3, iz0, "minecraft:black_wool")
    K.table(k, (ix0 + ix1) // 2, f + 1, iz0 + 2, "minecraft:white_carpet")
    k.titik("kursi", (ix0 + ix1) // 2, f + 1, iz0 + 1, "south", {"peran": "PENCERAMAH_FANA"})
    n = 0
    for z in range(iz0 + 4, iz1 - 1, 2):
        for x in range(ix0 + 2, ix1 - 1, 3):
            if n >= 12:
                break
            k.put(x, f + 1, z, "wood_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                                 "waterlogged": "false"})
            k.titik("kursi_lantai", x, f + 1, z + 1, "north")
            n += 1
    for x in range(ix0, ix1 + 1, 2):
        if abs(x - (ix0 + ix1) // 2) > 1:
            K.shelf(k, x, f + 2, iz1, "north")
    # playground
    for x in range(x0 + 4, x0 + 44):
        for z in range(z0 + 28, z0 + 44):
            k.put(x, y, z, "minecraft:coarse_dirt" if (x + z) % 6 else "minecraft:dirt_path")
    for x in range(x0 + 8, x0 + 13):
        for z in range(z0 + 32, z0 + 37):
            k.put(x, y, z, "minecraft:sand")
    for z in (z0 + 31, z0 + 37):
        for dy in range(1, 5):
            k.put(x0 + 20, y + dy, z, "log", {"axis": "y"})
    for z in range(z0 + 31, z0 + 38):
        k.put(x0 + 20, y + 5, z, "log", {"axis": "z"})
    for z in (z0 + 33, z0 + 35):
        for dy in (3, 4):
            k.put(x0 + 20, y + dy, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        k.slab(x0 + 20, y + 2, z, "wood_slab")
    for dy in range(1, 7):
        k.put(x0 + 36, y + dy, z0 + 34, "wood_fence")
    k.put(x0 + 36, y + 7, z0 + 34, "minecraft:red_banner", {"rotation": "4"})
    for i, (mx, mz) in enumerate(((x0 + 10, z0 + 34), (x0 + 18, z0 + 34), (x0 + 26, z0 + 40), (x0 + 30, z0 + 32),
                                   (x0 + 38, z0 + 38), (x0 + 15, z0 + 41))):
        k.titik("main", mx, y + 1, mz, "south")
    K.dandan(k, (ix0, iz0 + 1, ix1, iz1), f, "kantor", titik_maks=2)
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz0 + 3.5, "kotabesar/layanan", yaw=180.0, layanan="sekolah", peran="PENCERAMAH_FANA")


def taman_tinta(k, x0, z0, y=LY):
    """Taman Tinta (38.B.3): the ink garden of the scholars - a long pond with a moon bridge, scholar's rocks of tuff and
    calcite, plum (cherry) trees, a small pavilion with a painting table, stone stelae with carved poems."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 3, z0 + 45):
            k.put(x, y, z, "ground", {"snowy": "false"})
    K.pond(k, x0 + 24, z0 + 24, 16, 5, y, lilies=10)
    # moon bridge: an arch of stairs and slabs over the pond
    for i, dy in enumerate((1, 2, 3, 3, 3, 2, 1)):
        z = z0 + 18 + i * 2
        for zz in (z, z + 1):
            for x in (x0 + 23, x0 + 24, x0 + 25):
                k.slab(x, y + dy, zz, "minecraft:smooth_quartz_slab", "bottom" if dy < 3 else "top")
            k.put(x0 + 22, y + dy + 1, zz, "minecraft:diorite_wall")
            k.put(x0 + 26, y + dy + 1, zz, "minecraft:diorite_wall")
    for (rx, rz) in ((x0 + 8, z0 + 10), (x0 + 38, z0 + 12), (x0 + 12, z0 + 36), (x0 + 36, z0 + 38)):
        for dy in range(1, 6):
            off = (dy % 3) - 1
            k.put(rx + off, y + dy, rz, "minecraft:tuff" if dy % 2 else "minecraft:calcite")
        k.put(rx + 1, y + 1, rz, "minecraft:tuff")
    for (tx, tz) in ((x0 + 5, z0 + 5), (x0 + 42, z0 + 6), (x0 + 6, z0 + 42), (x0 + 40, z0 + 42)):
        K.cherry(k, tx, y + 1, tz, 5, 3)
    # pavilion with painting table
    for x, z in ((x0 + 32, z0 + 4), (x0 + 38, z0 + 4), (x0 + 32, z0 + 9), (x0 + 38, z0 + 9)):
        for dy in range(1, 5):
            k.put(x, y + dy, z, "red")
    K.beam_band(k, x0 + 32, z0 + 4, x0 + 38, z0 + 9, y + 5)
    K.hip(k, x0 + 32, y + 6, z0 + 4, x0 + 38, z0 + 9, overhang=1)
    K.table(k, x0 + 35, y + 1, z0 + 6, "minecraft:white_carpet")
    k.titik("meja_tulis", x0 + 35, y + 1, z0 + 7, "north")
    for (sx, sz) in ((x0 + 10, z0 + 24), (x0 + 38, z0 + 24)):
        k.put(sx, y + 1, sz, "kerb")
        k.put(sx, y + 2, sz, "minecraft:chiseled_stone_bricks")
        k.put(sx, y + 3, sz, "minecraft:chiseled_stone_bricks")
        k.slab(sx, y + 4, sz, "kerb_slab")
    for i, (bx, bz, fc) in enumerate(((x0 + 16, z0 + 14, "south"), (x0 + 30, z0 + 34, "north"))):
        K.bench(k, bx, bz, fc, n=2)
        k.titik("obrol", bx, y + 1, bz, fc)
    k.titik("meditasi", x0 + 8, y + 1, z0 + 30, "east")


# ===================================================================================================== awan
def build_awan():
    k = kanvas("awan", h=84)
    skip = {(1, 0, "e"), (2, 0, "w")}
    K.street_grid(k, skip=skip)
    kantor_pelabuhan(k, *o(0, 0))
    dermaga_langit(k)
    stasiun_teleport(k, *o(1, 1))
    gudang(k, *o(0, 1), seed=1)
    gudang(k, *o(2, 1), seed=2)
    penginapan_awak(k, *o(0, 2))
    bengkel_kapal(k, *o(1, 2))
    K.tempel(k, rumah_susun(tl._seed("awan/rumah"), 26, 22), 99, 110, 0)
    names = {(0, 0): "kantor_pelabuhan", (1, 0): "dermaga_a", (2, 0): "dermaga_b", (0, 1): "gudang_a",
             (1, 1): "stasiun_teleport", (2, 1): "gudang_b", (0, 2): "penginapan_awak", (1, 2): "bengkel_kapal",
             (2, 2): "rumah_awan"}
    simpan_kanvas(k, "awan", names, utuh=("dermaga_a", "dermaga_b", "stasiun_teleport"))


def kantor_pelabuhan(k, x0, z0, y=LY):
    """Kantor Pelabuhan Langit (38.B.3): the ticket office of the Sky Dock - a counter with three windows, a waiting room
    of ten chairs, a schedule board (client billboard), luggage racks, a cloud-ship model on a stand."""
    a = K.aula(k, x0 + 5, z0 + 8, x0 + 40, z0 + 36, h=6, podium_h=1, teras=2, roof="hip", door_sides=("south", "east"),
               door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.counter(k, ix0 + 2, ix1 - 2, iz0 + 4, f, "south")
    for x in range(ix0 + 4, ix1 - 3, 6):
        for dy in (1, 2, 3):
            k.put(x, f + dy, iz0 + 4, "red")
    for x in range(ix0 + 1, ix1):
        K.shelf(k, x, f + 2, iz0, "south")
    seats = 0
    for z in range(iz0 + 8, iz1 - 1, 3):
        for x in range(ix0 + 3, ix1 - 2, 3):
            if seats >= 10:
                break
            K.chair(k, x, f + 1, z, "north")
            k.titik("kursi", x, f + 1, z, "north")
            seats += 1
    K.notice_board(k, ix1 - 6, iz1 - 1, "north", w=5)
    # ship model: a hull of stripped spruce on a stand
    mx, mz = ix0 + 2, iz1 - 3
    k.put(mx, f + 1, mz, "kerb_chisel")
    k.put(mx, f + 2, mz, "minecraft:stripped_spruce_log", {"axis": "z"})
    k.put(mx, f + 2, mz - 1, "minecraft:spruce_stairs", {"facing": "south", "half": "bottom", "shape": "straight",
                                                        "waterlogged": "false"})
    k.put(mx, f + 3, mz, "minecraft:white_wool")
    for i, xx in enumerate(range(ix0 + 4, ix1 - 3, 6)):
        k.titik("kerja:meja_tulis", xx + 2, f + 1, iz0 + 3, "south")
    # D-KS: luggage stacks, waiting benches and tea tables for passengers between the chairs
    K.dandan(k, (ix0, iz0 + 6, ix1, iz1), f, "tunggu", titik_maks=2)
    K.k_peti(K.Ruang(k, (ix1 - 3, iz0 + 6, ix1, iz0 + 8), f), ix1 - 2, iz0 + 6, w=2, d=2)
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz0 + 2.5, "kotabesar/layanan", yaw=0.0, layanan="dermaga", peran="NAKHODA",
              tokoh="yun_fei")
    k.kotak("bangunan", x0 + 5, y, z0 + 8, x0 + 40, f + 14, z0 + 36, "south", nama="kantor_pelabuhan")


def dermaga_langit(k, y=LY):
    """Dermaga Langit (38.B.3): two dock towers 12 x 44 x 12 (stone to 12, timber above, a lift shaft of ladders and
    landings) joined by a wooden bridge at +32, four mooring arms at +32 (anchors kotabesar/dermaga), lanterns on poles,
    a cargo warehouse between the towers' feet with cranes (fence booms)."""
    deck = y + 32
    towers = ((56, 16), (124, 16))
    for tx, tz in towers:
        for yy in range(y + 1, deck + 1):
            for x in range(tx, tx + 12):
                for z in range(tz, tz + 12):
                    edge = x in (tx, tx + 11) or z in (tz, tz + 11)
                    if edge:
                        if yy <= y + 12:
                            k.put(x, yy, z, "kerb_chisel" if (yy - y) % 6 == 0 else "kerb")
                        elif (x - tx) % 11 == 0 and (z - tz) % 11 == 0:
                            k.put(x, yy, z, "log", {"axis": "y"})
                        elif (yy - y) % 8 == 0:
                            k.put(x, yy, z, "beam", {"axis": "x" if z in (tz, tz + 11) else "z"})
                        else:
                            k.put(x, yy, z, "plaster" if (yy - y) % 8 not in (3, 4) or (x + z) % 3 else "window",
                                  {"facing": "north", "half": "bottom", "open": "true", "powered": "false",
                                   "waterlogged": "false"} if (yy - y) % 8 in (3, 4) and (x + z) % 3 == 0 else None)
                    else:
                        k.put(x, yy, z, "minecraft:air" if (yy - y) % 8 else "wood")
            if (yy - y) % 8 == 0 and yy > y:
                for x in range(tx + 1, tx + 3):
                    for z in range(tz + 1, tz + 3):
                        k.put(x, yy, z, "minecraft:air")
        for yy in range(y + 1, deck + 1):
            k.put(tx + 1, yy, tz + 1, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
        for dy in range(1, 4):
            for w in (5, 6):
                k.put(tx + w, y + dy, tz + 11, "minecraft:air")
        # D-KS: every tower storey is a room: cargo at the foot, crew bunks, the signal office, the lookout's lounge
        for lvl, gaya in ((y, "gudang"), (y + 8, "asrama"), (y + 16, "kantor"), (y + 24, "teh")):
            K.dandan(k, (tx + 1, tz + 1, tx + 10, tz + 10), lvl, gaya, titik_maks=1,
                     simpan=[(tx + 1, tz + 1, tx + 3, tz + 3)], buka_jarak=1 if lvl == y else -1)
        # deck on the tower top
        for x in range(tx - 1, tx + 13):
            for z in range(tz - 1, tz + 13):
                k.put(x, deck, z, "wood")
                if x in (tx - 1, tx + 12) or z in (tz - 1, tz + 12):
                    k.put(x, deck + 1, z, "rail")
        for x in (tx, tx + 11):
            for z in (tz, tz + 11):
                for dy in range(1, 6):
                    k.put(x, deck + dy, z, "red")
        K.beam_band(k, tx, tz, tx + 11, tz + 11, deck + 6)
        K.hip(k, tx, deck + 7, tz, tx + 11, tz + 11, overhang=1, tiers=2, tier_gap=2, tier_band=1,
              role="tile_stairs", slab_role="tile_slab", full_role="tile")
        # two mooring arms: north and south, 10 long, with a bollard and a lantern pole at the end
        for side in (-1, 1):
            zz0 = tz - 1 if side < 0 else tz + 12
            for i in range(10):
                z = zz0 + side * i
                for x in range(tx + 4, tx + 8):
                    k.put(x, deck, z, "wood")
                k.put(tx + 3, deck + 1, z, "rail")
                k.put(tx + 8, deck + 1, z, "rail")
                k.stairs(tx + 4, deck - 1, z, "wood_stairs", "east", half="top")
                k.stairs(tx + 7, deck - 1, z, "wood_stairs", "west", half="top")
            end = zz0 + side * 9
            K.post(k, tx + 4, deck + 1, end, height=3)
            K.post(k, tx + 7, deck + 1, end, height=3)
            k.put(tx + 5, deck + 1, end, "kerb_wall")
            k.titik("tambatan", tx + 6, deck + 1, end - side, "north" if side < 0 else "south")
            kelas = "besar" if (tx < 90) == (side < 0) else "kecil"
            k.jangkar(tx + 6.0, deck + 1, end - side * 2 + 0.5, "kotabesar/dermaga", kelas=kelas,
                      arah="utara" if side < 0 else "selatan")
        k.titik("jaga", tx + 6, deck + 1, tz + 6, "south")
    # bridge between the towers at the deck height
    (ax, az), (bx, bz) = towers
    for x in range(ax + 12, bx):
        for z in (az + 5, az + 6):
            k.put(x, deck, z, "wood")
        k.put(x, deck + 1, az + 4, "rail")
        k.put(x, deck + 1, az + 7, "rail")
        if (x - ax) % 8 == 0:
            K.post(k, x, deck + 1, az + 4, height=2)
        if (x - ax) % 16 == 0:
            for dy in range(1, deck - y):
                k.put(x, y + dy, az + 5, "log", {"axis": "y"})
                k.put(x, y + dy, az + 6, "log", {"axis": "y"})
    # cargo warehouse under the bridge
    w = K.aula(k, 74, 26, 117, 42, h=7, podium_h=1, teras=0, roof="gable", along="x", door_sides=("south", "north"),
               door_w=5, tile=True)
    jx0, jz0, jx1, jz1 = w["inner"]
    wf = w["f"]
    for x in range(jx0 + 1, jx1, 5):
        K.crates(k, x, jz0 + 1, x + 2, jz0 + 3, wf + 1, h=3)
        K.crates(k, x, jz1 - 3, x + 2, jz1 - 1, wf + 1, h=2)
    for x in range(jx0 + 2, jx1 - 1, 10):
        k.titik("kerja:rak", x, wf + 1, (jz0 + jz1) // 2, "north")
    # cranes at the tower feet: a log mast with a fence boom and a chain
    for cx_ in (70, 120):
        for dy in range(1, 14):
            k.put(cx_, y + dy, 30, "log", {"axis": "y"})
        for i in range(1, 7):
            k.put(cx_ + (i if cx_ < 90 else -i), y + 13, 30, "wood_fence")
        for dy in range(9, 13):
            k.put(cx_ + (6 if cx_ < 90 else -6), y + dy, 30, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        K.barrel(k, cx_ + (6 if cx_ < 90 else -6), y + 8, 30)
    k.jangkar(96.5, LY + 1, 22.5, "kotabesar/zona", distrik="awan", protect=100)
    K.dandan(k, (jx0, jz0, jx1, jz1), wf, "gudang", lorong="z", lorong_w=5, titik_maks=1)
    k.kotak("bangunan", 74, y, 26, 117, y + 18, 42, "south", nama="gudang_dermaga")


def stasiun_teleport(k, x0, z0, y=LY):
    """Stasiun Formasi Teleportasi (38.B.3, 38.B.4.3): a 13 x 13 carved stone disc (chiseled stone bricks with a
    polished blackstone trigram pattern) raised two steps, four crystal pillars (amethyst clusters on calcite), a ring
    of red columns carrying a half roof, the array keeper's desk."""
    cx, cz = x0 + 24, z0 + 22
    for x in range(cx - 9, cx + 10):
        for z in range(cz - 9, cz + 10):
            if math.hypot(x - cx, z - cz) <= 9.4:
                k.put(x, y + 1, z, "kerb")
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x - cx, z - cz)
            if d > 6.4:
                continue
            ring = int(d)
            ang = math.atan2(z - cz, x - cx)
            tri = int(((ang + math.pi) / (2 * math.pi)) * 8) % 8
            line_ = ring in (2, 4, 6) and ((tri + ring) % 2 == 0)
            k.put(x, y + 2, z, "minecraft:polished_blackstone" if line_ or ring == 0 else "minecraft:chiseled_stone_bricks")
    k.put(cx, y + 2, cz, "jade")
    for dx, dz in ((-6, -6), (6, -6), (-6, 6), (6, 6)):
        for dy in range(2, 7):
            k.put(cx + dx, y + dy, cz + dz, "minecraft:calcite")
        k.put(cx + dx, y + 7, cz + dz, "minecraft:amethyst_block")
        k.put(cx + dx, y + 8, cz + dz, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    # cloister ring of columns with a half roof
    for x in range(cx - 13, cx + 14):
        for z in range(cz - 13, cz + 14):
            d = math.hypot(x - cx, z - cz)
            if 12.5 <= d <= 13.5 and (x + z) % 3 == 0 and z <= cz + 8:
                for dy in range(1, 6):
                    k.put(x, y + dy, z, "red")
            if 11.5 <= d <= 15.0 and z <= cz + 9:
                k.slab(x, y + 6, z, "tile_slab")
    K.counter(k, cx - 3, cx + 3, cz + 14, y, "north")
    k.titik("kerja:meja_tulis", cx, y + 1, cz + 15, "north")
    k.titik("tiba", cx + 2, y + 3, cz + 2, "south")
    k.jangkar(cx + 0.5, y + 3, cz + 0.5, "kotabesar/cakram", protect=24)
    k.jangkar(cx + 0.5, y + 1, cz + 15.5, "kotabesar/layanan", yaw=180.0, layanan="teleport", peran="PENJAGA_FORMASI")
    for x in (x0 + 6, x0 + 41):
        K.stone_lantern(k, x, y + 1, z0 + 41)


def gudang(k, x0, z0, y=LY, seed=1):
    """A warehouse of the Sky Dock: a long hall of crates and barrels on racks, a loading yard with a hand cart."""
    a = K.aula(k, x0 + 4, z0 + 6, x0 + 43, z0 + 30, h=7, podium_h=1, teras=0, roof="gable", along="x",
               door_sides=("south",), door_w=5, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for x in range(ix0 + 1, ix1, 4):
        K.crates(k, x, iz0 + 1, x + 2, iz0 + 6, f + 1, h=3)
    for x in range(ix0 + 1, ix1 - 1, 6):
        for dy in (1, 2, 3):
            K.shelf(k, x, f + dy, iz1 - 1, "north")
    k.titik("kerja:rak", (ix0 + ix1) // 2, f + 1, iz0 + 8, "north")
    for x in range(x0 + 6, x0 + 42):
        for z in range(z0 + 33, z0 + 44):
            k.put(x, y, z, "minecraft:packed_mud" if (x + z) % 4 else "paving")
    K.crates(k, x0 + 8, z0 + 36, x0 + 11, z0 + 38, y + 1, h=2)
    k.put(x0 + 30, y + 1, z0 + 38, "minecraft:spruce_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                                "powered": "false", "waterlogged": "false"})
    k.titik("kerja:rak", x0 + 20, y + 1, z0 + 37, "north", {"alat": "angkut"})
    K.dandan(k, (ix0, iz0, ix1, iz1), f, "gudang", lorong="z", lorong_w=5, titik_maks=1)
    k.kotak("bangunan", x0 + 4, y, z0 + 6, x0 + 43, f + 14, z0 + 30, "south", nama="gudang")


def penginapan_awak(k, x0, z0, y=LY):
    """Penginapan Awak (38.B.3): the crew inn - a two-storey hall with a common room (long tables, a counter), a dormitory
    of ten bunks upstairs (beds and trapdoor lockers)."""
    a = K.aula(k, x0 + 5, z0 + 6, x0 + 42, z0 + 30, h=5, podium_h=1, teras=1, roof="gable", along="x",
               door_sides=("south",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for x in range(ix0 + 2, ix1 - 6, 6):
        for z in (iz0 + 4, iz0 + 9):
            for dx in range(3):
                K.table(k, x + dx, f + 1, z)
            for dx in range(3):
                K.chair(k, x + dx, f + 1, z - 1, "south")
                K.chair(k, x + dx, f + 1, z + 1, "north")
            k.titik("meja_makan", x + 1, f + 1, z + 1, "north")
    K.counter_z(k, ix1 - 3, iz0 + 1, iz1 - 3, f, "west")
    kitchen_row(k, ix1 - 2, ix1, iz0, f, "south")
    f2 = f + 5
    K.upper_floor(k, ix0, iz0, ix1, iz1, f2, hole=(ix0, iz1 - 4, ix0 + 1, iz1))
    for i in range(5):
        k.stairs(ix0, f + 1 + i, iz1 - i, "wood_stairs", "north")
        k.stairs(ix0 + 1, f + 1 + i, iz1 - i, "wood_stairs", "north")
    k.walls(ix0 - 1, f2 + 1, iz0 - 1, ix1 + 1, f2 + 4, iz1 + 1, role="plaster", pillar="beam", windows=3, interior_air=True)
    for i, x in enumerate(range(ix0 + 3, ix1 - 1, 3)):
        if i >= 10:
            break
        z = iz0 + 1 if i % 2 == 0 else iz1 - 1
        K.bed(k, x, f2 + 1, z, "north" if i % 2 == 0 else "south")
        k.titik("kasur", x, f2 + 1, z, "south" if i % 2 == 0 else "north")
    K.dandan(k, (ix0, iz0, ix1 - 4, iz1), f, "makan", titik_maks=2)
    K.dandan(k, (ix0 + 2, iz0, ix1, iz1), f2, "asrama", titik_maks=2, simpan=[(ix0, iz1 - 5, ix0 + 3, iz1)])
    K.beam_band(k, ix0 - 1, iz0 - 1, ix1 + 1, iz1 + 1, f2 + 5)
    k.fill(ix0, f2 + 5, iz0, ix1, f2 + 5, iz1, "wood")
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz1 - 2.5, "kotabesar/layanan", yaw=0.0, layanan="penginapan",
              peran="PEMILIK_PENGINAPAN")
    k.kotak("bangunan", x0 + 5, y, z0 + 6, x0 + 42, f2 + 12, z0 + 30, "south", nama="penginapan_awak")


def bengkel_kapal(k, x0, z0, y=LY):
    """Bengkel Kapal (38.B.3): the shipwright's yard - a cloud ship's hull on the slipway (keel of stripped logs, fence
    ribs, half-laid planking), scaffolding, a timber store, anvils and a saw bench."""
    kz0, kz1 = z0 + 8, z0 + 38
    kx = x0 + 24
    for z in range(kz0, kz1 + 1):
        k.put(kx, y + 1, z, "minecraft:stripped_spruce_log", {"axis": "z"})
        k.put(kx - 3, y + 1, z, "kerb")
        k.put(kx + 3, y + 1, z, "kerb")
        if (z - kz0) % 3 == 0:
            span = int(5 * math.sin(math.pi * (z - kz0) / (kz1 - kz0))) + 1
            for dx in range(-span, span + 1):
                yy = y + 2 + abs(dx) // 2
                k.put(kx + dx, yy, z, "minecraft:spruce_fence")
                if abs(dx) == span:
                    for dy in range(1, 4):
                        k.put(kx + dx, yy + dy, z, "minecraft:spruce_fence")
        if z < kz0 + 12:
            for dx in range(-3, 4):
                k.put(kx + dx, y + 2 + abs(dx) // 2, z, "minecraft:spruce_planks")
    for z in range(kz0, kz1 + 1, 6):
        for x in (kx - 7, kx + 7):
            for dy in range(1, 9):
                k.put(x, y + dy, z, "minecraft:scaffolding", {"bottom": "false", "distance": "0", "waterlogged": "false"})
    for x in range(x0 + 4, x0 + 12):
        for z in range(z0 + 30, z0 + 36):
            for dy in (1, 2):
                k.put(x, y + dy, z, "minecraft:stripped_spruce_log", {"axis": "x"})
    for x, z in ((x0 + 38, z0 + 10), (x0 + 38, z0 + 14)):
        k.put(x, y + 1, z, "minecraft:anvil", {"facing": "north"})
    k.put(x0 + 38, y + 1, z0 + 20, "minecraft:stonecutter", {"facing": "north"})
    k.put(x0 + 40, y + 1, z0 + 20, "minecraft:crafting_table")
    k.titik("kerja:palu", x0 + 37, y + 1, z0 + 12, "east")
    k.titik("kerja:gergaji", x0 + 38, y + 1, z0 + 21, "north")
    k.titik("kerja:palu", kx - 6, y + 1, z0 + 20, "east")
    k.titik("kerja:pahat", kx + 6, y + 1, z0 + 26, "west")
    k.jangkar(x0 + 40.5, y + 1, z0 + 30.5, "kotabesar/tukang", yaw=0.0)


# ===================================================================================================== gilda
def armor_stand(k, x, y, z, yaw=0.0, kit="besi"):
    """A display armour stand (38.B.3 toko senjata): invulnerable, every slot locked, so the display cannot be looted."""
    sets = {"besi": ("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"),
            "rantai": ("chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"),
            "kulit": ("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
            "emas": ("golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots")}
    h, c, l, f = sets[kit]
    k.entity(x, y, z, {"id": "minecraft:armor_stand", "Invulnerable": True, "DisabledSlots": 4144959, "ShowArms": True,
                       "PersistenceRequired": True,
                       "Rotation": [structgen.nbt.Float(yaw), structgen.nbt.Float(0.0)],
                       "equipment": {"head": {"id": "minecraft:" + h, "count": 1},
                                     "chest": {"id": "minecraft:" + c, "count": 1},
                                     "legs": {"id": "minecraft:" + l, "count": 1},
                                     "feet": {"id": "minecraft:" + f, "count": 1}}})


def weapon_rack(k, x, z, y, facing, n=5):
    """A weapon rack along a wall: dark oak frame, iron bars as blades, fences as hafts, a banner over it (the real
    weapons on display are 38MD client models on the kotabesar/rak_senjata anchor)."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(n):
        px, pz = x + rx * i, z + rz * i
        k.put(px, y + 1, pz, "wood")
        k.put(px, y + 2, pz, "minecraft:iron_bars" if i % 2 == 0 else "wood_fence")
        k.put(px, y + 3, pz, "minecraft:iron_bars" if i % 2 == 0 else "wood_fence")
        k.slab(px, y + 4, pz, "wood_slab")


def forge_bay(k, x0, z0, y, face="south"):
    """One forge bay 9 x 7: a brick hearth with a blast furnace and a chimney, an anvil, a smithing table, a grindstone,
    a quench cauldron, tool shelves; kerja:palu and kerja:tungku points."""
    for dx in range(3):
        for dz in range(2):
            k.put(x0 + dx, y + 1, z0 + dz, "minecraft:bricks")
    k.put(x0 + 1, y + 2, z0, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    for dy in range(2, 16):
        for dx in range(3):
            if dy > 2 or dx != 1:
                k.put(x0 + dx, y + dy, z0, "minecraft:bricks")
    k.put(x0 + 4, y + 1, z0 + 3, "minecraft:anvil", {"facing": "east"})
    k.put(x0 + 7, y + 1, z0 + 1, "minecraft:smithing_table")
    k.put(x0 + 8, y + 1, z0 + 1, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    k.put(x0 + 6, y + 1, z0 + 5, "minecraft:water_cauldron", {"level": "3"})
    for dx in range(4, 9):
        K.shelf(k, x0 + dx, y + 3, z0, "south")
    k.titik("kerja:palu", x0 + 4, y + 1, z0 + 4, "north")
    k.titik("kerja:tungku", x0 + 1, y + 1, z0 + 2, "north")


def build_gilda():
    k = kanvas("gilda", h=64)
    skip = {(0, 0, "e"), (1, 0, "w"), (2, 0, "s"), (2, 1, "n"), (0, 1, "e"), (1, 1, "w")}
    K.street_grid(k, skip=skip)
    gilda_alkemis(k)
    gilda_tempa(k)
    gilda_kultivator(k)
    balai_tabib(k, *o(0, 2))
    K.tempel(k, rumah_susun(tl._seed("gilda/asrama"), 28, 22), 58, 110, 0)
    rumah_makan(k, *o(2, 2), name="bakmi")
    names = {(0, 0): "gilda_alkemis_a", (1, 0): "gilda_alkemis_b", (2, 0): "gilda_tempa_a", (0, 1): "gilda_kultivator_a",
             (1, 1): "gilda_kultivator_b", (2, 1): "gilda_tempa_b", (0, 2): "balai_tabib", (1, 2): "asrama_gilda",
             (2, 2): "rumah_makan_bakmi"}
    simpan_kanvas(k, "gilda", names)


def gilda_alkemis(k, y=LY):
    """Gilda Alkemis (38.B.3, 38.B.6.2) across lots (0,0)+(1,0): the examination hall with the round Pill Gathering stage
    (20 across) ringed by eight cauldron stations over lit campfires (the alchemy template list allows them), a three-step
    tribune around it, the herb store (composters, barrels, potted herbs, drying racks) in the west wing, the exam rooms
    in the east wing, the guild master's desk."""
    a = K.aula(k, 18, 4, 77, 43, h=9, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south",), door_w=5,
               tile=True, top="spire")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    pts = []
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            d = math.hypot(x - cx, z - cz)
            if d <= 10.2:
                k.put(x, f + 1, z, "imperial" if d > 3 else "jade")
            elif d <= 11.5:
                k.put(x, f + 1, z, "kerb")
            elif d <= 12.8:
                k.stairs(x, f + 1, z, "wood_stairs", "north" if z > cz and abs(z - cz) >= abs(x - cx) else
                         "south" if z < cz and abs(z - cz) >= abs(x - cx) else "west" if x > cx else "east")
            elif d <= 14.0:
                k.put(x, f + 1, z, "wood")
                k.stairs(x, f + 2, z, "wood_stairs", "north" if z > cz and abs(z - cz) >= abs(x - cx) else
                         "south" if z < cz and abs(z - cz) >= abs(x - cx) else "west" if x > cx else "east")
    for n in range(8):
        ang = n * math.pi / 4.0 + math.pi / 8.0
        sx = cx + int(round(math.cos(ang) * 7))
        sz = cz + int(round(math.sin(ang) * 7))
        k.put(sx, f + 1, sz, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false",
                                                   "waterlogged": "false"})
        k.put(sx, f + 2, sz, "minecraft:cauldron")
        tx, tz = cx + int(round(math.cos(ang) * 5)), cz + int(round(math.sin(ang) * 5))
        k.titik("kerja:kuali", tx, f + 2, tz, "north", {"stasiun": n})
        pts += [tx - cx, 2, tz - cz]
    for n in range(0, 24):
        ang = n * math.pi / 12.0
        sx = cx + int(round(math.cos(ang) * 13))
        sz = cz + int(round(math.sin(ang) * 13))
        if abs(sz - (iz1)) > 1:
            k.titik("penonton", sx, f + 3, sz, "north")
    for x in range(ix0 + 2, ix1 - 1, 8):
        K.lit(k, x, f + 9, cz, chain=3, role="lamp")
    # D-KS: the hall's side bays: herb display plinths, judges' tea tables, screens (the ring and tribune stay clear)
    K.dandan(k, (ix0, iz0, ix1, iz1), f, "teh", simpan=[(cx - 15, cz - 15, cx + 15, cz + 15)], pilar=True, titik_maks=2)
    k.kotak("panggung", cx - 10, f + 2, cz - 10, cx + 10, f + 6, cz + 10, "south", acara="pil")
    k.jangkar(cx + 0.5, f + 2, cz + 0.5, "kotabesar/panggung", acara="pil", pts=pts)
    k.jangkar(cx + 0.5, f + 2, iz1 - 1.5, "kotabesar/layanan", yaw=0.0, layanan="gilda_alkemis", peran="ALKEMIS")
    k.jangkar(cx + 3.5, f + 2, iz1 - 1.5, "kotabesar/layanan", yaw=0.0, layanan="ketua_alkemis", peran="KETUA_GILDA",
              tokoh="dan_chen")
    # herb store (west wing)
    b = K.aula(k, 3, 8, 16, 40, h=6, podium_h=1, teras=1, roof="gable", along="z", door_sides=("east",), door_w=1, tile=True)
    bx0, bz0, bx1, bz1 = b["inner"]
    bf = b["f"]
    for z in range(bz0, bz1 + 1, 2):
        k.put(bx0, bf + 1, z, "minecraft:composter", {"level": "7"} if z % 4 == 0 else {"level": "3"})
        K.barrel(k, bx1, bf + 1, z, "west")
        k.put(bx1, bf + 2, z, "minecraft:potted_azalea_bush" if z % 4 else "minecraft:potted_fern")
    for z in range(bz0 + 2, bz1 - 1, 5):
        for x in range(bx0 + 2, bx1 - 1):
            k.put(x, bf + 4, z, "wood_fence")
            k.put(x, bf + 3, z, "minecraft:dried_kelp_block")
    K.dandan(k, (bx0 + 1, bz0, bx1 - 1, bz1), bf, "obat", wall=False, titik_maks=1)
    k.titik("kerja:lumpang", (bx0 + bx1) // 2, bf + 1, bz0 + 3, "west")
    k.titik("kerja:rak", (bx0 + bx1) // 2, bf + 1, bz1 - 3, "east")
    # exam rooms (east wing): four booths with brewing stands
    c = K.aula(k, 79, 8, 92, 40, h=6, podium_h=1, teras=1, roof="gable", along="z", door_sides=("west",), door_w=1,
               tile=True)
    cx0, cz0, cx1, cz1 = c["inner"]
    cf = c["f"]
    for i, z in enumerate(range(cz0 + 1, cz1 - 1, 6)):
        for dz in range(5):
            if dz in (0, 4):
                k.put(cx0 + 4, cf + 1, z + dz, "wood")
        k.put(cx1, cf + 1, z + 2, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false",
                                                              "has_bottle_2": "false"})
        k.put(cx1 - 1, cf + 1, z + 2, "minecraft:cauldron")
        k.titik("kerja:kuali", cx1 - 2, cf + 1, z + 2, "east", {"ujian": i})
    K.dandan(k, (cx0, cz0 + 2, cx0 + 3, cz1), cf, "kantor", wall=False, titik_maks=1)
    k.jangkar((cx0 + cx1) / 2.0 + 0.5, cf + 1, cz0 + 1.5, "kotabesar/layanan", yaw=0.0, layanan="ujian_alkemis", peran="PENGUJI")
    k.jangkar(47.5, LY + 1, 46.5, "kotabesar/zona", distrik="gilda", protect=100)
    k.kotak("bangunan", 18, y, 4, 77, f + 26, 43, "south", nama="gilda_alkemis")


def gilda_tempa(k, y=LY):
    """Gilda Pandai Pusaka (38.B.3) across lots (2,0)+(2,1): four forge bays under a long smithy roof, the display hall
    with weapon racks (anchors kotabesar/rak_senjata) and armour stands, the Altar Jiwa Pusaka (a soul-lit stone altar
    where natal treasures are bound), the master smith's counter."""
    x0, x1 = 100, 139
    # smithy hall (north lot)
    a = K.aula(k, x0, 4, x1, 40, h=8, podium_h=1, teras=1, roof="gable", along="z", door_sides=("west", "south"),
               door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for i, (bx, bz) in enumerate(((ix0 + 1, iz0 + 1), (ix1 - 9, iz0 + 1), (ix0 + 1, iz0 + 17), (ix1 - 9, iz0 + 17))):
        forge_bay(k, bx, bz, f)
    K.counter_z(k, (ix0 + ix1) // 2, iz0 + 10, iz0 + 14, f, "west")
    # D-KS: ore and charcoal stock, timber for hafts, finished blanks on racks between the forge bays
    K.dandan(k, (ix0, iz0, ix1, iz1), f, "gudang", lorong="z", titik_maks=1,
             simpan=[((ix0 + ix1) // 2 - 2, iz0 + 8, (ix0 + ix1) // 2 + 2, iz0 + 16)])
    k.jangkar((ix0 + ix1) / 2.0 - 0.5, f + 1, iz0 + 12.5, "kotabesar/layanan", yaw=90.0, layanan="gilda_tempa", peran="EMPU",
              tokoh="ou_ye")
    k.titik("kerja:palu", (ix0 + ix1) // 2 + 1, f + 1, iz0 + 12, "west", {"peran": "EMPU"})
    # display hall (south lot)
    b = K.aula(k, x0 + 2, 50, x1 - 2, 90, h=7, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("west",),
               door_w=3, tile=True)
    jx0, jz0, jx1, jz1 = b["inner"]
    bf = b["f"]
    kelas = ("jian", "dao", "qiang", "zirah")
    for i, (rx, rz, face) in enumerate(((jx1, jz0 + 3, "west"), (jx1, jz0 + 12, "west"), (jx0 + 6, jz0, "south"),
                                       (jx0 + 6, jz1, "north"))):
        weapon_rack(k, rx, rz, bf, face, n=6)
        dx, dz = FACE[face]
        k.jangkar(rx + dx * 1 + 0.5 if face != "west" else rx - 1.5, bf + 1, rz + 0.5 if face == "west" else rz + dz + 0.5,
                  "kotabesar/rak_senjata", yaw={"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}[face],
                  kelas=kelas[i])
    for i, z in enumerate(range(jz0 + 4, jz1 - 3, 5)):
        armor_stand(k, jx0 + 2.5, bf + 1, z + 0.5, yaw=270.0, kit=("besi", "rantai", "kulit", "emas")[i % 4])
    # D-KS: display plinths with finished pieces, crates of blanks, columns and a runner from the west door
    K.dandan(k, (jx0 + 4, jz0 + 2, jx1 - 2, jz1 - 2), bf, "toko", lorong="x", pilar=True, karpet=("red", "black"),
             simpan=[((jx0 + jx1) // 2 - 4, jz1 - 7, (jx0 + jx1) // 2 + 4, jz1)], titik_maks=1)
    # Altar Jiwa Pusaka
    ax, az = (jx0 + jx1) // 2, jz1 - 4
    for dx in range(-2, 3):
        for dz in range(-1, 2):
            k.put(ax + dx, bf + 1, az + dz, "minecraft:polished_blackstone")
    for dx in (-2, 2):
        for dy in (2, 3):
            k.put(ax + dx, bf + dy, az, "minecraft:polished_blackstone_wall")
        k.put(ax + dx, bf + 4, az, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    k.put(ax, bf + 2, az, "minecraft:crying_obsidian")
    K.candle(k, ax - 1, bf + 2, az, 4, "purple")
    K.candle(k, ax + 1, bf + 2, az, 4, "purple")
    k.titik("dupa", ax, bf + 1, az - 2, "south")
    k.jangkar(ax + 0.5, bf + 2, az - 1.5, "kotabesar/layanan", yaw=0.0, layanan="altar_pusaka", peran="EMPU")
    k.kotak("bangunan", x0, y, 4, x1, f + 20, 40, "west", nama="gilda_tempa")


def gilda_kultivator(k, y=LY):
    """Gilda Kultivator (38.B.3) across lots (0,1)+(1,1): the hall of the great mission board (9 x 5, papers = client
    billboard), the rank counters, the Daftar Naga Muda stele (polished basalt), the 15 x 15 duel test floor with a rope
    ring, banners of the nine ranks."""
    a = K.aula(k, 6, 52, 89, 91, h=8, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south", "north"), door_w=5,
               tile=True, top="spire")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cx = (ix0 + ix1) // 2
    # the mission board on the west wall: 9 wide x 5 high
    bz0 = (iz0 + iz1) // 2 - 4
    for z in range(bz0, bz0 + 9):
        for dy in range(2, 7):
            edge = z in (bz0, bz0 + 8) or dy in (2, 6)
            k.put(ix0, f + dy, z, "log" if edge else ("minecraft:birch_planks" if (z + dy) % 3 else "minecraft:white_wool"),
                  {"axis": "y"} if edge else None)
    k.jangkar(ix0 + 1.5, f + 1, bz0 + 4.5, "kota/papan", yaw=270.0)
    # rank counters on the north wall
    K.counter(k, cx - 8, cx + 8, iz0 + 3, f, "south")
    for x in range(cx - 8, cx + 9, 4):
        for dy in (1, 2, 3):
            k.put(x, f + dy, iz0 + 3, "red")
    for x in range(ix0 + 4, ix1 - 3, 4):
        K.wall_banner(k, x, f + 6, iz0, "south", ("white", "light_gray", "lime", "green", "cyan", "light_blue", "blue",
                                                  "purple", "yellow")[(x // 4) % 9])
    k.jangkar(cx + 0.5, f + 1, iz0 + 2.5, "kotabesar/layanan", yaw=0.0, layanan="gilda_misi", peran="PETUGAS_GILDA",
              tokoh="xiao_mei")
    for x in (cx - 5, cx + 5):
        k.titik("kerja:meja_tulis", x, f + 1, iz0 + 2, "south", {"peran": "PETUGAS_GILDA"})
    # the Young Dragon List stele
    sx, sz = ix1 - 6, (iz0 + iz1) // 2
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            k.put(sx + dx, f + 1, sz + dz, "kerb_chisel")
    for dy in range(2, 9):
        k.put(sx, f + dy, sz, "minecraft:polished_basalt", {"axis": "y"})
        k.put(sx, f + dy, sz - 1, "minecraft:polished_basalt", {"axis": "y"})
    k.put(sx, f + 9, sz, "gold")
    k.put(sx, f + 9, sz - 1, "gold")
    k.jangkar(sx - 1.5, f + 1, sz + 0.5, "kotabesar/layanan", yaw=270.0, layanan="naga_muda", peran="KETUA_GILDA",
              tokoh="lin_fei")
    # duel test floor 15 x 15 with a rope ring
    tx0, tz0 = cx - 7, (iz0 + iz1) // 2 - 3
    for x in range(tx0, tx0 + 15):
        for z in range(tz0, tz0 + 15):
            k.put(x, f, z, "minecraft:smooth_stone")
            if x in (tx0, tx0 + 14) or z in (tz0, tz0 + 14):
                if (x + z) % 4 == 0:
                    k.put(x, f + 1, z, "wood_fence")
                    k.put(x, f + 2, z, "wood_fence")
    k.kotak("panggung", tx0 + 1, f + 1, tz0 + 1, tx0 + 13, f + 4, tz0 + 13, "north", acara="uji_duel")
    k.jangkar(cx + 0.5, f + 1, tz0 + 15.5, "kotabesar/layanan", yaw=0.0, layanan="uji_duel", peran="PENGUJI",
              tokoh="feng_ao")
    for z in range(tz0 + 1, tz0 + 14, 3):
        k.titik("penonton", tx0 - 2, f + 1, z, "east")
        k.titik("penonton", tx0 + 16, f + 1, z, "west")
    # D-KS: the hall around the ring: party desks (where teams sign missions), tea corners for waiting cultivators,
    # trophy plinths, inner columns, a red runner from door to door, book walls of mission records
    K.dandan(k, (ix0, iz0, ix1, iz1), f, "gilda", lorong="z", pilar=True, karpet=("red", "yellow"), titik_maks=10,
             simpan=[(tx0 - 3, tz0 - 1, tx0 + 17, tz0 + 16), (ix0, bz0 - 1, ix0 + 3, bz0 + 9), (cx - 9, iz0, cx + 9, iz0 + 5),
                     (sx - 3, sz - 3, sx + 2, sz + 2)])
    for i, zz in enumerate(range(iz0 + 6, iz1 - 4, 9)):
        # rank robes on stands along the east wall, one per rank band
        K.k_zirah(K.Ruang(k, (ix1 - 2, zz, ix1 - 2, zz), f), ix1 - 2, zz, yaw=90.0, kit=("kulit", "rantai", "besi", "emas")[i % 4])
    k.kotak("bangunan", 6, y, 52, 89, f + 24, 91, "south", nama="gilda_kultivator")


def balai_tabib(k, x0, z0, y=LY):
    """Balai Tabib (38.B.3, 38.B.4.4): six treatment beds behind folding screens (wool panels), medicine cabinets
    (shelves, brewing stands, barrels), a mortar table, a herb garden behind."""
    a = K.aula(k, x0 + 5, z0 + 5, x0 + 42, z0 + 30, h=6, podium_h=1, teras=1, roof="gable", along="x",
               door_sides=("south",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for i, x in enumerate(range(ix0 + 1, ix1 - 1, 5)):
        if i >= 6:
            break
        K.bed(k, x, f + 1, iz0 + 1, "north", role="minecraft:white_bed")
        k.titik("kasur", x, f + 1, iz0 + 1, "south", {"rawat": 1})
        for dy in (1, 2):
            k.put(x + 2, f + dy, iz0 + 1, "minecraft:white_wool")
            k.put(x + 2, f + dy, iz0 + 2, "minecraft:white_wool")
    for x in range(ix0, ix1 + 1):
        if x % 3 == 0:
            k.put(x, f + 1, iz1, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false",
                                                             "has_bottle_2": "true"})
        else:
            K.barrel(k, x, f + 1, iz1, "north")
        K.shelf(k, x, f + 2, iz1, "north")
    K.table(k, (ix0 + ix1) // 2, f + 1, iz1 - 3, "minecraft:white_carpet")
    k.titik("kerja:lumpang", (ix0 + ix1) // 2, f + 1, iz1 - 2, "north")
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz1 - 4.5, "kotabesar/layanan", yaw=180.0, layanan="tabib", peran="TABIB")
    for x in range(x0 + 6, x0 + 42):
        for z in range(z0 + 34, z0 + 44):
            if (x - x0) % 4:
                k.put(x, y, z, "minecraft:farmland", {"moisture": "7"})
            else:
                k.put(x, y, z, "minecraft:dirt_path")
            if (x - x0) % 4 and (x + z) % 2 == 0:
                k.put(x, y + 1, z, ("minecraft:fern", "minecraft:short_grass", "minecraft:azure_bluet")[(x * z) % 3])
    k.titik("kerja:cangkul", x0 + 20, y + 1, z0 + 38, "south")
    # D-KS: apothecary tables, two more recovery bays behind screens, jars and herb pots between the beds and the cabinets
    K.dandan(k, (ix0, iz0 + 3, ix1, iz1 - 1), f, "obat", titik_maks=2)
    k.kotak("bangunan", x0 + 5, y, z0 + 5, x0 + 42, f + 12, z0 + 30, "south", nama="balai_tabib")


def rumah_makan(k, x0, z0, y=LY, name="teratai", seats=10):
    """A restaurant (38.B.3 rumah_makan_teratai, kedai bakmi): an open kitchen with a lit campfire stove under a hood,
    smokers and a noodle board, ten tables with stools, a counter, red lanterns, a back yard with water jars."""
    a = K.aula(k, x0 + 4, z0 + 5, x0 + 43, z0 + 34, h=6, podium_h=1, teras=2, roof="hip", door_sides=("south",), door_w=3,
               tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    kitchen_row(k, ix0 + 1, ix1 - 1, iz0, f, "south")
    for x in (ix0 + 3, ix1 - 3):
        k.put(x, f + 1, iz0 + 1, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                       "waterlogged": "false"})
        for dy in range(3, 6):
            k.put(x, f + dy, iz0 + 1, "minecraft:bricks")
    K.counter(k, ix0 + 1, ix1 - 1, iz0 + 3, f, "south")
    n = 0
    for z in range(iz0 + 6, iz1 - 1, 4):
        for x in range(ix0 + 3, ix1 - 2, 5):
            if n >= seats:
                break
            K.furnish_dining(k, x, z, f, n=4)
            n += 1
    for x in range(ix0 + 2, ix1 - 1, 5):
        K.lit(k, x, f + 6, (iz0 + iz1) // 2, chain=1, role="lamp")
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz0 + 2.5, "kotabesar/layanan", yaw=0.0, layanan="rumah_makan", peran="KOKI",
              tokoh="gendut_zhu")
    k.titik("kerja:teko", (ix0 + ix1) // 2 + 3, f + 1, iz0 + 4, "south", {"peran": "PELAYAN"})
    for x in range(x0 + 6, x0 + 42, 3):
        if x % 2:
            k.put(x, y + 1, z0 + 40, "minecraft:water_cauldron", {"level": "3"})
        else:
            K.barrel(k, x, y + 1, z0 + 40)
    K.dandan(k, (ix0, iz0 + 4, ix1, iz1), f, "makan", titik_maks=2)
    k.kotak("bangunan", x0 + 4, y, z0 + 5, x0 + 43, f + 14, z0 + 34, "south", nama="rumah_makan_" + name)


# ===================================================================================================== pasar
def build_pasar():
    k = kanvas("pasar", h=64)
    skip = {(1, 0, "e"), (2, 0, "w")}
    K.street_grid(k, skip=skip)
    lelang_agung(k)
    rumah_uang(k, *o(0, 0))
    rumah_judi_batu(k, *o(0, 1))
    pasar_binatang(k, *o(1, 1))
    toko_senjata(k, *o(2, 1))
    kios_pil(k, *o(0, 2))
    lapak_sekte(k, *o(1, 2))
    pasar_malam(k, *o(2, 2))
    names = {(0, 0): "rumah_uang", (1, 0): "lelang_agung_a", (2, 0): "lelang_agung_b", (0, 1): "rumah_judi_batu",
             (1, 1): "pasar_binatang", (2, 1): "toko_senjata", (0, 2): "kios_pil", (1, 2): "lapak_sekte",
             (2, 2): "pasar_malam"}
    simpan_kanvas(k, "pasar", names, utuh=("lelang_agung_a", "lelang_agung_b"))


def lelang_agung(k, y=LY):
    """Balai Lelang Agung (38.B.3, 38.M.2.3) across lots (1,0)+(2,0): a 40 x 22 x 30 hall - the turning stage on the
    north (the turning is a client picture on kotabesar/panggung), 48 seats in curved rows, a mezzanine of six VIP booths
    with screens, the consignment office in the east annex, banners, a great chandelier of lanterns."""
    a = K.aula(k, 60, 4, 123, 43, h=12, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south",), door_w=5,
               tile=True, top="spire")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cx = (ix0 + ix1) // 2
    # stage
    for x in range(cx - 8, cx + 9):
        for z in range(iz0, iz0 + 6):
            k.put(x, f + 1, z, "wood")
            k.put(x, f + 2, z, "minecraft:red_carpet")
    for x in range(cx - 8, cx + 9):
        k.stairs(x, f + 1, iz0 + 6, "wood_stairs", "north")
    k.put(cx, f + 2, iz0 + 2, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    k.titik("kursi", cx, f + 2, iz0 + 1, "south", {"peran": "JURU_LELANG"})
    k.kotak("panggung", cx - 3, f + 2, iz0 + 2, cx + 3, f + 6, iz0 + 5, "south", acara="lelang")
    # 48 seats in curved rows
    seats = 0
    for row in range(4):
        r = 11 + row * 3
        for i in range(-6, 7):
            if seats >= 48:
                break
            ang = i * 0.16
            sx = cx + int(round(math.sin(ang) * r))
            sz = iz0 + 4 + int(round(math.cos(ang) * r))
            if ix0 < sx < ix1 and iz0 < sz < iz1:
                K.chair(k, sx, f + 1 + (row // 2), sz, "north")
                if row >= 2:
                    k.put(sx, f + 1, sz, "wood")
                k.titik("kursi", sx, f + 1 + (row // 2), sz, "north")
                seats += 1
    # mezzanine VIP booths along east and west walls at +6
    for side, xx in ((0, ix0), (1, ix1)):
        for z in range(iz0 + 4, iz1 - 1):
            for d in range(4):
                k.put(xx + (d if side == 0 else -d), f + 6, z, "wood")
            k.put(xx + (4 if side == 0 else -4), f + 7, z, "rail")
        for b in range(3):
            bz = iz0 + 6 + b * 9
            for d in range(4):
                for dy in (7, 8, 9):
                    k.put(xx + (d if side == 0 else -d), f + dy, bz, "minecraft:red_wool" if dy < 9 else "wood")
            K.chair(k, xx + (2 if side == 0 else -2), f + 7, bz + 3, "east" if side == 0 else "west")
            k.titik("kursi", xx + (2 if side == 0 else -2), f + 7, bz + 3, "east" if side == 0 else "west", {"vip": b + side * 3})
    for i in range(6):
        k.stairs(ix1 - 1, f + 1 + i, iz1 - 2 - i, "wood_stairs", "north")
    for z in range(iz0 + 6, iz1 - 2, 6):
        K.wall_banner(k, ix0, f + 10, z, "east")
        K.wall_banner(k, ix1, f + 10, z, "west")
    for x in range(cx - 6, cx + 7, 4):
        for z in range(iz0 + 12, iz1 - 2, 6):
            K.lit(k, x, f + 12, z, chain=2, role="lamp")
    k.jangkar(cx + 0.5, f + 2, iz0 + 3.5, "kotabesar/panggung", acara="lelang")
    k.jangkar(cx + 0.5, f + 2, iz0 + 1.5, "kotabesar/layanan", yaw=0.0, layanan="lelang_agung", peran="JURU_LELANG")
    # D-KS: preview plinths of the coming lots, crates of consigned goods and vases along the side aisles
    K.dandan(k, (ix0 + 5, iz0, ix1 - 5, iz1), f, "toko", simpan=[(cx - 21, iz0, cx + 21, iz0 + 25)], titik_maks=1)
    # consignment annex (east)
    c = K.aula(k, 126, 10, 141, 36, h=6, podium_h=1, teras=1, roof="gable", along="z", door_sides=("west",), door_w=1,
               tile=True)
    cx0, cz0, cx1, cz1 = c["inner"]
    cf = c["f"]
    K.counter_z(k, cx0 + 3, cz0 + 2, cz1 - 2, cf, "west")
    for z in range(cz0, cz1 + 1):
        K.barrel(k, cx1, cf + 1, z, "west")
        K.barrel(k, cx1, cf + 2, z, "west")
    K.dandan(k, (cx0 + 4, cz0, cx1 - 1, cz1), cf, "gudang", wall=False, titik_maks=1)
    k.jangkar(cx0 + 1.5, cf + 1, (cz0 + cz1) / 2.0 + 0.5, "kotabesar/layanan", yaw=90.0, layanan="titip_lelang",
              peran="JURU_LELANG")
    k.jangkar(96.5, LY + 1, 46.5, "kotabesar/zona", distrik="pasar", protect=100)
    k.kotak("bangunan", 60, y, 4, 123, f + 28, 43, "south", nama="lelang_agung")


def rumah_uang(k, x0, z0, y=LY):
    """Rumah Uang Seribu Tael (38.B.3): four teller windows behind iron bars, the vault (iron blocks, barrels, an iron
    door), a scale table, a waiting bench, the owner's desk with an abacus (a row of buttons on a slab)."""
    a = K.aula(k, x0 + 5, z0 + 5, x0 + 42, z0 + 38, h=6, podium_h=2, teras=2, roof="hip", door_sides=("south",), door_w=3,
               tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    wz = iz0 + 8
    for x in range(ix0, ix1 + 1):
        k.put(x, f + 1, wz, "wood")
        for dy in (2, 3):
            k.put(x, f + dy, wz, "minecraft:iron_bars" if x % 5 else "red")
        k.put(x, f + 4, wz, "wood")
    for i, x in enumerate(range(ix0 + 3, ix1 - 1, 6)):
        if i >= 4:
            break
        k.put(x, f + 2, wz, "minecraft:air")
        k.put(x, f + 1, wz, "wood_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                              "waterlogged": "false"})
        K.chair(k, x, f + 1, wz - 1, "south")
        k.titik("kerja:timbangan", x, f + 1, wz - 1, "south", {"peran": "BANKIR"})
        k.titik("lapak", x, f + 1, wz + 1, "north")
    for z in range(iz0, iz0 + 6):
        for x in range(ix0, ix0 + 8):
            if x in (ix0 + 7,) or z == iz0 + 5:
                for dy in range(1, 5):
                    k.put(x, f + dy, z, "minecraft:iron_block")
            else:
                if (x * z) % 7 == 0 and (x + z) % 2 == 0:
                    k.put(x, f + 1, z, "minecraft:gold_block")
                else:
                    K.barrel(k, x, f + 1, z)
    k.put(ix0 + 7, f + 1, iz0 + 3, "minecraft:iron_door", {"facing": "east", "half": "lower", "hinge": "left", "open": "false",
                                                          "powered": "false"})
    k.put(ix0 + 7, f + 2, iz0 + 3, "minecraft:iron_door", {"facing": "east", "half": "upper", "hinge": "left", "open": "false",
                                                          "powered": "false"})
    K.table(k, ix1 - 2, f + 1, iz0 + 2, "minecraft:white_carpet")
    for x in range(ix1 - 4, ix1 - 1):
        k.put(x, f + 2, iz0 + 1, "minecraft:dark_oak_button", {"face": "floor", "facing": "south", "powered": "false"})
    K.bench(k, ix0 + 3, iz1 - 2, "north", n=4)
    K.bench(k, ix1 - 6, iz1 - 2, "north", n=4)
    # D-KS: the customers' hall (benches, tea, vases) and the back office behind the teller bars (desks, ledgers)
    K.dandan(k, (ix0, wz + 2, ix1, iz1 - 3), f, "tunggu", titik_maks=2)
    K.dandan(k, (ix0 + 9, iz0, ix1, wz - 2), f, "kantor", titik_maks=2)
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, wz + 1.5, "kotabesar/layanan", yaw=180.0, layanan="bank", peran="BANKIR",
              tokoh="qian_duoduo")
    k.kotak("bangunan", x0 + 5, y, z0 + 5, x0 + 42, f + 14, z0 + 38, "south", nama="rumah_uang")


def rumah_judi_batu(k, x0, z0, y=LY):
    """Rumah Batu Sumber (38.B.3, 38.M.2.4): racks of raw source stones (stone, deepslate, tuff, calcite, cobbled
    deepslate in heaps), cutting tables (stonecutters), a spectators' gallery, the dealer's high table."""
    a = K.aula(k, x0 + 4, z0 + 5, x0 + 43, z0 + 40, h=7, podium_h=1, teras=2, roof="hip", door_sides=("east",), door_w=3,
               tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    stones = ("minecraft:stone", "minecraft:deepslate", "minecraft:tuff", "minecraft:calcite", "minecraft:cobbled_deepslate",
              "minecraft:andesite", "minecraft:granite")
    for x in range(ix0, ix1 - 6):
        for z in (iz0, iz0 + 1):
            for dy in range(1, 1 + 1 + (x + z) % 3):
                k.put(x, f + dy, z, stones[(x * 3 + z + dy) % len(stones)])
    for i, z in enumerate(range(iz0 + 6, iz1 - 2, 6)):
        k.put(ix0 + 6, f + 1, z, "minecraft:stonecutter", {"facing": "east"})
        k.put(ix0 + 7, f + 1, z, "wood_trapdoor", {"facing": "east", "half": "top", "open": "false", "powered": "false",
                                                   "waterlogged": "false"})
        k.titik("kerja:pahat", ix0 + 5, f + 1, z, "east", {"peran": "PEMOTONG_BATU"})
        for dx in (10, 12):
            K.chair(k, ix0 + dx, f + 1, z, "west")
            k.titik("penonton", ix0 + dx, f + 1, z, "west")
    for z in range(iz0 + 4, iz1 - 1):
        k.put(ix1 - 3, f + 1, z, "wood")
        if z % 2:
            k.stairs(ix1 - 2, f + 2, z, "wood_stairs", "west")
    K.counter(k, ix1 - 8, ix1 - 1, iz0 + 3, f, "south")
    k.titik("kursi", ix1 - 4, f + 1, iz0 + 2, "south", {"peran": "BANDAR_BATU"})
    k.jangkar(ix1 - 4.5, f + 1, iz0 + 4.5, "kotabesar/layanan", yaw=0.0, layanan="judi_batu", peran="BANDAR_BATU",
              tokoh="lao_shi")
    k.jangkar(ix0 + 7.5, f + 1, (iz0 + iz1) / 2.0 + 0.5, "kotabesar/panggung", acara="judi_batu")
    K.dandan(k, (ix0 + 14, iz0 + 3, ix1 - 4, iz1), f, "judi", titik_maks=2)
    k.kotak("bangunan", x0 + 4, y, z0 + 5, x0 + 43, f + 16, z0 + 40, "east", nama="rumah_judi_batu")


def pasar_binatang(k, x0, z0, y=LY):
    """Pasar Binatang Roh (38.B.3): open pens of oak fence with hay and troughs (the beasts are 38EK entities), a reed
    pond for water beasts, a feed stall, the price board, the trainer's ring."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 3, z0 + 45):
            k.put(x, y, z, "minecraft:coarse_dirt" if (x * 7 + z) % 5 else "minecraft:dirt_path")
    for i in range(6):
        px0 = x0 + 5 + (i % 3) * 13
        pz0 = z0 + 5 + (i // 3) * 14
        for x in range(px0, px0 + 10):
            for z in range(pz0, pz0 + 9):
                if x in (px0, px0 + 9) or z in (pz0, pz0 + 8):
                    k.put(x, y + 1, z, "minecraft:oak_fence")
                else:
                    k.put(x, y, z, "minecraft:grass_block", {"snowy": "false"})
        k.put(px0 + 4, y + 1, pz0 + 8, "minecraft:oak_fence_gate", {"facing": "south", "in_wall": "false",
                                                                    "open": "false", "powered": "false"})
        k.put(px0 + 1, y + 1, pz0 + 1, "minecraft:hay_block", {"axis": "y"})
        k.put(px0 + 8, y + 1, pz0 + 1, "minecraft:water_cauldron", {"level": "3"})
        k.titik("lapak", px0 + 4, y + 1, pz0 + 9, "north", {"kandang": i})
    K.pond(k, x0 + 36, z0 + 36, 5, 4, y, lilies=4)
    for (bx, bz) in ((x0 + 31, z0 + 33), (x0 + 41, z0 + 39)):
        K.bamboo_clump(k, bx, bz, y + 1, 5)
    sx, sz = K.stall(k, x0 + 8, z0 + 38, "north", w=3, goods=("minecraft:hay_block", "minecraft:melon"))
    K.notice_board(k, x0 + 18, z0 + 42, "north", w=5)
    k.jangkar(sx + 0.5, y + 1, sz + 0.5, "kotabesar/layanan", yaw=180.0, layanan="pasar_binatang", peran="PEDAGANG_BINATANG",
              tokoh="ma_laoliu")
    k.titik("kerja:sapu", x0 + 24, y + 1, z0 + 30, "north")


def toko_senjata(k, x0, z0, y=LY):
    """Paviliun Seribu Bilah (38.B.3, 38.M.2.1): the weapon pavilion - racks along both walls (anchors
    kotabesar/rak_senjata), armour stands, a sword display table, the merchant's counter, a test yard of straw dummies."""
    a = K.aula(k, x0 + 5, z0 + 4, x0 + 42, z0 + 30, h=7, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south",),
               door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    weapon_rack(k, ix0, iz0 + 1, f, "east", n=12)
    weapon_rack(k, ix1, iz0 + 1, f, "west", n=12)
    k.jangkar(ix0 + 1.5, f + 1, iz0 + 6.5, "kotabesar/rak_senjata", yaw=270.0, kelas="pedang")
    k.jangkar(ix1 - 0.5, f + 1, iz0 + 6.5, "kotabesar/rak_senjata", yaw=90.0, kelas="tombak")
    for i, x in enumerate(range(ix0 + 4, ix1 - 3, 5)):
        armor_stand(k, x + 0.5, f + 1, iz0 + 1.5, yaw=0.0, kit=("besi", "rantai", "emas", "kulit")[i % 4])
    for x in range((ix0 + ix1) // 2 - 2, (ix0 + ix1) // 2 + 3):
        k.put(x, f + 1, (iz0 + iz1) // 2, "kerb_chisel")
        k.put(x, f + 2, (iz0 + iz1) // 2, "minecraft:iron_bars")
    K.counter(k, ix0 + 8, ix1 - 8, iz1 - 3, f, "south")
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz1 - 3.5, "kotabesar/layanan", yaw=0.0, layanan="toko_senjata",
              peran="PEDAGANG_SENJATA")
    for i in range(3):
        dx, dz = x0 + 12 + i * 10, z0 + 38
        k.put(dx, y + 1, dz, "minecraft:oak_fence")
        k.put(dx, y + 2, dz, "minecraft:hay_block", {"axis": "y"})
        k.put(dx, y + 3, dz, "minecraft:target", {"power": "0"})
        k.titik("latihan", dx, y + 1, dz - 2, "south")
    K.dandan(k, (ix0 + 2, iz0 + 3, ix1 - 2, iz1 - 4), f, "senjata", lorong="z", karpet=("red", "black"), titik_maks=1)
    k.kotak("bangunan", x0 + 5, y, z0 + 4, x0 + 42, f + 18, z0 + 30, "south", nama="toko_senjata")


def kios_pil(k, x0, z0, y=LY):
    """Kios Pil (38.B.3): two pill pavilions (anchors kota/pil, kota/pil) facing a small square, the treasure stall
    (kota/pusaka) and the talisman shop (jimat) in a rumah toko."""
    for i, (px, pz) in enumerate(((x0 + 4, z0 + 4), (x0 + 26, z0 + 4))):
        a = K.aula(k, px, pz, px + 17, pz + 14, h=5, podium_h=1, teras=1, roof="hip", door_sides=("south",), door_w=3,
                   tile=False)
        ix0, iz0, ix1, iz1 = a["inner"]
        f = a["f"]
        K.counter(k, ix0 + 1, ix1 - 1, iz1 - 2, f, "south")
        for x in range(ix0, ix1 + 1):
            for dy in (1, 2, 3):
                if dy > 1:
                    K.shelf(k, x, f + dy, iz0, "south")
                elif x % 3 == 0:
                    k.put(x, f + dy, iz0, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "true",
                                                                      "has_bottle_2": "false"})
                else:
                    K.barrel(k, x, f + dy, iz0, "south")
        k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz1 - 3.5, "kota/pil", yaw=0.0)
    K.tempel(k, rumah_toko(tl._seed("pasar/jimat"), 20, 22, goods=("minecraft:flower_pot",)), x0 + 4, z0 + 24, 0)
    sx, sz = K.stall(k, x0 + 32, z0 + 30, "west", w=3, goods=("minecraft:decorated_pot",))
    k.jangkar(sx + 0.5, y + 1, sz + 0.5, "kota/pusaka", yaw=270.0)
    k.jangkar(x0 + 14.5, y + 1, z0 + 41.5, "kotabesar/layanan", yaw=0.0, layanan="jimat", peran="PEDAGANG_KELILING")


def lapak_sekte(k, x0, z0, y=LY):
    """Deret Lapak Sekte (38.B.3, 38.M.4.1): six rentable sect kiosks in a roofed row, each with a counter, shelves, a
    banner bracket for the renting sect (the sect emblem is a client picture), anchors kotabesar/lapak_sekte 0-5."""
    for n in range(6):
        bx = x0 + 4 + (n % 3) * 14
        bz = z0 + 4 if n < 3 else z0 + 26
        face = "south" if n < 3 else "north"
        for x in range(bx, bx + 12):
            for z in range(bz, bz + 16):
                edge = x in (bx, bx + 11) or z in (bz, bz + 15)
                front = (z == bz + 15) if n < 3 else (z == bz)
                if edge and not front:
                    for dy in range(1, 5):
                        k.put(x, y + dy, z, "plaster" if (x - bx) % 11 else "red")
                if front and (x - bx) % 11 == 0:
                    for dy in range(1, 5):
                        k.put(x, y + dy, z, "red")
        for x in range(bx - 1, bx + 13):
            for z in range(bz - 1, bz + 17):
                k.slab(x, y + 5, z, "tile_slab")
        fz = bz + 13 if n < 3 else bz + 2
        K.counter(k, bx + 2, bx + 9, fz, y, face)
        for x in range(bx + 1, bx + 11):
            K.shelf(k, x, y + 2, bz + 1 if n < 3 else bz + 14, face)
        k.put(bx + 5, y + 4, bz + (15 if n < 3 else 0), "minecraft:white_wall_banner", {"facing": face})
        k.jangkar(bx + 6.0, y + 1, fz + (-1.5 if n < 3 else 2.5), "kotabesar/lapak_sekte", yaw=0.0 if n < 3 else 180.0,
                  nomor=n)
        k.titik("lapak", bx + 5, y + 1, fz + (-1 if n < 3 else 1), face, {"nomor": n})


def pasar_malam(k, x0, z0, y=LY):
    """Pasar Malam (38.B.3, 38.B.6): a lane of twelve kiosks under strings of paper lanterns (lit by the client during the
    night market), food stalls with smokers, a fortune teller's tent; anchor kotabesar/panggung acara=pasar_malam with
    the twelve stall points."""
    pts = []
    n = 0
    for row, (zz, face) in enumerate(((z0 + 10, "south"), (z0 + 34, "north"))):
        for i in range(6):
            sx = x0 + 5 + i * 7
            cloth = ("cloth", "cloth_alt") if (i + row) % 2 else ("minecraft:yellow_wool", "cloth")
            px, pz = K.stall(k, sx, zz, face, w=3, cloth=cloth,
                             goods=("minecraft:flower_pot", "minecraft:decorated_pot", "minecraft:melon"))
            k.titik("lapak", px, y + 1, pz, face, {"nomor": n})
            pts += [px - (x0 + 24), 1, pz - (z0 + 24)]
            n += 1
    for x in range(x0 + 4, x0 + 45, 2):
        for z in (z0 + 16, z0 + 28):
            if x % 4:
                k.put(x, y + 6, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
            else:
                k.put(x, y + 6, z, "lamp", {"axis": "y"})
    for x in (x0 + 4, x0 + 44):
        for z in (z0 + 16, z0 + 28):
            for dy in range(1, 7):
                k.put(x, y + dy, z, "log", {"axis": "y"})
    # fortune teller's tent at the east end
    for x in range(x0 + 38, x0 + 44):
        for z in range(z0 + 19, z0 + 26):
            k.put(x, y + 4, z, "minecraft:purple_wool" if (x + z) % 2 else "minecraft:magenta_wool")
    k.put(x0 + 41, y + 1, z0 + 22, "wood_fence")
    k.put(x0 + 41, y + 2, z0 + 22, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    k.titik("lapak", x0 + 42, y + 1, z0 + 22, "west", {"nomor": 12})
    k.jangkar(x0 + 24.5, y + 1, z0 + 24.5, "kotabesar/panggung", acara="pasar_malam", pts=pts)


# ===================================================================================================== hunian (houses)
# Residential lots shared by hunian_barat and arena come from one weighted pool kota_besar/hunian/rumah (38.B.3): six
# whole-lot variants (each its own 48 x 48 canvas with its 3-block edge streets), so neighbouring capitals and even
# neighbouring lots differ while the NBT budget stays small. LotPick (38W) picks the variant by hash per lot.
RUMAH_VARIAN = []


def daftar_pool(distrik, i, j, pid, templates, utuh=False):
    LOTS[distrik].append({"dx": dxz(i), "dz": dxz(j), "pool": pid, "putar": 0})
    POOLS.setdefault(pid, (list(templates), utuh))


def lot_sendiri(name, draw, h=48):
    """A whole-lot canvas 48 x h x 48 with its own edge streets, drawn by draw(v), saved as kota_besar/<name>."""
    v = K.Kanvas(tl.LOT, h, tl.LOT, seed=tl._seed(F + "/" + name))
    K.street_grid(v, nx=1, nz=1)
    draw(v)
    lots = K.potong(v, {(0, 0): name}, nx=1, nz=1)
    lots[(0, 0)].simpan()
    return tid(name)


CROP_MAX = {"minecraft:carrots": 7, "minecraft:potatoes": 7, "minecraft:wheat": 7, "minecraft:beetroots": 3}


def rumah_kecil(seed, w=20, d=20, roof_tile=False, beds=2, crop="minecraft:carrots"):
    """A commoner's house in its own yard (38.B.3 hunian): stone plinth course, plastered walls between dark beams,
    a wooden gable roof, one main room (family altar shelf, dining table, a kitchen corner with stove, water jar and
    barrels) and a bedroom behind a plank partition; outside a woodpile, a vegetable patch, washing on a line, a bench
    by the gate and a wattle fence."""
    b = K.sub(w, 20, d, seed)
    y = LY
    b.fill(0, y - 1, 0, w - 1, y - 1, d - 1, "found")
    for x in range(w):
        for z in range(d):
            b.put(x, y, z, "ground" if (x * 3 + z * 7) % 11 else "minecraft:coarse_dirt", {"snowy": "false"}
                  if (x * 3 + z * 7) % 11 else None)
    # wattle fence with a gate on the south
    gx = w // 2
    for x in range(w):
        for z in range(d):
            if x in (0, w - 1) or z in (0, d - 1):
                if z == d - 1 and x == gx:
                    continue
                b.put(x, y + 1, z, "minecraft:spruce_fence" if (x + z) % 5 else "log", {"axis": "y"} if (x + z) % 5 == 0
                      else None)
    b.put(gx, y + 1, d - 1, "wood_gate", {"facing": "south", "in_wall": "false", "open": "false", "powered": "false"})
    for z in range(11, d - 1):
        b.put(gx, y, z, "paving" if z % 3 else "paving_alt")
    hx0, hz0, hx1, hz1 = 2, 2, w - 3, 10
    b.fill(hx0, y, hz0, hx1, y, hz1, "kerb")
    ix0, iz0, ix1, iz1 = K.hall(b, hx0, hz0, hx1, hz1, y, 4, door_sides=("south",), door_w=1, windows=4,
                                pillar_role="beam", lamps=False)
    b.fill(ix0, y, iz0, ix1, y, iz1, "wood_alt")
    if roof_tile:
        K.gable(b, hx0, y + 5, hz0, hx1, hz1, along="x", overhang=1, pearl=False, role="tile_stairs",
                slab_role="tile_slab", full_role="tile")
    else:
        K.gable(b, hx0, y + 5, hz0, hx1, hz1, along="x", overhang=1, pearl=False)
    # bedroom (west) behind a partition
    px = ix0 + 4
    for z in range(iz0, iz1 + 1):
        for dy in range(1, 4):
            gap = z == iz1 - 1 and dy <= 2
            b.put(px, y + dy, z, "minecraft:air" if gap else "wood")
    for i in range(beds):
        bx = ix0 + i * 2
        if bx < px:
            K.bed(b, bx, y + 1, iz0 + 1, "north")
            b.titik("kasur", bx, y + 1, iz0 + 1, "south")
    K.chest(b, px - 1, y + 1, iz1, None, "north")
    b.titik("peti_milik", px - 1, y + 1, iz1 - 1, "south")
    b.put(ix0, y + 1, iz1, "minecraft:barrel", {"facing": "up", "open": "false"})
    K.lit(b, ix0 + 2, y + 4, (iz0 + iz1) // 2, chain=1)
    # main room: altar shelf on the north wall, dining table, kitchen corner (east)
    cx = (px + 1 + ix1) // 2
    K.shelf(b, cx, y + 2, iz0, "south")
    K.candle(b, cx - 1, y + 2, iz0, 1, "red")
    K.candle(b, cx + 1, y + 2, iz0, 1, "red")
    b.titik("dupa", cx, y + 1, iz0 + 1, "north")
    K.table(b, cx - 1, y + 1, iz0 + 3)
    K.chair(b, cx - 2, y + 1, iz0 + 3, "east")
    K.chair(b, cx, y + 1, iz0 + 3, "west")
    b.titik("meja_makan", cx - 1, y + 1, iz0 + 3, "south")
    b.titik("kursi", cx - 2, y + 1, iz0 + 3, "east")
    b.titik("kursi", cx, y + 1, iz0 + 3, "west")
    b.put(ix1, y + 1, iz0, "minecraft:furnace", {"facing": "west", "lit": "false"})
    b.put(ix1, y + 1, iz0 + 1, "minecraft:water_cauldron", {"level": "3"})
    K.barrel(b, ix1, y + 1, iz0 + 2, "west")
    b.put(ix1, y + 1, iz0 + 3, "minecraft:crafting_table")
    b.put(ix1, y + 3, iz0, "wood_trapdoor", {"facing": "west", "half": "top", "open": "false", "powered": "false",
                                             "waterlogged": "false"})
    b.titik("kerja:kuali", ix1 - 1, y + 1, iz0 + 1, "east")
    K.lit(b, cx + 1, y + 4, (iz0 + iz1) // 2, chain=1)
    # yard: woodpile against the east fence, vegetable patch west, washing line, bench by the gate
    for z in range(12, 16):
        for dy in (1, 2):
            b.put(w - 2, y + dy, z, "minecraft:stripped_spruce_log", {"axis": "z"})
    b.put(w - 3, y + 1, 13, "minecraft:oak_log", {"axis": "y"})
    b.titik("kerja:gergaji", w - 4, y + 1, 13, "east")
    for x in range(2, gx - 2):
        for z in range(13, d - 3):
            if z == 15 and x == 3:
                b.put(x, y, z, "minecraft:water", {"level": "0"})
            elif z == 15:
                b.put(x, y, z, "minecraft:dirt_path")
            else:
                b.put(x, y, z, "minecraft:farmland", {"moisture": "7"})
                b.put(x, y + 1, z, crop, {"age": str(min(CROP_MAX.get(crop, 7), 2 + (x + z) % 6))})
    b.titik("kerja:cangkul", gx - 2, y + 1, 14, "west")
    for x in (gx + 2, w - 4):
        for dy in (1, 2, 3):
            b.put(x, y + dy, d - 4, "wood_fence")
    for x in range(gx + 3, w - 4):
        b.put(x, y + 3, d - 4, "minecraft:white_wool" if x % 2 else "minecraft:light_blue_wool")
    K.bench(b, gx + 2, d - 2, "south", n=2)
    b.titik("obrol", gx + 2, y + 1, d - 2, "south")
    b.titik("main", gx + 3, y + 1, 16, "north")
    b.titik("pintu", (hx0 + hx1) // 2, y + 1, hz1 + 1, "south")
    b.jalur([(gx, d - 1), (gx, 12), ((hx0 + hx1) // 2, hz1 + 1)], jalur="rumah")
    b.kotak("bangunan", hx0, y, hz0, hx1, y + 10, hz1, "south", nama="rumah_kecil")
    return b


def sumur_umum(k, cx, cz, y=LY, r=3):
    """A public well with a stone apron (radius r), a roofed windlass frame on two red posts, a bucket (cauldron), a
    water jar and a washing slab."""
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            k.put(cx + dx, y, cz + dz, "kerb" if max(abs(dx), abs(dz)) == r else "paving")
    K.well(k, cx, cz, y)
    for dx in (-2, 2):
        for dy in range(1, 5):
            k.put(cx + dx, y + dy, cz, "red")
    for dx in range(-2, 3):
        k.stairs(cx + dx, y + 5, cz - 1, "tile_stairs", "south")
        k.stairs(cx + dx, y + 5, cz + 1, "tile_stairs", "north")
        k.put(cx + dx, y + 5, cz, "tile")
    k.put(cx + r, y + 1, cz + r - 1, "minecraft:water_cauldron", {"level": "3"})
    k.put(cx - r, y + 1, cz - r + 1, "minecraft:cauldron")
    k.slab(cx - r, y + 1, cz + r - 1, "kerb_slab")
    k.titik("obrol", cx + 1, y + 1, cz + r, "north")
    k.titik("kerja:sapu", cx - 1, y + 1, cz + r, "north")


def rumah_varian_a(v):
    K.tempel(v, siheyuan(tl._seed("hunian/rumah_a"), 42, 42, tree="pine"), 3, 3, 0)


def rumah_varian_b(v):
    K.tempel(v, siheyuan(tl._seed("hunian/rumah_b"), 42, 42, tree="cherry", rich=True), 3, 3, 0)


def rumah_varian_c(v):
    """Two shophouses on the south street and a shared back yard with a well and vegetable plots."""
    K.tempel(v, rumah_toko(tl._seed("hunian/rumah_c1"), 20, 24, goods=("minecraft:flower_pot", "minecraft:melon")),
             3, 21, 0)
    K.tempel(v, rumah_toko(tl._seed("hunian/rumah_c2"), 20, 24, goods=("minecraft:decorated_pot", "minecraft:pumpkin")),
             24, 21, 0)
    K.tempel(v, rumah_kecil(tl._seed("hunian/rumah_c3"), 22, 17, roof_tile=True, crop="minecraft:potatoes"), 3, 3, 2)
    sumur_umum(v, 34, 11)
    for x in range(27, 32):
        v.put(x, LY + 1, 4, "minecraft:composter", {"level": str(2 + x % 6)})
    for x in range(27, 42):
        for z in (17, 18):
            v.put(x, LY, z, "minecraft:farmland", {"moisture": "7"})
            v.put(x, LY + 1, z, "minecraft:wheat", {"age": str(4 + x % 4)})
    v.titik("kerja:cangkul", 26, LY + 1, 17, "east")
    K.tree(v, 41, LY + 1, 5, "willow")


def rumah_varian_d(v):
    """Two timber tenements facing each other over a long washing court."""
    K.tempel(v, rumah_susun(tl._seed("hunian/rumah_d1"), 42, 20), 3, 3, 0)
    K.tempel(v, rumah_susun(tl._seed("hunian/rumah_d2"), 42, 20), 3, 25, 2)
    for x in range(8, 40, 6):
        v.put(x, LY + 1, 23, "minecraft:water_cauldron", {"level": "3"})
        v.titik("obrol", x + 1, LY + 1, 23, "north" if x % 12 else "south")


def rumah_varian_e(v):
    """Four small family houses around a shared yard with a well and a big tree (a 'lane court')."""
    seeds = ("hunian/rumah_e1", "hunian/rumah_e2", "hunian/rumah_e3", "hunian/rumah_e4")
    crops = ("minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots", "minecraft:wheat")
    for n, (x, z, r) in enumerate(((3, 3, 0), (27, 3, 0), (3, 27, 2), (27, 27, 2))):
        K.tempel(v, rumah_kecil(tl._seed(seeds[n]), 18, 18, roof_tile=n % 2 == 1, crop=crops[n]), x, z, r)
    sumur_umum(v, 24, 24, r=2)
    for x in (22, 26):
        v.titik("main", x, LY + 1, 22, "south")


def rumah_varian_f(v):
    """A tenement, a shophouse and a small house round a garden with a pond."""
    K.tempel(v, rumah_susun(tl._seed("hunian/rumah_f1"), 24, 20), 3, 3, 0)
    K.tempel(v, rumah_toko(tl._seed("hunian/rumah_f2"), 18, 22, goods=("minecraft:flower_pot",)), 27, 23, 0)
    K.tempel(v, rumah_kecil(tl._seed("hunian/rumah_f3"), 18, 18, roof_tile=True, crop="minecraft:beetroots"), 27, 3, 1)
    K.garden(v, 4, 26, 24, 43, trees=("cherry",))
    K.bench(v, 10, 25, "north", n=3)
    v.titik("obrol", 11, LY + 1, 25, "south")


def build_rumah_pool():
    if RUMAH_VARIAN:
        return RUMAH_VARIAN
    for suffix, fn in (("a", rumah_varian_a), ("b", rumah_varian_b), ("c", rumah_varian_c), ("d", rumah_varian_d),
                       ("e", rumah_varian_e), ("f", rumah_varian_f)):
        RUMAH_VARIAN.append(lot_sendiri("hunian/rumah_" + suffix, fn))
    return RUMAH_VARIAN


# ===================================================================================================== hunian_barat
def build_hunian_barat():
    """Distrik Hunian Barat (38.B.3, south-west): six residential lots from the house pool, the craftsmen's yard
    (kotabesar/tukang), the neighbourhood square with the well and the Earth God shrine, and the little noodle house."""
    rumah = build_rumah_pool()
    k = kanvas("hunian_barat", h=48)
    K.street_grid(k)
    bengkel_tukang(k, *o(0, 1))
    alun_hunian(k, *o(1, 1))
    rumah_makan_kecil(k, *o(1, 2))
    names = {(0, 1): "bengkel", (1, 1): "alun_hunian", (1, 2): "rumah_makan_kecil"}
    simpan_kanvas(k, "hunian_barat", names)
    for ij in ((0, 0), (1, 0), (2, 0), (2, 1), (0, 2), (2, 2)):
        daftar_pool("hunian_barat", ij[0], ij[1], "hunian/rumah", rumah)


def bengkel_tukang(k, x0, z0, y=LY):
    """Bengkel Tukang (38.B.3, 38.R): the builders' yard where the city's repair crews live and work - an open-fronted
    timber workshop (workbenches, sawhorses, a lathe corner, tool racks), a mason's shed with a stonecutter and dressed
    stone, stacked lumber, a scaffolding tower being tried out, a hand cart, the foreman's house; anchor kotabesar/tukang."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 3, z0 + 45):
            k.put(x, y, z, "minecraft:coarse_dirt" if (x * 7 + z * 3) % 6 else "minecraft:gravel")
    # workshop: open on the south, gable roof along x
    wx0, wz0, wx1, wz1 = x0 + 4, z0 + 4, x0 + 30, z0 + 16
    k.fill(wx0, y, wz0, wx1, y, wz1, "wood_alt")
    k.air(wx0 + 1, y + 1, wz0 + 1, wx1 - 1, y + 5, wz1)
    for x in range(wx0, wx1 + 1):
        for dy in range(1, 6):
            k.put(x, y + dy, wz0, "plaster" if (x - wx0) % 4 else "beam", {"axis": "y"} if (x - wx0) % 4 == 0 else None)
    for z in range(wz0, wz1 + 1):
        for dy in range(1, 6):
            for x in (wx0, wx1):
                k.put(x, y + dy, z, "plaster" if (z - wz0) % 4 else "beam", {"axis": "y"} if (z - wz0) % 4 == 0 else None)
    for x in range(wx0, wx1 + 1, 4):
        for dy in range(1, 6):
            k.put(x, y + dy, wz1, "beam", {"axis": "y"})
    K.beam_band(k, wx0, wz0, wx1, wz1, y + 6)
    k.fill(wx0 + 1, y + 6, wz0 + 1, wx1 - 1, y + 6, wz1 - 1, "wood")
    K.brackets(k, wx0, wz0, wx1, wz1, y + 6)
    K.gable(k, wx0, y + 7, wz0, wx1, wz1, along="x", overhang=1)
    for i, x in enumerate(range(wx0 + 2, wx1 - 1, 5)):
        k.put(x, y + 1, wz0 + 2, "minecraft:crafting_table")
        k.put(x + 1, y + 1, wz0 + 2, "wood_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                       "powered": "false", "waterlogged": "false"})
        for dx in range(0, 4):
            K.shelf(k, x + dx, y + 3, wz0, "south")
        k.titik("kerja:gergaji" if i % 2 else "kerja:palu", x, y + 1, wz0 + 3, "north", {"peran": "TUKANG"})
    for z in (wz0 + 7, wz0 + 10):
        for x in (wx0 + 4, wx0 + 7):
            k.put(x, y + 1, z, "wood_fence")
        for x in range(wx0 + 4, wx0 + 8):
            k.slab(x, y + 2, z, "wood_slab")
    k.put(wx0 + 6, y + 3, wz0 + 7, "minecraft:stripped_spruce_log", {"axis": "x"})
    k.put(wx1 - 3, y + 1, wz0 + 8, "minecraft:loom", {"facing": "south"})
    k.put(wx1 - 2, y + 1, wz0 + 8, "minecraft:fletching_table")
    K.lit(k, (wx0 + wx1) // 2, y + 6, (wz0 + wz1) // 2, chain=1)
    K.lit(k, wx0 + 5, y + 6, (wz0 + wz1) // 2, chain=1)
    K.lit(k, wx1 - 5, y + 6, (wz0 + wz1) // 2, chain=1)
    # lumber stacks in the yard
    for z in range(z0 + 20, z0 + 26):
        for dy in range(1, 3 + (z % 2)):
            for x in range(x0 + 5, x0 + 13):
                k.put(x, y + dy, z, "minecraft:stripped_spruce_log" if dy % 2 else "minecraft:stripped_dark_oak_log",
                      {"axis": "x"})
    for x in range(x0 + 5, x0 + 13, 3):
        k.put(x, y + 1, z0 + 19, "wood_fence")
        k.put(x, y + 1, z0 + 26, "wood_fence")
    # mason's shed: stonecutter, dressed stone, a block in work
    sx0, sz0 = x0 + 16, z0 + 20
    for x in range(sx0, sx0 + 10):
        for z in range(sz0, sz0 + 8):
            if x in (sx0, sx0 + 9) and z in (sz0, sz0 + 7):
                for dy in range(1, 5):
                    k.put(x, y + dy, z, "log", {"axis": "y"})
            k.slab(x, y + 5, z, "roof_slab")
    k.put(sx0 + 3, y + 1, sz0 + 3, "minecraft:stonecutter", {"facing": "south"})
    k.put(sx0 + 5, y + 1, sz0 + 3, "minecraft:chiseled_stone_bricks")
    k.put(sx0 + 6, y + 1, sz0 + 3, "minecraft:polished_andesite")
    for x in range(sx0 + 1, sx0 + 9):
        k.put(x, y + 1, sz0 + 6, ("minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:smooth_stone")[x % 3])
        if x % 2:
            k.put(x, y + 2, sz0 + 6, "minecraft:stone_bricks")
    k.titik("kerja:pahat", sx0 + 4, y + 1, sz0 + 4, "north", {"peran": "TUKANG"})
    # scaffolding tower (vanilla scaffolding as a trial frame)
    tx, tz = x0 + 38, z0 + 7
    for dy in range(1, 9):
        for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)):
            k.put(tx + dx, y + dy, tz + dz, "minecraft:scaffolding",
                  {"bottom": "false", "distance": "0", "waterlogged": "false"})
    # hand cart: a trapdoor bed on two "wheels" (barrels on their sides) with planks
    k.put(x0 + 34, y + 1, z0 + 22, "minecraft:barrel", {"facing": "east", "open": "false"})
    k.put(x0 + 34, y + 1, z0 + 24, "minecraft:barrel", {"facing": "east", "open": "false"})
    for z in range(z0 + 21, z0 + 26):
        k.put(x0 + 35, y + 1, z, "wood_trapdoor", {"facing": "west", "half": "top", "open": "false", "powered": "false",
                                                   "waterlogged": "false"})
        k.put(x0 + 35, y + 2, z, "minecraft:spruce_planks" if z % 2 else "minecraft:stone_bricks")
    k.put(x0 + 35, y + 1, z0 + 20, "wood_fence")
    # foreman's house in the south part
    K.tempel(k, rumah_kecil(tl._seed("hunian/mandor"), 20, 16, roof_tile=True, crop="minecraft:wheat"), x0 + 24, z0 + 29, 0)
    K.notice_board(k, x0 + 10, z0 + 41, "south", w=5)
    k.jangkar(x0 + 10.5, y + 1, z0 + 18.5, "kotabesar/tukang", yaw=0.0)
    k.jangkar(x0 + 16.5, y + 1, z0 + 17.5, "kotabesar/layanan", yaw=0.0, layanan="perbaikan", peran="TUKANG")
    k.titik("jalan", x0 + 24, y + 1, z0 + 44, "south", {"urut": 0, "jalur": "tukang"})
    k.titik("jalan", x0 + 24, y + 1, z0 + 18, "south", {"urut": 1, "jalur": "tukang"})
    k.kotak("bangunan", wx0, y, wz0, wx1, y + 14, wz1, "south", nama="bengkel_tukang")


def kuil_tudi(k, x0, z0, y=LY):
    """A small Earth God shrine (tudi gong, 38.B.3 kuil kecil): a 9 x 7 hall on a two-step plinth, red doors, an
    altar with the god's stone image, candles and an offering jar, a bronze incense burner in front,
    a pair of stone lions."""
    a = K.aula(k, x0, z0, x0 + 10, z0 + 8, h=4, podium_h=2, teras=0, roof="hip", door_sides=("south",), door_w=3,
               tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cx = (ix0 + ix1) // 2
    for dx in (-1, 0, 1):
        k.put(cx + dx, f + 1, iz0, "kerb")
    k.put(cx, f + 2, iz0, "statue")
    k.put(cx, f + 3, iz0, "minecraft:chiseled_stone_bricks")
    K.candle(k, cx - 1, f + 2, iz0, 3, "red")
    K.candle(k, cx + 1, f + 2, iz0, 3, "red")
    k.put(cx, f + 1, iz0 + 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    K.incense_burner(k, cx, y + 1, iz1 + 4)
    K.lion(k, cx - 3, y + 1, iz1 + 4, "south")
    K.lion(k, cx + 3, y + 1, iz1 + 4, "south")
    k.titik("dupa", cx, y + 1, iz1 + 5, "north")
    k.titik("meditasi", cx, f + 1, iz0 + 2, "north")
    k.kotak("bangunan", x0, y, z0, x0 + 10, f + 10, z0 + 8, "south", nama="kuil_tudi")


def alun_hunian(k, x0, z0, y=LY):
    """The neighbourhood square of Hunian Barat: a paved court with a public well, the Earth God shrine on the north
    side, an old willow with a stone ring bench, four morning vegetable stalls, a notice board, children's play spots;
    the district zone anchor."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 3, z0 + 45):
            d = max(abs(x - (x0 + 24)), abs(z - (z0 + 26)))
            k.put(x, y, z, "kerb_chisel" if d % 7 == 0 and (x + z) % 2 == 0 else "paving" if (x + z) % 9 else "paving_alt")
    kuil_tudi(k, x0 + 19, z0 + 4)
    sumur_umum(k, x0 + 24, z0 + 27)
    # old willow in a stone ring
    wx, wz = x0 + 10, z0 + 34
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                k.slab(wx + dx, y + 1, wz + dz, "kerb_slab", "top")
            else:
                k.put(wx + dx, y, wz + dz, "ground", {"snowy": "false"})
    K.tree(k, wx, y + 1, wz, "willow")
    for (bx, bz, f) in ((wx - 3, wz, "west"), (wx + 3, wz, "east"), (wx, wz + 3, "south")):
        k.titik("obrol", bx, y + 1, bz, f)
    # morning vegetable stalls
    goods = (("minecraft:melon", "minecraft:pumpkin"), ("minecraft:hay_block",), ("minecraft:flower_pot",),
             ("minecraft:decorated_pot",))
    for i, (sx, sz, f) in enumerate(((x0 + 33, z0 + 18, "west"), (x0 + 33, z0 + 26, "west"), (x0 + 33, z0 + 34, "west"),
                                     (x0 + 6, z0 + 20, "east"))):
        px, pz = K.stall(k, sx, sz, f, w=3, goods=goods[i])
        k.titik("lapak", px, y + 1, pz, f, {"pagi": 1})
    K.notice_board(k, x0 + 15, z0 + 42, "north", w=5)
    for x, z in ((x0 + 18, z0 + 36), (x0 + 30, z0 + 38), (x0 + 22, z0 + 18)):
        k.titik("main", x, y + 1, z, "south")
    for z in (z0 + 22, z0 + 32):
        K.stone_lantern(k, x0 + 18, y + 1, z)
        K.stone_lantern(k, x0 + 30, y + 1, z)
    K.bench(k, x0 + 20, z0 + 40, "north", n=4)
    k.titik("kursi", x0 + 21, y + 1, z0 + 40, "north")
    k.titik("kerja:sapu", x0 + 26, y + 1, z0 + 20, "south")
    k.jalur([(x0 + 24, z0 + 45), (x0 + 24, z0 + 33), (x0 + 24, z0 + 16), (x0 + 3, z0 + 26), (x0 + 45, z0 + 26)],
            jalur="alun_hunian")
    k.jangkar(x0 + 24.5, y + 1, z0 + 23.5, "kotabesar/zona", distrik="hunian_barat", protect=100)
    k.jangkar(x0 + 12.5, y + 1, z0 + 44.5, "kota/penjaga", yaw=0.0)


def rumah_makan_kecil(k, x0, z0, y=LY):
    """Rumah Makan Kecil (38.B.3): the neighbourhood noodle house - a hip-roofed shop with an open kitchen (a lit stove
    under a brick hood, smoker, furnace, water and barrels along the back wall), a serving counter, six tables inside,
    four more under a leafy pergola in the front yard with paper lamps, and a row of water jars and potted herbs."""
    a = K.aula(k, x0 + 5, z0 + 4, x0 + 42, z0 + 24, h=5, podium_h=1, teras=1, roof="hip", tiers=2, door_sides=("south",),
               door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    kitchen_row(k, ix0 + 1, ix0 + 12, iz0, f, "south")
    k.put(ix0 + 3, f + 1, iz0 + 1, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                         "waterlogged": "false"})
    for dy in range(3, 5):
        for dx in (-1, 0, 1):
            k.put(ix0 + 3 + dx, f + dy, iz0 + 1, "minecraft:bricks")
    K.counter(k, ix0 + 1, ix0 + 12, iz0 + 3, f, "south")
    for x in range(ix0 + 2, ix0 + 12, 3):
        k.put(x, f + 2, iz0 + 3, "minecraft:flower_pot")
    for i, (tx, tz) in enumerate(((ix0 + 16, iz0 + 3), (ix0 + 22, iz0 + 3), (ix0 + 28, iz0 + 3), (ix0 + 16, iz0 + 9),
                                  (ix0 + 22, iz0 + 9), (ix0 + 28, iz0 + 9))):
        if tx < ix1:
            K.furnish_dining(k, tx, tz, f, n=4)
    for x in range(ix0 + 3, ix1 - 1, 6):
        K.lit(k, x, f + 5, (iz0 + iz1) // 2, chain=1, role="lamp")
    k.jangkar(ix0 + 6.5, f + 1, iz0 + 4.5, "kotabesar/layanan", yaw=0.0, layanan="rumah_makan", peran="KOKI")
    k.titik("kerja:teko", ix0 + 10, f + 1, iz0 + 2, "south", {"peran": "PELAYAN"})
    # pergola with four tables in the front yard
    px0, pz0, px1, pz1 = x0 + 6, z0 + 29, x0 + 41, z0 + 41
    for x in range(px0, px1 + 1, 5):
        for z in (pz0, pz1):
            for dy in range(1, 5):
                k.put(x, y + dy, z, "log", {"axis": "y"})
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if (x - px0) % 2 == 0:
                k.put(x, y + 5, z, "wood_fence" if z not in (pz0, pz1) else "beam", {"axis": "z"} if z in (pz0, pz1)
                      else None)
            if (x * 3 + z) % 4 == 0 and z not in (pz0, pz1):
                k.put(x, y + 5, z, "minecraft:oak_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
    for i, tx in enumerate(range(px0 + 5, px1 - 2, 9)):
        K.furnish_dining(k, tx, (pz0 + pz1) // 2, y - 0, n=4)
    for x in range(px0 + 2, px1 - 1, 8):
        K.lit(k, x, y + 5, (pz0 + pz1) // 2, chain=1, role="lamp")
    for x in range(x0 + 6, x0 + 42, 4):
        if x % 8 == 2:
            k.put(x, y + 1, z0 + 26, "minecraft:water_cauldron", {"level": "3"})
        else:
            K.pot(k, x, y + 1, z0 + 26)
    k.titik("obrol", x0 + 24, y + 1, z0 + 43, "north")
    K.dandan(k, (ix0, iz0, ix1, iz1), f, "makan", simpan=[(ix0, iz0, ix0 + 13, iz0 + 5)], titik_maks=2)
    k.kotak("bangunan", x0 + 5, y, z0 + 4, x0 + 42, f + 14, z0 + 24, "south", nama="rumah_makan_kecil")


# ===================================================================================================== gerbang
def build_gerbang():
    """Distrik Gerbang (38.B.3, south centre): the Imperial Avenue from the Great South Gate to the centre with arcades,
    the gate plaza (customs, guard house, notice board, arrival), two inns, the Lotus restaurant, the Grand Tea House and
    the two halves of the Lantern Street."""
    k = kanvas("gerbang", h=56)
    skip = {(1, 0, "s"), (1, 1, "n"), (1, 1, "s"), (1, 2, "n")}
    K.street_grid(k, skip=skip)
    K.imperial_avenue(k, 64, 79, 0, 143)
    for j in (0, 1):
        x0, z0 = o(1, j)
        avenue_lot_decor(k, x0, z0 + (0 if j == 0 else 3), z0 + 47)
    x0, z0 = o(1, 1)
    K.paifang(k, x0 + 23, z0 + 4, width=17, height=10)
    k.jangkar(x0 + 24.5, LY + 1, z0 + 24.5, "kotabesar/zona", distrik="gerbang", protect=100)
    alun_gerbang(k, *o(1, 2))
    penginapan_tingkat(k, *o(0, 0))
    penginapan_bulan_perak(k, *o(2, 0))
    rumah_makan_teratai(k, *o(0, 1))
    kedai_teh_agung(k, *o(2, 1))
    jalan_lentera(k, *o(0, 2), sisi=0)
    jalan_lentera(k, *o(2, 2), sisi=1)
    names = {(0, 0): "penginapan_awan_singgah", (1, 0): "jalan_raya_gerbang_utara", (2, 0): "penginapan_bulan_perak",
             (0, 1): "rumah_makan_teratai", (1, 1): "jalan_raya_gerbang", (2, 1): "kedai_teh_agung",
             (0, 2): "jalan_lentera_barat", (1, 2): "alun_gerbang", (2, 2): "jalan_lentera_timur"}
    simpan_kanvas(k, "gerbang", names, utuh=("alun_gerbang",))


def alun_gerbang(k, x0, z0, y=LY):
    """The gate plaza behind the Great South Gate (38.B.4.1): the avenue widens into a paved court with a customs house
    (scales, crates, the clerk's desk), the guard house (bunks, weapon rack, a drum), hitching posts and a horse trough,
    the notice board, arrival points and guards' posts."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 20, z0 + 48):
            if not (16 <= x - x0 <= 31):
                k.put(x, y, z, "paving" if (x * 5 + z) % 9 else "kerb")
    # customs house (west of the avenue)
    a = K.aula(k, x0 + 3, z0 + 4, x0 + 14, z0 + 18, h=5, podium_h=1, teras=1, roof="hip", door_sides=("east",), door_w=3,
               tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.counter_z(k, ix1 - 2, iz0 + 1, iz1 - 1, f, "east")
    k.put(ix0, f + 1, iz0, "minecraft:barrel", {"facing": "up", "open": "false"})
    K.crates(k, ix0, iz1 - 2, ix0 + 1, iz1, f + 1, h=2)
    K.table(k, ix0 + 1, f + 1, (iz0 + iz1) // 2, "minecraft:white_carpet")
    k.titik("kerja:timbangan", ix1 - 3, f + 1, (iz0 + iz1) // 2, "east", {"peran": "MENTERI"})
    k.jangkar(ix1 - 3.5, f + 1, (iz0 + iz1) / 2.0 + 0.5, "kotabesar/layanan", yaw=270.0, layanan="cukai",
              peran="MENTERI")
    # guard house (east of the avenue)
    g = K.aula(k, x0 + 33, z0 + 4, x0 + 44, z0 + 18, h=5, podium_h=1, teras=1, roof="hip", door_sides=("west",), door_w=3,
               tile=True)
    gx0, gz0, gx1, gz1 = g["inner"]
    gf = g["f"]
    for z in (gz0 + 1, gz0 + 4, gz0 + 7):
        K.bed(k, gx1, gf + 1, z, "east")
        k.titik("kasur", gx1, gf + 1, z, "west", {"peran": "PENJAGA"})
    weapon_rack(k, gx0, gz1, gf, "north", n=4)
    K.drum(k, gx0 + 1, gf + 1, gz0, "south")
    k.jangkar(gx0 - 1.5, gf + 1, (gz0 + gz1) / 2.0 + 0.5, "kota/penjaga", yaw=90.0)
    # hitching posts and a horse trough beside the avenue
    for z in range(z0 + 24, z0 + 42, 4):
        k.put(x0 + 12, y + 1, z, "wood_fence")
        k.put(x0 + 12, y + 2, z, "wood_fence")
        k.put(x0 + 35, y + 1, z, "wood_fence")
        k.put(x0 + 35, y + 2, z, "wood_fence")
    for z in range(z0 + 26, z0 + 32):
        k.put(x0 + 8, y, z, "kerb")
        k.put(x0 + 8, y + 1, z, "minecraft:water_cauldron", {"level": "3"})
    K.notice_board(k, x0 + 40, z0 + 28, "west", w=5)
    k.jangkar(x0 + 38.5, y + 1, z0 + 30.5, "kota/papan", yaw=90.0)
    for x in (x0 + 18, x0 + 29):
        K.huabiao(k, x, y, z0 + 44, height=7)
    for x in range(x0 + 18, x0 + 30, 2):
        k.titik("tiba", x, y + 1, z0 + 46, "north")
    for x, z in ((x0 + 15, z0 + 21), (x0 + 32, z0 + 21), (x0 + 15, z0 + 40), (x0 + 32, z0 + 40)):
        k.titik("jaga", x, y + 1, z, "south")
    for z in range(z0 + 24, z0 + 46, 7):
        K.stone_lantern(k, x0 + 5, y + 1, z)
        K.stone_lantern(k, x0 + 42, y + 1, z)
    k.titik("obrol", x0 + 10, y + 1, z0 + 36, "east")
    k.titik("obrol", x0 + 37, y + 1, z0 + 36, "west")
    k.jalur([(x0 + 24, z0 + 47), (x0 + 24, z0 + 30), (x0 + 24, z0 + 0)], jalur="gerbang")


def _inn_floor(k, ix0, iz0, ix1, iz1, fy, first_room, stair_east):
    """One guest floor of the Awan Singgah inn: a corridor along the middle, three rooms each side (bed, chest, table,
    lamp, rug) between two stair landings at the west and east ends."""
    mid = (iz0 + iz1) // 2
    rx_a, rx_b = ix0 + 6, ix1 - 6
    width = rx_b - rx_a + 1
    edges = [rx_a - 1] + [rx_a + width * m // 3 for m in (1, 2)] + [rx_b + 1]
    n = first_room
    for e in edges:
        for z in list(range(iz0, mid - 1)) + list(range(mid + 2, iz1 + 1)):
            for dy in range(1, 4):
                k.put(e, fy + dy, z, "wood")
    for side, (rz0, rz1, front, face) in enumerate(((iz0, mid - 2, mid - 2, "south"), (mid + 2, iz1, mid + 2, "north"))):
        for r in range(3):
            rx0, rx1 = edges[r] + 1, edges[r + 1] - 1
            for x in range(rx0, rx1 + 1):
                for dy in range(1, 4):
                    k.put(x, fy + dy, front, "plaster" if (x - rx0) % 5 else "beam", {"axis": "y"} if (x - rx0) % 5 == 0
                          else None)
            dx = (rx0 + rx1) // 2
            K.door(k, dx, fy + 1, front, face, "left")
            back = rz0 if face == "south" else rz1
            bx = rx0 + 1
            K.bed(k, bx, fy + 1, back + (1 if face == "south" else -1), "north" if face == "south" else "south")
            k.titik("kasur", bx, fy + 1, back + (1 if face == "south" else -1), face, {"kamar": n})
            K.chest(k, rx1, fy + 1, back, None, face)
            K.table(k, rx1 - 1, fy + 1, (rz0 + rz1) // 2)
            K.chair(k, rx1 - 2, fy + 1, (rz0 + rz1) // 2, "east")
            K.rug(k, rx0 + 3, rz0 + 1, rx1 - 3, rz1 - 1, fy + 1)
            K.lit(k, dx, fy + 4, (rz0 + rz1) // 2, chain=1)
            n += 1
    K.lit(k, (ix0 + ix1) // 2, fy + 4, mid, chain=1, role="lamp")
    return n


def penginapan_tingkat(k, x0, z0, y=LY):
    """Penginapan Awan Singgah (38.B.3): a three-storey inn - the ground floor common room (reception counter, four
    dining tables, a kitchen wall, a tea corner), twelve guest rooms on two upper floors along central corridors, the
    stair wells, a south gallery with lanterns, a two-tier hip roof; the stable yard and well behind."""
    hx0, hz0, hx1, hz1 = x0 + 4, z0 + 6, x0 + 43, z0 + 30
    f = K.podium(k, hx0 - 1, hz0 - 1, hx1 + 1, hz1 + 1, y, 1, steps=("south",), step_w=5)
    ix0, iz0, ix1, iz1 = K.hall(k, hx0, hz0, hx1, hz1, f, 5, door_sides=("south",), door_w=3, windows=4)
    mid = (iz0 + iz1) // 2
    # ground floor
    K.counter(k, ix1 - 8, ix1 - 1, iz1 - 4, f, "south")
    k.jangkar(ix1 - 4.5, f + 1, iz1 - 2.5, "kotabesar/layanan", yaw=180.0, layanan="penginapan",
              peran="PEMILIK_PENGINAPAN", nama="awan_singgah", tokoh="bibi_hong")
    k.titik("kerja:timbangan", ix1 - 4, f + 1, iz1 - 5, "south", {"peran": "PEMILIK_PENGINAPAN"})
    furnish_kitchen = K.furnish_kitchen
    furnish_kitchen(k, ix0 + 8, iz0, ix1 - 10, f, iz0, "south")
    for tx in (ix0 + 10, ix0 + 16, ix0 + 22):
        K.furnish_dining(k, tx, mid + 1, f, n=4)
    K.furnish_dining(k, ix0 + 28, mid + 1, f, n=3)
    for z in range(iz0 + 2, iz1 - 6, 3):
        K.low_table(k, ix1 - 2, f + 1, z)
        k.put(ix1 - 1, f + 1, z, "minecraft:red_carpet")
        k.titik("kursi_lantai", ix1 - 1, f + 1, z, "west")
    # D-KS: luggage, a tea corner and vases in the common room (the stair foot and the counter stay clear)
    K.dandan(k, (ix0 + 3, iz0 + 2, ix1 - 3, iz1), f, "makan", titik_maks=2,
             simpan=[(ix0, mid - 3, ix0 + 4, mid + 6), (ix1 - 9, iz1 - 6, ix1, iz1)])
    # stairs: ground -> 2 on the west landing, 2 -> 3 on the east landing, both rising north
    K.stair_flight(k, ix0 + 1, f + 1, mid + 3, "north", 5, width=2)
    f2 = f + 5
    K.upper_floor(k, ix0, iz0, ix1, iz1, f2, hole=(ix0 + 1, mid - 1, ix0 + 2, mid + 3))
    K.hall(k, hx0, hz0, hx1, hz1, f2, 4, door_sides=(), windows=4, ceiling=False, lamps=False)
    n = _inn_floor(k, ix0, iz0, ix1, iz1, f2, 1, False)
    K.stair_flight(k, ix1 - 2, f2 + 1, mid + 3, "north", 4, width=2)
    f3 = f2 + 4
    K.upper_floor(k, ix0, iz0, ix1, iz1, f3, hole=(ix1 - 2, mid, ix1 - 1, mid + 3))
    K.hall(k, hx0, hz0, hx1, hz1, f3, 4, door_sides=(), windows=4, ceiling=True, lamps=False)
    _inn_floor(k, ix0, iz0, ix1, iz1, f3, n, True)
    for x in range(ix0 + 1, ix0 + 3):
        k.put(x, f2 + 1, mid - 2, "rail")
    # south gallery on the second floor, reached through the middle south room
    for x in range(hx0 - 1, hx1 + 2):
        k.slab(x, f2, hz1 + 1, "wood_slab", "top")
        k.slab(x, f2, hz1 + 2, "wood_slab", "top")
        k.put(x, f2 + 1, hz1 + 2, "rail")
    K.door(k, (hx0 + hx1) // 2, f2 + 1, hz1, "south", "left")
    for x in range(hx0 + 2, hx1 - 1, 6):
        k.put(x, f2 - 1, hz1 + 2, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    top = f3 + 4
    K.brackets(k, hx0, hz0, hx1, hz1, top)
    K.hip(k, hx0, top + 1, hz0, hx1, hz1, overhang=2, tiers=2, tier_gap=2, tier_band=2, band_role="plaster",
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    # sign board over the door
    for x in range((hx0 + hx1) // 2 - 3, (hx0 + hx1) // 2 + 4):
        k.put(x, f + 5, hz1 + 1, "gold" if abs(x - (hx0 + hx1) // 2) <= 1 else "red")
    # stable yard behind (north strip)
    for x in range(x0 + 4, x0 + 44):
        k.put(x, y, z0 + 4, "minecraft:coarse_dirt")
    for x in range(x0 + 6, x0 + 30, 4):
        k.put(x, y + 1, z0 + 3, "wood_fence")
        k.put(x + 1, y + 1, z0 + 3, "minecraft:hay_block", {"axis": "y"})
    k.put(x0 + 38, y + 1, z0 + 3, "minecraft:water_cauldron", {"level": "3"})
    k.titik("kerja:sapu", x0 + 24, y + 1, z0 + 4, "south", {"peran": "PELAYAN"})
    k.titik("pintu", (hx0 + hx1) // 2, f + 1, hz1 + 1, "south")
    k.kotak("bangunan", hx0, y, hz0, hx1, top + 10, hz1, "south", nama="penginapan_awan_singgah")


def penginapan_bulan_perak(k, x0, z0, y=LY):
    """Penginapan Bulan Perak (38.B.3): the better inn - a walled courtyard compound entered through its street hall
    (reception counter, a quiet dining room with a grey-and-white rug), a moon-gate wall into the inner garden, east and
    west guest wings of four rooms each with their own doors onto the garden, a lotus pond, a cherry tree and bamboo,
    and the private pavilion suite at the back."""
    K.yard_wall(k, x0 + 3, z0 + 3, x0 + 44, z0 + 44, h=4, gate="south", gate_w=3)
    K.paving(k, x0 + 4, z0 + 4, x0 + 43, z0 + 43, y)
    main = K.aula(k, x0 + 8, z0 + 29, x0 + 39, z0 + 41, h=6, podium_h=2, teras=1, roof="hip", tiers=2,
                  door_sides=("south", "north"), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = main["inner"]
    f = main["f"]
    K.counter(k, ix0 + 2, ix0 + 9, iz1 - 3, f, "south")
    k.jangkar(ix0 + 5.5, f + 1, iz1 - 4.5, "kotabesar/layanan", yaw=180.0, layanan="penginapan",
              peran="PEMILIK_PENGINAPAN", nama="bulan_perak")
    k.titik("kerja:timbangan", ix0 + 5, f + 1, iz1 - 4, "south", {"peran": "PEMILIK_PENGINAPAN"})
    for tx in (ix1 - 9, ix1 - 4):
        K.furnish_dining(k, tx, (iz0 + iz1) // 2, f, n=4)
    K.rug(k, ix1 - 11, iz0 + 1, ix1 - 1, iz1 - 1, f + 1, role="minecraft:light_gray_carpet", border="minecraft:white_carpet")
    for x in range(ix0 + 1, ix1, 5):
        K.lit(k, x, f + 6, iz0 + 1, chain=1)
    n = 1
    for side, wx0 in ((0, x0 + 5), (1, x0 + 33)):
        wing = K.aula(k, wx0, z0 + 5, wx0 + 9, z0 + 26, h=4, podium_h=1, teras=1, roof="gable", along="z",
                      door_sides=(), door_w=1, tile=True)
        jx0, jz0, jx1, jz1 = wing["inner"]
        wf = wing["f"]
        for r in range(4):
            rz0 = jz0 + r * 5
            if r > 0:
                for x in range(jx0, jx1 + 1):
                    for dy in range(1, 4):
                        k.put(x, wf + dy, rz0 - 1, "wood")
            bx = jx0 if side == 0 else jx1
            K.bed(k, bx, wf + 1, rz0 + 1, "north")
            k.titik("kasur", bx, wf + 1, rz0 + 1, "south", {"kamar": n})
            K.chest(k, bx, wf + 1, rz0, None, "east" if side == 0 else "west")
            K.pot(k, jx1 if side == 0 else jx0, wf + 1, rz0)
            K.table(k, jx1 - 1 if side == 0 else jx0 + 1, wf + 1, rz0 + 2)
            door_x = jx1 + 1 if side == 0 else jx0 - 1
            K.door(k, door_x, wf + 1, rz0 + 1, "east" if side == 0 else "west", "left")
            K.lit(k, (jx0 + jx1) // 2, wf + 4, rz0 + 1, chain=1)
            n += 1
    # moon gate wall between the street hall and the garden
    mz = z0 + 27
    for x in range(x0 + 15, x0 + 33):
        for dy in range(1, 7):
            dd = math.hypot(x - (x0 + 23.5), dy - 3.0)
            k.put(x, y + dy, mz, "minecraft:air" if dd <= 2.6 else ("plaster" if dy < 6 else "kerb"))
        k.slab(x, y + 7, mz, "tile_slab")
    for x in range(x0 + 16, x0 + 32):
        for z in range(z0 + 13, z0 + 27):
            if (x + z) % 4:
                k.put(x, y, z, "ground", {"snowy": "false"})
            else:
                k.put(x, y, z, "minecraft:moss_block")
    K.pond(k, x0 + 24, z0 + 20, 4, 3, y, lilies=4)
    k.path([(x0 + 23, z0 + 26), (x0 + 19, z0 + 20), (x0 + 23, z0 + 14)], y, width=1, role="minecraft:gravel",
           alt="paving", alt_chance=0.3)
    pav = K.aula(k, x0 + 18, z0 + 4, x0 + 29, z0 + 11, h=4, podium_h=1, teras=1, roof="hip", door_sides=("south",),
                 door_w=1, tile=True)
    px0, pz0, px1, pz1 = pav["inner"]
    pf = pav["f"]
    K.bed(k, px0, pf + 1, pz0 + 1, "north", role="minecraft:white_bed")
    k.titik("kasur", px0, pf + 1, pz0 + 1, "south", {"kamar": n, "vip": 1})
    K.table(k, px1, pf + 1, pz0, "")
    K.candle(k, px1, pf + 2, pz0, 2, "white")
    K.chair(k, px1 - 1, pf + 1, pz0, "east")
    K.rug(k, px0 + 1, pz0, px1 - 2, pz1, pf + 1, role="minecraft:white_carpet", border="minecraft:light_gray_carpet")
    for x, z in ((x0 + 17, z0 + 14), (x0 + 30, z0 + 14), (x0 + 17, z0 + 25), (x0 + 30, z0 + 25)):
        K.stone_lantern(k, x, y + 1, z)
    K.tree(k, x0 + 17, y + 1, z0 + 20, "cherry")
    K.bamboo_clump(k, x0 + 30, z0 + 19, y + 1, 6)
    k.titik("obrol", x0 + 20, y + 1, z0 + 24, "east")
    k.titik("meditasi", x0 + 28, y + 1, z0 + 15, "west")
    k.titik("pintu", x0 + 23, y + 1, z0 + 44, "south")
    k.kotak("bangunan", x0 + 8, y, z0 + 29, x0 + 39, f + 20, z0 + 41, "south", nama="penginapan_bulan_perak")


def rumah_makan_teratai(k, x0, z0, y=LY):
    """Rumah Makan Teratai (38.B.3): the famous Lotus restaurant - a tall two-storey hall with the open kitchen (smokers,
    two lit stoves under brick hoods) and ten tables below, an upper dining gallery round an open well with six tables,
    a balcony over the lotus pond, and a zigzag bridge across the pond to the street."""
    a = K.aula(k, x0 + 4, z0 + 4, x0 + 43, z0 + 26, h=11, podium_h=1, teras=2, roof="hip", tiers=2,
               door_sides=("south",), door_w=3, tile=True, top="spire")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    kitchen_row(k, ix0 + 1, ix1 - 1, iz0, f, "south")
    for x in (ix0 + 4, ix1 - 4):
        k.put(x, f + 1, iz0 + 1, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                       "waterlogged": "false"})
        for dy in range(3, 6):
            k.put(x, f + dy, iz0 + 1, "minecraft:bricks")
    K.counter(k, ix0 + 1, ix1 - 1, iz0 + 3, f, "south")
    n = 0
    for z in (iz0 + 7, iz0 + 11):
        for x in range(ix0 + 4, ix1 - 4, 6):
            if n < 10:
                K.furnish_dining(k, x, z, f, n=4)
                n += 1
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz0 + 4.5, "kotabesar/layanan", yaw=0.0, layanan="rumah_makan",
              peran="KOKI", nama="teratai")
    k.titik("kerja:teko", ix1 - 6, f + 1, iz0 + 4, "south", {"peran": "PELAYAN"})
    # upper gallery: a ring floor round an open well over the dining room
    f2 = f + 6
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            ring = x < ix0 + 5 or x > ix1 - 5 or z < iz0 + 4 or z > iz1 - 2
            if ring:
                k.put(x, f2, z, "wood_alt")
            elif x in (ix0 + 5, ix1 - 5) or z in (iz0 + 4, iz1 - 2):
                k.put(x, f2 + 1, z, "rail")
    for i in range(6):
        k.stairs(ix1 - 1, f + 1 + i, iz1 - 1 - i, "wood_stairs", "north")
        k.stairs(ix1 - 2, f + 1 + i, iz1 - 1 - i, "wood_stairs", "north")
    for z in range(iz1 - 5, iz1):
        for x in (ix1 - 2, ix1 - 1):
            k.put(x, f2, z, "minecraft:air")
    for (tx, tz) in ((ix0 + 2, iz0 + 6), (ix0 + 2, iz0 + 11), (ix1 - 3, iz0 + 6), (ix1 - 3, iz0 + 9),
                     (ix0 + 12, iz0 + 1), (ix1 - 12, iz0 + 1)):
        K.furnish_dining(k, tx, tz, f2, n=3)
    for x in range(ix0 + 2, ix1 - 1):
        if (x - ix0) % 4 in (1, 2) and abs(x - (ix0 + ix1) // 2) > 2:
            K.lattice(k, x, f2 + 2, iz1 + 1, "south")
            K.lattice(k, x, f2 + 3, iz1 + 1, "south")
    for x in range(ix0 + 6, ix1 - 5, 5):
        K.lit(k, x, f + 11, (iz0 + iz1) // 2, chain=3, role="lamp")
    # balcony over the pond, entered by a door in the middle of the upper south wall
    K.door(k, (ix0 + ix1) // 2, f2 + 1, iz1 + 1, "south", "left")
    for x in range(x0 + 10, x0 + 38):
        for z in (z0 + 27, z0 + 28):
            k.slab(x, f2, z, "wood_slab", "top")
        k.put(x, f2 + 1, z0 + 29, "rail")
    for x in (x0 + 10, x0 + 37):
        for dy in range(1, f2 - y):
            k.put(x, y + dy, z0 + 28, "red")
    for x in range(x0 + 12, x0 + 37, 6):
        K.chair(k, x, f2 + 1, z0 + 28, "south")
        k.titik("kursi", x, f2 + 1, z0 + 28, "south")
    # lotus pond and zigzag bridge
    for x in range(x0 + 5, x0 + 43):
        for z in range(z0 + 31, z0 + 44):
            edge = x in (x0 + 5, x0 + 42) or z in (z0 + 31, z0 + 43)
            if edge:
                k.put(x, y, z, "kerb")
                k.slab(x, y + 1, z, "kerb_slab")
            else:
                k.put(x, y - 1, z, "minecraft:clay")
                k.put(x, y, z, "minecraft:water", {"level": "0"})
                if (x * 7 + z * 5) % 6 == 0:
                    k.put(x, y + 1, z, "minecraft:lily_pad")
    zig = [(x0 + 22, z0 + 31), (x0 + 22, z0 + 35), (x0 + 26, z0 + 35), (x0 + 26, z0 + 39), (x0 + 22, z0 + 39),
           (x0 + 22, z0 + 43)]
    for (ax, az), (bx, bz) in zip(zig, zig[1:]):
        for t in range(max(abs(bx - ax), abs(bz - az)) + 1):
            x = ax + (t if bx > ax else -t if bx < ax else 0)
            z = az + (t if bz > az else -t if bz < az else 0)
            for w in (0, 1):
                k.put(x + w, y, z, "wood")
                k.put(x + w, y + 1, z, "minecraft:air")
    for (x, z) in zig[1:-1]:
        K.post(k, x - 1, y + 1, z - 1, height=2)
    for x, z in ((x0 + 10, z0 + 37), (x0 + 34, z0 + 36)):
        k.put(x, y + 1, z, "minecraft:mossy_cobblestone")
    k.titik("kerja:pancing", x0 + 8, y + 1, z0 + 30, "south")
    k.titik("obrol", x0 + 28, y + 1, z0 + 37, "west")
    k.kotak("bangunan", x0 + 4, y, z0 + 4, x0 + 43, f + 26, z0 + 26, "south", nama="rumah_makan_teratai")


def kedai_teh_agung(k, x0, z0, y=LY):
    """Kedai Teh Agung (38.B.3, module K's tea house anchors): the Grand Tea House - the storyteller's dais with his
    table and clapper, low tea tables with floor cushions, a tea counter of kettles, barrels and shelves, galleries for
    the regulars on two sides, bamboo and stone lanterns in the forecourt; anchors kota/teh and two kota/kursi_pengembara."""
    a = K.aula(k, x0 + 4, z0 + 4, x0 + 43, z0 + 34, h=7, podium_h=2, teras=2, roof="hip", tiers=2,
               door_sides=("west",), door_w=3, tile=True, top="spire")
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    # storyteller's dais on the east wall
    for x in range(ix1 - 4, ix1 + 1):
        for z in range(iz0 + 8, iz1 - 7):
            k.put(x, f + 1, z, "wood")
    for z in range(iz0 + 8, iz1 - 7):
        k.stairs(ix1 - 5, f + 1, z, "wood_stairs", "east")
    tz = (iz0 + iz1) // 2
    K.table(k, ix1 - 2, f + 2, tz, "minecraft:red_carpet")
    K.chair(k, ix1 - 1, f + 2, tz, "west")
    k.titik("kursi", ix1 - 1, f + 2, tz, "west", {"peran": "TETUA_DESA"})
    k.put(ix1 - 2, f + 2, tz - 1, "minecraft:dark_oak_button", {"face": "floor", "facing": "west", "powered": "false"})
    K.wall_banner(k, ix1, f + 5, tz, "west")
    k.kotak("panggung", ix1 - 4, f + 2, iz0 + 8, ix1, f + 6, iz1 - 8, "west", acara="cerita")
    # tea tables with floor cushions
    for x in range(ix0 + 3, ix1 - 7, 5):
        for z in range(iz0 + 3, iz1 - 1, 5):
            K.low_table(k, x, f + 1, z)
            if (x + z) % 2:
                k.put(x, f + 2, z, "minecraft:flower_pot")
            else:
                k.put(x, f + 2, z, "minecraft:decorated_pot", {"facing": "south", "cracked": "false",
                                                               "waterlogged": "false"})
            for (dx, face) in ((-1, "east"), (1, "west")):
                k.put(x + dx, f + 1, z, "minecraft:red_carpet" if (x + z) % 3 else "minecraft:yellow_carpet")
                k.titik("kursi_lantai", x + dx, f + 1, z, face)
    # tea counter (north wall)
    K.counter(k, ix0 + 2, ix0 + 12, iz0 + 1, f, "south")
    for x in range(ix0 + 2, ix0 + 13):
        if x % 3 == 0:
            k.put(x, f + 1, iz0, "minecraft:water_cauldron", {"level": "3"})
        else:
            K.barrel(k, x, f + 1, iz0, "south")
        K.shelf(k, x, f + 3, iz0, "south")
    k.jangkar(ix0 + 7.5, f + 1, iz0 + 2.5, "kota/teh", yaw=0.0)
    k.titik("kerja:teko", ix0 + 6, f + 1, iz0 + 2, "south", {"peran": "PEMILIK_TEH"})
    k.jangkar(ix0 + 4.5, f + 1, iz1 - 2.5, "kota/kursi_pengembara", yaw=90.0)
    k.jangkar(ix0 + 14.5, f + 1, iz1 - 2.5, "kota/kursi_pengembara", yaw=270.0)
    # galleries along the north (east of the counter) and south walls at +4
    for x in range(ix0, ix1 - 5):
        for z in (iz1 - 2, iz1 - 1, iz1):
            k.put(x, f + 4, z, "wood_alt")
        k.put(x, f + 5, iz1 - 3, "rail")
        if x > ix0 + 13:
            for z in (iz0, iz0 + 1, iz0 + 2):
                k.put(x, f + 4, z, "wood_alt")
            k.put(x, f + 5, iz0 + 3, "rail")
    for i in range(4):
        k.stairs(ix1 - 6 - i, f + 1 + i, iz1, "wood_stairs", "west")
    for x in range(ix0 + 4, ix1 - 9, 5):
        K.chair(k, x, f + 5, iz1 - 1, "north")
        k.titik("kursi", x, f + 5, iz1 - 1, "north")
    for x in range(ix0 + 3, ix1 - 6, 6):
        K.lit(k, x, f + 7, tz, chain=2, role="lamp")
    # forecourt (west, the door side): bamboo and stone lanterns, pots along the south street
    for i, z in enumerate((z0 + 6, z0 + 12, z0 + 26, z0 + 32)):
        if i % 2:
            K.stone_lantern(k, x0 + 2, y + 1, z)
        else:
            K.bamboo_clump(k, x0 + 2, z, y + 1, 5)
    k.titik("obrol", x0 + 3, y + 1, z0 + 38, "north")
    k.titik("obrol", x0 + 20, y + 1, z0 + 40, "north")
    for x in range(x0 + 6, x0 + 42, 5):
        K.pot(k, x, y + 1, z0 + 38)
    K.bench(k, x0 + 26, z0 + 41, "north", n=4)
    k.kotak("bangunan", x0 + 4, y, z0 + 4, x0 + 43, f + 26, z0 + 34, "west", nama="kedai_teh_agung")


def jalan_lentera(k, x0, z0, y=LY, sisi=0):
    """Jalan Lentera (38.B.3, 38.B.6.1): the Lantern Street - an east-west lane with tall lantern posts every 6 blocks
    on both sides (festival lantern points jalan_lentera), shophouses on the north side, a willow-lined stream with an
    arched stone bridge on the south side, a painted gate where the lane meets the avenue, benches and a lantern
    seller's stall."""
    lz = z0 + 25
    for x in range(x0 + 3, x0 + 45):
        for z in range(lz - 2, lz + 2):
            k.put(x, y, z, "kerb_chisel" if (x % 6 == 0 and z in (lz - 1, lz)) else "paving")
    for x in range(x0 + 6, x0 + 43, 6):
        for z in (lz - 3, lz + 2):
            for dy in range(1, 7):
                k.put(x, y + dy, z, "log", {"axis": "y"})
            arm = 1 if z > lz else -1
            k.put(x, y + 7, z, "beam", {"axis": "z"})
            k.put(x, y + 7, z - arm, "wood_fence")
            k.put(x, y + 6, z - arm, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
            k.put(x, y + 5, z - arm, "lamp", {"axis": "y"})
            k.titik("jalan_lentera", x, y + 5, z - arm, "south" if arm > 0 else "north")
    # festival strings between opposite posts (chains; the paper lanterns on them are lit by the client)
    for x in range(x0 + 6, x0 + 43, 12):
        for z in range(lz - 2, lz + 2):
            k.put(x, y + 7, z, "minecraft:iron_chain", {"axis": "z", "waterlogged": "false"})
    goods = (("minecraft:flower_pot", "minecraft:decorated_pot"), ("minecraft:melon", "minecraft:flower_pot"))
    K.tempel(k, rumah_toko(tl._seed("gerbang/lentera_%d_a" % sisi), 20, 19, goods=goods[0]), x0 + 3, z0 + 3, 0)
    K.tempel(k, rumah_toko(tl._seed("gerbang/lentera_%d_b" % sisi), 20, 19, goods=goods[1]), x0 + 24, z0 + 3, 0)
    # stream, willows and an arched bridge on the south side
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 33, z0 + 37):
            if z in (z0 + 33, z0 + 36):
                k.put(x, y, z, "kerb")
                if x % 2:
                    k.slab(x, y + 1, z, "kerb_slab")
            else:
                k.put(x, y - 1, z, "minecraft:gravel")
                k.put(x, y, z, "minecraft:water", {"level": "0"})
    bx = x0 + 24
    for z in range(z0 + 32, z0 + 38):
        rise = 1 if z in (z0 + 34, z0 + 35) else 0
        for w in range(-1, 2):
            if rise:
                k.put(bx + w, y + 1, z, "kerb")
            else:
                k.stairs(bx + w, y + 1, z, "kerb_stairs", "south" if z < z0 + 34 else "north")
        k.put(bx - 2, y + 1 + rise, z, "kerb_wall")
        k.put(bx + 2, y + 1 + rise, z, "kerb_wall")
    for x in (x0 + 8, x0 + 38):
        K.tree(k, x, y + 1, z0 + 40, "willow")
    K.bench(k, x0 + 14, z0 + 40, "north", n=3)
    K.bench(k, x0 + 33, z0 + 40, "north", n=3)
    k.titik("obrol", x0 + 13, y + 1, z0 + 40, "north")
    k.titik("kerja:pancing", x0 + 18, y + 1, z0 + 37, "north")
    sx, sz = K.stall(k, x0 + 30, lz + 6, "north", w=3, cloth=("minecraft:yellow_wool", "cloth"),
                     goods=("minecraft:lantern",))
    k.titik("lapak", sx, y + 1, sz, "north", {"lentera": 1})
    # painted gate where the lane meets the avenue
    end = x0 + 44 if sisi == 0 else x0 + 3
    for dz in (-3, 2):
        for dy in range(1, 9):
            k.put(end, y + dy, lz + dz, "red")
    for z in range(lz - 4, lz + 4):
        k.put(end, y + 9, z, "beam", {"axis": "z"})
        k.put(end, y + 8, z, "gold" if lz - 1 <= z <= lz else "plaster")
    K.gable(k, end, y + 10, lz - 4, end, lz + 3, along="z", overhang=1, pearl=True, role="tile_stairs",
            slab_role="tile_slab", full_role="tile")
    k.jalur([(x0 + 3, lz), (x0 + 24, lz), (x0 + 44, lz)], jalur="lentera")


# ===================================================================================================== arena
AR_W0, AR_W1 = 3, 92          # outer wall of the Arena Agung (canvas x / z)
AR_F0, AR_F1 = 18, 77         # arena floor 60 x 60
AR_GATE = (45, 50)            # gate tunnels in the middle of every side (along coordinate)
SEKTE_WARNA = ("red", "blue", "green", "yellow", "purple", "white", "black", "cyan")


def _ar_pos(side, a, d):
    """Canvas (x, z) of a tribune cell: side n/s/w/e, `a` along the side, `d` outward from the floor edge."""
    if side == "n":
        return a, AR_F0 - 1 - d
    if side == "s":
        return a, AR_F1 + 1 + d
    if side == "w":
        return AR_F0 - 1 - d, a
    return AR_F1 + 1 + d, a


AR_FACE = {"n": "south", "s": "north", "w": "east", "e": "west"}


def build_arena():
    """Distrik Arena (38.B.3, south-east): the Arena Agung over four lots, the sect delegations' lodge, the public
    training ground and three house lots from the shared pool."""
    rumah = build_rumah_pool()
    k = kanvas("arena", h=48)
    skip = {(0, 0, "e"), (1, 0, "w"), (0, 0, "s"), (0, 1, "n"), (1, 0, "s"), (1, 1, "n"), (0, 1, "e"), (1, 1, "w")}
    K.street_grid(k, skip=skip)
    arena_agung(k)
    wisma_delegasi(k, *o(2, 0))
    lapangan_latihan(k, *o(1, 2))
    names = {(0, 0): "arena_agung_a", (1, 0): "arena_agung_b", (0, 1): "arena_agung_c", (1, 1): "arena_agung_d",
             (2, 0): "wisma_delegasi", (1, 2): "lapangan_latihan"}
    simpan_kanvas(k, "arena", names, utuh=("arena_agung_a", "arena_agung_b", "arena_agung_c", "arena_agung_d"))
    for ij in ((2, 1), (0, 2), (2, 2)):
        daftar_pool("arena", ij[0], ij[1], "hunian/rumah", rumah)


def arena_agung(k, y=LY):
    """Arena Agung (38.B.3, 38.B.6.4): a 90 x 90 stadium - the 60 x 60 fighting floor with the 21 x 21 lei tai in the
    middle (polished andesite, red nether brick rim, steps on four sides, corner lanterns and sect banners), six-row
    tribunes on all four sides (160 spectator points for the client crowd), a covered promenade under a tile roof along
    the outer wall, four gate tunnels, the judges' pavilion over the north gate with gong and drum, the champions' room
    and the preparation room under the south tribune, eight sect banner poles round the floor."""
    # --- the floor
    for x in range(AR_F0, AR_F1 + 1):
        for z in range(AR_F0, AR_F1 + 1):
            ring = min(x - AR_F0, AR_F1 - x, z - AR_F0, AR_F1 - z)
            if ring < 2:
                k.put(x, y, z, "minecraft:packed_mud")
            elif (x - AR_F0) % 10 == 0 or (z - AR_F0) % 10 == 0:
                k.put(x, y, z, "minecraft:polished_andesite")
            else:
                k.put(x, y, z, "minecraft:smooth_stone")
    c0, c1 = 37, 57
    cx = (c0 + c1) // 2
    for x in range(c0, c1 + 1):
        for z in range(c0, c1 + 1):
            edge = x in (c0, c1) or z in (c0, c1)
            k.put(x, y + 1, z, "minecraft:red_nether_bricks" if edge else "kerb")
            k.put(x, y + 2, z, "minecraft:red_nether_bricks" if edge else
                  ("minecraft:chiseled_stone_bricks" if (x - cx) % 5 == 0 and (z - cx) % 5 == 0 else "paving"))
    for d, (dx, dz) in FACE.items():
        for w in range(-2, 3):
            for step in (0, 1):
                px = cx + dx * (11 + step) + (w if dz else 0)
                pz = cx + dz * (11 + step) + (w if dx else 0)
                k.stairs(px, y + 2 - step, pz, "paving_stairs", OPP[d])
                if step == 0:
                    k.put(px, y + 1, pz, "kerb")
    for i, (x, z) in enumerate(((c0, c0), (c1, c0), (c0, c1), (c1, c1))):
        k.put(x, y + 3, z, "minecraft:red_nether_brick_wall")
        k.put(x, y + 4, z, "minecraft:red_nether_brick_wall")
        k.put(x, y + 5, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    pts = [-6, 3, 0, 6, 3, 0, 0, 3, -6, 0, 3, 6]
    k.kotak("panggung", c0, y + 3, c0, c1, y + 8, c1, "south", acara="turnamen")
    k.jangkar(cx + 0.5, y + 3, cx + 0.5, "kotabesar/panggung", acara="turnamen", pts=pts)
    k.titik("latihan", cx - 6, y + 3, cx, "east", {"sudut": "biru"})
    k.titik("latihan", cx + 6, y + 3, cx, "west", {"sudut": "merah"})
    # --- the tribunes
    sides = ("n", "s", "w", "e")
    n_pen = {s_: 0 for s_ in sides}
    for side in sides:
        face = AR_FACE[side]
        for a in range(AR_F0, AR_F1 + 1):
            gate = AR_GATE[0] - 1 <= a <= AR_GATE[1] + 1
            for d in range(0, 15):
                x, z = _ar_pos(side, a, d)
                if d == 14:
                    _arena_wall(k, x, z, y, side, a)
                    continue
                if gate:
                    for yy in range(y + 1, y + 7):
                        k.put(x, yy, z, "kerb")
                    continue
                if d >= 12:
                    for yy in range(y + 1, y + 7):
                        k.put(x, yy, z, "kerb")
                    continue
                r = d // 2
                if d % 2 == 0:
                    for yy in range(y + 1, y + 1 + r):
                        k.put(x, yy, z, "kerb")
                    K.chair(k, x, y + 1 + r, z, face)
                    if (a + r * 3) % 8 == 0 and n_pen[side] < 40:
                        k.titik("penonton", x, y + 1 + r, z, face, {"tribun": side, "baris": r})
                        n_pen[side] += 1
                else:
                    for yy in range(y + 1, y + 2 + r):
                        k.put(x, yy, z, "kerb" if (a + d) % 9 else "kerb_alt")
            # gallery columns and the roof
            xg, zg = _ar_pos(side, a, 12)
            if (a - AR_F0) % 6 == 3:
                for yy in range(y + 7, y + 12):
                    k.put(xg, yy, zg, "red")
            for d, role in ((11, "tile_slab"), (12, "tile_stairs"), (13, "tile"), (14, "tile")):
                xr, zr = _ar_pos(side, a, d)
                if role == "tile_slab":
                    k.slab(xr, y + 12, zr, "tile_slab")
                elif role == "tile_stairs":
                    k.stairs(xr, y + 12, zr, "tile_stairs", face)
                else:
                    k.put(xr, y + 12, zr, "tile")
            xb, zb = _ar_pos(side, a, 13)
            if (a - AR_F0) % 6 == 0:
                k.put(xb, y + 11, zb, "lamp", {"axis": "y"})
    # corners: solid up to the promenade, a roofed corner tower with a stair down to the street
    for (cxa, cza) in ((AR_W0, AR_W0), (AR_F1 + 1, AR_W0), (AR_W0, AR_F1 + 1), (AR_F1 + 1, AR_F1 + 1)):
        for x in range(cxa, cxa + 15):
            for z in range(cza, cza + 15):
                outer = x in (AR_W0, AR_W1) or z in (AR_W0, AR_W1)
                for yy in range(y + 1, y + (13 if outer else 7)):
                    k.put(x, yy, z, "kerb" if not outer or yy < y + 7 else "plaster")
                if not outer:
                    k.put(x, y + 12, z, "tile_slab", {"type": "bottom", "waterlogged": "false"})
        K.stone_lantern(k, cxa + 7, y + 7, cza + 7)
    # gate tunnels (6 wide, 5 high) through every tribune, lamps inside, gold boards outside
    for side in sides:
        for a in range(AR_GATE[0], AR_GATE[1] + 1):
            for d in range(0, 15):
                x, z = _ar_pos(side, a, d)
                for yy in range(y + 1, y + 6):
                    k.put(x, yy, z, "minecraft:air")
                k.put(x, y, z, "paving" if (a + d) % 4 else "kerb_chisel")
        for d in (3, 9):
            x, z = _ar_pos(side, (AR_GATE[0] + AR_GATE[1]) // 2, d)
            K.lit(k, x, y + 6, z, chain=1)
        for a in range(AR_GATE[0] - 1, AR_GATE[1] + 2):
            x, z = _ar_pos(side, a, 14)
            k.put(x, y + 7, z, "gold" if AR_GATE[0] + 1 <= a <= AR_GATE[1] - 1 else "red")
        x, z = _ar_pos(side, AR_GATE[0] - 1, 0)
        k.titik("jaga", x, y + 1, z, AR_FACE[side])
    # judges' pavilion over the north gate
    jx0, jx1 = 41, 54
    for x in range(jx0, jx1 + 1):
        for d in range(7, 14):
            _, z = _ar_pos("n", x, d)
            for yy in range(y + 1, y + 7):
                if k.get(x, yy, z) in (None, "minecraft:air") and not (AR_GATE[0] <= x <= AR_GATE[1] and yy <= y + 5):
                    k.put(x, yy, z, "kerb")
            k.put(x, y + 6, z, "imperial")
    z_front = _ar_pos("n", 0, 7)[1]
    z_back = _ar_pos("n", 0, 13)[1]
    K.colonnade(k, jx0, z_back, jx1, z_front, y + 6, 5, every=3)
    K.roof_over(k, jx0, z_back, jx1, z_front, y + 13, kind="hip", tiers=2, overhang=1, tile=True)
    for i, x in enumerate((cx - 3, cx, cx + 3)):
        K.chair(k, x, y + 7, z_front + 1 - 2, "south")
        k.titik("kursi", x, y + 7, z_front - 1, "south", {"peran": "PENGUJI"})
    k.put(jx0 + 2, y + 7, z_back + 2, "minecraft:bell", {"attachment": "floor", "facing": "south", "powered": "false"})
    K.drum(k, jx1 - 2, y + 7, z_back + 2, "south")
    for x in range(jx0 + 1, jx1, 3):
        K.wall_banner(k, x, y + 10, z_front, "south", SEKTE_WARNA[x % 8])
    # champions' room (south-east) and preparation room (south-west) under the south tribune
    for name, (rx0, rx1) in (("juara", (52, 72)), ("persiapan", (23, 43))):
        rz0, rz1 = _ar_pos("s", 0, 8)[1], _ar_pos("s", 0, 12)[1]
        for x in range(rx0, rx1 + 1):
            for z in range(rz0, rz1 + 1):
                k.put(x, y, z, "wood")
                for yy in range(y + 1, y + 4):
                    k.put(x, yy, z, "minecraft:air")
                k.put(x, y + 4, z, "wood")
        door_x = rx0 - 1 if name == "juara" else rx1 + 1
        for dy in (1, 2):
            k.put(door_x, y + dy, rz0 + 2, "minecraft:air")
        K.door(k, door_x, y + 1, rz0 + 2, "west" if name == "juara" else "east", "left")
        K.lit(k, (rx0 + rx1) // 2, y + 4, (rz0 + rz1) // 2, chain=1)
        K.lit(k, rx0 + 3, y + 4, (rz0 + rz1) // 2, chain=1)
        K.lit(k, rx1 - 3, y + 4, (rz0 + rz1) // 2, chain=1)
        if name == "juara":
            px = (rx0 + rx1) // 2
            k.put(px, y + 1, rz1, "kerb_chisel")
            k.put(px, y + 2, rz1, "gold")
            for x in (px - 2, px + 2):
                K.wall_banner(k, x, y + 3, rz1, "north", "yellow")
            for x in range(rx0 + 1, rx0 + 7, 2):
                K.bed(k, x, y + 1, rz1, "south")
                k.titik("kasur", x, y + 1, rz1, "north", {"peran": "JUARA_SEKTE"})
            K.bench(k, rx1 - 1, rz0 + 3, "west", n=3)
            K.chest(k, rx1, y + 1, rz1, None, "north")
            k.jangkar(px + 0.5, y + 1, rz1 - 1.5, "kotabesar/layanan", yaw=180.0, layanan="ruang_juara",
                      peran="PENGUJI")
        else:
            weapon_rack(k, rx0, rz1, y, "north", n=8)
            for x in range(rx1 - 7, rx1, 2):
                K.bed(k, x, y + 1, rz0, "north")
                k.titik("kasur", x, y + 1, rz0, "south", {"peran": "JUARA_SEKTE"})
            for x in range(rx0 + 1, rx0 + 9, 4):
                k.titik("meditasi", x, y + 1, rz0 + 2, "south")
    # registration desk inside the south gate and the eight sect banner poles round the floor
    sx, sz = _ar_pos("s", AR_GATE[1] + 3, 0)
    K.table(k, sx, y + 1, sz - 2, "minecraft:white_carpet")
    K.chair(k, sx + 1, y + 1, sz - 2, "west")
    k.jangkar(sx + 0.5, y + 1, sz - 3.5, "kotabesar/layanan", yaw=180.0, layanan="turnamen", peran="PENGUJI")
    poles = ((30, AR_F0 + 1), (65, AR_F0 + 1), (30, AR_F1 - 1), (65, AR_F1 - 1),
             (AR_F0 + 1, 30), (AR_F0 + 1, 65), (AR_F1 - 1, 30), (AR_F1 - 1, 65))
    for i, (x, z) in enumerate(poles):
        k.put(x, y, z, "kerb_chisel")
        K.banner_pole(k, x, y + 1, z, height=5, color=SEKTE_WARNA[i], rotation=(i * 4) % 16)
    for side in sides:
        for a in range(AR_F0 + 5, AR_F1 - 3, 10):
            if AR_GATE[0] - 2 <= a <= AR_GATE[1] + 2:
                continue
            x, z = _ar_pos(side, a, -2)
            K.stone_lantern(k, x, y + 1, z)
    k.jangkar(60.5, y + 1, 60.5, "kotabesar/zona", distrik="arena", protect=100)
    k.kotak("bangunan", AR_W0, y, AR_W0, AR_W1, y + 16, AR_W1, "south", nama="arena_agung")


def _arena_wall(k, x, z, y, side, a):
    """One column of the arena's outer wall: stone to the promenade, plaster with lattice windows above, red pillars
    every 6, a beam band; the roof tile on top is laid by the tribune loop."""
    pillar = (a - AR_F0) % 6 == 3
    for yy in range(y + 1, y + 12):
        if pillar:
            k.put(x, yy, z, "red" if yy > y + 1 else "kerb_chisel")
        elif yy <= y + 6:
            k.put(x, yy, z, "kerb")
        elif yy == y + 11:
            k.put(x, yy, z, "beam", {"axis": "x" if side in ("n", "s") else "z"})
        elif yy in (y + 8, y + 9) and (a - AR_F0) % 6 in (1, 5):
            K.lattice(k, x, yy, z, {"n": "north", "s": "south", "w": "west", "e": "east"}[side])
        else:
            k.put(x, yy, z, "plaster")
    if (a - AR_F0) % 12 == 0:
        K.banner_pole(k, x, y + 13, z, height=3, color=SEKTE_WARNA[(a // 12) % 8])


def wisma_delegasi(k, x0, z0, y=LY):
    """Wisma Delegasi (38.B.6.4): the lodge of the visiting sect delegations - a two-storey hall whose ground floor is
    the common hall (one long feast table with sixteen seats under eight sect banners, a tea corner) and whose upper
    floor holds six suites for the champions; a sparring court with a wooden post and a meditation corner behind."""
    hx0, hz0, hx1, hz1 = x0 + 4, z0 + 5, x0 + 43, z0 + 27
    f = K.podium(k, hx0 - 1, hz0 - 1, hx1 + 1, hz1 + 1, y, 1, steps=("south",), step_w=5)
    ix0, iz0, ix1, iz1 = K.hall(k, hx0, hz0, hx1, hz1, f, 5, door_sides=("south",), door_w=3, windows=4)
    mid = (iz0 + iz1) // 2
    for x in range(ix0 + 8, ix1 - 7):
        K.table(k, x, f + 1, mid, "minecraft:red_carpet")
        for dz, face in ((-1, "south"), (1, "north")):
            if (x - ix0) % 2 == 0:
                K.chair(k, x, f + 1, mid + dz, face)
                k.titik("kursi", x, f + 1, mid + dz, face)
    k.titik("meja_makan", (ix0 + ix1) // 2, f + 1, mid, "south")
    for i, x in enumerate(range(ix0 + 4, ix1 - 2, 4)):
        K.wall_banner(k, x, f + 4, iz0, "south", SEKTE_WARNA[i % 8])
    for z in range(iz0 + 1, iz1 - 6, 3):
        K.low_table(k, ix1 - 2, f + 1, z)
        k.put(ix1 - 1, f + 1, z, "minecraft:red_carpet")
        k.titik("kursi_lantai", ix1 - 1, f + 1, z, "west")
    K.dandan(k, (ix0 + 3, iz0, ix1 - 3, iz1), f, "teh", titik_maks=2, simpan=[(ix0, mid - 3, ix0 + 4, mid + 6)])
    K.stair_flight(k, ix0 + 1, f + 1, mid + 3, "north", 5, width=2)
    f2 = f + 5
    K.upper_floor(k, ix0, iz0, ix1, iz1, f2, hole=(ix0 + 1, mid - 1, ix0 + 2, mid + 3))
    K.hall(k, hx0, hz0, hx1, hz1, f2, 4, door_sides=(), windows=4, ceiling=True, lamps=False)
    _inn_floor(k, ix0, iz0, ix1, iz1, f2, 1, False)
    top = f2 + 4
    K.brackets(k, hx0, hz0, hx1, hz1, top)
    K.hip(k, hx0, top + 1, hz0, hx1, hz1, overhang=2, tiers=2, tier_gap=2, tier_band=2, band_role="plaster",
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    k.jangkar((ix0 + ix1) / 2.0 + 0.5, f + 1, iz1 - 1.5, "kotabesar/layanan", yaw=180.0, layanan="wisma_delegasi",
              peran="PELAYAN")
    # sparring court behind (south yard)
    for x in range(x0 + 6, x0 + 42):
        for z in range(z0 + 33, z0 + 44):
            edge = x in (x0 + 6, x0 + 41) or z in (z0 + 33, z0 + 43)
            k.put(x, y, z, "kerb" if edge else "minecraft:packed_mud")
    for x in (x0 + 14, x0 + 34):
        k.put(x, y + 1, z0 + 38, "log", {"axis": "y"})
        k.put(x, y + 2, z0 + 38, "log", {"axis": "y"})
        k.put(x, y + 3, z0 + 38, "minecraft:hay_block", {"axis": "y"})
        k.titik("latihan", x, y + 1, z0 + 40, "north")
    for x in (x0 + 20, x0 + 24, x0 + 28):
        k.titik("latihan", x, y + 1, z0 + 37, "south")
    k.titik("meditasi", x0 + 8, y + 1, z0 + 35, "east")
    K.tree(k, x0 + 42, y + 1, z0 + 30, "pine")
    k.titik("pintu", (hx0 + hx1) // 2, f + 1, hz1 + 1, "south")
    k.kotak("bangunan", hx0, y, hz0, hx1, top + 10, hz1, "south", nama="wisma_delegasi")


def lapangan_latihan(k, x0, z0, y=LY):
    """Lapangan Latihan (38.B.3 arena): the public training ground - eight straw dummies with pumpkin heads, an archery
    range of four targets with a shooting line, two sparring rings, weapon racks, a stone lifting row, the instructor's
    pavilion with a drum; twelve latihan points."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 3, z0 + 45):
            k.put(x, y, z, "minecraft:packed_mud" if (x + z) % 7 else "minecraft:coarse_dirt")
    for i in range(8):
        dx = x0 + 6 + (i % 4) * 5
        dz = z0 + 7 + (i // 4) * 6
        k.put(dx, y + 1, dz, "wood_fence")
        k.put(dx, y + 2, dz, "minecraft:hay_block", {"axis": "y"})
        k.put(dx, y + 3, dz, "minecraft:carved_pumpkin", {"facing": "south"})
        k.put(dx - 1, y + 2, dz, "wood_fence")
        k.put(dx + 1, y + 2, dz, "wood_fence")
        k.titik("latihan", dx, y + 1, dz + 2, "north", {"boneka": i})
    # archery range on the east side: shooting line z+40, targets at z+8
    for i in range(4):
        tx = x0 + 30 + i * 4
        k.put(tx, y + 1, z0 + 6, "minecraft:hay_block", {"axis": "y"})
        k.put(tx, y + 2, z0 + 6, "minecraft:target", {"power": "0"})
        k.put(tx, y, z0 + 34, "kerb_chisel")
        k.titik("latihan", tx, y + 1, z0 + 35, "north", {"panah": i})
    for x in range(x0 + 28, x0 + 44):
        k.put(x, y, z0 + 33, "kerb")
    # sparring rings
    for (rx, rz) in ((x0 + 10, z0 + 26), (x0 + 22, z0 + 26)):
        for x in range(rx - 4, rx + 5):
            for z in range(rz - 4, rz + 5):
                dd = math.hypot(x - rx, z - rz)
                if 3.5 <= dd < 4.5:
                    k.put(x, y, z, "kerb")
                elif dd < 3.5:
                    k.put(x, y, z, "minecraft:smooth_stone")
        k.titik("latihan", rx - 2, y + 1, rz, "east")
        k.titik("latihan", rx + 2, y + 1, rz, "west")
    weapon_rack(k, x0 + 4, z0 + 42, y, "north", n=8)
    for x in range(x0 + 14, x0 + 22, 2):
        k.put(x, y + 1, z0 + 42, ("minecraft:stone", "minecraft:cobblestone", "minecraft:andesite")[x % 3])
    k.titik("latihan", x0 + 17, y + 1, z0 + 41, "south", {"angkat": 1})
    # instructor's pavilion (north-east of the rings)
    pv = K.aula(k, x0 + 28, z0 + 37, x0 + 43, z0 + 44, h=4, podium_h=1, teras=0, roof="hip", door_sides=("north",),
                door_w=3, tile=True)
    px0, pz0, px1, pz1 = pv["inner"]
    pf = pv["f"]
    K.drum(k, px0 + 1, pf + 1, pz1, "north")
    K.chair(k, (px0 + px1) // 2, pf + 1, pz1, "north")
    k.titik("kursi", (px0 + px1) // 2, pf + 1, pz1, "north", {"peran": "PENGUJI"})
    k.jangkar((px0 + px1) / 2.0 + 0.5, pf + 1, pz0 - 1.5, "kotabesar/layanan", yaw=0.0, layanan="pelatih",
              peran="PENGUJI")
    k.kotak("bangunan", x0 + 28, y, z0 + 37, x0 + 43, pf + 10, z0 + 44, "north", nama="lapangan_latihan")


# ===================================================================================================== kumuh
KM_ROAD = (69, 75)       # the east road from the side gate (canvas z), gate arch centred on z = 72
KM_WALL = ("minecraft:spruce_planks", "minecraft:spruce_planks", "minecraft:spruce_planks", "minecraft:oak_planks",
           "minecraft:stripped_spruce_log", "minecraft:spruce_planks", "minecraft:mossy_cobblestone")
PLUS_U = {"south": "east", "north": "west", "east": "south", "west": "north"}
JUNK = ("minecraft:cobblestone", "minecraft:flower_pot", "minecraft:iron_bars", "minecraft:barrel", "minecraft:iron_chain",
        "minecraft:cracked_stone_bricks", "minecraft:lantern")


def km_ground(k, x0, z0, x1, z1, y=LY):
    """Trodden earth of the slum: coarse dirt and paths with gravel and mud patches."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = (x * 73856093 ^ z * 19349663) & 0xFF
            if h < 120:
                role = "minecraft:coarse_dirt"
            elif h < 190:
                role = "minecraft:dirt_path"
            elif h < 225:
                role = "minecraft:gravel"
            elif h < 250:
                role = "minecraft:packed_mud"
            else:
                role = "minecraft:mud"
            k.put(x, y, z, role)


def _gubuk_frame(x0, z0, w, d, face):
    """Frame of a shack footprint (x0..x0+w-1, z0..z0+d-1): u runs along the door side, v from the door to the back.
    Returns (U, V, at)."""
    if face == "south":
        return w, d, lambda u, v: (x0 + u, z0 + d - 1 - v)
    if face == "north":
        return w, d, lambda u, v: (x0 + w - 1 - u, z0 + v)
    if face == "east":
        return d, w, lambda u, v: (x0 + w - 1 - v, z0 + u)
    return d, w, lambda u, v: (x0 + v, z0 + d - 1 - u)


def gubuk(k, x0, z0, w, d, face="south", y=LY, kind=0):
    """A slum shack (38.B.3 kumuh): weathered spruce and oak boards between log corner posts on a cobble sill, mossy
    patches, a lean-to roof of oak slabs falling toward the door with an overhang, a doorway under a propped trapdoor
    awning, a shutter window; inside a bed or a straw pallet, a barrel with a candle, sometimes a chest, and the work
    corner that tells who lives there (kind 0 cook, 1 weaver, 2 net mender, 3 scavenger, 4 old herbalist)."""
    U, V, at = _gubuk_frame(x0, z0, w, d, face)
    inward = OPP[face]
    for u in range(U):
        for v in range(V):
            x, z = at(u, v)
            edge_u = u in (0, U - 1)
            edge_v = v in (0, V - 1)
            top = y + 3 + v // 2
            k.put(x, y, z, "minecraft:cobblestone" if (edge_u or edge_v) else "minecraft:spruce_planks")
            for yy in range(y + 1, top + 1):
                if edge_u and edge_v:
                    k.put(x, yy, z, "minecraft:spruce_log", {"axis": "y"})
                elif edge_u or edge_v:
                    if yy == y + 1 and (x * 5 + z * 11) % 3 == 0:
                        k.put(x, yy, z, "minecraft:mossy_cobblestone")
                    else:
                        bid = KM_WALL[(x * 7 + z * 3 + yy) % len(KM_WALL)]
                        k.put(x, yy, z, bid, {"axis": "y"} if bid.endswith("_log") else None)
                else:
                    k.put(x, yy, z, "minecraft:air")
            k.put(x, top + 1, z, "minecraft:oak_slab", {"type": "bottom" if v % 2 == 0 else "top", "waterlogged": "false"})
        xo, zo = at(u, -1)
        k.put(xo, y + 4, zo, "minecraft:oak_slab", {"type": "bottom", "waterlogged": "false"})
    du = U // 2
    xd, zd = at(du, 0)
    k.put(xd, y + 1, zd, "minecraft:air")
    k.put(xd, y + 2, zd, "minecraft:air")
    xa, za = at(du, -1)
    k.put(xa, y + 3, za, "minecraft:spruce_trapdoor", {"facing": inward, "half": "top", "open": "true",
                                                       "powered": "false", "waterlogged": "false"})
    if V >= 6:
        xw, zw = at(U - 1, V // 2)
        k.put(xw, y + 2, zw, "minecraft:spruce_trapdoor", {"facing": PLUS_U[face], "half": "bottom", "open": "true",
                                                           "powered": "false", "waterlogged": "false"})
    # bed or straw pallet against the back wall (head at the back)
    bx, bz = at(1, V - 2)
    if kind in (0, 1) and V >= 5:
        K.bed(k, bx, y + 1, bz, inward, role="minecraft:brown_bed")
        k.titik("kasur", bx, y + 1, bz, face)
    else:
        k.put(bx, y + 1, bz, "minecraft:hay_block", {"axis": "y"})
        k.put(bx, y + 2, bz, "minecraft:brown_carpet")
        k.titik("kasur", bx, y + 2, bz, face)
    cx_, cz_ = at(U - 2, V - 2)
    K.barrel(k, cx_, y + 1, cz_, "up")
    k.put(cx_, y + 2, cz_, "minecraft:candle", {"candles": "1", "lit": "true", "waterlogged": "false"})
    # the work corner by the door
    px, pz = at(U - 2, 1)
    tx, tz = at(U - 3, 1) if U >= 5 else at(du, 1)
    look = PLUS_U[face]
    if kind == 1:
        k.put(px, y + 1, pz, "minecraft:loom", {"facing": inward})
        k.titik("kerja:tenun", tx, y + 1, tz, look)
    elif kind == 2:
        k.put(px, y + 1, pz, "minecraft:barrel", {"facing": "up", "open": "true"})
        for side in (-1, 1):
            xn, zn = at(du + side * 2, -1)
            k.put(xn, y + 1, zn, "minecraft:spruce_fence")
            k.put(xn, y + 2, zn, "minecraft:spruce_fence")
        xm, zm = at(du + 1, -1)
        k.put(xm, y + 2, zm, "minecraft:white_carpet")
        xj, zj = at(du - 1, -2)
        k.titik("kerja:jala", xj, y + 1, zj, inward)
    elif kind == 3:
        K.barrel(k, px, y + 1, pz, "east")
        k.put(px, y + 2, pz, JUNK[(x0 + z0) % len(JUNK)])
        k.titik("kerja:rak", tx, y + 1, tz, look)
    elif kind == 4:
        k.put(px, y + 1, pz, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false",
                                                         "has_bottle_2": "false"})
        xh, zh = at(du + 2, -1)
        k.put(xh, y + 1, zh, "minecraft:potted_fern")
        k.titik("kerja:lumpang", tx, y + 1, tz, look)
    else:
        k.put(px, y + 1, pz, "minecraft:cauldron")
        k.titik("kerja:kuali", tx, y + 1, tz, look)
    if (x0 * 3 + z0) % 2 == 0 and U >= 6:
        cx2, cz2 = at(1, 1)
        K.chest(k, cx2, y + 1, cz2, None, face)
    xp, zp = at(du, -1)
    xa, za = at(0, 0)
    xb, zb = at(U - 1, V - 1)
    k.kotak("bangunan", min(xa, xb), y, min(za, zb), max(xa, xb), y + 4 + V // 2, max(za, zb), face, nama="gubuk")
    return xp, zp


def gubuk_blok(k, x0, z0, x1, z1, seed, y=LY):
    """Fills a rectangle with paired rows of shacks facing narrow alleys (3 wide): row facing south, alley, row facing
    north, a one-block back gap, again. Alleys get washing lines, crates, puddles and the alley life points."""
    import random
    rng = random.Random(seed)
    km_ground(k, x0, z0, x1, z1, y)
    z = z0
    pair = 0
    alleys = []
    doors = set()
    while z + 5 <= z1:
        face = "south" if pair % 2 == 0 else "north"
        depth = 7 + rng.randrange(2)
        if z + depth - 1 > z1:
            depth = z1 - z + 1
            if depth < 6:
                break
        x = x0
        while x + 5 <= x1:
            w = 7 + rng.randrange(4)
            if x + w - 1 > x1:
                w = x1 - x + 1
                if w < 6:
                    break
            doors.add(gubuk(k, x, z, w, depth, face, y=y, kind=rng.randrange(5)))
            x += w + (1 if rng.random() < 0.6 else 0)
        if face == "south":
            alleys.append((z + depth, z + depth + 2))
            z += depth + 3
        else:
            z += depth + 1
        pair += 1
    def free(x, zz):
        return all((x + dx, zz) not in doors for dx in (-1, 0, 1))
    for (a0, a1) in alleys:
        if a1 > z1:
            continue
        for x in range(x0 + 4, x1 - 3, 11):
            if free(x, a0) and free(x, a1):
                for zz in (a0, a1):
                    for dy in (1, 2, 3):
                        k.put(x, y + dy, zz, "minecraft:spruce_fence")
                k.put(x, y + 3, a0 + 1, "minecraft:white_wool" if x % 2 else "minecraft:brown_wool")
        for i, x in enumerate(range(x0 + 2, x1 - 1, 9)):
            k.put(x, y, (a0 + a1) // 2, "minecraft:water", {"level": "0"})
            if i % 2 == 0:
                k.titik("main", x + 3, y + 1, (a0 + a1) // 2, "east")
        for x in range(x0 + 7, x1 - 1, 13):
            if free(x, a1) and free(x + 1, a1):
                K.crates(k, x, a1, x + 1, a1, y + 1, h=2)
        k.titik("obrol", (x0 + x1) // 2, y + 1, a0 + 1, "west")
        k.titik("kerja:sapu", x0 + 3, y + 1, a0 + 1, "east")
        k.jalur([(x0, a0 + 1), ((x0 + x1) // 2, a0 + 1), (x1, a0 + 1)], jalur="gang")


def km_road(k, x0, x1, y=LY):
    """The east road: a rutted earth road 7 wide with gravel wheel ruts and plank boardwalks over the mud edges."""
    for x in range(x0, x1 + 1):
        for z in range(KM_ROAD[0], KM_ROAD[1] + 1):
            if z in (KM_ROAD[0], KM_ROAD[1]):
                k.put(x, y, z, "minecraft:spruce_planks" if x % 5 else "minecraft:stripped_spruce_log", {"axis": "x"}
                      if x % 5 == 0 else None)
            elif z in (KM_ROAD[0] + 2, KM_ROAD[1] - 2):
                k.put(x, y, z, "minecraft:gravel")
            else:
                k.put(x, y, z, "minecraft:dirt_path" if (x * 3 + z) % 7 else "minecraft:coarse_dirt")


def build_kumuh():
    """Kampung Kumuh (38.B.3, outside the east gate): the shanty town along the east road - five lots of shack rows and
    alleys, the junk market and the slum carpenter by the gate, the Rat's Nest pawnshop over the Pasar Gelap cellar,
    the roadside warung, and the temple's public kitchen. No wall of its own; weathered on purpose."""
    k = kanvas("kumuh", h=40)
    km_ground(k, 0, 0, 143, 143)
    km_road(k, 0, 143)
    for (i, j), seed in (((0, 0), 1), ((1, 0), 2), ((2, 0), 3), ((1, 2), 4), ((2, 2), 5)):
        x0, z0 = o(i, j)
        gubuk_blok(k, x0 + 1, z0 + 1, x0 + 46, z0 + (39 if (i, j) == (1, 0) else 46), tl._seed("kumuh/blok_%d" % seed))
    kuil_tikus(k, *o(1, 0))
    pasar_loak(k, *o(0, 1))
    sarang_tikus(k, *o(1, 1))
    warung_jalan(k, *o(2, 1))
    dapur_umum(k, *o(0, 2))
    names = {(0, 0): "gubuk_a", (1, 0): "gubuk_b", (2, 0): "gubuk_c", (0, 1): "pasar_loak", (1, 1): "sarang_tikus",
             (2, 1): "warung_jalan", (0, 2): "dapur_umum", (1, 2): "gubuk_d", (2, 2): "gubuk_e"}
    simpan_kanvas(k, "kumuh", names, tinggi_min=20)


def kuil_tikus(k, x0, z0, y=LY):
    """A tiny roadside shrine of the slum in the middle of gubuk_b: a stone niche with a clay image, candles and a
    cracked offering pot, a bench, a wishing ribbon post."""
    cx, cz = x0 + 40, z0 + 43
    for dx in range(-2, 3):
        for dz in range(-1, 2):
            k.put(cx + dx, y, cz + dz, "minecraft:cobblestone")
            for yy in range(y + 1, y + 5):
                k.put(cx + dx, yy, cz + dz, "minecraft:air")
    for dx in (-1, 0, 1):
        k.put(cx + dx, y + 1, cz - 1, "minecraft:mossy_stone_bricks")
        k.put(cx + dx, y + 3, cz - 1, "minecraft:mossy_stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    k.put(cx, y + 2, cz - 1, "minecraft:decorated_pot", {"facing": "south", "cracked": "true", "waterlogged": "false"})
    K.candle(k, cx - 1, y + 2, cz - 1, 2, "white")
    K.candle(k, cx + 1, y + 2, cz - 1, 1, "red")
    k.titik("dupa", cx, y + 1, cz, "north")


def pasar_loak(k, x0, z0, y=LY):
    """The junk market at the east gate (gubuk + road lot (0,1)): stalls of scrap and second-hand goods on both sides
    of the road, the slum carpenter's open shed (kotabesar/tukang, 38PR), shack rows north and south."""
    gubuk_blok(k, x0 + 1, z0 + 1, x0 + 46, z0 + 14, tl._seed("kumuh/loak_n"))
    gubuk_blok(k, x0 + 18, z0 + 33, x0 + 46, z0 + 46, tl._seed("kumuh/loak_s"))
    km_road(k, x0, x0 + 47)
    for i, sx in enumerate(range(x0 + 6, x0 + 42, 7)):
        for (zz, f) in ((KM_ROAD[0] - 2, "south"), (KM_ROAD[1] + 2, "north")):
            gx, gz = K.stall(k, sx, zz, f, w=3, cloth=("minecraft:brown_wool", "minecraft:gray_wool"),
                             goods=(JUNK[i % len(JUNK)], JUNK[(i + 3) % len(JUNK)]))
            k.titik("lapak", gx, y + 1, gz, f, {"loak": 1})
    # carpenter's shed (south side, west end)
    sx0, sz0 = x0 + 4, z0 + 34
    for x in range(sx0, sx0 + 12):
        for z in range(sz0, sz0 + 8):
            k.put(x, y, z, "minecraft:spruce_planks")
            if x in (sx0, sx0 + 11) and z in (sz0, sz0 + 7):
                for yy in range(y + 1, y + 5):
                    k.put(x, yy, z, "minecraft:spruce_log", {"axis": "y"})
            else:
                for yy in range(y + 1, y + 5):
                    k.put(x, yy, z, "minecraft:air")
            k.put(x, y + 5, z, "minecraft:oak_slab", {"type": "bottom", "waterlogged": "false"})
    k.put(sx0 + 2, y + 1, sz0 + 1, "minecraft:crafting_table")
    k.put(sx0 + 4, y + 1, sz0 + 1, "minecraft:chipped_anvil", {"facing": "east"})
    for z in range(sz0 + 3, sz0 + 6):
        for dy in (1, 2):
            k.put(sx0 + 9, y + dy, z, "minecraft:stripped_spruce_log", {"axis": "x"})
    k.titik("kerja:gergaji", sx0 + 3, y + 1, sz0 + 2, "north", {"peran": "TUKANG"})
    k.jangkar(sx0 + 6.5, y + 1, sz0 + 4.5, "kotabesar/tukang", yaw=0.0, kampung="kumuh")
    k.titik("jalan", x0 + 1, y + 1, 72, "east", {"urut": 0, "jalur": "jalan_timur"})
    k.titik("tiba", x0 + 2, y + 1, 72, "east")


def sarang_tikus(k, x0, z0, y=LY):
    """Sarang Tikus (38.B.3, module K's Pasar Gelap): the Rat's Nest - a leaning two-storey pawnshop of grey boards on
    the north side of the road (barred counter, shelves of pawned goods, a dice table), a back room behind a shelf wall
    whose hidden door is the kota/gerbang_gelap entity, a stair down to the cramped smugglers' cellar of the Pasar Gelap
    (violet-awninged stalls, cages, soul lanterns, the dealer kota/gelap); the thugs' loft upstairs. South of the road a
    well, a beggar's corner and shacks; the district zone anchor."""
    km_road(k, x0, x0 + 47)
    gubuk_blok(k, x0 + 1, z0 + 33, x0 + 46, z0 + 46, tl._seed("kumuh/tikus_s"))
    bx0, bz0, bx1, bz1 = x0 + 6, z0 + 2, x0 + 30, z0 + 18
    # the cellar (below the street floor: y 0..3)
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            edge = x in (bx0, bx1) or z in (bz0, bz1)
            k.put(x, 0, z, "minecraft:polished_blackstone" if (x + z) % 3 else "minecraft:blackstone")
            for yy in (1, 2, 3):
                k.put(x, yy, z, "minecraft:polished_blackstone_bricks" if edge else "minecraft:air")
    for i, (sx, sz) in enumerate(((bx0 + 2, bz0 + 3), (bx0 + 7, bz0 + 3), (bx0 + 12, bz0 + 3), (bx0 + 2, bz0 + 9),
                                  (bx0 + 7, bz0 + 9))):
        for x in range(sx, sx + 3):
            k.slab(x, 1, sz, "minecraft:dark_oak_slab", "top")
            k.put(x, 3, sz - 1, "minecraft:purple_wool")
            k.put(x, 3, sz, "minecraft:purple_wool")
        k.put(sx, 1, sz - 1, "minecraft:dark_oak_fence")
        k.put(sx + 2, 1, sz - 1, "minecraft:dark_oak_fence")
        if i % 2:
            k.put(sx + 1, 2, sz, "minecraft:decorated_pot", {"facing": "south", "cracked": "true", "waterlogged": "false"})
        else:
            k.put(sx + 1, 2, sz, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
        k.titik("lapak", sx + 1, 1, sz + 1, "north", {"gelap": i})
    for x, z in ((bx1 - 3, bz1 - 3), (bx1 - 2, bz1 - 3), (bx1 - 3, bz1 - 2)):
        for yy in (1, 2):
            k.put(x, yy, z, "minecraft:iron_bars")
    k.put(bx1 - 2, 1, bz1 - 2, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    K.chest(k, bx0 + 14, 1, bz1 - 1, "kultivator:chests/dunia/kota/pasar_gelap", "north")
    k.put(bx0 + 15, 1, bz1 - 1, "minecraft:smithing_table")
    for x in (bx0 + 4, bx0 + 10, bx0 + 16):
        k.put(x, 3, (bz0 + bz1) // 2, "minecraft:soul_lantern", {"hanging": "true", "waterlogged": "false"})
    for x, z in ((bx0 + 1, bz0 + 1), (bx1 - 1, bz0 + 1)):
        k.put(x, 3, z, "minecraft:cobweb")
    k.jangkar(bx0 + 10.5, 1.0, bz1 - 2.5, "kota/gelap", yaw=0.0)
    # the pawnshop over it
    f = y
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            k.put(x, f, z, "minecraft:spruce_planks" if (x + z) % 4 else "minecraft:oak_planks")
    k.walls(bx0, f + 1, bz0, bx1, f + 5, bz1, role="minecraft:spruce_planks", pillar="minecraft:spruce_log",
            windows=4, window_role="minecraft:spruce_trapdoor", door=("south", 1))
    for x in range(bx0, bx1 + 1):
        for z in (bz0, bz1):
            if (x * 5 + z) % 7 == 0:
                k.put(x, f + 1, z, "minecraft:mossy_cobblestone")
            elif (x * 3 + z) % 11 == 0:
                k.put(x, f + 3, z, "minecraft:oak_planks")
    K.door(k, (bx0 + bx1) // 2, f + 1, bz1, "south", "left")
    # front room: barred counter, shelves of pawned goods, a dice table
    for x in range(bx0 + 3, bx1 - 2):
        k.put(x, f + 1, bz1 - 4, "minecraft:spruce_planks")
        if x % 3:
            k.put(x, f + 2, bz1 - 4, "minecraft:iron_bars")
    for x in range(bx0 + 1, bx1 - 4):
        if x % 4:
            K.shelf(k, x, f + 2, bz1 - 6, "south")
        else:
            K.barrel(k, x, f + 1, bz1 - 6, "south")
    k.titik("kerja:timbangan", (bx0 + bx1) // 2, f + 1, bz1 - 5, "south", {"peran": "BANKIR"})
    k.jangkar((bx0 + bx1) / 2.0 + 0.5, f + 1, bz1 - 4.5, "kotabesar/layanan", yaw=0.0, layanan="pegadaian",
              peran="BANKIR")
    K.table(k, bx0 + 3, f + 1, bz1 - 2, "")
    k.put(bx0 + 3, f + 2, bz1 - 2, "minecraft:white_carpet")
    for dx, face in ((-1, "east"), (1, "west")):
        K.chair(k, bx0 + 3 + dx, f + 1, bz1 - 2, face)
        k.titik("kursi", bx0 + 3 + dx, f + 1, bz1 - 2, face)
    # back room behind the shelf wall: the hidden door gap (2 wide) and the stair down into the cellar
    wz = bz0 + 7
    for x in range(bx0 + 1, bx1):
        for yy in (1, 2, 3):
            k.put(x, f + yy, wz, "minecraft:spruce_planks")
    gx = bx1 - 4
    for x in (gx, gx + 1):
        for yy in (1, 2):
            k.put(x, f + yy, wz, "minecraft:air")
    k.jangkar(gx + 1.0, f + 1.0, wz + 0.5, "kota/gerbang_gelap", yaw=0.0, protect=12)
    for x in range(bx0 + 1, bx1):
        for z in range(bz0 + 1, wz):
            for yy in (1, 2, 3, 4):
                k.put(x, f + yy, z, "minecraft:air")
    for i in range(4):
        sz = wz - 2 - i
        k.stairs(gx, f - i, sz, "minecraft:polished_blackstone_brick_stairs", "south")
        k.stairs(gx + 1, f - i, sz, "minecraft:polished_blackstone_brick_stairs", "south")
        for yy in range(f - i + 1, f + 3):
            k.put(gx, yy, sz, "minecraft:air")
            k.put(gx + 1, yy, sz, "minecraft:air")
    K.crates(k, bx0 + 1, bz0 + 1, bx0 + 4, bz0 + 2, f + 1, h=2)
    k.put(bx0 + 6, f + 4, bz0 + 3, "minecraft:soul_lantern", {"hanging": "true", "waterlogged": "false"})
    # the loft
    f2 = f + 5
    k.fill(bx0 + 1, f2, bz0 + 1, bx1 - 1, f2, bz1 - 1, "minecraft:spruce_planks")
    for x in range(bx0 + 2, bx0 + 8, 2):
        K.bed(k, x, f2 + 1, bz0 + 2, "north", role="minecraft:gray_bed")
        k.titik("kasur", x, f2 + 1, bz0 + 2, "south", {"peran": "KULI"})
    K.table(k, bx1 - 4, f2 + 1, bz0 + 4, "minecraft:red_carpet")
    for i in range(5):
        k.stairs(bx0 + 1, f + 1 + i, bz1 - 1 - i, "minecraft:spruce_stairs", "north")
        k.put(bx0 + 1, f2, bz1 - 1 - i, "minecraft:air")
    K.gable(k, bx0, f2 + 3, bz0, bx1, bz1, along="x", overhang=1, pearl=False, role="minecraft:spruce_stairs",
            slab_role="minecraft:spruce_slab", full_role="minecraft:spruce_planks")
    k.walls(bx0, f2 + 1, bz0, bx1, f2 + 2, bz1, role="minecraft:spruce_planks", pillar="minecraft:spruce_log",
            windows=5, window_role="minecraft:spruce_trapdoor", interior_air=False)
    K.wall_banner(k, (bx0 + bx1) // 2 - 3, f + 4, bz1 + 1, "south", "gray")
    K.wall_banner(k, (bx0 + bx1) // 2 + 3, f + 4, bz1 + 1, "south", "black")
    # south of the road: well, beggars' corner, zone anchor
    sumur_umum(k, x0 + 40, z0 + 30, r=2)
    for x in range(x0 + 4, x0 + 10):
        k.put(x, y + 1, z0 + 29, "minecraft:brown_carpet")
    k.titik("kursi_lantai", x0 + 6, y + 1, z0 + 29, "north", {"pengemis": 1})
    k.titik("kursi_lantai", x0 + 8, y + 1, z0 + 29, "north", {"pengemis": 1})
    k.jangkar(x0 + 7.5, y + 1, z0 + 29.5, "kotabesar/layanan", yaw=0.0, layanan="kumuh", peran="PENGEMIS", tokoh="kakek_lumpur")
    k.jangkar(x0 + 35.5, y + 1, z0 + 30.5, "kotabesar/layanan", yaw=90.0, layanan="kumuh_anak", peran="ANAK", tokoh="dou")
    k.jangkar(x0 + 24.5, y + 1, z0 + 29.5, "kotabesar/zona", distrik="kumuh", protect=100)
    k.kotak("bangunan", bx0, 0, bz0, bx1, f2 + 10, bz1, "south", nama="sarang_tikus")


def warung_jalan(k, x0, z0, y=LY):
    """The roadside warung where the east road leaves the slum: an open shed on posts with a big pot over a lit stove,
    a counter, benches and tables under the roof, a water butt; shack rows north and south."""
    gubuk_blok(k, x0 + 1, z0 + 1, x0 + 46, z0 + 17, tl._seed("kumuh/warung_n"))
    gubuk_blok(k, x0 + 34, z0 + 29, x0 + 46, z0 + 46, tl._seed("kumuh/warung_s"))
    gubuk_blok(k, x0 + 1, z0 + 40, x0 + 31, z0 + 46, tl._seed("kumuh/warung_sw"))
    km_road(k, x0, x0 + 47)
    sx0, sz0, sx1, sz1 = x0 + 8, z0 + 30, x0 + 30, z0 + 38
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            k.put(x, y, z, "minecraft:spruce_planks")
            for yy in range(y + 1, y + 5):
                k.put(x, yy, z, "minecraft:air")
            corner = x in (sx0, sx1) and z in (sz0, sz1)
            if corner or (x - sx0) % 6 == 0 and z in (sz0, sz1):
                for yy in range(y + 1, y + 5):
                    k.put(x, yy, z, "minecraft:spruce_log", {"axis": "y"})
    K.gable(k, sx0, y + 5, sz0, sx1, sz1, along="x", overhang=1, pearl=False, role="minecraft:spruce_stairs",
            slab_role="minecraft:spruce_slab", full_role="minecraft:spruce_planks")
    k.put(sx0 + 3, y + 1, sz1 - 1, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false",
                                                          "waterlogged": "false"})
    k.put(sx0 + 3, y + 2, sz1 - 1, "minecraft:cauldron")
    k.titik("kerja:kuali", sx0 + 3, y + 1, sz1 - 2, "south", {"peran": "KOKI"})
    K.counter(k, sx0 + 1, sx0 + 7, sz1 - 3, y, "north")
    for tx in range(sx0 + 11, sx1 - 1, 5):
        K.table(k, tx, y + 1, sz0 + 3)
        K.bench(k, tx + 1, sz0 + 2, "south", n=3)
        K.bench(k, tx - 1, sz0 + 4, "north", n=3)
        k.titik("kursi", tx, y + 1, sz0 + 2, "south")
        k.titik("kursi", tx, y + 1, sz0 + 4, "north")
    k.put(sx1 + 2, y + 1, sz0 + 1, "minecraft:water_cauldron", {"level": "3"})
    k.jangkar(sx0 + 4.5, y + 1, sz1 - 4.5, "kotabesar/layanan", yaw=0.0, layanan="warung", peran="KOKI")
    k.titik("jalan", x0 + 46, y + 1, 72, "east", {"urut": 1, "jalur": "jalan_timur"})
    k.kotak("bangunan", sx0, y, sz0, sx1, y + 10, sz1, "north", nama="warung_jalan")


def dapur_umum(k, x0, z0, y=LY):
    """Dapur Umum (38.B.3 kumuh): the Sky Temple's public kitchen - a long open hall on red posts under a tile roof with
    four great cauldrons over lit stoves, sacks of rice and millet, long tables with benches, a roped queue lane from
    the alley, a donation box and the monks' wash basin; the street children's corner."""
    km_ground(k, x0 + 1, z0 + 1, x0 + 46, z0 + 46)
    hx0, hz0, hx1, hz1 = x0 + 4, z0 + 6, x0 + 43, z0 + 26
    k.fill(hx0, y, hz0, hx1, y, hz1, "paving")
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            for yy in range(y + 1, y + 6):
                k.put(x, yy, z, "minecraft:air")
    for x in range(hx0, hx1 + 1, 6):
        for z in (hz0, hz1):
            for yy in range(y + 1, y + 6):
                k.put(x, yy, z, "red")
    for x in range(hx0, hx1 + 1):
        for z in (hz0,):
            for yy in range(y + 1, y + 4):
                if (x - hx0) % 6:
                    k.put(x, yy, z, "plaster")
    K.beam_band(k, hx0, hz0, hx1, hz1, y + 6)
    K.brackets(k, hx0, hz0, hx1, hz1, y + 6)
    K.gable(k, hx0, y + 7, hz0, hx1, hz1, along="x", overhang=1, pearl=True, role="tile_stairs", slab_role="tile_slab",
            full_role="tile")
    for i, x in enumerate(range(hx0 + 4, hx1 - 2, 9)):
        k.put(x, y + 1, hz0 + 2, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                       "waterlogged": "false"})
        k.put(x, y + 2, hz0 + 2, "minecraft:water_cauldron", {"level": "3"})
        for dy in range(4, 6):
            k.put(x, y + dy, hz0 + 2, "minecraft:bricks")
        k.titik("kerja:kuali", x, y + 1, hz0 + 3, "north", {"peran": "RAHIB"})
        K.crates(k, x + 2, hz0 + 1, x + 3, hz0 + 1, y + 1, h=2)
    K.counter(k, hx0 + 2, hx1 - 2, hz0 + 5, y, "south")
    for z in (hz0 + 9, hz0 + 14):
        for x in range(hx0 + 3, hx1 - 2):
            K.table(k, x, y + 1, z, "minecraft:white_carpet" if x % 2 else "")
        K.bench(k, hx1 - 3, z - 1, "south", n=hx1 - hx0 - 5)
        K.bench(k, hx0 + 3, z + 1, "north", n=hx1 - hx0 - 5)
        for x in range(hx0 + 4, hx1 - 3, 4):
            k.titik("kursi", x, y + 1, z - 1, "south")
            k.titik("kursi", x, y + 1, z + 1, "north")
    k.titik("meja_makan", (hx0 + hx1) // 2, y + 1, hz0 + 9, "south")
    k.jangkar((hx0 + hx1) / 2.0 + 0.5, y + 1, hz0 + 4.5, "kotabesar/layanan", yaw=0.0, layanan="dapur_umum", peran="RAHIB")
    # the queue lane and the donation box
    for x in range(hx0 + 10, hx1 - 9, 3):
        k.put(x, y + 1, hz1 + 3, "minecraft:spruce_fence")
        k.put(x, y + 1, hz1 + 6, "minecraft:spruce_fence")
    for i, x in enumerate(range(hx0 + 11, hx1 - 9, 4)):
        k.titik("jalan", x, y + 1, hz1 + 4, "west", {"urut": i, "jalur": "antre"})
    k.put(hx0 + 8, y + 1, hz1 + 4, "minecraft:chiseled_stone_bricks")
    k.put(hx0 + 8, y + 2, hz1 + 4, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    k.put(hx1 - 3, y + 1, hz1 + 4, "minecraft:water_cauldron", {"level": "3"})
    k.titik("kerja:sapu", hx1 - 4, y + 1, hz1 + 5, "north", {"peran": "RAHIB"})
    for x, z in ((x0 + 8, z0 + 38), (x0 + 14, z0 + 41), (x0 + 30, z0 + 40), (x0 + 38, z0 + 37)):
        k.titik("main", x, y + 1, z, "north", {"anak_jalanan": 1})
    for x in range(x0 + 20, x0 + 27):
        k.put(x, y + 1, z0 + 43, "minecraft:brown_carpet")
    K.tree(k, x0 + 42, y + 1, z0 + 42, "willow")
    K.dandan(k, (hx0 + 1, hz0 + 16, hx1 - 1, hz1 - 1), y, "makan", wall=False, titik_maks=2)
    k.kotak("bangunan", hx0, y, hz0, hx1, y + 12, hz1, "south", nama="dapur_umum")


# ===================================================================================================== luar
def luar_ground(k, x0, z0, x1, z1, y=LY):
    """Open meadow outside the wall: grass with patches of coarse dirt and podzol."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = (x * 92837111 ^ z * 689287499) & 0xFF
            if h < 200:
                k.put(x, y, z, "ground", {"snowy": "false"})
            elif h < 235:
                k.put(x, y, z, "minecraft:coarse_dirt")
            else:
                k.put(x, y, z, "ground_alt", {"snowy": "false"})


def build_luar():
    """Lapangan Kafilah (38.B.3, outside the Great South Gate): the Imperial Road leaving the capital with its gate
    forecourt, milestone and farewell pavilion; the caravan stables with four horses, the Caravan Post and its
    warehouse yard, the caravan camp, the roadside stalls with the wheelwright (kotabesar/tukang), fields and an
    orchard with the travellers' shrine."""
    k = kanvas("luar", h=40)
    luar_ground(k, 0, 0, 143, 143)
    K.imperial_avenue(k, 64, 79, 0, 143, lanterns=12, pines=24)
    gerbang_luar(k, *o(1, 0))
    tugu_jarak(k, *o(1, 1))
    paviliun_perpisahan(k, *o(1, 2))
    kandang_kuda(k, *o(0, 0))
    pos_kafilah(k, *o(2, 0))
    perkemahan(k, *o(0, 1))
    kios_jalan(k, *o(2, 1))
    ladang(k, *o(0, 2))
    kebun_dewa_jalan(k, *o(2, 2))
    names = {(0, 0): "kandang_kuda", (1, 0): "jalan_kafilah_utara", (2, 0): "pos_kafilah", (0, 1): "perkemahan",
             (1, 1): "jalan_kafilah", (2, 1): "kios_jalan", (0, 2): "ladang", (1, 2): "jalan_kafilah_selatan",
             (2, 2): "kebun_dewa_jalan"}
    simpan_kanvas(k, "luar", names, tinggi_min=20)


def gerbang_luar(k, x0, z0, y=LY):
    """The forecourt in front of the Great South Gate: a paved apron, two stone lions, two huabiao columns, a guard
    hut with a brazier stand (unlit), the toll rope posts, the arrival points of the road."""
    for x in range(x0 + 3, x0 + 45):
        for z in range(z0 + 0, z0 + 12):
            if not (16 <= x - x0 <= 31):
                k.put(x, y, z, "paving" if (x + z) % 5 else "kerb")
    K.lion(k, x0 + 14, y + 1, z0 + 4, "south")
    K.lion(k, x0 + 33, y + 1, z0 + 4, "south")
    K.huabiao(k, x0 + 12, y, z0 + 9, height=8)
    K.huabiao(k, x0 + 35, y, z0 + 9, height=8)
    hut = K.aula(k, x0 + 36, z0 + 14, x0 + 44, z0 + 22, h=4, podium_h=1, teras=0, roof="hip", door_sides=("west",),
                 door_w=1, tile=True)
    hx0, hz0, hx1, hz1 = hut["inner"]
    hf = hut["f"]
    K.bed(k, hx1, hf + 1, hz0, "north")
    k.titik("kasur", hx1, hf + 1, hz0, "south", {"peran": "PENJAGA"})
    K.table(k, hx0 + 1, hf + 1, hz1)
    K.chair(k, hx0 + 2, hf + 1, hz1, "west")
    k.jangkar(x0 + 34.5, y + 1, z0 + 18.5, "kota/penjaga", yaw=90.0)
    for z in range(z0 + 14, z0 + 30, 4):
        k.put(x0 + 15, y + 1, z, "wood_fence")
        k.put(x0 + 32, y + 1, z, "wood_fence")
    for x in range(x0 + 18, x0 + 30, 2):
        k.titik("tiba", x, y + 1, z0 + 2, "south")
    k.titik("jaga", x0 + 15, y + 1, z0 + 12, "south")
    k.titik("jaga", x0 + 32, y + 1, z0 + 12, "south")


def tugu_jarak(k, x0, z0, y=LY):
    """The milestone of the Imperial Road (a stone stele with a tile cap giving the distance to the next towns - the
    text is a client billboard), a horse trough, benches; the district zone anchor."""
    sx, sz = x0 + 13, z0 + 24
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            k.put(sx + dx, y, sz + dz, "kerb_chisel" if dx == 0 and dz == 0 else "kerb")
    for dy in range(1, 5):
        k.put(sx, y + dy, sz, "minecraft:polished_andesite")
        if dy < 4:
            k.put(sx, y + dy, sz + 1, "minecraft:polished_andesite")
    k.stairs(sx, y + 5, sz - 1, "tile_stairs", "south")
    k.stairs(sx, y + 5, sz + 1, "tile_stairs", "north")
    k.put(sx, y + 5, sz, "tile")
    for z in range(z0 + 20, z0 + 28):
        if z % 2:
            k.put(x0 + 34, y + 1, z, "minecraft:water_cauldron", {"level": "3"})
        else:
            K.barrel(k, x0 + 34, y + 1, z)
    K.bench(k, x0 + 10, z0 + 30, "east", n=3)
    k.titik("obrol", x0 + 10, y + 1, z0 + 31, "east")
    k.jangkar(x0 + 24.5, y + 1, z0 + 24.5, "kotabesar/zona", distrik="luar", protect=100)
    k.jangkar(sx + 1.5, y + 1, sz + 0.5, "kotabesar/layanan", yaw=90.0, layanan="tugu_jarak", peran="PENJAGA")


def paviliun_perpisahan(k, x0, z0, y=LY):
    """The Farewell Pavilion (changting) beside the road where travellers are seen off: an open hexagon-like pavilion of
    red columns under a two-tier roof, a stone table with stools, willows (the willow twig of parting), a stone stele
    at the edge of the capital's land."""
    px0, pz0 = x0 + 33, z0 + 18
    K.podium(k, px0, pz0, px0 + 10, pz0 + 10, y, 1, steps=("west",), step_w=3)
    K.colonnade(k, px0 + 1, pz0 + 1, px0 + 9, pz0 + 9, y + 1, 4, every=4)
    K.roof_over(k, px0 + 1, pz0 + 1, px0 + 9, pz0 + 9, y + 7, kind="hip", tiers=2, overhang=1, tile=True)
    k.put(px0 + 5, y + 2, pz0 + 5, "minecraft:polished_andesite")
    k.slab(px0 + 5, y + 3, pz0 + 5, "minecraft:polished_andesite_slab")
    for d, (dx, dz) in FACE.items():
        k.put(px0 + 5 + dx * 2, y + 2, pz0 + 5 + dz * 2, "kerb_wall")
        k.titik("kursi", px0 + 5 + dx * 2, y + 2, pz0 + 5 + dz * 2, OPP[d])
    for (tx, tz) in ((x0 + 36, z0 + 14), (x0 + 44, z0 + 32), (x0 + 8, z0 + 20)):
        K.tree(k, tx, y + 1, tz, "willow")
    sx, sz = x0 + 6, z0 + 40
    for dy in range(1, 6):
        k.put(sx, y + dy, sz, "minecraft:polished_andesite")
    k.slab(sx, y + 6, sz, "tile_slab")
    k.titik("obrol", px0 + 5, y + 2, pz0 + 3, "south")
    k.titik("jalan", x0 + 24, y + 1, z0 + 47, "south", {"urut": 9, "jalur": "kekaisaran"})


def kandang_kuda(k, x0, z0, y=LY):
    """The caravan stables: a long stable of eight stalls under a gable roof (hay, troughs), a fenced paddock with four
    untamed horses, the farrier's corner (anvil, grindstone), the hay barn."""
    sx0, sz0, sx1, sz1 = x0 + 4, z0 + 4, x0 + 40, z0 + 14
    k.fill(sx0, y, sz0, sx1, y, sz1, "minecraft:spruce_planks")
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            for yy in range(y + 1, y + 5):
                if z == sz0:
                    k.put(x, yy, z, "minecraft:spruce_planks" if (x - sx0) % 4 else "log", {"axis": "y"} if (x - sx0) % 4 == 0
                          else None)
                elif x in (sx0, sx1):
                    k.put(x, yy, z, "minecraft:spruce_planks")
                elif (x - sx0) % 4 == 0 and z < sz1 - 3:
                    k.put(x, yy, z, "minecraft:spruce_fence" if yy < y + 3 else "minecraft:air")
                elif z == sz1 and (x - sx0) % 4 == 0:
                    k.put(x, yy, z, "log", {"axis": "y"})
                else:
                    k.put(x, yy, z, "minecraft:air")
    for x in range(sx0 + 2, sx1 - 1, 4):
        k.put(x, y + 1, sz0 + 1, "minecraft:hay_block", {"axis": "y"})
        k.put(x + 1, y + 1, sz0 + 1, "minecraft:water_cauldron", {"level": "3"})
        k.put(x, y, sz0 + 4, "minecraft:coarse_dirt")
    K.beam_band(k, sx0, sz0, sx1, sz1, y + 5)
    K.gable(k, sx0, y + 6, sz0, sx1, sz1, along="x", overhang=1, pearl=False)
    for x in range(sx0 + 4, sx1, 8):
        K.lit(k, x, y + 5, sz1 - 2, chain=1)
    k.put(sx1 - 2, y + 1, sz1 - 1, "minecraft:anvil", {"facing": "east"})
    k.put(sx1 - 4, y + 1, sz1 - 1, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    k.titik("kerja:palu", sx1 - 3, y + 1, sz1 - 2, "south", {"peran": "TUKANG"})
    # paddock with four horses
    px0, pz0, px1, pz1 = x0 + 4, z0 + 18, x0 + 40, z0 + 42
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if x in (px0, px1) or z in (pz0, pz1):
                k.put(x, y + 1, z, "minecraft:oak_fence")
    k.put((px0 + px1) // 2, y + 1, pz0, "minecraft:oak_fence_gate", {"facing": "north", "in_wall": "false", "open": "false",
                                                                     "powered": "false"})
    for i, (hx, hz, var) in enumerate(((x0 + 12, z0 + 26, 0), (x0 + 22, z0 + 30, 2), (x0 + 30, z0 + 24, 4),
                                       (x0 + 18, z0 + 37, 1))):
        k.entity(hx + 0.5, y + 1, hz + 0.5, {"id": "minecraft:horse", "Variant": var + 256 * (i % 3), "Tame": False,
                                             "PersistenceRequired": True,
                                             "Rotation": [structgen.nbt.Float(90.0 * i), structgen.nbt.Float(0.0)]})
    for (tx, tz) in ((px0 + 3, pz1 - 3), (px1 - 3, pz0 + 3)):
        k.put(tx, y + 1, tz, "minecraft:hay_block", {"axis": "y"})
        k.put(tx + 1, y + 1, tz, "minecraft:water_cauldron", {"level": "3"})
    K.tree(k, x0 + 34, y + 1, z0 + 36, "pine")
    # hay barn
    for x in range(x0 + 42, x0 + 46):
        for z in range(z0 + 4, z0 + 14):
            for dy in range(1, 3 + (x + z) % 2):
                k.put(x, y + dy, z, "minecraft:hay_block", {"axis": "y"})
    k.titik("kerja:sapu", (px0 + px1) // 2, y + 1, pz0 - 2, "north", {"peran": "KULI"})
    k.kotak("bangunan", sx0, y, sz0, sx1, y + 12, sz1, "south", nama="kandang_kuda")


def pos_kafilah(k, x0, z0, y=LY):
    """Pos Kafilah (38.B.3, 38.M caravans): the caravan office (counter, a map table, the route board, the master's
    desk), a warehouse of crates and barrels, and the wagon yard with wheel ruts where caravans form up."""
    a = K.aula(k, x0 + 4, z0 + 4, x0 + 26, z0 + 20, h=5, podium_h=1, teras=1, roof="hip", tiers=2, door_sides=("west",),
               door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.counter_z(k, ix0 + 3, iz0 + 2, iz1 - 2, f, "west")
    k.put(ix1 - 2, f + 1, iz0 + 2, "minecraft:cartography_table")
    for z in range(iz0 + 4, iz0 + 9):
        for dy in (2, 3):
            k.put(ix1, f + dy, z, "minecraft:white_wool" if (z + dy) % 2 else "minecraft:birch_planks")
    K.table(k, ix1 - 3, f + 1, iz1 - 2, "minecraft:white_carpet")
    K.chair(k, ix1 - 2, f + 1, iz1 - 2, "west")
    k.titik("kerja:meja_tulis", ix1 - 2, f + 1, iz1 - 2, "west", {"peran": "PEDAGANG_KELILING"})
    k.jangkar(ix0 + 1.5, f + 1, (iz0 + iz1) / 2.0 + 0.5, "kotabesar/layanan", yaw=270.0, layanan="kafilah",
              peran="PEDAGANG_KELILING")
    K.dandan(k, (ix0 + 4, iz0, ix1 - 1, iz1), f, "kantor", titik_maks=1)
    # warehouse
    w = K.aula(k, x0 + 29, z0 + 4, x0 + 44, z0 + 24, h=6, podium_h=1, teras=0, roof="gable", along="z",
               door_sides=("south",), door_w=3, tile=True)
    wx0, wz0, wx1, wz1 = w["inner"]
    wf = w["f"]
    K.crates(k, wx0, wz0, wx1, wz0 + 3, wf + 1, h=3)
    K.crates(k, wx0, wz0 + 6, wx0 + 3, wz1 - 3, wf + 1, h=2)
    K.crates(k, wx1 - 3, wz0 + 6, wx1, wz1 - 3, wf + 1, h=2)
    K.dandan(k, (wx0, wz0, wx1, wz1), wf, "gudang", lorong="z", titik_maks=0)
    k.titik("kerja:rak", (wx0 + wx1) // 2, wf + 1, wz0 + 5, "north", {"peran": "KULI"})
    # wagon yard with ruts
    for x in range(x0 + 4, x0 + 45):
        for z in range(z0 + 28, z0 + 46):
            k.put(x, y, z, "minecraft:gravel" if (z - z0) % 6 in (1, 4) else "minecraft:dirt_path")
    for x in range(x0 + 6, x0 + 44, 9):
        k.titik("lapak", x, y + 1, z0 + 36, "north", {"gerobak": 1})
    K.crates(k, x0 + 40, z0 + 40, x0 + 43, z0 + 43, y + 1, h=2)
    k.kotak("bangunan", x0 + 4, y, z0 + 4, x0 + 26, f + 14, z0 + 20, "west", nama="pos_kafilah")


def perkemahan(k, x0, z0, y=LY):
    """The caravan camp: six cloth tents (wool on fence frames) with bedrolls, a ring of logs round an unlit fire pit,
    cooking pots, tethered packs, a lookout post."""
    tents = ((x0 + 5, z0 + 5), (x0 + 17, z0 + 5), (x0 + 29, z0 + 5), (x0 + 5, z0 + 32), (x0 + 17, z0 + 32),
             (x0 + 29, z0 + 32))
    cloths = ("minecraft:white_wool", "minecraft:brown_wool", "minecraft:light_gray_wool", "minecraft:red_wool")
    for i, (tx, tz) in enumerate(tents):
        face = "south" if i < 3 else "north"
        cl = cloths[i % len(cloths)]
        for x in range(tx, tx + 9):
            for z in range(tz, tz + 8):
                k.put(x, y, z, "minecraft:coarse_dirt")
        for z in range(tz, tz + 8):
            ridge = (tx + tx + 8) // 2
            for x in range(tx, tx + 9):
                hgt = 4 - abs(x - ridge)
                if hgt >= 1:
                    k.put(x, y + hgt, z, cl)
            if z in (tz, tz + 7):
                k.put(ridge, y + 1, z, "wood_fence")
                k.put(ridge, y + 2, z, "wood_fence")
                k.put(ridge, y + 3, z, "wood_fence")
        door_z = tz + 7 if face == "south" else tz
        for x in range(tx + 3, tx + 6):
            for dy in (1, 2):
                k.put(x, y + dy, door_z, "minecraft:air")
        for x in (tx + 2, tx + 6):
            k.put(x, y + 1, tz + 3, "minecraft:brown_carpet")
            k.put(x, y + 1, tz + 4, "minecraft:brown_carpet")
            k.titik("kasur", x, y + 1, tz + 3, face, {"kafilah": i})
        K.barrel(k, tx + 4, y + 1, tz + (1 if face == "south" else 6), "up")
    cx, cz = x0 + 24, z0 + 23
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            k.put(cx + dx, y, cz + dz, "minecraft:cobblestone")
    k.put(cx, y + 1, cz, "minecraft:campfire", {"facing": "north", "lit": "false", "signal_fire": "false",
                                                "waterlogged": "false"})
    for d, (dx, dz) in FACE.items():
        for w in (-1, 0, 1):
            px = cx + dx * 3 + (w if dz else 0)
            pz = cz + dz * 3 + (w if dx else 0)
            k.put(px, y + 1, pz, "minecraft:stripped_spruce_log", {"axis": "x" if dz else "z"})
        k.titik("kursi", cx + dx * 3, y + 2, cz + dz * 3, OPP[d])
    k.put(cx + 5, y + 1, cz, "minecraft:cauldron")
    k.titik("kerja:kuali", cx + 5, y + 1, cz + 1, "north")
    for z in range(z0 + 18, z0 + 29, 2):
        k.put(x0 + 42, y + 1, z, "wood_fence")
        if z % 4 == 0:
            k.put(x0 + 41, y + 1, z, "minecraft:barrel", {"facing": "east", "open": "false"})
    for dy in range(1, 8):
        k.put(x0 + 4, y + dy, z0 + 23, "log", {"axis": "y"})
    k.slab(x0 + 4, y + 8, z0 + 23, "wood_slab")
    k.titik("jaga", x0 + 5, y + 1, z0 + 23, "west")
    k.titik("obrol", cx - 3, y + 1, cz + 3, "north")


def kios_jalan(k, x0, z0, y=LY):
    """Roadside stalls along the Imperial Road (food, straw sandals, rope, water) and the wheelwright's yard
    (kotabesar/tukang): wheels drying on racks, an anvil, a stack of spokes, a cart under repair."""
    goods = (("minecraft:melon", "minecraft:pumpkin"), ("minecraft:hay_block",), ("minecraft:flower_pot",),
             ("minecraft:decorated_pot",), ("minecraft:barrel",))
    for i, z in enumerate(range(z0 + 4, z0 + 44, 9)):
        sx, sz = K.stall(k, x0 + 3, z, "west", w=3, cloth=("cloth", "cloth_alt") if i % 2 else
                         ("minecraft:yellow_wool", "minecraft:green_wool"), goods=goods[i % len(goods)])
        k.titik("lapak", sx, y + 1, sz, "west", {"jalan": i})
    wx0, wz0 = x0 + 18, z0 + 10
    for x in range(wx0, wx0 + 22):
        for z in range(wz0, wz0 + 24):
            k.put(x, y, z, "minecraft:coarse_dirt" if (x + z) % 3 else "minecraft:dirt_path")
    shed = K.aula(k, wx0 + 6, wz0, wx0 + 21, wz0 + 9, h=5, podium_h=1, teras=0, roof="gable", along="x",
                  door_sides=("south",), door_w=3, tile=False)
    ix0, iz0, ix1, iz1 = shed["inner"]
    sf = shed["f"]
    k.put(ix0 + 1, sf + 1, iz0, "minecraft:anvil", {"facing": "east"})
    k.put(ix0 + 3, sf + 1, iz0, "minecraft:smithing_table")
    for x in range(ix0 + 5, ix1):
        k.put(x, sf + 1, iz0, "minecraft:stripped_oak_log", {"axis": "x"})
    k.titik("kerja:palu", ix0 + 2, sf + 1, iz0 + 1, "north", {"peran": "TUKANG"})
    for z in range(wz0 + 13, wz0 + 22, 3):
        for dy in (1, 2, 3):
            k.put(wx0 + 2, y + dy, z, "minecraft:oak_fence")
        k.put(wx0 + 3, y + 2, z, "minecraft:stripped_spruce_wood", {"axis": "x"})
    for x in range(wx0 + 8, wx0 + 14):
        k.put(x, y + 1, wz0 + 17, "wood_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                                    "waterlogged": "false"})
    k.put(wx0 + 8, y + 1, wz0 + 18, "minecraft:barrel", {"facing": "south", "open": "false"})
    k.put(wx0 + 13, y + 1, wz0 + 18, "minecraft:barrel", {"facing": "south", "open": "false"})
    k.titik("kerja:gergaji", wx0 + 10, y + 1, wz0 + 19, "north", {"peran": "TUKANG"})
    k.jangkar(wx0 + 12.5, y + 1, wz0 + 12.5, "kotabesar/tukang", yaw=0.0, kampung="luar")
    k.kotak("bangunan", wx0 + 6, y, wz0, wx0 + 21, sf + 10, wz0 + 9, "south", nama="bengkel_roda")


def ladang(k, x0, z0, y=LY):
    """Fields of the capital's market gardeners: wheat, carrots and cabbages (beetroot) in strips with irrigation
    ditches, a scarecrow, a farmhouse and a tool shed."""
    crops = (("minecraft:wheat", 7), ("minecraft:carrots", 7), ("minecraft:beetroots", 3), ("minecraft:potatoes", 7))
    for i in range(4):
        fx0 = x0 + 4 + i * 10
        for x in range(fx0, fx0 + 8):
            for z in range(z0 + 4, z0 + 28):
                if x == fx0 + 4:
                    k.put(x, y, z, "minecraft:water", {"level": "0"})
                else:
                    k.put(x, y, z, "minecraft:farmland", {"moisture": "7"})
                    c, mx = crops[i]
                    k.put(x, y + 1, z, c, {"age": str(min(mx, (x + z) % (mx + 1)))})
        k.titik("kerja:cangkul", fx0 + 2, y + 1, z0 + 29, "north")
    k.put(x0 + 20, y + 1, z0 + 16, "wood_fence")
    k.put(x0 + 20, y + 2, z0 + 16, "minecraft:hay_block", {"axis": "y"})
    k.put(x0 + 20, y + 3, z0 + 16, "minecraft:carved_pumpkin", {"facing": "south"})
    k.put(x0 + 19, y + 2, z0 + 16, "wood_fence")
    k.put(x0 + 21, y + 2, z0 + 16, "wood_fence")
    K.tempel(k, rumah_kecil(tl._seed("luar/petani"), 20, 16, roof_tile=False, crop="minecraft:wheat"), x0 + 4, z0 + 31, 0)
    for x in range(x0 + 30, x0 + 40):
        for z in range(z0 + 34, z0 + 42):
            if x in (x0 + 30, x0 + 39) or z in (z0 + 34, z0 + 41):
                for dy in (1, 2, 3):
                    k.put(x, y + dy, z, "minecraft:oak_planks")
            k.put(x, y + 4, z, "minecraft:oak_slab", {"type": "bottom", "waterlogged": "false"})
    for dy in (1, 2):
        k.put(x0 + 34, y + dy, z0 + 34, "minecraft:air")


def kebun_dewa_jalan(k, x0, z0, y=LY):
    """An orchard of flowering trees beside the road and the travellers' shrine (Dewa Jalan): a small roofed altar
    where caravans burn incense before leaving, offering pots, stone lanterns; benches under the cherries."""
    for (tx, tz, kind) in ((x0 + 8, z0 + 8, "cherry"), (x0 + 18, z0 + 6, "cherry"), (x0 + 30, z0 + 9, "cherry"),
                           (x0 + 40, z0 + 7, "cherry"), (x0 + 10, z0 + 20, "pine"), (x0 + 40, z0 + 22, "cherry"),
                           (x0 + 8, z0 + 34, "cherry"), (x0 + 20, z0 + 40, "cherry"), (x0 + 40, z0 + 38, "pine")):
        K.tree(k, tx, y + 1, tz, kind)
    K.flowerbed(k, x0 + 14, z0 + 14, x0 + 20, z0 + 18, y)
    shrine = K.aula(k, x0 + 22, z0 + 20, x0 + 32, z0 + 28, h=4, podium_h=2, teras=1, roof="hip", door_sides=("west",),
                    door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = shrine["inner"]
    sf = shrine["f"]
    cz = (iz0 + iz1) // 2
    k.put(ix1, sf + 1, cz, "kerb")
    k.put(ix1, sf + 2, cz, "statue")
    K.candle(k, ix1, sf + 2, cz - 1, 3, "red")
    K.candle(k, ix1, sf + 2, cz + 1, 3, "red")
    k.put(ix1 - 1, sf + 1, cz, "minecraft:decorated_pot", {"facing": "west", "cracked": "false", "waterlogged": "false"})
    K.incense_burner(k, x0 + 19, y + 1, z0 + 24)
    k.titik("dupa", x0 + 18, y + 1, z0 + 24, "east")
    k.titik("meditasi", ix0 + 1, sf + 1, cz, "east")
    for (lx, lz) in ((x0 + 20, z0 + 19), (x0 + 20, z0 + 29)):
        K.stone_lantern(k, lx, y + 1, lz)
    K.bench(k, x0 + 9, z0 + 12, "south", n=3)
    K.bench(k, x0 + 22, z0 + 38, "north", n=3)
    k.titik("obrol", x0 + 9, y + 1, z0 + 12, "south")
    k.titik("obrol", x0 + 22, y + 1, z0 + 38, "north")
    k.jangkar(x0 + 18.5, y + 1, z0 + 26.5, "kotabesar/layanan", yaw=90.0, layanan="dewa_jalan", peran="RAHIB")
    k.kotak("bangunan", x0 + 22, y, z0 + 20, x0 + 32, sf + 10, z0 + 28, "west", nama="kuil_dewa_jalan")


# ===================================================================================================== JSON
BIOMES_TANAH = [
    "plains", "sunflower_plains", "snowy_plains", "desert", "swamp", "mangrove_swamp", "forest", "flower_forest",
    "birch_forest", "dappled_forest", "dark_forest", "pale_garden", "old_growth_birch_forest", "old_growth_pine_taiga",
    "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna", "savanna_plateau", "windswept_hills",
    "windswept_gravelly_hills", "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle", "bamboo_jungle",
    "badlands", "eroded_badlands", "wooded_badlands", "meadow", "cherry_grove", "grove", "snowy_slopes",
]
BIOMES_SEMUA_EXTRA = ["river", "frozen_river", "beach", "snowy_beach", "stony_shore", "lush_caves", "dripstone_caves",
                      "sulfur_caves", "jagged_peaks", "frozen_peaks", "stony_peaks", "ice_spikes"]
GAYA_BIOMES = {
    "salju": ["snowy_plains", "snowy_taiga", "grove", "snowy_slopes", "frozen_river", "snowy_beach", "ice_spikes",
              "old_growth_pine_taiga", "old_growth_spruce_taiga", "taiga", "frozen_peaks", "jagged_peaks"],
    "gurun": ["desert", "badlands", "eroded_badlands", "wooded_badlands", "savanna", "savanna_plateau", "windswept_savanna"],
    "sakura": ["cherry_grove"],
}


def tag(path, values):
    structgen.write_json(os.path.join(structgen.DATA, "tags", "worldgen", path + ".json"),
                         {"replace": False, "values": values})


def write_tags():
    tag("biome/kota_besar/tanah", ["minecraft:" + b for b in BIOMES_TANAH])
    tag("biome/has_structure/kota_besar/semua", ["#kultivator:kota_besar/tanah"] + ["minecraft:" + b for b in BIOMES_SEMUA_EXTRA])
    for g, bs in GAYA_BIOMES.items():
        tag("biome/kota_besar/gaya_" + g, ["minecraft:" + b for b in bs])
    tag("structure/kota_besar", ["%s%s/%s" % (NS, F, d) for d in ORDER])


def write_processors():
    states = tl.semua_state()
    tl.tulis_processor(F + "/lapuk_ringan", tl.processor_gaya(F, "dasar", states, lapuk=True))
    tl.tulis_processor(F + "/utuh", tl.processor_utuh())
    for g, spec in tl.GAYA[F].items():
        if spec["tag"] is None:
            continue
        tl.tulis_processor("%s/gaya_%s" % (F, g), tl.processor_gaya(F, g, states, lapuk=True))
        tl.tulis_processor("%s/gaya_%s_utuh" % (F, g), tl.processor_gaya(F, g, states, lapuk=False))


def write_pools():
    for pid, (templates, utuh) in sorted(POOLS.items()):
        base = NS + F + ("/utuh" if utuh else "/lapuk_ringan")
        name = "%s/%s" % (F, pid)
        weights = [1] * len(templates)
        tl.tulis_pool(name, [(tl.elemen(t, base), w) for t, w in zip(templates, weights)])
        for g, spec in tl.GAYA[F].items():
            if spec["tag"] is None:
                continue
            proc = "%s%s/gaya_%s%s" % (NS, F, g, "_utuh" if utuh else "")
            tl.tulis_pool("%s_%s" % (name, g), [(tl.elemen(t, proc), w) for t, w in zip(templates, weights)])


def write_structures():
    gaya = [("#kultivator:kota_besar/gaya_salju", "salju"), ("#kultivator:kota_besar/gaya_gurun", "gurun"),
            ("#kultivator:kota_besar/gaya_sakura", "sakura")]
    for n, d in enumerate(ORDER):
        lots = [{"dx": l["dx"], "dz": l["dz"], "pool": "%s%s/%s" % (NS, F, l["pool"]), "putar": l["putar"]}
                for l in LOTS[d]]
        s = tl.struktur_tata_letak("kota_besar", d, lots, "#kultivator:has_structure/kota_besar/semua", gaya=gaya)
        tl.tulis_wg("structure", "%s/%s" % (F, d), s)
        tl.tulis_wg("structure_set", "%s/%s" % (F, d),
                    tl.set_rencana("%s%s/%s" % (NS, F, d), "kota_besar", d, SALT0 + n), pack=PACK)
    pack_dir = os.path.join(structgen.REPO, "src", "main", "resources", "resourcepacks", PACK)
    os.makedirs(pack_dir, exist_ok=True)
    structgen.write_json(os.path.join(pack_dir, "pack.mcmeta"),
                         {"pack": {"description": {"translate": "kultivator.dunia.pack.kotabesar"},
                                   "min_format": 121, "max_format": 121}})


BUILDERS = [("pusat", build_pusat), ("istana", build_istana), ("cendekia", build_cendekia), ("awan", build_awan), ("gilda", build_gilda), ("pasar", build_pasar), ("hunian_barat", build_hunian_barat), ("gerbang", build_gerbang), ("arena", build_arena), ("kumuh", build_kumuh), ("luar", build_luar)]


def main(argv):
    only = [a for a in argv if not a.startswith("-")]
    build_walls()
    wall_lots()
    for name, fn in BUILDERS:
        if not only or name in only:
            fn()
    if only:
        # a partial run (preview while editing one district) must not rewrite pools/structures with the other
        # districts' lots missing; run the generator without arguments before shipping
        tl.cek_semua()
        print("kota_besar: partial run %s, NBT only (JSON untouched)" % ",".join(only))
        return
    write_tags()
    write_processors()
    write_pools()
    write_structures()
    tl.cek_semua()
    total = sum(os.path.getsize(p) for p, _ in structgen._SAVED)
    print("kota_besar: %d templates, %d pools, gzip %d bytes" % (len(structgen._SAVED), len(POOLS), total))


if __name__ == "__main__":
    main(sys.argv[1:])
