"""Module D (DESIGN 34.5.13, 34.5.14, 34.6 D): every structure template and every worldgen JSON of the two new
dimensions, Dunia Bawah (kultivator:dunia_bawah) and Laut Tanpa Batas (kultivator:laut_tanpa_batas).

    python tools/gen_dunia_dimensi.py            # writes the templates + JSON, prints validate_all()

Writes ONLY module D's folders:
    data/kultivator/structure/{bawah,laut}/**                      (NBT, through tools/structgen.py)
    data/kultivator/worldgen/{structure,template_pool,processor_list,structure_set}/{bawah,laut}/**
    data/kultivator/worldgen/{biome,noise_settings,material_rule,feature,placed_feature}/... (D's ids only)
    data/kultivator/dimension/{dunia_bawah,laut_tanpa_batas}.json, data/kultivator/dimension_type/(same).json
    data/kultivator/tags/worldgen/{biome/has_structure/{bawah,laut}, structure/{bawah,laut}.json}
    data/kultivator/loot_table/chests/dunia/dimensi/*, recipe/dunia/dimensi_*, advancement/dunia/{naihe_crossed,
        ten_courts,sea_arrived,dragon_king,meng_po_soup,court_mirror,letter_delivered,lost_soul_home,name_struck,
        pearl_returned,penglai_water,islands_all,turtle_rider,sea_tier4}.json
    resourcepacks/dunia_dimensi/** (the overworld sets: kuil_gerbang_hantu, pusaran_laut; DESIGN 34.2.3)

Block rule (DESIGN 34.0 W1): these templates are placed ONLY by world generation in new chunks. Runtime code never
places, breaks or changes a block; gates, books, mirrors and whirlpools are entities spawned at the template's anchors.

Detail pass (2026-10-22): every island kind is its own structure (laut/pulau_<kind>, two forms each; the old mixed
laut/pulau_roh structure stays loadable but is no longer placed), the swamp form of the ghost shrine is its own structure
(bawah/kuil_rawa, picked by biome inside the same set), more Desa Arwah houses and lanes, a pagoda shrine and a family
tomb, the fisherman's jetty and a sunken junk among the satellites, and dressing everywhere (guardian lions, censers
with end-rod incense, banners with patterns, ledgers, river candles, ridge pearls). At most 6 anchors per piece.

Layout notes (worked out in DESIGN 34.5.13 terms; the Ten Courts hub fits max_distance_from_center 128):
    Istana Yama (dimension origin, start_jigsaw_name kultivator:bawah/tiba at x 0 z 0):
        N  Teras -> Jalan Huangquan -> Jembatan Naihe (river Wangchuan) -> Paviliun Meng Po
        S  Lorong A: courts 1-4, end Kuil Dizang
        E  Lorong B: courts 5-8, end Pos Yaksha
        W  Lorong C: courts 9-10, end Istana Yanluo (Raja Yama, Buku Hidup dan Mati)
    every court has its own pool (pengadilan_<n>) so each number appears exactly once.
    Dermaga Tiba (Laut Tanpa Batas origin, start_jigsaw_name kultivator:laut/tiba at x 0 z 0 y 100): the arrival
        harbour on stilts with the Gerbang Pulang shrine, the lighthouse pagoda and the sea merchant's stall.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from structgen import (DATA, PALETTES, REPO, Piece, json_path, out_path, pool, single, validate_all,  # noqa: E402
                       write_json)

NS = "kultivator"
PACK_DIR = os.path.join(REPO, "src", "main", "resources", "resourcepacks", "dunia_dimensi")

# ---------------------------------------------------------------------------------------------------------- palettes
DIYU = dict(PALETTES["diyu"])
DIYU.update({
    "floor": "minecraft:polished_blackstone_bricks", "floor_alt": "minecraft:cracked_polished_blackstone_bricks",
    "found": "minecraft:polished_blackstone", "found_alt": "minecraft:blackstone",
    "wall": "minecraft:nether_bricks", "wall_alt": "minecraft:cracked_nether_bricks",
    "pillar": "minecraft:crimson_stem", "beam": "minecraft:stripped_crimson_stem", "trim": "minecraft:chiseled_polished_blackstone",
    "roof": "minecraft:blackstone_stairs", "roof_slab": "minecraft:blackstone_slab", "roof_full": "minecraft:blackstone",
    "glow": "minecraft:soul_lantern", "path": "minecraft:mud_bricks", "path_alt": "minecraft:packed_mud",
    "fence": "minecraft:crimson_fence", "rail": "minecraft:red_nether_brick_wall", "curb": "minecraft:polished_blackstone_wall",
    "gold": "minecraft:gilded_blackstone", "red": "minecraft:red_nether_bricks", "bone": "minecraft:bone_block",
    "window": "minecraft:iron_bars", "carpet": "minecraft:red_carpet", "dirt": "minecraft:coarse_dirt",
    "dirt_alt": "minecraft:rooted_dirt", "wood": "minecraft:pale_oak_planks", "log": "minecraft:pale_oak_log",
})
KUIL = dict(PALETTES["kota"])
KUIL.update({
    "floor": "minecraft:mossy_stone_bricks", "floor_alt": "minecraft:cracked_stone_bricks",
    "found": "minecraft:cobblestone", "found_alt": "minecraft:mossy_cobblestone",
    "wall": "minecraft:dark_oak_planks", "pillar": "minecraft:crimson_stem", "beam": "minecraft:dark_oak_log",
    "trim": "minecraft:chiseled_stone_bricks", "roof": "minecraft:deepslate_tile_stairs",
    "roof_slab": "minecraft:deepslate_tile_slab", "roof_full": "minecraft:deepslate_tiles", "glow": "minecraft:soul_lantern",
    "path": "minecraft:mossy_cobblestone", "path_alt": "minecraft:cobblestone", "fence": "minecraft:dark_oak_fence",
    "rail": "minecraft:mossy_stone_brick_wall", "red": "minecraft:red_nether_bricks", "gold": "minecraft:gold_block",
    "curb": "minecraft:mossy_stone_brick_wall",
})
LAUT = dict(PALETTES["laut"])
LAUT.update({
    "found": "minecraft:dark_prismarine", "found_alt": "minecraft:prismarine",
    "beam": "minecraft:stripped_warped_stem", "rail": "minecraft:prismarine_wall", "curb": "minecraft:prismarine_wall",
    "red": "minecraft:red_nether_bricks", "gold": "minecraft:gold_block", "pearl": "minecraft:pearlescent_froglight",
    "sand": "minecraft:sand", "rock": "minecraft:stone", "rock_alt": "minecraft:andesite", "grass": "minecraft:grass_block",
    "dirt": "minecraft:dirt", "wood": "minecraft:spruce_planks", "log": "minecraft:spruce_log",
    "path": "minecraft:smooth_sandstone", "path_alt": "minecraft:sandstone",
})

# ---------------------------------------------------------------------------------------------------------- piece
_FENCE_LIKE = ("_fence", "_wall", "iron_bars", "_pane")
_NO_CONNECT = ("_fence_gate", "_wall_sign", "_wall_torch", "_wall_fan", "_wall_banner", "_wall_head", "_wall_skull",
               "_wall_hanging_sign")
_THIN = ("_slab", "_stairs", "_carpet", "_trapdoor", "_door", "lantern", "iron_chain", "_button", "_pressure_plate",
         "torch", "_sapling", "flower", "poppy", "tulip", "_candle", "candle", "lily_pad", "cobweb", "_coral",
         "_coral_fan", "sea_pickle", "seagrass", "kelp", "campfire", "cauldron", "decorated_pot", "lectern", "bell",
         "_head", "_skull", "leaves", "_bush", "dead_bush", "glass_pane", "_pane", "lily", "_roots", "short_grass",
         "fern", "dripleaf", "_moss_carpet", "hanging_moss", "end_rod", "lightning_rod", "anvil", "grindstone",
         "chest", "_sign", "flower_pot", "pink_petals", "wildflowers", "leaf_litter", "_rail", "torchflower",
         "eyeblossom", "firefly_bush", "bamboo", "vine", "conduit", "water", "_shelf")
_FLUIDS = ("minecraft:water",)


def _family(bid):
    if bid.endswith("_fence") or bid.endswith("_fence_gate"):
        return "wood_fence" if "nether_brick" not in bid else "nether_fence"
    if bid.endswith("_wall"):
        return "wall"
    if bid.endswith("_pane") or bid.endswith("iron_bars") or "glass" in bid:
        return "pane"
    return None


class DPiece(Piece):
    """structgen.Piece plus the helpers module D needs (water, gates, trees, connections)."""

    def water(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, "minecraft:water", {"level": "0"})

    def leaves(self, x, y, z, block="minecraft:cherry_leaves"):
        self.put(x, y, z, block, {"persistent": "true", "distance": "1", "waterlogged": "false"})

    def log(self, x, y, z, role="log", axis="y"):
        self.put(x, y, z, role, {"axis": axis})

    def door(self, x, y, z, block, facing, hinge="left"):
        for half, dy in (("lower", 0), ("upper", 1)):
            self.put(x, y + dy, z, block, {"facing": facing, "half": half, "hinge": hinge, "open": "false",
                                           "powered": "false"})

    def candle(self, x, y, z, block="minecraft:white_candle", n=1, lit=True):
        self.put(x, y, z, block, {"candles": str(max(1, min(4, n))), "lit": "true" if lit else "false",
                                  "waterlogged": "false"})

    def flower(self, x, y, z, rng_pick=("minecraft:poppy", "minecraft:red_tulip", "minecraft:poppy")):
        self.put(x, y, z, self.rng.choice(rng_pick))

    def lily_bed(self, x0, z0, x1, z1, y, density=0.55, soil=("dirt", "dirt_alt")):
        """Red spider lilies (poppies and red tulips, DESIGN 34.5.13) on coarse/rooted dirt: soil at y, flowers at y+1."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                self.put(x, y, z, self.rng.choice(soil))
                if self.rng.random() < density:
                    self.flower(x, y + 1, z)
                elif self.rng.random() < 0.12:
                    self.put(x, y + 1, z, "minecraft:dead_bush")

    def dead_tree(self, x, y, z, h=6):
        """A leafless pale oak with crooked branches (the ghost forest)."""
        for i in range(h):
            self.log(x, y + i, z)
        r = self.rng
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if r.random() < 0.7:
                by = y + h - 2 - r.randint(0, 2)
                ax = "x" if dx else "z"
                self.log(x + dx, by, z + dz, axis=ax)
                if r.random() < 0.6:
                    self.log(x + 2 * dx, by + 1, z + 2 * dz, axis="y")
                if r.random() < 0.5:
                    self.put(x + dx, by - 1, z + dz, "minecraft:pale_hanging_moss", {"tip": "true"})

    def round_tree(self, x, y, z, h=5, leaves="minecraft:cherry_leaves", log="minecraft:cherry_log", radius=2):
        for i in range(h):
            self.put(x, y + i, z, log, {"axis": "y"})
        top = y + h
        for dx in range(-radius - 1, radius + 2):
            for dz in range(-radius - 1, radius + 2):
                for dy in range(-2, 2):
                    d = math.sqrt(dx * dx + dz * dz + (dy * 1.4) ** 2)
                    if d <= radius + 0.6 and not (dx == 0 and dz == 0 and dy < 0):
                        if self.inside(x + dx, top + dy, z + dz) and self.get(x + dx, top + dy, z + dz) in (None, "minecraft:air"):
                            self.leaves(x + dx, top + dy, z + dz, leaves)

    def paifang(self, x0, y, z, x1, h=6, role_pillar="pillar", role_beam="beam", role_roof="roof", lamps=True,
                axis="x"):
        """A memorial gate (paifang) spanning x0..x1 at depth z (axis x) or z0..z1 at x (axis z): two red pillars on
        stone bases, a double beam, a small gable roof with curled ends and hanging soul lanterns."""
        def at(u, v):
            return (u, v) if axis == "x" else (v, u)
        for u in (x0, x1):
            px, pz = at(u, z)
            self.put(px, y, pz, "trim")
            for i in range(1, h):
                self.put(px, y + i, pz, role_pillar, {"axis": "y"})
        for u in range(x0 - 1, x1 + 2):
            px, pz = at(u, z)
            self.put(px, y + h, pz, role_beam, {"axis": axis})
            if x0 < u < x1:
                self.put(px, y + h - 2, pz, role_beam, {"axis": axis})
        for u in range(x0 - 2, x1 + 3):
            px, pz = at(u, z)
            self.slab(px, y + h + 1, pz, "roof_slab")
            for side in (-1, 1):
                qx, qz = at(u, z + side)
                self.stairs(qx, y + h, qz, role_roof, _face(axis, side))
        for u, face in ((x0 - 3, "west" if axis == "x" else "north"), (x1 + 3, "east" if axis == "x" else "south")):
            px, pz = at(u, z)
            self.stairs(px, y + h + 1, pz, role_roof, _opp(face), half="top")
        # plaque between the beams
        mid = (x0 + x1) // 2
        px, pz = at(mid, z)
        self.put(px, y + h - 1, pz, "gold")
        if lamps:
            for u in (x0 + 1, x1 - 1):
                if x1 - x0 >= 4:
                    px, pz = at(u, z)
                    self.lantern(px, y + h - 2, pz, chain=1)

    def lamp_post(self, x, y, z, h=3, post="fence", glow="glow", base="trim"):
        if base:
            self.put(x, y, z, base)
            y += 1
        for i in range(h):
            self.put(x, y + i, z, post)
        g = self.b(glow)
        props = {"hanging": "false", "waterlogged": "false"} if g.endswith("lantern") and g != "minecraft:sea_lantern" else None
        self.put(x, y + h, z, g, props)

    def connect(self):
        """Sets fence / wall / pane / bar connections like the game would (templates keep stored states)."""
        cells = self.cells
        for (x, y, z) in sorted(cells):
            (bid, props), data = cells[(x, y, z)]
            if not any(bid.endswith(s) for s in _FENCE_LIKE) or any(bid.endswith(s) for s in _NO_CONNECT):
                continue
            fam = _family(bid)
            p = dict(props)
            sides = {}
            for name, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
                n = cells.get((x + dx, y, z + dz))
                ok = False
                if n is not None:
                    nid = n[0][0]
                    nfam = _family(nid)
                    if nfam is not None and (nfam == fam or (fam in ("wall", "pane") and nfam in ("wall", "pane"))):
                        ok = not any(nid.endswith(s) for s in _NO_CONNECT) or nid.endswith("_fence_gate")
                    elif nid not in ("minecraft:air", "minecraft:water") and not any(t in nid for t in _THIN) and nfam is None:
                        ok = True
                sides[name] = ok
            above = cells.get((x, y + 1, z))
            above_solid = above is not None and above[0][0] not in ("minecraft:air", "minecraft:water")
            if fam == "wall":
                for name, ok in sides.items():
                    p[name] = ("tall" if above_solid else "low") if ok else "none"
                straight = (sides["north"] and sides["south"] and not sides["east"] and not sides["west"]) or \
                           (sides["east"] and sides["west"] and not sides["north"] and not sides["south"])
                p["up"] = "false" if straight and not above_solid else "true"
            else:
                for name, ok in sides.items():
                    p[name] = "true" if ok else "false"
            p.setdefault("waterlogged", "false")
            self.set(x, y, z, bid, p, data)

    def finish(self, path):
        self.connect()
        return self.save(path)

    # ---------------------------------------------------------------------------------------- detail pass helpers
    def banner(self, x, y, z, color, facing=None, rotation=0, patterns=()):
        """A wall banner (facing = the side it hangs on, pointing away from its wall) or a standing banner (rotation
        0-15); patterns [(pattern id without namespace, dye colour)] go into the block entity like a looted banner."""
        if facing:
            bid, props = "minecraft:%s_wall_banner" % color, {"facing": facing}
        else:
            bid, props = "minecraft:%s_banner" % color, {"rotation": str(rotation % 16)}
        data = None
        if patterns:
            data = {"id": "minecraft:banner",
                    "patterns": [{"pattern": "minecraft:" + pat, "color": col} for pat, col in patterns]}
        self.put(x, y, z, bid, props, data)

    def censer(self, x, y, z, plinth="trim", smoke=True):
        """A bronze tripod censer (a cauldron on a plinth) with an incense stick (an end rod: its white motes rise like
        incense smoke)."""
        if plinth:
            self.put(x, y, z, plinth, self._axis_y(plinth))
            y += 1
        self.put(x, y, z, "minecraft:cauldron")
        if smoke:
            self.put(x, y + 1, z, "minecraft:end_rod", {"facing": "up"})

    def guardian(self, x, y, z, facing, stone="minecraft:polished_blackstone", accent="minecraft:gilded_blackstone"):
        """A guardian lion (shishi) facing `facing`: a carved plinth, the crouching body, the head turned out, a paw on
        the embroidered ball (a slab of `accent` in front)."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        stairs = {"minecraft:polished_blackstone": "minecraft:polished_blackstone_stairs",
                  "minecraft:stone_bricks": "minecraft:stone_brick_stairs",
                  "minecraft:smooth_quartz": "minecraft:smooth_quartz_stairs",
                  "minecraft:prismarine_bricks": "minecraft:prismarine_brick_stairs",
                  "minecraft:mossy_stone_bricks": "minecraft:mossy_stone_brick_stairs"}.get(stone, "minecraft:stone_brick_stairs")
        self.put(x, y, z, "minecraft:chiseled_polished_blackstone" if "blackstone" in stone else "minecraft:chiseled_stone_bricks")
        self.stairs(x, y + 1, z, stairs, _opp(facing))
        self.put(x, y + 2, z, stone)
        self.stairs(x + dx, y + 2, z + dz, stairs, _opp(facing), half="top")
        self.put(x + dx, y + 1, z + dz, accent)

    def ridge_pearl(self, x, y, z, block="minecraft:sea_lantern", flame=False):
        """The flaming pearl on a roof ridge (a glowing block and, with flame, a soul fire candle cluster above)."""
        self.put(x, y, z, block, {"axis": "y"} if block.endswith("froglight") else None)
        if flame:
            self.put(x, y + 1, z, "minecraft:end_rod", {"facing": "up"})

    def table(self, x0, y, z0, x1, z1, top="minecraft:dark_oak_slab", leg="minecraft:dark_oak_fence"):
        """A long table: top-half slabs on fence legs at the four corners."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                self.put(x, y + 1, z, top, {"type": "top", "waterlogged": "false"})
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            self.put(x, y, z, leg)

    def bookshelf_wall(self, x0, y0, z0, x1, y1, z1, facing):
        """Shelves of ledgers: bookshelves with a chiseled shelf every third block (some slots empty)."""
        k = 0
        for y in range(y0, y1 + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    k += 1
                    if k % 3 == 0:
                        props = {"facing": facing}
                        for s in range(6):
                            props["slot_%d_occupied" % s] = "true" if self.rng.random() < 0.6 else "false"
                        self.put(x, y, z, "minecraft:chiseled_bookshelf", props)
                    else:
                        self.put(x, y, z, "minecraft:bookshelf")

    def floating_candle(self, x, y, z, color="minecraft:white_candle"):
        """A river lantern for the dead: a lit candle on a lily pad (the pad at y, on the water's top layer)."""
        self.put(x, y, z, "minecraft:lily_pad")
        self.candle(x, y + 1, z, color, n=1)

    def pot(self, x, y, z, plant="minecraft:potted_fern"):
        self.put(x, y, z, plant)


def _face(axis, side):
    """The stair facing on side `side` (-1 / +1 across the gate) whose high back points to the gate's ridge."""
    if axis == "x":
        return "south" if side < 0 else "north"
    return "east" if side < 0 else "west"


def _opp(face):
    return {"north": "south", "south": "north", "east": "west", "west": "east"}[face]


def J(name):
    return NS + ":" + name


EMPTY = "minecraft:empty"

# ================================================================================================== Dunia Bawah
BAWAH_PROC = J("bawah/lapuk")
KUIL_PROC = J("bawah/kuil_lapuk")
LAUT_PROC = J("laut/terumbu")
SAFE = J("bawah/aman")  # only the sect-protection rule (the Yama court is not weathered)


def teras():
    """Start piece of Istana Yama: Teras Wangxiang, the homesick terrace (Yuli Baochao: from it the dead look home)."""
    p = DPiece(41, 20, 41, seed=63001, palette=DIYU)
    c = 20
    p.fill(0, 0, 0, 40, 2, 40, "found", alt="found_alt", alt_chance=0.2)
    p.air(0, 4, 0, 40, 19, 40)
    # paved court with ring bands
    for x in range(41):
        for z in range(41):
            d = max(abs(x - c), abs(z - c))
            role = "floor" if d % 6 else "trim"
            if p.rng.random() < 0.1 and role == "floor":
                role = "floor_alt"
            p.put(x, 3, z, role)
    # red spider lily beds outside the terrace, between the four roads (the flowers of the Yellow Springs)
    for x in range(1, 40):
        for z in range(1, 40):
            if max(abs(x - c), abs(z - c)) > 13 and abs(x - c) > 4 and abs(z - c) > 4:
                p.put(x, 3, z, "dirt" if p.rng.random() > 0.35 else "dirt_alt")
                if p.rng.random() < 0.5:
                    p.flower(x, 4, z)
                elif p.rng.random() < 0.08:
                    p.put(x, 4, z, "minecraft:dead_bush")
    # four mud-brick roads to the exits (7 wide, blackstone curbs)
    for a in range(41):
        for w in range(-3, 4):
            for (x, z) in ((c + w, a), (a, c + w)):
                if abs(x - c) > 12 or abs(z - c) > 12:
                    p.put(x, 3, z, "path" if p.rng.random() > 0.2 else "path_alt")
                    p.air(x, 4, z, x, 4, z)
        for w in (-4, 4):
            for (x, z) in ((c + w, a), (a, c + w)):
                if abs(x - c) > 12 or abs(z - c) > 12:
                    p.put(x, 3, z, "trim")
                    p.air(x, 4, z, x, 4, z)
    # pale dead trees in the outer corners and censers at the inner corners of the beds
    for (x, z) in ((4, 4), (36, 4), (4, 36), (36, 36)):
        p.put(x, 3, z, "dirt")
        p.dead_tree(x, 4, z, h=7)
    for (x, z) in ((6, 6), (34, 6), (6, 34), (34, 34)):
        p.put(x, 3, z, "trim")
        p.put(x, 4, z, "minecraft:decorated_pot")
        p.put(x, 5, z, "minecraft:soul_campfire", {"facing": "north", "lit": "true", "signal_fire": "false",
                                                    "waterlogged": "false"})
    # three tiers: 25 / 17 / 9 wide, stairs on the four axes (climbing toward the centre)
    for (r, top) in ((12, 4), (8, 5), (4, 6)):
        for x in range(c - r, c + r + 1):
            for z in range(c - r, c + r + 1):
                edge = max(abs(x - c), abs(z - c)) == r
                p.put(x, top, z, "trim" if edge else ("gold" if (x + z) % 4 == 0 and r == 4 else "floor"))
                p.air(x, top + 1, z, x, top + 1, z)
        for w in range(-1, 2):
            p.stairs(c + w, top, c - r - 1, "roof", "south")
            p.stairs(c + w, top, c + r + 1, "roof", "north")
            p.stairs(c - r - 1, top, c + w, "roof", "east")
            p.stairs(c + r + 1, top, c + w, "roof", "west")
    # balustrades on tiers 2 and 3 (gaps at the stairs)
    for (r, top) in ((8, 5), (4, 6)):
        for x in range(c - r, c + r + 1):
            for z in range(c - r, c + r + 1):
                if max(abs(x - c), abs(z - c)) == r and min(abs(x - c), abs(z - c)) > 1:
                    p.put(x, top + 1, z, "curb")
    # lantern posts at the top tier's corners and the Wangxiang lodestone (the return compass)
    for dx in (-4, 4):
        for dz in (-4, 4):
            p.lamp_post(c + dx, 7, c + dz, h=2, post="fence", base=None)
    p.put(c, 7, c - 3, "minecraft:lodestone")
    p.put(c - 1, 7, c - 3, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    p.put(c + 1, 7, c - 3, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    # the arrival circle: start jigsaw in the centre of the top tier
    p.jigsaw(c, 6, c, "up_north", J("bawah/tiba"), EMPTY, EMPTY, final_state="minecraft:chiseled_polished_blackstone")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        p.put(c + dx, 6, c + dz, "gold")
    # gates at the four exits
    p.paifang(c - 4, 4, 3, c + 4, h=7)
    p.paifang(c - 4, 4, 37, c + 4, h=7)
    p.paifang(c - 4, 4, 3, c + 4, h=7, axis="z")
    p.paifang(c - 4, 4, 37, c + 4, h=7, axis="z")
    # detail pass: guardian lions inside every gate, facing the road; censers with rising incense on the middle tier;
    # mourning banners at the outer tier's corners (white on Bai Wuchang's west side, black on Hei Wuchang's east)
    for (gx, gz, face) in ((c - 6, 6, "south"), (c + 6, 6, "south"), (c - 6, 34, "north"), (c + 6, 34, "north")):
        p.guardian(gx, 4, gz, face)
    for (gx, gz, face) in ((6, c - 6, "east"), (6, c + 6, "east"), (34, c - 6, "west"), (34, c + 6, "west")):
        p.guardian(gx, 4, gz, face)
    for (dx, dz) in ((-6, -6), (6, -6), (-6, 6), (6, 6)):
        p.censer(c + dx, 6, c + dz, plinth=None)
    for (dx, dz, rot) in ((-11, -11, 10), (-11, 11, 14), (11, -11, 6), (11, 11, 2)):
        west = dx < 0
        p.put(c + dx, 5, c + dz, "minecraft:polished_blackstone_wall", {"up": "true", "north": "none", "south": "none",
                                                                         "east": "none", "west": "none",
                                                                         "waterlogged": "false"})
        p.banner(c + dx, 6, c + dz, "white" if west else "black", rotation=rot,
                 patterns=(("stripe_center", "black" if west else "white"), ("border", "gray"),
                           ("triangle_top", "red")))
    # exits
    p.jigsaw_side(c, 3, 0, "north", J("bawah/teras_utara"), J("bawah/jalan_masuk"), J("bawah/jalan"),
                  final_state=DIYU["path"])
    p.jigsaw_side(c, 3, 40, "south", J("bawah/teras_selatan"), J("bawah/lorong_masuk"), J("bawah/lorong_a"),
                  final_state=DIYU["path"])
    p.jigsaw_side(40, 3, c, "east", J("bawah/teras_timur"), J("bawah/lorong_masuk"), J("bawah/lorong_b"),
                  final_state=DIYU["path"])
    p.jigsaw_side(0, 3, c, "west", J("bawah/teras_barat"), J("bawah/lorong_masuk"), J("bawah/lorong_c"),
                  final_state=DIYU["path"])
    # anchors: arrival point, return compass, site, protection of the whole court (72 b footprint)
    p.anchor(c + 0.5, 7.0, c + 0.5, "bawah/teras", protect=24)
    p.anchor(c + 0.5, 7.0, c - 1.5, "bawah/gerbang_pulang", protect=4)
    p.anchor(c + 0.5, 4.0, c + 14.5, "bawah/situs", situs="bawah/istana_yama", tier=4, protect=72)
    # Bai and Hei Wuchang, who lead the dead below, wait beside the arrival circle (BawahAnchors "wuchang")
    p.anchor(c - 2.5, 7.0, c + 2.5, "bawah/wuchang", role="putih", protect=0)
    p.anchor(c + 3.5, 7.0, c + 2.5, "bawah/wuchang", role="hitam", protect=0)
    return p.finish(out_path("bawah", "istana/teras"))


def jalan_huangquan():
    """Jalan Huangquan: the Yellow Springs road, mud bricks between fields of red spider lilies."""
    p = DPiece(15, 14, 30, seed=63002, palette=DIYU)
    p.fill(0, 0, 0, 14, 2, 29, "found", alt="found_alt", alt_chance=0.25)
    p.air(0, 4, 0, 14, 13, 29)
    for z in range(30):
        for x in range(15):
            if 5 <= x <= 9:
                p.put(x, 3, z, "path" if p.rng.random() > 0.22 else "path_alt")
            elif x in (4, 10):
                p.put(x, 3, z, "trim" if z % 6 == 0 else "found")
        p.lily_bed(0, z, 3, z, 3, density=0.62)
        p.lily_bed(11, z, 14, z, 3, density=0.62)
    for z in range(2, 30, 6):
        p.lamp_post(4, 3, z, h=3, base=None)
        p.lamp_post(10, 3, z, h=3, base=None)
    # a stele naming the road and two bone cairns
    for y in range(4, 8):
        p.put(2, y, 14, "minecraft:polished_blackstone" if y < 7 else "trim")
    p.put(1, 4, 14, "minecraft:polished_blackstone_stairs", {"facing": "east", "half": "bottom", "shape": "straight",
                                                             "waterlogged": "false"})
    p.put(3, 4, 14, "minecraft:polished_blackstone_stairs", {"facing": "west", "half": "bottom", "shape": "straight",
                                                             "waterlogged": "false"})
    for (x, z) in ((12, 8), (12, 22)):
        p.put(x, 4, z, "bone", {"axis": "y"})
        p.put(x, 5, z, "minecraft:skeleton_skull", {"rotation": str(p.rng.randint(0, 15))})
    p.jigsaw_side(7, 3, 29, "south", J("bawah/jalan_masuk"), EMPTY, EMPTY, final_state=DIYU["path"])
    p.jigsaw_side(7, 3, 0, "north", J("bawah/jalan_keluar"), J("bawah/jembatan_masuk"), J("bawah/jembatan"),
                  final_state=DIYU["path"])
    p.anchor(7.5, 4.0, 15.5, "bawah/huangquan", protect=12)
    return p.finish(out_path("bawah", "istana/jalan_huangquan"))


def jembatan_naihe():
    """Jembatan Naihe over the Wangchuan: an arched red-railed bridge over a 20-wide river basin (the river is held by
    stone embankments inside the piece, so no water ever flows out of it)."""
    p = DPiece(48, 24, 40, seed=63003, palette=DIYU)
    F = 5
    p.fill(0, 0, 0, 47, F - 1, 39, "found", alt="found_alt", alt_chance=0.2)
    p.air(0, F + 1, 0, 47, 23, 39)
    # banks
    for x in range(48):
        for z in list(range(0, 10)) + list(range(30, 40)):
            p.put(x, F, z, "floor" if 21 <= x <= 26 else ("dirt" if p.rng.random() > 0.3 else "dirt_alt"))
            if not (19 <= x <= 28) and p.rng.random() < 0.45 and z not in (9, 30):
                p.flower(x, F + 1, z)
    # river basin: bed y0 basalt, water y1..F-1, embankment walls at x 0/47 and z 9/30
    for x in range(1, 47):
        for z in range(10, 30):
            if p.rng.random() > 0.3:
                p.put(x, 0, z, "minecraft:smooth_basalt")
            else:
                p.put(x, 0, z, "minecraft:polished_basalt", {"axis": "y"})
            p.water(x, 1, z, x, F - 1, z)
            p.air(x, F, z, x, F, z)
    for z in range(9, 31):
        for y in range(0, F + 1):
            p.put(0, y, z, "found")
            p.put(47, y, z, "found")
        p.put(0, F + 1, z, "curb")
        p.put(47, F + 1, z, "curb")
    for x in range(48):
        p.put(x, F, 9, "trim" if x % 4 == 0 else "found")
        p.put(x, F, 30, "trim" if x % 4 == 0 else "found")
    # glowing pickles on the river bed (cold soul lights under the water) and lily pads
    for i in range(26):
        x, z = p.rng.randint(2, 45), p.rng.randint(11, 28)
        if not (20 <= x <= 27):
            p.put(x, 1, z, "minecraft:sea_pickle", {"pickles": str(p.rng.randint(2, 4)), "waterlogged": "true"})
    for i in range(30):
        x, z = p.rng.randint(2, 45), p.rng.randint(11, 28)
        if not (19 <= x <= 28):
            p.put(x, F, z, "minecraft:lily_pad")
    # the arched bridge: deck 5 wide (x 21..25) from z 7 to z 32, peak F+3 at the middle
    z0, z1 = 7, 32
    for z in range(z0, z1 + 1):
        t = (z - z0) / (z1 - z0)
        h = F + int(round(3.0 * math.sin(math.pi * t)))
        for x in range(21, 26):
            p.put(x, h, z, "red" if x in (21, 25) else "floor")
            for y in range(F, h):
                if x in (21, 25) or z < 10 or z > 29:
                    p.put(x, y, z, "red")
            p.air(x, h + 1, z, x, h + 4, z)
        p.put(20, h + 1, z, "rail")
        p.put(26, h + 1, z, "rail")
        p.put(20, h, z, "red")
        p.put(26, h, z, "red")
        if z % 4 == 3:
            for x in (20, 26):
                p.put(x, h + 1, z, "trim")
                p.put(x, h + 2, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    # stair treads where the deck height changes (walk without jumping)
    prev = None
    for z in range(z0, z1 + 1):
        t = (z - z0) / (z1 - z0)
        h = F + int(round(3.0 * math.sin(math.pi * t)))
        if prev is not None and h != prev:
            zz, hh, face = (z - 1, prev + 1, "south") if h > prev else (z, h + 1, "north")
            for x in range(22, 25):
                p.stairs(x, hh, zz, "minecraft:polished_blackstone_brick_stairs", face)
        prev = h
    # piers and arches under the bridge
    for zp in (14, 20, 25):
        for x in range(21, 26):
            for y in range(0, F):
                p.put(x, y, zp, "found")
    # Sanshengshi, the Three-Lives Stone, on the far bank
    for y in range(F + 1, F + 7):
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if y < F + 6 or (dx, dz) == (0, 0):
                p.put(14 + dx, y, 34 + dz, "minecraft:polished_blackstone" if y % 2 else "minecraft:blackstone")
    p.put(14, F + 7, 34, "trim")
    for (x, z) in ((6, 4), (40, 4), (6, 35), (40, 35)):
        p.lamp_post(x, F, z, h=3, base=None)
    # detail pass: river lanterns for the dead drifting on the Wangchuan (lit candles on lily pads), weeping pale
    # willows on the banks, guardian lions and censers at both bridge heads
    n = 0
    while n < 16:
        x, z = p.rng.randint(3, 44), p.rng.randint(12, 27)
        if not (18 <= x <= 29) and p.get(x, F, z) in (None, "minecraft:air"):
            p.floating_candle(x, F, z, p.rng.choice(("minecraft:white_candle", "minecraft:white_candle",
                                                      "minecraft:yellow_candle", "minecraft:red_candle")))
            n += 1
    for (x, z) in ((12, 4), (35, 35), (2, 33), (45, 6)):
        p.put(x, F, z, "dirt")
        p.dead_tree(x, F + 1, z, h=6)
        for (dx, dz) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            if p.inside(x + dx, F + 4, z + dz) and p.get(x + dx, F + 4, z + dz) in (None, "minecraft:air"):
                p.put(x + dx, F + 4, z + dz, "minecraft:pale_hanging_moss", {"tip": "true"})
    for (x, face, z) in ((19, "north", 3), (27, "north", 3), (19, "south", 36), (27, "south", 36)):
        p.guardian(x, F + 1, z, face)
    for (x, z) in ((19, 6), (27, 6), (19, 33), (27, 33)):
        p.censer(x, F + 1, z, plinth="trim")
    p.jigsaw_side(23, F, 39, "south", J("bawah/jembatan_masuk"), EMPTY, EMPTY, final_state=DIYU["floor"])
    p.jigsaw_side(23, F, 0, "north", J("bawah/jembatan_keluar"), J("bawah/mengpo_masuk"), J("bawah/mengpo"),
                  final_state=DIYU["floor"])
    p.anchor(23.5, F + 4.0, 20.5, "bawah/naihe", protect=24)
    return p.finish(out_path("bawah", "istana/jembatan_naihe"))


def paviliun_mengpo():
    """Paviliun Meng Po: an open pavilion with a soup cauldron over soul fire, long tables, a lily pond and pale trees."""
    p = DPiece(33, 28, 33, seed=63004, palette=DIYU)
    p.fill(0, 0, 0, 32, 2, 32, "found", alt="found_alt", alt_chance=0.2)
    p.floor(0, 3, 0, 32, 32, "floor", alt="floor_alt", alt_chance=0.12)
    p.air(0, 4, 0, 32, 27, 32)
    # path from the entrance
    for z in range(20, 33):
        for x in range(14, 19):
            p.put(x, 3, z, "path" if p.rng.random() > 0.2 else "path_alt")
    # the pavilion 17 x 13: red pillars on stone bases, a two-tier hip roof
    x0, z0, x1, z1 = 8, 5, 24, 17
    p.fill(x0, 3, z0, x1, 3, z1, "minecraft:polished_blackstone")
    p.fill(x0 + 1, 4, z0 + 1, x1 - 1, 4, z1 - 1, "minecraft:dark_oak_planks")
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1):
            p.pillar(x, 4, 10, z, role="pillar", base="trim", cap="beam")
    for z in range(z0, z1 + 1, 4):
        for x in (x0, x1):
            p.pillar(x, 4, 10, z, role="pillar", base="trim", cap="beam")
    for x in range(x0, x1 + 1):
        p.put(x, 10, z0, "beam", {"axis": "x"})
        p.put(x, 10, z1, "beam", {"axis": "x"})
    for z in range(z0, z1 + 1):
        p.put(x0, 10, z, "beam", {"axis": "z"})
        p.put(x1, 10, z, "beam", {"axis": "z"})
    p.fill(x0 + 1, 10, z0 + 1, x1 - 1, 10, z1 - 1, "minecraft:dark_oak_planks")
    p.roof_hip(x0, 11, z0, x1, z1, role="minecraft:nether_brick_stairs", slab_role="minecraft:nether_brick_slab",
               full_role="minecraft:nether_bricks", overhang=2, tiers=2, tier_gap=2, band_role="minecraft:red_nether_bricks")
    for x in range(x0 + 2, x1 - 1, 4):
        p.lantern(x, 10, z0 + 3, chain=2)
        p.lantern(x, 10, z1 - 3, chain=2)
    # Meng Po's cauldron over soul fire, ladle shelf, bowls on tables
    cx, cz = 16, 9
    p.put(cx, 4, cz, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                  "waterlogged": "false"})
    p.put(cx, 5, cz, "minecraft:water_cauldron", {"level": "3"})
    for dx in (-1, 1):
        p.put(cx + dx, 4, cz, "minecraft:polished_blackstone_bricks")
        p.put(cx + dx, 5, cz, "minecraft:decorated_pot")
    for z in (12, 14):
        for x in range(11, 22):
            if x == 16:
                continue
            p.put(x, 5, z, "minecraft:dark_oak_slab", {"type": "top", "waterlogged": "false"})
            p.put(x, 4, z, "minecraft:dark_oak_fence") if x in (11, 15, 17, 21) else None
            if x % 3 == 0:
                p.candle(x, 6, z, "minecraft:white_candle", n=p.rng.randint(1, 3))
        for x in range(11, 22, 2):
            if x != 16:
                p.stairs(x, 5, z - 1 if z == 12 else z + 1, "minecraft:crimson_stairs", "north" if z == 12 else "south")
    # lily pond in front, pale trees, offerings
    for x in range(3, 9):
        for z in range(21, 28):
            edge = x in (3, 8) or z in (21, 27)
            if edge:
                p.put(x, 3, z, "trim")
            else:
                p.put(x, 2, z, "minecraft:smooth_basalt")
                p.water(x, 3, z, x, 3, z)
                if p.rng.random() < 0.35:
                    p.put(x, 4, z, "minecraft:lily_pad")
    p.lily_bed(22, 20, 30, 30, 3, density=0.45)
    p.dead_tree(26, 4, 24, h=7)
    p.dead_tree(5, 4, 8, h=6)
    p.dead_tree(28, 4, 6, h=6)
    for (x, z) in ((13, 30), (19, 30), (2, 2), (30, 2)):
        p.lamp_post(x, 3, z, h=3, base=None)
    # detail pass: soup bowls on the long tables, Meng Po's kitchen (barrels of herbs, the smoker, jars), red banners
    # on the front pillars, the queue of the dead held by chain ropes along the path
    for z in (12, 14):
        for x in range(11, 22):
            if x != 16 and x % 3 == 1:
                p.put(x, 6, z, "minecraft:flower_pot")
    for (x, z) in ((9, 6), (10, 6), (9, 7)):
        p.put(x, 5, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(23, 5, 6, "minecraft:smoker", {"facing": "south", "lit": "true"})
    for x in (21, 22):
        p.put(x, 5, 6, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    p.put(23, 5, 7, "minecraft:cauldron")
    for x in (8, 24):
        p.banner(x, 8, z1 + 1, "red", facing="south", patterns=(("rhombus", "white"), ("circle", "black"),
                                                                ("border", "black")))
    for x in (12, 20):
        p.banner(x, 8, z1 + 1, "black", facing="south", patterns=(("stripe_center", "white"), ("triangle_top", "red")))
    for z in range(20, 32):
        for x in (13, 19):
            if z % 3 == 2:
                p.put(x, 4, z, "minecraft:crimson_fence")
                p.put(x, 5, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"}) if z % 6 == 2 else None
            else:
                p.put(x, 4, z, "minecraft:iron_chain", {"axis": "z", "waterlogged": "false"})
    for x in (12, 20):
        p.censer(x, 4, 19, plinth="trim")
    p.jigsaw_side(16, 3, 32, "south", J("bawah/mengpo_masuk"), EMPTY, EMPTY, final_state=DIYU["path"])
    p.anchor(16.5, 5.0, 10.5, "bawah/meng_po", protect=20)
    return p.finish(out_path("bawah", "istana/paviliun_mengpo"))


COURT_KINGS = ["qinguang", "chujiang", "songdi", "wuguan", "yanluo", "biancheng", "taishan", "dushi", "pingdeng",
               "zhuanlun"]
COURT_ACCENT = ["minecraft:tinted_glass", "minecraft:packed_ice", "minecraft:iron_bars", "minecraft:red_stained_glass",
                "minecraft:gold_block", "minecraft:grindstone", "minecraft:magma_block", "minecraft:iron_chain",
                "minecraft:iron_bars", "minecraft:gilded_blackstone"]
COURT_DYE = ["white", "light_blue", "gray", "red", "yellow", "brown", "orange", "black", "purple", "lime"]
COURT_CANDLE = ["minecraft:white_candle", "minecraft:light_blue_candle", "minecraft:gray_candle", "minecraft:red_candle",
                "minecraft:yellow_candle", "minecraft:brown_candle", "minecraft:orange_candle", "minecraft:black_candle",
                "minecraft:purple_candle", "minecraft:lime_candle"]


def lorong(name, pools, end_pool):
    """A court street 15 x 48: 7-wide paved road, curbs, lantern posts; court connectors on both sides at z 24 and 1."""
    p = DPiece(15, 14, 48, seed=63010 + ord(name[-1]), palette=DIYU)
    p.fill(0, 0, 0, 14, 2, 47, "found", alt="found_alt", alt_chance=0.2)
    p.air(0, 4, 0, 14, 13, 47)
    for z in range(48):
        for x in range(15):
            if 4 <= x <= 10:
                p.put(x, 3, z, "floor" if p.rng.random() > 0.12 else "floor_alt")
            elif x in (3, 11):
                p.put(x, 3, z, "trim")
            else:
                p.put(x, 3, z, "dirt" if p.rng.random() > 0.3 else "dirt_alt")
                if p.rng.random() < 0.35 and not (20 <= z <= 28) and z > 4:
                    p.flower(x, 4, z)
    for z in range(3, 48, 7):
        p.lamp_post(3, 3, z, h=3, base=None)
        p.lamp_post(11, 3, z, h=3, base=None)
    # bone-white guardian steles along the road
    for z in (12, 36):
        for x in (1, 13):
            p.put(x, 4, z, "bone", {"axis": "y"})
            p.put(x, 5, z, "bone", {"axis": "y"})
            p.put(x, 6, z, "minecraft:wither_skeleton_skull", {"rotation": "0"})
    p.jigsaw_side(7, 3, 47, "south", J("bawah/lorong_masuk"), EMPTY, EMPTY, final_state=DIYU["floor"])
    sides = [(0, 24, "west"), (14, 24, "east"), (0, 1, "west"), (14, 1, "east")]
    for (x, z, face), target in zip(sides, pools):
        if target:
            p.jigsaw_side(x, 3, z, face, J("bawah/lorong_sisi"), J("bawah/pengadilan_masuk"), J("bawah/" + target),
                          final_state=DIYU["floor"])
    if end_pool:
        p.jigsaw_side(7, 3, 0, "north", J("bawah/lorong_ujung"), J("bawah/ujung_masuk"), J("bawah/" + end_pool),
                      final_state=DIYU["floor"])
    return p.finish(out_path("bawah", "istana/" + name))


def pengadilan(n):
    """Court n (1-10) of the Ten Courts: a walled yard and a hall with the judge's dais, two guard posts (Ox-Head and
    Horse-Face), the court's own accent (mirror, ice, bars, blood glass, scales, millstones, fire, chains, iron trees,
    the wheel) and its candles."""
    i = n - 1
    p = DPiece(21, 22, 25, seed=63100 + n, palette=DIYU)
    p.fill(0, 0, 0, 20, 2, 24, "found", alt="found_alt", alt_chance=0.2)
    p.floor(0, 3, 0, 20, 24, "floor", alt="floor_alt", alt_chance=0.1)
    p.air(0, 4, 0, 20, 21, 24)
    # yard wall with gate at the street side (z = 24)
    for x in range(21):
        for z in (0, 24):
            if z == 24 and 8 <= x <= 12:
                continue
            p.put(x, 4, z, "wall")
            p.put(x, 5, z, "curb")
    for z in range(25):
        for x in (0, 20):
            p.put(x, 4, z, "wall")
            p.put(x, 5, z, "curb")
    p.paifang(8, 4, 23, 12, h=6)
    # the hall: x 2..18, z 2..15, walls of nether brick, red pillars, windows of iron bars
    p.walls(2, 4, 2, 18, 9, 15, role="wall", pillar="pillar", windows=4, window_role="window", door=("south", 3))
    p.fill(2, 10, 2, 18, 10, 15, "minecraft:dark_oak_planks")
    ridge = p.roof_hip(2, 11, 2, 18, 15, role="roof", slab_role="roof_slab", full_role="roof_full", overhang=2, tiers=1)
    if ridge < 21:
        p.ridge_pearl(10, ridge, 8, "minecraft:gilded_blackstone", flame=ridge + 1 < 22)
    # dais at the back with the judge's desk and throne
    p.fill(5, 4, 3, 15, 4, 6, "minecraft:polished_blackstone_bricks")
    p.fill(6, 5, 3, 14, 5, 4, "minecraft:polished_blackstone_bricks")
    for x in range(6, 15):
        p.stairs(x, 4, 7, "minecraft:polished_blackstone_brick_stairs", "north")
    for x in range(7, 14):
        p.put(x, 5, 6, "minecraft:dark_oak_slab", {"type": "top", "waterlogged": "false"})
    p.put(10, 6, 3, "minecraft:crimson_stairs", {"facing": "north", "half": "bottom", "shape": "straight",
                                                  "waterlogged": "false"})
    p.put(10, 7, 3, "minecraft:red_nether_bricks")
    p.put(9, 6, 3, "red")
    p.put(11, 6, 3, "red")
    p.put(8, 6, 6, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    p.candle(12, 6, 6, COURT_CANDLE[i], n=3)
    p.candle(7, 6, 6, COURT_CANDLE[i], n=2)
    # plaque above the door: the court's number in gilded blocks
    for k in range(min(n, 5)):
        p.put(8 + k, 9, 15, "gold")
    if n > 5:
        for k in range(n - 5):
            p.put(8 + k, 8, 15, "gold")
    # banners of the court: carpets on the floor, candles along the aisle
    for z in range(8, 15):
        p.put(10, 4, z, "minecraft:red_carpet")
        if z % 2 == 0:
            p.candle(6, 4, z, COURT_CANDLE[i])
            p.candle(14, 4, z, COURT_CANDLE[i])
    # the court's accent
    acc = COURT_ACCENT[i]
    if n == 1:      # Niejing Tai: the Mirror of Retribution
        for y in range(5, 9):
            for x in range(16, 18):
                p.put(x, y, 9, "minecraft:tinted_glass")
        for y in range(4, 10):
            p.put(15, y, 9, "gold")
            p.put(18, y, 9, "gold")
        p.put(16, 4, 9, "gold")
        p.put(17, 4, 9, "gold")
        p.put(16, 9, 9, "gold")
        p.put(17, 9, 9, "gold")
    elif n == 2:    # the cold hell
        for (x, z) in ((4, 9), (4, 12), (16, 9), (16, 12)):
            p.put(x, 4, z, "minecraft:packed_ice")
            p.put(x, 5, z, "minecraft:blue_ice")
    elif n in (3, 9):  # cages / iron trees
        for (x, z) in ((4, 10), (16, 10)):
            for y in range(4, 8):
                p.put(x, y, z, "minecraft:iron_bars")
            p.put(x, 8, z, "minecraft:iron_chain", {"axis": "y"})
    elif n == 4:    # the pool of blood
        for x in range(4, 7):
            for z in range(9, 13):
                p.put(x, 3, z, "minecraft:red_stained_glass")
                p.put(x, 2, z, "minecraft:shroomlight")
    elif n == 5:    # Yanluo's scales
        for y in range(4, 8):
            p.put(16, y, 10, "minecraft:iron_chain", {"axis": "y"})
        p.put(16, 8, 10, "gold")
        p.put(15, 8, 10, "minecraft:iron_chain", {"axis": "x"})
        p.put(17, 8, 10, "minecraft:iron_chain", {"axis": "x"})
        p.put(14, 7, 10, "minecraft:cauldron")
        p.put(18, 6, 10, "minecraft:cauldron")
        p.put(18, 7, 10, "minecraft:iron_chain", {"axis": "y"})
    elif n == 6:    # millstones
        for (x, z) in ((4, 10), (16, 10)):
            p.put(x, 4, z, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    elif n == 7:    # the cauldron of boiling oil
        for (x, z) in ((4, 10), (16, 10)):
            p.put(x, 3, z, "minecraft:magma_block")
            p.put(x, 4, z, "minecraft:lava_cauldron")
    elif n == 8:    # chains of the eighth court
        for x in range(4, 17, 3):
            p.lantern(x, 10, 11, chain=3, role="minecraft:soul_lantern")
    elif n == 10:   # the wheel of rebirth on the back wall, behind the throne
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                d = math.hypot(dx, dy)
                if 1.6 <= d <= 2.6:
                    p.put(10 + dx, 7 + dy, 2, "gold")
                elif d < 1.6 and (dx == 0 or dy == 0):
                    p.put(10 + dx, 7 + dy, 2, "minecraft:chiseled_polished_blackstone")
    # yard: lily beds, censers, guard posts
    p.lily_bed(1, 17, 6, 22, 3, density=0.5)
    p.lily_bed(14, 17, 19, 22, 3, density=0.5)
    # detail pass: the ledgers of every soul's deeds on shelves beside the dais, the court's banners behind the
    # judge, a black kneeling mat before the steps, guardian lions inside the gate and a censer in the yard
    p.bookshelf_wall(3, 4, 3, 3, 6, 5, "east")
    p.bookshelf_wall(17, 4, 3, 17, 6, 5, "west")
    dye = COURT_DYE[i]
    for x in (7, 13):
        p.banner(x, 8, 3, dye, facing="south", patterns=(("stripe_center", "black"), ("border", "black"),
                                                        ("circle", "yellow" if n in (5, 10) else "white")))
    for x in (9, 11):
        p.put(x, 4, 8, "minecraft:black_carpet")
    p.guardian(6, 4, 21, "south")
    p.guardian(14, 4, 21, "south")
    p.censer(10, 4, 19, plinth="trim")
    for x in (3, 17):
        p.put(x, 4, 16, "trim")
        p.put(x, 5, 16, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                     "waterlogged": "false"})
    p.jigsaw_side(10, 3, 24, "south", J("bawah/pengadilan_masuk"), EMPTY, EMPTY, final_state=DIYU["floor"])
    p.anchor(10.5, 5.0, 5.5, "bawah/pengadilan", court=n, raja=COURT_KINGS[i], protect=16)
    p.anchor(7.5, 4.0, 13.5, "bawah/penjaga", role="lembu", court=n, protect=0)
    p.anchor(13.5, 4.0, 13.5, "bawah/penjaga", role="kuda", court=n, protect=0)
    return p.finish(out_path("bawah", "istana/pengadilan_%d" % n))


def istana_yanluo():
    """Istana Yanluo, King Yama's palace at the end of the western street: a raised hall with a two-tier hip roof, the
    throne, Judge Cui's table and the Book of Life and Death on its lectern."""
    p = DPiece(41, 40, 41, seed=63200, palette=DIYU)
    p.fill(0, 0, 0, 40, 2, 40, "found", alt="found_alt", alt_chance=0.2)
    p.floor(0, 3, 0, 40, 40, "floor", alt="floor_alt", alt_chance=0.1)
    p.air(0, 4, 0, 40, 39, 40)
    # approach from z = 40: a wide stair onto the platform (y 4..6)
    p.fill(6, 4, 4, 34, 6, 30, "minecraft:polished_blackstone")
    for k in range(3):
        for x in range(14, 27):
            p.stairs(x, 4 + k, 33 - k, "minecraft:polished_blackstone_brick_stairs", "north")
            for y in range(4, 4 + k):
                p.put(x, y, 33 - k, "minecraft:polished_blackstone")
    for x in range(6, 35):
        for z in (4, 30):
            p.put(x, 7, z, "curb")
    for z in range(4, 31):
        for x in (6, 34):
            p.put(x, 7, z, "curb")
    for x in range(14, 27):
        p.air(x, 7, 30, x, 7, 30)
    # the hall
    p.walls(9, 7, 7, 31, 15, 26, role="wall", pillar="pillar", windows=4, window_role="window", door=("south", 5))
    p.fill(9, 16, 7, 31, 16, 26, "minecraft:dark_oak_planks")
    p.roof_hip(9, 17, 7, 31, 26, role="roof", slab_role="roof_slab", full_role="roof_full", overhang=2, tiers=2,
               tier_gap=2, tier_band=2, band_role="red")
    for z in range(10, 25, 4):
        for x in (13, 27):
            p.pillar(x, 7, 15, z, role="pillar", base="gold", cap="beam")
    # throne dais
    p.fill(15, 7, 8, 25, 7, 12, "minecraft:polished_blackstone_bricks")
    p.fill(17, 8, 8, 23, 8, 10, "minecraft:polished_blackstone_bricks")
    for x in range(15, 26):
        p.stairs(x, 7, 13, "minecraft:polished_blackstone_brick_stairs", "north")
    p.put(20, 9, 8, "minecraft:crimson_stairs", {"facing": "north", "half": "bottom", "shape": "straight",
                                                  "waterlogged": "false"})
    for y in range(9, 13):
        p.put(20, y + 1, 8, "gold" if y == 12 else "red")
    for x in (18, 22):
        for y in range(9, 12):
            p.put(x, y, 8, "gold" if y == 11 else "red")
    # Judge Cui's table with the Book and brushes
    p.put(20, 9, 11, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    for x in (17, 23):
        p.put(x, 9, 11, "minecraft:dark_oak_slab", {"type": "top", "waterlogged": "false"})
        p.candle(x, 10, 11, "minecraft:red_candle", n=4)
    # aisle with red carpet and soul fire braziers
    for z in range(13, 27):
        for x in (19, 20, 21):
            p.put(x, 7, z, "minecraft:red_carpet")
    for z in (15, 19, 23):
        for x in (16, 24):
            p.put(x, 7, z, "trim")
            p.put(x, 8, z, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                        "waterlogged": "false"})
    for x in range(12, 30, 4):
        for z in (10, 18, 24):
            p.lantern(x, 16, z, chain=2)
    # courtyard: two dead trees, lily beds, guardian beasts (bone pillars)
    p.lily_bed(1, 31, 12, 39, 3, density=0.5)
    p.lily_bed(28, 31, 39, 39, 3, density=0.5)
    p.dead_tree(5, 4, 35, h=8)
    p.dead_tree(35, 4, 35, h=8)
    for x in (11, 29):
        for y in range(4, 7):
            p.put(x, y, 36, "bone", {"axis": "y"})
        p.put(x, 7, 36, "minecraft:wither_skeleton_skull", {"rotation": "8"})
    for x in range(14, 27):
        for z in range(34, 41):
            p.put(x, 3, z, "path" if p.rng.random() > 0.2 else "path_alt")
    p.paifang(15, 4, 38, 25, h=8)
    p.jigsaw_side(20, 3, 40, "south", J("bawah/ujung_masuk"), EMPTY, EMPTY, final_state=DIYU["path"])
    p.anchor(20.5, 9.0, 9.5, "bawah/yama", protect=32)
    p.anchor(20.5, 10.0, 11.5, "bawah/buku", protect=8)
    p.anchor(17.5, 7.0, 15.5, "bawah/penjaga", role="lembu", court=11, protect=0)
    p.anchor(23.5, 7.0, 15.5, "bawah/penjaga", role="kuda", court=11, protect=0)
    return p.finish(out_path("bawah", "istana/istana_yanluo"))


def kuil_dizang():
    """End of the southern street: a small shrine of Dizang (Ksitigarbha), who vowed to empty the hells."""
    p = DPiece(15, 16, 15, seed=63210, palette=DIYU)
    p.fill(0, 0, 0, 14, 2, 14, "found")
    p.floor(0, 3, 0, 14, 14, "floor")
    p.air(0, 4, 0, 14, 15, 14)
    p.walls(3, 4, 2, 11, 8, 10, role="wall", pillar="pillar", windows=0, door=("south", 3))
    p.fill(3, 9, 2, 11, 9, 10, "minecraft:dark_oak_planks")
    p.roof_hip(3, 10, 2, 11, 10, overhang=1)
    # a seated figure of gold and blackstone on a lotus of candles
    p.put(7, 4, 4, "minecraft:polished_blackstone_bricks")
    p.put(7, 5, 4, "gold")
    p.put(7, 6, 4, "minecraft:gold_block")
    p.put(7, 7, 4, "minecraft:raw_gold_block")
    for dx in (-1, 1):
        p.put(7 + dx, 5, 4, "minecraft:gilded_blackstone")
        p.candle(7 + dx, 4, 6, "minecraft:yellow_candle", n=3)
    p.lantern(7, 9, 7, chain=1)
    p.lily_bed(0, 11, 4, 14, 3)
    p.lily_bed(10, 11, 14, 14, 3)
    p.jigsaw_side(7, 3, 14, "south", J("bawah/ujung_masuk"), EMPTY, EMPTY, final_state=DIYU["floor"])
    p.anchor(7.5, 4.0, 7.5, "bawah/dizang", protect=8)
    return p.finish(out_path("bawah", "istana/kuil_dizang"))


def pos_yaksha():
    """End of the eastern street: the Yaksha watch post (the patrols of the courts start here)."""
    p = DPiece(15, 16, 15, seed=63211, palette=DIYU)
    p.fill(0, 0, 0, 14, 2, 14, "found")
    p.floor(0, 3, 0, 14, 14, "floor")
    p.air(0, 4, 0, 14, 15, 14)
    p.walls(2, 4, 2, 12, 7, 10, role="wall_alt", pillar="red", windows=3, door=("south", 3))
    p.fill(2, 8, 2, 12, 8, 10, "minecraft:nether_bricks")
    for x in range(2, 13):
        for z in (2, 10):
            if x % 2 == 0:
                p.put(x, 9, z, "minecraft:nether_brick_fence")
    # weapon racks (iron bars and chains) and braziers
    for x in (4, 10):
        p.put(x, 4, 3, "minecraft:iron_bars")
        p.put(x, 5, 3, "minecraft:iron_bars")
    p.put(7, 4, 4, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                "waterlogged": "false"})
    p.jigsaw_side(7, 3, 14, "south", J("bawah/ujung_masuk"), EMPTY, EMPTY, final_state=DIYU["floor"])
    p.anchor(7.5, 4.0, 7.5, "bawah/yaksha", protect=8)
    return p.finish(out_path("bawah", "istana/pos_yaksha"))


# ------------------------------------------------------------------------------------------------- Desa Arwah
def desa_pusat():
    """Ghost village square: a dry well, an old pale oak, paper lanterns (white candles) and a spirit tablet stand."""
    p = DPiece(23, 16, 23, seed=63301, palette=DIYU)
    p.fill(0, 0, 0, 22, 2, 22, "minecraft:soul_soil")
    p.air(0, 4, 0, 22, 15, 22)
    for x in range(23):
        for z in range(23):
            d = math.hypot(x - 11, z - 11)
            p.put(x, 3, z, "minecraft:packed_mud" if d < 9 else ("minecraft:soul_soil" if p.rng.random() > 0.4 else
                                                                  "minecraft:coarse_dirt"))
    for a in range(23):
        for w in (-1, 0, 1):
            p.put(11 + w, 3, a, "minecraft:mud_bricks")
            p.put(a, 3, 11 + w, "minecraft:mud_bricks")
    # the well
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                p.put(11 + dx, 4, 11 + dz, "minecraft:cobblestone_wall" if (dx + dz) % 2 else "minecraft:mossy_cobblestone")
            else:
                p.air(11 + dx, 1, 11 + dz, 11 + dx, 3, 11 + dz)
                p.put(11 + dx, 0, 11 + dz, "minecraft:smooth_basalt")
    for dx in (-2, 2):
        for y in range(5, 8):
            p.put(11 + dx, y, 11, "minecraft:pale_oak_fence")
    for x in range(9, 14):
        p.put(x, 8, 11, "minecraft:pale_oak_slab", {"type": "bottom", "waterlogged": "false"})
    p.lantern(11, 8, 11, chain=1, role="minecraft:soul_lantern")
    p.dead_tree(4, 4, 4, h=8)
    p.dead_tree(18, 4, 18, h=7)
    for (x, z) in ((4, 18), (18, 4)):
        p.lamp_post(x, 3, z, h=2, post="minecraft:pale_oak_fence", glow="minecraft:soul_lantern", base=None)
    for x in (7, 15):
        p.candle(x, 4, 7, "minecraft:white_candle", n=2)
        p.candle(x, 4, 15, "minecraft:white_candle", n=2)
    for face, (x, z) in (("north", (11, 0)), ("south", (11, 22)), ("east", (22, 11)), ("west", (0, 11))):
        p.jigsaw_side(x, 3, z, face, J("bawah/desa_sisi"), J("bawah/desa_jalan_masuk"), J("bawah/desa_jalan"),
                      final_state="minecraft:mud_bricks")
    p.anchor(11.5, 4.0, 14.5, "bawah/arwah", peran="pesan", protect=0)
    p.anchor(11.5, 4.0, 18.5, "bawah/situs", situs="bawah/desa_arwah", tier=3, protect=0)
    return p.finish(out_path("bawah", "desa/pusat"))


def desa_jalan(v=0):
    """A village lane; v 1 adds a roadside earth-god shrine (Tudigong's niche: tablet, candles, offerings)."""
    p = DPiece(7, 12, 16, seed=63302 + 7 * v, palette=DIYU)
    p.fill(0, 0, 0, 6, 2, 15, "minecraft:soul_soil")
    p.air(0, 4, 0, 6, 11, 15)
    for z in range(16):
        for x in range(7):
            p.put(x, 3, z, "minecraft:mud_bricks" if 2 <= x <= 4 else ("minecraft:soul_soil" if p.rng.random() > 0.5
                                                                       else "minecraft:coarse_dirt"))
            if x in (0, 6) and p.rng.random() < 0.3:
                p.flower(x, 4, z)
    p.lamp_post(0, 3, 8, h=2, post="minecraft:pale_oak_fence", glow="minecraft:soul_lantern", base=None)
    if v == 1:
        # the earth god's niche on the west verge: a little shrine of blackstone, a tablet, candles, a bowl
        for z in range(3, 6):
            p.put(0, 4, z, "minecraft:polished_blackstone_bricks")
            p.put(0, 7, z, "minecraft:blackstone_slab", {"type": "bottom", "waterlogged": "false"})
        for z in (3, 5):
            p.put(0, 5, z, "minecraft:pale_oak_fence")
            p.put(0, 6, z, "minecraft:pale_oak_fence")
        p.put(0, 5, 4, "minecraft:dark_oak_trapdoor", {"facing": "east", "half": "bottom", "open": "true",
                                                       "powered": "false", "waterlogged": "false"})
        p.put(1, 4, 4, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})
        p.candle(1, 5, 3, "minecraft:red_candle", n=2)
        p.put(1, 5, 5, "minecraft:flower_pot")
        p.put(6, 4, 12, "minecraft:decorated_pot", {"facing": "west", "cracked": "true", "waterlogged": "false"})
        p.put(6, 4, 2, "minecraft:skeleton_skull", {"rotation": "4"})
    p.jigsaw_side(3, 3, 15, "south", J("bawah/desa_jalan_masuk"), EMPTY, EMPTY, final_state="minecraft:mud_bricks")
    p.jigsaw_side(3, 3, 0, "north", J("bawah/desa_jalan_ujung"), J("bawah/desa_rumah_masuk"), J("bawah/desa_rumah"),
                  final_state="minecraft:mud_bricks")
    p.jigsaw_side(6, 3, 8, "east", J("bawah/desa_jalan_sisi"), J("bawah/desa_rumah_masuk"), J("bawah/desa_rumah"),
                  final_state="minecraft:mud_bricks")
    return p.finish(out_path("bawah", "desa/jalan" if v == 0 else "desa/jalan_%d" % (v + 1)))


def desa_rumah(v):
    """Abandoned ghost houses: pale oak and blackstone, cobwebs, a broken table, candles for the dead."""
    sizes = [(11, 11), (13, 9), (9, 9)]
    sx, sz = sizes[v]
    W, D = sx + 2, sz + 4
    p = DPiece(W, 16, D, seed=63310 + v, palette=DIYU)
    p.fill(0, 0, 0, W - 1, 2, D - 1, "minecraft:soul_soil")
    p.air(0, 4, 0, W - 1, 15, D - 1)
    p.fill(0, 3, 0, W - 1, 3, D - 1, "minecraft:coarse_dirt")
    x0, z0, x1, z1 = 1, 1, sx, sz
    p.fill(x0, 3, z0, x1, 3, z1, "minecraft:pale_oak_planks")
    p.walls(x0, 4, z0, x1, 7, z1, role="minecraft:pale_oak_planks", pillar="minecraft:pale_oak_log", windows=3,
            window_role="minecraft:iron_bars", door=("south", 1))
    p.roof_gable(x0, 8, z0, x1, z1, role="minecraft:blackstone_stairs", slab_role="minecraft:blackstone_slab",
                 full_role="minecraft:blackstone", along="x", overhang=1)
    for i in range(5):
        x, z = p.rng.randint(x0 + 1, x1 - 1), p.rng.randint(z0 + 1, z1 - 1)
        p.put(x, 6 + p.rng.randint(0, 1), z, "minecraft:cobweb")
    p.put(x0 + 1, 4, z0 + 1, "minecraft:pale_oak_slab", {"type": "top", "waterlogged": "false"})
    p.candle(x0 + 1, 5, z0 + 1, "minecraft:white_candle", n=3)
    p.put(x1 - 1, 4, z0 + 1, "minecraft:decorated_pot")
    cx = (x0 + x1) // 2
    if v == 0:
        p.put(cx, 4, z0 + 1, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
              {"id": "minecraft:chest", "LootTable": "kultivator:chests/dunia/dimensi/desa_arwah"})
    p.lantern(cx, 8, (z0 + z1) // 2, chain=1, role="minecraft:soul_lantern")
    for z in range(z1 + 1, D):
        p.put(cx, 3, z, "minecraft:mud_bricks")
    p.jigsaw_side(cx, 3, D - 1, "south", J("bawah/desa_rumah_masuk"), EMPTY, EMPTY, final_state="minecraft:mud_bricks")
    if v != 2:
        p.anchor(cx + 0.5, 4.0, (z0 + z1) // 2 + 0.5, "bawah/arwah", peran="tersesat" if v == 1 else "pesan", protect=0)
    else:
        p.anchor(x0 + 1.5, 4.0, z1 - 1.5, "bawah/hantu", protect=0)
    return p.finish(out_path("bawah", "desa/rumah_%d" % (v + 1)))


def desa_rumah_abu():
    """Rumah Abu, an ancestral hall of the dead village: a walled forecourt with a censer and a pale tree, the hall
    under a curled gable roof with a ridge pearl, the altar of spirit tablets (open dark trapdoors) between candles,
    ledgers and offering tables; a lost soul waits inside."""
    W, D = 17, 19
    p = DPiece(W, 18, D, seed=63320, palette=DIYU)
    p.fill(0, 0, 0, W - 1, 2, D - 1, "minecraft:soul_soil")
    p.air(0, 4, 0, W - 1, 17, D - 1)
    p.fill(0, 3, 0, W - 1, 3, D - 1, "minecraft:packed_mud")
    # hall x 1..15, z 1..8
    x0, z0, x1, z1 = 1, 1, 15, 8
    p.fill(x0, 3, z0, x1, 3, z1, "minecraft:polished_blackstone_bricks")
    p.walls(x0, 4, z0, x1, 8, z1, role="minecraft:pale_oak_planks", pillar="minecraft:crimson_stem", windows=4,
            window_role="minecraft:iron_bars", door=("south", 3))
    p.fill(x0, 9, z0, x1, 9, z1, "minecraft:dark_oak_planks")
    ridge = p.roof_gable(x0, 10, z0, x1, z1, role="minecraft:blackstone_stairs", slab_role="minecraft:blackstone_slab",
                         full_role="minecraft:blackstone", along="x", overhang=1)
    p.ridge_pearl(8, ridge + 1, (z0 + z1) // 2, "minecraft:gilded_blackstone")
    # the altar of tablets on the back wall
    p.fill(4, 4, 2, 12, 4, 3, "minecraft:polished_blackstone_bricks")
    for x in range(4, 13):
        p.put(x, 5, 2, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "bottom", "open": "true",
                                                       "powered": "false", "waterlogged": "false"})
        if x % 2 == 0:
            p.put(x, 6, 2, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "bottom", "open": "true",
                                                           "powered": "false", "waterlogged": "false"})
        p.candle(x, 5, 3, "minecraft:white_candle" if x % 3 else "minecraft:red_candle", n=1 + x % 3)
    p.table(5, 4, 5, 11, 5)
    for x in (6, 8, 10):
        p.put(x, 6, 5, "minecraft:flower_pot")
    p.bookshelf_wall(2, 4, 2, 2, 6, 3, "east")
    p.bookshelf_wall(14, 4, 2, 14, 6, 3, "west")
    for x in (4, 8, 12):
        p.lantern(x, 9, 6, chain=1, role="minecraft:soul_lantern")
    for i in range(4):
        p.put(p.rng.randint(2, 14), 8, p.rng.randint(2, 7), "minecraft:cobweb")
    # forecourt z 9..15 inside a low wall, gate at the south
    for x in range(0, W):
        if not (6 <= x <= 10):
            p.put(x, 4, 16, "minecraft:polished_blackstone_wall")
    for z in range(9, 17):
        p.put(0, 4, z, "minecraft:polished_blackstone_wall")
        p.put(W - 1, 4, z, "minecraft:polished_blackstone_wall")
    for z in range(9, D):
        for x in (7, 8, 9):
            p.put(x, 3, z, "minecraft:mud_bricks")
    p.censer(8, 4, 12, plinth="trim")
    p.put(3, 3, 12, "minecraft:coarse_dirt")
    p.dead_tree(3, 4, 12, h=6)
    p.lily_bed(11, 10, 14, 14, 3, density=0.5)
    for x in (6, 10):
        p.lamp_post(x, 3, 16, h=2, post="minecraft:pale_oak_fence", glow="minecraft:soul_lantern", base="trim")
    p.jigsaw_side(8, 3, D - 1, "south", J("bawah/desa_rumah_masuk"), EMPTY, EMPTY, final_state="minecraft:mud_bricks")
    p.anchor(8.5, 4.0, 6.5, "bawah/arwah", peran="tersesat", protect=0)
    return p.finish(out_path("bawah", "desa/rumah_4"))


def desa_toko_kertas():
    """The paper-offering shop: an open front with a counter, paper houses and a paper horse made for burning (white
    and yellow wool, birch), white mourning banners, hanging soul lanterns; its ghost still keeps the shop."""
    W, D = 13, 15
    p = DPiece(W, 16, D, seed=63321, palette=DIYU)
    p.fill(0, 0, 0, W - 1, 2, D - 1, "minecraft:soul_soil")
    p.air(0, 4, 0, W - 1, 15, D - 1)
    p.fill(0, 3, 0, W - 1, 3, D - 1, "minecraft:coarse_dirt")
    x0, z0, x1, z1 = 1, 1, 11, 9
    p.fill(x0, 3, z0, x1, 3, z1, "minecraft:birch_planks")
    p.walls(x0, 4, z0, x1, 7, z1, role="minecraft:pale_oak_planks", pillar="minecraft:stripped_birch_log", windows=0,
            door=None)
    # the open shop front: the south wall stays open above a counter, a gap in the middle to walk in
    p.air(x0 + 1, 4, z1, x1 - 1, 6, z1)
    for x in range(x0 + 1, x1):
        if x != 6:
            p.put(x, 4, z1, "minecraft:birch_slab", {"type": "top", "waterlogged": "false"})
    p.roof_gable(x0, 8, z0, x1, z1, role="minecraft:blackstone_stairs", slab_role="minecraft:blackstone_slab",
                 full_role="minecraft:blackstone", along="x", overhang=1)
    # paper houses on the back shelf (wool and birch roofs), stacks of spirit money (yellow carpet on white)
    for (x, col) in ((2, "minecraft:white_wool"), (3, "minecraft:yellow_wool"), (9, "minecraft:white_wool"),
                     (10, "minecraft:yellow_wool")):
        p.put(x, 4, z0 + 1, col)
        p.stairs(x, 5, z0 + 1, "minecraft:birch_stairs", "south")
    for x in (5, 6, 7):
        p.put(x, 4, z0 + 1, "minecraft:white_wool")
        p.put(x, 5, z0 + 1, "minecraft:yellow_carpet")
    # the paper horse in the middle of the room (legs of birch fence, a wool body, a stair for the head)
    hx, hz = 6, 5
    p.put(hx, 4, hz - 1, "minecraft:birch_fence")
    p.put(hx, 4, hz + 1, "minecraft:birch_fence")
    p.put(hx, 5, hz - 1, "minecraft:white_wool")
    p.put(hx, 5, hz, "minecraft:white_wool")
    p.put(hx, 5, hz + 1, "minecraft:white_wool")
    p.put(hx, 6, hz - 1, "minecraft:white_wool")
    p.stairs(hx, 6, hz - 2, "minecraft:birch_stairs", "south", half="top")
    p.put(hx, 6, hz + 1, "minecraft:white_carpet")
    # banners and lanterns
    for x in (x0, x1):
        p.banner(x, 6, z1 + 1, "white", facing="south", patterns=(("stripe_center", "black"), ("border", "black")))
    for x in (3, 9):
        p.lantern(x, 8, 5, chain=1, role="minecraft:soul_lantern")
    p.put(2, 4, 7, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(10, 4, 7, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": "kultivator:chests/dunia/dimensi/desa_arwah"})
    for z in range(z1 + 1, D):
        for x in (5, 6, 7):
            p.put(x, 3, z, "minecraft:mud_bricks")
    p.lamp_post(3, 3, 12, h=2, post="minecraft:pale_oak_fence", glow="minecraft:soul_lantern", base=None)
    p.jigsaw_side(6, 3, D - 1, "south", J("bawah/desa_rumah_masuk"), EMPTY, EMPTY, final_state="minecraft:mud_bricks")
    p.anchor(4.5, 4.0, 4.5, "bawah/arwah", peran="pesan", protect=0)
    return p.finish(out_path("bawah", "desa/rumah_5"))


# ------------------------------------------------------------------------------------- Kuil Gerbang Hantu (overworld)
def kuil():
    """The overworld entry: a weathered shrine with the incense altar where the soul leaves the body."""
    p = DPiece(17, 24, 19, seed=63401, palette=KUIL)
    p.foundation(0, 0, 16, 18, depth=3, role="found")
    p.floor(0, 3, 0, 16, 18, "floor", alt="floor_alt", alt_chance=0.25)
    p.air(0, 4, 0, 16, 23, 18)
    p.walls(2, 4, 2, 14, 9, 13, role="wall", pillar="pillar", windows=4, window_role="minecraft:spruce_trapdoor",
            door=("south", 3))
    p.fill(2, 10, 2, 14, 10, 13, "minecraft:dark_oak_planks")
    p.roof_gable(2, 11, 2, 14, 13, along="x", overhang=2)
    # the incense altar: stone table, censer, candles, spirit tablets (stone steles)
    p.fill(5, 4, 3, 11, 4, 5, "minecraft:polished_blackstone_bricks")
    p.fill(6, 5, 3, 10, 5, 4, "minecraft:polished_blackstone_bricks")
    p.put(8, 5, 5, "minecraft:decorated_pot")
    p.put(8, 6, 3, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                "waterlogged": "false"})
    for x in (6, 7, 9, 10):
        p.put(x, 6, 3, "minecraft:polished_blackstone_wall")
        p.candle(x, 6, 4, "minecraft:white_candle", n=p.rng.randint(1, 4))
    for x in (5, 11):
        p.candle(x, 5, 5, "minecraft:red_candle", n=3)
    for x in range(4, 13, 4):
        p.lantern(x, 10, 9, chain=1)
    # yard: crooked trees, cobwebs, a bell
    p.round_tree(2, 4, 16, h=5, leaves="minecraft:dark_oak_leaves", log="minecraft:dark_oak_log", radius=1)
    p.round_tree(14, 4, 16, h=4, leaves="minecraft:dark_oak_leaves", log="minecraft:dark_oak_log", radius=1)
    for i in range(6):
        p.put(p.rng.randint(3, 13), 9, p.rng.randint(3, 12), "minecraft:cobweb")
    p.weather(0.15)
    p.jigsaw_side(8, 3, 18, "south", J("bawah/kuil_depan"), J("bawah/kuil_gerbang_masuk"), J("bawah/kuil_gerbang"),
                  final_state=KUIL["path"])
    p.jigsaw_side(16, 3, 9, "east", J("bawah/kuil_sisi"), J("bawah/kuil_makam_masuk"), J("bawah/kuil_makam"),
                  final_state=KUIL["floor"])
    p.anchor(8.5, 4.0, 7.5, "bawah/altar", protect=12)
    p.anchor(8.5, 4.0, 15.5, "bawah/situs", situs="bawah/kuil", tier=3, protect=12)
    return p.finish(out_path("bawah", "kuil/kuil"))


def kuil_gerbang():
    """A mossy path with soul lanterns to the three-bay Gerbang Hantu (the ghost gate)."""
    p = DPiece(13, 16, 20, seed=63402, palette=KUIL)
    p.foundation(0, 0, 12, 19, depth=3, role="found")
    p.air(0, 4, 0, 12, 15, 19)
    for z in range(20):
        for x in range(13):
            p.put(x, 3, z, "path" if 4 <= x <= 8 else ("minecraft:podzol" if p.rng.random() > 0.3 else
                                                         "minecraft:coarse_dirt"))
            if (x < 3 or x > 9) and p.rng.random() < 0.3:
                p.flower(x, 4, z, ("minecraft:poppy", "minecraft:red_tulip", "minecraft:fern", "minecraft:short_grass"))
    for z in range(2, 18, 5):
        p.lamp_post(3, 3, z, h=2, base="trim")
        p.lamp_post(9, 3, z, h=2, base="trim")
    p.paifang(3, 4, 17, 9, h=7)
    for x in (1, 11):
        p.lamp_post(x, 3, 17, h=3, base="trim")
    p.weather(0.2)
    p.jigsaw_side(6, 3, 0, "north", J("bawah/kuil_gerbang_masuk"), EMPTY, EMPTY, final_state=KUIL["path"])
    return p.finish(out_path("bawah", "kuil/gerbang"))


def kuil_makam():
    """A small graveyard beside the shrine: mounds of coarse dirt with stone tablets and offerings."""
    p = DPiece(13, 10, 13, seed=63403, palette=KUIL)
    p.foundation(0, 0, 12, 12, depth=3, role="found")
    p.air(0, 4, 0, 12, 9, 12)
    for x in range(13):
        for z in range(13):
            p.put(x, 3, z, "minecraft:podzol" if p.rng.random() > 0.35 else "minecraft:coarse_dirt")
    for (x, z) in ((3, 3), (8, 3), (3, 8), (8, 8)):
        p.put(x, 4, z, "minecraft:coarse_dirt")
        p.put(x + 1, 4, z, "minecraft:rooted_dirt")
        p.put(x, 4, z + 1, "minecraft:mossy_stone_bricks")
        p.put(x, 5, z + 1, "minecraft:mossy_stone_brick_wall")
        p.candle(x + 1, 5, z, "minecraft:white_candle", n=2)
    p.put(0, 4, 6, "minecraft:mossy_cobblestone_wall")
    p.jigsaw_side(0, 3, 6, "west", J("bawah/kuil_makam_masuk"), EMPTY, EMPTY, final_state="minecraft:podzol")
    return p.finish(out_path("bawah", "kuil/makam"))


def _kuil_altar(p, cx, y, z_back, palette_stone="minecraft:polished_blackstone_bricks"):
    """The incense altar every shrine shares (the soul's way in): a stone table, the censer of soul fire, candles and
    the spirit tablets; returns the anchor spot."""
    p.fill(cx - 3, y, z_back, cx + 3, y, z_back + 2, palette_stone)
    p.fill(cx - 2, y + 1, z_back, cx + 2, y + 1, z_back + 1, palette_stone)
    p.put(cx, y + 1, z_back + 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "true", "waterlogged": "false"})
    p.put(cx, y + 2, z_back, "minecraft:soul_campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                                          "waterlogged": "false"})
    for dx in (-2, -1, 1, 2):
        p.put(cx + dx, y + 2, z_back, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "bottom", "open": "true",
                                                                      "powered": "false", "waterlogged": "false"})
        p.candle(cx + dx, y + 2, z_back + 1, "minecraft:white_candle", n=p.rng.randint(1, 4))
    for dx in (-3, 3):
        p.candle(cx + dx, y + 1, z_back + 2, "minecraft:red_candle", n=3)


def kuil_pagoda():
    """Kuil Gerbang Hantu, pagoda form (dark forests and pale gardens): a three-tier pagoda of dark oak and red pillars
    on a mossy platform, the incense altar inside, guardian lions at the steps, banners of the dead, stone lanterns."""
    p = DPiece(17, 30, 19, seed=63404, palette=KUIL)
    p.foundation(0, 0, 16, 18, depth=3, role="found")
    p.floor(0, 3, 0, 16, 18, "floor", alt="floor_alt", alt_chance=0.25)
    p.air(0, 4, 0, 16, 29, 18)
    # raised platform with steps to the south
    p.fill(3, 4, 2, 13, 4, 12, "minecraft:mossy_stone_bricks")
    for x in range(6, 11):
        p.stairs(x, 4, 13, "minecraft:mossy_stone_brick_stairs", "north")
    x0, z0, x1, z1 = 4, 3, 12, 11
    p.walls(x0, 5, z0, x1, 9, z1, role="wall", pillar="pillar", windows=4, window_role="minecraft:spruce_trapdoor",
            door=("south", 3))
    p.fill(x0, 10, z0, x1, 10, z1, "minecraft:dark_oak_planks")
    top = p.roof_hip(x0, 11, z0, x1, z1, role="roof", slab_role="roof_slab", full_role="roof_full", overhang=2, tiers=3,
                     tier_gap=2, tier_band=2, band_role="minecraft:red_nether_bricks")
    if top < 29:
        p.ridge_pearl(8, top, 7, "minecraft:gold_block", flame=top + 1 < 30)
    _kuil_altar(p, 8, 5, 4)
    for x in (5, 11):
        p.lantern(x, 10, 8, chain=2)
    # guardian lions, stone lanterns, banners of the dead on poles, a pale tree and cobwebs
    p.guardian(5, 4, 15, "south", stone="minecraft:mossy_stone_bricks", accent="minecraft:gold_block")
    p.guardian(11, 4, 15, "south", stone="minecraft:mossy_stone_bricks", accent="minecraft:gold_block")
    for (x, z) in ((1, 3), (15, 3), (1, 12), (15, 12)):
        p.lamp_post(x, 3, z, h=2, base="trim")
    for (x, rot) in ((2, 0), (14, 0)):
        p.put(x, 4, 16, "minecraft:dark_oak_fence")
        p.banner(x, 5, 16, "white", rotation=rot, patterns=(("stripe_center", "black"), ("border", "black"),
                                                           ("triangle_top", "black")))
    for i in range(6):
        p.put(p.rng.randint(5, 11), 9, p.rng.randint(4, 10), "minecraft:cobweb")
    p.weather(0.15)
    p.jigsaw_side(8, 3, 18, "south", J("bawah/kuil_depan"), J("bawah/kuil_gerbang_masuk"), J("bawah/kuil_gerbang"),
                  final_state=KUIL["path"])
    p.jigsaw_side(16, 3, 9, "east", J("bawah/kuil_sisi"), J("bawah/kuil_makam_masuk"), J("bawah/kuil_makam"),
                  final_state=KUIL["floor"])
    p.anchor(8.5, 5.0, 7.5, "bawah/altar", protect=12)
    p.anchor(8.5, 4.0, 16.5, "bawah/situs", situs="bawah/kuil", tier=3, protect=12)
    return p.finish(out_path("bawah", "kuil/kuil_pagoda"))


def kuil_rawa():
    """Kuil Gerbang Hantu, swamp form (swamps and mangroves): a shrine of mangrove wood raised on a mud-brick plinth
    among mangrove roots, its veranda hung with soul lanterns and white banners, lily pads in the moat."""
    p = DPiece(17, 26, 19, seed=63405, palette=KUIL)
    p.foundation(0, 0, 16, 18, depth=3, role="minecraft:mud_bricks")
    p.air(0, 4, 0, 16, 25, 18)
    for x in range(17):
        for z in range(19):
            if p.rng.random() < 0.3:
                p.put(x, 3, z, "minecraft:muddy_mangrove_roots", {"axis": "y"})
            else:
                p.put(x, 3, z, "minecraft:mud")
    # a moat of still water around the plinth, with lily pads
    for x in range(1, 16):
        for z in range(1, 15):
            if x in (1, 15) or z in (1, 14):
                p.water(x, 3, z, x, 3, z)
                if p.rng.random() < 0.4:
                    p.put(x, 4, z, "minecraft:lily_pad")
    p.fill(2, 3, 2, 14, 4, 13, "minecraft:mud_bricks")
    for x in range(7, 10):
        p.put(x, 3, 14, "minecraft:mangrove_planks")
        p.put(x, 3, 15, "minecraft:mangrove_planks")
        p.stairs(x, 4, 14, "minecraft:mud_brick_stairs", "north")
    x0, z0, x1, z1 = 3, 3, 13, 11
    p.fill(x0, 4, z0, x1, 4, z1, "minecraft:mangrove_planks")
    p.walls(x0 + 1, 5, z0, x1 - 1, 9, z1 - 1, role="minecraft:mangrove_planks", pillar="minecraft:mangrove_log",
            windows=3, window_role="minecraft:mangrove_trapdoor", door=("south", 3))
    # veranda posts in front
    for x in (x0, x1):
        for y in range(5, 10):
            p.put(x, y, z1, "minecraft:stripped_mangrove_log", {"axis": "y"})
    p.fill(x0, 10, z0, x1, 10, z1, "minecraft:mangrove_planks")
    ridge = p.roof_gable(x0, 11, z0, x1, z1, role="roof", slab_role="roof_slab", full_role="roof_full", along="x",
                         overhang=1)
    p.ridge_pearl(8, ridge + 1, 7, "minecraft:gold_block")
    _kuil_altar(p, 8, 5, z0 + 1, palette_stone="minecraft:mud_bricks")
    for x in (x0 + 1, 8, x1 - 1):
        p.lantern(x, 10, z1, chain=1)
    for x in (x0, x1):
        p.banner(x, 8, z1 + 1, "white", facing="south", patterns=(("stripe_center", "black"), ("border", "black")))
    # mangrove roots and propagules about the yard corners
    for (x, z) in ((0, 0), (16, 0), (0, 17), (16, 17), (0, 9), (16, 9)):
        for y in range(4, 6):
            p.put(x, y, z, "minecraft:mangrove_roots")
        p.put(x, 6, z, "minecraft:mangrove_log", {"axis": "y"})
        p.leaves(x, 7, z, "minecraft:mangrove_leaves")
    for i in range(5):
        p.put(p.rng.randint(4, 12), 9, p.rng.randint(4, 9), "minecraft:cobweb")
    p.weather(0.12)
    p.jigsaw_side(8, 3, 18, "south", J("bawah/kuil_depan"), J("bawah/kuil_gerbang_masuk"), J("bawah/kuil_gerbang"),
                  final_state="minecraft:mud_bricks")
    p.jigsaw_side(16, 3, 9, "east", J("bawah/kuil_sisi"), J("bawah/kuil_makam_masuk"), J("bawah/kuil_makam"),
                  final_state="minecraft:mud")
    p.anchor(8.5, 5.0, 7.5, "bawah/altar", protect=12)
    p.anchor(8.5, 4.0, 16.5, "bawah/situs", situs="bawah/kuil", tier=3, protect=12)
    return p.finish(out_path("bawah", "kuil/kuil_rawa"))


def kuil_makam_keluarga():
    """A family tomb beside the shrine (the omega-shaped Chinese grave): a curved back wall around the mound, the
    tombstone with its little roof, an offering table, a censer and two stone lanterns."""
    p = DPiece(15, 12, 15, seed=63406, palette=KUIL)
    p.foundation(0, 0, 14, 14, depth=3, role="found")
    p.air(0, 4, 0, 14, 11, 14)
    for x in range(15):
        for z in range(15):
            p.put(x, 3, z, "minecraft:podzol" if p.rng.random() > 0.35 else "minecraft:coarse_dirt")
    cx, cz = 7, 6
    # the mound and the curved wall behind it (the open side faces east, toward the shrine's side jigsaw at x 0)
    for x in range(15):
        for z in range(15):
            d = math.hypot(x - cx, z - cz)
            if d <= 3.2:
                p.put(x, 4, z, "minecraft:rooted_dirt")
                if d <= 2.0:
                    p.put(x, 5, z, "minecraft:moss_block")
            elif 4.6 <= d <= 5.5 and x >= cx - 1:
                p.put(x, 4, z, "minecraft:mossy_stone_bricks")
                p.put(x, 5, z, "minecraft:mossy_stone_brick_wall")
    # the tombstone on the west face of the mound, under a little tiled roof
    for y in (4, 5, 6):
        p.put(cx - 4, y, cz, "minecraft:polished_andesite" if y < 6 else "minecraft:chiseled_stone_bricks")
    p.stairs(cx - 4, 7, cz, "minecraft:deepslate_tile_stairs", "east")
    p.stairs(cx - 4, 7, cz - 1, "minecraft:deepslate_tile_stairs", "south")
    p.stairs(cx - 4, 7, cz + 1, "minecraft:deepslate_tile_stairs", "north")
    p.put(cx - 5, 4, cz, "minecraft:stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    p.candle(cx - 5, 5, cz - 1, "minecraft:white_candle", n=2)
    p.candle(cx - 5, 5, cz + 1, "minecraft:white_candle", n=2)
    p.put(cx - 5, 5, cz, "minecraft:flower_pot")
    p.censer(cx - 7, 4, cz, plinth=None)
    for z in (cz - 3, cz + 3):
        p.lamp_post(cx - 6, 3, z, h=2, post="minecraft:mossy_stone_brick_wall", glow="minecraft:soul_lantern", base="trim")
    p.weather(0.2)
    p.put(0, 4, 5, "minecraft:mossy_cobblestone_wall")
    p.jigsaw_side(0, 3, 7, "west", J("bawah/kuil_makam_masuk"), EMPTY, EMPTY, final_state="minecraft:podzol")
    return p.finish(out_path("bawah", "kuil/makam_2"))


# ================================================================================================== Laut Tanpa Batas
# Laut dimension: sand floor top y 39, water y 40..99. Island templates start at local y 0 = world 40 (start_height 41),
# so local 59 is the sea surface and local 60 the first air layer.
SEA = 59
ISLAND_KINDS = ("herba", "pertapa", "pasar", "karang")
ISLAND_WEIGHT = {"herba": 3, "pertapa": 2, "pasar": 2, "karang": 3}


def _island_body(p, cx, cz, r_top, r_bottom, top_h, rock="rock", sand="sand", grass="grass", dirt="dirt", shell=3,
                 noise=0.0, beach=2, lagoon=0.0, stretch=1.0):
    """A hollow island cone from the sea floor (local 0) to the land surface: the outer `shell` blocks only (the
    inside is never seen; leaving it void keeps the template small). Above the sea: grass on dirt with a sand beach.
    `lagoon` > 0 makes an atoll: no land within that radius and a shallow sand floor 4 below the surface; `stretch`
    elongates the island along x (an oval)."""
    rng = p.rng
    sx, sy, sz = p.size
    heights = {}
    for x in range(sx):
        for z in range(sz):
            d = math.hypot((x - cx) / stretch, z - cz) + (rng.random() - 0.5) * noise
            # land height above the sea surface: a dome (an atoll: a ring around the lagoon)
            if lagoon > 0 and d < lagoon:
                h = None
            elif d <= r_top:
                if lagoon > 0:
                    t = abs(d - (lagoon + r_top) / 2.0) / max(1.0, (r_top - lagoon) / 2.0)
                else:
                    t = d / max(1.0, r_top)
                h = SEA + 1 + int(round(top_h * (1.0 - min(1.0, t) ** 2)))
            else:
                h = None
            heights[(x, z)] = h
    for x in range(sx):
        for z in range(sz):
            d = math.hypot((x - cx) / stretch, z - cz)
            if lagoon > 0 and d < lagoon - 1:
                p.put(x, SEA - 4, z, sand)
                if rng.random() < 0.3:
                    p.put(x, SEA - 5, z, rock)
            # underwater radius grows downward
            for y in range(0, SEA + 1):
                t = y / float(SEA)
                r = r_bottom + (r_top - r_bottom) * t
                if d <= r:
                    inner = d <= r - shell
                    if not inner or y == 0:
                        p.put(x, y, z, rock if (y < SEA - 6 or rng.random() < 0.4) else sand)
            h = heights[(x, z)]
            if h is not None:
                for y in range(SEA + 1, h + 1):
                    top = y == h
                    beach_ring = h <= SEA + beach
                    if top:
                        p.put(x, y, z, sand if beach_ring else grass)
                    elif y >= h - 2:
                        p.put(x, y, z, sand if beach_ring else dirt)
                    elif d > r_top - shell - 1:
                        p.put(x, y, z, rock)
    return heights


def _surface(heights, x, z):
    return heights.get((x, z))


def _shrine_pulang(p, x, y, z):
    """Gerbang Pulang: a small sea-shrine with a paifang and a pearl lamp (the return gate anchor)."""
    p.fill(x - 2, y, z - 2, x + 2, y, z + 2, "minecraft:prismarine_bricks")
    p.paifang(x - 2, y + 1, z, x + 2, h=5, role_pillar="minecraft:quartz_pillar", role_beam="minecraft:stripped_warped_stem",
              role_roof="minecraft:dark_prismarine_stairs", lamps=False)
    p.put(x, y + 1, z, "minecraft:sea_lantern")
    p.anchor(x + 0.5, y + 2.0, z + 0.5, "laut/gerbang_pulang", protect=6)


def pulau_core(kind, v=0):
    """An island core 48 x 96 x 48 (herba, pertapa, pasar, karang; two forms each, v 0 / 1) with satellite connectors
    at the waterline. Every kind is its own structure (laut/pulau_<kind>), so locate and the fisherman tell them apart."""
    if v == 1:
        return pulau_core_2(kind)
    seeds = {"herba": 64001, "pertapa": 64002, "pasar": 64003, "karang": 64004}
    p = DPiece(48, 96, 48, seed=seeds[kind], palette=LAUT)
    cx = cz = 23
    if kind == "karang":
        heights = _island_body(p, cx, cz, 15, 21, 5, rock="minecraft:tuff", sand="minecraft:sand", grass="minecraft:sand",
                               dirt="minecraft:sandstone", noise=4.0, beach=6)
    else:
        heights = _island_body(p, cx, cz, 20, 23, 9 if kind != "pertapa" else 16, noise=2.5)
    top = lambda x, z: _surface(heights, x, z) or SEA  # noqa: E731
    rng = p.rng
    if kind == "herba":
        # terraced herb beds: moss and rooted dirt with ferns, flowers, berries; bamboo screens; a drying rack hut
        for (bx, bz) in ((16, 16), (26, 16), (16, 26), (26, 26)):
            y = top(bx, bz)
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    yy = top(bx + dx, bz + dz)
                    p.put(bx + dx, yy, bz + dz, "minecraft:moss_block" if (dx + dz) % 2 else "minecraft:rooted_dirt")
                    plant = rng.choice(["minecraft:fern", "minecraft:sweet_berry_bush", "minecraft:lily_of_the_valley",
                                        "minecraft:allium", "minecraft:azure_bluet", "minecraft:large_fern"])
                    if plant == "minecraft:sweet_berry_bush":
                        p.put(bx + dx, yy + 1, bz + dz, plant, {"age": "3"})
                    elif plant == "minecraft:large_fern":
                        p.put(bx + dx, yy + 1, bz + dz, plant, {"half": "lower"})
                        p.put(bx + dx, yy + 2, bz + dz, plant, {"half": "upper"})
                    else:
                        p.put(bx + dx, yy + 1, bz + dz, plant)
            p.anchor(bx + 0.5, y + 1.0, bz + 0.5, "laut/herba", protect=0)
        for (x, z) in ((12, 22), (34, 24)):
            p.round_tree(x, top(x, z) + 1, z, h=5, leaves="minecraft:flowering_azalea_leaves", log="minecraft:oak_log")
        for z in range(14, 30):
            if z % 3:
                p.put(31, top(31, z) + 1, z, "minecraft:bamboo", {"age": "1", "leaves": "large", "stage": "0"})
    elif kind == "pertapa":
        # a cliff-top hermitage: stone platform, a thatched hut, bamboo grove, a meditation stone
        y = top(cx, cz)
        p.fill(cx - 4, y, cz - 4, cx + 4, y, cz + 4, "minecraft:mossy_stone_bricks")
        p.air(cx - 4, y + 1, cz - 4, cx + 4, y + 10, cz + 4)
        p.walls(cx - 3, y + 1, cz - 3, cx + 1, y + 4, cz + 1, role="minecraft:bamboo_planks",
                pillar="minecraft:stripped_bamboo_block", windows=3, window_role="minecraft:bamboo_fence",
                door=("south", 1))
        p.roof_gable(cx - 3, y + 5, cz - 3, cx + 1, cz + 1, role="minecraft:bamboo_mosaic_stairs",
                     slab_role="minecraft:bamboo_mosaic_slab", full_role="minecraft:bamboo_mosaic", along="x", overhang=1)
        p.put(cx + 3, y + 1, cz + 3, "minecraft:mossy_cobblestone")
        p.put(cx + 3, y + 2, cz + 3, "minecraft:moss_carpet")
        p.lantern(cx - 1, y + 5, cz - 1, chain=1, role="minecraft:lantern")
        # detail pass: a censer and a bell by the meditation stone, a moss cushion, prayer pennants on a pole
        p.censer(cx + 3, y + 1, cz - 2, plinth=None)
        p.put(cx + 1, y + 1, cz + 3, "minecraft:bell", {"attachment": "floor", "facing": "east", "powered": "false"})
        p.put(cx - 4, y + 1, cz + 3, "minecraft:bamboo_fence")
        p.put(cx - 4, y + 2, cz + 3, "minecraft:bamboo_fence")
        p.banner(cx - 4, y + 3, cz + 3, "lime", rotation=4, patterns=(("stripe_middle", "white"), ("border", "green")))
        for i in range(40):
            x, z = rng.randint(10, 36), rng.randint(10, 36)
            if abs(x - cx) > 5 or abs(z - cz) > 5:
                yy = top(x, z)
                if yy > SEA + 2:
                    for k in range(rng.randint(3, 7)):
                        p.put(x, yy + 1 + k, z, "minecraft:bamboo", {"age": "1", "leaves": "small" if k < 2 else "large",
                                                                     "stage": "0"})
        p.anchor(cx + 3.5, y + 2.0, cz + 3.5, "laut/pertapa", protect=10)
    elif kind == "pasar":
        # island market: a plaza, four stalls with wool awnings, a tea counter, lantern strings (K's kota anchors)
        y = top(cx, cz)
        p.fill(cx - 9, y, cz - 9, cx + 9, y, cz + 9, "minecraft:smooth_sandstone")
        p.air(cx - 9, y + 1, cz - 9, cx + 9, y + 12, cz + 9)
        for x in range(cx - 9, cx + 10):
            for z in range(cz - 9, cz + 10):
                for yy in range(top(x, z) + 1, y):
                    p.put(x, yy, z, "minecraft:sandstone")
        colours = ["minecraft:red_wool", "minecraft:cyan_wool", "minecraft:yellow_wool", "minecraft:white_wool"]
        for i, (sx_, sz_) in enumerate(((cx - 6, cz - 6), (cx + 3, cz - 6), (cx - 6, cz + 3), (cx + 3, cz + 3))):
            for (dx, dz) in ((0, 0), (3, 0), (0, 3), (3, 3)):
                for k in range(1, 4):
                    p.put(sx_ + dx, y + k, sz_ + dz, "minecraft:spruce_fence")
            p.fill(sx_, y + 4, sz_, sx_ + 3, y + 4, sz_ + 3, colours[i])
            p.fill(sx_, y + 1, sz_ + 1, sx_ + 3, y + 1, sz_ + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        for x in range(cx - 8, cx + 9, 4):
            p.lamp_post(x, y, cz - 9, h=3, post="minecraft:spruce_fence", glow="minecraft:lantern", base=None)
            p.lamp_post(x, y, cz + 9, h=3, post="minecraft:spruce_fence", glow="minecraft:lantern", base=None)
        for i, (sx_, sz_) in enumerate(((cx - 6, cz - 6), (cx + 3, cz - 6), (cx - 6, cz + 3), (cx + 3, cz + 3))):
            p.banner(sx_, y + 3, sz_ - 1, ("red", "cyan", "yellow", "white")[i], facing="north",
                     patterns=(("rhombus", "white" if i != 3 else "blue"), ("border", "black")))
        p.censer(cx, y + 1, cz + 7, plinth=None)
        p.anchor(cx - 4.5, y + 1.0, cz - 2.5, "kota/teh", protect=8)
        p.anchor(cx + 4.5, y + 1.0, cz - 2.5, "laut/pedagang", protect=8)
        p.anchor(cx + 0.5, y + 1.0, cz + 0.5, "kota/lentera", protect=0)
    elif kind == "karang":
        # a reef island: jagged tuff spires, a lagoon ring, coral gardens below the waterline, crab dens
        for i in range(14):
            x, z = rng.randint(12, 34), rng.randint(12, 34)
            yy = top(x, z)
            for k in range(rng.randint(3, 9)):
                p.put(x, yy + 1 + k, z, rng.choice(["minecraft:tuff", "minecraft:andesite", "minecraft:calcite"]))
        corals = ["tube", "brain", "bubble", "fire", "horn"]
        for i in range(220):
            ang = rng.random() * math.tau
            yy = rng.randint(SEA - 24, SEA - 3)
            r = 21 + (15 - 21) * (yy / float(SEA))
            x, z = int(round(cx + (r + 1.0) * math.cos(ang))), int(round(cz + (r + 1.0) * math.sin(ang)))
            if p.inside(x, yy, z) and p.get(x, yy, z) is None:
                p.put(x, yy, z, "minecraft:%s_coral_block" % rng.choice(corals))
                if p.inside(x, yy + 1, z) and p.get(x, yy + 1, z) is None and rng.random() < 0.5:
                    p.put(x, yy + 1, z, "minecraft:%s_coral" % rng.choice(corals), {"waterlogged": "true"})
        for (x, z) in ((cx - 6, cz), (cx + 6, cz)):
            p.anchor(x + 0.5, top(x, z) + 1.0, z + 0.5, "laut/karang", monster="kepiting", protect=0)
        p.anchor(cx + 0.5, top(cx, cz + 6) + 1.0, cz + 6.5, "laut/karang", monster="yaksha", protect=0)
    # every island: the return shrine on the south shore and a site anchor
    gx, gz = cx, cz + 12 if kind != "karang" else cz + 8
    gy = top(gx, gz)
    p.air(gx - 3, gy + 1, gz - 3, gx + 3, gy + 10, gz + 3)
    _shrine_pulang(p, gx, gy, gz)
    p.anchor(cx + 0.5, top(cx, cz) + 1.0, cz - 0.5, "laut/situs", situs="laut/pulau", tier=3, jenis=kind, protect=0)
    # satellites at the waterline (local SEA): west and east
    p.jigsaw_side(0, SEA, cz, "west", J("laut/pulau_sisi"), J("laut/satelit_masuk"), J("laut/satelit"),
                  final_state="minecraft:water")
    p.jigsaw_side(47, SEA, cz, "east", J("laut/pulau_sisi"), J("laut/satelit_masuk"), J("laut/satelit"),
                  final_state="minecraft:water")
    return p.finish(out_path("laut", "pulau/%s" % kind))


def _boat(p, x, y, z, along="x", length=7):
    """A moored fishing sampan on the water (y = the sea's top water layer): a spruce hull of stairs and slabs, a
    curved bamboo-mat cabin, a bow lantern."""
    for i in range(length):
        u = i - length // 2
        bx, bz = (x + u, z) if along == "x" else (x, z + u)
        sx_, sz_ = (0, 1) if along == "x" else (1, 0)
        end = i in (0, length - 1)
        if end:
            face = ("west" if i == 0 else "east") if along == "x" else ("north" if i == 0 else "south")
            p.stairs(bx, y + 1, bz, "minecraft:spruce_stairs", _opp(face))
            continue
        p.put(bx, y, bz, "minecraft:spruce_planks")
        p.stairs(bx - sx_, y + 1, bz - sz_, "minecraft:spruce_stairs", "north" if along == "x" else "west", half="top")
        p.stairs(bx + sx_, y + 1, bz + sz_, "minecraft:spruce_stairs", "south" if along == "x" else "east", half="top")
        p.slab(bx, y + 1, bz, "minecraft:spruce_slab")
        if abs(u) <= 1:
            p.put(bx - sx_, y + 2, bz - sz_, "minecraft:bamboo_fence")
            p.put(bx + sx_, y + 2, bz + sz_, "minecraft:bamboo_fence")
            p.slab(bx, y + 3, bz, "minecraft:bamboo_mosaic_slab")
            p.slab(bx - sx_, y + 3, bz - sz_, "minecraft:bamboo_mosaic_slab")
            p.slab(bx + sx_, y + 3, bz + sz_, "minecraft:bamboo_mosaic_slab")
    bx, bz = (x + length // 2 - 1, z) if along == "x" else (x, z + length // 2 - 1)
    p.put(bx, y + 2, bz, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def _pavilion(p, cx, y, cz, half=2, roof="minecraft:cherry_stairs", slab="minecraft:cherry_slab",
              full="minecraft:cherry_planks", post="minecraft:stripped_cherry_log", floor="minecraft:smooth_quartz",
              pearl="minecraft:pearlescent_froglight"):
    """An open garden pavilion (ting): a stone floor, four posts, a curled hip roof with a pearl on its crown, a
    stone table with four stools inside, a hanging lantern."""
    p.fill(cx - half, y, cz - half, cx + half, y, cz + half, floor)
    p.air(cx - half, y + 1, cz - half, cx + half, y + 4, cz + half)
    for (dx, dz) in ((-half, -half), (half, -half), (-half, half), (half, half)):
        for k in range(1, 4):
            p.put(cx + dx, y + k, cz + dz, post, {"axis": "y"})
    p.fill(cx - half, y + 4, cz - half, cx + half, y + 4, cz + half, full)
    top = p.roof_hip(cx - half, y + 5, cz - half, cx + half, cz + half, role=roof, slab_role=slab, full_role=full,
                     overhang=1)
    p.ridge_pearl(cx, top, cz, pearl)
    p.put(cx, y + 1, cz, "minecraft:andesite_wall")
    p.slab(cx, y + 2, cz, "minecraft:smooth_stone_slab")
    for (dx, dz, face) in ((-1, 0, "east"), (1, 0, "west"), (0, -1, "south"), (0, 1, "north")):
        p.stairs(cx + dx, y + 1, cz + dz, "minecraft:polished_andesite_stairs", face)
    p.lantern(cx, y + 4, cz + 1, chain=1, role="minecraft:lantern")


def pulau_core_2(kind):
    """The second form of every island kind (the detail pass, DESIGN 34.5.14):
    herba   Taman Herba Berdinding: a walled herb garden with a moon gate, raised beds, a koi pond and a pavilion;
    pertapa Puncak Pertapa: a tall crag, a stair up the south face, a cave-cell, an old pine and a bell at the summit;
    pasar   Pasar Dermaga: a harbour market, stalls on a boardwalk, moored sampans, the old fisherman's pier;
    karang  Atol Karang: a ring of reef around a lagoon, a ruined watchtower, coral gardens in the lagoon."""
    seeds = {"herba": 64011, "pertapa": 64012, "pasar": 64013, "karang": 64014}
    p = DPiece(48, 96, 48, seed=seeds[kind], palette=LAUT)
    cx = cz = 23
    rng = p.rng
    if kind == "karang":
        heights = _island_body(p, cx, cz, 20, 22, 4, rock="minecraft:tuff", sand="minecraft:sand", grass="minecraft:sand",
                               dirt="minecraft:sandstone", noise=3.0, beach=6, lagoon=11.0)
    elif kind == "pertapa":
        heights = _island_body(p, cx, cz, 17, 21, 24, noise=2.0)
    elif kind == "pasar":
        heights = _island_body(p, cx, cz, 18, 22, 6, noise=1.5, stretch=1.15)
    else:
        heights = _island_body(p, cx, cz, 20, 23, 7, noise=2.0, stretch=1.1)
    top = lambda x, z: _surface(heights, x, z) or SEA  # noqa: E731
    if kind == "herba":
        y = top(cx, cz)
        # level the garden and wall it (a ring of mossy stone with a tiled coping), a moon gate to the south
        for x in range(cx - 9, cx + 10):
            for z in range(cz - 9, cz + 10):
                for yy in range(top(x, z) + 1, y + 1):
                    p.put(x, yy, z, "minecraft:dirt")
                p.put(x, y, z, "minecraft:grass_block")
                p.air(x, y + 1, z, x, y + 9, z)
        for x in range(cx - 9, cx + 10):
            for z in range(cz - 9, cz + 10):
                if x in (cx - 9, cx + 9) or z in (cz - 9, cz + 9):
                    for k in range(1, 4):
                        p.put(x, y + k, z, "minecraft:mossy_stone_bricks" if k < 3 else "minecraft:stone_bricks")
                    p.slab(x, y + 4, z, "minecraft:deepslate_tile_slab")
        for dx in range(-2, 3):
            for k in range(1, 5):
                if math.hypot(dx, (k - 2.2) * 1.15) <= 2.3:
                    p.put(cx + dx, y + k, cz + 9, "minecraft:air")
        # four raised beds edged in stone, the herb anchors
        for (bx, bz) in ((cx - 5, cz - 5), (cx + 5, cz - 5), (cx - 5, cz + 4), (cx + 5, cz + 4)):
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    edge = max(abs(dx), abs(dz)) == 2
                    p.put(bx + dx, y + 1, bz + dz, "minecraft:stone_bricks" if edge else "minecraft:rooted_dirt")
                    if not edge:
                        plant = rng.choice(["minecraft:fern", "minecraft:lily_of_the_valley", "minecraft:allium",
                                            "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:blue_orchid"])
                        p.put(bx + dx, y + 2, bz + dz, plant)
            p.anchor(bx + 0.5, y + 2.0, bz + 0.5, "laut/herba", protect=0)
        # the koi pond between them, stepping stones, the pavilion at the back
        for dx in range(-3, 4):
            for dz in range(-1, 2):
                p.put(cx + dx, y - 1, cz + dz, "minecraft:clay")
                p.water(cx + dx, y, cz + dz, cx + dx, y, cz + dz)
                if rng.random() < 0.3:
                    p.put(cx + dx, y + 1, cz + dz, "minecraft:lily_pad")
                elif rng.random() < 0.2:
                    p.put(cx + dx, y - 1, cz + dz, "minecraft:sea_pickle", {"pickles": "2", "waterlogged": "true"})
        for (dx, dz) in ((0, 2), (0, 4), (0, 6)):
            p.put(cx + dx, y, cz + dz, "minecraft:smooth_stone")
        _pavilion(p, cx, y, cz - 6, half=2)
        for (x, z) in ((cx - 7, cz - 7), (cx + 7, cz - 7), (cx - 7, cz + 7), (cx + 7, cz + 7)):
            p.round_tree(x, y + 1, z, h=4, radius=1)
        for (x, z) in ((cx - 3, cz + 8), (cx + 3, cz + 8)):
            p.lamp_post(x, y, z, h=2, post="minecraft:stone_brick_wall", glow="minecraft:lantern", base=None)
        gx, gz = cx, cz + 13
    elif kind == "pertapa":
        y = top(cx, cz)
        # the summit platform, the cell cut into the rock, the old pine, the bell, the censer
        p.fill(cx - 3, y, cz - 3, cx + 3, y, cz + 3, "minecraft:mossy_cobblestone")
        p.air(cx - 3, y + 1, cz - 3, cx + 3, y + 10, cz + 3)
        p.walls(cx - 3, y + 1, cz - 3, cx, y + 3, cz, role="minecraft:mossy_stone_bricks", pillar="minecraft:stone_bricks",
                windows=0, door=("south", 1))
        p.fill(cx - 3, y + 4, cz - 3, cx, y + 4, cz, "minecraft:moss_block")
        p.put(cx - 2, y + 1, cz - 2, "minecraft:moss_carpet")
        p.lantern(cx - 1, y + 4, cz - 1, chain=1, role="minecraft:lantern")
        p.round_tree(cx + 2, y + 1, cz - 2, h=6, leaves="minecraft:spruce_leaves", log="minecraft:spruce_log", radius=2)
        p.put(cx + 2, y + 1, cz + 2, "minecraft:bell", {"attachment": "floor", "facing": "north", "powered": "false"})
        p.censer(cx, y + 1, cz + 2, plinth=None)
        # the stair up the south face: a stone flight from the shore to the summit
        z = cz + 4
        yy = y
        while z < 44 and yy > SEA + 1:
            for dx in (-1, 0, 1):
                p.air(cx + dx, yy, z, cx + dx, yy + 3, z)
                p.stairs(cx + dx, yy, z, "minecraft:mossy_stone_brick_stairs", "north")
                for k in range(top(cx + dx, z) + 1, yy):
                    p.put(cx + dx, k, z, "minecraft:stone")
            yy -= 1
            z += 1
        for i in range(30):
            x, z = rng.randint(10, 36), rng.randint(10, 36)
            if abs(x - cx) > 4 and top(x, z) > SEA + 3 and rng.random() < 0.5:
                p.put(x, top(x, z) + 1, z, rng.choice(["minecraft:fern", "minecraft:short_grass", "minecraft:lily_of_the_valley"]))
        p.anchor(cx - 1.5, y + 1.0, cz - 1.5, "laut/pertapa", protect=10)
        gx, gz = cx - 10, cz + 8
    elif kind == "pasar":
        y = top(cx, cz)
        p.fill(cx - 10, y, cz - 7, cx + 10, y, cz + 7, "minecraft:smooth_sandstone")
        p.air(cx - 10, y + 1, cz - 7, cx + 10, y + 10, cz + 7)
        for x in range(cx - 10, cx + 11):
            for z in range(cz - 7, cz + 8):
                for yy in range(top(x, z) + 1, y):
                    p.put(x, yy, z, "minecraft:sandstone")
        # six stalls in two rows, awnings of alternating colours, banners, barrels and crates
        cols = ["minecraft:red_wool", "minecraft:cyan_wool", "minecraft:yellow_wool", "minecraft:lime_wool",
                "minecraft:orange_wool", "minecraft:light_blue_wool"]
        dyes = ["red", "cyan", "yellow", "lime", "orange", "light_blue"]
        k = 0
        for row_z in (cz - 6, cz + 4):
            for sx_ in (cx - 9, cx - 2, cx + 5):
                for (dx, dz) in ((0, 0), (3, 0), (0, 2), (3, 2)):
                    for kk in range(1, 4):
                        p.put(sx_ + dx, y + kk, row_z + dz, "minecraft:spruce_fence")
                for dx in range(0, 4):
                    for dz in range(0, 3):
                        p.put(sx_ + dx, y + 4, row_z + dz, cols[k] if (dx + dz) % 2 == 0 else "minecraft:white_wool")
                p.put(sx_ + 1, y + 1, row_z + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
                p.slab(sx_ + 2, y + 1, row_z + 1, "minecraft:spruce_slab", kind="top")
                p.put(sx_ + 2, y + 2, row_z + 1, "minecraft:flower_pot")
                p.banner(sx_, y + 3, row_z - 1 if row_z < cz else row_z + 3, dyes[k],
                         facing="north" if row_z < cz else "south", patterns=(("rhombus", "white"), ("border", "black")))
                k += 1
        for x in range(cx - 10, cx + 11, 5):
            p.lamp_post(x, y, cz - 7, h=3, post="minecraft:spruce_fence", glow="minecraft:lantern", base=None)
        # the fisherman's pier to the east with drying racks, and two sampans moored on the water
        for x in range(cx + 11, 46):
            for dz in (-1, 0, 1):
                p.put(x, SEA + 1, cz + dz, "minecraft:spruce_planks")
                p.air(x, SEA + 2, cz + dz, x, SEA + 5, cz + dz)
            if x % 3 == 0:
                for dz in (-2, 2):
                    for yy in range(SEA - 6, SEA + 3):
                        p.put(x, yy, cz + dz, "minecraft:spruce_log", {"axis": "y"})
        for x in (cx + 13, cx + 15):
            p.put(x, SEA + 2, cz - 1, "minecraft:spruce_fence")
            p.put(x, SEA + 3, cz - 1, "minecraft:spruce_fence")
        p.put(cx + 14, SEA + 3, cz - 1, "minecraft:dried_kelp_block")
        p.put(cx + 17, SEA + 2, cz + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        _boat(p, cx + 16, SEA, cz + 4, along="x")
        _boat(p, cx - 15, SEA, cz + 11, along="z", length=6)
        p.anchor(cx - 5.5, y + 1.0, cz - 1.5, "kota/teh", protect=8)
        p.anchor(cx + 5.5, y + 1.0, cz - 1.5, "laut/pedagang", protect=8)
        p.anchor(cx + 0.5, y + 1.0, cz + 0.5, "kota/lentera", protect=0)
        p.anchor(cx + 18.5, SEA + 2.0, cz + 0.5, "laut/nelayan", protect=6)
        gx, gz = cx - 3, cz + 12
    else:
        # the atoll: tuff and calcite crags on the ring, a ruined watchtower, coral gardens in the lagoon
        for i in range(10):
            ang = rng.random() * math.tau
            r = 15 + rng.random() * 3
            x, z = int(round(cx + r * math.cos(ang))), int(round(cz + r * math.sin(ang)))
            yy = top(x, z)
            for k in range(rng.randint(2, 6)):
                p.put(x, yy + 1 + k, z, rng.choice(["minecraft:tuff", "minecraft:calcite", "minecraft:andesite"]))
        tx, tz = cx + 15, cz - 4
        ty = top(tx, tz)
        for k in range(1, 12):
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if max(abs(dx), abs(dz)) == 2 and (k < 7 or rng.random() < 0.55):
                        p.put(tx + dx, ty + k, tz + dz, rng.choice(["minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks",
                                                                    "minecraft:stone_bricks"]))
                    elif max(abs(dx), abs(dz)) < 2:
                        p.put(tx + dx, ty + k, tz + dz, "minecraft:air")
        p.put(tx, ty + 1, tz, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"},
              {"id": "minecraft:chest", "LootTable": "kultivator:chests/dunia/dimensi/pulau"})
        p.air(tx - 2, ty + 1, tz, tx - 2, ty + 2, tz)
        corals = ["tube", "brain", "bubble", "fire", "horn"]
        for i in range(160):
            x, z = rng.randint(cx - 10, cx + 10), rng.randint(cz - 10, cz + 10)
            if math.hypot(x - cx, z - cz) < 9:
                col = rng.choice(corals)
                r = rng.random()
                if r < 0.35:
                    p.put(x, SEA - 3, z, "minecraft:%s_coral_block" % col)
                elif r < 0.75:
                    p.put(x, SEA - 3, z, "minecraft:%s_coral" % col, {"waterlogged": "true"})
                else:
                    p.put(x, SEA - 3, z, "minecraft:sea_pickle", {"pickles": str(rng.randint(1, 4)), "waterlogged": "true"})
        for ang in (0.6, 2.4, 4.4):
            x, z = int(round(cx + 15.5 * math.cos(ang))), int(round(cz + 15.5 * math.sin(ang)))
            p.anchor(x + 0.5, top(x, z) + 1.0, z + 0.5, "laut/karang", monster="kepiting" if ang < 4 else "yaksha",
                     protect=0)
        gx, gz = cx - 2, cz + 15
    gy = top(gx, gz)
    p.air(gx - 3, gy + 1, gz - 3, gx + 3, gy + 10, gz + 3)
    _shrine_pulang(p, gx, gy, gz)
    sx0, sz0 = (cx, cz) if kind != "karang" else (cx + 15, cz + 3)
    p.anchor(sx0 + 0.5, top(sx0, sz0) + 1.0, sz0 + 0.5, "laut/situs", situs="laut/pulau", tier=3, jenis=kind, protect=0)
    p.jigsaw_side(0, SEA, cz, "west", J("laut/pulau_sisi"), J("laut/satelit_masuk"), J("laut/satelit"),
                  final_state="minecraft:water")
    p.jigsaw_side(47, SEA, cz, "east", J("laut/pulau_sisi"), J("laut/satelit_masuk"), J("laut/satelit"),
                  final_state="minecraft:water")
    return p.finish(out_path("laut", "pulau/%s_2" % kind))


def satelit(kind):
    """Islets, docks and reef rings attached to an island core at the waterline."""
    if kind == "dermaga":
        p = DPiece(9, 66, 22, seed=64101, palette=LAUT)
        for z in range(22):
            for x in range(2, 7):
                p.put(x, SEA + 1, z, "minecraft:spruce_planks")
            if z % 4 == 1:
                for x in (2, 6):
                    for y in range(0, SEA + 3):
                        p.put(x, y, z, "minecraft:spruce_log", {"axis": "y"})
                    p.put(x, SEA + 3, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
        for x in range(2, 7):
            p.air(x, SEA + 2, 0, x, SEA + 5, 21)
        p.jigsaw_side(4, SEA, 21, "south", J("laut/satelit_masuk"), EMPTY, EMPTY, final_state="minecraft:water")
        return p.finish(out_path("laut", "pulau/satelit_dermaga"))
    if kind == "nelayan":
        # the old fisherman's dock: a plank jetty on log piles, his reed hut, drying racks of kelp, a sampan
        p = DPiece(15, 70, 24, seed=64104, palette=LAUT)
        for z in range(24):
            for x in range(5, 10):
                p.put(x, SEA + 1, z, "minecraft:spruce_planks" if x not in (5, 9) else "minecraft:stripped_spruce_log",
                      {"axis": "z"} if x in (5, 9) else None)
            p.air(5, SEA + 2, z, 9, SEA + 6, z)
            if z % 4 == 2:
                for x in (5, 9):
                    for y in range(0, SEA + 1):
                        p.put(x, y, z, "minecraft:spruce_log", {"axis": "y"})
        # the hut at the far end (z 1..6): bamboo walls, a thatch roof, a lantern and his gear
        p.walls(5, SEA + 2, 1, 9, SEA + 4, 5, role="minecraft:bamboo_planks", pillar="minecraft:stripped_bamboo_block",
                windows=2, window_role="minecraft:bamboo_fence", door=("south", 1))
        p.roof_gable(5, SEA + 5, 1, 9, 5, role="minecraft:bamboo_mosaic_stairs", slab_role="minecraft:bamboo_mosaic_slab",
                     full_role="minecraft:bamboo_mosaic", along="z", overhang=1)
        p.put(6, SEA + 2, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(8, SEA + 2, 2, "minecraft:composter", {"level": "0"})
        p.lantern(7, SEA + 5, 3, chain=1, role="minecraft:lantern")
        for z in (9, 12):
            for x in (4, 10):
                p.put(x, SEA + 1, z, "minecraft:spruce_planks")
                p.put(x, SEA + 2, z, "minecraft:spruce_fence")
                p.put(x, SEA + 3, z, "minecraft:spruce_fence")
            p.put(4, SEA + 4, z, "minecraft:dried_kelp_block")
            p.put(10, SEA + 4, z, "minecraft:dried_kelp_block")
        _boat(p, 12, SEA, 17, along="z", length=6)
        p.put(7, SEA + 2, 21, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
        p.jigsaw_side(7, SEA, 23, "south", J("laut/satelit_masuk"), EMPTY, EMPTY, final_state="minecraft:water")
        p.anchor(7.5, SEA + 2.0, 8.5, "laut/nelayan", protect=6)
        return p.finish(out_path("laut", "pulau/satelit_nelayan"))
    if kind == "kapal_karam":
        # a sunken junk on the reef shelf: a broken hull listing on the sand, a snapped mast, a salvage chest
        p = DPiece(15, 66, 26, seed=64105, palette=LAUT)
        base = SEA - 18
        for z in range(3, 22):
            w = 4 if 7 <= z <= 17 else (3 if 5 <= z <= 19 else 2)
            for dx in range(-w, w + 1):
                tilt = (dx + w) // 3
                y = base + tilt
                if abs(dx) == w:
                    for k in range(0, 4):
                        if p.rng.random() < 0.78:
                            p.put(7 + dx, y + k, z, "minecraft:spruce_planks" if k % 2 else "minecraft:stripped_spruce_log",
                                  {"axis": "z"} if k % 2 == 0 else None)
                else:
                    p.put(7 + dx, y, z, "minecraft:spruce_planks" if p.rng.random() < 0.85 else "minecraft:sand")
            for y in range(base - 4, base):
                p.put(7, y, z, "minecraft:sand")
        for k in range(1, 9):
            p.put(7 + (k // 4), base + 1 + k, 12, "minecraft:spruce_log", {"axis": "y"})
        p.put(5, base + 1, 9, "minecraft:chest", {"facing": "east", "type": "single", "waterlogged": "true"},
              {"id": "minecraft:chest", "LootTable": "kultivator:chests/dunia/dimensi/kapal_karam"})
        for (x, z) in ((8, 6), (6, 15), (9, 18)):
            p.put(x, base + 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        for i in range(30):
            x, z = p.rng.randint(0, 14), p.rng.randint(0, 25)
            if p.get(x, base, z) is None and p.get(x, base + 1, z) is None:
                p.put(x, base, z, "minecraft:sand")
                pick = p.rng.random()
                if pick < 0.5:
                    p.put(x, base + 1, z, "minecraft:seagrass")
                elif pick < 0.8:
                    p.put(x, base + 1, z, "minecraft:sea_pickle", {"pickles": "2", "waterlogged": "true"})
                else:
                    p.put(x, base + 1, z, "minecraft:tube_coral", {"waterlogged": "true"})
        p.jigsaw_side(7, SEA, 25, "south", J("laut/satelit_masuk"), EMPTY, EMPTY, final_state="minecraft:water")
        return p.finish(out_path("laut", "pulau/satelit_kapal_karam"))
    if kind == "pulau_kecil":
        p = DPiece(24, 72, 24, seed=64102, palette=LAUT)
        heights = _island_body(p, 11, 11, 8, 11, 4, noise=2.0)
        y = heights.get((11, 11)) or SEA
        p.round_tree(11, y + 1, 11, h=4, leaves="minecraft:jungle_leaves", log="minecraft:jungle_log", radius=2)
        p.jigsaw_side(11, SEA, 23, "south", J("laut/satelit_masuk"), EMPTY, EMPTY, final_state="minecraft:water")
        return p.finish(out_path("laut", "pulau/satelit_pulau_kecil"))
    p = DPiece(21, 68, 21, seed=64103, palette=LAUT)
    for i in range(70):
        ang = p.rng.random() * math.tau
        d = 4 + p.rng.random() * 5
        x, z = int(10 + d * math.cos(ang)), int(10 + d * math.sin(ang))
        h = p.rng.randint(SEA - 10, SEA + 3)
        for y in range(0, h + 1):
            if y < 4 or y > h - 5:
                p.put(x, y, z, p.rng.choice(["minecraft:tuff", "minecraft:stone", "minecraft:andesite"]))
    p.jigsaw_side(10, SEA, 20, "south", J("laut/satelit_masuk"), EMPTY, EMPTY, final_state="minecraft:water")
    return p.finish(out_path("laut", "pulau/satelit_karang"))


def istana_naga():
    """Istana Naga on the sea floor (Journey to the West ch. 3: the Crystal Palace of the Eastern Sea): an air-filled
    prismarine hall behind door airlocks (doors hold the water back), quartz pillars with gold bands, pearl lamps,
    glass windows onto coral gardens, the Dragon King's throne and treasury."""
    p = DPiece(48, 44, 48, seed=64201, palette=LAUT)
    c = 23
    # sea-floor terrace (local 0 = world 40, the first water layer)
    for x in range(48):
        for z in range(48):
            if math.hypot(x - c, z - c) <= 23.5:
                p.put(x, 0, z, "minecraft:dark_prismarine" if (x + z) % 5 else "minecraft:sea_lantern")
    # the hall shell x 8..38, z 6..38, y 1..20 (sealed), interior air
    x0, z0, x1, z1, y0, y1 = 8, 6, 38, 38, 1, 20
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                edge = x in (x0, x1) or z in (z0, z1) or y in (y0, y1)
                if edge:
                    wall = "minecraft:prismarine_bricks" if y % 6 else "minecraft:dark_prismarine"
                    # windows: glass panes behind 2-high slots along the walls
                    if y in (6, 7, 13, 14) and (x in (x0, x1) or z in (z0, z1)) and ((x + z) % 4 == 1) and \
                            not (x in (x0, x1) and z in (z0, z1)):
                        wall = "minecraft:light_blue_stained_glass"
                    if y == y0:
                        wall = "minecraft:prismarine_bricks"
                    if y == y1 and (x - c) % 6 == 0 and (z - c) % 6 == 0:
                        wall = "minecraft:sea_lantern"
                    p.put(x, y, z, wall)
                else:
                    p.put(x, y, z, "minecraft:air")
    # inner floor pattern and the aisle
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            p.put(x, y0, z, "minecraft:quartz_bricks" if abs(x - c) <= 2 else
                  ("minecraft:prismarine_bricks" if (x + z) % 2 else "minecraft:dark_prismarine"))
    # quartz pillars with gold bands and pearl lamps
    for z in range(z0 + 4, z1 - 2, 6):
        for x in (c - 8, c + 8):
            for y in range(y0 + 1, y1):
                p.put(x, y, z, "gold" if y in (5, 12) else "minecraft:quartz_pillar", {"axis": "y"} if y not in (5, 12) else None)
            p.put(x, y1 - 1, z, "minecraft:pearlescent_froglight", {"axis": "y"})
    # throne dais at the north end
    p.fill(c - 6, y0 + 1, z0 + 1, c + 6, y0 + 1, z0 + 6, "minecraft:quartz_block")
    p.fill(c - 4, y0 + 2, z0 + 1, c + 4, y0 + 2, z0 + 3, "minecraft:quartz_block")
    for x in range(c - 6, c + 7):
        p.stairs(x, y0 + 1, z0 + 7, "minecraft:quartz_stairs", "north")
    p.put(c, y0 + 3, z0 + 1, "minecraft:quartz_stairs", {"facing": "north", "half": "bottom", "shape": "straight",
                                                         "waterlogged": "false"})
    for y in range(y0 + 4, y0 + 9):
        p.put(c, y, z0 + 1, "gold" if y % 2 else "minecraft:prismarine_bricks")
    for x in (c - 2, c + 2):
        p.put(x, y0 + 3, z0 + 1, "gold")
        p.put(x, y0 + 4, z0 + 1, "minecraft:sea_lantern")
    # coral garden displays inside glass tanks along the aisle (waterlogged corals in water behind glass)
    for z in (z0 + 12, z0 + 20, z0 + 28):
        for x in (c - 13, c + 11):
            for dx in range(3):
                for dz in range(3):
                    for dy in range(1, 4):
                        edge = dx in (0, 2) or dz in (0, 2) or dy == 3
                        if edge:
                            p.put(x + dx, y0 + dy, z + dz, "minecraft:glass" if dy < 3 else "minecraft:prismarine_bricks")
                        else:
                            p.water(x + dx, y0 + dy, z + dz, x + dx, y0 + dy, z + dz)
                p.put(x + 1, y0 + 1, z + 1, "minecraft:%s_coral" % p.rng.choice(["tube", "brain", "fire"]),
                      {"waterlogged": "true"})
    # treasury chest behind the throne and pearl lamps
    p.put(c + 5, y0 + 2, z0 + 2, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": "kultivator:chests/dunia/dimensi/istana_naga"})
    for x in range(x0 + 4, x1 - 2, 6):
        p.lantern(x, y1, c + 6, chain=2, role="minecraft:sea_lantern")
    # the airlock at the south: outer door, a 3-deep vestibule, inner door
    for (zz, face) in ((z1, "south"), (z1 - 4, "south")):
        for dx in (0, 1):
            p.air(c + dx, y0 + 1, zz, c + dx, y0 + 2, zz)
        p.door(c, y0 + 1, zz, "minecraft:warped_door", face, hinge="left")
        p.door(c + 1, y0 + 1, zz, "minecraft:warped_door", face, hinge="right")
    for z in range(z1 - 4, z1 + 1):
        for x in (c - 1, c + 2):
            for y in range(y0 + 1, y0 + 4):
                p.put(x, y, z, "minecraft:prismarine_bricks")
        for x in (c, c + 1):
            p.put(x, y0 + 3, z, "minecraft:prismarine_bricks")
    # roof: a broad hip of dark prismarine stairs (under water: waterlogged by the liquid settings)
    p.roof_hip(x0, y1 + 1, z0, x1, z1, role="minecraft:dark_prismarine_stairs", slab_role="minecraft:dark_prismarine_slab",
               full_role="minecraft:dark_prismarine", overhang=1, tiers=1)
    # coral gardens outside
    for i in range(140):
        x, z = p.rng.randint(0, 47), p.rng.randint(0, 47)
        if not (x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1) and math.hypot(x - c, z - c) <= 23:
            col = p.rng.choice(["tube", "brain", "bubble", "fire", "horn"])
            r = p.rng.random()
            if r < 0.3:
                p.put(x, 1, z, "minecraft:%s_coral_block" % col)
            elif r < 0.75:
                p.put(x, 1, z, "minecraft:%s_coral" % col, {"waterlogged": "true"})
            else:
                p.put(x, 1, z, "minecraft:sea_pickle", {"pickles": str(p.rng.randint(1, 4)), "waterlogged": "true"})
    p.jigsaw_side(c, 0, 47, "south", J("laut/istana_depan"), J("laut/istana_gerbang_masuk"), J("laut/istana_gerbang"),
                  final_state="minecraft:dark_prismarine")
    p.anchor(c + 0.5, y0 + 3.0, z0 + 3.5, "laut/raja_naga", protect=40)
    p.anchor(c + 3.5, y0 + 1.0, z1 - 6.5, "laut/penjaga", protect=8)
    p.anchor(c + 0.5, y0 + 1.0, c + 0.5, "laut/situs", situs="laut/istana_naga", tier=5, protect=40)
    return p.finish(out_path("laut", "istana/istana_naga"))


def istana_gerbang():
    """The palace's sea gate: a coral avenue between two great quartz pillars topped with pearl lamps."""
    p = DPiece(21, 26, 21, seed=64202, palette=LAUT)
    for x in range(21):
        for z in range(21):
            p.put(x, 0, z, "minecraft:smooth_sandstone" if 7 <= x <= 13 else "minecraft:sand")
    for x in (6, 14):
        for y in range(1, 18):
            p.put(x, y, 10, "gold" if y % 6 == 0 else "minecraft:quartz_pillar", {"axis": "y"} if y % 6 else None)
        p.put(x, 18, 10, "minecraft:pearlescent_froglight", {"axis": "y"})
    for x in range(5, 16):
        p.put(x, 16, 10, "minecraft:dark_prismarine")
        p.put(x, 17, 10, "minecraft:dark_prismarine_slab", {"type": "bottom", "waterlogged": "true"})
    for i in range(60):
        x, z = p.rng.randint(0, 20), p.rng.randint(0, 20)
        if not (6 <= x <= 14):
            col = p.rng.choice(["tube", "brain", "bubble", "fire", "horn"])
            p.put(x, 1, z, "minecraft:%s_coral_fan" % col if p.rng.random() < 0.5 else "minecraft:%s_coral" % col,
                  {"waterlogged": "true"})
    for z in range(0, 21, 2):
        p.put(7, 1, z, "minecraft:sea_pickle", {"pickles": "3", "waterlogged": "true"})
        p.put(13, 1, z, "minecraft:sea_pickle", {"pickles": "3", "waterlogged": "true"})
    p.jigsaw_side(10, 0, 0, "north", J("laut/istana_gerbang_masuk"), EMPTY, EMPTY, final_state="minecraft:smooth_sandstone")
    return p.finish(out_path("laut", "istana/gerbang"))


def penglai():
    """Pulau Penglai (Liezi, Tang Wen): a tall mountain island with cliff terraces, peach trees (cherry), a spring
    pool at the peak's foot, a pavilion of white jade and gold on the summit."""
    p = DPiece(48, 112, 48, seed=64301, palette=LAUT)
    c = 23
    heights = {}
    for x in range(48):
        for z in range(48):
            d = math.hypot(x - c, z - c)
            if d <= 21:
                t = d / 21.0
                h = SEA + 1 + int(round(31 * (1 - t) ** 1.6 + 3 * math.sin(x * 0.7) * math.cos(z * 0.6) * (1 - t)))
                heights[(x, z)] = max(SEA + 1, h)
    for x in range(48):
        for z in range(48):
            d = math.hypot(x - c, z - c)
            for y in range(0, SEA + 1):
                r = 23 - 2 * (y / SEA)
                if d <= r and (d > r - 3 or y == 0):
                    p.put(x, y, z, "minecraft:stone" if y < SEA - 5 else "minecraft:sand")
            h = heights.get((x, z))
            if h is None:
                continue
            # neighbour heights decide where the cliff shell must be
            nmin = min(heights.get((x + dx, z + dz), SEA) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            for y in range(SEA + 1, h + 1):
                if y == h:
                    p.put(x, y, z, "minecraft:grass_block" if h - nmin < 3 else "minecraft:calcite")
                elif y >= nmin - 1 or d > 18:
                    p.put(x, y, z, "minecraft:calcite" if (y // 3) % 2 else "minecraft:stone")
    top = lambda x, z: heights.get((x, z), SEA)  # noqa: E731
    # peach trees (cherry) on the slopes, the spring pool, the summit pavilion
    for i in range(16):
        ang = p.rng.random() * math.tau
        d = 7 + p.rng.random() * 11
        x, z = int(c + d * math.cos(ang)), int(c + d * math.sin(ang))
        if 3 <= x <= 44 and 3 <= z <= 44:
            p.round_tree(x, top(x, z) + 1, z, h=4 + p.rng.randint(0, 2), radius=2)
    sy = top(c, c)
    p.fill(c - 5, sy, c - 5, c + 5, sy, c + 5, "minecraft:smooth_quartz")
    p.air(c - 5, sy + 1, c - 5, c + 5, sy + 12, c + 5)
    for (dx, dz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        p.pillar(c + dx, sy + 1, sy + 5, c + dz, role="minecraft:quartz_pillar", base="gold", cap="gold")
    p.fill(c - 4, sy + 6, c - 4, c + 4, sy + 6, c + 4, "minecraft:smooth_quartz")
    p.roof_hip(c - 4, sy + 7, c - 4, c + 4, c + 4, role="minecraft:cherry_stairs", slab_role="minecraft:cherry_slab",
               full_role="minecraft:cherry_planks", overhang=2, tiers=2, tier_gap=2, band_role="minecraft:gold_block")
    p.put(c, sy + 1, c, "minecraft:amethyst_block")
    p.put(c, sy + 2, c, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    # the spring: a pool on the eastern terrace
    px, pz = c + 9, c
    py = top(px, pz)
    for x in range(px - 2, px + 3):
        for z in range(pz - 2, pz + 3):
            if max(abs(x - px), abs(z - pz)) == 2:
                p.put(x, py, z, "minecraft:calcite")
            else:
                p.put(x, py - 1, z, "minecraft:calcite")
                p.water(x, py, z, x, py, z)
                p.air(x, py + 1, z, x, py + 3, z)
    p.anchor(px + 0.5, py + 1.0, pz + 0.5, "laut/penglai", protect=20)
    p.anchor(c + 0.5, sy + 1.0, c + 2.5, "laut/situs", situs="laut/penglai", tier=6, protect=30)
    gx, gz = c, c + 19
    gy = top(gx, gz)
    if gy:
        p.air(gx - 3, gy + 1, gz - 3, gx + 3, gy + 8, gz + 3)
        _shrine_pulang(p, gx, gy, gz)
    return p.finish(out_path("laut", "penglai/penglai"))


def dermaga_tiba():
    """Dermaga Tiba, the arrival harbour at the origin of Laut Tanpa Batas (where the whirlpool and the ship set
    travellers down, DimensiLevels.LAUT_ARRIVAL): an octagonal deck of sandstone and prismarine on dark-prismarine
    stilts rising 60 blocks from the sea floor, a gold-and-pearl compass rose around the arrival jigsaw, four plank
    piers with lantern posts and mooring bollards, a sea-gate paifang to the north, the Gerbang Pulang shrine to the
    south, a three-tier lighthouse pagoda on the east pier, the sea merchant's awning stall on the west pier, lily pads
    and sea pickles among the stilts. Local 0 = world 40 (the first water layer); the deck is local 60 = world 100."""
    p = DPiece(45, 80, 45, seed=64501, palette=LAUT)
    c = 22
    deck = SEA + 1
    # --- the octagonal deck (radius 9), its rim, the stilts down to the sea floor
    for x in range(45):
        for z in range(45):
            dx, dz = abs(x - c), abs(z - c)
            if max(dx, dz) <= 9 and dx + dz <= 13:
                rim = max(dx, dz) == 9 or dx + dz == 13
                ring = max(dx, dz) in (4, 7)
                role = "minecraft:dark_prismarine" if rim else ("minecraft:prismarine_bricks" if ring else
                                                                ("minecraft:smooth_sandstone" if (x + z) % 2 else
                                                                 "minecraft:cut_sandstone"))
                p.put(x, deck, z, role)
                p.air(x, deck + 1, z, x, deck + 1, z)
    # the compass rose: gold cardinal rays and pearl lamps set in the deck
    for d in range(1, 4):
        for (rx, rz) in ((0, -d), (0, d), (-d, 0), (d, 0)):
            p.put(c + rx, deck, c + rz, "gold" if d < 3 else "minecraft:chiseled_quartz_block")
    for (rx, rz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        p.put(c + rx, deck, c + rz, "minecraft:pearlescent_froglight", {"axis": "y"})
    # balustrade along the rim with gaps for the piers and the shrine
    for x in range(45):
        for z in range(45):
            dx, dz = abs(x - c), abs(z - c)
            if (max(dx, dz) == 9 and dx + dz <= 13 or dx + dz == 13 and max(dx, dz) <= 9) and min(dx, dz) > 2:
                p.put(x, deck + 1, z, "minecraft:prismarine_wall")
    # stilts: an octagon of eight and an inner four, banded every 6 blocks, sea lanterns at their feet
    stilts = [(c + 8, c), (c - 8, c), (c, c + 8), (c, c - 8), (c + 6, c + 6), (c - 6, c + 6), (c + 6, c - 6),
              (c - 6, c - 6), (c + 3, c + 3), (c - 3, c + 3), (c + 3, c - 3), (c - 3, c - 3)]
    for (sx, sz) in stilts:
        for y in range(0, deck):
            p.put(sx, y, sz, "minecraft:prismarine_bricks" if y % 6 == 5 else "minecraft:dark_prismarine")
        p.put(sx, 0, sz, "minecraft:sea_lantern")
        for (ox, oz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if p.rng.random() < 0.6:
                p.put(sx + ox, 0, sz + oz, "minecraft:sea_pickle", {"pickles": str(p.rng.randint(2, 4)), "waterlogged": "true"})
            elif p.rng.random() < 0.5:
                p.put(sx + ox, 0, sz + oz, "minecraft:seagrass")
    # --- four plank piers (5 wide) from the deck rim to the piece edge, with posts, lanterns and bollards
    for (ux, uz) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        for i in range(10, 23):
            for w in range(-2, 3):
                x, z = c + ux * i + (w if ux == 0 else 0), c + uz * i + (w if uz == 0 else 0)
                if not p.inside(x, deck, z):
                    continue
                p.put(x, deck, z, "minecraft:spruce_planks" if abs(w) < 2 else "minecraft:stripped_spruce_log",
                      {"axis": "z" if ux == 0 else "x"} if abs(w) == 2 else None)
                p.air(x, deck + 1, z, x, deck + 3, z)
            if i % 4 == 1:
                for w in (-2, 2):
                    x, z = c + ux * i + (w if ux == 0 else 0), c + uz * i + (w if uz == 0 else 0)
                    if not p.inside(x, deck, z):
                        continue
                    for y in range(0, deck):
                        p.put(x, y, z, "minecraft:spruce_log", {"axis": "y"})
                    p.put(x, deck + 1, z, "minecraft:spruce_fence")
                    p.put(x, deck + 2, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
            elif i % 4 == 3:
                for w in (-2, 2):
                    x, z = c + ux * i + (w if ux == 0 else 0), c + uz * i + (w if uz == 0 else 0)
                    if p.inside(x, deck + 1, z):
                        p.put(x, deck + 1, z, "minecraft:stripped_spruce_wood", {"axis": "y"})
    # lily pads and a few drifting barrels in the water between the piers
    for i in range(70):
        x, z = p.rng.randint(0, 44), p.rng.randint(0, 44)
        dx, dz = abs(x - c), abs(z - c)
        if dx + dz > 14 and min(dx, dz) > 3 and p.get(x, deck, z) is None:
            p.put(x, deck, z, "minecraft:lily_pad")
    # --- north: the sea-gate paifang over the pier
    p.paifang(c - 3, deck + 1, c - 12, c + 3, h=7, role_pillar="minecraft:quartz_pillar",
              role_beam="minecraft:stripped_warped_stem", role_roof="minecraft:dark_prismarine_stairs", lamps=True)
    # --- south: the Gerbang Pulang shrine on the deck's south arc
    p.air(c - 3, deck + 1, c + 4, c + 3, deck + 9, c + 8)
    _shrine_pulang(p, c, deck, c + 6)
    # the paifang's pillars stand on stilts beside the pier
    for x in (c - 3, c + 3):
        for y in range(0, deck + 1):
            p.put(x, y, c - 12, "minecraft:dark_prismarine" if y < deck else "minecraft:prismarine_bricks")
    # --- east: the lighthouse pagoda at the end of the pier (7 x 7, three tiers, a sea-lantern crown)
    lx = c + 18
    p.fill(lx - 3, deck, c - 3, lx + 3, deck, c + 3, "minecraft:prismarine_bricks")
    for y in range(0, deck):
        for (ox, oz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
            p.put(lx + ox, y, c + oz, "minecraft:dark_prismarine")
    p.walls(lx - 2, deck + 1, c - 2, lx + 2, deck + 6, c + 2, role="minecraft:smooth_quartz", pillar="minecraft:quartz_pillar",
            windows=2, window_role="minecraft:light_blue_stained_glass_pane", door=("west", 1))
    p.fill(lx - 2, deck + 7, c - 2, lx + 2, deck + 7, c + 2, "minecraft:prismarine_bricks")
    top = p.roof_hip(lx - 2, deck + 8, c - 2, lx + 2, c + 2, role="minecraft:dark_prismarine_stairs",
                     slab_role="minecraft:dark_prismarine_slab", full_role="minecraft:dark_prismarine", overhang=1,
                     tiers=3, tier_gap=1, tier_band=2, band_role="minecraft:smooth_quartz")
    p.put(lx, top, c, "minecraft:sea_lantern")
    p.put(lx, top + 1, c, "minecraft:end_rod", {"facing": "up"})
    p.lantern(lx, deck + 7, c, chain=2, role="minecraft:sea_lantern")
    # --- west: the sea merchant's stall (cyan awning, barrels, a coral display)
    sx = c - 15
    for (ox, oz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for k in range(1, 4):
            p.put(sx + ox, deck + k, c + oz, "minecraft:spruce_fence")
    p.fill(sx - 2, deck + 4, c - 2, sx + 2, deck + 4, c + 2, "minecraft:cyan_wool")
    p.fill(sx - 1, deck + 5, c - 1, sx + 1, deck + 5, c + 1, "minecraft:white_wool")
    p.put(sx - 2, deck + 1, c - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(sx - 2, deck + 1, c + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(sx - 2, deck + 1, c, "minecraft:spruce_trapdoor", {"facing": "east", "half": "top", "open": "false",
                                                             "powered": "false", "waterlogged": "false"})
    p.put(sx - 2, deck + 2, c, "minecraft:flower_pot")
    p.put(sx + 2, deck + 1, c + 2, "minecraft:decorated_pot")
    # --- detail pass: the old fisherman's nook at the end of the south pier (a bamboo awning, a bench, his barrel
    # and kelp racks) and guardian lions of quartz at the sea gate
    fz = c + 20
    for (ox, oz) in ((-2, -1), (2, -1), (-2, 1), (2, 1)):
        for k in range(1, 4):
            p.put(c + ox, deck + k, fz + oz, "minecraft:bamboo_fence")
    for ox in range(-2, 3):
        for oz in (-1, 0, 1):
            p.slab(c + ox, deck + 4, fz + oz, "minecraft:bamboo_mosaic_slab")
    p.stairs(c - 1, deck + 1, fz + 1, "minecraft:spruce_stairs", "north")
    p.stairs(c, deck + 1, fz + 1, "minecraft:spruce_stairs", "north")
    p.put(c + 1, deck + 1, fz - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(c - 2, deck + 4, fz, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    p.guardian(c - 2, deck + 1, c - 14, "north", stone="minecraft:smooth_quartz", accent="minecraft:gold_block")
    p.guardian(c + 2, deck + 1, c - 14, "north", stone="minecraft:smooth_quartz", accent="minecraft:gold_block")
    # --- the arrival point (start jigsaw, named kultivator:laut/tiba, lands on x 0 z 0 y 100)
    p.jigsaw(c, deck, c, "up_north", J("laut/tiba"), EMPTY, EMPTY, final_state="minecraft:chiseled_quartz_block")
    p.anchor(c + 0.5, deck + 1.0, c + 0.5, "laut/tiba", protect=26)
    p.anchor(sx + 0.5, deck + 1.0, c + 0.5, "laut/pedagang", protect=6)
    p.anchor(c + 0.5, deck + 1.0, fz + 0.5, "laut/nelayan", protect=6)
    return p.finish(out_path("laut", "dermaga/dermaga_tiba"))


def pusaran():
    """Pusaran Laut on an overworld deep-ocean floor: a ring of dark prismarine pillars with sea lanterns around a
    sunken eye; the whirlpool itself is a client effect over the gate anchor (34.5.14)."""
    p = DPiece(17, 12, 17, seed=64401, palette=LAUT)
    c = 8
    for x in range(17):
        for z in range(17):
            d = math.hypot(x - c, z - c)
            if d <= 8.4:
                p.put(x, 0, z, "minecraft:dark_prismarine" if d > 3 else "minecraft:prismarine_bricks")
                p.put(x, 1, z, "minecraft:dark_prismarine") if 6.5 < d <= 8.4 else None
    for k in range(8):
        ang = math.tau * k / 8
        x, z = int(round(c + 6.5 * math.cos(ang))), int(round(c + 6.5 * math.sin(ang)))
        for y in range(1, 7 + (k % 2) * 2):
            p.put(x, y, z, "minecraft:prismarine_bricks" if y % 3 else "minecraft:dark_prismarine")
        p.put(x, 7 + (k % 2) * 2, z, "minecraft:sea_lantern")
    p.put(c, 0, c, "minecraft:sea_lantern")
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        p.put(c + dx, 1, c + dz, "minecraft:sea_pickle", {"pickles": "4", "waterlogged": "true"})
    p.anchor(c + 0.5, 2.0, c + 0.5, "laut/pusaran", protect=10)
    p.anchor(c + 0.5, 1.0, c + 3.5, "laut/situs", situs="laut/pusaran", tier=3, protect=0)
    return p.finish(out_path("laut", "pusaran/pusaran"))


# ================================================================================================== JSON
def el(name, procs=BAWAH_PROC, projection="rigid"):
    return single(J(name), procs, projection)


def write_pools():
    pools = {
        "bawah/istana_start": [(el("bawah/istana/teras", SAFE), 1)],
        "bawah/jalan": [(el("bawah/istana/jalan_huangquan", SAFE), 1)],
        "bawah/jembatan": [(el("bawah/istana/jembatan_naihe", SAFE), 1)],
        "bawah/mengpo": [(el("bawah/istana/paviliun_mengpo", SAFE), 1)],
        "bawah/lorong_a": [(el("bawah/istana/lorong_a", SAFE), 1)],
        "bawah/lorong_b": [(el("bawah/istana/lorong_b", SAFE), 1)],
        "bawah/lorong_c": [(el("bawah/istana/lorong_c", SAFE), 1)],
        "bawah/istana": [(el("bawah/istana/istana_yanluo", SAFE), 1)],
        "bawah/ujung_dizang": [(el("bawah/istana/kuil_dizang", SAFE), 1)],
        "bawah/ujung_yaksha": [(el("bawah/istana/pos_yaksha", SAFE), 1)],
        "bawah/desa_start": [(el("bawah/desa/pusat"), 1)],
        "bawah/desa_jalan": [(el("bawah/desa/jalan"), 2), (el("bawah/desa/jalan_2"), 1)],
        "bawah/desa_rumah": [(el("bawah/desa/rumah_1"), 3), (el("bawah/desa/rumah_2"), 3), (el("bawah/desa/rumah_3"), 2),
                             (el("bawah/desa/rumah_4"), 2), (el("bawah/desa/rumah_5"), 2)],
        "bawah/kuil_start": [(el("bawah/kuil/kuil", KUIL_PROC), 1), (el("bawah/kuil/kuil_pagoda", KUIL_PROC), 1)],
        "bawah/kuil_rawa_start": [(el("bawah/kuil/kuil_rawa", KUIL_PROC), 1)],
        "bawah/kuil_gerbang": [(el("bawah/kuil/gerbang", KUIL_PROC), 1)],
        "bawah/kuil_makam": [(el("bawah/kuil/makam", KUIL_PROC), 1), (el("bawah/kuil/makam_2", KUIL_PROC), 1)],
        "laut/pulau_start": [(el("laut/pulau/herba", LAUT_PROC), 3), (el("laut/pulau/pertapa", LAUT_PROC), 2),
                             (el("laut/pulau/pasar", LAUT_PROC), 2), (el("laut/pulau/karang", LAUT_PROC), 3)],
        "laut/pulau_herba_start": [(el("laut/pulau/herba", LAUT_PROC), 1), (el("laut/pulau/herba_2", LAUT_PROC), 1)],
        "laut/pulau_pertapa_start": [(el("laut/pulau/pertapa", LAUT_PROC), 1), (el("laut/pulau/pertapa_2", LAUT_PROC), 1)],
        "laut/pulau_pasar_start": [(el("laut/pulau/pasar", LAUT_PROC), 1), (el("laut/pulau/pasar_2", LAUT_PROC), 1)],
        "laut/pulau_karang_start": [(el("laut/pulau/karang", LAUT_PROC), 1), (el("laut/pulau/karang_2", LAUT_PROC), 1)],
        "laut/satelit": [(el("laut/pulau/satelit_dermaga", LAUT_PROC), 3), (el("laut/pulau/satelit_pulau_kecil", LAUT_PROC), 3),
                         (el("laut/pulau/satelit_karang", LAUT_PROC), 2), (el("laut/pulau/satelit_nelayan", LAUT_PROC), 2),
                         (el("laut/pulau/satelit_kapal_karam", LAUT_PROC), 1),
                         ({"element_type": "minecraft:empty_pool_element"}, 3)],
        "laut/istana_start": [(el("laut/istana/istana_naga", LAUT_PROC), 1)],
        "laut/istana_gerbang": [(el("laut/istana/gerbang", LAUT_PROC), 1)],
        "laut/penglai_start": [(el("laut/penglai/penglai", LAUT_PROC), 1)],
        "laut/pusaran_start": [(el("laut/pusaran/pusaran", LAUT_PROC), 1)],
        "laut/dermaga_start": [(el("laut/dermaga/dermaga_tiba", SAFE), 1)],
    }
    for n in range(1, 11):
        pools["bawah/pengadilan_%d" % n] = [(el("bawah/istana/pengadilan_%d" % n, SAFE), 1)]
    for name, elements in pools.items():
        folder, rest = name.split("/", 1)
        write_json(json_path("template_pool", folder, rest), pool(J(name), elements))


def write_processors():
    protect = {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}

    def rule(block, out, prob):
        return {"input_predicate": {"block": block, "predicate_type": "minecraft:random_block_match", "probability": prob},
                "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": out}
    write_json(json_path("processor_list", "bawah", "aman"), {"processors": [protect]})
    write_json(json_path("processor_list", "bawah", "lapuk"), {"processors": [
        {"processor_type": "minecraft:rule", "rules": [
            rule("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.12),
            rule("minecraft:nether_bricks", "minecraft:cracked_nether_bricks", 0.1),
            rule("minecraft:pale_oak_planks", "minecraft:cobweb", 0.03),
        ]}, protect]})
    write_json(json_path("processor_list", "bawah", "kuil_lapuk"), {"processors": [
        {"processor_type": "minecraft:rule", "rules": [
            rule("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.25),
            rule("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.3),
            rule("minecraft:dark_oak_planks", "minecraft:stripped_dark_oak_wood", 0.08),
        ]}, protect]})
    write_json(json_path("processor_list", "laut", "terumbu"), {"processors": [
        {"processor_type": "minecraft:rule", "rules": [
            rule("minecraft:prismarine_bricks", "minecraft:prismarine", 0.1),
            rule("minecraft:stone", "minecraft:andesite", 0.15),
        ]}, protect]})


def _no_monsters():
    return {"monster": {"bounding_box": "full", "spawns": []}}


def write_structures():
    def js(start_pool, biomes, size, maxd, height, heightmap=None, adapt="beard_box", jig=None, safe=False):
        s = {"type": "minecraft:jigsaw", "biomes": biomes, "max_distance_from_center": maxd, "size": size,
             "spawn_overrides": _no_monsters() if safe else {}, "start_height": {"absolute": height},
             "start_pool": J(start_pool), "step": "surface_structures", "terrain_adaptation": adapt,
             "use_expansion_hack": False}
        if heightmap:
            s["project_start_to_heightmap"] = heightmap
        if jig:
            s["start_jigsaw_name"] = J(jig)
        return s
    # Istana Yama: the start jigsaw (top terrace, local y 6) lands at y 51, so the court's floor (local y 3) is y 48:
    # the flattened ground of dunia_bawah's density (zero crossing at y 48 within 96 b of the origin).
    write_json(json_path("structure", "bawah", "istana_yama"),
               js("bawah/istana_start", "#kultivator:has_structure/bawah/istana_yama", 4, 128, 52, jig="bawah/tiba",
                  adapt="none", safe=True))
    write_json(json_path("structure", "bawah", "desa_arwah"),
               js("bawah/desa_start", "#kultivator:has_structure/bawah/desa_arwah", 3, 64, -3,
                  heightmap="WORLD_SURFACE_WG", adapt="beard_thin"))
    write_json(json_path("structure", "bawah", "kuil_gerbang_hantu"),
               js("bawah/kuil_start", "#kultivator:has_structure/bawah/kuil_gerbang_hantu", 2, 48, -3,
                  heightmap="WORLD_SURFACE_WG", adapt="beard_thin"))
    # the swamp form: the same set picks it where the start lies in a swamp or mangrove (biome-fitting wood)
    write_json(json_path("structure", "bawah", "kuil_rawa"),
               js("bawah/kuil_rawa_start", "#kultivator:has_structure/bawah/kuil_rawa", 2, 48, -3,
                  heightmap="WORLD_SURFACE_WG", adapt="beard_thin"))
    write_json(json_path("structure", "laut", "pulau_roh"),
               js("laut/pulau_start", "#kultivator:has_structure/laut/pulau_roh", 2, 80, 41, adapt="none"))
    # the detail pass: every island kind is its own structure (two forms each), so locate tells them apart
    for kind in ISLAND_KINDS:
        write_json(json_path("structure", "laut", "pulau_" + kind),
                   js("laut/pulau_%s_start" % kind, "#kultivator:has_structure/laut/pulau_roh", 2, 80, 41, adapt="none"))
    write_json(json_path("structure", "laut", "istana_naga"),
               js("laut/istana_start", "#kultivator:has_structure/laut/istana_naga", 2, 64, 41, adapt="none", safe=True))
    write_json(json_path("structure", "laut", "pulau_penglai"),
               js("laut/penglai_start", "#kultivator:has_structure/laut/pulau_penglai", 1, 48, 41, adapt="none",
                  safe=True))
    # Dermaga Tiba: the start jigsaw (deck, local y 60) lands at y 100, one block above the sea's top water layer (99).
    write_json(json_path("structure", "laut", "dermaga_tiba"),
               js("laut/dermaga_start", "#kultivator:has_structure/laut/dermaga_tiba", 0, 32, 101, adapt="none",
                  jig="laut/tiba", safe=True))
    write_json(json_path("structure", "laut", "pusaran_laut"),
               js("laut/pusaran_start", "#kultivator:has_structure/laut/pusaran_laut", 1, 32, 0,
                  heightmap="OCEAN_FLOOR_WG", adapt="beard_thin"))


def write_sets():
    def rs(struct, spacing, separation, salt, exclusion=None, overworld=False):
        placement = {"type": "minecraft:random_spread", "salt": salt, "separation": separation, "spacing": spacing}
        if overworld:
            # rule W4: never within 6 chunks of the world's centre (the spine's random_spread with min_chunks)
            placement["type"] = J("jauh_dari_pusat")
            placement["min_chunks"] = 6
        if exclusion:
            placement["exclusion_zone"] = {"other_set": exclusion[0], "chunk_count": exclusion[1]}
        return {"placement": placement, "structures": [{"structure": J(struct), "weight": 1}]}
    # base data: the sets of D's own dimensions
    write_json(json_path("structure_set", "bawah", "istana_yama"),
               {"placement": {"type": "minecraft:dimension_origin"},
                "structures": [{"structure": J("bawah/istana_yama"), "weight": 1}]})
    write_json(json_path("structure_set", "bawah", "desa_arwah"),
               rs("bawah/desa_arwah", 32, 12, 340564, (J("bawah/istana_yama"), 12)))
    write_json(json_path("structure_set", "laut", "dermaga_tiba"),
               {"placement": {"type": "minecraft:dimension_origin"},
                "structures": [{"structure": J("laut/dermaga_tiba"), "weight": 1}]})
    pulau_set = rs("laut/pulau_herba", 20, 8, 340569, (J("laut/dermaga_tiba"), 3))
    pulau_set["structures"] = [{"structure": J("laut/pulau_" + kind), "weight": ISLAND_WEIGHT[kind]} for kind in ISLAND_KINDS]
    write_json(json_path("structure_set", "laut", "pulau_roh"), pulau_set)
    write_json(json_path("structure_set", "laut", "istana_naga"),
               rs("laut/istana_naga", 60, 24, 340570, (J("laut/pulau_roh"), 3)))
    write_json(json_path("structure_set", "laut", "pulau_penglai"),
               rs("laut/pulau_penglai", 140, 60, 340571, (J("laut/pulau_roh"), 4)))
    # the toggle pack (DESIGN 34.2.3, 34.4.5): overworld sets only
    pack_sets = os.path.join(PACK_DIR, "data", NS, "worldgen", "structure_set")
    kuil_set = rs("bawah/kuil_gerbang_hantu", 56, 22, 340563, ("minecraft:villages", 6), overworld=True)
    kuil_set["structures"].append({"structure": J("bawah/kuil_rawa"), "weight": 1})
    write_json(os.path.join(pack_sets, "bawah", "kuil_gerbang_hantu.json"), kuil_set)
    write_json(os.path.join(pack_sets, "laut", "pusaran_laut.json"),
               rs("laut/pusaran_laut", 80, 32, 340568, ("minecraft:ocean_monuments", 5), overworld=True))
    write_json(os.path.join(PACK_DIR, "pack.mcmeta"), {
        "pack": {"description": {"translate": "kultivator.dimensi.pack.description"}, "min_format": 121, "max_format": 121}})


def write_tags():
    tag = lambda vals: {"replace": False, "values": vals}  # noqa: E731
    b = os.path.join(DATA, "tags", "worldgen", "biome", "has_structure")
    write_json(os.path.join(b, "bawah", "istana_yama.json"),
               tag([J("huangquan"), J("wangchuan"), J("hutan_arwah"), J("dataran_cermin")]))
    write_json(os.path.join(b, "bawah", "desa_arwah.json"), tag([J("huangquan"), J("hutan_arwah"), J("dataran_cermin")]))
    write_json(os.path.join(b, "bawah", "kuil_gerbang_hantu.json"), tag(["minecraft:dark_forest", "minecraft:pale_garden"]))
    write_json(os.path.join(b, "bawah", "kuil_rawa.json"), tag(["minecraft:swamp", "minecraft:mangrove_swamp"]))
    write_json(os.path.join(b, "laut", "pulau_roh.json"), tag([J("laut_roh")]))
    write_json(os.path.join(b, "laut", "dermaga_tiba.json"), tag([J("laut_roh")]))
    write_json(os.path.join(b, "laut", "istana_naga.json"), tag([J("laut_roh")]))
    write_json(os.path.join(b, "laut", "pulau_penglai.json"), tag([J("laut_roh")]))
    write_json(os.path.join(b, "laut", "pusaran_laut.json"),
               tag(["minecraft:deep_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean",
                    "minecraft:deep_frozen_ocean"]))
    s = os.path.join(DATA, "tags", "worldgen", "structure")
    write_json(os.path.join(s, "bawah.json"),
               tag([J("bawah/istana_yama"), J("bawah/desa_arwah"), J("bawah/kuil_gerbang_hantu"), J("bawah/kuil_rawa")]))
    write_json(os.path.join(s, "bawah", "kuil.json"), tag([J("bawah/kuil_gerbang_hantu"), J("bawah/kuil_rawa")]))
    write_json(os.path.join(s, "laut.json"),
               tag([J("laut/pulau_roh")] + [J("laut/pulau_" + k) for k in ISLAND_KINDS]
                   + [J("laut/istana_naga"), J("laut/pulau_penglai"), J("laut/pusaran_laut")]))
    write_json(os.path.join(s, "laut", "pusaran.json"), tag([J("laut/pusaran_laut")]))
    write_json(os.path.join(s, "laut", "pulau.json"), tag([J("laut/pulau_roh")] + [J("laut/pulau_" + k) for k in ISLAND_KINDS]))
    for kind in ISLAND_KINDS:
        write_json(os.path.join(s, "laut", "pulau_%s.json" % kind), tag([J("laut/pulau_" + kind)]))
    write_json(os.path.join(s, "laut", "istana.json"), tag([J("laut/istana_naga")]))
    write_json(os.path.join(s, "laut", "penglai.json"), tag([J("laut/pulau_penglai")]))


# ------------------------------------------------------------------------------------------------ dimensions
def g(axis, a, b, va, vb):
    return {"type": "minecraft:gradient", "axis": axis, "from_coordinate": a, "to_coordinate": b,
            "from_value": va, "to_value": vb}


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def noise(n, xz, y):
    return {"type": "minecraft:noise", "noise": n, "xz_scale": xz, "y_scale": y}


def clamp(i, lo, hi):
    return {"type": "minecraft:clamp", "input": i, "min": lo, "max": hi}


def bawah_density():
    """Dunia Bawah terrain (no vanilla copy needed for the shape; structure copied from nether.json's router):
    an open dark plain around y 48 (zero crossing), its height pushed by the same 2-D noise the router uses as
    'vegetation' (so the Wangchuan biome, chosen where that noise is low, is where the ground dips under the sea at
    y 44), rare basalt spires rising into the fog, and a flat ring within ~96 b of the origin for the Yama court."""
    veg = noise("minecraft:nether/vegetation", 0.25, 0.0)
    dist = {"type": "minecraft:sqrt", "input": add({"type": "minecraft:square", "input": g("x", -512, 512, -512.0, 512.0)},
                                                   {"type": "minecraft:square", "input": g("z", -512, 512, -512.0, 512.0)})}
    flat = clamp(add(mul(dist, 0.012), -1.15), 0.0, 1.0)          # 0 inside ~96 b, 1 beyond ~180 b
    base = g("y", 16, 80, 1.2, -1.2)                               # 0 at y 48
    relief = mul(flat, add(mul(veg, 0.75), mul(noise("minecraft:surface", 1.0, 0.0), 0.25)))
    detail = mul(flat, mul(noise("minecraft:gravel", 2.0, 1.5), 0.08))
    floor = add(base, add(relief, detail))
    spire_mask = add(mul(noise("minecraft:pillar", 6.0, 0.0), 5.0), -3.4)   # positive in rare spots
    spire = {"type": "minecraft:min", "left": mul(flat, spire_mask), "right": g("y", 60, 150, 1.5, -1.5)}
    shape = {"type": "minecraft:max", "left": floor, "right": spire}
    final = add({"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8, "input": shape},
                {"type": "minecraft:beardifier"})
    return final, veg


def write_bawah_dimension():
    final, veg = bawah_density()
    settings = {
        "default_block": "minecraft:blackstone",
        "default_fluid": "minecraft:water",
        "disable_mob_generation": True,
        "legacy_random_source": False,
        "material_rule": J("dunia_bawah"),
        "noise": {"height": 256, "min_y": 0},
        "noise_router": {
            "chunk_surface_level": 0.0, "continents": 0.0, "depth": 0.0, "erosion": 0.0, "ridges": 0.0,
            "final_density": final,
            "temperature": noise("minecraft:nether/temperature", 0.25, 0.0),
            "vegetation": veg,
        },
        "sea_level": 44,
        "spawn_target": [],
    }
    write_json(os.path.join(DATA, "worldgen", "noise_settings", "dunia_bawah.json"), settings)

    def blk(b):
        return {"type": "minecraft:block", "result_state": b}

    def cond(c, then):
        return {"type": "minecraft:condition", "if_true": c, "then_run": then}

    def biome(*bs):
        return {"type": "minecraft:biome", "biome_is": [J(b) for b in bs]}

    def patch(lo, then, n="minecraft:patch"):
        return cond({"type": "minecraft:noise_threshold", "noise": n, "min_threshold": lo,
                     "max_threshold": 1.7976931348623157e+308}, then)
    surface = {"type": "minecraft:sequence", "sequence": [
        cond(biome("huangquan"), {"type": "minecraft:sequence", "sequence": [
            cond("minecraft:on_floor", {"type": "minecraft:sequence", "sequence": [
                patch(0.25, blk("minecraft:rooted_dirt")), patch(-0.1, blk("minecraft:coarse_dirt"), "minecraft:gravel"),
                blk("minecraft:podzol")]}),
            cond("minecraft:under_floor", blk("minecraft:packed_mud"))]}),
        cond(biome("wangchuan"), {"type": "minecraft:sequence", "sequence": [
            cond("minecraft:on_floor", {"type": "minecraft:sequence", "sequence": [
                patch(0.1, blk("minecraft:gravel")), blk("minecraft:mud")]}),
            cond("minecraft:under_floor", blk("minecraft:smooth_basalt"))]}),
        cond(biome("hutan_arwah"), {"type": "minecraft:sequence", "sequence": [
            cond("minecraft:on_floor", {"type": "minecraft:sequence", "sequence": [
                patch(0.3, blk("minecraft:soul_soil")), blk("minecraft:pale_moss_block")]}),
            cond("minecraft:under_floor", blk("minecraft:rooted_dirt"))]}),
        cond(biome("dataran_cermin"), {"type": "minecraft:sequence", "sequence": [
            cond("minecraft:on_floor", {"type": "minecraft:sequence", "sequence": [
                patch(0.35, blk("minecraft:calcite")), patch(-0.2, blk("minecraft:smooth_basalt"), "minecraft:gravel"),
                blk("minecraft:polished_blackstone")]}),
            cond("minecraft:under_floor", blk("minecraft:basalt"))]}),
    ]}
    rule = {"type": "minecraft:sequence", "sequence": [
        "minecraft:bedrock_floor",
        surface,
        patch(0.55, blk("minecraft:basalt"), "minecraft:nether_state_selector"),
        cond({"type": "minecraft:noise_threshold", "noise": "minecraft:patch", "min_threshold": 0.8,
              "max_threshold": 1.7976931348623157e+308}, blk("minecraft:bone_block")),
    ]}
    write_json(os.path.join(DATA, "worldgen", "material_rule", "dunia_bawah.json"), rule)
    write_json(os.path.join(DATA, "dimension", "dunia_bawah.json"), {
        "type": J("dunia_bawah"),
        "generator": {"type": "minecraft:noise", "settings": J("dunia_bawah"), "biome_source": {
            "type": "minecraft:multi_noise", "biomes": [
                _mn("wangchuan", t=(-1.0, 1.0), v=(-1.0, -0.32)),
                _mn("huangquan", t=(-1.0, -0.15), v=(-0.32, 1.0)),
                _mn("dataran_cermin", t=(-0.15, 0.3), v=(-0.32, 1.0)),
                _mn("hutan_arwah", t=(0.3, 1.0), v=(-0.32, 1.0)),
            ]}}})
    no_sleep = {"can_set_spawn": "never", "can_sleep": "never", "error_message": {"translate": "kultivator.dimensi.no_sleep"}}
    write_json(os.path.join(DATA, "dimension_type", "dunia_bawah.json"), {
        "ambient_light": 0.15,
        "attributes": {
            "minecraft:gameplay/bed_rule": no_sleep,
            "minecraft:gameplay/straw_bed_rule": no_sleep,
            "minecraft:gameplay/can_pillager_patrol_spawn": False,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/natural_mob_spawns": _no_spawns(),
            "minecraft:gameplay/piglins_zombify": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/sky_light_level": 4.0,
            "minecraft:visual/ambient_light_color": "#2a2436",
            "minecraft:visual/fog_start_distance": 10.0,
            "minecraft:visual/fog_end_distance": 96.0,
            "minecraft:visual/sky_light_color": "#6a5a8a",
            "minecraft:visual/sky_light_factor": 0.0,
        },
        "cardinal_light": "nether",
        "coordinate_scale": 1.0,
        "has_ceiling": True,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": False,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_nether",
        "logical_height": 256,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "skybox": "none",
        "timelines": "#minecraft:in_nether",
    })
    # biomes
    lilies_a = _flower_feature("bawah/bunga_huangquan", "minecraft:poppy")
    lilies_b = _flower_feature("bawah/bunga_huangquan_tulip", "minecraft:red_tulip")
    _bawah_biome("huangquan", fog="#3a1c1c", water="#5a1e24", wfog="#2a0c10", particles="minecraft:ash", p=0.004,
                 features=[lilies_a, lilies_b], music="minecraft:music.nether.soul_sand_valley")
    _bawah_biome("wangchuan", fog="#1c2a33", water="#1f3f4f", wfog="#0e1c24", particles="minecraft:white_ash",
                 p=0.006, features=[], music="minecraft:music.nether.basalt_deltas")
    _bawah_biome("hutan_arwah", fog="#2c3033", water="#2f3a40", wfog="#141a1e", particles="minecraft:white_ash",
                 p=0.01, features=["minecraft:pale_garden_vegetation", "minecraft:pale_moss_patch", "minecraft:patch_soul_fire"],
                 music="minecraft:music.nether.warped_forest")
    _bawah_biome("dataran_cermin", fog="#262233", water="#3b3a5c", wfog="#16142a", particles="minecraft:ash",
                 p=0.003, features=["minecraft:small_basalt_columns"],
                 music="minecraft:music.nether.soul_sand_valley")


def _mn(b, t, v):
    full = [-1.0, 1.0]
    return {"biome": J(b), "parameters": {"temperature": list(t), "humidity": list(v), "continentalness": full,
                                         "erosion": full, "depth": 0.0, "weirdness": full, "offset": 0.0}}


def _no_spawns():
    return {"spawn_costs": {}, "spawns_by_category": {k: [] for k in (
        "ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient",
        "water_creature")}}


def _flower_feature(name, block):
    folder, rest = name.split("/", 1)
    write_json(os.path.join(DATA, "worldgen", "feature", folder, rest + ".json"),
               {"type": "minecraft:simple_block", "to_place": {"id": block}})
    write_json(os.path.join(DATA, "worldgen", "placed_feature", folder, rest + ".json"), {
        "feature": J(name),
        "placement": [
            {"type": "minecraft:count", "count": 6},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
            {"type": "minecraft:biome"},
            {"type": "minecraft:count", "count": 40},
            {"type": "minecraft:offset",
             "x": {"type": "minecraft:trapezoid", "max": 6, "min": -6, "plateau": 0},
             "y": {"type": "minecraft:trapezoid", "max": 2, "min": -2, "plateau": 0},
             "z": {"type": "minecraft:trapezoid", "max": 6, "min": -6, "plateau": 0}},
            {"type": "minecraft:block_predicate_filter",
             "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
        ]})
    return J(name)


def _bawah_biome(name, fog, water, wfog, particles, p, features, music):
    steps = [[] for _ in range(11)]
    steps[9] = list(features)
    write_json(os.path.join(DATA, "worldgen", "biome", name + ".json"), {
        "attributes": {
            "minecraft:audio/ambient_sounds": {
                "additions": {"sound": "minecraft:ambient.soul_sand_valley.additions", "tick_chance": 0.0111},
                "loop": "minecraft:ambient.soul_sand_valley.loop",
                "mood": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.soul_sand_valley.mood",
                         "tick_delay": 6000}},
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": music}},
            "minecraft:visual/ambient_particles": {"argument": [{"particle": {"type": particles}, "probability": p}],
                                                   "modifier": "append"},
            "minecraft:visual/fog_color": fog,
            "minecraft:visual/water_fog_color": wfog,
        },
        "carvers": [],
        "downfall": 0.0,
        "effects": {"water_color": water, "grass_color": "#6f6a5a", "foliage_color": "#6a665c"},
        "features": steps,
        "has_precipitation": False,
        "temperature": 0.8,
    })


def write_laut_dimension():
    write_json(os.path.join(DATA, "dimension", "laut_tanpa_batas.json"), {
        "type": J("laut_tanpa_batas"),
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": J("laut_roh"), "features": True, "lakes": False,
            "layers": [{"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:sandstone", "height": 33},
                       {"block": "minecraft:sand", "height": 6}, {"block": "minecraft:water", "height": 60}],
            "structure_overrides": [J("laut/dermaga_tiba"), J("laut/pulau_roh"), J("laut/istana_naga"),
                                    J("laut/pulau_penglai")]}}})
    no_sleep = {"can_set_spawn": "never", "can_sleep": "never", "error_message": {"translate": "kultivator.dimensi.no_sleep"}}
    write_json(os.path.join(DATA, "dimension_type", "laut_tanpa_batas.json"), {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:gameplay/bed_rule": no_sleep,
            "minecraft:gameplay/straw_bed_rule": no_sleep,
            "minecraft:gameplay/can_pillager_patrol_spawn": False,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/piglins_zombify": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/cloud_color": "#e8ffffff",
            "minecraft:visual/cloud_height": 196.0,
        },
        "coordinate_scale": 1.0,
        "default_clock": "minecraft:overworld",
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": False,
        "has_skylight": True,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 256,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "skybox": "overworld",
        "timelines": ["minecraft:day", "minecraft:moon"],
    })
    steps = [[] for _ in range(11)]
    steps[9] = ["minecraft:warm_ocean_vegetation", "minecraft:seagrass_deep_warm", "minecraft:sea_pickle",
                "minecraft:kelp_warm"]
    write_json(os.path.join(DATA, "worldgen", "biome", "laut_roh.json"), {
        "attributes": {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000,
                                                             "sound": "minecraft:music.overworld.lush_caves"}},
            "minecraft:gameplay/natural_mob_spawns": {"argument": {
                "spawn_costs": {},
                "spawns_by_category": {
                    "ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [],
                    "underground_water_creature": [],
                    "water_ambient": [{"type": "minecraft:tropical_fish", "count": {"type": "minecraft:uniform",
                                                                                    "max_inclusive": 8, "min_inclusive": 4},
                                       "weight": 25}],
                    "water_creature": [{"type": "minecraft:dolphin", "count": {"type": "minecraft:uniform",
                                                                              "max_inclusive": 2, "min_inclusive": 1},
                                        "weight": 2},
                                       {"type": "minecraft:squid", "count": {"type": "minecraft:uniform",
                                                                            "max_inclusive": 4, "min_inclusive": 1},
                                        "weight": 3}]}},
                "modifier": "overlay"},
            "minecraft:visual/ambient_particles": {"argument": [{"particle": {"type": "minecraft:end_rod"},
                                                                 "probability": 0.0008}], "modifier": "append"},
            "minecraft:visual/fog_color": "#c8f0f4",
            "minecraft:visual/sky_color": "#7fd2f0",
            "minecraft:visual/water_fog_color": "#0b5a66",
        },
        "carvers": [],
        "downfall": 0.5,
        "effects": {"water_color": "#1fc2c8", "grass_color": "#7ee0a0", "foliage_color": "#6fd59a"},
        "features": steps,
        "has_precipitation": True,
        "temperature": 0.8,
    })


def _loot_item(name, weight, lo=1, hi=1):
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    if hi > 1:
        entry["functions"] = [{"function": "minecraft:set_count",
                               "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return entry


def write_rewards():
    """Chest loot of the templates, the two recipes and the four advancements of module D (DESIGN 34.5.13-14, 34.9).
    Every pool is capped like harta/HartaLoot: a handful of common rolls and one rare roll that is usually empty."""
    loot = os.path.join(DATA, "loot_table", "chests", "dunia", "dimensi")
    write_json(os.path.join(loot, "desa_arwah.json"), {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
            _loot_item("kultivator:giok_arwah", 6, 1, 2),
            _loot_item("kultivator:spirit_stone_low", 8, 2, 6),
            _loot_item("kultivator:spirit_stone_mid", 3),
            _loot_item("minecraft:paper", 6, 1, 4),
            _loot_item("minecraft:white_candle", 5, 1, 3),
            _loot_item("minecraft:bone", 5, 1, 3),
            _loot_item("minecraft:soul_torch", 4, 2, 4)]},
        {"rolls": 1, "entries": [{"type": "minecraft:empty", "weight": 7},
                                 _loot_item("kultivator:soul_nurturing_wood", 2),
                                 _loot_item("kultivator:dupa_arwah", 1)]}]})
    write_json(os.path.join(loot, "istana_naga.json"), {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
            _loot_item("kultivator:spirit_stone_mid", 6, 1, 4),
            _loot_item("kultivator:spirit_stone_high", 2),
            _loot_item("minecraft:prismarine_crystals", 6, 2, 8),
            _loot_item("minecraft:nautilus_shell", 4, 1, 2),
            _loot_item("minecraft:gold_ingot", 5, 2, 5),
            _loot_item("minecraft:sea_lantern", 3, 1, 3)]},
        {"rolls": 1, "entries": [{"type": "minecraft:empty", "weight": 6},
                                 _loot_item("kultivator:mutiara_naga", 2),
                                 _loot_item("minecraft:heart_of_the_sea", 1),
                                 _loot_item("kultivator:mutiara_penunjuk_laut", 1)]}]})
    # the detail pass: the atoll tower's cache (Golden Core-Nascent Soul) and the sunken junk's salvage
    write_json(os.path.join(loot, "pulau.json"), {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
            _loot_item("kultivator:spirit_stone_mid", 5, 1, 3),
            _loot_item("kultivator:spirit_stone_low", 8, 3, 8),
            _loot_item("minecraft:prismarine_shard", 6, 2, 6),
            _loot_item("minecraft:nautilus_shell", 2),
            _loot_item("minecraft:spyglass", 1),
            _loot_item("minecraft:cooked_cod", 5, 2, 6)]},
        {"rolls": 1, "entries": [{"type": "minecraft:empty", "weight": 7},
                                 _loot_item("kultivator:mutiara_penunjuk_laut", 2),
                                 _loot_item("kultivator:spirit_stone_high", 1)]}]})
    write_json(os.path.join(loot, "kapal_karam.json"), {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 5}, "entries": [
            _loot_item("kultivator:spirit_stone_low", 8, 2, 10),
            _loot_item("kultivator:spirit_stone_mid", 4, 1, 2),
            _loot_item("minecraft:gold_nugget", 6, 3, 9),
            _loot_item("minecraft:paper", 4, 1, 5),
            _loot_item("minecraft:emerald", 3, 1, 3),
            _loot_item("minecraft:lead", 3)]},
        {"rolls": 1, "entries": [{"type": "minecraft:empty", "weight": 8},
                                 _loot_item("minecraft:heart_of_the_sea", 1),
                                 _loot_item("kultivator:mutiara_penghindar_air", 1)]}]})
    recipes = os.path.join(DATA, "recipe", "dunia")
    write_json(os.path.join(recipes, "dimensi_dupa_arwah.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["kultivator:soul_nurturing_wood", "kultivator:spirit_stone_mid", "kultivator:spirit_stone_mid"],
        "result": {"count": 1, "id": "kultivator:dupa_arwah"}})
    write_json(os.path.join(recipes, "dimensi_mutiara_penunjuk_laut.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:nautilus_shell", "minecraft:prismarine_crystals", "minecraft:prismarine_crystals",
                        "kultivator:spirit_stone_mid"],
        "result": {"count": 1, "id": "kultivator:mutiara_penunjuk_laut"}})
    adv = os.path.join(DATA, "advancement", "dunia")
    for name, icon, frame in (("naihe_crossed", "kultivator:lentera_arwah", "task"),
                              ("ten_courts", "kultivator:giok_arwah", "goal"),
                              ("sea_arrived", "kultivator:mutiara_penunjuk_laut", "task"),
                              ("dragon_king", "kultivator:mutiara_naga", "goal"),
                              # the detail pass (BawahPeople, Courts, GhostQuests, SeaPeople, Dimensi.monsterSlain)
                              ("meng_po_soup", "minecraft:mushroom_stew", "task"),
                              ("court_mirror", "kultivator:giok_arwah", "task"),
                              ("letter_delivered", "kultivator:surat_arwah", "task"),
                              ("lost_soul_home", "kultivator:lentera_arwah", "task"),
                              ("name_struck", "minecraft:writable_book", "challenge"),
                              ("pearl_returned", "kultivator:mutiara_naga", "goal"),
                              ("penglai_water", "kultivator:air_mata_air_penglai", "goal"),
                              ("islands_all", "kultivator:mutiara_penunjuk_laut", "goal"),
                              ("turtle_rider", "minecraft:turtle_scute", "task"),
                              ("sea_tier4", "minecraft:trident", "challenge")):
        write_json(os.path.join(adv, name + ".json"), {
            "parent": "kultivator:root",
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "display": {"description": {"translate": "advancement.kultivator.dunia.%s.description" % name},
                        "icon": {"id": icon},
                        "title": {"translate": "advancement.kultivator.dunia.%s.title" % name},
                        "frame": frame, "hidden": True},
            "requirements": [["done"]]})


def main():
    built = []
    built.append(teras())
    built.append(jalan_huangquan())
    built.append(jembatan_naihe())
    built.append(paviliun_mengpo())
    built.append(lorong("lorong_a", ["pengadilan_1", "pengadilan_2", "pengadilan_3", "pengadilan_4"], "ujung_dizang"))
    built.append(lorong("lorong_b", ["pengadilan_5", "pengadilan_6", "pengadilan_7", "pengadilan_8"], "ujung_yaksha"))
    built.append(lorong("lorong_c", ["pengadilan_9", "pengadilan_10", None, None], "istana"))
    for n in range(1, 11):
        built.append(pengadilan(n))
    built.append(istana_yanluo())
    built.append(kuil_dizang())
    built.append(pos_yaksha())
    built.append(desa_pusat())
    built.append(desa_jalan())
    built.append(desa_jalan(1))
    for v in range(3):
        built.append(desa_rumah(v))
    built.append(desa_rumah_abu())
    built.append(desa_toko_kertas())
    built.append(kuil())
    built.append(kuil_pagoda())
    built.append(kuil_rawa())
    built.append(kuil_gerbang())
    built.append(kuil_makam())
    built.append(kuil_makam_keluarga())
    for kind in ISLAND_KINDS:
        built.append(pulau_core(kind))
        built.append(pulau_core(kind, 1))
    for kind in ("dermaga", "pulau_kecil", "karang", "nelayan", "kapal_karam"):
        built.append(satelit(kind))
    built.append(istana_naga())
    built.append(istana_gerbang())
    built.append(penglai())
    built.append(pusaran())
    built.append(dermaga_tiba())
    write_pools()
    write_processors()
    write_structures()
    write_sets()
    write_tags()
    write_bawah_dimension()
    write_laut_dimension()
    write_rewards()
    # read every template back (the round trip the game will do)
    for path in built:
        _, root = nbt.read_file(path)
        assert root["DataVersion"] == 5023
        assert len(root["blocks"]) > 0
    validate_all()


if __name__ == "__main__":
    main()
