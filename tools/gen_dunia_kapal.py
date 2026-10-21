"""Kapal Terbang Sekte hull models (DESIGN 34.5.15, module T): builds the three hulls with structgen.Piece and exports

  assets/kultivator/dunia/kapal/<class>.json   the client display list (block-state runs, sails, lights, banner, engine)
  data/kultivator/structure/kapal/<class>.nbt  the same hull as a template, only for `uji struktur lihat kapal/<class>`
                                               (never placed by worldgen or at runtime: the ship is an ENTITY, rule W2)

Run: python tools/gen_dunia_kapal.py [preview_dir]
It prints the layout constants (seats, deck tiles, helm, sizes) that KapalClass.java must match, a plan and a side view
of every hull, the run counts (the renderer's LOD sets) and finally structgen's validation of every block id.

Local frame of a hull: x across the ship (cell 0 = port side as drawn), y up (0 = keel), z along the ship (0 = stern,
L-1 = bow), so the bow points to local +z. The root entity sits at the footprint centre on the keel bottom; the JSON
offsets are relative to it (x - cx, y, z - cz with cx = W/2, cz = L/2).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structgen import Piece, out_path, validate_all, REPO  # noqa: E402

ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "kultivator", "dunia", "kapal")

# Blocks the Minimal / far LOD leaves out (decoration that reads only up close).
DETAIL_WORDS = ("fence", "lantern", "chain", "trapdoor", "end_rod", "button", "lever", "carpet", "candle", "wall",
                "pane", "bars", "grindstone", "lightning_rod", "banner", "flower_pot", "bell")


# ----------------------------------------------------------------------------------------------------------- helpers
def stair(p, x, y, z, bid, facing, half="bottom", shape="straight"):
    p.put(x, y, z, bid, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})


def slab(p, x, y, z, bid, kind="bottom"):
    p.put(x, y, z, bid, {"type": kind, "waterlogged": "false"})


def log(p, x, y, z, bid, axis):
    p.put(x, y, z, bid, {"axis": axis})


def lantern(p, x, y, z, hanging=True, soul=False):
    p.put(x, y, z, "minecraft:soul_lantern" if soul else "minecraft:lantern",
          {"hanging": "true" if hanging else "false", "waterlogged": "false"})


def chain(p, x, y, z):
    p.put(x, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def fence_connect(p):
    """Gives every fence and wall its connections to its fence/wall/solid neighbours, as the game would."""
    for (x, y, z), ((bid, props), data) in list(p.cells.items()):
        if not bid.endswith("_fence"):
            continue
        out = dict(props)
        for face, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            other = p.get(x + dx, y, z + dz)
            out[face] = "true" if other is not None and (other.endswith("_fence") or other.endswith("_planks")
                                                          or other.endswith("_log") or other.endswith("stem")) else "false"
        out["waterlogged"] = "false"
        p.set(x, y, z, bid, out)


def hull(p, length, half, keel, deck, mat, start=0):
    """A rounded hull from z=start to start+length-1: half(z) is the deck half-width in cells at z (0 = only the centre
    column). Rows narrow one cell per layer below the deck; the narrowing steps get upside-down stairs (the rounded
    bilge), the bow and stern rows stairs facing along the ship. Returns the deck cells {(x, z)}."""
    w = p.size[0]
    cx = w // 2
    deck_cells = set()
    for z in range(start, start + length):
        h = half(z)
        if h < 0:
            continue
        for y in range(keel + 1, deck + 1):
            hy = h - (deck - y)
            if hy < 0:
                continue
            for x in range(cx - hy, cx + hy + 1):
                edge = x in (cx - hy, cx + hy)
                bottom = y == keel + 1
                if y == deck:
                    deck_cells.add((x, z))
                    if edge:
                        p.put(x, y, z, mat["strake"])
                    else:
                        p.put(x, y, z, mat["deck"])
                    continue
                if edge or bottom:
                    below = h - (deck - (y - 1))
                    if edge and y > keel + 1 and below < hy:
                        # the bilge curve: an upside-down stair whose step faces the ship's middle
                        stair(p, x, y, z, mat["hull_stair"], "west" if x > cx else "east", half="top")
                    else:
                        p.put(x, y, z, mat["hull"])
    # ends: where the hull shortens along z, the outer rows get stairs facing back into the hull
    for (x, z) in sorted(deck_cells):
        for y in range(keel + 1, deck):
            if p.get(x, y, z) is None:
                continue
            fwd = p.get(x, y, z + 1)
            back = p.get(x, y, z - 1)
            if fwd is None and z + 1 < p.size[2] and p.get(x, y + 1, z + 1) is not None:
                stair(p, x, y, z, mat["hull_stair"], "north", half="top")
            elif back is None and z - 1 >= 0 and p.get(x, y + 1, z - 1) is not None:
                stair(p, x, y, z, mat["hull_stair"], "south", half="top")
    # the keel: a log along the middle under the hull
    zs = sorted({z for (x, z) in deck_cells if x == cx})
    for z in zs[1:-1]:
        log(p, cx, keel, z, mat["keel"], "z")
    return deck_cells


def bulwark(p, deck_cells, deck, mat, rail_y=None, gaps=()):
    """The bulwark (one block above the deck edge) with a fence rail of posts; `gaps` are (x, z) left open."""
    w = p.size[0]
    cx = w // 2
    edge = set()
    for (x, z) in deck_cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) not in deck_cells:
                edge.add((x, z))
    for (x, z) in sorted(edge):
        if (x, z) in gaps:
            continue
        p.put(x, deck + 1, z, mat["bulwark"])
        if mat.get("rail"):
            p.put(x, deck + 2, z, mat["rail"])
    return edge


def mast(p, x, z, y0, y1, mat, yard_y=None, yard_half=0):
    for y in range(y0, y1 + 1):
        log(p, x, y, z, mat["mast"], "y")
    if yard_y is not None:
        for dx in range(-yard_half, yard_half + 1):
            if dx:
                log(p, x + dx, yard_y, z, mat["yard"], "x")
        # a lower boom
    p.put(x, y1 + 1, z, "minecraft:end_rod", {"facing": "up"})


def chair(p, x, y, z, mat, facing_back="north"):
    """A chair facing the bow: a stair whose back is toward the stern, a red cushion carpet would float, so the seat
    itself is the stair (a stair's step is the seat)."""
    stair(p, x, y, z, mat["seat"], facing_back)


def helm(p, x, y, z, mat):
    """The helm: a fence post with a grindstone wheel facing the bow (the captain sits behind it)."""
    p.put(x, y, z, mat["helm_post"])
    p.put(x, y + 1, z, "minecraft:grindstone", {"face": "wall", "facing": "north"})


# ----------------------------------------------------------------------------------------------------------- export
def runs_of(p, cx, cz):
    """Greedy runs of identical states along x or z (≤ 4 long), hidden blocks dropped. Returns (palette, runs)."""
    used = set()
    palette = []
    index = {}
    runs = []
    cells = p.cells

    def opaque(pos):
        c = cells.get(pos)
        if c is None:
            return False
        bid = c[0][0]
        props = dict(c[0][1])
        return (bid.endswith(("_planks", "_log", "_wood", "_tiles", "_bricks", "_block", "_stem", "_hyphae"))
                and not props.get("type") and "stairs" not in bid)

    for pos in sorted(cells, key=lambda q: (q[1], q[2], q[0])):
        if pos in used:
            continue
        (bid, props), _ = cells[pos]
        if bid == "minecraft:air":
            continue
        x, y, z = pos
        if all(opaque((x + dx, y + dy, z + dz)) for dx, dy, dz in
               ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            used.add(pos)  # fully enclosed: never seen
            continue
        key = (bid, props)
        lx = 1
        while lx < 4 and (x + lx, y, z) not in used and cells.get((x + lx, y, z), (None,))[0] == key:
            lx += 1
        lz = 1
        while lz < 4 and (x, y, z + lz) not in used and cells.get((x, y, z + lz), (None,))[0] == key:
            lz += 1
        along_z = lz > lx
        n = lz if along_z else lx
        for i in range(n):
            used.add((x, y, z + i) if along_z else (x + i, y, z))
        if key not in index:
            index[key] = len(palette)
            palette.append({"id": bid, "p": {k: v for k, v in props}} if props else {"id": bid})
        detail = any(w in bid for w in DETAIL_WORDS)
        runs.append({"b": index[key], "x": round(x - cx, 3), "y": y, "z": round(z - cz, 3),
                     "n": n, "a": "z" if along_z else "x", "d": 1 if detail else 0})
    return palette, runs


def export(name, p, extra):
    w, h, l = p.size
    cx, cz = w / 2.0, l / 2.0
    fence_connect(p)
    palette, runs = runs_of(p, cx, cz)
    # draw order: the hull first (the Minimal set is the first `core` runs), details last
    runs.sort(key=lambda r: (r["d"], r["y"], r["z"], r["x"]))
    core = sum(1 for r in runs if r["d"] == 0)
    model = {
        "class": name,
        "size": [w, h, l],
        "blocks": len([c for c in p.cells.values() if c[0][0] != "minecraft:air"]),
        "core": core,
        "palette": palette,
        "runs": runs,
    }
    for k, v in extra.items():
        model[k] = v
    os.makedirs(ASSETS, exist_ok=True)
    path = os.path.join(ASSETS, name + ".json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, separators=(",", ":"), ensure_ascii=False)
        f.write("\n")
    p.save(out_path("kapal", name))
    return model


def loc(p, x, y, z):
    """Block-centre local offsets for points of interest (relative to the root)."""
    w, _, l = p.size
    return [round(x + 0.5 - w / 2.0, 3), round(float(y), 3), round(z + 0.5 - l / 2.0, 3)]


def side_view(p):
    w, h, l = p.size
    rows = []
    for y in range(h - 1, -1, -1):
        row = ""
        for z in range(l):
            col = [p.get(x, y, z) for x in range(w)]
            col = [c for c in col if c and c != "minecraft:air"]
            row += "#" if col else " "
        rows.append(row.rstrip())
    return "\n".join(r for r in rows)


# ----------------------------------------------------------------------------------------------------------- hulls
PERAHU_MAT = {
    "hull": "minecraft:dark_oak_planks", "hull_stair": "minecraft:dark_oak_stairs", "keel": "minecraft:stripped_dark_oak_log",
    "deck": "minecraft:spruce_planks", "strake": "minecraft:stripped_crimson_hyphae", "bulwark": "minecraft:dark_oak_slab",
    "rail": None, "mast": "minecraft:dark_oak_log", "yard": "minecraft:stripped_dark_oak_log", "seat": "minecraft:spruce_stairs",
    "helm_post": "minecraft:dark_oak_fence",
}


def build_perahu():
    """Perahu Awan: a 7 x 13 personal cloud skiff, two seats, one batten sail, a jade-gold prow."""
    W, H, L = 7, 12, 13
    p = Piece(W, H, L, seed=56001)
    keel, deck = 0, 2
    prof = {0: 0, 1: 1, 2: 2, 3: 2, 4: 3, 5: 3, 6: 3, 7: 3, 8: 3, 9: 2, 10: 2, 11: 1, 12: 0}
    cells = hull(p, L, lambda z: prof.get(z, -1), keel, deck, PERAHU_MAT)
    # a low bulwark of slabs (top half) so the deck reads; the prow and the stern post rise
    edge = set()
    for (x, z) in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) not in cells:
                edge.add((x, z))
    for (x, z) in sorted(edge):
        if z in (0, L - 1):
            continue
        slab(p, x, deck + 1, z, "minecraft:dark_oak_slab", "bottom")
    cx = W // 2
    # prow: an upturned stem with a gold cap and a jade (prismarine) ornament, a hanging lantern
    log(p, cx, deck + 1, L - 1, "minecraft:stripped_dark_oak_log", "y")
    stair(p, cx, deck + 2, L - 1, "minecraft:dark_oak_stairs", "north")
    p.put(cx, deck + 3, L - 1, "minecraft:gold_block")
    stair(p, cx, deck + 4, L - 1, "minecraft:prismarine_brick_stairs", "north", half="bottom")
    p.put(cx, deck + 2, L - 2, "minecraft:dark_oak_fence")
    # stern post with the lantern and the banner pole
    log(p, cx, deck + 1, 0, "minecraft:stripped_dark_oak_log", "y")
    log(p, cx, deck + 2, 0, "minecraft:stripped_dark_oak_log", "y")
    p.put(cx, deck + 3, 0, "minecraft:dark_oak_fence")
    p.put(cx, deck + 4, 0, "minecraft:dark_oak_fence")
    stair(p, cx, deck + 3, 1, "minecraft:dark_oak_stairs", "north", half="top")
    chain(p, cx, deck + 2, 1)
    lantern(p, cx, deck + 1, 1)
    # the qi engine at the transom: a sea lantern framed in gold
    p.put(cx, deck - 1, 0, "minecraft:sea_lantern")
    stair(p, cx - 1, deck - 1, 1, "minecraft:dark_oak_stairs", "east")
    stair(p, cx + 1, deck - 1, 1, "minecraft:dark_oak_stairs", "west")
    # helm and the two seats (helm seat behind the wheel, a passenger bench forward of the mast)
    helm(p, cx, deck + 1, 4, PERAHU_MAT)
    stair(p, cx, deck + 1, 3, "minecraft:spruce_stairs", "north")
    stair(p, cx, deck + 1, 9, "minecraft:spruce_stairs", "north")
    # mast (y 3..10) with a yard at 10 and the boom at 4
    mz = 7
    mast(p, cx, mz, deck + 1, 10, PERAHU_MAT, yard_y=10, yard_half=2)
    for dx in (-2, -1, 1, 2):
        log(p, cx + dx, 4, mz, "minecraft:stripped_dark_oak_log", "x")
    lantern(p, cx + 2, 9, mz, hanging=True)
    seats = [loc(p, cx, deck + 1, 3), loc(p, cx, deck + 1, 9)]
    for s in seats:
        s[1] = deck + 1 + 0.5
    deck_top = deck + 1.0
    extra = {
        "origin": [W / 2.0, 0.0, L / 2.0],
        "deck": deck_top,
        "seats": seats,
        "helm": loc(p, cx, deck + 2, 4),
        "sails": [sail(p, cx, mz, 4.6, 9.8, 2.3, 0.0, 1)],
        "lights": [light(p, cx, deck + 1, 1, 0xFFC66A), light(p, cx + 2, 8, mz, 0xFFC66A),
                   light(p, cx, deck + 4, L - 1, 0x7FFFD4, 0.6)],
        "engine": [loc(p, cx, deck - 1, 0)],
        "banner": banner(p, cx, deck + 4, 0, 0.9, 1.6),
        "wake": [W * 0.5, L * 0.5],
        "deckTiles": deck_tiles(cells, W, L, 3),
    }
    return p, extra, cells


SEKTE_MAT = {
    "hull": "minecraft:dark_oak_planks", "hull_stair": "minecraft:dark_oak_stairs", "keel": "minecraft:stripped_dark_oak_log",
    "deck": "minecraft:spruce_planks", "strake": "minecraft:mangrove_planks", "bulwark": "minecraft:dark_oak_planks",
    "rail": "minecraft:dark_oak_fence", "mast": "minecraft:dark_oak_log", "yard": "minecraft:stripped_dark_oak_log",
    "seat": "minecraft:spruce_stairs", "helm_post": "minecraft:dark_oak_fence",
}


def roof(p, x0, y, z0, x1, z1, stairs_id, slab_id, full_id, along="x", curl=True):
    """structgen's Chinese gable roof with an explicit palette."""
    p.pal.update({"roof": stairs_id, "roof_slab": slab_id, "roof_full": full_id})
    return p.roof_gable(x0, y, z0, x1, z1, role="roof", slab_role="roof_slab", full_role="roof_full", along=along,
                        overhang=1, eave_curl=curl)


def build_sekte():
    """Kapal Sekte: an 11 x 25 sect junk, eight seats, a stern cabin with a curled tile roof, two masts and a dragon
    prow."""
    W, H, L = 11, 20, 25
    p = Piece(W, H, L, seed=56002)
    keel, deck = 0, 3
    prof = {}
    for z in range(L):
        if z <= 1:
            prof[z] = 2 + z
        elif z <= 3:
            prof[z] = 4
        elif z <= 17:
            prof[z] = 5
        elif z <= 20:
            prof[z] = 4
        elif z <= 22:
            prof[z] = 3
        elif z == 23:
            prof[z] = 2
        else:
            prof[z] = 1
    cells = hull(p, L, lambda z: prof.get(z, -1), keel, deck, SEKTE_MAT)
    cx = W // 2
    # gold rub-strake one row under the deck edge, port and starboard, mid-ship
    for z in range(4, 18, 4):
        p.put(cx - 5, deck - 1, z, "minecraft:gold_block")
        p.put(cx + 5, deck - 1, z, "minecraft:gold_block")
    gaps = {(cx - 5, 12), (cx + 5, 12)}  # boarding gaps mid-ship
    bulwark(p, cells, deck, SEKTE_MAT, gaps=gaps)
    # red lacquer posts every 3 cells on the rail
    for (x, z) in sorted(cells):
        if p.get(x, deck + 2, z) == "minecraft:dark_oak_fence" and z % 3 == 0 and x in (cx - prof[z], cx + prof[z]):
            log(p, x, deck + 2, z, "minecraft:stripped_crimson_stem", "y")
    # --- the stern cabin z 1..7, x cx-3..cx+3, walls 3 high, windows, door facing the bow
    x0, x1, z0, z1 = cx - 3, cx + 3, 1, 7
    for y in range(deck + 1, deck + 4):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    if corner:
                        log(p, x, y, z, "minecraft:stripped_crimson_stem", "y")
                    elif y == deck + 2 and ((z in (z0, z1) and x % 2 == 1) or (x in (x0, x1) and z % 2 == 0)):
                        facing = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
                        p.put(x, y, z, "minecraft:spruce_trapdoor", {"facing": facing, "half": "bottom", "open": "true",
                                                                       "powered": "false", "waterlogged": "false"})
                    else:
                        p.put(x, y, z, "minecraft:dark_oak_planks")
                else:
                    p.put(x, y, z, "minecraft:air")
    for y in range(deck + 1, deck + 3):
        p.put(cx, y, z1, "minecraft:air")  # the door toward the bow
    gable_fill(p, x0, deck + 4, x1, (z0, z1), "minecraft:dark_oak_planks")
    top = roof(p, x0, deck + 4, z0, x1, z1, "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab",
               "minecraft:deepslate_tiles", along="z")
    # lanterns under the front eave
    for lx in (x0 - 1, x1 + 1):
        chain(p, lx, deck + 3, z1 + 1)
        lantern(p, lx, deck + 2, z1 + 1)
    # helm in front of the cabin door, the captain's chair behind it
    helm(p, cx, deck + 1, 10, SEKTE_MAT)
    stair(p, cx, deck + 1, 9, "minecraft:spruce_stairs", "north")
    # benches: three a side facing the middle, mid-ship
    seat_cells = [(cx, 9)]
    for z in (13, 15, 17):
        stair(p, cx - 3, deck + 1, z, "minecraft:spruce_stairs", "west")
        stair(p, cx + 3, deck + 1, z, "minecraft:spruce_stairs", "east")
        seat_cells += [(cx - 3, z), (cx + 3, z)]
    stair(p, cx, deck + 1, 21, "minecraft:spruce_stairs", "north")
    seat_cells.append((cx, 21))
    # masts: main z=14 (y 4..17), fore z=20 (y 4..13)
    mast(p, cx, 14, deck + 1, 17, SEKTE_MAT, yard_y=17, yard_half=4)
    for dx in range(-4, 5):
        if dx:
            log(p, cx + dx, deck + 3, 14, "minecraft:stripped_dark_oak_log", "x")
    mast(p, cx, 19, deck + 1, 13, SEKTE_MAT, yard_y=13, yard_half=3)
    for dx in range(-3, 4):
        if dx:
            log(p, cx + dx, deck + 3, 19, "minecraft:stripped_dark_oak_log", "x")
    for lx in (cx - 4, cx + 4):
        chain(p, lx, 16, 14)
        lantern(p, lx, 15, 14)
    # the dragon prow: a jade head with gold horns and whiskers of end rods
    bz = L - 1
    log(p, cx, deck + 1, bz, "minecraft:stripped_dark_oak_log", "y")
    p.put(cx, deck + 2, bz, "minecraft:prismarine_bricks")
    stair(p, cx, deck + 3, bz, "minecraft:prismarine_brick_stairs", "north")
    stair(p, cx, deck + 2, bz - 1, "minecraft:prismarine_brick_stairs", "north", half="top")
    p.put(cx, deck + 3, bz - 1, "minecraft:dark_prismarine")
    stair(p, cx - 1, deck + 4, bz - 1, "minecraft:dark_prismarine_stairs", "east")
    stair(p, cx + 1, deck + 4, bz - 1, "minecraft:dark_prismarine_stairs", "west")
    p.put(cx - 1, deck + 3, bz - 1, "minecraft:gold_block")
    p.put(cx + 1, deck + 3, bz - 1, "minecraft:gold_block")
    p.put(cx, deck + 4, bz, "minecraft:end_rod", {"facing": "south"})
    # stern: engine row and the sect banner pole
    for dx in (-1, 0, 1):
        p.put(cx + dx, deck - 1, 0, "minecraft:sea_lantern")
    p.put(cx - 2, deck - 1, 0, "minecraft:gold_block")
    p.put(cx + 2, deck - 1, 0, "minecraft:gold_block")
    for y in range(top + 1, top + 4):
        p.put(cx, y, z0, "minecraft:dark_oak_fence")
    seats = []
    for (x, z) in seat_cells:
        s = loc(p, x, deck + 1, z)
        s[1] = deck + 1 + 0.5
        seats.append(s)
    extra = {
        "origin": [W / 2.0, 0.0, L / 2.0],
        "deck": deck + 1.0,
        "seats": seats,
        "helm": loc(p, cx, deck + 2, 10),
        "sails": [sail(p, cx, 14, deck + 3.4, 16.8, 4.4, 0.0, 1), sail(p, cx, 19, deck + 3.4, 12.8, 3.4, 0.0, 1)],
        "lights": [light(p, x0 - 1, deck + 2, z1 + 1, 0xFFC66A), light(p, x1 + 1, deck + 2, z1 + 1, 0xFFC66A),
                   light(p, cx - 4, 15, 14, 0xFFC66A), light(p, cx + 4, 15, 14, 0xFFC66A),
                   light(p, cx, deck + 4, bz, 0xE8FFFF, 0.5)],
        "engine": [loc(p, cx + dx, deck - 1, 0) for dx in (-1, 0, 1)],
        "banner": banner(p, cx, top + 3, z0, 1.4, 2.6),
        "wake": [W * 0.5, L * 0.5],
        "deckTiles": deck_tiles(cells, W, L, 3, skip_rect=(x0, z0, x1, z1)),
    }
    return p, extra, cells


BAHTERA_MAT = {
    "hull": "minecraft:deepslate_tiles", "hull_stair": "minecraft:deepslate_tile_stairs", "keel": "minecraft:stripped_dark_oak_log",
    "deck": "minecraft:stripped_dark_oak_wood", "strake": "minecraft:gold_block", "bulwark": "minecraft:dark_oak_planks",
    "rail": "minecraft:dark_oak_fence", "mast": "minecraft:dark_oak_log", "yard": "minecraft:stripped_dark_oak_log",
    "seat": "minecraft:dark_oak_stairs", "helm_post": "minecraft:dark_oak_fence",
}


def build_bahtera():
    """Bahtera Langit: a 15 x 37 armoured sky ark, twelve seats, a two-tier jade pagoda, three masts, cloud fins."""
    W, H, L = 17, 28, 37
    p = Piece(W, H, L, seed=56003)
    keel, deck = 0, 4
    prof = {}
    for z in range(L):
        if z == 0:
            prof[z] = 4
        elif z <= 2:
            prof[z] = 5
        elif z <= 4:
            prof[z] = 6
        elif z <= 27:
            prof[z] = 7
        elif z <= 30:
            prof[z] = 6
        elif z <= 32:
            prof[z] = 5
        elif z <= 34:
            prof[z] = 3
        else:
            prof[z] = 2 if z == 35 else 1
    cells = hull(p, L, lambda z: prof.get(z, -1), keel, deck, BAHTERA_MAT)
    cx = W // 2
    # upper hull in dark oak (the armour is the deepslate below), a gold band at the deck edge
    for (x, z) in sorted(cells):
        h = prof[z]
        for y in (deck - 1,):
            if x in (cx - h, cx + h) and p.get(x, y, z) is not None:
                p.put(x, y, z, "minecraft:dark_oak_planks")
    # armour studs: polished blackstone every 3 along the waterline
    for z in range(3, 30, 3):
        for x in (cx - prof[z] + 1, cx + prof[z] - 1):
            if p.get(x, deck - 3, z) is not None:
                p.put(x, deck - 3, z, "minecraft:polished_blackstone_bricks")
    gaps = {(cx - 7, 16), (cx + 7, 16), (cx - 7, 17), (cx + 7, 17)}
    bulwark(p, cells, deck, BAHTERA_MAT, gaps=gaps)
    for (x, z) in sorted(cells):
        if p.get(x, deck + 2, z) == "minecraft:dark_oak_fence" and z % 4 == 0:
            log(p, x, deck + 2, z, "minecraft:stripped_crimson_stem", "y")
            lantern(p, x, deck + 3, z, hanging=False)
    # cloud fins: white quartz wings under the deck edge on both sides mid-ship
    for z in range(10, 24):
        for x, facing in ((0, "east"), (W - 1, "west")):
            if z in (10, 23):
                slab(p, x, deck - 1, z, "minecraft:quartz_slab", "top")
            else:
                stair(p, x, deck - 1, z, "minecraft:quartz_stairs", facing, half="top")
                p.put(x, deck - 2, z, "minecraft:calcite") if z % 2 == 0 else None
    # --- the pagoda: z 3..12, x cx-4..cx+4, storey 1 (4 high) and a smaller storey 2
    x0, x1, z0, z1 = cx - 4, cx + 4, 3, 12
    for y in range(deck + 1, deck + 5):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    pillar = corner or (z in (z0, z1) and (x - x0) % 4 == 0) or (x in (x0, x1) and (z - z0) % 3 == 0)
                    if pillar:
                        log(p, x, y, z, "minecraft:stripped_crimson_stem", "y")
                    elif y in (deck + 2, deck + 3) and not pillar:
                        facing = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
                        p.put(x, y, z, "minecraft:dark_oak_trapdoor", {"facing": facing, "half": "bottom", "open": "true",
                                                                         "powered": "false", "waterlogged": "false"})
                    else:
                        p.put(x, y, z, "minecraft:dark_oak_planks")
                else:
                    p.put(x, y, z, "minecraft:air")
    for y in range(deck + 1, deck + 4):
        for dx in (-1, 0, 1):
            p.put(cx + dx, y, z1, "minecraft:air")
    p.pal.update({"roof": "minecraft:dark_prismarine_stairs", "roof_slab": "minecraft:dark_prismarine_slab",
                  "roof_full": "minecraft:dark_prismarine", "wall": "minecraft:stripped_crimson_hyphae"})
    top = p.roof_hip(x0, deck + 5, z0, x1, z1, role="roof", slab_role="roof_slab", full_role="roof_full", overhang=1,
                     eave_curl=True, tiers=2, tier_gap=2, tier_band=3, band_role="wall")
    p.put(cx, top, (z0 + z1) // 2, "minecraft:gold_block")
    p.put(cx, top + 1, (z0 + z1) // 2, "minecraft:end_rod", {"facing": "up"})
    for (lx, lz) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        chain(p, lx, deck + 4, lz)
        lantern(p, lx, deck + 3, lz)
    # helm on a raised step forward of the pagoda
    for dx in (-1, 0, 1):
        slab(p, cx + dx, deck + 1, 14, "minecraft:dark_oak_slab", "bottom")
    helm(p, cx, deck + 1, 15, BAHTERA_MAT)
    stair(p, cx, deck + 1, 14, "minecraft:dark_oak_stairs", "north")
    seat_cells = [(cx, 14)]
    for z in (19, 21, 23, 25, 27):
        stair(p, cx - 4, deck + 1, z, "minecraft:dark_oak_stairs", "west")
        stair(p, cx + 4, deck + 1, z, "minecraft:dark_oak_stairs", "east")
        seat_cells += [(cx - 4, z), (cx + 4, z)]
    stair(p, cx, deck + 1, 31, "minecraft:dark_oak_stairs", "north")
    seat_cells.append((cx, 31))
    # three masts
    for mz, top_y, half in ((17, 24, 6), (24, 22, 5), (30, 17, 4)):
        mast(p, cx, mz, deck + 1, top_y, BAHTERA_MAT, yard_y=top_y, yard_half=half)
        for dx in range(-half, half + 1):
            if dx:
                log(p, cx + dx, deck + 4, mz, "minecraft:stripped_dark_oak_log", "x")
    for lx in (cx - 6, cx + 6):
        chain(p, lx, 23, 17)
        lantern(p, lx, 22, 17)
    # the ram: a gold-shod prow with a blackstone beak and a soul-lantern eye
    bz = L - 1
    for y in range(deck - 2, deck + 2):
        p.put(cx, y, bz, "minecraft:polished_blackstone_bricks")
    p.put(cx, deck + 2, bz, "minecraft:gold_block")
    stair(p, cx, deck + 3, bz, "minecraft:polished_blackstone_brick_stairs", "north")
    p.put(cx, deck + 2, bz - 1, "minecraft:gold_block")
    lantern(p, cx, deck + 3, bz - 1, hanging=False, soul=True)
    # stern: a five-lantern engine and the tall banner pole
    for dx in (-2, -1, 0, 1, 2):
        p.put(cx + dx, deck - 2, 0, "minecraft:sea_lantern")
    for dx in (-3, 3):
        p.put(cx + dx, deck - 2, 0, "minecraft:gold_block")
    for y in range(top + 1, top + 5):
        p.put(cx, y, z0, "minecraft:dark_oak_fence")
    seats = []
    for (x, z) in seat_cells:
        s = loc(p, x, deck + 1, z)
        s[1] = deck + 1 + 0.5
        seats.append(s)
    extra = {
        "origin": [W / 2.0, 0.0, L / 2.0],
        "deck": deck + 1.0,
        "seats": seats,
        "helm": loc(p, cx, deck + 2, 15),
        "sails": [sail(p, cx, 17, deck + 4.4, 23.8, 6.4, 0.0, 1), sail(p, cx, 24, deck + 4.4, 21.8, 5.4, 0.0, 1),
                  sail(p, cx, 30, deck + 4.4, 16.8, 4.4, 0.0, 1)],
        "lights": [light(p, x0 - 1, deck + 3, z0 - 1, 0xFFC66A), light(p, x1 + 1, deck + 3, z0 - 1, 0xFFC66A),
                   light(p, x0 - 1, deck + 3, z1 + 1, 0xFFC66A), light(p, x1 + 1, deck + 3, z1 + 1, 0xFFC66A),
                   light(p, cx - 6, 22, 17, 0xFFC66A), light(p, cx + 6, 22, 17, 0xFFC66A),
                   light(p, cx, deck + 3, bz - 1, 0x7FD8FF, 0.8)],
        "engine": [loc(p, cx + dx, deck - 2, 0) for dx in (-2, -1, 0, 1, 2)],
        "banner": banner(p, cx, top + 4, z0, 2.0, 3.6),
        "wake": [W * 0.5, L * 0.5],
        "deckTiles": deck_tiles(cells, W, L, 3, skip_rect=(x0, z0, x1, z1)),
    }
    return p, extra, cells


def sail(p, cx, mz, y0, y1, half, bias, kind):
    """A batten sail on the mast at z=mz: a quad from y0 to y1, -half..half across, just aft of the mast."""
    w, _, l = p.size
    return {"z": round(mz + 0.5 - l / 2.0 - 0.6, 3), "y0": y0, "y1": y1, "x0": round(-half + bias, 3),
            "x1": round(half + bias, 3), "kind": kind}


def light(p, x, y, z, rgb, size=1.0):
    o = loc(p, x, y, z)
    o[1] += 0.45
    return o + ["#%06X" % rgb, size]


def banner(p, cx, y, z, w, h):
    o = loc(p, cx, y, z)
    return {"x": o[0], "y": float(y) + 0.9, "z": o[2], "w": w, "h": h}


def deck_tiles(cells, W, L, size, skip_rect=None):
    """The deck colliders (DESIGN 34.5.15, entity kapal_dek): rectangles of walkable deck inside the bulwark, each
    [centre x, centre z, width x, depth z] in local blocks, at most 10. Rows with the same walkable segments merge into one
    rectangle (at most `depth` rows deep, deepened until 10 are enough); the cabin is left out (its side walks stay)."""
    walk = interior(cells)
    if skip_rect:
        x0, z0, x1, z1 = skip_rect
        walk = {(x, z) for (x, z) in walk if not (x0 <= x <= x1 and z0 <= z <= z1)}
    rows = {}
    for (x, z) in walk:
        rows.setdefault(z, set()).add(x)

    def segments(xs):
        out, run = [], []
        for x in sorted(xs):
            if run and x != run[-1] + 1:
                out.append((run[0], run[-1]))
                run = []
            run.append(x)
        if run:
            out.append((run[0], run[-1]))
        return tuple(seg for seg in out if seg[1] - seg[0] + 1 >= 2)

    for depth in range(size, 40):
        rects = []
        zs = sorted(rows)
        i = 0
        while i < len(zs):
            segs = segments(rows[zs[i]])
            j = i
            while (j + 1 < len(zs) and zs[j + 1] == zs[j] + 1 and segments(rows[zs[j + 1]]) == segs
                   and j + 1 - i < depth):
                j += 1
            for (a, b) in segs:
                rects.append((a, zs[i], b, zs[j]))
            i = j + 1
        if len(rects) <= 10:
            break
    out = []
    for (a, za, b, zb) in sorted(rects, key=lambda r: (r[1], r[0])):
        w, d = b - a + 1, zb - za + 1
        out.append([round(a + w / 2.0 - W / 2.0, 3), round(za + d / 2.0 - L / 2.0, 3), float(w), float(d)])
    return out


def interior(cells):
    out = set()
    for (x, z) in cells:
        if all((x + dx, z + dz) in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            out.add((x, z))
    return out


def gable_fill(p, x0, y, x1, zs, bid):
    """Fills the triangular gable ends under a roof whose ridge runs along z (overhang 1)."""
    layers = (x1 - x0 + 3) // 2
    for i in range(layers):
        for x in range(x0 + i, x1 - i + 1):
            for z in zs:
                p.put(x, y + i, z, bid)


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    out = {}
    for name, builder in (("perahu_awan", build_perahu), ("kapal_sekte", build_sekte), ("bahtera_langit", build_bahtera)):
        p, extra, cells = builder()
        model = export(name, p, extra)
        out[name] = model
        print("== %s  size %s  blocks %d  runs %d (core %d)" % (name, model["size"], model["blocks"], len(model["runs"]),
                                                                model["core"]))
        print("   deck top %.1f  seats %d  deck tiles %d" % (model["deck"], len(model["seats"]), len(model["deckTiles"])))
        print("   JAVA seats: " + ", ".join("{%sF, %sF, %sF}" % tuple(s) for s in model["seats"]))
        print("   JAVA tiles: " + ", ".join("{%sF, %sF, %sF, %sF}" % tuple(t) for t in model["deckTiles"]))
        print("   JAVA helm: %s" % model["helm"])
        if preview:
            os.makedirs(preview, exist_ok=True)
            with open(os.path.join(preview, name + "_side.txt"), "w", encoding="utf-8") as f:
                f.write(side_view(p) + "\n")
    validate_all()


if __name__ == "__main__":
    main()
