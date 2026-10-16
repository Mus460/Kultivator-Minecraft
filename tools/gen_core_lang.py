"""Writes lang-src/core.en_us.json and lang-src/core.id_id.json (the spine's texts)."""
import json
import os

en = {}
id_ = {}


def t(k, e, i):
    en[k] = e
    id_[k] = i


realms = [("mortal", "Mortal", "Manusia Fana"), ("qi_refining", "Qi Refining", "Pemurnian Qi"),
          ("foundation", "Foundation Establishment", "Pembangunan Fondasi"), ("golden_core", "Golden Core", "Inti Emas"),
          ("nascent_soul", "Nascent Soul", "Jiwa Baru Lahir"), ("deity_transformation", "Deity Transformation", "Transformasi Dewa"),
          ("dao_union", "Dao Union", "Penyatuan Dao"), ("immortal_ascension", "Immortal Ascension", "Kenaikan Abadi"),
          ("true_immortal", "True Immortal", "Dewa Abadi")]
for k, e, i in realms:
    t("kultivator.realm." + k, e, i)
for n, (e, i) in enumerate([("Early", "Awal"), ("Middle", "Menengah"), ("Late", "Akhir"), ("Peak", "Puncak")]):
    t(f"kultivator.stage.{n}", e, i)
t("kultivator.rank.simple", "%s, %s", "%s, Tahap %s")
t("kultivator.rank.layered", "%s %s, %s", "%s Tingkat %s, Tahap %s")
for k, e, i in [("metal", "Metal", "Logam"), ("wood", "Wood", "Kayu"), ("water", "Water", "Air"), ("fire", "Fire", "Api"),
                ("earth", "Earth", "Tanah"), ("lightning", "Lightning", "Petir"), ("ice", "Ice", "Es"), ("wind", "Wind", "Angin"),
                ("space", "Space", "Ruang")]:
    t("kultivator.element." + k, e, i)
for k, e, i in [("trash", "Trash", "Sampah"), ("low", "Low", "Rendah"), ("mid", "Middle", "Menengah"), ("high", "High", "Tinggi"),
                ("heavenly", "Heavenly", "Surgawi")]:
    t("kultivator.root_grade." + k, e, i)
for k, e, i in [("sword", "Sword Dao", "Dao Pedang"), ("fire", "Fire Dao", "Dao Api"), ("ice", "Ice Dao", "Dao Es"),
                ("lightning", "Lightning Dao", "Dao Petir"), ("body", "Body Dao", "Dao Tubuh"), ("life", "Life Dao", "Dao Kehidupan"),
                ("shadow", "Shadow & Wind Dao", "Dao Bayangan & Angin"), ("space", "Space Dao", "Dao Ruang")]:
    t("kultivator.dao." + k, e, i)
for n, (e, i) in enumerate([("Novice", "Pemula"), ("Minor Comprehension", "Pemahaman Kecil"), ("Major Comprehension", "Pemahaman Besar"),
                            ("Perfect Comprehension", "Pemahaman Sempurna"), ("Dao Domain", "Wilayah Dao")]):
    t(f"kultivator.comprehension.{n}", e, i)
for k, e, i in [("cracked", "Cracked", "Retak"), ("normal", "Normal", "Biasa"), ("perfect", "Perfect", "Sempurna"),
                ("heavenly", "Heavenly", "Surgawi")]:
    t("kultivator.quality." + k, e, i)

tech = [
    ("cloud_step", "Cloud Step", "Langkah Awan", "Jump again in mid-air on clouds of qi; no fall damage right after.",
     "Melompat lagi di udara di atas awan qi; tanpa damage jatuh sesaat setelahnya."),
    ("qi_palm", "Qi Palm", "Telapak Qi", "A blast of qi rushes forward and strikes monsters.",
     "Hentakan qi melesat ke depan dan menghantam monster."),
    ("flying_sword", "Flying Sword", "Pedang Terbang", "Stand on your sword and fly. Drains qi every second.",
     "Berdiri di atas pedang dan terbang. Menguras qi tiap detik."),
    ("divine_sense", "Divine Sense", "Indra Ilahi", "Spread the sense of your soul: monsters glow and nearby ores reveal themselves.",
     "Sebarkan indra jiwa: monster bercahaya dan bijih di dekatmu terlihat."),
    ("buddha_palm", "Buddha Palm", "Telapak Buddha", "A giant golden palm falls from the sky onto your foes.",
     "Telapak emas raksasa jatuh dari langit menimpa musuh."),
    ("dharma_form", "Dharma Form", "Wujud Dharma", "Become a towering Dharma avatar: bigger, stronger, tougher.",
     "Menjelma menjadi wujud Dharma raksasa: lebih besar, kuat, dan tangguh."),
    ("dao_domain", "Dao Domain", "Wilayah Dao", "Unfold your Dao around you; monsters inside suffer its law.",
     "Bentangkan Dao di sekitarmu; monster di dalamnya tunduk pada hukumnya."),
    ("sword_qi", "Sword Qi Slash", "Tebasan Qi Pedang", "A crescent of sword qi cuts through every monster in a line.",
     "Bulan sabit qi pedang menebas semua monster dalam satu garis."),
    ("thousand_swords", "Thousand Swords", "Seribu Pedang", "Swords of light rain on every monster around you.",
     "Hujan pedang cahaya menimpa semua monster di sekitarmu."),
    ("flame_palm", "Flame Palm", "Telapak Api", "A cone of spirit fire burns the monsters in front of you.",
     "Kerucut api roh membakar monster di depanmu."),
    ("phoenix_flame", "Phoenix Flame", "Api Phoenix", "A phoenix of fire soars forward, burning all monsters in its path.",
     "Phoenix api terbang ke depan, membakar semua monster yang dilewatinya."),
    ("frost_lotus", "Frost Lotus", "Lotus Es", "A lotus of frost blooms and freezes monsters around you.",
     "Lotus es mekar dan membekukan monster di sekitarmu."),
    ("glacier_prison", "Glacier Prison", "Penjara Gletser", "Seal your target and the monsters near it in ice.",
     "Segel target dan monster di dekatnya di dalam es."),
    ("thunder_palm", "Thunder Palm", "Telapak Petir", "Lightning strikes your target and jumps to more monsters.",
     "Petir menyambar target lalu melompat ke monster lain."),
    ("heavenly_thunder", "Heavenly Thunder", "Petir Surgawi", "Call down heavenly thunder on the monsters around you.",
     "Panggil petir surgawi ke monster di sekitarmu."),
    ("iron_body", "Iron Body", "Tubuh Besi", "Your body hardens like iron and throws back blows.",
     "Tubuhmu mengeras seperti besi dan memantulkan pukulan."),
    ("mountain_stomp", "Mountain Stomp", "Hentakan Gunung", "Stomp like a falling mountain and launch monsters into the air.",
     "Menghentak seperti gunung runtuh dan melempar monster ke udara."),
    ("spring_of_life", "Spring of Life", "Mata Air Kehidupan", "Heal yourself, your friends and your pets.",
     "Pulihkan dirimu, temanmu, dan hewan peliharaanmu."),
    ("life_drain", "Life Drain", "Serap Kehidupan", "Drain the life of nearby monsters to heal yourself.",
     "Serap nyawa monster di dekatmu untuk memulihkan diri."),
    ("shadow_step", "Shadow Step", "Langkah Bayangan", "Step through the shadows to behind the monster you look at.",
     "Melangkah lewat bayangan ke belakang monster yang kamu lihat."),
    ("gale", "Gale", "Badai Angin", "A storm wind hurls monsters away and hides you.",
     "Angin badai melempar monster menjauh dan menyembunyikanmu."),
    ("space_blink", "Space Blink", "Kedipan Ruang", "Fold space and appear where you look.",
     "Lipat ruang dan muncul di tempat yang kamu lihat."),
    ("space_prison", "Space Prison", "Penjara Ruang", "Lock the space around monsters, holding them in place.",
     "Kunci ruang di sekitar monster dan tahan mereka di tempat."),
]
for k, e, i, de, di in tech:
    t("kultivator.technique." + k, e, i)
    t("kultivator.technique." + k + ".desc", de, di)

t("kultivator.core.prefix", "[Cultivation]", "[Kultivasi]")
t("kultivator.core.hardcore_on", "Hardcore breakthroughs ON: a failed breakthrough drops you one realm.",
  "Terobosan Hardcore AKTIF: gagal terobosan menurunkanmu satu ranah.")
t("kultivator.core.hardcore_off", "Hardcore breakthroughs OFF: a failed breakthrough only causes a Qi Deviation.",
  "Terobosan Hardcore MATI: gagal terobosan hanya menyebabkan Penyimpangan Qi.")
t("kultivator.core.root_already", "Your spiritual root is already known: %s.", "Akar spiritualmu sudah diketahui: %s.")
t("kultivator.core.root_result", "The test is done. You have a %s.", "Uji selesai. Kamu memiliki %s.")
t("kultivator.core.root_description", "%s root of %s grade", "akar %s bermutu %s")
for k, e, i in [("header", "Cultivation of %s", "Kultivasi %s"), ("rank", "Realm:", "Ranah:"), ("xp", "Cultivation:", "Kultivasi:"),
                ("stored", "Stored Qi:", "Qi Tertimbun:"), ("qi", "Qi:", "Qi:"), ("root", "Spiritual root:", "Akar spiritual:"),
                ("root_unknown", "not tested yet", "belum diuji"), ("dao", "Dao:", "Dao:"), ("dao_none", "none chosen", "belum dipilih"),
                ("dao_value", "%s (%s%%, %s)", "%s (%s%%, %s)"), ("deviation", "Qi Deviation:", "Penyimpangan Qi:"),
                ("hardcore", "Hardcore breakthroughs", "Terobosan Hardcore")]:
    t("kultivator.core.info." + k, e, i)
t("kultivator.core.visual_on", "%s is now shown.", "%s sekarang ditampilkan.")
t("kultivator.core.visual_off", "%s is now hidden.", "%s sekarang disembunyikan.")
for k, e, i in [("aura", "Aura", "Aura"), ("robe", "Robe", "Jubah"), ("back_sword", "Sword on the back", "Pedang di punggung"),
                ("eyes", "Glowing eyes", "Mata bercahaya"), ("dao_circle", "Dao circle", "Lingkaran Dao")]:
    t("kultivator.core.visual." + k, e, i)
t("kultivator.core.debug.done", "Done.", "Selesai.")
t("kultivator.core.debug.unknown", "Unknown value: %s", "Nilai tidak dikenal: %s")
t("kultivator.core.debug.no_dao", "Choose a Dao first.", "Pilih Dao dulu.")
t("key.category.kultivator.main", "Kultivator", "Kultivator")
for k, e, i in [("cast", "Cast selected technique", "Gunakan teknik terpilih"), ("meditate", "Meditate", "Meditasi"),
                ("info", "Cultivation screen", "Layar kultivasi"), ("previous", "Previous technique", "Teknik sebelumnya"),
                ("next", "Next technique", "Teknik berikutnya"), ("flying_sword", "Flying sword", "Pedang terbang")]:
    t("key.kultivator." + k, e, i)
for k, e, i in [("minimal", "Minimal", "Minimal"), ("normal", "Normal", "Normal"), ("maximal", "Maximal", "Maksimal")]:
    t("kultivator.quality_setting." + k, e, i)
t("kultivator.screen.title", "Cultivation", "Kultivasi")
t("advancement.kultivator.root.title", "Path of Cultivation", "Jalan Kultivasi")
t("advancement.kultivator.root.description", "Every immortal was once a mortal.", "Setiap abadi pernah menjadi manusia fana.")

assert en.keys() == id_.keys()
base = os.path.join(os.path.dirname(__file__), "..", "lang-src")
os.makedirs(base, exist_ok=True)
with open(os.path.join(base, "core.en_us.json"), "w", encoding="utf-8") as f:
    json.dump(en, f, ensure_ascii=False, indent=2)
with open(os.path.join(base, "core.id_id.json"), "w", encoding="utf-8") as f:
    json.dump(id_, f, ensure_ascii=False, indent=2)
print(len(en), "keys")
