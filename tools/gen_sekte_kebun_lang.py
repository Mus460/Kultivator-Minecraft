"""Module W (Kebun Obat & Tambang Roh, DESIGN 36.2.10) lang: run `python tools/gen_sekte_kebun_lang.py`. Writes lang-src/sekte_kebun.{id_id,en_us}.json with identical keys (Indonesian primary)."""
import json
import os
from collections import OrderedDict

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lang-src")
K = "kultivator.sekte.kebun."

# key: (Indonesian, English)
E = OrderedDict()


def add(key, idn, en):
    if key in E:
        raise SystemExit("duplicate " + key)
    E[key] = (idn, en)


# ---- blueprint components, item, hub, advancements, scenes, sources
add("kultivator.blueprint.comp.sx_kebun_petak", "Petak Herba Roh", "Spirit Herb Beds")
add("kultivator.blueprint.comp.sx_kebun_tambang", "Mulut Tambang Roh", "Spirit Mine Mouth")
add("item.kultivator.inti_roh_gunung", "Inti Roh Gunung", "Mountain Spirit Core")
add("item.kultivator.inti_roh_gunung.tooltip", "Gunakan: +%s PK ke kolam aula, +%s kemasyhuran sekte",
    "Use: +%s PK to the hall pool, +%s sect renown")
add("item.kultivator.inti_roh_gunung.source", "Sumber: batu lima warna Nüwa (Huainanzi bab 6)",
    "Source: Nüwa's five-coloured stones (Huainanzi ch. 6)")
add("kultivator.hub.card.sekte_kebun_tambang", "Kebun & Tambang", "Garden & Mine")
add("kultivator.hub.card.sekte_kebun_tambang.tip",
    "Petak herba roh dan perawatnya, kedalaman Tambang Roh, giliran penambang dan temuan dari perut gunung.",
    "The spirit herb beds and their gardeners, the Spirit Mine's depth, the miners' shifts and the finds from the mountain's heart.")
add(K + "adv.panen.title", "Kebun Seratus Obat", "The Hundred Medicine Garden")
add(K + "adv.panen.desc", "Panen pertama dari petak herba roh sekte", "Harvest the sect's spirit herb beds for the first time")
add(K + "adv.tambang.title", "Perut Gunung", "The Mountain's Heart")
add(K + "adv.tambang.desc", "Giliran pertama Tambang Roh membawa pulang hasil", "The first shift of the Spirit Mine brings up a haul")
add(K + "scene.bloom", "Mekar Langka — herba %s tahun", "A Rare Bloom — a %s-year herb")
add(K + "scene.core", "Inti Roh Gunung — tingkat %s", "Mountain Spirit Core — level %s")
add(K + "scene.core.sub", "Batu lima warna dari perut gunung", "A five-coloured stone from the mountain's heart")
add(K + "sumber.kebun", "Sumber: Kebun Seratus Obat Sekte Huang Feng (A Record of a Mortal's Journey, bab 150-151).",
    "Source: the Hundred Medicine Garden of the Huang Feng Sect (A Record of a Mortal's Journey, ch. 150-151).")
add(K + "sumber.tambang", "Sumber: batu lima warna yang dilebur Nüwa untuk menambal langit (Huainanzi bab 6); gunung-gunung giok Shanhaijing.",
    "Source: the five-coloured stones Nüwa melted to mend the sky (Huainanzi ch. 6); the jade mountains of the Shanhaijing.")

# ---- stages
for n, (i, e) in enumerate((("Kosong", "Bare"), ("Tunas", "Sprout"), ("Muda", "Young"), ("Kuncup", "Bud"), ("Mekar", "Bloom"))):
    add(K + "stage.%d" % n, i, e)

# ---- server messages
add(K + "no_hall", "Kamu belum punya Aula Sekte yang aktif.", "You have no active Sect Hall.")
add(K + "bed.bare", "Petak %s masih kosong.", "Bed %s is still bare.")
add(K + "bed.busy", "Petak %s sudah ditanami.", "Bed %s is already sown.")
add(K + "bed.cleared", "Petak %s dicabut dan dibersihkan.", "Bed %s is uprooted and cleared.")
add(K + "bed.harvested", "Petak %s dipanen: %s× %s masuk Lumbung.", "Bed %s harvested: %s× %s into the Lumbung.")
add(K + "bed.no_seed", "Tidak ada benih %s di Lumbung maupun di tasmu.", "No %s seed in the Lumbung or your pack.")
add(K + "bed.none", "Petak itu tidak ada.", "There is no such bed.")
add(K + "bed.none_ripe", "Belum ada petak yang siap dipanen.", "No bed is ready to harvest yet.")
add(K + "bed.not_ripe", "Petak %s belum siap dipanen.", "Bed %s is not ready to harvest.")
add(K + "bed.sown", "Petak %s ditanami %s; matang dalam %s hari dunia.", "Bed %s sown with %s; ripe in %s world days.")
add(K + "job.busy", "%s sedang sibuk (terobosan atau duel).", "%s is busy (a breakthrough or a duel).")
add(K + "job.defiant", "%s membangkang dan menolak bekerja.", "%s is defiant and refuses to work.")
add(K + "job.far", "%s terlalu jauh dari prasasti sekte.", "%s is too far from the sect stele.")
add(K + "job.gone", "Murid itu tidak ada di sini.", "That disciple is not here.")
add(K + "job.no_slot", "Tempat kerja itu tidak ada.", "There is no such work slot.")
add(K + "job.resting", "%s sedang libur; biarkan ia beristirahat dulu.", "%s is on a day off; let them rest first.")
add(K + "mine.deepest", "Tambang Roh sudah di tingkat terdalam.", "The Spirit Mine is already at its deepest level.")
add(K + "mine.no_materials", "Perlu %s kayu dan %s batu dari setoran bangunan.", "Needs %s wood and %s stone from the building deposits.")
add(K + "mine.no_pk", "Kolam aula butuh %s PK.", "The hall pool needs %s PK.")
add(K + "mine.not_built", "Mulut Tambang Roh belum dibangun (komponen Pembangunan Sekte tingkat 4).",
    "The Spirit Mine Mouth is not built yet (a Sect Construction component of tier 4).")
add(K + "inti.no_hall", "Inti ini hanya bisa dilebur di aula sektemu sendiri.", "This core can only be melted into your own sect hall.")
add(K + "inti.used", "Inti Roh Gunung dilebur: +%s PK, +%s kemasyhuran.", "Mountain Spirit Core melted: +%s PK, +%s renown.")
add(K + "inti.renown", "Inti Roh Gunung", "Mountain Spirit Core")

# ---- the log
add(K + "log.bloom", "Mekar langka! %s tumbuh seolah berusia %s tahun.", "A rare bloom! %s grew as if %s years old.")
add(K + "log.cave_empty", "Batu runtuh di Tambang Roh, untung tak ada yang di dalam.", "Rock fell in the Spirit Mine; luckily no one was inside.")
add(K + "log.cave_in", "Runtuhan di Tambang Roh! %s terluka dan dibawa keluar untuk pulih sehari.",
    "A cave-in in the Spirit Mine! %s is hurt and carried out to heal for a day.")
add(K + "log.core", "%s menemukan Inti Roh Gunung di tingkat %s!", "%s found a Mountain Spirit Core at level %s!")
add(K + "log.deepen", "Tambang Roh digali ke tingkat %s; kini %s penambang per giliran.", "The Spirit Mine is dug to level %s; now %s miners a shift.")
add(K + "log.first_harvest", "Panen pertama Kebun Obat: %s× %s.", "The Medicine Garden's first harvest: %s× %s.")
add(K + "log.first_ore", "Giliran pertama Tambang Roh membawa pulang %s barang.", "The Spirit Mine's first shift brought up %s items.")
add(K + "log.forced", "%s sudah %s hari bekerja tanpa libur dan mulai menggerutu.", "%s has worked %s days without a day off and is grumbling.")
add(K + "log.harvest", "%s× %s dipanen dari petak %s.", "%s× %s harvested from bed %s.")
add(K + "log.harvest_by", "%s memanen %s× %s dari petak %s.", "%s harvested %s× %s from bed %s.")
add(K + "log.inti_used", "%s melebur Inti Roh Gunung ke kolam aula (+%s PK).", "%s melted a Mountain Spirit Core into the hall pool (+%s PK).")
add(K + "log.miner", "%s turun ke tambang (tempat %s).", "%s goes down the mine (slot %s).")
add(K + "log.miner_left", "%s meninggalkan giliran tambang.", "%s left the mine shift.")
add(K + "log.tender", "%s merawat petak %s-%s.", "%s tends beds %s-%s.")
add(K + "log.tender_left", "%s berhenti merawat kebun.", "%s stopped tending the garden.")

# ---- memories (Ingatan)
add(K + "ingat.dipaksa", "Dipaksa bekerja %s hari berturut-turut", "Forced to work %s days in a row")
add(K + "ingat.inti", "Menemukan Inti Roh Gunung di perut gunung", "Found a Mountain Spirit Core in the mountain's heart")
add(K + "ingat.mekar", "Merawat %s hingga mekar langka", "Tended %s into a rare bloom")
add(K + "ingat.runtuh", "Tertimpa runtuhan di Tambang Roh", "Caught in a cave-in in the Spirit Mine")

# ---- test commands
add(K + "cmd.bad", "Petak atau herba tidak dikenal.", "Unknown bed or herb.")
add(K + "cmd.bed", "  Petak %s: %s, tahap %s, %s%%, %s tahun", "  Bed %s: %s, stage %s, %s%%, %s years")
add(K + "cmd.full", "Lumbung penuh; inti tidak bisa disimpan.", "The Lumbung is full; the core cannot be stored.")
add(K + "cmd.grown", "Kebun dan tambang dimajukan %s hari dunia.", "The garden and mine advanced by %s world days.")
add(K + "cmd.harvested", "%s petak dipanen.", "%s beds harvested.")
add(K + "cmd.info", "Kebun: %s petak, %s tempat perawat (%s terisi). Tambang: tingkat %s, %s/%s penambang.",
    "Garden: %s beds, %s gardener slots (%s filled). Mine: level %s, %s/%s miners.")
add(K + "cmd.level", "Tambang Roh diatur ke tingkat %s (%s penambang per giliran).", "Spirit Mine set to level %s (%s miners a shift).")
add(K + "cmd.shift", "Giliran hari %s diselesaikan.", "The shift of day %s is settled.")
add(K + "cmd.stats", "  Panen %s, herba %s, hasil tambang %s, inti %s", "  Harvests %s, herbs %s, mine haul %s, cores %s")
add(K + "cmd.streaks", "%s pekerja diatur ke %s hari kerja berturut.", "%s workers set to %s consecutive work days.")
add(K + "cmd.test_beds", "Petak uji: %s (kini %s petak dihitung).", "Test beds: %s (now %s beds count).")
add(K + "cmd.test_mine_on", "Tambang uji aktif tanpa bangunan.", "Test mine on without the building.")
add(K + "cmd.test_mine_off", "Tambang uji dimatikan.", "Test mine off.")

# ---- the board
U = K + "ui."
add(U + "judul", "Kebun Obat & Tambang Roh", "Medicine Garden & Spirit Mine")
add(U + "tab_kebun", "Kebun", "Garden")
add(U + "tab_tambang", "Tambang", "Mine")
add(U + "bare", "kosong", "bare")
add(U + "kosong", "Kosong", "Bare")
add(U + "kebun_mati", "Kebun dimatikan di config (kebun.kebunAktif).", "The garden is switched off in the config (kebun.kebunAktif).")
add(U + "tambang_mati", "Tambang dimatikan di config (kebun.tambangAktif).", "The mine is switched off in the config (kebun.tambangAktif).")
add(U + "belum_petak", "Belum ada petak herba roh. Bangun komponen \"Petak Herba Roh\" (Pembangunan Sekte tingkat 3) dari bahan yang disetor.",
    "No spirit herb beds yet. Build the \"Spirit Herb Beds\" component (Sect Construction tier 3) from deposited materials.")
add(U + "belum_tambang", "Mulut Tambang Roh belum berdiri. Bangun komponennya (Pembangunan Sekte tingkat 4, templat 4-9) dari bahan yang disetor.",
    "The Spirit Mine Mouth does not stand yet. Build its component (Sect Construction tier 4, templates 4-9) from deposited materials.")
add(U + "petak_judul", "Petak Herba Roh: %s (aula tingkat %s)", "Spirit Herb Beds: %s (hall level %s)")
add(U + "petak_n", "Petak %s", "Bed %s")
add(U + "siap_panen", "Siap panen!", "Ready to harvest!")
add(U + "tip_tahap", "%s, %s%%", "%s, %s%%")
add(U + "tip_sisa", "Matang dalam %s", "Ripe in %s")
add(U + "tip_umur", "Umur benih: %s tahun", "Seed age: %s years")
add(U + "tip_dirawat", "Dirawat (tumbuh penuh)", "Tended (full growth)")
add(U + "tip_tak_dirawat", "Tak dirawat (tumbuh setengah)", "Untended (half growth)")
add(U + "hari", "%s hari", "%s days")
add(U + "jam", "%s jam", "%s hours")
add(U + "pilih_petak", "Pilih sebuah petak", "Choose a bed")
add(U + "pilih_petak_tip", "Klik petak untuk menanam atau melihatnya; klik lagi petak yang mekar untuk memanen. Kamu juga bisa klik tanahnya di dunia.",
    "Click a bed to sow or inspect it; click a blooming bed again to harvest. You can also click its soil in the world.")
add(U + "pilih_benih", "Pilih benih (dari Lumbung dulu, lalu dari tasmu):", "Choose a seed (from the Lumbung first, then your pack):")
add(U + "benih_hari", "%s hr", "%s d")
add(U + "benih_stok", "%s/%s", "%s/%s")
add(U + "panen", "Panen", "Harvest")
add(U + "panen_semua", "Panen semua (%s)", "Harvest all (%s)")
add(U + "cabut", "Cabut", "Uproot")
add(U + "ulang_on", "Tanam ulang: ya", "Replant: on")
add(U + "ulang_off", "Tanam ulang: tidak", "Replant: off")
add(U + "ulang_tip", "Tanam ulang memakai satu herba hasil panen sebagai benih berikutnya.",
    "Replanting keeps one harvested herb as the next seed.")
add(U + "statistik_kebun", "Sepanjang masa: %s panen, %s herba.", "All time: %s harvests, %s herbs.")
add(U + "perawat", "Perawat kebun (libur tiap %s hari kerja)", "Gardeners (a day off every %s work days)")
add(U + "perawat_baris", "%s hari beruntun", "%s days in a row")
add(U + "penambang_baris", "%s hr · hari ini %s · total %s", "%s d · today %s · total %s")
add(U + "slot_petak", "Petak %s-%s", "Beds %s-%s")
add(U + "slot_mulut", "Mulut (%s)", "Adit (%s)")
add(U + "slot_dalam", "Dalam (%s)", "Deep (%s)")
add(U + "pilih_murid", "+ pilih murid", "+ choose a disciple")
add(U + "lepas", "Lepaskan dari tugas", "Release from the job")
add(U + "libur", "Beri hari libur (wajib tiap %s hari kerja)", "Give a day off (owed every %s work days)")
add(U + "giliran", "Giliran: %s penambang (libur tiap %s hari)", "Shift: %s miners (a day off every %s days)")
add(U + "giliran_tip", "Satu giliran = satu hari dunia; hasil masuk Lumbung saat hari berganti. Lebih dari batas hari tanpa libur = dipaksa kerja.",
    "One shift = one world day; the haul goes to the Lumbung when the day turns. Past the limit without a day off = forced work.")
add(U + "statistik_tambang", "Sepanjang masa: %s hasil tambang, %s Inti Roh Gunung.", "All time: %s mine haul, %s Mountain Spirit Cores.")
add(U + "kedalaman", "Kedalaman %s/9", "Depth %s/9")
add(U + "bahaya", "Bahaya runtuh %s%% per giliran", "Cave-in risk %s%% a shift")
add(U + "aman", "Belum ada bahaya runtuh", "No cave-in risk yet")
add(U + "tabel", "Hasil tingkat %s", "Yield at level %s")
add(U + "tabel_baris", "%s× %s", "%s× %s")
add(U + "terdalam", "Sudah di tingkat terdalam.", "Already at the deepest level.")
add(U + "perdalam_judul", "Gali ke tingkat %s", "Dig to level %s")
add(U + "biaya_pk", "%s PK dari kolam aula", "%s PK from the hall pool")
add(U + "biaya_kayu", "%s kayu (ada %s)", "%s wood (have %s)")
add(U + "biaya_batu", "%s batu (ada %s)", "%s stone (have %s)")
add(U + "perdalam", "Perdalam tambang", "Deepen the mine")
add(U + "pilih_perawat", "Perawat untuk tempat %s", "Gardener for slot %s")
add(U + "pilih_penambang", "Penambang untuk tempat %s", "Miner for slot %s")
add(U + "akar_kebun", "Akar Kayu atau Air: hasil ×1,1", "Wood or Water root: yield ×1.1")
add(U + "akar_tambang", "Akar Tanah atau Logam: hasil ×1,1", "Earth or Metal root: yield ×1.1")
add(U + "tak_ada_murid", "Tidak ada murid di dekat prasasti sekte (64 blok).", "No disciples near the sect stele (64 blocks).")
add(U + "c_membangkang", "membangkang", "defiant")
add(U + "c_libur", "sedang libur", "on a day off")
add(U + "c_terobosan", "terobosan", "breaking through")
add(U + "c_luang", "luang", "free")
add(U + "c_kerja.kebun", "di kebun", "in the garden")
add(U + "c_kerja.tambang", "di tambang", "in the mine")
add(U + "c_kerja.tugas", "bertugas", "on duty")
add(U + "c_kerja.lain", "bekerja", "working")
add(U + "c_beruntun", "%s · %s hr", "%s · %s d")
add(U + "batal", "Batal", "Cancel")
add(U + "log_baris", "Hari %s: %s", "Day %s: %s")


def write(lang, idx):
    data = OrderedDict((k, v[idx]) for k, v in E.items())
    path = os.path.join(OUT, "sekte_kebun.%s.json" % lang)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(path, len(data))


write("id_id", 0)
write("en_us", 1)
