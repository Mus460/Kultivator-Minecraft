"""Writes lang-src/sekte.{id_id,en_us}.json (the lead's shared texts, DESIGN 36.3) and
lang-src/sekte_panduan.{id_id,en_us}.json (Kitab Panduan chapters 15-16). Indonesian primary; identical keys."""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

S = "kultivator.sekte."
core = {}


def put(key, idn, en):
    core[key] = (idn, en)


put(S + "cmd.help.title", "Sekte yang Hidup — perintah uji", "Living Sect — test commands")
put(S + "cmd.help.intro", "Klik untuk mengisi kotak obrolan. Setiap modul menambah perintahnya sendiri.",
    "Click to fill the chat box. Every module adds its own commands.")
put(S + "cmd.reload.ok", "Konfigurasi %s dimuat ulang.", "Reloaded %s.")
put(S + "cmd.reload.fail", "%s tidak bisa dibaca; nilai lama tetap dipakai.", "%s could not be read; the old values stay.")
put(S + "cmd.module", "Modul %s: %s", "Module %s: %s")
put(S + "cmd.module.on", "aktif", "live")
put(S + "cmd.module.off", "belum ada / mati", "missing / off")
put(S + "cmd.player.help", "Perguruan: murid, hubungan, lambang, diplomasi, misi, kebun (sub-perintah dari tiap modul).",
    "Sect: disciples, ties, emblem, diplomacy, missions, garden (each module's sub-commands).")
put("kultivator.cheat.group.perguruan", "Sekte yang Hidup", "Living Sect")
put("kultivator.cheat.group.perguruan.desc", "kepribadian, hubungan, turnamen, tangga, paviliun, tetua, misi, lambang, diplomasi, kebun",
    "personality, ties, tournament, stair, pavilion, elders, missions, emblem, diplomacy, garden")

sifat = {
    "rajin": ("Rajin", "Diligent", "Berlatih tanpa disuruh: meditasi lebih cepat, kerja lebih banyak.",
              "Trains unasked: faster meditation, more work done."),
    "malas": ("Malas", "Lazy", "Mencari celah untuk tidur: meditasi lebih lambat, tetapi jarang mengeluh.",
              "Finds a corner to nap: slower meditation, but rarely complains."),
    "sombong": ("Sombong", "Arrogant", "Bangga pada bakatnya: berani di arena, sakit hati bila dipermalukan.",
                "Proud of its gift: bold in the arena, stung by every humiliation."),
    "rendah_hati": ("Rendah Hati", "Humble", "Mudah diajar dan mudah berteman.", "Easy to teach and quick to befriend."),
    "setia": ("Setia", "Loyal", "Tidak mudah goyah; bertahan lebih lama sebelum pergi.", "Hard to shake; stays far longer before leaving."),
    "licik": ("Licik", "Cunning", "Pulang dari misi dengan lebih banyak barang, tetapi lebih mudah berkhianat.",
              "Brings more home from missions, but betrays more easily."),
    "berani": ("Berani", "Brave", "Bertarung lebih lama dan tidak gentar dikirim ke bahaya.", "Fights longer and does not flinch at danger."),
    "penakut": ("Penakut", "Timid", "Mundur lebih awal, tetapi jarang terluka parah.", "Retreats early, but is rarely badly hurt."),
    "ambisius": ("Ambisius", "Ambitious", "Mengejar terobosan; kecewa bila dilangkahi.", "Chases breakthroughs; bitter when passed over."),
    "tenang": ("Tenang", "Serene", "Hatinya jarang bergejolak; suasana hatinya berubah setengah lambatnya.",
               "Its heart seldom stirs; its mood moves at half speed."),
    "penyayang": ("Penyayang", "Kind", "Menjaga saudara seperguruan; dendamnya cepat reda.", "Looks after its fellows; its grudges fade fast."),
    "pendendam": ("Pendendam", "Vengeful", "Tidak pernah lupa luka, dari siapa pun.", "Never forgets a wound, from anyone."),
}
for k, (i, e, di, de) in sifat.items():
    put(S + "sifat." + k, i, e)
    put(S + "sifat." + k + ".desc", di, de)

for k, i, e in [("none", "Tidak ada", "None"), ("kenalan", "Kenalan", "Acquaintance"), ("sahabat", "Sahabat", "Friend"),
                ("saudara_angkat", "Saudara Angkat", "Sworn Sibling"), ("kekasih", "Kekasih", "Beloved"),
                ("pasangan_dao", "Pasangan Dao", "Dao Companion"), ("saingan", "Saingan", "Rival"), ("dendam", "Dendam", "Grudge")]:
    put(S + "ikatan." + k, i, e)

for k, i, e in [("agung", "Tetua Agung", "Grand Elder"), ("hukum", "Tetua Balai Hukum", "Discipline Elder"),
                ("ajar", "Tetua Paviliun Kitab", "Scripture Elder"), ("pusaka", "Tetua Perbendaharaan", "Treasury Elder"),
                ("luar", "Tetua Urusan Luar", "Outer Affairs Elder"), ("obat", "Tetua Obat", "Medicine Elder")]:
    put(S + "jabatan." + k, i, e)

for k, i, e in [("perang", "Perang", "War"), ("bermusuhan", "Bermusuhan", "Hostile"), ("dingin", "Dingin", "Cold"),
                ("netral", "Netral", "Neutral"), ("ramah", "Ramah", "Friendly"), ("sekutu", "Sekutu", "Allied")]:
    put(S + "sikap." + k, i, e)

for k, i, e in [("dipukul", "Dipukul gurunya", "Struck by its master"), ("dihukum", "Dihukum", "Punished"),
                ("diabaikan", "Diabaikan", "Neglected"), ("dipaksa_kerja", "Dipaksa bekerja tanpa istirahat", "Worked without rest"),
                ("dirampas", "Haknya dirampas", "Robbed of its due"), ("dikorbankan", "Dibiarkan jatuh", "Left to fall"),
                ("dipermalukan", "Dipermalukan", "Humiliated"), ("tidak_adil", "Diperlakukan tidak adil", "Treated unfairly")]:
    put(S + "perlakuan." + k, i, e)

for k, i, e in [("minggat", "Minggat diam-diam", "Slipped away"), ("memberontak", "Memberontak", "Rebelled"),
                ("berkhianat", "Berkhianat ke sekte lain", "Defected"), ("diusir", "Diusir", "Expelled"),
                ("mengembara", "Pergi mengembara", "Left to wander")]:
    put(S + "kepergian." + k, i, e)

for k, i, e in [("tumbuh", "Sekte itu tumbuh", "The sect grew"), ("utusan", "Utusan datang", "An envoy arrived"),
                ("hadiah", "Hadiah dikirim", "A gift was sent"), ("tantangan", "Tantangan dilayangkan", "A challenge was issued"),
                ("serangan", "Serangan", "An attack"), ("perang", "Perang dinyatakan", "War declared"),
                ("damai", "Perdamaian", "Peace"), ("sekutu", "Persekutuan", "Alliance"), ("putus", "Persekutuan putus", "Alliance broken"),
                ("sergap_misi", "Tim misi disergap", "A mission team ambushed"), ("merekrut", "Membujuk murid", "Luring a disciple"),
                ("perang_npc", "Perang antar sekte", "War between sects")]:
    put(S + "aksi." + k, i, e)

for k, i, e in [("setia", "Setia", "Loyal"), ("gelisah", "Gelisah", "Restless"), ("membangkang", "Membangkang", "Defiant"),
                ("akan_pergi", "Akan Pergi", "About to Leave")]:
    put(S + "keadaan." + k, i, e)

# ---------------------------------------------------------------- Kitab Panduan 15-16
G = "kultivator.guide."
guide = {}


def g(key, idn, en):
    guide[key] = (idn, en)


g(G + "chapter.15", "Murid yang Hidup", "Living Disciples")
g(G + "chapter.15.desc", "Watak, suasana hati, persahabatan, ujian tangga, kitab, leluhur, tetua dan penerus.",
  "Traits, moods, friendships, the stair trial, scriptures, ancestors, elders and the successor.")
g(G + "chapter.16", "Sekte di Dunia", "The Sect in the World")
g(G + "chapter.16.desc", "Lambang dan bendera, turnamen, misi, kebun dan tambang, sekte lain: sekutu atau musuh.",
  "Emblem and banners, tournaments, missions, garden and mine, other sects: allies or foes.")

steps15 = [
    ("sifat", "Kenali watak muridmu", "Learn your disciple's nature",
     "Setiap murid punya dua watak (kadang tiga), misalnya Rajin dan Setia, atau Malas dan Sombong.\nBuka gulungan murid atau ketik perintahnya: watak mengubah kecepatan latihan, kerja dan kesetiaannya.\nWatak tidak bisa dibeli; hanya ujian hati tertentu yang bisa menggesernya.",
     "Every disciple has two traits (sometimes three), such as Diligent and Loyal, or Lazy and Arrogant.\nOpen its scroll or type the command: traits change how fast it trains, how much it works and how loyal it stays.\nTraits cannot be bought; only certain trials of the heart can shift them."),
    ("puas", "Buat murid benar-benar puas", "Make a disciple truly content",
     "Kepuasan (0-100) naik saat kamu mengajar, memberi pil, bermeditasi bersamanya, memuji kemenangannya dan menghormati leluhur.\nKepuasan turun bila ia dipukul, diabaikan, dipaksa bekerja atau dipermalukan.\nBicaralah sekali sehari, beri hadiah (pil, herba, batu roh) atau minta maaf di depan prasasti Aula.\nCapai kepuasan 90 pada satu murid.",
     "Contentment (0-100) rises when you teach, give pills, meditate together, praise its victories and honour the ancestors.\nIt falls when the disciple is struck, neglected, overworked or humiliated.\nTalk to it once a day, give a gift (pill, herb, spirit stone) or apologise before the Hall stele.\nBring one disciple to 90."),
    ("rukun", "Redakan murid yang membangkang", "Calm a defiant disciple",
     "Murid yang kesetiaannya jatuh menjadi Gelisah, lalu Membangkang (menolak tugas), lalu bersiap pergi.\nBicaralah dengannya, beri hadiah, minta maaf di depan Aula atau serahkan pada Tetua Balai Hukum (bila ada).\nMurid yang terikat Segel Jiwa tidak akan pernah minggat.",
     "A disciple whose loyalty falls turns Restless, then Defiant (refuses duties), then prepares to leave.\nTalk to it, give a gift, apologise before the Hall or leave it to the Discipline Elder (if you have one).\nA disciple bound by the Soul Seal never runs away."),
    ("sahabat", "Lihat dua murid bersahabat", "See two disciples become friends",
     "Murid yang sering bertemu minum teh bersama; watak, Dao dan pengalaman bersama menentukan apakah mereka cocok.\nSahabat saling membantu dalam pertarungan dan misi.\nLihat Jaring Hubungan di hub Sekte atau tab Hubungan di gulungan murid.",
     "Disciples who meet often share tea; traits, Dao and shared deeds decide whether they get along.\nFriends help each other in fights and on missions.\nSee the Web of Ties in the Sect hub or the Ties tab of the disciple scroll."),
    ("saingan", "Lahirnya persaingan", "A rivalry is born",
     "Kekalahan di arena dan kenaikan jenjang yang tidak adil melahirkan saingan, bahkan dendam.\nPersaingan memacu latihan; dendam merusak suasana hati.\nDuel Kehormatan di depan sekte bisa mengubah dendam menjadi persaingan.",
     "Arena defeats and unfair promotions breed rivals, even grudges.\nRivalry spurs training; a grudge sours the mood.\nAn Honour Duel before the sect can turn a grudge into a rivalry."),
    ("tangga", "Adakan Ujian Tangga Seribu", "Hold the Thousand-Step Trial",
     "Bangun Tangga Ujian di depan gerbang (komponen sekte, dari bahan yang kamu setor).\nSaat kamu menguji calon murid (pecahan ametis, seperti biasa), ia mendaki tangga ilusi seribu anak tangga di bawah tekanan yang terus naik.\nYang bertahan sampai puncak menjadi murid dengan hati yang teguh.\nMurid lama bisa mendaki ulang: /kultivasi perguruan tangga ujian <nama> (200 PK, sekali per 30 hari).",
     "Build the Trial Stair before the gate (a sect component, from the materials you deposit).\nWhen you test a candidate (an amethyst shard, as before), it climbs an illusory thousand-step stair under rising pressure.\nThose who reach the top join with a steadfast heart.\nA disciple may climb again: /kultivasi perguruan tangga ujian <name> (200 PK, once per 30 days)."),
    ("kitab", "Murid belajar sendiri di Paviliun Kitab", "A disciple studies alone in the Scripture Pavilion",
     "Klik Paviliun Kitab sambil memegang kitab teknik untuk menyimpannya di rak (atau buka kartu hub Paviliun & Makam).\nMurid yang luang duduk membaca dan perlahan mempelajari teknik itu tanpa menghabiskan Qi-mu.\nTetua Paviliun Kitab mempercepat belajar mereka.",
     "Click the Scripture Pavilion with a technique manual in hand to shelve it (or open the hub card Pavilion & Tombs).\nIdle disciples sit and read, slowly learning the technique without spending your Qi.\nThe Scripture Elder speeds their study."),
    ("leluhur", "Hormati para leluhur", "Honour the ancestors",
     "Murid yang gugur dimakamkan di Pemakaman Sekte; papan arwahnya ditempatkan di Altar Leluhur.\nPersembahkan dupa dan adakan upacara: berkah leluhur menenangkan hati para murid.\nDupa dibuat dari tongkat, arang dan buah beri manis; upacara besar Qingming datang setiap 15 hari.",
     "Fallen disciples rest in the Sect Cemetery; their spirit tablets stand on the Ancestral Altar.\nOffer incense and hold the rites: the ancestors' blessing calms the disciples' hearts.\nIncense is crafted from a stick, charcoal and sweet berries; the great Qingming rite comes every 15 days."),
    ("tetua", "Angkat seorang tetua", "Appoint an elder",
     "Murid berjenjang Tetua bisa memegang jabatan: Balai Hukum, Paviliun Kitab, Perbendaharaan, Urusan Luar atau Obat.\nTetua mengurus urusan harian sendiri dan melapor kepadamu setiap pagi.\nTetua Agung memimpin mereka semua.",
     "A disciple of Elder rank can hold an office: Discipline, Scripture, Treasury, Outer Affairs or Medicine.\nElders run the daily affairs on their own and report to you every morning.\nThe Grand Elder leads them all."),
    ("penerus", "Serahkan takhta kepada pewaris", "Hand the seat to your heir",
     "Pewaris yang sudah ditetapkan dan cukup kuat bisa dinobatkan sebagai Ketua Sekte generasi kedua.\nKamu menjadi Leluhur Pendiri; sekte terus berjalan di tangan penerusmu.\nPilihlah dengan adil: murid yang dilangkahi akan mengingatnya.",
     "A named heir strong enough can be crowned second-generation Sect Master.\nYou become the Founding Patriarch; the sect runs on in your successor's hands.\nChoose fairly: the disciples passed over will remember."),
]
steps16 = [
    ("lambang", "Rancang lambang sekte", "Design the sect emblem",
     "Pilih bentuk, simbol dan tiga warna, lalu semboyan sekte.\nLambang muncul di bendera, gerbang, formasi dan jubah murid.",
     "Choose a shape, a symbol, three colours and the sect's motto.\nThe emblem shows on banners, the gate, the formations and the disciples' robes."),
    ("bendera", "Kibarkan bendera sekte", "Raise the sect banners",
     "Tiang bendera di gerbang dan di sekitar cakram formasi dibangun dari bahan yang kamu setor.\nTiang formasi datang pada tingkat 4. Atur warna seragam setiap jenjang di kartu hub Sekte > Lambang & Seragam.",
     "Banner poles at the gate and around the formation disc are built from deposited materials.\nThe formation poles come at tier 4. Set each rank's uniform colours in the hub card Sect > Emblem & Uniform."),
    ("turnamen", "Nobatkan juara turnamen sekte", "Crown a sect tournament champion",
     "Bangun tribun lei tai (templat 6-9, tingkat 7): turnamen pindah ke panggung arena dengan penonton, Duel Pedang di setiap babak dan papan Elo per musim.\nSekte NPC bisa datang menantang dalam turnamen antar-sekte.\nKemenangan melahirkan kebanggaan; kekalahan bisa melahirkan saingan.",
     "Build the lei tai stands (templates 6-9, tier 7): tournaments move to the arena stage with spectators, a Sword Duel every round and a seasonal Elo board.\nNPC sects may come to challenge you in an inter-sect tournament.\nVictory breeds pride; defeat may breed a rival."),
    ("misi", "Sambut tim misi pulang", "Welcome a mission team home",
     "Kirim murid keluar sekte untuk misi bercerita: mengawal kafilah, memburu binatang iblis, menyelidiki reruntuhan.\nMereka pulang lewat gerbang membawa laporan tertulis tentang apa yang terjadi.",
     "Send disciples out on story missions: escorting caravans, hunting demonic beasts, exploring ruins.\nThey return through the gate with a written report of what happened."),
    ("bawa", "Murid pulang membawa sesuatu", "A disciple brings something home",
     "Kadang tim pulang membawa pemuda berbakat sebagai calon murid, atau harta yang mereka temukan.\nWatak dan ikatan anggota tim mengubah hasilnya.",
     "Sometimes a team brings a gifted youth home as a candidate, or a treasure it found.\nThe team's traits and ties change the outcome."),
    ("kebun", "Panen dari Kebun Obat", "Harvest the herb garden",
     "Klik tanah petak atau buka kartu hub Kebun & Tambang untuk menanam herba roh; murid merawatnya setiap hari.\nTidak ada tanaman yang benar-benar ditanam di dunia: hasilnya masuk ke Lumbung.",
     "Click a bed's soil or open the hub card Garden & Mine to sow spirit herbs; disciples tend them every day.\nNothing is actually planted in the world: the harvest goes to the Storehouse."),
    ("tambang", "Gali Tambang Roh", "Work the Spirit Mine",
     "Mulut tambang dibangun di sisi bukit sekte; murid bergiliran masuk dan keluar membawa batu roh dan bijih.\nSemakin dalam tingkatnya, semakin berharga hasilnya, dan semakin besar bahayanya.\nKlik mulut tambang untuk membuka papannya. Tidak ada blok yang digali.",
     "The mine mouth is built on the sect's hillside; disciples take shifts inside and come out with spirit stones and ore.\nThe deeper the level, the richer the yield and the greater the danger.\nClick the mine mouth to open its board. No block is ever dug."),
    ("rival", "Temukan sekte lain", "Find another sect",
     "Sekte-sekte NPC punya gerbang gunung mereka sendiri di dunia, tumbuh dan saling berperang.\nTemukan mereka lewat misi, utusan atau dengan mendatangi gerbangnya.",
     "NPC sects have their own mountain gates in the world; they grow and fight one another.\nFind them through missions, envoys or by visiting their gates."),
    ("sekutu", "Jalin persekutuan", "Forge an alliance",
     "Kirim utusan dan hadiah sampai sikap mereka Ramah, lalu ajukan pakta.\nSekutu mengirim penjaga saat sekte diserang dan mengundang ke turnamen bersama.",
     "Send envoys and gifts until they are Friendly, then propose a pact.\nAllies send guards when the sect is attacked and invite you to joint tournaments."),
    ("perang", "Menangkan perang sekte", "Win a sect war",
     "Sekte yang bermusuhan bisa menyatakan perang dan menyerbu wilayahmu.\nDatangi gerbang mereka dan kalahkan ketuanya untuk menang; tanpa gerbang, ketua musuh datang ke Aula setelah mengirim surat tantangan.\nTiga serbuan yang menjarah sektemu berarti perang kalah.",
     "A hostile sect may declare war and raid your territory.\nGo to their gate and defeat their master to win; without a gate, the enemy master comes to your Hall after a challenge letter.\nThree raids that sack your sect lose the war."),
]
for ch, steps in ((15, steps15), (16, steps16)):
    for name, ti, te, bi, be in steps:
        g(G + "c%d.%s.title" % (ch, name), ti, te)
        g(G + "c%d.%s.body" % (ch, name), bi, be)


def write(name, table):
    for lang, idx in (("id_id", 0), ("en_us", 1)):
        path = os.path.join(REPO, "lang-src", "%s.%s.json" % (name, lang))
        data = {k: v[idx] for k, v in table.items()}
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(path, len(data))


write("sekte", core)
write("sekte_panduan", guide)
