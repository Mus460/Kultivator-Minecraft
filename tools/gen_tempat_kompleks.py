"""Kompleks Sekte NPC world generation (DESIGN 38.C.3, module 38DS): the compound behind every NPC sect gate.

Writes the NBT lots (data/kultivator/structure/kompleks_sekte/**), one processor list per alignment, the template pools
(kompleks_sekte/<aliran>/<lot>), the kultivator:tata_letak structures kompleks_sekte/<aliran>_<distrik>, the structure
and biome tags and, in the built-in pack resourcepacks/dunia_kompleks, the three structure sets on
kultivator:rencana_tempat (kompleks_sekte/halaman|dalam|puncak, salt 380201-380203). Run:

    python tools/gen_tempat_kompleks.py            (then python tools/cek_tempat.py)

Frame (RencanaKompleks.java): the compound turns with its gate; in canvas coordinates north (-z) climbs the mountain and
south (+z) faces the gate. Districts (3 x 3 lots of 48, the summit of a middle band 3 x 2):

    band kecil  : halaman_kecil                       (one court: main hall, training ground, summit hall)
    band sedang : halaman  +  puncak_sedang (+18)
    band besar  : halaman  +  dalam (+10)  +  puncak (+18)

Every district is drawn on one canvas (tempat_ds_kit.Kanvas) and cut into lots; identical lots of different districts
(the summit rows of the middle and great bands, the courts of the small and middle bands) are saved once. The climb
between two districts is a separate stair piece (tangga_<naik>) and three terrace-wall pieces (tebing_<naik>_*) listed
after the lots with dy = -naik, so they stand on the lower floor and end on the upper one. The three alignments share
the NBT: a processor per alignment swaps the base blocks (white jade and blue tiles) for neutral wood and gold or
demonic blackstone and blood red (38.C.3.2); demonic halls and pools that the other two have not are their own lots.
Only world generation in NEW chunks places these templates (DESIGN 38.0 rule 1). Deterministic: the same bytes each run.
"""
import hashlib
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import tempat_ds_kit as K  # noqa: E402
from tempat_ds_kit import LY, FACE, OPP  # noqa: E402

F = "kompleks_sekte"
NS = "kultivator:"
PACK = "dunia_kompleks"
SALT = {"halaman": 380201, "dalam": 380202, "puncak": 380203}
ALIRAN = ("lurus", "netral", "iblis")
MAKS_TITIK_LOT = 64          # 38.C.10: <= 64 points per compound lot

# ===================================================================================================== base palette
# The base look is the righteous one (white jade, pale birch, blue tile); every block of a role is unique so the
# alignment processors can swap it. Literal ids the kit uses (dark oak plates, trapdoors) are swapped too.
BASE = dict(K.PAL)
BASE.update({
    "floor": "minecraft:smooth_quartz", "floor_alt": "minecraft:quartz_bricks", "found": "minecraft:stone_bricks",
    "paving": "minecraft:smooth_quartz", "paving_alt": "minecraft:quartz_bricks", "paving_slab": "minecraft:smooth_quartz_slab",
    "paving_stairs": "minecraft:smooth_quartz_stairs", "path": "minecraft:polished_diorite", "path_alt": "minecraft:diorite",
    "kerb": "minecraft:stone_bricks", "kerb_alt": "minecraft:mossy_stone_bricks", "kerb_wall": "minecraft:stone_brick_wall",
    "kerb_stairs": "minecraft:stone_brick_stairs", "kerb_slab": "minecraft:stone_brick_slab",
    "kerb_chisel": "minecraft:chiseled_stone_bricks", "gutter": "minecraft:smooth_stone",
    "plaster": "minecraft:calcite", "plaster_alt": "minecraft:white_concrete", "beam": "minecraft:stripped_birch_log",
    "red": "minecraft:quartz_pillar", "red_wall": "minecraft:diorite_wall", "pillar": "minecraft:quartz_pillar",
    "wood": "minecraft:birch_planks", "wood_alt": "minecraft:pale_oak_planks", "wood_stairs": "minecraft:birch_stairs",
    "wood_slab": "minecraft:birch_slab", "wood_trapdoor": "minecraft:birch_trapdoor", "wood_fence": "minecraft:birch_fence",
    "wood_gate": "minecraft:birch_fence_gate", "door": "minecraft:birch_door", "shelf": "minecraft:birch_shelf",
    "log": "minecraft:birch_log", "rail": "minecraft:birch_fence", "fence": "minecraft:birch_fence",
    "window": "minecraft:birch_trapdoor", "light_wood": "minecraft:pale_oak_planks", "light_slab": "minecraft:pale_oak_slab",
    "light_stairs": "minecraft:pale_oak_stairs", "lamp": "minecraft:ochre_froglight", "glow": "minecraft:lantern",
    "banner": "light_blue", "banner_alt": "white", "cloth": "minecraft:light_blue_wool", "cloth_alt": "minecraft:white_wool",
    "ground": "minecraft:grass_block", "ground_alt": "minecraft:podzol", "statue": "minecraft:polished_andesite",
    "statue_top": "minecraft:chiseled_stone_bricks", "flower_tall": "minecraft:peony", "flower_tall_alt": "minecraft:lilac",
    "flower": "minecraft:cornflower", "flower_alt": "minecraft:lily_of_the_valley", "bush": "minecraft:azalea",
    "leaves": "minecraft:spruce_leaves", "trunk": "minecraft:spruce_log", "bed": "minecraft:light_blue_bed",
    "rug": "minecraft:light_blue_carpet", "rug_alt": "minecraft:white_carpet", "carpet": "minecraft:light_blue_carpet",
    "tile": "minecraft:dark_prismarine", "tile_stairs": "minecraft:dark_prismarine_stairs",
    "tile_slab": "minecraft:dark_prismarine_slab", "roof": "minecraft:prismarine_brick_stairs",
    "roof_slab": "minecraft:prismarine_brick_slab", "roof_full": "minecraft:prismarine_bricks", "gold": "minecraft:gold_block",
    "jade": "minecraft:prismarine", "glass": "minecraft:white_stained_glass_pane", "accent": "minecraft:lapis_block",
    "trim": "minecraft:light_blue_terracotta", "wall": "minecraft:quartz_bricks", "wall_alt": "minecraft:birch_planks",
    "imperial": "minecraft:smooth_quartz",
})

# Block swaps per alignment (base id -> id). Properties are kept when both blocks know them (see _rule_props).
WOOD = ("_planks", "_stairs", "_slab", "_fence", "_fence_gate", "_log", "_door", "_trapdoor", "_pressure_plate", "_shelf",
        "_button", "_sign", "_wall_sign")


def _kayu(dari, ke):
    m = {"minecraft:%s%s" % (dari, s): "minecraft:%s%s" % (ke, s) for s in WOOD}
    m["minecraft:stripped_%s_log" % dari] = "minecraft:stripped_%s_log" % ke
    return m


def _warna(dari, ke):
    return {"minecraft:%s_%s" % (dari, s): "minecraft:%s_%s" % (ke, s)
            for s in ("banner", "wall_banner", "wool", "carpet", "bed", "candle", "stained_glass_pane")}


SWAP = {
    "lurus": dict(_kayu("dark_oak", "birch"), **{"minecraft:stripped_spruce_wood": "minecraft:stripped_birch_wood"}),
    "netral": {},
    "iblis": {},
}
# neutral: spruce wood and gold, grey tile roofs, andesite paving, yellow cloth
SWAP["netral"].update(_kayu("birch", "spruce"))
SWAP["netral"].update(_kayu("pale_oak", "oak"))
SWAP["netral"].update(_kayu("dark_oak", "spruce"))
SWAP["netral"].update(_warna("light_blue", "yellow"))
SWAP["netral"].update({
    "minecraft:white_wool": "minecraft:orange_wool", "minecraft:white_carpet": "minecraft:brown_carpet",
    "minecraft:white_banner": "minecraft:orange_banner", "minecraft:white_wall_banner": "minecraft:orange_wall_banner",
    "minecraft:smooth_quartz": "minecraft:polished_andesite", "minecraft:quartz_bricks": "minecraft:andesite",
    "minecraft:smooth_quartz_slab": "minecraft:polished_andesite_slab", "minecraft:smooth_quartz_stairs": "minecraft:polished_andesite_stairs",
    "minecraft:polished_diorite": "minecraft:packed_mud", "minecraft:diorite": "minecraft:mud_bricks",
    "minecraft:calcite": "minecraft:white_terracotta", "minecraft:white_concrete": "minecraft:yellow_terracotta",
    "minecraft:quartz_pillar": "minecraft:stripped_spruce_log", "minecraft:diorite_wall": "minecraft:andesite_wall",
    "minecraft:dark_prismarine": "minecraft:deepslate_tiles", "minecraft:dark_prismarine_stairs": "minecraft:deepslate_tile_stairs",
    "minecraft:dark_prismarine_slab": "minecraft:deepslate_tile_slab", "minecraft:prismarine_brick_stairs": "minecraft:mud_brick_stairs",
    "minecraft:prismarine_brick_slab": "minecraft:mud_brick_slab", "minecraft:prismarine_bricks": "minecraft:mud_bricks",
    "minecraft:prismarine": "minecraft:moss_block", "minecraft:lapis_block": "minecraft:gold_block",
    "minecraft:light_blue_terracotta": "minecraft:yellow_terracotta", "minecraft:white_stained_glass_pane": "minecraft:glass_pane",
    "minecraft:cornflower": "minecraft:dandelion", "minecraft:lily_of_the_valley": "minecraft:oxeye_daisy",
    "minecraft:lilac": "minecraft:rose_bush", "minecraft:prismarine_wall": "minecraft:mud_brick_wall",
})
# demonic: blackstone, crimson wood, nether brick roofs, soul light, black and blood-red cloth
SWAP["iblis"].update(_kayu("birch", "dark_oak"))
SWAP["iblis"].update(_kayu("pale_oak", "crimson"))
SWAP["iblis"].update({"minecraft:pale_oak_log": "minecraft:crimson_stem", "minecraft:stripped_pale_oak_log": "minecraft:stripped_crimson_stem",
                      "minecraft:birch_log": "minecraft:dark_oak_log", "minecraft:stripped_birch_log": "minecraft:stripped_dark_oak_log"})
SWAP["iblis"].update(_warna("light_blue", "black"))
SWAP["iblis"].update({
    "minecraft:white_wool": "minecraft:red_wool", "minecraft:white_carpet": "minecraft:red_carpet",
    "minecraft:white_banner": "minecraft:red_banner", "minecraft:white_wall_banner": "minecraft:red_wall_banner",
    "minecraft:smooth_quartz": "minecraft:polished_blackstone", "minecraft:quartz_bricks": "minecraft:polished_blackstone_bricks",
    "minecraft:smooth_quartz_slab": "minecraft:polished_blackstone_slab",
    "minecraft:smooth_quartz_stairs": "minecraft:polished_blackstone_stairs", "minecraft:polished_diorite": "minecraft:blackstone",
    "minecraft:diorite": "minecraft:basalt", "minecraft:stone_bricks": "minecraft:polished_blackstone_bricks",
    "minecraft:mossy_stone_bricks": "minecraft:cracked_polished_blackstone_bricks",
    "minecraft:stone_brick_wall": "minecraft:polished_blackstone_brick_wall",
    "minecraft:stone_brick_stairs": "minecraft:polished_blackstone_brick_stairs",
    "minecraft:stone_brick_slab": "minecraft:polished_blackstone_brick_slab",
    "minecraft:chiseled_stone_bricks": "minecraft:chiseled_polished_blackstone", "minecraft:smooth_stone": "minecraft:blackstone",
    "minecraft:calcite": "minecraft:black_terracotta", "minecraft:white_concrete": "minecraft:red_terracotta",
    "minecraft:quartz_pillar": "minecraft:crimson_hyphae", "minecraft:diorite_wall": "minecraft:red_nether_brick_wall",
    "minecraft:dark_prismarine": "minecraft:blackstone", "minecraft:dark_prismarine_stairs": "minecraft:blackstone_stairs",
    "minecraft:dark_prismarine_slab": "minecraft:blackstone_slab", "minecraft:prismarine_brick_stairs": "minecraft:nether_brick_stairs",
    "minecraft:prismarine_brick_slab": "minecraft:nether_brick_slab", "minecraft:prismarine_bricks": "minecraft:nether_bricks",
    "minecraft:prismarine": "minecraft:crying_obsidian", "minecraft:prismarine_wall": "minecraft:nether_brick_wall",
    "minecraft:gold_block": "minecraft:gilded_blackstone", "minecraft:lapis_block": "minecraft:red_nether_bricks",
    "minecraft:light_blue_terracotta": "minecraft:crimson_hyphae", "minecraft:lantern": "minecraft:soul_lantern",
    "minecraft:ochre_froglight": "minecraft:pearlescent_froglight", "minecraft:white_stained_glass_pane": "minecraft:red_stained_glass_pane",
    "minecraft:grass_block": "minecraft:crimson_nylium", "minecraft:podzol": "minecraft:soul_soil",
    "minecraft:polished_andesite": "minecraft:polished_basalt", "minecraft:spruce_log": "minecraft:crimson_stem",
    "minecraft:spruce_leaves": "minecraft:nether_wart_block", "minecraft:cornflower": "minecraft:wither_rose",
    "minecraft:lily_of_the_valley": "minecraft:crimson_roots", "minecraft:azalea": "minecraft:crimson_fungus",
    "minecraft:lilac": "minecraft:rose_bush", "minecraft:cherry_leaves": "minecraft:nether_wart_block",
    "minecraft:cherry_log": "minecraft:crimson_stem", "minecraft:short_grass": "minecraft:crimson_roots",
    "minecraft:potted_bamboo": "minecraft:potted_crimson_fungus", "minecraft:potted_azalea_bush": "minecraft:potted_crimson_roots",
})

# =========================================================================================== registry of this run
SAVED = {}        # content hash -> template id
NAMA = {}         # template name -> content hash (two different lots never share a name)
LOTNAME = {}      # (district key, i, j) -> template id
EXTRA = {}        # district key -> [lot dict] (stairs, terrace walls, back walls)
SPESIFIK = {}     # template id -> aliran tuple the template is drawn for (None = all three through processors)


def tid(name):
    return "%s%s/%s" % (NS, F, name)


def _hash(lot):
    h = hashlib.sha1()
    h.update(repr(lot.size).encode())
    for pos in sorted(lot.cells):
        (key, nbt) = lot.cells[pos]
        h.update(repr((pos, key, sorted((nbt or {}).items()) if nbt else None)).encode())
    for e in sorted(lot.entities, key=lambda e: (e[0], e[1], e[2], repr(sorted(e[3].items())))):
        h.update(repr((e[0], e[1], e[2], repr(sorted(e[3].items())))).encode())
    for t in lot.titik_list:
        h.update(json.dumps(t, sort_keys=True).encode())
    return h.hexdigest()


def simpan_lot(lot, aliran=None):
    """Saves a lot unless an identical one was saved already; returns the template id used."""
    key = _hash(lot)
    if key in SAVED:
        return SAVED[key]
    base, n = lot.nama, 2
    while lot.nama in NAMA:
        lot.nama = "%s_%d" % (base, n)
        n += 1
    NAMA[lot.nama] = key
    if len(lot.titik_list) > MAKS_TITIK_LOT:
        raise SystemExit("%s: %d titik > %d (38.C.10 compound lot)" % (lot.id, len(lot.titik_list), MAKS_TITIK_LOT))
    lot.simpan()
    SAVED[key] = lot.id
    SPESIFIK[lot.id] = aliran
    return lot.id


def potong_distrik(k, distrik, names, aliran=None, tinggi_min=20):
    """Cuts a canvas into lots named names[(i, j)] (paths inside kompleks_sekte/) and records them for `distrik`."""
    lots = K.potong(k, {ij: n for ij, n in names.items()}, tinggi_min=tinggi_min, folder=F)
    for ij, lot in lots.items():
        LOTNAME[(distrik, ij[0], ij[1])] = simpan_lot(lot, aliran)


def kanvas(name, rows=3, h=56):
    return K.Kanvas(144, h, rows * tl.LOT, seed=tl._seed(F + "/" + name), palette=BASE)


# ===================================================================================================== frame
CX = 72          # canvas centre column (avenue x 66..77)


def tanah(k, rows, avenue=True):
    """Floor of a district: jade paving, a 12-wide avenue up the middle with carved kerbs and stone lanterns, 4-wide
    cross streets on the lot seams, gutters along the avenue."""
    sx, _, sz = k.size
    K.paving(k, 0, 0, sx - 1, sz - 1, LY, role="floor", alt="floor_alt", alt_chance=0.09)
    if avenue:
        for z in range(sz):
            for x in range(66, 78):
                edge = x in (66, 77)
                mid = x in (71, 72)
                k.put(x, LY, z, "kerb" if edge else ("kerb_chisel" if mid and z % 6 == 0 else "path"))
            for x in (65, 78):
                k.put(x, LY, z, "gutter")
        for z in range(9, sz - 2, 12):
            K.stone_lantern(k, 64, LY + 1, z)
            K.stone_lantern(k, 79, LY + 1, z)
    for j in range(1, rows + 1):
        z0 = j * tl.LOT - 2
        for z in range(z0, min(sz, z0 + 4)):
            for x in list(range(3, 65)) + list(range(79, 141)):
                k.put(x, LY, z, "path_alt" if (x + z) % 9 == 0 else "path")


def tembok_sisi(k, h=6):
    """The compound wall along both outer sides: stone plinth, plaster body, tile cap and merlons, lantern posts inside
    every 12, a small watch pavilion on every lot seam."""
    _, _, sz = k.size
    for x0, inner in ((0, 3), (141, 140)):
        for z in range(sz):
            for x in range(x0, x0 + 3):
                k.put(x, LY, z, "kerb")
                for dy in range(1, h + 1):
                    role = "kerb" if dy <= 2 else ("beam" if dy == h else "plaster")
                    if role == "beam":
                        k.put(x, LY + dy, z, role, {"axis": "z"})
                    else:
                        k.put(x, LY + dy, z, role)
            outer = x0 if x0 == 0 else x0 + 2
            k.slab(x0 + 1, LY + h + 1, z, "tile_slab")
            k.stairs(outer, LY + h + 1, z, "tile_stairs", "east" if x0 == 0 else "west")
            k.stairs(x0 + (2 if x0 == 0 else 0), LY + h + 1, z, "tile_stairs", "west" if x0 == 0 else "east")
            if z % 12 == 6:
                K.post(k, inner, LY + 1, z, height=3, post_role="wood_fence")
        for z in range(24, sz, 48):
            # watch pavilion standing on the wall
            for dz in (-2, 2):
                for dy in range(h + 1, h + 5):
                    k.put(x0 + 1, LY + dy, z + dz, "red")
            K.gable(k, x0, LY + h + 5, z - 2, x0 + 2, z + 2, along="z", overhang=1, pearl=True,
                    role="tile_stairs", slab_role="tile_slab", full_role="tile")
            K.lit(k, x0 + 1, LY + h + 5, z, chain=1, role="lamp")


def tembok_lintang(k, z0, gap=None, h=6):
    """A wall across the canvas at z0..z0+2 (the back of the summit, the front of the court) with an optional gap."""
    sx, _, _ = k.size
    for x in range(sx):
        if gap and gap[0] <= x <= gap[1]:
            continue
        for z in range(z0, z0 + 3):
            k.put(x, LY, z, "kerb")
            for dy in range(1, h + 1):
                role = "kerb" if dy <= 2 else ("beam" if dy == h else "plaster")
                k.put(x, LY + dy, z, role, {"axis": "x"} if role == "beam" else None)
        k.slab(x, LY + h + 1, z0 + 1, "tile_slab")
        k.stairs(x, LY + h + 1, z0, "tile_stairs", "south")
        k.stairs(x, LY + h + 1, z0 + 2, "tile_stairs", "north")


# ===================================================================================================== small pieces
def boneka(k, x, y, z, yaw, senjata="wooden_sword", zirah=None):
    """A training dummy: an armour stand with arms (locked slots, invulnerable), a practice weapon, maybe leather."""
    e = {"id": "minecraft:armor_stand", "Invulnerable": True, "DisabledSlots": 4144959, "ShowArms": True,
         "PersistenceRequired": True, "Rotation": [structgen.nbt.Float(yaw), structgen.nbt.Float(0.0)],
         "equipment": {"mainhand": {"id": "minecraft:" + senjata, "count": 1}}}
    if zirah:
        e["equipment"]["chest"] = {"id": "minecraft:" + zirah, "count": 1}
    k.entity(x, y, z, e)


def tiang_latih(k, x, y, z):
    """A wooden striking post (stripped log + hay head + fence arms)."""
    K.put_ax(k, x, y, z, "beam")
    K.put_ax(k, x, y + 1, z, "beam")
    k.put(x, y + 2, z, "minecraft:hay_block", {"axis": "y"})
    k.put(x - 1, y + 1, z, "wood_fence")
    k.put(x + 1, y + 1, z, "wood_fence")


def rak_senjata(k, x, z, y, facing, n=4):
    """A weapon rack: a dark frame with display stands holding spears and swords (entities, locked)."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(n):
        px, pz = x + rx * i, z + rz * i
        k.put(px - dx, y + 1, pz - dz, "log", {"axis": "y"})
        k.put(px - dx, y + 2, pz - dz, "log", {"axis": "y"})
        k.put(px - dx, y + 3, pz - dz, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    yaw = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}[facing]
    for i in range(0, n, 2):
        boneka(k, x + rx * i + 0.5, y + 1, z + rz * i + 0.5, yaw, ("iron_sword", "stone_sword", "iron_axe")[i // 2 % 3])


def bangku_batu(k, x, z, facing, n=4, y=LY):
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(n):
        k.stairs(x + rx * i, y + 1, z + rz * i, "kerb_stairs", OPP[facing])


def lonceng(k, x, y, z, facing="north"):
    """A bronze bell hung in a red frame (bell attached to the ceiling beam)."""
    for dx in (-1, 1):
        for dy in range(0, 4):
            k.put(x + dx, y + dy, z, "red")
    k.put(x, y + 3, z, "beam", {"axis": "x"})
    k.put(x, y + 2, z, "minecraft:bell", {"attachment": "ceiling", "facing": facing, "powered": "false"})


def bantal(k, x, y, z, hadap, jenis="kursi_lantai"):
    """A meditation / floor cushion (carpet) with its life point."""
    k.put(x, y, z, "rug")
    k.titik(jenis, x, y, z, hadap)


def bedeng(k, x0, z0, x1, z1, y=LY):
    """A kerbed flower bed with a fixed pattern (no canvas random: lots shared by two bands must stay identical)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                k.slab(x, y, z, "kerb_slab", "top")
                continue
            k.put(x, y, z, "ground", {"snowy": "false"})
            m = (x * 7 + z * 3) % 6
            if m == 0:
                K.tall_flower(k, x, y + 1, z, "flower_tall")
            elif m in (1, 4):
                k.put(x, y + 1, z, "flower" if m == 1 else "flower_alt")
            elif m == 3:
                k.put(x, y + 1, z, "minecraft:short_grass")


def batu_hias(k, x, y, z, h=3):
    """A scholar's rock: a tall irregular stack of andesite, tuff and moss."""
    for dy in range(h):
        k.put(x, y + dy, z, ("minecraft:andesite", "minecraft:tuff", "minecraft:mossy_cobblestone")[(x + dy) % 3])
    k.put(x + 1, y, z, "minecraft:tuff")
    k.put(x, y + h, z, "minecraft:moss_carpet")


# ===================================================================================================== HALAMAN
def gerbang_halaman(k):
    """The court gate on the south wall (lot (1,2)): a two-storey gatehouse with three passages, a drum loft, a gold name
    board over the middle arch, guards' posts, then a forecourt with lions, a paifang and a bronze censer."""
    z0, z1 = 128, 140
    f = K.podium(k, 57, z0, 86, z1, LY, 1, steps=("south", "north"), step_w=7)
    # ground storey: solid piers with three passages
    for x in range(58, 86):
        for z in range(z0 + 1, z1):
            passage = 68 <= x <= 75 or 61 <= x <= 64 or 79 <= x <= 82
            for dy in range(1, 7):
                if passage and dy <= 5:
                    k.put(x, f + dy, z, "minecraft:air")
                elif x in (58, 85) or z in (z0 + 1, z1 - 1) or not passage:
                    k.put(x, f + dy, z, "kerb" if dy <= 2 else "plaster")
    for x in range(58, 86):
        k.put(x, f + 6, z0 + 1, "beam", {"axis": "x"})
        k.put(x, f + 6, z1 - 1, "beam", {"axis": "x"})
    # arch heads: upside-down stairs at the top corners of each passage
    for a, b in ((68, 75), (61, 64), (79, 82)):
        for z in (z0 + 1, z1 - 1):
            k.stairs(a, f + 5, z, "kerb_stairs", "east", half="top")
            k.stairs(b, f + 5, z, "kerb_stairs", "west", half="top")
    # upper storey (drum loft)
    k.fill(59, f + 7, z0 + 2, 84, f + 7, z1 - 2, "wood")
    K.hall(k, 60, z0 + 2, 83, z1 - 2, f + 7, 5, door_sides=(), windows=3, lamps=True, plaque_top=False)
    K.drum(k, 66, f + 8, 134, "south")
    K.drum(k, 77, f + 8, 134, "south")
    K.ladder(k, 68, f + 1, f + 7, 134, "east")
    K.roof_over(k, 59, z0 + 1, 84, z1 - 1, f + 13, kind="hip", tiers=2, overhang=2, top="pearl")
    K.plaque(k, 71, f + 6, z1, "x")
    K.plaque(k, 72, f + 6, z1, "x")
    for x in (66, 77):
        K.wall_banner(k, x, f + 4, z1 + 1, "south")
    # guards
    for x, z, hd in ((65, 143, "south"), (78, 143, "south"), (66, 125, "north"), (77, 125, "north")):
        k.titik("jaga", x, LY + 1, z, hd)
    k.jangkar(72.0, LY + 1, 124.5, "kompleks/gerbang", yaw=180.0, protect=40)
    # forecourt
    K.lion(k, 65, LY, 121, "south")
    K.lion(k, 78, LY, 121, "south")
    K.paifang(k, 71, 114, LY, width=15, height=9, facing="south")
    K.incense_burner(k, 72, LY + 1, 104, big=True)
    for x in (60, 83):
        K.banner_pole(k, x, LY + 1, 110, height=6)
    k.jalur([(72, 142), (72, 126), (72, 108), (72, 98)], jalur="gerbang")


def asrama_luar(k):
    """Outer disciples' dormitory (lot (0,2)): two long halls of eight beds facing a laundry court with a well."""
    for (z0, z1, door) in ((101, 112, "south"), (122, 133, "north")):
        f = K.podium(k, 6, z0, 40, z1, LY, 1, steps=(door,), step_w=3)
        inner = K.hall(k, 7, z0 + 1, 39, z1 - 1, f, 5, door_sides=(door,), door_w=2, windows=4)
        ix0, iz0, ix1, iz1 = inner
        for x in range(ix0 + 1, ix1, 4):
            if abs(x - 23) <= 2:
                continue
            for zz, face in ((iz0, "south"), (iz1, "north")):
                K.bed(k, x, f + 1, zz, "north" if face == "south" else "south")
                k.titik("kasur", x, f + 1, zz, face)
                K.chest(k, x + 1, f + 1, zz, None, face)
        K.rug(k, ix0 + 1, (iz0 + iz1) // 2, ix1 - 1, (iz0 + iz1) // 2, f + 1)
        K.gable(k, 6, f + 6, z0, 40, z1, along="x", overhang=1)
        k.titik("pintu", 23, f + 1, z1 + 1 if door == "south" else z0 - 1, door)
    # laundry court
    K.well(k, 23, 117, LY)
    for x in range(10, 37, 6):
        k.put(x, LY + 1, 115, "wood_fence")
        k.put(x, LY + 2, 115, "wood_fence")
        k.put(x, LY + 1, 119, "wood_fence")
        k.put(x, LY + 2, 119, "wood_fence")
    for x in range(10, 37):
        if x % 6:
            k.put(x, LY + 3, 115, "rug_alt")
    for x in (12, 34):
        K.bench(k, x, 118, "north", n=3)
    k.titik("obrol", 14, LY + 1, 117, "east")
    k.titik("obrol", 32, LY + 1, 117, "west")
    k.titik("kerja:sapu", 28, LY + 1, 120, "west")
    k.jalur([(23, 98), (23, 117), (44, 117), (64, 117)], jalur="asrama")


def dapur_makan(k):
    """Refectory and kitchen (lot (2,2)): a long dining hall with four tables and benches, a kitchen with cooking fires
    (the only lit fires of the court), a woodshed and water vats."""
    f = K.podium(k, 100, 100, 138, 115, LY, 1, steps=("north", "west"), step_w=3)
    inner = K.hall(k, 101, 101, 137, 114, f, 6, door_sides=("north", "west"), door_w=3, windows=4)
    ix0, iz0, ix1, iz1 = inner
    n = 0
    for z in (104, 110):
        for x0 in (104, 121):
            for x in range(x0, x0 + 12):
                K.table(k, x, f + 1, z)
                if x % 3 == 0 and n < 24:
                    K.chair(k, x, f + 1, z - 1, "south")
                    K.chair(k, x, f + 1, z + 1, "north")
                    k.titik("kursi", x, f + 1, z - 1, "south")
                    k.titik("kursi", x, f + 1, z + 1, "north")
                    n += 2
            k.titik("meja_makan", x0 + 6, f + 1, z, "south")
    K.wall_banner(k, 119, f + 4, iz1, "north")
    K.roof_over(k, 100, 100, 138, 115, f + 7, kind="hip", overhang=1, top="pearl")
    # kitchen
    fk = K.podium(k, 104, 120, 124, 134, LY, 1, steps=("north",), step_w=3)
    K.hall(k, 105, 121, 123, 133, fk, 5, door_sides=("north",), door_w=2, windows=3)
    K.furnish_kitchen(k, 107, 122, 121, fk, 132, "north", lit_fire=True)
    for x in (108, 114, 120):
        k.put(x, fk + 1, 126, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false",
                                                    "waterlogged": "false"})
        k.put(x, fk + 2, 126, "minecraft:cauldron")
        k.titik("kerja:kuali", x, fk + 1, 125, "south")
    K.counter(k, 106, 122, 129, fk, "north")
    k.titik("kerja:lumpang", 111, fk + 1, 130, "north")
    K.gable(k, 104, fk + 6, 120, 124, 134, along="x", overhang=1)
    # woodshed + vats
    for x in range(128, 138):
        for dy in (1, 2):
            k.put(x, LY + dy, 132, "beam" if x % 2 else "log", {"axis": "x"})
        k.slab(x, LY + 3, 132, "wood_slab")
    for x in (129, 133):
        k.put(x, LY + 1, 124, "minecraft:water_cauldron", {"level": "3"})
        k.put(x + 1, LY + 1, 124, "minecraft:barrel", {"facing": "up", "open": "false"})
    k.titik("kerja:sapu", 126, LY + 1, 118, "south")
    k.jalur([(119, 98), (119, 99), (112, 118), (112, 119)], jalur="dapur")


def lapangan_latihan(k):
    """Training ground (lot (1,1)): a 36 x 36 stone court with a carved border, six dummies, striking posts, weapon
    racks, a referee's drum, banner poles, stone benches for spectators; 12 drill points (38.C.3.2)."""
    x0, z0, x1, z1 = 54, 54, 89, 89
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            k.put(x, LY, z, "kerb_chisel" if edge and (x + z) % 4 == 0 else "kerb" if edge else
                  ("paving_alt" if ((x - x0) // 6 + (z - z0) // 6) % 2 else "paving"))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if not 69 <= x <= 74:
                k.put(x, LY + 1, z, "kerb_wall")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if not 69 <= z <= 74:
                k.put(x, LY + 1, z, "kerb_wall")
    for i, x in enumerate((60, 72, 84)):
        boneka(k, x + 0.5, LY + 1, 59.5, 0.0, "wooden_sword", "leather_chestplate" if i == 1 else None)
        boneka(k, x + 0.5, LY + 1, 84.5, 180.0, "stone_sword")
        tiang_latih(k, x - 3, LY + 1, 64)
    rak_senjata(k, x0 + 1, 64, LY, "east", n=6)
    rak_senjata(k, x1 - 1, 64, LY, "west", n=6)
    K.drum(k, 72, LY + 1, z0 + 1, "south")
    K.drum(k, 71, LY + 1, z0 + 1, "south")
    for x in (57, 86):
        K.banner_pole(k, x, LY + 1, z0 + 2, height=6)
    for z in (60, 72, 84):
        bangku_batu(k, x0 - 3, z, "east", n=3)
        bangku_batu(k, x1 + 3, z - 2, "west", n=3)
        k.titik("penonton", x0 - 3, LY + 1, z + 1, "east")
        k.titik("penonton", x1 + 3, LY + 1, z - 1, "west")
    for x in range(60, 85, 8):
        for z in range(68, 81, 6):
            k.titik("latihan", x, LY + 1, z, "north")
    k.titik("jaga", 72, LY + 1, z0 + 2, "south")
    k.jangkar(72.0, LY + 1, 72.0, "kompleks/arena", protect=36)


def gudang_pelayan(k):
    """Storehouse and servants' quarters (lot (2,1)): granary with crates and sacks, a cart, a servants' house of four
    beds, a firewood stack and a wash place."""
    f = K.podium(k, 100, 53, 121, 70, LY, 1, steps=("south",), step_w=3)
    K.hall(k, 101, 54, 120, 69, f, 6, door_sides=("south",), door_w=3, windows=0)
    K.crates(k, 102, 55, 108, 60, f + 1, h=3)
    K.crates(k, 113, 55, 119, 60, f + 1, h=3)
    for x in range(103, 119, 3):
        K.chest(k, x, f + 1, 68, None, "north")
    k.titik("kerja:rak", 110, f + 1, 63, "north")
    K.gable(k, 100, f + 7, 53, 121, 70, along="x", overhang=1)
    fh = K.podium(k, 125, 54, 138, 68, LY, 1, steps=("west",), step_w=3)
    inner = K.hall(k, 126, 55, 137, 67, fh, 5, door_sides=("west",), door_w=2, windows=3)
    for i, x in enumerate((128, 131, 134)):
        K.bed(k, x, fh + 1, 56, "north")
        k.titik("kasur", x, fh + 1, 56, "south")
    K.bed(k, 135, fh + 1, 66, "south")
    k.titik("kasur", 135, fh + 1, 66, "north")
    K.furnish_dining(k, 130, 62, fh, n=4)
    K.gable(k, 125, fh + 6, 54, 138, 68, along="z", overhang=1)
    _ = inner
    # cart and firewood
    for x in range(104, 110):
        k.put(x, LY + 1, 76, "wood_slab", {"type": "top", "waterlogged": "false"})
    k.put(104, LY + 1, 77, "minecraft:oak_fence")
    k.put(109, LY + 1, 77, "minecraft:oak_fence")
    for x in range(126, 138):
        for dy in (1, 2, 3):
            k.put(x, LY + dy, 80, "log", {"axis": "x"})
    k.put(131, LY + 1, 86, "minecraft:water_cauldron", {"level": "3"})
    for x, z, hd in ((112, 78, "south"), (128, 76, "north"), (96, 88, "east")):
        k.titik("kerja:sapu", x, LY + 1, z, hd)
    k.titik("peti_milik", 106, f + 1, 68, "north")


def aula_utama(k):
    """The main hall (lot (1,0)): 32 x 26 on a two-step podium, double-eaved, a veranda colonnade; inside an ancestral
    altar with tablets, censer and candles, rows of cushions for the morning lecture, drum and bell stands. The plaza in
    front has twin carved columns and a bronze censer. Anchors: the compound site and the elders' audience."""
    a = K.aula(k, 56, 6, 87, 31, h=8, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south",), door_w=3)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    # altar
    for x in range(67, 77):
        k.put(x, f + 1, iz0, "kerb_chisel" if x in (67, 76) else "kerb")
        k.put(x, f + 2, iz0, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    for x in (69, 72, 74):
        k.put(x, f + 2, iz0, "gold")
        k.put(x, f + 3, iz0, "minecraft:white_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    K.incense_burner(k, 72, f + 1, iz0 + 3)
    k.titik("dupa", 72, f + 1, iz0 + 4, "north")
    k.titik("jaga", 66, f + 1, iz0 + 2, "south")
    k.titik("jaga", 78, f + 1, iz0 + 2, "south")
    for x in (63, 81):
        lonceng(k, x, f + 1, iz0 + 2)
        K.drum(k, x, f + 1, iz0 + 6, "south")
    n = 0
    for z in range(iz0 + 8, iz1 - 2, 3):
        for x in range(ix0 + 4, ix1 - 3, 3):
            if abs(x - 72) <= 1:
                continue
            if n < 16:
                bantal(k, x, f + 1, z, "north")
                n += 1
    for x in (ix0 + 2, ix1 - 2):
        for z in range(iz0 + 3, iz1, 5):
            K.put_ax(k, x, f + 1, z, "red")
    K.rug(k, 71, iz0 + 6, 72, iz1, f + 1, role="rug_alt", border="rug_alt")
    k.jangkar(72.0, f + 1, iz0 + 5.0, "kompleks/aula", yaw=180.0)
    # plaza
    K.huabiao(k, 62, LY + 1, 38, 8)
    K.huabiao(k, 81, LY + 1, 38, 8)
    K.incense_burner(k, 72, LY + 1, 40, big=True)
    k.jangkar(72.0, LY + 1, 43.5, "kompleks/situs", protect=120)
    k.titik("obrol", 60, LY + 1, 42, "east")
    k.titik("obrol", 84, LY + 1, 42, "west")
    k.jalur([(72, 46), (72, 36), (72, 33)], jalur="aula")


# ------------------------------------------------------------------------------------------- alignment slots (court)
def kebun_obat(k):
    """Herb garden (lurus slot A, lot (0,1)): terraced beds of berries, azalea and ferns watered by stone channels, a
    drying shed with racks and a small greenhouse-pavilion."""
    for bi, (x0, z0) in enumerate(((6, 54), (22, 54), (6, 70), (22, 70))):
        for x in range(x0, x0 + 13):
            for z in range(z0, z0 + 12):
                edge = x in (x0, x0 + 12) or z in (z0, z0 + 11)
                if edge:
                    k.put(x, LY + 1, z, "kerb_slab", {"type": "bottom", "waterlogged": "false"})
                    continue
                k.put(x, LY, z, "minecraft:farmland" if (x + z) % 5 else "minecraft:water", {"moisture": "7"} if (x + z) % 5 else {"level": "0"})
                r = (x * 7 + z * 3 + bi) % 6
                if (x + z) % 5:
                    if r == 0:
                        k.put(x, LY + 1, z, "minecraft:sweet_berry_bush", {"age": "2"})
                    elif r == 1:
                        k.put(x, LY + 1, z, "minecraft:wheat", {"age": "7"})
                    elif r == 2:
                        k.put(x, LY + 1, z, "minecraft:carrots", {"age": "5"})
                    elif r == 3:
                        k.put(x, LY + 1, z, "minecraft:beetroots", {"age": "3"})
                    else:
                        k.put(x, LY + 1, z, "minecraft:potatoes", {"age": "6"})
        k.titik("kerja:cangkul", x0 + 6, LY + 1, z0 + 12, "north")
    for x in range(6, 35):
        k.put(x, LY, 66, "minecraft:water", {"level": "0"})
        k.put(x, LY - 1, 66, "kerb")
    for z in range(56, 86, 4):
        k.put(38, LY + 1, z, "minecraft:flowering_azalea")
        k.put(40, LY + 1, z + 2, "minecraft:fern")
    # drying shed
    f = K.podium(k, 8, 86, 30, 94, LY, 1, steps=("north",), step_w=3)
    for x in range(9, 30, 4):
        for z in (87, 93):
            for dy in range(1, 5):
                k.put(x, f + dy, z, "red")
    for x in range(9, 30):
        for z in range(88, 93):
            if z in (89, 91):
                k.put(x, f + 3, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
        k.put(x, f + 2, 90, "minecraft:dried_kelp_block")
    K.gable(k, 8, f + 5, 86, 30, 94, along="x", overhang=1)
    k.titik("kerja:sabit", 20, f + 1, 89, "south")
    k.titik("kerja:lumpang", 26, f + 1, 91, "west")


def pasar_sekte(k):
    """Sect market (netral slot A, lot (0,1)): four sect stalls around a tea square, a scale house and an anchor for
    the market keeper (38.C.3.3 pasar sekte)."""
    K.paving(k, 6, 52, 44, 92, LY, role="paving", alt="paving_alt", alt_chance=0.15)
    spots = []
    for (x, z, face) in ((10, 58, "south"), (24, 58, "south"), (10, 84, "north"), (24, 84, "north")):
        spots.append((K.stall(k, x, z, face, w=5, goods=("minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block")), face))
    for (sx_, sz_), face in spots:
        k.titik("lapak", sx_, LY + 1, sz_, face)
    # tea pavilion in the middle
    f = K.podium(k, 14, 66, 28, 76, LY, 1, steps=("east", "west"), step_w=3)
    for x in (15, 27):
        for z in (67, 75):
            for dy in range(1, 5):
                k.put(x, f + dy, z, "red")
    K.roof_over(k, 15, 67, 27, 75, f + 5, kind="hip", overhang=1, top="lantern")
    for cx in (18, 24):
        K.low_table(k, cx, f + 1, 71)
        for dz in (-1, 1):
            k.put(cx, f + 1, 71 + dz, "rug")
            k.titik("kursi_lantai", cx, f + 1, 71 + dz, "north" if dz > 0 else "south")
    # scale house
    fs = K.podium(k, 34, 60, 43, 70, LY, 1, steps=("west",), step_w=3)
    K.hall(k, 35, 61, 42, 69, fs, 4, door_sides=("west",), door_w=2, windows=3)
    K.counter_z(k, 37, 62, 68, fs, "west")
    k.titik("kerja:timbangan", 38, fs + 1, 65, "west")
    K.gable(k, 34, fs + 5, 60, 43, 70, along="z", overhang=1)
    k.jangkar(36.5, fs + 1, 65.5, "kompleks/pasar", yaw=90.0)


def sangkar_tawanan(k):
    """Cages (iblis slot A, lot (0,1)): a blackstone yard of iron-barred cells hung with chains, a whipping post and a
    guard tower; nobody is caged here (captives are 38GU's), the cells stand open."""
    for i, x0 in enumerate(range(8, 40, 8)):
        for z0 in (56, 78):
            for x in range(x0, x0 + 5):
                for z in range(z0, z0 + 5):
                    k.put(x, LY, z, "minecraft:polished_blackstone_bricks")
                    edge = x in (x0, x0 + 4) or z in (z0, z0 + 4)
                    door = z == (z0 + 4 if z0 == 56 else z0) and x == x0 + 2
                    for dy in (1, 2, 3):
                        if edge and not door:
                            k.put(x, LY + dy, z, "minecraft:iron_bars")
                    k.put(x, LY + 4, z, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})
            k.put(x0 + 2, LY + 3, z0 + 2, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
            k.put(x0 + 2, LY + 1, z0 + 2, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    for z in range(66, 76):
        for x in range(8, 40):
            k.put(x, LY, z, "minecraft:blackstone" if (x + z) % 3 else "minecraft:polished_basalt", {"axis": "y"} if (x + z) % 3 == 0 else None)
    k.put(24, LY + 1, 71, "minecraft:crimson_hyphae", {"axis": "y"})
    k.put(24, LY + 2, 71, "minecraft:crimson_hyphae", {"axis": "y"})
    k.put(24, LY + 3, 71, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
    for z in (56, 84):
        for dy in range(1, 9):
            k.put(42, LY + dy, z, "minecraft:polished_blackstone_bricks")
        k.put(42, LY + 9, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    for x, z, hd in ((24, 64, "south"), (24, 77, "north"), (40, 71, "west")):
        k.titik("jaga", x, LY + 1, z, hd)


def paviliun_kitab(k):
    """Scripture pavilion (lurus/netral slot B, lot (0,0)): a two-storey library with shelf walls, lecterns and copying
    desks below, a reading gallery above, a double-eaved roof, an ink-stone garden in front."""
    a = K.aula(k, 6, 6, 40, 32, h=6, podium_h=1, teras=1, roof="hip", tiers=2, door_sides=("south",), door_w=3)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.bookshelf_wall(k, ix0, iz0, ix1, iz0, f + 1, f + 4, face="south")
    K.bookshelf_wall(k, ix0, iz0 + 1, ix0, iz1 - 3, f + 1, f + 4, face="east")
    K.bookshelf_wall(k, ix1, iz0 + 1, ix1, iz1 - 3, f + 1, f + 4, face="west")
    n = 0
    for x in range(ix0 + 3, ix1 - 2, 4):
        for z in (iz0 + 5, iz0 + 10):
            k.put(x, f + 1, z, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
            k.stairs(x, f + 1, z + 1, "wood_stairs", "north")
            if n < 6:
                k.titik("meja_tulis", x, f + 1, z + 1, "north")
                n += 1
    K.upper_floor(k, ix0 + 1, iz0 + 1, ix1 - 1, iz0 + 4, f + 5)
    K.stair_flight(k, ix1 - 2, f + 1, iz0 + 9, "north", 4, width=1)
    for x in range(ix0 + 2, ix1 - 1, 3):
        k.put(x, f + 6, iz0 + 5, "rail")
    for x in range(10, 38, 4):
        k.put(x, LY + 1, 36, "minecraft:flower_pot")
        batu_hias(k, x + 2, LY + 1, 40, 2)
    k.titik("obrol", 23, LY + 1, 38, "north")


def altar_iblis(k):
    """Blood altar (iblis slot B, lot (0,0)): a stepped blackstone altar with a crying-obsidian heart, chains to four
    pillars of nether brick, soul lanterns, black banners; the rune circle is a client picture."""
    cx, cz = 23, 22
    for r, h in ((12, 1), (9, 2), (6, 3), (3, 4)):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if max(abs(x - cx), abs(z - cz)) == r:
                    k.stairs(x, LY + h, z, "minecraft:polished_blackstone_stairs",
                             "north" if z == cz + r else "south" if z == cz - r else "west" if x == cx + r else "east")
                else:
                    k.put(x, LY + h, z, "minecraft:polished_blackstone")
    k.put(cx, LY + 5, cz, "minecraft:crying_obsidian")
    k.put(cx, LY + 6, cz, "minecraft:crying_obsidian")
    for dx, dz in ((-10, -10), (10, -10), (-10, 10), (10, 10)):
        for dy in range(1, 10):
            k.put(cx + dx, LY + dy, cz + dz, "minecraft:red_nether_bricks")
        k.put(cx + dx, LY + 10, cz + dz, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
        sx, sz = (1 if dx < 0 else -1), (1 if dz < 0 else -1)
        for i in range(1, 6):
            k.put(cx + dx + sx * i, LY + 9 - i // 2, cz + dz + sz * i, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        k.put(cx + dx, LY + 7, cz + dz + (1 if dz < 0 else -1), "minecraft:black_wall_banner", {"facing": "south" if dz < 0 else "north"})
    for x, z, hd in ((cx, cz + 13, "north"), (cx - 13, cz, "east"), (cx + 13, cz, "west"), (cx, cz - 13, "south")):
        k.titik("dupa", x, LY + 1, z, hd)
    k.titik("jaga", 8, LY + 1, 40, "north")
    k.titik("jaga", 38, LY + 1, 40, "north")


def paviliun_alkimia(k):
    """Alchemy pavilion (lurus/netral slot C, lot (2,0)): four pill cauldrons over low fires, brewing stands, herb
    cabinets, a furnace wall and a drying rack."""
    a = K.aula(k, 100, 6, 138, 32, h=7, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("south", "west"), door_w=3)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for i, (x, z) in enumerate(((107, iz0 + 6), (131, iz0 + 6), (107, iz0 + 15), (131, iz0 + 15))):
        k.put(x, f + 1, z, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        k.put(x, f + 2, z, "minecraft:cauldron")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            k.stairs(x + dx, f + 1, z + dz, "kerb_stairs", {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)])
        k.titik("kerja:tungku", x, f + 1, z + 2, "north")
    for x in range(ix0 + 1, ix1, 3):
        k.put(x, f + 1, iz0, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
        K.barrel(k, x + 1, f + 1, iz0, "south")
        K.shelf(k, x, f + 3, iz0, "south")
    K.counter(k, 114, 124, iz0 + 10, f, "south")
    k.titik("kerja:lumpang", 119, f + 1, iz0 + 9, "south")
    k.titik("kerja:lumpang", 116, f + 1, iz0 + 11, "north")
    for x in range(104, 135, 3):
        k.put(x, LY + 1, 38, "minecraft:composter", {"level": "4"})
    k.jalur([(119, 46), (119, 34)], jalur="alkimia")


def aula_racun(k):
    """Poison hall (iblis slot C, lot (2,0); its path name keeps 'alkimia' for the lit-fire rule): soul-fire cauldrons
    of venom, cages of spiders' silk, a skull shelf, black glass windows."""
    a = K.aula(k, 100, 6, 138, 32, h=7, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("south",), door_w=3)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for i, (x, z) in enumerate(((108, iz0 + 7), (119, iz0 + 7), (130, iz0 + 7))):
        k.put(x, f + 1, z, "minecraft:soul_campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        k.put(x, f + 2, z, "minecraft:water_cauldron", {"level": "2"})
        k.titik("kerja:tungku", x, f + 1, z + 2, "north")
    for x in range(ix0 + 1, ix1, 2):
        k.put(x, f + 1, iz0, "minecraft:cobweb")
        k.put(x, f + 2, iz0, "minecraft:skeleton_skull", {"rotation": "0", "powered": "false"})
    for x in range(106, 134, 4):
        k.put(x, f + 1, iz1 - 2, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
    k.titik("kerja:lumpang", 119, f + 1, iz1 - 3, "south")
    k.titik("jaga", 104, LY + 1, 36, "south")


def aula_puncak(k, x0, z0, x1, z1, podium_h=3, tiers=2, h=9, besar=True):
    """A summit hall: a high podium with a grand flight, a throne under a carved screen, gold-and-jade pillars, a long
    carpet, censers; anchor kompleks/puncak (the sect master's seat, TempatApi.spot "puncak")."""
    a = K.aula(k, x0, z0, x1, z1, h=h, podium_h=podium_h, teras=2, roof="hip", tiers=tiers, door_sides=("south",), door_w=5)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    cx = (x0 + x1) // 2
    # throne dais
    for x in range(cx - 4, cx + 6):
        for z in range(iz0, iz0 + 4):
            k.put(x, f + 1, z, "kerb_chisel" if z == iz0 + 3 else "kerb")
    k.stairs(cx, f + 2, iz0 + 1, "wood_stairs", "north")
    k.stairs(cx + 1, f + 2, iz0 + 1, "wood_stairs", "north")
    for x in (cx - 1, cx + 2):
        k.put(x, f + 2, iz0 + 1, "gold")
    for x in range(cx - 3, cx + 5):
        for dy in range(2, 6):
            k.put(x, f + dy, iz0, "jade" if (x + dy) % 2 else "gold")
    for x in range(cx - 4, cx + 6, 3):
        for z in range(iz0 + 6, iz1, 5):
            K.put_ax(k, x - 3 if x < cx else x + 3, f + 1, z, "red")
    K.rug(k, cx, iz0 + 4, cx + 1, iz1, f + 1, role="rug", border="rug")
    K.incense_burner(k, cx - 5, f + 1, iz0 + 5)
    K.incense_burner(k, cx + 6, f + 1, iz0 + 5)
    k.titik("jaga", cx - 6, f + 1, iz0 + 2, "south")
    k.titik("jaga", cx + 7, f + 1, iz0 + 2, "south")
    k.titik("dupa", cx, f + 1, iz0 + 5, "north")
    k.jangkar(cx + 1.0, f + 2, iz0 + 2.5, "kompleks/puncak", yaw=0.0)
    if besar:
        for z in range(iz0 + 7, iz1 - 1, 4):
            K.banner_pole(k, ix0 + 1, f + 1, z, height=4)
            K.banner_pole(k, ix1 - 1, f + 1, z, height=4)
    return a


def aula_puncak_kecil(k, langit=False):
    """The master's hall of a small sect inside its only court (halaman_kecil lot (2,1)); with a sky palace a cloud pier
    rises behind it (anchor kompleks/dermaga_awan)."""
    aula_puncak(k, 102, 52, 133, 77, podium_h=2, tiers=1, h=7, besar=False)
    K.garden(k, 100, 82, 138, 93, LY, trees=("pine", "pine"), pond_too=False)
    if langit:
        dermaga_awan(k, 134, 52, 140, 90)


def dermaga_awan(k, x0, z0, x1, z1):
    """A cloud pier (38.C.3.3 sky sects): a stair tower up to a railed deck nine blocks up, mooring posts, wind banners;
    anchor kompleks/dermaga_awan where a cloud boat lands (37 reads it through TempatApi.spot)."""
    deck = LY + 10
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            k.put(x, deck, z, "wood")
            if x in (x0, x1) or z in (z0, z1):
                k.put(x, deck + 1, z, "rail")
    for z in range(z0, z1 + 1, 6):
        for x in (x0, x1):
            for dy in range(1, 10):
                k.put(x, LY + dy, z, "red")
    for i in range(9):
        k.stairs(x0 + 1 + (i % 2), LY + 1 + i, z1 - 1 - i, "wood_stairs", "north")
    for z in range(z0 + 3, z1, 8):
        K.banner_pole(k, x1, deck + 1, z, height=4)
        k.put(x0, deck + 2, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    for z in range(z0 + 2, z1, 10):
        K.post(k, (x0 + x1) // 2, deck + 1, z, height=2)
    k.titik("pandang", (x0 + x1) // 2, deck + 1, z0 + 1, "north")
    k.jangkar((x0 + x1) / 2.0 + 0.5, deck + 1, (z0 + z1) / 2.0, "kompleks/dermaga_awan")


# ===================================================================================================== DALAM
def gerbang_dalam(k):
    """Inner gate court (dalam lot (1,2)): the stair arrival, a moon gate pavilion that only friends may pass
    (anchor kompleks/dalam), twin bell frames and a sweeping court."""
    z = 112
    f = K.podium(k, 60, z - 4, 83, z + 4, LY, 1, steps=("south", "north"), step_w=5)
    for x in range(61, 83):
        for dy in range(1, 9):
            d = math.hypot((x - 71.5) / 4.6, (dy - 3.5) / 4.0)
            if d > 1.0:
                k.put(x, f + dy, z, "plaster" if dy < 8 else "beam", {"axis": "x"} if dy == 8 else None)
            else:
                k.put(x, f + dy, z, "minecraft:air")
    K.gable(k, 60, f + 9, z - 1, 83, z + 1, along="x", overhang=1, role="tile_stairs", slab_role="tile_slab", full_role="tile")
    lonceng(k, 58, LY + 1, 120)
    lonceng(k, 86, LY + 1, 120)
    k.titik("jaga", 66, LY + 1, z + 6, "south")
    k.titik("jaga", 77, LY + 1, z + 6, "south")
    k.titik("kerja:sapu", 70, LY + 1, 126, "north")
    k.jangkar(72.0, LY + 1, z + 7.5, "kompleks/dalam", yaw=180.0, protect=24)
    K.garden(k, 50, 124, 63, 136, LY, trees=("cherry",), pond_too=False)
    K.garden(k, 81, 124, 94, 136, LY, trees=("cherry",), pond_too=False)


def asrama_dalam(k):
    """Inner disciples' quarters (dalam lot (0,2)): a courtyard house of four rooms with two beds each, a study bench,
    a plum tree and a well in the yard."""
    K.yard_wall(k, 5, 100, 42, 138, LY, h=3, gate="east", gate_w=3)
    rooms = ((8, 103, 22, 113, "south"), (25, 103, 39, 113, "south"), (8, 125, 22, 135, "north"), (25, 125, 39, 135, "north"))
    for (x0, z0, x1, z1, door) in rooms:
        f = K.podium(k, x0, z0, x1, z1, LY, 1, steps=(door,), step_w=3)
        inner = K.hall(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, 5, door_sides=(door,), door_w=2, windows=3)
        ix0, iz0, ix1, iz1 = inner
        back = iz0 if door == "south" else iz1
        for x in (ix0 + 1, ix1 - 1):
            K.bed(k, x, f + 1, back, "north" if door == "south" else "south")
            k.titik("kasur", x, f + 1, back, door)
        k.put((ix0 + ix1) // 2, f + 1, back, "minecraft:lectern", {"facing": door, "has_book": "false", "powered": "false"})
        k.titik("meja_tulis", (ix0 + ix1) // 2, f + 1, back + (1 if door == "south" else -1), OPP[door])
        front = iz1 if door == "south" else iz0
        mid = (iz0 + iz1) // 2
        for x in (ix0 + 3, ix1 - 3):
            bantal(k, x, f + 1, mid, OPP[door], "meditasi")
        K.chest(k, ix0, f + 1, front, None, "east")
        K.barrel(k, ix1, f + 1, front, "west")
        K.pot(k, ix1, f + 2, front, "minecraft:potted_bamboo")
        k.put(ix0, f + 1, mid, "minecraft:white_candle", {"candles": "1", "lit": "true", "waterlogged": "false"})
        K.lit(k, (ix0 + ix1) // 2, f + 5, mid, chain=1, role="lamp")
        K.gable(k, x0, f + 6, z0, x1, z1, along="x", overhang=1)
    K.well(k, 23, 119, LY)
    K.cherry(k, 12, LY + 1, 119, 5)
    K.cherry(k, 35, LY + 1, 119, 5)
    k.titik("obrol", 18, LY + 1, 119, "east")
    k.titik("main", 29, LY + 1, 121, "west")


def kolam_roh(k, x0, z0):
    """Spirit pool (lurus/netral slot D): a jade-fenced pond with lotus, stepping stones to a meditation island, eight
    cushions on the rim, a weeping willow (38.C.3.2 kolam_roh, 8 meditasi)."""
    cx, cz = x0 + 21, z0 + 21
    for x in range(cx - 16, cx + 17):
        for z in range(cz - 14, cz + 15):
            d = ((x - cx) / 16.0) ** 2 + ((z - cz) / 14.0) ** 2
            if d <= 0.8:
                k.put(x, LY - 1, z, "minecraft:clay")
                k.put(x, LY, z, "minecraft:water", {"level": "0"})
            elif d <= 1.0:
                k.put(x, LY, z, "jade")
                k.put(x, LY + 1, z, "minecraft:prismarine_wall")
    for i in range(16):
        x = cx - 12 + (i * 7) % 25
        z = cz - 10 + (i * 5) % 21
        if k.get(x, LY, z) == "minecraft:water":
            k.put(x, LY + 1, z, "minecraft:lily_pad")
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            k.put(x, LY, z, "kerb")
    for x in range(cx + 3, cx + 16):
        if x % 2 == 0:
            k.put(x, LY, cz, "kerb_slab", {"type": "top", "waterlogged": "false"})
    for x in range(cx + 15, cx + 18):
        for dy in (1, 2):
            k.put(x, LY + dy, cz, "minecraft:air")
    k.put(cx, LY + 1, cz, "minecraft:moss_carpet")
    k.titik("meditasi", cx, LY + 1, cz, "south")
    for i in range(7):
        a = i * 2 * math.pi / 7
        x = int(round(cx + math.cos(a) * 18))
        z = int(round(cz + math.sin(a) * 16))
        if k.inside(x, LY + 1, z) and k.get(x, LY + 1, z) is None:
            bantal(k, x, LY + 1, z, "north" if z > cz else "south", "meditasi")
    K.willow(k, cx - 16, LY + 1, cz - 14)


def kolam_darah(k, x0, z0):
    """Blood pool (iblis slot D): a pool of red concrete rimmed with crimson, chains from a gibbet frame, an iron cage on
    a chain over the pool, soul lanterns; guard points (38.C.3.2 kolam_darah)."""
    cx, cz = x0 + 22, z0 + 22
    for x in range(cx - 14, cx + 15):
        for z in range(cz - 12, cz + 13):
            d = ((x - cx) / 14.0) ** 2 + ((z - cz) / 12.0) ** 2
            if d <= 0.78:
                k.put(x, LY, z, "minecraft:red_concrete" if (x * 3 + z) % 7 else "minecraft:red_concrete_powder")
            elif d <= 1.0:
                k.put(x, LY, z, "minecraft:crimson_nylium")
                k.put(x, LY + 1, z, "minecraft:nether_brick_fence")
    for x in (cx - 6, cx + 6):
        for dy in range(1, 11):
            k.put(x, LY + dy, cz, "minecraft:crimson_hyphae", {"axis": "y"})
    for x in range(cx - 6, cx + 7):
        k.put(x, LY + 11, cz, "minecraft:crimson_hyphae", {"axis": "x"})
    for dy in range(7, 11):
        k.put(cx, LY + dy, cz, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            for dy in (4, 5, 6):
                if x != cx or z != cz or dy == 6:
                    k.put(x, LY + dy, z, "minecraft:iron_bars")
    for dx, dz in ((-15, -13), (15, -13), (-15, 13), (15, 13)):
        K.post(k, cx + dx, LY + 1, cz + dz, height=3, role="minecraft:soul_lantern", post_role="minecraft:nether_brick_fence")
    for x, z, hd in ((cx, cz + 14, "north"), (cx - 16, cz, "east"), (cx + 16, cz, "west")):
        k.titik("jaga", x, LY + 1, z, hd)


def paviliun_sutra(k):
    """Sutra lecture hall (dalam lot (1,1)): a teacher's dais, three rows of low desks with cushions, scroll racks."""
    a = K.aula(k, 56, 56, 87, 82, h=7, podium_h=1, teras=2, roof="hip", tiers=1, door_sides=("south", "north"), door_w=3)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for x in range(68, 76):
        k.put(x, f + 1, iz0 + 1, "kerb")
    K.low_table(k, 72, f + 2, iz0 + 2)
    k.titik("meja_tulis", 72, f + 2, iz0 + 1, "south")
    n = 0
    for z in range(iz0 + 6, iz1 - 1, 4):
        for x in range(ix0 + 3, ix1 - 1, 4):
            if abs(x - 72) <= 1:
                continue
            K.low_table(k, x, f + 1, z)
            if n < 18:
                bantal(k, x, f + 1, z + 1, "north")
                n += 1
    K.bookshelf_wall(k, ix0, iz0 + 2, ix0, iz1 - 2, f + 1, f + 3, face="east")
    K.bookshelf_wall(k, ix1, iz0 + 2, ix1, iz1 - 2, f + 1, f + 3, face="west")


def gua_meditasi(k, x0, z0, w=40, d=26, n=6):
    """Meditation caves (38.C.3.2 gua_meditasi): an artificial cliff of stone and moss with a row of 5 x 4 x 5 niches, a
    cushion and a lamp in each, a thin waterfall into a basin, ferns and dripleaf."""
    top = LY + 12
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            hgt = top - int(abs(z - (z0 + d * 0.35)) * 0.6) + ((x * 7 + z * 3) % 3)
            for y in range(LY + 1, min(hgt, top + 2)):
                k.put(x, y, z, ("minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:mossy_cobblestone")[(x + y * 3 + z) % 4])
            if hgt > LY + 1:
                k.put(x, min(hgt, top + 2), z, "minecraft:moss_block")
    step = (w - 4) // n
    for i in range(n):
        nx = x0 + 3 + i * step
        nz = z0 + d - 7
        for x in range(nx, nx + 5):
            for z in range(nz, nz + 5):
                for y in range(LY + 1, LY + 5):
                    k.put(x, y, z, "minecraft:air")
                k.put(x, LY, z, "minecraft:mossy_stone_bricks" if (x + z) % 3 else "kerb")
        bantal(k, nx + 2, LY + 1, nz + 1, "south", "meditasi")
        k.put(nx + 2, LY + 4, nz + 1, "glow", {"hanging": "true", "waterlogged": "false"})
        k.put(nx, LY + 1, nz + 4, "minecraft:fern")
    wx = x0 + w // 2
    for y in range(LY + 1, top + 1):
        k.put(wx, y, z0 + d - 9, "minecraft:water", {"level": "0"})
    for x in range(wx - 2, wx + 3):
        for z in range(z0 + d - 3, z0 + d):
            k.put(x, LY, z, "minecraft:water", {"level": "0"})
            k.put(x, LY - 1, z, "minecraft:clay")
    k.put(x0 + 1, LY + 1, z0 + d - 1, "minecraft:big_dripleaf", {"facing": "south", "tilt": "none", "waterlogged": "false"})


def menara_lonceng(k, x0, z0, bell=True):
    """Bell (lurus) or drum (netral/iblis) tower (dalam lot (2,1)): a nine-wide pagoda tower 24 high with the bell or a
    great drum in its open top storey; ladder inside."""
    top = K.pagoda(k, x0, z0, 9, 3, sh=5, y=LY, base_h=2, top="spire", door="south")
    deck = LY + 2 + 3 * 6
    if bell:
        k.put(x0 + 4, deck + 3, z0 + 4, "minecraft:bell", {"attachment": "ceiling", "facing": "south", "powered": "false"})
    else:
        K.drum(k, x0 + 4, deck + 1, z0 + 4, "south")
    k.titik("jaga", x0 + 4, LY + 3, z0 + 11, "south")
    return top


def taman_dalam(k):
    """Inner garden (dalam lot (1,0)): the founder's statue on a lotus plinth, pines, a winding gravel walk and a
    stream with a stone bridge."""
    K.garden(k, 50, 4, 93, 44, LY, trees=("pine", "pine", "cherry"), pond_too=True)
    for x in range(69, 76):
        for z in range(14, 21):
            k.put(x, LY + 1, z, "kerb_chisel" if (x + z) % 2 else "kerb")
    for dy in range(2, 7):
        k.put(72, LY + dy, 17, "statue" if dy < 6 else "statue_top")
        if dy in (3, 4):
            k.put(71, LY + dy, 17, "statue")
            k.put(73, LY + dy, 17, "statue")
    k.stairs(72, LY + 7, 17, "kerb_stairs", "south")
    K.incense_burner(k, 72, LY + 1, 24)
    k.titik("dupa", 72, LY + 1, 26, "north")
    k.titik("obrol", 60, LY + 1, 30, "east")


def makam_leluhur(k):
    """Ancestors' tombs (lurus/netral slot F, dalam lot (0,0)): three earth mounds with stone steles, an offering table,
    lantern posts and dark pines."""
    for i, cx in enumerate((12, 24, 36)):
        cz = 18 if i != 1 else 14
        for x in range(cx - 4, cx + 5):
            for z in range(cz - 4, cz + 5):
                d = math.hypot(x - cx, z - cz)
                if d <= 4.2:
                    hh = 1 + int(max(0, 3.5 - d))
                    for y in range(LY + 1, LY + 1 + hh):
                        k.put(x, y, z, "minecraft:rooted_dirt" if y < LY + hh else "minecraft:grass_block", {"snowy": "false"} if y == LY + hh else None)
        k.put(cx, LY + 1, cz + 5, "kerb_chisel")
        k.put(cx, LY + 2, cz + 5, "statue")
        k.slab(cx, LY + 3, cz + 5, "kerb_slab")
        k.put(cx, LY + 1, cz + 7, "minecraft:white_candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
        k.titik("dupa", cx, LY + 1, cz + 8, "north")
        K.pine(k, cx - 6, LY + 1, cz - 4, 10)
    for x in (8, 40):
        K.stone_lantern(k, x, LY + 1, 32)
    # offering slabs before every stele: an ash bowl, a candle, white flowers in pots
    for i, cx in enumerate((12, 24, 36)):
        cz = 18 if i != 1 else 14
        for dx in (-1, 0, 1):
            k.slab(cx + dx, LY + 1, cz + 6, "kerb_slab", "top")
        k.put(cx, LY + 2, cz + 6, "minecraft:flower_pot")
        k.put(cx - 1, LY + 2, cz + 6, "minecraft:white_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
        k.put(cx + 1, LY + 2, cz + 6, ("minecraft:potted_white_tulip", "minecraft:potted_oxeye_daisy", "minecraft:potted_lily_of_the_valley")[i])
    # a low stone fence round the tomb ground, open to the spirit way
    for x in range(4, 45):
        k.put(x, LY + 1, 4, "kerb_wall")
        if not 21 <= x <= 27:
            k.put(x, LY + 1, 28, "kerb_wall")
    for z in range(4, 29):
        k.put(4, LY + 1, z, "kerb_wall")
        k.put(44, LY + 1, z, "kerb_wall")
    # the spirit way: a paved walk between stone guardians and white mourning banners
    for z in range(29, 45):
        for x in range(21, 28):
            k.put(x, LY, z, "kerb" if x in (21, 27) else ("kerb_chisel" if x == 24 and z % 4 == 0 else "path"))
    for z in (33, 39):
        K.lion(k, 19, LY, z, "east")
        K.lion(k, 29, LY, z, "west")
    for x in (19, 29):
        K.banner_pole(k, x, LY + 1, 43, height=4, color="white")
    k.titik("jaga", 24, LY + 1, 31, "south")
    k.titik("kerja:sapu", 16, LY + 1, 30, "north")


def lubang_tulang(k):
    """Bone pit (iblis slot F, dalam lot (0,0)): a sunken pit ringed with bone pillars and skulls, chains down, soul
    lanterns; where the sect throws what it does not need."""
    cx, cz = 23, 21
    for x in range(cx - 10, cx + 11):
        for z in range(cz - 10, cz + 11):
            d = math.hypot(x - cx, z - cz)
            if d <= 7.5:
                for y in range(LY - 3, LY + 1):
                    k.put(x, y, z, "minecraft:air")
                k.put(x, LY - 4, z, "minecraft:soul_soil")
                if (x + z) % 4 == 0:
                    k.put(x, LY - 3, z, "minecraft:bone_block", {"axis": "x" if x % 2 else "z"})
            elif d <= 9:
                k.put(x, LY, z, "minecraft:polished_blackstone_bricks")
    for i in range(8):
        a = i * math.pi / 4
        x = int(round(cx + math.cos(a) * 9))
        z = int(round(cz + math.sin(a) * 9))
        for dy in range(1, 5):
            k.put(x, LY + dy, z, "minecraft:bone_block", {"axis": "y"})
        k.put(x, LY + 5, z, "minecraft:skeleton_skull", {"rotation": str((i * 2) % 16), "powered": "false"})
        k.put(x, LY + 4, z + (1 if z < cz else -1), "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    k.titik("jaga", cx, LY + 1, cz + 11, "north")


def aula_harta(k):
    """Treasury (dalam lot (2,0)): a squat stone strongroom with iron doors, chests, a gold-plated altar for the sect's
    relics and two guard posts."""
    f = K.podium(k, 102, 8, 136, 34, LY, 2, steps=("south",), step_w=3)
    K.hall(k, 103, 9, 135, 33, f, 6, door_sides=("south",), door_w=2, windows=0, wall_role="kerb", pillar_role="red", doors=False)
    for dx in (0, 1):
        k.put(119 + dx, f + 1, 33, "minecraft:iron_door", {"facing": "south", "half": "lower", "hinge": "left" if dx == 0 else "right", "open": "false", "powered": "false"})
        k.put(119 + dx, f + 2, 33, "minecraft:iron_door", {"facing": "south", "half": "upper", "hinge": "left" if dx == 0 else "right", "open": "false", "powered": "false"})
    for x in range(105, 134, 2):
        K.chest(k, x, f + 1, 10, None, "south")
    for x in range(115, 125):
        k.put(x, f + 1, 20, "gold")
    k.put(119, f + 2, 20, "minecraft:enchanting_table")
    # relic pedestals in two rows: crystal clusters, sealed urns and the sect's old blades on stands
    for i, (x, z) in enumerate(((107, 15), (112, 15), (126, 15), (131, 15), (107, 26), (112, 26), (126, 26), (131, 26))):
        k.put(x, f + 1, z, "kerb_chisel")
        kind = i % 4
        if kind == 0:
            k.put(x, f + 2, z, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
        elif kind == 1:
            k.put(x, f + 2, z, "minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})
        elif kind == 2:
            boneka(k, x + 0.5, f + 2, z + 0.5, 0.0, ("golden_sword", "diamond_sword")[i // 4])
        else:
            k.put(x, f + 2, z, "minecraft:lodestone")
    # heaps of spirit stones (amethyst) and gold, the treasurer's steelyard and ledger, a runner to the altar, lamps
    for (x, z) in ((133, 30), (133, 31), (132, 31)):
        k.put(x, f + 1, z, "minecraft:amethyst_block")
    k.put(133, f + 2, 31, "minecraft:amethyst_block")
    for (x, z) in ((105, 30), (105, 31), (106, 31)):
        k.put(x, f + 1, z, "gold")
    k.put(110, f + 1, 21, "wood_fence")
    k.put(110, f + 2, 21, "minecraft:light_weighted_pressure_plate", {"power": "0"})
    k.titik("kerja:timbangan", 110, f + 1, 22, "north")
    k.put(128, f + 1, 21, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    k.titik("meja_tulis", 128, f + 1, 22, "north")
    for z in range(22, 33):
        k.put(119, f + 1, z, "rug")
        k.put(120, f + 1, z, "rug")
    for (x, z) in ((110, 14), (128, 14), (110, 27), (128, 27)):
        K.lit(k, x, f + 6, z, chain=1, role="lamp")
    k.titik("jaga", 117, f + 1, 31, "north")
    K.roof_over(k, 102, 8, 136, 34, f + 7, kind="hip", overhang=1, top="pearl")
    k.titik("jaga", 116, LY + 1, 37, "south")
    k.titik("jaga", 123, LY + 1, 37, "south")


# ===================================================================================================== PUNCAK
def pelataran_ketua(k):
    """Master's forecourt (puncak lot (1,1)): the processional way up to the summit hall with lions, censers and plum
    trees; the stair from below arrives at its south edge."""
    K.lion(k, 64, LY, 56, "south")
    K.lion(k, 79, LY, 56, "south")
    for z in (64, 80):
        K.incense_burner(k, 60, LY + 1, z)
        K.incense_burner(k, 84, LY + 1, z)
    for z in (60, 72, 84):
        K.cherry(k, 54, LY + 1, z, 5)
        K.cherry(k, 90, LY + 1, z, 5)
    K.paifang(k, 71, 88, LY, width=13, height=8, facing="south")
    # the great censer on the way, twin columns, flower beds under the plum trees, the sect's banners at the gate
    K.incense_burner(k, 72, LY + 1, 70, big=True)
    k.titik("dupa", 72, LY + 1, 73, "north")
    K.huabiao(k, 60, LY, 72, height=8)
    K.huabiao(k, 84, LY, 72, height=8)
    bedeng(k, 49, 62, 51, 82)
    bedeng(k, 92, 62, 94, 82)
    K.banner_pole(k, 63, LY + 1, 92, height=5)
    K.banner_pole(k, 80, LY + 1, 92, height=5)
    k.titik("jaga", 66, LY + 1, 90, "south")
    k.titik("jaga", 77, LY + 1, 90, "south")
    k.titik("obrol", 58, LY + 1, 72, "east")


def rumah_tetua(k, x0, flip=False):
    """An elder's courtyard house (puncak lots (0,1) and (2,1)): bedroom, study with scrolls and a tea room around a
    rock garden; anchor kompleks/tetua (the elder receives visitors here)."""
    K.yard_wall(k, x0, 52, x0 + 38, 92, LY, h=3, gate="east" if not flip else "west", gate_w=3)
    f = K.podium(k, x0 + 4, 55, x0 + 34, 68, LY, 1, steps=("south",), step_w=3)
    inner = K.hall(k, x0 + 5, 56, x0 + 33, 67, f, 6, door_sides=("south",), door_w=2, windows=4)
    ix0, iz0, ix1, iz1 = inner
    K.bed(k, ix0 + 1, f + 1, iz0, "north")
    k.titik("kasur", ix0 + 1, f + 1, iz0, "south")
    K.bookshelf_wall(k, ix1, iz0, ix1, iz0 + 4, f + 1, f + 3, face="west")
    k.put(ix1 - 2, f + 1, iz0 + 2, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    k.titik("meja_tulis", ix1 - 3, f + 1, iz0 + 2, "east")
    K.low_table(k, (ix0 + ix1) // 2, f + 1, (iz0 + iz1) // 2)
    for dx in (-1, 1):
        k.put((ix0 + ix1) // 2 + dx, f + 1, (iz0 + iz1) // 2, "rug")
        k.titik("kursi_lantai", (ix0 + ix1) // 2 + dx, f + 1, (iz0 + iz1) // 2, "west" if dx > 0 else "east")
    # a paper screen before the bed, the wardrobe and chest, an altar shelf, a scroll, lamps, a rug under the tea table
    for z in (iz0, iz0 + 1, iz0 + 2):
        k.put(ix0 + 3, f + 1, z, "wood_fence")
        k.put(ix0 + 3, f + 2, z, "rug_alt")
    K.barrel(k, ix0, f + 1, iz0 + 4, "east")
    K.barrel(k, ix0, f + 2, iz0 + 4, "east")
    K.chest(k, ix0, f + 1, iz0 + 5, None, "east")
    k.titik("peti_milik", ix0 + 1, f + 1, iz0 + 5, "west")
    mx, mz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    K.rug(k, mx - 2, mz - 1, mx + 2, mz + 1, f + 1)
    K.low_table(k, mx, f + 1, mz)
    k.put(mx, f + 2, mz, "minecraft:flower_pot")
    for dx in (-1, 1):
        k.put(mx + dx, f + 1, mz, "rug")
    k.put(mx, f + 1, iz0, "wood")
    k.put(mx, f + 2, iz0, "minecraft:white_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    K.wall_banner(k, mx, f + 4, iz0, "south", "white")
    K.pot(k, ix1, f + 1, iz1, "minecraft:potted_bamboo")
    K.pot(k, ix0, f + 1, iz1, "minecraft:potted_azalea_bush")
    for x in (ix0 + 4, ix1 - 4):
        K.lit(k, x, f + 6, mz, chain=1, role="lamp")
    K.roof_over(k, x0 + 4, 55, x0 + 34, 68, f + 7, kind="hip", overhang=1)
    # a stone table for weiqi in the rock garden, with two stools
    k.slab(x0 + 12, LY + 1, 84, "kerb_slab", "top")
    k.put(x0 + 12, LY + 2, 84, "minecraft:white_carpet")
    for dx, hd in ((-1, "east"), (1, "west")):
        k.slab(x0 + 12 + dx, LY + 1, 84, "kerb_slab", "bottom")
        k.titik("obrol", x0 + 12 + dx, LY + 1, 84, hd)
    # rock garden
    for x in range(x0 + 5, x0 + 34):
        for z in range(72, 88):
            k.put(x, LY, z, "minecraft:gravel" if (x + z) % 7 else "kerb")
    for (bx, bz) in ((x0 + 10, 76), (x0 + 22, 82), (x0 + 29, 75)):
        batu_hias(k, bx, LY + 1, bz, 3)
    K.pine(k, x0 + 16, LY + 1, 77, 8)
    k.jangkar(x0 + 19.5, f + 1, iz1 - 1.5, "kompleks/tetua", yaw=180.0)


def gua_puncak(k):
    gua_meditasi(k, 4, 6, w=40, d=26, n=5)


def paviliun_pandang(k):
    """View pavilion (puncak lot (2,0)): an eight-pillared pavilion on the cliff edge with a double roof, stone seats
    looking out over the sect (titik pandang)."""
    f = K.podium(k, 108, 10, 130, 32, LY, 2, steps=("south",), step_w=5)
    for x in (110, 119, 128):
        for z in (12, 21, 30):
            if (x, z) == (119, 21):
                continue
            for dy in range(1, 7):
                k.put(x, f + dy, z, "red")
    K.beam_band(k, 110, 12, 128, 30, f + 7)
    K.roof_over(k, 110, 12, 128, 30, f + 8, kind="hip", tiers=2, overhang=2, top="spire")
    for x in range(111, 128):
        k.put(x, f + 1, 12, "rail")
    for x, z, hd in ((114, 14, "north"), (119, 14, "north"), (124, 14, "north"), (112, 21, "west")):
        k.stairs(x, f + 1, z, "wood_stairs", OPP[hd])
        k.titik("pandang", x, f + 1, z, hd)
    # rails on the sides (the south steps stay open), a tea table with cushions, a calligraphy desk, bonsai, lamps
    for z in range(13, 30):
        if z != 21:
            k.put(110, f + 1, z, "rail")
            k.put(128, f + 1, z, "rail")
    for x in range(111, 128):
        if not 117 <= x <= 121:
            k.put(x, f + 1, 30, "rail")
    K.low_table(k, 119, f + 1, 24)
    k.put(119, f + 2, 24, "minecraft:flower_pot")
    for dx, hd in ((-1, "east"), (1, "west")):
        k.put(119 + dx, f + 1, 24, "rug")
        k.titik("kursi_lantai", 119 + dx, f + 1, 24, hd)
    k.put(125, f + 1, 26, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    k.titik("meja_tulis", 124, f + 1, 26, "east")
    for (x, z) in ((111, 29), (127, 29), (111, 13), (127, 13)):
        K.pot(k, x, f + 1, z, "minecraft:potted_azalea_bush" if (x + z) % 2 else "minecraft:potted_bamboo")
    for x in (114, 124):
        K.lit(k, x, f + 7, 21, chain=1, role="lamp")


def taman_teh(k):
    """Tea garden terraces (puncak lot (0,2), great band): rows of azalea bushes on low stone terraces, a tea hut."""
    for t, z0 in enumerate(range(100, 136, 6)):
        for x in range(6, 42):
            k.put(x, LY + (t % 2), z0, "kerb")
            for z in range(z0 + 1, z0 + 5):
                k.put(x, LY + (t % 2), z, "minecraft:grass_block", {"snowy": "false"})
                if z == z0 + 2 and x % 2 == 0:
                    k.put(x, LY + 1 + (t % 2), z, "bush" if x % 4 else "minecraft:flowering_azalea")
        k.titik("kerja:sabit", 8 + t * 5, LY + 1 + (t % 2), z0 + 3, "north")
    f = K.podium(k, 30, 128, 41, 138, LY, 1, steps=("west",), step_w=3)
    K.hall(k, 31, 129, 40, 137, f, 4, door_sides=("west",), door_w=2, windows=3)
    K.low_table(k, 35, f + 1, 133)
    K.gable(k, 30, f + 5, 128, 41, 138, along="z", overhang=1)


def makam_pendiri(k):
    """The founder's tomb (puncak lot (2,2), great band): a stepped stone mound, a tall stele on a tortoise plinth,
    stone guardians along the spirit way, lanterns."""
    cx, cz = 120, 112
    for r in range(8, 0, -2):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                k.put(x, LY + (8 - r) // 2 + 1, z, "kerb" if r % 4 == 0 else "minecraft:grass_block", {"snowy": "false"} if r % 4 else None)
    # the stone tortoise (bixi) that carries the stele: shell, legs and head
    k.put(cx, LY + 5, cz, "kerb_chisel")
    for d, (dx, dz) in K.FACE.items():
        k.stairs(cx + dx, LY + 5, cz + dz, "kerb_stairs", K.OPP[d])
    k.put(cx, LY + 5, cz + 2, "statue_top")
    for dy in range(1, 9):
        k.put(cx, LY + 5 + dy, cz, "statue" if dy < 8 else "statue_top")
    for z in range(cz + 10, 140, 6):
        K.lion(k, cx - 4, LY, z, "east")
        K.lion(k, cx + 4, LY, z, "west")
        K.stone_lantern(k, cx - 6, LY + 1, z + 2)
        K.stone_lantern(k, cx + 6, LY + 1, z + 2)
    # the offering altar, the great censer and the twin columns before the mound; a grove of pines behind it
    for dx in (-1, 0, 1):
        k.slab(cx + dx, LY + 1, cz + 10, "kerb_slab", "top")
    k.put(cx, LY + 2, cz + 10, "minecraft:flower_pot")
    k.put(cx - 1, LY + 2, cz + 10, "minecraft:white_candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
    k.put(cx + 1, LY + 2, cz + 10, "minecraft:white_candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
    K.incense_burner(k, cx, LY + 1, cz + 15, big=True)
    K.huabiao(k, cx - 8, LY, cz + 9, height=8)
    K.huabiao(k, cx + 8, LY, cz + 9, height=8)
    for (x, z) in ((cx - 12, cz - 6), (cx + 12, cz - 6), (cx - 12, cz + 3), (cx + 12, cz + 3), (cx, cz - 12)):
        K.pine(k, x, LY + 1, z, 9)
    for x in (cx - 2, cx + 2):
        K.banner_pole(k, x, LY + 1, 139, height=4, color="white")
    k.titik("dupa", cx, LY + 1, cz + 11, "north")
    k.titik("dupa", cx - 2, LY + 1, cz + 12, "north")
    k.titik("dupa", cx + 2, LY + 1, cz + 12, "north")


def taman_tiba(k):
    """Arrival garden (puncak lot (1,2), great band): the stair from the inner court ends in a garden with a paifang
    and a reflecting pool."""
    K.paifang(k, 71, 118, LY, width=13, height=8, facing="south")
    K.pond(k, 58, 108, 4, 3, LY, lilies=4)
    K.pond(k, 86, 108, 4, 3, LY, lilies=4)
    for z in (100, 128):
        K.stone_lantern(k, 62, LY + 1, z)
        K.stone_lantern(k, 82, LY + 1, z)
    # plum trees and stone benches where arrivals rest after the climb; flower beds by the ponds
    for x in (52, 92):
        K.cherry(k, x, LY + 1, 122, 5)
    for x, hd in ((55, "east"), (89, "west")):
        for z in (114, 116):
            k.stairs(x, LY + 1, z, "kerb_stairs", OPP[hd])
            k.titik("kursi", x, LY + 1, z, hd)
    bedeng(k, 54, 102, 62, 104)
    bedeng(k, 82, 102, 90, 104)
    k.titik("obrol", 60, LY + 1, 120, "east")
    k.titik("obrol", 84, LY + 1, 120, "west")
    k.titik("tiba", 72, LY + 1, 132, "north")


# ===================================================================================================== districts
def halaman(kecil=False, langit=False, slot=None):
    """The outer court. slot = (A, B, C) builders for the alignment lots, or None for the shared lots only."""
    rows = 3
    name = "halaman"
    k = kanvas(name)
    tanah(k, rows)
    tembok_sisi(k)
    tembok_lintang(k, 141, gap=(57, 86))
    gerbang_halaman(k)
    asrama_luar(k)
    dapur_makan(k)
    lapangan_latihan(k)
    aula_utama(k)
    if kecil:
        aula_puncak_kecil(k, langit)
    else:
        gudang_pelayan(k)
    if slot:
        slot[0](k)
        slot[1](k)
        slot[2](k)
    return k


def dalam(slot=None):
    k = kanvas("dalam")
    tanah(k, 3)
    tembok_sisi(k)
    gerbang_dalam(k)
    asrama_dalam(k)
    paviliun_sutra(k)
    gua_meditasi(k, 4, 56, w=40, d=26, n=6)
    taman_dalam(k)
    aula_harta(k)
    if slot:
        for fn in slot:
            fn(k)
    return k


def puncak(rows, langit=False):
    """The summit: rows 0-1 (hall, caves, view pavilion or cloud pier, forecourt, elders' houses) are the same for both
    bands; the great band adds row 2 (tea terraces, arrival garden, founder's tomb)."""
    k = kanvas("puncak_%d" % rows, rows=rows)
    tanah(k, rows)
    tembok_sisi(k)
    tembok_lintang(k, 0)
    aula_puncak(k, 54, 6, 89, 36)
    gua_puncak(k)
    if langit:
        dermaga_awan(k, 108, 6, 138, 40)
    else:
        paviliun_pandang(k)
    pelataran_ketua(k)
    rumah_tetua(k, 4)
    rumah_tetua(k, 101, flip=True)
    if rows == 3:
        taman_teh(k)
        taman_tiba(k)
        makam_pendiri(k)
    return k


# ----------------------------------------------------------------------------------------- stairs and terrace walls
def tangga(naik):
    """The stair piece between two districts: 9 wide, climbing `naik` blocks northwards from the lower floor (template
    y = LY) to the upper floor (y = LY + naik): a foot row with a small gate (gapura), the flight with a three-row landing
    and a second gate after every 12 steps, a two-row top landing; stone flanks with a balustrade and lamps every five
    rows (38.C.3.1 tangga_gunung)."""
    seq = [0]
    for s_ in range(1, naik + 1):
        seq.append(s_)
        if s_ % 12 == 0 and s_ < naik:
            seq += [s_] * 3
    seq += [naik, naik]
    run = len(seq)
    lot = tl.Lot(F, "tangga_%d" % naik, tinggi=LY + naik + 9, lebar=9, dalam=run, palet=BASE)
    gates = [run - 1]
    for idx, h in enumerate(seq):
        z = run - 1 - idx
        stepped = idx > 0 and seq[idx - 1] < h
        for x in range(1, 8):
            for yy in range(0, LY + h):
                lot.put(x, yy, z, "kerb")
            if stepped:
                lot.stairs(x, LY + h, z, "kerb_stairs", "north")
            else:
                lot.put(x, LY + h, z, "path" if 2 <= x <= 6 else "kerb")
            for yy in range(LY + h + 1, LY + naik + 5):
                lot.put(x, yy, z, "minecraft:air")
        for x in (0, 8):
            for yy in range(0, LY + h + 1):
                lot.put(x, yy, z, "kerb_chisel" if yy == LY + h and z % 5 == 0 else "kerb")
            lot.put(x, LY + h + 1, z, "kerb_wall")
            if z % 5 == 2:
                lot.put(x, LY + h + 2, z, "glow", {"hanging": "false", "waterlogged": "false"})
        if idx > 0 and seq[idx - 1] == h and h % 12 == 0 and 0 < h < naik and (idx < 2 or seq[idx - 2] < h):
            gates.append(z)
    for zz in gates:
        hh = seq[run - 1 - zz]
        for x in (0, 8):
            for dy in range(1, 6):
                lot.put(x, LY + hh + dy, zz, "red")
        for x in range(9):
            lot.put(x, LY + hh + 6, zz, "beam", {"axis": "x"})
            lot.stairs(x, LY + hh + 7, zz, "tile_stairs", "south")
        lot.put(4, LY + hh + 6, zz, "gold")
    lot.titik("jalan", 4, LY + 1, run - 1, "north", {"urut": 0, "jalur": "tangga"})
    lot.titik("jalan", 4, LY + naik + 1, 0, "north", {"urut": 1, "jalur": "tangga"})
    return simpan_lot(lot), run


def tebing(naik, tengah):
    """A terrace wall piece: 48 long, 3 deep, from the lower floor up to the upper floor (the upper district's south
    walkway), buttresses every 8, a balustrade, hanging lanterns and moss; tengah leaves the 9-wide stair gap."""
    lot = tl.Lot(F, "tebing_%d_%s" % (naik, "tengah" if tengah else "sisi"), tinggi=LY + naik + 3, lebar=48, dalam=3, palet=BASE)
    top = LY + naik
    for x in range(48):
        if tengah and 20 <= x <= 28:
            continue
        for z in range(3):
            for yy in range(0, top + 1):
                role = "kerb"
                if z == 2 and (yy - LY) % 5 == 4:
                    role = "kerb_chisel"
                elif z == 2 and (x * 3 + yy) % 11 == 0:
                    role = "kerb_alt"
                lot.put(x, yy, z, role)
            lot.put(x, top, z, "path" if z < 2 else "kerb")
        lot.put(x, top + 1, 2, "kerb_wall")
        if x % 8 == 4:
            lot.put(x, top + 1, 2, "kerb_chisel")
            lot.put(x, top + 2, 2, "glow", {"hanging": "false", "waterlogged": "false"})
            lot.put(x, top - 2, 2, "kerb_chisel")
        if x % 6 == 1:
            for yy in range(top - 3, top):
                lot.put(x, yy, 2, "minecraft:mossy_stone_bricks")
    return simpan_lot(lot)


def belakang_kecil():
    """The back wall of a small sect's only court (three pieces along the north edge of halaman_kecil)."""
    lot = tl.Lot(F, "belakang", tinggi=LY + 9, lebar=48, dalam=3, palet=BASE)
    for x in range(48):
        for z in range(3):
            lot.put(x, LY, z, "kerb")
            for dy in range(1, 7):
                role = "kerb" if dy <= 2 else ("beam" if dy == 6 else "plaster")
                lot.put(x, LY + dy, z, role, {"axis": "x"} if role == "beam" else None)
        lot.slab(x, LY + 7, 1, "tile_slab")
        lot.stairs(x, LY + 7, 0, "tile_stairs", "south")
        lot.stairs(x, LY + 7, 2, "tile_stairs", "north")
        if x % 12 == 6:
            lot.put(x, LY + 5, 3 - 1, "kerb_chisel")
    return simpan_lot(lot)


# ===================================================================================================== build all
NAMES3 = {(i, j): None for i in range(3) for j in range(3)}


def lot_names(prefix, rows=3, skip=()):
    return {(i, j): "%s_%d%d" % (prefix, i, j) for i in range(3) for j in range(rows) if (i, j) not in skip}


def build():
    # --- court: shared lots once, then each alignment's three slot lots, then the small band's master hall lot
    k = halaman()
    potong_distrik(k, "halaman", {(1, 0): "halaman/aula_utama", (1, 1): "halaman/lapangan_latihan",
                                  (2, 1): "halaman/gudang_pelayan", (0, 2): "halaman/asrama_luar",
                                  (1, 2): "halaman/gerbang", (2, 2): "halaman/dapur"})
    slots = {
        "lurus": (kebun_obat, paviliun_kitab, paviliun_alkimia),
        "netral": (pasar_sekte, paviliun_kitab, paviliun_alkimia),
        "iblis": (sangkar_tawanan, altar_iblis, aula_racun),
    }
    nama_slot = {kebun_obat: "kebun_obat", pasar_sekte: "pasar_sekte", sangkar_tawanan: "sangkar_tawanan",
                 paviliun_kitab: "paviliun_kitab", altar_iblis: "altar_iblis", paviliun_alkimia: "paviliun_alkimia",
                 aula_racun: "alkimia_racun"}
    for a in ALIRAN:
        fns = slots[a]
        for fn, ij in zip(fns, ((0, 1), (0, 0), (2, 0))):
            k = kanvas("halaman_slot_" + nama_slot[fn])
            tanah(k, 3)
            tembok_sisi(k)
            fn(k)
            only = None if a != "iblis" else ("iblis",)
            potong_distrik(k, ("halaman", a), {ij: "halaman/%s" % nama_slot[fn]}, aliran=only)
    for langit in (False, True):
        k = halaman(kecil=True, langit=langit)
        potong_distrik(k, ("halaman_kecil", langit), {(2, 1): "halaman/aula_ketua%s" % ("_langit" if langit else "")})
    # --- inner court (great band)
    k = dalam()
    potong_distrik(k, "dalam", {(1, 2): "dalam/gerbang_dalam", (0, 2): "dalam/asrama_dalam", (1, 1): "dalam/paviliun_sutra",
                                (0, 1): "dalam/gua_meditasi", (1, 0): "dalam/taman_dalam", (2, 0): "dalam/aula_harta"})
    dslots = {
        "lurus": ((lambda kk: kolam_roh(kk, 96, 96), "kolam_roh", (2, 2)), (lambda kk: menara_lonceng(kk, 115, 66, True), "menara_lonceng", (2, 1)),
                  (makam_leluhur, "makam_leluhur", (0, 0))),
        "netral": ((lambda kk: kolam_roh(kk, 96, 96), "kolam_roh", (2, 2)), (lambda kk: menara_lonceng(kk, 115, 66, False), "menara_genderang", (2, 1)),
                   (makam_leluhur, "makam_leluhur", (0, 0))),
        "iblis": ((lambda kk: kolam_darah(kk, 96, 96), "kolam_darah", (2, 2)), (lambda kk: menara_lonceng(kk, 115, 66, False), "menara_genderang", (2, 1)),
                  (lubang_tulang, "lubang_tulang", (0, 0))),
    }
    for a in ALIRAN:
        for fn, nm, ij in dslots[a]:
            k = kanvas("dalam_slot_" + nm)
            tanah(k, 3)
            tembok_sisi(k)
            fn(k)
            only = ("iblis",) if nm in ("kolam_darah", "lubang_tulang") else None
            potong_distrik(k, ("dalam", a), {ij: "dalam/%s" % nm}, aliran=only)
    # --- summit: 3 rows (great band) and 2 rows (middle band), each with and without the cloud pier
    for rows in (3, 2):
        for langit in (False, True):
            k = puncak(rows, langit)
            names = {(1, 0): "puncak/aula_puncak", (0, 0): "puncak/gua_puncak",
                     (2, 0): "puncak/dermaga_awan" if langit else "puncak/paviliun_pandang", (1, 1): "puncak/pelataran",
                     (0, 1): "puncak/rumah_tetua_barat", (2, 1): "puncak/rumah_tetua_timur"}
            if rows == 3:
                names.update({(0, 2): "puncak/taman_teh", (1, 2): "puncak/taman_tiba", (2, 2): "puncak/makam_pendiri"})
            potong_distrik(k, ("puncak", rows, langit), names)
    # --- climbs and walls
    for naik in (10, 8, 18):
        tid_tangga, run = tangga(naik)
        EXTRA[("tangga", naik)] = (tid_tangga, run)
        EXTRA[("tebing", naik, False)] = tebing(naik, False)
        EXTRA[("tebing", naik, True)] = tebing(naik, True)
    EXTRA["belakang"] = belakang_kecil()


def lot_of(dkey, i, j):
    t = LOTNAME.get((dkey, i, j))
    if t is None:
        raise KeyError("no lot %r %d %d" % (dkey, i, j))
    return t


def grid(rows, pick):
    """Grid lots of a district: pick(i, j) -> template id."""
    out = []
    half = rows * tl.LOT // 2
    for j in range(rows):
        for i in range(3):
            out.append({"dx": tl.LOT * i - 72, "dz": tl.LOT * j - half, "t": pick(i, j)})
    return out


def climb(rows, naik):
    """Stair + terrace walls along the south edge of an upper district (listed after the lots, dy = -naik)."""
    half = rows * tl.LOT // 2
    tt, run = EXTRA[("tangga", naik)]
    out = []
    for i, dx in enumerate((-72, -24, 24)):
        out.append({"dx": dx, "dz": half - 3, "dy": -naik, "t": EXTRA[("tebing", naik, i == 1)]})
    out.append({"dx": -4, "dz": half - run, "dy": -naik, "t": tt})
    return out


def distrik_lots(a):
    """{district id (without aliran): [lot dicts with template ids]} for alignment a."""
    def court(i, j, kecil=False, langit=False):
        if kecil and (i, j) == (2, 1):
            return lot_of(("halaman_kecil", langit), 2, 1)
        if (i, j) in ((0, 1), (0, 0), (2, 0)):
            return lot_of(("halaman", a), i, j)
        return lot_of("halaman", i, j)

    def inner(i, j):
        if (i, j) in ((2, 2), (2, 1), (0, 0)):
            return lot_of(("dalam", a), i, j)
        return lot_of("dalam", i, j)

    out = {}
    out["halaman"] = grid(3, lambda i, j: court(i, j))
    for langit in (False, True):
        back = [{"dx": dx, "dz": -75, "dy": 0, "t": EXTRA["belakang"]} for dx in (-72, -24, 24)]
        out["halaman_kecil" + ("_langit" if langit else "")] = grid(3, lambda i, j, l=langit: court(i, j, True, l)) + back
    out["dalam"] = grid(3, inner) + climb(3, 10)
    for langit in (False, True):
        suf = "_langit" if langit else ""
        out["puncak" + suf] = grid(3, lambda i, j, l=langit: lot_of(("puncak", 3, l), i, j)) + climb(3, 8)
        out["puncak_sedang" + suf] = grid(2, lambda i, j, l=langit: lot_of(("puncak", 2, l), i, j)) + climb(2, 18)
    return out


# ===================================================================================================== JSON
def _family(bid):
    n = bid.split(":")[-1]
    for suf in ("wall_banner", "banner", "stairs", "slab", "wall", "fence_gate", "fence", "pane", "bars", "trapdoor", "door",
                "pressure_plate", "carpet", "bed", "candle", "lantern", "shelf", "sign"):
        if n.endswith(suf):
            return suf
    return None


_BLOCKS = None


def _props_for(src, dst, props):
    """The properties of src that dst keeps: all of them within one family (stairs -> stairs), else only those the
    target's blockstate file names (axis of logs and pillars) - so nylium never gets 'snowy'."""
    global _BLOCKS
    if _BLOCKS is None:
        with open(structgen.BLOCK_LIST, encoding="utf-8") as f:
            _BLOCKS = json.load(f)
    if _family(src) is not None and _family(src) == _family(dst):
        return dict(props)
    known = set(_BLOCKS.get(dst, []))
    return {k: v for k, v in props if k in known}


def processor(aliran, states):
    swap = SWAP[aliran]
    rules = []
    for bid, props in sorted(set(states)):
        to = swap.get(bid)
        if to is None or to == bid:
            continue
        inp = {"id": bid}
        out = {"id": to}
        if props:
            inp["properties"] = {k: v for k, v in props}
        kept = _props_for(bid, to, props)
        if kept:
            out["properties"] = kept
        rules.append({"input_predicate": {"predicate_type": "minecraft:blockstate_match", "block_state": inp},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": out})
    lapuk = [("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.04),
             ("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.03),
             ("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.04)]
    for src, dst, p in lapuk:
        rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": swap.get(src, src), "probability": p},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": swap.get(dst, dst)}})
    return {"processors": [{"processor_type": "minecraft:rule", "rules": rules},
                           {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}]}


def tulis_semua():
    states = tl.semua_state()
    for a in ALIRAN:
        tl.tulis_processor("%s/gaya_%s" % (F, a), processor(a, states))
    pools = {}
    structures = {}
    for a in ALIRAN:
        proc = "%s%s/gaya_%s" % (NS, F, a)
        for d, lots in distrik_lots(a).items():
            out = []
            for l in lots:
                t = l["t"]
                pid = "%s/%s/%s" % (F, a, t.split("/", 1)[1].split(F + "/", 1)[-1].replace("/", "_"))
                if pid not in pools:
                    pools[pid] = (t, proc)
                lot = {"dx": l["dx"], "dz": l["dz"], "pool": NS + pid, "putar": 0}
                if l.get("dy"):
                    lot["dy"] = l["dy"]
                out.append(lot)
            structures["%s_%s" % (a, d)] = out
    for pid, (t, proc) in sorted(pools.items()):
        tl.tulis_pool(pid, [(tl.elemen(t, proc), 1)])
    rows_of = {"halaman": [], "dalam": [], "puncak": []}
    for sid, lots in sorted(structures.items()):
        a, d = sid.split("_", 1)
        s = tl.struktur_tata_letak("kompleks_sekte", sid, lots, "#kultivator:kompleks_sekte/semua")
        tl.tulis_wg("structure", "%s/%s" % (F, sid), s)
        rows_of[d.split("_")[0]].append(NS + F + "/" + sid)
    for row, ids in rows_of.items():
        st = {"placement": {"type": "kultivator:rencana_tempat", "salt": SALT[row], "spacing": 128, "separation": 0,
                            "rencana": "kompleks_sekte", "distrik": row},
              "structures": [{"structure": s, "weight": 1} for s in ids]}
        tl.tulis_wg("structure_set", "%s/%s" % (F, row), st, pack=PACK)
    tag("biome/kompleks_sekte/semua", ["#minecraft:is_overworld"])
    tag("structure/kompleks_sekte", sorted(NS + F + "/" + s for s in structures))
    pack_dir = os.path.join(structgen.REPO, "src", "main", "resources", "resourcepacks", PACK)
    structgen.write_json(os.path.join(pack_dir, "pack.mcmeta"),
                         {"pack": {"description": {"translate": "kultivator.dunia.pack.kompleks"},
                                   "min_format": 121, "max_format": 121}})
    return pools, structures


def tag(path, values):
    structgen.write_json(os.path.join(structgen.DATA, "tags", "worldgen", path + ".json"), {"replace": False, "values": values})


def main(argv):
    build()
    pools, structures = tulis_semua()
    tl.cek_semua()
    total = sum(os.path.getsize(p) for p, _ in structgen._SAVED)
    print("kompleks_sekte: %d templates, %d pools, %d structures, gzip %d bytes" % (len(structgen._SAVED), len(pools),
                                                                                    len(structures), total))
    if total > 1_200_000:
        raise SystemExit("kompleks_sekte: gzip %d > 1.2 MB budget (38.C.10)" % total)


if __name__ == "__main__":
    main(sys.argv[1:])
