"""Module R of DESIGN 34 (Reruntuhan, Penjara & Menara: ideas 55, 62, 65): every structure template and its worldgen JSON.

Writes (only module R's own folders, DESIGN 34.2.3 / 34.6 R):
  data/kultivator/structure/reruntuhan/<tier>/*.nbt        ancestral cave ruins (Gua Leluhur), three tiers
  data/kultivator/structure/penjara/*.nbt                  the ancient demon prison (shrine, shaft, ring hall)
  data/kultivator/structure/menara/*.nbt                   the nine-tier pagoda of the 100-floor tower and its lobby
  data/kultivator/worldgen/{structure,template_pool,processor_list}/{reruntuhan,penjara,menara}/...
  data/kultivator/worldgen/structure_set/menara/lobi.json  (the tower dimension's own set: base data, never toggled)
  data/kultivator/tags/worldgen/biome/has_structure/{reruntuhan,penjara,menara}/*.json
  data/kultivator/tags/worldgen/structure/{reruntuhan,penjara,menara}.json (+ one tag per structure for locate)
  data/kultivator/tags/block/reruntuhan_lapuk.json         blocks the block_rot processor may drop (weathering)
  data/kultivator/loot_table/chests/dunia/reruntuhan/*.json chest loot (capped like harta/HartaLoot)
  resourcepacks/dunia_reruntuhan/pack.mcmeta + data/kultivator/worldgen/structure_set/... (the overworld sets,
                                                            the module's built-in toggle pack kultivator:dunia_reruntuhan)

Facts this file relies on (checked in reference/mcsrc, 26.3):
  * SinglePoolElement places with setKnownShape(true): NO neighbour shape update runs, so every fence, wall, bar, pane
    and stair shape is baked here (fix_connections below), like vanilla's own templates.
  * Template entities are created from their nbt and turned with the piece (StructureTemplate.placeEntities:
    entity.rotate(rotation)), so an anchor saved with Rotation [0, 0] tells the module the piece's facing. Every
    puzzle position is written as an offset in that frame into the anchor's data (one source for blocks and code).
  * JigsawPlacement: a connector's children come from the target pool while the parent's depth != size, then only from
    its fallback pool; children at depth size + 1 are never placed. So a ruin is gate -> N puzzle rooms (pool teka,
    size N) -> the final piece (the teka pool's fallback), and N is the structure's size.
  * With start_jigsaw_name the named jigsaw lands on the start position (the tower lobby centre is world 0, 0).
  * Beardifier: beard_box / beard_thin carve above a piece's ground level inside its whole height; the prison's 40 block
    stair shaft would hollow the mountain around it, so the prison uses terrain_adaptation "none" with solid shells and
    a deep stone plinth under the shrine (DESIGN 34.2.1 deviation, reported).
  * Chest nbt: {id, LootTable} (RandomizableContainer.LOOT_TABLE_TAG); loot tables use "modifier" (one function or an
    inline list) and "condition" in 26.3 (LootPoolEntryContainer, LootPool).
  * Data pack format 121 (version.json pack_version.data_major), pack.mcmeta min_format / max_format.

Run: python tools/gen_dunia_reruntuhan.py [--plan]   (prints validate_all(); --plan also prints floor plans)
Only worldgen ever places these templates (DESIGN 34.0 rule W1); no runtime code calls setBlock for them.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from nbt import Float  # noqa: E402
import structgen  # noqa: E402
from structgen import DATA, PALETTES, REPO, Piece, out_path, validate_all, write_json  # noqa: E402

# Properties verified in mcsrc but missing from the blockstate-derived list (the chest and the pot are drawn by their
# block entity renderers, so their blockstate files have one variant): ChestBlock FACING, TYPE, WATERLOGGED;
# DecoratedPotBlock HORIZONTAL_FACING, WATERLOGGED, CRACKED. Local to this run only.
structgen._SILENT_PROPS = set(structgen._SILENT_PROPS) | {"facing", "type", "cracked"}

NS = "kultivator"
PACK_DIR = os.path.join(REPO, "src", "main", "resources", "resourcepacks", "dunia_reruntuhan")
PACK_DATA = os.path.join(PACK_DIR, "data", NS)
PLAN = "--plan" in sys.argv

# ================================================================================================= palettes

PAL = {
    # Gua Kecil (Qi Refining - Foundation): mossy stone, soul light, oxidised copper inlays
    "kecil": dict(PALETTES["leluhur"], **{
        "wall": "minecraft:stone_bricks", "wall_alt": "minecraft:mossy_stone_bricks", "wall_crack": "minecraft:cracked_stone_bricks",
        "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:andesite", "floor_inlay": "minecraft:polished_deepslate",
        "pillar": "minecraft:chiseled_stone_bricks", "trim": "minecraft:polished_blackstone_bricks",
        "stair": "minecraft:stone_brick_stairs", "slab": "minecraft:stone_brick_slab", "wallb": "minecraft:stone_brick_wall",
        "roof": "minecraft:deepslate_brick_stairs", "roof_slab": "minecraft:deepslate_brick_slab", "roof_full": "minecraft:deepslate_bricks",
        "accent": "minecraft:chiseled_tuff_bricks", "metal": "minecraft:waxed_oxidized_cut_copper",
        "glow": "minecraft:soul_lantern", "light_block": "minecraft:verdant_froglight", "ceiling": "minecraft:deepslate_tiles",
        "carpet": "minecraft:moss_carpet", "gold": "minecraft:waxed_oxidized_copper",
    }),
    # Gua Agung (Golden Core - Nascent Soul): tuff and deepslate halls with gold
    "agung": {
        "wall": "minecraft:tuff_bricks", "wall_alt": "minecraft:polished_tuff", "wall_crack": "minecraft:cracked_deepslate_bricks",
        "floor": "minecraft:polished_deepslate", "floor_alt": "minecraft:deepslate_tiles", "floor_inlay": "minecraft:polished_tuff",
        "pillar": "minecraft:chiseled_tuff_bricks", "trim": "minecraft:chiseled_deepslate",
        "stair": "minecraft:tuff_brick_stairs", "slab": "minecraft:tuff_brick_slab", "wallb": "minecraft:tuff_brick_wall",
        "roof": "minecraft:deepslate_tile_stairs", "roof_slab": "minecraft:deepslate_tile_slab", "roof_full": "minecraft:deepslate_tiles",
        "accent": "minecraft:chiseled_tuff", "metal": "minecraft:gold_block", "glow": "minecraft:soul_lantern",
        "light_block": "minecraft:ochre_froglight", "ceiling": "minecraft:deepslate_bricks", "carpet": "minecraft:black_carpet",
        "gold": "minecraft:gold_block", "path": "minecraft:cobbled_deepslate", "path_alt": "minecraft:tuff",
        "fence": "minecraft:iron_bars", "window": "minecraft:iron_bars", "jade": "minecraft:waxed_oxidized_copper",
        "floor_alt2": "minecraft:cracked_deepslate_tiles",
    },
    # Gua Kuno (Deity Transformation - Dao Union): blackstone, red terracotta, gilded blackstone, crying obsidian
    "kuno": {
        "wall": "minecraft:polished_blackstone_bricks", "wall_alt": "minecraft:red_terracotta",
        "wall_crack": "minecraft:cracked_polished_blackstone_bricks", "floor": "minecraft:polished_blackstone",
        "floor_alt": "minecraft:polished_deepslate", "floor_inlay": "minecraft:gilded_blackstone",
        "pillar": "minecraft:chiseled_polished_blackstone", "trim": "minecraft:gilded_blackstone",
        "stair": "minecraft:polished_blackstone_brick_stairs", "slab": "minecraft:polished_blackstone_brick_slab",
        "wallb": "minecraft:polished_blackstone_brick_wall", "roof": "minecraft:blackstone_stairs",
        "roof_slab": "minecraft:blackstone_slab", "roof_full": "minecraft:blackstone", "accent": "minecraft:crying_obsidian",
        "metal": "minecraft:gold_block", "glow": "minecraft:soul_lantern", "light_block": "minecraft:pearlescent_froglight",
        "ceiling": "minecraft:polished_blackstone_bricks", "carpet": "minecraft:red_carpet", "gold": "minecraft:gold_block",
        "path": "minecraft:blackstone", "path_alt": "minecraft:basalt", "fence": "minecraft:iron_bars",
        "window": "minecraft:iron_bars", "jade": "minecraft:waxed_oxidized_copper", "floor_alt2": "minecraft:deepslate_tiles",
    },
    "penjara": dict(PALETTES["penjara"], **{
        "stair": "minecraft:polished_blackstone_brick_stairs", "slab": "minecraft:polished_blackstone_brick_slab",
        "wallb": "minecraft:polished_blackstone_brick_wall", "wall_crack": "minecraft:cracked_polished_blackstone_bricks",
        "floor_inlay": "minecraft:gilded_blackstone", "light_block": "minecraft:shroomlight", "ceiling": "minecraft:blackstone",
        "metal": "minecraft:gold_block", "gold": "minecraft:gold_block", "plinth": "minecraft:stone_bricks",
        "plinth_alt": "minecraft:mossy_stone_bricks", "plinth_stair": "minecraft:stone_brick_stairs",
        "post": "minecraft:crimson_stem", "beam": "minecraft:stripped_crimson_stem", "band": "minecraft:crimson_planks",
        "railing": "minecraft:stone_brick_wall", "banner": "red", "banner_alt": "black", "lion": "minecraft:polished_andesite",
        "plant": "minecraft:potted_fern",
    }),
    # the badlands prison (DESIGN 34.2.1 biome palette): red sandstone plinth, acacia posts, terracotta bands
    "penjara_gurun": dict(PALETTES["penjara"], **{
        "stair": "minecraft:polished_blackstone_brick_stairs", "slab": "minecraft:polished_blackstone_brick_slab",
        "wallb": "minecraft:polished_blackstone_brick_wall", "wall_crack": "minecraft:cracked_polished_blackstone_bricks",
        "floor_inlay": "minecraft:gilded_blackstone", "light_block": "minecraft:shroomlight", "ceiling": "minecraft:blackstone",
        "metal": "minecraft:gold_block", "gold": "minecraft:gold_block", "plinth": "minecraft:cut_red_sandstone",
        "plinth_alt": "minecraft:smooth_red_sandstone", "plinth_stair": "minecraft:red_sandstone_stairs",
        "post": "minecraft:stripped_acacia_log", "beam": "minecraft:acacia_log", "band": "minecraft:red_terracotta",
        "roof": "minecraft:mud_brick_stairs", "roof_slab": "minecraft:mud_brick_slab", "roof_full": "minecraft:mud_bricks",
        "floor": "minecraft:polished_blackstone", "floor_alt": "minecraft:terracotta",
        "railing": "minecraft:red_sandstone_wall", "banner": "orange", "banner_alt": "black", "lion": "minecraft:chiseled_red_sandstone",
        "plant": "minecraft:potted_dead_bush",
    }),
    "menara": dict(PALETTES["menara"], **{
        "stair": "minecraft:quartz_stairs", "slab": "minecraft:smooth_quartz_slab", "wallb": "minecraft:stone_brick_wall",
        "wall_crack": "minecraft:quartz_bricks", "floor_inlay": "minecraft:lapis_block", "light_block": "minecraft:sea_lantern",
        "ceiling": "minecraft:smooth_quartz", "metal": "minecraft:gold_block", "gold": "minecraft:gold_block",
        "beam": "minecraft:stripped_dark_oak_log", "lacquer": "minecraft:red_nether_bricks", "lacquer_stair": "minecraft:red_nether_brick_stairs",
        "plinth": "minecraft:polished_andesite", "plinth_alt": "minecraft:andesite", "plinth_stair": "minecraft:polished_andesite_stairs",
        "railing": "minecraft:diorite_wall", "banner": "red", "banner_alt": "blue", "lion": "minecraft:chiseled_quartz_block",
        "plant": "minecraft:potted_azalea_bush", "court": "minecraft:smooth_stone", "court_alt": "minecraft:polished_diorite",
        "lamp": "minecraft:lantern",
    }),
}
# the tower's two biome looks (DESIGN 34.2.1: one pagoda per biome family, the same nine-storey plan)
PAL["menara_sakura"] = dict(PAL["menara"], **{
    "roof": "minecraft:cherry_stairs", "roof_slab": "minecraft:cherry_slab", "roof_full": "minecraft:cherry_planks",
    "wall": "minecraft:calcite", "wall_alt": "minecraft:smooth_quartz", "floor_inlay": "minecraft:pink_glazed_terracotta",
    "accent": "minecraft:pink_terracotta", "railing": "minecraft:cherry_fence", "banner": "pink", "banner_alt": "white",
    "window": "minecraft:cherry_trapdoor", "plant": "minecraft:potted_cherry_sapling", "lamp": "minecraft:lantern",
    "court": "minecraft:smooth_quartz", "court_alt": "minecraft:quartz_bricks",
})
PAL["menara_sabana"] = dict(PAL["menara"], **{
    "roof": "minecraft:brick_stairs", "roof_slab": "minecraft:brick_slab", "roof_full": "minecraft:bricks",
    "wall": "minecraft:mud_bricks", "wall_alt": "minecraft:packed_mud", "wall_crack": "minecraft:mud_bricks",
    "floor": "minecraft:smooth_sandstone", "floor_alt": "minecraft:cut_sandstone", "floor_inlay": "minecraft:orange_glazed_terracotta",
    "ceiling": "minecraft:smooth_sandstone", "accent": "minecraft:orange_terracotta", "lacquer": "minecraft:red_terracotta",
    "beam": "minecraft:stripped_acacia_log", "railing": "minecraft:acacia_fence", "banner": "orange", "banner_alt": "yellow",
    "window": "minecraft:acacia_trapdoor", "plant": "minecraft:potted_dead_bush", "plinth": "minecraft:sandstone",
    "plinth_alt": "minecraft:chiseled_sandstone", "plinth_stair": "minecraft:sandstone_stairs", "lion": "minecraft:chiseled_sandstone",
    "court": "minecraft:smooth_sandstone", "court_alt": "minecraft:cut_sandstone", "stair": "minecraft:smooth_sandstone_stairs",
    "slab": "minecraft:smooth_sandstone_slab", "glow": "minecraft:lantern",
})
# Gua: the dressing of every tier (banner colours, the plants of its garden rooms, the lion stone)
for _t, _d in (("kecil", {"banner": "green", "banner_alt": "white", "plant": "minecraft:potted_azalea_bush", "lion": "minecraft:mossy_stone_bricks",
                          "railing": "minecraft:stone_brick_wall", "lamp": "minecraft:soul_lantern"}),
               ("agung", {"banner": "blue", "banner_alt": "white", "plant": "minecraft:potted_fern", "lion": "minecraft:chiseled_tuff",
                          "railing": "minecraft:tuff_brick_wall", "lamp": "minecraft:soul_lantern"}),
               ("kuno", {"banner": "red", "banner_alt": "black", "plant": "minecraft:potted_dead_bush", "lion": "minecraft:chiseled_polished_blackstone",
                         "railing": "minecraft:polished_blackstone_brick_wall", "lamp": "minecraft:soul_lantern"})):
    PAL[_t].update(_d)
# Dao theme of each tier's Giok Warisan (technique/source/InheritanceTrials.THEMES)
THEMES = {"kecil": ["sword", "life", "body"], "agung": ["fire", "ice", "lightning", "sword"], "kuno": ["space", "shadow", "universal"]}
TIER = {"kecil": 1, "agung": 2, "kuno": 3}
PUZZLES = ["bagua", "unsur", "bintang", "ilusi", "cermin", "timbangan"]
# exit side per puzzle room (the chain meanders so a five-room Gua Kuno stays inside the 128 block jigsaw range)
EXIT = {"bagua": "east", "unsur": "north", "bintang": "west", "ilusi": "north", "cermin": "east", "timbangan": "west"}
ROCK = {
    "hill": [("minecraft:stone", 50), ("minecraft:andesite", 14), ("minecraft:cobblestone", 10), ("minecraft:mossy_cobblestone", 8),
             ("minecraft:tuff", 8), ("minecraft:gravel", 3), ("minecraft:moss_block", 4), ("minecraft:coarse_dirt", 3)],
    "mountain": [("minecraft:stone", 50), ("minecraft:andesite", 18), ("minecraft:tuff", 12), ("minecraft:calcite", 6),
                 ("minecraft:cobblestone", 8), ("minecraft:diorite", 6)],
    "badlands": [("minecraft:terracotta", 24), ("minecraft:red_terracotta", 18), ("minecraft:orange_terracotta", 14),
                 ("minecraft:brown_terracotta", 10), ("minecraft:light_gray_terracotta", 8), ("minecraft:red_sandstone", 10),
                 ("minecraft:blackstone", 8), ("minecraft:basalt", 8)],
}

_OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
_DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}

JSONS = []       # (path, obj)
PACK_JSONS = []  # (path, obj) inside the toggle pack
TEMPLATES = []   # (id, Piece) for the plan / read-back check


def j(path, obj, pack=False):
    (PACK_JSONS if pack else JSONS).append((path, obj))


def wjson(kind, folder, name, obj, pack=False):
    base = PACK_DATA if pack else DATA
    j(os.path.join(base, "worldgen", kind, folder, name + ".json"), obj, pack)


def weighted(rng, table):
    total = sum(w for _, w in table)
    r = rng.random() * total
    for item, w in table:
        r -= w
        if r <= 0:
            return item
    return table[-1][0]


# ================================================================================================= piece helpers

class RPiece(Piece):
    """structgen.Piece with module R's decoration helpers."""

    def rock(self, x, y, z, kind="hill"):
        self.put(x, y, z, weighted(self.rng, ROCK[kind]))

    def rock_fill(self, x0, y0, z0, x1, y1, z1, kind="hill", only_empty=True):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if not self.inside(x, y, z):
                        continue
                    if only_empty and self.get(x, y, z) is not None:
                        continue
                    self.rock(x, y, z, kind)

    def mixed(self, x0, y0, z0, x1, y1, z1, roles, only_empty=False):
        """Fill with a weighted mix [(role, weight)] (walls that are not one flat colour)."""
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if only_empty and self.inside(x, y, z) and self.get(x, y, z) is not None:
                        continue
                    self.put(x, y, z, weighted(self.rng, roles))

    def stair(self, x, y, z, role, facing, half="bottom", shape="straight"):
        self.stairs(x, y, z, role, facing, half, shape)

    def eaves(self, x0, y, z0, x1, z1, layers=3, overhang=2, role="roof", curl=True, core="roof_full"):
        """A pagoda skirt roof: a stepped frustum whose outer `layers` rings are stairs rising inward from `overhang`
        blocks outside the body x0..x1 / z0..z1 (outer corner shapes, the four corners curled up by a top stair one
        higher), solid underneath (no holes seen from below) and a flat solid deck inside, at y + layers - 1, that the
        next storey stands on. Returns the y of the deck."""
        ax0, az0, ax1, az1 = x0 - overhang, z0 - overhang, x1 + overhang, z1 + overhang
        for x in range(ax0, ax1 + 1):
            for z in range(az0, az1 + 1):
                i = min(x - ax0, ax1 - x, z - az0, az1 - z)
                if i >= layers:
                    for yy in range(y, y + layers):
                        self.put(x, yy, z, core)
                    continue
                for yy in range(y, y + i):
                    self.put(x, yy, z, core)
                lx0, lz0, lx1, lz1 = ax0 + i, az0 + i, ax1 - i, az1 - i
                corner = (x in (lx0, lx1)) and (z in (lz0, lz1))
                if corner:
                    if z == lz0:
                        self.stairs(x, y + i, z, role, "south", shape="outer_left" if x == lx0 else "outer_right")
                    else:
                        self.stairs(x, y + i, z, role, "north", shape="outer_right" if x == lx0 else "outer_left")
                elif z == lz0:
                    self.stairs(x, y + i, z, role, "south")
                elif z == lz1:
                    self.stairs(x, y + i, z, role, "north")
                elif x == lx0:
                    self.stairs(x, y + i, z, role, "east")
                else:
                    self.stairs(x, y + i, z, role, "west")
        if curl:
            for cx, cz, f in ((ax0, az0, "south"), (ax1, az0, "south"), (ax0, az1, "north"), (ax1, az1, "north")):
                self.stairs(cx, y + 1, cz, role, f, half="top")
        return y + layers - 1

    def column(self, x, z, y0, y1, role="pillar", base="stair", cap="stair", ring=None):
        """A square column with a stair-flared foot and capital on the four sides (the ring of stairs sits on the
        neighbours: call it where those cells are free floor / ceiling)."""
        for y in range(y0, y1 + 1):
            self.put(x, y, z, role, self._axis_y(role))
        ring = ring or ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south"))
        if base:
            for dx, dz, face in ring:
                if self.inside(x + dx, y0, z + dz) and self.get(x + dx, y0, z + dz) in (None, "minecraft:air"):
                    self.stair(x + dx, y0, z + dz, base, face)
        if cap:
            for dx, dz, face in ring:
                if self.inside(x + dx, y1, z + dz) and self.get(x + dx, y1, z + dz) in (None, "minecraft:air"):
                    self.stair(x + dx, y1, z + dz, cap, face, half="top")

    def hang(self, x, y_ceiling, z, length=2, role="glow"):
        """A lantern hanging under the ceiling block at y_ceiling."""
        self.lantern(x, y_ceiling, z, chain=length, role=role)

    def candles(self, x, y, z, n=None, color="", lit=True):
        n = n or self.rng.randint(1, 4)
        bid = "minecraft:%scandle" % (color + "_" if color else "")
        self.put(x, y, z, bid, {"candles": str(n), "lit": "true" if lit else "false", "waterlogged": "false"})

    def cobweb_corners(self, x0, y, z0, x1, z1, chance=0.5):
        for cx, cz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            if self.rng.random() < chance and self.get(cx, y, cz) in (None, "minecraft:air"):
                self.put(cx, y, cz, "minecraft:cobweb")

    def roots(self, x0, y, z0, x1, z1, chance=0.06):
        """Hanging roots and lichen under a ceiling at y + 1 (decay)."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.get(x, y, z) in (None, "minecraft:air") and self.rng.random() < chance:
                    self.put(x, y, z, "minecraft:hanging_roots", {"waterlogged": "false"})

    def rubble(self, x, y, z, size=2, kind="hill"):
        """A pile of fallen stones: cobble, gravel and slabs, highest in the middle."""
        for dx in range(-size, size + 1):
            for dz in range(-size, size + 1):
                d = abs(dx) + abs(dz)
                if d > size + 1 or not self.inside(x + dx, y, z + dz):
                    continue
                if self.rng.random() < 0.15:
                    continue
                h = max(0, size - d)
                for k in range(h):
                    self.rock(x + dx, y + k, z + dz, kind)
                top = y + h
                if self.inside(x + dx, top, z + dz) and self.get(x + dx, top, z + dz) in (None, "minecraft:air"):
                    pick = self.rng.random()
                    if pick < 0.35:
                        self.slab(x + dx, top, z + dz, "minecraft:cobblestone_slab")
                    elif pick < 0.55:
                        self.put(x + dx, top, z + dz, "minecraft:gravel")
                    elif pick < 0.62:
                        self.put(x + dx, top, z + dz, "minecraft:stone_button", {"face": "floor", "facing": "north", "powered": "false"})

    def niche(self, x, y, z, facing, deco="candle"):
        """A recess in a wall (the wall cell at x,y,z becomes air with a little shrine)."""
        self.put(x, y, z, "minecraft:air")
        if deco == "candle":
            self.candles(x, y, z, lit=True)
        elif deco == "skull":
            self.put(x, y, z, "minecraft:skeleton_skull", {"rotation": str(self.rng.randint(0, 15))})
        elif deco == "pot":
            self.put(x, y, z, "minecraft:decorated_pot", {"facing": facing, "cracked": "false", "waterlogged": "false"})
        elif deco == "book":
            self.put(x, y, z, "minecraft:chiseled_bookshelf", {"facing": facing})

    def chest(self, x, y, z, facing, loot):
        self.put(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": loot})

    def anchor_at(self, x, y, z, kind, yaw=0.0, **data):
        """A dunia anchor (DESIGN 34.4.1) at the centre of block (x, y, z) with Rotation [yaw, 0] (turned with the piece)."""
        payload = {"id": "kultivator:dunia_anchor", "kind": kind, "Rotation": [Float(float(yaw)), Float(0.0)]}
        if data:
            payload["data"] = data
        self.entity(x + 0.5, float(y), z + 0.5, payload)

    # ------------------------------------------------------------------------------------------ dressing (detail pass)
    # Everything below is plain vanilla blocks placed by worldgen only; the ambient life of a ruin (campfire smoke, the
    # flicker of candles, falling spore dust) is vanilla's own client animation, so it costs no runtime code.

    def free(self, x, y, z):
        """True when (x, y, z) is inside and empty or explicit air (dressing never overwrites a built block)."""
        return self.inside(x, y, z) and self.get(x, y, z) in (None, "minecraft:air")

    def banner(self, x, y, z, facing, color=None):
        """A wall banner on the block behind it, facing away from that wall (`color` a dye name or a palette role)."""
        c = color or self.pal.get("banner", "red")
        if c in self.pal and ":" not in self.pal[c]:
            c = self.pal[c]
        if self.free(x, y, z):
            self.put(x, y, z, "minecraft:%s_wall_banner" % c, {"facing": facing})

    def standing_banner(self, x, y, z, color, rotation):
        """A free-standing banner; rotation 0 faces south, every step 22.5 degrees clockwise."""
        if self.free(x, y, z):
            self.put(x, y, z, "minecraft:%s_banner" % color, {"rotation": str(int(rotation) % 16)})

    def bracket_lantern(self, x, y, z, toward, role=None):
        """A lantern under a carved bracket: a top-half stair at (x, y, z) whose back leans on the wall at `toward`."""
        if not self.free(x, y, z) or not self.free(x, y - 1, z):
            return
        self.stair(x, y, z, "stair", toward, half="top")
        self.lantern(x, y, z, chain=0, role=role or ("lamp" if "lamp" in self.pal else "glow"))

    def brazier(self, x, y, z, soul=True, post="wallb"):
        """An incense brazier: a carved post with a lit campfire on it (vanilla smoke, nothing to tick)."""
        if post:
            if not self.free(x, y, z):
                return
            self.put(x, y, z, post)
            y += 1
        if self.free(x, y, z):
            self.put(x, y, z, "minecraft:soul_campfire" if soul else "minecraft:campfire",
                     {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})

    def lion(self, x, y, z, facing, body=None):
        """A stone guardian lion (shishi) looking `facing`: plinth, crouched body, a stair head and a carved mane."""
        if not (self.free(x, y, z) and self.free(x, y + 1, z) and self.free(x, y + 2, z)):
            return
        self.put(x, y, z, "trim")
        self.put(x, y + 1, z, body or self.pal.get("lion", self.b("accent")))
        self.stair(x, y + 2, z, "stair", _OPP[facing])
        dx, dz = _DIRS[facing]
        if self.free(x + dx, y + 1, z + dz):
            self.slab(x + dx, y + 1, z + dz, "slab", "top")

    def toro(self, x, y, z, post="wallb", role=None, cap="slab"):
        """A stone lantern (toro): a post, the lantern and a little slab roof."""
        if not all(self.free(x, y + k, z) for k in range(3)):
            return
        self.put(x, y, z, post)
        bid = self.b(role or ("lamp" if "lamp" in self.pal else "glow"))
        self.put(x, y + 1, z, bid, {"hanging": "false", "waterlogged": "false"} if bid.endswith("lantern") and "sea_" not in bid else None)
        self.slab(x, y + 2, z, cap)

    def plant(self, x, y, z, kind=None):
        """A potted plant (the palette's plant or a given potted_* id)."""
        if self.free(x, y, z):
            self.put(x, y, z, kind or self.pal.get("plant", "minecraft:potted_fern"))

    def tea_table(self, x, y, z, wood="spruce", cushion="red_carpet"):
        """A low table (a top-half trapdoor), a teapot (flower pot) and a candle on it, cushions round it."""
        if not self.free(x, y, z):
            return
        self.put(x, y, z, "minecraft:%s_trapdoor" % wood, {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                                          "waterlogged": "false"})
        if self.free(x, y + 1, z):
            if self.rng.random() < 0.5:
                self.put(x, y + 1, z, "minecraft:flower_pot")
            else:
                self.put(x, y + 1, z, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if self.free(x + dx, y, z + dz) and self.rng.random() < 0.75:
                self.put(x + dx, y, z + dz, "minecraft:" + cushion)

    def lichen(self, x, y, z, face):
        """Glow lichen growing on the `face` side of the cell (the wall it clings to)."""
        if self.free(x, y, z):
            props = {s: "false" for s in ("north", "south", "east", "west", "up", "down")}
            props[face] = "true"
            props["waterlogged"] = "false"
            self.put(x, y, z, "minecraft:glow_lichen", props)

    def bell(self, x, y, z, facing="north", attach="ceiling"):
        if self.free(x, y, z):
            self.put(x, y, z, "minecraft:bell", {"attachment": attach, "facing": facing, "powered": "false"})

    def pond(self, x0, z0, x1, z1, y, lily=0.35, pickle=0.0):
        """A shallow garden pool sunk into the floor at y (water bounded by the floor around it), lily pads on it."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not self.inside(x, y, z):
                    continue
                self.put(x, y, z, "minecraft:water", {"level": "0"})
                self.put(x, y - 1, z, "minecraft:mud" if self.rng.random() < 0.5 else "minecraft:clay")
                if self.free(x, y + 1, z) and self.rng.random() < lily:
                    self.put(x, y + 1, z, "minecraft:lily_pad")

    def mini_roof(self, x0, x1, z, y, role="roof", slab_role="roof_slab", full_role="roof_full", along="x"):
        """A three-row curled roof along x (or z) centred on row z (or x): two stair slopes, a slab ridge and the four
        upturned eave corners. Used over gateways, archways and shrines."""
        if along == "x":
            for x in range(x0, x1 + 1):
                self.stairs(x, y, z - 1, role, "south")
                self.stairs(x, y, z + 1, role, "north")
                self.put(x, y, z, full_role)
                self.slab(x, y + 1, z, slab_role)
            for x in (x0, x1):
                self.stairs(x, y + 1, z - 1, role, "south", half="top")
                self.stairs(x, y + 1, z + 1, role, "north", half="top")
        else:
            for zz in range(x0, x1 + 1):
                self.stairs(z - 1, y, zz, role, "east")
                self.stairs(z + 1, y, zz, role, "west")
                self.put(z, y, zz, full_role)
                self.slab(z, y + 1, zz, slab_role)
            for zz in (x0, x1):
                self.stairs(z - 1, y + 1, zz, role, "east", half="top")
                self.stairs(z + 1, y + 1, zz, role, "west", half="top")

    def paifang(self, cx, y, z, half=3, height=6, post="lacquer", beam="beam", along="x"):
        """A memorial archway (paifang) of two posts `half` blocks either side of cx, a beam with a gold plaque and a
        curled roof on top; along x (the way through runs along z)."""
        post = post if post in self.pal or ":" in post else "pillar"
        beam = beam if beam in self.pal or ":" in beam else "trim"
        for s in (-half, half):
            px, pz = (cx + s, z) if along == "x" else (z, cx + s)
            self.put(px, y - 1, pz, "trim")
            for yy in range(y, y + height):
                self.put(px, yy, pz, post, self._axis_y(post))
        for s in range(-half - 1, half + 2):
            px, pz = (cx + s, z) if along == "x" else (z, cx + s)
            self.put(px, y + height, pz, beam, {"axis": along} if self.b(beam).endswith(("_log", "_stem", "_wood", "_hyphae")) else None)
        mx, mz = (cx, z) if along == "x" else (z, cx)
        self.put(mx, y + height - 1, mz, "gold")
        self.mini_roof(cx - half - 1, cx + half + 1, z, y + height + 1, along=along)

    def railing(self, x0, z0, x1, z1, y, role="railing", gap=None):
        """A carved railing along a straight line (connections are baked by fix_connections); `gap` (a, b) leaves a way
        through between along-positions a..b."""
        n = max(abs(x1 - x0), abs(z1 - z0))
        for s in range(n + 1):
            x = x0 + (x1 - x0) * s // max(1, n)
            z = z0 + (z1 - z0) * s // max(1, n)
            if gap and gap[0] <= s <= gap[1]:
                continue
            if self.free(x, y, z):
                self.put(x, y, z, role if role in self.pal or ":" in role else "wallb")

    def wall_dressing(self, x, y, z, face, kind):
        """One wall cell's dressing facing `face`: 'banner', 'lantern', 'lichen', 'candle'."""
        if kind == "banner":
            self.banner(x, y, z, face, self.pal.get("banner", "red") if self.rng.random() < 0.7 else self.pal.get("banner_alt", "white"))
        elif kind == "lantern":
            self.bracket_lantern(x, y, z, _OPP[face])
        elif kind == "lichen":
            self.lichen(x, y, z, _OPP[face])

    def moss_floor(self, x0, y, z0, x1, z1, chance, keep=None):
        """Moss carpets on free floor cells (skipping `keep(x, z)` cells)."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if keep and keep(x, z):
                    continue
                if self.free(x, y, z) and self.get(x, y - 1, z) not in (None, "minecraft:air", "minecraft:water") and self.rng.random() < chance:
                    self.put(x, y, z, "minecraft:moss_carpet")

    def spore(self, x, y, z):
        """A spore blossom under the ceiling at y + 1 (its falling spores are vanilla ambient)."""
        if self.free(x, y, z) and not self.free(x, y + 1, z):
            self.put(x, y, z, "minecraft:spore_blossom")

    def bones(self, x, y, z, facing="east"):
        """Remains of an old challenger: a bone block, a skull and a scatter of bone meal-white slabs."""
        if self.free(x, y, z):
            self.put(x, y, z, "minecraft:bone_block", {"axis": "x" if facing in ("east", "west") else "z"})
        dx, dz = _DIRS[facing]
        if self.free(x + dx, y, z + dz):
            self.put(x + dx, y, z + dz, "minecraft:skeleton_skull", {"rotation": str(self.rng.randint(0, 15))})


# --------------------------------------------------------------------------------------------- connection shapes

_PANE = ("_pane", "iron_bars", "copper_bars")
_FENCE = ("_fence",)
_WALL = ("_wall",)


def _kind(bid):
    name = bid.split(":", 1)[1]
    if name.endswith("_wall") and not name.endswith("_wall_banner") and "wall_" not in name:
        return "wall"
    if name.endswith("_fence"):
        return "fence"
    if name.endswith("_pane") or name in ("iron_bars", "copper_bars") or name.endswith("_bars"):
        return "pane"
    return None


def fix_connections(p):
    """Bake the neighbour shapes vanilla would compute (SinglePoolElement places with known shape, no updates)."""
    solid = {}

    def is_solid(x, y, z):
        c = p.cells.get((x, y, z))
        if c is None:
            return False
        (bid, props), _ = c
        return nbt._is_full(bid, dict(props))

    def kind_at(x, y, z):
        c = p.cells.get((x, y, z))
        return None if c is None else _kind(c[0][0])

    def bid_at(x, y, z):
        c = p.cells.get((x, y, z))
        return None if c is None else c[0][0]

    sides = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    for pos in sorted(p.cells):
        (bid, props), data = p.cells[pos]
        k = _kind(bid)
        if k is None:
            continue
        x, y, z = pos
        out = dict(props)
        out["waterlogged"] = "false"
        conn = {}
        for side, (dx, dz) in sides.items():
            nk = kind_at(x + dx, y, z + dz)
            nb = bid_at(x + dx, y, z + dz)
            if k == "fence":
                c = (nk == "fence" and ("nether_brick" in nb) == ("nether_brick" in bid)) or is_solid(x + dx, y, z + dz) \
                    or (nb is not None and nb.endswith("_fence_gate"))
            elif k == "pane":
                c = nk in ("pane", "wall") or is_solid(x + dx, y, z + dz)
            else:
                c = nk in ("wall", "pane") or is_solid(x + dx, y, z + dz) or (nb is not None and nb.endswith("_fence_gate"))
            conn[side] = c
        if k in ("fence", "pane"):
            for side, c in conn.items():
                out[side] = "true" if c else "false"
        else:
            above = p.cells.get((x, y + 1, z))
            above_solid = is_solid(x, y + 1, z)
            above_wall = kind_at(x, y + 1, z) == "wall"
            for side, c in conn.items():
                out[side] = ("tall" if (above_solid or above_wall) else "low") if c else "none"
            straight = (conn["north"] and conn["south"] and not conn["east"] and not conn["west"]) or \
                       (conn["east"] and conn["west"] and not conn["north"] and not conn["south"])
            out["up"] = "false" if (straight and not (above is not None and above[0][0] != "minecraft:air" and not above_solid)) else "true"
            if straight and above_wall:
                out["up"] = "true"
        p.cells[pos] = ((bid, tuple(sorted(out.items()))), data)
    return p


def finish(p, tid):
    fix_connections(p)
    TEMPLATES.append((tid, p))
    return p


# ================================================================================================= jigsaw names

J_OUT = "kultivator:reruntuhan/pintu"    # a room's way on (target: the next room's entry)
J_IN = "kultivator:reruntuhan/masuk"     # a room's entry
FACE_YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}


def loot_id(name):
    return "%s:chests/dunia/reruntuhan/%s" % (NS, name)


# ================================================================================================= Gua: the gate

def gua_gate(tier, variant=0):
    """The cliff gate (start piece). Front (the approach trench) south, the way on north.

    Template 19 x 15 x 24: y0 foundation, floor y1 (= surface - 6 through start_height -6 + WORLD_SURFACE_WG), the
    antechamber z 1..11, the carved facade at z 12 with the sealed doorway (Gerbang Segel anchor), the approach trench
    z 13..23 climbing to the surface (y 7) at the south edge, cliff rock around everything up to y 14.

    variant 0 "Gerbang Runtuh": the collapsed gate (broken retaining walls, rubble, a fallen pillar, vines, skull niches).
    variant 1 "Gerbang Paviliun": the gate a later sect kept: a lacquered porch with a curled gable roof over the sealed
    door, a paifang archway at the trench mouth, paired stone lanterns, prayer banners, an ancestral altar with spirit
    tablets inside and guardian lions at the inner door. Both keep every anchor and connector in the same place."""
    pal = PAL[tier]
    rock = "mountain" if tier == "agung" else "badlands" if tier == "kuno" else "hill"
    p = RPiece(19, 15, 24, seed=55100 + TIER[tier] + 10 * variant, palette=pal)
    W, D = 19, 24
    # cliff mass first (everything not built below stays natural rock)
    p.rock_fill(0, 0, 0, W - 1, 14, D - 1, rock, only_empty=False)
    # antechamber shell
    p.mixed(1, 0, 0, 17, 0, 11, [("floor_alt", 3), ("floor", 1)])
    p.fill(2, 1, 1, 16, 1, 11, "floor")
    # floor inlay: a long runner of inlay with trim borders toward the stele
    for z in range(2, 11):
        p.put(9, 1, z, "floor_inlay")
        if z % 2 == 0:
            p.put(8, 1, z, "trim")
            p.put(10, 1, z, "trim")
    p.walls(1, 2, 0, 17, 8, 12, "wall", pillar="pillar", windows=0, interior_air=True)
    # vault ceiling with a stepped profile
    p.fill(1, 9, 0, 17, 9, 12, "ceiling")
    for z in range(1, 12):
        p.stair(2, 8, z, "stair", "east", half="top")
        p.stair(16, 8, z, "stair", "west", half="top")
    p.fill(3, 8, 1, 15, 8, 11, "minecraft:air")
    p.fill(6, 9, 2, 12, 9, 10, "minecraft:air")
    p.fill(6, 10, 2, 12, 10, 10, "ceiling")
    for x in range(6, 13):
        p.stair(x, 9, 1, "stair", "south", half="top")
        p.stair(x, 9, 11, "stair", "north", half="top")
    # mossy and cracked patches on the walls
    for (x, y, z), cell in list(p.cells.items()):
        if cell[0][0] == p.b("wall") and p.rng.random() < (0.22 if variant == 0 else 0.1):
            p.set(x, y, z, p.b(p.rng.choice(["wall_alt", "wall_crack"])))
    # inner columns
    for x in (4, 14):
        for z in (3, 9):
            p.column(x, z, 2, 7)
    # the riddle stele at the back (z 2): a slab of chiseled stone on a plinth with candles
    p.fill(8, 2, 1, 10, 2, 2, "trim")
    p.fill(8, 3, 1, 10, 5, 1, "accent")
    p.put(9, 6, 1, "pillar")
    p.stair(8, 6, 1, "stair", "east", half="bottom")
    p.stair(10, 6, 1, "stair", "west", half="bottom")
    p.candles(7, 2, 2)
    p.candles(11, 2, 2)
    p.anchor_at(9, 2, 3, "reruntuhan/prasasti", yaw=FACE_YAW["south"], tier=TIER[tier], protect=6)
    if variant == 0:
        # wall niches with candles and skulls, shelves of old books
        for z in (5, 7):
            p.niche(1, 3, z, "east", "candle")
            p.niche(17, 3, z, "west", "skull" if tier != "kecil" else "candle")
        p.niche(1, 4, 9, "east", "book")
        p.niche(17, 4, 9, "west", "book")
        p.hang(9, 10, 6, length=2)
        p.hang(5, 8, 6, length=1)
        p.hang(13, 8, 6, length=1)
        # tattered banners of the founder's sect between the columns
        for z in (6, 10):
            p.banner(2, 6, z, "east", "banner_alt" if z == 10 else None)
            p.banner(16, 6, z, "west", "banner_alt" if z == 10 else None)
        p.cobweb_corners(2, 7, 1, 16, 11, chance=0.7)
        p.roots(3, 8, 2, 15, 10, chance=0.05)
        p.rubble(14, 2, 2, size=1, kind=rock)
        p.bones(3, 2, 10, "east")
    else:
        # the ancestral altar: a long table of spirit tablets along both side walls, mourning banners above
        for x, face in ((2, "east"), (16, "west")):
            for z in range(4, 9):
                p.slab(x, 2, z, "slab", "top")
                if z % 2 == 0:
                    p.put(x, 3, z, p.b("wallb"))
                    p.slab(x, 4, z, "slab", "bottom")
                else:
                    p.candles(x, 3, z, n=p.rng.randint(1, 3))
            p.banner(x, 6, 6, face, "banner_alt")
            p.banner(x, 6, 8, face, "banner")
        p.bracket_lantern(2, 7, 10, "west")
        p.bracket_lantern(16, 7, 10, "east")
        # censers before the altar and guardian lions at the inner door
        p.brazier(6, 2, 10, soul=True)
        p.brazier(12, 2, 10, soul=True)
        p.lion(6, 2, 1, "south")
        p.lion(12, 2, 1, "south")
        p.hang(9, 10, 6, length=2)
        p.tea_table(13, 2, 6, wood="dark_oak", cushion="black_carpet" if tier == "kuno" else "red_carpet")
        p.cobweb_corners(2, 7, 1, 16, 11, chance=0.3)
        p.lichen(3, 7, 2, "north")
        p.lichen(15, 6, 11, "south")
    # the way on: open doorway in the north wall, the connector in its floor
    p.air(8, 2, 0, 10, 5, 0)
    p.stair(8, 6, 0, "stair", "east", half="top")
    p.stair(10, 6, 0, "stair", "west", half="top")
    p.jigsaw_side(9, 1, 0, "north", name=J_OUT, target=J_IN, pool="kultivator:reruntuhan/%s/teka" % gua_id(tier),
                  final_state=p.b("floor"))
    # ---------------------------------------------------------------- the facade (z = 12) and the sealed doorway
    for x in range(1, 18):
        for y in range(1, 14):
            p.put(x, y, 12, "wall" if (x + y) % 5 else "wall_alt")
    p.air(8, 2, 12, 10, 5, 12)       # the doorway: the Gerbang Segel entity fills it (an entity, never a block)
    if variant == 0:
        # great frame: two banded pillars, a lintel and a plaque
        for x in (6, 12):
            for y in range(2, 9):
                p.put(x, y, 12, "pillar")
                p.put(x, y, 13, "pillar" if y in (2, 8) else "trim")
        for x in range(6, 13):
            p.put(x, 7, 13, "trim")
            p.put(x, 8, 13, "pillar")
        p.fill(7, 9, 13, 11, 10, 13, "accent")
        p.put(9, 10, 14, "gold")
        for x in range(5, 14):
            p.stair(x, 11, 13, "roof", "south")
        p.stair(4, 11, 13, "roof", "south", shape="outer_left")
        p.stair(14, 11, 13, "roof", "south", shape="outer_right")
        p.stair(4, 12, 13, "roof", "south", half="top")
        p.stair(14, 12, 13, "roof", "south", half="top")
        p.fill(5, 12, 12, 13, 12, 12, "roof_full")
        p.slab(9, 13, 12, "roof_slab")
        # guardian lion-dogs (shishi) on both sides of the doorway
        for x, face in ((4, "east"), (14, "west")):
            p.put(x, 2, 14, "trim")
            p.put(x, 3, 14, "wall")
            p.stair(x, 4, 14, "stair", "south")
            p.put(x, 4, 13, "wall")
            p.put(x, 5, 13, "pillar")
            p.stair(x, 5, 14, "stair", "south", half="top")
            p.put(x, 6, 13, "minecraft:skeleton_skull" if tier == "kuno" else p.b("accent"),
                  {"rotation": "0"} if tier == "kuno" else None)
        # soul fire braziers before the door
        p.put(7, 1, 15, "minecraft:soul_soil")
        p.put(11, 1, 15, "minecraft:soul_soil")
        p.put(7, 2, 15, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        p.put(11, 2, 15, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    p.anchor_at(9, 2, 12, "reruntuhan/gerbang", yaw=FACE_YAW["south"], tier=TIER[tier], protect=12, w=3, h=4)
    p.anchor_at(9, 3, 7, "reruntuhan/situs", yaw=0.0, situs="reruntuhan/" + gua_id(tier), tier=TIER[tier], protect=0)
    # ---------------------------------------------------------------- the approach trench (z 13..23)
    for z in range(13, D):
        rise = max(0, min(5, (z - 14) * 5 // 8))
        floor_y = 1 + rise
        x0, x1 = 5, 13
        for x in range(x0, x1 + 1):
            for y in range(1, floor_y):
                p.rock(x, y, z, rock)
            if z >= 14:
                if variant == 0:
                    p.put(x, floor_y, z, "path" if p.rng.random() < 0.7 else "path_alt")
                else:
                    p.put(x, floor_y, z, "floor_inlay" if x == 9 else "path" if x in (8, 10) else "floor")
            for y in range(floor_y + 1, 15):
                p.put(x, y, z, "minecraft:air")
        # stepped stairs where the floor rises
        nxt = 1 + max(0, min(5, (z + 1 - 14) * 5 // 8))
        if nxt > floor_y and z + 1 < D:
            for x in range(x0 + 1, x1):
                p.stair(x, floor_y + 1, z, "stair", "south")
        # retaining walls on both sides: broken (variant 0) or dressed stone with a railing on top (variant 1)
        for x in (x0 - 1, x1 + 1):
            if variant == 0:
                for y in range(floor_y, min(15, floor_y + 4)):
                    if p.rng.random() < 0.8:
                        p.put(x, y, z, "wall" if p.rng.random() < 0.6 else "wall_alt")
            else:
                for y in range(floor_y, min(15, floor_y + 3)):
                    p.put(x, y, z, "wall" if (y + z) % 4 else "pillar")
                if floor_y + 3 < 15:
                    p.put(x, floor_y + 3, z, "wallb")
    p.fill(5, 1, 13, 13, 1, 14, "floor")
    p.air(5, 2, 13, 13, 14, 14)
    if variant == 0:
        p.rubble(6, 2, 16, size=1, kind=rock)
        p.rubble(12, 3, 18, size=1, kind=rock)
        # a fallen pillar across the trench edge
        for x in range(7, 11):
            p.put(x, 4, 20, "pillar")
        p.standing_lantern(5, 2, 13, post="wallb", role="glow", height=1)
        p.standing_lantern(13, 2, 13, post="wallb", role="glow", height=1)
        # moss and vines over the cliff face
        for x in range(1, 18):
            for y in range(9, 14):
                if p.get(x, y, 13) in (None, "minecraft:air") and p.get(x, y, 12) not in (None, "minecraft:air") and p.rng.random() < 0.2:
                    p.put(x, y, 13, "minecraft:vine", {"north": "true", "east": "false", "south": "false", "west": "false", "up": "false"})
        p.weather(0.06)
    else:
        # the porch: two lacquered posts, beams and a curled gable roof leaning on the cliff over the sealed door
        post = "minecraft:stripped_dark_oak_log" if tier == "kecil" else "minecraft:crimson_stem" if tier == "kuno" else "minecraft:stripped_spruce_log"
        for x in (6, 12):
            for y in range(2, 9):
                p.put(x, y, 15, post, {"axis": "y"})
            p.put(x, 1, 15, "trim")
        for x in range(5, 14):
            p.put(x, 9, 15, "minecraft:stripped_dark_oak_log", {"axis": "x"})
            p.put(x, 9, 13, "minecraft:stripped_dark_oak_log", {"axis": "x"})
        for x in (6, 12):
            p.put(x, 9, 14, "minecraft:stripped_dark_oak_log", {"axis": "z"})
        p.roof_gable(6, 10, 13, 12, 15, role="roof", slab_role="roof_slab", full_role="roof_full", along="x", overhang=1, eave_curl=True)
        # the door frame: lacquer jambs, a lintel and the gilded name plaque
        for y in range(2, 7):
            p.put(7, y, 13, "pillar")
            p.put(11, y, 13, "pillar")
        for x in range(7, 12):
            p.put(x, 7, 13, "trim")
        p.put(9, 8, 13, "gold")
        p.banner(7, 6, 14, "south")
        p.banner(11, 6, 14, "south")
        for x in (8, 10):
            p.lantern(x, 9, 14, chain=1)
        p.lion(5, 2, 14, "south")
        p.lion(13, 2, 14, "south")
        # paired stone lanterns up the trench, prayer banners on its walls, the paifang at its mouth
        for z in (16, 19):
            fy = 1 + max(0, min(5, (z - 14) * 5 // 8))
            p.toro(6, fy + 1, z, post="wallb")
            p.toro(12, fy + 1, z, post="wallb")
        for z in (17, 20):
            fy = 1 + max(0, min(5, (z - 14) * 5 // 8))
            p.banner(5, fy + 3, z, "east", "banner" if z == 17 else "banner_alt")
            p.banner(13, fy + 3, z, "west", "banner_alt" if z == 17 else "banner")
        fy = 1 + max(0, min(5, (22 - 14) * 5 // 8))
        p.paifang(9, fy + 1, 22, half=4, height=5, post=post, beam="minecraft:stripped_dark_oak_log")
        p.plant(6, fy + 1, 23)
        p.plant(12, fy + 1, 23)
        p.weather(0.03)
    return finish(p, gua_id(tier) + ("/gerbang" if variant == 0 else "/gerbang_b"))


def gua_id(tier):
    return "gua_" + tier


# ================================================================================================= Gua: puzzle rooms

ROOM_W, ROOM_H, ROOM_D = 17, 11, 23
CX, CY, CZ = 8, 2, 8          # the room's main anchor block (centre of the 15 x 15 interior, standing level)


def room_shell(tier, puzzle, seed):
    pal = PAL[tier]
    rock = "mountain" if tier == "agung" else "badlands" if tier == "kuno" else "hill"
    p = RPiece(ROOM_W, ROOM_H, ROOM_D, seed=seed, palette=pal)
    p.rock_fill(0, 0, 0, ROOM_W - 1, ROOM_H - 1, ROOM_D - 1, rock, only_empty=False)
    # room box x 0..16, z 0..16; corridor x 6..10, z 17..22
    p.fill(0, 0, 0, 16, 0, 16, "floor_alt")
    p.fill(1, 1, 1, 15, 1, 15, "floor")
    p.walls(0, 2, 0, 16, 8, 16, "wall", pillar="pillar", windows=0)
    p.fill(0, 1, 0, 16, 1, 0, "trim")
    p.fill(0, 1, 16, 16, 1, 16, "trim")
    p.fill(0, 1, 0, 0, 1, 16, "trim")
    p.fill(16, 1, 0, 16, 1, 16, "trim")
    p.fill(0, 9, 0, 16, 9, 16, "ceiling")
    # coffered ceiling: beams every 4 with stair brackets
    for k in (4, 8, 12):
        for t in range(1, 16):
            p.put(k, 8, t, "trim")
            p.put(t, 8, k, "trim")
    for t in range(1, 16):
        p.stair(t, 8, 1, "stair", "south", half="top")
        p.stair(t, 8, 15, "stair", "north", half="top")
        p.stair(1, 8, t, "stair", "east", half="top")
        p.stair(15, 8, t, "stair", "west", half="top")
    for (x, y, z), cell in list(p.cells.items()):
        if cell[0][0] == p.b("wall") and p.rng.random() < 0.25:
            p.set(x, y, z, p.b(p.rng.choice(["wall_alt", "wall_crack"])))
    # corridor
    p.fill(5, 0, 17, 11, 0, 22, "floor_alt")
    p.fill(6, 1, 17, 10, 1, 22, "path")
    for z in range(17, 23):
        for x in (5, 11):
            for y in range(1, 7):
                p.put(x, y, z, "wall" if p.rng.random() < 0.7 else "wall_alt")
        p.fill(5, 7, z, 11, 7, z, "ceiling")
        p.air(6, 2, z, 10, 6, z)
        if z % 3 == 0:
            p.put(5, 2, z, "pillar")
            p.put(11, 2, z, "pillar")
            p.stair(6, 6, z, "stair", "east", half="top")
            p.stair(10, 6, z, "stair", "west", half="top")
    p.put(8, 1, 20, "floor_inlay")
    p.air(7, 2, 16, 9, 5, 16)           # the entry doorway (open)
    p.stair(7, 6, 16, "stair", "east", half="top")
    p.stair(9, 6, 16, "stair", "west", half="top")
    p.jigsaw_side(8, 1, 22, "south", name=J_IN, target=J_OUT, pool="minecraft:empty", final_state=p.b("path"))
    p.hang(8, 7, 19, length=1)
    # wall lanterns between the pillars and candles in niches
    for x in (2, 14):
        p.hang(x, 8, 2, length=1)
        p.hang(x, 8, 14, length=1)
    p.cobweb_corners(1, 7, 1, 15, 15, chance=0.6)
    p.roots(2, 7, 2, 14, 14, chance=0.03)
    return p, rock


def room_exit(p, tier, side):
    """The sealed exit doorway on `side` (its Gerbang Segel comes from the puzzle) and the connector to the next room."""
    pool = "kultivator:reruntuhan/%s/teka" % gua_id(tier)
    if side == "north":
        p.air(7, 2, 0, 9, 5, 0)
        p.jigsaw_side(8, 1, 0, "north", name=J_OUT, target=J_IN, pool=pool, final_state=p.b("floor"))
        door = (0, 0, -8)
    elif side == "east":
        p.air(16, 2, 7, 16, 5, 9)
        p.jigsaw_side(16, 1, 8, "east", name=J_OUT, target=J_IN, pool=pool, final_state=p.b("floor"))
        door = (8, 0, 0)
    else:
        p.air(0, 2, 7, 0, 5, 9)
        p.jigsaw_side(0, 1, 8, "west", name=J_OUT, target=J_IN, pool=pool, final_state=p.b("floor"))
        door = (-8, 0, 0)
    # a carved lintel and two glyph stones over the sealed way
    dx, _, dz = door
    lx, lz = CX + dx, CZ + dz
    if side == "north":
        for x in (6, 10):
            for y in range(2, 7):
                p.put(x, y, 1, "pillar")
        for x in range(6, 11):
            p.put(x, 6, 1, "trim")
        p.put(8, 7, 1, "accent")
    else:
        xx = 15 if side == "east" else 1
        for z in (6, 10):
            for y in range(2, 7):
                p.put(xx, y, z, "pillar")
        for z in range(6, 11):
            p.put(xx, 6, z, "trim")
        p.put(xx, 7, 8, "accent")
    return list(door), FACE_YAW[side] if side != "north" else FACE_YAW["north"]


def stele(p, x, z, facing):
    """A hint stele: plinth, carved slab and a cap (the module spawns a clickable part in front of it)."""
    p.put(x, 2, z, "trim")
    p.put(x, 3, z, "accent")
    p.put(x, 4, z, "accent")
    p.slab(x, 5, z, "slab")
    p.candles(x + (1 if facing in ("north", "south") else 0), 2, z + (1 if facing in ("east", "west") else 0))


def gua_room(tier, puzzle, variant=0):
    seed = 55200 + TIER[tier] * 100 + PUZZLES.index(puzzle) * 10 + variant
    p, rock = room_shell(tier, puzzle, seed)
    side = EXIT[puzzle]
    door, door_yaw = room_exit(p, tier, side)
    data = {"puzzle": puzzle, "tier": TIER[tier], "door": nbt.IntArray(door), "dw": 3, "dh": 4, "protect": 11,
            "stele": nbt.IntArray([-5, 0, 6])}
    stele(p, CX - 5, CZ + 6, "south")
    rng = p.rng
    if puzzle == "bagua":
        # the octagon of eight plates (radius 4) around a taiji disc; plates are entities, the pads are blocks
        pads = [(4, 0), (3, 3), (0, 4), (-3, 3), (-4, 0), (-3, -3), (0, -4), (3, -3)]
        p.disc(CX, 1, CZ, 5, "floor_inlay")
        p.ring(CX, 1, CZ, 5, "trim")
        for dx, dz in pads:
            p.put(CX + dx, 1, CZ + dz, "pillar")
        for x in range(CX - 2, CX + 3):
            for z in range(CZ - 2, CZ + 3):
                if math.hypot(x - CX, z - CZ) <= 2.5:
                    light = (x - CX) + (z - CZ) * 0.35 > 0
                    p.put(x, 1, z, "minecraft:smooth_quartz" if light else "minecraft:polished_blackstone")
        p.put(CX + 1, 1, CZ, "minecraft:polished_blackstone")
        p.put(CX - 1, 1, CZ, "minecraft:smooth_quartz")
        # eight banner-like trigram panels on the walls (accent blocks with candles)
        for x, z in ((4, 1), (12, 1), (1, 4), (1, 12), (15, 4), (15, 12), (4, 15), (12, 15)):
            if 0 < x < 16 and 0 < z < 16:
                p.put(x, 4, z if z not in (1, 15) else z, p.b("accent"))
        data["pads"] = nbt.IntArray([c for d in pads for c in (d[0], 0, d[1])])
    elif puzzle == "unsur":
        lamps = [(0, -5), (5, -2), (3, 4), (-3, 4), (-5, -2)]
        p.ring(CX, 1, CZ, 6, "floor_inlay")
        for dx, dz in lamps:
            x, z = CX + dx, CZ + dz
            p.put(x, 1, z, "trim")
            p.put(x, 2, z, "pillar")
            p.slab(x, 3, z, "slab", "bottom")
            for ox, oz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
                if p.get(x + ox, 2, z + oz) in (None, "minecraft:air"):
                    p.stair(x + ox, 2, z + oz, "stair", face)
        # the five-phase star inlaid between the pedestals
        for i in range(5):
            a = lamps[i]
            b = lamps[(i + 2) % 5]
            steps = 12
            for s in range(steps + 1):
                x = round(CX + a[0] + (b[0] - a[0]) * s / steps)
                z = round(CZ + a[1] + (b[1] - a[1]) * s / steps)
                if p.get(x, 1, z) == p.b("floor"):
                    p.put(x, 1, z, "gold" if tier != "kecil" else "metal")
        data["lamps"] = nbt.IntArray([c for d in lamps for c in (d[0], 1, d[1])])
    elif puzzle == "bintang":
        # star map: three concentric rings of pads (radius 2, 4, 6) and the Beidou mural on the ceiling
        for r, role in ((2, "trim"), (4, "floor_inlay"), (6, "trim")):
            p.ring(CX, 1, CZ, r, role)
        p.put(CX, 1, CZ, "light_block")
        for x in range(2, 15):
            for z in range(2, 15):
                if p.get(x, 9, z) == p.b("ceiling") and rng.random() < 0.05:
                    p.put(x, 9, z, "minecraft:sea_lantern" if rng.random() < 0.3 else "minecraft:end_stone_bricks")
        # the seven stars of the Dipper as lit blocks in the ceiling (the target star is a client display)
        dipper = [(-5, -4), (-3, -5), (-1, -4), (0, -2), (2, -1), (4, -2), (5, 0)]
        for dx, dz in dipper:
            p.put(CX + dx, 9, CZ + dz, "light_block")
        data["rings"] = nbt.IntArray([2, 4, 6])
    elif puzzle == "ilusi":
        # a 15 x 15 maze (7 x 7 cells) carved by a seeded depth-first walk from the entry (south) to the exit (north)
        maze_rng = random.Random(seed)
        n = 7
        seen = [[False] * n for _ in range(n)]
        walls = {}
        for gx in range(15):
            for gz in range(15):
                walls[(gx, gz)] = (gx % 2 == 0 or gz % 2 == 0)
        stack = [(3, 6)]
        seen[3][6] = True
        while stack:
            cx, cz = stack[-1]
            nexts = [(cx + dx, cz + dz, dx, dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                     if 0 <= cx + dx < n and 0 <= cz + dz < n and not seen[cx + dx][cz + dz]]
            if not nexts:
                stack.pop()
                continue
            nx, nz, dx, dz = maze_rng.choice(nexts)
            walls[(2 * cx + 1 + dx, 2 * cz + 1 + dz)] = False
            seen[nx][nz] = True
            stack.append((nx, nz))
        walls[(7, 14)] = False   # entry gap (south, by the doorway)
        walls[(7, 0)] = False    # exit gap (north)
        # the solution path (BFS) gets the phantom walls: one in three of its open wall-cells
        from collections import deque
        start, goal = (7, 13), (7, 1)
        prev = {start: None}
        q = deque([start])
        while q:
            c = q.popleft()
            if c == goal:
                break
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nc = (c[0] + dx, c[1] + dz)
                if 0 <= nc[0] < 15 and 0 <= nc[1] < 15 and not walls[nc] and nc not in prev:
                    prev[nc] = c
                    q.append(nc)
        path = []
        c = goal
        while c is not None:
            path.append(c)
            c = prev.get(c)
        phantom = [c for c in path if (c[0] % 2 == 0) != (c[1] % 2 == 0)][::3]
        dead = []
        for gx in range(1, 15, 2):
            for gz in range(1, 15, 2):
                if (gx, gz) in path:
                    continue
                openings = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if not walls.get((gx + dx, gz + dz), True))
                if openings == 1:
                    dead.append((gx, gz))
        for (gx, gz), wall in walls.items():
            x, z = gx + 1, gz + 1
            if wall:
                for y in (2, 3, 4):
                    p.put(x, y, z, "wall" if rng.random() < 0.8 else "wall_alt")
                p.slab(x, 5, z, "slab", "bottom") if rng.random() < 0.3 else None
        # light every dead end with a pressure-plate pad (formation pulses hit there)
        for gx, gz in dead:
            p.put(gx + 1, 1, gz + 1, "floor_inlay")
        p.put(CX, 1, 2, "light_block")
        stele(p, CX - 5, CZ + 6, "south")
        data["walls"] = nbt.IntArray([c for gx, gz in phantom for c in (gx + 1 - CX, gz + 1 - CZ)])
        data["dead"] = nbt.IntArray([c for gx, gz in dead[:8] for c in (gx + 1 - CX, gz + 1 - CZ)])
        data["exit"] = nbt.IntArray([0, -6])
    elif puzzle == "cermin":
        # the mirror of the heart: a tall frame of quartz and glass on the far wall, a round duelling floor
        p.disc(CX, 1, CZ, 6, "floor_inlay")
        p.ring(CX, 1, CZ, 6, "trim")
        p.ring(CX, 1, CZ, 3, "gold" if tier != "kecil" else "metal")
        mx = {"east": (15, None), "north": (None, 1), "west": (1, None)}
        # the mirror stands opposite the exit side's left wall: always on the west wall (x = 1) unless the exit is there
        wall_x = 15 if side == "west" else 1
        for z in range(5, 12):
            for y in range(2, 8):
                edge = z in (5, 11) or y in (2, 7)
                p.put(wall_x, y, z, "minecraft:quartz_pillar" if edge else "minecraft:light_gray_stained_glass_pane",
                      {"axis": "y"} if edge else None)
        data["mirror"] = nbt.IntArray([wall_x - CX + (1 if wall_x == 1 else -1), 0, 0])
    elif puzzle == "timbangan":
        # two pans (3 x 3 pads) on either side of a bronze fulcrum, the balance beam is a display entity
        for sx in (-4, 4):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    p.put(CX + sx + dx, 1, CZ + dz, "gold" if (dx == 0 and dz == 0) else "floor_inlay")
            p.ring(CX + sx, 1, CZ, 2, "trim")
        for y in range(2, 6):
            p.put(CX, y, CZ, "minecraft:waxed_oxidized_copper" if y < 5 else "minecraft:waxed_oxidized_cut_copper")
        p.put(CX, 1, CZ, "trim")
        for ox, oz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            p.stair(CX + ox, 2, CZ + oz, "stair", face)
        data["pans"] = nbt.IntArray([-4, 0, 4, 0])
        data["weight"] = 4 + TIER[tier] + rng.randint(0, 2)
    # decor common to every room: chest in a corner for tier 2-3, rubble, candles; then the room's look
    theme = "batu" if variant == 0 else THEME_B[puzzle]
    if tier != "kecil" and puzzle in ("bagua", "bintang", "timbangan"):
        p.chest(14, 2, 14, "north", loot_id(tier + "_kamar"))
    room_dress(p, tier, puzzle, side, theme)
    if puzzle != "ilusi":
        if theme != "taman":
            p.rubble(13, 2, 3, size=1, kind=rock)
        for x, z in ((3, 13), (13, 13)):
            if p.free(x, 2, z):
                p.candles(x, 2, z)
    data["seed"] = seed
    data["look"] = THEMES_LOOK.index(theme)
    p.anchor_at(CX, CY, CZ, "reruntuhan/teka", yaw=0.0, **data)
    p.weather(0.05 if theme != "pustaka" else 0.03)
    return finish(p, "%s/kamar_%s%s" % (gua_id(tier), puzzle, "" if variant == 0 else "_b"))


# the second look of every puzzle room (variant b): a library, a shrine or a garden grotto
THEME_B = {"bagua": "pustaka", "unsur": "kuil", "bintang": "taman", "ilusi": "taman", "cermin": "kuil", "timbangan": "pustaka"}
THEMES_LOOK = ["batu", "pustaka", "kuil", "taman"]
# inner face of each wall of a room box (x 0..16, z 0..16): (fixed axis, fixed value, facing into the room)
ROOM_WALLS = {"west": ("x", 1, "east"), "east": ("x", 15, "west"), "north": ("z", 1, "south"), "south": ("z", 15, "north")}


def room_dress(p, tier, puzzle, side, theme):
    """Dresses a puzzle room after its puzzle layout is built, never inside the puzzle's floor (radius 6.8 round the
    centre; the whole interior below y 6 for the maze), never on the exit wall, never over a built block.

    batu: the plain ruin (banners of the founder's sect, bracket lanterns, cobwebs);
    pustaka: a library: the side walls become shelves of chiseled bookshelves, lecterns in the corners, a lantern over
             the formation, scroll banners;
    kuil: a shrine: an altar of candles and pots on a free side wall, soul braziers in the corners, red and black
          banners, incense smoke;
    taman: a garden grotto: moss over the stone, a lily pool in a corner, potted azaleas, glow lichen on the walls,
           froglights set into the coffers and spore blossoms dusting the air."""
    maze = puzzle == "ilusi"
    mirror = ("east" if side == "west" else "west") if puzzle == "cermin" else None
    free_walls = [w for w in ("west", "north", "east", "south") if w != side and w != mirror]
    side_walls = [w for w in free_walls if w != "south"]

    def inner(wall, along, y):
        axis, fixed, _ = ROOM_WALLS[wall]
        return (fixed, y, along) if axis == "x" else (along, y, fixed)

    def outer(wall, along, y):
        axis, fixed, _ = ROOM_WALLS[wall]
        return ((0 if fixed == 1 else 16), y, along) if axis == "x" else (along, y, (0 if fixed == 1 else 16))

    def outside_puzzle(x, z):
        return math.hypot(x - CX, z - CZ) > 6.8

    def floor_cell(x, z):
        return p.get(x, 1, z) == p.b("floor")

    def near_exit(x, z):
        if side == "north":
            return z <= 2 and 5 <= x <= 11
        if side == "east":
            return x >= 14 and 5 <= z <= 11
        return x <= 2 and 5 <= z <= 11

    # banners at head height on every free wall (the maze's outer walls end at y 5)
    for wall in free_walls:
        face = ROOM_WALLS[wall][2]
        for along in (6, 10):
            x, y, z = inner(wall, along, 6)
            if theme == "kuil":
                p.banner(x, y, z, face, "banner" if along == 6 else "banner_alt")
            elif theme == "pustaka":
                p.banner(x, y, z, face, "white" if along == 6 else "banner_alt")
            elif theme == "batu" and p.rng.random() < 0.75:
                p.banner(x, y, z, face, "banner" if p.rng.random() < 0.6 else "banner_alt")
        if theme in ("batu", "kuil") and wall != "south":
            x, y, z = inner(wall, 8, 7)
            p.bracket_lantern(x, y, z, _OPP[face])
    if theme == "pustaka" and not maze:
        # the side walls become shelves between their pillars (the wall stays closed: shelves are full blocks)
        for wall in side_walls:
            face = ROOM_WALLS[wall][2]
            for along in range(1, 16):
                if along % 4 == 0:
                    continue
                for y in (2, 3, 4):
                    x, yy, z = outer(wall, along, y)
                    if p.get(x, yy, z) in (p.b("wall"), p.b("wall_alt"), p.b("wall_crack")):
                        if p.rng.random() < 0.55:
                            p.put(x, yy, z, "minecraft:chiseled_bookshelf", {"facing": face})
                        else:
                            p.put(x, yy, z, "minecraft:bookshelf")
        for x, z, face in ((1, 1, "south"), (15, 1, "south"), (1, 15, "north"), (15, 15, "north")):
            if p.free(x, 2, z) and outside_puzzle(x, z) and not near_exit(x, z):
                p.put(x, 2, z, "minecraft:lectern", {"facing": face, "has_book": "false", "powered": "false"})
        if p.free(CX, 5, CZ) and p.free(CX, 6, CZ):
            p.hang(CX, 9, CZ, length=3)
    if theme == "kuil":
        # an altar on the first free side wall: a stone table, candles, pots and a skull of the last guardian
        wall = side_walls[0] if side_walls else None
        if wall and not maze:
            face = ROOM_WALLS[wall][2]
            for along in (7, 8, 9):
                x, y, z = inner(wall, along, 2)
                if p.free(x, y, z):
                    p.slab(x, y, z, "slab", "top")
                    deco = {7: "candle", 8: "pot", 9: "candle"}[along]
                    if deco == "candle":
                        p.candles(x, 3, z, n=p.rng.randint(2, 4))
                    else:
                        p.put(x, 3, z, "minecraft:decorated_pot", {"facing": face, "cracked": "false", "waterlogged": "false"})
            x, y, z = inner(wall, 8, 5)
            p.banner(x, y, z, face, "banner")
        if not maze:
            for x, z in ((2, 2), (14, 2), (2, 14), (14, 14)):
                if outside_puzzle(x, z) and not near_exit(x, z):
                    p.brazier(x, 2, z, soul=True)
    if theme == "taman":
        keep = (lambda x, z: not floor_cell(x, z)) if maze else (lambda x, z: not floor_cell(x, z) or not outside_puzzle(x, z))
        p.moss_floor(1, 2, 1, 15, 15, 0.22 if maze else 0.4, keep=keep)
        if not maze:
            p.pond(13, 1, 14, 2, 1)
            for x, z in ((1, 1), (15, 1), (1, 15), (15, 15), (1, 8), (15, 8)):
                if outside_puzzle(x, z) and not near_exit(x, z):
                    p.plant(x, 2, z, p.rng.choice(["minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush",
                                                   p.pal.get("plant", "minecraft:potted_fern")]))
        for cx, cz in ((2, 2), (14, 14), (2, 14), (14, 2), (6, 10), (10, 6)):
            if p.get(cx, 9, cz) == p.b("ceiling"):
                p.put(cx, 9, cz, "minecraft:verdant_froglight" if tier == "kecil" else "minecraft:ochre_froglight" if tier == "agung"
                      else "minecraft:pearlescent_froglight", {"axis": "y"})
        p.spore(10, 8, 10)
        p.spore(6, 8, 6)
        for _ in range(26):
            wall = p.rng.choice(["west", "east", "north", "south"])
            along = p.rng.randint(1, 15)
            y = p.rng.randint(6 if maze else 3, 7)
            x, yy, z = inner(wall, along, y)
            p.lichen(x, yy, z, _OPP[ROOM_WALLS[wall][2]])
        p.roots(2, 7, 2, 14, 14, chance=0.05)
    if theme == "batu":
        p.cobweb_corners(1, 6, 1, 15, 15, chance=0.35)


# ================================================================================================= Gua: the final piece

def gua_final(tier, variant=0):
    """The guardian arena and the Aula Warisan (the teka pool's fallback). 23 x 14 x 40: entry z 35..39, arena
    x 1..21 z 15..34, the sealed hall door at z 15, hall x 4..18 z 1..14 with the seated ancestor (bone) on a dais.

    variant 0 "Aula Dupa": the dais under the coffered dome, bookcases, banners of the founder's Dao and incense.
    variant 1 "Aula Taman": the ancestor sits in a little pavilion with a curled hip roof and wind bells, the hall floor
    is a garden of moss, lily pools and potted azaleas round a stepping-stone path; the arena keeps hanging soul
    lanterns on chains over a cracked ring. Both keep every anchor in the same place."""
    pal = PAL[tier]
    rock = "mountain" if tier == "agung" else "badlands" if tier == "kuno" else "hill"
    p = RPiece(23, 14, 40, seed=55300 + TIER[tier] + 10 * variant, palette=pal)
    p.rock_fill(0, 0, 0, 22, 13, 39, rock, only_empty=False)
    # entry corridor
    p.fill(8, 0, 35, 14, 0, 39, "floor_alt")
    p.fill(9, 1, 35, 13, 1, 39, "path")
    for z in range(35, 40):
        for x in (8, 14):
            for y in range(1, 7):
                p.put(x, y, z, "wall")
        p.fill(8, 7, z, 14, 7, z, "ceiling")
        p.air(9, 2, z, 13, 6, z)
    p.jigsaw_side(11, 1, 39, "south", name=J_IN, target=J_OUT, pool="minecraft:empty", final_state=p.b("path"))
    # arena: round-cornered hall with eight columns and a sunken ring
    p.fill(0, 0, 15, 22, 0, 35, "floor_alt")
    p.fill(1, 1, 16, 21, 1, 34, "floor")
    p.walls(0, 2, 15, 22, 11, 35, "wall", pillar="pillar")
    p.fill(0, 12, 15, 22, 12, 35, "ceiling")
    p.air(10, 2, 35, 12, 6, 35)
    acx, acz = 11, 25
    p.ring(acx, 1, acz, 8, "trim")
    p.ring(acx, 1, acz, 5, "floor_inlay")
    p.disc(acx, 1, acz, 2, "floor_inlay")
    p.put(acx, 1, acz, "accent")
    if variant == 1:
        # a cracked fighting ring between the inlays
        for x in range(acx - 7, acx + 8):
            for z in range(acz - 7, acz + 8):
                d = math.hypot(x - acx, z - acz)
                if 5.6 < d < 7.4 and p.get(x, 1, z) == p.b("floor") and p.rng.random() < 0.55:
                    p.put(x, 1, z, pal.get("floor_alt2", pal["floor_alt"]))
    for a in range(8):
        ang = a * math.pi / 4 + math.pi / 8
        x = round(acx + 8.5 * math.cos(ang))
        z = round(acz + 8.5 * math.sin(ang))
        if 1 <= x <= 21 and 16 <= z <= 34:
            p.column(x, z, 2, 10, base="stair", cap="stair")
    for x in (2, 20):
        for z in (17, 33):
            p.hang(x, 11, z, length=2)
    p.hang(acx, 12, acz, length=4)
    # broken statues of past guardians along the walls
    for z in (19, 31):
        for x in (2, 20):
            p.put(x, 2, z, "trim")
            p.put(x, 3, z, "wall_alt")
            p.put(x, 4, z, "minecraft:skeleton_skull" if tier == "kuno" else p.b("accent"),
                  {"rotation": "4" if x == 2 else "12"} if tier == "kuno" else None)
    # war banners of the guardian's creator high on the walls, braziers at the middle of each side
    for z in (21, 29):
        p.banner(1, 8, z, "east", "banner")
        p.banner(21, 8, z, "west", "banner")
    for x in (6, 16):
        p.banner(x, 8, 34, "north", "banner_alt")
    p.brazier(2, 2, 25, soul=True)
    p.brazier(20, 2, 25, soul=True)
    if variant == 1:
        # soul lanterns hanging on long chains between the columns (the guardian fights under them)
        for a in range(4):
            ang = a * math.pi / 2 + math.pi / 4
            x = round(acx + 6.5 * math.cos(ang))
            z = round(acz + 6.5 * math.sin(ang))
            if p.free(x, 9, z):
                p.lantern(x, 12, z, chain=4, role="glow")
        p.bones(5, 2, 31, "east")
        p.bones(17, 2, 19, "west")
    p.rubble(4, 2, 28, size=2, kind=rock)
    p.rubble(18, 2, 21, size=1, kind=rock)
    p.cobweb_corners(1, 10, 16, 21, 34, chance=0.8)
    p.roots(2, 11, 17, 20, 33, chance=0.04)
    # the sealed door to the hall (z = 15)
    p.air(10, 2, 15, 12, 6, 15)
    for x in (9, 13):
        for y in range(2, 8):
            p.put(x, y, 15, "pillar")
            p.put(x, y, 16, "trim" if y == 7 else "minecraft:air")
    for x in range(9, 14):
        p.put(x, 7, 15, "trim")
    p.fill(10, 8, 15, 12, 8, 15, "accent")
    p.put(11, 9, 16, "gold")
    p.anchor_at(acx, 2, acz, "reruntuhan/penjaga", yaw=0.0, tier=TIER[tier], protect=16,
                door=nbt.IntArray([0, 0, 15 - acz]), dw=3, dh=5, arena=10)
    # ---------------------------------------------------------------- the Aula Warisan
    p.fill(3, 0, 1, 19, 0, 15, "floor_alt")
    p.fill(4, 1, 2, 18, 1, 14, "floor")
    p.walls(3, 2, 1, 19, 10, 15, "wall", pillar="pillar", interior_air=True)
    p.air(10, 2, 15, 12, 6, 15)
    p.fill(3, 11, 1, 19, 11, 15, "ceiling")
    # stepped coffer and a dome of light
    for x in range(5, 18):
        for z in range(3, 14):
            if x in (5, 17) or z in (3, 13):
                p.put(x, 10, z, "trim")
    p.fill(8, 11, 6, 14, 11, 10, "minecraft:air")
    p.fill(8, 12, 6, 14, 12, 10, "light_block" if tier != "kecil" else "ceiling")
    p.put(11, 12, 8, "light_block")
    if variant == 0:
        # the carpet runner to the dais
        for z in range(5, 15):
            p.put(11, 1, z, "floor_inlay")
            if z > 6:
                for x in (10, 11, 12):
                    p.put(x, 2, z, "carpet")
    else:
        # a stepping-stone path through a moss garden with two lily pools
        for z in range(5, 15):
            p.put(11, 1, z, "floor_inlay" if z % 2 else "floor")
        p.pond(6, 11, 8, 13, 1)
        p.pond(14, 11, 16, 13, 1)
        for x, z in ((6, 7), (16, 7), (9, 13), (13, 13), (6, 9), (16, 9)):
            p.plant(x, 2, z, p.rng.choice(["minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush", "minecraft:potted_bamboo"]))
        p.moss_floor(5, 2, 6, 17, 14, 0.45, keep=lambda x, z: abs(x - 11) <= 1 or p.get(x, 1, z) != p.b("floor"))
    # dais (two steps) and the seated ancestor of bone facing the door (south)
    p.fill(7, 2, 2, 15, 2, 5, "trim")
    p.fill(8, 3, 2, 14, 3, 4, "floor_inlay")
    for x in range(7, 16):
        p.stair(x, 2, 6, "stair", "north")
    for x in range(8, 15):
        p.stair(x, 3, 5, "stair", "north")
    # the remnant body: crossed legs, upright spine, shoulders, skull
    p.put(10, 4, 3, "minecraft:bone_block", {"axis": "x"})
    p.put(11, 4, 3, "minecraft:bone_block", {"axis": "x"})
    p.put(12, 4, 3, "minecraft:bone_block", {"axis": "x"})
    p.put(11, 4, 2, "minecraft:bone_block", {"axis": "y"})
    p.put(11, 5, 2, "minecraft:bone_block", {"axis": "y"})
    p.put(10, 6, 2, "minecraft:bone_block", {"axis": "x"})
    p.put(12, 6, 2, "minecraft:bone_block", {"axis": "x"})
    p.put(11, 6, 2, "minecraft:bone_block", {"axis": "y"})
    p.put(11, 7, 2, "minecraft:skeleton_skull", {"rotation": "0"})
    p.put(10, 5, 3, "minecraft:bone_block", {"axis": "z"})
    p.put(12, 5, 3, "minecraft:bone_block", {"axis": "z"})
    p.candles(9, 4, 4, n=3)
    p.candles(13, 4, 4, n=3)
    p.put(8, 4, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    p.put(14, 4, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "true", "waterlogged": "false"})
    if variant == 1:
        # the pavilion over the dais: four posts, beams, a curled hip roof that merges into the ceiling, wind bells
        post = "minecraft:crimson_stem" if tier == "kuno" else "minecraft:stripped_dark_oak_log" if tier == "kecil" else "minecraft:stripped_spruce_log"
        for x in (7, 15):
            for z in (2, 5):
                for y in range(3, 9):
                    p.put(x, y, z, post, {"axis": "y"})
        for x in range(7, 16):
            p.put(x, 9, 2, "minecraft:stripped_dark_oak_log", {"axis": "x"})
            p.put(x, 9, 5, "minecraft:stripped_dark_oak_log", {"axis": "x"})
        for z in (3, 4):
            p.put(7, 9, z, "minecraft:stripped_dark_oak_log", {"axis": "z"})
            p.put(15, 9, z, "minecraft:stripped_dark_oak_log", {"axis": "z"})
        p.roof_hip(7, 10, 2, 15, 5, role="roof", slab_role="roof_slab", full_role="roof_full", overhang=1, eave_curl=True)
        p.bell(6, 9, 6, "south")
        p.bell(16, 9, 6, "south")
        p.banner(7, 7, 6, "south", "banner")
        p.banner(15, 7, 6, "south", "banner")
    else:
        # incense before the dais and the Dao banners of the ancestor on the side walls
        p.brazier(6, 2, 7, soul=True)
        p.brazier(16, 2, 7, soul=True)
        for z in (6, 10):
            p.banner(4, 7, z, "east", "banner")
            p.banner(18, 7, z, "west", "banner")
    # side alcoves: bookcases, treasure chests, lantern shrines
    for x, face in ((4, "east"), (18, "west")):
        for z in (4, 8, 12):
            p.put(x, 2, z, "minecraft:chiseled_bookshelf", {"facing": face})
            p.put(x, 3, z, "minecraft:chiseled_bookshelf", {"facing": face})
        p.chest(x + (1 if x == 4 else -1), 2, 10, face, loot_id(tier + "_warisan"))
    p.hang(6, 11, 4, length=2)
    p.hang(16, 11, 4, length=2)
    p.hang(6, 11, 12, length=2)
    p.hang(16, 11, 12, length=2)
    p.lichen(4, 8, 14, "south")
    p.lichen(18, 9, 13, "east")
    theme = THEMES[tier][0]
    p.anchor_at(11, 2, 8, "reruntuhan/warisan", yaw=0.0, tier=TIER[tier], protect=12, theme=theme,
                seat=nbt.IntArray([0, 2, -5]))
    p.weather(0.05)
    return finish(p, gua_id(tier) + ("/akhir" if variant == 0 else "/akhir_b"))


# ================================================================================================= Penjara Iblis Kuno

# the two prison looks: the mountain prison and the badlands prison (same plan, anchors and connectors)
PRISON_FAMS = {"": "penjara", "_gurun": "penjara_gurun"}


def prison_shrine(fam=""):
    """Kuil Segel (start piece, projected to the surface with start_height -12): a stone plinth 29 x 29 with five banded
    seal pillars in a pentagon round a sealed well, a hip-roofed pavilion over the well and a spiral stair going down to
    the shaft connector at the bottom. Floor (template y 13) = the surface.

    Detail pass: a paifang of the sealing sect over the south approach with guardian lions, incense braziers between the
    pillars, talisman banners and wind bells on the pavilion, the plinth stairs of the biome's stone."""
    pal = PAL[PRISON_FAMS[fam]]
    p = RPiece(29, 34, 29, seed=62001 + (50 if fam else 0), palette=pal)
    C = 14
    FY = 13
    # the plinth: a solid stone skirt from y 0 up to the floor, stepped at the edge
    for x in range(29):
        for z in range(29):
            d = max(abs(x - C), abs(z - C))
            top = FY if d <= 12 else FY - 1 if d <= 13 else FY - 3
            for y in range(0, top + 1):
                if d <= 12 and y == top:
                    continue
                p.put(x, y, z, "plinth" if p.rng.random() < 0.8 else "plinth_alt")
            if d == 13:
                p.stair(x, FY - 1, z, "plinth_stair",
                        "south" if z == 1 else "north" if z == 27 else "east" if x == 1 else "west",
                        shape="straight")
    # platform floor with a pentagram inlay
    for x in range(2, 27):
        for z in range(2, 27):
            if max(abs(x - C), abs(z - C)) <= 12:
                p.put(x, FY, z, "floor" if (x + z) % 2 else "floor_alt")
    p.ring(C, FY, C, 11, "trim")
    p.ring(C, FY, C, 7, "floor_inlay")
    pillars = []
    for i in range(5):
        ang = -math.pi / 2 + i * 2 * math.pi / 5
        x = round(C + 9 * math.cos(ang))
        z = round(C + 9 * math.sin(ang))
        pillars.append((x, z))
    for i in range(5):
        a, b = pillars[i], pillars[(i + 2) % 5]
        for s in range(19):
            x = round(a[0] + (b[0] - a[0]) * s / 18)
            z = round(a[1] + (b[1] - a[1]) * s / 18)
            if math.hypot(x - C, z - C) > 3.5:
                p.put(x, FY, z, "accent")
    # five seal pillars: banded, capped, with a talisman anchor on the face toward the well
    for i, (x, z) in enumerate(pillars):
        for y in range(FY + 1, FY + 9):
            p.put(x, y, z, "pillar" if y % 3 else "gold")
        for dx, dz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            p.stair(x + dx, FY + 1, z + dz, "stair", face)
            p.stair(x + dx, FY + 8, z + dz, "stair", face, half="top")
        p.put(x, FY + 9, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    # the sealed well: a round curb with iron bars and chains, the stair inside
    p.ring(C, FY + 1, C, 3, "wallb")
    for x in range(C - 3, C + 4):
        for z in range(C - 3, C + 4):
            if math.hypot(x - C, z - C) <= 2.6:
                for y in range(0, FY + 1):
                    p.put(x, y, z, "minecraft:air")
    # spiral stair around a chain column, from the floor down to y 1
    ring = [(1, -2), (2, -1), (2, 0), (2, 1), (1, 2), (0, 2), (-1, 2), (-2, 1), (-2, 0), (-2, -1), (-1, -2), (0, -2)]
    faces = ["east", "south", "south", "south", "west", "west", "west", "north", "north", "north", "east", "east"]
    y = FY
    i = 0
    while y > 1:
        dx, dz = ring[i % len(ring)]
        p.stair(C + dx, y, C + dz, "stair", faces[i % len(faces)])
        for yy in range(1, y):
            pass
        y -= 1
        i += 1
    for yy in range(0, FY + 6):
        p.put(C, yy, C, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    p.jigsaw_down(C + 1, 0, C, name="kultivator:penjara/turun", target="kultivator:penjara/naik",
                  pool="kultivator:penjara/sumur" + fam, final_state="minecraft:air")
    # the pavilion over the well: four lacquer posts and a two-tier hip roof with curled eaves
    for x, z in ((C - 4, C - 4), (C + 4, C - 4), (C - 4, C + 4), (C + 4, C + 4)):
        for yy in range(FY + 1, FY + 7):
            p.put(x, yy, z, "post", {"axis": "y"})
    for x in range(C - 4, C + 5):
        p.put(x, FY + 7, C - 4, "beam", {"axis": "x"})
        p.put(x, FY + 7, C + 4, "beam", {"axis": "x"})
    for z in range(C - 4, C + 5):
        p.put(C - 4, FY + 7, z, "beam", {"axis": "z"})
        p.put(C + 4, FY + 7, z, "beam", {"axis": "z"})
    top = p.roof_hip(C - 4, FY + 8, C - 4, C + 4, C + 4, role="roof", slab_role="roof_slab", overhang=2, tiers=2,
                     tier_gap=2, tier_band=2, band_role="band")
    p.put(C, min(33, top), C, "gold")
    for x, z in ((C - 3, C - 3), (C + 3, C - 3), (C - 3, C + 3), (C + 3, C + 3)):
        p.lantern(x, FY + 7, z, chain=1)
    # a seal tablet at the north edge and stone lanterns at the plinth corners
    for x in (3, 25):
        for z in (3, 25):
            p.standing_lantern(x, FY + 1, z, post="wallb", role="glow", height=2)
    p.fill(12, FY + 1, 3, 16, FY + 1, 3, "trim")
    p.fill(13, FY + 2, 3, 15, FY + 4, 3, "accent")
    p.slab(14, FY + 5, 3, "roof_slab")
    # ---- detail pass: talisman banners on the pavilion posts (black and white strips of the sealing sect), wind bells
    for x, z in ((C - 4, C - 4), (C + 4, C - 4), (C - 4, C + 4), (C + 4, C + 4)):
        p.banner(x, FY + 5, z + (1 if z > C else -1), "south" if z > C else "north", "banner_alt")
        p.banner(x + (1 if x > C else -1), FY + 5, z, "east" if x > C else "west", "white")
    for x, z in ((C - 5, C - 5), (C + 5, C - 5), (C - 5, C + 5), (C + 5, C + 5)):
        if p.get(x, FY + 8, z) not in (None, "minecraft:air"):
            p.bell(x, FY + 7, z, "north")
    # incense braziers between the seal pillars (the south gap stays the approach)
    for i in range(5):
        ang = -math.pi / 2 + (i + 0.5) * 2 * math.pi / 5
        x = round(C + 10.5 * math.cos(ang))
        z = round(C + 10.5 * math.sin(ang))
        if z < C + 8:
            p.brazier(x, FY + 1, z, soul=True)
    # the paifang of the sealing sect over the south approach, guardian lions and paired stone lanterns before it
    p.paifang(C, FY + 1, 25, half=4, height=6, post="post", beam="beam")
    p.lion(C - 3, FY + 1, 26, "south")
    p.lion(C + 3, FY + 1, 26, "south")
    for x in (C - 6, C + 6):
        p.toro(x, FY + 1, 23, post="wallb", role="glow")
    # potted plants of the biome by the tablet, a drift of torn seal paper on the floor (white carpet scraps)
    for x in (11, 17):
        p.plant(x, FY + 1, 4)
    for _ in range(7):
        x = p.rng.randint(5, 23)
        z = p.rng.randint(5, 23)
        if math.hypot(x - C, z - C) > 4.5 and p.free(x, FY + 1, z) and p.get(x, FY, z) not in (None, "minecraft:air"):
            p.put(x, FY + 1, z, "minecraft:white_carpet")
    p.anchor_at(C, FY + 1, 4, "penjara/situs", yaw=0.0, situs="penjara/penjara", tier=6, protect=0)
    p.anchor_at(C, FY + 1, C, "penjara/kuil", yaw=0.0, protect=16,
                pillars=nbt.IntArray([c for x, z in pillars for c in (x - C, 2, z - C)]))
    # clear the air above the platform (the plinth top is the surface)
    for x in range(2, 27):
        for z in range(2, 27):
            for yy in range(FY + 1, 34):
                if p.get(x, yy, z) is None:
                    p.put(x, yy, z, "minecraft:air")
    p.weather(0.07, pairs={"minecraft:stone_bricks": ("minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
                           "minecraft:polished_blackstone_bricks": ("minecraft:cracked_polished_blackstone_bricks",)})
    return finish(p, "kuil" + fam)


def prison_shaft(fam=""):
    """Sumur Penjara: a 40 block spiral stair shaft (11 x 40 x 11) with chains, soul lanterns and talisman niches."""
    pal = PAL[PRISON_FAMS[fam]]
    H = 40
    p = RPiece(11, H, 11, seed=62002 + (50 if fam else 0), palette=pal)
    C = 5
    for y in range(H):
        for x in range(11):
            for z in range(11):
                d = math.hypot(x - C, z - C)
                if d <= 3.6:
                    p.put(x, y, z, "minecraft:air")
                elif d <= 5.2:
                    p.put(x, y, z, "wall" if p.rng.random() < 0.75 else "wall_alt" if p.rng.random() < 0.5 else "wall_crack")
    ring = [(2, -2), (3, -1), (3, 0), (3, 1), (2, 2), (1, 3), (0, 3), (-1, 3), (-2, 2), (-3, 1), (-3, 0), (-3, -1),
            (-2, -2), (-1, -3), (0, -3), (1, -3)]
    i = 0
    for y in range(H - 1, 0, -1):
        dx, dz = ring[i % len(ring)]
        nx, nz = ring[(i + 1) % len(ring)]
        face = "north" if nz < dz else "south" if nz > dz else "west" if nx < dx else "east"
        # the stair steps down toward the next ring cell: its back (facing side) is the higher step
        back = {"north": "south", "south": "north", "east": "west", "west": "east"}[face]
        p.stair(C + dx, y, C + dz, "stair", back)
        p.put(C + dx, y - 1, C + dz, "floor")
        i += 1
    for y in range(0, H):
        p.put(C, y, C, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    for y in range(4, H, 8):
        for dx, dz in ((4, 0), (-4, 0), (0, 4), (0, -4)):
            p.put(C + dx, y, C + dz, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"}) if (y // 8) % 2 == 0 else \
                p.put(C + dx, y, C + dz, "minecraft:air")
    p.jigsaw_up(C + 1, H - 1, C, name="kultivator:penjara/naik", target="kultivator:penjara/turun", pool="minecraft:empty",
                final_state="minecraft:air")
    p.jigsaw_down(C + 1, 0, C, name="kultivator:penjara/turun", target="kultivator:penjara/naik",
                  pool="kultivator:penjara/aula" + fam, final_state="minecraft:air")
    # detail pass: talisman strips on the shaft wall every 6 blocks and cobwebs in the cold stone
    for y in range(7, H - 2, 6):
        for dx, dz, face in ((3, 2, "west"), (-3, -2, "east"), (2, -3, "south"), (-2, 3, "north")):
            x, z = C + dx, C + dz
            ox, oz = {"west": (1, 0), "east": (-1, 0), "south": (0, -1), "north": (0, 1)}[face]
            if p.free(x, y, z) and p.get(x + ox, y, z + oz) not in (None, "minecraft:air"):
                p.banner(x, y, z, face, "white" if (y // 6) % 2 else "banner_alt")
    for y in range(3, H - 3, 5):
        dx, dz = p.rng.choice(((3, 3), (-3, 3), (3, -3), (-3, -3)))
        if p.free(C + dx, y, C + dz):
            p.put(C + dx, y, C + dz, "minecraft:cobweb")
    return finish(p, "sumur" + fam)


def prison_hall(fam=""):
    """Aula Rantai: the ring hall 47 x 18 x 47 round the sealed pit (radius 7) with the Jantung Segel at its centre,
    five re-lightable seal pillars on the ring, four barred cells of lesser demons, hanging chains converging on the
    pit and veins of crying obsidian. The shaft lands on the north walkway."""
    pal = PAL[PRISON_FAMS[fam]]
    p = RPiece(47, 18, 47, seed=62003 + (50 if fam else 0), palette=pal)
    C = 23
    R_OUT = 21
    # shell
    for x in range(47):
        for z in range(47):
            d = math.hypot(x - C, z - C)
            if d > R_OUT + 1.5:
                continue
            p.put(x, 0, z, "floor_alt")
            p.put(x, 17, z, "ceiling")
            if d > R_OUT:
                for y in range(1, 17):
                    p.put(x, y, z, "wall" if p.rng.random() < 0.8 else "wall_crack")
            else:
                p.put(x, 1, z, "floor" if p.rng.random() < 0.85 else "floor_alt")
                for y in range(2, 17):
                    p.put(x, y, z, "minecraft:air")
    # the pit: stepped down to y -? (we stay inside the template: three steps then a dark disc of blackstone)
    for x in range(C - 8, C + 9):
        for z in range(C - 8, C + 9):
            d = math.hypot(x - C, z - C)
            if d <= 7.5:
                p.put(x, 1, z, "minecraft:air")
                p.put(x, 0, z, "minecraft:blackstone" if d > 2.5 else "accent")
    p.ring(C, 1, C, 8, "floor_inlay")
    p.ring(C, 1, C, 9, "trim")
    # the seal core: a pillar of gilded blackstone wrapped in chains in the middle of the pit
    for y in range(1, 6):
        p.put(C, y, C, "minecraft:gilded_blackstone" if y % 2 else "accent")
    p.put(C, 6, C, "minecraft:soul_campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    hall_pillars = []
    hall_cells = []
    # chains converging from the ceiling ring onto the core
    for k in range(10):
        ang = k * math.pi / 5
        for s in range(0, 9):
            r = 9 - s
            x = round(C + r * math.cos(ang))
            z = round(C + r * math.sin(ang))
            y = 16 - s
            if 0 <= x < 47 and 0 <= z < 47 and 1 <= y <= 16 and (x, z) != (C, C):
                p.put(x, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    # the five seal pillars on the ring (radius 12)
    for i in range(5):
        ang = -math.pi / 2 + i * 2 * math.pi / 5
        x = round(C + 12 * math.cos(ang))
        z = round(C + 12 * math.sin(ang))
        for y in range(2, 12):
            p.put(x, y, z, "pillar" if y % 3 else "gold")
        for dx, dz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            p.stair(x + dx, 2, z + dz, "stair", face)
            p.stair(x + dx, 11, z + dz, "stair", face, half="top")
        p.put(x, 12, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
        hall_pillars.append((x - C, 1, z - C))
    # four barred cells in the outer wall (east, south, west and north-west) with a sealed demon anchor each
    cells = [(C + 19, C, "west"), (C, C + 19, "north"), (C - 19, C, "east"), (C - 14, C - 14, "south")]
    for n, (x, z, face) in enumerate(cells):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for y in range(1, 7):
                    if 0 <= x + dx < 47 and 0 <= z + dz < 47:
                        p.put(x + dx, y, z + dz, "wall" if (abs(dx) == 2 or abs(dz) == 2 or y == 6) else "minecraft:air")
        # bars on the face toward the hall
        fdx, fdz = {"west": (-2, 0), "north": (0, -2), "east": (2, 0), "south": (0, 2)}[face]
        for t in range(-1, 2):
            for y in range(2, 6):
                bx = x + fdx + (t if fdx == 0 else 0)
                bz = z + fdz + (t if fdz == 0 else 0)
                p.put(bx, y, bz, "minecraft:iron_bars")
        p.put(x, 1, z, "minecraft:soul_soil")
        p.put(x, 2, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        p.put(x, 3, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        p.put(x + 1, 2, z, "minecraft:skeleton_skull", {"rotation": str(p.rng.randint(0, 15))})
        hall_cells.append((x - C, 1, z - C))
    # crying obsidian veins, braziers and broken chain piles round the ring
    for _ in range(40):
        ang = p.rng.random() * 2 * math.pi
        r = p.rng.uniform(15, 20.5)
        x = round(C + r * math.cos(ang))
        z = round(C + r * math.sin(ang))
        if p.get(x, 1, z) in (p.b("floor"), p.b("floor_alt")):
            p.put(x, 1, z, "accent" if p.rng.random() < 0.5 else "minecraft:magma_block")
    for i in range(8):
        ang = i * math.pi / 4 + math.pi / 8
        x = round(C + 17 * math.cos(ang))
        z = round(C + 17 * math.sin(ang))
        if p.get(x, 2, z) == "minecraft:air":
            p.put(x, 2, z, "minecraft:soul_campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
            p.put(x, 1, z, "minecraft:soul_soil")
    for i in range(12):
        ang = i * math.pi / 6
        x = round(C + 20 * math.cos(ang))
        z = round(C + 20 * math.sin(ang))
        p.lantern(x, 17, z, chain=3)
    # the walkway landing of the shaft (north side) and the connector in the ceiling
    for x in range(C - 3, C + 4):
        for z in range(C - 19, C - 13):
            p.put(x, 1, z, "floor")
            for y in range(2, 17):
                if p.get(x, y, z) != "minecraft:iron_chain":
                    p.put(x, y, z, "minecraft:air")
    p.jigsaw_up(C + 1, 17, C - 16, name="kultivator:penjara/naik", target="kultivator:penjara/turun", pool="minecraft:empty",
                final_state="minecraft:air")
    for x in range(C - 4, C + 5):
        for z in range(C - 21, C - 11):
            if math.hypot(x - (C + 1), z - (C - 16)) <= 3.6:
                p.put(x, 17, z, "minecraft:air")
    p.anchor_at(C, 1, C, "penjara/segel", yaw=0.0, protect=34, arena=22,
                pillars=nbt.IntArray([c for t in hall_pillars for c in t]), cells=nbt.IntArray([c for t in hall_cells for c in t]),
                landing=nbt.IntArray([1, 1, -16]))
    p.chest(C + 17, 2, C - 6, "west", loot_id("penjara"))
    p.chest(C - 17, 2, C + 6, "east", loot_id("penjara"))
    # ---- detail pass
    # talisman banners (black / white) on the ring wall between the hanging lanterns
    for k in range(12):
        ang = k * math.pi / 6 + math.pi / 12
        x = round(C + 20.2 * math.cos(ang))
        z = round(C + 20.2 * math.sin(ang))
        if abs(math.cos(ang)) >= abs(math.sin(ang)):
            face = "west" if math.cos(ang) > 0 else "east"
            back = (x + (1 if face == "west" else -1), z)
        else:
            face = "north" if math.sin(ang) > 0 else "south"
            back = (x, z + (1 if face == "north" else -1))
        if p.get(back[0], 10, back[1]) not in (None, "minecraft:air"):
            p.banner(x, 10, z, face, "banner_alt" if k % 2 else "white")
            p.banner(x, 8, z, face, "banner")
    # candles of the sealers at the foot of every seal pillar, bones of failed sealers in the cells
    for (dx, _, dz) in hall_pillars:
        x, z = C + dx, C + dz
        for ox, oz in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
            if p.free(x + ox, 2, z + oz):
                p.candles(x + ox, 2, z + oz, n=p.rng.randint(1, 3))
    for (dx, _, dz) in hall_cells:
        x, z = C + dx, C + dz
        p.bones(x - 1, 2, z - 1, "south")
        for ox, oz in ((-1, 1), (1, -1)):
            if p.free(x + ox, 5, z + oz):
                p.put(x + ox, 5, z + oz, "minecraft:cobweb")
    # a sealers' camp on the walkway landing: a tea table and an old lectern of seal records
    p.tea_table(C - 4, 2, C - 15, wood="dark_oak", cushion="black_carpet")
    if p.free(C + 4, 2, C - 17):
        p.put(C + 4, 2, C - 17, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    p.weather(0.06)
    return finish(p, "aula" + fam)


# ================================================================================================= Menara Seratus Lantai

PAGODA = [30, 26, 24, 22, 20, 18, 16, 14, 12]     # body width of the nine tiers (eaves stick out 2 more each side)


# the three looks of the pagoda (detail pass): the white jade tower of the plains, the cherry tower of the cherry groves
# and the mud-brick tower of the savanna. Same nine-storey plan, anchors and connectors; each has its own structure.
TOWER_FAMS = {"menara_seratus": "menara", "menara_sakura": "menara_sakura", "menara_sabana": "menara_sabana"}
# small trees of the forecourt per look: (log, leaves)
TOWER_TREES = {"menara_seratus": ("minecraft:oak_log", "minecraft:flowering_azalea_leaves"),
               "menara_sakura": ("minecraft:cherry_log", "minecraft:cherry_leaves"),
               "menara_sabana": ("minecraft:acacia_log", "minecraft:acacia_leaves")}
COURT = 12   # depth of the forecourt in front of the ground hall (south)


def small_tree(p, x, y, z, log, leaves, height=3):
    """A small ornamental tree: a trunk and a rounded crown of persistent leaves (worldgen only, never decays)."""
    for k in range(height):
        if p.free(x, y + k, z):
            p.put(x, y + k, z, log, {"axis": "y"})
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(0, 3):
                if abs(dx) + abs(dz) + dy > 3 or (abs(dx) == 2 and abs(dz) == 2):
                    continue
                if dy == 2 and (abs(dx) > 1 or abs(dz) > 1):
                    continue
                cx, cy, cz = x + dx, y + height - 1 + dy, z + dz
                if p.free(cx, cy, cz):
                    p.put(cx, cy, cz, leaves, {"distance": "1", "persistent": "true", "waterlogged": "false"})


def pagoda_tier(i, fam="menara_seratus"):
    """Tier i (0 = the ground hall) of the nine-storey pagoda. Width PAGODA[i] + eaves; each sits on the one below
    through a vertical connector at its centre (joint aligned, so every tier keeps the same facing).

    Detail pass: the ground hall gets a forecourt (COURT deep, south) with a paifang, a censer, lily pools behind carved
    railings, paired stone lanterns, small trees of the biome, standing banners and guardian lions; inside, banners,
    lecterns and tea tables. The upper storeys get a carved railing round the balcony, lanterns and wind bells hanging
    from the curled eave corners and banners under the eaves."""
    pal = PAL[TOWER_FAMS[fam]]
    body = PAGODA[i]
    w = body + 4
    h = 18 if i == 0 else 22 if i == 8 else 11
    depth = w + COURT if i == 0 else w
    p = RPiece(min(48, w), h, min(48, depth), seed=65000 + i + 1000 * list(TOWER_FAMS).index(fam), palette=pal)
    c = w // 2
    b0, b1 = 2, w - 3
    pool = lambda n: "kultivator:menara/%s/tingkat_%d" % (fam, n)  # noqa: E731
    if i == 0:
        # ground hall: plinth, terrace, the hall with the gate and the ranking stele, first eaves
        p.foundation(0, 0, w - 1, depth - 1, depth=2, role="plinth")
        p.fill(0, 2, 0, w - 1, 2, w - 1, "plinth_alt")
        for x in range(w):
            for z in range(w):
                if min(x, z, w - 1 - x, w - 1 - z) >= 1:
                    p.put(x, 2, z, "floor" if (x + z) % 2 else "floor_alt")
        for x in range(1, w - 1):
            p.stair(x, 2, 0, "stair", "south")
        p.walls(b0, 3, b0, b1, 10, b1, "wall", pillar="lacquer", windows=4, window_role="window", door=("south", 5))
        for x in range(b0 + 1, b1):
            for z in range(b0 + 1, b1):
                p.put(x, 2, z, "floor_inlay" if (abs(x - c) <= 1 or abs(z - c) <= 1) else "floor")
        # gold band and the bracket stairs under the eaves
        for x in range(b0, b1 + 1):
            for z in (b0, b1):
                p.put(x, 11, z, "gold")
            for z in range(b0, b1 + 1):
                if x in (b0, b1):
                    p.put(x, 11, z, "gold")
        p.fill(b0 + 1, 11, b0 + 1, b1 - 1, 11, b1 - 1, "ceiling")
        # the inner gate: bronze doors framed by lacquer columns on the north wall, facing the entrance
        for x in (c - 3, c + 3):
            for y in range(3, 10):
                p.put(x, y, b0 + 1, "lacquer")
        for x in range(c - 3, c + 4):
            p.put(x, 9, b0 + 1, "gold")
            p.put(x, 10, b0 + 1, "lacquer")
        p.fill(c - 2, 3, b0, c + 2, 8, b0, "minecraft:waxed_oxidized_cut_copper")
        p.anchor_at(c, 3, b0 + 2, "menara/gerbang", yaw=FACE_YAW["south"], protect=24, w=5, h=6)
        p.anchor_at(c - 6, 3, c, "menara/prasasti", yaw=0.0, protect=6)
        p.anchor_at(c, 4, c, "menara/situs", yaw=0.0, situs="menara/menara", tier=5, protect=0)
        # the ranking stele (a tall tablet on a tortoise-like base) and lantern rows
        sx, sz = c - 7, c
        p.fill(sx, 3, sz - 1, sx, 3, sz + 1, "trim")
        p.fill(sx, 4, sz, sx, 8, sz, "minecraft:chiseled_quartz_block")
        p.slab(sx, 9, sz, "slab")
        for z in range(b0 + 3, b1 - 2, 4):
            p.hang(b0 + 3, 11, z, length=2)
            p.hang(b1 - 3, 11, z, length=2)
        for x, z in ((3, 3), (w - 4, 3)):
            p.standing_lantern(x, 3, z, post="fence", role="glow", height=2)
        # ---- the hall's dressing: banners on the side walls, braziers before the gate, a reading corner and tea
        for z in (c - 4, c + 4):
            p.banner(b0 + 1, 7, z, "east", "banner")
            p.banner(b1 - 1, 7, z, "west", "banner_alt")
        p.brazier(c - 5, 3, b0 + 3, soul=False)
        p.brazier(c + 5, 3, b0 + 3, soul=False)
        for z in (c - 2, c + 2):
            p.put(b1 - 2, 3, z, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
        p.tea_table(c + 6, 3, c + 5, wood="dark_oak" if fam != "menara_sakura" else "cherry",
                    cushion="red_carpet" if fam != "menara_sabana" else "orange_carpet")
        for x, z in ((b0 + 1, b1 - 1), (b1 - 1, b1 - 1), (b0 + 1, b0 + 2), (b1 - 1, b0 + 2)):
            p.plant(x, 3, z)
        top = p.eaves(b0, 12, b0, b1, b1, layers=4, overhang=2)
        # the roof field inside is solid so the next tier stands on it
        p.fill(b0 + 2, 12, b0 + 2, b1 - 2, h - 2, b1 - 2, "roof_full")
        p.fill(b0 + 3, h - 1, b0 + 3, b1 - 3, h - 1, b1 - 3, "roof_full")
        p.jigsaw_up(c, h - 1, c, name="kultivator:menara/naik", target="kultivator:menara/turun",
                    pool=pool(2), final_state=p.b("roof_full"), joint="aligned")
        # lanterns on chains and wind bells under the four curled eave corners
        for x, z in ((b0 - 2, b0 - 2), (b1 + 2, b0 - 2), (b0 - 2, b1 + 2), (b1 + 2, b1 + 2)):
            p.lantern(x, 12, z, chain=1, role="lamp")
        # ---- the forecourt (z w .. depth-1): paving, the processional way, pools, lanterns, trees, paifang
        for x in range(w):
            for z in range(w, depth):
                edge = x in (0, w - 1) or z == depth - 1
                p.put(x, 2, z, "plinth_alt" if edge else ("court" if (x + z) % 3 else "court_alt"))
        for x in range(1, w - 1):
            p.put(x, 2, w - 1, "court")       # the old south step row becomes paving
            p.stair(x, 2, depth - 1, "plinth_stair", "north")
        for z in range(w - 1, depth - 1):
            for x in (c - 1, c, c + 1):
                p.put(x, 2, z, "floor_inlay" if x == c else "path")
        for z in range(1, w - 1):
            for x in (0, w - 1):
                p.put(x, 2, z, "plinth_alt")
        log, leaves = TOWER_TREES[fam]
        # two lily pools behind carved railings with a gap toward the way
        for x0, x1 in ((4, 10), (w - 11, w - 5)):
            p.pond(x0, w + 2, x1, w + 6, 2, lily=0.3)
            p.railing(x0 - 1, w + 1, x1 + 1, w + 1, 3)
            p.railing(x0 - 1, w + 7, x1 + 1, w + 7, 3)
            outer = x0 - 1 if x0 < c else x1 + 1
            inner = x1 + 1 if x0 < c else x0 - 1
            p.railing(outer, w + 1, outer, w + 7, 3)
            p.railing(inner, w + 1, inner, w + 7, 3, gap=(2, 4))
        # paired stone lanterns along the way, a censer in the middle of it, lions at the hall door
        for z in (w + 2, w + 6):
            p.toro(c - 3, 3, z, post="wallb", role="lamp")
            p.toro(c + 3, 3, z, post="wallb", role="lamp")
        p.brazier(c, 3, w + 4, soul=False)
        p.lion(c - 4, 3, w, "south")
        p.lion(c + 4, 3, w, "south")
        p.paifang(c, 3, depth - 3, half=4, height=7, post="lacquer", beam="beam")
        for x in (2, w - 3):
            small_tree(p, x, 3, w + 2, log, leaves, height=4)
            small_tree(p, x, 3, depth - 3, log, leaves, height=3)
        for x, rot in ((c - 6, 8), (c + 6, 8)):
            p.standing_banner(x, 3, depth - 2, pal.get("banner", "red"), rot)
        for x in (1, w - 2):
            for z in range(w + 8, depth - 1, 2):
                p.plant(x, 3, z)
        for x in range(w):
            for z in range(depth):
                for y in range(3, h):
                    if p.get(x, y, z) is None:
                        p.put(x, y, z, "minecraft:air")
        return finish(p, fam + "/tingkat_1")
    # tiers 2..9: a storey of wall with windows, a gold band, eaves; the crown has a spire of gold and pearls
    p.jigsaw_down(c, 0, c, name="kultivator:menara/turun", target="kultivator:menara/naik", pool="minecraft:empty",
                  final_state=p.b("roof_full"), joint="aligned")
    storey = 4
    p.fill(b0, 0, b0, b1, 0, b1, "floor")
    p.walls(b0, 1, b0, b1, storey, b1, "wall", pillar="lacquer", windows=3, window_role="window")
    for x in range(b0, b1 + 1):
        for z in range(b0, b1 + 1):
            if x in (b0, b1) or z in (b0, b1):
                p.put(x, storey + 1, z, "gold")
    p.fill(b0 + 1, storey + 1, b0 + 1, b1 - 1, storey + 1, b1 - 1, "ceiling")
    # the balcony ring with a carved railing (posts at the corners and every third block)
    for x in range(b0 - 1, b1 + 2):
        for z in (b0 - 1, b1 + 1):
            p.put(x, 0, z, "floor_alt")
    for z in range(b0 - 1, b1 + 2):
        for x in (b0 - 1, b1 + 1):
            p.put(x, 0, z, "floor_alt")
    p.railing(b0 - 1, b0 - 1, b1 + 1, b0 - 1, 1)
    p.railing(b0 - 1, b1 + 1, b1 + 1, b1 + 1, 1)
    p.railing(b0 - 1, b0 - 1, b0 - 1, b1 + 1, 1)
    p.railing(b1 + 1, b0 - 1, b1 + 1, b1 + 1, 1)
    p.hang(c, storey + 1, c, length=1)
    # banners under the eaves in the middle of every face (only where a solid wall stands behind)
    for x, z, face in ((c, b0 - 1, "north"), (c, b1 + 1, "south"), (b0 - 1, c, "west"), (b1 + 1, c, "east")):
        bx, bz = {"north": (x, z + 1), "south": (x, z - 1), "west": (x + 1, z), "east": (x - 1, z)}[face]
        for dx in (-2, 2) if face in ("north", "south") else (0,):
            for dz in (-2, 2) if face in ("west", "east") else (0,):
                if p.get(bx + dx, storey, bz + dz) == p.b("wall") and p.free(x + dx, storey, z + dz):
                    p.banner(x + dx, storey, z + dz, face, "banner" if i % 2 else "banner_alt")
    if i < 8:
        p.eaves(b0, storey + 2, b0, b1, b1, layers=3, overhang=2)
        p.fill(b0 + 2, storey + 2, b0 + 2, b1 - 2, h - 1, b1 - 2, "roof_full")
        p.jigsaw_up(c, h - 1, c, name="kultivator:menara/naik", target="kultivator:menara/turun",
                    pool=pool(i + 2), final_state=p.b("roof_full"), joint="aligned")
        # under each curled eave corner: a lantern on a chain (odd storeys) or a wind bell (even storeys)
        for x, z in ((b0 - 2, b0 - 2), (b1 + 2, b0 - 2), (b0 - 2, b1 + 2), (b1 + 2, b1 + 2)):
            if i % 2:
                p.lantern(x, storey + 2, z, chain=1, role="lamp")
            else:
                p.bell(x, storey + 1, z, "north")
        name = fam + "/tingkat_%d" % (i + 1)
    else:
        top = p.roof_hip(b0, storey + 2, b0, b1, b1, role="roof", slab_role="roof_slab", overhang=2, tiers=2, tier_gap=2, tier_band=1)
        y = min(h - 8, top)
        # the spire: a gold stem with seven pearl rings (sea lanterns and end rods)
        for yy in range(y, h):
            p.put(c, yy, c, "gold" if yy % 3 else "minecraft:sea_lantern")
        for k, yy in enumerate(range(y + 1, h - 1, 2)):
            if k % 2 == 0:
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    p.put(c + dx, yy, c + dz, "minecraft:end_rod", {"facing": {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(dx, dz)]})
        for x, z in ((b0 - 2, b0 - 2), (b1 + 2, b0 - 2), (b0 - 2, b1 + 2), (b1 + 2, b1 + 2)):
            if p.get(x, storey + 2, z) not in (None, "minecraft:air"):
                p.lantern(x, storey + 2, z, chain=2, role="lamp")
        name = fam + "/tingkat_9"
    for x in range(p.size[0]):
        for z in range(p.size[2]):
            for yy in range(1, h):
                if p.get(x, yy, z) is None and b0 <= x <= b1 and b0 <= z <= b1:
                    p.put(x, yy, z, "minecraft:air")
    return finish(p, name)


def tower_lobby():
    """Aula Lobi of the tower dimension (47 x 22 x 47, the start at the dimension origin; its centre jigsaw lands on
    world 0, 0): an octagonal hall of white jade with ten band gates on a ring (radius 16), the ranking stele on the
    central dais and the exit gate to the south. Floor (template y 0) at world y 2 (start_height 3)."""
    pal = PAL["menara"]
    p = RPiece(47, 22, 47, seed=65100, palette=pal)
    C = 23
    for x in range(47):
        for z in range(47):
            d = max(abs(x - C), abs(z - C), (abs(x - C) + abs(z - C)) * 0.72)
            if d > 22.5:
                continue
            p.put(x, 0, z, "floor" if (x + z) % 2 else "floor_alt")
            if d > 21.0:
                for y in range(1, 21):
                    p.put(x, y, z, "wall" if y % 6 else "gold")
            else:
                for y in range(1, 21):
                    p.put(x, y, z, "minecraft:air")
            p.put(x, 21, z, "ceiling")
    # concentric inlays and the dais
    for r, role in ((3, "gold"), (6, "floor_inlay"), (12, "trim"), (16, "floor_inlay")):
        p.ring(C, 0, C, r, role)
    p.disc(C, 1, C, 4, "minecraft:smooth_quartz_slab")
    for x in range(C - 4, C + 5):
        for z in range(C - 4, C + 5):
            if math.hypot(x - C, z - C) <= 4.5:
                p.slab(x, 1, z, "minecraft:smooth_quartz_slab")
    p.fill(C - 1, 1, C - 4, C + 1, 6, C - 4, "minecraft:chiseled_quartz_block")
    p.slab(C, 7, C - 4, "slab")
    p.put(C, 0, C, "minecraft:jigsaw", {"orientation": "up_north"},
          {"id": "minecraft:jigsaw", "name": "kultivator:menara/lobi_pusat", "target": "minecraft:empty", "pool": "minecraft:empty",
           "final_state": p.b("gold"), "joint": "rollable", "placement_priority": 0, "selection_priority": 0})
    gates = []
    # ten band gates: torii-like frames of lacquer and gold on the ring (radius 16), facing the centre
    for b in range(10):
        ang = -math.pi / 2 + (b + 0.5) * 2 * math.pi / 11
        gx = round(C + 16 * math.cos(ang))
        gz = round(C + 16 * math.sin(ang))
        tx, tz = math.cos(ang), math.sin(ang)
        # posts two blocks to either side along the tangent
        px, pz = -tz, tx
        for s in (-2, 2):
            x = round(gx + px * s)
            z = round(gz + pz * s)
            for y in range(1, 7):
                p.put(x, y, z, "lacquer")
            p.put(x, 7, z, "gold")
        for s in range(-3, 4):
            x = round(gx + px * s)
            z = round(gz + pz * s)
            p.put(x, 8, z, "lacquer")
        p.put(gx, 0, gz, "light_block")
        gates.append((gx - C, 0, gz - C))
    # the exit gate (south, the eleventh place on the ring)
    ang = -math.pi / 2 + 10.5 * 2 * math.pi / 11
    gx = round(C + 16 * math.cos(ang))
    gz = round(C + 16 * math.sin(ang))
    p.put(gx, 0, gz, "minecraft:crying_obsidian")
    p.anchor_at(C, 1, C, "menara/lobi", yaw=0.0, protect=0, gates=nbt.IntArray([c for t in gates for c in t]),
                exit=nbt.IntArray([gx - C, 0, gz - C]), stele=nbt.IntArray([0, 1, -4]))
    # sixteen columns and hanging lanterns
    for k in range(16):
        ang = k * math.pi / 8
        x = round(C + 19.5 * math.cos(ang))
        z = round(C + 19.5 * math.sin(ang))
        for y in range(1, 21):
            p.put(x, y, z, "pillar", {"axis": "y"})
        x2 = round(C + 9 * math.cos(ang))
        z2 = round(C + 9 * math.sin(ang))
        p.lantern(x2, 21, z2, chain=4)
    # detail pass: banners of the ten bands between the columns, stone lanterns and plants between the gates, a censer
    # before the stele
    dyes = ["white", "light_gray", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "red"]
    for k in range(16):
        ang = (k + 0.5) * math.pi / 8
        x = round(C + 20.6 * math.cos(ang))
        z = round(C + 20.6 * math.sin(ang))
        if abs(math.cos(ang)) >= abs(math.sin(ang)):
            face = "west" if math.cos(ang) > 0 else "east"
            back = (x + (1 if face == "west" else -1), z)
        else:
            face = "north" if math.sin(ang) > 0 else "south"
            back = (x, z + (1 if face == "north" else -1))
        if p.get(back[0], 9, back[1]) not in (None, "minecraft:air"):
            p.banner(x, 9, z, face, dyes[k % 10])
    for b in range(11):
        ang = -math.pi / 2 + (b + 1.0) * 2 * math.pi / 11
        x = round(C + 16 * math.cos(ang))
        z = round(C + 16 * math.sin(ang))
        if b % 2 == 0:
            p.toro(x, 1, z, post="wallb", role="lamp")
        else:
            p.plant(x, 1, z)
    p.brazier(C, 1, C - 7, soul=False)
    return finish(p, "lobi")


# ================================================================================================= JSON

def biomes_tag(folder, name, biomes):
    j(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", folder, name + ".json"), {"replace": False, "values": biomes})
    return "#%s:has_structure/%s/%s" % (NS, folder, name)


def single(location, processors):
    return {"element_type": "minecraft:single_pool_element", "location": location, "processors": processors, "projection": "rigid"}


def processors():
    lapuk = ["minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_cobblestone",
             "minecraft:cracked_deepslate_bricks", "minecraft:cracked_deepslate_tiles", "minecraft:cracked_polished_blackstone_bricks",
             "minecraft:cobblestone", "minecraft:gravel"]
    j(os.path.join(DATA, "tags", "block", "reruntuhan_lapuk.json"), {"replace": False, "values": lapuk})
    protect = {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}
    mossify = {"processor_type": "minecraft:rule", "rules": [
        {"input_predicate": {"block": "minecraft:stone_bricks", "predicate_type": "minecraft:random_block_match", "probability": 0.1},
         "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": "minecraft:mossy_stone_bricks"},
        {"input_predicate": {"block": "minecraft:stone_bricks", "predicate_type": "minecraft:random_block_match", "probability": 0.06},
         "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": "minecraft:cracked_stone_bricks"},
        {"input_predicate": {"block": "minecraft:cobblestone", "predicate_type": "minecraft:random_block_match", "probability": 0.2},
         "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": "minecraft:mossy_cobblestone"},
        {"input_predicate": {"block": "minecraft:deepslate_bricks", "predicate_type": "minecraft:random_block_match", "probability": 0.08},
         "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": "minecraft:cracked_deepslate_bricks"},
        {"input_predicate": {"block": "minecraft:polished_blackstone_bricks", "predicate_type": "minecraft:random_block_match", "probability": 0.08},
         "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": "minecraft:cracked_polished_blackstone_bricks"},
    ]}
    rot = {"processor_type": "minecraft:block_rot", "integrity": 0.9, "rottable_blocks": "#kultivator:reruntuhan_lapuk"}
    wjson("processor_list", "reruntuhan", "lapuk", {"processors": [mossify, rot, protect]})
    wjson("processor_list", "penjara", "lapuk", {"processors": [mossify, protect]})
    wjson("processor_list", "menara", "utuh", {"processors": [protect]})


def structures():
    processors()
    loc = lambda folder, name: "%s:%s/%s" % (NS, folder, name)  # noqa: E731
    # --------------------------------------------------------------- Gua Leluhur
    gua_biomes = {
        "kecil": ["minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills",
                  "minecraft:forest", "minecraft:dark_forest", "minecraft:taiga", "minecraft:old_growth_pine_taiga",
                  "minecraft:jungle", "minecraft:birch_forest"],
        "agung": ["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks", "minecraft:meadow",
                  "minecraft:grove", "minecraft:snowy_slopes"],
        "kuno": ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:deep_dark"],
    }
    sizes = {"kecil": 2, "agung": 3, "kuno": 5}
    dist = {"kecil": 80, "agung": 104, "kuno": 128}
    sets = {"kecil": (40, 16, 340555), "agung": (72, 30, 340556), "kuno": (96, 40, 340557)}
    proc = "kultivator:reruntuhan/lapuk"
    all_gua = []
    for tier in ("kecil", "agung", "kuno"):
        gid = gua_id(tier)
        # every piece has two looks (detail pass): the plain ruin and its b variant (pavilion gate, library / shrine /
        # garden rooms, garden hall); a puzzle keeps the same total weight so the puzzle mix is unchanged
        wjson("template_pool", "reruntuhan", gid + "/gerbang",
              {"elements": [{"element": single(loc("reruntuhan", gid + suffix), proc), "weight": 1} for suffix in ("/gerbang", "/gerbang_b")],
               "fallback": "minecraft:empty"})
        rooms = [{"element": single(loc("reruntuhan", "%s/kamar_%s%s" % (gid, pz, b)), proc), "weight": 5}
                 for pz in PUZZLES for b in ("", "_b")]
        wjson("template_pool", "reruntuhan", gid + "/teka", {"elements": rooms, "fallback": "kultivator:reruntuhan/%s/akhir" % gid})
        wjson("template_pool", "reruntuhan", gid + "/akhir",
              {"elements": [{"element": single(loc("reruntuhan", gid + suffix), proc), "weight": 1} for suffix in ("/akhir", "/akhir_b")],
               "fallback": "minecraft:empty"})
        biome = biomes_tag("reruntuhan", gid, gua_biomes[tier])
        s = {"type": "minecraft:jigsaw", "biomes": biome, "max_distance_from_center": dist[tier], "size": sizes[tier],
             "spawn_overrides": {}, "start_pool": "kultivator:reruntuhan/%s/gerbang" % gid,
             "step": "underground_structures", "terrain_adaptation": "none" if tier == "kuno" else "beard_box",
             "use_expansion_hack": False}
        if tier == "kuno":
            s["start_height"] = {"absolute": -30}
        else:
            s["start_height"] = {"absolute": -6}
            s["project_start_to_heightmap"] = "WORLD_SURFACE_WG"
        wjson("structure", "reruntuhan", gid, s)
        spacing, separation, salt = sets[tier]
        # W4 (integration audit T7): never within 6 chunks of the world centre; the Kuno caves (Deity Transformation) 12
        wjson("structure_set", "reruntuhan", gid, {"placement": {"type": "kultivator:jauh_dari_pusat", "salt": salt,
                                                                  "separation": separation, "spacing": spacing,
                                                                  "min_chunks": 12 if tier == "kuno" else 6},
                                                    "structures": [{"structure": "kultivator:reruntuhan/" + gid, "weight": 1}]}, pack=True)
        j(os.path.join(DATA, "tags", "worldgen", "structure", "reruntuhan", gid + ".json"),
          {"replace": False, "values": ["kultivator:reruntuhan/" + gid]})
        all_gua.append("kultivator:reruntuhan/" + gid)
    j(os.path.join(DATA, "tags", "worldgen", "structure", "reruntuhan.json"), {"replace": False, "values": all_gua})
    # --------------------------------------------------------------- Penjara Iblis Kuno
    # two looks (detail pass): the mountain prison and the badlands prison (red sandstone plinth, acacia, mud-brick roof).
    # One structure set holds both; the chunk generator tries each structure of a set until one fits its biome.
    prison_biomes = {"": ["minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:frozen_peaks", "minecraft:windswept_hills"],
                     "_gurun": ["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"]}
    prison_ids = []
    for fam in PRISON_FAMS:
        for piece in ("kuil", "sumur", "aula"):
            wjson("template_pool", "penjara", piece + fam,
                  {"elements": [{"element": single(loc("penjara", piece + fam), "kultivator:penjara/lapuk"), "weight": 1}],
                   "fallback": "minecraft:empty"})
        sid = "penjara_iblis" + fam
        biome = biomes_tag("penjara", sid, prison_biomes[fam])
        wjson("structure", "penjara", sid, {
            "type": "minecraft:jigsaw", "biomes": biome, "max_distance_from_center": {"horizontal": 64, "vertical": 160}, "size": 2,
            "spawn_overrides": {}, "start_height": {"absolute": -12}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "start_pool": "kultivator:penjara/kuil" + fam, "step": "surface_structures", "terrain_adaptation": "none",
            "use_expansion_hack": False})
        prison_ids.append("kultivator:penjara/" + sid)
    # W4 (integration audit T7): the Dao Union prison never starts within 12 chunks of the world centre
    wjson("structure_set", "penjara", "penjara_iblis", {"placement": {"type": "kultivator:jauh_dari_pusat", "salt": 340562, "separation": 48,
                                                                      "spacing": 120, "min_chunks": 12},
                                                        "structures": [{"structure": sid, "weight": 1} for sid in prison_ids]}, pack=True)
    j(os.path.join(DATA, "tags", "worldgen", "structure", "penjara.json"), {"replace": False, "values": prison_ids})
    # --------------------------------------------------------------- Menara Seratus Lantai
    # three looks (detail pass), one structure set: the chunk generator tries each until one fits the biome
    tower_biomes = {"menara_seratus": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
                    "menara_sakura": ["minecraft:cherry_grove"],
                    "menara_sabana": ["minecraft:savanna", "minecraft:savanna_plateau"]}
    tower_ids = []
    for fam in TOWER_FAMS:
        for i in range(9):
            wjson("template_pool", "menara", "%s/tingkat_%d" % (fam, i + 1),
                  {"elements": [{"element": single(loc("menara", "%s/tingkat_%d" % (fam, i + 1)), "kultivator:menara/utuh"), "weight": 1}],
                   "fallback": "minecraft:empty"})
        biome = biomes_tag("menara", fam, tower_biomes[fam])
        wjson("structure", "menara", fam, {
            "type": "minecraft:jigsaw", "biomes": biome, "max_distance_from_center": {"horizontal": 40, "vertical": 128}, "size": 8,
            "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}}, "start_height": {"absolute": 0},
            "project_start_to_heightmap": "WORLD_SURFACE_WG", "start_pool": "kultivator:menara/%s/tingkat_1" % fam,
            "step": "surface_structures", "terrain_adaptation": "beard_thin", "use_expansion_hack": False})
        tower_ids.append("kultivator:menara/" + fam)
    # W4 (integration audit T7): never within 6 chunks of the world centre
    wjson("structure_set", "menara", "menara_seratus", {"placement": {"type": "kultivator:jauh_dari_pusat", "salt": 340565, "separation": 64,
                                                                      "spacing": 160, "min_chunks": 6},
                                                        "structures": [{"structure": sid, "weight": 1} for sid in tower_ids]}, pack=True)
    # the lobby of the tower dimension (base data: the dimension's flat settings list this set)
    wjson("template_pool", "menara", "lobi", {"elements": [{"element": single(loc("menara", "lobi"), "kultivator:menara/utuh"), "weight": 1}],
                                              "fallback": "minecraft:empty"})
    biome = biomes_tag("menara", "lobi", ["kultivator:menara_dalam"])
    wjson("structure", "menara", "lobi", {
        "type": "minecraft:jigsaw", "biomes": biome, "max_distance_from_center": 32, "size": 0,
        "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}, "creature": {"bounding_box": "full", "spawns": []}},
        "start_height": {"absolute": 3}, "start_jigsaw_name": "kultivator:menara/lobi_pusat", "start_pool": "kultivator:menara/lobi",
        "step": "surface_structures", "terrain_adaptation": "none", "use_expansion_hack": False})
    wjson("structure_set", "menara", "lobi", {"placement": {"type": "minecraft:dimension_origin"},
                                              "structures": [{"structure": "kultivator:menara/lobi", "weight": 1}]})
    j(os.path.join(DATA, "tags", "worldgen", "structure", "menara.json"), {"replace": False, "values": tower_ids})


def loot_tables():
    """Chest loot, capped like harta/HartaLoot (DESIGN 34.2.4): a few rolls, stones by NB value, aged herbs, cores."""
    def stones(item, lo, hi, weight):
        return {"type": "minecraft:item", "name": "kultivator:" + item, "weight": weight,
                "modifier": {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}}

    def herb(item, age_lo, age_hi, weight):
        return {"type": "minecraft:item", "name": "kultivator:" + item, "weight": weight,
                "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                             {"type": "minecraft:set_components", "components": {"kultivator:herb_age": random.Random(age_lo).randint(age_lo, age_hi)}}]}

    def item(name, weight, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": name, "weight": weight}
        if hi > 1:
            e["modifier"] = {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}
        return e

    tables = {
        "kecil_warisan": [(2, [stones("spirit_stone_low", 3, 9, 40), stones("spirit_stone_mid", 1, 1, 10), herb("spirit_grass", 100, 300, 25),
                               herb("ninecurl_ginseng", 100, 200, 10), item("kultivator:beast_core_low", 15), item("minecraft:candle", 10, 1, 3),
                               item("minecraft:book", 10, 1, 2)])],
        "agung_kamar": [(1, [stones("spirit_stone_mid", 1, 3, 30), herb("purple_flame_flower", 300, 600, 20), item("kultivator:beast_core_mid", 15),
                             item("minecraft:gold_ingot", 20, 1, 3), item("minecraft:amethyst_shard", 15, 2, 5)])],
        "agung_warisan": [(2, [stones("spirit_stone_mid", 2, 6, 35), stones("spirit_stone_high", 1, 1, 5), herb("eight_angled_ice_grass", 300, 1000, 20),
                               herb("gold_lightning_bamboo", 300, 1000, 10), item("kultivator:beast_core_high", 10),
                               item("minecraft:gold_ingot", 20, 2, 5)])],
        "kuno_kamar": [(1, [stones("spirit_stone_high", 1, 2, 25), herb("illusory_spirit_herb", 1000, 2000, 15), item("kultivator:beast_core_high", 15),
                            item("minecraft:diamond", 10, 1, 2), item("minecraft:echo_shard", 10, 1, 2)])],
        "kuno_warisan": [(2, [stones("spirit_stone_high", 2, 5, 35), stones("spirit_stone_supreme", 1, 1, 4), herb("ginseng_fruit", 1000, 3000, 12),
                              herb("illusory_spirit_herb", 1000, 3000, 15), item("kultivator:beast_core_king", 6), item("minecraft:diamond", 15, 1, 3),
                              item("minecraft:netherite_scrap", 6)])],
        "penjara": [(2, [stones("spirit_stone_high", 1, 3, 30), stones("spirit_stone_mid", 4, 9, 25), item("kultivator:beast_core_high", 15),
                         item("minecraft:gold_ingot", 15, 2, 6), item("minecraft:crying_obsidian", 10, 1, 3), item("kultivator:blank_talisman", 15, 2, 4)])],
    }
    for name, pools in tables.items():
        out = {"type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": r, "max": r + 2}, "entries": e} for r, e in pools],
               "random_sequence": loot_id(name)}
        j(os.path.join(DATA, "loot_table", "chests", "dunia", "reruntuhan", name + ".json"), out)


def pack():
    meta = {"pack": {"description": {"translate": "kultivator.dunia.pack.reruntuhan"}, "min_format": 121, "max_format": 121}}
    j(os.path.join(PACK_DIR, "pack.mcmeta"), meta, pack=True)


# ================================================================================================= build and check

def build():
    for tier in ("kecil", "agung", "kuno"):
        for variant in (0, 1):
            gua_gate(tier, variant)
            for pz in PUZZLES:
                gua_room(tier, pz, variant)
            gua_final(tier, variant)
    for fam in PRISON_FAMS:
        prison_shrine(fam)
        prison_shaft(fam)
        prison_hall(fam)
    for fam in TOWER_FAMS:
        for i in range(9):
            pagoda_tier(i, fam)
    tower_lobby()
    structures()
    loot_tables()
    pack()


def folder_of(tid):
    if tid.split("_")[0] in ("kuil", "sumur", "aula"):
        return "penjara"
    if tid.startswith("menara_") or tid == "lobi":
        return "menara"
    return "reruntuhan"


def save_all():
    for tid, p in TEMPLATES:
        p.save(out_path(folder_of(tid), tid))
    for path, obj in JSONS + PACK_JSONS:
        write_json(path, obj)


def read_back():
    """Re-read every template from disk: the block count, jigsaw nbt and anchor data must match what was built."""
    problems = 0
    for tid, p in TEMPLATES:
        path = out_path(folder_of(tid), tid)
        _, root = nbt.read_file(path)
        blocks = root["blocks"]
        if len(blocks) != len(p.cells):
            print("READBACK %s: %d blocks on disk, %d built" % (tid, len(blocks), len(p.cells)))
            problems += 1
        anchors = [e for e in root["entities"] if e["nbt"].get("id") == "kultivator:dunia_anchor"]
        if len(anchors) > 6 and tid != "aula" and not tid.startswith("lobi"):
            print("READBACK %s: %d anchors (max 6 per piece)" % (tid, len(anchors)))
        jig = [b for b in blocks if "nbt" in b and b["nbt"].get("id") == "minecraft:jigsaw"]
        for b in jig:
            for key in ("name", "target", "pool", "final_state", "joint"):
                if key not in b["nbt"]:
                    print("READBACK %s: jigsaw missing %s" % (tid, key))
                    problems += 1
        for e in anchors:
            if "/" not in e["nbt"].get("kind", ""):
                print("READBACK %s: bad anchor kind" % tid)
                problems += 1
    return problems


def plan(tid, p, y):
    print("plan %s at y=%d" % (tid, y))
    glyph = {}
    for z in range(p.size[2]):
        row = ""
        for x in range(p.size[0]):
            b = p.get(x, y, z)
            if b is None:
                row += " "
            elif b == "minecraft:air":
                row += "."
            elif b == "minecraft:jigsaw":
                row += "J"
            else:
                short = b.split(":")[1]
                if short not in glyph:
                    glyph[short] = "#ABCDEFGHIKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[len(glyph) % 62]
                row += glyph[short]
        print("  " + row)


def main():
    build()
    save_all()
    problems = read_back()
    total_entities = sum(len(p.entities) for _, p in TEMPLATES)
    print("module R: %d templates, %d anchors, %d json files (%d in the toggle pack), read-back problems: %d"
          % (len(TEMPLATES), total_entities, len(JSONS) + len(PACK_JSONS), len(PACK_JSONS), problems))
    if PLAN:
        for tid, p in TEMPLATES:
            if tid in ("gua_kecil/gerbang", "gua_kecil/kamar_ilusi", "kuil", "lobi"):
                plan(tid, p, 2 if tid != "kuil" else 13)
    validate_all()
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
