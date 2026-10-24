#!/usr/bin/env python3
"""Writes lang-src/sosok.{id_id,en_us}.json (DESIGN 40, module 40I): clip and prop names, identity words, gendered
disciple names 36-71, gendered given names of town people, look-key names and the messages of
/kultivasi uji penampilan. Clip and prop ids are read from GerakId.java / PropId.java (never hand-numbered here).

Run from the repo root:  python -I tools/gen_lang_sosok.py
Both files get identical keys (checked before writing); Indonesian is primary.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOSOK = os.path.join(ROOT, "src", "main", "java", "dev", "kultivator", "sosok")
OUT = os.path.join(ROOT, "lang-src")

# ------------------------------------------------------------------------------------------------ clips (GerakId)
KLIP = {
    "jalan_santai": ("Jalan santai", "Easy walk"),
    "jalan_tergesa": ("Jalan tergesa", "Hurried walk"),
    "jalan_tua": ("Jalan orang tua", "Old person's walk"),
    "jalan_bawa": ("Jalan membawa barang", "Walk carrying a load"),
    "jalan_pikul": ("Jalan memikul", "Walk with a carrying pole"),
    "jalan_anak": ("Jalan anak", "Child's walk"),
    "baris_jaga": ("Baris penjaga", "Guard march"),
    "lari": ("Lari", "Run"),
    "lari_takut": ("Lari ketakutan", "Fleeing run"),
    "duduk_kursi": ("Duduk di kursi", "Sit on a chair"),
    "duduk_lantai": ("Duduk di lantai", "Sit on the floor"),
    "napas_meditasi": ("Napas meditasi", "Meditation breath"),
    "tidur": ("Tidur", "Sleep"),
    "tidur_duduk": ("Tertidur sambil duduk", "Dozing while seated"),
    "berdiri_jaga": ("Berdiri jaga", "Stand watch"),
    "berlutut": ("Berlutut", "Kneel"),
    "takut_sembunyi": ("Takut dan bersembunyi", "Cower and hide"),
    "kaget": ("Kaget", "Startle"),
    "makan": ("Makan", "Eat"),
    "minum_teh": ("Minum teh", "Drink tea"),
    "tuang_teh": ("Menuang teh", "Pour tea"),
    "masak": ("Memasak", "Cook"),
    "sapu": ("Menyapu", "Sweep"),
    "palu_landasan": ("Menempa di landasan", "Hammer on the anvil"),
    "tumbuk_herba": ("Menumbuk herba", "Pound herbs"),
    "menulis": ("Menulis", "Write"),
    "membaca": ("Membaca", "Read"),
    "hitung_sempoa": ("Menghitung dengan sempoa", "Count on the abacus"),
    "tawar": ("Menawar", "Haggle"),
    "seru_dagang": ("Menyerukan dagangan", "Call out wares"),
    "hormat": ("Memberi hormat", "Salute"),
    "hormat_dalam": ("Hormat dalam", "Deep bow"),
    "lambai": ("Melambai", "Wave"),
    "tunjuk": ("Menunjuk", "Point"),
    "tertawa": ("Tertawa", "Laugh"),
    "berdebat": ("Berdebat", "Argue"),
    "bicara_tangan": ("Bicara dengan tangan", "Talk with the hands"),
    "bisik": ("Berbisik", "Whisper"),
    "marah_tunjuk": ("Menunjuk dengan marah", "Point in anger"),
    "angkat_bawa": ("Mengangkat barang", "Lift and carry"),
    "memancing": ("Memancing", "Fish with a rod"),
    "mencangkul": ("Mencangkul", "Hoe"),
    "memanen": ("Memanen", "Harvest"),
    "tanam_bibit": ("Menanam bibit", "Plant seedlings"),
    "angkat_air": ("Mengangkat air", "Carry water"),
    "sujud_dupa": ("Bersujud dengan dupa", "Bow with incense"),
    "basuh_muka": ("Membasuh muka", "Wash the face"),
    "genderang": ("Menabuh genderang", "Beat the drum"),
    "palu_hakim": ("Mengetuk palu hakim", "Strike the judge's gavel"),
    "lempar_pakan": ("Menabur pakan", "Scatter feed"),
    "kipas": ("Berkipas", "Fan oneself"),
    "catur": ("Bermain catur", "Play chess"),
    "cerita": ("Bercerita", "Tell a story"),
    "hangat_tangan": ("Menghangatkan tangan", "Warm the hands"),
    "tatap_langit": ("Menatap langit", "Gaze at the sky"),
    "basuh_pakaian": ("Mencuci dan menjemur pakaian", "Wash and hang laundry"),
    "rak_susun": ("Menyusun rak", "Stack shelves"),
    "hunus": ("Menghunus senjata", "Draw a weapon"),
    "latihan_pedang_1": ("Latihan pedang (tusukan)", "Sword drill (thrust)"),
    "latihan_pedang_2": ("Latihan pedang (tebasan)", "Sword drill (cut)"),
    "latihan_pedang_3": ("Latihan pedang (putaran)", "Sword drill (spin)"),
    "latihan_tinju": ("Latihan tinju", "Fist drill"),
    "latihan_tombak": ("Latihan tombak", "Spear drill"),
    "tai_chi_pagi": ("Tai chi pagi", "Morning tai chi"),
    "tanding_a": ("Tanding (pihak A)", "Sparring (side A)"),
    "tanding_b": ("Tanding (pihak B)", "Sparring (side B)"),
    "main_kejar": ("Main kejar-kejaran", "Play tag"),
    "main_lompat": ("Main lompat-lompatan", "Play hopscotch"),
    "main_gasing": ("Main gasing", "Spin a top"),
    "main_layangan": ("Main layang-layang", "Fly a kite"),
    "main_lentera": ("Main lentera", "Play with a lantern"),
    "menari_panen": ("Tari panen", "Harvest dance"),
    "perbaiki_palu": ("Memperbaiki dengan palu", "Repair with a hammer"),
    "perbaiki_plester": ("Memplester dinding", "Plaster a wall"),
    "angkat_papan": ("Mengangkat papan", "Carry planks"),
    "ganti_tanah": ("Mengganti tanah", "Replace soil"),
    "tanam_ulang": ("Menanam ulang", "Replant"),
    "dayung_yuloh": ("Mendayung yuloh", "Scull with a yuloh"),
    "tarik_jala": ("Menarik jala", "Haul a net"),
    "jemur_ikan": ("Menjemur ikan", "Dry fish"),
    "ikat_tali": ("Mengikat tali", "Tie ropes"),
    "palu_lunas": ("Memalu lunas kapal", "Hammer a ship's keel"),
    "teropong_laut": ("Meneropong laut", "Scan the sea with a spyglass"),
    "pimpin_unta": ("Menuntun unta", "Lead a camel"),
    "tawar_bazar": ("Menawar di bazar", "Bargain at the bazaar"),
    "tari_putar": ("Tari berputar", "Whirling dance"),
    "lukis_dinding": ("Melukis dinding gua", "Paint a cave wall"),
    "timba_sumur": ("Menimba sumur", "Draw from a well"),
    "tenun": ("Menenun", "Weave"),
    "petik_teh": ("Memetik teh", "Pick tea"),
    "seduh_teh": ("Menyeduh teh", "Brew tea"),
    "nyanyi_perahu": ("Bernyanyi di perahu", "Boat song"),
    "lepas_lentera_air": ("Melepas lentera air", "Release a water lantern"),
    "daki_pikul": ("Mendaki sambil memikul", "Climb with a load"),
    "putar_katrol": ("Memutar katrol", "Turn a winch"),
    "petik_herba_tebing": ("Memetik herba tebing", "Pick cliff herbs"),
    "sujud_ziarah": ("Sujud peziarah", "Pilgrim's prostration"),
    "tabuh_ikan_kayu": ("Menabuh ikan kayu", "Strike the wooden fish"),
    "sorak_arena": ("Bersorak di arena", "Cheer in the arena"),
    "asah_pisau": ("Mengasah pisau", "Whet a knife"),
    "ancam": ("Mengancam", "Threaten"),
    "hitung_koin_gelap": ("Menghitung koin gelap", "Count shady coins"),
    "berlutut_rantai": ("Berlutut terantai", "Kneel in chains"),
    "semedi_darah": ("Semedi darah", "Blood meditation"),
    "pikul_tandu": ("Memikul tandu", "Bear a sedan chair"),
    "tabuh_genderang_perahu": ("Menabuh genderang perahu", "Beat the boat drum"),
    "sikap_tangan_belakang": ("Sikap: tangan di punggung", "Stance: hands behind the back"),
    "sikap_lipat_tangan": ("Sikap: tangan terlipat", "Stance: arms folded"),
    "sikap_tangan_lengan": ("Sikap: tangan di lengan baju", "Stance: hands in the sleeves"),
    "sikap_tombak": ("Sikap: bertumpu tombak", "Stance: leaning on a spear"),
    "sikap_gagang": ("Sikap: tangan di gagang", "Stance: hand on the hilt"),
    "sikap_santai_l": ("Sikap: santai (laki-laki)", "Stance: relaxed (man)"),
    "sikap_santai_p": ("Sikap: santai (perempuan)", "Stance: relaxed (woman)"),
    "sikap_tua": ("Sikap: orang tua", "Stance: elder"),
    "sikap_anak": ("Sikap: anak", "Stance: child"),
    "sikap_berkacak": ("Sikap: berkacak pinggang", "Stance: hands on hips"),
    "sikap_tunduk": ("Sikap: menunduk", "Stance: head bowed"),
    "sikap_angkuh": ("Sikap: angkuh", "Stance: haughty"),
    "sikap_doa": ("Sikap: tangan berdoa", "Stance: praying hands"),
    "sikap_tangan_depan": ("Sikap: tangan di depan", "Stance: hands clasped in front"),
    "meditasi_napas": ("Meditasi napas", "Breathing meditation"),
    "sirkulasi_qi": ("Sirkulasi qi", "Qi circulation"),
    "segel_tangan": ("Segel tangan", "Hand seals"),
    "tiang_berdiri": ("Kuda-kuda tiang berdiri", "Standing post"),
    "telapak_latihan": ("Latihan telapak", "Palm drill"),
    "asah_pedang": ("Mengasah pedang", "Whet a sword"),
    "tebas_udara": ("Menebas udara", "Cut the air"),
    "sujud_guru": ("Sujud kepada guru", "Kowtow to the master"),
    "kipas_tungku": ("Mengipas tungku", "Fan the furnace"),
    "racik_herba": ("Meracik herba", "Blend herbs"),
    "gulung_pil": ("Menggulung pil", "Roll pills"),
    "pasang_bendera": ("Memasang bendera formasi", "Plant formation flags"),
    "tulis_jimat": ("Menulis jimat", "Write a talisman"),
    "tempel_jimat": ("Menempel jimat", "Paste a talisman"),
    "periksa_nadi": ("Memeriksa nadi", "Take a pulse"),
    "beri_makan_binatang": ("Memberi makan binatang roh", "Feed a spirit beast"),
    "mengajar": ("Mengajar", "Teach"),
    "main_seruling": ("Meniup seruling", "Play the flute"),
    "main_guqin": ("Memetik guqin", "Play the guqin"),
    "minum_arak": ("Minum arak", "Drink wine"),
    "makan_pil": ("Menelan pil", "Swallow a pill"),
    "genggam_batu_roh": ("Menggenggam batu roh", "Hold spirit stones"),
    "terobosan_berhasil": ("Terobosan berhasil", "Breakthrough succeeded"),
    "terobosan_gagal": ("Terobosan gagal", "Breakthrough failed"),
    "pincang": ("Berjalan pincang", "Limp"),
    "lengan_digendong": ("Lengan digendong", "Arm in a sling"),
    "menguap_peregangan": ("Menguap dan meregang", "Yawn and stretch"),
    "menangis": ("Menangis", "Weep"),
    "fungsi_belajar": ("Fungsi: belajar di paviliun", "Function: study in the pavilion"),
    "fungsi_berkabung": ("Fungsi: berkabung", "Function: mourning"),
    "fungsi_tambang": ("Fungsi: menambang", "Function: mining"),
    "fungsi_rawat": ("Fungsi: merawat kebun roh", "Function: tending the spirit garden"),
    "fungsi_kantor": ("Fungsi: kerja kantor sekte", "Function: sect office work"),
    "fungsi_teh": ("Fungsi: kumpul minum teh", "Function: tea gathering"),
    "fungsi_merajuk": ("Fungsi: merajuk", "Function: sulking"),
    "fungsi_apel": ("Fungsi: apel", "Function: muster"),
    "fungsi_mendaki": ("Fungsi: mendaki tangga", "Function: climbing the stairs"),
    "fungsi_pergi": ("Fungsi: pergi dari sekte", "Function: leaving the sect"),
    "fungsi_sujud_tetua": ("Fungsi: sujud kepada tetua", "Function: bowing to an elder"),
    "fungsi_cadangan": ("Fungsi: cadangan", "Function: reserved"),
    "terbang_pedang": ("Terbang di atas pedang", "Sword flight"),
    "jaga_tepi": ("Berjaga di tepi", "Guard the edge"),
    "memandang": ("Memandang jauh", "Gaze into the distance"),
    "tepuk_tangan": ("Bertepuk tangan", "Clap"),
    "tepuk_bahu": ("Menepuk bahu", "Pat a shoulder"),
    "angguk_dengar": ("Mengangguk mendengarkan", "Nod while listening"),
    "lari_lambai": ("Berlari sambil melambai", "Run and wave"),
    "tidur_meja": ("Tertidur di meja", "Asleep at the desk"),
    "marah_hentak": ("Menghentak marah", "Stamp in anger"),
    "bicara": ("Berbicara", "Speak"),
    "latih_jurus": ("Melatih jurus", "Practise a form"),
    "berkemas": ("Berkemas", "Pack up"),
    "lihat_barang": ("Melihat-lihat barang", "Browse wares"),
    "bersandar": ("Bersandar", "Lean back and rest"),
    "berdiri_di_pedang": ("Berdiri di atas pedang", "Stand on a flying sword"),
}

# ------------------------------------------------------------------------------------------------ props (PropId)
PROP = {
    "mangkuk": ("Mangkuk", "Bowl"), "sumpit": ("Sumpit", "Chopsticks"), "cangkir": ("Cangkir", "Cup"),
    "lepek": ("Lepek", "Saucer"), "teko": ("Teko", "Teapot"), "sendok_sayur": ("Sendok sayur", "Ladle"),
    "sapu": ("Sapu", "Broom"), "palu": ("Palu", "Hammer"), "alu": ("Alu", "Pestle"), "kuas": ("Kuas", "Brush"),
    "kitab": ("Kitab", "Book"), "sempoa": ("Sempoa", "Abacus"), "tongkat": ("Tongkat", "Walking stick"),
    "peti": ("Peti", "Chest"), "pikulan": ("Pikulan", "Carrying pole"), "joran": ("Joran", "Fishing rod"),
    "cangkul": ("Cangkul", "Hoe"), "keranjang": ("Keranjang", "Basket"), "ember": ("Ember", "Bucket"),
    "dupa": ("Dupa", "Incense"), "pemukul": ("Pemukul genderang", "Drumstick"), "palu_kayu": ("Palu kayu", "Gavel"),
    "kipas_lipat": ("Kipas lipat", "Folding fan"), "papan_catur": ("Papan catur", "Chessboard"),
    "payung_kertas": ("Payung kertas", "Paper umbrella"), "gasing": ("Gasing", "Spinning top"),
    "layang_layang": ("Layang-layang", "Kite"), "lentera_tangan": ("Lentera tangan", "Hand lantern"),
    "kantong_uang": ("Kantong uang", "Money pouch"), "nampan": ("Nampan", "Tray"),
    "sendok_plester": ("Sendok plester", "Trowel"), "papan_kayu": ("Papan kayu", "Plank"), "sekop": ("Sekop", "Shovel"),
    "bibit": ("Bibit", "Seedlings"), "tombak": ("Tombak", "Spear"), "kain": ("Kain", "Cloth"),
    "teropong": ("Teropong", "Spyglass"), "labu_pil": ("Labu pil", "Pill gourd"), "pahat": ("Pahat", "Chisel"),
    "batu_mentah": ("Batu mentah", "Raw stone"), "dayung_yuloh": ("Dayung yuloh", "Yuloh oar"), "jala": ("Jala", "Net"),
    "ikan_tali": ("Ikan bertali", "Fish on a string"), "tali": ("Tali", "Rope"), "palu_dempul": ("Palu dempul", "Caulking mallet"),
    "kuas_besar": ("Kuas besar", "Large brush"), "palet": ("Palet", "Palette"), "kendi": ("Kendi", "Water jug"),
    "keranjang_teh": ("Keranjang teh", "Tea basket"), "lentera_teratai": ("Lentera teratai", "Lotus lantern"),
    "pemukul_kecil": ("Pemukul kecil", "Small beater"), "ikan_kayu": ("Ikan kayu", "Wooden fish"),
    "tongkat_cincin": ("Tongkat bercincin", "Ringed staff"), "tongkat_bambu": ("Tongkat bambu", "Bamboo staff"),
    "tas_dupa": ("Tas dupa", "Incense bag"), "rangka_punggung": ("Rangka punggung", "Back frame"),
    "pisau_ganda": ("Pisau ganda", "Twin knives"), "rantai_tangan": ("Rantai tangan", "Wrist chains"),
    "cawan_racun": ("Cawan racun", "Poison cup"), "cambuk_kafilah": ("Cambuk kafilah", "Caravan whip"),
    "sabit_herba": ("Sabit herba", "Herb sickle"), "kipas_sutra": ("Kipas sutra", "Silk fan"),
    "kertas_jimat": ("Kertas jimat", "Talisman paper"), "kipas_tungku": ("Kipas tungku", "Furnace fan"),
    "kebut": ("Kebut", "Horsetail whisk"), "papan_titah": ("Papan titah", "Edict tablet"),
    "papan_catatan": ("Papan catatan", "Record board"), "tali_kekang": ("Tali kekang", "Reins"),
    "piringan_formasi": ("Piringan formasi", "Formation disc"), "kunci_kamar": ("Kunci kamar", "Room key"),
    "kotak_obat": ("Kotak obat", "Medicine box"), "gulungan": ("Gulungan", "Scroll"), "cawan_arak": ("Cawan arak", "Wine cup"),
    "labu_arak": ("Labu arak", "Wine gourd"), "pil": ("Pil", "Pill"), "botol_pil": ("Botol pil", "Pill bottle"),
    "batu_roh": ("Batu roh", "Spirit stone"), "herba": ("Herba", "Herbs"), "seruling": ("Seruling", "Flute"),
    "guqin": ("Guqin", "Guqin"), "pedang_kayu": ("Pedang kayu", "Wooden sword"), "peluit": ("Peluit", "Whistle"),
    "cap_sekte": ("Cap sekte", "Sect seal"), "bendera_formasi": ("Bendera formasi", "Formation flag"),
    "kantong_qiankun": ("Kantong Qiankun", "Qiankun pouch"), "cermin_perunggu": ("Cermin perunggu", "Bronze mirror"),
    "tasbih_tangan": ("Tasbih tangan", "Prayer beads"), "trofi_taring": ("Trofi taring", "Fang trophy"),
}

JALAN = {
    "pedang": ("Pendekar Pedang", "Sword Cultivator"), "alkemis": ("Alkemis", "Alchemist"),
    "formasi": ("Ahli Formasi & Jimat", "Formation & Talisman Master"), "pertapa": ("Pertapa", "Hermit"),
    "petualang": ("Petualang", "Adventurer"), "pemburu": ("Pemburu Binatang", "Beast Hunter"),
    "pedagang": ("Pedagang", "Merchant"), "cendekia": ("Cendekia Kitab", "Scripture Scholar"),
    "penjaga": ("Penjaga Sekte", "Sect Guardian"), "iblis": ("Jalan Iblis", "Demonic Path"),
    "tempa": ("Penempa", "Smith"), "tabib": ("Tabib", "Physician"), "petani_roh": ("Petani Roh", "Spirit Farmer"),
    "musisi": ("Musisi", "Musician"), "bangsawan": ("Bangsawan", "Noble"), "tidak_ada": ("Belum ada jalan", "No path yet"),
}

# look keys that are not NpcRole ids (role titles come from kultivator.kota.peran.*)
RUPA = {
    "d.meng_po": ("Meng Po", "Meng Po"), "d.hakim": ("Hakim Dunia Bawah", "Underworld Judge"),
    "d.raja_yama": ("Raja Yama", "King Yama"), "d.arwah": ("Arwah", "Wandering Soul"),
    "d.penjaga_laut": ("Penjaga Istana Laut", "Sea Palace Guard"), "d.pertapa_laut": ("Pertapa Laut", "Sea Hermit"),
    "d.dewa_penglai": ("Dewa Penglai", "Penglai Immortal"), "d.nelayan": ("Nelayan Laut Tanpa Batas", "Boundless Sea Fisher"),
    "d.pedagang": ("Pedagang Pulau Pasar", "Market Isle Merchant"),
    "utusan_langit": ("Utusan Murka Langit", "Envoy of Heaven's Wrath"),
    "penonton.utusan": ("Utusan Sekte Tamu", "Guest Sect Envoy"), "penonton.warga": ("Penonton Warga", "Townsfolk Spectator"),
    "murid.0": ("Murid Tercatat", "Registered Disciple"), "murid.1": ("Murid Luar", "Outer Disciple"),
    "murid.2": ("Murid Dalam", "Inner Disciple"), "murid.3": ("Murid Inti", "Core Disciple"),
    "murid.4": ("Tetua", "Elder"), "murid.5": ("Tetua Agung", "Grand Elder"),
    "murid.calon": ("Calon Murid", "Candidate"), "murid.saingan": ("Murid Saingan", "Rival Disciple"),
    "murid.bayangan": ("Bayangan Alam Hampa", "Void Realm Phantom"), "murid.klon": ("Klon Ilahi", "Divine Clone"),
    "kelompok.PEKERJA": ("Pekerja", "Worker"), "kelompok.PEDAGANG": ("Pedagang", "Trader"),
    "kelompok.PENJAGA": ("Penjaga", "Guard"), "kelompok.ANAK": ("Anak", "Child"), "kelompok.TETUA": ("Tetua", "Elder"),
    "kelompok.KULTIVATOR": ("Kultivator", "Cultivator"), "kelompok.CENDEKIA": ("Cendekia", "Scholar"),
    "kelompok.RAHIB": ("Rahib", "Monk"), "kelompok.PEJABAT": ("Pejabat", "Official"),
    "kelompok.KHUSUS": ("Orang khusus", "Special person"), "kelompok.SIPIL": ("Warga sipil", "Civilian"),
}
# the eight demonic cults of kultivator.musuh.sect.0-7 (lang-src/musuh.*), same order
ROGUE = [("Kultivator Liar Kultus Bulan Darah", "Rogue of the Blood Moon Cult"),
         ("Kultivator Liar Kultus Tulang Putih", "Rogue of the White Bone Cult"),
         ("Kultivator Liar Kultus Seribu Bisa", "Rogue of the Thousand Venom Cult"),
         ("Kultivator Liar Kultus Hantu Salju", "Rogue of the Frost Ghost Cult"),
         ("Kultivator Liar Kultus Hampa Hitam", "Rogue of the Black Void Cult"),
         ("Kultivator Liar Kultus Petir Neraka", "Rogue of the Hell Thunder Cult"),
         ("Kultivator Liar Kultus Arus Hitam", "Rogue of the Black Tide Cult"),
         ("Kultivator Liar Kultus Gunung Mayat", "Rogue of the Corpse Mountain Cult")]
for i, (a, b) in enumerate(ROGUE):
    RUPA["liar.%d" % i] = (a, b)

# ------------------------------------------------------------------------------------------------ names (40.2.5)
NAMA_MURID_P = ["Liu Ruyan", "Shen Yueru", "Xie Wanqing", "Lan Xinyao", "Qing Ziyan", "Mu Wanrou", "Jiang Lixue", "Lin Shiyin",
                "Su Mengyao", "Chen Yuwei", "Wen Xiangyun", "Gong Yuhua", "Luo Qingyao", "Xia Ningzhi", "Tang Zixuan", "Yao Yunxi",
                "He Lianyi", "Zhou Bingyu", "Hua Yinxue", "Qiu Lingxi"]
NAMA_MURID_L = ["Gao Yunhe", "Tie Hanshan", "Lei Zhenyu", "Qu Fengming", "Yu Wenchang", "Xue Tianheng", "Kong Yanshu", "Huo Lieyang",
                "Peng Jingshan", "Cao Mosheng", "Wang Xuance", "Du Qianshan", "Ling Haoran", "Situ Jian", "Rong Beichen", "Wei Changge"]
NAMA_DIRI_L = ["Feng", "Hao", "Jian", "Ming", "Shan", "Tian", "Wei", "Zhen", "Hong", "Kai", "Long", "Ping", "Tao", "Lei", "Bo",
               "Cheng", "Gang", "Heng", "Jun", "Kun", "Peng", "Qiang", "Wen", "Yong"]
NAMA_DIRI_P = ["Xue", "Lan", "Mei", "Qing", "Xin", "Yao", "Jing", "Ling", "Ning", "Qiu", "Shu", "Yan", "Yu", "Zi", "Hua", "Rui",
               "Yun", "Wan", "Rou", "Ying", "Xiang", "Juan", "Li", "Fang"]

USIA = [("Anak", "Child"), ("Muda", "Youth"), ("Dewasa", "Adult"), ("Tua", "Elder")]
KELAMIN = [("Laki-laki", "Male"), ("Perempuan", "Female")]
SUASANA = [("Netral", "Neutral"), ("Senang", "Happy"), ("Sedih", "Sad"), ("Marah", "Angry"), ("Takut", "Afraid"),
           ("Mengantuk", "Sleepy"), ("Malu", "Shy"), ("Bangga", "Proud")]
LUKA = [("Sehat", "Healthy"), ("Pincang", "Limping"), ("Lengan dibalut", "Bandaged arm"), ("Habis qi", "Drained of qi"),
        ("Terluka parah", "Badly hurt")]
BUSANA = [("Otomatis", "Automatic"), ("Jubah bepergian", "Travelling clothes"), ("Putih berkabung", "Mourning white"),
          ("Pakaian pesta", "Festival clothes"), ("Pakaian kerja", "Work clothes"), ("Pakaian tidur", "Sleeping clothes"),
          ("Baju zirah tugas", "Duty armour"), ("Penyamaran", "Disguise")]

# ------------------------------------------------------------------------------------------------ command messages
CMD = {
    "judul": ("Uji Penampilan (Sosok)", "Appearance Tests (Sosok)"),
    "intro": ("Murid dan NPC manusia: kelamin, usia, rupa, busana, klip animasi. Badan uji hilang sendiri setelah 10 menit.",
              "Disciples and human NPCs: sex, age, looks, clothes, animation clips. Test bodies vanish by themselves after 10 minutes."),
    "galeri.peran": ("Galeri peran halaman %s/%s: %s badan (%s rupa NPC seluruhnya; depan laki-laki, belakang perempuan).",
                     "Role gallery page %s/%s: %s bodies (%s NPC looks in all; men in front, women behind)."),
    "galeri.murid": ("Galeri murid: %s badan (pangkat 0-5, calon, saingan, bayangan, klon; satu Dao per kolom).",
                     "Disciple gallery: %s bodies (ranks 0-5, candidate, rival, phantom, clone; one Dao per column)."),
    "galeri.jalan": ("Galeri jalan hidup: %s badan (15 jalan dan murid tanpa jalan, laki-laki dan perempuan).",
                     "Life path gallery: %s bodies (15 paths and a pathless disciple, men and women)."),
    "galeri.usia": ("Galeri usia %s: %s badan (anak, muda, dewasa, tua; perhatikan skala, kepala anak, bungkuk tua).",
                    "Age gallery of %s: %s bodies (child, youth, adult, elder; mind the scale, the child's head, the elder's stoop)."),
    "galeri.dao": ("Galeri Dao: %s murid dalam (Inti Emas di depan, Jiwa Baru Lahir di belakang).",
                   "Dao gallery: %s inner disciples (Golden Core in front, Nascent Soul behind)."),
    "galeri.fisik": ("Galeri fisik langka: %s murid uji 1,5 blok di depan (lihat mata dan tanda dada).",
                     "Rare physique gallery: %s test disciples 1.5 blocks ahead (look at the eyes and chest marks)."),
    "acak": ("%s sosok acak dengan benih baru. Ulangi untuk wajah lain.", "%s random bodies with new seeds. Repeat for other faces."),
    "klip": ("Klip %s (%s) dimainkan oleh %s badan.", "Clip %s (%s) played by %s bodies."),
    "klip.tidak_ada": ("Klip tidak dikenal: %s", "Unknown clip: %s"),
    "klip.kelompok_tidak_ada": ("Kelompok klip tidak dikenal: %s (tempat, sikap, murid, fungsi, aktivitas)",
                                "Unknown clip group: %s (tempat, sikap, murid, fungsi, aktivitas)"),
    "klip.parade": ("Parade klip %s: %s klip bergiliran pada %s badan (papan nama = id dan nama klip).",
                    "Clip parade %s: %s clips in turn on %s bodies (the plate shows clip id and name)."),
    "klip.aktivitas": ("Aktivitas %s: %s murid uji, masing-masing satu aktivitas.", "Activities %s: %s test disciples, one activity each."),
    "jalan": ("%s badan berjalan bolak-balik 12 blok, satu per gaya jalan (papan nama = profil).",
              "%s bodies walking to and fro over 12 blocks, one per gait (the plate names the profile)."),
    "wajah": ("Pertunjukan wajah: setiap suasana, wajah habis qi, mata tertutup, lalu salam.",
              "Face show: every mood, the drained face, closed eyes, then a greeting."),
    "wajah.suasana": ("Suasana: %s", "Mood: %s"),
    "wajah.luka": ("Luka: %s", "Wound: %s"),
    "wajah.mata": ("Mata tertutup (napas meditasi)", "Eyes closed (meditation breath)"),
    "wajah.bicara": ("Berbicara", "Speaking"),
    "wajah.salam": ("Salam, Tuan Muda!", "Greetings, Young Master!"),
    "pegas": ("%s badan berlari mengelilingimu lalu melompat: lihat rambut, ekor kuda, lengan lebar, selendang.",
              "%s bodies run round you and jump: watch hair, ponytail, wide sleeves, shawl."),
    "murid": ("%s sekarang: %s", "%s now: %s"),
    "murid.tidak_ada": ("Tatap muridmu sendiri dalam 8 blok.", "Look at one of your own disciples within 8 blocks."),
    "benih": ("Badan benih %s rupa %s: %s", "Body with seed %s, look %s: %s"),
    "info.tidak_ada": ("Tatap murid atau NPC manusia dalam 8 blok.", "Look at a disciple or a human NPC within 8 blocks."),
    "info.judul": ("Sosok %s", "Sosok of %s"),
    "info.sosok": ("Kelamin %s, usia %s, postur %s, benih %s, skala %s", "Sex %s, age %s, build %s, seed %s, scale %s"),
    "info.gaya": ("Sifat %s / %s, jalan %s, luka %s, busana %s, suasana %s",
                  "Traits %s / %s, path %s, wound %s, outfit %s, mood %s"),
    "info.gerak": ("Rupa %s, klip %s, menatap entitas %s", "Look %s, clip %s, looking at entity %s"),
    "hapus": ("%s badan uji Sosok dihapus.", "%s Sosok test bodies removed."),
    "hanya_pemain": ("Hanya pemain yang bisa memakai perintah ini.", "Only a player can use this command."),
    "penuh": ("Badan uji sudah %s dari %s. Pakai \"hapus\" dulu.", "Test bodies at %s of %s. Use \"hapus\" first."),
    "nilai_salah": ("Nilai tidak dikenal: %s", "Unknown value: %s"),
    "ringkas": ("%s, %s, postur %s, %s, %s, %s, %s", "%s, %s, build %s, %s, %s, %s, %s"),
}


def read_ids(path, kind):
    """name -> id from the Java constants and put(CONST, "name"...) calls."""
    src = open(path, encoding="utf-8").read()
    consts = {m.group(1): int(m.group(2)) for m in re.finditer(r"public static final int ([A-Z_0-9]+) = (\d+);", src)}
    out = {}
    for m in re.finditer(r'put\(([A-Z_0-9]+), "([a-z_0-9]+)"', src):
        out[m.group(2)] = consts[m.group(1)]
    if not out:
        sys.exit("no %s ids read from %s" % (kind, path))
    return out


def main():
    klip_ids = read_ids(os.path.join(SOSOK, "GerakId.java"), "clip")
    prop_ids = read_ids(os.path.join(SOSOK, "PropId.java"), "prop")
    missing = sorted(set(klip_ids) - set(KLIP)) + sorted(set(prop_ids) - set(PROP))
    extra = sorted(set(KLIP) - set(klip_ids)) + sorted(set(PROP) - set(prop_ids))
    if missing or extra:
        sys.exit("names out of step with GerakId/PropId: missing %s, unknown %s" % (missing, extra))
    if len(NAMA_MURID_P) != 20 or len(NAMA_MURID_L) != 16 or len(NAMA_DIRI_L) != 24 or len(NAMA_DIRI_P) != 24:
        sys.exit("name list sizes changed")

    id_id, en_us = {}, {}

    def put(key, pair):
        id_id[key], en_us[key] = pair

    p = "kultivator.sosok."
    for name, cid in sorted(klip_ids.items(), key=lambda kv: kv[1]):
        put(p + "klip.%d" % cid, KLIP[name])
    for name, pid in sorted(prop_ids.items(), key=lambda kv: kv[1]):
        put(p + "prop.%d" % pid, PROP[name])
    for i, pair in enumerate(USIA):
        put(p + "usia.%d" % i, pair)
    for i, pair in enumerate(KELAMIN):
        put(p + "kelamin.%d" % i, pair)
    for i, pair in enumerate(SUASANA):
        put(p + "suasana.%d" % i, pair)
    for i, pair in enumerate(LUKA):
        put(p + "luka.%d" % i, pair)
    for i, pair in enumerate(BUSANA):
        put(p + "busana.%d" % i, pair)
    for key, pair in JALAN.items():
        put(p + "jalan." + key, pair)
    for key, pair in RUPA.items():
        put(p + "rupa." + key, pair)
    for key, pair in CMD.items():
        put(p + "cmd." + key, pair)
    for i, name in enumerate(NAMA_MURID_P):
        put("kultivator.disciples.name.%d" % (36 + i), (name, name))
    for i, name in enumerate(NAMA_MURID_L):
        put("kultivator.disciples.name.%d" % (56 + i), (name, name))
    for i, name in enumerate(NAMA_DIRI_L):
        put("kultivator.kota.nama_diri_l.%d" % i, (name, name))
    for i, name in enumerate(NAMA_DIRI_P):
        put("kultivator.kota.nama_diri_p.%d" % i, (name, name))

    assert list(id_id) == list(en_us)
    for lang, data in (("id_id", id_id), ("en_us", en_us)):
        path = os.path.join(OUT, "sosok.%s.json" % lang)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print("sosok lang: %d keys (%d clips, %d props)" % (len(id_id), len(klip_ids), len(prop_ids)))


if __name__ == "__main__":
    main()
