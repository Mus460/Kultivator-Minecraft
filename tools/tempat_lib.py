"""Shared library of every DESIGN 38 place generator (38W, frozen API since wave W0; later changes are additive only).

Built on tools/structgen.py (Piece, validate_all) and tools/nbt.py (Template.titik / Template.sisi). Used by
gen_tempat_jalan.py (38W), gen_tempat_kota_besar.py (38KS), gen_tempat_kampung.py + gen_tempat_kompleks.py (38DS),
gen_tempat_gua.py (38GU), gen_dunia_kota.py (38DS, titik only) and, through tempat_tema_lib.py, the themed cities (38TK).

    import os, sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tempat_lib as tl

    lot = tl.Lot("kota_besar", "pasar/kios_a", tinggi=24, palet="kota_besar")
    lot.fondasi()                                   # 4 foundation layers, floor at y = tl.LANTAI_Y
    lot.jalan_tepi("nesw")                          # 3-block edge road, so neighbouring lots meet in a 6-block street
    ... build with the structgen.Piece helpers (walls, roof_hip, lantern, ...) at y >= tl.LANTAI_Y ...
    lot.titik("kasur", 10, tl.LANTAI_Y + 1, 12, "east")
    lot.titik("kerja:timbangan", 20, tl.LANTAI_Y + 1, 8, "south")
    lot.kotak("bangunan", 6, tl.LANTAI_Y, 6, 30, tl.LANTAI_Y + 12, 28, nama="kios_a")
    lot.jalur([(1, 24), (24, 24), (46, 24)])         # path nodes "jalan" (y = floor + 1)
    lot.jangkar(24.5, tl.LANTAI_Y + 1, 24.5, "kotabesar/layanan", layanan="rumah_makan", peran="KOKI")
    lot.simpan()                                    # data/kultivator/structure/kota_besar/pasar/kios_a.nbt
    tl.tulis_processor("kota_besar/gaya_salju", tl.processor_gaya("kota_besar", "salju", tl.semua_state()))
    tl.cek_semua()                                  # structgen.validate_all + the 38 lot rules below

Coordinates are template-local (x east, y up, z south, (0,0,0) = min corner), inclusive bounds like structgen.
Lot rules (DESIGN 38.B.2.5, 38.C.10, 38.D.2): <= 48 x 48, floor at y = LANTAI_Y over LANTAI_Y foundation layers, edge
road TEPI blocks, <= MAKS_TITIK points, <= MAKS_JANGKAR anchors; anchors only for services and sites (residents use
points). Only worldgen places these templates; runtime code never calls setBlock for them (DESIGN 38.0 rule 1).
"""
import json
import os
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
from structgen import PALETTES, Piece, out_path, json_path, write_json, single, pool  # noqa: E402,F401

# ------------------------------------------------------------------------------------------------------- constants
LOT = 48            # lot edge (blocks); every tata_letak lot is LOT x H x LOT
LANTAI_Y = 4        # street floor level inside a lot (4 foundation layers below)
TEPI = 3            # edge road width per lot (two lots -> 6-block street)
MAKS_TITIK = 96     # points per template (38.C.10)
MAKS_JANGKAR = 6    # anchors per template (34.2.4 / 38.B.2.5)
MAKS_JANGKAR_DISTRIK = 40
KODE_SISI = ("J", "K", "T", "A", "G", "B")   # 38.T.2.4: road, canal, wall, open water, stairs, free border
SISI = ("n", "e", "s", "w")

# Tools the "kerja:<alat>" points name (38.B.2.5); the NPC clip and held prop follow the tool (38.N, 40).
ALAT = ("palu", "tungku", "lumpang", "teko", "meja_tulis", "rak", "kios", "sapu", "cangkul", "pancing", "kuali",
        "timbangan", "dupa", "tenun", "dayung", "jala", "kuas", "sabit", "gergaji", "pahat", "lesung", "kipas")

# Every legal point type ("kerja:<alat>" is legal for each ALAT entry). Core list 38.B.2.5, themed list 38.T.2.4.
JENIS_TITIK = (
    # core
    "kasur", "kursi", "kursi_lantai", "meja_makan", "pintu", "jalan", "main", "obrol", "jaga", "latihan", "meditasi",
    "lapak", "peti_milik", "bangunan", "meja_tulis", "dupa", "jalan_lentera", "panggung", "penonton", "tiba",
    # themed cities
    "tambatan", "jalur_kapal", "jalur_perahu", "stasiun_lift", "jalur_tangga", "kanal", "lentera_air", "kandang_unta",
    "sumur_timba", "alat_tenun", "arena_pintu", "sel_tawanan", "zona_perjanjian", "tandu", "pandang", "jaga_suar",
)
# Point types that describe a box (data dx, dy, dz = size - 1); the others are single blocks.
TITIK_KOTAK = ("bangunan", "kanal", "zona_perjanjian", "panggung")


def titik_sah(jenis):
    """True when jenis is a legal point type (core, themed or kerja:<alat>)."""
    if jenis.startswith("kerja:"):
        return jenis[6:] in ALAT
    return jenis in JENIS_TITIK


# ------------------------------------------------------------------------------------------------------- palettes
# Role names follow structgen ("floor", "wall", "pillar", "trim", "roof", "roof_slab", "roof_full", "accent", "jade",
# "glow", "path", "path_alt", "fence", "window", "carpet") plus "plaster" and "found" (foundation) used by 38 lots.
PALET = {
    # Kota Besar (38.B.2.5 dasar): dark oak roofs, stone brick + white plaster walls, red lacquer pillars, jade accents
    "kota_besar": {
        "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:stone_bricks", "found": "minecraft:stone_bricks",
        "wall": "minecraft:stone_bricks", "wall_alt": "minecraft:dark_oak_planks", "plaster": "minecraft:white_terracotta",
        "pillar": "minecraft:red_terracotta", "trim": "minecraft:dark_oak_log", "roof": "minecraft:dark_oak_stairs",
        "roof_slab": "minecraft:dark_oak_slab", "roof_full": "minecraft:dark_oak_planks", "accent": "minecraft:gold_block",
        "jade": "minecraft:prismarine_bricks", "glow": "minecraft:lantern", "path": "minecraft:polished_andesite",
        "path_alt": "minecraft:stone_bricks", "fence": "minecraft:dark_oak_fence", "window": "minecraft:dark_oak_trapdoor",
        "carpet": "minecraft:red_carpet", "imperial": "minecraft:smooth_quartz",
    },
    # Kampung fana (38.C.2.1 sedang): oak + plaster, thatch roofs, dirt roads
    "kampung": {
        "floor": "minecraft:oak_planks", "floor_alt": "minecraft:packed_mud", "found": "minecraft:cobblestone",
        "wall": "minecraft:oak_planks", "wall_alt": "minecraft:stripped_oak_log", "plaster": "minecraft:white_terracotta",
        "pillar": "minecraft:oak_log", "trim": "minecraft:stripped_oak_log", "roof": "minecraft:oak_stairs",
        "roof_slab": "minecraft:oak_slab", "roof_full": "minecraft:hay_block", "accent": "minecraft:red_terracotta",
        "jade": "minecraft:moss_block", "glow": "minecraft:lantern", "path": "minecraft:dirt_path",
        "path_alt": "minecraft:coarse_dirt", "fence": "minecraft:oak_fence", "window": "minecraft:oak_trapdoor",
        "carpet": "minecraft:brown_carpet",
    },
    # Desa Klan (38.C.2.2): walled, brick, dark wood, red pillars
    "klan": {
        "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:mud_bricks", "found": "minecraft:stone_bricks",
        "wall": "minecraft:bricks", "wall_alt": "minecraft:spruce_planks", "plaster": "minecraft:white_terracotta",
        "pillar": "minecraft:red_terracotta", "trim": "minecraft:spruce_log", "roof": "minecraft:deepslate_tile_stairs",
        "roof_slab": "minecraft:deepslate_tile_slab", "roof_full": "minecraft:deepslate_tiles",
        "accent": "minecraft:gold_block", "jade": "minecraft:prismarine_bricks", "glow": "minecraft:lantern",
        "path": "minecraft:stone_bricks", "path_alt": "minecraft:cracked_stone_bricks", "fence": "minecraft:spruce_fence",
        "window": "minecraft:spruce_trapdoor", "carpet": "minecraft:red_carpet",
    },
    # Kompleks sekte Lurus (38.C.3.2): white, jade, blue
    "kompleks_lurus": {
        "floor": "minecraft:smooth_quartz", "floor_alt": "minecraft:quartz_bricks", "found": "minecraft:stone_bricks",
        "wall": "minecraft:quartz_bricks", "wall_alt": "minecraft:birch_planks", "plaster": "minecraft:calcite",
        "pillar": "minecraft:quartz_pillar", "trim": "minecraft:light_blue_terracotta", "roof": "minecraft:prismarine_brick_stairs",
        "roof_slab": "minecraft:prismarine_brick_slab", "roof_full": "minecraft:prismarine_bricks",
        "accent": "minecraft:lapis_block", "jade": "minecraft:prismarine", "glow": "minecraft:sea_lantern",
        "path": "minecraft:smooth_quartz", "path_alt": "minecraft:polished_diorite", "fence": "minecraft:birch_fence",
        "window": "minecraft:white_stained_glass_pane", "carpet": "minecraft:light_blue_carpet",
    },
    # Kompleks sekte Netral: wood and gold
    "kompleks_netral": {
        "floor": "minecraft:spruce_planks", "floor_alt": "minecraft:polished_andesite", "found": "minecraft:stone_bricks",
        "wall": "minecraft:spruce_planks", "wall_alt": "minecraft:stripped_spruce_log", "plaster": "minecraft:yellow_terracotta",
        "pillar": "minecraft:spruce_log", "trim": "minecraft:stripped_spruce_log", "roof": "minecraft:spruce_stairs",
        "roof_slab": "minecraft:spruce_slab", "roof_full": "minecraft:spruce_planks", "accent": "minecraft:gold_block",
        "jade": "minecraft:moss_block", "glow": "minecraft:lantern", "path": "minecraft:polished_andesite",
        "path_alt": "minecraft:andesite", "fence": "minecraft:spruce_fence", "window": "minecraft:spruce_trapdoor",
        "carpet": "minecraft:yellow_carpet",
    },
    # Kompleks sekte Iblis: black and red, chains, soul light (no fire blocks in templates)
    "kompleks_iblis": {
        "floor": "minecraft:polished_blackstone", "floor_alt": "minecraft:polished_blackstone_bricks",
        "found": "minecraft:blackstone", "wall": "minecraft:polished_blackstone_bricks", "wall_alt": "minecraft:dark_oak_planks",
        "plaster": "minecraft:black_terracotta", "pillar": "minecraft:red_nether_bricks", "trim": "minecraft:crimson_hyphae",
        "roof": "minecraft:blackstone_stairs", "roof_slab": "minecraft:blackstone_slab", "roof_full": "minecraft:blackstone",
        "accent": "minecraft:crying_obsidian", "jade": "minecraft:obsidian", "glow": "minecraft:soul_lantern",
        "path": "minecraft:polished_basalt", "path_alt": "minecraft:basalt", "fence": "minecraft:iron_chain",
        "window": "minecraft:iron_bars", "carpet": "minecraft:black_carpet",
    },
    # Gua (38.C.4): rough stone, moss, amethyst light
    "gua": {
        "floor": "minecraft:cobbled_deepslate", "floor_alt": "minecraft:mossy_cobblestone", "found": "minecraft:deepslate",
        "wall": "minecraft:tuff_bricks", "wall_alt": "minecraft:mossy_stone_bricks", "plaster": "minecraft:calcite",
        "pillar": "minecraft:chiseled_tuff_bricks", "trim": "minecraft:polished_tuff", "roof": "minecraft:tuff_brick_stairs",
        "roof_slab": "minecraft:tuff_brick_slab", "roof_full": "minecraft:tuff_bricks", "accent": "minecraft:amethyst_block",
        "jade": "minecraft:oxidized_cut_copper", "glow": "minecraft:amethyst_cluster", "path": "minecraft:cobbled_deepslate",
        "path_alt": "minecraft:mossy_cobblestone", "fence": "minecraft:tuff_wall", "window": "minecraft:iron_bars",
        "carpet": "minecraft:moss_carpet",
    },
    # Jalan & pos (38.C.6): per road class (imperial/desa/setapak) through role names
    "jalan": {
        "floor": "minecraft:stone_bricks", "floor_alt": "minecraft:andesite", "found": "minecraft:cobblestone",
        "wall": "minecraft:spruce_planks", "wall_alt": "minecraft:stripped_spruce_log", "plaster": "minecraft:white_terracotta",
        "pillar": "minecraft:spruce_log", "trim": "minecraft:stripped_spruce_log", "roof": "minecraft:dark_oak_stairs",
        "roof_slab": "minecraft:dark_oak_slab", "roof_full": "minecraft:dark_oak_planks", "accent": "minecraft:red_terracotta",
        "jade": "minecraft:chiseled_stone_bricks", "glow": "minecraft:lantern", "path": "minecraft:stone_bricks",
        "path_alt": "minecraft:andesite", "fence": "minecraft:spruce_fence", "window": "minecraft:spruce_trapdoor",
        "carpet": "minecraft:red_carpet",
        "kekaisaran": "minecraft:stone_bricks", "kekaisaran_tepi": "minecraft:polished_andesite",
        "desa": "minecraft:dirt_path", "desa_tepi": "minecraft:coarse_dirt", "desa_alt": "minecraft:gravel",
        "setapak": "minecraft:dirt_path", "setapak_alt": "minecraft:rooted_dirt",
    },
}

# ------------------------------------------------------------------------------------------------ biome styles (gaya)
# A style keeps the SAME NBT and swaps blocks through a processor (38.B.2.5): each entry gives the biome tag of the
# style and a block-id swap map. Wood families swap every member (planks, stairs, slab, log, ...); properties are kept
# because processor_gaya emits one exact-state rule per state found in the templates. "dasar" = the base, no swaps.
_KAYU = ("_planks", "_stairs", "_slab", "_fence", "_fence_gate", "_log", "_wood", "_door", "_trapdoor", "_button",
         "_pressure_plate", "_sign", "_wall_sign", "_hanging_sign", "_wall_hanging_sign")


def ganti_kayu(dari, ke):
    """Swap map for a whole wood family (oak, spruce, dark_oak, acacia, jungle, cherry, mangrove, birch, pale_oak)."""
    m = {}
    for s in _KAYU:
        m["minecraft:" + dari + s] = "minecraft:" + ke + s
    m["minecraft:stripped_%s_log" % dari] = "minecraft:stripped_%s_log" % ke
    m["minecraft:stripped_%s_wood" % dari] = "minecraft:stripped_%s_wood" % ke
    return m


def _gabung(*maps):
    out = {}
    for m in maps:
        out.update(m)
    return out


GAYA = {
    "kota_besar": {
        "dasar": {"tag": None, "ganti": {}},
        "salju": {"tag": "#kultivator:kota_besar/gaya_salju", "ganti": ganti_kayu("dark_oak", "spruce")},
        "gurun": {"tag": "#kultivator:kota_besar/gaya_gurun", "ganti": _gabung(ganti_kayu("dark_oak", "jungle"), {
            "minecraft:stone_bricks": "minecraft:cut_sandstone", "minecraft:white_terracotta": "minecraft:smooth_sandstone",
            "minecraft:stone_brick_stairs": "minecraft:sandstone_stairs", "minecraft:stone_brick_slab": "minecraft:sandstone_slab",
            "minecraft:stone_brick_wall": "minecraft:sandstone_wall"})},
        "sakura": {"tag": "#kultivator:kota_besar/gaya_sakura", "ganti": _gabung(ganti_kayu("dark_oak", "cherry"), {
            "minecraft:red_terracotta": "minecraft:pink_terracotta"})},
    },
    "kampung": {
        "sedang": {"tag": "#kultivator:has_structure/kampung/sedang", "ganti": {}},
        "salju": {"tag": "#kultivator:has_structure/kampung/salju", "ganti": _gabung(ganti_kayu("oak", "spruce"), {
            "minecraft:hay_block": "minecraft:spruce_planks"})},
        "sabana": {"tag": "#kultivator:has_structure/kampung/sabana", "ganti": _gabung(ganti_kayu("oak", "acacia"), {
            "minecraft:white_terracotta": "minecraft:terracotta"})},
        "sakura": {"tag": "#kultivator:has_structure/kampung/sakura", "ganti": ganti_kayu("oak", "cherry")},
        "gurun": {"tag": "#kultivator:has_structure/kampung/gurun", "ganti": _gabung(ganti_kayu("oak", "jungle"), {
            "minecraft:white_terracotta": "minecraft:smooth_sandstone", "minecraft:cobblestone": "minecraft:sandstone",
            "minecraft:packed_mud": "minecraft:smooth_sandstone", "minecraft:hay_block": "minecraft:cut_sandstone"})},
        "rawa": {"tag": "#kultivator:has_structure/kampung/rawa", "ganti": ganti_kayu("oak", "mangrove")},
        "terasering": {"tag": "#kultivator:has_structure/kampung/terasering", "ganti": {
            "minecraft:oak_planks": "minecraft:cobblestone", "minecraft:white_terracotta": "minecraft:stone_bricks"}},
    },
    "klan": {
        "sedang": {"tag": "#kultivator:has_structure/kampung/klan_sedang", "ganti": {}},
        "salju": {"tag": "#kultivator:has_structure/kampung/klan_salju", "ganti": ganti_kayu("spruce", "dark_oak")},
        "sakura": {"tag": "#kultivator:has_structure/kampung/klan_sakura", "ganti": _gabung(ganti_kayu("spruce", "cherry"), {
            "minecraft:red_terracotta": "minecraft:pink_terracotta"})},
        "gunung": {"tag": "#kultivator:has_structure/kampung/klan_gunung", "ganti": {
            "minecraft:bricks": "minecraft:stone_bricks", "minecraft:mud_bricks": "minecraft:andesite"}},
    },
}

# Light weathering rules per family (38.B.2.5 lapuk_ringan: moss 4 %, cracks 3 %); applied by processor_gaya(lapuk=True).
LAPUK_RINGAN = (
    ("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.04),
    ("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.03),
    ("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.06),
    ("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.03),
    ("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.03),
    ("minecraft:white_terracotta", "minecraft:light_gray_terracotta", 0.02),
)

SPAWN_KOSONG = {"monster": {"bounding_box": "full", "spawns": []}}


# ---------------------------------------------------------------------------------------------------------- the lot
def _seed(nama):
    return zlib.crc32(nama.encode("utf-8")) & 0x7FFFFFFF


class Lot(Piece):
    """One 38 template (lot, piece, tile). folder = structure folder ("kota_besar", "kampung/sedang", "jalan", ...),
    nama = path inside it. Same name -> same seed -> same bytes."""

    def __init__(self, folder, nama, tinggi=32, lebar=LOT, dalam=LOT, palet="kota_besar", seed=None):
        super().__init__(lebar, tinggi, dalam, seed=_seed(folder + "/" + nama) if seed is None else seed,
                         palette=PALET[palet] if isinstance(palet, str) else palet)
        self.folder = folder
        self.nama = nama
        self.jangkar_n = 0

    @property
    def id(self):
        """The template id kultivator:<folder>/<nama> (pool "location")."""
        return "kultivator:%s/%s" % (self.folder, self.nama)

    # ------------------------------------------------------------------------------------------------ ground
    def fondasi(self, role="found", lantai="floor", alt="floor_alt", alt_chance=0.12):
        """LANTAI_Y foundation layers under the whole lot and the street floor at y = LANTAI_Y."""
        sx, _, sz = self.size
        self.fill(0, 0, 0, sx - 1, LANTAI_Y - 1, sz - 1, role)
        self.floor(0, LANTAI_Y, 0, sx - 1, sz - 1, lantai, alt=alt, alt_chance=alt_chance)

    def jalan_tepi(self, sisi="nesw", role="path", alt="path_alt", alt_chance=0.2, lebar=TEPI, talang=True):
        """Edge road `lebar` wide on the named sides (n/e/s/w) at y = LANTAI_Y, with a gutter line (talang) of
        slabs on its inner edge. Two neighbouring lots together form a 6-block street (38.B.2.5)."""
        sx, _, sz = self.size
        y = LANTAI_Y
        for c in sisi:
            if c == "n":
                box = (0, 0, sx - 1, lebar - 1)
            elif c == "s":
                box = (0, sz - lebar, sx - 1, sz - 1)
            elif c == "w":
                box = (0, 0, lebar - 1, sz - 1)
            elif c == "e":
                box = (sx - lebar, 0, sx - 1, sz - 1)
            else:
                raise ValueError("side %r not in nesw" % c)
            self.floor(box[0], y, box[1], box[2], box[3], role, alt=alt, alt_chance=alt_chance)
        if talang:
            for c in sisi:
                if c in "ns":
                    z = lebar if c == "n" else sz - lebar - 1
                    for x in range(lebar, sx - lebar):
                        if self.get(x, y + 1, z) is None:
                            self.put(x, y, z, "minecraft:smooth_stone")
                else:
                    x = lebar if c == "w" else sx - lebar - 1
                    for z in range(lebar, sz - lebar):
                        if self.get(x, y + 1, z) is None:
                            self.put(x, y, z, "minecraft:smooth_stone")

    # ------------------------------------------------------------------------------------------------ points
    def kotak(self, jenis, x0, y0, z0, x1, y1, z1, hadap="south", **data):
        """A box point (bangunan, kanal, zona_perjanjian, panggung): min corner + data dx/dy/dz (size - 1)."""
        if jenis not in TITIK_KOTAK:
            raise ValueError("%r is not a box point type" % jenis)
        lo = (min(x0, x1), min(y0, y1), min(z0, z1))
        hi = (max(x0, x1), max(y0, y1), max(z0, z1))
        d = {"dx": hi[0] - lo[0], "dy": hi[1] - lo[1], "dz": hi[2] - lo[2]}
        d.update(data)
        return self.titik(jenis, lo[0], lo[1], lo[2], hadap, d)

    def jalur(self, titik_xz, jalur="a", y=None):
        """Path nodes ("jalan" points) in order; y defaults to the floor + 1 (standing level)."""
        yy = LANTAI_Y + 1 if y is None else y
        out = []
        for i, p in enumerate(titik_xz):
            x, z = p[0], p[1]
            py = p[2] if len(p) > 2 else yy
            out.append(self.titik("jalan", x, py, z, "south", {"urut": i, "jalur": jalur}))
        return out

    def titik(self, jenis, x, y, z, hadap="south", data=None):
        if not titik_sah(jenis):
            raise ValueError("unknown titik type %r (tempat_lib.JENIS_TITIK / ALAT)" % jenis)
        return super().titik(jenis, x, y, z, hadap, data)

    def jangkar(self, x, y, z, kind, **data):
        """A service/site anchor (DuniaAnchor entity). At most MAKS_JANGKAR per template."""
        if self.jangkar_n >= MAKS_JANGKAR:
            raise ValueError("%s: more than %d anchors in one template" % (self.id, MAKS_JANGKAR))
        self.jangkar_n += 1
        self.anchor(x, y, z, kind, **{k: str(v) for k, v in data.items()})

    # ------------------------------------------------------------------------------------------------ save
    def simpan(self):
        return self.save(out_path(self.folder, self.nama))


# -------------------------------------------------------------------------------------------------- JSON builders
def elemen(template_id, processors="minecraft:empty", projection="rigid"):
    """A single_pool_element (projection "terrain_matching" for road tiles)."""
    return single(template_id, processors=processors, projection=projection)


def tulis_pool(nama, elemen_bobot, fallback="minecraft:empty"):
    """data/kultivator/worldgen/template_pool/<nama>.json; elemen_bobot = [(element_dict, weight)]."""
    return write_json(_wg("template_pool", nama), pool(nama, elemen_bobot, fallback))


def tulis_pool_gaya(nama, template_ids, family, processor_dasar, bobot=None):
    """One pool per style: <nama> (base, processor_dasar) and <nama>_<gaya> with processor <family>/gaya_<gaya>
    (TataLetakStructure picks <pool>_<akhiran> when it exists, 38.B.2.3). Returns the written paths."""
    bobot = bobot or [1] * len(template_ids)
    paths = [tulis_pool(nama, [(elemen(t, processor_dasar), w) for t, w in zip(template_ids, bobot)])]
    for gaya, spec in GAYA[family].items():
        if spec["tag"] is None:
            continue
        proc = "kultivator:%s/gaya_%s" % (family, gaya)
        paths.append(tulis_pool("%s_%s" % (nama, gaya), [(elemen(t, proc), w) for t, w in zip(template_ids, bobot)]))
    return paths


def _wg(kind, nama):
    return os.path.join(structgen.DATA, "worldgen", kind, nama + ".json")


def processor_gaya(family, gaya, states, lapuk=True, lindungi=True):
    """A processor_list for one style: an exact-state rule for every (block, properties) in `states` whose block the
    style swaps (properties are kept), the light weathering rules, and protected_blocks #kultivator:sect_protected.
    `states` = iterable of (block_id, ((prop, value), ...)) such as semua_state() returns."""
    ganti = GAYA[family][gaya]["ganti"] if gaya in GAYA.get(family, {}) else {}
    rules = []
    for bid, props in sorted(set(states)):
        to = ganti.get(bid)
        if to is None:
            continue
        inp = {"id": bid}
        out = {"id": to}
        if props:
            inp["properties"] = {k: v for k, v in props}
            out["properties"] = {k: v for k, v in props}
        rules.append({"input_predicate": {"predicate_type": "minecraft:blockstate_match", "block_state": inp},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": out})
    if lapuk:
        for src, dst, p in LAPUK_RINGAN:
            src2 = ganti.get(src, src)
            dst2 = ganti.get(dst, dst)
            rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": src2,
                                              "probability": p},
                          "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": dst2}})
    procs = []
    if rules:
        procs.append({"processor_type": "minecraft:rule", "rules": rules})
    if lindungi:
        procs.append({"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"})
    return {"processors": procs}


def processor_utuh():
    """No weathering, protected blocks kept (palaces, gates, shrines)."""
    return {"processors": [{"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}]}


def tulis_processor(nama, obj):
    return write_json(_wg("processor_list", nama), obj)


def semua_state(templates=None):
    """Every (block, props) state used by the given templates (default: every template saved in this run)."""
    ts = templates if templates is not None else [t for _, t in structgen._SAVED]
    out = set()
    for t in ts:
        for (key, _nbt) in t.cells.values():
            out.add(key)
    return out


def struktur_tata_letak(rencana, distrik, lot, biomes, gaya=(), adaptasi="beard_box", step="surface_structures",
                        spawn=None):
    """A kultivator:tata_letak structure (38.B.2.3). lot = [{"dx", "dz", "dy"?, "pool", "putar"}], dx/dz from the
    district centre to the lot's north-west corner before rotation; gaya = [(biome_tag, akhiran)]."""
    return {
        "type": "kultivator:tata_letak",
        "biomes": biomes,
        "step": step,
        "terrain_adaptation": adaptasi,
        "spawn_overrides": SPAWN_KOSONG if spawn is None else spawn,
        "rencana": rencana,
        "distrik": distrik,
        "lot": [dict(l) for l in lot],
        "gaya": [{"biomes": b, "akhiran": a} for b, a in gaya],
    }


def set_rencana(struktur, rencana, distrik, salt, spacing=256):
    """A structure_set on kultivator:rencana_tempat (start only where the plan says)."""
    return {"placement": {"type": "kultivator:rencana_tempat", "salt": salt, "spacing": spacing, "separation": 0,
                          "rencana": rencana, "distrik": distrik},
            "structures": [{"structure": struktur, "weight": 1}]}


def set_cincin(structures, spacing, separation, salt, min_chunks=6, cincin=None, jarak_tempat=8, exclusion=None,
               frequency=None):
    """A structure_set on kultivator:cincin_tempat (38.D.1). cincin = [(sampai_chunk, frekuensi)] by distance ring
    (the last ring's frequency applies beyond its limit); structures = [(id, weight)]."""
    pl = {"type": "kultivator:cincin_tempat", "salt": salt, "spacing": spacing, "separation": separation,
          "min_chunks": min_chunks, "jarak_tempat": jarak_tempat,
          "cincin": [{"sampai_chunk": a, "frekuensi": f} for a, f in (cincin or [])]}
    if frequency is not None:
        pl["frequency"] = frequency
    if exclusion:
        pl["exclusion_zone"] = {"other_set": exclusion[0], "chunk_count": exclusion[1]}
    return {"placement": pl, "structures": [{"structure": s, "weight": w} for s, w in structures]}


def set_jalan(struktur, salt):
    """The road network set on kultivator:jalan (38.C.6.2)."""
    return {"placement": {"type": "kultivator:jalan", "salt": salt, "spacing": 1, "separation": 0},
            "structures": [{"structure": struktur, "weight": 1}]}


def tulis_wg(kind, nama, obj, pack=None):
    """Writes a worldgen JSON into the mod data (pack None) or into resourcepacks/<pack> (structure_set files of a
    built-in pack, DuniaPacks.worldgen)."""
    if pack is None:
        return write_json(_wg(kind, nama), obj)
    path = os.path.join(structgen.REPO, "src", "main", "resources", "resourcepacks", pack, "data", "kultivator",
                        "worldgen", kind, nama + ".json")
    return write_json(path, obj)


# ------------------------------------------------------------------------------------------------- side codes (tema)
def putar_sisi(sisi_map, putar):
    """Side codes after `putar` clockwise quarter turns (Rotation CLOCKWISE_90 = 1): north goes to east."""
    order = ["n", "e", "s", "w"]
    out = {}
    for i, k in enumerate(order):
        out[order[(i + putar) % 4]] = sisi_map[k]
    return out


def sisi_cocok(a, sisi_a, b, sisi_b):
    """True when side sisi_a of map a meets side sisi_b of map b (codes equal)."""
    return a[sisi_a] == b[sisi_b]


# --------------------------------------------------------------------------------------------------------- checks
def cek_lot(t, where=""):
    """Errors of the 38 lot rules for one template (size, anchors, points, point types, box data, side codes)."""
    errs = []
    sx, sy, sz = t.size
    if sx > LOT or sz > LOT:
        errs.append("%s: %dx%d exceeds %dx%d" % (where, sx, sz, LOT, LOT))
    anchors = [e for e in t.entities if e[3].get("id") == "kultivator:dunia_anchor"]
    if len(anchors) > MAKS_JANGKAR:
        errs.append("%s: %d anchors > %d" % (where, len(anchors), MAKS_JANGKAR))
    pts = getattr(t, "titik_list", [])
    if len(pts) > MAKS_TITIK:
        errs.append("%s: %d titik > %d" % (where, len(pts), MAKS_TITIK))
    for p in pts:
        if not titik_sah(p["jenis"]):
            errs.append("%s: unknown titik %r" % (where, p["jenis"]))
        if p["jenis"] in TITIK_KOTAK:
            d = p.get("data") or {}
            if not all(k in d for k in ("dx", "dy", "dz")):
                errs.append("%s: box titik %s without dx/dy/dz" % (where, p["jenis"]))
    sisi = getattr(t, "sisi_map", None)
    if sisi is not None and (set(sisi) != set(SISI) or any(v not in KODE_SISI for v in sisi.values())):
        errs.append("%s: bad kultivator_sisi %r" % (where, sisi))
    return errs


def cek_semua(strict=True):
    """structgen.validate_all plus cek_lot on every template saved in this run."""
    structgen.validate_all(strict=strict)
    errs = []
    for path, t in structgen._SAVED:
        errs += cek_lot(t, os.path.relpath(path, structgen.REPO))
    for e in errs:
        print("ERROR:", e)
    if errs and strict:
        raise SystemExit("tempat_lib: %d lot rule error(s)" % len(errs))
    print("tempat_lib: %d template(s) pass the 38 lot rules" % len(structgen._SAVED))


def json_dump(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False)


if __name__ == "__main__":
    print(__doc__)
