"""Shared data of the themed cities (DESIGN 38.T.2.3, 38.T.10, 38.T.11; owner 38TK inti). Run AFTER the theme
generators (gen_tempat_tema_{pelabuhan,oasis,air,gunung,iblis}.py) and gen_tempat_tema_satelit.py: a structure set is
written only for a structure JSON that exists (missing ones are listed), so the registry never points at nothing.

Writes:
  resourcepacks/dunia_kotatema/pack.mcmeta + data/kultivator/worldgen/structure_set/kota_tema/<tema>/<distrik>.json
      (kultivator:rencana_tempat, rencana kota_tema_<tema>, salt from Tema.java) and the satellites
      kota_tema/reruntuhan_gurun (kultivator:cincin_tempat, 380451) and kota_tema/pos_pemburu (rencana, 380452)
  tags/worldgen/biome/kota_tema/{pelabuhan/darat,pelabuhan/laut,oasis,air,gunung,gunung/puncak,iblis}.json (PutusanTema)
  tags/worldgen/biome/kota_tema/gaya/<tema>_<gaya>.json (style tags, tempat_tema_lib.GAYA_TEMA)
  tags/worldgen/biome/has_structure/kota_tema/{<tema>,reruntuhan_gurun}.json
  tags/worldgen/structure/kota_tema.json (+ kota_tema/<tema>.json)
  worldgen/processor_list/kota_tema/{lapuk_ringan,utuh,iblis_lapuk}.json (shared processors)
  advancement/tempat/tema_*.json (the 22 inti advancements; lang in lang-src/tempat_tema.*)
  data/kultivator/tempat/dagang/{barang,jalur}.json (the trade table of DagangItems and the six model routes)

    python tools/gen_tempat_tema_data.py
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import structgen  # noqa: E402
import tempat_lib as tl  # noqa: E402
import tempat_tema_lib as ttl  # noqa: E402

NS = "kultivator"
PACK = "dunia_kotatema"
DATA = structgen.DATA
TAGS = os.path.join(DATA, "tags", "worldgen")

# PutusanTema biome tags (design 38.T.2.2 table)
BIOMA = {
    "pelabuhan/darat": ["beach", "snowy_beach", "plains", "sunflower_plains", "forest", "birch_forest", "dappled_forest", "savanna",
                        "sparse_jungle", "meadow", "cherry_grove", "mangrove_swamp", "stony_shore"],
    "pelabuhan/laut": ["ocean", "lukewarm_ocean", "warm_ocean", "cold_ocean", "deep_ocean", "deep_lukewarm_ocean", "deep_cold_ocean",
                       "beach", "river", "stony_shore"],
    "oasis": ["desert", "badlands", "eroded_badlands", "wooded_badlands"],
    "air": ["plains", "sunflower_plains", "forest", "birch_forest", "flower_forest", "dappled_forest", "swamp", "river", "cherry_grove"],
    "gunung": ["windswept_hills", "windswept_forest", "windswept_gravelly_hills", "meadow", "grove", "snowy_slopes", "taiga",
               "old_growth_pine_taiga"],
    "gunung/puncak": ["stony_peaks", "jagged_peaks", "frozen_peaks", "snowy_slopes", "grove"],
    "iblis": ["dark_forest", "pale_garden", "badlands", "eroded_badlands", "wooded_badlands", "swamp", "mangrove_swamp",
              "windswept_gravelly_hills", "old_growth_spruce_taiga"],
}
# biomes that are new in recent versions: written as optional entries so an older biome list never breaks the tag
OPSIONAL = {"dappled_forest", "pale_garden"}
RERUNTUHAN_BIOMA = ["desert", "badlands", "eroded_badlands", "wooded_badlands"]
RING_RERUNTUHAN = [(62, 0.08), (187, 0.20), (500, 0.25), (9999, 0.30)]


def entri(b):
    return {"id": "minecraft:" + b, "required": False} if b in OPSIONAL else "minecraft:" + b


def tag_bioma(path, biomes):
    structgen.write_json(os.path.join(TAGS, "biome", path + ".json"), {"replace": False, "values": [entri(b) for b in biomes]})


def struktur_ada(sid):
    return os.path.exists(os.path.join(DATA, "worldgen", "structure", sid + ".json"))


def tema_distrik():
    out, sat = ttl._baca_tema()
    return out, sat


def sets_and_tags():
    districts, sat = tema_distrik()
    hilang = []
    semua = []
    per_tema = {}
    for tema, rows in districts.items():
        for d in rows:
            sid = "kota_tema/%s/%s" % (tema, d["id"])
            if not struktur_ada(sid):
                hilang.append(sid)
                continue
            obj = tl.set_rencana(NS + ":" + sid, "kota_tema_" + tema, d["id"], d["salt"])
            tl.tulis_wg("structure_set", sid, obj, pack=PACK)
            semua.append(NS + ":" + sid)
            per_tema.setdefault(tema, []).append(NS + ":" + sid)
    if struktur_ada("kota_tema/reruntuhan_gurun"):
        obj = tl.set_cincin([(NS + ":kota_tema/reruntuhan_gurun", 1)], 40, 14, sat["SALT_RERUNTUHAN_GURUN"], min_chunks=12,
                            cincin=RING_RERUNTUHAN, jarak_tempat=6)
        tl.tulis_wg("structure_set", "kota_tema/reruntuhan_gurun", obj, pack=PACK)
        semua.append(NS + ":kota_tema/reruntuhan_gurun")
    else:
        hilang.append("kota_tema/reruntuhan_gurun")
    if struktur_ada("kota_tema/pos_pemburu"):
        obj = tl.set_rencana(NS + ":kota_tema/pos_pemburu", "kota_tema_pos_pemburu", "pos", sat["SALT_POS_PEMBURU"])
        tl.tulis_wg("structure_set", "kota_tema/pos_pemburu", obj, pack=PACK)
        semua.append(NS + ":kota_tema/pos_pemburu")
    else:
        hilang.append("kota_tema/pos_pemburu")
    st = os.path.join(TAGS, "structure")
    for tema, ids in per_tema.items():
        structgen.write_json(os.path.join(st, "kota_tema", tema + ".json"), {"replace": False, "values": ids})
    structgen.write_json(os.path.join(st, "kota_tema.json"), {"replace": False, "values": semua})
    # biome tags
    for path, biomes in BIOMA.items():
        tag_bioma("kota_tema/" + path, biomes)
    for tema, styles in ttl.GAYA_TEMA.items():
        for g, s in styles.items():
            if s["biomes"] is not None:
                tag_bioma("kota_tema/gaya/%s_%s" % (tema, g), s["biomes"])
        # the plan decides where a town stands (PutusanTema); the structure itself accepts any overworld biome so a
        # district whose own centre lies in a neighbouring biome is never dropped (never half a town)
        structgen.write_json(os.path.join(TAGS, "biome", "has_structure", "kota_tema", tema + ".json"),
                             {"replace": False, "values": ["#minecraft:is_overworld"]})
    tag_bioma("has_structure/kota_tema/reruntuhan_gurun", RERUNTUHAN_BIOMA)
    structgen.write_json(os.path.join(structgen.REPO, "src", "main", "resources", "resourcepacks", PACK, "pack.mcmeta"),
                         {"pack": {"description": {"translate": "kultivator.dunia.pack.kotatema"}, "min_format": 121, "max_format": 121}})
    return semua, hilang


def processors():
    """kota_tema/lapuk_ringan (the light weathering of 38.B.2.5 for any theme), kota_tema/utuh (temples, palaces,
    lighthouses: none), kota_tema/iblis_lapuk (cracks 8 %, dried blood 2 % on the outer floors, never fire)."""
    def rule(src, dst, p):
        return {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": src, "probability": p},
                "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": dst}}
    lindungi = {"processor_type": "minecraft:protected_blocks", "value": "#kultivator:sect_protected"}
    ringan = [rule(a, b, p) for a, b, p in tl.LAPUK_RINGAN] if hasattr(tl, "LAPUK_RINGAN") else []
    tl.tulis_processor("kota_tema/lapuk_ringan", {"processors": ([{"processor_type": "minecraft:rule", "rules": ringan}] if ringan else []) + [lindungi]})
    tl.tulis_processor("kota_tema/utuh", {"processors": [lindungi]})
    iblis = [rule("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.08),
             rule("minecraft:nether_bricks", "minecraft:cracked_nether_bricks", 0.08),
             rule("minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", 0.08),
             rule("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.08),
             rule("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.08)]
    blood = {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": "minecraft:polished_blackstone", "probability": 0.02},
             "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"id": "minecraft:red_concrete_powder"}}
    tl.tulis_processor("kota_tema/iblis_lapuk", {"processors": [{"processor_type": "minecraft:rule", "rules": iblis + [blood]}, lindungi]})


# (name, parent, icon, frame, hidden)
ADV = [
    ("tema_pelabuhan", "dunia/kota_found", "minecraft:heart_of_the_sea", "task", False),
    ("tema_oasis", "dunia/kota_found", "minecraft:sand", "task", False),
    ("tema_air", "dunia/kota_found", "minecraft:lily_pad", "task", False),
    ("tema_gunung", "dunia/kota_found", "minecraft:stone_bricks", "task", False),
    ("tema_iblis", "dunia/kota_found", "minecraft:wither_skeleton_skull", "goal", False),
    ("tema_lima_kota", "tempat/tema_pelabuhan", "minecraft:filled_map", "challenge", False),
    ("tema_dagang", "dunia/kota_found", "minecraft:emerald", "task", False),
    ("tema_saudagar", "tempat/tema_dagang", "minecraft:emerald_block", "goal", False),
    ("tema_lintas_laut", "tempat/tema_pelabuhan", "minecraft:oak_boat", "goal", False),
    ("tema_ziarah", "tempat/tema_gunung", "minecraft:candle", "goal", False),
    ("tema_lentera_air", "tempat/tema_air", "minecraft:lantern", "task", False),
    ("tema_tebus", "tempat/tema_iblis", "minecraft:iron_chain", "goal", False),
    ("tema_pembebas", "tempat/tema_tebus", "minecraft:shears", "challenge", False),
    ("tema_penjaga_pelita", "tempat/tema_pelabuhan", "minecraft:sea_lantern", "challenge", False),
    ("tema_pembawa_air", "tempat/tema_oasis", "minecraft:water_bucket", "challenge", False),
    ("tema_penenun_malam", "tempat/tema_air", "minecraft:red_wool", "challenge", False),
    ("tema_penjaga_tali", "tempat/tema_gunung", "minecraft:lead", "challenge", False),
    ("tema_peziarah", "tempat/tema_ziarah", "minecraft:gold_block", "goal", False),
    ("tema_juara_tangga_darah", "tempat/tema_iblis", "minecraft:netherite_sword", "challenge", False),
    ("tema_pemutus_rantai", "tempat/tema_iblis", "minecraft:tripwire_hook", "challenge", False),
    ("tema_pembawa_fajar", "tempat/tema_iblis", "minecraft:sunflower", "challenge", False),
    ("tema_tamu_darah", "tempat/tema_iblis", "minecraft:red_dye", "task", True),
]


def advancements():
    folder = os.path.join(DATA, "advancement", "tempat")
    for name, parent, icon, frame, hidden in ADV:
        disp = {"description": {"translate": "advancement.kultivator.tempat.%s.description" % name}, "icon": {"id": icon},
                "title": {"translate": "advancement.kultivator.tempat.%s.title" % name}, "frame": frame}
        if hidden:
            disp["hidden"] = True
        structgen.write_json(os.path.join(folder, name + ".json"), {
            "parent": NS + ":" + parent, "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "display": disp, "requirements": [["done"]]})


def dagang_data():
    """The trade table as data (read by tools and by anyone who wants the numbers without code): DagangItems.java is
    parsed so the file can never drift from the registered items."""
    src = open(os.path.join(structgen.REPO, "src", "main", "java", "dev", "kultivator", "dunia", "tempat", "tema", "bersama",
                            "DagangItems.java"), encoding="utf-8").read()
    rows = []
    pat = re.compile(r'barang\("([a-z_]+)",\s*Tema\.([A-Z]+),\s*(\d+),.*?,\s*(true|false),\s*"([a-z]*)",\s*([-0-9.,\s]+)\);', re.S)
    for m in pat.finditer(src):
        f = [float(v) for v in m.group(6).replace("\n", " ").split(",") if v.strip()]
        rows.append({"id": NS + ":dagang/" + m.group(1), "asal": m.group(2).lower(), "dasar": int(m.group(3)),
                     "selundupan": m.group(4) == "true", "catatan": m.group(5),
                     "permintaan": dict(zip(("pelabuhan", "oasis", "air", "gunung", "ibu_kota", "iblis"), f))})
    if len(rows) != 17:
        raise SystemExit("DagangItems.java parse: %d goods, expected 17" % len(rows))
    out = os.path.join(DATA, "tempat", "dagang")
    structgen.write_json(os.path.join(out, "barang.json"), {
        "_": "DESIGN 38.T.10.1; generated from DagangItems.java by tools/gen_tempat_tema_data.py (do not edit)",
        "kurs": {"rendah": 1, "menengah": 9, "tinggi": 81, "agung": 729}, "stok_harian": 16, "jenuh_min": 0.6,
        "batas_laba_mingguan": "150 x (band + 1)", "barang": rows})
    jalur = [("air", "oasis", "sutra_awan"), ("pelabuhan", "oasis", "porselen_hijau"), ("oasis", "air", "giok_barat"),
             ("gunung", "ibu_kota", "lingzhi_gunung"), ("gunung", "pelabuhan", "dupa_gunung"), ("pelabuhan", "gunung", "rempah_seberang")]
    by = {r["id"].split("/")[-1]: r for r in rows}
    routes = []
    for a, b, good in jalur:
        r = by[good]
        sell = round(r["dasar"] * r["permintaan"][b], 2)
        routes.append({"dari": a, "ke": b, "barang": r["id"], "beli": r["dasar"], "jual_ideal": sell, "laba_ideal": round(sell - r["dasar"], 2)})
    structgen.write_json(os.path.join(out, "jalur.json"), {"_": "DESIGN 38.T.10.1 model routes (ideal unit profit before saturation)",
                                                           "jalur": routes})
    return rows, routes


def main():
    semua, hilang = sets_and_tags()
    processors()
    advancements()
    rows, routes = dagang_data()
    print("kota_tema data: %d structure sets, %d advancements, %d trade goods, %d routes" % (len(semua), len(ADV), len(rows), len(routes)))
    if hilang:
        print("waiting for theme structures (%d): %s" % (len(hilang), ", ".join(hilang)))


if __name__ == "__main__":
    main()
