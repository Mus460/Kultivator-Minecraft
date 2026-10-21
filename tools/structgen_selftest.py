"""Self test for tools/nbt.py and tools/structgen.py (DESIGN 34.3). Writes NOTHING into the mod.

Run with: python tools/structgen_selftest.py <scratch_dir> [vanilla_template.nbt ...]
It builds a tiny tea pavilion (one of each helper), saves it to <scratch_dir>/sample_pavilion.nbt, reads it back with
nbt.read_file, checks the round trip (palette, block order, jigsaw nbt, anchor entity, DataVersion), validates every id
against tools/data/vanilla_blocks_26_3.json and prints a side elevation and a plan. Extra .nbt arguments (for example
vanilla templates unzipped from the 26.3 jar) are read and summarised, to prove the reader matches the game's writer.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from structgen import PALETTES, Piece, validate  # noqa: E402

GLYPH = {
    "air": " ", "polished_andesite": "_", "stone_bricks": "=", "cracked_stone_bricks": "=", "dark_oak_planks": "#",
    "red_nether_bricks": "|", "deepslate_tile_stairs": "^", "deepslate_tile_slab": "-", "deepslate_tiles": "M",
    "lantern": "o", "iron_chain": ":", "jigsaw": "J", "dark_oak_fence": "+", "spruce_trapdoor": "/",
    "dark_oak_log": "L", "gold_block": "$",
}


def build(out_dir):
    p = Piece(11, 12, 11, seed=34003, palette=PALETTES["kota"])
    p.foundation(0, 0, 10, 10, depth=1)
    p.floor(0, 1, 0, 10, 10)
    p.walls(2, 2, 2, 8, 4, 8, windows=3, door=("south", 3))
    p.roof_gable(2, 5, 2, 8, 8, along="x", overhang=2)
    p.lantern(5, 5, 5, chain=1)
    p.standing_lantern(0, 2, 10)
    p.path([(5, 8), (5, 9)], 1, width=3)
    p.put(5, 1, 5, "accent")
    p.jigsaw_side(5, 1, 10, "south", name="kultivator:kota/pintu", target="kultivator:kota/jalan",
                  pool="kultivator:kota/jalan", final_state="minecraft:stone_bricks")
    p.anchor(5.5, 2.0, 5.5, "kota/kedai_teh", tier=1)
    path = os.path.join(out_dir, "sample_pavilion.nbt")
    p.save(path)
    return p, path


def check(p, path):
    name, root = nbt.read_file(path)
    assert name == "", "root name"
    assert root["DataVersion"] == nbt.DATA_VERSION == 5023, "DataVersion"
    assert root["size"] == [11, 12, 11], root["size"]
    palette, blocks = root["palette"], root["blocks"]
    assert len(blocks) == len(p.cells), (len(blocks), len(p.cells))
    for b in blocks:
        assert isinstance(b["state"], int) and 0 <= b["state"] < len(palette)
        x, y, z = b["pos"]
        key, _ = p.cells[(x, y, z)]
        entry = palette[b["state"]]
        assert entry["id"] == key[0], (entry, key)
        assert dict(entry.get("properties", {})) == dict(key[1]), (entry, key)
    # vanilla order: full blocks, then other blocks, then block entities
    kinds = ["nbt" if "nbt" in b else ("full" if nbt._is_full(palette[b["state"]]["id"],
                                                             palette[b["state"]].get("properties")) else "other")
             for b in blocks]
    order = {"full": 0, "other": 1, "nbt": 2}
    assert all(order[a] <= order[b] for a, b in zip(kinds, kinds[1:])), "block order"
    jig = [b for b in blocks if "nbt" in b]
    assert len(jig) == 1 and jig[0]["nbt"]["id"] == "minecraft:jigsaw" and jig[0]["nbt"]["pool"] == "kultivator:kota/jalan"
    assert palette[jig[0]["state"]]["properties"]["orientation"] == "south_up"
    ents = root["entities"]
    assert len(ents) == 1 and ents[0]["nbt"]["id"] == "kultivator:dunia_anchor" and ents[0]["blockPos"] == [5, 2, 5]
    assert isinstance(ents[0]["pos"][0], float), "entity pos must be doubles"
    # deterministic bytes
    with open(path, "rb") as f:
        first = f.read()
    p2, path2 = build(os.path.dirname(path))
    with open(path2, "rb") as f:
        assert f.read() == first, "output is not deterministic"
    errors, warnings = validate(p, "sample_pavilion")
    for w in warnings:
        print("warning:", w)
    assert not errors, errors
    assert p.clipped == 0, "helpers clipped %d block(s)" % p.clipped
    return root


def draw(root):
    pal = root["palette"]
    sx, sy, sz = root["size"]
    grid = {}
    for b in root["blocks"]:
        grid[tuple(b["pos"])] = pal[b["state"]]["id"].split(":")[1]

    def g(n):
        return GLYPH.get(n, "?") if n else "."
    print("section (x = 5, looking east; z runs left to right):")
    for y in range(sy - 1, -1, -1):
        print("  y%-2d " % y + "".join(g(grid.get((5, y, z))) for z in range(sz)))
    print("section (x = 0, the gable end with its curled eave corners):")
    for y in range(sy - 1, -1, -1):
        print("  y%-2d " % y + "".join(g(grid.get((0, y, z))) for z in range(sz)))
    print("plan (y = 1):")
    for z in range(sz):
        print("  z%-2d " % z + "".join(g(grid.get((x, 1, z))) for x in range(sx)))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    p, path = build(out_dir)
    root = check(p, path)
    print(nbt.describe(root))
    draw(root)
    for extra in sys.argv[2:]:
        _, r = nbt.read_file(extra)
        print(os.path.basename(extra) + ": " + nbt.describe(r))
    print("structgen self test OK:", path)


if __name__ == "__main__":
    main()
