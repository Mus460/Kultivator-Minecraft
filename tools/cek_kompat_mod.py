"""World-mod compatibility check (docs/progres/kompat-mod-dunia.md). Run after adding or changing a structure set:
    python tools/cek_kompat_mod.py [--soft] [MODS_DIR]

MODS_DIR (default %APPDATA%/.minecraft/versions/SODIUM/mods) holds the installed world-generation mods (Terralith,
Tectonic, Structory, Structory Towers, Dungeons and Taverns, Nullscape, Amplified Nether, ...). Every *.jar there is read
with zipfile (nothing is extracted or run). Checks (ERROR lines; exit code 1 when any error, unless --soft):
  1. Placement collisions: a Kultivator structure set whose start chunk comes from the vanilla random-spread grid
     (minecraft:random_spread, kultivator:jauh_dari_pusat, kultivator:cincin_tempat) with the same spacing, separation,
     spread type and salt as a set of a mod jar, of vanilla (StructureSets.java of 26.3) or of another Kultivator set
     picks the very same chunks in every world: ERROR. The same salt with another grid is only listed (INFO).
     Plan-driven placements (rencana_tempat, gerbang_sekte, sekte_langit, jalan, dimension_origin) do not use the salt
     for their position and are skipped.
  2. Floating structures: a Kultivator jigsaw with an absolute start height of 100 or more and no heightmap projection
     that spawns in overworld biomes must be in #kultivator:melayang (TempatKeepOut lifts it above Tectonic peaks).
  3. Summary of the mods' surface sets (spacing, separation, structure count) and of the steps they use that are not
     surface_structures (raw_generation, fluid_springs, ...): TempatKeepOut.permukaan must treat them as surface.
Overlaps of real starts are not decided here: ChunkGeneratorTempatMixin refuses a foreign start that touches a
Kultivator start at world generation (TempatKeepOut.saring), and roads skip foreign pieces (StructureStartJalanMixin).
"""
import glob
import json
import os
import sys
import zipfile

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(REPO, "src", "main", "resources")

# Grid placements: the start chunk is the vanilla RandomSpreadStructurePlacement.getPotentialStructureChunk of the salt.
GRID = {"minecraft:random_spread", "kultivator:jauh_dari_pusat", "kultivator:cincin_tempat"}

# Vanilla 26.3 net.minecraft.data.worldgen.StructureSets: id -> (spacing, separation, spread_type, salt).
VANILLA = {
    "minecraft:villages": (34, 8, "linear", 10387312),
    "minecraft:desert_pyramids": (32, 8, "linear", 14357617),
    "minecraft:igloos": (32, 8, "linear", 14357618),
    "minecraft:jungle_temples": (32, 8, "linear", 14357619),
    "minecraft:swamp_huts": (32, 8, "linear", 14357620),
    "minecraft:pillager_outposts": (32, 8, "linear", 165745296),
    "minecraft:ancient_cities": (24, 8, "linear", 20083232),
    "minecraft:ocean_monuments": (32, 5, "triangular", 10387313),
    "minecraft:woodland_mansions": (80, 20, "triangular", 10387319),
    "minecraft:buried_treasures": (1, 0, "linear", 0),
    "minecraft:mineshafts": (1, 0, "linear", 0),
    "minecraft:ruined_portals": (40, 15, "linear", 34222645),
    "minecraft:shipwrecks": (24, 4, "linear", 165745295),
    "minecraft:ocean_ruins": (20, 8, "linear", 14357621),
    "minecraft:nether_complexes": (27, 4, "linear", 30084232),
    "minecraft:nether_fossils": (2, 1, "linear", 14357921),
    "minecraft:end_cities": (20, 11, "triangular", 10387313),
    "minecraft:trail_ruins": (34, 8, "linear", 83469867),
    "minecraft:trial_chambers": (34, 12, "linear", 94251327),
    "minecraft:abandoned_camps": (37, 8, "linear", 91231127),
}


def grid_of(placement):
    """(spacing, separation, spread_type, salt) of a grid placement, else None."""
    if placement.get("type") not in GRID:
        return None
    spacing = int(placement.get("spacing", 1))
    separation = int(placement.get("separation", 0))
    spacing = max(spacing, separation + 1)  # our codecs force spacing > separation like vanilla validation
    return spacing, separation, placement.get("spread_type", "linear"), int(placement.get("salt", 0))


def set_id(path):
    """namespace:path of a structure_set file path inside data/."""
    p = path.replace("\\", "/")
    ns = p.split("data/", 1)[1].split("/", 1)[0]
    rest = p.split("/worldgen/structure_set/", 1)[1][:-5]
    return ns + ":" + rest


def our_sets():
    out = {}
    for f in glob.glob(os.path.join(RES, "**", "worldgen", "structure_set", "**", "*.json"), recursive=True):
        with open(f, encoding="utf-8") as fh:
            d = json.load(fh)
        if "placement" in d:
            out[set_id(f)] = d
    return out


def jar_sets(mods_dir):
    """{mod jar name: {set id: json}} for every jar with structure sets (Terralith's disable.* subpacks skipped)."""
    out = {}
    for jar in sorted(glob.glob(os.path.join(mods_dir, "*.jar"))):
        name = os.path.basename(jar)
        if name.startswith("kultivator"):
            continue
        sets = {}
        steps = {}
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            for n in names:
                if "/worldgen/structure_set/" in n and n.endswith(".json") and n.startswith(("data/", "resourcepacks/")) \
                        and "disable." not in n:
                    try:
                        sets[set_id(n)] = json.loads(z.read(n).decode("utf-8"))
                    except (ValueError, KeyError, IndexError):
                        continue
                elif "/worldgen/structure/" in n and n.endswith(".json") and "disable." not in n:
                    try:
                        s = json.loads(z.read(n).decode("utf-8"))
                    except ValueError:
                        continue
                    step = s.get("step", "surface_structures") if isinstance(s, dict) else "?"
                    steps[step] = steps.get(step, 0) + 1
        if sets:
            out[name] = (sets, steps)
    return out


def collisions(ours, others):
    """Pure core: [(our id, other id, grid, exact)] for grids sharing a salt; exact = same chunks every world."""
    found = []
    for oid, og in ours.items():
        for xid, xg in others.items():
            if oid == xid or og[3] != xg[3]:
                continue
            found.append((oid, xid, og, og == xg))
    return found


def _selftest():
    a = {"kultivator:a": (24, 8, "linear", 5)}
    b = {"mod:b": (24, 8, "linear", 5), "mod:c": (30, 8, "linear", 5), "mod:d": (24, 8, "linear", 6)}
    got = collisions(a, b)
    assert ("kultivator:a", "mod:b", (24, 8, "linear", 5), True) in got
    assert ("kultivator:a", "mod:c", (24, 8, "linear", 5), False) in got
    assert all(x[1] != "mod:d" for x in got)
    assert grid_of({"type": "kultivator:cincin_tempat", "spacing": 8, "separation": 8, "salt": 1}) == (9, 8, "linear", 1)
    assert grid_of({"type": "kultivator:rencana_tempat", "salt": 1}) is None


def melayang_errors():
    errors = []
    tag = os.path.join(RES, "data", "kultivator", "tags", "worldgen", "structure", "melayang.json")
    tagged = set()
    if os.path.exists(tag):
        with open(tag, encoding="utf-8") as fh:
            for v in json.load(fh)["values"]:
                tagged.add(v["id"] if isinstance(v, dict) else v)
    for f in glob.glob(os.path.join(RES, "**", "worldgen", "structure", "**", "*.json"), recursive=True):
        with open(f, encoding="utf-8") as fh:
            s = json.load(fh)
        if s.get("type") != "minecraft:jigsaw" or "project_start_to_heightmap" in s:
            continue
        sh = s.get("start_height", {})
        low = sh.get("absolute") if "absolute" in sh else sh.get("min_inclusive", {}).get("absolute")
        if low is None or low < 100:
            continue
        sid = "kultivator:" + f.replace("\\", "/").split("/worldgen/structure/", 1)[1][:-5]
        biomes = str(s.get("biomes", ""))
        overworld = "is_overworld" in biomes or "sekte_langit" in biomes
        if overworld and sid not in tagged:
            errors.append(f"ERROR floating {sid} (start y {low}) spawns in the overworld but is not in #kultivator:melayang")
        print(f"INFO floating {sid}: start y {low}, overworld={overworld}, melayang={sid in tagged}")
    return errors


def main(argv):
    _selftest()
    soft = "--soft" in argv
    args = [a for a in argv if not a.startswith("--")]
    mods = args[0] if args else os.path.join(os.environ.get("APPDATA", ""), ".minecraft", "versions", "SODIUM", "mods")
    errors = []
    ours = {k: g for k, d in our_sets().items() if (g := grid_of(d["placement"])) is not None}
    plan = sorted(k for k, d in our_sets().items() if grid_of(d["placement"]) is None)
    print(f"ours: {len(ours)} grid sets, {len(plan)} plan-driven sets (salt not used for position)")
    others = {k: v for k, v in VANILLA.items()}
    if os.path.isdir(mods):
        for jar, (sets, steps) in jar_sets(mods).items():
            grids = {k: g for k, d in sets.items() if (g := grid_of(d.get("placement", {}))) is not None}
            others.update(grids)
            odd = {k: v for k, v in steps.items() if k != "surface_structures"}
            print(f"mod {jar}: {len(sets)} sets ({len(grids)} grid), steps besides surface: {odd or '-'}")
    else:
        print(f"WARN mods dir not found: {mods} (vanilla only)")
    for oid, xid, g, exact in collisions(ours, others):
        if exact:
            errors.append(f"ERROR {oid} and {xid} share spacing/separation/spread/salt {g}: same start chunks")
        else:
            print(f"INFO {oid} {g[:3]} shares salt {g[3]} with {xid} {others[xid][:3]} (other grid, no collision)")
    for oid, xid, g, exact in collisions(ours, ours):
        if exact and oid < xid:
            errors.append(f"ERROR Kultivator sets {oid} and {xid} share the grid {g}")
    errors += melayang_errors()
    for e in errors:
        print(e)
    print(f"{len(errors)} error(s)")
    return 1 if errors and not soft else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
