"""Growth plans of the living towns (DESIGN 42.9.4, 42.12.1; module 42W, wave 42-G1 label 42W-G).

Writes, for every plan (gaya x jenis of kota_hidup_kit.GAYA_KAVLING x JENIS_RENCANA = 121 plans), five CUMULATIVE stage
templates (Yingzao Fashi order) that the server reads through StructureTemplateManager and applies block by block with
WorldBreaker.submitRestore (42F, reason "tumbuh"); they are never placed by /place or by worldgen:

    data/kultivator/structure/kota_tumbuh/<gaya>/<jenis>/tahap_<1-5>.nbt
        1 pondasi  stone footing, plinth, floor, yard paving, front step (clears the kavling's surveyor string)
        2 rangka   posts, ring beams, floor joists, ridge beam, porch posts, tower legs
        3 dinding  walls, paper / lattice windows, doors, upper floor boards, outside stair, railings
        4 atap     ceilings, roof (gable / hip / jiangnan firewalls / flat with dome / thatch), dougong, finials
        5 isi      the same interior kits as worldgen (tools/kota_hidup_kit.py), lanterns, sign, chimney, life points
    data/kultivator/structure/kota_tumbuh/rencana.json   sidecar: size, stage templates, materials per stage
        (42.9.4 units: kayu 1/4, batu 1/4, genteng 1/2, kaca_kertas 1/2, kain 1/4 blocks), block -> material map,
        life points of stage 5, need served (rumah / usaha / sekolah / jaga / ibadah / suasana), households, jobs.
        StructureTemplateManager lists only *.nbt under structure/, so vanilla ignores the sidecar.

Conventions (kota_hidup_kit kavling contract): the front (street side, entrance) faces SOUTH (+z); the footprint is
exactly UKURAN_KELAS[KELAS_JENIS[jenis]] (kecil 7 x 7, besar 13 x 13); y 0 = the kavling ground row; height <= 18
(TINGGI_RENCANA); 42F turns a plan with kota_hidup_kit.rotasi_rencana(hadap). Only vanilla blocks, no entities (armour
stands of a kit are dropped: growth places blocks only), no fire / lava / lightning rods, campfires unlit.

Usage:  python tools/gen_kota_tumbuh.py            regenerate every plan + rencana.json (writes only kota_tumbuh/)
        python tools/gen_kota_tumbuh.py --uji DIR   build everything into DIR and run the self test (repo untouched)
"""
import json
import math
import os
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import kota_besar_kit as K  # noqa: E402
import kota_hidup_kit as khk  # noqa: E402

OUT = os.path.join(structgen.DATA, "structure", "kota_tumbuh")
NS = "kultivator:kota_tumbuh/"
TAHAP = ("pondasi", "rangka", "dinding", "atap", "isi")
H = khk.TINGGI_RENCANA                     # 18: y 0..17
ANGGARAN = 2_400_000                       # gzip bytes of the whole folder (cek_tempat FOLDERS["kota_tumbuh"])
SATUAN = {"kayu": 4, "batu": 4, "genteng": 2, "kaca_kertas": 2, "kain": 4}   # blocks per material unit (42.9.4)
AIR = "minecraft:air"


# ===================================================================================================== staged piece
class Tahapan(structgen.Piece):
    """A Piece that keeps only the writes of stages <= batas: the plan code runs in full for every stage limit (so RNG
    use, reads and point counts stay identical) and a write tagged with a later stage is dropped. Entities are always
    dropped (42F applies blocks only)."""

    def __init__(self, w, d, seed, palette, batas):
        super().__init__(w, H, d, seed=seed, palette=palette)
        self.batas = batas
        self.tahap = 1
        self.dibuang = 0

    def set(self, x, y, z, block, props=None, nbt=None):
        if self.tahap <= self.batas:
            super().set(x, y, z, block, props, nbt)

    def titik(self, jenis, x, y, z, hadap="south", data=None):
        if self.tahap > self.batas:
            return None
        return super().titik(jenis, x, y, z, hadap, data)

    def entity(self, x, y, z, nbt):
        self.dibuang += 1


def kosong(p, x, y, z):
    return p.inside(x, y, z) and p.get(x, y, z) in (None, AIR)


def blok(p, x, y, z, role, props=None):
    """Like put, with axis y for logs and no snowy grass trouble."""
    bid = p.b(role)
    if props is None:
        props = K.axis(p, role)
        if bid in ("minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium"):
            props = {"snowy": "false"}
    p.put(x, y, z, role, props)


def balok(p, x, y, z, role, ax):
    """A beam block laid along axis ax (x or z) when the role is a log, else plain."""
    p.put(x, y, z, role, K.axis(p, role, ax))


# ===================================================================================================== styles
def _kayu(w, log=None):
    nether = w in ("crimson", "warped")
    lg = log or ("%s_stem" % w if nether else "%s_log" % w)
    return {"wood": "minecraft:%s_planks" % w, "wood_stairs": "minecraft:%s_stairs" % w,
            "wood_slab": "minecraft:%s_slab" % w, "wood_trapdoor": "minecraft:%s_trapdoor" % w,
            "wood_fence": "minecraft:%s_fence" % w, "wood_gate": "minecraft:%s_fence_gate" % w,
            "fence": "minecraft:%s_fence" % w, "rail": "minecraft:%s_fence" % w, "window": "minecraft:%s_trapdoor" % w,
            "door": "minecraft:%s_door" % w, "log": "minecraft:" + lg, "trim": "minecraft:" + lg,
            "beam": "minecraft:stripped_" + lg, "shelf": "minecraft:%s_shelf" % w}


def _pal(kayu, **o):
    d = dict(K.PAL)
    d.update(_kayu(kayu))
    d.update(o)
    return d


# per gaya: palette (roles used by this generator, kota_besar_kit and the interior kits), roof kind, window kind,
# finial, lantern colour, interior kits (shop, workshop, family), tree / plant set, lamp count factor
GAYA = {
    "kota_kultivator": {
        "pal": _pal("dark_oak", found="minecraft:stone_bricks", dasar="minecraft:stone_bricks",
                    wall="minecraft:dark_oak_planks", plaster="minecraft:white_terracotta",
                    pillar="minecraft:red_nether_bricks", floor="minecraft:spruce_planks",
                    paving="minecraft:polished_andesite", paving_alt="minecraft:stone_bricks",
                    roof="minecraft:deepslate_tile_stairs", roof_slab="minecraft:deepslate_tile_slab",
                    roof_full="minecraft:deepslate_tiles", cloth="minecraft:red_wool", cloth_alt="minecraft:white_wool"),
        "atap": "gable", "jendela": "kisi", "finial": "pearl", "lentera": "merah",
        "toko": "apotek", "bengkel": "pandai", "rumah": "rumah_sarjana", "pohon": "pine", "glif_teh": "cha"},
    "ibukota_hunian_barat": {
        "pal": _pal("dark_oak", found="minecraft:polished_andesite", dasar="minecraft:polished_andesite",
                    wall="minecraft:white_terracotta", plaster="minecraft:white_terracotta",
                    pillar="minecraft:red_terracotta", floor="minecraft:dark_oak_planks",
                    paving="minecraft:polished_andesite", paving_alt="minecraft:andesite",
                    roof="minecraft:deepslate_tile_stairs", roof_slab="minecraft:deepslate_tile_slab",
                    roof_full="minecraft:deepslate_tiles"),
        "atap": "gable", "atap_aula": "hip2", "jendela": "kertas", "finial": "pearl", "lentera": "merah",
        "toko": "toko_buku", "bengkel": "tukang_kayu", "rumah": "rumah_sarjana", "pohon": "cherry"},
    "ibukota_pasar": {
        "pal": _pal("dark_oak", found="minecraft:stone_bricks", dasar="minecraft:bricks", wall="minecraft:bricks",
                    plaster="minecraft:white_terracotta", pillar="minecraft:red_terracotta",
                    floor="minecraft:spruce_planks", paving="minecraft:polished_andesite",
                    paving_alt="minecraft:bricks", roof="minecraft:polished_deepslate_stairs",
                    roof_slab="minecraft:polished_deepslate_slab", roof_full="minecraft:polished_deepslate",
                    cloth="minecraft:red_wool", cloth_alt="minecraft:yellow_wool"),
        "atap": "gable", "jendela": "kisi", "finial": "lantern", "lentera": "merah", "tenda": True,
        "toko": "penjahit", "bengkel": "pandai", "rumah": "rumah_petani", "pohon": "willow"},
    "ibukota_kumuh": {
        "pal": _pal("spruce", found="minecraft:cobblestone", dasar="minecraft:mossy_cobblestone",
                    wall="minecraft:spruce_planks", plaster="minecraft:packed_mud", pillar="minecraft:stripped_spruce_log",
                    floor="minecraft:packed_mud", paving="minecraft:cobblestone", paving_alt="minecraft:gravel",
                    kerb="minecraft:cobblestone", kerb_slab="minecraft:cobblestone_slab",
                    kerb_stairs="minecraft:cobblestone_stairs", kerb_wall="minecraft:cobblestone_wall",
                    roof="minecraft:mud_brick_stairs", roof_slab="minecraft:mud_brick_slab",
                    roof_full="minecraft:mud_bricks", cloth="minecraft:brown_wool", cloth_alt="minecraft:light_gray_wool"),
        "atap": "gable", "jendela": "kisi", "finial": None, "lentera": "merah", "tambal": True,
        "toko": "rumah_gadai", "bengkel": "pandai", "rumah": "rumah_petani", "pohon": "bamboo"},
    "ibukota_gerbang": {
        "pal": _pal("dark_oak", found="minecraft:stone_bricks", dasar="minecraft:chiseled_stone_bricks",
                    wall="minecraft:stone_bricks", plaster="minecraft:white_terracotta",
                    pillar="minecraft:red_terracotta", floor="minecraft:dark_oak_planks",
                    paving="minecraft:stone_bricks", paving_alt="minecraft:cracked_stone_bricks",
                    roof="minecraft:polished_blackstone_brick_stairs",
                    roof_slab="minecraft:polished_blackstone_brick_slab",
                    roof_full="minecraft:polished_blackstone_bricks"),
        "atap": "gable", "atap_aula": "hip2", "jendela": "kisi", "finial": "pearl", "lentera": "merah",
        "toko": "toko_jimat", "bengkel": "pandai", "rumah": "rumah_sarjana", "pohon": "pine"},
    "ibukota_luar": {
        "pal": _pal("spruce", found="minecraft:cobblestone", dasar="minecraft:cobblestone", wall="minecraft:mud_bricks",
                    plaster="minecraft:packed_mud", pillar="minecraft:spruce_log", floor="minecraft:spruce_planks",
                    paving="minecraft:packed_mud", paving_alt="minecraft:coarse_dirt", kerb="minecraft:cobblestone",
                    kerb_slab="minecraft:cobblestone_slab", kerb_stairs="minecraft:cobblestone_stairs",
                    kerb_wall="minecraft:cobblestone_wall", roof="minecraft:dark_oak_stairs",
                    roof_slab="minecraft:dark_oak_slab", roof_full="minecraft:dark_oak_planks"),
        "atap": "gable", "jendela": "kisi", "finial": None, "lentera": "merah",
        "toko": "toko_lentera", "bengkel": "tukang_kayu", "rumah": "rumah_petani", "pohon": "willow"},
    "pelabuhan": {
        "pal": _pal("jungle", found="minecraft:stone_bricks", dasar="minecraft:stone_bricks", wall="minecraft:bricks",
                    plaster="minecraft:white_terracotta", pillar="minecraft:red_terracotta",
                    floor="minecraft:jungle_planks", paving="minecraft:polished_granite", paving_alt="minecraft:bricks",
                    roof="minecraft:brick_stairs", roof_slab="minecraft:brick_slab", roof_full="minecraft:bricks",
                    cloth="minecraft:red_wool", cloth_alt="minecraft:light_blue_wool", glow="minecraft:lantern"),
        "atap": "gable", "jendela": "kisi", "finial": "pearl", "lentera": "merah",
        "toko": "penjual_ikan", "bengkel": "tukang_kayu", "rumah": "rumah_nelayan", "pohon": "palm"},
    "oasis": {
        "pal": _pal("jungle", found="minecraft:sandstone", dasar="minecraft:cut_sandstone", wall="minecraft:mud_bricks",
                    plaster="minecraft:smooth_sandstone", pillar="minecraft:cut_sandstone",
                    floor="minecraft:smooth_sandstone", paving="minecraft:smooth_sandstone",
                    paving_alt="minecraft:sandstone", kerb="minecraft:cut_sandstone",
                    kerb_slab="minecraft:cut_sandstone_slab", kerb_stairs="minecraft:sandstone_stairs",
                    kerb_wall="minecraft:sandstone_wall", kerb_chisel="minecraft:chiseled_sandstone",
                    roof="minecraft:mud_brick_stairs", roof_slab="minecraft:mud_brick_slab",
                    roof_full="minecraft:packed_mud", dome="minecraft:cut_sandstone",
                    dome_slab="minecraft:cut_sandstone_slab", cloth="minecraft:orange_wool",
                    cloth_alt="minecraft:red_wool", ground="minecraft:sand", ground_alt="minecraft:coarse_dirt"),
        "atap": "datar", "jendela": "kisi", "finial": None, "lentera": "merah", "tenda": True,
        "toko": "penjahit", "bengkel": "pandai", "rumah": "rumah_petani", "pohon": "palm"},
    "air": {
        "pal": _pal("dark_oak", found="minecraft:stone_bricks", dasar="minecraft:stone_bricks",
                    wall="minecraft:white_terracotta", plaster="minecraft:white_terracotta",
                    pillar="minecraft:stripped_dark_oak_log", floor="minecraft:dark_oak_planks",
                    paving="minecraft:stone_bricks", paving_alt="minecraft:mossy_stone_bricks",
                    roof="minecraft:deepslate_tile_stairs", roof_slab="minecraft:deepslate_tile_slab",
                    roof_full="minecraft:deepslate_tiles"),
        "atap": "kuda", "jendela": "kisi", "finial": None, "lentera": "merah",
        "toko": "toko_kue", "bengkel": "tukang_kayu", "rumah": "rumah_nelayan", "pohon": "willow"},
    "gunung": {
        "pal": _pal("spruce", found="minecraft:stone_bricks", dasar="minecraft:stone_bricks",
                    wall="minecraft:spruce_planks", plaster="minecraft:white_terracotta", pillar="minecraft:spruce_log",
                    floor="minecraft:spruce_planks", paving="minecraft:andesite", paving_alt="minecraft:cobblestone",
                    roof="minecraft:dark_oak_stairs", roof_slab="minecraft:dark_oak_slab",
                    roof_full="minecraft:dark_oak_planks", cloth="minecraft:yellow_wool"),
        "atap": "gable", "jendela": "kisi", "finial": "pearl", "lentera": "merah", "dasar_tinggi": 2,
        "toko": "apotek", "bengkel": "pandai", "rumah": "rumah_petani", "pohon": "pine"},
    "kampung": {
        "pal": _pal("oak", found="minecraft:cobblestone", dasar="minecraft:cobblestone", wall="minecraft:oak_planks",
                    plaster="minecraft:white_terracotta", pillar="minecraft:oak_log", floor="minecraft:oak_planks",
                    paving="minecraft:dirt_path", paving_alt="minecraft:coarse_dirt", kerb="minecraft:cobblestone",
                    kerb_slab="minecraft:cobblestone_slab", kerb_stairs="minecraft:cobblestone_stairs",
                    kerb_wall="minecraft:cobblestone_wall", roof="minecraft:spruce_stairs", roof_slab="minecraft:spruce_slab",
                    roof_full="minecraft:hay_block", cloth="minecraft:brown_wool"),
        "atap": "jerami", "jendela": "kisi", "finial": None, "lentera": "merah",
        "toko": "toko_kue", "bengkel": "tukang_kayu", "rumah": "rumah_petani", "pohon": "oak"},
}
assert set(GAYA) == set(khk.GAYA_KAVLING), "every kavling gaya needs a style"


def atap_ids(S):
    """The block ids that count as genteng (roof) for a style."""
    pal = S["pal"]
    ids = {pal["roof"], pal["roof_slab"], pal["roof_full"]}
    return ids


# ===================================================================================================== walls and frame
def jendela(p, S, x, y, z, face):
    if S["jendela"] == "kertas":
        p.put(x, y, z, "minecraft:white_stained_glass_pane", {"waterlogged": "false"})
    else:
        K.lattice(p, x, y, z, face)


def ring(x0, z0, x1, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                yield x, z


def sisi_dari(x, z, x0, z0, x1, z1):
    if z == z1:
        return "south"
    if z == z0:
        return "north"
    return "west" if x == x0 else "east"


def tiang_dinding(x0, z0, x1, z1, depan=(), langkah=5):
    """Post positions of a wall ring: corners, every `langkah` along the sides and the listed front xs."""
    pos = set()
    for x, z in ring(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        if corner:
            pos.add((x, z))
        elif z == z1:
            if x in depan:
                pos.add((x, z))
        elif z == z0 and (x - x0) % langkah == 0 and x1 - x >= 2:
            pos.add((x, z))
        elif x in (x0, x1) and (z - z0) % langkah == 0 and z1 - z >= 2:
            pos.add((x, z))
    return pos


def rangka(p, x0, z0, x1, z1, ya, yb, tiang, pita=()):
    """Stage 2: posts from ya to yb, beam bands at the rows of `pita` (ring beams)."""
    for (x, z) in tiang:
        for y in range(ya, yb + 1):
            blok(p, x, y, z, "pillar")
    for y in pita:
        for x, z in ring(x0, z0, x1, z1):
            if (x, z) in tiang:
                continue
            balok(p, x, y, z, "beam", "x" if z in (z0, z1) else "z")


def dinding(p, S, x0, z0, x1, z1, ya, yb, tiang, bukaan=None, jendela_y=(), bahan_atas="plaster", y_atas=None,
            lewati=()):
    """Stage 3: wall infill between the posts from ya to yb; lower course 'dasar', walls above, 'plaster' from y_atas.
    bukaan = {(x, z): (y0, y1)} door / opening cells left as air; jendela_y = rows that get windows (not next to posts
    and not beside an opening); lewati = cells (x, z, y) not to fill (already framed)."""
    bukaan = bukaan or {}
    tinggi_dasar = S.get("dasar_tinggi", 1)
    for x, z in ring(x0, z0, x1, z1):
        if (x, z) in tiang:
            continue
        face = sisi_dari(x, z, x0, z0, x1, z1)
        dekat_tiang = any((x + dx, z + dz) in tiang for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        dekat_buka = any((x + dx, z + dz) in bukaan for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        for y in range(ya, yb + 1):
            if (x, y, z) in lewati:
                continue
            if (x, z) in bukaan and bukaan[(x, z)][0] <= y <= bukaan[(x, z)][1]:
                p.put(x, y, z, AIR)
                continue
            if y in jendela_y and not dekat_tiang and not dekat_buka:
                jendela(p, S, x, y, z, face)
                continue
            if y < ya + tinggi_dasar:
                role = "dasar"
            elif y_atas is not None and y >= y_atas:
                role = bahan_atas
            else:
                role = "wall"
            blok(p, x, y, z, role)


def lantai(p, x0, z0, x1, z1, y, role="floor"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            blok(p, x, y, z, role)


def tutup_bawah_atap(p, x0, z0, x1, z1, y_dari, role="plaster"):
    """Stage 4 (after the roof): raises every wall-ring column from y_dari up to just under the roof above it (gable
    triangles, eave gaps), so no hole is left between the wall plate and the tiles."""
    for x, z in ring(x0, z0, x1, z1):
        y = y_dari
        while y < H and not kosong(p, x, y, z):      # skip the wall plate / beam
            y += 1
        atas = next((yy for yy in range(y, H) if not kosong(p, x, yy, z)), None)
        if atas is None:
            continue
        for yy in range(y, atas):
            blok(p, x, yy, z, role)


def langit(p, x0, z0, x1, z1, y):
    """A plank ceiling (the kits hang their lanterns under it)."""
    lantai(p, x0, z0, x1, z1, y, "wood")


# ===================================================================================================== roofs
def puncak(p, S, x, y, z):
    if S.get("finial"):
        K.finial(p, x, y, z, S["finial"])


def atap(p, S, kind, x0, z0, x1, z1, y, xw=None):
    """Stage 4 roof over the roof rectangle x0..x1, z0..z1 (overhangs included) from row y; xw = (wall x0, wall x1)
    for the jiangnan firewalls. Returns the highest roof row."""
    if kind == "gable":
        top = p.roof_gable(x0 + 1, y, z0 + 1, x1 - 1, z1 - 1, overhang=1, eave_curl=True)
        rows = z1 - z0 + 1
        puncak(p, S, (x0 + x1) // 2, top + 1, z0 + rows // 2 - (0 if rows % 2 else 1))
        return top + 1
    if kind in ("hip", "hip2"):
        tiers = 2 if kind == "hip2" else 1
        yy = p.roof_hip(x0 + 1, y, z0 + 1, x1 - 1, z1 - 1, overhang=1, eave_curl=True, tiers=tiers, tier_gap=2,
                        tier_band=2, band_role="plaster")
        puncak(p, S, (x0 + x1) // 2, yy, (z0 + z1) // 2)
        return yy
    if kind == "kuda":
        wx0, wx1 = xw
        top = p.roof_gable(wx0 + 1, y, z0 + 1, wx1 - 1, z1 - 1, overhang=1, eave_curl=False)
        for wx in (wx0, wx1):
            tembok_kuda(p, wx, z0, z1, y, top)
        return top + 1
    if kind == "jerami":
        return jerami(p, x0, z0, x1, z1, y)
    if kind == "datar":
        return datar(p, S, x0, z0, x1, z1, y)
    raise ValueError(kind)


def tembok_kuda(p, x, z0, z1, y, top):
    """Jiangnan 'horse-head' firewall on a gable wall: the wall rises over the roof in three steps, each step capped
    with tiles and upturned at its outer end."""
    n = z1 - z0 + 1
    for z in range(z0, z1 + 1):
        d = min(z - z0, z1 - z)                     # distance from the nearer eave
        tier = 0 if d < n // 5 + 1 else (1 if d < 2 * (n // 5) + 1 else 2)
        cap = min(y + 2 + 2 * tier + (1 if tier == 2 else 0), H - 1, top + 1 if tier == 2 else H - 1)
        for yy in range(y, cap):
            blok(p, x, yy, z, "plaster")
        p.slab(x, cap, z, "roof_slab")
        # the step end turns up like a horse's head
        dn = min(z - 1 - z0, z1 - z + 1) if z > z0 else -1
        nxt = 0 if dn < 0 else (0 if dn < n // 5 + 1 else (1 if dn < 2 * (n // 5) + 1 else 2))
        if z == z0 or z == z1 or nxt != tier:
            if cap + 1 < H:
                p.stairs(x, cap, z, "roof", "south" if z - z0 < z1 - z else "north", half="top")


def jerami(p, x0, z0, x1, z1, y):
    """A thatched gable (hay layers stepping in, oak slab ridge, timber barge boards)."""
    rows = z1 - z0 + 1
    layers = rows // 2
    for i in range(layers):
        for x in range(x0, x1 + 1):
            for z in (z0 + i, z1 - i):
                if x in (x0, x1):
                    p.stairs(x, y + i, z, "roof", "south" if z == z0 + i else "north")
                else:
                    p.put(x, y + i, z, "roof_full", {"axis": "y"})
    top = y + layers
    mids = [z0 + layers] if rows % 2 else []
    for x in range(x0, x1 + 1):
        for z in mids:
            p.put(x, top - 1 + (0 if rows % 2 else 1), z, "roof_full", {"axis": "y"})
        for z in (mids or [z0 + layers - 1, z0 + layers]):
            p.slab(x, top + (0 if rows % 2 else 0), z, "wood_slab")
    return top


def datar(p, S, x0, z0, x1, z1, y):
    """A flat desert roof: a slab-topped deck over the walls, a sandstone parapet with merlons and rain spouts."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            p.put(x, y, z, "roof_full")
    for x, z in ring(x0, z0, x1, z1):
        if (x + z) % 3 == 0 or (x in (x0, x1) and z in (z0, z1)):
            p.put(x, y + 1, z, "kerb_wall")
        else:
            p.slab(x, y + 1, z, "roof_slab")
    return y + 1


def kubah(p, S, cx, cz, y, r=2):
    """A small stepped dome (cut sandstone rings, slab cap, gold tip)."""
    for i in range(r + 1):
        rr = r - i
        for x in range(cx - rr, cx + rr + 1):
            for z in range(cz - rr, cz + rr + 1):
                if abs(x - cx) == rr or abs(z - cz) == rr:
                    p.put(x, y + i, z, "dome")
    p.slab(cx, y + r + 1, cz, "dome_slab")
    if y + r + 2 < H:
        p.put(cx, y + r + 2, cz, "gold")


def dougong(p, xs, z, y, face="north"):
    """Brackets: upside-down wood stairs under an eave, facing the wall."""
    for x in xs:
        if kosong(p, x, y, z):
            p.stairs(x, y, z, "wood_stairs", face, half="top")


# ===================================================================================================== besar shell
# walls x 1..11, z 0..10 (back wall on the footprint edge), interior x 2..10 / z 1..9 (9 x 9), porch z 11, step z 12;
# roof x 0..12, z 0..11. Two storeys: floor 1, clear 2..4, upper floor 5, clear 6..8, plate 9, roof from 10.
# Hall: floor 1, clear 2..6, plate 7, roof from 8. The upper storey is reached by an outside stair along x = 12.
BX0, BX1, BZ0, BZ1 = 1, 11, 0, 10
DEPAN_TIANG = (1, 4, 8, 11)


def pondasi_besar(p, S, pintu_xs):
    p.tahap = 1
    for x in range(13):
        for z in range(13):
            pv = "paving_alt" if (x * 5 + z * 3 + (x * z) % 7) % 9 == 0 else "paving"
            blok(p, x, 0, z, "found" if (BX0 <= x <= BX1 and z <= BZ1) else pv)
    for x in range(13):
        for z in range(12):
            if BX0 <= x <= BX1 and BZ0 <= z <= BZ1:
                inner = BX0 < x < BX1 and BZ0 < z < BZ1
                blok(p, x, 1, z, "floor" if inner else "kerb")
            elif x in (0, 12) or z == 11:
                edge = x in (0, 12) and z in (0, 11) or (z == 11 and x in (0, 12))
                blok(p, x, 1, z, "kerb_chisel" if edge else ("kerb" if x in (0, 12) else "paving"))
    for x in range(13):
        if any(abs(x - px) <= 1 for px in pintu_xs):
            p.stairs(x, 1, 12, "kerb_stairs", "north")
        else:
            p.slab(x, 1, 12, "kerb_slab")


def cangkang_besar(p, S, dua, depan_terbuka=False, lebar_pintu=1, kind=None):
    """The besar building shell through stage 4. Returns a dict with the floors, door, roof info."""
    kind = kind or S["atap"]
    pintu = 6
    plate = 9 if dua else 7
    base = plate + 1
    pondasi_besar(p, S, [pintu] + ([12] if dua else []))
    tiang = tiang_dinding(BX0, BZ0, BX1, BZ1, DEPAN_TIANG)
    # ---- stage 2: frame
    p.tahap = 2
    rangka(p, BX0, BZ0, BX1, BZ1, 2, plate, tiang, pita=(5, plate) if dua else (plate,))
    porch = [(1, 11), (11, 11)]
    for (x, z) in porch:
        for y in range(2, (5 if dua else plate) + 1):
            blok(p, x, y, z, "pillar")
    if dua:
        for x in range(BX0 + 1, BX1):          # floor joists of the upper storey (along z, every other column)
            if x % 2 == 0:
                for z in range(BZ0 + 1, BZ1):
                    balok(p, x, 5, z, "beam", "z")
    if kind in ("gable", "kuda", "jerami"):
        rows = 12
        ridge_y = base + rows // 2 - 2
        if ridge_y < H:
            for x in range(BX0, BX1 + 1):
                for z in (5, 6):
                    balok(p, x, ridge_y, z, "log", "x")
            for x in (BX0, BX1):
                for y in range(plate + 1, ridge_y):
                    for z in (5, 6):
                        blok(p, x, y, z, "pillar")
    # ---- stage 3: walls, windows, doors, floors
    p.tahap = 3
    bukaan = {}
    if depan_terbuka:
        for x in range(BX0 + 1, BX1):
            if (x, BZ1) not in tiang:
                bukaan[(x, BZ1)] = (3, plate - 1) if abs(x - pintu) > 1 else (2, plate - 1)
    else:
        for dx in range(-(lebar_pintu // 2), lebar_pintu - lebar_pintu // 2):
            bukaan[(pintu + dx, BZ1)] = (2, 3 if lebar_pintu == 1 else 4)
    if dua:
        bukaan[(BX1, 6)] = (6, 7)                  # upper door from the outside stair
    jy = (3, 7) if dua else (3, 4)
    dinding(p, S, BX0, BZ0, BX1, BZ1, 2, plate - 1, tiang, bukaan, jy, y_atas=6 if dua else None,
            lewati={(x, z, 5) for x, z in ring(BX0, BZ0, BX1, BZ1)} if dua else ())
    if dua:
        lantai(p, BX0 + 1, BZ0 + 1, BX1 - 1, BZ1 - 1, 5, "wood")
        for x in range(BX0 + 1, BX1):
            if x % 2 == 0:
                for z in range(BZ0 + 1, BZ1):
                    balok(p, x, 5, z, "beam", "z")
        # outside stair along x = 12, from the porch up to a landing at the upper door
        for z, y in ((10, 2), (9, 3), (8, 4)):
            p.stairs(12, y, z, "wood_stairs", "north")
            for yy in range(2, y):
                blok(p, 12, yy, z, "kerb")
        for z in (7, 6):
            blok(p, 12, 5, z, "wood")
            for yy in range(2, 5):
                blok(p, 12, yy, z, "log" if z == 6 else "kerb")
        p.put(12, 6, 5, "fence")
        p.put(12, 6, 7, "fence")
    if not depan_terbuka:
        if lebar_pintu == 1:
            K.door(p, pintu, 2, BZ1, "south")
        elif lebar_pintu == 2:
            K.door(p, pintu, 2, BZ1, "south", "left")
            K.door(p, pintu + 1, 2, BZ1, "south", "right")
    if dua:
        K.door(p, BX1, 6, 6, "east")
    if depan_terbuka:
        for x in range(BX0 + 1, BX1):
            if (x, BZ1) not in tiang and abs(x - pintu) > 1:
                p.put(x, 2, BZ1, "wood_slab", {"type": "top", "waterlogged": "false"})
    # ---- stage 4: ceilings and roof
    p.tahap = 4
    langit(p, BX0 + 1, BZ0 + 1, BX1 - 1, BZ1 - 1, plate)
    if dua and kind != "datar":
        for x in range(BX0, BX1 + 1):          # the pent roof (yaoyan) between the storeys over the porch
            if (x, 11) not in porch:
                p.stairs(x, 5, 11, "roof", "north")
        p.stairs(0, 5, 11, "roof", "north", shape="outer_left")
    if kind == "datar":
        top = datar(p, S, BX0, BZ0, BX1, BZ1, base - 1)
        for x in range(BX0, BX1 + 1):
            p.put(x, base - 2 if dua else plate - 1, 11, "cloth" if x % 2 else "cloth_alt")
    else:
        top = atap(p, S, kind, 0, 0, 12, 11, base, xw=(BX0, BX1))
        tutup_bawah_atap(p, BX0, BZ0, BX1, BZ1, plate, "plaster")
        dougong(p, [x for x in range(2, 11, 2) if (x, 11) not in porch], 11, base - 1)
        if kind in ("hip", "hip2"):
            for x in (0, 12):
                for z in range(1, 11, 2):
                    if kosong(p, x, base - 1, z):
                        p.stairs(x, base - 1, z, "wood_stairs", "east" if x == 0 else "west", half="top")
    return {"pintu": pintu, "plate": plate, "base": base, "top": top, "dua": dua, "kind": kind, "porch": porch,
            "lebar_pintu": 0 if depan_terbuka else lebar_pintu}


# ===================================================================================================== interiors
DAPUR_BELAKANG = (10, 1)       # chimney spot over the back wall when a kit has no stove of its own


def cerobong_atas(p, x, z):
    """A chimney through the roof above (x, z) with its smoke point (cerobong) when there is room under y 17."""
    top = None
    for y in range(H - 1, 0, -1):
        if not kosong(p, x, y, z):
            top = y
            break
    if top is None or top + 2 >= H - 1:
        return None
    t = min(3, H - 2 - top)
    return khk.cerobong(p, x, top + 1, z, t, "minecraft:bricks")


def kamar_tidur(p, S, x0, y0, z0, w, d, h, hadap, pintu, ranjang=3):
    """An upper-floor bedroom suite: beds along the back wall with kasur points, wardrobes, chests with peti_milik,
    a folding screen, a dressing table, rugs and lanterns under the ceiling."""
    b = khk.Bilik(p, x0, y0, z0, w, d, h, hadap, pintu=pintu, titik_maks=14, kit="kamar", min_wdh=(5, 4, 3))
    warna = ("red", "light_gray", "brown", "white")
    u = 0
    n = 0
    while u + 1 < b.W and n < ranjang:
        if b.semua_bebas([(u, b.D - 1), (u, b.D - 2)]):
            b.kasur(u, b.D - 1, "south", warna[n % len(warna)])
            b.titik("kasur", u, b.D - 2, "north")
            b.pakai([(u, b.D - 1), (u, b.D - 2)])
            if u + 1 < b.W and b.bebas(u + 1, b.D - 1):
                b.peti(u + 1, 1, b.D - 1, "north")
                b.p(u + 1, 2, b.D - 1, "minecraft:candle", {"candles": "1", "lit": "false", "waterlogged": "false"})
                b.titik("peti_milik", u + 1, b.D - 2, "south")
                b.pakai([(u + 1, b.D - 1)])
            n += 1
            u += 3
        else:
            u += 1
    if b.D >= 6:
        b.layar(1, b.W - 2, b.D - 4, "white")
        b.pakai([(uu, b.D - 4) for uu in range(1, b.W - 1)])
    for u in (0, b.W - 1):
        if b.bebas(u, 1):
            b.tong(u, 1, 1, b.arah("south"))
            b.tong(u, 2, 1, "up")
            b.pakai([(u, 1)])
    if b.bebas(b.W - 1, 2):
        b.meja_papan(b.W - 1, 2, 1)
        b.pot(b.W - 1, 2, 2, "minecraft:potted_azalea_bush")
        b.pakai([(b.W - 1, 2)])
    b.karpet(2, 2, b.W - 3, min(3, b.D - 3), "red", "yellow")
    b.isi_dinding(("bunga", "-", "rak", "-"), "we")
    b.isi_lampu(4)
    return b


def ruang_minum(p, S, x0, y0, z0, w, d, h, hadap, pintu):
    """The upper drinking room of a wine house: square tables with four stools, wine jars along the walls, red
    lanterns; kursi points (the night district's drinkers, 42.9.6)."""
    b = khk.Bilik(p, x0, y0, z0, w, d, h, hadap, pintu=pintu, titik_maks=18, kit="minum", min_wdh=(6, 6, 3))
    b.lorong(3, 0, 2)

    def meja(u, v):
        return khk._meja_kursi(b, u + 1, v + 1, "kursi", ("w", "e", "n", "s"))

    b.atur(2, b.D - 3, 4, 3, meja, 1, 1)
    b.isi_dinding(("guci", "tong2", "-", "guci", "-", "panji"), "web")
    for i in range(0, b.W, 3):
        if b.bebas(i, b.D - 1):
            b.guci(i, 1, b.D - 1)
    b.isi_lampu(3, "minecraft:lantern")
    return b


def kamar_kecil(p, S, gaya):
    """The 5 x 4 room of a small house: bed in the back corner, hearth with a cooking pot, table and stool, chest,
    family altar shelf, water jar; kasur, meja_makan, kerja:kuali, dupa points."""
    b = khk.Bilik(p, 1, 1, 1, 5, 4, 3, "south", pintu=3, titik_maks=10, kit="kecil", min_wdh=(5, 4, 3))
    b.kasur(0, 3, "south", "red" if gaya != "kampung" else "brown")
    b.pakai([(0, 3), (0, 2)])
    b.titik("kasur", 1, 2, "west")
    # hearth: smoker + barrel + a pot rack above
    b.p(4, 1, 3, "minecraft:smoker", {"facing": b.arah("north"), "lit": "false"})
    b.tong(4, 1, 2, b.arah("west"))
    b.p(4, 2, 2, "minecraft:flower_pot")
    b.p(4, 3, 3, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    b.dapur = b.xz(4, 3)
    b.titik("kerja:kuali", 3, 3, "east")
    b.pakai([(4, 3), (4, 2), (3, 3)])
    # altar shelf on the back wall between bed and hearth
    b.p(2, 2, 3, "minecraft:dark_oak_trapdoor" if gaya != "oasis" else "minecraft:jungle_trapdoor",
        {"facing": b.arah("north"), "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    b.lilin(2, 3, 3, 2, "red")
    b.titik("dupa", 2, 2, "south")
    b.pakai([(2, 3)])
    # table with a stool, a chest by the door, a water jar
    b.meja(4, 0)
    b.pakai([(4, 0)])
    b.peti(0, 1, 0, b.arah("east"))
    b.pakai([(0, 0)])
    b.guci(0, 1, 1)
    b.pakai([(0, 1)])
    b.titik("meja_makan", 3, 0, "east")
    b.lentera(2, 1)
    return b


# ===================================================================================================== besar plans
def dandan_depan(p, S, sh, jenis):
    """Stage 5 outside dressing of a besar building: couplet banners, porch lanterns (client paper lanterns), potted
    plants and barrels on the side strips, an awning for market styles, the building box and door points."""
    p.tahap = 5
    pintu = sh["pintu"]
    ly = 3 if sh["dua"] else 6
    for x in (3, 9):
        if kosong(p, x, ly + 1, 11) and kosong(p, x, ly, 11):
            khk.lentera_gantung(p, x, ly, 11, "south", S["lentera"])
    lebar = sh.get("lebar_pintu", 1)
    kiri = pintu - lebar // 2
    for x in (kiri - 1, kiri + lebar):                   # red couplet banners on both door jambs
        if kosong(p, x, 3, 11) and not kosong(p, x, 3, 10):
            K.wall_banner(p, x, 3, 11, "south", "red")
    for x in (0,):
        for z, it in ((2, "barrel"), (4, "pot"), (8, "pot")):
            if kosong(p, x, 2, z):
                if it == "barrel":
                    K.barrel(p, x, 2, z, "up")
                    K.barrel(p, x, 3, z, "east")
                else:
                    p.put(x, 2, z, S["pal"]["potted"] if "potted" in S["pal"] else "minecraft:potted_bamboo")
    if not sh["dua"]:
        for z in (2, 8):
            if kosong(p, 12, 2, z):
                p.put(12, 2, z, "minecraft:potted_azalea_bush" if z == 2 else "minecraft:potted_fern")
    hiasan_luar(p, S, sh, jenis)
    p.titik("bangunan", BX0, 1, BZ0, "south", {"dx": BX1 - BX0, "dy": min(H - 2, sh["top"]) - 1,
                                                "dz": BZ1 - BZ0, "nama": "kota_tumbuh"})
    p.titik("pintu", pintu, 2, 11, "south")


def bangku_luar(p, xs, z=11, titik=0):
    """Porch benches (stairs facing the street); the first `titik` get kursi points."""
    n = 0
    for x in xs:
        if kosong(p, x, 2, z) and kosong(p, x, 3, z):
            p.stairs(x, 2, z, "wood_stairs", "north")
            if n < titik:
                p.titik("kursi", x, 2, z, "south")
            n += 1


def tumpukan_kayu(p, x, z0, z1, tinggi=2):
    """A firewood / timber pile along a side strip (logs laid along z, a slab cover)."""
    for z in range(z0, z1 + 1):
        if not kosong(p, x, 2, z):
            continue
        for y in range(2, 2 + tinggi):
            balok(p, x, y, z, "log", "z")
        p.slab(x, 2 + tinggi, z, "wood_slab")


def hiasan_luar(p, S, sh, jenis):
    """Per-plan outside detail on the porch and the side strips (x 0 / x 12; the outside stair of a two-storey
    building keeps x 12, z 6..11 free)."""
    if jenis == "rumah_besar":
        if kosong(p, 12, 2, 2):
            p.put(12, 2, 2, "minecraft:cauldron")                  # rain-water vat
            p.put(12, 2, 3, "minecraft:composter", {"level": "0"})
        tumpukan_kayu(p, 0, 5, 7)
        bangku_luar(p, (9, 10), titik=0)
        p.titik("obrol", 9, 2, 12, "north")
    elif jenis == "rumah_toko":
        for x in (2, 4):                                            # goods counter on the porch
            if kosong(p, x, 2, 11):
                K.barrel(p, x, 2, 11, "south")
                p.put(x, 3, 11, "wood_trapdoor", {"facing": "south", "half": "bottom", "open": "false",
                                                  "powered": "false", "waterlogged": "false"})
        p.titik("lapak", 3, 2, 12, "north")
        if kosong(p, 12, 2, 3):
            K.barrel(p, 12, 2, 3, "up")
            K.barrel(p, 12, 2, 4, "north")
    elif jenis == "kedai_teh":
        bangku_luar(p, (2, 4, 8, 10), titik=4)
        for x in (12,):
            for z in (4, 6):
                if kosong(p, x, 2, z):
                    p.put(x, 2, z, "minecraft:potted_bamboo")
    elif jenis == "kedai_arak":
        for x in (2, 10):
            if kosong(p, x, 2, 11):
                p.put(x, 2, 11, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
        for z in (2, 3, 4):
            if kosong(p, 12, 2, z):
                K.barrel(p, 12, 2, z, "east")
                if z != 3:
                    K.barrel(p, 12, 3, z, "up")
    elif jenis == "gudang":
        for z in (1, 2, 3):
            if kosong(p, 0, 2, z):
                p.put(0, 2, z, "minecraft:hay_block", {"axis": "y"})
                if z != 2:
                    p.put(0, 3, z, "minecraft:hay_block", {"axis": "z"})
        for z in (2, 3, 7, 8):
            if kosong(p, 12, 2, z):
                K.barrel(p, 12, 2, z, "up")
                p.put(12, 3, z, "minecraft:spruce_planks" if z in (3, 7) else "minecraft:barrel",
                      None if z in (3, 7) else {"facing": "north", "open": "false"})
        p.titik("lumbung", 6, 2, 12, "north")
    elif jenis == "bengkel":
        if S["bengkel"] == "tukang_kayu":
            tumpukan_kayu(p, 12, 1, 4, 2)
            if kosong(p, 0, 2, 6):
                p.put(0, 2, 6, "minecraft:stonecutter", {"facing": "east"})
        else:
            if kosong(p, 12, 2, 3):
                p.put(12, 2, 3, "minecraft:cauldron")              # quench tub
                p.put(12, 2, 4, "minecraft:grindstone", {"face": "floor", "facing": "west"})
                p.put(12, 2, 6, "minecraft:coal_block")
                p.slab(12, 3, 6, "minecraft:cobblestone_slab")
    elif jenis == "sekolah":
        if kosong(p, 12, 2, 9):
            K.drum(p, 12, 2, 9, "west")                            # the lesson drum
        bangku_luar(p, (2, 3, 9, 10), titik=0)
        for z in (2, 5):
            if kosong(p, 0, 2, z):
                p.put(0, 2, z, "minecraft:potted_bamboo")


def kit_dasar(p, kit, sh, papan=True, titik_maks=None):
    kw = {"pintu": sh["pintu"]}
    if titik_maks:
        kw["titik_maks"] = titik_maks
    if kit in ("rumah_petani", "rumah_nelayan", "rumah_sarjana", "gudang", "altar_leluhur", "kuil_kecil",
               "balai_kota", "rumah_kaya"):
        kw["papan"] = False
    else:
        kw["papan"] = papan
    h = (4 if sh["dua"] else 6) - 1
    return khk.interior(kit, p, BX0 + 1, 1, BZ0 + 1, 9, 9, h, "south", **kw)


def rencana_rumah_besar(p, S, gaya):
    sh = cangkang_besar(p, S, True)
    p.tahap = 5
    b = kit_dasar(p, S["rumah"], sh)
    kamar_tidur(p, S, BX0 + 1, 5, BZ0 + 1, 9, 9, 3, "east", 6, ranjang=3)
    cerobong_atas(p, *(b.dapur or DAPUR_BELAKANG))
    dandan_depan(p, S, sh, "rumah_besar")
    return sh, [S["rumah"], "kamar"]


def rencana_rumah_toko(p, S, gaya):
    sh = cangkang_besar(p, S, True)
    p.tahap = 5
    b = kit_dasar(p, S["toko"], sh)
    kamar_tidur(p, S, BX0 + 1, 5, BZ0 + 1, 9, 9, 3, "east", 6, ranjang=2)   # the shopkeeper's family lives upstairs
    cerobong_atas(p, *(b.dapur or DAPUR_BELAKANG))
    dandan_depan(p, S, sh, "rumah_toko")
    return sh, [S["toko"], "kamar"]


def rencana_kedai_arak(p, S, gaya):
    sh = cangkang_besar(p, S, True)
    p.tahap = 5
    b = kit_dasar(p, "kedai_arak", sh)
    ruang_minum(p, S, BX0 + 1, 5, BZ0 + 1, 9, 9, 3, "east", 6)
    cerobong_atas(p, *(b.dapur or DAPUR_BELAKANG))           # the wine warmer's stove
    dandan_depan(p, S, sh, "kedai_arak")
    return sh, ["kedai_arak", "minum"]


def rencana_kedai_teh(p, S, gaya):
    sh = cangkang_besar(p, S, False, kind=S.get("atap_aula", "hip") if S["atap"] in ("gable",) else S["atap"])
    p.tahap = 5
    b = kit_dasar(p, "kedai_teh", sh)
    cerobong_atas(p, *(b.dapur or DAPUR_BELAKANG))           # the water boiler's stove
    if sh["kind"] == "datar":
        kubah(p, S, 6, 5, sh["top"] - 0, 2)
    dandan_depan(p, S, sh, "kedai_teh")
    return sh, ["kedai_teh"]


def rencana_sekolah(p, S, gaya):
    sh = cangkang_besar(p, S, False, kind="hip" if S["atap"] == "gable" else S["atap"], lebar_pintu=2)
    p.tahap = 5
    kit_dasar(p, "sekolah", sh)
    dandan_depan(p, S, sh, "sekolah")
    return sh, ["sekolah"]


def rencana_gudang(p, S, gaya):
    sh = cangkang_besar(p, S, False, lebar_pintu=3)
    p.tahap = 5
    kit_dasar(p, "gudang", sh)
    dandan_depan(p, S, sh, "gudang")
    return sh, ["gudang"]


def rencana_bengkel(p, S, gaya):
    sh = cangkang_besar(p, S, False, depan_terbuka=True)
    p.tahap = 5
    b = kit_dasar(p, S["bengkel"], sh)
    if b.dapur:
        cerobong_atas(p, *b.dapur)
    dandan_depan(p, S, sh, "bengkel")
    return sh, [S["bengkel"]]


# ===================================================================================================== kecil plans
def rencana_rumah_kecil(p, S, gaya):
    """A one-room cottage: walls x 0..6 / z 0..5, porch z 6 under the eave, roof per style."""
    p.tahap = 1
    for x in range(7):
        for z in range(7):
            blok(p, x, 0, z, "found")
            if z <= 5:
                blok(p, x, 1, z, "floor" if 0 < x < 6 and 0 < z < 5 else "kerb")
            elif x in (0, 6):
                blok(p, x, 1, z, "kerb_chisel")
            else:
                p.slab(x, 1, z, "kerb_slab")
    tiang = {(0, 0), (6, 0), (0, 5), (6, 5), (3, 0)}
    p.tahap = 2
    rangka(p, 0, 0, 6, 5, 2, 5, tiang, pita=(5,))
    for x in (0, 6):
        for y in range(2, 6):
            blok(p, x, y, 6, "pillar")
    p.tahap = 3
    dinding(p, S, 0, 0, 6, 5, 2, 4, tiang, {(3, 5): (2, 3)}, (3,))
    K.door(p, 3, 2, 5, "south")
    p.tahap = 4
    langit(p, 1, 1, 5, 4, 5)
    kind = S["atap"]
    if kind == "datar":
        top = datar(p, S, 0, 0, 6, 5, 5)
        for x in range(0, 7):
            p.put(x, 4, 6, "cloth" if x % 2 else "cloth_alt")
    else:
        top = atap(p, S, "gable" if kind in ("hip", "hip2") else kind, 0, 0, 6, 6, 6, xw=(0, 6))
        tutup_bawah_atap(p, 0, 0, 6, 5, 5, "plaster")
    p.tahap = 5
    b = kamar_kecil(p, S, gaya)
    if b.dapur:
        cerobong_atas(p, *b.dapur)
    if kosong(p, 1, 4, 6) and kosong(p, 1, 5, 6):
        khk.lentera_gantung(p, 1, 4, 6, "south", S["lentera"])
    if kosong(p, 5, 2, 6):
        p.put(5, 2, 6, "minecraft:potted_bamboo" if gaya != "oasis" else "minecraft:potted_dead_bush")
    p.titik("bangunan", 0, 1, 0, "south", {"dx": 6, "dy": min(H - 2, top) - 1, "dz": 5, "nama": "kota_tumbuh"})
    p.titik("pintu", 3, 2, 6, "south")
    return {"pintu": 3, "top": top, "kind": kind}, ["kecil"]


def rencana_kuil_kecil(p, S, gaya):
    """An open roadside Earth-God shrine: 7 x 7 stone floor, four lacquered posts, a lattice screen band at the back,
    the kuil_kecil kit (altar figure, incense burner, bell, donation chest) and a hip roof with curled eaves."""
    p.tahap = 1
    for x in range(7):
        for z in range(7):
            blok(p, x, 0, z, "found")
            blok(p, x, 1, z, "kerb" if (x in (0, 6) or z == 0) else "paving")
    for x in range(1, 6):
        p.stairs(x, 1, 6, "kerb_stairs", "north")
    tiang = [(0, 0), (6, 0), (0, 6), (6, 6)]
    p.tahap = 2
    for (x, z) in tiang:
        for y in range(2, 6):
            blok(p, x, y, z, "pillar")
    for x in range(7):
        for z in (0, 6):
            if (x, z) not in tiang:
                balok(p, x, 5, z, "beam", "x")
    for z in range(1, 6):
        for x in (0, 6):
            balok(p, x, 5, z, "beam", "z")
    p.tahap = 3
    for x in range(1, 6):
        K.lattice(p, x, 4, 0, "north")
    for z in range(1, 6):
        for x in (0, 6):
            p.put(x, 2, z, "kerb_wall")
    p.tahap = 4
    langit(p, 1, 1, 5, 5, 6)
    kind = S["atap"]
    if kind == "datar":
        top = datar(p, S, 0, 0, 6, 6, 6)
        kubah(p, S, 3, 3, top, 2)
        top += 4
    elif kind == "jerami":
        top = jerami(p, 0, 0, 6, 6, 6)
    else:
        top = p.roof_hip(1, 6, 1, 5, 5, overhang=1, eave_curl=True)
        puncak(p, S, 3, top, 3)
    p.tahap = 5
    khk.interior("kuil_kecil", p, 0, 1, 0, 7, 7, 4, "south", pintu=3)
    for x in (1, 5):
        if kosong(p, x, 4, 6) and kosong(p, x, 5, 6):
            khk.lentera_gantung(p, x, 4, 6, "south", S["lentera"])
    p.titik("bangunan", 0, 1, 0, "south", {"dx": 6, "dy": min(H - 2, top) - 1, "dz": 6, "nama": "kota_tumbuh"})
    return {"pintu": 3, "top": top, "kind": kind}, ["kuil_kecil"]


def rencana_menara_jaga(p, S, gaya):
    """A watchtower: masonry base room, a middle floor, a ladder in the back, a railed lookout platform with a drum
    and a hip roof on four tall posts; jaga points face the street and the side."""
    p.tahap = 1
    for x in range(7):
        for z in range(7):
            blok(p, x, 0, z, "found" if 1 <= x <= 5 and 1 <= z <= 5 else "paving")
    for x, z in ring(0, 0, 6, 6):
        p.put(x, 1, z, AIR)
    posts = [(1, 1), (5, 1), (1, 5), (5, 5)]
    p.tahap = 2
    for (x, z) in posts:
        for y in range(1, 12):
            blok(p, x, y, z, "pillar" if y < 8 else "log")
    for y in (4, 8):
        for x, z in ring(1, 1, 5, 5):
            if (x, z) not in posts:
                balok(p, x, y, z, "beam", "x" if z in (1, 5) else "z")
    p.tahap = 3
    for x, z in ring(1, 1, 5, 5):
        if (x, z) in posts:
            continue
        for y in range(1, 4):
            if (x, z) == (3, 5) and y <= 2:
                p.put(x, y, z, AIR)
            else:
                blok(p, x, y, z, "dasar" if y == 1 else "wall")
        for y in range(5, 8):
            if z == 1:
                blok(p, x, y, z, "wall")
            elif y == 6 and (x == 3 or z == 3):
                K.lattice(p, x, y, z, sisi_dari(x, z, 1, 1, 5, 5))
            else:
                blok(p, x, y, z, "plaster")
    K.door(p, 3, 1, 5, "south")
    lantai(p, 2, 2, 4, 4, 4, "wood")
    for x in range(7):
        for z in range(7):
            if (x, z) not in posts and (x, z) != (3, 2):
                blok(p, x, 8, z, "wood")
    for x, z in ring(0, 0, 6, 6):
        p.put(x, 9, z, "fence")
    p.put(3, 4, 2, AIR)
    for y in range(1, 9):
        p.put(3, y, 2, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    # corner struts under the platform
    for (x, z), (dx, dz) in (((1, 1), (-1, -1)), ((5, 1), (1, -1)), ((1, 5), (-1, 1)), ((5, 5), (1, 1))):
        p.stairs(x + dx, 7, z, "wood_stairs", "east" if dx < 0 else "west", half="top")
        p.stairs(x, 7, z + dz, "wood_stairs", "south" if dz < 0 else "north", half="top")
    p.tahap = 4
    kind = S["atap"]
    if kind == "datar":
        top = datar(p, S, 0, 0, 6, 6, 12)
    elif kind == "jerami":
        top = jerami(p, 0, 0, 6, 6, 12)
    else:
        top = p.roof_hip(1, 12, 1, 5, 5, overhang=1, eave_curl=True)
        puncak(p, S, 3, top, 3)
    p.tahap = 5
    K.drum(p, 4, 9, 2, "south")
    p.put(2, 9, 4, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.put(2, 10, 4, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    p.titik("jaga", 3, 9, 4, "south")
    p.titik("jaga", 4, 9, 4, "east")
    p.titik("jaga", 2, 9, 2, "west")
    khk.panji_dinding(p, 6, 10, 3, "east", "panji", "jie")
    for x, z in ((0, 0), (6, 6)):
        if kosong(p, x, 11, z) and kosong(p, x, 10, z):
            khk.lentera_gantung(p, x, 10, z, "south", S["lentera"])
    p.titik("pintu", 3, 1, 6, "south")
    K.chest(p, 4, 5, 4, None, "west")
    p.put(4, 5, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    p.titik("bangunan", 1, 0, 1, "south", {"dx": 4, "dy": min(H - 1, top), "dz": 4, "nama": "kota_tumbuh"})
    return {"pintu": 3, "top": top, "kind": kind}, ["menara"]


TANAMAN = {
    "pine": ("minecraft:spruce_log", "minecraft:spruce_leaves"), "cherry": ("minecraft:cherry_log", "minecraft:cherry_leaves"),
    "willow": ("minecraft:oak_log", "minecraft:oak_leaves"), "bamboo": (None, None),
    "palm": ("minecraft:jungle_log", "minecraft:jungle_leaves"), "oak": ("minecraft:oak_log", "minecraft:oak_leaves"),
}


def pohon_kecil(p, gaya_pohon, x, z, y=1, tinggi=3):
    log, daun = TANAMAN[gaya_pohon]
    if log is None:
        for dx, dz in ((0, 0), (1, 0), (0, 1)):
            for yy in range(y, y + 3 + (dx + dz) % 2 + 1):
                p.put(x + dx, yy, z + dz, "minecraft:bamboo", {"age": "1", "leaves": "small" if yy < y + 2 else "large",
                                                              "stage": "0"})
        return
    for yy in range(y, y + tinggi):
        p.put(x, yy, z, log, {"axis": "y"})
    top = y + tinggi
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for dy in (0, 1):
                if dy == 1 and abs(dx) + abs(dz) == 2:
                    continue
                if (dx, dz, dy) == (0, 0, 0):
                    p.put(x, top, z, log, {"axis": "y"})
                    continue
                if p.inside(x + dx, top - 1 + dy + 1 - 1, z + dz):
                    p.put(x + dx, top + dy - (1 if gaya_pohon == "willow" and dy == 0 else 0), z + dz, daun,
                          {"distance": "1", "persistent": "true", "waterlogged": "false"})
    p.put(x, top + 2, z, daun, {"distance": "1", "persistent": "true", "waterlogged": "false"})
    if gaya_pohon == "willow":
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            sisi = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}[(dx, dz)]
            p.put(x + dx, top - 2, z + dz, "minecraft:vine", {sisi: "true"})


def rencana_taman(p, S, gaya):
    """A pocket garden: a 3 x 3 pavilion (ting) at the back with a stone chess table, flower beds, a rockery, a tree,
    a stone lantern and a bench along the street; catur, gantung_sangkar, obrol, kucing_tidur points."""
    tanah = "ground" if gaya != "kampung" else "minecraft:grass_block"
    p.tahap = 1
    for x in range(7):
        for z in range(7):
            jalan = x == 3 or z == 4 or (1 <= x <= 5 and 1 <= z <= 3)
            blok(p, x, 0, z, "paving" if jalan else (tanah if (x + z) % 4 else "ground_alt"))
    for x, z in ring(0, 0, 6, 6):
        p.put(x, 1, z, AIR)
    posts = [(2, 1), (4, 1), (2, 3), (4, 3)]
    p.tahap = 2
    for (x, z) in posts:
        for y in range(1, 4):
            blok(p, x, y, z, "pillar")
        p.put(x, 0, z, "kerb_chisel")
    for x in range(2, 5):
        for z in (1, 3):
            balok(p, x, 4, z, "beam", "x")
    for z in (2,):
        for x in (2, 4):
            balok(p, x, 4, z, "beam", "z")
    p.tahap = 3
    p.stairs(3, 1, 1, "wood_stairs", "north")          # a backrest seat (meirenkao) between the back posts
    for x, z in ((0, 0), (1, 0), (0, 1)):
        p.put(x, 1, z, "kerb_alt" if (x + z) % 2 else "kerb")
    p.put(0, 2, 0, "minecraft:mossy_cobblestone_wall" if gaya != "oasis" else "kerb_wall")
    for x in (0, 6):                                   # low kerb posts at the street corners
        p.slab(x, 1, 6, "kerb_slab")
    # bench along the street side
    for x in (4, 5):
        p.stairs(x, 1, 6, "wood_stairs", "south")
    p.tahap = 4
    kind = S["atap"]
    if kind == "datar":
        for x in range(1, 6):
            for z in range(0, 5):
                p.put(x, 5, z, "cloth" if (x + z) % 2 else "cloth_alt")
        top = 5
    elif kind == "jerami":
        top = jerami(p, 1, 0, 5, 4, 5)
    else:
        top = p.roof_hip(2, 5, 1, 4, 3, overhang=1, eave_curl=True)
        puncak(p, S, 3, top, 2)
    p.tahap = 5
    p.put(3, 1, 2, "minecraft:polished_andesite")
    p.slab(3, 2, 2, "minecraft:smooth_stone_slab")
    p.put(2, 1, 2, "minecraft:polished_andesite_slab", {"type": "bottom", "waterlogged": "false"})
    p.put(4, 1, 2, "minecraft:polished_andesite_slab", {"type": "bottom", "waterlogged": "false"})
    p.titik("catur", 2, 1, 2, "east", {"kursi": "a"})
    p.titik("catur", 4, 1, 2, "west", {"kursi": "b"})
    pohon_kecil(p, S["pohon"], 5, 5, 1, 3)
    for x, z, f in ((1, 5, None), (6, 3, None), (1, 4, None), (6, 1, None)):
        if kosong(p, x, 1, z):
            p.put(x, 1, z, S["pal"]["flower"] if (x + z) % 2 else S["pal"]["flower_alt"])
    for x, z in ((0, 4), (0, 3)):
        if kosong(p, x, 1, z):
            p.put(x, 1, z, S["pal"]["bush"])
    K.stone_lantern(p, 6, 1, 0)
    p.titik("gantung_sangkar", 4, 1, 4, "east")
    p.titik("obrol", 4, 1, 5, "south")
    p.titik("kucing_tidur", 1, 2, 0, "south")
    khk.lentera_gantung(p, 3, 2, 3, "south", S["lentera"], kait=False) if kosong(p, 3, 2, 3) else None
    return {"pintu": 3, "top": top, "kind": kind}, ["taman"]


RENCANA = {
    "rumah_kecil": (rencana_rumah_kecil, "rumah"), "kuil_kecil": (rencana_kuil_kecil, "ibadah"),
    "menara_jaga": (rencana_menara_jaga, "jaga"), "taman": (rencana_taman, "suasana"),
    "rumah_besar": (rencana_rumah_besar, "rumah"), "rumah_toko": (rencana_rumah_toko, "rumah,usaha"),
    "kedai_teh": (rencana_kedai_teh, "usaha"), "kedai_arak": (rencana_kedai_arak, "usaha"),
    "bengkel": (rencana_bengkel, "usaha"), "gudang": (rencana_gudang, "usaha"), "sekolah": (rencana_sekolah, "sekolah"),
}
assert set(RENCANA) == set(khk.JENIS_RENCANA)


# ===================================================================================================== build + data
def _seed(*a):
    return zlib.crc32("|".join(map(str, a)).encode()) & 0x7FFFFFFF


def bangun(gaya, jenis, batas):
    S = GAYA[gaya]
    w, d = khk.UKURAN_KELAS[khk.KELAS_JENIS[jenis]]
    p = Tahapan(w, d, _seed(gaya, jenis), S["pal"], batas)
    fn = RENCANA[jenis][0]
    info, kits = fn(p, S, gaya)
    p.tahap = 0
    khk.sambung(p)
    return p, info, kits


def kelas_bahan(bid, roof=(), tahap=0):
    """42.9.4 material of a block: roof ids count as genteng when the atap stage lays them, else by kind."""
    n = bid.split(":", 1)[1]
    if tahap == 4 and bid in roof:
        return "genteng"
    if "glass" in n:
        return "kaca_kertas"
    if n.endswith("_wool") or n.endswith("_carpet"):
        return "kain"
    woods = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo",
             "crimson", "warped")
    shapes = ("_planks", "_log", "_wood", "_stairs", "_slab", "_fence", "_fence_gate", "_trapdoor", "_door", "_stem",
              "_hyphae")
    if any(n.startswith(wd + "_") or n.startswith("stripped_" + wd + "_") for wd in woods) and \
            any(n.endswith(s) for s in shapes):
        return "kayu"
    if n == "ladder":
        return "kayu"
    stones = ("stone", "brick", "terracotta", "sandstone", "deepslate", "andesite", "diorite", "granite", "cobble",
              "mud", "tuff", "calcite", "quartz", "blackstone", "prismarine", "concrete", "basalt")
    if any(s in n for s in stones) and not n.startswith("potted") and n not in ("stonecutter", "grindstone",
                                                                               "brewing_stand"):
        return "batu"
    return None


def keadaan(p):
    """{pos: (bid, props)} of the non-air cells."""
    return {pos: cell[0] for pos, cell in p.cells.items() if cell[0][0] != AIR}


def buat(out_root):
    """Builds every plan into out_root/<gaya>/<jenis>/tahap_<n>.nbt and returns (rencana json, report rows, errors)."""
    blocks = structgen._load_block_list()
    errs, warns, rows = [], [], []
    rencana = {}
    palet_bahan = {}
    total_gz = 0
    for gaya in khk.GAYA_KAVLING:
        S = GAYA[gaya]
        roof = atap_ids(S)
        for jenis in khk.JENIS_RENCANA:
            key = "%s/%s" % (gaya, jenis)
            w, d = khk.UKURAN_KELAS[khk.KELAS_JENIS[jenis]]
            stages, prev = [], {}
            tahap_data, bahan_total = [], {k: 0 for k in SATUAN}
            info = kits = None
            p5 = None
            for n in range(1, 6):
                p, info, kits = bangun(gaya, jenis, n)
                where = "kota_tumbuh/%s/tahap_%d" % (key, n)
                e, w_ = structgen.validate(p, where, blocks)
                errs.extend(e)
                warns.extend(w_)
                st = keadaan(p)
                # cumulative: nothing of the previous stage disappears
                hilang = [pos for pos in prev if pos not in st]
                if hilang:
                    errs.append("%s: %d blocks of stage %d vanish (e.g. %s)" % (where, len(hilang), n - 1, hilang[0]))
                baru = {pos: c for pos, c in st.items() if prev.get(pos) != c}
                if not baru:
                    errs.append("%s: stage adds nothing" % where)
                hit = {k: 0 for k in SATUAN}
                for (bid, _props) in baru.values():
                    kb = kelas_bahan(bid, roof, n)
                    palet_bahan[bid] = kelas_bahan(bid)
                    if kb:
                        hit[kb] += 1
                sat = {k: int(math.ceil(v / float(SATUAN[k]))) for k, v in hit.items() if v}
                for k, v in sat.items():
                    bahan_total[k] += v
                for (bid, props), _ in p.cells.values():
                    if bid in khk._DILARANG:
                        errs.append("%s: forbidden %s" % (where, bid))
                    if bid in ("minecraft:campfire", "minecraft:soul_campfire") and dict(props).get("lit") == "true":
                        errs.append("%s: lit campfire" % where)
                if n < 5 and p.titik_list:
                    errs.append("%s: life points before stage 5" % where)
                if p.entities:
                    errs.append("%s: entities in a growth template" % where)
                if tuple(p.size) != (w, H, d):
                    errs.append("%s: size %s != %s" % (where, p.size, (w, H, d)))
                path = os.path.join(out_root, gaya, jenis, "tahap_%d.nbt" % n)
                os.makedirs(os.path.dirname(path), exist_ok=True)
                p.save(path)
                total_gz += os.path.getsize(path)
                tahap_data.append({"tahap": n, "nama": TAHAP[n - 1], "template": NS + "%s/tahap_%d" % (key, n),
                                   "blok_baru": len(baru), "blok": len(st), "bahan": sat,
                                   "satuan": sum(sat.values())})
                prev = st
                p5 = p
            # stage-5 checks: points, door, height
            jt = {}
            for t in p5.titik_list:
                if not khk.titik_sah(t["jenis"]):
                    errs.append("%s: illegal titik %s" % (key, t["jenis"]))
                if t["jenis"] in ("bangunan", "kavling", "panggung") and not all(k in t.get("data", {})
                                                                               for k in ("dx", "dy", "dz")):
                    errs.append("%s: box titik without size" % key)
                j = t["jenis"]
                jt[j] = jt.get(j, 0) + 1
            if len(p5.titik_list) > 96:
                errs.append("%s: %d titik > 96" % (key, len(p5.titik_list)))
            top = max(y for (_x, y, _z) in keadaan(p5))
            if top >= H:
                errs.append("%s: block above the height limit" % key)
            pin = [t for t in p5.titik_list if t["jenis"] == "pintu"]
            for t in pin:
                x, y, z = t["pos"]
                if not (kosong(p5, x, y, z) or p5.get(x, y, z).endswith("_slab")) or not kosong(p5, x, y + 1, z):
                    errs.append("%s: door point %s blocked (%s)" % (key, t["pos"], p5.get(x, y, z)))
            if p5.clipped:
                warns.append("%s: %d clipped writes" % (key, p5.clipped))
            kasur = jt.get("kasur", 0)
            kerja = sum(v for k, v in jt.items() if k.startswith("kerja:") or k in ("lapak", "mengajar", "jaga"))
            rt = {"rumah_kecil": 1, "rumah_besar": 2, "rumah_toko": 1}.get(jenis, 0)
            rencana[key] = {
                "gaya": gaya, "jenis": jenis, "kelas": khk.KELAS_JENIS[jenis], "ukuran": [w, H, d], "hadap": "south",
                "tinggi": top + 1, "kebutuhan": RENCANA[jenis][1].split(","), "interior": kits,
                "rumah_tangga": rt, "kasur": kasur, "lapangan_kerja": kerja,
                "tahap": tahap_data, "bahan_total": {k: v for k, v in bahan_total.items() if v},
                "satuan_total": sum(bahan_total.values()), "titik": dict(sorted(jt.items())),
                "pintu": pin[0]["pos"] if pin else None, "atap": info.get("kind"),
            }
            rows.append("%-34s h%2d  blok %4d  satuan %3d  titik %2d  kasur %d  kerja %d" % (
                key, top + 1, len(keadaan(p5)), rencana[key]["satuan_total"], len(p5.titik_list), kasur, kerja))
    data = {
        "versi": 1,
        "keterangan": "DESIGN 42.9.4 growth plans (tools/gen_kota_tumbuh.py); front faces south, rotate with "
                      "kota_hidup_kit.rotasi_rencana(hadap); stages are cumulative; bahan = units of 42.9.4 needed by the "
                      "blocks a stage adds (genteng = the ids of atap_genteng[gaya] laid by stage 4 atap; palet_bahan = the "
                      "material of every other block, blocks not listed cost nothing)",
        "satuan_blok": SATUAN, "tahap": list(TAHAP), "kelas": {k: list(v) for k, v in khk.UKURAN_KELAS.items()},
        "tinggi_maks": H, "gaya": list(khk.GAYA_KAVLING), "jenis": list(khk.JENIS_RENCANA),
        "palet_bahan": dict(sorted((k, v) for k, v in palet_bahan.items() if v)),
        "atap_genteng": {g: sorted(atap_ids(GAYA[g])) for g in khk.GAYA_KAVLING},
        "rencana": rencana,
    }
    if total_gz > ANGGARAN:
        errs.append("kota_tumbuh: %d gzip bytes > budget %d" % (total_gz, ANGGARAN))
    return data, rows, errs, sorted(set(warns)), total_gz


def tulis_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1, sort_keys=False)
        f.write("\n")


def main(argv):
    uji = len(argv) >= 3 and argv[1] == "--uji"
    out = os.path.abspath(argv[2]) if uji else OUT
    if uji:
        repo_struct = os.path.abspath(os.path.join(structgen.DATA, "structure"))
        if out.startswith(repo_struct):
            raise SystemExit("--uji DIR must be outside the repository's structure folder")
    data, rows, errs, warns, gz = buat(out)
    tulis_json(os.path.join(out, "rencana.json"), data)
    for w in warns:
        print("warning:", w)
    for r in rows:
        print(r)
    for e in errs:
        print("ERROR:", e)
    print("gen_kota_tumbuh: %d plans x 5 stages, %d gzip bytes (budget %d), %d errors"
          % (len(data["rencana"]), gz, ANGGARAN, len(errs)))
    return 0 if not errs else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
