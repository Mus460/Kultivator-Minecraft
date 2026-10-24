"""Lang fragment of module 39E (DESIGN 39.7.3-39.7.5, 39.8, 39.10.4): the Jalan Hidup tab, the board, the diary book, the
notice cards, the bubbles, the commands, the guide chapter 21, the encyclopedia topic "mandiri", the two advancements and
the whole Buku Harian vocabulary (openers by weather / moon / place / hour, core lines of every module's diary kinds,
reflections by trait, closings by goal). Each entry is written once as an (Indonesian, English) pair, so both files always
have identical keys. Placeholders follow Minecraft's %1$s.

Writes lang-src/sekte_mandiri_cerita.id_id.json and .en_us.json (LF, sorted, UTF-8). Run: python -I tools/gen_mandiri_lang.py
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "lang-src")
C = "kultivator.sekte.mandiri.cerita."
H = "kultivator.sekte.mandiri.harian."
ID = {}
EN = {}


def k(key, id_text, en_text):
    if key in ID:
        raise SystemExit("duplicate key " + key)
    ID[key] = id_text
    EN[key] = en_text


def c(key, id_text, en_text):
    k(C + key, id_text, en_text)


def v(base, pairs):
    """Variants base.0 .. base.n-1 (the composer probes them)."""
    for i, (a, b) in enumerate(pairs):
        k(base + "." + str(i), a, b)


def h(key, pairs):
    v(H + key, pairs)


# ==================================================================================================== UI: the tab

c("sumber", "Sumber: Terinspirasi dari murid-murid yang hidup sendiri dalam novel kultivasi (Kaisar Martial Arts, Battle Through the Heavens); angka dari kode.",
  "Source: Inspired by disciples who live their own lives in cultivation novels (Martial Peak, Battle Through the Heavens); numbers from the code.")
c("tab", "Jalan Hidup", "Life Path")
c("tab.short", "Jalan", "Path")
c("tab.memuat", "Membuka catatan murid...", "Opening the disciple's records...")
c("tab.mati", "Murid Mandiri dimatikan di pengaturan server (umum.mandiri / mandiri.aktif).",
  "Autonomous Disciples is turned off in the server settings (umum.mandiri / mandiri.aktif).")
c("tab.bukan_murid", "Hanya murid sekte yang punya jalan hidup sendiri.", "Only sect disciples have a life path of their own.")
for key, a, b in (("ikhtisar", "Ikhtisar", "Overview"), ("harian", "Buku Harian", "Diary"), ("tas", "Dompet & Tas", "Purse & Bag"),
                  ("kebijakan", "Kebijakan", "Policy")):
    c("tab.hal." + key, a, b)
c("tab.hal.ikhtisar.tip", "Jalan hidup, cita-cita, dorongan dan pita 24 jam hari ini.", "Life path, goals, drives and today's 24-hour strip.")
c("tab.hal.harian.tip", "Buku harian yang ditulis murid sendiri dan gulungan perjalanannya.", "The diary the disciple writes and its travel scrolls.")
c("tab.hal.tas.tip", "Uang saku, dompet, sembilan petak tas, rencana belanja, ikatan dan tokoh yang dikenal.",
  "Pocket money, purse, the nine bag slots, the shopping plan, ties and the characters it knows.")
c("tab.hal.kebijakan.tip", "Kebijakan murid ini sendiri, menimpa kebijakan aula.", "This disciple's own policy, over the hall's.")
c("tab.jalan_belum", "Belum memilih jalan", "No path chosen yet")
c("tab.alasan_kosong", "Jalannya masih mencari bentuk.", "Its path is still taking shape.")
c("tab.godaan", "Godaan %1$s/100", "Temptation %1$s/100")
c("tab.godaan.tip", "Dari 60 ke atas murid tergoda Jalan Iblis: ia menyukai jalan pintas dan bisa jatuh. Tegur, ajak menembus bersama, atau lakukan ritual [Tebus].",
  "From 60 up the disciple is tempted by the Demonic Path: it likes shortcuts and may fall. Rebuke it, break through together, or perform the [Redeem] rite.")
c("tab.dorong", "Dorong ke jalan...", "Push to a path...")
c("tab.dorong.tip", "Pilih salah satu dari sembilan jalan. Dorongan Guru menambah 0,8 skor jalan itu sampai dicabut (dorong jalan yang sama lagi untuk mencabut).",
  "Pick one of the nine paths. The master's push adds 0.8 to that path's score until removed (push the current path again to clear it).")
c("tab.dorong.pilih", "Klik: dorong murid ke jalan ini.", "Click: push the disciple toward this path.")
c("tab.dorong.sama", "Jalan sekarang. Klik: cabut semua dorongan.", "The current path. Click: clear every push.")
c("tab.tebus", "Tebus", "Redeem")
c("tab.tebus.tip", "Ritual penebusan di aula (butuh murid di dekatmu, godaan 60 ke atas). Murid kembali dari ambang Jalan Iblis.",
  "The redemption rite in the hall (the disciple near you, temptation 60 or more). It comes back from the edge of the Demonic Path.")
c("tab.cita", "Cita-cita", "Goals")
c("tab.cita_kosong", "Belum ada cita-cita. Murid memilihnya sendiri dari jalan hidup dan kenangannya.",
  "No goals yet. The disciple chooses them from its life path and memories.")
c("tab.cita_prog", "%1$s%%", "%1$s%%")
c("tab.cita_asal", "%1$s%% · %2$s · hari ke-%3$s", "%1$s%% · %2$s · day %3$s")
c("tab.dukung", "Dukung", "Support")
c("tab.dukung.tip", "Jadikan cita-cita utama (prioritas 1).", "Make it the main goal (priority 1).")
c("tab.batalkan", "Batalkan", "Cancel")
c("tab.batalkan.tip", "Batalkan cita-cita ini (tidak bisa untuk Ranah dan Guru). Murid sedikit kecewa.",
  "Cancel this goal (not possible for Realm and Master). The disciple is a little disappointed.")
c("tab.dorongan", "Dorongan", "Drives")
c("tab.hari_ini", "Hari ini", "Today")
c("tab.sedang", "Sedang: %1$s", "Now: %1$s")
c("tab.sedang_sisa", "Sedang: %1$s (sisa %2$s)", "Now: %1$s (%2$s left)")
c("tab.sedang_kosong", "Sedang menimbang apa yang harus dilakukan", "Deciding what to do")
c("tab.sedang_tutup", "Sedang Tutup Diri: pintunya disegel", "In seclusion: its door is sealed")
c("tab.pita.jam", "%1$s - %2$s hari ini", "%1$s - %2$s today")
c("tab.pita.jam_kemarin", "%1$s - %2$s kemarin", "%1$s - %2$s yesterday")
c("tab.pita.larang", "Klik: larang semua kegiatan %1$s", "Click: forbid every %1$s activity")
c("tab.rencana_memuat", "Rencana: membuka buku...", "Plan: opening the book...")
c("tab.rencana_belanja", "Rencana: membeli %1$s× %2$s (paling mahal %3$s)", "Plan: buy %1$s× %2$s (at most %3$s)")
c("tab.rencana_cita", "Rencana: mengejar %1$s", "Plan: pursue %1$s")
c("tab.rencana_kosong", "Rencana: menjalani hari seperti biasa", "Plan: an ordinary day")
c("tab.tegur", "Tegur", "Rebuke")
c("tab.tegur.tip", "Sekali sehari. Godaan Jalan Iblis -6; murid yang sombong tidak menyukainya.",
  "Once a day. Demonic temptation -6; a proud disciple dislikes it.")
c("tab.puji", "Puji", "Praise")
c("tab.puji.tip", "Sekali sehari. Murid merasa dihargai (kepuasan +2).", "Once a day. The disciple feels honoured (contentment +2).")
c("tab.buku", "Buka Buku", "Open Book")
c("tab.buku.tip", "Buku Harian lengkap: 40 catatan terakhir dan gulungan perjalanan.", "The whole diary: the last 40 entries and the travel scrolls.")
c("tab.gulungan", "Gulungan Perjalanan (%1$s)", "Travel Scrolls (%1$s)")
c("tab.ke_harian", "Kembali ke Buku Harian", "Back to the Diary")
c("tab.harian_kosong", "Halaman-halamannya masih kosong. Murid menulis setiap hari yang berarti: belanja di kota, terobosan, pertemuan, kekalahan.",
  "The pages are still empty. The disciple writes every day that matters: town trips, breakthroughs, meetings, defeats.")
c("tab.gulungan_kosong", "Belum ada perjalanan yang selesai.", "No journey has finished yet.")
c("tab.entri_penting", "Catatan penting (pita merah): tidak pernah tergeser catatan biasa.",
  "An important entry (red ribbon): never pushed out by ordinary ones.")
c("tab.entri_biasa", "Catatan biasa.", "An ordinary entry.")
c("tab.dompet", "Dompet", "Purse")
c("tab.dompet.tip", "Uang murid sendiri dari uang saku harian, upah dan hasil karyanya; batasnya naik bersama jenjangnya.",
  "The disciple's own money from daily pocket money, wages and its crafts; the cap grows with its rank.")
c("tab.saku", "Uang saku %1$s%% (≈ %2$s sehari)", "Pocket money %1$s%% (≈ %2$s a day)")
c("tab.saku_pendek", "Uang saku %1$s%%", "Pocket money %1$s%%")
c("tab.saku.tip", "Ubah uang saku murid ini (0-200 %%, langkah 25). Dibayar dari kas aula setiap pagi.",
  "Change this disciple's pocket money (0-200 %%, steps of 25). Paid from the hall's funds every morning.")
c("tab.beri_uang", "Beri uang", "Give money")
c("tab.beri_uang.tip", "Batu roh di tangan utamamu masuk ke dompet murid (sebanyak yang muat). Murid harus di dekatmu.",
  "The spirit stones in your main hand go into the disciple's purse (as many as fit). The disciple must be near.")
c("tab.tas", "Tas (9 petak)", "Bag (9 slots)")
c("tab.tas.ambil", "Klik: ambil ke Guru (kenangan -1, murid suka menyimpan miliknya).", "Click: take it (memory -1, a disciple likes to keep what it bought).")
c("tab.tas.lihat", "Milik murid.", "The disciple's own.")
c("tab.belanja", "Rencana belanja", "Shopping plan")
c("tab.belanja_kosong", "Tidak ada yang perlu dibeli.", "Nothing to buy.")
c("tab.belanja.prio", "Prioritas %1$s", "Priority %1$s")
c("tab.ikatan", "Ikatan terkuat", "Strongest ties")
c("tab.ikatan_kosong", "Belum ada ikatan yang kuat.", "No strong ties yet.")
c("tab.tokoh", "Tokoh yang dikenal", "Characters it knows")
c("tab.tokoh_kosong", "Belum mengenal siapa-siapa di luar sekte. Perjalanan ke kota mempertemukannya dengan pedagang, pengembara, saingan...",
  "It knows no one outside the sect yet. Town trips bring it merchants, wanderers, rivals...")
c("tab.tokoh.jumpa", "Bertemu %1$s kali · sikap %2$s", "Met %1$s times · attitude %2$s")
c("tab.kb.ikut", "Murid ini mengikuti kebijakan aula.", "This disciple follows the hall policy.")
c("tab.kb.tumpang", "Murid ini punya kebijakan sendiri (menimpa aula).", "This disciple has its own policy (over the hall's).")
c("tab.kb.mode", "Mode", "Mode")
c("tab.kb.risiko", "Kehati-hatian", "Caution")
c("tab.kb.izin", "Izin kegiatan", "Allowed activities")
c("tab.kb.klik_larang", "Klik: larang", "Click: forbid")
c("tab.kb.klik_izinkan", "Klik: izinkan", "Click: allow")
c("tab.kb.perintah", "Perintah Guru masih berlaku %1$s", "The master's order still holds for %1$s")
c("tab.kb.perintah_tetap", "Perintah Guru berlaku sampai dicabut", "The master's order holds until lifted")
c("tab.ikut_aula", "Ikut aula", "Follow hall")
c("tab.ikut_aula.tip", "Hapus kebijakan khusus murid ini; ia kembali mengikuti kebijakan aula.",
  "Remove this disciple's own policy; it follows the hall again.")
c("tab.bebaskan", "Bebaskan", "Release")
c("tab.bebaskan.tip", "Akhiri jeda perintah: murid kembali menentukan harinya sendiri.", "End the order pause: the disciple plans its own days again.")
c("waktu_jam", "%1$s jam", "%1$s h")
c("waktu_hari", "%1$s hari", "%1$s days")
for key, a, b in (("akar", "akar roh cocok", "a fitting spirit root"), ("kenangan", "kenangan", "memories"),
                  ("kebiasaan", "kebiasaan", "habit"), ("guru", "dorongan Guru", "the master's push")):
    c("alasan." + key, a, b)
c("alasan.bakat", "bakat %1$s", "%1$s talent")
c("alasan.dao", "Dao %1$s", "the %1$s Dao")
for key, a, b in (("guru", "didukung Guru", "backed by the master"), ("ranah", "langkah ranah berikutnya", "the next realm step"),
                  ("final_kalah", "sejak kalah di final", "since losing a final"), ("duka", "sejak kehilangan saudara", "since losing a sibling"),
                  ("misi", "dari sebuah misi", "from a mission"), ("kalah", "sejak kekalahan itu", "since that defeat"),
                  ("uji", "ditetapkan untuk uji", "set for a test"),
                  ("mandiri_seteru_pisah", "setelah perpisahan lintas sekte", "after a farewell across sects"),
                  ("lain", "dari hatinya sendiri", "from its own heart")):
    c("asal." + key, a, b)
c("asal.jalan", "dari jalan %1$s", "from the %1$s path")
CITA_DESC = {
    "ranah": ("Naik ke langkah ranah berikutnya, sampai Abadi Sejati.", "Rise to the next realm step, all the way to True Immortal."),
    "dao": ("Memahami Dao-nya lebih dalam.", "Comprehend its Dao more deeply."),
    "teknik": ("Menguasai teknik tertentu sampai tingkat tinggi.", "Master a certain technique to a high level."),
    "pusaka": ("Memiliki pusaka yang layak.", "Own a worthy treasure."),
    "juara": ("Menjuarai turnamen sekte.", "Win the sect tournament."),
    "dendam": ("Mengalahkan orang yang pernah mempermalukannya.", "Defeat the one who once humiliated it."),
    "jodoh": ("Menemukan pasangan hati atau pasangan Dao.", "Find a sweetheart or a Dao companion."),
    "guru": ("Melampaui Guru suatu hari nanti.", "Surpass the master some day."),
    "jenjang": ("Naik jenjang di sekte.", "Rise in sect rank."),
    "kekayaan": ("Mengumpulkan kekayaan sendiri.", "Gather a fortune of its own."),
    "binatang": ("Menundukkan binatang roh yang kuat.", "Subdue a strong spirit beast."),
    "rahasia": ("Menjelajah alam rahasia.", "Explore secret realms."),
    "menolong": ("Menolong orang-orang di jalan.", "Help people on the road."),
    "karya": ("Membuat karya bermutu (pil, jimat, senjata).", "Make a fine work (pill, talisman, weapon)."),
}
for key, (a, b) in CITA_DESC.items():
    c("cita.desc." + key, a, b)
DORONG_DESC = {
    "kultivasi": ("Lapar kultivasi: naik saat lama tidak berlatih.", "Hunger to cultivate: rises when it has not trained for a while."),
    "bosan": ("Bosan: naik saat kegiatannya itu-itu saja.", "Boredom: rises when it does the same thing again and again."),
    "lelah": ("Lelah: naik dengan kerja dan latihan, turun saat istirahat.", "Fatigue: rises with work and drills, falls with rest."),
    "sosial": ("Sosial: ingin minum teh, bermain, berbincang.", "Social: wants tea, play and talk."),
    "ambisi": ("Ambisi: dorongan mengejar cita-cita utama.", "Ambition: the push toward its main goal."),
    "barang": ("Barang: ada yang perlu dibeli atau diambil.", "Goods: something needs buying or fetching."),
    "luka": ("Luka: tubuhnya perlu dipulihkan.", "Wounds: its body needs healing."),
}
for key, (a, b) in DORONG_DESC.items():
    c("dorong.desc." + key, a, b)
for key, a, b in (("senang", "Senang", "Happy"), ("tenang", "Tenang", "Calm"), ("lelah", "Lelah", "Tired"), ("kesal", "Kesal", "Annoyed"),
                  ("sedih", "Sedih", "Sad"), ("bangga", "Bangga", "Proud"), ("tergoda", "Tergoda", "Tempted")):
    c("suasana." + key, a, b)
for key, a, b in (("penuh", "Murid menentukan sendiri hampir semuanya; terobosan dan Dao tanpa menunggu Guru.",
                   "Disciples decide almost everything; breakthroughs and Dao without waiting for the master."),
                  ("seimbang", "Murid hidup sendiri, tetapi terobosan dan Dao menunggu Guru beberapa hari.",
                   "Disciples live on their own, but breakthroughs and Dao wait some days for the master."),
                  ("patuh", "Murid hanya berlatih di aula dan menunggu perintah.", "Disciples only train in the hall and wait for orders.")):
    c("mode.desc." + key, a, b)
for key, a, b in (("aman", "Tidak mau mengambil risiko: mundur dari bahaya, tidak menembus sebelum siap penuh.",
                   "Takes no risks: backs away from danger, never breaks through before fully ready."),
                  ("wajar", "Hati-hati yang wajar (bawaan).", "Sensible caution (default)."),
                  ("berani", "Berani: perjalanan lebih jauh, lawan lebih kuat.", "Bold: longer journeys, stronger foes."),
                  ("nekat", "Nekat: mengejar hasil besar walau berisiko luka.", "Reckless: chases big rewards even at risk of wounds.")):
    c("kehatian.desc." + key, a, b)
IZIN_DESC = {
    "kultivasi": ("Meditasi, Tutup Diri, merenung, menyerap batu roh.", "Meditation, seclusion, contemplation, absorbing spirit stones."),
    "latihan": ("Latihan jurus sendiri di lapangan.", "Drilling techniques alone in the yard."),
    "tanding": ("Bertanding dengan saudara seperguruan.", "Sparring with fellow disciples."),
    "belajar": ("Membaca kitab dan mengajar adik seperguruan.", "Reading scriptures and teaching juniors."),
    "kerajinan": ("Meramu pil dan menulis jimat.", "Refining pills and writing talismans."),
    "kerja": ("Kebun, tambang dan pos tugas.", "Gardens, mines and duty posts."),
    "sosial": ("Teh, makan bersama, bermain.", "Tea, shared meals, play."),
    "jaga": ("Menjaga formasi dan gerbang.", "Guarding formations and the gate."),
    "belanja": ("Turun ke kota membeli perbekalan.", "Going down to town for supplies."),
    "misi_gilda": ("Mengambil misi di Gilda Kultivator.", "Taking missions at the Cultivator Guild."),
    "berburu": ("Berburu binatang roh.", "Hunting spirit beasts."),
    "reruntuhan": ("Menjelajah reruntuhan.", "Exploring ruins."),
    "gua": ("Masuk gua dan sarang.", "Entering caves and lairs."),
    "alam_rahasia": ("Masuk alam rahasia (bawaan: dilarang).", "Entering secret realms (default: forbidden)."),
    "berkelana": ("Berkelana jauh berhari-hari.", "Wandering far for days."),
    "desa_sekte": ("Mengunjungi desa dan kompleks sekte lain.", "Visiting villages and other sects' compounds."),
}
for key, (a, b) in IZIN_DESC.items():
    c("izin.desc." + key, a, b)
for key, a, b in (("biasa", "Wataknya biasa saja.", "An ordinary temperament."), ("lembut", "Berhati lembut.", "Gentle-hearted."),
                  ("sombong", "Sombong dan suka menantang.", "Proud and fond of challenges."), ("ramah", "Ramah dan suka bercerita.", "Friendly and talkative."),
                  ("licik", "Licik; jangan lengah.", "Sly; stay alert."), ("tenang", "Tenang seperti air sumur tua.", "Calm as water in an old well.")):
    c("watak." + key, a, b)

# ==================================================================================================== UI: the book

c("buku.judul", "Buku Harian", "Diary")
c("buku.sub", "Jalan %1$s · %2$s catatan · %3$s gulungan", "%1$s path · %2$s entries · %3$s scrolls")
c("buku.cermin", "Tubuhnya tidak dimuat: catatan terakhir yang diketahui aula.", "Its body is not loaded: the last record the hall knows.")
c("buku.pita_harian", "Buku Harian", "Diary")
c("buku.pita_gulungan", "Gulungan Perjalanan", "Travel Scrolls")
c("gulungan.judul", "%1$s, berangkat hari ke-%2$s", "%1$s, set out on day %2$s")
c("gulungan.tujuan", "Tujuan: %1$s", "Destination: %1$s")
c("harian.kepala", "Hari ke-%1$s, %2$s.", "Day %1$s, %2$s.")
for key, a, b in (("fajar", "fajar", "dawn"), ("pagi", "pagi", "morning"), ("siang", "siang", "noon"), ("sore", "sore", "afternoon"),
                  ("senja", "senja", "dusk"), ("malam", "malam", "night"), ("malam_purnama", "malam purnama", "night of the full moon"),
                  ("malam_gelap", "malam tanpa bulan", "moonless night")):
    c("waktu." + key, a, b)
JENIS = {"belanja": ("belanja", "shopping"), "tawar": ("tawar-menawar", "haggling"), "copet": ("pencopet", "a pickpocket"), "makan": ("makan", "meals"),
         "teh": ("minum teh", "tea"), "latih": ("latihan jurus", "drills"), "tanding": ("tanding", "sparring"), "tutup": ("Tutup Diri", "seclusion"),
         "tembus": ("terobosan", "breakthroughs"), "hati": ("Ujian Hati", "the Heart Trial"), "dao": ("Dao", "the Dao"), "belajar": ("belajar", "study"),
         "ajar": ("mengajar", "teaching"), "meramu": ("meramu pil", "pill refining"), "jimat": ("menulis jimat", "talismans"), "kebun": ("kebun", "the garden"),
         "tambang": ("tambang", "the mine"), "jalan": ("perjalanan", "the journey"), "pulang": ("pulang", "coming home"), "hadiah": ("hadiah", "gifts"),
         "iblis": ("bisikan iblis", "demonic whispers"), "tokoh": ("pertemuan", "a meeting"), "cita": ("cita-cita", "a goal"), "kota": ("kota", "the town"),
         "kejar": ("hari-hari yang lewat", "the days gone by"), "saku": ("uang saku", "pocket money"), "guru": ("Guru", "the master"),
         "renung": ("merenung", "contemplation"), "serap": ("batu roh", "spirit stones"), "pil": ("pil", "pills"), "beli": ("membeli", "buying"),
         "hufa": ("menjaga sahabat", "guarding a friend"), "jenjang": ("jenjang sekte", "sect rank"), "natal": ("pusaka natal", "the natal treasure"),
         "pos": ("pos tugas", "duty posts"), "formasi": ("formasi", "formations"), "tawanan": ("tawanan", "captivity"), "kesempatan": ("peluang", "a chance"),
         "seteru": ("perseteruan", "a feud"), "lain": ("urusan sehari-hari", "daily matters")}
for key, (a, b) in JENIS.items():
    c("jenis." + key, a, b)
TARGET = {"dao": ("Dao %1$s", "the %1$s Dao"), "teknik": ("teknik %1$s", "the %1$s technique"), "pusaka": ("pusaka tingkat %1$s", "a grade %1$s treasure"),
          "juara": ("gelar juara %1$s", "%1$s titles"), "dendam": ("mengalahkan %1$s", "defeating %1$s"), "jodoh": ("pasangan hati", "a sweetheart"),
          "guru": ("melampaui Guru", "surpassing the master"), "kekayaan": ("%1$s NB", "%1$s NB"), "binatang": ("binatang roh tingkat %1$s", "a tier %1$s spirit beast"),
          "rahasia": ("alam rahasia tingkat %1$s", "a tier %1$s secret realm"), "menolong": ("%1$s pertolongan", "%1$s good deeds"),
          "karya": ("karya mutu %1$s", "a grade %1$s work")}
for key, (a, b) in TARGET.items():
    c("target." + key, a, b)
c("tak_dikenal", "seseorang", "someone")

# ==================================================================================================== UI: the board

c("papan.judul", "Jalan Hidup Murid", "Disciples' Life Paths")
c("papan.hari", "Hari ke-%1$s, pukul %2$s", "Day %1$s, hour %2$s")
c("papan.memuat", "Mengumpulkan kabar dari seluruh aula...", "Gathering news from the whole hall...")
c("papan.tanpa_aula", "Kamu belum punya Prasasti Aula Sekte. Murid mandiri hidup di sekte milikmu sendiri.",
  "You have no Sect Hall stele yet. Autonomous disciples live in a sect of your own.")
for key, a, b, ta, tb in (("murid", "Murid", "Disciples", "Semua murid: yang dimuat, yang bepergian dan yang terakhir terlihat.",
                           "Every disciple: loaded, travelling and last seen."),
                          ("kabar", "Kabar", "News", "30 peristiwa terakhir aula.", "The hall's last 30 events."),
                          ("kebijakan", "Kebijakan Aula", "Hall Policy", "Kebijakan yang diikuti semua murid tanpa kebijakan sendiri.",
                           "The policy every disciple without its own follows."),
                          ("kas", "Kas", "Funds", "Uang saku tujuh hari dan buku kas murid.", "Seven days of pocket money and the disciples' money book.")):
    c("papan.tampil." + key, a, b)
    c("papan.tampil." + key + ".tip", ta, tb)
for key, a, b in (("semua", "Semua (%1$s)", "All (%1$s)"), ("pergi", "Bepergian (%1$s)", "Away (%1$s)"), ("luka", "Terluka (%1$s)", "Wounded (%1$s)"),
                  ("siap", "Siap menembus (%1$s)", "Ready (%1$s)"), ("tergoda", "Tergoda (%1$s)", "Tempted (%1$s)")):
    c("papan.saring." + key, a, b)
for key, a, b in (("nama", "Murid & jalan", "Disciple & path"), ("kegiatan", "Kegiatan / tempat", "Activity / place"), ("ranah", "Ranah", "Realm"),
                  ("cita", "Cita-cita utama", "Main goal"), ("dompet", "Dompet", "Purse")):
    c("papan.kol." + key, a, b)
c("papan.kosong", "Aula belum punya murid.", "The hall has no disciples yet.")
c("papan.saring_kosong", "Tidak ada murid yang cocok dengan saringan ini.", "No disciple matches this filter.")
c("papan.pergi", "Dalam %1$s", "On %1$s")
c("papan.pergi_pulang", "Dalam %1$s, pulang ±%2$s hari", "On %1$s, home in about %2$s days")
c("papan.tutup", "Tutup Diri hari %1$s/%2$s", "Seclusion day %1$s/%2$s")
c("papan.terakhir", "%1$s (terakhir terlihat)", "%1$s (last seen)")
c("papan.jauh", "%1$s (jauh)", "%1$s (far)")
c("papan.terakhir_dilihat", "Tubuhnya tidak dimuat; baris ini catatan terakhir aula.", "Its body is not loaded; this row is the hall's last record.")
c("papan.klik_buka", "Klik: buka gulungan murid pada tab Jalan Hidup.", "Click: open the disciple scroll on its Life Path tab.")
c("papan.klik_buku", "Klik: buka Buku Hariannya.", "Click: open its diary.")
c("papan.siap", "Siap menembus", "Ready")
c("papan.tergoda", "Tergoda", "Tempted")
c("papan.perintah", "Menjalankan perintah", "Following an order")
for key, a, b in (("other", "urusan di luar", "an errand away"), ("mandiri_jalan", "perjalanan", "a journey"), ("mandiri_seteru", "urusan perseteruan", "feud business"),
                  ("alam", "alam rahasia", "a secret realm"), ("kafilah", "pengawalan kafilah", "caravan escort"), ("musuh_terluka", "pemulihan luka", "healing wounds"),
                  ("soul_flight", "perjalanan jiwa", "a soul's flight"), ("misi", "misi sekte", "a sect mission"), ("mission", "misi sekte", "a sect mission")):
    c("papan.alasan." + key, a, b)
c("papan.kabar_kosong", "Belum ada kabar.", "No news yet.")
c("papan.kabar_waktu", "Hari %1$s, %2$s:00", "Day %1$s, %2$s:00")
c("papan.kabar_hari", "Hari %1$s", "Day %1$s")
c("papan.kas.saku7", "Uang saku 7 hari terakhir: %1$s", "Pocket money, last 7 days: %1$s")
c("papan.kas.pool", "Poin kontribusi aula: %1$s", "Hall contribution points: %1$s")
c("papan.kas.kosong", "Buku kas masih kosong.", "The money book is still empty.")
c("papan.kb.saku", "Uang saku aula: %1$s%%", "Hall pocket money: %1$s%%")
c("papan.kb.saku.tip", "Persentase uang saku harian semua murid menurut jenjangnya (0-200 %%).", "The daily pocket money of every disciple by rank (0-200 %%).")
c("papan.kb.titipan", "Titipan belanja aula: %1$s/minggu", "Hall errand money: %1$s/week")
c("papan.kb.titipan.tip", "Uang mingguan aula untuk titipan yang dibeli murid di kota (pil untuk Lumbung, bahan).",
  "The hall's weekly money for errands disciples buy in town (pills for the Granary, materials).")
c("papan.kb.tunggu_tembus", "Terobosan menunggu Guru: %1$s hari", "Breakthroughs wait for the master: %1$s days")
c("papan.kb.tunggu_tembus.tip", "Murid yang siap menembus menunggu Guru selama ini sebelum mencoba sendiri.",
  "A disciple ready to break through waits this long for the master before trying alone.")
c("papan.kb.tunggu_dao", "Memilih Dao menunggu Guru: %1$s hari", "Dao choice waits for the master: %1$s days")
c("papan.kb.tunggu_dao.tip", "Murid menunggu Guru selama ini sebelum memilih Dao-nya sendiri.", "A disciple waits this long before choosing its own Dao.")
c("papan.kb.jenjang", "Persetujuan jenjang", "Rank approval")
for key, a, b, ta, tb in (("tetua_agung", "Tetua Agung", "Grand Elder", "Tetua Agung menyetujui permohonan jenjang (bila ada), selain itu Guru.",
                           "The Grand Elder approves rank requests (if the hall has one), else the master."),
                          ("otomatis", "Otomatis", "Automatic", "Permohonan jenjang langsung disetujui.", "Rank requests are approved at once."),
                          ("tanya", "Tanya Guru", "Ask master", "Selalu bertanya kepada Guru lewat obrolan.", "Always asks the master in chat.")):
    c("papan.kb.jenjang." + key, a, b)
    c("papan.kb.jenjang." + key + ".tip", ta, tb)
c("papan.kb.seteru", "Sikap perseteruan", "Feud stance")
for key, a, b, ta, tb in (("selalu_damaikan", "Selalu damaikan", "Always settle", "Aula selalu mendamaikan perseteruan sejak awal.",
                           "The hall always settles feuds early."),
                          ("tanya", "Tanya Guru", "Ask master", "Bawaan: Guru ditanya mulai tingkat 2.", "Default: the master is asked from step 2."),
                          ("biarkan", "Biarkan", "Let it be", "Murid menyelesaikan sendiri (tetap tanpa perang dan tanpa kematian).",
                           "Disciples settle it themselves (still no war and no deaths).")):
    c("papan.kb.seteru." + key, a, b)
    c("papan.kb.seteru." + key + ".tip", ta, tb)
c("papan.kb.hati", "Terobosan hati-hati", "Careful breakthroughs")
c("papan.kb.hati.tip", "Murid hanya menembus dengan pil persiapan lengkap dan penjaga Hu Fa.", "Disciples only break through with full preparation pills and a Hu Fa guard.")
c("papan.kb.beritahu", "Beritahu Guru tentang", "Tell the master about")
BERITAHU = {"terobosan": ("Terobosan", "Breakthroughs", "Murid menembus atau gagal menembus.", "A disciple breaks through or fails."),
            "pulang": ("Kepulangan", "Homecomings", "Murid pulang dari perjalanan.", "A disciple comes home from a journey."),
            "luka": ("Luka", "Wounds", "Murid terluka di luar.", "A disciple is wounded outside."),
            "tertawan": ("Tertawan", "Captivity", "Murid tertawan dan menunggu ditolong.", "A disciple is taken and waits for rescue."),
            "belanja_mahal": ("Belanja mahal", "Dear purchases", "Murid membeli barang seharga 200 NB atau lebih.", "A disciple buys something of 200 NB or more."),
            "teknik": ("Teknik baru", "New techniques", "Murid memahami atau mempelajari teknik sendiri.", "A disciple learns a technique on its own."),
            "jalan_ganti": ("Ganti jalan", "Path changes", "Jalan hidup murid berganti.", "A disciple's life path changes."),
            "iblis": ("Jalan Iblis", "Demonic path", "Murid tergoda atau jatuh ke Jalan Iblis.", "A disciple is tempted by or falls to the Demonic Path."),
            "seteru": ("Perseteruan", "Feuds", "Perseteruan dengan sekte lain bergerak.", "A feud with another sect moves.")}
for key, (a, b, ta, tb) in BERITAHU.items():
    c("beritahu." + key, a, b)
    c("beritahu." + key + ".tip", ta, tb)
KAS = {"guru": ("pemberian Guru", "the master's gift"), "saku": ("uang saku", "pocket money"), "sumbang": ("sumbangan ke aula", "a donation to the hall"),
       "kembalian_batu": ("kembalian batu roh", "spirit stone change"), "serap_batu": ("batu roh diserap", "spirit stones absorbed"),
       "uji": ("uji", "test"), "setor": ("setoran ke kas aula", "paid into the hall funds"), "lumbung_diambil": ("barang Lumbung diambil", "Granary goods taken"),
       "jalan_belanja": ("belanja di perjalanan", "shopping on a journey"), "jalan_upah": ("upah perjalanan", "journey wages"),
       "seteru": ("ganti rugi perseteruan", "feud compensation"), "belanja": ("belanja", "shopping"), "jual": ("menjual karya", "selling a work"),
       "gilda": ("misi gilda", "guild mission"), "hadiah": ("hadiah", "gift"), "tebus": ("tebusan", "ransom"), "denda": ("denda", "fine")}
for key, (a, b) in KAS.items():
    c("kas.sebab." + key, a, b)
k("kultivator.hub.card.sekte_mandiri_jalan", "Jalan Hidup Murid", "Disciples' Life Paths")
k("kultivator.hub.card.sekte_mandiri_jalan.tip", "Semua murid, di mana mereka, apa yang mereka kejar; kabar aula, kebijakan dan kas.",
  "Every disciple, where it is and what it pursues; hall news, policy and funds.")

# ==================================================================================================== toasts, notices, bubbles, actions

c("toast.gabung_klien", "%1$s kabar dari murid-muridmu", "%1$s notices from your disciples")
c("toast.gabung", "%1$s kabar dari murid-muridmu", "%1$s notices from your disciples")
c("toast.ringkas", "Selama Guru pergi: %1$s kabar", "While you were away: %1$s notices")
c("toast.ringkas_penting", "Selama Guru pergi: %1$s kabar. %2$s", "While you were away: %1$s notices. %2$s")
c("ringkas.judul", "Selama Guru pergi (%1$s kabar):", "While you were away (%1$s notices):")
c("ringkas.lebih", "...dan %1$s kabar lagi di papan Jalan Hidup Murid.", "...and %1$s more on the Disciples' Life Paths board.")
c("pesan.lihat", "[Lihat]", "[View]")
c("pesan.lihat.tip", "Buka papan Jalan Hidup Murid.", "Open the Disciples' Life Paths board.")
for key, a, b in (("terobosan", "Terobosan", "Breakthrough"), ("pulang", "Pulang", "Home"), ("luka", "Luka", "Wounded"), ("tertawan", "Tertawan", "Taken"),
                  ("belanja_mahal", "Belanja", "Purchase"), ("teknik", "Teknik", "Technique"), ("jalan_ganti", "Jalan", "Path"), ("iblis", "Iblis", "Demonic"),
                  ("seteru", "Seteru", "Feud")):
    c("pesan.cap." + key, "[" + a + "]", "[" + b + "]")
for key, a, b in (("iblis", "Kekuatan... lebih banyak...", "Power... more..."), ("uji", "Gelembung berfungsi!", "Bubbles work!"),
                  ("lelah", "Ingin istirahat...", "Need a rest..."), ("kultivasi", "Harus berlatih!", "Must cultivate!"),
                  ("barang", "Ada yang perlu dibeli.", "Something to buy."), ("sosial", "Teh bersama, yuk?", "Tea together?"),
                  ("bosan", "Bosan sekali...", "So bored...")):
    c("pikir." + key, a, b)
AKSI = {"jauh": ("Murid itu tidak dimuat; tombol ini butuh tubuhnya.", "That disciple is not loaded; this button needs its body."),
        "tegur.ok": ("Murid menunduk mendengar teguranmu.", "The disciple bows its head at your rebuke."),
        "puji.ok": ("Murid tersenyum mendengar pujianmu.", "The disciple smiles at your praise."),
        "sudah_hari_ini": ("Sudah hari ini. Coba lagi besok.", "Already done today. Try again tomorrow."),
        "dorong.ok": ("Kau mendorong %1$s ke jalan %2$s.", "You push %1$s toward the %2$s path."),
        "bebaskan.ok": ("%1$s kembali menentukan harinya sendiri.", "%1$s plans its own days again."),
        "ikut_aula.ok": ("%1$s kembali mengikuti kebijakan aula.", "%1$s follows the hall policy again."),
        "cita_dukung.ok": ("%1$s menjadikan %2$s cita-cita utamanya.", "%1$s makes %2$s its main goal."),
        "cita_batal.ok": ("%1$s melepaskan cita-cita %2$s.", "%1$s lets go of %2$s."),
        "cita_batal.tidak": ("%2$s tidak bisa dibatalkan.", "%2$s cannot be cancelled."),
        "terlalu_jauh": ("Murid harus di dekatmu.", "The disciple must be near you."),
        "beri_uang.tanpa_batu": ("Pegang batu roh di tangan utama.", "Hold spirit stones in your main hand."),
        "beri_uang.penuh": ("Dompet %1$s sudah penuh.", "%1$s's purse is full."),
        "beri_uang.ok": ("%1$s menerima %2$s dengan kedua tangan.", "%1$s receives %2$s with both hands."),
        "ambil.ok": ("Kau mengambil %2$s dari tas %1$s.", "You take %2$s from %1$s's bag."),
        "aula.ok": ("Kebijakan aula diperbarui.", "Hall policy updated.")}
for key, (a, b) in AKSI.items():
    c("aksi." + key, a, b)
c("ingatan.uang_guru", "Guru memberiku uang saku tambahan.", "The master gave me extra pocket money.")
c("ingatan.barang_diambil", "Guru mengambil barang dari tasku.", "The master took something from my bag.")
c("ingatan.asmara_pisah", "Perpisahan yang pahit-manis di kota.", "A bittersweet farewell in town.")
c("calon.kenalan_hati", "Kenalan hati dari kota", "A sweetheart from town")

# ==================================================================================================== commands (op tests)

c("uji.harian.ok", "Lima catatan contoh ditulis ke buku harian %1$s. Buka tab Jalan Hidup > Buku Harian.",
  "Five sample entries written to %1$s's diary. Open the Life Path tab > Diary.")
c("uji.tidak_ada", "Tidak ada murid bernama %1$s di dekatmu.", "No disciple called %1$s near you.")
c("uji.tokoh.judul", "Tokoh aula: %1$s/%2$s", "Hall characters: %1$s/%2$s")
c("uji.tokoh.baris", " · jumpa %1$s · hari %2$s · %3$s", " · met %1$s · day %2$s · %3$s")
c("uji.tokoh.ok", "Tokoh %1$s dibuat untuk %2$s.", "Character %1$s made for %2$s.")
c("uji.tokoh.milik_39f", "Murid sekte rival dikelola modul perseteruan (uji perguruan mandiri seteru).",
  "Rival sect disciples belong to the feud module (uji perguruan mandiri seteru).")
c("uji.ringkas.1", "Lin Yue menembus Inti Emas.", "Lin Yue broke through to Golden Core.")
c("uji.ringkas.2", "Bai Qing pulang dari Kota Awan.", "Bai Qing came home from Cloud City.")
c("uji.ringkas.3", "Han Shuo memahami Tapak Awan sendiri.", "Han Shuo learned Cloud Palm on his own.")
for key, (a, b, _x, _y) in BERITAHU.items():
    c("uji.toast." + key, "Uji kabar: " + a.lower(), "Test notice: " + b.lower())

# ==================================================================================================== advancements, guide, encyclopedia

c("adv.harian.title", "Buku Harian Murid", "A Disciple's Diary")
c("adv.harian.desc", "Baca buku harian yang ditulis muridmu sendiri.", "Read a diary your disciple wrote on its own.")
c("adv.cita.title", "Cita-cita yang Tercapai", "A Goal Reached")
c("adv.cita.desc", "Seorang murid mencapai cita-citanya sendiri.", "A disciple reaches a goal of its own.")
k("kultivator.guide.chapter.21", "Murid Mandiri", "Autonomous Disciples")
k("kultivator.guide.chapter.21.desc", "Murid yang memilih jalan hidup, belanja ke kota, menembus sendiri dan menulis buku harian: cara membimbing tanpa mengatur segalanya.",
  "Disciples who choose a life path, shop in town, break through alone and keep a diary: how to guide without ruling everything.")
GUIDE = [
    ("jalan", "Jalan Hidup Murid", "Life Paths",
     "Setiap murid memilih jalan hidupnya sendiri dari sifat, bakat, Dao, akar dan kenangannya: Pendekar Pedang, Alkemis, Ahli Formasi, Pertapa, Petualang, Pemburu, Pedagang, Cendekia atau Penjaga.\nBuka gulungan murid lalu tab Jalan Hidup. Di sana tertulis kenapa ia memilih jalan itu, dan kau bisa mendorongnya ke jalan lain.",
     "Every disciple chooses its own life path from its traits, talent, Dao, root and memories: Swordsman, Alchemist, Formation Master, Hermit, Adventurer, Hunter, Merchant, Scholar or Guardian.\nOpen the disciple scroll, then the Life Path tab. It tells why it chose that path, and you can push it toward another."),
    ("kebijakan", "Kebijakan Aula", "Hall Policy",
     "Di papan Jalan Hidup Murid (hub, seksi Murid) kau mengatur Mandiri Penuh, Seimbang atau Patuh, kehati-hatian, uang saku dan izin kegiatan.\nMurid tertentu bisa diberi kebijakan sendiri dari tab Kebijakan di gulungannya. Perintah Guru selalu menang.",
     "On the Disciples' Life Paths board (hub, Disciples section) you set Full, Balanced or Obedient autonomy, caution, pocket money and allowed activities.\nA single disciple can get its own policy from the Policy page of its scroll. The master's orders always win."),
    ("harian", "Baca Buku Hariannya", "Read the Diary",
     "Murid menulis buku harian setiap hari yang berarti: cuaca hari itu, apa yang terjadi, apa yang ia rasakan dan cita-citanya.\nBuka tab Jalan Hidup > Buku Harian, atau [Buka Buku] untuk 40 catatan dan gulungan perjalanannya. Murid yang tidak dimuat bisa dibaca dari papan.",
     "Disciples write a diary every day that matters: the weather, what happened, how they felt and their goals.\nOpen the Life Path tab > Diary, or [Open Book] for 40 entries and the travel scrolls. Unloaded disciples can be read from the board."),
    ("belanja", "Turun ke Kota", "Down to Town",
     "Dengan uang sakunya murid turun ke kota membeli pil, bahan dan perlengkapan, menawar harga dan kadang dicopet.\nIa membawa pulang oleh-oleh dan cerita; pedagang yang sering ia datangi menjadi langganan.",
     "With its pocket money a disciple goes down to town for pills, materials and gear, haggles and sometimes meets a pickpocket.\nIt brings home gifts and stories; a stall it visits often becomes its regular merchant."),
    ("tembus", "Menembus Sendiri", "Breaking Through Alone",
     "Murid yang siap menunggu Guru beberapa hari, lalu menembus sendiri dengan pil persiapannya, dijaga sahabat sebagai Hu Fa.\nAtur lama menunggu dan sikap hati-hati di papan.",
     "A ready disciple waits some days for the master, then breaks through on its own with its preparation pills, guarded by a friend as Hu Fa.\nSet how long it waits and the careful stance on the board."),
    ("cita", "Cita-cita yang Tercapai", "A Goal Reached",
     "Setiap murid memegang sampai tiga cita-cita: ranah berikutnya, menjuarai turnamen, membalas kekalahan, membuat karya bermutu...\nDukung cita-cita yang kau sukai. Saat tercapai, ia menulisnya dengan pita merah di buku hariannya.",
     "Each disciple holds up to three goals: the next realm, winning the tournament, avenging a defeat, making a fine work...\nSupport the goals you like. When one is reached it writes it with a red ribbon in its diary."),
    ("abadi", "Abadi Sejati dengan Kekuatan Sendiri", "True Immortal on Its Own",
     "Tujuan yang jauh: seorang murid menempuh seluruh jalan sampai Abadi Sejati tanpa digendong Guru.",
     "A far target: a disciple walks the whole way to True Immortal without being carried by its master."),
    ("seteru", "Akhir Sebuah Perseteruan", "The End of a Feud",
     "Murid sekte lain bisa menjadi seteru. Damaikan atau selesaikan dengan duel resmi; tidak ada perang dan tidak ada kematian.",
     "Disciples of other sects can become rivals. Make peace or settle it with an official duel; there is no war and no death."),
]
for step, ta, tb, ba, bb in GUIDE:
    k("kultivator.guide.c21." + step + ".title", ta, tb)
    k("kultivator.guide.c21." + step + ".body", ba, bb)
k("kultivator.screen.help.mandiri.title", "Murid Mandiri", "Autonomous Disciples")
k("kultivator.screen.help.mandiri",
  "Murid sekte hidup sendiri. Setiap pagi perencana memilih kegiatan dari tujuh dorongan (lapar kultivasi, bosan, lelah, sosial, ambisi, barang, luka), jalan hidup, cita-cita dan kebijakan aula.\n\n"
  "Jalan Hidup: sepuluh jalan (sembilan pilihan dan Jalan Iblis). Godaan 60 ke atas berbahaya; tegur, ajak menembus bersama, atau tebus di aula.\n\n"
  "Cita-cita: sampai tiga, berprioritas. [Dukung] menjadikannya utama; Ranah dan Guru tidak bisa dibatalkan.\n\n"
  "Uang: uang saku harian dari kas aula menurut jenjang, upah dan hasil karya. [Beri uang] memasukkan batu roh di tanganmu ke dompet murid.\n\n"
  "Perjalanan: belanja, misi gilda, berburu, reruntuhan, gua, berkelana. Murid bisa terluka atau tertawan, tidak pernah mati; tolong atau tebus mereka.\n\n"
  "Buku Harian: disusun dari kejadian nyata dalam bahasamu. Pita merah menandai hari besar.\n\n"
  "Kebijakan: Mandiri Penuh, Seimbang, Patuh; kehati-hatian; izin 16 kelompok kegiatan. Perintah Guru selalu menang.",
  "Sect disciples live on their own. Each morning the planner picks activities from seven drives (hunger to cultivate, boredom, fatigue, social, ambition, goods, wounds), the life path, goals and hall policy.\n\n"
  "Life Path: ten paths (nine choices and the Demonic Path). Temptation from 60 up is dangerous; rebuke it, break through together, or redeem it in the hall.\n\n"
  "Goals: up to three, by priority. [Support] makes one the main goal; Realm and Master cannot be cancelled.\n\n"
  "Money: daily pocket money from the hall funds by rank, wages and crafts. [Give money] puts the spirit stones in your hand into the purse.\n\n"
  "Journeys: shopping, guild missions, hunts, ruins, caves, wandering. Disciples can be wounded or taken, never killed; rescue or ransom them.\n\n"
  "Diary: written from real events in your language. A red ribbon marks the big days.\n\n"
  "Policy: Full, Balanced, Obedient; caution; 16 activity groups. The master's orders always win.")

# ==================================================================================================== Buku Harian: openers (39.8.1)
# buka.<konteks>.<n>; "kota" gets the town name as %1$s, the others ignore the argument.

h("buka.hujan", [
    ("Hujan tipis turun sejak pagi, membasahi atap-atap aula.", "A thin rain fell from the morning, wetting the hall roofs."),
    ("Bunyi hujan di genting membuatku sulit berkonsentrasi.", "The rain on the tiles made it hard to concentrate."),
    ("Hujan deras; jalan setapak berubah menjadi sungai kecil.", "Heavy rain; the footpaths turned into little streams."),
    ("Udara basah dan dingin, qi langit dan bumi terasa berat.", "The air was wet and cold, the qi of heaven and earth felt heavy."),
    ("Payung kertasku bocor lagi hari ini.", "My paper umbrella leaked again today."),
    ("Kabut dan gerimis menutupi puncak-puncak di kejauhan.", "Mist and drizzle hid the far peaks."),
    ("Hujan tidak berhenti; aroma tanah basah memenuhi halaman.", "The rain did not stop; the smell of wet earth filled the courtyard."),
    ("Di bawah hujan, kolam teratai beriak tanpa henti.", "Under the rain the lotus pond rippled without end."),
])
h("buka.salju", [
    ("Salju turun pelan-pelan, menutup tangga batu dengan putih.", "Snow fell slowly, covering the stone stairs in white."),
    ("Napasku berasap; salju setebal telapak tangan di halaman.", "My breath smoked; the snow lay a palm deep in the yard."),
    ("Dahan-dahan pinus merunduk menahan salju.", "The pine branches bowed under the snow."),
    ("Dingin sekali; aku menghangatkan tangan dengan qi.", "Bitter cold; I warmed my hands with qi."),
    ("Dunia sunyi di bawah salju, hanya derak langkahku yang terdengar.", "The world was silent under the snow, only my footsteps creaked."),
    ("Salju pertama tahun ini; adik-adik seperguruan berlarian gembira.", "The year's first snow; the juniors ran about in delight."),
    ("Angin membawa butir salju ke dalam lorong.", "The wind blew snowflakes into the corridor."),
    ("Kolam membeku; ikan-ikan roh tidur di bawah es.", "The pond froze; the spirit fish slept under the ice."),
])
h("buka.badai", [
    ("Badai mengamuk; petir menyambar di balik pegunungan.", "A storm raged; lightning struck behind the mountains."),
    ("Guntur mengguncang jendela, dan aku teringat petir kesengsaraan.", "Thunder shook the windows, and I thought of tribulation lightning."),
    ("Angin badai mencabuti daun-daun bambu.", "The storm wind tore the bamboo leaves away."),
    ("Langit hitam; qi kacau seperti air mendidih.", "The sky was black; the qi churned like boiling water."),
    ("Hujan badai menghantam atap sepanjang hari.", "Storm rain hammered the roofs all day."),
    ("Kilat menerangi halaman sesaat, lalu gelap lagi.", "Lightning lit the courtyard for an instant, then dark again."),
    ("Semua orang berlindung di dalam; badai tidak memberi ampun.", "Everyone sheltered inside; the storm gave no mercy."),
    ("Di tengah badai, formasi pelindung sekte berdengung keras.", "In the storm the sect's guarding formation hummed loudly."),
])
h("buka.purnama", [
    ("Bulan penuh, qi turun seperti embun.", "The moon was full, qi fell like dew."),
    ("Cahaya purnama memandikan halaman dengan perak.", "The full moon bathed the courtyard in silver."),
    ("Malam purnama: saat terbaik menyerap sari bulan.", "A full-moon night: the best time to absorb the moon's essence."),
    ("Di bawah bulan bulat, bayanganku tampak tajam di batu.", "Under the round moon my shadow lay sharp on the stone."),
    ("Bulan purnama menggantung di atas puncak, besar dan tenang.", "The full moon hung above the peak, large and calm."),
    ("Malam ini terang sekali; aku tidak butuh lentera.", "Tonight was so bright I needed no lantern."),
    ("Serangga malam bernyanyi di bawah bulan penuh.", "Night insects sang under the full moon."),
    ("Para tetua bilang qi Yin paling murni saat purnama.", "The elders say Yin qi is purest at the full moon."),
])
h("buka.bulan_baru", [
    ("Malam tanpa bulan; bintang-bintang tampak lebih banyak.", "A moonless night; there seemed to be more stars."),
    ("Gelap pekat; hanya lentera aula yang menyala.", "Pitch dark; only the hall lanterns burned."),
    ("Bulan baru: malam yang cocok untuk merenung dalam sunyi.", "New moon: a night fit for silent contemplation."),
    ("Langit hitam bersih, Bima Sakti membentang seperti sungai.", "The sky was clean black, the Milky Way stretched like a river."),
    ("Tanpa bulan, suara angin terdengar lebih jelas.", "Without the moon, the wind sounded clearer."),
    ("Malam gelap; aku berjalan pelan agar tidak tersandung.", "A dark night; I walked slowly so as not to trip."),
    ("Kunang-kunang terlihat jelas di malam tanpa bulan.", "Fireflies showed clearly on the moonless night."),
    ("Gelap seperti ini, kata orang, iblis hati lebih mudah datang.", "In a darkness like this, people say, heart demons come more easily."),
])
h("buka.kota", [
    ("Jalanan %1$s ramai sejak pagi.", "The streets of %1$s were busy from the morning."),
    ("Aroma bakpao dan dupa memenuhi pasar %1$s.", "The smell of steamed buns and incense filled the market of %1$s."),
    ("Genderang pasar %1$s berbunyi ketika aku tiba.", "The market drum of %1$s sounded as I arrived."),
    ("Gerbang %1$s penuh pedagang dan gerobak.", "The gate of %1$s was full of merchants and carts."),
    ("Di %1$s, setiap sudut punya suara tawar-menawar.", "In %1$s every corner had the sound of haggling."),
    ("Lentera-lentera merah %1$s bergoyang ditiup angin.", "The red lanterns of %1$s swayed in the wind."),
    ("Para penjaga %1$s memandangku sebentar, lalu membiarkanku lewat.", "The guards of %1$s looked me over, then let me pass."),
    ("Kedai teh di %1$s penuh orang yang bergosip.", "The teahouse of %1$s was full of gossiping people."),
])
h("buka.jalan", [
    ("Jalan berdebu memanjang ke balik bukit.", "A dusty road stretched beyond the hill."),
    ("Kami berangkat sebelum fajar; embun masih di rumput.", "We set out before dawn; dew still on the grass."),
    ("Burung-burung beterbangan dari semak saat kami lewat.", "Birds burst from the bushes as we passed."),
    ("Batu penanda jalan menunjukkan perjalanan masih jauh.", "The milestone said there was still a long way."),
    ("Di persimpangan, seorang petani menunjukkan arah.", "At the crossroads a farmer showed us the way."),
    ("Matahari terik; kami berteduh di bawah pohon tua.", "The sun was harsh; we rested under an old tree."),
    ("Sungai kecil di tepi jalan berkilau di bawah matahari.", "A stream by the road glittered in the sun."),
    ("Jalan setapak menembus hutan pinus yang harum.", "The path cut through a fragrant pine forest."),
])
h("buka.fajar", [
    ("Fajar menyingsing di balik puncak timur.", "Dawn broke behind the eastern peak."),
    ("Lonceng pagi berbunyi; aku sudah duduk bersila sebelumnya.", "The morning bell rang; I was already sitting cross-legged."),
    ("Kabut pagi melayang di antara paviliun.", "Morning mist drifted between the pavilions."),
    ("Cahaya pertama menyentuh atap aula, keemasan.", "The first light touched the hall roof, golden."),
    ("Ayam roh berkokok terlalu pagi lagi.", "The spirit rooster crowed too early again."),
    ("Udara fajar segar; qi ungu dari timur paling murni saat ini.", "The dawn air was fresh; the purple qi from the east is purest now."),
    ("Aku bangun sebelum yang lain dan menyapu halaman.", "I rose before the others and swept the yard."),
    ("Embun menetes dari ujung daun bambu.", "Dew dripped from the tips of the bamboo leaves."),
])
h("buka.siang", [
    ("Matahari tinggi; batu-batu halaman terasa hangat.", "The sun stood high; the yard stones felt warm."),
    ("Siang yang tenang di aula.", "A quiet noon at the hall."),
    ("Suara jangkrik memenuhi siang yang panas.", "Crickets filled the hot noon."),
    ("Langit biru tanpa awan.", "A blue sky without clouds."),
    ("Bayangan pohon pendek di tengah hari.", "The tree shadows were short at midday."),
    ("Dapur aula mengepulkan asap; waktu makan siang.", "The hall kitchen smoked; lunchtime."),
    ("Angin sepoi meniup tirai paviliun.", "A soft breeze moved the pavilion curtains."),
    ("Hari cerah; para murid berlatih di lapangan.", "A clear day; disciples drilled in the yard."),
])
h("buka.senja", [
    ("Senja memerahkan langit barat.", "Dusk reddened the western sky."),
    ("Lonceng senja berbunyi panjang dari menara.", "The dusk bell rang long from the tower."),
    ("Burung gagak pulang ke sarang di pinus tua.", "Crows flew home to the old pines."),
    ("Lentera-lentera mulai dinyalakan satu per satu.", "The lanterns were lit one by one."),
    ("Cahaya senja membuat kolam berwarna tembaga.", "The dusk light turned the pond copper."),
    ("Angin senja membawa bau masakan dari dapur.", "The dusk wind carried the smell of cooking from the kitchen."),
    ("Matahari tenggelam di balik pegunungan berlapis.", "The sun sank behind layered mountains."),
    ("Bayangan-bayangan memanjang di halaman.", "Shadows lengthened across the yard."),
])
h("buka.malam", [
    ("Malam sunyi; hanya suara serangga.", "A quiet night; only insects."),
    ("Lentera kamarku berkedip-kedip.", "My room's lantern flickered."),
    ("Bintang-bintang bertaburan di atas aula.", "Stars were scattered above the hall."),
    ("Angin malam yang dingin menyusup lewat celah jendela.", "The cold night wind crept through the window."),
    ("Larut malam, aula sudah tertidur.", "Late at night, the hall was asleep."),
    ("Burung hantu berbunyi di hutan belakang.", "An owl called in the woods behind."),
    ("Aku menulis di bawah cahaya lilin.", "I write by candlelight."),
    ("Malam ini terasa panjang.", "Tonight feels long."),
])

# ==================================================================================================== reflections by trait

h("renung.rajin", [("Besok aku akan bangun lebih pagi lagi.", "Tomorrow I will rise even earlier."),
                   ("Kerja keras tidak pernah mengkhianati.", "Hard work never betrays."),
                   ("Satu hari tanpa latihan adalah satu langkah mundur.", "A day without training is a step back."),
                   ("Masih banyak yang harus kupelajari.", "There is still so much to learn.")])
h("renung.malas", [("Sebenarnya aku lebih suka tidur siang.", "Honestly, I would rather nap."),
                   ("Besok saja aku lanjutkan.", "I will continue tomorrow."),
                   ("Kenapa semua orang terburu-buru?", "Why is everyone in such a hurry?"),
                   ("Mungkin ada jalan yang lebih mudah.", "Maybe there is an easier way.")])
h("renung.sombong", [("Tentu saja; siapa lagi kalau bukan aku.", "Of course; who else but me."),
                     ("Mereka masih jauh di bawahku.", "They are still far below me."),
                     ("Suatu hari Langit pun akan mengenal namaku.", "One day even Heaven will know my name."),
                     ("Aku pantas mendapat lebih dari ini.", "I deserve more than this.")])
h("renung.rendah_hati", [("Kata Guru, kesabaran juga bagian dari kultivasi.", "Master says patience is part of cultivation too."),
                         ("Aku hanya beruntung hari ini.", "I was only lucky today."),
                         ("Di luar gunung ada gunung, di luar orang ada orang.", "Beyond the mountain is another mountain, beyond a person another person."),
                         ("Masih banyak kakak seperguruan yang lebih hebat.", "Many seniors are far better than me.")])
h("renung.setia", [("Semua ini untuk sekte dan Guru.", "All of this is for the sect and the master."),
                   ("Aku tidak akan mengecewakan Guru.", "I will not disappoint the master."),
                   ("Saudara seperguruan adalah keluargaku.", "My fellow disciples are my family."),
                   ("Selama sekte berdiri, aku akan menjaganya.", "As long as the sect stands, I will guard it.")])
h("renung.licik", [("Yang penting hasilnya, bukan caranya.", "What matters is the result, not the way."),
                   ("Aku sudah menyiapkan rencana cadangan.", "I have a backup plan ready."),
                   ("Orang jujur sering rugi.", "Honest folk often lose."),
                   ("Diam-diam aku mencatat kelemahan mereka.", "Quietly I noted their weaknesses.")])
h("renung.berani", [("Aku tidak takut apa pun.", "I fear nothing."),
                    ("Kalau ada bahaya, biar aku yang maju.", "If there is danger, let me go first."),
                    ("Pedang yang tak pernah diuji akan tumpul.", "A sword never tested grows dull."),
                    ("Besok aku akan mencoba lagi, lebih keras.", "Tomorrow I will try again, harder.")])
h("renung.penakut", [("Aku masih gemetar memikirkannya.", "I still tremble thinking of it."),
                     ("Semoga besok tidak ada kejadian aneh.", "I hope nothing strange happens tomorrow."),
                     ("Lebih aman tinggal di dekat aula.", "It is safer near the hall."),
                     ("Aku ingin lebih kuat agar tidak takut lagi.", "I want to be stronger so I am not afraid anymore.")])
h("renung.ambisius", [("Ini baru permulaan.", "This is only the beginning."),
                      ("Aku harus lebih cepat dari yang lain.", "I must be faster than the others."),
                      ("Suatu hari aku akan berdiri di puncak.", "One day I will stand at the summit."),
                      ("Setiap batu roh membawaku lebih dekat.", "Every spirit stone brings me closer.")])
h("renung.tenang", [("Air yang tenang memantulkan bulan dengan jelas.", "Still water reflects the moon clearly."),
                    ("Tidak ada yang perlu dikejar dengan tergesa.", "Nothing needs to be chased in haste."),
                    ("Aku menarik napas panjang dan melepaskannya.", "I took a long breath and let it go."),
                    ("Hari ini cukup; besok akan datang sendiri.", "Today is enough; tomorrow comes on its own.")])
h("renung.penyayang", [("Semoga semua orang di aula sehat.", "May everyone at the hall be well."),
                       ("Aku menyisihkan sedikit untuk adik-adik.", "I set a little aside for the juniors."),
                       ("Hati yang hangat juga menguatkan qi.", "A warm heart strengthens qi too."),
                       ("Aku senang bisa membantu.", "I am glad I could help.")])
h("renung.pendendam", [("Aku tidak akan melupakannya.", "I will not forget it."),
                       ("Suatu hari utang ini akan kubalas.", "One day I will repay this debt."),
                       ("Aku mencatat setiap penghinaan.", "I keep note of every insult."),
                       ("Biar waktu yang membuktikan siapa yang benar.", "Let time prove who was right.")])

# ==================================================================================================== closings by goal

h("tutup.ranah", [("Ranah berikutnya... sebentar lagi.", "The next realm... soon."),
                  ("Dinding ranah ini akan kutembus.", "I will break through this realm's wall."),
                  ("Selangkah lagi menuju puncak.", "One more step toward the peak.")])
h("tutup.dao", [("Dao-ku masih kabur, tapi makin jelas.", "My Dao is still blurred, but clearer."),
                ("Suatu hari aku akan memahami Dao sepenuhnya.", "One day I will understand the Dao fully."),
                ("Setiap hari aku mendengar Dao sedikit lebih jelas.", "Each day I hear the Dao a little more clearly.")])
h("tutup.teknik", [("Jurus itu belum sempurna, tapi besok lebih baik.", "That technique is not perfect yet, but tomorrow will be better."),
                   ("Seribu kali latihan, satu kali pencerahan.", "A thousand drills, one enlightenment."),
                   ("Teknik itu akan menjadi milikku.", "That technique will be mine.")])
h("tutup.pusaka", [("Aku masih bermimpi tentang pusaka yang layak.", "I still dream of a worthy treasure."),
                   ("Pedang yang baik menunggu tuan yang baik.", "A good sword waits for a good master."),
                   ("Suatu hari aku akan memegang pusakaku sendiri.", "One day I will hold a treasure of my own.")])
h("tutup.juara", [("Musim depan, panggung itu milikku.", "Next season, that stage is mine."),
                  ("Aku akan berdiri di podium juara.", "I will stand on the champion's podium."),
                  ("Gong final masih terngiang di telingaku.", "The final's gong still rings in my ears.")])
h("tutup.dendam", [("Aku belum lupa wajahnya.", "I have not forgotten that face."),
                   ("Pertemuan berikutnya, aku tidak akan kalah.", "Next time we meet, I will not lose."),
                   ("Utang itu akan lunas.", "That debt will be paid.")])
h("tutup.jodoh", [("Entah kapan aku bertemu seseorang yang mengerti hatiku.", "I wonder when I will meet someone who understands my heart."),
                  ("Ada wajah yang terus muncul dalam meditasiku.", "There is a face that keeps appearing in my meditation."),
                  ("Mungkin di festival nanti...", "Maybe at the festival...")])
h("tutup.guru", [("Suatu hari aku akan membuat Guru bangga.", "One day I will make the master proud."),
                 ("Bayangan Guru masih terlalu jauh di depan.", "The master's shadow is still far ahead."),
                 ("Aku ingin melampaui Guru, bukan meninggalkannya.", "I want to surpass the master, not leave them.")])
h("tutup.jenjang", [("Jubah murid inti itu akan kupakai.", "I will wear that core disciple's robe."),
                    ("Satu jenjang lagi.", "One more rank."),
                    ("Tetua pasti melihat usahaku.", "The elders must see my effort.")])
h("tutup.kekayaan", [("Dompetku makin berat, sedikit demi sedikit.", "My purse grows heavier, little by little."),
                     ("Batu demi batu, kekayaanku tumbuh.", "Stone by stone, my fortune grows."),
                     ("Suatu hari aku akan membeli seluruh lapak itu.", "One day I will buy that whole stall.")])
h("tutup.binatang", [("Binatang roh yang hebat menunggu di hutan.", "A great spirit beast waits in the forest."),
                     ("Aku akan menundukkan binatang yang lebih kuat.", "I will subdue a stronger beast."),
                     ("Jejak cakar besar itu masih kuingat.", "I still remember those huge claw marks.")])
h("tutup.rahasia", [("Alam rahasia memanggilku.", "The secret realms are calling me."),
                    ("Di balik gerbang kabut ada harta yang menunggu.", "Beyond the gate of mist a treasure waits."),
                    ("Peta itu akan kulengkapi.", "I will complete that map.")])
h("tutup.menolong", [("Semoga besok aku bisa menolong seseorang lagi.", "I hope I can help someone again tomorrow."),
                     ("Jalan ini lebih indah kalau dibagi.", "This road is more beautiful when shared."),
                     ("Satu kebaikan kecil setiap hari.", "One small kindness every day.")])
h("tutup.karya", [("Karya terbaikku belum lahir.", "My best work is not yet born."),
                  ("Tungku itu akan menghasilkan pil bermutu tinggi.", "That furnace will make a high-grade pill."),
                  ("Tanganku makin mantap setiap hari.", "My hands grow steadier every day.")])

# ==================================================================================================== core lines: 39B self-growth (short keys)

h("tembus.siap", [("Persiapanku untuk %1$s sudah lengkap. Aku menunggu waktu yang baik.", "My preparation for %1$s is complete. I wait for a good moment."),
                  ("Pil-pil persiapan sudah kususun. %1$s di depan mata.", "The preparation pills are laid out. %1$s is right before my eyes."),
                  ("Qi-ku penuh sampai ke ujung jari; %1$s tinggal selangkah.", "My qi fills me to the fingertips; %1$s is a single step away.")])
h("tembus.tergesa", [("Aku belum benar-benar siap untuk %1$s, tapi aku tidak mau menunggu lagi.", "I am not truly ready for %1$s, but I will not wait any longer."),
                     ("Mungkin terlalu cepat... tapi %1$s memanggilku.", "Maybe too soon... but %1$s is calling me."),
                     ("Kakak senior bilang aku tergesa-gesa. Aku tetap akan mencoba menembus %1$s.",
                      "A senior said I was rushing. I will still try to break through to %1$s.")])
h("tembus.berhasil", [("Aku menembus %1$s! %2$s sahabat menjagaku sampai petir terakhir.", "I broke through to %1$s! %2$s friends guarded me to the last bolt."),
                      ("Dinding itu runtuh, dan %1$s terbuka di hadapanku. Terima kasih untuk %2$s penjagaku.",
                       "The wall fell, and %1$s opened before me. Thanks to my %2$s guards."),
                      ("Langit bergemuruh, tetapi aku tetap berdiri. %1$s! Aku melakukannya dengan kekuatanku sendiri.",
                       "Heaven roared, but I kept standing. %1$s! I did it with my own strength.")])
h("tembus.gagal", [("Terobosanku ke %1$s gagal. Dadaku masih sakit.", "My breakthrough to %1$s failed. My chest still hurts."),
                   ("Qi-ku buyar di detik terakhir. %1$s belum mau menerimaku.", "My qi scattered at the last moment. %1$s will not have me yet."),
                   ("Aku jatuh dari ambang %1$s. Lain kali aku akan lebih siap.", "I fell from the threshold of %1$s. Next time I will be readier.")])
h("tembus.urung", [("Aku mengurungkan terobosan ke %1$s; hatiku belum tenang.", "I called off the breakthrough to %1$s; my heart is not calm yet."),
                   ("Hari ini bukan harinya untuk %1$s.", "Today is not the day for %1$s."),
                   ("Aku duduk lama, lalu memutuskan menunda %1$s.", "I sat a long time, then decided to put off %1$s.")])
h("tembus.batal_tempur", [("Pertempuran pecah di dekat aula; terobosanku ke %1$s terpaksa kuhentikan.", "A fight broke out near the hall; I had to stop my breakthrough to %1$s."),
                          ("Musuh datang tepat saat aku hendak menembus %1$s.", "Enemies came just as I was about to break through to %1$s."),
                          ("Aku menyimpan qi untuk bertarung; %1$s bisa menunggu.", "I kept my qi for the fight; %1$s can wait.")])
h("tembus.batal_lemah", [("Tubuhku terlalu lemah untuk menembus %1$s hari ini.", "My body is too weak to break through to %1$s today."),
                         ("Luka lamaku belum pulih; %1$s harus menunggu.", "My old wound has not healed; %1$s must wait."),
                         ("Qi-ku goyah, jadi aku berhenti sebelum %1$s.", "My qi wavered, so I stopped short of %1$s.")])
h("tembus.tunda_belanja", [("Pil persiapan untuk %1$s belum ada; aku harus turun ke kota dulu.", "I have no preparation pills for %1$s; I must go down to town first."),
                           ("Aku menunda %1$s sampai membeli perlengkapan.", "I put off %1$s until I buy supplies."),
                           ("Tanpa pil yang tepat, %1$s terlalu berbahaya. Besok aku ke pasar.", "Without the right pills %1$s is too dangerous. Tomorrow, the market.")])
h("hati.lulus", [("Iblis hati datang dengan wajah orang-orang yang kusayangi. Aku tidak goyah. Jalan menuju %1$s terbuka.",
                  "The heart demon came wearing the faces of those I love. I did not waver. The road to %1$s is open."),
                 ("Ujian Hati selesai. Bayangan-bayangan itu tidak bisa menipuku.", "The Heart Trial is over. Those shadows could not fool me."),
                 ("Aku melihat ketakutanku sendiri, lalu melewatinya.", "I saw my own fear, and walked past it.")])
h("hati.gagal", [("Ujian Hati menjatuhkanku. Aku masih melihat bayangan itu saat memejamkan mata.", "The Heart Trial threw me down. I still see that shadow when I close my eyes."),
                 ("Iblis hati menang kali ini. Jalan ke %1$s tertutup sementara.", "The heart demon won this time. The road to %1$s is closed for now."),
                 ("Hatiku belum cukup jernih untuk %1$s.", "My heart is not clear enough for %1$s.")])
h("hufa.jaga_berhasil", [("Aku menjaga %1$s sampai ia menembus %2$s. Petirnya tidak pernah menyentuhnya.", "I guarded %1$s until they broke through to %2$s. The lightning never touched them."),
                         ("%1$s berhasil mencapai %2$s, dan aku berdiri di sampingnya sepanjang waktu.", "%1$s reached %2$s, and I stood beside them the whole time."),
                         ("Menjaga %1$s membuatku mengerti arti saudara seperguruan.", "Guarding %1$s taught me what a fellow disciple means.")])
h("tutup.masuk", [("Aku menutup diri selama %1$s hari. Pintu sudah disegel.", "I enter seclusion for %1$s days. The door is sealed."),
                  ("Mulai hari ini aku mengasingkan diri %1$s hari. Jangan cari aku.", "From today I withdraw for %1$s days. Do not look for me."),
                  ("Kertas segel kupasang di pintu. %1$s hari dalam sunyi.", "I put the seal paper on the door. %1$s days in silence.")])
h("tutup.keluar", [("Aku keluar dari pengasingan setelah %1$s hari. Qi-ku lebih jernih.", "I left seclusion after %1$s days. My qi is clearer."),
                   ("%1$s hari dalam gelap, dan dunia di luar terasa baru.", "%1$s days in the dark, and the world outside feels new."),
                   ("Segel pintu kulepas. Pengasingan %1$s hari ini tidak sia-sia.", "I took down the door seal. These %1$s days were not wasted.")])
h("tutup.terganggu", [("Pengasinganku terganggu di hari ke-%1$s. Kesal sekali.", "My seclusion was disturbed on day %1$s. So annoying."),
                      ("Seseorang mengetuk pintuku di hari ke-%1$s; konsentrasiku buyar.", "Someone knocked on my door on day %1$s; my focus broke."),
                      ("Keributan di luar memutus pengasinganku setelah %1$s hari.", "A commotion outside cut my seclusion short after %1$s days.")])
h("renung.selesai", [("Aku merenung lama di bawah pohon tua. %1$s terasa sedikit lebih dekat.", "I contemplated a long time under the old tree. %1$s feels a little closer."),
                     ("Aku duduk memandang awan dan memikirkan jalanku.", "I sat watching the clouds and thought about my path."),
                     ("Renungan hari ini memberiku satu pertanyaan baru.", "Today's contemplation gave me one new question.")])
h("serap.mulai", [("Aku menyerap batu roh senilai %1$s NB. Qi murni mengalir ke dantian.", "I absorbed spirit stones worth %1$s NB. Pure qi flowed into my dantian."),
                  ("Batu-batu roh berputar di sekelilingku; %1$s NB habis dalam satu sore.", "Spirit stones circled around me; %1$s NB gone in one afternoon."),
                  ("Aku membuka kantong batu roh. Mahal, tapi qi-nya murni.", "I opened my spirit stone pouch. Dear, but the qi is pure.")])
h("belajar.tas_mulai", [("Aku mulai mempelajari %1$s dari gulungan di tasku.", "I began studying %1$s from the scroll in my bag."),
                        ("Gulungan %1$s kubuka pelan-pelan. Huruf-hurufnya sulit.", "I opened the %1$s scroll slowly. Its characters are hard."),
                        ("Malam ini aku membaca %1$s sampai lilin habis.", "Tonight I read %1$s until the candle burned out.")])
h("belajar.tas_selesai", [("Akhirnya aku menguasai %1$s!", "At last I have mastered %1$s!"),
                          ("Gulungan %1$s sudah habis kubaca, dan jurusnya hidup di tanganku.", "I finished the %1$s scroll, and the technique lives in my hands."),
                          ("%1$s kini milikku.", "%1$s is mine now.")])
h("belajar.pencerahan", [("Saat menatap air terjun, %1$s tiba-tiba menjadi jelas. Aku memahaminya sendiri!", "Watching the waterfall, %1$s suddenly became clear. I understood it on my own!"),
                         ("Pencerahan! Aku menemukan %1$s tanpa guru.", "Enlightenment! I found %1$s without a teacher."),
                         ("Seperti kabut tersibak, %1$s terbuka di pikiranku.", "Like mist parting, %1$s opened in my mind.")])
h("belajar.kejar", [("Selama hari-hari itu aku mempelajari %1$s.", "During those days I studied %1$s."),
                    ("Diam-diam aku berlatih %1$s setiap malam.", "Quietly I practised %1$s every night."),
                    ("%1$s mulai kupahami sedikit demi sedikit.", "Little by little I began to understand %1$s.")])
h("ajar.diajar", [("%1$s mengajariku %2$s dengan sabar.", "%1$s patiently taught me %2$s."),
                  ("Kakak senior %1$s menunjukkan inti %2$s kepadaku.", "Senior %1$s showed me the heart of %2$s."),
                  ("Berkat %1$s, aku akhirnya mengerti %2$s.", "Thanks to %1$s, I finally understand %2$s.")])
h("ajar.mengajar", [("Aku mengajari %1$s jurus %2$s. Mengajar ternyata juga belajar.", "I taught %1$s the %2$s technique. Teaching is learning too."),
                    ("%1$s cepat menangkap %2$s. Aku bangga.", "%1$s picked up %2$s quickly. I am proud."),
                    ("Aku menunjukkan %2$s kepada %1$s sampai matahari terbenam.", "I showed %2$s to %1$s until sunset.")])
h("ajar.tetua", [("Tetua %1$s mengajariku %2$s selagi Guru pergi.", "Elder %1$s taught me %2$s while the master was away."),
                 ("Di paviliun, %1$s membimbingku memahami %2$s.", "In the pavilion, %1$s guided me through %2$s."),
                 ("%1$s memberiku pelajaran %2$s. Aku akan berlatih keras.", "%1$s gave me a lesson in %2$s. I will train hard.")])
h("dao.bimbang", [("Aku bimbang: apakah Dao %1$s benar-benar jalanku?", "I hesitate: is the %1$s Dao truly my path?"),
                  ("Dao %1$s memanggil, tapi hatiku belum yakin.", "The %1$s Dao calls, but my heart is unsure."),
                  ("Semalaman aku memikirkan Dao %1$s.", "All night I thought about the %1$s Dao.")])
h("dao.upacara", [("Hari ini aku memilih Dao %1$s. Jalanku adalah Dao ini.", "Today I chose the %1$s Dao. This Dao is my path."),
                  ("Di depan dupa aku bersumpah menempuh Dao %1$s.", "Before the incense I swore to walk the %1$s Dao."),
                  ("Dao %1$s menjawab panggilanku. Upacara selesai.", "The %1$s Dao answered my call. The ceremony is done.")])
h("dewa.pilih", [("Aku memilih %1$s sebagai seni dewaku.", "I chose %1$s as my divine art."),
                 ("Seni dewa %1$s terukir dalam jiwaku.", "The divine art %1$s is carved into my soul."),
                 ("Dengan %1$s, aku melangkah ke tingkat dewa.", "With %1$s I step into the divine.")])
h("jenjang.mohon", [("Aku mengajukan permohonan naik ke jenjang %1$s.", "I asked to rise to the rank of %1$s."),
                    ("Surat permohonan jenjang %1$s sudah kuserahkan.", "I handed in my request for the rank of %1$s."),
                    ("Semoga permohonanku menjadi %1$s disetujui.", "I hope my request to become %1$s is approved.")])
h("jenjang.naik", [("Aku naik ke jenjang %1$s!", "I rose to the rank of %1$s!"),
                   ("Jubah %1$s yang baru terasa berat dan membanggakan.", "The new robe of a %1$s feels heavy and proud."),
                   ("Hari ini aku resmi menjadi %1$s.", "Today I officially became a %1$s.")])
h("jenjang.disetujui_tetua", [("%1$s menyetujui kenaikanku ke %2$s.", "%1$s approved my rise to %2$s."),
                              ("Tetua %1$s menepuk bahuku: aku kini %2$s.", "Elder %1$s patted my shoulder: I am now a %2$s."),
                              ("Dengan restu %1$s, aku menjadi %2$s.", "With the blessing of %1$s, I became a %2$s.")])
h("jenjang.ditunda", [("Permohonan jenjangku ditunda. Aku harus membuktikan diri lagi.", "My rank request was postponed. I must prove myself again."),
                      ("Belum saatnya naik jenjang, kata mereka.", "Not yet time to rise, they said."),
                      ("Kecewa, tapi aku akan berusaha lebih keras.", "Disappointed, but I will try harder.")])
h("ambisi.tanding", [("Aku menantang diriku sendiri di arena latihan hari ini.", "I challenged myself in the training arena today."),
                     ("Ambisiku membara; aku mencari lawan yang lebih kuat.", "My ambition burns; I seek stronger opponents."),
                     ("Tidak boleh puas. Aku berlatih tanding sampai sore.", "No resting on laurels. I sparred until evening.")])
h("ambisi.bencana", [("Aku melewati bencana ke-%1$s. Langit menguji, aku bertahan.", "I passed calamity number %1$s. Heaven tested, I endured."),
                     ("Bencana ke-%1$s sudah lewat; tubuhku penuh luka tapi hatiku utuh.", "Calamity %1$s is past; my body is wounded but my heart is whole."),
                     ("Satu bencana lagi tertaklukkan: yang ke-%1$s.", "One more calamity conquered: the %1$s.")])
h("pil.tas", [("Aku menelan %1$s dari tasku.", "I swallowed %1$s from my bag."),
              ("%1$s terasa hangat di perut.", "%1$s felt warm in my belly."),
              ("Aku memakai %1$s; qi-ku pulih.", "I used %1$s; my qi recovered.")])
h("pil.lumbung", [("Aku mengambil %1$s dari Lumbung sekte.", "I took %1$s from the sect Granary."),
                  ("Penjaga Lumbung memberiku %1$s.", "The Granary keeper gave me %1$s."),
                  ("%1$s dari Lumbung membantu latihanku.", "%1$s from the Granary helped my training.")])
h("beli.slip", [("Aku membeli gulungan %1$s seharga %2$s NB.", "I bought the %1$s scroll for %2$s NB."),
                ("%1$s! Harganya %2$s NB, tapi sepadan.", "%1$s! It cost %2$s NB, but it was worth it."),
                ("Uang sakuku berkurang %2$s NB untuk %1$s.", "My pocket money shrank by %2$s NB for %1$s.")])
h("beli.paviliun", [("Aku membeli %1$s di paviliun seharga %2$s NB.", "I bought %1$s at the pavilion for %2$s NB."),
                    ("Paviliun menjual %1$s kepadaku: %2$s NB.", "The pavilion sold me %1$s: %2$s NB."),
                    ("%1$s untuk terobosanku, %2$s NB.", "%1$s for my breakthrough, %2$s NB.")])

# ==================================================================================================== core lines: named characters (39E)

h("tokoh.pedagang.kenal", [("Di %2$s aku berkenalan dengan %1$s, pedagang yang menjual %3$s.", "In %2$s I met %1$s, a merchant who sells %3$s."),
                           ("%1$s dari %2$s menawarkan %3$s dengan senyum lebar.", "%1$s of %2$s offered %3$s with a broad smile."),
                           ("Lapak %1$s di %2$s kini jadi langgananku.", "The stall of %1$s in %2$s is my regular now.")])
h("tokoh.pedagang.simpan", [("%1$s menyimpan %3$s khusus untukku di %2$s.", "%1$s kept %3$s aside just for me in %2$s."),
                            ("Langgananku %1$s di %2$s menyisihkan %3$s yang langka.", "My regular %1$s in %2$s set aside a rare %3$s."),
                            ("Begitu aku datang, %1$s mengeluarkan %3$s dari bawah meja.", "As soon as I came, %1$s brought %3$s out from under the table.")])
h("tokoh.pedagang.sapa", [("%1$s menyapaku dengan nama di %2$s dan memberi potongan; aku hanya membayar %3$s NB.",
                           "%1$s greeted me by name in %2$s and gave a discount; I paid only %3$s NB."),
                          ("\"Murid sekte langgananku!\" seru %1$s di %2$s.", "\"My favourite sect disciple!\" called %1$s in %2$s."),
                          ("%1$s di %2$s bercerita panjang soal harga-harga.", "%1$s in %2$s talked at length about prices.")])
h("tokoh.pencuri.lolos", [("Pencopet %1$s merampas kantongku di %2$s dan lolos. Kesal!", "The pickpocket %1$s snatched my purse in %2$s and got away. Infuriating!"),
                          ("Aku mengejar %1$s di gang-gang %2$s, tapi ia menghilang.", "I chased %1$s through the alleys of %2$s, but they vanished."),
                          ("Wajah %1$s akan kuingat. Lain kali di %2$s...", "I will remember the face of %1$s. Next time in %2$s...")])
h("tokoh.pencuri.tertangkap", [("Akhirnya! Aku menangkap %1$s, pencopet yang dulu lolos di %2$s.", "At last! I caught %1$s, the pickpocket who once escaped in %2$s."),
                               ("%1$s tidak bisa kabur kali ini; penjaga %2$s membawanya pergi.", "%1$s could not run this time; the guards of %2$s took them away."),
                               ("Nama baik Guru naik sedikit hari ini: %1$s tertangkap di %2$s.", "The master's good name rose a little today: %1$s was caught in %2$s.")])
h("tokoh.pengembara.kenal", [("Di %2$s aku bertemu %1$s, pengembara yang sudah melihat banyak gunung.", "In %2$s I met %1$s, a wanderer who has seen many mountains."),
                             ("%1$s bercerita tentang tempat-tempat jauh di kedai %2$s.", "%1$s told of far places in a teahouse of %2$s."),
                             ("Pengembara bernama %1$s berbagi teh denganku di %2$s.", "A wanderer called %1$s shared tea with me in %2$s.")])
h("tokoh.pengembara.pamit", [("%1$s berpamitan di gerbang %2$s. Entah kapan bertemu lagi.", "%1$s said goodbye at the gate of %2$s. Who knows when we meet again."),
                             ("Pengembara %1$s pergi ke barat; aku melambai dari %2$s.", "The wanderer %1$s went west; I waved from %2$s."),
                             ("%1$s meninggalkan %2$s pagi-pagi sekali.", "%1$s left %2$s very early in the morning.")])
h("tokoh.pengembara.kabar", [("%1$s memberiku kabar tentang tempat baru di luar %2$s.", "%1$s told me of a new place beyond %2$s."),
                             ("Dari %1$s aku mendengar ada reruntuhan di dekat %2$s.", "From %1$s I heard of ruins near %2$s."),
                             ("%1$s menggambar peta kecil untukku di %2$s.", "%1$s drew me a little map in %2$s.")])
h("tokoh.ahli_tua.ajar", [("Ahli tua %1$s di %2$s membetulkan kuda-kudaku. Penguasaanku naik.", "The old master %1$s in %2$s corrected my stance. My mastery rose."),
                          ("%1$s hanya mengucapkan tiga kalimat, tapi jurusku berubah.", "%1$s said only three sentences, but my technique changed."),
                          ("Di %2$s, %1$s memperlihatkan gerakan yang belum pernah kulihat.", "In %2$s, %1$s showed me a move I had never seen.")])
h("tokoh.ahli_tua.dao", [("%1$s memberiku petunjuk tentang Dao. Aku harus merenungkannya lama.", "%1$s gave me a hint about the Dao. I must ponder it long."),
                         ("Kata-kata %1$s di %2$s menyentuh inti Dao-ku.", "The words of %1$s in %2$s touched the core of my Dao."),
                         ("Ahli tua %1$s menatapku lama, lalu berkata: \"Dao-mu ada di air.\"", "The old master %1$s looked at me long, then said: \"Your Dao is in the water.\"")])
h("tokoh.saingan.kenal_menang", [("Aku mengalahkan %1$s di %2$s. Ia bersumpah membalas.", "I defeated %1$s in %2$s. They swore revenge."),
                                 ("%1$s dari sekte lain menantangku di %2$s, dan aku menang.", "%1$s of another sect challenged me in %2$s, and I won."),
                                 ("Tatapan %1$s setelah kalah di %2$s... ia tidak akan melupakannya.", "The look of %1$s after losing in %2$s... they will not forget it.")])
h("tokoh.saingan.kenal_kalah", [("Aku kalah dari %1$s di %2$s. Memalukan.", "I lost to %1$s in %2$s. Shameful."),
                                ("%1$s menjatuhkanku di depan orang banyak di %2$s.", "%1$s threw me down before a crowd in %2$s."),
                                ("Ingat nama ini: %1$s. Suatu hari aku akan membalasnya.", "Remember this name: %1$s. One day I will pay it back.")])
h("tokoh.saingan.hormat", [("%1$s dan aku saling memberi hormat di %2$s. Saingan, bukan musuh.", "%1$s and I bowed to each other in %2$s. Rivals, not enemies."),
                           ("Kini %1$s mengangguk setiap melihatku.", "Now %1$s nods whenever they see me."),
                           ("Setelah semua itu, %1$s mengajakku minum teh di %2$s.", "After all that, %1$s invited me to tea in %2$s.")])
h("tokoh.saingan.jumpa_menang", [("Pertemuan ke-%3$s dengan %1$s di %2$s: aku menang lagi.", "Meeting %3$s with %1$s in %2$s: I won again."),
                                 ("%1$s makin kuat, tapi aku masih lebih cepat.", "%1$s grows stronger, but I am still faster."),
                                 ("Aku mengalahkan %1$s untuk kesekian kalinya.", "I beat %1$s yet again.")])
h("tokoh.saingan.jumpa_kalah", [("Pertemuan ke-%3$s dengan %1$s di %2$s: aku kalah.", "Meeting %3$s with %1$s in %2$s: I lost."),
                                ("%1$s tertawa melihatku jatuh. Belum selesai.", "%1$s laughed as I fell. This is not over."),
                                ("Aku harus berlatih lebih keras sebelum bertemu %1$s lagi.", "I must train harder before I meet %1$s again.")])
h("tokoh.saingan.janji", [("%1$s dan aku berjanji bertarung sampai tuntas di arena %2$s.", "%1$s and I promised to fight it out in the arena of %2$s."),
                          ("Duel penentu dengan %1$s sudah ditetapkan.", "The deciding duel with %1$s is set."),
                          ("Tantangan %1$s kuterima. Panggung akan menjadi saksinya.", "I accepted the challenge of %1$s. The stage will be our witness.")])
h("tokoh.saingan.duel_menang", [("Duel penentu melawan %1$s di %2$s: aku menang! Dendamku lunas.", "The deciding duel against %1$s in %2$s: I won! My grudge is settled."),
                                ("%1$s berlutut dan memberi hormat. Aku menang.", "%1$s knelt and bowed. I won."),
                                ("Aku mengalahkan %1$s di depan semua orang. Hari ini milikku.", "I defeated %1$s before everyone. Today is mine.")])
h("tokoh.saingan.duel_kalah", [("Aku kalah dalam duel penentu melawan %1$s. Pahit sekali.", "I lost the deciding duel against %1$s. So bitter."),
                               ("%1$s lebih kuat. Aku akan mengurung diri dan berlatih.", "%1$s is stronger. I will shut myself away and train."),
                               ("Kekalahan dari %1$s di %2$s akan kujadikan cambuk.", "My defeat by %1$s in %2$s will be my whip.")])
h("tokoh.hati.kenal", [("Di %2$s aku bertemu %1$s. Senyumnya... aku lupa mau membeli apa.", "In %2$s I met %1$s. That smile... I forgot what I came to buy."),
                       ("%1$s menjatuhkan saputangan di pasar %2$s, dan aku mengembalikannya.", "%1$s dropped a handkerchief in the market of %2$s, and I gave it back."),
                       ("Entah kenapa aku terus memikirkan %1$s dari %2$s.", "Somehow I keep thinking of %1$s from %2$s.")])
h("tokoh.hati.jumpa", [("Aku bertemu %1$s lagi di %2$s. Kami berjalan di tepi kanal.", "I met %1$s again in %2$s. We walked along the canal."),
                       ("%1$s memberiku sebutir permen kacang di %2$s.", "%1$s gave me a peanut sweet in %2$s."),
                       ("Sore bersama %1$s terasa terlalu singkat.", "An afternoon with %1$s felt too short.")])
h("tokoh.hati.janji", [("%1$s dan aku berjanji bertemu di festival lentera %2$s.", "%1$s and I promised to meet at the lantern festival of %2$s."),
                       ("Di bawah lentera merah %2$s, %1$s berjanji menungguku.", "Under the red lanterns of %2$s, %1$s promised to wait for me."),
                       ("Festival nanti, %1$s dan aku akan melepas lentera bersama.", "At the festival, %1$s and I will release a lantern together.")])
h("tokoh.hati.datang", [("%1$s datang ke sekte sebagai calon murid! Aku tidak bisa berhenti tersenyum.", "%1$s came to the sect as a candidate! I cannot stop smiling."),
                        ("%1$s berdiri di gerbang sekte, membawa buntalan kecil.", "%1$s stood at the sect gate with a small bundle."),
                        ("Hari ini %1$s mendaftar menjadi murid. Semoga Guru menerimanya.", "Today %1$s applied to be a disciple. I hope the master accepts.")])
h("tokoh.hati.pisah", [("%1$s dan aku berpisah di jembatan %2$s. Pahit dan manis sekaligus.", "%1$s and I parted on the bridge of %2$s. Bitter and sweet at once."),
                       ("Jalan kami berbeda. Aku akan mengingat %1$s selamanya.", "Our roads differ. I will remember %1$s forever."),
                       ("%1$s pulang ke desanya. Hatiku kosong sore ini.", "%1$s went home to their village. My heart is empty this afternoon.")])
h("tokoh.desa.kenal", [("Kepala desa %1$s menyambut kami dengan teh dan ubi bakar.", "The village head %1$s welcomed us with tea and roasted yams."),
                       ("%1$s meminta tolong soal sumur desa yang kering.", "%1$s asked for help with the village's dry well."),
                       ("Desa kecil itu dipimpin %1$s, orang tua yang ramah.", "The little village is led by %1$s, a kindly old soul.")])
h("tokoh.desa.terima_kasih", [("%1$s berterima kasih berkali-kali; masalah desa terselesaikan.", "%1$s thanked us again and again; the village's trouble is solved."),
                              ("Warga desa %1$s mengantar kami sampai batas desa.", "The villagers of %1$s walked us to the village edge."),
                              ("Kepala desa %1$s memberi kami sekarung beras.", "The village head %1$s gave us a sack of rice.")])
h("tokoh.desa.minta", [("%1$s meminta kami kembali; masalah desa belum selesai.", "%1$s asked us to return; the village's trouble is not over."),
                       ("Kami gagal membantu %1$s kali ini. Aku malu.", "We failed to help %1$s this time. I am ashamed."),
                       ("%1$s tetap tersenyum, tapi matanya sedih.", "%1$s kept smiling, but their eyes were sad.")])

# ==================================================================================================== W3 detail pass: more variants


def lagi(key, pairs):
    """More variants of an existing diary base: continues base.<n> after the last one written above."""
    n = 0
    while H + key + "." + str(n) in ID:
        n += 1
    if n == 0:
        raise SystemExit("lagi() on a base without variants: " + key)
    for a, b in pairs:
        k(H + key + "." + str(n), a, b)
        n += 1


lagi("renung.rajin", [("Keringat hari ini adalah qi hari esok.", "Today's sweat is tomorrow's qi."),
                      ("Satu jurus lagi sebelum tidur tidak akan membunuhku.", "One more form before bed will not kill me.")])
lagi("renung.malas", [("Besok saja. Besok pasti lebih semangat.", "Tomorrow. I will surely feel more like it tomorrow."),
                      ("Kenapa harus terburu-buru kalau umur kultivator panjang?", "Why hurry when a cultivator lives so long?")])
lagi("renung.sombong", [("Kakak-kakak itu seharusnya belajar dariku.", "Those seniors ought to learn from me."),
                        ("Langit pun akan menunduk suatu hari nanti.", "Even heaven will bow one day.")])
lagi("renung.rendah_hati", [("Masih banyak yang lebih kuat dariku; aku hanya beruntung.", "Many are stronger than me; I was only lucky."),
                            ("Gunung tidak pernah membanggakan tingginya.", "A mountain never boasts of its height.")])
lagi("renung.setia", [("Selama Guru masih di aula, aku tidak akan pergi ke mana-mana.", "As long as the master is at the hall, I am going nowhere."),
                      ("Nama sekte di jubahku lebih berat dari namaku sendiri.", "The sect's name on my robe weighs more than my own.")])
lagi("renung.licik", [("Orang jujur makan terakhir. Aku tidak suka lapar.", "Honest people eat last. I do not like being hungry."),
                      ("Asal Guru tidak tahu, tidak ada yang salah.", "As long as the master does not know, nothing is wrong.")])
lagi("renung.berani", [("Kalau jatuh, aku tinggal bangun lagi.", "If I fall, I simply get up again."),
                       ("Ketakutan hanya bayangan; tebas saja.", "Fear is only a shadow; cut through it.")])
lagi("renung.penakut", [("Untung tidak ada yang melihat lututku gemetar.", "Lucky no one saw my knees shaking."),
                        ("Malam ini aku tidur dengan lentera menyala.", "Tonight I sleep with the lantern lit.")])
lagi("renung.ambisius", [("Puncak masih jauh, tapi aku sudah bisa melihatnya.", "The summit is still far, but I can see it already."),
                         ("Suatu hari namaku akan tertulis di prasasti sekte.", "One day my name will be carved on the sect's stele.")])
lagi("renung.tenang", [("Angin datang dan pergi; aku tetap duduk.", "The wind comes and goes; I stay seated."),
                       ("Tidak ada yang perlu dikejar malam ini.", "There is nothing to chase tonight.")])
lagi("renung.penyayang", [("Besok aku akan membawakan bubur untuk adik yang sakit.", "Tomorrow I will bring porridge to the sick junior."),
                          ("Kucing aula sudah makan belum, ya?", "I wonder if the hall cat has eaten yet.")])
lagi("renung.pendendam", [("Setiap hinaan kucatat. Setiap catatan akan kubayar.", "Every insult I write down. Every note I will repay."),
                          ("Senyum mereka hari ini akan menjadi tangis mereka besok.", "Their smiles today will be their tears tomorrow.")])

lagi("tutup.ranah", [("Setiap napas mendekatkanku ke ranah berikutnya.", "Every breath brings me closer to the next realm.")])
lagi("tutup.dao", [("Suatu malam nanti Dao-ku akan menyebut namanya sendiri.", "Some night my Dao will speak its own name.")])
lagi("tutup.teknik", [("Gulungan berikutnya sudah menungguku di paviliun.", "The next scroll is already waiting for me in the pavilion.")])
lagi("tutup.pusaka", [("Pusaka yang layak akan datang pada tangan yang siap.", "A worthy treasure comes to a ready hand.")])
lagi("tutup.juara", [("Aku sudah bisa mendengar sorak penonton di arena.", "I can already hear the crowd cheering in the arena.")])
lagi("tutup.dendam", [("Hutang itu akan kubayar lunas, dengan bunganya.", "That debt I will repay in full, with interest.")])
lagi("tutup.jodoh", [("Mungkin festival lentera berikutnya akan berbeda.", "Perhaps the next lantern festival will be different.")])
lagi("tutup.guru", [("Satu hari nanti Guru akan menyebut namaku dengan bangga.", "One day the master will speak my name with pride.")])
lagi("tutup.jenjang", [("Papan nama aula menunggu namaku dipahat lebih tinggi.", "The hall's name board waits for my name to be carved higher.")])
lagi("tutup.kekayaan", [("Satu batu hari ini, seribu batu kelak.", "One stone today, a thousand someday.")])
lagi("tutup.binatang", [("Suatu hari seekor binatang roh akan memilih tidur di kakiku.", "One day a spirit beast will choose to sleep at my feet.")])
lagi("tutup.rahasia", [("Peta di bawah bantalku penuh tanda silang menuju alam rahasia.", "The map under my pillow is full of crosses leading to secret realms.")])
lagi("tutup.menolong", [("Tangan yang kuat ada untuk mengangkat yang jatuh.", "Strong hands exist to lift the fallen.")])
lagi("tutup.karya", [("Tanganku belum puas; karya berikutnya harus lebih baik.", "My hands are not satisfied; the next work must be better.")])

lagi("tembus.siap", [("Aku mandi air dingin, mengganti jubah, dan menyalakan dupa. Besok %1$s.", "I bathed in cold water, changed my robe and lit incense. Tomorrow, %1$s."),
                     ("Sahabat-sahabatku sudah berjanji menjaga. Tidak ada lagi alasan menunda %1$s.", "My friends have promised to stand guard. No more reasons to delay %1$s.")])
lagi("tembus.tergesa", [("Bisikan di dadaku berkata sekarang atau tidak sama sekali. %1$s, aku datang.", "A voice in my chest says now or never. %1$s, here I come."),
                        ("Pilku kurang satu, penjagaku kurang satu. Tapi aku tetap duduk untuk %1$s.", "One pill short, one guard short. But I still sat down for %1$s.")])
lagi("tembus.berhasil", [("Petir ketujuh menyambar dan hilang. Aku membuka mata di %1$s, dan %2$s sahabatku bersorak.", "The seventh bolt struck and was gone. I opened my eyes in %1$s, and my %2$s friends cheered."),
                         ("Tulang-tulangku berderak seperti es mencair. %1$s! %2$s penjaga tidak sekali pun meninggalkan tempatnya.", "My bones cracked like melting ice. %1$s! %2$s guards never once left their posts.")])
lagi("tembus.gagal", [("Aku terbangun di kamar dengan mulut berasa darah. %1$s masih di luar jangkauan.", "I woke in my room tasting blood. %1$s is still out of reach."),
                      ("Kata tabib sekte aku beruntung masih hidup. %1$s harus menunggu bertahun-tahun lagi, mungkin.", "The sect healer says I am lucky to be alive. %1$s must wait years more, perhaps.")])
lagi("tembus.urung", [("Langit mendung dan hatiku ikut mendung. %1$s kutunda.", "The sky clouded over and so did my heart. I put %1$s off."),
                      ("Saat hendak duduk, aku teringat satu hal yang belum selesai. %1$s bisa menunggu.", "As I went to sit, I remembered something unfinished. %1$s can wait.")])
lagi("tembus.batal_tempur", [("Lonceng bahaya berbunyi; aku berdiri dan meraih pedang. %1$s kutinggalkan di atas tikar.", "The alarm bell rang; I stood and grabbed my sword. %1$s I left on the mat."),
                             ("Aula diserang. Mana mungkin aku menembus %1$s sementara saudara-saudaraku bertarung.", "The hall is under attack. How could I break through to %1$s while my brothers and sisters fight.")])
lagi("tembus.batal_lemah", [("Tanganku masih gemetar sejak kemarin. Menembus %1$s sekarang sama saja bunuh diri.", "My hands have shaken since yesterday. Trying %1$s now would be suicide."),
                            ("Aku jatuh pingsan saat menghimpun qi. Tabib melarangku mencoba %1$s seminggu ini.", "I fainted gathering qi. The healer forbade me from trying %1$s this week.")])
lagi("tembus.tunda_belanja", [("Daftar belanjaku untuk %1$s panjang sekali. Semoga uang sakuku cukup.", "My shopping list for %1$s is very long. I hope my pocket money is enough."),
                              ("Pil Fondasi habis di Lumbung. Kota terdekat harus menjualnya, demi %1$s.", "The Granary is out of Foundation Pills. The nearest town must sell them, for %1$s.")])
lagi("hati.lulus", [("Iblis hati menawarkan semua yang pernah kuinginkan. Aku menolaknya, dan ia pecah seperti kaca.", "The heart demon offered all I ever wanted. I refused, and it shattered like glass."),
                    ("Bayangan diriku yang lebih kuat menantangku. Aku memaafkannya, dan ia menghilang. %1$s menunggu.", "A stronger shadow of myself challenged me. I forgave it, and it vanished. %1$s awaits.")])
lagi("hati.gagal", [("Aku menangis seperti anak kecil di tengah ujian. Mungkin hatiku masih terlalu muda untuk %1$s.", "I cried like a child in the middle of the trial. Perhaps my heart is still too young for %1$s."),
                    ("Iblis hati memakai wajah Guru dan aku goyah. Aku harus berdamai dengan diriku dulu.", "The heart demon wore the master's face and I wavered. I must make peace with myself first.")])
lagi("hufa.jaga_berhasil", [("Petir mengarah ke %1$s dan aku menangkisnya dengan seluruh qi-ku. Kini ia di %2$s.", "Lightning aimed at %1$s and I turned it aside with all my qi. Now they stand in %2$s."),
                            ("%1$s memelukku setelah mencapai %2$s. Jubahku hangus sedikit, tapi tidak apa-apa.", "%1$s hugged me after reaching %2$s. My robe is a little singed, but that is fine.")])
lagi("tutup.masuk", [("Aku menyerahkan kunci kamar pada sahabatku. %1$s hari, lalu aku keluar sebagai orang baru.", "I gave my room key to my friend. %1$s days, then I come out a new person."),
                     ("Bekal %1$s hari kususun di sudut: air, beras, batu roh. Sunyi, aku datang.", "Supplies for %1$s days stacked in the corner: water, rice, spirit stones. Silence, here I come.")])
lagi("tutup.keluar", [("Cahaya matahari menyilaukan setelah %1$s hari. Adik-adik bilang mataku berubah.", "Sunlight dazzled me after %1$s days. The juniors say my eyes have changed."),
                      ("Rambutku memanjang dan qi-ku memadat. %1$s hari yang terasa seperti setahun.", "My hair grew and my qi condensed. %1$s days that felt like a year.")])
lagi("tutup.terganggu", [("Hari ke-%1$s, seseorang berteriak memanggilku. Qi yang hampir padat buyar lagi.", "Day %1$s, someone shouted my name. Qi that had nearly condensed scattered again."),
                         ("Gempa kecil di hari ke-%1$s membuyarkan segalanya. Aku harus mulai dari awal.", "A small tremor on day %1$s undid everything. I must start over.")])
lagi("renung.selesai", [("Daun jatuh di pangkuanku tiga kali selama aku merenung. %1$s, katanya, sudah dekat.", "A leaf fell into my lap three times while I contemplated. %1$s, it seemed to say, is near."),
                        ("Aku menulis satu kalimat tentang %1$s di batu tepi kolam, lalu menghapusnya. Cukup kuingat.", "I wrote one sentence about %1$s on a pond-side stone, then wiped it. Remembering is enough.")])
lagi("serap.mulai", [("Batu roh senilai %1$s NB meredup satu per satu di telapak tanganku. Rasanya seperti minum cahaya.", "Spirit stones worth %1$s NB dimmed one by one in my palms. Like drinking light."),
                     ("Kantongku berkurang %1$s NB, dantianku bertambah. Pertukaran yang adil.", "My purse lost %1$s NB, my dantian gained. A fair trade.")])
lagi("belajar.tas_mulai", [("Aku menempelkan lempeng giok %1$s ke dahi dan huruf-hurufnya mengalir seperti air.", "I pressed the %1$s jade slip to my forehead and its characters flowed like water."),
                           ("Halaman pertama %1$s sudah kubaca sepuluh kali. Halaman kedua besok.", "I have read the first page of %1$s ten times. The second page tomorrow.")])
lagi("belajar.tas_selesai", [("Gerakan terakhir %1$s keluar dengan sendirinya, dan angin di halaman ikut berputar.", "The last movement of %1$s came out on its own, and the courtyard wind turned with it."),
                             ("Kakak Senior menonton %1$s-ku dan mengangguk sekali. Itu cukup.", "A senior watched my %1$s and nodded once. That was enough.")])
lagi("belajar.pencerahan", [("Seekor burung bangau mendarat di kolam, dan gerakannya adalah %1$s. Aku tertawa keras.", "A crane landed on the pond, and its movement was %1$s. I laughed out loud."),
                            ("Tanpa gulungan, tanpa guru: %1$s lahir dari keheningan malam ini.", "No scroll, no teacher: %1$s was born from tonight's silence.")])
lagi("belajar.kejar", [("Di sela tugas, aku mencuri waktu untuk %1$s. Sedikit demi sedikit, bukit pun terdaki.", "Between chores I stole time for %1$s. Little by little even a hill is climbed."),
                       ("Catatan tentang %1$s memenuhi tiga halaman bukuku.", "Notes on %1$s fill three pages of my book.")])
lagi("ajar.diajar", [("%1$s mengulang %2$s sepuluh kali tanpa mengeluh sampai aku paham.", "%1$s repeated %2$s ten times without complaint until I understood."),
                     ("Aku salah tiga kali, dan %1$s tetap tersenyum. %2$s akhirnya masuk.", "I got it wrong three times, and %1$s kept smiling. %2$s finally sank in.")])
lagi("ajar.mengajar", [("%1$s bertanya hal yang belum pernah kupikirkan tentang %2$s. Aku belajar sebanyak ia.", "%1$s asked something about %2$s I had never considered. I learned as much as they did."),
                       ("Melihat %1$s memperagakan %2$s dengan benar membuatku lebih senang daripada menguasainya sendiri.", "Seeing %1$s perform %2$s correctly made me happier than mastering it myself.")])
lagi("ajar.tetua", [("Tetua %1$s keras dan tidak sabar, tapi %2$s yang diajarkannya tidak akan kulupakan.", "Elder %1$s was strict and impatient, but the %2$s they taught I will never forget."),
                    ("%1$s menutup pelajaran %2$s dengan satu kalimat: latihan, bukan bakat.", "%1$s closed the %2$s lesson with one sentence: practice, not talent.")])
lagi("dao.bimbang", [("Pedang atau air, api atau sunyi? Dao %1$s terus kembali ke pikiranku.", "Sword or water, fire or silence? The %1$s Dao keeps coming back to my mind."),
                     ("Aku bertanya pada bintang tentang Dao %1$s. Mereka hanya berkedip.", "I asked the stars about the %1$s Dao. They only twinkled.")])
lagi("dao.upacara", [("Pilar cahaya turun dari langit saat aku menyebut Dao %1$s. Seluruh aula keluar menonton.", "A pillar of light came down from the sky as I named the %1$s Dao. The whole hall came out to watch."),
                     ("Dao %1$s. Dua kata, tapi seluruh hidupku ada di dalamnya.", "The %1$s Dao. A few words, but my whole life is inside them.")])
lagi("dewa.pilih", [("Saat %1$s menyatu dengan jiwaku, aku merasakan kehangatan seperti pagi pertama musim semi.", "As %1$s joined my soul, I felt a warmth like the first morning of spring."),
                    ("Seni dewa %1$s. Kakak-kakak bilang pilihan yang berani. Aku bilang pilihan yang jujur.", "The divine art %1$s. The seniors call it a bold choice. I call it an honest one.")])
lagi("jenjang.mohon", [("Aku merapikan jubah dan menyerahkan permohonan jenjang %1$s dengan kedua tangan.", "I straightened my robe and handed in my request for %1$s with both hands."),
                       ("Daftar jasaku untuk jenjang %1$s sudah lengkap. Sekarang tinggal menunggu.", "My list of merits for the rank of %1$s is complete. Now I wait.")])
lagi("jenjang.naik", [("Adik-adik memanggilku %1$s dan aku menoleh ke belakang, mencari orang lain. Ternyata aku.", "The juniors called me %1$s and I looked behind me for someone else. It was me."),
                      ("Lencana %1$s kupasang di dada. Tanganku agak gemetar.", "I pinned the badge of a %1$s to my chest. My hand shook a little.")])
lagi("jenjang.disetujui_tetua", [("%1$s membaca permohonanku, mengangkat alis, lalu mengecap: %2$s.", "%1$s read my request, raised an eyebrow, then stamped it: %2$s."),
                                 ("Tanpa Guru, %1$s yang menyetujui. Aku sekarang %2$s, dan akan kuceritakan saat Guru pulang.", "Without the master, %1$s approved it. I am now a %2$s, and I will tell the master when they return.")])
lagi("jenjang.ditunda", [("Tetua bilang jasaku belum cukup. Aku akan mengumpulkannya satu per satu.", "The elder says my merit is not enough yet. I will gather it one by one."),
                         ("Ditunda lagi. Aku menyapu halaman sampai malam untuk menenangkan diri.", "Postponed again. I swept the courtyard until night to calm myself.")])
lagi("ambisi.tanding", [("Lima lawan, lima kemenangan. Aku masih lapar.", "Five opponents, five wins. I am still hungry."),
                        ("Aku menantang kakak senior yang tak pernah kalah. Kali ini aku hampir menang.", "I challenged the senior who never loses. This time I almost won.")])
lagi("ambisi.bencana", [("Bencana ke-%1$s datang seperti hujan api. Aku berdiri di tengahnya dan tertawa.", "Calamity %1$s came like a rain of fire. I stood in the middle of it and laughed."),
                        ("Langit mencoba menghapusku untuk ke-%1$s kalinya. Langit gagal lagi.", "Heaven tried to erase me for the %1$s time. Heaven failed again.")])
lagi("pil.tas", [("%1$s pahit sekali, tapi qi-ku langsung bangkit seperti api disiram minyak.", "%1$s was terribly bitter, but my qi rose at once like fire fed with oil."),
                 ("Aku menyimpan %1$s untuk saat genting. Hari ini saat itu datang.", "I had saved %1$s for a dire moment. Today that moment came.")])
lagi("pil.lumbung", [("Penjaga Lumbung mencatat namaku sebelum menyerahkan %1$s. Aku akan menggantinya suatu hari.", "The Granary keeper wrote down my name before handing over %1$s. I will repay it someday."),
                     ("%1$s dari Lumbung sekte. Rasanya seperti makan masakan rumah.", "%1$s from the sect Granary. It tasted like home cooking.")])
lagi("beli.slip", [("Gulungan %1$s, %2$s NB. Aku memeluknya sepanjang jalan pulang.", "The %1$s scroll, %2$s NB. I hugged it all the way home."),
                   ("Aku menabung tiga minggu untuk %1$s. %2$s NB, dan tidak ada penyesalan.", "I saved three weeks for %1$s. %2$s NB, and no regrets.")])
lagi("beli.paviliun", [("Penjaga paviliun membungkus %1$s dengan kertas merah. %2$s NB, dan ia mendoakan terobosanku.", "The pavilion keeper wrapped %1$s in red paper. %2$s NB, and they wished my breakthrough well."),
                       ("%1$s di paviliun lebih mahal dari kota, %2$s NB, tapi aku tidak sempat turun gunung.", "%1$s at the pavilion costs more than in town, %2$s NB, but I had no time to go down the mountain.")])

lagi("tokoh.pedagang.kenal", [("%1$s menawariku teh sebelum menawarkan %3$s. Pedagang yang cerdik di %2$s.", "%1$s offered me tea before offering %3$s. A clever merchant in %2$s."),
                              ("Aku kembali ke lapak %1$s di %2$s tiga kali. Kini ia hafal aku suka %3$s.", "I went back to the stall of %1$s in %2$s three times. Now they know I like %3$s.")])
lagi("tokoh.pedagang.simpan", [("%1$s berbisik: barang ini tidak kujual ke sembarang orang. %3$s, khusus untukmu.", "%1$s whispered: I do not sell this to just anyone. %3$s, just for you."),
                               ("Di balik tirai lapaknya di %2$s, %1$s menyimpan %3$s untuk langganan setia.", "Behind the curtain of their stall in %2$s, %1$s keeps %3$s for loyal regulars.")])
lagi("tokoh.pedagang.sapa", [("%1$s menuang teh untukku sebelum aku sempat bicara. Di %2$s aku sudah seperti keluarga.", "%1$s poured me tea before I could speak. In %2$s I am almost family."),
                             ("Potongan harga lagi dari %1$s: hanya %3$s NB. Langganan memang ada untungnya.", "Another discount from %1$s: only %3$s NB. Being a regular pays.")])
lagi("tokoh.pencuri.lolos", [("%1$s menjulurkan lidah dari atas atap %2$s sebelum menghilang. Aku menghentakkan kaki.", "%1$s stuck out their tongue from a rooftop in %2$s before vanishing. I stamped my foot."),
                             ("Kantongku dan %1$s lenyap bersama di keramaian %2$s.", "My purse and %1$s vanished together in the crowds of %2$s.")])
lagi("tokoh.pencuri.tertangkap", [("Aku mengenali gaya jalan %1$s di pasar %2$s dan mencengkeram kerahnya. Ia pucat.", "I recognised the walk of %1$s in the market of %2$s and grabbed their collar. They went pale."),
                                  ("%1$s mengembalikan semua yang dulu dicurinya di %2$s, ditambah permintaan maaf.", "%1$s returned everything once stolen in %2$s, plus an apology.")])
lagi("tokoh.pengembara.kenal", [("%1$s membawa tongkat yang diukir dengan nama seratus kota. %2$s salah satunya.", "%1$s carries a staff carved with the names of a hundred towns. %2$s is one of them."),
                                ("Pengembara %1$s mengajariku cara membaca bintang di atap penginapan %2$s.", "The wanderer %1$s taught me to read the stars on an inn roof in %2$s.")])
lagi("tokoh.pengembara.pamit", [("%1$s menitipkan sebuah batu kecil padaku di %2$s: kembalikan saat kita bertemu lagi.", "%1$s left me a small stone in %2$s: give it back when we meet again."),
                                ("Langkah %1$s menghilang di jalan keluar %2$s. Aku iri pada kebebasannya.", "The steps of %1$s faded on the road out of %2$s. I envy their freedom.")])
lagi("tokoh.pengembara.kabar", [("%1$s bercerita ada mata air roh tersembunyi tiga hari dari %2$s.", "%1$s says there is a hidden spirit spring three days from %2$s."),
                                ("Peta coretan %1$s menunjuk tempat yang tidak ada di peta sekte, dekat %2$s.", "The scribbled map of %1$s points to a place not on the sect's maps, near %2$s.")])
lagi("tokoh.ahli_tua.ajar", [("%1$s menyentuh pergelangan tanganku dan aliran qi-ku langsung lurus. Di %2$s, keajaiban kecil.", "%1$s touched my wrist and my qi flow straightened at once. In %2$s, a small miracle."),
                             ("Ahli tua %1$s menertawakan jurusku, lalu memperbaikinya dalam sekejap.", "The old master %1$s laughed at my form, then fixed it in an instant.")])
lagi("tokoh.ahli_tua.dao", [("%1$s menulis satu huruf di pasir %2$s dan berkata: ini Dao-mu. Ombak menghapusnya.", "%1$s wrote a single character in the sand of %2$s and said: this is your Dao. The waves erased it."),
                            ("Sebelum pergi, %1$s berbisik satu kata tentang Dao. Aku akan memikirkannya sepuluh tahun.", "Before leaving, %1$s whispered one word about the Dao. I will think about it for ten years.")])
lagi("tokoh.saingan.kenal_menang", [("%1$s meludah ke tanah setelah kalah di %2$s. Kurasa kami akan bertemu lagi.", "%1$s spat on the ground after losing in %2$s. I think we will meet again."),
                                    ("Aku menang melawan %1$s di %2$s, tapi pukulannya masih terasa di bahuku.", "I won against %1$s in %2$s, but their blow still aches in my shoulder.")])
lagi("tokoh.saingan.kenal_kalah", [("%1$s bahkan tidak berkeringat saat mengalahkanku di %2$s.", "%1$s did not even break a sweat beating me in %2$s."),
                                   ("Di %2$s aku belajar satu nama dan satu rasa malu: %1$s.", "In %2$s I learned one name and one shame: %1$s.")])
lagi("tokoh.saingan.hormat", [("%1$s menuang arak untukku di %2$s. Musuh kemarin, kawan minum hari ini.", "%1$s poured me wine in %2$s. Yesterday's enemy, today's drinking companion."),
                              ("Saat orang lain menghina sekteku, %1$s yang membelaku. Dunia memang aneh.", "When others insulted my sect, it was %1$s who defended me. The world is strange.")])
lagi("tokoh.saingan.jumpa_menang", [("%1$s datang dengan jurus baru, tapi aku sudah membaca gerakannya.", "%1$s came with a new form, but I had already read their movements."),
                                    ("Kemenangan lagi atas %1$s di %2$s. Tapi ia makin dekat.", "Another win over %1$s in %2$s. But they are getting closer.")])
lagi("tokoh.saingan.jumpa_kalah", [("%1$s menunggu di gerbang %2$s seolah tahu aku akan lewat. Aku kalah lagi.", "%1$s waited at the gate of %2$s as if they knew I would pass. I lost again."),
                                   ("Kekalahan ke sekian dari %1$s. Aku mencatat setiap jurusnya.", "Yet another loss to %1$s. I noted every one of their forms.")])
lagi("tokoh.saingan.janji", [("Kami menukar sapu tangan sebagai tanda: duel di %2$s, tanpa mundur, %1$s dan aku.", "We exchanged handkerchiefs as a pledge: a duel in %2$s, no retreat, %1$s and I."),
                             ("%1$s menancapkan pedang di tanah: hari itu salah satu dari kita akan membungkuk.", "%1$s drove a sword into the ground: that day one of us will bow.")])
lagi("tokoh.saingan.duel_menang", [("Pedang %1$s terlepas dan jatuh di kakiku. Kerumunan %2$s diam, lalu bersorak.", "The sword of %1$s fell from their hand to my feet. The crowd of %2$s went silent, then roared."),
                                   ("%1$s mengulurkan tangan setelah kalah. Aku menyambutnya. Cerita kami selesai dengan baik.", "%1$s held out a hand after losing. I took it. Our story ended well.")])
lagi("tokoh.saingan.duel_kalah", [("%1$s berdiri di atasku dan berkata: belum waktunya. Aku akan membuktikan ia salah.", "%1$s stood over me and said: not yet your time. I will prove them wrong."),
                                  ("Seluruh %2$s melihatku jatuh di depan %1$s. Aku akan bangkit lebih tinggi.", "All of %2$s saw me fall before %1$s. I will rise higher.")])
lagi("tokoh.hati.kenal", [("%1$s membeli buah yang sama denganku di %2$s, dan kami tertawa bersama. Itu saja. Tapi aku terus mengingatnya.", "%1$s bought the same fruit as me in %2$s, and we laughed together. That was all. But I keep remembering."),
                          ("Suara %1$s seperti lonceng angin di %2$s. Aku lupa menawar.", "The voice of %1$s was like a wind chime in %2$s. I forgot to haggle.")])
lagi("tokoh.hati.jumpa", [("%1$s menungguku di jembatan %2$s, pura-pura melihat ikan.", "%1$s waited for me on the bridge of %2$s, pretending to watch the fish."),
                          ("Kami makan mi dari satu mangkuk di %2$s, %1$s dan aku. Kuahnya terlalu pedas, tapi tidak ada yang mengeluh.", "We ate noodles from one bowl in %2$s, %1$s and I. The broth was too spicy, but no one complained.")])
lagi("tokoh.hati.janji", [("%1$s mengikat benang merah di jariku: sampai festival di %2$s.", "%1$s tied a red thread around my finger: until the festival in %2$s."),
                          ("Janji di bawah pohon persik %2$s: %1$s akan datang saat lentera pertama menyala.", "A promise under the peach tree of %2$s: %1$s will come when the first lantern is lit.")])
lagi("tokoh.hati.datang", [("Aku melihat %1$s di antrean calon murid dan pura-pura tidak kenal. Wajahku merah sekali.", "I saw %1$s in the line of candidates and pretended not to know them. My face was very red."),
                           ("%1$s menempuh perjalanan jauh hanya untuk mengetuk gerbang kita. Aku berdoa Guru menerimanya.", "%1$s travelled far just to knock on our gate. I pray the master accepts them.")])
lagi("tokoh.hati.pisah", [("%1$s memberiku sapu tangan bersulam bunga di %2$s. Lalu ia pergi, dan aku tidak mengejarnya.", "%1$s gave me a handkerchief embroidered with flowers in %2$s. Then they left, and I did not follow."),
                          ("Kami berjanji tidak menangis. Kami berdua melanggarnya di jembatan %2$s, %1$s dan aku.", "We promised not to cry. We both broke it on the bridge of %2$s, %1$s and I.")])
lagi("tokoh.desa.kenal", [("Anak-anak desa %1$s mengikuti kami ke mana-mana, menanyakan apakah kami bisa terbang.", "The children of %1$s's village followed us everywhere, asking if we could fly."),
                          ("Kepala desa %1$s menunjukkan ladang yang rusak dan bercerita sambil menahan tangis.", "The village head %1$s showed us the ruined fields and told their story holding back tears.")])
lagi("tokoh.desa.terima_kasih", [("Warga desa %1$s menyanyikan lagu panen untuk kami. Aku tidak tahu harus berbuat apa dengan tanganku.", "The villagers of %1$s sang a harvest song for us. I did not know what to do with my hands."),
                                 ("%1$s menggenggam tanganku lama sekali. Desa itu akan baik-baik saja sekarang.", "%1$s held my hand for a long time. That village will be all right now.")])
lagi("tokoh.desa.minta", [("Mata %1$s penuh harap saat kami pergi. Aku harus kembali dengan lebih kuat.", "The eyes of %1$s were full of hope as we left. I must come back stronger."),
                          ("%1$s mengantar kami dengan senyum yang dipaksakan. Kami belum cukup.", "%1$s saw us off with a forced smile. We were not enough yet.")])

# ==================================================================================================== the op test samples and the fallback

k(H + "contoh.belanja", "Pedagang pil %2$s meminta %4$s batu untuk sebutir %3$s; aku menawar sampai %5$s dan ia menggerutu sambil membungkusnya.",
  "The pill merchant %2$s asked %4$s stones for one %3$s; I haggled down to %5$s and they grumbled as they wrapped it.")
k(H + "contoh.purnama", "Kakak Senior %1$s menembus %2$s di halaman, dan aku berdiri menjaganya sampai petir terakhir. Aku iri, sedikit. Tapi lebih banyak bangga.",
  "Senior %1$s broke through to %2$s in the courtyard, and I stood guard to the last bolt. A little envious. But mostly proud.")
k(H + "contoh.begal", "Di jalan pulang dari reruntuhan, %2$s begal menghadang kami. %1$s menangkis dua, aku menjatuhkan yang ketiga. Kami tertawa sepanjang sisa jalan.",
  "On the way home from the ruins, %2$s bandits blocked us. %1$s parried two, I brought down the third. We laughed the rest of the way.")
k(H + "contoh.teh", "%1$s menyeduh teh krisan di beranda sementara salju turun. Kami tidak banyak bicara; tidak perlu.",
  "%1$s brewed chrysanthemum tea on the veranda while the snow fell. We hardly spoke; there was no need.")
h("umum", [("Hari ini diisi dengan %1$s.", "Today was filled with %1$s."),
           ("Aku menghabiskan hari dengan %1$s.", "I spent the day on %1$s."),
           ("Tidak banyak yang terjadi, selain %1$s.", "Not much happened, besides %1$s."),
           ("Hari yang biasa: %1$s, lalu istirahat.", "An ordinary day: %1$s, then rest.")])


# ==================================================================================================== write


def check():
    """Placeholders must match between the languages; the en and id key sets are equal by construction."""
    bad = 0
    for key in ID:
        a = sorted(set(re.findall(r"%(\d+)\$s", ID[key])))
        b = sorted(set(re.findall(r"%(\d+)\$s", EN[key])))
        if a != b:
            print("placeholder mismatch", key, a, b)
            bad += 1
        if ID[key].count("%") != ID[key].count("%%") * 2 + len(re.findall(r"%\d+\$s", ID[key])):
            print("stray % in", key)
            bad += 1
    return bad


def main():
    bad = check()
    os.makedirs(OUT, exist_ok=True)
    for lang, d in (("id_id", ID), ("en_us", EN)):
        with open(os.path.join(OUT, "sekte_mandiri_cerita.%s.json" % lang), "w", encoding="utf-8", newline="\n") as f:
            json.dump(dict(sorted(d.items())), f, ensure_ascii=False, indent=2)
            f.write("\n")
    diary = sum(1 for key in ID if key.startswith(H))
    print("keys %d (diary %d), problems %d" % (len(ID), diary, bad))
    if bad:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
