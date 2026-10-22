"""Module K world generation (DESIGN 34.2, 34.5.1, 34.5.4): Kota Kultivator (overworld) and Kota Abadi (Alam Abadi).

Writes the NBT templates (data/kultivator/structure/kota/**) AND their JSON (structures, template pools, processor
lists, biome and structure tags, loot tables, and the structure sets in the built-in pack resourcepacks/dunia_kota), so
the two can never disagree. Run:  python tools/gen_dunia_kota.py [preview_dir]
Only world generation in NEW chunks ever places these templates (DESIGN 34.0 rule W1). Deterministic: every piece has its
own seed, so the same bytes come out on every run.

Four biome looks of one town plan (one structure each, all four in the one structure set kota/kota_kultivator, so the
placement grid and the village exclusion stay as designed; ChunkGenerator tries the weighted list until the biome fits):
  sedang  plains, meadow, forest, birch forest, flower forest: dark oak, white plaster, grey tiles, an old pine
          (templates kota/<piece>, structure kota/kota_kultivator - the names of wave 1, so half-built towns still load)
  salju   snowy plains, taiga: spruce, calcite plaster, snow drifts in the yards, spruce trees
          (kota/salju/<piece>, structure kota/kota_kultivator_salju)
  sabana  savanna: acacia, ochre terracotta, mud-brick tile roofs, sandstone streets, acacias
          (kota/sabana/<piece>, structure kota/kota_kultivator_sabana)
  sakura  cherry grove: cherry wood, pale plaster, pink banners, petals and cherry trees
          (kota/sakura/<piece>, structure kota/kota_kultivator_sakura)

Layout of a town (jigsaw, every piece <= 48 x 48; buildings face south and the jigsaw turns them to face their street):
  gerbang | gerbang_2 (start: paifang gate with watch towers, or a walled gatehouse; the town stele)
    -> jalan_utama_a (main street; west: Paviliun Pil, east: Paviliun Pusaka)
    -> jalan_utama_b (main street; west: the inn, east: the guard sect's office)
    -> alun (the square: well, old tree, incense burner, huabiao pillars, notice board; north: Balai Lelang, east: Kedai
       Teh, west: the coffin shop over the Pasar Gelap; four side streets)
       -> jalan_lurus_1..5 / jalan_silang(_2) / jembatan(_2) (houses, courtyard houses, shophouses, stalls, gardens,
          shrines, a training yard on both sides) -> tembok_ujung_1..3 (wall sections with corner towers close every street)
Kota Abadi: abadi_gerbang (start, cloud gate) -> abadi_jembatan (houses on both sides) -> abadi_alun (the plaza; Istana
Giok, Pasar Abadi, Paviliun Dao, Kebun Persik, Menara Lonceng, Balai Lelang Abadi) - every piece floats on its own
tapering underside.

Craft rules of this generator: fences, walls, bars and panes get their connections written into the template (the
jigsaw places with knownShape, so nothing would join them in the world); every roof has curled eaves, a ridge with
chiwen ends and a pearl or a finial; eaves rest on dougong brackets; lanterns hang from chains or fences; rooms are
furnished; yards are gardens. Anchors ("kota/<what>", DESIGN 34.4.1) carry "yaw0" (their authored yaw) so the module can
turn point lists with the piece, and "pts" (relative x, y, z triples) for light points.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from structgen import (PALETTES, Piece, out_path, json_path, write_json, single, pool, validate_all,  # noqa: E402
                       DATA, REPO)

K = "kultivator:"

# ======================================================================================================== palettes
_BASE = dict(PALETTES["kota"])
_BASE.update({
    "plaster": "minecraft:white_terracotta", "plaster_alt": "minecraft:calcite", "beam": "minecraft:stripped_dark_oak_log",
    "red": "minecraft:red_nether_bricks", "red_wall": "minecraft:red_nether_brick_wall", "red_stairs": "minecraft:red_nether_brick_stairs",
    "red_slab": "minecraft:red_nether_brick_slab",
    "paving": "minecraft:polished_andesite", "paving_alt": "minecraft:andesite", "paving_crack": "minecraft:cracked_stone_bricks",
    "kerb": "minecraft:stone_bricks", "kerb_alt": "minecraft:mossy_stone_bricks", "kerb_wall": "minecraft:stone_brick_wall",
    "kerb_stairs": "minecraft:stone_brick_stairs", "kerb_slab": "minecraft:stone_brick_slab", "kerb_chisel": "minecraft:chiseled_stone_bricks",
    "gutter": "minecraft:cobblestone", "gutter_alt": "minecraft:mossy_cobblestone",
    "drain": "minecraft:stone_brick_slab", "rail": "minecraft:dark_oak_fence",
    "wood": "minecraft:dark_oak_planks", "wood_alt": "minecraft:spruce_planks", "wood_stairs": "minecraft:dark_oak_stairs",
    "wood_slab": "minecraft:dark_oak_slab", "wood_trapdoor": "minecraft:spruce_trapdoor", "wood_fence": "minecraft:dark_oak_fence",
    "wood_gate": "minecraft:dark_oak_fence_gate", "log": "minecraft:dark_oak_log", "shelf": "minecraft:dark_oak_shelf",
    "light_wood": "minecraft:spruce_planks", "light_stairs": "minecraft:spruce_stairs", "light_slab": "minecraft:spruce_slab",
    "lamp": "minecraft:ochre_froglight", "banner": "red", "banner_alt": "yellow", "cloth": "minecraft:red_wool",
    "cloth_alt": "minecraft:white_wool", "ground": "minecraft:grass_block", "ground_alt": "minecraft:podzol",
    "statue": "minecraft:polished_andesite", "statue_top": "minecraft:chiseled_stone_bricks",
    "tree": "pine", "flower_tall": "minecraft:peony", "flower_tall_alt": "minecraft:rose_bush", "flower": "minecraft:allium",
    "flower_alt": "minecraft:lily_of_the_valley", "bush": "minecraft:azalea", "bush_alt": "minecraft:flowering_azalea",
    "leaves": "minecraft:spruce_leaves", "trunk": "minecraft:spruce_log", "bed": "minecraft:red_bed", "rug": "minecraft:red_carpet",
    "rug_alt": "minecraft:brown_carpet", "potted": "minecraft:potted_bamboo",
})

VARIANTS = {
    "sedang": dict(_BASE),
    "salju": dict(_BASE, **{
        "wall": "minecraft:spruce_planks", "beam": "minecraft:stripped_spruce_log", "plaster": "minecraft:calcite",
        "plaster_alt": "minecraft:white_terracotta", "trim": "minecraft:spruce_log", "wood": "minecraft:spruce_planks",
        "wood_alt": "minecraft:dark_oak_planks", "wood_stairs": "minecraft:spruce_stairs", "wood_slab": "minecraft:spruce_slab",
        "wood_fence": "minecraft:spruce_fence", "wood_gate": "minecraft:spruce_fence_gate", "log": "minecraft:spruce_log",
        "shelf": "minecraft:spruce_shelf", "rail": "minecraft:spruce_fence", "fence": "minecraft:spruce_fence",
        "light_wood": "minecraft:birch_planks", "light_stairs": "minecraft:birch_stairs", "light_slab": "minecraft:birch_slab",
        "ground": "minecraft:snow_block", "ground_alt": "minecraft:podzol", "tree": "spruce", "flower_tall": "minecraft:large_fern",
        "flower_tall_alt": "minecraft:large_fern", "flower": "minecraft:sweet_berry_bush", "flower_alt": "minecraft:fern",
        "bush": "minecraft:spruce_leaves", "bush_alt": "minecraft:spruce_leaves", "banner": "blue", "banner_alt": "white",
        "cloth": "minecraft:blue_wool", "rug": "minecraft:blue_carpet", "rug_alt": "minecraft:gray_carpet", "bed": "minecraft:blue_bed",
        "potted": "minecraft:potted_spruce_sapling", "glow": "minecraft:lantern",
    }),
    "sabana": dict(_BASE, **{
        "wall": "minecraft:acacia_planks", "beam": "minecraft:stripped_acacia_log", "plaster": "minecraft:orange_terracotta",
        "plaster_alt": "minecraft:terracotta", "trim": "minecraft:acacia_log", "pillar": "minecraft:red_terracotta",
        "red": "minecraft:red_terracotta", "red_wall": "minecraft:red_sandstone_wall", "red_stairs": "minecraft:red_sandstone_stairs",
        "red_slab": "minecraft:red_sandstone_slab",
        "roof": "minecraft:mud_brick_stairs", "roof_slab": "minecraft:mud_brick_slab", "roof_full": "minecraft:mud_bricks",
        "paving": "minecraft:smooth_sandstone", "paving_alt": "minecraft:cut_sandstone", "paving_crack": "minecraft:sandstone",
        "kerb": "minecraft:sandstone", "kerb_alt": "minecraft:chiseled_sandstone", "kerb_wall": "minecraft:sandstone_wall",
        "kerb_stairs": "minecraft:sandstone_stairs", "kerb_slab": "minecraft:sandstone_slab", "kerb_chisel": "minecraft:chiseled_sandstone",
        "gutter": "minecraft:packed_mud", "gutter_alt": "minecraft:mud_bricks", "drain": "minecraft:sandstone_slab",
        "floor": "minecraft:smooth_sandstone", "floor_alt": "minecraft:cut_sandstone", "path": "minecraft:cut_sandstone",
        "path_alt": "minecraft:sandstone", "wood": "minecraft:acacia_planks", "wood_alt": "minecraft:jungle_planks",
        "wood_stairs": "minecraft:acacia_stairs", "wood_slab": "minecraft:acacia_slab", "wood_trapdoor": "minecraft:acacia_trapdoor",
        "wood_fence": "minecraft:acacia_fence", "wood_gate": "minecraft:acacia_fence_gate", "log": "minecraft:acacia_log",
        "shelf": "minecraft:acacia_shelf", "rail": "minecraft:acacia_fence", "fence": "minecraft:acacia_fence",
        "window": "minecraft:acacia_trapdoor", "light_wood": "minecraft:jungle_planks", "light_stairs": "minecraft:jungle_stairs",
        "light_slab": "minecraft:jungle_slab", "tree": "acacia", "leaves": "minecraft:acacia_leaves", "trunk": "minecraft:acacia_log",
        "flower_tall": "minecraft:tall_grass", "flower_tall_alt": "minecraft:tall_grass", "flower": "minecraft:dead_bush",
        "flower_alt": "minecraft:short_grass", "bush": "minecraft:acacia_leaves", "bush_alt": "minecraft:acacia_leaves",
        "banner": "orange", "banner_alt": "yellow", "cloth": "minecraft:orange_wool", "cloth_alt": "minecraft:yellow_wool",
        "rug": "minecraft:orange_carpet", "rug_alt": "minecraft:yellow_carpet", "bed": "minecraft:orange_bed",
        "statue": "minecraft:cut_sandstone", "statue_top": "minecraft:chiseled_sandstone", "potted": "minecraft:potted_dead_bush",
    }),
    "sakura": dict(_BASE, **{
        "wall": "minecraft:cherry_planks", "beam": "minecraft:stripped_cherry_log", "plaster": "minecraft:white_concrete",
        "plaster_alt": "minecraft:calcite", "trim": "minecraft:cherry_log", "wood": "minecraft:cherry_planks",
        "wood_alt": "minecraft:dark_oak_planks", "wood_stairs": "minecraft:cherry_stairs", "wood_slab": "minecraft:cherry_slab",
        "wood_trapdoor": "minecraft:cherry_trapdoor", "wood_fence": "minecraft:cherry_fence", "wood_gate": "minecraft:cherry_fence_gate",
        "log": "minecraft:cherry_log", "shelf": "minecraft:cherry_shelf", "rail": "minecraft:cherry_fence", "fence": "minecraft:cherry_fence",
        "window": "minecraft:cherry_trapdoor", "light_wood": "minecraft:bamboo_planks", "light_stairs": "minecraft:bamboo_stairs",
        "light_slab": "minecraft:bamboo_slab",
        "roof": "minecraft:polished_blackstone_brick_stairs", "roof_slab": "minecraft:polished_blackstone_brick_slab",
        "roof_full": "minecraft:polished_blackstone_bricks", "tree": "cherry", "leaves": "minecraft:cherry_leaves",
        "trunk": "minecraft:cherry_log", "flower_tall": "minecraft:peony", "flower_tall_alt": "minecraft:lilac",
        "flower": "minecraft:pink_tulip", "flower_alt": "minecraft:white_tulip", "bush": "minecraft:flowering_azalea",
        "bush_alt": "minecraft:cherry_leaves", "banner": "pink", "banner_alt": "white", "cloth": "minecraft:pink_wool",
        "cloth_alt": "minecraft:white_wool", "rug": "minecraft:pink_carpet", "rug_alt": "minecraft:magenta_carpet",
        "bed": "minecraft:pink_bed", "potted": "minecraft:potted_cherry_sapling",
    }),
}

ABADI = dict(PALETTES["abadi"])
ABADI.update({
    "under": "minecraft:calcite", "under_alt": "minecraft:smooth_quartz", "under_tip": "minecraft:white_concrete",
    "rail": "minecraft:cherry_fence", "beam": "minecraft:stripped_cherry_log", "plaster": "minecraft:white_concrete",
    "roof": "minecraft:cherry_stairs", "roof_slab": "minecraft:cherry_slab", "roof_full": "minecraft:cherry_planks",
    "jade_tile": "minecraft:prismarine_bricks", "lamp": "minecraft:pearlescent_froglight", "wood": "minecraft:cherry_planks",
    "wood_stairs": "minecraft:cherry_stairs", "wood_slab": "minecraft:cherry_slab", "wood_fence": "minecraft:cherry_fence",
    "wood_trapdoor": "minecraft:cherry_trapdoor", "red": "minecraft:quartz_pillar", "kerb_wall": "minecraft:diorite_wall",
    "banner": "white", "banner_alt": "yellow", "statue": "minecraft:smooth_quartz", "statue_top": "minecraft:chiseled_quartz_block",
    "kerb": "minecraft:quartz_bricks", "kerb_slab": "minecraft:quartz_slab", "kerb_stairs": "minecraft:quartz_stairs",
    "shelf": "minecraft:cherry_shelf", "rug": "minecraft:white_carpet", "potted": "minecraft:potted_cherry_sapling",
})

CUR = {"v": "sedang"}
SAVED = []
LANTERN_PTS = {}
COUNTS = {}


def PAL():
    return VARIANTS[CUR["v"]]


def folder():
    return "kota" if CUR["v"] == "sedang" else "kota/" + CUR["v"]


def ref(name):
    """A template or pool id of the current variant (wave-1 names for 'sedang')."""
    if ":" in name:
        return name
    if name.startswith("kota/"):
        name = name[5:]
    return K + folder() + "/" + name


def F(v):
    return nbt.Float(v)


def piece(sx, sy, sz, seed, palette=None):
    """A Piece seeded per variant (the same plan, a different hand in every biome)."""
    salt = {"sedang": 0, "salju": 101, "sabana": 211, "sakura": 307}[CUR["v"]]
    return Piece(sx, sy, sz, seed=seed + salt if palette is None else seed, palette=palette or PAL())


def anchor(p, x, y, z, kind, yaw=0.0, **data):
    """The kultivator:dunia_anchor entity (DESIGN 34.4.1) with its yaw (turned with the piece) and data."""
    d = {"yaw0": F(yaw)}
    d.update(data)
    p.entity(x, y, z, {"id": K + "dunia_anchor", "kind": kind, "data": d, "Rotation": [F(yaw), F(0.0)]})


def note_light(p, x, y, z):
    LANTERN_PTS.setdefault(id(p), []).append((x, y, z))


def lit(p, x, y, z, chain=1, role="glow"):
    """A hanging lantern (or paper lantern with role "lamp") that also feeds the piece's glow-sprite anchor."""
    if role == "lamp":
        for i in range(chain):
            p.put(x, y - 1 - i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        p.put(x, y - 1 - chain, z, "lamp", {"axis": "y"})
    else:
        p.lantern(x, y, z, chain=chain, role=role)
    note_light(p, x, y - 1 - chain, z)


def post(p, x, y, z, height=2, post_role="fence", role="glow"):
    p.standing_lantern(x, y, z, post=post_role, role=role, height=height)
    note_light(p, x, y + height, z)


def finish_lights(p, ax, ay, az, kind="kota/lentera"):
    """One light anchor at (ax, ay, az) listing every lantern of the piece (at most 64)."""
    pts = LANTERN_PTS.pop(id(p), [])
    if not pts:
        return
    flat = []
    for x, y, z in pts[:64]:
        flat += [x - int(ax), y - int(ay), z - int(az)]
    anchor(p, ax + 0.5, ay, az + 0.5, kind, protect=0, pts=flat)


# ======================================================================================================== shape pass
_NOT_SOLID = ("air", "stairs", "slab", "fence", "_wall", "pane", "bars", "lantern", "chain", "carpet", "trapdoor", "door",
              "flower", "potted", "pot", "short_grass", "fern", "bamboo", "torch", "candle", "button", "plate", "sign", "banner",
              "_bed", "chest", "campfire", "lectern", "anvil", "cauldron", "brewing", "grindstone", "bell", "rod", "dripstone",
              "petals", "lily", "water", "leaves", "sapling", "rose", "peony", "lilac", "tulip", "allium", "lily_of", "berry",
              "dead_bush", "grass", "azalea", "snow", "jigsaw", "shelf", "ladder", "scaffolding", "barrel", "smithing", "loom",
              "cartography", "stonecutter", "composter", "hay", "melon", "pumpkin", "target", "bush", "bookshelf")


def _family(bid):
    n = bid.split(":")[-1]
    if n.endswith("_fence") and "nether" not in n:
        return "wood_fence"
    if n.endswith("_fence"):
        return "nether_fence"
    if n.endswith("_wall") and "banner" not in n and "sign" not in n and "torch" not in n:
        return "wall"
    if n.endswith("_pane") or n == "iron_bars":
        return "pane"
    return None


def _solid(bid):
    n = bid.split(":")[-1]
    if n.endswith("_wall") and "banner" not in n:
        return False
    return not any(t in n for t in _NOT_SOLID) or n in ("hay_block", "melon", "pumpkin", "barrel", "bookshelf", "target",
                                                        "smithing_table", "cartography_table", "loom", "chiseled_bookshelf")


def connect_all(p):
    """Writes fence / wall / pane connections from the template's own neighbours (the jigsaw places with knownShape)."""
    dirs = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    changes = []
    for pos, ((bid, props), data) in p.cells.items():
        fam = _family(bid)
        if fam is None:
            continue
        x, y, z = pos
        conn = {}
        for d, (dx, dz) in dirs.items():
            nb = p.get(x + dx, y, z + dz)
            ok = False
            if nb is not None:
                nf = _family(nb)
                nn = nb.split(":")[-1]
                if fam == "wood_fence":
                    ok = nf == "wood_fence" or nn.endswith("_fence_gate") or _solid(nb)
                elif fam == "nether_fence":
                    ok = nf == "nether_fence" or nn.endswith("_fence_gate") or _solid(nb)
                elif fam == "wall":
                    ok = nf == "wall" or nn.endswith("_fence_gate") or _solid(nb) or nf == "pane"
                else:
                    ok = nf == "pane" or nf == "wall" or _solid(nb)
            conn[d] = ok
        new = dict(props)
        if fam == "wall":
            above = p.get(x, y + 1, z)
            tall = above is not None and (_solid(above) or _family(above) == "wall")
            for d in dirs:
                new[d] = ("tall" if tall else "low") if conn[d] else "none"
            straight = (conn["north"] and conn["south"] and not conn["east"] and not conn["west"]) or \
                       (conn["east"] and conn["west"] and not conn["north"] and not conn["south"])
            new["up"] = "false" if straight and not (above is not None and _family(above) is None and not _solid(above)
                                                     and above != "minecraft:air") else "true"
            new.setdefault("waterlogged", "false")
        else:
            for d in dirs:
                new[d] = "true" if conn[d] else "false"
            new.setdefault("waterlogged", "false")
        changes.append((pos, bid, new, data))
    for (x, y, z), bid, new, data in changes:
        p.set(x, y, z, bid, new, data)


def save(p, name):
    connect_all(p)
    path = out_path(folder(), name)
    p.save(path)
    SAVED.append(folder() + "/" + name)
    COUNTS[folder() + "/" + name] = len(p.cells)
    return path


def conn(p, x, y, z, facing, name, target="minecraft:empty", pool_id="minecraft:empty", final="minecraft:air", prio=0):
    """A jigsaw connector; the pool is resolved inside the current variant's folder."""
    pid = pool_id if pool_id == "minecraft:empty" or ":" in pool_id else ref(pool_id)
    p.jigsaw(x, y, z, facing + "_up", K + name if ":" not in name else name, target if ":" in target else K + target,
             pid, final, placement_priority=prio)


# ======================================================================================================== detail kit
FACE_OUT = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def finial(p, x, y, z, kind="rod"):
    """A ridge pearl or finial on top of a roof (a gold pearl, a copper spire, a lantern)."""
    if not p.inside(x, y, z):
        return
    if kind == "pearl":
        p.put(x, y, z, "accent")
        if p.inside(x, y + 1, z):
            p.put(x, y + 1, z, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    elif kind == "lantern":
        p.put(x, y, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    else:
        p.put(x, y, z, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})


def gable(p, x0, y, z0, x1, z1, along="x", overhang=1, pearl=True):
    """A gable roof with curled eaves, chiwen ends and a ridge pearl in the middle; returns the ridge y."""
    top = p.roof_gable(x0, y, z0, x1, z1, along=along, overhang=overhang, eave_curl=True)
    if pearl:
        if along == "x":
            rows = (z1 + overhang) - (z0 - overhang) + 1
            mz = (z0 - overhang) + rows // 2 - (0 if rows % 2 else 1)
            finial(p, (x0 + x1) // 2, top + 1, mz, "pearl")
        else:
            rows = (x1 + overhang) - (x0 - overhang) + 1
            mx = (x0 - overhang) + rows // 2 - (0 if rows % 2 else 1)
            finial(p, mx, top + 1, (z0 + z1) // 2, "pearl")
    return top


def hip(p, x0, y, z0, x1, z1, overhang=1, tiers=1, tier_gap=2, tier_band=2, band_role="plaster", top="rod"):
    """A hip (wudian) roof, optionally a pagoda of tiers, with a finial; returns the y above it."""
    yy = p.roof_hip(x0, y, z0, x1, z1, overhang=overhang, eave_curl=True, tiers=tiers, tier_gap=tier_gap, tier_band=tier_band,
                    band_role=band_role)
    finial(p, (x0 + x1) // 2, yy + 1, (z0 + z1) // 2, top)
    return yy


def brackets(p, x0, z0, x1, z1, y, role="wood_stairs", every=2):
    """Dougong: upside-down stairs under the eaves around a rectangle, every other block, facing the wall."""
    for x in range(x0, x1 + 1, every):
        for z, face in ((z0 - 1, "south"), (z1 + 1, "north")):
            if p.inside(x, y, z) and p.get(x, y, z) in (None, "minecraft:air"):
                p.stairs(x, y, z, role, face, half="top")
    for z in range(z0, z1 + 1, every):
        for x, face in ((x0 - 1, "east"), (x1 + 1, "west")):
            if p.inside(x, y, z) and p.get(x, y, z) in (None, "minecraft:air"):
                p.stairs(x, y, z, role, face, half="top")


def beam_band(p, x0, z0, x1, z1, y, role="beam"):
    """A lintel band of beams around a rectangle (the red-and-dark frieze under the eaves)."""
    block = p.b(role)
    axial = any(block.endswith(t) for t in ("_log", "_wood", "_pillar", "_stem", "_hyphae", "basalt", "hay_block", "bone_block"))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            p.put(x, y, z, role, {"axis": "x"} if axial else None)
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            p.put(x, y, z, role, {"axis": "z"} if axial else None)


def rail_line(p, a, b, y, role="rail"):
    """A railing from a to b (x, z) at height y (connections are written by the shape pass)."""
    (ax, az), (bx, bz) = a, b
    n = max(abs(bx - ax), abs(bz - az))
    for i in range(n + 1):
        x = ax + (0 if bx == ax else (i if bx > ax else -i))
        z = az + (0 if bz == az else (i if bz > az else -i))
        if p.inside(x, y, z):
            p.put(x, y, z, role)


def wall_banner(p, x, y, z, facing, color=None):
    """A wall banner hanging on the block behind it (facing = the side it shows)."""
    c = color or PAL()["banner"]
    p.put(x, y, z, "minecraft:%s_wall_banner" % c, {"facing": facing})


def banner_pole(p, x, y, z, height=3, color=None, rotation=0):
    for i in range(height):
        p.put(x, y + i, z, "wood_fence")
    c = color or PAL()["banner"]
    p.put(x, y + height, z, "minecraft:%s_banner" % c, {"rotation": str(rotation)})


def table(p, x, y, z, top="plate"):
    p.put(x, y, z, "wood_fence")
    if top == "plate":
        p.put(x, y + 1, z, "minecraft:spruce_pressure_plate" if CUR["v"] != "sabana" else "minecraft:acacia_pressure_plate", {"powered": "false"})
    elif top:
        p.put(x, y + 1, z, top)


def chair(p, x, y, z, facing):
    """A chair facing its table (a stair whose back is away from the table)."""
    p.stairs(x, y, z, "wood_stairs", OPP[facing])


def bed(p, x, y, z, facing="north", role="bed"):
    """A bed with its head toward `facing`."""
    dx, dz = FACE_OUT[facing]
    p.put(x, y, z, role, {"facing": facing, "part": "head", "occupied": "false"})
    p.put(x - dx, y, z - dz, role, {"facing": facing, "part": "foot", "occupied": "false"})


def shelf(p, x, y, z, facing):
    p.put(x, y, z, "shelf", {"facing": facing, "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})


def rug(p, x0, z0, x1, z1, y, role="rug", border="rug_alt"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            p.put(x, y, z, border if edge else role)


def candle(p, x, y, z, n=2, color="red"):
    p.put(x, y, z, "minecraft:%s_candle" % color if color else "minecraft:candle", {"candles": str(n), "lit": "true", "waterlogged": "false"})


def tall_flower(p, x, y, z, role="flower_tall"):
    bid = p.b(role)
    if bid.endswith(("peony", "rose_bush", "lilac", "sunflower", "large_fern", "tall_grass")):
        p.put(x, y, z, role, {"half": "lower"})
        if p.inside(x, y + 1, z):
            p.put(x, y + 1, z, role, {"half": "upper"})
    else:
        p.put(x, y, z, role)


def small_flower(p, x, y, z, role="flower"):
    bid = p.b(role)
    if bid.endswith("sweet_berry_bush"):
        p.put(x, y, z, role, {"age": "2"})
    else:
        p.put(x, y, z, role)


def flowerbed(p, x0, z0, x1, z1, y, kerb=True):
    """A raised bed: kerb rim, earth, tall and small flowers (biome kinds), a bush in the middle."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            rim = kerb and (x in (x0, x1) or z in (z0, z1))
            if rim:
                p.put(x, y, z, "kerb_slab" if p.b("kerb_slab").endswith("slab") else "kerb", {"type": "top", "waterlogged": "false"})
                continue
            p.put(x, y, z, "ground", {"snowy": "false"} if p.b("ground").endswith("grass_block") else None)
            r = p.rng.random()
            if r < 0.3:
                tall_flower(p, x, y + 1, z, "flower_tall" if r < 0.15 else "flower_tall_alt")
            elif r < 0.65:
                small_flower(p, x, y + 1, z, "flower" if r < 0.5 else "flower_alt")
            elif r < 0.8 and CUR["v"] == "salju":
                p.put(x, y + 1, z, "minecraft:snow", {"layers": "1"})
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if x1 - x0 >= 2 and z1 - z0 >= 2:
        p.put(cx, y + 1, cz, "bush", {"persistent": "true", "distance": "1", "waterlogged": "false"} if p.b("bush").endswith("leaves") else None)
        if p.inside(cx, y + 2, cz) and p.get(cx, y + 2, cz) not in (None,):
            pass


def stone_lantern(p, x, y, z):
    """A toro: a stone post, a lantern in a little stone house, a slab cap."""
    p.put(x, y, z, "kerb_wall")
    p.put(x, y + 1, z, "glow", {"hanging": "false", "waterlogged": "false"} if p.b("glow").endswith("lantern") else None)
    p.slab(x, y + 2, z, "kerb_slab")
    note_light(p, x, y + 1, z)


def lion(p, x, y, z, facing):
    """A guardian lion on a plinth: a stone base, a chiselled body and a stair head looking out."""
    p.put(x, y, z, "kerb")
    p.put(x, y + 1, z, "statue_top")
    p.stairs(x, y + 2, z, "kerb_stairs", OPP[facing])


def huabiao(p, x, y, z, height=6):
    """A carved ceremonial column with a cloud board near the top and a crouching beast on its cap."""
    p.put(x, y, z, "kerb")
    for i in range(1, height):
        p.put(x, y + i, z, "statue" if i % 2 else "statue_top")
    for d, (dx, dz) in FACE_OUT.items():
        if p.inside(x + dx, y + height - 2, z + dz):
            p.stairs(x + dx, y + height - 2, z + dz, "kerb_stairs", OPP[d], half="top")
    p.slab(x, y + height, z, "kerb_slab")


def incense_burner(p, x, y, z):
    """A ding: a cauldron on a stone base over a smouldering campfire (its smoke is vanilla and free)."""
    p.put(x, y, z, "kerb_chisel")
    p.put(x, y + 1, z, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    for d, (dx, dz) in FACE_OUT.items():
        if p.inside(x + dx, y, z + dz):
            p.stairs(x + dx, y, z + dz, "kerb_stairs", OPP[d])
    p.put(x, y + 2, z, "minecraft:cauldron")


def pond(p, cx, cz, rx, rz, y, lilies=5, rim=True):
    """A pond sunk one block (water at y), rocks on the rim, lily pads, a reed or two."""
    for x in range(cx - rx - 1, cx + rx + 2):
        for z in range(cz - rz - 1, cz + rz + 2):
            d = ((x - cx) / (rx + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2
            if not p.inside(x, y, z):
                continue
            if d <= 1.0:
                if p.inside(x, y - 1, z):  # a pond in a piece's bottom layer rests on the beard's terrain
                    p.put(x, y - 1, z, "minecraft:clay")
                p.put(x, y, z, "minecraft:water", {"level": "0"})
                if p.inside(x, y + 1, z):
                    p.put(x, y + 1, z, "minecraft:air")
            elif d <= 1.6 and rim:
                p.put(x, y, z, "kerb_alt" if p.rng.random() < 0.4 else "minecraft:cobblestone" if CUR["v"] != "sabana" else "kerb")
    for _ in range(lilies):
        x = cx + p.rng.randint(-rx + 1, max(-rx + 1, rx - 1))
        z = cz + p.rng.randint(-rz + 1, max(-rz + 1, rz - 1))
        if p.get(x, y, z) == "minecraft:water" and p.inside(x, y + 1, z):
            p.put(x, y + 1, z, "minecraft:lily_pad")


def bamboo_clump(p, x, z, y, height):
    for dy in range(height):
        if p.inside(x, y + dy, z):
            p.put(x, y + dy, z, "minecraft:bamboo", {"age": "1", "leaves": "large" if dy >= height - 2 else "small" if dy >= height - 4 else "none",
                                                     "stage": "0"})


def leaves(p, x, y, z, role="leaves"):
    if p.inside(x, y, z) and p.get(x, y, z) in (None, "minecraft:air"):
        p.put(x, y, z, role, {"persistent": "true", "distance": "1", "waterlogged": "false"})


def pine(p, x, y, z, height=9):
    """An old pine: a crooked trunk with flat, layered crowns."""
    for dy in range(height):
        dx = 1 if dy > height * 0.6 else 0
        p.put(x + dx, y + dy, z, "trunk", {"axis": "y"})
    for layer, (dy, r) in enumerate([(height - 5, 3), (height - 3, 2), (height - 1, 2), (height, 1)]):
        cx = x + (1 if dy > height * 0.6 else 0)
        for ox in range(-r, r + 1):
            for oz in range(-r, r + 1):
                if abs(ox) + abs(oz) <= r + (1 if layer == 0 else 0):
                    leaves(p, cx + ox, y + dy, z + oz)
    # a low branch to one side, like the ink paintings
    for i in range(1, 3):
        if p.inside(x - i, y + height - 6, z):
            p.put(x - i, y + height - 6, z, "trunk", {"axis": "x"})
    for ox in range(-4, -1):
        for oz in range(-1, 2):
            leaves(p, x + ox, y + height - 5, z + oz)


def spruce(p, x, y, z, height=9):
    for dy in range(height):
        p.put(x, y + dy, z, "trunk", {"axis": "y"})
    for dy in range(2, height + 1):
        r = max(0, int((height - dy) / 2.6))
        if dy % 2 == 0 or dy == height:
            for ox in range(-r, r + 1):
                for oz in range(-r, r + 1):
                    if abs(ox) + abs(oz) <= r and (ox or oz or dy == height):
                        leaves(p, x + ox, y + dy, z + oz)
    leaves(p, x, y + height + 1, z)


def acacia(p, x, y, z, height=6):
    for dy in range(height - 2):
        p.put(x, y + dy, z, "trunk", {"axis": "y"})
    for i, (dx, dz) in enumerate(((1, 0), (-1, 1))):
        bx, bz = x, z
        for dy in range(height - 2, height):
            bx += dx
            bz += dz
            p.put(bx, y + dy, bz, "trunk", {"axis": "y"})
        for ox in range(-2, 3):
            for oz in range(-2, 3):
                if abs(ox) + abs(oz) <= 3:
                    leaves(p, bx + ox, y + height, bz + oz)
        for ox in range(-1, 2):
            for oz in range(-1, 2):
                leaves(p, bx + ox, y + height + 1, bz + oz)


def cherry(p, x, y, z, height=5, r=3):
    for dy in range(height):
        p.put(x, y + dy, z, "minecraft:cherry_log" if PAL() is not ABADI else "minecraft:cherry_log", {"axis": "y"})
    for ox in range(-r, r + 1):
        for oz in range(-r, r + 1):
            for dy in (height - 1, height, height + 1):
                d = math.hypot(ox, oz) + (dy - height) * 0.9
                if d <= r - 0.2:
                    leaves(p, x + ox, y + dy, z + oz, "minecraft:cherry_leaves")
    for ox, oz in ((r, 0), (-r, 1), (1, -r), (0, r), (2, 2), (-2, -1)):
        if p.inside(x + ox, y, z + oz) and p.get(x + ox, y, z + oz) is None:
            p.put(x + ox, y, z + oz, "minecraft:pink_petals", {"facing": "north", "flower_amount": str(1 + (ox * 7 + oz) % 4)})


def tree(p, x, y, z, height=None):
    kind = PAL()["tree"]
    if kind == "pine":
        pine(p, x, y, z, height or 9)
    elif kind == "spruce":
        spruce(p, x, y, z, height or 10)
    elif kind == "acacia":
        acacia(p, x, y, z, height or 6)
    else:
        cherry(p, x, y, z, height or 5, 3)


def snow_drift(p, x0, z0, x1, z1, y, chance=0.5):
    """Snow layers on open ground (snowy towns only): sturdy floors below, never on stairs or slabs."""
    if CUR["v"] != "salju":
        return
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            below = p.get(x, y - 1, z)
            if below is None or not _solid(below) or p.get(x, y, z) not in (None, "minecraft:air"):
                continue
            if p.rng.random() < chance:
                p.put(x, y, z, "minecraft:snow", {"layers": str(1 + (p.rng.random() < 0.3))})


def petals(p, x0, z0, x1, z1, y, chance=0.12):
    """Fallen petals on open floors (cherry towns only)."""
    if CUR["v"] != "sakura":
        return
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            below = p.get(x, y - 1, z)
            if below is None or not _solid(below) or p.get(x, y, z) not in (None, "minecraft:air"):
                continue
            if p.rng.random() < chance:
                p.put(x, y, z, "minecraft:pink_petals", {"facing": ["north", "east", "south", "west"][p.rng.randrange(4)],
                                                         "flower_amount": str(1 + p.rng.randrange(4))})


def awning(p, x0, x1, z0, z1, y, a="cloth", b="cloth_alt"):
    """A striped cloth awning over a stall."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, y, z, a if (x + z) % 2 == 0 else b)


def lattice_window(p, x, y, z, facing):
    p.put(x, y, z, "wood_trapdoor", {"facing": facing, "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})


def plaque(p, x, y, z):
    """A gold-rimmed name board over a door."""
    p.put(x - 1, y, z, "wood")
    p.put(x, y, z, "accent")
    p.put(x + 1, y, z, "wood")


def chest(p, x, y, z, loot, facing="south"):
    p.set(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"}, {"id": "minecraft:chest", "LootTable": loot})


def paving(p, x0, z0, x1, z1, y=0, kerb_edge=True):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = kerb_edge and (x in (x0, x1) or z in (z0, z1))
            r = p.rng.random()
            role = "kerb" if edge else ("paving" if r < 0.7 else "paving_alt" if r < 0.92 else "paving_crack")
            p.put(x, y, z, role)


def front_door(p, sx, sz, door_x, y=0):
    """The door side is south (z = sz - 1); the building's connector."""
    conn(p, door_x, y, sz - 1, "south", "kota/pintu", "minecraft:empty", "minecraft:empty", "kerb")


def door_frame(p, x, y, z, width=3, height=3, along="x", plaque_too=True):
    """Red jambs and a lintel around a doorway, gold plaque above."""
    half = width // 2
    for dy in range(height):
        if along == "x":
            p.put(x - half - 1, y + dy, z, "red")
            p.put(x + half + 1, y + dy, z, "red")
        else:
            p.put(x, y + dy, z - half - 1, "red")
            p.put(x, y + dy, z + half + 1, "red")
    if plaque_too:
        if along == "x":
            plaque(p, x, y + height, z)
        else:
            p.put(x, y + height, z, "accent")


def couplets(p, x, y, z, facing, width=3):
    """The red paper couplets on both sides of a door (wall banners)."""
    dx, dz = (1, 0) if facing in ("north", "south") else (0, 1)
    half = width // 2 + 1
    for s in (-1, 1):
        bx, bz = x + dx * half * s, z + dz * half * s
        if p.inside(bx, y, bz):
            wall_banner(p, bx, y, bz, facing)


# ======================================================================================================== the gate (start)
def tower(p, tx, tz, w=5, d=5, h=9):
    """A watch tower: stone plinth, plastered storeys with red corner pillars, a balcony with a rail, dougong, a hip roof."""
    x1, z1 = tx + w - 1, tz + d - 1
    p.fill(tx, 1, tz, x1, 1, z1, "kerb")
    p.walls(tx, 2, tz, x1, h + 1, z1, role="plaster", pillar="red", windows=2, window_role="window")
    mid = 2 + h // 2
    beam_band(p, tx, tz, x1, z1, mid)
    p.fill(tx + 1, mid, tz + 1, x1 - 1, mid, z1 - 1, "wood_alt")
    # balcony on all four sides at the middle storey
    for x in range(tx - 1, x1 + 2):
        for z in (tz - 1, z1 + 1):
            if p.inside(x, mid, z):
                p.put(x, mid, z, "wood_slab", {"type": "top", "waterlogged": "false"})
                if p.inside(x, mid + 1, z):
                    p.put(x, mid + 1, z, "rail")
    for z in range(tz, z1 + 1):
        for x in (tx - 1, x1 + 1):
            if p.inside(x, mid, z):
                p.put(x, mid, z, "wood_slab", {"type": "top", "waterlogged": "false"})
                p.put(x, mid + 1, z, "rail")
    beam_band(p, tx, tz, x1, z1, h + 1)
    brackets(p, tx, tz, x1, z1, h + 1)
    top = hip(p, tx, h + 2, tz, x1, z1, overhang=1, top="pearl")
    for cx, cz in ((tx - 1, tz - 1), (x1 + 1, tz - 1), (tx - 1, z1 + 1), (x1 + 1, z1 + 1)):
        if p.inside(cx, h + 1, cz):
            lit(p, cx, h + 1, cz, chain=1, role="lamp")
    return top


def gerbang():
    p = piece(23, 22, 13, seed=53001)
    paving(p, 0, 0, 22, 12)
    p.path([(11, 1), (11, 11)], 0, width=5, role="paving", alt="kerb", alt_chance=0.12)
    p.air(0, 1, 0, 22, 21, 12)
    for tx in (1, 17):
        tower(p, tx, 4, h=9)
        p.doorway(tx, 2, 4, tx + 4, 8, "north", width=1, height=2)
        wall_banner(p, tx + 2, 5, 9, "south")
    # the paifang: four red pillars on drum stones, three bays, a tiled roof over each bay, the name board
    pillars = (6, 9, 13, 16)
    for x in pillars:
        p.pillar(x, 1, 9, 6, role="red", base="kerb", cap="beam")
        p.stairs(x, 1, 5, "kerb_stairs", "south")
        p.stairs(x, 1, 7, "kerb_stairs", "north")
    for x in range(5, 18):
        p.put(x, 10, 6, "beam", {"axis": "x"})
        p.put(x, 8, 6, "beam", {"axis": "x"})
        if x not in pillars:
            p.put(x, 9, 6, "red")
    p.fill(10, 9, 6, 12, 9, 6, "accent")
    plaque(p, 11, 7, 6)
    brackets(p, 9, 6, 13, 6, 10)
    gable(p, 9, 11, 5, 13, 7, along="x", overhang=1)
    gable(p, 5, 10, 5, 8, 7, along="x", overhang=0, pearl=False)
    gable(p, 14, 10, 5, 17, 7, along="x", overhang=0, pearl=False)
    for x in (7, 15):
        lit(p, x, 8, 5, chain=1, role="lamp")
        lit(p, x, 8, 7, chain=1, role="lamp")
    lit(p, 11, 7, 5, chain=1)
    # guardian lions, banner poles, the stele on a tortoise
    lion(p, 8, 1, 11, "south")
    lion(p, 14, 1, 11, "south")
    banner_pole(p, 6, 1, 12, height=4, rotation=8)
    banner_pole(p, 16, 1, 12, height=4, rotation=8)
    p.put(4, 1, 11, "kerb_chisel")
    p.stairs(4, 1, 12, "kerb_stairs", "north")
    p.stairs(3, 1, 11, "kerb_stairs", "east")
    p.stairs(5, 1, 11, "kerb_stairs", "west")
    p.put(4, 2, 11, "statue")
    p.put(4, 3, 11, "statue_top")
    p.put(4, 4, 11, "statue")
    p.slab(4, 5, 11, "kerb_slab")
    post(p, 1, 1, 1)
    post(p, 21, 1, 1)
    post(p, 1, 1, 12)
    post(p, 21, 1, 12)
    stone_lantern(p, 19, 1, 11)
    snow_drift(p, 0, 9, 22, 12, 1, 0.35)
    petals(p, 0, 0, 22, 12, 1)
    anchor(p, 4.5, 1.0, 12.5, "kota/situs", 0.0, situs="kota/kota", tier=1, protect=40)
    anchor(p, 11.5, 1.0, 9.5, "kota/penjaga", 0.0)
    anchor(p, 2.5, 1.0, 10.5, "kota/penjaga", 90.0)
    p.weather(0.06)
    finish_lights(p, 11, 1, 3)
    conn(p, 11, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_utama_a", "minecraft:polished_andesite", prio=2)
    save(p, "gerbang")


def gerbang_2():
    """A walled gatehouse: crenellated walls, a stone gate passage under a two-storey gate tower with a double eave."""
    p = piece(23, 26, 13, seed=53002)
    paving(p, 0, 0, 22, 12)
    p.air(0, 1, 0, 22, 25, 12)
    # walls on both sides of the gate
    for x0, x1 in ((0, 7), (15, 22)):
        for x in range(x0, x1 + 1):
            for z in range(4, 9):
                for y in range(1, 8):
                    p.put(x, y, z, "kerb" if p.rng.random() < 0.85 else "kerb_alt")
            for z in (4, 8):
                p.put(x, 8, z, "kerb_wall" if x % 2 == 0 else "minecraft:air")
            for z in range(5, 8):
                p.put(x, 8, z, "minecraft:air")
        for x in range(x0 + 1, x1, 3):
            p.put(x, 5, 4, "minecraft:iron_bars")
    # the gate piers and passage
    for x in range(8, 15):
        for z in range(3, 10):
            for y in range(1, 8):
                p.put(x, y, z, "kerb")
    p.air(10, 1, 3, 12, 4, 9)
    for z in (3, 9):
        p.stairs(10, 4, z, "kerb_stairs", "east", half="top")
        p.stairs(12, 4, z, "kerb_stairs", "west", half="top")
        p.put(11, 5, z, "kerb_chisel")
    lit(p, 11, 5, 6, chain=0)
    # gate leaves standing open against the passage walls
    for z in (4, 8):
        for y in (1, 2, 3):
            p.put(10, y, z, "wood_trapdoor", {"facing": "east", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
            p.put(12, y, z, "wood_trapdoor", {"facing": "west", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    # the gate tower: plaster storey with red pillars, a gallery rail, a double-eave hip roof
    p.fill(7, 8, 2, 15, 8, 10, "wood")
    rail_line(p, (7, 2), (15, 2), 9)
    rail_line(p, (7, 10), (15, 10), 9)
    rail_line(p, (7, 3), (7, 9), 9)
    rail_line(p, (15, 3), (15, 9), 9)
    p.walls(8, 9, 4, 14, 13, 8, role="plaster", pillar="red", windows=2, window_role="window", door=("south", 1))
    beam_band(p, 8, 4, 14, 8, 14)
    brackets(p, 8, 4, 14, 8, 14)
    hip(p, 8, 15, 4, 14, 8, overhang=2, tiers=2, tier_gap=2, tier_band=2, band_role="plaster", top="pearl")
    p.put(11, 9, 5, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(11, 10, 5, "minecraft:note_block", {"instrument": "basedrum", "note": "0", "powered": "false"})
    p.fill(10, 12, 3, 12, 12, 3, "accent")
    plaque(p, 11, 6, 2)
    for x in (9, 13):
        wall_banner(p, x, 12, 3, "north")
        wall_banner(p, x, 12, 9, "south")
    for x in (7, 15):
        lit(p, x, 14, 2, chain=2, role="lamp")
        lit(p, x, 14, 10, chain=2, role="lamp")
    # outside: lions, poles, the stele, posts
    lion(p, 8, 1, 11, "south")
    lion(p, 14, 1, 11, "south")
    banner_pole(p, 6, 1, 11, height=5, rotation=8)
    banner_pole(p, 16, 1, 11, height=5, rotation=8)
    p.put(4, 1, 11, "kerb_chisel")
    p.stairs(4, 1, 12, "kerb_stairs", "north")
    p.put(4, 2, 11, "statue")
    p.put(4, 3, 11, "statue_top")
    p.slab(4, 4, 11, "kerb_slab")
    for x in (1, 21):
        post(p, x, 1, 1)
        post(p, x, 1, 11)
    p.path([(11, 0), (11, 2)], 0, width=3, role="paving", alt="kerb", alt_chance=0.1)
    p.path([(11, 10), (11, 12)], 0, width=3, role="paving", alt="kerb", alt_chance=0.1)
    snow_drift(p, 0, 9, 22, 12, 1, 0.35)
    petals(p, 0, 9, 22, 12, 1)
    anchor(p, 4.5, 1.0, 12.5, "kota/situs", 0.0, situs="kota/kota", tier=1, protect=40)
    anchor(p, 11.5, 1.0, 10.5, "kota/penjaga", 0.0)
    anchor(p, 11.5, 9.0, 6.5, "kota/penjaga", 180.0)
    p.weather(0.08)
    finish_lights(p, 11, 1, 1)
    conn(p, 11, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_utama_a", "minecraft:polished_andesite", prio=2)
    save(p, "gerbang_2")


# ======================================================================================================== streets
def street_base(p, sx, sz, lamps=True, drains=True, height=5):
    for z in range(sz):
        for x in range(sx):
            edge = x in (0, sx - 1)
            gutter = drains and x in (1, sx - 2)
            r = p.rng.random()
            if edge:
                p.put(x, 0, z, "kerb")
            elif gutter:
                p.put(x, 0, z, "gutter" if r < 0.8 else "gutter_alt")
            else:
                p.put(x, 0, z, "paving" if r < 0.72 else "paving_alt" if r < 0.92 else "paving_crack")
        for x in range(sx):
            for y in range(1, height):
                p.put(x, y, z, "minecraft:air")
    if lamps:
        for z in range(3, sz - 2, 6):
            post(p, 0, 1, z, height=2)
            post(p, sx - 1, 1, z, height=2)


def lantern_arch(p, z, x0, x1, y=5):
    """Two posts and a rail across the street with paper lanterns hanging from it."""
    for x in (x0, x1):
        for yy in range(1, y + 1):
            p.put(x, yy, z, "wood_fence")
    for x in range(x0, x1 + 1):
        p.put(x, y, z, "wood_fence")
    for x in range(x0 + 2, x1 - 1, 2):
        p.put(x, y - 1, z, "lamp", {"axis": "y"})
        note_light(p, x, y - 1, z)


def jalan_utama(name, west_pool, east_pool, next_pool, seed):
    p = piece(9, 8, 22, seed=seed)
    street_base(p, 9, 22, height=7)
    lantern_arch(p, 6, 0, 8)
    lantern_arch(p, 17, 0, 8)
    # banners of the guard sect on poles between the lamps, pots and barrels by the walls
    for z in (10, 14):
        for x, rot in ((0, 4), (8, 12)):
            banner_pole(p, x, 1, z, height=3, rotation=rot)
    for z in (2, 20):
        p.put(1, 1, z, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
        p.put(7, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    snow_drift(p, 1, 0, 7, 21, 1, 0.12)
    petals(p, 1, 0, 7, 21, 1, 0.08)
    finish_lights(p, 4, 1, 11)
    conn(p, 4, 0, 21, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 4, 0, 0, "north", "kota/keluar", "kota/masuk", next_pool, "minecraft:polished_andesite", prio=2)
    conn(p, 0, 0, 12, "west", "kota/sisi", "kota/pintu", west_pool, "minecraft:stone_bricks", prio=1)
    conn(p, 8, 0, 12, "east", "kota/sisi", "kota/pintu", east_pool, "minecraft:stone_bricks", prio=1)
    save(p, name)


def jalan(name, seed, decor):
    p = piece(7, 10, 16, seed=seed)
    street_base(p, 7, 16, lamps=False, height=6)
    post(p, 0, 1, 2, height=2)
    post(p, 6, 1, 13, height=2)
    if decor == "kios":
        # a fruit stall with a striped awning
        for x, z in ((1, 5), (1, 10)):
            for y in (1, 2):
                p.put(x, y, z, "wood_fence")
        awning(p, 1, 2, 5, 10, 3)
        p.put(1, 1, 7, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(1, 1, 8, "minecraft:melon")
        p.put(1, 2, 8, "minecraft:pumpkin")
        p.put(1, 1, 9, "minecraft:decorated_pot", {"facing": "east", "cracked": "false", "waterlogged": "false"})
        p.put(1, 1, 6, "minecraft:hay_block", {"axis": "z"})
    elif decor == "gerobak":
        # a hand cart full of hay and crates
        p.put(5, 1, 7, "minecraft:hay_block", {"axis": "z"})
        p.put(5, 1, 8, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(5, 2, 8, "minecraft:hay_block", {"axis": "z"})
        for z, f in ((6, "north"), (9, "south")):
            p.put(4, 1, z, "wood_trapdoor", {"facing": f, "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
        p.put(5, 1, 10, "wood_fence")
    elif decor == "pasar":
        # two facing market stalls under awnings, goods on slab counters
        for x in (1, 5):
            for z in (4, 8):
                for y in (1, 2):
                    p.put(x, y, z, "wood_fence")
        awning(p, 1, 1, 4, 8, 3, "cloth", "cloth_alt")
        awning(p, 5, 5, 4, 8, 3, "cloth_alt", "cloth")
        for z in range(5, 8):
            p.slab(1, 1, z, "wood_slab", "top")
            p.slab(5, 1, z, "wood_slab", "top")
        p.put(1, 2, 5, "minecraft:decorated_pot", {"facing": "east", "cracked": "false", "waterlogged": "false"})
        p.put(1, 2, 7, "minecraft:flower_pot")
        p.put(5, 2, 6, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
        p.put(5, 2, 7, "minecraft:potted_red_mushroom")
        p.put(1, 1, 10, "minecraft:barrel", {"facing": "east", "open": "false"})
        p.put(5, 1, 11, "minecraft:composter", {"level": "3"})
    elif decor == "taman":
        # a garden strip with a tree, a bench and a stone lantern
        flowerbed(p, 4, 5, 6, 10, 0, kerb=False)
        for z in range(5, 11):
            p.put(6, 0, z, "kerb")
        p.put(5, 0, 8, "ground_alt", {"snowy": "false"})
        p.put(5, 1, 8, "minecraft:air")
        tree(p, 5, 1, 8, 7 if PAL()["tree"] in ("pine", "spruce") else None)
        p.stairs(1, 1, 7, "wood_stairs", "west")
        p.stairs(1, 1, 8, "wood_stairs", "west")
        stone_lantern(p, 1, 1, 11)
    else:
        # lanterns on a string across the street
        lantern_arch(p, 8, 0, 6, y=5)
    snow_drift(p, 1, 0, 5, 15, 1, 0.15)
    petals(p, 1, 0, 5, 15, 1, 0.1)
    finish_lights(p, 3, 1, 8)
    conn(p, 3, 0, 15, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 3, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    conn(p, 0, 0, 8, "west", "kota/sisi", "kota/pintu", "kota/rumah", "minecraft:stone_bricks")
    conn(p, 6, 0, 8, "east", "kota/sisi", "kota/pintu", "kota/rumah", "minecraft:stone_bricks")
    save(p, name)


def jalan_silang(name="jalan_silang", seed=53031, kind="kuil"):
    p = piece(13, 12, 13, seed=seed)
    for x in range(13):
        for z in range(13):
            ring = max(abs(x - 6), abs(z - 6))
            p.put(x, 0, z, "kerb" if ring == 3 else "paving" if p.rng.random() < 0.8 else "paving_alt")
            for y in range(1, 8):
                p.put(x, y, z, "minecraft:air")
    if kind == "kuil":
        # a little shrine of the earth god: a stone dais, an altar, incense, a roof on four posts
        p.fill(5, 1, 5, 7, 1, 7, "kerb_slab", {"type": "bottom", "waterlogged": "false"})
        p.put(6, 1, 6, "kerb")
        p.put(6, 2, 6, "kerb_chisel")
        p.put(6, 3, 6, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
        for x, z in ((5, 5), (7, 5), (5, 7), (7, 7)):
            for y in (2, 3, 4):
                p.put(x, y, z, "red_wall")
        p.put(6, 2, 5, "minecraft:flower_pot")
        p.put(5, 2, 6, "minecraft:red_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
        p.put(7, 2, 6, "minecraft:red_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
        hip(p, 5, 5, 5, 7, 7, overhang=1, top="pearl")
    else:
        # an old tree in a round planter with two little wells beside it
        p.ring(6, 1, 6, 2, "kerb_slab")
        p.disc(6, 0, 6, 1, "ground_alt")
        tree(p, 6, 1, 6)
        for x, z in ((3, 9), (9, 3)):
            p.ring(x, 1, z, 1, "kerb_wall")
            p.put(x, 0, z, "minecraft:water", {"level": "0"})
            p.put(x, 1, z, "minecraft:air")
    post(p, 0, 1, 0)
    post(p, 12, 1, 0)
    post(p, 0, 1, 12)
    post(p, 12, 1, 12)
    snow_drift(p, 0, 0, 12, 12, 1, 0.2)
    petals(p, 0, 0, 12, 12, 1, 0.12)
    finish_lights(p, 6, 1, 4)
    conn(p, 6, 0, 12, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 6, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    conn(p, 0, 0, 6, "west", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    conn(p, 12, 0, 6, "east", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    save(p, name)


def jembatan(name="jembatan", seed=53032, covered=False):
    """A canal crossing: water on both sides of a humped stone bridge with red rails (or a covered bridge)."""
    p = piece(11, 14, 15, seed=seed)
    for x in range(11):
        for z in range(15):
            p.put(x, 0, z, "kerb")
            for y in range(1, 10):
                p.put(x, y, z, "minecraft:air")
    for x in (0, 1, 9, 10):
        for z in range(4, 11):
            p.put(x, 0, z, "minecraft:water", {"level": "0"})
    for z in range(4, 11):
        p.put(0, 0, z, "kerb")
        p.put(10, 0, z, "kerb")
    # the arch: stairs rise to a slab crown
    for x in range(3, 8):
        p.stairs(x, 1, 5, "kerb_stairs", "south")
        p.stairs(x, 1, 9, "kerb_stairs", "north")
        for z in (6, 7, 8):
            p.slab(x, 1, z, "kerb_slab")
    for z in range(4, 11):
        hump = 1 if 5 <= z <= 9 else 0
        for x in (2, 8):
            p.put(x, 0, z, "kerb")
            p.put(x, 1 + hump, z, "red_wall" if not covered else "rail")
            if hump:
                p.put(x, 1, z, "kerb")
    for x in (2, 8):
        post(p, x, 1, 3, height=2)
        post(p, x, 1, 11, height=2)
    for x, z in ((1, 6), (9, 8), (1, 9)):
        p.put(x, 1, z, "minecraft:lily_pad")
    if covered:
        for x, z in ((2, 5), (8, 5), (2, 9), (8, 9)):
            for y in range(2, 6):
                p.put(x, y, z, "red")
        beam_band(p, 2, 5, 8, 9, 6)
        gable(p, 2, 7, 5, 8, 9, along="z", overhang=1)
        lit(p, 5, 7, 7, chain=1, role="lamp")
    snow_drift(p, 3, 0, 7, 3, 1, 0.3)
    finish_lights(p, 5, 1, 7)
    conn(p, 5, 0, 14, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 5, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    save(p, name)


def tembok_ujung(name, seed, kind):
    """A wall section closing a street: crenellated wall with a walkway, a corner tower; a gate or a drum tower."""
    p = piece(15, 18, 7, seed=seed)
    for x in range(15):
        for z in range(7):
            p.put(x, 0, z, "kerb")
    for x in range(15):
        for y in range(1, 7):
            for z in (1, 2, 3):
                p.put(x, y, z, "kerb" if p.rng.random() < 0.85 else "kerb_alt")
        p.put(x, 7, 1, "kerb_wall" if x % 2 == 0 else "minecraft:air")
        p.put(x, 7, 3, "kerb_wall" if x % 2 == 0 else "minecraft:air")
        p.put(x, 7, 2, "minecraft:air")
    for x in range(15):
        for z in (4, 5, 6):
            for y in range(1, 6):
                p.put(x, y, z, "minecraft:air")
    # a stair up to the walkway on the town side (not where a gate opens)
    if kind != "gerbang":
        for i in range(6):
            p.stairs(2 + i, 1 + i, 4, "kerb_stairs", "east")
            for y in range(1, 1 + i):
                p.put(2 + i, y, 4, "kerb")
    # tower at the east end
    p.walls(10, 1, 1, 13, 9, 4, role="kerb", pillar="kerb_chisel", windows=2, window_role="minecraft:iron_bars")
    beam_band(p, 10, 1, 13, 4, 9)
    hip(p, 10, 10, 1, 13, 4, overhang=1)
    lit(p, 11, 9, 2)
    if kind == "gerbang":
        for x in range(5, 8):
            for y in range(1, 4):
                for z in (1, 2, 3):
                    p.put(x, y, z, "minecraft:air")
        p.fill(5, 4, 1, 7, 4, 3, "beam", {"axis": "x"})
        p.put(6, 1, 1, "wood_gate", {"facing": "north", "in_wall": "false", "open": "false", "powered": "false"})
        wall_banner(p, 4, 5, 4, "south")
        wall_banner(p, 8, 5, 4, "south")
    elif kind == "drum":
        # a drum and a banner on the walkway, arrow slits
        p.put(5, 7, 2, "minecraft:note_block", {"instrument": "basedrum", "note": "0", "powered": "false"})
        banner_pole(p, 8, 7, 2, height=3, rotation=0)
        for x in (3, 7, 9):
            p.put(x, 4, 1, "minecraft:iron_bars")
        wall_banner(p, 8, 5, 4, "south", PAL()["banner_alt"])
    post(p, 9, 1, 5)
    snow_drift(p, 0, 4, 14, 6, 1, 0.4)
    finish_lights(p, 6, 1, 5)
    p.weather(0.1)
    conn(p, 6, 0, 6, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:stone_bricks")
    save(p, name)


# ======================================================================================================== the square
def alun():
    p = piece(33, 16, 33, seed=53040)
    for x in range(33):
        for z in range(33):
            ring = max(abs(x - 16), abs(z - 16))
            role = "kerb" if ring in (16, 9) else ("paving" if (x + z) % 2 == 0 else "paving_alt")
            if ring <= 3:
                role = "kerb"
            if ring == 12 and (x == 16 or z == 16):
                role = "kerb_chisel"
            p.put(x, 0, z, role)
            for y in range(1, 8):
                p.put(x, y, z, "minecraft:air")
    # the well under a little roof, a rope and a bucket barrel
    p.ring(16, 1, 16, 2, "kerb")
    for x in range(15, 18):
        for z in range(15, 18):
            p.put(x, 0, z, "minecraft:water", {"level": "0"})
            if (x, z) != (16, 16):
                p.put(x, 1, z, "minecraft:air")
    p.put(16, 1, 16, "minecraft:air")
    for x, z in ((14, 14), (18, 14), (14, 18), (18, 18)):
        p.pillar(x, 1, 4, z, role="red", base="kerb", cap="beam")
    beam_band(p, 14, 14, 18, 18, 5)
    gable(p, 14, 6, 14, 18, 18, along="x", overhang=1)
    for y in (2, 3, 4):
        p.put(16, y, 16, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    p.put(16, 5, 16, "beam", {"axis": "x"})
    p.put(19, 1, 16, "minecraft:barrel", {"facing": "up", "open": "true"})
    lit(p, 15, 5, 16, chain=1, role="lamp")
    # the old tree with stone benches around it
    p.disc(6, 0, 6, 2, "ground_alt", alt="ground", alt_chance=0.4)
    tree(p, 6, 1, 6, 10 if PAL()["tree"] in ("pine", "spruce") else None)
    for x, z, f in ((3, 10, "south"), (6, 10, "south"), (10, 3, "east"), (10, 6, "east")):
        p.stairs(x, 1, z, "kerb_stairs", OPP[f])
    stone_lantern(p, 10, 1, 10)
    # the notice board: two posts, a board of paper notices, a little roof, and its keeper
    for x in (23, 29):
        for y in range(1, 5):
            p.put(x, y, 7, "log", {"axis": "y"})
    for x in range(24, 29):
        for y in (2, 3):
            p.put(x, y, 7, "light_wood")
        wall_banner(p, x, 3, 8, "south", "white") if x % 2 == 0 else None
    for x in range(22, 31):
        p.stairs(x, 5, 6, "roof", "south")
        p.stairs(x, 5, 8, "roof", "north")
        p.slab(x, 6, 7, "roof_slab")
    p.stairs(22, 6, 7, "roof", "east", half="top")
    p.stairs(30, 6, 7, "roof", "west", half="top")
    lit(p, 26, 5, 7, chain=0)
    anchor(p, 26.5, 1.0, 9.5, "kota/papan", 180.0)
    # the huabiao pair before the hall, the incense burner before the well
    huabiao(p, 12, 1, 3, height=7)
    huabiao(p, 20, 1, 3, height=7)
    incense_burner(p, 16, 1, 23)
    # flower beds, lantern posts, banner poles, stone lanterns
    flowerbed(p, 22, 22, 26, 26, 0)
    flowerbed(p, 6, 22, 10, 26, 0)
    for x in (3, 29):
        for z in (3, 29):
            post(p, x, 1, z, height=3)
    for x in (12, 20):
        post(p, x, 1, 31, height=2)
    for x, z in ((14, 21), (18, 21)):
        stone_lantern(p, x, 1, z)
    for x, z, rot in ((1, 12, 4), (1, 20, 4), (31, 12, 12), (31, 20, 12)):
        banner_pole(p, x, 1, z, height=4, rotation=rot)
    # a cart of the morning market and two stalls on the east side
    p.put(27, 1, 14, "minecraft:hay_block", {"axis": "x"})
    p.put(28, 1, 14, "minecraft:barrel", {"facing": "up", "open": "false"})
    for x, z in ((25, 18), (25, 20), (28, 18), (28, 20)):
        p.put(x, 1, z, "wood_fence")
        p.put(x, 2, z, "wood_fence")
    awning(p, 25, 28, 18, 20, 3)
    p.slab(26, 1, 19, "wood_slab", "top")
    p.slab(27, 1, 19, "wood_slab", "top")
    p.put(26, 2, 19, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    p.put(27, 2, 19, "minecraft:candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    snow_drift(p, 1, 1, 31, 31, 1, 0.1)
    petals(p, 1, 1, 31, 31, 1, 0.06)
    anchor(p, 16.5, 1.0, 12.5, "kota/alun", 180.0, protect=72)
    anchor(p, 20.5, 1.0, 22.5, "kota/warga", 0.0)
    anchor(p, 11.5, 1.0, 21.5, "kota/warga", 90.0)
    anchor(p, 22.5, 1.0, 12.5, "kota/penjaga", 270.0)
    finish_lights(p, 16, 1, 22)
    conn(p, 16, 0, 32, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:stone_bricks")
    conn(p, 16, 0, 0, "north", "kota/sisi", "kota/pintu", "kota/unik_lelang", "minecraft:stone_bricks", prio=3)
    conn(p, 32, 0, 16, "east", "kota/sisi", "kota/pintu", "kota/unik_teh", "minecraft:stone_bricks", prio=3)
    conn(p, 0, 0, 16, "west", "kota/sisi", "kota/pintu", "kota/unik_peti", "minecraft:stone_bricks", prio=3)
    conn(p, 32, 0, 28, "east", "kota/keluar", "kota/masuk", "kota/jalan", "minecraft:stone_bricks", prio=1)
    conn(p, 0, 0, 28, "west", "kota/keluar", "kota/masuk", "kota/jalan", "minecraft:stone_bricks", prio=1)
    conn(p, 1, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan", "minecraft:stone_bricks", prio=1)
    conn(p, 31, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan", "minecraft:stone_bricks", prio=1)
    save(p, "alun")


# ======================================================================================================== unique buildings
def veranda(p, x0, x1, z, y0, y1, every=3, door_x=None, door_w=3):
    """A row of red columns on stone bases at z with a rail between them (open in the door bay)."""
    for x in range(x0, x1 + 1, every):
        p.pillar(x, y0, y1, z, role="red", base="kerb", cap="beam")
    half = door_w // 2
    for x in range(x0, x1 + 1):
        if (x - x0) % every == 0 or (door_x is not None and abs(x - door_x) <= half):
            continue
        p.put(x, y0, z, "rail")


def balai_lelang():
    sx, sy, sz = 23, 30, 17
    p = piece(sx, sy, sz, seed=53050)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="wood", alt="wood_alt", alt_chance=0.25)
    p.walls(1, 2, 1, sx - 2, 7, sz - 3, role="plaster", pillar="red", windows=4, window_role="window", door=("south", 3))
    # the veranda along the front: red columns under the lower eave, a rail between them, banners on the wall
    p.fill(1, 1, sz - 2, sx - 2, 1, sz - 2, "kerb")
    veranda(p, 1, sx - 2, sz - 2, 2, 7, every=3, door_x=11)
    for x in (5, 17):
        wall_banner(p, x, 6, sz - 2, "south")
    for x in range(10, 13):
        p.stairs(x, 1, sz - 1, "kerb_stairs", "north")
    lion(p, 7, 1, sz - 1, "south")
    lion(p, 15, 1, sz - 1, "south")
    # the gallery (second floor ring) with a lattice rail
    p.fill(2, 7, 2, sx - 3, 7, sz - 4, "wood")
    p.air(6, 7, 4, sx - 7, 7, sz - 6)
    rail_line(p, (6, 4), (sx - 7, 4), 8)
    rail_line(p, (6, sz - 6), (sx - 7, sz - 6), 8)
    rail_line(p, (6, 5), (6, sz - 7), 8)
    rail_line(p, (sx - 7, 5), (sx - 7, sz - 7), 8)
    p.walls(1, 8, 1, sx - 2, 12, sz - 3, role="plaster", pillar="red", windows=3, window_role="window", interior_air=True)
    p.fill(2, 7, 2, sx - 3, 7, 3, "wood")
    p.staircase(2, 2, 10, "north", 6, width=1, role="wood_stairs", support="wood")
    p.air(2, 7, 6, 2, 8, 10)
    # gallery seats and a tea table for the rich bidders
    for x in (3, 19):
        for z in (3, 9):
            p.stairs(x, 8, z, "wood_stairs", "east" if x == 3 else "west")
    # the stage at the back: raised, red carpet, gold edge, two pillars, a lacquer screen, the lectern
    p.fill(7, 2, 2, sx - 8, 2, 5, "kerb_slab", {"type": "top", "waterlogged": "false"})
    for x in range(7, sx - 7):
        p.put(x, 3, 4, "minecraft:red_carpet")
        p.put(x, 3, 5, "minecraft:red_carpet")
        p.stairs(x, 2, 6, "kerb_stairs", "south")
    p.fill(8, 3, 2, sx - 9, 6, 2, "red")
    p.fill(10, 4, 2, sx - 11, 5, 2, "accent")
    p.put(11, 6, 2, "minecraft:emerald_block")
    for x in (7, sx - 8):
        p.pillar(x, 3, 6, 3, role="red", base="accent", cap="accent")
    p.put(11, 3, 4, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    for x in (8, 14):
        p.put(x, 3, 3, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    # bidders' benches with an aisle, display tables along the walls
    for z in (8, 10, 12):
        for x in range(5, sx - 5):
            if x not in (10, 11, 12):
                p.stairs(x, 2, z, "wood_stairs", "south")
    for z in (8, 11):
        table(p, 2, 2, z)
        table(p, sx - 3, 2, z)
        p.put(2, 2, z + 1, "minecraft:decorated_pot", {"facing": "east", "cracked": "false", "waterlogged": "false"})
        candle(p, sx - 3, 2, z + 1, 3, "white")
    # a lantern chandelier over the hall and lanterns inside and on the eaves
    for x in (9, 11, 13):
        lit(p, x, 12, 9, chain=2, role="lamp")
    for x in (5, 17):
        lit(p, x, 7, 8, chain=1)
    for x in (1, 7, 15, sx - 2):
        lit(p, x, 7, sz - 2, chain=1, role="lamp")
    # dougong, a double-eave hip roof with a pearl
    beam_band(p, 1, 1, sx - 2, sz - 3, 13, role="beam")
    brackets(p, 1, 1, sx - 2, sz - 3, 13)
    hip(p, 1, 14, 1, sx - 2, sz - 3, overhang=1, tiers=2, tier_gap=2, tier_band=2, band_role="plaster", top="pearl")
    plaque(p, 11, 6, sz - 2)
    anchor(p, 11.5, 3.0, 3.5, "kota/lelang", 0.0)
    anchor(p, 11.5, 7.0, 3.5, "kota/panggung", 0.0, protect=0, pts=[-3, 0, 0, 0, 0, 0, 3, 0, 0])
    anchor(p, 4.5, 2.0, 12.5, "kota/warga", 180.0)
    finish_lights(p, 11, 2, 10)
    front_door(p, sx, sz, 11)
    save(p, "balai_lelang")


def kedai_teh():
    sx, sy, sz = 23, 18, 23
    p = piece(sx, sy, sz, seed=53051)
    # the pond with lilies and stone banks
    for x in range(sx):
        for z in range(sz):
            bank = x in (0, sx - 1) or z in (0, sz - 1)
            p.put(x, 0, z, "kerb" if bank else "minecraft:water", None if bank else {"level": "0"})
            for y in range(1, 5):
                p.put(x, y, z, "minecraft:air")
    for _ in range(18):
        x = p.rng.randrange(2, sx - 2)
        z = p.rng.randrange(2, sz - 2)
        if abs(x - 11) > 5 or abs(z - 10) > 5:
            p.put(x, 1, z, "minecraft:lily_pad")
    # rocks in the water
    for x, z in ((3, 14), (18, 4), (19, 15), (4, 6)):
        p.put(x, 0, z, "kerb_alt")
        p.put(x, 1, z, "minecraft:cobblestone_wall" if CUR["v"] != "sabana" else "kerb_wall")
    # the zig-zag bridge from the door (south) to the pavilion, a rail on both sides
    zig = [(11, 22), (11, 19), (8, 19), (8, 16), (11, 16)]
    cells = set()
    for (ax, az), (bx, bz) in zip(zig, zig[1:]):
        for t in range(max(abs(bx - ax), abs(bz - az)) + 1):
            x = ax + (0 if bx == ax else (t if bx > ax else -t))
            z = az + (0 if bz == az else (t if bz > az else -t))
            cells.add((x, z))
    for x, z in cells:
        p.put(x, 0, z, "light_wood")
    for x, z in cells:
        for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + ox, z + oz
            if (nx, nz) not in cells and p.get(nx, 0, nz) == "minecraft:water" and nz < 21:
                p.put(nx, 0, nz, "light_wood")
                p.put(nx, 1, nz, "minecraft:bamboo_fence")
    # the pavilion on its stone island
    p.fill(6, 0, 4, 16, 0, 15, "kerb")
    p.floor(7, 1, 5, 15, 14, role="minecraft:bamboo_mosaic", alt="minecraft:bamboo_planks", alt_chance=0.3)
    for x, z in ((7, 5), (15, 5), (7, 14), (15, 14), (11, 5), (9, 14), (13, 14), (7, 9), (15, 9)):
        p.pillar(x, 2, 6, z, role="red", base="kerb", cap="beam")
    for x in range(8, 15):
        if x != 11:
            p.put(x, 2, 5, "minecraft:bamboo_fence")
    for z in range(6, 14):
        for x in (7, 15):
            if z not in (9,):
                lattice_window(p, x, 4, z, "west" if x == 7 else "east")
                p.put(x, 2, z, "minecraft:bamboo_fence") if z not in (12, 13) or x == 15 else None
    beam_band(p, 7, 5, 15, 14, 7)
    brackets(p, 7, 5, 15, 14, 7)
    gable(p, 7, 8, 5, 15, 14, along="z", overhang=2)
    # low tables and cushions, tea sets
    for tx, tz in ((9, 8), (13, 8), (9, 12), (13, 12)):
        p.put(tx, 2, tz, "wood_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
        p.put(tx, 3, tz, "minecraft:flower_pot" if (tx + tz) % 2 else "minecraft:white_candle", None if (tx + tz) % 2 else
              {"candles": "1", "lit": "true", "waterlogged": "false"})
        for ox in (-1, 1):
            p.put(tx + ox, 2, tz, "minecraft:green_carpet" if CUR["v"] != "sakura" else "minecraft:pink_carpet")
    # the tea master's counter, stove and kettle shelf
    p.fill(10, 2, 6, 12, 2, 6, "light_wood")
    p.put(13, 2, 6, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    p.put(10, 3, 6, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    p.put(12, 3, 6, "minecraft:flower_pot")
    p.put(9, 2, 6, "minecraft:barrel", {"facing": "up", "open": "false"})
    # bamboo groves in the corners, a stone lantern on each bank corner
    for bx, bz in ((2, 2), (3, 3), (2, 4), (20, 2), (19, 3), (20, 20), (19, 19), (2, 20), (3, 19)):
        p.put(bx, 0, bz, "ground_alt", {"snowy": "false"})
        bamboo_clump(p, bx, bz, 1, 6 + p.rng.randrange(4))
    for x, z in ((1, 1), (21, 1)):
        stone_lantern(p, x, 1, z)
    for x in (7, 15):
        lit(p, x, 7, 4, chain=1, role="lamp")
        lit(p, x, 7, 15, chain=1, role="lamp")
    lit(p, 11, 8, 10, chain=2)
    anchor(p, 11.5, 2.0, 7.5, "kota/teh", 180.0)
    anchor(p, 8.5, 2.0, 8.5, "kota/kursi_pengembara", 90.0)
    anchor(p, 14.5, 2.0, 12.5, "kota/kursi_pengembara", 270.0)
    anchor(p, 11.5, 0.0, 11.5, "kota/kolam", 0.0, protect=0, pts=[-7, 0, -7, 7, 0, -7, -7, 0, 7, 7, 0, 7, 0, 0, 9, -9, 0, 0])
    finish_lights(p, 11, 2, 10)
    front_door(p, sx, sz, 11)
    save(p, "kedai_teh")


def kedai_teh_2():
    """A two-storey teahouse behind a courtyard garden with a pond, a balcony with a carved rail and a moon window."""
    sx, sy, sz = 21, 20, 21
    p = piece(sx, sy, sz, seed=53057)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.air(0, 1, 0, sx - 1, sy - 1, sz - 1)
    # the building at the back
    p.floor(1, 1, 1, sx - 2, 10, role="light_wood", alt="wood", alt_chance=0.2)
    p.walls(1, 2, 1, sx - 2, 6, 10, role="plaster", pillar="red", windows=3, window_role="window", door=("south", 3))
    p.fill(1, 7, 1, sx - 2, 7, 11, "wood")
    p.walls(2, 8, 2, sx - 3, 11, 9, role="plaster", pillar="red", windows=3, window_role="window")
    p.air(3, 7, 3, 5, 7, 5)
    p.staircase(3, 2, 5, "north", 5, width=1, role="wood_stairs", support="wood")
    # balcony on the upper floor over the garden
    rail_line(p, (1, 11), (sx - 2, 11), 8)
    for x in (2, 6, 14, 18):
        lit(p, x, 12, 10, chain=1, role="lamp")
    beam_band(p, 2, 2, sx - 3, 9, 12)
    brackets(p, 2, 2, sx - 3, 9, 12)
    hip(p, 2, 13, 2, sx - 3, 9, overhang=2, top="pearl")
    # ground floor: counter, stove, shelves, tables
    p.fill(13, 2, 2, 17, 2, 2, "light_wood")
    p.put(16, 2, 3, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    for x in (13, 15, 17):
        shelf(p, x, 4, 2, "south")
    p.put(14, 3, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    for tx, tz in ((5, 7), (10, 7), (15, 7)):
        table(p, tx, 2, tz)
        chair(p, tx - 1, 2, tz, "east")
        chair(p, tx + 1, 2, tz, "west")
    # upstairs: private rooms with low tables and cushions
    for tx in (6, 10, 14):
        p.put(tx, 8, 5, "wood_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
        p.put(tx - 1, 8, 5, "rug")
        p.put(tx + 1, 8, 5, "rug")
        p.put(tx, 9, 5, "minecraft:white_candle", {"candles": "1", "lit": "true", "waterlogged": "false"})
    # the courtyard garden: path, pond with a little bridge, rocks, bamboo, flowers, stone lanterns
    for x in range(1, sx - 1):
        for z in range(12, sz - 1):
            p.put(x, 0, z, "ground", {"snowy": "false"} if p.b("ground").endswith("grass_block") else None)
    p.path([(10, sz - 1), (10, 11)], 0, width=3, role="paving", alt="paving_alt", alt_chance=0.2)
    pond(p, 15, 15, 3, 2, 0, lilies=4)
    pond(p, 5, 16, 2, 2, 0, lilies=2)
    for bx, bz in ((1, 13), (2, 12), (1, 19), (19, 19), (19, 12)):
        bamboo_clump(p, bx, bz, 1, 6 + p.rng.randrange(3))
    flowerbed(p, 2, 13, 6, 13, 0, kerb=False)
    stone_lantern(p, 8, 1, 13)
    stone_lantern(p, 12, 1, 13)
    tree(p, 17, 1, 19, 6 if PAL()["tree"] in ("pine", "spruce") else None)
    for x in (0, sx - 1):
        for z in range(12, sz):
            p.put(x, 1, z, "kerb_wall")
    for x in range(0, sx):
        if abs(x - 10) > 1:
            p.put(x, 1, sz - 1, "kerb_wall")
    snow_drift(p, 1, 12, sx - 2, sz - 2, 1, 0.3)
    petals(p, 1, 12, sx - 2, sz - 2, 1, 0.2)
    lit(p, 10, 6, 11, chain=1, role="lamp")
    plaque(p, 10, 5, 10)
    for x in range(1, sx - 1):
        p.put(x, 1, 11, "kerb" if abs(x - 10) > 1 else "minecraft:air")
    for x in (9, 10, 11):
        p.stairs(x, 1, 11, "kerb_stairs", "north")
    anchor(p, 15.5, 2.0, 3.5, "kota/teh", 180.0)
    anchor(p, 4.5, 2.0, 7.5, "kota/kursi_pengembara", 90.0)
    anchor(p, 16.5, 2.0, 7.5, "kota/kursi_pengembara", 270.0)
    anchor(p, 15.5, 0.0, 15.5, "kota/kolam", 0.0, protect=0, pts=[-3, 0, -1, 3, 0, 1, 0, 0, 0, -10, 0, 1, 2, 0, -2])
    finish_lights(p, 10, 2, 8)
    front_door(p, sx, sz, 10)
    save(p, "kedai_teh_2")


def toko_peti():
    """The coffin shop and, below it, the Pasar Gelap (floor of the shop at y = 9, the market hall at y = 1..7)."""
    sx, sy, sz = 15, 27, 17
    F0 = 9
    p = piece(sx, sy, sz, seed=53052)
    # the underground hall: blackstone, stalls under violet awnings, braziers, cages, crates
    p.box(0, 0, 0, sx - 1, F0 - 1, sz - 1, "minecraft:polished_blackstone_bricks", hollow=True)
    p.floor(1, 1, 1, sx - 2, sz - 2, role="minecraft:polished_blackstone", alt="minecraft:blackstone", alt_chance=0.3)
    p.air(1, 2, 1, sx - 2, F0 - 2, sz - 2)
    for x in (1, sx - 2):
        for z in range(2, sz - 2, 4):
            p.put(x, 2, z, "minecraft:soul_campfire", {"facing": "east" if x == 1 else "west", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    for sxi, sz0 in ((3, 3), (3, 9), (9, 3), (9, 9)):
        for x in range(sxi, sxi + 3):
            p.put(x, 2, sz0, "minecraft:dark_oak_slab", {"type": "top", "waterlogged": "false"})
        p.put(sxi, 3, sz0 - 1, "minecraft:dark_oak_fence")
        p.put(sxi + 2, 3, sz0 - 1, "minecraft:dark_oak_fence")
        p.put(sxi, 2, sz0 - 1, "minecraft:dark_oak_fence")
        p.put(sxi + 2, 2, sz0 - 1, "minecraft:dark_oak_fence")
        p.fill(sxi, 4, sz0 - 1, sxi + 2, 4, sz0, "minecraft:purple_wool")
        p.put(sxi + 1, 2, sz0 - 1, "minecraft:barrel", {"facing": "up", "open": "true"})
        p.put(sxi + 1, 3, sz0, "minecraft:candle", {"candles": "2", "lit": "true", "waterlogged": "false"}) if (sxi + sz0) % 2 else \
            p.put(sxi, 3, sz0, "minecraft:decorated_pot", {"facing": "south", "cracked": "true", "waterlogged": "false"})
    # a cage with something rare, cobwebs in the corners, a hanging soul lantern over each stall
    for y in (2, 3):
        for x, z in ((12, 13), (13, 13), (12, 14)):
            p.put(x, y, z, "minecraft:iron_bars")
    p.put(13, 2, 14, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    for x, z in ((1, 1), (sx - 2, 1), (1, sz - 2)):
        p.put(x, F0 - 2, z, "minecraft:cobweb")
    chest(p, 6, 2, 13, K + "chests/dunia/kota/pasar_gelap")
    p.put(7, 2, 13, "minecraft:smithing_table")
    p.put(5, 2, 13, "minecraft:barrel", {"facing": "north", "open": "false"})
    for x in (4, 10):
        lit(p, x, F0 - 1, 7, chain=1, role="minecraft:soul_lantern")
    lit(p, 7, F0 - 1, 11, chain=2, role="minecraft:soul_lantern")
    anchor(p, 7.5, 2.0, 11.5, "kota/gelap", 0.0)
    # the stair from the back room down into the hall
    for i in range(F0 - 1):
        z = 13 - i
        if z < 2:
            break
        p.stairs(sx - 3, F0 - 1 - i, z, "minecraft:polished_blackstone_brick_stairs", "south")
        for y in range(F0 - i, F0 + 2):
            p.put(sx - 3, y, z, "minecraft:air")
    # the shop above
    p.fill(0, F0 - 1, 0, sx - 1, F0 - 1, sz - 1, "kerb")
    p.floor(1, F0, 1, sx - 2, sz - 2, role="wood_alt", alt="wood", alt_chance=0.3)
    p.walls(1, F0 + 1, 1, sx - 2, F0 + 5, sz - 2, role="wood", pillar="beam", windows=4, window_role="window", door=("south", 1))
    for z in range(9, 14):
        p.put(sx - 3, F0, z, "minecraft:air")
    # coffins on trestles, paper money, incense, a funeral lantern
    for cz in (4, 7):
        for x in range(3, 7):
            p.put(x, F0 + 1, cz, "wood_alt")
            p.put(x, F0 + 2, cz, "minecraft:dark_oak_trapdoor", {"facing": "north", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})
    p.put(3, F0 + 1, 11, "minecraft:white_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    p.put(8, F0 + 1, 4, "minecraft:white_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    for x in (9, 11):
        wall_banner(p, x, F0 + 3, 2, "south", "white")
    p.put(10, F0 + 1, 4, "minecraft:flower_pot")
    p.put(12, F0 + 1, 6, "minecraft:decorated_pot", {"facing": "west", "cracked": "false", "waterlogged": "false"})
    # the back room wall with the hidden door gap (the door entity fills it)
    for x in range(2, sx - 2):
        for y in (1, 2, 3):
            p.put(x, F0 + y, 9, "wood")
    for x in (sx - 4, sx - 3):
        for y in (1, 2):
            p.put(x, F0 + y, 9, "minecraft:air")
    shelf(p, 4, F0 + 2, 9, "south")
    shelf(p, 6, F0 + 2, 9, "south")
    anchor(p, sx - 3.5, F0 + 1.0, 9.5, "kota/gerbang_gelap", 0.0, protect=12)
    beam_band(p, 1, 1, sx - 2, sz - 2, F0 + 6)
    gable(p, 1, F0 + 7, 1, sx - 2, sz - 2, along="x", overhang=1)
    plaque(p, 7, F0 + 4, sz - 2)
    wall_banner(p, 5, F0 + 4, sz - 1, "south", "white")
    wall_banner(p, 9, F0 + 4, sz - 1, "south", "white")
    lit(p, 7, F0 + 5, 5, chain=1, role="minecraft:soul_lantern")
    finish_lights(p, 7, F0 + 1, 13)
    conn(p, 7, F0, sz - 1, "south", "kota/pintu", "minecraft:empty", "minecraft:empty", "minecraft:stone_bricks")
    save(p, "toko_peti")



def paviliun(name, seed, kind):
    sx, sy, sz = 11, 17, 11
    p = piece(sx, sy, sz, seed=seed)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="wood_alt", alt="wood", alt_chance=0.25)
    p.walls(1, 2, 1, sx - 2, 6, sz - 3, role="plaster", pillar="red", windows=3, window_role="window", door=("south", 3))
    p.fill(1, 1, sz - 2, sx - 2, 1, sz - 2, "kerb")
    veranda(p, 1, sx - 2, sz - 2, 2, 6, every=4, door_x=5)
    p.fill(3, 2, 5, 7, 2, 5, "wood_slab", {"type": "top", "waterlogged": "false"})
    if kind == "pil":
        for x in range(2, 9):
            p.put(x, 2, 2, "minecraft:bookshelf" if x % 3 else "minecraft:chiseled_bookshelf",
                  None if x % 3 else {"facing": "south", "slot_0_occupied": "true", "slot_1_occupied": "false", "slot_2_occupied": "true",
                                      "slot_3_occupied": "false", "slot_4_occupied": "true", "slot_5_occupied": "false"})
            p.put(x, 3, 2, "minecraft:flower_pot" if x % 2 else "minecraft:potted_bamboo")
            shelf(p, x, 4, 2, "south")
        p.put(2, 2, 4, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
        p.put(8, 2, 4, "minecraft:water_cauldron", {"level": "2"})
        p.put(5, 3, 5, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
        p.put(4, 3, 5, "minecraft:potted_red_mushroom")
        p.put(6, 3, 5, "minecraft:potted_brown_mushroom")
        for x in (3, 7):
            p.put(x, 5, 3, "minecraft:hay_block", {"axis": "x"})
        role = "kota/pil"
    else:
        p.put(2, 2, 2, "minecraft:blast_furnace", {"facing": "south", "lit": "true"})
        p.put(3, 2, 2, "minecraft:anvil", {"facing": "east"})
        p.put(7, 2, 2, "minecraft:grindstone", {"face": "floor", "facing": "south"})
        p.put(8, 2, 2, "minecraft:smithing_table")
        for x in range(4, 7):
            p.put(x, 2, 2, "minecraft:iron_bars")
            p.put(x, 3, 2, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
        p.put(5, 3, 5, "minecraft:anvil", {"facing": "east"})
        p.put(2, 2, 4, "minecraft:water_cauldron", {"level": "3"})
        role = "kota/pusaka"
    beam_band(p, 1, 1, sx - 2, sz - 3, 7)
    brackets(p, 1, 1, sx - 2, sz - 3, 7)
    hip(p, 1, 8, 1, sx - 2, sz - 3, overhang=1, top="pearl")
    plaque(p, 5, 5, sz - 2)
    lit(p, 5, 6, 4, chain=1)
    lit(p, 1, 7, sz - 2, chain=1, role="lamp")
    lit(p, sx - 2, 7, sz - 2, chain=1, role="lamp")
    post(p, 0, 1, sz - 1)
    post(p, sx - 1, 1, sz - 1)
    anchor(p, 5.5, 2.0, 4.0, role, 0.0)
    finish_lights(p, 5, 2, 7)
    front_door(p, sx, sz, 5)
    save(p, name)


def paviliun_2(name, seed, kind):
    """The bigger pavilion: a hall with a front yard (an herb garden or an open-air forge)."""
    sx, sy, sz = 15, 18, 15
    p = piece(sx, sy, sz, seed=seed)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.air(0, 1, 0, sx - 1, sy - 1, sz - 1)
    p.floor(1, 1, 1, sx - 2, 8, role="wood_alt", alt="wood", alt_chance=0.25)
    p.walls(1, 2, 1, sx - 2, 6, 8, role="plaster", pillar="red", windows=3, window_role="window", door=("south", 3))
    beam_band(p, 1, 1, sx - 2, 8, 7)
    brackets(p, 1, 1, sx - 2, 8, 7)
    gable(p, 1, 8, 1, sx - 2, 8, along="x", overhang=1)
    plaque(p, 7, 5, 8)
    # the front yard with a low wall and a gate gap
    for x in range(1, sx - 1):
        for z in range(9, sz - 1):
            p.put(x, 0, z, "paving" if p.rng.random() < 0.7 else "paving_alt")
    for x in range(0, sx):
        if abs(x - 7) > 1:
            p.put(x, 1, sz - 1, "kerb_wall")
    for z in range(9, sz):
        p.put(0, 1, z, "kerb_wall")
        p.put(sx - 1, 1, z, "kerb_wall")
    if kind == "pil":
        # inside: drawers of herbs (barrels), shelves, the pill furnace, a scale table
        for x in range(2, 13):
            p.put(x, 2, 2, "minecraft:barrel" if x % 2 else "minecraft:bookshelf", {"facing": "south", "open": "false"} if x % 2 else None)
            shelf(p, x, 3, 2, "south")
            p.put(x, 4, 2, "minecraft:flower_pot" if x % 3 else "minecraft:potted_azure_bluet")
        p.put(11, 2, 5, "minecraft:blast_furnace", {"facing": "west", "lit": "true"})
        p.put(11, 2, 6, "minecraft:campfire", {"facing": "west", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        p.put(10, 2, 5, "minecraft:cauldron")
        table(p, 4, 2, 5)
        p.put(3, 2, 5, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "true", "has_bottle_2": "false"})
        # the herb garden: rows of crops in tilled earth, a water channel, drying racks
        for x in range(2, 13):
            for z in (10, 12):
                if x == 7:
                    continue
                p.put(x, 0, z, "minecraft:farmland", {"moisture": "7"})
                crop = ["minecraft:wheat", "minecraft:carrots", "minecraft:beetroots", "minecraft:potatoes"][(x + z) % 4]
                p.put(x, 1, z, crop, {"age": "3" if crop == "minecraft:beetroots" else "5"})
            p.put(x, 0, 11, "minecraft:water", {"level": "0"}) if x != 7 else None
        for x in (2, 12):
            for y in (1, 2):
                p.put(x, y, 13, "wood_fence")
            p.put(x, 3, 13, "minecraft:hay_block", {"axis": "y"})
        role = "kota/pil"
    else:
        # inside: weapon racks, the show-case table, the owner's anvil
        for x in range(2, 13, 2):
            p.put(x, 2, 2, "wood_fence")
            p.put(x, 3, 2, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
        for x in range(3, 12, 2):
            p.put(x, 2, 2, "minecraft:iron_bars")
        table(p, 5, 2, 5, top="minecraft:decorated_pot")
        table(p, 9, 2, 5, top=None)
        p.put(12, 2, 6, "minecraft:smithing_table")
        # the open forge in the yard: furnace, anvils, quench trough, coal heap, smoke
        p.put(2, 1, 10, "minecraft:blast_furnace", {"facing": "east", "lit": "true"})
        p.put(2, 1, 11, "minecraft:campfire", {"facing": "east", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        p.put(2, 2, 10, "minecraft:blast_furnace", {"facing": "east", "lit": "true"})
        p.put(4, 1, 11, "minecraft:anvil", {"facing": "south"})
        p.put(5, 1, 12, "minecraft:chipped_anvil", {"facing": "east"})
        p.put(11, 1, 10, "minecraft:water_cauldron", {"level": "3"})
        p.put(12, 1, 10, "minecraft:water_cauldron", {"level": "3"})
        p.put(12, 1, 12, "minecraft:coal_block")
        p.put(11, 1, 12, "minecraft:grindstone", {"face": "floor", "facing": "west"})
        for x in (4, 10):
            for y in (1, 2, 3):
                p.put(x, y, 13, "wood_fence")
            p.put(x, 4, 13, "minecraft:%s_banner" % PAL()["banner"], {"rotation": "8"})
        role = "kota/pusaka"
    for x in (2, 12):
        lit(p, x, 7, 9, chain=1, role="lamp")
    lit(p, 7, 6, 4, chain=1)
    stone_lantern(p, 5, 1, 13) if kind == "pil" else None
    snow_drift(p, 1, 9, sx - 2, sz - 2, 1, 0.25)
    petals(p, 1, 9, sx - 2, sz - 2, 1, 0.15)
    anchor(p, 7.5, 2.0, 5.0, role, 0.0)
    finish_lights(p, 7, 2, 7)
    front_door(p, sx, sz, 7)
    save(p, name)


def penginapan():
    sx, sy, sz = 15, 22, 13
    p = piece(sx, sy, sz, seed=53055)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="wood_alt")
    p.walls(1, 2, 1, sx - 2, 5, sz - 2, role="plaster", pillar="beam", windows=3, door=("south", 3))
    p.fill(1, 6, 1, sx - 2, 6, sz - 2, "wood_alt")
    p.walls(1, 7, 1, sx - 2, 10, sz - 2, role="plaster", pillar="beam", windows=3)
    p.staircase(2, 2, 9, "north", 5, role="wood_stairs", support="wood_alt")
    p.air(2, 6, 6, 2, 6, 9)
    # guest rooms upstairs: partitions, beds, a chest and a lamp each
    for x in (5, 9):
        for y in range(7, 10):
            for z in range(2, 7):
                p.put(x, y, z, "plaster" if z != 6 or y > 8 else "minecraft:air")
    for bx in (3, 7, 11):
        bed(p, bx, 7, 2, "north")
        p.put(bx + 1, 7, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(bx + 1, 8, 2, "minecraft:white_candle", {"candles": "1", "lit": "true", "waterlogged": "false"})
        p.put(bx, 7, 4, "rug")
    # the common room: tables with benches, a hearth, a wine counter
    for tx in (5, 9):
        table(p, tx, 2, 6)
        p.stairs(tx - 1, 2, 6, "wood_stairs", "west")
        p.stairs(tx + 1, 2, 6, "wood_stairs", "east")
    p.put(12, 2, 2, "minecraft:smoker", {"facing": "south", "lit": "true"})
    p.put(11, 2, 2, "minecraft:barrel", {"facing": "south", "open": "false"})
    for x in (9, 10):
        p.put(x, 2, 2, "minecraft:barrel", {"facing": "south", "open": "false"})
        p.put(x, 3, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    # a balcony over the street, banners and lanterns, a curled gable roof
    rail_line(p, (1, sz - 1), (sx - 2, sz - 1), 7)
    for x in range(1, sx - 1):
        p.slab(x, 6, sz - 1, "wood_slab", "top")
    beam_band(p, 1, 1, sx - 2, sz - 2, 11)
    gable(p, 1, 12, 1, sx - 2, sz - 2, along="x", overhang=1)
    plaque(p, 7, 5, sz - 2)
    wall_banner(p, 3, 5, sz - 1, "south")
    wall_banner(p, 11, 5, sz - 1, "south")
    for x in (4, 10):
        lit(p, x, 5, 4, chain=1)
    lit(p, 7, 10, 6, chain=1)
    anchor(p, 11.5, 2.0, 4.5, "kota/warga", 180.0)
    finish_lights(p, 7, 2, 9)
    front_door(p, sx, sz, 7)
    save(p, "penginapan")


def penginapan_2():
    """An inn around a stable yard: a two-storey wing, a stable with hay, a well and a lantern gate."""
    sx, sy, sz = 17, 18, 15
    p = piece(sx, sy, sz, seed=53058)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.air(0, 1, 0, sx - 1, sy - 1, sz - 1)
    # the main wing at the back
    p.floor(1, 1, 1, sx - 2, 6, role="wood_alt")
    p.walls(1, 2, 1, sx - 2, 5, 6, role="plaster", pillar="beam", windows=3, door=("south", 3))
    p.fill(1, 6, 1, sx - 2, 6, 6, "wood_alt")
    p.walls(1, 7, 1, sx - 2, 9, 6, role="plaster", pillar="beam", windows=3)
    p.staircase(2, 2, 5, "north", 4, role="wood_stairs", support="wood_alt")
    p.air(2, 6, 2, 2, 6, 5)
    for bx in (5, 9, 13):
        bed(p, bx, 7, 2, "north")
        p.put(bx + 1, 7, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    for tx in (6, 11):
        table(p, tx, 2, 4)
        p.stairs(tx - 1, 2, 4, "wood_stairs", "west")
        p.stairs(tx + 1, 2, 4, "wood_stairs", "east")
    p.put(14, 2, 2, "minecraft:smoker", {"facing": "south", "lit": "true"})
    beam_band(p, 1, 1, sx - 2, 6, 10)
    gable(p, 1, 11, 1, sx - 2, 6, along="x", overhang=1)
    # the stable on the east side of the yard
    p.floor(11, 1, 8, sx - 2, sz - 3, role="minecraft:hay_block", alt="ground_alt", alt_chance=0.5)
    for x, z in ((11, 8), (15, 8), (11, 12), (15, 12)):
        for y in range(1, 4):
            p.put(x, y, z, "log", {"axis": "y"})
    for x in range(12, 15):
        p.put(x, 1, 12, "wood_fence")
    for z in range(9, 12):
        p.put(15, 1, z, "wood_fence")
    p.fill(11, 4, 8, 15, 4, 12, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    p.put(12, 1, 9, "minecraft:composter", {"level": "4"})
    p.put(14, 1, 9, "minecraft:water_cauldron", {"level": "3"})
    # the yard: paving, a well, a cart, a tree
    for x in range(1, 11):
        for z in range(7, sz - 1):
            p.put(x, 0, z, "paving" if p.rng.random() < 0.7 else "paving_alt")
    p.ring(4, 1, 10, 1, "kerb_wall")
    p.put(4, 0, 10, "minecraft:water", {"level": "0"})
    p.put(4, 1, 10, "minecraft:air")
    p.put(8, 1, 9, "minecraft:hay_block", {"axis": "x"})
    p.put(9, 1, 9, "minecraft:barrel", {"facing": "up", "open": "false"})
    tree(p, 2, 1, 13, 6 if PAL()["tree"] in ("pine", "spruce") else 4)
    # the yard wall with a lantern gate
    for x in range(0, sx):
        if abs(x - 8) > 1:
            p.put(x, 1, sz - 1, "kerb_wall")
            p.put(x, 2, sz - 1, "kerb_wall") if x in (6, 10) else None
    for z in range(7, sz):
        p.put(0, 1, z, "kerb_wall")
    for x in (6, 10):
        p.put(x, 3, sz - 1, "glow", {"hanging": "false", "waterlogged": "false"} if p.b("glow").endswith("lantern") else None)
        note_light(p, x, 3, sz - 1)
    for x in (4, 12):
        lit(p, x, 5, 3, chain=1)
    lit(p, 8, 6, 7, chain=1, role="lamp")
    plaque(p, 8, 5, 6)
    snow_drift(p, 1, 7, 10, sz - 2, 1, 0.3)
    petals(p, 1, 7, 10, sz - 2, 1, 0.12)
    anchor(p, 13.5, 2.0, 3.5, "kota/warga", 180.0)
    anchor(p, 6.5, 1.0, 11.5, "kota/warga", 0.0)
    finish_lights(p, 8, 2, 8)
    front_door(p, sx, sz, 8)
    save(p, "penginapan_2")


def kantor_sekte():
    sx, sy, sz = 15, 26, 15
    p = piece(sx, sy, sz, seed=53056)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.fill(0, 1, 0, sx - 1, 1, sz - 1, "kerb")
    p.floor(2, 2, 2, sx - 3, sz - 3, role="paving", alt="kerb", alt_chance=0.3)
    p.walls(2, 3, 2, sx - 3, 8, sz - 3, role="plaster", pillar="red", windows=4, door=("south", 3))
    # the hall: the sect's altar, banners, weapon stands, a desk
    p.fill(5, 3, 3, 9, 3, 4, "paving")
    p.fill(6, 4, 3, 8, 4, 3, "accent")
    for x in (5, 9):
        wall_banner(p, x, 6, 3, "south")
    wall_banner(p, 7, 7, 3, "south", PAL()["banner_alt"])
    for z in (6, 9):
        for x in (4, 10):
            p.put(x, 3, z, "wood_fence")
            p.put(x, 4, z, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    table(p, 7, 3, 7)
    p.stairs(7, 3, 8, "wood_stairs", "south")
    p.staircase(6, 1, sz - 1, "north", 1, width=3, role="kerb_stairs", support=None)
    beam_band(p, 2, 2, sx - 3, sz - 3, 9)
    brackets(p, 2, 2, sx - 3, sz - 3, 9)
    hip(p, 1, 10, 1, sx - 2, sz - 2, overhang=1, tiers=2, tier_gap=2, tier_band=2, band_role="plaster", top="pearl")
    plaque(p, 7, 7, sz - 3)
    for x in (4, 10):
        lit(p, x, 8, 8, chain=1)
    post(p, 1, 2, sz - 2)
    post(p, sx - 2, 2, sz - 2)
    banner_pole(p, 3, 2, sz - 2, height=3, rotation=8)
    banner_pole(p, sx - 4, 2, sz - 2, height=3, rotation=8)
    anchor(p, 7.5, 4.0, 5.5, "kota/penjaga", 0.0)
    anchor(p, 4.5, 3.0, 10.5, "kota/penjaga", 90.0)
    finish_lights(p, 7, 3, 9)
    front_door(p, sx, sz, 7, y=1)
    save(p, "kantor_sekte")


def kantor_sekte_2():
    """The guard sect's office with a training yard: dummies, weapon racks, a drum, a sparring ring."""
    sx, sy, sz = 19, 22, 19
    p = piece(sx, sy, sz, seed=53059)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.air(0, 1, 0, sx - 1, sy - 1, sz - 1)
    p.fill(0, 1, 0, sx - 1, 1, 8, "kerb")
    p.floor(2, 1, 1, sx - 3, 7, role="paving", alt="kerb", alt_chance=0.3)
    p.walls(2, 2, 1, sx - 3, 6, 7, role="plaster", pillar="red", windows=4, door=("south", 3))
    for x in (5, 13):
        wall_banner(p, x, 5, 2, "south")
    p.fill(8, 2, 2, 10, 2, 2, "accent")
    table(p, 9, 2, 4)
    p.stairs(9, 2, 5, "wood_stairs", "south")
    beam_band(p, 2, 1, sx - 3, 7, 7)
    brackets(p, 2, 1, sx - 3, 7, 7)
    hip(p, 2, 8, 1, sx - 3, 7, overhang=1, top="pearl")
    for x in (8, 9, 10):
        p.stairs(x, 1, 9, "kerb_stairs", "north")
    # the training yard: a sand-and-gravel ring, wooden dummies, weapon racks, a drum, banners
    for x in range(1, sx - 1):
        for z in range(9, sz - 1):
            d = math.hypot(x - 9, z - 13)
            p.put(x, 0, z, "minecraft:coarse_dirt" if d <= 3.5 else "paving" if p.rng.random() < 0.7 else "paving_alt")
    p.ring(9, 0, 13, 4, "kerb")
    for x, z in ((3, 11), (3, 15), (15, 11)):
        p.put(x, 1, z, "wood_fence")
        p.put(x, 2, z, "minecraft:hay_block", {"axis": "y"})
        p.put(x, 3, z, "minecraft:target", {"power": "0"})
    for z in range(10, 16, 2):
        p.put(sx - 2, 1, z, "wood_fence")
        p.put(sx - 2, 2, z, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    p.put(15, 1, 15, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(15, 2, 15, "minecraft:note_block", {"instrument": "basedrum", "note": "0", "powered": "false"})
    for x, rot in ((1, 4), (sx - 2, 12)):
        banner_pole(p, x, 1, 9, height=4, rotation=rot)
    for x in range(0, sx):
        if abs(x - 9) > 1:
            p.put(x, 1, sz - 1, "kerb_wall")
    for z in range(9, sz):
        p.put(0, 1, z, "kerb_wall")
        p.put(sx - 1, 1, z, "kerb_wall")
    for x in (7, 11):
        p.put(x, 2, sz - 1, "kerb_wall")
        p.put(x, 3, sz - 1, "glow", {"hanging": "false", "waterlogged": "false"} if p.b("glow").endswith("lantern") else None)
        note_light(p, x, 3, sz - 1)
    for x in (5, 13):
        lit(p, x, 6, 4, chain=1)
    snow_drift(p, 1, 9, sx - 2, sz - 2, 1, 0.2)
    anchor(p, 9.5, 2.0, 3.5, "kota/penjaga", 0.0)
    anchor(p, 9.5, 1.0, 12.5, "kota/penjaga", 180.0)
    finish_lights(p, 9, 2, 9)
    front_door(p, sx, sz, 9, y=0)
    save(p, "kantor_sekte_2")


# ======================================================================================================== houses and decorations
def furnish(p, sx, sz, y, variant, bedroom=True):
    """Furniture for a small house interior (walls at 1..sx-2 / 1..sz-2)."""
    p.put(2, y, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    if sx >= 9 and bedroom:
        bed(p, sx - 3, y, 3, "north")
        p.put(2, y, 3, "minecraft:crafting_table")
        table(p, sx // 2, y, sz // 2)
        chair(p, sx // 2 - 1, y, sz // 2, "east")
    else:
        p.put(sx - 3, y, 2, "minecraft:composter", {"level": "0"})
        p.put(2, y, sz - 3, "rug")
    if variant == 1:
        p.put(sx // 2, y, 2, "minecraft:loom", {"facing": "south"})
    elif variant == 2:
        p.put(sx // 2, y, 2, "minecraft:cartography_table")
    else:
        shelf(p, sx // 2, y + 1, 2, "south")
        p.put(sx // 2, y, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})


def rumah(name, sx, sz, h, seed, variant):
    p = piece(sx, h + 11, sz, seed=seed)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="wood_alt", alt="minecraft:oak_planks" if CUR["v"] == "sedang" else "wood", alt_chance=0.2)
    wall = ["plaster", "minecraft:mud_bricks" if CUR["v"] != "salju" else "wall", "wall"][variant % 3]
    p.walls(1, 2, 1, sx - 2, 1 + h, sz - 2, role=wall, pillar="beam", windows=3, door=("south", 1))
    furnish(p, sx, sz, 2, variant)
    # a small garden in front, a bench and a pot at the door
    for x in range(0, sx):
        if x != sx // 2:
            p.put(x, 0, sz - 1, "ground", {"snowy": "false"} if p.b("ground").endswith("grass_block") else None)
            r = p.rng.random()
            if r < 0.35:
                small_flower(p, x, 1, sz - 1, "flower" if r < 0.2 else "flower_alt")
            elif r < 0.5:
                p.put(x, 1, sz - 1, "minecraft:short_grass" if CUR["v"] != "salju" else "minecraft:snow", None if CUR["v"] != "salju" else {"layers": "1"})
    p.put(sx // 2 - 1 if sx // 2 - 1 > 0 else 1, 1, sz - 1, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    top = 2 + h
    beam_band(p, 1, 1, sx - 2, sz - 2, top)
    if variant == 0:
        gable(p, 1, top + 1, 1, sx - 2, sz - 2, along="x", overhang=1, pearl=sx >= 9)
    elif variant == 1:
        hip(p, 1, top + 1, 1, sx - 2, sz - 2, overhang=1, top="rod")
    else:
        gable(p, 1, top + 1, 1, sx - 2, sz - 2, along="z", overhang=1, pearl=sx >= 9)
    if sx >= 9:
        brackets(p, 1, 1, sx - 2, sz - 2, top, every=3)
    lit(p, sx // 2, 1 + h, sz // 2, chain=1)
    if variant != 2:
        lit(p, sx // 2 + 1, top + 1, sz - 1, chain=1, role="lamp")
    else:
        post(p, 0, 1, sz - 1, height=1)
    if variant == 1 and sx >= 9:
        wall_banner(p, sx // 2 - 1, top - 1, sz - 1, "south")
    anchor(p, sx / 2.0, 2.0, sz / 2.0, "kota/warga", 180.0)
    finish_lights(p, sx // 2, 2, sz // 2)
    front_door(p, sx, sz, sx // 2, y=1)
    save(p, name)


def rumah_halaman(name, seed, variant):
    """A courtyard house (siheyuan): a gate house, two side wings, the main hall, a tree and a well in the yard."""
    sx, sy, sz = 15, 14, 15
    p = piece(sx, sy, sz, seed=seed)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.air(0, 1, 0, sx - 1, sy - 1, sz - 1)
    for x in range(1, sx - 1):
        for z in range(1, sz - 1):
            p.put(x, 1, z, "paving" if p.rng.random() < 0.75 else "paving_alt")
    # outer wall with a gate in the south, tiled coping
    for x in range(0, sx):
        for z in (0, sz - 1):
            for y in (2, 3):
                p.put(x, y, z, "plaster")
            p.put(x, 4, z, "roof_slab", {"type": "bottom", "waterlogged": "false"})
    for z in range(0, sz):
        for x in (0, sx - 1):
            for y in (2, 3):
                p.put(x, y, z, "plaster")
            p.put(x, 4, z, "roof_slab", {"type": "bottom", "waterlogged": "false"})
    for y in (2, 3, 4):
        p.put(7, y, sz - 1, "minecraft:air")
        p.put(6, y, sz - 1, "red" if y < 4 else "minecraft:air")
        p.put(8, y, sz - 1, "red" if y < 4 else "minecraft:air")
    gable(p, 5, 5, sz - 2, 9, sz - 1, along="x", overhang=0, pearl=False)
    # the main hall (north)
    p.walls(2, 2, 1, sx - 3, 5, 5, role="plaster" if variant != 1 else "wall", pillar="red", windows=3, window_role="window", door=("south", 1))
    beam_band(p, 2, 1, sx - 3, 5, 6)
    gable(p, 2, 7, 1, sx - 3, 5, along="x", overhang=1)
    bed(p, 4, 2, 3, "north")
    table(p, 9, 2, 3)
    chair(p, 8, 2, 3, "east")
    shelf(p, 11, 3, 2, "south")
    p.put(11, 2, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    # the side wings (east and west)
    for x0 in (1, sx - 5):
        p.walls(x0, 2, 7, x0 + 3, 4, 11, role="wall" if variant != 2 else "plaster", pillar="beam", windows=2, window_role="window",
                door=("east" if x0 == 1 else "west", 1))
        gable(p, x0, 5, 7, x0 + 3, 11, along="z", overhang=1, pearl=False)
    p.put(2, 2, 8, "minecraft:crafting_table")
    p.put(2, 2, 10, "minecraft:loom", {"facing": "east"}) if variant == 0 else p.put(2, 2, 10, "minecraft:barrel", {"facing": "up", "open": "false"})
    bed(p, sx - 3, 2, 8, "north")
    # the yard: a tree, a little well, potted plants, a rock
    p.put(7, 1, 9, "ground_alt", {"snowy": "false"})
    if PAL()["tree"] == "cherry" or variant == 2:
        cherry(p, 7, 2, 9, 4, 2)
    elif PAL()["tree"] == "acacia":
        acacia(p, 7, 2, 9, 5)
    else:
        p.put(7, 2, 9, "bush", {"persistent": "true", "distance": "1", "waterlogged": "false"} if p.b("bush").endswith("leaves") else None)
        p.put(6, 2, 8, "minecraft:potted_azalea_bush") if variant == 0 else None
    p.put(9, 2, 11, "kerb_wall")
    p.put(9, 3, 11, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    note_light(p, 9, 3, 11)
    p.put(5, 2, 11, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
    p.put(9, 2, 6, p.b("potted"))
    snow_drift(p, 1, 6, sx - 2, sz - 2, 2, 0.4)
    petals(p, 1, 6, sx - 2, sz - 2, 2, 0.25)
    lit(p, 7, 5, sz - 2, chain=1, role="lamp")
    lit(p, 7, 6, 6, chain=1, role="lamp")
    anchor(p, 7.5, 2.0, 7.5, "kota/warga", 180.0)
    anchor(p, 4.5, 2.0, 3.5, "kota/warga", 180.0)
    finish_lights(p, 7, 2, 7)
    front_door(p, sx, sz, 7, y=1)
    save(p, name)


def rumah_toko(name, seed, variant):
    """A two-storey shophouse: an open shop front with a counter and goods, living rooms above, a hanging sign."""
    sx, sy, sz = 9, 19, 11
    p = piece(sx, sy, sz, seed=seed)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.air(0, 1, 0, sx - 1, sy - 1, sz - 1)
    p.floor(1, 1, 1, sx - 2, sz - 2, role="wood_alt", alt="wood", alt_chance=0.3)
    p.walls(1, 2, 1, sx - 2, 5, sz - 3, role="plaster", pillar="beam", windows=3)
    # the open shop front: shutters folded up, a counter, goods by kind
    p.air(2, 2, sz - 3, sx - 3, 4, sz - 3)
    for x in range(2, sx - 2):
        p.put(x, 5, sz - 2, "wood_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    for x in range(2, sx - 2):
        if x != sx // 2:
            p.put(x, 2, sz - 4, "wood")
    goods = [("minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"}),
             ("minecraft:flower_pot", None), ("minecraft:white_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})]
    if variant == 1:
        goods = [("minecraft:melon", None), ("minecraft:pumpkin", {}), ("minecraft:hay_block", {"axis": "y"})]
    elif variant == 2:
        goods = [("minecraft:bookshelf", None), ("minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"}),
                 ("minecraft:cartography_table", None)]
    for i, x in enumerate((2, 3, 5, 6)):
        if x < sx - 2:
            bid, props = goods[i % len(goods)]
            if bid in ("minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block", "minecraft:bookshelf", "minecraft:cartography_table",
                       "minecraft:lectern"):
                p.put(x, 2, 2, bid, props or None)
            else:
                p.put(x, 3, sz - 4, bid, props) if x != sx // 2 else None
    for x in range(2, sx - 2):
        shelf(p, x, 3, 2, "south") if variant != 2 else None
    # upstairs
    p.fill(1, 6, 1, sx - 2, 6, sz - 2, "wood")
    p.walls(1, 7, 1, sx - 2, 9, sz - 2, role="plaster", pillar="beam", windows=3)
    p.staircase(2, 2, 6, "north", 5, role="wood_stairs", support="wood")
    p.air(2, 6, 3, 2, 6, 6)
    bed(p, sx - 3, 7, 3, "north")
    p.put(4, 7, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(sx - 3, 7, sz - 3, "rug")
    # a balcony rail on the upper storey, the hanging shop sign, lanterns
    rail_line(p, (1, sz - 1), (sx - 2, sz - 1), 7)
    for x in range(1, sx - 1):
        p.slab(x, 6, sz - 1, "wood_slab", "top")
    beam_band(p, 1, 1, sx - 2, sz - 2, 10)
    gable(p, 1, 11, 1, sx - 2, sz - 2, along="x" if variant != 1 else "z", overhang=1, pearl=False)
    lit(p, 0, 10, sz - 1, chain=2, role="lamp")
    lit(p, sx - 1, 10, sz - 1, chain=2, role="lamp")
    lit(p, 4, 5, 5, chain=1)
    anchor(p, 4.5, 2.0, 6.5, "kota/warga", 180.0)
    finish_lights(p, 4, 2, 5)
    front_door(p, sx, sz, sx // 2, y=1)
    save(p, name)


def hias(name, seed, kind):
    p = piece(7, 9, 7, seed=seed)
    for x in range(7):
        for z in range(7):
            p.put(x, 0, z, "paving" if p.rng.random() < 0.7 else "paving_alt")
            for y in range(1, 8):
                p.put(x, y, z, "minecraft:air")
    if kind == "kios":
        for x in (1, 5):
            for z in (1, 4):
                p.put(x, 1, z, "wood_fence")
                p.put(x, 2, z, "wood_fence")
        awning(p, 1, 5, 1, 4, 3, "cloth_alt", "cloth")
        p.fill(2, 1, 2, 4, 1, 2, "wood_slab", {"type": "top", "waterlogged": "false"})
        p.put(2, 2, 2, "minecraft:melon")
        p.put(4, 2, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
        p.put(3, 2, 2, "minecraft:candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
        anchor(p, 3.5, 1.0, 3.5, "kota/warga", 180.0)
    elif kind == "gerobak":
        p.put(2, 1, 3, "minecraft:hay_block", {"axis": "x"})
        p.put(3, 1, 3, "minecraft:hay_block", {"axis": "x"})
        p.put(4, 1, 3, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(3, 2, 3, "minecraft:hay_block", {"axis": "x"})
        p.put(1, 1, 3, "wood_fence")
        for x, f in ((2, "north"), (4, "north")):
            p.put(x, 1, 2, "wood_trapdoor", {"facing": f, "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    elif kind == "sumur":
        p.ring(3, 1, 3, 1, "kerb_wall")
        p.put(3, 0, 3, "minecraft:water", {"level": "0"})
        p.put(3, 1, 3, "minecraft:air")
        for x in (2, 4):
            for y in (2, 3):
                p.put(x, y, 3, "wood_fence")
        p.put(3, 4, 3, "beam", {"axis": "x"})
        p.put(2, 4, 3, "wood_fence")
        p.put(4, 4, 3, "wood_fence")
        p.put(3, 3, 3, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        gable(p, 2, 5, 2, 4, 4, along="x", overhang=0, pearl=False)
        p.put(5, 1, 2, "minecraft:barrel", {"facing": "up", "open": "true"})
    elif kind == "kuil":
        p.put(3, 1, 3, "kerb")
        p.put(3, 2, 3, "kerb_chisel")
        p.put(3, 3, 3, "minecraft:candle", {"candles": "4", "lit": "true", "waterlogged": "false"})
        for x, z in ((2, 2), (4, 2), (2, 4), (4, 4)):
            for y in (1, 2, 3):
                p.put(x, y, z, "red_wall")
        hip(p, 2, 4, 2, 4, 4, overhang=1, top="pearl")
        p.put(3, 1, 4, "minecraft:red_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
        p.put(2, 1, 5, "minecraft:flower_pot")
    elif kind == "batu":
        # a rock garden: raked gravel, standing stones, moss, a pine bonsai
        for x in range(1, 6):
            for z in range(1, 6):
                p.put(x, 0, z, "minecraft:gravel")
        for x, z, h in ((2, 2, 2), (4, 3, 1), (2, 4, 1)):
            for y in range(1, h + 1):
                p.put(x, y, z, "kerb_alt" if y == 1 else "minecraft:cobblestone" if CUR["v"] != "sabana" else "kerb")
        p.put(4, 1, 2, "minecraft:moss_carpet")
        p.put(3, 1, 4, "minecraft:moss_carpet")
        p.put(4, 1, 5, p.b("potted"))
        stone_lantern(p, 5, 1, 1)
    elif kind == "latihan":
        # a training corner: two dummies and a weapon rack
        for x in (2, 4):
            p.put(x, 1, 3, "wood_fence")
            p.put(x, 2, 3, "minecraft:hay_block", {"axis": "y"})
            p.put(x, 3, 3, "minecraft:carved_pumpkin", {"facing": "south"})
        for x in range(1, 6):
            p.put(x, 1, 1, "wood_fence")
        for x in (1, 3, 5):
            p.put(x, 2, 1, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    elif kind == "pohon":
        p.disc(3, 0, 3, 1, "ground_alt")
        tree(p, 3, 1, 3, 7 if PAL()["tree"] in ("pine", "spruce") else 4)
        p.ring(3, 1, 3, 2, "kerb_slab")
    else:
        for x in (1, 3, 5):
            post(p, x, 1, 3, height=3)
        for x in range(1, 6):
            if x not in (1, 3, 5):
                p.put(x, 4, 3, "wood_fence")
    snow_drift(p, 0, 0, 6, 6, 1, 0.25)
    petals(p, 0, 0, 6, 6, 1, 0.15)
    finish_lights(p, 3, 1, 3)
    front_door(p, 7, 7, 3)
    save(p, name)


# ======================================================================================================== Kota Abadi
DECK = 8


def apiece(sx, sy, sz, seed):
    return Piece(sx, sy, sz, seed=seed, palette=ABADI)


def underside(p, x0, z0, x1, z1, depth=DECK):
    """The floating district's stone underside: layers shrinking toward a few hanging tips, amethyst glints, chains."""
    cx, cz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
    rx, rz = (x1 - x0) / 2.0 + 0.5, (z1 - z0) / 2.0 + 0.5
    for y in range(depth - 1, -1, -1):
        shrink = (depth - 1 - y) / float(depth)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                nx = (x - cx) / max(0.5, rx * (1.0 - shrink * 0.85))
                nz = (z - cz) / max(0.5, rz * (1.0 - shrink * 0.85))
                if nx * nx + nz * nz <= 1.0 + 0.15 * p.rng.random():
                    role = "under" if p.rng.random() < 0.7 else "under_alt"
                    if y == 0:
                        role = "under_tip"
                    p.put(x, y, z, role)
    # amethyst glints hanging from the lowest stones
    for (x, y, z), cell in list(p.cells.items()):
        if y <= 2 and cell[0][0] in ("minecraft:calcite", "minecraft:white_concrete") and p.rng.random() < 0.08:
            if y - 1 >= 0 and p.get(x, y - 1, z) is None:
                p.put(x, y - 1, z, "minecraft:amethyst_cluster", {"facing": "down", "waterlogged": "false"})


def abadi_deck(p, x0, z0, x1, z1, gaps=()):
    """The white-jade deck with a continuous cherry rail along its edge (open where a connector meets it)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            p.put(x, DECK, z, "minecraft:quartz_bricks" if edge else ("minecraft:smooth_quartz" if (x + z) % 2 else "minecraft:calcite"))
            for y in range(DECK + 1, min(p.size[1], DECK + 6)):
                p.put(x, y, z, "minecraft:air")
    gapset = set()
    for gx, gz in gaps:
        for d in (-1, 0, 1):
            gapset.add((gx + d, gz))
            gapset.add((gx, gz + d))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and (x, z) not in gapset:
                corner = x in (x0, x1) and z in (z0, z1)
                p.put(x, DECK + 1, z, "minecraft:quartz_pillar" if corner else "rail", {"axis": "y"} if corner else None)
                if corner and p.inside(x, DECK + 2, z):
                    p.put(x, DECK + 2, z, "glow")
                    note_light(p, x, DECK + 2, z)
    # pearl lanterns on chains under the deck rim
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        if p.get(x, DECK - 1, z) is None and DECK - 3 >= 0:
            p.put(x, DECK - 1, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
            p.put(x, DECK - 2, z, "lamp", {"axis": "y"})


def clouds(p, x0, z0, x1, z1, y, count=8):
    pts = []
    for i in range(count):
        a = 2.0 * math.pi * i / count
        cx = (x0 + x1) / 2.0 + math.cos(a) * (x1 - x0) / 2.0
        cz = (z0 + z1) / 2.0 + math.sin(a) * (z1 - z0) / 2.0
        pts += [int(cx), y, int(cz)]
    return pts


def jade_lion(p, x, y, z, facing):
    p.put(x, y, z, "minecraft:quartz_bricks")
    p.put(x, y + 1, z, "minecraft:chiseled_quartz_block")
    p.stairs(x, y + 2, z, "minecraft:quartz_stairs", OPP[facing])


def abadi_gerbang():
    sx, sy, sz = 23, 26, 15
    p = apiece(sx, sy, sz, 66001)
    underside(p, 0, 0, sx - 1, sz - 1)
    abadi_deck(p, 0, 0, sx - 1, sz - 1, gaps=((11, 0), (11, sz - 1)))
    # the cloud gate: white jade pillars with gold rings, gold beams, a three-tier roof with a pearl
    for x in (6, 16):
        p.pillar(x, DECK + 1, DECK + 9, 7, role="minecraft:quartz_pillar", base="trim", cap="trim")
        p.put(x, DECK + 5, 7, "trim")
        for dz in (-1, 1):
            p.stairs(x, DECK + 1, 7 + dz, "minecraft:quartz_stairs", "south" if dz < 0 else "north")
    for x in range(5, 18):
        p.put(x, DECK + 10, 7, "trim")
    for x in range(7, 16):
        p.put(x, DECK + 9, 7, "minecraft:white_concrete" if x % 2 else "jade_tile")
    hip(p, 5, DECK + 11, 6, 17, 8, overhang=1, tiers=3, tier_gap=1, tier_band=1, band_role="minecraft:quartz_bricks", top="pearl")
    for x in range(7, 16):
        for y in range(DECK + 1, DECK + 9):
            p.put(x, y, 7, "minecraft:air")
    for x in (8, 14):
        wall_banner(p, x, DECK + 8, 6, "north", "white")
        wall_banner(p, x, DECK + 8, 8, "south", "yellow")
    for x in (7, 15):
        lit(p, x, DECK + 9, 6, chain=1, role="lamp")
        lit(p, x, DECK + 9, 8, chain=1, role="lamp")
    for x in (3, 19):
        post(p, x, DECK + 1, 3, height=3, post_role="rail")
        post(p, x, DECK + 1, 11, height=3, post_role="rail")
    jade_lion(p, 9, DECK + 1, 11, "south")
    jade_lion(p, 13, DECK + 1, 11, "south")
    anchor(p, 11.5, DECK + 1.0, 11.5, "kota/abadi_gerbang", 0.0, situs="kota/kota_abadi", tier=8, protect=72)
    anchor(p, 4.5, DECK + 1.0, 9.5, "kota/abadi_warga", 90.0)
    anchor(p, 11.5, float(DECK - 1), 7.5, "kota/abadi_awan", 0.0, protect=0, pts=[p_ - c for p_, c in zip(clouds(p, 0, 0, sx - 1, sz - 1, DECK - 1),
                                                                                                         [11, DECK - 1, 7] * 8)])
    finish_lights(p, 11, DECK + 1, 4)
    conn(p, 11, DECK, 0, "north", "kota/abadi_keluar", "kota/abadi_masuk", "kota/abadi_jembatan", "minecraft:quartz_bricks", prio=2)
    save(p, "abadi_gerbang")


def abadi_jembatan(name, seed, length, next_pool, houses=False):
    sx, sz = 7, length
    p = apiece(sx, DECK + 7, sz, seed)
    side_z = (5, length - 6) if houses else ()
    for z in range(sz):
        for x in range(1, sx - 1):
            p.put(x, DECK, z, "jade_tile" if x == 3 else "minecraft:quartz_bricks")
            p.put(x, DECK - 1, z, "under")
            if 2 <= x <= 4:
                p.put(x, DECK - 2, z, "under_alt")
            if x == 3:
                p.put(x, DECK - 3, z, "under_tip")
            for y in range(DECK + 1, DECK + 6):
                p.put(x, y, z, "minecraft:air")
        for x in (1, sx - 2):
            if z not in side_z:
                p.put(x, DECK + 1, z, "rail")
    for z in side_z:
        for x in (0, sx - 1):
            p.put(x, DECK, z, "minecraft:quartz_bricks")
            p.put(x, DECK - 1, z, "under")
    for z in range(2, sz - 1, 5):
        if z not in side_z and z - 1 not in side_z and z + 1 not in side_z:
            post(p, 1, DECK + 1, z, height=2, post_role="rail")
            post(p, sx - 2, DECK + 1, z, height=2, post_role="rail")
    finish_lights(p, 3, DECK + 1, sz // 2)
    conn(p, 3, DECK, sz - 1, "south", "kota/abadi_masuk", "kota/abadi_keluar", "minecraft:empty", "minecraft:quartz_bricks")
    conn(p, 3, DECK, 0, "north", "kota/abadi_keluar", "kota/abadi_masuk", next_pool, "minecraft:quartz_bricks", prio=1)
    for z in side_z:
        conn(p, 0, DECK, z, "west", "kota/abadi_sisi", "kota/abadi_pintu", "kota/abadi_rumah", "minecraft:quartz_bricks")
        conn(p, sx - 1, DECK, z, "east", "kota/abadi_sisi", "kota/abadi_pintu", "kota/abadi_rumah", "minecraft:quartz_bricks")
    save(p, name)


def abadi_alun():
    s = 33
    p = apiece(s, DECK + 16, s, 66010)
    underside(p, 0, 0, s - 1, s - 1)
    abadi_deck(p, 0, 0, s - 1, s - 1, gaps=((16, s - 1), (16, 0), (s - 1, 10), (0, 10), (s - 1, 24), (0, 24), (3, 0)))
    # a jade mosaic of the eight trigrams around a lotus fountain
    p.ring(16, DECK, 16, 6, "jade_tile", thickness=1)
    p.ring(16, DECK, 16, 9, "minecraft:gold_block", thickness=1)
    for a in range(8):
        ang = a * math.pi / 4.0
        for r in (7, 8):
            x = int(round(16 + math.cos(ang) * r))
            z = int(round(16 + math.sin(ang) * r))
            p.put(x, DECK, z, "minecraft:prismarine" if a % 2 else "minecraft:dark_prismarine")
    p.disc(16, DECK, 16, 2, "minecraft:water")
    p.ring(16, DECK + 1, 16, 3, "minecraft:quartz_slab")
    p.put(16, DECK, 16, "minecraft:pearlescent_froglight", {"axis": "y"})
    for x, z in ((15, 16), (17, 16), (16, 15)):
        p.put(x, DECK + 1, z, "minecraft:lily_pad")
    for a in range(8):
        ang = a * math.pi / 4.0
        x = int(round(16 + math.cos(ang) * 12))
        z = int(round(16 + math.sin(ang) * 12))
        p.put(x, DECK + 1, z, "minecraft:quartz_pillar", {"axis": "y"})
        p.put(x, DECK + 2, z, "minecraft:quartz_pillar", {"axis": "y"})
        post(p, x, DECK + 3, z, height=0, post_role="rail")
    for x, z in ((5, 5), (27, 5), (5, 27), (27, 27)):
        cherry(p, x, DECK + 1, z, height=5, r=3)
    # the incense ding before the palace and two white jade huabiao
    p.put(16, DECK + 1, 8, "minecraft:chiseled_quartz_block")
    p.put(16, DECK + 2, 8, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    p.put(16, DECK + 3, 8, "minecraft:cauldron")
    for x in (12, 20):
        p.put(x, DECK + 1, 3, "minecraft:quartz_bricks")
        for y in range(2, 7):
            p.put(x, DECK + y, 3, "minecraft:quartz_pillar", {"axis": "y"})
        p.put(x, DECK + 7, 3, "minecraft:chiseled_quartz_block")
        p.put(x, DECK + 8, 3, "trim")
    anchor(p, 16.5, DECK + 1.0, 21.5, "kota/abadi_warga", 180.0)
    anchor(p, 12.5, DECK + 1.0, 10.5, "kota/abadi_warga", 90.0)
    anchor(p, 16.5, float(DECK - 1), 16.5, "kota/abadi_awan", 0.0, protect=0,
           pts=[v - c for v, c in zip(clouds(p, 0, 0, s - 1, s - 1, DECK - 2, count=12), [16, DECK - 1, 16] * 12)])
    anchor(p, 16.5, DECK + 3.0, 16.5, "kota/abadi_kebun", 0.0, protect=0, pts=[-11, 2, -11, 11, 2, -11, -11, 2, 11, 11, 2, 11])
    finish_lights(p, 16, DECK + 1, 12)
    conn(p, 16, DECK, s - 1, "south", "kota/abadi_masuk", "kota/abadi_keluar", "minecraft:empty", "minecraft:quartz_bricks")
    conn(p, 16, DECK, 0, "north", "kota/abadi_sisi", "kota/abadi_pintu", "kota/abadi_istana", "minecraft:quartz_bricks", prio=3)
    conn(p, s - 1, DECK, 10, "east", "kota/abadi_sisi", "kota/abadi_pintu", "kota/abadi_pasar", "minecraft:quartz_bricks", prio=3)
    conn(p, 0, DECK, 10, "west", "kota/abadi_sisi", "kota/abadi_pintu", "kota/abadi_dao", "minecraft:quartz_bricks", prio=3)
    conn(p, s - 1, DECK, 24, "east", "kota/abadi_keluar", "kota/abadi_masuk", "kota/abadi_jembatan_kebun", "minecraft:quartz_bricks", prio=2)
    conn(p, 0, DECK, 24, "west", "kota/abadi_keluar", "kota/abadi_masuk", "kota/abadi_jembatan_lonceng", "minecraft:quartz_bricks", prio=2)
    conn(p, 3, DECK, 0, "north", "kota/abadi_keluar", "kota/abadi_masuk", "kota/abadi_jembatan_lelang", "minecraft:quartz_bricks", prio=2)
    save(p, "abadi_alun")


def abadi_hall(name, sx, sz, seed, build, door_y=DECK, extra_h=16):
    p = apiece(sx, DECK + extra_h, sz, seed)
    underside(p, 0, 0, sx - 1, sz - 1)
    abadi_deck(p, 0, 0, sx - 1, sz - 1, gaps=((sx // 2, sz - 1),))
    build(p, sx, sz)
    conn(p, sx // 2, door_y, sz - 1, "south", "kota/abadi_pintu", "minecraft:empty", "minecraft:empty", "minecraft:quartz_bricks")
    save(p, name)


def build_istana(p, sx, sz):
    p.fill(3, DECK + 1, 3, sx - 4, DECK + 1, sz - 4, "minecraft:quartz_bricks")
    p.walls(4, DECK + 2, 3, sx - 5, DECK + 8, sz - 5, role="plaster", pillar="minecraft:quartz_pillar", windows=4,
            window_role="window", door=("south", 3))
    p.staircase(sx // 2 - 2, DECK + 1, sz - 3, "north", 1, width=5, role="minecraft:quartz_stairs", support=None)
    # the throne dais: gold, jade, a screen of yellow banners
    p.fill(sx // 2 - 3, DECK + 2, 4, sx // 2 + 3, DECK + 2, 6, "minecraft:gold_block")
    p.put(sx // 2, DECK + 3, 5, "minecraft:emerald_block")
    for x in (sx // 2 - 2, sx // 2 + 2):
        wall_banner(p, x, DECK + 6, 4, "south", "yellow")
    for x in range(6, sx - 6, 3):
        for z in (8, sz - 8):
            p.pillar(x, DECK + 2, DECK + 7, z, role="minecraft:quartz_pillar", base="trim", cap="trim")
    for x in (7, sx - 8):
        p.put(x, DECK + 2, 6, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    beam_band(p, 4, 3, sx - 5, sz - 5, DECK + 9, role="trim")
    hip(p, 3, DECK + 10, 2, sx - 4, sz - 4, overhang=1, tiers=3, tier_gap=2, tier_band=2, band_role="plaster", top="pearl")
    for x in (7, sx // 2, sx - 8):
        lit(p, x, DECK + 8, sz // 2, chain=1, role="lamp")
    for x in (sx // 2 - 3, sx // 2 + 3):
        jade_lion(p, x, DECK + 1, sz - 3, "south")
    anchor(p, sx / 2.0, DECK + 3.0, 7.5, "kota/abadi_utusan", 0.0)
    anchor(p, 6.5, DECK + 2.0, sz - 7.5, "kota/abadi_warga", 90.0)
    finish_lights(p, sx // 2, DECK + 2, sz // 2)


def build_pasar(p, sx, sz):
    for i, (x0, z0) in enumerate(((2, 2), (12, 2), (2, 9), (12, 9))):
        p.fill(x0, DECK + 1, z0, x0 + 6, DECK + 1, z0 + 2, "minecraft:cherry_slab", {"type": "top", "waterlogged": "false"})
        for x, z in ((x0, z0), (x0 + 6, z0), (x0, z0 + 3), (x0 + 6, z0 + 3)):
            for y in range(DECK + 1, DECK + 4):
                p.put(x, y, z, "rail")
        for x in range(x0, x0 + 7):
            for z in range(z0, z0 + 4):
                p.put(x, DECK + 4, z, ["minecraft:pink_wool", "minecraft:white_wool"][(x + z) % 2] if i % 2 == 0 else
                      ["minecraft:yellow_wool", "minecraft:white_wool"][(x + z) % 2])
        goods = ["minecraft:amethyst_cluster", "minecraft:decorated_pot", "minecraft:potted_cherry_sapling", "minecraft:candle"]
        for k, gx in enumerate(range(x0 + 1, x0 + 6, 2)):
            g = goods[(i + k) % len(goods)]
            props = {"facing": "up", "waterlogged": "false"} if g.endswith("cluster") else \
                {"facing": "south", "cracked": "false", "waterlogged": "false"} if g.endswith("decorated_pot") else \
                {"candles": "3", "lit": "true", "waterlogged": "false"} if g.endswith("candle") else None
            p.put(gx, DECK + 2, z0 + 1, g, props)
        lit(p, x0 + 3, DECK + 4, z0 + 2, chain=0)
    anchor(p, sx / 2.0, DECK + 1.0, sz / 2.0, "kota/abadi_pasar", 180.0)
    anchor(p, 4.5, DECK + 1.0, sz - 3.5, "kota/abadi_warga", 0.0)
    finish_lights(p, sx // 2, DECK + 1, 3)


def build_dao(p, sx, sz):
    p.disc(sx // 2, DECK, sz // 2, 7, "jade_tile")
    p.ring(sx // 2, DECK, sz // 2, 7, "trim")
    p.disc(sx // 2, DECK, sz // 2, 1, "minecraft:gold_block")
    for a in range(8):
        ang = a * math.pi / 4.0
        x = int(round(sx // 2 + math.cos(ang) * 7))
        z = int(round(sz // 2 + math.sin(ang) * 7))
        p.pillar(x, DECK + 1, DECK + 6, z, role="minecraft:quartz_pillar", base="trim", cap="trim")
    for a in range(12):
        ang = a * math.pi / 6.0
        x = int(round(sx // 2 + math.cos(ang) * 4))
        z = int(round(sz // 2 + math.sin(ang) * 4))
        p.put(x, DECK + 1, z, "minecraft:white_carpet" if a % 2 else "minecraft:pink_carpet")
    p.put(sx // 2, DECK + 1, sz // 2 - 2, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    for x in (sx // 2 - 2, sx // 2 + 2):
        p.put(x, DECK + 1, sz // 2 - 3, "minecraft:bookshelf")
        p.put(x, DECK + 2, sz // 2 - 3, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    hip(p, sx // 2 - 8, DECK + 7, sz // 2 - 8, sx // 2 + 8, sz // 2 + 8, overhang=0, tiers=2, tier_gap=2, tier_band=1, band_role="plaster",
        top="pearl")
    lit(p, sx // 2, DECK + 7, sz // 2, chain=2, role="lamp")
    anchor(p, sx / 2.0, DECK + 1.0, sz / 2.0 - 1.0, "kota/abadi_penceramah", 0.0)
    finish_lights(p, sx // 2, DECK + 1, sz // 2)


def build_kebun(p, sx, sz):
    for x in range(1, sx - 1):
        for z in range(1, sz - 1):
            p.put(x, DECK, z, "minecraft:grass_block", {"snowy": "false"})
    p.path([(10, sz - 1), (10, 3)], DECK, width=1, role="minecraft:quartz_bricks", alt="minecraft:smooth_quartz", alt_chance=0.3)
    p.path([(3, 11), (17, 11)], DECK, width=1, role="minecraft:quartz_bricks", alt="minecraft:smooth_quartz", alt_chance=0.3)
    for x, z in ((4, 4), (16, 4), (4, 17), (16, 17), (4, 8), (16, 8), (4, 14), (16, 14)):
        cherry(p, x, DECK + 1, z, height=4 + (x + z) % 2, r=2)
    p.disc(10, DECK, 11, 2, "minecraft:water")
    p.put(10, DECK + 1, 11, "minecraft:lily_pad")
    # a little pavilion over the spring
    for x, z in ((8, 9), (12, 9), (8, 13), (12, 13)):
        for y in range(DECK + 1, DECK + 4):
            p.put(x, y, z, "minecraft:quartz_pillar", {"axis": "y"})
    hip(p, 8, DECK + 4, 9, 12, 13, overhang=1, top="pearl")
    lit(p, 10, DECK + 4, 11, chain=1, role="lamp")
    for x in range(2, sx - 2, 3):
        p.put(x, DECK + 1, 2, "minecraft:flowering_azalea")
    anchor(p, 10.5, DECK + 1.0, 15.5, "kota/abadi_persik", 180.0)
    anchor(p, 10.5, DECK + 3.0, 10.5, "kota/abadi_kebun", 0.0, protect=0, pts=[-6, 2, -6, 6, 2, -6, -6, 2, 6, 6, 2, 6, 0, 3, 0])
    post(p, 1, DECK + 1, sz - 3, height=2, post_role="rail")
    post(p, sx - 2, DECK + 1, sz - 3, height=2, post_role="rail")
    finish_lights(p, 10, DECK + 1, 18)


def build_lonceng(p, sx, sz):
    p.walls(2, DECK + 1, 2, sx - 3, DECK + 16, sz - 3, role="plaster", pillar="minecraft:quartz_pillar", windows=4, door=("south", 1))
    for y in (DECK + 5, DECK + 10):
        p.fill(3, y, 3, sx - 4, y, sz - 4, "minecraft:cherry_planks")
        p.put(sx // 2, y, sz // 2, "minecraft:air")
        beam_band(p, 2, 2, sx - 3, sz - 3, y, role="trim")
    p.put(sx // 2, DECK + 16, sz // 2, "minecraft:cherry_planks")
    p.put(sx // 2, DECK + 15, sz // 2, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    hip(p, 1, DECK + 17, 1, sx - 2, sz - 2, overhang=1, tiers=2, tier_gap=1, tier_band=2, band_role="plaster", top="pearl")
    anchor(p, sx / 2.0, DECK + 14.0, sz / 2.0, "kota/abadi_lonceng", 0.0, protect=16)
    lit(p, sx // 2, DECK + 10, 3, chain=1, role="lamp")
    finish_lights(p, sx // 2, DECK + 1, sz // 2)


def build_lelang_abadi(p, sx, sz):
    p.fill(4, DECK + 1, 2, sx - 5, DECK + 2, 6, "minecraft:quartz_bricks")
    for x in range(4, sx - 4):
        p.put(x, DECK + 3, 6, "minecraft:white_carpet")
        p.put(x, DECK + 3, 5, "minecraft:yellow_carpet")
    for x in (4, sx - 5):
        p.pillar(x, DECK + 3, DECK + 9, 2, role="minecraft:quartz_pillar", base="trim", cap="trim")
    for x in range(4, sx - 4):
        p.put(x, DECK + 10, 2, "trim")
    for x in range(5, sx - 5):
        p.put(x, DECK + 4, 2, "minecraft:white_concrete" if x % 2 else "jade_tile")
    hip(p, 3, DECK + 11, 1, sx - 4, 7, overhang=1, top="pearl")
    for z in (9, 11, 13):
        for x in range(3, sx - 3):
            if x != sx // 2:
                p.stairs(x, DECK + 1, z, "minecraft:quartz_stairs", "north")
    anchor(p, sx / 2.0, DECK + 3.0, 4.5, "kota/abadi_lelang", 0.0)
    anchor(p, sx / 2.0, DECK + 7.0, 4.5, "kota/panggung", 0.0, protect=0, pts=[-4, 0, 0, 4, 0, 0])
    lit(p, sx // 2, DECK + 10, 4, chain=1, role="lamp")
    finish_lights(p, sx // 2, DECK + 1, 8)


def build_rumah_abadi(p, sx, sz):
    p.walls(2, DECK + 1, 2, sx - 3, DECK + 5, sz - 4, role="plaster", pillar="minecraft:quartz_pillar", windows=3, door=("south", 1))
    beam_band(p, 2, 2, sx - 3, sz - 4, DECK + 6, role="trim")
    gable(p, 2, DECK + 7, 2, sx - 3, sz - 4, along="x", overhang=1)
    p.put(4, DECK + 1, 4, "minecraft:pink_carpet")
    p.put(6, DECK + 1, 4, "minecraft:white_carpet")
    p.put(sx - 4, DECK + 1, 3, "minecraft:bookshelf")
    p.put(sx - 4, DECK + 2, 3, "minecraft:candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    p.put(3, DECK + 1, sz - 3, "minecraft:flowering_azalea")
    p.put(sx - 3, DECK + 1, sz - 3, "minecraft:potted_cherry_sapling")
    lit(p, sx // 2, DECK + 5, sz // 2 - 1, chain=1, role="lamp")
    anchor(p, sx / 2.0, DECK + 1.0, sz / 2.0 - 1.0, "kota/abadi_warga", 180.0)
    finish_lights(p, sx // 2, DECK + 1, sz // 2)


def build_rumah_abadi_2(p, sx, sz):
    """A scholar's retreat: a round moon gate, a tea table on a terrace, a cherry tree and a tiny waterfall pool."""
    p.walls(2, DECK + 1, 2, sx - 3, DECK + 4, 6, role="plaster", pillar="minecraft:quartz_pillar", windows=2, door=("south", 1))
    hip(p, 2, DECK + 5, 2, sx - 3, 6, overhang=1, top="pearl")
    p.put(sx // 2, DECK + 1, 3, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    p.put(3, DECK + 1, 8, "minecraft:cherry_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    p.put(2, DECK + 1, 8, "minecraft:pink_carpet")
    p.put(4, DECK + 1, 8, "minecraft:pink_carpet")
    p.put(3, DECK + 2, 8, "minecraft:white_candle", {"candles": "1", "lit": "true", "waterlogged": "false"})
    cherry(p, sx - 3, DECK + 1, 8, 4, 2)
    p.disc(sx - 4, DECK, sz - 3, 1, "minecraft:water")
    lit(p, sx // 2, DECK + 4, 4, chain=1, role="lamp")
    anchor(p, sx / 2.0, DECK + 1.0, 8.5, "kota/abadi_warga", 180.0)
    finish_lights(p, sx // 2, DECK + 1, sz // 2)


# ======================================================================================================== JSON
TOWN_BIOMES = {
    "sedang": ["minecraft:plains", "minecraft:meadow", "minecraft:forest", "minecraft:birch_forest", "minecraft:flower_forest"],
    "salju": ["minecraft:taiga", "minecraft:snowy_plains"],
    "sabana": ["minecraft:savanna"],
    "sakura": ["minecraft:cherry_grove"],
}
STRUCTURE_ID = {"sedang": "kota_kultivator", "salju": "kota_kultivator_salju", "sabana": "kota_kultivator_sabana",
                "sakura": "kota_kultivator_sakura"}


def write_town_pools():
    """The template pools of the current variant (kota/<pool> for sedang, kota/<variant>/<pool> otherwise)."""
    terr = "terrain_matching"
    rigid = "rigid"
    lapuk = K + "kota/lapuk"
    utuh = K + "kota/utuh"

    def S(name, proc=lapuk, proj=rigid):
        return single(ref(name), proc, proj)

    pools = {
        "gerbang": [(S("gerbang"), 2), (S("gerbang_2"), 1)],
        "jalan_utama_a": [(S("jalan_utama_a", utuh, terr), 1)],
        "jalan_utama_b": [(S("jalan_utama_b", utuh, terr), 1)],
        "alun": [(S("alun", utuh), 1)],
        "unik_lelang": [(S("balai_lelang"), 1)],
        "unik_teh": [(S("kedai_teh", utuh), 1), (S("kedai_teh_2"), 1)],
        "unik_peti": [(S("toko_peti"), 1)],
        "unik_pil": [(S("paviliun_pil"), 1), (S("paviliun_pil_2"), 1)],
        "unik_pusaka": [(S("paviliun_pusaka"), 1), (S("paviliun_pusaka_2"), 1)],
        "unik_penginapan": [(S("penginapan"), 1), (S("penginapan_2"), 1)],
        "unik_kantor": [(S("kantor_sekte"), 1), (S("kantor_sekte_2"), 1)],
        "jalan": [(S("jalan_lurus_1", utuh, terr), 3), (S("jalan_lurus_2", utuh, terr), 2), (S("jalan_lurus_3", utuh, terr), 2),
                  (S("jalan_lurus_4", utuh, terr), 2), (S("jalan_lurus_5", utuh, terr), 2), (S("jembatan", utuh, terr), 1),
                  (S("jembatan_2", utuh, terr), 1)],
        "jalan_ujung": [(S("jalan_lurus_1", utuh, terr), 3), (S("jalan_lurus_2", utuh, terr), 2), (S("jalan_lurus_4", utuh, terr), 2),
                        (S("jalan_lurus_5", utuh, terr), 1), (S("jalan_silang", utuh, terr), 2), (S("jalan_silang_2", utuh, terr), 1),
                        (S("jembatan", utuh, terr), 1), (S("jembatan_2", utuh, terr), 1),
                        (S("tembok_ujung_1"), 2), (S("tembok_ujung_2"), 2), (S("tembok_ujung_3"), 1)],
        "tembok_ujung": [(S("tembok_ujung_1"), 2), (S("tembok_ujung_2"), 2), (S("tembok_ujung_3"), 1)],
        "rumah": [(S("rumah_kecil_%d" % i), 3) for i in range(3)]
                 + [(S("rumah_sedang_%d" % i), 2) for i in range(3)]
                 + [(S("rumah_besar_%d" % i), 1) for i in range(3)]
                 + [(S("rumah_halaman_%d" % i), 1) for i in range(3)]
                 + [(S("rumah_toko_%d" % i), 2) for i in range(3)]
                 + [(S("hias_%s" % k, utuh), 1) for k in ("kios", "gerobak", "lentera", "sumur", "kuil", "batu", "latihan", "pohon")]
                 + [({"element_type": "minecraft:empty_pool_element"}, 1)],
    }
    fallback = {"jalan": ref("tembok_ujung"), "jalan_ujung": ref("tembok_ujung")}
    for name, elements in pools.items():
        write_json(json_path("template_pool", folder(), name), pool(ref(name), elements, fallback.get(name, "minecraft:empty")))


def write_abadi_pools():
    utuh = K + "kota/utuh"

    def S(name):
        return single(K + "kota/" + name, utuh, "rigid")

    pools = {
        "abadi_gerbang": [(S("abadi_gerbang"), 1)],
        "abadi_jembatan": [(S("abadi_jembatan"), 1)],
        "abadi_jembatan_kebun": [(S("abadi_jembatan_kebun"), 1)],
        "abadi_jembatan_lonceng": [(S("abadi_jembatan_lonceng"), 1)],
        "abadi_jembatan_lelang": [(S("abadi_jembatan_lelang"), 1)],
        "abadi_alun": [(S("abadi_alun"), 1)],
        "abadi_istana": [(S("abadi_istana"), 1)],
        "abadi_pasar": [(S("abadi_pasar"), 1)],
        "abadi_dao": [(S("abadi_dao"), 1)],
        "abadi_kebun": [(S("abadi_kebun"), 1)],
        "abadi_lonceng": [(S("abadi_lonceng"), 1)],
        "abadi_lelang": [(S("abadi_lelang"), 1)],
        "abadi_rumah": [(S("abadi_rumah"), 2), (S("abadi_rumah_2"), 2), ({"element_type": "minecraft:empty_pool_element"}, 1)],
    }
    for name, elements in pools.items():
        write_json(json_path("template_pool", "kota", name), pool(K + "kota/" + name, elements, "minecraft:empty"))


def write_processors():
    protect = {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}

    def rule(block, out, chance):
        return {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": block, "probability": chance},
                "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": out}}

    mossy = {"processor_type": "minecraft:rule", "rules": [
        rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.12),
        rule("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.2),
        rule("minecraft:polished_andesite", "minecraft:cracked_stone_bricks", 0.06),
        rule("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.05),
        rule("minecraft:mud_bricks", "minecraft:packed_mud", 0.06),
        rule("minecraft:smooth_sandstone", "minecraft:sandstone", 0.08),
        rule("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.05),
        rule("minecraft:white_terracotta", "minecraft:light_gray_terracotta", 0.03),
    ]}
    write_json(json_path("processor_list", "kota", "lapuk"), {"processors": [mossy, protect]})
    write_json(json_path("processor_list", "kota", "utuh"), {"processors": [protect]})


def write_structures():
    tags = os.path.join(DATA, "tags", "worldgen")
    for v, sid in STRUCTURE_ID.items():
        CUR["v"] = v
        town = {
            "type": "minecraft:jigsaw", "biomes": "#kultivator:has_structure/kota/" + sid, "step": "surface_structures",
            "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}}, "terrain_adaptation": "beard_thin",
            "start_pool": ref("gerbang"), "size": 6, "start_height": {"absolute": 0}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 72, "use_expansion_hack": False,
        }
        write_json(json_path("structure", "kota", sid), town)
        write_json(os.path.join(tags, "biome", "has_structure", "kota", sid + ".json"), {"replace": False, "values": TOWN_BIOMES[v]})
    CUR["v"] = "sedang"
    city = {
        "type": "minecraft:jigsaw", "biomes": "#kultivator:has_structure/kota/kota_abadi", "step": "surface_structures",
        "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}}, "terrain_adaptation": "none",
        "start_pool": K + "kota/abadi_gerbang", "size": 5, "start_height": {"absolute": 150},
        "max_distance_from_center": 80, "use_expansion_hack": False,
    }
    write_json(json_path("structure", "kota", "kota_abadi"), city)
    write_json(os.path.join(tags, "biome", "has_structure", "kota", "kota_abadi.json"), {"replace": False, "values": [K + "alam_abadi"]})
    write_json(os.path.join(tags, "structure", "kota.json"), {"replace": False, "values": [K + "kota/" + s for s in STRUCTURE_ID.values()]})
    write_json(os.path.join(tags, "structure", "kota_abadi.json"), {"replace": False, "values": [K + "kota/kota_abadi"]})
    # the structure sets live in the built-in pack kultivator:dunia_kota (DESIGN 34.4.5): no pack, no new towns. One set for
    # the four looks: the same grid, salt and village exclusion as wave 1; the generator tries them until the biome fits.
    pack = os.path.join(REPO, "src", "main", "resources", "resourcepacks", "dunia_kota")
    write_json(os.path.join(pack, "pack.mcmeta"), {"pack": {"description": {"translate": "kultivator.dunia.pack.kota"},
                                                            "min_format": 121, "max_format": 121}})
    sets = os.path.join(pack, "data", "kultivator", "worldgen", "structure_set", "kota")
    write_json(os.path.join(sets, "kota_kultivator.json"), {
        "structures": [{"structure": K + "kota/" + s, "weight": 1} for s in STRUCTURE_ID.values()],
        "placement": {"type": K + "jauh_dari_pusat", "salt": 340531, "spacing": 64, "separation": 26, "min_chunks": 6,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 8}}})
    write_json(os.path.join(sets, "kota_abadi.json"), {
        "structures": [{"structure": K + "kota/kota_abadi", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "salt": 340566, "spacing": 48, "separation": 20}})


def write_loot():
    base = os.path.join(DATA, "loot_table", "chests", "dunia", "kota")
    write_json(os.path.join(base, "pasar_gelap.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4},
             "entries": [
                 {"type": "minecraft:item", "name": K + "spirit_stone_low", "weight": 10,
                  "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 8}}]},
                 {"type": "minecraft:item", "name": K + "spirit_stone_mid", "weight": 3},
                 {"type": "minecraft:item", "name": K + "blank_talisman", "weight": 6,
                  "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                 {"type": "minecraft:item", "name": K + "jade_slip", "weight": 2},
                 {"type": "minecraft:item", "name": "minecraft:ink_sac", "weight": 4},
                 {"type": "minecraft:item", "name": "minecraft:paper", "weight": 6}]},
            {"rolls": 1, "entries": [
                {"type": "minecraft:empty", "weight": 8},
                {"type": "minecraft:item", "name": K + "token_pasar_gelap", "weight": 1}]}]})


# ======================================================================================================== main
def build_town():
    gerbang()
    gerbang_2()
    jalan_utama("jalan_utama_a", "kota/unik_pil", "kota/unik_pusaka", "kota/jalan_utama_b", 53010)
    jalan_utama("jalan_utama_b", "kota/unik_penginapan", "kota/unik_kantor", "kota/alun", 53011)
    jalan("jalan_lurus_1", 53020, "lentera")
    jalan("jalan_lurus_2", 53021, "kios")
    jalan("jalan_lurus_3", 53022, "gerobak")
    jalan("jalan_lurus_4", 53023, "pasar")
    jalan("jalan_lurus_5", 53024, "taman")
    jalan_silang("jalan_silang", 53031, "kuil")
    jalan_silang("jalan_silang_2", 53033, "pohon")
    jembatan("jembatan", 53032, covered=False)
    jembatan("jembatan_2", 53034, covered=True)
    tembok_ujung("tembok_ujung_1", 53035, "polos")
    tembok_ujung("tembok_ujung_2", 53036, "gerbang")
    tembok_ujung("tembok_ujung_3", 53037, "drum")
    alun()
    balai_lelang()
    kedai_teh()
    kedai_teh_2()
    toko_peti()
    paviliun("paviliun_pil", 53053, "pil")
    paviliun("paviliun_pusaka", 53054, "pusaka")
    paviliun_2("paviliun_pil_2", 53063, "pil")
    paviliun_2("paviliun_pusaka_2", 53064, "pusaka")
    penginapan()
    penginapan_2()
    kantor_sekte()
    kantor_sekte_2()
    for i in range(3):
        rumah("rumah_kecil_%d" % i, 7, 7, 3, 53060 + i, i)
        rumah("rumah_sedang_%d" % i, 9, 9, 4, 53070 + i, i)
        rumah("rumah_besar_%d" % i, 11, 9, 4, 53080 + i, i)
        rumah_halaman("rumah_halaman_%d" % i, 53100 + i, i)
        rumah_toko("rumah_toko_%d" % i, 53110 + i, i)
    for i, k in enumerate(("kios", "gerobak", "lentera", "sumur", "kuil", "batu", "latihan", "pohon")):
        hias("hias_" + k, 53090 + i, k)
    write_town_pools()


def build_city():
    CUR["v"] = "sedang"
    abadi_gerbang()
    abadi_jembatan("abadi_jembatan", 66002, 18, "kota/abadi_alun", houses=True)
    abadi_jembatan("abadi_jembatan_kebun", 66003, 10, "kota/abadi_kebun")
    abadi_jembatan("abadi_jembatan_lonceng", 66004, 10, "kota/abadi_lonceng")
    abadi_jembatan("abadi_jembatan_lelang", 66005, 12, "kota/abadi_lelang")
    abadi_alun()
    abadi_hall("abadi_istana", 27, 23, 66020, build_istana, extra_h=34)
    abadi_hall("abadi_pasar", 21, 16, 66021, build_pasar)
    abadi_hall("abadi_dao", 21, 21, 66022, build_dao, extra_h=18)
    abadi_hall("abadi_kebun", 21, 21, 66023, build_kebun)
    abadi_hall("abadi_lonceng", 11, 11, 66024, build_lonceng, extra_h=30)
    abadi_hall("abadi_lelang", 19, 16, 66025, build_lelang_abadi, extra_h=16)
    abadi_hall("abadi_rumah", 11, 11, 66026, build_rumah_abadi, extra_h=14)
    abadi_hall("abadi_rumah_2", 13, 13, 66027, build_rumah_abadi_2, extra_h=12)
    write_abadi_pools()


def main():
    for v in VARIANTS:
        CUR["v"] = v
        build_town()
    build_city()
    write_processors()
    write_structures()
    write_loot()
    validate_all()
    by = {}
    for name, n in COUNTS.items():
        by.setdefault(name.rsplit("/", 1)[0], []).append(n)
    for k, ns in sorted(by.items()):
        print("%-12s %3d pieces, %6d cells, largest %5d" % (k, len(ns), sum(ns), max(ns)))
    if len(sys.argv) > 1:
        preview(sys.argv[1])


def preview(out_dir):
    """Top views of every piece as text files (one glyph per column, the highest block)."""
    os.makedirs(out_dir, exist_ok=True)
    for name in SAVED:
        fold, piece_name = name.rsplit("/", 1)
        _, root = nbt.read_file(out_path(fold, piece_name))
        sx, sy, sz = root["size"]
        top = {}
        palette = root["palette"]
        for b in root["blocks"]:
            x, y, z = b["pos"]
            bid = palette[b["state"]]["id"].split(":")[1]
            if bid == "air":
                continue
            if (x, z) not in top or top[(x, z)][0] < y:
                top[(x, z)] = (y, bid)
        lines = []
        for z in range(sz):
            row = ""
            for x in range(sx):
                cell = top.get((x, z))
                row += " " if cell is None else cell[1][0]
            lines.append(row)
        with open(os.path.join(out_dir, name.replace("/", "_") + ".txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
