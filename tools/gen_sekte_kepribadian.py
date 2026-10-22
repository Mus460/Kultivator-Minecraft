"""Module P of Sekte yang Hidup (DESIGN 36.2.1, 36.4): Kepribadian & Kesetiaan. Writes, from this one script:

  lang-src/sekte_kepribadian.{id_id,en_us}.json      every text of module P (Indonesian primary, identical keys)
  assets/kultivator/textures/sekte/kepribadian/gelembung.png   thought-bubble atlas 128x64 (4 x 2 cells of 32 px):
        0 the cloud bubble, 1 heart, 2 rain cloud, 3 storm with a bolt, 4 red mark, 5 tear, 6 star, 7 cloth bundle
  assets/kultivator/textures/sekte/kepribadian/sifat.png       trait seals 64x48 (4 x 3 cells of 16 px, Sifat ordinal order)
  assets/kultivator/textures/item/fx/sekte_kepribadian_kain.png indigo sashiko cloth of the deserter's bundle (16x16)
  assets/kultivator/items/fx/sekte_kepribadian_buntalan.json + models/item/fx/sekte_kepribadian_buntalan.json
        the FormModels piece of activity 22 (a stick with a knotted cloth bundle at its end)
  data/kultivator/advancement/sekte/kepribadian_{sifat,puas,rukun}.json   the Kitab Panduan checks (minecraft:impossible)

Run with: python tools/gen_sekte_kepribadian.py   (no dependencies; uses tools/png.py)
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png import Image  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "kultivator")
DATA = os.path.join(REPO, "src", "main", "resources", "data", "kultivator")
LANG = os.path.join(REPO, "lang-src")

# =============================================================================================== lang

P = "kultivator.sekte.kepribadian."
texts = {}


def put(key, idn, en):
    if key in texts:
        raise SystemExit("duplicate key " + key)
    texts[key] = (idn, en)


# ---- the tab
put(P + "tab", "Hati", "Heart")
put(P + "tab.short", "Hati", "Heart")
put(P + "tab.kosong", "Hati murid ini belum terbaca. Tunggu sebentar di dekatnya.", "This disciple's heart is not read yet. Stay near it for a moment.")
put(P + "tab.watak", "Watak", "Traits")
put(P + "tab.ingatan", "Ingatan terkuat", "Strongest memories")
put(P + "tab.ingatan.kosong", "Belum ada yang membekas.", "Nothing has left a mark yet.")
put(P + "tab.lalu", "%s hr", "%sd")
put(P + "tab.bobot", "Bobot %s · dari %s", "Weight %s · from %s")
put(P + "tab.bujuk", "Dibujuk %s untuk membelot", "Lured to defect by %s")
put(P + "tab.hati", "Kesetiaan", "Loyalty")
put(P + "tab.setia_singkat", "setia", "loyal")
put(P + "tab.kesetiaan", "Kesetiaan %s", "Loyalty %s")
put(P + "tab.kesetiaan.desc", "Kepuasan bercampur Ikatan guru-murid. Di bawah 50 gelisah, di bawah 30 membangkang.",
    "Contentment blended with the master bond. Below 50 restless, below 30 defiant.")
put(P + "tab.ambang", "Di bawah %s ia bersiap pergi.", "Below %s it prepares to leave.")
put(P + "tab.keadaan.setia", "Hatinya tenang di sekte ini.", "Its heart is at peace in this sect.")
put(P + "tab.keadaan.gelisah", "Ada yang mengganjal; ia sering melamun.", "Something weighs on it; it often stares into the distance.")
put(P + "tab.keadaan.membangkang", "Menolak tugas dan separuh perintah.", "Refuses duties and half the orders.")
put(P + "tab.keadaan.akan_pergi", "Diam-diam bersiap meninggalkan sekte.", "Quietly preparing to leave the sect.")
put(P + "tab.sisa", "Pergi dalam %s hari bila tidak dirawat!", "Leaves in %s days unless cared for!")
put(P + "tab.terikat", "Terikat Segel Jiwa / pewaris: tidak akan pergi.", "Bound by the Soul Seal / heir: will never leave.")
put(P + "tab.pend.1", "Akan menantangmu berduel!", "Will challenge you to a duel!")
put(P + "tab.pend.2", "Sedang berduel denganmu.", "Dueling you now.")
put(P + "tab.pend.3", "Menunggu malam untuk minggat…", "Waiting for nightfall to slip away…")
put(P + "tab.pend.4", "Berpamitan…", "Saying farewell…")
put(P + "tab.pend.5", "Meminta izin mengembara.", "Asking leave to wander.")
put(P + "tab.pend.6", "Akan membelot ke sekte lain!", "About to defect to another sect!")
put(P + "tab.kepuasan", "Kepuasan %s", "Contentment %s")
put(P + "tab.kepuasan.desc", "Naik saat diajar, diberi pil, bermeditasi bersamamu, dipuji dan diajak bicara. Turun saat dipukul, diabaikan, dipaksa bekerja atau dipermalukan.",
    "Rises when taught, given pills, meditating beside you, praised and talked to. Falls when struck, neglected, overworked or shamed.")
put(P + "tab.ikatan", "Ikatan guru-murid %s", "Master bond %s")
put(P + "tab.ikatan.desc", "Ikatan yang sudah ada (mengajar +5, pil +2, meditasi bersama). Ikatan kuat menjaga kesetiaan.",
    "The existing bond (teaching +5, a pill +2, shared meditation). A strong bond holds loyalty.")
put(P + "tab.kerja", "Semangat kerja ×%s", "Work spirit ×%s")
put(P + "tab.rawat", "Perhatian hari ini: %s", "Care today: %s")
put(P + "tab.abai", "Merasa diabaikan (%s/%s hari)", "Feels neglected (%s/%s days)")
put(P + "btn.bicara", "Bicara", "Talk")
put(P + "btn.bicara.desc", "Sekali sehari: dengarkan isi hatinya. Kepuasan +4 (Rendah Hati +6).", "Once a day: hear what is on its heart. Contentment +4 (Humble +6).")
put(P + "btn.bicara.sudah", "Sudah bicara hari ini.", "Already talked today.")
put(P + "btn.hadiah", "Hadiah (%s)", "Gift (%s)")
put(P + "btn.hadiah.desc", "Beri barang di tangan: pil, herba roh atau batu roh. +2 sampai +6 menurut mutunya, tiga kali sehari.",
    "Give the item in your hand: a pill, a spirit herb or a spirit stone. +2 to +6 by its worth, three a day.")
put(P + "btn.hadiah.cukup", "Sudah tiga hadiah hari ini.", "Three gifts already today.")
put(P + "btn.maaf", "Minta Maaf", "Apologise")
put(P + "btn.maaf.desc", "Berdirilah bersamanya di depan prasasti Aula dan minta maaf. Kepuasan +10, sekali tiap tiga hari.",
    "Stand with it before the Hall stele and apologise. Contentment +10, once every three days.")
put(P + "btn.maaf.tunggu", "Bisa lagi dalam %s hari.", "Again in %s days.")
put(P + "btn.usir", "Usir", "Expel")
put(P + "btn.usir.yakin", "Yakin? Klik lagi", "Sure? Click again")
put(P + "btn.usir.desc", "Mengusir murid dari sekte (datanya disimpan di daftar Yang Pergi). Semua murid lain kecewa.",
    "Expels the disciple (its record is kept in the list of those who left). Every other disciple is disappointed.")
put(P + "btn.usir.terikat", "Murid terikat Segel Jiwa atau pewaris tidak bisa diusir.", "A soul-sealed disciple or the heir cannot be expelled.")
put(P + "sumber", "Sumber: Han Li (A Record of a Mortal's Journey), Bai Xiaochun (A Will Eternal), Xiao Yan dan Yao Lao (Battle Through the Heavens), Wang Lin (Renegade Immortal).",
    "Source: Han Li (A Record of a Mortal's Journey), Bai Xiaochun (A Will Eternal), Xiao Yan and Yao Lao (Battle Through the Heavens), Wang Lin (Renegade Immortal).")
put(P + "ringkas", " · Puas %s · %s", " · Content %s · %s")

# ---- trait effects
efek = {
    "rajin": ("Meditasi +8% XP, kerja +15%.", "Meditation +8% XP, work +15%."),
    "malas": ("Meditasi -10% XP, kerja -20%, separuh sakit hati bila diabaikan.", "Meditation -10% XP, work -20%, half as hurt by neglect."),
    "sombong": ("Kerusakan +5% dalam duel, dua kali sakit hati bila dipermalukan.", "Damage +5% in duels, twice as hurt when shamed."),
    "rendah_hati": ("Biaya Qi mengajar -10%, Bicara +6.", "Teaching qi cost -10%, Talk +6."),
    "setia": ("Kesetiaan +15, baru bersiap pergi di bawah 10.", "Loyalty +15, prepares to leave only below 10."),
    "licik": ("Bisa membelot ke sekte musuh bila akhirnya pergi.", "May defect to a hostile sect if it ever leaves."),
    "berani": ("Separuh sakit hati bila dikorbankan; memberontak terbuka bila pergi.", "Half as hurt when sacrificed; rebels openly if it leaves."),
    "penakut": ("Mundur dari pertarungan lebih awal (35% nyawa).", "Retreats from fights earlier (35% health)."),
    "ambisius": ("Peluang Fondasi +3%, dua kali sakit hati bila dilangkahi; bisa minta izin mengembara.",
                 "Foundation chance +3%, twice as hurt when passed over; may ask leave to wander."),
    "tenang": ("Perubahan kepuasan harian setengahnya.", "Half the daily change of contentment."),
    "penyayang": ("+1 kepuasan sehari bila punya sahabat; berduka lebih dalam.", "+1 contentment a day with a friend; grieves more deeply."),
    "pendendam": ("Ingatan buruk lebih dalam; bisa lahir dari pengkhianatan sahabat.", "Bad memories cut deeper; may be born of a friend's betrayal."),
}
for sid, (i, e) in efek.items():
    put(P + "efek." + sid, i, e)

# ---- talk lines (state x 3, trait x 2)
talk_state = {
    "setia": [("Hari ini Qi-ku terasa jernih, Guru. Terima kasih sudah membimbingku.", "My qi feels clear today, Master. Thank you for guiding me."),
              ("Kalau sekte ini dalam bahaya, aku akan berdiri paling depan.", "If this sect is ever in danger, I will stand at the front."),
              ("Aku bermimpi kita berdua melihat lautan awan dari puncak tertinggi.", "I dreamt we both watched the sea of clouds from the highest peak.")],
    "gelisah": [("Maaf, Guru… pikiranku sedang ke mana-mana akhir-akhir ini.", "Forgive me, Master… my thoughts wander a lot lately."),
                ("Apakah aku masih berguna bagi sekte ini?", "Am I still of any use to this sect?"),
                ("Aku… hanya lelah. Mungkin besok lebih baik.", "I am… just tired. Perhaps tomorrow will be better.")],
    "membangkang": [("Kenapa selalu aku yang disuruh? Yang lain dibiarkan saja.", "Why is it always me who is ordered about? The others are left alone."),
                    ("Aku tidak mau. Cari saja murid lain.", "I won't. Find another disciple."),
                    ("Guru tidak pernah benar-benar mendengarkan.", "Master never really listens.")],
    "akan_pergi": [("Jalan Dao itu luas, Guru. Mungkin jalanku bukan di sini.", "The Dao is wide, Master. Perhaps my road is not here."),
                   ("…Aku sudah lama memikirkan sesuatu. Nanti saja kubicarakan.", "…I have been thinking about something for a while. Another time."),
                   ("Di luar sana pasti ada yang menghargai bakatku.", "Out there someone surely values my talent.")],
}
for state, lines in talk_state.items():
    for n, (i, e) in enumerate(lines):
        put(P + "bicara." + state + "." + str(n), i, e)
talk_trait = {
    "rajin": [("Aku sudah bangun sebelum fajar untuk berlatih jurus. Boleh kutunjukkan?", "I was up before dawn practising the forms. May I show you?"),
              ("Satu putaran lagi… lalu satu lagi. Begitu caranya mencapai puncak, kan?", "One more cycle… then another. That is how one reaches the peak, isn't it?")],
    "malas": [("Guru, bukankah bermeditasi sambil berbaring juga meditasi?", "Master, isn't meditating lying down still meditation?"),
              ("Hari ini cerah sekali. Sayang kalau dipakai untuk bekerja…", "Such a fine day. A pity to waste it on work…")],
    "sombong": [("Murid-murid sekte lain? Mereka bukan tandinganku.", "Disciples of other sects? They are no match for me."),
                ("Sebentar lagi namaku akan tertulis di Daftar Kuat.", "Soon my name will be on the Strength Ranking.")],
    "rendah_hati": [("Aku masih harus banyak belajar dari Guru dan kakak-kakak seperguruan.", "I still have much to learn from you and my senior brothers and sisters."),
                    ("Jika aku salah, tolong tegur aku, Guru.", "If I am wrong, please correct me, Master.")],
    "setia": [("Ke mana pun Guru pergi, aku ikut.", "Wherever you go, Master, I follow."),
              ("Sekte ini rumahku. Aku tidak punya rumah lain.", "This sect is my home. I have no other.")],
    "licik": [("Aku dengar kabar menarik di kota… tentu saja, gratis untuk Guru.", "I heard interesting news in town… free for you, of course, Master."),
              ("Kadang jalan memutar lebih cepat daripada jalan lurus, bukan?", "Sometimes the winding road is quicker than the straight one, no?")],
    "berani": [("Kirim aku ke garis depan berikutnya, Guru!", "Send me to the next front line, Master!"),
               ("Binatang iblis kemarin? Aku ingin melawannya lagi.", "The demonic beast yesterday? I want to fight it again.")],
    "penakut": [("Guru… apakah malam ini ada serbuan? Aku dengar suara aneh.", "Master… is there a raid tonight? I heard strange sounds."),
                ("Aku lebih suka menjaga ladang obat daripada bertarung…", "I would rather tend the herb fields than fight…")],
    "ambisius": [("Kapan aku boleh mencoba terobosan berikutnya?", "When may I attempt my next breakthrough?"),
                 ("Suatu hari aku ingin berdiri sejajar dengan Guru.", "One day I want to stand level with you, Master.")],
    "tenang": [("Angin di puncak gunung tidak pernah tergesa-gesa. Aku belajar darinya.", "The wind on the peak is never in a hurry. I learn from it."),
               ("Apa pun yang terjadi, hati harus seperti air yang diam.", "Whatever happens, the heart should be like still water.")],
    "penyayang": [("Adik seperguruan kemarin terluka. Sudah kubawakan ramuan untuknya.", "A junior was hurt yesterday. I brought them some medicine."),
                  ("Guru sudah makan? Aku menyisihkan bubur roh untuk Guru.", "Have you eaten, Master? I set aside some spirit porridge for you.")],
    "pendendam": [("Aku tidak akan lupa siapa yang mempermalukanku.", "I will not forget who shamed me."),
                  ("Sepuluh tahun pun bukan waktu yang lama untuk membalas.", "Ten years is not long to repay a grudge.")],
}
for sid, lines in talk_trait.items():
    for n, (i, e) in enumerate(lines):
        put(P + "bicara.sifat." + sid + "." + str(n), i, e)
put(P + "bicara.kata", "%s: «%s»", "%s: «%s»")
put(P + "bicara.sudah", "%s sudah bercerita banyak hari ini. Besok lagi.", "%s has said a lot today. Tomorrow again.")

# ---- gifts, apology
put(P + "hadiah.bukan", "Pegang pil, herba roh atau batu roh di tangan untuk memberi hadiah.", "Hold a pill, a spirit herb or a spirit stone to give a gift.")
put(P + "hadiah.cukup", "%s sudah menerima tiga hadiah hari ini.", "%s already received three gifts today.")
put(P + "hadiah.terima", "%s menerima %s dengan mata berbinar (kepuasan +%s).", "%s accepts %s with shining eyes (contentment +%s).")
put(P + "maaf.tunggu", "Permintaan maaf terlalu sering kehilangan maknanya. Coba lagi dalam %s hari.",
    "An apology too often loses its meaning. Try again in %s days.")
put(P + "maaf.aula", "Minta maaf harus di depan prasasti Aula, bersama murid itu (dalam 12 blok).",
    "An apology must be made before the Hall stele, with the disciple there (within 12 blocks).")
put(P + "maaf.ok", "Kamu membungkuk di depan prasasti leluhur. %s membalas dengan hormat yang dalam.",
    "You bow before the ancestral stele. %s answers with a deep bow.")

# ---- warnings, states
put(P + "peringatan.membangkang", "%s mulai membangkang: menolak tugas dan separuh perintahmu. Bicaralah dengannya.",
    "%s turns defiant: it refuses duties and half your orders. Talk to it.")
put(P + "peringatan.akan_pergi", "%s bersiap meninggalkan sekte! Sekitar %s hari lagi bila tidak ada yang berubah.",
    "%s is preparing to leave the sect! About %s days unless something changes.")
put(P + "pulih", "Hati %s kembali melunak (%s).", "%s's heart softens again (%s).")
put(P + "tolak.tugas", "%s meninggalkan tugasnya dan merajuk dengan lengan bersilang.", "%s leaves its duty and sulks with crossed arms.")
put(P + "tolak.perintah", "%s membuang muka dan tidak mau menuruti perintah \"%s\".", "%s looks away and will not obey the order \"%s\".")
put(P + "tolak.pergi", "%s tidak lagi mendengarkan perintah apa pun.", "%s no longer listens to any order.")
put(P + "ujian_hati", "Ujian Hati di tangga mengubah %s: watak %s menjadi %s.", "The heart trial on the stair changed %s: %s became %s.")

# ---- the rebel's challenge, the wanderer's request
put(P + "tantang.ajak", "%s berdiri tegak di hadapanmu: \"Guru, hari ini aku menantangmu!\" %s %s",
    "%s stands tall before you: \"Master, today I challenge you!\" %s %s")
put(P + "tantang.terima", "[Terima duel]", "[Accept the duel]")
put(P + "tantang.tolak", "[Tolak]", "[Refuse]")
put(P + "tantang.tidak_ada", "Tidak ada murid yang sedang menantangmu.", "No disciple is challenging you.")
put(P + "tantang.gagal", "Duel belum bisa dimulai: %s", "The duel cannot start yet: %s")
put(P + "tantang.mulai", "Duel kehormatan dengan %s dimulai. Kalahkan dia untuk menundukkan hatinya.",
    "The honour duel with %s begins. Defeat it to win back its heart.")
put(P + "tantang.lewat", "%s menunggu jawabanmu, lalu berbalik pergi dengan geram.", "%s waited for your answer, then turned away in anger.")
put(P + "tantang.tunduk", "%s menundukkan kepala: \"Aku kalah. Aku akan tetap di sekte ini.\"", "%s bows its head: \"I lost. I will stay in this sect.\"")
put(P + "pamit.minta", "%s membungkuk: \"Guru, aku ingin mengembara mencari Dao-ku sendiri. Bolehkah?\" %s %s",
    "%s bows: \"Master, I wish to wander and seek my own Dao. May I?\" %s %s")
put(P + "pamit.izinkan", "[Izinkan]", "[Allow]")
put(P + "pamit.tolak", "[Tolak]", "[Refuse]")
put(P + "pamit.tidak_ada", "Tidak ada murid yang meminta izin.", "No disciple is asking leave.")
put(P + "pamit.ditolak", "%s menelan kecewanya dan kembali ke tempatnya.", "%s swallows its disappointment and goes back to its place.")
put(P + "pamit.lewat", "%s tidak mendapat jawaban dan mengurungkan niatnya untuk sementara.", "%s got no answer and drops the idea for now.")

# ---- departures
put(P + "pergi.minggat", "%s minggat dari sekte di tengah malam, membawa buntalan kecil.", "%s slipped out of the sect at night with a small bundle.")
put(P + "pergi.memberontak", "%s memberontak dan meninggalkan sekte!", "%s rebelled and left the sect!")
put(P + "pergi.berkhianat", "%s membelot ke %s!", "%s defected to %s!")
put(P + "pergi.diusir", "%s diusir dari sekte.", "%s was expelled from the sect.")
put(P + "pergi.mengembara", "%s berpamitan dengan hormat dan pergi mengembara. Mungkin suatu hari ia kembali.",
    "%s takes its leave with respect and goes wandering. Perhaps one day it returns.")
put(P + "kembali.ok", "%s kembali ke sekte!", "%s has come back to the sect!")
put(P + "kembali.penuh", "%s ingin kembali, tetapi sekte sudah penuh (%s/%s).", "%s wants to come back, but the sect is full (%s/%s).")
put(P + "kembali.gagal", "%s belum bisa kembali sekarang (tempatnya belum termuat).", "%s cannot come back right now (the place is not loaded).")
put(P + "blok.mati", "Modul Kepribadian dimatikan.", "The personality module is off.")
put(P + "blok.boleh", "Kepergian murid dimatikan di konfigurasi (kepribadian.bolehPergi).", "Disciple departures are off in the config (kepribadian.bolehPergi).")
put(P + "blok.guru", "Gurunya tidak ada di dunia.", "Its master is not in the world.")
put(P + "blok.terikat", "%s terikat Segel Jiwa atau pewaris: ia tidak akan pernah meninggalkan sekte.",
    "%s is bound by the Soul Seal or is the heir: it will never leave the sect.")
put(P + "blok.tubuh", "%s tidak bisa pergi sekarang.", "%s cannot leave right now.")
put(P + "blok.terobosan", "%s sedang menembus ranah.", "%s is breaking through.")
put(P + "blok.duel", "%s sedang berduel.", "%s is in a duel.")

# ---- hall log
put(P + "log.gelisah", "%s tampak gelisah.", "%s looks restless.")
put(P + "log.membangkang", "%s mulai membangkang.", "%s turned defiant.")
put(P + "log.akan_pergi", "%s bersiap meninggalkan sekte.", "%s prepares to leave the sect.")
put(P + "log.tenang", "Hati %s kembali tenang.", "%s's heart is at peace again.")
put(P + "log.tantang", "%s menantang Ketua Sekte berduel.", "%s challenged the Sect Master to a duel.")
put(P + "log.tunduk", "%s kalah duel dan tunduk kembali.", "%s lost the duel and submitted.")
put(P + "log.pergi", "%s meninggalkan sekte (%s).", "%s left the sect (%s).")
put(P + "log.kembali", "%s kembali ke sekte.", "%s came back to the sect.")
put(P + "log.maaf", "Ketua Sekte meminta maaf kepada %s di depan prasasti.", "The Sect Master apologised to %s before the stele.")
put(P + "log.dibujuk", "Utusan %2$s membujuk %1$s untuk membelot.", "%2$s's envoy tried to lure %1$s away.")

# ---- memories (Ingatan texts; one optional argument)
ingat = {
    "dipukul": ("Dipukul oleh Guru", "Struck by the Master"),
    "dihukum": ("Dihukum", "Punished"),
    "diabaikan": ("Merasa diabaikan berhari-hari", "Felt neglected for days"),
    "dipaksa_kerja": ("Dipaksa bekerja tanpa libur", "Worked without a rest day"),
    "dirampas": ("Bagiannya dirampas", "Had its share taken"),
    "dikorbankan": ("Dibiarkan terluka dalam pertarungan", "Left wounded in a fight"),
    "dipermalukan": ("Dipermalukan di depan orang", "Shamed in front of others"),
    "tidak_adil": ("Dilangkahi secara tidak adil", "Unfairly passed over"),
    "bicara": ("Bercakap hangat dengan Guru", "A warm talk with the Master"),
    "hadiah": ("Menerima hadiah: %s", "Received a gift: %s"),
    "maaf": ("Guru meminta maaf di depan prasasti", "The Master apologised before the stele"),
    "tunduk": ("Kalah duel dari Guru dan tunduk", "Lost a duel to the Master and submitted"),
    "ditolak_tantang": ("Tantangannya ditolak", "Its challenge was refused"),
    "kembali": ("Pulang ke sekte", "Came home to the sect"),
    "teman_pergi": ("Sahabatnya %s pergi", "Its friend %s left"),
    "ikut_pergi": ("Pergi bersama saudara angkat %s", "Left with sworn sibling %s"),
    "usir": ("Melihat %s diusir", "Saw %s expelled"),
    "dikhianati": ("Dikhianati sahabat", "Betrayed by a friend"),
    "upacara": ("Ikut upacara leluhur", "Attended an ancestral rite"),
    "dibujuk": ("Dibujuk sekte %s", "Lured by the sect %s"),
    "diangkat": ("Diangkat menjadi tetua", "Appointed an elder"),
    "pewaris": ("Ditetapkan sebagai pewaris", "Named the heir"),
    "berduka": ("Berduka untuk saudara yang gugur", "Mourned a fallen fellow disciple"),
    "ujian_hati": ("Menempa hati di Tangga Seribu", "Forged its heart on the Thousand Steps"),
}
for k, (i, e) in ingat.items():
    put(P + "ingat." + k, i, e)

# ---- commands
put(P + "cmd.judul", "Hati para murid", "The disciples' hearts")
put(P + "cmd.kosong", "Tidak ada murid di dekatmu.", "No disciple near you.")
put(P + "cmd.angka", " · setia %s · puas %s · ikatan %s", " · loyalty %s · content %s · bond %s")
put(P + "cmd.sisa", " · pergi dalam %s hari!", " · leaves in %s days!")
put(P + "cmd.terikat", " · terikat", " · bound")
put(P + "cmd.yangpergi", "Yang pergi: %s", "Those who left: %s")
put(P + "cmd.hari_lalu", " · %s hari lalu", " · %s days ago")
put(P + "uji.tidak_ada", "Tidak ada murid bernama %s di dekatmu.", "No disciple named %s near you.")
put(P + "uji.info", "  K %s · hari rendah %s · ambang %s · kerja ×%s · drift ingatan %s · rawat hari ini %s · tanpa perhatian %s hari",
    "  K %s · low days %s · threshold %s · work ×%s · memory drift %s · care today %s · %s days without care")
put(P + "uji.sifat_salah", "Watak tidak dikenal: %s", "Unknown trait: %s")
put(P + "uji.perlakuan_salah", "Perlakuan tidak dikenal: %s", "Unknown treatment: %s")
put(P + "uji.cara_salah", "Cara pergi tidak dikenal: %s", "Unknown way of leaving: %s")
put(P + "uji.hari", "%s hari pembaruan dijalankan untuk %s murid.", "Ran %s daily updates for %s disciples.")
put(P + "uji.pergi", "Kepergian dimulai: %s.", "Departure started: %s.")
put(P + "uji.bujuk", "%s murid tergoda bujukan.", "%s disciples were tempted.")
put(P + "uji.reset", "Hati %s murid dan daftar Yang Pergi dihapus.", "Cleared the hearts of %s disciples and the list of those who left.")

# ---- hub card and its scroll
put("kultivator.hub.card.sekte_kepribadian_suasana", "Suasana Hati Murid", "Disciples' Moods")
put("kultivator.hub.card.sekte_kepribadian_suasana.tip", "Watak, kesetiaan dan hitung mundur setiap murid; yang pergi dan caranya.",
    "Every disciple's traits, loyalty and countdown; those who left and how.")
put(P + "suasana.judul", "Suasana Hati Murid", "Disciples' Moods")
put(P + "suasana.memuat", "Membaca hati para murid…", "Reading the disciples' hearts…")
put(P + "suasana.kosong", "Tidak ada murid yang termuat di dekatmu.", "No disciple loaded near you.")
put(P + "suasana.sisa", "%s · %s hari", "%s · %s days")
put(P + "suasana.angka", "Kesetiaan %s · Kepuasan %s", "Loyalty %s · Contentment %s")
put(P + "suasana.pending", "Sedang dalam proses pergi!", "In the middle of leaving!")
put(P + "suasana.petunjuk", "Buka gulungannya (tab Hati) untuk bicara, memberi hadiah atau minta maaf.",
    "Open its scroll (Heart tab) to talk, give a gift or apologise.")
put(P + "suasana.yangpergi", "Yang pergi (%s)", "Those who left (%s)")

# ---- scenes
put(P + "adegan.memberontak", "%s Memberontak!", "%s Rebels!")
put(P + "adegan.tunduk", "%s Tunduk", "%s Submits")
put(P + "adegan.pergi", "Seorang Murid Pergi", "A Disciple Leaves")

# ---- advancements
adv = {
    "kepribadian_sifat": ("Kenali Hati Murid", "Lihat watak muridmu di tab Hati atau dengan /kultivasi perguruan murid.",
                          "Know Your Disciples' Hearts", "See your disciples' traits on the Heart tab or with /kultivasi perguruan murid."),
    "kepribadian_puas": ("Murid yang Bahagia", "Buat seorang murid mencapai kepuasan 90.",
                         "A Happy Disciple", "Bring one disciple to contentment 90."),
    "kepribadian_rukun": ("Hati yang Dilunakkan", "Pulihkan murid yang membangkang atau hampir pergi.",
                          "A Heart Softened", "Win back a defiant disciple or one about to leave."),
}
for name, (ti, di, te, de) in adv.items():
    put("advancement.kultivator.sekte." + name + ".title", ti, te)
    put("advancement.kultivator.sekte." + name + ".description", di, de)


def write_lang():
    for lang, idx in (("id_id", 0), ("en_us", 1)):
        out = {k: v[idx] for k, v in texts.items()}
        path = os.path.join(LANG, "sekte_kepribadian." + lang + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
            f.write("\n")
    # No key may exist in another fragment.
    others = {}
    for name in os.listdir(LANG):
        if name.endswith(".id_id.json") and not name.startswith("sekte_kepribadian."):
            with open(os.path.join(LANG, name), encoding="utf-8") as f:
                for k in json.load(f):
                    others[k] = name
    clash = [k for k in texts if k in others]
    if clash:
        raise SystemExit("keys already in other fragments: " + ", ".join(clash[:10]))


# =============================================================================================== pictures

SS = 4  # supersampling per axis


def cover(fn, x, y):
    """Coverage of pixel (x, y) by the shape fn(px, py) -> bool, sampled SS x SS."""
    hit = 0
    for j in range(SS):
        for i in range(SS):
            if fn(x + (i + 0.5) / SS, y + (j + 0.5) / SS):
                hit += 1
    return hit / (SS * SS)


def paint(img, ox, oy, w, h, fn, color, alpha=1.0):
    for y in range(h):
        for x in range(w):
            c = cover(fn, x, y)
            if c > 0:
                col = color(x + 0.5, y + 0.5) if callable(color) else color
                img.blend(ox + x, oy + y, (col[0], col[1], col[2], int(round((col[3] if len(col) > 3 else 255) * c * alpha))))


def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def union(*fns):
    return lambda x, y: any(f(x, y) for f in fns)


def minus(a, b):
    return lambda x, y: a(x, y) and not b(x, y)


def poly(points):
    def inside(x, y):
        n = len(points)
        hit = False
        j = n - 1
        for i in range(n):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
                hit = not hit
            j = i
        return hit
    return inside


def rect(x0, y0, x1, y1):
    return lambda x, y: x0 <= x <= x1 and y0 <= y <= y1


def rounded(x0, y0, x1, y1, r):
    def inside(x, y):
        if not (x0 <= x <= x1 and y0 <= y <= y1):
            return False
        cx = min(max(x, x0 + r), x1 - r)
        cy = min(max(y, y0 + r), y1 - r)
        return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
    return inside


def heart(cx, cy, s):
    # The classic implicit heart (x^2 + y^2 - 1)^3 - x^2 y^3 <= 0, y up.
    def inside(x, y):
        u = (x - cx) / s
        v = -(y - cy) / s
        return (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0
    return inside


def outlined(img, ox, oy, fn, fill, edge, width=1.0, w=32, h=32):
    """Fill with an outline: the shape grown by `width` in the edge colour, then the shape itself."""
    grown = lambda x, y: any(fn(x + dx * width, y + dy * width) for dx, dy in
                             ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (0.7, 0.7), (-0.7, 0.7), (0.7, -0.7), (-0.7, -0.7)))
    paint(img, ox, oy, w, h, grown, edge)
    paint(img, ox, oy, w, h, fn, fill)


def bubble_atlas():
    img = Image(128, 64)
    ink = (46, 54, 50, 235)
    # 0: the thought cloud with two trailing puffs (bottom left), cream white, soft shade at the bottom.
    cloud = union(ellipse(16, 13.5, 12.5, 8.5), circle(9.5, 10.5, 6.2), circle(22.5, 10.5, 6.2), circle(16, 7.0, 6.8),
                  circle(11, 16.5, 5.5), circle(21, 16.5, 5.5))
    puffs = union(circle(8.5, 25.0, 2.6), circle(5.0, 29.0, 1.6))
    shape = union(cloud, puffs)
    outlined(img, 0, 0, shape, lambda x, y: (252, 251, 244, 255) if y < 15 else (234, 236, 228, 255), ink, 1.1)
    paint(img, 0, 0, 32, 32, ellipse(12, 7.5, 4.5, 2.2), (255, 255, 255, 170))

    # 1: heart, rose red with a light top left.
    hx = 32
    outlined(img, hx, 0, heart(16, 15, 11.5), lambda x, y: (232, 66, 96, 255) if x + y < 34 else (200, 38, 72, 255), (90, 14, 34, 240), 1.0)
    paint(img, hx, 0, 32, 32, ellipse(10.5, 10.5, 3.2, 2.2), (255, 200, 210, 200))

    # 2: grey rain cloud and three blue drops.
    cx = 64
    rain = union(ellipse(16, 12, 11, 6.5), circle(10, 10, 5.5), circle(21, 9.5, 6.0), circle(15, 6.5, 5.5))
    outlined(img, cx, 0, rain, lambda x, y: (150, 158, 170, 255) if y < 11 else (118, 126, 140, 255), (52, 58, 70, 240), 1.0)
    for dx, dy in ((9, 22), (16, 25), (23, 22)):
        drop = union(circle(dx, dy + 2.2, 2.0), poly([(dx, dy - 2.6), (dx - 1.9, dy + 1.8), (dx + 1.9, dy + 1.8)]))
        outlined(img, cx, 0, drop, (88, 160, 236, 255), (24, 60, 120, 230), 0.8)

    # 3: dark storm cloud with a yellow bolt.
    sx = 96
    storm = union(ellipse(16, 11.5, 12, 7), circle(9, 9.5, 6.0), circle(22, 8.5, 6.5), circle(15.5, 5.5, 6.0))
    outlined(img, sx, 0, storm, lambda x, y: (84, 82, 98, 255) if y < 10 else (60, 58, 74, 255), (22, 20, 30, 245), 1.0)
    bolt = poly([(17.5, 12), (11, 21.5), (15.5, 21.5), (12.5, 30.5), (21.5, 18.5), (16.8, 18.5), (20.5, 12)])
    outlined(img, sx, 0, bolt, (255, 214, 64, 255), (120, 72, 0, 245), 0.9)

    # 4: a red "!" in a white disc.
    ax, ay = 0, 32
    outlined(img, ax, ay, circle(16, 16, 13), (255, 247, 240, 255), (150, 24, 20, 245), 1.2)
    paint(img, ax, ay, 32, 32, circle(16, 16, 11.3), (226, 52, 40, 255))
    mark = union(rounded(13.6, 6.0, 18.4, 19.0, 2.2), circle(16, 24.0, 2.6))
    paint(img, ax, ay, 32, 32, mark, (255, 250, 245, 255))

    # 5: a tear.
    tx = 32
    tear = union(circle(16, 19.5, 8.0), poly([(16, 3.5), (8.4, 17.0), (23.6, 17.0)]))
    outlined(img, tx, 32, tear, lambda x, y: (120, 190, 250, 255) if x < 16 else (70, 150, 230, 255), (20, 66, 130, 240), 1.0)
    paint(img, tx, 32, 32, 32, ellipse(12.8, 19.5, 2.0, 3.6), (230, 246, 255, 220))

    # 6: a golden star with a sparkle.
    stx = 64
    pts = []
    for k in range(10):
        ang = -math.pi / 2 + k * math.pi / 5
        r = 13.0 if k % 2 == 0 else 5.6
        pts.append((16 + r * math.cos(ang), 16.5 + r * math.sin(ang)))
    outlined(img, stx, 32, poly(pts), lambda x, y: (255, 222, 90, 255) if y < 15 else (242, 178, 40, 255), (130, 82, 0, 240), 1.0)
    paint(img, stx, 32, 32, 32, union(poly([(27, 2), (28, 5.5), (31, 6.5), (28, 7.5), (27, 11), (26, 7.5), (23, 6.5), (26, 5.5)])),
          (255, 255, 230, 240))

    # 7: the cloth bundle on a stick (indigo with a white knot).
    bx = 96
    stick = poly([(3, 27), (5, 29), (23, 11), (21, 9)])
    outlined(img, bx, 32, stick, (150, 104, 60, 255), (70, 44, 20, 240), 0.8)
    sack = union(circle(19.5, 18.5, 8.5), ellipse(19.5, 12.0, 4.5, 3.0))
    outlined(img, bx, 32, sack, lambda x, y: (70, 82, 160, 255) if x + y < 38 else (48, 56, 120, 255), (18, 22, 60, 240), 1.0)
    paint(img, bx, 32, 32, 32, union(circle(19.5, 10.8, 2.4), ellipse(15.5, 9.5, 2.8, 1.4), ellipse(23.5, 9.5, 2.8, 1.4)), (238, 236, 226, 255))
    for px, py in ((16, 18), (22, 21), (18, 23), (23, 16)):
        paint(img, bx, 32, 32, 32, rect(px - 1.0, py - 0.35, px + 1.0, py + 0.35), (210, 214, 240, 200))
    return img


def seal_atlas():
    img = Image(64, 48)
    axis_rgb = [(63, 154, 78), (192, 138, 30), (58, 111, 196), (194, 65, 47), (122, 79, 192), (196, 82, 127)]
    cream = (252, 244, 222, 255)
    glyphs = [
        # rajin: a rising sun over a line (labour from dawn)
        union(minus(circle(8, 9.5, 3.6), rect(0, 9.5, 16, 16)), rect(3, 10, 13, 11),
              rect(7.6, 3.0, 8.4, 4.6), poly([(3.2, 5.6), (4.0, 5.0), (5.0, 6.4), (4.4, 7.0)]), poly([(12.8, 5.6), (12.0, 5.0), (11.0, 6.4), (11.6, 7.0)])),
        # malas: a crescent moon and a small z
        union(minus(circle(7.0, 8.5, 4.4), circle(8.8, 7.4, 3.8)), rect(10, 4, 13, 4.8), poly([(13, 4.8), (10, 8.2), (10.8, 8.2), (13.6, 4.8)]),
              rect(10, 8.2, 13.2, 9.0)),
        # sombong: a crown
        poly([(3, 12), (13, 12), (13.5, 5), (10.5, 8.5), (8, 3.5), (5.5, 8.5), (2.5, 5)]),
        # rendah hati: a bowed figure (head and a bent back)
        union(circle(6.0, 5.5, 1.9), poly([(5, 8), (12, 7.5), (12.5, 9), (7.5, 9.6), (8, 13), (6.4, 13), (5.4, 9.6)])),
        # setia: two linked rings
        union(minus(circle(6.3, 8, 3.6), circle(6.3, 8, 2.3)), minus(circle(9.7, 8, 3.6), circle(9.7, 8, 2.3))),
        # licik: a fox eye with a slit pupil
        minus(union(poly([(2.5, 8), (8, 4.5), (13.5, 8), (8, 11.5)])), ellipse(8, 8, 0.8, 2.6)),
        # berani: a sword
        union(rect(7.3, 2.5, 8.7, 10.5), poly([(7.3, 2.5), (8, 1.5), (8.7, 2.5)]), rect(5, 10.5, 11, 11.5), rect(7.4, 11.5, 8.6, 14)),
        # penakut: rabbit ears and a small head
        union(ellipse(6.2, 5.5, 1.3, 3.6), ellipse(9.8, 5.5, 1.3, 3.6), circle(8, 10.5, 3.0)),
        # ambisius: a peak with a flag
        union(poly([(2, 13), (8, 5), (14, 13)]), rect(7.6, 1.5, 8.4, 5.5), poly([(8.4, 1.5), (12, 2.6), (8.4, 3.8)])),
        # tenang: still water, three calm lines
        union(rect(3, 5, 13, 6), rect(4, 8, 12, 9), rect(5, 11, 11, 12)),
        # penyayang: a heart
        heart(8, 8.6, 4.6),
        # pendendam: a cracked heart
        minus(heart(8, 8.6, 4.6), poly([(8.4, 3.5), (6.8, 7.5), (8.8, 8.6), (7.2, 13.5), (7.8, 13.5), (9.6, 8.4), (7.6, 7.3), (9.2, 3.5)])),
    ]
    for i, glyph in enumerate(glyphs):
        ox = (i % 4) * 16
        oy = (i // 4) * 16
        r, g, b = axis_rgb[i // 2]
        light = (min(255, r + 50), min(255, g + 50), min(255, b + 50), 255)
        dark = (max(0, r - 60), max(0, g - 60), max(0, b - 60), 255)
        paint(img, ox, oy, 16, 16, rounded(0.6, 0.6, 15.4, 15.4, 3.0), dark)
        paint(img, ox, oy, 16, 16, rounded(1.4, 1.4, 14.6, 14.6, 2.4),
              lambda x, y, r=r, g=g, b=b, light=light: light if y < 5 else (r, g, b, 255))
        paint(img, ox, oy, 16, 16, rounded(2.0, 1.8, 14.0, 3.0, 0.6), (255, 255, 255, 60))
        paint(img, ox, oy, 16, 16, lambda x, y, gl=glyph: gl(x, y + 0.6), (0, 0, 0, 70))
        paint(img, ox, oy, 16, 16, glyph, cream)
    return img


def cloth():
    img = Image(16, 16)
    for y in range(16):
        for x in range(16):
            n = ((x * 7 + y * 13) % 5) - 2
            base = (54 + n * 3, 64 + n * 3, 128 + n * 4, 255)
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                base = (204, 210, 236, 255)  # sashiko stitches
            if y in (5, 11):
                base = (base[0] - 18, base[1] - 18, base[2] - 26, 255)  # folds
            img.set(x, y, base)
    return img


# =============================================================================================== models, data

def bundle_model():
    stick = {"from": [7.5, 4, 7.5], "to": [8.5, 24, 8.5], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 8, 8]},
             "faces": {f: {"uv": [7, 0, 8, 16], "texture": "#wood"} for f in ("north", "south", "east", "west")}}
    stick["faces"]["up"] = {"uv": [7, 7, 8, 8], "texture": "#wood"}
    stick["faces"]["down"] = {"uv": [7, 7, 8, 8], "texture": "#wood"}

    def box(a, b, tex="#cloth", uv=(0, 0, 16, 16)):
        return {"from": a, "to": b, "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 8, 8]},
                "faces": {f: {"uv": list(uv), "texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}

    elements = [
        stick,
        box([4.5, 19, 9.5], [11.5, 26, 16.5]),                         # the sack, hanging behind the stick's end
        box([6.5, 25.5, 11.5], [9.5, 27.5, 14.5], uv=(4, 4, 8, 8)),     # the knot
        box([4.0, 26, 12.5], [6.5, 27.2, 13.5], uv=(0, 0, 4, 2)),       # left ear of the knot
        box([9.5, 26, 12.5], [12.0, 27.2, 13.5], uv=(12, 0, 16, 2)),    # right ear
    ]
    model = {"textures": {"wood": "kultivator:item/fx/mat_wood", "cloth": "kultivator:item/fx/sekte_kepribadian_kain",
                          "particle": "kultivator:item/fx/sekte_kepribadian_kain"},
             "elements": elements}
    item = {"model": {"type": "minecraft:model", "model": "kultivator:item/fx/sekte_kepribadian_buntalan"}}
    return item, model


def advancement(name, parent, icon, frame):
    return {
        "parent": parent,
        "criteria": {"done": {"trigger": "minecraft:impossible"}},
        "display": {
            "description": {"translate": "advancement.kultivator.sekte." + name + ".description"},
            "icon": {"id": icon},
            "title": {"translate": "advancement.kultivator.sekte." + name + ".title"},
            "frame": frame,
        },
        "requirements": [["done"]],
    }


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


def main():
    write_lang()
    tex = os.path.join(ASSETS, "textures", "sekte", "kepribadian")
    os.makedirs(tex, exist_ok=True)
    bubble_atlas().save(os.path.join(tex, "gelembung.png"))
    seal_atlas().save(os.path.join(tex, "sifat.png"))
    cloth().save(os.path.join(ASSETS, "textures", "item", "fx", "sekte_kepribadian_kain.png"))
    item, model = bundle_model()
    write_json(os.path.join(ASSETS, "items", "fx", "sekte_kepribadian_buntalan.json"), item)
    write_json(os.path.join(ASSETS, "models", "item", "fx", "sekte_kepribadian_buntalan.json"), model)
    adv = os.path.join(DATA, "advancement", "sekte")
    write_json(os.path.join(adv, "kepribadian_sifat.json"), advancement("kepribadian_sifat", "kultivator:first_disciple", "minecraft:writable_book", "task"))
    write_json(os.path.join(adv, "kepribadian_puas.json"), advancement("kepribadian_puas", "kultivator:sekte/kepribadian_sifat", "minecraft:cake", "goal"))
    write_json(os.path.join(adv, "kepribadian_rukun.json"), advancement("kepribadian_rukun", "kultivator:sekte/kepribadian_sifat", "minecraft:poppy", "goal"))
    print("module P: %d lang keys, textures, bundle piece, 3 advancements" % len(texts))


if __name__ == "__main__":
    main()
