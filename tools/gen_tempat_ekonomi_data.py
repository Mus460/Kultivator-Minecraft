"""tools/gen_tempat_ekonomi_data.py (38EK / D-EK): writes the three 38EK demand tables (data/kultivator/tempat/permintaan/{kota_kota,kota_kota_besar,pos_dagang}.json).

Only items with a town value count (Harga.value / SenjataKota: mod pills, talismans, herbs, cores, teas, rings, wares and
city weapons); vanilla items are worth 0 NB and never travel on a sect caravan, so they are not listed. A factor 0 means
the place does not buy it at all; KafilahSekte clamps the rest to 0.9-1.3 after the weekly +-0.1 drift.

Usage: python tools/gen_tempat_ekonomi_data.py <repo>
"""
import json
import sys
from collections import OrderedDict

K = "kultivator:"
BENTUK = ["jian", "dao", "qiang", "kapak", "gada", "kipas_besi", "kebut"]


def senjata(mutu, f, only=None):
    return {K + "senjata/" + b + "_" + mutu: f for b in (only or BENTUK)}


def tabel(catatan, default, rows):
    out = OrderedDict()
    out["_catatan"] = catatan
    out["*"] = default
    for k, v in rows:
        out[k] = v
    return out


kota = tabel(
    "Kota biasa (DESIGN 38.M.4.2): orang kota butuh pil penyembuh dan pil qi dasar, jimat murah, herba muda untuk tabib, "
    "inti binatang kecil untuk tungku pandai besi, teh harian dan senjata fana untuk penjaga gerbang. Pil tinggi, cincin "
    "mahal dan pusaka hampir tidak ada pembelinya; panji iblis dan token darah ditolak (kota lurus).",
    1.0,
    [
        (K + "healing_pill", 1.25), (K + "qi_gathering_pill", 1.2), (K + "qi_recovery_pill", 1.2),
        (K + "toxin_purging_pill", 1.15), (K + "bone_tempering_pill", 1.1),
        (K + "foundation_pill", 1.0), (K + "marrow_cleansing_pill", 0.95), (K + "golden_core_pill", 0.9),
        (K + "enlightenment_pill", 0.9), (K + "soul_nurturing_pill", 0.9), (K + "dao_insight_pill", 0.9),
        (K + "blank_talisman", 1.2), (K + "shield_talisman", 1.2), (K + "fire_cloud_talisman", 1.15),
        (K + "thunder_talisman", 1.1), (K + "binding_talisman", 1.1), (K + "return_talisman", 1.05),
        (K + "spirit_grass", 1.25), (K + "foundation_herb", 1.15), (K + "yellow_essence_mushroom", 1.15),
        (K + "ninecurl_ginseng", 1.1), (K + "ginseng_fruit", 0.95),
        (K + "beast_core_low", 1.2), (K + "beast_core_mid", 1.1), (K + "beast_core_high", 0.95), (K + "beast_core_king", 0.9),
        (K + "teh_embun_pagi", 1.25), (K + "teh_awan_ungu", 1.15), (K + "teh_penenang_hati", 1.1),
        (K + "storage_pouch", 1.15), (K + "storage_ring_low", 1.05), (K + "storage_ring_mid", 0.9), (K + "storage_ring_high", 0.9),
        (K + "jade_slip", 1.05), (K + "catatan_pengembara", 1.1), (K + "forged_artifact", 0.95),
        (K + "demonic_banner", 0.0), (K + "token_darah_api", 0.0), (K + "demonic_loot_pouch", 0.0), (K + "token_pasar_gelap", 0.0),
    ] + list(senjata("fana", 1.2).items()) + list(senjata("roh_rendah", 1.05).items())
      + list(senjata("roh_menengah", 0.95).items()) + list(senjata("roh_tinggi", 0.9).items()),
)

kota_besar = tabel(
    "Ibu kota (DESIGN 38.M.4.2): istana, gilda dan rumah lelang membeli barang mewah - pil Inti Emas ke atas, cincin "
    "penyimpanan, pusaka tempa, giok teknik, peta Kehampaan Langit dan senjata roh tinggi. Pil dan herba murahan berlimpah "
    "dari kampung sekitar, jadi harganya turun. Barang iblis ditolak di gerbang.",
    1.05,
    [
        (K + "healing_pill", 0.9), (K + "qi_gathering_pill", 0.9), (K + "qi_recovery_pill", 0.9), (K + "toxin_purging_pill", 0.95),
        (K + "bone_tempering_pill", 1.0), (K + "foundation_pill", 1.1), (K + "marrow_cleansing_pill", 1.15),
        (K + "mata_terang_pill", 1.15), (K + "heart_calming_pill", 1.2), (K + "golden_core_pill", 1.3),
        (K + "enlightenment_pill", 1.25), (K + "soul_nurturing_pill", 1.3), (K + "dao_insight_pill", 1.25),
        (K + "tribulation_warding_pill", 1.3), (K + "spirit_beast_pill", 1.15),
        (K + "blank_talisman", 0.9), (K + "thunder_talisman", 0.95), (K + "limpidity_talisman", 1.15),
        (K + "thousand_mile_talisman", 1.1), (K + "tribulation_ward_talisman", 1.25),
        (K + "spirit_grass", 0.9), (K + "foundation_herb", 0.95), (K + "eight_angled_ice_grass", 1.15),
        (K + "purple_flame_flower", 1.15), (K + "illusory_spirit_herb", 1.2), (K + "gold_lightning_bamboo", 1.2),
        (K + "ginseng_fruit", 1.3),
        (K + "beast_core_low", 0.9), (K + "beast_core_mid", 1.0), (K + "beast_core_high", 1.2), (K + "beast_core_king", 1.3),
        (K + "teh_embun_pagi", 0.9), (K + "teh_awan_ungu", 1.1), (K + "teh_penenang_hati", 1.2),
        (K + "storage_ring_low", 1.1), (K + "storage_ring_mid", 1.2), (K + "storage_ring_high", 1.3),
        (K + "forged_artifact", 1.25), (K + "jade_slip", 1.2), (K + "lost_scripture_page", 1.25),
        (K + "heavenvoid_fragment", 1.15), (K + "heavenvoid_map", 1.25), (K + "vein_compass", 1.2),
        (K + "inheritance_crystal", 1.25), (K + "soul_nurturing_wood", 1.2), (K + "soul_seal_jade", 1.2),
        (K + "cangkang_kura_langit", 1.2), (K + "selendang_pengikat_naga", 1.15),
        (K + "demonic_banner", 0.0), (K + "token_darah_api", 0.0), (K + "demonic_loot_pouch", 0.0), (K + "token_pasar_gelap", 0.0),
        (K + "kantong_saingan", 0.95),
    ] + list(senjata("fana", 0.9).items()) + list(senjata("roh_rendah", 0.95).items())
      + list(senjata("roh_menengah", 1.1).items()) + list(senjata("roh_tinggi", 1.25).items()),
)

pos = tabel(
    "Pos dagang / karavanserai (DESIGN 38.M.4.2): para pedagang membeli apa saja yang laku di wilayah lain - bekal "
    "perjalanan (pil penyembuh, jimat seribu li dan jimat pulang, kantong penyimpanan), inti binatang dan herba kering "
    "yang tahan dibawa jauh, dan senjata yang dijual lagi ke pengawal kafilah. Kantong rampasan iblis dibeli diam-diam, "
    "panji iblis tidak.",
    1.1,
    [
        (K + "healing_pill", 1.25), (K + "qi_recovery_pill", 1.2), (K + "toxin_purging_pill", 1.2), (K + "spirit_beast_pill", 1.15),
        (K + "thousand_mile_talisman", 1.3), (K + "return_talisman", 1.25), (K + "shield_talisman", 1.2),
        (K + "binding_talisman", 1.15), (K + "fire_cloud_talisman", 1.15),
        (K + "storage_pouch", 1.3), (K + "storage_ring_low", 1.25), (K + "storage_ring_mid", 1.15),
        (K + "beast_core_low", 1.15), (K + "beast_core_mid", 1.2), (K + "beast_core_high", 1.2), (K + "beast_core_king", 1.15),
        (K + "spirit_grass", 1.0), (K + "yellow_essence_mushroom", 1.2), (K + "eight_angled_ice_grass", 1.2),
        (K + "purple_flame_flower", 1.2), (K + "ninecurl_ginseng", 1.15),
        (K + "teh_awan_ungu", 1.2), (K + "teh_penenang_hati", 1.15),
        (K + "catatan_pengembara", 1.25), (K + "vein_compass", 1.25), (K + "heavenvoid_map", 1.15),
        (K + "loot_pouch", 1.15), (K + "demonic_loot_pouch", 1.0), (K + "demonic_banner", 0.0), (K + "token_darah_api", 0.0),
        (K + "golden_core_pill", 1.0), (K + "soul_nurturing_pill", 0.95), (K + "forged_artifact", 1.05),
    ] + list(senjata("fana", 1.2).items()) + list(senjata("roh_rendah", 1.2).items())
      + list(senjata("roh_menengah", 1.1).items()) + list(senjata("roh_tinggi", 1.0).items())
      + [(K + "senjata/qiang_" + m, 1.3) for m in ("fana", "roh_rendah")],
)


def main():
    base = sys.argv[1] + "/src/main/resources/data/kultivator/tempat/permintaan/"
    for name, data in (("kota_kota", kota), ("kota_kota_besar", kota_besar), ("pos_dagang", pos)):
        text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
        with open(base + name + ".json", "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        print(name, len(data) - 1)


main()
