"""Shared library of the themed-city generators (DESIGN 38.T, owner 38TK inti; theme generators import it).

Built on tempat_lib (lot rules, titik, side codes) and kota_besar_kit (Kanvas, potong and the detail helpers: hall, aula,
pagoda, stall, garden, pond, trees, furniture ...). A theme generator draws each 96 x H x 96 district on one Kanvas
with the theme palette, cuts it into its four 48-lots with potong_distrik() (side codes set per lot), writes pools per
biome style with tulis_pool_tema(), the structure JSON with tulis_distrik(), and at the very end the style processors
with tulis_processor_tema() (they need every block state the run used).

    import os, sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tempat_tema_lib as ttl
    kit = ttl.kit                                     # kota_besar_kit (hall, stall, garden, ...)

    k = ttl.kanvas("air", "pasar_kanal")             # 96 x 40 x 96, palette ttl.PALET_TEMA["air"]
    ttl.kanal(k, 0, 44, 95, 51)                       # a 6-wide canal (two K half-strips) with quays
    kit.hall(k, 8, 8, 30, 28, ttl.LY, 6)
    lots = ttl.potong_distrik(k, "air", "pasar_kanal", sisi={(0, 0): "JKKJ", (1, 0): "JJKK", ...})
    ids = ttl.simpan_lot(lots)                        # {(i, j): "kultivator:kota_tema/air/pasar_kanal/l00"}
    ttl.tulis_pool_tema("air", "pasar_kanal/l00", [ids[(0, 0)]])
    ttl.tulis_distrik("air", "pasar_kanal", [ttl.lot_inti(0, 0, "kota_tema/air/pasar_kanal/l00"), ...])
    ...
    ttl.tulis_processor_tema("air")                   # once, after every template of the theme is saved
    ttl.cek_semua()

Geometry (docs/progres/kota-TK.md sec 1, RencanaKotaTema): a district is 2 x 2 lots, lot NW corners at dx, dz in
{-48, 0} from the district centre (lot_inti(lx, lz, ...)); wall/tower/gate lots outside the 96 cell, every piece within
+-72 of the centre; keep extra lots off the 48 grid (e.g. dx = 49 or -57) so cek_tempat's grid-hole check stays quiet.
Gunung rows get Distrik.dy 0 / 18 / 36 (DY_GUNUNG, bottom / middle / top row); inside a district use lot "dy" yourself.
Pelabuhan sea row (grid z = -1, laut districts): terrain_adaptation "none"; to reach the sea floor put the deck at
template y = LAUT_LANTAI over piles (tiang_pancang) and give the lot "dy": LAUT_DY (the deck then sits on the street floor).

Side codes (38.T.2.4, checked by cek_tempat.py): J road 3 (edge strip, two lots make a 6 street), K canal (edge strip:
KANAL_AIR water + KANAL_JALAN quay path; two K lots make a 2 x KANAL_AIR = 6 wide canal between two 2-wide quays),
T wall, A open water / sea, G stairs (a rising edge, gunung), B free border (garden, hedge, field). Codes are written
before rotation; the structure's lot "putar" turns them (tempat_lib.putar_sisi).

Every block change of the themed cities happens here at generation; runtime never sets a block (38.T.0 butir 1).
Forbidden in templates (cek_tempat.py): fire, lava, tnt, spawner, lightning_rod, lit campfires outside kitchens.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import kota_besar_kit as kit  # noqa: E402

LY = tl.LANTAI_Y
LOT = tl.LOT
SEL = 96
DY_GUNUNG = {-1: 36, 0: 18, 1: 0}       # Distrik.dy by grid row z (RencanaKotaTema.DY_GUNUNG)
LAUT_LANTAI = 24                         # pier deck height inside a sea-row template
LAUT_DY = -(LAUT_LANTAI - LY)            # lot "dy" that puts that deck on the street floor (-20)
KANAL_AIR = 3                            # canal water blocks per K edge strip (two lots -> 6)
KANAL_JALAN = 2                          # quay path behind the water
KANAL_DALAM = 2                          # canal depth below the street floor
TEMA_JAVA = os.path.join(structgen.REPO, "src", "main", "java", "dev", "kultivator", "dunia", "tempat", "tema", "Tema.java")
TEMA_IDS = ("pelabuhan", "oasis", "air", "gunung", "iblis")


# ================================================================================================ districts (Tema.java)
def _baca_tema():
    """{tema: [{"id", "x", "z", "salt", "laut"}]} parsed from Tema.java (the one source of truth)."""
    src = open(TEMA_JAVA, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'^\s*[A-Z]+\("([a-z_]+)",(.*?)\)\)\s*[,;]', src, re.S | re.M):
        rows = []
        for d in re.finditer(r'new Distrik\("([a-z_]+)",\s*(-?\d+),\s*(-?\d+),\s*(\d+),\s*(true|false)\)', m.group(2)):
            rows.append({"id": d.group(1), "x": int(d.group(2)), "z": int(d.group(3)), "salt": int(d.group(4)),
                         "laut": d.group(5) == "true"})
        out[m.group(1)] = rows
    sat = {}
    for const in ("SALT_RERUNTUHAN_GURUN", "SALT_POS_PEMBURU"):
        mm = re.search(const + r"\s*=\s*(\d+)", src)
        if mm:
            sat[const] = int(mm.group(1))
    return out, sat


DISTRIK, SALT_SATELIT = _baca_tema()


def distrik(tema, did):
    """The district record of Tema.java, or KeyError."""
    for d in DISTRIK[tema]:
        if d["id"] == did:
            return d
    raise KeyError("%s/%s not in Tema.java" % (tema, did))


# ================================================================================================ palettes
def _pal(**kw):
    p = dict(kit.PAL)
    p.update(kw)
    return p


# Role names are those of kota_besar_kit.PAL (so every kit helper works) plus a few theme roles ("sea_wood", "pile",
# "tile_red", "dome", "mud", "sand", "spike", ...). The BASE style of each theme; other styles swap through processors.
PALET_TEMA = {
    # Pelabuhan "selatan": Minnan red brick, white plaster, red swallow-tail roofs, acacia/jungle quays
    "pelabuhan": _pal(
        floor="minecraft:polished_granite", floor_alt="minecraft:bricks", found="minecraft:stone_bricks",
        wall="minecraft:bricks", wall_alt="minecraft:jungle_planks", plaster="minecraft:white_terracotta",
        plaster_alt="minecraft:calcite", pillar="minecraft:red_terracotta", red="minecraft:red_terracotta",
        trim="minecraft:stripped_jungle_log", beam="minecraft:stripped_jungle_log", roof="minecraft:brick_stairs",
        roof_slab="minecraft:brick_slab", roof_full="minecraft:bricks", tile="minecraft:bricks",
        tile_stairs="minecraft:brick_stairs", tile_slab="minecraft:brick_slab", tile_red="minecraft:red_nether_bricks",
        wood="minecraft:jungle_planks", wood_alt="minecraft:acacia_planks", wood_stairs="minecraft:jungle_stairs",
        wood_slab="minecraft:jungle_slab", wood_trapdoor="minecraft:jungle_trapdoor", wood_fence="minecraft:jungle_fence",
        wood_gate="minecraft:jungle_fence_gate", log="minecraft:jungle_log", door="minecraft:jungle_door",
        rail="minecraft:jungle_fence", fence="minecraft:jungle_fence", window="minecraft:jungle_trapdoor",
        sea_wood="minecraft:acacia_planks", sea_slab="minecraft:acacia_slab", sea_stairs="minecraft:acacia_stairs",
        sea_fence="minecraft:acacia_fence", pile="minecraft:stripped_spruce_log", pile_alt="minecraft:stripped_mangrove_log",
        paving="minecraft:polished_granite", paving_alt="minecraft:granite", paving_slab="minecraft:polished_granite_slab",
        paving_stairs="minecraft:polished_granite_stairs", kerb="minecraft:stone_bricks", path="minecraft:polished_granite",
        path_alt="minecraft:bricks", cloth="minecraft:red_wool", cloth_alt="minecraft:light_blue_wool", rug="minecraft:red_carpet",
        leaves="minecraft:jungle_leaves", trunk="minecraft:jungle_log", ground="minecraft:sand", ground_alt="minecraft:grass_block",
        glow="minecraft:lantern", lamp="minecraft:sea_lantern", accent="minecraft:gold_block", jade="minecraft:prismarine_bricks",
        flower_tall="minecraft:rose_bush", bush="minecraft:flowering_azalea"),
    # Oasis "pasir": mud brick, smooth sandstone, cut sandstone domes, orange/red cloth tents, date palms
    "oasis": _pal(
        floor="minecraft:smooth_sandstone", floor_alt="minecraft:cut_sandstone", found="minecraft:sandstone",
        wall="minecraft:mud_bricks", wall_alt="minecraft:packed_mud", plaster="minecraft:smooth_sandstone",
        plaster_alt="minecraft:cut_sandstone", pillar="minecraft:chiseled_sandstone", red="minecraft:orange_terracotta",
        trim="minecraft:stripped_jungle_log", beam="minecraft:stripped_jungle_log", roof="minecraft:sandstone_stairs",
        roof_slab="minecraft:smooth_sandstone_slab", roof_full="minecraft:smooth_sandstone", dome="minecraft:cut_sandstone",
        dome_slab="minecraft:cut_sandstone_slab", tile="minecraft:cut_sandstone", tile_stairs="minecraft:sandstone_stairs",
        tile_slab="minecraft:cut_sandstone_slab", mud="minecraft:packed_mud", sand="minecraft:sand",
        wood="minecraft:jungle_planks", wood_alt="minecraft:acacia_planks", wood_stairs="minecraft:jungle_stairs",
        wood_slab="minecraft:jungle_slab", wood_trapdoor="minecraft:jungle_trapdoor", wood_fence="minecraft:jungle_fence",
        wood_gate="minecraft:jungle_fence_gate", log="minecraft:jungle_log", door="minecraft:jungle_door",
        rail="minecraft:jungle_fence", fence="minecraft:jungle_fence", window="minecraft:jungle_trapdoor",
        paving="minecraft:smooth_sandstone", paving_alt="minecraft:sandstone", paving_slab="minecraft:smooth_sandstone_slab",
        paving_stairs="minecraft:smooth_sandstone_stairs", kerb="minecraft:cut_sandstone", kerb_wall="minecraft:sandstone_wall",
        kerb_stairs="minecraft:sandstone_stairs", kerb_slab="minecraft:sandstone_slab", path="minecraft:smooth_sandstone",
        path_alt="minecraft:sand", cloth="minecraft:orange_wool", cloth_alt="minecraft:red_wool", rug="minecraft:orange_carpet",
        rug_alt="minecraft:red_carpet", leaves="minecraft:jungle_leaves", trunk="minecraft:jungle_log",
        ground="minecraft:sand", ground_alt="minecraft:coarse_dirt", glow="minecraft:lantern", lamp="minecraft:ochre_froglight",
        accent="minecraft:gold_block", jade="minecraft:prismarine", flower_tall="minecraft:sunflower", bush="minecraft:dead_bush"),
    # Air "jiangnan": white walls, black tiles, old dark oak, lattice windows, stepped horse-head walls
    "air": _pal(
        floor="minecraft:stone_bricks", floor_alt="minecraft:mossy_stone_bricks", found="minecraft:stone_bricks",
        wall="minecraft:white_terracotta", wall_alt="minecraft:dark_oak_planks", plaster="minecraft:white_terracotta",
        plaster_alt="minecraft:calcite", pillar="minecraft:stripped_dark_oak_log", red="minecraft:stripped_dark_oak_log",
        trim="minecraft:stripped_dark_oak_log", beam="minecraft:stripped_dark_oak_log", roof="minecraft:deepslate_tile_stairs",
        roof_slab="minecraft:deepslate_tile_slab", roof_full="minecraft:deepslate_tiles", tile="minecraft:deepslate_tiles",
        tile_stairs="minecraft:deepslate_tile_stairs", tile_slab="minecraft:deepslate_tile_slab",
        tile_dark="minecraft:polished_blackstone_bricks", horse_wall="minecraft:stone_brick_wall",
        horse_slab="minecraft:polished_blackstone_brick_slab", paving="minecraft:stone_bricks",
        paving_alt="minecraft:cracked_stone_bricks", paving_slab="minecraft:stone_brick_slab",
        paving_stairs="minecraft:stone_brick_stairs", kerb="minecraft:stone_bricks", quay="minecraft:chiseled_stone_bricks",
        canal_bed="minecraft:gravel", path="minecraft:stone_bricks", path_alt="minecraft:mossy_stone_bricks",
        cloth="minecraft:white_wool", cloth_alt="minecraft:light_blue_wool", rug="minecraft:light_gray_carpet",
        leaves="minecraft:oak_leaves", trunk="minecraft:oak_log", glow="minecraft:lantern", lamp="minecraft:pearlescent_froglight",
        flower_tall="minecraft:peony", bush="minecraft:azalea", accent="minecraft:lapis_block", jade="minecraft:prismarine_bricks"),
    # Gunung "batu": stone bricks, andesite, dark oak tiles, stilt houses on spruce logs, retaining terraces
    "gunung": _pal(
        floor="minecraft:stone_bricks", floor_alt="minecraft:andesite", found="minecraft:stone_bricks",
        wall="minecraft:stone_bricks", wall_alt="minecraft:spruce_planks", plaster="minecraft:white_terracotta",
        plaster_alt="minecraft:calcite", pillar="minecraft:spruce_log", red="minecraft:red_terracotta",
        trim="minecraft:stripped_spruce_log", beam="minecraft:stripped_spruce_log", roof="minecraft:dark_oak_stairs",
        roof_slab="minecraft:dark_oak_slab", roof_full="minecraft:dark_oak_planks", terrace="minecraft:stone_bricks",
        terrace_alt="minecraft:mossy_stone_bricks", stilt="minecraft:spruce_log", paving="minecraft:andesite",
        paving_alt="minecraft:cobblestone", paving_slab="minecraft:andesite_slab", paving_stairs="minecraft:stone_brick_stairs",
        kerb="minecraft:stone_bricks", step="minecraft:stone_brick_stairs", step_slab="minecraft:stone_brick_slab",
        path="minecraft:andesite", path_alt="minecraft:cobblestone", cloth="minecraft:yellow_wool", cloth_alt="minecraft:red_wool",
        rug="minecraft:yellow_carpet", leaves="minecraft:spruce_leaves", trunk="minecraft:spruce_log",
        ground="minecraft:grass_block", ground_alt="minecraft:moss_block", glow="minecraft:lantern", lamp="minecraft:ochre_froglight",
        accent="minecraft:gold_block", jade="minecraft:moss_block", flower_tall="minecraft:lilac", bush="minecraft:azalea"),
    # Iblis "hitam": blackstone, polished basalt, nether bricks, red accents, chains, soul light (never fire)
    "iblis": _pal(
        floor="minecraft:polished_blackstone", floor_alt="minecraft:polished_blackstone_bricks", found="minecraft:blackstone",
        wall="minecraft:polished_blackstone_bricks", wall_alt="minecraft:nether_bricks", plaster="minecraft:black_terracotta",
        plaster_alt="minecraft:gray_terracotta", pillar="minecraft:polished_basalt", red="minecraft:red_nether_bricks",
        trim="minecraft:crimson_hyphae", beam="minecraft:stripped_crimson_stem", roof="minecraft:blackstone_stairs",
        roof_slab="minecraft:blackstone_slab", roof_full="minecraft:blackstone", tile="minecraft:nether_bricks",
        tile_stairs="minecraft:nether_brick_stairs", tile_slab="minecraft:nether_brick_slab", bone="minecraft:bone_block",
        spike="minecraft:iron_bars", spike_tip="minecraft:end_rod", paving="minecraft:polished_basalt",
        paving_alt="minecraft:basalt", paving_slab="minecraft:polished_blackstone_slab",
        paving_stairs="minecraft:polished_blackstone_stairs", kerb="minecraft:polished_blackstone_bricks",
        kerb_wall="minecraft:polished_blackstone_brick_wall", kerb_stairs="minecraft:polished_blackstone_brick_stairs",
        kerb_slab="minecraft:polished_blackstone_brick_slab", wood="minecraft:dark_oak_planks", wood_alt="minecraft:crimson_planks",
        wood_stairs="minecraft:dark_oak_stairs", wood_slab="minecraft:dark_oak_slab", wood_trapdoor="minecraft:dark_oak_trapdoor",
        wood_fence="minecraft:nether_brick_fence", rail="minecraft:iron_chain", fence="minecraft:iron_chain",
        window="minecraft:iron_bars", path="minecraft:polished_basalt", path_alt="minecraft:basalt", glow="minecraft:soul_lantern",
        lamp="minecraft:shroomlight", cloth="minecraft:black_wool", cloth_alt="minecraft:red_wool", rug="minecraft:black_carpet",
        rug_alt="minecraft:red_carpet", leaves="minecraft:dark_oak_leaves", trunk="minecraft:dark_oak_log",
        ground="minecraft:soul_soil", ground_alt="minecraft:coarse_dirt", accent="minecraft:crying_obsidian",
        jade="minecraft:obsidian", flower_tall="minecraft:wither_rose", bush="minecraft:crimson_roots", bed="minecraft:black_bed"),
}
for _t, _p in PALET_TEMA.items():
    tl.PALET["kota_tema_" + _t] = _p


# ================================================================================================ biome styles
def _kayu(a, b):
    return tl.ganti_kayu(a, b)


def _g(*maps):
    out = {}
    for m in maps:
        out.update(m)
    return out


# {tema: {gaya: {"biomes": [...] or None (base), "ganti": {from: to}}}}; the style tag is
# #kultivator:kota_tema/gaya/<tema>_<gaya> (written by gen_tempat_tema_data.py from this table).
GAYA_TEMA = {
    "pelabuhan": {
        "selatan": {"biomes": None, "ganti": {}},
        "utara": {"biomes": ["cold_ocean", "snowy_beach", "taiga", "snowy_taiga", "snowy_plains", "old_growth_spruce_taiga"],
                  "ganti": _g(_kayu("jungle", "spruce"), _kayu("acacia", "spruce"), {
                      "minecraft:bricks": "minecraft:stone_bricks", "minecraft:brick_stairs": "minecraft:dark_oak_stairs",
                      "minecraft:brick_slab": "minecraft:dark_oak_slab", "minecraft:brick_wall": "minecraft:stone_brick_wall",
                      "minecraft:red_terracotta": "minecraft:light_gray_terracotta",
                      "minecraft:polished_granite": "minecraft:polished_andesite", "minecraft:granite": "minecraft:andesite",
                      "minecraft:polished_granite_slab": "minecraft:polished_andesite_slab",
                      "minecraft:polished_granite_stairs": "minecraft:polished_andesite_stairs",
                      "minecraft:red_nether_bricks": "minecraft:deepslate_bricks", "minecraft:jungle_leaves": "minecraft:spruce_leaves"})},
    },
    "oasis": {
        "pasir": {"biomes": None, "ganti": {}},
        "merah": {"biomes": ["badlands", "eroded_badlands", "wooded_badlands"],
                  "ganti": _g(_kayu("jungle", "acacia"), {
                      "minecraft:smooth_sandstone": "minecraft:smooth_red_sandstone", "minecraft:cut_sandstone": "minecraft:cut_red_sandstone",
                      "minecraft:sandstone": "minecraft:red_sandstone", "minecraft:chiseled_sandstone": "minecraft:chiseled_red_sandstone",
                      "minecraft:sandstone_stairs": "minecraft:red_sandstone_stairs", "minecraft:sandstone_slab": "minecraft:red_sandstone_slab",
                      "minecraft:sandstone_wall": "minecraft:red_sandstone_wall",
                      "minecraft:smooth_sandstone_slab": "minecraft:smooth_red_sandstone_slab",
                      "minecraft:smooth_sandstone_stairs": "minecraft:smooth_red_sandstone_stairs",
                      "minecraft:cut_sandstone_slab": "minecraft:cut_red_sandstone_slab", "minecraft:sand": "minecraft:red_sand",
                      "minecraft:mud_bricks": "minecraft:terracotta", "minecraft:packed_mud": "minecraft:brown_terracotta",
                      "minecraft:jungle_leaves": "minecraft:acacia_leaves"})},
    },
    "air": {
        "jiangnan": {"biomes": None, "ganti": {}},
        "sakura": {"biomes": ["cherry_grove"],
                   "ganti": _g(_kayu("dark_oak", "cherry"), {
                       "minecraft:stripped_dark_oak_log": "minecraft:stripped_cherry_log", "minecraft:oak_leaves": "minecraft:cherry_leaves",
                       "minecraft:light_blue_wool": "minecraft:pink_wool", "minecraft:light_gray_carpet": "minecraft:pink_carpet"})},
    },
    "gunung": {
        "batu": {"biomes": None, "ganti": {}},
        "salju": {"biomes": ["grove", "snowy_slopes", "frozen_peaks", "jagged_peaks"],
                  "ganti": _g(_kayu("dark_oak", "spruce"), {
                      "minecraft:white_terracotta": "minecraft:snow_block", "minecraft:moss_block": "minecraft:snow_block",
                      "minecraft:grass_block": "minecraft:snow_block", "minecraft:mossy_stone_bricks": "minecraft:stone_bricks"})},
        "hutan": {"biomes": ["meadow", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga"],
                  "ganti": {"minecraft:stone_bricks": "minecraft:mossy_stone_bricks", "minecraft:andesite": "minecraft:cobblestone",
                            "minecraft:dark_oak_stairs": "minecraft:spruce_stairs", "minecraft:dark_oak_slab": "minecraft:spruce_slab",
                            "minecraft:dark_oak_planks": "minecraft:spruce_planks"}},
    },
    "iblis": {
        "hitam": {"biomes": None, "ganti": {}},
        "merah": {"biomes": ["badlands", "eroded_badlands", "wooded_badlands"],
                  "ganti": {"minecraft:polished_blackstone_bricks": "minecraft:cut_red_sandstone",
                            "minecraft:polished_blackstone": "minecraft:smooth_red_sandstone",
                            "minecraft:black_terracotta": "minecraft:brown_terracotta", "minecraft:blackstone": "minecraft:red_sandstone",
                            "minecraft:blackstone_stairs": "minecraft:red_sandstone_stairs", "minecraft:blackstone_slab": "minecraft:red_sandstone_slab",
                            "minecraft:polished_basalt": "minecraft:bone_block", "minecraft:soul_soil": "minecraft:red_sand"}},
        "rawa": {"biomes": ["swamp", "mangrove_swamp"],
                 "ganti": _g(_kayu("dark_oak", "mangrove"), {
                     "minecraft:polished_blackstone_bricks": "minecraft:mud_bricks", "minecraft:polished_blackstone": "minecraft:packed_mud",
                     "minecraft:polished_basalt": "minecraft:muddy_mangrove_roots", "minecraft:soul_soil": "minecraft:mud",
                     "minecraft:dark_oak_leaves": "minecraft:mangrove_leaves"})},
    },
}
for _t, _styles in GAYA_TEMA.items():
    tl.GAYA["kota_tema_" + _t] = {g: {"tag": None if s["biomes"] is None else "#kultivator:kota_tema/gaya/%s_%s" % (_t, g),
                                       "ganti": s["ganti"]} for g, s in _styles.items()}

# Weathering per theme (applied by tulis_processor_tema; "utuh" pools skip it).
LAPUK_TEMA = {
    "pelabuhan": (("minecraft:bricks", "minecraft:mossy_stone_bricks", 0.02), ("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.05),
                  ("minecraft:white_terracotta", "minecraft:light_gray_terracotta", 0.03)),
    "oasis": (("minecraft:mud_bricks", "minecraft:packed_mud", 0.05), ("minecraft:smooth_sandstone", "minecraft:sandstone", 0.04)),
    "air": (("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.08), ("minecraft:white_terracotta", "minecraft:light_gray_terracotta", 0.03),
            ("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.03)),
    "gunung": (("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.06), ("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.03),
               ("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.08)),
    # iblis_lapuk (38.T.2.4): cracks 8 %, dried blood (red concrete powder) 2 % on the outer floors, never fire
    "iblis": (("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.08),
              ("minecraft:nether_bricks", "minecraft:cracked_nether_bricks", 0.08),
              ("minecraft:polished_basalt", "minecraft:red_concrete_powder", 0.02)),
}


def gaya_struktur(tema):
    """The "gaya" list of a district structure: [(tag, akhiran)] of every non-base style."""
    out = []
    for g, s in GAYA_TEMA[tema].items():
        if s["biomes"] is not None:
            out.append(("#kultivator:kota_tema/gaya/%s_%s" % (tema, g), g))
    return out


# ================================================================================================ canvas and lots
def kanvas(tema, nama, tinggi=40, lebar=SEL, dalam=SEL, palet=None):
    """A district canvas (kota_besar_kit.Kanvas) with the theme palette; seed from the name (same name, same bytes)."""
    return kit.Kanvas(lebar, tinggi, dalam, tl._seed("kota_tema/%s/%s" % (tema, nama)), palette=palet or PALET_TEMA[tema])


def potong_distrik(k, tema, did, nama=None, sisi=None, tinggi_min=24, ox=0, oz=0):
    """Cuts a district canvas into its lots (folder kota_tema/<tema>/<did>). nama[(i, j)] = lot file name (default
    "l<i><j>"); sisi[(i, j)] = 4-letter side code string "NESW" (e.g. "JKTB"). Returns {(i, j): Lot}."""
    folder = "kota_tema/%s/%s" % (tema, did)
    sx, _, sz = k.size
    nx, nz = (sx - ox) // LOT, (sz - oz) // LOT
    names = nama or {(i, j): "l%d%d" % (i, j) for i in range(nx) for j in range(nz)}
    lots = kit.potong(k, names, tinggi_min=tinggi_min, folder=folder, ox=ox, oz=oz, nx=nx, nz=nz)
    for key, lot in lots.items():
        lot.pal = dict(k.pal)
        code = (sisi or {}).get(key)
        if code:
            lot.sisi(code[0], code[1], code[2], code[3])
    return lots


def simpan_lot(lots):
    """Saves every lot; returns {(i, j): template id}."""
    out = {}
    for key, lot in lots.items():
        lot.simpan()
        out[key] = lot.id
    return out


def lot_inti(lx, lz, pool, putar=0, dy=0):
    """A core lot entry of a district structure: lot (lx, lz) in {0, 1} -> NW corner (-48 + 48 lx, -48 + 48 lz)."""
    e = {"dx": -LOT + LOT * lx, "dz": -LOT + LOT * lz, "pool": pool if ":" in pool else "kultivator:" + pool, "putar": putar}
    if dy:
        e["dy"] = dy
    return e


def lot_luar(dx, dz, pool, putar=0, dy=0):
    """An extra lot (wall, tower, gate, pier) at an explicit NW corner offset."""
    e = {"dx": dx, "dz": dz, "pool": pool if ":" in pool else "kultivator:" + pool, "putar": putar}
    if dy:
        e["dy"] = dy
    return e


# ================================================================================================ JSON writers
def proc_id(tema, gaya, utuh=False):
    """The processor list id of one style: kultivator:kota_tema/<tema>/gaya_<g> (weathered) or .../utuh_<g>."""
    return "kultivator:kota_tema/%s/%s_%s" % (tema, "utuh" if utuh else "gaya", gaya)


def tulis_pool_tema(tema, nama, template_ids, bobot=None, utuh=False, projection="rigid"):
    """Writes template_pool kota_tema/<tema>/<nama> (base style) and kota_tema/<tema>/<nama>_<gaya> for every other style,
    each element with its style processor (TataLetakStructure picks <pool>_<akhiran> by biome). Returns the pool ids."""
    bobot = bobot or [1] * len(template_ids)
    base = [g for g, s in GAYA_TEMA[tema].items() if s["biomes"] is None][0]
    out = []
    for g, s in GAYA_TEMA[tema].items():
        name = "kota_tema/%s/%s" % (tema, nama) + ("" if s["biomes"] is None else "_" + g)
        proc = proc_id(tema, g if s["biomes"] is not None else base, utuh)
        tl.tulis_pool(name, [(tl.elemen(t, proc, projection), w) for t, w in zip(template_ids, bobot)])
        out.append("kultivator:" + name)
    return out


def tulis_processor_tema(tema, states=None):
    """Writes every style processor of the theme (gaya_<g> with weathering, utuh_<g> without) for the block states of
    the templates saved in this run (call once, after the last template)."""
    st = states if states is not None else tl.semua_state()
    lapuk = LAPUK_TEMA.get(tema, ())
    paths = []
    for g, s in GAYA_TEMA[tema].items():
        for utuh in (False, True):
            ganti = s["ganti"]
            rules = []
            for bid, props in sorted(set(st)):
                to = ganti.get(bid)
                if to is None:
                    continue
                inp = {"id": bid}
                out = {"id": to}
                if props:
                    inp["properties"] = {k: v for k, v in props}
                    out["properties"] = {k: v for k, v in props}
                rules.append({"input_predicate": {"predicate_type": "minecraft:blockstate_match", "block_state": inp},
                              "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": out})
            if not utuh:
                for src, dst, p in lapuk:
                    rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match",
                                                      "block": ganti.get(src, src), "probability": p},
                                  "location_predicate": {"predicate_type": "minecraft:always_true"},
                                  "output_state": {"id": ganti.get(dst, dst)}})
            procs = []
            if rules:
                procs.append({"processor_type": "minecraft:rule", "rules": rules})
            procs.append({"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"})
            name = proc_id(tema, g, utuh).split(":", 1)[1]
            paths.append(tl.tulis_processor(name, {"processors": procs}))
    return paths


def struktur_distrik(tema, did, lots, adaptasi=None):
    """The kultivator:tata_letak structure of one district (rencana kota_tema_<tema>, biomes tag, theme styles)."""
    d = distrik(tema, did)
    if adaptasi is None:
        adaptasi = "none" if d["laut"] else "beard_box"
    return tl.struktur_tata_letak("kota_tema_" + tema, did, lots, "#kultivator:has_structure/kota_tema/" + tema,
                                  gaya=gaya_struktur(tema), adaptasi=adaptasi)


def tulis_distrik(tema, did, lots, adaptasi=None):
    """Writes worldgen/structure/kota_tema/<tema>/<did>.json (sets and tags are gen_tempat_tema_data.py's)."""
    return tl.tulis_wg("structure", "kota_tema/%s/%s" % (tema, did), struktur_distrik(tema, did, lots, adaptasi))


# ================================================================================================ build helpers
def kanal(p, x0, z0, x1, z1, y=LY, dalam=KANAL_DALAM, quay="quay", bed="canal_bed", kerb="kerb"):
    """A stone-banked canal channel: water from y - dalam + 1 .. y - 1 (surface one below the street, 38.T.2.2), bed
    below, quay blocks on the street level at the long edges left to the caller. Inclusive box x0..x1, z0..z1."""
    xa, xb = min(x0, x1), max(x0, x1)
    za, zb = min(z0, z1), max(z0, z1)
    for x in range(xa, xb + 1):
        for z in range(za, zb + 1):
            p.put(x, y - dalam, z, bed if bed in p.pal else "minecraft:gravel")
            for yy in range(y - dalam + 1, y):
                p.put(x, yy, z, "minecraft:water")
            p.put(x, y, z, "minecraft:air")
            for yy in range(y + 1, y + 4):
                if p.get(x, yy, z) is None:
                    p.put(x, yy, z, "minecraft:air")
    # banks: stone under the quay edges so the channel never leaks into the foundation
    for x in range(xa - 1, xb + 2):
        for z in (za - 1, zb + 1):
            for yy in range(y - dalam, y):
                if p.inside(x, yy, z) and p.get(x, yy, z) is None:
                    p.put(x, yy, z, kerb)
    for z in range(za, zb + 1):
        for x in (xa - 1, xb + 1):
            for yy in range(y - dalam, y):
                if p.inside(x, yy, z) and p.get(x, yy, z) is None:
                    p.put(x, yy, z, kerb)


def kanal_tepi(p, sisi, y=LY, mulai=0, akhir=None):
    """The K edge strip of side `sisi` (n/e/s/w): KANAL_AIR water blocks at the edge, a quay path of KANAL_JALAN behind
    it (chiseled quay stones on the rim, every 8th a mooring post). Two K lots meet in one 6-wide canal."""
    sx, _, sz = p.size
    end = (sx if sisi in "ns" else sz) - 1 if akhir is None else akhir
    w = KANAL_AIR
    if sisi == "n":
        kanal(p, mulai, 0, end, w - 1, y)
        rim = [(x, w) for x in range(mulai, end + 1)]
        path = [(x, w + 1) for x in range(mulai, end + 1)]
    elif sisi == "s":
        kanal(p, mulai, sz - w, end, sz - 1, y)
        rim = [(x, sz - w - 1) for x in range(mulai, end + 1)]
        path = [(x, sz - w - 2) for x in range(mulai, end + 1)]
    elif sisi == "w":
        kanal(p, 0, mulai, w - 1, end, y)
        rim = [(w, z) for z in range(mulai, end + 1)]
        path = [(w + 1, z) for z in range(mulai, end + 1)]
    else:
        kanal(p, sx - w, mulai, sx - 1, end, y)
        rim = [(sx - w - 1, z) for z in range(mulai, end + 1)]
        path = [(sx - w - 2, z) for z in range(mulai, end + 1)]
    for i, (x, z) in enumerate(rim):
        p.put(x, y, z, "quay" if "quay" in p.pal else "kerb")
        if i % 8 == 4:
            p.put(x, y + 1, z, "wood_fence" if "wood_fence" in p.pal else "minecraft:oak_fence")
    for x, z in path:
        p.put(x, y, z, "paving")
    return rim


def tiang_pancang(p, x, z, y_atas, dalam=24, role="pile", alt="pile_alt", alt_chance=0.25):
    """A pier pile column from y_atas - 1 down `dalam` blocks (clipped at y = 0); the cells around stay unset, so the
    sea keeps its water (absent template cells are never placed)."""
    if role not in p.pal and ":" not in role:
        role = "minecraft:stripped_spruce_log"
    for y in range(max(0, y_atas - dalam), y_atas):
        r = alt if alt in p.pal and p.rng.random() < alt_chance else role
        p.put(x, y, z, r, {"axis": "y"})


def tembok(p, tema, x0, z0, x1, z1, y=LY, tinggi=8, tebal=3):
    """A town wall segment along x (z0 == z1 outer face) or z: solid core `tebal` deep, a walkway with a parapet and the
    theme's crown: pelabuhan/air merlons with lanterns, oasis rounded mud merlons, gunung slab caps, iblis iron spikes
    with end-rod tips (38.T.7.2; no pointed dripstone)."""
    along_x = abs(x1 - x0) >= abs(z1 - z0)
    xa, xb = min(x0, x1), max(x0, x1)
    za, zb = min(z0, z1), max(z0, z1)
    if along_x:
        zb = za + tebal - 1
    else:
        xb = xa + tebal - 1
    wall = "kerb" if tema in ("pelabuhan", "gunung", "air") else "wall"
    p.fill(xa, y, za, xb, y + tinggi - 1, zb, wall, alt="kerb_alt" if "kerb_alt" in p.pal else None, alt_chance=0.08)
    top = y + tinggi
    p.fill(xa, top - 1, za, xb, top - 1, zb, "paving")
    n = (xb - xa + 1) if along_x else (zb - za + 1)
    for i in range(n):
        x = xa + i if along_x else xa
        z = za if along_x else za + i
        merlon = i % 3 != 1
        if tema == "iblis":
            p.put(x, top, z, "kerb_wall")
            if i % 2 == 0:
                p.put(x, top + 1, z, "spike")
                p.put(x, top + 2, z, "spike_tip", {"facing": "up"})
        elif tema == "oasis":
            if merlon:
                p.put(x, top, z, "wall")
                p.put(x, top + 1, z, "kerb_slab", {"type": "bottom"})
        elif tema == "gunung":
            p.put(x, top, z, "kerb_slab" if merlon else "kerb_wall", {"type": "bottom"} if merlon else None)
        else:
            if merlon:
                p.put(x, top, z, "kerb")
                if i % 6 == 0:
                    p.put(x, top + 1, z, "glow")
    return top


def menara(p, tema, x0, z0, ukuran=9, y=LY, tinggi=14):
    """A corner/gate tower: hollow stone shaft with a ladder, floor every 5, a theme roof (hip roof for pelabuhan/air/
    gunung, cut-sandstone dome for oasis, a spired black roof for iblis)."""
    x1, z1 = x0 + ukuran - 1, z0 + ukuran - 1
    wall = "kerb" if tema != "iblis" and tema != "oasis" else "wall"
    p.box(x0, y, z0, x1, y + tinggi, z1, wall, hollow=True)
    for fy in range(y + 5, y + tinggi, 5):
        p.fill(x0 + 1, fy, z0 + 1, x1 - 1, fy, z1 - 1, "wood")
        p.put(x0 + 1, fy, z0 + 1, "minecraft:air")
    for yy in range(y + 1, y + tinggi + 1):
        p.put(x0 + 1, yy, z0 + 1, "minecraft:ladder", {"facing": "south"})
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    top = y + tinggi + 1
    if tema == "oasis":
        for r in range(ukuran // 2, -1, -1):
            yy = top + (ukuran // 2 - r)
            p.disc(cx, yy, cz, r, "dome")
        p.put(cx, top + ukuran // 2 + 1, cz, "gold")
    elif tema == "iblis":
        for i in range(0, ukuran // 2 + 1):
            p.fill(x0 + i, top + i * 2, z0 + i, x1 - i, top + i * 2 + 1, z1 - i, "roof_full")
        p.put(cx, top + ukuran + 1, cz, "spike")
        p.put(cx, top + ukuran + 2, cz, "spike_tip", {"facing": "up"})
    else:
        kit.hip(p, x0 - 1, top, z0 - 1, x1 + 1, z1 + 1, overhang=1)
    p.lantern(cx, y + tinggi - 1, cz)
    return top


def jalan_kota(p, x0, z0, x1, z1, y=LY, lampu_setiap=12):
    """A paved town street with a gutter and lanterns on posts every `lampu_setiap` blocks along its long side."""
    p.fill(min(x0, x1), y, min(z0, z1), max(x0, x1), y, max(z0, z1), "paving", alt="paving_alt", alt_chance=0.15)
    along_x = abs(x1 - x0) >= abs(z1 - z0)
    n = abs(x1 - x0) if along_x else abs(z1 - z0)
    for i in range(0, n + 1, lampu_setiap):
        x = min(x0, x1) + i if along_x else min(x0, x1)
        z = min(z0, z1) if along_x else min(z0, z1) + i
        kit.post(p, x, y + 1, z, height=2)


def cek_semua(strict=True):
    """tempat_lib.cek_semua (structgen validate + lot rules)."""
    tl.cek_semua(strict=strict)


def json_dump(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False)


if __name__ == "__main__":
    print(__doc__)
    for t in TEMA_IDS:
        print(t, [d["id"] for d in DISTRIK.get(t, [])])
    print("satelit", SALT_SATELIT)
