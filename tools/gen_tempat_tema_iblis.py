"""Kota Iblis world generation (DESIGN 38.T.7, 38.T.2.3-38.T.2.4; module 38TK-I): the nine districts of Kota Darah Senja.

Writes every NBT lot (data/kultivator/structure/kota_tema/iblis/**), the template pools (base + the styles "merah" and
"rawa"), the processor lists kota_tema/iblis_* and the nine kultivator:tata_letak structures
kota_tema/iblis/<distrik>. The structure sets (rencana_tempat, salts 380441-380449 of Tema.java), the shared tags and the
pack dunia_kotatema are written by 38TK inti (tools/gen_tempat_tema_data.py). Run:

    python tools/gen_tempat_tema_iblis.py            (then python tools/cek_tempat.py)

Plan (38.T.7.2, before rotation, district cells 96 blocks = 2 x 2 lots, north = -z):

             x = -1                 x = 0                  x = +1
 z = -1   [menara_terlarang]    [istana_darah]        [penjara_tawanan]
 z =  0   [pasar_gelap] (ZP)    [arena_darah]         [sarang_kontrak]
 z = +1   [kumuh_iblis]         [gerbang_tengkorak]   [kedai_racun] (ZP)

Every district is drawn on one 96 x H x 96 canvas (kota_besar_kit.Kanvas) and cut into its four lots, so the arena, the
palace and the tower stay whole across lot borders. The city wall (blackstone and polished basalt, 16 high over the
street, spiked merlons of iron bars and end rods, never dripstone) runs in an 8-block band just outside the nine cells,
with a watch tower piece on each outer side, pointed corner towers and, in the south, the Gerbang Tengkorak: a skull of
bone and basalt 22 high whose lower jaw and iron double door are the entity kultivator:gerbang_tengkorak (anchor
kota_tema/gerbang_iblis), never blocks that change. Zona Perjanjian = the districts pasar_gelap and kedai_racun (box points
zona_perjanjian). No fire, lava or lightning rod anywhere; light is soul lanterns, shroomlight, froglights and candles.
Only world generation in NEW chunks ever places these templates (DESIGN 38.0 rule 1). Deterministic: same bytes on
every run.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import kota_besar_kit as K  # noqa: E402
import tempat_tema_lib as ttl  # noqa: E402
from kota_besar_kit import LY, FACE, OPP  # noqa: E402
import hias_tema_oi as HO  # noqa: E402  (registers the demon-city room styles into hias_tema)

F = "kota_tema/iblis"
NS = "kultivator:"
RENCANA = "kota_tema_iblis"
SEL = 96
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}

# district id -> grid (x, z) before rotation (Tema.java IBLIS)
DISTRIK = {
    "menara_terlarang": (-1, -1), "istana_darah": (0, -1), "penjara_tawanan": (1, -1),
    "pasar_gelap": (-1, 0), "arena_darah": (0, 0), "sarang_kontrak": (1, 0),
    "kumuh_iblis": (-1, 1), "gerbang_tengkorak": (0, 1), "kedai_racun": (1, 1),
}
ORDER = list(DISTRIK)
ZP = ("pasar_gelap", "kedai_racun")

# The vanilla biomes of the three styles (38.T.2.4): base "hitam"; "merah" badlands; "rawa" swamps.
GAYA_BIOMES = {
    "merah": ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"],
    "rawa": ["minecraft:swamp", "minecraft:mangrove_swamp"],
}

# ===================================================================================================== palette
PAL = dict(K.PAL)
PAL.update({
    "floor": "minecraft:polished_blackstone", "floor_alt": "minecraft:polished_blackstone_bricks",
    "found": "minecraft:blackstone", "wall": "minecraft:polished_blackstone_bricks", "wall_alt": "minecraft:dark_oak_planks",
    "plaster": "minecraft:black_terracotta", "plaster_alt": "minecraft:gray_terracotta",
    "pillar": "minecraft:red_nether_bricks", "trim": "minecraft:polished_basalt",
    "roof": "minecraft:nether_brick_stairs", "roof_slab": "minecraft:nether_brick_slab", "roof_full": "minecraft:nether_bricks",
    "accent": "minecraft:crying_obsidian", "jade": "minecraft:crying_obsidian", "glow": "minecraft:soul_lantern",
    "lamp": "minecraft:pearlescent_froglight", "path": "minecraft:polished_blackstone", "path_alt": "minecraft:smooth_basalt",
    "fence": "minecraft:dark_oak_fence", "window": "minecraft:iron_bars", "carpet": "minecraft:black_carpet",
    "beam": "minecraft:stripped_dark_oak_log", "red": "minecraft:red_nether_bricks", "red_wall": "minecraft:red_nether_brick_wall",
    "kerb": "minecraft:polished_blackstone_bricks", "kerb_alt": "minecraft:cracked_polished_blackstone_bricks",
    "kerb_wall": "minecraft:polished_blackstone_brick_wall", "kerb_stairs": "minecraft:polished_blackstone_brick_stairs",
    "kerb_slab": "minecraft:polished_blackstone_brick_slab", "kerb_chisel": "minecraft:chiseled_polished_blackstone",
    "paving": "minecraft:polished_blackstone", "paving_alt": "minecraft:smooth_basalt",
    "paving_slab": "minecraft:polished_blackstone_slab", "paving_stairs": "minecraft:polished_blackstone_stairs",
    "gutter": "minecraft:smooth_basalt", "rail": "minecraft:dark_oak_fence", "wood": "minecraft:dark_oak_planks",
    "wood_alt": "minecraft:crimson_planks", "wood_stairs": "minecraft:dark_oak_stairs", "wood_slab": "minecraft:dark_oak_slab",
    "wood_trapdoor": "minecraft:dark_oak_trapdoor", "wood_fence": "minecraft:dark_oak_fence",
    "wood_gate": "minecraft:dark_oak_fence_gate", "log": "minecraft:dark_oak_log", "shelf": "minecraft:dark_oak_shelf",
    "door": "minecraft:dark_oak_door", "light_wood": "minecraft:crimson_planks", "light_slab": "minecraft:crimson_slab",
    "light_stairs": "minecraft:crimson_stairs", "banner": "black", "banner_alt": "red",
    "cloth": "minecraft:black_wool", "cloth_alt": "minecraft:red_wool", "ground": "minecraft:podzol",
    "ground_alt": "minecraft:mycelium", "statue": "minecraft:polished_blackstone",
    "statue_top": "minecraft:chiseled_polished_blackstone", "flower_tall": "minecraft:rose_bush",
    "flower_tall_alt": "minecraft:rose_bush", "flower": "minecraft:crimson_roots", "flower_alt": "minecraft:red_mushroom",
    "bush": "minecraft:dead_bush", "leaves": "minecraft:dark_oak_leaves", "trunk": "minecraft:dark_oak_log",
    "bed": "minecraft:red_bed", "rug": "minecraft:red_carpet", "rug_alt": "minecraft:black_carpet",
    "potted": "minecraft:potted_crimson_roots", "tile": "minecraft:nether_bricks", "tile_stairs": "minecraft:nether_brick_stairs",
    "tile_slab": "minecraft:nether_brick_slab", "imperial": "minecraft:smooth_basalt",
    "imperial_slab": "minecraft:polished_blackstone_slab", "imperial_stairs": "minecraft:polished_blackstone_stairs",
    "gold": "minecraft:gilded_blackstone", "glass": "minecraft:black_stained_glass_pane",
    # Kota Iblis own roles
    "tulang": "minecraft:bone_block", "basal": "minecraft:polished_basalt", "basal_kasar": "minecraft:basalt",
    "batu_hitam": "minecraft:blackstone", "bata_hitam": "minecraft:polished_blackstone_bricks",
    "bata_merah": "minecraft:red_nether_bricks", "nether": "minecraft:nether_bricks",
    "darah": "minecraft:red_concrete", "kaca_darah": "minecraft:red_stained_glass", "cahaya_darah": "minecraft:shroomlight",
})

# ===================================================================================================== bookkeeping
LOTS = {d: [] for d in ORDER}   # district -> [{dx, dz, pool, putar}]
POOLS = {}                       # pool id (path under F) -> ([template ids], utuh)


def tid(name):
    return "%s%s/%s" % (NS, F, name)


def daftar_bebas(distrik, dx, dz, pool, putar=0, utuh=True, templates=None):
    LOTS[distrik].append({"dx": dx, "dz": dz, "pool": pool, "putar": putar})
    POOLS.setdefault(pool, ([tid(t) for t in (templates or [pool])], utuh))


def kanvas(distrik, h=40, seed_extra=0):
    return K.Kanvas(SEL, h, SEL, seed=tl._seed(F + "/" + distrik) + seed_extra, palette=PAL)


# rooms to dress (D-TK2 detail pass): registered while building, dressed in simpan_distrik() after everything else
# stands, so the dressing only ever fills what is still empty (hias_tema never overwrites a placed block)
_RUANG = []


def ruang(k, x0, z0, x1, z1, f, gaya, pulau=None, **kw):
    """Registers one room of canvas k (inner floor box, floor level f, room style of hias_tema / hias_tema_oi; `pulau`
    = furniture islands for the open middle of a large hall, see hias_tema_oi.tengah)."""
    _RUANG.append((k, (x0, z0, x1, z1, f, gaya), dict(kw, pulau=pulau)))


def hias(k):
    """Dresses every registered room of canvas k (called from simpan_distrik())."""
    n = 0
    for entry in [r for r in _RUANG if r[0] is k]:
        n += HO.kamar(k, *entry[1], **entry[2])
        _RUANG.remove(entry)
    return n


def sisi_lot(distrik, i, j, gabung=()):
    """Side codes (n, e, s, w) of lot (i, j): T toward the city wall, B on a seam a building crosses, else J."""
    gx, gz = DISTRIK[distrik]
    n = "T" if gz == -1 and j == 0 else ("B" if (i, j, "n") in gabung else "J")
    s = "T" if gz == 1 and j == 1 else ("B" if (i, j, "s") in gabung else "J")
    w = "T" if gx == -1 and i == 0 else ("B" if (i, j, "w") in gabung else "J")
    e = "T" if gx == 1 and i == 1 else ("B" if (i, j, "e") in gabung else "J")
    return n, e, s, w


def gabung_seam(seams):
    """{((i, j), (i2, j2))} merged seams -> {(i, j, side)} for both lots."""
    out = set()
    for (a, b) in seams:
        (i, j), (i2, j2) = a, b
        if i2 == i + 1:
            out.add((i, j, "e"))
            out.add((i2, j2, "w"))
        elif j2 == j + 1:
            out.add((i, j, "s"))
            out.add((i2, j2, "n"))
    return out


def simpan_distrik(k, distrik, names, seams=(), utuh=(), tinggi_min=24, varian=None):
    """Cuts a district canvas into its four lots, writes the side codes, saves and registers them.
    names[(i, j)] = lot name (path under the district). varian = {(i, j): [extra canvases cut the same way]} for lots
    whose pool holds several house layouts."""
    hias(k)
    gab = gabung_seam(seams)
    full = {ij: "%s/%s" % (distrik, n) for ij, n in names.items()}
    lots = K.potong(k, full, tinggi_min=tinggi_min, folder=F)
    for (i, j), lot in sorted(lots.items()):
        lot.sisi(*sisi_lot(distrik, i, j, gab))
        lot.simpan()
        pool_templates = [full[(i, j)]]
        for extra_name in (varian or {}).get((i, j), []):
            pool_templates.append(extra_name)
        LOTS[distrik].append({"dx": -48 + 48 * i, "dz": -48 + 48 * j, "pool": full[(i, j)], "putar": 0})
        POOLS.setdefault(full[(i, j)], ([tid(t) for t in pool_templates], names[(i, j)] in utuh))
    return lots


# ===================================================================================================== small details
def kayu_tegak(p, x, y, z, role="log"):
    p.put(x, y, z, role, K.axis(p, role, "y"))


def tiang(p, x, y0, y1, z, role="basal"):
    for y in range(y0, y1 + 1):
        p.put(x, y, z, role, K.axis(p, role, "y"))


def rantai(p, x, y0, y1, z, axis="y"):
    for y in range(y0, y1 + 1):
        p.put(x, y, z, "minecraft:iron_chain", {"axis": axis, "waterlogged": "false"})


def paku(p, x, y, z):
    """A spike: iron bars with an end rod tip (no dripstone: it could fall)."""
    p.put(x, y, z, "minecraft:iron_bars")
    p.put(x, y + 1, z, "minecraft:end_rod", {"facing": "up"})


def lentera_jiwa(p, x, y, z, hanging=True):
    p.put(x, y, z, "minecraft:soul_lantern", {"hanging": "true" if hanging else "false", "waterlogged": "false"})
    if hasattr(p, "lights"):
        p.lights.append((x, y, z))


def obor_jiwa(p, x, z, y=LY, h=4):
    """A tall soul-lantern post: a blackstone brick wall shaft, a chain, an arm and a hanging soul lantern."""
    p.put(x, y + 1, z, "kerb_chisel")
    for i in range(2, h + 1):
        p.put(x, y + i, z, "kerb_wall")
    p.put(x, y + h + 1, z, "minecraft:polished_basalt", {"axis": "y"})
    rantai(p, x, y + h + 2, y + h + 2, z)
    lentera_jiwa(p, x, y + h + 3, z, hanging=False)


def tiang_lentera_rantai(p, x, z, y=LY, h=6, arah="east"):
    """A gallows-like lamp: basalt post, a beam out over the street and a soul lantern on two chains."""
    tiang(p, x, y + 1, y + h, z)
    dx, dz = FACE[arah]
    for i in (1, 2):
        p.put(x + dx * i, y + h, z + dz * i, "minecraft:polished_basalt", {"axis": "x" if dx else "z"})
    rantai(p, x + dx * 2, y + h - 2, y + h - 1, z + dz * 2)
    lentera_jiwa(p, x + dx * 2, y + h - 3, z + dz * 2)


def panji(p, x, y, z, facing, warna=None):
    K.wall_banner(p, x, y, z, facing, warna or ("black" if (x + z) % 2 else "red"))


def tengkorak(p, x, y, z, rot=0, layu=False):
    p.put(x, y, z, "minecraft:wither_skeleton_skull" if layu else "minecraft:skeleton_skull", {"rotation": str(rot % 16)})


def lilin(p, x, y, z, n=None):
    K.candle(p, x, y, z, n or (1 + (x * 7 + z * 3) % 4), "red" if (x + z) % 3 else "black")


def rak_senjata(p, x, y, z, facing, n=3):
    """A weapon rack: dark oak frame, three grindstone-and-bar 'blades', chains."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(n):
        px, pz = x + rx * i, z + rz * i
        p.put(px, y, pz, "minecraft:dark_oak_fence")
        p.put(px, y + 1, pz, "minecraft:iron_bars")
        p.put(px, y + 2, pz, "minecraft:end_rod", {"facing": "up"} if i % 2 == 0 else {"facing": "down"})
    for i in (-1, n):
        px, pz = x + rx * i, z + rz * i
        p.put(px, y, pz, "minecraft:dark_oak_log", {"axis": "y"})
        p.put(px, y + 1, pz, "minecraft:dark_oak_log", {"axis": "y"})
        p.put(px, y + 2, pz, "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})


def rak_rantai(p, x, y, z, along="x", n=4):
    """A wall of hanging chains with shackles (iron trapdoors) - the enforcers' chain racks."""
    for i in range(n):
        px, pz = (x + i, z) if along == "x" else (x, z + i)
        rantai(p, px, y + 1, y + 3, pz)
        p.put(px, y + 4, pz, "minecraft:polished_basalt", {"axis": "x" if along == "x" else "z"})
        if i % 2 == 0:
            p.put(px, y, pz, "minecraft:iron_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                        "powered": "false", "waterlogged": "false"})


def armor_stand(p, x, y, z, yaw=0.0, kit="hitam"):
    """An armor stand with iron or netherite-look gear (no tooltips, plain items)."""
    items = {
        "hitam": ["minecraft:chainmail_boots", "minecraft:chainmail_leggings", "minecraft:iron_chestplate", "minecraft:chainmail_helmet"],
        "kulit": ["minecraft:leather_boots", "minecraft:leather_leggings", "minecraft:leather_chestplate", "minecraft:air"],
    }[kit]
    eq = {}
    for slot, item in zip(("feet", "legs", "chest", "head"), items):
        if item != "minecraft:air":
            eq[slot] = {"id": item, "count": nbt.Int(1)}
    p.entity(x + 0.5, y, z + 0.5, {"id": "minecraft:armor_stand", "ShowArms": nbt.Byte(1),
                                   "Rotation": [nbt.Float(yaw), nbt.Float(0.0)], "equipment": eq})


def tong_minyak_dekor(p, x, y, z):
    p.put(x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(x, y + 1, z, "minecraft:black_carpet")


def meja(p, x, y, z, atas=None):
    K.table(p, x, y, z, atas if atas is not None else "minecraft:dark_oak_pressure_plate")


def kursi(p, x, y, z, facing, titik=True):
    K.chair(p, x, y, z, facing)
    if titik:
        p.titik("kursi", x, y, z, facing)


def ranjang(p, x, y, z, facing="north", titik=True, warna="red"):
    K.bed(p, x, y, z, facing, role="minecraft:%s_bed" % warna)
    if titik:
        p.titik("kasur", x, y, z, OPP[facing])


def peti(p, x, y, z, loot=None, facing="south"):
    K.chest(p, x, y, z, loot, facing)


def tirai(p, x0, x1, z, y0, y1, along="x", warna="red"):
    """A curtain of wool with a carpet hem (red silk of the stages and the Lord's box)."""
    for y in range(y0, y1 + 1):
        for i in range(x0, x1 + 1):
            px, pz = (i, z) if along == "x" else (z, i)
            if y == y0:
                p.put(px, y, pz, "minecraft:%s_carpet" % warna)
            else:
                p.put(px, y, pz, "minecraft:%s_wool" % warna)


def kolam_darah(p, x0, z0, x1, z1, y, tepi="kerb"):
    """A shallow 'blood pool' WITHOUT any new fluid (38.T.7.2 note): red concrete under red stained glass, shroomlight
    hidden under the rim (light >= 8), the ripple is a client effect. Floor at y (the glass), rim one higher."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                p.put(x, y - 1, z, "cahaya_darah")
                p.put(x, y, z, tepi)
                p.slab(x, y + 1, z, "kerb_slab")
            else:
                p.put(x, y - 1, z, "darah")
                p.put(x, y, z, "kaca_darah")


def jalan_tulang(p, x0, z0, x1, z1, y=LY, along="z"):
    """A bone-edged avenue: basalt and blackstone paving with bone kerbs and a red-brick centre line."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (along == "z" and x in (x0, x1)) or (along == "x" and z in (z0, z1)):
                p.put(x, y, z, "tulang", {"axis": along})
            elif (along == "z" and abs(x - (x0 + x1) / 2.0) < 1) or (along == "x" and abs(z - (z0 + z1) / 2.0) < 1):
                p.put(x, y, z, "bata_merah" if ((z if along == "z" else x) % 3) else "kerb_chisel")
            else:
                p.put(x, y, z, "paving" if (x * 3 + z * 5) % 7 else "paving_alt")


def tanah_kota(k, distrik):
    """Street floor and the 3-block ring road round the whole district canvas. No foundation layers: beard_box raises
    the ground to the street (piece ground level delta 5), and the 38.T.2.4 budget (1.2 MB gzip per theme) is spent on
    the buildings instead."""
    sx, _, sz = k.size
    K.paving(k, 0, 0, sx - 1, sz - 1, LY, role="floor", alt="floor_alt", alt_chance=0.08)
    for (x0, z0, x1, z1) in ((0, 0, sx - 1, 2), (0, sz - 3, sx - 1, sz - 1), (0, 0, 2, sz - 1), (sx - 3, 0, sx - 1, sz - 1)):
        K.paving(k, x0, z0, x1, z1, LY, role="path", alt="path_alt", alt_chance=0.18)
    # gutter line of basalt slabs on the inner edge of the ring road
    for x in range(3, sx - 3):
        for z in (3, sz - 4):
            if k.get(x, LY + 1, z) is None:
                k.put(x, LY, z, "gutter")
    for z in range(3, sz - 3):
        for x in (3, sx - 4):
            if k.get(x, LY + 1, z) is None:
                k.put(x, LY, z, "gutter")
    # life: the ring road as a patrol path of the enforcers (jalan points, jalur "keliling")
    pts = [(1, 1), (47, 1), (94, 1), (94, 47), (94, 94), (47, 94), (1, 94), (1, 47)]
    for i, (x, z) in enumerate(pts):
        k.titik("jalan", x, LY + 1, z, "south", {"urut": i, "jalur": "keliling"})
    # every district is protected for Mode Kehancuran and named for the zone index of 38TK inti
    k.jangkar(48.5, LY + 1, 48.5, "kota_tema/zona", tema="iblis", distrik=distrik, protect=72)


def lampu_jalan(k, pts, y=LY):
    for (x, z) in pts:
        if k.get(x, y + 1, z) is None:
            obor_jiwa(k, x, z, y)


# ----------------------------------------------------------------------------------------------- houses
def atap_runcing(p, x0, z0, x1, z1, y, role="roof", spike=True):
    """A steep pointed hip roof (two stair layers per ring) with a spike finial - the Kota Iblis roof line."""
    i = 0
    yy = y
    while x0 + i <= x1 - i and z0 + i <= z1 - i:
        a0, b0, a1, b1 = x0 + i, z0 + i, x1 - i, z1 - i
        for rep in range(2 if i > 0 else 1):
            if a0 == a1 or b0 == b1:
                for x in range(a0, a1 + 1):
                    for z in range(b0, b1 + 1):
                        p.put(x, yy, z, "roof_full")
                continue
            for x in range(a0 + 1, a1):
                p.stairs(x, yy, b0, role, "south")
                p.stairs(x, yy, b1, role, "north")
            for z in range(b0 + 1, b1):
                p.stairs(a0, yy, z, role, "east")
                p.stairs(a1, yy, z, role, "west")
            p.stairs(a0, yy, b0, role, "south", shape="outer_left")
            p.stairs(a1, yy, b0, role, "south", shape="outer_right")
            p.stairs(a0, yy, b1, role, "north", shape="outer_right")
            p.stairs(a1, yy, b1, role, "north", shape="outer_left")
            if rep == 1:
                for x in range(a0 + 1, a1):
                    for z in range(b0 + 1, b1):
                        p.put(x, yy, z, "roof_full")
            yy += 1
        if i == 0:
            for cx, cz, f in ((a0, b0, "south"), (a1, b0, "south"), (a0, b1, "north"), (a1, b1, "north")):
                p.stairs(cx, yy, cz, role, f, half="top")
        i += 1
    if spike:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        p.put(cx, yy, cz, "bata_merah")
        paku(p, cx, yy + 1, cz)
    return yy


def rumah_batu(p, x0, z0, w, d, face="south", y=LY, storeys=1, sh=5, kamar=2, varian=0, nama=None, rich=False):
    """A demonic cultivator's black stone house: red-brick corner piers, iron-barred windows, a dark oak door with an
    iron-bound frame, an inner meditation corner, beds, a padlocked chest (no loot: the residents' own), shelves and
    a steep pointed roof. Returns the door (x, z) outside."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    p.fill(x0, y, z0, x1, y, z1, "wood")
    for s in range(storeys):
        f = y + s * sh
        p.walls(x0, f + 1, z0, x1, f + sh, z1, role="kerb" if s == 0 else "plaster", pillar="red", windows=0,
                interior_air=True)
        for x in range(x0 + 2, x1 - 1, 3):
            for z, fc in ((z0, "north"), (z1, "south")):
                p.put(x, f + 2, z, "minecraft:iron_bars")
                p.put(x, f + 3, z, "minecraft:iron_bars")
        for z in range(z0 + 2, z1 - 1, 3):
            for x in (x0, x1):
                p.put(x, f + 2, z, "minecraft:iron_bars")
                p.put(x, f + 3, z, "minecraft:iron_bars")
        K.beam_band(p, x0, z0, x1, z1, f + sh, role="trim")
        if s > 0:
            p.fill(x0 + 1, f, z0 + 1, x1 - 1, f, z1 - 1, "wood")
            p.air(x1 - 2, f, z0 + 1, x1 - 1, f, z0 + 2)
            K.ladder(p, x1 - 1, f - sh + 1, f, z0 + 1, "south")
    top = y + storeys * sh
    p.fill(x0 + 1, top, z0 + 1, x1 - 1, top, z1 - 1, "wood")
    atap_runcing(p, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top + 1)
    # door
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if face == "south":
        dxz = (cx, z1)
        out = (cx, z1 + 1)
    elif face == "north":
        dxz = (cx, z0)
        out = (cx, z0 - 1)
    elif face == "east":
        dxz = (x1, cz)
        out = (x1 + 1, cz)
    else:
        dxz = (x0, cz)
        out = (x0 - 1, cz)
    K.door(p, dxz[0], y + 1, dxz[1], face)
    p.put(dxz[0], y + 3, dxz[1], "kerb_chisel")
    # interior (ground floor): beds along the back, a meditation mat, a table, a chest
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    back_z = iz0 if face == "south" else iz1
    bed_face = "north" if face == "south" else "south"
    for b in range(kamar):
        bx = ix0 + b * 2
        if bx <= ix1 - 2:
            ranjang(p, bx, y + 1, back_z, bed_face, warna="red" if (b + varian) % 2 else "black")
    peti(p, ix1, y + 1, back_z, None, "south" if face == "south" else "north")
    p.titik("peti_milik", ix1, y + 1, (iz0 + iz1) // 2, "east")
    mx, mz = ix0 + 1, (iz0 + iz1) // 2 + (1 if face == "south" else -1)
    if ix1 - ix0 >= 4:
        p.put(mx, y + 1, mz, "minecraft:red_carpet")
        p.titik("meditasi", mx, y + 1, mz, face)
        lilin(p, mx - 1 if mx - 1 >= ix0 else mx + 1, y + 1, mz)
    meja(p, ix1 - 1, y + 1, (iz0 + iz1) // 2)
    kursi(p, ix1 - 2, y + 1, (iz0 + iz1) // 2, "east")
    K.lit(p, cx, y + sh, cz, chain=1)
    if rich:
        p.put(ix0, y + 1, (iz0 + iz1) // 2, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false",
                                                                         "has_bottle_2": "true"})
        K.bookshelf_wall(p, ix0, iz0 if face == "south" else iz1, ix0, iz0 if face == "south" else iz1, y + 2, y + 3,
                         face="east")
    for s in range(storeys):
        ruang(p, ix0, iz0, ix1, iz1, y + s * sh, "rumah_iblis" if s == 0 else "iblis_inap", isi=0.45 if s == 0 else 0.6,
              rug=s > 0, seed=varian * 5 + s)
    p.kotak("bangunan", x0, y, z0, x1, top, z1, face, nama=nama or "rumah_batu")
    p.titik("pintu", out[0], y + 1, out[1], face)
    return out


def gubuk(p, x0, z0, w, d, face="south", y=LY, kind=0):
    """A slum shack (kumuh_iblis): patched dark oak and crimson planks, a lean-to roof of slabs with wool patches, one
    mat bed, a stove (unlit furnace), sacks (hay), a rag curtain door."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    woods = ("minecraft:dark_oak_planks", "minecraft:crimson_planks", "minecraft:mangrove_planks", "minecraft:spruce_planks")
    p.fill(x0, y, z0, x1, y, z1, "minecraft:coarse_dirt")
    p.air(x0 + 1, y + 1, z0 + 1, x1 - 1, y + 3, z1 - 1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            for dy in range(1, 4):
                if corner:
                    p.put(x, y + dy, z, "minecraft:dark_oak_log", {"axis": "y"})
                else:
                    p.put(x, y + dy, z, woods[(x * 3 + z * 5 + dy + kind) % len(woods)])
    # holes in the walls (a window and a crack)
    p.put(x0 + 1, y + 2, z0, "minecraft:dark_oak_fence")
    p.put(x1, y + 2, (z0 + z1) // 2, "minecraft:air")
    # lean-to roof falling to the back
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            hi = z <= (z0 + z1) // 2
            if (x + z + kind) % 7 == 0:
                p.put(x, y + 4, z, "minecraft:black_carpet" if hi else "minecraft:brown_carpet")
            else:
                p.slab(x, y + 4, z, "minecraft:dark_oak_slab" if (x + z) % 3 else "minecraft:crimson_slab",
                       "top" if hi else "bottom")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if face == "south":
        door_at, out = (cx, z1), (cx, z1 + 1)
    elif face == "north":
        door_at, out = (cx, z0), (cx, z0 - 1)
    elif face == "east":
        door_at, out = (x1, cz), (x1 + 1, cz)
    else:
        door_at, out = (x0, cz), (x0 - 1, cz)
    p.put(door_at[0], y + 1, door_at[1], "minecraft:air")
    p.put(door_at[0], y + 2, door_at[1], "minecraft:air")
    p.put(door_at[0], y + 3, door_at[1], "minecraft:black_wool")
    ranjang(p, x0 + 1, y + 1, z0 + 1 if face == "south" else z1 - 1, "east", warna="brown")
    p.put(x1 - 1, y + 1, z0 + 1 if face == "south" else z1 - 1, "minecraft:furnace", {"facing": face, "lit": "false"})
    p.put(x1 - 1, y + 1, cz, "minecraft:hay_block", {"axis": "y"})
    p.kotak("bangunan", x0, y, z0, x1, y + 4, z1, face, nama="gubuk")
    p.titik("pintu", out[0], y + 1, out[1], face)
    return out


# ===================================================================================================== the city wall
WALL_H = 16      # over the street floor
WALK = 13        # walkway over the street floor
BAND = 8
DEPTH = 11       # template depth: outer margin 2, band 8, inner margin 1


def _wall_run(p, x0, x1, y=LY, z0=2, depth=BAND, merlon=True, slits=True, tangga_x=None):
    """A wall run along x (outside north at z0, inside at z0 + depth - 1): basalt plinth, blackstone body with
    polished basalt ribs every 4, a red-brick drip course, a walkway at +WALK, merlons every 2 crowned with spikes."""
    zi = z0 + depth - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, zi + 1):
            p.put(x, y - 1, z, "found")
            p.put(x, y, z, "kerb")
        rib = (x % 4 == 0)
        for yy in range(y + 1, y + WALK):
            for z in range(z0, zi + 1):
                if z in (z0, zi):
                    if yy <= y + 2:
                        role = "minecraft:polished_basalt" if z == z0 else "kerb"
                        p.put(x, yy, z, role, {"axis": "y"} if role.endswith("basalt") else None)
                        continue
                    if rib:
                        p.put(x, yy, z, "minecraft:polished_basalt", {"axis": "y"})
                        continue
                    if yy == y + WALK - 2:
                        p.put(x, yy, z, "bata_merah")
                        continue
                    p.put(x, yy, z, "batu_hitam" if (x + yy) % 5 else "minecraft:gilded_blackstone" if (x * yy) % 23 == 0 else "batu_hitam")
                else:
                    p.put(x, yy, z, "batu_hitam")
        for z in range(z0 + 1, zi):
            p.put(x, y + WALK, z, "paving" if z not in (z0 + 2, zi - 2) else "minecraft:polished_blackstone_bricks")
        p.put(x, y + WALK, z0, "kerb")
        p.put(x, y + WALK, zi, "kerb")
        # outer parapet: merlons every 2 with spikes, crenels with a slab
        p.put(x, y + WALK + 1, z0, "kerb")
        if merlon and x % 2 == 0:
            p.put(x, y + WALK + 2, z0, "kerb")
            p.put(x, y + WALK + 3, z0, "kerb_chisel")
            paku(p, x, y + WALK + 4, z0)
        else:
            p.slab(x, y + WALK + 2, z0, "kerb_slab")
        if slits and x % 6 == 3:
            p.put(x, y + WALK - 5, z0, "minecraft:iron_bars")
            p.put(x, y + WALK - 6, z0, "minecraft:iron_bars")
        # inner parapet low wall, soul lanterns every 8
        p.put(x, y + WALK + 1, zi, "kerb_wall")
        if x % 8 == 4:
            lentera_jiwa(p, x, y + WALK + 2, zi, hanging=False)
        # outer drip course under the walkway
        if z0 - 1 >= 0:
            p.stairs(x, y + WALK - 1, z0 - 1, "kerb_stairs", "south", half="top")
        # chains hanging on the outer face every 6 (prisoners' chains, no bodies)
        if x % 6 == 0 and z0 - 1 >= 0:
            rantai(p, x, y + 6, y + WALK - 3, z0 - 1)
    if tangga_x is not None:
        for i in range(WALK):
            x = tangga_x + i
            if x > x1:
                break
            for z in (zi - 1, zi):
                p.stairs(x, y + 1 + i, z, "kerb_stairs", "east")
                for yy in range(y + 2 + i, y + WALK + 1):
                    p.put(x, yy, z, "minecraft:air")


def _banners_out(p, x0, x1, y, z, every=8):
    for x in range(x0 + 3, x1 - 1, every):
        if p.inside(x, y, z):
            panji(p, x, y, z, "north", "black" if (x // every) % 2 else "red")


def tembok_lurus(name, panjang=48, varian=0, simpan=True):
    """A wall run piece (outer side north): varian 0 plain with a stair flight, 1 with a guard alcove and drum,
    2 with hanging cages of chains."""
    p = tl.Lot(F, name, tinggi=WALL_H + 8, lebar=panjang, dalam=DEPTH, palet=PAL)
    p.lights = []
    _wall_run(p, 0, panjang - 1, tangga_x=6 if varian == 0 and panjang >= 30 else None)
    _banners_out(p, 0, panjang - 1, LY + WALK - 3, 1)
    zi = 2 + BAND - 1
    if varian == 1:
        # roofed guard post on the walkway
        a, b = panjang // 2 - 3, panjang // 2 + 3
        for x in (a, b):
            for z in (3, zi - 1):
                tiang(p, x, LY + WALK + 1, LY + WALK + 3, z)
        for x in range(a - 1, b + 2):
            for z in range(2, zi + 1):
                p.slab(x, LY + WALK + 4, z, "roof_slab", "bottom")
        K.drum(p, panjang // 2, LY + WALK + 1, 5, "south")
        p.titik("jaga", panjang // 2 - 1, LY + WALK + 1, 4, "north")
        p.titik("jaga", panjang // 2 + 1, LY + WALK + 1, 4, "north")
    elif varian == 2:
        for x in range(4, panjang - 3, 10):
            rantai(p, x, LY + 8, LY + WALK - 4, 0)
            p.put(x, LY + 7, 0, "minecraft:iron_bars")
            tengkorak(p, x, LY + 6, 0, 8)
    p.titik("jaga", panjang // 3, LY + WALK + 1, 5, "north")
    p.titik("jalan", 1, LY + WALK + 1, 5, "south", {"urut": 0, "jalur": "tembok"})
    p.titik("jalan", panjang - 2, LY + WALK + 1, 5, "south", {"urut": 1, "jalur": "tembok"})
    if simpan:
        p.simpan()
    return p


def tembok_menara(name="tembok/menara"):
    """A run with a square watch tower that juts 4 out and rises to +24 with a pointed roof and a spike."""
    p = tl.Lot(F, name, tinggi=LY + 46, lebar=48, dalam=DEPTH + 4, palet=PAL)
    p.lights = []
    _wall_run(p, 0, 47, z0=6)
    t0, t1 = 18, 29
    zt0, zt1 = 1, 13
    top = LY + 24
    for x in range(t0, t1 + 1):
        for z in range(zt0, zt1 + 1):
            edge = x in (t0, t1) or z in (zt0, zt1)
            for yy in range(LY, top + 1):
                if edge:
                    role = "minecraft:polished_basalt" if (x in (t0, t1) and z in (zt0, zt1)) else "batu_hitam"
                    p.put(x, yy, z, role, {"axis": "y"} if role.endswith("basalt") else None)
                elif yy in (LY, LY + WALK, top):
                    p.put(x, yy, z, "paving")
                else:
                    p.put(x, yy, z, "minecraft:air")
    # arrow slits and a red band
    for yy in (LY + 6, LY + 10, LY + 18, LY + 21):
        for x in (t0 + 3, t1 - 3):
            p.put(x, yy, zt0, "minecraft:iron_bars")
        for z in (zt0 + 4, zt1 - 4):
            p.put(t0, yy, z, "minecraft:iron_bars")
            p.put(t1, yy, z, "minecraft:iron_bars")
    for x in range(t0, t1 + 1):
        for z in range(zt0, zt1 + 1):
            if x in (t0, t1) or z in (zt0, zt1):
                p.put(x, top - 2, z, "bata_merah")
    # ladder up inside, doors onto the walkway
    K.ladder(p, t1 - 1, LY + 1, top - 1, zt1 - 1, "north")
    for z in range(6, 6 + BAND):
        if zt0 < z < zt1:
            for dy in (1, 2):
                p.put(t0, LY + WALK + dy, z, "minecraft:air")
                p.put(t1, LY + WALK + dy, z, "minecraft:air")
    # parapet with spikes on top, pointed roof on four basalt posts
    for x in range(t0, t1 + 1):
        for z in (zt0, zt1):
            p.put(x, top + 1, z, "kerb")
            if x % 2 == 0:
                paku(p, x, top + 2, z)
    for z in range(zt0, zt1 + 1):
        for x in (t0, t1):
            p.put(x, top + 1, z, "kerb")
    for (x, z) in ((t0 + 2, zt0 + 2), (t1 - 2, zt0 + 2), (t0 + 2, zt1 - 2), (t1 - 2, zt1 - 2)):
        tiang(p, x, top + 1, top + 3, z)
    atap_runcing(p, t0 + 1, zt0 + 1, t1 - 1, zt1 - 1, top + 4)
    K.lit(p, (t0 + t1) // 2, top + 4, (zt0 + zt1) // 2, chain=1)
    K.drum(p, t0 + 2, top + 1, zt1 - 3, "north")
    for (x, z) in ((t0 + 3, zt0 + 2), (t1 - 3, zt0 + 2)):
        p.titik("jaga", x, top + 1, z, "north")
    p.titik("pandang", (t0 + t1) // 2, top + 1, zt0 + 1, "north", {"nama": "menara_jaga"})
    panji(p, (t0 + t1) // 2, LY + WALK + 3, zt0 - 1, "north", "red")
    p.simpan()
    return p


def tembok_sudut(name="tembok/sudut"):
    """A pointed corner tower over the crossing of two wall bands: an 11 x 11 piece (walls 1..9, drawn as the north-west
    corner; the other corners turn it), the bands enter it on its east and south sides."""
    S = 11
    p = tl.Lot(F, name, tinggi=WALL_H + 26, lebar=S, dalam=S, palet=PAL)
    p.lights = []
    a, b = 1, S - 2
    top = LY + 22
    for x in range(a, b + 1):
        for z in range(a, b + 1):
            edge = x in (a, b) or z in (a, b)
            p.put(x, LY - 1, z, "found")
            for yy in range(LY, top + 1):
                if edge:
                    corner = x in (a, b) and z in (a, b)
                    role = "minecraft:polished_basalt" if corner else ("bata_merah" if yy % 7 == 0 else "batu_hitam")
                    p.put(x, yy, z, role, {"axis": "y"} if corner else None)
                elif yy in (LY, LY + WALK, top):
                    p.put(x, yy, z, "paving")
                else:
                    p.put(x, yy, z, "minecraft:air")
    for yy in (LY + 7, LY + 17):
        for c in (a + 3, b - 3):
            p.put(c, yy, a, "minecraft:iron_bars")
            p.put(a, yy, c, "minecraft:iron_bars")
    # walkway doors toward both runs (inner sides are east and south of a NW corner)
    for z in range(4, 8):
        for dy in (1, 2):
            p.put(b, LY + WALK + dy, z, "minecraft:air")
    for x in range(4, 8):
        for dy in (1, 2):
            p.put(x, LY + WALK + dy, b, "minecraft:air")
    K.ladder(p, b - 1, LY + 1, top - 1, b - 1, "north")
    for x in range(a, b + 1):
        for z in range(a, b + 1):
            if x in (a, b) or z in (a, b):
                p.put(x, top + 1, z, "kerb")
                if (x + z) % 2 == 0:
                    paku(p, x, top + 2, z)
    atap_runcing(p, a, a, b, b, top + 4)
    for (x, z) in ((a + 1, a + 1), (b - 1, a + 1), (a + 1, b - 1), (b - 1, b - 1)):
        tiang(p, x, top + 1, top + 3, z)
    K.lit(p, S // 2, top + 3, S // 2, chain=1)
    p.titik("jaga", a + 2, top + 1, a + 2, "north")
    p.simpan()
    return p


def gerbang_tengkorak(name="tembok/gerbang_tengkorak"):
    """The Skull Gate piece (south wall of gerbang_tengkorak, authored in world orientation: inside north, outside
    south). 48 x 30: inner margin z 0, wall band z 1..8 (world 48..55), the skull face juts out to z 15, the bone road
    and the arrival field outside to z 29. The opening (6 wide, 6 high) is the entity gerbang_tengkorak: lower jaw and
    iron double door (anchor kota_tema/gerbang_iblis); nothing here ever changes at runtime."""
    W, D = 48, 30
    p = tl.Lot(F, name, tinggi=LY + 34, lebar=W, dalam=D, palet=PAL)
    p.lights = []
    cx = 24              # the opening spans x 21..26 (world -3..2), centre between 23 and 24
    o0, o1 = 21, 26
    # ground outside: foundation + bone road + arrival field
    p.fill(0, 0, 9, W - 1, LY - 1, D - 1, "found")
    K.paving(p, 0, 9, W - 1, D - 1, LY, role="minecraft:coarse_dirt", alt="minecraft:soul_soil", alt_chance=0.25)
    jalan_tulang(p, o0 - 2, 9, o1 + 2, D - 1, along="z")
    # the wall band with the opening (outer face at z 1 is NORTH in _wall_run; here the outside is south, so the run is
    # drawn mirrored by hand)
    zb0, zb1 = 1, 8
    for x in range(W):
        for z in range(zb0, zb1 + 1):
            p.put(x, LY - 1, z, "found")
            p.put(x, LY, z, "kerb")
        rib = x % 4 == 0
        for yy in range(LY + 1, LY + WALK):
            for z in range(zb0, zb1 + 1):
                if z == zb1:
                    role = "minecraft:polished_basalt" if (yy <= LY + 2 or rib) else ("bata_merah" if yy == LY + WALK - 2 else "batu_hitam")
                    p.put(x, yy, z, role, {"axis": "y"} if role.endswith("basalt") else None)
                elif z == zb0:
                    p.put(x, yy, z, "kerb" if yy <= LY + 2 else "batu_hitam")
                else:
                    p.put(x, yy, z, "batu_hitam")
        for z in range(zb0 + 1, zb1):
            p.put(x, LY + WALK, z, "paving")
        p.put(x, LY + WALK, zb0, "kerb")
        p.put(x, LY + WALK, zb1, "kerb")
        p.put(x, LY + WALK + 1, zb1, "kerb")
        if x % 2 == 0 and not (8 <= x <= 39):
            p.put(x, LY + WALK + 2, zb1, "kerb")
            paku(p, x, LY + WALK + 3, zb1)
        p.put(x, LY + WALK + 1, zb0, "kerb_wall")
    # carve the gate passage through the band (6 wide, 7 high) with a basalt frame
    for x in range(o0, o1 + 1):
        for z in range(zb0, zb1 + 1):
            p.put(x, LY, z, "kerb_chisel" if z in (zb0, zb1) else "paving")
            for yy in range(LY + 1, LY + 8):
                p.put(x, yy, z, "minecraft:air")
    for z in range(zb0, zb1 + 1):
        for x in (o0 - 1, o1 + 1):
            tiang(p, x, LY + 1, LY + 8, z)
        for x in range(o0 - 1, o1 + 2):
            p.put(x, LY + 8, z, "minecraft:polished_basalt", {"axis": "x"})
    # ---- the skull face (outside, z 9..15), bone and basalt, 22 high over the street ----
    def bone(x, y, z, ax="y"):
        p.put(x, y, z, "tulang", {"axis": ax})

    face_z = zb1 + 1
    for y in range(LY + 1, LY + 23):
        hy = y - LY
        # half width of the skull at this height: jaw sides to cheekbones (wide) to the cranium dome
        if hy <= 8:
            half = 9
        elif hy <= 12:
            half = 12
        elif hy <= 18:
            half = 13
        else:
            half = int(round(math.sqrt(max(0.0, 13.5 ** 2 - (hy - 18) ** 2 * 7.0))))
        for x in range(cx - half, cx + half):
            if o0 <= x <= o1 and hy <= 7:
                continue  # the mouth = the gate opening
            depth = 3 if abs(x - cx + 0.5) < half - 2 else 2
            if hy >= 19:
                depth = 2
            for dz in range(depth):
                z = face_z + dz
                if dz == depth - 1:
                    bone(x, y, z, "z" if hy % 3 else "y")
                else:
                    p.put(x, y, z, "minecraft:basalt", {"axis": "y"})
    # eye sockets: deep hollows lit by hidden shroomlight, a red stained glass 'iris'
    for ex in (cx - 8, cx + 3):
        for x in range(ex, ex + 5):
            for y in range(LY + 14, LY + 18):
                edge = x in (ex, ex + 4) and y in (LY + 14, LY + 17)
                if edge:
                    continue
                p.put(x, y, face_z + 2, "minecraft:air")
                p.put(x, y, face_z + 1, "minecraft:air")
                p.put(x, y, face_z, "cahaya_darah" if (x - ex) in (1, 3) else "kaca_darah")
        p.put(ex + 2, LY + 15, face_z, "minecraft:crying_obsidian")
    # nose: an inverted V of air with a blackstone back
    for i, y in enumerate(range(LY + 13, LY + 10, -1)):
        for x in range(cx - 1 - i, cx + 1 + i):
            p.put(x, y, face_z + 2, "minecraft:air")
            p.put(x, y, face_z + 1, "batu_hitam")
    # upper teeth over the gate: bone columns with downward fangs (end rods)
    for x in range(o0 - 2, o1 + 3):
        p.put(x, LY + 9, face_z + 2, "tulang", {"axis": "y"})
        p.put(x, LY + 8, face_z + 2, "tulang", {"axis": "y"} if (x - o0) % 2 == 0 else {"axis": "x"})
        if (x - o0) % 2 == 0:
            p.put(x, LY + 7, face_z + 2, "minecraft:end_rod", {"facing": "down"})
    # cheekbones jutting out
    for side in (-1, 1):
        for i in range(4):
            x = cx + side * (10 + i) - (1 if side < 0 else 0)
            bone(x, LY + 11, face_z + 3, "x")
            bone(x, LY + 12, face_z + 3, "x")
    # horns: three segments curving up and out from the temples
    for side in (-1, 1):
        hx = cx + side * 13 - (1 if side < 0 else 0)
        segs = [(0, 0), (side, 1), (side * 2, 2), (side * 3, 4), (side * 3, 6), (side * 2, 8), (side * 2, 9)]
        for (ox, oy) in segs:
            for dz in (0, 1):
                bone(hx + ox, LY + 18 + oy, face_z + dz, "y")
            bone(hx + ox, LY + 19 + oy, face_z, "y")
        paku(p, hx + side * 2, LY + 29, face_z)
    # crown of spikes along the dome
    for x in range(cx - 8, cx + 8, 2):
        paku(p, x, LY + 23, face_z + 1)
    # banners hanging from the cheeks, soul lanterns in the jaws' corners
    for side, x in ((-1, o0 - 3), (1, o1 + 3)):
        panji(p, x, LY + 6, face_z + 3, "south", "red")
        lentera_jiwa(p, x, LY + 7, face_z + 3)
    # ---- the approach: pike rows with chains and skulls, toll stones, the arrival point ----
    for z in range(17, D - 1, 4):
        for x in (o0 - 5, o1 + 5):
            tiang(p, x, LY + 1, LY + 5, z)
            p.put(x, LY + 6, z, "minecraft:iron_bars")
            tengkorak(p, x, LY + 7, z, 8 if x < cx else 0)
            rantai(p, x + (1 if x < cx else -1), LY + 3, LY + 4, z)
    for x in (o0 - 3, o1 + 3):
        obor_jiwa(p, x, 12)
    # warning stones (black slabs) where the realm gate turns the weak away
    for x in range(o0 - 2, o1 + 3):
        p.put(x, LY, 20, "kerb_chisel")
    p.kotak("bangunan", 0, LY, zb0, W - 1, LY + 22, 15, "south", nama="gerbang_tengkorak")
    # anchors: the gate entity (between x 23 and 24, mid band), the arrival point outside (38.T.11, kota_tema/tiba)
    p.jangkar(24.0, LY + 1, 4.5, "kota_tema/gerbang_iblis", protect=24)
    p.jangkar(24.0, LY + 1, 26.5, "kota_tema/tiba", tema="iblis")
    p.titik("tiba", 24, LY + 1, 26, "north")
    for (x, z) in ((o0 - 2, 11), (o1 + 2, 11)):
        p.titik("jaga", x, LY + 1, z, "south")
    p.titik("pandang", 24, LY + 2, 28, "north", {"nama": "gerbang_tengkorak"})
    p.jalur([(24, 28), (24, 18), (24, 10), (24, 4), (24, 0)], jalur="gerbang")
    p.simpan()
    return p


def tembok_terowongan(name="tembok/lurus_terowongan"):
    """The east wall run of penjara_tawanan with the mouth of the Terowongan Tua: a low tunnel through the foundation
    under the band (x 30..32), closed outside by rubble that is an ENTITY (anchor kota_tema/terowongan), so the
    ransom route (38.T.7.11 bab 5) opens it without changing a block."""
    p = tembok_lurus(name, 48, varian=2, simpan=False)
    # carve under the band: the tunnel runs from the outer margin (z 0) to the band's inner face (z 9) at y 1..3;
    # the inner margin z 10 overlaps the district lot, whose canvas carves the same tunnel (lot pieces win there)
    for z in range(0, DEPTH - 1):
        for x in range(29, 34):
            for y in range(0, LY):
                edge = x in (29, 33) or y == 0
                if edge:
                    p.put(x, y, z, "minecraft:mossy_cobblestone" if (x + z) % 3 == 0 else "kerb_alt")
                else:
                    p.put(x, y, z, "minecraft:air")
    for z in range(1, DEPTH - 1, 4):
        p.put(31, LY - 1, z, "kerb")
        lentera_jiwa(p, 31, LY - 2, z)
    # the outer mouth: a ruined arch half-buried by rubble (the rubble itself is the entity)
    for y in range(0, LY + 1):
        p.put(28, y, 0, "minecraft:mossy_cobblestone")
        p.put(34, y, 0, "minecraft:mossy_cobblestone")
    p.jangkar(31.5, 1, 0.5, "kota_tema/terowongan", sisi="luar")
    p.titik("jalan", 31, 1, 8, "south", {"urut": 0, "jalur": "terowongan"})
    p.titik("jalan", 31, 1, 1, "south", {"urut": 1, "jalur": "terowongan"})
    p.simpan()
    return p


# wall layout per edge district: side -> list of (pool, offset along the side, length)
WALLS = {
    "menara_terlarang": {"n": ["tembok/lurus", "tembok/lurus_jaga"], "w": ["tembok/menara", "tembok/lurus_sangkar"], "sudut": "nw"},
    "istana_darah": {"n": ["tembok/lurus_sangkar", "tembok/menara"]},
    "penjara_tawanan": {"n": ["tembok/lurus_jaga", "tembok/lurus"], "e": ["tembok/lurus", "tembok/lurus_terowongan"], "sudut": "ne"},
    "pasar_gelap": {"w": ["tembok/lurus", "tembok/lurus_jaga"]},
    "sarang_kontrak": {"e": ["tembok/menara", "tembok/lurus_sangkar"]},
    "kumuh_iblis": {"w": ["tembok/lurus_sangkar", "tembok/lurus"], "s": ["tembok/lurus", "tembok/lurus_jaga"], "sudut": "sw"},
    "gerbang_tengkorak": {"s": "gerbang"},
    "kedai_racun": {"e": ["tembok/lurus_jaga", "tembok/menara"], "s": ["tembok/lurus", "tembok/lurus"], "sudut": "se"},
}
SIDE_PUTAR = {"n": 0, "e": 1, "s": 2, "w": 3}


def wall_lots():
    """The wall pieces of every edge district. A run (11 deep) has its band 2..9 blocks outside the district cell; a
    tower run is 15 deep (the tower juts 4 further out). Off the 48-grid by one block so cek_tempat sees no grid hole."""
    for d, spec in WALLS.items():
        for side, kinds in spec.items():
            if side == "sudut":
                continue
            if kinds == "gerbang":
                daftar_bebas(d, -48, 47, "tembok/lurus_pendek", 2)
                daftar_bebas(d, -24, 47, "tembok/gerbang_tengkorak", 0)
                daftar_bebas(d, 24, 47, "tembok/lurus_pendek", 2)
                continue
            for idx, kind in enumerate(kinds):
                deep = DEPTH + (4 if kind == "tembok/menara" else 0)
                along = -48 + 48 * idx
                # the band lies on the 8 blocks just outside the cell; the 1-block inner margin overlaps the ring road
                # (unset cells are structure_void) and keeps every wall piece off the 48-grid
                if side == "n":
                    dx, dz = along, -48 - (deep - 1)
                elif side == "s":
                    dx, dz = along, 47
                elif side == "e":
                    dx, dz = 47, along
                else:
                    dx, dz = -48 - (deep - 1), along
                # south and east runs mirror the template order along the side (180 / 90 degree turns)
                daftar_bebas(d, dx, dz, kind, SIDE_PUTAR[side])
        corner = spec.get("sudut")
        if corner:
            putar = {"nw": 0, "ne": 1, "se": 2, "sw": 3}[corner]
            dx = -58 if corner in ("nw", "sw") else 47
            dz = -58 if corner in ("nw", "ne") else 47
            daftar_bebas(d, dx, dz, "tembok/sudut", putar)


def build_walls():
    tembok_lurus("tembok/lurus", 48, 0)
    tembok_lurus("tembok/lurus_jaga", 48, 1)
    tembok_lurus("tembok/lurus_sangkar", 48, 2)
    tembok_lurus("tembok/lurus_pendek", 24, 2)
    tembok_menara()
    tembok_sudut()
    gerbang_tengkorak()
    tembok_terowongan()


# ===================================================================================================== district helpers
def silang(k, v=True, h=True, y=LY):
    """Inner streets on the unmerged lot seams (6 wide, 3 per lot: the J side code is a real street)."""
    if v:
        K.paving(k, 45, 3, 50, 92, y, role="path", alt="path_alt", alt_chance=0.2)
    if h:
        K.paving(k, 3, 45, 92, 50, y, role="path", alt="path_alt", alt_chance=0.2)


def tokoh(k, x, z, nama, y=LY, yaw=0.0, hadap="south"):
    """A named character's anchor (38.T.7.11; dunia/tempat/tema/iblis/TokohIblis brings the body) and a talk point."""
    k.jangkar(x + 0.5, y + 1, z + 0.5, "kota_tema/iblis_tokoh", yaw=yaw, tokoh=nama)
    k.titik("obrol", x, y + 1, z, hadap, {"tokoh": nama})


def pagar_besi(p, x0, z0, x1, z1, y=LY, h=3, gerbang=None, cap=True):
    """An iron-bar fence on a blackstone kerb, polished basalt posts every 4 with spikes; gerbang = (side, from, width)
    leaves a gap (the gap is open ground; a mechanism entity may stand in it)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            if gerbang:
                side, at, w = gerbang
                if side in ("north", "south") and z == (z0 if side == "north" else z1) and at <= x < at + w:
                    p.put(x, y, z, "kerb_chisel")
                    continue
                if side in ("west", "east") and x == (x0 if side == "west" else x1) and at <= z < at + w:
                    p.put(x, y, z, "kerb_chisel")
                    continue
            p.put(x, y, z, "kerb")
            along = (x - x0) if z in (z0, z1) else (z - z0)
            post = (x in (x0, x1) and z in (z0, z1)) or along % 4 == 0
            for dy in range(1, h + 1):
                if post:
                    p.put(x, y + dy, z, "minecraft:polished_basalt", {"axis": "y"})
                else:
                    p.put(x, y + dy, z, "minecraft:iron_bars")
            if cap and post:
                paku(p, x, y + h + 1, z)


def tiang_peringatan(p, x, z, i, y=LY):
    """A memorial pike of the Lapangan Peringatan: a basalt shaft on a chiselled foot, chains with shackles, a broken
    blade (iron bars and an end rod) at its foot and a cracked mask (a skull) on top - weapons and masks, no bodies."""
    p.put(x, y + 1, z, "kerb_chisel")
    tiang(p, x, y + 2, y + 5, z)
    p.put(x, y + 6, z, "bata_merah")
    tengkorak(p, x, y + 7, z, (i * 5) % 16, layu=(i % 3 == 0))
    sides = ((1, 0), (-1, 0)) if i % 2 else ((0, 1), (0, -1))
    for (dx, dz) in sides:
        rantai(p, x + dx, y + 3, y + 5, z + dz)
        p.put(x + dx, y + 2, z + dz, "minecraft:iron_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                                  "powered": "false", "waterlogged": "false"})
    fx, fz = (x, z + 1) if i % 2 else (x + 1, z)
    p.put(fx, y + 1, fz, "minecraft:iron_bars")
    p.put(fx, y + 2, fz, "minecraft:end_rod", {"facing": "up" if i % 4 else "east"})


def tangga_cincin(p, x0, z0, x1, z1, y, role="kerb_stairs"):
    """A ring of stairs round a box (the high side toward the box) - plinths, obelisks, the throne dais."""
    for x in range(x0 - 1, x1 + 2):
        p.stairs(x, y, z0 - 1, role, "south")
        p.stairs(x, y, z1 + 1, role, "north")
    for z in range(z0, z1 + 1):
        p.stairs(x0 - 1, y, z, role, "east")
        p.stairs(x1 + 1, y, z, role, "west")


def tanah_jiwa(p, x, z, y=LY, n=0):
    """A patch of soul soil with crimson roots, dead bushes or a red mushroom (the scorched ground of Kota Iblis)."""
    p.put(x, y, z, "minecraft:soul_soil")
    plant = ("minecraft:crimson_roots", "minecraft:dead_bush", "minecraft:crimson_roots", "minecraft:red_mushroom")[n % 4]
    p.put(x, y + 1, z, plant)


def serigala(p, x, z, varian, y=LY, yaw=0.0):
    """A shadow wolf of the enforcers (vanilla wolf, untamed, persistent; 38.T.7.2 kandang serigala bayangan)."""
    p.entity(x + 0.5, y + 1, z + 0.5, {"id": "minecraft:wolf", "PersistenceRequired": nbt.Byte(1),
                                       "variant": "minecraft:" + varian, "Rotation": [nbt.Float(yaw), nbt.Float(0.0)]})


def jendela_besi(p, x0, z0, x1, z1, y0, y1, every=4, skip=()):
    """Iron-barred windows every `every` on the four walls of a box at rows y0..y1 (skip = sides with doors)."""
    for x in range(x0 + 2, x1 - 1):
        if (x - x0) % every == every // 2:
            for z, side in ((z0, "north"), (z1, "south")):
                if side not in skip:
                    for yy in range(y0, y1 + 1):
                        p.put(x, yy, z, "minecraft:iron_bars")
    for z in range(z0 + 2, z1 - 1):
        if (z - z0) % every == every // 2:
            for x, side in ((x0, "west"), (x1, "east")):
                if side not in skip:
                    for yy in range(y0, y1 + 1):
                        p.put(x, yy, z, "minecraft:iron_bars")


def pintu_besi(p, x, y, z, facing, lebar=1):
    """A doorway with dark oak doors under a chiselled lintel; lebar 2 = a double door."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(lebar):
        K.door(p, x + rx * i, y, z + rz * i, facing, "left" if i == 0 else "right")
        p.put(x + rx * i, y + 2, z + rz * i, "kerb_chisel")


# ===================================================================================================== gerbang_tengkorak
def lapangan_peringatan(k):
    """Lot (0,0): the Square of Warnings - memorial pikes with broken weapons and cracked masks round a black obelisk,
    the board of 'the names that challenged the Lord', candles; Ye Wuhen waits here (chapter 1)."""
    x0, z0, x1, z1 = 4, 4, 42, 42
    cx, cz = 23, 24
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            r = max(abs(x - cx), abs(z - cz))
            k.put(x, LY, z, "paving_alt" if r % 4 == 0 else ("floor_alt" if (x * 3 + z) % 7 == 0 else "floor"))
    # the obelisk: a 3 x 3 plinth of two layers, a stepped ring, a crying obsidian shaft, a spike
    k.fill(cx - 1, LY + 1, cz - 1, cx + 1, LY + 1, cz + 1, "kerb")
    tangga_cincin(k, cx - 1, cz - 1, cx + 1, cz + 1, LY + 1)
    k.fill(cx - 1, LY + 2, cz - 1, cx + 1, LY + 2, cz + 1, "kerb_chisel")
    for yy in range(LY + 3, LY + 11):
        k.put(cx, yy, cz, "minecraft:crying_obsidian" if yy % 3 == 0 else "minecraft:chiseled_polished_blackstone")
    for (dx, dz, f) in ((0, -1, "north"), (0, 1, "south"), (-1, 0, "west"), (1, 0, "east")):
        k.stairs(cx + dx, LY + 3, cz + dz, "kerb_stairs", OPP[f])
        lilin(k, cx + dx * 2, LY + 2, cz + dz * 2)
    k.put(cx, LY + 11, cz, "bata_merah")
    paku(k, cx, LY + 12, cz)
    # twelve memorial pikes in two rings
    ring = [(cx - 9, cz - 9), (cx, cz - 11), (cx + 9, cz - 9), (cx + 11, cz), (cx + 9, cz + 9), (cx, cz + 11),
            (cx - 9, cz + 9), (cx - 11, cz), (cx - 15, cz - 15), (cx + 15, cz - 15), (cx + 15, cz + 15), (cx - 15, cz + 15)]
    for i, (x, z) in enumerate(ring):
        tiang_peringatan(k, x, z, i)
    # the board of names (north edge, facing the square) with candles and two soul-lantern posts
    K.notice_board(k, 30, 6, "south", w=11)
    for x in range(20, 31):
        if x % 2 == 0:
            lilin(k, x, LY + 1, 8)
    for x in (18, 32):
        obor_jiwa(k, x, 7)
    # scorched patches and crimson roots
    for i, (x, z) in enumerate(((7, 31), (8, 32), (36, 12), (37, 13), (38, 36), (12, 14), (31, 38), (6, 20))):
        tanah_jiwa(k, x, z, n=i)
    lampu_jalan(k, [(5, 5), (41, 5), (5, 41), (41, 41)])
    tokoh(k, 25, 10, "ye_wuhen", hadap="south")
    for (x, z) in ((14, 24), (32, 24), (23, 36)):
        k.titik("penonton", x, LY + 1, z, "north")
    k.titik("pandang", 23, LY + 2, 40, "north", {"nama": "lapangan_peringatan"})


def rumah_bea(k):
    """Lot (0,1): the toll house by the gate - the toll counter with weighing plates, the cash chest, the rack of
    confiscated weapons, the register shelves, the guards' bunks and the gate lever room (anchor kota_tema/tuas_gerbang,
    the lever is an entity); west of it the fenced yard of confiscated goods."""
    x0, z0, x1, z1 = 20, 58, 40, 84
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "floor_alt")
    k.walls(x0, f + 1, z0, x1, f + 6, z1, role="kerb", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, x0, z0, x1, z1, f + 2, f + 3, every=4)
    K.beam_band(k, x0, z0, x1, z1, f + 6, role="trim")
    k.fill(x0 + 1, f + 6, z0 + 1, x1 - 1, f + 6, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 7, kind="hip", overhang=1)
    # east doorway onto the bone avenue: three wide, an iron portcullis over it
    for z in range(70, 73):
        for dy in range(1, 5):
            k.put(x1, f + dy, z, "minecraft:air")
        k.put(x1, f + 5, z, "minecraft:iron_bars")
    k.put(x1, f + 6, 71, "kerb_chisel")
    panji(k, x1 + 1, f + 5, 69, "east", "black")
    panji(k, x1 + 1, f + 5, 73, "east", "red")
    # the toll counter (customers stand east of it), weighing plates on barrels, the clerk's point
    K.counter_z(k, 33, 63, 79, f, "east")
    for z in (68, 74):
        k.put(33, f + 2, z, "minecraft:light_weighted_pressure_plate", {"power": "0"})
        rantai(k, 33, f + 4, f + 5, z)
        k.put(33, f + 3, z, "minecraft:iron_bars")
    k.titik("kerja:timbangan", 32, f + 1, 71, "east")
    k.titik("jaga", 32, f + 1, 65, "east")
    k.titik("jaga", 32, f + 1, 77, "east")
    peti(k, 31, f + 1, 72, "kultivator:tema/iblis/upeti", "east")
    k.put(30, f + 1, 70, "minecraft:lectern", {"facing": "east", "has_book": "true", "powered": "false"})
    # the register: bookshelves along the west wall
    K.bookshelf_wall(k, x0 + 1, z0 + 2, x0 + 1, z0 + 12, f + 1, f + 3, face="east")
    # the rack of confiscated weapons along the north wall, two armour stands with taken armour
    rak_senjata(k, 30, f + 1, z0 + 1, "south", n=5)
    armor_stand(k, 22, f + 1, z0 + 2, 180.0)
    armor_stand(k, 32, f + 1, z0 + 2, 180.0, kit="kulit")
    # the guards' bunks
    for x in (23, 26, 29):
        ranjang(k, x, f + 1, 66, "north", warna="black")
    # the lever room in the south-west corner (partition wall, a door, the lever's chains to the ceiling)
    for x in range(x0 + 1, 29):
        for dy in range(1, 6):
            k.put(x, f + dy, 76, "minecraft:air" if (x == 25 and dy <= 2) else "kerb")
    pintu_besi(k, 25, f + 1, 76, "north")
    for (x, z) in ((22, 79), (27, 79), (24, 82)):
        rantai(k, x, f + 2, f + 5, z)
    k.put(24, f + 1, 79, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    k.jangkar(24.5, f + 1, 80.5, "kota_tema/tuas_gerbang", yaw=180.0)
    k.titik("jaga", 22, f + 1, 82, "north")
    for (x, z) in ((25, 64), (36, 65), (36, 77), (25, 80)):
        K.lit(k, x, f + 6, z, chain=1)
    k.kotak("bangunan", x0, f, z0, x1, f + 12, z1, "east", nama="rumah_bea")
    k.titik("pintu", x1 + 1, f + 1, 71, "east")
    # queue rails along the avenue (blackstone posts, a soul lantern on every third)
    for z in range(54, 93, 3):
        k.put(42, LY + 1, z, "kerb_wall")
        if z % 9 == 0:
            lentera_jiwa(k, 42, LY + 2, z, hanging=False)
    # the yard of confiscated goods (west)
    pagar_besi(k, 4, 54, 16, 90, h=2, gerbang=("east", 70, 3))
    K.crates(k, 6, 57, 14, 62, LY + 1, h=3)
    K.crates(k, 6, 82, 10, 88, LY + 1, h=2)
    for (x, z) in ((7, 67), (7, 74)):
        armor_stand(k, x, LY + 1, z, 90.0, kit="kulit")
    for (x, z) in ((12, 66), (12, 75), (13, 70)):
        k.put(x, LY + 1, z, "minecraft:barrel", {"facing": "north", "open": "false"})
        k.put(x, LY + 2, z, "minecraft:skeleton_skull", {"rotation": str((x + z) % 16)})
    k.titik("jaga", 10, LY + 1, 71, "east")
    lampu_jalan(k, [(5, 52), (41, 52), (5, 91)])


def pos_penegak(k):
    """Lot (1,0): the enforcers' post - a barracks of eight bunks with chain racks and weapon racks, a mess table,
    armour stands, and the training yard with dummies and a whipping post."""
    x0, z0, x1, z1 = 56, 5, 90, 22
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "wood")
    k.walls(x0, f + 1, z0, x1, f + 5, z1, role="kerb", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, x0, z0, x1, z1, f + 2, f + 3, every=4, skip=("south",))
    K.beam_band(k, x0, z0, x1, z1, f + 5, role="trim")
    k.fill(x0 + 1, f + 5, z0 + 1, x1 - 1, f + 5, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 6, kind="hip", overhang=1)
    # south double door to the yard
    for x in (73, 74):
        for dy in (1, 2, 3):
            k.put(x, f + dy, z1, "minecraft:air")
    pintu_besi(k, 74, f + 1, z1, "south", lebar=2)
    panji(k, 72, f + 4, z1 + 1, "south", "red")
    panji(k, 75, f + 4, z1 + 1, "south", "black")
    # eight bunks along the north wall, a footlocker barrel between them
    for i in range(8):
        ranjang(k, 58 + i * 4, f + 1, z0 + 1, "north", warna="black" if i % 2 else "red")
        if i < 7:
            k.put(60 + i * 4, f + 1, z0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    # chain racks and weapon racks on the south wall, armour stands at the ends
    rak_rantai(k, 59, f + 1, z1 - 1, "x", n=6)
    rak_senjata(k, 88, f + 1, z1 - 2, "west", n=4)
    armor_stand(k, 87, f + 1, z0 + 4, 90.0)
    armor_stand(k, 87, f + 1, z0 + 7, 90.0)
    # the mess table with stools on both sides
    for x in range(64, 81):
        K.table(k, x, f + 1, 13, "minecraft:dark_oak_pressure_plate" if x % 4 else False)
    for x in range(64, 81, 2):
        kursi(k, x, f + 1, 12, "south")
        kursi(k, x, f + 1, 14, "north")
    k.titik("meja_makan", 72, f + 1, 13, "south")
    for (x, z) in ((62, 13), (72, 9), (82, 13), (72, 18)):
        K.lit(k, x, f + 5, z, chain=1)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "barak_iblis", isi=0.45, rug=False)
    k.kotak("bangunan", x0, f, z0, x1, f + 12, z1, "south", nama="pos_penegak")
    k.titik("pintu", 74, f + 1, z1 + 1, "south")
    # the training yard: soft ground, four dummies, a whipping post, a rack of practice blades
    for x in range(56, 91):
        for z in range(26, 43):
            k.put(x, LY, z, "minecraft:coarse_dirt" if (x + 2 * z) % 5 else "minecraft:soul_soil")
    pagar_besi(k, 55, 25, 91, 43, h=1, gerbang=("north", 72, 4), cap=False)
    for x in (61, 69, 77, 85):
        k.put(x, LY + 1, 33, "minecraft:dark_oak_fence")
        k.put(x, LY + 2, 33, "minecraft:hay_block", {"axis": "y"})
        k.put(x, LY + 3, 33, "minecraft:carved_pumpkin", {"facing": "south"})
        k.put(x - 1, LY + 2, 33, "minecraft:dark_oak_fence")
        k.put(x + 1, LY + 2, 33, "minecraft:dark_oak_fence")
        k.titik("latihan", x, LY + 1, 36, "north")
    tiang(k, 73, LY + 1, LY + 4, 40)
    rantai(k, 72, LY + 2, LY + 3, 40)
    rantai(k, 74, LY + 2, LY + 3, 40)
    rak_senjata(k, 89, LY + 1, 30, "west", n=5)
    k.titik("jaga", 72, LY + 1, 28, "south")
    lampu_jalan(k, [(57, 27), (89, 27), (57, 41), (89, 41)])


def kandang_serigala(k):
    """Lot (1,1): the pen of the shadow wolves (the enforcers' mounts): an iron-bar pen with four kennels, bone piles,
    troughs, a dead tree and the handler's shed; four untamed persistent wolves."""
    x0, z0, x1, z1 = 58, 56, 90, 89
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            k.put(x, LY, z, ("minecraft:coarse_dirt", "minecraft:podzol", "minecraft:soul_soil", "minecraft:coarse_dirt")[(x * 7 + z * 3) % 4])
    pagar_besi(k, x0, z0, x1, z1, h=3, gerbang=("west", 71, 3))
    # four kennels (dark oak huts with hay beds and slab roofs, open to the south)
    for (kx, kz) in ((61, 59), (61, 82), (75, 82), (84, 72)):
        for x in range(kx, kx + 4):
            for z in range(kz, kz + 4):
                edge = x in (kx, kx + 3) or z in (kz, kz + 3)
                corner = x in (kx, kx + 3) and z in (kz, kz + 3)
                if edge and not (z == kz + 3 and x in (kx + 1, kx + 2)):
                    for dy in (1, 2):
                        if corner:
                            k.put(x, LY + dy, z, "minecraft:dark_oak_log", {"axis": "y"})
                        else:
                            k.put(x, LY + dy, z, "wood")
                k.slab(x, LY + 3, z, "minecraft:dark_oak_slab", "bottom")
        k.put(kx + 1, LY + 1, kz + 1, "minecraft:hay_block", {"axis": "x"})
        k.put(kx + 2, LY + 1, kz + 1, "minecraft:hay_block", {"axis": "x"})
    # bone piles, troughs, a dead tree with chains
    for (x, z) in ((70, 62), (71, 62), (70, 63), (80, 66), (66, 76)):
        k.put(x, LY + 1, z, "tulang", {"axis": "x" if (x + z) % 2 else "z"})
    k.put(71, LY + 2, 62, "minecraft:skeleton_skull", {"rotation": "3"})
    for x in range(66, 71):
        if x % 2 == 0:
            k.put(x, LY + 1, 70, "minecraft:cauldron")
        else:
            k.put(x, LY + 1, 70, "minecraft:composter", {"level": "0"})
    tiang(k, 78, LY + 1, LY + 7, 74, role="minecraft:dark_oak_log")
    for (dx, dz, ax, dy) in ((1, 0, "x", 6), (2, 0, "x", 7), (-1, 0, "x", 6), (0, 1, "z", 6), (0, -1, "z", 6), (0, -2, "z", 7)):
        k.put(78 + dx, LY + dy, 74 + dz, "minecraft:dark_oak_log", {"axis": ax})
    rantai(k, 80, LY + 4, LY + 6, 74)
    # the handler's shed (north-east corner)
    for x in range(82, 90):
        for z in range(57, 64):
            if x in (82, 89) or z in (57, 63):
                for dy in (1, 2):
                    k.put(x, LY + dy, z, "wood_alt")
                k.put(x, LY + 3, z, "minecraft:stripped_dark_oak_log", {"axis": "x"})
            k.slab(x, LY + 4, z, "roof_slab", "bottom")
    for dy in (1, 2):
        k.put(85, LY + dy, 63, "minecraft:air")
    ranjang(k, 84, LY + 1, 58, "north", warna="brown")
    peti(k, 88, LY + 1, 58, None, "south")
    k.put(88, LY + 1, 61, "minecraft:barrel", {"facing": "west", "open": "false"})
    k.titik("kerja:sapu", 86, LY + 1, 66, "south")
    # the wolves
    for (x, z, v, yaw) in ((68, 66, "black", 30.0), (73, 77, "black", 200.0), (80, 70, "ashen", 120.0), (65, 85, "black", 300.0)):
        serigala(k, x, z, v, yaw=yaw)
    k.titik("jaga", 57, LY + 1, 69, "west")
    lampu_jalan(k, [(56, 54), (91, 91), (56, 91)])


def d_gerbang_tengkorak():
    D = "gerbang_tengkorak"
    k = kanvas(D, h=36)
    tanah_kota(k, D)
    silang(k, v=False)
    jalan_tulang(k, 43, 3, 52, 95)
    for z in range(8, 92, 12):
        obor_jiwa(k, 42, z)
        obor_jiwa(k, 53, z)
    k.jalur([(48, 94), (48, 70), (48, 48), (48, 20), (48, 1)], jalur="jalan_tulang")
    lapangan_peringatan(k)
    rumah_bea(k)
    pos_penegak(k)
    kandang_serigala(k)
    names = {(0, 0): "lapangan_peringatan", (0, 1): "rumah_bea", (1, 0): "pos_penegak", (1, 1): "kandang_serigala"}
    simpan_distrik(k, D, names)


# ===================================================================================================== arena_darah
ARENA_LO, ARENA_HI = 32, 64      # pit wall ring (canvas x and z); the sand is 33..63 (31 x 31)
TRIBUN = 6                       # tiers, 2 deep and 1 high each
LUAR = ARENA_LO - 1 - 2 * TRIBUN  # outer wall line west/north (19); east/south 64 + 13 = 77


def ruang_bawah(k, x0, z0, x1, z1, lantai="floor", dinding="kerb", lampu=True):
    """An underground room under the tribunes: floor y 0, walls and air y 1..3, ceiling y 4 (the street level);
    explicit air because beard_box fills everything below the street."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            k.put(x, 0, z, lantai if not edge else "kerb")
            k.put(x, LY, z, "kerb") if k.get(x, LY, z) is None else None
            for y in range(1, LY):
                if edge:
                    corner = x in (x0, x1) and z in (z0, z1)
                    along = (x - x0) if z in (z0, z1) else (z - z0)
                    if corner or along % 5 == 0:
                        k.put(x, y, z, "minecraft:polished_basalt", {"axis": "y"})
                    else:
                        k.put(x, y, z, dinding)
                else:
                    k.put(x, y, z, "minecraft:air")
    if lampu:
        for x in range(x0 + 3, x1 - 1, 6):
            for z in range(z0 + 3, z1 - 1, 6):
                lentera_jiwa(k, x, LY - 1, z)


def lorong_bawah(k, x0, z0, x1, z1):
    """A 3-wide passage (floor y 0, air y 1..3); its walls come from the rooms or from the solid fill around it."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, 0, z, "floor_alt")
            for y in range(1, LY):
                k.put(x, y, z, "minecraft:air")
            if k.get(x, LY, z) is None:
                k.put(x, LY, z, "kerb")


def _jarak_arena(x, z):
    dx = max(ARENA_LO - x, 0, x - ARENA_HI)
    dz = max(ARENA_LO - z, 0, z - ARENA_HI)
    return dx, dz


def arena_lubang(k):
    """The pit: 31 x 31 sand of polished blackstone with dried-blood stains and a blood sigil, a basalt-ribbed wall down
    to y 0, four cage gates (titik arena_pintu -> PintuArenaEntity), the rim parapet with spikes."""
    lo, hi = ARENA_LO, ARENA_HI
    c = (lo + hi) // 2
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            wall = x in (lo, hi) or z in (lo, hi)
            if wall:
                k.put(x, 0, z, "kerb")
                along = (x - lo) if z in (lo, hi) else (z - lo)
                for y in range(1, LY):
                    if along % 4 == 0:
                        k.put(x, y, z, "minecraft:polished_basalt", {"axis": "y"})
                    else:
                        k.put(x, y, z, "bata_hitam" if y != 2 else "bata_merah")
                k.put(x, LY, z, "kerb_chisel" if along % 4 == 0 else "kerb")
                if along % 6 == 3:
                    paku(k, x, LY + 1, z)
                else:
                    k.put(x, LY + 1, z, "kerb_wall")
                continue
            r = math.hypot(x - c, z - c)
            if 3.5 <= r < 4.5:
                bid = "bata_merah"
            elif r < 1.5:
                bid = "minecraft:chiseled_polished_blackstone"
            elif (x * 11 + z * 7) % 17 == 0 or (abs(r - 9) < 0.5 and (x + z) % 3 == 0):
                bid = "minecraft:red_concrete_powder"
            else:
                bid = "floor" if (x + z) % 5 else "floor_alt"
            k.put(x, 0, z, bid)
            for y in range(1, LY + 1):
                k.put(x, y, z, "minecraft:air")
    # chains hanging from the rim into the pit, skulls in niches
    for i in range(lo + 5, hi - 4, 8):
        for (x, z) in ((i, lo + 1), (i, hi - 1), (lo + 1, i), (hi - 1, i)):
            rantai(k, x, 2, 3, z)
    # the four cage gates and their tunnels (north -> store, south -> gladiators, west -> beasts, east -> the butcher)
    gates = [("north", c, lo, 0, -1), ("south", c, hi, 0, 1), ("west", lo, c, -1, 0), ("east", hi, c, 1, 0)]
    for nomor, (side, gx, gz, dx, dz) in enumerate(gates):
        for w in (-1, 0, 1):
            px, pz = gx + (w if dz else 0), gz + (w if dx else 0)
            for y in range(1, LY):
                k.put(px, y, pz, "minecraft:air")
            k.put(px, 0, pz, "kerb_chisel")
        k.titik("arena_pintu", gx, 1, gz, OPP[side], {"nomor": nomor, "sisi": side})
        # tunnel 6 long outward, walls of basalt
        for t in range(1, 7):
            for w in (-2, -1, 0, 1, 2):
                px, pz = gx + dx * t + (w if dz else 0), gz + dz * t + (w if dx else 0)
                if abs(w) == 2:
                    for y in range(1, LY):
                        k.put(px, y, pz, "minecraft:polished_basalt", {"axis": "y"} if t % 3 else None)
                    k.put(px, 0, pz, "kerb")
                else:
                    k.put(px, 0, pz, "floor_alt")
                    for y in range(1, LY):
                        k.put(px, y, pz, "minecraft:air")
                k.put(px, LY, pz, "kerb")
        lentera_jiwa(k, gx + dx * 3, LY - 1, gz + dz * 3)
    k.titik("pandang", c, LY + 8, hi + 12, "north", {"nama": "arena_darah"})
    for (x, z) in ((c - 6, c), (c + 6, c)):
        k.titik("latihan", x, 1, z, "north" if x < c else "south")


def arena_tribun(k):
    """Six tiers round the pit (seats = stairs looking at the sand, kursi points ~140), the outer wall with soul-lantern
    poles on chains, the main aisle in the south, the corner diagonals as climbing aisles."""
    lo, hi = ARENA_LO, ARENA_HI
    gap = range(46, 51)   # main south aisle
    n = 0
    for x in range(LUAR, hi + 2 * TRIBUN + 2):
        for z in range(LUAR, hi + 2 * TRIBUN + 2):
            dx, dz = _jarak_arena(x, z)
            d = max(dx, dz)
            if d < 1 or d > 2 * TRIBUN:
                continue
            i = (d - 1) // 2
            inner = (d - 1) % 2 == 0
            if z > hi and x in gap:
                continue
            top = LY + 1 + i
            for y in range(LY + 1, top):
                k.put(x, y, z, "kerb")
            if dx == dz:
                k.put(x, top, z, "kerb_chisel")
                continue
            if dx > dz:
                face = "west" if x < lo else "east"
            else:
                face = "north" if z < lo else "south"
            if inner:
                k.stairs(x, top, z, "minecraft:polished_blackstone_stairs", face)
                along = z if face in ("west", "east") else x
                if along % 7 == 3:
                    n += 1
                    k.titik("kursi", x, top, z, OPP[face], {"arena": "1"})
            else:
                k.put(x, top, z, "kerb" if (x + z + i) % 9 else "bata_merah")
    # the outer wall (to LY + 7) with arches at the aisle, soul-lantern poles on chains every 8
    ow0, ow1 = LUAR - 1, hi + 2 * TRIBUN + 1
    for x in range(ow0, ow1 + 1):
        for z in range(ow0, ow1 + 1):
            if not (x in (ow0, ow1) or z in (ow0, ow1)):
                continue
            if z == ow1 and x in gap:
                k.put(x, LY + 6, z, "minecraft:polished_basalt", {"axis": "x"})
                k.put(x, LY + 7, z, "kerb")
                continue
            along = (x - ow0) if z in (ow0, ow1) else (z - ow0)
            for y in range(LY + 1, LY + 8):
                if along % 8 == 0:
                    k.put(x, y, z, "minecraft:polished_basalt", {"axis": "y"})
                elif y in (LY + 3, LY + 4) and along % 4 == 2:
                    k.put(x, y, z, "minecraft:iron_bars")
                else:
                    k.put(x, y, z, "batu_hitam" if y != LY + 6 else "bata_merah")
            if along % 8 == 0:
                tiang(k, x, LY + 8, LY + 10, z)
                k.put(x, LY + 11, z, "minecraft:polished_basalt", {"axis": "y"})
                rantai(k, x, LY + 12, LY + 12, z)
                lentera_jiwa(k, x, LY + 13, z, hanging=False)
            elif along % 2 == 0:
                paku(k, x, LY + 8, z)
            else:
                k.put(x, LY + 8, z, "kerb_wall")
            if along % 16 == 4:
                face = "north" if z == ow0 else "south" if z == ow1 else "west" if x == ow0 else "east"
                ex, ez = FACE[face]
                panji(k, x + ex, LY + 6, z + ez, face, "red" if along % 32 == 4 else "black")
    # aisle rails and the overlook at the rim
    for z in range(hi + 1, ow1):
        k.put(45, LY + 1, z, "kerb_wall")
        k.put(51, LY + 1, z, "kerb_wall")
    for x in gap:
        k.put(x, LY, ow1, "kerb_chisel")
    return n


def bilik_penguasa(k):
    """The Lord's Box over the north tiers: a raised room on basalt piers (floor LY + 8), red curtains, the throne of
    blackstone with crying obsidian, banners, a pointed roof; a stone flight from the north street."""
    x0, x1, z0, z1 = 39, 57, 14, 28
    f = LY + 8
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, f, z, "floor_alt" if (x + z) % 4 else "bata_merah")
            for y in range(f + 1, f + 6):
                k.put(x, y, z, "minecraft:air")
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, 21), (x1, 21)):
        tiang(k, x, LY + 1, f + 5, z)
    for x in range(x0, x1 + 1):
        for y in range(f + 1, f + 6):
            k.put(x, y, z0, "bata_hitam" if y != f + 3 else "bata_merah")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            for y in range(f + 1, f + 6):
                if z > 22 and y < f + 4:
                    continue
                k.put(x, y, z, "bata_hitam")
    # the open front with a parapet, curtains drawn to the sides
    for x in range(x0 + 1, x1):
        k.put(x, f + 1, z1, "minecraft:polished_blackstone_wall")
    tirai(k, x0 + 1, x0 + 3, z1, f + 1, f + 5)
    tirai(k, x1 - 3, x1 - 1, z1, f + 1, f + 5)
    for x in range(x0 + 1, x1):
        k.put(x, f + 5, z1, "minecraft:red_wool" if x % 2 else "minecraft:black_wool")
    # the throne
    cx = (x0 + x1) // 2
    K.rug(k, cx - 2, z0 + 1, cx + 2, z1 - 1, f + 1, role="minecraft:red_carpet", border="minecraft:black_carpet")
    k.put(cx, f + 1, z0 + 2, "kerb_chisel")
    k.stairs(cx, f + 2, z0 + 2, "minecraft:polished_blackstone_stairs", "north")
    k.put(cx - 1, f + 2, z0 + 2, "minecraft:polished_blackstone_wall")
    k.put(cx + 1, f + 2, z0 + 2, "minecraft:polished_blackstone_wall")
    for y in range(f + 2, f + 5):
        k.put(cx, y + 1, z0 + 1, "minecraft:crying_obsidian" if y == f + 3 else "minecraft:chiseled_polished_blackstone")
    paku(k, cx - 1, f + 3, z0 + 1)
    paku(k, cx + 1, f + 3, z0 + 1)
    k.titik("kursi", cx, f + 2, z0 + 2, "south", {"peran": "PENGUASA_IBLIS", "bilik": "1"})
    for x in (cx - 4, cx + 4):
        kursi(k, x, f + 1, z0 + 4, "south")
        k.titik("jaga", x + (2 if x > cx else -2), f + 1, z0 + 2, "south")
    for x in (x0 + 2, x1 - 2):
        panji(k, x, f + 4, z0 + 1, "south", "red")
    for (x, z) in ((cx - 5, 20), (cx + 5, 20), (cx, 25)):
        K.lit(k, x, f + 5, z, chain=1)
    atap_runcing(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 6)
    # the flight up from the north street (z 4 .. 13), rails, a landing
    for i in range(f - LY):
        z = 4 + i
        for x in (47, 48, 49):
            for y in range(LY + 1, LY + 1 + i):
                k.put(x, y, z, "kerb")
            k.stairs(x, LY + 1 + i, z, "kerb_stairs", "south")
        k.put(46, LY + 2 + i, z, "minecraft:polished_blackstone_wall")
        k.put(50, LY + 2 + i, z, "minecraft:polished_blackstone_wall")
    for x in (47, 48, 49):
        for y in range(f + 1, f + 4):
            k.put(x, y, z0, "minecraft:air")
        k.put(x, f, z0 - 1, "kerb")
        for y in range(LY + 1, f):
            k.put(x, y, z0 - 1, "kerb")
    pintu_besi(k, 49, f + 1, z0, "north", lebar=3)
    k.kotak("bangunan", x0, f, z0, x1, f + 14, z1, "south", nama="bilik_penguasa")


def ruang_gladiator(k):
    """Under the south tiers: the gladiators' room - ten bunks, weapon racks, a sparring floor (latihan x 6), a water
    trough, a stair up to the street (south)."""
    x0, z0, x1, z1 = 35, 71, 61, 78
    ruang_bawah(k, x0, z0, x1, z1)
    lorong_bawah(k, 47, 65, 49, 70)
    for i in range(5):
        ranjang(k, x0 + 2 + i * 2, 1, z1 - 1, "south", warna="black" if i % 2 else "red")
        ranjang(k, x1 - 2 - i * 2, 1, z1 - 1, "south", warna="red" if i % 2 else "black")
    rak_senjata(k, x0 + 6, 1, z0 + 1, "south", n=4)
    rak_senjata(k, x1 - 2, 1, z0 + 1, "south", n=4)
    for x in range(x0 + 13, x0 + 17):
        k.put(x, 1, z0 + 3, "minecraft:cauldron")
    for i, x in enumerate((x0 + 4, x0 + 8, x0 + 12, x1 - 12, x1 - 8, x1 - 4)):
        k.titik("latihan", x, 1, z0 + 4, "south" if i % 2 else "north")
    armor_stand(k, x0 + 1, 1, z0 + 3, 90.0, kit="kulit")
    armor_stand(k, x1 - 1, 1, z0 + 3, 270.0, kit="kulit")
    peti(k, x0 + 1, 1, z1 - 1, "kultivator:tema/iblis/gladiator", "east")
    tokoh(k, x1 - 3, z0 + 5, "lao_kuang", y=0, hadap="west")
    # the stair up to the street, outside the south wall (z 79 .. 82)
    for i, z in enumerate(range(z1, z1 + 4)):
        for x in (57, 58, 59):
            k.put(x, i, z, "kerb") if i > 0 else None
            k.stairs(x, i + 1, z, "kerb_stairs", "south")
            for y in range(i + 2, LY + 2):
                k.put(x, y, z, "minecraft:air")
    for z in range(z1, z1 + 4):
        k.put(56, LY + 1, z, "kerb_wall")
        k.put(60, LY + 1, z, "kerb_wall")
    k.titik("pintu", 58, LY + 1, z1 + 4, "south")


def kandang_binatang(k):
    """Under the west tiers: the beast cages of the arena - four iron-bar cages, bones and chains, the keeper's post;
    the elite demon beasts of Lawan Binatang are brought by the anchor kota_tema/kandang_binatang."""
    x0, z0, x1, z1 = 21, 36, 29, 60
    ruang_bawah(k, x0, z0, x1, z1)
    lorong_bawah(k, 26, 47, 31, 49)
    for i, cz in enumerate((37, 43, 51, 56)):
        for z in range(cz, cz + 4):
            for x in range(x0 + 1, x0 + 5):
                edge = x == x0 + 4 or z in (cz, cz + 3)
                if edge and not (x == x0 + 4 and z in (cz + 1, cz + 2) and i == 0):
                    for y in (1, 2, 3):
                        k.put(x, y, z, "minecraft:iron_bars")
                elif not edge:
                    k.put(x, 0, z, "minecraft:soul_soil")
        k.put(x0 + 2, 1, cz + 1, "tulang", {"axis": "x"})
        rantai(k, x0 + 3, 2, 3, cz + 2)
    k.jangkar(27.5, 1, 40.5, "kota_tema/kandang_binatang", yaw=90.0)
    k.titik("jaga", 27, 1, 54, "west")


def ruang_jagal(k):
    """Under the east tiers: the butcher's room of Tu Gang - hooks on chains, chopping blocks, barrels of salt, the
    drag-out door the fallen leave by."""
    x0, z0, x1, z1 = 67, 36, 75, 60
    ruang_bawah(k, x0, z0, x1, z1)
    lorong_bawah(k, 65, 47, 70, 49)
    for z in range(z0 + 2, z1 - 1, 3):
        rantai(k, x1 - 2, 2, 3, z)
        k.put(x1 - 2, 1, z, "minecraft:iron_bars")
    for z in (40, 44, 54):
        k.put(x0 + 2, 1, z, "minecraft:stripped_dark_oak_log", {"axis": "y"})
        k.put(x0 + 2, 2, z, "minecraft:dark_oak_pressure_plate", {"powered": "false"})
    for z in (z0 + 1, z0 + 2, z1 - 1):
        k.put(x1 - 1, 1, z, "minecraft:barrel", {"facing": "west", "open": "false"})
    k.put(x0 + 1, 1, 57, "minecraft:cauldron")
    k.titik("kerja:pahat", x0 + 3, 1, 44, "east")
    k.titik("kasur", x1 - 1, 1, 50, "west")
    ranjang(k, x1 - 1, 1, 50, "east", titik=False, warna="brown")


def ruang_simpan(k):
    """Under the north tiers: the arena store - racks of chains and shackles, crates of feed, the stair to the box."""
    x0, z0, x1, z1 = 38, 21, 58, 29
    ruang_bawah(k, x0, z0, x1, z1)
    lorong_bawah(k, 47, 27, 49, 31)
    K.crates(k, x0 + 1, z0 + 1, x0 + 4, z0 + 3, 1, h=2)
    K.crates(k, x1 - 4, z0 + 1, x1 - 1, z0 + 3, 1, h=2)
    rak_rantai(k, x0 + 6, 1, z0 + 1, "x", n=4)
    for x in range(x0 + 12, x1 - 5, 2):
        k.put(x, 1, z0 + 1, "minecraft:barrel", {"facing": "south", "open": "false"})
    peti(k, x1 - 1, 1, z1 - 1, "kultivator:tema/iblis/arena", "west")


def meja_taruhan(k):
    """Outside the south arch: the betting pavilion of the arena (counter, odds board, chests, the anchor
    kota_tema/taruhan), and the arena master's post (kota_tema/arena; Tu Gang)."""
    x0, z0, x1, z1 = 30, 81, 44, 91
    k.fill(x0, LY, z0, x1, LY, z1, "floor_alt")
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), ((x0 + x1) // 2, z0), ((x0 + x1) // 2, z1)):
        tiang(k, x, LY + 1, LY + 4, z, role="bata_merah")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            k.slab(x, LY + 5, z, "roof_slab", "bottom")
    atap_runcing(k, x0 + 2, z0 + 2, x1 - 2, z1 - 2, LY + 6)
    K.counter(k, x0 + 2, x1 - 2, z0 + 2, LY, "north")
    K.notice_board(k, x1 - 2, z1 - 1, "north", w=9)
    for x in (x0 + 3, x1 - 3):
        peti(k, x, LY + 1, z1 - 2, None, "north")
    k.titik("kerja:timbangan", (x0 + x1) // 2, LY + 1, z0 + 3, "north")
    k.jangkar((x0 + x1) / 2.0 + 0.5, LY + 1, z0 + 4.5, "kota_tema/taruhan", yaw=180.0)
    for x in range(x0 + 2, x1 - 1, 3):
        k.titik("penonton", x, LY + 1, z0, "south")
    # the arena master's post by the arch
    k.jangkar(52.5, LY + 1, 80.5, "kota_tema/arena", yaw=180.0)
    tokoh(k, 53, 82, "tu_gang", hadap="north")
    K.drum(k, 55, LY + 1, 80, "south")
    for x in (54, 62):
        obor_jiwa(k, x, 84)


def d_arena_darah():
    D = "arena_darah"
    k = kanvas(D, h=40)
    tanah_kota(k, D)
    # the plaza round the arena: smooth basalt rings and blackstone
    for x in range(3, 93):
        for z in range(3, 93):
            if k.get(x, LY + 1, z) is None:
                d = max(_jarak_arena(x, z))
                if d > 2 * TRIBUN + 1 and (d - 2 * TRIBUN) % 5 == 0:
                    k.put(x, LY, z, "paving_alt")
    arena_lubang(k)
    arena_tribun(k)
    bilik_penguasa(k)
    ruang_gladiator(k)
    kandang_binatang(k)
    ruang_jagal(k)
    ruang_simpan(k)
    meja_taruhan(k)
    k.jangkar(48.5, LY + 1, 88.5, "kota_tema/situs", tema="iblis", protect=150)
    lampu_jalan(k, [(6, 6), (89, 6), (6, 89), (89, 89), (6, 48), (89, 48), (66, 88)])
    k.jalur([(48, 94), (48, 82), (36, 80), (14, 60), (14, 30), (30, 10), (66, 10), (82, 30), (82, 66), (60, 82)], jalur="arena")
    names = {(0, 0): "arena_barat_laut", (1, 0): "arena_timur_laut", (0, 1): "arena_barat_daya", (1, 1): "arena_tenggara"}
    seams = (((0, 0), (1, 0)), ((0, 1), (1, 1)), ((0, 0), (0, 1)), ((1, 0), (1, 1)))
    simpan_distrik(k, D, names, seams=seams)


# ===================================================================================================== istana_darah
def pilar_merah(k, x, z, y0, y1):
    """A 2 x 2 red nether brick column with a gilded blackstone base and capital."""
    for dx in (0, 1):
        for dz in (0, 1):
            k.put(x + dx, y0, z + dz, "minecraft:gilded_blackstone")
            for y in range(y0 + 1, y1):
                k.put(x + dx, y, z + dz, "bata_merah")
            k.put(x + dx, y1, z + dz, "minecraft:chiseled_polished_blackstone")


def takhta(k, cx, z, f, menghadap="south"):
    """The Blood Throne: a dais of two steps, a blackstone seat, a crying obsidian back with bone horns and spikes."""
    k.fill(cx - 3, f + 1, z - 1, cx + 3, f + 1, z + 2, "kerb")
    tangga_cincin(k, cx - 3, z - 1, cx + 3, z + 2, f + 1)
    k.fill(cx - 2, f + 2, z - 1, cx + 2, f + 2, z + 1, "kerb_chisel")
    k.stairs(cx, f + 3, z + 1, "minecraft:polished_blackstone_stairs", "north")
    k.put(cx - 1, f + 3, z + 1, "minecraft:polished_blackstone_wall")
    k.put(cx + 1, f + 3, z + 1, "minecraft:polished_blackstone_wall")
    for y in range(f + 3, f + 8):
        k.put(cx, y, z, "minecraft:crying_obsidian" if y in (f + 4, f + 6) else "minecraft:chiseled_polished_blackstone")
        if y < f + 6:
            k.put(cx - 1, y, z, "bata_merah")
            k.put(cx + 1, y, z, "bata_merah")
    for side in (-1, 1):
        for (ox, oy) in ((2, 5), (3, 6), (3, 7), (2, 8)):
            k.put(cx + side * ox, f + oy, z, "tulang", {"axis": "y"})
        paku(k, cx + side * 2, f + 9, z)
    k.put(cx, f + 8, z, "bata_merah")
    paku(k, cx, f + 9, z)
    k.titik("kursi", cx, f + 3, z + 1, "south", {"peran": "PENGUASA_IBLIS", "takhta": "1"})


def istana_aula(k):
    """Lots (0,0)+(1,0): the Blood Palace - a podium with a grand stair, the throne hall 30 x 26 with 2 x 2 red columns,
    the shallow blood pool before the throne (red concrete under red glass, hidden shroomlight, no fluid), tall barred
    windows, a hip roof; the banquet wing (west) and the blood meditation wing (east, meditasi x 4)."""
    f = LY + 3
    K.podium(k, 4, 5, 91, 38, LY, 3, steps=("south",), step_w=12, top="floor")
    # ---- the throne hall ----
    x0, z0, x1, z1 = 33, 8, 62, 33
    h = 12
    k.walls(x0, f + 1, z0, x1, f + h, z1, role="bata_hitam", pillar="minecraft:polished_basalt", windows=0, interior_air=False)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 6, z1 - 1)
    for z in range(z0 + 2, z1 - 1, 4):
        for x in (x0, x1):
            for y in range(f + 3, f + 9):
                k.put(x, y, z, "minecraft:red_stained_glass_pane" if y in (f + 5, f + 6) else "minecraft:iron_bars")
    for x in range(x0 + 4, x1 - 3, 6):
        for y in range(f + 7, f + 10):
            k.put(x, y, z1, "minecraft:iron_bars")
    for y in (f + 4, f + 9):
        for x in range(x0, x1 + 1):
            k.put(x, y, z0, "bata_merah")
            if k.get(x, y, z1) == k.b("bata_hitam"):
                k.put(x, y, z1, "bata_merah")
    K.beam_band(k, x0, z0, x1, z1, f + h, role="trim")
    k.fill(x0 + 1, f + h, z0 + 1, x1 - 1, f + h, z1 - 1, "nether")
    # the door: 4 wide, 6 high, a portcullis of bars over it, banners and spikes
    for x in range(46, 50):
        for y in range(f + 1, f + 7):
            k.put(x, y, z1, "minecraft:air")
        k.put(x, f + 7, z1, "minecraft:iron_bars")
    for x in (45, 50):
        panji(k, x, f + 6, z1 + 1, "south", "red")
    # columns, the pool, the throne
    for x in (37, 43, 51, 57):
        for z in (13, 21, 28):
            pilar_merah(k, x, z, f, f + h - 1)
    K.rug(k, 46, 19, 49, z1 - 1, f + 1, role="minecraft:red_carpet", border="minecraft:black_carpet")
    kolam_darah(k, 42, 13, 53, 18, f)
    takhta(k, 47, 10, f)
    for (x, z) in ((40, 11), (55, 11)):
        tiang(k, x, f + 1, f + 3, z, role="bata_merah")
        lentera_jiwa(k, x, f + 4, z, hanging=False)
    for x in range(x0 + 2, x1 - 1, 4):
        panji(k, x, f + 8, z0 + 1, "south", "black" if x % 8 else "red")
    for (x, z) in ((40, 17), (55, 17), (40, 25), (55, 25), (47, 25), (47, 30)):
        K.lit(k, x, f + h, z, chain=3)
    for x in (39, 56):
        for z in (19, 25, 31):
            k.titik("jaga", x, f + 1, z, "east" if x < 47 else "west")
    tokoh(k, 47, 13, "xue_yao", y=f, hadap="south")
    k.titik("pandang", 47, f + 3, 31, "north", {"nama": "aula_takhta"})
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + h + 1, kind="hip", overhang=1, top=None)
    ruang(k, x0 + 1, z0 + 1, x0 + 3, z1 - 1, f, "istana_darah", isi=0.5, rug=False, seed=2)
    ruang(k, x1 - 3, z0 + 1, x1 - 1, z1 - 1, f, "istana_darah", isi=0.5, rug=False, seed=3)
    k.kotak("bangunan", x0, f, z0, x1, f + h + 14, z1, "south", nama="aula_takhta")
    k.titik("pintu", 47, f + 1, z1 + 1, "south")
    # ---- the banquet wing (west) ----
    bx0, bz0, bx1, bz1 = 7, 11, 28, 31
    k.walls(bx0, f + 1, bz0, bx1, f + 6, bz1, role="bata_hitam", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, bx0, bz0, bx1, bz1, f + 2, f + 3, every=4, skip=("east",))
    K.beam_band(k, bx0, bz0, bx1, bz1, f + 6, role="trim")
    k.fill(bx0 + 1, f + 6, bz0 + 1, bx1 - 1, f + 6, bz1 - 1, "wood")
    K.roof_over(k, bx0 - 1, bz0 - 1, bx1 + 1, bz1 + 1, f + 7, kind="hip", overhang=1)
    for z in (20, 21, 22):
        for y in (f + 1, f + 2, f + 3):
            k.put(bx1, y, z, "minecraft:air")
    for x in range(bx1 + 1, x0):
        for z in (20, 21, 22):
            k.put(x, f, z, "floor_alt")
    for x in range(10, 26):
        K.table(k, x, f + 1, 21, "minecraft:dark_oak_pressure_plate" if x % 3 else False)
        if x % 3 == 0:
            k.put(x, f + 2, 21, "minecraft:red_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    for x in range(10, 26, 2):
        kursi(k, x, f + 1, 20, "south")
        kursi(k, x, f + 1, 22, "north")
    k.stairs(9, f + 1, 21, "minecraft:polished_blackstone_stairs", "west")
    k.titik("kursi", 9, f + 1, 21, "east", {"peran": "PENGUASA_IBLIS"})
    k.titik("meja_makan", 17, f + 1, 21, "south")
    for x in range(bx0 + 1, bx0 + 6):
        k.put(x, f + 1, bz0 + 1, "minecraft:barrel", {"facing": "south", "open": "false"})
        k.put(x, f + 2, bz0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"}) if x % 2 else None
    K.furnish_kitchen(k, bx1 - 6, bz0 + 1, bx1 - 1, f, bz0 + 1, "south")
    for x in (bx0 + 2, bx0 + 4):
        k.put(x, f + 1, bz1 - 2, "minecraft:note_block", {"instrument": "harp", "note": "6", "powered": "false"})
        k.titik("kerja:kipas", x, f + 1, bz1 - 3, "north")
    for (x, z) in ((13, 21), (20, 21), (13, 15), (20, 27)):
        K.lit(k, x, f + 6, z, chain=1)
    ruang(k, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, f, "istana_darah", isi=0.45, rug=False)
    k.kotak("bangunan", bx0, f, bz0, bx1, f + 14, bz1, "east", nama="ruang_perjamuan")
    # ---- the blood meditation wing (east) ----
    mx0, mz0, mx1, mz1 = 67, 11, 88, 31
    k.walls(mx0, f + 1, mz0, mx1, f + 6, mz1, role="bata_hitam", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, mx0, mz0, mx1, mz1, f + 2, f + 3, every=4, skip=("west",))
    K.beam_band(k, mx0, mz0, mx1, mz1, f + 6, role="trim")
    k.fill(mx0 + 1, f + 6, mz0 + 1, mx1 - 1, f + 6, mz1 - 1, "wood")
    K.roof_over(k, mx0 - 1, mz0 - 1, mx1 + 1, mz1 + 1, f + 7, kind="hip", overhang=1)
    for z in (20, 21, 22):
        for y in (f + 1, f + 2, f + 3):
            k.put(mx0, y, z, "minecraft:air")
    for x in range(x1 + 1, mx0):
        for z in (20, 21, 22):
            k.put(x, f, z, "floor_alt")
    kolam_darah(k, 74, 18, 81, 24, f)
    for (x, z, hadap) in ((77, 16, "south"), (77, 26, "north"), (72, 21, "east"), (83, 21, "west")):
        k.put(x, f + 1, z, "minecraft:red_carpet")
        k.titik("meditasi", x, f + 1, z, hadap)
        lilin(k, x + (1 if hadap in ("north", "south") else 0), f + 1, z + (0 if hadap in ("north", "south") else 1))
    K.bookshelf_wall(k, mx1 - 1, mz0 + 2, mx1 - 1, mz1 - 2, f + 1, f + 3, face="west")
    for (x, z) in ((74, 14), (81, 14), (74, 28), (81, 28)):
        K.lit(k, x, f + 6, z, chain=1)
    ruang(k, mx0 + 1, mz0 + 1, mx1 - 1, mz1 - 1, f, "istana_darah", isi=0.4, rug=False, seed=1)
    k.kotak("bangunan", mx0, f, mz0, mx1, f + 14, mz1, "west", nama="ruang_semedi_darah")
    # ---- the front court ----
    for x in range(30, 66):
        for z in range(34, 39):
            if k.get(x, f + 1, z) is None:
                k.put(x, f, z, "paving_alt" if (x + z) % 4 == 0 else "floor")
    for x in (34, 40, 56, 62):
        K.banner_pole(k, x, f + 1, 36, height=5, color="red" if x in (34, 62) else "black")
    for x in (30, 65):
        obor_jiwa(k, x, 36, y=f)
    for x in range(12, 84, 8):
        if not (28 <= x <= 66):
            obor_jiwa(k, x, 36, y=f)


def barak_istana(k):
    """Lot (0,1): the palace barracks - a two-storey blackstone hall: armoury and mess below, twelve bunks above, a yard
    with a well and racks."""
    x0, z0, x1, z1 = 6, 55, 40, 75
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "floor_alt")
    k.walls(x0, f + 1, z0, x1, f + 5, z1, role="kerb", pillar="bata_merah", windows=0, interior_air=True)
    k.walls(x0, f + 6, z0, x1, f + 10, z1, role="plaster", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, x0, z0, x1, z1, f + 2, f + 3, every=4, skip=("south",))
    jendela_besi(k, x0, z0, x1, z1, f + 7, f + 8, every=4)
    K.beam_band(k, x0, z0, x1, z1, f + 5, role="trim")
    K.beam_band(k, x0, z0, x1, z1, f + 10, role="trim")
    K.upper_floor(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f + 5, hole=(x1 - 3, z0 + 1, x1 - 1, z0 + 6))
    k.fill(x0 + 1, f + 10, z0 + 1, x1 - 1, f + 10, z1 - 1, "wood")
    atap_runcing(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 11, spike=False)
    for i in range(5):
        k.stairs(x1 - 2, f + 1 + i, z0 + 6 - i, "wood_stairs", "north")
        k.stairs(x1 - 1, f + 1 + i, z0 + 6 - i, "wood_stairs", "north")
    for x in (22, 23):
        for dy in (1, 2, 3):
            k.put(x, f + dy, z1, "minecraft:air")
    pintu_besi(k, 23, f + 1, z1, "south", lebar=2)
    # ground floor: armoury racks, armour stands, the mess table
    rak_senjata(k, 14, f + 1, z0 + 1, "south", n=6)
    rak_rantai(k, 17, f + 1, z0 + 1, "x", n=6)
    for x in (8, 10, 12):
        armor_stand(k, x, f + 1, z0 + 2, 180.0)
    for x in range(10, 31):
        K.table(k, x, f + 1, 67, "minecraft:dark_oak_pressure_plate" if x % 4 else False)
    for x in range(10, 31, 2):
        kursi(k, x, f + 1, 66, "south")
        kursi(k, x, f + 1, 68, "north")
    k.titik("meja_makan", 20, f + 1, 67, "south")
    for (x, z) in ((14, 65), (26, 65), (20, 71)):
        K.lit(k, x, f + 5, z, chain=1)
    # upper floor: twelve bunks in two rows with footlockers
    for i in range(6):
        ranjang(k, x0 + 3 + i * 5, f + 6, z0 + 1, "north", warna="black")
        ranjang(k, x0 + 3 + i * 5, f + 6, z1 - 1, "south", warna="red")
        k.put(x0 + 4 + i * 5, f + 6, z0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    for (x, z) in ((14, 65), (28, 65)):
        K.lit(k, x, f + 10, z, chain=1)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "barak_iblis", isi=0.4, rug=False)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f + 5, "barak_iblis", pulau=("peti", "senjata"), jarak=6, isi=0.35, seed=1)
    k.kotak("bangunan", x0, f, z0, x1, f + 22, z1, "south", nama="barak_istana")
    k.titik("pintu", 22, f + 1, z1 + 1, "south")
    # the yard: a well, racks, a pell
    K.well(k, 14, 84)
    rak_senjata(k, 34, LY + 1, 82, "west", n=4)
    k.put(25, LY + 1, 85, "minecraft:dark_oak_fence")
    k.put(25, LY + 2, 85, "minecraft:hay_block", {"axis": "y"})
    k.put(25, LY + 3, 85, "minecraft:carved_pumpkin", {"facing": "north"})
    k.titik("latihan", 25, LY + 1, 83, "south")
    k.titik("kerja:sapu", 18, LY + 1, 88, "north")
    lampu_jalan(k, [(5, 53), (41, 53), (5, 91), (41, 91)])


def perbendaharaan(k):
    """Lot (1,1): the Treasury of Tribute - a fenced vault house with double walls, iron doors, rows of chests and
    decorated pots on shelves, the counting desk and the ledger; anchor kota_tema/perbendaharaan (quest target)."""
    pagar_besi(k, 54, 53, 91, 91, h=3, gerbang=("west", 71, 3))
    x0, z0, x1, z1 = 60, 59, 86, 85
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "kerb")
    k.walls(x0, f + 1, z0, x1, f + 7, z1, role="bata_hitam", pillar="minecraft:polished_basalt", windows=0, interior_air=True)
    k.walls(x0 + 2, f + 1, z0 + 2, x1 - 2, f + 6, z1 - 2, role="kerb", pillar="minecraft:gilded_blackstone", windows=0,
            interior_air=True)
    K.beam_band(k, x0, z0, x1, z1, f + 7, role="trim")
    k.fill(x0 + 1, f + 7, z0 + 1, x1 - 1, f + 7, z1 - 1, "kerb")
    atap_runcing(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 8)
    # doors: outer on the west, inner on the north side of the inner vault (a dog-leg corridor)
    for z in (71, 72):
        for dy in (1, 2):
            k.put(x0, f + dy, z, "minecraft:air")
        k.put(x0, f + 1, z, "minecraft:iron_door", {"facing": "west", "half": "lower", "hinge": "left" if z == 71 else "right",
                                                  "open": "false", "powered": "false"})
        k.put(x0, f + 2, z, "minecraft:iron_door", {"facing": "west", "half": "upper", "hinge": "left" if z == 71 else "right",
                                                  "open": "false", "powered": "false"})
    k.put(x0 - 1, f + 1, 70, "minecraft:stone_button", {"face": "wall", "facing": "west", "powered": "false"})
    for x in (73,):
        for dy in (1, 2):
            k.put(x, f + dy, z0 + 2, "minecraft:air")
        k.put(x, f + 1, z0 + 2, "minecraft:iron_door", {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
        k.put(x, f + 2, z0 + 2, "minecraft:iron_door", {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
        k.put(x + 1, f + 2, z0 + 1, "minecraft:stone_button", {"face": "wall", "facing": "north", "powered": "false"})
        k.put(x - 1, f + 2, z0 + 3, "minecraft:stone_button", {"face": "wall", "facing": "south", "powered": "false"})
    # the corridor: the counting desk, the ledger, guards
    k.put(x0 + 1, f + 1, z0 + 1, "minecraft:lectern", {"facing": "east", "has_book": "true", "powered": "false"})
    K.table(k, x0 + 1, f + 1, 75)
    kursi(k, x0 + 1, f + 1, 76, "north")
    k.titik("meja_tulis", x0 + 1, f + 1, 76, "north")
    for (x, z, hadap) in ((x0 + 1, 68, "east"), (x0 + 1, 79, "east"), (75, z0 + 1, "south")):
        k.titik("jaga", x, f + 1, z, hadap)
    # the inner vault: chests in rows, shelves of decorated pots, gilded heaps, chains
    for zi, z in enumerate(range(z0 + 5, z1 - 3, 4)):
        for x in range(x0 + 5, x1 - 4, 2):
            peti(k, x, f + 1, z, "kultivator:tema/iblis/perbendaharaan" if (x + zi) % 3 == 0 else None, "south")
    for x in range(x0 + 4, x1 - 3):
        K.shelf(k, x, f + 2, z1 - 3, "north")
        k.put(x, f + 1, z1 - 3, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"}) \
            if x % 2 else k.put(x, f + 1, z1 - 3, "minecraft:gilded_blackstone")
    for (x, z) in ((x0 + 4, z0 + 4), (x1 - 4, z0 + 4)):
        k.put(x, f + 1, z, "minecraft:gilded_blackstone")
        k.put(x, f + 2, z, "minecraft:gilded_blackstone")
        lilin(k, x, f + 3, z)
    for (x, z) in ((67, 66), (79, 66), (67, 78), (79, 78)):
        K.lit(k, x, f + 6, z, chain=1)
    k.jangkar(73.5, f + 1, 72.5, "kota_tema/perbendaharaan", yaw=0.0)
    k.kotak("bangunan", x0, f, z0, x1, f + 20, z1, "west", nama="perbendaharaan")
    k.titik("pintu", x0 - 1, f + 1, 71, "west")
    for (x, z) in ((56, 56), (89, 56), (56, 89), (89, 89)):
        obor_jiwa(k, x, z)


def d_istana_darah():
    D = "istana_darah"
    k = kanvas(D, h=48)
    tanah_kota(k, D)
    K.paving(k, 3, 45, 92, 50, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 45, 48, 50, 92, LY, role="path", alt="path_alt", alt_chance=0.2)
    jalan_tulang(k, 43, 39, 52, 50, along="z")
    istana_aula(k)
    barak_istana(k)
    perbendaharaan(k)
    lampu_jalan(k, [(44, 52), (51, 60), (44, 70), (51, 80), (44, 90)])
    names = {(0, 0): "istana_barat", (1, 0): "istana_timur", (0, 1): "barak_istana", (1, 1): "perbendaharaan"}
    simpan_distrik(k, D, names, seams=(((0, 0), (1, 0)),), utuh=("istana_barat", "istana_timur"))


# ===================================================================================================== menara_terlarang
MENARA_X0, MENARA_Z0 = 15, 41     # 15 x 15 footprint (x 15..29, z 41..55), across the seam of lots (0,0)/(0,1)
MENARA_N = 9                      # floors
MENARA_SH = 5                     # floor height
MENARA_F1 = LY + 2                # floor 1 (over a 2-high plinth)


def rak_kitab_berantai(k, x0, z0, x1, z1, y0, y1, face):
    """A wall of forbidden books: bookshelves and chiselled bookshelves bound by chains in front (38.T.7.2)."""
    K.bookshelf_wall(k, x0, z0, x1, z1, y0, y1, chiseled_every=2, face=face)
    dx, dz = FACE[face]
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if (x + z) % 3 == 0:
                k.put(x + dx, y1, z + dz, "minecraft:iron_chain", {"axis": "x" if dz else "z", "waterlogged": "false"})


def menara_kitab(k):
    """Lots (0,0)+(0,1): the Tower of Forbidden Books - nine floors 15 x 15 x 52 of blackstone bricks, narrow barred
    slits, a square stair winding up the walls, chained shelves on every floor, floor 9 sealed (anchor
    kota_tema/segel_menara: SegelPintuEntity over the last stair), a spired crown; Mo Wen's desk on floor 1."""
    x0, z0 = MENARA_X0, MENARA_Z0
    x1, z1 = x0 + 14, z0 + 14
    cx, cz = x0 + 7, z0 + 7
    # plinth with buttresses and steps to the east door
    for x in range(x0 - 2, x1 + 3):
        for z in range(z0 - 2, z1 + 3):
            k.put(x, LY + 1, z, "kerb")
            if x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1:
                k.put(x, LY + 2, z, "kerb_chisel" if (x + z) % 4 == 0 else "kerb")
    tangga_cincin(k, x0 - 2, z0 - 2, x1 + 2, z1 + 2, LY + 1)
    for (bx, bz) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        for y in range(LY + 3, LY + 12):
            k.put(bx, y, bz, "minecraft:polished_basalt", {"axis": "y"})
        k.stairs(bx, LY + 12, bz, "kerb_stairs", "south" if bz < cz else "north")
    top = MENARA_F1 + MENARA_N * MENARA_SH
    for y in range(MENARA_F1, top + 1):
        n = (y - MENARA_F1) // MENARA_SH          # 0..8 storey index
        rel = (y - MENARA_F1) % MENARA_SH
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                if edge:
                    if corner:
                        k.put(x, y, z, "minecraft:polished_basalt", {"axis": "y"})
                    elif rel == 0:
                        k.put(x, y, z, "bata_merah")
                    else:
                        along = (x - x0) if z in (z0, z1) else (z - z0)
                        if along in (4, 10) and rel in (2, 3):
                            k.put(x, y, z, "minecraft:iron_bars")
                        else:
                            k.put(x, y, z, "bata_hitam")
                elif rel == 0:
                    k.put(x, y, z, "floor" if (x + z) % 3 else "floor_alt")
                else:
                    k.put(x, y, z, "minecraft:air")
    # the east door on floor 1
    for z in (cz - 1, cz, cz + 1):
        for dy in (1, 2, 3):
            k.put(x1, MENARA_F1 + dy, z, "minecraft:air")
    pintu_besi(k, x1, MENARA_F1 + 1, cz, "east", lebar=2)
    k.put(x1, MENARA_F1 + 4, cz, "minecraft:crying_obsidian")
    panji(k, x1 + 1, MENARA_F1 + 4, cz - 2, "east", "red")
    panji(k, x1 + 1, MENARA_F1 + 4, cz + 2, "east", "black")
    # the stair: floor n climbs along one inner wall (west, north, east, south in turn), a hole in the floor above
    runs = ["west", "north", "east", "south"]
    for n in range(MENARA_N - 1):
        f = MENARA_F1 + n * MENARA_SH
        side = runs[n % 4]
        for i in range(MENARA_SH):
            if side == "west":
                x, z, face = x0 + 1, z1 - 2 - i, "north"
            elif side == "north":
                x, z, face = x0 + 2 + i, z0 + 1, "east"
            elif side == "east":
                x, z, face = x1 - 1, z0 + 2 + i, "south"
            else:
                x, z, face = x1 - 2 - i, z1 - 1, "west"
            for w in (0, 1):
                wx = x + (w if side in ("west",) else -w if side == "east" else 0)
                wz = z + (w if side == "north" else -w if side == "south" else 0)
                for yy in range(f + 1, f + 1 + i):
                    k.put(wx, yy, wz, "bata_hitam")
                k.stairs(wx, f + 1 + i, wz, "minecraft:polished_blackstone_brick_stairs", face)
                if i >= MENARA_SH - 2:
                    for yy in range(f + MENARA_SH, f + MENARA_SH + 2):
                        k.put(wx, yy, wz, "minecraft:air")
        if n == MENARA_N - 2:
            # the last flight ends under the seal: the hatch of floor 9 (anchor; the seal is an entity)
            hx, hz = (x1 - 3, z1 - 2) if side == "south" else (x1 - 2, z0 + 4)
            k.jangkar(hx + 0.5, f + MENARA_SH, hz + 0.5, "kota_tema/segel_menara", lantai=9)
    # every floor: chained shelves on two walls, a reading desk, a lectern, candles, a hanging soul lantern
    for n in range(MENARA_N):
        f = MENARA_F1 + n * MENARA_SH
        side = runs[n % 4]
        shelf_sides = [s for s in ("north", "south", "west", "east") if s != side and s != runs[(n - 1) % 4]][:2]
        for s in shelf_sides:
            if s == "north":
                rak_kitab_berantai(k, x0 + 3, z0 + 1, x1 - 3, z0 + 1, f + 1, f + 3, "south")
            elif s == "south":
                rak_kitab_berantai(k, x0 + 3, z1 - 1, x1 - 3, z1 - 1, f + 1, f + 3, "north")
            elif s == "west":
                rak_kitab_berantai(k, x0 + 1, z0 + 3, x0 + 1, z1 - 3, f + 1, f + 3, "east")
            else:
                rak_kitab_berantai(k, x1 - 1, z0 + 3, x1 - 1, z1 - 3, f + 1, f + 3, "west")
        k.put(cx, f + 1, cz, "minecraft:lectern", {"facing": ("south", "west", "north", "east")[n % 4], "has_book": "true",
                                                   "powered": "false"})
        lilin(k, cx + 1, f + 1, cz + 1)
        lilin(k, cx - 1, f + 1, cz - 1)
        K.lit(k, cx, f + MENARA_SH, cz - 2, chain=1)
        if n < MENARA_N - 1:
            k.titik("meja_tulis", cx, f + 1, cz + 1, "north", {"lantai": n + 1})
    # floor 1: Mo Wen's desk and the anchor of the shop (kota_tema/kitab_terlarang), incense
    f1 = MENARA_F1
    K.table(k, cx + 2, f1 + 1, cz)
    kursi(k, cx + 3, f1 + 1, cz, "west", titik=False)
    k.jangkar(cx + 2.5, f1 + 1, cz + 1.5, "kota_tema/kitab_terlarang", yaw=270.0)
    tokoh(k, cx + 3, cz + 2, "mo_wen", y=f1, hadap="west")
    k.put(cx - 2, f1 + 1, cz + 2, "minecraft:flower_pot")
    k.titik("dupa", cx - 2, f1 + 1, cz + 3, "north")
    # floor 9: the altar of the Kitab Darah Senja
    f9 = MENARA_F1 + (MENARA_N - 1) * MENARA_SH
    kolam_darah(k, cx - 2, cz - 2, cx + 2, cz + 2, f9)
    k.put(cx, f9 + 1, cz, "minecraft:crying_obsidian")
    k.put(cx, f9 + 2, cz, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        tiang(k, cx + dx, f9 + 1, f9 + 3, cz + dz, role="bata_merah")
        lilin(k, cx + dx, f9 + 4, cz + dz, n=4)
    k.titik("pandang", cx, f9 + 1, cz + 4, "north", {"nama": "lantai_sembilan"})
    # the crown: machicolations, spikes, a spire of nether bricks
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                k.stairs(x, top, z, "kerb_stairs", "south" if z == z0 - 1 else "north" if z == z1 + 1 else
                         "east" if x == x0 - 1 else "west", half="top")
                k.put(x, top + 1, z, "kerb")
                if (x + z) % 2 == 0:
                    paku(k, x, top + 2, z)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, top, z, "kerb")
    atap_runcing(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, top + 1)
    for (x, z) in ((cx, z0 - 2), (cx, z1 + 2)):
        panji(k, x, top - 2, z, "north" if z < cz else "south", "red")
    k.kotak("bangunan", x0, LY, z0, x1, top + 12, z1, "east", nama="menara_kitab_terlarang")
    k.titik("pintu", x1 + 3, LY + 1, cz, "east")
    # the yard round the tower: dead trees, bone lanterns
    for (x, z) in ((6, 12), (8, 84), (38, 20), (38, 76)):
        tiang(k, x, LY + 1, LY + 6, z, role="minecraft:dark_oak_log")
        for (dx, dz, dy) in ((1, 0, 5), (-1, 0, 4), (0, 1, 6)):
            k.put(x + dx, LY + dy, z + dz, "minecraft:dark_oak_log", {"axis": "x" if dx else "z"})
    for i, (x, z) in enumerate(((10, 30), (11, 31), (34, 64), (35, 66), (9, 70), (36, 34))):
        tanah_jiwa(k, x, z, n=i)
    lampu_jalan(k, [(5, 5), (41, 5), (5, 92), (41, 92), (41, 48)])


def rumah_mo_wen(k):
    """Lot (1,0): the house of Blind Mo Wen - a scholar's house of black plaster round a small courtyard of dead trees,
    every wall a bookshelf, a tea table, an ink desk, his bed and an incense altar; a wind chime of chains."""
    x0, z0, x1, z1 = 56, 8, 88, 38
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "wood")
    # main hall (north) and a side room (east), the court in between
    for (a0, b0, a1, b1) in ((x0, z0, x1, z0 + 12), (x1 - 10, z0 + 13, x1, z1)):
        k.walls(a0, f + 1, b0, a1, f + 5, b1, role="plaster", pillar="minecraft:stripped_dark_oak_log", windows=0,
                interior_air=True)
        K.beam_band(k, a0, b0, a1, b1, f + 5, role="trim")
        k.fill(a0 + 1, f + 5, b0 + 1, a1 - 1, f + 5, b1 - 1, "wood")
        K.roof_over(k, a0 - 1, b0 - 1, a1 + 1, b1 + 1, f + 6, kind="gable", along="x" if a1 - a0 > b1 - b0 else "z",
                    overhang=1, top=None)
    for x in range(x0 + 3, x1 - 12, 4):
        K.lattice(k, x, f + 2, z0 + 12, "south")
        K.lattice(k, x, f + 3, z0 + 12, "south")
    for dy in (1, 2):
        k.put(70, f + dy, z0 + 12, "minecraft:air")
        k.put(x1 - 10, f + dy, 28, "minecraft:air")
    K.door(k, 70, f + 1, z0 + 12, "south")
    K.door(k, x1 - 10, f + 1, 28, "west")
    # the hall: shelves everywhere, the ink desk, the tea table, the incense altar
    K.bookshelf_wall(k, x0 + 1, z0 + 1, x1 - 1, z0 + 1, f + 1, f + 4, chiseled_every=3, face="south")
    K.bookshelf_wall(k, x0 + 1, z0 + 2, x0 + 1, z0 + 11, f + 1, f + 3, chiseled_every=3, face="east")
    K.table(k, 62, f + 1, z0 + 6)
    kursi(k, 62, f + 1, z0 + 7, "north", titik=False)
    k.titik("meja_tulis", 62, f + 1, z0 + 7, "north")
    K.low_table(k, 72, f + 1, z0 + 6)
    for (dx, dz) in ((-1, 0), (1, 0), (0, 1)):
        k.put(72 + dx, f + 1, z0 + 6 + dz, "minecraft:red_carpet")
        k.titik("kursi_lantai", 72 + dx, f + 1, z0 + 6 + dz, "north")
    K.incense_burner(k, 80, f + 1, z0 + 4)
    k.titik("dupa", 80, f + 1, z0 + 6, "north")
    for (x, z) in ((62, z0 + 6), (74, z0 + 6), (82, z0 + 8)):
        K.lit(k, x, f + 5, z, chain=1)
    # the side room: his bed, a chest, a rack of scrolls
    ranjang(k, x1 - 2, f + 1, z0 + 15, "north", warna="black")
    peti(k, x1 - 1, f + 1, z0 + 19, None, "west")
    K.bookshelf_wall(k, x1 - 1, z0 + 21, x1 - 1, z1 - 2, f + 1, f + 2, face="west")
    K.lit(k, x1 - 5, f + 5, z0 + 20, chain=1)
    k.kotak("bangunan", x0, f, z0, x1, f + 16, z1, "south", nama="rumah_mo_wen")
    k.titik("pintu", 70, f + 1, z1 + 1, "south")
    # the courtyard: gravel, two dead trees, a stone bench, the chime of chains
    for x in range(x0 + 1, x1 - 11):
        for z in range(z0 + 13, z1):
            k.put(x, f, z, "minecraft:gravel" if (x + z) % 4 else "minecraft:soul_soil")
    for (tx, tz) in ((61, 28), (73, 33)):
        tiang(k, tx, f + 1, f + 5, tz, role="minecraft:dark_oak_log")
        k.put(tx + 1, f + 4, tz, "minecraft:dark_oak_log", {"axis": "x"})
        k.put(tx, f + 5, tz + 1, "minecraft:dark_oak_log", {"axis": "z"})
    K.bench(k, 64, 34, "north", n=3)
    for i in range(3):
        rantai(k, 70 + i, f + 3, f + 4, z0 + 13)
    k.titik("obrol", 66, f + 1, 30, "north")


def kolam_semedi(k):
    """Lot (1,1): the Blood Meditation Pool - a sunken red pool with stepping stones, six mats (meditasi x 6), a
    colonnade of basalt with chains and banners, a crying-obsidian stele shrine; anchor kota_tema/kolam_semedi
    (the extra meditation site, 38.T.11)."""
    x0, z0, x1, z1 = 56, 56, 90, 90
    cx, cz = 73, 74
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, LY, z, "paving_alt" if max(abs(x - cx), abs(z - cz)) % 3 == 0 else "floor")
    kolam_darah(k, cx - 8, cz - 7, cx + 8, cz + 7, LY)
    for (x, z) in ((cx, cz - 4), (cx, cz), (cx, cz + 4), (cx - 4, cz), (cx + 4, cz)):
        k.put(x, LY, z, "kerb_chisel")
        k.slab(x, LY + 1, z, "kerb_slab")
    mats = [(cx - 11, cz - 4, "east"), (cx - 11, cz + 4, "east"), (cx + 11, cz - 4, "west"), (cx + 11, cz + 4, "west"),
            (cx, cz - 10, "south"), (cx, cz + 10, "north")]
    for (x, z, hadap) in mats:
        k.put(x, LY + 1, z, "minecraft:red_carpet")
        k.titik("meditasi", x, LY + 1, z, hadap, {"situs": "kolam_semedi_darah"})
        lilin(k, x + (0 if hadap in ("east", "west") else 1), LY + 1, z + (1 if hadap in ("east", "west") else 0))
    for x in range(x0 + 1, x1, 5):
        for z in (z0 + 1, z1 - 1):
            tiang(k, x, LY + 1, LY + 5, z)
            k.put(x, LY + 6, z, "bata_merah")
            rantai(k, x, LY + 3, LY + 4, z + (1 if z == z0 + 1 else -1))
    for z in range(z0 + 1, z1, 5):
        for x in (x0 + 1, x1 - 1):
            tiang(k, x, LY + 1, LY + 5, z)
            k.put(x, LY + 6, z, "bata_merah")
    for x in range(x0 + 1, x1):
        for z in (z0 + 1, z1 - 1):
            if k.get(x, LY + 6, z) is None:
                k.slab(x, LY + 6, z, "kerb_slab", "top")
    for z in range(z0 + 1, z1):
        for x in (x0 + 1, x1 - 1):
            if k.get(x, LY + 6, z) is None:
                k.slab(x, LY + 6, z, "kerb_slab", "top")
    for x in range(x0 + 4, x1 - 2, 10):
        panji(k, x, LY + 5, z0 + 2, "south", "red")
    # the stele shrine (north)
    k.fill(cx - 2, LY + 1, z0 + 3, cx + 2, LY + 1, z0 + 5, "kerb")
    for y in range(LY + 2, LY + 9):
        k.put(cx, y, z0 + 4, "minecraft:crying_obsidian" if y % 2 else "minecraft:chiseled_polished_blackstone")
    for x in (cx - 2, cx + 2):
        lilin(k, x, LY + 2, z0 + 4, n=4)
    k.put(cx, LY + 9, z0 + 4, "bata_merah")
    paku(k, cx, LY + 10, z0 + 4)
    k.jangkar(cx + 0.5, LY + 1, z0 + 7.5, "kota_tema/kolam_semedi", yaw=0.0)
    k.titik("pandang", cx, LY + 2, z1 - 2, "north", {"nama": "kolam_semedi"})


def d_menara_terlarang():
    D = "menara_terlarang"
    k = kanvas(D, h=72)
    tanah_kota(k, D)
    K.paving(k, 45, 3, 50, 92, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 51, 45, 92, 50, LY, role="path", alt="path_alt", alt_chance=0.2)
    menara_kitab(k)
    rumah_mo_wen(k)
    kolam_semedi(k)
    lampu_jalan(k, [(53, 52), (90, 52), (53, 43), (90, 43)])
    names = {(0, 0): "menara_utara", (0, 1): "menara_selatan", (1, 0): "rumah_mo_wen", (1, 1): "kolam_semedi"}
    simpan_distrik(k, D, names, seams=(((0, 0), (0, 1)),), utuh=("menara_utara", "menara_selatan"))


# ===================================================================================================== penjara_tawanan
SEL_X = (10, 17, 24, 31, 38, 55, 62, 69, 76, 83)    # cell west edges (5 wide, 6 deep), the north row of the yard
SEL_Z0 = 7


def sel_kecil(k, x0, z0, nomor):
    """One captive cell (5 x 6 outer, 3 x 4 inside): blackstone walls, a barred front with the door gap where the
    SelTawananEntity stands (titik sel_tawanan), straw, a water bucket (cauldron), a chain on the wall."""
    x1, z1 = x0 + 4, z0 + 5
    k.fill(x0, LY, z0, x1, LY, z1, "floor_alt")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge:
                for y in (LY + 1, LY + 2, LY + 3):
                    k.put(x, y, z, "minecraft:air")
                continue
            for y in (LY + 1, LY + 2, LY + 3):
                if z == z1 and x0 < x < x1:
                    k.put(x, y, z, "minecraft:air" if x == x0 + 2 else "minecraft:iron_bars")
                else:
                    k.put(x, y, z, "bata_hitam" if not (x in (x0, x1) and z in (z0, z1)) else "minecraft:polished_basalt",
                          {"axis": "y"} if (x in (x0, x1) and z in (z0, z1)) else None)
            k.put(x, LY + 4, z, "kerb")
    k.fill(x0 + 1, LY + 4, z0 + 1, x1 - 1, LY + 4, z1 - 1, "kerb")
    k.put(x0 + 1, LY + 1, z0 + 1, "minecraft:hay_block", {"axis": "x"})
    k.put(x0 + 3, LY + 1, z0 + 1, "minecraft:cauldron")
    rantai(k, x0 + 2, LY + 2, LY + 3, z0 + 1)
    k.put(x0 + 2, LY + 1, z0 + 2, "minecraft:brown_carpet")
    k.titik("sel_tawanan", x0 + 2, LY + 1, z1, "south", {"nomor": nomor})
    k.titik("kasur", x0 + 2, LY + 1, z0 + 2, "south", {"tawanan": nomor})
    k.put(x0 + 2, LY + 4, z1 + 1, "minecraft:soul_lantern", {"hanging": "true", "waterlogged": "false"}) if nomor % 2 == 0 else None


def kandang_tawanan(k):
    """Lots (0,0)+(1,0): the Captives' Pens - an iron-bar compound, ten cells along the north, the yard with a feeding
    trough and the watch tower in the middle, the guard desk at the gate (anchor kota_tema/tawanan: ransom only)."""
    pagar_besi(k, 6, 4, 90, 42, h=3, gerbang=("south", 46, 4))
    for x in range(7, 90):
        for z in range(5, 42):
            k.put(x, LY, z, "minecraft:coarse_dirt" if (x * 5 + z * 3) % 7 == 0 else ("floor" if (x + z) % 3 else "floor_alt"))
    for i, x in enumerate(SEL_X):
        sel_kecil(k, x, SEL_Z0, i)
    # the watch tower: 6 x 6, a lookout 14 high under a pointed roof, a ladder inside
    tx0, tz0 = 45, 20
    for x in range(tx0, tx0 + 6):
        for z in range(tz0, tz0 + 6):
            edge = x in (tx0, tx0 + 5) or z in (tz0, tz0 + 5)
            for y in range(LY + 1, LY + 14):
                if edge:
                    corner = x in (tx0, tx0 + 5) and z in (tz0, tz0 + 5)
                    k.put(x, y, z, "minecraft:polished_basalt" if corner else ("minecraft:iron_bars" if y in (LY + 6, LY + 10) and
                          (x - tx0 + z - tz0) % 2 else "bata_hitam"), {"axis": "y"} if corner else None)
                else:
                    k.put(x, y, z, "minecraft:air")
            k.put(x, LY + 14, z, "kerb")
    for x in range(tx0 - 1, tx0 + 7):
        for z in range(tz0 - 1, tz0 + 7):
            if x in (tx0 - 1, tx0 + 6) or z in (tz0 - 1, tz0 + 6):
                k.put(x, LY + 14, z, "kerb")
                k.put(x, LY + 15, z, "kerb_wall")
    K.ladder(k, tx0 + 4, LY + 1, LY + 14, tz0 + 4, "north")
    k.put(tx0 + 4, LY + 14, tz0 + 4, "minecraft:iron_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                                "powered": "false", "waterlogged": "false"})
    for dy in (1, 2):
        k.put(tx0 + 2, LY + dy, tz0 + 5, "minecraft:air")
    for (x, z) in ((tx0, tz0), (tx0 + 5, tz0), (tx0, tz0 + 5), (tx0 + 5, tz0 + 5)):
        tiang(k, x, LY + 15, LY + 17, z)
    atap_runcing(k, tx0 - 1, tz0 - 1, tx0 + 6, tz0 + 6, LY + 18)
    K.drum(k, tx0 + 1, LY + 15, tz0 + 1, "south")
    k.titik("jaga", tx0 + 2, LY + 15, tz0 + 1, "north")
    k.titik("jaga", tx0 + 3, LY + 15, tz0 + 4, "south")
    k.titik("pandang", tx0 + 3, LY + 16, tz0 + 6, "north", {"nama": "kandang_tawanan"})
    # the yard: a feeding trough, buckets, a whipping post, straw
    for x in range(20, 30):
        k.put(x, LY + 1, 22, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                           "powered": "false", "waterlogged": "false"}) if x % 3 else \
            k.put(x, LY + 1, 22, "minecraft:cauldron")
    k.titik("kerja:kuali", 25, LY + 1, 24, "north")
    for x in range(64, 72):
        if x % 2:
            k.put(x, LY + 1, 26, "minecraft:hay_block", {"axis": "x"})
    tiang(k, 76, LY + 1, LY + 4, 30)
    rantai(k, 75, LY + 2, LY + 3, 30)
    rantai(k, 77, LY + 2, LY + 3, 30)
    for (x, z) in ((12, 30), (34, 34), (60, 36), (84, 30)):
        k.titik("main", x, LY + 1, z, "north", {"tawanan": "1"})
    # the guard desk by the gate (inside), the ransom anchor
    K.counter(k, 52, 58, 39, LY, "south")
    k.put(55, LY + 2, 39, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    k.jangkar(55.5, LY + 1, 37.5, "kota_tema/tawanan", yaw=180.0)
    k.titik("jaga", 55, LY + 1, 38, "south")
    k.titik("jaga", 44, LY + 1, 40, "south")
    tokoh(k, 26, 14, "a_fu", hadap="south")
    for x in range(10, 88, 12):
        obor_jiwa(k, x, 15)
    lampu_jalan(k, [(8, 40), (88, 40)])


def ruang_penjaga_penjara(k):
    """Lot (0,1): the jailers' house - six bunks, the key board and the chain rack, the prisoners' kitchen (the 08:00
    feeding), a mess table, a store of shackles."""
    x0, z0, x1, z1 = 6, 55, 40, 85
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "floor_alt")
    k.walls(x0, f + 1, z0, x1, f + 5, z1, role="kerb", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, x0, z0, x1, z1, f + 2, f + 3, every=4, skip=("north",))
    K.beam_band(k, x0, z0, x1, z1, f + 5, role="trim")
    k.fill(x0 + 1, f + 5, z0 + 1, x1 - 1, f + 5, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 6, kind="hip", overhang=1)
    for dy in (1, 2, 3):
        for x in (22, 23):
            k.put(x, f + dy, z0, "minecraft:air")
    pintu_besi(k, 22, f + 1, z0, "north", lebar=2)
    # partition: bunk room (south), hall (north)
    for x in range(x0 + 1, x1):
        for dy in range(1, 5):
            k.put(x, f + dy, 72, "minecraft:air" if (x in (22, 23) and dy <= 2) else "kerb")
    for i in range(6):
        ranjang(k, x0 + 3 + i * 5, f + 1, z1 - 1, "south", warna="black")
        k.put(x0 + 4 + i * 5, f + 1, z1 - 1, "minecraft:barrel", {"facing": "up", "open": "false"}) if i < 5 else None
    K.lit(k, 15, f + 5, 79, chain=1)
    K.lit(k, 30, f + 5, 79, chain=1)
    # the hall: the key board (tripwire hooks on dark oak), the chain rack, a table, the kitchen
    for x in range(x0 + 2, x0 + 8):
        k.put(x, f + 2, z0 + 1, "wood")
        k.put(x, f + 2, z0 + 2, "minecraft:tripwire_hook", {"attached": "false", "facing": "south", "powered": "false"})
    rak_rantai(k, x0 + 10, f + 1, z0 + 1, "x", n=5)
    K.furnish_kitchen(k, x1 - 7, z0 + 1, x1 - 1, f, z0 + 1, "south")
    for x in range(14, 30):
        K.table(k, x, f + 1, 65, "minecraft:dark_oak_pressure_plate" if x % 4 else False)
    for x in range(14, 30, 3):
        kursi(k, x, f + 1, 64, "south")
        kursi(k, x, f + 1, 66, "north")
    k.titik("meja_makan", 21, f + 1, 65, "south")
    K.crates(k, x0 + 1, 68, x0 + 3, 70, f + 1, h=2)
    for (x, z) in ((14, 62), (30, 62), (22, 68)):
        K.lit(k, x, f + 5, z, chain=1)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, 71, f, "penjara", isi=0.45, rug=False)
    ruang(k, x0 + 1, 73, x1 - 1, z1 - 1, f, "barak_iblis", pulau=("peti", "senjata"), jarak=6, isi=0.4, seed=1)
    k.kotak("bangunan", x0, f, z0, x1, f + 14, z1, "north", nama="ruang_penjaga")
    k.titik("pintu", 22, f + 1, z0 - 1, "north")
    lampu_jalan(k, [(5, 53), (41, 53), (5, 91), (41, 91)])


def terowongan_tua(k):
    """Lot (1,1): the Old Tunnel - a collapsed shed over a stair down, a 3-wide passage under the street running east
    to the wall (its far end is the rubble entity of the wall piece); the inner anchor kota_tema/terowongan."""
    # the collapsed shed (mossy, half its roof gone)
    x0, z0, x1, z1 = 56, 72, 66, 86
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                hgt = 4 if (x + z) % 5 else 2
                for dy in range(1, hgt + 1):
                    k.put(x, LY + dy, z, "minecraft:mossy_cobblestone" if (x * 3 + z + dy) % 4 == 0 else "kerb_alt")
            elif x < 61:
                k.slab(x, LY + 5, z, "minecraft:dark_oak_slab", "bottom")
    for dy in (1, 2, 3):
        k.put(x0, LY + dy, 79, "minecraft:air")
        k.put(x0, LY + dy, 80, "minecraft:air")
    for (x, z) in ((62, 74), (63, 75), (64, 84), (58, 85)):
        k.put(x, LY + 1, z, "minecraft:cobblestone")
    k.slab(63, LY + 2, 75, "minecraft:cobblestone_slab", "bottom")
    # the stair down (west -> east): y LY at x 59 to y 1 at x 62
    for i, x in enumerate(range(59, 63)):
        for z in (78, 79, 80):
            for y in range(LY - i, LY + 4):
                k.put(x, y, z, "minecraft:air")
            k.stairs(x, LY - i, z, "minecraft:cobblestone_stairs", "west")
            if i < 3:
                k.put(x, LY - i - 1, z, "minecraft:cobblestone")
    # the passage x 63 .. 95 at z 78 .. 80
    for x in range(63, 96):
        for z in range(77, 82):
            edge = z in (77, 81)
            k.put(x, 0, z, "minecraft:mossy_cobblestone" if (x + z) % 3 == 0 else "kerb_alt")
            for y in range(1, LY):
                if edge:
                    k.put(x, y, z, "minecraft:mossy_cobblestone" if (x + y) % 4 == 0 else "minecraft:cracked_polished_blackstone_bricks")
                else:
                    k.put(x, y, z, "minecraft:air")
            k.put(x, LY, z, "kerb") if k.get(x, LY + 1, z) is None else None
        if x % 6 == 3:
            lentera_jiwa(k, x, LY - 1, 79)
        if x % 9 == 0:
            for y in (1, 2):
                k.put(x, y, 78, "minecraft:dark_oak_log", {"axis": "y"})
                k.put(x, y, 80, "minecraft:dark_oak_log", {"axis": "y"})
            k.put(x, 3, 78, "minecraft:dark_oak_log", {"axis": "z"})
            k.put(x, 3, 79, "minecraft:dark_oak_log", {"axis": "z"})
            k.put(x, 3, 80, "minecraft:dark_oak_log", {"axis": "z"})
    k.jangkar(61.5, LY + 1, 79.5, "kota_tema/terowongan", sisi="dalam")
    k.jalur([(60, 79, LY + 1), (63, 79, 1), (80, 79, 1), (94, 79, 1)], jalur="terowongan")
    # the yard round it: rubble, a dead tree, crimson roots
    for i, (x, z) in enumerate(((70, 60), (72, 62), (84, 66), (88, 86), (76, 88), (60, 62))):
        tanah_jiwa(k, x, z, n=i)
    K.crates(k, 80, 56, 84, 58, LY + 1, h=2)
    k.titik("jaga", 68, LY + 1, 79, "west")
    lampu_jalan(k, [(53, 53), (90, 53), (53, 91), (90, 91)])


def d_penjara_tawanan():
    D = "penjara_tawanan"
    k = kanvas(D, h=36)
    tanah_kota(k, D)
    K.paving(k, 3, 45, 92, 50, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 45, 51, 50, 92, LY, role="path", alt="path_alt", alt_chance=0.2)
    kandang_tawanan(k)
    ruang_penjaga_penjara(k)
    terowongan_tua(k)
    names = {(0, 0): "kandang_barat", (1, 0): "kandang_timur", (0, 1): "ruang_penjaga", (1, 1): "terowongan_tua"}
    simpan_distrik(k, D, names, seams=(((0, 0), (1, 0)),))


# ===================================================================================================== pasar_gelap (ZP)
BARANG_GELAP = {
    "racun": ["minecraft:cauldron", "minecraft:brewing_stand", "minecraft:cauldron"],
    "pil_darah": ["minecraft:brewing_stand", "minecraft:red_candle", "minecraft:flower_pot"],
    "tulang": ["minecraft:bone_block", "minecraft:skeleton_skull", "minecraft:bone_block"],
    "senjata": ["minecraft:iron_bars", "minecraft:grindstone", "minecraft:iron_bars"],
    "peta": ["minecraft:cartography_table", "minecraft:lectern", "minecraft:cartography_table"],
    "inti": ["minecraft:crying_obsidian", "minecraft:magma_block", "minecraft:crying_obsidian"],
    "topeng": ["minecraft:carved_pumpkin", "minecraft:wither_skeleton_skull", "minecraft:skeleton_skull"],
}
JENIS_LAPAK = ("racun", "pil_darah", "tulang", "senjata", "peta", "inti")


def lapak_gelap(k, x, z, facing, nomor, jenis):
    """A curtained stall of the Thousand Shadows market: counter, goods, black-and-red awning, curtains on both sides,
    a red candle and a hanging soul lantern; titik lapak (data jenis, nomor)."""
    goods = BARANG_GELAP[jenis]
    sx, sz = K.stall(k, x, z, facing, w=3, cloth=("minecraft:black_wool", "minecraft:red_wool"))
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i, g in enumerate(goods):
        gx, gz = x + rx * i, z + rz * i
        if g in ("minecraft:carved_pumpkin",):
            k.put(gx, LY + 2, gz, g, {"facing": facing})
        elif g.endswith("skull"):
            k.put(gx, LY + 2, gz, g, {"rotation": str({"south": 0, "west": 4, "north": 8, "east": 12}[facing])})
        elif g == "minecraft:brewing_stand":
            k.put(gx, LY + 2, gz, g, {"has_bottle_0": "true", "has_bottle_1": "true", "has_bottle_2": "false"})
        elif g == "minecraft:lectern":
            k.put(gx, LY + 2, gz, g, {"facing": facing, "has_book": "true", "powered": "false"})
        elif g == "minecraft:grindstone":
            k.put(gx, LY + 2, gz, g, {"face": "floor", "facing": facing})
        elif g == "minecraft:red_candle":
            k.put(gx, LY + 2, gz, g, {"candles": "3", "lit": "true", "waterlogged": "false"})
        elif g == "minecraft:bone_block":
            k.put(gx, LY + 2, gz, g, {"axis": "x"})
        else:
            k.put(gx, LY + 2, gz, g)
    # curtains on the two sides of the booth
    for i in (-1, 3):
        for back in (0, 1, 2):
            px, pz = x + rx * i - dx * back, z + rz * i - dz * back
            for dy in (1, 2, 3):
                k.put(px, LY + dy, pz, "minecraft:black_wool" if (back + dy) % 2 else "minecraft:red_wool")
    lentera_jiwa(k, x + rx, LY + 3, z + rz - 0)
    k.titik("lapak", sx, LY + 1, sz, facing, {"jenis": jenis, "nomor": nomor})
    return sx, sz


def pasar_seribu_bayangan(k):
    """Lots (0,0)+(1,0): the Market of a Thousand Shadows - three narrow alleys under black cloth, twenty curtained
    stalls (poison, blood pills, demon-beast bones, stolen weapons, lair maps, beast cores) and the mask kiosk; two
    broker anchors kota_tema/pasar_gelap; the Pact Zone boxes."""
    nomor = 0
    alleys = (9, 23, 37)
    xs = (8, 28, 58, 78)
    for ai, az in enumerate(alleys):
        for x in range(4, 92):
            for z in range(az - 1, az + 2):
                k.put(x, LY, z, "path" if (x + z) % 4 else "path_alt")
        # the cloth roof over the alley (black with red stripes, open every 7th for smoke)
        for x in range(4, 92):
            if x % 7 == 3:
                continue
            for z in range(az - 1, az + 2):
                k.put(x, LY + 5, z, "minecraft:red_carpet" if x % 9 == 0 else "minecraft:black_carpet")
        for x in range(6, 92, 6):
            for z in (az - 2, az + 2):
                if k.get(x, LY + 1, z) is None:
                    k.put(x, LY + 1, z, "minecraft:dark_oak_fence")
                    k.put(x, LY + 2, z, "minecraft:dark_oak_fence")
                    k.put(x, LY + 3, z, "minecraft:dark_oak_fence")
                    k.put(x, LY + 4, z, "minecraft:dark_oak_fence")
        for x in range(10, 90, 10):
            K.lit(k, x, LY + 5, az, chain=0)
        for j, x in enumerate(xs):
            # north side faces south (onto the alley), south side faces north
            jn = JENIS_LAPAK[(nomor + ai) % len(JENIS_LAPAK)]
            if not (ai == 1 and j == 1):
                lapak_gelap(k, x + 2, az - 3, "south", nomor, jn)
                nomor += 1
            js = JENIS_LAPAK[(nomor + 2) % len(JENIS_LAPAK)]
            if not (ai == 2 and j == 3):
                lapak_gelap(k, x, az + 3, "north", nomor, js)
                nomor += 1
    # the mask kiosk (centre alley, north side): masks on armour stands and shelves, its own sign of banners
    lapak_gelap(k, 30, 20, "south", nomor, "topeng")
    nomor += 1
    for (x, z, yaw) in ((26, 18, 180.0), (34, 18, 180.0)):
        k.entity(x + 0.5, LY + 1, z + 0.5, {"id": "minecraft:armor_stand", "ShowArms": nbt.Byte(0),
                                            "Rotation": [nbt.Float(yaw), nbt.Float(0.0)],
                                            "equipment": {"head": {"id": "minecraft:carved_pumpkin", "count": nbt.Int(1)},
                                                          "chest": {"id": "minecraft:leather_chestplate", "count": nbt.Int(1)}}})
    panji(k, 29, LY + 4, 21, "south", "red")
    panji(k, 32, LY + 4, 21, "south", "black")
    # the backs of the rows: storage, crates, a covered den between the rows
    for zz in (15, 16, 29, 30):
        for x in range(6, 90, 4):
            if k.get(x, LY + 1, zz) is None and (x + zz) % 3 and not 43 <= x <= 52:
                k.put(x, LY + 1, zz, "minecraft:barrel", {"facing": "up", "open": "false"})
    K.crates(k, 36, 14, 41, 16, LY + 1, h=2)
    K.crates(k, 54, 28, 57, 30, LY + 1, h=2)
    # the brokers' booths at the central lane (anchors kota_tema/pasar_gelap x 2)
    for (x, z, yaw, nama) in ((47, 12, 180.0, "barat"), (48, 40, 0.0, "timur")):
        k.jangkar(x + 0.5, LY + 1, z + 0.5, "kota_tema/pasar_gelap", yaw=yaw, kios=nama)
    # the pact: guards at the alley mouths, the zone boxes (one per lot), a pact stele at the west entrance
    for az in alleys:
        k.titik("jaga", 4, LY + 1, az, "east")
        k.titik("jaga", 91, LY + 1, az, "west")
    for x in (6, 89):
        for y in range(LY + 1, LY + 6):
            k.put(x, y, 44, "minecraft:polished_basalt", {"axis": "y"})
        k.put(x, LY + 6, 44, "minecraft:crying_obsidian")
    k.titik("pandang", 47, LY + 3, 44, "north", {"nama": "pasar_seribu_bayangan"})
    for x in range(8, 90, 14):
        k.titik("penonton", x, LY + 1, 23, "north")
    return nomor


def balai_lelang_gelap(k):
    """Lot (0,1): the Dark Auction Hall - a small hall with three tiers of benches, a red-curtained stage, the auctioneer's
    lectern and the lot table, guards at the door; anchor kota_tema/lelang_gelap (BalaiLelang profile GELAP)."""
    x0, z0, x1, z1 = 6, 55, 41, 89
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "floor_alt")
    k.walls(x0, f + 1, z0, x1, f + 8, z1, role="bata_hitam", pillar="bata_merah", windows=0, interior_air=False)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 6, z1 - 1)
    jendela_besi(k, x0, z0, x1, z1, f + 4, f + 6, every=6, skip=("south",))
    K.beam_band(k, x0, z0, x1, z1, f + 8, role="trim")
    k.fill(x0 + 1, f + 8, z0 + 1, x1 - 1, f + 8, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 9, kind="hip", overhang=1)
    for x in (23, 24):
        for dy in (1, 2, 3):
            k.put(x, f + dy, z1, "minecraft:air")
    pintu_besi(k, 24, f + 1, z1, "south", lebar=2)
    for x in (21, 26):
        panji(k, x, f + 4, z1 + 1, "south", "red")
    # the stage (north): a platform 2 high, curtains, the lot table, the lectern
    for x in range(x0 + 2, x1 - 1):
        for z in range(z0 + 1, z0 + 8):
            k.put(x, f + 1, z, "kerb")
            k.put(x, f + 2, z, "wood" if z > z0 + 1 else "kerb")
    for x in range(x0 + 2, x1 - 1):
        k.stairs(x, f + 1, z0 + 8, "kerb_stairs", "north")
    tirai(k, x0 + 2, x0 + 6, z0 + 2, f + 3, f + 7)
    tirai(k, x1 - 6, x1 - 2, z0 + 2, f + 3, f + 7)
    for x in range(x0 + 7, x1 - 6):
        k.put(x, f + 7, z0 + 2, "minecraft:red_wool")
    k.put(23, f + 3, z0 + 6, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    for x in range(19, 29):
        if x != 23:
            K.table(k, x, f + 3, z0 + 4, "minecraft:red_carpet" if x % 2 else False)
    k.put(21, f + 4, z0 + 4, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    k.put(26, f + 4, z0 + 4, "minecraft:skeleton_skull", {"rotation": "0"})
    k.jangkar(23.5, f + 3, z0 + 5.5, "kota_tema/lelang_gelap", yaw=180.0)
    k.titik("panggung", x0 + 2, f + 3, z0 + 3, "south", {"dx": x1 - x0 - 4, "dy": 4, "dz": 5})
    # three tiers of benches
    for t in range(3):
        zz = z0 + 12 + t * 5
        for x in range(x0 + 2, x1 - 1):
            for z in range(zz, zz + 4):
                for y in range(f + 1, f + 1 + t):
                    k.put(x, y, z, "kerb")
            if x in (23, 24):
                continue
            k.stairs(x, f + 1 + t, zz, "minecraft:dark_oak_stairs", "south")
            if x % 3 == 0:
                k.titik("kursi", x, f + 1 + t, zz, "north", {"lelang": "1"})
        for x in (23, 24):
            for z in range(zz, zz + 4):
                k.put(x, f + t, z, "kerb") if t > 0 else None
    for (x, z) in ((14, 66), (32, 66), (14, 78), (32, 78), (23, z0 + 6)):
        K.lit(k, x, f + 8, z, chain=2)
    for x in (x0 + 1, x1 - 1):
        k.titik("jaga", x, f + 1, z1 - 2, "north")
    ruang(k, x0 + 1, z0 + 27, x1 - 1, z1 - 1, f, "lelang", isi=0.55, rug=False)
    k.kotak("bangunan", x0, f, z0, x1, f + 18, z1, "south", nama="balai_lelang_gelap")
    k.titik("pintu", 24, f + 1, z1 + 1, "south")


def rumah_tukar(k):
    """Lot (1,1): the Broker's Exchange of Madam Bai She - the front office with a long counter and weighing plates,
    the strong room (iron door, chests, barrels), the scented back room where she receives (snake-coiled couch,
    incense, a jade-green carpet)."""
    x0, z0, x1, z1 = 56, 55, 90, 89
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "wood")
    k.walls(x0, f + 1, z0, x1, f + 6, z1, role="plaster", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, x0, z0, x1, z1, f + 2, f + 3, every=4, skip=("west",))
    K.beam_band(k, x0, z0, x1, z1, f + 6, role="trim")
    k.fill(x0 + 1, f + 6, z0 + 1, x1 - 1, f + 6, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 7, kind="hip", overhang=1)
    for z in (71, 72):
        for dy in (1, 2, 3):
            k.put(x0, f + dy, z, "minecraft:air")
    pintu_besi(k, x0, f + 1, 72, "west", lebar=2)
    panji(k, x0 - 1, f + 4, 69, "west", "red")
    panji(k, x0 - 1, f + 4, 74, "west", "black")
    # partitions: office (west), strong room (north-east), back room (south-east)
    for z in range(z0 + 1, z1):
        for dy in range(1, 6):
            k.put(72, f + dy, z, "minecraft:air" if (z in (71, 72) and dy <= 2) else "kerb")
    for x in range(73, x1):
        for dy in range(1, 6):
            k.put(x, f + dy, 70, "minecraft:air" if (x == 80 and dy <= 2) else "kerb")
    K.door(k, 72, f + 1, 72, "east")
    k.put(80, f + 1, 70, "minecraft:iron_door", {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    k.put(80, f + 2, 70, "minecraft:iron_door", {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    k.put(81, f + 2, 71, "minecraft:stone_button", {"face": "wall", "facing": "south", "powered": "false"})
    k.put(79, f + 2, 69, "minecraft:stone_button", {"face": "wall", "facing": "north", "powered": "false"})
    # office: the counter along z with weighing plates, ledgers, a brazier of soul lantern light
    K.counter_z(k, 64, z0 + 4, z1 - 4, f, "west")
    for z in (64, 80):
        k.put(64, f + 2, z, "minecraft:light_weighted_pressure_plate", {"power": "0"})
    k.put(66, f + 1, 72, "minecraft:lectern", {"facing": "west", "has_book": "true", "powered": "false"})
    k.titik("kerja:timbangan", 65, f + 1, 66, "west")
    k.titik("kerja:timbangan", 65, f + 1, 78, "west")
    K.bookshelf_wall(k, 71, z0 + 2, 71, z0 + 10, f + 1, f + 3, face="west")
    for (x, z) in ((60, 62), (60, 82), (67, 72)):
        K.lit(k, x, f + 6, z, chain=1)
    k.jangkar(66.5, f + 1, 70.5, "kota_tema/makelar", yaw=90.0)
    # strong room: chests and barrels
    for x in range(74, x1 - 1, 2):
        peti(k, x, f + 1, z0 + 1, "kultivator:tema/iblis/makelar" if x % 4 == 2 else None, "south")
        k.put(x, f + 1, z0 + 3, "minecraft:barrel", {"facing": "up", "open": "false"})
    for x in range(74, x1 - 1, 3):
        k.put(x, f + 2, z0 + 3, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    k.titik("jaga", 80, f + 1, 68, "south")
    # back room: the couch, incense, green carpet, a coiled 'snake' of green and gold
    K.rug(k, 74, 72, x1 - 2, z1 - 2, f + 1, role="minecraft:green_carpet", border="minecraft:black_carpet")
    for x in range(78, 85):
        k.stairs(x, f + 1, z1 - 1, "minecraft:dark_oak_stairs", "south")
        k.titik("kursi", x, f + 1, z1 - 1, "north") if x == 81 else None
    k.put(77, f + 1, z1 - 1, "minecraft:dark_oak_log", {"axis": "y"})
    k.put(85, f + 1, z1 - 1, "minecraft:dark_oak_log", {"axis": "y"})
    K.incense_burner(k, x1 - 2, f + 1, 73)
    k.titik("dupa", x1 - 3, f + 1, 74, "east")
    for (x, y, z) in ((75, f + 1, 85), (75, f + 2, 85), (76, f + 3, 85), (76, f + 3, 86), (75, f + 4, 86)):
        k.put(x, y, z, "minecraft:green_terracotta" if y % 2 else "minecraft:lime_terracotta")
    K.table(k, 81, f + 1, z1 - 3)
    k.put(81, f + 2, z1 - 3, "minecraft:flower_pot")
    tokoh(k, 81, z1 - 4, "bai_she", hadap="north")
    for (x, z) in ((80, 76), (80, 84)):
        K.lit(k, x, f + 6, z, chain=1)
    k.kotak("bangunan", x0, f, z0, x1, f + 16, z1, "west", nama="rumah_tukar")
    k.titik("pintu", x0 - 1, f + 1, 72, "west")


def d_pasar_gelap():
    D = "pasar_gelap"
    k = kanvas(D, h=36)
    tanah_kota(k, D)
    K.paving(k, 3, 45, 92, 50, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 45, 51, 50, 92, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 44, 3, 51, 44, LY, role="path", alt="path_alt", alt_chance=0.2)
    pasar_seribu_bayangan(k)
    balai_lelang_gelap(k)
    rumah_tukar(k)
    for (i, j) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        k.kotak("zona_perjanjian", 48 * i, LY, 48 * j, 48 * i + 47, LY + 16, 48 * j + 47, "south", distrik=D)
    lampu_jalan(k, [(44, 52), (51, 64), (44, 76), (51, 88)])
    names = {(0, 0): "pasar_barat", (1, 0): "pasar_timur", (0, 1): "balai_lelang_gelap", (1, 1): "rumah_tukar"}
    simpan_distrik(k, D, names, seams=(((0, 0), (1, 0)),))


# ===================================================================================================== sarang_kontrak
GUDANG_TONG = ((-4, -4), (0, -4), (4, -4), (-4, 4), (0, 4), (4, 4))   # oil barrel spots round kota_tema/gudang_minyak


def sarang_kontrak_aula(k):
    """Lot (0,0): the Contract Den (the assassins' guild) - a black hall with the wall of contracts (four boards; the
    live posters are a client billboard), three curtained counters, drinking tables, racks of twin knives, and the
    secret room behind the curtains (anchor kota_tema/kontrak)."""
    x0, z0, x1, z1 = 8, 10, 52, 40
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "floor_alt")
    k.walls(x0, f + 1, z0, x1, f + 7, z1, role="bata_hitam", pillar="minecraft:polished_basalt", windows=0, interior_air=False)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 5, z1 - 1)
    jendela_besi(k, x0, z0, x1, z1, f + 4, f + 5, every=6, skip=("north", "west"))
    K.beam_band(k, x0, z0, x1, z1, f + 7, role="trim")
    k.fill(x0 + 1, f + 7, z0 + 1, x1 - 1, f + 7, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 8, kind="hip", overhang=1)
    for x in (29, 30, 31):
        for dy in (1, 2, 3):
            k.put(x, f + dy, z1, "minecraft:air")
    k.put(30, f + 4, z1, "kerb_chisel")
    for x in (28, 32):
        panji(k, x, f + 4, z1 + 1, "south", "black")
    # the wall of contracts (west wall, facing east): four boards with candles under them
    for i, z in enumerate((15, 21, 27, 33)):
        K.notice_board(k, x0 + 1, z, "east", w=5)
        lilin(k, x0 + 2, f + 1, z + 2)
    k.jangkar(x0 + 3.5, f + 1, 23.5, "kota_tema/kontrak", yaw=90.0)
    k.titik("kerja:meja_tulis", x0 + 2, f + 1, 23, "west")
    # three curtained counters along the north wall
    for i, cx in enumerate((18, 30, 42)):
        K.counter(k, cx - 2, cx + 2, z0 + 4, f, "south")
        for z in range(z0 + 1, z0 + 5):
            for dy in range(1, 6):
                k.put(cx - 3, f + dy, z, "minecraft:black_wool" if dy % 2 else "minecraft:red_wool")
                k.put(cx + 3, f + dy, z, "minecraft:black_wool" if dy % 2 else "minecraft:red_wool")
        for x in range(cx - 2, cx + 3):
            k.put(x, f + 5, z0 + 4, "minecraft:red_wool")
        k.titik("jaga", cx, f + 1, z0 + 2, "south", {"loket": i})
        lentera_jiwa(k, cx, f + 4, z0 + 3)
    # the secret room behind the middle counter (a gap in the north wall's curtain into the back room)
    for x in range(26, 35):
        for z in range(z0 - 5, z0):
            edge = x in (26, 34) or z == z0 - 5
            k.put(x, f, z, "floor_alt")
            for dy in range(1, 5):
                k.put(x, f + dy, z, "bata_hitam" if edge else "minecraft:air")
            k.put(x, f + 5, z, "kerb")
    for dy in (1, 2):
        k.put(30, f + dy, z0, "minecraft:air")
    k.put(30, f + 3, z0, "minecraft:red_wool")
    K.table(k, 30, f + 1, z0 - 3)
    kursi(k, 30, f + 1, z0 - 4, "south", titik=False)
    kursi(k, 28, f + 1, z0 - 3, "east")
    k.put(32, f + 1, z0 - 4, "minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"})
    peti(k, 33, f + 1, z0 - 4, "kultivator:tema/iblis/kontrak", "west")
    lentera_jiwa(k, 30, f + 4, z0 - 2)
    # drinking tables, racks of twin knives
    for (tx, tz) in ((16, 22), (24, 30), (36, 22), (44, 30), (16, 35), (38, 35)):
        K.furnish_dining(k, tx, tz, f, n=3)
    for x in (x1 - 1,):
        for z in (17, 25, 33):
            rak_senjata(k, x, f + 1, z, "west", n=3)
    for (x, z) in ((18, 19), (30, 25), (42, 19), (18, 33), (42, 33)):
        K.lit(k, x, f + 7, z, chain=2)
    ruang(k, x0 + 1, z0 + 5, x1 - 1, z1 - 1, f, "kontrak", pulau=("meja_kursi", "meja_judi"), jarak=6, isi=0.45)
    k.kotak("bangunan", x0, f, z0 - 5, x1, f + 16, z1, "south", nama="sarang_kontrak")
    k.titik("pintu", 30, f + 1, z1 + 1, "south")


def gudang_minyak(k):
    """Lot (1,0): the warehouse of Shadow Oil (Bai She's smuggling, quest chapter 3): stacked barrels and crates, a loading
    bay, the clerk's corner; the six oil barrels of the quest are TongMinyakEntity at GUDANG_TONG round the anchor
    kota_tema/gudang_minyak (empty floor there); Hong Pao waits by the bay."""
    x0, z0, x1, z1 = 58, 6, 90, 40
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "wood")
    k.walls(x0, f + 1, z0, x1, f + 7, z1, role="wood_alt", pillar="minecraft:stripped_dark_oak_log", windows=0,
            interior_air=False)
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 5, z1 - 1)
    K.beam_band(k, x0, z0, x1, z1, f + 7, role="trim")
    k.fill(x0 + 1, f + 7, z0 + 1, x1 - 1, f + 7, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 8, kind="gable", along="z", overhang=1, top=None)
    # the loading bay (south, 5 wide, open), a ramp of slabs
    for x in range(72, 77):
        for dy in range(1, 5):
            k.put(x, f + dy, z1, "minecraft:air")
        k.slab(x, f + 1, z1 + 1, "minecraft:dark_oak_slab", "bottom")
    k.put(74, f + 5, z1, "kerb_chisel")
    # rows of barrels and crates (leave the quest spots round the anchor free)
    cx, cz = 74, 22
    free = {(cx + dx, cz + dz) for (dx, dz) in GUDANG_TONG} | {(cx, cz)}
    for x in range(x0 + 2, x1 - 1, 3):
        for z in range(z0 + 2, z1 - 3):
            if abs(x - cx) <= 6 and abs(z - cz) <= 6:
                continue
            if (z - z0) % 5 in (0, 1):
                hh = 1 + (x * 3 + z) % 3
                for dy in range(hh):
                    k.put(x, f + 1 + dy, z, "minecraft:barrel", {"facing": ("up", "north", "east")[(x + z + dy) % 3], "open": "false"})
    for (x, z) in free:
        k.put(x, f, z, "kerb_chisel")
    k.jangkar(cx + 0.5, f + 1, cz + 0.5, "kota_tema/gudang_minyak", yaw=0.0, tong=len(GUDANG_TONG))
    # the clerk's corner
    K.table(k, x0 + 2, f + 1, z1 - 3)
    kursi(k, x0 + 2, f + 1, z1 - 2, "north", titik=False)
    k.titik("kerja:timbangan", x0 + 3, f + 1, z1 - 3, "west")
    k.put(x0 + 1, f + 1, z1 - 5, "minecraft:lectern", {"facing": "east", "has_book": "true", "powered": "false"})
    tokoh(k, 70, z1 + 2, "hong_pao", hadap="north")
    for (x, z) in ((66, 14), (82, 14), (66, 30), (82, 30), (74, 22)):
        K.lit(k, x, f + 7, z, chain=2)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "iblis_gudang", isi=0.5, rug=False)
    k.kotak("bangunan", x0, f, z0, x1, f + 18, z1, "south", nama="gudang_minyak")
    k.titik("pintu", 74, f + 1, z1 + 2, "south")
    k.titik("jaga", 76, f + 1, z1 + 2, "south")


def rumah_iblis_blok(k, x0, z0, x1, z1, varian):
    """A block of demonic cultivators' houses behind a low black wall: padlocked stone houses with meditation corners,
    a yard with a dead tree, a training stone and soul lanterns."""
    if varian == 0:
        a = rumah_batu(k, x0 + 2, z0 + 2, 13, 10, face="south", kamar=2, varian=0, nama="rumah_iblis")
        b = rumah_batu(k, x0 + 20, z0 + 2, 15, 11, face="south", storeys=2, kamar=3, varian=1, nama="rumah_iblis", rich=True)
        c = rumah_batu(k, x0 + 2, z0 + 22, 12, 12, face="east", kamar=2, varian=2, nama="rumah_iblis")
        yard = (x0 + 17, z0 + 18, x1 - 2, z1 - 2)
    else:
        a = rumah_batu(k, x0 + 2, z0 + 2, 15, 12, face="south", storeys=2, kamar=3, varian=3, nama="rumah_iblis", rich=True)
        b = rumah_batu(k, x0 + 24, z0 + 2, 12, 10, face="south", kamar=2, varian=4, nama="rumah_iblis")
        c = rumah_batu(k, x0 + 22, z0 + 21, 14, 12, face="west", kamar=2, varian=5, nama="rumah_iblis")
        yard = (x0 + 2, z0 + 18, x0 + 18, z1 - 2)
    for door in (a, b, c):
        K.paving(k, door[0] - 1, door[1] - 1, door[0] + 1, door[1] + 1, LY, role="kerb_chisel", alt="paving_alt", alt_chance=0.3)
    yx0, yz0, yx1, yz1 = yard
    for x in range(yx0, yx1 + 1):
        for z in range(yz0, yz1 + 1):
            k.put(x, LY, z, "minecraft:soul_soil" if (x + z) % 3 == 0 else "minecraft:coarse_dirt")
    tx, tz = (yx0 + yx1) // 2, (yz0 + yz1) // 2
    tiang(k, tx, LY + 1, LY + 5, tz, role="minecraft:dark_oak_log")
    k.put(tx + 1, LY + 4, tz, "minecraft:dark_oak_log", {"axis": "x"})
    k.put(tx, LY + 5, tz - 1, "minecraft:dark_oak_log", {"axis": "z"})
    k.put(tx - 3, LY + 1, tz + 2, "kerb_chisel")
    k.put(tx - 3, LY + 2, tz + 2, "minecraft:polished_blackstone_wall")
    k.titik("latihan", tx - 3, LY + 1, tz + 3, "north")
    k.titik("meditasi", tx + 2, LY + 1, tz + 2, "west")
    for (x, z) in ((yx0, yz0), (yx1, yz1)):
        obor_jiwa(k, x, z)
    # the low black wall round the block with a gate on the street side
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if not (x0 + 16 <= x <= x0 + 19 and z == z1):
                k.put(x, LY + 1, z, "kerb_wall")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if not (z0 + 16 <= z <= z0 + 19 and x == (x1 if varian == 0 else x0)):
                k.put(x, LY + 1, z, "kerb_wall")


def d_sarang_kontrak():
    D = "sarang_kontrak"
    k = kanvas(D, h=40)
    tanah_kota(k, D)
    K.paving(k, 3, 45, 92, 50, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 45, 51, 50, 92, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 53, 3, 56, 44, LY, role="path", alt="path_alt", alt_chance=0.2)
    sarang_kontrak_aula(k)
    gudang_minyak(k)
    rumah_iblis_blok(k, 4, 53, 43, 91, 0)
    rumah_iblis_blok(k, 52, 53, 91, 91, 1)
    lampu_jalan(k, [(54, 6), (54, 30), (44, 52), (51, 70), (44, 90)])
    names = {(0, 0): "sarang_kontrak", (1, 0): "gudang_minyak", (0, 1): "rumah_iblis_barat", (1, 1): "rumah_iblis_timur"}
    simpan_distrik(k, D, names, seams=(((0, 0), (1, 0)),))


# ===================================================================================================== kumuh_iblis
def lorong_kumuh(k, pts, lebar=2):
    """A crooked slum alley of trodden dirt, coarse dirt and broken blackstone between the shacks."""
    for (a, b) in zip(pts, pts[1:]):
        (x0, z0), (x1, z1) = a, b
        n = max(abs(x1 - x0), abs(z1 - z0))
        for i in range(n + 1):
            x = x0 + (x1 - x0) * i // max(1, n)
            z = z0 + (z1 - z0) * i // max(1, n)
            for dx in range(lebar):
                for dz in range(lebar):
                    k.put(x + dx, LY, z + dz, ("minecraft:coarse_dirt", "minecraft:packed_mud", "minecraft:cracked_polished_blackstone_bricks",
                                                "minecraft:coarse_dirt", "minecraft:dirt_path")[(x * 3 + z * 7) % 5])


def tali_jemuran(k, x0, x1, z, y):
    """A washing line of chains with rags (carpets cannot hang: wool blocks on a chain line)."""
    for x in range(x0, x1 + 1):
        if x in (x0, x1):
            tiang(k, x, LY + 1, y, z, role="minecraft:dark_oak_fence")
        else:
            k.put(x, y, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
            if x % 3 == 0:
                k.put(x, y - 1, z, ("minecraft:brown_wool", "minecraft:gray_wool", "minecraft:black_wool")[x % 3])


def kumuh_barat_laut(k):
    """Lot (0,0): shacks of the indebted followers along a crooked alley, a washing line, the tainted well (water
    with crimson roots and a broken roof), children's corners."""
    for i, (x, z, w, d, face) in enumerate(((5, 5, 7, 6, "south"), (15, 4, 6, 6, "south"), (24, 5, 8, 6, "south"),
                                            (35, 5, 7, 7, "south"), (5, 17, 6, 7, "east"), (34, 20, 8, 6, "west"),
                                            (6, 30, 7, 6, "north"), (17, 33, 6, 6, "north"), (31, 33, 9, 7, "north"))):
        gubuk(k, x, z, w, d, face=face, kind=i)
    lorong_kumuh(k, [(4, 13), (14, 14), (22, 12), (32, 15), (42, 13)])
    lorong_kumuh(k, [(13, 14), (14, 24), (22, 28), (30, 26), (42, 29)])
    # the tainted well
    K.well(k, 22, 21)
    for (x, z) in ((20, 19), (24, 23), (20, 23), (24, 19)):
        k.put(x, LY, z, "minecraft:soul_soil")
        k.put(x, LY + 1, z, "minecraft:crimson_roots")
    k.titik("obrol", 22, LY + 1, 24, "north")
    tali_jemuran(k, 6, 13, 27, LY + 3)
    tali_jemuran(k, 27, 33, 17, LY + 3)
    for (x, z) in ((10, 25), (30, 29), (40, 18)):
        k.titik("main", x, LY + 1, z, "north", {"anak_liar": "1"})
    for i, (x, z) in enumerate(((3, 42), (12, 43), (27, 43), (41, 4))):
        tanah_jiwa(k, x, z, n=i)
    lampu_jalan(k, [(14, 15), (42, 30)])


def dapur_umum(k):
    """Lot (1,0): the public kitchen - an open shed with two big cauldrons, a campfire (lit: this is a kitchen), a long
    table where the poor eat, sacks of grain, the bread rack the wild children steal from (quest tema_iblis_anak_liar)."""
    x0, z0, x1, z1 = 56, 6, 88, 26
    k.fill(x0, LY, z0, x1, LY, z1, "minecraft:packed_mud")
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (72, z0), (72, z1), (x0, 16), (x1, 16)):
        tiang(k, x, LY + 1, LY + 5, z, role="minecraft:dark_oak_log")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            hi = z < 16
            k.slab(x, LY + 6 if hi else LY + 5, z, "minecraft:dark_oak_slab" if (x + z) % 4 else "minecraft:crimson_slab", "bottom")
    for x in range(x0, x1 + 1):
        k.put(x, LY + 1, z0, "minecraft:dark_oak_fence") if x % 2 else None
    # two big cauldrons on a stone hearth, the campfire between them
    for (cx, cz) in ((62, 10), (68, 10)):
        for dx in (-1, 0, 1):
            k.put(cx + dx, LY + 1, cz + 1, "kerb")
        k.put(cx, LY + 1, cz, "minecraft:cauldron")
        k.put(cx, LY + 2, cz, "minecraft:cauldron")
    k.put(65, LY + 1, 10, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    k.titik("kerja:kuali", 62, LY + 1, 12, "north")
    k.titik("kerja:kuali", 68, LY + 1, 12, "north")
    # the bread rack and sacks
    for x in range(76, 87):
        K.shelf(k, x, LY + 2, z0 + 1, "south")
        k.put(x, LY + 1, z0 + 1, "minecraft:hay_block", {"axis": "x"}) if x % 2 else k.put(x, LY + 1, z0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    k.titik("jaga", 81, LY + 1, z0 + 3, "north", {"dapur": "1"})
    # the long table
    for x in range(60, 86):
        K.table(k, x, LY + 1, 20, "minecraft:dark_oak_pressure_plate" if x % 3 else False)
    for x in range(60, 86, 2):
        kursi(k, x, LY + 1, 19, "south", titik=x % 4 == 0)
        kursi(k, x, LY + 1, 21, "north", titik=x % 4 == 2)
    k.titik("meja_makan", 72, LY + 1, 20, "south")
    for (x, z) in ((64, 16), (80, 16)):
        K.lit(k, x, LY + 5, z, chain=1)
    # shacks behind the kitchen
    for i, (x, z, w, d, face) in enumerate(((56, 31, 7, 6, "north"), (66, 32, 6, 6, "north"), (75, 31, 7, 7, "north"),
                                            (85, 30, 6, 7, "west"))):
        gubuk(k, x, z, w, d, face=face, kind=i + 3)
    lorong_kumuh(k, [(54, 28), (70, 29), (91, 28)])
    k.titik("main", 74, LY + 1, 29, "north", {"anak_liar": "1"})
    lampu_jalan(k, [(55, 28), (90, 41)])


def kumuh_barat_daya(k):
    """Lot (0,1): the deepest slum by the wall - shacks leaning on each other, a rag market corner, a shrine of the
    poor (candles, a cracked skull), more alleys."""
    for i, (x, z, w, d, face) in enumerate(((5, 54, 6, 6, "south"), (14, 53, 7, 6, "south"), (25, 54, 6, 7, "south"),
                                            (35, 53, 7, 6, "south"), (5, 66, 7, 6, "east"), (33, 65, 8, 6, "west"),
                                            (5, 79, 6, 7, "north"), (15, 81, 7, 6, "north"), (26, 80, 6, 7, "north"),
                                            (35, 79, 7, 7, "north"))):
        gubuk(k, x, z, w, d, face=face, kind=i + 1)
    lorong_kumuh(k, [(4, 62), (16, 63), (24, 61), (33, 63), (42, 62)])
    lorong_kumuh(k, [(12, 63), (14, 72), (24, 76), (32, 74), (42, 76)])
    # the rag market corner (two poor stalls) and the shrine of the poor
    for (x, z, f) in ((18, 70, "south"), (25, 70, "south")):
        sx, sz = K.stall(k, x, z, f, w=3, cloth=("minecraft:brown_wool", "minecraft:gray_wool"))
        k.titik("lapak", sx, LY + 1, sz, f, {"jenis": "rongsok"})
    k.put(40, LY + 1, 70, "kerb_chisel")
    k.put(40, LY + 2, 70, "minecraft:skeleton_skull", {"rotation": "12"})
    for (x, z) in ((40, 69), (40, 71), (39, 70)):
        lilin(k, x, LY + 1, z)
    k.titik("dupa", 38, LY + 1, 70, "east")
    tali_jemuran(k, 6, 12, 76, LY + 3)
    for (x, z) in ((10, 75), (30, 78)):
        k.titik("main", x, LY + 1, z, "north", {"anak_liar": "1"})
    lampu_jalan(k, [(13, 63), (42, 77)])


def bengkel_kumuh(k):
    """Lot (1,1): the hired craftsmen's workshop (anchor kotabesar/tukang, 38.R repairs) - an anvil, a grindstone,
    timber and stone stacks, a lean-to; and the houses of small traders deep in debt."""
    x0, z0, x1, z1 = 56, 55, 76, 70
    k.fill(x0, LY, z0, x1, LY, z1, "minecraft:packed_mud")
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (66, z0), (66, z1)):
        tiang(k, x, LY + 1, LY + 4, z, role="minecraft:dark_oak_log")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            k.slab(x, LY + 5, z, "minecraft:dark_oak_slab", "bottom")
    k.put(60, LY + 1, 59, "minecraft:anvil", {"facing": "east"})
    k.put(63, LY + 1, 59, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    k.put(66, LY + 1, 59, "minecraft:smithing_table")
    k.put(69, LY + 1, 59, "minecraft:stonecutter", {"facing": "north"})
    k.titik("kerja:palu", 60, LY + 1, 60, "north")
    k.titik("kerja:gergaji", 69, LY + 1, 60, "north")
    for x in range(58, 75, 2):
        for dy in (1, 2):
            k.put(x, LY + dy, 68, "minecraft:stripped_dark_oak_log" if x % 4 else "minecraft:cobblestone",
                  {"axis": "x"} if x % 4 else None)
    k.jangkar(66.5, LY + 1, 63.5, "kotabesar/tukang", yaw=180.0)
    for i, (x, z, w, d, face) in enumerate(((80, 55, 10, 8, "west"), (56, 76, 9, 8, "north"), (69, 77, 9, 8, "north"),
                                            (81, 75, 9, 9, "north"))):
        if i == 0:
            rumah_batu(k, x, z, w, d, face=face, kamar=1, varian=i, nama="rumah_utang")
        else:
            gubuk(k, x, z, w, d, face=face, kind=i + 6)
    lorong_kumuh(k, [(53, 73), (66, 74), (78, 72), (91, 74)])
    k.titik("main", 75, LY + 1, 73, "north", {"anak_liar": "1"})
    lampu_jalan(k, [(54, 54), (90, 90), (54, 90)])


def d_kumuh_iblis():
    D = "kumuh_iblis"
    k = kanvas(D, h=30)
    tanah_kota(k, D)
    for x in range(3, 93):
        for z in range(3, 93):
            if (x * 7 + z * 13) % 11 == 0:
                k.put(x, LY, z, "minecraft:coarse_dirt")
    silang(k)
    kumuh_barat_laut(k)
    dapur_umum(k)
    kumuh_barat_daya(k)
    bengkel_kumuh(k)
    names = {(0, 0): "kumuh_barat_laut", (1, 0): "dapur_umum", (0, 1): "kumuh_barat_daya", (1, 1): "bengkel_kumuh"}
    simpan_distrik(k, D, names)


# ===================================================================================================== kedai_racun (ZP)
def kedai_cawan_racun(k):
    """Lots (0,0)+(1,0): the Inn of the Poison Cup - two storeys: below twelve tables, a long bar with bottles and kegs,
    a music stage, the kitchen; above eight rooms for rent (penginapan + rumah_makan of 38KS at x1.5 prices)."""
    x0, z0, x1, z1 = 8, 6, 88, 38
    f = LY
    k.fill(x0, f, z0, x1, f, z1, "wood")
    k.walls(x0, f + 1, z0, x1, f + 6, z1, role="bata_hitam", pillar="bata_merah", windows=0, interior_air=True)
    k.walls(x0, f + 7, z0, x1, f + 11, z1, role="plaster", pillar="bata_merah", windows=0, interior_air=True)
    jendela_besi(k, x0, z0, x1, z1, f + 2, f + 3, every=6, skip=("south",))
    jendela_besi(k, x0, z0, x1, z1, f + 8, f + 9, every=5)
    K.beam_band(k, x0, z0, x1, z1, f + 6, role="trim")
    K.beam_band(k, x0, z0, x1, z1, f + 11, role="trim")
    K.upper_floor(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f + 6, hole=(x0 + 2, z0 + 1, x0 + 3, z0 + 8))
    k.fill(x0 + 1, f + 11, z0 + 1, x1 - 1, f + 11, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 12, kind="hip", overhang=2, top=None)
    for i in range(6):
        for x in (x0 + 2, x0 + 3):
            k.stairs(x, f + 1 + i, z0 + 7 - i, "wood_stairs", "north")
    # the south front: a 5-wide door under an awning of red cloth, lanterns, the sign of the cup
    for x in range(46, 51):
        for dy in (1, 2, 3):
            k.put(x, f + dy, z1, "minecraft:air")
    for x in range(44, 53):
        for z in (z1 + 1, z1 + 2):
            k.put(x, f + 5, z, "minecraft:red_wool" if (x + z) % 2 else "minecraft:black_wool")
    k.put(48, f + 5, z1, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    for x in (44, 52):
        tiang(k, x, f + 1, f + 4, z1 + 2)
        lentera_jiwa(k, x, f + 4, z1 + 3)
    # ground floor: the bar (north), twelve tables, the stage (east), the kitchen (west)
    K.counter(k, 30, 66, z0 + 5, f, "south")
    for x in range(30, 67):
        k.put(x, f + 1, z0 + 1, "minecraft:barrel", {"facing": "south", "open": "false"}) if x % 2 else \
            k.put(x, f + 1, z0 + 1, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
        K.shelf(k, x, f + 3, z0 + 1, "south")
    for x in range(32, 66, 4):
        k.titik("kursi", x, f + 1, z0 + 6, "north", {"bar": "1"})
        k.stairs(x, f + 1, z0 + 6, "minecraft:dark_oak_stairs", "south")
    k.titik("kerja:teko", 48, f + 1, z0 + 3, "south")
    tables = [(18 + 10 * i, 16 + 9 * j) for i in range(6) for j in range(2)]
    for (tx, tz) in tables:
        K.furnish_dining(k, tx, tz, f, n=4)
        k.put(tx, f + 2, tz, "minecraft:red_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    # the stage
    for x in range(78, 87):
        for z in range(14, 30):
            k.put(x, f + 1, z, "kerb")
    for z in range(14, 30):
        k.stairs(77, f + 1, z, "kerb_stairs", "west")
    tirai(k, 14, 29, 87, f + 2, f + 5, along="z")
    k.titik("panggung", 78, f + 2, 14, "west", {"dx": 8, "dy": 3, "dz": 15})
    for z in (18, 22, 26):
        k.put(84, f + 2, z, "minecraft:note_block", {"instrument": ("harp", "flute", "bell")[z % 3], "note": "8", "powered": "false"})
        k.titik("kerja:kipas", 82, f + 2, z, "west")
    # the kitchen corner (west): unlit stoves, kegs
    K.furnish_kitchen(k, x0 + 5, z0 + 1, x0 + 12, f, z0 + 1, "south")
    K.crates(k, x0 + 1, z1 - 4, x0 + 4, z1 - 1, f + 1, h=2)
    k.jangkar(48.5, f + 1, z0 + 8.5, "kotabesar/layanan", yaw=180.0, layanan="rumah_makan", peran="KOKI", harga="1.5")
    k.jangkar(14.5, f + 1, z1 - 3.5, "kotabesar/layanan", yaw=90.0, layanan="penginapan", peran="PEMILIK_PENGINAPAN", harga="1.5")
    for (x, z) in ((24, 20), (44, 20), (64, 20), (24, 30), (44, 30), (64, 30), (82, 22)):
        K.lit(k, x, f + 6, z, chain=1)
    # upper floor: a corridor along the south, eight rooms north of it
    for x in range(x0 + 1, x1):
        for dy in range(1, 5):
            k.put(x, f + 6 + dy, 27, "minecraft:air" if ((x - x0) % 10 == 5 and dy <= 2) else "wood_alt")
    for r in range(8):
        rx0 = x0 + 1 + r * 10
        if r > 0:
            for z in range(z0 + 1, 27):
                for dy in range(1, 5):
                    k.put(rx0 - 1, f + 6 + dy, z, "wood_alt")
        ranjang(k, rx0 + 2, f + 7, z0 + 1, "north", warna="red" if r % 2 else "black")
        k.put(rx0 + 5, f + 7, z0 + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        K.table(k, rx0 + 6, f + 7, z0 + 4)
        kursi(k, rx0 + 6, f + 7, z0 + 5, "north", titik=False)
        K.lit(k, rx0 + 4, f + 11, z0 + 8, chain=1)
        K.door(k, rx0 + 4, f + 7, 27, "south")
        ruang(k, rx0, z0 + 1, rx0 + 8, 26, f + 6, "iblis_inap", isi=0.6, seed=r)
    for x in range(x0 + 5, x1 - 4, 12):
        K.lit(k, x, f + 11, 32, chain=1)
    ruang(k, x0 + 1, z0 + 7, 76, z1 - 1, f, "iblis_kedai", isi=0.4, rug=False)
    ruang(k, x0 + 4, 28, x1 - 1, z1 - 1, f + 6, "iblis_kedai", pulau=("meja_kursi", "meja_judi"), jarak=8, isi=0.4, seed=1)
    k.kotak("bangunan", x0, f, z0, x1, f + 24, z1, "south", nama="kedai_cawan_racun")
    k.titik("pintu", 48, f + 1, z1 + 1, "south")
    for x in (45, 51):
        k.titik("jaga", x, f + 1, z1 + 1, "south")


def kedai_teh_asap(k):
    """Lot (0,1): the Smoke Tea House - low tables on a raised floor, braziers of soul lanterns, screens of red lattice,
    the teapot counter; the tea anchor kota/teh (rumours are 38ST's tea house)."""
    x0, z0, x1, z1 = 6, 55, 40, 85
    f = LY + 1
    k.fill(x0, LY + 1, z0, x1, LY + 1, z1, "wood")
    tangga_cincin(k, x0, z0, x1, z1, LY + 1, role="wood_stairs")
    k.walls(x0, f + 1, z0, x1, f + 5, z1, role="plaster", pillar="minecraft:stripped_dark_oak_log", windows=0, interior_air=True)
    for x in range(x0 + 2, x1 - 1, 3):
        for z, side in ((z0, "north"), (z1, "south")):
            if not 21 <= x <= 25:
                K.lattice(k, x, f + 2, z, side)
                K.lattice(k, x, f + 3, z, side)
    K.beam_band(k, x0, z0, x1, z1, f + 5, role="trim")
    k.fill(x0 + 1, f + 5, z0 + 1, x1 - 1, f + 5, z1 - 1, "wood")
    K.roof_over(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, f + 6, kind="hip", overhang=2, top=None)
    for x in (22, 23, 24):
        for dy in (1, 2, 3):
            k.put(x, f + dy, z1, "minecraft:air")
    for (tx, tz) in ((12, 62), (20, 62), (30, 62), (12, 72), (30, 72), (12, 80), (20, 80), (30, 80)):
        K.low_table(k, tx, f + 1, tz)
        for (dx, dz, h) in ((-1, 0, "east"), (1, 0, "west"), (0, 1, "north")):
            k.put(tx + dx, f + 1, tz + dz, "minecraft:red_carpet")
            k.titik("kursi_lantai", tx + dx, f + 1, tz + dz, h)
        k.put(tx, f + 2, tz, "minecraft:flower_pot")
    K.counter_z(k, 36, 65, 77, f, "west")
    k.titik("kerja:teko", 37, f + 1, 71, "west")
    k.jangkar(37.5, f + 1, 69.5, "kota/teh", yaw=270.0, nama="teh_asap")
    for (x, z) in ((16, 67), (26, 67), (16, 77), (26, 77)):
        K.lit(k, x, f + 5, z, chain=1)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "teh_asap", isi=0.5, rug=False)
    k.kotak("bangunan", x0, LY, z0, x1, f + 14, z1, "south", nama="kedai_teh_asap")
    k.titik("pintu", 23, f + 1, z1 + 2, "south")


def stasiun_formasi_hitam(k):
    """Lot (1,1): the Black Formation Station (kotabesar/cakram of 38KS): a raised round platform of blackstone with a
    rune ring of crying obsidian and gilded blackstone, eight basalt pillars with soul lanterns and chains."""
    cx, cz = 72, 72
    for x in range(cx - 13, cx + 14):
        for z in range(cz - 13, cz + 14):
            r = math.hypot(x - cx, z - cz)
            if r <= 12.5:
                k.put(x, LY + 1, z, "kerb" if r > 11.5 else "floor")
            if r <= 10.5:
                k.put(x, LY + 2, z, "floor_alt" if r > 9.5 else "floor")
            if 6.5 <= r <= 7.5:
                k.put(x, LY + 2, z, "minecraft:crying_obsidian" if (x + z) % 3 == 0 else "minecraft:gilded_blackstone")
            if r <= 2.0:
                k.put(x, LY + 2, z, "minecraft:chiseled_polished_blackstone")
    for (dx, dz, f) in ((0, -13, "south"), (0, 13, "north"), (-13, 0, "east"), (13, 0, "west")):
        for w in (-1, 0, 1):
            k.stairs(cx + dx + (w if dz else 0), LY + 1, cz + dz + (w if dx else 0), "kerb_stairs", f)
    for i in range(8):
        a = i * math.pi / 4.0
        px, pz = int(round(cx + 9.5 * math.cos(a))), int(round(cz + 9.5 * math.sin(a)))
        tiang(k, px, LY + 3, LY + 8, pz)
        k.put(px, LY + 9, pz, "bata_merah")
        rantai(k, px, LY + 10, LY + 10, pz)
        lentera_jiwa(k, px, LY + 11, pz, hanging=False)
    k.jangkar(cx + 0.5, LY + 3, cz + 0.5, "kotabesar/cakram", yaw=0.0, protect=12)
    k.titik("jaga", cx, LY + 3, cz + 9, "north")
    k.titik("pandang", cx, LY + 4, cz + 16, "north", {"nama": "stasiun_formasi_hitam"})


def d_kedai_racun():
    D = "kedai_racun"
    k = kanvas(D, h=42)
    tanah_kota(k, D)
    K.paving(k, 3, 45, 92, 50, LY, role="path", alt="path_alt", alt_chance=0.2)
    K.paving(k, 45, 51, 50, 92, LY, role="path", alt="path_alt", alt_chance=0.2)
    kedai_cawan_racun(k)
    kedai_teh_asap(k)
    stasiun_formasi_hitam(k)
    for (i, j) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        k.kotak("zona_perjanjian", 48 * i, LY, 48 * j, 48 * i + 47, LY + 16, 48 * j + 47, "south", distrik=D)
    lampu_jalan(k, [(44, 52), (51, 64), (44, 76), (51, 88), (6, 42), (89, 42)])
    names = {(0, 0): "kedai_barat", (1, 0): "kedai_timur", (0, 1): "kedai_teh_asap", (1, 1): "stasiun_formasi_hitam"}
    simpan_distrik(k, D, names, seams=(((0, 0), (1, 0)),))


# @@BAGIAN_DISTRIK@@
# ===================================================================================================== JSON and main
def tulis_json():
    """Pools (base "hitam" + styles "merah", "rawa", 38TK inti's tempat_tema_lib writers), the nine district structures
    and, last, the style processors kota_tema/iblis/{gaya,utuh}_{hitam,merah,rawa} for every block state of this run."""
    for pool, (templates, utuh) in sorted(POOLS.items()):
        ttl.tulis_pool_tema("iblis", pool, templates, utuh=utuh)
    for d in ORDER:
        if not LOTS[d]:
            continue
        lots = [ttl.lot_luar(l["dx"], l["dz"], "kota_tema/iblis/" + l["pool"], l["putar"]) for l in LOTS[d]]
        ttl.tulis_distrik("iblis", d, lots)
    ttl.tulis_processor_tema("iblis")


def main(argv):
    build_walls()
    for _name, fn in BUILDERS:
        fn()
    wall_lots()
    tulis_json()
    tl.cek_semua()
    total = sum(os.path.getsize(p) for p, _ in structgen._SAVED)
    n = sum(len(v) for v in LOTS.values())
    print("kota_tema/iblis: %d templates, %d lots placed, %d pools, gzip %d bytes" % (len(structgen._SAVED), n, len(POOLS), total))


BUILDERS = [
    ("gerbang_tengkorak", d_gerbang_tengkorak),
    ("arena_darah", d_arena_darah),
    ("istana_darah", d_istana_darah),
    ("menara_terlarang", d_menara_terlarang),
    ("penjara_tawanan", d_penjara_tawanan),
    ("pasar_gelap", d_pasar_gelap),
    ("sarang_kontrak", d_sarang_kontrak),
    ("kumuh_iblis", d_kumuh_iblis),
    ("kedai_racun", d_kedai_racun),
]


if __name__ == "__main__":
    main(sys.argv[1:])
