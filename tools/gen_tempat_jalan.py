"""DESIGN 38.C.6 (module 38W): roads, bridges, road stairs, ferry docks and the road-side posts.

Writes (data/kultivator/...):
  structure/jalan/**.nbt       road tiles (terrain_matching), lantern posts, bridges 8-28 per road class, road stairs,
                               the ferry dock
  structure/pos/**.nbt         milestone (tonggak), road shrine (kuil_jalan), rest post (singgah), great rest post
                               (singgah_besar, 2 x 2 lots of 32), caravanserai (dagang), bandit camp (kemah_begal)
  worldgen/template_pool/jalan/**, pos/**, worldgen/structure/jalan/jalan.json, structure/pos/<jenis>.json
  resourcepacks/dunia_jalan/data/kultivator/worldgen/structure_set/{jalan/jalan, pos/<jenis>}.json  (salt 380301-380307)
  tags/worldgen/structure/{boleh_di_tempat, jalan, pos, tempat_dirawat}.json, tags/worldgen/structure_set/tempat_berpenghuni.json
  (the two shared 38 tags are written whole: every inhabited / cared-for place of all 38 modules, see tag_tempat)

Front of every post template = south (+z): JalanStructure turns it toward the road. Floors at tl.LANTAI_Y over four
foundation layers; the yard is cleared of terrain six blocks up (beard_thin fills below). No fire, lava, spawner or
lit campfire (DESIGN 38.D.2); the bandit camp's campfire is unlit. Only world generation places these templates.

Run:  python tools/gen_tempat_jalan.py      (then python tools/cek_tempat.py)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tempat_lib as tl  # noqa: E402
import structgen  # noqa: E402

LY = tl.LANTAI_Y
KELAS = ("kekaisaran", "desa", "setapak")
LEBAR = {"kekaisaran": 5, "desa": 3, "setapak": 2}
PANJANG = (8, 12, 16, 20, 24, 28)
DEK_Y = 12        # JalanStructure.DEK_Y
DERMAGA_Y = 6     # JalanStructure.DERMAGA_Y
SALT_JALAN = 380301
SALT_POS = {"tonggak": 380302, "kuil_jalan": 380303, "singgah": 380304, "singgah_besar": 380305, "dagang": 380306,
            "kemah_begal": 380307}
OVERWORLD = "#minecraft:is_overworld"
PACK = "dunia_jalan"


# ================================================================================================== small helpers
def stairs(t, x, y, z, bid, facing, half="bottom", shape="straight"):
    t.put(x, y, z, bid, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})


def slab(t, x, y, z, bid, kind="bottom"):
    t.put(x, y, z, bid, {"type": kind, "waterlogged": "false"})


def log(t, x, y, z, bid, axis="y"):
    t.put(x, y, z, bid, {"axis": axis})


def lantern(t, x, y, z, hanging=False, bid="minecraft:lantern"):
    t.put(x, y, z, bid, {"hanging": "true" if hanging else "false", "waterlogged": "false"})


def candle(t, x, y, z, n=1, color=""):
    t.put(x, y, z, "minecraft:%scandle" % (color + "_" if color else ""), {"candles": str(n), "lit": "false",
                                                                         "waterlogged": "false"})


def bed(t, x, y, z, facing, color="red"):
    """A bed whose foot is at (x, y, z) and head one block toward `facing`."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    t.put(x, y, z, "minecraft:%s_bed" % color, {"facing": facing, "part": "foot", "occupied": "false"})
    t.put(x + dx, y, z + dz, "minecraft:%s_bed" % color, {"facing": facing, "part": "head", "occupied": "false"})


def table(t, x, y, z, top="minecraft:spruce_pressure_plate", leg="minecraft:spruce_fence"):
    t.put(x, y, z, leg)
    t.put(x, y + 1, z, top, {"powered": "false"} if top.endswith("pressure_plate") else None)


def chair(t, x, y, z, facing, bid="minecraft:spruce_stairs"):
    stairs(t, x, y, z, bid, facing)


def chest(t, x, y, z, facing, loot=None):
    nbt = {"LootTable": loot} if loot else None
    t.put(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"}, nbt)


def barrel(t, x, y, z, facing="up"):
    t.put(x, y, z, "minecraft:barrel", {"facing": facing, "open": "false"})


def door(t, x, y, z, facing, bid="minecraft:spruce_door", hinge="left"):
    t.put(x, y, z, bid, {"facing": facing, "half": "lower", "hinge": hinge, "open": "false", "powered": "false"})
    t.put(x, y + 1, z, bid, {"facing": facing, "half": "upper", "hinge": hinge, "open": "false", "powered": "false"})


def trapdoor_window(t, x, y, z, facing, bid="minecraft:spruce_trapdoor"):
    t.put(x, y, z, bid, {"facing": facing, "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})


def hanging_lantern(t, x, y_ceiling, z, chain=1):
    for i in range(chain):
        t.put(x, y_ceiling - 1 - i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    lantern(t, x, y_ceiling - 1 - chain, z, hanging=True)


def stone_lantern(t, x, y, z, base="minecraft:stone_bricks", wall="minecraft:stone_brick_wall",
                  cap="minecraft:stone_brick_slab"):
    """A toro-style stone lantern: base, two wall posts, lantern, slab cap (4 tall)."""
    t.put(x, y, z, base)
    t.put(x, y + 1, z, wall)
    lantern(t, x, y + 2, z)
    slab(t, x, y + 3, z, cap)


def tree(t, x, y, z, kind="oak", height=5, radius=2):
    """A small built tree (persistent leaves, so nothing decays)."""
    for i in range(height):
        log(t, x, y + i, z, "minecraft:%s_log" % kind)
    top = y + height
    for dy in range(-2, 2):
        r = radius if dy < 0 else radius - 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r and t.rng.random() < 0.6:
                    continue
                if dx == 0 and dz == 0 and dy < 0:
                    continue
                t.put(x + dx, top + dy, z + dz, "minecraft:%s_leaves" % kind,
                      {"persistent": "true", "distance": "1", "waterlogged": "false"})


def pine(t, x, y, z, height=7, rmax=2):
    """A spruce in tiers (persistent)."""
    for i in range(height):
        log(t, x, y + i, z, "minecraft:spruce_log")
    for tier, (dy, r) in enumerate(((2, rmax), (4, rmax), (5, 1), (6, 1), (7, 0))):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) > r + (1 if r == 2 else 0):
                    continue
                if dx == 0 and dz == 0 and dy < height:
                    continue
                t.put(x + dx, y + dy, z + dz, "minecraft:spruce_leaves",
                      {"persistent": "true", "distance": "1", "waterlogged": "false"})


def clear(t, y_from=LY + 1, height=7):
    sx, _, sz = t.size
    t.air(0, y_from, 0, sx - 1, min(t.size[1] - 1, y_from + height - 1), sz - 1)


def ground(t, top="minecraft:grass_block", alt="minecraft:coarse_dirt", alt_chance=0.15, found="minecraft:dirt"):
    """Four foundation layers (stone below, dirt above) and a natural yard floor at LY."""
    sx, _, sz = t.size
    t.fill(0, 0, 0, sx - 1, 1, sz - 1, "minecraft:cobblestone")
    t.fill(0, 2, 0, sx - 1, LY - 1, sz - 1, found)
    for x in range(sx):
        for z in range(sz):
            b = alt if t.rng.random() < alt_chance else top
            t.put(x, LY, z, b, {"snowy": "false"} if b == "minecraft:grass_block" else None)


def path_line(t, pts, width=3, main="minecraft:dirt_path", alt="minecraft:gravel", alt_chance=0.18):
    sx, _, sz = t.size
    h = width // 2
    pts = [(min(max(x, h), sx - 1 - h), min(max(z, h), sz - 1 - h)) for x, z in pts]
    t.path(pts, LY, width=width, role=main, alt=alt, alt_chance=alt_chance)


def fence_line(t, x0, z0, x1, z1, y=LY + 1, bid="minecraft:spruce_fence", post_every=0, post="minecraft:stripped_spruce_log"):
    if x0 == x1:
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if post_every and (z - min(z0, z1)) % post_every == 0:
                log(t, x0, y, z, post)
                t.put(x0, y + 1, z, bid)
            else:
                t.put(x0, y, z, bid)
    else:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            if post_every and (x - min(x0, x1)) % post_every == 0:
                log(t, x, y, z0, post)
                t.put(x, y + 1, z0, bid)
            else:
                t.put(x, y, z0, bid)


# ================================================================================================== road pieces
def ubin():
    """Road tiles (terrain_matching): one road block per column + one layer of air above (snow and plants come later)."""
    ids = {k: [] for k in KELAS}
    mats = {
        "kekaisaran": (["minecraft:stone_bricks"] * 6 + ["minecraft:andesite", "minecraft:cracked_stone_bricks",
                                                         "minecraft:mossy_stone_bricks"], "minecraft:polished_andesite"),
        "desa": (["minecraft:dirt_path"] * 6 + ["minecraft:gravel", "minecraft:coarse_dirt"], "minecraft:coarse_dirt"),
        "setapak": (["minecraft:dirt_path"] * 4 + ["minecraft:rooted_dirt", "minecraft:coarse_dirt"], None),
    }
    for kelas in KELAS:
        w = LEBAR[kelas]
        inner, edge = mats[kelas]
        for v in "abc" if kelas != "setapak" else "ab":
            t = tl.Lot("jalan", "ubin_%s_%s" % (kelas, v), tinggi=2, lebar=w, dalam=w, palet="jalan")
            for x in range(w):
                for z in range(w):
                    on_edge = edge and (x in (0, w - 1) or z in (0, w - 1))
                    b = edge if on_edge and t.rng.random() < 0.7 else t.rng.choice(inner)
                    t.put(x, 0, z, b)
                    t.put(x, 1, z, "minecraft:air")
            t.simpan()
            ids[kelas].append(t.id)
    for kelas in KELAS:
        tl.tulis_pool("jalan/ubin_" + kelas, [(tl.elemen(i, projection="terrain_matching"), 1) for i in ids[kelas]])
    # imperial lantern posts (terrain_matching)
    lids = []
    for v, (base, wall) in enumerate((("minecraft:stone_bricks", "minecraft:stone_brick_wall"),
                                      ("minecraft:chiseled_stone_bricks", "minecraft:andesite_wall"))):
        t = tl.Lot("jalan", "lentera_%s" % "ab"[v], tinggi=5, lebar=1, dalam=1, palet="jalan")
        t.put(0, 0, 0, "minecraft:polished_andesite")
        t.put(0, 1, 0, base)
        t.put(0, 2, 0, wall)
        lantern(t, 0, 3, 0)
        slab(t, 0, 4, 0, "minecraft:stone_brick_slab")
        t.simpan()
        lids.append(t.id)
    tl.tulis_pool("jalan/lentera", [(tl.elemen(i, projection="terrain_matching"), 1) for i in lids])


def jembatan():
    """Bridges along +z: deck at DEK_Y, piers/piles down to y 0, length n (8..28), class width + 2."""
    for kelas in KELAS:
        ids = []
        for n in PANJANG:
            w = LEBAR[kelas] + 2
            t = tl.Lot("jalan", "jembatan_%s_%d" % (kelas, n), tinggi=DEK_Y + 4, lebar=w, dalam=n, palet="jalan")
            d = DEK_Y
            if kelas == "kekaisaran":
                for z in range(n):
                    for x in range(w):
                        edge = x in (0, w - 1)
                        b = "minecraft:polished_andesite" if edge else t.rng.choice(
                            ["minecraft:stone_bricks"] * 5 + ["minecraft:cracked_stone_bricks", "minecraft:andesite"])
                        t.put(x, d, z, b)
                        t.air(x, d + 1, z, x, d + 2, z)
                    # parapet with chiseled posts, lanterns every 8
                    for x in (0, w - 1):
                        post = z % 4 == 0 or z == n - 1
                        t.put(x, d + 1, z, "minecraft:chiseled_stone_bricks" if post else "minecraft:stone_brick_wall")
                        if post and z % 8 == 0:
                            lantern(t, x, d + 2, z)
                    # arch underside: upside-down stairs along the span (between piers)
                    if 2 <= z <= n - 3:
                        for x in range(1, w - 1):
                            stairs(t, x, d - 1, z, "minecraft:stone_brick_stairs", "north" if z % 8 < 4 else "south",
                                   half="top")
                # piers every 8 and the two abutments: cutwater piers to y 0
                piers = [0, n - 1] + [z for z in range(8, n - 4, 8)]
                for z in piers:
                    for y in range(0, d):
                        for x in range(1, w - 1):
                            t.put(x, y, z, "minecraft:stone_bricks" if y > 2 else "minecraft:mossy_stone_bricks")
                        if z not in (0, n - 1) and y < d - 1:
                            for zz, face in ((z - 1, "south"), (z + 1, "north")):
                                stairs(t, w // 2, y, zz, "minecraft:stone_brick_stairs", face)
            elif kelas == "desa":
                for z in range(n):
                    for x in range(w):
                        if x in (0, w - 1):
                            t.put(x, d, z, "minecraft:stripped_spruce_log", {"axis": "z"})
                            post = z % 4 == 0 or z == n - 1
                            if post:
                                log(t, x, d + 1, z, "minecraft:spruce_log")
                                if z % 8 == 0:
                                    lantern(t, x, d + 2, z)
                            else:
                                t.put(x, d + 1, z, "minecraft:spruce_fence")
                        else:
                            t.put(x, d, z, "minecraft:spruce_planks" if (z + x) % 5 else "minecraft:stripped_spruce_wood",
                                  {"axis": "y"} if (z + x) % 5 == 0 else None)
                            t.air(x, d + 1, z, x, d + 2, z)
                    # cross beam under the deck
                    if z % 4 == 2:
                        for x in range(w):
                            log(t, x, d - 1, z, "minecraft:stripped_spruce_log", axis="x")
                piles = [0, n - 1] + [z for z in range(6, n - 3, 6)]
                for z in piles:
                    for x in (0, w - 1):
                        for y in range(0, d):
                            log(t, x, y, z, "minecraft:spruce_log")
                    # diagonal brace (fence) between the piles just under the deck
                    for x in range(1, w - 1):
                        t.put(x, d - 2, z, "minecraft:spruce_fence")
            else:  # setapak: plank rope bridge, posts only at the ends and the middle
                for z in range(n):
                    for x in range(1, w - 1):
                        slab(t, x, d, z, "minecraft:spruce_slab", "top")
                        t.air(x, d + 1, z, x, d + 2, z)
                    for x in (0, w - 1):
                        if z in (0, n - 1) or z == n // 2:
                            for y in range(0, d + 2):
                                log(t, x, y, z, "minecraft:stripped_spruce_log")
                            t.put(x, d + 2, z, "minecraft:spruce_fence")
                        else:
                            t.put(x, d + 1, z, "minecraft:iron_chain", {"axis": "z", "waterlogged": "false"})
                lantern(t, 0, d + 3, 0, hanging=False)
            t.simpan()
            ids.append(t.id)
            tl.tulis_pool("jalan/jembatan_%s_%d" % (kelas, n), [(tl.elemen(t.id), 1)])
        tl.tulis_pool("jalan/jembatan_" + kelas, [(tl.elemen(ids[-1]), 1)])


def tangga():
    """Road stairs along +z: landing at y 0 (z 0), four steps up (z 1-4), landing at y 4 (z 5); air three above."""
    mats = {"kekaisaran": ("minecraft:stone_brick_stairs", "minecraft:stone_bricks", "minecraft:stone_brick_wall"),
            "desa": ("minecraft:cobblestone_stairs", "minecraft:cobblestone", "minecraft:spruce_fence"),
            "setapak": ("minecraft:mossy_cobblestone_stairs", "minecraft:mossy_cobblestone", None)}
    for kelas in KELAS:
        st, full, rail = mats[kelas]
        w = LEBAR[kelas] + (2 if rail else 0)
        t = tl.Lot("jalan", "tangga_" + kelas, tinggi=8, lebar=w, dalam=6, palet="jalan")
        for z in range(6):
            top = 0 if z == 0 else min(z, 4)
            for x in range(w):
                for y in range(0, top):
                    t.put(x, y, z, full)
                if rail and x in (0, w - 1):
                    t.put(x, top, z, full)
                    t.put(x, top + 1, z, rail)
                    continue
                if z == 0 or z == 5:
                    t.put(x, top, z, full)
                else:
                    stairs(t, x, top, z, st, "south")
                t.air(x, top + 1, z, x, top + 3, z)
        t.simpan()
        tl.tulis_pool("jalan/tangga_" + kelas, [(tl.elemen(t.id), 1)])


def penyeberangan():
    """Ferry dock (38.C.6.2): pier along +z out over the water, deck at DERMAGA_Y; local z 2 sits on the bank.
    Ramp at the land end, bell post, ferry board, mooring for the raft (RakitEntity, spawned by the pos/penyeberangan
    anchor), lanterns."""
    w, n = 9, 14
    t = tl.Lot("jalan", "penyeberangan_a", tinggi=DERMAGA_Y + 6, lebar=w, dalam=n, palet="jalan")
    d = DERMAGA_Y
    for z in range(n):
        for x in range(1, w - 1):
            if z < 2:
                stairs(t, x, d, z, "minecraft:spruce_stairs", "south")
                t.fill(x, d - 3, z, x, d - 1, z, "minecraft:cobblestone")
            else:
                t.put(x, d, z, "minecraft:spruce_planks" if x % 3 else "minecraft:stripped_spruce_log",
                      {"axis": "z"} if x % 3 == 0 else None)
            t.air(x, d + 1, z, x, d + 3, z)
        for x in (0, w - 1):
            if z >= 2:
                post = z % 3 == 2
                if post:
                    for y in range(0, d + 2):
                        log(t, x, y, z, "minecraft:spruce_log")
                    if z % 6 == 2:
                        lantern(t, x, d + 2, z)
                else:
                    t.put(x, d, z, "minecraft:spruce_planks")
                    t.put(x, d + 1, z, "minecraft:spruce_fence")
    # mid piles under the deck
    for z in range(4, n, 4):
        for y in range(0, d):
            log(t, w // 2, y, z, "minecraft:stripped_spruce_log")
    # mooring bollards and coiled rope at the far end (the raft waits here)
    for x in (2, w - 3):
        t.put(x, d + 1, n - 1, "minecraft:spruce_fence")
        t.put(x, d + 2, n - 1, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    t.put(w // 2, d + 1, n - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    # bell post and the ferry board near the land end
    log(t, 1, d + 1, 3, "minecraft:spruce_log")
    log(t, 1, d + 2, 3, "minecraft:spruce_log")
    t.put(1, d + 3, 3, "minecraft:spruce_planks")
    t.put(2, d + 2, 3, "minecraft:bell", {"attachment": "single_wall", "facing": "east", "powered": "false"})
    t.put(w - 2, d + 1, 3, "minecraft:spruce_planks")
    t.put(w - 2, d + 2, 3, "minecraft:spruce_wall_sign", {"facing": "north", "waterlogged": "false"})
    t.titik("tambatan", w // 2, d + 1, n - 2, "south", {"kelas": "rakit"})
    t.titik("jaga", 2, d + 1, 4, "south")
    t.jangkar(w / 2.0, d + 1, n - 3.5, "pos/penyeberangan", protect="12", situs="pos/penyeberangan")
    t.simpan()
    tl.tulis_pool("jalan/penyeberangan", [(tl.elemen(t.id), 1)])


# ================================================================================================== post buildings
def roof_over(t, x0, y, z0, x1, z1, along="x", roof="minecraft:dark_oak_stairs", slab_r="minecraft:dark_oak_slab",
              full="minecraft:dark_oak_planks"):
    pal = t.pal
    t.pal = dict(pal, roof=roof, roof_slab=slab_r, roof_full=full)
    top = t.roof_gable(x0, y, z0, x1, z1, along=along)
    t.pal = pal
    return top


def house(t, x0, z0, x1, z1, wall_h=4, door_side="south", windows=True, frame="minecraft:stripped_spruce_log",
          wall="minecraft:white_terracotta", base="minecraft:cobblestone", along="x", roof="minecraft:dark_oak_stairs",
          slab_r="minecraft:dark_oak_slab", full="minecraft:dark_oak_planks", floor="minecraft:spruce_planks"):
    """A timber-frame house: stone plinth row, plaster walls with log frame, trapdoor windows, a door, a gable roof.
    Returns (inner box, roof top y)."""
    y0 = LY + 1
    t.air(x0, y0, z0, x1, y0 + wall_h + 6, z1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.put(x, LY, z, floor if (x0 < x < x1 and z0 < z < z1) else base)
    for y in range(y0, y0 + wall_h):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                along_i = (x - x0) if z in (z0, z1) else (z - z0)
                if corner or along_i % 4 == 0:
                    log(t, x, y, z, frame)
                elif y == y0:
                    t.put(x, y, z, base)
                elif windows and y == y0 + 2 and along_i % 4 == 2:
                    face = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
                    trapdoor_window(t, x, y, z, face)
                else:
                    t.put(x, y, z, wall)
    # top beam
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            log(t, x, y0 + wall_h, z, frame, axis="x")
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            log(t, x, y0 + wall_h, z, frame, axis="z")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if door_side == "south":
        door(t, cx, y0, z1, "south")
        stairs(t, cx, LY, z1 + 1, "minecraft:cobblestone_stairs", "north")
    elif door_side == "north":
        door(t, cx, y0, z0, "north")
    elif door_side == "east":
        door(t, x1, y0, cz, "east")
    else:
        door(t, x0, y0, cz, "west")
    top = roof_over(t, x0, y0 + wall_h + 1, z0, x1, z1, along=along, roof=roof, slab_r=slab_r, full=full)
    # ceiling lantern inside
    hanging_lantern(t, cx, y0 + wall_h, cz)
    return (x0 + 1, z0 + 1, x1 - 1, z1 - 1), top


def tonggak(v):
    """Milestone stela (38.C.6.3): plinth, carved column, cap; a lantern and offerings; the stone carries the
    billboard "<- place 1.2 li / 600 b ->" (client) and is touched to discover the road."""
    t = tl.Lot("pos", "tonggak/tonggak_%s" % v, tinggi=LY + 9, lebar=5, dalam=5, palet="jalan")
    t.fill(0, 0, 0, 4, LY - 1, 4, "minecraft:cobblestone")
    for x in range(5):
        for z in range(5):
            t.put(x, LY, z, "minecraft:polished_andesite" if 0 < x < 4 and 0 < z < 4 else
                  ("minecraft:gravel" if t.rng.random() < 0.3 else "minecraft:coarse_dirt"))
    clear(t, height=8)
    y = LY + 1
    t.fill(1, y, 1, 3, y, 3, "minecraft:stone_bricks" if v == "a" else "minecraft:mossy_stone_bricks")
    for (x, z, f) in ((2, 1, "south"), (2, 3, "north"), (1, 2, "east"), (3, 2, "west")):
        stairs(t, x, y + 1, z, "minecraft:stone_brick_stairs", f)
    for (x, z) in ((1, 1), (3, 1), (1, 3), (3, 3)):
        slab(t, x, y + 1, z, "minecraft:stone_brick_slab")
    t.put(2, y + 1, 2, "minecraft:chiseled_stone_bricks")
    t.put(2, y + 2, 2, "minecraft:stone_bricks" if v == "a" else "minecraft:cracked_stone_bricks")
    t.put(2, y + 3, 2, "minecraft:chiseled_stone_bricks")
    t.put(2, y + 4, 2, "minecraft:stone_bricks")
    for (x, z, f) in ((2, 1, "south"), (2, 3, "north"), (1, 2, "east"), (3, 2, "west")):
        stairs(t, x, y + 5, z, "minecraft:stone_brick_stairs", f, half="top")
    t.put(2, y + 5, 2, "minecraft:chiseled_stone_bricks")
    slab(t, 2, y + 6, 2, "minecraft:stone_brick_slab")
    # offerings on the plinth front: candles and a potted fern
    candle(t, 1, y + 2, 3, 2, "white")
    t.put(3, y + 2, 3, "minecraft:potted_fern")
    if v == "b":
        t.put(0, y, 0, "minecraft:mossy_cobblestone")
        t.put(4, y, 4, "minecraft:moss_carpet")
        stone_lantern(t, 4, y, 0)
    t.titik("obrol", 2, y, 4, "north")
    t.jangkar(2.5, y + 1, 2.5, "pos/tonggak", protect="6", situs="pos/tonggak")
    t.simpan()
    return t.id


def kuil_jalan(v):
    """Road shrine of the Earth God (Tudi Gong) 9 x 9: stepped base, plaster back wall with the god's niche, red
    pillars, altar with candles and offering bowls, incense urn in front, hip roof, two stone lanterns."""
    t = tl.Lot("pos", "kuil_jalan/kuil_%s" % v, tinggi=LY + 12, lebar=9, dalam=9, palet="jalan")
    t.fill(0, 0, 0, 8, LY - 1, 8, "minecraft:cobblestone")
    for x in range(9):
        for z in range(9):
            edge = x in (0, 8) or z in (0, 8)
            t.put(x, LY, z, "minecraft:stone_bricks" if edge else "minecraft:polished_andesite")
    clear(t, height=10)
    y = LY + 1
    t.fill(2, y, 2, 6, y, 6, "minecraft:stone_bricks")
    for x in range(2, 7):
        stairs(t, x, y, 7, "minecraft:stone_brick_stairs", "north")
    # back wall (plaster in a log frame) and short side walls
    for yy in range(y + 1, y + 4):
        for x in range(2, 7):
            if x in (2, 6):
                log(t, x, yy, 2, "minecraft:stripped_dark_oak_log")
            else:
                t.put(x, yy, 2, "minecraft:white_terracotta")
        for z in (3,):
            for x in (2, 6):
                t.put(x, yy, z, "minecraft:white_terracotta")
    # front pillars (red lacquer)
    for x in (2, 6):
        for yy in range(y + 1, y + 4):
            t.put(x, yy, 6, "minecraft:red_terracotta" if v == "a" else "minecraft:stripped_cherry_log",
                  None if v == "a" else {"axis": "y"})
    # the god: a small seated figure in the niche
    t.put(4, y + 1, 3, "minecraft:polished_andesite")
    t.put(4, y + 2, 3, "minecraft:chiseled_stone_bricks")
    t.put(4, y + 3, 3, "minecraft:stone_button", {"face": "floor", "facing": "south", "powered": "false"})
    # altar table with candles and offerings
    t.put(3, y + 1, 4, "minecraft:polished_andesite_slab", {"type": "top", "waterlogged": "false"})
    t.put(4, y + 1, 4, "minecraft:polished_andesite_slab", {"type": "top", "waterlogged": "false"})
    t.put(5, y + 1, 4, "minecraft:polished_andesite_slab", {"type": "top", "waterlogged": "false"})
    candle(t, 3, y + 2, 4, 3, "red")
    t.put(4, y + 2, 4, "minecraft:flower_pot")
    candle(t, 5, y + 2, 4, 2, "red")
    # incense urn in front (cauldron) and kneeling mats
    t.put(4, LY, 8, "minecraft:polished_andesite")
    t.put(4, y, 8, "minecraft:cauldron")
    t.put(3, y + 1, 6, "minecraft:red_carpet")
    t.put(5, y + 1, 6, "minecraft:red_carpet")
    # roof
    if v == "a":
        t.roof_hip(2, y + 4, 2, 6, 6)
    else:
        pal = t.pal
        t.pal = dict(pal, roof="minecraft:deepslate_tile_stairs", roof_slab="minecraft:deepslate_tile_slab",
                     roof_full="minecraft:deepslate_tiles")
        t.roof_hip(2, y + 4, 2, 6, 6)
        t.pal = pal
    hanging_lantern(t, 2, y + 4, 7)
    hanging_lantern(t, 6, y + 4, 7)
    stone_lantern(t, 0, y, 7)
    stone_lantern(t, 8, y, 7)
    if v == "b":
        pine(t, 7, y, 1, height=6, rmax=1)
    else:
        tree(t, 1, y, 1, "oak", height=4, radius=1)
    t.titik("kerja:dupa", 4, y, 7, "north")
    t.titik("dupa", 4, y + 1, 5, "north")
    t.titik("obrol", 1, y, 5, "east")
    t.jangkar(4.5, y, 7.5, "pos/kuil_jalan", protect="8", situs="pos/kuil_jalan")
    t.simpan()
    return t.id


def tonggak_bixi():
    """Milestone variant c (detail pass D-W): an imperial stela carried by a stone tortoise (bixi), the classic road and
    tomb stela of the dynasties. The tortoise looks south toward the road; the 3-wide stela carries carved bands and a
    small gabled cap with a crest. Offerings of candles in front of the head."""
    t = tl.Lot("pos", "tonggak/tonggak_c", tinggi=LY + 10, lebar=5, dalam=5, palet="jalan")
    t.fill(0, 0, 0, 4, LY - 1, 4, "minecraft:cobblestone")
    for x in range(5):
        for z in range(5):
            edge = x in (0, 4) or z in (0, 4)
            t.put(x, LY, z, ("minecraft:gravel" if t.rng.random() < 0.35 else "minecraft:coarse_dirt") if edge
                  else "minecraft:polished_andesite")
    clear(t, height=9)
    y = LY + 1
    # tortoise body, feet, head and tail
    t.fill(1, y, 1, 3, y, 3, "minecraft:polished_andesite")
    for (x, z) in ((1, 4), (3, 4), (1, 0), (3, 0)):
        slab(t, x, y, z, "minecraft:polished_andesite_slab")
    stairs(t, 2, y, 4, "minecraft:andesite_stairs", "north")
    slab(t, 2, y, 0, "minecraft:andesite_slab")
    # the domed shell around the stela's foot
    for x in (1, 2, 3):
        stairs(t, x, y + 1, 1, "minecraft:stone_brick_stairs", "south")
        stairs(t, x, y + 1, 3, "minecraft:stone_brick_stairs", "north")
    # stela: smooth faces, a carved middle column, carved bands at two heights
    for yy in range(y + 1, y + 5):
        for x in (1, 2, 3):
            carved = (x == 2 and yy in (y + 1, y + 2, y + 3))
            t.put(x, yy, 2, "minecraft:chiseled_stone_bricks" if carved else "minecraft:smooth_stone")
    stairs(t, 1, y + 5, 2, "minecraft:stone_brick_stairs", "east")
    stairs(t, 3, y + 5, 2, "minecraft:stone_brick_stairs", "west")
    t.put(2, y + 5, 2, "minecraft:chiseled_stone_bricks")
    t.put(2, y + 6, 2, "minecraft:stone_brick_wall")
    # offerings by the head
    candle(t, 1, y + 1, 4, 3, "white")
    candle(t, 3, y + 1, 4, 2, "red")
    t.put(0, y, 4, "minecraft:potted_red_tulip")
    t.put(4, y, 4, "minecraft:potted_fern")
    t.put(0, y, 0, "minecraft:mossy_cobblestone")
    t.put(4, y, 1, "minecraft:moss_carpet")
    t.titik("obrol", 2, y, 4, "north")
    t.jangkar(2.5, y + 1, 2.5, "pos/tonggak", protect="6", situs="pos/tonggak", papan="7.4")
    t.simpan()
    return t.id


def tonggak_paviliun():
    """Milestone variant d (detail pass D-W): a stela pavilion (bei ting) on a raised stone platform: four lacquered
    corner posts, low rails on three sides, a teal-glazed hip roof with curled eaves, two hanging lanterns and the
    stela in the middle; steps on the road side."""
    t = tl.Lot("pos", "tonggak/tonggak_d", tinggi=LY + 10, lebar=5, dalam=5, palet="jalan")
    t.fill(0, 0, 0, 4, LY - 1, 4, "minecraft:cobblestone")
    for x in range(5):
        for z in range(5):
            t.put(x, LY, z, "minecraft:stone_bricks" if (x + z) % 3 else "minecraft:cracked_stone_bricks")
    clear(t, height=9)
    y = LY + 1
    t.fill(0, y, 0, 4, y, 3, "minecraft:stone_bricks")
    for x in range(5):
        stairs(t, x, y, 4, "minecraft:stone_brick_stairs", "north")
    for (x, z) in ((0, 0), (4, 0), (0, 3), (4, 3)):
        for yy in range(y + 1, y + 4):
            log(t, x, yy, z, "minecraft:stripped_crimson_stem")
    for x in (1, 2, 3):
        t.put(x, y + 1, 0, "minecraft:spruce_fence")
    for z in (1, 2):
        t.put(0, y + 1, z, "minecraft:spruce_fence")
        t.put(4, y + 1, z, "minecraft:spruce_fence")
    # stela
    t.put(2, y + 1, 2, "minecraft:stone_bricks")
    t.put(2, y + 2, 2, "minecraft:chiseled_stone_bricks")
    t.put(2, y + 3, 2, "minecraft:smooth_stone")
    t.put(2, y + 4, 2, "minecraft:chiseled_stone_bricks")
    candle(t, 1, y + 1, 2, 2, "white")
    candle(t, 3, y + 1, 2, 2, "white")
    t.put(2, y + 1, 3, "minecraft:potted_azalea_bush")
    pal = t.pal
    t.pal = dict(pal, roof="minecraft:dark_prismarine_stairs", roof_slab="minecraft:dark_prismarine_slab",
                 roof_full="minecraft:dark_prismarine")
    t.roof_hip(1, y + 4, 1, 3, 2, overhang=1)
    t.pal = pal
    hanging_lantern(t, 2, y + 4, 0, chain=0)
    hanging_lantern(t, 2, y + 4, 3, chain=0)
    t.titik("obrol", 1, y, 4, "north")
    t.jangkar(2.5, y + 1, 2.5, "pos/tonggak", protect="6", situs="pos/tonggak", papan="7.0")
    t.simpan()
    return t.id


def kuil_pohon():
    """Road shrine variant c (detail pass D-W): the Earth God under a great old tree. A dark-oak giant with a red
    sash tied round its trunk, a brick spirit house with the god's figure at its foot, an offering table with
    candles, an incense urn, kneeling mats, stone benches for travellers and two stone lanterns."""
    t = tl.Lot("pos", "kuil_jalan/kuil_c", tinggi=LY + 14, lebar=9, dalam=9, palet="jalan")
    ground(t, top="minecraft:grass_block", alt="minecraft:podzol", alt_chance=0.2)
    clear(t, height=12)
    y = LY + 1
    # gravel path from the road to the urn
    for z in range(5, 9):
        for x in (3, 4, 5):
            t.put(x, LY, z, "minecraft:gravel" if (x + z) % 2 else "minecraft:coarse_dirt")
    # the old tree: 2 x 2 trunk, roots, a wide crown
    for yy in range(y, y + 7):
        for (x, z) in ((3, 1), (4, 1), (3, 2), (4, 2)):
            log(t, x, yy, z, "minecraft:dark_oak_log")
    for (x, z, ax) in ((2, 1, "x"), (5, 2, "x"), (3, 0, "z"), (4, 3, "z")):
        log(t, x, y, z, "minecraft:dark_oak_log", axis=ax)
    for (x, z) in ((2, 2), (5, 1)):
        t.put(x, y, z, "minecraft:rooted_dirt")
    for dy in range(0, 4):
        r = (4, 4, 3, 2)[dy]
        for dx in range(-r, r + 2):
            for dz in range(-r, r + 2):
                cx, cz = 3 + dx, 1 + dz
                if not (0 <= cx < 9 and 0 <= cz < 9):
                    continue
                if (dx in (-r, r + 1)) and (dz in (-r, r + 1)):
                    continue
                if dy == 0 and t.rng.random() < 0.25:
                    continue
                if 3 <= cx <= 4 and 1 <= cz <= 2 and dy < 1:
                    continue
                t.put(cx, y + 6 + dy, cz, "minecraft:dark_oak_leaves",
                      {"persistent": "true", "distance": "1", "waterlogged": "false"})
    # red sash round the trunk, a banner and prayer lanterns hanging from the crown
    for (x, z) in ((2, 1), (2, 2), (5, 1), (5, 2), (3, 0), (4, 0), (3, 3), (4, 3)):
        t.put(x, y + 3, z, "minecraft:red_wool")
    t.put(3, y + 4, 3, "minecraft:red_wall_banner", {"facing": "south"})
    hanging_lantern(t, 6, y + 6, 4, chain=2)
    hanging_lantern(t, 1, y + 6, 3, chain=1)
    # the spirit house at the foot of the tree
    for x in (3, 4, 5):
        t.put(x, y, 4, "minecraft:bricks")
    t.put(3, y + 1, 4, "minecraft:bricks")
    t.put(5, y + 1, 4, "minecraft:bricks")
    t.put(4, y + 1, 4, "minecraft:polished_andesite")
    stairs(t, 3, y + 2, 4, "minecraft:brick_stairs", "east")
    stairs(t, 5, y + 2, 4, "minecraft:brick_stairs", "west")
    slab(t, 4, y + 2, 4, "minecraft:brick_slab")
    # offering table, candles, mats, urn
    for x in (3, 4, 5):
        slab(t, x, y, 5, "minecraft:polished_andesite_slab", kind="top")
    candle(t, 3, y + 1, 5, 3, "red")
    t.put(4, y + 1, 5, "minecraft:potted_flowering_azalea_bush")
    candle(t, 5, y + 1, 5, 2, "red")
    t.put(3, y, 6, "minecraft:red_carpet")
    t.put(5, y, 6, "minecraft:red_carpet")
    t.put(4, y, 7, "minecraft:cauldron")
    # benches and lanterns
    for x in (0, 1, 7, 8):
        slab(t, x, y, 6, "minecraft:smooth_stone_slab")
    stone_lantern(t, 1, y, 8)
    stone_lantern(t, 7, y, 8)
    t.put(8, y, 1, "minecraft:fern")
    t.put(0, y, 4, "minecraft:fern")
    t.put(7, y, 3, "minecraft:moss_carpet")
    t.titik("kerja:dupa", 4, y, 8, "north")
    t.titik("dupa", 4, y, 6, "north")
    t.titik("kursi", 1, y, 6, "east")
    t.titik("kursi", 7, y, 6, "west")
    t.jangkar(4.5, y, 7.5, "pos/kuil_jalan", protect="8", situs="pos/kuil_jalan")
    t.simpan()
    return t.id


def kuil_gua():
    """Road shrine variant d (detail pass D-W): a grotto shrine in a mossy rock outcrop. The god sits in a niche cut
    into the rock under a small tiled awning on two posts; a spring pool with lily pads, ferns and azaleas around,
    an incense urn and kneeling mats in front, a mossy stone lantern by the pool."""
    t = tl.Lot("pos", "kuil_jalan/kuil_d", tinggi=LY + 12, lebar=9, dalam=9, palet="jalan")
    ground(t, top="minecraft:grass_block", alt="minecraft:moss_block", alt_chance=0.2)
    clear(t, height=10)
    y = LY + 1
    rocks = ("minecraft:stone", "minecraft:andesite", "minecraft:mossy_cobblestone", "minecraft:cobblestone",
             "minecraft:tuff")
    # the outcrop: a lumpy ridge along the back, higher in the middle
    for x in range(9):
        for z in range(0, 4):
            h = 6 - abs(x - 4) // 2 - (2 if z == 3 else 0) - (1 if z == 2 else 0) - (1 if t.rng.random() < 0.3 else 0)
            if x in (0, 8):
                h -= 2
            h = max(1, h)
            for yy in range(y, y + h):
                t.put(x, yy, z, rocks[int(t.rng.random() * len(rocks))])
            if t.rng.random() < 0.55:
                t.put(x, y + h, z, "minecraft:moss_carpet")
    # the niche (3 wide, 3 high, 2 deep) and the god
    t.air(3, y, 2, 5, y + 2, 3)
    t.put(4, y, 1, "minecraft:polished_andesite")
    t.put(4, y + 1, 1, "minecraft:chiseled_stone_bricks")
    slab(t, 4, y, 2, "minecraft:polished_andesite_slab")
    candle(t, 3, y, 2, 3, "white")
    candle(t, 5, y, 2, 2, "white")
    lantern(t, 4, y + 2, 2, hanging=True)
    # awning on two posts over the niche mouth
    for x in (2, 6):
        for yy in range(y, y + 3):
            t.put(x, yy, 4, "minecraft:dark_oak_fence")
    for x in range(2, 7):
        stairs(t, x, y + 3, 4, "minecraft:deepslate_tile_stairs", "north")
    t.put(3, y, 4, "minecraft:red_carpet")
    t.put(5, y, 4, "minecraft:red_carpet")
    # spring pool sunk into the yard floor, urn, lantern, plants
    for (x, z) in ((6, 5), (7, 5), (7, 6), (6, 6)):
        t.put(x, LY - 1, z, "minecraft:mossy_cobblestone")
        t.put(x, LY, z, "minecraft:water", {"level": "0"})
    t.put(7, y, 5, "minecraft:lily_pad")
    for (x, z) in ((5, 5), (5, 6), (6, 7), (7, 7), (8, 5), (8, 6), (6, 4), (7, 4)):
        t.put(x, LY, z, "minecraft:mossy_cobblestone")
    t.put(4, y, 7, "minecraft:cauldron")
    stone_lantern(t, 8, y, 7, base="minecraft:mossy_stone_bricks", wall="minecraft:mossy_stone_brick_wall",
                  cap="minecraft:mossy_stone_brick_slab")
    for (x, z) in ((0, 5), (1, 7), (2, 6), (8, 8)):
        t.put(x, y, z, "minecraft:fern" if (x + z) % 2 else "minecraft:azalea")
    t.put(0, y, 8, "minecraft:flowering_azalea")
    t.titik("kerja:dupa", 4, y, 8, "north")
    t.titik("dupa", 4, y, 5, "north")
    t.titik("obrol", 1, y, 6, "east")
    t.jangkar(4.5, y, 7.5, "pos/kuil_jalan", protect="8", situs="pos/kuil_jalan")
    t.simpan()
    return t.id


def inn(t, x0, z0, x1, z1, rooms=2, beds_per_room=1, storeys=1):
    """A road inn: common room in front (tables, counter), `rooms` bedrooms behind, gable roof."""
    wall_h = 4 if storeys == 1 else 8
    inner, _ = house(t, x0, z0, x1, z1, wall_h=wall_h, door_side="south")
    ix0, iz0, ix1, iz1 = inner
    y = LY + 1
    split = (iz0 + iz1) // 2
    # partition between common room and bedrooms (with doorway)
    for x in range(ix0, ix1 + 1):
        for yy in range(y, y + 3):
            t.put(x, yy, split, "minecraft:spruce_planks")
    room_w = (ix1 - ix0 + 1) // rooms
    for r in range(rooms):
        rx0 = ix0 + r * room_w
        rx1 = ix1 if r == rooms - 1 else rx0 + room_w - 1
        if r < rooms - 1:
            for z in range(iz0, split):
                for yy in range(y, y + 3):
                    t.put(rx1 + 1 if rx1 + 1 <= ix1 else rx1, yy, z, "minecraft:spruce_planks")
        dx = (rx0 + rx1) // 2
        door(t, dx, y, split, "south", hinge="right")
        for b in range(beds_per_room):
            bx = rx0 + b * 2
            if bx + 0 <= rx1:
                bed(t, bx, y, iz0 + 1, "north", ("red", "blue", "green", "yellow")[(r + b) % 4])
                t.titik("kasur", bx, y, iz0 + 1, "north")
        chest(t, rx1, y, iz0, "south")
        t.titik("peti_milik", rx1, y, iz0, "south", {"kamar": str(r)})
        hanging_lantern(t, dx, y + 3, iz0 + 1)
    # common room: counter, tables and chairs
    for x in range(ix0, ix0 + 3):
        t.put(x, y, split + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.put(ix0 + 1, y + 1, split + 1, "minecraft:flower_pot")
    t.titik("kerja:teko", ix0 + 1, y, split + 2, "north")
    tx = ix1 - 2
    for tz in range(split + 2, iz1, 3):
        table(t, tx, y, tz)
        chair(t, tx - 1, y, tz, "east")
        chair(t, tx + 1, y, tz, "west")
        t.titik("meja_makan", tx, y, tz, "south")
        t.titik("kursi", tx - 1, y, tz, "east")
        t.titik("kursi", tx + 1, y, tz, "west")
    # dressing (detail pass D-W): rug runner from the door, wall shelf of jars and candles on the west wall, a lantern
    # and guest ledger on the counter, a rug and bedside stool in every bedroom; only on cells still empty
    def kosong(x, yy, z):
        return t.get(x, yy, z) in (None, "minecraft:air")
    cx = (ix0 + ix1) // 2
    for z in range(split + 1, iz1 + 1):
        if kosong(cx, y, z):
            t.put(cx, y, z, "minecraft:red_carpet" if z not in (split + 1, iz1) else "minecraft:yellow_carpet")
    for z in range(split + 3, iz1):
        if kosong(ix0, y + 1, z) and kosong(ix0, y + 2, z):
            slab(t, ix0, y + 1, z, "minecraft:spruce_slab", "top")
            if z % 3 == 0:
                candle(t, ix0, y + 2, z, 3, "white")
            else:
                t.put(ix0, y + 2, z, "minecraft:decorated_pot" if z % 3 == 1 else "minecraft:flower_pot",
                      {"facing": "east", "cracked": "false", "waterlogged": "false"} if z % 3 == 1 else None)
    if kosong(ix0 + 2, y + 1, split + 1):
        lantern(t, ix0 + 2, y + 1, split + 1)
    if kosong(ix0, y + 1, split + 1):
        t.put(ix0, y + 1, split + 1, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    for r in range(rooms):
        rx0 = ix0 + r * room_w
        rx1 = ix1 if r == rooms - 1 else rx0 + room_w - 1
        dx = (rx0 + rx1) // 2
        for z in (split - 1, split - 2):
            if z > iz0 + 2 and kosong(dx, y, z):
                t.put(dx, y, z, "minecraft:%s_carpet" % ("brown", "light_gray", "cyan")[r % 3])
        if kosong(rx0, y, split - 1) and rx0 != dx:
            log(t, rx0, y, split - 1, "minecraft:stripped_spruce_log")
            candle(t, rx0, y + 1, split - 1, 1, "yellow")
    if storeys > 1:
        # upper floor gallery: floor slab ring and ladder
        for x in range(ix0, ix1 + 1):
            for z in range(iz0, iz1 + 1):
                if z <= split:
                    t.put(x, y + 4, z, "minecraft:spruce_planks")
        t.put(ix1, y, split + 1, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
        for yy in range(y, y + 5):
            t.put(ix1, yy, split + 1, "minecraft:ladder", {"facing": "north", "waterlogged": "false"})
        for b in range(0, ix1 - ix0 - 1, 3):
            bed(t, ix0 + b, y + 5, iz0 + 1, "north", "white")
            t.titik("kasur", ix0 + b, y + 5, iz0 + 1, "north")


def warung(t, x0, z0, x1, z1):
    """An open food stall: roof on posts, cooking counter (smoker), tables and benches, hanging lanterns."""
    y = LY + 1
    t.air(x0, y, z0, x1, y + 9, z1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.put(x, LY, z, "minecraft:spruce_planks" if (x + z) % 7 else "minecraft:stripped_spruce_wood",
                  None if (x + z) % 7 else {"axis": "y"})
    for x in (x0, x1):
        for z in range(z0, z1 + 1, 4):
            for yy in range(y, y + 4):
                log(t, x, yy, z, "minecraft:stripped_spruce_log")
    for z in (z0, z1):
        for x in range(x0, x1 + 1, 4):
            for yy in range(y, y + 4):
                log(t, x, yy, z, "minecraft:stripped_spruce_log")
    roof_over(t, x0, y + 4, z0, x1, z1, along="x")
    # back counter and stove
    for x in range(x0 + 1, x1):
        t.put(x, y, z0 + 1, "minecraft:spruce_planks")
        slab(t, x, y + 1, z0 + 1, "minecraft:smooth_stone_slab")
    t.put(x0 + 2, y, z0 + 1, "minecraft:smoker", {"facing": "south", "lit": "false"})
    t.put(x0 + 3, y + 1, z0 + 1, "minecraft:cauldron")
    t.put(x1 - 2, y + 1, z0 + 1, "minecraft:flower_pot")
    barrel(t, x1 - 1, y, z0 + 1)
    t.titik("kerja:kuali", x0 + 3, y, z0 + 2, "north")
    t.titik("kerja:teko", x1 - 2, y, z0 + 2, "north")
    # tables
    for tx in range(x0 + 2, x1 - 1, 4):
        for tz in range(z0 + 4, z1, 3):
            table(t, tx, y, tz)
            chair(t, tx, y, tz - 1, "south")
            chair(t, tx, y, tz + 1, "north")
            t.titik("meja_makan", tx, y, tz, "south")
            t.titik("kursi", tx, y, tz - 1, "south")
    for x in range(x0 + 2, x1, 4):
        hanging_lantern(t, x, y + 4, (z0 + z1) // 2)


def kandang(t, x0, z0, x1, z1):
    """A horse stable: roofed stalls with hay, troughs and a tack wall."""
    y = LY + 1
    t.air(x0, y, z0, x1, y + 8, z1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.put(x, LY, z, "minecraft:coarse_dirt" if t.rng.random() < 0.6 else "minecraft:packed_mud")
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1):
            for yy in range(y, y + 4):
                log(t, x, yy, z, "minecraft:spruce_log")
    for x in range(x0, x1 + 1):
        t.put(x, y, z0, "minecraft:spruce_planks")
        t.put(x, y + 1, z0, "minecraft:spruce_planks")
    roof_over(t, x0, y + 4, z0, x1, z1, along="x", roof="minecraft:spruce_stairs", slab_r="minecraft:spruce_slab",
              full="minecraft:spruce_planks")
    stalls = list(range(x0 + 1, x1, 4))
    for i, sx in enumerate(stalls):
        for z in range(z0 + 1, z1 - 1):
            if sx + 3 <= x1:
                t.put(sx + 3, y, z, "minecraft:spruce_fence")
        log(t, sx + 1, y, z0 + 1, "minecraft:hay_block")
        t.put(sx, y, z0 + 1, "minecraft:water_cauldron", {"level": "3"})
        t.put(sx + 2, y + 1, z0, "minecraft:spruce_trapdoor",
              {"facing": "south", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
        t.titik("kerja:sapu", sx + 1, y, z1 - 1, "north")
    for x in range(x0 + 1, x1, 5):
        log(t, x, y, z1 + 1 if z1 + 1 < t.size[2] else z1, "minecraft:hay_block")


def sumur(t, cx, cz):
    """A roofed well: stone ring, water, windlass posts and roof."""
    y = LY + 1
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if dx == 0 and dz == 0:
                for yy in range(LY - 3, LY + 1):
                    t.put(cx, yy, cz, "minecraft:water", {"level": "0"})
            else:
                t.put(cx + dx, y, cz + dz, "minecraft:cobblestone_wall" if (dx + dz) % 2 else "minecraft:mossy_cobblestone")
                t.put(cx + dx, LY, cz + dz, "minecraft:cobblestone")
    for dx in (-1, 1):
        log(t, cx + dx, y + 1, cz, "minecraft:spruce_log")
        log(t, cx + dx, y + 2, cz, "minecraft:spruce_log")
    log(t, cx, y + 3, cz, "minecraft:stripped_spruce_log", axis="x")
    log(t, cx - 1, y + 3, cz, "minecraft:spruce_log")
    log(t, cx + 1, y + 3, cz, "minecraft:spruce_log")
    t.put(cx, y + 2, cz, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    for dz, face in ((-1, "south"), (1, "north")):
        for dx in range(-2, 3):
            stairs(t, cx + dx, y + 4 if dz == 0 else y + 3, cz + dz * 1, "minecraft:dark_oak_stairs", face)
    for dx in range(-2, 3):
        slab(t, cx + dx, y + 4, cz, "minecraft:dark_oak_slab")


def papan(t, x, z, facing="south"):
    """A notice board: two posts, plank board, roof slab (the text is a client billboard)."""
    y = LY + 1
    for dx in (0, 2):
        log(t, x + dx, y, z, "minecraft:spruce_log")
        log(t, x + dx, y + 1, z, "minecraft:spruce_log")
        log(t, x + dx, y + 2, z, "minecraft:spruce_log")
    t.put(x + 1, y + 1, z, "minecraft:spruce_planks")
    t.put(x + 1, y + 2, z, "minecraft:spruce_planks")
    t.put(x + 1, y + 1, z + (1 if facing == "south" else -1), "minecraft:spruce_wall_sign",
          {"facing": facing, "waterlogged": "false"})
    for dx in range(-1, 4):
        slab(t, x + dx, y + 3, z, "minecraft:dark_oak_slab")


def bangku(t, x, z, n=3, facing="south"):
    for i in range(n):
        stairs(t, x + i, LY + 1, z, "minecraft:spruce_stairs", facing)


def singgah():
    """Rest post (38.C.6.3) 40 x 40: fenced yard with a gate to the road (south), a small inn with two rooms, a food
    stall, a horse stable, a roofed well, a notice board with the guild's mission copy, a traveller's bench under a tree,
    lantern posts along the paths."""
    t = tl.Lot("pos", "singgah/singgah_a", tinggi=LY + 18, lebar=40, dalam=40, palet="jalan")
    ground(t)
    clear(t, height=12)
    # paths: gate (south middle) to the well, then to each building
    path_line(t, [(20, 39), (20, 24), (20, 16)])
    path_line(t, [(20, 22), (10, 22), (10, 16)])
    path_line(t, [(20, 22), (30, 22), (30, 15)])
    path_line(t, [(20, 26), (31, 26)])
    # fence around the yard with the gate gap
    fence_line(t, 0, 0, 39, 0, post_every=6)
    fence_line(t, 0, 0, 0, 39, post_every=6)
    fence_line(t, 39, 0, 39, 39, post_every=6)
    fence_line(t, 0, 39, 17, 39, post_every=6)
    fence_line(t, 23, 39, 39, 39, post_every=6)
    for x in (17, 23):
        for yy in range(LY + 1, LY + 5):
            log(t, x, yy, 39, "minecraft:spruce_log")
    for x in range(16, 25):
        slab(t, x, LY + 5, 39, "minecraft:dark_oak_slab")
    lantern(t, 17, LY + 6, 39)
    lantern(t, 23, LY + 6, 39)
    inn(t, 2, 3, 17, 14, rooms=2, beds_per_room=2)
    warung(t, 23, 3, 36, 13)
    kandang(t, 26, 28, 37, 35)
    sumur(t, 20, 22)
    papan(t, 13, 34)
    tree(t, 6, LY + 1, 30, "oak", height=5, radius=3)
    bangku(t, 4, 33, 4, "north")
    for (x, z) in ((19, 30), (21, 34), (12, 22), (28, 22)):
        stone_lantern(t, x, LY + 1, z, base="minecraft:mossy_cobblestone", wall="minecraft:cobblestone_wall",
                      cap="minecraft:spruce_slab")
    t.titik("obrol", 5, LY + 1, 32, "north")
    t.titik("obrol", 7, LY + 1, 32, "north")
    t.titik("jaga", 20, LY + 1, 37, "south")
    t.titik("kerja:sapu", 18, LY + 1, 25, "north")
    t.jalur([(20, 38), (20, 24), (10, 22), (10, 16), (30, 22), (30, 15)])
    t.kotak("bangunan", 2, LY, 3, 17, LY + 12, 14, nama="penginapan")
    t.kotak("bangunan", 23, LY, 3, 36, LY + 10, 13, nama="warung")
    t.jangkar(20.5, LY + 1, 20.5, "pos/singgah", protect="24", situs="pos/singgah")
    t.jangkar(9.5, LY + 1, 12.5, "kotabesar/layanan", layanan="penginapan", peran="PEMILIK_PENGINAPAN")
    t.jangkar(29.5, LY + 1, 6.5, "kotabesar/layanan", layanan="rumah_makan", peran="KOKI")
    t.jangkar(14.5, LY + 1, 33.5, "kotabesar/layanan", layanan="papan_gilda", peran="PETUGAS_GILDA")
    t.jangkar(5.5, LY + 1, 33.5, "kota/kursi_pengembara")
    t.jangkar(14.5, LY + 1, 35.5, "kota/papan")
    t.simpan()
    return t.id


def bambu(t, x, z, h):
    """One bamboo stalk h tall: bare below, small leaves, large leaves on the top two."""
    for i in range(h):
        leaves = "large" if i >= h - 2 else "small" if i >= h - 4 else "none"
        t.put(x, LY + 1 + i, z, "minecraft:bamboo", {"age": "1" if i < h - 2 else "0", "leaves": leaves, "stage": "0"})


def singgah_kolam():
    """Rest post variant b (detail pass D-W) 40 x 40, "Singgah Tepi Kolam": a stone gate with a tiled cap, a two-storey
    inn (west), a tea pavilion standing in a lotus pond reached by a zigzag bridge (north-east, the food service), a
    stable (south-east), a well, a bamboo grove with a meditation stone, the guild board and a bench under a tree."""
    t = tl.Lot("pos", "singgah/singgah_b", tinggi=LY + 22, lebar=40, dalam=40, palet="jalan")
    ground(t)
    clear(t, height=16)
    y = LY + 1
    path_line(t, [(20, 39), (20, 24), (8, 20), (8, 18)])
    path_line(t, [(20, 24), (25, 24), (25, 18)])
    path_line(t, [(20, 30), (30, 30), (30, 25)], width=2)
    fence_line(t, 0, 0, 39, 0, post_every=6)
    fence_line(t, 0, 0, 0, 39, post_every=6)
    fence_line(t, 39, 0, 39, 39, post_every=6)
    fence_line(t, 0, 39, 16, 39, post_every=6)
    fence_line(t, 24, 39, 39, 39, post_every=6)
    # stone gate: two pillars, a lintel and a teal tiled cap
    for x in (17, 23):
        t.put(x, y, 39, "minecraft:chiseled_stone_bricks")
        for yy in range(y + 1, y + 5):
            t.put(x, yy, 39, "minecraft:stone_bricks")
    for x in range(17, 24):
        t.put(x, y + 5, 39, "minecraft:polished_andesite")
    for x in range(17, 24):
        slab(t, x, y + 6, 39, "minecraft:dark_prismarine_slab")
    stairs(t, 16, y + 6, 39, "minecraft:dark_prismarine_stairs", "east", half="top")  # curled cap ends
    stairs(t, 24, y + 6, 39, "minecraft:dark_prismarine_stairs", "west", half="top")
    hanging_lantern(t, 18, y + 5, 38, chain=0)
    hanging_lantern(t, 22, y + 5, 38, chain=0)
    # two-storey inn
    inn(t, 2, 3, 15, 16, rooms=2, beds_per_room=2, storeys=2)
    # lotus pond with stone banks
    p0x, p1x, p0z, p1z = 22, 37, 3, 15
    for x in range(p0x - 1, p1x + 2):
        for z in range(p0z - 1, p1z + 2):
            bank = x in (p0x - 1, p1x + 1) or z in (p0z - 1, p1z + 1)
            if bank:
                t.put(x, LY, z, "minecraft:mossy_cobblestone" if (x * 7 + z) % 3 == 0 else "minecraft:cobblestone")
                if (x + z) % 5 == 0:
                    slab(t, x, y, z, "minecraft:mossy_cobblestone_slab")
            else:
                t.put(x, LY - 2, z, "minecraft:clay" if (x + z) % 4 else "minecraft:sand")
                t.put(x, LY - 1, z, "minecraft:water", {"level": "0"})
                t.put(x, LY, z, "minecraft:water", {"level": "0"})
    # pavilion platform (27..33 x 6..11) and the zigzag bridge
    deck = set((x, z) for x in range(27, 34) for z in range(6, 12))
    bridge = [(25, z) for z in range(16, 12, -1)] + [(x, 13) for x in range(26, 30)] + [(29, 12)]
    for (x, z) in list(deck) + bridge:
        t.put(x, LY, z, "minecraft:spruce_planks")
        t.put(x, LY - 1, z, "minecraft:stripped_spruce_log", {"axis": "y"})
    allc = deck | set(bridge)
    for i, (x, z) in enumerate(bridge):
        # rails on the water sides of each run (the north-south run first, then the east-west run)
        side = [(x + 1, z), (x - 1, z)] if i < 4 else [(x, z + 1), (x, z - 1)]
        for (nx, nz) in side:
            if (nx, nz) not in allc and p0x <= nx <= p1x and p0z <= nz <= p1z:
                t.put(nx, LY, nz, "minecraft:stripped_spruce_log", {"axis": "y"})
                t.put(nx, y, nz, "minecraft:spruce_fence")
    rng = t.rng
    for x in range(p0x, p1x + 1):
        for z in range(p0z, p1z + 1):
            if (x, z) in allc or t.get(x, LY, z) != "minecraft:water" or t.get(x, y, z) not in (None, "minecraft:air"):
                continue
            if rng.random() < 0.13:
                t.put(x, y, z, "minecraft:lily_pad")
            elif rng.random() < 0.05:
                t.put(x, LY - 1, z, "minecraft:sea_pickle", {"pickles": "2", "waterlogged": "true"})
    # pavilion: lacquered posts, rails, teal hip roof, tea counter and two tables
    for (x, z) in ((27, 6), (30, 6), (33, 6), (27, 11), (33, 11), (27, 8), (33, 8)):
        for yy in range(y, y + 4):
            log(t, x, yy, z, "minecraft:stripped_crimson_stem")
    for x in range(28, 33):
        if x != 29:
            t.put(x, y, 11, "minecraft:spruce_fence")
    for z in (7, 9, 10):
        t.put(27, y, z, "minecraft:spruce_fence")
        t.put(33, y, z, "minecraft:spruce_fence")
    pal = t.pal
    t.pal = dict(pal, roof="minecraft:dark_prismarine_stairs", roof_slab="minecraft:dark_prismarine_slab",
                 roof_full="minecraft:dark_prismarine")
    t.roof_hip(27, y + 4, 6, 33, 11, overhang=1)
    t.pal = pal
    for x in range(28, 33):
        t.put(x, y, 7, "minecraft:spruce_planks" if x != 30 else "minecraft:barrel", {"facing": "up", "open": "false"}
              if x == 30 else None)
    t.put(28, y + 1, 7, "minecraft:cauldron")
    t.put(31, y + 1, 7, "minecraft:flower_pot")
    candle(t, 32, y + 1, 7, 2, "white")
    t.titik("kerja:teko", 29, y, 8, "north")
    for tx in (29, 31):
        table(t, tx, y, 9, top="minecraft:bamboo_pressure_plate", leg="minecraft:bamboo_fence")
        t.titik("meja_makan", tx, y, 9, "south")
    for (cx2, face) in ((28, "east"), (30, "west"), (32, "west")):
        chair(t, cx2, y, 9, face, bid="minecraft:bamboo_stairs")
        t.titik("kursi", cx2, y, 9, face)
    hanging_lantern(t, 29, y + 4, 9, chain=0)
    hanging_lantern(t, 31, y + 4, 9, chain=0)
    # stable, well, notice board
    kandang(t, 24, 26, 37, 33)
    sumur(t, 14, 26)
    papan(t, 13, 34)
    # bamboo grove with a meditation stone (south-west of the inn)
    for x in range(2, 11):
        for z in range(20, 29):
            if (x - 6) ** 2 + (z - 24) ** 2 <= 2:
                continue
            if rng.random() < 0.42:
                bambu(t, x, z, rng.randint(5, 9))
            elif rng.random() < 0.3:
                t.put(x, y, z, "minecraft:fern" if rng.random() < 0.6 else "minecraft:moss_carpet")
    t.put(6, LY, 24, "minecraft:moss_block")
    t.put(6, y, 24, "minecraft:mossy_stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    t.put(5, y, 24, "minecraft:moss_carpet")
    t.put(7, y, 24, "minecraft:moss_carpet")
    t.titik("meditasi", 6, y, 24, "north")
    tree(t, 5, y, 36, "cherry", height=5, radius=2)
    bangku(t, 3, 33, 4, "north")
    for (x, z) in ((19, 34), (21, 28), (12, 21), (27, 22)):
        stone_lantern(t, x, y, z, base="minecraft:mossy_stone_bricks", wall="minecraft:mossy_stone_brick_wall",
                      cap="minecraft:dark_prismarine_slab")
    t.titik("obrol", 4, y, 32, "north")
    t.titik("obrol", 6, y, 32, "north")
    t.titik("jaga", 20, y, 37, "south")
    t.titik("kerja:sapu", 18, y, 25, "north")
    t.titik("kerja:pancing", 24, y, 16, "north")
    t.jalur([(20, 38), (20, 24), (8, 20), (8, 18), (25, 24), (25, 17), (29, 12)])
    t.kotak("bangunan", 2, LY, 3, 15, LY + 18, 16, nama="penginapan")
    t.kotak("bangunan", 26, LY, 5, 34, LY + 8, 12, nama="paviliun_teh")
    t.jangkar(20.5, y, 22.5, "pos/singgah", protect="24", situs="pos/singgah")
    t.jangkar(5.5, y, 13.5, "kotabesar/layanan", layanan="penginapan", peran="PEMILIK_PENGINAPAN")
    t.jangkar(30.5, y, 9.5, "kotabesar/layanan", layanan="rumah_makan", peran="KOKI")
    t.jangkar(14.5, y, 33.5, "kotabesar/layanan", layanan="papan_gilda", peran="PETUGAS_GILDA")
    t.jangkar(4.5, y, 33.5, "kota/kursi_pengembara")
    t.jangkar(14.5, y, 35.5, "kota/papan")
    t.simpan()
    return t.id


def cakram(t, cx, cz, r=5):
    """A teleport disc plaza (38.B.4.3 stasiun formasi): jade ring, gold rune spokes, four lantern pillars."""
    y = LY
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = (dx * dx + dz * dz) ** 0.5
            if d <= r + 0.4:
                if r - 1 < d:
                    b = "minecraft:prismarine_bricks"
                elif dx == 0 or dz == 0 or abs(dx) == abs(dz):
                    b = "minecraft:gold_block" if d > 1 else "minecraft:chiseled_quartz_block"
                else:
                    b = "minecraft:smooth_quartz"
                t.put(cx + dx, y, cz + dz, b)
    for dx, dz in ((-r - 1, 0), (r + 1, 0), (0, -r - 1), (0, r + 1)):
        t.put(cx + dx, y + 1, cz + dz, "minecraft:quartz_pillar", {"axis": "y"})
        t.put(cx + dx, y + 2, cz + dz, "minecraft:quartz_pillar", {"axis": "y"})
        t.put(cx + dx, y + 3, cz + dz, "minecraft:sea_lantern")


def gudang(t, x0, z0, x1, z1):
    """A caravan warehouse: stone-and-timber shed, stacked barrels and crates, a wide door."""
    inner, _ = house(t, x0, z0, x1, z1, wall_h=5, door_side="south", windows=False, wall="minecraft:spruce_planks",
                     along="x")
    ix0, iz0, ix1, iz1 = inner
    y = LY + 1
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz0 + 2):
            for yy in range(y, y + 1 + (x + z) % 3):
                barrel(t, x, yy, z, "up")
    for x in range(ix0, ix1 + 1, 2):
        chest(t, x, y, iz1, "north")
    t.titik("kerja:rak", (ix0 + ix1) // 2, y, iz0 + 2, "north")


def bengkel(t, x0, z0, x1, z1):
    """A farrier's forge: open shed, anvil, blast furnace, grindstone, horseshoe rack."""
    y = LY + 1
    t.air(x0, y, z0, x1, y + 8, z1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.put(x, LY, z, "minecraft:cobblestone" if (x * z) % 5 else "minecraft:gravel")
    for x in (x0, x1):
        for z in (z0, z1):
            for yy in range(y, y + 4):
                log(t, x, yy, z, "minecraft:spruce_log")
    for x in range(x0, x1 + 1):
        for yy in range(y, y + 3):
            t.put(x, yy, z0, "minecraft:cobblestone")
    roof_over(t, x0, y + 4, z0, x1, z1, along="x")
    t.put(x0 + 1, y, z0 + 1, "minecraft:blast_furnace", {"facing": "south", "lit": "false"})
    t.put(x0 + 3, y, z0 + 2, "minecraft:anvil", {"facing": "east"})
    t.put(x1 - 1, y, z0 + 1, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    t.put(x1 - 2, y, z0 + 1, "minecraft:smithing_table")
    t.put(x0 + 2, y + 1, z0, "minecraft:iron_bars")
    t.titik("kerja:palu", x0 + 3, y, z0 + 3, "north")


def singgah_besar():
    """Great rest post (38.C.6.3) 64 x 64 in four 32 x 32 lots: NW two-storey inn, NE stall + guild board,
    SW stable + farrier, SE teleport disc + caravan warehouse. Gate to the road at the south seam (x 32)."""
    ids = {}
    for part in ("nw", "ne", "sw", "se"):
        t = tl.Lot("pos", "singgah_besar/%s" % part, tinggi=LY + 24, lebar=32, dalam=32, palet="jalan")
        ground(t)
        clear(t, height=14)
        north = part[0] == "n"
        west = part[1] == "w"
        # outer fence on the outer sides only
        if north:
            fence_line(t, 0, 0, 31, 0, post_every=6)
        if west:
            fence_line(t, 0, 0, 0, 31, post_every=6)
        else:
            fence_line(t, 31, 0, 31, 31, post_every=6)
        if not north:
            if west:
                fence_line(t, 0, 31, 27, 31, post_every=6)
                for yy in range(LY + 1, LY + 6):
                    log(t, 27, yy, 31, "minecraft:spruce_log")
                lantern(t, 27, LY + 6, 31)
            else:
                fence_line(t, 4, 31, 31, 31, post_every=6)
                for yy in range(LY + 1, LY + 6):
                    log(t, 4, yy, 31, "minecraft:spruce_log")
                lantern(t, 4, LY + 6, 31)
        # the main avenue at the inner seam
        if west:
            path_line(t, [(30, 0), (30, 31)], width=3)
        else:
            path_line(t, [(1, 0), (1, 31)], width=3)
        if part == "nw":
            inn(t, 3, 3, 24, 18, rooms=3, beds_per_room=2, storeys=2)
            tree(t, 6, LY + 1, 25, "oak", height=5, radius=3)
            bangku(t, 9, 27, 4, "north")
            t.jalur([(29, 2), (29, 22), (14, 22), (14, 20)])
            t.titik("obrol", 10, LY + 1, 26, "north")
            t.kotak("bangunan", 3, LY, 3, 24, LY + 16, 18, nama="penginapan_besar")
            t.jangkar(13.5, LY + 1, 16.5, "kotabesar/layanan", layanan="penginapan", peran="PEMILIK_PENGINAPAN")
            t.jangkar(10.5, LY + 1, 27.5, "kota/kursi_pengembara")
        elif part == "ne":
            warung(t, 6, 3, 27, 15)
            papan(t, 8, 24)
            papan(t, 16, 24)
            sumur(t, 23, 24)
            t.jalur([(2, 2), (2, 20), (15, 20)])
            t.kotak("bangunan", 6, LY, 3, 27, LY + 10, 15, nama="warung_besar")
            t.jangkar(15.5, LY + 1, 6.5, "kotabesar/layanan", layanan="rumah_makan", peran="KOKI")
            t.jangkar(9.5, LY + 1, 26.5, "kotabesar/layanan", layanan="papan_gilda", peran="PETUGAS_GILDA")
            t.jangkar(17.5, LY + 1, 26.5, "kota/papan")
        elif part == "sw":
            kandang(t, 2, 3, 22, 12)
            bengkel(t, 4, 17, 14, 25)
            for x in range(17, 26, 3):
                log(t, x, LY + 1, 22, "minecraft:spruce_log")
                t.put(x, LY + 2, 22, "minecraft:spruce_fence")
            t.titik("kerja:sapu", 12, LY + 1, 14, "north")
            t.jalur([(29, 0), (29, 28), (10, 28), (10, 26)])
            t.jangkar(9.5, LY + 1, 21.5, "kotabesar/layanan", layanan="pandai_kuda", peran="PANDAI_DESA")
            t.jangkar(12.5, LY + 1, 8.5, "kotabesar/layanan", layanan="kandang", peran="KULI")
        else:  # se
            cakram(t, 16, 10, r=5)
            gudang(t, 6, 19, 27, 27)
            stone_lantern(t, 8, LY + 1, 3)
            stone_lantern(t, 24, LY + 1, 3)
            t.titik("jaga", 16, LY + 1, 17, "north")
            t.jalur([(2, 0), (2, 16), (16, 16), (16, 28)])
            t.kotak("bangunan", 6, LY, 19, 27, LY + 12, 27, nama="gudang_kafilah")
            t.jangkar(16.5, LY + 1, 10.5, "kotabesar/cakram")
            t.jangkar(16.5, LY + 1, 18.5, "pos/singgah_besar", protect="40", situs="pos/singgah_besar")
            t.jangkar(14.5, LY + 1, 24.5, "kotabesar/layanan", layanan="gudang_kafilah", peran="PEDAGANG_KELILING")
        t.simpan()
        ids[part] = t.id
    return ids


def dagang():
    """Caravanserai (38.C.6.3) 44 x 44: a square ring of arcades and storerooms around an inner court with four merchant
    stalls, a well and tie posts; a two-storey gatehouse to the road (south)."""
    t = tl.Lot("pos", "dagang/dagang_a", tinggi=LY + 22, lebar=44, dalam=44, palet="jalan")
    ground(t, top="minecraft:packed_mud", alt="minecraft:coarse_dirt", alt_chance=0.3)
    clear(t, height=14)
    y = LY + 1
    o0, o1 = 2, 41          # outer wall
    i0, i1 = 10, 33         # inner court
    for x in range(o0, o1 + 1):
        for z in range(o0, o1 + 1):
            ring = not (i0 < x < i1 and i0 < z < i1)
            if ring:
                t.put(x, LY, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:stone_bricks")
    # outer wall: mud brick with a timber band
    for x in range(o0, o1 + 1):
        for z in range(o0, o1 + 1):
            if x in (o0, o1) or z in (o0, o1):
                for yy in range(y, y + 6):
                    if yy == y + 4:
                        log(t, x, yy, z, "minecraft:stripped_spruce_log", axis="x" if z in (o0, o1) else "z")
                    else:
                        t.put(x, yy, z, "minecraft:mud_bricks" if yy > y else "minecraft:cobblestone")
    # inner arcade: pillars every 3 on the court edge, flat roof over the ring
    for x in range(i0, i1 + 1):
        for z in range(i0, i1 + 1):
            if (x in (i0, i1) or z in (i0, i1)) and (x % 3 == 1 or z % 3 == 1) and (x in (i0, i1)) != (z in (i0, i1)) or \
                    (x in (i0, i1) and z in (i0, i1)):
                for yy in range(y, y + 5):
                    log(t, x, yy, z, "minecraft:spruce_log")
    for x in range(o0, o1 + 1):
        for z in range(o0, o1 + 1):
            if not (i0 < x < i1 and i0 < z < i1):
                t.put(x, y + 5, z, "minecraft:spruce_planks")
                if x in (o0, o1) or z in (o0, o1) or x in (i0, i1) or z in (i0, i1):
                    slab(t, x, y + 6, z, "minecraft:mud_brick_slab")
    # eaves toward the court and a crenellated parapet outside
    for x in range(i0, i1 + 1):
        stairs(t, x, y + 5, i0, "minecraft:dark_oak_stairs", "north")
        stairs(t, x, y + 5, i1, "minecraft:dark_oak_stairs", "south")
    for z in range(i0 + 1, i1):
        stairs(t, i0, y + 5, z, "minecraft:dark_oak_stairs", "west")
        stairs(t, i1, y + 5, z, "minecraft:dark_oak_stairs", "east")
    for x in range(o0, o1 + 1):
        for z in (o0, o1):
            if x % 2 == 0:
                t.put(x, y + 7, z, "minecraft:mud_brick_wall")
    for z in range(o0, o1 + 1):
        for x in (o0, o1):
            if z % 2 == 0:
                t.put(x, y + 7, z, "minecraft:mud_brick_wall")
    # two watch towers on the back corners with hip roofs and lanterns
    pal = t.pal
    t.pal = dict(pal, roof="minecraft:dark_oak_stairs", roof_slab="minecraft:dark_oak_slab",
                 roof_full="minecraft:dark_oak_planks")
    for (tx, tz) in ((o0, o0), (o1 - 3, o0)):
        for x in range(tx, tx + 4):
            for z in range(tz, tz + 4):
                for yy in range(y + 6, y + 11):
                    edge = x in (tx, tx + 3) or z in (tz, tz + 3)
                    if edge:
                        corner = x in (tx, tx + 3) and z in (tz, tz + 3)
                        if corner:
                            log(t, x, yy, z, "minecraft:spruce_log")
                        elif yy == y + 8:
                            trapdoor_window(t, x, yy, z, "north" if z == tz else "south" if z == tz + 3 else
                                            "west" if x == tx else "east")
                        else:
                            t.put(x, yy, z, "minecraft:mud_bricks")
                    elif yy == y + 6:
                        t.put(x, yy, z, "minecraft:spruce_planks")
                    else:
                        t.put(x, yy, z, "minecraft:air")
        t.roof_hip(tx, y + 11, tz, tx + 3, tz + 3)
        hanging_lantern(t, tx + 1, y + 11, tz + 1)
    t.pal = pal
    # storerooms on the ring (walls between bays), barrels and chests
    for z in range(o0 + 1, o1, 8):
        for x in list(range(o0 + 1, i0)) + list(range(i1 + 1, o1)):
            for yy in range(y, y + 4):
                t.put(x, yy, z, "minecraft:spruce_planks")
    for z in range(o0 + 2, o1 - 1, 4):
        barrel(t, o0 + 1, y, z)
        barrel(t, o1 - 1, y, z)
        chest(t, o0 + 2, y, z, "east")
        chest(t, o1 - 2, y, z, "west")
    # merchant stalls in the court (striped awnings)
    stalls = ((14, 14), (25, 14), (14, 25), (25, 25))
    colours = ("red", "yellow", "cyan", "orange")
    for i, (sx, sz) in enumerate(stalls):
        for dx in range(5):
            t.put(sx + dx, y, sz, "minecraft:spruce_planks")
            slab(t, sx + dx, y + 1, sz, "minecraft:spruce_slab")
            t.put(sx + dx, y + 3, sz, "minecraft:%s_wool" % colours[i] if dx % 2 == 0 else "minecraft:white_wool")
            t.put(sx + dx, y + 3, sz - 1, "minecraft:%s_wool" % colours[i] if dx % 2 == 0 else "minecraft:white_wool")
        for dx in (0, 4):
            t.put(sx + dx, y + 1, sz - 1, "minecraft:spruce_fence")
            t.put(sx + dx, y + 2, sz - 1, "minecraft:spruce_fence")
        barrel(t, sx + 1, y, sz - 1)
        t.put(sx + 3, y + 2, sz, "minecraft:flower_pot")
        t.titik("lapak", sx + 2, y, sz + 1, "north", {"nomor": str(i + 1)})
    sumur(t, 21, 21)
    for x in (12, 31):
        for z in (19, 24):
            log(t, x, y, z, "minecraft:spruce_log")
            t.put(x, y + 1, z, "minecraft:spruce_fence")
    # dressing (detail pass D-W): troughs and hay at the tie posts, a guest dormitory on the north ring (bedrolls,
    # rugs, stools with candles), a caravan kitchen in its east end, and stacked cargo in the court corners
    for x in (11, 32):
        log(t, x, y, 20, "minecraft:hay_block")
        t.put(x, y, 21, "minecraft:water_cauldron", {"level": "3"})
        t.put(x, y, 22, "minecraft:water_cauldron", {"level": "3"})
        log(t, x, y, 23, "minecraft:hay_block")
    beds = ("white", "light_gray", "brown", "white", "orange", "light_gray", "brown")
    for i, bx in enumerate(range(12, 31, 3)):
        bed(t, bx, y, o0 + 2, "north", beds[i % len(beds)])
        t.titik("kasur", bx, y, o0 + 2, "north")
        t.put(bx, y, o0 + 3, "minecraft:%s_carpet" % ("brown", "red")[i % 2])
        if bx + 1 < 31:
            log(t, bx + 1, y, o0 + 1, "minecraft:stripped_spruce_log")
            candle(t, bx + 1, y + 1, o0 + 1, 1 + i % 3, "white")
    for x in range(14, 31, 6):
        hanging_lantern(t, x, y + 5, o0 + 5)
    t.put(33, y, o0 + 1, "minecraft:smoker", {"facing": "south", "lit": "false"})
    t.put(34, y, o0 + 1, "minecraft:water_cauldron", {"level": "3"})
    barrel(t, 35, y, o0 + 1)
    barrel(t, 36, y, o0 + 1, "south")
    t.put(36, y + 1, o0 + 1, "minecraft:flower_pot")
    table(t, 34, y, o0 + 5)
    chair(t, 33, y, o0 + 5, "east")
    chair(t, 35, y, o0 + 5, "west")
    t.titik("kerja:kuali", 34, y, o0 + 2, "north")
    t.titik("meja_makan", 34, y, o0 + 5, "south")
    for (cx2, cz2) in ((12, 12), (31, 12), (12, 31), (31, 31)):
        barrel(t, cx2, y, cz2, "north")
        barrel(t, cx2 + (1 if cx2 < 20 else -1), y, cz2, "up")
        barrel(t, cx2, y + 1, cz2, "up")
        t.put(cx2, y, cz2 + (1 if cz2 < 20 else -1), "minecraft:chest",
              {"facing": "south" if cz2 < 20 else "north", "type": "single", "waterlogged": "false"})
        t.put(cx2 + (1 if cx2 < 20 else -1), y + 1, cz2, "minecraft:%s_carpet" % ("yellow", "cyan")[cx2 < 20])
    # gatehouse (south): arch through the outer ring, upper room with lanterns
    for x in range(18, 26):
        for z in range(o1 - 1, o1 + 1):
            for yy in range(y, y + 4):
                t.put(x, yy, z, "minecraft:air")
    for x in (17, 26):
        for yy in range(y, y + 9):
            t.put(x, yy, o1, "minecraft:mud_bricks")
    for x in range(17, 27):
        for yy in range(y + 6, y + 9):
            t.put(x, yy, o1, "minecraft:mud_bricks" if yy != y + 7 or x % 3 else "minecraft:spruce_trapdoor")
    pal = t.pal
    t.pal = dict(pal, roof="minecraft:dark_oak_stairs", roof_slab="minecraft:dark_oak_slab",
                 roof_full="minecraft:dark_oak_planks")
    t.roof_hip(17, y + 9, o1 - 3, 26, o1)
    t.pal = pal
    hanging_lantern(t, 20, y + 4, o1)
    hanging_lantern(t, 23, y + 4, o1)
    for x in (15, 28):
        stone_lantern(t, x, y, 43)
    t.jalur([(21, 43), (21, 30), (21, 23), (16, 16), (27, 16)])
    t.titik("jaga", 18, y, 42, "south")
    t.titik("jaga", 25, y, 42, "south")
    t.titik("obrol", 21, y, 26, "north")
    t.kotak("bangunan", o0, LY, o0, o1, LY + 12, o1, nama="karavanserai")
    t.jangkar(21.5, y, 30.5, "pos/dagang", protect="32", situs="pos/dagang")
    t.jangkar(16.5, y, 15.5, "kotabesar/layanan", layanan="dagang_wilayah", peran="PEDAGANG_KELILING")
    t.jangkar(6.5, y, 22.5, "kotabesar/layanan", layanan="penginapan", peran="PEMILIK_PENGINAPAN")
    t.jangkar(37.5, y, 22.5, "kotabesar/layanan", layanan="papan_gilda", peran="PETUGAS_GILDA")
    t.simpan()
    return t.id


def kemah_begal(v):
    """Bandit camp (38.C.6.3) 24 x 24 off the road: spiked palisade with a gap, three hide tents, an UNLIT campfire with
    log seats, loot crates, a weapon rack and a lookout platform. Not a safe place; the band comes from
    RogueCultivators when a player is near (through Sibuk)."""
    t = tl.Lot("pos", "kemah_begal/kemah_%s" % v, tinggi=LY + 12, lebar=24, dalam=24, palet="jalan")
    ground(t, top="minecraft:coarse_dirt" if v == "a" else "minecraft:podzol", alt="minecraft:grass_block",
           alt_chance=0.25)
    clear(t, height=9)
    y = LY + 1
    c = 12
    # palisade ring (sharpened logs, 3-4 tall) with a gap to the south-east
    for x in range(24):
        for z in range(24):
            d = ((x - c + 0.5) ** 2 + (z - c + 0.5) ** 2) ** 0.5
            if 10.0 <= d < 11.0 and not (x > 13 and z > 17):
                h = 3 + (x * 7 + z * 3) % 2
                for yy in range(y, y + h):
                    log(t, x, yy, z, "minecraft:spruce_log" if v == "a" else "minecraft:dark_oak_log")
                t.put(x, y + h, z, "minecraft:spruce_fence")
    # campfire (unlit) with log seats
    t.put(c, y, c, "minecraft:campfire", {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    for dx, dz, ax in ((-2, 0, "z"), (2, 0, "z"), (0, -2, "x"), (0, 2, "x")):
        log(t, c + dx, y, c + dz, "minecraft:stripped_spruce_log" if v == "a" else "minecraft:stripped_oak_log", axis=ax)
    # tents: A-frames of wool over fence poles
    for (tx, tz, col) in ((5, 5, "brown"), (15, 5, "white" if v == "a" else "gray"), (5, 15, "brown")):
        for dz in range(4):
            for h in range(3):
                t.put(tx + h, y + h, tz + dz, "minecraft:%s_wool" % col)
                t.put(tx + 4 - h, y + h, tz + dz, "minecraft:%s_wool" % col)
            t.put(tx + 2, y + 3, tz + dz, "minecraft:%s_wool" % col)
            if dz != 3:
                t.air(tx + 1, y, tz + dz, tx + 3, y + 1, tz + dz)
            if dz in (1, 2):
                t.put(tx + 2, y + 2, tz + dz, "minecraft:air")
        t.put(tx + 2, y, tz + 3, "minecraft:%s_wool" % col)
        t.put(tx + 1, y, tz + 1, "minecraft:%s_carpet" % ("brown" if col != "brown" else "black"))
        t.titik("kasur", tx + 2, y, tz + 1, "south")
    # loot and the rack
    chest(t, 17, y, 14, "west", loot="kultivator:pos/kemah_begal")
    barrel(t, 18, y, 13)
    barrel(t, 18, y + 1, 13)
    barrel(t, 17, y, 12)
    t.put(9, y, 18, "minecraft:grindstone", {"face": "floor", "facing": "east"})
    if v == "b":
        # the band's last catch: a caravan cart turned over by the stockade
        gerobak_rusak(t, 14, y, 15)
    for dz in range(3):
        t.put(10, y, 19 + dz, "minecraft:spruce_fence")
    # lookout platform (north-east)
    for (x, z) in ((18, 4), (20, 4), (18, 6), (20, 6)):
        for yy in range(y, y + 4):
            log(t, x, yy, z, "minecraft:spruce_log")
    for x in range(18, 21):
        for z in range(4, 7):
            t.put(x, y + 4, z, "minecraft:spruce_planks")
            if x in (18, 20) or z in (4, 6):
                t.put(x, y + 5, z, "minecraft:spruce_fence")
    for yy in range(y, y + 4):
        t.put(19, yy, 7, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    t.titik("jaga", 19, y + 5, 5, "south")
    t.titik("jaga", 16, y, 19, "south")
    t.titik("obrol", c - 2, y, c, "east")
    t.titik("obrol", c + 2, y, c, "west")
    t.jangkar(c + 0.5, y, c + 3.5, "pos/kemah_begal", protect="0", situs="pos/kemah_begal")
    t.simpan()
    return t.id


def gerobak_rusak(t, x, y, z):
    """An overturned caravan cart (east-west): plank bed on its side, two wheels (trapdoors), spilled goods."""
    for dx in range(3):
        t.put(x + dx, y, z, "minecraft:spruce_planks")
        slab(t, x + dx, y + 1, z, "minecraft:spruce_slab")
    t.put(x, y, z - 1, "minecraft:dark_oak_trapdoor", {"facing": "north", "half": "bottom", "open": "true",
                                                       "powered": "false", "waterlogged": "false"})
    t.put(x + 2, y + 1, z - 1, "minecraft:dark_oak_trapdoor", {"facing": "north", "half": "bottom", "open": "true",
                                                               "powered": "false", "waterlogged": "false"})
    t.put(x + 3, y, z, "minecraft:spruce_fence")
    barrel(t, x + 1, y, z + 1, "east")
    t.put(x + 2, y, z + 1, "minecraft:white_carpet")
    t.put(x - 1, y, z + 1, "minecraft:hay_block", {"axis": "x"})


def kemah_benteng():
    """Bandit camp variant c (detail pass D-W): a fortified hideout. A square stake palisade with a gate of fence gates,
    a roofed watchtower in the north-east corner with a ladder, lean-to tents of hides along the west wall with bed
    rolls, an UNLIT campfire with a cooking cauldron and log seats, a cage of iron bars beside a plundered caravan
    cart, a weapon rack with grindstone and chipped anvil, the band's black banner on a pole and the loot chest. Not
    a safe place; the band comes from RogueCultivators when a player is near (through Sibuk)."""
    t = tl.Lot("pos", "kemah_begal/kemah_c", tinggi=LY + 12, lebar=24, dalam=24, palet="jalan")
    ground(t, top="minecraft:coarse_dirt", alt="minecraft:podzol", alt_chance=0.3)
    clear(t, height=10)
    y = LY + 1
    for (x, z) in ((10, 20), (11, 20), (12, 20), (13, 20), (11, 19), (12, 19), (11, 21), (12, 21), (12, 12), (11, 13)):
        t.put(x, LY, z, "minecraft:gravel")
    # palisade: stakes 4 tall with sharpened fence tips, a two-wide gate on the south wall
    for i in range(2, 22):
        for (x, z) in ((i, 2), (i, 21), (2, i), (21, i)):
            if z == 21 and 11 <= x <= 12:
                continue
            h = 4 if (x + z) % 3 else 3
            for yy in range(y, y + h):
                log(t, x, yy, z, "minecraft:stripped_spruce_log" if (x * 3 + z) % 7 == 0 else "minecraft:spruce_log")
            t.put(x, y + h, z, "minecraft:spruce_fence")
    for x in (10, 13):
        for yy in range(y, y + 5):
            log(t, x, yy, 21, "minecraft:dark_oak_log")
        t.put(x, y + 5, 21, "minecraft:skeleton_skull", {"rotation": "8"})
    for x in (11, 12):
        t.put(x, y, 21, "minecraft:spruce_fence_gate", {"facing": "south", "in_wall": "false", "open": "true",
                                                        "powered": "false"})
    for x in range(10, 14):
        log(t, x, y + 4, 21, "minecraft:dark_oak_log", axis="x")
    # watchtower (north-east corner)
    for (x, z) in ((17, 3), (20, 3), (17, 6), (20, 6)):
        for yy in range(y, y + 6):
            log(t, x, yy, z, "minecraft:spruce_log")
    for x in range(17, 21):
        for z in range(3, 7):
            t.put(x, y + 6, z, "minecraft:spruce_planks")
            if x in (17, 20) or z in (3, 6):
                t.put(x, y + 7, z, "minecraft:spruce_fence")
    for x in range(16, 22):
        for z in range(2, 8):
            if x in (16, 21) or z in (2, 7):
                slab(t, x, y + 9, z, "minecraft:spruce_slab")
            else:
                slab(t, x, y + 10, z, "minecraft:spruce_slab")
    for (x, z) in ((17, 3), (20, 3), (17, 6), (20, 6)):
        t.put(x, y + 8, z, "minecraft:spruce_fence")
    for yy in range(y, y + 6):
        t.put(18, yy, 7, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    t.air(18, y + 6, 6, 18, y + 7, 6)
    lantern(t, 19, y + 7, 5)
    # lean-to tents of hides along the west wall
    for (tz, col) in ((5, "black"), (10, "brown"), (15, "black")):
        for dz in range(3):
            for dx, hh in ((3, 2), (4, 1), (5, 0)):
                t.put(dx, y + hh, tz + dz, "minecraft:%s_wool" % col)
            t.put(3, y, tz + dz, "minecraft:%s_wool" % col)
            t.put(3, y + 1, tz + dz, "minecraft:%s_wool" % col)
        t.put(4, y, tz + 1, "minecraft:hay_block", {"axis": "z"})
        t.put(4, y, tz, "minecraft:brown_carpet")
        t.titik("kasur", 4, y, tz + 2, "east")
    # cooking fire: unlit campfire, the pot on its tripod, log seats
    c = 11
    t.put(c, y, c, "minecraft:campfire", {"facing": "north", "lit": "false", "signal_fire": "false", "waterlogged": "false"})
    t.put(c + 1, y, c, "minecraft:cauldron")
    for (dx, dz) in ((-1, -1), (2, -1)):
        t.put(c + dx, y, c + dz, "minecraft:spruce_fence")
    for dx in range(-1, 3):
        log(t, c + dx, y + 1, c - 1, "minecraft:stripped_spruce_log", axis="x")
    for (dx, dz, ax) in ((-2, 1, "z"), (3, 1, "z"), (0, 3, "x"), (2, 3, "x")):
        log(t, c + dx, y, c + dz, "minecraft:spruce_log", axis=ax)
    # the cage and the plundered cart (south-east)
    for x in range(16, 20):
        for z in range(15, 19):
            if x in (16, 19) or z in (15, 18):
                for yy in range(y, y + 3):
                    t.put(x, yy, z, "minecraft:iron_bars")
            t.put(x, y + 3, z, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.put(17, y, 16, "minecraft:hay_block", {"axis": "x"})
    t.put(18, y, 17, "minecraft:white_carpet")
    gerobak_rusak(t, 14, y, 13)
    # loot: chest, barrels and crates
    chest(t, 19, y, 10, "west", loot="kultivator:pos/kemah_begal")
    barrel(t, 20, y, 10)
    barrel(t, 20, y + 1, 10)
    barrel(t, 20, y, 11, "west")
    t.put(19, y, 12, "minecraft:spruce_planks")
    t.put(20, y, 12, "minecraft:spruce_trapdoor", {"facing": "west", "half": "top", "open": "false", "powered": "false",
                                                    "waterlogged": "false"})
    # weapon rack, grindstone, chipped anvil
    for z in (14, 15, 16):
        t.put(7, y, z, "minecraft:spruce_fence")
        t.put(7, y + 1, z, "minecraft:spruce_fence")
    t.put(8, y, 17, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    t.put(8, y, 13, "minecraft:chipped_anvil", {"facing": "west"})
    # the band's banner on a tall pole by the fire
    for yy in range(y, y + 4):
        t.put(9, yy, 9, "minecraft:dark_oak_fence")
    t.put(9, y + 4, 9, "minecraft:black_banner", {"rotation": "8"})
    t.titik("jaga", 19, y + 7, 4, "south")
    t.titik("jaga", 11, y, 19, "south")
    t.titik("jaga", 14, y, 18, "west")
    t.titik("obrol", c - 2, y, c + 1, "east")
    t.titik("obrol", c + 3, y, c + 1, "west")
    t.titik("obrol", c + 1, y, c + 3, "north")
    t.jangkar(c + 0.5, y, c + 5.5, "pos/kemah_begal", protect="0", situs="pos/kemah_begal")
    t.simpan()
    return t.id


# ================================================================================================== worldgen JSON
def pos_struktur(jenis, lots, adaptasi="beard_thin"):
    return {"type": "kultivator:jalan", "biomes": OVERWORLD, "step": "surface_structures",
            "terrain_adaptation": adaptasi,
            "spawn_overrides": tl.SPAWN_KOSONG if jenis != "kemah_begal" else {},
            "pos": jenis, "lot": lots}


def worldgen(pos_ids):
    tl.tulis_wg("structure", "jalan/jalan", {
        "type": "kultivator:jalan", "biomes": OVERWORLD, "step": "surface_structures", "terrain_adaptation": "none",
        "spawn_overrides": {},
        "ubin": ["kultivator:jalan/ubin_" + k for k in KELAS],
        "jembatan": ["kultivator:jalan/jembatan_" + k for k in KELAS],
        "tangga": ["kultivator:jalan/tangga_" + k for k in KELAS],
        "penyeberangan": "kultivator:jalan/penyeberangan",
        "lentera": "kultivator:jalan/lentera",
    })
    tl.tulis_wg("structure_set", "jalan/jalan", tl.set_jalan("kultivator:jalan/jalan", SALT_JALAN), pack=PACK)
    single = {"tonggak": "tonggak", "kuil_jalan": "kuil_jalan", "singgah": "singgah", "dagang": "dagang",
              "kemah_begal": "kemah_begal"}
    for jenis, folder in single.items():
        tl.tulis_pool("pos/" + jenis, [(tl.elemen(i), 1) for i in pos_ids[jenis]])
        w = {"tonggak": 5, "kuil_jalan": 9, "singgah": 40, "dagang": 44, "kemah_begal": 24}[jenis]
        lots = [{"dx": -(w // 2), "dz": -(w // 2), "pool": "kultivator:pos/" + jenis}]
        tl.tulis_wg("structure", "pos/" + jenis, pos_struktur(jenis, lots))
    for part, i in pos_ids["singgah_besar"].items():
        tl.tulis_pool("pos/singgah_besar/" + part, [(tl.elemen(i), 1)])
    lots = [{"dx": -32 if p[1] == "w" else 0, "dz": -32 if p[0] == "n" else 0, "pool": "kultivator:pos/singgah_besar/" + p}
            for p in ("nw", "ne", "sw", "se")]
    tl.tulis_wg("structure", "pos/singgah_besar", pos_struktur("singgah_besar", lots))
    for jenis, salt in SALT_POS.items():
        tl.tulis_wg("structure_set", "pos/" + jenis, {
            "placement": {"type": "kultivator:jalan", "salt": salt, "spacing": 1, "separation": 0, "titik": jenis},
            "structures": [{"structure": "kultivator:pos/" + jenis, "weight": 1}]}, pack=PACK)
    tags = os.path.join(structgen.DATA, "tags", "worldgen")
    structgen.write_json(os.path.join(tags, "structure", "jalan.json"),
                         {"values": ["kultivator:jalan/jalan"]})
    structgen.write_json(os.path.join(tags, "structure", "pos.json"),
                         {"values": ["kultivator:pos/" + j for j in SALT_POS]})
    structgen.write_json(os.path.join(tags, "structure", "boleh_di_tempat.json"),
                         {"replace": False, "values": ["#kultivator:jalan"]})
    tag_tempat(tags)


# The two shared 38 tags are owned by 38W (38.D.1, 38.R.1, 38.T.2.3): written whole here, every entry of another
# module optional ("required": false) so a disabled worldgen pack or a module that has not generated yet never breaks
# tag loading. Sets live in the optional packs, so even our own sets are optional in the set tag.
TEMA_DISTRIK = {
    "pelabuhan": ["dermaga_nelayan", "dermaga_agung", "galangan", "kota_awak", "pasar_laut", "gudang_dagang",
                  "kuil_laut", "gerbang_darat", "hunian_saudagar"],
    "oasis": ["kuil_air", "istana_pasir", "kebun_palma", "bazar_barat", "mata_air", "bazar_timur", "hunian_tanah",
              "gerbang_giok", "karavanserai", "lapangan_kafilah"],
    "air": ["taman_sastra", "rumah_sutra", "bukit_teh", "jembatan_kembar", "danau_lentera", "pasar_kanal",
            "hunian_kanal", "gerbang_air", "dermaga_perahu"],
    "gunung": ["tebing_papan", "puncak_emas", "biara_awan", "teras_hunian", "aula_herba", "teras_kebun",
               "penginapan_peziarah", "kaki_gerbang", "pasar_kaki"],
    "iblis": ["menara_terlarang", "istana_darah", "penjara_tawanan", "pasar_gelap", "arena_darah", "sarang_kontrak",
              "kumuh_iblis", "gerbang_tengkorak", "kedai_racun"],
}
KOTA_BESAR_DISTRIK = ["istana", "cendekia", "awan", "gilda", "pusat", "pasar", "hunian_barat", "gerbang", "arena",
                      "kumuh", "luar"]


def _opt(i):
    return {"id": i, "required": False}


def tag_tempat(tags):
    """#kultivator:tempat_berpenghuni (structure SETS, read by cincin_tempat.jarak_tempat) and
    #kultivator:tempat_dirawat (STRUCTURES cared for by 38.R)."""
    sets = ["kultivator:kota_besar/" + d for d in KOTA_BESAR_DISTRIK]
    sets += ["kultivator:kota/kota_kultivator", "kultivator:kota/kota_abadi"]
    sets += ["kultivator:kampung/" + k for k in ("dusun", "desa", "desa_besar", "klan")]
    sets += ["kultivator:kompleks_sekte/" + d for d in ("halaman", "dalam", "puncak")]
    sets += ["kultivator:pos/singgah", "kultivator:pos/singgah_besar", "kultivator:pos/dagang"]
    for tema, ds in TEMA_DISTRIK.items():
        sets += ["kultivator:kota_tema/%s/%s" % (tema, d) for d in ds]
    sets += ["kultivator:kota_tema/pos_pemburu"]
    structgen.write_json(os.path.join(tags, "structure_set", "tempat_berpenghuni.json"),
                         {"replace": False, "values": [_opt(v) for v in sets]})
    rawat = ["kultivator:pos/singgah", "kultivator:pos/singgah_besar", "kultivator:pos/dagang",
             "kultivator:jalan/jalan", "#kultivator:kota_besar", "#kultivator:kota", "#kultivator:kota_abadi"]
    rawat_opt = ["#kultivator:kampung", "#kultivator:kompleks_sekte", "#kultivator:kota_tema",
                 "kultivator:laut/pulau_pasar", "kultivator:kota_tema/pos_pemburu"]
    structgen.write_json(os.path.join(tags, "structure", "tempat_dirawat.json"),
                         {"replace": False, "values": rawat + [_opt(v) for v in rawat_opt]})


def loot():
    """The bandit camp chest (stolen goods: food, iron, a few low spirit stones through vanilla items only)."""
    path = os.path.join(structgen.DATA, "loot_table", "pos", "kemah_begal.json")
    structgen.write_json(path, {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
                {"type": "minecraft:item", "name": "minecraft:bread", "weight": 10,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                {"type": "minecraft:item", "name": "minecraft:cooked_mutton", "weight": 6,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                {"type": "minecraft:item", "name": "minecraft:iron_ingot", "weight": 6,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                {"type": "minecraft:item", "name": "minecraft:gold_nugget", "weight": 6,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 8}}]},
                {"type": "minecraft:item", "name": "minecraft:leather", "weight": 5},
                {"type": "minecraft:item", "name": "minecraft:arrow", "weight": 5,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 4, "max": 10}}]},
                {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 2},
                {"type": "minecraft:item", "name": "minecraft:saddle", "weight": 1}]}]})


def main():
    ubin()
    jembatan()
    tangga()
    penyeberangan()
    pos_ids = {
        "tonggak": [tonggak("a"), tonggak("b"), tonggak_bixi(), tonggak_paviliun()],
        "kuil_jalan": [kuil_jalan("a"), kuil_jalan("b"), kuil_pohon(), kuil_gua()],
        "singgah": [singgah(), singgah_kolam()],
        "singgah_besar": singgah_besar(),
        "dagang": [dagang()],
        "kemah_begal": [kemah_begal("a"), kemah_begal("b"), kemah_benteng()],
    }
    worldgen(pos_ids)
    loot()
    tl.cek_semua()


if __name__ == "__main__":
    main()
