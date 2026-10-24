"""Detail kit of the capital generator (DESIGN 38.B, module 38KS); used only by tools/gen_tempat_kota_besar.py.

A district is drawn on one Kanvas (144 x H x 144, or any size, without the 48-block template limit) so streets and
buildings that span two or four lots stay continuous; potong() then cuts it into 48 x H x 48 lots (tempat_lib.Lot)
and every block, entity (anchor) and life point (titik) goes to the lot it stands in. Fence / wall / pane connections are
written on the whole canvas before cutting (tata_letak places with knownShape, nothing would join them in the world).

Craft rules (same as the town generator, DESIGN 34.5.1): every hall stands on a stone podium with steps, has red lacquer
columns, white plaster between dark beams, lattice windows, a beam frieze, dougong brackets under the eaves and a roof
with curled eaves and a ridge; rooms are furnished; yards are gardens; lanterns hang from chains or stand on posts.
Forbidden in 38 templates (tools/cek_tempat.py): fire, lava, tnt, spawner, lightning_rod, and lit campfires outside
kitchen/alchemy templates. Blocks swap per biome style through processors (dark oak -> spruce / jungle / cherry, stone
brick -> sandstone), so the kit uses the dark oak and stone brick families on purpose.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
from structgen import Piece  # noqa: E402

LY = tl.LANTAI_Y          # street floor inside a lot
FOLDER = "kota_besar"

# ===================================================================================================== palette
PAL = dict(tl.PALET["kota_besar"])
PAL.update({
    "plaster": "minecraft:white_terracotta", "plaster_alt": "minecraft:calcite", "beam": "minecraft:stripped_dark_oak_log",
    "red": "minecraft:red_terracotta", "red_wall": "minecraft:red_nether_brick_wall",
    "kerb": "minecraft:stone_bricks", "kerb_alt": "minecraft:mossy_stone_bricks", "kerb_wall": "minecraft:stone_brick_wall",
    "kerb_stairs": "minecraft:stone_brick_stairs", "kerb_slab": "minecraft:stone_brick_slab",
    "kerb_chisel": "minecraft:chiseled_stone_bricks", "paving": "minecraft:polished_andesite", "paving_alt": "minecraft:andesite",
    "paving_slab": "minecraft:polished_andesite_slab", "paving_stairs": "minecraft:polished_andesite_stairs",
    "gutter": "minecraft:smooth_stone", "rail": "minecraft:dark_oak_fence", "wood": "minecraft:dark_oak_planks",
    "wood_alt": "minecraft:spruce_planks", "wood_stairs": "minecraft:dark_oak_stairs", "wood_slab": "minecraft:dark_oak_slab",
    "wood_trapdoor": "minecraft:dark_oak_trapdoor", "wood_fence": "minecraft:dark_oak_fence",
    "wood_gate": "minecraft:dark_oak_fence_gate", "log": "minecraft:dark_oak_log", "shelf": "minecraft:dark_oak_shelf",
    "door": "minecraft:dark_oak_door", "light_wood": "minecraft:spruce_planks", "light_slab": "minecraft:spruce_slab",
    "light_stairs": "minecraft:spruce_stairs", "lamp": "minecraft:ochre_froglight", "banner": "red", "banner_alt": "yellow",
    "cloth": "minecraft:red_wool", "cloth_alt": "minecraft:white_wool", "ground": "minecraft:grass_block",
    "ground_alt": "minecraft:podzol", "statue": "minecraft:polished_andesite", "statue_top": "minecraft:chiseled_stone_bricks",
    "flower_tall": "minecraft:peony", "flower_tall_alt": "minecraft:rose_bush", "flower": "minecraft:allium",
    "flower_alt": "minecraft:lily_of_the_valley", "bush": "minecraft:azalea", "leaves": "minecraft:spruce_leaves",
    "trunk": "minecraft:spruce_log", "bed": "minecraft:red_bed", "rug": "minecraft:red_carpet", "rug_alt": "minecraft:brown_carpet",
    "potted": "minecraft:potted_bamboo", "tile": "minecraft:deepslate_tiles", "tile_stairs": "minecraft:deepslate_tile_stairs",
    "tile_slab": "minecraft:deepslate_tile_slab", "imperial": "minecraft:smooth_quartz", "imperial_slab": "minecraft:smooth_quartz_slab",
    "imperial_stairs": "minecraft:smooth_quartz_stairs", "gold": "minecraft:gold_block", "jade": "minecraft:prismarine_bricks",
    "glass": "minecraft:white_stained_glass_pane",
})

FACE = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}


# ===================================================================================================== the canvas
class Kanvas(Piece):
    """A Piece of any size (no 48 limit) that keeps anchors and life points for potong()."""

    def __init__(self, sx, sy, sz, seed, palette=None):  # noqa: D401 - deliberately skips Template's size check
        self.size = (sx, sy, sz)
        self.cells = {}
        self.entities = []
        self.titik_list = []
        self.sisi_map = None
        self.rng = random.Random(seed)
        self.pal = dict(palette or PAL)
        self.clipped = 0
        self.lights = []

    def titik(self, jenis, x, y, z, hadap="south", data=None):
        if not tl.titik_sah(jenis):
            raise ValueError("unknown titik type %r" % jenis)
        if not self.inside(x, y, z):
            self.clipped += 1
            return None
        if hadap not in FACE:
            raise ValueError("bad hadap %r" % hadap)
        e = {"jenis": jenis, "pos": [int(x), int(y), int(z)], "hadap": hadap}
        if data:
            e["data"] = {str(k): str(v) for k, v in data.items()}
        self.titik_list.append(e)
        return e

    def kotak(self, jenis, x0, y0, z0, x1, y1, z1, hadap="south", **data):
        lo = (min(x0, x1), min(y0, y1), min(z0, z1))
        hi = (max(x0, x1), max(y0, y1), max(z0, z1))
        d = {"dx": hi[0] - lo[0], "dy": hi[1] - lo[1], "dz": hi[2] - lo[2]}
        d.update(data)
        return self.titik(jenis, lo[0], lo[1], lo[2], hadap, d)

    def jalur(self, pts, jalur="a", y=None):
        yy = LY + 1 if y is None else y
        for i, p in enumerate(pts):
            self.titik("jalan", p[0], p[2] if len(p) > 2 else yy, p[1], "south", {"urut": i, "jalur": jalur})

    def jangkar(self, x, y, z, kind, yaw=0.0, **data):
        """A DuniaAnchor (kultivator:dunia_anchor); ints stay NBT ints (protect, tier, nomor), lists stay lists."""
        payload = {"id": "kultivator:dunia_anchor", "kind": kind, "Rotation": [structgen.nbt.Float(yaw), structgen.nbt.Float(0.0)]}
        d = {}
        for k, v in data.items():
            d[k] = v if isinstance(v, (int, list)) else str(v)
        if d:
            payload["data"] = d
        self.entity(x, y, z, payload)

    def entity(self, x, y, z, nbt):
        bx, by, bz = int(x // 1), int(y // 1), int(z // 1)
        if not self.inside(bx, by, bz):
            raise IndexError("entity (%r,%r,%r) outside canvas %r" % (x, y, z, self.size))
        self.entities.append((float(x), float(y), float(z), dict(nbt)))


def potong(k, nama, tinggi_min=24, folder=FOLDER, ox=0, oz=0, nx=None, nz=None):
    """Cuts canvas k into 48 x H x 48 lots. nama[(i, j)] = template path inside folder (None skips that lot). Each lot's
    height is its highest used block + 2 (at least tinggi_min, so beard_box carves the hills over the street).
    Returns {(i, j): Lot}."""
    connect_all(k)
    sx, sy, sz = k.size
    nx = nx or (sx - ox) // tl.LOT
    nz = nz or (sz - oz) // tl.LOT
    out = {}
    for i in range(nx):
        for j in range(nz):
            name = nama.get((i, j))
            if not name:
                continue
            x0, z0 = ox + i * tl.LOT, oz + j * tl.LOT
            top = 0
            cells = []
            for (x, y, z), cell in k.cells.items():
                if x0 <= x < x0 + tl.LOT and z0 <= z < z0 + tl.LOT:
                    cells.append(((x - x0, y, z - z0), cell))
                    if cell[0][0] != "minecraft:air":
                        top = max(top, y)
            h = max(tinggi_min, min(sy, top + 2))
            lot = tl.Lot(folder, name, tinggi=h, palet=PAL)
            for (x, y, z), cell in cells:
                if y < h:
                    lot.cells[(x, y, z)] = cell
            for (x, y, z, nbt) in k.entities:
                if x0 <= x < x0 + tl.LOT and z0 <= z < z0 + tl.LOT:
                    lot.entities.append((x - x0, y, z - z0, nbt))
                    if nbt.get("id") == "kultivator:dunia_anchor":
                        lot.jangkar_n += 1
            for t in k.titik_list:
                px, py, pz = t["pos"]
                if x0 <= px < x0 + tl.LOT and z0 <= pz < z0 + tl.LOT:
                    e = dict(t)
                    e["pos"] = [px - x0, py, pz - z0]
                    if "data" in t:
                        e["data"] = dict(t["data"])
                        # a box point keeps its size, clipped to the lot
                        if t["jenis"] in tl.TITIK_KOTAK:
                            e["data"]["dx"] = str(min(int(t["data"]["dx"]), x0 + tl.LOT - 1 - px))
                            e["data"]["dz"] = str(min(int(t["data"]["dz"]), z0 + tl.LOT - 1 - pz))
                    if py < h:
                        lot.titik_list.append(e)
            out[(i, j)] = lot
    return out


# ===================================================================================================== shape pass
_NOT_SOLID = ("air", "stairs", "slab", "fence", "_wall", "pane", "bars", "lantern", "chain", "carpet", "trapdoor", "door",
              "flower", "potted", "pot", "short_grass", "fern", "bamboo", "torch", "candle", "button", "plate", "sign", "banner",
              "_bed", "chest", "campfire", "lectern", "anvil", "cauldron", "brewing", "grindstone", "bell", "rod", "petals",
              "lily", "water", "leaves", "sapling", "rose", "peony", "lilac", "tulip", "allium", "lily_of", "berry", "dead_bush",
              "grass", "azalea", "snow", "shelf", "ladder", "scaffolding", "stonecutter", "composter", "cluster", "head",
              "skull", "amethyst_bud", "hopper", "vine", "dripleaf", "pickle", "egg")
_SOLID_OK = ("hay_block", "melon", "pumpkin", "barrel", "bookshelf", "target", "smithing_table", "cartography_table", "loom",
             "chiseled_bookshelf", "blast_furnace", "smoker", "furnace", "note_block", "crafting_table", "fletching_table")


def _family(bid):
    n = bid.split(":")[-1]
    if n.endswith("_fence") and "nether" not in n:
        return "wood_fence"
    if n.endswith("_fence"):
        return "nether_fence"
    if n.endswith("_wall") and "banner" not in n and "sign" not in n and "torch" not in n:
        return "wall"
    if n.endswith("_pane") or n == "iron_bars" or n.endswith("_bars"):
        return "pane"
    return None


def solid(bid):
    if bid is None:
        return False
    n = bid.split(":")[-1]
    if n in _SOLID_OK:
        return True
    if n.endswith("_wall") and "banner" not in n:
        return False
    return not any(t in n for t in _NOT_SOLID)


def connect_all(p):
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
            if nb is not None and nb != "minecraft:air":
                nf = _family(nb)
                nn = nb.split(":")[-1]
                if fam in ("wood_fence", "nether_fence"):
                    ok = nf == fam or nn.endswith("_fence_gate") or solid(nb)
                elif fam == "wall":
                    ok = nf in ("wall", "pane") or nn.endswith("_fence_gate") or solid(nb)
                else:
                    ok = nf in ("pane", "wall") or solid(nb)
            conn[d] = ok
        new = dict(props)
        if fam == "wall":
            above = p.get(x, y + 1, z)
            tall = above is not None and (solid(above) or _family(above) == "wall")
            for d in dirs:
                new[d] = ("tall" if tall else "low") if conn[d] else "none"
            straight = (conn["north"] and conn["south"] and not conn["east"] and not conn["west"]) or \
                       (conn["east"] and conn["west"] and not conn["north"] and not conn["south"])
            new["up"] = "false" if straight and (above is None or above == "minecraft:air") else "true"
        else:
            for d in dirs:
                new[d] = "true" if conn[d] else "false"
        new.setdefault("waterlogged", "false")
        changes.append((pos, bid, new, data))
    for (x, y, z), bid, new, data in changes:
        p.set(x, y, z, bid, new, data)


# ===================================================================================================== small details
def axis(p, role, ax="y"):
    bid = p.b(role)
    if any(bid.endswith(t) for t in ("_log", "_wood", "_pillar", "_stem", "_hyphae", "basalt", "hay_block", "bone_block",
                                     "bamboo_block", "froglight", "chain")):
        return {"axis": ax}
    return None


def put_ax(p, x, y, z, role, ax="y"):
    p.put(x, y, z, role, axis(p, role, ax))


def finial(p, x, y, z, kind="pearl"):
    """A ridge pearl (gold) on a small stone base, or a lantern finial. Never a lightning rod (38 forbids it)."""
    if not p.inside(x, y, z):
        return
    if kind == "pearl":
        p.put(x, y, z, "gold")
    elif kind == "lantern":
        p.put(x, y, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    elif kind == "spire":
        p.put(x, y, z, "gold")
        for i in range(1, 3):
            if p.inside(x, y + i, z):
                p.put(x, y + i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        if p.inside(x, y + 3, z):
            p.put(x, y + 3, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def gable(p, x0, y, z0, x1, z1, along="x", overhang=1, pearl=True, role="roof", slab_role="roof_slab", full_role="roof_full"):
    top = p.roof_gable(x0, y, z0, x1, z1, role=role, slab_role=slab_role, full_role=full_role, along=along,
                       overhang=overhang, eave_curl=True)
    if pearl:
        if along == "x":
            rows = (z1 + overhang) - (z0 - overhang) + 1
            mz = (z0 - overhang) + rows // 2 - (0 if rows % 2 else 1)
            finial(p, (x0 + x1) // 2, top + 1, mz)
        else:
            rows = (x1 + overhang) - (x0 - overhang) + 1
            mx = (x0 - overhang) + rows // 2 - (0 if rows % 2 else 1)
            finial(p, mx, top + 1, (z0 + z1) // 2)
    return top


def hip(p, x0, y, z0, x1, z1, overhang=1, tiers=1, tier_gap=2, tier_band=2, band_role="plaster", top="pearl",
        role="roof", slab_role="roof_slab", full_role="roof_full"):
    yy = p.roof_hip(x0, y, z0, x1, z1, role=role, slab_role=slab_role, full_role=full_role, overhang=overhang,
                    eave_curl=True, tiers=tiers, tier_gap=tier_gap, tier_band=tier_band, band_role=band_role)
    if top:
        finial(p, (x0 + x1) // 2, yy, (z0 + z1) // 2, top)
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
    bid = p.b(role)
    axial = any(bid.endswith(t) for t in ("_log", "_wood", "_pillar", "_stem", "_hyphae"))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            p.put(x, y, z, role, {"axis": "x"} if axial else None)
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            p.put(x, y, z, role, {"axis": "z"} if axial else None)


def line(p, a, b, y, role, props=None):
    (ax, az), (bx, bz) = a, b
    n = max(abs(bx - ax), abs(bz - az))
    for i in range(n + 1):
        x = ax + (0 if bx == ax else (i if bx > ax else -i))
        z = az + (0 if bz == az else (i if bz > az else -i))
        p.put(x, y, z, role, props)


def wall_banner(p, x, y, z, facing, color=None):
    p.put(x, y, z, "minecraft:%s_wall_banner" % (color or p.pal["banner"]), {"facing": facing})


def banner_pole(p, x, y, z, height=4, color=None, rotation=0):
    for i in range(height):
        p.put(x, y + i, z, "wood_fence")
    p.put(x, y + height, z, "minecraft:%s_banner" % (color or p.pal["banner"]), {"rotation": str(rotation)})


def lit(p, x, y, z, chain=1, role="glow"):
    """A lantern hanging from `chain` chains under the ceiling block at y (paper lamp with role 'lamp')."""
    for i in range(chain):
        p.put(x, y - 1 - i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    if role == "lamp":
        p.put(x, y - 1 - chain, z, "lamp", {"axis": "y"})
    else:
        p.put(x, y - 1 - chain, z, role, {"hanging": "true", "waterlogged": "false"})
    if hasattr(p, "lights"):
        p.lights.append((x, y - 1 - chain, z))


def post(p, x, y, z, height=2, role="glow", post_role="wood_fence"):
    for i in range(height):
        p.put(x, y + i, z, post_role)
    p.put(x, y + height, z, role, {"hanging": "false", "waterlogged": "false"})
    if hasattr(p, "lights"):
        p.lights.append((x, y + height, z))


def stone_lantern(p, x, y, z):
    """A toro: stone foot, wall shaft, lantern house, slab cap."""
    p.put(x, y, z, "kerb_chisel")
    p.put(x, y + 1, z, "kerb_wall")
    p.put(x, y + 2, z, "glow", {"hanging": "false", "waterlogged": "false"})
    p.slab(x, y + 3, z, "kerb_slab")
    if hasattr(p, "lights"):
        p.lights.append((x, y + 2, z))


def table(p, x, y, z, top=None):
    p.put(x, y, z, "wood_fence")
    if top is None:
        p.put(x, y + 1, z, "minecraft:dark_oak_pressure_plate", {"powered": "false"})
    elif top:
        p.put(x, y + 1, z, top)


def low_table(p, x, y, z):
    """A low tea table (a closed top trapdoor) for floor cushions."""
    p.put(x, y, z, "wood_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                     "waterlogged": "false"})


def chair(p, x, y, z, facing):
    """A chair whose sitter looks toward `facing` (the stair's back is away from it)."""
    p.stairs(x, y, z, "wood_stairs", OPP[facing])


def bed(p, x, y, z, facing="north", role="bed"):
    dx, dz = FACE[facing]
    p.put(x, y, z, role, {"facing": facing, "part": "head", "occupied": "false"})
    p.put(x - dx, y, z - dz, role, {"facing": facing, "part": "foot", "occupied": "false"})


def shelf(p, x, y, z, facing):
    p.put(x, y, z, "shelf", {"facing": facing, "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})


def bookshelf_wall(p, x0, z0, x1, z1, y0, y1, chiseled_every=3, face="south"):
    for y in range(y0, y1 + 1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                if (x + z + y) % chiseled_every == 0:
                    p.put(x, y, z, "minecraft:chiseled_bookshelf",
                          {"facing": face, "slot_0_occupied": "true", "slot_1_occupied": "false", "slot_2_occupied": "true",
                           "slot_3_occupied": "true", "slot_4_occupied": "false", "slot_5_occupied": "true"})
                else:
                    p.put(x, y, z, "minecraft:bookshelf")


def rug(p, x0, z0, x1, z1, y, role="rug", border="rug_alt"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, y, z, border if (x in (x0, x1) or z in (z0, z1)) else role)


def candle(p, x, y, z, n=2, color="red"):
    p.put(x, y, z, "minecraft:%s_candle" % color, {"candles": str(n), "lit": "true", "waterlogged": "false"})


def tall_flower(p, x, y, z, role="flower_tall"):
    p.put(x, y, z, role, {"half": "lower"})
    if p.inside(x, y + 1, z):
        p.put(x, y + 1, z, role, {"half": "upper"})


def flowerbed(p, x0, z0, x1, z1, y, kerb=True):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if kerb and (x in (x0, x1) or z in (z0, z1)):
                p.slab(x, y, z, "kerb_slab", "top")
                continue
            p.put(x, y, z, "ground", {"snowy": "false"})
            r = p.rng.random()
            if r < 0.25:
                tall_flower(p, x, y + 1, z, "flower_tall" if r < 0.12 else "flower_tall_alt")
            elif r < 0.6:
                p.put(x, y + 1, z, "flower" if r < 0.45 else "flower_alt")
            elif r < 0.7:
                p.put(x, y + 1, z, "minecraft:short_grass")


def pot(p, x, y, z, plant=None):
    p.put(x, y, z, plant or p.rng.choice(("minecraft:potted_bamboo", "minecraft:potted_azalea_bush",
                                         "minecraft:potted_fern", "minecraft:potted_red_tulip")))


def lion(p, x, y, z, facing):
    p.put(x, y, z, "kerb")
    p.put(x, y + 1, z, "statue_top")
    p.stairs(x, y + 2, z, "kerb_stairs", OPP[facing])


def huabiao(p, x, y, z, height=7):
    p.put(x, y, z, "kerb")
    for i in range(1, height):
        p.put(x, y + i, z, "statue" if i % 2 else "statue_top")
    for d, (dx, dz) in FACE.items():
        if p.inside(x + dx, y + height - 2, z + dz):
            p.stairs(x + dx, y + height - 2, z + dz, "kerb_stairs", OPP[d], half="top")
    p.slab(x, y + height, z, "kerb_slab")


def incense_burner(p, x, y, z, big=False):
    """A ding: stone base, a cauldron body and a soul lantern on top (the smoke is a client particle, no fire)."""
    p.put(x, y, z, "kerb_chisel")
    for d, (dx, dz) in FACE.items():
        p.stairs(x + dx, y, z + dz, "kerb_stairs", OPP[d])
    p.put(x, y + 1, z, "minecraft:cauldron")
    p.put(x, y + 2, z, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    if big:
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            p.put(x + dx, y, z + dz, "minecraft:polished_andesite")
            p.put(x + dx, y + 1, z + dz, "kerb_wall")


def pond(p, cx, cz, rx, rz, y, lilies=5, rim=True):
    for x in range(cx - rx - 1, cx + rx + 2):
        for z in range(cz - rz - 1, cz + rz + 2):
            if not p.inside(x, y, z):
                continue
            d = ((x - cx) / (rx + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2
            if d <= 1.0:
                p.put(x, y - 1, z, "minecraft:clay")
                p.put(x, y, z, "minecraft:water", {"level": "0"})
                p.put(x, y + 1, z, "minecraft:air")
            elif d <= 1.7 and rim:
                p.put(x, y, z, "kerb_alt" if p.rng.random() < 0.35 else "minecraft:cobblestone")
    for _ in range(lilies):
        x = cx + p.rng.randint(-max(1, rx - 1), max(1, rx - 1))
        z = cz + p.rng.randint(-max(1, rz - 1), max(1, rz - 1))
        if p.get(x, y, z) == "minecraft:water":
            p.put(x, y + 1, z, "minecraft:lily_pad")


def bamboo_clump(p, x, z, y, height):
    for dy in range(height):
        p.put(x, y + dy, z, "minecraft:bamboo", {"age": "1", "stage": "0",
                                                 "leaves": "large" if dy >= height - 2 else "small" if dy >= height - 4 else "none"})


def leaf(p, x, y, z, role="leaves"):
    if p.inside(x, y, z) and p.get(x, y, z) in (None, "minecraft:air"):
        p.put(x, y, z, role, {"persistent": "true", "distance": "1", "waterlogged": "false"})


def pine(p, x, y, z, height=9):
    """An old pine: crooked trunk, flat layered crowns, one long branch."""
    for dy in range(height):
        dx = 1 if dy > height * 0.6 else 0
        p.put(x + dx, y + dy, z, "trunk", {"axis": "y"})
    for layer, (dy, r) in enumerate([(height - 5, 3), (height - 3, 2), (height - 1, 2), (height, 1)]):
        cx = x + (1 if dy > height * 0.6 else 0)
        for ox in range(-r, r + 1):
            for oz in range(-r, r + 1):
                if abs(ox) + abs(oz) <= r + (1 if layer == 0 else 0):
                    leaf(p, cx + ox, y + dy, z + oz)
    for i in range(1, 3):
        p.put(x - i, y + height - 6, z, "trunk", {"axis": "x"})
    for ox in range(-4, -1):
        for oz in range(-1, 2):
            leaf(p, x + ox, y + height - 5, z + oz)


def cherry(p, x, y, z, height=5, r=3):
    for dy in range(height):
        p.put(x, y + dy, z, "minecraft:cherry_log", {"axis": "y"})
    for ox in range(-r, r + 1):
        for oz in range(-r, r + 1):
            for dy in (height - 1, height, height + 1):
                if math.hypot(ox, oz) + (dy - height) * 0.9 <= r - 0.2:
                    leaf(p, x + ox, y + dy, z + oz, "minecraft:cherry_leaves")


def willow(p, x, y, z, height=6):
    """A weeping willow by water: oak trunk, a wide crown and hanging vines of leaves."""
    for dy in range(height):
        p.put(x, y + dy, z, "minecraft:oak_log", {"axis": "y"})
    for ox in range(-3, 4):
        for oz in range(-3, 4):
            d = math.hypot(ox, oz)
            if d <= 3.2:
                leaf(p, x + ox, y + height, z + oz, "minecraft:oak_leaves")
            if d <= 2.2:
                leaf(p, x + ox, y + height + 1, z + oz, "minecraft:oak_leaves")
            if 2.4 <= d <= 3.2 and (ox * 3 + oz) % 2 == 0:
                for dy in (1, 2):
                    leaf(p, x + ox, y + height - dy, z + oz, "minecraft:oak_leaves")


def tree(p, x, y, z, kind="pine", height=None):
    if kind == "cherry":
        cherry(p, x, y, z, height or 5)
    elif kind == "willow":
        willow(p, x, y, z, height or 6)
    else:
        pine(p, x, y, z, height or 9)


def potted_pine(p, x, y, z):
    """A pine in a stone planter (the imperial avenue)."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            p.put(x + dx, y, z + dz, "kerb" if dx or dz else "ground", {"snowy": "false"} if not (dx or dz) else None)
    p.put(x, y + 1, z, "trunk", {"axis": "y"})
    p.put(x, y + 2, z, "trunk", {"axis": "y"})
    for ox in range(-1, 2):
        for oz in range(-1, 2):
            leaf(p, x + ox, y + 3, z + oz)
    leaf(p, x, y + 4, z)


def awning(p, x0, x1, z0, z1, y, a="cloth", b="cloth_alt"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, y, z, a if (x + z) % 2 == 0 else b)


def plaque(p, x, y, z, along="x"):
    """A gold-rimmed name board over a door (red board, gold middle)."""
    if along == "x":
        p.put(x - 1, y, z, "wood")
        p.put(x, y, z, "gold")
        p.put(x + 1, y, z, "wood")
    else:
        p.put(x, y, z - 1, "wood")
        p.put(x, y, z, "gold")
        p.put(x, y, z + 1, "wood")


def door(p, x, y, z, facing, hinge="left", role="door"):
    """A door whose outside is `facing` (the door block faces into the room the player walks into)."""
    p.put(x, y, z, role, {"facing": facing, "half": "lower", "hinge": hinge, "open": "false", "powered": "false"})
    p.put(x, y + 1, z, role, {"facing": facing, "half": "upper", "hinge": hinge, "open": "false", "powered": "false"})


def chest(p, x, y, z, loot=None, facing="south"):
    nbt = {"id": "minecraft:chest"}
    if loot:
        nbt["LootTable"] = loot
    p.set(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"}, nbt)


def barrel(p, x, y, z, facing="up"):
    p.put(x, y, z, "minecraft:barrel", {"facing": facing, "open": "false"})


def crates(p, x0, z0, x1, z1, y, h=2):
    """A stack of crates and barrels (storeroom, dock, caravan field)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            hh = 1 + p.rng.randrange(h)
            for dy in range(hh):
                r = p.rng.random()
                if r < 0.4:
                    barrel(p, x, y + dy, z, p.rng.choice(("up", "north", "east")))
                elif r < 0.7:
                    p.put(x, y + dy, z, "minecraft:spruce_planks" if (x + z) % 2 else "minecraft:stripped_spruce_wood",
                          {"axis": "y"} if (x + z) % 2 == 0 else None)
                else:
                    p.put(x, y + dy, z, "minecraft:hay_block", {"axis": "y"})


def ladder(p, x, y0, y1, z, facing):
    for y in range(y0, y1 + 1):
        p.put(x, y, z, "minecraft:ladder", {"facing": facing, "waterlogged": "false"})


def paving(p, x0, z0, x1, z1, y=LY, role="paving", alt="paving_alt", alt_chance=0.12, kerb_edge=False):
    """Paving with a periodic scatter of the alternate block (period 11/13: looks irregular, compresses well)."""
    step = max(2, int(round(1.0 / max(alt_chance, 0.01))))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if kerb_edge and (x in (x0, x1) or z in (z0, z1)):
                p.put(x, y, z, "kerb")
            else:
                p.put(x, y, z, alt if alt_chance > 0 and (x * 5 + z * 3 + (x * z) % 7) % step == 0 else role)


def lattice(p, x, y, z, facing):
    p.put(x, y, z, "wood_trapdoor", {"facing": facing, "half": "bottom", "open": "true", "powered": "false",
                                     "waterlogged": "false"})


def couplets(p, x, y, z, facing, width=3):
    dx, dz = (1, 0) if facing in ("north", "south") else (0, 1)
    half = width // 2 + 1
    for s in (-1, 1):
        bx, bz = x + dx * half * s, z + dz * half * s
        if p.inside(bx, y, bz):
            wall_banner(p, bx, y, bz, facing)


# ===================================================================================================== big parts
def podium(p, x0, z0, x1, z1, y, h, steps=(), step_w=5, role="kerb", top="paving", edge_stairs=True):
    """A stone platform from y+1 to y+h (top surface y+h), stepped on the named sides ("south", ...); returns y+h."""
    for yy in range(y + 1, y + h + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if yy == y + h:
                    p.put(x, yy, z, "kerb" if edge else top)
                else:
                    p.put(x, yy, z, role)
    for side in steps:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        half = step_w // 2
        for i in range(h):
            for w in range(-half, step_w - half):
                if side == "south":
                    px, pz = cx + w, z1 + 1 + i
                elif side == "north":
                    px, pz = cx + w, z0 - 1 - i
                elif side == "east":
                    px, pz = x1 + 1 + i, cz + w
                else:
                    px, pz = x0 - 1 - i, cz + w
                p.stairs(px, y + h - i, pz, "kerb_stairs", OPP[side])
                for yy in range(y + 1, y + h - i):
                    p.put(px, yy, pz, role)
        # balustrade posts beside the steps
        if h >= 2:
            for w in (-half - 1, step_w - half):
                if side in ("south", "north"):
                    zz = z1 if side == "south" else z0
                    p.put(cx + w, y + h + 1, zz, "kerb_wall")
                else:
                    xx = x1 if side == "east" else x0
                    p.put(xx, y + h + 1, cz + w, "kerb_wall")
    return y + h


def hall(p, x0, z0, x1, z1, f, h, door_sides=("south",), door_w=3, windows=4, wall_role="plaster", pillar_role="red",
         ceiling=True, interior_air=True, lamps=True, doors=True, plaque_top=True):
    """Walls of a hall on floor f (standing level f+1 .. f+h): plaster between red pillars every 4, lattice windows,
    a beam frieze at f+h, doors. Returns (ix0, iz0, ix1, iz1) interior."""
    # explicit air only for the walking volume (5 blocks): the rest of a tall room is carved by beard_box, and the
    # explicit air of whole halls would cost the capital's gzip budget (38.C.10)
    p.walls(x0, f + 1, z0, x1, f + h, z1, role=wall_role, pillar=pillar_role, windows=0, interior_air=False)
    if interior_air:
        p.air(x0 + 1, f + 1, z0 + 1, x1 - 1, f + min(h - 1, 5), z1 - 1)
    # windows: lattice trapdoors in pairs between pillars
    if windows:
        for x in range(x0 + 2, x1 - 1):
            if (x - x0) % 4 in (1, 3) or (x - x0) % 4 == 2:
                if (x - x0) % 4 != 0:
                    for z, face in ((z0, "north"), (z1, "south")):
                        if face in door_sides and abs(x - (x0 + x1) // 2) <= door_w // 2 + 1:
                            continue
                        if (x - x0) % windows in (1, 2, 3) and h >= 4:
                            lattice(p, x, f + 2, z, face)
                            lattice(p, x, f + 3, z, face)
        for z in range(z0 + 2, z1 - 1):
            if (z - z0) % 4 != 0:
                for x, face in ((x0, "west"), (x1, "east")):
                    if face in door_sides and abs(z - (z0 + z1) // 2) <= door_w // 2 + 1:
                        continue
                    if (z - z0) % windows in (1, 2, 3) and h >= 4:
                        lattice(p, x, f + 2, z, face)
                        lattice(p, x, f + 3, z, face)
    beam_band(p, x0, z0, x1, z1, f + h)
    for side in door_sides:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        half = door_w // 2
        for dy in range(1, min(h, 4)):
            for w in range(-half, door_w - half):
                if side in ("north", "south"):
                    p.put(cx + w, f + dy, z0 if side == "north" else z1, "minecraft:air")
                else:
                    p.put(x0 if side == "west" else x1, f + dy, cz + w, "minecraft:air")
        if doors and door_w <= 2:
            for w in range(-half, door_w - half):
                hinge = "left" if w == -half else "right"
                if side in ("north", "south"):
                    door(p, cx + w, f + 1, z0 if side == "north" else z1, side, hinge)
                else:
                    door(p, x0 if side == "west" else x1, f + 1, cz + w, side, hinge)
        if plaque_top and h >= 5:
            if side in ("north", "south"):
                plaque(p, cx, f + 4 if h > 5 else f + h - 1, z0 if side == "north" else z1, "x")
            else:
                plaque(p, x0 if side == "west" else x1, f + 4 if h > 5 else f + h - 1, cz, "z")
    if ceiling:
        p.fill(x0 + 1, f + h, z0 + 1, x1 - 1, f + h, z1 - 1, "wood")
        # exposed cross beams under the ceiling
        for x in range(x0 + 2, x1 - 1, 4):
            for z in range(z0 + 1, z1):
                if h >= 6:
                    p.put(x, f + h - 1, z, "beam", {"axis": "z"})
    if lamps:
        for x in range(x0 + 3, x1 - 2, 6):
            for z in range(z0 + 3, z1 - 2, 6):
                lit(p, x, f + h - (1 if h >= 6 else 0), z, chain=1, role="glow" if (x + z) % 12 else "lamp")
    return x0 + 1, z0 + 1, x1 - 1, z1 - 1


def colonnade(p, x0, z0, x1, z1, f, h, every=3, rail=True, skip_sides=()):
    """Red columns around a veranda rectangle (on the podium edge) with a lintel; open between columns."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            along = (x - x0) if z in (z0, z1) else (z - z0)
            corner = x in (x0, x1) and z in (z0, z1)
            if corner or along % every == 0:
                for yy in range(f + 1, f + h + 1):
                    p.put(x, yy, z, "red")
                p.put(x, f + 1, z, "kerb_chisel")
    beam_band(p, x0, z0, x1, z1, f + h + 1)


def roof_over(p, x0, z0, x1, z1, y, kind="hip", tiers=1, overhang=2, along="x", top="pearl", tile=False):
    """The roof of a hall: brackets under the eaves, then a hip or gable roof. tile=True uses deepslate tiles."""
    brackets(p, x0, z0, x1, z1, y - 1)
    kw = {"role": "tile_stairs", "slab_role": "tile_slab", "full_role": "tile"} if tile else {}
    if kind == "hip":
        return hip(p, x0, y, z0, x1, z1, overhang=overhang, tiers=tiers, top=top, **kw)
    return gable(p, x0, y, z0, x1, z1, along=along, overhang=overhang, pearl=bool(top), **kw)


def aula(p, x0, z0, x1, z1, h=6, podium_h=2, teras=2, roof="hip", tiers=1, door_sides=("south",), door_w=3,
         along="x", tile=False, top="pearl", steps=None, interior_air=True, y=LY):
    """A complete Chinese hall: podium, veranda colonnade, walls, frieze, brackets, roof. Returns a dict with the
    interior box, the floor y (standing level f+1) and the roof top."""
    f = podium(p, x0, z0, x1, z1, y, podium_h, steps=steps if steps is not None else door_sides, step_w=door_w + 2)
    wx0, wz0, wx1, wz1 = x0 + teras, z0 + teras, x1 - teras, z1 - teras
    if teras > 0:
        colonnade(p, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f, h - 1)
    inner = hall(p, wx0, wz0, wx1, wz1, f, h, door_sides=door_sides, door_w=door_w, interior_air=interior_air)
    if teras > 0:
        # veranda ceiling between the colonnade and the wall
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if not (wx0 <= x <= wx1 and wz0 <= z <= wz1):
                    p.put(x, f + h, z, "wood_slab", {"type": "top", "waterlogged": "false"})
    rx0, rz0, rx1, rz1 = (x0 + 1, z0 + 1, x1 - 1, z1 - 1) if teras > 0 else (wx0, wz0, wx1, wz1)
    top_y = roof_over(p, rx0, rz0, rx1, rz1, f + h + 1, kind=roof, tiers=tiers, overhang=1, along=along, top=top, tile=tile)
    return {"inner": inner, "f": f, "top": top_y, "walls": (wx0, wz0, wx1, wz1)}


def upper_floor(p, ix0, iz0, ix1, iz1, y, hole=None):
    """A plank floor at y over the interior with a stair hole (x0, z0, x1, z1)."""
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            if hole and hole[0] <= x <= hole[2] and hole[1] <= z <= hole[3]:
                p.put(x, y, z, "minecraft:air")
            else:
                p.put(x, y, z, "wood_alt")


def stair_flight(p, x, y, z, direction, steps, width=2, role="wood_stairs"):
    p.staircase(x, y, z, direction, steps, width=width, role=role, support=None)


def pagoda(p, x0, z0, size, storeys, sh=5, y=LY, base_h=2, top="spire", balcony=True, door="south", titik_top=None,
           isi=None):
    """A square pagoda: stone base, `storeys` floors each one block smaller, balconies with railings, a ring of eaves at
    every storey, a ladder up the north-west corner, a hip roof with a spire. isi = list of dandan recipes, one per
    storey (cycled), furnishes every storey around the ladder. Returns the top y."""
    f = podium(p, x0 - 1, z0 - 1, x0 + size, z0 + size, y, base_h, steps=(door,), step_w=3)
    yy = f
    s = 0
    off = 0
    for s in range(storeys):
        a0, b0 = x0 + off, z0 + off
        a1, b1 = x0 + size - 1 - off, z0 + size - 1 - off
        if a1 - a0 < 4:
            break
        role = "kerb" if s == 0 else "plaster"
        p.walls(a0, yy + 1, b0, a1, yy + sh, b1, role=role, pillar="red", windows=0, interior_air=(s == 0))
        for x in range(a0 + 1, a1):
            for z, face in ((b0, "north"), (b1, "south")):
                if (x - a0) % 3 == 1 and s > 0:
                    lattice(p, x, yy + 2, z, face)
                    lattice(p, x, yy + 3, z, face)
        for z in range(b0 + 1, b1):
            for x, face in ((a0, "west"), (a1, "east")):
                if (z - b0) % 3 == 1 and s > 0:
                    lattice(p, x, yy + 2, z, face)
                    lattice(p, x, yy + 3, z, face)
        beam_band(p, a0, b0, a1, b1, yy + sh)
        if s == 0:
            cx = (a0 + a1) // 2
            for dy in range(1, 4):
                for w in (-1, 0, 1):
                    if door == "south":
                        p.put(cx + w, yy + dy, b1, "minecraft:air")
                    else:
                        p.put(cx + w, yy + dy, b0, "minecraft:air")
        # floor of the next storey + eave ring
        p.fill(a0 + 1, yy + sh, b0 + 1, a1 - 1, yy + sh, b1 - 1, "wood")
        ladder(p, a0 + 1, yy + 1, yy + sh, b0 + 1, "south")
        p.put(a0 + 1, yy + sh, b0 + 1, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
        e0, f0, e1, f1 = a0 - 1, b0 - 1, a1 + 1, b1 + 1
        for x in range(e0 + 1, e1):
            p.stairs(x, yy + sh + 1, f0, "roof", "south")
            p.stairs(x, yy + sh + 1, f1, "roof", "north")
        for z in range(f0 + 1, f1):
            p.stairs(e0, yy + sh + 1, z, "roof", "east")
            p.stairs(e1, yy + sh + 1, z, "roof", "west")
        for cx, cz, fc, sh_ in ((e0, f0, "south", "outer_left"), (e1, f0, "south", "outer_right"),
                                (e0, f1, "north", "outer_right"), (e1, f1, "north", "outer_left")):
            p.stairs(cx, yy + sh + 1, cz, "roof", fc, shape=sh_)
            p.stairs(cx, yy + sh + 2, cz, "roof", fc, half="top")
            lit(p, cx, yy + sh + 1, cz, chain=1, role="lamp")
        brackets(p, a0, b0, a1, b1, yy + sh)
        if isi:
            # storey 0 stands on the podium (f); upper storeys stand on the previous ceiling (yy - 1)
            dandan(p, (a0 + 1, b0 + 1, a1 - 1, b1 - 1), yy if s == 0 else yy - 1, isi[s % len(isi)], titik_maks=1,
                   buka_jarak=2 if s == 0 else -1,
                   simpan=[(a0 + 1, b0 + 1, a0 + 2, b0 + 3), (a0 + 1, b0 + 1, a0 + 3, b0 + 2)], tepi=0)
        yy += sh + 1
        off += 1
    a0, b0 = x0 + off, z0 + off
    a1, b1 = x0 + size - 1 - off, z0 + size - 1 - off
    # open top floor with railing (the viewing deck)
    for x in range(a0 - 1, a1 + 2):
        for z in range(b0 - 1, b1 + 2):
            if x in (a0 - 1, a1 + 1) or z in (b0 - 1, b1 + 1):
                p.put(x, yy + 1, z, "rail")
    for cx, cz in ((a0, b0), (a1, b0), (a0, b1), (a1, b1)):
        for dy in range(1, 4):
            p.put(cx, yy + dy, cz, "red")
    if titik_top:
        for (tx, tz) in ((a0 + 1, b0 + 1), (a1 - 1, b0 + 1), (a0 + 1, b1 - 1), (a1 - 1, b1 - 1)):
            p.titik(titik_top, tx, yy + 1, tz, "south")
    top_y = hip(p, a0, yy + 4, b0, a1, b1, overhang=1, top=top)
    lit(p, (a0 + a1) // 2, yy + 4, (b0 + b1) // 2, chain=1)
    return top_y


def stall(p, x, z, facing, y=LY, w=3, cloth=("cloth", "cloth_alt"), goods=None):
    """A market stall: counter (barrels / trapdoor top), four posts, striped awning; returns the seller spot."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(w):
        cx, cz = x + rx * i, z + rz * i
        p.put(cx, y + 1, cz, "minecraft:barrel", {"facing": "up", "open": "false"}) if i % 2 == 0 else \
            p.put(cx, y + 1, cz, "wood_trapdoor", {"facing": facing, "half": "top", "open": "false", "powered": "false",
                                                  "waterlogged": "false"})
        if goods:
            p.put(cx, y + 2, cz, goods[i % len(goods)])
    for i in (0, w - 1):
        for back in (0, 2):
            px, pz = x + rx * i - dx * back, z + rz * i - dz * back
            for dy in range(1, 4):
                if back == 0 and dy == 1:
                    continue
                p.put(px, y + dy, pz, "wood_fence")
    for i in range(-1, w + 1):
        for back in range(-1, 3):
            px, pz = x + rx * i - dx * back, z + rz * i - dz * back
            p.put(px, y + 4, pz, cloth[0] if (i + back) % 2 == 0 else cloth[1])
    return x + rx * (w // 2) - dx * 1, z + rz * (w // 2) - dz * 1


def bench(p, x, z, facing, y=LY, n=3):
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(n):
        p.stairs(x + rx * i, y + 1, z + rz * i, "wood_stairs", OPP[facing])


def well(p, cx, cz, y=LY):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 and dz == 0:
                p.put(cx, y, cz, "minecraft:water", {"level": "0"})
                p.put(cx, y - 1, cz, "minecraft:water", {"level": "0"})
            else:
                p.put(cx + dx, y + 1, cz + dz, "kerb_wall" if dx and dz else "kerb")
    for dx, dz in ((-1, -1), (1, 1)):
        for dy in (2, 3):
            p.put(cx + dx, y + dy, cz + dz, "wood_fence")
    for dx in (-1, 0, 1):
        p.put(cx + dx, y + 4, cz + dx, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    p.put(cx, y + 3, cz, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})


def notice_board(p, x, z, facing, y=LY, w=5):
    """A notice board: two posts, a dark frame and paper (birch trapdoors) - the live text is a client billboard."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    for i in range(w):
        px, pz = x + rx * i, z + rz * i
        for dy in (1, 2, 3, 4):
            if i in (0, w - 1):
                p.put(px, y + dy, pz, "log", {"axis": "y"})
            elif dy in (2, 3):
                p.put(px, y + dy, pz, "minecraft:birch_trapdoor" if (i + dy) % 2 else "minecraft:white_wool")
            elif dy == 4:
                p.put(px, y + dy, pz, "wood")
        p.stairs(px, y + 5, pz, "roof", OPP[facing])


def street_grid(k, nx=3, nz=3, skip=(), lamps=True, y=LY, imperial_cols=()):
    """Floor of a whole district canvas: 1 foundation layer + paving; the 3-block border of every lot is street with a
    gutter line; skip = {(i, j, side)} lot borders that are merged (no street, a building crosses there)."""
    sx, _, sz = k.size
    paving(k, 0, 0, sx - 1, sz - 1, y, role="floor", alt="floor_alt", alt_chance=0.10)
    for i in range(nx):
        for j in range(nz):
            x0, z0 = i * tl.LOT, j * tl.LOT
            for side in "nesw":
                if (i, j, side) in skip:
                    continue
                if side == "n":
                    box = (x0, z0, x0 + 47, z0 + 2)
                elif side == "s":
                    box = (x0, z0 + 45, x0 + 47, z0 + 47)
                elif side == "w":
                    box = (x0, z0, x0 + 2, z0 + 47)
                else:
                    box = (x0 + 45, z0, x0 + 47, z0 + 47)
                paving(k, box[0], box[1], box[2], box[3], y, role="path", alt="path_alt", alt_chance=0.22)
    # gutters on the inner street edge, lamps at street crossings
    if lamps:
        for i in range(nx + 1):
            for j in range(nz + 1):
                cx, cz = i * tl.LOT, j * tl.LOT
                for (px, pz) in ((cx - 3, cz - 3), (cx + 2, cz + 2)):
                    if 0 <= px < sx and 0 <= pz < sz and k.get(px, y + 1, pz) is None:
                        post(k, px, y + 1, pz, height=3, post_role="wood_fence")


def imperial_avenue(k, x0, x1, z0, z1, y=LY, lanterns=8, pines=16):
    """Jalan Kekaisaran (38.B.2.5): pale jade paving 16 wide (smooth quartz with polished andesite bands and a carved
    centre line), stone lanterns every 8 blocks on both kerbs, pines in planters."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1)
            centre = abs(x - (x0 + x1) / 2.0) < 1
            band = (z - z0) % 8 == 0
            if edge:
                k.put(x, y, z, "kerb")
            elif centre:
                k.put(x, y, z, "kerb_chisel" if (z % 4 == 0) else "imperial")
            elif band:
                k.put(x, y, z, "paving")
            else:
                k.put(x, y, z, "imperial")
    for z in range(z0 + 3, z1 - 1, lanterns):
        stone_lantern(k, x0 + 1, y + 1, z)
        stone_lantern(k, x1 - 1, y + 1, z)
    for z in range(z0 + 7, z1 - 3, pines):
        potted_pine(k, x0 - 2, y, z)
        potted_pine(k, x1 + 2, y, z)


def furnish_bedroom(p, x0, z0, x1, z1, f, beds=1, facing="north", titik=True):
    """Beds along the back wall, a chest, a wardrobe (barrel), a lantern, a rug; kasur points."""
    out = []
    bx = x0 + 1
    for i in range(beds):
        x = x0 + 1 + i * 2
        if x > x1 - 1:
            break
        bed(p, x, f + 1, z0 + 1 if facing == "north" else z1 - 1, facing)
        if titik:
            p.titik("kasur", x, f + 1, z0 + 1 if facing == "north" else z1 - 1, OPP[facing])
        out.append(x)
    if x1 - x0 >= 3:
        chest(p, x1, f + 1, z0 if facing == "north" else z1, None, "south" if facing == "north" else "north")
        barrel(p, x1, f + 1, (z0 + z1) // 2, "west")
        if titik:
            p.titik("peti_milik", x1 - 1, f + 1, z0 if facing == "north" else z1, "east")
    if x1 - x0 >= 4 and z1 - z0 >= 4:
        rug(p, x0 + 1, (z0 + z1) // 2, x1 - 1, z1 - 1 if facing == "north" else (z0 + z1) // 2 + 1, f + 1)
    _ = bx
    return out


def furnish_dining(p, cx, cz, f, n=4, titik=True):
    """A round dining table (fence + plate) with chairs around; meja_makan + kursi points."""
    table(p, cx, f + 1, cz, "minecraft:dark_oak_pressure_plate")
    if titik:
        p.titik("meja_makan", cx, f + 1, cz, "south")
    for d, (dx, dz) in list(FACE.items())[:n]:
        chair(p, cx + dx, f + 1, cz + dz, OPP[d])
        if titik:
            p.titik("kursi", cx + dx, f + 1, cz + dz, OPP[d])


def furnish_kitchen(p, x0, z0, x1, f, wall_z, face, lit_fire=False):
    """A kitchen wall: smoker, furnace, cauldron, barrels, a hanging pot rack (chains + lanterns)."""
    items = ["minecraft:smoker", "minecraft:furnace", "minecraft:cauldron", "minecraft:barrel", "minecraft:crafting_table"]
    for i, x in enumerate(range(x0, x1 + 1)):
        it = items[i % len(items)]
        if it in ("minecraft:smoker", "minecraft:furnace"):
            p.put(x, f + 1, wall_z, it, {"facing": face, "lit": "false"})
        elif it == "minecraft:barrel":
            barrel(p, x, f + 1, wall_z, face)
        else:
            p.put(x, f + 1, wall_z, it)
        p.put(x, f + 3, wall_z, "minecraft:dark_oak_trapdoor", {"facing": face, "half": "top", "open": "false",
                                                               "powered": "false", "waterlogged": "false"})
    if lit_fire:
        p.put(x0, f + 1, wall_z + (1 if face == "south" else -1), "minecraft:campfire",
              {"facing": face, "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    p.titik("kerja:kuali", (x0 + x1) // 2, f + 1, wall_z + (1 if face == "south" else -1),
            "north" if face == "south" else "south")


def counter(p, x0, x1, z, f, face, top="wood_trapdoor"):
    """A shop counter along x at z (barrels with trapdoor tops), the seller stands behind (face = toward customers)."""
    for x in range(x0, x1 + 1):
        if (x - x0) % 3 == 1:
            p.put(x, f + 1, z, "minecraft:barrel", {"facing": face, "open": "false"})
        else:
            p.put(x, f + 1, z, "wood_trapdoor", {"facing": face, "half": "top", "open": "false", "powered": "false",
                                                 "waterlogged": "false"})


def counter_z(p, x, z0, z1, f, face):
    for z in range(z0, z1 + 1):
        if (z - z0) % 3 == 1:
            p.put(x, f + 1, z, "minecraft:barrel", {"facing": face, "open": "false"})
        else:
            p.put(x, f + 1, z, "wood_trapdoor", {"facing": face, "half": "top", "open": "false", "powered": "false",
                                                 "waterlogged": "false"})


def garden(p, x0, z0, x1, z1, y=LY, trees=("pine",), pond_too=True, seed_paths=True):
    """A walled courtyard garden: grass and gravel, a winding path, a pond with lilies, rocks, a tree or two,
    flowers, stone lanterns."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, y, z, "ground" if p.rng.random() < 0.85 else "ground_alt", {"snowy": "false"})
    if seed_paths:
        p.path([(x0 + 1, (z0 + z1) // 2), ((x0 + x1) // 2, z0 + 2), (x1 - 1, (z0 + z1) // 2)], y, width=1,
               role="minecraft:gravel", alt="paving", alt_chance=0.3)
    if pond_too and x1 - x0 >= 9 and z1 - z0 >= 9:
        pond(p, (x0 + x1) // 2 + 1, (z0 + z1) // 2 + 2, max(2, (x1 - x0) // 5), max(2, (z1 - z0) // 5), y)
    for i, kind in enumerate(trees):
        tx = x0 + 2 + i * max(3, (x1 - x0 - 4) // max(1, len(trees)))
        tree(p, tx, y + 1, z0 + 2, kind)
    for _ in range(max(2, (x1 - x0) * (z1 - z0) // 30)):
        x, z = p.rng.randint(x0 + 1, x1 - 1), p.rng.randint(z0 + 1, z1 - 1)
        if p.get(x, y + 1, z) is None and p.get(x, y, z) in (p.b("ground"), p.b("ground_alt")):
            r = p.rng.random()
            if r < 0.3:
                tall_flower(p, x, y + 1, z, "flower_tall" if r < 0.15 else "flower_tall_alt")
            elif r < 0.6:
                p.put(x, y + 1, z, "flower" if r < 0.45 else "flower_alt")
            elif r < 0.8:
                p.put(x, y + 1, z, "minecraft:mossy_cobblestone")
            else:
                p.put(x, y + 1, z, "minecraft:short_grass")
    stone_lantern(p, x0 + 1, y + 1, z1 - 1)
    stone_lantern(p, x1 - 1, y + 1, z1 - 1)


def yard_wall(p, x0, z0, x1, z1, y=LY, h=3, gate=None, gate_w=3):
    """A plastered courtyard wall with a tile cap; gate = side name."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            for dy in range(1, h + 1):
                p.put(x, y + dy, z, "plaster" if dy > 1 else "kerb")
            p.slab(x, y + h + 1, z, "tile_slab")
    if gate:
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        half = gate_w // 2
        for w in range(-half, gate_w - half):
            for dy in range(1, h + 2):
                if gate in ("north", "south"):
                    p.put(cx + w, y + dy, z0 if gate == "north" else z1, "minecraft:air")
                else:
                    p.put(x0 if gate == "west" else x1, y + dy, cz + w, "minecraft:air")
        # a small roofed gate (red posts, tile roof)
        for w in (-half - 1, gate_w - half):
            for dy in range(1, h + 2):
                if gate in ("north", "south"):
                    p.put(cx + w, y + dy, z0 if gate == "north" else z1, "red")
                else:
                    p.put(x0 if gate == "west" else x1, y + dy, cz + w, "red")
        if gate in ("north", "south"):
            zz = z0 if gate == "north" else z1
            gable(p, cx - half - 1, y + h + 2, zz, cx + gate_w - half, zz, along="x", overhang=1, pearl=False,
                  role="tile_stairs", slab_role="tile_slab", full_role="tile")
        else:
            xx = x0 if gate == "west" else x1
            gable(p, xx, y + h + 2, cz - half - 1, xx, cz + gate_w - half, along="z", overhang=1, pearl=False,
                  role="tile_stairs", slab_role="tile_slab", full_role="tile")


def paifang(p, cx, z, y=LY, width=13, height=9, facing="south", gold=True):
    """A three-bay ceremonial archway (paifang) across x, centred on cx, at z."""
    half = width // 2
    for px in (cx - half, cx - half // 3 - 1, cx + half // 3 + 1, cx + half):
        p.put(px, y + 1, z, "kerb_chisel")
        for dy in range(2, height):
            p.put(px, y + dy, z, "red")
        for dz in (-1, 1):
            p.stairs(px, y + 1, z + dz, "kerb_stairs", OPP["south" if dz > 0 else "north"])
    for x in range(cx - half, cx + half + 1):
        p.put(x, y + height - 2, z, "beam", {"axis": "x"})
        p.put(x, y + height, z, "beam", {"axis": "x"})
        if abs(x - cx) <= 1:
            p.put(x, y + height - 1, z, "gold" if gold else "wood")
        else:
            p.put(x, y + height - 1, z, "plaster" if (x % 2) else "minecraft:blue_terracotta")
    gable(p, cx - half, y + height + 1, z, cx + half, z, along="x", overhang=1, pearl=True,
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for px in (cx - half - 1, cx + half + 1):
        lit(p, px, y + height, z, chain=1, role="lamp")


def drum(p, x, y, z, facing):
    """A big drum on a red stand (note block body, red stand, a hide face = white wool)."""
    p.put(x, y, z, "red")
    p.put(x, y + 1, z, "minecraft:note_block", {"instrument": "basedrum", "note": "0", "powered": "false"})
    p.stairs(x, y + 2, z, "wood_stairs", facing, half="top")


# ===================================================================================================== paste with rotation
_FACINGS = ("north", "east", "south", "west")


def _rot_face(f, r):
    if f in _FACINGS:
        return _FACINGS[(_FACINGS.index(f) + r) % 4]
    return f


def _rot_props(props, r):
    if r == 0 or not props:
        return props
    out = {}
    for k, v in props:
        if k == "facing":
            v = _rot_face(v, r)
        elif k == "axis" and r % 2 == 1 and v in ("x", "z"):
            v = "z" if v == "x" else "x"
        elif k == "rotation":
            try:
                v = str((int(v) + 4 * r) % 16)
            except ValueError:
                pass
        out_key = k
        if k in _FACINGS:
            out_key = _rot_face(k, r)
        out[out_key] = v
    return out


def _rot_xz(x, z, w, d, r):
    """Block (x, z) of a w x d footprint after r clockwise quarter turns (kept in the rotated footprint)."""
    if r == 0:
        return x, z
    if r == 1:
        return d - 1 - z, x
    if r == 2:
        return w - 1 - x, d - 1 - z
    return z, w - 1 - x


def _rot_xz_f(x, z, w, d, r):
    """A continuous point (entities) after r clockwise quarter turns."""
    if r == 0:
        return x, z
    if r == 1:
        return d - z, x
    if r == 2:
        return w - x, d - z
    return z, w - x


def tempel(dst, src, x0, z0, r=0, y0=0):
    """Pastes canvas/piece src into dst with its min corner at (x0, y0, z0) after r clockwise quarter turns (vanilla
    Rotation order: 1 = CLOCKWISE_90, north -> east). Block properties (facing, axis, rotation, side connections), life
    points (position, facing, box size) and anchors (position, yaw) turn with it."""
    w, _, d = src.size
    for (x, y, z), ((bid, props), nbt) in src.cells.items():
        rx, rz = _rot_xz(x, z, w, d, r)
        np = _rot_props(props, r)
        dst.put(x0 + rx, y0 + y, z0 + rz, bid, dict(np) if np else None, nbt) if dst.inside(x0 + rx, y0 + y, z0 + rz) \
            else None
    for (x, y, z, nbt) in src.entities:
        rx, rz = _rot_xz_f(x, z, w, d, r)
        e = dict(nbt)
        if "Rotation" in e:
            yaw = e["Rotation"][0].v if hasattr(e["Rotation"][0], "v") else float(e["Rotation"][0])
            e["Rotation"] = [structgen.nbt.Float((yaw + 90.0 * r) % 360.0), e["Rotation"][1]]
        dst.entity(x0 + rx, y0 + y, z0 + rz, e)
    for t in src.titik_list:
        px, py, pz = t["pos"]
        data = dict(t.get("data") or {})
        if t["jenis"] in tl.TITIK_KOTAK:
            ddx, ddz = int(data["dx"]), int(data["dz"])
            ax, az = _rot_xz(px, pz, w, d, r)
            bx, bz = _rot_xz(px + ddx, pz + ddz, w, d, r)
            px2, pz2 = min(ax, bx), min(az, bz)
            data["dx"], data["dz"] = str(abs(bx - ax)), str(abs(bz - az))
            dst.titik(t["jenis"], x0 + px2, y0 + py, z0 + pz2, _rot_face(t["hadap"], r), data)
        else:
            rx, rz = _rot_xz(px, pz, w, d, r)
            dst.titik(t["jenis"], x0 + rx, y0 + py, z0 + rz, _rot_face(t["hadap"], r), data or None)
    for (lx, ly, lz) in getattr(src, "lights", []):
        rx, rz = _rot_xz(lx, lz, w, d, r)
        if hasattr(dst, "lights"):
            dst.lights.append((x0 + rx, y0 + ly, z0 + rz))


def sub(w, h, d, seed):
    """A scratch canvas for one building (built facing south, pasted with tempel at any rotation)."""
    k = Kanvas(w, h, d, seed)
    return k



# ===================================================================================================== interior dressing
# D-KS detail pass: halls that were drawn with only their service desk and anchors get real rooms. dandan() furnishes an
# interior box by recipe (gaya) without ever touching a life point, an anchor, a stage box, a doorway or the walking
# aisle; it only writes into cells that are still air (or unset) at standing height over a solid floor.
_KOSONG = (None, "minecraft:air", "minecraft:cave_air")
_BUNGA_POT = ("minecraft:potted_bamboo", "minecraft:potted_azalea_bush", "minecraft:potted_fern",
              "minecraft:potted_flowering_azalea_bush", "minecraft:potted_red_tulip", "minecraft:potted_white_tulip",
              "minecraft:potted_lily_of_the_valley", "minecraft:potted_spruce_sapling")


def _kosong(p, x, y, z):
    return p.inside(x, y, z) and p.get(x, y, z) in _KOSONG


def cadangan(p, f):
    """Cells (x, z) near floor f that furniture must leave free: life points and anchors (+1 around), stage boxes."""
    res = set()
    for e in getattr(p, "titik_list", []):
        x, y, z = e["pos"]
        d = e.get("data") or {}
        if e["jenis"] in tl.TITIK_KOTAK:
            if e["jenis"] == "bangunan":
                continue
            dx, dy, dz = int(d.get("dx", 0)), int(d.get("dy", 0)), int(d.get("dz", 0))
            if y > f + 3 or y + dy < f:
                continue
            for xx in range(x - 1, x + dx + 2):
                for zz in range(z - 1, z + dz + 2):
                    res.add((xx, zz))
            continue
        if abs(y - (f + 1)) > 2:
            continue
        for xx in (x - 1, x, x + 1):
            for zz in (z - 1, z, z + 1):
                res.add((xx, zz))
    for (ex, ey, ez, _d) in getattr(p, "entities", []):
        if abs(int(ey // 1) - (f + 1)) > 2:
            continue
        bx, bz = int(ex // 1), int(ez // 1)
        for xx in (bx - 1, bx, bx + 1):
            for zz in (bz - 1, bz, bz + 1):
                res.add((xx, zz))
    return res


def _bukaan(p, box, f, jarak=2):
    """Cells inside box within `jarak` of an opening (air or door in the wall ring at f+1)."""
    ix0, iz0, ix1, iz1 = box
    out = set()
    ring = [(x, iz0 - 1) for x in range(ix0, ix1 + 1)] + [(x, iz1 + 1) for x in range(ix0, ix1 + 1)] + \
           [(ix0 - 1, z) for z in range(iz0, iz1 + 1)] + [(ix1 + 1, z) for z in range(iz0, iz1 + 1)]
    for (x, z) in ring:
        b = p.get(x, f + 1, z) if p.inside(x, f + 1, z) else "x"
        if b in _KOSONG or (b and b.endswith("_door")):
            for xx in range(x - jarak, x + jarak + 1):
                for zz in range(z - jarak, z + jarak + 1):
                    out.add((xx, zz))
    return out


class Ruang:
    """One interior being furnished: tracks which floor cells are still free."""

    def __init__(self, p, box, f, lorong=None, lorong_w=3, simpan=(), titik_maks=6, buka_jarak=2):
        self.p, self.box, self.f = p, box, f
        ix0, iz0, ix1, iz1 = box
        self.blok = cadangan(p, f) | _bukaan(p, box, f, buka_jarak)
        self.simpan = set()
        for (x0, z0, x1, z1) in simpan:
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.simpan.add((x, z))
        self.blok |= self.simpan
        if lorong:
            cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
            h = lorong_w // 2
            for x in range(ix0, ix1 + 1):
                for z in range(iz0, iz1 + 1):
                    if (lorong == "z" and abs(x - cx) <= h) or (lorong == "x" and abs(z - cz) <= h):
                        self.blok.add((x, z))
        self.lorong = lorong
        self.titik_sisa = titik_maks
        self.n = 0

    def bebas(self, x, z, tinggi=2):
        ix0, iz0, ix1, iz1 = self.box
        if not (ix0 <= x <= ix1 and iz0 <= z <= iz1) or (x, z) in self.blok:
            return False
        p, f = self.p, self.f
        if not p.inside(x, f, z) or not solid(p.get(x, f, z)):
            return False
        return all(_kosong(p, x, f + dy, z) for dy in range(1, tinggi + 1))

    def semua_bebas(self, cells, tinggi=2):
        return all(self.bebas(x, z, tinggi) for (x, z) in cells)

    def pakai(self, cells, pinggir=0):
        for (x, z) in cells:
            for dx in range(-pinggir, pinggir + 1):
                for dz in range(-pinggir, pinggir + 1):
                    self.blok.add((x + dx, z + dz))
        self.n += 1

    def titik(self, jenis, x, z, hadap, data=None):
        if self.titik_sisa > 0:
            self.titik_sisa -= 1
            self.p.titik(jenis, x, self.f + 1, z, hadap, data)

    def plafon(self, x, z, maks=14):
        """Height of the first block above the floor at (x, z) (the ceiling), or None."""
        for dy in range(2, maks):
            if not _kosong(self.p, x, self.f + dy, z):
                return self.f + dy
        return None


# ------------------------------------------------------------------------------------------- clusters (anchor top-left)
def _papan(p, x, y, z):
    p.put(x, y, z, "wood_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                     "waterlogged": "false"})


def k_meja_kerja(r, x, z, hadap="south"):
    """A clerk's desk (a barrel drawer and a trapdoor board), an ink candle, a book stand or a pot, and a chair; the
    clerk sits facing `hadap` (south: the desk is on the north row, the chair on the south row)."""
    cells = [(x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)]
    if not r.semua_bebas(cells):
        return False
    p, f = r.p, r.f
    if hadap in ("south", "north"):
        dz = 0 if hadap == "south" else 1
        cz = z + 1 - dz
        p.put(x, f + 1, z + dz, "minecraft:barrel", {"facing": "up", "open": "false"})
        _papan(p, x + 1, f + 1, z + dz)
        candle(p, x, f + 2, z + dz, 1, "white")
        if (x + z) % 3 == 0:
            p.put(x + 1, f + 2, z + dz, "minecraft:lectern", {"facing": OPP[hadap], "has_book": "false", "powered": "false"})
        else:
            p.put(x + 1, f + 2, z + dz, "minecraft:flower_pot")
        chair(p, x, f + 1, cz, OPP[hadap])
        r.titik("meja_tulis", x, cz, OPP[hadap])
    else:
        dx = 0 if hadap == "east" else 1
        cx = x + 1 - dx
        p.put(x + dx, f + 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        _papan(p, x + dx, f + 1, z + 1)
        candle(p, x + dx, f + 2, z, 1, "white")
        chair(p, cx, f + 1, z, OPP[hadap])
        r.titik("meja_tulis", cx, z, OPP[hadap])
    r.pakai(cells, 1)
    return True


def k_teh(r, x, z):
    """A tea corner: a low table with a candle in the middle of four cushions, a pot of bamboo at one corner."""
    cells = [(x + i, z + j) for i in range(3) for j in range(3)]
    if not r.semua_bebas(cells):
        return False
    p, f = r.p, r.f
    low_table(p, x + 1, f + 1, z + 1)
    candle(p, x + 1, f + 2, z + 1, 1 + (x + z) % 3, ("white", "yellow", "red")[(x * 3 + z) % 3])
    col = ("red", "white", "brown", "green")[(x + 2 * z) % 4]
    for (cx, cz) in ((x + 1, z), (x + 1, z + 2), (x, z + 1), (x + 2, z + 1)):
        p.put(cx, f + 1, cz, "minecraft:%s_carpet" % col)
    pot(p, x, f + 1, z, _BUNGA_POT[(x + z) % len(_BUNGA_POT)])
    r.titik("kursi_lantai", x + 1, z, "south")
    r.titik("obrol", x + 1, z + 2, "north")
    r.pakai(cells, 1)
    return True


def k_meja_makan(r, x, z):
    """A dining table for four (fence + plate) with chairs and a tea jar (decorated pot) beside it."""
    cells = [(x + i, z + j) for i in range(3) for j in range(3)]
    if not r.semua_bebas(cells):
        return False
    p, f = r.p, r.f
    table(p, x + 1, f + 1, z + 1, "minecraft:dark_oak_pressure_plate")
    for d, (dx, dz) in FACE.items():
        chair(p, x + 1 + dx, f + 1, z + 1 + dz, OPP[d])
    p.put(x, f + 1, z + 2, "minecraft:decorated_pot", {"cracked": "false", "facing": "north", "waterlogged": "false"})
    r.titik("meja_makan", x + 1, z + 1, "south")
    r.titik("kursi", x + 1, z, "south")
    r.pakai(cells, 1)
    return True


def k_peti(r, x, z, w=3, d=2):
    """A stack of crates, barrels and hay on a pallet (storerooms)."""
    cells = [(x + i, z + j) for i in range(w) for j in range(d)]
    if not r.semua_bebas(cells, 3):
        return False
    crates(r.p, x, z, x + w - 1, z + d - 1, r.f + 1, h=3)
    r.pakai(cells, 1)
    return True


def k_arsip(r, x, z, panjang=4, along="x"):
    """A free-standing archive case: a row of bookshelves three high with a slab cap."""
    cells = [(x + i, z) if along == "x" else (x, z + i) for i in range(panjang)]
    if not r.semua_bebas(cells, 4):
        return False
    p, f = r.p, r.f
    for i, (cx, cz) in enumerate(cells):
        for dy in (1, 2, 3):
            if (i + dy) % 3 == 0:
                p.put(cx, f + dy, cz, "minecraft:chiseled_bookshelf",
                      {"facing": "south" if along == "x" else "east", "slot_0_occupied": "true", "slot_1_occupied": "true",
                       "slot_2_occupied": "false", "slot_3_occupied": "true", "slot_4_occupied": "false",
                       "slot_5_occupied": "true"})
            else:
                p.put(cx, f + dy, cz, "minecraft:bookshelf")
        p.slab(cx, f + 4, cz, "wood_slab")
    r.pakai(cells, 1)
    return True


def k_ranjang(r, x, z, hadap="north"):
    """A dormitory bay: two beds side by side, a chest at the foot, a lantern on a fence post between them."""
    cells = [(x + i, z + j) for i in range(3) for j in range(3)]
    if not r.semua_bebas(cells):
        return False
    p, f = r.p, r.f
    if hadap == "north":
        bed(p, x, f + 1, z, "north")
        bed(p, x + 2, f + 1, z, "north")
        post(p, x + 1, f + 1, z, height=1)
        chest(p, x + 1, f + 1, z + 2, None, "north")
        r.titik("kasur", x, z, "south")
    else:
        bed(p, x, f + 1, z + 2, "south")
        bed(p, x + 2, f + 1, z + 2, "south")
        post(p, x + 1, f + 1, z + 2, height=1)
        chest(p, x + 1, f + 1, z, None, "south")
        r.titik("kasur", x, z + 2, "north")
    r.pakai(cells, 0)
    return True


def k_obat(r, x, z):
    """An apothecary table: a board with a brewing stand, a barrel of roots with a fern, a jar (decorated pot)."""
    cells = [(x + i, z) for i in range(3)]
    if not r.semua_bebas(cells):
        return False
    p, f = r.p, r.f
    _papan(p, x, f + 1, z)
    p.put(x, f + 2, z, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
    p.put(x + 1, f + 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(x + 1, f + 2, z, "minecraft:potted_fern")
    p.put(x + 2, f + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})
    if r.bebas(x + 1, z + 1):
        r.titik("kerja:lumpang", x + 1, z + 1, "north")
    r.pakai(cells, 1)
    return True


def k_pajangan(r, x, z):
    """A display stand: a chiselled quartz plinth with a decorated pot or a candle cluster."""
    if not r.semua_bebas([(x, z)], 3):
        return False
    p, f = r.p, r.f
    p.put(x, f + 1, z, "minecraft:chiseled_quartz_block")
    if (x + z) % 2:
        p.put(x, f + 2, z, "minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})
    else:
        candle(p, x, f + 2, z, 3, "yellow")
    r.pakai([(x, z)], 1)
    return True


def k_pot_besar(r, x, z):
    """A tall vase with bamboo or an azalea in a soil tub."""
    if not r.semua_bebas([(x, z)], 3):
        return False
    p, f = r.p, r.f
    if (x + z) % 2 == 0:
        p.put(x, f + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": "south", "waterlogged": "false"})
        pot(p, x, f + 2, z, "minecraft:potted_bamboo")
    else:
        p.put(x, f + 1, z, "minecraft:rooted_dirt")
        p.put(x, f + 2, z, "minecraft:azalea")
    r.pakai([(x, z)], 0)
    return True


def k_layar(r, x, z, along="x", n=3):
    """A folding screen: lacquered frame posts with white panels (stained glass panes) two high."""
    cells = [(x + i, z) if along == "x" else (x, z + i) for i in range(n)]
    if not r.semua_bebas(cells, 3):
        return False
    p, f = r.p, r.f
    for i, (cx, cz) in enumerate(cells):
        role = "wood_fence" if i in (0, n - 1) else "minecraft:white_stained_glass_pane"
        p.put(cx, f + 1, cz, role)
        p.put(cx, f + 2, cz, role)
    r.pakai(cells, 1)
    return True


def k_dupa(r, x, z):
    """A small offering table: two trapdoor boards with a candle pair and a lily pot."""
    cells = [(x, z), (x + 1, z)]
    if not r.semua_bebas(cells):
        return False
    p, f = r.p, r.f
    for cx in (x, x + 1):
        _papan(p, cx, f + 1, z)
    candle(p, x, f + 2, z, 2, "red")
    pot(p, x + 1, f + 2, z, "minecraft:potted_lily_of_the_valley")
    r.pakai(cells, 1)
    return True


def k_bangku(r, x, z, along="x", n=3, hadap="south"):
    """A row of benches (stairs) whose sitters face `hadap`."""
    cells = [(x + i, z) if along == "x" else (x, z + i) for i in range(n)]
    if not r.semua_bebas(cells):
        return False
    for (cx, cz) in cells:
        chair(r.p, cx, r.f + 1, cz, hadap)
    r.titik("kursi", cells[n // 2][0], cells[n // 2][1], hadap)
    r.pakai(cells, 1)
    return True


def k_zirah(r, x, z, yaw=180.0, kit="besi"):
    """An armour stand on a stone foot (rank robes, guard kit)."""
    if not r.semua_bebas([(x, z)], 3):
        return False
    sets = {"besi": ("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"),
            "rantai": ("chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"),
            "kulit": ("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
            "emas": ("golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots")}
    h, c, l, ft = sets[kit]
    r.p.entity(x + 0.5, r.f + 1, z + 0.5, {
        "id": "minecraft:armor_stand", "Invulnerable": True, "DisabledSlots": 4144959, "ShowArms": True,
        "PersistenceRequired": True, "Rotation": [structgen.nbt.Float(yaw), structgen.nbt.Float(0.0)],
        "equipment": {"head": {"id": "minecraft:" + h, "count": 1}, "chest": {"id": "minecraft:" + c, "count": 1},
                      "legs": {"id": "minecraft:" + l, "count": 1}, "feet": {"id": "minecraft:" + ft, "count": 1}}})
    r.pakai([(x, z)], 1)
    return True


def k_batu(r, x, z):
    """A heap of uncut source stones on a plank pallet (the gamblers' stone hall): mixed rock blocks one to three high."""
    cells = [(x + i, z + j) for i in range(2) for j in range(2)]
    if not r.semua_bebas(cells, 4):
        return False
    stones = ("minecraft:stone", "minecraft:deepslate", "minecraft:tuff", "minecraft:calcite", "minecraft:cobbled_deepslate",
              "minecraft:andesite", "minecraft:granite", "minecraft:diorite")
    p, f = r.p, r.f
    for i, (cx, cz) in enumerate(cells):
        h = 1 + (cx * 3 + cz * 5) % 3
        for dy in range(1, h + 1):
            p.put(cx, f + dy, cz, stones[(cx * 7 + cz * 3 + dy) % len(stones)])
    r.pakai(cells, 1)
    return True


# ------------------------------------------------------------------------------------------------ walls and columns
_DINDING = {  # "-" leaves the cell free (walls read as furnished, not packed)
    "kantor": ("bookshelf3", "bookshelf3", "shelf", "-", "pot", "-"),
    "arsip": ("bookshelf3", "bookshelf3", "bookshelf3", "-"),
    "gilda": ("bookshelf3", "shelf", "-", "banner", "-", "pot", "shelf", "-", "barrel", "-"),
    "gudang": ("barrel2", "crate", "barrel", "shelf", "-"),
    "asrama": ("barrel", "-", "shelf", "-", "pot", "-"),
    "aula": ("banner", "-", "-", "pot", "-", "-", "banner", "-", "incense", "-"),
    "toko": ("shelf", "shelf", "barrel", "-", "pot", "-"),
    "obat": ("shelf", "barrel", "shelf", "-", "pot", "-"),
    "teh": ("pot", "-", "shelf", "-", "banner", "-", "-"),
    "makan": ("barrel", "-", "pot", "-", "shelf", "-", "banner", "-"),
    "rumah": ("shelf", "-", "barrel", "-", "pot", "-"),
    "kuil": ("incense", "-", "banner", "-", "pot", "-", "-"),
    "barak": ("barrel", "shelf", "-", "banner", "-"),
}


def dinding(r, gaya):
    """Dresses the cells along the inside of the four walls by recipe (skips doorways and reserved cells)."""
    ix0, iz0, ix1, iz1 = r.box
    p, f = r.p, r.f
    pola = _DINDING.get(gaya, ("pot",))
    sel = [(x, iz0, "south") for x in range(ix0, ix1 + 1)] + [(x, iz1, "north") for x in range(ix0, ix1 + 1)] + \
          [(ix0, z, "east") for z in range(iz0 + 1, iz1)] + [(ix1, z, "west") for z in range(iz0 + 1, iz1)]
    for (x, z, face) in sel:
        jenis = pola[(x + z) % len(pola)]
        if jenis == "-":
            continue
        need = 4 if jenis == "bookshelf3" else 3 if jenis == "banner" else 2
        if not r.bebas(x, z, need):
            continue
        if jenis == "bookshelf3":
            bookshelf_wall(p, x, z, x, z, f + 1, f + 3, face=face)
        elif jenis == "shelf":
            p.put(x, f + 1, z, "minecraft:barrel", {"facing": face, "open": "false"})
            shelf(p, x, f + 2, z, face)
        elif jenis == "barrel":
            barrel(p, x, f + 1, z, face)
        elif jenis == "barrel2":
            barrel(p, x, f + 1, z, "up")
            barrel(p, x, f + 2, z, face)
        elif jenis == "crate":
            p.put(x, f + 1, z, "minecraft:spruce_planks")
            p.put(x, f + 2, z, "minecraft:hay_block", {"axis": "y"})
        elif jenis == "pot":
            pot(p, x, f + 1, z, _BUNGA_POT[(x + 2 * z) % len(_BUNGA_POT)])
        elif jenis == "incense":
            p.put(x, f + 1, z, "kerb_chisel")
            candle(p, x, f + 2, z, 3, "red")
        elif jenis == "banner":
            wx, wz = x - FACE[face][0], z - FACE[face][1]
            if p.inside(wx, f + 3, wz) and solid(p.get(wx, f + 3, wz)):
                wall_banner(p, x, f + 3, z, face, ("red", "yellow", "white")[(x + z) % 3])
            p.put(x, f + 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": face, "waterlogged": "false"})
        r.blok.add((x, z))
        r.n += 1


def pilar_dalam(r, every=6):
    """Red columns on stone feet in two rows inside a wide hall (span 15+), up to the ceiling, with bracket stairs."""
    ix0, iz0, ix1, iz1 = r.box
    w, d = ix1 - ix0 + 1, iz1 - iz0 + 1
    p, f = r.p, r.f
    long_x = w >= d
    span = d if long_x else w
    if span < 15:
        return 0
    o = max(3, span // 4)
    n = 0
    for a in (o, span - 1 - o):
        for t in range(every // 2 + 2, (w if long_x else d) - 2, every):
            x, z = (ix0 + t, iz0 + a) if long_x else (ix0 + a, iz0 + t)
            if not r.bebas(x, z, 3):
                continue
            top = r.plafon(x, z)
            if top is None or top - f > 12:
                continue
            p.put(x, f + 1, z, "kerb_chisel")
            for yy in range(f + 2, top):
                p.put(x, yy, z, "red")
            if top - f >= 5:
                for dname, (dx, dz) in FACE.items():
                    if _kosong(p, x + dx, top - 1, z + dz):
                        p.stairs(x + dx, top - 1, z + dz, "wood_stairs", OPP[dname], half="top")
            r.pakai([(x, z)], 1)
            n += 1
    return n


def karpet_lorong(r, warna="red", tepi="yellow"):
    """A runner along the aisle (carpets never block walking)."""
    if not r.lorong:
        return
    ix0, iz0, ix1, iz1 = r.box
    p, f = r.p, r.f
    cx, cz = (ix0 + ix1) // 2, (iz0 + iz1) // 2
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            off = (x - cx) if r.lorong == "z" else (z - cz)
            if abs(off) > 1 or (x, z) in r.simpan:
                continue
            if not (p.inside(x, f, z) and solid(p.get(x, f, z)) and _kosong(p, x, f + 1, z)):
                continue
            p.put(x, f + 1, z, "minecraft:%s_carpet" % (tepi if abs(off) == 1 else warna))


RESEP = {  # gaya -> (grid step x, step z, share of grid points used, [(cluster, weight, kwargs), ...])
    "gilda": (6, 6, 0.65, [(k_meja_kerja, 3, {}), (k_teh, 3, {}), (k_pajangan, 1, {}), (k_layar, 1, {}),
                           (k_pot_besar, 1, {})]),
    "kantor": (5, 5, 0.8, [(k_meja_kerja, 5, {}), (k_arsip, 2, {}), (k_pot_besar, 1, {})]),
    "arsip": (4, 4, 0.9, [(k_arsip, 5, {"panjang": 4}), (k_meja_kerja, 1, {})]),
    "teh": (5, 5, 0.8, [(k_teh, 5, {}), (k_layar, 1, {}), (k_pot_besar, 1, {})]),
    "makan": (5, 5, 0.85, [(k_meja_makan, 5, {}), (k_teh, 1, {}), (k_pot_besar, 1, {})]),
    "gudang": (4, 4, 0.85, [(k_peti, 4, {}), (k_peti, 2, {"w": 2, "d": 2}), (k_arsip, 1, {"panjang": 3})]),
    "asrama": (4, 5, 0.85, [(k_ranjang, 5, {}), (k_layar, 1, {}), (k_pot_besar, 1, {})]),
    "obat": (5, 4, 0.8, [(k_obat, 3, {}), (k_ranjang, 2, {}), (k_layar, 1, {})]),
    "toko": (4, 4, 0.7, [(k_pajangan, 3, {}), (k_peti, 2, {"w": 2, "d": 2}), (k_pot_besar, 1, {})]),
    "aula": (7, 7, 0.6, [(k_pot_besar, 2, {}), (k_dupa, 2, {}), (k_bangku, 2, {}), (k_teh, 1, {})]),
    "kuil": (5, 4, 0.8, [(k_bangku, 4, {"n": 4, "hadap": "north"}), (k_dupa, 2, {}), (k_pot_besar, 1, {})]),
    "rumah": (5, 5, 0.7, [(k_teh, 3, {}), (k_meja_makan, 2, {}), (k_layar, 1, {}), (k_pot_besar, 1, {})]),
    "tunggu": (6, 5, 0.75, [(k_bangku, 4, {"n": 4}), (k_pot_besar, 2, {}), (k_teh, 1, {})]),
    "judi": (5, 5, 0.8, [(k_batu, 4, {}), (k_teh, 2, {}), (k_pajangan, 1, {})]),
    "senjata": (5, 5, 0.75, [(k_zirah, 2, {}), (k_pajangan, 3, {}), (k_peti, 1, {"w": 2, "d": 1})]),
    "barak": (5, 5, 0.85, [(k_ranjang, 5, {}), (k_zirah, 2, {}), (k_peti, 1, {"w": 2, "d": 1})]),
}


def _hash(x, z, salt):
    v = (x * 73856093) ^ (z * 19349663) ^ (salt * 83492791)
    return (v & 0x7fffffff) / float(0x7fffffff)


def dandan(p, box, f, gaya, lorong=None, simpan=(), pilar=False, karpet=None, wall=True, titik_maks=6, tepi=1,
           lorong_w=3, buka_jarak=2):
    """Furnishes interior `box` (ix0, iz0, ix1, iz1) on floor f by recipe `gaya`; returns the number of pieces placed.
    lorong 'x' / 'z' keeps a 3-wide aisle through the middle; simpan = extra keep-out rectangles (x0, z0, x1, z1);
    karpet = (colour, border colour) lays a runner in the aisle; pilar adds inner columns in wide halls."""
    r = Ruang(p, box, f, lorong=lorong, lorong_w=lorong_w, simpan=simpan, titik_maks=titik_maks, buka_jarak=buka_jarak)
    if pilar:
        pilar_dalam(r)
    if karpet:
        karpet_lorong(r, *karpet)
    if wall:
        dinding(r, gaya)
    ix0, iz0, ix1, iz1 = box
    sx, sz, share, items = RESEP.get(gaya, (5, 5, 0.0, []))
    total = sum(w for _, w, _ in items) or 1
    salt = len(gaya) * 31 + ix0 * 7 + iz0
    for z in range(iz0 + tepi, iz1 - tepi, sz):
        for x in range(ix0 + tepi, ix1 - tepi, sx):
            if _hash(x, z, salt) > share:
                continue
            pick = _hash(z, x, salt + 1) * total
            order = []
            for fn, w, kw in items:
                pick -= w
                if pick < 0 and not order:
                    order.append((fn, kw))
            order += [(fn, kw) for fn, _, kw in items if (fn, kw) not in order]
            for fn, kw in order:
                if fn(r, x, z, **kw):
                    break
    return r.n
