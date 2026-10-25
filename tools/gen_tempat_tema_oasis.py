"""Kota Oasis Gurun world generation (DESIGN 38.T.4, module 38TK-O): the oasis town's ten districts and its walls.

Writes every NBT lot (data/kultivator/structure/kota_tema/oasis/**), the template pools (base style "pasir" + style
"merah" for the badlands, tools/tempat_tema_lib.py GAYA_TEMA), the processor lists (worldgen/processor_list/
kota_tema/oasis/*) and the ten kultivator:tata_letak structures (worldgen/structure/kota_tema/oasis/*). Structure sets,
the shared biome tags and the pack dunia_kotatema are 38TK inti's (gen_tempat_tema_data.py, salts 380411-380420 from
Tema.java). Run:

    python tools/gen_tempat_tema_oasis.py        (then python tools/cek_tempat.py)

Plan (38.T.4.2, before rotation; districts 96 x 96 = 2 x 2 lots of 48; the gate is in the south wall):

             x = -1              x = 0               x = +1
 z = -1   [kuil_air]         [istana_pasir]      [kebun_palma]
 z =  0   [bazar_barat]      [mata_air]          [bazar_timur]
 z = +1   [hunian_tanah]     [gerbang_giok]      [karavanserai]
 z = +2                      [lapangan_kafilah]  (outside the Gerbang Giok)

Walls (rammed earth: packed mud core, mud-brick faces, 12 high, triangular merlons, domed corner towers) stand in a
band 144..156 blocks out from the town centre as extra lots of the outer districts: north and west bands off the 48-grid
(dz/dx = -60), south and east bands on it (dz/dx = 48) so cek_tempat's grid-hole check stays complete. The Gerbang Giok
(48 x 34 x 14: one arch 7 wide and 10 high lined with prismarine bricks, two domed watchtowers) is the south band of
district gerbang_giok, drawn on one canvas and cut into two halves. The caravan field starts 158 blocks south of the
centre (its lots sit at dz -34 / 14), clear of the gate. Routes used by dev.kultivator.dunia.tempat.tema.oasis.KafilahUnta
(grid blocks from the centre): road (0, 262), field (0, 210)/(0, 172), arch (0, 150), avenue x = 0 to the cross street
z = 96, east along it to the Karavanserai Agung west gate (56..62, 96) and its courtyard (78, 96).

Only world generation in NEW chunks ever places these templates (DESIGN 38.0 rule 1): camels, the shaduf beams, the
Batu Ukur Air marker, the gate leaves and the sand finds are noSave entities spawned from the anchors below (module
"tema_oasis", served by dev.kultivator.dunia.tempat.tema.oasis). No fire: every campfire is unlit, lamps are lanterns,
copper lanterns and sea lanterns. Deterministic: the same bytes on every run.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import tempat_tema_lib as ttl  # noqa: E402
import tema_hidup_kit as thk  # noqa: E402  (DESIGN 42 lots outside the wall, 42W-T)
import kota_besar_kit as K  # noqa: E402
from kota_besar_kit import FACE, OPP  # noqa: E402
import hias_tema_oi as HO  # noqa: E402  (registers the desert room styles; H below is the canvas height)

TEMA = "oasis"
F = "kota_tema/oasis"
LY = tl.LANTAI_Y
SEL = 96
H = 40
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}

ORDER = ["kuil_air", "istana_pasir", "kebun_palma", "bazar_barat", "mata_air", "bazar_timur", "hunian_tanah",
         "gerbang_giok", "karavanserai", "lapangan_kafilah"]

# ===================================================================================================== palette
PAL = dict(ttl.PALET_TEMA[TEMA])
PAL.update({
    "mud": "minecraft:packed_mud", "mudb": "minecraft:mud_bricks", "mudb_stairs": "minecraft:mud_brick_stairs",
    "mudb_slab": "minecraft:mud_brick_slab", "mudb_wall": "minecraft:mud_brick_wall",
    "ss": "minecraft:sandstone", "ss_smooth": "minecraft:smooth_sandstone", "ss_cut": "minecraft:cut_sandstone",
    "ss_chis": "minecraft:chiseled_sandstone", "ss_stairs": "minecraft:sandstone_stairs", "ss_slab": "minecraft:sandstone_slab",
    "ss_wall": "minecraft:sandstone_wall", "sss_stairs": "minecraft:smooth_sandstone_stairs",
    "sss_slab": "minecraft:smooth_sandstone_slab", "cut_slab": "minecraft:cut_sandstone_slab",
    "terra": "minecraft:terracotta", "terra_o": "minecraft:orange_terracotta", "terra_y": "minecraft:yellow_terracotta",
    "terra_w": "minecraft:white_terracotta", "terra_r": "minecraft:red_terracotta", "terra_b": "minecraft:brown_terracotta",
    "terra_c": "minecraft:cyan_terracotta", "glazed_c": "minecraft:cyan_glazed_terracotta",
    "glazed_lb": "minecraft:light_blue_glazed_terracotta", "glazed_y": "minecraft:yellow_glazed_terracotta",
    "jade": "minecraft:prismarine_bricks", "jade2": "minecraft:prismarine", "jade_dark": "minecraft:dark_prismarine",
    "jade_stairs": "minecraft:prismarine_brick_stairs", "jade_slab": "minecraft:prismarine_brick_slab",
    "brass": "minecraft:waxed_cut_copper", "brass_block": "minecraft:waxed_copper_block",
    "brass_lamp": "minecraft:waxed_copper_lantern", "sea": "minecraft:sea_lantern",
    "palm_log": "minecraft:jungle_log", "palm_wood": "minecraft:stripped_jungle_log",
    "wool_r": "minecraft:red_wool", "wool_o": "minecraft:orange_wool", "wool_y": "minecraft:yellow_wool",
    "wool_w": "minecraft:white_wool", "wool_b": "minecraft:brown_wool", "wool_k": "minecraft:black_wool",
    "wool_c": "minecraft:cyan_wool", "wool_lb": "minecraft:light_blue_wool", "wool_p": "minecraft:purple_wool",
    "wool_m": "minecraft:magenta_wool", "wool_g": "minecraft:green_wool",
    "carpet_r": "minecraft:red_carpet", "carpet_o": "minecraft:orange_carpet", "carpet_y": "minecraft:yellow_carpet",
    "carpet_w": "minecraft:white_carpet", "carpet_b": "minecraft:brown_carpet", "carpet_c": "minecraft:cyan_carpet",
    "carpet_p": "minecraft:purple_carpet", "carpet_lb": "minecraft:light_blue_carpet",
    "bed": "minecraft:orange_bed", "bed_alt": "minecraft:red_bed", "bed_w": "minecraft:white_bed",
})
TENDA = (("wool_o", "wool_y"), ("wool_r", "wool_w"), ("wool_r", "wool_y"), ("wool_c", "wool_w"), ("wool_o", "wool_r"))
PERMADANI = (("wool_r", "wool_o", "wool_y", "wool_k"), ("wool_r", "wool_b", "wool_y", "wool_w"),
             ("wool_c", "wool_w", "wool_y", "wool_r"), ("wool_p", "wool_m", "wool_y", "wool_k"))

# lots per district and pool templates
LOTS = {d: [] for d in ORDER}
POOLS = {}


def tid(name):
    return "kultivator:%s/%s" % (F, name)


def pid(name):
    return "%s/%s" % (F, name)


def kanvas(distrik, h=H, w=SEL, d=SEL):
    return K.Kanvas(w, h, d, seed=tl._seed(F + "/" + distrik), palette=PAL)


# rooms to dress (D-TK2 detail pass): registered while building, dressed in simpan() after everything else stands, so
# the dressing only ever fills what is still empty (hias_tema never overwrites a placed block)
_RUANG = []


def ruang(k, x0, z0, x1, z1, f, gaya, pulau=None, **kw):
    """Registers one room of canvas k (inner floor box, floor level f, room style of hias_tema / hias_tema_oi; `pulau`
    = furniture islands for the open middle of a large hall, see hias_tema_oi.tengah)."""
    _RUANG.append((k, (x0, z0, x1, z1, f, gaya), dict(kw, pulau=pulau)))


def hias(k):
    """Dresses every registered room of canvas k (called from simpan())."""
    n = 0
    for entry in [r for r in _RUANG if r[0] is k]:
        n += HO.kamar(k, *entry[1], **entry[2])
        _RUANG.remove(entry)
    return n


# ===================================================================================================== basics
def tanah(k, x0=0, z0=0, x1=None, z1=None, top="ground", dalam=LY):
    """The street floor at LY (sand by default). No foundation layers and no air above: tata_letak places the lots with
    beard_box and a ground delta of LY + 1, so the terrain is filled up to the floor and carved inside the lot box; only
    rooms keep explicit air (see ringkas()). This keeps the oasis under its gzip budget (cek_tempat 1.2 MB per theme)."""
    sx, _, sz = k.size
    x1 = sx - 1 if x1 is None else x1
    z1 = sz - 1 if z1 is None else z1
    k.fill(x0, LY, z0, x1, LY, z1, top)


def ringkas(k):
    """Budget pass before cutting: drops explicit air that the beard_box carving makes anyway (open sky, high in a
    room) and keeps it only in covered rooms up to four blocks over a floor, so dunes never fill a doorway."""
    sx, sy, sz = k.size
    solid_above = {}
    for (x, y, z), cell in k.cells.items():
        if cell[0][0] != "minecraft:air":
            key = (x, z)
            if y > solid_above.get(key, -1):
                solid_above[key] = y
    drop = []
    for (x, y, z), cell in k.cells.items():
        if cell[0][0] != "minecraft:air":
            continue
        top = solid_above.get((x, z), -1)
        if y > top or y > LY + 4 and top - y > 3 or y < LY:
            drop.append((x, y, z))
    for key in drop:
        del k.cells[key]


def cangkang(k, x0, x1, z0, z1, y0, y1, tebal=2):
    """Hollows a solid mass: removes the cells deeper than `tebal` from every face (the carved box keeps it air)."""
    filled = set()
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                c = k.get(x, y, z)
                if c is not None and c[0][0] != "minecraft:air":
                    filled.add((x, y, z))
    drop = []
    for (x, y, z) in filled:
        deep = True
        for ox in range(-tebal, tebal + 1):
            for oy in range(-tebal, tebal + 1):
                for oz in range(-tebal, tebal + 1):
                    if (x + ox, y + oy, z + oz) not in filled:
                        deep = False
                        break
                if not deep:
                    break
            if not deep:
                break
        if deep:
            drop.append((x, y, z))
    for key in drop:
        del k.cells[key]


def jalan_lot(k, i, j, sisi, lebar=tl.TEPI, role="paving", alt="paving_alt"):
    """The 3-wide edge road of lot (i, j) of a 2 x 2 district canvas on every side coded J (codes "NESW")."""
    x0, z0 = 48 * i, 48 * j
    x1, z1 = x0 + 47, z0 + 47
    for c, side in zip(sisi, "nesw"):
        if c != "J":
            continue
        if side == "n":
            box = (x0, z0, x1, z0 + lebar - 1)
        elif side == "s":
            box = (x0, z1 - lebar + 1, x1, z1)
        elif side == "w":
            box = (x0, z0, x0 + lebar - 1, z1)
        else:
            box = (x1 - lebar + 1, z0, x1, z1)
        K.paving(k, box[0], box[1], box[2], box[3], LY, role, alt, 0.12)
        # a gutter of smooth stone on the inner edge, oasis style: a thin water channel every 16
        if side in "ns":
            z = box[3] + 1 if side == "n" else box[1] - 1
            for x in range(box[0] + 3, box[2] - 2):
                if k.get(x, LY + 1, z) is None:
                    k.put(x, LY, z, "gutter")
        else:
            x = box[2] + 1 if side == "w" else box[0] - 1
            for z in range(box[1] + 3, box[3] - 2):
                if k.get(x, LY + 1, z) is None:
                    k.put(x, LY, z, "gutter")


def jalan_semua(k, sisi):
    for (i, j), code in sisi.items():
        jalan_lot(k, i, j, code)


def lampu_jalan(k, x, z, y=LY):
    """A brass lamp post: sandstone foot, wall shaft, copper lantern on top."""
    k.put(x, y + 1, z, "ss_chis")
    k.put(x, y + 2, z, "ss_wall")
    k.put(x, y + 3, z, "ss_wall")
    k.put(x, y + 4, z, "brass_lamp", {"hanging": "false", "waterlogged": "false"})


def lampu_gantung(k, x, y, z, chain=1):
    for i in range(chain):
        k.put(x, y - 1 - i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    k.put(x, y - 1 - chain, z, "brass_lamp", {"hanging": "true", "waterlogged": "false"})


def daun(k, x, y, z):
    if k.inside(x, y, z) and (k.get(x, y, z) is None or k.get(x, y, z)[0][0] == "minecraft:air"):
        k.put(x, y, z, "minecraft:jungle_leaves", {"persistent": "true", "distance": "1", "waterlogged": "false"})


def palma(k, x, z, y=LY + 1, h=None, condong=None, kurma=True):
    """A date palm: a slightly leaning jungle trunk, a star crown of drooping fronds and cocoa 'dates' under it."""
    h = h or 7 + k.rng.randrange(4)
    lean = condong if condong is not None else k.rng.choice(("n", "e", "s", "w", None))
    dx, dz = {"n": (0, -1), "e": (1, 0), "s": (0, 1), "w": (-1, 0), None: (0, 0)}[lean]
    cx, cz = x, z
    for i in range(h):
        if lean and i in (h // 2, h - 2):
            cx += dx
            cz += dz
        k.put(cx, y + i, cz, "palm_log", {"axis": "y"})
    top = y + h
    k.put(cx, top, cz, "palm_log", {"axis": "y"})
    daun(k, cx, top + 1, cz)
    for ax, az in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        length = 3 if ax and az else 4
        for s in range(1, length + 1):
            fy = top + (1 if s <= 1 else 0) - (1 if s >= length else 0)
            daun(k, cx + ax * s, fy, cz + az * s)
    if kurma:
        for face, (ox, oz) in (("west", (1, 0)), ("east", (-1, 0)), ("north", (0, 1)), ("south", (0, -1))):
            if k.rng.random() < 0.7:
                k.put(cx + ox, top - 1, cz + oz, "minecraft:cocoa", {"age": "2", "facing": face})


def semak(k, x, z, y=LY + 1):
    r = k.rng.random()
    if r < 0.35:
        k.put(x, y, z, "minecraft:short_dry_grass")
    elif r < 0.55:
        k.put(x, y, z, "minecraft:tall_dry_grass")
    elif r < 0.75:
        k.put(x, y, z, "minecraft:dead_bush")
    else:
        k.put(x, y, z, "minecraft:cactus")
        if k.rng.random() < 0.5:
            k.put(x, y + 1, z, "minecraft:cactus_flower")


def kubah(k, cx, cz, y, r, role="dome", top="gold", tebal=1, drum=0, drum_role="ss_cut", jendela=False):
    """A hemispherical dome of radius r on an optional drum of `drum` rings; a gold finial and a crescent spike."""
    for d in range(drum):
        k.ring(cx, y + d, cz, r, drum_role, thickness=1)
        if jendela and d == drum // 2:
            for a in range(0, 360, 45):
                wx = cx + int(round(math.cos(math.radians(a)) * r))
                wz = cz + int(round(math.sin(math.radians(a)) * r))
                k.put(wx, y + d, wz, "minecraft:yellow_stained_glass")
    base = y + drum
    for dy in range(0, r + 1):
        rr = math.sqrt(max(0.0, r * r - dy * dy))
        ro = int(round(rr))
        ri = int(round(rr)) - tebal
        for ox in range(-ro, ro + 1):
            for oz in range(-ro, ro + 1):
                dist = math.sqrt(ox * ox + oz * oz)
                if dist <= rr + 0.45 and (dist > ri - 0.2 or dy == r):
                    k.put(cx + ox, base + dy, cz + oz, role)
    k.put(cx, base + r + 1, cz, top)
    k.put(cx, base + r + 2, cz, "minecraft:end_rod", {"facing": "up"})
    return base + r


def menara_angin(k, x0, z0, w=5, h=16, y=LY, role="ss_cut"):
    """A wind catcher: a square shaft with vents (open trapdoors behind bars) on all sides near the top, a slab roof."""
    x1, z1 = x0 + w - 1, z0 + w - 1
    k.box(x0, y + 1, z0, x1, y + h, z1, role, hollow=True)
    for yy in range(y + 1, y + h, 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                if x in (x0, x1):
                    k.put(x, yy, z, "ss_chis")
    top = y + h
    for yy in (top - 3, top - 2, top - 1):
        for x in range(x0 + 1, x1):
            k.put(x, yy, z0, "minecraft:iron_bars")
            k.put(x, yy, z1, "minecraft:iron_bars")
        for z in range(z0 + 1, z1):
            k.put(x0, yy, z, "minecraft:iron_bars")
            k.put(x1, yy, z, "minecraft:iron_bars")
    k.fill(x0 - 1, top + 1, z0 - 1, x1 + 1, top + 1, z1 + 1, "cut_slab", {"type": "bottom", "waterlogged": "false"})
    k.fill(x0, top + 1, z0, x1, top + 1, z1, "ss_cut")
    k.put((x0 + x1) // 2, top + 2, (z0 + z1) // 2, "brass_block")


def parapet(k, x0, z0, x1, z1, y, role="mudb_wall", merlon="mudb", segitiga=True):
    """A roof parapet: low walls on the rim, every third a stepped (triangular) merlon."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            n = x + z
            if segitiga and n % 4 == 0:
                k.put(x, y, z, merlon)
                k.put(x, y + 1, z, "mudb_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                k.put(x, y, z, role)


def permadani_dinding(k, x0, y0, z, x1, y1, motif=0, sumbu="x"):
    """A hanging carpet on a wall: wool rows in a diamond motif with a border (sumbu x: along x at z; z: along z at x)."""
    cols = PERMADANI[motif % len(PERMADANI)]
    w = x1 - x0
    hgt = y1 - y0
    for a in range(w + 1):
        for b in range(hgt + 1):
            border = a in (0, w) or b in (0, hgt)
            mid = abs(a - w / 2.0) / max(1.0, w / 2.0) + abs(b - hgt / 2.0) / max(1.0, hgt / 2.0)
            role = cols[3] if border else cols[0] if mid > 0.75 else cols[1] if mid > 0.4 else cols[2]
            if sumbu == "x":
                k.put(x0 + a, y0 + b, z, role)
            else:
                k.put(z, y0 + b, x0 + a, role)


def tenda(k, x0, z0, x1, z1, y, warna=0, tiang=4, tinggi=4):
    """A striped bazaar awning: jungle fence posts every `tiang` blocks, a wool roof in alternating stripes with a
    ridge (one block higher in the middle) and fringe banners."""
    a, b = TENDA[warna % len(TENDA)]
    for x in range(x0, x1 + 1, tiang):
        for z in (z0, z1):
            for dy in range(1, tinggi + 1):
                k.put(x, y + dy, z, "wood_fence")
    if (x1 - x0) % tiang:
        for z in (z0, z1):
            for dy in range(1, tinggi + 1):
                k.put(x1, y + dy, z, "wood_fence")
    mz = (z0 + z1) // 2
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            lift = 1 if abs(z - mz) <= max(1, (z1 - z0) // 4) else 0
            k.put(x, y + tinggi + 1 + lift, z, a if (x // 2) % 2 == 0 else b)


def kios(k, x, z, facing, jenis="rempah", y=LY):
    """A bazaar stall of `jenis` (rempah, permadani, kaca, giok, logam, air, tamu): a counter of goods toward the lane,
    a back shelf, a striped awning; a lapak point at the seller's place."""
    goods = {
        "rempah": ["minecraft:composter", "minecraft:barrel", "minecraft:composter", "minecraft:decorated_pot"],
        "permadani": ["wool_r", "wool_o", "wool_y", "wool_p"],
        "kaca": ["minecraft:orange_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:yellow_stained_glass",
                 "minecraft:purple_stained_glass"],
        "giok": ["jade2", "minecraft:amethyst_block", "jade", "minecraft:emerald_block"],
        "logam": ["minecraft:anvil", "brass_block", "minecraft:iron_block", "brass"],
        "air": ["minecraft:blue_ice", "minecraft:water_cauldron", "minecraft:packed_ice", "minecraft:water_cauldron"],
        "tamu": ["minecraft:decorated_pot", "minecraft:chest", "minecraft:decorated_pot", "minecraft:barrel"],
    }[jenis]
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    w = 4
    for i in range(w):
        cx, cz = x + rx * i, z + rz * i
        g = goods[i % len(goods)]
        if g == "minecraft:composter":
            k.put(cx, y + 1, cz, g, {"level": "7"})
        elif g == "minecraft:water_cauldron":
            k.put(cx, y + 1, cz, g, {"level": "3"})
        elif g == "minecraft:anvil":
            k.put(cx, y + 1, cz, g, {"facing": facing})
        elif g in ("minecraft:barrel",):
            k.put(cx, y + 1, cz, g, {"facing": "up", "open": "true"})
        elif g == "minecraft:decorated_pot":
            k.put(cx, y + 1, cz, g, {"facing": facing, "cracked": "false", "waterlogged": "false"})
        elif g == "minecraft:chest":
            K.chest(k, cx, y + 1, cz, None, facing)
        else:
            k.put(cx, y + 1, cz, g)
        # back shelf with more goods
        bx, bz = cx - dx * 3, cz - dz * 3
        k.put(bx, y + 1, bz, "minecraft:barrel", {"facing": facing, "open": "false"})
        k.put(bx, y + 2, bz, "minecraft:jungle_shelf", {"facing": facing, "powered": "false", "side_chain": "unconnected",
                                                         "waterlogged": "false"})
    for i in (-1, w):
        for back in (0, 3):
            px, pz = x + rx * i - dx * back, z + rz * i - dz * back
            for dy in range(1, 4):
                k.put(px, y + dy, pz, "wood_fence")
    a, b = TENDA[(x + z) % len(TENDA)]
    for i in range(-1, w + 1):
        for back in range(-1, 4):
            px, pz = x + rx * i - dx * back, z + rz * i - dz * back
            k.put(px, y + 4 + (1 if back in (1, 2) else 0), pz, a if (i + back) % 2 == 0 else b)
    lampu_gantung(k, x + rx * 1 - dx * 1, y + 4, z + rz * 1 - dz * 1, 1)
    sx, sz = x + rx * 1 - dx * 1, z + rz * 1 - dz * 1
    k.titik("lapak", sx, y + 1, sz, facing, {"kios": jenis})
    return sx, sz


def pintu(k, x, y, z, facing, role="door"):
    K.door(k, x, y, z, facing, "left", role)


def jendela(k, x, y, z, facing):
    """A small high window: an open jungle trapdoor shutter on the outside of a hole."""
    k.put(x, y, z, "minecraft:air")
    dx, dz = FACE[facing]
    k.put(x + dx, y, z + dz, "wood_trapdoor", {"facing": facing, "half": "top", "open": "true", "powered": "false",
                                               "waterlogged": "false"})


def lengkung(k, x0, x1, z, y, h, sumbu="x", role="ss_cut", tangga="ss_stairs"):
    """An opening x0..x1 (or z0..z1 when sumbu z) h high in a wall at z (or x), crowned by an upside-down stair arch."""
    a, b = min(x0, x1), max(x0, x1)
    for t in range(a, b + 1):
        for yy in range(y + 1, y + h + 1):
            if sumbu == "x":
                k.put(t, yy, z, "minecraft:air")
            else:
                k.put(z, yy, t, "minecraft:air")
    # the curve: stairs on the shoulders, a keystone
    if sumbu == "x":
        k.stairs(a, y + h, z, tangga, "east", "top")
        k.stairs(b, y + h, z, tangga, "west", "top")
        for t in range(a + 1, b):
            k.put(t, y + h + 1, z, role)
        k.put((a + b) // 2, y + h + 1, z, "ss_chis")
    else:
        k.stairs(z, y + h, a, tangga, "south", "top")
        k.stairs(z, y + h, b, tangga, "north", "top")
        for t in range(a + 1, b):
            k.put(z, y + h + 1, t, role)
        k.put(z, y + h + 1, (a + b) // 2, "ss_chis")


def patung_duduk(k, x, y, z, facing, role="ss_smooth"):
    """A seated sandstone figure (Dunhuang niche statue): plinth, crossed legs, robed body, folded hands, head, halo."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    k.put(x, y, z, "ss_chis")
    k.put(x + rx, y, z + rz, "ss_cut")
    k.put(x - rx, y, z - rz, "ss_cut")
    k.stairs(x + dx, y + 1, z + dz, "sss_stairs", OPP[facing])
    k.stairs(x + rx + dx, y + 1, z + rz + dz, "sss_stairs", OPP[facing])
    k.stairs(x - rx + dx, y + 1, z - rz + dz, "sss_stairs", OPP[facing])
    k.put(x, y + 1, z, role)
    k.put(x, y + 2, z, role)
    k.stairs(x + rx, y + 2, z + rz, "sss_stairs", facing, "top")
    k.stairs(x - rx, y + 2, z - rz, "sss_stairs", facing, "top")
    k.put(x, y + 3, z, "ss_chis")
    k.put(x - dx, y + 3, z - dz, "minecraft:gold_block")
    k.put(x - dx + rx, y + 4, z - dz + rz, "cut_slab", {"type": "bottom", "waterlogged": "false"})
    k.put(x - dx - rx, y + 4, z - dz - rz, "cut_slab", {"type": "bottom", "waterlogged": "false"})
    k.put(x - dx, y + 4, z - dz, "cut_slab", {"type": "bottom", "waterlogged": "false"})


def bantal(k, x, y, z, warna="carpet_r"):
    k.put(x, y, z, warna)


def oven_tanah(k, x, z, y=LY, facing="south"):
    """A clay oven: mud-brick hump around a smoker (unlit), a chimney slab."""
    k.put(x, y + 1, z, "minecraft:smoker", {"facing": facing, "lit": "false"})
    for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if (ox, oz) != FACE[facing]:
            k.put(x + ox, y + 1, z + oz, "mudb")
    k.put(x, y + 2, z, "mudb")
    k.put(x, y + 3, z, "mudb_slab", {"type": "bottom", "waterlogged": "false"})
    k.titik("kerja:kuali", x + FACE[facing][0], y + 1, z + FACE[facing][1], OPP[facing])


def sumur(k, cx, cz, y=LY, anchor=True, antre=0):
    """A shaduf well: a cut-sandstone ring with water two deep, the post of the lifting beam (the beam itself is the
    timba_sumur entity), a stone trough; a sumur_timba point and the tema_oasis/timba anchor."""
    for ox in range(-2, 3):
        for oz in range(-2, 3):
            r = max(abs(ox), abs(oz))
            if r == 2:
                k.put(cx + ox, y, cz + oz, "ss_cut")
            elif r == 1:
                k.put(cx + ox, y + 1, cz + oz, "ss_wall" if abs(ox) == abs(oz) else "ss_cut")
                k.put(cx + ox, y, cz + oz, "ss")
    k.put(cx, y + 1, cz, "minecraft:air")
    for yy in (y - 2, y - 1, y):
        k.put(cx, yy, cz, "minecraft:water", {"level": "0"})
    k.put(cx, y - 3, cz, "ss")
    # the shaduf post two blocks off the well
    for dy in range(1, 5):
        k.put(cx + 3, y + dy, cz, "palm_log", {"axis": "y"})
    k.put(cx + 3, y + 5, cz, "wood_fence")
    # trough
    for oz in (-3, -2, -1):
        k.put(cx - 3, y + 1, cz + oz, "minecraft:water_cauldron", {"level": "3"})
    k.titik("sumur_timba", cx + 1, y + 1, cz + 1, "north")
    if anchor:
        k.jangkar(cx + 3.5, y + 1, cz + 0.5, "tema_oasis/timba", yaw=90.0, antre=antre)


def kursi_lantai_lingkar(k, cx, cz, y, r=2, warna="carpet_r", titik=True):
    """Floor cushions in a ring around a low table (tea and dice in the heat break)."""
    K.low_table(k, cx, y, cz)
    for face, (dx, dz) in FACE.items():
        k.put(cx + dx * r, y, cz + dz * r, warna)
        if titik:
            k.titik("kursi_lantai", cx + dx * r, y, cz + dz * r, OPP[face])


def zona(k, x, z, distrik, protect=72, y=LY + 1):
    k.jangkar(x + 0.5, y, z + 0.5, "kota_tema/zona", yaw=0.0, distrik=distrik, protect=protect)


# ===================================================================================================== houses
def rumah_datar(k, x0, z0, x1, z1, pintu_sisi="south", f=LY, h=5, atap_kasur=2, tangga="east", nama="rumah", warna=0,
                halaman=True, dapur=True, peti=True):
    """A flat-roofed mud house (38.T.4.2 hunian): mud-brick walls on a sandstone plinth, a cool central room with floor
    cushions and a wall carpet, a sleeping alcove, a clay oven by the door, an outside stair to the roof terrace where
    beds stand under a cloth awning for the hot nights (kasur points outdoors), a parapet with stepped merlons."""
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + h, z1, role="mudb", pillar="mud", windows=0)
    k.fill(x0, f + h + 1, z0, x1, f + h + 1, z1, "mud")
    k.fill(x0 + 1, f + h + 1, z0 + 1, x1 - 1, f + h + 1, z1 - 1, "ss_smooth")
    parapet(k, x0, z0, x1, z1, f + h + 2)
    # floor inside
    k.fill(x0 + 1, f, z0 + 1, x1 - 1, f, z1 - 1, "terra_o" if warna % 2 == 0 else "terra_y")
    K.rug(k, x0 + 2, z0 + 2, x1 - 2, z1 - 2, f + 1, "carpet_r" if warna % 2 == 0 else "carpet_o", "carpet_y")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    K.low_table(k, cx, f + 1, cz)
    for face, (dx, dz) in FACE.items():
        if 0 < cx + dx * 2 - x0 < x1 - x0 and 0 < cz + dz * 2 - z0 < z1 - z0:
            k.put(cx + dx * 2, f + 1, cz + dz * 2, "carpet_w")
            k.titik("kursi_lantai", cx + dx * 2, f + 1, cz + dz * 2, OPP[face])
    lampu_gantung(k, cx, f + h + 1, cz, 1)
    # door and windows
    if pintu_sisi == "south":
        dxp, dzp = cx, z1
    elif pintu_sisi == "north":
        dxp, dzp = cx, z0
    elif pintu_sisi == "west":
        dxp, dzp = x0, cz
    else:
        dxp, dzp = x1, cz
    pintu(k, dxp, f + 1, dzp, pintu_sisi)
    k.put(dxp, f + 3, dzp, "ss_chis")
    k.titik("pintu", dxp + FACE[pintu_sisi][0], f + 1, dzp + FACE[pintu_sisi][1], pintu_sisi)
    for x in range(x0 + 2, x1 - 1, 3):
        if abs(x - dxp) > 1:
            jendela(k, x, f + 3, z0, "north")
            jendela(k, x, f + 3, z1, "south")
    for z in range(z0 + 2, z1 - 1, 3):
        if abs(z - dzp) > 1:
            jendela(k, x0, f + 3, z, "west")
            jendela(k, x1, f + 3, z, "east")
    # wall carpet on the back wall
    back = z0 + 1 if pintu_sisi == "south" else z1 - 1 if pintu_sisi == "north" else None
    if back is not None and x1 - x0 >= 6:
        permadani_dinding(k, cx - 2, f + 2, back, cx + 2, f + 4, motif=warna)
    # sleeping alcove: bed and chest in a corner
    bx = x0 + 1
    bz = z0 + 2 if pintu_sisi != "north" else z1 - 2
    K.bed(k, bx, f + 1, bz, "north" if pintu_sisi != "north" else "south", "bed" if warna % 2 else "bed_alt")
    k.titik("kasur", bx, f + 1, bz, "east")
    if peti:
        K.chest(k, x1 - 1, f + 1, z0 + 1 if pintu_sisi != "north" else z1 - 1, None, "west")
        K.barrel(k, x1 - 1, f + 1, z0 + 2 if pintu_sisi != "north" else z1 - 2, "west")
        k.put(x1 - 1, f + 2, z0 + 2 if pintu_sisi != "north" else z1 - 2, "minecraft:decorated_pot",
              {"facing": "west", "cracked": "false", "waterlogged": "false"})
    # outside stair to the roof along one wall
    if tangga == "east":
        sx = x1 + 1
        for i in range(h + 1):
            zz = z1 - i
            if zz <= z0:
                break
            k.stairs(sx, f + 1 + i, zz, "ss_stairs", "north")
            for yy in range(f + 1, f + 1 + i):
                k.put(sx, yy, zz, "mud")
    elif tangga == "west":
        sx = x0 - 1
        for i in range(h + 1):
            zz = z1 - i
            if zz <= z0:
                break
            k.stairs(sx, f + 1 + i, zz, "ss_stairs", "north")
            for yy in range(f + 1, f + 1 + i):
                k.put(sx, yy, zz, "mud")
    # roof terrace: beds under an awning on posts, a water jar, a carpet
    ry = f + h + 2
    a, b = TENDA[warna % len(TENDA)]
    ax0, az0 = x0 + 1, z0 + 1
    ax1, az1 = min(x1 - 1, x0 + 5), min(z1 - 1, z0 + 4)
    for x, z in ((ax0, az0), (ax1, az0), (ax0, az1), (ax1, az1)):
        k.put(x, ry, z, "wood_fence")
        k.put(x, ry + 1, z, "wood_fence")
    for x in range(ax0, ax1 + 1):
        for z in range(az0, az1 + 1):
            k.put(x, ry + 2, z, a if (x + z) % 2 == 0 else b)
    for n in range(atap_kasur):
        bx2 = ax0 + 1 + n * 2
        if bx2 >= ax1:
            break
        K.bed(k, bx2, ry, az0 + 2, "north", "bed_w")
        k.titik("kasur", bx2, ry, az0 + 2, "south", {"luar": "1"})
    k.put(x1 - 1, ry, z1 - 1, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
    k.put(x1 - 2, ry, z1 - 1, "carpet_y")
    if dapur:
        ox = x0 + 2 if pintu_sisi in ("south", "north") else dxp + (2 if pintu_sisi == "east" else -2)
        oz = (z1 + 2) if pintu_sisi == "south" else (z0 - 2) if pintu_sisi == "north" else cz + 3
        if k.inside(ox, f + 1, oz):
            oven_tanah(k, ox, oz, f, "east")
    k.kotak("bangunan", x0, f, z0, x1, f + h + 3, z1, "south", nama=nama)
    return dxp, dzp


def halaman_rumah(k, x0, z0, x1, z1, y=LY, pohon=True, sumur_kecil=False):
    """A packed-earth courtyard with a low mud wall, a palm or two, jars and a bench (house yards, inns)."""
    k.fill(x0, y, z0, x1, y, z1, "minecraft:coarse_dirt")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x * 7 + z * 3) % 11 == 0:
                k.put(x, y, z, "mud")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if (x - x0) % 5 != 2:
                k.put(x, y + 1, z, "mudb_wall")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if (z - z0) % 5 != 2:
                k.put(x, y + 1, z, "mudb_wall")
    if pohon:
        palma(k, x0 + 2, z0 + 2, y + 1, 6 + (x0 + z0) % 3)
    for i in range(2):
        k.put(x1 - 1 - i, y + 1, z1 - 1, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
    if sumur_kecil:
        k.put((x0 + x1) // 2, y, (z0 + z1) // 2, "minecraft:water", {"level": "0"})
        k.put((x0 + x1) // 2, y + 1, (z0 + z1) // 2, "minecraft:air")


# ===================================================================================================== saving
def simpan(k, distrik, names, sisi, tinggi_min=24):
    """Cuts a district canvas into its four lots (side codes "NESW" before rotation), saves them, writes one pool per
    lot (base + styles) and records the structure entries."""
    hias(k)
    ringkas(k)
    lots = ttl.potong_distrik(k, TEMA, distrik, nama={ij: n for ij, n in names.items()}, sisi=sisi, tinggi_min=tinggi_min)
    for ij, lot in sorted(lots.items()):
        lot.simpan()
        name = "%s/%s" % (distrik, names[ij])
        POOLS[pid(name)] = [lot.id]
        LOTS[distrik].append(ttl.lot_inti(ij[0], ij[1], pid(name)))
    return lots


def potong_pita(k, nama, x0, x1, z0, z1, tinggi=None):
    """Cuts a band of canvas k (x0..x1, z0..z1 inclusive) into one wall template kota_tema/oasis/<nama>."""
    w, d = x1 - x0 + 1, z1 - z0 + 1
    ringkas(k)
    K.connect_all(k)
    top = 0
    for (x, y, z), cell in k.cells.items():
        if x0 <= x <= x1 and z0 <= z <= z1 and cell[0][0] != "minecraft:air":
            top = max(top, y)
    hgt = tinggi or min(k.size[1], top + 2)
    lot = tl.Lot(F, nama, tinggi=hgt, lebar=w, dalam=d, palet=PAL)
    for (x, y, z), cell in k.cells.items():
        if x0 <= x <= x1 and z0 <= z <= z1 and y < hgt:
            lot.cells[(x - x0, y, z - z0)] = cell
    for (x, y, z, nbt) in k.entities:
        if x0 <= x < x1 + 1 and z0 <= z < z1 + 1:
            lot.entities.append((x - x0, y, z - z0, nbt))
            if nbt.get("id") == "kultivator:dunia_anchor":
                lot.jangkar_n += 1
    for t in k.titik_list:
        px, py, pz = t["pos"]
        if x0 <= px <= x1 and z0 <= pz <= z1 and py < hgt:
            e = dict(t)
            e["pos"] = [px - x0, py, pz - z0]
            lot.titik_list.append(e)
    lot.simpan()
    return lot


def pool_tembok(nama, template_id):
    POOLS[pid(nama)] = [template_id]


def tembok_lot(distrik, dx, dz, nama, putar):
    LOTS[distrik].append(ttl.lot_luar(dx, dz, pid(nama), putar))


# ===================================================================================================== kuil_air
GUA_Z = (4, 19, 34, 49, 64, 79)        # north edge of each niche (7 wide along z)


def tebing(k):
    """Tebing Seribu Lukisan: a man-made sandstone cliff 96 long and 30 high facing east, banded with terracotta, its
    face cut with 12 niche caves in two rows (7 x 6 x 9: a seated figure, a painted back wall = client mural, cushions)
    reached by a palm-wood scaffold of stairs and balconies; murals numbered 1-6 below (north to south), 7-12 above."""
    face = 30                                  # x of the cliff face
    for z in range(0, 96):
        for x in range(1, face + 1):
            # the top steps back toward the west: a ragged skyline
            h = 30 - max(0, (x - 18)) // 2 - (1 if (z * 7 + x) % 9 == 0 else 0)
            for y in range(LY, LY + h + 1):
                band = (y - LY) % 7
                role = "ss_smooth" if band < 4 else "ss_cut" if band == 4 else "terra_w" if band == 5 else "terra_o"
                if (x + y * 3 + z) % 23 == 0:
                    role = "ss"
                k.put(x, y, z, role)
    # sand scree at the foot
    for z in range(0, 96):
        for x in range(face + 1, face + 3):
            if (z * 5 + x) % 3:
                k.put(x, LY + 1, z, "minecraft:sand")
    cangkang(k, 1, face, 0, 95, LY + 1, LY + 31, 2)
    no = 0
    for row, fy in ((0, LY + 1), (1, LY + 11)):
        for idx, z0 in enumerate(GUA_Z):
            no += 1
            gua(k, face, z0, fy, no)
    # scaffold: a balcony at the upper row, stairs at both ends, jungle-log posts
    by = LY + 10
    for z in range(2, 94):
        for x in (face + 1, face + 2, face + 3):
            k.put(x, by, z, "wood_slab", {"type": "top", "waterlogged": "false"})
        k.put(face + 4, by + 1, z, "wood_fence")
        if z % 6 == 2:
            for y in range(LY + 1, by):
                k.put(face + 3, y, z, "palm_log", {"axis": "y"})
    # stairs: north end going up southward, south end going up northward
    for i in range(by - LY):
        k.stairs(face + 4, LY + 1 + i, 2 + i, "wood_stairs", "south")
        k.stairs(face + 4, LY + 1 + i, 93 - i, "wood_stairs", "north")
    for z in range(2, 2 + by - LY):
        k.put(face + 4, by + 1, z, "minecraft:air")
    for z in range(93 - (by - LY) + 1, 94):
        k.put(face + 4, by + 1, z, "minecraft:air")
    for z in range(8, 90, 12):
        lampu_gantung(k, face + 2, by, z, 1)
    # painters' scaffolds (ladders and planks) in front of four niches
    for z0 in (GUA_Z[1], GUA_Z[4]):
        k.fill(face + 1, LY + 6, z0 + 2, face + 2, LY + 6, z0 + 4, "wood_slab", {"type": "bottom", "waterlogged": "false"})
        K.ladder(k, face + 3, LY + 1, LY + 6, z0 + 3, "east")


def gua(k, face, z0, fy, no):
    """One niche cave 9 deep into the cliff: arch mouth, painted back wall (white plaster framed in cyan and gold glaze),
    a seated figure on a plinth, cushions, candles; points: pandang (mural=no), meditasi, and kerja:kuas at 4 niches."""
    x0 = face - 8
    z1 = z0 + 6
    k.air(x0, fy, z0, face, fy + 5, z1)
    k.fill(x0, fy - 1, z0, face, fy - 1, z1, "ss_cut")
    # back wall = the painting
    for z in range(z0, z1 + 1):
        for y in range(fy, fy + 6):
            k.put(x0 - 1, y, z, "terra_w")
    for z in range(z0, z1 + 1):
        k.put(x0 - 1, fy + 5, z, "glazed_c", {"facing": "east"})
        k.put(x0 - 1, fy, z, "glazed_y", {"facing": "east"})
    # ceiling coffer painted in blue/gold
    for x in range(x0, face):
        for z in range(z0, z1 + 1):
            k.put(x, fy + 6, z, "terra_c" if (x + z) % 2 else "terra_w")
    k.put((x0 + face) // 2, fy + 6, (z0 + z1) // 2, "minecraft:gold_block")
    # mouth: a pointed arch narrowing the opening
    for z in (z0, z1):
        for y in range(fy, fy + 6):
            k.put(face, y, z, "ss_cut")
    k.stairs(face, fy + 5, z0 + 1, "ss_stairs", "south", "top")
    k.stairs(face, fy + 5, z1 - 1, "ss_stairs", "north", "top")
    k.put(face, fy + 5, (z0 + z1) // 2, "ss_chis")
    # figure, cushions, candles
    cz = (z0 + z1) // 2
    patung_duduk(k, x0 + 1, fy, cz, "east")
    for z in (z0 + 1, z1 - 1):
        K.candle(k, x0 + 1, fy, z, 3, "white")
    k.put(x0 + 5, fy, cz, "carpet_r")
    k.put(x0 + 5, fy, cz - 1, "carpet_o")
    k.put(x0 + 5, fy, cz + 1, "carpet_o")
    k.titik("pandang", x0, fy + 1, cz, "west", {"mural": no})
    k.titik("meditasi", x0 + 5, fy, cz, "west")
    if no in (2, 5, 8, 11):
        k.titik("kerja:kuas", x0 + 1, fy, z0 + 1, "west")
    k.kotak("bangunan", x0 - 1, fy - 1, z0, face, fy + 6, z1, "east", nama="gua_%d" % no)
    if no == 12:
        k.jangkar(x0 + 4.5, fy, cz + 0.5, "tema_oasis/gua12", yaw=YAW["west"])


def kuil(k):
    """The Kuil Air (lot 1,0): a domed hall of smooth sandstone on a plinth, cyan tile band, a south arch; inside the
    holy pool (water over sea lanterns) ringed with prismarine, the Batu Ukur Air stela at its north rim, columns with
    tile capitals, meditation cushions and incense; four corner domes."""
    x0, z0, x1, z1 = 54, 6, 88, 40
    f = LY + 2
    # plinth with steps on the south
    k.fill(x0 - 1, LY + 1, z0 - 1, x1 + 1, f, z1 + 1, "ss_cut")
    for i, x in enumerate(range(66, 77)):
        k.stairs(x, LY + 1, z1 + 2, "sss_stairs", "north")
        k.stairs(x, LY + 2, z1 + 1, "sss_stairs", "north")
    hgt = 10
    k.walls(x0, f + 1, z0, x1, f + hgt, z1, role="ss_smooth", pillar="ss_chis", windows=0)
    k.fill(x0, f, z0, x1, f, z1, "ss_smooth")
    # tile band and pilasters
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            k.put(x, f + 7, z, "glazed_c", {"facing": "south"})
            if (x - x0) % 5 == 0:
                for y in range(f + 1, f + hgt + 1):
                    k.put(x, y, z, "ss_cut")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            k.put(x, f + 7, z, "glazed_c", {"facing": "east"})
            if (z - z0) % 5 == 0:
                for y in range(f + 1, f + hgt + 1):
                    k.put(x, y, z, "ss_cut")
    # pointed-arch windows high up
    for x in range(x0 + 5, x1 - 3, 5):
        for z in (z0, z1):
            k.put(x + 2, f + 5, z, "minecraft:yellow_stained_glass_pane")
            k.put(x + 2, f + 6, z, "minecraft:yellow_stained_glass_pane")
    # entrance arch (south)
    lengkung(k, 68, 74, z1, f, 7, "x", "ss_cut", "sss_stairs")
    k.put(71, f + 9, z1, "glazed_lb", {"facing": "south"})
    k.titik("pintu", 71, f + 1, z1 + 1, "south")
    # roof slab and dome
    k.fill(x0, f + hgt + 1, z0, x1, f + hgt + 1, z1, "ss_cut")
    parapet(k, x0, z0, x1, z1, f + hgt + 2, role="ss_wall", merlon="ss_cut")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    kubah(k, cx, cz, f + hgt + 2, 12, role="dome", top="gold", drum=3, drum_role="ss_cut", jendela=True)
    for ox, oz in ((x0 + 4, z0 + 4), (x1 - 4, z0 + 4), (x0 + 4, z1 - 4), (x1 - 4, z1 - 4)):
        kubah(k, ox, oz, f + hgt + 2, 3, role="ss_smooth", top="brass_block", drum=1)
    # interior: the holy pool
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + hgt, z1 - 1)
    px0, pz0, px1, pz1 = cx - 7, cz - 7, cx + 7, cz + 7
    for x in range(px0 - 1, px1 + 2):
        for z in range(pz0 - 1, pz1 + 2):
            edge = x in (px0 - 1, px1 + 1) or z in (pz0 - 1, pz1 + 1)
            if edge:
                k.put(x, f, z, "jade")
                k.put(x, f + 1, z, "jade_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                k.put(x, f - 2, z, "sea" if (x + z) % 4 == 0 else "minecraft:sand")
                k.put(x, f - 1, z, "minecraft:water", {"level": "0"})
                k.put(x, f, z, "minecraft:water", {"level": "0"})
    for x, z in ((px0 + 2, pz0 + 3), (px1 - 3, pz1 - 2), (px0 + 5, pz1 - 4)):
        k.put(x, f + 1, z, "minecraft:lily_pad")
    # Batu Ukur Air: prismarine stela with cut marks at the north rim (the marker is an entity)
    for y in range(f + 1, f + 6):
        k.put(cx, y, pz0 - 2, "jade2" if y % 2 else "jade_dark")
    k.put(cx, f + 6, pz0 - 2, "minecraft:gold_block")
    for y in (f + 2, f + 4):
        k.stairs(cx - 1, y, pz0 - 2, "jade_stairs", "west")
        k.stairs(cx + 1, y, pz0 - 2, "jade_stairs", "east")
    k.jangkar(cx + 0.5, f + 1, pz0 - 0.5, "tema_oasis/batu_ukur", yaw=YAW["south"])
    k.jangkar(cx + 0.5, f + 2, pz1 + 2.5, "tema_oasis/kuil_air", yaw=YAW["north"], r=8)
    # columns in two rings
    for x in (x0 + 4, x0 + 10, x1 - 10, x1 - 4):
        for z in (z0 + 4, z0 + 10, z1 - 10, z1 - 4):
            if px0 - 2 <= x <= px1 + 2 and pz0 - 2 <= z <= pz1 + 2:
                continue
            for y in range(f + 1, f + hgt):
                k.put(x, y, z, "ss_chis" if y in (f + 1, f + hgt - 1) else "ss_smooth")
            k.put(x, f + hgt, z, "glazed_c", {"facing": "north"})
    # cushions and incense around the pool
    for face, (ox, oz) in FACE.items():
        for s in (-4, 0, 4):
            x = cx + ox * 10 + (0 if ox else s)
            z = cz + oz * 10 + (0 if oz else s)
            if face == "north" and abs(s) == 0:
                continue
            k.put(x, f + 1, z, "carpet_lb")
            k.titik("meditasi", x, f + 1, z, OPP[face])
    for x, z in ((px0 - 3, pz0 - 3), (px1 + 3, pz0 - 3), (px0 - 3, pz1 + 3), (px1 + 3, pz1 + 3)):
        K.incense_burner(k, x, f + 1, z)
        k.titik("dupa", x, f + 1, z, "north")
    for x in range(x0 + 6, x1 - 4, 7):
        lampu_gantung(k, x, f + hgt + 1, cz - 10, 2)
        lampu_gantung(k, x, f + hgt + 1, cz + 10, 2)
    # water jars and the priests' bench along the walls
    for z in range(z0 + 2, z1 - 1, 4):
        k.put(x0 + 1, f + 1, z, "minecraft:decorated_pot", {"facing": "east", "cracked": "false", "waterlogged": "false"})
        k.put(x1 - 1, f + 1, z, "minecraft:water_cauldron", {"level": "3"})
    k.kotak("bangunan", x0, f, z0, x1, f + hgt + 16, z1, "south", nama="kuil_air")


def asrama(k):
    """Lot 1,1: the priests' dormitory (rooms around a shaded court) and the shaduf well where the water queue forms."""
    x0, z0, x1, z1 = 54, 54, 90, 76
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 5, z1, role="ss_smooth", pillar="ss_cut", windows=0)
    k.fill(x0, f + 6, z0, x1, f + 6, z1, "ss_cut")
    parapet(k, x0, z0, x1, z1, f + 7, role="ss_wall", merlon="ss_cut")
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 5, z1 - 1)
    # inner partition walls: six cells along the north, a refectory to the south
    for x in range(x0 + 6, x1, 6):
        for z in range(z0 + 1, z0 + 9):
            for y in range(f + 1, f + 6):
                k.put(x, y, z, "ss_smooth")
        K.door(k, x - 3, f + 1, z0 + 9, "south")
    for x in range(x0 + 1, x1):
        for y in range(f + 1, f + 6):
            if (x - x0) % 6 != 3:
                k.put(x, y, z0 + 9, "ss_smooth")
    cell = 0
    for x in range(x0 + 1, x1 - 1, 6):
        K.bed(k, x + 1, f + 1, z0 + 3, "north", "bed_w")
        k.titik("kasur", x + 1, f + 1, z0 + 3, "south")
        k.put(x + 3, f + 1, z0 + 1, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
        k.titik("meja_tulis", x + 3, f + 1, z0 + 2, "north")
        k.put(x + 4, f + 1, z0 + 6, "carpet_lb")
        lampu_gantung(k, x + 2, f + 6, z0 + 4, 1)
        cell += 1
    # refectory: long low table with cushions, a water cauldron and an oven
    for x in range(x0 + 4, x1 - 3):
        K.low_table(k, x, f + 1, z0 + 15)
        if x % 2 == 0:
            k.put(x, f + 1, z0 + 13, "carpet_w")
            k.put(x, f + 1, z0 + 17, "carpet_w")
    k.titik("meja_makan", (x0 + x1) // 2, f + 1, z0 + 15, "south")
    oven_tanah(k, x1 - 2, z1 - 2, f, "north")
    K.door(k, (x0 + x1) // 2, f + 1, z1, "south")
    k.titik("pintu", (x0 + x1) // 2, f + 1, z1 + 1, "south")
    for x in range(x0 + 4, x1 - 2, 8):
        lampu_gantung(k, x, f + 6, z0 + 15, 1)
    for x in range(x0 + 1, x1 - 1, 6):
        ruang(k, x, z0 + 1, x + 4, z0 + 8, f, "kuil_air", isi=0.35, rug=False, seed=x)
    ruang(k, x0 + 1, z0 + 10, x1 - 1, z1 - 1, f, "gurun_dapur", isi=0.45)
    k.kotak("bangunan", x0, f, z0, x1, f + 8, z1, "south", nama="asrama_pendeta")
    # the well with its queue (chain stage 1) and a garden of palms
    sumur(k, 71, 85, LY, True, 1)
    for x, z in ((60, 82), (82, 82), (60, 90), (84, 90)):
        palma(k, x, z, LY + 1, 7)
    for x in range(56, 89, 4):
        semak(k, x, 92)


def gen_kuil_air():
    d = "kuil_air"
    k = kanvas(d)
    sisi = {(0, 0): "TJBT", (0, 1): "BJJT", (1, 0): "TJJJ", (1, 1): "JJJJ"}
    tanah(k)
    jalan_semua(k, sisi)
    tebing(k)
    kuil(k)
    asrama(k)
    # Mo Hua at the foot of the scaffold, the painter's cart of pigments
    k.jangkar(36.5, LY + 1, 48.5, "tema_oasis/mural", yaw=YAW["west"])
    k.put(38, LY + 1, 46, "minecraft:barrel", {"facing": "up", "open": "true"})
    k.put(38, LY + 1, 47, "minecraft:decorated_pot", {"facing": "west", "cracked": "false", "waterlogged": "false"})
    k.put(38, LY + 2, 46, "minecraft:orange_glazed_terracotta", {"facing": "north"})
    for z in range(10, 90, 16):
        lampu_jalan(k, 44, z)
    for x in range(52, 92, 10):
        lampu_jalan(k, x, 44)
    zona(k, 60, 47, d)
    simpan(k, d, {(0, 0): "tebing_utara", (0, 1): "tebing_selatan", (1, 0): "kuil", (1, 1): "asrama"}, sisi)
    # walls: north band, west band, the north-west corner tower
    tembok_lot(d, -48, -60, "tembok/lurus", 0)
    tembok_lot(d, 0, -60, "tembok/lurus", 0)
    tembok_lot(d, -60, -48, "tembok/lurus", 3)
    tembok_lot(d, -60, 0, "tembok/lurus", 3)
    tembok_lot(d, -60, -60, "tembok/sudut", 0)


# ===================================================================================================== istana_pasir
def istana(k):
    """The Istana Pasir across lots (0,0)+(1,0): an enclosure wall with the inner gate on the south, the front court,
    the domed audience hall (28 x 18, columns, red carpet, curtained throne), the water garden with a canal loop and a
    fountain to the west, the wind tower and the women's pavilion to the east."""
    # enclosure
    ex0, ez0, ex1, ez1 = 3, 1, 92, 43
    for x in range(ex0, ex1 + 1):
        for z in (ez0, ez1):
            for y in range(LY + 1, LY + 7):
                k.put(x, y, z, "mudb")
            k.put(x, LY + 7, z, "mudb" if x % 3 == 0 else "mudb_wall")
    for z in range(ez0, ez1 + 1):
        for x in (ex0, ex1):
            for y in range(LY + 1, LY + 7):
                k.put(x, y, z, "mudb")
            k.put(x, LY + 7, z, "mudb" if z % 3 == 0 else "mudb_wall")
    # inner gate (gerbang dalam) on the south wall: a gatehouse with an arch and a small dome
    gx0, gx1 = 42, 53
    k.fill(gx0, LY + 1, ez1 - 2, gx1, LY + 9, ez1 + 1, "ss_cut")
    k.air(45, LY + 1, ez1 - 2, 50, LY + 6, ez1 + 1)
    k.stairs(45, LY + 6, ez1 + 1, "ss_stairs", "east", "top")
    k.stairs(50, LY + 6, ez1 + 1, "ss_stairs", "west", "top")
    for x in range(gx0, gx1 + 1):
        k.put(x, LY + 8, ez1 + 1, "glazed_c", {"facing": "south"})
    kubah(k, 47, ez1 - 1, LY + 10, 3, role="dome", top="gold")
    for x in (gx0 - 1, gx1 + 1):
        K.banner_pole(k, x, LY + 1, ez1 + 2, 6, "cyan")
    k.jangkar(44.5, LY + 1, ez1 + 2.5, "kota/penjaga", yaw=YAW["south"])
    k.jangkar(51.5, LY + 1, ez1 + 2.5, "kota/penjaga", yaw=YAW["south"])
    k.titik("jaga", 44, LY + 1, ez1 + 2, "south")
    k.titik("jaga", 51, LY + 1, ez1 + 2, "south")
    # front court: paving, palm rows and lamps
    K.paving(k, ex0 + 1, 29, ex1 - 1, ez1 - 1, LY, "paving", "paving_alt", 0.1)
    for x in range(34, 62, 6):
        palma(k, x, 33, LY + 1, 7, None)
        palma(k, x + 3, 39, LY + 1, 6, None)
    for x in (31, 64):
        for z in (31, 39):
            lampu_jalan(k, x, z)
    # the audience hall
    hx0, hz0, hx1, hz1 = 34, 9, 61, 26
    f = LY + 1
    k.fill(hx0 - 2, LY + 1, hz0 - 2, hx1 + 2, f, hz1 + 2, "ss_cut")
    for x in range(44, 52):
        k.stairs(x, LY + 1, hz1 + 3, "sss_stairs", "north")
    hgt = 12
    k.walls(hx0, f + 1, hz0, hx1, f + hgt, hz1, role="ss_smooth", pillar="ss_chis", windows=0)
    k.fill(hx0, f, hz0, hx1, f, hz1, "ss_smooth")
    for x in range(hx0, hx1 + 1):
        for z in (hz0, hz1):
            k.put(x, f + 9, z, "glazed_c", {"facing": "south"})
            k.put(x, f + 10, z, "glazed_y", {"facing": "south"})
    for z in range(hz0, hz1 + 1):
        for x in (hx0, hx1):
            k.put(x, f + 9, z, "glazed_c", {"facing": "east"})
            k.put(x, f + 10, z, "glazed_y", {"facing": "east"})
    lengkung(k, 45, 50, hz1, f, 8, "x", "ss_cut", "sss_stairs")
    for x in range(hx0 + 3, hx1 - 1, 4):
        if 44 <= x <= 51:
            continue
        for y in (f + 4, f + 5, f + 6):
            k.put(x, y, hz1, "minecraft:yellow_stained_glass_pane")
            k.put(x, y, hz0, "minecraft:yellow_stained_glass_pane")
    k.fill(hx0, f + hgt + 1, hz0, hx1, f + hgt + 1, hz1, "ss_cut")
    parapet(k, hx0, hz0, hx1, hz1, f + hgt + 2, role="ss_wall", merlon="ss_cut")
    kubah(k, 47, 17, f + hgt + 2, 8, role="dome", top="gold", drum=2, drum_role="ss_cut", jendela=True)
    for ox in (hx0 + 2, hx1 - 2):
        for oz in (hz0 + 2, hz1 - 2):
            kubah(k, ox, oz, f + hgt + 2, 2, role="ss_smooth", top="brass_block")
    k.air(hx0 + 1, f + 1, hz0 + 1, hx1 - 1, f + hgt, hz1 - 1)
    # carved columns: two rows
    for x in range(hx0 + 4, hx1 - 2, 4):
        for z in (hz0 + 5, hz1 - 5):
            for y in range(f + 1, f + hgt + 1):
                k.put(x, y, z, "ss_chis" if (y - f) % 4 == 1 else "ss_smooth")
            k.put(x, f + hgt, z, "minecraft:gold_block")
    # red carpet aisle and the throne on its dais, curtained
    for z in range(hz0 + 4, hz1):
        for x in (46, 47, 48, 49):
            k.put(x, f + 1, z, "carpet_r" if x in (47, 48) else "carpet_y")
    for x in range(44, 52):
        for z in range(hz0 + 1, hz0 + 4):
            k.put(x, f + 1, z, "ss_cut")
        k.stairs(x, f + 1, hz0 + 4, "sss_stairs", "north")
    k.put(47, f + 2, hz0 + 2, "gold")
    k.put(48, f + 2, hz0 + 2, "gold")
    k.stairs(47, f + 3, hz0 + 2, "minecraft:smooth_quartz_stairs", "south")
    k.stairs(48, f + 3, hz0 + 2, "minecraft:smooth_quartz_stairs", "south")
    k.put(47, f + 4, hz0 + 1, "gold")
    k.put(48, f + 4, hz0 + 1, "gold")
    for x in range(44, 52):
        for y in range(f + 5, f + 9):
            if x in (44, 51):
                k.put(x, y, hz0 + 3, "wool_r")
        k.put(x, f + 9, hz0 + 3, "wool_y")
    permadani_dinding(k, 43, f + 2, hz0 + 1, 52, f + 8, motif=0)
    k.jangkar(47.5, f + 2, hz0 + 3.5, "tema_oasis/istana", yaw=YAW["south"])
    k.titik("meja_tulis", 41, f + 1, hz0 + 6, "east")
    k.titik("meja_tulis", 54, f + 1, hz0 + 6, "west")
    K.low_table(k, 40, f + 1, hz0 + 6)
    K.low_table(k, 55, f + 1, hz0 + 6)
    for x in (37, 58):
        for z in (hz0 + 3, hz1 - 3):
            k.titik("jaga", x, f + 1, z, "south")
    for x in range(hx0 + 4, hx1 - 2, 6):
        lampu_gantung(k, x, f + hgt + 1, 13, 3)
        lampu_gantung(k, x, f + hgt + 1, 22, 3)
    k.kotak("bangunan", hx0, f, hz0, hx1, f + hgt + 12, hz1, "south", nama="aula_audiensi")
    # water garden to the west: canal loop, fountain basin, palms, a pavilion
    gx0_, gz0_, gx1_, gz1_ = 6, 4, 30, 26
    k.fill(gx0_, LY, gz0_, gx1_, LY, gz1_, "minecraft:grass_block", {"snowy": "false"})
    for x in range(gx0_, gx1_ + 1):
        for z in range(gz0_, gz1_ + 1):
            ring = x in (gx0_ + 2, gx1_ - 2) and gz0_ + 2 <= z <= gz1_ - 2 or z in (gz0_ + 2, gz1_ - 2) and gx0_ + 2 <= x <= gx1_ - 2
            if ring:
                k.put(x, LY - 1, z, "jade")
                k.put(x, LY, z, "minecraft:water", {"level": "0"})
            elif x in (gx0_ + 1, gx0_ + 3, gx1_ - 3, gx1_ - 1) and gz0_ + 1 <= z <= gz1_ - 1 or \
                    z in (gz0_ + 1, gz0_ + 3, gz1_ - 3, gz1_ - 1) and gx0_ + 1 <= x <= gx1_ - 1:
                k.put(x, LY, z, "jade_slab", {"type": "top", "waterlogged": "false"})
    fx, fz = (gx0_ + gx1_) // 2, (gz0_ + gz1_) // 2
    for ox in range(-2, 3):
        for oz in range(-2, 3):
            if max(abs(ox), abs(oz)) == 2:
                k.put(fx + ox, LY + 1, fz + oz, "jade")
            else:
                k.put(fx + ox, LY, fz + oz, "minecraft:water", {"level": "0"})
    k.put(fx, LY, fz, "sea")
    k.put(fx, LY + 1, fz, "jade2")
    k.put(fx, LY + 2, fz, "minecraft:water", {"level": "0"})
    for x, z in ((gx0_ + 5, gz0_ + 5), (gx1_ - 5, gz0_ + 5), (gx0_ + 5, gz1_ - 5), (gx1_ - 5, gz1_ - 5)):
        palma(k, x, z, LY + 1, 8)
    for x in range(gx0_ + 6, gx1_ - 5, 3):
        K.pot(k, x, LY + 1, gz0_ + 1, "minecraft:potted_red_tulip" if x % 2 else "minecraft:potted_orange_tulip")
    k.titik("obrol", fx + 4, LY + 1, fz, "west")
    k.titik("main", fx - 4, LY + 1, fz + 3, "east")
    # east: the wind tower and the pavilion
    menara_angin(k, 80, 5, 7, 22, LY, "ss_cut")
    k.fill(64, LY, 14, 90, LY, 28, "ss")
    k.walls(64, LY + 1, 14, 90, LY + 6, 28, role="ss_smooth", pillar="ss_cut", windows=0)
    k.fill(64, LY + 7, 14, 90, LY + 7, 28, "ss_cut")
    parapet(k, 64, 14, 90, 28, LY + 8, role="ss_wall", merlon="ss_cut")
    k.air(65, LY + 1, 15, 89, LY + 6, 27)
    lengkung(k, 75, 79, 28, LY, 4, "x", "ss_cut", "sss_stairs")
    for x in range(66, 89, 2):
        k.put(x, LY + 4, 14, "minecraft:iron_bars")
    K.rug(k, 66, 17, 88, 26, LY + 1, "carpet_p", "carpet_y")
    for x in (68, 74, 80, 86):
        kursi_lantai_lingkar(k, x, 21, LY + 1, 2, "carpet_r")
    for x in range(67, 89, 6):
        lampu_gantung(k, x, LY + 7, 21, 1)
    for x in (66, 88):
        k.put(x, LY + 1, 16, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    k.kotak("bangunan", 64, LY, 14, 90, LY + 9, 28, "south", nama="paviliun_timur")


def barak(k):
    """Lot (0,1): the desert guards' barracks: 12 beds, spear racks, a drill yard with targets."""
    x0, z0, x1, z1 = 5, 54, 41, 76
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 5, z1, role="mudb", pillar="mud", windows=0)
    k.fill(x0, f + 6, z0, x1, f + 6, z1, "mud")
    parapet(k, x0, z0, x1, z1, f + 7)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 5, z1 - 1)
    k.fill(x0 + 1, f, z0 + 1, x1 - 1, f, z1 - 1, "terra_o")
    for i, x in enumerate(range(x0 + 2, x1 - 1, 3)):
        K.bed(k, x, f + 1, z0 + 2, "north", "bed_alt")
        k.titik("kasur", x, f + 1, z0 + 2, "south")
        K.barrel(k, x + 1, f + 1, z0 + 1, "south")
    for x in range(x0 + 2, x1 - 1, 3):
        K.bed(k, x, f + 1, z1 - 2, "south", "bed_alt")
        k.titik("kasur", x, f + 1, z1 - 2, "north")
    # spear racks: fences with iron bars "spearheads"
    for z in range(z0 + 6, z1 - 5, 2):
        k.put(x0 + 1, f + 1, z, "wood_fence")
        k.put(x0 + 1, f + 2, z, "wood_fence")
        k.put(x0 + 1, f + 3, z, "minecraft:iron_bars")
    K.door(k, (x0 + x1) // 2, f + 1, z1, "south")
    k.titik("pintu", (x0 + x1) // 2, f + 1, z1 + 1, "south")
    for x in range(x0 + 4, x1 - 2, 8):
        lampu_gantung(k, x, f + 6, (z0 + z1) // 2, 1)
    k.put(x1 - 2, f + 1, z0 + 10, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    k.titik("kerja:pahat", x1 - 3, f + 1, z0 + 10, "east")
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "barak_gurun", pulau=("peti", "tombak", "peti"), jarak=6, isi=0.4)
    k.kotak("bangunan", x0, f, z0, x1, f + 8, z1, "south", nama="barak_pengawal")
    # drill yard
    K.paving(k, x0, z1 + 3, x1, 91, LY, "minecraft:coarse_dirt", "minecraft:sand", 0.3)
    for x in range(x0 + 3, x1 - 1, 7):
        k.put(x, LY + 1, 89, "minecraft:hay_block", {"axis": "y"})
        k.put(x, LY + 2, 89, "minecraft:target")
        k.titik("latihan", x, LY + 1, 85, "south")


def perbendaharaan(k):
    """Lot (1,1): the treasury: the water store (barrels in rows, labelled), the gift room of the caravans."""
    x0, z0, x1, z1 = 54, 54, 90, 86
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 7, z1, role="ss_smooth", pillar="ss_cut", windows=0)
    k.fill(x0, f + 8, z0, x1, f + 8, z1, "ss_cut")
    parapet(k, x0, z0, x1, z1, f + 9, role="ss_wall", merlon="ss_cut")
    kubah(k, (x0 + x1) // 2, 76, f + 9, 5, role="dome", top="gold", drum=1)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 7, z1 - 1)
    # partition: water store north, gift room south
    for x in range(x0 + 1, x1):
        for y in range(f + 1, f + 8):
            if not (71 <= x <= 73 and y <= f + 3):
                k.put(x, y, 68, "ss_smooth")
    for row in range(z0 + 2, 67, 3):
        for x in range(x0 + 2, x1 - 1):
            if x % 4 == 0:
                continue
            K.barrel(k, x, f + 1, row, "north")
            K.barrel(k, x, f + 2, row, "up")
            k.put(x, f + 3, row, "minecraft:white_carpet")
    # gift room: pedestals with gifts of the caravans
    for x in range(x0 + 4, x1 - 3, 5):
        for z in (72, 80):
            k.put(x, f + 1, z, "ss_chis")
            gift = ("minecraft:amethyst_block", "minecraft:gold_block", "minecraft:decorated_pot", "jade2", "brass_block",
                    "minecraft:emerald_block")[(x + z) % 6]
            if gift == "minecraft:decorated_pot":
                k.put(x, f + 2, z, gift, {"facing": "north", "cracked": "false", "waterlogged": "false"})
            else:
                k.put(x, f + 2, z, gift)
    K.rug(k, x0 + 2, 70, x1 - 2, z1 - 2, f + 1, "carpet_r", "carpet_y")
    K.chest(k, x1 - 2, f + 1, z1 - 2, None, "west")
    for x in range(x0 + 5, x1 - 3, 8):
        lampu_gantung(k, x, f + 8, 76, 2)
        lampu_gantung(k, x, f + 8, 61, 2)
    K.door(k, 72, f + 1, z1, "south")
    k.titik("pintu", 72, f + 1, z1 + 1, "south")
    k.titik("jaga", 70, f + 1, z1 + 2, "south")
    k.titik("jaga", 74, f + 1, z1 + 2, "south")
    k.kotak("bangunan", x0, f, z0, x1, f + 16, z1, "south", nama="perbendaharaan")


def gen_istana_pasir():
    d = "istana_pasir"
    k = kanvas(d)
    sisi = {(0, 0): "TBJJ", (1, 0): "TJJB", (0, 1): "JJJJ", (1, 1): "JJJJ"}
    tanah(k)
    jalan_semua(k, sisi)
    istana(k)
    barak(k)
    perbendaharaan(k)
    for x in (10, 24, 72, 86):
        lampu_jalan(k, x, 49)
    zona(k, 48, 50, d)
    simpan(k, d, {(0, 0): "istana_barat", (1, 0): "istana_timur", (0, 1): "barak", (1, 1): "perbendaharaan"}, sisi)
    tembok_lot(d, -48, -60, "tembok/lurus", 0)
    tembok_lot(d, 0, -60, "tembok/lurus", 0)


# ===================================================================================================== kebun_palma
def kebun(k):
    """The Kebun Kurma across lots (0,0)+(1,0): 24 date palms in four rows on dark soil, stone irrigation channels fed
    from a cistern, harvest points, the guard's hut."""
    x0, z0, x1, z1 = 3, 2, 92, 42
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, LY, z, "minecraft:rooted_dirt" if (x * 3 + z) % 7 == 0 else "minecraft:coarse_dirt")
    rows = (8, 18, 28, 38)
    cols = [10 + 13 * i for i in range(6)]
    n = 0
    for zi, z in enumerate(rows):
        # the channel along the row
        for x in range(x0 + 2, x1 - 1):
            k.put(x, LY - 1, z + 2, "ss")
            k.put(x, LY, z + 2, "minecraft:water", {"level": "0"})
            if x % 8 == 0:
                k.put(x, LY, z + 3, "ss_cut")
        for xi, x in enumerate(cols):
            palma(k, x + (zi % 2) * 4, z, LY + 1, 8 + (xi + zi) % 3)
            if n < 8 and (xi + zi) % 3 == 0:
                k.titik("kerja:sabit", x + (zi % 2) * 4 + 1, LY + 1, z + 1, "west")
                n += 1
    while n < 8:
        k.titik("kerja:sabit", cols[n % 6] + 2, LY + 1, rows[n % 4] - 1, "south")
        n += 1
    # the cistern at the west end feeding the channels
    for x in range(x0, x0 + 4):
        for z in range(z0 + 4, z1 - 1):
            k.put(x, LY - 1, z, "ss")
            k.put(x, LY, z, "minecraft:water", {"level": "0"})
    for z in range(z0 + 3, z1):
        k.put(x0 + 4, LY + 1, z, "ss_wall")
    # harvested baskets
    for x in (20, 46, 72):
        k.put(x, LY + 1, 13, "minecraft:composter", {"level": "8"})
        k.put(x + 1, LY + 1, 13, "minecraft:hay_block", {"axis": "y"})
    # guard's hut
    hx0, hz0, hx1, hz1 = 82, 31, 90, 40
    k.fill(hx0, LY, hz0, hx1, LY, hz1, "ss")
    k.walls(hx0, LY + 1, hz0, hx1, LY + 4, hz1, role="mudb", pillar="mud", windows=0)
    k.fill(hx0, LY + 5, hz0, hx1, LY + 5, hz1, "mud")
    parapet(k, hx0, hz0, hx1, hz1, LY + 6)
    k.air(hx0 + 1, LY + 1, hz0 + 1, hx1 - 1, LY + 4, hz1 - 1)
    K.door(k, hx0, LY + 1, 35, "west")
    K.bed(k, hx1 - 1, LY + 1, hz0 + 2, "north", "bed")
    k.titik("kasur", hx1 - 1, LY + 1, hz0 + 2, "west")
    k.titik("jaga", hx0 - 2, LY + 1, 35, "west")
    lampu_gantung(k, 86, LY + 5, 35, 1)
    k.kotak("bangunan", hx0, LY, hz0, hx1, LY + 6, hz1, "south", nama="pondok_penjaga")
    k.jangkar(48.5, LY + 1, 23.5, "tema_oasis/kebun", yaw=YAW["south"], r=26)


def kandang_istana(k):
    """Lot (0,1): the palace camel pen: oak fence, straw floor, a shade roof, hay and troughs; the camel hire."""
    x0, z0, x1, z1 = 5, 53, 42, 91
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, LY, z, "minecraft:coarse_dirt" if (x + z) % 3 else "minecraft:packed_mud")
            if x in (x0, x1) or z in (z0, z1):
                if not (z == z0 and 22 <= x <= 25):
                    k.put(x, LY + 1, z, "minecraft:oak_fence")
    for x in (22, 25):
        k.put(x, LY + 1, z0, "minecraft:oak_fence")
    k.put(23, LY + 1, z0, "minecraft:oak_fence_gate", {"facing": "north", "in_wall": "false", "open": "false", "powered": "false"})
    k.put(24, LY + 1, z0, "minecraft:oak_fence_gate", {"facing": "north", "in_wall": "false", "open": "false", "powered": "false"})
    # shade roof on posts along the west side
    for z in range(z0 + 2, z1 - 1, 5):
        for y in range(LY + 1, LY + 5):
            k.put(x0 + 2, y, z, "palm_log", {"axis": "y"})
            k.put(x0 + 12, y, z, "palm_log", {"axis": "y"})
    for x in range(x0 + 1, x0 + 14):
        for z in range(z0 + 1, z1):
            k.put(x, LY + 5, z, "minecraft:hay_block" if (x + z) % 4 else "wood_slab", {"axis": "y"} if (x + z) % 4 else
                  {"type": "bottom", "waterlogged": "false"})
    for z in range(z0 + 3, z1 - 2, 3):
        k.put(x0 + 3, LY + 1, z, "minecraft:hay_block", {"axis": "y"})
        k.titik("kandang_unta", x0 + 6, LY + 1, z, "west")
    # long trough
    for z in range(z0 + 4, z1 - 4):
        k.put(x1 - 3, LY, z, "ss_cut")
        k.put(x1 - 3, LY + 1, z, "minecraft:water_cauldron", {"level": "3"})
    k.jangkar(28.5, LY + 1, 72.5, "tema_oasis/kandang", yaw=YAW["west"], unta=3, sewa=1)
    lampu_jalan(k, 21, z0 - 1)


def lumbung(k):
    """Lot (1,1): the date farmer's house and the granary (hay, composters, drying racks of dates)."""
    rumah_datar(k, 56, 56, 72, 70, "south", LY, 5, 2, "east", "rumah_petani", 1)
    halaman_rumah(k, 56, 74, 72, 88, LY, True)
    gx0, gz0, gx1, gz1 = 77, 56, 90, 80
    k.fill(gx0, LY, gz0, gx1, LY, gz1, "ss")
    k.walls(gx0, LY + 1, gz0, gx1, LY + 7, gz1, role="mudb", pillar="mud", windows=0)
    k.fill(gx0, LY + 8, gz0, gx1, LY + 8, gz1, "mud")
    parapet(k, gx0, gz0, gx1, gz1, LY + 9)
    k.air(gx0 + 1, LY + 1, gz0 + 1, gx1 - 1, LY + 7, gz1 - 1)
    lengkung(k, 82, 85, gz1, LY, 4, "x", "mudb", "mudb_stairs")
    for x in range(gx0 + 1, gx1):
        for z in range(gz0 + 1, gz0 + 8):
            for y in range(LY + 1, LY + 1 + 1 + (x + z) % 4):
                k.put(x, y, z, "minecraft:hay_block", {"axis": "y"})
    for z in range(gz0 + 10, gz1 - 2, 3):
        k.put(gx0 + 1, LY + 1, z, "minecraft:composter", {"level": "8"})
        k.put(gx1 - 1, LY + 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    # drying racks outside: fences with slabs
    for x in range(78, 90, 3):
        k.put(x, LY + 1, 85, "wood_fence")
        k.put(x, LY + 2, 85, "wood_slab", {"type": "bottom", "waterlogged": "false"})
        k.put(x + 1, LY + 2, 85, "minecraft:brown_carpet")
    k.titik("kerja:sabit", 84, LY + 1, 87, "north")
    k.kotak("bangunan", gx0, LY, gz0, gx1, LY + 9, gz1, "south", nama="lumbung")


def gen_kebun_palma():
    d = "kebun_palma"
    k = kanvas(d)
    sisi = {(0, 0): "TBJJ", (1, 0): "TTJB", (0, 1): "JJJJ", (1, 1): "JTJJ"}
    tanah(k)
    jalan_semua(k, sisi)
    kebun(k)
    kandang_istana(k)
    lumbung(k)
    zona(k, 48, 50, d)
    simpan(k, d, {(0, 0): "kebun_barat", (1, 0): "kebun_timur", (0, 1): "kandang_istana", (1, 1): "lumbung"}, sisi)
    tembok_lot(d, -48, -60, "tembok/lurus", 0)
    tembok_lot(d, 0, -60, "tembok/lurus", 0)
    tembok_lot(d, 48, -48, "tembok/lurus", 1)
    tembok_lot(d, 48, 0, "tembok/lurus", 1)
    tembok_lot(d, 48, -60, "tembok/sudut", 0)


# ===================================================================================================== bazar_barat
def lorong_bazar(k):
    """The covered Bazar Rempah dan Permadani across lots (0,0)+(1,0): an east-west lane under striped cloth on palm
    posts, nine stalls on each side (spices in composters, rolled carpets, sacks), storerooms behind them with carpets
    hung on their lane walls, brass lamps on chains; two tema_oasis/bazar anchors (the crescent guest stalls)."""
    lz0, lz1 = 19, 28                         # the lane
    K.paving(k, 3, lz0, 92, lz1, LY, "paving", "minecraft:smooth_sandstone_slab", 0.0)
    for x in range(3, 93):
        for z in range(lz0, lz1 + 1):
            if (x * 3 + z) % 13 == 0:
                k.put(x, LY, z, "terra_o")
    # storerooms behind the stalls
    for (sz0, sz1) in ((4, 13), (34, 43)):
        k.fill(3, LY, sz0, 92, LY, sz1, "ss")
        k.walls(3, LY + 1, sz0, 92, LY + 6, sz1, role="mudb", pillar="mud", windows=0)
        k.fill(3, LY + 7, sz0, 92, LY + 7, sz1, "mud")
        parapet(k, 3, sz0, 92, sz1, LY + 8)
        for x in range(13, 92, 10):
            for z in range(sz0 + 1, sz1):
                for y in range(LY + 1, LY + 7):
                    k.put(x, y, z, "mudb")
        lane_z = sz1 if sz0 < lz0 else sz0
        for i, x in enumerate(range(6, 90, 10)):
            permadani_dinding(k, x, LY + 2, lane_z, x + 5, LY + 5, motif=i + (1 if sz0 > lz0 else 0))
            K.door(k, x - 1, LY + 1, lane_z, "south" if sz0 < lz0 else "north")
            # storeroom behind the stall: spice sacks and caravan chests, every third one the carpet store
            ruang(k, x - 2, sz0 + 1, min(x + 6, 91), sz1 - 1, LY, "tenun" if i % 3 == 1 else "gurun_gudang", isi=0.7,
                  rug=False, seed=i + sz0)
    # nine stalls each side
    jenis = ("rempah", "permadani", "rempah", "permadani", "rempah", "permadani", "rempah", "permadani", "rempah")
    for i, x in enumerate(range(5, 92, 10)):
        if i >= 9:
            break
        kios(k, x, lz0 + 1, "south", jenis[i])
        kios(k, x + 4, lz1 - 1, "north", jenis[(i + 1) % 9])
    # the cloth roof over the whole lane, on palm posts
    for x in range(3, 93, 6):
        for z in (lz0, lz1):
            for y in range(LY + 1, LY + 7):
                if k.get(x, y, z) is None or k.get(x, y, z)[0][0] == "minecraft:air":
                    k.put(x, y, z, "palm_wood", {"axis": "y"})
    for x in range(3, 93):
        for z in range(lz0, lz1 + 1):
            a, b = TENDA[(x // 6) % len(TENDA)]
            lift = 1 if lz0 + 3 <= z <= lz1 - 3 else 0
            k.put(x, LY + 7 + lift, z, a if (x // 2 + z // 3) % 2 == 0 else b)
    for x in range(8, 90, 8):
        lampu_gantung(k, x, LY + 7, (lz0 + lz1) // 2, 2)
    k.jangkar(28.5, LY + 1, 23.5, "tema_oasis/bazar", yaw=YAW["east"], sisi="barat")
    k.jangkar(68.5, LY + 1, 23.5, "tema_oasis/bazar", yaw=YAW["east"], sisi="barat")
    for x in (20, 48, 76):
        k.titik("obrol", x, LY + 1, 23, "north")
    k.kotak("bangunan", 3, LY, 4, 92, LY + 9, 43, "south", nama="bazar_rempah")


def kedai_teh(k):
    """Lot (0,1): the Kedai Teh Unta: an open veranda under awnings with floor cushions and low tables, the samovar
    corner (brewing stands, kettles), a back room, a roof terrace; kota/teh and two wanderers' seats."""
    x0, z0, x1, z1 = 5, 55, 41, 89
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    # back room (north part)
    k.walls(x0, f + 1, z0, x1, f + 6, z0 + 12, role="mudb", pillar="mud", windows=0)
    k.fill(x0, f + 7, z0, x1, f + 7, z0 + 12, "mud")
    parapet(k, x0, z0, x1, z0 + 12, f + 8)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 6, z0 + 11)
    for x in range(x0 + 2, x1 - 1):
        if x % 3 == 0:
            k.put(x, f + 1, z0 + 1, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
        else:
            k.put(x, f + 1, z0 + 1, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
        k.put(x, f + 3, z0 + 1, "minecraft:jungle_shelf", {"facing": "south", "powered": "false", "side_chain": "unconnected",
                                                            "waterlogged": "false"})
    oven_tanah(k, x1 - 2, z0 + 6, f, "west")
    k.titik("kerja:teko", 20, f + 1, z0 + 2, "north")
    for x in range(x0 + 4, x1 - 2, 6):
        lampu_gantung(k, x, f + 7, z0 + 6, 1)
    lengkung(k, 20, 25, z0 + 12, f, 4, "x", "mudb", "mudb_stairs")
    # veranda with cushions and tables under a striped awning
    tenda(k, x0, z0 + 14, x1, z1, f, 1, 6, 4)
    K.rug(k, x0 + 1, z0 + 15, x1 - 1, z1 - 1, f + 1, "carpet_r", "carpet_o")
    for x in range(x0 + 4, x1 - 2, 7):
        for z in range(z0 + 18, z1 - 2, 7):
            kursi_lantai_lingkar(k, x, z, f + 1, 2, "carpet_y")
    for x in range(x0 + 3, x1 - 1, 8):
        lampu_gantung(k, x, f + 5, z0 + 24, 1)
    k.jangkar(22.5, f + 1, z0 + 15.5, "kota/teh", yaw=YAW["south"])
    k.jangkar(9.5, f + 1, z1 - 2.5, "kota/kursi_pengembara", yaw=YAW["east"])
    k.jangkar(37.5, f + 1, z1 - 2.5, "kota/kursi_pengembara", yaw=YAW["west"])
    k.titik("obrol", 22, f + 1, z1 - 4, "north")
    k.kotak("bangunan", x0, f, z0, x1, f + 9, z1, "south", nama="kedai_teh_unta")


def penenun(k):
    """Lot (1,1): the carpet weaver's house: three looms by the light, dye vats, finished carpets drying on lines."""
    x0, z0, x1, z1 = 54, 55, 90, 76
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 6, z1, role="mudb", pillar="mud", windows=0)
    k.fill(x0, f + 7, z0, x1, f + 7, z1, "mud")
    parapet(k, x0, z0, x1, z1, f + 8)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 6, z1 - 1)
    for x in range(x0 + 3, x1 - 2, 4):
        jendela(k, x, f + 3, z1, "south")
        jendela(k, x, f + 4, z0, "north")
    for i, x in enumerate((60, 70, 80)):
        k.put(x, f + 1, z1 - 3, "minecraft:loom", {"facing": "south"})
        k.titik("kerja:tenun", x, f + 1, z1 - 2, "north")
        permadani_dinding(k, x - 2, f + 2, z0 + 1, x + 2, f + 5, motif=i)
    for x in range(x0 + 2, x1 - 1, 3):
        k.put(x, f + 1, z0 + 3, "minecraft:water_cauldron", {"level": "3"})
        k.put(x, f + 1, z0 + 4, ("wool_r", "wool_o", "wool_y", "wool_c", "wool_p")[x % 5])
    K.bed(k, x1 - 2, f + 1, (z0 + z1) // 2, "east", "bed")
    k.titik("kasur", x1 - 2, f + 1, (z0 + z1) // 2, "west")
    K.door(k, 72, f + 1, z1, "south")
    k.titik("pintu", 72, f + 1, z1 + 1, "south")
    for x in range(x0 + 4, x1 - 2, 8):
        lampu_gantung(k, x, f + 7, (z0 + z1) // 2, 1)
    k.jangkar(64.5, f + 1, z1 - 4.5, "tema_oasis/tenun", yaw=YAW["south"])
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "tenun", pulau=("tenun", "meja_bantal"), jarak=7, isi=0.5)
    k.kotak("bangunan", x0, f, z0, x1, f + 9, z1, "south", nama="rumah_penenun")
    # drying lines in the yard: fences with hanging wool
    for x in range(56, 90, 2):
        k.put(x, f + 3, 84, "wood_fence")
        if x % 8:
            k.put(x, f + 2, 84, ("wool_r", "wool_o", "wool_y", "wool_c")[x % 4])
    for x in range(56, 90, 8):
        k.put(x, f + 2, 84, "wood_fence")


def gen_bazar_barat():
    d = "bazar_barat"
    k = kanvas(d)
    sisi = {(0, 0): "JBJT", (1, 0): "JJJB", (0, 1): "JJJT", (1, 1): "JJJJ"}
    tanah(k)
    jalan_semua(k, sisi)
    lorong_bazar(k)
    kedai_teh(k)
    penenun(k)
    for x in (12, 36, 60, 84):
        lampu_jalan(k, x, 49)
    zona(k, 48, 47, d)
    simpan(k, d, {(0, 0): "bazar_barat_a", (1, 0): "bazar_barat_b", (0, 1): "kedai_teh", (1, 1): "penenun"}, sisi)
    tembok_lot(d, -60, -48, "tembok/lurus", 3)
    tembok_lot(d, -60, 0, "tembok/lurus", 3)


# ===================================================================================================== mata_air
def di_danau(x, z):
    """The crescent of the Danau Bulan Sabit: inside circle A, outside circle B (horns point north, the square sits in
    the inner arc around the district centre)."""
    ax, az, ar = 48.0, 52.0, 37.0
    bx, bz, br = 48.0, 33.0, 30.5
    return (x - ax) ** 2 + (z - az) ** 2 <= ar * ar and (x - bx) ** 2 + (z - bz) ** 2 > br * br


def danau(k):
    """The Danau Bulan Sabit: water three deep over sand with rare sea lanterns, stepped stone shores, reeds and grass,
    lily pads; the drinking pavilion on the west horn, the round dance stage (11 across) on the belly, a causeway."""
    cells = [(x, z) for x in range(3, 93) for z in range(3, 93) if di_danau(x, z)]
    water = set(cells)
    for x, z in cells:
        k.put(x, 0, z, "sea" if (x * 7 + z * 11) % 37 == 0 else "minecraft:sand")
        for y in (1, 2, 3):
            k.put(x, y, z, "minecraft:water", {"level": "0"})
        k.put(x, LY, z, "minecraft:air")
    # shore ring: one step of smooth sandstone, reeds on sand behind it
    for x, z in cells:
        for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + ox, z + oz
            if (nx, nz) in water or not k.inside(nx, LY, nz):
                continue
            face = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(ox, oz)]
            k.put(nx, LY, nz, "minecraft:sand")
            k.stairs(nx, 3, nz, "sss_stairs", face)
            r = (nx * 13 + nz * 7) % 10
            if r < 4:
                k.put(nx, LY + 1, nz, "minecraft:sugar_cane")
                k.put(nx, LY + 2, nz, "minecraft:sugar_cane")
            elif r < 6:
                k.put(nx, LY + 1, nz, "minecraft:tall_grass", {"half": "lower"})
                k.put(nx, LY + 2, nz, "minecraft:tall_grass", {"half": "upper"})
            elif r < 7:
                k.put(nx, LY + 1, nz, "minecraft:short_grass")
    for x, z in cells[::17]:
        k.put(x, LY, z, "minecraft:lily_pad")
    # causeway and stage on the belly
    sx, sz = 48, 78
    for z in range(61, sz - 4):
        for x in (47, 48, 49):
            k.put(x, LY - 1, z, "ss_cut")
            k.put(x, LY, z, "cut_slab", {"type": "top", "waterlogged": "true"})
        for x in (46, 50):
            if z % 3 == 0:
                k.put(x, LY + 1, z, "brass_lamp", {"hanging": "false", "waterlogged": "false"})
    for ox in range(-5, 6):
        for oz in range(-5, 6):
            dd = ox * ox + oz * oz
            if dd <= 30:
                for y in (1, 2, 3):
                    k.put(sx + ox, y, sz + oz, "ss")
                k.put(sx + ox, LY, sz + oz, "ss_cut" if dd > 12 else "ss_chis" if dd == 0 else "ss_smooth")
                if 20 < dd <= 30 and (ox + oz) % 3 == 0:
                    k.put(sx + ox, LY + 1, sz + oz, "brass_lamp", {"hanging": "false", "waterlogged": "false"})
    k.kotak("panggung", sx - 5, LY, sz - 5, sx + 5, LY + 4, sz + 5, "north")
    k.jangkar(sx + 0.5, LY + 1, sz + 0.5, "tema_oasis/panggung", yaw=YAW["north"], r=3)
    # the drinking pavilion on the west horn
    px, pz = 14, 40
    for ox in (-3, 3):
        for oz in (-3, 3):
            for y in range(LY + 1, LY + 6):
                k.put(px + ox, y, pz + oz, "ss_chis" if y in (LY + 1, LY + 5) else "ss_smooth")
    k.fill(px - 4, LY + 6, pz - 4, px + 4, LY + 6, pz + 4, "ss_cut")
    kubah(k, px, pz, LY + 7, 4, role="dome", top="gold")
    k.fill(px - 3, LY, pz - 3, px + 3, LY, pz + 3, "ss_smooth")
    for ox in (-2, 0, 2):
        k.put(px + ox, LY + 1, pz - 2, "minecraft:water_cauldron", {"level": "3"})
    lampu_gantung(k, px, LY + 6, pz, 1)
    k.titik("obrol", px, LY + 1, pz + 1, "north")
    k.kotak("bangunan", px - 4, LY, pz - 4, px + 4, LY + 12, pz + 4, "south", nama="paviliun_minum")


def alun(k):
    """The Alun Oasis in the inner arc: a round paved square with a star of tiles, the town's notice board, benches,
    a ring of palms and lamps; the town anchors (kota/situs, kota/alun, kota/papan, kota_tema/situs, tema_oasis/pusat)."""
    cx, cz, r = 48, 40, 17
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            dd = (x - cx) ** 2 + (z - cz) ** 2
            if dd <= r * r and not di_danau(x, z):
                star = (abs(x - cx) == abs(z - cz) or x == cx or z == cz) and dd < (r - 3) ** 2
                k.put(x, LY, z, "terra_o" if star and (x + z) % 2 else "ss_cut" if star else "paving")
                k.air(x, LY + 1, z, x, LY + 4, z)
    # a sundial plinth at the very centre of the star, a small fountain basin north of it
    k.put(cx, LY + 1, cz, "ss_chis")
    k.put(cx, LY + 2, cz, "minecraft:end_rod", {"facing": "up"})
    for ox in range(-2, 3):
        for oz in range(-2, 3):
            if max(abs(ox), abs(oz)) == 2:
                k.put(cx + ox, LY + 1, cz - 9 + oz, "jade_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                k.put(cx + ox, LY, cz - 9 + oz, "minecraft:water", {"level": "0"})
    k.put(cx, LY - 1, cz - 9, "sea")
    # palms and lamps on the rim, benches between
    for a in range(0, 360, 30):
        x = cx + int(round(math.cos(math.radians(a)) * (r - 1)))
        z = cz + int(round(math.sin(math.radians(a)) * (r - 1)))
        if di_danau(x, z):
            continue
        if a % 60 == 0:
            palma(k, x, z, LY + 1, 8)
        else:
            lampu_jalan(k, x, z)
    for a in (45, 135, 225, 315):
        x = cx + int(round(math.cos(math.radians(a)) * (r - 4)))
        z = cz + int(round(math.sin(math.radians(a)) * (r - 4)))
        k.stairs(x, LY + 1, z, "sss_stairs", "north")
        k.stairs(x + 1, LY + 1, z, "sss_stairs", "north")
        k.titik("obrol", x, LY + 1, z + 1, "north")
    K.notice_board(k, cx - 2, cz - 15, "south", LY, 5)
    for x, z in ((cx - 8, cz + 4), (cx + 8, cz - 4), (cx, cz + 8)):
        k.titik("main", x, LY + 1, z, "south")
    k.titik("jalan_lentera", cx, LY + 1, cz + 14, "south")
    k.jangkar(cx + 0.5, LY + 1, cz + 0.5 + 8, "tema_oasis/pusat", yaw=YAW["south"])
    k.jangkar(cx + 0.5, LY + 2, cz + 3.5, "kota/situs", yaw=0.0, situs="kota/kota_tema", tema="oasis", tier=3, protect=100)
    k.jangkar(cx + 4.5, LY + 1, cz + 0.5, "kota/alun", yaw=0.0, protect=64)
    k.jangkar(cx + 0.5, LY + 1, cz - 13.5, "kota/papan", yaw=YAW["south"])
    k.jangkar(cx - 3.5, LY + 1, cz + 3.5, "kota_tema/situs", yaw=0.0, tema="oasis", protect=96)


def gen_mata_air():
    d = "mata_air"
    k = kanvas(d)
    sisi = {(0, 0): "JBBJ", (1, 0): "JJBB", (0, 1): "BBJJ", (1, 1): "BJJB"}
    tanah(k)
    # gardens round the lake: grass and palms where neither water nor square is
    for x in range(3, 93):
        for z in range(3, 93):
            if not di_danau(x, z) and (x - 48) ** 2 + (z - 40) ** 2 > 18 * 18:
                k.put(x, LY, z, "minecraft:grass_block" if (x * 5 + z * 3) % 4 else "minecraft:sand", {"snowy": "false"}
                      if (x * 5 + z * 3) % 4 else None)
    jalan_semua(k, sisi)
    danau(k)
    alun(k)
    for x, z in ((8, 70), (12, 84), (84, 70), (80, 84), (24, 88), (72, 88), (6, 56), (90, 56), (34, 90), (62, 90)):
        if not di_danau(x, z):
            palma(k, x, z, LY + 1, 7 + (x + z) % 4)
    for x, z in ((20, 80), (76, 80), (40, 92), (56, 92)):
        if not di_danau(x, z):
            semak(k, x, z)
    for x in (10, 30, 66, 86):
        lampu_jalan(k, x, 4)
    k.titik("penonton", 40, LY + 1, 62, "south")
    k.titik("penonton", 56, LY + 1, 62, "south")
    zona(k, 30, 30, d)
    simpan(k, d, {(0, 0): "danau_barat_laut", (1, 0): "danau_timur_laut", (0, 1): "danau_barat_daya", (1, 1): "danau_tenggara"}, sisi)


# ===================================================================================================== bazar_timur
def bazar_kaca(k):
    """Lot (0,0): the Bazar Kaca dan Giok: a court of stalls round a basin, coloured glass hung in the stall fronts,
    the jade dealer (kota/pusaka) and the weighing stall; the eastern bazaar's tema_oasis/bazar anchor."""
    x0, z0, x1, z1 = 4, 4, 43, 43
    K.paving(k, x0, z0, x1, z1, LY, "paving", "terra_y", 0.08)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for ox in range(-3, 4):
        for oz in range(-3, 4):
            if max(abs(ox), abs(oz)) == 3:
                k.put(cx + ox, LY + 1, cz + oz, "jade_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                k.put(cx + ox, LY, cz + oz, "minecraft:water", {"level": "0"})
    k.put(cx, LY - 1, cz, "sea")
    k.put(cx, LY + 1, cz, "jade")
    k.put(cx, LY + 2, cz, "minecraft:water", {"level": "0"})
    # stalls on three sides facing the basin
    kios(k, 8, 9, "south", "kaca")
    kios(k, 18, 9, "south", "kaca")
    kios(k, 28, 9, "south", "giok")
    kios(k, 8, 38, "north", "giok")
    kios(k, 18, 38, "north", "kaca")
    kios(k, 28, 38, "north", "logam")
    for z in (14, 24):
        kios(k, 8, z + 3, "east", "kaca")
    # glass panes hung like lanterns between the stalls
    for x in range(6, 40, 4):
        k.put(x, LY + 6, 20, "minecraft:orange_stained_glass_pane")
        k.put(x, LY + 6, 27, "minecraft:light_blue_stained_glass_pane")
    k.jangkar(32.5, LY + 1, 37.5, "kota/pusaka", yaw=YAW["north"])
    k.put(36, LY + 1, 30, "minecraft:anvil", {"facing": "north"})
    k.titik("kerja:timbangan", 36, LY + 1, 31, "north")
    k.jangkar(cx + 0.5, LY + 1, cz + 5.5, "tema_oasis/bazar", yaw=YAW["south"], sisi="timur")
    for x, z in ((6, 6), (41, 6), (6, 41), (41, 41)):
        palma(k, x, z, LY + 1, 7)


def toko_air(k):
    """Lot (1,0): the water and ice shop: a cool vaulted room, blue ice blocks, jars and cauldrons, a counter."""
    x0, z0, x1, z1 = 54, 6, 90, 38
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 7, z1, role="ss_smooth", pillar="ss_cut", windows=0)
    k.fill(x0, f + 8, z0, x1, f + 8, z1, "ss_cut")
    parapet(k, x0, z0, x1, z1, f + 9, role="ss_wall", merlon="ss_cut")
    kubah(k, 72, 22, f + 9, 6, role="dome", top="brass_block", drum=1)
    menara_angin(k, 84, 8, 5, 14, LY, "ss_cut")
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 7, z1 - 1)
    for x in range(x0 + 2, x1 - 1):
        for z in (z0 + 1, z0 + 2):
            if (x + z) % 3:
                k.put(x, f + 1, z, "minecraft:blue_ice")
                k.put(x, f + 2, z, "minecraft:packed_ice" if x % 2 else "minecraft:blue_ice")
    for x in range(x0 + 2, x1 - 1, 3):
        k.put(x, f + 1, z1 - 8, "minecraft:water_cauldron", {"level": "3"})
        k.put(x + 1, f + 1, z1 - 8, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    K.counter(k, x0 + 6, x1 - 6, z1 - 5, f, "south")
    k.titik("lapak", 72, f + 1, z1 - 6, "south", {"kios": "air"})
    lengkung(k, 70, 74, z1, f, 5, "x", "ss_cut", "sss_stairs")
    for x in range(x0 + 4, x1 - 2, 6):
        lampu_gantung(k, x, f + 8, z1 - 12, 2)
    k.jangkar(72.5, f + 1, z1 - 6.5, "tema_oasis/toko_air", yaw=YAW["south"])
    k.kotak("bangunan", x0, f, z0, x1, f + 16, z1, "south", nama="toko_air")


def rumah_uang(k):
    """Lot (0,1): the branch of the Rumah Uang (counter, vault door, ledgers) and the guild board under an awning."""
    x0, z0, x1, z1 = 5, 54, 41, 80
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 7, z1, role="ss_smooth", pillar="ss_cut", windows=0)
    k.fill(x0, f + 8, z0, x1, f + 8, z1, "ss_cut")
    parapet(k, x0, z0, x1, z1, f + 9, role="ss_wall", merlon="ss_cut")
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 7, z1 - 1)
    K.rug(k, x0 + 2, z0 + 10, x1 - 2, z1 - 2, f + 1, "carpet_r", "carpet_y")
    K.counter(k, x0 + 3, x1 - 3, z0 + 8, f, "south")
    for x in range(x0 + 3, x1 - 2, 4):
        if x % 8 == 0:
            k.put(x, f + 2, z0 + 8, "minecraft:gold_block")
    # the vault behind the counter
    for x in range(x0 + 12, x0 + 24):
        for y in range(f + 1, f + 6):
            k.put(x, y, z0 + 3, "minecraft:iron_block" if y < f + 5 else "ss_cut")
    k.put(x0 + 17, f + 1, z0 + 3, "minecraft:iron_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false",
                                                           "powered": "false"})
    k.put(x0 + 17, f + 2, z0 + 3, "minecraft:iron_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false",
                                                           "powered": "false"})
    for x in range(x0 + 2, x0 + 10):
        k.put(x, f + 1, z0 + 1, "minecraft:chiseled_bookshelf", {"facing": "south", "slot_0_occupied": "true", "slot_1_occupied": "true",
                                                                "slot_2_occupied": "false", "slot_3_occupied": "true",
                                                                "slot_4_occupied": "false", "slot_5_occupied": "true"})
    k.titik("meja_tulis", x0 + 6, f + 1, z0 + 6, "south")
    lengkung(k, 21, 25, z1, f, 5, "x", "ss_cut", "sss_stairs")
    for x in range(x0 + 4, x1 - 2, 8):
        lampu_gantung(k, x, f + 8, z0 + 14, 2)
    k.jangkar(23.5, f + 1, z0 + 9.5, "kotabesar/layanan", yaw=YAW["south"], layanan="bank_cabang", peran="BANKIR")
    k.kotak("bangunan", x0, f, z0, x1, f + 10, z1, "south", nama="rumah_uang_cabang")
    # the guild board outside
    K.notice_board(k, 12, 86, "north", LY, 7)
    tenda(k, 10, 84, 20, 88, LY, 3, 5, 5)
    k.jangkar(15.5, LY + 1, 84.5, "kotabesar/layanan", yaw=YAW["south"], layanan="papan_gilda", peran="PETUGAS_GILDA")


def pandai_kuningan(k):
    """Lot (1,1): the brass smith (two anvils, a cold blast furnace, copper plates and lamps) and the pill corner."""
    x0, z0, x1, z1 = 54, 54, 90, 82
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 6, z1, role="mudb", pillar="mud", windows=0)
    k.fill(x0, f + 7, z0, x1, f + 7, z1, "mud")
    parapet(k, x0, z0, x1, z1, f + 8)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 6, z1 - 1)
    for i, x in enumerate((60, 66)):
        k.put(x, f + 1, z0 + 6, "minecraft:anvil", {"facing": "east"})
        k.titik("kerja:palu", x, f + 1, z0 + 7, "north")
    k.put(58, f + 1, z0 + 2, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    k.put(59, f + 1, z0 + 2, "minecraft:smithing_table")
    for x in range(61, 72):
        k.put(x, f + 3, z0 + 1, "brass")
        if x % 2:
            k.put(x, f + 2, z0 + 1, "minecraft:waxed_copper_lantern", {"hanging": "false", "waterlogged": "false"})
    for x in range(x0 + 2, x1 - 1, 4):
        k.put(x, f + 1, z1 - 2, "brass_block")
        k.put(x + 1, f + 1, z1 - 2, "minecraft:waxed_copper_lantern", {"hanging": "false", "waterlogged": "false"})
    # pill corner (east room)
    for z in range(z0 + 1, z1):
        for y in range(f + 1, f + 6):
            if z not in (z0 + 13, z0 + 14):
                k.put(78, y, z, "mudb")
    for z in range(z0 + 2, z1 - 1, 3):
        k.put(x1 - 1, f + 1, z, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "true", "has_bottle_2": "false"})
        k.put(x1 - 1, f + 2, z, "minecraft:jungle_shelf", {"facing": "west", "powered": "false", "side_chain": "unconnected",
                                                            "waterlogged": "false"})
    k.jangkar(84.5, f + 1, z0 + 14.5, "kota/pil", yaw=YAW["west"])
    K.door(k, 66, f + 1, z1, "south")
    K.door(k, 84, f + 1, z1, "south")
    k.titik("pintu", 66, f + 1, z1 + 1, "south")
    for x in range(x0 + 4, x1 - 2, 8):
        lampu_gantung(k, x, f + 7, (z0 + z1) // 2, 1)
    ruang(k, x0 + 1, z0 + 1, 77, z1 - 1, f, "kuningan", pulau=("kuningan", "tumpukan"), jarak=6, isi=0.5)
    ruang(k, 79, z0 + 1, x1 - 1, z1 - 1, f, "kantor_gurun", isi=0.45)
    k.kotak("bangunan", x0, f, z0, x1, f + 9, z1, "south", nama="pandai_kuningan")


def gen_bazar_timur():
    d = "bazar_timur"
    k = kanvas(d)
    sisi = {(0, 0): "JJJJ", (1, 0): "JTJJ", (0, 1): "JJJJ", (1, 1): "JTJJ"}
    tanah(k)
    jalan_semua(k, sisi)
    bazar_kaca(k)
    toko_air(k)
    rumah_uang(k)
    pandai_kuningan(k)
    for x in (12, 36, 60, 84):
        lampu_jalan(k, x, 49)
    zona(k, 47, 47, d)
    simpan(k, d, {(0, 0): "bazar_kaca", (1, 0): "toko_air", (0, 1): "rumah_uang", (1, 1): "pandai_kuningan"}, sisi)
    tembok_lot(d, 48, -48, "tembok/lurus", 1)
    tembok_lot(d, 48, 0, "tembok/lurus", 1)


# ===================================================================================================== hunian_tanah
def hunian_kanvas(varian):
    """The mud-house quarter: four lots of courtyard houses with roof terraces (two layouts per lot, pool
    kota_tema/oasis/hunian_tanah/<lot>), the shared shaduf well, clay ovens in the lanes, the bengkel (kotabesar/tukang)."""
    k = K.Kanvas(SEL, H, SEL, seed=tl._seed(F + "/hunian_tanah/" + varian), palette=PAL)
    sisi = {(0, 0): "JJJT", (1, 0): "JJJJ", (0, 1): "JJTT", (1, 1): "JJTJ"}
    tanah(k, top="minecraft:coarse_dirt")
    jalan_semua(k, sisi)
    w = 0 if varian == "a" else 1
    for (i, j) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        bx, bz = 48 * i, 48 * j
        if varian == "a":
            rumah_datar(k, bx + 5, bz + 5, bx + 19, bz + 17, "south", LY, 5, 2, "east", "rumah_%d%d_1" % (i, j), w + i)
            rumah_datar(k, bx + 25, bz + 5, bx + 41, bz + 19, "south", LY, 6, 3, "west", "rumah_%d%d_2" % (i, j), w + j + 1)
            halaman_rumah(k, bx + 5, bz + 22, bx + 41, bz + 31, LY, True)
            rumah_datar(k, bx + 5, bz + 34, bx + 21, bz + 43, "north", LY, 5, 2, "east", "rumah_%d%d_3" % (i, j), w + 2)
            if not (i == 1 and j == 1):
                rumah_datar(k, bx + 28, bz + 34, bx + 41, bz + 43, "north", LY, 5, 1, "west", "rumah_%d%d_4" % (i, j), w + 3)
        else:
            rumah_datar(k, bx + 5, bz + 5, bx + 23, bz + 19, "east", LY, 6, 3, "west", "rumah_%d%d_1" % (i, j), w + i + 2)
            halaman_rumah(k, bx + 26, bz + 5, bx + 41, bz + 19, LY, True, True)
            rumah_datar(k, bx + 5, bz + 24, bx + 18, bz + 36, "south", LY, 5, 2, "east", "rumah_%d%d_2" % (i, j), w + j)
            rumah_datar(k, bx + 23, bz + 24, bx + 41, bz + 37, "west", LY, 5, 2, "east", "rumah_%d%d_3" % (i, j), w + 1)
        for x in range(bx + 6, bx + 42, 9):
            semak(k, x, bz + 45 if j == 0 else bz + 3)
    # the quarter's well where the lanes cross, clay ovens and children's corner
    sumur(k, 48, 48, LY, True, 0)
    for x, z in ((40, 40), (56, 56)):
        oven_tanah(k, x, z, LY, "south")
    k.titik("main", 52, LY + 1, 44, "south")
    k.titik("main", 44, LY + 1, 52, "north")
    # the bengkel: an open workshop under a roof on the south-east lot
    if varian == "a":
        tx0, tz0, tx1, tz1 = 76, 82, 91, 91
    else:
        tx0, tz0, tx1, tz1 = 72, 82, 91, 91
    k.fill(tx0, LY, tz0, tx1, LY, tz1, "ss")
    for x in (tx0, tx1):
        for z in (tz0, tz1):
            for y in range(LY + 1, LY + 5):
                k.put(x, y, z, "palm_log", {"axis": "y"})
    k.fill(tx0, LY + 5, tz0, tx1, LY + 5, tz1, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    k.put(tx0 + 2, LY + 1, tz0 + 2, "minecraft:crafting_table")
    k.put(tx0 + 3, LY + 1, tz0 + 2, "minecraft:stonecutter", {"facing": "south"})
    k.put(tx0 + 4, LY + 1, tz0 + 2, "minecraft:anvil", {"facing": "west"})
    K.crates(k, tx1 - 3, tz0 + 1, tx1 - 1, tz1 - 1, LY + 1, 2)
    k.jangkar(tx0 + 6.5, LY + 1, tz0 + 4.5, "kotabesar/tukang", yaw=YAW["north"])
    k.titik("kerja:gergaji", tx0 + 5, LY + 1, tz0 + 3, "north")
    zona(k, 47, 47, "hunian_tanah")
    return k, sisi


def gen_hunian_tanah():
    d = "hunian_tanah"
    per_lot = {}
    for varian in ("a", "b"):
        k, sisi = hunian_kanvas(varian)
        names = {ij: "%s_%d%d" % (varian, ij[0], ij[1]) for ij in sisi}
        ringkas(k)
        lots = ttl.potong_distrik(k, TEMA, d, nama=names, sisi=sisi, tinggi_min=24)
        for ij, lot in sorted(lots.items()):
            lot.simpan()
            per_lot.setdefault(ij, []).append(lot.id)
    for ij, ids in sorted(per_lot.items()):
        name = "%s/rumah_%d%d" % (d, ij[0], ij[1])
        POOLS[pid(name)] = ids
        LOTS[d].append(ttl.lot_inti(ij[0], ij[1], pid(name)))
    tembok_lot(d, -60, -48, "tembok/lurus", 3)
    tembok_lot(d, -60, 0, "tembok/lurus", 3)
    tembok_lot(d, -48, 48, "tembok/lurus", 2)
    tembok_lot(d, 0, 48, "tembok/lurus", 2)
    tembok_lot(d, -60, 48, "tembok/sudut", 0)


# ===================================================================================================== gerbang_giok
def penginapan(k):
    """Lot (0,0): the Penginapan Pasir Bernyanyi: two storeys round a court with a fountain; ten rooms upstairs
    and down, the host's counter, a dining hall."""
    x0, z0, x1, z1 = 4, 4, 42, 42
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 11, z1, role="mudb", pillar="mud", windows=0)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 11, z1 - 1)
    k.fill(x0, f + 12, z0, x1, f + 12, z1, "mud")
    parapet(k, x0, z0, x1, z1, f + 13)
    cx0, cz0, cx1, cz1 = x0 + 10, z0 + 10, x1 - 10, z1 - 10
    k.air(cx0, f + 12, cz0, cx1, f + 13, cz1)
    # upper floor ring with a gallery on the court side
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if not (cx0 <= x <= cx1 and cz0 <= z <= cz1):
                k.put(x, f + 6, z, "wood")
    for x in range(cx0, cx1 + 1):
        for z in (cz0 - 1, cz1 + 1):
            k.put(x, f + 7, z, "wood_fence")
    for z in range(cz0, cz1 + 1):
        for x in (cx0 - 1, cx1 + 1):
            k.put(x, f + 7, z, "wood_fence")
    # court: tiles and the fountain
    K.paving(k, cx0, cz0, cx1, cz1, f, "terra_o", "terra_y", 0.3)
    fx, fz = (cx0 + cx1) // 2, (cz0 + cz1) // 2
    for ox in range(-2, 3):
        for oz in range(-2, 3):
            if max(abs(ox), abs(oz)) == 2:
                k.put(fx + ox, f + 1, fz + oz, "jade_slab", {"type": "bottom", "waterlogged": "false"})
            else:
                k.put(fx + ox, f, fz + oz, "minecraft:water", {"level": "0"})
    k.put(fx, f + 1, fz, "jade2")
    k.put(fx, f + 2, fz, "minecraft:water", {"level": "0"})
    for x, z in ((cx0 + 1, cz0 + 1), (cx1 - 1, cz1 - 1)):
        palma(k, x, z, f + 1, 9)
    # rooms: five on the ground floor (west and east), five upstairs
    rooms = [(x0 + 1, z0 + 1, x0 + 9, z0 + 8), (x0 + 1, z0 + 10, x0 + 9, z0 + 18), (x0 + 1, z0 + 20, x0 + 9, z0 + 28),
             (x1 - 9, z0 + 1, x1 - 1, z0 + 8), (x1 - 9, z0 + 10, x1 - 1, z0 + 18)]
    for level, fy in ((0, f), (1, f + 6)):
        for n, (rx0, rz0, rx1, rz1) in enumerate(rooms):
            for x in range(rx0, rx1 + 1):
                for y in range(fy + 1, fy + 6):
                    k.put(x, y, rz1 + 1, "mudb")
            for z in range(rz0, rz1 + 1):
                for y in range(fy + 1, fy + 6):
                    k.put(rx1 + 1 if rx0 == x0 + 1 else rx0 - 1, y, z, "mudb")
            door_x = rx1 + 1 if rx0 == x0 + 1 else rx0 - 1
            K.door(k, door_x, fy + 1, (rz0 + rz1) // 2, "east" if rx0 == x0 + 1 else "west")
            bx = rx0 + 1
            K.bed(k, bx, fy + 1, rz0 + 2, "north", "bed" if (n + level) % 2 else "bed_alt")
            k.titik("kasur", bx, fy + 1, rz0 + 2, "south")
            K.rug(k, rx0 + 3, rz0 + 2, rx1 - 1, rz1 - 1, fy + 1, "carpet_o", "carpet_r")
            k.put(rx1 - 1 if rx0 == x0 + 1 else rx0 + 1, fy + 1, rz0, "minecraft:decorated_pot", {"facing": "south",
                                                                                                  "cracked": "false", "waterlogged": "false"})
            lampu_gantung(k, (rx0 + rx1) // 2, fy + 6 if level == 0 else fy + 6, (rz0 + rz1) // 2, 1)
            ruang(k, rx0, rz0, rx1, rz1, fy, "gurun_inap", isi=0.6, rug=False, seed=n + 7 * level)
    # stairs to the upper floor (south-west corner)
    for i in range(6):
        k.stairs(x0 + 12 + i, f + 1 + i, z1 - 2, "wood_stairs", "east")
        k.air(x0 + 12 + i, f + 6, z1 - 2, x0 + 12 + i, f + 6, z1 - 2)
    # the host's counter and the dining hall on the south side
    K.counter(k, x0 + 22, x0 + 30, z1 - 6, f, "north")
    k.titik("lapak", x0 + 26, f + 1, z1 - 5, "north")
    for x in range(x0 + 20, x1 - 2, 5):
        kursi_lantai_lingkar(k, x, z1 - 2, f + 1, 1, "carpet_y", titik=False)
        k.titik("meja_makan", x, f + 1, z1 - 2, "north")
    lengkung(k, 21, 25, z1, f, 5, "x", "mudb", "mudb_stairs")
    k.titik("pintu", 23, f + 1, z1 + 1, "south")
    k.jangkar(26.5, f + 1, z1 - 4.5, "kotabesar/layanan", yaw=YAW["south"], layanan="penginapan", peran="PEMILIK_PENGINAPAN")
    ruang(k, cx0, z0 + 1, cx1, cz0 - 2, f + 6, "gurun_teh", isi=0.5, rug=False)
    ruang(k, cx0, cz1 + 2, cx1, z1 - 1, f + 6, "gurun_inap", isi=0.5, rug=False, seed=3)
    k.kotak("bangunan", x0, f, z0, x1, f + 14, z1, "south", nama="penginapan_pasir_bernyanyi")


def rumah_makan(k):
    """Lot (1,0): the Rumah Makan Bakar: a hall of low tables and cushions, a kitchen of clay ovens and a cold grill,
    meat hung on chains, water jars; an awning terrace on the street."""
    x0, z0, x1, z1 = 53, 5, 91, 34
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 6, z1, role="mudb", pillar="mud", windows=0)
    k.fill(x0, f + 7, z0, x1, f + 7, z1, "mud")
    parapet(k, x0, z0, x1, z1, f + 8)
    menara_angin(k, x1 - 6, z0 + 1, 5, 14, f, "mudb")
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 7, f + 6, z1 - 1)
    for x in range(x0 + 2, x1 - 8):
        if x % 5 == 0:
            oven_tanah(k, x, z0 + 2, f, "south")
        elif x % 5 == 2:
            k.put(x, f + 1, z0 + 1, "minecraft:campfire", {"facing": "south", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
        for y in (f + 5, f + 4):
            if x % 3 == 0 and y == f + 5:
                k.put(x, y, z0 + 4, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    for x in range(x0 + 4, x1 - 8, 6):
        for z in range(z0 + 9, z1 - 2, 6):
            kursi_lantai_lingkar(k, x, z, f + 1, 2, "carpet_r", titik=False)
            k.titik("meja_makan", x, f + 1, z, "south")
    for x in range(x0 + 3, x1 - 8, 7):
        lampu_gantung(k, x, f + 7, (z0 + z1) // 2 + 3, 1)
    lengkung(k, 68, 72, z1, f, 5, "x", "mudb", "mudb_stairs")
    tenda(k, 58, z1 + 2, 84, 42, f, 4, 6, 4)
    for x in range(61, 84, 7):
        kursi_lantai_lingkar(k, x, 39, f + 1, 2, "carpet_o", titik=False)
    k.titik("pintu", 70, f + 1, z1 + 1, "south")
    k.jangkar(64.5, f + 1, z0 + 5.5, "kotabesar/layanan", yaw=YAW["south"], layanan="rumah_makan", peran="KOKI")
    ruang(k, x0 + 1, z0 + 1, x1 - 7, z1 - 1, f, "gurun_dapur", isi=0.45, rug=False)
    k.kotak("bangunan", x0, f, z0, x1, f + 9, z1, "south", nama="rumah_makan_bakar")


def bea_gerbang(k):
    """Lot (0,1): the customs house of the Gerbang Giok: scales, sealed-crate stacks, the guards' bench; the inner
    plaza of the gate with the customs desk and the two gate guards."""
    x0, z0, x1, z1 = 6, 56, 36, 80
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    k.walls(x0, f + 1, z0, x1, f + 6, z1, role="ss_smooth", pillar="ss_cut", windows=0)
    k.fill(x0, f + 7, z0, x1, f + 7, z1, "ss_cut")
    parapet(k, x0, z0, x1, z1, f + 8, role="ss_wall", merlon="ss_cut")
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 6, z1 - 1)
    K.counter(k, x0 + 4, x1 - 4, z1 - 6, f, "south")
    k.put(x0 + 8, f + 2, z1 - 6, "minecraft:anvil", {"facing": "east"})
    k.titik("kerja:timbangan", x0 + 8, f + 1, z1 - 7, "south")
    K.crates(k, x0 + 2, z0 + 2, x0 + 12, z0 + 6, f + 1, 3)
    K.crates(k, x1 - 8, z0 + 2, x1 - 2, z0 + 5, f + 1, 2)
    lengkung(k, 30, 34, z1, f, 5, "x", "ss_cut", "sss_stairs")
    lengkung(k, x1, z0 + 10, z0 + 14, f, 4, "z", "ss_cut", "sss_stairs")
    for x in range(x0 + 4, x1 - 2, 8):
        lampu_gantung(k, x, f + 7, (z0 + z1) // 2, 1)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "kantor_gurun", pulau=("tumpukan",), jarak=6, isi=0.5)
    k.kotak("bangunan", x0, f, z0, x1, f + 8, z1, "south", nama="rumah_bea")
    # plaza inside the arch: paving, guards, customs desk on the avenue
    K.paving(k, 38, 82, 58, 95, LY, "ss_cut", "paving", 0.3)
    k.jangkar(43.5, LY + 1, 88.5, "tema_oasis/bea", yaw=YAW["east"])
    k.put(42, LY + 1, 86, "minecraft:barrel", {"facing": "up", "open": "false"})
    k.put(42, LY + 1, 87, "wood_trapdoor", {"facing": "east", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    k.jangkar(44.5, LY + 1, 93.5, "kota/penjaga", yaw=YAW["south"])
    k.jangkar(52.5, LY + 1, 93.5, "kota/penjaga", yaw=YAW["south"])
    k.titik("jaga", 44, LY + 1, 93, "south")
    k.titik("jaga", 52, LY + 1, 93, "south")
    for x in (40, 56):
        lampu_jalan(k, x, 84)


def stasiun(k):
    """Lot (1,1): the small formation station (kotabesar/cakram): a raised jade disc ringed by rune pillars."""
    cx, cz = 72, 70
    for ox in range(-8, 9):
        for oz in range(-8, 9):
            dd = ox * ox + oz * oz
            if dd <= 64:
                k.put(cx + ox, LY, cz + oz, "ss_cut")
                k.put(cx + ox, LY + 1, cz + oz, "jade" if dd <= 9 else "ss_smooth" if dd <= 36 else "cut_slab",
                      {"type": "bottom", "waterlogged": "false"} if dd > 36 else None)
    k.put(cx, LY + 1, cz, "jade_dark")
    for a in range(0, 360, 45):
        x = cx + int(round(math.cos(math.radians(a)) * 7))
        z = cz + int(round(math.sin(math.radians(a)) * 7))
        for y in range(LY + 2, LY + 5):
            k.put(x, y, z, "jade2" if y < LY + 4 else "minecraft:end_rod", {"facing": "up"} if y == LY + 4 else None)
    k.jangkar(cx + 0.5, LY + 2, cz + 0.5, "kotabesar/cakram", yaw=0.0, protect=12)
    rumah_datar(k, 82, 54, 91, 62, "west", LY, 5, 1, "east", "pondok_penjaga_cakram", 2, False, False, True)


def gen_gerbang_giok():
    d = "gerbang_giok"
    k = kanvas(d)
    sisi = {(0, 0): "JJJJ", (1, 0): "JJJJ", (0, 1): "JJTJ", (1, 1): "JJTJ"}
    tanah(k)
    jalan_semua(k, sisi)
    # the avenue from the arch runs through the south lots to the cross street
    K.paving(k, 45, 50, 51, 95, LY, "paving", "ss_cut", 0.15)
    k.air(45, LY + 1, 50, 51, LY + 6, 95)
    penginapan(k)
    rumah_makan(k)
    bea_gerbang(k)
    stasiun(k)
    zona(k, 60, 47, d)
    simpan(k, d, {(0, 0): "penginapan", (1, 0): "rumah_makan", (0, 1): "bea", (1, 1): "stasiun"}, sisi)
    # the Gerbang Giok halves (template outside = north; putar 2 turns it to face south, so the canvas' west half
    # stands east of the avenue)
    tembok_lot(d, 0, 48, "tembok/gerbang_a", 2)
    tembok_lot(d, -48, 48, "tembok/gerbang_b", 2)


# ===================================================================================================== karavanserai
def karavanserai_kanvas(k):
    """The Karavanserai Agung: an 80 x 80 square round a court; the ground floor of camel stalls and stores behind an
    arcade, merchants' rooms upstairs behind a gallery, four corner wind towers, the great west portal (iwan) with jade
    trim, the court well, the caravan office; points for 12 camels and 16 beds."""
    x0, z0, x1, z1 = 8, 8, 87, 87
    c0, c1 = 21, 74                           # the court
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "ss")
    hgt = 13
    k.walls(x0, f + 1, z0, x1, f + hgt, z1, role="mudb", pillar="mud", windows=0)
    # banded outer face
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            k.put(x, f + 6, z, "ss_cut")
            k.put(x, f + 12, z, "ss_cut")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            k.put(x, f + 6, z, "ss_cut")
            k.put(x, f + 12, z, "ss_cut")
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + hgt, z1 - 1)
    # court walls (inner face) with an arcade on the ground floor and a gallery above
    for t in range(c0, c1 + 1):
        for (x, z) in ((t, c0), (t, c1), (c0, t), (c1, t)):
            for y in range(f + 1, f + hgt + 1):
                k.put(x, y, z, "ss_smooth" if y > f + 6 else "mudb")
    for t in range(c0 + 2, c1 - 1, 5):
        for (x, z, sumbu) in ((t, c0, "x"), (t, c1, "x"), (c0, t, "z"), (c1, t, "z")):
            if sumbu == "x":
                lengkung(k, t, t + 2, z, f, 4, "x", "ss_cut", "sss_stairs")
                k.air(t, f + 8, z, t + 2, f + 10, z)
            else:
                lengkung(k, t, t + 2, x, f, 4, "z", "ss_cut", "sss_stairs")
                k.air(x, f + 8, t, x, f + 10, t + 2)
    # upper floor slab over the ring, gallery railing over the court edge
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if not (c0 < x < c1 and c0 < z < c1):
                k.put(x, f + 7, z, "wood")
    for t in range(c0, c1 + 1):
        for (x, z) in ((t, c0 + 1), (t, c1 - 1), (c0 + 1, t), (c1 - 1, t)):
            k.put(x, f + 7, z, "wood_slab", {"type": "top", "waterlogged": "false"})
            k.put(x, f + 8, z, "wood_fence")
    k.fill(x0, f + hgt + 1, z0, x1, f + hgt + 1, z1, "mud")
    k.air(c0 + 1, f + hgt + 1, c0 + 1, c1 - 1, f + hgt + 1, c1 - 1)
    parapet(k, x0, z0, x1, z1, f + hgt + 2)
    # the court: tiles, the well, palms in pots, the pen
    K.paving(k, c0 + 1, c0 + 1, c1 - 1, c1 - 1, f, "minecraft:coarse_dirt", "mud", 0.25)
    for x in range(c0 + 1, c1):
        for z in (c0 + 2, c1 - 2):
            k.put(x, f, z, "ss_cut")
    sumur(k, 48, 48, f, True, 0)
    for x, z in ((c0 + 4, c0 + 4), (c1 - 4, c0 + 4), (c0 + 4, c1 - 4), (c1 - 4, c1 - 4)):
        palma(k, x, z, f + 1, 8)
    k.jangkar(48.5, f + 1, 64.5, "tema_oasis/kandang", yaw=YAW["north"], unta=4, sewa=1)
    for x in range(c0 + 6, c1 - 5, 6):
        k.put(x, f + 1, c1 - 3, "minecraft:hay_block", {"axis": "y"})
    # ground-floor ring: camel stalls south and north, stores east and west; points for 12 camels
    n = 0
    for x in range(x0 + 2, x1 - 1, 6):
        for z, face in ((z0 + 4, "south"), (z1 - 4, "north")):
            if x0 + 2 <= x <= x1 - 2:
                k.put(x, f + 1, z + (-2 if face == "south" else 2), "minecraft:hay_block", {"axis": "y"})
                k.put(x + 1, f + 1, z + (-2 if face == "south" else 2), "minecraft:water_cauldron", {"level": "3"})
                if n < 12:
                    k.titik("kandang_unta", x + 1, f + 1, z, face)
                    n += 1
    for z in range(z0 + 6, z1 - 5, 4):
        K.crates(k, x0 + 2, z, x0 + 5, z + 1, f + 1, 2)
        K.crates(k, x1 - 5, z, x1 - 2, z + 1, f + 1, 2)
    # the stair towers in the court corners
    for (sx, sz, face) in ((c0 + 1, c0 + 1, "east"), (c1 - 7, c1 - 1, "east")):
        for i in range(6):
            k.stairs(sx + i, f + 1 + i, sz, "wood_stairs", face)
            k.air(sx + i, f + 7, sz, sx + i, f + 7, sz)
    # upper rooms: 16 beds along the north and south ranges
    beds = 0
    for x in range(x0 + 3, x1 - 2, 5):
        for z, face in ((z0 + 2, "north"), (z1 - 2, "south")):
            if beds >= 16 or c0 - 2 < x < c0 + 2:
                continue
            K.bed(k, x, f + 8, z, face, "bed" if beds % 2 else "bed_alt")
            k.titik("kasur", x, f + 8, z, OPP[face])
            k.put(x + 1, f + 8, z, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
            k.put(x + 2, f + 8, z + (2 if face == "north" else -2), "carpet_r")
            beds += 1
        for z in (z0 + 6, z1 - 6):
            for y in range(f + 8, f + hgt + 1):
                k.put(x + 4, y, z, "mudb")
    for x in range(x0 + 4, x1 - 2, 8):
        lampu_gantung(k, x, f + 7, z0 + 4, 1)
        lampu_gantung(k, x, f + 7, z1 - 4, 1)
        lampu_gantung(k, x, f + hgt + 1, z0 + 4, 1)
        lampu_gantung(k, x, f + hgt + 1, z1 - 4, 1)
    for z in range(z0 + 4, z1 - 2, 8):
        lampu_gantung(k, x0 + 4, f + 7, z, 1)
        lampu_gantung(k, x1 - 4, f + 7, z, 1)
    # four corner wind towers
    for (tx, tz) in ((x0, z0), (x1 - 6, z0), (x0, z1 - 6), (x1 - 6, z1 - 6)):
        menara_angin(k, tx, tz, 7, 24, f, "ss_cut")
    # the great west portal (iwan): a tall pointed arch framed in jade, the passage to the court
    py0, pz0, pz1 = f, 42, 53
    for z in range(pz0 - 3, pz1 + 4):
        for y in range(f + 1, f + hgt + 6):
            k.put(x0, y, z, "ss_smooth")
    for z in range(pz0 - 2, pz1 + 3):
        k.put(x0, f + hgt + 5, z, "ss_chis")
        k.put(x0, f + hgt + 4, z, "glazed_c", {"facing": "west"})
    for z in range(pz0, pz1 + 1):
        for y in range(f + 1, f + 11):
            k.put(x0, y, z, "minecraft:air")
        for x in range(x0 + 1, c0 + 1):
            for y in range(f + 1, f + 7):
                k.put(x, y, z, "minecraft:air")
            k.put(x, f, z, "ss_cut")
    for y in range(f + 1, f + 12):
        k.put(x0, y, pz0 - 1, "jade")
        k.put(x0, y, pz1 + 1, "jade")
    for z in range(pz0, pz1 + 1):
        k.put(x0, f + 11, z, "jade")
    k.stairs(x0, f + 10, pz0, "jade_stairs", "south", "top")
    k.stairs(x0, f + 10, pz1, "jade_stairs", "north", "top")
    for z in (pz0 - 2, pz1 + 2):
        K.banner_pole(k, x0 - 2, f + 1, z, 7, "cyan")
    # the caravan office: a room off the passage on the north side, Bulat Khan's desk
    for x in range(x0 + 2, c0 - 1):
        for y in range(f + 1, f + 7):
            k.put(x, y, pz0 - 1, "mudb")
    K.door(k, x0 + 6, f + 1, pz0 - 1, "south")
    k.put(x0 + 4, f + 1, pz0 - 5, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    k.put(x0 + 5, f + 1, pz0 - 6, "minecraft:cartography_table")
    K.chest(k, x0 + 2, f + 1, pz0 - 7, None, "east")
    k.titik("meja_tulis", x0 + 4, f + 1, pz0 - 4, "north")
    k.jangkar(x0 + 6.5, f + 1, pz0 - 4.5, "tema_oasis/karavanserai", yaw=YAW["south"])
    for z in (pz0 + 2, pz1 - 2):
        k.titik("obrol", x0 + 10, f + 1, z, "east")
    # dressing: the merchants' upper ranges (cushioned tables), the office wing, the east stores, the camel aisles
    ruang(k, x0 + 1, z0 + 1, x1 - 1, c0 - 1, f + 7, "gurun_inap", pulau=("meja_bantal",), jarak=7, isi=0.4)
    ruang(k, x0 + 1, c1 + 1, x1 - 1, z1 - 1, f + 7, "gurun_inap", pulau=("meja_bantal",), jarak=7, isi=0.4, seed=1)
    ruang(k, x0 + 1, c0, c0 - 1, c1, f + 7, "gurun_teh", isi=0.45, rug=False)
    ruang(k, c1 + 1, c0, x1 - 1, c1, f + 7, "gurun_gudang", isi=0.45)
    ruang(k, x0 + 1, c0 + 1, c0 - 1, pz0 - 2, f, "kantor_gurun", isi=0.5, rug=False)
    ruang(k, c1 + 1, c0, x1 - 1, c1, f, "gurun_gudang", isi=0.35)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, c0 - 1, f, "gurun_gudang", isi=0.25, seed=2)
    ruang(k, x0 + 1, c1 + 1, x1 - 1, z1 - 1, f, "gurun_gudang", isi=0.25, seed=3)
    k.kotak("bangunan", x0, f, z0, x1, f + 30, z1, "south", nama="karavanserai_agung")


def gen_karavanserai():
    d = "karavanserai"
    k = kanvas(d)
    sisi = {(0, 0): "JBBJ", (1, 0): "JTBB", (0, 1): "BBTJ", (1, 1): "BTTB"}
    tanah(k, top="minecraft:coarse_dirt")
    jalan_semua(k, sisi)
    karavanserai_kanvas(k)
    zona(k, 30, 30, d)
    simpan(k, d, {(0, 0): "karavanserai_barat_laut", (1, 0): "karavanserai_timur_laut", (0, 1): "karavanserai_barat_daya",
                  (1, 1): "karavanserai_tenggara"}, sisi)
    tembok_lot(d, 48, -48, "tembok/lurus", 1)
    tembok_lot(d, 48, 0, "tembok/lurus", 1)
    tembok_lot(d, -48, 48, "tembok/lurus", 2)
    tembok_lot(d, 0, 48, "tembok/lurus", 2)
    tembok_lot(d, 48, 48, "tembok/sudut", 0)


# ===================================================================================================== lapangan_kafilah
def tenda_bundar(k, cx, cz, r=4, y=LY, warna=("wool_w", "wool_b")):
    """A round caravan tent: a felt wall ring two high, a cone roof in bands, the door flap to the road, rugs inside."""
    a, b = warna
    for ox in range(-r, r + 1):
        for oz in range(-r, r + 1):
            dd = math.sqrt(ox * ox + oz * oz)
            if dd <= r + 0.3:
                k.put(cx + ox, y, cz + oz, "carpet_b" if dd > r - 1 else "carpet_r")
            if r - 0.6 <= dd <= r + 0.3:
                k.put(cx + ox, y + 1, cz + oz, a)
                k.put(cx + ox, y + 2, cz + oz, b if (ox + oz) % 3 == 0 else a)
    for lv in range(0, r + 1):
        rr = r - lv
        for ox in range(-rr, rr + 1):
            for oz in range(-rr, rr + 1):
                dd = math.sqrt(ox * ox + oz * oz)
                if rr - 0.8 <= dd <= rr + 0.3 or rr == 0:
                    k.put(cx + ox, y + 3 + lv, cz + oz, b if lv % 2 else a)
    k.put(cx, y + 4 + r, cz, "wood_fence")
    # door flap toward +x (the road)
    k.put(cx + r, y + 1, cz, "minecraft:air")
    k.put(cx + r, y + 2, cz, "minecraft:air")
    k.put(cx, y + 1, cz, "minecraft:decorated_pot", {"facing": "east", "cracked": "false", "waterlogged": "false"})
    k.titik("kasur", cx - 2, y + 1, cz, "east", {"luar": "1"})


def api_unggun(k, cx, cz, y=LY):
    """A dead campfire ring with log seats (the caravan men tell stories here at night)."""
    k.put(cx, y + 1, cz, "minecraft:campfire", {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        k.put(cx + ox, y, cz + oz, "minecraft:gravel")
    for face, (dx, dz) in FACE.items():
        k.put(cx + dx * 3, y + 1, cz + dz * 3, "palm_log", {"axis": "x" if dx == 0 else "z"})
        k.titik("kursi_lantai", cx + dx * 3, y + 2, cz + dz * 3, OPP[face])
    k.titik("obrol", cx + 2, y + 1, cz + 2, "north")


def gen_lapangan_kafilah():
    d = "lapangan_kafilah"
    k = kanvas(d, h=30)
    sisi = {(0, 0): "BJBB", (1, 0): "BBBJ", (0, 1): "BJBB", (1, 1): "BBBJ"}
    tanah(k, top="minecraft:sand")
    for x in range(0, 96):
        for z in range(0, 96):
            if (x * 11 + z * 7) % 9 == 0:
                k.put(x, LY, z, "minecraft:coarse_dirt")
    jalan_semua(k, sisi)
    # sandy road ruts down the middle
    for z in range(0, 96):
        for x in (44, 52):
            k.put(x, LY, z, "minecraft:coarse_dirt")
    # tents on both sides of the road
    for (cx, cz, w) in ((20, 14, 0), (32, 34, 1), (16, 58, 0), (76, 18, 1), (66, 40, 0), (80, 64, 1)):
        tenda_bundar(k, cx, cz, 4, LY, ("wool_w", "wool_b") if w == 0 else ("wool_b", "wool_o"))
    for (cx, cz) in ((24, 44), (72, 30), (60, 82)):
        api_unggun(k, cx, cz)
    # the long trough and the two tethering stations (8 camel points)
    for z in range(66, 88):
        k.put(38, LY, z, "ss_cut")
        k.put(38, LY + 1, z, "minecraft:water_cauldron", {"level": "3"})
    n = 0
    for (tx, tz) in ((28, 70), (64, 58)):
        for i in range(4):
            px = tx + (i % 2) * 6
            pz = tz + (i // 2) * 6
            k.put(px, LY + 1, pz, "wood_fence")
            k.put(px, LY + 2, pz, "wood_fence")
            k.put(px + 1, LY + 1, pz, "minecraft:hay_block", {"axis": "y"})
            k.titik("kandang_unta", px + 2, LY + 1, pz, "west")
            n += 1
    K.crates(k, 8, 78, 14, 84, LY + 1, 3)
    K.crates(k, 82, 80, 88, 86, LY + 1, 2)
    for x, z in ((6, 30), (90, 46), (10, 90), (88, 6), (40, 6), (58, 92)):
        palma(k, x, z, LY + 1, 7, None, False)
    for x in range(4, 94, 7):
        semak(k, x, (x * 13) % 90 + 3)
    # the start of the caravan road: a milestone stela by the road
    for y in range(LY + 1, LY + 5):
        k.put(54, y, 90, "ss_chis" if y == LY + 4 else "ss_smooth")
    k.put(54, LY + 5, 90, "brass_lamp", {"hanging": "false", "waterlogged": "false"})
    k.jangkar(40.5, LY + 1, 20.5, "tema_oasis/lapangan", yaw=YAW["east"])
    k.jangkar(53.5, LY + 1, 70.5, "kota_tema/tiba", yaw=YAW["north"])
    k.jangkar(68.5, LY + 1, 62.5, "tema_oasis/kandang", yaw=YAW["west"], unta=3, sewa=0)
    k.titik("tiba", 50, LY + 1, 70, "north")
    zona(k, 48, 30, d)
    ringkas(k)
    lots = ttl.potong_distrik(k, TEMA, d, nama={(0, 0): "lapangan_a", (1, 0): "lapangan_b", (0, 1): "lapangan_c", (1, 1): "lapangan_d"},
                              sisi=sisi, tinggi_min=24)
    for (i, j), lot in sorted(lots.items()):
        lot.simpan()
        name = "%s/%s" % (d, lot.nama.split("/")[-1])
        POOLS[pid(name)] = [lot.id]
        LOTS[d].append(ttl.lot_luar(-48 + 48 * i, -34 + 48 * j, pid(name)))


# ===================================================================================================== walls
def dinding_bumi(k, x0, x1, tinggi=12, y=LY, gerbang=None):
    """A rammed-earth wall segment along x in a 12-deep band (outside = z 0): a sloping glacis, a 4-thick packed-mud core
    with mud-brick faces and timber-lacing bands, a walk with triangular merlons and arrow slits, buttresses inside every
    12, a 3-wide road along the inside."""
    for x in range(x0, x1 + 1):
        if gerbang and gerbang[0] <= x <= gerbang[1]:
            continue
        k.stairs(x, y + 1, 0, "mudb_stairs", "south")
        k.put(x, y + 1, 1, "mud")
        k.stairs(x, y + 2, 1, "mudb_stairs", "south")
        for z in range(2, 6):
            for yy in range(y + 1, y + tinggi + 1):
                face = z in (2, 5)
                band = (yy - y) % 4 == 0
                k.put(x, yy, z, ("palm_wood" if band and face and x % 2 == 0 else "mudb") if face else "mud",
                      {"axis": "x"} if band and face and x % 2 == 0 else None)
        top = y + tinggi + 1
        for z in (3, 4, 5):
            k.put(x, top - 1, z, "mud")
        # merlons on the outer edge: stepped triangles
        m = x % 4
        if m in (0, 1):
            k.put(x, top, 2, "mudb")
            k.put(x, top + 1, 2, "mudb_slab" if m == 0 else "mudb", {"type": "bottom", "waterlogged": "false"} if m == 0 else None)
        else:
            k.put(x, top, 2, "mudb_wall")
        if x % 8 == 4:
            k.put(x, y + 7, 2, "minecraft:air")
            k.put(x, y + 7, 3, "minecraft:air")
        k.put(x, top, 5, "mudb_wall")
        # buttresses
        if x % 12 == 6:
            for yy in range(y + 1, y + tinggi - 2):
                k.put(x, yy, 6, "mudb")
            k.stairs(x, y + tinggi - 2, 6, "mudb_stairs", "south")
        # inside road
        for z in (9, 10, 11):
            k.put(x, y, z, "paving" if (x + z) % 5 else "paving_alt")
        if x % 16 == 8:
            lampu_jalan(k, x, 8)


def gen_tembok():
    # straight wall
    k = K.Kanvas(48, 30, 12, seed=tl._seed(F + "/tembok/lurus"), palette=PAL)
    tanah(k)
    dinding_bumi(k, 0, 47)
    # a stair up to the walk on the inside (both ends of the long walls meet towers)
    for i in range(10):
        k.stairs(30 + i, LY + 1 + i, 7, "mudb_stairs", "east")
        for yy in range(LY + 1, LY + 1 + i):
            k.put(30 + i, yy, 7, "mud")
    potong_pita(k, "tembok/lurus", 0, 47, 0, 11)
    pool_tembok("tembok/lurus", tid("tembok/lurus"))
    # corner tower
    k = K.Kanvas(12, 34, 12, seed=tl._seed(F + "/tembok/sudut"), palette=PAL)
    tanah(k)
    ttl.menara(k, TEMA, 0, 0, 11, LY, 15)
    for x in range(0, 11):
        for z in (0, 10):
            if x % 3 == 1:
                k.put(x, LY + 10, z, "minecraft:air")
    for z in range(0, 11):
        for x in (0, 10):
            if z % 3 == 1:
                k.put(x, LY + 10, z, "minecraft:air")
    k.put(5, LY + 15, 5, "brass_lamp", {"hanging": "false", "waterlogged": "false"})
    potong_pita(k, "tembok/sudut", 0, 11, 0, 11)
    pool_tembok("tembok/sudut", tid("tembok/sudut"))
    # the Gerbang Giok: 96 x 34 x 14 canvas cut into two 48-wide halves
    k = K.Kanvas(96, 36, 14, seed=tl._seed(F + "/tembok/gerbang"), palette=PAL)
    tanah(k)
    dinding_bumi(k, 0, 95, 12, LY, gerbang=(24, 71))
    # gatehouse body and the two watchtowers
    for (tx0, tx1) in ((24, 35), (60, 71)):
        k.fill(tx0, LY + 1, 0, tx1, LY + 20, 12, "mudb")
        k.air(tx0 + 2, LY + 1, 2, tx1 - 2, LY + 19, 10)
        for yy in range(LY + 5, LY + 20, 5):
            k.fill(tx0 + 2, yy, 2, tx1 - 2, yy, 10, "wood")
            k.put(tx0 + 3, yy, 3, "minecraft:air")
        K.ladder(k, tx0 + 3, LY + 1, LY + 20, 3, "south")
        for x in range(tx0, tx1 + 1):
            k.put(x, LY + 8, 0, "ss_cut")
            k.put(x, LY + 16, 0, "ss_cut")
            if x % 3 == 1:
                k.put(x, LY + 12, 0, "minecraft:air")
        k.fill(tx0, LY + 21, 0, tx1, LY + 21, 12, "ss_cut")
        parapet(k, tx0, 0, tx1, 12, LY + 22, role="mudb_wall", merlon="mudb")
        kubah(k, (tx0 + tx1) // 2, 6, LY + 22, 4, role="dome", top="gold", drum=1)
        for x in (tx0 + 2, tx1 - 2):
            k.put(x, LY + 18, 0, "minecraft:cyan_wall_banner", {"facing": "north"})
            k.put(x, LY + 17, 0, "minecraft:white_wall_banner", {"facing": "north"})
    k.fill(36, LY + 1, 0, 59, LY + 15, 12, "mudb")
    for x in range(36, 60):
        k.put(x, LY + 8, 0, "ss_cut")
        k.put(x, LY + 14, 0, "glazed_c", {"facing": "north"})
    k.fill(36, LY + 16, 0, 59, LY + 16, 12, "ss_cut")
    parapet(k, 36, 0, 59, 12, LY + 17, role="mudb_wall", merlon="mudb")
    # the arch: 7 wide (45..51) and 10 high through the whole depth, lined with jade bricks
    for z in range(0, 14):
        for x in range(45, 52):
            for yy in range(LY + 1, LY + 11):
                k.put(x, yy, z, "minecraft:air")
            k.put(x, LY, z, "ss_cut" if z % 4 else "jade")
        for yy in range(LY + 1, LY + 12):
            k.put(44, yy, z, "jade")
            k.put(52, yy, z, "jade")
        for x in range(45, 52):
            k.put(x, LY + 11, z, "jade")
        k.stairs(45, LY + 10, z, "jade_stairs", "east", "top")
        k.stairs(51, LY + 10, z, "jade_stairs", "west", "top")
    for x in range(43, 54):
        k.put(x, LY + 12, 0, "jade_dark")
    k.put(48, LY + 13, 0, "minecraft:gold_block")
    for x in (41, 55):
        for yy in range(LY + 1, LY + 9):
            k.put(x, yy, 0, "ss_chis" if yy % 3 == 0 else "ss_smooth")
    k.jangkar(48.5, LY + 1, 7.5, "tema_oasis/gerbang", yaw=YAW["north"], lebar=7)
    # torch-free lighting: brass lamps under the arch, banners on the gatehouse
    for z in (2, 11):
        lampu_gantung(k, 48, LY + 11, z, 1)
    for x in (38, 57):
        k.put(x, LY + 12, 0, "minecraft:cyan_wall_banner", {"facing": "north"})
    potong_pita(k, "tembok/gerbang_a", 0, 47, 0, 13)
    potong_pita(k, "tembok/gerbang_b", 48, 95, 0, 13)
    pool_tembok("tembok/gerbang_a", tid("tembok/gerbang_a"))
    pool_tembok("tembok/gerbang_b", tid("tembok/gerbang_b"))


# ===================================================================================================== main
# ===================================================================================================== DESIGN 42 (42W-T)
# 42.12.1 row tema: the night street, the recruitment office (2 booths), 10 kavling of gaya "oasis", the domed desert
# cemetery (makam gurun berkubah) and the Ghost Market / rentable stall points, all on free lots OUTSIDE the
# rammed-earth wall inside the keep-out zone of their district (tools/tema_hidup_kit.py; off the 48-grid, no side code).
# Flat mud-brick roofs with parapets and domes; every campfire stays unlit.
GAYA42 = {
    "tema": "oasis", "pal": PAL, "pondasi": "minecraft:sandstone",
    "tanah": ["minecraft:sand", "minecraft:sand", "minecraft:sand", "minecraft:sandstone", "minecraft:sand"],
    "jalan": ["minecraft:smooth_sandstone", "minecraft:cut_sandstone", "minecraft:smooth_sandstone",
              "minecraft:sandstone"],
    "alas": "minecraft:cut_sandstone", "alas_tangga": "minecraft:sandstone_stairs",
    "lantai": ["minecraft:smooth_sandstone", "minecraft:terracotta"],
    "dinding": ["minecraft:mud_bricks", "minecraft:packed_mud", "minecraft:mud_bricks"],
    "tiang": "minecraft:cut_sandstone", "balok": "minecraft:stripped_jungle_log",
    "jendela": "minecraft:jungle_trapdoor", "pintu": "minecraft:jungle_door", "plafon": "minecraft:jungle_planks",
    "atap": "kubah", "atap_tangga": "minecraft:sandstone_stairs", "atap_slab": "minecraft:smooth_sandstone_slab",
    "atap_penuh": "minecraft:smooth_sandstone", "parapet": "minecraft:mud_brick_wall", "kubah": "minecraft:cut_sandstone",
    "puncak": "minecraft:prismarine_bricks",
    "kain": ("minecraft:orange_wool", "minecraft:white_wool"), "warna": "orange", "lampu": "minecraft:lantern",
    "pohon": "palma", "kavling": "oasis", "makam": ("gurun", "kubah"),
    "rencana": (("rumah_besar", "kedai_teh", "gudang", "rumah_toko", "bengkel", "kedai_arak", "sekolah"),
                ("rumah_kecil", "taman", "kuil_kecil", "menara_jaga", "rumah_kecil")),
    "bilik_warna": ("cyan", "orange"),
}
# (district, dx, dz, lot): south band west of the Gerbang Giok beside the caravan field (night street, kavling), east
# of it (office, kavling); the cemetery in the dunes west of the Bazar Barat wall
LOT42 = (("hunian_tanah", -48, 62, "hidup42/kavling_barat"), ("hunian_tanah", 0, 62, "hidup42/jalan_malam"),
         ("karavanserai", -48, 62, "hidup42/kantor_rekrut"), ("karavanserai", 0, 62, "hidup42/kavling_timur"),
         ("bazar_barat", -112, -24, "hidup42/makam"))


def lot42_buat(dry=False):
    """Writes the DESIGN 42 lots and registers them (lapak_sewa 0-4, pasar_hantu 0-3, nisan 0-31)."""
    out = [thk.lot_jalan_malam(F, GAYA42, lapak0=0, hantu0=0),
           thk.lot_kantor_rekrut(F, GAYA42, lapak0=2),
           thk.lot_kavling(F, GAYA42, "hidup42/kavling_barat", 0),
           thk.lot_kavling(F, GAYA42, "hidup42/kavling_timur", 1, hantu=3),
           thk.lot_makam(F, GAYA42, nisan0=0, hantu0=1)]
    if not dry:
        for distrik, dx, dz, name in LOT42:
            POOLS[pid(name)] = [tid(name)]
            LOTS[distrik].append(ttl.lot_luar(dx, dz, pid(name), 0))
    return out


def main():
    gen_tembok()
    gen_kuil_air()
    gen_istana_pasir()
    gen_kebun_palma()
    gen_bazar_barat()
    gen_mata_air()
    gen_bazar_timur()
    gen_hunian_tanah()
    gen_gerbang_giok()
    gen_karavanserai()
    gen_lapangan_kafilah()
    lot42_buat()
    for name, ids in sorted(POOLS.items()):
        ttl.tulis_pool_tema(TEMA, name.split("/", 2)[2], ids)
    for d in ORDER:
        ttl.tulis_distrik(TEMA, d, LOTS[d])
    ttl.tulis_processor_tema(TEMA)
    ttl.cek_semua()
    total = 0
    for path, t in structgen._SAVED:
        total += os.path.getsize(path)
    print("oasis: %d templates, %d bytes NBT, %d districts" % (len(structgen._SAVED), total, len(ORDER)))


if __name__ == "__main__":
    main()
