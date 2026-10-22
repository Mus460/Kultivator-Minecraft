"""Writes lang-src/sekte_tetua.{id_id,en_us}.json: module E (Tetua & Pewaris, DESIGN 36.2.6).
Indonesian primary; both files get exactly the same keys. Run: python tools/gen_sekte_tetua_lang.py"""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = "kultivator.sekte.tetua."
L = {}


def put(key, idn, en):
    assert key not in L, key
    L[key] = (idn, en)


# ------------------------------------------------------------------ offices, appointments
put(T + "wakil", "Wakil %s", "Deputy %s")
put(T + "appointed", "%s kini %s. Bonus bidangnya %s.", "%s is now %s. Its domain's bonus: %s.")
put(T + "appointed.deputy", "%s belum berjenjang tetua, jadi menjabat sebagai wakil (separuh bonus).",
    "%s is not of elder rank yet, so it serves as a deputy (half the bonus).")
put(T + "dismissed", "%s dibebaskan dari jabatan %s.", "%s is relieved of the office of %s.")
put(T + "why.not_yours", "Itu bukan muridmu.", "That is not your disciple.")
put(T + "why.no_hall", "Dirikan Aula Sekte dulu: tetua mengurus sebuah sekte.", "Found a Sect Hall first: elders run a sect.")
put(T + "why.is_master", "%s adalah Ketua sekte; Ketua tidak memegang jabatan tetua.", "%s is the Sect Master; the Master holds no elder's office.")
put(T + "why.rank", "%s harus berjenjang %s atau lebih untuk jabatan ini.", "%s must be %s or higher for this office.")
put(T + "why.rank_agung", "Kursi Tetua Agung butuh %s jenjang %s atau Tetua Agung.", "The Grand Elder's seat needs %s to be %s or Grand Elder.")
put(T + "why.already", "%s sudah menjabat %s.", "%s already serves as %s.")
put(T + "why.not_loaded", "Murid itu tidak ada di dekat sini; panggil dulu ke aula.", "That disciple is not nearby; call it to the hall first.")
put(T + "auto_accept", "%s menerima %s sebagai murid atas namamu pada Hari Penerimaan.",
    "%s accepted %s as a disciple in your name on Recruitment Day.")

# ------------------------------------------------------------------ hall log and report lines
put(T + "log.appointed", "%s diangkat menjadi %s (%s).", "%s was appointed %s (%s).")
put(T + "log.dismissed", "%s dibebaskan dari jabatan %s.", "%s was relieved as %s.")
put(T + "log.lost_rank", "%s turun jenjang dan melepas jabatan %s.", "%s lost its rank and left the office of %s.")
put(T + "log.left", "%s meninggalkan sekte; jabatan %s kosong.", "%s left the sect; the office of %s stands empty.")
put(T + "log.vacant", "Jabatan %s kosong: %s tidak lagi bersama kita.", "The office of %s is empty: %s is no longer with us.")
put(T + "log.heir", "%s diangkat menjadi pewaris sekte.", "%s was named heir of the sect.")
put(T + "log.succession", "%s menerima jubah dan mangkuk: Ketua Generasi ke-%s. %s kini Leluhur Pendiri.",
    "%s received the robe and bowl: Sect Master of generation %s. %s is now the Founding Patriarch.")
put(T + "log.master_fallen", "Ketua generasi ini telah tiada; Leluhur Pendiri memimpin lagi.",
    "This generation's Sect Master is gone; the Founding Patriarch leads again.")
put(T + "log.auto_accept", "%s menerima calon %s atas nama Ketua.", "%s accepted the candidate %s in the Master's name.")
put(T + "urusan.agung", "%s menengahi %s dan %s (%s); amarah mereka mereda.", "%s mediated between %s and %s (%s); their anger eased.")
put(T + "urusan.hukum", "%s dari Balai Hukum menasihati %s.", "%s of the Hall of Law counselled %s.")
put(T + "urusan.ajar", "%s membimbing %s membaca di Paviliun Kitab.", "%s guided %s's reading in the Scripture Pavilion.")
put(T + "urusan.pusaka", "%s merapikan Lumbung: %s tumpukan digabung.", "%s tidied the Storehouse: %s stacks merged.")
put(T + "urusan.luar", "%s menyambut utusan %s di gerbang.", "%s received the envoy of %s at the gate.")
put(T + "urusan.obat", "%s memberi pil penyembuh kepada %s yang terluka.", "%s gave a healing pill to the wounded %s.")
put(T + "quiet.agung", "%s memeriksa catatan sekte; tidak ada perselisihan.", "%s went over the sect's records; no quarrels.")
put(T + "quiet.hukum", "%s berjaga di Balai Hukum; semua murid tenang.", "%s kept the Hall of Law; every disciple is at peace.")
put(T + "quiet.ajar", "%s menyalin kitab; tidak ada yang sedang membaca.", "%s copied scriptures; nobody was reading.")
put(T + "quiet.pusaka", "%s menghitung isi Lumbung; semuanya rapi.", "%s counted the Storehouse; all in order.")
put(T + "quiet.luar", "%s menulis surat kepada sekte tetangga.", "%s wrote letters to the neighbouring sects.")
put(T + "quiet.obat", "%s meracik obat; tidak ada yang terluka.", "%s mixed medicines; nobody is wounded.")
put(T + "lapor.loyalty_down", "%s kini %s.", "%s is now %s.")
put(T + "lapor.loyalty_up", "%s kembali %s.", "%s is %s again.")
put(T + "lapor.by_none", "catatan aula", "the hall's records")
put(T + "lapor.brought", "%s menyerahkan laporan pagi.", "%s hands you the morning report.")
put(T + "someone", "seorang murid", "a disciple")
put(T + "renown.founder", "nama Leluhur Pendiri", "the Founding Patriarch's name")

# ------------------------------------------------------------------ memories (the disciple's scroll)
offices = {"agung": ("Tetua Agung", "Grand Elder"), "hukum": ("Tetua Balai Hukum", "Elder of the Hall of Law"),
           "ajar": ("Tetua Paviliun Kitab", "Elder of the Scripture Pavilion"), "pusaka": ("Tetua Perbendaharaan", "Elder of the Treasury"),
           "luar": ("Tetua Urusan Luar", "Elder of Outer Affairs"), "obat": ("Tetua Obat", "Elder of Medicine")}
for oid, (idn, en) in offices.items():
    put(T + "memory.appointed." + oid, "Diangkat menjadi %s di depan sekte." % idn, "Appointed %s before the whole sect." % en)
put(T + "memory.replaced", "Jabatannya diberikan kepada %s.", "Its office was given to %s.")
put(T + "memory.dismissed", "Dibebaskan dari jabatannya.", "Relieved of its office.")
put(T + "memory.lost_rank", "Melepas jabatan karena turun jenjang.", "Gave up its office after losing rank.")
put(T + "memory.counsel", "Dinasihati %s dari Balai Hukum.", "Counselled by %s of the Hall of Law.")
put(T + "memory.guided", "Dibimbing %s membaca kitab.", "Guided through a scripture by %s.")
put(T + "memory.heir", "Diangkat menjadi pewaris oleh gurunya.", "Named heir by its master.")
put(T + "memory.passed_over", "Gurunya memilih %s sebagai pewaris, bukan dirinya.", "Its master chose %s as heir, not itself.")
put(T + "memory.throne", "Menerima jubah dan mangkuk: Ketua Generasi ke-%s.", "Received the robe and bowl: Sect Master of generation %s.")
put(T + "memory.emeritus", "Menyerahkan takhta kepada generasi ke-%s.", "Passed the throne on to generation %s.")

# ------------------------------------------------------------------ requirements and the ceremony
put(T + "req.heir", "Pewaris: %s", "Heir: %s")
put(T + "req.heir_none", "Belum ada pewaris (Angkat Pewaris di layar murid)", "No heir yet (Name Heir on the disciple screen)")
put(T + "req.heir_away", "Pewaris tidak ada di dekat sini", "The heir is not nearby")
put(T + "req.realm", "Pewaris setidaknya %s", "Heir at least %s")
put(T + "req.stars", "Bintang pewaris %s/%s", "Heir's stars %s/%s")
put(T + "req.hall", "Tingkat aula %s/%s", "Hall level %s/%s")
put(T + "req.age", "Sekte berdiri %s/%s hari", "Sect standing %s/%s days")
put(T + "req.already", "Pewaris ini sudah menjadi Ketua", "This heir already rules")
put(T + "takhta.start", "Upacara Penyerahan Takhta dimulai. %s berjalan ke depan aula; %s murid berbaris.",
    "The Succession Ceremony begins. %s walks before the hall; %s disciples form the rows.")
put(T + "takhta.start_other", "%s menyerahkan takhta sektenya kepada %s. Upacara dimulai.",
    "%s is passing the sect's throne to %s. The ceremony begins.")
put(T + "takhta.broken", "Upacara terputus: %s atau gurunya meninggalkan tempat, atau bahaya datang. Mulai lagi kapan saja.",
    "The ceremony broke off: %s or its master left, or danger came. Begin again any time.")
put(T + "takhta.done", "%s kini Ketua Generasi ke-%s.", "%s is now Sect Master of generation %s.")
put(T + "takhta.founder", "Kamu kini Leluhur Pendiri: nama sekte bertambah harum, sekte lain menghormatimu.",
    "You are now the Founding Patriarch: the sect's name grows, other sects honour you.")
put(T + "takhta.end", "Barisan bubar. Upacara selesai.", "The rows disperse. The ceremony is over.")
put(T + "takhta.why.running", "Upacara sudah berlangsung.", "A ceremony is already under way.")
put(T + "takhta.why.requirements", "Syarat Penyerahan Takhta belum lengkap (lihat Balai Tetua › Penerus).",
    "The succession's requirements are not met (see Hall of Elders › Heir).")
put(T + "takhta.why.heir_away", "Pewaris harus ada di dekatmu.", "The heir must be near you.")
put(T + "takhta.why.heir_busy", "%s sedang sibuk (bertarung, bertugas, atau menerobos).", "%s is busy (fighting, on duty or breaking through).")
put(T + "takhta.why.place", "Upacara diadakan di depan aula utama sektemu.", "The ceremony is held before your sect's main hall.")
put(T + "takhta.why.gather", "Berdirilah dalam %s blok dari depan aula bersama pewaris.", "Stand within %s blocks of the hall's front with the heir.")
put(T + "takhta.why.danger", "Tidak ada upacara saat bahaya mengancam.", "No ceremony while danger is near.")
put(T + "title.founder", "Leluhur Pendiri %s", "Founding Patriarch %s")
put(T + "status.generation", "Ketua Generasi ke-%s: %s", "Sect Master, generation %s: %s")
put(T + "status.offices", "Tetua menjabat: %s/%s", "Elders in office: %s/%s")

# ------------------------------------------------------------------ the card
put(T + "card.title", "Laporan Pagi", "Morning Report")
put(T + "card.day", "Hari ke-%s · dari %s", "Day %s · from %s")
put(T + "card.brought", "Hari ke-%s · dibawa %s", "Day %s · brought by %s")
put(T + "card.quiet", "Malam berlalu dengan tenang. Tidak ada kabar.", "The night passed quietly. No news.")
put(T + "card.more", "…dan %s kabar lagi", "…and %s more")
put(T + "card.foot", "Hadir %s · Gelisah %s · Tetua %s · Kebun +%s · Tambang +%s", "Present %s · Restless %s · Elders %s · Garden +%s · Mine +%s")
mods = {"tetua": ("Para Tetua", "The Elders"), "kepribadian": ("Hati Murid", "Hearts"), "hubungan": ("Hubungan", "Ties"),
        "turnamen": ("Turnamen", "Tournament"), "tangga": ("Tangga Ujian", "Trial Stair"), "paviliun": ("Paviliun & Leluhur", "Pavilion & Ancestors"),
        "misi": ("Misi", "Missions"), "seragam": ("Lambang & Seragam", "Emblem & Robes"), "diplomasi": ("Sekte Lain", "Other Sects"),
        "kebun": ("Kebun & Tambang", "Garden & Mine"), "lain": ("Lain-lain", "Other")}
for mid, (idn, en) in mods.items():
    put(T + "modul." + mid, idn, en)

# ------------------------------------------------------------------ scenes
put(T + "scene.throne", "Penyerahan Takhta", "The Succession")
put(T + "scene.throne.sub", "Jubah dan mangkuk berpindah tangan · Generasi ke-%s", "The robe and bowl change hands · Generation %s")
put(T + "scene.report", "Laporan Pagi · %s", "Morning Report · %s")
put(T + "scene.appoint", "Diangkat Tetua · %s", "Elder Appointed · %s")

# ------------------------------------------------------------------ the screen
put(T + "ui.title", "Balai Tetua", "Hall of Elders")
put(T + "ui.title.hall", "Balai Tetua · %s", "Hall of Elders · %s")
put(T + "ui.tab.jabatan", "Jabatan", "Offices")
put(T + "ui.tab.laporan", "Laporan", "Report")
put(T + "ui.tab.penerus", "Penerus", "Heir")
put(T + "ui.close", "Tutup", "Close")
put(T + "ui.cancel", "Batal", "Cancel")
put(T + "ui.appoint", "Angkat", "Appoint")
put(T + "ui.appoint.tip", "Pilih murid untuk jabatan %s", "Choose a disciple for %s")
put(T + "ui.dismiss", "Copot", "Relieve")
put(T + "ui.dismiss.sure", "Yakin?", "Sure?")
put(T + "ui.vacant", "— kosong —", "— vacant —")
put(T + "ui.deputy", "%s (wakil)", "%s (deputy)")
put(T + "ui.detail", "%s · %s · %s urusan · %s", "%s · %s · %s affairs · %s")
put(T + "ui.status.desk", "di meja", "at the desk")
put(T + "ui.status.idle", "luang", "free")
put(T + "ui.status.away", "jauh", "away")
put(T + "ui.hours", "Jam kerja %s-%s · sekarang %s (%s)", "Office hours %s-%s · now %s (%s)")
put(T + "ui.hours.open", "buka", "open")
put(T + "ui.hours.closed", "tutup", "closed")
put(T + "ui.pick.title", "Siapa yang menjabat %s?", "Who shall serve as %s?")
put(T + "ui.pick.none", "Belum ada murid berjenjang Inti atau lebih di dekat sini. Naikkan jenjang murid di papan Jenjang aula.",
    "No disciple of Core rank or higher is nearby. Promote disciples on the hall's Rank tablet.")
put(T + "ui.pick.line", "%s · %s", "%s · %s")
put(T + "ui.pick.holds", "kini: %s", "now: %s")
put(T + "ui.pick.full", "Bisa menjabat penuh.", "Can hold the office in full.")
put(T + "ui.pick.deputy", "Bisa menjabat sebagai wakil (separuh bonus).", "Can serve as a deputy (half the bonus).")
put(T + "ui.pick.no", "Jenjangnya belum cukup.", "Its rank is not high enough.")
put(T + "ui.report.none", "Belum ada laporan. Tetua Agung menyerahkannya setiap pagi saat kamu berada di wilayah aula.",
    "No report yet. The Grand Elder brings one every morning while you are in the hall's territory.")
put(T + "ui.report.show", "Tampilkan kartu", "Show the card")
put(T + "ui.report.show.tip", "Menutup balai dan menampilkan laporan terakhir di layar.", "Closes the hall and shows the last report on screen.")
put(T + "ui.heir", "Pewaris", "Heir")
put(T + "ui.heir.none", "Belum ada pewaris. Angkat pewaris lewat ritual di layar murid (Jalan › Pewaris).",
    "No heir yet. Name one through the ritual on the disciple screen (Path › Heir).")
put(T + "ui.heir.line", "%s · bintang %s · segel %s/9", "%s · stars %s · hexes %s/9")
put(T + "ui.heir.away", "tidak ada di dekat sini", "not nearby")
put(T + "ui.throne", "Upacara Penyerahan Takhta", "Succession Ceremony")
put(T + "ui.throne.sure", "Klik lagi untuk memulai", "Click again to begin")
put(T + "ui.throne.running", "Upacara berlangsung", "Ceremony under way")
put(T + "ui.throne.left", "Upacara: %s detik lagi", "Ceremony: %s s left")
put(T + "ui.throne.tip", "90 detik di depan aula utama. Pewaris menjadi Ketua Generasi Kedua (tetap muridmu); kamu menjadi Leluhur Pendiri. Permanen.",
    "90 seconds before the main hall. The heir becomes the second-generation Sect Master (still your disciple); you become the Founding Patriarch. Permanent.")
put(T + "ui.lineage", "Silsilah Ketua", "Lineage of Masters")
put(T + "ui.ketua", "Ketua Generasi ke-%s: %s", "Sect Master, generation %s: %s")
put(T + "ui.founder.hint", "Setelah Penyerahan Takhta kamu menjadi Leluhur Pendiri: +10%% nama aula, +10 hubungan awal dengan sekte lain.",
    "After the Succession you become the Founding Patriarch: +10%% hall renown, +10 starting standing with other sects.")
put(T + "ui.lineage.founder", "Generasi %s · %s (pendiri, hari %s)", "Generation %s · %s (founder, day %s)")
put(T + "ui.lineage.line", "Generasi %s · %s (hari %s)", "Generation %s · %s (day %s)")
put(T + "ui.age", "Sekte telah berdiri %s hari.", "The sect has stood for %s days.")

# ------------------------------------------------------------------ commands
put(T + "cmd.report", "Laporan pagi dibuat.", "Morning report made.")
put(T + "cmd.affairs", "%s tetua menjalankan urusannya sekarang.", "%s elders ran their affairs now.")
put(T + "cmd.skip", "Upacara melompat ke puncaknya.", "The ceremony jumps to its climax.")
put(T + "cmd.no_ceremony", "Tidak ada upacara yang bisa dilompati.", "No ceremony to skip.")
put(T + "cmd.no_disciple", "Tidak ada murid termuat dengan nama itu.", "No loaded disciple has that name.")
put(T + "cmd.no_office", "Jabatan tidak dikenal: agung, hukum, ajar, pusaka, luar, obat.", "Unknown office: agung, hukum, ajar, pusaka, luar, obat.")
put(T + "cmd.no_holder", "Murid itu tidak memegang jabatan.", "That disciple holds no office.")
put(T + "cmd.lineage", "Generasi sekarang: %s · Leluhur Pendiri: %s", "Current generation: %s · Founding Patriarch: %s")
put(T + "cmd.lineage_line", " Generasi %s: %s (hari %s)", " Generation %s: %s (day %s)")
put(T + "cmd.heir", "Pewaris: %s", "Heir: %s")
put(T + "yes", "ya", "yes")
put(T + "no", "belum", "not yet")
put(T + "none", "tidak ada", "none")

# ------------------------------------------------------------------ hub card and advancements
put("kultivator.hub.card.sekte_tetua_balai", "Balai Tetua", "Hall of Elders")
put("kultivator.hub.card.sekte_tetua_balai.tip", "Angkat tetua untuk enam jabatan, baca laporan pagi, dan serahkan takhta kepada pewarismu.",
    "Appoint elders to six offices, read the morning report, and pass the throne to your heir.")
put("advancement.kultivator.sekte.tetua_jabatan.title", "Balai Tetua", "Hall of Elders")
put("advancement.kultivator.sekte.tetua_jabatan.description", "Angkat seorang murid ke salah satu jabatan tetua.",
    "Appoint a disciple to one of the elders' offices.")
put("advancement.kultivator.sekte.tetua_penerus.title", "Jubah dan Mangkuk", "Robe and Bowl")
put("advancement.kultivator.sekte.tetua_penerus.description", "Serahkan takhta kepada pewarismu dan jadilah Leluhur Pendiri.",
    "Pass the throne to your heir and become the Founding Patriarch.")


def write(name, index):
    path = os.path.join(REPO, "lang-src", "sekte_tetua.%s.json" % name)
    data = {k: v[index] for k, v in L.items()}
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


write("id_id", 0)
write("en_us", 1)
print("sekte_tetua lang: %d keys" % len(L))
