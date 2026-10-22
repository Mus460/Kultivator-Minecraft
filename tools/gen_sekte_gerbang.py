"""Module D of DESIGN 36 (Diplomasi & Sekte Rival, ideas 48 and 49): the mountain gates of the NPC sects.

Writes (only module D's own files, DESIGN 36.4 D):
  data/kultivator/structure/sekte/gerbang_{kecil,sedang,besar}.nbt        one template per gate size
  data/kultivator/worldgen/template_pool/sekte/gerbang_{kecil,sedang,besar}.json
  data/kultivator/worldgen/structure/sekte_gerbang_{kecil,sedang,besar}.json
  data/kultivator/tags/worldgen/biome/has_structure/sekte_gerbang.json   land biomes of the overworld
  data/kultivator/tags/worldgen/structure/sekte_gerbang.json             all three (for /locate)
  resourcepacks/sekte_gerbang/pack.mcmeta                                  the module's built-in pack (registered by
  resourcepacks/sekte_gerbang/data/kultivator/worldgen/structure_set/      GerbangSekte only while the module and
      sekte_gerbang_{kecil,sedang,besar}.json                              diplomasi.strukturAktif are on)

The sets use the placement kultivator:gerbang_sekte (GerbangSektePlacement): each sect's gate chunk comes from the seeded
GerbangPlan, one set per size band (a structure set cannot choose its structure by band). Only world generation places
these templates, in new chunks (DESIGN 36.0 rule 1, contract C36.D4); no runtime code calls setBlock for them.

Facts this file relies on (checked in reference/mcsrc, 26.3, and in tools/gen_dunia_reruntuhan.py's notes):
  * SinglePoolElement places with known shape (no neighbour updates): fences stand as plain posts, so none of these
    pieces relies on a connected fence, wall or pane; stairs carry their shape explicitly.
  * The start piece of a jigsaw structure is placed at the chunk's min corner, projected to WORLD_SURFACE_WG, with a random
    rotation; template entities (the DuniaAnchor markers) rotate with it.
  * Beardifier: beard_box fills under the piece and carves terrain above its ground level inside its box, so the raised
    terraces stand on solid stone even on a slope and no hill grows through a hall.
  * Leaves in a template keep persistent=true (they never decay); water is a plain source block.

Anchors (kind "sektenpc/<what>", DESIGN 34.4.1 marker entity) read by AnggotaSekte: "gerbang" (one, at the gate; marks
the gate born and greets visitors), "murid" (member spots, data slot 0-5, panji = carries the sect banner), "ketua" (the
sect master's throne).

Run: python tools/gen_sekte_gerbang.py [--plan]   (prints validate_all(); --plan prints a floor plan of each size)
Sumber: the mountain gates of the Seven Great Sects of Yue (A Record of a Mortal's Journey); the paifang gate, the
terraced halls and the lotus pond of a Chinese mountain monastery (Shaolin, Wudang).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from structgen import DATA, PALETTES, REPO, Piece, out_path, validate_all, write_json  # noqa: E402

PLAN = "--plan" in sys.argv

# ------------------------------------------------------------------------------------------------- palettes
BASE = dict(PALETTES["kota"])
KECIL = dict(BASE)
KECIL.update({
    "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:stone_bricks", "wall": "minecraft:spruce_planks",
    "wall_alt": "minecraft:stripped_spruce_log", "pillar": "minecraft:stripped_mangrove_log", "trim": "minecraft:spruce_log",
    "roof": "minecraft:dark_oak_stairs", "roof_slab": "minecraft:dark_oak_slab", "roof_full": "minecraft:dark_oak_planks",
    "found": "minecraft:stone_bricks", "found_alt": "minecraft:mossy_stone_bricks", "step": "minecraft:stone_brick_stairs",
    "rim": "minecraft:stone_brick_slab", "path": "minecraft:stone_bricks", "path_alt": "minecraft:cracked_stone_bricks",
    "fence": "minecraft:spruce_fence", "window": "minecraft:spruce_trapdoor", "banner": "minecraft:red_wall_banner",
    "glow": "minecraft:lantern", "carpet": "minecraft:red_carpet", "accent": "minecraft:gold_block",
})
SEDANG = dict(BASE)
SEDANG.update({
    "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:andesite", "wall": "minecraft:dark_oak_planks",
    "wall_alt": "minecraft:stripped_dark_oak_log", "pillar": "minecraft:stripped_mangrove_log", "trim": "minecraft:dark_oak_log",
    "roof": "minecraft:deepslate_tile_stairs", "roof_slab": "minecraft:deepslate_tile_slab", "roof_full": "minecraft:deepslate_tiles",
    "found": "minecraft:stone_bricks", "found_alt": "minecraft:cracked_stone_bricks", "step": "minecraft:stone_brick_stairs",
    "rim": "minecraft:stone_brick_slab", "path": "minecraft:stone_bricks", "path_alt": "minecraft:chiseled_stone_bricks",
    "fence": "minecraft:dark_oak_fence", "window": "minecraft:spruce_trapdoor", "banner": "minecraft:red_wall_banner",
    "glow": "minecraft:lantern", "carpet": "minecraft:red_carpet", "accent": "minecraft:gold_block",
})
BESAR = dict(BASE)
BESAR.update({
    "floor": "minecraft:smooth_quartz", "floor_alt": "minecraft:polished_diorite", "wall": "minecraft:quartz_bricks",
    "wall_alt": "minecraft:chiseled_quartz_block", "pillar": "minecraft:stripped_mangrove_log", "trim": "minecraft:quartz_pillar",
    "roof": "minecraft:dark_prismarine_stairs", "roof_slab": "minecraft:dark_prismarine_slab", "roof_full": "minecraft:dark_prismarine",
    "found": "minecraft:stone_bricks", "found_alt": "minecraft:polished_andesite", "step": "minecraft:polished_diorite_stairs",
    "rim": "minecraft:smooth_quartz_slab", "path": "minecraft:polished_diorite", "path_alt": "minecraft:smooth_quartz",
    "fence": "minecraft:dark_oak_fence", "window": "minecraft:dark_oak_trapdoor", "banner": "minecraft:yellow_wall_banner",
    "glow": "minecraft:lantern", "carpet": "minecraft:yellow_carpet", "accent": "minecraft:gold_block",
})

LEAVES = {"persistent": "true", "distance": "1", "waterlogged": "false"}
TEMPLATES = []
JSONS = []
PACK_JSONS = []
PACK = os.path.join(REPO, "src", "main", "resources", "resourcepacks", "sekte_gerbang")


# ------------------------------------------------------------------------------------------------- shared pieces
def terrace(p, x0, z0, x1, z1, top):
    """A raised stone terrace: solid foundation y 0..top-1, paved floor at y=top, a slab rim round the open edges."""
    p.fill(x0, 0, z0, x1, top - 1, z1, "found", alt="found_alt", alt_chance=0.12)
    p.floor(x0, top, z0, x1, z1)


def rim(p, x0, z0, x1, z1, y, gap=None):
    """Bottom slabs along the terrace edge (walkable), leaving the x-range `gap` open on the south edge."""
    for x in range(x0, x1 + 1):
        if not (gap and gap[0] <= x <= gap[1]):
            p.slab(x, y, z1, "rim")
        p.slab(x, y, z0, "rim")
    for z in range(z0, z1 + 1):
        p.slab(x0, y, z, "rim")
        p.slab(x1, y, z, "rim")


def steps(p, x0, width, z_low, y_low, count):
    """A broad flight up toward the north from (x0, y_low, z_low), `width` wide, `count` steps, solid underneath."""
    p.staircase(x0, y_low, z_low, "north", count, width=width, role="step", support="found")


def paifang(p, xs, z, y, height, roof_y, banner=True):
    """The memorial gate: red pillars at the x positions `xs`, a lintel with a gold plaque, a small hip roof per bay
    (the middle bay higher) and wall banners on the outer pillars."""
    for x in xs:
        p.pillar(x, y, y + height, z, role="pillar", base="found", cap="trim")
    x0, x1 = xs[0], xs[-1]
    for x in range(x0 - 1, x1 + 2):
        p.put(x, y + height, z, "trim", {"axis": "x"} if p.b("trim").endswith(("_log", "_pillar")) else None)
    mid = (x0 + x1) // 2
    p.put(mid, y + height - 1, z, "accent")
    p.put(mid - 1, y + height - 1, z, "roof_full")
    p.put(mid + 1, y + height - 1, z, "roof_full")
    bays = list(zip(xs, xs[1:]))
    centre = len(bays) // 2
    for i, (a, b) in enumerate(bays):
        lift = 1 if i == centre else 0
        p.roof_hip(a, roof_y + lift, z, b, z, overhang=1)
    if banner:
        for x in (x0, x1):
            p.put(x, y + height - 3, z + 1, "banner", {"facing": "south"})


def hall(p, x0, z0, x1, z1, y, height, tiers=1, door_side="south", windows=4):
    """A hall: pillared walls, a door, a plank ceiling, a hip roof (tiers > 1: a pagoda-style double eave)."""
    p.walls(x0, y, z0, x1, y + height - 1, z1, door=(door_side, 3), windows=windows)
    p.floor(x0 + 1, y - 1, z0 + 1, x1 - 1, z1 - 1, "floor", alt="floor_alt", alt_chance=0.05)
    p.fill(x0, y + height, z0, x1, y + height, z1, "roof_full")
    return p.roof_hip(x0, y + height + 1, z0, x1, z1, overhang=1, tiers=tiers, tier_gap=2, tier_band=2, band_role="wall")


def throne(p, x, y, z):
    """The sect master's seat facing south, gold-flanked, a screen of wall behind."""
    p.stairs(x, y, z, "roof", "north")
    p.put(x - 1, y, z, "accent")
    p.put(x + 1, y, z, "accent")
    p.slab(x - 1, y + 1, z, "roof_slab")
    p.slab(x + 1, y + 1, z, "roof_slab")


def aisle(p, x, y, z0, z1):
    for z in range(z0, z1 + 1):
        p.put(x, y, z, "carpet")


def cherry(p, x, y, z, h=4):
    """A small cherry tree (persistent leaves)."""
    for i in range(h):
        p.put(x, y + i, z, "minecraft:cherry_log", {"axis": "y"})
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (h - 1, h):
                if abs(dx) + abs(dz) <= (3 if dy == h - 1 else 2) and not (dx == 0 and dz == 0 and dy == h - 1):
                    if p.get(x + dx, y + dy, z + dz) is None:
                        p.put(x + dx, y + dy, z + dz, "minecraft:cherry_leaves", LEAVES)
    p.put(x, y + h + 1, z, "minecraft:cherry_leaves", LEAVES)


def censer(p, x, y, z):
    """A bronze incense cauldron on a dark plinth with a candle on each side."""
    p.put(x, y, z, "minecraft:polished_blackstone")
    p.put(x, y + 1, z, "minecraft:cauldron")
    for dx in (-1, 1):
        p.put(x + dx, y, z, "minecraft:polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})


def pond(p, x0, z0, x1, z1, y, rng_lily=0.25):
    """A lotus pond sunk into the floor: one block of water over stone, lily pads, a stone rim."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                p.put(x, y, z, "minecraft:chiseled_stone_bricks" if (x + z) % 4 == 0 else "minecraft:stone_bricks")
            else:
                p.put(x, y - 1, z, "minecraft:mud_bricks")
                p.put(x, y, z, "minecraft:water")
                if p.rng.random() < rng_lily:
                    p.put(x, y + 1, z, "minecraft:lily_pad")


def anchors(p, gate, members, master, band):
    p.anchor(gate[0], gate[1], gate[2], "sektenpc/gerbang", tier=band + 1)
    for i, (x, y, z) in enumerate(members):
        p.anchor(x, y, z, "sektenpc/murid", slot=i, panji=(i % 3 == 0))
    p.anchor(master[0], master[1], master[2], "sektenpc/ketua")


# ------------------------------------------------------------------------------------------------- kecil (tier 1-3)
def build_kecil():
    sx, sy, sz = 25, 20, 33
    p = Piece(sx, sy, sz, seed=360901, palette=KECIL)
    y = 4
    terrace(p, 0, 0, 24, 27, y)
    rim(p, 0, 0, 24, 27, y + 1, gap=(9, 15))
    steps(p, 9, 7, 32, 0, 5)
    paifang(p, [8, 16], 26, y + 1, 6, y + 8)
    # courtyard
    p.path([(12, 26), (12, 12)], y, width=5)
    p.standing_lantern(8, y + 1, 20)
    p.standing_lantern(16, y + 1, 20)
    censer(p, 12, y + 1, 17)
    # training posts
    for x in (4, 20):
        p.put(x, y + 1, 21, "minecraft:stripped_oak_log", {"axis": "y"})
        p.put(x, y + 2, 21, "minecraft:hay_block", {"axis": "y"})
    cherry(p, 3, y + 1, 14, 4)
    cherry(p, 21, y + 1, 14, 4)
    # main hall
    top = hall(p, 4, 2, 20, 11, y + 1, 5, tiers=1)
    aisle(p, 12, y + 1, 5, 10)
    throne(p, 12, y + 1, 4)
    for x in (8, 16):
        p.lantern(x, y + 6, 7)
    p.put(12, y + 1, 3, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    p.put(5, y + 1, 3, "minecraft:bookshelf")
    p.put(19, y + 1, 3, "minecraft:bookshelf")
    anchors(p, (12.5, y + 1.0, 23.5), [(6.5, y + 1.0, 22.5), (18.5, y + 1.0, 22.5), (9.5, y + 1.0, 15.5), (15.5, y + 1.0, 15.5)],
            (12.5, y + 1.0, 5.5), 0)
    p.weather(0.06)
    TEMPLATES.append(("gerbang_kecil", p, top))


# ------------------------------------------------------------------------------------------------- sedang (tier 4-6)
def build_sedang():
    sx, sy, sz = 37, 26, 45
    p = Piece(sx, sy, sz, seed=360902, palette=SEDANG)
    y = 4
    terrace(p, 0, 0, 36, 39, y)
    rim(p, 0, 0, 36, 39, y + 1, gap=(14, 22))
    steps(p, 14, 9, 44, 0, 5)
    paifang(p, [10, 15, 21, 26], 38, y + 1, 7, y + 9)
    # courtyard: lotus pond split by the path
    p.path([(18, 38), (18, 16)], y, width=5)
    pond(p, 6, 22, 15, 31, y)
    pond(p, 21, 22, 30, 31, y)
    for x in (10, 26):
        p.standing_lantern(x, y + 1, 34, height=3)
        p.standing_lantern(x, y + 1, 19, height=3)
    censer(p, 18, y + 1, 19)
    cherry(p, 3, y + 1, 35, 5)
    cherry(p, 33, y + 1, 35, 5)
    # side pavilions facing the courtyard
    hall(p, 1, 17, 7, 25, y + 1, 4, door_side="east", windows=3)
    hall(p, 29, 17, 35, 25, y + 1, 4, door_side="west", windows=3)
    # main hall with a double eave
    top = hall(p, 7, 2, 29, 14, y + 1, 6, tiers=2)
    aisle(p, 18, y + 1, 5, 13)
    throne(p, 18, y + 1, 4)
    for x in (11, 25):
        for z in (6, 11):
            p.pillar(x, y + 1, y + 6, z, role="pillar", base=None, cap=None)
    for x in (13, 23):
        p.lantern(x, y + 7, 8)
    for x in (16, 20):
        p.put(x, y + 1, 3, "minecraft:bookshelf")
    for x in (9, 27):
        p.put(x, y + 3, 14 + 1, "banner", {"facing": "south"})
    anchors(p, (18.5, y + 1.0, 36.5),
            [(12.5, y + 1.0, 35.5), (24.5, y + 1.0, 35.5), (4.5, y + 1.0, 27.5), (32.5, y + 1.0, 27.5), (15.5, y + 1.0, 18.5),
             (21.5, y + 1.0, 18.5)],
            (18.5, y + 1.0, 5.5), 1)
    p.weather(0.05)
    TEMPLATES.append(("gerbang_sedang", p, top))


# ------------------------------------------------------------------------------------------------- besar (tier 7-9)
def cut(p, x0, x1, z0, z1, y0, y1):
    """Clears built cells (a stair cut through a terrace); the beard then carves the terrain there."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                p.clear(x, y, z)


def build_besar():
    sx, sy, sz = 47, 38, 48
    p = Piece(sx, sy, sz, seed=360903, palette=BESAR)
    low, mid, high = 4, 8, 12
    # three terraces: the gate court (front), the middle court, the high court of the great hall
    terrace(p, 0, 36, 46, 42, low)
    terrace(p, 0, 18, 46, 35, mid)
    terrace(p, 0, 0, 46, 17, high)
    rim(p, 0, 36, 46, 42, low + 1, gap=(18, 28))
    # broad central flights: ground -> gate court -> middle court -> high court
    steps(p, 18, 11, 47, 0, 5)
    cut(p, 19, 27, 31, 35, low + 1, mid)
    steps(p, 19, 9, 35, low, 5)
    cut(p, 19, 27, 13, 17, mid + 1, high)
    steps(p, 19, 9, 17, mid, 5)
    # the grand gate
    paifang(p, [11, 17, 23, 29, 35], 41, low + 1, 9, low + 11)
    p.path([(23, 42), (23, 36)], low, width=7)
    for x in (15, 31):
        p.standing_lantern(x, low + 1, 37, height=3)
    cherry(p, 5, low + 1, 39, 5)
    cherry(p, 41, low + 1, 39, 5)
    # middle court: two lotus ponds, censers, side halls, the bell and drum towers
    p.path([(23, 30), (23, 18)], mid, width=5)
    pond(p, 13, 20, 20, 28, mid)
    pond(p, 26, 20, 33, 28, mid)
    censer(p, 17, mid + 1, 30)
    censer(p, 29, mid + 1, 30)
    hall(p, 1, 19, 9, 27, mid + 1, 5, door_side="east", windows=3)
    hall(p, 37, 19, 45, 27, mid + 1, 5, door_side="west", windows=3)
    for x0 in (1, 40):
        hall(p, x0, 29, x0 + 5, 34, mid + 1, 4, tiers=3, door_side="south", windows=0)
    p.put(3, mid + 4, 31, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    # the great hall: double eave, inner pillars, the throne under gold
    top = hall(p, 8, 1, 38, 12, high + 1, 7, tiers=2, windows=4)
    aisle(p, 23, high + 1, 4, 11)
    throne(p, 23, high + 1, 3)
    for x in (13, 18, 28, 33):
        for z in (5, 9):
            p.pillar(x, high + 1, high + 7, z, role="pillar", base=None, cap=None)
    for x in (15, 23, 31):
        p.lantern(x, high + 8, 7)
    for x in (21, 25):
        p.put(x, high + 1, 2, "minecraft:bookshelf")
        p.put(x, high + 2, 2, "minecraft:bookshelf")
    for x in (10, 36):
        p.put(x, high + 4, 13, "banner", {"facing": "south"})
    anchors(p, (23.5, low + 1.0, 39.5),
            [(12.5, low + 1.0, 38.5), (34.5, low + 1.0, 38.5), (10.5, mid + 1.0, 29.5), (36.5, mid + 1.0, 29.5), (17.5, mid + 1.0, 19.5),
             (29.5, mid + 1.0, 19.5)],
            (23.5, high + 1.0, 4.5), 2)
    p.weather(0.04)
    TEMPLATES.append(("gerbang_besar", p, top))


# ------------------------------------------------------------------------------------------------- worldgen JSON
BIOMES = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:flower_forest",
          "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:taiga",
          "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_plains", "minecraft:snowy_taiga",
          "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_hills", "minecraft:windswept_forest",
          "minecraft:windswept_gravelly_hills", "minecraft:cherry_grove", "minecraft:grove", "minecraft:snowy_slopes",
          "minecraft:bamboo_jungle", "minecraft:sparse_jungle", "minecraft:jungle", "minecraft:desert", "minecraft:badlands",
          "minecraft:wooded_badlands", "minecraft:stony_shore", "minecraft:river"]
SETS = {"kecil": (0, 360911), "sedang": (1, 360912), "besar": (2, 360913)}


def wjson(path, obj, pack=False):
    (PACK_JSONS if pack else JSONS).append((path, obj))


def jsons():
    wjson(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", "sekte_gerbang.json"), {"replace": False, "values": BIOMES})
    ids = []
    for size, (band, salt) in SETS.items():
        sid = "sekte_gerbang_" + size
        ids.append("kultivator:" + sid)
        wjson(os.path.join(DATA, "worldgen", "template_pool", "sekte", "gerbang_" + size + ".json"),
              {"elements": [{"element": {"element_type": "minecraft:single_pool_element", "location": "kultivator:sekte/gerbang_" + size,
                                         "processors": "minecraft:empty", "projection": "rigid"}, "weight": 1}],
               "fallback": "minecraft:empty"})
        wjson(os.path.join(DATA, "worldgen", "structure", sid + ".json"),
              {"type": "minecraft:jigsaw", "biomes": "#kultivator:has_structure/sekte_gerbang", "max_distance_from_center": 80, "size": 1,
               "spawn_overrides": {}, "start_height": {"absolute": 0}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
               "start_pool": "kultivator:sekte/gerbang_" + size, "step": "surface_structures", "terrain_adaptation": "beard_box",
               "use_expansion_hack": False})
        wjson(os.path.join(PACK, "data", "kultivator", "worldgen", "structure_set", sid + ".json"),
              {"placement": {"type": "kultivator:gerbang_sekte", "salt": salt, "spacing": 24, "separation": 8, "band": band},
               "structures": [{"structure": "kultivator:" + sid, "weight": 1}]}, pack=True)
    wjson(os.path.join(DATA, "tags", "worldgen", "structure", "sekte_gerbang.json"), {"replace": False, "values": ids})
    wjson(os.path.join(PACK, "pack.mcmeta"),
          {"pack": {"description": {"translate": "kultivator.sekte.diplomasi.pack"}, "min_format": 121, "max_format": 121}}, pack=True)


# ------------------------------------------------------------------------------------------------- output
def plan(name, p, y):
    print("plan %s at y=%d (%dx%dx%d)" % (name, y, p.size[0], p.size[1], p.size[2]))
    glyph = {}
    for z in range(p.size[2]):
        row = ""
        for x in range(p.size[0]):
            b = p.get(x, y, z)
            if b is None:
                row += " "
            elif b == "minecraft:air":
                row += "."
            else:
                short = b.split(":")[1]
                if short not in glyph:
                    glyph[short] = "#ABCDEFGHIKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[len(glyph) % 62]
                row += glyph[short]
        print("  " + row)
    print("  " + ", ".join("%s=%s" % (g, k) for k, g in glyph.items()))


def main():
    build_kecil()
    build_sedang()
    build_besar()
    jsons()
    problems = 0
    for name, p, top in TEMPLATES:
        path = p.save(out_path("sekte", name))
        _, root = nbt.read_file(path)
        anchors_found = [e for e in root["entities"] if e["nbt"].get("id") == "kultivator:dunia_anchor"]
        if len(root["blocks"]) != len(p.cells):
            print("READBACK %s: %d blocks on disk, %d built" % (name, len(root["blocks"]), len(p.cells)))
            problems += 1
        print("%s: %d blocks, %d anchors, roof top y=%d of %d, clipped %d" % (name, len(p.cells), len(anchors_found), top, p.size[1], p.clipped))
        if top >= p.size[1] or p.clipped:
            problems += 1
        if PLAN:
            plan(name, p, 5 if name != "gerbang_besar" else 13)
    for path, obj in JSONS + PACK_JSONS:
        write_json(path, obj)
    print("module D: %d templates, %d json files (%d in the pack sekte_gerbang), problems: %d"
          % (len(TEMPLATES), len(JSONS) + len(PACK_JSONS), len(PACK_JSONS), problems))
    validate_all()
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
