"""Sekte Langit module 37G (DESIGN 37.7.6): the floating palaces of the NPC sky sects, and the trophy item's assets.

Writes (only module 37G's own files, DESIGN 37.9.5):
  data/kultivator/structure/sekte_langit/inti_{giok,awan}.nbt            the palace island (48 x 52 x 48), two styles
  data/kultivator/structure/sekte_langit/satelit_{kebun,alkimia,arena}.nbt  satellite isles (24 x 28 x 24)
  data/kultivator/structure/sekte_langit/jembatan.nbt                     the jade bridge connector (5 x 12 x 20)
  data/kultivator/structure/sekte_langit/gerbang.nbt                      the three-roof gate on its terrace (13 x 26 x 12)
  data/kultivator/worldgen/template_pool/sekte_langit/{inti,jembatan,satelit,gerbang}.json
  data/kultivator/worldgen/structure/sekte_langit/istana_langit.json      jigsaw, absolute start height 204-228
  data/kultivator/tags/worldgen/biome/has_structure/sekte_langit.json     every overworld biome
  data/kultivator/tags/worldgen/structure/sekte_langit.json               for /locate
  resourcepacks/sekte_langit/pack.mcmeta                                  the module's built-in pack (registered by
  resourcepacks/sekte_langit/data/kultivator/worldgen/structure_set/      LangitNpcPack only while Sekte Langit and
      sekte_langit/istana_langit_{0,1}.json                               sekteLangit.strukturNpc are on)
  assets/kultivator/textures/item/cangkang_kura_langit.png               the trophy's 32 x 32 texture
  assets/kultivator/models/item/cangkang_kura_langit.json + items/...     its cuboid model (shell, rim, scutes, gold ridge)

The island is the same shape as the build module's PulauAwan (DESIGN 37.4.2) rewritten here: a noisy rim
rho_max(theta) = r (1 + 0.12 n1(theta) + 0.06 n2(3 theta)) clamped to 0.82r..1.15r, terraces at 0.55 / 0.72 / 0.86 of the
rim, an inverted cone d(rho) = D (1 - (rho/rho_max)^1.6)(0.85 + 0.30 n3) + 2 of which only a 3-block skin is placed (the
inside is structure_void, so nothing is placed or carried there), earth, stone, tuff and deepslate by relative depth,
hanging roots, glow lichen, cave vines and amethyst crystals below, a stone turtle under the front (Liezi, "Tang Wen").
No water ever leaves an island (only an enclosed pond); no gravity blocks, no budding amethyst, no fire next to wood.

Jigsaw: the palace (start, absolute height, terrain_adaptation none) has connectors east and west (pool jembatan; a
bridge's far end asks the pool satelit) and south (pool gerbang). Only world generation places these templates, in new
chunks (DESIGN 37.0 rule 2); no runtime code calls setBlock for them. Anchors (DuniaAnchor markers, kind
"sektelangit/<what>", read by LangitNpcAnchors): gerbang, murid (slot), ketua, dermaga.

Run: python tools/gen_sekte_langit.py   (prints validate_all(); every id checked against the 26.3 block list)
Sumber: Liezi "Tang Wen" (the five floating mountains of Guixu, the fifteen turtles); Huainanzi (Kunlun's hanging garden).
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from png import Image  # noqa: E402
from structgen import DATA, PALETTES, REPO, Piece, out_path, validate_all, write_json  # noqa: E402

MODULE = "sekte_langit"
NS = "kultivator:sekte_langit/"
PACK = os.path.join(REPO, "src", "main", "resources", "resourcepacks", "sekte_langit")
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "kultivator")

JADE = "minecraft:waxed_oxidized_cut_copper"
JADE_SLAB = "minecraft:waxed_oxidized_cut_copper_slab"
JADE_STAIRS = "minecraft:waxed_oxidized_cut_copper_stairs"

GIOK = dict(PALETTES["abadi"])
GIOK.update({
    "floor": "minecraft:smooth_quartz", "floor_alt": "minecraft:quartz_bricks", "wall": "minecraft:white_concrete",
    "wall_alt": "minecraft:calcite", "pillar": "minecraft:stripped_mangrove_log", "trim": "minecraft:quartz_pillar",
    "roof": JADE_STAIRS, "roof_slab": JADE_SLAB, "roof_full": JADE, "accent": "minecraft:gold_block", "jade": JADE,
    "glow": "minecraft:lantern", "path": "minecraft:quartz_bricks", "path_alt": "minecraft:smooth_quartz",
    "fence": "minecraft:pale_oak_fence", "window": "minecraft:spruce_trapdoor", "carpet": "minecraft:red_carpet",
})
AWAN = dict(GIOK)
AWAN.update({
    "wall": "minecraft:quartz_bricks", "wall_alt": "minecraft:chiseled_quartz_block", "pillar": "minecraft:quartz_pillar",
    "trim": "minecraft:gold_block", "roof": "minecraft:dark_prismarine_stairs", "roof_slab": "minecraft:dark_prismarine_slab",
    "roof_full": "minecraft:dark_prismarine", "glow": "minecraft:sea_lantern", "carpet": "minecraft:cyan_carpet",
    "window": "minecraft:light_blue_stained_glass_pane",
})

LEAVES = {"persistent": "true", "distance": "1", "waterlogged": "false"}
TEMPLATES = []
JSONS = []
PACK_JSONS = []


# ------------------------------------------------------------------------------------------------- the island
def periodic_noise(rng, points):
    vals = [rng.random() * 2.0 - 1.0 for _ in range(points)]

    def f(t):
        u = (t / (2.0 * math.pi)) % 1.0 * points
        i = int(u)
        a = vals[i % points]
        b = vals[(i + 1) % points]
        s = u - i
        s = s * s * (3.0 - 2.0 * s)
        return a + (b - a) * s
    return f


def value_noise_2d(rng, cell=5):
    table = {}

    def at(ix, iz):
        key = (ix, iz)
        if key not in table:
            table[key] = rng.random()
        return table[key]

    def f(x, z):
        gx, gz = x / cell, z / cell
        ix, iz = math.floor(gx), math.floor(gz)
        sx, sz = gx - ix, gz - iz
        sx, sz = sx * sx * (3 - 2 * sx), sz * sz * (3 - 2 * sz)
        a = at(ix, iz) + (at(ix + 1, iz) - at(ix, iz)) * sx
        b = at(ix, iz + 1) + (at(ix + 1, iz + 1) - at(ix, iz + 1)) * sx
        return a + (b - a) * sz
    return f


class Island:
    """One floating island in a piece: the shape of PulauAwan (DESIGN 37.4.2), its surface heights and its rim."""

    def __init__(self, p, cx, cz, r, top, seed, lapis=2, depth=None, notch=()):
        self.p, self.cx, self.cz, self.r, self.top = p, cx, cz, r, top
        self.rng = random.Random(seed)
        self.n1 = periodic_noise(self.rng, 16)
        self.n2 = periodic_noise(self.rng, 8)
        self.n3 = value_noise_2d(self.rng, 4)
        self.lapis = lapis
        self.depth = depth if depth is not None else round(1.1 * r) + 4
        self.tip = (cx + self.rng.uniform(-0.15, 0.15) * r, cz + self.rng.uniform(-0.15, 0.15) * r)
        self.notch = notch  # angles (radians) where the rim is flattened for paths
        self.surface = {}
        self.cells = []

    def rho_max(self, theta):
        m = self.r * (1.0 + 0.12 * self.n1(theta) + 0.06 * self.n2(3.0 * theta))
        for a in self.notch:
            d = abs((theta - a + math.pi) % (2 * math.pi) - math.pi)
            if d < 0.25:
                m = max(m, self.r * 0.98)
        lim = min(self.cx, self.cz, self.p.size[0] - 1 - self.cx, self.p.size[2] - 1 - self.cz) - 0.5
        return max(0.82 * self.r, min(1.15 * self.r, m, lim))

    def level(self, x, z):
        """Standing-surface offset of a column (0 plaza, -2 per terrace)."""
        dx, dz = x - self.cx, z - self.cz
        rho = math.hypot(dx, dz)
        t = rho / self.rho_max(math.atan2(dz, dx))
        if t < 0.55:
            return 0
        if t < 0.72 or self.lapis < 2:
            return -1 if self.lapis >= 2 else 0
        if t < 0.86 or self.lapis < 3:
            return -2
        return -3

    def build(self, style="taman"):
        p, rng = self.p, self.rng
        strata = ["minecraft:stone", "minecraft:andesite", "minecraft:tuff", "minecraft:stone", "minecraft:calcite"]
        band = rng.randrange(5)
        R = int(self.r * 1.2) + 2
        for x in range(self.cx - R, self.cx + R + 1):
            for z in range(self.cz - R, self.cz + R + 1):
                if not p.inside(x, 0, z):
                    continue
                dx, dz = x - self.cx, z - self.cz
                rho = math.hypot(dx, dz)
                theta = math.atan2(dz, dx)
                rm = self.rho_max(theta)
                if rho > rm:
                    continue
                ys = self.top + self.level(x, z)
                self.surface[(x, z)] = ys
                self.cells.append((x, z))
                # the inverted cone below, its skin only (the tip leans a little)
                tdx, tdz = x - self.tip[0], z - self.tip[1]
                trho = min(rm, math.hypot(tdx, tdz))
                d = self.depth * (1.0 - (trho / rm) ** 1.6) * (0.85 + 0.30 * self.n3(x, z)) + 2.0
                bottom = max(0, int(round(self.top - d)))
                for y in range(bottom, ys + 1):
                    near_top = y >= ys - 2
                    skin = y <= bottom + 3 or rho >= rm - 3 or y >= self.top - 4
                    if not (skin or near_top):
                        continue
                    if y == ys:
                        roll = rng.random()
                        if style == "batu":
                            block = "minecraft:stone" if roll < 0.5 else "minecraft:andesite" if roll < 0.8 else "minecraft:moss_block"
                        else:
                            block = "minecraft:moss_block" if self.n3(x * 2.1, z * 2.1) > 0.72 else "minecraft:grass_block"
                        p.put(x, y, z, block, {"snowy": "false"} if block == "minecraft:grass_block" else None)
                        continue
                    if y >= ys - 2:
                        if rho >= rm - 1.6:
                            p.put(x, y, z, strata[(y + band) % 5])
                        else:
                            p.put(x, y, z, "minecraft:dirt")
                        continue
                    q = (self.top - y) / max(1.0, d)
                    if q < 0.15:
                        block = "minecraft:rooted_dirt" if rng.random() < 0.3 else "minecraft:dirt"
                    elif q < 0.55:
                        block = "minecraft:stone" if self.n3(x + y, z) > 0.45 else "minecraft:andesite"
                    elif q < 0.85:
                        block = "minecraft:calcite" if rng.random() < 0.12 else "minecraft:tuff"
                    else:
                        block = "minecraft:deepslate"
                    p.put(x, y, z, block)
                # hanging growth under the skin (never on the open sides above)
                if bottom >= 1:
                    roll = rng.random()
                    if roll < 0.16:
                        p.put(x, bottom - 1, z, "minecraft:hanging_roots", {"waterlogged": "false"})
                    elif roll < 0.25:
                        p.put(x, bottom - 1, z, "minecraft:glow_lichen", {"up": "true", "down": "false", "north": "false", "south": "false",
                                                                          "east": "false", "west": "false", "waterlogged": "false"})
                    elif roll < 0.33 and rho >= 0.75 * rm:
                        length = 1 + rng.randrange(4)
                        for k in range(length):
                            yy = bottom - 1 - k
                            if yy < 0:
                                break
                            last = k == length - 1 or yy == 0
                            p.put(x, yy, z, "minecraft:cave_vines" if last else "minecraft:cave_vines_plant",
                                  {"berries": "true" if rng.random() < 0.34 else "false"} if not last else
                                  {"berries": "true" if rng.random() < 0.34 else "false", "age": "25"})
        return self

    def roots(self, count):
        """Mangrove root strands under the cone's tip, ending in rooted dirt and hanging roots."""
        p, rng = self.p, self.rng
        tx, tz = int(round(self.tip[0])), int(round(self.tip[1]))
        for i in range(count):
            a = rng.random() * math.pi * 2
            rr = rng.random() * self.r * 0.3
            x, z = int(round(tx + math.cos(a) * rr)), int(round(tz + math.sin(a) * rr))
            y = self.bottom_at(x, z)
            if y is None:
                continue
            length = 3 + rng.randrange(6)
            for k in range(1, length + 1):
                if y - k < 0:
                    break
                p.put(x, y - k, z, "minecraft:mangrove_roots", {"waterlogged": "false"})
                if rng.random() < 0.33:
                    x += rng.choice((-1, 0, 1))
                    z += rng.choice((-1, 0, 1))
            end = y - length - 1
            if end >= 1:
                p.put(x, end, z, "minecraft:rooted_dirt")
                p.put(x, end - 1, z, "minecraft:hanging_roots", {"waterlogged": "false"})

    def bottom_at(self, x, z):
        for y in range(0, self.top):
            if self.p.get(x, y, z) not in (None, "minecraft:air", "minecraft:hanging_roots", "minecraft:glow_lichen", "minecraft:cave_vines",
                                           "minecraft:cave_vines_plant"):
                return y
        return None

    def crystals(self, count):
        """Amethyst clusters hanging in the cone's middle band (inspired by 37.4.2 step 7, smaller)."""
        p, rng = self.p, self.rng
        for i in range(count):
            a = rng.random() * math.pi * 2
            rr = self.r * (0.35 + rng.random() * 0.35)
            x, z = int(round(self.cx + math.cos(a) * rr)), int(round(self.cz + math.sin(a) * rr))
            y = self.bottom_at(x, z)
            if y is None or y < 6:
                continue
            length = 3 + rng.randrange(4)
            for k in range(1, length + 1):
                p.put(x, y - k, z, "minecraft:amethyst_block")
                if k <= 2:
                    for ox, oz, face in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
                        if rng.random() < 0.45 and p.get(x + ox, y - k, z + oz) is None:
                            p.put(x + ox, y - k, z + oz, "minecraft:large_amethyst_bud", {"facing": face, "waterlogged": "false"})
            p.put(x, y - length - 1, z, "minecraft:amethyst_cluster", {"facing": "down", "waterlogged": "false"})
            if i == 0:
                p.put(x, y - length - 2, z, "minecraft:end_rod", {"facing": "down"})

    def rim(self, role, skip=()):
        """A low parapet (bottom slabs) on the outermost ring, gaps at the path notches."""
        for (x, z) in self.cells:
            dx, dz = x - self.cx, z - self.cz
            rho = math.hypot(dx, dz)
            theta = math.atan2(dz, dx)
            if rho < self.rho_max(theta) - 1.0:
                continue
            if any(abs((theta - a + math.pi) % (2 * math.pi) - math.pi) < 0.16 for a in skip):
                continue
            y = self.surface[(x, z)] + 1
            if self.p.get(x, y, z) is None:
                self.p.slab(x, y, z, role)

    def turtle(self):
        """A stone turtle's head under the front rim (+z), looking out (Liezi: the turtles that carry the islands)."""
        p = self.p
        x0 = self.cx
        zf = self.cz + int(self.r * 0.8)
        y = self.top - int(self.depth * 0.45)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in range(0, 4):
                    if p.inside(x0 + dx, y + dy, zf + dz):
                        p.put(x0 + dx, y + dy, zf + dz, "minecraft:mossy_cobblestone" if (dx + dy + dz) % 3 else "minecraft:mossy_stone_bricks")
        for dx in (-1, 1):
            p.put(x0 + dx, y + 1, zf + 3, "minecraft:ochre_froglight", {"axis": "y"})
        p.put(x0, y - 1, zf + 4, "minecraft:mossy_cobblestone")


# ------------------------------------------------------------------------------------------------- small builders
def tree_plum(p, x, y, z, rng):
    h = 3 + rng.randrange(2)
    for k in range(h):
        p.put(x, y + k, z, "minecraft:cherry_log", {"axis": "y"})
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(h - 1, h + 2):
                if abs(dx) + abs(dz) + max(0, dy - h) * 2 <= 3 and rng.random() < 0.85 and p.get(x + dx, y + dy, z + dz) is None:
                    p.put(x + dx, y + dy, z + dz, "minecraft:cherry_leaves", LEAVES)
    for k in range(3):
        px, pz = x + rng.randrange(-2, 3), z + rng.randrange(-2, 3)
        if p.get(px, y, pz) is None:
            p.put(px, y, pz, "minecraft:pink_petals", {"facing": "north", "flower_amount": str(1 + rng.randrange(4))})


def bamboo(p, x, y, z, rng, n=5):
    for i in range(n):
        bx, bz = x + rng.randrange(-1, 2), z + rng.randrange(-1, 2)
        if p.get(bx, y, bz) is not None:
            continue
        h = 3 + rng.randrange(4)
        for k in range(h):
            p.put(bx, y + k, bz, "minecraft:bamboo", {"age": "1", "leaves": "large" if k >= h - 2 else "small" if k == h - 3 else "none", "stage": "0"})


def pavilion(p, x0, y, z0, x1, z1, pal_roof=True):
    """An open pavilion: four pillars, a floor, a hip roof with curled eaves."""
    p.fill(x0, y - 1, z0, x1, y - 1, z1, "floor_alt")
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        p.pillar(x, y, y + 2, z, "pillar", base=None, cap="trim")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if x not in (x0, x1):
                p.put(x, y + 2, z, "trim", p._axis_y("trim"))
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            p.put(x, y + 2, z, "trim", p._axis_y("trim"))
    return p.roof_hip(x0, y + 3, z0, x1, z1, overhang=1, eave_curl=True)


def lantern_post(p, x, y, z):
    p.standing_lantern(x, y, z, post="fence", role="glow", height=2)


def connector(p, x, y, z, facing, name, target, pool, final="minecraft:smooth_quartz"):
    p.jigsaw_side(x, y, z, facing, name=name, target=target, pool=pool, final_state=final)


# ------------------------------------------------------------------------------------------------- the palace
def build_inti(style, seed):
    pal = GIOK if style == "giok" else AWAN
    p = Piece(48, 52, 48, seed=seed, palette=pal)
    top = 30
    cx = cz = 24
    east, west, south = 0.0, math.pi, math.pi / 2
    isle = Island(p, cx, cz, 20, top, seed + 7, lapis=2, notch=(east, west, south)).build("taman")
    isle.roots(7)
    isle.crystals(4)
    isle.turtle()
    rng = p.rng
    # paths: plaza disc, axes to the east / west connectors and the south gate (3 wide, at plaza level)
    p.disc(cx, top, cz, 8, "path_alt", alt="path", alt_chance=0.15)
    p.ring(cx, top, cz, 8, "minecraft:chiseled_quartz_block")
    for x in range(0, 48):
        for dz in (-1, 0, 1):
            if (x, cz + dz) in isle.surface or x < 4 or x > 43:
                p.put(x, top, cz + dz, "path")
                for yy in range(top + 1, top + 4):
                    if p.get(x, yy, cz + dz) is not None and p.get(x, yy, cz + dz) != "minecraft:air":
                        p.put(x, yy, cz + dz, "minecraft:air")
    for z in range(cz, 48):
        for dx in (-1, 0, 1):
            p.put(cx + dx, top, z, "path")
    # piers under the paths where they leave the island (so a connector never floats on air)
    for x in list(range(0, 6)) + list(range(42, 48)):
        for dz in (-1, 0, 1):
            if p.get(x, top - 1, cz + dz) is None:
                p.put(x, top - 1, cz + dz, "minecraft:quartz_bricks")
    for z in range(42, 48):
        for dx in (-1, 0, 1):
            if p.get(cx + dx, top - 1, z) is None:
                p.put(cx + dx, top - 1, z, "minecraft:quartz_bricks")
    connector(p, 47, top, cz, "east", NS + "kait_inti", NS + "kait_jembatan", NS + "jembatan")
    connector(p, 0, top, cz, "west", NS + "kait_inti", NS + "kait_jembatan", NS + "jembatan")
    connector(p, cx, top, 47, "south", NS + "kait_inti_gerbang", NS + "kait_gerbang", NS + "gerbang")
    # the central stele of the sect
    p.put(cx, top + 1, cz, "minecraft:chiseled_quartz_block")
    p.put(cx, top + 2, cz, "minecraft:quartz_pillar", {"axis": "y"})
    p.put(cx, top + 3, cz, "minecraft:gilded_blackstone")
    p.put(cx, top + 4, cz, "accent")
    # the main hall in the north: plinth, red-pillared walls, a double-eaved hip roof
    hx0, hz0, hx1, hz1 = 15, 6, 33, 15
    p.fill(hx0 - 1, top + 1, hz0 - 1, hx1 + 1, top + 1, hz1 + 1, "floor_alt")
    for x in range(cx - 2, cx + 3):
        p.stairs(x, top + 1, hz1 + 2, "minecraft:quartz_stairs", "north")
    p.walls(hx0, top + 2, hz0, hx1, top + 6, hz1, role="wall", pillar="pillar", windows=4, window_role="window", door=("south", 3))
    p.fill(hx0 + 1, top + 1, hz0 + 1, hx1 - 1, top + 1, hz1 - 1, "floor")
    for z in range(hz0 + 2, hz1):
        p.put(cx, top + 2, z, "carpet")
    p.stairs(cx, top + 2, hz0 + 1, "minecraft:quartz_stairs", "north")
    p.put(cx - 1, top + 2, hz0 + 1, "minecraft:quartz_pillar", {"axis": "y"})
    p.put(cx + 1, top + 2, hz0 + 1, "minecraft:quartz_pillar", {"axis": "y"})
    for x in (hx0 + 1, hx1 - 1):
        for z in range(hz0 + 1, hz1, 2):
            p.put(x, top + 2, z, "minecraft:bookshelf")
    for x in (hx0 + 1, hx1 - 1):
        for z in range(hz0 + 1, hz1, 4):
            p.put(x, top + 3, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    roof_top = p.roof_hip(hx0, top + 7, hz0, hx1, hz1, overhang=1, eave_curl=True, tiers=2, tier_gap=2, tier_band=2, band_role="wall")
    p.put(cx, roof_top, (hz0 + hz1) // 2, "accent")
    p.anchor(cx + 0.5, top + 2, hz0 + 2.5, "sektelangit/ketua")
    # the pagoda on the east: five storeys
    px0, pz0 = 35, 13
    p.fill(px0 - 1, top + 1, pz0 - 1, px0 + 5, top + 1, pz0 + 5, "floor_alt")
    p.walls(px0, top + 2, pz0, px0 + 4, top + 4, pz0 + 4, role="wall_alt", pillar="pillar", windows=0, door=("west", 1))
    pt = p.roof_hip(px0, top + 5, pz0, px0 + 4, pz0 + 4, overhang=1, eave_curl=True, tiers=4, tier_gap=1, tier_band=2, band_role="wall_alt")
    p.put(px0 + 2, pt, pz0 + 2, "minecraft:end_rod", {"facing": "up"})
    # the alchemy pavilion in the west, with its cauldron
    pavilion(p, 7, top + 1, 15, 12, 20)
    p.put(9, top + 1, 17, "minecraft:water_cauldron", {"level": "3"})
    p.put(10, top + 1, 18, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    # the garden in the south-east: plum trees, an enclosed pond, bamboo
    # an enclosed pond (water never reaches an edge): clay bed, a quartz kerb on all four sides at the water's level
    for x in range(27, 32):
        for z in range(31, 34):
            p.put(x, top, z, "minecraft:water")
            p.put(x, top - 1, z, "minecraft:clay")
        p.put(x, top, 30, "minecraft:smooth_quartz")
        p.put(x, top, 34, "minecraft:smooth_quartz")
    for z in range(30, 35):
        p.put(26, top, z, "minecraft:smooth_quartz")
        p.put(32, top, z, "minecraft:smooth_quartz")
    for x in range(26, 33):
        for z in range(30, 35):
            if p.get(x, top - 1, z) is None:
                p.put(x, top - 1, z, "minecraft:quartz_bricks")
    p.put(28, top + 1, 32, "minecraft:lily_pad")
    p.put(30, top + 1, 31, "minecraft:lily_pad")
    tree_plum(p, 36, top + 1, 29, rng)
    tree_plum(p, 14, top + 1, 31, rng)
    tree_plum(p, 33, top + 1, 20, rng)
    bamboo(p, 13, top + 1, 36, rng, n=7)
    # lanterns on the plaza ring and along the gate path
    for k in range(8):
        a = math.pi * 2 * k / 8 + math.pi / 8
        lantern_post(p, int(round(cx + math.cos(a) * 9)), top + 1, int(round(cz + math.sin(a) * 9)))
    for z in (34, 40):
        lantern_post(p, cx - 2, top + 1, z)
        lantern_post(p, cx + 2, top + 1, z)
    isle.rim("minecraft:quartz_slab", skip=(east, west, south))
    # the people: six members around the plaza, the gate greeter at the gate path
    for i in range(6):
        a = math.pi * 2 * i / 6
        p.anchor(cx + math.cos(a) * 5.5 + 0.5, top + 1, cz + math.sin(a) * 5.5 + 0.5, "sektelangit/murid", slot=i)
    p.anchor(cx + 0.5, top + 1, 41.5, "sektelangit/gerbang")
    TEMPLATES.append(("inti_" + style, p))


# ------------------------------------------------------------------------------------------------- satellites
def build_satelit(kind, seed):
    p = Piece(24, 28, 24, seed=seed, palette=GIOK)
    top = 14
    cx = cz = 12
    isle = Island(p, cx, cz, 9, top, seed + 11, lapis=1, depth=13, notch=(-math.pi / 2,)).build("batu" if kind == "arena" else "taman")
    isle.roots(3)
    rng = p.rng
    for z in range(0, cz):
        for dx in (-1, 0, 1):
            p.put(cx + dx, top, z, "path")
            if p.get(cx + dx, top - 1, z) is None:
                p.put(cx + dx, top - 1, z, "minecraft:quartz_bricks")
    connector(p, cx, top, 0, "north", NS + "kait_satelit", "minecraft:empty", "minecraft:empty")
    if kind == "kebun":
        # Taman Gantung: herb beds on moss, a tea pavilion, bamboo and flowers
        for bx, bz in ((7, 9), (15, 9), (7, 15), (15, 15)):
            for x in range(bx - 1, bx + 2):
                for z in range(bz - 1, bz + 2):
                    p.put(x, top, z, "minecraft:moss_block")
                    if p.get(x, top + 1, z) is None:
                        p.put(x, top + 1, z, rng.choice(["minecraft:fern", "minecraft:short_grass", "minecraft:lily_of_the_valley",
                                                          "minecraft:allium", "minecraft:azure_bluet"]))
        pavilion(p, 10, top + 1, 15, 14, 19)
        p.put(12, top + 1, 17, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
        bamboo(p, 18, top + 1, 6, rng, n=6)
        tree_plum(p, 5, top + 1, 5, rng)
        p.anchor(12.5, top + 1, 12.5, "sektelangit/murid", slot=6)
    elif kind == "alkimia":
        # Paviliun Tungku: a walled two-storey alchemy hall, a cauldron on a soul fire, shelves of jars
        p.fill(7, top + 1, 9, 17, top + 1, 18, "floor_alt")
        p.walls(8, top + 2, 10, 16, top + 5, 17, role="wall", pillar="pillar", windows=4, door=("north", 3))
        p.fill(9, top + 1, 11, 15, top + 1, 16, "floor")
        p.put(12, top + 2, 14, "minecraft:soul_campfire", {"facing": "north", "lit": "true", "signal_fire": "false", "waterlogged": "false"})
        p.put(12, top + 3, 14, "minecraft:water_cauldron", {"level": "3"})
        for x in (9, 15):
            for z in range(11, 17, 2):
                p.put(x, top + 2, z, "minecraft:bookshelf")
                p.put(x, top + 3, z, "minecraft:decorated_pot", {"facing": "east" if x == 9 else "west", "cracked": "false", "waterlogged": "false"})
        p.put(10, top + 2, 16, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "false", "has_bottle_2": "true"})
        p.roof_hip(8, top + 6, 10, 16, 17, overhang=1, eave_curl=True, tiers=2, tier_gap=2, tier_band=1, band_role="wall")
        p.lantern(12, top + 5, 12, chain=1)
        p.anchor(12.5, top + 2, 12.5, "sektelangit/murid", slot=7)
    else:
        # Gelanggang Awan: a lei tai of smooth stone, red pillars at its corners, targets for sword practice
        for x in range(7, 18):
            for z in range(7, 18):
                edge = x in (7, 17) or z in (7, 17)
                p.put(x, top + 1, z, "minecraft:smooth_stone_slab" if edge else "minecraft:smooth_stone", {"type": "bottom", "waterlogged": "false"}
                      if edge else None)
        for x in range(9, 16):
            p.put(x, top + 1, 12, "minecraft:polished_diorite")
        for (x, z) in ((7, 7), (17, 7), (7, 17), (17, 17)):
            p.pillar(x, top + 2, top + 4, z, "minecraft:stripped_mangrove_log", base=None, cap="minecraft:gold_block")
            p.put(x, top + 5, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
        for x in (4, 20):
            p.put(x, top + 1, 12, "minecraft:target", {"power": "0"})
            p.put(x, top + 2, 12, "minecraft:target", {"power": "0"})
        p.anchor(12.5, top + 2, 10.5, "sektelangit/murid", slot=8)
        p.anchor(12.5, top + 2, 14.5, "sektelangit/murid", slot=9)
    isle.rim("minecraft:quartz_slab", skip=(-math.pi / 2,))
    lantern_post(p, cx - 2, top + 1, 4)
    lantern_post(p, cx + 2, top + 1, 4)
    TEMPLATES.append(("satelit_" + kind, p))


# ------------------------------------------------------------------------------------------------- bridge and gate
def build_jembatan(seed):
    p = Piece(5, 12, 20, seed=seed, palette=GIOK)
    deck = 4
    for z in range(20):
        arch = 1 if 6 <= z <= 13 else 0
        for x in range(5):
            p.put(x, deck, z, JADE if x in (1, 2, 3) else "minecraft:quartz_bricks")
        if arch:
            for x in (1, 2, 3):
                p.slab(x, deck + 1, z, JADE_SLAB, "bottom" if z in (6, 13) else "top")
        rail_y = deck + 1 + arch
        for x in (0, 4):
            p.put(x, rail_y, z, "minecraft:pale_oak_fence", {"north": "true" if z > 0 else "false", "south": "true" if z < 19 else "false",
                                                             "east": "false", "west": "false", "waterlogged": "false"})
        if z % 4 == 2:
            for x in (0, 4):
                for k in range(1, 3):
                    p.put(x, deck - k, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
                p.put(x, deck - 3, z, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    for z in (0, 19):
        for x in (0, 4):
            p.pillar(x, deck + 1, deck + 4, z, "minecraft:quartz_pillar", base=None, cap="minecraft:chiseled_quartz_block")
            p.put(x, deck + 5, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    connector(p, 2, deck, 0, "north", NS + "kait_jembatan", "minecraft:empty", "minecraft:empty", final=JADE)
    connector(p, 2, deck, 19, "south", NS + "kait_luar", NS + "kait_satelit", NS + "satelit", final=JADE)
    TEMPLATES.append(("jembatan", p))


def build_gerbang(seed):
    p = Piece(13, 26, 12, seed=seed, palette=GIOK)
    floor = 4
    # the cantilevered terrace and its corbels
    for x in range(13):
        for z in range(12):
            p.put(x, floor, z, "minecraft:smooth_quartz" if 2 <= x <= 10 else "minecraft:quartz_bricks")
    for z in range(1, 12, 3):
        for x in (1, 11):
            for k in range(1, 4):
                p.put(x, floor - k, z, "minecraft:quartz_pillar", {"axis": "y"})
    for x in range(13):
        p.slab(x, floor + 1, 11, "minecraft:quartz_slab")
    for z in range(12):
        p.slab(0, floor + 1, z, "minecraft:quartz_slab")
        p.slab(12, floor + 1, z, "minecraft:quartz_slab")
    # the three-roofed gate (a smaller Gerbang Sembilan Langit): red pillars, a gold plaque, three stacked roofs
    gz = 6
    for x in (3, 9):
        p.pillar(x, floor + 1, floor + 9, gz, "minecraft:stripped_mangrove_log", base="minecraft:chiseled_quartz_block", cap="minecraft:gold_block")
    for x in range(3, 10):
        p.put(x, floor + 7, gz, "minecraft:stripped_mangrove_log", {"axis": "x"})
    p.put(6, floor + 8, gz, "minecraft:gilded_blackstone")
    p.put(5, floor + 8, gz, "minecraft:gold_block")
    p.put(7, floor + 8, gz, "minecraft:gold_block")
    y = floor + 10
    colors = [JADE_STAIRS, "minecraft:dark_prismarine_stairs", "minecraft:quartz_stairs"]
    slabs = [JADE_SLAB, "minecraft:dark_prismarine_slab", "minecraft:quartz_slab"]
    fulls = [JADE, "minecraft:dark_prismarine", "minecraft:quartz_block"]
    x0, x1 = 2, 10
    for t in range(3):
        top = p.roof_gable(x0, y, gz, x1, gz, role=colors[t], slab_role=slabs[t], full_role=fulls[t], along="x", overhang=1, eave_curl=True)
        y = top + 1
        x0 += 1
        x1 -= 1
        if t < 2:
            for x in range(x0, x1 + 1):
                p.put(x, y, gz, "minecraft:white_concrete")
            y += 1
    p.put(6, y, gz, "minecraft:end_rod", {"facing": "up"})
    for x in (2, 10):
        p.standing_lantern(x, floor + 1, 2, post="minecraft:pale_oak_fence", role="minecraft:lantern", height=2)
    connector(p, 6, floor, 0, "north", NS + "kait_gerbang", "minecraft:empty", "minecraft:empty")
    p.anchor(6.5, floor + 1, 9.5, "sektelangit/dermaga")
    TEMPLATES.append(("gerbang", p))


# ------------------------------------------------------------------------------------------------- worldgen JSON
def wjson(path, obj, pack=False):
    (PACK_JSONS if pack else JSONS).append((path, obj))


def jsons():
    pool_dir = os.path.join(DATA, "worldgen", "template_pool", MODULE)

    def single(name):
        return {"element_type": "minecraft:single_pool_element", "location": NS + name, "processors": "minecraft:empty", "projection": "rigid"}

    wjson(os.path.join(pool_dir, "inti.json"), {"elements": [{"element": single("inti_giok"), "weight": 1},
                                                           {"element": single("inti_awan"), "weight": 1}], "fallback": "minecraft:empty"})
    wjson(os.path.join(pool_dir, "jembatan.json"), {"elements": [{"element": single("jembatan"), "weight": 1}], "fallback": "minecraft:empty"})
    wjson(os.path.join(pool_dir, "satelit.json"), {"elements": [{"element": single("satelit_kebun"), "weight": 2},
                                                              {"element": single("satelit_alkimia"), "weight": 1},
                                                              {"element": single("satelit_arena"), "weight": 1}], "fallback": "minecraft:empty"})
    wjson(os.path.join(pool_dir, "gerbang.json"), {"elements": [{"element": single("gerbang"), "weight": 1}], "fallback": "minecraft:empty"})
    wjson(os.path.join(DATA, "worldgen", "structure", MODULE, "istana_langit.json"),
          {"type": "minecraft:jigsaw", "biomes": "#kultivator:has_structure/sekte_langit", "max_distance_from_center": 80, "size": 3,
           "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": []}},
           "start_height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 204}, "max_inclusive": {"absolute": 228}},
           "start_pool": NS + "inti", "step": "surface_structures", "terrain_adaptation": "none", "use_expansion_hack": False})
    wjson(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", "sekte_langit.json"),
          {"replace": False, "values": ["#minecraft:is_overworld"]})
    wjson(os.path.join(DATA, "tags", "worldgen", "structure", "sekte_langit.json"), {"replace": False, "values": [NS + "istana_langit"]})
    for index, salt in ((0, 360921), (1, 360922)):
        wjson(os.path.join(PACK, "data", "kultivator", "worldgen", "structure_set", MODULE, "istana_langit_%d.json" % index),
              {"placement": {"type": "kultivator:sekte_langit", "salt": salt, "spacing": 24, "separation": 8, "index": index},
               "structures": [{"structure": NS + "istana_langit", "weight": 1}]}, pack=True)
    wjson(os.path.join(PACK, "pack.mcmeta"),
          {"pack": {"description": {"translate": "kultivator.sekte_langit.npc.pack"}, "min_format": 121, "max_format": 121}}, pack=True)


# ------------------------------------------------------------------------------------------------- the trophy item
def item_assets():
    """Cangkang Kura-kura Langit: a 32 x 32 texture (jade scutes, gold seams, a cloud rim, a cream plastron) and a cuboid
    model (plastron, three shell steps, the gold ridge, four scute bosses, the cloud-jade rim)."""
    rng = random.Random(370707)
    img = Image(32, 32)
    # 0..15 x 0..15: the shell top (jade scutes with gold seams)
    for y in range(16):
        for x in range(16):
            hx = (x + (y // 4 % 2) * 2) % 4
            seam = hx == 0 or y % 4 == 0
            base = (46, 137, 99) if not seam else (212, 166, 55)
            shade = rng.randint(-10, 10)
            if not seam and (x + y) % 5 == 0:
                shade += 18
            img.set(x, y, (base[0] + shade, base[1] + shade, base[2] + shade, 255))
    # 16..31 x 0..15: the shell sides (darker jade, a cloud band)
    for y in range(16):
        for x in range(16, 32):
            if y in (6, 7):
                c = (236, 240, 250) if (x + y) % 3 else (200, 214, 236)
            else:
                d = rng.randint(-8, 8)
                c = (30 + d, 104 + d, 76 + d)
            img.set(x, y, (c[0], c[1], c[2], 255))
    # 0..15 x 16..31: the plastron (cream with brown plates)
    for y in range(16, 32):
        for x in range(16):
            plate = (x % 5 == 0) or ((y - 16) % 5 == 0)
            c = (150, 110, 70) if plate else (232, 218, 182)
            d = rng.randint(-6, 6)
            img.set(x, y, (c[0] + d, c[1] + d, c[2] + d, 255))
    # 16..31 x 16..31: gold (ridge, bosses) with highlights
    for y in range(16, 32):
        for x in range(16, 32):
            hi = (x + y) % 7 == 0
            c = (255, 226, 140) if hi else (212, 166, 55)
            img.set(x, y, (c[0], c[1], c[2], 255))
    tex = os.path.join(ASSETS, "textures", "item", "cangkang_kura_langit.png")
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    img.save(tex)

    def box(frm, to, top_uv, side_uv, bottom_uv=None):
        faces = {}
        for f in ("north", "south", "east", "west"):
            faces[f] = {"uv": side_uv, "texture": "#t"}
        faces["up"] = {"uv": top_uv, "texture": "#t"}
        faces["down"] = {"uv": bottom_uv or side_uv, "texture": "#t"}
        return {"from": frm, "to": to, "faces": faces}

    shell_top = [0, 0, 8, 8]
    shell_side = [8, 0, 16, 4]
    cloud = [8, 3, 16, 4]
    plastron = [0, 8, 8, 16]
    gold = [8, 8, 16, 16]
    elements = [
        box([3, 0, 2.5], [13, 1, 13.5], plastron, plastron, plastron),
        box([2, 1, 2], [14, 2, 14], shell_top, cloud, plastron),
        box([2.5, 2, 3], [13.5, 4, 13], shell_top, shell_side),
        box([4, 4, 4.5], [12, 5.5, 11.5], shell_top, shell_side),
        box([5.5, 5.5, 6], [10.5, 6.5, 10], shell_top, shell_side),
        box([7.5, 6.5, 4], [8.5, 7, 12], gold, gold),
        box([4.5, 4, 3.5], [6.5, 5, 5], gold, gold),
        box([9.5, 4, 3.5], [11.5, 5, 5], gold, gold),
        box([4.5, 4, 11], [6.5, 5, 12.5], gold, gold),
        box([9.5, 4, 11], [11.5, 5, 12.5], gold, gold),
        box([7, 0.5, 13.5], [9, 2, 15], shell_side, shell_side),
    ]
    model = {
        "textures": {"t": "kultivator:item/cangkang_kura_langit", "particle": "kultivator:item/cangkang_kura_langit"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [0.9, 0.9, 0.9]},
        },
    }
    write_json(os.path.join(ASSETS, "models", "item", "cangkang_kura_langit.json"), model)
    write_json(os.path.join(ASSETS, "items", "cangkang_kura_langit.json"),
               {"model": {"type": "minecraft:model", "model": "kultivator:item/cangkang_kura_langit"}})


# ------------------------------------------------------------------------------------------------- output
def main():
    build_inti("giok", 370601)
    build_inti("awan", 370602)
    build_satelit("kebun", 370611)
    build_satelit("alkimia", 370612)
    build_satelit("arena", 370613)
    build_jembatan(370621)
    build_gerbang(370631)
    jsons()
    problems = 0
    for name, p in TEMPLATES:
        path = p.save(out_path(MODULE, name))
        _, root = nbt.read_file(path)
        anchors = [e for e in root["entities"] if e["nbt"].get("id") == "kultivator:dunia_anchor"]
        jigsaws = sum(1 for b in root["blocks"] if "nbt" in b and b["nbt"].get("id") == "minecraft:jigsaw")
        top = max(y for (_x, y, _z) in p.cells) if p.cells else 0
        print("%s: %d blocks, %d anchors, %d connectors, top y=%d of %d, clipped %d" % (name, len(p.cells), len(anchors), jigsaws, top, p.size[1],
                                                                                         p.clipped))
        if len(root["blocks"]) != len(p.cells) or p.clipped:
            problems += 1
    for path, obj in JSONS + PACK_JSONS:
        write_json(path, obj)
    item_assets()
    print("module 37G: %d templates, %d json files (%d in the pack sekte_langit), problems: %d"
          % (len(TEMPLATES), len(JSONS) + len(PACK_JSONS), len(PACK_JSONS), problems))
    validate_all()
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
