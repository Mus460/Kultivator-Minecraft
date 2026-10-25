"""Kota Pelabuhan world generation (DESIGN 38.T.3, module 38TK-P): the harbour town's nine districts.

Writes every NBT lot (data/kultivator/structure/kota_tema/pelabuhan/**), the template pools (base + style "utara"),
the processor lists (worldgen/processor_list/kota_tema/pelabuhan/*) and the nine kultivator:tata_letak structures
(worldgen/structure/kota_tema/pelabuhan/*). The structure sets, the shared tags and the pack dunia_kotatema are 38TK
inti's (gen_tempat_tema_data.py, salts 380401-380409 from Tema.java). Run:

    python tools/gen_tempat_tema_pelabuhan.py        (then python tools/cek_tempat.py)

Plan (38.T.3.2, before rotation; the sea is grid north; district cells 96 blocks = 2 x 2 lots of 48):

             x = -1                x = 0                 x = +1
 z = -1   [dermaga_nelayan]    [dermaga_agung]       [galangan]          over the water (terrain_adaptation none)
 z =  0   [kota_awak]          [pasar_laut]          [gudang_dagang]     the shore (sea wall promenade)
 z = +1   [kuil_laut]          [gerbang_darat]       [hunian_saudagar]   land (land wall, the Land Gate to the road)

Sea lots are built with the deck at template y = PY (24) on piles reaching y = 0 (lot dy = LY - PY), so a deck stands
1-8 blocks over the water and the piles reach the sea floor; nothing but piles, rock and walls is written below the
deck, so the sea stays water around them (no air pockets; LiquidSettings.APPLY_WATERLOGGING wets the rest). The dry
dock is the one closed stone basin. Land lots follow tempat_lib (street floor y = 4). Everything is red-brick Minnan
style (polished diorite plinths, brick walls with white panels, granite-tile gable roofs with swallowtail ridge ends);
the "utara" style (cold_ocean, snowy_beach, taiga, snowy_plains) swaps to stone bricks, spruce and dark oak roofs.

Only world generation in NEW chunks ever places these templates (DESIGN 38.0 rule 1): ships, cranes, the lamp, the
slipway hulls and the dock gate are noSave entities spawned from the anchors below (module "pelabuhan", served by
dev.kultivator.dunia.tempat.tema.pelabuhan). Deterministic: the same bytes on every run.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import kota_besar_kit as K  # noqa: E402
import hias_tema as H  # noqa: E402
import tema_hidup_kit as thk  # noqa: E402  (DESIGN 42 lots outside the wall, 42W-T)
from kota_besar_kit import FACE, OPP  # noqa: E402

F = "kota_tema/pelabuhan"
NS = "kultivator:"
FAM = "kota_tema/pelabuhan"
RENCANA = "kota_tema_pelabuhan"
LY = tl.LANTAI_Y
PY = 24                 # deck floor of the sea lots (piles below)
DY_LAUT = LY - PY       # lot dy of the sea lots
SEL = 96
M = 2                   # wall piece margin
BAND = 6                # land wall band depth
WALL_H = 14             # land wall height over the street
WALK = 10               # wall walk level over the street

LAUT = ("dermaga_nelayan", "dermaga_agung", "galangan")
ORDER = ["dermaga_nelayan", "dermaga_agung", "galangan", "kota_awak", "pasar_laut", "gudang_dagang",
         "kuil_laut", "gerbang_darat", "hunian_saudagar"]

# Biomes where a district start may stand (the plan's terrain verdict decides first; caves for the start's y).
BIOMES = ["minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore", "minecraft:ocean", "minecraft:lukewarm_ocean",
          "minecraft:warm_ocean", "minecraft:cold_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:deep_ocean",
          "minecraft:deep_cold_ocean", "minecraft:river", "minecraft:plains", "minecraft:sunflower_plains",
          "minecraft:snowy_plains", "minecraft:forest", "minecraft:birch_forest", "minecraft:dappled_forest",
          "minecraft:flower_forest", "minecraft:savanna", "minecraft:sparse_jungle", "minecraft:jungle",
          "minecraft:meadow", "minecraft:cherry_grove", "minecraft:mangrove_swamp", "minecraft:swamp", "minecraft:taiga",
          "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
          "minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:sulfur_caves"]
# F2: district starts use the tag (written by gen_tempat_tema_data.py, extended for Terralith by gen_kompat_bioma)
# so a harbour in a modded-biome world keeps every district; BIOMES stays as the list of intended vanilla shores.
BIOMA_TAG = "#kultivator:has_structure/kota_tema/pelabuhan"
UTARA = ["minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:snowy_beach", "minecraft:taiga",
         "minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:old_growth_pine_taiga",
         "minecraft:old_growth_spruce_taiga"]

# ===================================================================================================== palette
PAL = dict(K.PAL)
PAL.update({
    "brick": "minecraft:bricks", "brick_stairs": "minecraft:brick_stairs", "brick_slab": "minecraft:brick_slab",
    "brick_wall": "minecraft:brick_wall",
    "roof": "minecraft:polished_granite_stairs", "roof_slab": "minecraft:polished_granite_slab",
    "roof_full": "minecraft:polished_granite",
    "base": "minecraft:polished_diorite", "base_stairs": "minecraft:polished_diorite_stairs",
    "base_slab": "minecraft:polished_diorite_slab",
    "deck": "minecraft:acacia_planks", "deck_alt": "minecraft:jungle_planks", "deck_slab": "minecraft:acacia_slab",
    "deck_stairs": "minecraft:acacia_stairs", "deck_fence": "minecraft:acacia_fence",
    "deck_trapdoor": "minecraft:acacia_trapdoor",
    "pile": "minecraft:stripped_spruce_log", "pile_alt": "minecraft:stripped_mangrove_log",
    "beam_log": "minecraft:stripped_acacia_log",
    "rock": "minecraft:cobblestone", "rock_alt": "minecraft:mossy_cobblestone", "rock_dark": "minecraft:andesite",
    "cloth": "minecraft:white_wool", "cloth_alt": "minecraft:light_blue_wool",
    "path": "minecraft:stone_bricks", "path_alt": "minecraft:polished_andesite",
    "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:stone_bricks",
    "bed": "minecraft:blue_bed", "rug": "minecraft:blue_carpet", "rug_alt": "minecraft:white_carpet",
    "banner": "blue", "banner_alt": "white",
    "copper": "minecraft:dark_prismarine", "copper_stairs": "minecraft:dark_prismarine_stairs",
    "copper_slab": "minecraft:dark_prismarine_slab",
})

# The style "utara" keeps the NBT and swaps blocks (processor rules, properties kept).
UTARA_GANTI = {}
UTARA_GANTI.update(tl.ganti_kayu("acacia", "spruce"))
UTARA_GANTI.update(tl.ganti_kayu("jungle", "spruce"))
UTARA_GANTI.update({
    "minecraft:bricks": "minecraft:stone_bricks", "minecraft:brick_stairs": "minecraft:stone_brick_stairs",
    "minecraft:brick_slab": "minecraft:stone_brick_slab", "minecraft:brick_wall": "minecraft:stone_brick_wall",
    "minecraft:polished_granite_stairs": "minecraft:dark_oak_stairs", "minecraft:polished_granite_slab": "minecraft:dark_oak_slab",
    "minecraft:polished_granite": "minecraft:dark_oak_planks", "minecraft:polished_diorite": "minecraft:stone_bricks",
    "minecraft:polished_diorite_stairs": "minecraft:stone_brick_stairs", "minecraft:polished_diorite_slab": "minecraft:stone_brick_slab",
    "minecraft:red_terracotta": "minecraft:brown_terracotta", "minecraft:blue_bed": "minecraft:brown_bed",
})
tl.GAYA[FAM] = {
    "dasar": {"tag": None, "ganti": {}},
    "utara": {"tag": None, "ganti": UTARA_GANTI},
}

# lots per district: list of dicts {dx, dz, dy, pool, putar}; pool id -> (templates, utuh)
LOTS = {d: [] for d in ORDER}
POOLS = {}


def tid(name):
    return "%s%s/%s" % (NS, F, name)


def pid(name):
    return "%s/%s" % (F, name)


def kanvas(distrik, h):
    return K.Kanvas(SEL, h, SEL, seed=tl._seed(F + "/" + distrik), palette=PAL)


# rooms to dress (D-TK1 detail pass): registered while building, dressed in simpan() after everything else stands, so
# the dressing only ever fills what is still empty (hias_tema.ruang never overwrites a placed block)
_RUANG = []

# rumah_merah house names -> room style per storey (last entry repeats upward)
GAYA_RUMAH = {"rumah_tukang_kapal": ("bengkel", "rumah"), "cabang_rumah_uang": ("kantor",), "bengkel_tukang": ("bengkel",),
              "pos_jaga_gerbang": ("gudang",), "rumah_pendeta": ("kuil", "rumah"), "rumah_zhao_induk": ("kantor", "rumah"),
              "gudang_jala": ("perahu",)}


def ruang(k, x0, z0, x1, z1, f, gaya, **kw):
    """Registers one room of canvas k for dressing (inner floor box, floor level f, hias_tema style)."""
    _RUANG.append((k, (x0, z0, x1, z1, f, gaya), kw))


def hias(k):
    """Dresses every registered room of canvas k (from simpan())."""
    n = 0
    for entry in [r for r in _RUANG if r[0] is k]:
        n += H.ruang(k, *entry[1], **entry[2])
        _RUANG.remove(entry)
    return n


def simpan(k, distrik, names, sisi, utuh=(), tinggi_min=24):
    """Cuts a district canvas into its four lots, writes their side codes and registers them. names[(i, j)] = lot name;
    sisi[(i, j)] = (n, e, s, w) before rotation."""
    hias(k)
    laut = distrik in LAUT
    full = {ij: "%s/%s" % (distrik, n) for ij, n in names.items()}
    lots = K.potong(k, full, tinggi_min=tinggi_min, folder=F)
    for ij, lot in sorted(lots.items()):
        lot.sisi(*sisi[ij])
        lot.simpan()
        entry = {"dx": -48 + 48 * ij[0], "dz": -48 + 48 * ij[1], "pool": pid(full[ij]), "putar": 0}
        if laut:
            entry["dy"] = DY_LAUT
        LOTS[distrik].append(entry)
        POOLS[pid(full[ij])] = ([tid(full[ij])], names[ij] in utuh)
    return lots


def daftar_bebas(distrik, dx, dz, name, putar):
    LOTS[distrik].append({"dx": dx, "dz": dz, "pool": pid(name), "putar": putar})
    POOLS.setdefault(pid(name), ([tid(name)], True))


# ===================================================================================================== small helpers
def stairs(p, x, y, z, role, facing, half="bottom", shape="straight"):
    p.put(x, y, z, role, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})


def slab(p, x, y, z, role, kind="bottom"):
    p.put(x, y, z, role, {"type": kind, "waterlogged": "false"})


def log(p, x, y, z, role, ax="y"):
    p.put(x, y, z, role, {"axis": ax})


def trapdoor(p, x, y, z, role, facing, half="top", open_="false"):
    p.put(x, y, z, role, {"facing": facing, "half": half, "open": open_, "powered": "false", "waterlogged": "false"})


def fence(p, x, y, z, role="deck_fence"):
    p.put(x, y, z, role)


def lantern(p, x, y, z, hanging=False, soul=False):
    p.put(x, y, z, "minecraft:soul_lantern" if soul else "minecraft:lantern",
          {"hanging": "true" if hanging else "false", "waterlogged": "false"})
    if hasattr(p, "lights"):
        p.lights.append((x, y, z))


def pot(p, x, y, z, cracked=False):
    p.put(x, y, z, "minecraft:decorated_pot", {"facing": "south", "cracked": "true" if cracked else "false",
                                               "waterlogged": "false"})


def carpet(p, x, y, z, color):
    p.put(x, y, z, "minecraft:%s_carpet" % color)


def candle(p, x, y, z, n=1, color="white"):
    p.put(x, y, z, "minecraft:%s_candle" % color, {"candles": str(n), "lit": "true", "waterlogged": "false"})


def tiang(p, x, z, top=PY - 1, role="pile"):
    """One pile from the sea floor (y 0) up to `top`."""
    for y in range(0, top + 1):
        log(p, x, y, z, role)


def geladak(p, x0, z0, x1, z1, y=PY, step=4, rim=True, arah="x", piles=True):
    """A plank deck at y on piles every `step` blocks (and on its rim), cross beams under it; never air below."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if rim and (z in (z0, z1)):
                log(p, x, y, z, "beam_log", "x")
            elif rim and x in (x0, x1):
                log(p, x, y, z, "beam_log", "z")
            else:
                row = z if arah == "x" else x
                p.put(x, y, z, "deck_alt" if row % 5 == 0 else "deck")
    if not piles:
        return
    xs = sorted(set(list(range(x0, x1 + 1, step)) + [x1]))
    zs = sorted(set(list(range(z0, z1 + 1, step)) + [z1]))
    for x in xs:
        for z in zs:
            tiang(p, x, z, y - 1, "pile" if (x // step + z // step) % 3 else "pile_alt")
    for z in zs:
        for x in range(x0, x1 + 1):
            if p.get(x, y - 1, z) is None:
                log(p, x, y - 1, z, "beam_log", "x")


def pagar(p, x0, z0, x1, z1, y, sisi="nesw", celah=(), role="deck_fence", lampu=10):
    """A railing on the named rim sides of a deck; `celah` = set of (x, z) left open; a lantern post every `lampu`."""
    cells = []
    for c in sisi:
        if c == "n":
            cells += [(x, z0) for x in range(x0, x1 + 1)]
        elif c == "s":
            cells += [(x, z1) for x in range(x0, x1 + 1)]
        elif c == "w":
            cells += [(x0, z) for z in range(z0, z1 + 1)]
        else:
            cells += [(x1, z) for z in range(z0, z1 + 1)]
    for i, (x, z) in enumerate(cells):
        if (x, z) in celah:
            continue
        fence(p, x, y + 1, z, role)
        if lampu and i % lampu == lampu // 2:
            fence(p, x, y + 2, z, role)
            lantern(p, x, y + 3, z)


def bollard(p, x, y, z):
    p.put(x, y + 1, z, "minecraft:andesite_wall")
    p.put(x, y + 2, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def ekor_walet(p, x0, z0, x1, z1, top, along="x", overhang=1):
    """Swallowtail ridge ends of a Minnan gable roof: the ridge rises and forks up at both ends (granite + end rods)."""
    if along == "x":
        rows = (z1 + overhang) - (z0 - overhang) + 1
        base = z0 - overhang + rows // 2
        mids = [base] if rows % 2 else [base - 1, base]
        for z in mids:
            for xe, d, out in ((x0 - overhang, -1, "west"), (x1 + overhang, 1, "east")):
                p.put(xe, top + 1, z, "roof_full")
                stairs(p, xe + d, top + 1, z, "roof", out)
                p.put(xe + d, top + 2, z, "minecraft:end_rod", {"facing": "up"})
                stairs(p, xe, top + 2, z, "roof", out, half="top")
    else:
        rows = (x1 + overhang) - (x0 - overhang) + 1
        base = x0 - overhang + rows // 2
        mids = [base] if rows % 2 else [base - 1, base]
        for x in mids:
            for ze, d, out in ((z0 - overhang, -1, "north"), (z1 + overhang, 1, "south")):
                p.put(x, top + 1, ze, "roof_full")
                stairs(p, x, top + 1, ze + d, "roof", out)
                p.put(x, top + 2, ze + d, "minecraft:end_rod", {"facing": "up"})
                stairs(p, x, top + 2, ze, "roof", out, half="top")


def atap(p, x0, z0, x1, z1, y, along=None, overhang=1, walet=True, tile=False):
    """A Minnan gable roof over the walls box (brackets under the eaves) with swallowtail ridge ends."""
    if along is None:
        along = "x" if (x1 - x0) >= (z1 - z0) else "z"
    K.brackets(p, x0, z0, x1, z1, y - 1)
    kw = {"role": "tile_stairs", "slab_role": "tile_slab", "full_role": "tile"} if tile else {}
    top = K.gable(p, x0, y, z0, x1, z1, along=along, overhang=overhang, pearl=False, **kw)
    if walet and not tile:
        ekor_walet(p, x0, z0, x1, z1, top, along, overhang)
    return top


def dinding_merah(p, x0, z0, x1, z1, f, h, jendela=3):
    """Minnan walls on floor f (standing level f+1 .. f+h): a diorite plinth, brick walls, red pillars on the corners,
    white plaster panels between y f+2 and f+h-1 on the long faces, lattice windows every `jendela`."""
    for y in range(f + 1, f + h + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                if corner:
                    p.put(x, y, z, "red")
                elif y == f + 1:
                    p.put(x, y, z, "base")
                elif y == f + h:
                    p.put(x, y, z, "brick")
                else:
                    along = (x - x0) if z in (z0, z1) else (z - z0)
                    span = (x1 - x0) if z in (z0, z1) else (z1 - z0)
                    if jendela and y in (f + 2, f + 3) and along % jendela == jendela // 2 and 1 < along < span - 1:
                        face = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
                        K.lattice(p, x, y, z, face)
                    elif 2 <= along <= span - 2 and f + 2 <= y <= f + h - 1 and (along // 3) % 2 == 1:
                        p.put(x, y, z, "plaster")
                    else:
                        p.put(x, y, z, "brick")
    p.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + h, z1 - 1)


def pintu(p, x0, z0, x1, z1, f, side, w=1, at=None):
    """A doorway (door blocks when 1-2 wide, open when wider) centred on `side` (or at `at` along it)."""
    cx = (x0 + x1) // 2 if at is None else at
    cz = (z0 + z1) // 2 if at is None else at
    half = w // 2
    for i in range(-half, w - half):
        for dy in (1, 2, 3):
            if side in ("north", "south"):
                p.put(cx + i, f + dy, z0 if side == "north" else z1, "minecraft:air")
            else:
                p.put(x0 if side == "west" else x1, f + dy, cz + i, "minecraft:air")
        if w <= 2:
            hinge = "left" if i == -half else "right"
            if side in ("north", "south"):
                K.door(p, cx + i, f + 1, z0 if side == "north" else z1, side, hinge)
            else:
                K.door(p, x0 if side == "west" else x1, f + 1, cz + i, side, hinge)
    dx, dz = FACE[side]
    if side in ("north", "south"):
        return cx, (z0 if side == "north" else z1) + dz
    return (x0 if side == "west" else x1) + dx, cz


def rumah_merah(p, x0, z0, x1, z1, f, tingkat=1, sisi_pintu="south", beds=2, nama="rumah", h=4, isi=True,
                jalan_masuk=True):
    """A Minnan red-brick house: plinth, brick walls with white panels, red corner pillars, lattice windows, a granite
    gable roof with swallowtail ends; inside a plank floor, beds, a family table, a chest and an altar shelf; two
    storeys with a ladder when tingkat 2. Returns {"inner": box, "lantai": [floors], "top": ridge y}."""
    floors = []
    for t in range(tingkat):
        ff = f + t * (h + 1)
        floors.append(ff)
        dinding_merah(p, x0, z0, x1, z1, ff, h)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                p.put(x, ff, z, "wood_alt" if t else "minecraft:spruce_planks")
        if t > 0:
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        p.put(x, ff, z, "beam", {"axis": "x" if z in (z0, z1) else "z"})
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    gaya = GAYA_RUMAH.get(nama, ("rumah",))
    for t, ff in enumerate(floors):
        ruang(p, ix0, iz0, ix1, iz1, ff, gaya[min(t, len(gaya) - 1)], isi=0.45)
    door = pintu(p, x0, z0, x1, z1, f, sisi_pintu, 1)
    top_y = f + tingkat * (h + 1)
    top = atap(p, x0, z0, x1, z1, top_y)
    if tingkat > 1:
        K.ladder(p, ix0, f + 1, f + h + 1, iz0, "south")
        p.put(ix0, f + h + 1, iz0, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    if isi:
        # ground floor: table and stools, an altar shelf with candles, a chest
        cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
        if ix1 - ix0 >= 3 and iz1 - iz0 >= 3:
            K.table(p, cx, f + 1, cz)
            p.titik("meja_makan", cx, f + 1, cz, "south")
            for d in ("east", "west"):
                ddx, ddz = FACE[d]
                if p.get(cx + ddx, f + 1, cz + ddz) in (None, "minecraft:air"):
                    K.chair(p, cx + ddx, f + 1, cz + ddz, OPP[d])
                    p.titik("kursi", cx + ddx, f + 1, cz + ddz, OPP[d])
        back_z = iz0 if sisi_pintu == "south" else iz1
        trapdoor(p, ix1, f + 2, back_z, "wood_trapdoor", "south" if sisi_pintu == "south" else "north")
        candle(p, ix1, f + 3, back_z, 2, "red")
        K.chest(p, ix1, f + 1, back_z, None, "south" if sisi_pintu == "south" else "north")
        p.titik("peti_milik", ix1 - 1 if ix1 - 1 > ix0 else ix1, f + 1, back_z, "east")
        p.titik("dupa", ix1, f + 1, back_z + (1 if sisi_pintu == "south" else -1), "north" if sisi_pintu == "south" else "south")
        # beds: upstairs when there is one, else along the back wall
        bf = floors[-1]
        placed = 0
        for i in range(beds):
            bx = ix0 + 1 + i * 2 if tingkat > 1 else ix0 + i * 2
            if bx > ix1 - (1 if tingkat == 1 else 0) or (tingkat == 1 and bx == ix1):
                break
            bz = iz0 + 1 if sisi_pintu == "south" else iz1 - 1
            K.bed(p, bx, bf + 1, bz, "north" if sisi_pintu == "south" else "south")
            p.titik("kasur", bx, bf + 1, bz, "south" if sisi_pintu == "south" else "north")
            placed += 1
        K.lit(p, cx, top_y, cz, chain=1)
        if tingkat > 1:
            K.lit(p, cx, f + h + 1, cz, chain=1)
            K.barrel(p, ix1, bf + 1, iz1 if sisi_pintu == "south" else iz0, "west")
    if jalan_masuk:
        p.titik("pintu", door[0], f + 1, door[1], sisi_pintu)
    p.kotak("bangunan", x0, f, z0, x1, top, z1, sisi_pintu, nama=nama)
    return {"inner": (ix0, iz0, ix1, iz1), "lantai": floors, "top": top, "pintu": door}


def jala_jemur(p, x, z, y, panjang=5, arah="x"):
    """A net-drying frame: two posts, a top rail, nets hanging (cobweb) and floats (sea pickles on the rail)."""
    dx, dz = (1, 0) if arah == "x" else (0, 1)
    for i in range(panjang):
        px, pz = x + dx * i, z + dz * i
        if i in (0, panjang - 1):
            for dy in (1, 2, 3):
                fence(p, px, y + dy, pz, "deck_fence")
        else:
            fence(p, px, y + 3, pz, "deck_fence")
            p.put(px, y + 2, pz, "minecraft:cobweb")
    fence(p, x + dx * (panjang // 2), y + 4, z + dz * (panjang // 2), "deck_fence")


def rak_ikan(p, x, z, y, panjang=4, arah="x"):
    """A fish-drying rack: posts, slab grates on two levels, dried kelp bundles (the fish) on them."""
    dx, dz = (1, 0) if arah == "x" else (0, 1)
    for i in range(panjang):
        px, pz = x + dx * i, z + dz * i
        if i in (0, panjang - 1):
            for dy in (1, 2):
                fence(p, px, y + dy, pz, "deck_fence")
        slab(p, px, y + 3, pz, "deck_slab", "bottom")
        if i % 2 == 1:
            p.put(px, y + 1, pz, "minecraft:dried_kelp_block")
    p.titik("kerja:rak", x + dx * (panjang // 2) + dz, y + 1, z + dz * (panjang // 2) + dx,
            "north" if arah == "x" else "west")


def kapal_model(p, x, y, z):
    """A model junk on a shelf: a slab hull, a fence mast and a wool sail (pajangan)."""
    slab(p, x, y, z, "minecraft:dark_oak_slab")
    p.put(x, y + 1, z, "minecraft:spruce_fence")
    p.put(x, y + 2, z, "minecraft:white_wool")


def jangkar_raksasa(p, cx, y, cz):
    """The giant anchor of the harbour square: an iron shank on a stone plinth, a chain ring, anvil flukes."""
    K.podium(p, cx - 3, cz - 3, cx + 3, cz + 3, y, 1)
    for dy in range(2, 9):
        p.put(cx, y + dy, cz, "minecraft:iron_block")
    p.put(cx - 1, y + 8, cz, "minecraft:iron_bars")
    p.put(cx + 1, y + 8, cz, "minecraft:iron_bars")
    p.put(cx, y + 9, cz, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    p.put(cx, y + 10, cz, "minecraft:iron_block")
    for d in (-1, 1):
        p.put(cx + d, y + 2, cz, "minecraft:anvil", {"facing": "north"})
        p.put(cx + 2 * d, y + 3, cz, "minecraft:anvil", {"facing": "north"})
        p.put(cx + 2 * d, y + 4, cz, "minecraft:iron_bars")
    for i in range(1, 4):
        p.put(cx, y + 2, cz + i, "minecraft:iron_chain", {"axis": "z", "waterlogged": "false"})


# ===================================================================================================== the land wall
def _dinding_kota(p, x0, x1, z0=M, y=LY, depth=BAND, walk=WALK, tangga_di=None):
    """A brick land wall run along x (outside at z0): diorite plinth, brick body with red brick courses, a plank and
    stone walkway at +walk, crenels with granite caps, slits, lantern posts on the inner parapet."""
    zi = z0 + depth - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, zi + 1):
            p.put(x, y - 1, z, "found")
            p.put(x, y, z, "kerb")
        for yy in range(y + 1, y + walk):
            for z in range(z0, zi + 1):
                if z == z0 and yy <= y + 2:
                    role = "base"
                elif (yy - y) % 4 == 0 and z in (z0, zi):
                    role = "red"
                else:
                    role = "brick"
                p.put(x, yy, z, role)
        for z in range(z0 + 1, zi):
            p.put(x, y + walk, z, "deck" if z == zi - 1 else "path")
        p.put(x, y + walk, z0, "brick")
        p.put(x, y + walk, zi, "brick")
        p.put(x, y + walk + 1, z0, "brick")
        if x % 2 == 0:
            p.put(x, y + walk + 2, z0, "brick")
            p.put(x, y + walk + 3, z0, "brick")
            slab(p, x, y + walk + 4, z0, "roof_slab")
        else:
            slab(p, x, y + walk + 2, z0, "brick_slab")
        if x % 6 == 3:
            p.put(x, y + walk - 4, z0, "minecraft:iron_bars")
            p.put(x, y + walk - 5, z0, "minecraft:iron_bars")
        p.put(x, y + walk + 1, zi, "brick_wall")
        if x % 8 == 4:
            K.post(p, x, y + walk + 1, zi - 1, height=1, post_role="deck_fence")
        if z0 - 1 >= 0:
            stairs(p, x, y + walk - 1, z0 - 1, "brick_stairs", "south", half="top")
    if tangga_di is not None:
        for i in range(walk):
            x = tangga_di + i
            if x > x1:
                break
            for z in (zi - 1, zi):
                stairs(p, x, y + 1 + i, z, "kerb_stairs", "east")
                for yy in range(y + 2 + i, y + walk + 1):
                    p.put(x, yy, z, "minecraft:air")
        for i in range(walk):
            if tangga_di + i <= x1:
                p.put(tangga_di + i, y + walk, zi - 2, "kerb")


def _menara_jaga(p, x0, z0, size=8, y=LY, walk=WALK, tinggi=6):
    """A square brick watchtower whose top storey is a plastered guard room with lattice windows and a granite hip
    roof with a pearl; a ladder inside, a drum and two guard points."""
    x1, z1 = x0 + size - 1, z0 + size - 1
    for yy in range(y, y + walk + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if yy == y or yy == y + walk:
                    p.put(x, yy, z, "kerb" if yy == y else "path")
                elif edge:
                    p.put(x, yy, z, "base" if yy <= y + 2 else ("red" if (yy - y) % 4 == 0 else "brick"))
                else:
                    p.put(x, yy, z, "minecraft:air")
    K.ladder(p, x0 + 1, y + 1, y + walk, z0 + 1, "south")
    p.put(x0 + 1, y + walk, z0 + 1, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    dinding_merah(p, x0, z0, x1, z1, y + walk, tinggi - 1, jendela=2)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            p.put(x, y + walk + tinggi, z, "wood")
    K.hip(p, x0, y + walk + tinggi + 1, z0, x1, z1, overhang=1, top="pearl")
    K.lit(p, (x0 + x1) // 2, y + walk + tinggi, (z0 + z1) // 2, chain=1)
    K.drum(p, x1 - 1, y + walk + 1, z1 - 1, "north")
    p.titik("jaga", x0 + 2, y + walk + 1, z0 + 2, "north")
    p.titik("jaga", x1 - 2, y + walk + 1, z0 + 2, "north")
    pintu(p, x0, z0, x1, z1, y + walk, "east", 1)
    pintu(p, x0, z0, x1, z1, y + walk, "west", 1)


def tembok_lurus(name, panjang=48, jaga=False, tangga=False):
    p = tl.Lot(F, name, tinggi=WALL_H + 16, lebar=panjang, dalam=BAND + 2 * M, palet=PAL)
    p.lights = []
    zi = M + BAND - 1
    _dinding_kota(p, 0, panjang - 1, tangga_di=8 if tangga else None)
    if jaga:
        _menara_jaga(p, panjang // 2 - 4, M - 1, size=8)
    for x in range(3, panjang, 9):
        K.wall_banner(p, x, LY + WALK - 3, zi + 1, "south", "blue")
    if tangga:
        p.titik("jalan", 7, LY + 1, zi + 1, "south", {"urut": 0, "jalur": "tembok"})
        p.titik("jaga", panjang - 10, LY + WALK + 1, M + 2, "north")
    p.titik("jaga", 4, LY + WALK + 1, M + 2, "north")
    p.simpan()
    return name


def tembok_ujung(name, menara_di_akhir):
    """A wall run ending at the shore: a round-cornered sea bastion with a lantern tower on one end (x = 47 when
    menara_di_akhir, else x = 0), so the west wall's bastion and the east wall's bastion both stand at the sea."""
    p = tl.Lot(F, name, tinggi=WALL_H + 16, lebar=48, dalam=BAND + 2 * M + 6, palet=PAL)
    p.lights = []
    zi = M + BAND - 1
    if menara_di_akhir:
        _dinding_kota(p, 0, 36, tangga_di=6)
        bx0 = 37
    else:
        _dinding_kota(p, 11, 47, tangga_di=30)
        bx0 = 0
    # bastion 11 x 12 reaching out past the wall face, a lantern tower on top facing the sea
    for yy in range(LY - 1, LY + WALK + 1):
        for x in range(bx0, bx0 + 11):
            for z in range(0, zi + 7):
                cxd = abs(x - (bx0 + 5)) + abs(z - (zi + 1) // 2 - 2)
                if cxd > 10:
                    continue
                edge = cxd >= 9 or x in (bx0, bx0 + 10) or z in (0, zi + 6)
                if yy in (LY - 1, LY, LY + WALK):
                    p.put(x, yy, z, "kerb" if yy < LY + WALK else "path")
                elif edge:
                    p.put(x, yy, z, "base" if yy <= LY + 2 else "brick")
                else:
                    p.put(x, yy, z, "rock")
    for x in range(bx0, bx0 + 11):
        for z in range(0, zi + 7):
            if p.get(x, LY + WALK, z) == p.b("path") and (x in (bx0, bx0 + 10) or z in (0, zi + 6)):
                p.put(x, LY + WALK + 1, z, "brick_wall")
    _menara_jaga(p, bx0 + 2, 2, size=7, y=LY + WALK, walk=4, tinggi=5)
    lantern(p, bx0 + 5, LY + WALK + 1, zi + 5)
    p.titik("jaga", bx0 + 5, LY + WALK + 1, zi + 4, "north")
    p.titik("pandang", bx0 + 5, LY + WALK + 5, 3, "north")
    for x in range(3, 48, 9):
        if not (bx0 <= x <= bx0 + 10):
            K.wall_banner(p, x, LY + WALK - 3, zi + 1, "south", "blue")
    p.simpan()
    return name


def tembok_sudut(name="tembok/sudut"):
    """The corner tower: 12 x 12 brick bastion, two storeys, a granite double-eave roof."""
    S = 12 + 2 * M
    p = tl.Lot(F, name, tinggi=WALL_H + 18, lebar=S, dalam=S, palet=PAL)
    p.lights = []
    _menara_jaga(p, M, M, size=12, tinggi=7)
    p.simpan()
    return name


def gerbang_darat(name="tembok/gerbang_darat"):
    """Gerbang Darat 48 x 20 x 12: a brick gatehouse over a 5-wide arch, a two-storey gate tower with a granite hip
    roof and swallowtail eaves, side flights to the wall walk, the gate's name board and two guards."""
    D = 8 + 2 * M
    p = tl.Lot(F, name, tinggi=38, lebar=48, dalam=D, palet=PAL)
    p.lights = []
    z0, zi = M, M + 7
    _dinding_kota(p, 0, 47, z0=z0, depth=8)
    cx = 24
    for x in range(cx - 2, cx + 3):
        for z in range(z0, zi + 1):
            for yy in range(LY + 1, LY + 7):
                p.put(x, yy, z, "minecraft:air")
            p.put(x, LY, z, "path")
            p.put(x, LY + 7, z, "base")
    for z in range(z0, zi + 1):
        for x in (cx - 3, cx + 3):
            p.put(x, LY + 6, z, "base")
        stairs(p, cx - 2, LY + 6, z, "base_stairs", "east", half="top")
        stairs(p, cx + 2, LY + 6, z, "base_stairs", "west", half="top")
    for z in (z0, zi):
        K.plaque(p, cx, LY + 8, z, "x")
    # gate tower
    gx0, gx1 = cx - 8, cx + 8
    dinding_merah(p, gx0, z0, gx1, zi, LY + WALK, 5, jendela=2)
    for x in range(gx0 + 1, gx1):
        for z in range(z0 + 1, zi):
            p.put(x, LY + WALK, z, "wood")
            p.put(x, LY + WALK + 5, z, "wood")
    pintu(p, gx0, z0, gx1, zi, LY + WALK, "west", 1)
    pintu(p, gx0, z0, gx1, zi, LY + WALK, "east", 1)
    K.lit(p, cx, LY + WALK + 5, (z0 + zi) // 2, chain=1, role="lamp")
    top = K.hip(p, gx0, LY + WALK + 6, z0, gx1, zi, overhang=2, tiers=2, tier_gap=2, tier_band=2, band_role="plaster",
                top="pearl")
    _ = top
    K.drum(p, gx0 + 2, LY + WALK + 1, zi - 1, "north")
    K.banner_pole(p, gx0 - 2, LY + WALK + 1, z0 + 1, 4, "blue")
    K.banner_pole(p, gx1 + 2, LY + WALK + 1, z0 + 1, 4, "blue")
    for x, z in ((cx - 4, zi + 1), (cx + 4, zi + 1)):
        p.titik("jaga", x, LY + 1, z, "south")
    p.titik("jaga", cx, LY + WALK + 1, z0 + 2, "north")
    p.jangkar(cx - 5.5, LY + 1, zi + 1.5, "kota/penjaga", yaw=0.0)
    p.jangkar(cx + 5.5, LY + 1, zi + 1.5, "kota/penjaga", yaw=0.0)
    p.titik("jalan", cx, LY + 1, 0, "south", {"urut": 0, "jalur": "gerbang"})
    p.titik("jalan", cx, LY + 1, D - 1, "south", {"urut": 1, "jalur": "gerbang"})
    p.simpan()
    return name


SIDE_PUTAR = {"n": 0, "e": 1, "s": 2, "w": 3}


def pasang_tembok(distrik, side, idx, name, deep, putar=None, lebar=48, dx_dalam=None):
    """Registers a wall piece of `distrik` on `side` at lot slot idx (0 = west/north half)."""
    r = SIDE_PUTAR[side] if putar is None else putar
    along = -48 + 48 * idx if dx_dalam is None else dx_dalam
    if side == "n":
        dx, dz = along, -48 - deep + M
    elif side == "s":
        dx, dz = along, 48 - M
    elif side == "e":
        dx, dz = 48 - M, along
    else:
        dx, dz = -48 - deep + M, along
    daftar_bebas(distrik, dx, dz, name, r)


def build_walls():
    tembok_lurus("tembok/lurus", tangga=True)
    tembok_lurus("tembok/lurus_jaga", jaga=True)
    tembok_lurus("tembok/setengah", panjang=24)
    tembok_ujung("tembok/ujung_barat", True)
    tembok_ujung("tembok/ujung_timur", False)
    tembok_sudut()
    gerbang_darat()
    run = BAND + 2 * M
    # west wall (kota_awak, kuil_laut), east wall (gudang_dagang, hunian_saudagar), south wall (row +1)
    pasang_tembok("kota_awak", "w", 0, "tembok/ujung_barat", run + 6)
    pasang_tembok("kota_awak", "w", 1, "tembok/lurus", run)
    pasang_tembok("kuil_laut", "w", 0, "tembok/lurus_jaga", run)
    pasang_tembok("kuil_laut", "w", 1, "tembok/lurus", run)
    pasang_tembok("kuil_laut", "s", 0, "tembok/lurus", run)
    pasang_tembok("kuil_laut", "s", 1, "tembok/lurus_jaga", run)
    pasang_tembok("gudang_dagang", "e", 0, "tembok/ujung_timur", run + 6)
    pasang_tembok("gudang_dagang", "e", 1, "tembok/lurus", run)
    pasang_tembok("hunian_saudagar", "e", 0, "tembok/lurus_jaga", run)
    pasang_tembok("hunian_saudagar", "e", 1, "tembok/lurus", run)
    pasang_tembok("hunian_saudagar", "s", 0, "tembok/lurus_jaga", run)
    pasang_tembok("hunian_saudagar", "s", 1, "tembok/lurus", run)
    pasang_tembok("gerbang_darat", "s", 0, "tembok/setengah", run, dx_dalam=-48)
    pasang_tembok("gerbang_darat", "s", 0, "tembok/gerbang_darat", 8 + 2 * M, dx_dalam=-24)
    pasang_tembok("gerbang_darat", "s", 0, "tembok/setengah", run, dx_dalam=24)
    S = 12 + 2 * M
    daftar_bebas("kuil_laut", -48 - S + M, 48 - M, "tembok/sudut", 3)
    daftar_bebas("hunian_saudagar", 48 - M, 48 - M, "tembok/sudut", 2)


# ===================================================================================================== sea row
def d_dermaga_nelayan():
    """Dermaga Nelayan: the breakwater curving out to the Pelita Angin lighthouse, the morning fish market on its deck
    and the branching fishermen's jetty with eight boat moorings and the net shed."""
    k = kanvas("dermaga_nelayan", PY + 64)
    k.lights = []
    # ---------------------------------------------------------------- (0,0) Pemecah Ombak: a curved rock breakwater
    cx, cz, r = 47, 47, 27
    walk = []
    for step in range(0, 181):
        a = math.radians(180 + step * 0.5)
        x, z = cx + r * math.cos(a), cz + r * math.sin(a)
        walk.append((x, z))
    done = set()
    for (fx, fz) in walk:
        for ox in range(-6, 7):
            for oz in range(-6, 7):
                x, z = int(round(fx + ox)), int(round(fz + oz))
                if (x, z) in done or not (0 <= x <= 47 + 6 and 0 <= z <= 47):
                    continue
                d = math.hypot(fx + ox - fx, fz + oz - fz)
                if d > 5.6:
                    continue
                done.add((x, z))
                topy = PY if d <= 1.6 else PY - 1 - int((d - 1.6) * 2.2)
                for y in range(0, max(1, topy) + 1):
                    n = (x * 7 + z * 13 + y * 3) % 11
                    role = "rock_alt" if n < 3 else "rock_dark" if n < 5 else "rock"
                    if y == topy and d <= 1.6:
                        role = "path" if n else "path_alt"
                    k.put(x, y, z, role)
    # parapet on the outer (north-west) edge, lamps, fishing spots
    for i, (fx, fz) in enumerate(walk):
        if i % 2:
            continue
        a = math.radians(180 + i * 0.5)
        ox, oz = int(round(fx - 2 * math.cos(a) * -1)), int(round(fz - 2 * math.sin(a) * -1))
        if k.get(ox, PY, oz) not in (None, "minecraft:air"):
            k.put(ox, PY + 1, oz, "kerb_wall")
        if i % 40 == 20:
            K.post(k, int(round(fx)), PY + 1, int(round(fz)), height=2, post_role="minecraft:andesite_wall")
    for i in (30, 80, 130, 160):
        fx, fz = walk[i]
        a = math.radians(180 + i * 0.5)
        k.titik("kerja:pancing", int(round(fx + math.cos(a))), PY + 1, int(round(fz + math.sin(a))), "north")
    k.titik("obrol", int(walk[100][0]), PY + 1, int(walk[100][1]), "east")
    k.jalur([(int(round(walk[i][0])), int(round(walk[i][1])), PY + 1) for i in (0, 40, 80, 120, 160, 180)], jalur="ombak")

    # ---------------------------------------------------------------- (1,0) Mercusuar Pelita Angin on its islet
    mx0, mz0 = 55, 15            # tower 11 x 11
    mx1, mz1 = mx0 + 10, mz0 + 10
    icx, icz = mx0 + 5, mz0 + 5
    for x in range(48, 96):
        for z in range(0, 40):
            d = math.hypot(x - icx, (z - icz) * 1.1)
            if d > 12.5:
                continue
            topy = PY if d <= 9 else PY - 1 - int((d - 9) * 3)
            for y in range(0, max(0, topy) + 1):
                n = (x * 5 + z * 11 + y * 7) % 9
                k.put(x, y, z, "rock_alt" if n < 2 else "rock_dark" if n < 4 else "rock")
            if d <= 9:
                k.put(x, PY, z, "path" if (x + z) % 7 else "rock_alt")
    # islet path joining the breakwater end
    for x in range(44, 56):
        for z in range(18, 23):
            for y in range(0, PY + 1):
                k.put(x, y, z, "rock" if y < PY else "path")
    H = 40                       # tower walls to the lamp gallery
    for y in range(PY + 1, PY + H + 1):
        for x in range(mx0, mx1 + 1):
            for z in range(mz0, mz1 + 1):
                if not (x in (mx0, mx1) or z in (mz0, mz1)):
                    continue
                corner = x in (mx0, mx1) and z in (mz0, mz1)
                band = ((y - PY) // 8) % 2 == 1 and (y - PY) % 8 in (6, 7)
                if corner:
                    k.put(x, y, z, "base")
                elif band:
                    k.put(x, y, z, "red")
                elif y <= PY + 4:
                    k.put(x, y, z, "base")
                else:
                    k.put(x, y, z, "plaster" if (y - PY) % 8 not in (0,) else "kerb")
    k.air(mx0 + 1, PY + 1, mz0 + 1, mx1 - 1, PY + H, mz1 - 1)
    # floors every 8 with a stair loop around the inside walls
    perim = []
    ix0, iz0, ix1, iz1 = mx0 + 1, mz0 + 1, mx1 - 1, mz1 - 1
    for x in range(ix0, ix1 + 1):
        perim.append((x, iz0, "east"))
    for z in range(iz0 + 1, iz1 + 1):
        perim.append((ix1, z, "south"))
    for x in range(ix1 - 1, ix0 - 1, -1):
        perim.append((x, iz1, "west"))
    for z in range(iz1 - 1, iz0, -1):
        perim.append((ix0, z, "north"))
    n = len(perim)
    for lvl in range(5):
        fy = PY + lvl * 8
        if lvl:
            for x in range(ix0, ix1 + 1):
                for z in range(iz0, iz1 + 1):
                    k.put(x, fy, z, "wood")
        else:
            for x in range(ix0, ix1 + 1):
                for z in range(iz0, iz1 + 1):
                    k.put(x, fy, z, "path_alt")
        if lvl < 4:
            s0 = (lvl * 8) % n
            for i in range(8):
                px, pz, face = perim[(s0 + i) % n]
                stairs(k, px, fy + 1 + i, pz, "wood_stairs", face)
                if 4 <= i <= 6:
                    k.put(px, fy + 8, pz, "minecraft:air")
                    k.put(px, fy + 8 + 1, pz, "minecraft:air")
        # windows: two slits per face per floor
        for wy in (fy + 3, fy + 4):
            for (wx, wz) in ((icx, mz0), (icx, mz1), (mx0, icz), (mx1, icz)):
                k.put(wx, wy, wz, "minecraft:glass_pane")
    pintu(k, mx0, mz0, mx1, mz1, PY, "west", 1)
    # ground floor: the keeper's room
    K.bed(k, ix0 + 6, PY + 1, iz1 - 1, "south")
    k.titik("kasur", ix0 + 6, PY + 1, iz1 - 1, "north")
    k.put(icx, PY + 1, icz, "minecraft:cartography_table")
    K.chair(k, icx, PY + 1, icz + 1, "north")
    k.titik("meja_tulis", icx, PY + 1, icz + 1, "north")
    K.chest(k, ix1, PY + 1, iz1 - 2, None, "west")
    k.put(ix1, PY + 1, iz1 - 3, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    K.barrel(k, ix0 + 4, PY + 1, iz1, "north")
    K.lit(k, icx, PY + 8, icz, chain=2)
    # oil stores and the lens room
    for lvl, kind in ((1, "minyak"), (2, "minyak"), (3, "lensa")):
        fy = PY + lvl * 8
        for x in range(ix0 + 2, ix1 - 1):
            for z in (iz0 + 2, iz1 - 2):
                if kind == "minyak":
                    K.barrel(k, x, fy + 1, z, "up")
                    if (x + lvl) % 2:
                        K.barrel(k, x, fy + 2, z, "north")
                else:
                    k.put(x, fy + 1, z, "minecraft:glass" if (x % 2) else "minecraft:light_blue_stained_glass")
        if kind == "minyak":
            k.put(icx, fy + 1, icz, "minecraft:cauldron")
        else:
            k.put(icx, fy + 1, icz, "minecraft:crafting_table")
            k.titik("kerja:meja_tulis", icx, fy + 1, icz + 1, "north")
        K.lit(k, icx, fy + 8, icz, chain=1)
    # lamp gallery (floor at PY+32 -> lamp room PY+33..)
    gy = PY + 32
    for x in range(mx0 - 1, mx1 + 2):
        for z in range(mz0 - 1, mz1 + 2):
            edge = x in (mx0 - 1, mx1 + 1) or z in (mz0 - 1, mz1 + 1)
            k.put(x, gy, z, "kerb" if edge else "wood")
            if edge:
                k.put(x, gy + 1, z, "minecraft:iron_bars")
    k.air(mx0, gy + 1, mz0, mx1, gy + 6, mz1)
    for x in range(mx0 - 1, mx1 + 2):
        for z in range(mz0 - 1, mz1 + 2):
            if x in (mx0 - 1, mx1 + 1) or z in (mz0 - 1, mz1 + 1):
                k.put(x, gy + 1, z, "minecraft:iron_bars")
    for (px, pz) in ((mx0 + 1, mz0 + 1), (mx1 - 1, mz0 + 1), (mx0 + 1, mz1 - 1), (mx1 - 1, mz1 - 1)):
        for dy in range(1, 7):
            k.put(px, gy + dy, pz, "copper" if dy < 6 else "minecraft:chiseled_quartz_block")
    # the lamp pedestal (the turning lamp is the api_suar entity)
    for x in range(icx - 1, icx + 2):
        for z in range(icz - 1, icz + 2):
            k.put(x, gy + 1, z, "base")
    k.put(icx, gy + 2, icz, "minecraft:gold_block")
    k.put(icx, gy + 7, icz, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    for x in range(mx0 + 1, mx1):
        for z in range(mz0 + 1, mz1):
            k.put(x, gy + 7, z, "wood")
    K.hip(k, mx0 + 1, gy + 8, mz0 + 1, mx1 - 1, mz1 - 1, overhang=2, tiers=3, tier_gap=1, tier_band=1,
          band_role="copper", top="spire", role="copper_stairs", slab_role="copper_slab", full_role="copper")
    k.jangkar(icx + 0.5, gy + 3, icz + 0.5, "pelabuhan/suar", yaw=0.0, protect=24)
    k.jangkar(ix0 + 3.5, PY + 1, icz + 0.5, "pelabuhan/tokoh", yaw=90.0, tokoh="deng_ming", peran="PENJAGA_SUAR")
    for (jx, jz, f) in ((icx, mz0 - 1, "north"), (mx1 + 1, icz, "east")):
        k.titik("jaga_suar", jx, gy + 1, jz, f)
    k.titik("pandang", icx, gy + 1, mz1 + 1, "south")
    k.titik("pintu", mx0 - 1, PY + 1, icz, "west")
    k.kotak("bangunan", mx0, PY, mz0, mx1, gy + 16, mz1, "west", nama="mercusuar")

    # ---------------------------------------------------------------- (0,1) Pasar Ikan Pagi on its deck
    geladak(k, 4, 60, 43, 89)
    geladak(k, 18, 48, 22, 59, arah="z")      # walk to the breakwater
    geladak(k, 18, 90, 23, 95, arah="z")      # walk to the shore
    geladak(k, 44, 70, 47, 74)                # walk to the jetty
    gaps = {(x, 60) for x in range(18, 23)} | {(x, 89) for x in range(18, 24)} | {(43, z) for z in range(70, 75)}
    pagar(k, 4, 60, 43, 89, PY, "nesw", celah=gaps, lampu=9)
    stalls = []
    for i in range(7):
        sx = 7 + i * 5
        stalls.append(K.stall(k, sx, 67, "south", y=PY, w=3, cloth=("cloth", "cloth_alt"),
                              goods=("minecraft:blue_ice", "minecraft:barrel", "minecraft:dried_kelp_block")))
        stalls.append(K.stall(k, sx + 2, 82, "north", y=PY, w=3, cloth=("cloth_alt", "cloth"),
                              goods=("minecraft:dried_kelp_block", "minecraft:blue_ice")))
    for (sx, sz) in stalls:
        k.titik("lapak", sx, PY + 1, sz, "north" if sz > 75 else "south")
    for x in range(8, 41, 4):
        pot(k, x, PY + 1, 74 + (x % 3))
    K.table(k, 24, PY + 1, 75, "minecraft:heavy_weighted_pressure_plate")
    k.titik("kerja:timbangan", 24, PY + 1, 76, "north")
    rak_ikan(k, 6, 86, PY, 6)
    rak_ikan(k, 32, 86, PY, 6)
    rak_ikan(k, 40, 62, PY, 3, "z")
    for x in (10, 30):
        K.crates(k, x, 63, x + 1, 63, PY + 1, h=2)
    for (x, z) in ((6, 75), (41, 75), (14, 89), (34, 89)):
        bollard(k, x, PY, z)
    for (x, z, f) in ((6, 76, "east"), (41, 76, "west")):
        k.titik("kursi", x, PY + 1, z, f)
    k.titik("obrol", 22, PY + 1, 78, "east")
    k.titik("obrol", 26, PY + 1, 78, "west")
    k.titik("main", 20, PY + 1, 92, "south")
    k.jalur([(20, 50, PY + 1), (20, 62, PY + 1), (24, 75, PY + 1), (20, 88, PY + 1), (20, 95, PY + 1)], jalur="pasar_ikan")
    k.jangkar(24.5, PY + 1, 70.5, "pelabuhan/tokoh", yaw=0.0, tokoh="bibi_yu", peran="PENJUAL_IKAN")
    k.jangkar(20.5, PY + 1, 85.5, "pelabuhan/peran", yaw=180.0, peran="PENJUAL_IKAN")
    k.kotak("bangunan", 4, PY, 60, 43, PY + 6, 89, "south", nama="pasar_ikan_pagi")

    # ---------------------------------------------------------------- (1,1) Tambatan Nelayan, the net shed
    geladak(k, 68, 52, 72, 95, arah="z")                     # the spine
    fingers = []
    for i, z in enumerate((56, 64, 72, 80)):
        geladak(k, 52, z, 67, z + 2)                         # west finger
        geladak(k, 73, z, 90, z + 2)                         # east finger
        fingers.append((51, z + 1, "west"))
        fingers.append((91, z + 1, "east"))
    for x in (52, 90):
        for z in (56, 64, 72, 80):
            bollard(k, x, PY, z + 1)
    for i, (fx, fz, f) in enumerate(fingers):
        k.titik("tambatan", fx, PY, fz, f, {"kelas": "nelayan", "nomor": i})
    # net shed on a wide platform at the root of the spine
    geladak(k, 74, 84, 94, 95)
    dinding_merah(k, 78, 86, 92, 94, PY, 4, jendela=2)
    ruang(k, 79, 87, 91, 93, PY, "perahu", isi=0.55)
    for x in range(79, 92):
        for z in range(87, 94):
            k.put(x, PY, z, "deck")
    pintu(k, 78, 86, 92, 94, PY, "west", 1)
    atap(k, 78, 86, 92, 94, PY + 5)
    for x in range(80, 91, 3):
        k.put(x, PY + 4, 93, "minecraft:cobweb")
        k.titik("kerja:jala", x, PY + 1, 92, "south")
    K.crates(k, 80, 87, 82, 88, PY + 1, h=2)
    K.lit(k, 85, PY + 5, 90, chain=1)
    jala_jemur(k, 75, 85, PY, 5, "z")
    jala_jemur(k, 61, 90, PY, 5, "x")
    geladak(k, 54, 88, 67, 95)
    pagar(k, 54, 88, 67, 95, PY, "nw", lampu=7)
    k.jangkar(70.5, PY + 1, 92.5, "pelabuhan/perahu_sewa", yaw=0.0)
    k.jangkar(84.5, PY + 1, 89.5, "pelabuhan/peran", yaw=270.0, peran="NELAYAN")
    for i, (x, z) in enumerate(((72, 50), (86, 50), (94, 62), (94, 78), (80, 90), (64, 50))):
        k.titik("jalur_perahu", x, PY, z, "north", {"urut": i, "jalur": "lomba"})
    k.titik("main", 70, PY + 1, 54, "north")
    k.jalur([(70, 95, PY + 1), (70, 80, PY + 1), (70, 60, PY + 1)], jalur="tambatan")
    k.kotak("bangunan", 78, PY, 86, 92, PY + 10, 94, "west", nama="gudang_jala")
    simpan(k, "dermaga_nelayan", {(0, 0): "pemecah_ombak", (1, 0): "mercusuar", (0, 1): "pasar_ikan_pagi",
                                  (1, 1): "tambatan_nelayan"},
           {(0, 0): ("A", "J", "J", "A"), (1, 0): ("A", "A", "A", "J"), (0, 1): ("J", "J", "J", "A"),
            (1, 1): ("A", "A", "J", "J")}, utuh=("mercusuar",), tinggi_min=PY + 8)


def d_dermaga_agung():
    """Dermaga Agung: the long quay with three junk moorings (piers 28 long, bollards, gangway stairs, two treadwheel
    cranes), the customs house (Kantor Urusan Laut) and the sea-crossing post with its own mooring."""
    k = kanvas("dermaga_agung", PY + 40)
    k.lights = []
    # quay along the south of the north lots
    geladak(k, 0, 36, 95, 47)
    for x in range(0, 96):
        for z in range(36, 48):
            if z in (36,):
                k.put(x, PY, z, "path")
    piers = [(8, 12), (38, 42), (68, 72)]
    for n, (px0, px1) in enumerate(piers):
        geladak(k, px0, 6, px1, 35, arah="z")
        for z in range(8, 34, 5):
            bollard(k, px1, PY, z)
        for z in range(10, 34, 8):
            K.post(k, px0, PY + 1, z, height=2, post_role="deck_fence")
        cxs = px1 + 4                                       # ship centreline (7 wide hull)
        k.jangkar(cxs + 0.5, PY, 21.5, "pelabuhan/tambatan", yaw=0.0, nomor=n, kelas="jung", protect=16)
        k.titik("tambatan", cxs, PY, 21, "south", {"kelas": "jung", "nomor": n})
        for i, z in enumerate((0, 10, 21)):
            k.titik("jalur_kapal", cxs, PY, z, "south", {"urut": i, "jalur": "kapal%d" % n})
        k.titik("jalan", px0 + 2, PY + 1, 10, "south", {"urut": 0, "jalur": "kuli%d" % n})
        k.titik("jalan", px0 + 2, PY + 1, 30, "south", {"urut": 1, "jalur": "kuli%d" % n})
        k.titik("jalan", px0 + 2, PY + 1, 44, "south", {"urut": 2, "jalur": "kuli%d" % n})
    # cargo on the quay, lamp posts, two cranes
    for x0 in (16, 46, 76):
        K.crates(k, x0 + 6, 40, x0 + 9, 42, PY + 1, h=3)
    for x in range(4, 96, 12):
        K.post(k, x, PY + 1, 46, height=3, post_role="deck_fence")
    for (cx, cz, nomor) in ((28, 40, 0), (60, 40, 1)):
        for dx in (-2, 2):
            for dz in (-2, 2):
                k.put(cx + dx, PY + 1, cz + dz, "base")
        k.jangkar(cx + 0.5, PY + 1, cz + 0.5, "pelabuhan/derek", yaw=0.0, nomor=nomor, protect=12)
        k.titik("kerja:palu", cx + 3, PY + 1, cz, "west")
    k.titik("jaga", 2, PY + 1, 44, "north")
    k.titik("jaga", 93, PY + 1, 44, "north")
    k.titik("obrol", 34, PY + 1, 44, "north")
    k.titik("obrol", 54, PY + 1, 44, "north")

    # ---------------------------------------------------------------- (0,1) Kantor Urusan Laut (Shibosi)
    geladak(k, 0, 48, 47, 95)
    bx0, bz0, bx1, bz1 = 6, 56, 41, 83
    for x in range(bx0 - 2, bx1 + 3):
        for z in range(bz0 - 2, bz1 + 3):
            k.put(x, PY, z, "path" if (x + z) % 5 else "path_alt")
    dinding_merah(k, bx0, bz0, bx1, bz1, PY, 5)
    for x in range(bx0 + 1, bx1):
        for z in range(bz0 + 1, bz1):
            k.put(x, PY, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:stone_bricks")
            k.put(x, PY + 6, z, "wood")
    dinding_merah(k, bx0, bz0, bx1, bz1, PY + 6, 5)
    ruang(k, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, PY, "kantor", isi=0.4)
    ruang(k, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, PY + 6, "kantor", isi=0.5)
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            if x in (bx0, bx1) or z in (bz0, bz1):
                k.put(x, PY + 6, z, "beam", {"axis": "x" if z in (bz0, bz1) else "z"})
    pintu(k, bx0, bz0, bx1, bz1, PY, "north", 3)
    pintu(k, bx0, bz0, bx1, bz1, PY, "south", 3)
    top = atap(k, bx0, bz0, bx1, bz1, PY + 12, along="x", overhang=2)
    # ground floor: four customs counters with clerks behind, document racks, the confiscation cell
    for i, cx in enumerate((11, 18, 29, 36)):
        K.counter(k, cx - 2, cx + 2, 66, PY, "north")
        K.chair(k, cx, PY + 1, 67, "north")
        k.titik("meja_tulis", cx, PY + 1, 67, "north")
        K.bench(k, cx - 1, 61, "south", y=PY, n=3)
        k.titik("kursi", cx, PY + 1, 61, "south")
    K.table(k, 24, PY + 1, 64, "minecraft:heavy_weighted_pressure_plate")
    k.titik("kerja:timbangan", 24, PY + 1, 63, "south")
    K.bookshelf_wall(k, bx0 + 1, bz1 - 1, bx0 + 12, bz1 - 1, PY + 1, PY + 3, face="north")
    for x in range(bx0 + 1, bx0 + 13):
        k.put(x, PY + 1, bz1 - 3, "minecraft:spruce_shelf", {"facing": "north", "powered": "false", "side_chain": "unconnected",
                                                             "waterlogged": "false"})
    for x in range(32, bx1):
        for z in range(72, bz1):
            if x == 32 or z == 72:
                for dy in (1, 2, 3):
                    k.put(x, PY + dy, z, "minecraft:iron_bars")
    K.crates(k, 35, 76, 39, 81, PY + 1, h=2)
    k.put(32, PY + 1, 77, "minecraft:iron_door", {"facing": "west", "half": "lower", "hinge": "left", "open": "false",
                                                  "powered": "false"})
    k.put(32, PY + 2, 77, "minecraft:iron_door", {"facing": "west", "half": "upper", "hinge": "left", "open": "false",
                                                  "powered": "false"})
    K.stair_flight(k, bx0 + 1, PY + 1, 70, "north", 5, width=2)
    for x in (bx0 + 1, bx0 + 2):
        for z in range(65, 71):
            k.put(x, PY + 6, z, "minecraft:air")
    # upper floor: the harbourmaster's office and the north balcony over the quay
    K.table(k, 24, PY + 7, 70, "minecraft:white_carpet")
    K.table(k, 25, PY + 7, 70, "minecraft:white_carpet")
    K.chair(k, 24, PY + 7, 72, "north")
    k.titik("meja_tulis", 24, PY + 7, 72, "north")
    for x in (21, 28):
        K.chair(k, x, PY + 7, 70, "east" if x < 24 else "west")
        k.titik("kursi", x, PY + 7, 70, "east" if x < 24 else "west")
    k.put(30, PY + 7, 64, "minecraft:cartography_table")
    k.put(18, PY + 7, 64, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    K.bookshelf_wall(k, 14, bz1 - 1, 34, bz1 - 1, PY + 7, PY + 9, face="north")
    K.rug(k, 20, 66, 29, 74, PY + 7)
    K.wall_banner(k, 24, PY + 9, bz1 - 2, "north", "blue")
    for x in range(bx0 + 4, bx1 - 3):
        for z in range(bz0 - 3, bz0):
            k.put(x, PY + 6, z, "wood")
        k.put(x, PY + 7, bz0 - 3, "rail")
    for z in range(bz0 - 3, bz0):
        k.put(bx0 + 4, PY + 7, z, "rail")
        k.put(bx1 - 4, PY + 7, z, "rail")
    pintu(k, bx0, bz0, bx1, bz1, PY + 6, "north", 1)
    k.titik("pandang", 24, PY + 7, bz0 - 2, "north")
    for x in (12, 20, 28, 36):
        K.lit(k, x, PY + 6, 75, chain=1)
        K.lit(k, x, PY + 12, 74, chain=1, role="lamp")
    k.jangkar(24.5, PY + 7, 73.5, "pelabuhan/tokoh", yaw=180.0, tokoh="lin_haichao", peran="SYAHBANDAR")
    k.jangkar(18.5, PY + 1, 67.5, "pelabuhan/peran", yaw=180.0, peran="PETUGAS_BEA", layanan="bea")
    k.jangkar(29.5, PY + 1, 67.5, "pelabuhan/peran", yaw=180.0, peran="PETUGAS_BEA")
    k.jangkar(24.5, PY + 1, 53.5, "kota/penjaga", yaw=0.0)
    k.kotak("bangunan", bx0, PY, bz0, bx1, top, bz1, "north", nama="kantor_urusan_laut")
    k.jalur([(24, 48, PY + 1), (24, 55, PY + 1), (24, 64, PY + 1), (24, 85, PY + 1), (24, 95, PY + 1)], jalur="kantor")

    # ---------------------------------------------------------------- (1,1) Pos Kapal Lintas Laut
    geladak(k, 48, 48, 83, 95)
    geladak(k, 78, 52, 83, 92, arah="z")
    for z in range(54, 92, 6):
        bollard(k, 83, PY, z)
    wx0, wz0, wx1, wz1 = 52, 60, 74, 86
    dinding_merah(k, wx0, wz0, wx1, wz1, PY, 5)
    ruang(k, wx0 + 1, wz0 + 1, wx1 - 1, wz1 - 1, PY, "inap", isi=0.3)
    for x in range(wx0 + 1, wx1):
        for z in range(wz0 + 1, wz1):
            k.put(x, PY, z, "deck")
    pintu(k, wx0, wz0, wx1, wz1, PY, "east", 3)
    pintu(k, wx0, wz0, wx1, wz1, PY, "north", 1)
    atap(k, wx0, wz0, wx1, wz1, PY + 6, along="z", overhang=2)
    for row, z in enumerate((65, 69, 73, 77)):
        K.bench(k, 56, z, "east", y=PY, n=3)
        for i in range(3):
            k.titik("kursi", 56, PY + 1, z + i, "east")
    K.counter_z(k, 70, 64, 72, PY, "west")
    K.chair(k, 72, PY + 1, 68, "west")
    k.titik("meja_tulis", 72, PY + 1, 68, "west")
    K.notice_board(k, 62, wz1 - 1, "north", y=PY, w=5)
    for x in (58, 66):
        K.lit(k, x, PY + 6, 70, chain=1)
        K.lit(k, x, PY + 6, 80, chain=1)
    k.jangkar(88.5, PY, 72.5, "pelabuhan/tambatan", yaw=180.0, nomor=3, kelas="jung_laut", protect=16)
    k.titik("tambatan", 88, PY, 72, "north", {"kelas": "jung_laut", "nomor": 3})
    for i, z in enumerate((95, 84, 72)):
        k.titik("jalur_kapal", 88, PY, z, "north", {"urut": i, "jalur": "lintas"})
    k.jangkar(81.5, PY + 1, 70.5, "pelabuhan/lintas_laut", yaw=90.0, tokoh="gao_fan", peran="NAKHODA_LAUT")
    k.titik("jaga", 80, PY + 1, 60, "east")
    k.jalur([(48, 72, PY + 1), (63, 72, PY + 1), (76, 72, PY + 1), (81, 72, PY + 1)], jalur="lintas")
    k.kotak("bangunan", wx0, PY, wz0, wx1, PY + 14, wz1, "east", nama="pos_lintas_laut")
    simpan(k, "dermaga_agung", {(0, 0): "dermaga_barat", (1, 0): "dermaga_timur", (0, 1): "kantor_urusan_laut",
                                (1, 1): "pos_lintas_laut"},
           {(0, 0): ("A", "J", "J", "J"), (1, 0): ("A", "J", "J", "J"), (0, 1): ("J", "J", "J", "J"),
            (1, 1): ("J", "A", "J", "J")}, utuh=("kantor_urusan_laut",), tinggi_min=PY + 8)


def landasan(k, cx, z_tinggi=44, z_rendah=4, lebar=11):
    """A slipway: a stone ramp from the deck (south) down into the water (north), a timber cradle on top, keel blocks,
    the scaffolding frames either side."""
    half = lebar // 2
    for z in range(z_rendah, z_tinggi + 1):
        top = PY - (z_tinggi - z) // 4
        for x in range(cx - half, cx + half + 1):
            for y in range(0, top + 1):
                k.put(x, y, z, "kerb" if y < top else ("deck" if abs(x - cx) <= 1 else "path"))
        if z % 4 == 0:
            k.put(cx, top + 1, z, "minecraft:spruce_log", {"axis": "x"})
    for z in range(z_rendah + 4, z_tinggi - 2, 6):
        top = PY - (z_tinggi - z) // 4
        for side in (-1, 1):
            x = cx + side * (half + 1)
            for dy in range(1, 9):
                if top + dy >= PY - 2:
                    k.put(x, top + dy, z, "minecraft:spruce_fence")
            for xx in (cx + side * (half - 1), cx + side * half):
                slab(k, xx, max(PY - 1, top + 5), z, "minecraft:spruce_slab", "top")
                slab(k, xx, max(PY + 3, top + 9), z, "minecraft:spruce_slab", "top")
    for z in range(z_rendah + 4, z_tinggi - 2, 6):
        for side in (-1, 1):
            x = cx + side * (half + 1)
            for y in range(0, PY - 2):
                if k.get(x, y, z) is None:
                    log(k, x, y, z, "pile")


def d_galangan():
    """Galangan Kapal: two slipways where ordered hulls grow (kapal_galangan entities), scaffolding, the rope works and
    the tar kettle; Mbah Lunas's drafting house with model ships; the dry dock with its sea gate."""
    k = kanvas("galangan", PY + 36)
    k.lights = []
    geladak(k, 0, 36, 95, 47)
    geladak(k, 38, 0, 57, 35, arah="z")
    for n, cx in enumerate((22, 74)):
        landasan(k, cx)
        k.jangkar(cx + 0.5, PY - 4, 22.5, "pelabuhan/galangan", yaw=180.0, landasan=n, protect=16)
        for z in (10, 30):
            k.titik("kerja:palu", cx - 6, PY + 1 if z > 26 else PY, z, "east")
            k.titik("kerja:pahat", cx + 6, PY + 1 if z > 26 else PY, z, "west")
    # rope works on the middle pier, the tar kettle and timber stacks
    for z in range(4, 33, 4):
        for x in (39, 56):
            k.put(x, PY + 1, z, "deck_fence")
            k.put(x, PY + 2, z, "deck_fence")
        for x in range(40, 56):
            k.put(x, PY + 2, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
    for z in (8, 16, 24):
        k.titik("kerja:jala", 47, PY + 1, z, "east")
    K.barrel(k, 41, PY + 1, 34, "up")
    K.barrel(k, 42, PY + 1, 34, "north")
    k.put(50, PY + 1, 40, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    k.put(51, PY + 1, 40, "minecraft:cauldron")
    k.put(52, PY + 1, 40, "minecraft:smoker", {"facing": "south", "lit": "false"})
    k.put(51, PY + 2, 40, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    k.titik("kerja:kuali", 51, PY + 1, 41, "north")
    for x0 in (4, 84):
        for x in range(x0, x0 + 8):
            for z in (39, 40, 41):
                for dy in range(1, 3 + (x % 2)):
                    k.put(x, PY + dy, z, "minecraft:stripped_spruce_log" if dy % 2 else "minecraft:spruce_log", {"axis": "x"})
    for x in range(2, 96, 12):
        K.post(k, x, PY + 1, 46, height=3, post_role="deck_fence")
    k.titik("obrol", 32, PY + 1, 44, "east")
    k.titik("obrol", 64, PY + 1, 44, "west")

    # ---------------------------------------------------------------- (0,1) Rumah Tukang Kapal
    geladak(k, 0, 48, 47, 95)
    r = rumah_merah(k, 6, 58, 40, 84, PY, tingkat=2, sisi_pintu="north", beds=3, nama="rumah_tukang_kapal")
    ix0, iz0, ix1, iz1 = r["inner"]
    # drafting room: the keel drawing table, models on shelves, the loom of sail cloth
    k.put(14, PY + 1, 66, "minecraft:cartography_table")
    k.put(15, PY + 1, 66, "minecraft:cartography_table")
    K.chair(k, 14, PY + 1, 67, "north")
    k.titik("meja_tulis", 14, PY + 1, 67, "north")
    k.put(30, PY + 1, 80, "minecraft:loom", {"facing": "north"})
    k.titik("kerja:tenun", 30, PY + 1, 79, "south")
    for x in range(20, 36, 3):
        trapdoor(k, x, PY + 2, iz1, "wood_trapdoor", "north")
        kapal_model(k, x, PY + 3, iz1)
    K.bookshelf_wall(k, ix0, iz1, ix0 + 6, iz1, PY + 1, PY + 3, face="north")
    k.put(36, PY + 1, 70, "minecraft:smithing_table")
    k.put(36, PY + 1, 71, "minecraft:grindstone", {"face": "floor", "facing": "west"})
    k.titik("kerja:gergaji", 35, PY + 1, 70, "east")
    for x in range(ix0 + 1, ix1, 4):
        K.lit(k, x, PY + 5, 72, chain=1)
    k.jangkar(18.5, PY + 1, 70.5, "pelabuhan/tokoh", yaw=180.0, tokoh="gong_shumu", peran="TUKANG_KAPAL")
    k.jangkar(28.5, PY + 1, 70.5, "pelabuhan/peran", yaw=180.0, peran="TUKANG_KAPAL")
    K.crates(k, 2, 88, 10, 92, PY + 1, h=2)
    k.jalur([(24, 48, PY + 1), (24, 56, PY + 1), (24, 70, PY + 1)], jalur="tukang")

    # ---------------------------------------------------------------- (1,1) Dok Kering
    geladak(k, 48, 48, 95, 95)
    dx0, dz0, dx1, dz1 = 58, 62, 92, 75           # basin walls (inner 60..91 x 63..74 -> 32 x 12)
    for x in range(dx0, dx1 + 1):
        for z in range(dz0, dz1 + 1):
            for y in range(0, PY + 1):
                wall = x in (dx0, dx1) or z in (dz0, dz1)
                if wall:
                    k.put(x, y, z, "kerb" if y < PY else "path")
                elif y <= PY - 7:
                    k.put(x, y, z, "kerb" if y < PY - 7 else "minecraft:smooth_stone")
                else:
                    k.put(x, y, z, "minecraft:air")
    # the sea gate opening on the east end (closed by the pintu_air entity), steps down, keel blocks
    for z in range(dz0 + 3, dz1 - 2):
        for y in range(PY - 6, PY + 1):
            k.put(dx1, y, z, "minecraft:air")
    for z in range(dz0 + 1, dz1):
        for y in range(PY - 6, PY + 1):
            k.put(dx1 + 1, y, z, "kerb") if z in (dz0 + 1, dz0 + 2, dz1 - 2, dz1 - 1) else None
    for z in (dz0 + 2, dz1 - 2):
        for y in range(0, PY + 1):
            k.put(dx1 + 1, y, z, "kerb")
    for i in range(6):
        stairs(k, dx0 + 1 + i, PY - i, dz0 + 1, "kerb_stairs", "west")
    for x in range(dx0 + 4, dx1 - 3, 4):
        for z in (dz0 + 5, dz0 + 6, dz0 + 7):
            k.put(x, PY - 6, z, "minecraft:spruce_log", {"axis": "z"})
    pagar(k, dx0, dz0, dx1, dz1, PY, "nsw", lampu=8)
    k.jangkar(dx1 + 0.5, PY - 6, (dz0 + dz1) / 2.0 + 0.5, "pelabuhan/pintu_dok", yaw=90.0, protect=12)
    k.titik("kerja:palu", dx0 + 10, PY - 5, dz0 + 3, "south")
    k.titik("kerja:pahat", dx0 + 18, PY - 5, dz1 - 3, "north")
    k.titik("jaga", dx0 - 2, PY + 1, dz0 + 6, "east")
    K.crates(k, 50, 80, 56, 86, PY + 1, h=2)
    k.kotak("bangunan", dx0, PY - 7, dz0, dx1, PY + 2, dz1, "east", nama="dok_kering")
    simpan(k, "galangan", {(0, 0): "landasan_barat", (1, 0): "landasan_timur", (0, 1): "rumah_tukang_kapal",
                           (1, 1): "dok_kering"},
           {(0, 0): ("A", "J", "J", "A"), (1, 0): ("A", "A", "J", "J"), (0, 1): ("J", "J", "J", "J"),
            (1, 1): ("J", "A", "J", "J")}, tinggi_min=PY + 8)


# ===================================================================================================== land rows
def tanah(k, tanggul=False):
    """Floor of a land district: one foundation layer, paving, 3-block streets on every lot border with lamp posts; on
    the shore row a sea-wall promenade along the north edge (stone parapet with openings to the decks)."""
    k.fill(0, LY - 1, 0, SEL - 1, LY - 1, SEL - 1, "found")
    K.street_grid(k, nx=2, nz=2, lamps=True)
    if tanggul:
        for x in range(SEL):
            for z in range(0, 4):
                k.put(x, LY, z, "path" if z else "kerb")
            if x % 16 not in range(6, 11):
                k.put(x, LY + 1, 0, "kerb_wall")
            if x % 16 == 3:
                bollard(k, x, LY, 2)


def rumah_awak(k, x0, z0, varian, f=LY):
    """One sailor/fisher house of the Kota Awak lanes (5 kinds)."""
    if varian == 0:
        rumah_merah(k, x0, z0, x0 + 8, z0 + 10, f, 1, "south", beds=2, nama="rumah_awak")
    elif varian == 1:
        rumah_merah(k, x0, z0, x0 + 10, z0 + 8, f, 2, "south", beds=3, nama="rumah_awak_tingkat")
    elif varian == 2:
        # a stilt house: posts 2 high, a deck porch, steps
        for x in range(x0, x0 + 9):
            for z in range(z0, z0 + 9):
                if x in (x0, x0 + 8) and z in (z0, z0 + 8) or (x == x0 + 4 and z in (z0, z0 + 8)):
                    for y in range(f + 1, f + 3):
                        log(k, x, y, z, "minecraft:stripped_spruce_log")
        for x in range(x0, x0 + 9):
            for z in range(z0, z0 + 9):
                k.put(x, f + 3, z, "deck")
        rumah_merah(k, x0, z0, x0 + 8, z0 + 7, f + 3, 1, "south", beds=2, nama="rumah_panggung")
        for i in range(3):
            stairs(k, x0 + 4, f + 1 + i, z0 + 11 - i, "deck_stairs", "north")
        fence(k, x0, f + 4, z0 + 8)
        fence(k, x0 + 8, f + 4, z0 + 8)
    elif varian == 3:
        rumah_merah(k, x0, z0, x0 + 8, z0 + 8, f, 1, "south", beds=2, nama="rumah_jala")
        jala_jemur(k, x0 + 10, z0 + 1, f, 6, "z")
    else:
        rumah_merah(k, x0, z0, x0 + 9, z0 + 9, f, 1, "east", beds=2, nama="rumah_awak_timur")
        rak_ikan(k, x0 + 1, z0 + 11, f, 5)


def d_kota_awak():
    """Kota Awak: the sailors' quarter: Kedai Jangkar Patah (tavern, stage, rooms), the rope walk with crew row houses,
    and lanes of sailor and fisher houses with a well and the family shrine of the Sea Goddess."""
    k = kanvas("kota_awak", 40)
    k.lights = []
    tanah(k, tanggul=True)
    # ---------------------------------------------------------------- (0,0) Kedai Jangkar Patah
    x0, z0, x1, z1 = 6, 10, 41, 39
    for t in range(2):
        f = LY + t * 6
        dinding_merah(k, x0, z0, x1, z1, f, 5)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                k.put(x, f, z, "minecraft:spruce_planks" if t == 0 else "wood_alt")
    pintu(k, x0, z0, x1, z1, LY, "north", 3)
    pintu(k, x0, z0, x1, z1, LY, "east", 1)
    top = atap(k, x0, z0, x1, z1, LY + 12, along="x", overhang=2)
    # ground floor: ten tables, the bar, the little stage with a drum, the kitchen corner
    tables = [(11, 16), (17, 16), (23, 16), (11, 22), (17, 22), (23, 22), (11, 28), (17, 28), (23, 28), (17, 34)]
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, LY, "kedai", isi=0.4, rug=False)
    for (tx, tz) in tables:
        K.table(k, tx, LY + 1, tz)
        k.titik("meja_makan", tx, LY + 1, tz, "south")
        for d in ("east", "west"):
            ddx, _ = FACE[d]
            K.chair(k, tx + ddx, LY + 1, tz, OPP[d])
            k.titik("kursi", tx + ddx, LY + 1, tz, OPP[d])
    K.counter_z(k, 32, 13, 24, LY, "west")
    for z in range(13, 25, 2):
        K.barrel(k, 39, LY + 1, z, "west")
        K.barrel(k, 39, LY + 2, z, "west")
    k.titik("kerja:teko", 34, LY + 1, 18, "west")
    K.furnish_kitchen(k, 33, 34, 40, LY, 38, "north")
    for x in range(27, 33):
        for z in range(31, 38):
            k.put(x, LY + 1, z, "wood_slab", {"type": "top", "waterlogged": "false"})
    k.kotak("panggung", 27, LY + 1, 31, 32, LY + 1, 37, "north")
    K.drum(k, 28, LY + 2, 36, "north")
    k.titik("penonton", 24, LY + 1, 34, "east")
    # upstairs: six rooms for rent
    K.stair_flight(k, x0 + 1, LY + 1, 34, "north", 6, width=2)
    for x in (x0 + 1, x0 + 2):
        for z in range(28, 35):
            k.put(x, LY + 6, z, "minecraft:air")
    for i in range(6):
        rx0 = 9 + (i % 3) * 10
        rz0 = 12 if i < 3 else 27
        for x in range(rx0, rx0 + 9):
            for dy in range(1, 5):
                k.put(x, LY + 6 + dy, rz0 + 9 if i < 3 else rz0, "wood")
        for z in range(rz0, rz0 + 10):
            for dy in range(1, 5):
                k.put(rx0 + 9, LY + 6 + dy, z, "wood")
        dz = rz0 + 9 if i < 3 else rz0
        ruang(k, rx0, z0 + 1 if i < 3 else rz0 + 1, rx0 + 8, rz0 + 8 if i < 3 else z1 - 1, LY + 6, "inap", isi=0.6)
        k.put(rx0 + 4, LY + 7, dz, "minecraft:air")
        k.put(rx0 + 4, LY + 8, dz, "minecraft:air")
        bz = rz0 + 1 if i < 3 else rz0 + 8
        K.bed(k, rx0 + 2, LY + 7, bz, "north" if i < 3 else "south")
        k.titik("kasur", rx0 + 2, LY + 7, bz, "south" if i < 3 else "north")
        K.chest(k, rx0 + 6, LY + 7, bz, None, "south" if i < 3 else "north")
        K.lit(k, rx0 + 4, LY + 12, rz0 + 4, chain=2)
    # the broken anchor over the door
    k.put(23, LY + 7, z0 - 1, "minecraft:iron_block")
    k.put(23, LY + 6, z0 - 1, "minecraft:anvil", {"facing": "east"})
    k.put(22, LY + 8, z0 - 1, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
    k.put(24, LY + 8, z0 - 1, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
    for x in (14, 20, 26):
        K.lit(k, x, LY + 6, 20, chain=1, role="lamp")
        K.lit(k, x, LY + 6, 30, chain=1)
    k.jangkar(35.5, LY + 1, 20.5, "kotabesar/layanan", yaw=270.0, layanan="penginapan", peran="PEMILIK_PENGINAPAN")
    k.jangkar(36.5, LY + 1, 33.5, "kotabesar/layanan", yaw=0.0, layanan="rumah_makan", peran="KOKI")
    k.jangkar(17.5, LY + 1, 23.5, "pelabuhan/tokoh", yaw=90.0, tokoh="a_lan", peran="PELAUT")
    k.kotak("bangunan", x0, LY, z0, x1, top, z1, "north", nama="kedai_jangkar_patah")
    k.jalur([(24, 3), (24, 9), (24, 20), (30, 28)], jalur="kedai")

    # ---------------------------------------------------------------- (1,0) Lorong Tali and the crew rows
    for x in range(52, 94):
        for z in range(6, 13):
            k.put(x, LY, z, "minecraft:packed_mud" if (x + z) % 4 else "path_alt")
    for x in range(52, 94, 4):
        for z in (6, 12):
            for dy in range(1, 5):
                k.put(x, LY + dy, z, "minecraft:spruce_fence")
    for x in range(52, 94):
        for z in range(6, 13):
            k.put(x, LY + 5, z, "minecraft:spruce_slab" if (x + z) % 3 else "minecraft:dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
        for z in (8, 10):
            k.put(x, LY + 2, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
    for x in (54, 61, 68, 75, 82, 89):
        k.titik("kerja:jala", x, LY + 1, 9, "north")
        K.barrel(k, x, LY + 1, 7, "east")
    k.put(92, LY + 1, 9, "minecraft:grindstone", {"face": "floor", "facing": "west"})
    for i in range(4):
        rx = 52 + i * 10
        rumah_merah(k, rx, 18, rx + 8, 28, LY, 2, "south", beds=2, nama="rumah_awak_deret")
    k.put(56, LY + 1, 36, "minecraft:water_cauldron", {"level": "3"})
    jala_jemur(k, 66, 34, LY, 7, "x")
    k.jangkar(72.5, LY + 1, 9.5, "pelabuhan/peran", yaw=0.0, peran="PELAUT")
    k.titik("obrol", 80, LY + 1, 36, "west")
    k.titik("main", 86, LY + 1, 38, "north")

    # ---------------------------------------------------------------- (0,1)+(1,1) sailor and fisher lanes
    kinds = [0, 2, 1, 3, 4, 0, 3, 2]
    spots = [(6, 54), (20, 54), (34, 52), (6, 74), (20, 76), (54, 54), (68, 56), (82, 54)]
    for v, (sx, sz) in zip(kinds, spots):
        rumah_awak(k, sx, sz, v)
    for v, (sx, sz) in zip((1, 4, 0), ((54, 74), (68, 76), (82, 74))):
        rumah_awak(k, sx, sz, v)
    K.well(k, 46, 70)
    k.titik("obrol", 44, LY + 1, 72, "east")
    k.titik("main", 48, LY + 1, 66, "south")
    # the family shrine of the Sea Goddess (a small roofed altar)
    for x in range(34, 41):
        for z in range(76, 82):
            k.put(x, LY, z, "path")
    K.podium(k, 35, 77, 39, 80, LY, 1)
    for x, z in ((35, 77), (39, 77), (35, 80), (39, 80)):
        for dy in range(2, 5):
            k.put(x, LY + dy, z, "red")
    k.put(37, LY + 2, 79, "minecraft:quartz_pillar", {"axis": "y"})
    k.put(37, LY + 3, 79, "minecraft:light_blue_terracotta")
    k.put(37, LY + 4, 79, "minecraft:chiseled_quartz_block")
    candle(k, 36, LY + 2, 78, 3, "red")
    candle(k, 38, LY + 2, 78, 3, "red")
    K.gable(k, 35, LY + 5, 77, 39, 80, along="x", overhang=1, pearl=True)
    k.titik("dupa", 37, LY + 1, 76, "south")
    for x in range(8, 90, 16):
        K.flowerbed(k, x, 92, x + 3, 93, LY, kerb=True)
    k.jalur([(24, 48), (24, 70), (46, 70), (70, 70), (70, 95)], jalur="lorong")
    simpan(k, "kota_awak", {(0, 0): "kedai_jangkar_patah", (1, 0): "lorong_tali", (0, 1): "lorong_barat",
                            (1, 1): "lorong_timur"},
           {(0, 0): ("J", "J", "J", "T"), (1, 0): ("J", "J", "J", "J"), (0, 1): ("J", "J", "J", "T"),
            (1, 1): ("J", "J", "J", "J")})


def d_pasar_laut():
    """Pasar Laut: Pasar Seberang (the open hall of foreign goods, 16 kiosks), Alun Pelabuhan with the giant anchor and
    the notice board (the town's site stone), and Kedai Teh Ombak."""
    k = kanvas("pasar_laut", 40)
    k.lights = []
    tanah(k, tanggul=True)
    # ---------------------------------------------------------------- (0,0)+(1,0) Pasar Seberang
    hx0, hz0, hx1, hz1 = 8, 10, 87, 39
    K.podium(k, hx0, hz0, hx1, hz1, LY, 1, steps=("north", "south"), step_w=7)
    f = LY + 1
    for x in range(hx0 + 1, hx1):
        for z in range(hz0 + 1, hz1):
            k.put(x, f, z, "paving" if (x * 3 + z) % 9 else "paving_alt")
    for x in range(hx0 + 1, hx1, 4):
        for z in (hz0 + 1, hz1 - 1, (hz0 + hz1) // 2):
            for dy in range(1, 7):
                k.put(x, f + dy, z, "red")
            k.put(x, f + 1, z, "kerb_chisel")
    K.beam_band(k, hx0 + 1, hz0 + 1, hx1 - 1, hz1 - 1, f + 7)
    for x in range(hx0 + 2, hx1 - 1):
        for z in range(hz0 + 2, hz1 - 1):
            k.put(x, f + 7, z, "wood_slab", {"type": "top", "waterlogged": "false"})
    top = atap(k, hx0 + 1, hz0 + 1, hx1 - 1, hz1 - 1, f + 8, along="x", overhang=2)
    goods_rows = [("minecraft:decorated_pot", "minecraft:flower_pot"), ("minecraft:light_blue_stained_glass", "minecraft:glass"),
                  ("minecraft:white_wool", "minecraft:blue_wool"), ("minecraft:barrel", "minecraft:hay_block")]
    n = 0
    for i in range(8):
        sx = hx0 + 4 + i * 10
        for row, (z, face) in enumerate(((hz0 + 6, "south"), (hz1 - 6, "north"))):
            g = goods_rows[(i + row) % len(goods_rows)]
            spot = K.stall(k, sx, z, face, y=f, w=3, cloth=("minecraft:red_wool", "minecraft:yellow_wool") if (i + row) % 2
                           else ("cloth", "cloth_alt"), goods=g)
            k.titik("lapak", spot[0], f + 1, spot[1], face)
            n += 1
    for x in range(hx0 + 6, hx1 - 4, 8):
        K.lit(k, x, f + 7, (hz0 + hz1) // 2 - 3, chain=1, role="lamp")
        K.lit(k, x, f + 7, (hz0 + hz1) // 2 + 3, chain=1)
    K.notice_board(k, hx1 - 6, (hz0 + hz1) // 2, "west", y=f, w=5)
    k.titik("obrol", 40, f + 1, 24, "east")
    k.titik("obrol", 56, f + 1, 24, "west")
    k.jangkar(46.5, f + 1, 24.5, "pelabuhan/peran", yaw=0.0, peran="PEDAGANG_SEBERANG", layanan="toko_seberang")
    k.jangkar(22.5, f + 1, 24.5, "pelabuhan/peran", yaw=90.0, peran="PEDAGANG_SEBERANG")
    k.jangkar(70.5, f + 1, 24.5, "pelabuhan/peran", yaw=270.0, peran="PETUGAS_BEA")
    k.kotak("bangunan", hx0, LY, hz0, hx1, top, hz1, "south", nama="pasar_seberang")
    k.jalur([(48, 3), (48, 12), (48, 24), (48, 37), (48, 47)], jalur="pasar")

    # ---------------------------------------------------------------- (0,1) Alun Pelabuhan
    ax0, az0, ax1, az1 = 4, 52, 43, 91
    K.paving(k, ax0, az0, ax1, az1, LY, role="minecraft:polished_diorite", alt="minecraft:polished_andesite", alt_chance=0.2,
             kerb_edge=True)
    for x in range(ax0, ax1 + 1, 6):
        for z in (az0, az1):
            K.stone_lantern(k, x, LY + 1, z)
    for z in range(az0 + 6, az1, 6):
        for x in (ax0, ax1):
            K.stone_lantern(k, x, LY + 1, z)
    cx, cz = (ax0 + ax1) // 2, (az0 + az1) // 2
    jangkar_raksasa(k, cx, LY, cz)
    K.notice_board(k, cx - 2, az0 + 4, "south", w=5)
    for (bx, bz, f2) in ((cx - 8, cz, "east"), (cx + 8, cz, "west"), (cx, cz + 8, "north")):
        K.bench(k, bx, bz, f2, n=3)
        k.titik("kursi", bx, LY + 1, bz, f2)
    for (tx, tz) in ((ax0 + 4, az0 + 4), (ax1 - 4, az0 + 4), (ax0 + 4, az1 - 4), (ax1 - 4, az1 - 4)):
        K.tree(k, tx, LY + 1, tz, "pine", 7)
    k.jangkar(cx + 0.5, LY + 2, cz + 4.5, "kota/situs", yaw=0.0, situs="kota/kota_tema", tema="pelabuhan", tier=4, protect=100)
    k.jangkar(cx + 0.5, LY + 1, cz - 6.5, "kota/alun", yaw=0.0, protect=64)
    k.jangkar(cx - 0.5, LY + 1, az0 + 6.5, "kota/papan", yaw=0.0)
    k.jangkar(cx + 3.5, LY + 1, cz + 3.5, "kota_tema/situs", yaw=0.0, tema="pelabuhan", protect=96)
    k.jangkar(cx - 3.5, LY + 1, cz + 3.5, "kota_tema/tiba", yaw=180.0)
    k.jangkar(cx + 0.5, LY + 1, az1 - 3.5, "kota_tema/zona", yaw=0.0, distrik="pasar_laut", protect=72)
    for (ox, oz) in ((cx - 4, cz - 4), (cx + 4, cz + 4), (cx - 6, cz + 6)):
        k.titik("obrol", ox, LY + 1, oz, "south")
    k.titik("main", cx + 6, LY + 1, cz - 6, "west")
    k.titik("tiba", cx, LY + 1, cz + 10, "north")
    k.kotak("panggung", cx - 4, LY + 1, cz - 4, cx + 4, LY + 1, cz + 4, "south")

    # ---------------------------------------------------------------- (1,1) Kedai Teh Ombak
    tx0, tz0, tx1, tz1 = 54, 56, 87, 83
    a = K.aula(k, tx0, tz0, tx1, tz1, h=5, podium_h=1, teras=2, roof="gable", door_sides=("west", "north"), door_w=1,
               along="x")
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    ruang(k, ix0, iz0, ix1, iz1, fl, "teh", isi=0.45)
    ekor_walet(k, tx0 + 1, tz0 + 1, tx1 - 1, tz1 - 1, a["top"], "x", 1)
    for i, (px, pz) in enumerate(((60, 63), (66, 63), (72, 63), (60, 70), (66, 70), (72, 70), (78, 66))):
        K.low_table(k, px, fl + 1, pz)
        for d in ("north", "south"):
            ddx, ddz = FACE[d]
            carpet(k, px + ddx, fl + 1, pz + ddz, "green" if i % 2 else "lime")
            k.titik("kursi_lantai", px + ddx, fl + 1, pz + ddz, OPP[d])
    K.counter(k, 60, 76, iz1 - 2, fl, "north")
    for x in range(60, 77, 2):
        pot(k, x, fl + 1, iz1)
    k.put(68, fl + 1, iz1, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
    k.titik("kerja:teko", 68, fl + 1, iz1 - 1, "north")
    k.jangkar(66.5, fl + 1, iz1 - 0.5, "kota/teh", yaw=180.0)
    k.jangkar(75.5, fl + 1, 70.5, "kota/kursi_pengembara", yaw=270.0)
    k.jangkar(58.5, fl + 1, 66.5, "kota/kursi_pengembara", yaw=90.0)
    k.kotak("bangunan", tx0, LY, tz0, tx1, a["top"], tz1, "west", nama="kedai_teh_ombak")
    K.garden(k, 56, 86, 86, 93, trees=("cherry", "pine"), pond_too=False)
    simpan(k, "pasar_laut", {(0, 0): "pasar_seberang_barat", (1, 0): "pasar_seberang_timur", (0, 1): "alun_pelabuhan",
                             (1, 1): "kedai_teh_ombak"},
           {(0, 0): ("J", "J", "J", "J"), (1, 0): ("J", "J", "J", "J"), (0, 1): ("J", "J", "J", "J"),
            (1, 1): ("J", "J", "J", "J")}, utuh=("alun_pelabuhan",))


def d_gudang_dagang():
    """Gudang Dagang: the three-storey shared warehouse with its hoist, the Merchant Council hall, the bank branch with
    the guild board copy, and the Sky Mooring Tower with two platforms for flying ships."""
    k = kanvas("gudang_dagang", 64)
    k.lights = []
    tanah(k, tanggul=True)
    # ---------------------------------------------------------------- (0,0) Gudang Bersama
    x0, z0, x1, z1 = 5, 8, 42, 40
    for t in range(3):
        f = LY + t * 6
        dinding_merah(k, x0, z0, x1, z1, f, 5, jendela=4)
        ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "gudang", isi=0.55, rug=False)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                k.put(x, f, z, "minecraft:spruce_planks" if t else "path_alt")
        for x in range(x0 + 4, x1 - 2, 5):
            for z in range(z0 + 3, z1 - 2, 6):
                for dy in range(1, 4):
                    K.barrel(k, x, f + dy, z, ("up", "north", "east")[(x + z + dy) % 3])
                    K.barrel(k, x + 1, f + dy, z, "up")
        if t < 2:
            for x in (x0 + 1, x0 + 2):
                for z in range(z0 + 2, z0 + 9):
                    k.put(x, f + 6, z, "minecraft:air")
            K.stair_flight(k, x0 + 1, f + 1, z0 + 8, "north", 6, width=2)
        K.lit(k, (x0 + x1) // 2, f + 6, (z0 + z1) // 2, chain=1)
    pintu(k, x0, z0, x1, z1, LY, "north", 3)
    pintu(k, x0, z0, x1, z1, LY, "east", 3)
    # the hoist: a beam out of the top floor, a chain and a hanging crate
    for i in range(3):
        k.put(24, LY + 17, z0 - i, "beam", {"axis": "z"})
    for dy in range(6, 16):
        k.put(24, LY + dy, z0 - 3, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    K.barrel(k, 24, LY + 5, z0 - 3, "up")
    pintu(k, x0, z0, x1, z1, LY + 12, "north", 1)
    top = atap(k, x0, z0, x1, z1, LY + 18, along="x", overhang=1, tile=True)
    k.titik("kerja:rak", 20, LY + 1, 20, "north")
    k.titik("kerja:rak", 30, LY + 7, 30, "south")
    k.titik("kerja:timbangan", 24, LY + 1, z0 + 2, "north")
    k.jangkar(24.5, LY + 1, z0 + 3.5, "pelabuhan/gudang", yaw=180.0, peran="KULI")
    k.kotak("bangunan", x0, LY, z0, x1, top, z1, "north", nama="gudang_bersama")

    # ---------------------------------------------------------------- (1,0) Balai Dewan Saudagar
    a = K.aula(k, 52, 8, 92, 40, h=6, podium_h=2, teras=2, roof="hip", door_sides=("north", "west"), door_w=3, tiers=2)
    ruang(k, *a["inner"], a["f"], "kantor", isi=0.45)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    for x in range(ix0 + 4, ix1 - 3):
        K.table(k, x, fl + 1, 24, "minecraft:red_carpet")
    for i, x in enumerate(range(ix0 + 4, ix1 - 3, 2)):
        if i >= 12:
            break
        for z, face in ((23, "south"), (25, "north")):
            K.chair(k, x, fl + 1, z, face)
            k.titik("kursi", x, fl + 1, z, face)
    K.chair(k, ix1 - 2, fl + 1, 24, "west")
    k.titik("kursi", ix1 - 2, fl + 1, 24, "west")
    K.bookshelf_wall(k, ix0, iz1, ix0 + 10, iz1, fl + 1, fl + 3, face="north")
    for x in range(ix0 + 1, ix0 + 10):
        k.put(x, fl + 1, iz1 - 2, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"}) if x % 3 == 0 else None
    k.titik("meja_tulis", ix0 + 3, fl + 1, iz1 - 3, "north")
    K.wall_banner(k, ix1, fl + 4, 24, "west", "blue")
    k.jangkar(ix1 - 1.5, fl + 1, 24.5, "pelabuhan/tokoh", yaw=90.0, tokoh="zhao_wanli", peran="PEDAGANG_SEBERANG")
    k.jangkar(ix0 + 4.5, fl + 1, 21.5, "pelabuhan/peran", yaw=0.0, peran="PEDAGANG_SEBERANG", layanan="kontrak")
    k.kotak("bangunan", 52, LY, 8, 92, a["top"], 40, "west", nama="balai_dewan_saudagar")

    # ---------------------------------------------------------------- (0,1) bank branch and guild board copy
    r = rumah_merah(k, 6, 54, 40, 76, LY, 1, "north", beds=0, nama="cabang_rumah_uang", h=5, isi=False)
    K.counter(k, 12, 34, 64, LY, "north")
    for x in (14, 23, 32):
        K.chair(k, x, LY + 1, 66, "north")
        k.titik("meja_tulis", x, LY + 1, 66, "north")
    for x in range(10, 37, 3):
        K.chest(k, x, LY + 1, 75, None, "north")
    k.put(37, LY + 1, 70, "minecraft:iron_block")
    k.put(37, LY + 2, 70, "minecraft:gold_block")
    for x in (14, 24, 34):
        K.lit(k, x, LY + 6, 68, chain=1)
    k.jangkar(23.5, LY + 1, 66.5, "kotabesar/layanan", yaw=180.0, layanan="bank_cabang", peran="BANKIR")
    K.notice_board(k, 12, 84, "north", w=7)
    K.notice_board(k, 26, 84, "north", w=7)
    k.jangkar(23.5, LY + 1, 82.5, "kotabesar/layanan", yaw=180.0, layanan="papan_gilda", peran="PETUGAS_GILDA")
    k.titik("obrol", 20, LY + 1, 80, "south")
    _ = r

    # ---------------------------------------------------------------- (1,1) Menara Tambat Langit
    mx0, mz0, mx1, mz1 = 62, 62, 73, 73
    for y in range(LY + 1, LY + 29):
        for x in range(mx0, mx1 + 1):
            for z in range(mz0, mz1 + 1):
                if not (x in (mx0, mx1) or z in (mz0, mz1)):
                    continue
                corner = x in (mx0, mx1) and z in (mz0, mz1)
                if corner:
                    log(k, x, y, z, "minecraft:stripped_dark_oak_log")
                elif y <= LY + 8:
                    k.put(x, y, z, "base")
                elif (y - LY) % 6 == 0:
                    log(k, x, y, z, "beam", "x" if z in (mz0, mz1) else "z")
                else:
                    k.put(x, y, z, "brick" if (x + y) % 5 else "red")
    k.air(mx0 + 1, LY + 1, mz0 + 1, mx1 - 1, LY + 28, mz1 - 1)
    pintu(k, mx0, mz0, mx1, mz1, LY, "west", 1)
    perim = []
    ax0, az0, ax1, az1 = mx0 + 1, mz0 + 1, mx1 - 1, mz1 - 1
    for x in range(ax0, ax1 + 1):
        perim.append((x, az0, "east"))
    for z in range(az0 + 1, az1 + 1):
        perim.append((ax1, z, "south"))
    for x in range(ax1 - 1, ax0 - 1, -1):
        perim.append((x, az1, "west"))
    for z in range(az1 - 1, az0, -1):
        perim.append((ax0, z, "north"))
    for i in range(28):
        px, pz, face = perim[i % len(perim)]
        stairs(k, px, LY + 1 + i, pz, "wood_stairs", face)
    # two platforms at +28 reaching north and south, railings, mooring posts, the fuel counter
    py2 = LY + 28
    for x in range(mx0 - 1, mx1 + 2):
        for z in range(mz0 - 9, mz1 + 10):
            k.put(x, py2, z, "deck" if (x + z) % 4 else "deck_alt")
    for x in range(mx0 - 1, mx1 + 2):
        for z in (mz0 - 9, mz1 + 9):
            fence(k, x, py2 + 1, z)
    for z in range(mz0 - 9, mz1 + 10):
        for x in (mx0 - 1, mx1 + 1):
            fence(k, x, py2 + 1, z)
    for z in (mz0 - 9, mz1 + 9):
        for x in (mx0 + 2, mx1 - 2):
            for dy in range(1, 4):
                k.put(x, py2 + dy, z, "minecraft:stripped_dark_oak_log", {"axis": "y"})
            lantern(k, x, py2 + 4, z)
    for (sx, sz) in ((mx0 - 1, mz0 - 6), (mx0 - 1, mz1 + 6)):
        for dz in (-1, 0, 1):
            k.put(sx, py2 - dz - 2, sz + dz, "beam", {"axis": "x"})
    k.put(mx1, py2 + 1, mz0 + 4, "minecraft:barrel", {"facing": "up", "open": "false"})
    K.counter_z(k, mx1, mz0 + 2, mz0 + 6, py2, "west")
    k.titik("meja_tulis", mx1 - 1, py2 + 1, mz0 + 4, "east")
    for (x, z) in ((mx0 + 3, mz0 - 5), (mx0 + 3, mz1 + 5)):
        k.titik("jaga", x, py2 + 1, z, "north" if z < mz0 else "south")
    k.titik("pandang", (mx0 + mx1) // 2, py2 + 1, mz0 - 8, "north")
    K.hip(k, mx0, py2 + 6, mz0, mx1, mz1, overhang=1, top="spire")
    for x in range(mx0, mx1 + 1):
        for z in range(mz0, mz1 + 1):
            k.put(x, py2 + 5, z, "wood")
    for (x, z) in ((mx0, mz0), (mx1, mz0), (mx0, mz1), (mx1, mz1)):
        for dy in range(1, 5):
            k.put(x, py2 + dy, z, "minecraft:stripped_dark_oak_log", {"axis": "y"})
    k.jangkar((mx0 + mx1) / 2.0 + 0.5, py2 + 1, mz0 - 5.5, "pelabuhan/tambat_langit", yaw=180.0, anjungan=0, protect=24)
    k.jangkar((mx0 + mx1) / 2.0 + 0.5, py2 + 1, mz1 + 5.5, "pelabuhan/tambat_langit", yaw=0.0, anjungan=1)
    k.kotak("bangunan", mx0, LY, mz0, mx1, py2 + 12, mz1, "west", nama="menara_tambat_langit")
    for (x, z) in ((54, 54), (86, 88)):
        K.crates(k, x, z, x + 4, z + 2, LY + 1, h=2)
    simpan(k, "gudang_dagang", {(0, 0): "gudang_bersama", (1, 0): "balai_dewan_saudagar", (0, 1): "cabang_rumah_uang",
                                (1, 1): "menara_tambat_langit"},
           {(0, 0): ("J", "J", "J", "J"), (1, 0): ("J", "T", "J", "J"), (0, 1): ("J", "J", "J", "J"),
            (1, 1): ("J", "T", "J", "J")}, utuh=("menara_tambat_langit",))


def patung_dewi(k, cx, y, cz):
    """The Sea Goddess: a robed figure of quartz and pale blue terracotta, six blocks high, on a lotus plinth, hands
    holding a pearl lamp, a crown of waves."""
    K.podium(k, cx - 2, cz - 2, cx + 2, cz + 2, y, 1)
    f = y + 1
    for dy in range(1, 3):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                k.put(cx + dx, f + dy, cz + dz, "minecraft:light_blue_terracotta" if dy == 1 else "minecraft:smooth_quartz")
    for dy in range(3, 6):
        k.put(cx, f + dy, cz, "minecraft:quartz_pillar", {"axis": "y"})
        for dx in (-1, 1):
            k.put(cx + dx, f + dy, cz, "minecraft:light_blue_terracotta" if dy < 5 else "minecraft:smooth_quartz")
    k.put(cx, f + 4, cz + 1, "minecraft:sea_lantern")
    k.put(cx, f + 6, cz, "minecraft:chiseled_quartz_block")
    k.put(cx, f + 7, cz, "minecraft:prismarine_wall")
    for dx in (-1, 1):
        k.put(cx + dx, f + 7, cz, "minecraft:end_rod", {"facing": "up"})
    for d, (dx, dz) in FACE.items():
        stairs(k, cx + 2 * dx, f + 1, cz + 2 * dz, "minecraft:quartz_stairs", OPP[d])


def d_kuil_laut():
    """Kuil Laut: the Temple of the Sea Goddess (courtyard, incense burners, the main hall with her statue and the two
    guardians, the bell pavilion), the five-storey Guide Pagoda on its hill, and the priests' house with incense
    garden."""
    k = kanvas("kuil_laut", 56)
    k.lights = []
    tanah(k)
    # ---------------------------------------------------------------- (0,0)+(1,0) Kuil Dewi Laut
    K.paving(k, 6, 6, 89, 42, LY, role="minecraft:polished_andesite", alt="minecraft:stone_bricks", alt_chance=0.15, kerb_edge=True)
    K.yard_wall(k, 6, 6, 89, 42, h=3, gate="south", gate_w=5)
    a = K.aula(k, 26, 9, 69, 27, h=7, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south",), door_w=3)
    ruang(k, *a["inner"], a["f"], "kuil", isi=0.4, rug=False)
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    patung_dewi(k, 48, fl, iz0 + 3)
    for gx in (40, 56):
        k.put(gx, fl + 1, iz0 + 3, "minecraft:polished_andesite")
        k.put(gx, fl + 2, iz0 + 3, "minecraft:polished_andesite")
        k.put(gx, fl + 3, iz0 + 3, "minecraft:chiseled_stone_bricks")
        k.put(gx, fl + 4, iz0 + 3, "minecraft:polished_andesite")
        stairs(k, gx, fl + 5, iz0 + 3, "kerb_stairs", "north")
    for x in range(43, 54):
        k.put(x, fl + 1, iz0 + 6, "wood_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                                    "waterlogged": "false"})
        if x % 2:
            candle(k, x, fl + 2, iz0 + 6, 3, "red")
    k.titik("dupa", 48, fl + 1, iz0 + 8, "north")
    for x in (44, 52):
        carpet(k, x, fl + 1, iz0 + 9, "red")
        k.titik("kursi_lantai", x, fl + 1, iz0 + 9, "north")
    K.rug(k, 44, iz0 + 8, 52, iz1 - 1, fl + 1, "minecraft:red_carpet", "minecraft:yellow_carpet")
    for x in range(ix0 + 3, ix1 - 2, 6):
        K.lit(k, x, fl + 7, (iz0 + iz1) // 2, chain=2, role="lamp")
    K.incense_burner(k, 48, LY, 33, big=True)
    for x in (34, 62):
        K.incense_burner(k, x, LY, 34)
        K.lion(k, x, LY + 1, 38, "south")
    # bell pavilion (west) and drum (east) in the courtyard
    for x, z in ((12, 14), (17, 14), (12, 19), (17, 19)):
        for dy in range(1, 6):
            k.put(x, LY + dy, z, "red")
    K.gable(k, 12, LY + 6, 14, 17, 19, along="x", overhang=1, pearl=True)
    k.put(14, LY + 5, 16, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    for x in range(12, 18):
        for z in range(14, 20):
            k.put(x, LY + 5, z, "wood") if (x in (12, 17) or z in (14, 19)) else None
    K.drum(k, 82, LY + 1, 16, "west")
    K.drum(k, 82, LY + 1, 20, "west")
    k.titik("kerja:dupa", 48, LY + 1, 31, "north")
    k.titik("tandu", 48, LY + 1, 30, "south")
    for x in (40, 56):
        k.titik("obrol", x, LY + 1, 36, "north")
    k.jangkar(48.5, fl + 1, iz0 + 7.5, "pelabuhan/kuil", yaw=180.0, peran="PENDETA_LAUT", tokoh="pendeta_laut")
    k.jangkar(14.5, LY + 1, 21.5, "pelabuhan/peran", yaw=0.0, peran="PENDETA_LAUT")
    k.kotak("bangunan", 26, LY, 9, 69, a["top"], 27, "south", nama="kuil_dewi_laut")
    k.jalur([(48, 47), (48, 40), (48, 30), (48, iz1 + 2)], jalur="kuil")

    # ---------------------------------------------------------------- (0,1) Pagoda Penunjuk on its hill
    for x in range(4, 45):
        for z in range(52, 93):
            d = math.hypot(x - 24, z - 72)
            if d <= 18:
                hill = 3 if d <= 10 else 2 if d <= 14 else 1
                for dy in range(1, hill + 1):
                    k.put(x, LY + dy - 1 + 1, z, "minecraft:grass_block" if dy == hill else "minecraft:dirt",
                          {"snowy": "false"} if dy == hill else None)
    for i in range(3):
        for x in range(22, 27):
            stairs(k, x, LY + 1 + i, 90 - i * 2, "kerb_stairs", "north")
            k.put(x, LY + 1 + i, 91 - i * 2, "kerb") if i else None
    top = K.pagoda(k, 18, 66, 13, 5, sh=5, y=LY + 3, base_h=2, top="spire", door="south", titik_top="meditasi")
    k.titik("pandang", 24, LY + 33, 72, "north")
    for (tx, tz) in ((8, 56), (40, 58), (10, 88), (38, 86)):
        K.tree(k, tx, LY + 2, tz, "pine", 8)
    k.kotak("bangunan", 17, LY + 3, 65, 31, top, 79, "south", nama="pagoda_penunjuk")

    # ---------------------------------------------------------------- (1,1) priests' house and the incense garden
    rumah_merah(k, 54, 54, 76, 68, LY, 2, "south", beds=3, nama="rumah_pendeta")
    K.garden(k, 54, 72, 90, 92, trees=("pine", "cherry", "pine"), pond_too=True)
    for x in range(56, 90, 4):
        K.bamboo_clump(k, x, 92, LY + 1, 5 + x % 3)
    k.titik("kerja:sabit", 70, LY + 1, 80, "north")
    k.titik("meditasi", 84, LY + 1, 76, "west")
    simpan(k, "kuil_laut", {(0, 0): "kuil_dewi_laut_barat", (1, 0): "kuil_dewi_laut_timur", (0, 1): "pagoda_penunjuk",
                            (1, 1): "rumah_pendeta"},
           {(0, 0): ("J", "J", "J", "T"), (1, 0): ("J", "J", "J", "J"), (0, 1): ("J", "J", "T", "T"),
            (1, 1): ("J", "J", "T", "J")}, utuh=("kuil_dewi_laut_barat", "kuil_dewi_laut_timur", "pagoda_penunjuk"))


def d_gerbang_darat():
    """Gerbang Darat: the gate plaza and guard post, Penginapan Angin Selatan (12 rooms), Rumah Makan Sup Ikan, and the
    caravan yard with stables, carts and the small teleport disc."""
    k = kanvas("gerbang_darat", 40)
    k.lights = []
    tanah(k)
    # ---------------------------------------------------------------- (0,1) gate plaza + guard post (east half)
    K.paving(k, 30, 56, 66, 95, LY, role="path", alt="path_alt", alt_chance=0.2)
    gp = rumah_merah(k, 8, 62, 22, 76, LY, 1, "east", beds=2, nama="pos_jaga_gerbang", isi=False)
    for x in range(10, 21, 2):
        K.bed(k, x, LY + 1, 64, "north")
        k.titik("kasur", x, LY + 1, 64, "south")
    for x in (11, 15, 19):
        K.barrel(k, x, LY + 1, 74, "up")
    K.table(k, 15, LY + 1, 70)
    k.titik("meja_makan", 15, LY + 1, 70, "south")
    k.jangkar(26.5, LY + 1, 88.5, "kota/penjaga", yaw=0.0)
    k.jangkar(48.5, LY + 1, 70.5, "kota/penjaga", yaw=180.0)
    for (x, z) in ((40, 92), (56, 92)):
        K.stone_lantern(k, x, LY + 1, z)
    k.titik("jaga", 44, LY + 1, 90, "south")
    k.titik("jaga", 52, LY + 1, 90, "south")
    K.paifang(k, 48, 60, y=LY, width=13, height=9, facing="south")
    k.jalur([(48, 95), (48, 80), (48, 60), (48, 47), (48, 30), (48, 0)], jalur="gerbang")
    _ = gp

    # ---------------------------------------------------------------- (0,0) Penginapan Angin Selatan
    x0, z0, x1, z1 = 5, 6, 41, 40
    for t in range(2):
        f = LY + t * 6
        dinding_merah(k, x0, z0, x1, z1, f, 5)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                k.put(x, f, z, "minecraft:spruce_planks")
    pintu(k, x0, z0, x1, z1, LY, "east", 3)
    top = atap(k, x0, z0, x1, z1, LY + 12, along="z", overhang=2)
    for i in range(12):
        f = LY + (i // 6) * 6
        j = i % 6
        rx0 = x0 + 1 + (j % 2) * 18
        rz0 = z0 + 1 + (j // 2) * 11
        for z in range(rz0, rz0 + 11):
            for dy in range(1, 5):
                k.put(rx0 + 16 if j % 2 == 0 else rx0 - 1, f + dy, z, "wood")
        for x in range(rx0, rx0 + 16):
            for dy in range(1, 5):
                k.put(x, f + dy, rz0 + 10, "wood")
        k.put(rx0 + 8, f + 1, rz0 + 10, "minecraft:air")
        k.put(rx0 + 8, f + 2, rz0 + 10, "minecraft:air")
        K.bed(k, rx0 + 2, f + 1, rz0 + 2, "north")
        k.titik("kasur", rx0 + 2, f + 1, rz0 + 2, "south")
        ruang(k, rx0, rz0, rx0 + 15, rz0 + 9, f, "inap", isi=0.55)
        K.chest(k, rx0 + 4, f + 1, rz0, None, "south")
        K.table(k, rx0 + 10, f + 1, rz0 + 3)
        K.chair(k, rx0 + 11, f + 1, rz0 + 3, "west")
        K.lit(k, rx0 + 6, f + 5, rz0 + 5, chain=1)
    K.stair_flight(k, x1 - 3, LY + 1, z1 - 2, "north", 6, width=2)
    for x in (x1 - 3, x1 - 2):
        for z in range(z1 - 8, z1 - 1):
            k.put(x, LY + 6, z, "minecraft:air")
    k.jangkar(x1 - 2.5, LY + 1, 22.5, "kotabesar/layanan", yaw=90.0, layanan="penginapan", peran="PEMILIK_PENGINAPAN")
    k.kotak("bangunan", x0, LY, z0, x1, top, z1, "east", nama="penginapan_angin_selatan")

    # ---------------------------------------------------------------- (1,0) Rumah Makan Sup Ikan
    a = K.aula(k, 54, 8, 90, 38, h=5, podium_h=1, teras=2, roof="gable", door_sides=("west", "south"), door_w=3, along="x")
    ix0, iz0, ix1, iz1 = a["inner"]
    fl = a["f"]
    ruang(k, ix0, iz0, ix1, iz1, fl, "kedai", isi=0.4)
    ekor_walet(k, 55, 9, 89, 37, a["top"], "x", 1)
    for (tx, tz) in ((62, 16), (70, 16), (78, 16), (62, 24), (70, 24), (78, 24), (66, 30), (74, 30)):
        K.furnish_dining(k, tx, tz, fl, n=4)
    kz = iz0
    for x in range(ix1 - 6, ix1 + 1):
        k.put(x, fl + 1, kz, "minecraft:smoker" if x % 2 else "minecraft:cauldron", {"facing": "south", "lit": "false"} if x % 2 else None)
    k.titik("kerja:kuali", ix1 - 3, fl + 1, kz + 1, "north")
    k.jangkar(ix1 - 3.5, fl + 1, kz + 2.5, "kotabesar/layanan", yaw=180.0, layanan="rumah_makan", peran="KOKI")
    k.kotak("bangunan", 54, LY, 8, 90, a["top"], 38, "west", nama="rumah_makan_sup_ikan")

    # ---------------------------------------------------------------- (1,1) Halaman Kafilah Darat
    for x in range(52, 92):
        for z in range(52, 92):
            k.put(x, LY, z, "minecraft:coarse_dirt" if (x * 7 + z * 3) % 5 == 0 else "minecraft:dirt_path")
    for x in range(54, 72):
        for dy in range(1, 4):
            k.put(x, LY + dy, 54, "minecraft:spruce_planks" if dy < 3 else "minecraft:spruce_slab")
    for x in range(54, 72, 3):
        for z in range(55, 61):
            if x % 6 == 0:
                k.put(x, LY + 1, z, "minecraft:spruce_fence")
        k.put(x + 1, LY + 1, 58, "minecraft:hay_block", {"axis": "y"})
        k.put(x + 2, LY + 1, 56, "minecraft:water_cauldron", {"level": "2"}) if x % 6 else None
    for x in range(54, 72):
        for z in range(54, 62):
            k.put(x, LY + 4, z, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for (cx, cz) in ((60, 72), (66, 80)):
        for dx in (-1, 0, 1):
            k.put(cx + dx, LY + 1, cz, "minecraft:spruce_slab", {"type": "top", "waterlogged": "false"})
        k.put(cx - 2, LY + 1, cz, "minecraft:spruce_trapdoor", {"facing": "east", "half": "bottom", "open": "true",
                                                               "powered": "false", "waterlogged": "false"})
        K.barrel(k, cx, LY + 2, cz, "up")
        k.put(cx + 1, LY + 2, cz, "minecraft:hay_block", {"axis": "x"})
    # the teleport disc (9 x 9 jade ring) served by 38KS
    dcx, dcz = 82, 80
    for x in range(dcx - 4, dcx + 5):
        for z in range(dcz - 4, dcz + 5):
            d = math.hypot(x - dcx, z - dcz)
            if d <= 4.5:
                k.put(x, LY, z, "minecraft:prismarine_bricks" if d > 3.4 else "minecraft:dark_prismarine" if d > 1.5 else "minecraft:sea_lantern")
    for (x, z) in ((dcx - 4, dcz - 4), (dcx + 4, dcz - 4), (dcx - 4, dcz + 4), (dcx + 4, dcz + 4)):
        K.stone_lantern(k, x, LY + 1, z)
    k.jangkar(dcx + 0.5, LY + 1, dcz + 0.5, "kotabesar/cakram", yaw=0.0, protect=12)
    k.titik("kerja:sapu", 64, LY + 1, 66, "north")
    k.titik("obrol", 70, LY + 1, 76, "west")
    simpan(k, "gerbang_darat", {(0, 0): "penginapan_angin_selatan", (1, 0): "rumah_makan_sup_ikan",
                                (0, 1): "alun_gerbang", (1, 1): "halaman_kafilah"},
           {(0, 0): ("J", "J", "J", "J"), (1, 0): ("J", "J", "J", "J"), (0, 1): ("J", "J", "J", "J"),
            (1, 1): ("J", "J", "T", "J")})


def kubah(k, cx, y, cz, r=3):
    """A small cut-sandstone dome (the foreign merchants' houses), with a gold finial."""
    for dy in range(r + 1):
        rr = math.sqrt(max(0.0, r * r - dy * dy)) + 0.3
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                d = math.hypot(x - cx, z - cz)
                if rr - 1.0 < d <= rr:
                    k.put(x, y + dy, z, "minecraft:cut_sandstone" if dy < r else "minecraft:smooth_sandstone")
    k.put(cx, y + r + 1, cz, "minecraft:gold_block")


def rumah_seberang(k, x0, z0, w=10, d=10, f=LY, beds=2, nama="rumah_seberang"):
    """A foreign merchant's house: smooth sandstone walls with pointed-arch windows (stairs), a dome on a flat roof,
    carpets and cushions inside."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    for y in range(f + 1, f + 6):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    k.put(x, y, z, "minecraft:smooth_sandstone" if y > f + 1 else "minecraft:cut_sandstone")
    k.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + 5, z1 - 1)
    ruang(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, "rumah", isi=0.45, rug=False)
    for x in range(x0 + 2, x1 - 1, 3):
        for z, face in ((z0, "north"), (z1, "south")):
            k.put(x, f + 2, z, "minecraft:glass_pane")
            k.put(x, f + 3, z, "minecraft:glass_pane")
            stairs(k, x, f + 4, z, "minecraft:sandstone_stairs", "east" if x % 2 else "west", half="top")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.put(x, f + 6, z, "minecraft:cut_sandstone_slab", {"type": "bottom", "waterlogged": "false"}) if (x in (x0, x1) or z in (z0, z1)) \
                else k.put(x, f + 6, z, "minecraft:smooth_sandstone")
            if x in (x0, x1) or z in (z0, z1):
                k.put(x, f + 6, z, "minecraft:sandstone_wall")
    kubah(k, (x0 + x1) // 2, f + 6, (z0 + z1) // 2, min(3, (min(w, d) - 3) // 2))
    door = pintu(k, x0, z0, x1, z1, f, "south", 1)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            k.put(x, f, z, "minecraft:spruce_planks")
            carpet(k, x, f + 1, z, "red" if (x + z) % 3 else "orange") if (z0 + 2 <= z <= z1 - 2 and x0 + 2 <= x <= x1 - 2) else None
    for i in range(beds):
        bx = x0 + 1 + i * 2
        K.bed(k, bx, f + 1, z0 + 2, "north")
        k.titik("kasur", bx, f + 1, z0 + 2, "south")
    K.low_table(k, (x0 + x1) // 2, f + 1, (z0 + z1) // 2 + 1)
    k.titik("kursi_lantai", (x0 + x1) // 2 - 1, f + 1, (z0 + z1) // 2 + 1, "east")
    K.lit(k, (x0 + x1) // 2, f + 6, (z0 + z1) // 2, chain=1)
    k.titik("pintu", door[0], f + 1, door[1], "south")
    k.kotak("bangunan", x0, f, z0, x1, f + 10, z1, "south", nama=nama)


def d_hunian_saudagar():
    """Hunian Saudagar: Zhao Wanli's two-storey red-brick courtyard house with rock garden and porcelain hall, the
    foreign merchants' quarter with domed houses and their quiet prayer house, and the craftsmen's row with the
    workshop of the repair crews."""
    k = kanvas("hunian_saudagar", 40)
    k.lights = []
    tanah(k)
    # ---------------------------------------------------------------- (0,0)+(1,0) Rumah Saudagar Zhao
    K.paving(k, 4, 4, 91, 43, LY, role="minecraft:polished_andesite", alt="minecraft:polished_diorite", alt_chance=0.2)
    K.yard_wall(k, 4, 4, 91, 43, h=4, gate="south", gate_w=3)
    main = rumah_merah(k, 24, 8, 71, 20, LY, 2, "south", beds=4, nama="rumah_zhao_induk")
    for side, (wx0, wx1) in enumerate(((8, 20), (75, 87))):
        rumah_merah(k, wx0, 10, wx1, 36, LY, 1, "east" if side == 0 else "west", beds=2, nama="rumah_zhao_sayap")
    ix0, iz0, ix1, iz1 = main["inner"]
    # the porcelain hall: shelves of pots, a guest table, an ancestor altar
    for x in range(ix0 + 2, ix1 - 1, 2):
        trapdoor(k, x, LY + 2, iz0, "wood_trapdoor", "south")
        pot(k, x, LY + 3, iz0)
    for (tx, tz) in ((40, 15), (55, 15)):
        K.furnish_dining(k, tx, tz, LY, n=4)
    K.wall_banner(k, 47, LY + 4, iz0, "south", "red")
    K.rug(k, 42, iz0 + 1, 53, iz1, LY + 1, "minecraft:red_carpet", "minecraft:yellow_carpet")
    k.titik("dupa", 48, LY + 1, iz0 + 1, "north")
    # rock garden and pond in the courtyard
    K.pond(k, 48, 31, 5, 3, LY, lilies=6)
    for (rx, rz, h) in ((38, 28, 3), (58, 30, 4), (44, 36, 2), (54, 25, 2)):
        for dy in range(1, h + 1):
            k.put(rx, LY + dy, rz, "minecraft:mossy_cobblestone" if dy % 2 else "minecraft:andesite")
    for x in (30, 66):
        K.tree(k, x, LY + 1, 30, "cherry", 5)
    for x in range(36, 61, 6):
        K.pot(k, x, LY + 1, 39)
    k.titik("obrol", 42, LY + 1, 30, "east")
    k.titik("obrol", 54, LY + 1, 33, "west")
    k.titik("main", 48, LY + 1, 38, "north")
    k.jangkar(48.5, LY + 1, 17.5, "pelabuhan/peran", yaw=0.0, peran="PEDAGANG_SEBERANG", layanan="rumah_zhao")

    # ---------------------------------------------------------------- (0,1) Kampung Saudagar Seberang
    for (hx, hz) in ((6, 54), (20, 54), (6, 70), (34, 72)):
        rumah_seberang(k, hx, hz, 11, 11)
    # the quiet prayer house: no statues, carpets, lanterns, a lectern and a small fountain court
    px0, pz0, px1, pz1 = 26, 52, 42, 66
    for y in range(LY + 1, LY + 7):
        for x in range(px0, px1 + 1):
            for z in range(pz0, pz1 + 1):
                if x in (px0, px1) or z in (pz0, pz1):
                    k.put(x, y, z, "minecraft:smooth_sandstone" if y < LY + 6 else "minecraft:cut_sandstone")
    k.air(px0 + 1, LY + 1, pz0 + 1, px1 - 1, LY + 6, pz1 - 1)
    for x in range(px0 + 1, px1):
        for z in range(pz0 + 1, pz1):
            k.put(x, LY, z, "minecraft:smooth_sandstone")
            carpet(k, x, LY + 1, z, "green" if (x - px0) % 4 in (1, 2) else "lime")
            k.put(x, LY + 7, z, "minecraft:cut_sandstone")
    kubah(k, (px0 + px1) // 2, LY + 7, (pz0 + pz1) // 2, 5)
    for x in range(px0 + 3, px1 - 1, 3):
        K.lit(k, x, LY + 7, (pz0 + pz1) // 2, chain=2)
    pintu(k, px0, pz0, px1, pz1, LY, "south", 1)
    k.put((px0 + px1) // 2, LY + 1, pz0 + 2, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    for x in range(px0 + 2, px1 - 1, 3):
        k.titik("kursi_lantai", x, LY + 1, pz0 + 6, "north")
    k.put(34, LY, 70, "minecraft:water", {"level": "0"})
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        k.put(34 + dx, LY + 1, 70 + dz, "minecraft:sandstone_wall")
    k.jangkar(30.5, LY + 1, 60.5, "pelabuhan/tokoh", yaw=180.0, tokoh="ibrahim", peran="PEDAGANG_SEBERANG")
    k.titik("meditasi", 38, LY + 1, 60, "north")
    k.kotak("bangunan", px0, LY, pz0, px1, LY + 13, pz1, "south", nama="rumah_ibadah_seberang")

    # ---------------------------------------------------------------- (1,1) houses and the repair workshop
    for (hx, hz, v) in ((54, 54, 0), (68, 54, 1), (82, 54, 0), (54, 74, 1)):
        rumah_merah(k, hx, hz, hx + 9, hz + 10, LY, 1 + v, "south", beds=2, nama="rumah_tukang")
    w = rumah_merah(k, 68, 72, 90, 88, LY, 1, "west", beds=0, nama="bengkel_tukang", h=5, isi=False)
    for x in range(70, 89, 3):
        k.put(x, LY + 1, 87, "minecraft:smithing_table")
        k.put(x + 1, LY + 1, 87, "minecraft:stonecutter", {"facing": "north"})
    k.put(72, LY + 1, 74, "minecraft:anvil", {"facing": "east"})
    K.crates(k, 82, 74, 88, 78, LY + 1, h=2)
    for x in (73, 80, 86):
        k.titik("kerja:palu", x, LY + 1, 85, "south")
    k.jangkar(76.5, LY + 1, 80.5, "kotabesar/tukang", yaw=270.0)
    _ = w
    simpan(k, "hunian_saudagar", {(0, 0): "rumah_zhao_barat", (1, 0): "rumah_zhao_timur", (0, 1): "kampung_seberang",
                                  (1, 1): "deret_tukang"},
           {(0, 0): ("J", "J", "J", "J"), (1, 0): ("J", "T", "J", "J"), (0, 1): ("J", "J", "T", "J"),
            (1, 1): ("J", "T", "T", "J")})


# ===================================================================================================== DESIGN 42 (42W-T)
# 42.12.1 row tema: the night street, the recruitment office (2 booths), 10 kavling of gaya "pelabuhan", the sea-cliff
# cemetery (tebing makam laut) and the Ghost Market / rentable stall points, all on free lots OUTSIDE the wall inside the
# keep-out zone of the district that registers them (tools/tema_hidup_kit.py; off the 48-grid, no side code).
GAYA42 = {
    "tema": "pelabuhan", "pal": PAL, "pondasi": "minecraft:dirt",
    "tanah": ["minecraft:grass_block", "minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:grass_block",
              "minecraft:rooted_dirt", "minecraft:grass_block"],
    "jalan": ["minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:stone_bricks",
              "minecraft:cracked_stone_bricks", "minecraft:polished_andesite"],
    "alas": "minecraft:polished_diorite", "alas_tangga": "minecraft:polished_diorite_stairs",
    "lantai": ["minecraft:acacia_planks", "minecraft:jungle_planks"],
    "dinding": ["minecraft:bricks", "minecraft:bricks", "minecraft:bricks", "minecraft:white_terracotta"],
    "tiang": "minecraft:stripped_acacia_log", "balok": "minecraft:polished_diorite",
    "jendela": "minecraft:acacia_trapdoor", "pintu": "minecraft:acacia_door", "plafon": "minecraft:acacia_planks",
    "atap": "pelana", "atap_tangga": "minecraft:polished_granite_stairs", "atap_slab": "minecraft:polished_granite_slab",
    "atap_penuh": "minecraft:polished_granite", "parapet": "minecraft:brick_wall", "kubah": "minecraft:cut_sandstone",
    "kain": ("minecraft:white_wool", "minecraft:light_blue_wool"), "warna": "blue", "lampu": "minecraft:lantern",
    "pohon": "oak", "kavling": "pelabuhan", "makam": ("laut", "tebing"),
    "rencana": (("rumah_toko", "gudang", "kedai_teh", "rumah_besar", "kedai_arak", "bengkel", "sekolah"),
                ("rumah_kecil", "menara_jaga", "kuil_kecil", "taman", "rumah_kecil")),
    "bilik_warna": ("blue", "red"),
}
# (district, dx, dz, lot): south band west of the Land Gate (night street, kavling), east of it (office, kavling);
# the cemetery on the shore west of the Kota Awak wall
LOT42 = (("kuil_laut", -48, 62, "hidup42/kavling_barat"), ("kuil_laut", 0, 62, "hidup42/jalan_malam"),
         ("hunian_saudagar", -48, 62, "hidup42/kantor_rekrut"), ("hunian_saudagar", 0, 62, "hidup42/kavling_timur"),
         ("kota_awak", -112, -24, "hidup42/makam"))


def lot42_buat(dry=False):
    """Writes the DESIGN 42 lots and registers them (lapak_sewa 0-4, pasar_hantu 0-3, nisan 0-31)."""
    out = [thk.lot_jalan_malam(F, GAYA42, lapak0=0, hantu0=0),
           thk.lot_kantor_rekrut(F, GAYA42, lapak0=2),
           thk.lot_kavling(F, GAYA42, "hidup42/kavling_barat", 0),
           thk.lot_kavling(F, GAYA42, "hidup42/kavling_timur", 1, hantu=3),
           thk.lot_makam(F, GAYA42, nisan0=0, hantu0=1)]
    if not dry:
        for distrik, dx, dz, name in LOT42:
            daftar_bebas(distrik, dx, dz, name, 0)
    return out


# ===================================================================================================== JSON
def tulis_json():
    states = tl.semua_state()
    tl.tulis_processor(FAM + "/lapuk", tl.processor_gaya(FAM, "dasar", states, lapuk=True))
    tl.tulis_processor(FAM + "/utuh", tl.processor_utuh())
    tl.tulis_processor(FAM + "/gaya_utara", tl.processor_gaya(FAM, "utara", states, lapuk=True))
    tl.tulis_processor(FAM + "/gaya_utara_utuh", tl.processor_gaya(FAM, "utara", states, lapuk=False))
    for p, (templates, utuh) in sorted(POOLS.items()):
        base = "%s%s/%s" % (NS, FAM, "utuh" if utuh else "lapuk")
        sty = "%s%s/%s" % (NS, FAM, "gaya_utara_utuh" if utuh else "gaya_utara")
        tl.tulis_pool(p, [(tl.elemen(t, base), 1) for t in templates])
        tl.tulis_pool(p + "_utara", [(tl.elemen(t, sty), 1) for t in templates])
    for d in ORDER:
        lots = [dict(l, pool=NS + l["pool"]) for l in LOTS[d]]
        s = tl.struktur_tata_letak(RENCANA, d, lots, BIOMA_TAG, gaya=[(UTARA, "utara")],
                                   adaptasi="none" if d in LAUT else "beard_box")
        tl.tulis_wg("structure", "%s/%s" % (F, d), s)


def main():
    d_dermaga_nelayan()
    d_dermaga_agung()
    d_galangan()
    d_kota_awak()
    d_pasar_laut()
    d_gudang_dagang()
    d_kuil_laut()
    d_gerbang_darat()
    d_hunian_saudagar()
    build_walls()
    lot42_buat()
    tulis_json()
    tl.cek_semua()
    n = sum(len(v) for v in LOTS.values())
    print("pelabuhan: %d districts, %d lots placed, %d pools" % (len(ORDER), n, len(POOLS)))


if __name__ == "__main__":
    main()
