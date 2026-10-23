"""Module 37B (Sekte Langit "Bangun", DESIGN 37.3-37.4, 37.7.8) lang: run `python tools/gen_blueprint_awan_lang.py`.
Writes lang-src/blueprint_awan.{id_id,en_us}.json with identical keys (Indonesian primary): the sky templates A1-A9,
their components, the preview warnings, engine errors, the founding/Ascension flow, the offering, the warded air and the
plaques. Satellite names on A7-A9 follow the drifting mountains of Guixu (Liezi ch. 5 "Tang Wen")."""
import json
import os
from collections import OrderedDict

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lang-src")
E = OrderedDict()


def add(key, id_id, en_us):
    assert key not in E, key
    E[key] = (id_id, en_us)


# ---------------------------------------------------------------- templates (SectTemplates.SKY_IDS)
T = "kultivator.blueprint.template."
add(T + "awan_pertapa", "Batu Awan Pertapa", "Hermit's Cloud Rock")
add(T + "awan_kabut", "Pulau Kabut", "Isle of Mist")
add(T + "awan_bangau", "Pulau Bangau", "Crane Isle")
add(T + "awan_biru", "Gugus Awan Biru", "Azure Cloud Cluster")
add(T + "awan_puncak", "Puncak Melayang", "Floating Peak")
add(T + "awan_suci", "Tanah Suci Mengambang", "Drifting Holy Land")
add(T + "awan_gua_surga", "Gua Surga di Awan", "Cave Heaven in the Clouds")
add(T + "awan_kunlun", "Kunlun Melayang", "Floating Kunlun")
add(T + "awan_sembilan_langit", "Istana Sembilan Langit", "Palace of the Nine Heavens")

# ---------------------------------------------------------------- components
C = "kultivator.blueprint.comp."
add(C + "awan_inti", "Inti Pulau Awan", "Cloud Island Core")
add(C + "awan_tahta", "Tahta Prasasti", "Stele Throne")
add(C + "awan_gerbang", "Gerbang Awan", "Cloud Gate")
add(C + "awan_formasi", "Simpul Formasi Pindah Awan", "Cloud Transfer Node")
add(C + "awan_dataran", "Dataran Awan", "Cloud Plateau")
add(C + "awan_pelebaran", "Pelebaran Pulau", "Island Widening")
add(C + "awan_penuh", "Teras Awan Penuh", "Full Cloud Terraces")
add(C + "awan_pohon", "Pinus dan Taman Tepi", "Rim Pines and Gardens")
add(C + "awan_tangga", "Tangga Awan", "Cloud Stair")
add(C + "awan_pagar", "Pagar Tepi", "Rim Balustrade")
add(C + "awan_kolam", "Kolam Langit", "Sky Pond")
add(C + "awan_emitor", "Takik Air Terjun Kabut", "Mist Fall Notches")
add(C + "awan_akar", "Akar Gantung", "Hanging Roots")
add(C + "awan_jaring", "Formasi Jaring Awan", "Cloud Net Formation")
add(C + "awan_jembatan", "Jembatan Giok", "Jade Bridge")
add(C + "awan_taman", "Taman Awan", "Cloud Garden")
add(C + "awan_panggung_pedang", "Panggung Pedang", "Sword Terrace")
add(C + "awan_dermaga", "Dermaga Awan", "Cloud Docks")
add(C + "awan_halte_feri", "Halte Feri Awan", "Cloud Ferry Stop")
add(C + "awan_meditasi", "Batu Meditasi Tepi", "Rim Meditation Stones")
add(C + "awan_kristal", "Kristal Gantung", "Hanging Crystals")
add(C + "awan_gerbang_langit", "Gerbang Sembilan Langit", "Gate of the Nine Heavens")
add(C + "qi_seat", "Kursi Qi Tepi Awan", "Cloud Rim Qi Seats")
add(C + "awan_satelit_kebun", "Pulau Taman Gantung", "Hanging Garden Isle")
add(C + "awan_satelit_alkimia", "Pulau Tungku", "Furnace Isle")
add(C + "awan_satelit_leluhur", "Pulau Leluhur", "Ancestral Isle")
add(C + "awan_satelit_arena", "Pulau Arena", "Arena Isle")
add(C + "awan_satelit_asrama", "Pulau Asrama", "Dormitory Isle")
add(C + "awan_satelit_tambang", "Pulau Tambang", "Mine Isle")

# ---------------------------------------------------------------- plaques (AwanPapan)
P = "kultivator.blueprint.awan."
add(P + "papan.gerbang", "Gerbang Langit", "Heaven's Gate")
add(P + "papan.tangga", "Tangga Awan", "Cloud Stair")
for fid, id_name, en_name, id_sub, en_sub in [
        ("kebun", "Taman Gantung", "Hanging Garden", "kebun obat", "herb garden"),
        ("alkimia", "Pulau Tungku", "Furnace Isle", "balai alkimia", "alchemy hall"),
        ("leluhur", "Pulau Leluhur", "Ancestral Isle", "makam & altar", "tombs & altar"),
        ("arena", "Pulau Arena", "Arena Isle", "gelanggang", "proving ring"),
        ("asrama", "Pulau Asrama", "Dormitory Isle", "asrama murid", "disciple dorms"),
        ("tambang", "Pulau Tambang", "Mine Isle", "tambang roh", "spirit mine")]:
    add(P + "satelit." + fid, id_name, en_name)
    add(P + "satelit_sub." + fid, id_sub, en_sub)
for fid, id_name, en_name in [
        ("kebun", "Penglai", "Penglai"),
        ("alkimia", "Fanghu", "Fanghu"),
        ("leluhur", "Yingzhou", "Yingzhou"),
        ("arena", "Daiyu", "Daiyu"),
        ("asrama", "Yuanjiao", "Yuanjiao"),
        ("tambang", "Jurang Guixu", "Guixu Abyss")]:
    add(P + "guixu." + fid, id_name, en_name)

# ---------------------------------------------------------------- preview warnings (Validator, DESIGN 37.4.12)
W = "kultivator.blueprint.warn."
add(W + "langit_sempit", "Pita ketinggian kosong: plaza harus di antara %s dan %s", "No height band left: the plaza must lie between %s and %s")
add(W + "langit_rendah", "Plaza y %s terlalu rendah (minimal %s): naikkan dengan [Naik 4]", "Plaza y %s is too low (at least %s): raise it with [Up 4]")
add(W + "langit_tinggi", "Plaza y %s terlalu tinggi (maksimal %s): turunkan", "Plaza y %s is too high (at most %s): lower it")
add(W + "langit_terhalang", "%s blok dunia di ruang pulau: pilih ketinggian atau geser lain", "%s world blocks inside the island space: pick another height or shift")
add(W + "langit_seksi", "Dasar pulau y %s menyentuh seksi tapak darat; butuh y %s ke atas", "The island floor y %s touches the ground site's sections; needs y %s or higher")
add(W + "langit_kaki_relief", "Tanah kaki tangga tidak rata (%s blok)", "The stair foot's ground is uneven (%s blocks)")
add(W + "langit_kaki_air", "Kaki tangga %s%% di air", "The stair foot is %s%% water")
add(W + "langit_kaki_lava", "Ada lava di kaki tangga", "Lava at the stair foot")
add(W + "langit_kaki_rendah", "Kaki tangga terlalu dekat dasar dunia", "The stair foot is too close to the world's floor")
add(W + "langit_kaki_tapak", "Kaki tangga menyentuh chunk sekte lain atau tapak darat", "The stair foot touches another sect's chunks or the ground site")
add(W + "langit_tangga", "Tangga Awan turun %s blok ke tanah", "The Cloud Stair descends %s blocks to the ground")
add(W + "langit_jauh", "Tahta digeser %s blok dari prasasti (maksimal %s)", "The throne is shifted %s blocks from the stele (at most %s)")
add(W + "langit_udara", "Ruang udara pulau dilindungi sejak konfirmasi", "The island's airspace is warded from confirmation on")
add(W + "langit_bayangan", "Tanah di bawah pulau menjadi teduh; monster tidak muncul di bayangannya", "The ground under the island lies in shade; no monster spawns in its shadow")
add(W + "langit_aula_lain", "Aula sekte lain dalam %s blok dari tahta", "Another sect's hall within %s blocks of the throne")
add(W + "langit_ada_orang", "%s orang luar di ruang pulau (pembangun menunggu mereka)", "%s outsiders in the island space (the builders wait for them)")

# ---------------------------------------------------------------- engine errors
X = "kultivator.blueprint.error."
add(X + "langit_no_sky", "Tidak ada rancangan sekte langit yang tertambat.", "There is no tethered sky sect design.")
add(X + "langit_not_on_throne", "Prasasti belum berdiri di atas alas Tahta.", "The stele does not stand on the Throne's base yet.")
add(X + "langit_core_unfinished", "Inti pulau (tingkat 1 langit) belum selesai.", "The island core (sky tier 1) is not finished.")
add(X + "upgrade_family", "Naik Templat hanya dalam satu keluarga; dari darat ke langit lewat Upacara Naik.", "Template upgrades stay within one family; ground to sky goes through the Ascension.")

# ---------------------------------------------------------------- buttons
B = "kultivator.sectbuild.button."
add(B + "langit_rancang", "Rancang Sekte Langit", "Design a Sky Sect")
add(B + "langit_naik_tinggi", "Naik 4", "Up 4")
add(B + "langit_turun_tinggi", "Turun 4", "Down 4")
add(B + "langit_bubar", "Ya, bubarkan rancangan", "Yes, dissolve the design")
add(B + "langit_cara_naik", "Cara Naik", "How to Ascend")
add(B + "langit_naik", "Naik", "Ascend")
add(B + "langit_naik_tetap", "Naik, Kaki Gunung tetap", "Ascend, keep the Mountain Foot")
add(B + "langit_naik_bongkar", "Naik, bongkar tapak bawah", "Ascend, dismantle the ground site")
add(B + "langit_naik_tinggalkan", "Naik, tinggalkan tapak bawah", "Ascend, leave the ground site")

# ---------------------------------------------------------------- the gate of 37.3.3
L = "kultivator.sekte_langit."
add(L + "bangun.mati", "Sekte Langit dimatikan di config (sekteLangit.aktif).", "Sky Sects are switched off in the config (sekteLangit.aktif).")
add(L + "bangun.ranah", "%s menuntut ketua di ranah %s.", "%s needs a leader at the %s realm.")
add(L + "bangun.sudah", "Sektemu sudah punya rancangan langit: %s.", "Your sect already has a sky design: %s.")
add(L + "bangun.jalan_b", "Tanpa tapak darat, hanya templat langit sampai A%s yang boleh didirikan langsung di atas awan.", "Without a ground site, only sky templates up to A%s may be founded straight on the clouds.")
add(L + "bangun.serbuan", "Sekte sedang diserbu: angkat gunung setelah penyerbuan usai.", "The sect is under raid: lift the mountain once it is over.")
add(L + "bangun.kehancuran", "Mode Kehancuran sedang berjalan.", "Kehancuran is running.")
add(L + "bangun.sibuk", "Tapak darat sedang pindah, naik templat atau dibongkar.", "The ground site is moving, upgrading or being dismantled.")
add(L + "bangun.loncat", "Terlalu jauh di atas templat darat: paling tinggi %s.", "Too far above the ground template: at most %s.")
add(L + "bangun.tingkat", "Tapak darat harus mencapai tingkat %s.", "The ground site must reach tier %s.")
add(L + "bangun.level", "Aula sekte harus mencapai level %s.", "The sect hall must reach level %s.")
add(L + "bangun.formasi", "Formasi Agung harus aktif untuk mengangkat gunung.", "A Grand Formation must be active to lift the mountain.")

# ---------------------------------------------------------------- the design
add(L + "rancang.letak", "Rancangan %s: plaza y %s, geser %s, %s dari prasasti.", "Design %s: plaza y %s, shifted %s, %s from the stele.")
add(L + "rancang.tidak_ada", "Belum ada rancangan langit. Mulai dengan /kultivasi aula langit rancang <1-9>.", "No sky design yet. Start with /kultivasi aula langit rancang <1-9>.")
add(L + "rancang.bubar_tanya", "Bubarkan rancangan langit? Sesaji dikembalikan utuh.", "Dissolve the sky design? The offering comes back whole.")
add(L + "rancang.bubar_tanya_bongkar", "Bubarkan rancangan langit? Blok yang sudah berdiri dibongkar dengan pengembalian bahan; sesaji tidak kembali.", "Dissolve the sky design? Standing blocks come down with refunds; the offering does not come back.")
add(L + "rancang.bubar", "Rancangan langit dibubarkan.", "The sky design is dissolved.")
add(L + "rancang.bubar_bongkar", "Rancangan langit dibubarkan; pulau dibongkar perlahan.", "The sky design is dissolved; the island comes down slowly.")
add(L + "log.bubar", "Rancangan sekte langit dibubarkan", "The sky sect design was dissolved")

# ---------------------------------------------------------------- the offering (LangitSesaji)
add(L + "sesaji.tagihan", "Sesaji Angkat Gunung %s: %s", "Mountain-Lifting Offering for %s: %s")
add(L + "sesaji.cukup", "Inventarismu cukup untuk sesaji.", "Your inventory covers the offering.")
add(L + "sesaji.kurang", "Sesaji kurang: %s", "The offering is short: %s")
add(L + "sesaji.diambil", "Sesaji dipersembahkan: %s", "The offering is made: %s")
add(L + "sesaji.kembali", "Sesaji dikembalikan ke inventarismu.", "The offering is back in your inventory.")

# ---------------------------------------------------------------- the Ascension (LangitAngkat)
add(L + "naik.inti_siap", "Inti pulau berdiri. Cabut prasasti darat dan taruh di atas alas Tahta untuk Upacara Naik.", "The island core stands. Take up the ground stele and set it on the Throne's base for the Ascension.")
add(L + "naik.siap_dibawa", "Prasasti siap dibawa ke langit.", "The stele is ready to be carried to the sky.")
add(L + "naik.terlalu_jauh", "Tahta terlalu jauh dari prasasti; pembangunan langit dijeda sampai prasasti kembali dekat.", "The Throne is too far from the stele; the sky building pauses until the stele is near again.")
add(L + "naik.inti_belum", "Inti pulau belum selesai; prasasti di Tahta belum bisa naik.", "The island core is not finished; the stele on the Throne cannot ascend yet.")
add(L + "naik.tanya", "Aula %s siap naik ke awan. Pilih nasib tapak darat:", "Hall %s is ready to rise into the clouds. Choose the ground site's fate:")
add(L + "naik.peringatan", "Formasi Agung darat tidak ikut naik; pelindung aula kembali saat tingkat 5 langit selesai.", "The ground Grand Formation does not rise along; the hall's ward returns when sky tier 5 is finished.")
add(L + "naik.tidak_ada", "Tidak ada Upacara Naik yang menunggu.", "No Ascension is waiting.")
add(L + "naik.batal", "Upacara Naik ditunda; prasasti tetap di Tahta dan aula tertidur.", "The Ascension is put off; the stele stays on the Throne and the hall sleeps.")
add(L + "naik.log", "Sekte naik ke awan: %s", "The sect rose into the clouds: %s")
add(L + "naik.berhasil", "%s kini berdiri di atas awan. Tingkat %s dijamin %s hari dunia.", "%s now stands above the clouds. Tier %s is guaranteed for %s world days.")
add(L + "naik.kaki_tetap", "Tapak darat tetap sebagai Kaki Gunung.", "The ground site stays as the Mountain Foot.")
add(L + "naik.kaki_bongkar", "Tapak darat dibongkar dengan pengembalian bahan.", "The ground site comes down with refunds.")
add(L + "naik.kaki_tinggalkan", "Tapak darat ditinggalkan sebagai blok biasa.", "The ground site is left as plain blocks.")
add(L + "status.menunggu_naik", "Menunggu Upacara Naik%s", "Waiting for the Ascension%s")

# ---------------------------------------------------------------- warded air, gate, register, foot, info
add(L + "udara.terlindung", "Udara %s terlindung.", "The air of %s is warded.")
add(L + "udara.tanpa_nama", "sekte langit", "a sky sect")
add(L + "gerbang.tutup", "Gerbang langit ditutup: orang luar terdorong keluar perlahan.", "The sky gate is closed: outsiders drift gently out.")
add(L + "gerbang.buka", "Gerbang langit dibuka.", "The sky gate is open.")
add(L + "gerbang.belum_berdiri", "Gerbang baru menahan bila Gerbang Sembilan Langit berdiri atau Formasi Agung aktif.", "The gate only holds once the Gate of the Nine Heavens stands or a Grand Formation is active.")
add(L + "undang.dikirim", "%s dicatat di daftar tamu langit selama satu hari dunia.", "%s is written in the sky's guest register for one world day.")
add(L + "undang.diterima", "Namamu dicatat di daftar tamu sekte langit %s.", "Your name is written in %s's sky guest register.")
add(L + "kaki.bongkar", "Kaki Gunung dibongkar dengan pengembalian bahan.", "The Mountain Foot comes down with refunds.")
add(L + "kaki.lepas", "Kaki Gunung dilepaskan sebagai blok biasa.", "The Mountain Foot is let go as plain blocks.")
add(L + "kaki.tidak_ada", "Tidak ada Kaki Gunung.", "There is no Mountain Foot.")
add(L + "info.tidak_ada", "Sektemu belum punya rancangan langit.", "Your sect has no sky design.")
add(L + "info.kepala", "%s · Tingkat %s/%s", "%s · Tier %s/%s")
add(L + "info.letak", "Tahta %s %s %s · dasar pulau y %s", "Throne %s %s %s · island floor y %s")
add(L + "info.mode.tertambat", "Tertambat ke prasasti darat (hanya tingkat 1 dibangun)", "Tethered to the ground stele (only tier 1 builds)")
add(L + "info.mode.menanti", "Menanti prasasti di Tahta", "Waiting for the stele on the Throne")
add(L + "info.mode.naik", "Telah naik ke awan", "Risen into the clouds")
add(L + "info.gerbang_tutup", "Gerbang: tertutup", "Gate: closed")
add(L + "info.gerbang_buka", "Gerbang: terbuka", "Gate: open")
add(L + "info.formasi", "Formasi Agung aktif", "Grand Formation active")
add(L + "info.kaki", "Kaki Gunung masih berdiri di darat.", "The Mountain Foot still stands on the ground.")
add(L + "info.sesaji_tertahan", "Sesaji masih ditahan sampai blok pertama berdiri.", "The offering is held until the first block stands.")

# ---------------------------------------------------------------- template screen (Awan tab) and hologram
U = "kultivator.sectbuild."
add(U + "ui.tab_darat", "Darat", "Ground")
add(U + "ui.tab_awan", "Awan", "Clouds")
add(U + "ui.tab_awan_tip", "Sekte di atas awan: pulau melayang yang dibangun dari bahan Gudang, tertambat ke prasasti sampai Upacara Naik.", "A sect above the clouds: floating islands built from the storehouse, tethered to the stele until the Ascension.")
add(U + "ui.sky_height", "Plaza y %s", "Plaza y %s")
add(U + "ui.sky_height_tip", "Tinggi plaza pulau: naik atau turun 4 blok (dicek ulang oleh pratinjau).", "The island plaza's height: up or down 4 blocks (the preview checks it again).")
add(U + "holo.kaki", "Kaki Tangga Awan", "Cloud Stair Foot")
add(U + "holo.tahta", "Tahta · y %s", "Throne · y %s")
for n, id_a, en_a in [
        (11, "Batu pertapa di atas lautan awan", "A hermit's rock above the sea of clouds"),
        (12, "Pulau kabut dengan satu jembatan", "A misty isle with a single bridge"),
        (13, "Pulau bangau dan taman gantung", "Crane isle and its hanging garden"),
        (14, "Gugus pulau dan tangga awan", "An island cluster and its cloud stair"),
        (15, "Puncak melayang dan teras-teras", "A floating peak in terraces"),
        (16, "Tanah suci yang hanyut di langit", "A holy land adrift in the sky"),
        (17, "Gua surga dan kristal gantung", "A cave heaven with hanging crystals"),
        (18, "Kunlun dan pulau-pulau Guixu", "Kunlun and the isles of Guixu"),
        (19, "Sembilan langit di atas lima gunung", "Nine heavens above five mountains")]:
    add(U + "archetype.%d" % n, id_a, en_a)


def write(lang, idx):
    data = OrderedDict((k, v[idx]) for k, v in E.items())
    path = os.path.join(OUT, "blueprint_awan.%s.json" % lang)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(path, len(data))


write("id_id", 0)
write("en_us", 1)
