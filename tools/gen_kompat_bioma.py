"""Biome compatibility pack kultivator:kompat_bioma (Terralith).

Every biome tag of the mod lists vanilla biomes only, so in a Terralith world (where most of the surface is
terralith:*) the towns, capitals, villages, sect gates, caves and ruins found almost no place to stand. This
generator reads every biome tag under data/kultivator/tags/worldgen/biome (base data and the built-in packs) and
writes an extension tag with the same id into resourcepacks/kompat_bioma: for each vanilla biome a tag lists, the
Terralith biomes whose closest vanilla kin (KIN below) is that biome, as optional entries ("required": false), so
the pack is harmless without Terralith. Tags merge across packs ("replace": false).

Run after any generator that adds or changes a biome tag:  python tools/gen_kompat_bioma.py
"""
import json
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
OUT = os.path.join(RES, "resourcepacks", "kompat_bioma")
TAGS = os.path.join("data", "kultivator", "tags", "worldgen", "biome")

# Terralith 2.6 surface biomes -> the vanilla biome they stand in for. Caves map to the vanilla cave biomes,
# sky islands and the warm ocean have no kin (left out on purpose).
KIN = {
    "alpha_islands": "minecraft:beach",
    "alpha_islands_winter": "minecraft:snowy_beach",
    "alpine_grove": "minecraft:grove",
    "alpine_highlands": "minecraft:meadow",
    "amethyst_canyon": "minecraft:windswept_hills",
    "amethyst_rainforest": "minecraft:jungle",
    "ancient_sands": "minecraft:desert",
    "arid_highlands": "minecraft:savanna_plateau",
    "ashen_savanna": "minecraft:savanna",
    "basalt_cliffs": "minecraft:stony_shore",
    "birch_taiga": "minecraft:birch_forest",
    "blooming_plateau": "minecraft:meadow",
    "blooming_valley": "minecraft:flower_forest",
    "brushland": "minecraft:savanna",
    "bryce_canyon": "minecraft:eroded_badlands",
    "caldera": "minecraft:windswept_hills",
    "cloud_forest": "minecraft:forest",
    "cold_shrubland": "minecraft:snowy_plains",
    "desert_canyon": "minecraft:badlands",
    "desert_oasis": "minecraft:desert",
    "desert_spires": "minecraft:desert",
    "emerald_peaks": "minecraft:stony_peaks",
    "forested_highlands": "minecraft:forest",
    "fractured_savanna": "minecraft:windswept_savanna",
    "frozen_cliffs": "minecraft:frozen_peaks",
    "glacial_chasm": "minecraft:ice_spikes",
    "granite_cliffs": "minecraft:stony_peaks",
    "gravel_beach": "minecraft:stony_shore",
    "gravel_desert": "minecraft:desert",
    "haze_mountain": "minecraft:jagged_peaks",
    "highlands": "minecraft:meadow",
    "hot_shrubland": "minecraft:savanna",
    "ice_marsh": "minecraft:swamp",
    "jungle_mountains": "minecraft:jungle",
    "lavender_forest": "minecraft:flower_forest",
    "lavender_valley": "minecraft:meadow",
    "lush_desert": "minecraft:desert",
    "lush_valley": "minecraft:plains",
    "mirage_isles": "minecraft:beach",
    "moonlight_grove": "minecraft:forest",
    "moonlight_valley": "minecraft:plains",
    "orchid_swamp": "minecraft:swamp",
    "painted_mountains": "minecraft:badlands",
    "red_oasis": "minecraft:desert",
    "rocky_jungle": "minecraft:sparse_jungle",
    "rocky_mountains": "minecraft:windswept_hills",
    "rocky_shrubland": "minecraft:savanna",
    "sakura_grove": "minecraft:cherry_grove",
    "sakura_valley": "minecraft:cherry_grove",
    "sandstone_valley": "minecraft:desert",
    "savanna_badlands": "minecraft:wooded_badlands",
    "savanna_slopes": "minecraft:savanna_plateau",
    "scarlet_mountains": "minecraft:windswept_forest",
    "shield": "minecraft:taiga",
    "shield_clearing": "minecraft:plains",
    "shrubland": "minecraft:plains",
    "siberian_grove": "minecraft:snowy_taiga",
    "siberian_taiga": "minecraft:snowy_taiga",
    "snowy_badlands": "minecraft:eroded_badlands",
    "snowy_cherry_grove": "minecraft:cherry_grove",
    "snowy_maple_forest": "minecraft:snowy_taiga",
    "snowy_shield": "minecraft:snowy_taiga",
    "steppe": "minecraft:plains",
    "stony_spires": "minecraft:stony_peaks",
    "temperate_highlands": "minecraft:meadow",
    "tropical_jungle": "minecraft:jungle",
    "valley_clearing": "minecraft:plains",
    "volcanic_crater": "minecraft:windswept_gravelly_hills",
    "volcanic_peaks": "minecraft:jagged_peaks",
    "warm_river": "minecraft:river",
    "warped_mesa": "minecraft:badlands",
    "white_cliffs": "minecraft:stony_shore",
    "white_mesa": "minecraft:badlands",
    "windswept_spires": "minecraft:frozen_peaks",
    "wintry_forest": "minecraft:snowy_taiga",
    "wintry_lowlands": "minecraft:snowy_plains",
    "yellowstone": "minecraft:plains",
    "yosemite_cliffs": "minecraft:windswept_forest",
    "yosemite_lowlands": "minecraft:old_growth_pine_taiga",
    # caves (terralith:cave/<name>)
    "cave/andesite_caves": "minecraft:dripstone_caves",
    "cave/deep_caves": "minecraft:dripstone_caves",
    "cave/diorite_caves": "minecraft:dripstone_caves",
    "cave/frostfire_caves": "minecraft:dripstone_caves",
    "cave/fungal_caves": "minecraft:lush_caves",
    "cave/granite_caves": "minecraft:dripstone_caves",
    "cave/infested_caves": "minecraft:dripstone_caves",
    "cave/mantle_caves": "minecraft:dripstone_caves",
    "cave/thermal_caves": "minecraft:dripstone_caves",
    "cave/tuff_caves": "minecraft:dripstone_caves",
    "cave/underground_jungle": "minecraft:lush_caves",
}

PACK = {
    "pack": {
        "description": {"translate": "kultivator.dunia.pack.kompat_bioma"},
        "min_format": 121,
        "max_format": 121,
    }
}


def tag_dirs():
    yield os.path.join(RES, TAGS)
    packs = os.path.join(RES, "resourcepacks")
    for name in sorted(os.listdir(packs)):
        if name != "kompat_bioma":
            yield os.path.join(packs, name, TAGS)


def entry_id(value):
    return value if isinstance(value, str) else value.get("id", "")


def main():
    by_kin = {}
    for biome, kin in KIN.items():
        by_kin.setdefault(kin, []).append("terralith:" + biome)
    found = {}
    for base in tag_dirs():
        if not os.path.isdir(base):
            continue
        for folder, _, files in os.walk(base):
            for name in files:
                if not name.endswith(".json"):
                    continue
                path = os.path.join(folder, name)
                rel = os.path.relpath(path, base).replace(os.sep, "/")
                with open(path, encoding="utf-8") as handle:
                    data = json.load(handle)
                extra = found.setdefault(rel, [])
                for value in data.get("values", []):
                    for biome in by_kin.get(entry_id(value), []):
                        if biome not in extra:
                            extra.append(biome)
    out_tags = os.path.join(OUT, TAGS)
    written = 0
    keep = set()
    for rel, extra in sorted(found.items()):
        if not extra:
            continue
        target = os.path.join(out_tags, rel.replace("/", os.sep))
        keep.add(os.path.normcase(target))
        os.makedirs(os.path.dirname(target), exist_ok=True)
        body = {"replace": False, "values": [{"id": b, "required": False} for b in sorted(extra)]}
        with open(target, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(body, handle, indent=2)
            handle.write("\n")
        written += 1
    # drop extension tags whose source tag no longer lists a vanilla kin
    if os.path.isdir(out_tags):
        for folder, _, files in os.walk(out_tags):
            for name in files:
                path = os.path.join(folder, name)
                if os.path.normcase(path) not in keep:
                    os.remove(path)
    with open(os.path.join(OUT, "pack.mcmeta"), "w", encoding="utf-8", newline="\n") as handle:
        json.dump(PACK, handle, indent=2)
        handle.write("\n")
    print("kompat_bioma: %d extension tags from %d biome tags" % (written, len(found)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
