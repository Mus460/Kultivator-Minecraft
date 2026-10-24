"""Kampung Fana dan Desa Klan world generation (DESIGN 38.C.2, module 38DS).

Mortal villages (kultivator:kampung/<ukuran>_<gaya>, ukuran = dusun | desa | desa_besar, seven biome styles) are vanilla
jigsaws: a village square with the bell, the well and the shrine of the Earth God (start piece `pusat`), dirt streets
that follow the ground (terrain_matching) and buildings that hang on the street sides. Every style draws its own pieces
(stilt houses in the snow and the swamp, flat roofs and an oasis in the desert, terraced paddies on the hills, a tea
garden and a koi pond under the cherries). The street side connectors ask the pool `kampung/<gaya>/bangunan`; each size's
structure aliases it (pool_aliases, minecraft:direct) to `bangunan_<ukuran>`, so a hamlet never grows a market hall and a
market town gets its school and village hall.

The clan village (kultivator:kampung/klan_<gaya>) is one walled 144 x 144 compound drawn on a canvas (tempat_ds_kit),
cut into nine 48 x 48 lots chained by uniquely named jigsaw connectors (a fixed layout under the start's random
rotation), plus two field pieces outside the walls. Its four styles share the NBT: the structure aliases every lot pool
to the style's pool, whose processor swaps the wood and the brick (tempat_lib.GAYA["klan"]).

Writes the NBT (data/kultivator/structure/kampung/**), pools, processors, structures, biome and structure tags and, in
the built-in pack resourcepacks/dunia_kampung, the four structure sets on kultivator:cincin_tempat (kampung/dusun,
desa, desa_besar, klan; salt 380101-380104). Run:   python tools/gen_tempat_kampung.py   (then python tools/cek_tempat.py)
Only world generation in NEW chunks places these templates (DESIGN 38.0 rule 1). Deterministic: same bytes every run.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import tempat_ds_kit as K  # noqa: E402
from tempat_ds_kit import FACE, OPP  # noqa: E402

F = "kampung"
NS = "kultivator:"
PACK = "dunia_kampung"
MAKS_TITIK = 32               # 38.C.10: <= 32 points per village piece
GAYA = ("sedang", "salju", "sabana", "sakura", "gurun", "rawa", "terasering")
UKURAN = ("dusun", "desa", "desa_besar")
GAYA_KLAN = ("sedang", "salju", "sakura", "gunung")
BIOMA = {
    "sedang": ("plains", "sunflower_plains", "meadow", "forest", "birch_forest", "flower_forest"),
    "salju": ("taiga", "snowy_plains", "snowy_taiga", "grove"),
    "sabana": ("savanna", "savanna_plateau", "windswept_savanna"),
    "sakura": ("cherry_grove",),
    "gurun": ("desert",),
    "rawa": ("swamp", "mangrove_swamp"),
    "terasering": ("windswept_hills", "windswept_forest", "windswept_gravelly_hills", "stony_shore"),
}
BIOMA_KLAN = {
    "sedang": ("plains", "sunflower_plains", "forest", "birch_forest", "flower_forest", "dark_forest"),
    "salju": ("taiga", "snowy_plains", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga"),
    "sakura": ("cherry_grove",),
    "gunung": ("meadow", "grove", "windswept_hills"),
}
SET = {  # id: (spacing, separation, salt, min_chunks, rings [(until chunk, frequency)], exclusion)
    "dusun": (24, 9, 380101, 6, [(62, 0.30), (187, 0.26), (500, 0.21), (100000, 0.16)], ("minecraft:villages", 4)),
    "desa": (36, 12, 380102, 8, [(62, 0.27), (187, 0.32), (500, 0.27), (100000, 0.22)], ("minecraft:villages", 6)),
    "desa_besar": (64, 24, 380103, 16, [(62, 0.17), (187, 0.25), (500, 0.25), (100000, 0.20)], ("minecraft:villages", 6)),
    "klan": (56, 20, 380104, 24, [(62, 0.0), (187, 0.26), (500, 0.33), (100000, 0.33)], ("minecraft:villages", 6)),
}
JIGSAW = {"dusun": (3, 40, "beard_thin"), "desa": (5, 72, "beard_thin"), "desa_besar": (6, 100, "beard_thin"),
          "klan": (4, 116, "beard_box")}

J_JALAN = NS + "kampung/jalan"
J_SISI = NS + "kampung/sisi"
J_PINTU = NS + "kampung/pintu"

# ===================================================================================================== styles
S = {
    "sedang": dict(kayu="oak", vtype="plains", dinding="minecraft:white_terracotta", fondasi="minecraft:cobblestone",
                   lantai="minecraft:oak_planks", atap="jerami", jalan="minecraft:dirt_path", tepi="minecraft:coarse_dirt",
                   panggung=0, pohon="oak", tanah="minecraft:grass_block"),
    "salju": dict(kayu="spruce", vtype="snow", dinding="minecraft:stripped_spruce_log", fondasi="minecraft:cobblestone",
                  lantai="minecraft:spruce_planks", atap="salju", jalan="minecraft:dirt_path", tepi="minecraft:packed_ice",
                  panggung=2, pohon="spruce", tanah="minecraft:snow_block"),
    "sabana": dict(kayu="acacia", vtype="savanna", dinding="minecraft:terracotta", fondasi="minecraft:packed_mud",
                   lantai="minecraft:acacia_planks", atap="kayu", jalan="minecraft:coarse_dirt", tepi="minecraft:dirt_path",
                   panggung=0, pohon="acacia", tanah="minecraft:grass_block"),
    "sakura": dict(kayu="cherry", vtype="plains", dinding="minecraft:white_terracotta", fondasi="minecraft:stone_bricks",
                   lantai="minecraft:cherry_planks", atap="kayu", jalan="minecraft:gravel", tepi="minecraft:dirt_path",
                   panggung=0, pohon="cherry", tanah="minecraft:grass_block"),
    "gurun": dict(kayu="jungle", vtype="desert", dinding="minecraft:smooth_sandstone", fondasi="minecraft:sandstone",
                  lantai="minecraft:cut_sandstone", atap="datar", jalan="minecraft:smooth_sandstone", tepi="minecraft:sand",
                  panggung=0, pohon="palma", tanah="minecraft:sand"),
    "rawa": dict(kayu="mangrove", vtype="swamp", dinding="minecraft:mangrove_planks", fondasi="minecraft:mud_bricks",
                 lantai="minecraft:mangrove_planks", atap="kayu", jalan="minecraft:mangrove_planks", tepi="minecraft:mangrove_slab",
                 panggung=3, pohon="mangrove", tanah="minecraft:mud"),
    "terasering": dict(kayu="spruce", vtype="plains", dinding="minecraft:cobblestone", fondasi="minecraft:stone_bricks",
                       lantai="minecraft:spruce_planks", atap="genteng", jalan="minecraft:cobblestone", tepi="minecraft:gravel",
                       panggung=0, pohon="birch", tanah="minecraft:grass_block"),
}


def pal(st):
    """A structgen palette for a style (roles used by the kit helpers too)."""
    k = st["kayu"]
    p = dict(K.PAL)
    p.update({
        "wood": "minecraft:%s_planks" % k, "wood_alt": st["lantai"], "wood_stairs": "minecraft:%s_stairs" % k,
        "wood_slab": "minecraft:%s_slab" % k, "wood_trapdoor": "minecraft:%s_trapdoor" % k, "wood_fence": "minecraft:%s_fence" % k,
        "wood_gate": "minecraft:%s_fence_gate" % k, "door": "minecraft:%s_door" % k, "log": "minecraft:%s_log" % k,
        "beam": "minecraft:stripped_%s_log" % k, "shelf": "minecraft:%s_shelf" % k, "rail": "minecraft:%s_fence" % k,
        "fence": "minecraft:%s_fence" % k, "window": "minecraft:%s_trapdoor" % k, "plaster": st["dinding"],
        "wall": st["dinding"], "kerb": st["fondasi"], "found": st["fondasi"], "floor": st["lantai"], "path": st["jalan"],
        "path_alt": st["tepi"], "roof": "minecraft:%s_stairs" % k, "roof_slab": "minecraft:%s_slab" % k,
        "roof_full": "minecraft:%s_planks" % k, "glow": "minecraft:lantern", "red": "minecraft:%s_log" % k,
        "bed": "minecraft:%s_bed" % {"sedang": "brown", "salju": "light_gray", "sabana": "orange", "sakura": "pink",
                                    "gurun": "yellow", "rawa": "green", "terasering": "white"}.get(st.get("_id"), "brown"),
    })
    if st["kayu"] == "mangrove":
        p["log"] = "minecraft:mangrove_log"
        p["beam"] = "minecraft:stripped_mangrove_log"
    if st["atap"] == "genteng":
        p.update({"roof": "minecraft:deepslate_tile_stairs", "roof_slab": "minecraft:deepslate_tile_slab",
                  "roof_full": "minecraft:deepslate_tiles"})
    return p


for _g, _s in S.items():
    _s["_id"] = _g


def vtype(st):
    return "minecraft:" + st["vtype"]


# ===================================================================================================== pieces
PIECES = {g: {} for g in GAYA}     # style -> {piece name: template id}


class Potongan(tl.Lot):
    """A village piece: tl.Lot of any size up to 48 with style palette and a <= 32 point budget."""

    def __init__(self, gaya, nama, w, h, d, geser=(0, 0)):
        self.o = (0, 0)
        super().__init__(F, "%s/%s" % (gaya, nama), tinggi=h, lebar=w, dalam=d, palet=pal(S[gaya]))
        self.st = S[gaya]
        self.gaya = gaya
        self.pendek = nama
        self.o = geser

    # geser: every coordinate the builder writes is moved by (ox, oz), so eaves and stalls get a margin in the piece
    def inside(self, x, y, z):
        return super().inside(x + self.o[0], y, z + self.o[1])

    def set(self, x, y, z, block, props=None, nbt=None):
        o, self.o = self.o, (0, 0)
        try:
            super().set(x + o[0], y, z + o[1], block, props, nbt)
        finally:
            self.o = o

    def get(self, x, y, z):
        return super().get(x + self.o[0], y, z + self.o[1])

    def clear(self, x, y, z):
        super().clear(x + self.o[0], y, z + self.o[1])

    def titik(self, jenis, x, y, z, hadap="south", data=None):
        e = super().titik(jenis, x, y, z, hadap, data)
        e["pos"][0] += self.o[0]
        e["pos"][2] += self.o[1]
        return e

    def entity(self, x, y, z, nbt):
        super().entity(x, y, z, nbt)
        ex, ey, ez, data = self.entities[-1]
        self.entities[-1] = (ex + self.o[0], ey, ez + self.o[1], data)

    def simpan_ke(self, reg=None):
        if len(self.titik_list) > MAKS_TITIK:
            raise SystemExit("%s: %d titik > %d" % (self.id, len(self.titik_list), MAKS_TITIK))
        self.simpan()
        (reg if reg is not None else PIECES[self.gaya])[self.pendek] = self.id
        return self.id


def pintu_depan(p, x, z, facing="south", final="minecraft:air", y=1):
    """The building's connector at its doorstep (it hangs on a street side connector)."""
    p.jigsaw_side(x, y, z, facing, name=J_PINTU, target=J_SISI, pool="minecraft:empty", final_state=final)


def villager(p, x, y, z, prof="minecraft:none"):
    p.entity(x + 0.5, y, z + 0.5, {"id": "minecraft:villager", "PersistenceRequired": True,
                                   "VillagerData": {"type": vtype(p.st), "profession": prof, "level": 1}})


def hewan(p, x, y, z, jenis):
    p.entity(x + 0.5, y, z + 0.5, {"id": "minecraft:" + jenis, "PersistenceRequired": True})


def pohon(p, x, y, z, jenis, h=5):
    """A small tree of the style: oak/birch round crown, spruce cone, acacia flat crown, cherry, desert palm, mangrove."""
    leaf = {"oak": "minecraft:oak_leaves", "birch": "minecraft:birch_leaves", "spruce": "minecraft:spruce_leaves",
            "acacia": "minecraft:acacia_leaves", "cherry": "minecraft:cherry_leaves", "palma": "minecraft:jungle_leaves",
            "mangrove": "minecraft:mangrove_leaves"}[jenis]
    log = {"palma": "minecraft:jungle_log"}.get(jenis, "minecraft:%s_log" % jenis)

    def lf(xx, yy, zz):
        if p.inside(xx, yy, zz) and p.get(xx, yy, zz) is None:
            p.put(xx, yy, zz, leaf, {"persistent": "true", "distance": "1", "waterlogged": "false"})
    for dy in range(h):
        p.put(x + (1 if jenis == "palma" and dy > h - 3 else 0), y + dy, z, log, {"axis": "y"})
    top = y + h
    if jenis == "spruce":
        for dy, r in ((-3, 2), (-2, 1), (-1, 2), (0, 1), (1, 0)):
            for ox in range(-r, r + 1):
                for oz in range(-r, r + 1):
                    if abs(ox) + abs(oz) <= r + 1:
                        lf(x + ox, top + dy, z + oz)
    elif jenis == "palma":
        cx = x + 1
        lf(cx, top, z)
        for dx, dz in FACE.values():
            for i in (1, 2, 3):
                lf(cx + dx * i, top - (1 if i == 3 else 0), z + dz * i)
    elif jenis == "acacia":
        for ox in range(-2, 3):
            for oz in range(-2, 3):
                if abs(ox) + abs(oz) <= 3:
                    lf(x + ox, top, z + oz)
        lf(x, top + 1, z)
    else:
        for ox in range(-2, 3):
            for oz in range(-2, 3):
                for dy in (-1, 0, 1):
                    if math.hypot(ox, oz) + abs(dy) * 0.8 <= 2.4:
                        lf(x + ox, top + dy, z + oz)


# ----------------------------------------------------------------------------------------------- roofs per style
def atap(p, x0, y, z0, x1, z1, along="x"):
    """The roof of a style over the wall box (x0..x1, z0..z1); returns the top y."""
    st = p.st
    kind = st["atap"]
    if kind == "datar":
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                p.put(x, y, z, "minecraft:cut_sandstone" if edge else "minecraft:smooth_sandstone")
                if edge and (x + z) % 2 == 0:
                    p.put(x, y + 1, z, "minecraft:smooth_sandstone_slab", {"type": "bottom", "waterlogged": "false"})
        for x in range(x0 + 1, x1, 3):
            p.put(x, y - 1, z1 + 1, "minecraft:jungle_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                                  "powered": "false", "waterlogged": "false"})
        return y + 1
    if kind == "jerami":
        # thatch: hay steps with oak slab eaves, a hay ridge
        w = (z1 - z0) if along == "x" else (x1 - x0)
        layers = w // 2 + 1
        for i in range(layers + 1):
            for u in range((x0 if along == "x" else z0) - 1, (x1 if along == "x" else z1) + 2):
                for v in ((z0 - 1 + i, z1 + 1 - i) if along == "x" else (x0 - 1 + i, x1 + 1 - i)):
                    xx, zz = (u, v) if along == "x" else (v, u)
                    if i == 0:
                        p.slab(xx, y + i, zz, "minecraft:oak_slab")
                    else:
                        p.put(xx, y + i, zz, "minecraft:hay_block", {"axis": "x" if along == "x" else "z"})
            if (z1 + 1 - i) - (z0 - 1 + i) <= 1 if along == "x" else (x1 + 1 - i) - (x0 - 1 + i) <= 1:
                return y + i + 1
        return y + layers + 1
    top = p.roof_gable(x0, y, z0, x1, z1, along=along, overhang=1, eave_curl=True)
    if kind == "salju":
        mid = (z0 + z1) // 2 if along == "x" else (x0 + x1) // 2
        for u in range((x0 if along == "x" else z0) - 1, (x1 if along == "x" else z1) + 2):
            xx, zz = (u, mid) if along == "x" else (mid, u)
            if p.inside(xx, top + 1, zz):
                p.put(xx, top + 1, zz, "minecraft:snow", {"layers": "2"})
    return top


# ----------------------------------------------------------------------------------------------- houses
def rumah(gaya, ukuran):
    """A farmer's house of a style: kecil 7 x 7, sedang 9 x 9, besar 11 x 9 plus a yard; beds, table, stove, chest,
    the villager's workstation, lanterns, window shutters; stilts in snow and swamp styles."""
    fw, fd = {"kecil": (7, 7), "sedang": (9, 9), "besar": (11, 9)}[ukuran]
    st = S[gaya]
    pg = st["panggung"]
    yard = 5 if ukuran == "besar" else 3
    w, d = fw + 2, fd + 2 + yard
    p = Potongan(gaya, "rumah_" + ukuran, w, pg + 6 + fd // 2 + 4, d)
    x0, z0, x1, z1 = 1, 1, fw, fd
    f = pg        # floor block y
    # foundation / stilts
    if pg:
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), ((x0 + x1) // 2, z0), ((x0 + x1) // 2, z1)):
            for y in range(0, pg):
                p.put(x, y, z, "log", {"axis": "y"})
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, f, z, "floor" if not (x in (x0, x1) or z in (z0, z1)) else "kerb")
    p.walls(x0, f + 1, z0, x1, f + 4, z1, role="wall", pillar="log", windows=3, window_role="window")
    K.beam_band(p, x0, z0, x1, z1, f + 4)
    cx = (x0 + x1) // 2
    # door (south) and steps
    p.put(cx, f + 1, z1, "minecraft:air")
    p.put(cx, f + 2, z1, "minecraft:air")
    K.door(p, cx, f + 1, z1, "south", role="door")
    if pg:
        for i in range(pg):
            p.stairs(cx, pg - i, z1 + 1 + i, "wood_stairs", "north")
            for yy in range(0, pg - i):
                p.put(cx, yy, z1 + 1 + i, "log", {"axis": "y"})
        for x in range(x0, x1 + 1):
            p.slab(x, f, z1 + 1, "wood_slab", "top") if x != cx else None
            if x in (x0, x1):
                p.put(x, f + 1, z1 + 1, "fence")
    p.titik("pintu", cx, f + 1, z1 + 1 if not pg else z1 + pg + 1, "south") if p.inside(cx, f + 1, z1 + pg + 1) else None
    # roof
    atap(p, x0, f + 5, z0, x1, z1, along="x")
    # interior
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    beds = {"kecil": 1, "sedang": 2, "besar": 3}[ukuran]
    for i in range(beds):
        bx = ix0 + i * 2
        K.bed(p, bx, f + 1, iz0, "north")
        p.titik("kasur", bx, f + 1, iz0, "south")
    stove = "minecraft:smoker" if gaya in ("salju", "rawa") else "minecraft:furnace"
    if gaya == "salju":
        p.put(cx, f + 1, (iz0 + iz1) // 2 + 1, "minecraft:smoker", {"facing": "south", "lit": "false"})
        p.titik("kerja:kuali", cx, f + 1, (iz0 + iz1) // 2 + 2, "north")
    else:
        p.put(ix1, f + 1, iz0, stove, {"facing": "south", "lit": "false"})
        p.put(ix1, f + 3, iz0, "minecraft:%s_trapdoor" % st["kayu"], {"facing": "south", "half": "top", "open": "false",
                                                                   "powered": "false", "waterlogged": "false"})
        p.titik("kerja:kuali", ix1, f + 1, iz0 + 1, "north")
    tx, tz = ix0 + 1, iz1 - 1
    K.table(p, tx, f + 1, tz, "minecraft:%s_pressure_plate" % st["kayu"])
    p.titik("meja_makan", tx, f + 1, tz, "south")
    p.stairs(tx + 1, f + 1, tz, "wood_stairs", "east")
    p.titik("kursi", tx + 1, f + 1, tz, "west")
    K.chest(p, ix1, f + 1, iz1, None, "west")
    p.titik("peti_milik", ix1 - 1, f + 1, iz1, "east")
    work = {"kecil": "minecraft:composter", "sedang": "minecraft:loom", "besar": "minecraft:fletching_table"}[ukuran]
    p.put(ix1, f + 1, (iz0 + iz1) // 2, work, {"level": "2"} if work.endswith("composter") else ({"facing": "west"} if work.endswith("loom") else None))
    p.put(ix0, f + 1, (iz0 + iz1) // 2, "minecraft:flower_pot" if gaya == "gurun" else "minecraft:potted_red_tulip")
    p.lantern(cx, f + 5, (iz0 + iz1) // 2, chain=1)
    villager(p, cx, f + 1, (iz0 + iz1) // 2 + 1, "minecraft:none")
    if ukuran == "besar":
        villager(p, ix0 + 1, f + 1, (iz0 + iz1) // 2, "minecraft:none")
        # ancestor shelf
        p.put(cx - 1, f + 2, iz0, "minecraft:%s_shelf" % st["kayu"], {"facing": "south", "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})
        p.put(cx - 1, f + 3, iz0, "minecraft:red_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    # yard: drying line, crates, a little garden; doorstep connector at the front edge
    yz = z1 + 2 + (pg if pg else 0)
    if yz < d - 1:
        for x in (x0, x1):
            p.put(x, f + 1 if not pg else 1, d - 3, "fence")
            p.put(x, f + 2 if not pg else 2, d - 3, "fence")
        for x in range(x0 + 1, x1):
            p.put(x, (f + 2 if not pg else 2) + 1, d - 3, "minecraft:white_carpet" if x % 2 else "minecraft:light_blue_carpet")
        if ukuran != "kecil":
            K.barrel(p, x0, 1 if not pg else 1, d - 2, "up")
            p.put(x1, 1, d - 2, "minecraft:hay_block", {"axis": "y"})
    p.titik("obrol", cx + 1, 1 if not pg else 1, d - 2, "west")
    pintu_depan(p, cx, d - 1, "south")
    p.titik("jalan", cx, 1, d - 1, "north", {"urut": 0, "jalur": "rumah"})
    return p.simpan_ke()


def rumah_panjang(gaya):
    """A longhouse of two households under one roof (13 x 7 plus a veranda): a bedroom with two beds, a wardrobe and the
    ancestor shelf; a living kitchen with the stove, a dining table for four and the loom; a partition with an inner
    door; a deep veranda on posts with benches and a hanging lantern; the rice mortar (lesung) in the yard."""
    st = S[gaya]
    pg = st["panggung"]
    fw, fd, yard = 13, 7, 3
    w, d = fw + 2, fd + 2 + 2 + yard + pg
    p = Potongan(gaya, "rumah_panjang", w, pg + 6 + fd // 2 + 4, d)
    x0, z0, x1, z1 = 1, 1, fw, fd
    f = pg
    if pg:
        for x in range(x0, x1 + 1, 4):
            for z in (z0, z1, z1 + 2):
                for y in range(0, pg):
                    p.put(x, y, z, "log", {"axis": "y"})
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, f, z, "floor" if not (x in (x0, x1) or z in (z0, z1)) else "kerb")
    p.walls(x0, f + 1, z0, x1, f + 4, z1, role="wall", pillar="log", windows=3, window_role="window")
    K.beam_band(p, x0, z0, x1, z1, f + 4)
    xm = (x0 + x1) // 2          # partition
    for z in range(z0 + 1, z1):
        for y in range(f + 1, f + 4):
            p.put(xm, y, z, "wall" if z != (z0 + z1) // 2 or y > f + 2 else "minecraft:air")
    K.door(p, xm, f + 1, (z0 + z1) // 2, "east", role="door")
    for dx in (x0 + 3, x1 - 3):
        p.put(dx, f + 1, z1, "minecraft:air")
        p.put(dx, f + 2, z1, "minecraft:air")
        K.door(p, dx, f + 1, z1, "south", role="door")
        p.titik("pintu", dx, f + 1, z1 + 1, "south")
    atap(p, x0, f + 5, z0, x1, z1, along="x")
    # the veranda: a slab deck, posts, a lean-to roof under the eave, benches, a lantern
    for x in range(x0, x1 + 1):
        for z in (z1 + 1, z1 + 2):
            p.slab(x, f, z, "wood_slab", "top") if pg else p.put(x, f, z, "kerb" if z == z1 + 2 else "floor")
            p.slab(x, f + 4, z, "wood_slab", "bottom")
    for x in (x0, x0 + 4, x1 - 4, x1):
        for y in range(f + 1, f + 4):
            p.put(x, y, z1 + 2, "log", {"axis": "y"})
    for x, hd in ((x0 + 1, "east"), (x1 - 1, "west")):
        p.stairs(x, f + 1, z1 + 1, "wood_stairs", OPP[hd])
        p.titik("kursi", x, f + 1, z1 + 1, hd)
    p.lantern(xm, f + 4, z1 + 1, chain=0)
    p.titik("obrol", xm, f + 1, z1 + 2, "north")
    if pg:
        for i in range(pg):
            p.stairs(xm, pg - i, z1 + 3 + i, "wood_stairs", "north")
            for yy in range(0, pg - i):
                p.put(xm, yy, z1 + 3 + i, "log", {"axis": "y"})
    # west room: the bedroom
    ix0, iz0, iz1 = x0 + 1, z0 + 1, z1 - 1
    for i in range(2):
        bx = ix0 + i * 2
        K.bed(p, bx, f + 1, iz0, "north")
        p.titik("kasur", bx, f + 1, iz0, "south")
    K.chest(p, xm - 1, f + 1, iz0, None, "south")
    p.titik("peti_milik", xm - 1, f + 1, iz0 + 1, "north")
    K.barrel(p, ix0, f + 1, iz1, "east")
    K.barrel(p, ix0, f + 2, iz1, "east")
    p.put(xm - 1, f + 2, iz1, "minecraft:%s_shelf" % st["kayu"], {"facing": "west", "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})
    p.put(xm - 1, f + 3, iz1, "minecraft:red_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    p.put(xm - 1, f + 1, iz1, "minecraft:%s_planks" % st["kayu"])
    p.put(ix0 + 2, f + 1, iz1, "minecraft:brown_carpet" if gaya != "sakura" else "minecraft:pink_carpet")
    p.lantern(ix0 + 2, f + 5, (iz0 + iz1) // 2, chain=1)
    # east room: the living kitchen
    jx0, jx1 = xm + 1, x1 - 1
    stove = "minecraft:smoker" if gaya in ("salju", "rawa") else "minecraft:furnace"
    p.put(jx1, f + 1, iz0, stove, {"facing": "west", "lit": "true"})
    p.put(jx1 - 1, f + 1, iz0, "minecraft:water_cauldron", {"level": "3"})
    p.put(jx1 - 2, f + 1, iz0, "minecraft:crafting_table")
    for x in (jx1, jx1 - 1, jx1 - 2):
        p.put(x, f + 3, iz0, "minecraft:%s_trapdoor" % st["kayu"], {"facing": "south", "half": "top", "open": "false",
                                                                  "powered": "false", "waterlogged": "false"})
    p.titik("kerja:kuali", jx1, f + 1, iz0 + 1, "north")
    K.furnish_dining(p, jx0 + 2, iz1 - 2, f, n=3)
    p.put(jx0, f + 1, iz0, "minecraft:loom", {"facing": "south"})
    p.titik("kerja:tenun", jx0, f + 1, iz0 + 1, "north")
    K.barrel(p, jx1, f + 1, iz1, "west")
    p.put(jx1, f + 2, iz1, "minecraft:decorated_pot", {"cracked": "false", "facing": "west", "waterlogged": "false"})
    p.lantern(jx0 + 2, f + 5, (iz0 + iz1) // 2, chain=1)
    villager(p, jx0 + 1, f + 1, iz0 + 2, "minecraft:none")
    villager(p, ix0 + 1, f + 1, iz0 + 2, "minecraft:none")
    # the yard: a rice mortar (a hollowed log) and a pile of husks; the doorstep connector
    yz = z1 + 3 + pg
    if yz < d - 1:
        p.put(x0 + 1, 1, yz, "beam", {"axis": "x"})
        p.put(x0 + 2, 1, yz, "beam", {"axis": "x"})
        p.titik("kerja:lesung", x0 + 1, 1, yz + 1, "north")
        p.put(x1 - 1, 1, yz, "minecraft:hay_block", {"axis": "y"})
        K.barrel(p, x1 - 2, 1, yz, "up")
    pintu_depan(p, xm, d - 1, "south")
    p.titik("jalan", xm, 1, d - 1, "north", {"urut": 0, "jalur": "rumah"})
    return p.simpan_ke()


def warung_teh(gaya):
    """The village tea stall (warung teh): a small kitchen open to the street behind a counter, the kettle hanging over
    a charcoal fire, tea jars on the shelves; in front, under a striped awning on posts, two low tables with cushions
    and a table with stools where farmers sit out the noon heat and trade the news (kerja:teko, obrol)."""
    st = S[gaya]
    cloth = {"sedang": "minecraft:blue_wool", "salju": "minecraft:red_wool", "sabana": "minecraft:orange_wool",
             "sakura": "minecraft:pink_wool", "gurun": "minecraft:yellow_wool", "rawa": "minecraft:green_wool",
             "terasering": "minecraft:brown_wool"}[gaya]
    p = Potongan(gaya, "warung_teh", 11, 9, 12)
    for x in range(11):
        for z in range(11):
            p.put(x, 0, z, "floor" if z <= 4 else (st["jalan"] if (x + z) % 5 else st["tepi"] if not st["tepi"].endswith("_slab") else st["jalan"]))
    # the kitchen box: three walls, open to the south behind the counter
    p.walls(0, 1, 0, 10, 3, 4, role="wall", pillar="log", windows=0)
    for x in range(1, 10):
        for y in (1, 2, 3):
            p.put(x, y, 4, "minecraft:air")
    atap(p, 0, 4, 0, 10, 4, along="x")
    p.put(2, 1, 1, "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
    p.put(2, 2, 1, "minecraft:cauldron")
    p.put(2, 3, 1, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    p.put(1, 1, 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(3, 1, 1, "minecraft:water_cauldron", {"level": "3"})
    for x in range(5, 10):
        p.put(x, 2, 1, "minecraft:%s_shelf" % st["kayu"], {"facing": "south", "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})
        p.put(x, 1, 1, "minecraft:barrel", {"facing": "south", "open": "false"} if x % 2 else {"facing": "up", "open": "false"})
        p.put(x, 3, 1, "minecraft:decorated_pot" if x % 2 else "minecraft:flower_pot",
              {"cracked": "false", "facing": "south", "waterlogged": "false"} if x % 2 else None)
    K.counter(p, 1, 9, 4, 0, "south")
    p.put(5, 1, 4, "minecraft:air")
    p.put(5, 2, 4, "minecraft:air")
    p.put(4, 2, 4, "minecraft:flower_pot")
    p.put(6, 2, 4, "minecraft:white_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    p.titik("kerja:teko", 3, 1, 3, "south")
    # the awning on posts and the seats under it
    for (x, z) in ((0, 6), (10, 6), (0, 10), (10, 10)):
        for y in (1, 2, 3):
            p.put(x, y, z, "fence")
    K.awning(p, 0, 10, 6, 10, 4, a=cloth, b="minecraft:white_wool")
    for (tx, tz) in ((2, 8), (8, 8)):
        K.low_table(p, tx, 1, tz)
        for dz, hd in ((-1, "south"), (1, "north")):
            p.put(tx, 1, tz + dz, "minecraft:%s_carpet" % cloth.split(":")[1].replace("_wool", ""))
            p.titik("kursi_lantai", tx, 1, tz + dz, hd)
    K.table(p, 5, 1, 9, "minecraft:%s_pressure_plate" % st["kayu"])
    p.titik("meja_makan", 5, 1, 9, "south")
    for dx, hd in ((-1, "east"), (1, "west")):
        p.stairs(5 + dx, 1, 9, "wood_stairs", OPP[hd])
        p.titik("kursi", 5 + dx, 1, 9, hd)
    p.lantern(5, 4, 7, chain=0)
    p.titik("obrol", 1, 1, 7, "east")
    p.titik("obrol", 9, 1, 7, "west")
    villager(p, 5, 1, 7)
    pintu_depan(p, 5, 11, "south")
    return p.simpan_ke()


# ----------------------------------------------------------------------------------------------- fields and yards
def ladang(gaya):
    """A field 11 x 11 (sakura: a tea garden): farmland rows of wheat, carrots, potatoes and beetroots in ages, an
    irrigation channel in the middle, a scarecrow (hay + carved pumpkin), four hoeing points."""
    st = S[gaya]
    p = Potongan(gaya, "ladang", 13, 5, 14)
    for x in range(13):
        for z in range(13):
            edge = x in (0, 12) or z in (0, 12)
            if edge:
                p.put(x, 0, z, st["fondasi"] if gaya in ("terasering", "gurun") else "minecraft:%s_log" % st["kayu"] if st["kayu"] != "mangrove" else "minecraft:mangrove_log", {"axis": "x" if z in (0, 12) else "z"} if gaya not in ("terasering", "gurun") else None)
            elif x == 6:
                p.put(x, 0, z, "minecraft:water", {"level": "0"})
            elif gaya == "sakura":
                p.put(x, 0, z, "minecraft:grass_block", {"snowy": "false"})
                if z % 2 == 0:
                    p.put(x, 1, z, "minecraft:azalea" if (x + z) % 4 else "minecraft:flowering_azalea")
            else:
                p.put(x, 0, z, "minecraft:farmland", {"moisture": "7"})
                crop = ("wheat", "carrots", "potatoes", "beetroots")[(x // 3 + z // 6) % 4]
                age = {"wheat": 7, "carrots": 7, "potatoes": 7, "beetroots": 3}[crop]
                p.put(x, 1, z, "minecraft:" + crop, {"age": str(max(1, age - (x * z) % 4))})
    # scarecrow
    p.put(3, 1, 3, "minecraft:%s_fence" % st["kayu"])
    p.put(3, 2, 3, "minecraft:hay_block", {"axis": "y"})
    p.put(2, 2, 3, "minecraft:%s_fence" % st["kayu"])
    p.put(4, 2, 3, "minecraft:%s_fence" % st["kayu"])
    p.put(3, 3, 3, "minecraft:carved_pumpkin", {"facing": "south"})
    for (x, z) in ((3, 9), (9, 9), (9, 3), (3, 6)):
        p.titik("kerja:sabit" if gaya == "sakura" else "kerja:cangkul", x, 1, z, "north")
    pintu_depan(p, 6, 13, "south")
    return p.simpan_ke()


def sawah(gaya):
    """Rice terraces 15 x 15: water plots bordered by mud brick bunds (terasering: three stepped terraces), a scarecrow
    on the middle bund, and the farmer's stilt hut (pondok) standing in the lowest plot: log posts, a slab floor, a
    thatched hay roof, a seat where the farmer rests in the heat of the day."""
    st = S[gaya]
    p = Potongan(gaya, "sawah", 15, 8, 16)
    steps = 3 if gaya == "terasering" else 1
    for t in range(steps):
        z0 = t * 5
        y = (steps - 1 - t)
        for x in range(15):
            for z in range(z0, z0 + (5 if steps > 1 else 15)):
                for yy in range(0, y):
                    p.put(x, yy, z, "minecraft:mud_bricks")
                bund = x in (0, 14) or z in (z0, z0 + (4 if steps > 1 else 14)) or x == 7
                if bund:
                    p.put(x, y, z, "minecraft:mud_bricks" if gaya != "gurun" else "minecraft:sandstone")
                else:
                    p.put(x, y, z, "minecraft:water", {"level": "0"})
                    if (x + z) % 3 == 0:
                        p.put(x, y + 1, z, "minecraft:short_grass")
    for x in (3, 11):
        p.titik("kerja:cangkul", x, steps, 14, "north")
    p.titik("kerja:sabit", 7, steps, 7, "east")
    # scarecrow on the middle bund of the top plot
    sy = steps
    p.put(7, sy, 2, "fence")
    p.put(7, sy + 1, 2, "minecraft:hay_block", {"axis": "y"})
    p.put(6, sy + 1, 2, "fence")
    p.put(8, sy + 1, 2, "fence")
    p.put(7, sy + 2, 2, "minecraft:carved_pumpkin", {"facing": "south"})
    # the stilt hut in the lowest plot (y 0)
    for (x, z) in ((11, 11), (13, 11), (11, 13), (13, 13)):
        for y in range(0, 4):
            p.put(x, y, z, "log", {"axis": "y"})
    for x in range(11, 14):
        for z in range(11, 14):
            if (x, z) not in ((11, 11), (13, 11), (11, 13), (13, 13)):
                p.slab(x, 1, z, "wood_slab", "top")
    for x in range(10, 15):
        for z in range(10, 15):
            if x in (10, 14) or z in (10, 14):
                p.slab(x, 4, z, "minecraft:oak_slab" if gaya != "gurun" else "minecraft:sandstone_slab", "bottom")
            else:
                p.put(x, 4, z, "minecraft:hay_block", {"axis": "y"})
    p.put(12, 5, 12, "minecraft:hay_block", {"axis": "y"})
    p.stairs(12, 2, 11, "wood_stairs", "north")
    p.titik("kursi", 12, 2, 11, "south")
    p.put(13, 2, 12, "minecraft:decorated_pot", {"cracked": "false", "facing": "west", "waterlogged": "false"})
    p.put(11, 2, 12, "minecraft:%s_trapdoor" % st["kayu"], {"facing": "east", "half": "bottom", "open": "true",
                                                         "powered": "false", "waterlogged": "false"})
    pintu_depan(p, 7, 15, "south")
    return p.simpan_ke()


def lumbung(gaya):
    """Granary 9 x 8 x 9 (salju: a salted-fish store): raised floor on posts, hay bales and grain sacks, a loft reached
    by a ladder, the steelyard where the harvest is weighed (kerja:timbangan), a hoist beam over the door with a bale
    hanging from its chain, and the barn cat that keeps the mice down. The fish store dries kelp and fish on lines,
    keeps its catch on a floor of packed ice and salts it by the smoker."""
    st = S[gaya]
    nama = "gudang_ikan" if gaya == "salju" else "lumbung"
    p = Potongan(gaya, nama, 11, 15, 12, geser=(1, 1))
    for x in range(9):
        for z in range(9):
            p.put(x, 0, z, "kerb")
            p.put(x, 1, z, "floor")
    p.walls(0, 2, 0, 8, 6, 8, role="wall", pillar="log", windows=0)
    K.beam_band(p, 0, 0, 8, 8, 6)
    for dy in (2, 3, 4):
        p.put(4, dy, 8, "minecraft:air")
    p.put(3, 2, 8, "minecraft:%s_fence_gate" % st["kayu"], {"facing": "south", "in_wall": "false", "open": "true", "powered": "false"})
    atap(p, 0, 7, 0, 8, 8, along="z")
    # the loft over the back third, the ladder up to it
    for x in range(1, 8):
        for z in range(1, 4):
            p.slab(x, 5, z, "wood_slab", "top")
    K.ladder(p, 1, 2, 5, 4, "east")
    if gaya == "salju":
        for x in range(2, 8):
            for z in (4, 6):
                p.put(x, 4, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
                p.put(x, 3, z, "minecraft:dried_kelp_block")
        for x in (6, 7):
            for z in (6, 7):
                p.put(x, 1, z, "minecraft:packed_ice")
                K.barrel(p, x, 2, z, "up")
        p.put(7, 2, 5, "minecraft:smoker", {"facing": "west", "lit": "true"})
        K.barrel(p, 2, 2, 7, "up")
        for x in range(2, 7):
            p.put(x, 6, 1, "minecraft:dried_kelp_block")
        p.titik("kerja:rak", 4, 2, 5, "north")
        p.titik("kerja:kuali", 6, 2, 5, "east")
    else:
        for x in range(2, 8):
            for z in range(1, 3):
                p.put(x, 2 + (x + z) % 2, z, "minecraft:hay_block", {"axis": "x"})
        for x in range(2, 7):
            for z in range(1, 4):
                if (x + z) % 3:
                    p.put(x, 6, z, "minecraft:hay_block", {"axis": "z"})
        # grain sacks (white wool on barrels) along the east wall
        for z in range(4, 8):
            K.barrel(p, 7, 2, z, "up")
            if z % 2 == 0:
                p.put(7, 3, z, "minecraft:white_wool")
        for x in (1, 2):
            K.barrel(p, x, 2, 7, "up")
        # the steelyard: a post with a hanging pan
        p.put(4, 2, 5, "fence")
        p.put(4, 3, 5, "minecraft:light_weighted_pressure_plate", {"power": "0"})
        p.titik("kerja:timbangan", 4, 2, 6, "north")
        p.titik("kerja:rak", 5, 2, 3, "north")
        p.entity(3.5, 2.0, 5.5, {"id": "minecraft:cat", "PersistenceRequired": True})
    # hoist beam over the door
    p.put(4, 6, 9, "beam", {"axis": "z"})
    p.put(4, 5, 9, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    p.put(4, 4, 9, "minecraft:hay_block" if gaya != "salju" else "minecraft:barrel", {"axis": "y"} if gaya != "salju" else {"facing": "up", "open": "false"})
    pintu_depan(p, 4, 10, "south")
    p.put(4, 1, 9, st["jalan"])
    return p.simpan_ke()
def kandang(gaya):
    """Livestock pen (sabana: a big cattle kraal): fences, a gate, a straw shelter, a water trough, hay; chickens, pigs,
    cows or sheep as template entities."""
    besar = gaya == "sabana"
    w, d = (15, 14) if besar else (11, 11)
    st = S[gaya]
    p = Potongan(gaya, "kandang", w, 6, d + 1)
    for x in range(w):
        for z in range(d):
            p.put(x, 0, z, "minecraft:coarse_dirt" if (x * z) % 5 == 0 else ("minecraft:podzol" if gaya != "gurun" else "minecraft:sand"))
            if x in (0, w - 1) or z in (0, d - 1):
                p.put(x, 1, z, "minecraft:%s_fence" % st["kayu"])
    p.put(w // 2, 1, d - 1, "minecraft:%s_fence_gate" % st["kayu"], {"facing": "south", "in_wall": "false", "open": "false", "powered": "false"})
    for x in range(1, 5):
        for z in range(1, 4):
            p.put(x, 3, z, "minecraft:hay_block", {"axis": "x"})
    for (x, z) in ((1, 1), (4, 1), (1, 3), (4, 3)):
        p.put(x, 1, z, "log", {"axis": "y"})
        p.put(x, 2, z, "log", {"axis": "y"})
    for x in range(w - 4, w - 1):
        p.put(x, 1, 1, "minecraft:water_cauldron", {"level": "3"})
    p.put(2, 1, 2, "minecraft:hay_block", {"axis": "y"})
    # a feed trough (composters full of fodder) and a hen coop with straw nests
    for z in range(3, 6):
        p.put(w - 2, 1, z, "minecraft:composter", {"level": "6"})
    cx0 = 1
    for x in range(cx0, cx0 + 3):
        p.put(x, 1, d - 3, "minecraft:%s_planks" % st["kayu"])
        p.slab(x, 2, d - 3, "wood_slab", "top")
        p.put(x, 3, d - 3, "minecraft:hay_block", {"axis": "x"})
    p.put(cx0 + 1, 1, d - 4, "minecraft:hay_block", {"axis": "y"})
    p.standing_lantern(w // 2 + 1, 1, d - 1, height=1)
    animals = (("cow", "cow", "sheep", "sheep", "cow") if besar else
               {"salju": ("sheep", "sheep", "chicken"), "gurun": ("chicken", "chicken", "pig"),
                "rawa": ("pig", "pig", "chicken")}.get(gaya, ("chicken", "chicken", "pig", "cow")))
    for i, a in enumerate(animals):
        hewan(p, 3 + (i * 3) % (w - 5), 1, 5 + (i * 2) % (d - 7), a)
    p.titik("kerja:sapu", w // 2, 1, d - 2, "north")
    pintu_depan(p, w // 2, d, "south")
    return p.simpan_ke()


def kolam_ikan(gaya):
    """Fish pond with a jetty (gurun: the fenced oasis pool under palms; sakura: a koi pond with a red bridge; rawa:
    a jetty into open water with a moored boat). Two fishing points."""
    st = S[gaya]
    p = Potongan(gaya, "kolam_ikan", 13, 8, 14)
    cx, cz = 6, 6
    for x in range(13):
        for z in range(13):
            dd = math.hypot((x - cx) / 5.5, (z - cz) / 5.0)
            if dd <= 1.0:
                p.put(x, 0, z, "minecraft:water", {"level": "0"})
                if (x * 3 + z) % 9 == 0:
                    p.put(x, 1, z, "minecraft:lily_pad")
            elif dd <= 1.25:
                p.put(x, 0, z, st["fondasi"] if gaya != "rawa" else "minecraft:mud")
                if gaya == "gurun":
                    p.put(x, 1, z, "minecraft:sandstone_wall")
    for z in range(cz, 13):
        p.put(cx, 0, z, st["lantai"] if gaya != "gurun" else "minecraft:cut_sandstone")
        p.put(cx, 1, z, "minecraft:air")
    if gaya == "sakura":
        for z in range(cz - 1, cz + 2):
            p.stairs(cx, 1, z, "minecraft:mangrove_stairs", "east")
        p.put(cx, 1, cz - 2, "minecraft:red_terracotta")
    if gaya == "gurun":
        for (x, z) in ((1, 1), (11, 2), (2, 11)):
            pohon(p, x, 1, z, "palma", 6)
    elif gaya == "rawa":
        p.entity(cx + 2.5, 1.0, cz + 0.5, {"id": "minecraft:mangrove_boat", "Rotation": [structgen.nbt.Float(90.0), structgen.nbt.Float(0.0)]})
        for z in range(cz - 2, cz + 1):
            p.put(cx - 1, 0, z, "minecraft:mangrove_roots", {"waterlogged": "true"})
    else:
        pohon(p, 11, 1, 11, st["pohon"], 5)
    if gaya != "gurun":
        # a drying line for the catch, bait and fish baskets by the jetty, a lantern post at its foot
        for x in (8, 10):
            for y in (1, 2):
                p.put(x, y, 12, "fence")
        p.put(9, 2, 12, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
        p.put(9, 1, 12, "minecraft:dried_kelp_block")
        K.barrel(p, cx - 1, 1, 11, "up")
        K.barrel(p, cx - 1, 1, 12, "north")
        p.standing_lantern(cx + 1, 1, 12, height=1)
    else:
        # water jars and a shaded rest stone by the oasis pool
        for (x, z) in ((cx - 2, 12), (cx + 2, 12)):
            p.put(x, 1, z, "minecraft:decorated_pot", {"cracked": "false", "facing": "north", "waterlogged": "false"})
        p.stairs(cx + 3, 1, 11, "minecraft:sandstone_stairs", "east")
        p.titik("kursi", cx + 3, 1, 11, "west")
    p.titik("kerja:pancing", cx, 1, cz + 1, "north")
    p.titik("kerja:pancing", cx, 1, cz + 3, "west")
    pintu_depan(p, cx, 13, "south")
    return p.simpan_ke()


def kincir_air(gaya):
    """A water mill: a channel with flowing water, a static wheel of stripped logs (its turning is a client model), a
    millhouse with the grindstone."""
    st = S[gaya]
    p = Potongan(gaya, "kincir_air", 15, 13, 14, geser=(0, 0))
    for z in range(13):
        for x in range(13):
            p.put(x, 0, z, "kerb")
        p.put(2, 0, z, "minecraft:water", {"level": "0"})
        p.put(3, 0, z, "minecraft:water", {"level": "0"})
    # wheel in the x = 4 plane, radius 4, centred on z 6, y 4
    for a in range(0, 360, 15):
        r = math.radians(a)
        y = 4 + int(round(math.sin(r) * 4))
        z = 6 + int(round(math.cos(r) * 4))
        if 1 <= y:
            p.put(4, y, z, "beam", {"axis": "x"})
    for dz in range(-3, 4):
        p.put(4, 4, 6 + dz, "beam", {"axis": "z"})
    for dy in range(-3, 4):
        if 4 + dy >= 1:
            p.put(4, 4 + dy, 6, "beam", {"axis": "y"})
    p.put(5, 4, 6, "log", {"axis": "x"})
    # millhouse
    p.walls(6, 1, 2, 12, 5, 10, role="wall", pillar="log", windows=3)
    for dy in (1, 2):
        p.put(9, dy, 10, "minecraft:air")
    atap(p, 6, 6, 2, 12, 10, along="z")
    # the axle runs from the wheel through the wall to the gear shaft over the millstones
    for x in (6, 7, 8):
        p.put(x, 4, 6, "beam", {"axis": "x"})
    for y in (3, 4):
        p.put(9, y, 6, "log", {"axis": "y"})
    p.put(9, 5, 6, "minecraft:%s_planks" % st["kayu"])
    p.put(9, 1, 6, "minecraft:smooth_stone")
    p.slab(9, 2, 6, "minecraft:smooth_stone_slab", "bottom")
    p.put(9, 1, 4, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    # grain in, flour out: barrels of grain, sacks of flour (white wool), a sieve table
    K.barrel(p, 7, 1, 3, "up")
    K.barrel(p, 7, 2, 3, "up")
    K.barrel(p, 11, 1, 3, "up")
    for (x, y, z) in ((11, 1, 8), (11, 2, 8), (10, 1, 8), (11, 1, 7)):
        p.put(x, y, z, "minecraft:white_wool")
    p.put(7, 1, 8, "minecraft:hay_block", {"axis": "y"})
    p.put(7, 1, 7, "minecraft:hay_block", {"axis": "z"})
    K.low_table(p, 7, 1, 5)
    p.put(7, 2, 5, "minecraft:flower_pot")
    p.titik("kerja:lumpang", 9, 1, 7, "north")
    p.titik("kerja:rak", 8, 1, 4, "west")
    pintu_depan(p, 9, 13, "south")
    p.put(9, 0, 11, st["jalan"])
    p.put(9, 0, 12, st["jalan"])
    return p.simpan_ke()


def pasar_desa(gaya):
    """Village market (desa, desa_besar): four stalls with awnings around a square, a shade tree, the market anchor;
    four seller points (lapak)."""
    p = Potongan(gaya, "pasar_desa", 15, 9, 16)
    st = S[gaya]
    for x in range(15):
        for z in range(15):
            p.put(x, 0, z, st["jalan"] if (x + z) % 4 else st["tepi"] if st["tepi"] not in ("minecraft:packed_ice", "minecraft:mangrove_slab") else st["jalan"])
    goods = ("minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block")
    spots = [K.stall(p, 4, 3, "south", y=0, w=4, cloth=("minecraft:red_wool", "minecraft:white_wool"), goods=goods),
             K.stall(p, 13, 3, "south", y=0, w=4, cloth=("minecraft:yellow_wool", "minecraft:white_wool"), goods=goods)]
    spots2 = [K.stall(p, 1, 11, "north", y=0, w=4, cloth=("minecraft:green_wool", "minecraft:white_wool"), goods=goods),
              K.stall(p, 10, 11, "north", y=0, w=4, cloth=("minecraft:blue_wool", "minecraft:white_wool"), goods=goods)]
    for (sx_, sz_) in spots:
        p.titik("lapak", sx_, 1, sz_, "south")
    for (sx_, sz_) in spots2:
        p.titik("lapak", sx_, 1, sz_, "north")
    pohon(p, 7, 1, 7, st["pohon"] if st["pohon"] != "mangrove" else "oak", 4)
    p.titik("obrol", 5, 1, 7, "east")
    p.titik("obrol", 9, 1, 7, "west")
    p.jangkar(7.5, 1, 9.5, "kampung/pasar")
    pintu_depan(p, 7, 15, "south")
    return p.simpan_ke()


def rumah_kerja(gaya, nama):
    """A craft house: tabib (healer: herb pots, brewing stand, a patient's bed, anchor kampung/tabib), pandai (smith:
    open forge with anvil, smithing table, blast furnace, grindstone, water quench, anchor kampung/pandai), balai (village
    hall: benches for twelve, the headman's dais, anchor kampung/balai), sekolah (village school: low desks, a teacher's
    lectern, a play yard)."""
    st = S[gaya]
    w, d = {"tabib": (9, 9), "pandai": (11, 9), "balai": (15, 11), "sekolah": (13, 9)}[nama]
    pg = st["panggung"]
    p = Potongan(gaya, {"tabib": "rumah_tabib", "pandai": "bengkel_pandai", "balai": "balai_desa", "sekolah": "sekolah_desa"}[nama],
                 w + 2, pg + 7 + w // 2 + 3, d + 4 + pg)
    x0, z0, x1, z1 = 1, 1, w, d
    f = pg
    if pg:
        for x in range(x0, x1 + 1, 3):
            for z in (z0, z1):
                for y in range(pg):
                    p.put(x, y, z, "log", {"axis": "y"})
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, f, z, "floor" if not (x in (x0, x1) or z in (z0, z1)) else "kerb")
    open_front = nama == "pandai"
    p.walls(x0, f + 1, z0, x1, f + 4, z1, role="wall", pillar="log", windows=3 if not open_front else 0)
    K.beam_band(p, x0, z0, x1, z1, f + 4)
    cx = (x0 + x1) // 2
    if open_front:
        for x in range(x0 + 1, x1):
            for dy in (1, 2, 3):
                p.put(x, f + dy, z1, "minecraft:air")
    else:
        K.door(p, cx, f + 1, z1, "south")
    atap(p, x0, f + 5, z0, x1, z1, along="x")
    ix0, iz0, ix1, iz1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
    cz_mid = (iz0 + iz1) // 2 + 1
    if nama == "tabib":
        # back wall: the medicine stove (brewing stand, a simmering cauldron), herb shelves with potted herbs
        p.put(ix0, f + 1, iz0, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
        p.put(ix0 + 1, f + 1, iz0, "minecraft:water_cauldron", {"level": "2"})
        for x in range(ix0 + 2, ix1 + 1):
            p.put(x, f + 1, iz0, "minecraft:%s_shelf" % st["kayu"], {"facing": "south", "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})
            p.put(x, f + 2, iz0, ("minecraft:potted_fern", "minecraft:potted_allium", "minecraft:potted_azure_bluet", "minecraft:potted_dandelion")[x % 4])
        # drying loft under the roof: boards with bundles of herbs and roots laid out to dry
        for x in range(ix0, ix1 + 1):
            for z in (iz0, iz0 + 1):
                p.slab(x, f + 4, z, "wood_slab", "top")
                if (x + z) % 2 == 0:
                    kind = (x // 2 + z) % 4
                    p.put(x, f + 5, z, ("minecraft:flowering_azalea", "minecraft:dried_kelp_block", "minecraft:azalea", "minecraft:hay_block")[kind],
                          {"axis": "y"} if kind == 3 else None)
        # west wall: the medicine chest of drawers (barrels two high) and the healer's strongbox
        for z in range(iz0 + 2, iz0 + 5):
            K.barrel(p, ix0, f + 1, z, "east")
            K.barrel(p, ix0, f + 2, z, "east")
        p.put(ix0, f + 3, iz0 + 3, "minecraft:decorated_pot", {"cracked": "false", "facing": "east", "waterlogged": "false"})
        K.chest(p, ix0, f + 1, iz1 - 1, None, "east")
        p.put(ix0, f + 1, iz1, "minecraft:grindstone", {"face": "floor", "facing": "east"})
        # consultation: a low table with a candle, the healer's and the patient's cushions
        K.low_table(p, cx, f + 1, cz_mid)
        p.put(cx, f + 2, cz_mid, "minecraft:white_candle", {"candles": "1", "lit": "true", "waterlogged": "false"})
        for dx, hd in ((-1, "east"), (1, "west")):
            p.put(cx + dx, f + 1, cz_mid, "minecraft:green_carpet" if dx < 0 else "minecraft:white_carpet")
            p.titik("kursi_lantai", cx + dx, f + 1, cz_mid, hd)
        # the patient's bed behind a paper screen (fence posts with paper = white carpet on top)
        K.bed(p, ix1, f + 1, iz1, "south")
        p.titik("kasur", ix1, f + 1, iz1, "north")
        for z in (iz1 - 1, iz1):
            p.put(ix1 - 1, f + 1, z, "fence")
            p.put(ix1 - 1, f + 2, z, "minecraft:white_carpet")
        p.put(ix1, f + 3, iz1, "minecraft:%s_trapdoor" % st["kayu"], {"facing": "north", "half": "top", "open": "false",
                                                                   "powered": "false", "waterlogged": "false"})
        p.titik("kerja:lumpang", ix0 + 1, f + 1, iz0 + 1, "north")
        villager(p, cx, f + 1, iz0 + 2, "minecraft:cleric")
        p.jangkar(cx + 0.5, f + 1, iz1 - 0.5, "kampung/tabib")
        # the green medicine flag beside the door, herb pots on the doorstep
        p.put(cx + 2, f + 3, z1 + 1, "minecraft:green_wall_banner", {"facing": "south"})
        if not pg:
            for x in (x0 + 1, x0 + 2, x1 - 2, x1 - 1):
                p.put(x, 1, z1 + 1, ("minecraft:potted_oxeye_daisy", "minecraft:potted_fern", "minecraft:potted_cornflower",
                                     "minecraft:potted_azalea_bush")[x % 4])
    elif nama == "pandai":
        # the forge: a brick hearth with glowing coals, a hood and a chimney through the roof, the bellows
        for x in (ix0, ix0 + 1, ix0 + 2):
            for y in (f + 1, f + 2, f + 3):
                p.put(x, y, iz0, "minecraft:bricks")
            p.put(x, f + 3, iz0 + 1, "minecraft:brick_slab", {"type": "top", "waterlogged": "false"})
        p.put(ix0, f + 1, iz0 + 1, "minecraft:bricks")
        p.put(ix0 + 2, f + 1, iz0 + 1, "minecraft:bricks")
        p.put(ix0 + 1, f + 1, iz0 + 1, "minecraft:magma_block")     # glowing coals (no open fire outside kitchens, cek_tempat)
        for y in range(f + 4, f + 10):
            p.put(ix0 + 1, y, iz0, "minecraft:bricks")
        p.put(ix0 + 1, f + 10, iz0, "minecraft:brick_wall")
        K.barrel(p, ix0 + 3, f + 1, iz0 + 1, "west")
        p.put(ix0 + 3, f + 1, iz0, "minecraft:blast_furnace", {"facing": "south", "lit": "true"})
        p.put(ix0 + 4, f + 1, iz0, "minecraft:smithing_table")
        p.put(ix0 + 5, f + 1, iz0, "minecraft:crafting_table")
        # the anvil in the middle, the quench troughs, the grindstone
        p.put(ix0 + 2, f + 1, iz0 + 3, "minecraft:anvil", {"facing": "east"})
        p.put(ix1, f + 1, iz0, "minecraft:grindstone", {"face": "floor", "facing": "west"})
        p.put(ix1, f + 1, iz0 + 2, "minecraft:water_cauldron", {"level": "3"})
        p.put(ix1, f + 1, iz0 + 3, "minecraft:water_cauldron", {"level": "2"})
        # tool shelves on the west wall over the iron stock, a heap of charcoal
        for z in range(iz0 + 3, iz1):
            p.put(ix0, f + 1, z, "minecraft:iron_block" if z == iz0 + 4 else "minecraft:%s_planks" % st["kayu"])
            p.put(ix0, f + 2, z, "minecraft:%s_shelf" % st["kayu"], {"facing": "east", "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})
        p.put(ix0, f + 1, iz1, "minecraft:coal_block")
        p.put(ix0 + 1, f + 1, iz1, "minecraft:coal_block")
        p.put(ix0, f + 2, iz1, "minecraft:black_carpet")
        K.chest(p, ix1, f + 1, iz1, None, "west")
        p.put(ix1 - 1, f + 1, iz1, "minecraft:%s_slab" % st["kayu"], {"type": "top", "waterlogged": "false"})
        p.put(ix1 - 1, f + 2, iz1, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
        p.titik("kerja:palu", ix0 + 3, f + 1, iz0 + 3, "west")
        p.titik("kerja:tungku", ix0 + 1, f + 1, iz0 + 2, "north")
        villager(p, cx + 1, f + 1, iz0 + 4, "minecraft:weaponsmith")
        p.jangkar(cx + 0.5, f + 1, iz1 + 0.5, "kampung/pandai")
        if p.inside(x1 + 1, f + 3, iz1):
            p.put(x1 + 1, f + 3, iz1, "minecraft:gray_wall_banner", {"facing": "east"})
    elif nama == "balai":
        # the headman's dais (two deep) with his chair, a writing desk, the village records on shelves
        for x in range(ix0 + 1, ix1):
            for z in (iz0, iz0 + 1):
                p.put(x, f + 1, z, "kerb")
        p.stairs(cx, f + 2, iz0, "wood_stairs", "north")
        p.titik("kursi", cx, f + 2, iz0, "south")
        K.low_table(p, cx, f + 2, iz0 + 1)
        p.put(cx - 1, f + 2, iz0 + 1, "minecraft:white_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
        p.put(cx + 1, f + 2, iz0 + 1, "minecraft:flower_pot")
        for dx in (-3, 3):
            p.stairs(cx + dx, f + 2, iz0, "wood_stairs", "north")
        for x in (ix0, ix1):
            for y in (f + 1, f + 2, f + 3):
                p.put(x, y, iz0, "minecraft:bookshelf" if y < f + 3 else "minecraft:%s_planks" % st["kayu"])
            K.barrel(p, x, f + 1, iz0 + 1, "east" if x == ix0 else "west")
        # benches in two banks with an aisle and a red runner to the dais
        for z in range(iz0 + 3, iz1, 2):
            for x in list(range(ix0 + 1, cx)) + list(range(cx + 1, ix1)):
                p.stairs(x, f + 1, z, "wood_stairs", "south")
                if (x - ix0) % 2 == 1 and len(p.titik_list) < 26:
                    p.titik("kursi", x, f + 1, z, "north")
        for z in range(iz0 + 2, iz1 + 1):
            p.put(cx, f + 1, z, "minecraft:red_carpet")
        p.put(cx - 2, f + 3, iz0, "minecraft:red_wall_banner", {"facing": "south"})
        p.put(cx + 2, f + 3, iz0, "minecraft:red_wall_banner", {"facing": "south"})
        # the village drum that calls meetings, standing lanterns at the back
        K.drum(p, ix1, f + 1, iz1 - 1, "west")
        p.standing_lantern(ix0, f + 1, iz1, height=1)
        p.standing_lantern(ix1, f + 1, iz1, height=1)
        p.jangkar(cx + 0.5, f + 1, iz0 + 2.5, "kampung/balai")
    else:
        # the teacher's lectern before a blackboard framed in wood; reading primers on the shelves
        for x in range(cx - 2, cx + 3):
            for y in (f + 2, f + 3):
                p.put(x, y, z0, "minecraft:black_concrete")
            p.put(x, f + 4, z0, "beam", {"axis": "x"})
        for x in (cx - 3, cx + 3):
            for y in (f + 2, f + 3):
                p.put(x, y, z0, "log", {"axis": "y"})
        p.put(cx, f + 1, iz0, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
        p.titik("meja_tulis", cx, f + 1, iz0 + 1, "south")
        for z in (iz0, iz0 + 1):
            for y in (f + 1, f + 2):
                p.put(ix0, y, z, "minecraft:bookshelf")
        K.chest(p, ix1, f + 1, iz0, None, "west")
        p.put(ix1, f + 1, iz0 + 1, "minecraft:%s_slab" % st["kayu"], {"type": "top", "waterlogged": "false"})
        p.put(ix1, f + 2, iz0 + 1, "minecraft:white_candle", {"candles": "3", "lit": "false", "waterlogged": "false"})
        for z in range(iz0 + 3, iz1, 2):
            for x in range(ix0, ix1 + 1, 2):
                K.low_table(p, x, f + 1, z)
                p.put(x, f + 1, z + 1, "minecraft:white_carpet")
                p.titik("kursi_lantai", x, f + 1, z + 1, "north")
        p.put(ix1 - 1, f + 1, iz1, "minecraft:potted_bamboo")
        p.put(ix0 + 1, f + 1, iz1, "minecraft:potted_cactus" if gaya == "gurun" else "minecraft:potted_spruce_sapling")
        # the play yard: a hopscotch court chalked on the ground
        if not pg:
            for i, (x, z) in enumerate(((cx - 3, z1 + 2), (cx - 3, z1 + 3), (cx - 2, z1 + 2), (cx - 2, z1 + 3))):
                if p.inside(x, 1, z):
                    p.put(x, 0, z, st["jalan"])
                    p.put(x, 1, z, "minecraft:white_carpet" if i % 2 == 0 else "minecraft:yellow_carpet")
        p.titik("main", cx - 2, 1, z1 + 2, "north")
        p.titik("main", cx + 2, 1, z1 + 2, "north")
    p.lantern(cx, f + 5, (iz0 + iz1) // 2 + 1, chain=1)
    if pg:
        for i in range(pg):
            p.stairs(cx, pg - i, z1 + 1 + i, "wood_stairs", "north")
    pintu_depan(p, cx, d + 3 + pg, "south")
    return p.simpan_ke()


def makam_desa(gaya):
    """Village graveyard: cobblestone-wall headstones in rows with candles, an incense stone, a lantern gate, an old
    tree."""
    st = S[gaya]
    p = Potongan(gaya, "makam_desa", 11, 8, 12)
    for x in range(11):
        for z in range(11):
            p.put(x, 0, z, "minecraft:podzol" if gaya not in ("gurun",) else "minecraft:sand")
            if x in (0, 10) or z in (0, 10):
                p.put(x, 1, z, "minecraft:cobblestone_wall" if gaya != "gurun" else "minecraft:sandstone_wall")
    for dy in (1, 2):
        p.put(5, dy, 10, "minecraft:air")
    for x in range(2, 9, 2):
        for z in (2, 5):
            p.put(x, 1, z, "minecraft:mossy_cobblestone_wall" if gaya != "gurun" else "minecraft:sandstone_wall")
            p.put(x, 1, z + 1, "minecraft:white_candle", {"candles": "1", "lit": "true", "waterlogged": "false"})
    p.put(5, 1, 8, "minecraft:polished_andesite")
    p.titik("dupa", 5, 1, 9, "north")
    pohon(p, 9, 1, 8, st["pohon"] if st["pohon"] not in ("palma",) else "palma", 5)
    pintu_depan(p, 5, 11, "south")
    return p.simpan_ke()


def menara_pengawas(gaya):
    """Watchtower 5 x 14 x 5: log frame, ladder, a roofed platform with a lantern, a lookout point."""
    st = S[gaya]
    p = Potongan(gaya, "menara_pengawas", 7, 23, 9, geser=(1, 1))
    for (x, z) in ((0, 0), (4, 0), (0, 4), (4, 4)):
        for y in range(0, 13):
            p.put(x, y, z, "log", {"axis": "y"})
    for y in (4, 8):
        for x in range(5):
            for z in (0, 4):
                p.put(x, y, z, "beam", {"axis": "x"})
    K.ladder(p, 2, 1, 12, 1, "south")
    for x in range(5):
        for z in range(5):
            if (x, z) != (2, 1):
                p.put(x, 12, z, "wood")
            if x in (0, 4) or z in (0, 4):
                p.put(x, 13, z, "fence")
    for (x, z) in ((0, 0), (4, 0), (0, 4), (4, 4)):
        for y in (14, 15):
            p.put(x, y, z, "fence")
    atap(p, 0, 16, 0, 4, 4, along="x")
    p.lantern(2, 16, 2, chain=1)
    p.titik("jaga", 2, 13, 3, "south")
    pintu_depan(p, 2, 6, "south")
    return p.simpan_ke()


# ----------------------------------------------------------------------------------------------- the square and streets
def pusat(gaya):
    """Village square 17 x 17 (start piece): packed ground, the village bell under a little roof (meeting point of the
    villagers; Desa Bawahan pledges there), a stone well with a chain, the Earth God shrine 5 x 5 (andesite altar, a
    statue, candles), a notice board, benches, lanterns, a shade tree. Anchors kampung/situs, kampung/lonceng,
    kampung/tetua (the headman). Four street connectors."""
    st = S[gaya]
    p = Potongan(gaya, "pusat", 19, 14, 19)
    for x in range(19):
        for z in range(19):
            p.put(x, 0, z, st["jalan"] if (x * 3 + z * 7) % 5 else (st["fondasi"] if gaya != "rawa" else "minecraft:mangrove_planks"))
            p.put(x, 1, z, "minecraft:air")
    if gaya == "rawa":
        for x in range(0, 19, 6):
            for z in range(0, 19, 6):
                p.put(x, 0, z, "minecraft:mangrove_log", {"axis": "y"})
    # bell under a roof
    for (x, z) in ((7, 7), (11, 7)):
        for y in range(1, 5):
            p.put(x, y, z, "log", {"axis": "y"})
    for x in range(7, 12):
        p.put(x, 5, 7, "beam", {"axis": "x"})
    p.roof_gable(6, 6, 6, 12, 8, along="x", overhang=0, eave_curl=True)
    p.put(9, 4, 7, "minecraft:bell", {"attachment": "ceiling", "facing": "south", "powered": "false"})
    p.jangkar(9.5, 1, 8.5, "kampung/lonceng", protect=48)
    # well
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                p.put(4 + dx, 1, 13 + dz, "minecraft:cobblestone_wall" if gaya != "gurun" else "minecraft:sandstone_wall")
            else:
                p.put(4, 0, 13, "minecraft:water", {"level": "0"})
                p.put(4, 1, 13, "minecraft:air")
    p.put(3, 2, 12, "fence")
    p.put(5, 2, 14, "fence")
    p.put(3, 3, 12, "fence")
    p.put(5, 3, 14, "fence")
    for x in (3, 4, 5):
        p.put(x, 4, 13, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    p.put(4, 3, 13, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    p.titik("obrol", 6, 1, 13, "west")
    # Earth God shrine 5 x 5 at the north-east
    for x in range(12, 17):
        for z in range(11, 16):
            p.put(x, 0, z, "minecraft:polished_andesite")
    for (x, z) in ((12, 11), (16, 11), (12, 15), (16, 15)):
        for y in (1, 2, 3):
            p.put(x, y, z, "log", {"axis": "y"})
    p.roof_gable(12, 4, 11, 16, 15, along="x", overhang=1, eave_curl=True)
    for x in range(13, 16):
        p.put(x, 1, 12, "minecraft:polished_andesite")
    p.put(14, 2, 12, "minecraft:stone_bricks")
    p.put(14, 3, 12, "minecraft:chiseled_stone_bricks")
    p.put(13, 2, 12, "minecraft:red_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    p.put(15, 2, 12, "minecraft:red_candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    p.put(14, 1, 14, "minecraft:cauldron")
    p.titik("dupa", 14, 1, 15, "north")
    # notice board, benches, lanterns, tree
    K.notice_board(p, 5, 3, "south", y=0, w=4)
    for (x, z, face) in ((8, 13, "north"), (8, 16, "north")):
        K.bench(p, x, z, face, y=0, n=3)
        p.titik("kursi", x + 1, 1, z, "north")
    for (x, z) in ((1, 1), (17, 1), (1, 17), (17, 17)):
        K.post(p, x, 1, z, height=2, post_role="fence")
    pohon(p, 15, 1, 3, st["pohon"], 5)
    p.kotak("panggung", 7, 1, 9, 11, 1, 12, "south", acara="festival_panen")
    for (x, z) in ((6, 10), (12, 10), (9, 14)):
        p.titik("main", x, 1, z, "north")
    p.titik("tiba", 9, 1, 10, "south")
    p.jangkar(9.5, 1, 11.5, "kampung/situs", protect=64)
    p.jangkar(10.5, 1, 9.5, "kampung/tetua", yaw=180.0)
    for (x, z, facing) in ((9, 0, "north"), (18, 9, "east"), (9, 18, "south"), (0, 9, "west")):
        p.jigsaw_side(x, 0, z, facing, name=J_JALAN, target=J_JALAN, pool=NS + "kampung/%s/jalan" % gaya, final_state=st["jalan"])
    villager(p, 9, 1, 11)
    return p.simpan_ke()


def _jalan_base(p, length, w=3, along="z"):
    st = p.st
    for i in range(length):
        for j in range(w):
            x, z = (j, i) if along == "z" else (i, j)
            p.put(x, 0, z, st["jalan"] if (i * 3 + j) % 7 else st["tepi"] if not st["tepi"].endswith("_slab") else st["jalan"])


def _ujung(p, x, z, facing):
    p.jigsaw_side(x, 0, z, facing, name=J_JALAN, target=J_JALAN, pool=NS + "kampung/%s/jalan" % p.gaya, final_state=p.st["jalan"])


def _sisi(p, x, z, facing):
    p.jigsaw_side(x, 0, z, facing, name=J_SISI, target=J_PINTU, pool=NS + "kampung/%s/bangunan" % p.gaya, final_state=p.st["jalan"])


def jalan(gaya):
    """The street pieces (terrain_matching): straight 12 and 18 with house connectors on both sides, a crossroads, a
    bend, a little bridge over a ditch, and the dead end terminator (haystack, lantern post)."""
    out = {}
    for L, sides in ((12, (3, 8)), (18, (3, 9, 14))):
        p = Potongan(gaya, "jalan_lurus_%d" % L, 3, 4, L)
        _jalan_base(p, L)
        _ujung(p, 1, 0, "north")
        _ujung(p, 1, L - 1, "south")
        for z in sides:
            _sisi(p, 0, z, "west")
            _sisi(p, 2, z, "east")
        K.post(p, 2, 1, L // 2, height=2, post_role="fence") if gaya != "rawa" else None
        p.titik("jalan", 1, 1, 1, "south", {"urut": 0, "jalur": "jalan"})
        p.titik("jalan", 1, 1, L - 2, "south", {"urut": 1, "jalur": "jalan"})
        out["jalan_lurus_%d" % L] = p.simpan_ke()
    p = Potongan(gaya, "jalan_simpang", 7, 4, 7)
    for x in range(7):
        for z in range(7):
            if 2 <= x <= 4 or 2 <= z <= 4:
                p.put(x, 0, z, p.st["jalan"])
    _ujung(p, 3, 0, "north")
    _ujung(p, 6, 3, "east")
    _ujung(p, 3, 6, "south")
    _ujung(p, 0, 3, "west")
    K.post(p, 1, 1, 1, height=2, post_role="fence")
    p.titik("obrol", 5, 1, 5, "north")
    out["jalan_simpang"] = p.simpan_ke()
    p = Potongan(gaya, "jalan_belok", 5, 3, 5)
    for x in range(5):
        for z in range(5):
            if (1 <= x <= 3 and z <= 3) or (1 <= z <= 3 and x >= 1):
                p.put(x, 0, z, p.st["jalan"])
    _ujung(p, 2, 0, "north")
    _ujung(p, 4, 2, "east")
    out["jalan_belok"] = p.simpan_ke()
    p = Potongan(gaya, "jalan_jembatan", 5, 4, 11)
    for z in range(11):
        for x in range(5):
            if 3 <= z <= 7:
                p.put(x, 0, z, "minecraft:water", {"level": "0"} ) if 1 <= x <= 3 else p.put(x, 0, z, p.st["fondasi"])
            else:
                p.put(x, 0, z, p.st["jalan"] if 1 <= x <= 3 else p.st["tepi"] if not p.st["tepi"].endswith("_slab") else p.st["jalan"])
    for z in range(2, 9):
        for x in range(1, 4):
            p.slab(x, 1, z, "wood_slab", "bottom")
        p.put(0, 1, z, "fence")
        p.put(4, 1, z, "fence")
    _ujung(p, 2, 0, "north")
    _ujung(p, 2, 10, "south")
    out["jalan_jembatan"] = p.simpan_ke()
    p = Potongan(gaya, "ujung", 3, 4, 3)
    for x in range(3):
        for z in range(3):
            p.put(x, 0, z, p.st["jalan"])
    p.put(1, 1, 1, "minecraft:hay_block", {"axis": "y"})
    K.post(p, 2, 1, 2, height=2, post_role="fence")
    p.jigsaw_side(1, 0, 0, "north", name=J_JALAN, target=J_JALAN, pool="minecraft:empty", final_state=p.st["jalan"])
    out["ujung"] = p.simpan_ke()
    return out


def build_gaya(gaya):
    PIECES[gaya]["pusat"] = pusat(gaya)
    PIECES[gaya].update(jalan(gaya))
    for u in ("kecil", "sedang", "besar"):
        rumah(gaya, u)
    rumah_panjang(gaya)
    warung_teh(gaya)
    ladang(gaya)
    sawah(gaya)
    lumbung(gaya)
    kandang(gaya)
    kolam_ikan(gaya)
    kincir_air(gaya)
    pasar_desa(gaya)
    for n in ("tabib", "pandai", "balai", "sekolah"):
        rumah_kerja(gaya, n)
    makam_desa(gaya)
    menara_pengawas(gaya)


def bangunan_bobot(gaya, ukuran):
    """Weights of the building pool of one size."""
    lumb = "gudang_ikan" if gaya == "salju" else "lumbung"
    w = {"rumah_kecil": 6, "rumah_sedang": 3, "rumah_panjang": 1, "ladang": 4, "kandang": 2, "kolam_ikan": 1, lumb: 1,
         "makam_desa": 1}
    if ukuran in ("desa", "desa_besar"):
        w.update({"rumah_besar": 2, "rumah_panjang": 2, "sawah": 3, "kincir_air": 1, "pasar_desa": 1, "rumah_tabib": 1,
                  "bengkel_pandai": 1, "menara_pengawas": 1, "warung_teh": 1})
    if ukuran == "desa_besar":
        w.update({"rumah_sedang": 5, "rumah_besar": 3, "rumah_panjang": 3, "balai_desa": 1, "sekolah_desa": 1,
                  "pasar_desa": 2, "warung_teh": 2})
    return w


# ===================================================================================================== the clan village
KLAN_PAL = dict(K.PAL)
KLAN_PAL.update({
    "plaster": "minecraft:white_terracotta", "wall": "minecraft:bricks", "kerb": "minecraft:stone_bricks",
    "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:mud_bricks", "paving": "minecraft:polished_andesite",
    "paving_alt": "minecraft:mud_bricks", "path": "minecraft:stone_bricks", "path_alt": "minecraft:cracked_stone_bricks",
    "wood": "minecraft:spruce_planks", "wood_stairs": "minecraft:spruce_stairs", "wood_slab": "minecraft:spruce_slab",
    "wood_trapdoor": "minecraft:spruce_trapdoor", "wood_fence": "minecraft:spruce_fence", "wood_gate": "minecraft:spruce_fence_gate",
    "door": "minecraft:spruce_door", "log": "minecraft:spruce_log", "beam": "minecraft:stripped_spruce_log",
    "shelf": "minecraft:spruce_shelf", "rail": "minecraft:spruce_fence", "red": "minecraft:red_terracotta",
    "roof": "minecraft:deepslate_tile_stairs", "roof_slab": "minecraft:deepslate_tile_slab", "roof_full": "minecraft:deepslate_tiles",
    "tile": "minecraft:deepslate_tiles", "tile_stairs": "minecraft:deepslate_tile_stairs", "tile_slab": "minecraft:deepslate_tile_slab",
    "trunk": "minecraft:spruce_log", "leaves": "minecraft:spruce_leaves",
})
KL = K.LY


def _stand(k, x, y, z, yaw, item, small=False):
    """A locked display / dummy stand (armour stand with arms) holding `item`."""
    e = {"id": "minecraft:armor_stand", "Invulnerable": True, "DisabledSlots": 4144959, "ShowArms": True,
         "PersistenceRequired": True, "Rotation": [structgen.nbt.Float(yaw), structgen.nbt.Float(0.0)],
         "equipment": {"mainhand": {"id": "minecraft:" + item, "count": 1}}}
    if small:
        e["Small"] = True
    k.entity(x, y, z, e)


def _rak(k, x, z, y, facing, n=4):
    """A weapon rack against a frame of posts: stands with swords, an axe and a bow (entities, locked)."""
    dx, dz = FACE[facing]
    rx, rz = -dz, dx
    yaw = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}[facing]
    for i in range(n):
        px, pz = x + rx * i, z + rz * i
        k.put(px - dx, y + 1, pz - dz, "log", {"axis": "y"})
        k.put(px - dx, y + 2, pz - dz, "log", {"axis": "y"})
        k.slab(px - dx, y + 3, pz - dz, "wood_slab", "bottom")
        if i % 2 == 0:
            _stand(k, px + 0.5, y + 1, pz + 0.5, yaw, ("iron_sword", "stone_sword", "iron_axe", "bow")[(i // 2) % 4])


def klan_tembok(k):
    """The clan wall: brick 6 high on a stone plinth, plaster band, tile coping, corner towers, lantern posts."""
    for x in range(144):
        for z in range(144):
            if not (x <= 2 or x >= 141 or z <= 2 or z >= 141):
                continue
            if z >= 141 and 63 <= x <= 80:
                continue
            for y in range(0, KL + 1):
                k.put(x, y, z, "kerb")
            for dy in range(1, 7):
                k.put(x, KL + dy, z, "kerb" if dy == 1 else ("plaster" if dy == 5 else "wall"))
            k.slab(x, KL + 7, z, "tile_slab")
    for (cx, cz) in ((1, 1), (142, 1), (1, 142), (142, 142)):
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                if 0 <= x < 144 and 0 <= z < 144:
                    for dy in range(1, 11):
                        k.put(x, KL + dy, z, "wall")
        K.hip(k, max(0, cx - 1), KL + 11, max(0, cz - 1), min(143, cx + 1), min(143, cz + 1), overhang=1, top="lantern",
              role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for z in range(12, 140, 16):
        K.post(k, 4, KL + 1, z, height=2)
        K.post(k, 139, KL + 1, z, height=2)


def klan_gerbang(k):
    """Gatehouse 15 x 14 x 11 in the south wall: a roofed gate with lacquered doors standing open, guardian lions, the
    clan's bell, the clan name board (gold); anchors kampung/lonceng (protect 64) and the guards' points."""
    f = KL
    for x in range(64, 80):
        for z in range(130, 141):
            k.put(x, f, z, "paving")
    for x in (64, 79):
        for z in range(130, 141):
            for dy in range(1, 9):
                k.put(x, f + dy, z, "wall" if dy < 7 else "plaster")
    for x in range(64, 80):
        for z in (130, 140):
            k.put(x, f + 8, z, "beam", {"axis": "x"})
    for (x, z) in ((66, 130), (77, 130), (66, 140), (77, 140)):
        for dy in range(1, 8):
            k.put(x, f + dy, z, "red")
    K.roof_over(k, 64, 130, 79, 140, f + 9, kind="hip", tiers=2, overhang=2, top="pearl", tile=True)
    K.plaque(k, 71, f + 7, 141, "x")
    K.plaque(k, 72, f + 7, 141, "x")
    K.lion(k, 62, f, 143, "south")
    K.lion(k, 81, f, 143, "south")
    lonc_x, lonc_z = 71, 124
    for x in (69, 74):
        for dy in range(1, 5):
            k.put(x, f + dy, lonc_z, "red")
    for x in range(69, 75):
        k.put(x, f + 5, lonc_z, "beam", {"axis": "x"})
    k.put(lonc_x, f + 4, lonc_z, "minecraft:bell", {"attachment": "ceiling", "facing": "south", "powered": "false"})
    k.jangkar(lonc_x + 0.5, f + 1, lonc_z + 1.5, "kampung/lonceng", protect=64)
    for (x, z) in ((65, 142), (78, 142), (66, 128), (77, 128)):
        k.titik("jaga", x, f + 1, z, "south")
    # inside the passage: the guards' benches and racks, the alarm drum, stone lanterns, the board of clan edicts
    for x, hd in ((65, "east"), (78, "west")):
        for z in (132, 133):
            K.chair(k, x, f + 1, z, hd)
            k.titik("kursi", x, f + 1, z, hd)
        _rak(k, x, 136 if hd == "east" else 138, f, hd, n=3)
    K.drum(k, 66, f + 1, 125, "south")
    K.stone_lantern(k, 62, KL + 1, 127)
    K.stone_lantern(k, 81, KL + 1, 127)
    K.notice_board(k, 54, 126, "south", y=KL, w=5)
    k.titik("obrol", 52, KL + 1, 128, "north")
    # the clan's colours: red couplets on the front pillars, banner poles beside the lions
    K.wall_banner(k, 66, f + 5, 141, "south", "red")
    K.wall_banner(k, 77, f + 5, 141, "south", "red")
    K.banner_pole(k, 60, KL + 1, 138, height=5, color="red")
    K.banner_pole(k, 83, KL + 1, 138, height=5, color="yellow")


def klan_aula_leluhur(k):
    """Ancestral hall 19 x 16 x 21 (lot (1,0)): on a podium, double eaves, rows of spirit tablets on stepped altars (the
    tablets' names are a client picture), incense, the clan's founding sword on a stand; anchor kampung/situs."""
    a = K.aula(k, 62, 8, 81, 29, h=8, podium_h=2, teras=2, roof="hip", tiers=2, door_sides=("south",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for step in range(3):
        for x in range(ix0 + 1 + step, ix1 - step):
            k.put(x, f + 1 + step, iz0 + step, "kerb")
            if (x + step) % 2 == 0:
                # a spirit tablet: a dark slab board standing on the altar step
                k.put(x, f + 2 + step, iz0 + step, "wood_slab", {"type": "bottom", "waterlogged": "false"})
    # the offering table before the tablets: candles, fruit (melons, a cake for the founder's day), the founding sword
    for x in range(ix0 + 2, ix1 - 1):
        k.put(x, f + 1, iz0 + 3, "wood")
    for x in range(ix0 + 2, ix1 - 1, 2):
        k.put(x, f + 2, iz0 + 3, "minecraft:white_candle", {"candles": "2", "lit": "true", "waterlogged": "false"})
    k.put(71, f + 2, iz0 + 3, "minecraft:cake", {"bites": "0"})
    k.put(69, f + 2, iz0 + 3, "minecraft:melon")
    k.put(73, f + 2, iz0 + 3, "minecraft:melon")
    _stand(k, 71.5, f + 4, iz0 + 2.5, 0.0, "iron_sword", small=True)
    for z in range(iz0 + 5, iz1, 4):
        K.wall_banner(k, ix0, f + 5, z, "east", "red")
        K.wall_banner(k, ix1, f + 5, z, "west", "red")
    for z in (iz0 + 6, iz1 - 3):
        K.lit(k, 66, f + 8, z, chain=1)
        K.lit(k, 76, f + 8, z, chain=1)
    K.rug(k, 70, iz0 + 9, 72, iz1, f + 1)
    K.incense_burner(k, 71, f + 1, iz0 + 6)
    k.titik("dupa", 71, f + 1, iz0 + 8, "north")
    for x in (ix0 + 2, ix1 - 2):
        for z in range(iz0 + 5, iz1, 4):
            k.put(x, f + 1, z, "rug")
            k.titik("kursi_lantai", x, f + 1, z, "east" if x < 71 else "west")
    k.jangkar(71.5, KL + 1, 36.5, "kampung/situs", protect=96, situs="kampung/klan")
    K.incense_burner(k, 71, KL + 1, 40, big=True)


def klan_lapangan(k):
    """Clan training ground 21 x 21 (lot (1,1)): packed earth, wooden dummies, weapon racks, a sparring ring marked in
    stone, a drum; eight drill points; anchor kampung/tuan_muda (the arrogant young master waits here)."""
    x0, z0 = 61, 61
    for x in range(x0, x0 + 21):
        for z in range(z0, z0 + 21):
            k.put(x, KL, z, "minecraft:packed_mud" if (x + z) % 3 else "minecraft:coarse_dirt")
    k.ring(71, KL, 71, 6, "kerb")
    for i, x in enumerate(range(x0 + 2, x0 + 20, 5)):
        k.entity(x + 0.5, KL + 1, z0 + 1.5, {"id": "minecraft:armor_stand", "Invulnerable": True, "DisabledSlots": 4144959,
                                             "ShowArms": True, "PersistenceRequired": True,
                                             "equipment": {"mainhand": {"id": "minecraft:wooden_sword", "count": 1}}})
    for z in range(z0 + 3, z0 + 19, 4):
        k.put(x0, KL + 1, z, "log", {"axis": "y"})
        k.put(x0, KL + 2, z, "minecraft:hay_block", {"axis": "y"})
    K.drum(k, x0 + 20, KL + 1, z0 + 10, "west")
    for x in range(x0 + 3, x0 + 19, 5):
        for z in (z0 + 8, z0 + 14):
            k.titik("latihan", x, KL + 1, z, "north")
    k.jangkar(71.5, KL + 1, 77.5, "kampung/tuan_muda", yaw=180.0)
    # the patriarch's viewing pavilion (north-west): a raised platform, red posts, a tiled roof, his chair and tea
    pf = K.podium(k, 50, 52, 58, 60, KL, 1, steps=("east",), step_w=3)
    for (x, z) in ((51, 53), (57, 53), (51, 59), (57, 59)):
        for dy in range(1, 5):
            k.put(x, pf + dy, z, "red")
    K.roof_over(k, 50, 52, 58, 60, pf + 5, kind="hip", overhang=1, tile=True)
    K.chair(k, 54, pf + 1, 56, "east")
    k.titik("kursi", 54, pf + 1, 56, "east")
    K.low_table(k, 55, pf + 1, 56)
    for z, hd in ((55, "south"), (57, "north")):
        k.put(55, pf + 1, z, "rug")
        k.titik("kursi_lantai", 55, pf + 1, z, hd)
    K.lit(k, 54, pf + 5, 54, chain=1)
    # spectators' benches south of the ground (the clan watches the young master's bouts)
    for x in list(range(61, 67)) + list(range(77, 82)):
        K.chair(k, x, KL + 1, 84, "north")
        if x % 2:
            k.titik("penonton", x, KL + 1, 84, "north")
    # archery butts in the east, weapon racks in the west, lifting stones, the clan's banners at the corners
    for z in (55, 58, 61):
        k.put(88, KL + 1, z, "minecraft:hay_block", {"axis": "y"})
        k.put(88, KL + 2, z, "minecraft:target", {"power": "0"})
        k.titik("latihan", 83, KL + 1, z, "east")
    _rak(k, 59, 77, KL, "east", n=4)
    for z in (78, 80):
        k.put(84, KL + 1, z, "minecraft:polished_andesite")
        k.slab(84, KL + 2, z, "minecraft:polished_andesite_slab", "bottom")
    for (x, z), c in zip(((60, 60), (82, 60), (60, 82), (82, 82)), ("red", "yellow", "yellow", "red")):
        K.banner_pole(k, x, KL + 1, z, height=4, color=c)


def klan_rumah(k, x0, z0, nama, anchor, beds=2):
    """A courtyard house of the clan (patriarch / elders / guests / servants): hall with bedrooms, tea room, ancestor
    shelf, a walled yard with a tree; anchor for the resident."""
    K.yard_wall(k, x0, z0, x0 + 40, z0 + 40, KL, h=3, gate="south", gate_w=3)
    f = K.podium(k, x0 + 4, z0 + 4, x0 + 36, z0 + 18, KL, 1, steps=("south",), step_w=3)
    inner = K.hall(k, x0 + 5, z0 + 5, x0 + 35, z0 + 17, f, 6, door_sides=("south",), door_w=2, windows=4)
    ix0, iz0, ix1, iz1 = inner
    for i in range(beds):
        K.bed(k, ix0 + 1 + i * 3, f + 1, iz0, "north")
        k.titik("kasur", ix0 + 1 + i * 3, f + 1, iz0, "south")
    K.low_table(k, (ix0 + ix1) // 2, f + 1, (iz0 + iz1) // 2)
    for dx in (-1, 1):
        k.put((ix0 + ix1) // 2 + dx, f + 1, (iz0 + iz1) // 2, "rug")
        k.titik("kursi_lantai", (ix0 + ix1) // 2 + dx, f + 1, (iz0 + iz1) // 2, "west" if dx > 0 else "east")
    K.bookshelf_wall(k, ix1, iz0, ix1, iz0 + 3, f + 1, f + 3, face="west")
    K.roof_over(k, x0 + 4, z0 + 4, x0 + 36, z0 + 18, f + 7, kind="hip", overhang=1, tile=True)
    K.garden(k, x0 + 2, z0 + 22, x0 + 38, z0 + 38, KL, trees=("pine",), pond_too=True)
    if anchor:
        k.jangkar(x0 + 20.5, f + 1, iz1 - 1.5, anchor, yaw=180.0)
    _ = nama


def klan_alkimia(k):
    """Clan alchemy pavilion (lot (2,0)): pill cauldrons over fires (the lot name holds 'alkimia'), herb shelves."""
    a = K.aula(k, 100, 8, 136, 30, h=6, podium_h=1, teras=1, roof="hip", tiers=1, door_sides=("south",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    for x in (107, 118, 129):
        k.put(x, f + 1, iz0 + 6, "minecraft:campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        k.put(x, f + 2, iz0 + 6, "minecraft:cauldron")
        k.titik("kerja:tungku", x, f + 1, iz0 + 8, "north")
    for x in range(ix0 + 1, ix1, 2):
        k.put(x, f + 1, iz0, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "true", "has_bottle_2": "false"})
    k.titik("kerja:lumpang", 118, f + 1, iz1 - 2, "north")
    K.garden(k, 100, 34, 138, 44, KL, trees=("cherry",), pond_too=False)


def klan_pustaka_tamu(k):
    """Small library and guest house (lot (0,2))."""
    a = K.aula(k, 6, 100, 40, 116, h=6, podium_h=1, teras=1, roof="gable", door_sides=("east",), door_w=3, tile=True)
    ix0, iz0, ix1, iz1 = a["inner"]
    f = a["f"]
    K.bookshelf_wall(k, ix0, iz0, ix1, iz0, f + 1, f + 4, face="south")
    for x in range(ix0 + 2, ix1 - 1, 4):
        k.put(x, f + 1, iz0 + 4, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
        k.titik("meja_tulis", x, f + 1, iz0 + 5, "north")
    f2 = K.podium(k, 8, 122, 38, 136, KL, 1, steps=("east",), step_w=3)
    inner = K.hall(k, 9, 123, 37, 135, f2, 5, door_sides=("east",), door_w=2, windows=4)
    for i in range(3):
        K.bed(k, 11 + i * 4, f2 + 1, 124, "north")
        k.titik("kasur", 11 + i * 4, f2 + 1, 124, "south")
    K.furnish_dining(k, 24, 130, f2, n=4)
    K.gable(k, 8, f2 + 6, 122, 38, 136, along="x", overhang=1, role="tile_stairs", slab_role="tile_slab", full_role="tile")
    _ = inner


def klan_dapur_pelayan(k):
    """Servants' quarters and the clan kitchen (lot (2,2); the name holds 'dapur' for the cooking fires)."""
    f = K.podium(k, 102, 100, 138, 114, KL, 1, steps=("west",), step_w=3)
    K.hall(k, 103, 101, 137, 113, f, 5, door_sides=("west",), door_w=2, windows=4)
    K.furnish_kitchen(k, 106, 102, 120, f, 102, "south", lit_fire=True)
    for x in (124, 128, 132):
        K.bed(k, x, f + 1, 112, "south")
        k.titik("kasur", x, f + 1, 112, "north")
    K.furnish_dining(k, 113, 108, f, n=4)
    K.gable(k, 102, f + 6, 100, 138, 114, along="x", overhang=1, role="tile_stairs", slab_role="tile_slab", full_role="tile")
    K.crates(k, 104, 120, 112, 126, KL + 1, h=2)
    k.titik("kerja:sapu", 120, KL + 1, 122, "north")
    k.titik("kerja:sapu", 96, KL + 1, 96, "east")


def klan_kanvas():
    k = K.Kanvas(144, 48, 144, seed=tl._seed("kampung/klan"), palette=KLAN_PAL)
    K.paving(k, 0, 0, 143, 143, KL, role="floor", alt="floor_alt", alt_chance=0.08)
    for y in range(0, KL):
        for x in (0, 143):
            for z in range(144):
                k.put(x, y, z, "kerb")
        for z in (0, 143):
            for x in range(144):
                k.put(x, y, z, "kerb")
    # the cross avenues
    for z in range(144):
        for x in range(68, 76):
            k.put(x, KL, z, "path" if x not in (68, 75) else "kerb")
    for x in range(144):
        for z in range(68, 76):
            k.put(x, KL, z, "path" if z not in (68, 75) else "kerb")
    klan_tembok(k)
    klan_gerbang(k)
    klan_aula_leluhur(k)
    klan_lapangan(k)
    klan_rumah(k, 4, 4, "patriark", "kampung/patriark", beds=2)
    klan_alkimia(k)
    klan_rumah(k, 4, 50, "tetua_a", "kampung/tetua_klan", beds=2)
    klan_rumah(k, 99, 50, "tetua_b", "kampung/tetua_klan", beds=2)
    klan_pustaka_tamu(k)
    klan_dapur_pelayan(k)
    return k


KLAN_LOT = {(0, 0): "rumah_patriark", (1, 0): "aula_leluhur", (2, 0): "paviliun_alkimia",
            (0, 1): "rumah_tetua_barat", (1, 1): "lapangan", (2, 1): "rumah_tetua_timur",
            (0, 2): "pustaka_tamu", (1, 2): "gerbang", (2, 2): "dapur_pelayan"}
# parent -> children edges of the fixed chain (start = (1, 1))
KLAN_EDGE = [((1, 1), (1, 0)), ((1, 1), (0, 1)), ((1, 1), (2, 1)), ((1, 1), (1, 2)),
             ((1, 0), (0, 0)), ((1, 0), (2, 0)), ((1, 2), (0, 2)), ((1, 2), (2, 2))]
KLAN_TID = {}


def klan_pool(name):
    return NS + "kampung/klan/" + name


def ladang_klan(arah):
    """A clan field outside the wall (terrain_matching): rows of wheat and an herb strip, irrigation, two hoe points."""
    p = tl.Lot(F, "klan/ladang_" + arah, tinggi=4, lebar=21, dalam=33, palet=KLAN_PAL)
    for x in range(21):
        for z in range(33):
            if z == 0:
                p.put(x, 0, z, "minecraft:coarse_dirt")
            elif x % 5 == 2:
                p.put(x, 0, z, "minecraft:water", {"level": "0"})
            else:
                p.put(x, 0, z, "minecraft:farmland", {"moisture": "7"})
                p.put(x, 1, z, "minecraft:wheat" if x < 15 else "minecraft:carrots", {"age": str(7 - (x + z) % 3)})
    for x in (5, 15):
        p.titik("kerja:cangkul", x, 1, 16, "north")
    # connector on the north edge middle facing north (toward the wall)
    p.jigsaw_side(10, 0, 0, "north", name=NS + "klan/ladang_" + arah, target=NS + "klan/ladang_" + arah + "_a",
                  pool="minecraft:empty", final_state="minecraft:coarse_dirt")
    p.simpan()
    return p.id


def build_klan():
    k = klan_kanvas()
    lots = K.potong(k, {ij: "klan/" + n for ij, n in KLAN_LOT.items()}, tinggi_min=20, folder=F)
    # chain connectors (unique names per edge) on the lot edges at the floor level
    for (pa, ch) in KLAN_EDGE:
        dx, dz = ch[0] - pa[0], ch[1] - pa[1]
        face = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(dx, dz)]
        a = lots[pa]
        b = lots[ch]
        edge = "klan/%d%d_%d%d" % (pa[0], pa[1], ch[0], ch[1])
        if face in ("east", "west"):
            ax, bx = (47, 0) if face == "east" else (0, 47)
            pos_a, pos_b = (ax, 24), (bx, 24)
        else:
            az, bz = (47, 0) if face == "south" else (0, 47)
            pos_a, pos_b = (20, az), (20, bz)
        a.jigsaw_side(pos_a[0], KL, pos_a[1], face, name=NS + edge + "_a", target=NS + edge + "_b",
                      pool=klan_pool(KLAN_LOT[ch]), final_state=KLAN_PAL["floor"])
        b.jigsaw_side(pos_b[0], KL, pos_b[1], OPP[face], name=NS + edge + "_b", target=NS + edge + "_a",
                      pool="minecraft:empty", final_state=KLAN_PAL["floor"])
    # fields outside the west and east walls
    for ij, face, arah in (((0, 1), "west", "barat"), ((2, 1), "east", "timur")):
        x = 0 if face == "west" else 47
        lots[ij].jigsaw_side(x, KL, 30, face, name=NS + "klan/ladang_" + arah + "_a", target=NS + "klan/ladang_" + arah,
                             pool=klan_pool("ladang_" + arah), final_state=KLAN_PAL["kerb"])
        KLAN_TID["ladang_" + arah] = ladang_klan(arah)
    for ij, lot in lots.items():
        if len(lot.titik_list) > 64:
            raise SystemExit("%s: %d titik" % (lot.id, len(lot.titik_list)))
        lot.simpan()
        KLAN_TID[KLAN_LOT[ij]] = lot.id


# ===================================================================================================== JSON
def tag(path, values):
    structgen.write_json(os.path.join(structgen.DATA, "tags", "worldgen", path + ".json"), {"replace": False, "values": values})


def kampung_processor():
    """Light weathering and protected blocks for village pieces (no style swaps: each style is drawn on its own)."""
    return {"processors": [
        {"processor_type": "minecraft:rule", "rules": [
            {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:cobblestone", "probability": 0.12},
             "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": "minecraft:mossy_cobblestone"}},
            {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:stone_bricks", "probability": 0.06},
             "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": "minecraft:mossy_stone_bricks"}},
            {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:dirt_path", "probability": 0.15},
             "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": "minecraft:coarse_dirt"}}]},
        {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}]}


def tulis():
    proc = NS + "kampung/lapuk"
    tl.tulis_processor("kampung/lapuk", kampung_processor())
    for g in GAYA:
        pc = PIECES[g]
        tl.tulis_pool("kampung/%s/pusat" % g, [(tl.elemen(pc["pusat"], proc), 1)])
        street = [("jalan_lurus_12", 6), ("jalan_lurus_18", 4), ("jalan_simpang", 2), ("jalan_belok", 2), ("jalan_jembatan", 1)]
        structgen.write_json(os.path.join(structgen.DATA, "worldgen", "template_pool", "kampung", g, "jalan.json"),
                             {"elements": [{"element": tl.elemen(pc[n], proc, "terrain_matching"), "weight": w} for n, w in street],
                              "fallback": NS + "kampung/%s/ujung" % g})
        tl.tulis_pool("kampung/%s/ujung" % g, [(tl.elemen(pc["ujung"], proc, "terrain_matching"), 1)])
        tl.tulis_pool("kampung/%s/bangunan" % g, [(tl.elemen(pc["rumah_kecil"], proc), 1)])
        for u in UKURAN:
            bobot = bangunan_bobot(g, u)
            tl.tulis_pool("kampung/%s/bangunan_%s" % (g, u), [(tl.elemen(pc[n], proc), w) for n, w in sorted(bobot.items())])
            jumlah, jarak, adapt = JIGSAW[u]
            s = structgen.jigsaw_structure(NS + "kampung/%s/pusat" % g, "#kultivator:has_structure/kampung/" + g, size=jumlah,
                                           max_distance=jarak, terrain_adaptation=adapt, spawn_overrides=tl.SPAWN_KOSONG)
            s["pool_aliases"] = [{"type": "minecraft:direct", "alias": NS + "kampung/%s/bangunan" % g,
                                  "target": NS + "kampung/%s/bangunan_%s" % (g, u)}]
            tl.tulis_wg("structure", "kampung/%s_%s" % (u, g), s)
        tag("biome/has_structure/kampung/" + g, ["minecraft:" + b for b in BIOMA[g]])
    # clan: base pools (no swaps) + one pool per style with its processor, aliased by each style's structure
    states = tl.semua_state([t for _, t in structgen._SAVED if "/klan/" in t.id.replace("kultivator:kampung", "/")])
    for g in GAYA_KLAN:
        tl.tulis_processor("kampung/klan_gaya_" + g, tl.processor_gaya("klan", g, states, lapuk=True))
    for name, t in sorted(KLAN_TID.items()):
        proj = "terrain_matching" if name.startswith("ladang") else "rigid"
        tl.tulis_pool("kampung/klan/" + name, [(tl.elemen(t, NS + "kampung/klan_gaya_sedang", proj), 1)])
        for g in GAYA_KLAN:
            tl.tulis_pool("kampung/klan_%s/%s" % (g, name), [(tl.elemen(t, NS + "kampung/klan_gaya_" + g, proj), 1)])
    for g in GAYA_KLAN:
        jumlah, jarak, adapt = JIGSAW["klan"]
        s = structgen.jigsaw_structure(NS + "kampung/klan_%s/lapangan" % g, "#kultivator:has_structure/kampung/klan_" + g,
                                       size=jumlah, max_distance=jarak, terrain_adaptation=adapt, spawn_overrides=tl.SPAWN_KOSONG)
        s["pool_aliases"] = [{"type": "minecraft:direct", "alias": NS + "kampung/klan/" + n,
                              "target": NS + "kampung/klan_%s/%s" % (g, n)} for n in sorted(KLAN_TID) if n != "lapangan"]
        tl.tulis_wg("structure", "kampung/klan_" + g, s)
        tag("biome/has_structure/kampung/klan_" + g, ["minecraft:" + b for b in BIOMA_KLAN[g]])
    # sets (built-in pack) and tags
    for sid, (spacing, sep, salt, minc, rings, excl) in SET.items():
        if sid == "klan":
            structs = [(NS + "kampung/klan_" + g, 1) for g in GAYA_KLAN]
        else:
            structs = [(NS + "kampung/%s_%s" % (sid, g), 1) for g in GAYA]
        st = tl.set_cincin(structs, spacing, sep, salt, min_chunks=minc, cincin=rings, jarak_tempat=8, exclusion=excl)
        tl.tulis_wg("structure_set", "kampung/" + sid, st, pack=PACK)
    for u in UKURAN:
        tag("structure/kampung/" + u, [NS + "kampung/%s_%s" % (u, g) for g in GAYA])
    tag("structure/kampung/klan", [NS + "kampung/klan_" + g for g in GAYA_KLAN])
    # every village and clan village (#kultivator:kampung): locate, 38.R repairs (#kultivator:tempat_dirawat names it)
    tag("structure/kampung", ["#kultivator:kampung/" + u for u in UKURAN] + ["#kultivator:kampung/klan"])
    pack_dir = os.path.join(structgen.REPO, "src", "main", "resources", "resourcepacks", PACK)
    structgen.write_json(os.path.join(pack_dir, "pack.mcmeta"),
                         {"pack": {"description": {"translate": "kultivator.dunia.pack.kampung"}, "min_format": 121, "max_format": 121}})


def main(argv):
    for g in GAYA:
        build_gaya(g)
    build_klan()
    tulis()
    tl.cek_semua()
    total = sum(os.path.getsize(p) for p, _ in structgen._SAVED)
    print("kampung: %d templates, gzip %d bytes" % (len(structgen._SAVED), total))
    if total > 1_500_000:
        raise SystemExit("kampung: gzip %d > 1.5 MB budget (38.C.10)" % total)


if __name__ == "__main__":
    main(sys.argv[1:])
