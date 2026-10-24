"""Kota Air world generation (DESIGN 38.T.5, module 38TK-A): the canal town "Kota Air Seribu Jembatan".

Writes every NBT lot of the theme (data/kultivator/structure/kota_tema/air/**), the template pools (base + the
`_sakura` style pool), the processor lists (kota_tema/air/lapuk, utuh, gaya_sakura, gaya_sakura_utuh) and the nine
kultivator:tata_letak structures kota_tema/air/<distrik>. The structure sets, the biome tags and the built-in pack
dunia_kotatema are written by 38TK inti (tools/gen_tempat_tema_data.py, salts from Tema.java). Run:

    python tools/gen_tempat_tema_air.py            (then python tools/cek_tempat.py)

City plan before rotation (38.T.5.2; districts 96 x 96 = 2 x 2 lots of 48; north = -z; city centre (0, 0)):

             x = -1               x = 0                 x = +1
 z = -1   [taman_sastra]  ~~~~ [rumah_sutra]  ~~~~~~ [bukit_teh]
             |                    |                     |
 z =  0   [jembatan_kembar] ~~ [danau_lentera] ~~~~~ [pasar_kanal]
             |                    |                     |
 z = +1   [hunian_kanal]  ~~~~ [gerbang_air]  ~~~~~~ [dermaga_perahu]
                                  || (Kanal Utama leaves through the Pintu Air)

Water network (city coordinates, one rule for every district so the canals of two district starts meet exactly):
  * every district border is a canal: 6 water blocks centred on the border (3 in each district), a 2-block towpath on
    each bank (side code K = 2 path + 3 water);
  * the ring canal runs inside the low city wall: 3 water blocks in the edge district + 3 in the wall lot, the wall
    rises straight out of the water (towpath only on the town side);
  * water surface = street floor - 1 (water y 2..3 over a gravel/clay bed at y 1, street floor y 4 = tl.LANTAI_Y);
  * district-internal canals: Jembatan Kembar (two canals crossing at the district centre), Kanal Utama of Gerbang Air
    (north-south through the district into the lake and out through the Pintu Air), the 9-wide market canal of Pasar
    Kanal, the boat-house slip of Dermaga Perahu, the Danau Lentera (64 x 50, 3 deep, clay bed);
  * stone arch bridges (deck +4, three blocks of head room over the towpath, four over the water) cross every
    district-border canal twice, at the lot centres; flat slab bridges cross the ring canal at the three gates.

Every district is drawn on one canvas in CITY coordinates (KotaKanvas: 112 x H x 112, the district plus 8 blocks
around it for the wall band) and cut into its four lots and its wall pieces, so courtyards, ponds and bridges that span
two lots stay whole. Life points (titik), anchors (DuniaAnchor) and side codes (kultivator_sisi) go with each lot.
Anchors carry their city-local position (data ux, uz) so the server rebuilds the town frame (centre, rotation, floor)
from any one of them (dunia/tempat/tema/air/PetaAir). Only world generation in NEW chunks ever places these templates
(DESIGN 38.0 rule 1); boats, looms, the water gate and lanterns at runtime are entities or client pictures.
Deterministic: the same bytes on every run.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt as nbtmod  # noqa: E402
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import kota_besar_kit as K  # noqa: E402
import hias_tema as H  # noqa: E402

F = "kota_tema/air"
NS = "kultivator:"
RENCANA = "kota_tema_air"
LY = tl.LANTAI_Y          # street floor (y 4)
SEL = 96                  # district cell
SETENGAH = 144            # half the city side (3 x 96 / 2)
TEMBOK = 8                # wall band outside the cells
TINGGI = 60               # canvas height (pagoda tops stay below)
AIR_Y = (2, 3)            # canal water layers (surface = floor - 1)
DASAR_Y = 1               # canal bed
DECK = 4                  # arch bridge deck height over the floor

# districts in Tema.java order: id -> (gx, gz)
DISTRIK = {
    "taman_sastra": (-1, -1), "rumah_sutra": (0, -1), "bukit_teh": (1, -1),
    "jembatan_kembar": (-1, 0), "danau_lentera": (0, 0), "pasar_kanal": (1, 0),
    "hunian_kanal": (-1, 1), "gerbang_air": (0, 1), "dermaga_perahu": (1, 1),
}
ORDER = list(DISTRIK)

# ================================================================================================= palette (jiangnan)
PAL = dict(K.PAL)
PAL.update({
    "plaster": "minecraft:white_terracotta", "plaster_alt": "minecraft:calcite",
    "roof": "minecraft:deepslate_tile_stairs", "roof_slab": "minecraft:deepslate_tile_slab",
    "roof_full": "minecraft:deepslate_tiles", "tile": "minecraft:deepslate_tiles",
    "tile_stairs": "minecraft:deepslate_tile_stairs", "tile_slab": "minecraft:deepslate_tile_slab",
    "ridge": "minecraft:polished_blackstone_brick_slab", "ridge_stairs": "minecraft:polished_blackstone_brick_stairs",
    "red": "minecraft:dark_oak_log", "lacquer": "minecraft:red_terracotta", "beam": "minecraft:stripped_dark_oak_log",
    "wood": "minecraft:dark_oak_planks", "wood_stairs": "minecraft:dark_oak_stairs", "wood_slab": "minecraft:dark_oak_slab",
    "kerb": "minecraft:stone_bricks", "kerb_alt": "minecraft:mossy_stone_bricks", "kerb_wall": "minecraft:stone_brick_wall",
    "kerb_stairs": "minecraft:stone_brick_stairs", "kerb_slab": "minecraft:stone_brick_slab",
    "kerb_chisel": "minecraft:chiseled_stone_bricks",
    "paving": "minecraft:polished_andesite", "paving_alt": "minecraft:andesite",
    "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:andesite", "found": "minecraft:stone",
    "path": "minecraft:polished_andesite", "path_alt": "minecraft:tuff",
    "cloth": "minecraft:blue_wool", "cloth_alt": "minecraft:white_wool", "banner": "blue", "banner_alt": "white",
    "glass": "minecraft:white_stained_glass_pane", "lamp": "minecraft:ochre_froglight",
    "leaves": "minecraft:spruce_leaves", "trunk": "minecraft:spruce_log",
    "flower_tall": "minecraft:peony", "flower_tall_alt": "minecraft:lilac", "flower": "minecraft:azure_bluet",
    "flower_alt": "minecraft:lily_of_the_valley", "rug": "minecraft:light_gray_carpet", "rug_alt": "minecraft:gray_carpet",
    "bed": "minecraft:light_blue_bed",
})

AIR = "minecraft:air"
WATER = ("minecraft:water", {"level": "0"})


# ================================================================================================= the canvas
class KotaKanvas(K.Kanvas):
    """A Kanvas addressed in CITY coordinates (x east, z south, city centre 0,0): the canvas origin is (ox, oz).
    Every kit helper (fill, walls, roof, tempel, titik, jangkar ...) works unchanged; cells, titik and entities are kept
    in canvas-local coordinates for cutting."""

    def __init__(self, ox, oz, sx, sz, seed):
        super().__init__(sx, TINGGI, sz, seed, PAL)
        self.ox, self.oz = ox, oz

    def inside(self, x, y, z):
        return 0 <= x - self.ox < self.size[0] and 0 <= y < self.size[1] and 0 <= z - self.oz < self.size[2]

    def set(self, x, y, z, block, props=None, nbt=None):
        if not self.inside(x, y, z):
            raise IndexError("(%d,%d,%d) outside canvas at %d,%d" % (x, y, z, self.ox, self.oz))
        if props and "hanging" in props and str(block).endswith("froglight"):
            props = {"axis": "y"}          # kit lamps: a froglight paper lamp has no 'hanging'
        key = nbtmod._state_key(block, props)
        loc = (x - self.ox, y, z - self.oz)
        if key[0] == "minecraft:structure_void":
            self.cells.pop(loc, None)
            return
        self.cells[loc] = (key, dict(nbt) if nbt else None)

    def get(self, x, y, z):
        cell = self.cells.get((x - self.ox, y, z - self.oz))
        return None if cell is None else cell[0][0]

    def clear(self, x, y, z):
        self.cells.pop((x - self.ox, y, z - self.oz), None)

    def titik(self, jenis, x, y, z, hadap="south", data=None):
        if not tl.titik_sah(jenis):
            raise ValueError("unknown titik type %r" % jenis)
        if hadap not in K.FACE:
            raise ValueError("bad hadap %r" % hadap)
        if not self.inside(x, y, z):
            self.clipped += 1
            return None
        e = {"jenis": jenis, "pos": [int(x) - self.ox, int(y), int(z) - self.oz], "hadap": hadap}
        if data:
            e["data"] = {str(k): str(v) for k, v in data.items()}
        self.titik_list.append(e)
        return e

    def entity(self, x, y, z, nbt):
        bx, by, bz = int(math.floor(x)), int(math.floor(y)), int(math.floor(z))
        if not self.inside(bx, by, bz):
            raise IndexError("entity (%r,%r,%r) outside canvas" % (x, y, z))
        self.entities.append((float(x) - self.ox, float(y), float(z) - self.oz, dict(nbt)))

    def jangkar_air(self, x, y, z, kind, yaw=0.0, **data):
        """A DuniaAnchor of this theme: always tema=air, ux/uz = city-local block position (PetaAir frame)."""
        d = {"tema": "air", "ux": int(math.floor(x)), "uz": int(math.floor(z)), "uy": int(math.floor(y)) - LY}
        d.update(data)
        self.jangkar(x, y, z, kind, yaw, **d)

    def datar(self):
        """A plain Kanvas sharing the cells (canvas-local coordinates) for the kit's shape pass and the cutter."""
        k = K.Kanvas(self.size[0], self.size[1], self.size[2], 0, PAL)
        k.cells = self.cells
        k.entities = self.entities
        k.titik_list = self.titik_list
        k.ox, k.oz = self.ox, self.oz       # city origin, for the canal boxes of potong()
        return k


# ================================================================================================= water geometry
# Water rectangles in city coordinates [x0, x1) x [z0, z1): (x0, z0, x1, z1, kind). Kind "kanal" = bed y1, water y2-3;
# "danau" = clay bed y0, water y1-3; "pasar" like kanal; the towpath strips are computed from the "kanal" borders.
def _garis():
    """The standard canals: every district border inside the city (x and z = -48, 48) and the ring canal."""
    out = []
    for b in (-SEL // 2, SEL // 2):
        out.append((b - 3, -SETENGAH - 3, b + 3, SETENGAH + 3, "kanal"))      # north-south canal at x = b
        out.append((-SETENGAH - 3, b - 3, SETENGAH + 3, b + 3, "kanal"))      # east-west canal at z = b
    # ring canal: 3 water inside the cells + 3 in the wall band
    out.append((-SETENGAH - 3, -SETENGAH - 3, -SETENGAH + 3, SETENGAH + 3, "kanal"))
    out.append((SETENGAH - 3, -SETENGAH - 3, SETENGAH + 3, SETENGAH + 3, "kanal"))
    out.append((-SETENGAH - 3, -SETENGAH - 3, SETENGAH + 3, -SETENGAH + 3, "kanal"))
    out.append((-SETENGAH - 3, SETENGAH - 3, SETENGAH + 3, SETENGAH + 3, "kanal"))
    return out


# district-internal water (added by the district plans below, before drawing)
AIR_DALAM = [
    # Jembatan Kembar (-96, 0): two canals crossing at the district centre
    (-96 - 3, -48, -96 + 3, 48, "kanal"),
    (-144, -3, -48, 3, "kanal"),
    # Gerbang Air (0, 96): Kanal Utama from the border canal z = 48 to the ring canal and through the wall arch
    (-3, 48, 3, SETENGAH + 3, "kanal"),
    (-4, SETENGAH + 3, 4, SETENGAH + 7, "kanal"),      # the Pintu Air arch through the south wall
    # Danau Lentera (0, 0): the lake and its channel to the south border canal
    (-32, -25, 32, 25, "danau"),
    (-3, 25, 3, 48, "kanal"),
    # Pasar Kanal (96, 0): the 9-wide morning market canal from the border canal x = 48 to the ring canal (z -20..-12,
    # clear of the border-canal bridge at z = -24)
    (48, -20, SETENGAH, -11, "pasar"),
    # Dermaga Perahu (96, 96): the dragon-boat slip from the border canal z = 48 into the boat house
    (60, 48, 70, 76, "kanal"),
    # Hunian Kanal (-96, 96): the back-water canal between the two house rows (lot border z = 96)
    (-144, 93, -48, 99, "kanal"),
]
AIR_SEMUA = _garis() + AIR_DALAM


_AIRMAP = {}


def _peta_air():
    """The water map of the whole city and wall band, built once: {(x, z): kind}."""
    if not _AIRMAP:
        lim = SETENGAH + TEMBOK
        for (x0, z0, x1, z1, kind) in AIR_SEMUA:
            for x in range(max(x0, -lim), min(x1, lim)):
                for z in range(max(z0, -lim), min(z1, lim)):
                    if abs(x + 0.5) > SETENGAH + 3 and abs(z + 0.5) > SETENGAH + 3:
                        continue        # outside the ring corner
                    if (x, z) not in _AIRMAP or kind == "danau":
                        _AIRMAP[(x, z)] = kind
    return _AIRMAP


def air_di(x, z):
    """The water kind at column (x, z) or None."""
    return _peta_air().get((x, z))


def tepi_air(x, z, r=2):
    """True when a water column lies within Chebyshev distance r of (x, z) (towpath / quay ring)."""
    m = _peta_air()
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if (dx or dz) and (x + dx, z + dz) in m:
                return True
    return False


def dalam_sel(x, z):
    return -SETENGAH <= x < SETENGAH and -SETENGAH <= z < SETENGAH


def dalam_tembok(x, z):
    """The wall band (8 blocks outside the cells)."""
    lim = SETENGAH + TEMBOK
    return -lim <= x < lim and -lim <= z < lim and not dalam_sel(x, z)


# ================================================================================================= ground and water
def dasar(k):
    """Foundation, street floor, canals and towpaths of the whole canvas (city part); the wall band is tembok()."""
    sx, _, sz = k.size
    for x in range(k.ox, k.ox + sx):
        for z in range(k.oz, k.oz + sz):
            if not dalam_sel(x, z):
                continue
            kind = air_di(x, z)
            if kind:
                saluran(k, x, z, kind)
                continue
            # only the street floor and the quay walls are blocks of the template: below the floor beard_box raises the
            # terrain (as in the Kota Besar lots), which keeps the theme under the 38.C.10 gzip budget
            tepi = tepi_air(x, z, 2)
            if tepi:
                hadap_air = tepi_air(x, z, 1)
                if hadap_air:
                    for y in range(DASAR_Y, LY):
                        k.put(x, y, z, "kerb_alt" if k.rng.random() < 0.28 else "kerb")
                k.put(x, LY, z, "kerb" if hadap_air else ("paving" if (x * 7 + z * 3) % 11 else "paving_alt"))
            else:
                k.put(x, LY, z, "floor" if (x * 5 + z * 3 + (x * z) % 7) % 9 else "floor_alt")


def saluran(k, x, z, kind):
    """One water column: bed, water, explicit air above (so beard_box never fills a canal)."""
    if kind == "danau":
        k.put(x, 0, z, "minecraft:clay")
        for y in (1, 2, 3):
            k.put(x, y, z, *WATER)
    else:
        r = (x * 73856093 ^ z * 19349663) & 0xFF
        k.put(x, DASAR_Y, z, "minecraft:gravel" if r < 110 else "minecraft:clay" if r < 200 else "minecraft:mud")
        if r % 37 == 0:
            k.put(x, 2, z, "minecraft:seagrass")
        else:
            k.put(x, 2, z, *WATER)
        k.put(x, 3, z, *WATER)
    k.put(x, LY, z, AIR)       # one explicit air layer at street height: beard_box never lids a canal


# ================================================================================================= bridges
def _along(sumbu, t, s):
    """Bridge axis helper: sumbu 'x' runs along x (crossing a north-south canal), 'z' along z."""
    return (t, s) if sumbu == "x" else (s, t)


def jembatan(k, sumbu, b, c, lebar=5, tinggi=DECK, gaya="lengkung", lentera=True, air=None):
    """A stone arch bridge across the standard 6-wide canal centred on border coordinate b (water b-3 .. b+2,
    towpaths b-5, b-4 and b+3, b+4), centred on cross coordinate c. sumbu 'x': the canal runs north-south (x = b) and
    the bridge runs along x. gaya: 'lengkung' (round shoulders), 'persegi' (square lintel on abutments, Yong'an),
    'bundar' (high half-round, Shide). Deck top +tinggi; head room 3 over the towpaths."""
    half = lebar // 2
    deck_y = LY + tinggi
    up = "east" if sumbu == "x" else "south"
    down = "west" if sumbu == "x" else "north"
    w0, w1 = air or (b - 3, b + 2)     # first and last water coordinate (air = wider canals, e.g. the market canal)
    t_lo, t_hi = w0 - 2, w1 + 2

    def P(t, s, y, role, props=None):
        x, z = _along(sumbu, t, s)
        k.put(x, y, z, role, props)

    # ramps (stairs climbing toward the canal) with solid support
    for i in range(1, tinggi + 1):
        y = LY + i
        tw = t_lo - (tinggi + 1 - i)
        te = t_hi + (tinggi + 1 - i)
        for s in range(c - half, c + half + 1):
            for t, face in ((tw, up), (te, down)):
                for yy in range(LY + 1, y):
                    P(t, s, yy, "kerb")
                edge = s in (c - half, c + half)
                P(t, s, y, "kerb_stairs", {"facing": face, "half": "bottom", "shape": "straight", "waterlogged": "false"})
                if edge:
                    P(t, s, y + 1, "kerb_wall")
    # deck
    for t in range(t_lo, t_hi + 1):
        for s in range(c - half, c + half + 1):
            centre = s == c
            P(t, s, deck_y, "kerb_chisel" if centre and (t - t_lo) % 3 == 1 else "kerb")
            if s in (c - half, c + half):
                P(t, s, deck_y + 1, "kerb_wall")
            for yy in range(LY + 1, deck_y):
                P(t, s, yy, AIR)
    # arch shapes under the deck
    for s in range(c - half, c + half + 1):
        if gaya == "shide":
            # Shide bridge: a massive half-round arch, solid spandrels, the opening springs from the water surface;
            # the towpaths pass through low side openings (3 head room)
            mid = (w0 + w1) / 2.0
            r = (w1 - w0) / 2.0 + 0.6
            for t in range(t_lo, t_hi + 1):
                for yy in range(DASAR_Y, deck_y):
                    if w0 <= t <= w1:
                        if yy >= 3 and (t - mid) ** 2 + (yy - 3) ** 2 <= r * r:
                            P(t, s, yy, AIR if yy > 3 else WATER[0], None if yy > 3 else WATER[1])
                        elif yy >= 3:
                            ring = (t - mid) ** 2 + (yy - 3) ** 2 <= (r + 1.1) ** 2
                            P(t, s, yy, "kerb_chisel" if ring and s in (c - half, c + half) else "kerb")
                        elif t in (w0, w1):
                            P(t, s, yy, "kerb")
                    elif yy >= LY:
                        P(t, s, yy, AIR if yy <= LY + 3 and yy > LY else ("kerb" if yy > LY else "kerb"))
            continue
        if gaya == "persegi":
            for t in (w0, w1):
                for yy in range(DASAR_Y, deck_y):
                    P(t, s, yy, "kerb")
            for t in range(w0 + 1, w1):
                P(t, s, deck_y - 1, "kerb_slab", {"type": "top", "waterlogged": "false"})
        elif gaya == "bundar":
            P(w0, s, deck_y - 1, "kerb")
            P(w1, s, deck_y - 1, "kerb")
            P(w0, s, deck_y - 2, "kerb_stairs", {"facing": down, "half": "top", "shape": "straight", "waterlogged": "false"})
            P(w1, s, deck_y - 2, "kerb_stairs", {"facing": up, "half": "top", "shape": "straight", "waterlogged": "false"})
            P(w0 + 1, s, deck_y - 1, "kerb_stairs", {"facing": down, "half": "top", "shape": "straight", "waterlogged": "false"})
            P(w1 - 1, s, deck_y - 1, "kerb_stairs", {"facing": up, "half": "top", "shape": "straight", "waterlogged": "false"})
        else:
            P(w0, s, deck_y - 1, "kerb_stairs", {"facing": down, "half": "top", "shape": "straight", "waterlogged": "false"})
            P(w1, s, deck_y - 1, "kerb_stairs", {"facing": up, "half": "top", "shape": "straight", "waterlogged": "false"})
    # lanterns on posts at both feet
    if lentera:
        for t in (t_lo - tinggi - 1, t_hi + tinggi + 1):
            for s in (c - half, c + half):
                P(t, s, LY + 1, "kerb_chisel")
                P(t, s, LY + 2, "kerb_wall")
                P(t, s, LY + 3, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
                if hasattr(k, "lights"):
                    x, z = _along(sumbu, t, s)
                    k.lights.append((x, LY + 3, z))


def jembatan_datar(k, sumbu, t0, t1, c, lebar=3):
    """A flat granite slab bridge at street level over water from t0 to t1 (inclusive), with a pier every 3 and low
    parapets (the ring-canal gate bridges: boats do not pass under them)."""
    half = lebar // 2
    for t in range(t0, t1 + 1):
        for s in range(c - half - 1, c + half + 2):
            x, z = _along(sumbu, t, s)
            edge = s in (c - half - 1, c + half + 1)
            k.put(x, LY, z, "kerb" if edge else ("paving" if (t + s) % 5 else "kerb_chisel"))
            if edge:
                k.put(x, LY + 1, z, "kerb_wall" if (t - t0) % 3 else "kerb_chisel")
            if (t - t0) % 3 == 1:
                for y in range(DASAR_Y, LY):
                    k.put(x, y, z, "kerb")


def semua_jembatan(k):
    """Two arch bridges on every district-border canal segment, at the lot centres (city coordinates)."""
    for b in (-SEL // 2, SEL // 2):
        for zc in (-SEL, 0, SEL):
            for off in (-24, 24):
                c = zc + off
                gaya = "bundar" if (b + c) % 3 == 0 else "lengkung"
                jembatan(k, "x", b, c, gaya=gaya)
        for xc in (-SEL, 0, SEL):
            for off in (-24, 24):
                c = xc + off
                gaya = "bundar" if (b + c) % 3 == 0 else "lengkung"
                jembatan(k, "z", b, c, gaya=gaya)


# ================================================================================================= city wall
GERBANG_DARAT = (("barat", -SETENGAH, -24), ("timur", SETENGAH, 24), ("selatan", SETENGAH, -18))
GERBANG_AIR_X = (-4, 4)       # the water arch of the Pintu Air (x range), south wall


def tembok(k):
    """The low white city wall (6 high over the street, black tile coping) standing in the ring canal: 3 water,
    4 wall, 1 plinth; watch pavilions at the corners, two land gates (west, east), the Gerbang Air with the Pintu Air
    arch and its land gate (south)."""
    sx, _, sz = k.size
    lim = SETENGAH + TEMBOK
    for x in range(k.ox, k.ox + sx):
        for z in range(k.oz, k.oz + sz):
            if not dalam_tembok(x, z):
                continue
            d = max(abs(x + 0.5), abs(z + 0.5)) - SETENGAH   # 0.5 .. 7.5 outward
            if d < 3:
                kind = air_di(x, z) or "kanal"
                saluran(k, x, z, kind)
                continue
            outer = d > 7
            for y in range(DASAR_Y if d < 4 or outer else LY - 1, LY + 1):
                k.put(x, y, z, "kerb")
            if outer:
                k.slab(x, LY + 1, z, "kerb_slab")
                continue
            for y in range(LY + 1, LY + 7):
                k.put(x, y, z, "plaster" if y > LY + 1 else "kerb")
    # coping and leaky windows, per side
    for side in ("n", "s", "w", "e"):
        for a in range(-lim, lim):
            inner, outer = SETENGAH + 3, SETENGAH + 6
            for d, face in ((inner, "in"), (outer, "out")):
                x, z = _sisi_xz(side, a, d)
                if not k.inside(x, LY + 7, z) or not dalam_tembok(x, z):
                    continue
                f = _hadap_luar(side) if face == "out" else K.OPP[_hadap_luar(side)]
                k.stairs(x, LY + 7, z, "tile_stairs", K.OPP[f])
            for d in (SETENGAH + 4, SETENGAH + 5):
                x, z = _sisi_xz(side, a, d)
                if k.inside(x, LY + 7, z) and dalam_tembok(x, z):
                    k.put(x, LY + 7, z, "tile")
                    k.slab(x, LY + 8, z, "ridge")
            if a % 8 == 4 and abs(a) < SETENGAH - 6:
                for d in (SETENGAH + 3,):
                    x, z = _sisi_xz(side, a, d)
                    for y in (LY + 3, LY + 4):
                        if k.inside(x, y, z):
                            K.lattice(k, x, y, z, K.OPP[_hadap_luar(side)])
    for (cx, cz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        menara_sudut(k, cx, cz)
    for nama, garis, c in GERBANG_DARAT:
        gerbang_darat(k, nama, garis, c)
    gerbang_air(k)


def _sisi_xz(side, a, d):
    """Column at distance d outward from the cell edge on side n/s/w/e, running coordinate a."""
    if side == "n":
        return a, -d - 1
    if side == "s":
        return a, d
    if side == "w":
        return -d - 1, a
    return d, a


def _hadap_luar(side):
    return {"n": "north", "s": "south", "w": "west", "e": "east"}[side]


def menara_sudut(k, sx, sz):
    """A corner watch pavilion: a 6 x 6 stone and plaster tower over the corner of the wall with a hip roof."""
    x0 = -SETENGAH - TEMBOK if sx < 0 else SETENGAH + 2
    z0 = -SETENGAH - TEMBOK if sz < 0 else SETENGAH + 2
    x1, z1 = x0 + 5, z0 + 5
    if not (k.inside(x0, 0, z0) or k.inside(x1, 0, z1)):
        return
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(0, LY + 8):
                k.put(x, y, z, "kerb" if y <= LY + 1 else "plaster")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            k.put(x, LY + 8, z, "kerb")
            if edge:
                k.put(x, LY + 9, z, "rail" if (x + z) % 2 else "red")
    for (px, pz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(LY + 9, LY + 13):
            k.put(px, y, pz, "red")
    K.lit(k, (x0 + x1) // 2, LY + 13, (z0 + z1) // 2, chain=1, role="lamp")
    K.hip(k, x0, LY + 13, z0, x1, z1, overhang=1, top="pearl", role="tile_stairs", slab_role="tile_slab", full_role="tile")


def gerbang_darat(k, nama, garis, c):
    """A land gate: 3-wide passage through the wall, a flat slab bridge over the ring canal, a two-storey gatehouse with
    a hip roof and guard points; c = running coordinate of the gate centre."""
    if nama in ("barat", "timur"):
        sumbu = "x"
        sgn = -1 if nama == "barat" else 1
    else:
        sumbu = "z"
        sgn = 1
    inner = SETENGAH - 3        # first water column on the town side (|coord|)
    wall0, wall1 = SETENGAH + 3, SETENGAH + 7

    def T(d):
        return sgn * d if sgn > 0 else -d - 1

    # bridge over the ring water (both halves) and the passage floor
    t0, t1 = sorted((T(inner), T(SETENGAH + 2)))
    jembatan_datar(k, sumbu, t0, t1, c, lebar=3)
    for d in range(wall0, wall1 + 1):
        for s in range(c - 1, c + 2):
            x, z = _along(sumbu, T(d), s)
            k.put(x, LY, z, "paving")
            for y in range(LY + 1, LY + 5):
                k.put(x, y, z, AIR)
        for s in (c - 2, c + 2):
            x, z = _along(sumbu, T(d), s)
            for y in range(LY + 1, LY + 5):
                k.put(x, y, z, "kerb")
    # arch head and gatehouse
    for d in range(wall0, wall1 + 1):
        for s in range(c - 4, c + 5):
            x, z = _along(sumbu, T(d), s)
            k.put(x, LY + 5, z, "kerb_chisel" if abs(s - c) <= 1 else "kerb")
            for y in range(LY + 6, LY + 9):
                k.put(x, y, z, "plaster")
    for d in (wall0, wall1):
        for s in range(c - 4, c + 5):
            x, z = _along(sumbu, T(d), s)
            if abs(s - c) in (1, 3):
                k.put(x, LY + 7, z, "minecraft:dark_oak_trapdoor", {"facing": _arah(sumbu, sgn, d == wall1), "half": "bottom",
                                                                    "open": "true", "powered": "false", "waterlogged": "false"})
    for d in range(wall0, wall1 + 1):
        for s in range(c - 4, c + 5):
            x, z = _along(sumbu, T(d), s)
            k.put(x, LY + 9, z, "wood")
    for (dd, ss) in ((wall0, c - 4), (wall0, c + 4), (wall1, c - 4), (wall1, c + 4)):
        for y in range(LY + 10, LY + 13):
            x, z = _along(sumbu, T(dd), ss)
            k.put(x, y, z, "red")
    xa, za = _along(sumbu, T(wall0), c - 4)
    xb, zb = _along(sumbu, T(wall1), c + 4)
    K.hip(k, min(xa, xb), LY + 13, min(za, zb), max(xa, xb), max(za, zb), overhang=1, top="pearl",
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    xg, zg = _along(sumbu, T(SETENGAH - 8), c + 3)
    k.titik("jaga", xg, LY + 1, zg, _arah(sumbu, sgn, True))
    xg, zg = _along(sumbu, T(SETENGAH - 8), c - 3)
    k.titik("jaga", xg, LY + 1, zg, _arah(sumbu, sgn, True))
    xp, zp = _along(sumbu, T(SETENGAH - 6), c)
    k.titik("pintu", xp, LY + 1, zp, _arah(sumbu, sgn, True), {"nama": "gerbang_" + nama})


def _arah(sumbu, sgn, keluar):
    if sumbu == "x":
        f = "east" if sgn > 0 else "west"
    else:
        f = "south" if sgn > 0 else "north"
    return f if keluar else K.OPP[f]


def gerbang_air(k):
    """The Gerbang Air (38.T.5.2): a water arch 8 wide over the Kanal Utama through the south wall, the Pintu Air leaf
    (entity kultivator:pintu_air, anchor kota_tema/pintu_air) in its grooves, a three-bay gate tower with a
    two-tier roof over it; the water ends at a sunken sill under the outer face (the canal never spills outside)."""
    z0, z1 = SETENGAH + 3, SETENGAH + 7
    xa, xb = GERBANG_AIR_X
    if not k.inside(0, 0, z0) and not k.inside(0, 0, z1):
        return
    for z in range(z0, z1 + 1):
        for x in range(xa - 3, xb + 3):
            if xa <= x < xb:
                if z < z1:
                    saluran(k, x, z, "kanal")
                    for y in range(LY, LY + 4):
                        k.put(x, y, z, AIR)
                else:
                    for y in range(0, LY):
                        k.put(x, y, z, "kerb")
                    k.put(x, LY, z, "minecraft:iron_bars")
                    k.put(x, LY + 1, z, "minecraft:iron_bars")
                    for y in range(LY + 2, LY + 4):
                        k.put(x, y, z, AIR)
            else:
                for y in range(0, LY + 7):
                    k.put(x, y, z, "kerb" if y <= LY + 1 else "plaster")
        # grooves for the leaf
        for x in (xa - 1, xb):
            if z == z0 + 1:
                for y in range(DASAR_Y, LY + 5):
                    k.put(x, y, z, "minecraft:polished_deepslate")
        for x in range(xa, xb):
            k.put(x, LY + 4, z, "kerb_stairs" if x in (xa, xb - 1) else "kerb",
                  {"facing": "west" if x == xa else "east", "half": "top", "shape": "straight", "waterlogged": "false"}
                  if x in (xa, xb - 1) else None)
            k.put(x, LY + 5, z, "kerb_chisel")
    # tower: two storeys over the arch, a gallery on the town side, two-tier roof
    tx0, tx1 = xa - 6, xb + 5
    for z in range(z0, z1 + 1):
        for x in range(tx0, tx1 + 1):
            k.put(x, LY + 6, z, "kerb")
            edge = x in (tx0, tx1) or z in (z0, z1)
            for y in range(LY + 7, LY + 11):
                k.put(x, y, z, ("red" if (x - tx0) % 4 == 0 else "plaster") if edge else AIR)
            k.put(x, LY + 11, z, "wood")
    for x in range(tx0 + 2, tx1 - 1):
        if (x - tx0) % 4 != 0:
            for z, face in ((z0, "north"), (z1, "south")):
                K.lattice(k, x, LY + 8, z, face)
                K.lattice(k, x, LY + 9, z, face)
    K.hip(k, tx0, LY + 12, z0, tx1, z1, overhang=2, tiers=2, tier_gap=2, tier_band=3, band_role="plaster", top="spire",
          role="tile_stairs", slab_role="tile_slab", full_role="tile")
    K.lit(k, 0, LY + 11, z0 + 2, chain=1, role="lamp")
    k.jangkar_air(0.5, LY + 1, z0 + 1.5, "kota_tema/pintu_air", varian="kanal", buka=5, tutup=21, lebar=xb - xa)
    k.titik("jaga", xa - 2, LY + 7, z0, "north")
    k.titik("jaga", xb + 1, LY + 7, z0, "north")
    k.titik("pandang", 0, LY + 7, z0 + 2, "south", {"nama": "pintu_air"})


# ================================================================================================= cutting and output
POOLS = {}      # pool path -> (templates [ids], utuh)
LOTS = {d: [] for d in ORDER}


def _sisi_lot(distrik, i, j, kode_dalam):
    """Side codes (n, e, s, w) of main lot (i, j) of a district: every district border is a canal (K); inner sides
    from the district's table kode_dalam {"h0": between (0,0)-(1,0), "h1": (0,1)-(1,1), "v0": (0,0)-(0,1), "v1":
    (1,0)-(1,1)} (default J)."""
    d = {"n": "K", "e": "K", "s": "K", "w": "K"}
    if i == 0:
        d["e"] = kode_dalam.get("h%d" % j, "J")
    else:
        d["w"] = kode_dalam.get("h%d" % j, "J")
    if j == 0:
        d["s"] = kode_dalam.get("v%d" % i, "J")
    else:
        d["n"] = kode_dalam.get("v%d" % i, "J")
    return d


def potong(k, lx0, lz0, w, d, nama, sisi, tinggi_min=24):
    """Cuts the canvas rectangle [lx0, lx0 + w) x [lz0, lz0 + d) (canvas-local) into one Lot with its blocks, anchors,
    titik (box titik clipped to the lot) and kanal boxes for 38PR; returns the saved Lot."""
    top = 0
    cells = []
    for (x, y, z), cell in k.cells.items():
        if lx0 <= x < lx0 + w and lz0 <= z < lz0 + d:
            cells.append(((x - lx0, y, z - lz0), cell))
            if cell[0][0] != AIR:
                top = max(top, y)
    h = max(tinggi_min, min(TINGGI, top + 2))
    lot = tl.Lot(F, nama, tinggi=h, lebar=w, dalam=d, palet=PAL)
    for (x, y, z), cell in cells:
        if y < h:
            lot.cells[(x, y, z)] = cell
    for (x, y, z, e) in k.entities:
        if lx0 <= x < lx0 + w and lz0 <= z < lz0 + d:
            lot.entities.append((x - lx0, y, z - lz0, e))
            if e.get("id") == "kultivator:dunia_anchor":
                lot.jangkar_n += 1
    for t in k.titik_list:
        px, py, pz = t["pos"]
        if lx0 <= px < lx0 + w and lz0 <= pz < lz0 + d and py < h:
            e = {"jenis": t["jenis"], "pos": [px - lx0, py, pz - lz0], "hadap": t["hadap"]}
            if "data" in t:
                e["data"] = dict(t["data"])
                if t["jenis"] in tl.TITIK_KOTAK:
                    e["data"]["dx"] = str(min(int(t["data"]["dx"]), lx0 + w - 1 - px))
                    e["data"]["dz"] = str(min(int(t["data"]["dz"]), lz0 + d - 1 - pz))
            lot.titik_list.append(e)
    _hemat_titik(lot.titik_list, tl.MAKS_TITIK - 4)
    # canal boxes (38.R: canal water may be restored as a source inside a kanal box)
    X0, Z0 = k.ox + lx0, k.oz + lz0
    for (ax0, az0, ax1, az1, kind) in AIR_SEMUA:
        bx0, bz0, bx1, bz1 = max(ax0, X0), max(az0, Z0), min(ax1, X0 + w), min(az1, Z0 + d)
        if bx0 < bx1 and bz0 < bz1 and len(lot.titik_list) < tl.MAKS_TITIK:
            y0 = 1 if kind == "danau" else 2
            lot.kotak("kanal", bx0 - X0, y0, bz0 - Z0, bx1 - 1 - X0, 3, bz1 - 1 - Z0, "south", air=kind)
    lot.sisi(sisi["n"], sisi["e"], sisi["s"], sisi["w"])
    lot.simpan()
    return lot


TITIK_LONGGAR = ("kursi", "meja_makan", "kerja:kuali", "peti_milik", "pintu", "obrol")


def _hemat_titik(lst, maks):
    """Keeps a crowded lot (many canal houses) under the point budget: drops, one at a time, a point of the most
    numerous furniture-like kind (a chair of a fourth dining set, a second back door ...) until it fits; service,
    route, mooring and lantern points are never dropped."""
    while len(lst) > maks:
        counts = {}
        for e in lst:
            if e["jenis"] in TITIK_LONGGAR:
                counts[e["jenis"]] = counts.get(e["jenis"], 0) + 1
        if not counts:
            return
        jenis = max(sorted(counts), key=lambda j: counts[j])
        idx = max(i for i, e in enumerate(lst) if e["jenis"] == jenis)
        del lst[idx]


def daftar_lot(distrik, dx, dz, pool, templates, utuh=False):
    LOTS[distrik].append({"dx": dx, "dz": dz, "pool": pool})
    POOLS[pool] = (templates, utuh)


def siapkan(k):
    """Bridges and the wall over a district drawing, then the kit's shape pass (fences, walls, panes join)."""
    semua_jembatan(k)
    tembok(k)
    flat = k.datar()
    K.connect_all(flat)
    return flat


def kanvas_distrik(distrik, varian=0):
    gx, gz = DISTRIK[distrik]
    seed = tl._seed(F + "/" + distrik) + varian * 7919
    k = KotaKanvas(SEL * gx - SEL // 2 - TEMBOK, SEL * gz - SEL // 2 - TEMBOK, SEL + 2 * TEMBOK, SEL + 2 * TEMBOK, seed)
    dasar(k)
    return k


# rooms to dress (D-TK1 detail pass): registered while drawing, dressed first in selesai_distrik() so the dressing only
# fills what is still empty (hias_tema.ruang never overwrites a placed block)
_RUANG = []


def ruang(k, x0, z0, x1, z1, f, gaya, **kw):
    """Registers one room of canvas k (city coordinates, inner floor box, floor level f, hias_tema style)."""
    _RUANG.append((k, (x0, z0, x1, z1, f, gaya), kw))


def hias(k):
    n = 0
    for entry in [r for r in _RUANG if r[0] is k]:
        n += H.ruang(k, *entry[1], **entry[2])
        _RUANG.remove(entry)
    return n


def selesai_distrik(k, distrik, nama_lot, kode_dalam=None, varian=None, utuh=(), tembok_utuh=True):
    """Bridges and walls over the district's own drawing, the shape pass, then the cut into four lots (names
    nama_lot[(i, j)]) and the wall pieces of the city edge. varian: list of extra canvases (same district, other seed)
    whose lots join the same pools (hunian variants)."""
    hias(k)
    for vk in (varian or []):
        hias(vk)
    flat = siapkan(k)
    vflats = [siapkan(vk) for vk in (varian or [])]
    gx, gz = DISTRIK[distrik]
    kode_dalam = kode_dalam or {}
    for i in (0, 1):
        for j in (0, 1):
            name = "%s/%s" % (distrik, nama_lot[(i, j)])
            sisi = _sisi_lot(distrik, i, j, kode_dalam)
            lot = potong(flat, TEMBOK + 48 * i, TEMBOK + 48 * j, 48, 48, name, sisi)
            templates = [lot.id]
            for n, vflat in enumerate(vflats):
                vlot = potong(vflat, TEMBOK + 48 * i, TEMBOK + 48 * j, 48, 48, "%s_%s" % (name, "bcdefg"[n]), sisi)
                templates.append(vlot.id)
            daftar_lot(distrik, -48 + 48 * i, -48 + 48 * j, name, templates, utuh=nama_lot[(i, j)] in utuh)
    # wall pieces (city edge only), cut in place (putar 0)
    pieces = []
    if gz == -1:
        pieces += [(TEMBOK + 48 * i, 0, 48, TEMBOK, "utara%d" % i, -48 + 48 * i, -56, ("B", "T", "K", "T")) for i in (0, 1)]
    if gz == 1:
        pieces += [(TEMBOK + 48 * i, TEMBOK + SEL, 48, TEMBOK, "selatan%d" % i, -48 + 48 * i, 48, ("K", "T", "B", "T")) for i in (0, 1)]
    if gx == -1:
        pieces += [(0, TEMBOK + 48 * j, TEMBOK, 48, "barat%d" % j, -56, -48 + 48 * j, ("T", "K", "T", "B")) for j in (0, 1)]
    if gx == 1:
        pieces += [(TEMBOK + SEL, TEMBOK + 48 * j, TEMBOK, 48, "timur%d" % j, 48, -48 + 48 * j, ("T", "B", "T", "K")) for j in (0, 1)]
    for (cxs, czs, lx, lz) in ((-1, -1, 0, 0), (1, -1, TEMBOK + SEL, 0), (-1, 1, 0, TEMBOK + SEL), (1, 1, TEMBOK + SEL, TEMBOK + SEL)):
        if gx == cxs and gz == czs:
            pieces.append((lx, lz, TEMBOK, TEMBOK, "sudut", -56 if cxs < 0 else 48, -56 if czs < 0 else 48,
                           ("B" if czs < 0 else "T", "B" if cxs > 0 else "T", "B" if czs > 0 else "T", "B" if cxs < 0 else "T")))
    for (lx, lz, w, d, nm, dx, dz, s) in pieces:
        name = "tembok/%s_%s" % (distrik, nm)
        lot = potong(flat, lx, lz, w, d, name, {"n": s[0], "e": s[1], "s": s[2], "w": s[3]}, tinggi_min=16)
        daftar_lot(distrik, dx, dz, name, [lot.id], utuh=tembok_utuh)


# ================================================================================================= JSON
GAYA_AIR = {
    "sakura": dict(tl.ganti_kayu("dark_oak", "cherry")),
}
GAYA_AIR["sakura"].update({
    "minecraft:oak_leaves": "minecraft:cherry_leaves",
    "minecraft:spruce_leaves": "minecraft:cherry_leaves",
    "minecraft:light_blue_bed": "minecraft:pink_bed",
    "minecraft:blue_wool": "minecraft:pink_wool",
    "minecraft:light_gray_carpet": "minecraft:pink_carpet",
})
LAPUK_AIR = (
    ("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.06),
    ("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.03),
    ("minecraft:white_terracotta", "minecraft:light_gray_terracotta", 0.025),
    ("minecraft:polished_andesite", "minecraft:andesite", 0.04),
    ("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.03),
)


def processor(gaya, states, lapuk):
    ganti = GAYA_AIR.get(gaya, {})
    rules = []
    for bid, props in sorted(set(states)):
        to = ganti.get(bid)
        if to is None:
            continue
        inp, out = {"id": bid}, {"id": to}
        if props:
            inp["properties"] = {a: b for a, b in props}
            out["properties"] = {a: b for a, b in props}
        rules.append({"input_predicate": {"predicate_type": "minecraft:blockstate_match", "block_state": inp},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": out})
    if gaya == "sakura":
        # loose grass in gardens becomes fallen cherry petals
        for amount, p in ((3, 0.5), (2, 1.0)):
            rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:short_grass",
                                              "probability": p},
                          "location_predicate": {"predicate_type": "minecraft:always_true"},
                          "output_state": {"id": "minecraft:pink_petals", "properties": {"facing": "north", "flower_amount": str(amount)}}})
    if lapuk:
        for src, dst, p in LAPUK_AIR:
            rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": src, "probability": p},
                          "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": dst}})
    procs = []
    if rules:
        procs.append({"processor_type": "minecraft:rule", "rules": rules})
    procs.append({"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"})
    return {"processors": procs}


def write_processors():
    states = tl.semua_state()
    tl.tulis_processor(F + "/lapuk", processor("dasar", states, True))
    tl.tulis_processor(F + "/utuh", processor("dasar", states, False))
    tl.tulis_processor(F + "/gaya_sakura", processor("sakura", states, True))
    tl.tulis_processor(F + "/gaya_sakura_utuh", processor("sakura", states, False))


def write_pools():
    for pid, (templates, utuh) in sorted(POOLS.items()):
        base = NS + F + ("/utuh" if utuh else "/lapuk")
        name = "%s/%s" % (F, pid)
        tl.tulis_pool(name, [(tl.elemen(t, base), 1) for t in templates])
        proc = NS + F + ("/gaya_sakura_utuh" if utuh else "/gaya_sakura")
        tl.tulis_pool(name + "_sakura", [(tl.elemen(t, proc), 1) for t in templates])


def write_structures():
    for d in ORDER:
        lots = [{"dx": l["dx"], "dz": l["dz"], "pool": "%s%s/%s" % (NS, F, l["pool"]), "putar": 0} for l in LOTS[d]]
        s = tl.struktur_tata_letak(RENCANA, d, lots, "#minecraft:is_overworld", gaya=[("minecraft:cherry_grove", "sakura")])
        tl.tulis_wg("structure", "%s/%s" % (F, d), s)


# ================================================================================================= jiangnan kit
ROT = {"south": 0, "west": 1, "north": 2, "east": 3}


def pasang(k, src, x0, z0, depan):
    """Pastes a south-facing building canvas with its min corner at (x0, z0) facing `depan` (footprint w x d for
    south/north, d x w for east/west)."""
    K.tempel(k, src, x0, z0, ROT[depan])


def atap_pelana(p, x0, y, z0, x1, z1, overhang=1, kuda=True):
    """A black tile gable roof, ridge along x, eaves overhanging front and back by `overhang`, no gable overhang (the
    horse-head walls close both ends); a ridge of blackstone slabs with upturned ends. Returns the ridge y."""
    va, vb = z0 - overhang, z1 + overhang
    rows = vb - va + 1
    layers = rows // 2
    for i in range(layers):
        for x in range(x0, x1 + 1):
            p.stairs(x, y + i, va + i, "tile_stairs", "south")
            p.stairs(x, y + i, vb - i, "tile_stairs", "north")
    top = y + layers
    mids = [va + layers] if rows % 2 else [va + layers - 1, va + layers]
    for x in range(x0, x1 + 1):
        for v in mids:
            if rows % 2:
                p.put(x, top - 1, v, "tile")
            p.slab(x, top, v, "ridge")
    for x, face in ((x0, "east"), (x1, "west")):
        p.stairs(x, top + 1, mids[0], "ridge_stairs", face, half="top")
    if kuda:
        for x in (x0, x1):
            matou(p, x, y, va, vb)
    return top


def matou(p, x, y, va, vb):
    """Horse-head wall (matouqiang) on gable column x: the white gable rises over the roof in three flat steps, each
    capped with black tiles and an upturned tile end."""
    n = vb - va + 1
    third = max(1, n // 3)
    zones = [(va, va + third - 1, 1), (va + third, vb - third, 2), (vb - third + 1, vb, 1)]
    for (za, zb, lv) in zones:
        mid = (za + zb) / 2.0
        slope = min(za - va, vb - zb) if lv == 1 else min(int(mid) - va, vb - int(mid))
        h = y + slope + (2 if lv == 2 else 1)
        for z in range(za, zb + 1):
            for yy in range(y, h + 1):
                p.put(x, yy, z, "plaster")
            p.slab(x, h + 1, z, "tile_slab")
        p.stairs(x, h + 1, za, "tile_stairs", "north", half="top") if za > va else None
        p.stairs(x, h + 1, zb, "tile_stairs", "south", half="top") if zb < vb else None


def rumah_src(w, d, seed, tingkat=2, toko=None, belakang=False, kamar=2, papan=True):
    """A south-facing Jiangnan house on a (w+2) x (d+2) canvas (house at offset 1): stone plinth, white plaster in a
    dark timber frame, lattice windows, a gable roof between horse-head walls; ground floor = hall + kitchen or a shop
    front (toko = list of goods blocks), upper floor = bedrooms. Life points: pintu, kasur, meja_makan, kursi,
    kerja:kuali, lapak (shops), peti_milik."""
    p = K.Kanvas(w + 2, TINGGI, d + 2, seed, PAL)
    x0, z0, x1, z1 = 1, 1, w, d
    f = LY
    cx = (x0 + x1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            p.put(x, f, z, "kerb" if edge else ("wood" if not toko or z < z1 - 3 else "paving"))
    h1 = 4
    p.walls(x0, f + 1, z0, x1, f + h1, z1, role="plaster", pillar="beam", windows=0, interior_air=True)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if p.get(x, f + 1, z) == p.b("plaster"):
                p.put(x, f + 1, z, "kerb")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if p.get(x, f + 1, z) == p.b("plaster"):
                p.put(x, f + 1, z, "kerb")
    K.beam_band(p, x0, z0, x1, z1, f + h1)
    if toko:
        for x in range(x0 + 1, x1):
            for y in range(f + 1, f + h1):
                p.put(x, y, z1, AIR)
        K.counter(p, x0 + 1, x1 - 1, z1 - 1, f, "south")
        for i, x in enumerate(range(x0 + 1, x1)):
            if i % 2 == 0:
                p.put(x, f + 2, z1 - 1, toko[i % len(toko)])
        for x in range(x0, x1 + 1):
            p.slab(x, f + h1, z1 + 1, "wood_slab", "top")
        K.lit(p, x0 + 1, f + h1, z1 + 1, chain=1, role="lamp")
        K.lit(p, x1 - 1, f + h1, z1 + 1, chain=1, role="lamp")
        p.titik("lapak", cx, f + 1, z1 - 2, "south")
        K.wall_banner(p, x0, f + 3, z1 + 1, "south", "blue")
        K.wall_banner(p, x1, f + 3, z1 + 1, "south", "white")
    else:
        K.door(p, cx, f + 1, z1, "south")
        p.titik("pintu", cx, f + 1, z1 + 1, "north")
        for x in (x0 + 1, x1 - 1):
            if abs(x - cx) > 1:
                K.lattice(p, x, f + 2, z1, "south")
                K.lattice(p, x, f + 3, z1, "south")
    if belakang:
        K.door(p, cx, f + 1, z0, "north")
        p.titik("pintu", cx, f + 1, z0 + 1, "south", {"nama": "belakang"})
    for z in range(z0 + 2, z1 - 1, 3):
        for x, face in ((x0, "west"), (x1, "east")):
            K.lattice(p, x, f + 2, z, face)
            K.lattice(p, x, f + 3, z, face)
    # ground floor furnishing
    if not toko and w >= 6 and d >= 6:
        K.furnish_dining(p, cx, (z0 + z1) // 2 + 1, f, n=4)
    if w >= 5:
        K.furnish_kitchen(p, x0 + 1, z0 + 1, min(x1 - 2, x0 + 4), f, z0 + 1, "south")
    K.shelf(p, x1 - 1, f + 2, z0 + 1, "south")
    K.pot(p, x0 + 1, f + 1, z1 - 1 if not toko else z1 - 4)
    K.lit(p, cx, f + h1, (z0 + z1) // 2, chain=1, role="glow")
    roof_y = f + h1 + 1
    if tingkat >= 2:
        f2 = f + h1 + 1
        h2 = 3
        K.upper_floor(p, x0 + 1, z0 + 1, x1 - 1, z1 - 1, f2, hole=(x1 - 1, z0 + 2, x1 - 1, z0 + 4))
        K.ladder(p, x1 - 1, f + 1, f2, z0 + 3, "west")
        p.walls(x0, f2, z0, x1, f2 + h2, z1, role="plaster", pillar="beam", windows=0, interior_air=False)
        p.air(x0 + 1, f2 + 1, z0 + 1, x1 - 1, f2 + h2 - 1, z1 - 1)
        K.ladder(p, x1 - 1, f2 - 0, f2, z0 + 3, "west")
        for x in range(x0 + 1, x1):
            if (x - x0) % 2 == 1:
                K.lattice(p, x, f2 + 1, z1, "south")
                K.lattice(p, x, f2 + 2, z1, "south")
                K.lattice(p, x, f2 + 1, z0, "north")
        if toko:
            # the upper storey projects over the shop front on a beam (a deep eave for the street)
            for x in range(x0, x1 + 1):
                p.stairs(x, f2 - 1, z1 + 1, "wood_stairs", "north", half="top")
        beds = 0
        for i in range(kamar):
            bx = x0 + 1 + i * 2
            if bx >= x1 - 1:
                break
            K.bed(p, bx, f2 + 1, z0 + 1, "north")
            p.titik("kasur", bx, f2 + 1, z0 + 1, "south")
            beds += 1
        K.chest(p, x1 - 2, f2 + 1, z1 - 1, None, "north")
        p.titik("peti_milik", x1 - 2, f2 + 1, z1 - 2, "south")
        K.barrel(p, x0 + 1, f2 + 1, z1 - 1, "north")
        if w >= 6:
            K.rug(p, x0 + 2, (z0 + z1) // 2, x1 - 2, z1 - 2, f2 + 1)
        K.lit(p, cx, f2 + h2, (z0 + z1) // 2, chain=1, role="glow")
        roof_y = f2 + h2 + 1
    else:
        K.bed(p, x0 + 1, f + 1, z0 + 2, "north") if not toko else None
        if not toko:
            p.titik("kasur", x0 + 1, f + 1, z0 + 2, "south")
    atap_pelana(p, x0, roof_y, z0, x1, z1, overhang=1)
    if papan and not toko:
        K.plaque(p, cx, f + h1 - 1, z1 + 1, "x") if p.inside(cx, f + h1 - 1, z1 + 1) else None
    return p


def rumah(k, x0, z0, w, d, depan, seed, **kw):
    """Builds and pastes a Jiangnan house whose footprint min corner is (x0, z0) in city coordinates (the canvas margin
    of 1 is taken care of); w along the front, d deep."""
    src = rumah_src(w, d, seed, **kw)
    if depan in ("south", "north"):
        pasang(k, src, x0 - 1, z0 - 1, depan)
    else:
        pasang(k, src, x0 - 1, z0 - 1, depan)
    return src


def paviliun(k, x0, z0, x1, z1, f=LY, tinggi=4, tiers=1, buka=("north", "south", "east", "west"), lantai="wood",
             di_air=False, kursi=True, puncak="pearl", lampu=True):
    """An open pavilion: timber columns every 3, a bench rail (meirenkao) between them except at the openings, beam
    frieze, a hip roof of black tiles with curled corners (tiers > 1: a two-tier roof). di_air: stands on stilts in
    water, the floor over the surface. Returns the centre (cx, cz)."""
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            k.put(x, f, z, "kerb" if edge and lantai != "wood" else lantai)
            if di_air and edge and ((x - x0) % 3 == 0 or (z - z0) % 3 == 0):
                for y in range(DASAR_Y, f):
                    k.put(x, y, z, "red")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                for y in range(f + 1, f + tinggi + 1):
                    k.put(x, y, z, AIR)
                continue
            along = (x - x0) if z in (z0, z1) else (z - z0)
            corner = x in (x0, x1) and z in (z0, z1)
            side = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
            if corner or along % 3 == 0:
                for y in range(f + 1, f + tinggi + 1):
                    k.put(x, y, z, "red")
            else:
                opening = side in buka and abs((x - cx) if side in ("north", "south") else (z - cz)) <= 1
                if not opening:
                    k.put(x, f + 1, z, "wood_fence")
                    if kursi and along % 3 == 1:
                        k.put(x, f + 2, z, "minecraft:dark_oak_pressure_plate", {"powered": "false"})
                    K.lattice(k, x, f + tinggi, z, side)
    K.beam_band(k, x0, z0, x1, z1, f + tinggi + 1)
    K.brackets(k, x0, z0, x1, z1, f + tinggi + 1)
    K.hip(k, x0, f + tinggi + 2, z0, x1, z1, overhang=1, tiers=tiers, tier_gap=2, tier_band=2, band_role="plaster",
          top=puncak, role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            k.put(x, f + tinggi + 1, z, "wood")
    if lampu:
        K.lit(k, cx, f + tinggi + 1, cz, chain=1, role="lamp")
    return cx, cz


def koridor(k, sumbu, a0, a1, c, f=LY, dinding=0, jendela=3):
    """A covered gallery (lang) 3 wide from a0 to a1 along sumbu ('x' or 'z') centred on c: stone floor, timber posts
    every 3 on both sides, a low gable of black tiles; dinding = -1 / +1 puts a white wall with leaky lattice windows on
    that side (0 = open both sides)."""
    for t in range(a0, a1 + 1):
        for s in range(c - 1, c + 2):
            x, z = _along(sumbu, t, s)
            k.put(x, f, z, "paving" if s == c else "kerb")
            for y in range(f + 1, f + 4):
                k.put(x, y, z, AIR)
        for side in (-1, 1):
            x, z = _along(sumbu, t, c + side)
            if dinding == side:
                for y in range(f + 1, f + 4):
                    k.put(x, y, z, "plaster" if y > f + 1 else "kerb")
                if jendela and (t - a0) % jendela == jendela // 2 and t not in (a0, a1):
                    face = ("north" if side < 0 else "south") if sumbu == "x" else ("west" if side < 0 else "east")
                    K.lattice(k, x, f + 2, z, face)
            elif (t - a0) % 3 == 0 or t == a1:
                for y in range(f + 1, f + 4):
                    k.put(x, y, z, "red")
            else:
                k.put(x, f + 1, z, "wood_fence")
        for s in range(c - 2, c + 3):
            x, z = _along(sumbu, t, s)
            if abs(s - c) == 2:
                if sumbu == "x":
                    face = "south" if s < c else "north"
                else:
                    face = "east" if s < c else "west"
                k.stairs(x, f + 4, z, "tile_stairs", face)
            elif abs(s - c) == 1:
                k.put(x, f + 4, z, "tile")
                if sumbu == "x":
                    face = "south" if s < c else "north"
                else:
                    face = "east" if s < c else "west"
                k.stairs(x, f + 5, z, "tile_stairs", face)
            else:
                k.put(x, f + 4, z, "tile")
                k.slab(x, f + 5, z, "ridge")
        if (t - a0) % 6 == 3:
            x, z = _along(sumbu, t, c)
            K.lit(k, x, f + 4, z, chain=1, role="lamp")


def tembok_taman(k, sumbu, a0, a1, c, f=LY, tinggi=4, pintu=(), bulan=(), jendela=5):
    """A white garden wall 1 thick with a black tile cap; pintu = cross-coordinates of 3-wide openings, bulan = centres
    of moon gates (round openings 5 across), leaky windows (lattice) every `jendela`."""
    for t in range(a0, a1 + 1):
        x, z = _along(sumbu, t, c)
        for y in range(f + 1, f + tinggi + 1):
            k.put(x, y, z, "plaster" if y > f + 1 else "kerb")
        k.put(x, f + tinggi + 1, z, "tile")
        k.slab(x, f + tinggi + 2, z, "ridge")
        for side in (-1, 1):
            xx, zz = _along(sumbu, t, c + side)
            face = ("north" if side < 0 else "south") if sumbu == "x" else ("west" if side < 0 else "east")
            k.stairs(xx, f + tinggi + 1, zz, "tile_stairs", K.OPP[face])
        if jendela and (t - a0) % jendela == jendela // 2 and all(abs(t - p) > 3 for p in tuple(pintu) + tuple(bulan)):
            face = "north" if sumbu == "x" else "west"
            K.lattice(k, x, f + 2, z, face)
            K.lattice(k, x, f + 3, z, face)
    for p0 in pintu:
        for t in range(p0 - 1, p0 + 2):
            x, z = _along(sumbu, t, c)
            for y in range(f + 1, f + 4):
                k.put(x, y, z, AIR)
            k.put(x, f, z, "kerb")
    for b0 in bulan:
        r = 2.6
        cy = f + 3
        for t in range(b0 - 3, b0 + 4):
            for y in range(f + 1, f + tinggi + 1):
                x, z = _along(sumbu, t, c)
                dd = math.hypot(t - b0, y - cy)
                if dd <= r and y >= f + 1:
                    k.put(x, y, z, AIR)
                elif dd <= r + 0.9:
                    k.put(x, y, z, "kerb")
        for t in range(b0 - 2, b0 + 3):
            x, z = _along(sumbu, t, c)
            k.put(x, f, z, "kerb")


def kolam(k, elips, f=LY, teratai=0.10, batu=True):
    """An irregular garden pond: union of ellipses [(cx, cz, rx, rz)], water y 2-3 over a clay bed, rocky rim of tuff,
    andesite and mossy cobble at street level with a few standing rocks; lily pads. Returns the set of water columns."""
    air = set()
    for (cx, cz, rx, rz) in elips:
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                if ((x - cx) / (rx + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2 <= 1.0:
                    air.add((x, z))
    for (x, z) in air:
        k.put(x, 0, z, "found")
        k.put(x, DASAR_Y, z, "minecraft:clay")
        k.put(x, 2, z, *WATER)
        k.put(x, 3, z, *WATER)
        k.put(x, f, z, AIR)
        k.put(x, f + 1, z, AIR)
        r = (x * 31 + z * 17) % 100
        if r < teratai * 100:
            k.put(x, f, z, "minecraft:lily_pad")
    if batu:
        rim = set()
        for (x, z) in air:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    q = (x + dx, z + dz)
                    if q not in air:
                        rim.add(q)
        for (x, z) in sorted(rim):
            r = (x * 13 + z * 29) % 100
            k.put(x, f, z, "minecraft:tuff" if r < 30 else "minecraft:mossy_cobblestone" if r < 55 else "minecraft:andesite")
            for y in range(1, f):
                k.put(x, y, z, "kerb")
            if r < 9:
                k.put(x, f + 1, z, "minecraft:tuff" if r < 5 else "minecraft:mossy_cobblestone")
            elif r < 14:
                k.put(x, f + 1, z, "minecraft:fern")
    return air


def gunungan(k, cx, cz, rx, rz, tinggi, f=LY, terowongan=None):
    """A hollow scholar's rockery (Taihu rocks): a lumpy mound of tuff, calcite, diorite and andesite with holes and
    overhangs, a tunnel along `terowongan` ('x' or 'z') at walking height, ferns and moss on the ledges. Returns the
    top position (x, y, z)."""
    best = (cx, f, cz)
    for x in range(cx - rx, cx + rx + 1):
        for z in range(cz - rz, cz + rz + 1):
            d = ((x - cx) / (rx + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2
            if d > 1.0:
                continue
            n = math.sin(x * 1.7 + z * 0.9) * 1.3 + math.cos(x * 0.7 - z * 1.3) * 1.1
            h = int(tinggi * (1.0 - d) ** 0.6 + n)
            for y in range(f + 1, f + 1 + max(1, h)):
                r = (x * 73 + y * 37 + z * 19) % 100
                hollow = y > f + 1 and y < f + h - 1 and r < 13
                if hollow:
                    k.put(x, y, z, AIR)
                    continue
                k.put(x, y, z, "minecraft:tuff" if r < 38 else "minecraft:calcite" if r < 62 else "minecraft:diorite" if r < 82 else "minecraft:andesite")
            topy = f + max(1, h)
            if (x * 7 + z * 11) % 9 == 0:
                k.put(x, topy, z, "minecraft:moss_carpet")
            elif (x * 5 + z * 3) % 13 == 0:
                k.put(x, topy, z, "minecraft:fern")
            if topy > best[1]:
                best = (x, topy, z)
    if terowongan:
        for t in range(-(rx if terowongan == "x" else rz) - 1, (rx if terowongan == "x" else rz) + 2):
            for s in (-1, 0, 1):
                x, z = (cx + t, cz + s) if terowongan == "x" else (cx + s, cz + t)
                for y in (f + 1, f + 2, f + 3):
                    k.put(x, y, z, AIR)
                k.put(x, f, z, "minecraft:gravel")
    return best


def taman_tanah(k, x0, z0, x1, z1, f=LY, rumput=0.18):
    """Garden ground: grass with some podzol and moss, short grass and ferns."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            r = (x * 41 + z * 23 + (x * z) % 13) % 100
            k.put(x, f, z, "minecraft:grass_block" if r < 82 else "minecraft:moss_block" if r < 92 else "minecraft:podzol",
                  {"snowy": "false"} if r < 82 or r >= 92 else None)
            if r < rumput * 100 and k.get(x, f + 1, z) in (None, AIR):
                k.put(x, f + 1, z, "minecraft:short_grass" if r % 3 else "minecraft:fern")


def jalan_batu(k, pts, f=LY, lebar=1, role="minecraft:gravel", alt="paving"):
    """A garden path of gravel with stepping stones along a polyline."""
    k.path(pts, f, width=lebar, role=role, alt=alt, alt_chance=0.3)
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        n = max(abs(bx - ax), abs(bz - az), 1)
        for i in range(n + 1):
            x = round(ax + (bx - ax) * i / n)
            z = round(az + (bz - az) * i / n)
            if k.get(x, f + 1, z) in ("minecraft:short_grass", "minecraft:fern"):
                k.put(x, f + 1, z, AIR)


def bambu(k, x0, z0, x1, z1, f=LY, rapat=5):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x * 7 + z * 13) % rapat == 0:
                k.put(x, f, z, "minecraft:podzol", {"snowy": "false"})
                K.bamboo_clump(k, x, z, f + 1, 7 + (x + z) % 5)


def pinus_pot(k, x, z, f=LY):
    K.potted_pine(k, x, f, z)


def murbei(k, x, z, f=LY):
    """A low pollarded mulberry: short oak trunk, a flat crown of azalea leaves (fed to the silkworms)."""
    for y in (f + 1, f + 2):
        k.put(x, y, z, "minecraft:oak_log", {"axis": "y"})
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                K.leaf(k, x + dx, f + 3, z + dz, "minecraft:azalea_leaves")
            if abs(dx) + abs(dz) <= 1:
                K.leaf(k, x + dx, f + 4, z + dz, "minecraft:azalea_leaves")


def tangga_cuci(k, sumbu, t_air, c, arah, f=LY, lebar=3):
    """Washing steps (ghat) cut into a quay down to the water: stairs at y 4 and 3 (waterlogged) going toward the water
    in direction arah (+1 / -1 along sumbu), t_air = the first water coordinate."""
    for s in range(c - lebar // 2, c + lebar // 2 + 1):
        face = ("east" if arah < 0 else "west") if sumbu == "x" else ("south" if arah < 0 else "north")
        x, z = _along(sumbu, t_air - arah, s)
        k.put(x, f, z, "kerb_stairs", {"facing": face, "half": "bottom", "shape": "straight", "waterlogged": "false"})
        x, z = _along(sumbu, t_air, s)
        k.put(x, 3, z, "kerb_stairs", {"facing": face, "half": "bottom", "shape": "straight", "waterlogged": "true"})
        k.put(x, 2, z, "kerb")


def perahu_vanilla(k, x, z, yaw=0.0):
    """A small moored wooden boat (vanilla dark oak boat entity, saved with the chunk; never respawned)."""
    k.entity(x + 0.5, 3.6, z + 0.5, {"id": "minecraft:dark_oak_boat", "Rotation": [nbtmod.Float(yaw), nbtmod.Float(0.0)]})


def lampion(k, x, y, z, n=1):
    """A red paper lantern string under an eave: chain + ochre froglight (the warm paper glow)."""
    K.lit(k, x, y, z, chain=n, role="lamp")


def zona(k, x, z, distrik):
    """The district's Mode Kehancuran / safe zone anchor of 38TK inti (protect 72)."""
    k.jangkar_air(x + 0.5, LY + 1, z + 0.5, "kota_tema/zona", distrik=distrik, protect=72)


def tambatan(k, x, z, kelas, yaw=0.0, **data):
    """A mooring titik on the water surface (block y 3 = the water top block): kelas pos | sewa | lapak | naga | tur."""
    d = {"kelas": kelas}
    d.update(data)
    hadap = {0: "south", 90: "west", 180: "north", 270: "east"}.get(int(yaw) % 360, "south")
    k.titik("tambatan", x, 3, z, hadap, d)


def lentera_air(k, x, z, n):
    k.titik("lentera_air", x, LY + 1, z, "south", {"urut": n})


# ================================================================================================= Perahu Pos route
# The water lines of the Perahu Pos loop and the tour (city-local, x z on the water centre line), mirrored in
# dev.kultivator.dunia.tempat.tema.air.JalurKanal (keep both lists identical). Halte = stop.
RUTE_POS = [
    (48, 118, "halte:pusat"), (48, 48, ""), (0, 48, ""), (0, 108, "halte:gerbang_air"), (0, 48, ""),
    (0, 32, "halte:danau"), (0, 48, ""), (-48, 48, ""), (-48, 0, ""), (-74, 0, "halte:jembatan_kembar"),
    (-48, 0, ""), (-48, -48, ""), (-8, -48, "halte:rumah_sutra"), (48, -48, ""), (48, 48, ""), (48, 118, ""),
]


def titik_rute(k):
    """jalur_perahu titik every ~8 blocks along the Perahu Pos loop (data urut, jalur=pos, halte)."""
    n = 0
    for (ax, az, a_tag), (bx, bz, _b) in zip(RUTE_POS, RUTE_POS[1:]):
        steps = max(abs(bx - ax), abs(bz - az)) // 8 or 1
        for i in range(steps):
            x = ax + (bx - ax) * i // steps
            z = az + (bz - az) * i // steps
            data = {"urut": n, "jalur": "pos"}
            if i == 0 and a_tag:
                data["halte"] = a_tag.split(":")[1]
            if k.inside(x, 3, z):
                k.titik("jalur_perahu", x, 3, z, "south", data)
            n += 1


# ================================================================================================= districts
def C(distrik):
    gx, gz = DISTRIK[distrik]
    return SEL * gx, SEL * gz


def build_taman_sastra():
    """Taman Sang Sastrawan (38.T.5.2): a classical scholar's garden over the whole district."""
    d = "taman_sastra"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    x0, z0 = P(-41, -41)
    x1, z1 = P(35, 35)
    taman_tanah(k, x0, z0, x1, z1)
    # outer garden wall (ring sides at -42, bridge sides inside the landings at +36)
    tembok_taman(k, "x", cx - 42, cx + 36, cz - 42, jendela=6)
    tembok_taman(k, "z", cz - 42, cz + 36, cx - 42, jendela=6)
    tembok_taman(k, "x", cx - 42, cx + 36, cz + 36, pintu=(cx - 24,), bulan=(cx + 24,), jendela=6)
    tembok_taman(k, "z", cz - 42, cz + 36, cx + 36, pintu=(cz + 24,), bulan=(cz - 24,), jendela=6)
    for (u, v) in ((-24, 37), (24, 37)):
        x, z = P(u, v)
        k.path([(x, z), (x, z + 1)], LY, width=3, role="paving", alt="kerb", alt_chance=0.2)
    for (u, v) in ((37, -24), (37, 24)):
        x, z = P(u, v)
        k.path([(x, z), (x + 1, z)], LY, width=3, role="paving", alt="kerb", alt_chance=0.2)
    # the big winding pond
    air = kolam(k, [(cx + 0, cz + 2, 15, 9), (cx - 11, cz - 6, 8, 6), (cx + 11, cz + 8, 8, 5), (cx - 4, cz + 13, 7, 4),
                    (cx + 14, cz - 4, 5, 4)], teratai=0.14)
    # zigzag bridge (jiuqu) across the west lobe, slab deck flush with the banks, low rails
    zz = [P(-24, -2), P(-20, -2), P(-20, 2), P(-15, 2), P(-15, -1), P(-11, -1), P(-11, 3), P(-6, 3)]
    for (ax, az), (bx, bz) in zip(zz, zz[1:]):
        n = max(abs(bx - ax), abs(bz - az))
        for i in range(n + 1):
            x = ax + (0 if bx == ax else (i if bx > ax else -i))
            z = az + (0 if bz == az else (i if bz > az else -i))
            if (x, z) in air:
                k.put(x, LY - 1, z, "kerb")
                k.slab(x, LY, z, "kerb_slab", "top")
                for y in range(DASAR_Y, LY - 1):
                    k.put(x, y, z, "kerb") if (x + z) % 3 == 0 else None
    # Paviliun Bulan di Air: on stilts in the east lobe, reached by a short plank bridge
    px, pz = paviliun(k, cx + 8, cz - 4, cx + 14, cz + 2, f=LY, tinggi=4, buka=("west",), di_air=True, puncak="pearl")
    for x in range(cx + 3, cx + 8):
        k.put(x, LY, pz, "wood")
        for y in range(DASAR_Y, LY):
            k.put(x, y, pz, "red") if x % 2 == 0 else None
        k.put(x, LY + 1, pz - 1, "wood_fence")
        k.put(x, LY + 1, pz + 1, "wood_fence")
    K.low_table(k, px, LY + 1, pz)
    k.titik("kursi", px - 1, LY + 1, pz, "east", {"nama": "catur"})
    k.titik("kursi", px + 1, LY + 1, pz, "west", {"nama": "catur"})
    K.chair(k, px - 1, LY + 1, pz, "east")
    K.chair(k, px + 1, LY + 1, pz, "west")
    k.titik("pandang", px, LY + 1, pz + 2, "south", {"nama": "bulan_di_air"})
    k.titik("meditasi", px, LY + 1, pz - 2, "north")
    # library and the scholar's study at the north (faces the pond)
    lib = K.aula(k, cx - 10, cz - 39, cx + 8, cz - 29, h=5, podium_h=1, teras=2, roof="hip", door_sides=("south",),
                 door_w=3, tile=True, top="pearl")
    ix0, iz0, ix1, iz1 = lib["inner"]
    fl = lib["f"]
    K.bookshelf_wall(k, ix0, iz0, ix1, iz0, fl + 1, fl + 3, face="south")
    for x in (ix0 + 2, ix0 + 6, ix1 - 2):
        K.table(k, x, fl + 1, iz1 - 1, "minecraft:lectern" if x == ix0 + 6 else None)
        k.titik("meja_tulis", x, fl + 1, iz1 - 2, "south")
    K.rug(k, ix0 + 1, iz0 + 2, ix1 - 1, iz1 - 2, fl + 1)
    k.jangkar_air(cx - 0.5, LY + 1, cz - 26.5, "kota_tema/taman", yaw=0.0, layanan="bait", tokoh="wen_shi", situs_ekstra="taman_klasik")
    # covered galleries: north-east from the library to the east gate, west along the ring wall
    koridor(k, "x", cx + 9, cx + 33, cz - 33, dinding=-1)
    koridor(k, "z", cz - 33, cz + 16, cx - 38, dinding=-1)
    koridor(k, "x", cx - 38, cx - 26, cz + 17, dinding=0)
    # Paviliun Menyimak Hujan by the bamboo and the plantain (big dripleaf), west side
    hx, hz = paviliun(k, cx - 34, cz - 22, cx - 28, cz - 16, tinggi=4, buka=("east", "south"), puncak="pearl")
    for (u, v) in ((-32, -24), (-30, -24), (-27, -23), (-27, -21)):
        x, z = P(u, v)
        k.put(x, LY + 1, z, "minecraft:big_dripleaf", {"facing": "south", "tilt": "none", "waterlogged": "false"})
    bambu(k, cx - 37, cz - 28, cx - 27, cz - 25, rapat=4)
    k.titik("kursi", hx, LY + 1, hz - 1, "south", {"nama": "menyimak_hujan"})
    k.titik("meditasi", hx - 1, LY + 1, hz + 1, "east")
    k.titik("meditasi", hx + 1, LY + 1, hz + 1, "west")
    # the rockery hill with a tunnel and a tiny pavilion on top (south-west)
    top = gunungan(k, cx - 22, cz + 24, 9, 7, 8, terowongan="x")
    tx, ty, tz = top
    for x in range(tx - 1, tx + 2):
        for z in range(tz - 1, tz + 2):
            k.put(x, ty, z, "kerb")
            for y in range(ty + 1, ty + 4):
                k.put(x, y, z, AIR)
    for (qx, qz) in ((tx - 1, tz - 1), (tx + 1, tz - 1), (tx - 1, tz + 1), (tx + 1, tz + 1)):
        for y in range(ty + 1, ty + 4):
            k.put(qx, y, qz, "red")
    K.hip(k, tx - 1, ty + 4, tz - 1, tx + 1, tz + 1, overhang=1, top="pearl", role="tile_stairs", slab_role="tile_slab", full_role="tile")
    k.titik("meditasi", tx, ty + 1, tz, "north")
    k.titik("pandang", tx, ty + 1, tz + 1, "north", {"nama": "puncak_gunungan"})
    for i in range(1, ty - LY):
        x, z = tx + 2 + i, tz
        if k.get(x, LY + i, z) in (None, AIR):
            k.stairs(x, LY + i, z, "kerb_stairs", "west")
    # inner wall with the moon gate dividing the east bamboo court; leaky windows
    tembok_taman(k, "z", cz - 30, cz + 10, cx + 22, bulan=(cz - 12,), jendela=4)
    bambu(k, cx + 25, cz - 30, cx + 34, cz - 14, rapat=3)
    k.titik("pandang", cx + 20, LY + 1, cz - 12, "east", {"nama": "gerbang_bulan"})
    k.titik("meditasi", cx + 29, LY + 1, cz - 8, "north")
    # willows and pines around the water, potted pines on the terraces, stone lanterns, flowers
    for (u, v, kind) in ((-16, 9, "willow"), (17, 12, "willow"), (5, -12, "willow"), (-26, 6, "pine"), (26, 20, "pine"),
                         (-6, 22, "pine"), (12, 28, "pine")):
        x, z = P(u, v)
        if (x, z) not in air:
            K.tree(k, x, LY + 1, z, kind)
    for (u, v) in ((-14, -27), (12, -27), (-3, -27), (30, 30), (-38, 30)):
        x, z = P(u, v)
        pinus_pot(k, x, z)
    for (u, v) in ((-20, -10), (20, -12), (-8, 18), (16, 22), (0, -18), (-30, 0)):
        x, z = P(u, v)
        if (x, z) not in air:
            K.stone_lantern(k, x, LY + 1, z)
    for (u0, v0, u1, v1) in ((-36, 26, -30, 32), (24, 24, 30, 30), (-12, -24, -6, -20)):
        K.flowerbed(k, cx + u0, cz + v0, cx + u1, cz + v1, LY)
    jalan_batu(k, [P(-24, 37), P(-24, 30), P(-12, 24), P(-4, 18), P(8, 14), P(20, 18), P(24, 30), P(24, 37)])
    jalan_batu(k, [P(37, -24), P(28, -18), P(22, -12), P(14, -12), P(6, -18), P(0, -26)])
    jalan_batu(k, [P(37, 24), P(30, 20), P(20, 12)])
    jalan_batu(k, [P(-24, -2), P(-30, -8), P(-34, -14)])
    # points: meditation, talk, view
    for (u, v) in ((-19, -14), (19, 4), (-3, -16), (2, 20), (-36, 8)):
        x, z = P(u, v)
        k.titik("meditasi", x, LY + 1, z, "south")
    for (u, v, h) in ((-10, -18, "south"), (-8, -18, "south"), (24, 6, "west"), (-28, 12, "east"), (8, 24, "north"), (10, 24, "north")):
        x, z = P(u, v)
        k.titik("obrol", x, LY + 1, z, h)
    k.titik("pandang", cx - 6, LY + 1, cz - 14, "south", {"nama": "kolam_berliku"})
    k.jalur([P(-24, 34), P(-12, 24), P(8, 14), P(24, 30)], jalur="taman")
    zona(k, cx - 2, cz - 20, d)
    selesai_distrik(k, d, {(0, 0): "perpustakaan", (1, 0): "kebun_bambu", (0, 1): "gunungan", (1, 1): "kolam"},
                    kode_dalam={"h0": "B", "h1": "B", "v0": "B", "v1": "B"}, utuh=("perpustakaan", "kolam"))


def gudang_rak(k, x0, z0, x1, z1, f=LY):
    """Silkworm racks under an open shed: oak posts, four tiers of oak-slab trays carrying moss carpet 'mulberry
    leaves' (the worms themselves are a client picture)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1)
            if edge:
                for y in range(f + 1, f + 7):
                    k.put(x, y, z, "minecraft:oak_fence")
            else:
                for tier in range(4):
                    y = f + 1 + tier * 2
                    k.slab(x, y, z, "minecraft:oak_slab", "top")
                    k.put(x, y + 1, z, "minecraft:moss_carpet" if (x + z + tier) % 3 else "minecraft:green_carpet")


def build_rumah_sutra():
    """Rumah Sutra Su (38.T.5.2): the silk house of five generations: silkworm court, spinning room, weaving hall with
    six looms, cloth gallery, Madam Su's office; mulberry orchard; silk workers' houses."""
    d = "rumah_sutra"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    # compound wall (north half), gates south (main), west and east (bridge landings at v = -24)
    tembok_taman(k, "x", cx - 39, cx + 39, cz - 42, tinggi=5, jendela=7)
    tembok_taman(k, "x", cx - 39, cx + 39, cz - 7, tinggi=5, pintu=(cx,), jendela=7)
    tembok_taman(k, "z", cz - 42, cz - 7, cx - 39, tinggi=5, pintu=(cz - 24,), jendela=7)
    tembok_taman(k, "z", cz - 42, cz - 7, cx + 39, tinggi=5, pintu=(cz - 24,), jendela=7)
    K.paving(k, cx - 38, cz - 41, cx + 38, cz - 8, LY, role="paving", alt="kerb", alt_chance=0.08)
    # main gate hall with plaque and couplets
    gate = K.aula(k, cx - 5, cz - 12, cx + 5, cz - 6, h=5, podium_h=1, teras=1, roof="hip", door_sides=("south", "north"),
                  door_w=3, tile=True, top="pearl")
    K.couplets(k, cx, LY + 3, cz - 6, "south")
    lampion(k, cx - 3, LY + 6, cz - 5)
    lampion(k, cx + 3, LY + 6, cz - 5)
    # weaving hall (centre): six looms in two rows
    hall = K.aula(k, cx - 15, cz - 34, cx + 15, cz - 16, h=6, podium_h=1, teras=2, roof="hip", tiers=1,
                  door_sides=("south", "north"), door_w=3, tile=True, top="pearl")
    hx0, hz0, hx1, hz1 = hall["inner"]
    fl = hall["f"]
    loom_xs = (cx - 8, cx, cx + 8)
    for row, z in enumerate((hz0 + 3, hz0 + 8)):
        for i, x in enumerate(loom_xs):
            k.titik("alat_tenun", x, fl + 1, z, "south", {"urut": row * 3 + i, "varian": 1 if (row, i) == (0, 1) else 0})
            k.titik("kerja:tenun", x, fl + 1, z + 2, "north")
            K.chair(k, x, fl + 1, z + 2, "north")
    for x in range(hx0 + 1, hx1, 4):
        K.wall_banner(k, x, fl + 4, hz0, "south", ("red", "white", "light_blue", "pink", "yellow", "lime")[(x // 4) % 6])
    K.rug(k, hx0 + 1, hz1 - 3, hx1 - 1, hz1 - 1, fl + 1, role="minecraft:white_carpet", border="minecraft:light_gray_carpet")
    # Madam Su's office behind the hall (north): counter, account books, the family shrine
    off = K.aula(k, cx - 8, cz - 41, cx + 8, cz - 36, h=4, podium_h=1, teras=0, roof="hip", door_sides=("south",),
                 door_w=3, tile=True, top="pearl")
    ox0, oz0, ox1, oz1 = off["inner"]
    K.counter(k, ox0 + 2, ox1 - 2, oz1 - 1, off["f"], "south")
    K.bookshelf_wall(k, ox0, oz0, ox1, oz0, off["f"] + 1, off["f"] + 2, face="south")
    K.candle(k, cx, off["f"] + 2, oz0 + 1, n=3, color="red")
    k.jangkar_air(cx + 0.5, LY + 1, cz - 34.5, "kota_tema/sutra", yaw=0.0, layanan="sutra", tokoh="su_jin")
    k.titik("kerja:timbangan", cx, off["f"] + 1, oz1 - 2, "south")
    # silkworm court (west): racks under two open sheds, leaf baskets, the spinning room at its north
    for (u0, v0, u1, v1) in ((-36, -32, -26, -26), (-36, -22, -26, -16)):
        gudang_rak(k, cx + u0, cz + v0, cx + u1, cz + v1)
        for x in range(cx + u0 - 1, cx + u1 + 2):
            for z in range(cz + v0 - 1, cz + v1 + 2):
                k.slab(x, LY + 9, z, "tile_slab")
        k.titik("kerja:rak", cx + u1 + 1, LY + 1, cz + (v0 + v1) // 2, "west")
    for (u, v) in ((-24, -30), (-24, -20), (-22, -25)):
        x, z = P(u, v)
        K.barrel(k, x, LY + 1, z, "up")
        k.put(x, LY + 2, z, "minecraft:azalea_leaves", {"persistent": "true", "distance": "1", "waterlogged": "false"})
    spin = K.aula(k, cx - 37, cz - 41, cx - 20, cz - 36, h=4, podium_h=1, teras=0, roof="hip", door_sides=("south",),
                  door_w=3, tile=True, top=None)
    sx0, sz0, sx1, sz1 = spin["inner"]
    for i, x in enumerate(range(sx0 + 1, sx1, 3)):
        k.put(x, spin["f"] + 1, sz0 + 1, "minecraft:grindstone", {"face": "floor", "facing": "south"})
        k.titik("kerja:tenun", x, spin["f"] + 1, sz0 + 2, "north", {"nama": "pintal"})
    k.put(sx1 - 1, spin["f"] + 1, sz1 - 1, "minecraft:loom", {"facing": "south"})
    # cloth gallery (east): a long gallery hall with dyed silk hanging in bays, banners in the court
    gal = K.aula(k, cx + 20, cz - 40, cx + 37, cz - 10, h=5, podium_h=1, teras=1, roof="hip", door_sides=("west",),
                 door_w=3, tile=True, along="z", top="pearl")
    gx0, gz0, gx1, gz1 = gal["inner"]
    colours = ("red", "orange", "yellow", "lime", "light_blue", "blue", "purple", "magenta", "pink", "white", "cyan", "green")
    for i, z in enumerate(range(gz0 + 1, gz1, 2)):
        K.wall_banner(k, gx1, gal["f"] + 3, z, "west", colours[i % len(colours)])
        k.put(gx1 - 1, gal["f"] + 1, z, "minecraft:%s_wool" % colours[(i + 3) % len(colours)])
        k.put(gx1 - 1, gal["f"] + 2, z, "minecraft:%s_carpet" % colours[(i + 5) % len(colours)])
    for i, z in enumerate(range(cz - 36, cz - 12, 4)):
        K.banner_pole(k, cx + 18, LY + 1, z, height=3, color=colours[(i * 5) % len(colours)], rotation=4)
    k.titik("pandang", cx + 16, LY + 1, cz - 24, "east", {"nama": "galeri_kain"})
    k.titik("obrol", cx + 14, LY + 1, cz - 14, "east")
    k.titik("obrol", cx - 14, LY + 1, cz - 14, "west")
    # Perahu Pos stop on the south border canal (city z = -48, south of this district)
    x, z = P(-8, 47)
    tambatan(k, x, z, "pos", 90, halte="rumah_sutra")
    k.titik("obrol", x, LY + 1, z - 4, "south", {"nama": "halte"})
    # mulberry orchard (south-west lot)
    for u in range(-40, -6, 5):
        for v in range(8, 42, 5):
            if abs(v - 24) <= 2 and u < -30:
                continue
            if abs(u + 24) <= 3 and v > 34:
                continue
            x, z = P(u, v)
            k.put(x, LY, z, "minecraft:rooted_dirt")
            murbei(k, x, z)
    for (u, v) in ((-20, 12), (-12, 30), (-34, 36)):
        x, z = P(u, v)
        k.titik("kerja:sabit", x, LY + 1, z, "north", {"nama": "petik_murbei"})
    shed = K.aula(k, cx - 14, cz + 36, cx - 6, cz + 42, h=3, podium_h=0, teras=0, roof="hip", door_sides=("north",),
                  door_w=3, tile=True, top=None)
    K.crates(k, shed["inner"][0], shed["inner"][1] + 1, shed["inner"][2], shed["inner"][3], shed["f"] + 1, h=1)
    # silk workers' houses (south-east lot)
    rumah(k, cx + 8, cz + 6, 9, 8, "south", 101, tingkat=2, kamar=3)
    rumah(k, cx + 22, cz + 6, 9, 8, "south", 102, tingkat=2, kamar=3)
    rumah(k, cx + 8, cz + 30, 9, 8, "north", 103, tingkat=2, kamar=3)
    rumah(k, cx + 22, cz + 30, 9, 8, "north", 104, tingkat=2, kamar=3)
    K.well(k, cx + 20, cz + 23)
    k.titik("kerja:sapu", cx + 20, LY + 1, cz + 21, "south", {"nama": "sumur"})
    for (u, v, h) in ((16, 22, "east"), (24, 24, "west"), (12, 20, "south")):
        x, z = P(u, v)
        k.titik("obrol" if u != 12 else "main", x, LY + 1, z, h)
    for (u, v) in ((6, 1), (30, 1), (-30, 1), (-6, 1), (6, 46 - 5), (-38, 20)):
        x, z = P(u, v)
        K.post(k, x, LY + 1, z, height=3, post_role="wood_fence", role="lamp") if k.get(x, LY + 1, z) in (None, AIR) else None
    k.jalur([P(-40, 0), P(0, 0), P(40, 0)], jalur="jalan")
    k.jalur([P(0, -6), P(0, -14), P(0, -16)], jalur="sutra")
    titik_rute(k)
    zona(k, cx + 2, cz + 2, d)
    selesai_distrik(k, d, {(0, 0): "ulat_sutra", (1, 0): "galeri_kain", (0, 1): "kebun_murbei", (1, 1): "rumah_pekerja"},
                    kode_dalam={"h0": "B"}, utuh=("ulat_sutra", "galeri_kain"))


def build_bukit_teh():
    """Bukit Teh (38.T.5.2): four tea terraces, the drying hut, the Paviliun Teh Awan over the canal, the tea store."""
    d = "bukit_teh"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    # terraces rising to the north (4 tiers, 2 blocks each), retaining walls of mossy stone, tea rows on rooted dirt
    tiers = [(-16, -8, 0), (-24, -17, 2), (-32, -25, 4), (-42, -33, 6)]
    for (v0, v1, dy) in tiers:
        for u in range(-34, 41):
            for v in range(v0, v1 + 1):
                x, z = P(u, v)
                for y in range(LY, LY + dy):
                    k.put(x, y, z, "kerb_alt" if (u + v + y) % 4 == 0 else "kerb")
                top = LY + dy
                path = abs(u - 3) <= 1 or u in (-34, 40)
                if path:
                    k.put(x, top, z, "paving")
                elif (v - v0) % 2 == 0:
                    k.put(x, top, z, "minecraft:rooted_dirt")
                    k.put(x, top + 1, z, "minecraft:flowering_azalea" if (u * 3 + v) % 5 == 0 else "minecraft:azalea")
                else:
                    k.put(x, top, z, "minecraft:coarse_dirt")
        # stone face of the terrace front
        for u in range(-34, 41):
            x, z = P(u, v1 + 1)
            if dy > 0:
                for y in range(LY + 1, LY + dy + 1):
                    k.put(x, y, z, "kerb_alt" if (u + y) % 3 == 0 else "kerb")
    # central stone stair up the terraces and side steps
    for i, (v0, v1, dy) in enumerate(tiers[1:], 1):
        for u in (2, 3, 4):
            x, z = P(u, v1 + 1)
            k.stairs(x, LY + dy - 1, z, "kerb_stairs", "north")
            k.put(x, LY + dy - 2, z, "kerb") if dy >= 2 else None
            x, z = P(u, v1 + 2)
            k.put(x, LY + dy - 1, z, "kerb")
    for (v0, v1, dy) in tiers:
        for u in (-34, 40):
            x, z = P(u, v1 + 1)
            if dy:
                k.stairs(x, LY + dy, z, "kerb_stairs", "north")
    for (u, v, dy) in ((-20, -12, 0), (14, -12, 0), (-10, -20, 2), (22, -21, 2), (-26, -29, 4), (12, -28, 4), (-8, -37, 6)):
        x, z = P(u, v)
        k.titik("kerja:sabit", x, LY + dy + 1, z, "north", {"nama": "petik_teh"})
    # drying hut on the top tier: bamboo racks, roasting smokers, a tile roof on timber posts
    hx0, hz0, hx1, hz1 = cx + 20, cz - 41, cx + 36, cz - 34
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            k.put(x, LY + 6, z, "kerb")
            edge = x in (hx0, hx1) or z in (hz0, hz1)
            for y in range(LY + 7, LY + 11):
                k.put(x, y, z, ("red" if (x - hx0) % 4 == 0 or z in (hz0, hz1) and x in (hx0, hx1) else AIR) if edge else AIR)
    for x in range(hx0 + 2, hx1 - 1, 3):
        for y in (LY + 7, LY + 9):
            for z in range(hz0 + 1, hz0 + 4):
                k.put(x, y, z, "minecraft:bamboo_mosaic_slab", {"type": "top", "waterlogged": "false"})
                k.put(x, y + 1, z, "minecraft:green_carpet" if (x + z) % 2 else "minecraft:moss_carpet")
    for i, x in enumerate((hx0 + 3, hx1 - 3)):
        k.put(x, LY + 7, hz1 - 1, "minecraft:smoker", {"facing": "south", "lit": "false"})
        k.titik("kerja:kuali", x, LY + 7, hz1, "north", {"nama": "sangrai"})
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            k.put(x, LY + 11, z, "wood")
    K.hip(k, hx0, LY + 12, hz0, hx1, hz1, overhang=1, top=None, role="tile_stairs", slab_role="tile_slab", full_role="tile")
    # a small tea-god shrine and an old pine on the top tier
    x, z = P(-28, -39)
    K.incense_burner(k, x, LY + 7, z)
    k.titik("dupa", x, LY + 7, z + 2, "north")
    K.pine(k, cx - 32, LY + 7, cz - 40, 9)
    # Paviliun Teh Awan (south-west lot): two storeys, a balcony over the west canal towpath
    t0x, t0z, t1x, t1z = cx - 40, cz + 4, cx - 17, cz + 18
    house = K.aula(k, t0x, t0z, t1x, t1z, h=5, podium_h=1, teras=1, roof="hip", tiers=2, door_sides=("east", "south"),
                   door_w=3, tile=True, top="pearl")
    ix0, iz0, ix1, iz1 = house["inner"]
    fl = house["f"]
    ruang(k, ix0, iz0, ix1, iz1, fl, "teh", isi=0.5)
    seats = 0
    for x in range(ix0 + 2, ix1 - 1, 4):
        for z in (iz0 + 2, iz1 - 2):
            K.low_table(k, x, fl + 1, z)
            k.put(x - 1, fl + 1, z, "minecraft:dark_oak_stairs", {"facing": "east", "half": "bottom", "shape": "straight", "waterlogged": "false"})
            k.put(x + 1, fl + 1, z, "minecraft:dark_oak_stairs", {"facing": "west", "half": "bottom", "shape": "straight", "waterlogged": "false"})
            k.put(x, fl + 2, z, "minecraft:flower_pot")
            if seats < 12:
                k.titik("kursi", x - 1, fl + 1, z, "east", {"nama": "meja_teh"})
                k.titik("kursi", x + 1, fl + 1, z, "west", {"nama": "meja_teh"})
                seats += 2
    K.counter_z(k, ix1 - 1, iz0 + 2, iz1 - 2, fl, "west")
    k.put(ix1 - 1, fl + 2, iz0 + 3, "minecraft:decorated_pot")
    k.put(ix1 - 1, fl + 2, iz1 - 3, "minecraft:decorated_pot")
    k.titik("kerja:teko", ix1 - 2, fl + 1, (iz0 + iz1) // 2, "west")
    k.titik("kerja:teko", ix1 - 2, fl + 1, (iz0 + iz1) // 2 + 2, "west")
    for x in range(t0x - 2, t0x):
        for z in range(t0z + 2, t1z - 1):
            k.slab(x, fl + 4, z, "wood_slab", "top")
            k.put(x, fl + 5, z, "wood_fence") if x == t0x - 2 else None
    k.titik("pandang", t0x - 1, fl + 5, (t0z + t1z) // 2, "west", {"nama": "balkon_kanal"})
    k.jangkar_air(t1x + 1.5, LY + 1, (t0z + t1z) / 2.0, "kota_tema/teh_awan", yaw=0.0, layanan="teh", tokoh="lu_yuqing")
    k.jangkar_air(t1x + 1.5, LY + 1, (t0z + t1z) / 2.0 + 3, "kota/teh", yaw=0.0, nama="teh_awan")
    # tea store and pickers' houses (south-east lot)
    store = K.aula(k, cx + 8, cz + 6, cx + 26, cz + 18, h=5, podium_h=1, teras=0, roof="hip", door_sides=("west", "south"),
                   door_w=3, tile=True, top=None)
    K.crates(k, store["inner"][0] + 1, store["inner"][1] + 1, store["inner"][2] - 1, store["inner"][1] + 3, store["f"] + 1, h=2)
    ruang(k, *store["inner"], store["f"], "teh", isi=0.45, rug=False)
    for x in range(store["inner"][0] + 1, store["inner"][2], 2):
        k.put(x, store["f"] + 1, store["inner"][3] - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    k.titik("kerja:rak", store["inner"][0] + 3, store["f"] + 1, store["inner"][3] - 2, "south")
    rumah(k, cx + 8, cz + 26, 8, 7, "south", 201, tingkat=2, kamar=2)
    rumah(k, cx + 20, cz + 26, 8, 7, "south", 202, tingkat=1, kamar=1)
    rumah(k, cx + 31, cz + 6, 7, 8, "west", 203, tingkat=2, kamar=2)
    for (u, v) in ((-3, 30), (12, 24), (30, 30)):
        x, z = P(u, v)
        k.titik("main" if u == 12 else "obrol", x, LY + 1, z, "south")
    for (u, v) in ((-6, 1), (6, 1), (30, 1), (-30, 1)):
        x, z = P(u, v)
        K.post(k, x, LY + 1, z, height=3, post_role="wood_fence", role="lamp")
    k.jalur([P(-40, 0), P(0, 0), P(36, 0)], jalur="jalan")
    k.jalur([P(3, -6), P(3, -16), P(3, -25), P(3, -33)], jalur="teh", y=None)
    zona(k, cx + 2, cz - 4, d)
    selesai_distrik(k, d, {(0, 0): "teras_barat", (1, 0): "teras_timur", (0, 1): "paviliun_teh", (1, 1): "gudang_teh"},
                    kode_dalam={"h0": "B"}, utuh=("paviliun_teh",))


def toko_baris(k, xs, z0, w, d, depan, seed, barang):
    """A row of two-storey shop-houses along a canal street (38.T.5.2 jembatan_kembar): one shop per x in xs, footprint
    w along the street, d deep, the open front toward `depan` (south / north), a lapak point in each."""
    for i, x in enumerate(xs):
        rumah(k, x, z0, w, d, depan, seed + i, tingkat=2, toko=barang[i % len(barang)], kamar=2)


def toko_baris_z(k, x0, zs, w, d, depan, seed, barang):
    """The same along a north-south street (depan east / west): one shop per z in zs."""
    for i, z in enumerate(zs):
        rumah(k, x0, z, w, d, depan, seed + i, tingkat=2, toko=barang[i % len(barang)], kamar=2)


BARANG_TOKO = (
    ("minecraft:white_carpet", "minecraft:flower_pot"),                  # kipas (fans)
    ("minecraft:red_carpet", "minecraft:decorated_pot"),                  # payung kertas (paper umbrellas)
    ("minecraft:cake", "minecraft:flower_pot"),                           # kue beras (rice cakes)
    ("minecraft:mushroom_stem", "minecraft:barrel"),                      # tahu (tofu)
    ("minecraft:bamboo_mosaic_slab", "minecraft:decorated_pot"),          # keranjang bambu
    ("minecraft:pink_carpet", "minecraft:flower_pot"),                    # kain sutra
)


def jalan_lurus(k, x0, z0, x1, z1, f=LY, lampu=0):
    """A paved street rectangle (polished andesite with granite kerbs) and optional lamp posts every `lampu`."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            k.put(x, f, z, "kerb" if edge and (x + z) % 2 == 0 else ("paving" if (x * 3 + z) % 7 else "paving_alt"))
    if lampu:
        horiz = (x1 - x0) >= (z1 - z0)
        if horiz:
            for x in range(x0 + 2, x1 - 1, lampu):
                K.post(k, x, f + 1, z0, height=3, post_role="wood_fence", role="lamp") if k.get(x, f + 1, z0) in (None, AIR) else None
        else:
            for z in range(z0 + 2, z1 - 1, lampu):
                K.post(k, x0, f + 1, z, height=3, post_role="wood_fence", role="lamp") if k.get(x0, f + 1, z) in (None, AIR) else None


def pancing_jembatan(k, x, z, y, hadap):
    """A child's fishing spot on a bridge deck (38.T.5.3: anak memancing dari jembatan)."""
    k.titik("kerja:pancing", x, y + 1, z, hadap, {"nama": "jembatan"})


def build_jembatan_kembar():
    """Sudut Jembatan Kembar (38.T.5.2): two canals cross at the district centre; the round-arched Shide bridge and the
    square-lintel Yong'an bridge meet at the north-east corner of the crossing; twelve two-storey shops on the canal
    streets, the Wisma Zhang mansion of six small courtyards, the famous viewpoint."""
    d = "jembatan_kembar"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    # streets: the west-gate road (v = -24) across the whole district, canal streets one block behind the towpaths
    jalan_lurus(k, cx - 43, cz - 26, cx + 42, cz - 22, lampu=10)
    jalan_lurus(k, cx - 43, cz - 6, cx - 6, cz - 6)
    jalan_lurus(k, cx - 43, cz + 5, cx - 6, cz + 6)
    jalan_lurus(k, cx + 5, cz - 6, cx + 42, cz - 6)
    jalan_lurus(k, cx + 5, cz + 5, cx + 5, cz + 42)
    jalan_lurus(k, cx - 26, cz + 14, cx - 22, cz + 42, lampu=8)          # to the south border bridge u = -24
    jalan_lurus(k, cx + 37, cz + 5, cx + 42, cz + 42)                     # east lane to the border bridge v = +24
    # the twin bridges: Shide (round, high) over the north-south canal, Yong'an (square) over the east-west canal
    jembatan(k, "x", cx, cz - 14, lebar=7, tinggi=6, gaya="shide")
    jembatan(k, "z", cz, cx + 16, lebar=7, tinggi=5, gaya="persegi")
    # two plain arch bridges more: the gate road over the north-south canal, the south lane over the east-west canal
    jembatan(k, "x", cx, cz - 24, gaya="lengkung")
    jembatan(k, "z", cz, cx - 24, gaya="lengkung")
    jembatan(k, "x", cx, cz + 26, gaya="bundar")
    # the landing plaza of the twin bridges (north-east corner of the crossing), stone benches, a willow
    K.paving(k, cx + 5, cz - 20, cx + 20, cz - 7, LY, role="paving", alt="kerb", alt_chance=0.18)
    for (u, v, h) in ((8, -19, "south"), (19, -19, "south")):
        x, z = P(u, v)
        K.bench(k, x, z, h, n=3)
        k.titik("obrol", x + 1, LY + 1, z + 1, "south")
    K.willow(k, cx + 20, LY + 1, cz - 18)
    # bridge life: children fishing, people talking on the decks, the famous viewpoint (painting "Memory of Home")
    x, z = P(-1, -17)
    pancing_jembatan(k, x, z, LY + 6, "north")
    x, z = P(16, 2)
    pancing_jembatan(k, x + 3, z, LY + 5, "east")
    k.titik("obrol", cx - 2, LY + 7, cz - 13, "south")
    k.titik("obrol", cx + 2, LY + 7, cz - 13, "south")
    x, z = P(-7, 7)
    k.titik("pandang", x, LY + 1, z, "east", {"nama": "jembatan_kembar", "lukisan": "kenangan_kampung"})
    k.jangkar_air(x + 0.5, LY + 1, z + 0.5, "kota_tema/lukisan", yaw=225.0, kartu="kenangan_kampung")
    # twelve shops: four on each canal street, west row of the south-east bank
    toko_baris(k, [cx - 42, cx - 34, cx - 26, cx - 18], cz - 13, 6, 7, "south", 300, BARANG_TOKO)
    toko_baris(k, [cx - 42, cx - 34, cx - 26, cx - 18], cz + 7, 6, 7, "north", 310, BARANG_TOKO[2:] + BARANG_TOKO[:2])
    toko_baris(k, [cx + 22, cx + 29, cx + 36], cz - 13, 6, 7, "south", 320, BARANG_TOKO[4:] + BARANG_TOKO[:4])
    toko_baris_z(k, cx + 6, [cz - 43], 6, 7, "west", 330, (BARANG_TOKO[1],))
    # houses behind the shops (north of the gate road and the back of the south bank)
    rumah(k, cx - 42, cz - 41, 9, 8, "south", 340, tingkat=2, kamar=3)
    rumah(k, cx - 30, cz - 41, 9, 8, "south", 341, tingkat=2, kamar=2, belakang=True)
    rumah(k, cx - 18, cz - 41, 8, 8, "south", 342, tingkat=1, kamar=1)
    rumah(k, cx + 14, cz - 41, 7, 9, "west", 343, tingkat=2, kamar=2)
    rumah(k, cx + 28, cz - 41, 8, 8, "south", 344, tingkat=2, kamar=2)
    rumah(k, cx - 42, cz + 30, 9, 8, "north", 345, tingkat=2, kamar=3)
    rumah(k, cx - 18, cz + 30, 9, 8, "north", 346, tingkat=2, kamar=2)
    rumah(k, cx - 42, cz + 18, 9, 7, "north", 347, tingkat=1, kamar=1)
    K.well(k, cx - 14, cz + 21)
    k.titik("kerja:sapu", cx - 14, LY + 1, cz + 23, "north", {"nama": "sumur"})
    for (u, v, kind) in ((-30, 20, "willow"), (-6, 38, "pine"), (38, -30, "willow"), (-6, -36, "pine")):
        x, z = P(u, v)
        if k.get(x, LY + 1, z) in (None, AIR):
            K.tree(k, x, LY + 1, z, kind)
    # Wisma Zhang: six small courtyards on the south-east bank
    wisma_zhang(k, cx + 8, cz + 12, cx + 35, cz + 37)
    # washing steps on the canal banks and moored boats
    tangga_cuci(k, "z", cz - 3, cx - 30, 1)
    k.titik("kerja:sapu", cx - 30, LY + 1, cz - 5, "south", {"nama": "cuci"})
    tangga_cuci(k, "z", cz + 2, cx - 10, -1)
    k.titik("kerja:sapu", cx - 10, LY + 1, cz + 4, "north", {"nama": "cuci"})
    tangga_cuci(k, "x", cx - 3, cz + 36, 1)
    k.titik("kerja:sapu", cx - 5, LY + 1, cz + 36, "east", {"nama": "cuci"})
    for (u, v, yaw) in ((-38, 0, 90.0), (30, -1, 90.0), (0, 34, 0.0), (-1, -36, 0.0)):
        x, z = P(u, v)
        perahu_vanilla(k, x, z, yaw)
    # Perahu Pos stop on the east-west canal
    x, z = P(22, 0)
    tambatan(k, x, z, "pos", 90, halte="jembatan_kembar")
    k.titik("obrol", x, LY + 1, z - 5, "south", {"nama": "halte"})
    x, z = P(30, 0)
    tambatan(k, x, z, "tur", 90, urut=1, tempat="jembatan_kembar")
    for (u, v, h) in ((-38, -28, "south"), (-12, -20, "east"), (30, -20, "west"), (-30, 16, "north"), (24, 2, "south")):
        x, z = P(u, v)
        k.titik("obrol" if u != -12 else "main", x, LY + 1, z, h)
    k.jalur([P(-43, -24), P(-10, -24), P(9, -24), P(42, -24)], jalur="jalan")
    k.jalur([P(-24, 42), P(-24, 14), P(-24, 6), P(-6, 6)], jalur="jalan")
    titik_rute(k)
    zona(k, cx - 30, cz - 30, d)
    selesai_distrik(k, d, {(0, 0): "toko_utara", (1, 0): "jembatan_kembar", (0, 1): "toko_selatan", (1, 1): "wisma_zhang"},
                    kode_dalam={"h0": "K", "h1": "K", "v0": "K", "v1": "K"}, utuh=("jembatan_kembar", "wisma_zhang"))


def wisma_zhang(k, x0, z0, x1, z1):
    """Wisma Zhang (38.T.5.2): a noble house of six small courtyards behind a white wall: gate hall, guest hall on the
    axis, study and bedrooms at the back; each courtyard its own garden (pond, bamboo, rockery, plum, flowers)."""
    K.yard_wall(k, x0, z0, x1, z1, h=4, gate="north")
    K.paving(k, x0 + 1, z0 + 1, x1 - 1, z1 - 1, LY, role="paving", alt="kerb", alt_chance=0.10)
    mx = (x0 + x1) // 2
    # row A: gate hall on the axis, two front courtyards
    gate = K.aula(k, mx - 4, z0 + 1, mx + 4, z0 + 6, h=4, podium_h=1, teras=0, roof="hip", door_sides=("north", "south"),
                  door_w=3, tile=True, top="pearl")
    K.couplets(k, mx, LY + 3, z0, "north")
    lampion(k, mx - 2, LY + 5, z0 - 1)
    lampion(k, mx + 2, LY + 5, z0 - 1)
    k.titik("jaga", mx + 2, LY + 1, z0 - 1, "north", {"nama": "penjaga_wisma"})
    tembok_taman(k, "z", z0 + 1, z0 + 9, mx - 6, pintu=(z0 + 5,), jendela=3)
    tembok_taman(k, "z", z0 + 1, z0 + 9, mx + 6, pintu=(z0 + 5,), jendela=3)
    taman_tanah(k, x0 + 1, z0 + 1, mx - 7, z0 + 8)
    kolam(k, [(x0 + 4, z0 + 4, 2, 2)], teratai=0.3)
    taman_tanah(k, mx + 7, z0 + 1, x1 - 1, z0 + 8)
    bambu(k, mx + 8, z0 + 2, x1 - 2, z0 + 4, rapat=3)
    K.stone_lantern(k, mx + 9, LY + 1, z0 + 7)
    # cross wall between row A and row B
    tembok_taman(k, "x", x0 + 1, x1 - 1, z0 + 9, pintu=(mx,), bulan=(x0 + 5, x1 - 5), jendela=4)
    # row B: the guest hall on the axis with two side courtyards (rockery west, plum and flowers east)
    guest = K.aula(k, mx - 6, z0 + 10, mx + 6, z0 + 17, h=5, podium_h=1, teras=1, roof="hip", door_sides=("north", "south"),
                   door_w=3, tile=True, top="pearl")
    gx0, gz0, gx1, gz1 = guest["inner"]
    gf = guest["f"]
    K.table(k, mx, gf + 1, gz0 + 1, "minecraft:dark_oak_pressure_plate")
    K.chair(k, mx - 1, gf + 1, gz0 + 1, "south")
    K.chair(k, mx + 1, gf + 1, gz0 + 1, "south")
    k.titik("kursi", mx - 1, gf + 1, gz0 + 1, "south", {"nama": "tuan_rumah"})
    k.titik("kursi", mx + 1, gf + 1, gz0 + 1, "south", {"nama": "nyonya_rumah"})
    for x in (gx0 + 1, gx1 - 1):
        for z in (gz0 + 3, gz1 - 1):
            K.chair(k, x, gf + 1, z, "east" if x == gx0 + 1 else "west")
            k.titik("kursi", x, gf + 1, z, "east" if x == gx0 + 1 else "west", {"nama": "tamu"})
    K.rug(k, gx0 + 2, gz0 + 2, gx1 - 2, gz1 - 1, gf + 1, role="minecraft:red_carpet", border="minecraft:yellow_carpet")
    K.wall_banner(k, mx, gf + 4, gz1, "north", "red")
    tembok_taman(k, "z", z0 + 10, z0 + 18, mx - 8, pintu=(z0 + 14,), jendela=3)
    tembok_taman(k, "z", z0 + 10, z0 + 18, mx + 8, pintu=(z0 + 14,), jendela=3)
    taman_tanah(k, x0 + 1, z0 + 10, mx - 9, z0 + 17)
    gunungan(k, x0 + 4, z0 + 13, 2, 2, 4)
    k.titik("meditasi", x0 + 4, LY + 1, z0 + 16, "north")
    taman_tanah(k, mx + 9, z0 + 10, x1 - 1, z0 + 17)
    K.tree(k, mx + 11, LY + 1, z0 + 12, "cherry")
    K.flowerbed(k, mx + 9, z0 + 14, x1 - 2, z0 + 16, LY)
    tembok_taman(k, "x", x0 + 1, x1 - 1, z0 + 18, pintu=(mx,), bulan=(x0 + 5, x1 - 5), jendela=4)
    # row C: the study (west), bedrooms (east), a tiny well court between them
    study = K.aula(k, x0 + 1, z0 + 19, mx - 3, z1 - 1, h=4, podium_h=1, teras=0, roof="hip", door_sides=("north",),
                   door_w=3, tile=True, top=None)
    sx0, sz0, sx1, sz1 = study["inner"]
    K.bookshelf_wall(k, sx0, sz1, sx1, sz1, study["f"] + 1, study["f"] + 3, face="north")
    K.table(k, (sx0 + sx1) // 2, study["f"] + 1, sz0 + 1, "minecraft:lectern")
    k.titik("meja_tulis", (sx0 + sx1) // 2, study["f"] + 1, sz0 + 2, "north", {"nama": "ruang_belajar"})
    K.pot(k, sx0, study["f"] + 1, sz0)
    beds = K.aula(k, mx + 3, z0 + 19, x1 - 1, z1 - 1, h=4, podium_h=1, teras=0, roof="hip", door_sides=("north",),
                  door_w=3, tile=True, top=None)
    bx0, bz0, bx1, bz1 = beds["inner"]
    K.furnish_bedroom(k, bx0, bz0, bx1, bz1, beds["f"], beds=3, facing="south")
    taman_tanah(k, mx - 2, z0 + 19, mx + 2, z1 - 1, rumput=0.05)
    K.pot(k, mx, LY + 1, z1 - 2)
    k.titik("obrol", mx, LY + 1, z0 + 20, "south", {"nama": "wisma_zhang"})
    k.titik("main", mx - 1, LY + 1, z0 + 8, "south", {"nama": "wisma_zhang"})


def titian_panggung(k, x, z_utara, z_selatan, punuk=(None, None)):
    """The stone causeway from the north shore to the Panggung Air: 3 wide, deck at street level on piers, a hump of
    stairs (deck +3) over punuk = (z0, z1) so the boats of the lake and the dragon-boat race pass under it."""
    pz0, pz1 = punuk
    for z in range(z_utara, z_selatan + 1):
        hump = pz0 is not None and pz0 <= z <= pz1
        h = 0
        if hump:
            h = min(3, z - pz0 + 1, pz1 - z + 1)
        y = LY + h
        for xx in (x - 1, x, x + 1):
            if hump and h < 3 and z - pz0 < pz1 - z:
                k.stairs(xx, y, z, "kerb_stairs", "south")
            elif hump and h < 3:
                k.stairs(xx, y, z, "kerb_stairs", "north")
            else:
                k.put(xx, y, z, "kerb" if xx != x else ("kerb_chisel" if z % 4 == 0 else "paving"))
            if not hump and (z - z_utara) % 4 == 0:
                for yy in range(DASAR_Y, LY):
                    k.put(xx, yy, z, "kerb")
        for xx in (x - 2, x + 2):
            k.put(xx, y, z, "kerb")
            k.put(xx, y + 1, z, "kerb_wall" if (z - z_utara) % 4 else "kerb_chisel")
            if not hump and (z - z_utara) % 4 == 0:
                for yy in range(DASAR_Y, LY):
                    k.put(xx, yy, z, "kerb")
            if (z - z_utara) % 8 == 4 and not hump:
                k.put(xx, y + 2, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})


def dermaga_lentera(k, x, z, arah, n):
    """A short plank jetty into the lake with a lantern post, the release point n of the Malam Lentera Air."""
    dx, dz = K.FACE[arah]
    for i in range(1, 4):
        xx, zz = x + dx * i, z + dz * i
        k.put(xx, LY, zz, "wood")
        if i == 3:
            for y in range(DASAR_Y, LY):
                k.put(xx, y, zz, "red")
    xx, zz = x + dx * 3, z + dz * 3
    K.post(k, xx - dz, LY + 1, zz + dx, height=3, post_role="wood_fence", role="lamp")
    lentera_air(k, x + dx * 2, z + dz * 2, n)


def build_danau_lentera():
    """Danau Lentera (38.T.5.2): the 64 x 50 lantern lake with the Panggung Air on stilts, eight lantern jetties, a
    pagoda on its island, the Alun Tepi Danau (town stele, square, notice board), the small temple of the Water God."""
    d = "danau_lentera"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    # Alun Tepi Danau: the square on the north shore, the gateway arch, stele, notice board, trees
    K.paving(k, cx - 43, cz - 43, cx + 42, cz - 28, LY, role="paving", alt="kerb", alt_chance=0.15)
    for x in range(cx - 30, cx + 31):
        for z in (cz - 36,):
            k.put(x, LY, z, "kerb_chisel" if x % 6 == 0 else "kerb")
    K.paifang(k, cx, cz - 40, width=13, height=9, facing="south")
    K.huabiao(k, cx - 9, LY + 1, cz - 33, height=7)
    K.huabiao(k, cx + 9, LY + 1, cz - 33, height=7)
    K.lion(k, cx - 4, LY + 1, cz - 39, "south")
    K.lion(k, cx + 4, LY + 1, cz - 39, "south")
    K.notice_board(k, cx - 16, cz - 31, "south", w=5)
    k.jangkar_air(cx - 13.5, LY + 1, cz - 29.5, "kota/papan", yaw=0.0)
    k.jangkar_air(cx + 0.5, LY + 2, cz - 34.5, "kota/situs", yaw=0.0, situs="kota/kota_tema", tier=3, protect=100)
    k.jangkar_air(cx + 0.5, LY + 1, cz - 30.5, "kota/alun", yaw=0.0, protect=64)
    k.jangkar_air(cx + 4.5, LY + 1, cz - 30.5, "kota_tema/situs", yaw=0.0, protect=96)
    k.jangkar_air(cx - 4.5, LY + 1, cz - 42.5, "kota_tema/tiba", yaw=180.0)
    for x in (cx - 1, cx, cx + 1):
        for y in range(LY + 1, LY + 4):
            k.put(x, y, cz - 35, "minecraft:polished_diorite" if y < LY + 3 else "minecraft:chiseled_quartz_block")
    K.incense_burner(k, cx, LY + 1, cz - 32, big=True)
    for (u, v) in ((-36, -40), (-28, -32), (28, -32), (36, -40), (-36, -30), (36, -30)):
        x, z = P(u, v)
        K.tree(k, x, LY + 1, z, "willow" if v > -35 else "pine")
    for (u, v, h) in ((-20, -38, "south"), (14, -38, "south"), (-10, -29, "south"), (10, -29, "south")):
        x, z = P(u, v)
        K.bench(k, x, z, h, n=3)
        k.titik("penonton", x + 1, LY + 1, z, "south", {"nama": "panggung_air"})
    for (u, v, h) in ((-24, -34, "east"), (24, -34, "west"), (-6, -38, "south"), (6, -38, "south")):
        x, z = P(u, v)
        k.titik("obrol", x, LY + 1, z, h)
    k.titik("main", cx - 18, LY + 1, cz - 30, "south")
    k.titik("main", cx + 18, LY + 1, cz - 30, "south")
    # the causeway to the stage, with a hump the boats pass under
    titian_panggung(k, cx, cz - 27, cz - 6, punuk=(cz - 19, cz - 13))
    # the Panggung Air: stage on red stilts, gable roof over the back half, open front, drums
    sx0, sz0, sx1, sz1 = cx - 7, cz - 5, cx + 7, cz + 6
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            edge = x in (sx0, sx1) or z in (sz0, sz1)
            k.put(x, LY, z, "kerb" if edge else ("wood" if (x + z) % 5 else "minecraft:stripped_dark_oak_wood"))
            if edge and ((x - sx0) % 3 == 0 or (z - sz0) % 3 == 0):
                for y in range(DASAR_Y, LY):
                    k.put(x, y, z, "red")
            for y in range(LY + 1, LY + 6):
                k.put(x, y, z, AIR)
            if edge and not (z == sz0 and abs(x - cx) <= 1):
                k.put(x, LY + 1, z, "rail")
    for x in range(sx0 + 1, sx1):
        k.put(x, LY + 1, sz0, AIR) if abs(x - cx) <= 1 else None
    for (px, pz) in ((sx0, sz0), (sx1, sz0), (sx0, cz + 1), (sx1, cz + 1), (sx0, sz1), (sx1, sz1), (cx - 3, sz1), (cx + 3, sz1)):
        for y in range(LY + 1, LY + 7):
            k.put(px, y, pz, "red")
    # back screen (painted wall) and the roof over the back half
    for x in range(sx0 + 1, sx1):
        for y in range(LY + 1, LY + 6):
            k.put(x, y, sz1, "plaster" if (x + y) % 5 else "minecraft:light_blue_terracotta")
    K.beam_band(k, sx0, cz + 1, sx1, sz1, LY + 7)
    atap_pelana(k, sx0, LY + 8, cz + 1, sx1, sz1, overhang=1, kuda=False)
    K.drum(k, sx0 + 1, LY + 1, sz1 - 1, "north")
    K.drum(k, sx1 - 1, LY + 1, sz1 - 1, "north")
    for x in (cx - 4, cx, cx + 4):
        K.lit(k, x, LY + 7, cz + 2, chain=1, role="lamp")
    k.kotak("panggung", sx0 + 1, LY + 1, sz0 + 1, sx1 - 1, LY + 3, sz1 - 1, "north", nama="panggung_air")
    k.jangkar_air(cx + 0.5, LY + 1, cz + 0.5, "kota_tema/panggung_air", yaw=180.0)
    # the pagoda island (east lobe) and its plank bridge from the east shore
    ix, iz = cx + 20, cz - 10
    for x in range(ix - 6, ix + 7):
        for z in range(iz - 6, iz + 7):
            if (x - ix) ** 2 + (z - iz) ** 2 <= 40:
                k.put(x, 0, z, "found")
                for y in range(1, LY):
                    k.put(x, y, z, "kerb" if (x - ix) ** 2 + (z - iz) ** 2 > 28 else "found")
                k.put(x, LY, z, "minecraft:grass_block" if (x - ix) ** 2 + (z - iz) ** 2 <= 28 else "kerb", {"snowy": "false"}
                      if (x - ix) ** 2 + (z - iz) ** 2 <= 28 else None)
                k.put(x, LY + 1, z, AIR)
    K.pagoda(k, ix - 3, iz - 3, 7, 3, sh=4, base_h=1, door="south", titik_top="pandang")
    for x in range(ix + 6, cx + 33):
        k.put(x, LY, iz, "wood")
        k.put(x, LY + 1, iz - 1, "wood_fence")
        k.put(x, LY + 1, iz + 1, "wood_fence")
        if x % 3 == 0:
            for y in range(DASAR_Y, LY):
                k.put(x, y, iz, "red")
    K.tree(k, ix - 4, LY + 1, iz + 3, "pine")
    k.titik("meditasi", ix, LY + 1, iz + 5, "south")
    # eight lantern jetties around the lake (release points of the Malam Lentera Air)
    for n, (u, v, arah) in enumerate(((-18, -27, "south"), (16, -27, "south"), (34, 6, "west"), (34, 18, "west"),
                                      (14, 26, "north"), (-16, 26, "north"), (-34, 14, "east"), (-34, -12, "east"))):
        x, z = P(u, v)
        dermaga_lentera(k, x, z, arah, n)
    # the small temple of the Water God on the west shore (faces the lake)
    kuil = K.aula(k, cx - 44, cz - 7, cx - 36, cz + 7, h=4, podium_h=1, teras=1, roof="hip", door_sides=("east",),
                  door_w=3, tile=True, along="z", top="pearl")
    kx0, kz0, kx1, kz1 = kuil["inner"]
    K.candle(k, kx0, kuil["f"] + 1, (kz0 + kz1) // 2, n=4, color="blue")
    k.put(kx0, kuil["f"] + 2, (kz0 + kz1) // 2, "minecraft:conduit", {"waterlogged": "false"})
    K.incense_burner(k, cx - 35, LY + 1, cz)
    k.titik("dupa", cx - 34, LY + 1, cz + 1, "west", {"nama": "dewa_air"})
    k.titik("dupa", cx - 34, LY + 1, cz - 1, "west", {"nama": "dewa_air"})
    k.jangkar_air(cx - 39.5, LY + 2, cz + 0.5, "kota_tema/kuil_air", yaw=90.0)
    # south shore: lantern makers' stalls (west of the channel), the lake houses (east)
    for i, u in enumerate((-40, -32, -16, -10)):
        x, z = P(u, 32)
        sx, sz = K.stall(k, x, z, "north", goods=("minecraft:pink_candle", "minecraft:white_carpet", "minecraft:lantern"))
        k.titik("lapak", sx, LY + 1, sz, "north", {"nama": "lentera" if i < 2 else "kue"})
    rumah(k, cx - 34, cz + 36, 9, 6, "north", 401, tingkat=1, toko=("minecraft:lantern", "minecraft:pink_candle"), kamar=1)
    rumah(k, cx + 8, cz + 31, 9, 8, "north", 402, tingkat=2, kamar=3)
    rumah(k, cx + 30, cz + 31, 9, 8, "north", 403, tingkat=2, kamar=2)
    jembatan(k, "x", cx, cz + 36, gaya="lengkung")
    for (u, v) in ((-24, 29), (24, 29), (-40, 28), (40, 28)):
        x, z = P(u, v)
        if k.get(x, LY + 1, z) in (None, AIR):
            K.tree(k, x, LY + 1, z, "willow")
    for (u, v, h) in ((-6, 29, "north"), (6, 29, "north"), (-38, 20, "east"), (38, -20, "west")):
        x, z = P(u, v)
        k.titik("penonton", x, LY + 1, z, h, {"nama": "danau"})
    # the Perahu Pos stop in the south channel, a tour stop at the stage
    x, z = P(0, 32)
    tambatan(k, x, z, "pos", 0, halte="danau")
    k.titik("obrol", x - 5, LY + 1, z, "east", {"nama": "halte"})
    x, z = P(-10, 0)
    tambatan(k, x, z, "tur", 90, urut=3, tempat="panggung_air")
    for (u, v) in ((-26, -18), (26, 14), (-12, 18), (12, -18)):
        x, z = P(u, v)
        tambatan(k, x, z, "naga", 0, lintasan="danau")
    k.jalur([P(-24, -42), P(-24, -30), P(0, -30), P(24, -30), P(24, -42)], jalur="jalan")
    k.jalur([P(0, -30), P(0, -6)], jalur="panggung")
    titik_rute(k)
    zona(k, cx - 6, cz - 34, d)
    selesai_distrik(k, d, {(0, 0): "alun_barat", (1, 0): "alun_timur", (0, 1): "danau_barat", (1, 1): "danau_timur"},
                    kode_dalam={"h0": "B", "h1": "B", "v0": "B", "v1": "B"}, utuh=("alun_barat", "alun_timur"))


def build_pasar_kanal():
    """Pasar Kanal (38.T.5.2): the Pasar Air Pagi on the 9-wide market canal (ten moored stall boats, washing steps,
    canal-side shop rows), the Cabang Rumah Uang with the guild board and the pill shop, the Rumah Makan Ikan Danau with
    windows on the ring canal, the east land gate road."""
    d = "pasar_kanal"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    W0, W1 = cz - 20, cz - 12          # market canal water rows (city z)
    # two high arch bridges over the market canal
    for u in (-30, 20):
        jembatan(k, "z", cz - 16, cx + u, lebar=5, tinggi=5, gaya="bundar", air=(W0, W1))
    # inner street of the north strip and the street along the south bank, the east gate road (v = 24)
    jalan_lurus(k, cx - 43, cz - 36, cx + 42, cz - 33, lampu=12)
    jalan_lurus(k, cx - 43, cz - 9, cx + 42, cz - 8, lampu=9)
    jalan_lurus(k, cx - 43, cz + 22, cx + 42, cz + 26, lampu=10)
    jalan_lurus(k, cx - 2, cz - 7, cx + 2, cz + 21)
    # north bank: shop-houses with their open fronts on the market towpath (v -23)
    xs = [x for x in range(cx - 43, cx + 40, 7) if not (cx - 34 <= x + 5 and x <= cx - 26) and not (cx + 16 <= x + 5 and x <= cx + 24)]
    toko_baris(k, xs, cz - 29, 6, 6, "south", 500, BARANG_TOKO[2:] + BARANG_TOKO[:2])
    # houses north of the inner street, clear of the border bridges (u = +-24)
    for i, u in enumerate((-42, -14, 4, 32)):
        rumah(k, cx + u, cz - 43, 8, 6, "south", 510 + i, tingkat=2, kamar=2)
    # the stall boats: ten moorings along both banks, a seller spot on the bank beside each
    n = 0
    for u in range(-40, 44, 9):
        bank_n = n % 2 == 0
        x, z = P(u, -19 if bank_n else -13)
        if abs(u + 30) <= 3 or abs(u - 20) <= 3:
            x += 4
        tambatan(k, x, z, "lapak", 90, urut=n, barang=("sayur", "ikan", "teh", "kue", "sutra", "bunga", "tahu", "jamu", "garam", "lentera")[n % 10])
        k.titik("lapak", x, LY + 1, z - 2 if bank_n else z + 2, "south" if bank_n else "north", {"nama": "perahu_lapak", "urut": n})
        n += 1
        if n >= 10:
            break
    # washing steps (six) into the market canal, three on each bank
    for i, u in enumerate((-38, -6, 34)):
        tangga_cuci(k, "z", W0, cx + u, 1)
        k.titik("kerja:sapu", cx + u, LY + 1, W0 - 2, "south", {"nama": "cuci"})
        tangga_cuci(k, "z", W1, cx + u + 4, -1)
        k.titik("kerja:sapu", cx + u + 4, LY + 1, W1 + 2, "north", {"nama": "cuci"})
    # south bank: open stalls and awnings for the morning market
    for i, u in enumerate((-40, -22, -12, 6, 30, 38)):
        x, z = P(u, -6)
        if k.get(x, LY + 1, z) in (None, AIR):
            sx, sz = K.stall(k, x, z, "north", goods=("minecraft:melon", "minecraft:pumpkin", "minecraft:hay_block",
                                                     "minecraft:dried_kelp_block", "minecraft:decorated_pot"))
            k.titik("lapak", sx, LY + 1, sz, "north", {"nama": "pasar_pagi"})
    # Cabang Rumah Uang (bank branch) and the Papan Gilda (south-west lot)
    bank = K.aula(k, cx - 40, cz + 2, cx - 20, cz + 17, h=5, podium_h=2, teras=1, roof="hip", tiers=1,
                  door_sides=("south",), door_w=3, tile=True, top="pearl")
    bx0, bz0, bx1, bz1 = bank["inner"]
    K.counter(k, bx0 + 1, bx1 - 1, bz0 + 4, bank["f"], "south")
    for x in range(bx0 + 1, bx1, 3):
        K.chest(k, x, bank["f"] + 1, bz0, None, "south")
        k.put(x + 1, bank["f"] + 1, bz0, "minecraft:gold_block") if x + 1 < bx1 else None
    K.rug(k, bx0 + 1, bz0 + 6, bx1 - 1, bz1 - 1, bank["f"] + 1, role="minecraft:red_carpet", border="minecraft:yellow_carpet")
    k.jangkar_air((bx0 + bx1) / 2.0 + 0.5, bank["f"] + 1, bz0 + 2.5, "kotabesar/layanan", yaw=0.0, layanan="bank_cabang", peran="BANKIR")
    k.titik("jaga", bx0 - 1, LY + 1, bz1 + 3, "south", {"nama": "bank"})
    guild = paviliun(k, cx - 16, cz + 4, cx - 6, cz + 12, tinggi=4, buka=("south", "east"), puncak="pearl")
    K.notice_board(k, cx - 8, cz + 7, "south", w=5)
    k.jangkar_air(cx - 10.5, LY + 1, cz + 9.5, "kotabesar/layanan", yaw=0.0, layanan="papan_gilda", peran="PETUGAS_GILDA")
    # pill shop beside the bank
    pil = rumah(k, cx - 40, cz + 30, 9, 8, "north", 520, tingkat=2, toko=("minecraft:brewing_stand", "minecraft:flower_pot"), kamar=1)
    k.jangkar_air(cx - 35.5, LY + 1, cz + 33.5, "kota/pil", yaw=180.0)
    rumah(k, cx - 26, cz + 30, 9, 8, "north", 521, tingkat=2, kamar=3)
    rumah(k, cx - 14, cz + 30, 9, 8, "north", 522, tingkat=2, kamar=2)
    # Rumah Makan Ikan Danau (south-east lot): two storeys, dining hall with windows on the ring canal, kitchen, terrace
    rm = K.aula(k, cx + 14, cz + 1, cx + 42, cz + 18, h=5, podium_h=1, teras=1, roof="hip", tiers=2,
                door_sides=("west", "south"), door_w=3, tile=True, top="pearl")
    rx0, rz0, rx1, rz1 = rm["inner"]
    rf = rm["f"]
    for z in range(rz0 + 2, rz1, 2):
        K.lattice(k, rx1 + 1, rf + 2, z, "east")
        K.lattice(k, rx1 + 1, rf + 3, z, "east")
    for (tx, tz) in ((rx0 + 4, rz0 + 3), (rx0 + 4, rz1 - 3), (rx0 + 10, rz0 + 3), (rx0 + 10, rz1 - 3), (rx1 - 6, (rz0 + rz1) // 2)):
        K.furnish_dining(k, tx, tz, rf, n=4)
    K.furnish_kitchen(k, rx1 - 4, rz0, rx1 - 1, rf, rz0, "south")
    k.put(rx1 - 2, rf + 1, rz0 + 2, "minecraft:water_cauldron", {"level": "3"})
    k.jangkar_air(rx1 - 2.5, rf + 1, rz0 + 3.5, "kotabesar/layanan", yaw=180.0, layanan="rumah_makan", peran="KOKI", nama="ikan_danau")
    for x in range(cx + 15, cx + 42):
        k.slab(x, LY + 1, cz + 20, "wood_slab", "bottom") if (x - cx) % 4 else K.post(k, x, LY + 1, cz + 20, height=2, post_role="wood_fence", role="lamp")
    rumah(k, cx + 4, cz + 30, 9, 8, "north", 523, tingkat=2, kamar=2)
    rumah(k, cx + 16, cz + 30, 9, 8, "north", 524, tingkat=2, kamar=3)
    rumah(k, cx + 30, cz + 31, 8, 7, "north", 525, tingkat=1, kamar=1)
    for (u, v, h) in ((-2, -30, "south"), (12, -6, "north"), (-30, 20, "north"), (10, 20, "north"), (-4, 14, "west")):
        x, z = P(u, v)
        k.titik("obrol" if v != 14 else "main", x, LY + 1, z, h)
    for (u, v, kind) in ((-6, 18, "willow"), (40, -40, "pine"), (-20, -40, "willow")):
        x, z = P(u, v)
        if k.get(x, LY + 1, z) in (None, AIR):
            K.tree(k, x, LY + 1, z, kind)
    k.jalur([P(-43, 24), P(0, 24), P(42, 24)], jalur="jalan")
    k.jalur([P(-43, -8), P(42, -8)], jalur="pasar")
    titik_rute(k)
    zona(k, cx + 2, cz + 14, d)
    selesai_distrik(k, d, {(0, 0): "pasar_air_barat", (1, 0): "pasar_air_timur", (0, 1): "rumah_uang", (1, 1): "rumah_makan"},
                    kode_dalam={"h0": "B", "h1": "B", "v0": "B", "v1": "B"}, utuh=("rumah_uang",))


def rumah_kanal(k, x0, z0, w, d, depan, seed, tingkat=2, kamar=2, toko=None, perahu=True):
    """A canal house (38.T.5.2 hunian_kanal): street door in front, a back door with stone steps down to the water
    behind (the canal must lie right behind the back wall), a small boat moored at the steps."""
    rumah(k, x0, z0, w, d, depan, seed, tingkat=tingkat, kamar=kamar, toko=toko, belakang=True)
    if depan in ("south", "north"):
        mx = x0 + w // 2
        if depan == "south":
            zb = z0 - 1                 # back eave row, then the 2-block towpath, then the water
            t_air = zb - 3
            tangga_cuci(k, "z", t_air, mx, -1)
        else:
            zb = z0 + d
            t_air = zb + 3
            tangga_cuci(k, "z", t_air, mx, 1)
        k.titik("kerja:sapu", mx, LY + 1, zb, depan, {"nama": "cuci"})
        if perahu and air_di(mx + 2, t_air):
            perahu_vanilla(k, mx + 2, t_air, 90.0)


def hunian_baris(k, z0, depan, segmen, varian, seed, d=8):
    """One row of canal houses over x segments [(xa, xb)]; varian changes widths, storeys and shop fronts."""
    rng = __import__("random").Random(seed * 31 + varian)
    for (xa, xb) in segmen:
        x = xa
        while x <= xb - 6:
            w = rng.choice((7, 8, 9)) if varian else 8
            if x + w - 1 > xb:
                w = xb - x + 1
            if w < 6:
                break
            toko = None
            if varian == 2 and rng.random() < 0.35:
                toko = BARANG_TOKO[rng.randrange(len(BARANG_TOKO))]
            rumah_kanal(k, x, z0, w, d, depan, seed + x, tingkat=1 if (varian == 1 and rng.random() < 0.3) else 2,
                        kamar=rng.choice((2, 3)), toko=toko)
            x += w + (1 if varian != 1 else 2)


def kuil_leluhur(k, x0, z0, x1, z1, depan="south"):
    """A small ancestral shrine (38.T.5.2): a walled court, an incense burner, the shrine hall with tablets."""
    hall = K.aula(k, x0, z0, x1, z1, h=4, podium_h=1, teras=0, roof="hip", door_sides=(depan,), door_w=3, tile=True, top="pearl")
    ix0, iz0, ix1, iz1 = hall["inner"]
    back = iz0 if depan == "south" else iz1
    for x in range(ix0 + 1, ix1):
        k.put(x, hall["f"] + 1, back, "wood")
        k.put(x, hall["f"] + 2, back, "minecraft:dark_oak_trapdoor" if x % 2 else "minecraft:candle",
              {"facing": depan, "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"} if x % 2 else
              {"candles": "3", "lit": "false", "waterlogged": "false"})
    mx = (x0 + x1) // 2
    front = z1 + 2 if depan == "south" else z0 - 2
    K.incense_burner(k, mx, LY + 1, front)
    k.titik("dupa", mx, LY + 1, front + (1 if depan == "south" else -1), K.OPP[depan], {"nama": "leluhur"})


def bengkel_tukang(k, x0, z0, x1, z1):
    """The craftsman's workshop of the canal quarter (anchor kotabesar/tukang): forge corner (no fire: blast furnace,
    anvil), carpenter's bench, grindstone, tool racks, a yard with timber."""
    hall = K.aula(k, x0, z0, x1, z1, h=4, podium_h=0, teras=0, roof="hip", door_sides=("north",), door_w=3, tile=True, top=None)
    ix0, iz0, ix1, iz1 = hall["inner"]
    f = hall["f"]
    k.put(ix0, f + 1, iz1, "minecraft:blast_furnace", {"facing": "north", "lit": "false"})
    k.put(ix0 + 1, f + 1, iz1, "minecraft:anvil", {"facing": "east"})
    k.put(ix0 + 2, f + 1, iz1, "minecraft:smithing_table")
    k.put(ix1, f + 1, iz1, "minecraft:grindstone", {"face": "floor", "facing": "north"})
    k.put(ix1 - 1, f + 1, iz1, "minecraft:crafting_table")
    k.put(ix1 - 2, f + 1, iz1, "minecraft:stonecutter", {"facing": "north"})
    for x in range(ix0, ix1 + 1, 2):
        k.put(x, f + 3, iz1, "minecraft:dark_oak_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false", "waterlogged": "false"})
    k.titik("kerja:palu", ix0 + 1, f + 1, iz1 - 1, "south", {"nama": "landasan"})
    k.titik("kerja:gergaji", ix1 - 1, f + 1, iz1 - 1, "south")
    k.jangkar_air(ix0 + 3.5, f + 1, iz0 + 1.5, "kotabesar/tukang", yaw=0.0)
    for x in range(x0, x1 + 1, 3):
        k.put(x, LY + 1, z0 - 2, "minecraft:stripped_spruce_log", {"axis": "x"})
        k.put(x + 1, LY + 1, z0 - 2, "minecraft:stripped_spruce_log", {"axis": "x"}) if x + 1 <= x1 else None


def gambar_hunian(k, varian):
    cx, cz = C("hunian_kanal")

    def P(u, v):
        return cx + u, cz + v

    # the lanes between the two house rows of each strip, the alleys to the bridges (u = +-24)
    jalan_lurus(k, cx - 43, cz - 31, cx + 42, cz - 27, lampu=10)
    jalan_lurus(k, cx - 43, cz + 27, cx + 42, cz + 31, lampu=10)
    for u in (-24, 24):
        jalan_lurus(k, cx + u - 2, cz - 43, cx + u + 2, cz - 6)
        jalan_lurus(k, cx + u - 2, cz + 5, cx + u + 2, cz + 42)
    for v in (-24, 24):
        jalan_lurus(k, cx + 37, cz + v - 2, cx + 42, cz + v + 2)
    # two bridges over the back-water canal
    for u in (-24, 24):
        jembatan(k, "z", cz, cx + u, gaya="lengkung" if varian != 1 else "bundar")
    seg = [(cx - 43, cx - 28), (cx - 20, cx + 20), (cx + 28, cx + 42)]
    seed = 600 + 50 * varian
    # north strip: north row backs on the border canal (front south), south row backs on the back-water (front north)
    hunian_baris(k, cz - 42, "south", seg[1:], varian, seed)           # the shrine stands west of the first alley
    hunian_baris(k, cz - 14, "north", seg, varian, seed + 7)
    hunian_baris(k, cz + 6, "south", seg, varian, seed + 13)
    hunian_baris(k, cz + 35, "north", [(cx - 43, cx - 28), (cx - 20, cx + 20)], varian, seed + 19, d=7)
    # the ancestral shrine (lot 0,0, every variant) and the craftsman's workshop (lot 1,1, every variant)
    kuil_leluhur(k, cx - 43, cz - 42, cx - 33, cz - 34, "south")
    bengkel_tukang(k, cx + 28, cz + 34, cx + 41, cz + 41)
    # well, washing place, children's corner, laundry poles
    K.well(k, cx - 10, cz - 21)
    k.titik("kerja:sapu", cx - 10, LY + 1, cz - 19, "south", {"nama": "sumur"})
    K.well(k, cx + 10, cz + 21)
    k.titik("kerja:sapu", cx + 10, LY + 1, cz + 19, "north", {"nama": "sumur"})
    for (u, v) in ((-38, -22), (34, 22), (-6, 21)):
        x, z = P(u, v)
        for dx in (0, 4):
            for y in (LY + 1, LY + 2, LY + 3):
                k.put(x + dx, y, z, "wood_fence")
        for dx in range(1, 4):
            k.put(x + dx, LY + 3, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
    for (u, v, h) in ((-16, -24, "south"), (14, 24, "north"), (-30, 28, "east"), (30, -28, "west"), (0, -22, "south")):
        x, z = P(u, v)
        k.titik("main" if u == 0 else "obrol", x, LY + 1, z, h)
    for (u, v) in ((-2, -25), (2, 25), (36, -36)):
        x, z = P(u, v)
        if k.get(x, LY + 1, z) in (None, AIR):
            K.tree(k, x, LY + 1, z, "willow" if varian != 2 else "cherry")
    k.jalur([P(-43, -29), P(42, -29)], jalur="jalan")
    k.jalur([P(-43, 29), P(42, 29)], jalur="jalan")
    titik_rute(k)
    zona(k, cx - 2, cz - 22, "hunian_kanal")


def build_hunian_kanal():
    """Hunian Kanal (38.T.5.2): four lots of canal houses (front door to the lane, back steps down to the water, boats
    moored), drawn twice with other widths / storeys / shop fronts; every lot pool holds both variants. The
    ancestral shrine and the craftsman's workshop stand in every variant of their lot."""
    d = "hunian_kanal"
    ks = []
    for v in (0, 2):                  # two drawings (the 1.2 MB theme budget); varian 2 has shop fronts and cherries
        k = kanvas_distrik(d, varian=v)
        gambar_hunian(k, v)
        ks.append(k)
    selesai_distrik(ks[0], d, {(0, 0): "rumah_barat_laut", (1, 0): "rumah_timur_laut", (0, 1): "rumah_barat_daya",
                               (1, 1): "rumah_tenggara"},
                    kode_dalam={"h0": "B", "h1": "B", "v0": "K", "v1": "K"}, varian=ks[1:])


def build_gerbang_air():
    """Gerbang Air (38.T.5.2): the Kanal Utama runs south through the district and leaves through the Pintu Air (the
    gate tower over the water arch is drawn by tembok()); the Penginapan Bulan di Kanal, the teleport disc station,
    the south land gate square with the Perahu Pos stop, the guard post."""
    d = "gerbang_air"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    for v in (-24, 16):
        jembatan(k, "x", cx, cz + v, gaya="bundar" if v > 0 else "lengkung")
    # the south gate road (u = -18) and the cross streets
    jalan_lurus(k, cx - 20, cz - 2, cx - 16, cz + 42, lampu=8)
    jalan_lurus(k, cx - 43, cz - 26, cx - 11, cz - 22, lampu=10)
    jalan_lurus(k, cx + 10, cz - 26, cx + 42, cz - 22, lampu=10)
    jalan_lurus(k, cx - 43, cz + 14, cx - 11, cz + 18)
    jalan_lurus(k, cx + 10, cz + 14, cx + 42, cz + 18)
    jalan_lurus(k, cx - 43, cz - 2, cx - 6, cz + 1)
    K.paifang(k, cx - 18, cz + 38, width=11, height=8, facing="south")
    # Penginapan Bulan di Kanal (north-west lot): the main house, a courtyard, the canal wing with water-view rooms
    inn = K.aula(k, cx - 42, cz - 43, cx - 16, cz - 30, h=5, podium_h=1, teras=1, roof="hip", tiers=1,
                 door_sides=("south",), door_w=3, tile=True, top="pearl")
    ix0, iz0, ix1, iz1 = inn["inner"]
    f = inn["f"]
    ruang(k, ix0, iz0, ix1, iz1, f, "kedai", isi=0.35, rug=False)
    ruang(k, ix0, iz0, ix1, iz1, f + 5, "inap", isi=0.5)
    K.counter(k, ix0 + 2, ix0 + 8, iz1 - 3, f, "south")
    k.jangkar_air(ix0 + 5.5, f + 1, iz1 - 4.5, "kotabesar/layanan", yaw=0.0, layanan="penginapan", peran="PEMILIK_PENGINAPAN",
                  nama="bulan_di_kanal")
    for (tx, tz) in ((ix1 - 4, iz0 + 3), (ix1 - 10, iz0 + 3)):
        K.furnish_dining(k, tx, tz, f, n=4)
    K.upper_floor(k, ix0, iz0, ix1, iz1, f + 5, hole=(ix0, iz0, ix0, iz0 + 2))
    K.ladder(k, ix0, f + 1, f + 5, iz0 + 1, "east")
    for i, x in enumerate(range(ix0 + 2, ix1 - 1, 4)):
        K.furnish_bedroom(k, x, iz0, min(x + 3, ix1), iz0 + 4, f + 5, beds=1, facing="north")
    wing = K.aula(k, cx - 15, cz - 20, cx - 7, cz - 4, h=4, podium_h=1, teras=0, roof="hip", door_sides=("west",),
                  door_w=3, tile=True, along="z", top=None)
    wx0, wz0, wx1, wz1 = wing["inner"]
    ruang(k, wx0, wz0, wx1, wz1, wing["f"], "inap", isi=0.5, rug=False)
    for z in range(wz0, wz1 + 1, 3):
        K.bed(k, wx1, wing["f"] + 1, z, "east")
        k.titik("kasur", wx1, wing["f"] + 1, z, "west")
        K.lattice(k, wx1 + 1, wing["f"] + 2, z + 1, "east")
    taman_tanah(k, cx - 40, cz - 20, cx - 18, cz - 4)
    kolam(k, [(cx - 30, cz - 12, 5, 3)], teratai=0.2)
    paviliun(k, cx - 24, cz - 18, cx - 20, cz - 14, tinggi=3, puncak="pearl")
    k.titik("kursi", cx - 22, LY + 1, cz - 16, "south", {"nama": "penginapan"})
    k.titik("obrol", cx - 36, LY + 1, cz - 8, "east")
    # teleport disc station (north-east lot): a round stone court, four jade pillars, a roofed waiting gallery
    dcx, dcz = cx + 26, cz - 36
    for x in range(dcx - 6, dcx + 7):
        for z in range(dcz - 6, dcz + 7):
            r2 = (x - dcx) ** 2 + (z - dcz) ** 2
            if r2 <= 36:
                k.put(x, LY, z, "kerb_chisel" if r2 <= 4 else ("minecraft:polished_diorite" if r2 <= 16 else "kerb"))
    for (dx, dz) in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
        for y in range(LY + 1, LY + 5):
            k.put(dcx + dx, y, dcz + dz, "minecraft:prismarine_bricks" if y < LY + 4 else "minecraft:sea_lantern")
    k.jangkar_air(dcx + 0.5, LY + 1, dcz + 0.5, "kotabesar/cakram", yaw=0.0, protect=12)
    rumah(k, cx + 8, cz - 20, 9, 8, "south", 701, tingkat=2, kamar=2)
    rumah(k, cx + 22, cz - 20, 9, 8, "south", 702, tingkat=2, kamar=3)
    rumah(k, cx + 34, cz - 18, 8, 7, "south", 703, tingkat=1, kamar=1)
    rumah(k, cx + 8, cz + 3, 9, 8, "north", 704, tingkat=2, kamar=2)
    rumah(k, cx + 22, cz + 3, 9, 8, "north", 705, tingkat=2, toko=BARANG_TOKO[2], kamar=1)
    # the Perahu Pos stop and the waiting pavilion on the west bank of the Kanal Utama
    x, z = P(0, 12)
    tambatan(k, x, z, "pos", 0, halte="gerbang_air")
    paviliun(k, cx - 13, cz + 6, cx - 7, cz + 11, tinggi=3, buka=("east", "south"), puncak="pearl")
    k.titik("obrol", cx - 10, LY + 1, cz + 8, "east", {"nama": "halte"})
    k.titik("obrol", cx - 10, LY + 1, cz + 10, "east", {"nama": "halte"})
    x, z = P(0, 30)
    tambatan(k, x, z, "tur", 0, urut=0, tempat="gerbang_air")
    # south gate square: customs table, guards, carts of goods
    K.paving(k, cx - 40, cz + 30, cx - 8, cz + 42, LY, role="paving", alt="kerb", alt_chance=0.2)
    K.table(k, cx - 13, LY + 1, cz + 34)
    K.chair(k, cx - 13, LY + 1, cz + 33, "south")
    k.titik("jaga", cx - 13, LY + 1, cz + 33, "south", {"nama": "bea"})
    K.crates(k, cx - 38, cz + 32, cx - 32, cz + 36, LY + 1, h=2)
    for u in (-30, -24):
        x, z = P(u, 26)
        K.stone_lantern(k, x, LY + 1, z)
    rumah(k, cx - 42, cz + 20, 9, 7, "north", 706, tingkat=2, toko=BARANG_TOKO[5], kamar=1)
    rumah(k, cx - 30, cz + 20, 9, 7, "north", 707, tingkat=1, kamar=2)
    # guard post (south-east lot): barracks, drum tower watching the Pintu Air, weapon rack
    pos = K.aula(k, cx + 14, cz + 24, cx + 34, cz + 38, h=4, podium_h=1, teras=1, roof="hip", door_sides=("west",),
                 door_w=3, tile=True, top="pearl")
    px0, pz0, px1, pz1 = pos["inner"]
    for i, x in enumerate(range(px0 + 1, px1, 3)):
        K.bed(k, x, pos["f"] + 1, pz0, "north")
        k.titik("kasur", x, pos["f"] + 1, pz0, "south")
    K.table(k, (px0 + px1) // 2, pos["f"] + 1, pz1 - 1)
    k.jangkar_air(cx + 12.5, LY + 1, cz + 30.5, "kota/penjaga", yaw=270.0)
    k.jangkar_air(cx + 7.5, LY + 1, cz + 40.5, "kota/penjaga", yaw=180.0)
    k.jangkar_air(cx + 20.5, LY + 1, cz + 21.5, "kotabesar/rak_senjata", yaw=0.0)
    tx0, tz0 = cx + 36, cz + 34
    for x in range(tx0, tx0 + 5):
        for z in range(tz0, tz0 + 5):
            edge = x in (tx0, tx0 + 4) or z in (tz0, tz0 + 4)
            for y in range(LY + 1, LY + 9):
                k.put(x, y, z, ("kerb" if y < LY + 4 else "plaster") if edge else AIR)
            k.put(x, LY + 9, z, "wood")
    K.ladder(k, tx0 + 1, LY + 1, LY + 9, tz0 + 1, "south")
    k.put(tx0 + 1, LY + 9, tz0 + 1, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    for x in range(tx0 - 1, tx0 + 6):
        for z in range(tz0 - 1, tz0 + 6):
            if x in (tx0 - 1, tx0 + 5) or z in (tz0 - 1, tz0 + 5):
                k.put(x, LY + 10, z, "rail")
    K.drum(k, tx0 + 3, LY + 10, tz0 + 2, "west")
    K.hip(k, tx0, LY + 13, tz0, tx0 + 4, tz0 + 4, overhang=1, top="pearl", role="tile_stairs", slab_role="tile_slab", full_role="tile")
    for (px, pz) in ((tx0, tz0), (tx0 + 4, tz0), (tx0, tz0 + 4), (tx0 + 4, tz0 + 4)):
        for y in range(LY + 10, LY + 13):
            k.put(px, y, pz, "red")
    k.titik("jaga", tx0 + 2, LY + 10, tz0 + 3, "south", {"nama": "menara_pintu_air"})
    for (u, v, h) in ((-34, -24, "south"), (30, 16, "north"), (-28, 16, "north"), (16, -24, "south")):
        x, z = P(u, v)
        k.titik("obrol", x, LY + 1, z, h)
    k.jalur([P(-18, 42), P(-18, 16), P(-18, 0), P(-6, 0)], jalur="jalan")
    k.jalur([P(-43, -24), P(-11, -24), P(10, -24), P(42, -24)], jalur="jalan")
    titik_rute(k)
    zona(k, cx - 26, cz + 2, d)
    selesai_distrik(k, d, {(0, 0): "penginapan", (1, 0): "cakram", (0, 1): "gerbang_darat", (1, 1): "pos_jaga"},
                    kode_dalam={"h0": "K", "h1": "K", "v0": "B", "v1": "B"}, utuh=("penginapan",))


def perahu_terbalik(k, x0, z0, panjang, sumbu="x"):
    """An overturned boat on two trestles in the boatyard: a hull of upside-down dark oak stairs over a keel log."""
    for i in range(panjang):
        if sumbu == "x":
            x, z = x0 + i, z0
            sides = ((x, z - 1, "south"), (x, z + 1, "north"))
        else:
            x, z = x0, z0 + i
            sides = ((x - 1, z, "east"), (x + 1, z, "west"))
        k.put(x, LY + 3, z, "minecraft:stripped_dark_oak_log", {"axis": sumbu})
        if 0 < i < panjang - 1:
            for (sx, sz, face) in sides:
                k.stairs(sx, LY + 2, sz, "wood_stairs", face, half="top")
    for i in (1, panjang - 2):
        if sumbu == "x":
            k.put(x0 + i, LY + 1, z0, "wood_fence")
            k.put(x0 + i, LY + 2, z0, "wood_fence")
        else:
            k.put(x0, LY + 1, z0 + i, "wood_fence")
            k.put(x0, LY + 2, z0 + i, "wood_fence")


def build_dermaga_perahu():
    """Dermaga Perahu (38.T.5.2): the boatyard with overturned boats on trestles, the Rumah Perahu Naga over the slip
    (two dragon boats, carved heads), the boat-hire office, the central Perahu Pos stop with four moorings and the tour
    start, the rowers' houses."""
    d = "dermaga_perahu"
    k = kanvas_distrik(d)
    cx, cz = C(d)

    def P(u, v):
        return cx + u, cz + v

    # Rumah Perahu Naga over the end of the slip (slip water x 60..69 = u -36..-27, z 48..75 = v -48..-21)
    hx0, hz0, hx1, hz1 = cx - 39, cz - 36, cx - 24, cz - 14
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            if air_di(x, z):
                continue
            k.put(x, LY, z, "wood" if z > cz - 21 else "kerb")
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            edge = x in (hx0, hx1) or z in (hz0, hz1)
            post = edge and ((x - hx0) % 3 == 0 or (z - hz0) % 4 == 0)
            if post and not air_di(x, z):
                for y in range(LY + 1, LY + 7):
                    k.put(x, y, z, "red")
            elif edge and z == hz1 and not air_di(x, z):
                for y in range(LY + 1, LY + 7):
                    k.put(x, y, z, "plaster" if y > LY + 1 else "kerb")
    K.beam_band(k, hx0, hz0, hx1, hz1, LY + 7)
    K.brackets(k, hx0, hz0, hx1, hz1, LY + 7)
    ruang(k, hx0 + 1, hz0 + 1, hx1 - 1, hz1 - 1, LY, "perahu", isi=0.65, rug=False)
    K.hip(k, hx0, LY + 8, hz0, hx1, hz1, overhang=1, tiers=1, top="pearl", role="tile_stairs", slab_role="tile_slab", full_role="tile")
    # carved dragon heads on the slip end wall and the drums of the crews
    for (u, v) in ((-34, -15), (-29, -15)):
        x, z = P(u, v)
        k.put(x, LY + 3, z, "minecraft:gold_block")
        k.put(x, LY + 4, z, "minecraft:red_glazed_terracotta", {"facing": "north"})
        k.stairs(x, LY + 5, z, "minecraft:red_nether_brick_stairs", "north", half="bottom")
        k.put(x, LY + 3, z - 1, "minecraft:red_nether_brick_wall")
    for (u, v) in ((-38, -18), (-25, -18)):
        x, z = P(u, v)
        K.drum(k, x, LY + 1, z, "north")
    for (u, v) in ((-33, -30), (-30, -30)):
        x, z = P(u, v)
        tambatan(k, x, z, "naga", 0, lintasan="danau")
    k.titik("kerja:palu", cx - 37, LY + 1, cz - 25, "east", {"nama": "perahu_naga"})
    k.jangkar_air(cx - 31.5, LY + 1, cz - 16.5, "kota_tema/perahu_naga", yaw=180.0)
    # the boatyard shed (north-east of the slip): overturned boats, timber stacks, tools
    yx0, yz0, yx1, yz1 = cx - 18, cz - 40, cx + 18, cz - 18
    for x in range(yx0, yx1 + 1):
        for z in range(yz0, yz1 + 1):
            k.put(x, LY, z, "minecraft:coarse_dirt" if (x * 5 + z) % 4 else "minecraft:gravel")
            edge = x in (yx0, yx1) or z in (yz0, yz1)
            if edge and ((x - yx0) % 4 == 0 or (z - yz0) % 4 == 0) and z != yz1:
                for y in range(LY + 1, LY + 7):
                    k.put(x, y, z, "red")
    for x in range(yx0, yx1 + 1):
        for z in range(yz0, yz1 + 1):
            k.put(x, LY + 7, z, "wood") if x in (yx0, yx1) or z in (yz0, yz1) else None
    atap_pelana(k, yx0, LY + 8, yz0, yx1, yz1, overhang=1, kuda=False)
    ruang(k, yx0 + 1, yz0 + 1, yx1 - 1, yz1 - 1, LY, "perahu", isi=0.5, rug=False)
    for i, (u, v) in enumerate(((-14, -36), (-14, -28), (2, -36), (2, -28))):
        x, z = P(u, v)
        perahu_terbalik(k, x, z, 9 if i % 2 == 0 else 7)
        if i < 3:
            k.titik("kerja:palu", x + 4, LY + 1, z + 2, "north", {"nama": "galangan"})
    k.titik("kerja:gergaji", cx + 12, LY + 1, cz - 22, "north")
    for x in range(cx + 10, cx + 17):
        for y in (LY + 1, LY + 2):
            k.put(x, y, cz - 38, "minecraft:stripped_spruce_log", {"axis": "x"})
    K.crates(k, cx + 10, cz - 34, cx + 16, cz - 30, LY + 1, h=1)
    k.put(cx + 14, LY + 1, cz - 26, "minecraft:grindstone", {"face": "floor", "facing": "south"})
    k.put(cx + 15, LY + 1, cz - 26, "minecraft:crafting_table")
    # boat hire: office by the border canal, three hire moorings in the canal (anchor kota_tema/sewa_perahu)
    hire = rumah(k, cx + 30, cz - 42, 8, 6, "south", 801, tingkat=1, toko=("minecraft:dark_oak_trapdoor", "minecraft:lantern"), kamar=1)
    k.jangkar_air(cx + 33.5, LY + 1, cz - 34.5, "kota_tema/sewa_perahu", yaw=0.0, tokoh="bibi_chuan")
    for u in (30, 34, 38):
        x, z = P(u, -47)
        tambatan(k, x, z, "sewa", 90, tempat="dermaga")
    # Halte Perahu Pos pusat (south-west lot): four moorings on the west border canal, waiting galleries, the tour start
    for v in (10, 16, 28, 34):
        x, z = P(-47, v)
        tambatan(k, x, z, "pos", 0, halte="pusat")
    koridor(k, "z", cz + 6, cz + 19, cx - 41, dinding=1)
    koridor(k, "z", cz + 29, cz + 40, cx - 41, dinding=1)
    for v in (8, 14, 31, 37):
        x, z = P(-41, v)
        k.titik("obrol", x, LY + 1, z, "west", {"nama": "halte"})
    office = K.aula(k, cx - 36, cz + 8, cx - 24, cz + 18, h=4, podium_h=1, teras=1, roof="hip", door_sides=("west",),
                    door_w=3, tile=True, top="pearl")
    K.counter_z(k, office["inner"][0] + 1, office["inner"][1] + 1, office["inner"][3] - 1, office["f"], "west")
    ruang(k, *office["inner"], office["f"], "kantor", isi=0.45)
    k.jangkar_air(cx - 33.5, LY + 1, cz + 20.5, "kota_tema/halte", yaw=270.0, halte="pusat")
    x, z = P(-47, 40)
    tambatan(k, x, z, "tur", 0, urut=5, tempat="dermaga")
    # rowers' houses (south-east lot) with drying nets and a small river-god shrine
    for i, (u, v, dep) in enumerate(((6, 4, "south"), (20, 4, "south"), (6, 30, "north"), (20, 30, "north"), (32, 16, "west"))):
        rumah(k, cx + u, cz + v, 8, 8, dep, 810 + i, tingkat=2 if i % 2 == 0 else 1, kamar=2)
    for (u, v) in ((14, 22), (28, 24)):
        x, z = P(u, v)
        for y in (LY + 1, LY + 2, LY + 3):
            k.put(x, y, z, "wood_fence")
            k.put(x + 3, y, z, "wood_fence")
        for dx in range(1, 3):
            k.put(x + dx, LY + 3, z, "minecraft:iron_chain", {"axis": "x", "waterlogged": "false"})
        k.titik("kerja:jala", x + 1, LY + 1, z + 1, "north", {"nama": "jemur_jala"})
    kuil_leluhur(k, cx - 18, cz + 30, cx - 8, cz + 38, "north")
    for (u, v, h) in ((-6, -10, "south"), (10, -10, "south"), (-14, 22, "east"), (12, 40, "north")):
        x, z = P(u, v)
        k.titik("obrol", x, LY + 1, z, h)
    k.titik("main", cx + 2, LY + 1, cz + 20, "south")
    jalan_lurus(k, cx - 43, cz - 9, cx + 42, cz - 6, lampu=10)
    jalan_lurus(k, cx - 20, cz - 5, cx - 16, cz + 42)
    k.jalur([P(-43, -8), P(42, -8)], jalur="jalan")
    titik_rute(k)
    zona(k, cx + 4, cz - 12, d)
    selesai_distrik(k, d, {(0, 0): "rumah_perahu_naga", (1, 0): "galangan", (0, 1): "halte_pusat", (1, 1): "rumah_pendayung"},
                    kode_dalam={"h0": "B", "h1": "B", "v0": "B", "v1": "B"}, utuh=("rumah_perahu_naga",))


def main(argv):
    only = [a for a in argv if not a.startswith("-")]
    builders = {n: globals()["build_" + n] for n in ORDER if "build_" + n in globals()}
    for name in ORDER:
        if (not only or name in only) and name in builders:
            builders[name]()
    write_processors()
    write_pools()
    write_structures()
    tl.cek_semua()
    total = sum(os.path.getsize(p) for p, _ in structgen._SAVED)
    print("kota_tema/air: %d templates, %d pools, gzip %d bytes" % (len(structgen._SAVED), len(POOLS), total))


if __name__ == "__main__":
    main(sys.argv[1:])
