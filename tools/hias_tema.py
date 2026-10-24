"""Interior dressing for the themed-town generators of D-TK1 (pelabuhan, air, gunung; DESIGN 38.T.3/38.T.5/38.T.6).

`ruang(p, x0, z0, x1, z1, f, gaya)` dresses one room of a canvas (Kanvas / Lot / Piece): (x0..x1, z0..z1) is the
INNER floor area (walls outside it), f the floor block level (things stand on f + 1). It only ever fills empty cells
(missing or air) that stand on a solid floor, never touches a block the builder placed, keeps every cell in front of a
doorway, every life point (titik) and the cell in front of it free, and draws from its own seeded random stream, so the
rest of a generator's output does not change when a room is dressed. The result is deterministic.

Wall slots get furniture of the room style (inn room, tavern, office, store, workshop, kitchen, shrine, living room,
tea room, boat house); the open middle of a large enough room gets a rug. Every block is a vanilla 26.3 block; the
"utara" style processors still swap acacia/jungle wood because nothing here uses them.
"""
import random

import kota_besar_kit as K

_NOT_FLOOR = K._NOT_SOLID + ("farmland", "path", "water", "lava")
_SEMUA_ARAH = (("north", 0, -1), ("south", 0, 1), ("west", -1, 0), ("east", 1, 0))
_OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}


def _kosong(p, x, y, z):
    if not p.inside(x, y, z):
        return False
    b = p.get(x, y, z)
    return b is None or b in ("minecraft:air", "minecraft:cave_air")


def _padat(p, x, y, z):
    """A full block a thing can stand on or lean against."""
    if not p.inside(x, y, z):
        return False
    b = p.get(x, y, z)
    if b is None or b in ("minecraft:air", "minecraft:cave_air"):
        return False
    name = b.split(":", 1)[1]
    if name in ("barrel", "bookshelf", "chiseled_bookshelf", "hay_block", "crafting_table", "smoker", "furnace"):
        return True
    return not any(t in name for t in _NOT_FLOOR)


def taruh(p, x, y, z, bid, props=None, nbt=None):
    """Places `bid` only into an empty cell; True when placed."""
    if not _kosong(p, x, y, z):
        return False
    p.set(x, y, z, bid, props, nbt)
    return True


def _bebas(p):
    """Cells that must stay walkable: every life point and the cell it faces."""
    out = set()
    ox, oz = getattr(p, "ox", 0), getattr(p, "oz", 0)      # city-addressed canvases keep titik canvas-local
    for t in getattr(p, "titik_list", []):
        x, y, z = t["pos"]
        x, z = x + ox, z + oz
        out.add((x, y, z))
        out.add((x, y + 1, z))
        dx, dz = K.FACE.get(t.get("hadap", "south"), (0, 1))
        out.add((x + dx, y, z + dz))
    return out


# ------------------------------------------------------------------------------------------------ furniture pieces
def _lemari(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:barrel", {"facing": hadap, "open": "false"})
    if r.random() < 0.6:
        taruh(p, x, y + 1, z, "minecraft:barrel", {"facing": hadap, "open": "false"})
    elif r.random() < 0.5:
        taruh(p, x, y + 1, z, r.choice(_POT))


def _rak(p, x, y, z, hadap, r):
    """A two-high bookcase (scrolls and ledgers)."""
    taruh(p, x, y, z, "minecraft:bookshelf")
    if r.random() < 0.75:
        taruh(p, x, y + 1, z, "minecraft:bookshelf")


def _cuci(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:water_cauldron", {"level": str(r.choice((1, 2, 3)))})


def _pot(p, x, y, z, hadap, r):
    taruh(p, x, y, z, r.choice(_POT))


def _lilin_meja(p, x, y, z, hadap, r):
    if taruh(p, x, y, z, "minecraft:spruce_trapdoor", {"facing": hadap, "half": "top", "open": "false", "powered": "false",
                                                       "waterlogged": "false"}):
        taruh(p, x, y + 1, z, "minecraft:%s_candle" % r.choice(("white", "red", "yellow")),
              {"candles": str(r.choice((1, 2, 3))), "lit": "true", "waterlogged": "false"})


def _peti(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:chest", {"facing": hadap, "type": "single", "waterlogged": "false"}, {"id": "minecraft:chest"})


def _tong_rebah(p, x, y, z, hadap, r):
    """A wine/water cask lying on its side, sometimes two high."""
    ax = "east" if hadap in ("north", "south") else "south"
    taruh(p, x, y, z, "minecraft:barrel", {"facing": ax, "open": "false"})
    if r.random() < 0.45:
        taruh(p, x, y + 1, z, "minecraft:barrel", {"facing": ax, "open": "false"})


def _peti_kayu(p, x, y, z, hadap, r):
    """A crate stack: barrels or hay, sometimes two high."""
    if r.random() < 0.7:
        taruh(p, x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    else:
        taruh(p, x, y, z, "minecraft:hay_block", {"axis": "y"})
    if r.random() < 0.5:
        taruh(p, x, y + 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})


def _tali(p, x, y, z, hadap, r):
    """A coil of rope on a hook: chains hanging from the ceiling height."""
    taruh(p, x, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    taruh(p, x, y + 1, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def _meja_kerja(p, x, y, z, hadap, r):
    taruh(p, x, y, z, r.choice(("minecraft:smithing_table", "minecraft:fletching_table", "minecraft:crafting_table",
                                "minecraft:cartography_table")))


def _gerinda(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:grindstone", {"face": "floor", "facing": hadap})


def _landasan(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:anvil" if r.random() < 0.6 else "minecraft:chipped_anvil",
          {"facing": "east" if hadap in ("north", "south") else "north"})


def _tungku(p, x, y, z, hadap, r):
    taruh(p, x, y, z, r.choice(("minecraft:smoker", "minecraft:furnace")), {"facing": hadap, "lit": "false"})


def _kuali(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:cauldron")


def _kompos(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:composter", {"level": str(r.choice((0, 3, 5, 7)))})


def _guci(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:decorated_pot", {"cracked": "false", "facing": hadap, "waterlogged": "false"})


def _lilin_lantai(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:%s_candle" % r.choice(("red", "yellow", "white")),
          {"candles": str(r.choice((2, 3, 4))), "lit": "true", "waterlogged": "false"})


def _mimbar(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:lectern", {"facing": hadap, "has_book": "false", "powered": "false"})


def _bangku(p, x, y, z, hadap, r):
    taruh(p, x, y, z, "minecraft:spruce_stairs", {"facing": _OPP[hadap], "half": "bottom", "shape": "straight",
                                                  "waterlogged": "false"})


def _rak_teh(p, x, y, z, hadap, r):
    """A tea shelf: a closed bamboo board with a tea jar or a small plant on it."""
    if taruh(p, x, y, z, "minecraft:bamboo_trapdoor", {"facing": hadap, "half": "top", "open": "false", "powered": "false",
                                                       "waterlogged": "false"}):
        if r.random() < 0.6:
            taruh(p, x, y + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": hadap, "waterlogged": "false"})
        else:
            taruh(p, x, y + 1, z, r.choice(("minecraft:potted_fern", "minecraft:potted_bamboo", "minecraft:flower_pot")))


def _jala(p, x, y, z, hadap, r):
    """A folded net on a barrel (a carpet of net cloth on top)."""
    if taruh(p, x, y, z, "minecraft:barrel", {"facing": "up", "open": "false"}):
        taruh(p, x, y + 1, z, r.choice(("minecraft:white_carpet", "minecraft:light_gray_carpet", "minecraft:brown_carpet")))


def _dayung(p, x, y, z, hadap, r):
    """Oars leaning on the wall: two fences (an oar = fence + trapdoor blade)."""
    taruh(p, x, y, z, "minecraft:spruce_fence", {"north": "false", "south": "false", "east": "false", "west": "false",
                                                 "waterlogged": "false"})
    taruh(p, x, y + 1, z, "minecraft:spruce_fence", {"north": "false", "south": "false", "east": "false", "west": "false",
                                                     "waterlogged": "false"})


_POT = ("minecraft:potted_fern", "minecraft:potted_azalea_bush", "minecraft:potted_bamboo", "minecraft:potted_red_tulip",
        "minecraft:potted_lily_of_the_valley", "minecraft:potted_flowering_azalea_bush", "minecraft:potted_spruce_sapling")

# style -> [(piece, weight)]
GAYA = {
    "inap": [(_lemari, 3), (_cuci, 2), (_pot, 2), (_lilin_meja, 2), (_peti, 1), (_rak, 1), (_bangku, 1)],
    "kedai": [(_tong_rebah, 4), (_peti_kayu, 2), (_pot, 1), (_bangku, 3), (_lilin_meja, 2), (_guci, 1)],
    "kantor": [(_rak, 4), (_mimbar, 1), (_peti, 2), (_meja_kerja, 1), (_pot, 1), (_lilin_meja, 1), (_lemari, 1)],
    "gudang": [(_peti_kayu, 5), (_tong_rebah, 3), (_peti, 1), (_tali, 1), (_jala, 1)],
    "bengkel": [(_meja_kerja, 2), (_gerinda, 2), (_landasan, 1), (_peti_kayu, 2), (_tali, 2), (_peti, 1), (_tong_rebah, 1)],
    "dapur": [(_tungku, 3), (_kuali, 2), (_tong_rebah, 2), (_kompos, 1), (_guci, 2), (_peti_kayu, 1)],
    "kuil": [(_lilin_lantai, 3), (_pot, 2), (_guci, 2), (_mimbar, 1), (_bangku, 1)],
    "rumah": [(_lemari, 3), (_pot, 2), (_peti, 1), (_cuci, 1), (_lilin_meja, 1), (_guci, 1), (_bangku, 1)],
    "teh": [(_rak_teh, 4), (_guci, 2), (_pot, 2), (_lilin_meja, 1), (_bangku, 1)],
    "perahu": [(_dayung, 3), (_jala, 3), (_peti_kayu, 2), (_tali, 2), (_tong_rebah, 1), (_meja_kerja, 1)],
}

RUG = {"inap": ("minecraft:blue_carpet", "minecraft:white_carpet"), "kantor": ("minecraft:red_carpet", "minecraft:yellow_carpet"),
       "kuil": ("minecraft:yellow_carpet", "minecraft:red_carpet"), "rumah": ("minecraft:brown_carpet", "minecraft:light_gray_carpet"),
       "teh": ("minecraft:green_carpet", "minecraft:lime_carpet"), "kedai": ("minecraft:red_carpet", "minecraft:brown_carpet")}


def ruang(p, x0, z0, x1, z1, f, gaya, isi=0.5, rug=True, seed=0):
    """Dresses the room (x0..x1, z0..z1) on floor f in style `gaya`; returns the number of pieces placed."""
    x0, x1 = min(x0, x1), max(x0, x1)
    z0, z1 = min(z0, z1), max(z0, z1)
    r = random.Random((x0 * 73856093) ^ (z0 * 19349663) ^ (f * 83492791) ^ (x1 * 7919) ^ (z1 * 104729) ^ sum(ord(c) * 31 ** i for i, c in enumerate(gaya)) % 1000003 ^ seed)
    y = f + 1
    keep = _bebas(p)
    # doorway fronts: an inner cell whose neighbour on the boundary ring is open at standing height
    pintu = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for _a, dx, dz in _SEMUA_ARAH:
                nx, nz = x + dx, z + dz
                on_ring = nx in (x0 - 1, x1 + 1) or nz in (z0 - 1, z1 + 1)
                if on_ring and not _padat(p, nx, y, nz) and not _padat(p, nx, y + 1, nz):
                    for ex in (-1, 0, 1):
                        for ez in (-1, 0, 1):
                            pintu.add((x + ex, z + ez))
                    pintu.add((x - dx, z - dz))
                    pintu.add((x - 2 * dx, z - 2 * dz))
    # climbing and seating: keep the cells beside stairs, chairs, ladders and scaffolding (and one further out) free
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for yy in (y - 1, y, y + 1):
                b = p.get(x, yy, z) if p.inside(x, yy, z) else None
                if b and ("stairs" in b or "ladder" in b or "scaffolding" in b):
                    for _a, dx, dz in _SEMUA_ARAH:
                        pintu.add((x + dx, z + dz))
                        pintu.add((x + 2 * dx, z + 2 * dz))
                    break
    slots = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) in pintu or (x, y, z) in keep or not _kosong(p, x, y, z) or not _padat(p, x, f, z):
                continue
            walls = [a for a, dx, dz in _SEMUA_ARAH if _padat(p, x + dx, y, z + dz) and _padat(p, x + dx, y + 1, z + dz)
                     and (x + dx in (x0 - 1, x1 + 1) or z + dz in (z0 - 1, z1 + 1))]
            if walls:
                slots.append((x, z, _OPP[walls[0]]))
    pieces = GAYA[gaya]
    total = sum(w for _f, w in pieces)
    placed = 0
    taken = set()
    for (x, z, hadap) in slots:
        if r.random() > isi or (x, z) in taken:
            continue
        # never two pieces side by side along a wall more than twice: leave gaps so it reads as furniture, not a wall
        if (x - 1, z) in taken and (x - 2, z) in taken or (x, z - 1) in taken and (x, z - 2) in taken:
            continue
        roll = r.random() * total
        for fn, w in pieces:
            roll -= w
            if roll <= 0:
                break
        if (x, y + 1, z) in keep:
            continue
        fn(p, x, y, z, hadap, r)
        taken.add((x, z))
        placed += 1
    if rug and gaya in RUG and (x1 - x0) >= 4 and (z1 - z0) >= 4:
        main, rim = RUG[gaya]
        # a centred rug about half the room (never wall to wall), at most 9 x 7
        w = min(x1 - x0 - 3, max(3, (x1 - x0 + 1) // 2), 9)
        d = min(z1 - z0 - 3, max(3, (z1 - z0 + 1) // 2), 7)
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        rx0, rz0 = cx - w // 2, cz - d // 2
        rx1, rz1 = rx0 + w - 1, rz0 + d - 1
        for x in range(rx0, rx1 + 1):
            for z in range(rz0, rz1 + 1):
                if (x, y, z) in keep or not _padat(p, x, f, z):
                    continue
                taruh(p, x, y, z, rim if x in (rx0, rx1) or z in (rz0, rz1) else main)
    return placed
