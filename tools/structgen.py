"""Shared piece builder for the Dunia Kultivasi structures (DESIGN 34.3). Built on tools/nbt.py.

Every module's generator (tools/gen_dunia_<module>.py) does:

    import os, sys
    sys.path.insert(0, os.path.dirname(__file__))
    from structgen import Piece, PALETTES, out_path, validate_all

    p = Piece(15, 12, 15, seed=53001, palette=PALETTES["kota"])
    p.floor(0, 0, 0, 14, 14, "floor")
    p.walls(1, 1, 1, 13, 4, 13, "wall", pillar="pillar", windows=3)
    p.roof_hip(0, 5, 0, 14, 14, "roof", eave_curl=True)
    p.lantern(7, 4, 7)
    p.jigsaw_side(7, 0, 14, "south", name="kultivator:kota/door", target="kultivator:kota/street",
                  pool="kultivator:kota/streets", final_state="minecraft:dark_oak_planks")
    p.anchor(7.5, 1.0, 7.5, "kota/lelang")             # the DuniaAnchor entity, see DESIGN 34.4
    p.save(out_path("kota", "pusat/lelang_1"))
    validate_all()                                       # every id checked against the 26.3 block list

Coordinates are template-local: x east, y up, z south; (0,0,0) is the min corner. Every helper takes inclusive bounds.
Palettes map role names ("wall", "roof", "floor", "pillar", "trim", "accent", "glow", "path", "path_alt", "fence") to
block ids; a helper given a role looks it up, a helper given "minecraft:..." uses it as is.

Stair "facing" (vanilla): the full back of the stair is on the facing side, so a roof slope that rises toward the north
uses facing=north. roof_* helpers handle this; stairs() takes the facing explicitly.
Only worldgen ever places these templates (DESIGN 34.0 rule W1): runtime code never calls setBlock for them.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402

TOOLS = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(TOOLS, ".."))
DATA = os.path.join(REPO, "src", "main", "resources", "data", "kultivator")
BLOCK_LIST = os.path.join(TOOLS, "data", "vanilla_blocks_26_3.json")

_SAVED = []  # every Template saved in this run (validate_all checks them)

FACINGS = ("north", "east", "south", "west")
_DIR = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
_OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}

# Material palettes per module look (DESIGN 34.5.x). Modules may add their own dict; keep ids vanilla 26.3.
PALETTES = {
    # K: cultivator town (dark wood, red lacquer pillars, grey tile roofs, jade/gold accents)
    "kota": {
        "floor": "minecraft:polished_andesite", "floor_alt": "minecraft:stone_bricks", "wall": "minecraft:dark_oak_planks",
        "wall_alt": "minecraft:stripped_dark_oak_log", "pillar": "minecraft:red_nether_bricks", "trim": "minecraft:dark_oak_log",
        "roof": "minecraft:deepslate_tile_stairs", "roof_slab": "minecraft:deepslate_tile_slab",
        "roof_full": "minecraft:deepslate_tiles", "accent": "minecraft:gold_block", "jade": "minecraft:prismarine_bricks",
        "glow": "minecraft:lantern", "path": "minecraft:stone_bricks", "path_alt": "minecraft:cracked_stone_bricks",
        "fence": "minecraft:dark_oak_fence", "window": "minecraft:spruce_trapdoor", "carpet": "minecraft:red_carpet",
    },
    # R: ancestral cave ruins / demon prison / tower (mossy stone, deepslate, soul light)
    "leluhur": {
        "floor": "minecraft:polished_deepslate", "floor_alt": "minecraft:cracked_deepslate_tiles",
        "wall": "minecraft:stone_bricks", "wall_alt": "minecraft:mossy_stone_bricks", "pillar": "minecraft:chiseled_stone_bricks",
        "trim": "minecraft:polished_blackstone_bricks", "roof": "minecraft:deepslate_brick_stairs",
        "roof_slab": "minecraft:deepslate_brick_slab", "roof_full": "minecraft:deepslate_bricks",
        "accent": "minecraft:chiseled_tuff_bricks", "jade": "minecraft:oxidized_cut_copper", "glow": "minecraft:soul_lantern",
        "path": "minecraft:cobbled_deepslate", "path_alt": "minecraft:mossy_cobblestone", "fence": "minecraft:iron_bars",
        "window": "minecraft:iron_bars", "carpet": "minecraft:moss_carpet",
    },
    # R: demon prison (blackstone, crimson, chains)
    "penjara": {
        "floor": "minecraft:polished_blackstone", "floor_alt": "minecraft:cracked_polished_blackstone_bricks",
        "wall": "minecraft:polished_blackstone_bricks", "wall_alt": "minecraft:gilded_blackstone",
        "pillar": "minecraft:chiseled_polished_blackstone", "trim": "minecraft:crimson_hyphae",
        "roof": "minecraft:blackstone_stairs", "roof_slab": "minecraft:blackstone_slab", "roof_full": "minecraft:blackstone",
        "accent": "minecraft:crying_obsidian", "jade": "minecraft:obsidian", "glow": "minecraft:soul_lantern",
        "path": "minecraft:polished_basalt", "path_alt": "minecraft:basalt", "fence": "minecraft:iron_chain",
        "window": "minecraft:iron_bars", "carpet": "minecraft:black_carpet",
    },
    # R: 100-floor tower (white stone, quartz, gold trims)
    "menara": {
        "floor": "minecraft:polished_diorite", "floor_alt": "minecraft:smooth_quartz", "wall": "minecraft:quartz_bricks",
        "wall_alt": "minecraft:chiseled_quartz_block", "pillar": "minecraft:quartz_pillar", "trim": "minecraft:gold_block",
        "roof": "minecraft:dark_prismarine_stairs", "roof_slab": "minecraft:dark_prismarine_slab",
        "roof_full": "minecraft:dark_prismarine", "accent": "minecraft:lapis_block", "jade": "minecraft:prismarine",
        "glow": "minecraft:sea_lantern", "path": "minecraft:smooth_stone", "path_alt": "minecraft:polished_andesite",
        "fence": "minecraft:quartz_slab", "window": "minecraft:white_stained_glass_pane", "carpet": "minecraft:white_carpet",
    },
    # D: Dunia Bawah (Diyu): ash, soul soil, crimson bridge, bone
    "diyu": {
        "floor": "minecraft:soul_soil", "floor_alt": "minecraft:smooth_basalt", "wall": "minecraft:nether_bricks",
        "wall_alt": "minecraft:cracked_nether_bricks", "pillar": "minecraft:crimson_stem", "trim": "minecraft:bone_block",
        "roof": "minecraft:nether_brick_stairs", "roof_slab": "minecraft:nether_brick_slab", "roof_full": "minecraft:nether_bricks",
        "accent": "minecraft:shroomlight", "jade": "minecraft:warped_wart_block", "glow": "minecraft:soul_lantern",
        "path": "minecraft:crimson_planks", "path_alt": "minecraft:crimson_slab", "fence": "minecraft:crimson_fence",
        "window": "minecraft:iron_bars", "carpet": "minecraft:red_carpet",
    },
    # D: Laut Tanpa Batas / Istana Naga (prismarine, coral, pearl light)
    "laut": {
        "floor": "minecraft:prismarine_bricks", "floor_alt": "minecraft:dark_prismarine", "wall": "minecraft:prismarine",
        "wall_alt": "minecraft:prismarine_bricks", "pillar": "minecraft:quartz_pillar", "trim": "minecraft:gold_block",
        "roof": "minecraft:dark_prismarine_stairs", "roof_slab": "minecraft:dark_prismarine_slab",
        "roof_full": "minecraft:dark_prismarine", "accent": "minecraft:tube_coral_block", "jade": "minecraft:sea_lantern",
        "glow": "minecraft:sea_lantern", "path": "minecraft:smooth_sandstone", "path_alt": "minecraft:sandstone",
        "fence": "minecraft:prismarine_wall", "window": "minecraft:light_blue_stained_glass_pane",
        "carpet": "minecraft:cyan_carpet",
    },
    # K: Kota Abadi (white jade, gold, cherry)
    "abadi": {
        "floor": "minecraft:smooth_quartz", "floor_alt": "minecraft:quartz_bricks", "wall": "minecraft:white_concrete",
        "wall_alt": "minecraft:calcite", "pillar": "minecraft:quartz_pillar", "trim": "minecraft:gold_block",
        "roof": "minecraft:cherry_stairs", "roof_slab": "minecraft:cherry_slab", "roof_full": "minecraft:cherry_planks",
        "accent": "minecraft:amethyst_block", "jade": "minecraft:emerald_block", "glow": "minecraft:pearlescent_froglight",
        "path": "minecraft:quartz_bricks", "path_alt": "minecraft:smooth_quartz", "fence": "minecraft:cherry_fence",
        "window": "minecraft:white_stained_glass_pane", "carpet": "minecraft:pink_carpet",
    },
}


def out_path(module, name):
    """data/kultivator/structure/<module>/<name>.nbt (module = kota, reruntuhan, penjara, menara, langit, naga, bawah,
    laut, kapal; DESIGN 34.6 asset folders)."""
    return os.path.join(DATA, "structure", module, name + ".nbt")


def json_path(kind, module, name):
    """data/kultivator/worldgen/<kind>/<module>/<name>.json, kind = structure | template_pool | structure_set |
    processor_list (the structure registry ids then read kultivator:<module>/<name>)."""
    return os.path.join(DATA, "worldgen", kind, module, name + ".json")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")
    return path


class Piece(nbt.Template):
    """A Template with building helpers, a palette and a seeded RNG (same seed -> same bytes)."""

    def __init__(self, sx, sy, sz, seed=0, palette=None):
        super().__init__(sx, sy, sz)
        self.rng = random.Random(seed)
        self.pal = dict(palette or PALETTES["kota"])
        self.clipped = 0  # helper blocks that fell outside the piece (validate() reports them)

    # ------------------------------------------------------------------------------------------------- basics
    def b(self, role):
        """A palette role or a literal id."""
        if role is None:
            return None
        if ":" in role:
            return role
        if role not in self.pal:
            raise KeyError("palette has no role %r" % role)
        return self.pal[role]

    def put(self, x, y, z, role, props=None, nbt_data=None):
        if self.inside(x, y, z):
            self.set(x, y, z, self.b(role), props, nbt_data)
        else:
            self.clipped += 1

    def fill(self, x0, y0, z0, x1, y1, z1, role, props=None, alt=None, alt_chance=0.0):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    r = alt if alt and self.rng.random() < alt_chance else role
                    self.put(x, y, z, r, props)

    def air(self, x0, y0, z0, x1, y1, z1):
        """Explicit air: clears the terrain the piece is placed into (interiors, doorways)."""
        self.fill(x0, y0, z0, x1, y1, z1, "minecraft:air")

    def floor(self, x0, y, z0, x1, z1, role="floor", alt="floor_alt", alt_chance=0.15):
        self.fill(x0, y, z0, x1, y, z1, role, alt=alt if alt in self.pal or (alt and ":" in alt) else None,
                  alt_chance=alt_chance)

    def box(self, x0, y0, z0, x1, y1, z1, role, hollow=True):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    edge = x in (x0, x1) or y in (y0, y1) or z in (z0, z1)
                    if edge or not hollow:
                        self.put(x, y, z, role)
                    else:
                        self.put(x, y, z, "minecraft:air")

    def walls(self, x0, y0, z0, x1, y1, z1, role="wall", pillar="pillar", windows=0, window_role="window",
              door=None, interior_air=True):
        """Four walls with corner/every-4 pillars, optional windows every `windows` blocks at y0+1..y0+2, an optional
        doorway door=("south"|..., width) centred on that side, and an air interior so terrain never fills a room."""
        if interior_air:
            self.air(x0 + 1, y0, z0 + 1, x1 - 1, y1, z1 - 1)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if not (x in (x0, x1) or z in (z0, z1)):
                        continue
                    corner = x in (x0, x1) and z in (z0, z1)
                    along = (x - x0) if z in (z0, z1) else (z - z0)
                    is_pillar = pillar and (corner or along % 4 == 0)
                    if is_pillar:
                        self.put(x, y, z, pillar, self._axis_y(pillar))
                        continue
                    if windows and y in (y0 + 1, y0 + 2) and along % windows == windows // 2:
                        self._window(x, y, z, x0, x1, z0, z1, window_role)
                        continue
                    self.put(x, y, z, role)
        if door:
            side, width = door
            self.doorway(x0, y0, z0, x1, z1, side, width)

    def _axis_y(self, role):
        bid = self.b(role)
        if bid.endswith(("_log", "_stem", "_pillar", "_hyphae", "_wood", "basalt", "bone_block", "hay_block")):
            return {"axis": "y"}
        return None

    def _window(self, x, y, z, x0, x1, z0, z1, role):
        bid = self.b(role)
        if bid.endswith("_trapdoor"):
            facing = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
            self.put(x, y, z, bid, {"facing": facing, "half": "bottom", "open": "true", "powered": "false",
                                    "waterlogged": "false"})
        else:
            self.put(x, y, z, bid)

    def doorway(self, x0, y0, z0, x1, z1, side, width=3, height=3):
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        half = width // 2
        for dy in range(height):
            for d in range(-half, width - half):
                if side in ("north", "south"):
                    self.put(cx + d, y0 + dy, z0 if side == "north" else z1, "minecraft:air")
                else:
                    self.put(x0 if side == "west" else x1, y0 + dy, cz + d, "minecraft:air")

    def pillar(self, x, y0, y1, z, role="pillar", base="trim", cap="trim"):
        if base:
            self.put(x, y0, z, base, self._axis_y(base))
        for y in range(y0 + (1 if base else 0), y1 + (0 if cap else 1)):
            self.put(x, y, z, role, self._axis_y(role))
        if cap:
            self.put(x, y1, z, cap, self._axis_y(cap))

    def stairs(self, x, y, z, role, facing, half="bottom", shape="straight"):
        self.put(x, y, z, role, {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"})

    def slab(self, x, y, z, role, kind="bottom"):
        self.put(x, y, z, role, {"type": kind, "waterlogged": "false"})

    def staircase(self, x, y, z, direction, steps, width=1, role="roof", support="wall"):
        """A walkable flight going up toward `direction`, `width` wide (to the right of the direction)."""
        dx, dz = _DIR[direction]
        rx, rz = -dz, dx
        for i in range(steps):
            for w in range(width):
                px, pz = x + dx * i + rx * w, z + dz * i + rz * w
                self.stairs(px, y + i, pz, role, direction)
                for yy in range(y, y + i):
                    if support:
                        self.put(px, yy, pz, support)

    # ------------------------------------------------------------------------------------------------- roofs
    def roof_gable(self, x0, y, z0, x1, z1, role="roof", slab_role="roof_slab", full_role="roof_full", along="x",
                   overhang=1, eave_curl=True):
        """A Chinese gable roof: ridge along `along` (x or z), two slopes of stairs, an overhang of `overhang` blocks,
        a slab ridge with upturned ends (chiwen) and, with eave_curl, upturned eave corners (a top-half stair one block
        above each corner). Returns the y of the ridge."""
        xa, xb, za, zb = x0 - overhang, x1 + overhang, z0 - overhang, z1 + overhang
        if along == "x":
            ua, ub, va, vb = xa, xb, za, zb          # u runs along the ridge, v across it

            def at(u, v):
                return u, v
            low_face, high_face, end_a, end_b = "south", "north", "east", "west"
        else:
            ua, ub, va, vb = za, zb, xa, xb

            def at(u, v):
                return v, u
            low_face, high_face, end_a, end_b = "east", "west", "south", "north"
        rows = vb - va + 1
        layers = rows // 2
        for i in range(layers):
            for u in range(ua, ub + 1):
                x, z = at(u, va + i)
                self.stairs(x, y + i, z, role, low_face)
                x, z = at(u, vb - i)
                self.stairs(x, y + i, z, role, high_face)
        top = y + layers
        mids = [va + layers] if rows % 2 else [va + layers - 1, va + layers]
        for u in range(ua, ub + 1):
            for v in mids:
                x, z = at(u, v)
                if rows % 2:
                    self.put(x, top - 1, z, full_role)
                self.slab(x, top, z, slab_role)
        for u, face in ((ua, end_a), (ub, end_b)):
            x, z = at(u, mids[0])
            self.stairs(x, top + 1, z, role, face, half="top")
        if eave_curl:
            for u in (ua, ub):
                x, z = at(u, va)
                self.stairs(x, y + 1, z, role, low_face, half="top")
                x, z = at(u, vb)
                self.stairs(x, y + 1, z, role, high_face, half="top")
        return top

    def roof_hip(self, x0, y, z0, x1, z1, role="roof", slab_role="roof_slab", full_role="roof_full", overhang=1,
                 eave_curl=True, tiers=1, tier_gap=2, tier_band=2, band_role="wall"):
        """A hip (wudian) roof, optionally multi-tier like a pagoda: every tier shrinks by one ring per layer, corners
        use outer stair shapes and, with eave_curl, the four corners curl up (upside-down stair one block higher).
        tiers > 1: after `tier_gap` stair layers a `tier_band` high wall band starts the next storey. Returns the y above
        the last layer."""
        ax0, az0, ax1, az1 = x0 - overhang, z0 - overhang, x1 + overhang, z1 + overhang
        yy = y
        for t in range(tiers):
            i = 0
            while ax0 + i <= ax1 - i and az0 + i <= az1 - i:
                lx0, lz0, lx1, lz1 = ax0 + i, az0 + i, ax1 - i, az1 - i
                if lx0 == lx1 or lz0 == lz1:
                    for x in range(lx0, lx1 + 1):
                        for z in range(lz0, lz1 + 1):
                            self.slab(x, yy, z, slab_role)
                    break
                for x in range(lx0 + 1, lx1):
                    self.stairs(x, yy, lz0, role, "south")
                    self.stairs(x, yy, lz1, role, "north")
                for z in range(lz0 + 1, lz1):
                    self.stairs(lx0, yy, z, role, "east")
                    self.stairs(lx1, yy, z, role, "west")
                self.stairs(lx0, yy, lz0, role, "south", shape="outer_left")
                self.stairs(lx1, yy, lz0, role, "south", shape="outer_right")
                self.stairs(lx0, yy, lz1, role, "north", shape="outer_right")
                self.stairs(lx1, yy, lz1, role, "north", shape="outer_left")
                if i == 0 and eave_curl:
                    for cx, cz, f in ((lx0, lz0, "south"), (lx1, lz0, "south"), (lx0, lz1, "north"), (lx1, lz1, "north")):
                        self.stairs(cx, yy + 1, cz, role, f, half="top")
                i += 1
                yy += 1
                if t < tiers - 1 and i >= tier_gap:
                    break
            if t < tiers - 1:
                # the next tier sits on a band of wall just inside the last stair ring (the pagoda's storey), then
                # flares out one block again: every tier is one ring smaller than the one below
                rx0, rz0, rx1, rz1 = ax0 + i, az0 + i, ax1 - i, az1 - i
                if rx1 - rx0 < 2 or rz1 - rz0 < 2:
                    break
                for band in range(tier_band):
                    for x in range(rx0, rx1 + 1):
                        for z in range(rz0, rz1 + 1):
                            if x in (rx0, rx1) or z in (rz0, rz1):
                                self.put(x, yy, z, band_role)
                    yy += 1
                ax0, az0, ax1, az1 = rx0 - 1, rz0 - 1, rx1 + 1, rz1 + 1
        return yy

    # ------------------------------------------------------------------------------------------------- details
    def lantern(self, x, y, z, chain=1, role="glow"):
        """A lantern hanging from `chain` iron chains below (x, y, z) (y is the ceiling block above the chain)."""
        for i in range(chain):
            self.put(x, y - 1 - i, z, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        bid = self.b(role)
        props = {"hanging": "true", "waterlogged": "false"} if bid.endswith("lantern") and bid != "minecraft:sea_lantern" else None
        self.put(x, y - 1 - chain, z, bid, props)

    def standing_lantern(self, x, y, z, post="fence", role="glow", height=2):
        for i in range(height):
            self.put(x, y + i, z, post)
        bid = self.b(role)
        props = {"hanging": "false", "waterlogged": "false"} if bid.endswith("lantern") and bid != "minecraft:sea_lantern" else None
        self.put(x, y + height, z, bid, props)

    def path(self, points, y, width=3, role="path", alt="path_alt", alt_chance=0.2, edge=None):
        """A paved path through the polyline `points` [(x, z), ...] at height y, `width` wide, edged with `edge`."""
        half = width // 2
        for (ax, az), (bx, bz) in zip(points, points[1:]):
            steps = max(abs(bx - ax), abs(bz - az), 1)
            for s in range(steps + 1):
                cx = round(ax + (bx - ax) * s / steps)
                cz = round(az + (bz - az) * s / steps)
                for dx in range(-half, half + 1):
                    for dz in range(-half, half + 1):
                        r = alt if alt and self.rng.random() < alt_chance else role
                        self.put(cx + dx, y, cz + dz, r)
                if edge:
                    for dx, dz in ((half + 1, 0), (-half - 1, 0), (0, half + 1), (0, -half - 1)):
                        if self.inside(cx + dx, y, cz + dz) and self.get(cx + dx, y, cz + dz) is None:
                            self.put(cx + dx, y, cz + dz, edge)

    def weather(self, chance=0.08, pairs=None):
        """Age the piece: swap a share of blocks for their weathered form (mossy/cracked), seeded."""
        pairs = pairs or {
            "minecraft:stone_bricks": ("minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
            "minecraft:cobblestone": ("minecraft:mossy_cobblestone",),
            "minecraft:deepslate_bricks": ("minecraft:cracked_deepslate_bricks",),
            "minecraft:deepslate_tiles": ("minecraft:cracked_deepslate_tiles",),
            "minecraft:polished_blackstone_bricks": ("minecraft:cracked_polished_blackstone_bricks",),
            "minecraft:nether_bricks": ("minecraft:cracked_nether_bricks",),
        }
        for pos in sorted(self.cells):
            (bid, props), data = self.cells[pos]
            if data is None and bid in pairs and self.rng.random() < chance:
                self.set(pos[0], pos[1], pos[2], self.rng.choice(pairs[bid]), dict(props))

    def ring(self, cx, y, cz, radius, role, thickness=1):
        for x in range(cx - radius - 1, cx + radius + 2):
            for z in range(cz - radius - 1, cz + radius + 2):
                d = math.hypot(x - cx, z - cz)
                if radius - thickness < d <= radius + 0.5:
                    self.put(x, y, z, role)

    def disc(self, cx, y, cz, radius, role, alt=None, alt_chance=0.0):
        for x in range(cx - radius, cx + radius + 1):
            for z in range(cz - radius, cz + radius + 1):
                if math.hypot(x - cx, z - cz) <= radius + 0.5:
                    r = alt if alt and self.rng.random() < alt_chance else role
                    self.put(x, y, z, r)

    def foundation(self, x0, z0, x1, z1, depth=3, role="floor_alt"):
        """Solid rows under y=0..depth-1 so a piece on uneven ground stands on stone (use with terrain_adaptation
        beard_thin/beard_box in the structure JSON; the piece's floor is then at y=depth)."""
        self.fill(x0, 0, z0, x1, depth - 1, z1, role)

    # ------------------------------------------------------------------------------------------------- jigsaw
    def jigsaw_side(self, x, y, z, facing, name, target, pool, final_state="minecraft:air", **kw):
        """A horizontal connector facing outward (FrontAndTop "<facing>_up")."""
        self.jigsaw(x, y, z, facing + "_up", name, target, pool, final_state, **kw)

    def jigsaw_up(self, x, y, z, name, target, pool, final_state="minecraft:air", top="north", **kw):
        self.jigsaw(x, y, z, "up_" + top, name, target, pool, final_state, **kw)

    def jigsaw_down(self, x, y, z, name, target, pool, final_state="minecraft:air", top="north", **kw):
        self.jigsaw(x, y, z, "down_" + top, name, target, pool, final_state, **kw)

    # ------------------------------------------------------------------------------------------------- entities
    def anchor(self, x, y, z, kind, **data):
        """The DuniaAnchor entity (DESIGN 34.4.1): a never-synced, non-ticking marker the module's server code turns
        into NPCs, puzzle displays, seals or map markers when a player is near. `kind` is "<module>/<what>"."""
        payload = {"id": "kultivator:dunia_anchor", "kind": kind}
        if data:
            payload["data"] = {k: v for k, v in data.items()}
        self.entity(x, y, z, payload)

    def save(self, path):
        nbt.Template.save(self, path)
        _SAVED.append((path, self))
        return path


# --------------------------------------------------------------------------------------------------- JSON helpers
def single(location, processors="minecraft:empty", projection="rigid"):
    """A template_pool element (StructurePoolElementType "single_pool_element")."""
    return {"element_type": "minecraft:single_pool_element", "location": location, "processors": processors,
            "projection": projection}


def pool(name, elements, fallback="minecraft:empty"):
    """A template_pool: elements = [(element_dict, weight 1..150), ...]."""
    return {"elements": [{"element": e, "weight": w} for e, w in elements], "fallback": fallback}


def jigsaw_structure(start_pool, biomes, size=4, max_distance=80, step="surface_structures",
                     terrain_adaptation="beard_thin", start_height=0, heightmap="WORLD_SURFACE_WG",
                     start_jigsaw_name=None, spawn_overrides=None, expansion_hack=False):
    s = {"type": "minecraft:jigsaw", "biomes": biomes, "max_distance_from_center": max_distance, "size": size,
         "spawn_overrides": spawn_overrides or {}, "start_height": {"absolute": start_height}, "start_pool": start_pool,
         "step": step, "terrain_adaptation": terrain_adaptation, "use_expansion_hack": expansion_hack}
    if heightmap:
        s["project_start_to_heightmap"] = heightmap
    if start_jigsaw_name:
        s["start_jigsaw_name"] = start_jigsaw_name
    return s


def structure_set(structures, spacing, separation, salt, frequency=None, exclusion=None):
    """random_spread placement. spacing/separation in chunks; DESIGN 34.2 W4 keeps them moderate."""
    placement = {"type": "minecraft:random_spread", "salt": salt, "separation": separation, "spacing": spacing}
    if frequency is not None:
        placement["frequency"] = frequency
    if exclusion:
        placement["exclusion_zone"] = {"other_set": exclusion[0], "chunk_count": exclusion[1]}
    return {"placement": placement, "structures": [{"structure": s, "weight": w} for s, w in structures]}


# --------------------------------------------------------------------------------------------------- validation
def _load_block_list():
    if os.path.exists(BLOCK_LIST):
        with open(BLOCK_LIST, encoding="utf-8") as f:
            return json.load(f)
    return None


def build_block_list(client_jar):
    """Writes tools/data/vanilla_blocks_26_3.json from a 26.3 client jar (assets/minecraft/blockstates/*.json):
    {block id: [property names seen in its blockstate file]}. Run once: python tools/structgen.py --blocks <jar>."""
    import zipfile
    out = {"minecraft:air": [], "minecraft:cave_air": [], "minecraft:void_air": []}
    with zipfile.ZipFile(client_jar) as z:
        for name in z.namelist():
            if not (name.startswith("assets/minecraft/blockstates/") and name.endswith(".json")):
                continue
            block = "minecraft:" + name.rsplit("/", 1)[1][:-5]
            data = json.loads(z.read(name).decode("utf-8"))
            props = set()
            for key in (data.get("variants") or {}):
                for part in key.split(","):
                    if "=" in part:
                        props.add(part.split("=", 1)[0])
            for part in data.get("multipart") or []:
                when = part.get("when") or {}
                for cond in when.get("OR", [when]) if isinstance(when, dict) else []:
                    for k in cond:
                        if k != "OR":
                            props.add(k)
            out[block] = sorted(props)
    os.makedirs(os.path.dirname(BLOCK_LIST), exist_ok=True)
    with open(BLOCK_LIST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(out.items())), f, indent=0, sort_keys=True)
        f.write("\n")
    return len(out)


# Properties a blockstate file may leave out of its variant keys (they do not change the model).
_SILENT_PROPS = {"waterlogged", "powered", "persistent", "distance", "open", "in_wall", "occupied", "lit", "signal_fire",
                 "triggered", "enabled", "inverted", "attached", "disarmed", "has_book", "has_record", "bloom", "shrieking",
                 "can_summon", "tip", "berries", "unstable", "drag", "conditional", "mode", "note", "instrument", "age",
                 "stage", "hatch", "eggs", "pickles", "candles", "honey_level", "moisture", "level", "rotation"}


def validate(template, where="", block_list=None):
    """Errors for unknown block ids (strict) and warnings for unknown property names (soft)."""
    blocks = block_list if block_list is not None else _load_block_list()
    errors, warnings = [], []
    if blocks is None:
        warnings.append("%s: no tools/data/vanilla_blocks_26_3.json, ids not checked" % where)
        return errors, warnings
    seen = set()
    for (bid, props), _ in template.cells.values():
        if (bid, props) in seen:
            continue
        seen.add((bid, props))
        if bid.startswith("kultivator:"):
            continue  # mod blocks are checked by the Java registry at load (a missing one logs and becomes air)
        if bid not in blocks:
            errors.append("%s: unknown block %s" % (where, bid))
            continue
        known = set(blocks[bid])
        for k, _v in props:
            if k not in known and k not in _SILENT_PROPS:
                warnings.append("%s: %s has no property %r in its blockstate file" % (where, bid, k))
    if getattr(template, "clipped", 0):
        warnings.append("%s: %d helper block(s) fell outside the piece (make it larger or move the helper)"
                        % (where, template.clipped))
    for x, y, z, data in template.entities:
        if data.get("id") == "kultivator:dunia_anchor" and "/" not in data.get("kind", ""):
            errors.append("%s: anchor kind %r must be <module>/<what>" % (where, data.get("kind")))
    return errors, warnings


def validate_all(strict=True):
    blocks = _load_block_list()
    all_err, all_warn = [], []
    for path, t in _SAVED:
        e, w = validate(t, os.path.relpath(path, REPO), blocks)
        all_err += e
        all_warn += w
    for w in sorted(set(all_warn)):
        print("warning:", w)
    for e in all_err:
        print("ERROR:", e)
    if all_err and strict:
        raise SystemExit("structgen: %d invalid block(s)" % len(all_err))
    print("structgen: %d template(s) valid" % len(_SAVED))


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--blocks":
        print("wrote %d block ids to %s" % (build_block_list(sys.argv[2]), BLOCK_LIST))
    else:
        print(__doc__)
