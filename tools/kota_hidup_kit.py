"""Shared kit of every DESIGN 42 generator (42.9.1, 42.9.4, 42.9.6, 42.12.1; module 42W, API frozen in wave 42-G0 by 42W0;
later changes are additive only, owner 42W-B in 42-G1). Used by gen_tempat_kota_besar.py, gen_dunia_kota.py,
gen_tempat_kampung.py, gen_tempat_jalan.py, gen_tempat_tema_*.py and gen_kota_tumbuh.py.

    import os, sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tempat_lib as tl
    import kota_hidup_kit as khk          # importing it registers the 42 titik types in tempat_lib (see pasang())

    lot = tl.Lot("kota_besar", "pasar/kedai_x", tinggi=24)
    ... walls + roof of a 12 x 10 room whose door faces south ...
    khk.interior("kedai_teh", lot, 6, tl.LANTAI_Y, 8, 12, 10, 4, "south")     # or khk.interior_kedai_teh(lot, ...)
    khk.sumur(lot, 30, tl.LANTAI_Y, 30, "south")
    khk.kavling(lot, 4, tl.LANTAI_Y, 30, "rumah_kecil", "kota_kultivator", "north")
    khk.makam(lot, 2, tl.LANTAI_Y, 2, 32, 30, "south", 48, gaya="bukit", bertingkat=True)
    khk.sambung(lot)                        # fence / pane / wall connections (a Kanvas gets them in potong())
    lot.simpan()

Coordinates are template-local (x east, y up, z south), inclusive like structgen. Conventions every caller relies on:
  * Interior box: x0..x0+w-1, z0..z0+d-1; y0 = the FLOOR block row (the kit may refinish it); y0+1..y0+h is the clear
    room (h >= 3); y0+h+1 is the ceiling the caller builds. `hadap` = the side of the box whose wall holds the entrance
    (north/east/south/west); `pintu` = world x (north/south) or z (east/west) of the door centre, default the middle.
    Call the interior AFTER walls and ceiling: hanging lanterns are only hung under a ceiling block that exists.
  * Life point y: an NPC point (kursi, kasur, kerja:*, sumur, catur, nisan_<n>, ...) is the block the NPC stands in
    (floor + 1); a visual point (lentera_gantung, cerobong, panji, spanduk_*, kucing_tidur, pajangan:*) is the air
    cell the client draws in. `hadap` of a point = where the NPC / the visual looks.
  * pajangan:<jenis> (42.9.1): a small display the client draws as an item prop at the point (DisplayKey, <= 24 blocks);
    the point is the air cell on top of the table/shelf/counter. Names in PAJANGAN.
  * panji (42.9.3): data kain = panji|papan|bendera_arak|spanduk|kertas_pintu, glif = comma list of GLIF ids.
  * nisan_<n>: the headstone is the block one step ahead of the point in its `hadap` (rotation safe); n is unique
    per template and per place (start numbering with mulai= when a place has several cemetery templates).
  * kavling: a box point (dx/dy/dz) at the min corner of the buildable footprint, y = ground row; data rencana (the
    plan jenis of 42.9.4), gaya, kelas. Its hadap is the street side. The growth stages of gen_kota_tumbuh.py are authored with their front facing
    SOUTH and exactly the footprint size of their kelas (UKURAN_KELAS); rotasi_rencana(hadap) gives the clockwise turns.
  * dijual: house for sale (42.6.1): data rumah = the name of its `bangunan` box point, ukuran = kecil|sedang|besar|
    toko|paviliun; put it inside the house at the door.
Only vanilla blocks; no fire, lava, lightning rods; campfires unlit unless api=True (cek_tempat CAMPFIRE_OK rule).
Run `python tools/kota_hidup_kit.py --uji <scratch_dir>` for the self test (writes only into <scratch_dir>).
"""
import os
import random
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402

LY = tl.LANTAI_Y
FACINGS = ("north", "east", "south", "west")
FACE = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
_R = {"north": 0, "east": 1, "south": 2, "west": 3}

# ================================================================================================ titik registry (42.12.1)
TITIK_42 = (
    "sumur", "catur", "gantung_sangkar", "pancing", "jongkok",            # habits 42.4.12
    "kucing_tidur", "tambat", "kandang",                                  # animals 42.7.5 (kandang data hewan=...)
    "cerobong", "lentera_gantung", "panji", "spanduk_a", "spanduk_b", "nama_jalan",   # visuals 42.9.2-42.9.3
    "lumbung", "rekrut", "sidang", "meja_dadu",                           # services (panggung exists in 38)
    "kavling", "dijual",                                                  # growth 42.9.4, houses 42.6.1
    "sekolah", "mengajar", "gudang",                                      # interior points of 42.9.1
)
TITIK_BERNOMOR = ("ronda", "pasar_pagi", "pasar_hantu", "lapak_sewa", "nisan")   # <jenis>_<n>, n = 0, 1, 2, ...
ALAT_42 = ("wajan", "talenan", "guci", "laci", "gunting", "kukus", "jimat", "konter")   # new kerja:<alat> tools
TITIK_KOTAK_42 = ("kavling",)
# pajangan:<jenis>: the 42.9.1 list + existing 42 hand props (42.12.4 ids 170, 178, 182, 195, 199, 204)
PAJANGAN = ("mangkuk", "mi", "teko", "botol_pil", "herba", "kain", "pedang", "papan", "ikan", "bakpao", "lentera_kertas",
            "gulungan", "jimat", "keranjang", "jala", "guci_arak", "dadu_cawan", "bunga", "potret_almarhum", "uang_kertas",
            "dacing")
KAIN = ("panji", "papan", "bendera_arak", "spanduk", "kertas_pintu")
# 16 x 16 glyph textures of 42.12.4 (drawn by 42V); data values are these ascii ids
GLIF = {"fu": "福", "shou": "寿", "chun": "春", "xi": "囍", "dian": "奠", "jiu": "酒",
        "cha": "茶", "yao": "药", "bu": "布", "tie": "铁", "bing": "饼", "shu": "书",
        "fu_jimat": "符", "dang": "当", "ke": "客", "zhan": "栈", "feng": "丰", "jie": "戒",
        "mian": "面"}
WARNA_LENTERA = ("merah", "putih", "hijau", "biru", "sekte")
HEWAN_KANDANG = ("ayam_kota", "kuda_tambat", "unta", "keledai", "anjing_kota", "merpati")
UKURAN_RUMAH = ("kecil", "sedang", "besar", "toko", "paviliun")

# ================================================================================================ kavling plans (42.9.4)
JENIS_RENCANA = ("rumah_kecil", "rumah_besar", "rumah_toko", "kedai_teh", "kedai_arak", "bengkel", "gudang", "kuil_kecil",
                 "sekolah", "menara_jaga", "taman")
GAYA_KAVLING = ("kota_kultivator", "ibukota_hunian_barat", "ibukota_pasar", "ibukota_kumuh", "ibukota_gerbang",
                "ibukota_luar", "pelabuhan", "oasis", "air", "gunung", "kampung")
UKURAN_KELAS = {"kecil": (7, 7), "besar": (13, 13)}            # footprint w x d of a plan (and its kavling)
KELAS_JENIS = {"rumah_kecil": "kecil", "kuil_kecil": "kecil", "menara_jaga": "kecil", "taman": "kecil",
               "rumah_besar": "besar", "rumah_toko": "besar", "kedai_teh": "besar", "kedai_arak": "besar",
               "bengkel": "besar", "gudang": "besar", "sekolah": "besar"}
TINGGI_RENCANA = 18                                             # max stage template height (ground row included)


def titik_sah42(jenis):
    """True for a point type added by DESIGN 42 (TITIK_42, <bernomor>_<n>, kerja:<ALAT_42>, pajangan:<PAJANGAN>)."""
    if not isinstance(jenis, str):
        return False
    if jenis in TITIK_42:
        return True
    if jenis.startswith("kerja:"):
        return jenis[6:] in ALAT_42
    if jenis.startswith("pajangan:"):
        return jenis[9:] in PAJANGAN
    head, _, n = jenis.rpartition("_")
    return head in TITIK_BERNOMOR and n.isdigit() and str(int(n)) == n


def titik_sah(jenis):
    """Legal point type of 38 or 42."""
    return tl.titik_sah(jenis) or titik_sah42(jenis)


def pasang():
    """Registers the 42 point types in tempat_lib so tl.Lot.titik, kota_besar_kit / tempat_ds_kit Kanvas.titik, tempel()
    and tools/cek_tempat.py (when it imports this kit) accept them, and adds kavling to tl.TITIK_KOTAK (box points
    are rotated by tempel and clipped by potong). Runs once at import; idempotent."""
    if getattr(tl, "_kota_hidup_kit", False):
        return
    asli = tl.titik_sah

    def _sah(jenis):
        return asli(jenis) or titik_sah42(jenis)

    tl.titik_sah = _sah
    tl.TITIK_KOTAK = tuple(tl.TITIK_KOTAK) + tuple(t for t in TITIK_KOTAK_42 if t not in tl.TITIK_KOTAK)
    tl._kota_hidup_kit = True


pasang()


def sambung(k):
    """Fence / pane / wall side connections over the whole piece (tata_letak places with knownShape)."""
    import kota_besar_kit as kbk
    kbk.connect_all(k)


def rotasi_rencana(hadap):
    """Clockwise quarter turns that bring a growth-stage template (front authored facing south) to face `hadap`."""
    return (_R[hadap] - _R["south"]) % 4


# ================================================================================================ point writers
def tulis_titik(k, jenis, x, y, z, hadap="south", data=None):
    """Writes one point (38 or 42 type) unless it falls outside the piece (then k.clipped += 1). Returns the entry."""
    if not titik_sah(jenis):
        raise ValueError("unknown titik type %r (kota_hidup_kit.TITIK_42 / tempat_lib.JENIS_TITIK)" % jenis)
    if hadap not in FACE:
        raise ValueError("bad hadap %r" % hadap)
    if not k.inside(x, y, z):
        k.clipped = getattr(k, "clipped", 0) + 1
        return None
    return k.titik(jenis, x, y, z, hadap, {kk: str(v) for kk, v in data.items()} if data else None)


def titik42(k, jenis, x, y, z, hadap="south", **data):
    """Generic writer for any 42 point (see the module doc for the y convention)."""
    return tulis_titik(k, jenis, x, y, z, hadap, data or None)


def bernomor(k, jenis, n, x, y, z, hadap="south", **data):
    """ronda_<n>, pasar_pagi_<n>, pasar_hantu_<n>, lapak_sewa_<n>, nisan_<n>."""
    if jenis not in TITIK_BERNOMOR:
        raise ValueError("%r is not a numbered 42 point" % jenis)
    return tulis_titik(k, "%s_%d" % (jenis, int(n)), x, y, z, hadap, data or None)


def _kayu_dari(k):
    """The wood family of a piece (from its palette): oak, spruce, dark_oak, ... (default dark_oak)."""
    pal = getattr(k, "pal", {}) or {}
    for role in ("wood", "wood_stairs", "trim", "wall_alt", "wall", "fence", "window", "roof", "pillar", "roof_full"):
        bid = pal.get(role, "")
        n = bid.split(":")[-1].replace("stripped_", "")
        for w in ("dark_oak", "pale_oak", "spruce", "birch", "jungle", "acacia", "cherry", "mangrove", "oak", "crimson",
                  "warped"):
            if n.startswith(w + "_"):
                return w
    return "dark_oak"


def bahan(kayu):
    """Material ids of a wood family (planks, stairs, slab, fence, gate, trapdoor, log, slog, plate, door, shelf)."""
    nether = kayu in ("crimson", "warped")
    m = {s: "minecraft:%s_%s" % (kayu, s) for s in ("planks", "stairs", "slab", "fence", "fence_gate", "trapdoor",
                                                    "pressure_plate", "door", "shelf", "button")}
    m["gate"] = m.pop("fence_gate")
    m["plate"] = m.pop("pressure_plate")
    m["log"] = "minecraft:%s_%s" % (kayu, "stem" if nether else "log")
    m["slog"] = "minecraft:stripped_%s_%s" % (kayu, "stem" if nether else "log")
    m["wood"] = "minecraft:stripped_%s_%s" % (kayu, "hyphae" if nether else "wood")
    return m


def _seed(*parts):
    return zlib.crc32("|".join(str(p) for p in parts).encode("utf-8")) & 0x7FFFFFFF


_SOLID_CEIL = ("slab", "stairs", "planks", "log", "wood", "bricks", "tiles", "terracotta", "concrete", "quartz", "stone",
               "deepslate", "sandstone", "wool", "block", "prismarine", "blackstone", "calcite", "tuff", "mud", "andesite",
               "diorite", "granite", "basalt", "hyphae", "stem", "copper", "hay")


def _plafon_padat(bid):
    if bid is None:
        return False
    n = bid.split(":")[-1]
    if any(t in n for t in ("fence", "wall", "pane", "bars", "trapdoor", "door", "sign", "banner", "carpet", "lantern",
                            "leaves", "glass", "chain", "air")):
        return False
    return any(t in n for t in _SOLID_CEIL)


def _putar(props, r):
    """Block properties after r clockwise quarter turns (facing, x/z axis, banner rotation)."""
    if not props or r % 4 == 0:
        return props
    out = {}
    for key, val in props.items():
        if key == "facing" and val in FACINGS:
            val = FACINGS[(FACINGS.index(val) + r) % 4]
        elif key == "axis" and r % 2 == 1 and val in ("x", "z"):
            val = "z" if val == "x" else "x"
        elif key == "rotation":
            val = str((int(val) + 4 * r) % 16)
        out[key] = val
    return out


# ================================================================================================ the room frame
class Bilik:
    """An interior box seen from its entrance: local u runs across (left to right when standing in the door looking
    in), v runs from the front wall (v = 0, just inside the door) to the back (v = D - 1); dy = height above the floor
    row y0 (dy 0 = floor, 1 = standing level). Local facings use 'north' = toward the door, 'south' = toward the back,
    'east' = +u, 'west' = -u; props (facing, axis, banner rotation) and entity yaw are turned to the world."""

    def __init__(self, k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, kit="", api=False, min_wdh=(5, 5, 3)):
        if hadap not in _R:
            raise ValueError("hadap %r not in north/east/south/west" % (hadap,))
        self.k, self.x0, self.y0, self.z0, self.w, self.d, self.h, self.hadap = k, x0, y0, z0, w, d, h, hadap
        self.r = _R[hadap]
        self.W, self.D = (w, d) if self.r % 2 == 0 else (d, w)
        mw, md, mh = min_wdh
        if self.W < mw or self.D < md or h < mh:
            raise ValueError("interior %s needs at least %dx%dx%d (across x depth x height), got %dx%dx%d"
                             % (kit, mw, md, mh, self.W, self.D, h))
        if pintu is None:      # the middle of the front wall in world terms (= W // 2 for odd widths)
            pintu = x0 + w // 2 if self.r % 2 == 0 else z0 + d // 2
        self.pu = {0: pintu - x0, 1: pintu - z0, 2: w - 1 - (pintu - x0), 3: d - 1 - (pintu - z0)}[self.r]
        if not 0 <= self.pu < self.W:
            raise ValueError("pintu %r outside the front wall of the box" % (pintu,))
        self.kit = kit
        self.api = bool(api)
        self.m = bahan(_kayu_dari(k))
        self.rng = random.Random(_seed(kit, x0, y0, z0, w, d, hadap))
        self.sisa = titik_maks
        self.titik_n = 0
        self.res = set()
        self.dapur = None          # world (x, z) of the stove / oven when the kit has one (put a cerobong above it)
        self.lampu = []            # world positions of hung lanterns
        for u in range(self.pu - 1, self.pu + 2):
            for v in range(0, 2):
                self.res.add((u, v))

    # -------------------------------------------------------------------------------------------- transforms
    def xz(self, u, v):
        x0, z0, w, d = self.x0, self.z0, self.w, self.d
        if self.r == 0:
            return x0 + u, z0 + v
        if self.r == 1:
            return x0 + (w - 1 - v), z0 + u
        if self.r == 2:
            return x0 + (w - 1 - u), z0 + (d - 1 - v)
        return x0 + v, z0 + (d - 1 - u)

    def arah(self, f):
        """World facing of a local facing."""
        return FACINGS[(FACINGS.index(f) + self.r) % 4] if f in FACINGS else f

    def _props(self, props):
        return _putar(props, self.r)

    def ada(self, u, v):
        return 0 <= u < self.W and 0 <= v < self.D

    def p(self, u, dy, v, blok, props=None, nbt=None):
        """Places one block at local (u, dy, v) (blok = literal id or a material key of self.m)."""
        bid = self.m.get(blok, blok)
        x, z = self.xz(u, v)
        y = self.y0 + dy
        if not self.k.inside(x, y, z):
            self.k.clipped = getattr(self.k, "clipped", 0) + 1
            return
        if nbt is not None:
            self.k.set(x, y, z, bid, self._props(props), nbt)
        else:
            self.k.put(x, y, z, bid, self._props(props))

    def get(self, u, dy, v):
        x, z = self.xz(u, v)
        y = self.y0 + dy
        return self.k.get(x, y, z) if self.k.inside(x, y, z) else None

    def entitas(self, u, dy, v, nbt, yaw=0.0):
        """An entity at the centre of local cell (u, v), standing on dy; yaw is local (0 = facing the back wall)."""
        x, z = self.xz(u, v)
        e = dict(nbt)
        e["Rotation"] = [structgen.nbt.Float((yaw + 90.0 * self.r) % 360.0), structgen.nbt.Float(0.0)]
        self.k.entity(x + 0.5, self.y0 + dy, z + 0.5, e)

    # -------------------------------------------------------------------------------------------- reservations
    def bebas(self, u, v):
        return self.ada(u, v) and (u, v) not in self.res

    def semua_bebas(self, cells):
        return all(self.bebas(u, v) for (u, v) in cells)

    def pakai(self, cells, pinggir=0):
        for (u, v) in cells:
            for du in range(-pinggir, pinggir + 1):
                for dv in range(-pinggir, pinggir + 1):
                    self.res.add((u + du, v + dv))

    def lorong(self, lebar=3, dari=0, sampai=None):
        """Keeps a walking aisle from the door straight to the back (or to v = sampai)."""
        half = lebar // 2
        for v in range(dari, self.D if sampai is None else sampai + 1):
            for u in range(self.pu - half, self.pu - half + lebar):
                self.res.add((u, v))

    # -------------------------------------------------------------------------------------------- points
    def titik(self, jenis, u, v, f="south", data=None, dy=1, wajib=False):
        """A life point in local coordinates (f = local facing). Counted against titik_maks unless wajib."""
        if not wajib and self.sisa <= 0:
            return None
        x, z = self.xz(u, v)
        e = tulis_titik(self.k, jenis, x, self.y0 + dy, z, self.arah(f), data)
        if e is not None:
            self.titik_n += 1
            if not wajib:
                self.sisa -= 1
        return e

    def kotak_titik(self, jenis, u0, v0, u1, v1, dy0, dy1, f="north", **data):
        """A box point (panggung, kavling) over local cells u0..u1 x v0..v1, heights dy0..dy1 (always written)."""
        ax, az = self.xz(u0, v0)
        bx, bz = self.xz(u1, v1)
        d = {"dx": abs(bx - ax), "dy": abs(dy1 - dy0), "dz": abs(bz - az)}
        d.update(data)
        e = tulis_titik(self.k, jenis, min(ax, bx), self.y0 + min(dy0, dy1), min(az, bz), self.arah(f), d)
        if e is not None:
            self.titik_n += 1
        return e

    def pajangan(self, jenis, u, dy, v, f="north"):
        """A pajangan:<jenis> display point on the surface below local (u, dy, v)."""
        if jenis not in PAJANGAN:
            raise ValueError("unknown pajangan %r" % jenis)
        return self.titik("pajangan:" + jenis, u, v, f, None, dy)

    # -------------------------------------------------------------------------------------------- furniture
    def meja(self, u, v, atas="plate"):
        """A table: a fence leg and a top (pressure plate, or a literal top block / material key)."""
        self.p(u, 1, v, "fence")
        if atas == "plate":
            self.p(u, 2, v, "plate", {"powered": "false"})
        elif atas:
            self.p(u, 2, v, atas)

    def meja_papan(self, u, v, dy=1):
        """A board-top table or counter top (a closed top trapdoor)."""
        self.p(u, dy, v, "trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                      "waterlogged": "false"})

    def kursi(self, u, v, f, blok="stairs"):
        """A chair whose sitter looks toward local facing f (the stair back is on the other side)."""
        self.p(u, 1, v, blok, {"facing": OPP[f], "half": "bottom", "shape": "straight", "waterlogged": "false"})

    def bangku(self, u, v):
        """A stool (a bottom slab)."""
        self.p(u, 1, v, "slab", {"type": "bottom", "waterlogged": "false"})

    def tangga(self, u, dy, v, f, half="bottom", blok="stairs"):
        self.p(u, dy, v, blok, {"facing": f, "half": half, "shape": "straight", "waterlogged": "false"})

    def slab(self, u, dy, v, kind="bottom", blok="slab"):
        self.p(u, dy, v, blok, {"type": kind, "waterlogged": "false"})

    def rak(self, u, dy, v, f):
        """A shelf block whose front faces local f."""
        self.p(u, dy, v, "shelf", {"facing": f, "powered": "false", "side_chain": "unconnected", "waterlogged": "false"})

    def tong(self, u, dy, v, f="up"):
        self.p(u, dy, v, "minecraft:barrel", {"facing": f, "open": "false"})

    def peti(self, u, dy, v, f, loot=None):
        nbt = {"id": "minecraft:chest"}
        if loot:
            nbt["LootTable"] = loot
        self.p(u, dy, v, "minecraft:chest", {"facing": f, "type": "single", "waterlogged": "false"}, nbt)

    def lilin(self, u, dy, v, n=1, warna="", nyala=True):
        bid = "minecraft:%scandle" % (warna + "_" if warna else "")
        self.p(u, dy, v, bid, {"candles": str(n), "lit": "true" if nyala else "false", "waterlogged": "false"})

    def pot(self, u, dy, v, tanaman=None):
        self.p(u, dy, v, tanaman or self.rng.choice(("minecraft:potted_bamboo", "minecraft:potted_azalea_bush",
                                                     "minecraft:potted_fern", "minecraft:potted_red_tulip",
                                                     "minecraft:potted_spruce_sapling")))

    def karpet(self, u0, v0, u1, v1, warna="red", tepi=None):
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for v in range(min(v0, v1), max(v0, v1) + 1):
                edge = tepi and (u in (u0, u1) or v in (v0, v1))
                self.p(u, 1, v, "minecraft:%s_carpet" % (tepi if edge else warna))

    def lantai(self, u0, v0, u1, v1, blok, alt=None, tiap=5):
        """Refinishes the floor row (dy 0) of a local rectangle (alt every `tiap` cells in a fixed pattern)."""
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for v in range(min(v0, v1), max(v0, v1) + 1):
                self.p(u, 0, v, alt if alt and (u * 3 + v * 7) % tiap == 0 else blok)

    def lentera(self, u, v, blok="minecraft:lantern", rantai=0):
        """Hangs a lantern under the ceiling at local (u, v) (with `rantai` chains); skipped without a ceiling."""
        top = self.h + 1
        if not _plafon_padat(self.get(u, top, v)):
            return False
        for i in range(rantai):
            self.p(u, top - 1 - i, v, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        dy = top - 1 - rantai
        if dy < 3:
            return False
        props = {"hanging": "true", "waterlogged": "false"} if blok.endswith("lantern") else None
        self.p(u, dy, v, blok, props)
        x, z = self.xz(u, v)
        self.lampu.append((x, self.y0 + dy, z))
        return True

    def kasur(self, u, v, f="north", warna="red"):
        """A bed whose head is at local (u, v) and points toward f (the foot is one step the other way)."""
        du, dv = FACE[f]
        bid = "minecraft:%s_bed" % warna
        self.p(u, 1, v, bid, {"facing": f, "part": "head", "occupied": "false"})
        self.p(u - du, 1, v - dv, bid, {"facing": f, "part": "foot", "occupied": "false"})

    def konter(self, u0, u1, v, f="north", badan="barrel", celah=None):
        """A counter along u0..u1 at depth v: a body block (barrel/planks/log) with a board top, open toward f;
        `celah` = local u left open as the walk-through gap."""
        for u in range(min(u0, u1), max(u0, u1) + 1):
            if u == celah:
                continue
            if badan == "barrel":
                self.tong(u, 1, v, f)
            else:
                self.p(u, 1, v, badan, {"axis": "x"} if badan in ("log", "slog") else None)
            self.meja_papan(u, v, 2)

    def layar(self, u0, u1, v, warna="white"):
        """A folding screen: open trapdoor leaves standing in a line with a wool panel band (paper or silk)."""
        for i, u in enumerate(range(min(u0, u1), max(u0, u1) + 1)):
            self.p(u, 1, v, "trapdoor", {"facing": "north" if i % 2 else "south", "half": "bottom", "open": "true",
                                         "powered": "false", "waterlogged": "false"})
            if self.h >= 4:
                self.p(u, 2, v, "minecraft:%s_stained_glass_pane" % warna)

    def guci(self, u, dy, v):
        self.p(u, dy, v, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})

    def papan(self, glif, kain="papan", dy=None, u=None):
        """The shop sign over the entrance (42.9.1/42.9.3): a gold-centred board in the front wall above the door and a
        panji point one block outside the wall carrying kain + glif for the client glyph board / flag."""
        u = self.pu if u is None else u
        dy = min(self.h, 4) if dy is None else dy
        for du, bid in ((-1, "planks"), (0, "minecraft:gold_block"), (1, "planks")):
            self.p(u + du, dy, -1, bid)
        if kain == "bendera_arak":
            self.p(u + 2, dy, -2, "fence")      # a short pole arm off the wall; the flag hangs under it
            self._titik_luar("panji", u + 2, dy - 1, -2, {"kain": kain, "glif": glif})
        else:
            self._titik_luar("panji", u, dy, -2, {"kain": kain, "glif": glif})

    def _titik_luar(self, jenis, u, dy, v, data):
        x, z = self.xz(u, v) if 0 <= u < self.W else (None, None)
        if x is None:
            return None
        # v < 0 lies outside the box (in front of the wall): xz() extrapolates linearly, which is exact
        return tulis_titik(self.k, jenis, x, self.y0 + dy, z, self.arah("north"), data)

    def ringkas(self):
        return {"kit": self.kit, "titik": self.titik_n, "lampu": len(self.lampu), "dapur": self.dapur}

    # -------------------------------------------------------------------------------------------- dressing passes
    def kosong(self, u, v, dy0=1, dy1=2):
        return self.ada(u, v) and all(self.get(u, dy, v) in (None, "minecraft:air") for dy in range(dy0, dy1 + 1))

    def atur(self, v0, v1, langkah_v, lebar, fn, jarak=1, u0=0):
        """Greedy rows: from v0 to v1 every langkah_v rows, tries fn(u, v) at every u (fn returns True when it placed a
        cluster `lebar` wide starting at u); after a placement skips lebar + jarak columns. Returns the count."""
        n = 0
        for v in range(v0, v1 + 1, langkah_v):
            u = u0
            while u + lebar <= self.W:
                if fn(u, v):
                    n += 1
                    u += lebar + jarak
                else:
                    u += 1
        return n

    def isi_dinding(self, pola, sisi="wef"):
        """Dresses the free cells along the side walls (w: u = 0, e: u = W - 1), the front wall (f: v = 0, beside the
        door) and the back wall (b) by a repeating token pattern: rak (barrel + shelf), tong, tong2, guci, bunga (pot
        on a stand), pot, kain (wool bolts), kayu (planks + slab), jerami (hay), tepung (flour sacks), lilin (candle
        on stone), peti, buku (bookshelf column), panji (banner + jar), '-' (leave free)."""
        sel = []
        if "w" in sisi:
            sel += [(0, v, "east") for v in range(1, self.D - 1)]
        if "e" in sisi:
            sel += [(self.W - 1, v, "west") for v in range(1, self.D - 1)]
        if "f" in sisi:
            sel += [(u, 0, "south") for u in range(self.W)]
        if "b" in sisi:
            sel += [(u, self.D - 1, "north") for u in range(self.W)]
        n = 0
        kain = ("red", "blue", "yellow", "white", "green", "light_blue", "purple", "orange")
        for i, (u, v, f) in enumerate(sel):
            t = pola[(u * 3 + v * 5 + i) % len(pola)]
            if t == "-" or not self.bebas(u, v) or not self.kosong(u, v, 1, 2):
                continue
            if t == "rak":
                self.tong(u, 1, v, f)
                self.rak(u, 2, v, f)
            elif t == "tong":
                self.tong(u, 1, v, f)
            elif t == "tong2":
                self.tong(u, 1, v, "up")
                self.tong(u, 2, v, f)
            elif t == "guci":
                self.guci(u, 1, v)
            elif t == "bunga":
                self.p(u, 1, v, "fence")
                self.pot(u, 2, v, _BUNGA[(u + v) % len(_BUNGA)])
            elif t == "pot":
                self.pot(u, 1, v, _BUNGA[(u * 2 + v) % len(_BUNGA)])
            elif t == "kain":
                self.p(u, 1, v, "minecraft:%s_wool" % kain[(u + v) % len(kain)])
                self.p(u, 2, v, "minecraft:%s_wool" % kain[(u + 2 * v + 3) % len(kain)])
            elif t == "kayu":
                self.p(u, 1, v, "planks")
                self.slab(u, 2, v)
            elif t == "jerami":
                self.p(u, 1, v, "minecraft:hay_block", {"axis": "y"})
            elif t == "tepung":
                self.p(u, 1, v, "minecraft:white_wool")
                self.p(u, 2, v, "minecraft:white_carpet")
            elif t == "lilin":
                self.p(u, 1, v, "minecraft:chiseled_stone_bricks")
                self.lilin(u, 2, v, 2, "red")
            elif t == "peti":
                self.peti(u, 1, v, f)
            elif t == "buku":
                for dy in range(1, min(self.h, 3) + 1):
                    self.p(u, dy, v, "minecraft:bookshelf")
            elif t == "panji":
                self.guci(u, 1, v)
                if self.h >= 3:
                    self.p(u, 3, v, "minecraft:red_wall_banner", {"facing": f})
            self.pakai([(u, v)])
            n += 1
        return n

    def isi_lampu(self, jarak=4, blok="minecraft:lantern"):
        """Hangs lanterns on a grid under the ceiling, never closer than jarak - 1 to another lamp of this room."""
        n = 0
        for v in range(1, self.D - 1, jarak):
            for u in range(1 + (v // jarak) % 2, self.W - 1, jarak):
                x, z = self.xz(u, v)
                if any(abs(x - lx) + abs(z - lz) < jarak - 1 for (lx, _ly, lz) in self.lampu):
                    continue
                if self.get(u, self.h, v) not in (None, "minecraft:air"):
                    continue
                if self.lentera(u, v, blok):
                    n += 1
        return n


# ================================================================================================ small object frame
class Benda:
    """A small object around world point (x, y, z) facing `hadap`: local lx = to its right, lz = backward (lz < 0 is
    in front), dy above y (y = the ground row). Local facings use 'north' = forward (= hadap)."""

    def __init__(self, k, x, y, z, hadap="south"):
        if hadap not in _R:
            raise ValueError("hadap %r not in north/east/south/west" % (hadap,))
        self.k, self.x, self.y, self.z, self.hadap, self.r = k, x, y, z, hadap, _R[hadap]
        self.m = bahan(_kayu_dari(k))

    def xz(self, lx, lz):
        r = self.r
        if r == 0:
            dx, dz = lx, lz
        elif r == 1:
            dx, dz = -lz, lx
        elif r == 2:
            dx, dz = -lx, -lz
        else:
            dx, dz = lz, -lx
        return self.x + dx, self.z + dz

    def arah(self, f):
        return FACINGS[(FACINGS.index(f) + self.r) % 4] if f in FACINGS else f

    def p(self, lx, dy, lz, blok, props=None, nbt=None):
        bid = self.m.get(blok, blok)
        x, z = self.xz(lx, lz)
        y = self.y + dy
        if not self.k.inside(x, y, z):
            self.k.clipped = getattr(self.k, "clipped", 0) + 1
            return
        if nbt is not None:
            self.k.set(x, y, z, bid, _putar(props, self.r), nbt)
        else:
            self.k.put(x, y, z, bid, _putar(props, self.r))

    def titik(self, jenis, lx, lz, f="north", data=None, dy=1):
        x, z = self.xz(lx, lz)
        return tulis_titik(self.k, jenis, x, self.y + dy, z, self.arah(f), data)

    def tangga(self, lx, dy, lz, f, half="bottom", blok="stairs"):
        self.p(lx, dy, lz, blok, {"facing": f, "half": half, "shape": "straight", "waterlogged": "false"})

    def slab(self, lx, dy, lz, kind="bottom", blok="slab"):
        self.p(lx, dy, lz, blok, {"type": kind, "waterlogged": "false"})


_DAUN_OK = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak")


def _kayu_pohon(k):
    kayu = _kayu_dari(k)
    return kayu if kayu in _DAUN_OK and kayu != "pale_oak" else "oak"


def _pohon(o, lx, lz, tinggi=4, kayu="oak", cabang=None):
    """A small shade tree (trunk + two leaf layers + optional horizontal branch toward local facing `cabang`)."""
    log = "minecraft:%s_log" % kayu
    for dy in range(1, tinggi + 1):
        o.p(lx, dy, lz, log, {"axis": "y"})
    leaf = "minecraft:%s_leaves" % kayu
    lp = {"distance": "1", "persistent": "true", "waterlogged": "false"}
    for dy, rad in ((tinggi, 2), (tinggi + 1, 1)):
        for ax in range(-rad, rad + 1):
            for az in range(-rad, rad + 1):
                if (ax, az) == (0, 0) and dy == tinggi:
                    continue
                if rad == 2 and abs(ax) == 2 and abs(az) == 2:
                    continue
                o.p(lx + ax, dy, lz + az, leaf, lp)
    o.p(lx, tinggi + 2, lz, leaf, lp)
    if cabang:
        du, dv = FACE[cabang]
        o.p(lx + du, tinggi - 1, lz + dv, log, {"axis": "x" if du else "z"})


# ================================================================================================ 42.12.1 point builders
def sumur(k, x, y, z, hadap="south", atap=True, ember=True):
    """A town well (gossip centre, washing; titik `sumur` + `obrol`). (x, z) = the shaft, y = ground row; the
    `sumur` point stands two blocks out on the hadap side looking at the shaft. Footprint 5 x 5 (roof 3 x 3)."""
    o = Benda(k, x, y, z, hadap)
    o.p(0, -2, 0, "minecraft:stone_bricks")
    o.p(0, -1, 0, "minecraft:water", {"level": "0"})
    o.p(0, 0, 0, "minecraft:water", {"level": "0"})
    for lx in (-1, 0, 1):
        for lz in (-1, 0, 1):
            if (lx, lz) == (0, 0):
                continue
            o.p(lx, 0, lz, "minecraft:stone_bricks")
            if lx != 0 and lz == 0 and atap:
                o.p(lx, 1, lz, "log", {"axis": "y"})
            else:
                o.p(lx, 1, lz, "minecraft:stone_brick_wall")
    for lx in (-2, 2):
        for lz in (-2, -1, 0, 1, 2):
            o.p(lx, 0, lz, "minecraft:polished_andesite")
    for lz in (-2, 2):
        for lx in (-1, 0, 1):
            o.p(lx, 0, lz, "minecraft:polished_andesite")
    if atap:
        for lx in (-1, 1):
            o.p(lx, 2, 0, "fence")
            o.p(lx, 3, 0, "fence")
        for lx in (-1, 0, 1):
            o.p(lx, 4, 0, "log", {"axis": "x"})
            o.tangga(lx, 4, -1, "south")
            o.tangga(lx, 4, 1, "north")
            o.slab(lx, 5, 0)
        o.p(0, 3, 0, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    if ember:
        o.p(2, 1, 1, "minecraft:water_cauldron", {"level": "3"})
    o.titik("sumur", 0, -2, "south")
    o.titik("obrol", -2, -1, "east")
    return o


def catur(k, x, y, z, hadap="south", pohon=True):
    """A xiangqi table under a tree (42.4.12 kakek_catur): stone table at (x, z), stools left and right with the two
    `catur` points (data kursi=a/b) facing it; the board is a client prop on the table. Footprint 3 x 1 (+ tree)."""
    o = Benda(k, x, y, z, hadap)
    o.p(0, 1, 0, "minecraft:polished_andesite")
    for lx, f, kursi in ((-1, "east", "a"), (1, "west", "b")):
        o.slab(lx, 1, 0, "bottom", "minecraft:polished_andesite_slab")
        o.titik("catur", lx, 0, f, {"kursi": kursi})
    o.titik("obrol", 0, -1, "south")
    if pohon:
        _pohon(o, 0, 2, 4, _kayu_pohon(k))
        o.p(-1, 1, 2, "minecraft:moss_carpet")
        o.p(1, 1, 2, "minecraft:moss_carpet")
    return o


def gantung_sangkar(k, x, y, z, hadap="south", pohon=True, bangku=True):
    """Bird-cage branch (42.4.12 burung_sangkar): the point at (x, y+1, z) looks at a branch one step ahead (the cage is
    a client prop hung there); with pohon a tree two steps ahead grows that branch; a bench beside for listening."""
    o = Benda(k, x, y, z, hadap)
    if pohon:
        _pohon(o, 0, -2, 5, _kayu_pohon(k), cabang="south")
    if bangku:
        o.tangga(2, 1, 0, "east")
        o.tangga(2, 1, 1, "east")
    o.titik("gantung_sangkar", 0, 0, "north")
    return o


def pancing(k, x, y, z, hadap="south", dermaga=False):
    """Fishing spot (42.4.12 pemancing): the point looks at the water ahead. With dermaga a 2-wide, 3-long plank jetty
    is built forward from (x, z) on log piles and the point moves to its end."""
    o = Benda(k, x, y, z, hadap)
    ujung = 0
    if dermaga:
        for lz in (0, -1, -2):
            o.slab(0, 0, lz, "top")
            o.slab(-1, 0, lz, "top")
        for lx in (-2, 1):
            for dy in (-3, -2, -1, 0):
                o.p(lx, dy, -2, "log", {"axis": "y"})
            o.p(lx, 1, -2, "fence")
        ujung = -2
    o.p(1, 1, ujung + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    o.titik("pancing", 0, ujung, "north")
    return o


def jongkok(k, x, y, z, hadap="south", n=1):
    """Squat spot in an alley (42.4.12 adu_jangkrik); n (1-4) points in a small ring facing its centre."""
    o = Benda(k, x, y, z, hadap)
    ring = ((0, 0, "north"), (0, -2, "south"), (-1, -1, "east"), (1, -1, "west"))
    for i in range(max(1, min(n, 4))):
        lx, lz, f = ring[i]
        o.titik("jongkok", lx, lz, f)
    return o


def kucing_tidur(k, x, y, z, hadap="south"):
    """A cat napping spot on a roof, sill or wall top: the air cell the cat lies in."""
    return titik42(k, "kucing_tidur", x, y, z, hadap)


def tambat(k, x, y, z, hadap="south", palung=True):
    """A hitching rail (42.7.5 kuda_tambat): a 3-block fence rail between log posts at (x, z), the `tambat` point (the
    horse's place) two steps out on the hadap side looking at the rail, a trough and a hay bale beside."""
    o = Benda(k, x, y, z, hadap)
    o.p(-2, 1, 0, "log", {"axis": "y"})
    o.p(2, 1, 0, "log", {"axis": "y"})
    for lx in (-1, 0, 1):
        o.p(lx, 1, 0, "fence")
    if palung:
        o.p(3, 1, -1, "minecraft:water_cauldron", {"level": "2"})
        o.p(3, 1, 0, "minecraft:hay_block", {"axis": "y"})
    o.titik("tambat", 0, -2, "south")
    return o


def kandang(k, x0, y, z0, x1, z1, hewan="ayam_kota", pintu="south", jumlah=None):
    """A fenced pen (42.7.5) over x0..x1 x z0..z1 at ground row y, a gate in the middle of the `pintu` side, a trough
    and hay inside; chickens get a little coop. Point `kandang` (data hewan, jumlah) in the middle of the pen."""
    if hewan not in HEWAN_KANDANG:
        raise ValueError("hewan %r not in %s" % (hewan, HEWAN_KANDANG))
    lo_x, hi_x, lo_z, hi_z = min(x0, x1), max(x0, x1), min(z0, z1), max(z0, z1)
    if hi_x - lo_x < 5 or hi_z - lo_z < 5:
        raise ValueError("kandang needs at least 6 x 6")
    m = bahan(_kayu_dari(k))
    cx, cz = (lo_x + hi_x) // 2, (lo_z + hi_z) // 2
    gate = {"north": (cx, lo_z), "south": (cx, hi_z), "west": (lo_x, cz), "east": (hi_x, cz)}[pintu]
    for x in range(lo_x, hi_x + 1):
        for z in range(lo_z, hi_z + 1):
            edge = x in (lo_x, hi_x) or z in (lo_z, hi_z)
            k.put(x, y, z, "minecraft:coarse_dirt" if (x * 5 + z * 3) % 7 == 0 else
                  ("minecraft:dirt" if (x + z) % 3 else "minecraft:rooted_dirt"))
            k.put(x, y + 1, z, "minecraft:air")
            if not edge:
                continue
            if (x, z) == gate:
                k.put(x, y + 1, z, m["gate"], {"facing": pintu, "in_wall": "false", "open": "false", "powered": "false"})
            elif x in (lo_x, hi_x) and z in (lo_z, hi_z):
                k.put(x, y + 1, z, m["log"], {"axis": "y"})
            else:
                k.put(x, y + 1, z, m["fence"])
    k.put(lo_x + 1, y + 1, lo_z + 1, "minecraft:hay_block", {"axis": "y"})
    k.put(lo_x + 2, y + 1, lo_z + 1, "minecraft:hay_block", {"axis": "x"})
    k.put(hi_x - 1, y + 1, lo_z + 1, "minecraft:water_cauldron", {"level": "3"})
    if hewan == "ayam_kota":
        # the coop: a 2 x 2 hay-floored hut with a plank back wall and a slab roof, open to the yard
        for x in (hi_x - 2, hi_x - 1):
            for z in (hi_z - 2, hi_z - 1):
                k.put(x, y + 1, z, "minecraft:hay_block", {"axis": "y"})
                k.put(x, y + 3, z, m["slab"], {"type": "bottom", "waterlogged": "false"})
            k.put(x, y + 2, hi_z - 1, m["planks"])
        k.put(hi_x - 2, y + 2, hi_z - 2, m["fence"])
        k.put(lo_x + 1, y + 1, hi_z - 1, "minecraft:composter", {"level": "4"})
    else:
        k.put(lo_x + 1, y + 1, hi_z - 1, "minecraft:composter", {"level": "6"})
        k.put(lo_x + 2, y + 1, hi_z - 1, "minecraft:hay_block", {"axis": "y"})
    if jumlah is None:
        jumlah = {"ayam_kota": 5, "kuda_tambat": 2, "unta": 2, "keledai": 2, "anjing_kota": 1, "merpati": 6}[hewan]
    return tulis_titik(k, "kandang", cx, y + 1, cz, pintu, {"hewan": hewan, "jumlah": jumlah})


def cerobong(k, x, y, z, tinggi=3, blok="minecraft:bricks"):
    """A chimney stack from row y up (tinggi blocks, the top one a mud-brick pot); the `cerobong` point is the air cell
    above it, where the client draws the kitchen smoke (42.9.2). Put it over Bilik.dapur on the roof."""
    for dy in range(tinggi - 1):
        k.put(x, y + dy, z, blok)
    k.put(x, y + tinggi - 1, z, "minecraft:mud_brick_wall")
    return tulis_titik(k, "cerobong", x, y + tinggi, z, "south")


def lentera_gantung(k, x, y, z, hadap="south", warna="merah", kait=True, **data):
    """A town paper lantern (42.9.2, drawn by the client, no block): the point is the lantern's air cell; kait puts an
    iron chain hook in the cell above (under an eave, beam or arm)."""
    if warna not in WARNA_LENTERA:
        raise ValueError("warna %r not in %s" % (warna, WARNA_LENTERA))
    if kait:
        k.put(x, y + 1, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    d = {"warna": warna}
    d.update(data)
    return tulis_titik(k, "lentera_gantung", x, y, z, hadap, d)


def tiang_lentera(k, x, y, z, hadap="south", tinggi=4, warna="merah", **data):
    """A street lantern post on ground row y: a log foot and a fence pole tinggi high with a one-block arm toward hadap;
    the lantern point hangs under the arm."""
    if warna not in WARNA_LENTERA:
        raise ValueError("warna %r not in %s" % (warna, WARNA_LENTERA))
    o = Benda(k, x, y, z, hadap)
    o.p(0, 1, 0, "log", {"axis": "y"})
    for dy in range(2, tinggi + 1):
        o.p(0, dy, 0, "fence")
    o.slab(0, tinggi + 1, 0)
    o.p(0, tinggi, -1, "fence")
    d = {"warna": warna}
    d.update(data)
    return o.titik("lentera_gantung", 0, -1, "north", d, dy=tinggi - 1)


def _kain_data(kain, glif):
    if kain not in KAIN:
        raise ValueError("kain %r not in %s" % (kain, KAIN))
    d = {"kain": kain}
    if glif:
        ids = [g.strip() for g in (glif.split(",") if isinstance(glif, str) else glif)]
        for g in ids:
            if g not in GLIF:
                raise ValueError("glif %r not in GLIF" % g)
        d["glif"] = ",".join(ids)
    return d


def panji(k, x, y, z, hadap="south", kain="panji", glif=None, tiang=5, alas=True):
    """A banner pole (42.9.3) on ground row y: optional stone foot, a fence pole tiang high; the `panji` point is the
    air cell on the pole top, the cloth hangs from it on the hadap side (kain, glif data)."""
    d = _kain_data(kain, glif)
    m = bahan(_kayu_dari(k))
    if alas:
        k.put(x, y + 1, z, "minecraft:chiseled_stone_bricks")
    for dy in range(2 if alas else 1, tiang + 1):
        k.put(x, y + dy, z, m["fence"])
    return tulis_titik(k, "panji", x, y + tiang + 1, z, hadap, d)


def panji_dinding(k, x, y, z, hadap="south", kain="panji", glif=None):
    """A cloth / board hung on a wall: the point is the air cell in front of the wall face (hadap = out of the wall)."""
    return tulis_titik(k, "panji", x, y, z, hadap, _kain_data(kain, glif))


def spanduk(k, xa, za, xb, zb, y, tinggi=6, pasangan="a", glif=None):
    """A street-spanning banner pair (42.9.3): two poles at (xa, za) and (xb, zb) on ground row y; spanduk_a / spanduk_b
    points on the pole tops (data pasangan, optional glif); the client stretches the cloth between them."""
    m = bahan(_kayu_dari(k))
    hadap = "south" if abs(xb - xa) >= abs(zb - za) else "east"
    out = []
    for (x, z), j in (((xa, za), "spanduk_a"), ((xb, zb), "spanduk_b")):
        k.put(x, y + 1, z, "minecraft:chiseled_stone_bricks")
        k.put(x, y + 2, z, m["log"], {"axis": "y"})
        for dy in range(3, tinggi + 1):
            k.put(x, y + dy, z, m["fence"])
        d = {"pasangan": pasangan}
        if glif:
            d.update(_kain_data("spanduk", glif))
        out.append(tulis_titik(k, j, x, y + tinggi + 1, z, hadap, d))
    return out


def nama_jalan(k, x, y, z, hadap="south", jalan=""):
    """A street-name post (42.10 'Kota yang Tumbuh' reward): a 2-block post with a slab cap on ground row y; the
    `nama_jalan` point is the air cell above, where the client billboard shows the street name."""
    m = bahan(_kayu_dari(k))
    k.put(x, y + 1, z, m["fence"])
    k.put(x, y + 2, z, m["fence"])
    k.put(x, y + 3, z, m["slab"], {"type": "bottom", "waterlogged": "false"})
    return tulis_titik(k, "nama_jalan", x, y + 4, z, hadap, {"jalan": jalan} if jalan else None)


def ronda(k, pts, mulai=0, rute="a"):
    """Night-watch route (42.7.3): ronda_<mulai + i> points through pts [(x, y, z), ...] (y = standing level), each
    looking toward the next; data rute, urut."""
    out = []
    for i, (x, y, z) in enumerate(pts):
        nx, _, nz = pts[(i + 1) % len(pts)]
        dx, dz = nx - x, nz - z
        f = ("east" if dx > 0 else "west") if abs(dx) >= abs(dz) else ("south" if dz > 0 else "north")
        out.append(bernomor(k, "ronda", mulai + i, x, y, z, f, rute=rute, urut=i))
    return out


def pasar_pagi(k, n, x, y, z, hadap="south"):
    """Morning-market tent spot (42.7.2, y = standing level): the tenda_pasar entity stands here; keep 3 x 3 free."""
    return bernomor(k, "pasar_pagi", n, x, y, z, hadap)


def lapak_sewa(k, n, x, y, z, hadap="south", alas=True):
    """A rentable town stall (42.6.2) on ground row y: a 3 x 2 andesite slab pad (the tenda_lapak entity stands on it
    when rented) and the lapak_sewa_<n> point at its front middle looking at the street (hadap)."""
    o = Benda(k, x, y, z, hadap)
    if alas:
        for lx in (-1, 0, 1):
            for lz in (0, 1):
                o.slab(lx, 1, lz, "bottom", "minecraft:polished_andesite_slab")
    return o.titik("lapak_sewa_%d" % int(n), 0, 0, "north", None, dy=1)


TEMPAT_HANTU = ("bawah_jembatan", "gudang_kosong", "gang_buntu", "dekat_makam", "dermaga_tua")


def pasar_hantu(k, n, x, y, z, hadap="south", tempat="gang_buntu"):
    """Ghost Market spot (42.6.7, y = standing level): lapak_hantu entity + green lanterns at night; data tempat."""
    if tempat not in TEMPAT_HANTU:
        raise ValueError("tempat %r not in %s" % (tempat, TEMPAT_HANTU))
    return bernomor(k, "pasar_hantu", n, x, y, z, hadap, tempat=tempat)


def lumbung(k, x, y, z, hadap="south"):
    """Granary queue head (42.6.3 lumbung penstabil): warga queue with sacks in front of it."""
    return titik42(k, "lumbung", x, y, z, hadap)


def rekrut(k, x, y, z, hadap="south", bilik=None):
    """Sect recruitment desk spot (42.8.1); data bilik = booth number in a kantor_rekrut."""
    return titik42(k, "rekrut", x, y, z, hadap, **({"bilik": bilik} if bilik is not None else {}))


def sidang(k, x, y, z, hadap="south"):
    """Hearing seat of the magistrate / village head (42.6.6 Perkara)."""
    return titik42(k, "sidang", x, y, z, hadap)


def meja_dadu(k, x, y, z, hadap="south"):
    """Dice table entity spot (42.9.6, entity 1.3 x 0.9): keep the cell and its neighbours free."""
    return titik42(k, "meja_dadu", x, y, z, hadap)


def panggung(k, x0, y, z0, x1, z1, hadap="south", tinggi=4, **data):
    """A stage box point (38 type panggung) over x0..x1 x z0..z1 from standing level y up tinggi blocks."""
    lo = (min(x0, x1), min(z0, z1))
    hi = (max(x0, x1), max(z0, z1))
    d = {"dx": hi[0] - lo[0], "dy": tinggi - 1, "dz": hi[1] - lo[1]}
    d.update(data)
    return tulis_titik(k, "panggung", lo[0], y, lo[1], hadap, d)


def dijual(k, x, y, z, hadap="south", rumah="", ukuran="kecil"):
    """House-for-sale marker (42.6.1): inside the house at its door (standing level), data rumah = the name of the
    house's `bangunan` box point, ukuran (price class)."""
    if ukuran not in UKURAN_RUMAH:
        raise ValueError("ukuran %r not in %s" % (ukuran, UKURAN_RUMAH))
    return titik42(k, "dijual", x, y, z, hadap, rumah=rumah, ukuran=ukuran)


# ================================================================================================ kavling (42.9.4)
def kavling(k, x0, y, z0, jenis, gaya, hadap="south", kelas=None, pagar=True, bersih=6):
    """A city growth lot (42.9.4) whose buildable footprint starts at (x0, z0) on ground row y and is UKURAN_KELAS[kelas]
    big (kelas from KELAS_JENIS[jenis]); hadap = the street side. Draws: flat earth footprint with a surveyor's string
    between four corner stakes, a low fence ring ONE block outside the footprint (so a kecil kavling needs 9 x 9, a
    besar 15 x 15) with a gate in the middle of the street side and the board post two blocks to the gate's left
    (seen from the street) where the client draws the 'Kavling Kota' billboard on top; air above up to `bersih`.
    Writes the box point `kavling` (dx/dy/dz = footprint - 1 / TINGGI_RENCANA - 1, data rencana = the plan jenis,
    gaya, kelas; the key is `rencana` because `jenis` is the point-type argument of tl.Lot.kotak / Kanvas.kotak)."""
    if jenis not in JENIS_RENCANA:
        raise ValueError("jenis %r not in JENIS_RENCANA" % (jenis,))
    if gaya not in GAYA_KAVLING:
        raise ValueError("gaya %r not in GAYA_KAVLING" % (gaya,))
    kelas = kelas or KELAS_JENIS[jenis]
    if KELAS_JENIS[jenis] != kelas:
        raise ValueError("plan %s is a %s plan, not %s" % (jenis, KELAS_JENIS[jenis], kelas))
    w, d = UKURAN_KELAS[kelas]
    if pagar and not (k.inside(x0 - 1, y, z0 - 1) and k.inside(x0 + w, y + 1, z0 + d)):
        raise ValueError("kavling %s at (%d,%d) needs its fence ring (%dx%d) inside the piece" % (jenis, x0, z0, w + 2, d + 2))
    m = bahan(_kayu_dari(k))
    top = min(y + bersih, k.size[1] - 1)
    lo_x, lo_z, hi_x, hi_z = x0 - (1 if pagar else 0), z0 - (1 if pagar else 0), x0 + w - (0 if pagar else 1), \
        z0 + d - (0 if pagar else 1)
    for x in range(lo_x, hi_x + 1):
        for z in range(lo_z, hi_z + 1):
            for yy in range(y + 1, top + 1):
                k.put(x, yy, z, "minecraft:air")
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            h = (x * 7 + z * 13) % 11
            k.put(x, y, z, "minecraft:coarse_dirt" if h < 4 else "minecraft:dirt" if h < 9 else "minecraft:rooted_dirt")
    # surveyor's string along the footprint edge, stakes at the corners
    for x in range(x0, x0 + w):
        for z in (z0, z0 + d - 1):
            k.put(x, y + 1, z, "minecraft:tripwire", {"attached": "false", "disarmed": "false", "powered": "false",
                                                     "east": "true", "west": "true", "north": "false", "south": "false"})
    for z in range(z0, z0 + d):
        for x in (x0, x0 + w - 1):
            k.put(x, y + 1, z, "minecraft:tripwire", {"attached": "false", "disarmed": "false", "powered": "false",
                                                     "east": "false", "west": "false", "north": "true", "south": "true"})
    for x in (x0, x0 + w - 1):
        for z in (z0, z0 + d - 1):
            k.put(x, y + 1, z, m["slog"], {"axis": "y"})
    if pagar:
        cx, cz = x0 + w // 2, z0 + d // 2
        o = Benda(k, cx, y, cz, hadap)
        half = w // 2 + 1
        for x in range(lo_x, hi_x + 1):
            for z in range(lo_z, hi_z + 1):
                if x in (lo_x, hi_x) or z in (lo_z, hi_z):
                    k.put(x, y, z, "minecraft:dirt_path")
                    corner = x in (lo_x, hi_x) and z in (lo_z, hi_z)
                    k.put(x, y + 1, z, m["log"] if corner else m["fence"], {"axis": "y"} if corner else None)
        o.p(0, 1, -half, "gate", {"facing": "north", "in_wall": "false", "open": "false", "powered": "false"})
        # the board post: two to the gate's left seen from the street (the street looks back at the lot)
        o.p(2, 1, -half, "log", {"axis": "y"})
        o.p(2, 2, -half, "planks")
        o.slab(2, 3, -half)
    return tulis_titik(k, "kavling", x0, y, z0, hadap, {"dx": w - 1, "dy": TINGGI_RENCANA - 1, "dz": d - 1,
                                                       "rencana": jenis, "gaya": gaya, "kelas": kelas})


# ================================================================================================ cemetery (42.4.6, 42.12.1)
GAYA_MAKAM = {
    # surface, path, terrace face, mound, mound top, stele base, stele cap, gate post, gate roof stairs, gate roof slab
    "bukit": ("minecraft:grass_block", "minecraft:dirt_path", "minecraft:stone_bricks", "minecraft:podzol",
              "minecraft:moss_block", "minecraft:polished_andesite", "minecraft:polished_andesite_slab",
              "minecraft:stone_bricks", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab"),
    "desa": ("minecraft:grass_block", "minecraft:coarse_dirt", "minecraft:cobblestone", "minecraft:coarse_dirt",
             "minecraft:grass_block", "minecraft:mossy_cobblestone", "minecraft:mossy_cobblestone_slab",
             "minecraft:stripped_spruce_log", "minecraft:spruce_stairs", "minecraft:spruce_slab"),
    "laut": ("minecraft:grass_block", "minecraft:packed_mud", "minecraft:stone_bricks", "minecraft:rooted_dirt",
             "minecraft:moss_block", "minecraft:calcite", "minecraft:polished_diorite_slab", "minecraft:calcite",
             "minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine_slab"),
    "gurun": ("minecraft:smooth_sandstone", "minecraft:cut_sandstone", "minecraft:cut_sandstone", "minecraft:sandstone",
              "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone", "minecraft:cut_sandstone_slab",
              "minecraft:cut_sandstone", "minecraft:smooth_sandstone_stairs", "minecraft:smooth_sandstone_slab"),
    "pulau": ("minecraft:grass_block", "minecraft:mud_bricks", "minecraft:mud_bricks", "minecraft:moss_block",
              "minecraft:moss_block", "minecraft:polished_tuff", "minecraft:tuff_brick_slab", "minecraft:mud_bricks",
              "minecraft:mangrove_stairs", "minecraft:mangrove_slab"),
    "teras": ("minecraft:coarse_dirt", "minecraft:stone_bricks", "minecraft:deepslate_bricks", "minecraft:podzol",
              "minecraft:moss_block", "minecraft:polished_deepslate", "minecraft:polished_deepslate_slab",
              "minecraft:deepslate_bricks", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab"),
}
_TANGGA_MAKAM = {"bukit": "minecraft:stone_brick_stairs", "desa": "minecraft:cobblestone_stairs",
                 "laut": "minecraft:stone_brick_stairs", "gurun": "minecraft:sandstone_stairs",
                 "pulau": "minecraft:mud_brick_stairs", "teras": "minecraft:deepslate_brick_stairs"}
_SNOWY = ("minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium")


def _blok(b, u, dy, v, bid):
    b.p(u, dy, v, bid, {"snowy": "false"} if bid in _SNOWY else None)


def makam(k, x0, y, z0, w, d, hadap, n, mulai=0, gaya="bukit", bertingkat=False, gapura=True, pembakar=True):
    """A cemetery (Bukit Makam 32 x 32 / 48 graves, town 23 x 22 / 16, village 12 x 12 / 12) over x0..x0+w-1 x
    z0..z0+d-1 on ground row y, entered from the hadap side. Each grave is 3 wide and 4 deep: the visitor's row (the
    nisan_<mulai+i> point, looking at the stone), the stele (base + cap; the client writes name and years on it, one
    step ahead of the point), the earth mound behind; candles, flower pots and moss vary per grave. bertingkat raises
    each row one block (stone terrace face, stairs at the side lanes). The first three rows hold the entrance: a gate
    (gapura) and a paper-money burner with a `dupa` point and a pajangan:uang_kertas. Returns the Bilik frame."""
    if gaya not in GAYA_MAKAM:
        raise ValueError("gaya %r not in %s" % (gaya, tuple(GAYA_MAKAM)))
    g = GAYA_MAKAM[gaya]
    surf, jalan, muka, gundukan, puncak, alas, tutup, tiang, atap, atap_slab = g
    b = Bilik(k, x0, y, z0, w, d, 8, hadap, titik_maks=n + 4, kit="makam_" + gaya, min_wdh=(7, 8, 3))
    per_baris = (b.W - 2) // 4
    baris = (b.D - 3) // 4
    if per_baris * baris < n:
        raise ValueError("makam %dx%d holds %d graves, asked %d" % (b.W, b.D, per_baris * baris, n))
    rows_used = -(-n // per_baris)
    off = (b.W - per_baris * 4) // 2          # centre the grave columns; lanes on both sides
    # clear air over the whole yard, entrance plaza and paths
    for u in range(b.W):
        for v in range(b.D):
            for dy in range(1, 8):
                b.p(u, dy, v, "minecraft:air")
    for u in range(b.W):
        for v in range(3):
            _blok(b, u, 0, v, jalan if abs(u - b.pu) <= 1 or v == 2 else surf)
    i = 0
    for r in range(baris):
        s = r if bertingkat else 0
        v0 = 3 + 4 * r
        for v in range(v0, min(v0 + 4, b.D)):
            for u in range(b.W):
                for dy in range(0, s):
                    b.p(u, dy, v, muka if v == v0 else "minecraft:dirt")
                lane = u < off or u >= off + per_baris * 4
                _blok(b, u, s, v, jalan if (v == v0 or lane) else surf)
        if bertingkat and r > 0:
            for u in list(range(off)) + list(range(off + per_baris * 4, b.W)):
                b.tangga(u, s, v0, "south", blok=_TANGGA_MAKAM[gaya])
        if r >= rows_used:
            continue
        for c in range(per_baris):
            if i >= n:
                break
            u = off + c * 4
            h = _seed(gaya, mulai + i, x0, z0) % 97
            # mound (3 x 2) + rounded top
            for du in range(3):
                for dv in (2, 3):
                    _blok(b, u + du, s + 1, v0 + dv, gundukan)
            _blok(b, u + 1, s + 2, v0 + 3, puncak)
            if gaya == "gurun":
                b.slab(u + 1, s + 2, v0 + 2, "bottom", "minecraft:smooth_sandstone_slab")
            elif h % 3 == 0:
                b.p(u, s + 2, v0 + 3, "minecraft:short_grass")
            # the stele: base + cap (old ones mossy)
            b.p(u + 1, s + 1, v0 + 1, "minecraft:mossy_stone_bricks" if h % 11 == 0 and gaya in ("bukit", "teras")
                else alas)
            b.slab(u + 1, s + 2, v0 + 1, "bottom", tutup)
            # offerings beside the stele
            if h % 4 != 3:
                b.lilin(u + 2, s + 1, v0 + 1, 1 + h % 3, "white" if h % 2 else "")
            if h % 3 != 1:
                b.pot(u, s + 1, v0 + 1, ("minecraft:potted_white_tulip", "minecraft:potted_lily_of_the_valley",
                                         "minecraft:potted_oxeye_daisy", "minecraft:potted_azure_bluet")[h % 4])
            b.titik("nisan_%d" % (mulai + i), u + 1, v0, "south", None, dy=s + 1, wajib=True)
            i += 1
    if gapura:
        for u in (b.pu - 3, b.pu + 3):
            if 0 <= u < b.W:
                for dy in range(1, 5):
                    b.p(u, dy, 1, tiang, {"axis": "y"} if tiang.endswith("_log") else None)
        for u in range(b.pu - 4, b.pu + 5):
            if 0 <= u < b.W:
                b.tangga(u, 5, 0, "south", blok=atap)
                b.tangga(u, 5, 2, "north", blok=atap)
                b.slab(u, 6, 1, "bottom", atap_slab) if abs(u - b.pu) < 4 else b.slab(u, 5, 1, "top", atap_slab)
        for u in range(b.pu - 2, b.pu + 3):
            b.p(u, 5, 1, "minecraft:red_terracotta" if u != b.pu else "minecraft:gold_block")
    if pembakar:
        ub = b.pu + 5 if b.pu + 6 < b.W else max(0, b.pu - 6)
        b.p(ub, 1, 1, "minecraft:bricks")
        b.p(ub, 2, 1, "minecraft:blast_furnace", {"facing": "north", "lit": "false"})
        b.p(ub, 3, 1, "minecraft:brick_wall")
        b.p(ub + 1 if ub + 1 < b.W else ub - 1, 1, 1, "minecraft:polished_andesite")
        b.pajangan("uang_kertas", ub + 1 if ub + 1 < b.W else ub - 1, 2, 1)
        b.titik("dupa", ub, 0, "south")
    return b


# ================================================================================================ night district (42.9.6)
def jalan_malam(k, x0, z0, x1, z1, y, sumbu="x", tiang_setiap=4, lentera_maks=14, kursi_maks=4, dadu=True,
                ujung_bendera=True, warna="merah"):
    """Dresses a night-district street (42.9.6; lot jalan_malam, capital border Pasar Timur-Kumuh) over the rectangle
    x0..x1 x z0..z1 on ground row y, the street running along `sumbu`: red lantern posts on both edges every
    tiang_setiap blocks (arms over the street), floating cross-street lantern strings between facing posts (data
    tali), drinking-stool clusters (low table, stools, `kursi` points) on wide streets, a dice-table spot, two `obrol`
    points and wine flags (bendera_arak 'jiu') at the two ends. Returns a count dict."""
    lo_x, hi_x, lo_z, hi_z = min(x0, x1), max(x0, x1), min(z0, z1), max(z0, z1)
    along = range(lo_x + 1, hi_x, tiang_setiap) if sumbu == "x" else range(lo_z + 1, hi_z, tiang_setiap)
    lebar = (hi_z - lo_z + 1) if sumbu == "x" else (hi_x - lo_x + 1)
    n = {"lentera": 0, "kursi": 0, "meja_dadu": 0, "panji": 0, "obrol": 0}
    tali = 0
    for j, a in enumerate(along):
        if n["lentera"] + 2 > lentera_maks:
            break
        if sumbu == "x":
            ends = ((a, lo_z, "south"), (a, hi_z, "north"))
        else:
            ends = ((lo_x, a, "east"), (hi_x, a, "west"))
        for (x, z, f) in ends:
            tiang_lentera(k, x, y, z, f, 4, warna)
            n["lentera"] += 1
        if j % 2 == 1 and n["lentera"] < lentera_maks and lebar >= 7:
            mx, mz = ((a, (lo_z + hi_z) // 2) if sumbu == "x" else ((lo_x + hi_x) // 2, a))
            lentera_gantung(k, mx, y + 4, mz, "south" if sumbu == "x" else "east", warna, kait=False, tali=tali)
            tali += 1
            n["lentera"] += 1
    if lebar >= 9:
        spots = list(along)[:-1]
        for j, a in enumerate(spots):
            if n["kursi"] >= kursi_maks * 2:
                break
            mid = a + tiang_setiap // 2
            if sumbu == "x":
                side_z, inward = (lo_z + 2, "south") if j % 2 == 0 else (hi_z - 2, "north")
                o = Benda(k, mid, y, side_z, inward)
            else:
                side_x, inward = (lo_x + 2, "east") if j % 2 == 0 else (hi_x - 2, "west")
                o = Benda(k, side_x, y, mid, inward)
            o.p(0, 1, 0, "fence")
            o.p(0, 2, 0, "trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                      "waterlogged": "false"})
            for lx, f in ((-1, "east"), (1, "west")):
                o.slab(lx, 1, 0)
                o.titik("kursi", lx, 0, f)
                n["kursi"] += 1
            o.p(0, 1, 1, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
        if dadu and spots:
            a = spots[len(spots) // 2]
            if sumbu == "x":
                meja_dadu(k, a + tiang_setiap // 2, y + 1, (lo_z + hi_z) // 2 + 1, "north")
            else:
                meja_dadu(k, (lo_x + hi_x) // 2 + 1, y + 1, a + tiang_setiap // 2, "west")
            n["meja_dadu"] += 1
    for t, (ox, oz) in enumerate(((lo_x + 2, (lo_z + hi_z) // 2), (hi_x - 2, (lo_z + hi_z) // 2)) if sumbu == "x"
                                 else (((lo_x + hi_x) // 2, lo_z + 2), ((lo_x + hi_x) // 2, hi_z - 2))):
        titik42(k, "obrol", ox, y + 1, oz, ("east" if t == 0 else "west") if sumbu == "x" else
                ("south" if t == 0 else "north"))
        n["obrol"] += 1
    if ujung_bendera:
        if sumbu == "x":
            flags = ((lo_x, lo_z + 1, "east"), (hi_x, hi_z - 1, "west"))
        else:
            flags = ((lo_x + 1, lo_z, "south"), (hi_x - 1, hi_z, "north"))
        for (x, z, f) in flags:
            panji(k, x, y, z, f, "bendera_arak", "jiu", tiang=5)
            n["panji"] += 1
    return n


# ================================================================================================ scanners (42W-B, additive)
# Dressing passes over an already drawn piece (a district canvas before potong, or a Lot): they read the blocks and
# only add points (never a block), so they can run last on any generator's output.
def _kolom(k, x0, z0, x1, z1):
    """{(x, z): sorted list of y with a non-air block} inside x0..x1 x z0..z1."""
    out = {}
    for (x, y, z), cell in k.cells.items():
        if x0 <= x <= x1 and z0 <= z <= z1 and cell[0][0] not in ("minecraft:air", "minecraft:cave_air"):
            out.setdefault((x, z), []).append(y)
    for v in out.values():
        v.sort()
    return out


def _atap(bid):
    n = (bid or "").split(":")[-1]
    return "stairs" in n or "slab" in n


def _jauh(p, dipilih, jarak):
    return all(max(abs(p[0] - q[0]), abs(p[1] - q[1])) >= jarak for q in dipilih)


def lentera_atap(k, x0, z0, x1, z1, y, maks=6, jarak=6, warna="merah", tinggi=(3, 7), kolom=None, salt=0):
    """Paper lanterns under the eaves (42.9.2): finds cells over a walking floor at or above ground row y (the street,
    a podium or a veranda; the next tinggi[0]..tinggi[1] cells above it clear) whose first block above is a roof
    stair/slab and that lie at an eave (a side neighbour has open sky above that floor), and writes up to `maks`
    lentera_gantung points right under the eave (no block), at least `jarak` apart, looking out to the open side.
    Deterministic (hash order). Returns the written points."""
    kol = kolom if kolom is not None else _kolom(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1)
    kand = []
    for (x, z), ys in kol.items():
        if not (x0 <= x <= x1 and z0 <= z <= z1):
            continue
        for fy, ry in zip(ys, ys[1:]):
            if fy < y or not (tinggi[0] + 1 <= ry - fy <= tinggi[1] + 1) or not _atap(k.get(x, ry, z)):
                continue
            buka = [f for f, (dx, dz) in FACE.items()
                    if (x + dx, z + dz) in kol and not any(yy > fy + 1 for yy in kol[(x + dx, z + dz)])]
            if buka:
                kand.append((_seed("lentera", x, z, salt), x, ry - 1, z, buka[0]))
            break
    kand.sort()
    out, dipilih = [], []
    for _h, x, yy, z, f in kand:
        if len(out) >= maks:
            break
        if not _jauh((x, z), dipilih, jarak):
            continue
        e = lentera_gantung(k, x, yy, z, f, warna, kait=False)
        if e is not None:
            out.append(e)
            dipilih.append((x, z))
    return out


def kucing_atap(k, x0, z0, x1, z1, y, maks=2, jarak=10, kolom=None, salt=0):
    """Cat napping spots on roofs (42.7.5): the air cell over a roof stair/slab that is the top block of its column and at
    least 5 above ground row y. Returns the written points."""
    kol = kolom if kolom is not None else _kolom(k, x0, z0, x1, z1)
    kand = []
    for (x, z), ys in kol.items():
        if not (x0 <= x <= x1 and z0 <= z <= z1) or not ys:
            continue
        t = ys[-1]
        if t < y + 5 or not _atap(k.get(x, t, z)) or not k.inside(x, t + 1, z):
            continue
        kand.append((_seed("kucing", x, z, salt), x, t + 1, z))
    kand.sort()
    out, dipilih = [], []
    for _h, x, yy, z in kand:
        if len(out) >= maks:
            break
        if _jauh((x, z), dipilih, jarak):
            e = kucing_tidur(k, x, yy, z, FACINGS[_seed(x, z) % 4])
            if e is not None:
                out.append(e)
                dipilih.append((x, z))
    return out


_TANAH_LAHAN = ("grass_block", "podzol", "coarse_dirt", "dirt", "dirt_path", "rooted_dirt", "packed_mud", "gravel",
                "moss_block", "polished_andesite", "andesite", "stone_bricks", "mossy_stone_bricks", "cobblestone",
                "mossy_cobblestone", "smooth_stone", "spruce_planks", "mud")


def cari_lahan(k, x0, z0, x1, z1, y, w, d, maks=1, jarak=1, bersih=6, sudah=()):
    """Free flat dry ground for a kavling (42.12.1A: only flat dry lots): up to `maks` rectangles w x d (the OUTER size,
    fence ring included) inside x0..x1 x z0..z1 whose cells - grown by `jarak` - all have a plain ground block on row y
    (earth, grass, gravel, paving; never water, crops or a building floor raised above y), nothing above it up to
    y + bersih and no life point. Picks greedily in a fixed scan order (row by row from the north-west), so the result
    is deterministic; `sudah` = rectangles (x0, z0, x1, z1) already taken. Returns [(x0, z0)] outer corners."""
    blok = set()
    for (x, yy, z), cell in k.cells.items():
        if not (x0 - jarak <= x <= x1 + jarak and z0 - jarak <= z <= z1 + jarak):
            continue
        bid = cell[0][0]
        if y < yy <= y + bersih and bid not in ("minecraft:air", "minecraft:cave_air"):
            blok.add((x, z))
    for t in getattr(k, "titik_list", []):
        blok.add((t["pos"][0], t["pos"][2]))
    for (a0, b0, a1, b1) in sudah:
        for x in range(a0, a1 + 1):
            for z in range(b0, b1 + 1):
                blok.add((x, z))

    def bebas(x, z):
        if (x, z) in blok or not k.inside(x, y, z):
            return False
        g = k.get(x, y, z)
        return g is not None and g.split(":")[-1] in _TANAH_LAHAN

    out = []
    for fz in range(z0, z1 - d + 2):
        for fx in range(x0, x1 - w + 2):
            if len(out) >= maks:
                return out
            if any(fx - jarak <= ox + w - 1 + jarak and ox - jarak <= fx + w - 1 + jarak and
                   fz - jarak <= oz + d - 1 + jarak and oz - jarak <= fz + d - 1 + jarak for ox, oz in out):
                continue
            if all(bebas(x, z) for x in range(fx - jarak, fx + w + jarak) for z in range(fz - jarak, fz + d + jarak)):
                out.append((fx, fz))
    return out


# footprint (w, d) facing south and the builder's (x, z) offset from the footprint's north-west corner
_KEBIASAAN = {"catur": (5, 6, 2, 4), "gantung_sangkar": (5, 6, 2, 1), "jongkok": (3, 3, 1, 0), "sumur": (5, 5, 2, 2),
              "tambat": (6, 3, 3, 0), "kandang": (7, 7, 0, 0)}


def kebiasaan(k, x0, z0, x1, z1, y, jenis, maks=1, jarak=1, sudah=()):
    """Habit spots of 42.4.12 on free flat ground (cari_lahan) inside x0..x1 x z0..z1: catur (stone table, tree),
    gantung_sangkar (tree branch + bench), jongkok (points only, 2 squatters), sumur (well), tambat (hitching rail,
    42.7.5), kandang (a 7 x 7 chicken pen with its coop, 42.7.5); all facing south.
    Returns the list of footprints taken (x0, z0, x1, z1)."""
    w, d, ox, oz = _KEBIASAAN[jenis]
    out = []
    for fx, fz in cari_lahan(k, x0, z0, x1, z1, y, w, d, maks, jarak, sudah=sudah):
        x, z = fx + ox, fz + oz
        if jenis == "catur":
            catur(k, x, y, z, "south")
        elif jenis == "gantung_sangkar":
            gantung_sangkar(k, x, y, z, "south")
        elif jenis == "jongkok":
            jongkok(k, x, y, z, "south", 2)
        elif jenis == "tambat":
            tambat(k, x, y, z, "south")
        elif jenis == "kandang":
            kandang(k, fx, y, fz, fx + w - 1, fz + d - 1, "ayam_kota", "south")
        else:
            sumur(k, x, y, z, "south")
        out.append((fx, fz, fx + w - 1, fz + d - 1))
    return out


def pancing_kolam(k, x0, z0, x1, z1, y, maks=2, jarak=4):
    """Fishing spots on pond banks (42.4.12 pemancing): a dry standing cell (ground on row y, air above, no point)
    next to water on row y; the point looks at the water. At least `jarak` apart. Returns the written points."""
    pts = {(t["pos"][0], t["pos"][2]) for t in getattr(k, "titik_list", [])}
    air = (None, "minecraft:air", "minecraft:cave_air")
    out, dipilih = [], []
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            if len(out) >= maks:
                return out
            g = k.get(x, y, z)
            if g is None or "water" in g or g in air or (x, z) in pts:
                continue
            if k.get(x, y + 1, z) not in air or k.get(x, y + 2, z) not in air:
                continue
            for f, (dx, dz) in FACE.items():
                w = k.get(x + dx, y, z + dz) or ""
                if "water" in w and k.get(x + dx, y + 1, z + dz) in air and _jauh((x, z), dipilih, jarak):
                    e = tulis_titik(k, "pancing", x, y + 1, z, f)
                    if e is not None:
                        out.append(e)
                        dipilih.append((x, z))
                    break
    return out


_DAPUR = ("minecraft:smoker", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:campfire")


def cerobong_dapur(k, x0, z0, x1, z1, y, maks=2, jarak=5):
    """Chimneys over the kitchens (42.9.2): for each stove block (smoker, furnace, blast furnace, campfire) inside the
    rectangle whose column is roofed (the top block lies 4-16 above ground row y and is a roof stair/slab or a full
    block), a two-block brick stack on the roof top and the `cerobong` point over it. At least `jarak` apart."""
    kol = _kolom(k, x0, z0, x1, z1)
    kand = []
    for (x, yy, z), cell in k.cells.items():
        if x0 <= x <= x1 and z0 <= z <= z1 and cell[0][0] in _DAPUR and y < yy <= y + 8:
            ys = kol.get((x, z), ())
            if not ys:
                continue
            t = ys[-1]
            if y + 4 <= t <= y + 16 and t > yy + 1 and k.inside(x, t + 3, z):
                kand.append((x, z, t))
    kand.sort()
    out, dipilih = [], []
    for x, z, t in kand:
        if len(out) >= maks or not _jauh((x, z), dipilih, jarak):
            continue
        out.append(cerobong(k, x, t + 1, z, 2))
        dipilih.append((x, z))
    return out


def kavling_di(k, x0, z0, x1, z1, y, rencana, gaya, hadap, maks=1, jarak=1, sudah=()):
    """Finds free ground (cari_lahan) for each plan jenis of `rencana` in turn (cycled) and draws a fenced kavling
    there facing `hadap` (or hadap(x, z) -> facing). Returns the written kavling points."""
    out, taken = [], list(sudah)
    for i in range(maks):
        jenis = rencana[i % len(rencana)]
        w, d = UKURAN_KELAS[KELAS_JENIS[jenis]]
        spot = cari_lahan(k, x0, z0, x1, z1, y, w + 2, d + 2, 1, jarak, sudah=taken)
        if not spot:
            continue
        fx, fz = spot[0]
        f = hadap(fx + w // 2 + 1, fz + d // 2 + 1) if callable(hadap) else hadap
        e = kavling(k, fx + 1, y, fz + 1, jenis, gaya, f)
        if e is not None:
            out.append(e)
        taken.append((fx, fz, fx + w + 1, fz + d + 1))
    return out


# ================================================================================================ interior kits (42.9.1)
# Every kit: interior_<id>(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False) -> Bilik.
# Work points are written first, then seats, then displays, so a small titik_maks keeps the important ones.
# papan=False skips the sign over the door (inner rooms); api=True lights the kitchen campfire (only for templates
# whose path names a kitchen, cek_tempat CAMPFIRE_OK).
_BUNGA = ("minecraft:potted_bamboo", "minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush",
          "minecraft:potted_fern", "minecraft:potted_spruce_sapling", "minecraft:potted_red_tulip")


def _baru(kit, k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, min_wdh):
    return Bilik(k, x0, y0, z0, w, d, h, hadap, pintu=pintu, titik_maks=titik_maks, kit=kit, api=api, min_wdh=min_wdh)


def _gantung(b, u, v, blok, props=None):
    """A block hanging under the ceiling (chain, roots, wool lantern) when the ceiling exists."""
    top = b.h + 1
    if _plafon_padat(b.get(u, top, v)):
        b.p(u, top - 1, v, blok, props)
        return True
    return False


def _hias_dinding(b, warna="red", setiap=4, dy=2):
    """Wall banners along both side walls (inside cells against the wall), every `setiap` blocks."""
    for v in range(2, b.D - 1, setiap):
        b.p(0, dy, v, "minecraft:%s_wall_banner" % warna, {"facing": "east"})
        b.p(b.W - 1, dy, v, "minecraft:%s_wall_banner" % warna, {"facing": "west"})


def _sudut_bunga(b):
    for u in (0, b.W - 1):
        if b.bebas(u, 0):
            b.pot(u, 1, 0, _BUNGA[(u + b.D) % len(_BUNGA)])
            b.pakai([(u, 0)])


def _meja_kursi(b, u, v, jenis="meja_makan", sisi=("w", "e"), atas="plate"):
    """A 1-block table at (u, v) with chairs on the named sides (w e n s); a point per chair. False when blocked."""
    off = {"w": (-1, 0, "east"), "e": (1, 0, "west"), "n": (0, -1, "south"), "s": (0, 1, "north")}
    cells = [(u, v)] + [(u + off[s][0], v + off[s][1]) for s in sisi]
    if not b.semua_bebas(cells):
        return False
    b.meja(u, v, atas)
    for s in sisi:
        du, dv, f = off[s]
        b.kursi(u + du, v + dv, f)
        b.titik(jenis, u + du, v + dv, f)
    b.pakai(cells, 0)
    return True


# wall pattern (Bilik.isi_dinding tokens), sides, lamp spacing (0 = none) per kit
DANDAN = {
    "rumah_makan": (("tong", "-", "bunga", "-", "guci", "-", "rak", "-"), "wef", 4),
    "kedai_teh": (("bunga", "-", "rak", "-", "pot", "-"), "wef", 4),
    "kedai_arak": (("tong2", "guci", "-", "tong", "guci", "-", "panji", "-"), "wef", 4),
    "apotek": (("rak", "bunga", "-", "rak", "-", "tong"), "wef", 4),
    "penjahit": (("kain", "-", "rak", "kain", "-"), "wef", 4),
    "pandai": (("tong", "-", "kayu", "-", "tong2", "-"), "wef", 4),
    "tukang_kayu": (("kayu", "kayu", "-", "tong", "-", "jerami"), "wef", 4),
    "penjual_ikan": (("tong", "guci", "-", "tong2", "-"), "wef", 4),
    "toko_kue": (("tepung", "tong", "-", "rak", "-", "bunga", "-"), "wef", 4),
    "toko_lentera": (("pot", "-", "tong", "-"), "f", 0),
    "toko_buku": (("buku", "-", "bunga", "-"), "f", 4),
    "toko_jimat": (("lilin", "-", "bunga", "-", "rak", "-"), "wef", 4),
    "rumah_gadai": (("peti", "tong", "-", "guci", "-", "tong2"), "wef", 4),
    "rumah_judi": (("guci", "-", "bunga", "-", "tong", "-", "panji"), "wef", 4),
    "penginapan": (("bunga", "-", "tong", "-"), "f", 0),
    "sekolah": (("rak", "-", "bunga", "-", "buku", "-"), "wef", 4),
    "altar_leluhur": (("bunga", "-"), "we", 0),
    "rumah_petani": (("tong", "jerami", "-", "guci", "-", "tepung"), "wef", 4),
    "rumah_nelayan": (("tong", "guci", "-", "tong2", "-"), "wef", 4),
    "rumah_sarjana": (("buku", "-", "bunga", "-", "rak", "-"), "wef", 4),
    "rumah_kaya": (("guci", "-", "bunga", "-", "rak", "-", "panji", "-"), "wef", 4),
    "gudang": (("peti", "tong2", "-"), "f", 5),
    "balai_kota": (("bunga", "-", "rak", "-", "panji", "-"), "wef", 4),
    "kuil_kecil": (("lilin", "-", "bunga", "-"), "wef", 4),
}


# free-floor clusters per kit (placed greedily in the middle, one free cell around each)
TENGAH = {"pandai": ("bijih", "bangku_kerja", "peti_tumpuk"), "tukang_kayu": ("kudakuda", "bangku_kerja", "kudakuda"),
          "toko_kue": ("meja_pajang",), "apotek": ("meja_konsul",), "rumah_gadai": ("meja_pajang", "peti_tumpuk"),
          "penjahit": ("meja_pajang",), "toko_jimat": ("meja_pajang",), "toko_buku": ("meja_pajang",),
          "gudang": ("peti_tumpuk",)}


def _kluster(b, jenis, u, v):
    """A two-cell floor cluster at local (u, v), (u + 1, v); False when the cells (+1 margin) are not free."""
    cells = [(u, v), (u + 1, v)]
    if u < 2 or u + 2 > b.W - 2 or not b.semua_bebas(cells) or not all(b.kosong(cu, cv) for cu, cv in cells):
        return False
    ring = [(cu + du, cv + dv) for cu, cv in cells for du in (-1, 0, 1) for dv in (-1, 0, 1)]
    if not all(b.bebas(cu, cv) for cu, cv in ring if b.ada(cu, cv)):
        return False
    if jenis == "bijih":
        b.p(u, 1, v, "minecraft:raw_iron_block")
        b.p(u + 1, 1, v, "minecraft:coal_block")
        b.slab(u, 2, v, "bottom", "minecraft:cobblestone_slab")
    elif jenis == "bangku_kerja":
        b.p(u, 1, v, "minecraft:crafting_table")
        b.tong(u + 1, 1, v, "north")
        b.meja_papan(u + 1, v, 2)
    elif jenis == "peti_tumpuk":
        b.peti(u, 1, v, "north")
        b.tong(u + 1, 1, v, "up")
        b.tong(u + 1, 2, v, "east")
    elif jenis == "kudakuda":
        b.p(u, 1, v, "fence")
        b.p(u + 1, 1, v, "fence")
        b.p(u, 2, v, "log", {"axis": "x"})
        b.p(u + 1, 2, v, "log", {"axis": "x"})
    elif jenis == "meja_pajang":
        b.tong(u, 1, v, "north")
        b.tong(u + 1, 1, v, "north")
        b.meja_papan(u, v, 2)
        b.meja_papan(u + 1, v, 2)
    elif jenis == "meja_konsul":
        b.meja(u, v)
        b.kursi(u + 1, v, "west")
    b.pakai(ring)
    return True


def _selesai(b):
    """The common last pass of every kit: free-floor clusters (TENGAH), wall dressing by DANDAN pattern, then lamps
    where the room is still dark."""
    pola, sisi, jarak = DANDAN[b.kit]
    jenis = TENGAH.get(b.kit)
    if jenis:
        i = [0]

        def _coba(u, v):
            if _kluster(b, jenis[i[0] % len(jenis)], u, v):
                i[0] += 1
                return True
            return False

        b.atur(2, b.D - 4, 3, 2, _coba, 2)
    b.isi_dinding(pola, sisi)
    if jarak:
        b.isi_lampu(jarak)


def interior_rumah_makan(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Eating house / noodle shop: open kitchen on the back wall (smoker, hooded campfire, ingredient barrels, bowl
    shelves), a serving counter with a cutting board and a walk-through gap, dining tables with chairs in rows,
    lanterns over the tables, red banners; points kerja:wajan, kerja:talenan, meja_makan x6-12, pajangan:mangkuk/mi."""
    b = _baru("rumah_makan", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 8, 3))
    W, D = b.W, b.D
    b.lantai(0, D - 3, W - 1, D - 1, "minecraft:stone_bricks", "minecraft:polished_andesite", 4)
    # the kitchen line on the back wall
    b.p(1, 1, D - 1, "minecraft:smoker", {"facing": "north", "lit": "false"})
    b.p(2, 1, D - 1, "minecraft:campfire", {"facing": "north", "lit": "true" if b.api else "false",
                                            "signal_fire": "false", "waterlogged": "false"})
    for u in (1, 2, 3):
        b.tangga(u, 3, D - 1, "south", "top", "minecraft:brick_stairs")
    b.dapur = b.xz(2, D - 1)
    b.tong(3, 1, D - 1, "north")
    b.tong(3, 2, D - 1, "up")
    for u in range(4, W - 1):
        b.tong(u, 1, D - 1, "north" if u % 2 else "up")
        b.rak(u, 2, D - 1, "north")
    b.p(W - 1, 1, D - 1, "minecraft:water_cauldron", {"level": "3"})
    # counter with the cutting board; gap at W - 2
    b.konter(0, W - 1, D - 3, "north", "barrel", celah=W - 2)
    b.p(4, 2, D - 3, "plate", {"powered": "false"})
    b.pakai([(u, v) for u in range(W) for v in (D - 3, D - 2, D - 1)])
    b.titik("kerja:wajan", 2, D - 2, "south")
    b.titik("kerja:talenan", 4, D - 2, "north")
    b.pajangan("mangkuk", 1, 3, D - 3)
    # dining rows (aisle in front of the counter and from the door)
    b.lorong(3, 0, D - 4)
    b.pakai([(u, D - 4) for u in range(W)])
    meja = []

    def _set(u, v):
        if _meja_kursi(b, u + 1, v):
            meja.append((u + 1, v))
            b.lentera(u + 1, v)
            return True
        return False

    b.atur(2, D - 5, 2, 3, _set, 1)
    if meja:
        b.pajangan("mi", meja[0][0], 2, meja[0][1])
    _hias_dinding(b, "red", 5)
    _sudut_bunga(b)
    if papan:
        b.papan("mian")
    _selesai(b)
    return b


def interior_kedai_teh(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Teahouse: a storyteller's platform with chair, folding screen and side table in the back-left corner (panggung
    box), the tea corner back-right (L counter, kettle cauldron, barrels with teapots on top), round tables with four
    chairs; points kerja:teko, kursi x8, panggung, pajangan:teko."""
    b = _baru("kedai_teh", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (8, 8, 3))
    W, D = b.W, b.D
    # tea corner
    b.konter(W - 3, W - 1, D - 3, "north", "barrel")
    for u in range(W - 4, W):
        b.tong(u, 1, D - 1, "north")
        b.pot(u, 2, D - 1, "minecraft:flower_pot")
    b.p(W - 1, 1, D - 2, "minecraft:water_cauldron", {"level": "3"})
    b.pakai([(u, v) for u in range(W - 4, W) for v in range(D - 3, D)])
    b.titik("kerja:teko", W - 3, D - 2, "north")
    # storyteller platform (top slabs), chair, screen, side table
    for u in range(0, 4):
        for v in (D - 2, D - 1):
            b.slab(u, 1, v, "top")
    b.tangga(1, 2, D - 2, "south")
    if h >= 4:
        for u in range(0, 4):
            b.p(u, 2, D - 1, "trapdoor", {"facing": "north" if u % 2 else "south", "half": "bottom", "open": "true",
                                          "powered": "false", "waterlogged": "false"})
    b.meja_papan(2, D - 2, 2)
    b.pakai([(u, v) for u in range(0, 5) for v in range(D - 3, D)])
    b.kotak_titik("panggung", 0, D - 2, 3, D - 1, 2, 4, "north", nama="pendongeng")
    b.pajangan("teko", W - 2, 3, D - 3)
    # round tables
    b.lorong(3, 0, D - 4)
    meja = []

    def _bundar(u, v):
        if _meja_kursi(b, u + 1, v + 1, "kursi", ("w", "e", "n", "s")):
            meja.append((u + 1, v + 1))
            b.lentera(u + 1, v + 1)
            b.pakai([(u, v), (u + 2, v), (u, v + 2), (u + 2, v + 2)])
            return True
        return False

    b.atur(1, D - 6, 4, 3, _bundar, 1)
    b.atur(2, D - 5, 2, 2, lambda u, v: _meja_kursi(b, u, v, "kursi", ("e",)), 1)
    if meja:
        b.pajangan("teko", meja[0][0], 2, meja[0][1])
    _sudut_bunga(b)
    if papan:
        b.papan("cha")
    _selesai(b)
    return b


def interior_kedai_arak(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Wine house: barrel wall two high on the back with jars on top, a bar counter and the bartender's lane, big
    wine jars by the door, long tables with stools, a dice corner, red banners and runner; points kerja:guci, kursi
    x8, meja_dadu, pajangan:guci_arak; flag sign 'jiu'."""
    b = _baru("kedai_arak", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (8, 9, 3))
    W, D = b.W, b.D
    for u in range(W):
        b.tong(u, 1, D - 1, "north")
        b.tong(u, 2, D - 1, "north" if u % 3 else "up")
        if h >= 4 and u % 2 == 0:
            b.guci(u, 3, D - 1)
    b.konter(1, W - 1, D - 3, "north", "barrel", celah=W - 2)
    b.guci(0, 1, D - 3)
    b.guci(0, 1, D - 2)
    b.pakai([(u, v) for u in range(W) for v in range(D - 3, D)])
    b.titik("kerja:guci", 2, D - 2, "south")
    b.pajangan("guci_arak", 3, 3, D - 3)
    for u in (0, W - 1):
        b.guci(u, 1, 2)
        b.pakai([(u, 2)])
    b.lorong(3, 0, D - 4)
    b.pakai([(u, D - 4) for u in range(W)])
    # dice corner (left side, two stools): the meja_dadu entity stands on free floor
    dadu = None
    for v in range(D - 6, 2, -1):
        cells = [(0, v), (1, v), (2, v), (1, v - 1), (1, v + 1)]
        if b.semua_bebas(cells):
            dadu = v
            break
    if dadu is not None:
        b.titik("meja_dadu", 1, dadu, "east")
        b.bangku(1, dadu - 1)
        b.bangku(1, dadu + 1)
        b.pakai([(u, v) for u in range(0, 3) for v in range(dadu - 1, dadu + 2)], 0)
    # long tables of three
    def _panjang(u0, v):
        cells = [(u0 + i, v) for i in range(3)] + [(u0 + i, v + s) for i in range(3) for s in (-1, 1)]
        if not b.semua_bebas(cells):
            return False
        for i in range(3):
            b.meja(u0 + i, v)
        for i in range(3):
            for s, f in ((-1, "south"), (1, "north")):
                b.bangku(u0 + i, v + s)
                if i != 1:
                    b.titik("kursi", u0 + i, v + s, f)
        b.lentera(u0 + 1, v)
        b.pakai(cells)
        return True

    b.atur(3, D - 6, 4, 3, _panjang, 1)
    b.karpet(b.pu, 2, b.pu, D - 5, "red")
    _hias_dinding(b, "red", 4)
    if papan:
        b.papan("jiu", "bendera_arak")
    _selesai(b)
    return b


def interior_apotek(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Pill shop / physician: the hundred-drawer wall (barrels, chiseled bookshelves and trapdoor drawer fronts in a
    pattern), a counter with a balance (weighted plate) and a brewing stand, a stone mortar, dried herbs hanging from
    the ceiling, a waiting bench with potted herbs; points kerja:laci, kerja:lumpang, pajangan:botol_pil/herba."""
    b = _baru("apotek", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    top = min(h, 4)
    for u in range(W):
        for dy in range(1, top + 1):
            c = (u + dy * 2) % 3
            if c == 0:
                b.tong(u, dy, D - 1, "north")
            elif c == 1:
                b.p(u, dy, D - 1, "minecraft:chiseled_bookshelf", {
                    "facing": "north", "slot_0_occupied": "true", "slot_1_occupied": str((u + dy) % 2 == 0).lower(),
                    "slot_2_occupied": "true", "slot_3_occupied": str(u % 3 == 0).lower(), "slot_4_occupied": "true",
                    "slot_5_occupied": str(dy % 2 == 1).lower()})
            else:
                b.p(u, dy, D - 1, "trapdoor", {"facing": "north", "half": "bottom", "open": "true", "powered": "false",
                                               "waterlogged": "false"})
    b.konter(0, W - 2, D - 3, "north", "barrel", celah=W - 2)
    b.p(1, 2, D - 3, "minecraft:heavy_weighted_pressure_plate", {"power": "0"})
    b.p(3, 2, D - 3, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false",
                                                 "has_bottle_2": "true"})
    b.p(W - 1, 1, D - 2, "minecraft:cauldron")
    b.pakai([(u, v) for u in range(W) for v in range(D - 3, D)])
    b.titik("kerja:laci", 2, D - 2, "south")
    b.titik("kerja:lumpang", W - 2, D - 2, "east")
    b.pajangan("botol_pil", 2, 3, D - 3)
    for u in range(1, W - 1, 2):
        _gantung(b, u, D - 2, "minecraft:hanging_roots", {"waterlogged": "false"})
    # waiting bench on the left wall, herbs by the door
    for v in range(2, min(5, D - 4)):
        b.kursi(0, v, "east")
        if v == 2:
            b.titik("kursi", 0, v, "east")
        b.pakai([(0, v)])
    if b.bebas(W - 1, 2):
        b.meja(W - 1, 2, "minecraft:potted_fern")
        b.pakai([(W - 1, 2)])
    if b.bebas(W - 1, 3):
        b.meja_papan(W - 1, 3, 1)
        b.pajangan("herba", W - 1, 2, 3)
        b.pakai([(W - 1, 3)])
    _sudut_bunga(b)
    b.lentera(b.pu, D // 2)
    if papan:
        b.papan("yao")
    _selesai(b)
    return b


def _baju(b, u, v, warna, yaw=0.0):
    """An armour stand in a dyed robe (leather chest + legs) on a stone foot."""
    b.p(u, 0, v, "minecraft:polished_andesite")
    b.entitas(u, 1, v, {
        "id": "minecraft:armor_stand", "Invulnerable": True, "DisabledSlots": 4144959, "ShowArms": True,
        "PersistenceRequired": True,
        "equipment": {"chest": {"id": "minecraft:leather_chestplate", "count": 1,
                                "components": {"minecraft:dyed_color": warna}},
                      "legs": {"id": "minecraft:leather_leggings", "count": 1,
                               "components": {"minecraft:dyed_color": warna}}}}, yaw)


def interior_penjahit(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Cloth shop / tailor: looms at the back, bolts of coloured wool stacked on the side wall, robes on two armour
    stands by the door, a long cutting table with laid cloth, thread spools (barrels); points kerja:tenun,
    kerja:gunting, pajangan:kain."""
    b = _baru("penjahit", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    for u in (1, 3):
        b.p(u, 1, D - 1, "minecraft:loom", {"facing": "north"})
    b.tong(0, 1, D - 1, "up")
    b.tong(2, 1, D - 1, "up")
    b.pakai([(u, v) for u in range(0, 5) for v in (D - 2, D - 1)])
    b.titik("kerja:tenun", 1, D - 2, "south")
    kain = ("red", "blue", "yellow", "white", "green", "purple", "light_blue", "pink", "cyan", "orange")
    for i, v in enumerate(range(2, D - 1)):
        for dy in range(1, min(h, 3) + 1):
            if (v + dy) % 4 != 0:
                b.p(W - 1, dy, v, "minecraft:%s_wool" % kain[(i * 3 + dy) % len(kain)])
        b.pakai([(W - 1, v)])
    # cutting table (trapdoor tops with cloth laid on them)
    v = D // 2
    cells = [(u, v) for u in range(1, min(W - 2, 5))]
    for (u, vv) in cells:
        b.p(u, 1, vv, "fence")
        b.meja_papan(u, vv, 2)
        b.p(u, 3, vv, "minecraft:%s_carpet" % ("white" if u % 2 else "red"))
    b.pakai(cells + [(u, v + 1) for u in range(1, min(W - 2, 5))])
    b.titik("kerja:gunting", 2, v + 1, "north")
    b.pajangan("kain", 3, 3, v)
    for u, warna in ((0, 0x9B2D30), (W - 2, 0x2F5D8A)):
        if b.bebas(u, 2):
            _baju(b, u, 2, warna, 180.0)
            b.pakai([(u, 2)])
    b.lentera(b.pu, v - 1)
    if papan:
        b.papan("bu")
    _selesai(b)
    return b


def interior_pandai(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Smithy: a brick forge with blast furnace and brick hood on the back wall, an anvil on a log, grindstone and
    smithing table, a quench tub (water cauldron), a weapon rack (shelves), chains hanging, stone floor; points
    kerja:palu, kerja:tungku, pajangan:pedang. Bilik.dapur = the forge (sparks + smoke)."""
    b = _baru("pandai", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    b.lantai(0, 0, W - 1, D - 1, "minecraft:stone_bricks", "minecraft:cobblestone", 5)
    b.p(1, 1, D - 1, "minecraft:bricks")
    b.p(2, 1, D - 1, "minecraft:blast_furnace", {"facing": "north", "lit": "false"})
    b.p(3, 1, D - 1, "minecraft:bricks")
    for u in (1, 2, 3):
        b.p(u, 2, D - 1, "minecraft:bricks")
        if h >= 4:
            b.tangga(u, 3, D - 2, "south", "top", "minecraft:brick_stairs")
    b.dapur = b.xz(2, D - 1)
    b.p(4, 1, D - 1, "minecraft:smithing_table")
    b.p(W - 1, 1, D - 1, "minecraft:water_cauldron", {"level": "3"})
    b.pakai([(u, v) for u in range(W) for v in (D - 2, D - 1)])
    b.titik("kerja:tungku", 2, D - 2, "south")
    # anvil on a stump in the middle, grindstone beside
    cu, cv = W // 2, D // 2
    b.p(cu, 1, cv, "minecraft:anvil", {"facing": "east"})
    b.p(cu - 2, 1, cv, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    b.pakai([(cu, cv), (cu - 2, cv)], 0)
    b.titik("kerja:palu", cu, cv + 1, "north")
    b.pakai([(cu, cv + 1)])
    # weapon rack on the right wall
    for v in range(2, min(D - 3, 6)):
        b.rak(W - 1, 2, v, "west")
        if v % 2:
            b.p(W - 1, 1, v, "minecraft:polished_andesite")
        else:
            b.p(W - 1, 1, v, "log", {"axis": "y"})
        b.pakai([(W - 1, v)])
    b.pajangan("pedang", W - 1, 3, 3)
    for u in range(1, W - 1, 3):
        _gantung(b, u, 2, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    b.lentera(cu, cv - 2)
    if papan:
        b.papan("tie")
    _selesai(b)
    return b


def interior_tukang_kayu(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Carpenter's shop: a log pile (horizontal logs in a pyramid) and stacked boards, a saw bench (stonecutter) and a
    crafting table, half-built chairs and a table frame, tool rack shelves; points kerja:gergaji, pajangan:papan."""
    b = _baru("tukang_kayu", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    # log pile in the back-left corner
    for i, u in enumerate(range(0, min(4, W - 3))):
        for dy in range(1, 3 - (i // 2) + 1):
            b.p(u, dy, D - 1, "log", {"axis": "x"})
            if dy < 3:
                b.p(u, dy, D - 2, "log", {"axis": "x"})
    b.pakai([(u, v) for u in range(0, 4) for v in (D - 2, D - 1)])
    # boards and slabs stacked on the right wall
    for v in range(D - 4, D):
        b.p(W - 1, 1, v, "planks")
        b.slab(W - 1, 2, v)
        b.pakai([(W - 1, v)])
    # saw bench + crafting table
    sv = D - 4
    b.p(2, 1, sv, "minecraft:stonecutter", {"facing": "east"})
    b.p(3, 1, sv, "minecraft:crafting_table")
    b.pakai([(1, sv), (2, sv), (3, sv), (2, sv + 1)])
    b.titik("kerja:gergaji", 2, sv + 1, "north")
    # half-made furniture by the door
    if b.bebas(1, 2) and b.bebas(2, 2):
        b.tangga(1, 1, 2, "west")
        b.p(2, 1, 2, "fence")
        b.pakai([(1, 2), (2, 2)])
    if b.bebas(W - 2, 2):
        b.meja_papan(W - 2, 2, 1)
        b.pajangan("papan", W - 2, 2, 2)
        b.pakai([(W - 2, 2)])
    for v in range(2, D - 4, 2):
        b.rak(0, 2, v, "east")
    b.lentera(b.pu, D // 2)
    _selesai(b)
    return b


def interior_penjual_ikan(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Fish stall: water tubs (cauldrons) in a row, a wet stone slab table with ice, fish hooks on the walls, barrels of
    salt, wet floor (mud bricks); points lapak (the seller behind the table), pajangan:ikan."""
    b = _baru("penjual_ikan", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (6, 6, 3))
    W, D = b.W, b.D
    b.lantai(0, 0, W - 1, D - 1, "minecraft:mud_bricks", "minecraft:packed_mud", 3)
    tv = D - 3
    for u in range(0, W - 1):
        b.p(u, 1, tv, "minecraft:polished_andesite" if u % 3 else "minecraft:packed_ice")
    b.pakai([(u, tv) for u in range(W)])
    for u in range(0, W - 1, 2):
        b.p(u, 1, D - 1, "minecraft:water_cauldron", {"level": str(2 + u % 2)})
        b.tong(u + 1, 1, D - 1, "up")
    b.pakai([(u, v) for u in range(W) for v in (D - 2, D - 1)])
    b.titik("lapak", 2, D - 2, "north")
    b.pajangan("ikan", 1, 2, tv)
    b.pajangan("ikan", 3, 2, tv)
    for v in range(1, D - 3, 2):
        b.p(0, 2, v, "minecraft:tripwire_hook", {"facing": "east", "attached": "false", "powered": "false"})
        b.p(W - 1, 2, v, "minecraft:tripwire_hook", {"facing": "west", "attached": "false", "powered": "false"})
    b.lentera(b.pu, tv - 1)
    _selesai(b)
    return b


def interior_toko_kue(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Bun and cake shop: stacked bamboo steamers (composter + scaffolding towers) by a brick oven, a flour barrel and
    kneading board, a display counter with trays; points kerja:kukus (steam), pajangan:bakpao. Bilik.dapur = oven."""
    b = _baru("toko_kue", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (6, 6, 3))
    W, D = b.W, b.D
    b.p(0, 1, D - 1, "minecraft:bricks")
    b.p(1, 1, D - 1, "minecraft:furnace", {"facing": "north", "lit": "false"})
    b.p(0, 2, D - 1, "minecraft:bricks")
    b.p(1, 2, D - 1, "minecraft:bricks")
    b.dapur = b.xz(1, D - 1)
    for u in (2, 3):
        # a bamboo steamer stack: the bamboo frame (scaffolding, standing on the floor) under woven baskets
        b.p(u, 1, D - 1, "minecraft:scaffolding", {"bottom": "false", "distance": "0", "waterlogged": "false"})
        b.p(u, 2, D - 1, "minecraft:composter", {"level": "0"})
        if h >= 4:
            b.p(u, 3, D - 1, "minecraft:composter", {"level": "0"})
    b.tong(W - 1, 1, D - 1, "up")
    b.meja_papan(W - 2, D - 1, 1)
    b.pakai([(u, v) for u in range(W) for v in (D - 2, D - 1)])
    b.titik("kerja:kukus", 2, D - 2, "south")
    b.konter(0, W - 2, D - 3, "north", "planks")
    b.pakai([(u, D - 3) for u in range(W)])
    b.pajangan("bakpao", 1, 3, D - 3)
    b.pajangan("bakpao", 3, 3, D - 3)
    b.lentera(b.pu, D - 4)
    _sudut_bunga(b)
    if papan:
        b.papan("bing")
    _selesai(b)
    return b


def interior_toko_lentera(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Lantern and paper shop: lanterns and paper lanterns (red/white wool boxes) hanging densely from the ceiling on
    chains, a painting table with paper (white carpet) and a brush pot, shelves of lanterns on both side walls;
    points kerja:kuas, pajangan:lentera_kertas."""
    b = _baru("toko_lentera", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (6, 6, 3))
    W, D = b.W, b.D
    tv = D - 2
    for u in range(1, W - 1):
        b.p(u, 1, tv, "fence")
        b.meja_papan(u, tv, 2)
    b.p(2, 3, tv, "minecraft:white_carpet")
    b.pot(W - 2, 3, tv, "minecraft:flower_pot")
    b.pakai([(u, v) for u in range(W) for v in (tv, D - 1)])
    b.titik("kerja:kuas", 2, D - 1, "north")
    for v in range(1, D - 2):
        b.rak(0, 2, v, "east")
        b.rak(W - 1, 2, v, "west")
        b.pakai([(0, v), (W - 1, v)])
    b.pajangan("lentera_kertas", 0, 3, 2)
    b.pajangan("lentera_kertas", W - 1, 3, 2)
    b.pajangan("lentera_kertas", 1, 3, tv)
    for u in range(1, W - 1, 2):
        for v in range(1, D - 2, 2):
            if h >= 4 and _plafon_padat(b.get(u, h + 1, v)):
                b.p(u, h, v, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
                b.p(u, h - 1, v, "minecraft:red_wool" if (u + v) % 4 else "minecraft:white_wool")
            else:
                b.lentera(u, v)
    _selesai(b)
    return b


def interior_toko_buku(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Calligraphy and book shop: bookshelves and chiseled bookshelves along both side walls, scroll shelves on the
    back wall, a lectern, a writing desk with ink candle and brush pot, reading stools; points kerja:kuas, kursi,
    pajangan:gulungan."""
    b = _baru("toko_buku", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (6, 6, 3))
    W, D = b.W, b.D
    import kota_besar_kit as kbk
    top = min(h, 3)
    for u, f in ((0, "east"), (W - 1, "west")):
        x_a, z_a = b.xz(u, 2)
        x_b, z_b = b.xz(u, D - 2)
        kbk.bookshelf_wall(k, x_a, z_a, x_b, z_b, b.y0 + 1, b.y0 + top, 3, b.arah(f))
        b.pakai([(u, v) for v in range(2, D - 1)])
    for u in range(1, W - 1):
        b.rak(u, 2, D - 1, "north")
        b.tong(u, 1, D - 1, "north")
    b.pakai([(u, D - 1) for u in range(W)])
    dv = D - 3
    b.meja_papan(2, dv, 1)
    b.meja_papan(3, dv, 1)
    b.lilin(3, 2, dv, 1, "black")
    b.pot(2, 2, dv, "minecraft:flower_pot")
    b.kursi(2, dv + 1, "north")
    b.pakai([(1, dv), (2, dv), (3, dv), (2, dv + 1), (3, dv + 1)])
    b.titik("kerja:kuas", 2, dv + 1, "north")
    b.pajangan("gulungan", 2, 3, D - 1)
    if b.bebas(W - 2, 2):
        b.p(W - 2, 1, 2, "minecraft:lectern", {"facing": "west", "has_book": "true", "powered": "false"})
        b.pakai([(W - 2, 2)])
    if b.bebas(1, 2):
        b.bangku(1, 2)
        b.titik("kursi", 1, 2, "east")
        b.pakai([(1, 2)])
    b.lentera(b.pu, D // 2)
    if papan:
        b.papan("shu")
    _selesai(b)
    return b


def interior_toko_jimat(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Talisman shop: a small altar with candles of many colours and an incense pot on the back wall, yellow paper
    (yellow wool / banners) on the walls, a writing table with yellow cloth; points kerja:jimat, dupa, pajangan:jimat."""
    b = _baru("toko_jimat", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (6, 6, 3))
    W, D = b.W, b.D
    c = W // 2
    for u in range(c - 1, c + 2):
        b.tangga(u, 1, D - 1, "south", "top")
    b.lilin(c - 1, 2, D - 1, 3, "red")
    b.pot(c, 2, D - 1, "minecraft:flower_pot")
    b.lilin(c + 1, 2, D - 1, 2, "yellow")
    for u in (0, W - 1):
        b.p(u, 1, D - 1, "minecraft:yellow_wool")
        b.lilin(u, 2, D - 1, 1, "white")
    if h >= 4:
        b.p(c, 3, D - 1, "minecraft:yellow_wall_banner", {"facing": "north"})
    b.pakai([(u, v) for u in range(W) for v in (D - 2, D - 1)])
    b.titik("dupa", c, D - 2, "south")
    tv = D - 4
    b.meja_papan(1, tv, 1)
    b.meja_papan(2, tv, 1)
    b.p(1, 2, tv, "minecraft:yellow_carpet")
    b.kursi(1, tv + 1, "north")
    b.pakai([(0, tv), (1, tv), (2, tv), (1, tv + 1)])
    b.titik("kerja:jimat", 1, tv + 1, "north")
    b.pajangan("jimat", 2, 2, tv)
    for v in range(1, D - 2, 2):
        b.p(0, 2, v, "minecraft:yellow_wall_banner", {"facing": "east"})
        b.p(W - 1, 2, v, "minecraft:yellow_wall_banner", {"facing": "west"})
    b.lentera(c, tv)
    if papan:
        b.papan("fu_jimat")
    _selesai(b)
    return b


def interior_rumah_gadai(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=16, papan=True, api=False):
    """Pawnshop: a tall counter barred with iron bars and one open window, locked chests and barrels behind it, a
    customer bench; point kerja:konter (behind the window)."""
    b = _baru("rumah_gadai", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    cv = D - 3
    win = W // 2
    for u in range(W):
        b.p(u, 1, cv, "planks")
        b.meja_papan(u, cv, 2)
        if u != win:
            b.p(u, 3, cv, "minecraft:iron_bars")
    b.p(W - 1, 2, cv, "gate", {"facing": "east", "in_wall": "false", "open": "false", "powered": "false"})
    b.p(W - 1, 1, cv, "minecraft:air")
    for u in range(W - 1):
        if u % 2 == 0:
            b.peti(u, 1, D - 1, "north")
        else:
            b.tong(u, 1, D - 1, "north")
            if u % 3 == 0:
                b.tong(u, 2, D - 1, "up")
    b.pakai([(u, v) for u in range(W) for v in range(cv, D)])
    b.titik("kerja:konter", win, cv + 1, "north")
    for u in (0, 1, 2):
        if b.bebas(u, 2):
            b.kursi(u, 2, "east" if u == 0 else "south")
            b.pakai([(u, 2)])
    if b.get(1, 1, 2):
        b.titik("kursi", 1, 2, "south")
    b.lentera(win, cv - 1)
    if papan:
        b.papan("dang")
    _selesai(b)
    return b


def interior_rumah_judi(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=20, papan=True, api=False):
    """Gambling house: up to four dice-table spots (the meja_dadu entity) with stools, green wool card tables, a
    cashier's counter, folding screens at the back, red lanterns and runner; points meja_dadu x4, kursi,
    kerja:konter."""
    b = _baru("rumah_judi", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (9, 9, 3))
    W, D = b.W, b.D
    b.konter(W - 4, W - 1, D - 2, "north", "planks")
    b.pakai([(u, v) for u in range(W - 4, W) for v in (D - 2, D - 1)])
    b.titik("kerja:konter", W - 2, D - 1, "north")
    if h >= 4:
        b.layar(0, W - 5, D - 1, "red")
    else:
        for u in range(0, W - 5):
            b.p(u, 1, D - 1, "minecraft:red_wool")
    b.pakai([(u, D - 1) for u in range(W)])
    b.lorong(3, 0, D - 3)
    n = 0
    for v in range(2, D - 3, 4):
        for u in range(1, W - 1, 4):
            if n >= 4:
                break
            cells = [(u + du, v + dv) for du in range(3) for dv in range(3)]
            if not b.semua_bebas(cells):
                continue
            b.titik("meja_dadu", u + 1, v + 1, "south")
            b.bangku(u + 1, v)
            b.bangku(u + 1, v + 2)
            b.titik("kursi", u + 1, v + 2, "north")
            b.lentera(u + 1, v + 1)
            b.pakai(cells)
            n += 1
    for v in range(2, D - 3, 3):
        for u in range(1, W - 2, 4):
            cells = [(u, v), (u + 1, v), (u, v + 1), (u + 1, v + 1), (u - 1, v), (u + 2, v + 1)]
            if not b.semua_bebas(cells):
                continue
            b.p(u, 1, v, "minecraft:green_wool")
            b.p(u + 1, 1, v, "minecraft:green_wool")
            b.p(u, 2, v, "minecraft:white_carpet")
            b.bangku(u - 1, v)
            b.bangku(u + 2, v + 1)
            b.titik("kursi", u - 1, v, "east")
            b.pakai(cells)
    b.karpet(b.pu, 1, b.pu, D - 4, "red")
    _hias_dinding(b, "red", 3)
    if papan:
        b.papan("jiu", "panji")
    _selesai(b)
    return b


def interior_penginapan(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=24, papan=True, api=False):
    """Inn: a reception counter by the door (kerja:konter) and guest rooms on both sides of a 3-wide corridor; each
    room has plank walls, a door to the corridor, a bed (kasur point), a side table with a candle and a chest."""
    b = _baru("penginapan", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (11, 10, 3))
    W, D = b.W, b.D
    pu = b.pu
    # reception: counter on the left of the entrance hall
    b.konter(0, pu - 2, 3, "north", "planks", celah=0)
    b.tong(0, 1, 4, "up")
    b.pakai([(u, v) for u in range(0, pu - 1) for v in range(2, 5)])
    b.titik("kerja:konter", 1, 4, "north")
    b.lentera(pu, 2)
    # rooms from v = 5 to the back, 4 deep each, on both sides of the corridor
    left = (0, pu - 3)
    right = (pu + 3, W - 1)
    for (ua, ub), wall_u, door_f in ((left, pu - 2, "east"), (right, pu + 2, "west")):
        if ub - ua < 2:
            continue
        for dy in range(1, h + 1):
            for v in range(5, D):
                b.p(wall_u, dy, v, "planks")
        v0 = 5
        while v0 + 3 <= D:
            # partition at the back of the room (except after the last room)
            if v0 + 4 < D:
                for dy in range(1, h + 1):
                    for u in range(ua, ub + 1):
                        b.p(u, dy, v0 + 3, "planks")
            dv = v0 + 1
            x, z = b.xz(wall_u, dv)
            k.put(x, b.y0 + 1, z, b.m["door"], {"facing": b.arah(OPP[door_f]), "half": "lower", "hinge": "left",
                                                 "open": "false", "powered": "false"})
            k.put(x, b.y0 + 2, z, b.m["door"], {"facing": b.arah(OPP[door_f]), "half": "upper", "hinge": "left",
                                                 "open": "false", "powered": "false"})
            far = ua if door_f == "east" else ub
            near = ub if door_f == "east" else ua
            b.kasur(far, v0 + 1, "west" if door_f == "east" else "east", ("red", "white", "brown", "light_gray")[v0 % 4])
            b.titik("kasur", far, v0 + 1, "west" if door_f == "east" else "east")
            b.meja_papan(far, v0, 1)
            b.lilin(far, 2, v0, 1, "white")
            b.peti(near, 1, v0 + 2, "north")
            b.pakai([(u, v) for u in range(ua, ub + 1) for v in range(v0, v0 + 4)])
            v0 += 4
    b.lorong(3, 5, D - 1)
    b.karpet(pu, 5, pu, D - 1, "red")
    if papan:
        b.papan("ke,zhan")
    _selesai(b)
    return b


def interior_sekolah(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=18, papan=True, api=False):
    """Village / town school: a blackboard (blackstone) on the back wall, the teacher's lectern and the mengajar point
    behind it, rows of low desks (top trapdoors) with floor cushions for 8-12 children (kursi points), a shelf of
    scrolls, the sekolah point in the middle of the class."""
    b = _baru("sekolah", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (9, 8, 3))
    W, D = b.W, b.D
    c = W // 2
    for u in range(max(0, c - 3), min(W, c + 4)):
        b.p(u, 2, D - 1, "minecraft:polished_blackstone")
        if h >= 4:
            b.p(u, 3, D - 1, "minecraft:polished_blackstone")
    b.p(c, 1, D - 3, "minecraft:lectern", {"facing": "north", "has_book": "true", "powered": "false"})
    b.pakai([(u, v) for u in range(W) for v in (D - 3, D - 2, D - 1)])
    b.titik("mengajar", c, D - 2, "north")
    b.titik("sekolah", c, D // 2, "south")
    rows = range(2, D - 4, 2)
    for v in rows:
        for u in range(1, W - 1, 2):
            if u == c or not b.bebas(u, v) or not b.bebas(u, v + 1):
                continue
            b.meja_papan(u, v + 1, 1)
            b.p(u, 1, v, "minecraft:%s_carpet" % ("red" if (u + v) % 4 else "yellow"))
            b.titik("kursi", u, v, "south", {"anak": 1})
            b.pakai([(u, v), (u, v + 1)])
    for v in range(1, D - 3):
        if v % 2:
            b.rak(0, 2, v, "east")
    b.pajangan("gulungan", 0, 3, 1)
    b.lentera(c, D // 2 - 1)
    _selesai(b)
    return b


def interior_altar_leluhur(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=8, papan=False, api=False):
    """Ancestral altar room of a house: an altar of upside-down stairs on the back wall with red candles, an incense
    pot, offering pot and the ancestor portrait (pajangan:potret_almarhum), spirit tablets (trapdoor plaques) above,
    a red kneeling rug; point dupa."""
    b = _baru("altar_leluhur", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (5, 4, 3))
    W, D = b.W, b.D
    c = W // 2
    for u in range(max(0, c - 2), min(W, c + 3)):
        b.tangga(u, 1, D - 1, "south", "top")
    b.lilin(max(0, c - 2), 2, D - 1, 2, "red")
    b.lilin(min(W - 1, c + 2), 2, D - 1, 2, "red")
    b.pot(c, 2, D - 1, "minecraft:flower_pot")
    b.pot(c + 1, 2, D - 1, "minecraft:potted_white_tulip")
    b.pajangan("potret_almarhum", c - 1, 2, D - 1)
    if h >= 4:
        for u in range(c - 1, c + 2):
            b.p(u, 3, D - 1, "trapdoor", {"facing": "north", "half": "bottom", "open": "true", "powered": "false",
                                          "waterlogged": "false"})
    b.pakai([(u, D - 1) for u in range(W)])
    b.karpet(c - 1, D - 2, c + 1, D - 2, "red")
    b.titik("dupa", c, D - 2, "south")
    _selesai(b)
    return b


def interior_rumah_petani(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=8, papan=False, api=False):
    """Farmer's home: dried corn and hay hung on the walls (hay blocks high up), baskets (barrels) and sacks, a hoe
    rack, a straw bed, a stove with a cauldron pot, a table with stools; points kasur, meja_makan, pajangan:keranjang.
    Bilik.dapur = the stove."""
    b = _baru("rumah_petani", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (6, 6, 3))
    W, D = b.W, b.D
    b.kasur(W - 1, D - 1, "south", "brown")
    b.pakai([(W - 1, D - 1), (W - 1, D - 2), (W - 2, D - 1)])
    b.titik("kasur", W - 1, D - 1, "north")
    b.p(0, 1, D - 1, "minecraft:smoker", {"facing": "north", "lit": "false"})
    b.p(1, 1, D - 1, "minecraft:cauldron")
    b.dapur = b.xz(0, D - 1)
    b.tong(2, 1, D - 1, "up")
    b.pakai([(0, D - 1), (1, D - 1), (2, D - 1), (1, D - 2)])
    _meja_kursi(b, W // 2, D // 2, "meja_makan", ("w", "e"))
    for v in range(1, D - 1, 2):
        if h >= 3:
            b.p(0, h, v, "minecraft:hay_block", {"axis": "y"})
            if v < D - 2:
                b.p(W - 1, h, v, "minecraft:hay_block", {"axis": "y"})
    if b.bebas(0, 2):
        b.tong(0, 1, 2, "up")
        b.pajangan("keranjang", 0, 2, 2)
        b.pakai([(0, 2)])
    if b.bebas(W - 1, 2):
        b.p(W - 1, 1, 2, "minecraft:hay_block", {"axis": "x"})
        b.p(W - 1, 2, 2, "fence")
        b.pakai([(W - 1, 2)])
    b.lentera(W // 2, D // 2 - 1)
    _selesai(b)
    return b


def interior_rumah_nelayan(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=8, papan=False, api=False):
    """Fisherman's home: drying nets (string lines and hanging chains) on a wall, oars leaning (fence posts), salted
    fish jars (decorated pots, barrels), a bed, a stove, a table with stools; points kasur, meja_makan, pajangan:jala,
    pajangan:ikan. Bilik.dapur = the stove."""
    b = _baru("rumah_nelayan", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (6, 6, 3))
    W, D = b.W, b.D
    b.kasur(0, D - 1, "south", "light_blue")
    b.pakai([(0, D - 1), (0, D - 2), (1, D - 1)])
    b.titik("kasur", 0, D - 1, "north")
    b.p(W - 1, 1, D - 1, "minecraft:furnace", {"facing": "north", "lit": "false"})
    b.dapur = b.xz(W - 1, D - 1)
    b.guci(W - 2, 1, D - 1)
    b.guci(W - 3, 1, D - 1)
    b.tong(W - 1, 1, D - 2, "up")
    b.pakai([(u, v) for u in range(W - 3, W) for v in (D - 2, D - 1)])
    _meja_kursi(b, W // 2, D // 2, "meja_makan", ("w", "e"))
    # nets along the right wall: string lines on the floor and chains hanging
    for v in range(1, D - 2):
        b.p(W - 1, 1, v, "minecraft:tripwire", {"attached": "false", "disarmed": "false", "powered": "false",
                                                "north": "true", "south": "true", "east": "false", "west": "false"})
        _gantung(b, W - 1, v, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        b.pakai([(W - 1, v)])
    b.pajangan("jala", W - 1, 1, 2)
    for v in (1, 2):
        if b.bebas(0, v):
            b.p(0, 1, v, "fence")
            b.p(0, 2, v, "fence")
            b.pakai([(0, v)])
    b.pajangan("ikan", W // 2, 2, D // 2)
    b.lentera(W // 2, D // 2 - 1)
    _selesai(b)
    return b


def interior_rumah_sarjana(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=8, papan=False, api=False):
    """Scholar's home: a reading corner (bookshelves, a writing desk with candle and brush pot), a folding screen, bonsai
    on stands, a stone basin with water and lily pads when deep enough, a low tea table with cushions; points
    kerja:kuas, kursi."""
    b = _baru("rumah_sarjana", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    import kota_besar_kit as kbk
    xa, za = b.xz(0, D - 1)
    xb, zb = b.xz(2, D - 1)
    kbk.bookshelf_wall(k, xa, za, xb, zb, b.y0 + 1, b.y0 + min(h, 3), 3, b.arah("north"))
    b.meja_papan(1, D - 3, 1)
    b.meja_papan(2, D - 3, 1)
    b.lilin(2, 2, D - 3, 1, "white")
    b.pot(1, 2, D - 3, "minecraft:flower_pot")
    b.kursi(1, D - 2, "north")
    b.pakai([(u, v) for u in range(0, 4) for v in range(D - 3, D)])
    b.titik("kerja:kuas", 1, D - 2, "north")
    if h >= 4:
        b.layar(W - 4, W - 1, D - 1, "white")
    for u in (W - 1,):
        for v in range(2, D - 2, 2):
            b.p(u, 1, v, "fence")
            b.pot(u, 2, v, ("minecraft:potted_azalea_bush", "minecraft:potted_spruce_sapling")[v % 2])
            b.pakai([(u, v)])
    if D >= 9 and W >= 8:
        bu, bv = 1, 2
        for du in range(3):
            for dv in range(3):
                edge = du in (0, 2) or dv in (0, 2)
                b.p(bu + du, 1, bv + dv, "minecraft:polished_andesite_slab" if edge else "minecraft:water",
                    {"type": "bottom", "waterlogged": "false"} if edge else {"level": "0"})
        b.p(bu + 1, 0, bv + 1, "minecraft:water", {"level": "0"})
        b.pakai([(bu + du, bv + dv) for du in range(3) for dv in range(3)], 0)
    cu, cv = W // 2 + 1, D // 2
    if b.semua_bebas([(cu, cv), (cu - 1, cv), (cu + 1, cv)]):
        b.p(cu, 1, cv, "trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                    "waterlogged": "false"})
        for du, f in ((-1, "east"), (1, "west")):
            b.p(cu + du, 1, cv, "minecraft:white_carpet")
            b.titik("kursi", cu + du, cv, f)
        b.pakai([(cu - 1, cv), (cu, cv), (cu + 1, cv)])
    b.lentera(W // 2, 2)
    _selesai(b)
    return b


def interior_rumah_kaya(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=12, papan=False, api=False):
    """Merchant's hall: a spirit wall (yingbi) just inside the door so callers walk around it, a carp basin with lily pads,
    a xiangqi table (catur points), armchairs around a tea table (kursi), vases and banners, a rug; points kursi,
    catur."""
    b = _baru("rumah_kaya", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (11, 11, 4))
    W, D = b.W, b.D
    pu = b.pu
    # spirit wall: stone base, plaster body, tile cap, two cells behind the door
    for u in range(pu - 2, pu + 3):
        b.p(u, 1, 2, "minecraft:stone_bricks")
        b.p(u, 2, 2, "minecraft:white_terracotta")
        b.p(u, 3, 2, "minecraft:white_terracotta")
        if h >= 5:
            b.slab(u, 4, 2, "bottom", "minecraft:deepslate_tile_slab")
    b.p(pu, 2, 2, "minecraft:red_terracotta")
    b.pakai([(u, v) for u in range(pu - 3, pu + 4) for v in range(0, 4)])
    # basin in the middle
    cv = D // 2
    for du in range(-2, 3):
        for dv in range(-1, 2):
            edge = abs(du) == 2 or abs(dv) == 1
            b.p(pu + du, 1, cv + dv, "minecraft:polished_andesite_slab" if edge else "minecraft:water",
                {"type": "bottom", "waterlogged": "false"} if edge else {"level": "0"})
            if not edge:
                b.p(pu + du, 0, cv + dv, "minecraft:water", {"level": "0"})
    b.p(pu, 2, cv, "minecraft:lily_pad")
    b.pakai([(pu + du, cv + dv) for du in range(-2, 3) for dv in range(-1, 2)], 1)
    # tea set on the back wall: table, two armchairs
    tv = D - 2
    if b.semua_bebas([(pu - 1, tv), (pu, tv), (pu + 1, tv)]):
        b.meja(pu, tv, "plate")
        b.kursi(pu - 1, tv, "east")
        b.kursi(pu + 1, tv, "west")
        b.titik("kursi", pu - 1, tv, "east")
        b.titik("kursi", pu + 1, tv, "west")
        b.pakai([(pu - 1, tv), (pu, tv), (pu + 1, tv)])
    b.p(pu, 2 if h < 4 else 3, D - 1, "minecraft:red_wall_banner", {"facing": "north"})
    # chess table on the side
    cu = 2
    if b.semua_bebas([(cu - 1, cv), (cu, cv), (cu + 1, cv)]):
        b.p(cu, 1, cv, "minecraft:polished_andesite")
        for du, f, kursi in ((-1, "east", "a"), (1, "west", "b")):
            b.bangku(cu + du, cv)
            b.titik("catur", cu + du, cv, f, {"kursi": kursi})
        b.pakai([(cu - 1, cv), (cu, cv), (cu + 1, cv)])
    for u, v in ((0, D - 1), (W - 1, D - 1), (0, 4), (W - 1, 4)):
        if b.bebas(u, v):
            b.guci(u, 1, v)
            b.pakai([(u, v)])
    _hias_dinding(b, "red", 4, 2)
    for v in (cv - 2, cv + 2):
        b.lentera(pu, v)
    _selesai(b)
    return b


def interior_gudang(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=8, papan=False, api=False):
    """Warehouse: rows of barrels and crates stacked two or three high, chests, grain sacks (hay / white wool), a big
    balance on a log frame by the door; points gudang (the keeper) and lumbung (the queue head)."""
    b = _baru("gudang", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    b.lorong(3, 0, D - 1)
    top = min(h - 1, 3)
    for u in list(range(0, b.pu - 1)) + list(range(b.pu + 2, W)):
        for v in range(2, D):
            if (v - 2) % 4 == 3:
                continue
            hgt = 1 + (u * 3 + v * 5) % top
            for dy in range(1, hgt + 1):
                r = (u * 7 + v * 3 + dy) % 6
                if r < 2:
                    b.tong(u, dy, v, ("up", "north", "east")[r])
                elif r < 4:
                    b.p(u, dy, v, "minecraft:hay_block", {"axis": "y"})
                elif r == 4:
                    b.p(u, dy, v, "minecraft:white_wool")
                else:
                    b.p(u, dy, v, "wood", {"axis": "y"})
            b.pakai([(u, v)])
    for u in (0, W - 1):
        if b.bebas(u, 1):
            b.peti(u, 1, 1, "east" if u == 0 else "west")
            b.pakai([(u, 1)])
    # balance at the door: log posts, chain, heavy plate pan
    bu = b.pu - 2 if b.pu - 2 >= 0 else b.pu + 2
    b.p(bu, 1, 1, "log", {"axis": "y"})
    b.p(bu, 2, 1, "minecraft:heavy_weighted_pressure_plate", {"power": "0"})
    b.pajangan("dacing", bu, 3, 1)
    b.titik("lumbung", b.pu, 1, "north", wajib=True)
    b.titik("gudang", b.pu, D - 2, "south")
    b.lentera(b.pu, D // 2)
    _selesai(b)
    return b


def interior_balai_kota(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=12, papan=False, api=False):
    """Village / town hall: the official's chair on a dais behind a long table at the back (sidang), a clerk's desk
    (kerja:konter), a recruiting table (rekrut), benches for callers, a notice board of pinned paper (white carpet on
    a trapdoor board) and red banners."""
    b = _baru("balai_kota", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (9, 8, 3))
    W, D = b.W, b.D
    c = W // 2
    for u in range(c - 2, c + 3):
        b.slab(u, 1, D - 1, "top")
    b.tangga(c, 2, D - 1, "south")
    for u in range(c - 2, c + 3):
        b.p(u, 1, D - 3, "fence")
        b.meja_papan(u, D - 3, 2)
    b.p(c - 1, 3, D - 3, "minecraft:red_carpet")
    b.pakai([(u, v) for u in range(c - 3, c + 4) for v in range(D - 3, D)])
    b.titik("sidang", c, D - 1, "north", None, dy=2)
    for u in (c - 2, c + 2):
        b.p(u, 2, D - 1, "minecraft:red_wall_banner", {"facing": "north"})
    # clerk desk (left) and recruiting table (right)
    for u, jenis in ((1, "kerja:konter"), (W - 2, "rekrut")):
        v = D // 2
        if b.semua_bebas([(u, v), (u, v + 1)]):
            b.meja_papan(u, v, 1)
            if b.bebas(u, v - 1):
                b.tong(u, 1, v - 1, "up")
            b.kursi(u, v + 1, "north")
            b.titik(jenis, u, v + 1, "north")
            b.pakai([(u, v), (u, v + 1), (u, v - 1)])
    # callers' benches
    for v in range(2, D // 2 - 1, 2):
        for u in range(c - 3, c + 4):
            if u != c and b.bebas(u, v):
                b.kursi(u, v, "south")
                b.pakai([(u, v)])
    if b.get(c - 1, 1, 2):
        b.titik("kursi", c - 1, 2, "south")
    # notice board on the right wall by the door
    for v in (1, 2):
        b.p(W - 1, 2, v, "trapdoor", {"facing": "west", "half": "bottom", "open": "true", "powered": "false",
                                      "waterlogged": "false"})
    _hias_dinding(b, "red", 4)
    b.lentera(c, D // 2)
    _selesai(b)
    return b


def interior_kuil_kecil(k, x0, y0, z0, w, d, h, hadap, pintu=None, titik_maks=8, papan=False, api=False):
    """Small town temple (Earth God shrine): the god's figure on a raised altar at the back (andesite body, stair arms,
    gold crown), candles and offering pots, a big incense burner (cauldron + soul lantern) in front, a standing bell,
    a donation chest, kneeling cushions; points dupa, kerja:sapu."""
    b = _baru("kuil_kecil", k, x0, y0, z0, w, d, h, hadap, pintu, titik_maks, api, (7, 7, 3))
    W, D = b.W, b.D
    c = W // 2
    for u in range(c - 2, c + 3):
        b.tangga(u, 1, D - 2, "south", "top", "minecraft:stone_brick_stairs")
        b.p(u, 1, D - 1, "minecraft:chiseled_stone_bricks")
    b.p(c, 2, D - 1, "minecraft:polished_andesite")
    if h >= 4:
        b.p(c, 3, D - 1, "minecraft:chiseled_stone_bricks")
        if h >= 5:
            b.p(c, 4, D - 1, "minecraft:gold_block")
    b.tangga(c - 1, 2, D - 1, "east", "bottom", "minecraft:polished_andesite_stairs")
    b.tangga(c + 1, 2, D - 1, "west", "bottom", "minecraft:polished_andesite_stairs")
    b.lilin(c - 2, 2, D - 2, 3, "red")
    b.lilin(c + 2, 2, D - 2, 3, "red")
    b.pot(c - 1, 2, D - 2, "minecraft:potted_red_tulip")
    b.pot(c + 1, 2, D - 2, "minecraft:potted_red_tulip")
    b.pakai([(u, v) for u in range(W) for v in (D - 2, D - 1)])
    bv = D - 4
    b.p(c, 1, bv, "minecraft:cauldron")
    b.p(c, 2, bv, "minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"})
    b.pakai([(c, bv)], 0)
    b.titik("dupa", c, bv - 1, "south")
    if bv + 1 < D - 2:
        b.karpet(c - 1, bv + 1, c + 1, bv + 1, "red")
    if b.bebas(0, D - 3):
        b.p(0, 1, D - 3, "minecraft:bell", {"attachment": "floor", "facing": "east", "powered": "false"})
        b.pakai([(0, D - 3)])
    if b.bebas(W - 1, 2):
        b.peti(W - 1, 1, 2, "west")
        b.pakai([(W - 1, 2)])
    b.titik("kerja:sapu", 1, 2, "east")
    b.lentera(c, bv - 1)
    _selesai(b)
    return b


INTERIOR = {
    "rumah_makan": interior_rumah_makan, "kedai_teh": interior_kedai_teh, "kedai_arak": interior_kedai_arak,
    "apotek": interior_apotek, "penjahit": interior_penjahit, "pandai": interior_pandai,
    "tukang_kayu": interior_tukang_kayu, "penjual_ikan": interior_penjual_ikan, "toko_kue": interior_toko_kue,
    "toko_lentera": interior_toko_lentera, "toko_buku": interior_toko_buku, "toko_jimat": interior_toko_jimat,
    "rumah_gadai": interior_rumah_gadai, "rumah_judi": interior_rumah_judi, "penginapan": interior_penginapan,
    "sekolah": interior_sekolah, "altar_leluhur": interior_altar_leluhur, "rumah_petani": interior_rumah_petani,
    "rumah_nelayan": interior_rumah_nelayan, "rumah_sarjana": interior_rumah_sarjana, "rumah_kaya": interior_rumah_kaya,
    "gudang": interior_gudang, "balai_kota": interior_balai_kota, "kuil_kecil": interior_kuil_kecil,
}
# minimum (across, depth, height) per kit, from each kit's Bilik
UKURAN_MIN = {"rumah_makan": (7, 8, 3), "kedai_teh": (8, 8, 3), "kedai_arak": (8, 9, 3), "apotek": (7, 7, 3),
              "penjahit": (7, 7, 3), "pandai": (7, 7, 3), "tukang_kayu": (7, 7, 3), "penjual_ikan": (6, 6, 3),
              "toko_kue": (6, 6, 3), "toko_lentera": (6, 6, 3), "toko_buku": (6, 6, 3), "toko_jimat": (6, 6, 3),
              "rumah_gadai": (7, 7, 3), "rumah_judi": (9, 9, 3), "penginapan": (11, 10, 3), "sekolah": (9, 8, 3),
              "altar_leluhur": (5, 4, 3), "rumah_petani": (6, 6, 3), "rumah_nelayan": (6, 6, 3),
              "rumah_sarjana": (7, 7, 3), "rumah_kaya": (11, 11, 4), "gudang": (7, 7, 3), "balai_kota": (9, 8, 3),
              "kuil_kecil": (7, 7, 3)}


def interior(kit, k, x0, y0, z0, w, d, h, hadap, **kw):
    """Dispatches to interior_<kit>(k, x0, y0, z0, w, d, h, hadap, **kw)."""
    if kit not in INTERIOR:
        raise ValueError("unknown interior kit %r (kota_hidup_kit.INTERIOR)" % (kit,))
    return INTERIOR[kit](k, x0, y0, z0, w, d, h, hadap, **kw)


# ================================================================================================ self test
_DILARANG = ("minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:tnt", "minecraft:spawner",
             "minecraft:lightning_rod")


def _ruang_uji(kit, hadap, extra=3):
    """A walled, roofed test room for one kit and facing; returns (piece, box) with the door in the hadap wall."""
    mw, md, mh = UKURAN_MIN[kit]
    across, depth, h = mw + extra, md + extra, mh + 1
    across += 1 - across % 2      # odd sizes: the world middle door is the local middle in every facing
    depth += 1 - depth % 2
    w, d = (across, depth) if hadap in ("north", "south") else (depth, across)
    x0, y0, z0 = 4, 1, 4
    p = structgen.Piece(w + 8, y0 + h + 4, d + 8, seed=_seed("uji", kit, hadap), palette=tl.PALET["kota_besar"])
    p.fill(0, 0, 0, w + 7, 0, d + 7, "found")
    p.fill(0, y0, 0, w + 7, y0, d + 7, "floor")
    p.walls(x0 - 1, y0 + 1, z0 - 1, x0 + w, y0 + h, z0 + d, role="plaster", pillar="pillar", windows=0)
    p.fill(x0 - 1, y0 + h + 1, z0 - 1, x0 + w, y0 + h + 1, z0 + d, "roof_full")
    cx, cz = x0 + w // 2, z0 + d // 2
    door = {"north": (cx, z0 - 1), "south": (cx, z0 + d), "west": (x0 - 1, cz), "east": (x0 + w, cz)}[hadap]
    for dy in (1, 2):
        p.put(door[0], y0 + dy, door[1], "minecraft:air")
    return p, (x0, y0, z0, w, d, h)


def _sel(p):
    return {pos: cell for pos, cell in p.cells.items()}


def uji(out_dir):
    """Builds every interior kit in all four facings, every point builder, kavling, cemeteries and a night street;
    validates ids (structgen block list), point types, the forbidden-block rule, the door aisle, writes outside the box
    and the rotation invariance (same blocks and points per facing); saves the NBTs into out_dir for previews."""
    blocks = structgen._load_block_list()
    errs, warns = [], []
    os.makedirs(os.path.join(out_dir, "interior"), exist_ok=True)

    def periksa(p, where, api=False):
        e, w_ = structgen.validate(p, where, blocks)
        errs.extend(e)
        warns.extend(w_)
        for (bid, props), _ in p.cells.values():
            if bid in _DILARANG:
                errs.append("%s: forbidden %s" % (where, bid))
            if bid == "minecraft:campfire" and dict(props).get("lit") == "true" and not api:
                errs.append("%s: lit campfire" % where)
        for t in p.titik_list:
            if not titik_sah(t["jenis"]):
                errs.append("%s: bad titik %s" % (where, t["jenis"]))
            if not p.inside(*t["pos"]):
                errs.append("%s: titik outside %s" % (where, t))
            if t["jenis"] in ("panggung", "kavling") and not all(kk in t.get("data", {}) for kk in ("dx", "dy", "dz")):
                errs.append("%s: box titik without size %s" % (where, t))
        if getattr(p, "clipped", 0):
            warns.append("%s: %d clipped" % (where, p.clipped))

    ringkas = []
    for kit in INTERIOR:
        sig = None
        for hadap in FACINGS:
            p, (x0, y0, z0, w, d, h) = _ruang_uji(kit, hadap)
            before = _sel(p)
            b = interior(kit, p, x0, y0, z0, w, d, h, hadap)
            where = "interior/%s_%s" % (kit, hadap)
            periksa(p, where)
            # writes outside the box: only the sign (front wall row and the cells in front of it)
            for pos, cell in p.cells.items():
                if before.get(pos) == cell:
                    continue
                x, y, z = pos
                if x0 <= x < x0 + w and z0 <= z < z0 + d and y0 <= y <= y0 + h:
                    continue
                front = {"north": z < z0, "south": z >= z0 + d, "west": x < x0, "east": x >= x0 + w}[hadap]
                if not front:
                    errs.append("%s: block written outside the box at %s" % (where, pos))
            # door aisle stays walkable
            for v in (0, 1):
                for dy in (1, 2):
                    bid = b.get(b.pu, dy, v)
                    if bid not in (None, "minecraft:air") and not bid.endswith("_carpet"):
                        errs.append("%s: door aisle blocked by %s at v=%d dy=%d" % (where, bid, v, dy))
            if b.titik_n == 0:
                errs.append("%s: no titik" % where)
            jenis = sorted(t["jenis"] for t in p.titik_list)
            hitung = {}
            for (x, y, z), ((bid, _props), _n) in p.cells.items():
                if x0 <= x < x0 + w and z0 <= z < z0 + d and y0 <= y <= y0 + h:
                    hitung[bid] = hitung.get(bid, 0) + 1
            s = (tuple(jenis), tuple(sorted(hitung.items())), len(p.entities))
            if sig is None:
                sig = s
            elif s != sig:
                errs.append("%s: differs from the north build (rotation)" % where)
            p.save(os.path.join(out_dir, "interior", "%s_%s.nbt" % (kit, hadap)))
        ringkas.append("%-14s titik %2d  %s" % (kit, len(sig[0]), ",".join(sorted(set(sig[0])))))

    # point builders on one yard
    p = structgen.Piece(48, 24, 48, seed=42, palette=tl.PALET["kota_besar"])
    p.fill(0, 0, 0, 47, 3, 47, "found")
    p.fill(0, 4, 0, 47, 4, 47, "floor")
    y = 4
    sumur(p, 5, y, 5, "south")
    catur(p, 14, y, 4, "south")
    gantung_sangkar(p, 22, y, 7, "north")
    pancing(p, 30, y, 8, "north", dermaga=True)
    jongkok(p, 38, y, 6, "south", n=4)
    kucing_tidur(p, 44, y + 3, 4, "east")
    tambat(p, 6, y, 14, "east")
    kandang(p, 12, y, 12, 19, 19, "ayam_kota", "south")
    kandang(p, 21, y, 12, 28, 19, "kuda_tambat", "west")
    cerobong(p, 32, y + 1, 14, 3)
    lentera_gantung(p, 34, y + 4, 14, "south", "merah")
    tiang_lentera(p, 36, y, 14, "west", 4, "hijau")
    panji(p, 40, y, 14, "south", "panji", "fu,chun")
    spanduk(p, 42, 12, 46, 12, y, 6, "a", "fu")
    nama_jalan(p, 44, y, 18, "south", "jalan_giok")
    ronda(p, [(4, y + 1, 22), (12, y + 1, 22), (12, y + 1, 26), (4, y + 1, 26)])
    pasar_pagi(p, 0, 16, y + 1, 22)
    lapak_sewa(p, 0, 20, y, 22, "north")
    lapak_sewa(p, 1, 24, y, 22, "north")
    pasar_hantu(p, 0, 28, y + 1, 22, "south", "gang_buntu")
    lumbung(p, 30, y + 1, 22)
    rekrut(p, 32, y + 1, 22, "south", 1)
    sidang(p, 34, y + 1, 22)
    meja_dadu(p, 36, y + 1, 22)
    panggung(p, 38, y + 1, 21, 42, 23, "north")
    dijual(p, 44, y + 1, 22, "south", "rumah_a", "sedang")
    kavling(p, 3, y, 31, "rumah_kecil", "kota_kultivator", "north")
    kavling(p, 14, y, 31, "rumah_toko", "ibukota_pasar", "east")
    kavling(p, 30, y, 31, "sekolah", "kampung", "south")
    try:
        kavling(p, 44, y, 44, "rumah_kecil", "kota_kultivator", "north")
        errs.append("kavling outside the piece was accepted")
    except ValueError:
        pass
    for bad in (lambda: titik42(p, "lentera", 1, 5, 1), lambda: bernomor(p, "nisan", "x", 1, 5, 1),
                lambda: kavling(p, 3, y, 3, "taman", "kota_kultivator", "north", kelas="besar"),
                lambda: panji(p, 1, y, 1, "south", "panji", "naga")):
        try:
            bad()
            errs.append("an illegal call was accepted")
        except ValueError:
            pass
    periksa(p, "titik/halaman")
    p.save(os.path.join(out_dir, "titik_halaman.nbt"))
    names = [t["jenis"] for t in p.titik_list]
    for need in TITIK_42:
        if need in ("sekolah", "mengajar", "gudang"):
            continue
        if need not in names:
            errs.append("titik/halaman: builder for %s missing" % need)

    # cemeteries and the night street
    for gaya, (w, d, n, ting) in (("bukit", (32, 31, 48, True)), ("desa", (12, 12, 4, False)),
                                  ("gurun", (23, 22, 16, False)), ("teras", (23, 22, 16, True)),
                                  ("laut", (16, 14, 6, False)), ("pulau", (16, 14, 6, False))):
        for hadap in ("south", "east"):
            ww, dd = (w, d) if hadap in ("north", "south") else (d, w)
            p = structgen.Piece(ww + 2, 24, dd + 2, seed=_seed(gaya), palette=tl.PALET["kota_besar"])
            p.fill(0, 0, 0, ww + 1, 4, dd + 1, "found")
            makam(p, 1, 4, 1, ww, dd, hadap, n, gaya=gaya, bertingkat=ting)
            nis = [t for t in p.titik_list if t["jenis"].startswith("nisan_")]
            if len(nis) != n:
                errs.append("makam %s: %d nisan, wanted %d" % (gaya, len(nis), n))
            for t in nis:
                x, yy, z = t["pos"]
                fx, fz = FACE[t["hadap"]]
                stone = p.get(x + fx, yy, z + fz)
                if stone in (None, "minecraft:air"):
                    errs.append("makam %s: no stele ahead of %s" % (gaya, t["jenis"]))
            periksa(p, "makam/%s_%s" % (gaya, hadap))
            p.save(os.path.join(out_dir, "makam_%s_%s.nbt" % (gaya, hadap)))
    p = structgen.Piece(48, 16, 13, seed=7, palette=tl.PALET["kota_besar"])
    p.fill(0, 0, 0, 47, 4, 12, "found")
    jm = jalan_malam(p, 0, 0, 47, 12, 4, "x")
    periksa(p, "jalan_malam")
    p.save(os.path.join(out_dir, "jalan_malam.nbt"))

    for w_ in sorted(set(warns)):
        print("warning:", w_)
    for r in ringkas:
        print(r)
    print("jalan_malam:", jm)
    for e in errs:
        print("ERROR:", e)
    print("kota_hidup_kit: %d interior kits x 4 facings, %d errors" % (len(INTERIOR), len(errs)))
    return not errs


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--uji":
        sys.exit(0 if uji(sys.argv[2]) else 1)
    print(__doc__)
