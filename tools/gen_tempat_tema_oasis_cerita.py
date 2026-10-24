"""Kota Oasis story writer (DESIGN 38.T.4, 38.T.15; modules 38TK-O and D-TK2): every dialog, quest, rumour, demand and
schedule file, the loot table and advancements of the oasis town, and the lang fragment
lang-src/tempat_tema_oasis.{id_id,en_us}.json (identical keys). A dialog is written once with its Indonesian and English
text. Refuses lang clashes with other fragments and checks every literal oasis key of the Java code. Run:

    python tools/gen_tempat_tema_oasis_cerita.py            (repo root = the parent of tools/)

Moved into the repo from the 38TK-O scratchpad writer by D-TK2 (same bytes), then extended in place.
"""
import glob
import json
import os
import re
import sys


REPO = None
LANG = {}


def setel(repo):
    global REPO
    REPO = repo


def teks(key, id_text, en_text):
    if key in LANG and LANG[key] != [id_text, en_text]:
        raise SystemExit("lang clash: " + key)
    if id_text.count("%") != en_text.count("%"):
        raise SystemExit("format mismatch: " + key)
    LANG[key] = [id_text, en_text]
    return key


def P(t, ke=None, syarat=None, akibat=None, tampil=False, alasan=None):
    return {"t": t, "ke": ke, "syarat": syarat or [], "akibat": akibat or [], "tampil": tampil, "alasan": alasan}


def N(nid, t, pilihan=None, gestur="", prop="", suara="", akibat=None, isian=None, kabar=False):
    return {"id": nid, "t": t, "pilihan": pilihan or [], "gestur": gestur, "prop": prop, "suara": suara,
            "akibat": akibat or [], "isian": isian or [], "kabar": kabar}


def alasan(nama, id_text, en_text):
    return teks("kultivator.kota_tema.oasis.alasan." + nama, id_text, en_text)


def D(did, pembicara, simpul, prioritas=10, syarat=None, sekali=False):
    out = {"pembicara": pembicara if isinstance(pembicara, list) else [pembicara], "prioritas": prioritas}
    if syarat:
        out["syarat"] = syarat
    if sekali:
        out["sekali"] = True
    out["mulai"] = simpul[0]["id"]
    nodes = []
    for n in simpul:
        o = {"id": n["id"]}
        if n["kabar"]:
            o["teks"] = "kabar"
        else:
            teks("kultivator.cerita.%s.%s" % (did, n["id"]), n["t"][0], n["t"][1])
        if n["isian"]:
            o["isian"] = n["isian"]
        for k in ("gestur", "prop", "suara"):
            if n[k]:
                o[k] = n[k]
        if n["akibat"]:
            o["akibat"] = n["akibat"]
        ch = []
        for i, p in enumerate(n["pilihan"], 1):
            teks("kultivator.cerita.%s.%s.%d" % (did, n["id"], i), p["t"][0], p["t"][1])
            c = {}
            if p["syarat"]:
                c["syarat"] = p["syarat"]
            if p["akibat"]:
                c["akibat"] = p["akibat"]
            if p["ke"]:
                c["ke"] = p["ke"]
            if p["tampil"]:
                c["tampil"] = True
            if p["alasan"]:
                c["alasan"] = p["alasan"]
            ch.append(c)
        if ch:
            o["pilihan"] = ch
        nodes.append(o)
    ids = {n["id"] for n in simpul}
    for n in simpul:
        for p in n["pilihan"]:
            if p["ke"] and p["ke"] not in ids:
                raise SystemExit("%s: node %s -> unknown %s" % (did, n["id"], p["ke"]))
    out["simpul"] = nodes
    path = os.path.join(REPO, "src", "main", "resources", "data", "kultivator", "tempat", "dialog", did + ".json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return did


# ===================================================================================================================== java
def KENANG(butir):
    """Branch-aware talk after a story chain (D-TK2): butir = [(node, syarat, (question id, en), (answer id, en), gesture)] ->
    (choices for the start node, nodes). A choice whose conditions fail stays hidden, so each player only sees the lines of
    the branches they took."""
    kembali, pamit = ("Ada hal lain.", "Something else."), ("Aku pamit.", "I'll take my leave.")
    pil = [P(q, nid, syarat=list(syarat)) for nid, syarat, q, a, g in butir]
    sim = [N(nid, a, [P(kembali, "mulai"), P(pamit)], gestur=g) for nid, syarat, q, a, g in butir]
    return pil, sim


def bagian_java():
    """every key the oasis Java code reads, the 12 murals, the characters, the lore entry and the quest journal texts."""
    K = "kultivator.kota_tema.oasis."


    def t(key, a, b):
        teks(K + key, a, b)


    # ------------------------------------------------------------------------------------------------ sandstorm
    t("badai.pertanda", "Dinding pasir menjulang di cakrawala! Badai pasir tiba dalam semenit; berlindunglah di balik tembok kota.",
      "A wall of sand rises on the horizon! A sandstorm arrives within a minute; take shelter behind the city walls.")
    t("badai.seru", "Badai! Masuk, semuanya masuk!", "Storm! Inside, everyone inside!")
    t("badai.reda", "Badai pasir mereda. Langit gurun kembali biru.", "The sandstorm dies down. The desert sky is blue again.")
    t("badai.tertiup", "Tertiup Pasir: angin gurun memperlambat langkahmu.", "Sand-Blown: the desert wind slows your steps.")
    t("badai.tanpa_kerudung", "Pasir menyayat wajahmu. Pakailah kerudung pasir atau helm!", "The sand cuts your face. Wear a sand veil or a helmet!")
    t("temuan.dapat", "Kau menggali sesuatu dari pasir: %s.", "You dig something out of the sand: %s.")
    t("temuan.sudah", "Kau sudah mengambil temuan ini.", "You have already taken this find.")
    # ------------------------------------------------------------------------------------------------ Jade Gate
    t("surat.dapat", "Surat Jalan Gurun diterima: bebas bea Gerbang Giok selama %s hari.", "Desert Pass received: no Jade Gate toll for %s days.")
    t("surat.selamanya", "Surat Jalan Gurun diterima: bebas bea Gerbang Giok di semua kota oasis, selamanya.",
      "Desert Pass received: no Jade Gate toll in any oasis city, forever.")
    t("bea.bebas", "Surat Jalan Gurun diperiksa. Silakan lewat tanpa bea.", "Desert Pass checked. Pass through, no toll.")
    t("bea.terima", "Bea diterima. Selamat datang di kota oasis.", "Toll received. Welcome to the oasis city.")
    t("bea.tegur", "Hei! Barang dagang wajib membayar bea di Gerbang Giok!", "Hey! Trade goods owe a toll at the Jade Gate!")
    # ------------------------------------------------------------------------------------------------ caravans
    t("kafilah.datang", "Lonceng unta terdengar: Kafilah Agung mendekati %s!", "Camel bells ring out: the Great Caravan approaches %s!")
    t("kafilah.bongkar", "Bongkar muatan! Sutra ke bazar timur, rempah ke bazar barat!", "Unload! Silk to the east bazaar, spices to the west bazaar!")
    t("kafilah.diskon", "Kafilah Agung tiba: barang bawaannya 10%% lebih murah selama dua hari.",
      "The Great Caravan has arrived: its goods are 10%% cheaper for two days.")
    t("kafilah.quest_selamat", "Kafilah tiba dengan selamat. Ajun menemukan Lempeng Peta setengah terkubur di ujung jejak air!",
      "The caravan arrives safely. Ajun found a Map Tablet half buried at the end of the water trail!")
    t("kafilah.kawal_selesai", "Kafilah tiba dengan selamat. Tugas kawalmu selesai.", "The caravan arrives safely. Your escort duty is done.")
    t("kafilah.penumpang_tiba", "Kafilah tiba. Terima kasih telah menumpang; semoga gurun ramah padamu.",
      "The caravan has arrived. Thank you for riding along; may the desert be kind to you.")
    t("kafilah.serbuan", "Binatang gurun (tingkat %s) menyerbu kafilah! Lindungi untanya!", "Desert beasts (tier %s) attack the caravan! Protect the camels!")
    t("kafilah.ajun_hilang", "Seekor unta tumbang dan kafilah tercerai-berai! Ajun hilang di balik badai; carilah dia di antara bukit pasir.",
      "A camel falls and the caravan scatters! Ajun is lost in the storm; search for her among the dunes.")
    t("kafilah.bukan_kota", "Ini hanya bisa dilakukan di dekat Kota Oasis.", "This can only be done near an Oasis City.")
    t("kafilah.sedang_jalan", "Sebuah kafilah sedang dalam perjalanan. Tunggulah sampai ia tiba.", "A caravan is already on the road. Wait until it arrives.")
    t("kafilah.judul", "Kantor Kafilah", "Caravan Office")
    t("kafilah.agung_tiba", "Kafilah Agung tiba", "The Great Caravan arrives")
    t("kafilah.dari_barat", "dari Lautan Pasir Barat", "from the Western Sand Sea")
    t("kafilah.agung_pergi", "Kafilah Agung berangkat", "The Great Caravan departs")
    t("kafilah.ke_timur", "menuju jalan timur", "along the eastern road")
    t("kafilah.sudah_daftar", "Kau sudah terdaftar; kafilah berangkat dalam %s hari.", "You are already signed up; the caravan leaves in %s days.")
    t("kafilah.badai", "Badai pasir: tidak ada kafilah yang berangkat.", "Sandstorm: no caravan leaves.")
    t("kafilah.terima_penumpang", "Naiklah! Untamu yang di tengah, paling dekat dengan air.", "Climb on! Your camel is in the middle, closest to the water.")
    t("kafilah.terdaftar", "Kau terdaftar sebagai penumpang. Kafilah berangkat dalam %s hari dari Lapangan Kafilah.",
      "You are signed up as a passenger. The caravan leaves the Caravan Field in %s days.")
    t("kafilah.perlu_misi", "Pengawal kafilah harus mengambil misi gilda 'Kawal Unta' lebih dulu.",
      "Caravan guards must first take the guild mission 'Camel Escort'.")
    t("kafilah.pengawal_terdaftar", "Kau terdaftar sebagai pengawal. Kafilah berangkat dalam %s hari.",
      "You are signed up as a guard. The caravan leaves in %s days.")
    t("pelana.bukan", "Kau harus menumpang unta dalam kafilah untuk tidur di pelana.", "You must ride a camel in a caravan to sleep in the saddle.")
    t("pelana.bahaya", "Ada bahaya di dekat sini. Tak seorang pun bisa tidur sekarang.", "There is danger nearby. No one can sleep now.")
    t("pelana.judul", "Tidur di Pelana", "Sleeping in the Saddle")
    t("pelana.sub", "Unta berjalan, bintang berputar...", "The camels walk, the stars turn...")
    t("sewa.sudah", "Unta sewaanmu masih ada. Kembalikan dulu sebelum menyewa lagi.", "Your hired camel is still out. Return it before hiring again.")
    t("sewa.dipanggil", "Unta sewaanmu dipanggil kembali ke sini; sewanya masih berlaku.", "Your hired camel is called back here; the hire still runs.")
    t("sewa.ok", "Unta disewa untuk %s hari. Klik untanya untuk menunggang.", "Camel hired for %s days. Click it to ride.")
    t("sewa.kembali", "Unta dikembalikan. Terima kasih!", "Camel returned. Thank you!")
    t("sewa.habis", "Masa sewa untamu habis. Ia berjalan pulang ke kandang.", "Your camel's hire has ended. It walks home to the pen.")
    t("sewa.ditinggal", "Kau meninggalkan untamu terlalu lama. Ia pulang sendiri ke kandang.", "You left your camel too long. It went home to the pen by itself.")
    t("sewa.bukan", "Ini bukan unta sewaanmu. Sewalah unta di kandang atau karavanserai.", "This is not your hired camel. Hire one at the pen or the caravanserai.")
    t("sewa.milik_lain", "Unta ini disewa orang lain.", "Someone else has hired this camel.")
    # ------------------------------------------------------------------------------------------------ Si Belang (UntaLari)
    t("unta.jejak", "Ajun berjongkok membaca pasir: \"Si Belang lari ke arah %1$s, kira-kira %2$s langkah dari sini.\"",
      "Ajun crouches to read the sand: \"Si Belang ran to the %1$s, about %2$s paces from here.\"")
    t("unta.terlihat", "Seekor unta berpunuk belang melenguh di balik bukit pasir.", "A camel with a striped hump groans behind a dune.")
    t("unta.temu", "Si Belang menurunkan kepalanya dan berlutut. Naiklah, lalu bawa ia pulang ke kota.",
      "Si Belang lowers her head and kneels. Climb on, then take her home to the city.")
    t("unta.pulang", "Si Belang mencium bau kandangnya dan berlari kecil ke karavanserai. Temui Bulat Khan!",
      "Si Belang smells her pen and trots off to the caravanserai. Go and see Bulat Khan!")
    ARAH = [("timur", "east"), ("timur laut", "northeast"), ("utara", "north"), ("barat laut", "northwest"), ("barat", "west"),
            ("barat daya", "southwest"), ("selatan", "south"), ("tenggara", "southeast")]
    for i, (a, b) in enumerate(ARAH):
        t("arah.%d" % i, a, b)
    # ------------------------------------------------------------------------------------------------ Water Temple
    t("restu.sudah", "Kau sudah menerima Restu Mata Air hari ini.", "You have already received the Blessing of the Spring today.")
    t("restu.dapat", "Restu Mata Air: Regenerasi, dan angin gurun tak akan meniupmu selama 30 menit dunia.",
      "Blessing of the Spring: Regeneration, and the desert wind won't blow you about for 30 world minutes.")
    t("air_suci.beli", "Kau membeli %1$s botol air suci seharga %2$s.", "You buy %1$s flasks of holy water for %2$s.")
    t("situs.mata_gurun", "Mata Gurun", "Eye of the Desert")
    ANTRE = [("Empat ember sehari... anakku butuh lima.", "Four buckets a day... my child needs five."),
             ("Jangan menyerobot! Aku sudah antre sejak subuh.", "No cutting in! I've been queueing since dawn."),
             ("Kata pendeta, Batu Ukur Air turun lagi.", "The priestess says the Gauge Stone fell again."),
             ("Dulu sumur ini tak pernah ada antrean.", "This well never used to have a queue.")]
    for i, (a, b) in enumerate(ANTRE):
        t("antre.%d" % i, a, b)
    t("paham", "Kisah Xie Duo terangkai utuh: pemahaman +%s.", "Xie Duo's tale comes together: comprehension +%s.")
    # ------------------------------------------------------------------------------------------------ murals
    t("mural.judul", "Tebing Seribu Lukisan", "Cliff of a Thousand Paintings")
    t("mural.paham", "Kisah \"%1$s\": pemahaman +%2$s.", "Tale \"%1$s\": comprehension +%2$s.")
    t("mural.sudah_pesan", "Muralmu sudah dipesan.", "Your mural has already been commissioned.")
    t("mural.prestasi.ranah", "Mencapai ranah %s", "Reached the %s realm")
    t("mural.prestasi.dao", "Memahami Dao %s", "Comprehends the Dao of %s")
    t("mural.prestasi.kalajengking", "Penakluk Raja Kalajengking", "Slayer of the Scorpion King")
    t("mural.prestasi.pembawa_air", "Pembawa Air Kota Oasis", "Water Bearer of the Oasis City")
    t("mural.prestasi.kafilah", "Pengawal kafilah gurun", "Guard of the desert caravans")
    t("mural.prestasi.penari", "Penari Malam Purnama", "Dancer of the Full-Moon Night")
    t("mural.pesan_terima", "Tiga hari! Jangan mengintip sebelum catnya kering!", "Three days! No peeking before the paint is dry!")
    t("mural.dipesan", "Mural dipesan. Dalam %s hari kisahmu muncul di ceruk kedua belas.",
      "Mural commissioned. In %s days your tale appears in the twelfth niche.")
    MURAL = [
        ("Pendirian Kota", "The Founding",
         "Tujuh keluarga kafilah yang kehausan mengikuti seekor burung pasir ke sebuah cekungan. Di dasarnya, air jernih berbentuk bulan sabit "
         "memantulkan langit. Mereka menancapkan tongkat di tepinya dan bersumpah: siapa pun yang haus akan diberi minum.",
         "Seven thirsty caravan families followed a sand bird into a hollow. At its bottom, clear water shaped like a crescent moon mirrored "
         "the sky. They planted their staffs on its shore and swore: whoever is thirsty shall be given water."),
        ("Kafilah Pertama", "The First Caravan",
         "Seratus unta berlutut serentak di tepi danau, seolah memberi hormat. Di atas punggung mereka, sutra dari timur dan kaca dari barat. "
         "Sejak hari itu jalan kafilah berbelok ke oasis, dan kota tumbuh dari tenda menjadi bata lumpur.",
         "A hundred camels knelt at once on the lakeshore, as if bowing. On their backs, silk from the east and glass from the west. From "
         "that day the caravan road turned toward the oasis, and the city grew from tents into mud brick."),
        ("Murid Air", "The Water Disciple",
         "Seorang anak bernama Duo duduk di tepi kolam suci dengan telinga menempel di batu. Para pendeta tertawa, sampai ia menunjuk tempat "
         "menggali sumur baru, dan airnya memancar setinggi menara. Ia diangkat menjadi murid termuda Kuil Air.",
         "A child named Duo sat by the sacred pool with his ear pressed to the stone. The priests laughed, until he pointed to where a new "
         "well should be dug, and its water gushed as high as a tower. He was made the youngest disciple of the Water Temple."),
        ("Penari Bulan Sabit", "The Crescent Dancers",
         "Para penari berputar di bawah purnama, selendang merah mereka seperti nyala yang tak membakar. Angin mengikuti putaran mereka dan "
         "bukit pasir mulai bernyanyi. Malam itu hujan turun di gurun untuk pertama kalinya dalam satu generasi.",
         "The dancers whirl beneath the full moon, their red sashes like flames that do not burn. The wind follows their turning and the "
         "dunes begin to sing. That night rain fell on the desert for the first time in a generation."),
        ("Bintang Penunjuk", "The Guiding Stars",
         "Seorang kepala kafilah tua menunjuk langit malam: Bintang Kendi di utara, Tiga Unta di timur. Di bawahnya, garis-garis emas "
         "menghubungkan oasis demi oasis, peta pertama Jalan Kafilah yang dilukis dari ingatan para penuntun.",
         "An old caravan master points at the night sky: the Jug Star in the north, the Three Camels in the east. Beneath, golden lines join "
         "oasis to oasis, the first map of the Caravan Road, painted from the memory of the camel drivers."),
        ("Tahun Kering", "The Dry Year",
         "Danau surut hingga tinggal genangan. Keluarga-keluarga berjajar di sumur kosong. Di sudut mural, pendeta muda Duo menggambar "
         "lingkaran rune di pasir dan menancapkan empat tiang ke urat air terdalam. Air memancar, dan wajah para pendeta tua pucat ketakutan.",
         "The lake shrank to a puddle. Families stand in line at empty wells. In a corner of the mural, the young priest Duo draws a ring of "
         "runes in the sand and drives four pillars into the deepest water vein. Water bursts out, and the old priests' faces go pale with fear."),
        ("Pendeta yang Diusir", "The Banished Priest",
         "Kota hidup, tetapi para tetua menyebut formasinya 'luka di bumi'. Duo diarak ke Gerbang Giok, jubah birunya dirobek, namanya "
         "dihapus dari dinding kuil. Ia menoleh sekali ke arah danau sebelum melangkah ke gurun. Birunya terasa dingin di ujung jari.",
         "The city lived, but the elders called his formation 'a wound in the earth'. Duo was marched to the Jade Gate, his blue robe torn, his "
         "name scraped from the temple wall. He looked back once toward the lake before he walked into the desert. The blue feels cold under "
         "your fingertips."),
        ("Iblis Pasir Kalah", "The Sand Demon Defeated",
         "Seekor iblis pasir sebesar bukit menelan kafilah demi kafilah. Seorang pendekar berpedang api datang dari timur dan bertarung tiga "
         "hari tiga malam. Pasir di bawah kakinya meleleh menjadi kaca; kaca itulah yang kini dijual di Bazar Timur.",
         "A sand demon as big as a hill swallowed caravan after caravan. A swordsman with a blade of fire came from the east and fought three "
         "days and three nights. The sand beneath his feet melted into glass; that glass is what the East Bazaar sells today."),
        ("Kalajengking di Bawah Pasir", "The Scorpion Beneath the Sand",
         "Di aula-aula Kota Terkubur, seorang lelaki berjubah compang-camping memberi minum seekor kalajengking kecil dari telapak tangannya. "
         "Di panel berikutnya kalajengking itu sebesar unta. Di panel terakhir, sebesar rumah, dan matanya merah seperti bara.",
         "In the halls of the Buried City, a man in a ragged robe gives water to a small scorpion from his palm. In the next panel the scorpion "
         "is as big as a camel. In the last, as big as a house, its eyes red as embers."),
        ("Pedang di Gerbang Giok", "Swords at the Jade Gate",
         "Pengawal gurun dengan helm runcing berbaris di lengkung Gerbang Giok, tombak terhunus menghadap gerombolan perampok. Di atas mereka, "
         "Tuan Benteng pertama mengangkat panji biru-putih. Tidak satu pun perampok melewati gerbang malam itu.",
         "Desert guards in pointed helmets line the arch of the Jade Gate, spears levelled at a horde of raiders. Above them, the first Lord of "
         "the Fort raises the blue and white banner. Not one raider passed the gate that night."),
        ("Air yang Dicuri", "The Stolen Water",
         "Mural paling baru, catnya belum lama kering. Danau bulan sabit menyusut dari tepinya. Di bawah pasir, sebuah bayangan menyedot urat "
         "air melalui empat tiang berukir rune, dan di ujung urat itu duduk sosok berjubah biru yang pudar.",
         "The newest mural, its paint not long dry. The crescent lake shrinks from its shores. Beneath the sand, a shadow sucks the water vein "
         "through four rune-carved pillars, and at the end of the vein sits a figure in a faded blue robe."),
        ("Ceruk Kedua Belas", "The Twelfth Niche",
         "Dinding ceruk ini sengaja dibiarkan polos, hanya diberi bingkai emas. Kata para pelukis, setiap zaman meninggalkan satu kisah yang "
         "pantas dilukis. Ceruk ini menunggu kisah berikutnya.",
         "The wall of this niche is left bare on purpose, with only a golden frame. The painters say every age leaves one tale worth painting. "
         "This niche waits for the next one."),
    ]
    for i, (na, nb, ta, tb) in enumerate(MURAL, 1):
        t("mural.%d.nama" % i, na, nb)
        t("mural.%d.teks" % i, ta, tb)
    # ------------------------------------------------------------------------------------------------ rewards, harvest, orders
    t("hadiah.ekor_angin", "Ekor Angin", "Wind's Tail")
    t("hadiah.ekor_angin.lore", "Kebut pusaka Xie Duo. Bulunya berdesir seperti angin di punggung bukit pasir.",
      "Xie Duo's treasured whisk. Its hairs rustle like wind on the back of a dune.")
    t("hadiah.giok_xie", "Giok Urat Air Xie Duo", "Xie Duo's Water Vein Jade")
    t("hadiah.dapat", "Kau menerima %s.", "You receive %s.")
    t("panen.jauh", "Kau harus berada di Kebun Kurma untuk memanen.", "You must be in the Date Garden to harvest.")
    t("panen.sudah", "Kau sudah memanen hari ini. Kembalilah besok.", "You already harvested today. Come back tomorrow.")
    t("panen.dapat", "Panen kurma! Hari berturut-turut: %1$s/%2$s.", "Date harvest! Days in a row: %1$s/%2$s.")
    t("perintah.sudah", "Kau sudah menerima Perintah Benteng minggu ini.", "You have already received this week's Fort Order.")
    # ------------------------------------------------------------------------------------------------ ruins
    t("pintu.terkunci", "Pintu pasir tak bergeming. Mungkin ada mekanisme di dekatnya.", "The sand door won't budge. Perhaps there is a mechanism nearby.")
    t("pintu.terbuka", "Pintu pasir bergeser: ruang %s terbuka.", "The sand door slides aside: chamber %s is open.")
    t("raja.muncul", "Pasir bergolak... Raja Kalajengking bangkit dari sarangnya!", "The sand churns... the Scorpion King rises from its nest!")
    t("raja.hadiah", "Hadiah dari sarang Raja Kalajengking:", "Spoils from the Scorpion King's nest:")
    t("pilar.sisa", "Tiang formasi tersisa: %s.", "Formation pillars left: %s.")
    t("pilar.tersegel", "Formasi penyedot air tersegel. Urat air kota bernapas lagi.",
      "The water-draining formation is sealed. The city's water veins breathe again.")
    t("pilar.pecah", "Formasi penyedot air hancur berkeping-keping. Urat air kota bebas, tetapi bumi di sini terluka.",
      "The water-draining formation shatters. The city's water veins are free, but the earth here is wounded.")
    # ------------------------------------------------------------------------------------------------ the whirling dance
    t("tari.mulai", "Malam Tari Putar dimulai di panggung tepi danau!", "The Night of the Whirling Dance begins on the lakeside stage!")
    t("tari.bukan_malam", "Tari Putar hanya digelar pada malam purnama.", "The Whirling Dance is only held on full-moon nights.")
    t("tari.sudah", "Kau sudah ikut menari di festival ini.", "You have already danced at this festival.")
    t("tari.gagal", "Tarianmu mendapat skor %s. Butuh 70 untuk Selendang Penari.", "Your dance scored %s. You need 70 for the Dancer's Sash.")
    t("tari.lulus", "Skor %s! Para penari bersorak dan mengalungkan Selendang Penari padamu.",
      "Score %s! The dancers cheer and drape the Dancer's Sash around you.")
    # ------------------------------------------------------------------------------------------------ haggling
    t("tawar.judul", "Tawar-Menawar", "Haggling")
    t("tawar.kira", "Harga pasaran sekitar %s", "Market price about %s")
    t("tawar.panas", "Jeda panas! Kios tutup sampai pukul tiga sore.", "Heat break! The stalls are shut until three in the afternoon.")
    t("tawar.buka_lagi", "Buka lagi pukul 15.00", "Opens again at 15:00")
    t("tawar.sudah", "Kau sudah berbelanja di kios ini hari ini. Kembalilah besok.", "You already bought at this stall today. Come back tomorrow.")
    t("tawar.tolak", "Tidak! Hari ini aku tak sudi menjual apa pun padamu!", "No! Today I won't sell you a thing!")
    t("tawar.pergi", "Datang lagi, saudaraku!", "Come again, my brother!")
    t("tawar.akhir", "Harga terakhir: %s. Ambil atau tinggalkan.", "Final price: %s. Take it or leave it.")
    t("tawar.dibeli", "Kau membeli %1$s × %2$s seharga %3$s.", "You buy %1$s × %2$s for %3$s.")
    BUKA = {
        "rempah": ("Rempah dari seberang lautan, kurma emas dari kebun istana! Untukmu, %s saja.",
                   "Spices from across the sea, golden dates from the palace garden! For you, just %s."),
        "permadani": ("Lihat simpulnya, seribu per jengkal! Permadani ini %s, dan itu harga teman.",
                      "Look at the knots, a thousand per hand's breadth! This carpet is %s, and that's a friend's price."),
        "kaca": ("Kaca dari pasir yang dilelehkan pedang api! Pegang hati-hati. %s.",
                 "Glass from sand melted by a fire sword! Hold it carefully. %s."),
        "giok": ("Giok Barat, dingin di tangan, hangat di hati. Tak kurang dari %s.",
                 "Western jade, cool in the hand, warm in the heart. Not a stone less than %s."),
        "logam": ("Tembaga dan kuningan, ditempa di bawah matahari gurun. %s, timbangannya jujur.",
                  "Copper and brass, hammered under the desert sun. %s, and the scales are honest."),
        "air": ("Air dingin, es dari pegunungan! Di gurun ini, %s itu murah.",
                "Cold water, ice from the mountains! In this desert, %s is cheap."),
        "penenun": ("Benang ini kuwarnai sendiri, Nak. %s untuk tangan yang sabar.",
                    "I dyed this thread myself, child. %s for patient hands."),
        "tamu": ("Dari balik Lautan Pasir Barat, hanya satu ini. %s, malam ini saja.",
                 "From beyond the Western Sand Sea, only this one. %s, tonight only."),
        "kurma": ("Kurma emas dari kebun istana, manis seperti madu. %s sekeranjang.",
                  "Golden dates from the palace garden, sweet as honey. %s a basket."),
        "kandang": ("Pelana, tali dan jerami unta. %s.", "Saddles, ropes and camel straw. %s."),
    }
    for kios, (a, b) in BUKA.items():
        t("tawar.buka." + kios, a, b)
    for i, (a, b) in enumerate([("Kau menghina leluhurku dengan tawaran seperti itu!", "You insult my ancestors with an offer like that!"),
                                ("Hah! Untuk harga itu kau hanya dapat bayangan barangku.", "Hah! For that price you get only the shadow of my goods."),
                                ("Unta pun tertawa mendengar tawaranmu.", "Even a camel would laugh at your offer.")]):
        t("tawar.hina.%d" % i, a, b)
    for i, (a, b) in enumerate([("Kau pandai menawar, saudaraku. Baiklah, %s.", "You haggle well, my brother. Very well, %s."),
                                ("Untuk teman Kota Oasis... %s, tapi jangan bilang siapa-siapa.", "For a friend of the Oasis City... %s, but tell no one."),
                                ("Aduh, anakku akan makan roti kering malam ini. %s.", "Oh, my children will eat dry bread tonight. %s.")]):
        t("tawar.teman.%d" % i, a, b)
    for i, (a, b) in enumerate([("Sepakat! Semoga untamu selalu menemukan air.", "Agreed! May your camel always find water."),
                                ("Jadi! Kau merampokku, tapi dengan sopan.", "Deal! You've robbed me, but politely."),
                                ("Sepakat, sepakat. Bawa pulang dan ceritakan pada temanmu.", "Agreed, agreed. Take it home and tell your friends.")]):
        t("tawar.jadi.%d" % i, a, b)
    t("tamu.papan", "Pedagang Tamu dari Barat", "Guest Trader from the West")
    for i, (a, b) in enumerate([("Barang langka dari balik Lautan Pasir! Satu saja!", "Rare goods from beyond the Sand Sea! Only one!"),
                                ("Giok Barat, peta reruntuhan! Malam ini saja!", "Western jade, ruin maps! Tonight only!"),
                                ("Siapa cepat dia dapat, bulan sabit tak menunggu!", "First come first served, the crescent moon won't wait!"),
                                ("Lihat, lihat! Tak ada di kios lain!", "Look, look! You won't find it at any other stall!")]):
        t("tamu.seru.%d" % i, a, b)
    # ------------------------------------------------------------------------------------------------ service buttons (Jasa tab)
    t("jasa.tawar", "Tawar", "Haggle")
    t("jasa.tawar.catatan", "Menawar harga dagangan kios; pedagang punya suasana hati.", "Haggle over the stall's wares; traders have moods.")
    t("jasa.kafilah", "Kafilah", "Caravan")
    t("jasa.kafilah.catatan", "Jadwal Kafilah Agung, menumpang, mengawal, dan sewa unta.", "Great Caravan schedule, riding along, escorting and camel hire.")
    t("jasa.mural", "Kisah Mural", "Mural Tales")
    t("jasa.mural.catatan", "Membaca dua belas mural tebing dan memesan muralmu sendiri.", "Read the twelve cliff murals and commission your own.")
    # ------------------------------------------------------------------------------------------------ characters, names, commands
    TOKOH = {"sha_wuyan": ("Sha Wuyan", "Sha Wuyan", "Tuan Benteng", "Lord of the Fort"),
             "bulat_khan": ("Bulat Khan", "Bulat Khan", "Kepala Kafilah", "Caravan Master"),
             "shui_lian": ("Shui Lian", "Shui Lian", "Pendeta Air", "Water Priestess"),
             "mo_hua": ("Mo Hua", "Mo Hua", "Pelukis Gua", "Cave Painter"),
             "ajun": ("Si Kecil Ajun", "Little Ajun", "Pembaca Pasir", "Sand Reader"),
             "xie_duo": ("Xie Duo", "Xie Duo", "Pertapa Kalajengking", "Scorpion Hermit"),
             "gulnar": ("Nenek Gulnar", "Grandmother Gulnar", "Penenun Permadani", "Carpet Weaver")}
    for tid, (na, nb, ga, gb) in TOKOH.items():
        t("tokoh." + tid, na, nb)
        t("tokoh.%s.gelar" % tid, ga, gb)
    t("nama_umum", "Kota Oasis", "Oasis City")
    t("uji.tidak_ada", "Tidak ada Kota Oasis di dekat sini.", "There is no Oasis City nearby.")
    t("uji.info", "Kota Oasis %1$s di %2$s (jarak %3$s). Badai: %4$s, kafilah: %5$s, festival: %6$s, danau: %7$s, jangkar: %8$s.",
      "Oasis City %1$s at %2$s (distance %3$s). Storm: %4$s, caravan: %5$s, festival: %6$s, lake: %7$s, anchors: %8$s.")
    t("uji.ok", "Uji berhasil.", "Test succeeded.")
    t("uji.gagal", "Uji gagal.", "Test failed.")
    t("alasan.umum", "Belum bisa.", "Not yet.")
    teks("kultivator.cerita.gelar.pembawa_air", "Pembawa Air", "Water Bearer")
    teks("kultivator.cerita.tempat.kota_tema_oasis", "Kota Oasis", "Oasis City")
    # ------------------------------------------------------------------------------------------------ lore entry (38ST index entry requested)
    L = "kultivator.lore.kota_tema.oasis."
    teks(L + "judul", "Kota Oasis Gurun", "The Desert Oasis City")
    teks(L + "1", "Kota ini berdiri di tepi Danau Bulan Sabit, mata air yang seribu tahun tak pernah tertimbun pasir. Para kafilah menyebutnya "
         "Mata Gurun: pintu terakhir sebelum Lautan Pasir Barat, tempat unta minum untuk terakhir kalinya dan pedagang menimbang nyali.",
         "The city stands on the shore of the Crescent Lake, a spring the sand has not buried in a thousand years. The caravans call it the "
         "Eye of the Desert: the last door before the Western Sand Sea, where camels drink for the last time and merchants weigh their nerve.")
    teks(L + "2", "Di balik bukit pasir barat terkubur sebuah kota yang dulu lebih besar dari kota ini. Dalam satu malam badai menelannya, "
         "dan kata orang, danau mereka telah menyusut lebih dulu. Itulah sebabnya para pendeta Kuil Air menjaga Batu Ukur Air: bila tandanya "
         "turun, kota bersiap haus.",
         "Beyond the western dunes lies buried a city that was once greater than this one. In one night a storm swallowed it, and they say its "
         "lake had shrunk first. That is why the priests of the Water Temple keep the Water Gauge Stone: when its marks fall, the city prepares "
         "for thirst.")
    teks(L + "3", "Sejak kota berdiri, para pelukis gua mengecat kisah kafilah, penari dan pahlawan di Tebing Seribu Lukisan. Dua belas ceruk "
         "menyimpan seribu tahun ingatan, dan ceruk terakhir selalu dibiarkan kosong untuk kisah yang belum terjadi.",
         "Since the city was founded, the cave painters have painted the tales of caravans, dancers and heroes on the Cliff of a Thousand "
         "Paintings. Twelve niches hold a thousand years of memory, and the last niche is always left empty for a tale yet to happen.")
    teks(L + "sumber",
         "Sumber: Dunhuang di Jalur Sutra; Mata Air Bulan Sabit (Yueyaquan), kolam berbentuk bulan sabit di antara bukit pasir Mingsha yang "
         "dikenal 'bernyanyi' saat angin bertiup dan konon tidak pernah tertimbun pasir; Gerbang Giok (Yumenguan) dan Yangguan, pos terakhir "
         "bagi pengembara yang menuju barat. Terinspirasi dari mural gua Mogao dan kafilah unta Jalur Sutra; tata kota, tokoh, harga dan "
         "angkanya rancangan game.",
         "Source: Dunhuang on the Silk Road; the Crescent Moon Spring (Yueyaquan), a crescent-shaped pool among the Mingsha dunes, which are "
         "known to 'sing' when the wind blows and which, it is said, never buried it in sand; the Jade Gate (Yumenguan) and Yangguan, the last "
         "posts for travellers heading west. Inspired by the Mogao cave murals and the camel caravans of the Silk Road; town plan, characters, "
         "prices and numbers are game design.")
    # ------------------------------------------------------------------------------------------------ quest journal
    QK = "kultivator.cerita.quest."


    def quest(qid, nama, ringkas, tahap, hasil):
        teks(QK + qid + ".nama", *nama)
        teks(QK + qid + ".ringkas", *ringkas)
        for sid, (a, b) in tahap.items():
            teks(QK + qid + "." + sid, a, b)
        for sid, (a, b) in hasil.items():
            teks(QK + qid + ".hasil." + sid, a, b)


    quest("tema_oasis_mata_air", ("Mata Air yang Menyusut", "The Shrinking Spring"),
          ("Batu Ukur Air Kota Oasis turun dua tanda dalam sebulan. Sesuatu meminum danau dari bawah pasir.",
           "The Water Gauge Stone of the Oasis City fell two marks in a month. Something drinks the lake from beneath the sand."),
          {"ukur": ("Sampaikan kabar Pendeta Shui Lian kepada Tuan Benteng Sha Wuyan di Istana Pasir: dua tanda, satu bulan.",
                    "Bring Priestess Shui Lian's news to Lord Sha Wuyan at the Sand Palace: two marks, one month."),
           "kafilah": ("Temui Bulat Khan di Karavanserai Agung dan berangkatlah bersama kafilah ke barat. Lindungi untanya.",
                       "Meet Bulat Khan at the Great Caravanserai and ride west with the caravan. Protect the camels."),
           "cari_ajun": ("Kafilah tercerai-berai dalam badai. Temukan Ajun yang tersesat di antara bukit pasir.",
                         "The caravan scattered in the storm. Find Ajun, lost among the dunes."),
           "lempeng": ("Kafilah tiba dengan selamat dan Ajun menemukan Lempeng Peta.", "The caravan arrived safely and Ajun found a Map Tablet."),
           "mural": ("Mo Hua bilang mural ketujuh bergerak. Baca mural 3, 6, 7, 9 dan 11 di Tebing Seribu Lukisan.",
                     "Mo Hua says the seventh mural moves. Read murals 3, 6, 7, 9 and 11 on the Cliff of a Thousand Paintings."),
           "formasi": ("Ikuti Lempeng Peta ke reruntuhan Kota Terkubur. Hancurkan atau segel (dengan air suci) empat tiang formasi di dekat sarang "
                       "Raja Kalajengking.",
                       "Follow the Map Tablet to the ruins of the Buried City. Smash or seal (with holy water) the four formation pillars near the "
                       "Scorpion King's nest."),
           "xie": ("Xie Duo, pendeta yang dulu diusir, muncul dari pasir. Dengarkan tuntutannya.",
                   "Xie Duo, the priest banished long ago, rises from the sand. Hear his demand."),
           "duel": ("Kalahkan Xie Duo dalam duel. Bila ia menghilang, Shui Lian bisa memanggilnya dengan lonceng kuil.",
                    "Defeat Xie Duo in a duel. If he vanishes, Shui Lian can call him with the temple bell."),
           "pertapa": ("Xie Duo menyerah dan akan menjadi pertapa di ceruk kedua belas.", "Xie Duo yields and will become the hermit of the twelfth niche."),
           "pulih": ("Kembali ke Kuil Air. Pendeta Shui Lian menunggumu untuk upacara pemulihan danau.",
                     "Return to the Water Temple. Priestess Shui Lian awaits you for the ceremony that restores the lake.")},
          {"pulih": ("Danau Bulan Sabit naik empat tanda dan Malam Tari Putar digelar untukmu. Kota Oasis menyebutmu Pembawa Air.",
                     "The Crescent Lake rose four marks and the Night of the Whirling Dance was held for you. The Oasis City calls you Water Bearer.")})
    quest("tema_oasis_unta_hilang", ("Si Belang yang Hilang", "Si Belang Is Missing"),
          ("Unta pemimpin Bulat Khan lari saat badai terakhir. Tanpanya, kafilah tak mau berjalan.",
           "Bulat Khan's lead camel bolted in the last storm. Without her, the caravan won't move."),
          {"ajun": ("Minta Ajun membaca jejak Si Belang di pasir.", "Ask Ajun to read Si Belang's tracks in the sand."),
           "cari": ("Cari Si Belang ke arah yang ditunjuk Ajun, naiki dia, dan bawa pulang ke Kota Oasis.",
                    "Search for Si Belang where Ajun pointed, ride her, and bring her home to the Oasis City."),
           "hadiah": ("Si Belang sudah pulang. Temui Bulat Khan di karavanserai.", "Si Belang is home. See Bulat Khan at the caravanserai."),
           "lepas": ("Jejak Si Belang hilang ditiup angin.", "Si Belang's tracks were blown away by the wind.")},
          {"hadiah": ("Bulat Khan memelukmu seperti saudara. Si Belang kembali memimpin kafilah.",
                      "Bulat Khan hugged you like a brother. Si Belang leads the caravan again."),
           "lepas": ("Si Belang tak pernah ditemukan. Kata Bulat Khan, mungkin ia menemukan oasisnya sendiri.",
                     "Si Belang was never found. Bulat Khan says perhaps she found an oasis of her own.")})
    quest("tema_oasis_mural_pesanan", ("Mural di Ceruk Kedua Belas", "A Mural in the Twelfth Niche"),
          ("Mo Hua ingin melukis kisahmu di Tebing Seribu Lukisan.", "Mo Hua wants to paint your tale on the Cliff of a Thousand Paintings."),
          {"pesan": ("Pesan muralmu di layar Kisah Mural (50 NB) dan pilih satu prestasi.",
                     "Commission your mural on the Mural Tales screen (50 NB) and pick one deed."),
           "lukis": ("Mo Hua sedang melukis. Tunggu tiga hari.", "Mo Hua is painting. Wait three days."),
           "lihat": ("Muralmu sudah jadi. Temui Mo Hua di tebing.", "Your mural is finished. See Mo Hua at the cliff.")},
          {"lihat": ("Kisahmu kini menghiasi ceruk kedua belas, terlihat oleh setiap pengembara.",
                     "Your tale now graces the twelfth niche, seen by every traveller.")})
    quest("tema_oasis_permadani_ratu", ("Permadani Ratu", "The Queen's Carpet"),
          ("Nenek Gulnar ingin menenun Permadani Ratu sekali lagi, tetapi bahannya langka.",
           "Grandmother Gulnar wants to weave the Queen's Carpet once more, but its materials are rare."),
          {"bahan": ("Bawakan Nenek Gulnar 1 Ekor Racun Kalajengking dan 6 Bunga Kaktus.",
                     "Bring Grandmother Gulnar 1 Scorpion Venom Tail and 6 Cactus Flowers."),
           "tenun": ("Nenek Gulnar menenun. Tunggu dua hari.", "Grandmother Gulnar is weaving. Wait two days."),
           "ambil": ("Permadani sudah jadi. Temui Nenek Gulnar di Bazar Barat.", "The carpet is finished. See Grandmother Gulnar in the West Bazaar."),
           "antar": ("Permadani dibawa ke istana. Temui Tuan Benteng Sha Wuyan.", "The carpet was taken to the palace. See Lord Sha Wuyan.")},
          {"antar": ("Permadani Ratu kembali menghiasi aula Istana Pasir setelah enam puluh tahun.",
                     "The Queen's Carpet graces the hall of the Sand Palace again after sixty years.")})
    quest("tema_oasis_kurma_emas", ("Kurma Emas", "Golden Dates"),
          ("Kebun Kurma istana kekurangan tangan di musim panen.", "The palace Date Garden is short of hands at harvest."),
          {"panen": ("Bantu memanen di Kebun Kurma lima hari berturut-turut (bicara dengan petani kebun).",
                     "Help with the harvest in the Date Garden five days in a row (talk to the garden's farmers).")},
          {"panen": ("Para petani membayarmu dengan dua belas kurma emas yang memulihkan Qi.",
                     "The farmers paid you with twelve golden dates that restore Qi.")})
    quest("tema_oasis_perintah_reruntuhan", ("Perintah Benteng: Reruntuhan", "Fort Order: The Ruins"),
          ("Tuan Benteng memerintahkan pembersihan reruntuhan gurun dari makhluk buas.", "The Lord orders a desert ruin cleared of beasts."),
          {"bersihkan": ("Datangi sebuah reruntuhan gurun dan kalahkan 8 monster dalam tujuh hari.",
                         "Go to a desert ruin and defeat 8 monsters within seven days.")},
          {"bersihkan": ("Reruntuhan aman untuk sementara. Istana membayar upahmu.", "The ruin is safe for now. The palace paid your wage.")})
    quest("tema_oasis_perintah_kafilah", ("Perintah Benteng: Kafilah", "Fort Order: The Caravan"),
          ("Tuan Benteng meminta sebuah kafilah dikawal sampai tujuan.", "The Lord asks for a caravan to be escorted to its destination."),
          {"kawal": ("Ambil misi gilda 'Kawal Unta' dan kawal sebuah kafilah sampai tiba, dalam tujuh hari.",
                     "Take the guild mission 'Camel Escort' and guard a caravan until it arrives, within seven days.")},
          {"kawal": ("Kafilah tiba dengan selamat. Istana membayar upahmu.", "The caravan arrived safely. The palace paid your wage.")})
    quest("tema_oasis_perintah_buru", ("Perintah Benteng: Perburuan", "Fort Order: The Hunt"),
          ("Mayat hidup gurun mengganggu jalan kafilah.", "Desert husks are harassing the caravan road."),
          {"buru": ("Kalahkan 8 husk gurun dalam tujuh hari.", "Defeat 8 desert husks within seven days.")},
          {"buru": ("Jalan kafilah aman lagi. Istana membayar upahmu.", "The caravan road is safe again. The palace paid your wage.")})


# ===================================================================================================================== a
def bagian_a():
    """dialogs part A: Shui Lian, Sha Wuyan, Bulat Khan."""
    Q = "tema_oasis_mata_air"
    SL = "tokoh:shui_lian"
    SW = "tokoh:sha_wuyan"
    BK = "tokoh:bulat_khan"
    NB50 = alasan("nama_baik_50", "Butuh Nama Baik 50 di kota ini.", "Needs 50 Good Name in this town.")
    PAMIT = ("Aku pamit.", "I'll take my leave.")
    LAIN = ("Ada hal lain.", "Something else.")


    def layanan_kuil():
        return [
            P(("Mohon Restu Mata Air (3 NB).", "Ask for the Blessing of the Spring (3 NB)."), "restu", akibat=["oasis_restu"]),
            P(("Beli sebotol air suci (2 NB).", "Buy a flask of holy water (2 NB)."), "air", akibat=["oasis_air_suci 1"]),
            P(("Beli empat botol air suci (8 NB).", "Buy four flasks of holy water (8 NB)."), "air", akibat=["oasis_air_suci 4"]),
        ]


    def simpul_kuil():
        return [
            N("restu", ("Tundukkan kepalamu. Air yang telah berjalan seribu tahun di bawah pasir kini mengalir di nadimu. Angin gurun "
                        "tak akan merobohkanmu hari ini.",
                        "Bow your head. Water that has walked a thousand years beneath the sand now runs in your veins. The desert "
                        "wind will not throw you down today."), [P(LAIN, "mulai"), P(PAMIT)], gestur="sikap_doa", prop="kendi"),
            N("air", ("Simpan baik-baik. Tabib memakainya untuk pil penyejuk, dan konon formasi jahat pun gentar bila disiram dengannya.",
                      "Keep it well. Healers use it for cooling pills, and they say even a wicked formation flinches when it is "
                      "sprinkled with it."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tuang_teh", prop="kendi"),
        ]


    # ------------------------------------------------------------------------------------------------ Shui Lian
    D("tema_oasis_shui_lian_sapa", SL, [
        N("mulai", ("Selamat datang di Kuil Air, pengembara. Di sini setetes air lebih berharga daripada sekeping perak di kota lain. "
                    "Apa yang kaucari?",
                    "Welcome to the Water Temple, traveller. Here a drop of water is worth more than a silver coin anywhere else. "
                    "What do you seek?"),
          layanan_kuil() + [
              P(("Apa itu Batu Ukur Air?", "What is the Water Gauge Stone?"), "batu"),
              P(("Mengapa orang mengantre di sumur?", "Why are people queueing at the well?"), "antre"),
              P(PAMIT)],
          gestur="hormat", prop="kendi", suara="tema.oasis.water_priestess"),
        *simpul_kuil(),
        N("batu", ("Stela giok di tepi kolam suci itu. Setiap kali danau turun sejengkal, kami mengukir satu tanda. Seribu tahun "
                   "tanda itu hanya naik dan turun mengikuti musim... sampai bulan ini.",
                   "The jade stele at the edge of the sacred pool. Each time the lake drops a hand's breadth we carve one mark. For a "
                   "thousand years the marks only rose and fell with the seasons... until this month."),
          [P(("Apa yang terjadi bulan ini?", "What happened this month?"), "batu2"), P(PAMIT)], gestur="tunjuk"),
        N("batu2", ("Dua tanda dalam satu bulan. Itu tak pernah terjadi, bahkan pada tahun kering yang dilukis di tebing. Sesuatu "
                    "meminum danau kami dari bawah.",
                    "Two marks in one month. That never happened, not even in the dry year painted on the cliff. Something is "
                    "drinking our lake from below."), [P(LAIN, "mulai"), P(PAMIT)], gestur="sikap_lipat_tangan"),
        N("antre", ("Sejak tanda kedua turun, tiap keluarga hanya boleh menimba empat ember sehari. Anak-anak menunggu di bawah "
                    "terik. Aku benci melihatnya, tetapi aku lebih benci melihat sumur kering.",
                    "Since the second mark fell each family may draw only four buckets a day. Children wait under the blazing sun. "
                    "I hate to see it, but I would hate a dry well more."), [P(LAIN, "mulai"), P(PAMIT)], gestur="sikap_tunduk"),
    ])

    SL_PIL, SL_SIM = KENANG([
        ("xie_teh", ["quest " + Q + " selesai", "bendera oasis.xie_bujuk"],
         ("Bagaimana kabar Xie Duo di ceruknya?", "How is Xie Duo in his niche?"),
         ("Setiap senja aku membawakannya teh. Ia pura-pura tidak menunggu, tetapi cangkirnya selalu sudah bersih sebelum aku datang. "
          "Tiga puluh tahun kami menyebutnya pencuri; ternyata ia hanya orang yang terlalu takut melihat kota ini mati.",
          "Every dusk I bring him tea. He pretends not to wait, but his cup is always clean before I arrive. For thirty years we called "
          "him a thief; it turns out he was only a man too afraid to watch this city die."), "tuang_teh"),
        ("xie_duka", ["quest " + Q + " selesai", "!bendera oasis.xie_bujuk"],
         ("Apa kau berduka untuk Xie Duo?", "Do you grieve for Xie Duo?"),
         ("Ia pernah mengajariku menimba tanpa menumpahkan setetes pun. Aku mendoakannya di kolam suci setiap fajar. Kau tidak salah; "
          "danau harus diselamatkan. Tetapi izinkan aku berduka untuk pendeta yang dulu pernah ia jadi.",
          "He once taught me to draw water without spilling a single drop. I pray for him at the sacred pool every dawn. You did no "
          "wrong; the lake had to be saved. But let me grieve for the priest he once was."), "sikap_tunduk"),
        ("urat_retak", ["quest " + Q + " selesai", "bendera oasis.formasi_pecah"],
         ("Urat air yang retak waktu tiang-tiang itu kuhancurkan...", "The water veins that cracked when I smashed the pillars..."),
         ("Tiang-tiang itu pecah dan uratnya ikut retak. Para pendeta menambalnya dengan doa dan tanah liat setiap bulan purnama; butuh "
          "sepuluh tahun sampai lukanya menutup. Jangan menyesal. Segel pun akan meninggalkan bekas.",
          "The pillars broke and the veins cracked with them. The priests mend them with prayer and clay every full moon; it will take "
          "ten years for the wound to close. Do not regret it. A seal would have left a scar too."), "bicara_tangan"),
        ("segel_kilau", ["quest " + Q + " selesai", "bendera oasis.formasi_segel"],
         ("Apa segel air suci itu masih bertahan?", "Is the holy-water seal still holding?"),
         ("Kata para penuntun yang lewat, segelmu masih berkilau di dasar aula ketiga. Air memanggil air: formasi itu kini mengalirkan "
          "kembali apa yang dulu ia curi. Kau mengajari kami bahwa tidak semua hal harus dihancurkan.",
          "The drivers who pass by say your seal still glitters on the floor of the third hall. Water calls to water: the formation now "
          "returns what it once stole. You taught us that not everything has to be destroyed."), "tertawa"),
    ])

    D("tema_oasis_shui_lian_pulih", SL, [
        N("mulai", ("Dengarkah kau? Kolam suci bernyanyi lagi. Gerbang Kuil Air selalu terbuka untukmu, Pembawa Air.",
                    "Can you hear it? The sacred pool sings again. The gate of the Water Temple is always open to you, Water Bearer."),
          layanan_kuil() + [P(("Bagaimana keadaan danau?", "How is the lake?"), "danau")] + SL_PIL + [P(PAMIT)],
          gestur="lambai", prop="kendi", suara="tema.oasis.water_priestess"),
        *simpul_kuil(),
        N("danau", ("Batu Ukur Air naik empat tanda. Antrean di sumur bubar dan anak-anak bermain air lagi. Aku menulis namamu di "
                    "samping tanda tertinggi; para pendeta sesudahku akan membacanya.",
                    "The Gauge Stone rose four marks. The queue at the well is gone and the children splash again. I wrote your name "
                    "beside the highest mark; the priests after me will read it."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tertawa"),
        *SL_SIM,
    ], prioritas=12, syarat=["oasis_air_pulih"])

    D("tema_oasis_shui_lian_mulai", SL, [
        N("mulai", ("Tunggu, pengembara. Matamu tidak seperti mata pedagang yang hanya menghitung laba. Bolehkah kuminta sedikit "
                    "waktumu?",
                    "Wait, traveller. Your eyes are not a merchant's eyes that only count profit. May I ask for a little of your time?"),
          [P(("Katakan, Pendeta.", "Speak, Priestess."), "kisah"), P(("Maaf, aku sedang terburu-buru.", "Sorry, I'm in a hurry."))],
          gestur="sikap_doa", suara="tema.oasis.water_priestess"),
        N("kisah", ("Batu Ukur Air turun dua tanda dalam sebulan. Cerita lama berkata danau Kota Terkubur juga menyusut sebelum badai "
                    "menelannya dalam satu malam. Tuan Benteng Sha Wuyan harus tahu, tetapi beliau tak mau mendengar kabar buruk dari "
                    "seorang pendeta.",
                    "The Gauge Stone fell two marks in a month. The old tale says the lake of the Buried City shrank too, before a storm "
                    "swallowed it in one night. Lord Sha Wuyan must know, but he will not hear bad news from a priestess."),
          [P(("Aku akan menghadap Tuan Benteng untukmu.", "I will go before the Lord for you."), "terima", akibat=["quest_mulai " + Q]),
           P(("Mengapa beliau tak mau mendengar?", "Why won't he listen?"), "sha"),
           P(("Ini bukan urusanku.", "This is none of my business."))], gestur="bicara_tangan"),
        N("sha", ("Ayahnya memerintah pada tahun kering terakhir. Ia melihat ayahnya menjatah air rakyat sementara gudang istana "
                  "penuh. Sejak itu kata 'kering' tak boleh diucapkan di aula audiensi.",
                  "His father ruled in the last dry year. He watched his father ration the people's water while the palace stores "
                  "stayed full. Since then the word 'dry' may not be spoken in the audience hall."),
          [P(("Akan kusampaikan dengan caraku sendiri.", "I'll tell him my own way."), "terima", akibat=["quest_mulai " + Q]),
           P(("Lain kali saja.", "Another time."))], gestur="bisik"),
        N("terima", ("Istana Pasir berdiri di utara danau. Katakan padanya: dua tanda, satu bulan. Beliau akan mengerti.",
                     "The Sand Palace stands north of the lake. Tell him: two marks, one month. He will understand."), gestur="hormat_dalam"),
    ], prioritas=40, syarat=["quest " + Q + " baru", "oasis_di_kota"])

    D("tema_oasis_shui_lian_formasi", SL, [
        N("mulai", ("Lempeng Peta itu menunjuk reruntuhan Kota Terkubur. Bila benar ada formasi yang menyedot urat air kita, jangan "
                    "hancurkan tiangnya dengan amarah. Siram dengan air suci; formasi yang disegel tak meninggalkan luka di bumi.",
                    "That map tablet points to the ruins of the Buried City. If a formation really drinks our water veins, do not "
                    "smash its pillars in anger. Pour holy water on them; a sealed formation leaves no wound in the earth."),
          [P(("Beri aku empat botol air suci (8 NB).", "Give me four flasks of holy water (8 NB)."), "bekal", akibat=["oasis_air_suci 4"]),
           P(("Siapa yang membuat formasi itu?", "Who made that formation?"), "siapa"),
           P(PAMIT)], gestur="sikap_doa", prop="kendi"),
        N("bekal", ("Satu botol untuk tiap tiang. Semoga mata air menuntun langkahmu.", "One flask for each pillar. May the spring guide your steps."),
          [P(PAMIT)], gestur="tuang_teh", prop="kendi"),
        N("siapa", ("(Pendeta itu menatap tebing lukisan lama sekali.) Hanya pendeta air yang tahu cara mendengar urat air. Dan hanya "
                    "satu pendeta yang pernah diusir lewat Gerbang Giok. Aku masih kecil waktu itu. Ia mengajariku menimba tanpa "
                    "menumpahkan setetes pun.",
                    "(The priestess stares at the painted cliff for a long time.) Only a water priest knows how to listen to the water "
                    "veins. And only one priest was ever driven out through the Jade Gate. I was a child then. He taught me to draw "
                    "water without spilling a drop."),
          [P(("Beri aku air suci (8 NB).", "Give me holy water (8 NB)."), "bekal", akibat=["oasis_air_suci 4"]), P(PAMIT)], gestur="sikap_tunduk"),
    ], prioritas=50, syarat=["quest " + Q + " tahap formasi"])

    D("tema_oasis_shui_lian_duel", SL, [
        N("mulai", ("Xie Duo tidak akan pergi sebelum urusannya selesai. Bila ia menghilang di balik bukit pasir, aku bisa "
                    "memanggilnya dengan lonceng kuil. Ia masih mengenal bunyinya.",
                    "Xie Duo will not leave before his business is done. If he vanished behind the dunes, I can call him with the "
                    "temple bell. He still knows its sound."),
          [P(("Bunyikan lonceng itu.", "Ring that bell."), akibat=["oasis_panggil_xie"]), P(("Belum, aku harus bersiap.", "Not yet, I must prepare."))],
          gestur="sikap_doa"),
    ], prioritas=50, syarat=["quest " + Q + " tahap duel"])

    D("tema_oasis_shui_lian_upacara", SL, [
        N("mulai", ("Formasi itu sudah padam. Urat air kembali ke tempatnya. Malam ini kita mengembalikan danau kepada kota, dan kau "
                    "yang harus menuangkan air pertama ke kolam suci.",
                    "The formation has gone dark. The water veins are back where they belong. Tonight we give the lake back to the city, "
                    "and you must pour the first water into the sacred pool."),
          [P(("Aku siap.", "I am ready."), "upacara")], gestur="hormat", prop="kendi", suara="tema.oasis.water_priestess"),
        N("upacara", ("Tuangkan perlahan... Lihat! Batu Ukur Air bergerak! Satu tanda, dua, tiga... empat tanda! Penari, ke panggung! "
                      "Malam Tari Putar ini untuk Pembawa Air!",
                      "Pour slowly... Look! The Gauge Stone moves! One mark, two, three... four marks! Dancers, to the stage! This "
                      "Night of the Whirling Dance is for the Water Bearer!"),
          [P(("(Menuangkan air ke kolam suci.)", "(Pour the water into the sacred pool.)"), "akhir",
             akibat=["oasis_air_pulih", "oasis_surat selamanya", "gelar pembawa_air", "nama_baik 10", "quest_maju " + Q])],
          gestur="sujud_dupa", prop="kendi"),
        N("akhir", ("Tuan Benteng telah menulis Surat Jalan Gurun atas namamu, berlaku selamanya. Gurun ini akan mengingatmu, "
                    "Pembawa Air.",
                    "The Lord has written a Desert Pass in your name, valid forever. This desert will remember you, Water Bearer."),
          gestur="tepuk_tangan"),
    ], prioritas=60, syarat=["quest " + Q + " tahap pulih"])

    # ------------------------------------------------------------------------------------------------ Sha Wuyan
    SURAT = P(("Aku memohon Surat Jalan Gurun.", "I ask for a Desert Pass."), "surat", syarat=["nama_baik 50"], akibat=["oasis_surat"],
              tampil=True, alasan=NB50)
    PERINTAH = P(("Adakah perintah untukku minggu ini?", "Any orders for me this week?"), "perintah", syarat=["nama_baik 50"],
                 akibat=["oasis_perintah"], tampil=True, alasan=NB50)


    def simpul_istana():
        return [
            N("surat", ("Surat ini membawa capku. Penjaga Gerbang Giok di oasis mana pun tak akan memungut bea darimu, dan pengawal "
                        "kafilah akan menahan tombaknya bila terjadi salah paham. Jangan buat aku menyesal.",
                        "This pass carries my seal. The Jade Gate guards of any oasis will take no toll from you, and caravan guards "
                        "will hold their spears in a misunderstanding. Do not make me regret it."), [P(LAIN, "mulai"), P(PAMIT)],
              gestur="angguk_dengar"),
            N("perintah", ("Gurun tak pernah tidur. Laksanakan, dan kembalilah sebelum tujuh hari berlalu.",
                           "The desert never sleeps. Carry it out, and come back before seven days have passed."), [P(PAMIT)],
              gestur="tunjuk"),
        ]


    D("tema_oasis_sha_wuyan_sapa", SW, [
        N("mulai", ("Kau berdiri di aula Istana Pasir. Bicaralah singkat; matahari tidak menunggu siapa pun di gurun.",
                    "You stand in the hall of the Sand Palace. Speak briefly; the sun waits for no one in the desert."),
          [SURAT, PERINTAH, P(("Ceritakan tentang Kota Terkubur.", "Tell me about the Buried City."), "terkubur"),
           P(("Aku pamit, Tuan.", "I take my leave, my lord."))],
          gestur="angguk_dengar", suara="tema.oasis.lord"),
        *simpul_istana(),
        N("terkubur", ("Dahulu lebih besar dari kota ini; menaranya terlihat dari tiga hari perjalanan. Dalam satu malam badai "
                       "menimbunnya. Kakekku berkata, sebelum malam itu danau mereka menyusut, dan tak seorang pun mau mengakuinya.",
                       "Once it was greater than this city; its towers could be seen from three days away. In one night a storm "
                       "buried it. My grandfather said its lake had shrunk before that night, and no one would admit it."),
          [P(("Dan kota ini?", "And this city?"), "takut"), P(LAIN, "mulai")], gestur="sikap_tangan_belakang"),
        N("takut", ("...Aku tidak akan menjadi tuan terakhir kota ini. Hanya itu yang perlu kau tahu.",
                    "...I will not be the last lord of this city. That is all you need to know."), [P(LAIN, "mulai"), P(PAMIT)],
          gestur="sikap_lipat_tangan"),
    ])

    D("tema_oasis_sha_wuyan_ukur", SW, [
        N("mulai", ("Pendeta Shui Lian mengirimmu. Aku bisa membacanya dari caramu memandang danau.",
                    "Priestess Shui Lian sent you. I can read it in the way you look at the lake."),
          [P(("Dua tanda, satu bulan.", "Two marks, one month."), "dua")], gestur="angguk_dengar", suara="tema.oasis.lord"),
        N("dua", ("(Tuan Benteng terdiam lama. Jemarinya mengetuk lengan kursi.) ...Jadi sudah dimulai. Ayahku menunggu terlalu "
                  "lama. Aku tidak akan.",
                  "(The Lord is silent for a long time. His fingers tap the arm of his chair.) ...So it has begun. My father waited "
                  "too long. I will not."), [P(("Apa yang harus dilakukan?", "What must be done?"), "kafilah")], gestur="sikap_tangan_belakang"),
        N("kafilah", ("Bulat Khan di Karavanserai Agung mengenal setiap urat air antara sini dan Lautan Pasir Barat. Kafilahnya akan "
                      "berangkat ke barat; ikutlah. Ajak anak pembaca pasir itu, Ajun. Telinganya lebih tajam daripada kompas mana pun.",
                      "Bulat Khan of the Great Caravanserai knows every water vein between here and the Western Sand Sea. His caravan "
                      "will ride west; go with it. Take the sand-reading child, Ajun. Her ears are sharper than any compass."),
          [P(("Aku berangkat.", "I'm on my way."), akibat=["quest_maju " + Q])], gestur="tunjuk"),
    ], prioritas=50, syarat=["quest " + Q + " tahap ukur"])

    SW_PIL, SW_SIM = KENANG([
        ("xie_tebing", ["bendera oasis.xie_bujuk"],
         ("Xie Duo kini duduk di tebing. Tuan mengizinkannya?", "Xie Duo now sits on the cliff. You allow it, my lord?"),
         ("Ayahku mengusirnya lewat Gerbang Giok karena ia menyelamatkan kota dengan cara terlarang. Aku tidak akan mengulang kesalahan "
          "ayahku. Biar tebing yang menghakiminya; aula ini sudah terlalu lama menghakimi orang yang salah.",
          "My father drove him out through the Jade Gate for saving the city the forbidden way. I will not repeat my father's mistake. "
          "Let the cliff judge him; this hall has judged the wrong people for too long."), "sikap_lipat_tangan"),
        ("xie_ukir", ["!bendera oasis.xie_bujuk"],
         ("Xie Duo sudah tumbang, Tuan.", "Xie Duo has fallen, my lord."),
         ("Kuperintahkan namanya diukir di Batu Ukur Air, di bawah tanda tahun kering. Bagi danau ini ia pencuri; bagi ayahku ia "
          "penyelamat. Keduanya benar. Itulah beratnya menjadi tuan sebuah oasis.",
          "I ordered his name carved on the Water Gauge Stone, beneath the mark of the dry year. To this lake he was a thief; to my "
          "father he was a saviour. Both are true. That is the weight of ruling an oasis."), "sikap_tunduk"),
        ("ajun_dingin", ["bendera oasis.xie_ajun"],
         ("Mengapa Tuan menatapku sedingin itu?", "Why do you look at me so coldly, my lord?"),
         ("Kau menawarkan seorang anak kafilah kepada pertapa kalajengking. Danau terselamatkan dan aku tetap berterima kasih untuk itu. "
          "Tetapi jangan minta aku menyukaimu. Bulat Khan tidak akan melupakannya, begitu pula aku.",
          "You offered a caravan child to a scorpion hermit. The lake was saved and I remain grateful for that. But do not ask me to "
          "like you. Bulat Khan will not forget it, and neither will I."), "marah_tunjuk"),
    ])

    D("tema_oasis_sha_wuyan_selesai", SW, [
        N("mulai", ("Pembawa Air. Aula ini berutang padamu. Duduklah bila lelah; air di cawan itu kini boleh diminum siapa saja.",
                    "Water Bearer. This hall is in your debt. Sit if you are tired; the water in that cup may now be drunk by anyone."),
          [PERINTAH, P(("Bagaimana keadaan kota?", "How is the city?"), "kota")] + SW_PIL
          + [P(("Aku pamit, Tuan.", "I take my leave, my lord."))],
          gestur="hormat", suara="tema.oasis.lord"),
        *simpul_istana(),
        N("kota", ("Kafilah datang lebih sering. Para pedagang bilang mata air kita bernyanyi lagi. Untuk pertama kalinya sejak menjadi "
                   "tuan, aku tidur nyenyak.",
                   "Caravans come more often. The merchants say our spring sings again. For the first time since I became lord, I "
                   "sleep soundly."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tertawa"),
        *SW_SIM,
    ], prioritas=20, syarat=["quest " + Q + " selesai"])

    D("tema_oasis_sha_wuyan_permadani", SW, [
        N("mulai", ("Nenek Gulnar menenun Permadani Ratu lagi? Ibuku duduk di atas permadani itu ketika menerima utusan Kota Terkubur, "
                    "enam puluh tahun lalu. Kukira rahasia pewarnanya sudah ikut terkubur.",
                    "Grandmother Gulnar has woven the Queen's Carpet again? My mother sat on that carpet when she received the envoys of "
                    "the Buried City, sixty years ago. I thought the secret of its dye was buried with them."),
          [P(("Ekor racun kalajengking dan bunga kaktus. Permadaninya sudah tiba di istana.",
              "Scorpion venom tail and cactus flowers. The carpet has arrived at the palace."), "bayar")], gestur="angguk_dengar"),
        N("bayar", ("Merahnya tak akan pudar oleh seratus tahun matahari. Terimalah upahmu, dan sampaikan pada Nenek Gulnar: istana "
                    "akan membayar benangnya seumur hidupnya.",
                    "Its red will not fade in a hundred years of sun. Take your wage, and tell Grandmother Gulnar: the palace will pay "
                    "for her thread for the rest of her life."),
          [P(("Terima kasih, Tuan.", "Thank you, my lord."),
             akibat=["beri_nb_pita 40", "beri kultivator:dagang/permadani_gurun 1", "nama_baik 3", "quest_maju tema_oasis_permadani_ratu"])],
          gestur="hormat"),
    ], prioritas=50, syarat=["quest tema_oasis_permadani_ratu tahap antar"])

    # ------------------------------------------------------------------------------------------------ Bulat Khan
    BK_PIL, BK_SIM = KENANG([
        ("utuh", ["quest " + Q + " selesai", "!bendera oasis.kafilah_hilang"],
         ("Kafilah kita pulang utuh, Bulat Khan.", "Our caravan came home whole, Bulat Khan."),
         ("Hah! Enam unta berangkat, enam unta pulang, ditambah satu lempeng peta! Tiap malam kuceritakan di kedai teh, dan tiap malam "
          "badainya kubuat makin besar. Minggu depan badainya setinggi menara angin. Hahaha!",
          "Hah! Six camels set out, six camels came home, plus one map tablet! Every night I tell it at the teahouse, and every night "
          "the storm gets bigger. Next week it'll be as tall as the wind tower. Hahaha!"), "tertawa"),
        ("hilang", ["quest " + Q + " selesai", "bendera oasis.kafilah_hilang"],
         ("Maaf soal kafilah yang tercerai di badai.", "I'm sorry about the caravan scattered in the storm."),
         ("Badai tidak minta izin, Kawan. Yang penting kau kembali ke dalam badai itu untuk mencari Ajun. Unta bisa dibeli lagi; anak "
          "yang bisa membaca angin, tidak.",
          "Storms don't ask permission, friend. What matters is you went back into that storm for Ajun. Camels can be bought again; a "
          "child who reads the wind cannot."), "tepuk_bahu"),
        ("dingin", ["bendera oasis.xie_ajun"],
         ("Bulat Khan, boleh aku duduk?", "Bulat Khan, may I sit?"),
         ("Jangan di situ. Kursi itu untuk orang yang tidak menukar anak dengan air. ...Ajun bilang sudah memaafkanmu. Aku belum. Beri "
          "aku waktu, dan beri anak itu jarak.",
          "Not there. That seat is for people who don't trade children for water. ...Ajun says she has forgiven you. I haven't. Give "
          "me time, and give the child some distance."), "marah_tunjuk"),
        ("belang", ["bendera oasis.sahabat_kafilah"],
         ("Si Belang kelihatan sehat.", "Si Belang looks healthy."),
         ("Sekarang Si Belang tak mau berjalan kalau bukan kau yang memegang talinya! Unta itu keras kepala seperti nenekku. Kau sahabat "
          "kafilah ini seumur hidup, ingat itu.",
          "Now Si Belang won't walk unless you're the one holding her rope! That camel is as stubborn as my grandmother. You're a friend "
          "of this caravan for life, remember that."), "tertawa"),
    ])

    D("tema_oasis_bulat_khan_sapa", BK, [
        N("mulai", ("Hahaha! Wajah baru di Karavanserai Agung! Duduk, duduk. Unta-untaku bau, tapi mereka tak pernah tersesat. Mau ke "
                    "mana kau?",
                    "Hahaha! A new face in the Great Caravanserai! Sit, sit. My camels stink, but they never get lost. Where are you headed?"),
          [P(("Tunjukkan jadwal kafilah dan sewa unta.", "Show me the caravan schedule and camel hire."), akibat=["buka_layanan tema_kafilah"]),
           P(("Bagaimana kau menemukan jalan di gurun?", "How do you find your way in the desert?"), "bintang"),
           P(("Ada kabar dari jalan?", "Any news from the road?"), "kabar")] + BK_PIL
          + [P(PAMIT)], gestur="tertawa", prop="cambuk_kafilah", suara="tema.oasis.caravan"),
        N("bintang", ("Siang hari pasir berbohong, malam hari bintang jujur. Bintang Kendi di utara, Tiga Unta di timur. Kalau keduanya "
                      "hilang di balik debu, ikuti anak kecil itu, Ajun. Ia membaca angin di punggung bukit pasir seperti aku membaca buku "
                      "utang. Hahaha!",
                      "By day the sand lies, by night the stars tell the truth. The Jug Star in the north, the Three Camels in the east. If "
                      "both vanish behind the dust, follow that little one, Ajun. She reads the wind on the backs of the dunes the way I "
                      "read my ledger of debts. Hahaha!"), [P(LAIN, "mulai"), P(PAMIT)], gestur="tatap_langit"),
        N("kabar", ("", ""), [P(LAIN, "mulai"), P(PAMIT)], gestur="cerita", kabar=True),
        *BK_SIM,
    ])

    D("tema_oasis_bulat_khan_kafilah", BK, [
        N("mulai", ("Jadi Tuan Benteng akhirnya mau mendengar! Hah! Kafilah siap: enam unta, dua pengawal, dan Ajun di unta paling "
                    "depan. Kita berjalan enam ratus langkah ke barat, ke tempat yang kata Ajun anginnya 'berbau air'.",
                    "So the Lord finally listens! Hah! The caravan is ready: six camels, two guards, and Ajun on the lead camel. We ride "
                    "six hundred paces west, to where Ajun says the wind 'smells of water'."),
          [P(("Berangkat sekarang.", "We leave now."), akibat=["oasis_kafilah_quest"]),
           P(("Apa bahayanya?", "What are the dangers?"), "bahaya"),
           P(("Beri aku waktu untuk bersiap.", "Give me time to prepare."))], gestur="pimpin_unta", prop="tali_kekang",
          suara="tema.oasis.caravan"),
        N("bahaya", ("Badai pasir datang tanpa mengetuk pintu, dan binatang gurun suka menggigit kaki unta. Lindungi untaku; kalau satu "
                     "saja jatuh, kafilah berhenti. Dan anak itu... ia suka berlari ke dalam badai mengejar suara. Jaga dia.",
                     "Sandstorms come without knocking, and desert beasts love to bite camels' legs. Protect my camels; if even one falls, "
                     "the caravan stops. And that child... she likes to run into storms chasing sounds. Watch over her."),
          [P(("Berangkat sekarang.", "We leave now."), akibat=["oasis_kafilah_quest"]), P(("Nanti saja.", "Later."))], gestur="sikap_tua"),
    ], prioritas=50, syarat=["quest " + Q + " tahap kafilah"])

    D("tema_oasis_bulat_khan_unta", BK, [
        N("mulai", ("Hah... jangan menatapku begitu. Aku tidak sedih. Hanya saja... Si Belang, unta pemimpinku, lari saat badai terakhir. "
                    "Dua puluh tahun ia berjalan di depan kafilahku. Unta-unta lain tak mau melangkah tanpanya.",
                    "Hah... don't look at me like that. I'm not sad. It's just... Si Belang, my lead camel, bolted in the last storm. Twenty "
                    "years she walked at the head of my caravan. The others won't take a step without her."),
          [P(("Aku akan mencarinya.", "I'll look for her."), "terima", akibat=["quest_mulai tema_oasis_unta_hilang"]),
           P(("Unta bisa dibeli lagi.", "Camels can be bought."), "marah"), P(PAMIT)], gestur="sikap_tua", suara="tema.oasis.caravan"),
        N("terima", ("Ha! Tanyakan pada Ajun. Kalau ada yang bisa membaca jejak Si Belang di pasir yang sudah disapu badai, itu dia.",
                     "Ha! Ask Ajun. If anyone can read Si Belang's tracks in sand the storm has swept, it's her."), gestur="tepuk_bahu"),
        N("marah", ("Unta bisa dibeli, sahabat tidak! Pergilah sebelum kusuruh Si Belang meludahimu... kalau saja ia ada di sini.",
                    "Camels can be bought, friends cannot! Go, before I have Si Belang spit on you... if only she were here."),
          [P(("Maaf. Aku akan mencarinya.", "Sorry. I'll find her."), "terima", akibat=["quest_mulai tema_oasis_unta_hilang"]), P(PAMIT)],
          gestur="marah_tunjuk"),
    ], prioritas=30, syarat=["quest tema_oasis_unta_hilang baru", "quest " + Q + " 2"])

    D("tema_oasis_bulat_khan_unta_pulang", BK, [
        N("mulai", ("SI BELANG! Hahaha! Lihat dia, menyeringai seolah tak pernah berbuat salah! Kau, sahabat, mulai hari ini kau saudara "
                    "kafilahku!",
                    "SI BELANG! Hahaha! Look at her, grinning as if she never did anything wrong! You, friend, from today you are a brother "
                    "of my caravan!"),
          [P(("Ia menungguku di balik bukit pasir.", "She was waiting for me behind a dune."), "sahabat",
             akibat=["beri_nb_pita 25", "beri kultivator:dagang/kurma_emas 6", "nama_baik 3", "bendera oasis.sahabat_kafilah",
                     "kabar tema_oasis_unta_pulang pemain", "quest_maju tema_oasis_unta_hilang"])],
          gestur="tertawa", suara="tema.oasis.caravan"),
        N("sahabat", ("Kurma emas dari kebun istana, simpanlah untuk perjalanan jauh. Dan bila kau ikut kafilahku, untamu selalu yang "
                      "kedua dari depan, tepat di belakang Si Belang!",
                      "Golden dates from the palace garden, keep them for a long journey. And whenever you ride with my caravan, your "
                      "camel walks second from the front, right behind Si Belang!"), gestur="tepuk_bahu"),
    ], prioritas=50, syarat=["quest tema_oasis_unta_hilang tahap hadiah"])

    D("tema_oasis_bulat_khan_pelana", BK, [
        N("mulai", ("Lelah, penumpang? Pelana unta adalah ranjang terbaik di gurun. Pejamkan mata; saat kau bangun, kita sudah jauh di "
                    "depan.",
                    "Tired, passenger? A camel saddle is the best bed in the desert. Close your eyes; when you wake, we'll be far ahead."),
          [P(("Tidur di pelana.", "Sleep in the saddle."), akibat=["oasis_tidur_pelana"]),
           P(("Aku ingin melihat jalan.", "I want to watch the road."))],
          gestur="tertawa"),
    ], prioritas=45, syarat=["oasis_penumpang"])


# ===================================================================================================================== b
def bagian_b():
    """dialogs part B: Ajun, Mo Hua, Xie Duo, Nenek Gulnar."""
    Q = "tema_oasis_mata_air"
    AJ = "tokoh:ajun"
    MH = "tokoh:mo_hua"
    XD = "tokoh:xie_duo"
    GN = "tokoh:gulnar"
    PAMIT = ("Aku pamit.", "I'll take my leave.")
    LAIN = ("Ada hal lain.", "Something else.")

    # ------------------------------------------------------------------------------------------------ Ajun
    AJ_PIL, AJ_SIM = KENANG([
        ("angin_bersih", ["quest " + Q + " selesai", "!bendera oasis.xie_ajun"],
         ("Apa kata angin hari ini, Ajun?", "What does the wind say today, Ajun?"),
         ("Angin dari barat tidak berbau karat lagi. Dulu baunya seperti besi di mulut. Sekarang baunya seperti danau sesudah hujan. "
          "Kata Paman Bulat, artinya kota kita masih panjang umurnya.",
          "The west wind doesn't smell of rust any more. It used to taste like iron in your mouth. Now it smells like the lake after "
          "rain. Uncle Bulat says that means our city will live a long time."), "tunjuk"),
        ("jarak", ["bendera oasis.xie_ajun"],
         ("Ajun, aku...", "Ajun, I..."),
         ("Aku sudah tidak takut lagi. Paman Bulat bilang orang dewasa juga bisa salah membaca angin. Tapi... jangan berdiri terlalu "
          "dekat dulu, ya?",
          "I'm not scared any more. Uncle Bulat says grown-ups can misread the wind too. But... don't stand too close yet, okay?"),
         "sikap_tunduk"),
        ("kunjung_xie", ["quest " + Q + " selesai", "bendera oasis.xie_bujuk"],
         ("Kau pernah mengunjungi Xie Duo?", "Have you ever visited Xie Duo?"),
         ("Pernah, sekali. Ia mengajariku mendengar keheningan di antara dua tetes air. Aku tidak bisa. Tapi ia memberiku kurma, jadi "
          "aku akan datang lagi!",
          "Once. He taught me to hear the silence between two drops of water. I couldn't. But he gave me dates, so I'll go again!"),
         "tertawa"),
    ])

    D("tema_oasis_ajun_sapa", AJ, [
        N("mulai", ("Ssst! Dengar... angin sedang bicara di punggung bukit pasir. Hari ini ia bilang ada yang datang dari jauh. Itu "
                    "kau, ya?",
                    "Shh! Listen... the wind is talking on the backs of the dunes. Today it says someone came from far away. That's you, "
                    "isn't it?"),
          [P(("Apa lagi kata angin?", "What else does the wind say?"), "angin"),
           P(("Bagaimana kau membaca pasir?", "How do you read the sand?"), "baca"),
           P(("Ini kurma emas untukmu.", "Here's a golden date for you."), "kurma", syarat=["punya kultivator:dagang/kurma_emas"],
             akibat=["ambil kultivator:dagang/kurma_emas 1", "klip tertawa"])] + AJ_PIL
          + [P(PAMIT)], gestur="sikap_anak", suara="tempat.child_play"),
        N("angin", ("", ""), [P(LAIN, "mulai"), P(PAMIT)], gestur="tatap_langit", kabar=True),
        N("baca", ("Pasir itu seperti kulit unta. Kalau angin lewat, ia meninggalkan keriput. Keriput panjang artinya angin jauh, "
                   "keriput pendek artinya angin bohong. Ayahku yang mengajariku... sebelum kafilahnya tidak pulang.",
                   "Sand is like camel skin. When the wind passes it leaves wrinkles. Long wrinkles mean a far wind, short wrinkles "
                   "mean a lying wind. My father taught me... before his caravan never came home."), [P(LAIN, "mulai"), P(PAMIT)],
          gestur="berlutut"),
        *AJ_SIM,
        N("kurma", ("Kurma emas! Untukku? Aku akan menyimpan bijinya. Kata Nenek Gulnar, kalau ditanam di tepi danau, seratus tahun lagi "
                    "jadi pohon setinggi menara angin.",
                    "A golden date! For me? I'll keep the seed. Grandmother Gulnar says if you plant it by the lake, in a hundred years "
                    "it grows as tall as a wind tower."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tertawa"),
    ])

    D("tema_oasis_ajun_kafilah", AJ, [
        N("mulai", ("Paman Bulat bilang aku boleh duduk di unta paling depan! Dengar, angin dari barat berbau seperti batu basah. Itu bau "
                    "air yang bersembunyi di bawah pasir. Banyak sekali air... tapi ada yang meminumnya.",
                    "Uncle Bulat says I can sit on the lead camel! Listen, the west wind smells like wet stone. That's the smell of water "
                    "hiding under the sand. So much water... but something is drinking it."),
          [P(("Siapa yang meminumnya?", "Who is drinking it?"), "siapa"), P(("Kita akan menemukannya.", "We'll find it."))],
          gestur="tunjuk", suara="tempat.child_play"),
        N("siapa", ("Aku tidak tahu. Tapi anginnya gemetar di sana, seperti unta yang mencium bau kalajengking.",
                    "I don't know. But the wind trembles there, like a camel smelling a scorpion."),
          [P(("Ayo berangkat bersama Paman Bulat.", "Let's ride with Uncle Bulat."))], gestur="takut_sembunyi"),
    ], prioritas=50, syarat=["quest " + Q + " tahap kafilah"])

    D("tema_oasis_ajun_temu", AJ, [
        N("mulai", ("Kau menemukanku... Aku mengejar suara di dalam badai. Suaranya seperti orang menangis di bawah pasir. Aku takut "
                    "sekali.",
                    "You found me... I chased a sound into the storm. It sounded like someone crying under the sand. I was so scared."),
          [P(("Kau aman sekarang.", "You're safe now."), "peta")], gestur="menangis"),
        N("peta", ("Waktu badai reda aku melihat lempeng ini setengah terkubur. Ada gambar danau dan garis ke reruntuhan. Simpanlah, kau "
                   "lebih berani dariku.",
                   "When the storm died down I saw this tablet half buried. There's a lake drawn on it and a line to the ruins. Keep it, "
                   "you're braver than me."), gestur="tunjuk"),
    ], prioritas=60, syarat=["quest " + Q + " tahap mural", "bendera oasis.kafilah_hilang"], sekali=True)

    D("tema_oasis_ajun_unta", AJ, [
        N("mulai", ("Si Belang? Aku kenal jejaknya! Kaki kiri belakangnya sedikit menyeret, jadi pasirnya selalu miring. Tunggu, biar "
                    "kubaca...",
                    "Si Belang? I know her tracks! Her left hind foot drags a little, so the sand always leans. Wait, let me read it..."),
          [P(("Bacalah, Ajun.", "Read it, Ajun."), "baca", akibat=["oasis_unta_kabur", "quest_maju tema_oasis_unta_hilang"])],
          gestur="berlutut", suara="tempat.child_play"),
        N("baca", ("Di sana! Jejaknya belum hilang. Si Belang takut badai, jadi ia lari melawan angin. Bawa dia pulang, ya. Paman Bulat "
                   "pura-pura tidak sedih, padahal semalaman ia tidak tidur.",
                   "There! Her tracks aren't gone yet. Si Belang is afraid of storms, so she ran against the wind. Bring her home, okay? "
                   "Uncle Bulat pretends he isn't sad, but he didn't sleep all night."), gestur="tunjuk"),
    ], prioritas=50, syarat=["quest tema_oasis_unta_hilang tahap ajun"])

    D("tema_oasis_ajun_unta_cari", AJ, [
        N("mulai", ("Belum ketemu? Jangan khawatir, Si Belang tidak akan pergi jauh dari bau air.",
                    "Not found her yet? Don't worry, Si Belang never strays far from the smell of water."),
          [P(("Bacakan jejaknya sekali lagi.", "Read her tracks once more."), akibat=["oasis_unta_kabur"]), P(PAMIT)],
          gestur="sikap_anak"),
    ], prioritas=45, syarat=["quest tema_oasis_unta_hilang tahap cari"])

    D("tema_oasis_ajun_maaf", AJ, [
        N("mulai", ("(Ajun mundur selangkah.) Kau... kau mau menyerahkanku pada pertapa kalajengking itu. Aku mendengar semuanya.",
                    "(Ajun steps back.) You... you wanted to hand me over to that scorpion hermit. I heard everything."),
          [P(("Itu salah. Maafkan aku.", "That was wrong. Forgive me."), "maaf", akibat=["nama_baik 3"]), P(("(Diam.)", "(Say nothing.)"))],
          gestur="takut_sembunyi"),
        N("maaf", ("...Paman Bulat bilang orang dewasa juga boleh salah, asal mau minta maaf. Tapi jangan lakukan lagi.",
                   "...Uncle Bulat says grown-ups are allowed to be wrong too, as long as they say sorry. But don't do it again."),
          gestur="sikap_anak"),
    ], prioritas=70, syarat=["bendera oasis.xie_ajun"], sekali=True)

    # ------------------------------------------------------------------------------------------------ Mo Hua
    MH_PIL, MH_SIM = KENANG([
        ("mural_tiga_belas", ["quest " + Q + " selesai", "bendera oasis.xie_bujuk"],
         ("Apa mural ketujuh masih bergerak?", "Does the seventh mural still move?"),
         ("Diam sejak Xie Duo duduk di ceruk kedua belas. Kini aku melukis mural ketiga belas: seorang pertapa menuang air untuk "
          "pendeta muda. Aku belum tahu siapa yang menuang untuk siapa. Mungkin keduanya.",
          "Still since Xie Duo sat down in the twelfth niche. Now I'm painting a thirteenth mural: a hermit pouring water for a young "
          "priestess. I don't know yet who pours for whom. Maybe both."), "lukis_dinding"),
        ("mural_senja", ["quest " + Q + " selesai", "!bendera oasis.xie_bujuk"],
         ("Apa mural ketujuh masih bergerak?", "Does the seventh mural still move?"),
         ("Diam, sejak pertapa itu tumbang. Kadang kurasa matanya menatap ke arah gerbang. Kucat ulang langitnya dengan warna senja "
          "supaya ia tidak kesepian.",
          "Still, since the hermit fell. Sometimes I think his eyes look toward the gate. I repainted his sky in dusk colours so he "
          "wouldn't be lonely."), "sikap_tunduk"),
        ("kisah_kita", ["quest " + Q + " selesai"],
         ("Akankah kisah danau ini dilukis?", "Will the tale of the lake be painted?"),
         ("Sudah kumulai! Tunggu, di mana kuasku... Pokoknya kau ada di sana, berdiri di tepi kolam dengan sebotol air. Atau pedang. "
          "Belum kuputuskan; tergantung cerita mana yang lebih laku di kedai teh.",
          "I've already started! Wait, where's my brush... Anyway, you're in it, standing by the pool with a flask of water. Or a sword. "
          "I haven't decided; it depends on which tale sells better at the teahouse."), "tertawa"),
    ])

    D("tema_oasis_mo_hua_sapa", MH, [
        N("mulai", ("Oh! Kau bukan... bukan, kau bukan pembeli cat. Maaf, aku sering lupa wajah orang, tapi tak pernah lupa warna. Mau "
                    "melihat kisah-kisah di tebing?",
                    "Oh! You're not... no, you're not the paint buyer. Sorry, I often forget faces, but never colours. Would you like to "
                    "see the tales on the cliff?"),
          [P(("Tunjukkan Kisah Mural.", "Show me the Mural Tales."), akibat=["oasis_mural_buka"]),
           P(("Benarkah mural-mural ini bicara?", "Do the murals really speak?"), "suara"),
           P(("Siapa yang melukis semua ini?", "Who painted all of this?"), "pelukis")] + MH_PIL
          + [P(PAMIT)], gestur="lukis_dinding", prop="kuas_besar"),
        N("suara", ("Tidak dengan mulut! Mereka berbisik lewat warna. Biru tua di gua ketujuh, misalnya, selalu terasa dingin, seperti "
                    "air yang menangis. Para pelukis tua bilang aku terlalu banyak menghirup bau cat. Mungkin mereka benar.",
                    "Not with mouths! They whisper through colour. The deep blue in the seventh cave, for instance, always feels cold, "
                    "like weeping water. The old painters say I breathe too much paint. Maybe they're right."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="bicara_tangan", prop="kuas"),
        N("pelukis", ("Seribu tahun pelukis gua. Setiap kafilah besar, setiap badai, setiap pahlawan punya ceruknya sendiri. Ceruk kedua "
                      "belas masih kosong... katanya untuk kisah berikutnya.",
                      "A thousand years of cave painters. Every great caravan, every storm, every hero has a niche of its own. The twelfth "
                      "niche is still empty... they say it's for the next tale."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tunjuk"),
        *MH_SIM,
    ])

    D("tema_oasis_mo_hua_mural", MH, [
        N("mulai", ("Kau! Kau juga melihatnya, kan? Pendeta di mural ketujuh menoleh padaku semalam! Ia bergerak setiap kali kau lewat. "
                    "Aku yakin ia ingin kisahnya dibaca sampai utuh.",
                    "You! You saw it too, didn't you? The priest in the seventh mural turned his head at me last night! He moves every "
                    "time you pass. I'm sure he wants his tale read to the end."),
          [P(("Mural mana yang harus kubaca?", "Which murals must I read?"), "urutan"),
           P(("Buka Kisah Mural.", "Open the Mural Tales."), akibat=["oasis_mural_buka"])], gestur="kaget", prop="kuas"),
        N("urutan", ("Ketiga, Murid Air. Keenam, Tahun Kering. Ketujuh, Pendeta yang Diusir. Kesembilan, Kalajengking di Bawah Pasir. "
                     "Kesebelas, Air yang Dicuri. Bacalah kelimanya di dinding tebing; sentuh catnya, jangan takut.",
                     "Third, The Water Disciple. Sixth, The Dry Year. Seventh, The Banished Priest. Ninth, The Scorpion Beneath the Sand. "
                     "Eleventh, The Stolen Water. Read all five on the cliff wall; touch the paint, don't be afraid."),
          [P(("Buka Kisah Mural.", "Open the Mural Tales."), akibat=["oasis_mural_buka"]), P(("Aku ke tebing sekarang.", "I'm off to the cliff."))],
          gestur="bicara_tangan"),
    ], prioritas=50, syarat=["quest " + Q + " tahap mural"])

    D("tema_oasis_mo_hua_pesanan", MH, [
        N("mulai", ("Hei... wajahmu pantas berada di tebing. Bayar lima puluh Nilai Batu, ceritakan satu prestasimu, dan dalam tiga hari "
                    "ceruk kedua belas akan menampilkan kisahmu. Setiap pengembara di dunia ini bisa melihatnya!",
                    "Hey... your face belongs on the cliff. Pay fifty Spirit Stones, tell me one of your deeds, and in three days the "
                    "twelfth niche will show your tale. Every traveller in this world will be able to see it!"),
          [P(("Lukislah aku.", "Paint me."), akibat=["quest_mulai tema_oasis_mural_pesanan", "oasis_mural_buka"]),
           P(("Nanti saja.", "Maybe later."))], gestur="lukis_dinding", prop="palet"),
    ], prioritas=30, syarat=["quest tema_oasis_mural_pesanan baru"])

    D("tema_oasis_mo_hua_pesanan_jadi", MH, [
        N("mulai", ("Sudah melihat ceruk kedua belas? Aku menambahkan sedikit emas di matamu. Jangan tanya kenapa, tanganku yang "
                    "memutuskan. Ini sketsa pertamanya, untukmu.",
                    "Have you seen the twelfth niche? I added a little gold to your eyes. Don't ask why, my hand decided. Here is the "
                    "first sketch, for you."),
          [P(("Terima kasih, Mo Hua.", "Thank you, Mo Hua."))], gestur="tertawa", prop="kuas"),
    ], prioritas=50, syarat=["quest tema_oasis_mural_pesanan selesai"], sekali=True)

    # ------------------------------------------------------------------------------------------------ Xie Duo
    ALASAN_TXT = ("Mencuri? Tiga puluh tahun lalu aku menyelamatkan kota itu! Tahun kering, sumur-sumur kosong, dan aku menarik air dari "
                  "urat terdalam dengan formasi terlarang. Kota hidup. Lalu mereka mengusirku lewat Gerbang Giok karena 'menyentuh yang "
                  "terlarang'.",
                  "Stealing? Thirty years ago I saved that city! A dry year, empty wells, and I drew water from the deepest vein with a "
                  "forbidden formation. The city lived. Then they drove me out through the Jade Gate for 'touching the forbidden'.")
    TUNTUT_TXT = ("Aku hanya meminta yang adil. Biarkan Kuil Air menerimaku kembali, atau danau itu kuminum sampai tetes terakhir. Pilih, "
                  "pembawa pesan.",
                  "I only ask for what is fair. Let the Water Temple take me back, or I drink that lake to its last drop. Choose, "
                  "messenger.")
    TERIMA_TXT = ("(Xie Duo memandangi botol air suci itu lama sekali. Tangannya gemetar.) ...Air dari kolam suci. Rasanya masih sama. "
                  "Baiklah. Aku akan duduk di ceruk kedua belas, dan biar tebing itu yang menghakimiku.",
                  "(Xie Duo stares at the flask of holy water for a long time. His hands tremble.) ...Water from the sacred pool. It still "
                  "tastes the same. Very well. I will sit in the twelfth niche, and let the cliff judge me.")
    AJUN_TXT = ("Hah! Kau menawarkan seorang anak? Bahkan aku, yang diusir dan dikutuk, tidak sejatuh itu. Lihat, anak itu lari. Sekarang "
                "rasakan sengat kalajengking tua ini!",
                "Hah! You offer a child? Even I, banished and cursed, never sank so low. Look, the child runs. Now taste the sting of this "
                "old scorpion!")


    def xie(did, buka, bujuk_syarat, bujuk_ambil, alasan_key, flag):
        D(did, XD, [
            N("mulai", buka[0], [P(("Mengapa kau mencuri air kota?", "Why do you steal the city's water?"), "alasan")],
              gestur=buka[1], suara="tema.oasis.scorpion"),
            N("alasan", ALASAN_TXT, [P(("Lalu kini kau membalas dendam?", "And now you take revenge?"), "tuntut")], gestur="marah_tunjuk"),
            N("tuntut", TUNTUT_TXT, [
                P(("Kita selesaikan dengan pedang.", "We settle this with blades."), akibat=["quest_maju " + Q, "mulai_duel"]),
                P(("Danau itu milik semua orang, juga milikmu. Pulanglah sebagai pertapa.",
                   "The lake belongs to everyone, to you too. Come home as a hermit."), "terima", syarat=bujuk_syarat, tampil=True,
                  alasan=alasan_key, akibat=[bujuk_ambil, "bendera oasis.xie_bujuk", "oasis_xie_pertapa", "quest_maju " + Q]),
                P(("Ambil saja anak pembaca pasir itu sebagai pembaca airmu.", "Take the sand-reading child as your water reader."), "ajun",
                  akibat=["nama_baik -10", "oasis_ajun_lari", "bendera oasis.xie_ajun"]),
            ], gestur="sikap_angkuh"),
            N("terima", TERIMA_TXT, gestur="hormat_dalam"),
            N("ajun", AJUN_TXT, [P(("Hadapi aku.", "Face me."), akibat=["quest_maju " + Q, "mulai_duel"])], gestur="ancam"),
        ], prioritas=60, syarat=["quest " + Q + " tahap xie", "bendera " + flag])


    xie("tema_oasis_xie_segel",
        (("Air suci... Kau menyegel formasiku dengan air suci. Shui Lian kecil yang mengajarimu, bukan? Gadis itu dulu menimba di "
          "sampingku.",
          "Holy water... You sealed my formation with holy water. Little Shui Lian taught you, didn't she? That girl used to draw water "
          "beside me."), "sikap_angkuh"),
        ["nama_baik 40", "punya kultivator:tema/air_suci"], "ambil kultivator:tema/air_suci 1",
        alasan("xie_bujuk_40", "Butuh Nama Baik 40 dan sebotol air suci.", "Needs 40 Good Name and a flask of holy water."),
        "oasis.formasi_segel")
    xie("tema_oasis_xie_pecah",
        (("Kau menghancurkan tiang-tiangku! Tiga puluh tahun kurajut formasi itu, dan kau merobohkannya seperti anak kecil menendang "
          "istana pasir.",
          "You smashed my pillars! Thirty years I wove that formation, and you knocked it down like a child kicking a sandcastle."),
         "marah_hentak"),
        ["nama_baik 50", "punya kultivator:tema/air_suci 2"], "ambil kultivator:tema/air_suci 2",
        alasan("xie_bujuk_50", "Ia marah: butuh Nama Baik 50 dan dua botol air suci.", "He is furious: needs 50 Good Name and two flasks of holy water."),
        "oasis.formasi_pecah")

    D("tema_oasis_xie_duel", XD, [
        N("mulai", ("Masih berdiri? Bagus. Gurun menghargai yang keras kepala.", "Still standing? Good. The desert respects the stubborn."),
          [P(("Sekali lagi.", "Once more."), akibat=["mulai_duel"]), P(("Nanti.", "Later."))], gestur="sikap_angkuh",
          suara="tema.oasis.scorpion"),
    ], prioritas=55, syarat=["quest " + Q + " tahap duel"])

    D("tema_oasis_xie_pertapa", XD, [
        N("mulai", ("Duduklah. Dari ceruk ini aku bisa mendengar danau bernapas. Dulu kukira air harus dipaksa keluar. Ternyata air hanya "
                    "perlu didengarkan.",
                    "Sit. From this niche I can hear the lake breathing. I used to think water had to be forced out. It turns out water "
                    "only needs to be listened to."),
          [P(("Ajari aku mendengar air.", "Teach me to hear water."), "ajar"), P(("Apa kau menyesal?", "Do you regret it?"), "sesal"), P(PAMIT)],
          gestur="meditasi_napas", suara="tempat.hermit"),
        N("ajar", ("Pejamkan mata. Jangan mencari suara air; carilah keheningan di antara dua tetes. Di situlah urat air berjalan. "
                   "Kembalilah esok, dan esoknya lagi. Seratus kali duduk, baru sekali mendengar.",
                   "Close your eyes. Do not look for the sound of water; look for the silence between two drops. That is where the water "
                   "veins walk. Come back tomorrow, and the day after. A hundred sittings for one hearing."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="napas_meditasi"),
        N("sesal", ("Aku menyesal karena terlalu lama marah. Kini Shui Lian membawakanku teh setiap senja. Ia masih menumpahkan setetes "
                    "setiap kali. Aku tidak memberitahunya.",
                    "I regret staying angry for so long. Now Shui Lian brings me tea every dusk. She still spills a drop every time. I "
                    "haven't told her."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tertawa"),
    ], prioritas=40, syarat=["oasis_xie_pertapa"])

    # ------------------------------------------------------------------------------------------------ Nenek Gulnar
    BAHAN = alasan("bahan_ratu", "Butuh 1 Ekor Racun Kalajengking dan 6 Bunga Kaktus.", "Needs 1 Scorpion Venom Tail and 6 Cactus Flowers.")

    GN_PIL, GN_SIM = KENANG([
        ("benang_merah", ["quest " + Q + " selesai"],
         ("Nenek dengar danau sudah pulih?", "Did you hear the lake has recovered, Grandmother?"),
         ("Benangku tahu lebih dulu. Wol yang dicelup air danau kini menyerap merah lebih dalam, seperti zaman ibuku. Air yang sehat "
          "membuat warna yang jujur.",
          "My threads knew first. Wool dipped in lake water takes the red deeper now, like in my mother's day. Healthy water makes "
          "honest colours."), "tenun"),
    ])

    D("tema_oasis_gulnar_sapa", GN, [
        N("mulai", ("Duduklah di bantal itu, Nak. Jangan injak benang merahnya. Permadani gurun ditenun tiga musim, dijual dalam tiga "
                    "detik, dan dipakai tiga keturunan.",
                    "Sit on that cushion, child. Don't step on the red thread. A desert carpet is woven in three seasons, sold in three "
                    "seconds, and used by three generations."),
          [P(("Aku ingin melihat dagangan Nenek.", "I'd like to see your wares, Grandmother."), akibat=["buka_layanan tema_tawar"]),
           P(("Apa arti motif-motif ini?", "What do these patterns mean?"), "motif")] + GN_PIL + [P(PAMIT)], gestur="tenun"),
        N("motif", ("Belah ketupat itu mata air. Garis bergelombang itu jalan kafilah. Dan kalajengking di tepinya itu penjaga. Kata "
                    "nenekku, permadani tanpa kalajengking akan dicuri angin.",
                    "The diamond is the spring. The wavy line is the caravan road. And the scorpion at the border is the guardian. My "
                    "grandmother said a carpet without a scorpion gets stolen by the wind."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tunjuk"),
        *GN_SIM,
    ])

    D("tema_oasis_gulnar_ratu", GN, [
        N("mulai", ("Pernah dengar Permadani Ratu? Merah yang tak pernah pudar, kuning seperti matahari terbit. Ibuku menenun yang "
                    "terakhir untuk ibu Tuan Benteng. Aku masih ingat caranya... tapi bahannya, ah, bahannya.",
                    "Ever heard of the Queen's Carpet? A red that never fades, a yellow like sunrise. My mother wove the last one for the "
                    "Lord's mother. I still remember how... but the materials, ah, the materials."),
          [P(("Bahan apa yang Nenek butuhkan?", "What materials do you need?"), "bahan"), P(PAMIT)], gestur="tenun"),
        N("bahan", ("Merah dari ekor racun kalajengking gurun, direndam tujuh malam. Kuning dari enam kuntum bunga kaktus, dipetik "
                    "selagi berembun. Bawakan itu, dan aku akan menenun sekali lagi sebelum mataku menyerah.",
                    "Red from a desert scorpion's venom tail, soaked for seven nights. Yellow from six cactus flowers, picked while the "
                    "dew is on them. Bring me those, and I'll weave once more before my eyes give out."),
          [P(("Akan kubawakan.", "I'll bring them."), akibat=["quest_mulai tema_oasis_permadani_ratu"]),
           P(("Terlalu berbahaya.", "Too dangerous."))], gestur="bicara_tangan"),
    ], prioritas=30, syarat=["quest tema_oasis_permadani_ratu baru"])

    D("tema_oasis_gulnar_bahan", GN, [
        N("mulai", ("Sudah kau dapatkan bahannya, Nak?", "Have you found the materials, child?"),
          [P(("Ini ekor racun dan enam kuntum bunga kaktus.", "Here is the venom tail and six cactus flowers."), "terima",
             syarat=["punya kultivator:tema/ekor_racun_kalajengking", "punya minecraft:cactus_flower 6"], tampil=True, alasan=BAHAN,
             akibat=["ambil kultivator:tema/ekor_racun_kalajengking 1", "ambil minecraft:cactus_flower 6", "quest_maju tema_oasis_permadani_ratu"]),
           P(("Di mana aku mencarinya?", "Where do I look?"), "cari"), P(PAMIT)], gestur="tenun"),
        N("cari", ("Kalajengking besar bersarang di reruntuhan barat; para pemburu harta pulang membawa ekornya, atau tidak pulang sama "
                   "sekali. Kadang pedagang tamu Pasar Bulan Sabit menjualnya. Bunga kaktus mekar di puncak kaktus tua di bukit pasir.",
                   "Great scorpions nest in the western ruins; treasure hunters come back with a tail, or don't come back at all. "
                   "Sometimes the guest traders of the Crescent Market sell one. Cactus flowers bloom on top of old cacti in the dunes."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="tunjuk"),
        N("terima", ("Bagus, bagus. Dua hari, Nak. Dua hari dua malam, dan jangan ganggu aku.",
                     "Good, good. Two days, child. Two days and two nights, and don't disturb me."), gestur="tenun"),
    ], prioritas=50, syarat=["quest tema_oasis_permadani_ratu tahap bahan"])

    D("tema_oasis_gulnar_jadi", GN, [
        N("mulai", ("Lihatlah! (Permadani itu terbentang: merah membara, kuning fajar, dan seekor kalajengking kecil di sudutnya.) "
                    "Cucuku sudah membawanya ke istana. Pergilah ke Tuan Benteng; biar beliau tahu siapa yang mencari bahannya.",
                    "Look! (The carpet unrolls: blazing red, dawn yellow, and a little scorpion in its corner.) My grandson has already "
                    "carried it to the palace. Go to the Lord; let him know who found the materials."),
          [P(("Indah sekali, Nek.", "It's beautiful, Grandmother."), akibat=["quest_maju tema_oasis_permadani_ratu"])], gestur="tertawa"),
    ], prioritas=50, syarat=["quest tema_oasis_permadani_ratu tahap ambil"])


# ===================================================================================================================== c
def bagian_c():
    """dialogs part C: the oasis roles (priority 5, below the characters' greetings, 10: a role dialog also matches a named character of that role)."""
    PAMIT = ("Aku pamit.", "I'll take my leave.")
    LAIN = ("Ada hal lain.", "Something else.")
    KOTA = ["oasis_di_kota"]
    FESTIVAL = alasan("festival", "Hanya pada Malam Tari Putar (bulan purnama, selepas senja).",
                      "Only on the Night of the Whirling Dance (full moon, after dusk).")


    def kabar():
        return N("kabar", ("", ""), [P(LAIN, "mulai"), P(PAMIT)], gestur="bisik", kabar=True)


    # ------------------------------------------------------------------------------------------------ date garden
    D("tema_oasis_petani_kurma", "peran:petani", [
        N("mulai", ("Lihat pohon-pohon itu, berat oleh kurma. Tangan kami kurang di musim panen. Bantulah memetik lima hari berturut-turut, "
                    "dan kebun istana akan membayarmu dengan kurma emas yang memulihkan Qi.",
                    "Look at those trees, heavy with dates. We're short of hands at harvest. Help us pick five days in a row, and the "
                    "palace garden will pay you in golden dates that restore Qi."),
          [P(("Aku mau membantu.", "I'll help."), "petik", akibat=["quest_mulai tema_oasis_kurma_emas", "oasis_panen"]), P(PAMIT)],
          gestur="memanen", prop="keranjang"),
        N("petik", ("Petik yang warnanya seperti matahari sore. Datanglah lagi besok; kalau sehari saja kau absen, hitungannya mulai dari "
                    "awal.",
                    "Pick the ones the colour of the evening sun. Come back tomorrow; miss a single day and the count starts over."),
          gestur="tunjuk"),
    ], prioritas=25, syarat=KOTA + ["quest tema_oasis_kurma_emas baru"])

    D("tema_oasis_petani_panen", "peran:petani", [
        N("mulai", ("Selamat datang di Kebun Kurma. Mau ikut memetik hari ini?", "Welcome to the Date Garden. Will you pick with us today?"),
          [P(("Bantu memanen.", "Help with the harvest."), akibat=["oasis_panen"]),
           P(("Bagaimana kurma bisa tumbuh di gurun?", "How do dates grow in the desert?"), "tumbuh"), P(PAMIT)],
          gestur="memanen", prop="keranjang"),
        N("tumbuh", ("Kakinya di air, kepalanya di api, begitu kata leluhur kami. Saluran dari danau membasahi akarnya, matahari mematangkan "
                     "buahnya. Kalau danau menyusut, kebun inilah yang pertama mati.",
                     "Feet in water, head in fire, as our ancestors say. Channels from the lake wet the roots, the sun ripens the fruit. If "
                     "the lake shrinks, this garden is the first to die."), [P(LAIN, "mulai"), P(PAMIT)], gestur="bicara_tangan"),
    ], prioritas=20, syarat=KOTA)

    # ------------------------------------------------------------------------------------------------ dancers
    D("tema_oasis_penari", "peran:penari_gurun", [
        N("mulai", ("Gelang loncengku tetap berbunyi meski aku diam. Mau tahu rahasia Tari Putar?",
                    "My bell bracelets ring even when I stand still. Do you want to know the secret of the Whirling Dance?"),
          [P(("Biarkan aku ikut menari.", "Let me join the dance."), syarat=["oasis_festival"], tampil=True, alasan=FESTIVAL, akibat=["oasis_tari"]),
           P(("Apa rahasianya?", "What is the secret?"), "rahasia"),
           P(("Kapan Malam Tari Putar?", "When is the Night of the Whirling Dance?"), "kapan"), P(PAMIT)],
          gestur="kipas", prop="kipas_sutra", suara="tema.oasis.dance"),
        N("rahasia", ("Jangan memandang dunia yang berputar; pandanglah satu titik di dalam dadamu. Kata para pendeta, tarian ini dulu doa "
                      "agar angin membawa hujan. Sekarang ia membawa penonton, dan penonton membawa koin.",
                      "Don't watch the spinning world; watch one point inside your chest. The priests say this dance was once a prayer for "
                      "the wind to bring rain. Now it brings an audience, and the audience brings coins."), [P(LAIN, "mulai"), P(PAMIT)],
          gestur="tari_putar"),
        N("kapan", ("Setiap bulan purnama, selepas matahari tenggelam, di panggung tepi danau. Kecuali bila badai pasir datang; pasir di mata "
                    "penari bukan pertunjukan yang indah.",
                    "Every full moon, after the sun goes down, on the stage by the lake. Unless a sandstorm comes; sand in a dancer's eyes "
                    "is not a pretty show."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tunjuk"),
    ], prioritas=5, syarat=KOTA)

    # ------------------------------------------------------------------------------------------------ desert guards
    D("tema_oasis_pengawal", "peran:pengawal_gurun", [
        N("mulai", ("Berhenti di situ. ...Ah, kau sudah melewati Gerbang Giok. Baiklah. Jangan membuat keributan di bazar.",
                    "Halt there. ...Ah, you've already passed the Jade Gate. Very well. Don't make trouble in the bazaar."),
          [P(("Bagaimana dengan bea gerbang?", "What about the gate toll?"), "bea"),
           P(("Apa yang terjadi saat badai pasir?", "What happens in a sandstorm?"), "badai"),
           P(("Ada kabar?", "Any news?"), "kabar"), P(PAMIT)], gestur="sikap_tombak", prop="tombak"),
        N("bea", ("Tiga dari seratus bagian nilai barang dagang yang kau bawa masuk. Punya Surat Jalan Gurun dari Tuan Benteng? Tunjukkan "
                  "saja, dan kau lewat tanpa membayar sekeping pun.",
                  "Three parts in a hundred of the value of the trade goods you carry in. Have a Desert Pass from the Lord? Just show it, "
                  "and you pass without paying a single coin."), [P(LAIN, "mulai"), P(PAMIT)], gestur="tunjuk"),
        N("badai", ("Lonceng menara berbunyi tiga kali, kami menutup gerbang, dan semua orang masuk. Di luar tembok angin membawa pasir "
                    "setajam jarum. Pakailah kerudung pasir, atau mintalah restu di Kuil Air sebelum berangkat.",
                    "The tower bell rings three times, we close the gate, and everyone goes inside. Outside the walls the wind carries sand "
                    "as sharp as needles. Wear a sand veil, or ask for a blessing at the Water Temple before you set out."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="sikap_tombak", prop="tombak"),
        kabar(),
    ], prioritas=5, syarat=KOTA)

    # ------------------------------------------------------------------------------------------------ water priests
    D("tema_oasis_pendeta", "peran:pendeta_air", [
        N("mulai", ("Air bersamamu, pengembara. Pendeta Shui Lian memimpin kuil ini; kami hanya menjaga kolam dan menimba sumur.",
                    "Water be with you, traveller. Priestess Shui Lian leads this temple; we only tend the pool and draw from the well."),
          [P(("Mohon Restu Mata Air (3 NB).", "Ask for the Blessing of the Spring (3 NB)."), "restu", akibat=["oasis_restu"]),
           P(("Beli sebotol air suci (2 NB).", "Buy a flask of holy water (2 NB)."), "air", akibat=["oasis_air_suci 1"]),
           P(("Apa tugas seorang pendeta air?", "What does a water priest do?"), "tugas"), P(PAMIT)],
          gestur="sikap_doa", prop="kendi"),
        N("restu", ("Semoga air menjaga langkahmu di bawah matahari.", "May the water guard your steps beneath the sun."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="sikap_doa", prop="kendi"),
        N("air", ("Jangan diminum begitu saja; air suci untuk hal-hal yang suci.", "Don't just drink it; holy water is for holy things."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="tuang_teh", prop="kendi"),
        N("tugas", ("Fajar menimba sumur, pagi membaca Batu Ukur Air, sore berdoa di tepi danau, malam menyalakan lentera di tepinya. Seribu "
                    "tahun, setiap hari, tanpa sekali pun terlewat.",
                    "At dawn we draw the well, in the morning we read the Gauge Stone, in the afternoon we pray by the lake, at night we light "
                    "lanterns along its shore. A thousand years, every day, without missing one."), [P(LAIN, "mulai"), P(PAMIT)],
          gestur="bicara_tangan"),
    ], prioritas=5, syarat=KOTA)

    # ------------------------------------------------------------------------------------------------ camel drivers
    D("tema_oasis_penuntun", "peran:penuntun_unta", [
        N("mulai", ("(Ia menurunkan kain penutup mulutnya.) Mau menyewa unta? Dua belas Nilai Batu sehari. Lebih lambat dari kuda, tapi tak "
                    "pernah tenggelam di pasir isap.",
                    "(He lowers the cloth over his mouth.) Want to hire a camel? Twelve Spirit Stones a day. Slower than a horse, but it "
                    "never sinks in quicksand."),
          [P(("Tunjukkan unta sewaan dan jadwal kafilah.", "Show me the camels for hire and the caravan schedule."), akibat=["buka_layanan tema_kafilah"]),
           P(("Apa yang perlu kuketahui tentang unta?", "What should I know about camels?"), "unta"), P(PAMIT)],
          gestur="pimpin_unta", prop="tali_kekang", suara="tema.oasis.caravan"),
        N("unta", ("Unta berlari sebentar, lalu berjalan lagi; jangan dipaksa. Saat badai ia akan berlutut, dan kau harus berlindung di "
                   "baliknya. Kalau kau tinggalkan terlalu jauh, ia pulang sendiri ke kandang. Unta lebih pintar daripada kebanyakan "
                   "penunggangnya.",
                   "A camel sprints a little, then walks again; don't force it. In a storm it kneels, and you must shelter behind it. Leave "
                   "it too far behind and it walks home to the pen by itself. Camels are smarter than most of their riders."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="tepuk_bahu"),
    ], prioritas=5, syarat=KOTA)

    # ------------------------------------------------------------------------------------------------ bazaar traders
    D("tema_oasis_pedagang", "peran:pedagang_bazar", [
        N("mulai", ("Selamat datang, selamat datang! Harga pertama untuk orang bodoh, harga kedua untuk teman, harga ketiga untuk saudara. "
                    "Kau yang mana?",
                    "Welcome, welcome! The first price is for fools, the second for friends, the third for brothers. Which are you?"),
          [P(("Mari menawar.", "Let's haggle."), akibat=["buka_layanan tema_tawar"]),
           P(("Kapan Pasar Bulan Sabit buka?", "When does the Crescent Market open?"), "pasar"),
           P(("Ada kabar dari kafilah?", "Any news from the caravans?"), "kabar"), P(PAMIT)],
          gestur="seru_dagang", suara="tema.oasis.bazaar"),
        N("pasar", ("Setiap malam setelah panas reda, lampu kuningan menyala di sepanjang lorong. Saat bulan berbentuk sabit, pedagang tamu "
                    "dari barat ikut membuka kios: giok, peta reruntuhan, ekor kalajengking. Satu barang saja per kios, siapa cepat dia "
                    "dapat!",
                    "Every night once the heat breaks, brass lamps light up along the lanes. When the moon is a crescent, guest traders from "
                    "the west open stalls too: jade, ruin maps, scorpion tails. One item per stall only, first come first served!"),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="tawar_bazar"),
        kabar(),
    ], prioritas=5, syarat=KOTA)

    D("tema_oasis_kafilah_bashi", "peran:kafilah_bashi", [
        N("mulai", ("Kafilah kami membawa sutra ke barat dan giok ke timur. Mau menumpang atau mengawal?",
                    "Our caravans carry silk west and jade east. Will you ride along or stand guard?"),
          [P(("Tunjukkan jadwal kafilah.", "Show me the caravan schedule."), akibat=["buka_layanan tema_kafilah"]),
           P(("Seperti apa perjalanan kafilah?", "What is a caravan journey like?"), "jalan"), P(PAMIT)],
          gestur="pimpin_unta", prop="cambuk_kafilah", suara="tema.oasis.caravan"),
        N("jalan", ("Berangkat selepas fajar, berhenti saat panas memuncak, berjalan lagi sampai bintang muncul. Penumpang membayar delapan "
                    "Nilai Batu dan boleh tidur di pelana. Pengawal dibayar gilda, tapi harus siap menghadapi binatang gurun.",
                    "We set out after dawn, halt when the heat peaks, and walk again until the stars come out. Passengers pay eight Spirit "
                    "Stones and may sleep in the saddle. Guards are paid by the guild, but must be ready for desert beasts."),
          [P(LAIN, "mulai"), P(PAMIT)], gestur="tatap_langit"),
    ], prioritas=5, syarat=KOTA)

    D("tema_oasis_pelukis", "peran:pelukis_gua", [
        N("mulai", ("Hati-hati dengan tangga tebing, catnya belum kering. Mau membaca kisah mural?",
                    "Mind the cliff ladder, the paint isn't dry. Would you like to read the mural tales?"),
          [P(("Buka Kisah Mural.", "Open the Mural Tales."), akibat=["oasis_mural_buka"]),
           P(("Dari mana warna-warna ini?", "Where do these colours come from?"), "warna"), P(PAMIT)],
          gestur="lukis_dinding", prop="kuas"),
        N("warna", ("Merah dari tanah merah di barat, biru dari batu yang dibawa kafilah dari pegunungan, emas dari daun emas yang dipukul "
                    "setipis napas. Putih? Putih dari kapur tulang... jangan tanya tulang apa.",
                    "Red from the red earth of the west, blue from stones the caravans bring from the mountains, gold from gold leaf beaten "
                    "as thin as a breath. White? White is bone chalk... don't ask whose bones."), [P(LAIN, "mulai"), P(PAMIT)],
          gestur="bicara_tangan", prop="palet"),
    ], prioritas=5, syarat=KOTA)


# ===================================================================================================================== misc
def bagian_misc():
    """rumours, demand, schedule, loot and advancements."""
    DATA = os.path.join(REPO, "src", "main", "resources", "data", "kultivator")


    def tulis(rel, data):
        path = os.path.join(DATA, *rel.split("/"))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")


    # ================================================================================================ rumours (38.S.5)
    OASIS = ["pedagang_bazar", "penuntun_unta", "kafilah_bashi", "pengawal_gurun", "pendeta_air", "penari_gurun", "pelukis_gua"]
    KABAR = []


    def kabar(kid, pemicu, umur, peran, id_text, en_text, bobot=10):
        KABAR.append({"id": kid, "pemicu": pemicu, "bobot": bobot, "umur_hari": umur, "peran_penyebar": peran})
        teks("kultivator.cerita.kabar." + kid, id_text, en_text)


    # spread by the oasis code (CeritaApi.kabar / effect "kabar"; the trigger is the template's own id)
    kabar("tema_oasis_kafilah_tiba", "tema_oasis_kafilah_tiba", 2, ["pedagang_bazar", "kafilah_bashi", "penuntun_unta", "pemilik_teh", "warga"],
          "Kafilah Agung sudah membongkar muatan di karavanserai. Dua hari ini barang dari barat dijual lebih murah; cepatlah sebelum habis.",
          "The Great Caravan has unloaded at the caravanserai. For two days goods from the west sell cheaper; hurry before they're gone.")
    kabar("tema_oasis_air_pulih", "tema_oasis_air_pulih", 10, OASIS + ["warga", "pemilik_teh"],
          "Batu Ukur Air naik empat tanda dalam satu malam! Kata orang, %s yang menuangkan air pertama ke kolam suci.",
          "The Water Gauge Stone rose four marks in one night! They say %s poured the first water into the sacred pool.")
    kabar("tema_oasis_raja_tumbang", "tema_oasis_raja_tumbang", 6, ["pedagang_bazar", "petugas_gilda", "pemburu_lurus", "warga", "pemilik_teh"],
          "Raja Kalajengking di reruntuhan Kota Terkubur tumbang. Para penenun sudah menawar ekornya untuk pewarna merah.",
          "The Scorpion King in the ruins of the Buried City has fallen. The weavers are already bidding for its tail for red dye.")
    kabar("tema_oasis_xie_kalah", "tema_oasis_xie_kalah", 7, OASIS + ["warga"],
          "Pertapa kalajengking itu dikalahkan oleh %s di bawah matahari siang. Urat air kota tak lagi diisap dari bawah.",
          "The scorpion hermit was beaten by %s under the noon sun. The city's water veins are no longer drained from below.")
    kabar("tema_oasis_xie_pertapa", "tema_oasis_xie_pertapa", 10, OASIS + ["warga"],
          "Orang buangan tua itu kini duduk di ceruk kedua belas Tebing Seribu Lukisan. Katanya %s membujuknya pulang dengan sebotol air "
          "suci.",
          "The old exile now sits in the twelfth niche of the Cliff of a Thousand Paintings. They say %s coaxed him home with a flask of holy "
          "water.")
    kabar("tema_oasis_unta_pulang", "tema_oasis_unta_pulang", 4, ["kafilah_bashi", "penuntun_unta", "pemilik_teh", "warga"],
          "Si Belang pulang! Bulat Khan tertawa sampai unta-untanya ikut melenguh. %s yang menemukannya di balik bukit pasir.",
          "Si Belang is home! Bulat Khan laughed so hard his camels started groaning. %s found her behind a dune.")
    # themed events (TempatEvents.ACARA "tema.<id>" -> trigger acara_tema_<id>)
    kabar("tema_oasis_badai_1", "acara_tema_badai_pasir", 2, OASIS + ["warga"],
          "Lonceng menara berbunyi tiga kali: badai pasir! Gerbang Giok ditutup dan kafilah di jalan berhenti di balik untanya.",
          "The tower bell rang three times: a sandstorm! The Jade Gate is shut and the caravans on the road shelter behind their camels.")
    kabar("tema_oasis_badai_2", "acara_tema_badai_pasir", 3, ["pelukis_gua", "penuntun_unta", "pedagang_bazar"],
          "Sesudah badai, bukit pasir selalu memuntahkan sesuatu: koin kuno, pecahan mural, kadang lempeng peta. Carilah kilauannya di luar "
          "tembok.",
          "After a storm the dunes always spit something up: ancient coins, mural shards, sometimes a map tablet. Look for the glint outside "
          "the walls.")
    kabar("tema_oasis_kafilah_agung", "acara_tema_kafilah_agung", 1, OASIS + ["warga", "pemilik_teh"],
          "Dengar lonceng unta itu? Kafilah Agung melewati Gerbang Giok. Bazar akan berganti dagangan malam ini.",
          "Hear those camel bells? The Great Caravan is passing the Jade Gate. The bazaar will have new wares tonight.")
    kabar("tema_oasis_tari_putar", "acara_tema_tari_putar", 1, OASIS + ["warga", "pemilik_teh"],
          "Bulan purnama! Malam ini para penari berputar di panggung tepi danau. Siapa pun boleh ikut, kalau kakinya cukup cepat.",
          "Full moon! Tonight the dancers whirl on the lakeside stage. Anyone may join, if their feet are quick enough.")
    # finished side quests (TempatEvents.QUEST -> trigger quest_<id>, %s = the player)
    kabar("tema_oasis_permadani_jadi", "quest_tema_oasis_permadani_ratu", 8, ["pedagang_bazar", "pemilik_teh", "warga"],
          "Nenek Gulnar menenun Permadani Ratu lagi, yang pertama dalam enam puluh tahun. Bahannya dicari oleh %s.",
          "Grandmother Gulnar has woven the Queen's Carpet again, the first in sixty years. %s gathered its materials.")
    kabar("tema_oasis_kurma_jadi", "quest_tema_oasis_kurma_emas", 4, ["pedagang_bazar", "warga"],
          "Lima hari berturut-turut %s memanjat pohon kurma istana. Para petani bilang tangannya secepat monyet gurun.",
          "Five days in a row %s climbed the palace date palms. The farmers say those hands are as quick as a desert monkey's.")
    kabar("tema_oasis_mural_jadi", "quest_tema_oasis_mural_pesanan", 10, ["pelukis_gua", "pemilik_teh", "warga"],
          "Ceruk kedua belas Tebing Seribu Lukisan punya kisah baru: kisah %s. Mo Hua bilang matanya dilukis dengan daun emas.",
          "The twelfth niche of the Cliff of a Thousand Paintings has a new tale: that of %s. Mo Hua says the eyes are painted with gold leaf.")
    # background lines of the oasis roles (pemicu "umum", only these roles tell them)
    UMUM = [
        ("Jangan beli air dari orang di luar tembok. Air Toko Air sudah diberkati Kuil, air mereka diambil dari genangan unta.",
         "Never buy water from people outside the walls. The Water Shop's water is blessed by the Temple, theirs comes from camel puddles."),
        ("Pukul sebelas sampai tiga, bazar tutup. Siapa yang menawar di bawah matahari setinggi itu pasti orang asing.",
         "From eleven to three the bazaar is shut. Anyone haggling under a sun that high must be a foreigner."),
        ("Pada malam bulan sabit, pedagang tamu dari barat membuka kios di Bazar Barat. Mereka hanya membawa satu barang, tapi barangnya "
         "langka.",
         "On crescent-moon nights, guest traders from the west open stalls in the West Bazaar. They bring only one item each, but a rare one."),
        ("Bukit pasir di selatan bernyanyi kalau angin bertiup dari barat. Para penuntun bilang itu suara Kota Terkubur memanggil kafilahnya.",
         "The southern dunes sing when the wind blows from the west. The camel drivers say it's the Buried City calling its caravans."),
        ("Kerudung pasir lima belas Nilai Batu di kios permadani. Mahal? Coba saja berjalan di luar tembok saat badai tanpa kerudung.",
         "A sand veil costs fifteen Spirit Stones at the carpet stall. Expensive? Try walking outside the walls in a storm without one."),
        ("Pelukis muda itu, Mo Hua, katanya mendengar suara dari mural. Para pelukis tua tertawa, tapi tak satu pun mau tidur di ceruk ketujuh.",
         "That young painter, Mo Hua, claims he hears voices from the murals. The old painters laugh, but none will sleep in the seventh niche."),
        ("Tuan Benteng Sha Wuyan tak pernah tersenyum di aula. Tapi setiap fajar ia berdoa di Kuil Air, sendirian, sebelum siapa pun bangun.",
         "Lord Sha Wuyan never smiles in the hall. But every dawn he prays at the Water Temple, alone, before anyone else is awake."),
        ("Unta sewaan di kandang istana tak pernah tenggelam di pasir isap. Kuda? Sudah tiga kuda pedagang timur tenggelam bulan ini.",
         "The hired camels at the palace pen never sink in quicksand. Horses? Three eastern traders' horses have sunk this month."),
    ]
    UMUM += [
        ("Lonceng menara dibunyikan tiga kali kalau dinding pasir terlihat di cakrawala. Kalau kau dengar, lari ke karavanserai, jangan "
         "ke danau: di tepi air tak ada atap.",
         "The tower bell rings three times when a wall of sand shows on the horizon. If you hear it, run to the caravanserai, not the "
         "lake: there's no roof by the water."),
        ("Unta tahu badai lebih dulu dari pendeta. Kalau semua unta di lapangan berlutut menghadap timur, ikatlah kerudungmu.",
         "Camels know a storm before the priests do. If every camel in the field kneels facing east, tie your veil on."),
        ("Pedagang tamu Pasar Bulan Sabit tak pernah menawar. Satu harga, satu barang, satu malam. Besok kiosnya sudah jadi tumpukan kain.",
         "The Crescent Market's guest traders never haggle. One price, one item, one night. By morning the stall is a heap of cloth."),
        ("Ada yang membeli peta reruntuhan di Pasar Bulan Sabit lalu menjualnya lagi dua kali lipat di Rumah Uang. Petanya palsu. Rumah "
         "Uang tidak tertawa.",
         "Someone bought a ruin map at the Crescent Market and sold it again for double at the Money House. The map was fake. The Money "
         "House did not laugh."),
        ("Di Penginapan Pasir Bernyanyi, kamar atas sebelah timur paling sejuk. Pemiliknya menyimpannya untuk pengawal kafilah yang "
         "pulang dengan unta utuh.",
         "At the Singing Sand Inn the upper east room is the coolest. The owner keeps it for caravan guards who come home with every "
         "camel."),
        ("Pandai kuningan di Bazar Timur menempa lampu dari tembaga kafilah. Lampu yang belum menghijau, katanya, tidak boleh dijual "
         "kepada pengantin.",
         "The brass smith in the East Bazaar forges lamps from caravan copper. A lamp that hasn't turned green, he says, may not be sold "
         "to a bride."),
        ("Para pengawal gurun berlatih tombak tiap subuh di halaman barak. Siapa yang menjatuhkan tombaknya membersihkan palung unta "
         "seminggu.",
         "The desert guards drill with spears every dawn in the barracks yard. Whoever drops a spear cleans the camel trough for a week."),
        ("Penenun di rumah dekat Bazar Barat menolak menjual permadani hijau. Hijau itu warna danau, katanya, dan danau tidak dijual.",
         "The weaver near the West Bazaar refuses to sell green carpets. Green is the lake's colour, she says, and the lake isn't for "
         "sale."),
    ]
    for i, (a, b) in enumerate(UMUM):
        kabar("tema_oasis_umum_%d" % i, "umum", 30, OASIS, a, b, bobot=6)
    # the chain and the Lord's weekly orders done (TempatEvents.QUEST -> trigger quest_<id>, %s = the player)
    kabar("tema_oasis_mata_air_jadi", "quest_tema_oasis_mata_air", 12, OASIS + ["warga", "pemilik_teh"],
          "Antrean di sumur sudah bubar. Kata Pendeta Shui Lian, nama %s diukir di samping tanda tertinggi Batu Ukur Air.",
          "The queue at the well is gone. Priestess Shui Lian says the name of %s is carved beside the highest mark of the Gauge Stone.")
    kabar("tema_oasis_perintah_reruntuhan_jadi", "quest_tema_oasis_perintah_reruntuhan", 4, ["pengawal_gurun", "kafilah_bashi", "warga"],
          "Satu sarang di reruntuhan gurun dibersihkan lagi atas perintah Tuan Benteng. Yang membersihkannya %s.",
          "Another nest in the desert ruins was cleared on the Lord's orders. It was cleared by %s.")
    kabar("tema_oasis_perintah_kafilah_jadi", "quest_tema_oasis_perintah_kafilah", 4, ["kafilah_bashi", "penuntun_unta", "pemilik_teh"],
          "Kafilah minggu ini tiba tanpa kehilangan satu karung pun. Bulat Khan mentraktir %s teh tiga cawan.",
          "This week's caravan arrived without losing a single sack. Bulat Khan treated %s to three cups of tea.")
    kabar("tema_oasis_perintah_buru_jadi", "quest_tema_oasis_perintah_buru", 4, ["pengawal_gurun", "penuntun_unta", "warga"],
          "Kawanan binatang gurun yang mengganggu jalan kafilah sudah diburu oleh %s. Unta-unta bisa minum di palung luar lagi.",
          "The desert pack that harried the caravan road was hunted down by %s. The camels can drink at the outer trough again.")
    tulis("tempat/kabar/tema_oasis.json", {"templat": KABAR})

    # ================================================================================================ sale demand (38.M.4.2)
    tulis("tempat/permintaan/kota_tema_oasis.json", {
        "_catatan": "Kota Oasis: haus akan air, es, kayu dan logam; murah untuk pasir, kaktus dan hasil oasis sendiri.",
        "*": 1.0,
        "minecraft:water_bucket": 1.35, "minecraft:blue_ice": 1.35, "minecraft:packed_ice": 1.3, "minecraft:ice": 1.3,
        "minecraft:melon_slice": 1.25, "minecraft:oak_log": 1.25, "minecraft:spruce_log": 1.25, "minecraft:birch_log": 1.2,
        "minecraft:dark_oak_log": 1.2, "minecraft:iron_ingot": 1.2, "minecraft:copper_ingot": 1.15, "minecraft:leather": 1.2,
        "minecraft:wheat": 1.2, "minecraft:bread": 1.2, "minecraft:salmon": 1.25, "minecraft:cod": 1.25, "minecraft:kelp": 1.2,
        "kultivator:healing_pill": 1.15, "kultivator:dagang/rempah_seberang": 1.1,
        "minecraft:sand": 0.4, "minecraft:red_sand": 0.4, "minecraft:sandstone": 0.5, "minecraft:cactus": 0.5,
        "minecraft:dead_bush": 0.5, "minecraft:cactus_flower": 0.6, "kultivator:dagang/kurma_emas": 0.6,
        "kultivator:dagang/permadani_gurun": 0.7, "kultivator:dagang/giok_barat": 0.8,
    })

    # ================================================================================================ schedule (38.T.4.3 rows 38NL leaves open)
    E = lambda k, t, klip, kanan="", kiri="", jalan="", dalam=False: {  # noqa: E731
        **{"kegiatan": k, "tujuan": t, "klip": klip}, **({"kanan": kanan} if kanan else {}), **({"kiri": kiri} if kiri else {}),
        **({"jalan": jalan} if jalan else {}), **({"dalam": True} if dalam else {})}
    tulis("tempat/jadwal/kota_tema_oasis.json", {
        "jenis": "kota_tema/oasis",
        "peran": {
            # the Tuan Benteng: dawn prayer at the temple, audiences, the heat break, ledgers, the evening walk on the walls
            "tuan_benteng": {
                "5": E("doa_subuh", "dupa", "sujud_dupa", "dupa"),
                "6-7": E("makan", "meja_makan", "makan", "sumpit", "mangkuk"),
                "8-10": E("sidang", "@kerja", "duduk_kursi"),
                "11-14": E("jeda_panas", "@rumah", "kipas", "kipas_lipat"),
                "15-16": E("menulis", "meja_tulis", "menulis", "kuas", "papan_catatan"),
                "17": E("patroli", "jaga", "sikap_tangan_belakang", jalan="jalan_santai"),
                "18-20": E("jamuan", "meja_makan", "minum_teh", "cangkir", "lepek"),
                "21-4": E("tidur", "@kasur", "tidur", dalam=True),
            },
            # desert guards: spear drill at dawn, the gate in the morning, shade at the post in the heat, lantern rounds at night
            "pengawal_gurun": {
                "5": E("latihan_pagi", "latihan", "latihan_tombak", "tombak"),
                "6-10": E("patroli", "jaga", "berdiri_jaga", "tombak"),
                "11-14": E("jeda_panas", "jaga", "sikap_tombak", "tombak"),
                "15-17": E("patroli_siang", "jaga", "berdiri_jaga", "tombak", jalan="baris_jaga"),
                "18": E("makan", "meja_makan", "makan", "sumpit", "mangkuk"),
                "19-22": E("patroli_lentera", "jalan_lentera", "berdiri_jaga", "tombak", "lentera_tangan", jalan="baris_jaga"),
                "23-4": E("tidur", "@kasur", "tidur", dalam=True),
            },
            # oasis children: splashing at the lake edge in the cool morning, spinning tops in the courtyards after the heat
            "anak": {
                "7-10": E("main_air", "air", "main_kejar"),
                "15-17": E("main", "main", "main_gasing", "gasing"),
            },
        },
        "acara": {
            "tema.tari_putar": {"tuan_benteng": E("tonton_tari", "penonton", "tepuk_tangan")},
            "tema.kafilah_agung": {"pengawal_gurun": E("sambut_kafilah", "jaga", "sikap_tombak", "tombak")},
        },
    })

    # ================================================================================================ Raja Kalajengking loot
    def item(name, weight, lo=1, hi=1, age=None):
        e = {"type": "minecraft:item", "name": name, "weight": weight}
        # 26.3 format: "modifier" is one function object (dispatched by "type") or a list of them
        mods = []
        if hi > 1:
            mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
        if age:
            mods.append({"type": "minecraft:set_components", "components": {"kultivator:herb_age": age}})
        if mods:
            e["modifier"] = mods[0] if len(mods) == 1 else mods
        return e


    tulis("loot_table/tema/oasis/raja_kalajengking.json", {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
                item("kultivator:spirit_stone_mid", 24, 2, 5),
                item("kultivator:tema/koin_kuno", 20, 6, 14),
                item("kultivator:tema/pecahan_mural", 14, 1, 3),
                item("kultivator:beast_core_mid", 14),
                item("kultivator:purple_flame_flower", 8, age=200),
                item("minecraft:gold_ingot", 10, 2, 5),
                item("minecraft:emerald", 6, 1, 3),
                item("kultivator:spirit_stone_high", 4)]},
            {"rolls": 1, "entries": [
                item("kultivator:tema/lempeng_peta", 3),
                item("kultivator:dagang/giok_barat", 5, 1, 2),
                item("kultivator:beast_core_high", 2),
                {"type": "minecraft:empty", "weight": 10}]},
        ],
    })

    # ================================================================================================ advancements (theme extras)
    def adv(name, icon, frame, id_t, en_t, id_d, en_d, hidden=False):
        tulis("advancement/tempat/%s.json" % name, {
            "parent": "kultivator:tempat/tema_oasis",
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "display": {"description": {"translate": "advancement.kultivator.tempat.%s.description" % name},
                        "icon": {"id": icon},
                        "title": {"translate": "advancement.kultivator.tempat.%s.title" % name},
                        "frame": frame, **({"hidden": True} if hidden else {})},
            "requirements": [["done"]],
        })
        teks("advancement.kultivator.tempat.%s.title" % name, id_t, en_t)
        teks("advancement.kultivator.tempat.%s.description" % name, id_d, en_d)


    adv("tema_oasis_kalajengking", "kultivator:tema/ekor_racun_kalajengking", "goal", "Sengat Terakhir", "The Last Sting",
        "Kalahkan Raja Kalajengking di reruntuhan Kota Terkubur.", "Defeat the Scorpion King in the ruins of the Buried City.")
    adv("tema_oasis_penari", "kultivator:tema/selendang_penari", "task", "Berputar di Bawah Purnama", "Whirling Under the Full Moon",
        "Lulus Tari Putar pada Malam Tari Putar.", "Pass the Whirling Dance on the Night of the Whirling Dance.")
    adv("tema_oasis_si_belang", "minecraft:saddle", "task", "Saudara Kafilah", "Brother of the Caravan",
        "Bawa pulang Si Belang, unta pemimpin Bulat Khan.", "Bring home Si Belang, Bulat Khan's lead camel.")


# ===================================================================================================================== quest
def bagian_quest():
    """data/kultivator/tempat/quest/tema_oasis_*.json."""
    OUT = os.path.join(REPO, "src", "main", "resources", "data", "kultivator", "tempat", "quest")
    T = "kota_tema_oasis"
    Q = "tema_oasis_mata_air"

    def tulis(nama, data):
        with open(os.path.join(OUT, nama + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")

    QUESTS = {
        # ---------------------------------------------------------------- chain "Mata Air yang Menyusut" (38.T.4.7)
        Q: {"pemberi": "tokoh:shui_lian", "tempat": T, "tahap": [
            {"id": "ukur", "akibat": ["nama_baik 2"], "arah": "kota_tema/oasis", "ke": "kafilah"},
            {"id": "kafilah", "tujuan": ["oasis_kafilah akhir"], "arah": "kota_tema/oasis",
             "cabang": [{"syarat": ["bendera oasis.kafilah_hilang"], "ke": "cari_ajun"}], "ke": "lempeng"},
            {"id": "cari_ajun", "tujuan": ["bicara tokoh:ajun"],
             "akibat": ["oasis_ajun_pulang", "beri kultivator:tema/lempeng_peta", "beri_nb_pita 30", "nama_baik 1"], "ke": "mural"},
            {"id": "lempeng", "tujuan": ["hari 0"], "akibat": ["beri_nb_pita 60", "nama_baik 2"], "ke": "mural"},
            {"id": "mural", "tujuan": ["oasis_mural 3", "oasis_mural 6", "oasis_mural 7", "oasis_mural 9", "oasis_mural 11"],
             "akibat": ["oasis_paham 1"], "arah": "kota_tema/oasis", "ke": "formasi"},
            {"id": "formasi", "tujuan": ["oasis_formasi *"], "akibat": ["nama_baik 2"], "arah": "kota_tema/reruntuhan_gurun", "ke": "xie"},
            {"id": "xie", "akibat_masuk": ["oasis_panggil_xie"],
             "cabang": [{"syarat": ["bendera oasis.xie_bujuk"], "ke": "pertapa"}], "ke": "duel"},
            {"id": "duel", "tujuan": ["duel tokoh:xie_duo"], "akibat_masuk": ["oasis_panggil_xie"],
             "akibat": ["oasis_hadiah ekor_angin", "nama_baik 3", "kabar tema_oasis_xie_kalah pemain"], "ke": "pulih"},
            {"id": "pertapa", "tujuan": ["hari 0"],
             "akibat": ["oasis_hadiah giok_xie", "nama_baik 5", "kabar tema_oasis_xie_pertapa pemain"], "ke": "pulih"},
            {"id": "pulih", "arah": "kota_tema/oasis", "ke": "selesai"},
        ]},
        # ---------------------------------------------------------------- side quests
        "tema_oasis_unta_hilang": {"pemberi": "tokoh:bulat_khan", "tempat": T, "tahap": [
            {"id": "ajun", "arah": "kota_tema/oasis"},
            {"id": "cari", "tujuan": ["oasis_unta pulang"], "batas_hari": 3, "gagal_ke": "lepas"},
            {"id": "hadiah", "ke": "selesai"},
            {"id": "lepas", "tujuan": ["hari 0"], "akibat": ["nama_baik -1"], "ke": "gagal"},
        ]},
        "tema_oasis_mural_pesanan": {"pemberi": "tokoh:mo_hua", "tempat": T, "tahap": [
            {"id": "pesan", "tujuan": ["oasis_mural_dipesan"], "arah": "kota_tema/oasis"},
            {"id": "lukis", "tujuan": ["tunggu 3"]},
            {"id": "lihat", "tujuan": ["bicara tokoh:mo_hua"], "akibat": ["beri minecraft:painting 1", "nama_baik 2"], "ke": "selesai"},
        ]},
        "tema_oasis_permadani_ratu": {"pemberi": "tokoh:gulnar", "tempat": T, "tahap": [
            {"id": "bahan", "arah": "kota_tema/reruntuhan_gurun"},
            {"id": "tenun", "tujuan": ["tunggu 2"]},
            {"id": "ambil"},
            {"id": "antar", "ke": "selesai"},
        ]},
        "tema_oasis_kurma_emas": {"pemberi": "peran:petani", "tempat": T, "tahap": [
            {"id": "panen", "tujuan": ["oasis_kurma 5"], "arah": "kota_tema/oasis",
             "akibat": ["beri kultivator:dagang/kurma_emas 12", "nama_baik 2"], "ke": "selesai"},
        ]},
        # ---------------------------------------------------------------- Perintah Benteng (weekly, one of three)
        "tema_oasis_perintah_reruntuhan": {"pemberi": "tokoh:sha_wuyan", "tempat": T, "ulang": True, "tahap": [
            {"id": "bersihkan", "tujuan": ["pergi kota_tema/reruntuhan_gurun", "kalahkan monster x8"], "batas_hari": 7,
             "arah": "kota_tema/reruntuhan_gurun", "akibat": ["beri_nb_pita 40", "nama_baik 2"], "ke": "selesai"},
        ]},
        "tema_oasis_perintah_kafilah": {"pemberi": "tokoh:sha_wuyan", "tempat": T, "ulang": True, "tahap": [
            {"id": "kawal", "tujuan": ["misi_gilda kawal_unta"], "batas_hari": 7, "arah": "kota_tema/oasis",
             "akibat": ["beri_nb_pita 35", "nama_baik 2"], "ke": "selesai"},
        ]},
        "tema_oasis_perintah_buru": {"pemberi": "tokoh:sha_wuyan", "tempat": T, "ulang": True, "tahap": [
            {"id": "buru", "tujuan": ["kalahkan minecraft:husk x8"], "batas_hari": 7,
             "akibat": ["beri_nb_pita 30", "nama_baik 2"], "ke": "selesai"},
        ]},
    }

    for nama, data in QUESTS.items():
        tulis(nama, data)
    print("quests:", len(QUESTS))


# ===================================================================================================================== lang
def bagian_lang():
    """Writes lang-src/tempat_tema_oasis.{id_id,en_us}.json; refuses clashes and missing Java keys."""
    R = REPO
    merged = dict(LANG)
    LS = os.path.join(R, "lang-src")
    other = {}
    for f in glob.glob(os.path.join(LS, "*.id_id.json")):
        if os.path.basename(f).startswith("tempat_tema_oasis."):
            continue
        for k in json.load(open(f, encoding="utf-8")):
            other[k] = os.path.basename(f)
    bad = [(k, other[k]) for k in merged if k in other]
    if bad:
        for k, f in bad:
            print("CLASH", k, f)
        raise SystemExit(1)
    # every literal key of the oasis Java code (dynamic families are covered by the parts)
    JD = os.path.join(R, "src", "main", "java", "dev", "kultivator", "dunia", "tempat", "tema", "oasis")
    missing = []
    for f in glob.glob(os.path.join(JD, "*.java")):
        for k in re.findall(r'"(kultivator\.kota_tema\.oasis\.[a-z0-9_.]*[a-z0-9_])"', open(f, encoding="utf-8").read()):
            if k not in merged:
                missing.append(k)
    for fam, n in (("antre.", 4), ("tawar.hina.", 3), ("tawar.teman.", 3), ("tawar.jadi.", 3), ("tamu.seru.", 4), ("arah.", 8)):
        for i in range(n):
            if "kultivator.kota_tema.oasis." + fam + str(i) not in merged:
                missing.append(fam + str(i))
    for i in range(1, 13):
        for s in ("nama", "teks"):
            if "kultivator.kota_tema.oasis.mural.%d.%s" % (i, s) not in merged:
                missing.append("mural %d %s" % (i, s))
    if missing:
        raise SystemExit("missing: %s" % sorted(set(missing)))
    for loc, i in (("id_id", 0), ("en_us", 1)):
        out = {k: merged[k][i] for k in sorted(merged)}
        with open(os.path.join(LS, "tempat_tema_oasis.%s.json" % loc), "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("tempat_tema_oasis: %d keys (id_id = en_us)" % len(merged))


def main():
    setel(os.path.dirname(os.path.dirname(os.path.abspath(__file__))) if len(sys.argv) < 2 else sys.argv[1])
    for fn in (bagian_java, bagian_a, bagian_b, bagian_c, bagian_misc, bagian_quest, bagian_lang):
        fn()


if __name__ == "__main__":
    main()
