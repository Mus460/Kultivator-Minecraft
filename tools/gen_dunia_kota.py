"""Module K world generation (DESIGN 34.2, 34.5.1, 34.5.4): Kota Kultivator (overworld) and Kota Abadi (Alam Abadi).

Writes the NBT templates (data/kultivator/structure/kota/**) AND their JSON (structures, template pools, processor
lists, biome and structure tags, loot tables, and the structure sets in the built-in pack resourcepacks/dunia_kota), so
the two can never disagree. Run:  python tools/gen_dunia_kota.py [preview_dir]
Only world generation in NEW chunks ever places these templates (DESIGN 34.0 rule W1).

Layout of a town (jigsaw, every piece <= 48 x 48; buildings face south and the jigsaw turns them to face their street):
  gerbang (start: paifang gate, two watch towers, the town stele)
    -> jalan_utama_a (main street; west: Paviliun Pil, east: Paviliun Pusaka)
    -> jalan_utama_b (main street; west: the inn, east: the guard sect's office)
    -> alun (the square: well, old pine, notice board; north: Balai Lelang, east: Kedai Teh, west: the coffin shop over the
       Pasar Gelap; four side streets) -> jalan / jalan_silang / jembatan (houses, stalls, carts on both sides)
       -> tembok_ujung (wall sections with corner towers close every street)
Kota Abadi: abadi_gerbang (start, cloud gate) -> abadi_jembatan -> abadi_alun (the plaza; Istana Giok, Pasar Abadi,
Paviliun Dao, Kebun Persik, Menara Lonceng, Balai Lelang Abadi, houses) - every piece floats on its own tapering underside.

Anchors ("kota/<what>", DESIGN 34.4.1) carry "yaw0" (their authored yaw) so the module can turn point lists with the
piece, and "pts" (relative x, y, z triples) for light points.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from structgen import (PALETTES, Piece, out_path, json_path, write_json, single, pool, validate_all,  # noqa: E402
                       DATA, REPO)

K = "kultivator:"
PAL = dict(PALETTES["kota"])
PAL.update({
    "plaster": "minecraft:white_terracotta", "plaster_alt": "minecraft:calcite", "beam": "minecraft:stripped_dark_oak_log",
    "red": "minecraft:red_nether_bricks", "red_wall": "minecraft:red_nether_brick_wall", "tile": "minecraft:deepslate_tiles",
    "paving": "minecraft:polished_andesite", "paving_alt": "minecraft:andesite", "kerb": "minecraft:stone_bricks",
    "drain": "minecraft:stone_brick_slab", "rail": "minecraft:dark_oak_fence",
})
ABADI = dict(PALETTES["abadi"])
ABADI.update({
    "under": "minecraft:calcite", "under_alt": "minecraft:smooth_quartz", "under_tip": "minecraft:white_concrete",
    "rail": "minecraft:cherry_fence", "beam": "minecraft:stripped_cherry_log", "plaster": "minecraft:white_concrete",
    "roof": "minecraft:cherry_stairs", "roof_slab": "minecraft:cherry_slab", "roof_full": "minecraft:cherry_planks",
    "jade_tile": "minecraft:prismarine_bricks",
})

SAVED = []
LANTERN_PTS = {}


def F(v):
    return nbt.Float(v)


def anchor(p, x, y, z, kind, yaw=0.0, **data):
    """The kultivator:dunia_anchor entity (DESIGN 34.4.1) with its yaw (turned with the piece) and data."""
    d = {"yaw0": F(yaw)}
    d.update(data)
    p.entity(x, y, z, {"id": K + "dunia_anchor", "kind": kind, "data": d, "Rotation": [F(yaw), F(0.0)]})


def lit(p, x, y, z, chain=1, role="glow"):
    """A hanging lantern that also feeds the piece's glow-sprite anchor."""
    p.lantern(x, y, z, chain=chain, role=role)
    LANTERN_PTS.setdefault(id(p), []).append((x, y - 1 - chain, z))


def post(p, x, y, z, height=2, post_role="fence", role="glow"):
    p.standing_lantern(x, y, z, post=post_role, role=role, height=height)
    LANTERN_PTS.setdefault(id(p), []).append((x, y + height, z))


def finish_lights(p, ax, ay, az, kind="kota/lentera"):
    """One light anchor at (ax, ay, az) listing every lantern of the piece (at most 64)."""
    pts = LANTERN_PTS.pop(id(p), [])
    if not pts:
        return
    flat = []
    for x, y, z in pts[:64]:
        flat += [x - int(ax), y - int(ay), z - int(az)]
    anchor(p, ax + 0.5, ay, az + 0.5, kind, protect=0, pts=flat)


def save(p, name):
    path = out_path("kota", name)
    p.save(path)
    SAVED.append(name)
    return path


def conn(p, x, y, z, facing, name, target="minecraft:empty", pool_id="minecraft:empty", final="minecraft:air", prio=0):
    p.jigsaw(x, y, z, facing + "_up", K + name if ":" not in name else name, target if ":" in target else K + target,
             pool_id if ":" in pool_id else K + pool_id, final, placement_priority=prio)


# ======================================================================================================== details
def curl_roof_gable(p, x0, y, z0, x1, z1, along="x", overhang=1):
    top = p.roof_gable(x0, y, z0, x1, z1, along=along, overhang=overhang, eave_curl=True)
    return top


def bamboo_clump(p, x, z, y, height):
    for dy in range(height):
        p.put(x, y + dy, z, "minecraft:bamboo", {"age": "1", "leaves": "large" if dy >= height - 2 else "small"})


def pine(p, x, y, z, height=9):
    """An old pine: a crooked spruce trunk with flat, layered crowns."""
    for dy in range(height):
        dx = 1 if dy > height * 0.6 else 0
        p.put(x + dx, y + dy, z, "minecraft:spruce_log", {"axis": "y"})
    for layer, (dy, r) in enumerate([(height - 5, 3), (height - 3, 2), (height - 1, 2), (height, 1)]):
        cx = x + (1 if dy > height * 0.6 else 0)
        for ox in range(-r, r + 1):
            for oz in range(-r, r + 1):
                if abs(ox) + abs(oz) <= r + (1 if layer == 0 else 0) and p.inside(cx + ox, y + dy, z + oz) \
                        and p.get(cx + ox, y + dy, z + oz) in (None, "minecraft:air"):
                    p.put(cx + ox, y + dy, z + oz, "minecraft:spruce_leaves", {"persistent": "true", "distance": "1",
                                                                                "waterlogged": "false"})


def cherry(p, x, y, z, height=5, r=3):
    for dy in range(height):
        p.put(x, y + dy, z, "minecraft:cherry_log", {"axis": "y"})
    for ox in range(-r, r + 1):
        for oz in range(-r, r + 1):
            for dy in (height - 1, height, height + 1):
                d = math.hypot(ox, oz) + (dy - height) * 0.9
                if d <= r - 0.2 and p.inside(x + ox, y + dy, z + oz) and p.get(x + ox, y + dy, z + oz) in (None, "minecraft:air"):
                    p.put(x + ox, y + dy, z + oz, "minecraft:cherry_leaves", {"persistent": "true", "distance": "1",
                                                                              "waterlogged": "false"})
    for ox, oz in ((r, 0), (-r, 1), (1, -r), (0, r)):
        if p.inside(x + ox, y, z + oz) and p.get(x + ox, y, z + oz) is None:
            p.put(x + ox, y, z + oz, "minecraft:pink_petals", {"facing": "north", "flower_amount": "3"})


def bench(p, x, y, z, facing):
    p.stairs(x, y, z, "minecraft:spruce_stairs", facing)


def cushion(p, x, y, z, color="minecraft:red_carpet"):
    p.put(x, y, z, color)


def chest(p, x, y, z, loot):
    p.set(x, y, z, "minecraft:chest", None, {"id": "minecraft:chest", "LootTable": loot})


def lattice_window(p, x, y, z, facing):
    p.put(x, y, z, "minecraft:spruce_trapdoor", {"facing": facing, "half": "bottom", "open": "true", "powered": "false",
                                                 "waterlogged": "false"})


def plaque(p, x, y, z):
    """A gold-rimmed name board over a door."""
    p.put(x - 1, y, z, "minecraft:dark_oak_planks")
    p.put(x, y, z, "accent")
    p.put(x + 1, y, z, "minecraft:dark_oak_planks")


def paving(p, x0, z0, x1, z1, y=0):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            r = p.rng.random()
            role = "kerb" if edge else ("paving" if r < 0.7 else "paving_alt" if r < 0.9 else "minecraft:cracked_stone_bricks")
            p.put(x, y, z, role)


# ======================================================================================================== the gate (start)
def gerbang():
    p = Piece(23, 20, 13, seed=53001, palette=PAL)
    paving(p, 0, 0, 22, 12)
    p.path([(11, 2), (11, 10)], 0, width=5, role="paving", alt="kerb", alt_chance=0.15)
    # two watch towers (5 x 5, 3 storeys) with hip roofs and lantern balconies
    for tx in (1, 17):
        p.fill(tx, 1, 4, tx + 4, 1, 8, "kerb")
        p.walls(tx, 2, 4, tx + 4, 10, 8, role="plaster", pillar="red", windows=2, window_role="window")
        p.fill(tx, 6, 4, tx + 4, 6, 8, "beam", {"axis": "x"})
        p.fill(tx + 1, 6, 5, tx + 3, 6, 7, "minecraft:spruce_planks")
        p.doorway(tx, 2, 4, tx + 4, 8, "north", width=1, height=2)
        p.roof_hip(tx, 11, 4, tx + 4, 8, overhang=1, eave_curl=True, tiers=1)
        lit(p, tx + 2, 10, 6, chain=1)
        for cx, cz in ((tx - 1, 3), (tx + 5, 3)):
            if p.inside(cx, 6, cz):
                p.put(cx, 6, cz, "minecraft:dark_oak_fence")
    # the paifang: four red pillars, three bays, a tiled roof over each bay, the name board
    pillars = (6, 9, 13, 16)
    for x in pillars:
        p.pillar(x, 1, 9, 6, role="red", base="kerb", cap="beam")
        p.put(x, 1, 5, "kerb")
        p.put(x, 1, 7, "kerb")
    for x in range(5, 18):
        p.put(x, 10, 6, "beam", {"axis": "x"})
        p.put(x, 8, 6, "beam", {"axis": "x"})
    p.fill(10, 9, 6, 12, 9, 6, "accent")
    plaque(p, 11, 7, 6)
    curl_roof_gable(p, 9, 11, 5, 13, 7, along="x", overhang=1)
    curl_roof_gable(p, 5, 10, 5, 8, 7, along="x", overhang=0)
    curl_roof_gable(p, 14, 10, 5, 17, 7, along="x", overhang=0)
    for x in (6, 16):
        lit(p, x, 8, 5, chain=1)
        lit(p, x, 8, 7, chain=1)
    # stone lions (chiseled blocks on plinths) and the stele
    for x in (8, 14):
        p.put(x, 1, 11, "kerb")
        p.put(x, 2, 11, "minecraft:chiseled_stone_bricks")
        p.stairs(x, 3, 11, "minecraft:stone_brick_stairs", "south")
    p.put(4, 1, 11, "minecraft:polished_andesite")
    p.put(4, 2, 11, "minecraft:chiseled_stone_bricks")
    p.put(4, 3, 11, "minecraft:chiseled_stone_bricks")
    p.slab(4, 4, 11, "minecraft:stone_brick_slab")
    post(p, 2, 1, 1)
    post(p, 20, 1, 1)
    post(p, 2, 1, 12)
    post(p, 20, 1, 12)
    anchor(p, 4.5, 1.0, 12.5, "kota/situs", 0.0, situs="kota/kota", tier=1, protect=40)
    anchor(p, 11.5, 1.0, 9.5, "kota/penjaga", 0.0)
    anchor(p, 2.5, 1.0, 9.5, "kota/penjaga", 90.0)
    p.air(0, 1, 0, 22, 17, 3)
    p.air(5, 1, 9, 17, 17, 12)
    for x in range(5, 18):
        for y in range(1, 6):
            if x not in pillars:
                p.put(x, y, 6, "minecraft:air")
    p.weather(0.06)
    finish_lights(p, 11, 1, 3)
    conn(p, 11, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_utama_a", "minecraft:polished_andesite", prio=2)
    save(p, "gerbang")


# ======================================================================================================== streets
def street_base(p, sx, sz, lamps=True, drains=True):
    for z in range(sz):
        for x in range(sx):
            edge = x in (0, sx - 1)
            gutter = drains and x in (1, sx - 2)
            r = p.rng.random()
            if edge:
                p.put(x, 0, z, "kerb")
            elif gutter:
                p.put(x, 0, z, "minecraft:cobblestone" if r < 0.8 else "minecraft:mossy_cobblestone")
            else:
                p.put(x, 0, z, "paving" if r < 0.72 else "paving_alt" if r < 0.92 else "minecraft:cracked_stone_bricks")
        for x in range(sx):
            for y in range(1, 4):
                p.put(x, y, z, "minecraft:air")
    if lamps:
        for z in range(3, sz - 2, 6):
            post(p, 0, 1, z, height=2)
            post(p, sx - 1, 1, z, height=2)


def jalan_utama(name, west_pool, east_pool, next_pool, seed):
    p = Piece(9, 6, 22, seed=seed, palette=PAL)
    street_base(p, 9, 22)
    # banners of the guard sect between the lamps: red wool strips on dark posts
    for z in (9, 15):
        for x in (0, 8):
            p.put(x, 1, z, "minecraft:dark_oak_fence")
            p.put(x, 2, z, "minecraft:dark_oak_fence")
            p.put(x, 3, z, "minecraft:red_wool")
    finish_lights(p, 4, 1, 11)
    conn(p, 4, 0, 21, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 4, 0, 0, "north", "kota/keluar", "kota/masuk", next_pool, "minecraft:polished_andesite", prio=2)
    conn(p, 0, 0, 12, "west", "kota/sisi", "kota/pintu", west_pool, "minecraft:stone_bricks", prio=1)
    conn(p, 8, 0, 12, "east", "kota/sisi", "kota/pintu", east_pool, "minecraft:stone_bricks", prio=1)
    save(p, name)


def jalan(name, seed, decor):
    p = Piece(7, 6, 16, seed=seed, palette=PAL)
    street_base(p, 7, 16, lamps=False)
    post(p, 0, 1, 2, height=2)
    post(p, 6, 1, 13, height=2)
    if decor == "kios":
        # a fruit stall with a striped awning
        for x, z in ((1, 6), (1, 9)):
            p.put(x, 1, z, "minecraft:spruce_fence")
            p.put(x, 2, z, "minecraft:spruce_fence")
        p.fill(1, 3, 6, 2, 3, 9, "minecraft:red_wool")
        p.put(1, 1, 7, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(1, 1, 8, "minecraft:melon")
        p.put(1, 2, 8, "minecraft:pumpkin")
    elif decor == "gerobak":
        # a hand cart full of hay and crates
        p.put(5, 1, 7, "minecraft:hay_block", {"axis": "z"})
        p.put(5, 1, 8, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(5, 2, 8, "minecraft:hay_block", {"axis": "z"})
        p.put(4, 1, 6, "minecraft:spruce_trapdoor", {"facing": "north", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
        p.put(4, 1, 9, "minecraft:spruce_trapdoor", {"facing": "south", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    finish_lights(p, 3, 1, 8)
    conn(p, 3, 0, 15, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 3, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    conn(p, 0, 0, 8, "west", "kota/sisi", "kota/pintu", "kota/rumah", "minecraft:stone_bricks")
    conn(p, 6, 0, 8, "east", "kota/sisi", "kota/pintu", "kota/rumah", "minecraft:stone_bricks")
    save(p, name)


def jalan_silang():
    p = Piece(13, 7, 13, seed=53031, palette=PAL)
    for x in range(13):
        for z in range(13):
            p.put(x, 0, z, "paving" if p.rng.random() < 0.8 else "paving_alt")
            for y in range(1, 5):
                p.put(x, y, z, "minecraft:air")
    # a small shrine of the earth god in the middle
    p.put(6, 1, 6, "kerb")
    p.put(6, 2, 6, "minecraft:chiseled_stone_bricks")
    p.put(6, 3, 6, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    for x, z in ((5, 6), (7, 6), (6, 5), (6, 7)):
        p.slab(x, 1, z, "minecraft:stone_brick_slab")
    post(p, 0, 1, 0)
    post(p, 12, 1, 0)
    post(p, 0, 1, 12)
    post(p, 12, 1, 12)
    finish_lights(p, 6, 1, 4)
    conn(p, 6, 0, 12, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 6, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    conn(p, 0, 0, 6, "west", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    conn(p, 12, 0, 6, "east", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    save(p, "jalan_silang")


def jembatan():
    """A canal crossing: water on both sides of a humped stone bridge with red rails."""
    p = Piece(11, 6, 15, seed=53032, palette=PAL)
    for x in range(11):
        for z in range(15):
            p.put(x, 0, z, "kerb")
            for y in range(1, 5):
                p.put(x, y, z, "minecraft:air")
    for x in range(11):
        for z in range(5, 10):
            if x in (0, 1, 9, 10):
                p.put(x, 0, z, "minecraft:water", {"level": "0"})
            if x in (0, 10):
                p.put(x, 0, z, "kerb")
    for z in range(4, 11):
        hump = 1 if 6 <= z <= 8 else 0
        for x in range(3, 8):
            if hump:
                p.put(x, 1, z, "minecraft:stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
        for x in (2, 8):
            p.put(x, 1 + hump, z, "red_wall")
    for x in (2, 8):
        post(p, x, 1, 3, height=2)
        post(p, x, 1, 11, height=2)
    p.put(1, 0, 7, "minecraft:lily_pad") if False else None
    finish_lights(p, 5, 1, 7)
    conn(p, 5, 0, 14, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:polished_andesite")
    conn(p, 5, 0, 0, "north", "kota/keluar", "kota/masuk", "kota/jalan_ujung", "minecraft:polished_andesite")
    save(p, "jembatan")


def tembok_ujung(name, seed, gate):
    """A wall section closing a street: crenellated wall with a walkway, a corner tower, an arrow-slit gate or not."""
    p = Piece(15, 14, 7, seed=seed, palette=PAL)
    for x in range(15):
        for z in range(7):
            p.put(x, 0, z, "kerb")
    for x in range(15):
        for y in range(1, 7):
            for z in (1, 2, 3):
                p.put(x, y, z, "minecraft:stone_bricks" if p.rng.random() < 0.85 else "minecraft:mossy_stone_bricks")
        p.put(x, 7, 1, "minecraft:stone_brick_wall" if x % 2 == 0 else "minecraft:air")
        p.put(x, 7, 3, "minecraft:stone_brick_wall" if x % 2 == 0 else "minecraft:air")
    for x in range(15):
        for z in (4, 5, 6):
            for y in range(1, 5):
                p.put(x, y, z, "minecraft:air")
    # tower at the east end
    p.walls(11, 1, 0, 14, 9, 4, role="minecraft:stone_bricks", pillar="minecraft:chiseled_stone_bricks", windows=2,
            window_role="minecraft:iron_bars")
    p.roof_hip(11, 10, 0, 14, 4, overhang=0, eave_curl=True)
    lit(p, 12, 9, 2)
    if gate:
        for x in range(5, 8):
            for y in range(1, 4):
                for z in (1, 2, 3):
                    p.put(x, y, z, "minecraft:air")
        p.fill(5, 4, 1, 7, 4, 3, "beam", {"axis": "x"})
        for x in (5, 7):
            for z in (1, 3):
                p.put(x, 1, z, "minecraft:stone_bricks")
        p.put(6, 1, 1, "minecraft:dark_oak_fence_gate", {"facing": "north", "in_wall": "true", "open": "false", "powered": "false"})
    post(p, 3, 1, 5)
    post(p, 9, 1, 5)
    finish_lights(p, 6, 1, 5)
    p.weather(0.1)
    conn(p, 6, 0, 6, "south", "kota/masuk", "kota/keluar", "minecraft:empty", "minecraft:stone_bricks")
    save(p, name)


# ======================================================================================================== the square
def alun():
    p = Piece(33, 16, 33, seed=53040, palette=PAL)
    for x in range(33):
        for z in range(33):
            ring = max(abs(x - 16), abs(z - 16))
            role = "kerb" if ring in (16, 9) else ("minecraft:polished_andesite" if (x + z) % 2 == 0 else "minecraft:andesite")
            if ring <= 3:
                role = "minecraft:stone_bricks"
            p.put(x, 0, z, role)
            for y in range(1, 6):
                p.put(x, y, z, "minecraft:air")
    # the well under a little roof
    p.ring(16, 1, 16, 2, "minecraft:stone_bricks")
    for x in range(15, 18):
        for z in range(15, 18):
            p.put(x, 0, z, "minecraft:water", {"level": "0"})
            p.put(x, -0 + 1, z, "minecraft:air") if (x, z) != (16, 16) else None
    for x, z in ((14, 14), (18, 14), (14, 18), (18, 18)):
        p.pillar(x, 1, 4, z, role="red", base="kerb", cap="beam")
    curl_roof_gable(p, 14, 5, 14, 18, 18, along="x", overhang=1)
    lit(p, 16, 5, 16, chain=1)
    # the old pine and stone benches around it
    pine(p, 6, 1, 6, height=10)
    for x, z, f in ((4, 9, "south"), (8, 9, "south"), (9, 4, "east"), (9, 8, "east")):
        bench(p, x, 1, z, f)
    # the notice board (two posts, a roofed board) and its keeper
    for x in (24, 28):
        p.put(x, 1, 7, "minecraft:dark_oak_log", {"axis": "y"})
        p.put(x, 2, 7, "minecraft:dark_oak_log", {"axis": "y"})
        p.put(x, 3, 7, "minecraft:dark_oak_log", {"axis": "y"})
    for x in range(25, 28):
        p.put(x, 2, 7, "minecraft:birch_planks")
        p.put(x, 3, 7, "minecraft:birch_planks")
    for x in range(23, 30):
        p.stairs(x, 4, 7, "roof", "north")
    anchor(p, 26.5, 1.0, 9.5, "kota/papan", 180.0)
    # flower beds and stalls
    for x, z in ((24, 24), (8, 24)):
        for ox in range(-1, 2):
            for oz in range(-1, 2):
                p.put(x + ox, 0, z + oz, "minecraft:grass_block", {"snowy": "false"})
                p.put(x + ox, 1, z + oz, "minecraft:rose_bush", {"half": "lower"}) if (ox, oz) == (0, 0) else \
                    p.put(x + ox, 1, z + oz, "minecraft:short_grass")
        p.put(x, 2, z, "minecraft:rose_bush", {"half": "upper"})
    for x in (3, 29):
        for z in (3, 29):
            post(p, x, 1, z, height=3)
    for x in (12, 20):
        post(p, x, 1, 1, height=2)
        post(p, x, 1, 31, height=2)
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
def front_door(p, sx, sz, door_x, y=0, width=3):
    """The door side is south (z = sz - 1); a stoop and the building's connector."""
    conn(p, door_x, y, sz - 1, "south", "kota/pintu", "minecraft:empty", "minecraft:empty", "minecraft:stone_bricks")


def balai_lelang():
    sx, sy, sz = 23, 28, 17
    p = Piece(sx, sy, sz, seed=53050, palette=PAL)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="minecraft:dark_oak_planks", alt="minecraft:spruce_planks", alt_chance=0.25)
    p.walls(1, 2, 1, sx - 2, 7, sz - 3, role="plaster", pillar="red", windows=4, window_role="window", door=("south", 3))
    # the veranda along the front: red columns under the lower eave
    for x in range(1, sx - 1, 3):
        p.pillar(x, 2, 7, sz - 2, role="red", base="kerb", cap="beam")
    p.fill(1, 1, sz - 2, sx - 2, 1, sz - 2, "kerb")
    # the gallery (second floor ring) with a lattice rail
    p.fill(2, 7, 2, sx - 3, 7, sz - 4, "minecraft:dark_oak_planks")
    p.air(6, 7, 4, sx - 7, 7, sz - 6)
    for x in range(6, sx - 6):
        p.put(x, 8, 4, "rail")
        p.put(x, 8, sz - 6, "rail")
    for z in range(4, sz - 5):
        p.put(6, 8, z, "rail")
        p.put(sx - 7, 8, z, "rail")
    p.walls(1, 8, 1, sx - 2, 12, sz - 3, role="plaster", pillar="red", windows=3, window_role="window", interior_air=True)
    p.fill(2, 7, 2, sx - 3, 7, 3, "minecraft:dark_oak_planks")
    # a staircase to the gallery
    p.staircase(2, 2, 10, "north", 5, width=1, role="minecraft:dark_oak_stairs", support="minecraft:dark_oak_planks")
    # the stage at the back: raised, red carpet, gold edge, two pillars, a lacquer screen
    p.fill(7, 2, 2, sx - 8, 2, 5, "minecraft:polished_andesite")
    for x in range(7, sx - 7):
        p.put(x, 3, 5, "minecraft:red_carpet")
        p.put(x, 2, 6, "minecraft:polished_andesite_slab", {"type": "bottom", "waterlogged": "false"})
    p.fill(8, 3, 2, sx - 9, 6, 2, "minecraft:red_nether_bricks")
    p.fill(10, 4, 2, sx - 11, 5, 2, "accent")
    for x in (7, sx - 8):
        p.pillar(x, 3, 6, 3, role="red", base="accent", cap="accent")
    p.put(11, 3, 4, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    # bidders' benches
    for z in (8, 10, 12):
        for x in range(6, sx - 6):
            if x != 11:
                bench(p, x, 2, z, "north")
    # lanterns inside and on the eaves
    for x in (5, 11, 17):
        lit(p, x, 7, 8, chain=1)
        lit(p, x, 12, 9, chain=1)
    for x in (1, sx - 2):
        lit(p, x, 7, sz - 2, chain=1)
    # double-eave hip roof
    top = p.roof_hip(1, 13, 1, sx - 2, sz - 3, overhang=1, eave_curl=True, tiers=2, tier_gap=2, tier_band=2, band_role="plaster")
    p.put(11, min(sy - 1, top), 7, "accent")
    plaque(p, 11, 6, sz - 2)
    anchor(p, 11.5, 3.0, 3.5, "kota/lelang", 0.0)
    anchor(p, 11.5, 7.0, 3.5, "kota/panggung", 0.0, protect=0, pts=[-3, 0, 0, 0, 0, 0, 3, 0, 0])
    anchor(p, 4.5, 2.0, 12.5, "kota/warga", 180.0)
    finish_lights(p, 11, 2, 10)
    front_door(p, sx, sz, 11)
    save(p, "balai_lelang")


def kedai_teh():
    sx, sy, sz = 23, 17, 23
    p = Piece(sx, sy, sz, seed=53051, palette=PAL)
    # the pond with lilies and stone banks
    for x in range(sx):
        for z in range(sz):
            bank = x in (0, sx - 1) or z in (0, sz - 1)
            p.put(x, 0, z, "kerb" if bank else "minecraft:water", None if bank else {"level": "0"})
            for y in range(1, 4):
                p.put(x, y, z, "minecraft:air")
    for _ in range(14):
        x = p.rng.randrange(2, sx - 2)
        z = p.rng.randrange(2, sz - 2)
        if abs(x - 11) > 5 or abs(z - 10) > 5:
            p.put(x, 1, z, "minecraft:lily_pad")
    # the zig-zag bridge from the door (south) to the pavilion
    zig = [(11, 22), (11, 19), (8, 19), (8, 16), (11, 16)]
    for (ax, az), (bx, bz) in zip(zig, zig[1:]):
        for t in range(max(abs(bx - ax), abs(bz - az)) + 1):
            x = ax + (0 if bx == ax else (t if bx > ax else -t))
            z = az + (0 if bz == az else (t if bz > az else -t))
            p.put(x, 0, z, "minecraft:spruce_planks")
            p.slab(x, 1, z, "minecraft:spruce_slab") if False else None
            for ox in (-1, 1):
                if p.get(x + ox, 0, z) == "minecraft:water":
                    p.put(x + ox, 1, z, "minecraft:bamboo_fence") if (t % 2 == 0) else None
    # the pavilion on its stone island
    p.fill(6, 0, 4, 16, 0, 15, "kerb")
    p.floor(7, 1, 5, 15, 14, role="minecraft:bamboo_mosaic", alt="minecraft:bamboo_planks", alt_chance=0.3)
    for x, z in ((7, 5), (15, 5), (7, 14), (15, 14), (11, 5), (11, 14), (7, 9), (15, 9)):
        p.pillar(x, 2, 6, z, role="red", base="kerb", cap="beam")
    for x in range(7, 16):
        p.put(x, 2, 5, "minecraft:bamboo_fence") if x not in (7, 11, 15) else None
    for z in range(6, 14):
        for x in (7, 15):
            if z not in (9,):
                lattice_window(p, x, 4, z, "west" if x == 7 else "east")
    curl_roof_gable(p, 7, 7, 5, 15, 14, along="z", overhang=2)
    # low tables and cushions
    for tx, tz in ((9, 8), (13, 8), (9, 12), (13, 12)):
        p.put(tx, 2, tz, "minecraft:spruce_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
        p.put(tx, 3, tz, "minecraft:flower_pot")
        for ox, oz in ((-1, 0), (1, 0)):
            cushion(p, tx + ox, 2, tz + oz)
    # the tea master's counter and stove
    p.fill(10, 2, 6, 12, 2, 6, "minecraft:spruce_planks")
    p.put(13, 2, 6, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    p.put(10, 3, 6, "minecraft:decorated_pot")
    # bamboo groves in the corners
    for bx, bz in ((2, 2), (3, 3), (2, 4), (20, 2), (19, 3), (20, 20), (19, 19), (2, 20), (3, 19)):
        p.put(bx, 0, bz, "minecraft:podzol", {"snowy": "false"})
        bamboo_clump(p, bx, bz, 1, 6 + p.rng.randrange(4))
    for x in (7, 15):
        lit(p, x, 7, 4, chain=1)
        lit(p, x, 7, 15, chain=1)
    lit(p, 11, 7, 10, chain=2)
    anchor(p, 11.5, 2.0, 7.5, "kota/teh", 180.0)
    anchor(p, 8.5, 2.0, 8.5, "kota/kursi_pengembara", 90.0)
    anchor(p, 14.5, 2.0, 12.5, "kota/kursi_pengembara", 270.0)
    anchor(p, 11.5, 0.0, 11.5, "kota/kolam", 0.0, protect=0, pts=[-7, 0, -7, 7, 0, -7, -7, 0, 7, 7, 0, 7, 0, 0, 9, -9, 0, 0])
    finish_lights(p, 11, 2, 10)
    front_door(p, sx, sz, 11)
    save(p, "kedai_teh")


def toko_peti():
    """The coffin shop and, below it, the Pasar Gelap (floor of the shop at y = 9, the market hall at y = 1..7)."""
    sx, sy, sz = 15, 27, 17
    F0 = 9
    p = Piece(sx, sy, sz, seed=53052, palette=PAL)
    # the underground hall: blackstone, stalls, braziers
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
        p.fill(sxi, 4, sz0 - 1, sxi + 2, 4, sz0 - 1, "minecraft:purple_wool")
        p.put(sxi + 1, 2, sz0 - 1, "minecraft:barrel", {"facing": "up", "open": "true"})
    chest(p, 6, 2, 13, K + "chests/dunia/kota/pasar_gelap")
    p.put(7, 2, 13, "minecraft:smithing_table")
    for x in (4, 10):
        lit(p, x, F0 - 1, 7, chain=1, role="minecraft:soul_lantern")
    anchor(p, 7.5, 2.0, 11.5, "kota/gelap", 0.0)
    # the stair from the back room down into the hall (west side, along z)
    for i in range(F0 - 1):
        z = 13 - i
        if z < 2:
            break
        p.stairs(sx - 3, F0 - 1 - i, z, "minecraft:polished_blackstone_brick_stairs", "south")
        for y in range(F0 - i, F0 + 2):
            p.put(sx - 3, y, z, "minecraft:air")
    # the shop above
    p.fill(0, F0 - 1, 0, sx - 1, F0 - 1, sz - 1, "kerb")
    p.floor(1, F0, 1, sx - 2, sz - 2, role="minecraft:spruce_planks", alt="minecraft:dark_oak_planks", alt_chance=0.3)
    p.walls(1, F0 + 1, 1, sx - 2, F0 + 5, sz - 2, role="minecraft:dark_oak_planks", pillar="beam", windows=4, window_role="window",
            door=("south", 1))
    # the stair opening in the back room floor
    for z in range(9, 14):
        p.put(sx - 3, F0, z, "minecraft:air")
    # coffins on trestles: dark oak trapdoors over planks
    for cz in (4, 7):
        for x in range(3, 7):
            p.put(x, F0 + 1, cz, "minecraft:spruce_planks")
            p.put(x, F0 + 2, cz, "minecraft:dark_oak_trapdoor", {"facing": "north", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})
    p.put(3, F0 + 1, 11, "minecraft:white_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    # the back room wall with the hidden door gap (the door entity fills it)
    for x in range(2, sx - 2):
        p.put(x, F0 + 1, 9, "minecraft:dark_oak_planks")
        p.put(x, F0 + 2, 9, "minecraft:dark_oak_planks")
        p.put(x, F0 + 3, 9, "minecraft:dark_oak_planks")
    p.put(sx - 4, F0 + 1, 9, "minecraft:air")
    p.put(sx - 4, F0 + 2, 9, "minecraft:air")
    p.put(sx - 3, F0 + 1, 9, "minecraft:air")
    p.put(sx - 3, F0 + 2, 9, "minecraft:air")
    anchor(p, sx - 3.5, F0 + 1.0, 9.5, "kota/gerbang_gelap", 0.0, protect=12)
    curl_roof_gable(p, 1, F0 + 6, 1, sx - 2, sz - 2, along="x", overhang=1)
    plaque(p, 7, F0 + 4, sz - 2)
    lit(p, 7, F0 + 5, 5, chain=1)
    finish_lights(p, 7, F0 + 1, 13)
    conn(p, 7, F0, sz - 1, "south", "kota/pintu", "minecraft:empty", "minecraft:empty", "minecraft:stone_bricks")
    save(p, "toko_peti")


def paviliun(name, seed, kind):
    sx, sy, sz = 11, 15, 11
    p = Piece(sx, sy, sz, seed=seed, palette=PAL)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="minecraft:spruce_planks", alt="minecraft:dark_oak_planks", alt_chance=0.25)
    p.walls(1, 2, 1, sx - 2, 6, sz - 3, role="plaster", pillar="red", windows=3, window_role="window", door=("south", 3))
    for x in (1, 5, 9):
        p.pillar(x, 2, 6, sz - 2, role="red", base="kerb", cap="beam")
    p.fill(1, 1, sz - 2, sx - 2, 1, sz - 2, "kerb")
    p.fill(3, 2, 5, 7, 2, 5, "minecraft:spruce_slab", {"type": "top", "waterlogged": "false"})
    if kind == "pil":
        for x in range(2, 9):
            p.put(x, 2, 2, "minecraft:bookshelf")
            p.put(x, 3, 2, "minecraft:flower_pot" if x % 2 else "minecraft:potted_bamboo")
        p.put(2, 2, 4, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
        p.put(8, 2, 4, "minecraft:cauldron")
        p.put(5, 3, 5, "minecraft:decorated_pot")
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
        role = "kota/pusaka"
    top = p.roof_hip(1, 7, 1, sx - 2, sz - 3, overhang=1, eave_curl=True)
    plaque(p, 5, 5, sz - 2)
    lit(p, 5, 6, 4, chain=1)
    lit(p, 1, 6, sz - 2, chain=0) if False else None
    post(p, 0, 1, sz - 1)
    post(p, sx - 1, 1, sz - 1)
    anchor(p, 5.5, 2.0, 4.0, role, 0.0)
    finish_lights(p, 5, 2, 7)
    front_door(p, sx, sz, 5)
    save(p, name)


def penginapan():
    sx, sy, sz = 15, 21, 13
    p = Piece(sx, sy, sz, seed=53055, palette=PAL)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="minecraft:spruce_planks")
    p.walls(1, 2, 1, sx - 2, 5, sz - 2, role="plaster", pillar="beam", windows=3, door=("south", 3))
    p.fill(1, 6, 1, sx - 2, 6, sz - 2, "minecraft:spruce_planks")
    p.walls(1, 7, 1, sx - 2, 10, sz - 2, role="plaster", pillar="beam", windows=3)
    p.staircase(2, 2, 9, "north", 4, role="minecraft:spruce_stairs", support="minecraft:spruce_planks")
    for y in range(2, 7):
        for z in range(5, 9):
            p.put(2, y, z, "minecraft:air") if y > 5 else None
    # beds upstairs (heads to the north)
    for bx in (4, 7, 10, 12):
        p.put(bx, 7, 3, "minecraft:red_bed", {"facing": "north", "part": "head", "occupied": "false"})
        p.put(bx, 7, 4, "minecraft:red_bed", {"facing": "north", "part": "foot", "occupied": "false"})
    # the common room: tables and a hearth
    for tx in (5, 9):
        p.put(tx, 2, 6, "minecraft:spruce_fence")
        p.slab(tx, 3, 6, "minecraft:spruce_slab") if False else p.put(tx, 3, 6, "minecraft:spruce_pressure_plate", {"powered": "false"})
        bench(p, tx - 1, 2, 6, "east")
        bench(p, tx + 1, 2, 6, "west")
    p.put(12, 2, 2, "minecraft:smoker", {"facing": "south", "lit": "true"})
    p.put(11, 2, 2, "minecraft:barrel", {"facing": "south", "open": "false"})
    curl_roof_gable(p, 1, 11, 1, sx - 2, sz - 2, along="x", overhang=1)
    plaque(p, 7, 5, sz - 2)
    for x in (4, 10):
        lit(p, x, 5, 4, chain=1)
    anchor(p, 11.5, 2.0, 4.5, "kota/warga", 180.0)
    finish_lights(p, 7, 2, 9)
    front_door(p, sx, sz, 7)
    save(p, "penginapan")


def kantor_sekte():
    sx, sy, sz = 15, 24, 15
    p = Piece(sx, sy, sz, seed=53056, palette=PAL)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.fill(0, 1, 0, sx - 1, 1, sz - 1, "kerb")
    p.floor(2, 2, 2, sx - 3, sz - 3, role="minecraft:polished_andesite", alt="minecraft:stone_bricks", alt_chance=0.3)
    p.walls(2, 3, 2, sx - 3, 8, sz - 3, role="plaster", pillar="red", windows=4, door=("south", 3))
    p.fill(5, 3, 3, 9, 3, 4, "minecraft:polished_andesite")
    p.fill(6, 4, 3, 8, 4, 3, "accent")
    for x in (5, 9):
        p.put(x, 4, 3, "minecraft:white_banner")
    p.put(7, 5, 3, "minecraft:white_banner")
    for z in (6, 9):
        for x in (4, 10):
            p.put(x, 3, z, "minecraft:armor_stand") if False else p.put(x, 3, z, "minecraft:chiseled_stone_bricks")
    p.staircase(6, 1, sz - 1, "north", 1, width=3, role="minecraft:stone_brick_stairs", support=None)
    top = p.roof_hip(1, 9, 1, sx - 2, sz - 2, overhang=1, eave_curl=True, tiers=2, tier_gap=2, tier_band=2, band_role="plaster")
    plaque(p, 7, 7, sz - 3)
    for x in (4, 10):
        lit(p, x, 8, 8, chain=1)
    post(p, 1, 2, sz - 2)
    post(p, sx - 2, 2, sz - 2)
    anchor(p, 7.5, 4.0, 5.5, "kota/penjaga", 0.0)
    anchor(p, 4.5, 3.0, 10.5, "kota/penjaga", 90.0)
    finish_lights(p, 7, 3, 9)
    front_door(p, sx, sz, 7, y=1)
    save(p, "kantor_sekte")


# ======================================================================================================== houses and decorations
def rumah(name, sx, sz, h, seed, variant):
    p = Piece(sx, h + 10, sz, seed=seed, palette=PAL)
    p.foundation(0, 0, sx - 1, sz - 1, depth=1, role="kerb")
    p.floor(1, 1, 1, sx - 2, sz - 2, role="minecraft:spruce_planks", alt="minecraft:oak_planks", alt_chance=0.2)
    wall = ["plaster", "minecraft:mud_bricks", "minecraft:spruce_planks"][variant % 3]
    p.walls(1, 2, 1, sx - 2, 1 + h, sz - 2, role=wall, pillar="beam", windows=3, door=("south", 1))
    # furniture
    p.put(2, 2, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    if sx >= 9:
        p.put(sx - 3, 2, 2, "minecraft:red_bed", {"facing": "north", "part": "head", "occupied": "false"})
        p.put(sx - 3, 2, 3, "minecraft:red_bed", {"facing": "north", "part": "foot", "occupied": "false"})
        p.put(2, 2, 3, "minecraft:crafting_table")
    else:
        p.put(sx - 3, 2, 2, "minecraft:composter", {"level": "0"})
    if variant == 1:
        p.put(sx // 2, 2, 2, "minecraft:loom", {"facing": "south"})
    elif variant == 2:
        p.put(sx // 2, 2, 2, "minecraft:cartography_table")
    # a small garden in front
    for x in range(1, sx - 1):
        if x != sx // 2:
            p.put(x, 1, sz - 1, "minecraft:grass_block", {"snowy": "false"})
            p.put(x, 2, sz - 1, "minecraft:fern" if (x + seed) % 3 else "minecraft:short_grass")
    top = 2 + h
    if variant == 0:
        curl_roof_gable(p, 1, top, 1, sx - 2, sz - 2, along="x", overhang=1)
    else:
        p.roof_hip(1, top, 1, sx - 2, sz - 2, overhang=1, eave_curl=True)
    lit(p, sx // 2, 1 + h, sz // 2, chain=1)
    post(p, 0, 1, sz - 1, height=1)
    anchor(p, sx / 2.0, 2.0, sz / 2.0, "kota/warga", 180.0)
    finish_lights(p, sx // 2, 2, sz // 2)
    front_door(p, sx, sz, sx // 2, y=1)
    save(p, name)


def hias(name, seed, kind):
    p = Piece(7, 7, 7, seed=seed, palette=PAL)
    for x in range(7):
        for z in range(7):
            p.put(x, 0, z, "paving" if p.rng.random() < 0.7 else "paving_alt")
            for y in range(1, 5):
                p.put(x, y, z, "minecraft:air")
    if kind == "kios":
        for x in (1, 5):
            for z in (1, 4):
                p.put(x, 1, z, "minecraft:spruce_fence")
                p.put(x, 2, z, "minecraft:spruce_fence")
        p.fill(1, 3, 1, 5, 3, 4, "minecraft:yellow_wool")
        p.fill(2, 1, 2, 4, 1, 2, "minecraft:spruce_slab", {"type": "top", "waterlogged": "false"})
        p.put(2, 2, 2, "minecraft:melon")
        p.put(4, 2, 2, "minecraft:decorated_pot")
        anchor(p, 3.5, 1.0, 3.5, "kota/warga", 180.0)
    elif kind == "gerobak":
        p.put(2, 1, 3, "minecraft:hay_block", {"axis": "x"})
        p.put(3, 1, 3, "minecraft:hay_block", {"axis": "x"})
        p.put(4, 1, 3, "minecraft:barrel", {"facing": "up", "open": "false"})
        p.put(3, 2, 3, "minecraft:hay_block", {"axis": "x"})
        p.put(1, 1, 3, "minecraft:spruce_fence")
    else:
        for x in (1, 3, 5):
            post(p, x, 1, 3, height=3)
        for x in range(1, 6):
            p.put(x, 4, 3, "minecraft:dark_oak_fence") if x not in (1, 3, 5) else None
    finish_lights(p, 3, 1, 3)
    front_door(p, 7, 7, 3)
    save(p, name)


# ======================================================================================================== Kota Abadi
DECK = 8


def underside(p, x0, z0, x1, z1, depth=DECK):
    """The floating district's stone underside: layers shrinking toward a few hanging tips."""
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


def abadi_deck(p, x0, z0, x1, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            p.put(x, DECK, z, "minecraft:quartz_bricks" if edge else ("minecraft:smooth_quartz" if (x + z) % 2 else "minecraft:calcite"))
            for y in range(DECK + 1, min(p.size[1], DECK + 5)):
                p.put(x, y, z, "minecraft:air")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if x % 2 == 0:
                p.put(x, DECK + 1, z, "rail")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if z % 2 == 0:
                p.put(x, DECK + 1, z, "rail")


def clouds(p, x0, z0, x1, z1, y, count=8):
    pts = []
    for i in range(count):
        a = 2.0 * math.pi * i / count
        cx = (x0 + x1) / 2.0 + math.cos(a) * (x1 - x0) / 2.0
        cz = (z0 + z1) / 2.0 + math.sin(a) * (z1 - z0) / 2.0
        pts += [int(cx), y, int(cz)]
    return pts


def abadi_gerbang():
    sx, sy, sz = 23, 24, 15
    p = Piece(sx, sy, sz, seed=66001, palette=ABADI)
    underside(p, 0, 0, sx - 1, sz - 1)
    abadi_deck(p, 0, 0, sx - 1, sz - 1)
    # the cloud gate: white jade pillars, gold beams, a three-tier roof
    for x in (6, 16):
        p.pillar(x, DECK + 1, DECK + 9, 7, role="minecraft:quartz_pillar", base="trim", cap="trim")
    for x in range(5, 18):
        p.put(x, DECK + 10, 7, "trim")
    p.roof_hip(5, DECK + 11, 6, 17, 8, overhang=1, eave_curl=True, tiers=2, tier_gap=1, tier_band=1, band_role="minecraft:quartz_bricks")
    for x in range(7, 16):
        for y in range(DECK + 1, DECK + 9):
            p.put(x, y, 7, "minecraft:air")
    for x in (3, 19):
        post(p, x, DECK + 1, 2, height=3, post_role="rail")
        post(p, x, DECK + 1, 12, height=3, post_role="rail")
    anchor(p, 11.5, DECK + 1.0, 11.5, "kota/abadi_gerbang", 0.0, situs="kota/kota_abadi", tier=8, protect=72)
    anchor(p, 4.5, DECK + 1.0, 9.5, "kota/abadi_warga", 90.0)
    anchor(p, 11.5, float(DECK - 1), 7.5, "kota/abadi_awan", 0.0, protect=0, pts=[p_ - c for p_, c in zip(clouds(p, 0, 0, sx - 1, sz - 1, DECK - 1),
                                                                                                         [11, DECK - 1, 7] * 8)])
    finish_lights(p, 11, DECK + 1, 4)
    conn(p, 11, DECK, 0, "north", "kota/abadi_keluar", "kota/abadi_masuk", "kota/abadi_jembatan", "minecraft:quartz_bricks", prio=2)
    save(p, "abadi_gerbang")


def abadi_jembatan(name, seed, length, next_pool):
    sx, sz = 7, length
    p = Piece(sx, DECK + 6, sz, seed=seed, palette=ABADI)
    for z in range(sz):
        for x in range(1, sx - 1):
            p.put(x, DECK, z, "jade_tile" if x == 3 else "minecraft:quartz_bricks")
            p.put(x, DECK - 1, z, "under")
            if 2 <= x <= 4:
                p.put(x, DECK - 2, z, "under_alt")
            for y in range(DECK + 1, DECK + 5):
                p.put(x, y, z, "minecraft:air")
        p.put(1, DECK + 1, z, "rail")
        p.put(sx - 2, DECK + 1, z, "rail")
    for z in range(2, sz - 1, 5):
        post(p, 1, DECK + 1, z, height=2, post_role="rail")
        post(p, sx - 2, DECK + 1, z, height=2, post_role="rail")
    finish_lights(p, 3, DECK + 1, sz // 2)
    conn(p, 3, DECK, sz - 1, "south", "kota/abadi_masuk", "kota/abadi_keluar", "minecraft:empty", "minecraft:quartz_bricks")
    conn(p, 3, DECK, 0, "north", "kota/abadi_keluar", "kota/abadi_masuk", next_pool, "minecraft:quartz_bricks", prio=1)
    save(p, name)


def abadi_alun():
    s = 33
    p = Piece(s, DECK + 14, s, seed=66010, palette=ABADI)
    underside(p, 0, 0, s - 1, s - 1)
    abadi_deck(p, 0, 0, s - 1, s - 1)
    # a jade mosaic of the eight trigrams around a lotus fountain
    p.ring(16, DECK, 16, 6, "jade_tile", thickness=1)
    p.ring(16, DECK, 16, 9, "minecraft:gold_block", thickness=1)
    p.disc(16, DECK, 16, 2, "minecraft:water")
    p.ring(16, DECK + 1, 16, 3, "minecraft:quartz_slab")
    p.put(16, DECK, 16, "pearlescent" if False else "minecraft:pearlescent_froglight", {"axis": "y"})
    for a in range(8):
        ang = a * math.pi / 4.0
        x = int(round(16 + math.cos(ang) * 12))
        z = int(round(16 + math.sin(ang) * 12))
        p.put(x, DECK + 1, z, "minecraft:quartz_pillar", {"axis": "y"})
        p.put(x, DECK + 2, z, "minecraft:quartz_pillar", {"axis": "y"})
        post(p, x, DECK + 3, z, height=0, post_role="rail")
    for x, z in ((5, 5), (27, 5), (5, 27), (27, 27)):
        cherry(p, x, DECK + 1, z, height=5, r=3)
    anchor(p, 16.5, DECK + 1.0, 21.5, "kota/abadi_warga", 180.0)
    anchor(p, 12.5, DECK + 1.0, 10.5, "kota/abadi_warga", 90.0)
    anchor(p, 16.5, float(DECK - 1), 16.5, "kota/abadi_awan", 0.0, protect=0,
           pts=[v - c for v, c in zip(clouds(p, 0, 0, s - 1, s - 1, DECK - 2, count=12), [16, DECK - 1, 16] * 12)])
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
    p = Piece(sx, DECK + extra_h, sz, seed=seed, palette=ABADI)
    underside(p, 0, 0, sx - 1, sz - 1)
    abadi_deck(p, 0, 0, sx - 1, sz - 1)
    build(p, sx, sz)
    conn(p, sx // 2, door_y, sz - 1, "south", "kota/abadi_pintu", "minecraft:empty", "minecraft:empty", "minecraft:quartz_bricks")
    save(p, name)


def build_istana(p, sx, sz):
    p.fill(3, DECK + 1, 3, sx - 4, DECK + 1, sz - 4, "minecraft:quartz_bricks")
    p.walls(4, DECK + 2, 3, sx - 5, DECK + 8, sz - 5, role="plaster", pillar="minecraft:quartz_pillar", windows=4,
            window_role="window", door=("south", 3))
    p.staircase(sx // 2 - 2, DECK + 1, sz - 3, "north", 1, width=5, role="minecraft:quartz_stairs", support=None)
    p.fill(sx // 2 - 3, DECK + 2, 4, sx // 2 + 3, DECK + 2, 6, "minecraft:gold_block")
    p.put(sx // 2, DECK + 3, 5, "minecraft:emerald_block")
    for x in range(6, sx - 6, 3):
        for z in (8, sz - 8):
            p.pillar(x, DECK + 2, DECK + 7, z, role="minecraft:quartz_pillar", base="trim", cap="trim")
    p.roof_hip(3, DECK + 9, 2, sx - 4, sz - 4, overhang=1, eave_curl=True, tiers=3, tier_gap=2, tier_band=2, band_role="plaster")
    for x in (7, sx // 2, sx - 8):
        lit(p, x, DECK + 8, sz // 2, chain=1)
    anchor(p, sx / 2.0, DECK + 3.0, 7.5, "kota/abadi_utusan", 0.0)
    anchor(p, 6.5, DECK + 2.0, sz - 7.5, "kota/abadi_warga", 90.0)
    finish_lights(p, sx // 2, DECK + 2, sz // 2)


def build_pasar(p, sx, sz):
    for i, (x0, z0) in enumerate(((2, 2), (12, 2), (2, 11), (12, 11))):
        p.fill(x0, DECK + 1, z0, x0 + 6, DECK + 1, z0 + 2, "minecraft:cherry_slab", {"type": "top", "waterlogged": "false"})
        for x, z in ((x0, z0), (x0 + 6, z0), (x0, z0 + 3), (x0 + 6, z0 + 3)):
            for y in range(DECK + 1, DECK + 4):
                p.put(x, y, z, "rail")
        for x in range(x0, x0 + 7):
            for z in range(z0, z0 + 4):
                p.put(x, DECK + 4, z, ["minecraft:pink_wool", "minecraft:white_wool"][(x + z) % 2])
        p.put(x0 + 3, DECK + 2, z0 + 1, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
        lit(p, x0 + 3, DECK + 4, z0 + 2, chain=0)
    anchor(p, sx / 2.0, DECK + 1.0, sz / 2.0, "kota/abadi_pasar", 180.0)
    anchor(p, 4.5, DECK + 1.0, sz - 3.5, "kota/abadi_warga", 0.0)
    finish_lights(p, sx // 2, DECK + 1, 3)


def build_dao(p, sx, sz):
    p.disc(sx // 2, DECK, sz // 2, 7, "jade_tile")
    p.ring(sx // 2, DECK, sz // 2, 7, "trim")
    for a in range(8):
        ang = a * math.pi / 4.0
        x = int(round(sx // 2 + math.cos(ang) * 7))
        z = int(round(sz // 2 + math.sin(ang) * 7))
        p.pillar(x, DECK + 1, DECK + 6, z, role="minecraft:quartz_pillar", base="trim", cap="trim")
    for a in range(12):
        ang = a * math.pi / 6.0
        x = int(round(sx // 2 + math.cos(ang) * 4))
        z = int(round(sz // 2 + math.sin(ang) * 4))
        cushion(p, x, DECK + 1, z, "minecraft:white_carpet")
    p.put(sx // 2, DECK + 1, sz // 2 - 2, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    p.roof_hip(sx // 2 - 8, DECK + 7, sz // 2 - 8, sx // 2 + 8, sz // 2 + 8, overhang=0, eave_curl=True, tiers=2, tier_gap=2, tier_band=1,
               band_role="plaster")
    lit(p, sx // 2, DECK + 7, sz // 2, chain=2)
    anchor(p, sx / 2.0, DECK + 1.0, sz / 2.0 - 1.0, "kota/abadi_penceramah", 0.0)
    finish_lights(p, sx // 2, DECK + 1, sz // 2)


def build_kebun(p, sx, sz):
    for x in range(1, sx - 1):
        for z in range(1, sz - 1):
            p.put(x, DECK, z, "minecraft:grass_block", {"snowy": "false"})
    for x, z in ((4, 4), (10, 4), (16, 4), (4, 11), (16, 11), (4, 17), (10, 17), (16, 17)):
        cherry(p, x, DECK + 1, z, height=4 + (x + z) % 2, r=2)
    p.disc(10, DECK, 11, 2, "minecraft:water")
    p.put(10, DECK + 1, 11, "minecraft:lily_pad")
    anchor(p, 10.5, DECK + 1.0, 14.5, "kota/abadi_persik", 180.0)
    anchor(p, 10.5, DECK + 3.0, 10.5, "kota/abadi_kebun", 0.0, protect=0, pts=[-6, 2, -6, 6, 2, -6, -6, 2, 6, 6, 2, 6, 0, 3, 0])
    post(p, 1, DECK + 1, sz - 2, height=2, post_role="rail")
    post(p, sx - 2, DECK + 1, sz - 2, height=2, post_role="rail")
    finish_lights(p, 10, DECK + 1, 18)


def build_lonceng(p, sx, sz):
    p.walls(2, DECK + 1, 2, sx - 3, DECK + 16, sz - 3, role="plaster", pillar="minecraft:quartz_pillar", windows=4, door=("south", 1))
    for y in (DECK + 5, DECK + 10):
        p.fill(3, y, 3, sx - 4, y, sz - 4, "minecraft:cherry_planks")
        p.put(sx // 2, y, sz // 2, "minecraft:air")
    p.put(sx // 2, DECK + 16, sz // 2, "minecraft:cherry_planks")
    p.put(sx // 2, DECK + 15, sz // 2, "minecraft:bell", {"attachment": "ceiling", "facing": "north", "powered": "false"})
    p.roof_hip(1, DECK + 17, 1, sx - 2, sz - 2, overhang=1, eave_curl=True, tiers=2, tier_gap=1, tier_band=2, band_role="plaster")
    anchor(p, sx / 2.0, DECK + 14.0, sz / 2.0, "kota/abadi_lonceng", 0.0, protect=16)
    lit(p, sx // 2, DECK + 10, 3, chain=1)
    finish_lights(p, sx // 2, DECK + 1, sz // 2)


def build_lelang_abadi(p, sx, sz):
    p.fill(4, DECK + 1, 2, sx - 5, DECK + 2, 6, "minecraft:quartz_bricks")
    for x in range(4, sx - 4):
        p.put(x, DECK + 3, 6, "minecraft:white_carpet")
    for x in (4, sx - 5):
        p.pillar(x, DECK + 3, DECK + 9, 2, role="minecraft:quartz_pillar", base="trim", cap="trim")
    for x in range(4, sx - 4):
        p.put(x, DECK + 10, 2, "trim")
    p.roof_hip(3, DECK + 11, 1, sx - 4, 7, overhang=1, eave_curl=True)
    for z in (9, 11, 13):
        for x in range(3, sx - 3):
            if x != sx // 2:
                p.stairs(x, DECK + 1, z, "minecraft:quartz_stairs", "north")
    anchor(p, sx / 2.0, DECK + 3.0, 4.5, "kota/abadi_lelang", 0.0)
    anchor(p, sx / 2.0, DECK + 7.0, 4.5, "kota/panggung", 0.0, protect=0, pts=[-4, 0, 0, 4, 0, 0])
    lit(p, sx // 2, DECK + 10, 4, chain=1)
    finish_lights(p, sx // 2, DECK + 1, 8)


def build_rumah_abadi(p, sx, sz):
    p.walls(2, DECK + 1, 2, sx - 3, DECK + 5, sz - 3, role="plaster", pillar="minecraft:quartz_pillar", windows=3, door=("south", 1))
    p.roof_gable(2, DECK + 6, 2, sx - 3, sz - 3, along="x", overhang=1, eave_curl=True)
    cushion(p, 4, DECK + 1, 4, "minecraft:pink_carpet")
    lit(p, sx // 2, DECK + 5, sz // 2, chain=1)
    anchor(p, sx / 2.0, DECK + 1.0, sz / 2.0, "kota/abadi_warga", 180.0)
    finish_lights(p, sx // 2, DECK + 1, sz // 2)


# ======================================================================================================== JSON
def P(name):
    return K + "kota/" + name


def write_pools():
    terr = "terrain_matching"
    rigid = "rigid"
    lapuk = K + "kota/lapuk"
    utuh = K + "kota/utuh"
    pools = {
        "gerbang": [(single(P("gerbang"), lapuk, rigid), 1)],
        "jalan_utama_a": [(single(P("jalan_utama_a"), utuh, terr), 1)],
        "jalan_utama_b": [(single(P("jalan_utama_b"), utuh, terr), 1)],
        "alun": [(single(P("alun"), utuh, rigid), 1)],
        "unik_lelang": [(single(P("balai_lelang"), lapuk, rigid), 1)],
        "unik_teh": [(single(P("kedai_teh"), utuh, rigid), 1)],
        "unik_peti": [(single(P("toko_peti"), lapuk, rigid), 1)],
        "unik_pil": [(single(P("paviliun_pil"), lapuk, rigid), 1)],
        "unik_pusaka": [(single(P("paviliun_pusaka"), lapuk, rigid), 1)],
        "unik_penginapan": [(single(P("penginapan"), lapuk, rigid), 1)],
        "unik_kantor": [(single(P("kantor_sekte"), lapuk, rigid), 1)],
        "jalan": [(single(P("jalan_lurus_1"), utuh, terr), 3), (single(P("jalan_lurus_2"), utuh, terr), 2),
                  (single(P("jalan_lurus_3"), utuh, terr), 2), (single(P("jembatan"), utuh, terr), 1)],
        "jalan_ujung": [(single(P("jalan_lurus_1"), utuh, terr), 3), (single(P("jalan_lurus_2"), utuh, terr), 2),
                        (single(P("jalan_silang"), utuh, terr), 2), (single(P("jembatan"), utuh, terr), 1),
                        (single(P("tembok_ujung_1"), lapuk, rigid), 2), (single(P("tembok_ujung_2"), lapuk, rigid), 2)],
        "tembok_ujung": [(single(P("tembok_ujung_1"), lapuk, rigid), 1), (single(P("tembok_ujung_2"), lapuk, rigid), 1)],
        "rumah": [(single(P("rumah_kecil_%d" % i), lapuk, rigid), 3) for i in range(3)]
                 + [(single(P("rumah_sedang_%d" % i), lapuk, rigid), 2) for i in range(3)]
                 + [(single(P("rumah_besar_%d" % i), lapuk, rigid), 1) for i in range(3)]
                 + [(single(P("hias_%s" % k), utuh, rigid), 1) for k in ("kios", "gerobak", "lentera")]
                 + [({"element_type": "minecraft:empty_pool_element"}, 1)],
        # Kota Abadi
        "abadi_gerbang": [(single(P("abadi_gerbang"), utuh, rigid), 1)],
        "abadi_jembatan": [(single(P("abadi_jembatan"), utuh, rigid), 1)],
        "abadi_jembatan_kebun": [(single(P("abadi_jembatan_kebun"), utuh, rigid), 1)],
        "abadi_jembatan_lonceng": [(single(P("abadi_jembatan_lonceng"), utuh, rigid), 1)],
        "abadi_jembatan_lelang": [(single(P("abadi_jembatan_lelang"), utuh, rigid), 1)],
        "abadi_alun": [(single(P("abadi_alun"), utuh, rigid), 1)],
        "abadi_istana": [(single(P("abadi_istana"), utuh, rigid), 1)],
        "abadi_pasar": [(single(P("abadi_pasar"), utuh, rigid), 1)],
        "abadi_dao": [(single(P("abadi_dao"), utuh, rigid), 1)],
        "abadi_kebun": [(single(P("abadi_kebun"), utuh, rigid), 1)],
        "abadi_lonceng": [(single(P("abadi_lonceng"), utuh, rigid), 1)],
        "abadi_lelang": [(single(P("abadi_lelang"), utuh, rigid), 1)],
        "abadi_rumah": [(single(P("abadi_rumah"), utuh, rigid), 1)],
    }
    fallback = {"jalan": K + "kota/tembok_ujung", "jalan_ujung": K + "kota/tembok_ujung"}
    for name, elements in pools.items():
        write_json(json_path("template_pool", "kota", name), pool(P(name), elements, fallback.get(name, "minecraft:empty")))


def write_processors():
    protect = {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}
    mossy = {"processor_type": "minecraft:rule", "rules": [
        {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:stone_bricks", "probability": 0.12},
         "location_predicate": {"predicate_type": "minecraft:always_true"},
         "output_state": {"id": "minecraft:mossy_stone_bricks"}},
        {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:cobblestone", "probability": 0.2},
         "location_predicate": {"predicate_type": "minecraft:always_true"},
         "output_state": {"id": "minecraft:mossy_cobblestone"}},
        {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:polished_andesite", "probability": 0.06},
         "location_predicate": {"predicate_type": "minecraft:always_true"},
         "output_state": {"id": "minecraft:cracked_stone_bricks"}},
    ]}
    write_json(json_path("processor_list", "kota", "lapuk"), {"processors": [mossy, protect]})
    write_json(json_path("processor_list", "kota", "utuh"), {"processors": [protect]})


OVERWORLD_BIOMES = ["minecraft:plains", "minecraft:meadow", "minecraft:cherry_grove", "minecraft:forest", "minecraft:birch_forest",
                    "minecraft:flower_forest", "minecraft:savanna", "minecraft:taiga", "minecraft:snowy_plains"]


def write_structures():
    town = {
        "type": "minecraft:jigsaw", "biomes": "#kultivator:has_structure/kota/kota_kultivator", "step": "surface_structures",
        "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}}, "terrain_adaptation": "beard_thin",
        "start_pool": P("gerbang"), "size": 6, "start_height": {"absolute": 0}, "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 72, "use_expansion_hack": False,
    }
    write_json(json_path("structure", "kota", "kota_kultivator"), town)
    city = {
        "type": "minecraft:jigsaw", "biomes": "#kultivator:has_structure/kota/kota_abadi", "step": "surface_structures",
        "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": []}}, "terrain_adaptation": "none",
        "start_pool": P("abadi_gerbang"), "size": 5, "start_height": {"absolute": 150},
        "max_distance_from_center": 80, "use_expansion_hack": False,
    }
    write_json(json_path("structure", "kota", "kota_abadi"), city)
    tags = os.path.join(DATA, "tags", "worldgen")
    write_json(os.path.join(tags, "biome", "has_structure", "kota", "kota_kultivator.json"), {"replace": False, "values": OVERWORLD_BIOMES})
    write_json(os.path.join(tags, "biome", "has_structure", "kota", "kota_abadi.json"), {"replace": False, "values": [K + "alam_abadi"]})
    write_json(os.path.join(tags, "structure", "kota.json"), {"replace": False, "values": [K + "kota/kota_kultivator"]})
    write_json(os.path.join(tags, "structure", "kota_abadi.json"), {"replace": False, "values": [K + "kota/kota_abadi"]})
    # the structure sets live in the built-in pack kultivator:dunia_kota (DESIGN 34.4.5): no pack, no new towns
    pack = os.path.join(REPO, "src", "main", "resources", "resourcepacks", "dunia_kota")
    write_json(os.path.join(pack, "pack.mcmeta"), {"pack": {"description": {"translate": "kultivator.dunia.pack.kota"},
                                                            "min_format": 121, "max_format": 121}})
    sets = os.path.join(pack, "data", "kultivator", "worldgen", "structure_set", "kota")
    write_json(os.path.join(sets, "kota_kultivator.json"), {
        "structures": [{"structure": K + "kota/kota_kultivator", "weight": 1}],
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
def main():
    gerbang()
    jalan_utama("jalan_utama_a", "kota/unik_pil", "kota/unik_pusaka", "kota/jalan_utama_b", 53010)
    jalan_utama("jalan_utama_b", "kota/unik_penginapan", "kota/unik_kantor", "kota/alun", 53011)
    jalan("jalan_lurus_1", 53020, "lentera")
    jalan("jalan_lurus_2", 53021, "kios")
    jalan("jalan_lurus_3", 53022, "gerobak")
    jalan_silang()
    jembatan()
    tembok_ujung("tembok_ujung_1", 53035, gate=False)
    tembok_ujung("tembok_ujung_2", 53036, gate=True)
    alun()
    balai_lelang()
    kedai_teh()
    toko_peti()
    paviliun("paviliun_pil", 53053, "pil")
    paviliun("paviliun_pusaka", 53054, "pusaka")
    penginapan()
    kantor_sekte()
    for i in range(3):
        rumah("rumah_kecil_%d" % i, 7, 7, 3, 53060 + i, i)
        rumah("rumah_sedang_%d" % i, 9, 9, 4, 53070 + i, i)
        rumah("rumah_besar_%d" % i, 11, 9, 4, 53080 + i, i)
    hias("hias_kios", 53090, "kios")
    hias("hias_gerobak", 53091, "gerobak")
    hias("hias_lentera", 53092, "lentera")
    # Kota Abadi
    abadi_gerbang()
    abadi_jembatan("abadi_jembatan", 66002, 18, "kota/abadi_alun")
    abadi_jembatan("abadi_jembatan_kebun", 66003, 10, "kota/abadi_kebun")
    abadi_jembatan("abadi_jembatan_lonceng", 66004, 10, "kota/abadi_lonceng")
    abadi_jembatan("abadi_jembatan_lelang", 66005, 12, "kota/abadi_lelang")
    abadi_alun()
    abadi_hall("abadi_istana", 27, 23, 66020, build_istana, extra_h=32)
    abadi_hall("abadi_pasar", 21, 16, 66021, build_pasar)
    abadi_hall("abadi_dao", 21, 21, 66022, build_dao, extra_h=18)
    abadi_hall("abadi_kebun", 21, 21, 66023, build_kebun)
    abadi_hall("abadi_lonceng", 11, 11, 66024, build_lonceng, extra_h=30)
    abadi_hall("abadi_lelang", 19, 16, 66025, build_lelang_abadi, extra_h=16)
    abadi_hall("abadi_rumah", 11, 11, 66026, build_rumah_abadi, extra_h=12)
    write_pools()
    write_processors()
    write_structures()
    write_loot()
    validate_all()
    if len(sys.argv) > 1:
        preview(sys.argv[1])


def preview(out_dir):
    """Top views of every piece as text files (one glyph per column, the highest block)."""
    os.makedirs(out_dir, exist_ok=True)
    for name in SAVED:
        _, root = nbt.read_file(out_path("kota", name))
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
        with open(os.path.join(out_dir, name + ".txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
