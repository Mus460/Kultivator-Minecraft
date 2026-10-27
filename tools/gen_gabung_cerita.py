# 41W generator (D-41W: Permintaan Guru branches, elder memories): quest chains, rumour templates and the two lang fragments of module 41W.
# Usage: python -I gen.py <repo>
import json, sys, os

repo = sys.argv[1]
QDIR = os.path.join(repo, "src/main/resources/data/kultivator/tempat/quest")
KDIR = os.path.join(repo, "src/main/resources/data/kultivator/tempat/kabar")
LDIR = os.path.join(repo, "lang-src")

CER = []  # (key, id, en) -> sekte_gabung_cerita
DUN = []  # -> sekte_gabung_dunia
def c(k, i, e): CER.append((k, i, e))
def d(k, i, e): DUN.append((k, i, e))

C = "kultivator.gabung.cerita."
Q = "kultivator.cerita.quest."
VARIAN = ["pedang", "alkimia", "darah", "buddha", "lurus", "netral", "iblis"]

def write_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")

def S(id, tujuan=None, masuk=None, akibat=None, cabang=None, ke=None, batas=None, gagal=None, arah=None):
    o = {"id": id}
    if tujuan: o["tujuan"] = tujuan
    if masuk: o["akibat_masuk"] = masuk
    if akibat: o["akibat"] = akibat
    if cabang: o["cabang"] = [{"syarat": s, "ke": k} for s, k in cabang]
    if ke: o["ke"] = ke
    if batas: o["batas_hari"] = batas
    if gagal: o["gagal_ke"] = gagal
    if arah: o["arah"] = arah
    return o

QUESTS = {}
def quest(qid, pemberi, tahap, nama, ringkas, baris, hasil):
    QUESTS[qid] = {"pemberi": pemberi, "tempat": "gabung_sekte", "tahap": tahap}
    c(Q + qid + ".nama", nama[0], nama[1])
    c(Q + qid + ".ringkas", ringkas[0], ringkas[1])
    for st, (i, e) in baris.items():
        c(Q + qid + "." + st, i, e)
    for st, (i, e) in hasil.items():
        c(Q + qid + ".hasil." + st, i, e)
    ids = {t["id"] for t in tahap}
    assert set(baris) == ids, (qid, set(baris) ^ ids)
    ends = {t["id"] for t in tahap if t.get("ke") == "selesai"}
    assert ends <= set(hasil), (qid, ends - set(hasil))

def scene(r, tahap, i, e):
    c(C + r + "." + tahap, i, e)

def scene_v(r, tahap, table):
    assert set(table) == set(VARIAN), (r, tahap)
    for v in VARIAN:
        i, e = table[v]
        c(C + r + "." + tahap + "." + v, i, e)

def ask(r, tahap, i, e):
    c(C + r + "." + tahap + ".tanya", i, e)

def opt(r, o, i, e, hi, he):
    c(C + r + ".pilih." + o, i, e)
    c(C + r + ".pilih." + o + ".hover", hi, he)

PERAN = "peran:tetua_sekte"
KOMP = "sekte/kompleks"

# =====================================================================================================================
# 1. Air dan Kayu Bakar
r = "air_kayu"
quest("gabung_air_kayu", PERAN, [
    S("pagi", ["gabung_titik arena", "jam 5 10"], ["gabung.cerita air_kayu pagi kakak corak"], ["gabung.sikap kakak 5", "gabung.pk 5"],
      batas=2, gagal="dapur", arah=KOMP),
    S("dapur", ["gabung pilih:air_kayu"], ["gabung.cerita air_kayu dapur saingan", "gabung.pilihan air_kayu dapur balas mengalah"],
      cabang=[(["bendera gabung.air_kayu.balas"], "leitai")], ke="mengalah", batas=3, gagal="mengalah"),
    S("leitai", ["gabung tanding:saingan"], ["gabung.cerita air_kayu leitai saingan", "gabung.tanding saingan"],
      cabang=[(["bendera gabung.tanding.saingan.menang"], "menang")], ke="kalah", batas=2, gagal="kalah"),
    S("menang", ["hari 0"], akibat=["gabung.cerita air_kayu menang kakak", "gabung.sikap saingan -10", "gabung.pk 30"], ke="selesai"),
    S("kalah", ["hari 0"], akibat=["gabung.cerita air_kayu kalah kakak", "gabung.sikap kakak 5", "gabung.pk 10"], ke="selesai"),
    S("mengalah", ["hari 0"], akibat=["gabung.cerita air_kayu mengalah kakak", "gabung.sikap kakak 10", "gabung.pk 10"], ke="selesai"),
], ("Air dan Kayu Bakar", "Water and Firewood"),
   ("Hari-hari pertama di sekte: latihan pagi, dapur, dan seorang kakak seperguruan yang tidak suka murid baru.",
    "Your first days in the sect: the morning drill, the kitchen, and a senior who does not like newcomers."),
   {"pagi": ("Datanglah ke lapangan latihan saat latihan pagi (jam 5-10).", "Come to the training ground for the morning drill (5-10 o'clock)."),
    "dapur": ("Seseorang menunggumu di dapur. Putuskan bagaimana kau menjawab.", "Someone waits for you in the kitchen. Decide how you answer."),
    "leitai": ("Tanding di lei tai: kalahkan saingan dengan aturan sekte.", "A bout on the lei tai: beat your rival under the sect's rules."),
    "menang": ("Kau menang di lei tai.", "You won on the lei tai."),
    "kalah": ("Kau kalah di lei tai.", "You lost on the lei tai."),
    "mengalah": ("Kau memilih mengalah.", "You chose to yield.")},
   {"menang": ("Murid-murid yang menonton tidak lagi menertawakanmu. Saingan menyimpan dendam, tapi kini ia menghormatimu.",
               "The watching disciples no longer laugh at you. Your rival bears a grudge, but now he respects you."),
    "kalah": ("Kekalahan pertama di gunung ini. Kakak seperguruanmu membantumu bangun dan berkata itu bukan yang terakhir.",
              "Your first defeat on this mountain. Your senior helps you up and says it will not be the last."),
    "mengalah": ("Kau menelan malu dan kembali memikul air. Kakak seperguruanmu memperhatikan, dan tidak lupa.",
                 "You swallow the shame and go back to carrying water. Your senior noticed, and does not forget.")})
scene_v(r, "pagi", {
    "pedang": ("Fajar belum penuh, tapi lapangan sudah berdering oleh pedang kayu. %4$s berjalan di antara barisan murid: \"Tebasan pertama hari ini adalah tebasan yang paling jujur. Ikuti aku, adik.\"",
               "Dawn is not full yet, but the ground already rings with wooden swords. %4$s walks between the rows: \"The first cut of the day is the most honest one. Follow me, little brother.\""),
    "alkimia": ("Asap tungku semalam masih menggantung di atas lapangan %1$s. %4$s menepuk bahumu: \"Tangan alkemis harus kuat sebelum halus. Pegang kuda-kudamu, lalu kita lihat apakah kau tahan panas.\"",
                "Last night's furnace smoke still hangs over the grounds of %1$s. %4$s pats your shoulder: \"An alchemist's hands must be strong before they are fine. Hold your stance, then we will see whether you can bear the heat.\""),
    "darah": ("Lapangan %1$s berbau besi. %4$s tidak tersenyum: \"Di sini yang lemah dimakan, yang kuat makan. Ikuti gerakanku, dan jangan sampai jatuh di depan mereka.\"",
              "The grounds of %1$s smell of iron. %4$s does not smile: \"Here the weak are eaten and the strong eat. Follow my moves, and do not fall in front of them.\""),
    "buddha": ("Lonceng subuh baru selesai berdentang. %4$s merapatkan telapak tangan: \"Napas dulu, baru tinju. Tubuh yang tenang tidak mudah tumbang. Mari, adik, kita mulai dari kuda-kuda.\"",
               "The dawn bell has just fallen silent. %4$s presses his palms together: \"Breath first, then the fist. A calm body does not fall easily. Come, little brother, we begin with the stance.\""),
    "lurus": ("Embun masih di batu lapangan %1$s. %4$s memimpin senam pagi dan memberi tempat di sampingnya: \"Murid baru berdiri di dekatku. Kalau salah, aku yang membetulkan, bukan mereka yang menertawakan.\"",
              "Dew still lies on the stones of %1$s. %4$s leads the morning drill and makes room beside him: \"New disciples stand near me. If you err, I correct you, not they who laugh.\""),
    "netral": ("Di lapangan %1$s tak ada yang menyambut atau mengusir. %4$s mengangguk sekali: \"Di sini kau dinilai dari apa yang kau kerjakan. Mulailah dengan latihan pagi.\"",
               "On the grounds of %1$s nobody welcomes or sends away. %4$s nods once: \"Here you are judged by what you do. Begin with the morning drill.\""),
    "iblis": ("Murid-murid %1$s berlatih tanpa suara, seperti kawanan serigala. %4$s menatapmu lama: \"Kau akan dilihat lemah sampai kau membuktikan sebaliknya. Pagi ini, jangan buat aku malu.\"",
              "The disciples of %1$s drill in silence, like a pack of wolves. %4$s looks at you a long while: \"You will be thought weak until you prove otherwise. This morning, do not shame me.\"")})
scene(r, "dapur", "Di dapur, mangkuk nasimu ditepis ke lantai. %3$s tertawa di depan murid-murid lain: \"Murid baru makan sesudah anjing penjaga. Pungut, kalau kau lapar.\"",
      "In the kitchen your rice bowl is knocked to the floor. %3$s laughs in front of the others: \"New disciples eat after the guard dogs. Pick it up, if you are hungry.\"")
ask(r, "dapur", "%3$s menunggu jawabanmu. Semua mata di dapur tertuju padamu.", "%3$s waits for your answer. Every eye in the kitchen is on you.")
opt(r, "balas", "Tantang dia di lei tai", "Challenge him on the lei tai", "Tanding resmi dengan aturan sekte (berhenti di 10 %% nyawa).", "An official bout under the sect's rules (stops at 10 %% health).")
opt(r, "mengalah", "Pungut nasinya dan diam", "Pick up the rice and stay silent", "Telan malu sekarang. Kakak seperguruanmu akan mengingatnya.", "Swallow the shame for now. Your senior will remember it.")
scene(r, "leitai", "Lei tai di tengah lapangan. %3$s melempar jubah luarnya: \"Di sini tidak ada yang akan menolongmu, murid baru.\"",
      "The lei tai in the middle of the ground. %3$s throws off his outer robe: \"Nobody will help you up here, newcomer.\"")
scene(r, "menang", "\"Bagus!\" %4$s tertawa keras. \"Kau mungkin lebih lambat dari dia, tapi kepalamu dingin. Itu yang tidak bisa diajarkan.\"",
      "\"Well done!\" %4$s laughs out loud. \"You may be slower than him, but your head is cool. That cannot be taught.\"")
scene(r, "kalah", "%4$s mengulurkan tangan: \"Bangun. Kau berdiri di lei tai dan tidak lari. Besok kita latih kaki kirimu.\"",
      "%4$s holds out a hand: \"Get up. You stood on the lei tai and did not run. Tomorrow we train your left foot.\"")
scene(r, "mengalah", "Malamnya %4$s membawakan semangkuk nasi hangat ke bilikmu: \"Menahan diri bukan kelemahan. Tapi jangan biarkan itu jadi kebiasaan.\"",
      "That night %4$s brings a warm bowl of rice to your cell: \"Holding back is not weakness. But do not let it become a habit.\"")

# =====================================================================================================================
# 2. Kebun Seratus Obat
r = "kebun"
quest("gabung_kebun", PERAN, [
    S("hilang", ["gabung_titik tetua"], ["gabung.cerita kebun hilang obat corak"], ["gabung.sikap obat 3"], batas=3, gagal="jejak", arah=KOMP),
    S("jejak", ["gabung_titik gerbang", "jam 20 4"], ["gabung.cerita kebun jejak kakak"],
      cabang=[(["gabung.musuh", "acak 50"], "mata_mata")], ke="saingan", batas=3, gagal="saingan", arah=KOMP),
    S("saingan", ["gabung pilih:kebun"], ["gabung.cerita kebun saingan saingan", "gabung.pilihan kebun saingan lapor peras lindungi"],
      cabang=[(["bendera gabung.kebun.lapor"], "lapor_saingan"), (["bendera gabung.kebun.peras"], "peras")], ke="lindungi", batas=3, gagal="lindungi"),
    S("mata_mata", ["gabung pilih:kebun"], ["gabung.cerita kebun mata_mata", "gabung.pilihan kebun mata_mata lapor peras lindungi"],
      cabang=[(["bendera gabung.kebun.lapor"], "lapor_mata"), (["bendera gabung.kebun.peras"], "peras")], ke="lindungi", batas=3, gagal="lindungi"),
    S("lapor_saingan", ["hari 0"], akibat=["gabung.cerita kebun lapor_saingan hukum", "gabung.sikap hukum 10", "gabung.sikap saingan -15", "gabung.pk 40"], ke="selesai"),
    S("lapor_mata", ["hari 0"], akibat=["gabung.cerita kebun lapor_mata hukum", "gabung.sikap hukum 10", "gabung.standing 3", "gabung.standing_musuh -5", "gabung.pk 60"], ke="selesai"),
    S("peras", ["hari 0"], akibat=["gabung.cerita kebun peras", "gabung.infami 3", "beri_nb_pita 15", "gabung.sikap saingan -5"], ke="selesai"),
    S("lindungi", ["hari 0"], akibat=["gabung.cerita kebun lindungi kakak", "gabung.pelanggaran 5", "gabung.sikap kakak 10", "gabung.ikatan 5", "gabung.pk 20"], ke="selesai"),
], ("Kebun Seratus Obat", "The Garden of a Hundred Herbs"),
   ("Herba berharga hilang dari kebun obat sekte dan seorang pelayan dituduh. Siapa pencuri sebenarnya?",
    "Precious herbs vanish from the sect's garden and a servant is blamed. Who is the real thief?"),
   {"hilang": ("Temui Tetua Obat di halaman tetua.", "Meet the Medicine Elder in the elders' court."),
    "jejak": ("Berjaga di gerbang kompleks pada malam hari (jam 20-4).", "Keep watch at the compound gate at night (20-4 o'clock)."),
    "saingan": ("Kau tahu siapa pencurinya. Putuskan apa yang kau lakukan.", "You know who the thief is. Decide what you do."),
    "mata_mata": ("Pencurinya orang luar. Putuskan apa yang kau lakukan.", "The thief is an outsider. Decide what you do."),
    "lapor_saingan": ("Kau melapor ke Balai Hukum.", "You reported to the Hall of Discipline."),
    "lapor_mata": ("Kau melapor ke Balai Hukum.", "You reported to the Hall of Discipline."),
    "peras": ("Kau memeras si pencuri.", "You blackmailed the thief."),
    "lindungi": ("Kau melindungi pelayan itu.", "You shielded the servant.")},
   {"lapor_saingan": ("Saingan dihukum menyapu tangga seribu anak selama sebulan. Pelayan itu dibebaskan dan menangis berterima kasih.",
                      "Your rival is sentenced to sweep the thousand steps for a month. The servant is freed and thanks you in tears."),
    "lapor_mata": ("Mata-mata itu diusir dengan tangan terikat. Sekte berutang padamu, dan musuhnya kini mengenal namamu.",
                   "The spy is driven out with bound hands. The sect owes you, and its enemy now knows your name."),
    "peras": ("Kantongmu lebih berat. Hatimu, mungkin, lebih ringan dari yang seharusnya.",
              "Your purse is heavier. Your heart, perhaps, lighter than it should be."),
    "lindungi": ("Kau menanggung tuduhan sendiri. Catatan pelanggaranmu bertambah, tapi para pelayan kini menyimpan nasi terbaik untukmu.",
                 "You took the blame yourself. Your record grows, but the servants now save the best rice for you.")})
scene_v(r, "hilang", {
    "pedang": ("Tetua Obat di sekte pedang jarang dipedulikan. Kali ini ia memanggilmu sendiri: \"Tiga batang Rumput Embun Perak hilang semalam. Pedang bisa diganti, akar berumur seratus tahun tidak.\"",
               "A Medicine Elder in a sword sect is rarely heeded. This time he calls you himself: \"Three stalks of Silver Dew Grass vanished last night. A sword can be replaced, a hundred-year root cannot.\""),
    "alkimia": ("Wajah Tetua Obat pucat seperti kertas jimat. \"Bunga Api Sembilan Kelopak, bahan pil Fondasi untuk seluruh angkatan, hilang dari petak timur. Tungku tidak bisa menunggu.\"",
                "The Medicine Elder's face is pale as talisman paper. \"The Nine-Petal Fire Flower, the Foundation pill ingredient for a whole intake, is gone from the east bed. The furnace cannot wait.\""),
    "darah": ("Bahkan di %1$s, mencuri dari kebun tetua berarti nyawa. Tetua Obat berbisik: \"Tanaman Darah Naga hilang. Kalau tetua lain tahu lebih dulu, mereka akan mencari kambing hitam.\"",
              "Even in %1$s, stealing from an elder's garden costs a life. The Medicine Elder whispers: \"The Dragon Blood plant is gone. If the other elders learn first, they will look for a scapegoat.\""),
    "buddha": ("Tetua Obat memutar tasbihnya lebih cepat dari biasa. \"Teratai Hati Tenang dicabut sampai akarnya. Seorang pelayan sudah dituduh. Aku tidak yakin dia bersalah.\"",
               "The Medicine Elder turns his beads faster than usual. \"The Calm Heart Lotus was pulled out by the roots. A servant has been accused. I am not sure he is guilty.\""),
    "lurus": ("\"Seratus jenis herba, dan yang paling langka yang hilang,\" keluh Tetua Obat. \"Pelayan tua Ah Bo sudah dikurung. Tolong lihat dengan matamu sendiri, murid.\"",
              "\"A hundred kinds of herbs, and the rarest is gone,\" sighs the Medicine Elder. \"Old servant Ah Bo is already locked up. Please look with your own eyes, disciple.\""),
    "netral": ("Tetua Obat menghitung petak dengan jari. \"Ganoderma Ungu, tiga batang. Harganya bisa membeli satu desa. Seseorang di gunung ini tahu persis apa yang ia ambil.\"",
               "The Medicine Elder counts the beds on his fingers. \"Purple Ganoderma, three stalks. Their price could buy a village. Someone on this mountain knew exactly what he took.\""),
    "iblis": ("\"Di sekte ini mencuri itu biasa,\" kata Tetua Obat dingin, \"tapi mencuri dariku itu bodoh. Cari tahu siapa, lalu kita lihat seberapa berguna dirimu.\"",
              "\"In this sect stealing is common,\" the Medicine Elder says coldly, \"but stealing from me is stupid. Find out who, and we will see how useful you are.\"")})
scene(r, "jejak", "%4$s menunjuk tanah lembap di tepi kebun: \"Jejak sepatu murid, bukan sandal pelayan. Siapa pun dia, ia akan kembali saat bulan tinggi. Tunggu di gerbang.\"",
      "%4$s points at the damp earth by the garden: \"A disciple's boot, not a servant's sandal. Whoever it is will come back when the moon is high. Wait at the gate.\"")
scene(r, "saingan", "Bayangan itu terpeleset di batu gerbang dan kerudungnya jatuh. %3$s! Di balik jubahnya terselip ikatan herba: \"Kau tidak melihat apa-apa, adik, mengerti?\"",
      "The shadow slips on the gate stones and its hood falls. %3$s! A bundle of herbs is tucked under his robe: \"You saw nothing, little brother, understood?\"")
ask(r, "saingan", "Pelayan tua itu masih dikurung karena tuduhan ini.", "The old servant is still locked up for this.")
scene(r, "mata_mata", "Bayangan itu bukan murid %1$s. Lambang di ikat pinggangnya milik %6$s, dan kantongnya penuh herba kebunmu: \"Lepaskan aku dan aku bayar dua kali lipat.\"",
      "The shadow is no disciple of %1$s. The emblem on his belt belongs to %6$s, and his pouch is full of your garden's herbs: \"Let me go and I pay you double.\"")
ask(r, "mata_mata", "Pelayan tua itu masih dikurung karena tuduhan ini.", "The old servant is still locked up for this.")
opt(r, "lapor", "Laporkan ke Balai Hukum", "Report to the Hall of Discipline", "Kebenaran ke %5$s. Pelayan itu bebas.", "The truth to %5$s. The servant goes free.")
opt(r, "peras", "Peras si pencuri", "Blackmail the thief", "Diam, asal dibayar. Infami +3.", "Silence, for a price. Infamy +3.")
opt(r, "lindungi", "Tanggung sendiri tuduhannya", "Take the blame yourself", "Pelayan bebas, catatanmu bertambah. Kakak seperguruan menghargainya.", "The servant goes free, your record grows. Your senior will value it.")
scene(r, "lapor_saingan", "%5$s membaca laporanmu tanpa berkedip: \"Aturan adalah aturan, siapa pun ayahnya.\" Di luar balai, %3$s menatapmu seperti menatap musuh.",
      "%5$s reads your report without blinking: \"Rules are rules, whoever his father is.\" Outside the hall, %3$s looks at you as at an enemy.")
scene(r, "lapor_mata", "%5$s mengangkat lambang %6$s di bawah cahaya lentera: \"Jadi mereka sudah berani masuk sampai kebun kita. Kerja bagus, murid.\"",
      "%5$s holds the emblem of %6$s under the lantern light: \"So they dare to come as far as our garden. Good work, disciple.\"")
scene(r, "peras", "Kantong batu roh berpindah tangan di balik pilar gerbang. Tidak ada yang melihat, kecuali bulan.",
      "A pouch of spirit stones changes hands behind the gate pillar. Nobody sees, except the moon.")
scene(r, "lindungi", "Kau berlutut di Balai Hukum dan mengaku mengambil herba itu. Pelayan tua keluar dari kurungan. %4$s menggeleng pelan, tapi matanya hangat.",
      "You kneel in the Hall of Discipline and confess to taking the herbs. The old servant walks free. %4$s shakes his head slowly, but his eyes are warm.")

# =====================================================================================================================
# 3. Tanah Terlarang Berdarah
r = "tanah_terlarang"
quest("gabung_tanah_terlarang", PERAN, [
    S("berangkat", ["gabung.di_alam"], ["gabung.cerita tanah_terlarang berangkat kakak"], batas=5, gagal="batal"),
    S("khianat", ["gabung pilih:tanah_terlarang"], ["gabung.cerita tanah_terlarang khianat saingan", "gabung.pilihan tanah_terlarang khianat selamatkan tinggalkan"],
      cabang=[(["bendera gabung.tanah_terlarang.selamatkan"], "selamat")], ke="tinggal", batas=2, gagal="tinggal"),
    S("selamat", ["!gabung.di_alam"], akibat=["gabung.cerita tanah_terlarang selamat saingan", "gabung.sikap saingan 40", "gabung.sikap kakak 5", "gabung.pk 50"], ke="selesai"),
    S("tinggal", ["!gabung.di_alam"], akibat=["gabung.cerita tanah_terlarang tinggal kakak", "gabung.infami 2", "gabung.pk 100", "gabung.sikap saingan -30"], ke="selesai"),
    S("batal", ["hari 0"], akibat=["gabung.cerita tanah_terlarang batal kakak"], ke="selesai"),
], ("Tanah Terlarang Berdarah", "The Blood-Stained Forbidden Ground"),
   ("Kuota alam rahasia pertamamu. Kau masuk bersama kakak seperguruan dan saingan, dan tidak semua akan keluar sebagai sahabat.",
    "Your first secret-realm quota. You go in with your senior and your rival, and not everyone will come out a friend."),
   {"berangkat": ("Masuki alam rahasia dengan kuota sektemu.", "Enter the secret realm with your sect's quota."),
    "khianat": ("Saingan menjebakmu, lalu terjebak sendiri. Putuskan.", "Your rival set a trap, then fell into it himself. Decide."),
    "selamat": ("Keluar dari alam rahasia hidup-hidup.", "Leave the secret realm alive."),
    "tinggal": ("Keluar dari alam rahasia hidup-hidup.", "Leave the secret realm alive."),
    "batal": ("Kuota itu tidak kau pakai.", "You did not use the quota.")},
   {"selamat": ("Di gerbang alam, %3$s menunduk di hadapanmu. Sejak hari itu ia memanggilmu saudara, dan bersungguh-sungguh.",
                "At the realm gate, %3$s bows before you. From that day he calls you brother, and means it."),
    "tinggal": ("Kau keluar dengan rampasan kalian berdua. Sekte tidak bertanya. %3$s kembali seminggu kemudian, pincang dan diam.",
                "You came out with both your shares. The sect asks nothing. %3$s returns a week later, limping and silent."),
    "batal": ("Tanah Terlarang menunggu kuota berikutnya.", "The Forbidden Ground waits for the next quota.")})
scene(r, "berangkat", "%4$s membagikan jimat pelindung di depan gerbang alam: \"Di dalam, binatang bukan bahaya terbesar. Yang paling berbahaya adalah orang yang berjalan di sampingmu. Ingat itu.\"",
      "%4$s hands out warding talismans before the realm gate: \"Inside, beasts are not the greatest danger. The most dangerous is the one walking beside you. Remember that.\"")
scene(r, "khianat", "Tanah runtuh di bawah kakimu, jebakan yang dipasang %3$s untukmu. Tapi tanah itu menelan dia juga. Ia tergantung di tepi jurang, tangannya terulur: \"Tolong... aku salah.\"",
      "The ground collapses under your feet, a trap %3$s set for you. But it swallows him too. He hangs at the cliff's edge, hand outstretched: \"Help... I was wrong.\"")
ask(r, "khianat", "Tangan %3$s mulai licin. Kantong herbanya tergantung di pinggangnya.", "The hand of %3$s starts to slip. His herb pouch hangs at his waist.")
opt(r, "selamatkan", "Tarik dia naik", "Pull him up", "Saingan menjadi sahabat. Sikap +40.", "The rival becomes a friend. Attitude +40.")
opt(r, "tinggalkan", "Ambil kantongnya dan pergi", "Take his pouch and leave", "PK +100, Infami +2. Ia akan selamat, tapi tidak akan lupa.", "PK +100, Infamy +2. He will survive, but will not forget.")
scene(r, "selamat", "%3$s terengah di tanah, lalu tertawa getir: \"Aku memasang jebakan untukmu dan kau malah menolongku. Hutang ini kubayar seumur hidup.\"",
      "%3$s gasps on the ground, then laughs bitterly: \"I set a trap for you and you saved me. I will repay this for the rest of my life.\"")
scene(r, "tinggal", "%4$s melihat dua kantong di pinggangmu dan tidak berkata apa-apa. Diamnya lebih berat dari teguran.",
      "%4$s sees the two pouches at your waist and says nothing. His silence weighs more than a scolding.")
scene(r, "batal", "%4$s mengembalikan jimat pelindung ke laci: \"Lain kali, adik. Tanah Terlarang tidak ke mana-mana.\"",
      "%4$s puts the warding talismans back in the drawer: \"Next time, little brother. The Forbidden Ground is not going anywhere.\"")

# =====================================================================================================================
# 4-6. Permintaan Guru (D-41W: each request now has a decision and two or three endings)
# ---- 4. Akar di Urat Roh: pesan -> jaga -> temuan (choice) -> pulang_{semua,sisa,tunas}
r = "guru_herba"
quest("gabung_guru_herba", PERAN, [
    S("pesan", ["pergi gua/urat_roh"], ["gabung.cerita guru_herba pesan shifu"]),
    S("jaga", ["kalahkan elit"], ["gabung.cerita guru_herba jaga"]),
    S("temuan", ["gabung pilih:guru_herba"], ["gabung.cerita guru_herba temuan shifu", "gabung.pilihan guru_herba temuan semua sisakan tunas"],
      cabang=[(["bendera gabung.guru_herba.sisakan"], "pulang_sisa"), (["bendera gabung.guru_herba.tunas"], "pulang_tunas")],
      ke="pulang_semua", batas=3, gagal="pulang_semua"),
    S("pulang_semua", ["gabung temu:shifu"], ["gabung.cerita guru_herba pulang"],
      ["gabung.cerita guru_herba selesai_semua shifu", "gabung.ikatan 8", "gabung.sikap shifu -5", "gabung.pk 90"], ke="selesai", arah=KOMP),
    S("pulang_sisa", ["gabung temu:shifu"], ["gabung.cerita guru_herba pulang"],
      ["gabung.cerita guru_herba selesai_sisa shifu", "gabung.ikatan 22", "gabung.sikap shifu 15", "gabung.pk 60"], ke="selesai", arah=KOMP),
    S("pulang_tunas", ["gabung temu:shifu"], ["gabung.cerita guru_herba pulang"],
      ["gabung.cerita guru_herba selesai_tunas shifu", "gabung.ikatan 12", "gabung.sikap shifu 8", "gabung.sikap obat 12", "gabung.sumber 5",
       "gabung.kabar gabung_tunas_naga", "gabung.pk 70"], ke="selesai", arah=KOMP),
], ("Permintaan Guru: Akar di Urat Roh", "A Teacher's Request: The Root in the Spirit Vein"),
   ("Shifu-mu membutuhkan akar langka yang hanya tumbuh di gua urat roh. Apa yang kau bawa pulang akan memberi tahu gurumu siapa kau.",
    "Your Shifu needs a rare root that grows only in a spirit-vein cave. What you bring home will tell your teacher who you are."),
   {"pesan": ("Temukan gua urat roh.", "Find a spirit-vein cave."),
    "jaga": ("Kalahkan penjaga akar itu.", "Defeat the root's guardian."),
    "temuan": ("Putuskan apa yang kau bawa dari celah batu itu.", "Decide what you take from the crack in the stone."),
    "pulang_semua": ("Bawa akar dan tunasnya kepada Shifu.", "Bring the root and the sprout to your Shifu."),
    "pulang_sisa": ("Bawa akar tuanya kepada Shifu.", "Bring the old root to your Shifu."),
    "pulang_tunas": ("Bawa akar tua dan tunasnya kepada Shifu dan Tetua Obat.", "Bring the old root and the sprout to your Shifu and the Medicine Elder.")},
   {"pulang_semua": ("Shifu-mu menerima dua akar dan tidak marah. Ia hanya diam lebih lama dari biasanya.",
                     "Your Shifu accepts both roots and is not angry. He is only silent longer than usual."),
    "pulang_sisa": ("Akar tua masuk ke kotak giok. Tunasnya tumbuh di gua, menunggu seratus tahun, dan kau yang memilih untuk menunggu.",
                    "The old root goes into a jade box. The sprout grows on in the cave, waiting a hundred years, and you chose to wait."),
    "pulang_tunas": ("Kebun obat sekte kini punya Naga Tidur sendiri. Tetua Obat tidak berhenti menyebut namamu hari itu.",
                     "The sect's medicine garden now has a Sleeping Dragon of its own. The Medicine Elder did not stop saying your name that day.")})
scene(r, "pesan", "\"Akar Naga Tidur,\" kata %2$s sambil membuka gulungan tua. \"Tumbuh hanya di mana urat roh bocor ke batu. Aku terlalu tua untuk merangkak di gua. Kau tidak.\"",
      "\"Sleeping Dragon Root,\" says %2$s, unrolling an old scroll. \"It grows only where a spirit vein leaks into stone. I am too old to crawl through caves. You are not.\"")
scene(r, "jaga", "Akar itu melilit batu yang berdenyut cahaya biru. Dan sesuatu yang besar sudah lama menjaganya.",
      "The root coils around a stone pulsing with blue light. And something large has guarded it for a long time.")
scene(r, "temuan", "Penjaganya roboh dan batu biru itu meredup. Di celahnya tumbuh dua tanaman yang saling melilit: akar tua sebesar lengan bayi, dan di sampingnya satu tunas hijau seukuran jari, masih tersambung benang putih. Kau teringat kata %2$s: \"Urat roh tidak bocor dua kali di tempat yang sama.\"",
      "The guardian falls and the blue stone dims. In its crack grow two plants twined together: an old root as thick as an infant's arm, and beside it a green sprout the size of a finger, still joined by a white thread. You remember what %2$s said: \"A spirit vein does not leak twice in the same place.\"")
ask(r, "temuan", "Tanganmu sudah menyentuh tanah. Apa yang kau bawa pulang?", "Your hand is already on the soil. What do you take home?")
opt(r, "semua", "Cabut akar dan tunasnya", "Pull the root and the sprout",
    "Hasil terbanyak, tapi gua ini tidak akan menumbuhkan apa pun lagi. PK +90, Shifu-mu kecewa (sikap -5).", "The biggest haul, but this cave will grow nothing again. PK +90, your Shifu is disappointed (attitude -5).")
opt(r, "sisakan", "Ambil akar tua, tanam kembali tunasnya", "Take the old root, replant the sprout",
    "Gua ini menumbuhkan akar lagi dalam seratus tahun. Ikatan +22, sikap Shifu +15, PK +60.", "The cave will grow a root again in a hundred years. Bond +22, Shifu attitude +15, PK +60.")
opt(r, "tunas", "Bawa tunasnya ke kebun sekte", "Carry the sprout to the sect's garden",
    "Tunas dibawa bersama tanahnya untuk Tetua Obat. Sikap Tetua Obat +12, sekte mendapat sumber, PK +70.", "The sprout goes with its soil to the Medicine Elder. His attitude +12, the sect gains source, PK +70.")
scene(r, "pulang", "Akar itu hangat di kantongmu, seperti masih bernapas. Shifu-mu menunggu.", "The root is warm in your pouch, as if still breathing. Your Shifu is waiting.")
scene(r, "selesai_semua", "%2$s menimbang dua akar di telapak tangannya, lalu diam lama: \"Dua. Yang kedua itu tunasnya, bukan?\" Ia meletakkannya tanpa amarah. \"Mengambil semua itu mudah. Mengajarimu menyisakan, itu pekerjaanku yang belum selesai.\"",
      "%2$s weighs two roots in his palm, then is silent a long while: \"Two. The second is the sprout, is it not?\" He sets it down without anger. \"Taking everything is easy. Teaching you to leave some is my unfinished work.\"")
scene(r, "selesai_sisa", "%2$s mencium akar tua itu dan memejamkan mata: \"Seratus tahun, tidak kurang.\" Lalu ia mendengar bahwa kau menanam kembali tunasnya, dan matanya terbuka: \"Muridku tidak hanya kuat dan bermata tajam. Ia tahu kapan harus berhenti.\"",
      "%2$s smells the old root and closes his eyes: \"A hundred years, not less.\" Then he hears that you replanted the sprout, and his eyes open: \"My disciple is not only strong and sharp-eyed. He knows when to stop.\"")
scene(r, "selesai_tunas", "%2$s menerima akar tua dengan dua tangan, lalu menimang tunas berbalut tanah itu: \"Kebun kita akan punya Naga Tidur sendiri sebelum aku mati. Kau memberi hadiah yang tak bisa kubalas.\" Ia berangkat ke kebun saat itu juga, lupa menutup pintu.",
      "%2$s receives the old root with both hands, then cradles the sprout wrapped in soil: \"Our garden will have a Sleeping Dragon of its own before I die. You give a gift I cannot repay.\" He leaves for the garden at once, forgetting to close the door.")

# ---- 5. Surat untuk Kawan Lama: surat -> sahabat -> tulis (choice) -> balasan_{sabar,intip}
r = "guru_surat"
quest("gabung_guru_surat", PERAN, [
    S("surat", ["pergi kota/kota_besar"], ["gabung.cerita guru_surat surat shifu"]),
    S("sahabat", ["bicara peran:pustakawan"], ["gabung.cerita guru_surat sahabat"], ke="tulis"),
    S("tulis", ["gabung pilih:guru_surat"], ["gabung.cerita guru_surat tulis", "gabung.pilihan guru_surat tulis sabar intip"],
      cabang=[(["bendera gabung.guru_surat.intip"], "balasan_intip")], ke="balasan_sabar", batas=2, gagal="balasan_sabar"),
    S("balasan_sabar", ["gabung temu:shifu"], ["gabung.cerita guru_surat balasan"],
      ["gabung.cerita guru_surat selesai_sabar shifu", "gabung.ikatan 20", "gabung.sikap shifu 12", "gabung.kabar gabung_kawan_lama", "gabung.pk 40"],
      ke="selesai", arah=KOMP),
    S("balasan_intip", ["gabung temu:shifu"], ["gabung.cerita guru_surat balasan"],
      ["gabung.cerita guru_surat selesai_intip shifu", "gabung.ikatan 8", "gabung.sikap shifu -5", "gabung.pelanggaran 1", "gabung.pk 40"],
      ke="selesai", arah=KOMP),
], ("Permintaan Guru: Surat untuk Kawan Lama", "A Teacher's Request: A Letter to an Old Friend"),
   ("Shifu-mu menitipkan surat untuk seorang kawan lama di perpustakaan ibu kota. Surat itu menyimpan lebih dari sekadar kata.",
    "Your Shifu entrusts you with a letter for an old friend at the capital's library. The letter holds more than words."),
   {"surat": ("Pergi ke ibu kota.", "Travel to the capital."),
    "sahabat": ("Serahkan surat kepada pustakawan.", "Hand the letter to the librarian."),
    "tulis": ("Pustakawan menulis balasan. Putuskan bagaimana kau menunggu.", "The librarian writes a reply. Decide how you wait."),
    "balasan_sabar": ("Bawa balasan dan halaman kitab kepada Shifu.", "Bring the reply and the scripture page to your Shifu."),
    "balasan_intip": ("Bawa balasannya kepada Shifu.", "Bring the reply to your Shifu.")},
   {"balasan_sabar": ("Dua orang tua akan minum teh bersama minggu depan, lima puluh tahun terlambat. Kau yang membuka jalannya.",
                      "Two old men will drink tea together next week, fifty years late. You opened the road."),
    "balasan_intip": ("Surat sampai, dan gurumu tahu kau membaca sebagian. Ia memaafkan, tapi ingat.",
                      "The letter arrives, and your teacher knows you read part of it. He forgives, but remembers.")})
scene(r, "surat", "%2$s menyegel surat dengan lilin hijau: \"Lima puluh tahun aku tidak menulis kepadanya. Kami bertengkar soal satu kalimat kitab. Ternyata dia benar.\"",
      "%2$s seals a letter with green wax: \"Fifty years I have not written to him. We quarrelled over one line of scripture. It turns out he was right.\"")
scene(r, "sahabat", "Di antara rak-rak perpustakaan ibu kota, seorang pustakawan tua mengenali segel itu sebelum kau berbicara.",
      "Among the shelves of the capital's library, an old librarian recognises the seal before you speak.")
scene(r, "tulis", "Pustakawan tua itu membaca surat %2$s dua kali, melepas kacamatanya, dan terdiam lama. \"Tunggu di luar rak. Aku perlu menulis dengan tangan yang tidak gemetar.\" Ia menutup lembar balasan dengan telapak tangan, tapi tintanya basah, dan lembar itu terbuka sedikit ke arahmu.",
      "The old librarian reads the letter of %2$s twice, takes off his spectacles, and is silent a long while. \"Wait outside the shelves. I must write with a hand that does not tremble.\" He covers the reply with his palm, but the ink is wet, and the sheet lies open a little toward you.")
ask(r, "tulis", "Lembar itu tergeletak di meja. Pustakawan sedang menyeduh teh di ruang belakang.", "The sheet lies on the desk. The librarian is brewing tea in the back room.")
opt(r, "sabar", "Tunggu di luar rak", "Wait outside the shelves",
    "Pustakawan mengingat kesabaranmu. Ikatan +20, sikap Shifu +12, PK +40.", "The librarian remembers your patience. Bond +20, Shifu attitude +12, PK +40.")
opt(r, "intip", "Intip balasannya", "Peek at the reply",
    "Kau tahu isinya sebelum Shifu-mu. Ikatan +8, sikap Shifu -5, pelanggaran kecil.", "You know it before your Shifu does. Bond +8, Shifu attitude -5, a minor offence.")
scene(r, "balasan", "Balasan itu hanya satu lembar, ditulis dengan tangan gemetar: \"Datanglah minum teh sebelum salah satu dari kita mati.\"",
      "The reply is a single sheet, written with a trembling hand: \"Come for tea before one of us dies.\"")
scene(r, "selesai_sabar", "%2$s membaca balasan itu dua kali, lalu melipat halaman kitab tua itu ke lengan jubahnya. Matanya basah dan ia tidak menyembunyikannya: \"Teh pertama dalam lima puluh tahun akan diminum minggu depan. Kau membawa lebih dari sepucuk surat, murid.\"",
      "%2$s reads the reply twice, then tucks the old scripture page into his sleeve. His eyes are wet and he does not hide it: \"The first tea in fifty years will be drunk next week. You carried more than a letter, disciple.\"")
scene(r, "selesai_intip", "%2$s membaca balasan itu dan menatapmu lebih lama dari yang perlu: \"Segelnya utuh. Tapi ada bekas jari di sudut lembar ini, dan kau satu-satunya yang menyentuhnya.\" Ia mendengus, setengah tertawa. \"Rahasia orang tua tidak aman pada murid yang ingin tahu. Lain kali, tanyalah dulu.\"",
      "%2$s reads the reply and looks at you longer than needed: \"The seal is whole. But there is a fingermark at the corner of this sheet, and you are the only one who touched it.\" He snorts, half laughing. \"An old man's secrets are not safe with a curious disciple. Next time, ask first.\"")

# ---- 6. Hutang Lama: dendam -> buru -> hadapan (choice) -> lapor_{bunuh,hidup,dusta}
r = "guru_dendam"
quest("gabung_guru_dendam", PERAN, [
    S("dendam", ["pergi pos/kemah_begal"], ["gabung.cerita guru_dendam dendam shifu"]),
    S("buru", ["kalahkan elit"], ["gabung.cerita guru_dendam buru"]),
    S("hadapan", ["gabung pilih:guru_dendam"], ["gabung.cerita guru_dendam hadapan", "gabung.pilihan guru_dendam hadapan bunuh hidup dusta"],
      cabang=[(["bendera gabung.guru_dendam.hidup"], "lapor_hidup"), (["bendera gabung.guru_dendam.dusta"], "lapor_dusta")],
      ke="lapor_bunuh", batas=2, gagal="lapor_dusta"),
    S("lapor_bunuh", ["gabung temu:shifu"], ["gabung.cerita guru_dendam lapor"],
      ["gabung.cerita guru_dendam selesai_bunuh shifu", "gabung.ikatan 15", "gabung.sikap shifu 15", "gabung.infami 2", "gabung.pk 80"], ke="selesai", arah=KOMP),
    S("lapor_hidup", ["gabung temu:shifu"], ["gabung.cerita guru_dendam lapor"],
      ["gabung.cerita guru_dendam selesai_hidup shifu", "gabung.ikatan 20", "gabung.sikap shifu 10", "gabung.pk 60"], ke="selesai", arah=KOMP),
    S("lapor_dusta", ["gabung temu:shifu"], ["gabung.cerita guru_dendam lapor"],
      ["gabung.cerita guru_dendam selesai_dusta shifu", "gabung.ikatan 6", "gabung.sikap shifu 4", "gabung.pelanggaran 2", "gabung.pk 40"], ke="selesai", arah=KOMP),
], ("Permintaan Guru: Hutang Lama", "A Teacher's Request: An Old Debt"),
   ("Shifu-mu menyimpan dendam lama terhadap seorang pengkhianat yang kini memimpin para begal. Pedangmu yang akan memutuskan bagaimana dendam itu berakhir.",
    "Your Shifu holds an old grudge against a traitor who now leads bandits. Your sword will decide how the grudge ends."),
   {"dendam": ("Temukan kemah begal.", "Find a bandit camp."),
    "buru": ("Kalahkan pemimpin mereka.", "Defeat their leader."),
    "hadapan": ("Pemimpin begal itu di ujung pedangmu. Putuskan.", "The bandit leader is at your sword's point. Decide."),
    "lapor_bunuh": ("Laporkan kematiannya kepada Shifu.", "Report his death to your Shifu."),
    "lapor_hidup": ("Bawa pengakuannya kepada Shifu.", "Bring his confession to your Shifu."),
    "lapor_dusta": ("Laporkan kepada Shifu.", "Report to your Shifu.")},
   {"lapor_bunuh": ("Shifu-mu tertawa sampai batuk. Dendam tiga puluh tahun selesai dalam satu malam, oleh muridnya sendiri.",
                    "Your Shifu laughs until he coughs. A thirty-year grudge settled in one night, by his own disciple."),
    "lapor_hidup": ("Dendam itu tidak selesai dengan darah, tapi dengan satu lembar pengakuan. Shifu-mu menyimpannya di bawah bantal.",
                    "The grudge did not end in blood but in one sheet of confession. Your Shifu keeps it under his pillow."),
    "lapor_dusta": ("Kau berbohong kepada gurumu, dan ia memilih untuk percaya. Itu belum tentu sama dengan tertipu.",
                    "You lied to your teacher, and he chose to believe. That is not the same as being fooled.")})
scene(r, "dendam", "%2$s mengelus bekas luka di lehernya: \"Saudara seperguruanku menjual kami kepada sekte lurus, lalu lari membawa kitab kami. Kini ia bersembunyi di antara begal. Bawakan aku kabar kematiannya.\"",
      "%2$s strokes the scar on his neck: \"My sworn brother sold us to a righteous sect, then fled with our scriptures. Now he hides among bandits. Bring me news of his death.\"")
scene(r, "buru", "Di tengah kemah, seorang lelaki tua berpakaian begal memakai jurus yang kau kenal, jurus sektemu sendiri.",
      "In the middle of the camp, an old man in bandit clothes uses a stance you know, your own sect's stance.")
scene(r, "hadapan", "Lelaki tua itu berlutut di tanah, pedangnya patah dua. \"Aku tidak membawa kitab itu. Kitab itu terbakar di tahun kedua. Katakan kepada %2$s apa yang kau mau, kau yang memegang pedangnya.\" Di balik jubah begalnya, kau melihat lambang sektemu dijahit tangan, lusuh karena tiga puluh tahun.",
      "The old man kneels on the ground, his sword broken in two. \"I do not carry the scripture. It burned in the second year. Tell %2$s whatever you want, you hold the sword.\" Beneath his bandit robe you see your own sect's emblem, hand-sewn, worn by thirty years.")
ask(r, "hadapan", "Pedangmu masih di lehernya. Apa yang kau bawa pulang kepada %2$s?", "Your sword is still at his throat. What do you bring home to %2$s?")
opt(r, "bunuh", "Akhiri dia", "End him",
    "Seperti yang diminta Shifu-mu. Ikatan +15, sikap Shifu +15, Infami +2, PK +80.", "As your Shifu asked. Bond +15, Shifu attitude +15, Infamy +2, PK +80.")
opt(r, "hidup", "Ambil pengakuannya, biarkan hidup", "Take his confession, let him live",
    "Kau membawa kata-katanya, bukan kepalanya. Ikatan +20, sikap Shifu +10, PK +60.", "You bring his words, not his head. Bond +20, Shifu attitude +10, PK +60.")
opt(r, "dusta", "Katakan dia sudah mati, lalu lepaskan", "Say he is dead, and let him go",
    "Kau berbohong kepada gurumu. Ikatan +6, pelanggaran kecil, PK +40.", "You lie to your teacher. Bond +6, a minor offence, PK +40.")
scene(r, "lapor", "Kitab yang dicuri itu sudah lama terbakar. Yang tersisa hanya kabar untuk Shifu-mu.", "The stolen scripture burned long ago. All that is left is news for your Shifu.")
scene(r, "selesai_bunuh", "\"Bagus,\" bisik %2$s, dan untuk sesaat wajahnya tampak jauh lebih tua. \"Yang kuat dihormati. Kau kini lebih dari sekadar muridku.\"",
      "\"Good,\" whispers %2$s, and for a moment his face looks much older. \"The strong are respected. You are more than my disciple now.\"")
scene(r, "selesai_hidup", "%2$s membaca pengakuan itu sampai habis, lalu menaruhnya di dada. Lama tak ada suara. \"Tiga puluh tahun aku membencinya, dan ia sudah membenci dirinya sendiri lebih lama dariku.\" Ia menatapmu: \"Kau membawa pulang sesuatu yang lebih berat dari kepalanya. Terima kasih.\"",
      "%2$s reads the confession to the end, then holds it to his chest. For a long time there is no sound. \"For thirty years I hated him, and he hated himself longer than I did.\" He looks at you: \"You brought home something heavier than his head. Thank you.\"")
scene(r, "selesai_dusta", "%2$s mendengar laporanmu dan diam. Lalu ia tersenyum tipis: \"Mati, katamu. Baiklah. Aku sudah terlalu tua untuk menanyakan hal yang tidak ingin kudengar jawabannya.\" Ia tidak bertanya lagi, tapi malam itu lampu biliknya menyala sampai subuh.",
      "%2$s listens to your report and is silent. Then he smiles faintly: \"Dead, you say. Very well. I am too old to ask questions whose answers I do not wish to hear.\" He asks no more, but that night the lamp in his cell burns until dawn.")

# =====================================================================================================================
# 7. Perang Gerbang
r = "perang_gerbang"
quest("gabung_perang_gerbang", PERAN, [
    S("pertanda", ["gabung_titik gerbang"], ["bendera gabung.perang_gerbang.selesai off", "gabung.cerita perang_gerbang pertanda luar corak"],
      batas=2, gagal="bela", arah=KOMP),
    S("bela", ["gabung perang:bela"], ["gabung.cerita perang_gerbang bela luar"], batas=3, gagal="pos", arah=KOMP),
    S("pos", ["gabung perang:pos"], ["gabung.cerita perang_gerbang pos luar"], batas=3, gagal="duel"),
    S("duel", ["gabung perang:duel"], ["gabung.cerita perang_gerbang duel kakak"], batas=4, gagal="akhir"),
    S("akhir", ["bendera gabung.perang_gerbang.selesai"], ["gabung.cerita perang_gerbang akhir"],
      cabang=[(["bendera gabung.perang_gerbang.menang"], "menang")], ke="kalah", batas=12, gagal="kalah"),
    S("menang", ["hari 0"], akibat=["gabung.cerita perang_gerbang menang luar", "gelar pahlawan_gerbang", "gabung.pk 150", "gabung.standing 5"], ke="selesai"),
    S("kalah", ["hari 0"], akibat=["gabung.cerita perang_gerbang kalah ketua", "gabung.pk 50"], ke="selesai"),
], ("Perang Gerbang", "War at the Gate"),
   ("Panggilan perang pertama sektemu. Gerbang harus dijaga, pos lawan harus jatuh, dan seorang juara harus maju.",
    "Your sect's first call to war. The gate must hold, the enemy outpost must fall, and a champion must step forward."),
   {"pertanda": ("Berkumpul di gerbang kompleks.", "Gather at the compound gate."),
    "bela": ("Ikut misi Bela Gerbang.", "Join a Defend the Gate mission."),
    "pos": ("Ikut misi Serbu Pos Depan.", "Join a Storm the Outpost mission."),
    "duel": ("Ikut atau saksikan Duel Juara.", "Fight in or witness the Champions' Duel."),
    "akhir": ("Tunggu akhir perang.", "Wait for the war's end."),
    "menang": ("Sektemu menang.", "Your sect won."),
    "kalah": ("Sektemu kalah.", "Your sect lost.")},
   {"menang": ("Namamu dibacakan di aula utama sebagai Pahlawan Gerbang. Para murid muda menatapmu seperti dulu kau menatap kakak-kakakmu.",
               "Your name is read in the main hall as a Hero of the Gate. The young disciples look at you as you once looked at your seniors."),
    "kalah": ("Panji sekte diturunkan setengah tiang. Tapi gunung yang pernah jatuh bisa bangkit lagi, dan kau ada di sana.",
              "The sect's banner is lowered to half mast. But a mountain that fell can rise again, and you are there.")})
scene_v(r, "pertanda", {
    "pedang": ("Seribu pedang terbang naik dari puncak dan berbaris di langit. %9$s berdiri di gerbang: \"Mereka datang untuk menguji pedang kita. Kita jawab dengan pedang.\"",
               "A thousand flying swords rise from the peak and form ranks in the sky. %9$s stands at the gate: \"They come to test our swords. We answer with swords.\""),
    "alkimia": ("Tungku-tungku besar dipanaskan siang malam untuk pil perang. %9$s membagikan labu pil di gerbang: \"Pil kita lebih banyak dari pedang mereka. Jangan sampai sia-sia.\"",
                "The great furnaces burn day and night for war pills. %9$s hands out pill gourds at the gate: \"We have more pills than they have swords. Do not waste them.\""),
    "darah": ("Genderang kulit dipukul tiga kali. %9$s menjilat bibir: \"Akhirnya. Darah orang lain lebih manis dari darah kita sendiri. Ke gerbang!\"",
              "The hide drum is struck three times. %9$s licks his lips: \"At last. Other men's blood is sweeter than our own. To the gate!\""),
    "buddha": ("Lonceng besar dibunyikan seratus delapan kali. %9$s menunduk: \"Kita tidak mencari perang, tapi kita tidak akan membiarkan gerbang ini dilanggar. Tahan, jangan bunuh bila tak perlu.\"",
               "The great bell tolls a hundred and eight times. %9$s bows his head: \"We do not seek war, but we will not let this gate be breached. Hold, and do not kill without need.\""),
    "lurus": ("Panji %1$s dinaikkan di atas gerbang. %9$s memanggil semua murid: \"%6$s menyatakan perang. Murid Dalam ke atas, wajib ikut. Yang lain, jaga punggung kami.\"",
              "The banner of %1$s is raised above the gate. %9$s summons every disciple: \"%6$s has declared war. Inner disciples and above must take part. The rest, guard our backs.\""),
    "netral": ("%9$s menghitung kas dan murid dengan wajah datar: \"Perang ini mahal. Karena itu kita harus menang cepat. Ke gerbang.\"",
               "%9$s counts the treasury and the disciples with a flat face: \"This war is expensive. So we must win quickly. To the gate.\""),
    "iblis": ("Tengkorak di atas gerbang dinyalakan api hijau. %9$s tersenyum lebar: \"Yang membawa kepala paling banyak boleh memilih teknik di paviliun. Pergilah.\"",
              "The skulls above the gate are lit with green fire. %9$s grins: \"Whoever brings the most heads may choose a technique at the pavilion. Go.\"")})
scene(r, "bela", "Regu pertama lawan terlihat di jalan gunung. %9$s mengangkat tangan: \"Tiga gelombang. Tidak satu pun melewati tangga ini.\"",
      "The enemy's first squad appears on the mountain road. %9$s raises a hand: \"Three waves. Not one passes these steps.\"")
scene(r, "pos", "Peta perang dibentangkan: pos depan %6$s berdiri di tengah jalan antara dua gerbang. %9$s menunjuk: \"Cabut panji mereka, dan semangat mereka ikut tercabut.\"",
      "The war map is unrolled: the outpost of %6$s stands halfway between the two gates. %9$s points: \"Pull down their banner, and their spirit comes down with it.\"")
scene(r, "duel", "Kedua sekte sepakat: satu juara melawan satu juara. %4$s mengikat lengan bajunya: \"Kalau aku jatuh, kau yang maju berikutnya.\"",
      "Both sects agree: one champion against one champion. %4$s ties back his sleeves: \"If I fall, you step up next.\"")
scene(r, "akhir", "Perang belum selesai. Para tetua berunding siang dan malam di aula utama.", "The war is not over. The elders confer day and night in the main hall.")
scene(r, "menang", "%9$s menancapkan panji %6$s yang direbut di depan aula: \"Hari ini, %1$s mengingat nama-nama yang menjaga gerbangnya. Namamu salah satunya.\"",
      "%9$s plants the captured banner of %6$s before the hall: \"Today %1$s remembers the names of those who held its gate. Yours is one of them.\"")
scene(r, "kalah", "%8$s berdiri lama di depan gerbang yang retak: \"Kita kalah perang ini, bukan kalah selamanya. Rawat yang terluka. Gunung ini akan bangkit.\"",
      "%8$s stands long before the cracked gate: \"We lost this war, not forever. Tend the wounded. This mountain will rise.\"")

# =====================================================================================================================
# 8. Dua Topeng
r = "dua_topeng"
quest("gabung_dua_topeng", PERAN, [
    S("panggilan", ["gabung_titik papan_misi"], ["gabung.cerita dua_topeng panggilan luar"], batas=3, gagal="tolak", arah=KOMP),
    S("tawaran", ["gabung pilih:dua_topeng"], ["gabung.cerita dua_topeng tawaran luar", "gabung.pilihan dua_topeng tawaran terima tolak"],
      cabang=[(["bendera gabung.dua_topeng.terima"], "menyusup")], ke="tolak", batas=3, gagal="tolak"),
    S("tolak", ["hari 0"], akibat=["gabung.cerita dua_topeng tolak luar", "gabung.sikap luar -5"], ke="selesai"),
    S("menyusup", ["gabung_titik samaran:gerbang"], ["gabung.samaran mulai", "gabung.cerita dua_topeng menyusup luar"], batas=5, gagal="pulang_kosong",
      arah="sekte/gerbang"),
    S("rahasia", ["gabung pilih:dua_topeng"], ["gabung.cerita dua_topeng rahasia", "gabung.pilihan dua_topeng rahasia bocor berpaling"],
      cabang=[(["bendera gabung.dua_topeng.berpaling"], "berpaling"), (["acak 25"], "ketahuan")], ke="bocor", batas=3, gagal="ketahuan"),
    S("bocor", ["hari 0"], akibat=["gabung.samaran bocor", "gabung.cerita dua_topeng bocor luar", "gabung.pk 300"], ke="selesai"),
    S("ketahuan", ["hari 0"], akibat=["gabung.samaran ketahuan", "gabung.cerita dua_topeng ketahuan"], ke="selesai"),
    S("berpaling", ["hari 0"], akibat=["gabung.cerita dua_topeng berpaling", "gabung.samaran berpaling"], ke="selesai"),
    S("pulang_kosong", ["hari 0"], akibat=["gabung.samaran selesai", "gabung.cerita dua_topeng pulang_kosong luar", "gabung.sikap luar -5"], ke="selesai"),
], ("Dua Topeng", "Two Masks"),
   ("Tetua Luar memintamu menyusup ke sekte iblis sebagai calon murid dengan nama samaran, selama sepuluh hari.",
    "The Outer Elder asks you to infiltrate a demonic sect as a candidate under a false name, for ten days."),
   {"panggilan": ("Temui Tetua Luar di papan misi.", "Meet the Outer Elder at the mission board."),
    "tawaran": ("Terima atau tolak tugas rahasia ini.", "Accept or refuse this secret task."),
    "tolak": ("Kau menolak tugas itu.", "You refused the task."),
    "menyusup": ("Masuki gerbang sekte musuh dengan nama samaran.", "Enter the enemy sect's gate under your false name."),
    "rahasia": ("Kau menemukan rencana serbuan mereka. Putuskan.", "You found their raid plans. Decide."),
    "bocor": ("Rencana itu sampai ke sektemu.", "The plans reached your sect."),
    "ketahuan": ("Penyamaranmu terbongkar.", "Your disguise was uncovered."),
    "berpaling": ("Kau berpaling.", "You turned."),
    "pulang_kosong": ("Kau pulang tanpa hasil.", "You came home empty-handed.")},
   {"tolak": ("Tetua Luar mengangguk dingin. Ada murid lain yang akan pergi.", "The Outer Elder nods coldly. Another disciple will go."),
    "bocor": ("Serbuan mereka disambut jebakan. Hanya segelintir tetua yang tahu siapa yang membawa rahasia itu pulang.",
              "Their raid is met by traps. Only a handful of elders know who brought the secret home."),
    "ketahuan": ("Kau lolos dengan jubah robek dan nama samaran yang kini diburu. Selama sepuluh hari, jangan berjalan sendirian.",
                 "You escape with a torn robe and a false name now hunted. For ten days, do not walk alone."),
    "berpaling": ("Topeng kedua menjadi wajahmu. Gerbang lamamu kini tertutup, dan namamu ditulis di papan buronan mereka.",
                  "The second mask became your face. Your old gate is closed now, and your name is written on their wanted board."),
    "pulang_kosong": ("Sepuluh hari berlalu tanpa hasil. Tetua Luar tidak menghukum, tapi juga tidak lupa.",
                      "Ten days passed with nothing. The Outer Elder does not punish you, but does not forget either.")})
scene(r, "panggilan", "Sebuah gulungan tanpa nama terselip di bawah pintu bilikmu: \"Malam ini, di papan misi. Datang sendiri.\" Tulisannya milik %9$s.",
      "A scroll without a name is slipped under your door: \"Tonight, at the mission board. Come alone.\" The hand is that of %9$s.")
scene(r, "tawaran", "%9$s berbicara pelan, tanpa menoleh: \"%6$s merencanakan sesuatu. Aku butuh seseorang di dalam: calon murid, nama palsu, sepuluh hari. Kalau ketahuan, sekte ini tidak mengenalmu.\"",
      "%9$s speaks softly, without turning: \"%6$s is planning something. I need someone inside: a candidate, a false name, ten days. If you are caught, this sect does not know you.\"")
ask(r, "tawaran", "Sepuluh hari dengan topeng kedua.", "Ten days behind a second mask.")
opt(r, "terima", "Terima tugas", "Accept the task", "Status MENYAMAR 10 hari. Risiko besar, hadiah besar.", "Status DISGUISED for 10 days. Great risk, great reward.")
opt(r, "tolak", "Tolak", "Refuse", "Tetua Luar kecewa (sikap -5).", "The Outer Elder is disappointed (attitude -5).")
scene(r, "tolak", "%9$s menggulung peta tanpa berkata apa-apa, lalu: \"Lupakan malam ini.\"", "%9$s rolls up the map without a word, then: \"Forget tonight.\"")
scene(r, "menyusup", "%9$s memberimu jubah lusuh dan nama baru: \"Mulai sekarang kau calon murid pengembara. Gerbang %6$s ada di arah sana. Jangan menulis surat.\"",
      "%9$s gives you a shabby robe and a new name: \"From now on you are a wandering candidate. The gate of %6$s lies that way. Do not write letters.\"")
scene(r, "rahasia", "Di balik layar aula %6$s kau mendengar para tetua mereka: serbuan ke %1$s dalam tujuh hari, lewat jalan belakang yang tidak dijaga. Tapi seorang tetua mereka juga berkata: \"Calon yang ini berbakat. Kita bisa memakainya.\"",
      "Behind a screen in the hall of %6$s you hear their elders: a raid on %1$s in seven days, through the unguarded back path. But one of them also says: \"This candidate is gifted. We could use him.\"")
ask(r, "rahasia", "Dua jalan, dua sekte, dua wajah.", "Two roads, two sects, two faces.")
opt(r, "bocor", "Bawa rahasia itu pulang", "Bring the secret home", "Sektemu menang perang berikut. 25 %% risiko ketahuan.", "Your sect wins the next war. 25 %% risk of being caught.")
opt(r, "berpaling", "Berpaling ke sekte ini", "Turn to this sect", "Kau menjadi anggota sungguhan di sini, dan pembelot bagi sekte lamamu.", "You become a true member here, and a deserter to your old sect.")
scene(r, "bocor", "%9$s membaca catatanmu di bawah lampu redup, lalu membakarnya: \"Kau tidak pernah pergi ke mana pun. Tapi sekte ini berutang nyawa padamu.\"",
      "%9$s reads your notes under a dim lamp, then burns them: \"You never went anywhere. But this sect owes you its life.\"")
scene(r, "ketahuan", "Seseorang mengenali jurusmu. Teriakan \"Mata-mata!\" menggema di gerbang %6$s, dan kau berlari di bawah hujan anak panah.",
      "Someone recognises your stance. A cry of \"Spy!\" echoes at the gate of %6$s, and you run under a rain of arrows.")
scene(r, "berpaling", "Kau melepas topeng pertama. Para tetua %6$s tersenyum seperti serigala yang mendapat anak serigala baru.",
      "You take off the first mask. The elders of %6$s smile like wolves gaining a new cub.")
scene(r, "pulang_kosong", "%9$s mendengar laporanmu yang kosong: \"Tidak semua penyamaran membuahkan hasil. Yang penting kau pulang.\"",
      "%9$s hears your empty report: \"Not every disguise bears fruit. What matters is that you came back.\"")

# =====================================================================================================================
# 9. Gunung yang Bangkit
r = "gunung_bangkit"
quest("gabung_gunung_bangkit", PERAN, [
    S("kumpul", ["pergi kota x2"], ["gabung.cerita gunung_bangkit kumpul kakak corak"]),
    S("paviliun", ["racik * x3"], ["gabung.cerita gunung_bangkit paviliun ajar"]),
    S("penghina", ["gabung tanding:penghina"], ["gabung.cerita gunung_bangkit penghina penghina", "gabung.tanding penghina"],
      cabang=[(["bendera gabung.tanding.penghina.menang"], "bangkit")], ke="ulang"),
    S("ulang", ["tunggu 1"], akibat=["gabung.cerita gunung_bangkit ulang kakak"], ke="penghina"),
    S("bangkit", ["hari 0"], akibat=["gabung.cerita gunung_bangkit bangkit ketua", "gabung.bangkit", "gabung.pk 200"], ke="selesai"),
], ("Gunung yang Bangkit", "The Mountain That Rises"),
   ("Sektemu merosot: murid-murid tercerai, paviliun kosong, dan sekte lain menertawakan. Saatnya membangkitkan gunung.",
    "Your sect has fallen: disciples scattered, the pavilion empty, other sects laughing. Time to raise the mountain."),
   {"kumpul": ("Cari murid-murid yang tercerai di kota-kota (2 kota).", "Find the scattered disciples in the towns (2 towns)."),
    "paviliun": ("Isi kembali Paviliun Jasa: racik 3 pil.", "Restock the Merit Pavilion: refine 3 pills."),
    "penghina": ("Kalahkan murid sekte lain yang menghina gunungmu.", "Defeat the other sect's disciple who mocks your mountain."),
    "ulang": ("Pulihkan diri, lalu tantang lagi.", "Recover, then challenge him again."),
    "bangkit": ("Gunungmu bangkit.", "Your mountain rises.")},
   {"bangkit": ("Panji sekte dinaikkan kembali sampai puncak tiang. Murid yang dulu pergi berdiri lagi di barisan latihan pagi.",
                "The sect's banner climbs to the top of its pole again. The disciples who left stand once more in the morning drill.")})
scene_v(r, "kumpul", {
    "pedang": ("Rak pedang di lapangan setengah kosong. %4$s menghela napas: \"Mereka pergi membawa pedangnya. Cari mereka di kota, ingatkan sumpah yang pernah mereka ucapkan.\"",
               "The sword racks on the ground stand half empty. %4$s sighs: \"They left with their swords. Find them in the towns, remind them of the oath they once swore.\""),
    "alkimia": ("Separuh tungku sudah dingin berbulan-bulan. %4$s menyapu abu: \"Para alkemis muda pergi ke gilda kota. Bujuk mereka pulang, tungku ini butuh tangan.\"",
                "Half the furnaces have been cold for months. %4$s sweeps the ash: \"The young alchemists went to the town guilds. Coax them home, these furnaces need hands.\""),
    "darah": ("Kolam darah hampir kering. %4$s menggeram: \"Yang lemah lari begitu sekte jatuh. Temukan mereka. Yang mau kembali, kita terima. Yang tidak, biar dunia yang menghukum.\"",
              "The blood pool is nearly dry. %4$s growls: \"The weak ran the moment the sect fell. Find them. Those who return, we accept. Those who do not, let the world punish.\""),
    "buddha": ("Hanya separuh bantal meditasi yang terisi di aula. %4$s tersenyum sedih: \"Mereka tidak jahat, hanya lapar. Pergilah ke kota, bawa mereka pulang dengan kata-kata, bukan tinju.\"",
               "Only half the meditation cushions in the hall are filled. %4$s smiles sadly: \"They are not wicked, only hungry. Go to the towns, bring them home with words, not fists.\""),
    "lurus": ("Barisan latihan pagi tinggal tiga baris. %4$s menatap jalan turun gunung: \"Banyak yang pergi ke kota mencari makan. Mereka tetap saudara kita. Bawa mereka pulang.\"",
              "The morning drill is down to three rows. %4$s stares down the mountain road: \"Many went to the towns to earn a living. They are still our brothers. Bring them home.\""),
    "netral": ("Buku kas %1$s lebih tipis dari buku doa. %4$s berkata datar: \"Murid butuh alasan untuk kembali. Pergilah ke kota, tunjukkan bahwa gunung ini masih punya masa depan.\"",
               "The ledger of %1$s is thinner than a prayer book. %4$s says flatly: \"Disciples need a reason to return. Go to the towns, show them this mountain still has a future.\""),
    "iblis": ("Gerbang %1$s tak lagi ditakuti. %4$s menendang tengkorak yang jatuh: \"Murid kita kabur ke kota seperti tikus. Seret mereka pulang, atau buat mereka menyesal tidak pulang.\"",
              "The gate of %1$s is feared no more. %4$s kicks a fallen skull: \"Our disciples fled to the towns like rats. Drag them home, or make them regret not coming.\"")})
scene(r, "paviliun", "%9$s membuka pintu Paviliun Jasa yang berdebu. Rak-raknya kosong: \"Murid yang pulang butuh pil. Racik, sekecil apa pun mutunya.\"",
      "%9$s opens the dusty doors of the Merit Pavilion. The shelves are bare: \"Returning disciples need pills. Refine some, however low the grade.\"")
scene(r, "penghina", "Seorang murid sekte %6$s menunggu di jalan gunung, menendang batu ke jurang: \"Jadi ini sisa-sisa gunung yang jatuh? Mari kulihat apa yang masih tersisa darimu.\"",
      "A disciple of %6$s waits on the mountain road, kicking stones into the gorge: \"So these are the remnants of the fallen mountain? Let me see what is left of you.\"")
scene(r, "ulang", "%4$s membalut lukamu: \"Gunung tidak bangkit dalam sehari. Besok kita coba lagi.\"", "%4$s binds your wounds: \"A mountain does not rise in a day. Tomorrow we try again.\"")
scene(r, "bangkit", "%8$s berdiri di tangga aula utama dan memukul lonceng sendiri: \"Gunung ini pernah jatuh. Hari ini ia berdiri lagi, berkat murid-muridnya.\"",
      "%8$s stands on the steps of the main hall and strikes the bell himself: \"This mountain once fell. Today it stands again, thanks to its disciples.\"")

# =====================================================================================================================
# 10. Jubah dan Mangkuk
r = "jubah_mangkuk"
quest("gabung_jubah_mangkuk", PERAN, [
    S("wasiat", ["gabung_titik puncak"], ["gabung.cerita jubah_mangkuk wasiat ketua"], arah=KOMP),
    S("ujian", ["gabung tanding:ketua"], ["gabung.cerita jubah_mangkuk ujian ketua", "gabung.tanding ketua"],
      cabang=[(["bendera gabung.tanding.ketua.menang"], "serah")], ke="renung"),
    S("renung", ["tunggu 3"], akibat=["gabung.cerita jubah_mangkuk renung ketua"], ke="ujian"),
    S("serah", ["hari 0"], akibat=["gabung.cerita jubah_mangkuk serah ketua corak", "bendera gabung.jubah_mangkuk.restu", "gabung.sikap ketua 20", "gabung.pk 300"],
      ke="selesai"),
], ("Jubah dan Mangkuk", "The Robe and the Bowl"),
   ("Ketua memanggilmu ke puncak. Sebelum jubah dan mangkuk diserahkan, ia ingin melihat sendiri jalanmu.",
    "The master summons you to the peak. Before the robe and bowl are handed on, he wants to see your path himself."),
   {"wasiat": ("Naik ke puncak menemui Ketua.", "Climb to the peak to meet the master."),
    "ujian": ("Duel Ketua: tunjukkan jalanmu kepada Ketua.", "The Master's Duel: show your path to the master."),
    "renung": ("Renungkan kekalahanmu, lalu kembali.", "Reflect on your defeat, then return."),
    "serah": ("Ketua merestuimu.", "The master gives you his blessing.")},
   {"serah": ("Jubah dan mangkuk kini menunggu suksesi. Para tetua tahu: Ketua sudah memilih.",
              "The robe and bowl now await the succession. The elders know: the master has chosen.")})
scene(r, "wasiat", "Seorang pelayan membawa pesan tanpa segel: \"Ketua menunggumu di puncak sebelum matahari terbenam.\"",
      "A servant brings a message without a seal: \"The master awaits you at the peak before sunset.\"")
scene(r, "ujian", "%8$s menuang dua cangkir teh lalu berdiri: \"Patriark Kelima menyerahkan jubahnya kepada seorang tukang kayu karena satu bait. Aku tidak sebijak itu. Tunjukkan padaku dengan tanganmu.\"",
      "%8$s pours two cups of tea, then stands: \"The Fifth Patriarch gave his robe to a woodcutter for one verse. I am not that wise. Show me with your hands.\"")
scene(r, "renung", "%8$s meneguk tehnya yang sudah dingin: \"Belum. Kembalilah setelah tiga hari, dan bawa sesuatu yang belum kulihat.\"",
      "%8$s sips his cold tea: \"Not yet. Come back after three days, and bring something I have not seen.\"")
scene_v(r, "serah", {
    "pedang": ("%8$s menyarungkan pedangnya dan tertawa: \"Pedangmu sudah melampaui pedangku di satu tempat, hatinya. Jubah ini akan pas di bahumu.\"",
               "%8$s sheathes his sword and laughs: \"Your sword has surpassed mine in one place, its heart. This robe will fit your shoulders.\""),
    "alkimia": ("%8$s mengangkat mangkuk tua yang menghitam oleh api ribuan tungku: \"Mangkuk ini sudah meracik lebih banyak pil daripada umurku. Kini giliranmu.\"",
                "%8$s raises an old bowl blackened by the fire of a thousand furnaces: \"This bowl has refined more pills than my years. Now it is your turn.\""),
    "darah": ("%8$s mengusap darah dari bibirnya dan tersenyum puas: \"Akhirnya ada yang bisa melukaiku. Sekte ini akan ditakuti lagi di tanganmu.\"",
              "%8$s wipes the blood from his lips and smiles with satisfaction: \"At last someone who can wound me. This sect will be feared again in your hands.\""),
    "buddha": ("%8$s merapatkan telapak tangan: \"Kau menahan pukulan terakhirmu. Itulah jawaban yang kucari. Jubah dan mangkuk ini bukan hadiah, melainkan beban.\"",
               "%8$s presses his palms together: \"You held back your last blow. That is the answer I sought. This robe and bowl are not a gift, but a burden.\""),
    "lurus": ("%8$s melipat jubah ketua dengan hati-hati: \"Sekte ini kubangun dengan pedang dan kubesarkan dengan aturan. Jaga keduanya.\"",
              "%8$s folds the master's robe with care: \"I built this sect with the sword and raised it with rules. Guard both.\""),
    "netral": ("%8$s menyerahkan kunci kas dan jubah sekaligus: \"Kekuasaan dan tanggung jawab tidak bisa dipisah. Hitunglah keduanya dengan cermat.\"",
               "%8$s hands over the treasury key and the robe together: \"Power and responsibility cannot be separated. Count both with care.\""),
    "iblis": ("%8$s tertawa sampai menggema di tebing: \"Kau tidak membunuhku walau bisa. Bodoh, atau sangat cerdas. Ambil jubah ini sebelum aku berubah pikiran.\"",
              "%8$s laughs until it echoes off the cliffs: \"You did not kill me though you could. A fool, or very clever. Take this robe before I change my mind.\"")})

# =====================================================================================================================
# 11. Tebus Dosa
r = "tebus_dosa"
quest("gabung_tebus_dosa", PERAN, [
    S("setor", ["gabung tebus:setor"], ["gabung.cerita tebus_dosa setor hukum", "gabung.tebus_setor"], arah="sekte/gerbang"),
    S("duel", ["gabung tanding:hukum"], ["gabung.cerita tebus_dosa duel hukum", "gabung.tanding hukum"],
      cabang=[(["bendera gabung.tanding.hukum.menang"], "ampun")], ke="ulang"),
    S("ulang", ["tunggu 2"], akibat=["gabung.cerita tebus_dosa ulang hukum"], ke="duel"),
    S("ampun", ["hari 0"], akibat=["gabung.cerita tebus_dosa ampun hukum", "gabung.tebus_selesai"], ke="selesai"),
], ("Tebus Dosa", "Atonement"),
   ("Tiga puluh hari sejak kau diusir. Gerbang lama mungkin terbuka lagi, bila kau menebus kesalahanmu.",
    "Thirty days since you were expelled. The old gate may open again, if you atone for your wrong."),
   {"setor": ("Setor tebusan ke kas sekte.", "Pay the atonement into the sect's treasury."),
    "duel": ("Kalahkan Tetua Hukum dalam tanding ramah.", "Defeat the Discipline Elder in a friendly bout."),
    "ulang": ("Coba lagi setelah dua hari.", "Try again after two days."),
    "ampun": ("Kau diampuni.", "You are forgiven.")},
   {"ampun": ("Namamu dicoret dari daftar terusir. Kau boleh melamar lagi, mulai dari Murid Tercatat.",
              "Your name is struck from the list of the expelled. You may apply again, starting as a Registered Disciple.")})
scene(r, "setor", "%5$s menunggu di gerbang, gulungan aturan di tangannya: \"Kau diusir karena alasan yang baik. Kalau ingin kembali, bayar dulu kerugian yang kau timbulkan.\"",
      "%5$s waits at the gate, the scroll of rules in hand: \"You were expelled for good reason. If you wish to return, first pay for the harm you caused.\"")
scene(r, "duel", "%5$s menggulung lengan jubahnya: \"Uang bisa dicari. Tekad tidak. Tunjukkan bahwa kau kembali sebagai orang yang berbeda.\"",
      "%5$s rolls up his sleeves: \"Money can be found. Resolve cannot. Show me you return a different person.\"")
scene(r, "ulang", "%5$s menggeleng: \"Belum cukup. Dua hari lagi.\"", "%5$s shakes his head: \"Not enough. Two more days.\"")
scene(r, "ampun", "%5$s mencoret satu baris di kitab hukumnya: \"Aturan juga mengenal ampun. Jangan buat aku menyesal.\"",
      "%5$s strikes out a line in his book of law: \"The rules know mercy too. Do not make me regret it.\"")
c(C + "tebus_dosa.setor.tanya", "Tebusan untuk %1$s: %2$s NB, nilai barang 100 PK per kelas.", "Atonement to %1$s: %2$s NB, the value of 100 PK of goods per class.")
c(C + "tebus_dosa.pilih.setor", "Setor %s NB", "Pay %s NB")
c(C + "tebus_dosa.pilih.setor.hover", "Dibayar dari batu rohmu ke kas sekte. Tidak bisa dibatalkan.", "Paid from your spirit stones into the sect's treasury. Cannot be undone.")
c(C + "tebus_dosa.kurang", "Batu rohmu kurang. Tebusan: %s NB.", "Not enough spirit stones. Atonement: %s NB.")

# =====================================================================================================================
# card frames, anonymous names, bouts, list, disguise
c("kultivator.cerita.tempat.gabung_sekte", "Sekte", "Sect")
c("kultivator.cerita.gelar.pahlawan_gerbang", "Pahlawan Gerbang", "Hero of the Gate")
c(C + "judul", "%s · %s", "%s · %s")
c(C + "judul_pilih", "Pilihan · %s", "Choice · %s")
c(C + "sikap_naik", "%s: sikap +%s", "%s: attitude +%s")
c(C + "sikap_turun", "%s: sikap -%s", "%s: attitude -%s")
c(C + "pilih.tidak", "Pilihan ini sudah tidak berlaku.", "This choice is no longer open.")
c(C + "pilih.diambil", "Kau memilih: %s", "You chose: %s")
c(C + "daftar.judul", "Kisah sektemu", "Your sect stories")
c(C + "daftar.baris", "%s: %s", "%s: %s")
c(C + "daftar.selesai", "selesai", "finished")
c(C + "daftar.gagal", "gagal", "failed")
c(C + "daftar.kosong", "Belum ada kisah sekte. Kisah datang saat kau menjadi anggota.", "No sect stories yet. They come once you are a member.")
ANON = {"shifu": ("Shifu-mu", "your Shifu"), "kakak": ("kakak seperguruanmu", "your senior"), "saingan": ("saingan sesama murid", "a rival disciple"),
        "ketua": ("Ketua sekte", "the sect master"), "agung": ("Tetua Agung", "the Grand Elder"), "hukum": ("Tetua Hukum", "the Discipline Elder"),
        "luar": ("Tetua Luar", "the Outer Elder"), "ajar": ("Tetua Paviliun", "the Pavilion Elder"), "obat": ("Tetua Obat", "the Medicine Elder"),
        "pusaka": ("Tetua Pusaka", "the Treasury Elder"), "penerimaan": ("Tetua Penerimaan", "the Admissions Elder"), "penghina": ("murid sekte lain", "a disciple of another sect")}
for k, (i, e) in ANON.items():
    c(C + "anon." + k, i, e)
c(C + "anon.penghina_dari", "Murid %s", "Disciple of %s")
TAND = {
    "shifu": (("\"Hm. Kau belajar lebih cepat dari dugaanku.\"", "\"Hm. You learn faster than I expected.\""), ("\"Masih banyak yang harus kuajarkan.\"", "\"There is still much I must teach you.\"")),
    "kakak": (("\"Ha! Adik seperguruanku sudah besar!\"", "\"Ha! My little brother has grown!\""), ("\"Kuda-kudamu masih goyah, adik.\"", "\"Your stance still wobbles, little brother.\"")),
    "saingan": (("\"Ini... hanya keberuntungan!\"", "\"This... was only luck!\""), ("\"Sudah kubilang, murid baru makan terakhir.\"", "\"I told you, newcomers eat last.\"")),
    "ketua": (("\"Bagus. Bagus sekali.\"", "\"Good. Very good.\""), ("\"Belum waktunya.\"", "\"It is not yet time.\"")),
    "agung": (("\"Generasi muda memang menakutkan.\"", "\"The young generation is truly frightening.\""), ("\"Kesabaran, murid.\"", "\"Patience, disciple.\"")),
    "hukum": (("\"Aturan mengakui kekuatanmu.\"", "\"The rules acknowledge your strength.\""), ("\"Aturan adalah aturan.\"", "\"Rules are rules.\"")),
    "luar": (("\"Aku akan mengingat namamu di papan misi.\"", "\"I will remember your name at the mission board.\""), ("\"Lapangan menunggumu besok pagi.\"", "\"The ground awaits you tomorrow morning.\"")),
    "ajar": (("\"Hm, layak satu giok teknik.\"", "\"Hm, worth one technique slip.\""), ("\"Kau berutang pelajaran padaku.\"", "\"You owe me a lesson.\"")),
    "obat": (("\"Aduh, punggungku... kau menang.\"", "\"Ouch, my back... you win.\""), ("\"Minum ini, nanti sembuh.\"", "\"Drink this, it will heal.\"")),
    "pusaka": (("\"Kekalahan ini mahal harganya.\"", "\"This defeat comes at a price.\""), ("\"Gratis, untuk kali ini.\"", "\"Free of charge, this time.\"")),
    "penerimaan": (("\"Aku tidak salah menerimamu.\"", "\"I was not wrong to accept you.\""), ("\"Lebih giat lagi, murid.\"", "\"Work harder, disciple.\"")),
    "penghina": (("\"Gunung ini... belum mati rupanya.\"", "\"This mountain... is not dead after all.\""), ("\"Sisa-sisa gunung jatuh, seperti dugaanku.\"", "\"Remnants of a fallen mountain, as I thought.\""))}
for rel, ((ki, ke), (mi, me)) in TAND.items():
    c(C + "tanding.kalah." + rel, ki, ke)
    c(C + "tanding.menang." + rel, mi, me)
c(C + "tanding.gagal", "Tanding tidak bisa dimulai sekarang (lawan tidak menemukan tempat, atau kau sedang sibuk). Ketik /kultivasi gabung cerita untuk mencoba lagi.",
  "The bout cannot start now (no room for the opponent, or you are busy). Type /kultivasi gabung cerita to try again.")
c(C + "tanding.judul", "Tanding tertunda", "Bout pending")
c(C + "tanding.ulang.tanya", "Tanding melawan %s belum terjadi.", "The bout against %s has not happened yet.")
c(C + "tanding.ulang", "Mulai tanding", "Start the bout")
c(C + "tanding.ulang.hover", "Lawan muncul di depanmu. Aturan sekte: berhenti di 10 %% nyawa.", "Your opponent appears before you. Sect rules: stops at 10 %% health.")
c(C + "samaran.mulai", "Kau menuju %s dengan nama samaran %s. Sepuluh hari. Jangan sampai ketahuan.", "You head for %s under the false name %s. Ten days. Do not get caught.")
c(C + "samaran.habis", "Sepuluh hari penyamaran di %s berakhir. Kau kembali dengan namamu sendiri.", "Ten days of disguise at %s are over. You return under your own name.")
c("kultivator.gabung.riwayat.dunia_samaran", "Menyamar di %s sebagai %s", "Disguised at %s as %s")

# rumours
KAB = [
    ("gabung_diterima", "gabung_diterima", 10, 4, "Kudengar %s baru saja bersumpah di %s. Satu lagi yang memikul air di gunung itu.", "I hear %s just swore the oath at %s. One more carrying water up that mountain."),
    ("gabung_naik_inti", "gabung_naik_inti", 10, 6, "%s kini Murid Inti %s. Cepat sekali naiknya.", "%s is now a Core Disciple of %s. That was a fast climb."),
    ("gabung_naik_pewaris", "gabung_naik_pewaris", 10, 10, "%s dipilih sebagai Pewaris %s. Para tetua tua pasti cemberut.", "%s was chosen as Heir of %s. The old elders must be sulking."),
    ("gabung_naik_tetua", "gabung_naik_tetua", 10, 8, "Orang bilang %s sudah duduk di kursi tetua %s.", "They say %s now sits in an elder's seat at %s."),
    ("gabung_naik_wakil", "gabung_naik_wakil", 10, 10, "%s kini Wakil Ketua %s. Satu langkah lagi dari jubah ketua.", "%s is now Vice Master of %s. One step from the master's robe."),
    ("gabung_naik_ketua", "gabung_naik_ketua", 10, 14, "Lonceng %2$s berdentang semalaman: %1$s menerima jubah dan mangkuk!", "The bells of %2$s rang all night: %1$s received the robe and the bowl!"),
    ("gabung_diusir", "gabung_diusir", 10, 7, "%s diusir dari %s. Lencananya dihancurkan di depan gerbang.", "%s was expelled from %s. The badge was broken before the gate."),
    ("gabung_pembelot", "gabung_pembelot", 10, 10, "Pembelot! %s lari dari %s. Pemburu sekte sudah turun gunung.", "A deserter! %s fled %s. The sect's hunters have come down the mountain."),
    ("gabung_pamit", "gabung_pamit", 6, 4, "%s pamit baik-baik dari %s. Jarang ada yang keluar dengan hormat.", "%s left %s on good terms. Few leave with honour."),
    ("gabung_perang", "gabung_perang", 10, 8, "Perang! %s dan %s saling menyatakan perang. Jalan gunung jadi berbahaya.", "War! %s and %s have declared war. The mountain roads are dangerous now."),
    ("gabung_perang_menang", "gabung_perang_menang", 10, 8, "%s memenangkan perang melawan %s. Panji yang kalah dibakar di depan gerbang.", "%s won the war against %s. The loser's banner was burned before the gate."),
    ("gabung_ketua_baru", "gabung_ketua_baru", 10, 10, "%s punya ketua baru: %s.", "%s has a new master: %s."),
    ("gabung_bangkit", "gabung_bangkit", 10, 10, "Gunung yang jatuh bangkit lagi! Berkat %s, %s kembali dihormati.", "The fallen mountain rises again! Thanks to %s, %s is respected once more."),
    ("gabung_turnamen", "gabung_turnamen", 8, 6, "%s menjuarai Turnamen Murid %s.", "%s won the Disciple Tournament of %s."),
    ("gabung_hari_ujian", "gabung_hari_ujian", 8, 4, "Hari Ujian %s sudah dekat. Anak-anak desa berbondong naik gunung.", "The Test Day of %s is near. Village children flock up the mountain."),
]
KAB.append(("gabung_tunas_naga", "gabung_tunas_naga", 8, 8, "%s membawa pulang tunas Naga Tidur untuk kebun %2$s. Tetua Obatnya tidak tidur semalaman.", "%s brought home a Sleeping Dragon sprout for the garden of %2$s. Its Medicine Elder did not sleep all night."))
KAB.append(("gabung_kawan_lama", "gabung_kawan_lama", 8, 8, "Seorang pustakawan tua di ibu kota katanya menangis membaca surat dari %2$s. Pengantarnya, %1$s, hanya menunggu di luar rak.", "An old librarian in the capital is said to have wept over a letter from %2$s. The courier, %1$s, simply waited outside the shelves."))
for kelas, i, e in [("menengah", "Sekte Menengah", "Middle Sect"), ("besar", "Sekte Besar", "Great Sect"), ("agung", "Tanah Suci", "Holy Land"), ("abadi", "Sekte Abadi", "Immortal Sect")]:
    KAB.append(("gabung_kelas_naik_" + kelas, "gabung_kelas_naik_" + kelas, 10, 10, "%s kini disebut " + i + ". Gerbangnya ramai pelamar.", "%s is now called a " + e + ". Its gate swarms with applicants."))
for kelas, i, e in [("kecil", "perguruan kecil", "minor school"), ("menengah", "sekte menengah", "middle sect"), ("besar", "sekte besar", "great sect"), ("agung", "Tanah Suci", "Holy Land")]:
    KAB.append(("gabung_kelas_turun_" + kelas, "gabung_kelas_turun_" + kelas, 10, 10, "%s merosot menjadi " + i + ". Murid-muridnya mulai turun gunung.", "%s has fallen to a " + e + ". Its disciples start leaving the mountain."))
UMUM = [
    ("gabung_umum_gerbang", "Gerbang %s membuka pendaftaran. Katanya yang diterima diberi Bilik Asrama dan dua pil sebulan.", "The gate of %s is open for applicants. They say those accepted get a dormitory cell and two pills a month."),
    ("gabung_umum_tangga", "Tangga ujian %s katanya seribu anak. Anak tukang kayu kemarin pingsan di anak ke-delapan ratus.", "The test stair of %s has a thousand steps, they say. The carpenter's boy fainted at the eight hundredth."),
    ("gabung_umum_paviliun", "Paviliun Jasa %s menjual giok teknik, tapi hanya kepada anggota. Batu roh tidak laku di sana, hanya poin kontribusi.", "The Merit Pavilion of %s sells technique slips, but only to members. Spirit stones are no good there, only contribution points."),
    ("gabung_umum_tetua", "Tetua Hukum %s terkenal keras. Murid yang mencuri sebatang herba pun dikurung di Gua Renungan.", "The Discipline Elder of %s is known to be harsh. A disciple who stole one herb was confined in the Cave of Reflection."),
    ("gabung_umum_shifu", "Kalau diterima di %s, jangan tolak tetua yang mau jadi Shifu-mu. Guru yang baik lebih berharga dari pusaka.", "If %s accepts you, do not refuse an elder who offers to be your Shifu. A good teacher is worth more than a treasure."),
]
templat = []
for tid, pemicu, bobot, umur, i, e in KAB:
    templat.append({"id": tid, "pemicu": pemicu, "bobot": bobot, "umur_hari": umur, "teks": "kultivator.gabung.kabar." + tid})
    c("kultivator.gabung.kabar." + tid, i, e)
for tid, i, e in UMUM:
    templat.append({"id": tid, "pemicu": "umum", "bobot": 3, "umur_hari": 3, "teks": "kultivator.gabung.kabar." + tid, "isian": ["sekte_terdekat"]})
    c("kultivator.gabung.kabar." + tid, i, e)

# =====================================================================================================================
# sekte_gabung_dunia: titles, mottos, tempers, promises, greetings, statuses, tests
T = "kultivator.gabung.tokoh."
GELAR = [("agung", "Tetua Agung", "Grand Elder"), ("hukum", "Tetua Hukum", "Discipline Elder"), ("luar", "Tetua Luar", "Outer Elder"),
         ("ajar", "Tetua Paviliun", "Pavilion Elder"), ("obat", "Tetua Obat", "Medicine Elder"), ("pusaka", "Tetua Pusaka", "Treasury Elder"),
         ("penerimaan", "Tetua Penerimaan", "Admissions Elder"), ("ketua", "Ketua", "Sect Master")]
for k, i, e in GELAR:
    d(T + "gelar." + k, i + " %s", e + " %s")
    d(T + "papan." + k, i + " · %s", e + " · %s")
SEMBOYAN = {
    "agung": [("\"Gunung yang tinggi tidak terburu-buru.\"", "\"A tall mountain is never in a hurry.\""),
              ("\"Aku sudah melihat banyak murid datang dan pergi. Yang bertahan bukan yang terkuat.\"", "\"I have seen many disciples come and go. Those who stay are not the strongest.\""),
              ("\"Aku tidak peduli caramu, asal sekte ini tetap ditakuti.\"", "\"I do not care how, as long as this sect stays feared.\"")],
    "hukum": [("\"Aturan adalah aturan.\"", "\"Rules are rules.\""), ("\"Aturan ada untuk menjaga keseimbangan, bukan untuk ditakuti.\"", "\"Rules exist to keep the balance, not to be feared.\""),
              ("\"Di sini hanya satu aturan: jangan ketahuan.\"", "\"Here there is only one rule: do not get caught.\"")],
    "luar": [("\"Papan misi tidak pernah kosong. Murid malas, itu lain cerita.\"", "\"The mission board is never empty. Lazy disciples are another story.\""),
             ("\"Setiap misi ada harganya. Setiap harga ada misinya.\"", "\"Every mission has its price. Every price has its mission.\""),
             ("\"Bawa kepala, bawa pulang poin.\"", "\"Bring heads, bring home points.\"")],
    "ajar": [("\"Giok teknik tidak dijual kepada yang belum siap.\"", "\"Technique slips are not sold to the unready.\""),
             ("\"Harga di paviliunku adil. Pelit, mungkin. Tapi adil.\"", "\"Prices in my pavilion are fair. Stingy, perhaps. But fair.\""),
             ("\"Teknik terlarang? Untuk murid yang cukup berguna, mungkin.\"", "\"Forbidden techniques? For a disciple useful enough, perhaps.\"")],
    "obat": [("\"Pil yang baik lebih jujur dari sumpah.\"", "\"A good pill is more honest than an oath.\""), ("\"Eh, tadi aku mau ke mana? Ah, kebun.\"", "\"Eh, where was I going? Ah, the garden.\""),
             ("\"Racun dan obat dibedakan oleh takaran, bukan niat.\"", "\"Poison and medicine differ by the dose, not the intent.\"")],
    "pusaka": [("\"Setiap batu roh di kas punya nama penyumbangnya.\"", "\"Every spirit stone in the treasury bears its giver's name.\""),
               ("\"Gua kediaman bukan hadiah. Itu sewa.\"", "\"A dwelling cave is not a gift. It is rent.\""),
               ("\"Yang tidak membayar sewa tidur di luar bersama anjing.\"", "\"Those who do not pay rent sleep outside with the dogs.\"")],
    "penerimaan": [("\"Gerbang terbuka bagi yang hatinya lurus.\"", "\"The gate is open to those with an upright heart.\""),
                   ("\"Bakat bisa dilatih. Hati tidak.\"", "\"Talent can be trained. A heart cannot.\""),
                   ("\"Aku mencari yang lapar. Yang kenyang tidak akan bertarung.\"", "\"I look for the hungry. The well-fed will not fight.\"")],
    "ketua": [("\"Sekte ini adalah murid-muridnya.\"", "\"This sect is its disciples.\""), ("\"Gunung ini berdiri karena semua tangan memikulnya.\"", "\"This mountain stands because every hand carries it.\""),
              ("\"Yang kuat dihormati. Yang lemah melayani. Itulah jalan kami.\"", "\"The strong are respected. The weak serve. That is our way.\"")]}
for k, rows in SEMBOYAN.items():
    for a, (i, e) in enumerate(rows):
        d(T + "semboyan." + k + "." + str(a), i, e)
WATAK = {"rajin": ("bangun sebelum murid paling rajin, tidur sesudah yang paling malas", "rises before the most diligent disciple, sleeps after the laziest"),
         "malas": ("pelupa dan suka tidur siang, tapi tak pernah salah meracik", "forgetful and fond of naps, but never wrong at a mixture"),
         "sombong": ("bangga pada keluarganya dan tak suka dibantah", "proud of his lineage and dislikes being contradicted"),
         "rendah_hati": ("bicara pelan dan mendengar lebih banyak", "speaks softly and listens more"),
         "setia": ("keras pada aturan, lembut pada murid yang jujur", "hard on the rules, gentle with honest disciples"),
         "licik": ("selalu menghitung untung rugi sebelum tersenyum", "always counts gain and loss before smiling"),
         "berani": ("selalu berdiri paling depan saat gerbang diserang", "always stands in front when the gate is attacked"),
         "penakut": ("berhati-hati sampai dikira penakut", "so careful he is taken for a coward"),
         "ambisius": ("mengincar kursi yang lebih tinggi dan tidak menyembunyikannya", "eyes a higher seat and does not hide it"),
         "tenang": ("tidak pernah meninggikan suara, bahkan di medan perang", "never raises his voice, even on the battlefield"),
         "penyayang": ("mengingat nama setiap murid, bahkan pelayan", "remembers every disciple's name, even the servants'"),
         "pendendam": ("tak pernah lupa siapa yang pernah mempermalukannya", "never forgets who once shamed him")}
for k, (i, e) in WATAK.items():
    d(T + "watak." + k, i, e)
JANJI = {"sword": ("\"Aku akan mengajarimu pedang yang tidak pernah ragu.\"", "\"I will teach you a sword that never hesitates.\""),
         "fire": ("\"Aku punya resep pil yang belum pernah kutunjukkan kepada siapa pun.\"", "\"I have a pill recipe I have never shown anyone.\""),
         "ice": ("\"Hati yang dingin tidak mudah dibakar amarah. Aku akan mengajarimu itu.\"", "\"A cold heart is not easily burned by anger. I will teach you that.\""),
         "lightning": ("\"Petir tidak menunggu. Aku akan mengajarimu bergerak sebelum lawan berpikir.\"", "\"Lightning does not wait. I will teach you to move before the enemy thinks.\""),
         "body": ("\"Tubuhmu akan menjadi pusaka terkuatmu.\"", "\"Your body will become your strongest treasure.\""),
         "life": ("\"Aku akan mengajarimu menyembuhkan sebelum melukai.\"", "\"I will teach you to heal before you harm.\""),
         "shadow": ("\"Yang tak terlihat tak bisa dikalahkan. Ikutlah denganku.\"", "\"What cannot be seen cannot be defeated. Come with me.\""),
         "space": ("\"Formasi adalah dunia kecil. Aku akan mengajarimu membangunnya.\"", "\"A formation is a small world. I will teach you to build one.\"")}
for k, (i, e) in JANJI.items():
    d(T + "janji." + k, i, e)
SAPA = {
    "agung": {"murid": ("Muridku, %1$s. Teruslah begitu.", "My disciple, %1$s. Keep on like this."), "anggota": ("Rajinlah berlatih, murid %2$s.", "Train diligently, disciple of %2$s."),
              "tamu": ("Tamu %2$s, berjalanlah dengan sopan.", "Guest of %2$s, walk with courtesy."), "musuh": ("Kau berani menginjak gunung ini?", "You dare set foot on this mountain?")},
    "hukum": {"murid": ("%1$s, catatanmu bersih. Jaga terus.", "%1$s, your record is clean. Keep it so."), "anggota": ("Ingat aturan %2$s, murid.", "Remember the rules of %2$s, disciple."),
              "tamu": ("Di sini aturan %2$s berlaku juga bagi tamu.", "The rules of %2$s apply to guests too."), "musuh": ("Namamu ada di kitab hukumku.", "Your name is in my book of law.")},
    "luar": {"murid": ("%1$s! Papan misi punya sesuatu untukmu.", "%1$s! The board has something for you."), "anggota": ("Sudah ambil misi hari ini?", "Taken a mission today?"),
             "tamu": ("Mencari pekerjaan? %2$s hanya memberi misi kepada anggota.", "Looking for work? %2$s gives missions only to members."), "musuh": ("Penjaga! Awasi orang ini.", "Guards! Watch this one.")},
    "ajar": {"murid": ("Muridku datang. Ada giok baru di rak.", "My disciple comes. There are new slips on the shelf."), "anggota": ("Paviliun buka. Bawa poinmu.", "The pavilion is open. Bring your points."),
             "tamu": ("Paviliun ini hanya untuk murid %2$s.", "This pavilion is for disciples of %2$s only."), "musuh": ("Tidak ada yang kujual kepadamu.", "I sell nothing to you.")},
    "obat": {"murid": ("Ah, %1$s! Kau sudah makan pil hari ini?", "Ah, %1$s! Have you taken your pill today?"), "anggota": ("Lukamu kenapa? Sini, kuperiksa.", "What happened to you? Here, let me look."),
             "tamu": ("Tamu, jangan injak petak kebun.", "Guest, do not step on the beds."), "musuh": ("Hmph. Racunku tidak pilih-pilih.", "Hmph. My poisons are not choosy.")},
    "pusaka": {"murid": ("Muridku. Sewa gua sudah dibayar?", "My disciple. Is your cave rent paid?"), "anggota": ("Kas sekte menerima sumbangan, murid.", "The treasury accepts donations, disciple."),
               "tamu": ("Tamu tidak boleh dekat gudang pusaka.", "Guests may not come near the treasure store."), "musuh": ("Jauhi kas kami, pencuri.", "Keep away from our treasury, thief.")},
    "penerimaan": {"murid": ("%1$s, aku bangga dulu menerimamu.", "%1$s, I am proud I accepted you."), "anggota": ("Bagaimana hidup di %2$s? Betah?", "How is life in %2$s? Settled in?"),
                   "tamu": ("Ingin melamar ke %2$s? Hari Ujian berikutnya segera tiba.", "Wish to apply to %2$s? The next Test Day is coming."), "musuh": ("Gerbang ini tertutup untukmu.", "This gate is closed to you.")},
    "ketua": {"murid": ("%1$s. Duduklah, minum teh.", "%1$s. Sit, have some tea."), "anggota": ("Kau murid %2$s. Ingat itu ke mana pun kau pergi.", "You are a disciple of %2$s. Remember it wherever you go."),
              "tamu": ("Selamat datang di %2$s.", "Welcome to %2$s."), "musuh": ("Kau masih berani datang?", "You still dare to come?")}}
for k, rels in SAPA.items():
    for rel, (i, e) in rels.items():
        d(T + "sapa." + k + "." + rel, i, e)
d(T + "sapa.chat", "%s: \"%s\"", "%s: \"%s\"")
# ---- D-41W: what the named elders tell of themselves (TokohAnchors.kenang): 4 memories per office, unlocked by attitude
d(T + "cerita.chat", "%s bercerita: \"%s\"", "%s tells you: \"%s\"")
KENANGAN = {
    "agung": [
        ("Waktu aku masih memikul air, Tetua Agung zaman itu menyuruhku menghitung anak tangga setiap pagi. Tiga tahun kemudian aku baru mengerti: ia hanya ingin aku berhenti terburu-buru.",
         "When I still carried water, the Grand Elder of those days made me count the steps every morning. Three years later I understood: he only wanted me to stop hurrying."),
        ("Tiga ketua sudah kulihat naik dan turun dari kursi puncak. Yang pertama terlalu kuat, yang kedua terlalu pintar. Yang ketiga hanya tahu cara mendengarkan, dan sektenya bertahan paling lama.",
         "I have watched three masters rise and fall from the summit seat. The first was too strong, the second too clever. The third only knew how to listen, and his sect lasted longest."),
        ("Pernah ada malam ketika gerbang hampir jebol dan semua orang berteriak minta perintah. Aku duduk dan menyeduh teh. Mereka mengira aku gila. Teh itu kuminum bersama murid yang paling ketakutan, dan ia tak lagi lari sesudahnya.",
         "There was a night the gate nearly broke and everyone shouted for orders. I sat down and brewed tea. They thought me mad. I drank it with the most frightened disciple, and he never ran again."),
        ("Rahasia kecil, murid: tidak ada Tetua Agung yang tidak takut. Bedanya, kami sudah terlalu tua untuk membiarkan rasa takut memilih jalan.",
         "A small secret, disciple: there is no Grand Elder who is not afraid. The difference is that we are too old to let fear choose the road.")],
    "hukum": [
        ("Dua puluh tahun lalu aku mengurung muridku sendiri tiga hari di Gua Renungan karena menyembunyikan satu herba. Ia membenciku setahun. Kini ia memimpin sebuah paviliun dan setiap tahun mengirim satu herba utuh tanpa surat. Itu caranya berkata maaf, dan caraku berkata sudah.",
         "Twenty years ago I confined my own disciple three days in the Cave of Reflection for hiding one herb. He hated me for a year. Now he runs a pavilion and every year sends one whole herb without a letter. That is how he says sorry, and how I say it is done."),
        ("Orang mengira kitab hukum ini tebal karena aturannya banyak. Bukan. Tebal karena setiap halamannya satu kesalahan yang pernah terjadi, dan kutulis supaya tidak terulang.",
         "People think this book of law is thick because the rules are many. It is not. It is thick because every page is a mistake that once happened, and I wrote it down so it would not happen again."),
        ("Aku pernah membatalkan hukuman seorang murid karena ia menutupi saudaranya yang sakit. Kucoret satu baris di kitab. Ketua hanya bertanya apakah aku baik-baik saja. Itu satu-satunya kali kutemukan aturan yang kalah oleh hati, dan aku tidak menyesal.",
         "I once cancelled a disciple's punishment because he covered for his sick brother. I struck a line from the book. The master only asked whether I was well. It was the one time I found a rule beaten by the heart, and I do not regret it."),
        ("Ada satu aturan yang tidak tertulis di kitab: jangan menghukum saat marah. Kalau tanganku gemetar, aku pergi menyapu halaman dulu.",
         "There is one rule not written in the book: never punish in anger. When my hands shake, I go and sweep the courtyard first.")],
    "luar": [
        ("Misi pertamaku dulu mencari kambing hilang. Kambingnya ketemu, tapi pemiliknya tidak percaya aku murid sekte. Aku pulang membawa kambing dan sepotong kue. Rasa kue itu masih kuingat.",
         "My first mission was to find a lost goat. I found the goat, but its owner did not believe I was a sect disciple. I went home with the goat and a piece of cake. I still remember the taste of that cake."),
        ("Papan misi tidak bohong, tapi pemberi misi sering. Bacalah dua kali, tanyakan sekali, dan jangan pernah percaya pada upah yang terlalu bagus.",
         "The mission board does not lie, but the givers often do. Read twice, ask once, and never trust a wage that is too good."),
        ("Aku kehilangan kelingking di pos perbatasan. Anak kecil dari pos itu sekarang memanggilku paman dan membawakan buah. Jari itu harga yang murah.",
         "I lost my little finger at a border post. The small child from that post now calls me uncle and brings me fruit. The finger was a cheap price."),
        ("Murid yang selalu memilih misi teraman tidak akan jadi tetua. Yang selalu memilih tersulit tidak akan hidup sampai jadi tetua. Pilihlah yang ketiga: yang paling kau takuti tapi masih bisa kau tanggung.",
         "A disciple who always takes the safest mission never becomes an elder. One who always takes the hardest never lives to be one. Choose the third: the one you fear most but can still bear.")],
    "ajar": [
        ("Harga teknik di rakku kutetapkan bukan karena aku pelit, tapi karena murid menghargai apa yang mahal. Giok yang gratis tersimpan di laci dan berdebu.",
         "I set the price of techniques on my shelf not out of stinginess, but because disciples value what is dear. A free slip lies in a drawer gathering dust."),
        ("Dulu ada murid mencuri satu giok, lalu mengembalikannya sebulan kemudian dengan catatan: \"Aku sudah menyalinnya, maafkan.\" Aku tidak melapor. Kini ia mengajar di rak sebelah.",
         "A disciple once stole a slip and returned it a month later with a note: \"I copied it, forgive me.\" I did not report him. Now he teaches at the next shelf."),
        ("Hitung baik-baik: di Paviliun ini satu poin berarti satu hari kerja jujur. Aku menjual seratus teknik setahun. Artinya tiap tahun sekte ini menukar seribu hari kerja jujur menjadi kepandaian. Itu bisnis yang kubanggakan.",
         "Count it well: in this Pavilion one point is one day of honest work. I sell a hundred techniques a year. So every year this sect trades a thousand days of honest work for skill. That is a business I am proud of."),
        ("Ada satu giok di dasar peti yang tidak pernah kujual. Bukan teknik tertinggi, tapi tulisan tangan guruku. Kalau suatu hari kau jadi tetua, datanglah. Akan kuberikan gratis.",
         "There is one slip at the bottom of the chest I never sell. Not the highest technique, but my teacher's handwriting. If you become an elder one day, come. I will give it free.")],
    "obat": [
        ("Pertama kali meracik, aku salah menakar dan seluruh asrama tertawa seminggu penuh. Guruku yang mengajari: pil yang salah lebih berharga dari pil yang benar, kalau kau tahu kenapa salahnya.",
         "The first time I brewed, I mismeasured and the whole dormitory laughed for a week. My teacher taught me: a wrong pill is worth more than a right one, if you know why it is wrong."),
        ("Eh, tadi aku mau cerita apa ya? Ah, kebun. Tiga tahun lalu ada herba tumbuh di antara kubis, kukira gulma, kucabut. Ternyata resep seratus tahun. Sampai sekarang aku masih memeriksa kubis.",
         "Eh, what was I going to tell? Ah, the garden. Three years ago a herb grew among the cabbages, I took it for a weed and pulled it. It was a hundred-year recipe. To this day I still inspect the cabbages."),
        ("Satu pesanku: jangan pernah meracik saat marah atau lapar. Aku pernah meracik saat keduanya, dan seluruh sekte tidur dua hari. Ketua memaafkanku sambil menguap.",
         "One piece of advice: never brew when angry or hungry. I once brewed when both, and the whole sect slept two days. The master forgave me while yawning."),
        ("Rahasia terbesar tungku ini bukan apinya, tapi kesabarannya. Pil terbaik yang pernah kubuat memerlukan empat puluh hari menunggu. Aku tertidur tiga kali dan terbangun tepat waktu setiap kalinya. Aneh, ya?",
         "The great secret of this furnace is not its fire but its patience. The best pill I ever made took forty days of waiting. I fell asleep three times and woke exactly on time each time. Strange, is it not?")],
    "pusaka": [
        ("Gua kediaman itu sewa, ya, tapi bayangkan: tanpa sewa, tanpa kas, siapa membeli atap kalau hujan menjebol aula? Aku bukan pelit, aku penjaga atap.",
         "The dwelling cave is rent, yes, but imagine: without rent, without a treasury, who buys the roof when rain breaks through the hall? I am not stingy, I am the keeper of the roof."),
        ("Aku menghitung batu roh tiap malam bukan karena tak percaya orang, tapi karena uang itu bukan milikku: milik mereka yang gugur demi sekte ini.",
         "I count the spirit stones every night not because I distrust people, but because the money is not mine: it belongs to those who fell for this sect."),
        ("Pernah ada tahun paceklik. Kubuka kas dan kubagikan tiga kali lipat dari kebiasaan. Ketua heran. Kujawab: \"Kas adalah bantal. Kalau tidak dipakai saat jatuh, apa gunanya?\"",
         "There was once a famine year. I opened the treasury and handed out three times the usual. The master was surprised. I answered: \"A treasury is a cushion. If it is not used when we fall, what is it for?\""),
        ("Di bawah lantai gudang ada satu peti yang tidak tercatat. Isinya batu roh seorang murid yang gugur di perang, dengan pesan agar dibagikan kepada para pelayan. Setiap tahun kubagikan sedikit demi sedikit.",
         "Under the storehouse floor lies one chest that is not in the ledger. It holds the spirit stones of a disciple who fell in the war, with a note to share them among the servants. Every year I hand out a little.")],
    "penerimaan": [
        ("Aku pernah menerima anak petani karena hanya dialah yang menyisihkan separuh bekalnya untuk anak di sebelahnya, padahal ia sendiri belum makan. Kini ia Murid Inti.",
         "I once accepted a farmer's child because he alone shared half his food with the child beside him, though he had not eaten himself. Now he is a Core Disciple."),
        ("Ujian tangga bukan untuk menyaring yang lemah, tapi untuk melihat bagaimana seseorang berdiri ketika lututnya gemetar. Itulah yang kucatat, bukan jumlah anak tangga.",
         "The stair test is not to sift out the weak, but to see how a person stands when the knees tremble. That is what I record, not the number of steps."),
        ("Ada pelamar yang kutolak, dan ia berdiri di gerbang tiga hari tanpa makan. Hari keempat kubuka gerbang. Bukan karena ia lulus, tapi karena ia sudah menjawab ujianku yang sesungguhnya.",
         "There was an applicant I refused, and he stood at the gate three days without food. On the fourth day I opened it. Not because he passed, but because he had answered my real test."),
        ("Rahasia kecil: separuh tetua di sekte ini dulu pernah kutolak sekali. Mereka kembali. Itulah yang kucari: yang kembali.",
         "A small secret: half the elders of this sect I once turned away. They came back. That is what I look for: those who come back.")],
    "ketua": [
        ("Jubah ini lebih berat dari kelihatannya. Bukan kainnya, tapi nama-nama yang tersulam di dalamnya: satu untuk setiap ketua, dan satu yang masih kosong menunggu penerusku.",
         "This robe is heavier than it looks. Not the cloth, but the names stitched inside: one for every master, and one still empty, waiting for my successor."),
        ("Aku dulu pemanggul air, sama sepertimu. Kau tahu apa yang kuingat dari masa itu? Bukan sakit di bahu, tapi seorang tetua yang menyapaku dengan namaku, bukan dengan \"hei, kau\".",
         "I was once a water-carrier, like you. Do you know what I remember from then? Not the ache in my shoulders, but an elder who greeted me by name, not with \"hey, you\"."),
        ("Setiap malam aku berjalan sendirian mengitari kompleks. Orang mengira aku berjaga. Sebenarnya aku menghitung lampu bilik yang masih menyala dan bertanya: siapa yang tidak bisa tidur, dan kenapa.",
         "Every night I walk alone around the compound. People think I keep watch. In truth I count the lamps still lit in the cells and ask: who cannot sleep, and why."),
        ("Kalau kelak kau duduk di kursi ini, ingatlah: sekte adalah murid-muridnya. Kursi ini hanya tempat paling sepi untuk mendengar mereka.",
         "If you one day sit in this seat, remember: a sect is its disciples. This seat is only the quietest place from which to hear them.")]}
for kantor, rows in KENANGAN.items():
    assert len(rows) == 4
    for n, (i, e) in enumerate(rows):
        d(T + "cerita." + kantor + "." + str(n), i, e)
UCAP = {
    "rajin": ("Halaman ini sudah kusapu dua kali sebelum kau bangun, murid. Itu bukan kebanggaan, itu kebiasaan.", "I swept this courtyard twice before you woke, disciple. That is not pride, it is habit."),
    "malas": ("Jangan bangunkan aku sebelum tengah hari, kecuali ada yang terbakar atau keracunan.", "Do not wake me before noon, unless something is on fire or someone is poisoned."),
    "sombong": ("Keluargaku mengabdi pada gunung ini sebelum nenek moyangmu belajar berjalan. Ingat itu.", "My family served this mountain before your ancestors learned to walk. Remember that."),
    "rendah_hati": ("Aku hanya orang tua yang kebetulan bernapas lebih lama dari yang lain.", "I am only an old man who happened to breathe longer than the others."),
    "setia": ("Gunung ini membesarkanku. Aku akan jatuh bersamanya, kalau perlu.", "This mountain raised me. I will fall with it, if I must."),
    "licik": ("Setiap keramahan ada harganya, murid. Aku hanya jujur soal harga milikku.", "Every kindness has a price, disciple. I am merely honest about mine."),
    "berani": ("Kalau gerbang diserang, aku yang pertama maju. Jangan coba mendahuluiku, nanti kuhukum.", "If the gate is attacked, I go first. Do not try to outrun me, or I will punish you."),
    "penakut": ("Hati-hati di jalan turun. Ada batu longgar. Dan ular. Dan, eh, mungkin hantu.", "Mind the road down. Loose stones. And snakes. And, er, perhaps ghosts."),
    "ambisius": ("Kursi di atasku akan kosong suatu hari. Aku hanya memastikan akulah yang paling siap saat itu tiba.", "The seat above mine will be empty one day. I only make sure I am the readiest when it comes."),
    "tenang": ("Ia menghirup tehnya perlahan dan memandangimu lama tanpa berkata apa-apa. Entah mengapa, kau merasa sudah dijawab.", "He sips his tea slowly and looks at you a long while without a word. Somehow you feel answered."),
    "penyayang": ("Kau kurus, murid. Sudah makan? Ayo, masih ada bubur hangat di dapur.", "You are thin, disciple. Have you eaten? Come, there is still warm porridge in the kitchen."),
    "pendendam": ("Aku tidak pernah lupa siapa yang menertawakanku di hari pertamaku. Dan mereka tahu itu.", "I never forgot who laughed at me on my first day. And they know it.")}
for k, (i, e) in UCAP.items():
    d(T + "ucap." + k, i, e)
d("kultivator.dunia.marker.gabung.sekteku", "Perguruanku", "My Sect")
U = "kultivator.gabung.dunia."
for k, i, e in [("aktif", "menjabat", "in office"), ("pensiun", "Leluhur Hidup", "Living Ancestor"), ("diganti_pemain", "kursi diambil pemain", "seat taken by a player"), ("luka", "terluka", "wounded")]:
    d(U + "status." + k, i, e)
d(U + "uji.mulai", "Kisah %s dimulai.", "Story %s started.")
d(U + "uji.gagal", "Kisah %s tidak bisa dimulai (sudah berjalan, sudah selesai, atau tahap tidak dikenal).", "Story %s could not start (running, finished, or unknown stage).")
d(U + "uji.sekte_tidak", "Sekte %s tidak dikenal.", "Unknown sect %s.")
d(U + "uji.tokoh.judul", "Tokoh %s (%s, corak %s)", "Characters of %s (%s, style %s)")
d(U + "uji.tokoh.baris", "%s %s · %s · %s · %s · usia %s · sikap %s · %s %s", "%s %s · %s · %s · %s · age %s · attitude %s · %s %s")
d(U + "uji.tokoh.roster", "  %s %s (%s) · sikap %s", "  %s %s (%s) · attitude %s")
d(U + "uji.tokoh.milikmu", "Shifu: %s · Kakak: %s · Saingan: %s", "Shifu: %s · Senior: %s · Rival: %s")
d(U + "uji.bertubuh", "[bertubuh]", "[has a body]")
d(U + "uji.titik.judul", "Titik %s: %s dari %s dikenal", "Titik of %s: %s of %s known")
d(U + "uji.tp.tidak", "Titik %2$s sekte %1$s tidak dikenal.", "Titik %2$s of sect %1$s is unknown.")
d(U + "uji.tp.ok", "Teleport ke %s (%s, %s, %s).", "Teleported to %s (%s, %s, %s).")
for k, i, e in [("pikul_air", "pikul air", "carrying water"), ("jaga_malam", "jaga malam", "night watch"), ("pimpin_latihan", "memimpin latihan", "leading the drill"),
                ("kipas_tungku", "kipas tungku", "fanning the furnace")]:
    d("kultivator.hidup.kegiatan." + k, i, e)

# =====================================================================================================================
# write
for qid, obj in QUESTS.items():
    write_json(os.path.join(QDIR, qid + ".json"), obj)
write_json(os.path.join(KDIR, "gabung_sekte.json"), {"templat": templat})

def write_lang(name, rows):
    keys = [k for k, _, _ in rows]
    dup = {k for k in keys if keys.count(k) > 1}
    assert not dup, dup
    for lang, idx in (("id_id", 1), ("en_us", 2)):
        write_json(os.path.join(LDIR, name + "." + lang + ".json"), {r[0]: r[idx] for r in rows})
write_lang("sekte_gabung_cerita", CER)
write_lang("sekte_gabung_dunia", DUN)
print("quests", len(QUESTS), "templates", len(templat), "cerita keys", len(CER), "dunia keys", len(DUN))
