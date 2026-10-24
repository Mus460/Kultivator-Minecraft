"""DESIGN 38.C.4 caves and 38.C.5 natural sites (module 38GU).

Writes (data/kultivator/...):
  structure/gua/<gua>/**.nbt      gua_pertapa (5 pieces), makam_warisan tier 1-3 (10 pieces each), sarang_binatang
                                  (hollow tree + den cave), sarang_iblis (9 pieces), gua_urat_roh (geode + 2 tunnels),
                                  cermin_hati (2 pieces)
  structure/situs/<situs>.nbt     puncak_petir, panggung_bintang, mata_air_roh (one piece each)
  worldgen/template_pool/gua/**, situs/**; worldgen/structure/gua/*.json, situs/*.json; processor_list gua/*
  resourcepacks/dunia_gua/data/kultivator/worldgen/structure_set/{gua,situs}/*.json  (cincin_tempat, salt 380511-380523)
  tags/worldgen/biome/has_structure/{gua,situs}/*.json, tags/worldgen/structure/{gua,situs,gua_makam}.json

Coordinates are template-local (x east, y up, z south). Underground pieces are a rock shell around carved air: every
cell the generator does not touch stays structure void, so the world's own stone, ores and caves stay around them.
Surface-reached caves (hermit, beast den, demon lair, heart mirror) start in a sunken court at surface - 7
(start_height -8 projected to WORLD_SURFACE_WG, the reruntuhan gate pattern) whose stair climbs to the surface.
Doors, seals, cages, glyph plates and guardians are entities made by anchors (gua/<what>, DuniaAnchors module "gua";
DESIGN 38.0 rule 1): templates only leave room for them. No fire, lava, spawner, tnt or lightning rod (38.D.2).

Run:  python tools/gen_tempat_gua.py      (then python tools/cek_tempat.py)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tempat_lib as tl  # noqa: E402
import structgen  # noqa: E402

NS = "kultivator"
PACK = "dunia_gua"
J_PINTU = "kultivator:gua/pintu"      # a piece's way on (target: the next piece's entry)
J_MASUK = "kultivator:gua/masuk"      # a piece's entry
J_TURUN = "kultivator:gua/turun"      # a vertical way down (stela -> stair shaft)
J_ATAS = "kultivator:gua/atas"        # the top of a stair shaft
PROC = "kultivator:gua/lapuk"         # light moss and cracks + protected blocks
PROC_UTUH = "kultivator:gua/utuh"     # protected blocks only (sites, tomb halls)
FY = 1                                 # floor block layer of every piece; one stands at FY + 1

SALT = {
    "gua_pertapa": 380511, "makam_warisan_1": 380512, "makam_warisan_2": 380513, "makam_warisan_3": 380514,
    "sarang_binatang": 380515, "sarang_iblis": 380516, "gua_urat_roh": 380517, "cermin_hati": 380518,
    "puncak_petir": 380521, "panggung_bintang": 380522, "mata_air_roh": 380523,
}

BIOMES = {
    "gua_pertapa": ["windswept_hills", "windswept_forest", "windswept_gravelly_hills", "meadow", "grove", "forest",
                    "birch_forest", "dappled_forest", "dark_forest", "taiga", "old_growth_pine_taiga",
                    "old_growth_spruce_taiga", "jungle", "sparse_jungle", "cherry_grove"],
    "makam_warisan": ["plains", "sunflower_plains", "snowy_plains", "desert", "savanna", "savanna_plateau", "forest",
                      "flower_forest", "birch_forest", "dappled_forest", "dark_forest", "taiga", "snowy_taiga",
                      "meadow", "windswept_hills", "badlands", "wooded_badlands", "eroded_badlands", "jungle",
                      "sparse_jungle", "bamboo_jungle", "old_growth_birch_forest", "old_growth_pine_taiga",
                      "old_growth_spruce_taiga", "cherry_grove", "grove", "swamp", "pale_garden"],
    "sarang_binatang": ["forest", "birch_forest", "dappled_forest", "dark_forest", "jungle", "sparse_jungle",
                        "bamboo_jungle", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
                        "old_growth_birch_forest", "badlands", "wooded_badlands"],
    "sarang_iblis": ["plains", "snowy_plains", "desert", "savanna", "savanna_plateau", "forest", "birch_forest",
                     "dark_forest", "taiga", "snowy_taiga", "meadow", "windswept_hills", "windswept_gravelly_hills",
                     "windswept_savanna", "badlands", "wooded_badlands", "eroded_badlands", "jungle", "grove", "swamp",
                     "old_growth_pine_taiga", "old_growth_spruce_taiga"],
    "cermin_hati": ["dark_forest", "pale_garden", "swamp", "mangrove_swamp"],
    "puncak_petir": ["jagged_peaks", "frozen_peaks", "stony_peaks"],
    "panggung_bintang": ["meadow", "windswept_hills", "savanna_plateau", "snowy_slopes"],
    "mata_air_roh": ["forest", "birch_forest", "flower_forest", "dappled_forest", "cherry_grove",
                     "old_growth_birch_forest", "taiga"],
}

FACE_OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
SIDE_DIR = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


# ================================================================================================== noise
def _hash(*v):
    h = 0x9E3779B1
    for x in v:
        h = ((h ^ (int(x) & 0xFFFFFFFF)) * 0x85EBCA6B) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 0xC2B2AE35) & 0xFFFFFFFF
        h ^= h >> 16
    return h / 4294967296.0


def _smooth(t):
    return t * t * (3 - 2 * t)


def noise3(x, y, z, scale, seed):
    """Value noise in 0..1 (trilinear, smoothstep)."""
    fx, fy, fz = x / scale, y / scale, z / scale
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = _smooth(fx - ix), _smooth(fy - iy), _smooth(fz - iz)
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                acc += w * _hash(ix + dx, iy + dy, iz + dz, seed)
    return acc


# ================================================================================================== the piece
ROCK = {
    # natural rock mixes by family (weights); the hermit hills and forests, deep stone, mountain granite, badlands
    "batu": [("minecraft:stone", 10), ("minecraft:andesite", 3), ("minecraft:cobblestone", 1), ("minecraft:tuff", 1)],
    "dalam": [("minecraft:deepslate", 9), ("minecraft:tuff", 3), ("minecraft:cobbled_deepslate", 1)],
    "gunung": [("minecraft:stone", 8), ("minecraft:granite", 2), ("minecraft:andesite", 2), ("minecraft:calcite", 1)],
    "rawa": [("minecraft:mud", 4), ("minecraft:packed_mud", 3), ("minecraft:stone", 3), ("minecraft:tuff", 2)],
    "tanah": [("minecraft:dirt", 6), ("minecraft:coarse_dirt", 3), ("minecraft:rooted_dirt", 2), ("minecraft:stone", 2)],
}


class G(tl.Lot):
    """A cave or site template: tempat_lib.Lot plus natural rock, carving and cave dressing helpers."""

    def __init__(self, folder, nama, sx, sy, sz, palet="gua", batu="batu", seed=None):
        super().__init__(folder, nama, tinggi=sy, lebar=sx, dalam=sz, palet=palet, seed=seed)
        self.batu_mix = ROCK[batu]
        self.nseed = int(self.rng.random() * 1e6)

    # ------------------------------------------------------------------------------------------- basics
    def rock_id(self):
        total = sum(w for _, w in self.batu_mix)
        r = self.rng.random() * total
        for bid, w in self.batu_mix:
            r -= w
            if r <= 0:
                return bid
        return self.batu_mix[0][0]

    def simpan(self):
        """Saves after the hidden-rock pass: a natural-rock cell of this piece's mix that touches no air, water or
        non-full block on any side can never be seen, so it becomes the mix's main rock. The visible faces keep the
        mix; the template compresses far better (38.C.10 gzip budget)."""
        main = self.batu_mix[0][0]
        family = set(b for b, _ in self.batu_mix)
        for pos in list(self.cells):
            bid = self.get(*pos)
            if bid == main or bid not in family:
                continue
            x, y, z = pos
            hidden = True
            for (dx, dy, dz) in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                nx, ny, nz = x + dx, y + dy, z + dz
                if not self.inside(nx, ny, nz) or self.get(nx, ny, nz) is None:
                    continue
                if not self.solid(nx, ny, nz):
                    hidden = False
                    break
            if hidden:
                self.put(x, y, z, main)
        return super().simpan()

    def empty(self, x, y, z):
        return self.inside(x, y, z) and self.get(x, y, z) is None

    def is_air(self, x, y, z):
        return self.inside(x, y, z) and self.get(x, y, z) in ("minecraft:air", "minecraft:cave_air")

    def solid(self, x, y, z):
        """True for a set, full, non-air block (what dripstone, lichen and vines can hang on)."""
        if not self.inside(x, y, z):
            return False
        bid = self.get(x, y, z)
        if bid is None or bid in ("minecraft:air", "minecraft:cave_air", "minecraft:water"):
            return False
        n = bid.split(":")[1]
        return not any(s in n for s in ("slab", "stairs", "fence", "wall", "carpet", "lantern", "candle", "chain",
                                        "bars", "pane", "glass", "lichen", "vine", "dripstone", "button", "pot",
                                        "flower", "grass", "fern", "roots", "cluster", "bud", "torch", "skull",
                                        "leaves", "dripleaf", "moss_carpet", "bush", "petals", "rail", "door",
                                        "trapdoor", "sign", "banner", "bed", "head", "chest", "barrel", "cauldron",
                                        "brewing", "lectern", "anvil", "bell", "campfire", "rod", "pickle"))

    def rock(self, x, y, z, only_empty=True):
        if self.inside(x, y, z) and (not only_empty or self.get(x, y, z) is None):
            self.put(x, y, z, self.rock_id())

    def rock_box(self, x0, y0, z0, x1, y1, z1, only_empty=True):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.rock(x, y, z, only_empty)

    def set_air(self, x, y, z):
        if self.inside(x, y, z):
            self.put(x, y, z, "minecraft:air")

    # ------------------------------------------------------------------------------------------- carving
    def rongga(self, cx, cy, cz, rx, ry, rz, lantai=None, kasar=0.28, kulit=2, skala=5.0, tanah=None):
        """An irregular cave chamber: an ellipsoid of radii rx/ry/rz around (cx, cy, cz) bent by noise. Inside: air;
        a rock shell `kulit` thick around it (only where nothing is set yet). lantai = a flat floor height (the cells
        at that height and below inside the chamber become floor rock, the top layer `tanah` or rock)."""
        pad = kulit + 2
        for x in range(int(cx - rx - pad), int(cx + rx + pad) + 1):
            for y in range(int(cy - ry - pad), int(cy + ry + pad) + 1):
                for z in range(int(cz - rz - pad), int(cz + rz + pad) + 1):
                    if not self.inside(x, y, z):
                        continue
                    n = noise3(x, y, z, skala, self.nseed) - 0.5
                    d = math.sqrt(((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2) + n * kasar * 2
                    if d < 1.0:
                        if lantai is not None and y <= lantai:
                            if y == lantai and tanah:
                                self.put(x, y, z, tanah[0] if self.rng.random() > tanah[2] else tanah[1])
                            else:
                                self.rock(x, y, z, only_empty=False)
                        else:
                            cur = self.get(x, y, z)
                            if cur is None or self._is_rock(cur):
                                self.set_air(x, y, z)
                    elif d < 1.0 + kulit / max(1.0, min(rx, ry, rz)) * 1.6 and self.get(x, y, z) is None:
                        self.rock(x, y, z)

    def _is_rock(self, bid):
        return any(bid == b for b, _ in self.batu_mix) or bid in (
            "minecraft:stone", "minecraft:andesite", "minecraft:deepslate", "minecraft:tuff", "minecraft:cobblestone",
            "minecraft:granite", "minecraft:calcite", "minecraft:dirt", "minecraft:mud", "minecraft:packed_mud",
            "minecraft:cobbled_deepslate", "minecraft:coarse_dirt", "minecraft:rooted_dirt")

    def lorong(self, pts, lebar=3, tinggi=4, lantai=FY, kulit=2, lantai_blok=None, kasar=0.6):
        """A tunnel along the horizontal polyline pts [(x, z), ...]: an arched profile `lebar` wide and `tinggi` high
        over a floor at y = lantai (floor blocks lantai_blok or rock), rock shell around."""
        half = lebar / 2.0
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            steps = max(abs(bx - ax), abs(bz - az), 1)
            for s in range(steps + 1):
                px = ax + (bx - ax) * s / steps
                pz = az + (bz - az) * s / steps
                for dx in range(-int(half) - kulit - 1, int(half) + kulit + 2):
                    for dz in range(-int(half) - kulit - 1, int(half) + kulit + 2):
                        x, z = int(round(px + dx)), int(round(pz + dz))
                        r = math.hypot(x - px, z - pz)
                        wob = (noise3(x, 3, z, 3.0, self.nseed) - 0.5) * kasar
                        for y in range(lantai - kulit, lantai + tinggi + kulit + 1):
                            if not self.inside(x, y, z):
                                continue
                            h = tinggi - max(0.0, (r / max(half, 0.5)) ** 2 - 0.35) * 1.6 + wob
                            inner = r <= half + 0.15 + wob * 0.5
                            if inner and lantai < y <= lantai + h:
                                cur = self.get(x, y, z)
                                if cur is None or self._is_rock(cur):
                                    self.set_air(x, y, z)
                            elif inner and y == lantai:
                                if self.get(x, y, z) is None or self._is_rock(self.get(x, y, z)):
                                    self.put(x, y, z, lantai_blok or self.rock_id())
                            elif self.get(x, y, z) is None:
                                self.rock(x, y, z)

    # ------------------------------------------------------------------------------------------- dressing
    def stalaktit(self, x, y, z, n):
        """A dripstone hanging from the solid block above (x, y, z) down n blocks (y = the first air cell)."""
        cells = [y - i for i in range(n) if self.is_air(x, y - i, z)]
        if len(cells) < n or not self.solid(x, y + 1, z) or not self.is_air(x, y - n, z):
            return
        names = ["base", "middle", "frustum", "tip"] if n >= 4 else ["base", "frustum", "tip"] if n == 3 else \
            ["frustum", "tip"] if n == 2 else ["tip"]
        while len(names) < n:
            names.insert(1, "middle")
        for i, yy in enumerate(cells):
            self.put(x, yy, z, "minecraft:pointed_dripstone", {"thickness": names[i], "vertical_direction": "down",
                                                                "waterlogged": "false"})

    def stalagmit(self, x, y, z, n):
        """A dripstone standing on the solid block below (x, y, z) up n blocks."""
        if not self.solid(x, y - 1, z) or any(not self.is_air(x, y + i, z) for i in range(n + 1)):
            return
        names = ["base", "middle", "frustum", "tip"] if n >= 4 else ["base", "frustum", "tip"] if n == 3 else \
            ["frustum", "tip"] if n == 2 else ["tip"]
        while len(names) < n:
            names.insert(1, "middle")
        for i in range(n):
            self.put(x, y + i, z, "minecraft:pointed_dripstone", {"thickness": names[i], "vertical_direction": "up",
                                                                   "waterlogged": "false"})

    def tempel(self, x, y, z, bid, sisi_extra=None):
        """A multiface block (glow_lichen, sculk_vein, resin_clump) on every solid neighbour of the air cell."""
        if not self.is_air(x, y, z):
            return False
        props = {}
        for face, (dx, dy, dz) in (("north", (0, 0, -1)), ("south", (0, 0, 1)), ("east", (1, 0, 0)), ("west", (-1, 0, 0)),
                                   ("up", (0, 1, 0)), ("down", (0, -1, 0))):
            props[face] = "true" if self.solid(x + dx, y + dy, z + dz) else "false"
        if "true" not in props.values():
            return False
        props["waterlogged"] = "false"
        self.put(x, y, z, bid, props)
        return True

    def sulur(self, x, y, z, n=3):
        """Vines hanging down from the side of a solid wall next to (x, y, z)."""
        for face, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            if self.solid(x + dx, y, z + dz):
                for i in range(n):
                    if not self.is_air(x, y - i, z):
                        break
                    props = {"north": "false", "south": "false", "east": "false", "west": "false", "up": "false"}
                    props[face] = "true"
                    self.put(x, y - i, z, "minecraft:vine", props)
                return True
        return False

    def akar(self, x, y, z, n=2):
        """Hanging roots under a solid ceiling."""
        if self.solid(x, y + 1, z):
            for i in range(n):
                if self.is_air(x, y - i, z):
                    self.put(x, y - i, z, "minecraft:hanging_roots", {"waterlogged": "false"})

    def dandani(self, x0, y0, z0, x1, y1, z1, lumut=0.18, lichen=0.04, tetes=0.03, akar=0.02, jamur=0.01, sulur=0.0,
                semak=0.0, lantai_ok=None):
        """Dresses every air cell of a box: moss carpet / fern / mushrooms on floors, glow lichen on walls, dripstone and
        roots under ceilings. lantai_ok(bid) limits floor dressing to floors of those blocks."""
        cells = []
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if self.is_air(x, y, z):
                        cells.append((x, y, z))
        for (x, y, z) in cells:
            if not self.is_air(x, y, z):
                continue
            r = self.rng.random()
            below = self.get(x, y - 1, z) if self.inside(x, y - 1, z) else None
            if self.solid(x, y - 1, z) and (lantai_ok is None or lantai_ok(below)):
                if r < lumut:
                    self.put(x, y, z, "minecraft:moss_carpet")
                    continue
                r -= lumut
                if r < jamur:
                    self.put(x, y, z, self.rng.choice(["minecraft:brown_mushroom", "minecraft:red_mushroom"]))
                    continue
                r -= jamur
                if r < semak and self.is_air(x, y + 1, z):
                    self.put(x, y, z, self.rng.choice(["minecraft:fern", "minecraft:short_grass", "minecraft:bush"]))
                    continue
            elif self.solid(x, y + 1, z) and r < tetes + akar:
                if r < tetes:
                    n = 1 + int(self.rng.random() * 3)
                    self.stalaktit(x, y, z, n)
                else:
                    self.akar(x, y, z, 1 + int(self.rng.random() * 2))
                continue
            elif r < lichen:
                self.tempel(x, y, z, "minecraft:glow_lichen")
            elif r < lichen + sulur:
                self.sulur(x, y, z, 2 + int(self.rng.random() * 3))

    # ------------------------------------------------------------------------------------------- furniture
    def stairs(self, x, y, z, bid, facing, half="bottom", shape="straight"):
        self.put(x, y, z, bid, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})

    def slab(self, x, y, z, bid, kind="bottom"):
        self.put(x, y, z, bid, {"type": kind, "waterlogged": "false"})

    def log(self, x, y, z, bid, axis="y"):
        self.put(x, y, z, bid, {"axis": axis})

    def lentera(self, x, y, z, gantung=False, bid="minecraft:lantern"):
        self.put(x, y, z, bid, {"hanging": "true" if gantung else "false", "waterlogged": "false"})

    def lentera_rantai(self, x, y_ceiling, z, rantai=1, bid="minecraft:lantern"):
        """A lantern hanging from the ceiling block at y_ceiling on `rantai` iron chains."""
        for i in range(rantai):
            self.put(x, y_ceiling - 1 - i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        self.lentera(x, y_ceiling - 1 - rantai, z, True, bid)

    def lilin(self, x, y, z, n=1, warna=""):
        self.put(x, y, z, "minecraft:%scandle" % (warna + "_" if warna else ""),
                 {"candles": str(max(1, min(4, n))), "lit": "false", "waterlogged": "false"})

    def lentera_batu(self, x, y, z):
        """A stone garden lantern (toro): wall base, chiseled body, a lantern inside, a slab cap."""
        self.put(x, y, z, "minecraft:stone_brick_wall", {"east": "none", "north": "none", "south": "none",
                                                         "west": "none", "up": "true", "waterlogged": "false"})
        self.put(x, y + 1, z, "minecraft:chiseled_stone_bricks")
        self.lentera(x, y + 2, z)
        self.slab(x, y + 3, z, "minecraft:stone_brick_slab")

    def dinding_tiang(self, x, y, z, bid="minecraft:stone_brick_wall", up=True):
        self.put(x, y, z, bid, {"east": "none", "north": "none", "south": "none", "west": "none",
                                "up": "true" if up else "false", "waterlogged": "false"})

    def pagar(self, x, y, z, bid, sisi=()):
        props = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
        for s in sisi:
            props[s] = "true"
        self.put(x, y, z, bid, props)

    def jeruji(self, x, y, z, sisi=(), bid="minecraft:iron_bars"):
        self.pagar(x, y, z, bid, sisi)

    def bunga_pot(self, x, y, z, isi="fern"):
        self.put(x, y, z, "minecraft:potted_" + isi)

    def tengkorak(self, x, y, z, rot=0, bid="minecraft:skeleton_skull"):
        self.put(x, y, z, bid, {"rotation": str(rot % 16), "powered": "false"})

    def kasur(self, x, y, z, facing, warna="brown"):
        """A bed whose foot is at (x, y, z) and head one block toward `facing`."""
        dx, dz = SIDE_DIR[facing]
        self.put(x, y, z, "minecraft:%s_bed" % warna, {"facing": facing, "part": "foot", "occupied": "false"})
        self.put(x + dx, y, z + dz, "minecraft:%s_bed" % warna, {"facing": facing, "part": "head", "occupied": "false"})

    def peti(self, x, y, z, facing, loot=None, bid="minecraft:chest"):
        data = {"LootTable": loot} if loot else None
        props = {"facing": facing, "waterlogged": "false"}
        if bid == "minecraft:chest":
            props["type"] = "single"
        elif bid == "minecraft:barrel":
            props = {"facing": facing, "open": "false"}
        self.put(x, y, z, bid, props, data)

    def tong(self, x, y, z, facing="up", loot=None):
        self.peti(x, y, z, facing, loot, "minecraft:barrel")

    def rak_buku(self, x, y, z, facing):
        props = {"facing": facing}
        for i in range(6):
            props["slot_%d_occupied" % i] = "true" if self.rng.random() < 0.6 else "false"
        self.put(x, y, z, "minecraft:chiseled_bookshelf", props)

    def air_kolam(self, x0, y, z0, x1, z1, dasar="minecraft:clay", tepi=None):
        """A still pool: water at y over a basin floor at y - 1 (dasar), optional rim blocks at y around it."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.put(x, y - 1, z, dasar)
                self.put(x, y, z, "minecraft:water", {"level": "0"})
        if tepi:
            for x in range(x0 - 1, x1 + 2):
                for z in range(z0 - 1, z1 + 2):
                    if (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)) and self.inside(x, y, z):
                        self.put(x, y, z, tepi)

    def mat(self, x0, y, z0, x1, z1, bid="minecraft:brown_carpet"):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.put(x, y, z, bid)

    def jangkar(self, x, y, z, kind, yaw=0.0, **data):
        """A DuniaAnchor at the centre of block (x, y, z), Rotation [yaw, 0] (turned with the piece). Data values keep
        their Python type (int -> TAG_Int, so DuniaAnchorEntity.protectRadius reads "protect"). <= 6 per template."""
        if self.jangkar_n >= tl.MAKS_JANGKAR:
            raise ValueError("%s: more than %d anchors in one template" % (self.id, tl.MAKS_JANGKAR))
        self.jangkar_n += 1
        payload = {"id": "kultivator:dunia_anchor", "kind": kind,
                   "Rotation": [structgen.nbt.Float(float(yaw)), structgen.nbt.Float(0.0)]}
        if data:
            payload["data"] = dict(data)
        self.entity(x + 0.5, float(y), z + 0.5, payload)

    def masuk(self, x, y, z, facing, final="minecraft:air"):
        """The entry connector of a piece on its `facing` side (facing outward)."""
        self.jigsaw_side(x, y, z, facing, name=J_MASUK, target=J_PINTU, pool="minecraft:empty", final_state=final)

    def pintu(self, x, y, z, facing, pool, final="minecraft:air"):
        """A way-on connector toward the next piece (facing outward)."""
        self.jigsaw_side(x, y, z, facing, name=J_PINTU, target=J_MASUK, pool=pool, final_state=final)


def blok(t, x, y, z, bid, **props):
    t.put(x, y, z, bid, {k: str(v) for k, v in props.items()} or None)


def loot(nama):
    return "%s:chests/tempat/gua/%s" % (NS, nama)


FACE_YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}
BUNGA = ["minecraft:lily_of_the_valley", "minecraft:azure_bluet", "minecraft:allium", "minecraft:oxeye_daisy",
         "minecraft:cornflower", "minecraft:fern", "minecraft:fern", "minecraft:blue_orchid", "minecraft:white_tulip"]


def pool_id(*parts):
    return "%s:%s" % (NS, "/".join(parts))


# ================================================================================================== sunken court
def halaman_cekung(t, cx, cz, rx, rz, tangga_x, lebar_tangga=3, atas=8,
                   tanah=("minecraft:grass_block", "minecraft:moss_block", 0.25), dinding_puncak=(7, 9), tutup_atas=None,
                   tangga_blok=("minecraft:stone_brick_stairs", "minecraft:mossy_stone_brick_stairs"),
                   pijak="minecraft:mossy_stone_bricks", pagar="minecraft:mossy_stone_brick_wall"):
    """The sunken court of a surface-reached cave (start piece, floor at y = FY = surface - 7 through start_height -8):
    an irregular court (centre cx/cz, radii rx/rz) open to the sky, rock walls up to the surface around it, and a stair
    `lebar_tangga` wide at x = tangga_x climbing south from the court to the surface (y = atas) at the template's south
    edge. Returns (court mask {(x, z)}, the z where the stair leaves the court)."""
    sx, sy, sz = t.size
    court = set()
    for x in range(sx):
        for z in range(sz):
            n = noise3(x, 0, z, 4.0, t.nseed) - 0.5
            d = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 + n * 0.5
            if d < 1.0:
                court.add((x, z))
    half = lebar_tangga // 2
    z_mulai = max(z for (x, z) in court if abs(x - tangga_x) <= half) if court else sz // 2
    for x in range(sx):
        for z in range(sz):
            in_stair = abs(x - tangga_x) <= half and z > z_mulai
            if (x, z) in court:
                t.rock(x, 0, z, only_empty=False)
                if t.get(x, FY, z) is None:
                    t.put(x, FY, z, tanah[0] if t.rng.random() > tanah[2] else tanah[1])
                for y in range(FY + 1, sy):
                    if t.get(x, y, z) is None:
                        t.set_air(x, y, z)
            elif in_stair:
                step = min(atas, FY + (z - z_mulai))
                for y in range(0, step):
                    t.rock(x, y, z, only_empty=False)
                if step < atas:
                    t.stairs(x, step, z, tangga_blok[1] if t.rng.random() < 0.3 else tangga_blok[0], "south")
                else:
                    t.put(x, step, z, pijak)
                for y in range(step + 1, sy):
                    t.set_air(x, y, z)
            else:
                lo, hi = dinding_puncak
                top = lo + int(noise3(x, 7, z, 5.0, t.nseed + 7) * (hi - lo + 1))
                for y in range(0, min(top, sy - 1) + 1):
                    if t.get(x, y, z) is None:
                        if y == top and top >= atas - 1:
                            t.put(x, y, z, tutup_atas or ("minecraft:grass_block" if t.rng.random() < 0.8 else "minecraft:coarse_dirt"))
                        elif y >= top - 1 and top >= atas - 1:
                            t.put(x, y, z, "minecraft:dirt")
                        else:
                            t.rock(x, y, z)
    # low parapets along the stair
    for z in range(z_mulai + 1, sz):
        step = min(atas, FY + (z - z_mulai))
        for x in (tangga_x - half - 1, tangga_x + half + 1):
            if t.inside(x, step + 1, z) and (x, z) not in court:
                t.dinding_tiang(x, step + 1, z, pagar, up=(z % 3 == 0))
    return court, z_mulai


def mulut_gua(t, x, z, facing, lebar=3, tinggi=4, bingkai="minecraft:chiseled_tuff_bricks", lintel="minecraft:tuff_bricks",
              dalam=4, lantai="minecraft:tuff_bricks"):
    """A carved cave mouth in a rock face at (x, z): opening `lebar` x `tinggi` above FY going `dalam` blocks into the
    rock (away from `facing`, the side the court is on), with a stone frame and lintel on the face."""
    half = lebar // 2
    dx, dz = SIDE_DIR[facing]
    ax, az = (1, 0) if facing in ("north", "south") else (0, 1)
    for depth in range(0, dalam):
        bx, bz = x - dx * depth, z - dz * depth
        for w in range(-half, half + 1):
            for y in range(FY + 1, FY + 1 + tinggi - (1 if abs(w) == half and half > 0 else 0)):
                t.set_air(bx + ax * w, y, bz + az * w)
            t.put(bx + ax * w, FY, bz + az * w, lantai if depth else "minecraft:polished_tuff")
    for y in range(FY + 1, FY + tinggi + 2):
        for w in (-half - 1, half + 1):
            t.put(x + ax * w, y, z + az * w, bingkai if y in (FY + 1, FY + tinggi + 1) else "minecraft:polished_tuff")
    for w in range(-half - 1, half + 2):
        t.put(x + ax * w, FY + tinggi + 1, z + az * w, lintel)
    t.put(x, FY + tinggi + 2, z, "minecraft:chiseled_tuff")


def atap_tutup(t, x0, x1, z0, z1, y_dasar, y_puncak):
    """Re-fills the rock cap over a buried piece's dome (cells above y_dasar that are still empty become rock up to
    y_puncak) so a piece under a shallow surface keeps a roof."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y_dasar, y_puncak + 1):
                if t.get(x, y, z) is None and t.is_air(x, y - 1, z):
                    t.rock(x, y, z)


# ================================================================================================== Gua Pertapa
def pertapa_serambi():
    """Start piece: the hidden garden court before the hermit's cave (25 x 16 x 26). Floor y1 = surface - 7; the stair
    climbs to the surface at the south edge; the cave mouth is in the north rock face."""
    t = G("gua", "gua_pertapa/serambi", 25, 16, 26, batu="batu")
    court, z0 = halaman_cekung(t, 12, 11, 10.5, 8.0, tangga_x=12, lebar_tangga=3)
    # the cliff face of the cave mouth and the mouth itself
    for x in range(6, 19):
        for y in range(FY, 13):
            for z in range(0, 3):
                t.rock(x, y, z, only_empty=False)
    mulut_gua(t, 12, 2, "south", lebar=3, tinggi=4, dalam=3)
    t.pintu(12, FY, 0, "north", pool_id("gua", "gua_pertapa", "lorong"), final="minecraft:tuff_bricks")
    for (x, z) in ((9, 4), (15, 4)):
        if (x, z) in court:
            t.lentera_batu(x, FY + 1, z)
    # a weathered plaque over the mouth
    for x in range(10, 15):
        t.put(x, FY + 7, 2, "minecraft:stripped_dark_oak_log", {"axis": "x"})
    t.put(12, FY + 7, 3, "minecraft:dark_oak_wall_sign", {"facing": "south", "waterlogged": "false"})
    # stepping-stone path from the stair to the mouth
    for z in range(4, z0 + 1):
        if (12, z) in court:
            t.put(12, FY, z, "minecraft:mossy_cobblestone" if z % 2 else "minecraft:cobblestone")
            for x in (11, 13):
                if (x, z) in court and t.rng.random() < 0.35:
                    t.put(x, FY, z, "minecraft:gravel")
    # raised herb beds bordered by bamboo blocks
    for (bx0, bz0, bx1, bz1) in ((4, 7, 8, 10), (16, 7, 20, 10)):
        for x in range(bx0, bx1 + 1):
            for z in range(bz0, bz1 + 1):
                if (x, z) not in court:
                    continue
                edge = x in (bx0, bx1) or z in (bz0, bz1)
                if edge:
                    t.put(x, FY + 1, z, "minecraft:stripped_bamboo_block", {"axis": "x" if z in (bz0, bz1) else "z"})
                else:
                    t.put(x, FY + 1, z, "minecraft:rooted_dirt" if t.rng.random() < 0.5 else "minecraft:moss_block")
                    t.put(x, FY + 2, z, t.rng.choice(BUNGA))
    # the pond with lily pads and a stone basin fed by a bamboo spout
    for x in range(15, 20):
        for z in range(13, 17):
            if (x, z) in court:
                t.put(x, FY - 1, z, "minecraft:clay")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
                if t.rng.random() < 0.3:
                    t.put(x, FY + 1, z, "minecraft:lily_pad")
    for x in range(14, 21):
        for z in range(12, 18):
            if (x, z) in court and t.get(x, FY, z) != "minecraft:water" and (x in (14, 20) or z in (12, 17)):
                t.put(x, FY, z, "minecraft:mossy_cobblestone")
    if (20, 14) in court:
        t.put(20, FY + 1, 14, "minecraft:water_cauldron", {"level": "3"})
        t.log(20, FY + 2, 15, "minecraft:bamboo_block", "y")
        t.log(20, FY + 3, 15, "minecraft:bamboo_block", "z")
    # the bamboo grove in the south-west corner
    for (x, z) in ((4, 14), (5, 16), (6, 15), (4, 17), (7, 17), (3, 15), (5, 13)):
        if (x, z) not in court:
            continue
        h = 4 + int(t.rng.random() * 3)
        for y in range(FY + 1, FY + 1 + h):
            leaves = "large" if y == FY + h else "small" if y == FY + h - 1 else "none"
            t.put(x, y, z, "minecraft:bamboo", {"age": "1" if y == FY + 1 else "0", "leaves": leaves, "stage": "0"})
    # the tea corner: a low table, two cushions, a teapot
    t.put(18, FY + 1, 11, "minecraft:spruce_trapdoor", {"facing": "north", "half": "top", "open": "false",
                                                         "powered": "false", "waterlogged": "false"})
    t.put(18, FY + 2, 11, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
    t.put(17, FY + 1, 11, "minecraft:green_carpet")
    t.put(19, FY + 1, 11, "minecraft:green_carpet")
    # a woodpile by the west wall
    for (x, y, z) in ((3, FY + 1, 9), (3, FY + 1, 10), (3, FY + 2, 9)):
        if (x, z) in court:
            t.log(x, y, z, "minecraft:spruce_log", "z")
    t.dandani(1, FY + 1, 1, 23, 10, 24, lumut=0.10, lichen=0.0, tetes=0.0, akar=0.0, jamur=0.02, sulur=0.12, semak=0.10,
              lantai_ok=lambda b: b in ("minecraft:grass_block", "minecraft:moss_block"))
    t.jangkar(12, FY + 1, 11, "gua/situs", situs="gua/pertapa", tier=1, protect=0)
    t.jangkar(12, FY + 1, z0, "gua/masuk", yaw=FACE_YAW["north"], nama="gua_pertapa", ranah="", protect=0)
    t.titik("kerja:cangkul", 6, FY + 2, 11, "north")
    t.titik("kerja:teko", 18, FY + 1, 12, "north")
    t.titik("kursi_lantai", 17, FY + 1, 11, "east")
    t.jalur([(12, z0, FY + 1), (12, 11, FY + 1), (12, 4, FY + 1)])
    t.simpan()
    return t.id


def pertapa_lorong():
    """The passage into the hill (11 x 13 x 18): level from the court, then a rock-cut stair four steps down, then a
    winding stretch to the hall; spruce frames, candle niches, roots. Entry floor y5, exit floor y1."""
    t = G("gua", "gua_pertapa/lorong", 11, 13, 18)
    t.lorong([(5, 17), (5, 12)], lebar=3, tinggi=4, lantai=FY + 4, lantai_blok="minecraft:coarse_dirt")
    t.lorong([(5, 12), (5, 7)], lebar=3, tinggi=8, lantai=FY, lantai_blok="minecraft:coarse_dirt")
    t.lorong([(5, 8), (6, 4), (5, 0)], lebar=3, tinggi=4, lantai=FY, lantai_blok="minecraft:coarse_dirt")
    for i, z in enumerate(range(11, 7, -1)):
        h = FY + 3 - i
        for x in (4, 5, 6):
            for y in range(FY, h):
                t.put(x, y, z, "minecraft:tuff_bricks" if y == h - 1 else t.rock_id())
            t.stairs(x, h, z, "minecraft:tuff_brick_stairs", "south")
            for y in range(h + 1, h + 5):
                t.set_air(x, y, z)
        # side walls of the cut stair
        for x in (3, 7):
            for y in range(FY, h + 5):
                if t.get(x, y, z) in (None, "minecraft:air"):
                    t.put(x, y, z, "minecraft:tuff_bricks" if y > h else t.rock_id())
    for z in range(0, 18):
        for x in range(3, 9):
            if t.get(x, FY, z) == "minecraft:coarse_dirt" and t.rng.random() < 0.35:
                t.put(x, FY, z, "minecraft:gravel" if t.rng.random() < 0.5 else "minecraft:tuff")
    for (z, y0, cx) in ((15, FY + 4, 5), (3, FY, 5)):
        for x in (cx - 2, cx + 2):
            for y in range(y0 + 1, y0 + 5):
                t.log(x, y, z, "minecraft:spruce_log", "y")
        for x in range(cx - 2, cx + 3):
            t.log(x, y0 + 5, z, "minecraft:spruce_log", "x")
    t.lentera_rantai(5, FY + 9, 10, 2)
    for (x, y, z) in ((3, FY + 6, 14), (7, FY + 2, 5)):
        t.set_air(x, y, z)
        t.lilin(x, y, z, 2, "white")
        t.put(x, y + 1, z, "minecraft:chiseled_tuff")
    t.dandani(2, FY + 1, 0, 9, 11, 17, lumut=0.08, lichen=0.03, tetes=0.0, akar=0.06, jamur=0.02)
    t.masuk(5, FY + 4, 17, "south", final="minecraft:coarse_dirt")
    t.pintu(5, FY, 0, "north", pool_id("gua", "gua_pertapa", "ruang"), final="minecraft:coarse_dirt")
    t.jalur([(5, 16, FY + 5), (5, 12, FY + 5), (5, 7, FY + 1), (5, 1, FY + 1)])
    t.simpan()
    return t.id


def pertapa_ruang():
    """The meditation hall (25 x 13 x 25): a domed natural chamber, the round dais with the hermit's cushion, a spring
    pool against the north wall, scroll shelves, incense and a tea corner. Anchor gua/pertapa."""
    t = G("gua", "gua_pertapa/ruang_meditasi", 25, 13, 25)
    t.rongga(12, 4.5, 12, 10.5, 5.0, 10.5, lantai=FY, tanah=("minecraft:stone", "minecraft:mossy_cobblestone", 0.25),
             kasar=0.18)
    t.rock_box(0, 0, 0, 24, 0, 24)
    t.lorong([(12, 24), (12, 19)], lebar=3, tinggi=4)
    t.lorong([(0, 12), (5, 12)], lebar=3, tinggi=4)
    t.lorong([(24, 12), (19, 12)], lebar=3, tinggi=4)
    t.disc(12, FY, 12, 4, "minecraft:polished_tuff")
    t.disc(12, FY, 12, 3, "minecraft:smooth_stone")
    t.ring(12, FY + 1, 12, 3, "minecraft:moss_carpet")
    for (x, z) in ((12, 9), (12, 15), (9, 12), (15, 12)):
        t.slab(x, FY + 1, z, "minecraft:polished_tuff_slab")
    t.put(12, FY + 1, 12, "minecraft:brown_carpet")
    t.put(12, FY + 1, 10, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    t.lilin(11, FY + 1, 10, 3, "white")
    t.lilin(13, FY + 1, 10, 2, "white")
    # the spring basin against the north wall, seeping from a crack above it
    for x in range(9, 16):
        for z in range(3, 7):
            t.put(x, FY - 1, z, "minecraft:clay")
            if x in (9, 15) or z in (3, 6):
                t.put(x, FY, z, "minecraft:mossy_stone_bricks")
            else:
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
            for y in range(FY + 1, FY + 4):
                t.set_air(x, y, z)
    t.put(12, FY + 5, 3, "minecraft:dripstone_block")
    t.stalaktit(12, FY + 4, 3, 2)
    t.put(11, FY, 4, "minecraft:small_dripleaf", {"facing": "south", "half": "lower", "waterlogged": "true"})
    t.put(11, FY + 1, 4, "minecraft:small_dripleaf", {"facing": "south", "half": "upper", "waterlogged": "false"})
    # scroll shelves on a cedar back wall to the west
    for z in range(8, 17):
        for y in range(FY + 1, FY + 5):
            t.put(2, y, z, "minecraft:spruce_planks")
            if z in (8, 16):
                t.log(3, y, z, "minecraft:stripped_spruce_log", "y")
            elif y <= FY + 3:
                t.rak_buku(3, y, z, "east")
            else:
                t.stairs(3, y, z, "minecraft:spruce_stairs", "west", half="top")
    t.put(4, FY + 1, 12, "minecraft:lectern", {"facing": "east", "has_book": "false", "powered": "false"})
    t.put(12, FY + 6, 3, "minecraft:white_wall_banner", {"facing": "south"}) if t.is_air(12, FY + 6, 3) else None
    for (x, z) in ((8, 8), (16, 8), (8, 16), (16, 16)):
        top = max(y for y in range(FY + 2, 12) if t.is_air(x, y, z))
        t.lentera_rantai(x, top + 1, z, 1)
    top = max(y for y in range(FY + 2, 12) if t.is_air(12, y, 12))
    t.put(12, top + 1, 12, "minecraft:shroomlight")
    # the tea corner to the east
    t.put(17, FY + 1, 15, "minecraft:spruce_trapdoor", {"facing": "west", "half": "top", "open": "false",
                                                         "powered": "false", "waterlogged": "false"})
    t.put(17, FY + 2, 15, "minecraft:flower_pot")
    t.put(18, FY + 1, 15, "minecraft:green_carpet")
    t.put(17, FY + 1, 16, "minecraft:green_carpet")
    t.put(19, FY + 1, 17, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.dandani(1, FY + 1, 1, 23, 11, 23, lumut=0.06, lichen=0.025, tetes=0.035, akar=0.01, jamur=0.005,
              lantai_ok=lambda b: b in ("minecraft:stone", "minecraft:mossy_cobblestone"))
    t.masuk(12, FY, 24, "south", final="minecraft:stone")
    t.pintu(0, FY, 12, "west", pool_id("gua", "gua_pertapa", "bilik"), final="minecraft:stone")
    t.pintu(24, FY, 12, "east", pool_id("gua", "gua_pertapa", "kebun"), final="minecraft:stone")
    t.jangkar(12, FY + 1, 12, "gua/pertapa", yaw=FACE_YAW["south"], protect=12, ruang=6)
    t.titik("meditasi", 12, FY + 1, 12, "north")
    t.titik("dupa", 12, FY + 1, 11, "north")
    t.titik("kursi_lantai", 18, FY + 1, 15, "west")
    t.titik("meja_tulis", 5, FY + 1, 12, "west")
    t.simpan()
    return t.id


def pertapa_bilik():
    """The sleeping cell (13 x 9 x 13): plank floor, a bed, the hermit's chest, a writing lectern, a clothes rack."""
    t = G("gua", "gua_pertapa/bilik_tidur", 13, 9, 13)
    t.rongga(6, 4, 6, 5.0, 3.2, 5.0, lantai=FY, tanah=("minecraft:spruce_planks", "minecraft:spruce_planks", 0.0),
             kasar=0.15)
    t.rock_box(0, 0, 0, 12, 0, 12)
    t.lorong([(12, 6), (9, 6)], lebar=3, tinggi=3)
    t.kasur(3, FY + 1, 4, "north", "brown")
    t.mat(4, FY + 1, 3, 5, 5, "minecraft:brown_carpet")
    t.peti(2, FY + 1, 7, "east", loot("pertapa_bilik"))
    t.tong(2, FY + 1, 8, "east")
    t.put(4, FY + 1, 9, "minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    t.lilin(5, FY + 1, 9, 1, "white")
    for y in (FY + 1, FY + 2):
        t.pagar(9, y, 3, "minecraft:spruce_fence")
    t.put(9, FY + 3, 3, "minecraft:gray_carpet")
    t.put(8, FY + 1, 9, "minecraft:potted_bamboo")
    t.lentera_rantai(6, max(y for y in range(FY + 2, 8) if t.is_air(6, y, 6)) + 1, 6, 1)
    for x in range(1, 12):
        for z in range(1, 12):
            if t.is_air(x, FY + 1, z) and t.get(x, FY, z) not in ("minecraft:spruce_planks",):
                t.put(x, FY, z, "minecraft:spruce_planks")
    t.dandani(1, FY + 1, 1, 11, 7, 11, lumut=0.0, lichen=0.02, tetes=0.0, akar=0.03, jamur=0.0)
    t.masuk(12, FY, 6, "east", final="minecraft:spruce_planks")
    t.titik("kasur", 3, FY + 1, 4, "north")
    t.titik("kursi", 4, FY + 1, 8, "south")
    t.simpan()
    return t.id


def pertapa_kebun():
    """The herb grotto (15 x 12 x 15): beds of spirit herbs under a light hole up to the surface, a water channel,
    glow berries, a stone mortar and a drying rack."""
    t = G("gua", "gua_pertapa/ruang_herba", 15, 14, 15)
    t.rongga(7, 4, 7, 6.0, 3.6, 6.0, lantai=FY, tanah=("minecraft:rooted_dirt", "minecraft:moss_block", 0.4), kasar=0.2)
    t.rock_box(0, 0, 0, 14, 0, 14)
    t.lorong([(0, 7), (3, 7)], lebar=3, tinggi=3)
    for x in range(4, 11):
        for z in range(4, 11):
            d = math.hypot(x - 7, z - 7)
            if d <= 1.8:
                for y in range(5, 14):
                    t.set_air(x, y, z)
            elif d <= 2.9:
                for y in range(7, 14):
                    if t.get(x, y, z) is None:
                        t.rock(x, y, z)
    for x in range(4, 11):
        for z in range(4, 11):
            d = math.hypot(x - 7, z - 7)
            if d <= 3.2 and t.is_air(x, FY + 1, z):
                t.put(x, FY, z, "minecraft:moss_block" if d <= 1.5 else "minecraft:rooted_dirt")
                if d > 1.0:
                    t.put(x, FY + 1, z, t.rng.choice(BUNGA + ["minecraft:azalea", "minecraft:flowering_azalea"]))
    t.put(7, FY + 1, 7, "minecraft:flowering_azalea")
    for x in range(3, 12):
        if t.is_air(x, FY + 1, 11):
            t.put(x, FY - 1, 11, "minecraft:clay")
            t.put(x, FY, 11, "minecraft:water", {"level": "0"})
            if x % 3 == 0:
                t.put(x, FY + 1, 11, "minecraft:big_dripleaf", {"facing": "north", "tilt": "none", "waterlogged": "false"})
    for (x, z) in ((4, 9), (10, 4), (5, 4), (9, 10)):
        top = max((y for y in range(FY + 2, 9) if t.is_air(x, y, z)), default=None)
        if top is not None and t.solid(x, top + 1, z):
            t.put(x, top, z, "minecraft:cave_vines", {"berries": "true", "age": "20"})
    t.put(11, FY + 1, 9, "minecraft:cauldron")
    t.put(11, FY + 1, 8, "minecraft:composter", {"level": "3"})
    t.pagar(3, FY + 1, 9, "minecraft:spruce_fence", ("south",))
    t.pagar(3, FY + 1, 10, "minecraft:spruce_fence", ("north",))
    t.put(3, FY + 2, 9, "minecraft:hay_block", {"axis": "z"})
    t.dandani(1, FY + 1, 1, 13, 9, 13, lumut=0.05, lichen=0.03, tetes=0.0, akar=0.05, jamur=0.02,
              lantai_ok=lambda b: b in ("minecraft:rooted_dirt", "minecraft:moss_block"))
    t.masuk(0, FY, 7, "west", final="minecraft:rooted_dirt")
    t.titik("kerja:sabit", 7, FY + 1, 9, "north")
    t.titik("kerja:lesung", 10, FY + 1, 9, "east")
    t.simpan()
    return t.id


# ================================================================================================== Makam Warisan
# Tier palettes (38.C.4: tier 1 Foundation, 2 Nascent Soul, 3 Dao Union): an old stone tomb, a deepslate tomb with
# gold and lacquer red, a blackstone and jade tomb of a great ancestor.
MAKAM_PAL = {
    1: {"wall": "minecraft:stone_bricks", "wall_alt": "minecraft:mossy_stone_bricks", "wall_crack": "minecraft:cracked_stone_bricks",
        "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:andesite", "inlay": "minecraft:chiseled_stone_bricks",
        "pillar": "minecraft:stripped_spruce_log", "trim": "minecraft:spruce_planks", "stair": "minecraft:stone_brick_stairs",
        "slab": "minecraft:stone_brick_slab", "accent": "minecraft:chiseled_stone_bricks", "glow": "minecraft:lantern",
        "carpet": "minecraft:brown_carpet", "metal": "minecraft:copper_block", "batu": "batu", "lilin": "white",
        "jade": "minecraft:mossy_cobblestone", "ceiling": "minecraft:stone_bricks", "wallb": "minecraft:stone_brick_wall"},
    2: {"wall": "minecraft:deepslate_tiles", "wall_alt": "minecraft:deepslate_bricks", "wall_crack": "minecraft:cracked_deepslate_tiles",
        "floor": "minecraft:polished_deepslate", "floor_alt": "minecraft:deepslate_tiles", "inlay": "minecraft:chiseled_deepslate",
        "pillar": "minecraft:red_terracotta", "trim": "minecraft:dark_oak_planks", "stair": "minecraft:deepslate_tile_stairs",
        "slab": "minecraft:deepslate_tile_slab", "accent": "minecraft:gold_block", "glow": "minecraft:lantern",
        "carpet": "minecraft:red_carpet", "metal": "minecraft:gold_block", "batu": "dalam", "lilin": "red",
        "jade": "minecraft:prismarine_bricks", "ceiling": "minecraft:deepslate_tiles", "wallb": "minecraft:deepslate_tile_wall"},
    3: {"wall": "minecraft:polished_blackstone_bricks", "wall_alt": "minecraft:polished_blackstone",
        "wall_crack": "minecraft:cracked_polished_blackstone_bricks", "floor": "minecraft:smooth_quartz",
        "floor_alt": "minecraft:polished_blackstone", "inlay": "minecraft:chiseled_polished_blackstone",
        "pillar": "minecraft:quartz_pillar", "trim": "minecraft:dark_prismarine", "stair": "minecraft:polished_blackstone_brick_stairs",
        "slab": "minecraft:polished_blackstone_brick_slab", "accent": "minecraft:gold_block", "glow": "minecraft:soul_lantern",
        "carpet": "minecraft:cyan_carpet", "metal": "minecraft:gold_block", "batu": "dalam", "lilin": "cyan",
        "jade": "minecraft:prismarine_bricks", "ceiling": "minecraft:polished_blackstone_bricks",
        "wallb": "minecraft:polished_blackstone_brick_wall"},
}
RANAH_MAKAM = {1: "foundation", 2: "nascent_soul", 3: "dao_union"}

# Mural motifs (7 wide x 3 high, top row first): the ancestor's life told on terracotta along the long corridor.
MURAL_PAL = {"K": "minecraft:yellow_terracotta", "M": "minecraft:red_terracotta", "H": "minecraft:black_terracotta",
             "P": "minecraft:white_terracotta", "B": "minecraft:light_blue_terracotta", "G": "minecraft:green_terracotta",
             "C": "minecraft:brown_terracotta", "U": "minecraft:purple_terracotta", "O": "minecraft:orange_terracotta",
             ".": "minecraft:terracotta"}
MURAL = [
    ("..BBB..", ".G.H.G.", "GGGHGGG"),   # a youth below the mountain
    ("...K...", "..HMH..", "..H.H.."),   # the first breath of qi
    ("B.....B", ".BPKPB.", "...H..."),   # meditation under the clouds
    ("M..P..M", ".M.P.M.", "..MPM.."),   # the sword path
    ("UU...UU", ".UKKKU.", "..UUU.."),   # the heavenly tribulation
    ("..OOO..", ".OMKMO.", "..OOO.."),   # the golden core
    ("P.B.B.P", ".PBKBP.", "..PPP.."),   # ascending to the sky
    (".HHHHH.", ".HCCCH.", ".HHHHH."),   # the tomb is sealed
]


def mural(t, x, y, z, along, motif, pal):
    """A 7 x 3 terracotta mural with its top-left at (x, y + 2, z), running along +x ("x") or +z ("z"), framed by the
    tier's trim above and below."""
    rows = MURAL[motif % len(MURAL)]
    for r, row in enumerate(rows):
        for i, ch in enumerate(row):
            px, pz = (x + i, z) if along == "x" else (x, z + i)
            t.put(px, y + 2 - r, pz, MURAL_PAL[ch])
    for i in range(-1, 8):
        px, pz = (x + i, z) if along == "x" else (x, z + i)
        t.put(px, y - 1, pz, pal["trim"])
        t.put(px, y + 3, pz, pal["trim"])


def kotak_ruang(t, x0, y0, z0, x1, y1, z1, pal, lantai=True):
    """A built rectangular room: walls/ceiling of the tier (mixed with its alt and cracked bricks), floor, air inside,
    and the rock mass just outside so the room never touches open cave."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                edge = x in (x0, x1) or z in (z0, z1) or y in (y0, y1)
                if edge:
                    if y == y0 and lantai:
                        t.put(x, y, z, pal["floor"] if t.rng.random() > 0.15 else pal["floor_alt"])
                    elif y == y1:
                        t.put(x, y, z, pal["ceiling"])
                    else:
                        r = t.rng.random()
                        t.put(x, y, z, pal["wall"] if r > 0.25 else pal["wall_alt"] if r > 0.08 else pal["wall_crack"])
                else:
                    t.set_air(x, y, z)
    t.rock_box(max(0, x0 - 1), max(0, y0 - 1), max(0, z0 - 1), min(t.size[0] - 1, x1 + 1), min(t.size[1] - 1, y1 + 1),
               min(t.size[2] - 1, z1 + 1))


def pintu_jalan(t, x, y, z, side, pal, lebar=3, tinggi=3):
    """Opens a doorway through the wall on `side` of a built room at (x, y = floor, z) with a carved frame."""
    half = lebar // 2
    ax, az = (1, 0) if side in ("north", "south") else (0, 1)
    for w in range(-half, half + 1):
        for h in range(1, tinggi + 1):
            t.set_air(x + ax * w, y + h, z + az * w)
        t.put(x + ax * w, y, z + az * w, pal["inlay"])
    for h in range(1, tinggi + 2):
        for w in (-half - 1, half + 1):
            t.put(x + ax * w, y + h, z + az * w, pal["pillar"] if h <= tinggi else pal["accent"])
    for w in range(-half, half + 1):
        t.put(x + ax * w, y + tinggi + 1, z + az * w, pal["accent"] if w == 0 else pal["trim"])


def tiang(t, x, y0, y1, z, pal):
    t.put(x, y0, z, pal["inlay"])
    for y in range(y0 + 1, y1):
        t.put(x, y, z, pal["pillar"], {"axis": "y"} if pal["pillar"].endswith(("_log", "_pillar")) else None)
    t.put(x, y1, z, pal["accent"])


def makam_stela(tier):
    """Start piece (15 x 18 x 15): the ancestor's stela on a two-step plinth in open land; behind it a stair goes down
    north into the earth (6 steps) to the stair hall. Plinth top y7 = surface + 1 (start_height -6, projected)."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/stela" % tier, 15, 18, 15, batu=pal["batu"])
    S = 6  # surface level in the template
    # clear above the plinth (trees, tall grass) and a foundation below it so a slope never leaves it floating
    for x in range(15):
        for z in range(15):
            for y in range(0, S):
                t.rock(x, y, z)
            for y in range(S + 1, 18):
                t.set_air(x, y, z)
    for x in range(1, 14):
        for z in range(1, 14):
            t.put(x, S, z, pal["floor_alt"] if t.rng.random() < 0.3 else pal["wall"])
    for x in range(3, 12):
        for z in range(3, 12):
            t.put(x, S + 1, z, pal["floor"])
    for i in range(3, 12):
        for (x, z, f) in ((i, 2, "south"), (i, 12, "north"), (2, i, "east"), (12, i, "west")):
            t.stairs(x, S + 1, z, pal["stair"], f)
    # the stair well behind the stela: open 3 wide from z 4 down northward through the plinth
    for i, z in enumerate(range(9, 2, -1)):
        h = S + 1 - i
        for x in (6, 7, 8):
            for y in range(h + 1, S + 2):
                t.set_air(x, y, z)
            if i < 6:
                t.stairs(x, h, z, pal["stair"], "south")
            else:
                t.put(x, h, z, pal["floor"])
        for x in (5, 9):
            for y in range(h, S + 2):
                t.put(x, y, z, pal["wall"])
    for z in range(0, 3):
        for x in (6, 7, 8):
            for y in range(FY + 1, FY + 4):
                t.set_air(x, y, z)
            t.put(x, FY, z, pal["floor"])
        for x in (5, 9):
            for y in range(FY, FY + 5):
                t.put(x, y, z, pal["wall"])
        for x in (5, 6, 7, 8, 9):
            t.put(x, FY + 4, z, pal["ceiling"])
    # balustrade around the well mouth
    for x in (5, 9):
        for z in range(4, 10):
            t.dinding_tiang(x, S + 2, z, pal["wallb"], up=(z in (4, 9)))
    for x in range(5, 10):
        t.dinding_tiang(x, S + 2, 3, pal["wallb"], up=(x in (5, 9)))
    # the stela: plinth block, a tall carved slab, a little roof
    for x in (6, 7, 8):
        t.put(x, S + 2, 11, pal["accent"] if x == 7 else pal["inlay"])
        for y in range(S + 3, S + 8):
            t.put(x, y, 11, pal["wall"] if x != 7 else (pal["inlay"] if y % 2 else pal["wall_alt"]))
    t.put(7, S + 8, 11, pal["accent"])
    for x in range(5, 10):
        t.stairs(x, S + 9, 10, pal["stair"], "south")
        t.stairs(x, S + 9, 12, pal["stair"], "north")
        t.slab(x, S + 10, 11, pal["slab"])
    # paired guardian lanterns and incense before the stela
    for x in (4, 10):
        t.dinding_tiang(x, S + 2, 12, pal["wallb"], True)
        t.lentera(x, S + 3, 12, False, pal["glow"])
    t.lilin(7, S + 2, 12, 3, pal["lilin"])
    t.put(6, S + 2, 12, "minecraft:flower_pot")
    t.pintu(7, FY, 0, "north", pool_id("gua", "makam_warisan_%d" % tier, "tangga"), final=pal["floor"])
    t.jangkar(7, S + 2, 12, "gua/situs", situs="gua/makam", tier=tier, protect=0)
    t.jangkar(7, S + 2, 6, "gua/masuk", yaw=FACE_YAW["north"], nama="makam_warisan", ranah=RANAH_MAKAM[tier], tier=tier,
              protect=0)
    t.titik("dupa", 7, S + 2, 13, "north")
    t.simpan()
    return t.id


def makam_tangga(tier):
    """The stair hall (11 x 18 x 14): a switchback down 14 blocks under the stela: a flight north, a landing with a
    tablet niche, a flight back south; pillars, hanging lanterns. Entry and exit both on the south face."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/tangga" % tier, 11, 20, 14, batu=pal["batu"])
    kotak_ruang(t, 0, 0, 0, 10, 19, 13, pal)
    # dividing wall between the two flights (the landing at z 1..4 stays open)
    for z in range(5, 13):
        for y in range(1, 19):
            t.put(5, y, z, pal["wall"] if t.rng.random() > 0.2 else pal["wall_alt"])
    # flight A (x 1..4, entry at z 13 top y 15, going north down to y 8 at z 4)
    for i in range(8):
        z = 12 - i
        h = 15 - i
        for x in range(1, 5):
            for y in range(1, h):
                t.put(x, y, z, pal["wall"] if y < h - 1 else pal["floor_alt"])
            t.stairs(x, h, z, pal["stair"], "south") if i > 0 else t.put(x, h, z, pal["floor"])
            for y in range(h + 1, 19):
                t.set_air(x, y, z)
    for x in range(1, 5):
        for y in range(16, 19):
            t.set_air(x, y, 13)
    # landing (z 1..4, y 8)
    for x in range(1, 10):
        for z in range(1, 5):
            for y in range(1, 8):
                t.put(x, y, z, pal["wall"])
            t.put(x, 8, z, pal["floor"] if (x + z) % 3 else pal["inlay"])
            for y in range(9, 19):
                t.set_air(x, y, z)
    # flight B (x 6..9 from z 4 back south down to y 1 at z 12)
    for i in range(8):
        z = 5 + i
        h = 7 - i
        for x in range(6, 10):
            for y in range(1, max(1, h)):
                t.put(x, y, z, pal["wall"])
            if h >= 1:
                t.stairs(x, h, z, pal["stair"], "north") if h > 1 else t.put(x, 1, z, pal["floor"])
            for y in range(max(2, h + 1), 19):
                t.set_air(x, y, z)
    for x in range(6, 10):
        t.put(x, 1, 13, pal["floor"])
        for y in range(2, 6):
            t.set_air(x, y, 13)
    # the tablet niche on the landing and lanterns over the flights
    t.put(5, 10, 1, pal["accent"])
    t.put(5, 11, 1, pal["inlay"])
    t.lilin(4, 9, 1, 2, pal["lilin"])
    t.lilin(6, 9, 1, 2, pal["lilin"])
    t.lentera_rantai(3, 19, 8, 2, pal["glow"])
    t.lentera_rantai(7, 19, 9, 6, pal["glow"])
    t.lentera_rantai(5, 19, 3, 4, pal["glow"])
    t.masuk(2, 15, 13, "south", final=pal["floor"])
    t.pintu(8, 1, 13, "south", pool_id("gua", "makam_warisan_%d" % tier, "lorong_mural"), final=pal["floor"])
    t.simpan()
    return t.id


def makam_lorong_mural(tier):
    """The mural corridor (11 x 9 x 23): the ancestor's life in eight terracotta panels, pillars, beams and lanterns;
    the realm seal door stands near the entry (anchor gua/makam_penghalang; the door is an entity)."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/lorong_mural" % tier, 11, 9, 23, batu=pal["batu"])
    kotak_ruang(t, 1, 0, 0, 9, 7, 22, pal)
    for z in range(1, 22):
        for x in range(2, 9):
            t.put(x, 0, z, pal["floor"] if x not in (2, 8) else pal["floor_alt"])
        t.put(5, 0, z, pal["inlay"] if z % 2 else pal["floor"])
    # seal frame at z 19 (the entity door fills the opening)
    for x in range(2, 9):
        for y in range(1, 7):
            if not (4 <= x <= 6 and y <= 4):
                t.put(x, y, 19, pal["wall"])
    for y in range(1, 6):
        t.put(3, y, 19, pal["pillar"], {"axis": "y"} if pal["pillar"].endswith(("_log", "_pillar")) else None)
        t.put(7, y, 19, pal["pillar"], {"axis": "y"} if pal["pillar"].endswith(("_log", "_pillar")) else None)
    for x in range(3, 8):
        t.put(x, 5, 19, pal["accent"])
    for k, z in enumerate((3, 11)):
        mural(t, 1, 2, z, "z", k * 2 + tier, pal)
        mural(t, 9, 2, z, "z", k * 2 + 1 + tier, pal)
    for z in (2, 10, 18):
        for x in (2, 8):
            tiang(t, x, 0, 6, z, pal)
        for x in range(2, 9):
            t.put(x, 6, z, pal["trim"])
    for z in (6, 14):
        t.lentera_rantai(5, 7, z, 1, pal["glow"])
    pintu_jalan(t, 5, 0, 22, "south", pal)
    pintu_jalan(t, 5, 0, 0, "north", pal)
    t.masuk(5, 0, 22, "south", final=pal["floor"])
    t.pintu(5, 0, 0, "north", pool_id("gua", "makam_warisan_%d" % tier, "ruang_hati"), final=pal["floor"])
    # door 2 blocks north of the anchor (local z - 2): the seal fills x 4..6, y 1..4 at z 19
    t.jangkar(5, 1, 21, "gua/makam_penghalang", yaw=FACE_YAW["south"], tier=tier, protect=6, px=0, pz=-2)
    t.simpan()
    return t.id


def makam_hati(tier):
    """The Heart trial room (17 x 11 x 17): a dark mirror floor, three illusion daises (the three stages of the trial),
    incense and a soul lamp; the remnant soul appears in the middle (anchor gua/makam_hati). Exit door to the east."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/ruang_hati" % tier, 17, 11, 17, batu=pal["batu"])
    kotak_ruang(t, 0, 0, 0, 16, 9, 16, pal)
    for x in range(2, 15):
        for z in range(2, 15):
            d = math.hypot(x - 8, z - 8)
            t.put(x, 0, z, "minecraft:black_concrete" if d < 4.5 else "minecraft:polished_blackstone" if d < 5.5 else pal["floor"])
    t.ring(8, 0, 8, 5, pal["inlay"])
    # three daises: past, present, future (the soul's three questions)
    for (x, z) in ((4, 4), (12, 4), (8, 13)):
        t.put(x, 1, z, pal["inlay"])
        t.slab(x, 2, z, pal["slab"])
        t.lilin(x, 3, z, 3, pal["lilin"])
    for (x, z) in ((2, 2), (14, 2), (2, 14), (14, 14)):
        tiang(t, x, 0, 8, z, pal)
    t.lentera_rantai(8, 9, 8, 2, "minecraft:soul_lantern")
    t.put(8, 1, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "true", "waterlogged": "false"})
    for z in range(5, 12):
        t.put(1, 4, z, pal["trim"])
    mural(t, 1, 3, 5, "z", 2 + tier, pal) if tier >= 2 else None
    pintu_jalan(t, 8, 0, 16, "south", pal)
    pintu_jalan(t, 16, 0, 8, "east", pal)
    t.masuk(8, 0, 16, "south", final=pal["floor"])
    t.pintu(16, 0, 8, "east", pool_id("gua", "makam_warisan_%d" % tier, "lorong_a"), final=pal["floor"])
    t.jangkar(8, 1, 8, "gua/makam_hati", yaw=FACE_YAW["south"], tier=tier, protect=8, px=8, pz=0, pyaw=-90)
    t.kotak("panggung", 4, 1, 4, 12, 1, 12)
    t.simpan()
    return t.id


def makam_lorong(tier, nama, ke):
    """A short connecting gallery (9 x 7 x 11) between two trial rooms, with incense niches and a lantern."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/%s" % (tier, nama), 9, 7, 11, batu=pal["batu"])
    kotak_ruang(t, 1, 0, 0, 7, 5, 10, pal)
    for z in range(1, 10):
        t.put(4, 0, z, pal["inlay"] if z % 3 == 0 else pal["floor"])
    for z in (3, 7):
        t.set_air(1, 2, z)
        t.lilin(1, 2, z, 2, pal["lilin"])
        t.set_air(7, 2, z)
        t.lilin(7, 2, z, 1, pal["lilin"])
    t.lentera(4, 4, 5, True, pal["glow"])
    pintu_jalan(t, 4, 0, 10, "south", pal)
    pintu_jalan(t, 4, 0, 0, "north", pal)
    t.masuk(4, 0, 10, "south", final=pal["floor"])
    t.pintu(4, 0, 0, "north", pool_id("gua", "makam_warisan_%d" % tier, ke), final=pal["floor"])
    t.simpan()
    return t.id


def makam_kekuatan(tier):
    """The Strength trial hall (21 x 12 x 21): a sanded duel floor ringed by pillars, four wooden guardian puppets
    seated cross-legged on plinths (the anchor makes them; 2-4 rise), weapon racks. Exit door to the west."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/ruang_kekuatan" % tier, 21, 12, 21, batu=pal["batu"])
    kotak_ruang(t, 0, 0, 0, 20, 10, 20, pal)
    for x in range(2, 19):
        for z in range(2, 19):
            d = math.hypot(x - 10, z - 10)
            t.put(x, 0, z, "minecraft:smooth_sandstone" if d < 6.5 else pal["floor"])
    t.ring(10, 0, 10, 7, pal["inlay"])
    for (x, z) in ((4, 4), (16, 4), (4, 16), (16, 16)):
        tiang(t, x, 0, 9, z, pal)
        t.lentera(x, 8, z + (1 if z < 10 else -1), True, pal["glow"])
    # puppet plinths at the four sides (local offsets read by the anchor)
    for (x, z) in ((10, 3), (17, 10), (10, 17), (3, 10)):
        t.put(x, 0, z, pal["accent"])
        t.mat(x, 1, z, x, z, pal["carpet"])
    # weapon racks: fences with wooden swords (oak fences, armour-stand free)
    for (x, z, ax) in ((1, 6, "z"), (19, 14, "z")):
        for k in range(3):
            zz = z + k
            t.pagar(x, 1, zz, "minecraft:dark_oak_fence")
            t.put(x, 2, zz, "minecraft:dark_oak_trapdoor", {"facing": "east" if x == 1 else "west", "half": "bottom",
                                                             "open": "true", "powered": "false", "waterlogged": "false"})
    t.lentera_rantai(10, 10, 10, 2, pal["glow"])
    pintu_jalan(t, 10, 0, 20, "south", pal)
    pintu_jalan(t, 0, 0, 10, "west", pal)
    t.masuk(10, 0, 20, "south", final=pal["floor"])
    t.pintu(0, 0, 10, "west", pool_id("gua", "makam_warisan_%d" % tier, "lorong_b"), final=pal["floor"])
    # seats (local offsets from the anchor at the centre): N, E, S, W plinths; door 10 west (local x -10)
    t.jangkar(10, 1, 10, "gua/makam_kekuatan", yaw=FACE_YAW["south"], tier=tier, protect=10, px=-10, pz=0, pyaw=90,
              kursi="0,-7;7,0;0,7;-7,0")
    t.kotak("panggung", 4, 1, 4, 16, 1, 16)
    t.simpan()
    return t.id


def makam_pemahaman(tier):
    """The Comprehension trial room (17 x 11 x 17): five glyph sockets in a ring (the plates are entities turned by
    hand), the five-element cycle carved on the walls, shelves of bamboo slips. Exit door to the east."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/ruang_pemahaman" % tier, 17, 11, 17, batu=pal["batu"])
    kotak_ruang(t, 0, 0, 0, 16, 9, 16, pal)
    for x in range(2, 15):
        for z in range(2, 15):
            t.put(x, 0, z, pal["floor"] if (x + z) % 4 else pal["floor_alt"])
    t.disc(8, 0, 8, 2, pal["inlay"])
    socket = []
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        x, z = int(round(8 + math.cos(a) * 4.5)), int(round(8 + math.sin(a) * 4.5))
        t.put(x, 0, z, pal["accent"])
        socket.append((x - 8, z - 8))
    # five-element glyph tiles on the walls (fire, water, wood, metal, earth)
    tiles = ["minecraft:red_glazed_terracotta", "minecraft:blue_glazed_terracotta", "minecraft:green_glazed_terracotta",
             "minecraft:white_glazed_terracotta", "minecraft:brown_glazed_terracotta"]
    for k, bid in enumerate(tiles):
        z = 4 + k * 2
        t.put(1, 3, z, bid, {"facing": "east"})
        t.put(15, 3, 16 - z, bid, {"facing": "west"})
    for x in range(3, 14):
        if x in (7, 8, 9):
            continue
        t.rak_buku(x, 1, 1, "south")
        t.rak_buku(x, 2, 1, "south")
    t.put(8, 1, 8, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    for (x, z) in ((3, 3), (13, 3), (3, 13), (13, 13)):
        t.lentera(x, 1, z, False, pal["glow"])
    pintu_jalan(t, 8, 0, 16, "south", pal)
    pintu_jalan(t, 16, 0, 8, "east", pal)
    t.masuk(8, 0, 16, "south", final=pal["floor"])
    t.pintu(16, 0, 8, "east", pool_id("gua", "makam_warisan_%d" % tier, "lorong_c"), final=pal["floor"])
    t.jangkar(8, 1, 8, "gua/makam_pemahaman", yaw=FACE_YAW["south"], tier=tier, protect=8, px=8, pz=0, pyaw=-90,
              lempeng=";".join("%d,%d" % s for s in socket))
    t.simpan()
    return t.id


def makam_aula(tier):
    """The tomb hall (25 x 16 x 25): the ancestor's sarcophagus on a three-step dais, spirit tablets, a statue in
    meditation behind it, offering tables, treasure niches with the tomb's chests, banners and lanterns. The remnant
    soul gives the inheritance here (anchor gua/makam_aula)."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/aula" % tier, 25, 16, 25, batu=pal["batu"])
    kotak_ruang(t, 0, 0, 0, 24, 14, 24, pal)
    for x in range(1, 24):
        for z in range(1, 24):
            t.put(x, 0, z, pal["floor"] if (x % 4 and z % 4) else pal["floor_alt"])
    for z in range(13, 24):
        t.put(12, 0, z, pal["inlay"])
        t.put(11, 0, z, pal["carpet"].replace("_carpet", "_wool"))
        t.put(13, 0, z, pal["carpet"].replace("_carpet", "_wool"))
    # the dais and the sarcophagus
    for step, (r, y) in enumerate(((6, 1), (5, 2), (4, 3))):
        for x in range(12 - r, 13 + r):
            for z in range(7 - r + 2, 7 + r - 1):
                t.put(x, y, z, pal["wall"] if step < 2 else pal["floor"])
    for x in range(10, 15):
        for z in range(5, 9):
            t.put(x, 4, z, pal["inlay"] if x in (10, 14) or z in (5, 8) else pal["accent"])
    for x in range(10, 15):
        t.slab(x, 5, 6, pal["slab"])
        t.slab(x, 5, 7, pal["slab"])
    # the statue behind the dais: a seated figure of blocks
    for y in range(1, 9):
        t.put(12, y, 2, pal["jade"] if 3 < y < 7 else pal["wall_alt"])
    for x in (11, 13):
        t.put(x, 3, 2, pal["jade"])
        t.put(x, 5, 3, pal["jade"])
    t.put(12, 8, 2, pal["accent"])
    t.put(12, 9, 2, pal["metal"])
    # spirit tablets along the side walls and offering tables
    for x in (2, 22):
        for z in range(4, 21, 2):
            t.slab(x, 1, z, pal["slab"], "top")
            t.put(x, 2, z, pal["inlay"])
            t.lilin(x, 3, z, 1 + z % 3, pal["lilin"])
    for (x, z) in ((7, 12), (17, 12)):
        t.put(x, 1, z, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                        "powered": "false", "waterlogged": "false"})
        t.put(x, 2, z, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    # treasure niches with the tomb's chests (vanilla loot, once per world; the inheritance is personal)
    for (x, z, f) in ((1, 9, "east"), (23, 9, "west")):
        for y in (1, 2, 3):
            t.set_air(x, y, z)
        t.peti(x, 1, z, f, loot("makam_t%d" % tier))
        t.put(x, 4, z, pal["accent"])
    for (x, z) in ((4, 4), (20, 4), (4, 20), (20, 20), (4, 12), (20, 12)):
        tiang(t, x, 0, 13, z, pal)
    for (x, z) in ((8, 16), (16, 16), (12, 10)):
        t.lentera_rantai(x, 14, z, 3, pal["glow"])
    for x in (6, 18):
        t.put(x, 9, 1, "minecraft:%s_wall_banner" % ("red" if tier == 2 else "white" if tier == 1 else "cyan"),
              {"facing": "south"})
    pintu_jalan(t, 12, 0, 24, "south", pal, lebar=3, tinggi=4)
    t.masuk(12, 0, 24, "south", final=pal["floor"])
    t.jangkar(12, 1, 13, "gua/makam_aula", yaw=FACE_YAW["south"], tier=tier, protect=12)
    t.kotak("panggung", 7, 4, 4, 17, 4, 10)
    t.titik("dupa", 12, 1, 12, "north")
    t.simpan()
    return t.id


def makam_semua(tier):
    ids = {"stela": makam_stela(tier), "tangga": makam_tangga(tier), "lorong_mural": makam_lorong_mural(tier),
           "ruang_hati": makam_hati(tier), "lorong_a": makam_lorong(tier, "lorong_a", "ruang_kekuatan"),
           "ruang_kekuatan": makam_kekuatan(tier), "lorong_b": makam_lorong(tier, "lorong_b", "ruang_pemahaman"),
           "ruang_pemahaman": makam_pemahaman(tier), "lorong_c": makam_lorong(tier, "lorong_c", "aula"),
           "aula": makam_aula(tier)}
    return ids


# ================================================================================================== Sarang Binatang
def daun(t, x, y, z, bid="minecraft:dark_oak_leaves"):
    if t.inside(x, y, z) and t.get(x, y, z) is None:
        t.put(x, y, z, bid, {"distance": "1", "persistent": "true", "waterlogged": "false"})


def tulang(t, x, y, z):
    """A scatter of bones on a floor cell: bone block lying, a skull, or bone meal-coloured calcite chips."""
    r = t.rng.random()
    if r < 0.45:
        t.put(x, y, z, "minecraft:bone_block", {"axis": t.rng.choice(["x", "z"])})
    elif r < 0.7:
        t.tengkorak(x, y, z, int(t.rng.random() * 16))
    else:
        t.put(x, y, z, "minecraft:white_carpet")


def sarang_pohon():
    """The hollow giant tree (25 x 38 x 25, start piece, base at y 8 = surface): a 9 x 9 dark oak trunk hollow inside,
    flaring roots, four great boughs and a dark canopy; the beast sleeps in a nest of hay and bones inside the trunk and
    a root cellar below it. Anchors gua/situs, gua/masuk, gua/sarang_bos."""
    t = G("gua", "sarang_binatang/pohon", 25, 38, 25, batu="tanah")
    B = 8          # ground level (start_height -8 like the den court, so both start pieces share one structure)
    cx, cz = 12, 12
    # the trunk: a ring of logs 9 wide (bark = dark_oak_wood), hollow 7 x 7, tapering after y 20
    for y in range(B - 3, 31):
        r = 4.6 if y < 24 else 4.6 - (y - 24) * 0.45
        for x in range(cx - 6, cx + 7):
            for z in range(cz - 6, cz + 7):
                d = math.hypot(x - cx, z - cz) + (noise3(x, y, z, 3.0, t.nseed) - 0.5) * 0.8
                if d <= r:
                    inner = d <= r - 1.4 and y < 22
                    if y < B:
                        t.put(x, y, z, "minecraft:rooted_dirt" if inner else "minecraft:dark_oak_wood", {"axis": "y"} if not inner else None)
                    elif inner:
                        t.set_air(x, y, z)
                    else:
                        t.put(x, y, z, "minecraft:dark_oak_wood" if t.rng.random() < 0.7 else "minecraft:dark_oak_log", {"axis": "y"})
    # roots flaring out over the ground
    for k in range(8):
        a = k * math.pi / 4 + 0.3
        for s in range(5, 11):
            x, z = int(round(cx + math.cos(a) * s)), int(round(cz + math.sin(a) * s))
            y = B - (1 if s > 8 else 0)
            axis = "x" if abs(math.cos(a)) > 0.7 else "z" if abs(math.sin(a)) > 0.7 else "y"
            t.put(x, y, z, "minecraft:dark_oak_log", {"axis": axis})
            if s < 8:
                t.put(x, y + 1, z, "minecraft:dark_oak_wood", {"axis": "y"})
            for y2 in range(0, y):
                t.rock(x, y2, z)
    # the den mouth on the south side: a ragged opening 3 wide, 4 high
    for x in range(cx - 1, cx + 2):
        for y in range(B, B + 4):
            for z in range(cz + 3, cz + 7):
                if not (y == B + 3 and x != cx):
                    t.set_air(x, y, z)
        t.put(x, B - 1, cz + 5, "minecraft:coarse_dirt")
        t.put(x, B - 1, cz + 6, "minecraft:coarse_dirt")
    # inside: floor of trampled earth, the nest, bones, claw-scored walls
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            if t.is_air(x, B, z):
                t.put(x, B - 1, z, "minecraft:podzol" if t.rng.random() < 0.6 else "minecraft:coarse_dirt")
    for (x, z) in ((cx - 1, cz - 1), (cx, cz - 1), (cx + 1, cz - 1), (cx - 1, cz), (cx, cz), (cx + 1, cz), (cx, cz - 2)):
        t.put(x, B, z, "minecraft:hay_block", {"axis": "y"})
    for (x, z) in ((cx - 2, cz + 1), (cx + 2, cz - 2), (cx - 3, cz - 2), (cx + 3, cz + 2), (cx + 2, cz + 3)):
        if t.is_air(x, B, z):
            tulang(t, x, B, z)
    for y in range(B + 3, 21, 3):
        for (x, z) in ((cx - 3, cz), (cx + 3, cz), (cx, cz - 3)):
            if t.is_air(x, y, z):
                t.tempel(x, y, z, "minecraft:glow_lichen") if t.rng.random() < 0.5 else t.sulur(x, y, z, 2)
    # the root cellar under the nest (a trap door of roots leads down)
    t.rongga(cx, B - 3.5, cz - 1, 4.5, 2.4, 4.0, lantai=B - 6 + 1, tanah=("minecraft:podzol", "minecraft:coarse_dirt", 0.4))
    for y in range(B - 4, B + 1):
        t.put(cx + 2, y, cz - 3, "minecraft:dark_oak_log", {"axis": "y"})
        t.put(cx + 2, y, cz - 2, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    for (x, z) in ((cx - 2, cz - 2), (cx + 1, cz), (cx - 3, cz)):
        if t.is_air(x, B - 4, z):
            tulang(t, x, B - 4, z)
    for (x, z) in ((cx, cz - 3), (cx - 3, cz + 1)):
        t.akar(x, B - 2, z, 2)
    t.peti(cx + 3, B - 4, cz - 2, "west", loot("sarang_binatang"))
    # four great boughs and the canopy
    for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        y0 = 21 + k
        for s in range(4, 11):
            x, z = cx + dx * s, cz + dz * s
            y = y0 + s // 3
            t.put(x, y, z, "minecraft:dark_oak_log", {"axis": "x" if dx else "z"})
            if s == 10:
                for ddx in range(-4, 5):
                    for ddz in range(-4, 5):
                        for ddy in range(-1, 3):
                            if math.hypot(ddx, ddz) + abs(ddy) * 1.3 < 4.4 + t.rng.random():
                                daun(t, x + ddx, y + ddy, z + ddz)
    for x in range(0, 25):
        for z in range(0, 25):
            for y in range(28, 37):
                d = math.hypot(x - cx, z - cz) + abs(y - 32) * 1.6
                if d < 9.5 + (noise3(x, y, z, 4.0, t.nseed + 3) - 0.5) * 3:
                    daun(t, x, y, z)
    for x in range(0, 25):
        for z in range(0, 25):
            for y in range(18, 37):
                if t.get(x, y, z) == "minecraft:dark_oak_leaves" and t.is_air(x, y - 1, z) is False and t.get(x, y - 1, z) is None \
                        and t.rng.random() < 0.06:
                    for i in range(1, 4):
                        if t.get(x, y - i, z) is None:
                            t.put(x, y - i, z, "minecraft:vine", {"north": "false", "south": "false", "east": "false",
                                                                  "west": "false", "up": "true"})
                        break
    # clear a ring of open ground around the trunk (no tree trunks through the roots)
    for x in range(25):
        for z in range(25):
            if t.get(x, B, z) is None and math.hypot(x - cx, z - cz) < 11:
                for y in range(B, 18):
                    if t.get(x, y, z) is None:
                        t.set_air(x, y, z)
    t.jangkar(cx, B, cz + 8, "gua/situs", situs="gua/sarang_binatang", tier=1, protect=0)
    t.jangkar(cx, B, cz + 9, "gua/masuk", yaw=FACE_YAW["north"], nama="sarang_binatang", ranah="auto", protect=0)
    t.jangkar(cx, B + 1, cz, "gua/sarang_bos", yaw=FACE_YAW["south"], protect=0, sarang=3)
    t.titik("jaga", cx, B, cz + 4, "south")
    t.simpan()
    return t.id


def sarang_liang():
    """The den cave start (23 x 16 x 24): a sunken clearing of trampled earth and gnawed logs before a dark den mouth in a
    bank of earth and rock; claw-scored logs, a carcass of bones. Exit north into the den."""
    t = G("gua", "sarang_binatang/liang", 23, 16, 24, batu="tanah")
    court, z0 = halaman_cekung(t, 11, 11, 9.0, 7.5, tangga_x=11, lebar_tangga=3,
                               tanah=("minecraft:coarse_dirt", "minecraft:podzol", 0.45),
                               tangga_blok=("minecraft:cobblestone_stairs", "minecraft:mossy_cobblestone_stairs"),
                               pijak="minecraft:coarse_dirt", pagar="minecraft:cobblestone_wall")
    for x in range(5, 18):
        for y in range(FY, 12):
            for z in range(0, 3):
                t.rock(x, y, z, only_empty=False)
    # a ragged den mouth (no carved frame): air, rubble, roots
    for depth in range(0, 3):
        for w in range(-2, 3):
            for y in range(FY + 1, FY + 5 - (1 if abs(w) == 2 else 0)):
                t.set_air(11 + w, y, 2 - depth)
            t.put(11 + w, FY, 2 - depth, "minecraft:coarse_dirt")
    t.pintu(11, FY, 0, "north", pool_id("gua", "sarang_binatang", "sarang"), final="minecraft:coarse_dirt")
    for (x, z) in ((7, 5), (15, 7), (8, 13), (14, 14), (12, 9)):
        if (x, z) in court:
            tulang(t, x, FY + 1, z)
    for (x, z, a) in ((5, 9, "z"), (6, 9, "z"), (17, 11, "x"), (16, 11, "x")):
        if (x, z) in court:
            t.log(x, FY + 1, z, "minecraft:stripped_dark_oak_log", a)
    for (x, z) in ((6, 4), (16, 4)):
        t.akar(x, FY + 5, z, 2) if t.solid(x, FY + 6, z) else None
    t.dandani(1, FY + 1, 1, 21, 10, 22, lumut=0.04, lichen=0.0, tetes=0.0, akar=0.0, jamur=0.04, sulur=0.08, semak=0.12,
              lantai_ok=lambda b: b in ("minecraft:coarse_dirt", "minecraft:podzol"))
    t.jangkar(11, FY + 1, 10, "gua/situs", situs="gua/sarang_binatang", tier=1, protect=0)
    t.jangkar(11, FY + 1, z0, "gua/masuk", yaw=FACE_YAW["north"], nama="sarang_binatang", ranah="auto", protect=0)
    t.simpan()
    return t.id


def sarang_dalam():
    """The den chamber (25 x 12 x 25): a low wide cave of packed earth, a great nest of hay and bones in the middle,
    gnawed carcasses, scratched walls, hanging roots, a pool of dark water; the boss sleeps on the nest. Floor y1."""
    t = G("gua", "sarang_binatang/sarang", 25, 12, 25, batu="tanah")
    t.rongga(12, 3.8, 12, 10.5, 3.6, 10.5, lantai=FY, tanah=("minecraft:coarse_dirt", "minecraft:podzol", 0.5), kasar=0.3)
    t.rock_box(0, 0, 0, 24, 0, 24)
    t.lorong([(12, 24), (12, 20)], lebar=4, tinggi=4)
    for x in range(8, 17):
        for z in range(8, 17):
            d = math.hypot(x - 12, z - 12)
            if d < 3.6:
                t.put(x, FY, z, "minecraft:hay_block", {"axis": "y"})
                if d > 2.2 and t.rng.random() < 0.5:
                    t.put(x, FY + 1, z, "minecraft:hay_block", {"axis": t.rng.choice(["x", "z"])})
            elif d < 5.5 and t.rng.random() < 0.25 and t.is_air(x, FY + 1, z):
                tulang(t, x, FY + 1, z)
    for x in range(3, 8):
        for z in range(15, 19):
            if t.is_air(x, FY + 1, z) and math.hypot(x - 5, z - 17) < 2.4:
                t.put(x, FY - 1, z, "minecraft:mud")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
    t.peti(19, FY + 1, 7, "west", loot("sarang_binatang"))
    t.dandani(1, FY + 1, 1, 23, 10, 23, lumut=0.03, lichen=0.02, tetes=0.0, akar=0.08, jamur=0.03,
              lantai_ok=lambda b: b in ("minecraft:coarse_dirt", "minecraft:podzol"))
    t.masuk(12, FY, 24, "south", final="minecraft:coarse_dirt")
    t.jangkar(12, FY + 2, 12, "gua/sarang_bos", yaw=FACE_YAW["south"], protect=0, sarang=5)
    t.titik("jaga", 12, FY + 2, 12, "south")
    t.simpan()
    return t.id


# ================================================================================================== Sarang Kultivator Iblis
IBLIS = {"wall": "minecraft:polished_blackstone_bricks", "wall_alt": "minecraft:cracked_polished_blackstone_bricks",
         "floor": "minecraft:polished_blackstone", "floor_alt": "minecraft:blackstone", "accent": "minecraft:crying_obsidian",
         "pillar": "minecraft:red_nether_bricks", "trim": "minecraft:crimson_hyphae", "glow": "minecraft:soul_lantern",
         "stair": "minecraft:polished_blackstone_brick_stairs", "slab": "minecraft:polished_blackstone_brick_slab",
         "carpet": "minecraft:black_carpet", "inlay": "minecraft:chiseled_polished_blackstone",
         "wallb": "minecraft:polished_blackstone_brick_wall", "ceiling": "minecraft:blackstone", "wall_crack":
             "minecraft:cracked_polished_blackstone_bricks", "metal": "minecraft:iron_block", "jade": "minecraft:obsidian",
         "lilin": "red", "batu": "batu"}


def darah(t, x, y, z):
    """A blood stain on the floor block (red terracotta / nether wart block patches)."""
    if t.inside(x, y, z) and t.get(x, y, z) is not None and t.solid(x, y, z):
        t.put(x, y, z, "minecraft:red_terracotta" if t.rng.random() < 0.6 else "minecraft:nether_wart_block")


def iblis_gerbang():
    """Start piece (21 x 16 x 24): a sunken ravine court below a cliff, littered with bones; skulls on stakes, a dead
    tree, blood on the stones; the false-stone door in the north cliff (an entity, anchor gua/iblis_pintu)."""
    t = G("gua", "sarang_iblis/gerbang", 21, 16, 24, batu="batu")
    court, z0 = halaman_cekung(t, 10, 11, 8.0, 7.0, tangga_x=10, lebar_tangga=3,
                               tanah=("minecraft:coarse_dirt", "minecraft:gravel", 0.35),
                               tangga_blok=("minecraft:cobblestone_stairs", "minecraft:mossy_cobblestone_stairs"),
                               pijak="minecraft:cobblestone", pagar="minecraft:cobblestone_wall", dinding_puncak=(7, 10))
    for x in range(3, 18):
        for y in range(FY, 14):
            for z in range(0, 3):
                t.rock(x, y, z, only_empty=False)
    # the doorway behind the false stone (3 wide, 4 high, 2 deep), then the way on
    for z in range(0, 3):
        for x in range(9, 12):
            for y in range(FY + 1, FY + 5):
                t.set_air(x, y, z)
            t.put(x, FY, z, "minecraft:polished_blackstone")
    t.pintu(10, FY, 0, "north", pool_id("gua", "sarang_iblis", "pos_jaga"), final="minecraft:polished_blackstone")
    # skull stakes, a dead tree, bones, blood
    for (x, z) in ((6, 6), (14, 6), (5, 13), (15, 14)):
        if (x, z) in court:
            t.pagar(x, FY + 1, z, "minecraft:dark_oak_fence")
            t.pagar(x, FY + 2, z, "minecraft:dark_oak_fence")
            t.tengkorak(x, FY + 3, z, int(t.rng.random() * 16))
    if (16, 10) in court:
        for y in range(FY + 1, FY + 6):
            t.log(16, y, 10, "minecraft:stripped_dark_oak_log", "y")
        t.log(15, FY + 5, 10, "minecraft:stripped_dark_oak_log", "x")
        t.log(17, FY + 4, 10, "minecraft:stripped_dark_oak_log", "x")
        t.put(15, FY + 4, 10, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    for (x, z) in ((8, 8), (12, 12), (9, 15), (11, 5)):
        if (x, z) in court:
            tulang(t, x, FY + 1, z)
    for _ in range(18):
        x, z = int(t.rng.random() * 21), int(t.rng.random() * 24)
        if (x, z) in court:
            darah(t, x, FY, z)
    t.dandani(1, FY + 1, 1, 19, 10, 22, lumut=0.0, lichen=0.0, tetes=0.0, akar=0.0, jamur=0.02, sulur=0.05, semak=0.05,
              lantai_ok=lambda b: b in ("minecraft:coarse_dirt",))
    t.jangkar(10, FY + 1, 11, "gua/situs", situs="gua/sarang_iblis", tier=2, protect=0)
    t.jangkar(10, FY + 1, z0, "gua/masuk", yaw=FACE_YAW["north"], nama="sarang_iblis", ranah="auto", protect=0)
    # the cliff door stands in the doorway (x 9..11, z 2) facing the court
    t.jangkar(10, FY + 1, 3, "gua/iblis_pintu", yaw=FACE_YAW["south"], protect=4, px=0, pz=-1)
    t.simpan()
    return t.id


def iblis_pos_jaga():
    """The guard post behind the cliff door (13 x 9 x 15): a black stone vestibule, a guards' table with dice and wine
    jars, a weapon rack, chains on the walls, a soul lantern."""
    t = G("gua", "sarang_iblis/pos_jaga", 13, 9, 15)
    kotak_ruang(t, 1, 0, 0, 11, 6, 14, IBLIS)
    pintu_jalan(t, 6, 0, 14, "south", IBLIS)
    pintu_jalan(t, 6, 0, 0, "north", IBLIS)
    t.put(4, 1, 6, "minecraft:dark_oak_trapdoor", {"facing": "east", "half": "top", "open": "false", "powered": "false",
                                                    "waterlogged": "false"})
    t.put(4, 1, 7, "minecraft:dark_oak_trapdoor", {"facing": "east", "half": "top", "open": "false", "powered": "false",
                                                    "waterlogged": "false"})
    t.put(4, 2, 6, "minecraft:decorated_pot", {"facing": "east", "cracked": "true", "waterlogged": "false"})
    t.stairs(3, 1, 6, "minecraft:dark_oak_stairs", "east")
    t.stairs(5, 1, 7, "minecraft:dark_oak_stairs", "west")
    for z in (4, 5, 6):
        t.pagar(10, 1, z, "minecraft:dark_oak_fence")
        t.put(10, 2, z, "minecraft:dark_oak_trapdoor", {"facing": "west", "half": "bottom", "open": "true", "powered": "false",
                                                         "waterlogged": "false"})
    for (x, z) in ((2, 10), (10, 10)):
        for y in (3, 4, 5):
            t.put(x, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    t.tong(9, 1, 11, "up", loot("sarang_iblis_jaga"))
    t.lentera(6, 5, 7, True, IBLIS["glow"])
    for _ in range(6):
        darah(t, 2 + int(t.rng.random() * 9), 0, 1 + int(t.rng.random() * 13))
    t.masuk(6, 0, 14, "south", final=IBLIS["floor"])
    t.pintu(6, 0, 0, "north", pool_id("gua", "sarang_iblis", "turun"), final=IBLIS["floor"])
    t.titik("jaga", 6, 1, 3, "south")
    t.titik("kursi", 3, 1, 6, "east")
    t.simpan()
    return t.id


def iblis_turun():
    """The descending passage (9 x 13 x 17): a cut stair four steps down, chains and soul lanterns, then level."""
    t = G("gua", "sarang_iblis/turun", 9, 13, 17)
    kotak_ruang(t, 1, 0, 0, 7, 11, 16, IBLIS)
    for z in range(1, 16):
        floor = 4 if z >= 12 else 0 if z <= 7 else 4 - (12 - z)
        for x in range(2, 7):
            for y in range(1, floor + 1):
                t.put(x, y, z, IBLIS["wall"] if y < floor else IBLIS["floor"])
            if 8 <= z < 12:
                t.stairs(x, floor, z, IBLIS["stair"], "south") if floor > 0 else None
            for y in range(floor + 1, floor + 5):
                t.set_air(x, y, z)
            for y in range(floor + 5, 11):
                if t.is_air(x, y, z):
                    t.put(x, y, z, IBLIS["ceiling"])
    pintu_jalan(t, 4, 4, 16, "south", IBLIS)
    pintu_jalan(t, 4, 0, 0, "north", IBLIS)
    for (x, y, z) in ((1, 6, 13), (7, 3, 4)):
        t.put(x, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    t.lentera(4, 8, 13, True, IBLIS["glow"])
    t.lentera(4, 4, 4, True, IBLIS["glow"])
    t.masuk(4, 4, 16, "south", final=IBLIS["floor"])
    t.pintu(4, 0, 0, "north", pool_id("gua", "sarang_iblis", "aula"), final=IBLIS["floor"])
    t.simpan()
    return t.id


def iblis_aula():
    """The hall of the blood pool (25 x 13 x 25): a pool of blood-red stone and nether wart in the middle,
    four chained pillars of red brick, a sacrificial altar, banners of the demonic sect; exits to the prison (west), the
    pill lab (east) and the master's chamber (north). Anchor gua/iblis_band (the band wakes here)."""
    t = G("gua", "sarang_iblis/aula", 25, 13, 25)
    kotak_ruang(t, 0, 0, 0, 24, 11, 24, IBLIS)
    for x in range(1, 24):
        for z in range(1, 24):
            t.put(x, 0, z, IBLIS["floor"] if (x + z) % 5 else IBLIS["floor_alt"])
    # the blood pool: two steps down, red concrete bed, wart rim, crimson roots
    for x in range(8, 17):
        for z in range(8, 17):
            d = math.hypot(x - 12, z - 12)
            if d < 3.6:
                t.put(x, 0, z, "minecraft:red_concrete")
            elif d < 4.6:
                t.put(x, 0, z, "minecraft:nether_wart_block")
                if t.rng.random() < 0.4:
                    t.put(x, 1, z, "minecraft:crimson_roots")
            elif d < 5.3:
                t.slab(x, 1, z, IBLIS["slab"])
    for (x, z) in ((6, 6), (18, 6), (6, 18), (18, 18)):
        for y in range(1, 11):
            t.put(x, y, z, IBLIS["pillar"])
        for y in (3, 6, 9):
            for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if t.is_air(x + dx, y, z + dz) and t.rng.random() < 0.5:
                    t.put(x + dx, y, z + dz, "minecraft:iron_chain", {"axis": "x" if dx else "z", "waterlogged": "false"})
        t.lentera(x, 10, z + (1 if z < 12 else -1), True, IBLIS["glow"])
    # the altar to the north, a basin of blood and two skull pillars
    for x in range(10, 15):
        t.stairs(x, 1, 5, IBLIS["stair"], "south")
        for z in (3, 4):
            t.put(x, 1, z, IBLIS["wall"])
    for x in range(10, 15):
        t.put(x, 2, 3, IBLIS["accent"] if x == 12 else IBLIS["inlay"])
    t.put(12, 2, 4, "minecraft:water_cauldron", {"level": "2"})
    t.tengkorak(11, 2, 4, 8, "minecraft:wither_skeleton_skull")
    t.tengkorak(13, 2, 4, 8, "minecraft:wither_skeleton_skull")
    for x in (8, 16):
        t.put(x, 6, 1, "minecraft:black_wall_banner", {"facing": "south"})
    for _ in range(30):
        darah(t, 1 + int(t.rng.random() * 23), 0, 1 + int(t.rng.random() * 23))
    pintu_jalan(t, 12, 0, 24, "south", IBLIS)
    pintu_jalan(t, 0, 0, 12, "west", IBLIS)
    pintu_jalan(t, 24, 0, 12, "east", IBLIS)
    pintu_jalan(t, 12, 0, 0, "north", IBLIS)
    t.masuk(12, 0, 24, "south", final=IBLIS["floor"])
    t.pintu(0, 0, 12, "west", pool_id("gua", "sarang_iblis", "sel"), final=IBLIS["floor"])
    t.pintu(24, 0, 12, "east", pool_id("gua", "sarang_iblis", "racik"), final=IBLIS["floor"])
    t.pintu(12, 0, 0, "north", pool_id("gua", "sarang_iblis", "lorong"), final=IBLIS["floor"])
    t.jangkar(12, 1, 18, "gua/iblis_band", yaw=FACE_YAW["south"], protect=0)
    for (x, z) in ((8, 20), (16, 20), (4, 12), (20, 12)):
        t.titik("jaga", x, 1, z, "south")
    t.simpan()
    return t.id


def iblis_sel():
    """The prison (21 x 9 x 15): a corridor with four cells behind iron bars; each cell's door is an entity cage door
    (anchor gua/iblis_sel, local cell offsets); straw, a bucket, manacle chains, scratches."""
    t = G("gua", "sarang_iblis/sel", 21, 9, 15)
    kotak_ruang(t, 0, 0, 0, 20, 6, 14, IBLIS)
    pintu_jalan(t, 20, 0, 7, "east", IBLIS)
    # cells: two on the north side, two on the south side, each 5 wide, 4 deep
    sel = []
    for (x0, z0, z1, door_z, face) in ((2, 1, 4, 5, "south"), (8, 1, 4, 5, "south"), (2, 10, 13, 9, "north"),
                                       (8, 10, 13, 9, "north")):
        for x in range(x0, x0 + 5):
            for y in range(1, 6):
                t.put(x, y, door_z, "minecraft:iron_bars" if 1 <= y <= 4 and x0 < x < x0 + 4 else IBLIS["wall"],
                      {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"}
                      if 1 <= y <= 4 and x0 < x < x0 + 4 else None)
        for x in (x0, x0 + 4):
            for z in range(z0, z1 + 1):
                for y in range(1, 6):
                    t.put(x, y, z, IBLIS["wall"])
        dx = x0 + 2
        for y in (1, 2):
            t.set_air(dx, y, door_z)
        for z in range(z0, z1 + 1):
            for x in range(x0 + 1, x0 + 4):
                if t.rng.random() < 0.4:
                    t.put(x, 1, z, "minecraft:hay_block" if t.rng.random() < 0.2 else "minecraft:yellow_carpet")
        t.put(x0 + 1, 3, z0 if face == "south" else z1, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        sel.append((dx - 10, door_z - 7, 0 if face == "south" else 180))
    for x in range(14, 20):
        t.put(x, 1, 2, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                        "powered": "false", "waterlogged": "false"}) if x < 16 else None
    t.put(15, 2, 2, "minecraft:candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    t.tong(18, 1, 12, "up", None)
    t.lentera(10, 5, 7, True, IBLIS["glow"])
    t.lentera(16, 5, 7, True, IBLIS["glow"])
    t.masuk(20, 0, 7, "east", final=IBLIS["floor"])
    t.jangkar(10, 1, 7, "gua/iblis_sel", yaw=FACE_YAW["south"], protect=6,
              sel=";".join("%d,%d,%d" % s for s in sel))
    for i, (x, z) in enumerate(((4, 2), (10, 2), (4, 12), (10, 12))):
        t.titik("sel_tawanan", x, 1, z, "south" if z < 7 else "north", {"sel": i})
    t.simpan()
    return t.id


def iblis_racik():
    """The forbidden pill lab (17 x 10 x 17): cauldrons of dark brew, brewing stands, shelves of sealed jars, a slab
    table with a skull and bone dust, a cage of herbs; soul lanterns. Chests of forbidden pills."""
    t = G("gua", "sarang_iblis/racik", 17, 10, 17)
    kotak_ruang(t, 0, 0, 0, 16, 8, 16, IBLIS)
    pintu_jalan(t, 0, 0, 8, "west", IBLIS)
    for (x, z) in ((5, 5), (11, 5), (8, 11)):
        t.put(x, 1, z, "minecraft:water_cauldron", {"level": str(1 + int(t.rng.random() * 3))})
        t.put(x, 0, z, "minecraft:magma_block")
    for x in range(3, 14):
        for y in (1, 2, 3):
            if t.rng.random() < 0.75:
                t.put(x, y, 1, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"}) \
                    if y == 1 else t.put(x, y, 1, "minecraft:polished_blackstone_brick_slab",
                                         {"type": "top", "waterlogged": "false"}) if y == 2 else \
                    t.put(x, y, 1, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false",
                                                               "has_bottle_2": "true"})
    for z in range(9, 14):
        t.slab(14, 1, z, IBLIS["slab"], "top")
    t.tengkorak(14, 2, 10, 4)
    t.put(14, 2, 12, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "true", "has_bottle_2": "false"})
    t.peti(15, 1, 8, "west", loot("sarang_iblis_racik"))
    t.peti(15, 1, 14, "west", loot("sarang_iblis_racik"))
    for z in range(11, 15):
        for x in (2, 4):
            t.jeruji(x, 1, z, ("north", "south"))
            t.jeruji(x, 2, z, ("north", "south"))
    t.put(3, 1, 12, "minecraft:crimson_fungus")
    t.put(3, 1, 13, "minecraft:warped_fungus")
    t.lentera(8, 7, 8, True, IBLIS["glow"])
    t.lentera(13, 7, 4, True, IBLIS["glow"])
    t.masuk(0, 0, 8, "west", final=IBLIS["floor"])
    t.titik("kerja:kuali", 6, 1, 5, "north")
    t.titik("kerja:lumpang", 13, 1, 11, "east")
    t.simpan()
    return t.id


def iblis_lorong():
    """A corridor of the lair (9 x 7 x 13): chains, a soul lantern, bones in a niche."""
    t = G("gua", "sarang_iblis/lorong", 9, 7, 13)
    kotak_ruang(t, 1, 0, 0, 7, 5, 12, IBLIS)
    pintu_jalan(t, 4, 0, 12, "south", IBLIS)
    pintu_jalan(t, 4, 0, 0, "north", IBLIS)
    t.set_air(1, 2, 6)
    t.tengkorak(1, 2, 6, 12)
    t.put(7, 3, 4, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    t.lentera(4, 4, 6, True, IBLIS["glow"])
    t.masuk(4, 0, 12, "south", final=IBLIS["floor"])
    t.pintu(4, 0, 0, "north", pool_id("gua", "sarang_iblis", "kamar"), final=IBLIS["floor"])
    t.simpan()
    return t.id


def iblis_kamar():
    """The master's chamber (21 x 12 x 21): a throne of blackstone on a dais under banners, a curtained bed, a table of
    stolen treasures, the master's chest; the Demon Elder waits here (anchor gua/iblis_bos)."""
    t = G("gua", "sarang_iblis/kamar", 21, 12, 21)
    kotak_ruang(t, 0, 0, 0, 20, 10, 20, IBLIS)
    pintu_jalan(t, 10, 0, 20, "south", IBLIS)
    for z in range(12, 20):
        t.put(10, 0, z, "minecraft:red_carpet".replace("carpet", "wool"))
        t.put(9, 0, z, IBLIS["inlay"]) if z % 2 else None
    for x in range(6, 15):
        for z in range(2, 7):
            t.put(x, 1, z, IBLIS["wall"])
    for x in range(6, 15):
        t.stairs(x, 1, 7, IBLIS["stair"], "south")
    for x in range(8, 13):
        for z in range(2, 5):
            t.put(x, 2, z, IBLIS["floor"])
    t.stairs(10, 3, 3, "minecraft:blackstone_stairs", "south")
    t.put(9, 3, 3, "minecraft:polished_blackstone_wall", {"east": "none", "north": "none", "south": "none", "west": "none",
                                                          "up": "true", "waterlogged": "false"})
    t.put(11, 3, 3, "minecraft:polished_blackstone_wall", {"east": "none", "north": "none", "south": "none",
                                                           "west": "none", "up": "true", "waterlogged": "false"})
    for y in range(3, 7):
        t.put(10, y, 2, IBLIS["accent"] if y == 6 else IBLIS["wall"])
    t.tengkorak(10, 7, 2, 8, "minecraft:wither_skeleton_skull")
    for x in (7, 13):
        t.put(x, 7, 1, "minecraft:red_wall_banner", {"facing": "south"})
    t.kasur(17, 1, 4, "north", "black")
    for (x, z) in ((16, 2), (18, 2), (16, 6), (18, 6)):
        for y in range(1, 5):
            t.pagar(x, y, z, "minecraft:crimson_fence")
    for x in range(16, 19):
        for z in range(2, 7):
            t.put(x, 5, z, "minecraft:red_carpet") if (x, z) not in ((16, 2), (18, 2), (16, 6), (18, 6)) else None
    for z in range(10, 14):
        t.put(3, 1, z, "minecraft:crimson_trapdoor", {"facing": "east", "half": "top", "open": "false", "powered": "false",
                                                      "waterlogged": "false"})
    t.put(3, 2, 11, "minecraft:gold_block")
    t.put(3, 2, 12, "minecraft:decorated_pot", {"facing": "east", "cracked": "false", "waterlogged": "false"})
    t.peti(1, 1, 16, "east", loot("sarang_iblis_kamar"))
    for (x, z) in ((5, 5), (15, 15), (5, 15), (15, 9)):
        t.lentera(x, 9, z, True, IBLIS["glow"])
    t.masuk(10, 0, 20, "south", final=IBLIS["floor"])
    t.jangkar(10, 2, 9, "gua/iblis_bos", yaw=FACE_YAW["south"], protect=0)
    t.titik("kursi", 10, 3, 3, "south")
    t.titik("kasur", 17, 1, 4, "north")
    t.simpan()
    return t.id


# ================================================================================================== Gua Urat Roh
def urat_geode():
    """The spirit-vein geode (41 x 26 x 41, start piece, y -20..10): a basalt shell, a calcite layer laced with veins of
    diamond, emerald and lapis ore, an amethyst lining with clusters and buds turned inward, glow lichen; a flat floor of
    calcite and amethyst around a spirit spring in the middle. Two tunnels leave east and west. The Crystal Guardians
    wake from the floor (anchor gua/urat_inti)."""
    t = G("gua", "gua_urat_roh/geode", 41, 26, 41, batu="dalam")
    cx, cy, cz = 20, 12, 20
    rx, ry, rz = 18.5, 11.5, 18.5
    lantai = 4
    ores = ["minecraft:diamond_ore", "minecraft:emerald_ore", "minecraft:lapis_ore", "minecraft:lapis_ore"]
    for x in range(41):
        for y in range(26):
            for z in range(41):
                n = (noise3(x, y, z, 6.0, t.nseed) - 0.5) * 0.12
                d = math.sqrt(((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2) + n
                if d > 1.0:
                    continue
                if d > 0.93:
                    t.put(x, y, z, "minecraft:smooth_basalt")
                elif d > 0.86:
                    vein = noise3(x, y, z, 2.5, t.nseed + 11)
                    t.put(x, y, z, t.rng.choice(ores) if vein > 0.82 else "minecraft:calcite")
                elif d > 0.80:
                    t.put(x, y, z, "minecraft:budding_amethyst" if t.rng.random() < 0.06 else "minecraft:amethyst_block")
                elif y <= lantai:
                    t.put(x, y, z, "minecraft:calcite" if y < lantai else
                          ("minecraft:amethyst_block" if t.rng.random() < 0.25 else "minecraft:calcite"))
                else:
                    t.set_air(x, y, z)
    # clusters and buds on every inward amethyst face, lichen here and there
    faces = (("up", (0, -1, 0)), ("down", (0, 1, 0)), ("north", (0, 0, 1)), ("south", (0, 0, -1)), ("east", (-1, 0, 0)),
             ("west", (1, 0, 0)))
    for x in range(1, 40):
        for y in range(lantai + 1, 25):
            for z in range(1, 40):
                if not t.is_air(x, y, z):
                    continue
                for facing, (dx, dy, dz) in faces:
                    b = t.get(x + dx, y + dy, z + dz)
                    if b in ("minecraft:amethyst_block", "minecraft:budding_amethyst"):
                        r = t.rng.random()
                        if r < 0.08:
                            t.put(x, y, z, "minecraft:amethyst_cluster", {"facing": facing, "waterlogged": "false"})
                        elif r < 0.16:
                            t.put(x, y, z, "minecraft:large_amethyst_bud", {"facing": facing, "waterlogged": "false"})
                        elif r < 0.20:
                            t.put(x, y, z, "minecraft:medium_amethyst_bud", {"facing": facing, "waterlogged": "false"})
                        elif r < 0.24:
                            t.tempel(x, y, z, "minecraft:glow_lichen")
                        break
    # crystal pillars rising from the floor
    for k in range(7):
        a = k * 2 * math.pi / 7 + 0.4
        px, pz = int(round(cx + math.cos(a) * 10)), int(round(cz + math.sin(a) * 10))
        h = 3 + int(t.rng.random() * 5)
        for y in range(lantai + 1, lantai + 1 + h):
            t.put(px, y, pz, "minecraft:amethyst_block")
        t.put(px, lantai + 1 + h, pz, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    # the spirit spring: a round pool with a calcite rim and a quartz ring
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d < 2.6:
                t.put(x, lantai - 1, z, "minecraft:prismarine")
                t.put(x, lantai, z, "minecraft:water", {"level": "0"})
            elif d < 3.6:
                t.put(x, lantai, z, "minecraft:quartz_bricks")
            elif d < 4.4:
                t.put(x, lantai, z, "minecraft:smooth_quartz")
    t.put(cx, lantai - 2, cz, "minecraft:sea_lantern")
    t.put(cx, lantai - 1, cz, "minecraft:prismarine")
    # the two tunnels (east, west) at floor level
    t.lorong([(40, cz), (34, cz)], lebar=3, tinggi=4, lantai=lantai, lantai_blok="minecraft:smooth_basalt")
    t.lorong([(0, cz), (6, cz)], lebar=3, tinggi=4, lantai=lantai, lantai_blok="minecraft:smooth_basalt")
    t.pintu(40, lantai, cz, "east", pool_id("gua", "gua_urat_roh", "terowongan"), final="minecraft:smooth_basalt")
    t.pintu(0, lantai, cz, "west", pool_id("gua", "gua_urat_roh", "terowongan"), final="minecraft:smooth_basalt")
    t.jangkar(cx, lantai + 1, cz + 6, "gua/situs", situs="gua/urat_roh", tier=3, protect=0)
    t.jangkar(cx, lantai + 1, cz, "gua/urat_inti", yaw=FACE_YAW["south"], protect=6, jaga="-7,0;7,0", harta="0,-7")
    t.titik("meditasi", cx, lantai + 1, cz + 5, "north")
    t.titik("meditasi", cx - 5, lantai + 1, cz, "east")
    t.simpan()
    return t.id


def urat_terowongan():
    """A miners' tunnel off the geode (11 x 8 x 22): old timber props, a rail of planks, a lantern, chipped amethyst,
    a forgotten pick crate; it ends in rubble (where it meets natural caves the way is open)."""
    t = G("gua", "gua_urat_roh/terowongan", 11, 8, 22, batu="dalam")
    t.lorong([(5, 21), (5, 13), (6, 6), (5, 2)], lebar=3, tinggi=4, lantai=FY, lantai_blok="minecraft:cobbled_deepslate")
    for z in (17, 11, 5):
        cx = 5 if z != 5 else 6
        for x in (cx - 2, cx + 2):
            for y in range(FY + 1, FY + 4):
                t.log(x, y, z, "minecraft:spruce_log", "y")
        for x in range(cx - 2, cx + 3):
            t.log(x, FY + 4, z, "minecraft:spruce_log", "x")
    for z in range(3, 21):
        if t.is_air(5, FY + 1, z) and z % 2 == 0:
            t.put(5, FY + 1, z, "minecraft:rail", {"shape": "north_south", "waterlogged": "false"})
    t.lentera(5, FY + 4, 14, True)
    t.tong(4, FY + 1, 8, "up", loot("urat_roh_penambang"))
    for (x, z) in ((3, 4), (7, 12), (3, 16)):
        t.put(x, FY + 2, z, "minecraft:amethyst_block")
    for x in range(3, 8):
        for y in range(FY + 1, FY + 4):
            if t.rng.random() < 0.6:
                t.put(x, y, 1, "minecraft:cobbled_deepslate" if t.rng.random() < 0.6 else "minecraft:gravel")
    t.dandani(2, FY + 1, 1, 9, 6, 21, lumut=0.0, lichen=0.05, tetes=0.04, akar=0.0, jamur=0.0)
    t.masuk(5, FY, 21, "south", final="minecraft:cobbled_deepslate")
    t.simpan()
    return t.id


# ================================================================================================== Gua Cermin Hati
def cermin_mulut():
    """Start (19 x 16 x 22): a sunken hollow under dark trees, mossy and misty with pale moss, a mouth of black stone
    with an old rope of prayer slips; the way on goes north."""
    t = G("gua", "cermin_hati/mulut", 19, 16, 22, batu="rawa")
    court, z0 = halaman_cekung(t, 9, 10, 7.0, 6.5, tangga_x=9, lebar_tangga=3,
                               tanah=("minecraft:moss_block", "minecraft:mud", 0.35),
                               tangga_blok=("minecraft:mud_brick_stairs", "minecraft:stone_brick_stairs"),
                               pijak="minecraft:mud_bricks", pagar="minecraft:mud_brick_wall")
    for x in range(4, 15):
        for y in range(FY, 13):
            for z in range(0, 3):
                t.rock(x, y, z, only_empty=False)
    mulut_gua(t, 9, 2, "south", lebar=3, tinggi=4, bingkai="minecraft:polished_blackstone",
              lintel="minecraft:polished_blackstone_bricks", dalam=3, lantai="minecraft:polished_blackstone")
    for x in range(7, 12):
        t.put(x, FY + 5, 3, "minecraft:white_carpet") if t.is_air(x, FY + 5, 3) else None
    t.pintu(9, FY, 0, "north", pool_id("gua", "cermin_hati", "kolam"), final="minecraft:polished_blackstone")
    for (x, z) in ((6, 4), (12, 4)):
        if (x, z) in court:
            t.lentera_batu(x, FY + 1, z)
    t.dandani(1, FY + 1, 1, 17, 10, 20, lumut=0.18, lichen=0.0, tetes=0.0, akar=0.0, jamur=0.04, sulur=0.14, semak=0.10,
              lantai_ok=lambda b: b in ("minecraft:moss_block", "minecraft:mud"))
    for x in range(19):
        for z in range(22):
            for y in range(FY + 1, 12):
                if t.get(x, y, z) == "minecraft:moss_carpet" and t.rng.random() < 0.5:
                    t.put(x, y, z, "minecraft:pale_moss_carpet", {"bottom": "true", "north": "none", "south": "none",
                                                                  "east": "none", "west": "none"})
    t.jangkar(9, FY + 1, 10, "gua/situs", situs="gua/cermin_hati", tier=3, protect=0)
    t.jangkar(9, FY + 1, z0, "gua/masuk", yaw=FACE_YAW["north"], nama="cermin_hati", ranah="nascent_soul", protect=0)
    t.simpan()
    return t.id


def cermin_kolam():
    """The heart-mirror grotto (19 x 10 x 19): a still pool of shallow water over black stone that shows nothing but
    the sky of the mind, the stone mirror of polished blackstone in a carved frame at the north wall, a moss cushion at
    the south edge facing it, candles that were never lit, chains of old prayer slips. Anchor gua/cermin_kolam."""
    t = G("gua", "cermin_hati/kolam", 19, 10, 19, batu="batu")
    t.rongga(9, 4, 9, 8.0, 3.8, 8.0, lantai=FY, tanah=("minecraft:polished_blackstone", "minecraft:blackstone", 0.3), kasar=0.12)
    t.rock_box(0, 0, 0, 18, 0, 18)
    t.lorong([(9, 18), (9, 15)], lebar=3, tinggi=3)
    for x in range(3, 16):
        for z in range(4, 14):
            d = math.hypot((x - 9) / 6.0, (z - 9) / 4.6)
            if d < 1.0 and t.is_air(x, FY + 1, z):
                t.put(x, FY - 1, z, "minecraft:black_concrete")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
            elif d < 1.18 and t.is_air(x, FY + 1, z):
                t.put(x, FY, z, "minecraft:polished_blackstone_bricks")
    # the mirror: a 5 x 5 face of polished blackstone in a chiseled frame
    for x in range(6, 13):
        for y in range(FY + 1, FY + 7):
            edge = x in (6, 12) or y in (FY + 1, FY + 6)
            t.put(x, y, 2, "minecraft:chiseled_polished_blackstone" if edge else "minecraft:polished_blackstone")
            for z in (0, 1):
                t.rock(x, y, z, only_empty=False)
    t.put(9, FY + 7, 2, "minecraft:gilded_blackstone")
    t.put(9, FY + 6, 3, "minecraft:soul_lantern", {"hanging": "true", "waterlogged": "false"})
    # the cushion and the candles at the south edge
    t.put(9, FY, 14, "minecraft:moss_block")
    t.put(9, FY + 1, 14, "minecraft:moss_carpet")
    for (x, z) in ((7, 14), (11, 14), (6, 13), (12, 13)):
        if t.is_air(x, FY + 1, z):
            t.lilin(x, FY + 1, z, 2, "black")
    for (x, z) in ((5, 6), (13, 6), (5, 11), (13, 11)):
        top = max((y for y in range(FY + 2, 8) if t.is_air(x, y, z)), default=None)
        if top is not None and t.solid(x, top + 1, z):
            for i in range(2):
                if t.is_air(x, top - i, z):
                    t.put(x, top - i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
            t.put(x, top - 2, z, "minecraft:white_carpet") if t.is_air(x, top - 2, z) else None
    t.dandani(1, FY + 1, 1, 17, 8, 17, lumut=0.05, lichen=0.0, tetes=0.05, akar=0.04, jamur=0.0,
              lantai_ok=lambda b: b in ("minecraft:polished_blackstone", "minecraft:blackstone"))
    t.masuk(9, FY, 18, "south", final="minecraft:polished_blackstone")
    t.jangkar(9, FY + 1, 14, "gua/cermin_kolam", yaw=FACE_YAW["north"], protect=8, cx=0, cz=-11)
    t.titik("meditasi", 9, FY + 1, 14, "north")
    t.simpan()
    return t.id


# ================================================================================================== Situs alam
def situs_puncak_petir():
    """Puncak Petir (31 x 24 x 31): a terrace 23 x 23 of stone bricks near a peak with a copper inlay of the eight
    directions, a three-step round altar in the middle, nine waxed copper pillars in a ring, each with a small roof,
    the Batu Catatan stele on the north edge (names of those who passed are drawn over it), spectators' balustrade and
    benches on the east and west edges. Floor y4 over a stone foundation (beard_box)."""
    t = G("situs", "puncak_petir", 31, 24, 31, palet="gua", batu="gunung")
    F = 4
    c = 15
    for x in range(31):
        for z in range(31):
            inside = 4 <= x <= 26 and 4 <= z <= 26
            for y in range(0, F):
                if inside or (3 <= x <= 27 and 3 <= z <= 27 and y < F - 1):
                    t.put(x, y, z, "minecraft:stone_bricks" if inside else "minecraft:cobblestone")
            if inside:
                edge = x in (4, 26) or z in (4, 26)
                t.put(x, F, z, "minecraft:polished_andesite" if edge else
                      ("minecraft:stone_bricks" if (x + z) % 2 else "minecraft:smooth_stone"))
                for y in range(F + 1, 24):
                    t.set_air(x, y, z)
    for i in range(5, 26):
        for (x, z) in ((i, c), (c, i)):
            t.put(x, F, z, "minecraft:waxed_cut_copper")
        for (x, z) in ((i, i), (i, 30 - i)):
            if 5 <= x <= 25:
                t.put(x, F, z, "minecraft:waxed_exposed_cut_copper")
    # the altar: three rings rising, a seat of polished andesite, a copper heart
    for (r, y, bid) in ((5, F, "minecraft:polished_andesite"), (4, F + 1, "minecraft:stone_bricks"), (2, F + 2, "minecraft:smooth_stone")):
        t.disc(c, y, c, r, bid)
    t.ring(c, F + 1, c, 4, "minecraft:stone_brick_slab")
    t.put(c, F + 2, c, "minecraft:waxed_copper_block")
    t.slab(c, F + 3, c, "minecraft:smooth_stone_slab")
    # nine copper pillars, each 6 high with a little hip roof of copper stairs
    for k in range(9):
        a = k * 2 * math.pi / 9 - math.pi / 2
        px, pz = int(round(c + math.cos(a) * 9)), int(round(c + math.sin(a) * 9))
        t.put(px, F, pz, "minecraft:chiseled_stone_bricks")
        for y in range(F + 1, F + 7):
            t.put(px, y, pz, "minecraft:waxed_cut_copper" if y % 3 else "minecraft:waxed_chiseled_copper")
        t.put(px, F + 7, pz, "minecraft:waxed_copper_bulb", {"lit": "false", "powered": "false"})
        for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            t.stairs(px + dx, F + 8, pz + dz, "minecraft:waxed_cut_copper_stairs", f)
        t.slab(px, F + 9, pz, "minecraft:waxed_cut_copper_slab")
    # the Batu Catatan (record stone) on the north edge
    for x in range(c - 2, c + 3):
        t.put(x, F + 1, 6, "minecraft:polished_andesite")
        for y in range(F + 2, F + 8):
            t.put(x, y, 6, "minecraft:polished_diorite" if x in (c - 2, c + 2) else "minecraft:smooth_quartz")
    for x in range(c - 3, c + 4):
        t.stairs(x, F + 8, 5, "minecraft:stone_brick_stairs", "south")
        t.stairs(x, F + 8, 7, "minecraft:stone_brick_stairs", "north")
        t.put(x, F + 8, 6, "minecraft:stone_bricks")
    t.put(c, F + 9, 6, "minecraft:waxed_cut_copper_slab", {"type": "bottom", "waterlogged": "false"})
    # balustrade around the terrace, benches for spectators east and west
    for i in range(4, 27):
        for (x, z) in ((i, 4), (i, 26), (4, i), (26, i)):
            if (x, z) in ((c, 26),) or (c - 1 <= x <= c + 1 and z == 26):
                continue
            t.dinding_tiang(x, F + 1, z, "minecraft:stone_brick_wall", up=(i % 4 == 0))
    for z in range(9, 22):
        if z % 3 == 0:
            continue
        t.stairs(6, F + 1, z, "minecraft:spruce_stairs", "west")
        t.stairs(24, F + 1, z, "minecraft:spruce_stairs", "east")
    # entry steps south
    for i in range(4):
        for x in range(c - 1, c + 2):
            t.stairs(x, F - i, 27 + i, "minecraft:stone_brick_stairs", "north")
    t.jangkar(c, F + 1, c + 6, "gua/situs", situs="situs/puncak_petir", tier=2, protect=0)
    t.jangkar(c, F + 3, c, "gua/petir_altar", yaw=FACE_YAW["south"], protect=12, r=11, bx=0, bz=-9)
    for k, z in enumerate(range(9, 22, 2)):
        t.titik("penonton", 6, F + 1, z, "east")
        t.titik("penonton", 24, F + 1, z, "west")
    t.kotak("panggung", c - 4, F + 1, c - 4, c + 4, F + 3, c + 4)
    t.simpan()
    return t.id


def situs_panggung_bintang():
    """Panggung Bintang (27 x 18 x 27): a round stage 21 across of smooth quartz with a lapis star map, eight stone
    benches, an armillary frame of copper on a pedestal in the middle (its rings are a client model that turns), a
    low parapet with lanterns. Floor y3 (beard_box)."""
    t = G("situs", "panggung_bintang", 27, 18, 27, palet="gua", batu="gunung")
    F = 3
    c = 13
    for x in range(27):
        for z in range(27):
            d = math.hypot(x - c, z - c)
            if d <= 11.5:
                for y in range(0, F):
                    t.put(x, y, z, "minecraft:stone_bricks")
                t.put(x, F, z, "minecraft:smooth_quartz" if d < 10.2 else "minecraft:quartz_bricks")
                for y in range(F + 1, 18):
                    t.set_air(x, y, z)
    # star map: lapis rings and gold stars
    t.ring(c, F, c, 7, "minecraft:lapis_block")
    t.ring(c, F, c, 4, "minecraft:blue_glazed_terracotta".replace("blue_glazed_terracotta", "lapis_block"))
    for k in range(12):
        a = k * math.pi / 6
        for r in (5.5, 8.5):
            x, z = int(round(c + math.cos(a + r) * r)), int(round(c + math.sin(a + r) * r))
            t.put(x, F, z, "minecraft:gold_block" if k % 3 == 0 else "minecraft:light_blue_concrete")
    # the armillary pedestal and its static outer frame
    t.disc(c, F + 1, c, 1, "minecraft:chiseled_quartz_block")
    t.put(c, F + 2, c, "minecraft:quartz_pillar", {"axis": "y"})
    t.put(c, F + 3, c, "minecraft:lodestone")
    for k in range(16):
        a = k * math.pi / 8
        x = int(round(c + math.cos(a) * 4))
        y = int(round(F + 7 + math.sin(a) * 4))
        if F + 4 <= y:
            t.put(x, y, c, "minecraft:waxed_copper_grate")
    for (x, z) in ((c - 4, c), (c + 4, c)):
        for y in range(F + 1, F + 7):
            t.put(x, y, z, "minecraft:waxed_cut_copper" if y < F + 3 else "minecraft:waxed_copper_grate")
    # benches and parapet
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, z = int(round(c + math.cos(a) * 9.5)), int(round(c + math.sin(a) * 9.5))
        t.slab(x, F + 1, z, "minecraft:smooth_stone_slab")
    for x in range(27):
        for z in range(27):
            d = math.hypot(x - c, z - c)
            if 10.6 < d <= 11.5:
                if (x - c) == 0 and z > c:
                    continue
                t.dinding_tiang(x, F + 1, z, "minecraft:diorite_wall", up=((x + z) % 4 == 0))
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        x, z = int(round(c + math.cos(a) * 11)), int(round(c + math.sin(a) * 11))
        t.lentera(x, F + 2, z, False)
    for i in range(3):
        for x in range(c - 1, c + 2):
            t.stairs(x, F - i, 25 + i if 25 + i < 27 else 26, "minecraft:quartz_stairs", "north")
    t.jangkar(c, F + 1, c + 6, "gua/situs", situs="situs/panggung_bintang", tier=2, protect=0)
    t.jangkar(c, F + 1, c, "gua/bintang_panggung", yaw=FACE_YAW["south"], protect=12, r=10)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, z = int(round(c + math.cos(a) * 9.5)), int(round(c + math.sin(a) * 9.5))
        t.titik("pandang", x, F + 1, z, "north")
    t.kotak("panggung", c - 9, F + 1, c - 9, c + 9, F + 1, c + 9)
    t.simpan()
    return t.id


def situs_mata_air_roh():
    """Mata Air Roh (21 x 12 x 21): a spring welling up through jade-green stones into a clear round pool, stepping
    stones, a tiny roofed shrine with an incense table and a bamboo ladle stand, cherry and azalea planting, a stone
    basin. Floor y3 (beard_box). Drinking (once a day) is clicking the pool (anchor gua/mata_air)."""
    t = G("situs", "mata_air_roh", 21, 12, 21, palet="gua", batu="batu")
    F = 3
    c = 10
    for x in range(21):
        for z in range(21):
            d = math.hypot(x - c, z - c)
            if d <= 10:
                for y in range(0, F):
                    t.put(x, y, z, "minecraft:stone" if y < F - 1 else "minecraft:dirt")
                t.put(x, F, z, "minecraft:grass_block" if d > 6.6 else "minecraft:moss_block")
                for y in range(F + 1, 12):
                    t.set_air(x, y, z)
    for x in range(21):
        for z in range(21):
            d = math.hypot(x - c, z - (c + 1))
            if d < 3.4:
                t.put(x, F - 1, z, "minecraft:prismarine")
                t.put(x, F, z, "minecraft:water", {"level": "0"})
            elif d < 4.6:
                t.put(x, F, z, t.rng.choice(["minecraft:prismarine_bricks", "minecraft:dark_prismarine",
                                             "minecraft:mossy_stone_bricks", "minecraft:prismarine"]))
                if t.rng.random() < 0.3:
                    t.put(x, F + 1, z, "minecraft:moss_carpet")
    t.put(c, F - 2, c + 1, "minecraft:sea_lantern")
    t.put(c, F - 1, c + 1, "minecraft:prismarine")
    for (x, z) in ((c + 1, c + 2), (c - 1, c), (c, c + 1)):
        t.put(x, F + 1, z, "minecraft:lily_pad")
    # stepping stones from the south edge to the shrine
    for z in (19, 17, 15):
        t.put(c, F, z, "minecraft:smooth_stone")
    # the shrine (north), 5 x 4, open front
    for x in range(c - 2, c + 3):
        for z in range(1, 5):
            t.put(x, F, z, "minecraft:polished_andesite")
    for (x, z) in ((c - 2, 1), (c + 2, 1), (c - 2, 4), (c + 2, 4)):
        for y in range(F + 1, F + 4):
            t.log(x, y, z, "minecraft:stripped_cherry_log", "y")
    for x in range(c - 3, c + 4):
        t.stairs(x, F + 4, 0, "minecraft:cherry_stairs", "south")
        t.stairs(x, F + 4, 5, "minecraft:cherry_stairs", "north")
        for z in range(1, 5):
            t.put(x, F + 4, z, "minecraft:cherry_planks")
            t.slab(x, F + 5, z, "minecraft:cherry_slab") if z in (2, 3) else None
    for x in range(c - 1, c + 2):
        t.put(x, F + 1, 1, "minecraft:cherry_planks")
    t.put(c, F + 2, 1, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    t.lilin(c - 1, F + 2, 1, 2, "green")
    t.lilin(c + 1, F + 2, 1, 2, "green")
    t.lentera(c, F + 3, 3, True)
    t.put(c + 4, F + 1, c + 4, "minecraft:water_cauldron", {"level": "3"})
    t.log(c + 4, F + 1, c + 5, "minecraft:bamboo_block", "y")
    # planting: a cherry sapling grown (blocks), azaleas, ferns
    for (x, z) in ((3, 6), (17, 7), (4, 15), (16, 16)):
        if math.hypot(x - c, z - c) <= 9:
            t.put(x, F + 1, z, t.rng.choice(["minecraft:azalea", "minecraft:flowering_azalea", "minecraft:fern"]))
    for y in range(F + 1, F + 5):
        t.log(16, y, 4, "minecraft:cherry_log", "y")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (0, 1):
                if abs(dx) + abs(dz) + dy < 4:
                    daun(t, 16 + dx, F + 5 + dy, 4 + dz, "minecraft:cherry_leaves")
    t.jangkar(c, F + 1, c + 6, "gua/situs", situs="situs/mata_air_roh", tier=1, protect=0)
    t.jangkar(c, F + 1, c + 1, "gua/mata_air", yaw=FACE_YAW["south"], protect=8, r=3)
    t.titik("meditasi", c - 3, F + 1, c + 5, "north")
    t.titik("dupa", c, F + 1, 2, "north")
    t.simpan()
    return t.id


# ================================================================================================== detail pass (D-GU)
# Variant pieces: every pool slot of a cave gets a second (or third) piece with the same connectors and the same anchor
# contract, so two caves of one kind rarely look alike. A variant that does not fit falls back to the other elements of
# its pool (jigsaw tries every element), so the base piece stays the safety net.

def bonsai(t, x, y, z):
    """A potted bonsai on a low stand: a spruce trapdoor table with a potted azalea or bamboo."""
    t.put(x, y, z, "minecraft:spruce_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                                  "waterlogged": "false"})
    t.put(x, y + 1, z, t.rng.choice(["minecraft:potted_flowering_azalea_bush", "minecraft:potted_azalea_bush",
                                     "minecraft:potted_bamboo", "minecraft:potted_fern"]))


def gulungan(t, x, y, z, facing, warna="white"):
    """A hanging calligraphy scroll: a wall banner with a stripped log rod above it."""
    t.put(x, y, z, "minecraft:%s_wall_banner" % warna, {"facing": facing})


def bendera_doa(t, x0, x1, y, z, along="x"):
    """A line of prayer flags: carpets of five colours hung under an iron chain run between two walls."""
    warna = ["white", "red", "yellow", "lime", "light_blue"]
    for i, x in enumerate(range(x0, x1 + 1)):
        px, pz = (x, z) if along == "x" else (z, x)
        if t.is_air(px, y, pz):
            t.put(px, y, pz, "minecraft:iron_chain", {"axis": along, "waterlogged": "false"})
        if t.is_air(px, y - 1, pz) and i % 2 == 0:
            t.put(px, y - 1, pz, "minecraft:%s_carpet" % warna[(i // 2) % len(warna)])


def pertapa_ruang_air():
    """Meditation hall variant (25 x 17 x 25): a tall grotto with a waterfall. Water seeps from a sealed crack in the
    north overhang and falls two wide into a jade-rimmed basin; a stream leaves the basin and winds south-west under a
    little arched bridge; the hermit's round dais stands on a rock island between basin and stream. Scroll banners on
    the east wall, prayer flags across the vault, bonsai stands, a tea mat by the stream. Same connectors and anchor
    contract as gua_pertapa/ruang_meditasi (gua/pertapa at the dais, incense one block north of it)."""
    t = G("gua", "gua_pertapa/ruang_meditasi_air", 25, 17, 25)
    t.rongga(12, 5.5, 12, 10.5, 6.5, 10.5, lantai=FY, tanah=("minecraft:stone", "minecraft:mossy_cobblestone", 0.3),
             kasar=0.2)
    t.rock_box(0, 0, 0, 24, 0, 24)
    t.lorong([(12, 24), (12, 19)], lebar=3, tinggi=4)
    t.lorong([(0, 12), (5, 12)], lebar=3, tinggi=4)
    t.lorong([(24, 12), (19, 12)], lebar=3, tinggi=4)
    # the basin under the fall (north), rim of prismarine bricks and mossy stone
    for x in range(8, 17):
        for z in range(2, 8):
            if not t.inside(x, FY, z):
                continue
            d = math.hypot((x - 12) / 4.2, (z - 4.5) / 2.8)
            if d < 1.0:
                t.put(x, FY - 1, z, "minecraft:clay" if d > 0.5 else "minecraft:prismarine")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
                for y in range(FY + 1, FY + 4):
                    if t.get(x, y, z) is None or t._is_rock(t.get(x, y, z)):
                        t.set_air(x, y, z)
            elif d < 1.3:
                t.put(x, FY, z, "minecraft:prismarine_bricks" if (x + z) % 3 else "minecraft:mossy_stone_bricks")
    t.put(12, FY - 1, 4, "minecraft:sea_lantern")
    # the overhang: a rock lip over the basin, a sealed crack holds two source blocks, the fall below them
    top = 12
    for x in range(9, 16):
        for z in range(1, 5):
            for y in range(top - 1, top + 2):
                t.put(x, y, z, t.rock_id())
    for x in (11, 12):
        t.put(x, top, 3, "minecraft:water", {"level": "0"})
        for y in range(FY + 1, top):
            t.put(x, y, 3, "minecraft:water", {"level": "8"})
        for z in (2, 4):
            t.put(x, top, z, t.rock_id())
    for x in (10, 13):
        t.put(x, top, 3, t.rock_id())
    for x in (10, 13):
        for y in range(FY + 2, top - 1):
            if t.rng.random() < 0.55 and t.is_air(x, y, 3):
                t.put(x, y, 3, "minecraft:glow_lichen", {"north": "true", "south": "false", "east": "false",
                                                        "west": "false", "up": "false", "down": "false",
                                                        "waterlogged": "false"}) if t.solid(x, y, 2) else None
    for (x, z) in ((9, 3), (14, 2), (15, 5)):
        if t.is_air(x, FY + 1, z) and t.get(x, FY, z) == "minecraft:water":
            t.put(x, FY + 1, z, "minecraft:lily_pad")
    t.put(10, FY, 5, "minecraft:big_dripleaf_stem", {"facing": "south", "waterlogged": "true"}) \
        if t.get(10, FY, 5) == "minecraft:water" else None
    if t.get(10, FY, 5) == "minecraft:big_dripleaf_stem":
        t.put(10, FY + 1, 5, "minecraft:big_dripleaf", {"facing": "south", "tilt": "none", "waterlogged": "false"})
    # the stream: basin -> south-west, 2 wide, under a bridge on the path from the entry
    stream = [(9, 8), (8, 9), (7, 10), (6, 11), (6, 12), (6, 13), (5, 14), (4, 15), (4, 16)]
    for (sx_, sz_) in stream:
        for (dx, dz) in ((0, 0), (1, 0)):
            x, z = sx_ + dx, sz_ + dz
            if t.is_air(x, FY + 1, z) or t.get(x, FY + 1, z) is None:
                t.put(x, FY - 1, z, "minecraft:clay")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
                t.set_air(x, FY + 1, z)
    for (x, z) in ((3, 17), (4, 17), (5, 17), (3, 16), (5, 16)):
        if t.inside(x, FY, z) and t.get(x, FY, z) != "minecraft:water":
            t.put(x, FY, z, "minecraft:mossy_cobblestone")
    # the arched bridge over the stream where the west path crosses it (x 5..8, z 12)
    for x in range(5, 9):
        t.put(x, FY, 12, "minecraft:water", {"level": "0"}) if x in (6, 7) else None
    for (x, kind) in ((5, "stairs_e"), (6, "slab"), (7, "slab"), (8, "stairs_w")):
        if kind == "slab":
            t.slab(x, FY + 1, 12, "minecraft:spruce_slab", "bottom")
        else:
            t.stairs(x, FY + 1, 12, "minecraft:spruce_stairs", "east" if kind == "stairs_e" else "west")
    for x in (6, 7):
        for z in (11, 13):
            t.pagar(x, FY + 2, z, "minecraft:spruce_fence", ("east", "west"))
    # the rock island with the dais
    t.disc(12, FY, 12, 4, "minecraft:mossy_stone_bricks")
    t.disc(12, FY, 12, 3, "minecraft:polished_tuff")
    t.disc(12, FY + 1, 12, 2, "minecraft:moss_carpet")
    t.put(12, FY + 1, 12, "minecraft:brown_carpet")
    t.put(12, FY + 1, 11, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    t.lilin(11, FY + 1, 11, 3, "white")
    t.lilin(13, FY + 1, 11, 2, "white")
    for (x, z) in ((9, 12), (15, 12), (12, 15)):
        t.slab(x, FY + 1, z, "minecraft:polished_tuff_slab")
    # stepping stones from the entry to the island
    for z in (20, 18, 16):
        t.put(12, FY, z, "minecraft:smooth_stone")
        t.put(11 + (z % 3), FY, z, "minecraft:gravel")
    # the east wall: scroll banners on a cedar rail, bonsai stands and a lectern
    for z in range(8, 17):
        if t.solid(22, FY + 5, z) and (t.is_air(21, FY + 5, z) or t.get(21, FY + 5, z) is None):
            t.log(21, FY + 5, z, "minecraft:stripped_spruce_log", "z")
    for z in (9, 11, 13, 15):
        if t.is_air(21, FY + 4, z) and t.solid(22, FY + 4, z):
            gulungan(t, 21, FY + 4, z, "west", "white" if z % 4 == 1 else "light_gray")
    for (x, z) in ((19, 9), (19, 15)):
        if t.is_air(x, FY + 1, z):
            bonsai(t, x, FY + 1, z)
    t.put(19, FY + 1, 12, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    # the tea mat by the stream
    t.put(5, FY + 1, 8, "minecraft:spruce_trapdoor", {"facing": "east", "half": "top", "open": "false",
                                                       "powered": "false", "waterlogged": "false"})
    t.put(5, FY + 2, 8, "minecraft:flower_pot")
    for (x, z) in ((4, 8), (5, 7), (6, 8)):
        if t.is_air(x, FY + 1, z):
            t.put(x, FY + 1, z, "minecraft:green_carpet")
    t.tong(3, FY + 1, 6, "up")
    # prayer flags across the vault and lights
    bendera_doa(t, 4, 20, 10, 9)
    bendera_doa(t, 4, 20, 9, 16)
    for (x, z) in ((7, 7), (17, 7), (7, 17), (17, 17)):
        tops = [y for y in range(FY + 2, 16) if t.is_air(x, y, z)]
        if tops:
            t.lentera_rantai(x, max(tops) + 1, z, 2)
    t.dandani(1, FY + 1, 1, 23, 15, 23, lumut=0.07, lichen=0.03, tetes=0.03, akar=0.01, jamur=0.005, sulur=0.03,
              lantai_ok=lambda b: b in ("minecraft:stone", "minecraft:mossy_cobblestone"))
    t.masuk(12, FY, 24, "south", final="minecraft:stone")
    t.pintu(0, FY, 12, "west", pool_id("gua", "gua_pertapa", "bilik"), final="minecraft:stone")
    t.pintu(24, FY, 12, "east", pool_id("gua", "gua_pertapa", "kebun"), final="minecraft:stone")
    t.jangkar(12, FY + 1, 12, "gua/pertapa", yaw=FACE_YAW["south"], protect=12, ruang=6)
    t.titik("meditasi", 12, FY + 1, 12, "north")
    t.titik("dupa", 12, FY + 1, 11, "north")
    t.titik("kursi_lantai", 5, FY + 1, 7, "south")
    t.titik("meja_tulis", 18, FY + 1, 12, "east")
    t.titik("meditasi", 12, FY + 1, 6, "north") if t.get(12, FY, 6) != "minecraft:water" else None
    t.simpan()
    return t.id


def pertapa_bilik_kitab():
    """Sleeping cell variant (13 x 9 x 13): the hermit's study. A wall of chiseled bookshelves and scroll racks, a low
    writing table with an ink pot and brush pot, a calligraphy banner, a folding screen of trapdoors around a narrow bed,
    a stove of tuff, a chest of the hermit's things."""
    t = G("gua", "gua_pertapa/bilik_kitab", 13, 9, 13)
    t.rongga(6, 4, 6, 5.0, 3.4, 5.0, lantai=FY, tanah=("minecraft:spruce_planks", "minecraft:spruce_planks", 0.0),
             kasar=0.14)
    t.rock_box(0, 0, 0, 12, 0, 12)
    t.lorong([(12, 6), (9, 6)], lebar=3, tinggi=3)
    for x in range(1, 12):
        for z in range(1, 12):
            if t.is_air(x, FY + 1, z):
                t.put(x, FY, z, "minecraft:spruce_planks" if (x + z) % 5 else "minecraft:stripped_spruce_wood",
                      None if (x + z) % 5 else {"axis": "y"})
    # the book wall to the north
    for x in range(3, 10):
        for y in (FY + 1, FY + 2):
            if t.is_air(x, y, 2) or t.get(x, y, 2) is None:
                t.rak_buku(x, y, 2, "south")
        if t.is_air(x, FY + 3, 2) or t.get(x, FY + 3, 2) is None:
            t.slab(x, FY + 3, 2, "minecraft:spruce_slab", "bottom")
            if x % 2:
                t.put(x, FY + 4, 2, "minecraft:decorated_pot", {"facing": "south", "cracked": "false",
                                                                 "waterlogged": "false"}) if t.is_air(x, FY + 4, 2) else None
    # scroll racks: fences with rolled carpets of paper on the west wall
    for z in (5, 6, 7):
        t.pagar(2, FY + 1, z, "minecraft:spruce_fence", ("north", "south"))
        t.put(2, FY + 2, z, "minecraft:white_carpet")
    if t.solid(1, FY + 3, 6) and t.is_air(2, FY + 3, 6):
        gulungan(t, 2, FY + 3, 6, "east", "white")
    # the writing table: trapdoor desk, ink and brush pots, a cushion
    t.put(6, FY + 1, 5, "minecraft:spruce_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                       "powered": "false", "waterlogged": "false"})
    t.put(7, FY + 1, 5, "minecraft:spruce_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                       "powered": "false", "waterlogged": "false"})
    t.put(6, FY + 2, 5, "minecraft:flower_pot")
    t.lilin(7, FY + 2, 5, 1, "black")
    t.put(6, FY + 1, 6, "minecraft:brown_carpet")
    t.put(7, FY + 1, 6, "minecraft:brown_carpet")
    # the bed behind a folding screen in the south-west corner
    t.kasur(3, FY + 1, 9, "north", "white")
    for x in (4, 5):
        for y in (FY + 1, FY + 2):
            t.put(x, y, 10, "minecraft:spruce_trapdoor", {"facing": "north", "half": "bottom", "open": "true",
                                                           "powered": "false", "waterlogged": "false"})
    # the tuff stove with a kettle, a chest, a water jar
    t.put(9, FY + 1, 9, "minecraft:smoker", {"facing": "west", "lit": "false"})
    t.put(9, FY + 2, 9, "minecraft:decorated_pot", {"facing": "west", "cracked": "true", "waterlogged": "false"})
    t.peti(9, FY + 1, 10, "west", loot("pertapa_bilik"))
    t.put(9, FY + 1, 8, "minecraft:water_cauldron", {"level": "2"})
    t.lentera_rantai(6, max(y for y in range(FY + 2, 8) if t.is_air(6, y, 6)) + 1, 6, 1)
    t.dandani(1, FY + 1, 1, 11, 7, 11, lumut=0.0, lichen=0.015, tetes=0.0, akar=0.02, jamur=0.0)
    t.masuk(12, FY, 6, "east", final="minecraft:spruce_planks")
    t.titik("kasur", 3, FY + 1, 9, "north")
    t.titik("meja_tulis", 6, FY + 1, 6, "north")
    t.simpan()
    return t.id


def jamur_raksasa(t, x, y, z, tinggi, merah=True):
    """A giant mushroom built of blocks: a stem `tinggi` high and a cap (red dome or brown flat)."""
    for i in range(tinggi):
        t.put(x, y + i, z, "minecraft:mushroom_stem", {"up": "false", "down": "false", "north": "true", "south": "true",
                                                       "east": "true", "west": "true"})
    cap = "minecraft:red_mushroom_block" if merah else "minecraft:brown_mushroom_block"
    yt = y + tinggi
    r = 2 if merah else 3
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if abs(dx) == r and abs(dz) == r:
                continue
            yy = yt if not merah or (abs(dx) < r and abs(dz) < r) else yt - 1
            if t.inside(x + dx, yy, z + dz) and (t.get(x + dx, yy, z + dz) is None or t.is_air(x + dx, yy, z + dz)):
                t.put(x + dx, yy, z + dz, cap, {"up": "true", "down": "false", "north": "true", "south": "true",
                                                "east": "true", "west": "true"})


def pertapa_kebun_jamur():
    """Herb grotto variant (15 x 13 x 15): the glow grotto. Giant red and brown mushrooms over a floor of mycelium and
    moss, shroomlight in the vault, glow berries, a ring of spirit herbs round a still pool with sea pickles, a drying
    rack of herbs and a stone mortar."""
    t = G("gua", "gua_pertapa/kebun_jamur", 15, 13, 15)
    t.rongga(7, 4.5, 7, 6.2, 4.2, 6.2, lantai=FY, tanah=("minecraft:mycelium", "minecraft:moss_block", 0.45), kasar=0.22)
    t.rock_box(0, 0, 0, 14, 0, 14)
    t.lorong([(0, 7), (3, 7)], lebar=3, tinggi=3)
    # the still pool with sea pickles (lit from below)
    for x in range(6, 10):
        for z in range(6, 10):
            if math.hypot(x - 7.5, z - 7.5) < 1.9 and t.is_air(x, FY + 1, z):
                t.put(x, FY - 1, z, "minecraft:clay")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
    for (x, z, n) in ((7, 7, 3), (8, 8, 2)):
        if t.get(x, FY, z) == "minecraft:water":
            t.put(x, FY, z, "minecraft:sea_pickle", {"pickles": str(n), "waterlogged": "true"})
    for x in range(4, 12):
        for z in range(4, 12):
            d = math.hypot(x - 7.5, z - 7.5)
            if 2.0 < d < 3.3 and t.is_air(x, FY + 1, z) and t.get(x, FY, z) != "minecraft:water":
                t.put(x, FY, z, "minecraft:moss_block")
                t.put(x, FY + 1, z, t.rng.choice(BUNGA + ["minecraft:red_mushroom", "minecraft:brown_mushroom"]))
    # giant mushrooms in the corners
    jamur_raksasa(t, 3, FY + 1, 3, 4, True)
    jamur_raksasa(t, 11, FY + 1, 11, 3, False)
    jamur_raksasa(t, 11, FY + 1, 3, 3, True)
    # shroomlight and glow berries in the vault
    for (x, z) in ((7, 7), (4, 10), (10, 5)):
        tops = [y for y in range(FY + 2, 12) if t.is_air(x, y, z)]
        if tops and t.solid(x, max(tops) + 1, z):
            t.put(x, max(tops) + 1, z, "minecraft:shroomlight")
    for (x, z) in ((5, 9), (9, 4), (3, 7), (8, 11)):
        tops = [y for y in range(FY + 2, 10) if t.is_air(x, y, z)]
        if tops and t.solid(x, max(tops) + 1, z):
            y0 = max(tops)
            t.put(x, y0, z, "minecraft:cave_vines_plant", {"berries": "true"})
            if t.is_air(x, y0 - 1, z):
                t.put(x, y0 - 1, z, "minecraft:cave_vines", {"berries": "true", "age": "25"})
    # the drying rack and mortar by the west entry
    for z in (10, 11, 12):
        t.pagar(3, FY + 1, z, "minecraft:spruce_fence", ("north", "south"))
        t.put(3, FY + 2, z, "minecraft:hay_block", {"axis": "z"}) if z == 11 else \
            t.put(3, FY + 2, z, "minecraft:spruce_fence", {"north": "true", "south": "true", "east": "false",
                                                           "west": "false", "waterlogged": "false"})
    t.put(12, FY + 1, 7, "minecraft:cauldron")
    t.put(12, FY + 1, 8, "minecraft:composter", {"level": "5"})
    t.put(11, FY + 1, 7, "minecraft:spruce_trapdoor", {"facing": "west", "half": "top", "open": "false",
                                                        "powered": "false", "waterlogged": "false"})
    t.put(11, FY + 2, 7, "minecraft:flower_pot")
    t.dandani(1, FY + 1, 1, 13, 11, 13, lumut=0.04, lichen=0.04, tetes=0.0, akar=0.04, jamur=0.06,
              lantai_ok=lambda b: b in ("minecraft:mycelium", "minecraft:moss_block"))
    t.masuk(0, FY, 7, "west", final="minecraft:moss_block")
    t.titik("kerja:sabit", 6, FY + 1, 10, "north")
    t.titik("kerja:lesung", 11, FY + 1, 8, "east")
    t.simpan()
    return t.id

def guci(t, x, y, z, retak=False):
    """A funerary urn: a decorated pot (cracked when old)."""
    t.put(x, y, z, "minecraft:decorated_pot", {"facing": t.rng.choice(["north", "south", "east", "west"]),
                                                "cracked": "true" if retak else "false", "waterlogged": "false"})


def patung_berlutut(t, x, y, z, facing, pal):
    """A kneeling stone attendant (2 high): a stair as the folded legs, a wall-capped body, a head of the tier's inlay."""
    t.stairs(x, y, z, pal["stair"], facing)
    t.dinding_tiang(x, y + 1, z, pal["wallb"], up=True)
    t.put(x, y + 2, z, pal["inlay"])


def makam_lorong_relung(tier, nama, ke):
    """Connector variant 'relung' (11 x 7 x 15): an ossuary gallery. Both long walls are cut into two rows of niches
    holding urns, skulls and bone bundles of the ancestor's servants, each row under a trim beam; a runner of the tier's
    carpet, low incense stands, a lantern at each third. Same connectors as makam_lorong (entry south, way on north)."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/%s_relung" % (tier, nama), 11, 7, 15, batu=pal["batu"])
    kotak_ruang(t, 1, 0, 0, 9, 6, 14, pal)
    for z in range(1, 14):
        for x in range(2, 9):
            t.put(x, 0, z, pal["floor"] if x not in (2, 8) else pal["floor_alt"])
        t.put(5, 0, z, pal["carpet"].replace("_carpet", "_wool") if z % 4 else pal["inlay"])
    for z in range(2, 13):
        for x in (1, 9):
            for y in (1, 3):
                if z % 2 == 0:
                    t.set_air(x, y, z)
                    r = t.rng.random()
                    if r < 0.35:
                        guci(t, x, y, z, retak=r < 0.12)
                    elif r < 0.6:
                        t.tengkorak(x, y, z, 12 if x == 1 else 4)
                    elif r < 0.75:
                        t.put(x, y, z, "minecraft:bone_block", {"axis": "z"})
                    else:
                        t.lilin(x, y, z, 1 + int(t.rng.random() * 3), pal["lilin"])
                else:
                    t.put(x, y, z, pal["wall_alt"] if z % 4 == 1 else pal["wall"])
            t.put(x, 2, z, pal["trim"])
            t.put(x, 4, z, pal["trim"])
    for z in (4, 10):
        for x in (3, 7):
            t.put(x, 1, z, pal["slab"], {"type": "top", "waterlogged": "false"})
            t.lilin(x, 2, z, 2, pal["lilin"])
    for z in (3, 7, 11):
        t.lentera(5, 5, z, True, pal["glow"])
    for z in (1, 13):
        for x in (2, 8):
            tiang(t, x, 0, 5, z, pal)
    pintu_jalan(t, 5, 0, 14, "south", pal)
    pintu_jalan(t, 5, 0, 0, "north", pal)
    t.masuk(5, 0, 14, "south", final=pal["floor"])
    t.pintu(5, 0, 0, "north", pool_id("gua", "makam_warisan_%d" % tier, ke), final=pal["floor"])
    t.simpan()
    return t.id


def makam_lorong_jembatan(tier, nama, ke):
    """Connector variant 'jembatan' (11 x 12 x 15): a stone bridge three wide across a dark chasm. The chasm floor lies
    six below with still black water and soul lanterns glowing up through it; chains hang from the vault to the bridge's
    parapets; two guardian lions (blocks) at each end. Entry and way on stay at the bridge level (y 6)."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/%s_jembatan" % (tier, nama), 11, 12, 15, batu=pal["batu"])
    B = 6
    kotak_ruang(t, 0, 0, 0, 10, 11, 14, pal)
    # the chasm: everything between the walls from the floor to below the bridge is open
    for x in range(1, 10):
        for z in range(3, 12):
            for y in range(1, B + 5):
                t.set_air(x, y, z)
            t.put(x, 0, z, "minecraft:black_concrete")
            t.put(x, 1, z, "minecraft:water", {"level": "0"})
    for (x, z) in ((3, 5), (7, 9), (5, 7), (2, 10), (8, 4)):
        t.put(x, 0, z, "minecraft:sea_lantern")
    # landings at both ends (z 1..2 and 12..13) at bridge level
    for z in list(range(1, 3)) + list(range(12, 14)):
        for x in range(1, 10):
            for y in range(1, B):
                t.put(x, y, z, pal["wall"])
            t.put(x, B, z, pal["floor"] if x not in (1, 9) else pal["floor_alt"])
            for y in range(B + 1, 11):
                t.set_air(x, y, z)
    # the bridge (x 4..6) and its parapets
    for z in range(3, 12):
        for x in (4, 5, 6):
            t.put(x, B, z, pal["floor"] if x != 5 else pal["inlay"])
            t.put(x, B - 1, z, pal["slab"], {"type": "top", "waterlogged": "false"}) if z not in (3, 11) else \
                t.put(x, B - 1, z, pal["wall"])
        for x in (3, 7):
            t.put(x, B, z, pal["wall_alt"])
            t.dinding_tiang(x, B + 1, z, pal["wallb"], up=(z % 2 == 1))
    # the arch under the bridge: two piers standing in the black water
    for z in (6, 8):
        for x in (4, 6):
            for y in range(1, B):
                t.put(x, y, z, pal["wall"])
    # chains from the vault to the parapet posts
    for z in (5, 9):
        for x in (3, 7):
            for y in range(B + 2, 11):
                t.put(x, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    # guardian lions: a slab base, a stair body facing the bridge, a head of inlay
    for (x, z, f) in ((2, 2, "south"), (8, 2, "south"), (2, 12, "north"), (8, 12, "north")):
        t.put(x, B + 1, z, pal["accent"])
        t.stairs(x, B + 2, z, pal["stair"], f)
        t.put(x, B + 3, z, pal["inlay"])
    for z in (1, 13):
        t.lentera(5, 10, z, True, pal["glow"])
    t.lentera_rantai(5, 11, 7, 3, pal["glow"])
    pintu_jalan(t, 5, B, 14, "south", pal)
    pintu_jalan(t, 5, B, 0, "north", pal)
    t.masuk(5, B, 14, "south", final=pal["floor"])
    t.pintu(5, B, 0, "north", pool_id("gua", "makam_warisan_%d" % tier, ke), final=pal["floor"])
    t.simpan()
    return t.id


def makam_lorong_persembahan(tier, nama, ke):
    """Connector variant 'persembahan' (13 x 8 x 13): the offering gallery before the tomb hall. Kneeling stone
    attendants line both walls facing the way on, offering tables carry urns, fruit pots and incense, a gilded
    ancestor tablet hangs over the north door between two banners."""
    pal = MAKAM_PAL[tier]
    t = G("gua", "makam_warisan_%d/%s_persembahan" % (tier, nama), 13, 8, 13, batu=pal["batu"])
    kotak_ruang(t, 1, 0, 0, 11, 7, 12, pal)
    for z in range(1, 12):
        for x in range(2, 11):
            t.put(x, 0, z, pal["floor"] if (x + z) % 3 else pal["floor_alt"])
        t.put(6, 0, z, pal["inlay"])
        t.put(5, 0, z, pal["carpet"].replace("_carpet", "_wool"))
        t.put(7, 0, z, pal["carpet"].replace("_carpet", "_wool"))
    for z in (3, 6, 9):
        patung_berlutut(t, 2, 1, z, "east", pal)
        patung_berlutut(t, 10, 1, z, "west", pal)
    for z in (4, 8):
        for x in (3, 9):
            t.put(x, 1, z, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                            "powered": "false", "waterlogged": "false"})
            if z == 4:
                guci(t, x, 2, z)
            else:
                t.lilin(x, 2, z, 3, pal["lilin"])
    # the ancestor tablet over the north door
    for x in range(4, 9):
        t.put(x, 5, 1, pal["trim"])
    t.put(6, 6, 1, pal["metal"])
    t.put(6, 5, 1, pal["accent"])
    for x in (3, 9):
        t.put(x, 5, 2, "minecraft:%s_wall_banner" % ("red" if tier == 2 else "white" if tier == 1 else "cyan"),
              {"facing": "south"})
    for (x, z) in ((2, 1), (10, 1), (2, 11), (10, 11)):
        tiang(t, x, 0, 6, z, pal)
    t.lentera_rantai(6, 7, 6, 1, pal["glow"])
    t.lentera(4, 6, 10, True, pal["glow"])
    t.lentera(8, 6, 10, True, pal["glow"])
    pintu_jalan(t, 6, 0, 12, "south", pal)
    pintu_jalan(t, 6, 0, 0, "north", pal)
    t.masuk(6, 0, 12, "south", final=pal["floor"])
    t.pintu(6, 0, 0, "north", pool_id("gua", "makam_warisan_%d" % tier, ke), final=pal["floor"])
    t.titik("dupa", 6, 1, 3, "north")
    t.simpan()
    return t.id

def iblis_kebun_racun():
    """Pill lab variant (17 x 10 x 17): the poison garden. Raised beds of soul soil with crimson and warped growth,
    nether sprouts and mushrooms under soul lanterns, a pond of murky water, a herb-drying frame of chains and hay, a
    grindstone and a blast furnace (cold) for the forbidden pills, jars of tinted glass, two chests of the lab's loot."""
    t = G("gua", "sarang_iblis/kebun_racun", 17, 10, 17)
    kotak_ruang(t, 0, 0, 0, 16, 8, 16, IBLIS)
    pintu_jalan(t, 0, 0, 8, "west", IBLIS)
    for x in range(1, 16):
        for z in range(1, 16):
            t.put(x, 0, z, IBLIS["floor"] if (x * 3 + z) % 7 else IBLIS["floor_alt"])
    # four raised beds with growth
    tumbuh = ["minecraft:crimson_roots", "minecraft:warped_roots", "minecraft:nether_sprouts", "minecraft:crimson_fungus",
              "minecraft:warped_fungus", "minecraft:red_mushroom", "minecraft:brown_mushroom"]
    for (x0, z0) in ((3, 2), (10, 2), (3, 11), (10, 11)):
        for x in range(x0, x0 + 4):
            for z in range(z0, z0 + 3):
                edge = x in (x0, x0 + 3) or z in (z0, z0 + 2)
                if edge:
                    t.put(x, 1, z, "minecraft:polished_blackstone_brick_slab", {"type": "top", "waterlogged": "false"}) \
                        if (x + z) % 2 else t.put(x, 1, z, IBLIS["wall"])
                else:
                    t.put(x, 1, z, "minecraft:soul_soil")
                    t.put(x, 2, z, t.rng.choice(tumbuh))
    # the murky pond in the middle: mud bed, still water one up, a rim of brick around it
    for x in range(5, 12):
        for z in range(5, 12):
            d = math.hypot(x - 8, z - 8)
            if d < 1.9:
                t.put(x, 0, z, "minecraft:mud")
                t.put(x, 1, z, "minecraft:water", {"level": "0"})
            elif d < 2.9:
                t.put(x, 1, z, IBLIS["wall"] if (x + z) % 2 else IBLIS["wall_alt"])
                if t.rng.random() < 0.3:
                    t.put(x, 2, z, t.rng.choice(["minecraft:crimson_roots", "minecraft:nether_sprouts"]))
    t.put(8, 1, 8, "minecraft:big_dripleaf_stem", {"facing": "south", "waterlogged": "true"})
    t.put(8, 2, 8, "minecraft:big_dripleaf", {"facing": "south", "tilt": "none", "waterlogged": "false"})
    # the drying frame: chains from the ceiling holding hay bundles, over the east wall
    for z in (5, 8, 11):
        for y in (6, 7):
            t.put(14, y, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        t.put(14, 5, z, "minecraft:hay_block", {"axis": "y"})
    # the pill bench: grindstone, cold blast furnace, tinted jars, brewing stand
    t.put(15, 1, 4, "minecraft:blast_furnace", {"facing": "west", "lit": "false"})
    t.put(15, 1, 6, "minecraft:grindstone", {"face": "floor", "facing": "west"})
    for z in range(9, 13):
        t.slab(15, 1, z, IBLIS["slab"], "top")
    t.put(15, 2, 9, "minecraft:tinted_glass")
    t.put(15, 2, 10, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "true", "has_bottle_2": "true"})
    t.put(15, 2, 11, "minecraft:tinted_glass")
    t.tengkorak(15, 2, 12, 4)
    t.peti(15, 1, 14, "west", loot("sarang_iblis_racik"))
    t.peti(1, 1, 14, "east", loot("sarang_iblis_racik"))
    for (x, z) in ((5, 8), (11, 8), (8, 4), (8, 13)):
        t.lentera(x, 7, z, True, IBLIS["glow"])
    for _ in range(10):
        darah(t, 1 + int(t.rng.random() * 15), 0, 1 + int(t.rng.random() * 15))
    t.masuk(0, 0, 8, "west", final=IBLIS["floor"])
    t.titik("kerja:lumpang", 14, 1, 6, "east")
    t.titik("kerja:sabit", 5, 1, 4, "north")
    t.titik("kerja:kuali", 8, 1, 9, "north")
    t.simpan()
    return t.id


def iblis_blok_sel():
    """Prison variant (25 x 9 x 17): a cell block of six cells, three on each side of a wide corridor, the corridor
    floor sloping to a drain grate; a warden's desk with a ledger and a key board, a water trough, rows of manacles.
    Cage doors are entities (anchor gua/iblis_sel); a captive sits three blocks behind each door as in sarang_iblis/sel."""
    t = G("gua", "sarang_iblis/blok_sel", 25, 9, 17)
    kotak_ruang(t, 0, 0, 0, 24, 6, 16, IBLIS)
    pintu_jalan(t, 24, 0, 8, "east", IBLIS)
    for x in range(1, 24):
        for z in range(6, 11):
            t.put(x, 0, z, IBLIS["floor"] if z != 8 else IBLIS["inlay"])
    for x in (6, 12, 18):
        t.put(x, 0, 8, "minecraft:iron_trapdoor", {"facing": "north", "half": "top", "open": "false", "powered": "false",
                                                   "waterlogged": "false"})
    sel = []
    cells = []
    for i, x0 in enumerate((2, 8, 14)):
        cells.append((x0, 1, 4, 5, "south"))
        cells.append((x0, 12, 15, 11, "north"))
    for (x0, z0, z1, door_z, face) in cells:
        for x in range(x0, x0 + 5):
            for y in range(1, 6):
                bars = 1 <= y <= 4 and x0 < x < x0 + 4
                t.put(x, y, door_z, "minecraft:iron_bars" if bars else IBLIS["wall"],
                      {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"}
                      if bars else None)
        for x in (x0, x0 + 4):
            for z in range(z0, z1 + 1):
                for y in range(1, 6):
                    t.put(x, y, z, IBLIS["wall"])
        dx = x0 + 2
        for y in (1, 2):
            t.set_air(dx, y, door_z)
        for z in range(z0, z1 + 1):
            for x in range(x0 + 1, x0 + 4):
                r = t.rng.random()
                if r < 0.35:
                    t.put(x, 1, z, "minecraft:yellow_carpet")
                elif r < 0.45:
                    t.put(x, 1, z, "minecraft:hay_block", {"axis": "x"})
        back = z0 if face == "south" else z1
        t.put(x0 + 1, 3, back, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        t.put(x0 + 3, 3, back, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        t.put(x0 + 3, 1, back, "minecraft:cauldron") if t.rng.random() < 0.5 else None
        sel.append((dx - 12, door_z - 8, 0 if face == "south" else 180))
    # the warden's desk at the east end, the trough, the key board
    for z in (6, 7):
        t.put(21, 1, z, "minecraft:dark_oak_trapdoor", {"facing": "west", "half": "top", "open": "false",
                                                         "powered": "false", "waterlogged": "false"})
    t.put(21, 2, 6, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    t.lilin(21, 2, 7, 2, "red")
    t.stairs(22, 1, 7, "minecraft:dark_oak_stairs", "east")
    for z in (10, 11, 12):
        t.put(23, 3, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"}) if z != 11 else \
            t.put(23, 3, z, "minecraft:tripwire_hook", {"attached": "false", "facing": "west", "powered": "false"})
    for x in (21, 22):
        t.put(x, 1, 10, "minecraft:water_cauldron", {"level": "1"})
    t.tong(22, 1, 12, "up", loot("sarang_iblis_jaga"))
    for x in (5, 12, 19):
        t.lentera(x, 5, 8, True, IBLIS["glow"])
    for _ in range(14):
        darah(t, 1 + int(t.rng.random() * 23), 0, 6 + int(t.rng.random() * 5))
    t.masuk(24, 0, 8, "east", final=IBLIS["floor"])
    t.jangkar(12, 1, 8, "gua/iblis_sel", yaw=FACE_YAW["south"], protect=8, sel=";".join("%d,%d,%d" % s for s in sel))
    for i, (x0, z0, z1, door_z, face) in enumerate(cells):
        t.titik("sel_tawanan", x0 + 2, 1, (z0 + 1) if face == "south" else (z1 - 1), face, {"sel": i})
    t.titik("jaga", 22, 1, 7, "west")
    t.titik("kursi", 22, 1, 7, "west")
    t.simpan()
    return t.id


def sarang_rusuk():
    """Den chamber variant (25 x 14 x 25): the ribcage den. The skeleton of a great beast lies half sunk in the floor,
    its spine of bone blocks running north to south and eleven pairs of ribs arching over the nest of hay; its skull
    rests by the north wall with an eye socket for a lair; gnawed bones, a pool of dark water, hanging roots."""
    t = G("gua", "sarang_binatang/sarang_rusuk", 25, 14, 25, batu="tanah")
    t.rongga(12, 4.6, 12, 10.5, 4.6, 10.5, lantai=FY, tanah=("minecraft:coarse_dirt", "minecraft:podzol", 0.5), kasar=0.28)
    t.rock_box(0, 0, 0, 24, 0, 24)
    t.lorong([(12, 24), (12, 20)], lebar=4, tinggi=4)
    # spine along x = 12 from z 4 to z 19, slightly sunk
    for z in range(4, 20):
        t.put(12, FY, z, "minecraft:bone_block", {"axis": "z"})
        if z % 2 == 0:
            t.put(12, FY + 1, z, "minecraft:bone_block", {"axis": "y"}) if 6 <= z <= 17 else None
    # ribs: arcs over the spine, open between them
    for z in range(6, 18, 2):
        span = 4.5 if 8 <= z <= 15 else 3.5
        for k in range(0, 19):
            a = math.pi * k / 18.0
            x = 12 + math.cos(a) * span
            y = FY + 1 + math.sin(a) * (span + 1.2)
            ix, iy = int(round(x)), int(round(y))
            if t.inside(ix, iy, z) and (t.is_air(ix, iy, z) or t.get(ix, iy, z) is None):
                t.put(ix, iy, z, "minecraft:bone_block", {"axis": "y" if 3 < k < 15 else "x"})
    # the skull by the north wall: a hollow box of bone with eye sockets
    for x in range(9, 16):
        for y in range(FY + 1, FY + 5):
            for z in range(1, 4):
                if t.inside(x, y, z):
                    shell = x in (9, 15) or y in (FY + 1, FY + 4) or z == 1
                    t.put(x, y, z, "minecraft:bone_block" if shell else "minecraft:air", {"axis": "y"} if shell else None)
    for x in (10, 14):
        t.set_air(x, FY + 3, 3)
        t.put(x, FY + 3, 2, "minecraft:shroomlight")
    for x in range(10, 15, 2):
        t.put(x, FY + 1, 4, "minecraft:pointed_dripstone", {"thickness": "tip", "vertical_direction": "up",
                                                            "waterlogged": "false"})
    # the nest under the ribs
    for x in range(9, 16):
        for z in range(9, 15):
            d = math.hypot(x - 12, z - 12)
            if d < 2.6 and x != 12:
                t.put(x, FY + 1, z, "minecraft:hay_block", {"axis": "y"}) if t.is_air(x, FY + 1, z) else None
            elif d < 4.0 and t.rng.random() < 0.3 and t.is_air(x, FY + 1, z):
                tulang(t, x, FY + 1, z)
    for x in range(18, 22):
        for z in range(15, 20):
            if t.is_air(x, FY + 1, z) and math.hypot(x - 20, z - 17) < 2.0:
                t.put(x, FY - 1, z, "minecraft:mud")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
    t.peti(4, FY + 1, 7, "east", loot("sarang_binatang"))
    t.dandani(1, FY + 1, 1, 23, 12, 23, lumut=0.03, lichen=0.03, tetes=0.0, akar=0.09, jamur=0.03,
              lantai_ok=lambda b: b in ("minecraft:coarse_dirt", "minecraft:podzol"))
    t.masuk(12, FY, 24, "south", final="minecraft:coarse_dirt")
    t.jangkar(12, FY + 2, 12, "gua/sarang_bos", yaw=FACE_YAW["south"], protect=0, sarang=5)
    t.titik("jaga", 12, FY + 2, 12, "south")
    t.simpan()
    return t.id


def urat_kemah():
    """Miners' tunnel variant (13 x 8 x 22): the abandoned camp. The tunnel opens into a timbered chamber halfway: two
    bedrolls by a cold campfire, crates and a barrel of the miners' stores, a rail spur ending at a buffer, a pick on a
    rack, a table of amethyst samples and a lantern; then the tunnel goes on to rubble."""
    t = G("gua", "gua_urat_roh/kemah", 13, 8, 22, batu="dalam")
    t.lorong([(6, 21), (6, 15)], lebar=3, tinggi=4, lantai=FY, lantai_blok="minecraft:cobbled_deepslate")
    t.lorong([(6, 6), (5, 2)], lebar=3, tinggi=4, lantai=FY, lantai_blok="minecraft:cobbled_deepslate")
    t.rongga(6, 3.2, 10.5, 5.0, 2.6, 4.6, lantai=FY, tanah=("minecraft:cobbled_deepslate", "minecraft:tuff", 0.3),
             kasar=0.15)
    for z in (6, 15):
        for x in (3, 9):
            for y in range(FY + 1, FY + 5):
                t.log(x, y, z, "minecraft:spruce_log", "y")
        for x in range(3, 10):
            t.log(x, FY + 5, z, "minecraft:spruce_log", "x")
    for z in range(8, 14, 2):
        for x in range(3, 10):
            if t.is_air(x, FY + 5, z):
                t.log(x, FY + 5, z, "minecraft:stripped_spruce_log", "x")
    # the camp
    t.put(6, FY + 1, 11, "minecraft:campfire", {"facing": "south", "lit": "false", "signal_fire": "false",
                                               "waterlogged": "false"})
    for (x, z) in ((4, 9), (8, 13)):
        t.put(x, FY + 1, z, "minecraft:brown_carpet")
        t.put(x, FY + 1, z + (1 if z < 11 else -1), "minecraft:white_carpet")
    for (x, z, f) in ((8, 9, "west"), (4, 13, "east")):
        t.stairs(x, FY + 1, z, "minecraft:spruce_stairs", f)
    t.tong(3, FY + 1, 11, "up", loot("urat_roh_penambang"))
    t.tong(3, FY + 2, 11, "up")
    t.put(3, FY + 1, 12, "minecraft:barrel", {"facing": "east", "open": "false"})
    t.put(9, FY + 1, 11, "minecraft:spruce_trapdoor", {"facing": "west", "half": "top", "open": "false",
                                                        "powered": "false", "waterlogged": "false"})
    t.put(9, FY + 1, 10, "minecraft:spruce_trapdoor", {"facing": "west", "half": "top", "open": "false",
                                                        "powered": "false", "waterlogged": "false"})
    t.put(9, FY + 2, 11, "minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    t.put(9, FY + 2, 10, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    t.pagar(9, FY + 1, 8, "minecraft:spruce_fence")
    t.put(9, FY + 2, 8, "minecraft:spruce_fence", {"north": "false", "south": "false", "east": "false", "west": "false",
                                                   "waterlogged": "false"})
    # the rail spur along the tunnel, ending at a buffer of spruce
    for z in range(15, 21):
        t.put(6, FY + 1, z, "minecraft:rail", {"shape": "north_south", "waterlogged": "false"}) if t.is_air(6, FY + 1, z) else None
    t.put(6, FY + 1, 14, "minecraft:rail", {"shape": "north_south", "waterlogged": "false"})
    t.put(6, FY + 1, 13, "minecraft:stripped_spruce_wood", {"axis": "y"})
    for x in range(4, 9):
        for y in range(FY + 1, FY + 4):
            if t.rng.random() < 0.6 and t.is_air(x, y, 2):
                t.put(x, y, 2, "minecraft:cobbled_deepslate" if t.rng.random() < 0.6 else "minecraft:gravel")
    for (x, z) in ((2, 8), (10, 13), (4, 4)):
        t.put(x, FY + 2, z, "minecraft:amethyst_block") if t.get(x, FY + 2, z) is not None and t._is_rock(t.get(x, FY + 2, z)) else None
    t.lentera_rantai(6, FY + 5, 9, 1)
    t.dandani(1, FY + 1, 1, 11, 6, 21, lumut=0.0, lichen=0.05, tetes=0.04, akar=0.0, jamur=0.0)
    t.masuk(6, FY, 21, "south", final="minecraft:cobbled_deepslate")
    t.titik("kasur", 4, FY + 1, 9, "south")
    t.titik("kursi_lantai", 8, FY + 1, 13, "west")
    t.simpan()
    return t.id


def cermin_kolam_bakau():
    """Heart-mirror grotto variant (19 x 11 x 19): the mangrove grotto. Mangrove roots creep down the walls and stand
    in the still black pool, pale hanging moss and fireflies' bushes at the edges, the stone mirror sits under a root
    arch on the north wall; the moss cushion and black candles face it. Same anchor offsets as cermin_hati/kolam."""
    t = G("gua", "cermin_hati/kolam_bakau", 19, 11, 19, batu="rawa")
    t.rongga(9, 4.4, 9, 8.0, 4.2, 8.0, lantai=FY, tanah=("minecraft:mud", "minecraft:packed_mud", 0.4), kasar=0.16)
    t.rock_box(0, 0, 0, 18, 0, 18)
    t.lorong([(9, 18), (9, 15)], lebar=3, tinggi=3)
    for x in range(3, 16):
        for z in range(4, 14):
            d = math.hypot((x - 9) / 6.0, (z - 9) / 4.6)
            if d < 1.0 and t.is_air(x, FY + 1, z):
                t.put(x, FY - 1, z, "minecraft:black_concrete")
                t.put(x, FY, z, "minecraft:water", {"level": "0"})
            elif d < 1.2 and t.is_air(x, FY + 1, z):
                t.put(x, FY, z, "minecraft:mud_bricks")
    # mangrove roots: columns from the vault down into the pool edge and along the walls
    for (x, z) in ((4, 7), (14, 7), (5, 12), (13, 12), (3, 10), (15, 10), (7, 5), (11, 5)):
        tops = [y for y in range(FY + 1, 10) if t.is_air(x, y, z) or t.get(x, y, z) == "minecraft:water"]
        if not tops:
            continue
        for y in range(FY, max(tops) + 1):
            if t.get(x, y, z) == "minecraft:water":
                t.put(x, y, z, "minecraft:mangrove_roots", {"waterlogged": "true"})
            elif t.is_air(x, y, z):
                t.put(x, y, z, "minecraft:mangrove_roots", {"waterlogged": "false"})
    # the mirror under a root arch
    for x in range(6, 13):
        for y in range(FY + 1, FY + 7):
            edge = x in (6, 12) or y in (FY + 1, FY + 6)
            t.put(x, y, 2, "minecraft:chiseled_polished_blackstone" if edge else "minecraft:polished_blackstone")
            for z in (0, 1):
                t.rock(x, y, z, only_empty=False)
    for x in range(5, 14):
        y = FY + 7 + (1 if 7 <= x <= 11 else 0)
        if t.is_air(x, y, 3) or t.get(x, y, 3) is None:
            t.put(x, y, 3, "minecraft:muddy_mangrove_roots", {"axis": "x"})
    for x in (5, 13):
        for y in range(FY + 1, FY + 8):
            if t.is_air(x, y, 3):
                t.put(x, y, 3, "minecraft:mangrove_roots", {"waterlogged": "false"})
    t.put(9, FY + 7, 2, "minecraft:gilded_blackstone")
    t.put(9, FY + 6, 3, "minecraft:soul_lantern", {"hanging": "true", "waterlogged": "false"}) if t.is_air(9, FY + 6, 3) else None
    t.put(9, FY, 14, "minecraft:moss_block")
    t.put(9, FY + 1, 14, "minecraft:moss_carpet")
    for (x, z) in ((7, 14), (11, 14), (6, 13), (12, 13)):
        if t.is_air(x, FY + 1, z):
            t.lilin(x, FY + 1, z, 3, "black")
    for (x, z) in ((3, 14), (15, 14), (2, 9), (16, 9)):
        if t.is_air(x, FY + 1, z) and t.solid(x, FY, z):
            t.put(x, FY + 1, z, "minecraft:firefly_bush")
    for x in range(2, 17):
        for z in range(2, 17):
            tops = [y for y in range(FY + 3, 10) if t.is_air(x, y, z)]
            if tops and t.solid(x, max(tops) + 1, z) and t.rng.random() < 0.08:
                y0 = max(tops)
                t.put(x, y0, z, "minecraft:pale_hanging_moss", {"tip": "false"})
                if t.is_air(x, y0 - 1, z):
                    t.put(x, y0 - 1, z, "minecraft:pale_hanging_moss", {"tip": "true"})
                else:
                    t.put(x, y0, z, "minecraft:pale_hanging_moss", {"tip": "true"})
    t.dandani(1, FY + 1, 1, 17, 9, 17, lumut=0.06, lichen=0.0, tetes=0.0, akar=0.05, jamur=0.01,
              lantai_ok=lambda b: b in ("minecraft:mud", "minecraft:packed_mud"))
    t.masuk(9, FY, 18, "south", final="minecraft:mud")
    t.jangkar(9, FY + 1, 14, "gua/cermin_kolam", yaw=FACE_YAW["north"], protect=8, cx=0, cz=-11)
    t.titik("meditasi", 9, FY + 1, 14, "north")
    t.simpan()
    return t.id

def situs_puncak_petir_awan():
    """Puncak Petir variant 'Teras Awan' (31 x 24 x 31): the same terrace plan (altar, nine pillars, record stone,
    spectators' strips, entry steps south) dressed as a weathered mountain shrine: pillars of scorched blackstone with
    copper crowns, a rough monolith of andesite and tuff for the Batu Catatan with a copper plaque, two roofed galleries
    of dark oak over the spectators' benches (east, west), a great bronze drum on a stand, prayer flags between the
    pillars. Anchors and offsets exactly as situs/puncak_petir (altar top F+3, board at bx 0 bz -9, watchers x +-9)."""
    t = G("situs", "puncak_petir_awan", 31, 24, 31, palet="gua", batu="gunung")
    F = 4
    c = 15
    for x in range(31):
        for z in range(31):
            inside = 4 <= x <= 26 and 4 <= z <= 26
            for y in range(0, F):
                if inside or (3 <= x <= 27 and 3 <= z <= 27 and y < F - 1):
                    t.put(x, y, z, "minecraft:andesite" if not inside else
                          ("minecraft:stone_bricks" if t.rng.random() < 0.7 else "minecraft:cracked_stone_bricks"))
            if inside:
                edge = x in (4, 26) or z in (4, 26)
                n = noise3(x, 0, z, 3.0, t.nseed)
                t.put(x, F, z, "minecraft:polished_andesite" if edge else
                      ("minecraft:cobblestone" if n < 0.25 else "minecraft:stone_bricks" if n < 0.7 else
                       "minecraft:mossy_stone_bricks"))
                for y in range(F + 1, 24):
                    t.set_air(x, y, z)
    # the scorch star: blackstone rays where the strikes fell, copper at the heart
    for k in range(8):
        a = k * math.pi / 4
        for s in range(2, 11):
            x, z = int(round(c + math.cos(a) * s)), int(round(c + math.sin(a) * s))
            if t.rng.random() < 0.8 - s * 0.04:
                t.put(x, F, z, "minecraft:blackstone" if s % 3 else "minecraft:polished_blackstone")
    for (r, y, bid) in ((5, F, "minecraft:polished_tuff"), (4, F + 1, "minecraft:tuff_bricks"), (2, F + 2, "minecraft:polished_tuff")):
        t.disc(c, y, c, r, bid)
    t.ring(c, F + 1, c, 4, "minecraft:tuff_brick_slab")
    t.put(c, F + 2, c, "minecraft:waxed_oxidized_copper")
    t.slab(c, F + 3, c, "minecraft:polished_tuff_slab")
    pil = []
    for k in range(9):
        a = k * 2 * math.pi / 9 - math.pi / 2
        px, pz = int(round(c + math.cos(a) * 9)), int(round(c + math.sin(a) * 9))
        pil.append((px, pz))
        t.put(px, F, pz, "minecraft:chiseled_polished_blackstone")
        for y in range(F + 1, F + 7):
            t.put(px, y, pz, "minecraft:polished_blackstone" if y % 2 else "minecraft:gilded_blackstone" if y == F + 4
                  else "minecraft:polished_blackstone_bricks")
        t.put(px, F + 7, pz, "minecraft:waxed_weathered_chiseled_copper")
        t.put(px, F + 8, pz, "minecraft:waxed_weathered_copper_bulb", {"lit": "false", "powered": "false"})
    for i in range(9):
        (ax, az), (bx, bz) = pil[i], pil[(i + 1) % 9]
        steps = max(abs(bx - ax), abs(bz - az))
        warna = ["white", "red", "yellow", "lime", "light_blue"]
        for s in range(1, steps):
            x = int(round(ax + (bx - ax) * s / steps))
            z = int(round(az + (bz - az) * s / steps))
            if t.is_air(x, F + 6, z):
                t.put(x, F + 6, z, "minecraft:%s_carpet" % warna[(i + s) % 5]) if s % 2 else None
    # the record monolith on the north edge (x c-2..c+2, z 6), a copper plaque on its face
    for x in range(c - 3, c + 4):
        t.put(x, F + 1, 6, "minecraft:polished_andesite")
    for x in range(c - 2, c + 3):
        h = F + 8 if abs(x - c) < 2 else F + 6
        for y in range(F + 2, h):
            t.put(x, y, 6, "minecraft:andesite" if (x + y) % 3 else "minecraft:tuff")
        t.put(x, h, 6, "minecraft:tuff_wall", {"east": "none", "north": "none", "south": "none", "west": "none",
                                                "up": "true", "waterlogged": "false"}) if abs(x - c) >= 2 else None
    for x in range(c - 1, c + 2):
        t.put(x, F + 5, 7, "minecraft:waxed_cut_copper_slab", {"type": "top", "waterlogged": "false"}) if x != c else None
    t.put(c, F + 8, 6, "minecraft:waxed_chiseled_copper")
    t.put(c, F + 9, 6, "minecraft:waxed_copper_bulb",
          {"lit": "false", "powered": "false"})
    # balustrade, benches and the roofed galleries east and west
    for i in range(4, 27):
        for (x, z) in ((i, 4), (i, 26), (4, i), (26, i)):
            if c - 1 <= x <= c + 1 and z == 26:
                continue
            t.dinding_tiang(x, F + 1, z, "minecraft:andesite_wall", up=(i % 4 == 0))
    for z in range(9, 22):
        if z % 3 == 0:
            continue
        t.stairs(6, F + 1, z, "minecraft:dark_oak_stairs", "west")
        t.stairs(24, F + 1, z, "minecraft:dark_oak_stairs", "east")
    for gx in (5, 25):
        for z in (8, 12, 18, 22):
            for y in range(F + 1, F + 5):
                t.log(gx, y, z, "minecraft:stripped_dark_oak_log", "y")
        for z in range(7, 24):
            for dx in (-1, 0, 1):
                y = F + 5 + (1 if dx == 0 else 0)
                bid = "minecraft:dark_oak_slab" if dx != 0 else "minecraft:dark_oak_planks"
                if t.inside(gx + dx, y, z):
                    t.put(gx + dx, y, z, bid, {"type": "bottom", "waterlogged": "false"} if dx != 0 else None)
        t.lentera(gx, F + 4, 15, True)
    # the bronze drum on a stand by the south-east corner
    t.put(21, F + 1, 22, "minecraft:dark_oak_fence", {"north": "false", "south": "false", "east": "false",
                                                      "west": "false", "waterlogged": "false"})
    t.put(21, F + 2, 22, "minecraft:waxed_exposed_copper")
    t.put(21, F + 3, 22, "minecraft:brown_carpet")
    t.put(20, F + 1, 22, "minecraft:dark_oak_fence", {"north": "false", "south": "false", "east": "true",
                                                      "west": "false", "waterlogged": "false"})
    # entry steps south
    for i in range(4):
        for x in range(c - 1, c + 2):
            t.stairs(x, F - i, 27 + i, "minecraft:stone_brick_stairs", "north")
    t.jangkar(c, F + 1, c + 6, "gua/situs", situs="situs/puncak_petir", tier=2, protect=0)
    t.jangkar(c, F + 3, c, "gua/petir_altar", yaw=FACE_YAW["south"], protect=12, r=11, bx=0, bz=-9)
    for z in range(9, 22, 2):
        t.titik("penonton", 6, F + 1, z, "east")
        t.titik("penonton", 24, F + 1, z, "west")
    t.kotak("panggung", c - 4, F + 1, c - 4, c + 4, F + 3, c + 4)
    t.simpan()
    return t.id


def situs_balai_bintang():
    """Panggung Bintang variant 'Balai Bintang' (27 x 18 x 27): an octagonal stage of polished diorite and quartz with
    the twenty-eight lunar mansions laid in glazed tiles round the rim, a sundial plate with an end-rod gnomon south of
    the armillary pedestal, eight stone posts with lanterns and star banners, a small pavilion on the north edge with a
    star chart desk. Same anchor contract (gua/bintang_panggung at the centre, r 10)."""
    t = G("situs", "balai_bintang", 27, 18, 27, palet="gua", batu="gunung")
    F = 3
    c = 13
    def oktagon(x, z, r):
        dx, dz = abs(x - c), abs(z - c)
        return max(dx, dz) <= r and dx + dz <= r * 1.42
    for x in range(27):
        for z in range(27):
            if oktagon(x, z, 11):
                for y in range(0, F):
                    t.put(x, y, z, "minecraft:stone_bricks")
                t.put(x, F, z, "minecraft:polished_diorite" if oktagon(x, z, 9) else "minecraft:quartz_bricks")
                for y in range(F + 1, 18):
                    t.set_air(x, y, z)
    tiles = ["minecraft:light_blue_glazed_terracotta", "minecraft:blue_glazed_terracotta",
             "minecraft:cyan_glazed_terracotta", "minecraft:white_glazed_terracotta"]
    n = 0
    for x in range(27):
        for z in range(27):
            if oktagon(x, z, 10) and not oktagon(x, z, 9):
                t.put(x, F, z, tiles[n % 4], {"facing": ["north", "east", "south", "west"][(x + z) % 4]})
                n += 1
    t.ring(c, F, c, 5, "minecraft:lapis_block")
    for k in range(7):
        a = k * 2 * math.pi / 7
        x, z = int(round(c + math.cos(a) * 3)), int(round(c + math.sin(a) * 3))
        t.put(x, F, z, "minecraft:gold_block")
    # the armillary pedestal (the rings are a client model) and the sundial with its gnomon
    t.disc(c, F + 1, c, 1, "minecraft:chiseled_quartz_block")
    t.put(c, F + 2, c, "minecraft:quartz_pillar", {"axis": "y"})
    t.put(c, F + 3, c, "minecraft:lodestone")
    t.disc(c, F + 1, c + 6, 1, "minecraft:smooth_stone_slab")
    t.put(c, F + 1, c + 6, "minecraft:polished_diorite")
    t.put(c, F + 2, c + 6, "minecraft:end_rod", {"facing": "up"})
    # posts with lanterns and star banners
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, z = int(round(c + math.cos(a) * 10)), int(round(c + math.sin(a) * 10))
        for y in range(F + 1, F + 4):
            t.put(x, y, z, "minecraft:diorite_wall", {"east": "none", "north": "none", "south": "none", "west": "none",
                                                     "up": "true", "waterlogged": "false"})
        t.lentera(x, F + 4, z, False)
    # the north pavilion with the star chart desk
    for x in range(c - 3, c + 4):
        for z in range(2, 6):
            t.put(x, F, z, "minecraft:dark_oak_planks")
    for (x, z) in ((c - 3, 2), (c + 3, 2), (c - 3, 5), (c + 3, 5)):
        for y in range(F + 1, F + 4):
            t.log(x, y, z, "minecraft:stripped_dark_oak_log", "y")
    for x in range(c - 4, c + 5):
        t.stairs(x, F + 4, 1, "minecraft:deepslate_tile_stairs", "south")
        t.stairs(x, F + 4, 6, "minecraft:deepslate_tile_stairs", "north")
        for z in range(2, 6):
            t.put(x, F + 4, z, "minecraft:deepslate_tiles")
        for z in (3, 4):
            t.slab(x, F + 5, z, "minecraft:deepslate_tile_slab")
    for x in range(c - 1, c + 2):
        t.put(x, F + 1, 3, "minecraft:dark_oak_trapdoor", {"facing": "south", "half": "top", "open": "false",
                                                            "powered": "false", "waterlogged": "false"})
    t.put(c, F + 2, 3, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
    t.put(c - 1, F + 2, 3, "minecraft:candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    t.put(c + 1, F + 2, 3, "minecraft:decorated_pot", {"facing": "south", "cracked": "false", "waterlogged": "false"})
    t.lentera(c, F + 3, 4, True)
    for x in (c - 2, c + 2):
        t.put(x, F + 3, 2, "minecraft:blue_wall_banner", {"facing": "south"})
    for i in range(3):
        for x in range(c - 1, c + 2):
            t.stairs(x, F - i, 24 + i if 24 + i < 27 else 26, "minecraft:quartz_stairs", "north")
    t.jangkar(c, F + 1, c + 4, "gua/situs", situs="situs/panggung_bintang", tier=2, protect=0)
    t.jangkar(c, F + 1, c, "gua/bintang_panggung", yaw=FACE_YAW["south"], protect=12, r=10)
    for k in range(8):
        a = k * math.pi / 4
        x, z = int(round(c + math.cos(a) * 8)), int(round(c + math.sin(a) * 8))
        t.titik("pandang", x, F + 1, z, "north")
    t.titik("meja_tulis", c, F + 1, 4, "north")
    t.kotak("panggung", c - 8, F + 1, c - 8, c + 8, F + 1, c + 8)
    t.simpan()
    return t.id


def situs_mata_air_lumut():
    """Mata Air Roh variant 'Mata Air Lumut' (21 x 14 x 21): a spring that pours from a mossy boulder into a round pool
    rimmed with jade-green stone; a weathered stone gate (two posts and a lintel) on the path, a lean-to shrine of
    spruce with an incense table, an old willow-like dark oak hung with vines over the water, ferns and mossy rocks.
    Same anchor contract (gua/mata_air at the pool centre, r 3)."""
    t = G("situs", "mata_air_lumut", 21, 14, 21, palet="gua", batu="batu")
    F = 3
    c = 10
    for x in range(21):
        for z in range(21):
            d = math.hypot(x - c, z - c)
            if d <= 10:
                for y in range(0, F):
                    t.put(x, y, z, "minecraft:stone" if y < F - 1 else "minecraft:dirt")
                t.put(x, F, z, "minecraft:podzol" if d > 7.5 else "minecraft:moss_block" if d > 4.6 else "minecraft:mossy_cobblestone")
                for y in range(F + 1, 14):
                    t.set_air(x, y, z)
    pc = (c, c + 1)
    for x in range(21):
        for z in range(21):
            d = math.hypot(x - pc[0], z - pc[1])
            if d < 3.3:
                t.put(x, F - 1, z, "minecraft:prismarine" if d < 1.5 else "minecraft:clay")
                t.put(x, F, z, "minecraft:water", {"level": "0"})
            elif d < 4.4:
                t.put(x, F, z, t.rng.choice(["minecraft:dark_prismarine", "minecraft:prismarine_bricks",
                                             "minecraft:mossy_stone_bricks"]))
    t.put(pc[0], F - 2, pc[1], "minecraft:sea_lantern")
    # the boulder on the north rim with the spring pouring from it (a sealed source and a falling column)
    for x in range(c - 2, c + 3):
        for z in range(c - 6, c - 3):
            for y in range(F + 1, F + 5):
                if math.hypot(x - c, (z - (c - 4.5)) * 1.3) + (y - F - 1) * 0.5 < 3.0:
                    t.put(x, y, z, "minecraft:mossy_cobblestone" if t.rng.random() < 0.5 else "minecraft:moss_block")
    t.put(c, F + 3, c - 3, "minecraft:water", {"level": "0"})
    for (dx, dz, dy) in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1)):
        if t.is_air(c + dx, F + 3 + dy, c - 3 + dz) or t.get(c + dx, F + 3 + dy, c - 3 + dz) is None:
            t.put(c + dx, F + 3 + dy, c - 3 + dz, "minecraft:mossy_cobblestone")
    t.put(c, F + 3, c - 2, "minecraft:mossy_cobblestone")
    for y in (F + 1, F + 2):
        t.put(c, y, c - 3, "minecraft:water", {"level": "8"})
    t.put(c, F, c - 3, "minecraft:water", {"level": "0"})
    t.put(c, F - 1, c - 3, "minecraft:clay")
    # the stone gate on the path from the south
    for x in (c - 2, c + 2):
        for y in range(F + 1, F + 5):
            t.put(x, y, 17, "minecraft:mossy_stone_brick_wall", {"east": "none", "north": "none", "south": "none",
                                                                 "west": "none", "up": "true", "waterlogged": "false"})
    for x in range(c - 3, c + 4):
        t.put(x, F + 5, 17, "minecraft:mossy_stone_bricks" if abs(x - c) < 3 else "minecraft:mossy_stone_brick_slab",
              None if abs(x - c) < 3 else {"type": "bottom", "waterlogged": "false"})
    t.put(c, F + 6, 17, "minecraft:mossy_stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    for z in (19, 18, 16, 15):
        t.put(c, F, z, "minecraft:smooth_stone" if z % 2 else "minecraft:cobblestone")
    # the lean-to shrine on the west
    for z in range(8, 12):
        for x in (2, 3):
            t.put(x, F, z, "minecraft:spruce_planks")
    for z in (8, 11):
        for y in range(F + 1, F + 4):
            t.log(4, y, z, "minecraft:stripped_spruce_log", "y")
    for z in range(7, 13):
        t.stairs(4, F + 4, z, "minecraft:spruce_stairs", "east")
        t.stairs(3, F + 4, z, "minecraft:spruce_stairs", "east", half="top")
        t.put(2, F + 4, z, "minecraft:spruce_planks")
    t.put(2, F + 1, 9, "minecraft:spruce_trapdoor", {"facing": "east", "half": "top", "open": "false", "powered": "false",
                                                      "waterlogged": "false"})
    t.put(2, F + 1, 10, "minecraft:spruce_trapdoor", {"facing": "east", "half": "top", "open": "false",
                                                       "powered": "false", "waterlogged": "false"})
    t.put(2, F + 2, 9, "minecraft:decorated_pot", {"facing": "east", "cracked": "true", "waterlogged": "false"})
    t.lilin(2, F + 2, 10, 3, "green")
    t.lentera(3, F + 3, 9, True)
    # the old dark oak leaning over the water (east), hung with vines
    for y in range(F + 1, F + 6):
        t.log(16, y, 9, "minecraft:dark_oak_log", "y")
    for (x, y, z) in ((15, F + 6, 9), (14, F + 7, 9), (13, F + 7, 10)):
        t.log(x, y, z, "minecraft:dark_oak_log", "x")
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in (0, 1):
                if abs(dx) + abs(dz) + dy * 2 < 5:
                    daun(t, 14 + dx, F + 8 + dy, 10 + dz, "minecraft:dark_oak_leaves")
    for (x, z) in ((12, 9), (13, 12), (11, 11), (15, 12), (12, 13)):
        for i in range(3):
            y = F + 7 - i
            if t.is_air(x, y, z) and (i == 0 and t.get(x, y + 1, z) == "minecraft:dark_oak_leaves" or i > 0 and
                                      t.get(x, y + 1, z) == "minecraft:vine"):
                t.put(x, y, z, "minecraft:vine", {"north": "false", "south": "false", "east": "false", "west": "false",
                                                  "up": "true"})
    for (x, z) in ((5, 15), (15, 16), (6, 4), (17, 13), (4, 13)):
        if math.hypot(x - c, z - c) <= 9 and t.is_air(x, F + 1, z):
            t.put(x, F + 1, z, t.rng.choice(["minecraft:fern", "minecraft:fern",
                                             "minecraft:azalea", "minecraft:mossy_cobblestone"]))
    t.jangkar(c, F + 1, c + 6, "gua/situs", situs="situs/mata_air_roh", tier=1, protect=0)
    t.jangkar(pc[0], F + 1, pc[1], "gua/mata_air", yaw=FACE_YAW["south"], protect=8, r=3)
    t.titik("meditasi", c - 3, F + 1, c + 5, "north")
    t.titik("dupa", 3, F + 1, 10, "west")
    t.simpan()
    return t.id


# ================================================================================================== worldgen JSON
def tag_bioma(nama, biomes):
    path = os.path.join(structgen.DATA, "tags", "worldgen", "biome", "has_structure", "gua", nama + ".json")
    structgen.write_json(path, {"replace": False, "values": ["minecraft:" + b for b in biomes]})
    return "#%s:has_structure/gua/%s" % (NS, nama)


def jigsaw(start_pool, biomes, size, max_distance, start_height, heightmap="WORLD_SURFACE_WG", adaptasi="none",
           step="underground_structures", spawn=None):
    s = {"type": "minecraft:jigsaw", "biomes": biomes, "max_distance_from_center": max_distance, "size": size,
         "spawn_overrides": spawn if spawn is not None else {}, "start_height": start_height, "start_pool": start_pool,
         "step": step, "terrain_adaptation": adaptasi, "use_expansion_hack": False}
    if heightmap:
        s["project_start_to_heightmap"] = heightmap
    return s


def tulis_pool(nama, ids, proc=PROC, bobot=None):
    bobot = bobot or [1] * len(ids)
    tl.tulis_pool(nama, [(tl.elemen(i, proc), w) for i, w in zip(ids, bobot)])


def tulis_processor():
    rules = []
    for src, dst, p in (("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", 0.10),
                        ("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.05),
                        ("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.15),
                        ("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.04),
                        ("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.04),
                        ("minecraft:tuff_bricks", "minecraft:mossy_cobblestone", 0.02)):
        rules.append({"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": src, "probability": p},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": dst}})
    tl.tulis_processor("gua/lapuk", {"processors": [{"processor_type": "minecraft:rule", "rules": rules},
                                                    {"processor_type": "minecraft:protected_blocks",
                                                     "value": "#kultivator:sect_protected"}]})
    tl.tulis_processor("gua/utuh", tl.processor_utuh())


# ring frequencies (38.C.9): cincin = [(up to chunk, frequency)], rings R1 <= 62, R2 <= 187, R3 <= 500, R4 beyond
SET = {
    #  name               structures                                   spacing sep  min  rings                                    jarak
    "gua_pertapa":      ([("gua/gua_pertapa", 1)],                    32, 10, 10, [(62, 0.08), (187, 0.16), (500, 0.16), (9999, 0.16)], 4),
    "makam_warisan_1":  ([("gua/makam_warisan_1", 1)],                48, 16, 16, [(62, 0.03), (187, 0.06), (500, 0.03), (9999, 0.03)], 4),
    "makam_warisan_2":  ([("gua/makam_warisan_2", 1)],                48, 16, 32, [(62, 0.0), (187, 0.03), (500, 0.06), (9999, 0.03)], 4),
    "makam_warisan_3":  ([("gua/makam_warisan_3", 1)],                48, 16, 64, [(187, 0.0), (500, 0.06), (9999, 0.12)], 4),
    "sarang_binatang":  ([("gua/sarang_binatang", 1)],                24, 8, 8, [(62, 0.12), (187, 0.18), (500, 0.21), (9999, 0.24)], 4),
    "sarang_iblis":     ([("gua/sarang_iblis", 1)],                   40, 14, 20, [(62, 0.0), (187, 0.08), (500, 0.18), (9999, 0.25)], 6),
    "gua_urat_roh":     ([("gua/gua_urat_roh", 1)],                   24, 8, 12, [(62, 0.06), (187, 0.12), (500, 0.15), (9999, 0.15)], 0),
    "cermin_hati":      ([("gua/cermin_hati", 1)],                    40, 14, 24, [(62, 0.0), (187, 0.04), (500, 0.06), (9999, 0.08)], 4),
    "puncak_petir":     ([("situs/puncak_petir", 1)],                 32, 10, 12, [(62, 0.03), (187, 0.07), (500, 0.07), (9999, 0.07)], 4),
    "panggung_bintang": ([("situs/panggung_bintang", 1)],             48, 16, 24, [(62, 0.0), (187, 0.03), (500, 0.05), (9999, 0.06)], 4),
    "mata_air_roh":     ([("situs/mata_air_roh", 1)],                 24, 8, 8, [(62, 0.05), (187, 0.05), (500, 0.05), (9999, 0.05)], 4),
}
STRUKTUR_TAG = {
    "gua/gua_pertapa": ["gua/gua_pertapa"], "gua/makam_warisan": ["gua/makam_warisan_1", "gua/makam_warisan_2", "gua/makam_warisan_3"],
    "gua/sarang_binatang": ["gua/sarang_binatang"], "gua/sarang_iblis": ["gua/sarang_iblis"],
    "gua/gua_urat_roh": ["gua/gua_urat_roh"], "gua/cermin_hati": ["gua/cermin_hati"],
    "situs/puncak_petir": ["situs/puncak_petir"], "situs/panggung_bintang": ["situs/panggung_bintang"],
    "situs/mata_air_roh": ["situs/mata_air_roh"],
}


def worldgen(ids):
    tulis_processor()
    # pools
    # a slot is a list of (template, weight): the base piece first, the D-GU variants after it
    def slot(v):
        return v if isinstance(v, list) else [(v, 1)]

    def pools(prefix, group, proc_of=lambda k: PROC):
        for k, v in group.items():
            pairs = slot(v)
            tulis_pool(prefix + k, [i for i, _ in pairs], proc_of(k), bobot=[w for _, w in pairs])

    pools("gua/gua_pertapa/", ids["pertapa"])
    for tier in (1, 2, 3):
        pools("gua/makam_warisan_%d/" % tier, ids["makam"][tier],
              lambda k: PROC if k not in ("aula", "ruang_hati") else PROC_UTUH)
    tulis_pool("gua/sarang_binatang/mulai", [ids["binatang"]["pohon"], ids["binatang"]["liang"]], bobot=[3, 2])
    pools("gua/sarang_binatang/", {"sarang": ids["binatang"]["sarang"]})
    pools("gua/sarang_iblis/", ids["iblis"])
    pools("gua/gua_urat_roh/", ids["urat"])
    pools("gua/cermin_hati/", ids["cermin"])
    pools("situs/", ids["situs"], lambda k: PROC_UTUH)
    # structures
    kosong = tl.SPAWN_KOSONG
    struktur = {
        "gua/gua_pertapa": jigsaw("kultivator:gua/gua_pertapa/serambi", tag_bioma("gua_pertapa", BIOMES["gua_pertapa"]),
                                  3, 80, {"absolute": -8}, spawn=kosong),
        "gua/sarang_binatang": jigsaw("kultivator:gua/sarang_binatang/mulai",
                                      tag_bioma("sarang_binatang", BIOMES["sarang_binatang"]), 2, 64, {"absolute": -8}),
        "gua/sarang_iblis": jigsaw("kultivator:gua/sarang_iblis/gerbang", tag_bioma("sarang_iblis", BIOMES["sarang_iblis"]),
                                   6, 96, {"absolute": -8}),
        "gua/gua_urat_roh": jigsaw("kultivator:gua/gua_urat_roh/geode", "#minecraft:is_overworld", 1, 64,
                                   {"type": "minecraft:uniform", "min_inclusive": {"absolute": -24},
                                    "max_inclusive": {"absolute": 6}}, heightmap=None),
        "gua/cermin_hati": jigsaw("kultivator:gua/cermin_hati/mulut", tag_bioma("cermin_hati", BIOMES["cermin_hati"]), 1, 48,
                                  {"absolute": -8}),
        "situs/puncak_petir": jigsaw("kultivator:situs/puncak_petir", tag_bioma("puncak_petir", BIOMES["puncak_petir"]), 0, 32,
                                     {"absolute": -5}, adaptasi="beard_box", step="surface_structures"),
        "situs/panggung_bintang": jigsaw("kultivator:situs/panggung_bintang",
                                         tag_bioma("panggung_bintang", BIOMES["panggung_bintang"]), 0, 32, {"absolute": -4},
                                         adaptasi="beard_box", step="surface_structures"),
        "situs/mata_air_roh": jigsaw("kultivator:situs/mata_air_roh", tag_bioma("mata_air_roh", BIOMES["mata_air_roh"]), 0, 32,
                                     {"absolute": -4}, adaptasi="beard_box", step="surface_structures", spawn=kosong),
    }
    makam_biome = tag_bioma("makam_warisan", BIOMES["makam_warisan"])
    for tier in (1, 2, 3):
        struktur["gua/makam_warisan_%d" % tier] = jigsaw("kultivator:gua/makam_warisan_%d/stela" % tier, makam_biome, 10,
                                                        110, {"absolute": -6})
    for nama, obj in struktur.items():
        tl.tulis_wg("structure", nama, obj)
    # structure sets (pack dunia_gua)
    for nama, (structs, spacing, sep, mn, cincin, jarak) in SET.items():
        folder = "situs" if nama in ("puncak_petir", "panggung_bintang", "mata_air_roh") else "gua"
        obj = tl.set_cincin([("kultivator:" + s, w) for s, w in structs], spacing, sep, SALT[nama], min_chunks=mn,
                            cincin=cincin, jarak_tempat=jarak)
        tl.tulis_wg("structure_set", "%s/%s" % (folder, nama), obj, pack=PACK)
    # structure tags
    tags = os.path.join(structgen.DATA, "tags", "worldgen", "structure")
    for nama, members in STRUKTUR_TAG.items():
        structgen.write_json(os.path.join(tags, nama + ".json"), {"values": ["kultivator:" + m for m in members]})
    structgen.write_json(os.path.join(tags, "gua.json"),
                         {"values": ["#kultivator:" + n for n in STRUKTUR_TAG if n.startswith("gua/")]})
    structgen.write_json(os.path.join(tags, "situs.json"),
                         {"values": ["#kultivator:" + n for n in STRUKTUR_TAG if n.startswith("situs/")]})
    pack = os.path.join(structgen.REPO, "src", "main", "resources", "resourcepacks", PACK, "pack.mcmeta")
    structgen.write_json(pack, {"pack": {"description": {"translate": "kultivator.dunia.pack.gua"},
                                         "min_format": 121, "max_format": 121}})


# ================================================================================================== loot tables
def _item(name, w, lo=1, hi=1, umur=None):
    e = {"type": "minecraft:item", "name": name, "weight": w}
    mods = []
    if hi > 1 or lo != 1:
        mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if umur:
        mods.append({"type": "minecraft:set_components", "components": {"kultivator:herb_age": umur}})
    if mods:
        e["modifier"] = mods if len(mods) > 1 else mods[0]
    return e


def _tabel(nama, pools):
    path = os.path.join(structgen.DATA, "loot_table", "chests", "tempat", "gua", nama + ".json")
    structgen.write_json(path, {"type": "minecraft:chest", "pools": pools,
                                "random_sequence": "kultivator:chests/tempat/gua/" + nama})


def _pool(lo, hi, entries):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": entries}


def loot_tables():
    _tabel("pertapa_bilik", [_pool(2, 4, [
        _item("kultivator:spirit_grass", 20, 1, 3, 60), _item("kultivator:purple_flame_flower", 8, 1, 1, 80),
        _item("kultivator:qi_gathering_pill", 15, 1, 2), _item("kultivator:teh_embun_pagi", 15, 1, 2),
        _item("kultivator:jade_slip", 6), _item("kultivator:spirit_stone_low", 20, 2, 6), _item("minecraft:paper", 10, 1, 4),
        _item("kultivator:heart_calming_pill", 6)])])
    for tier, (batu, herba, umur, pil) in {1: ("spirit_stone_mid", "purple_flame_flower", 200, "foundation_pill"),
                                           2: ("spirit_stone_high", "ninecurl_ginseng", 600, "golden_core_pill"),
                                           3: ("spirit_stone_supreme", "ginseng_fruit", 2000, "soul_nurturing_pill")}.items():
        _tabel("makam_t%d" % tier, [_pool(2, 4, [
            _item("kultivator:" + batu, 30, 1, 3), _item("kultivator:" + herba, 18, 1, 2, umur),
            _item("kultivator:" + pil, 12), _item("kultivator:jade_slip", 10), _item("kultivator:lost_scripture_page", 8),
            _item("kultivator:tomb_essence", 8, 1, 2), _item("minecraft:gold_ingot", 14, 2, 5)])])
    _tabel("sarang_binatang", [_pool(1, 3, [
        _item("kultivator:beast_core_low", 30, 1, 2), _item("kultivator:beast_core_mid", 12), _item("minecraft:bone", 30, 2, 6),
        _item("minecraft:leather", 20, 1, 3), _item("kultivator:spirit_beast_pill", 6)])])
    _tabel("sarang_iblis_jaga", [_pool(1, 3, [
        _item("kultivator:spirit_stone_low", 30, 2, 8), _item("minecraft:bone", 20, 1, 4), _item("kultivator:token_darah_api", 4),
        _item("minecraft:rotten_flesh", 20, 1, 3), _item("kultivator:demonic_loot_pouch", 6)])])
    _tabel("sarang_iblis_racik", [_pool(2, 4, [
        _item("kultivator:toxin_purging_pill", 14), _item("kultivator:healing_pill", 18, 1, 2),
        _item("kultivator:illusory_spirit_herb", 12, 1, 2, 150), _item("minecraft:fermented_spider_eye", 18, 1, 3),
        _item("minecraft:glass_bottle", 20, 1, 4), _item("kultivator:marrow_cleansing_pill", 6),
        _item("kultivator:demonic_loot_pouch", 6)])])
    _tabel("sarang_iblis_kamar", [_pool(2, 5, [
        _item("kultivator:spirit_stone_mid", 30, 1, 4), _item("kultivator:demonic_banner", 6), _item("kultivator:jade_slip", 10),
        _item("kultivator:demonic_loot_pouch", 14), _item("minecraft:gold_ingot", 20, 2, 6),
        _item("kultivator:soul_seal_jade", 4)])])
    _tabel("urat_roh_penambang", [_pool(1, 3, [
        _item("minecraft:amethyst_shard", 30, 2, 6), _item("kultivator:spirit_stone_low", 25, 2, 6),
        _item("minecraft:iron_pickaxe", 6), _item("minecraft:torch", 20, 2, 8), _item("minecraft:bread", 15, 1, 3)])])
    # the extra reward roll of a Star Calamity begun on Panggung Bintang (StarCalamity hook, PanggungBintang.java)
    path = os.path.join(structgen.DATA, "loot_table", "gua", "hadiah_bintang.json")
    structgen.write_json(path, {"type": "minecraft:chest", "random_sequence": "kultivator:gua/hadiah_bintang", "pools": [
        _pool(1, 1, [_item("kultivator:heaven_seal_shard", 30, 1, 2), _item("kultivator:batu_meteor_roh", 30, 2, 4),
                     _item("kultivator:spirit_stone_supreme", 12), _item("kultivator:immortal_essence_pearl", 6),
                     _item("kultivator:kristal_gerhana", 14, 1, 2), _item("kultivator:inti_meteor_agung", 8)])]})


# ================================================================================================== main
def main():
    makam = {}
    for tier in (1, 2, 3):
        m = makam_semua(tier)
        m["lorong_a"] = [(m["lorong_a"], 1), (makam_lorong_relung(tier, "lorong_a", "ruang_kekuatan"), 1)]
        m["lorong_b"] = [(m["lorong_b"], 1), (makam_lorong_jembatan(tier, "lorong_b", "ruang_pemahaman"), 1)]
        m["lorong_c"] = [(m["lorong_c"], 1), (makam_lorong_persembahan(tier, "lorong_c", "aula"), 2)]
        makam[tier] = m
    ids = {
        "pertapa": {"serambi": pertapa_serambi(), "lorong": pertapa_lorong(),
                    "ruang": [(pertapa_ruang(), 1), (pertapa_ruang_air(), 1)],
                    "bilik": [(pertapa_bilik(), 1), (pertapa_bilik_kitab(), 1)],
                    "kebun": [(pertapa_kebun(), 1), (pertapa_kebun_jamur(), 1)]},
        "makam": makam,
        "binatang": {"pohon": sarang_pohon(), "liang": sarang_liang(),
                     "sarang": [(sarang_dalam(), 1), (sarang_rusuk(), 1)]},
        "iblis": {"gerbang": iblis_gerbang(), "pos_jaga": iblis_pos_jaga(), "turun": iblis_turun(), "aula": iblis_aula(),
                  "sel": [(iblis_sel(), 1), (iblis_blok_sel(), 1)], "racik": [(iblis_racik(), 1), (iblis_kebun_racun(), 1)],
                  "lorong": iblis_lorong(), "kamar": iblis_kamar()},
        "urat": {"geode": urat_geode(), "terowongan": [(urat_terowongan(), 1), (urat_kemah(), 1)]},
        "cermin": {"mulut": cermin_mulut(), "kolam": [(cermin_kolam(), 1), (cermin_kolam_bakau(), 1)]},
        "situs": {"puncak_petir": [(situs_puncak_petir(), 1), (situs_puncak_petir_awan(), 1)],
                  "panggung_bintang": [(situs_panggung_bintang(), 1), (situs_balai_bintang(), 1)],
                  "mata_air_roh": [(situs_mata_air_roh(), 1), (situs_mata_air_lumut(), 1)]},
    }
    worldgen(ids)
    loot_tables()
    tl.cek_semua()
    n = sum(1 for _ in structgen._SAVED)
    print("gen_tempat_gua: %d templates, %d structures, %d sets" % (n, 11, len(SET)))


# ================================================================================================== giok compass (D-GU)
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", NS)


def _baca_png(path):
    """Reads an 8-bit RGBA PNG written by tools/png.py (filter 0 rows only) into a png.Image."""
    import struct
    import zlib
    from png import Image
    raw = open(path, "rb").read()
    pos, w, h, data = 8, 0, 0, b""
    while pos < len(raw):
        n, kind = struct.unpack(">I4s", raw[pos:pos + 8])
        body = raw[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
            assert body[8] == 8 and body[9] == 6, "giok base must be 8-bit RGBA"
        elif kind == b"IDAT":
            data += body
        pos += 12 + n
    rows = zlib.decompress(data)
    img = Image(w, h)
    for y in range(h):
        row = rows[y * (w * 4 + 1):(y + 1) * (w * 4 + 1)]
        assert row[0] == 0, "giok base must use filter 0"
        for x in range(w):
            img.set(x, y, tuple(row[1 + x * 4:5 + x * 4]))
    return img


def giok_kompas():
    """Giok Warisan as a compass (DESIGN 38.C.4: the jade points to the tomb it was attuned to): 32 frames of the
    jade disc (base texture giok_warisan.png, not redrawn) with a gold spirit spark circling its face clockwise from
    the top (frame 16 = straight ahead, like the vanilla compass) and a faint jade tail behind it. Without a
    lodestone tracker the plain disc shows; with one the vanilla compass property turns the spark toward the tomb."""
    import json
    item, models, items = (os.path.join(ASSETS, "textures", "item"), os.path.join(ASSETS, "models", "item"),
                           os.path.join(ASSETS, "items"))
    base = _baca_png(os.path.join(item, "giok_warisan.png"))
    cx, cy, r = 7.5, 6.5, 4.1               # disc centre and the glyph ring between hole (2.0) and rim (6.2)
    for index in range(32):
        img = _baca_png(os.path.join(item, "giok_warisan.png"))
        a = ((index - 16) % 32) / 32.0 * 2.0 * math.pi
        # pale jade drowns pale gold, so the tail is deep jade and the spark a saturated amber with a lit rim pixel
        for t, rgba in ((-0.62, (40, 140, 96, 150)), (-0.34, (30, 150, 100, 230))):
            img.set(int(round(cx + math.sin(a + t) * r)), int(round(cy - math.cos(a + t) * r)), rgba)
        for rr, rgba in ((r - 0.9, (200, 110, 10, 255)), (r, (255, 170, 30, 255)), (r + 1.2, (255, 236, 140, 255))):
            x, y = int(round(cx + math.sin(a) * rr)), int(round(cy - math.cos(a) * rr))
            if base.get(x, y)[3] > 0 and math.hypot(x - cx, y - cy) > 2.2:
                img.set(x, y, rgba)
        name = "giok_warisan_%02d" % index
        img.save(os.path.join(item, name + ".png"))
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "kultivator:item/" + name}}, f, indent=2)
            f.write("\n")
    entries = [{"model": {"type": "minecraft:model", "model": "kultivator:item/giok_warisan_%02d" % ((16 + i) % 32)},
                "threshold": 0.0 if i == 0 else i - 0.5} for i in range(33)]
    with open(os.path.join(items, "giok_warisan.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"model": {
            "type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:lodestone_tracker",
            "on_false": {"type": "minecraft:model", "model": "kultivator:item/giok_warisan"},
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone",
                        "scale": 32.0, "entries": entries}}}, f, indent=2)
        f.write("\n")
    print("gen_tempat_gua: giok_warisan compass, 32 frames")


if __name__ == "__main__":
    if "--giok" in sys.argv:
        giok_kompas()
    else:
        main()
        giok_kompas()
