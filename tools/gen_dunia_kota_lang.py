"""Writes lang-src/dunia_kota.{id_id,en_us}.json (module K, DESIGN 34.5.1-34.5.4). Indonesian primary.
Run:  python tools/gen_dunia_kota_lang.py   (the JSON is generated: edit the texts here, never the JSON by hand)
"""
import json
import os
import re

LANG = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lang-src")


def write(fragment, entries):
    """entries: list of (key, id_text, en_text)"""
    keys = [k for k, _, _ in entries]
    dup = {k for k in keys if keys.count(k) > 1}
    if dup:
        raise SystemExit("duplicate keys: %s" % sorted(dup))
    for code, idx in (("id_id", 1), ("en_us", 2)):
        data = {e[0]: e[idx] for e in entries}
        path = os.path.join(LANG, "%s.%s.json" % (fragment, code))
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    # cross-check no key collides with another fragment
    taken = {}
    for name in os.listdir(LANG):
        if name.endswith(".id_id.json") and not name.startswith(fragment + "."):
            with open(os.path.join(LANG, name), encoding="utf-8") as f:
                for k in json.load(f):
                    taken[k] = name
    clash = [k for k in keys if k in taken]
    if clash:
        raise SystemExit("keys already in other fragments: %s" % [(k, taken[k]) for k in clash])
    print("%s: %d keys" % (fragment, len(entries)))

E = []


def add(key, id_text, en_text):
    E.append((key, id_text, en_text))


K = "kultivator.kota."

# ---------------------------------------------------------------- Sumber lines of the four features (34.5.1-34.5.4)
add(K + "kota.inspired",
    "Terinspirasi dari Battle Through the Heavens: Balai Lelang Mi Te di Kota Wu Tan, tempat Xiao Yan menjual pil lewat lelang, dan Wilayah Sudut Hitam yang tanpa hukum dengan pasar gelapnya; serta Kota Keluarga Shang di Reverend Insanity, kota dagang tempat para master Gu berjual beli. Tata kota, harga dan angkanya rancangan game.",
    "Inspired by Battle Through the Heavens: the Mi Te Auction House of Wu Tan City, where Xiao Yan sells pills at auction, and the lawless Black Corner Region with its black markets; and by the Shang Clan City of Reverend Insanity, a trading city where Gu Masters buy and sell. The town plan, prices and numbers are game design.")
add(K + "teh.inspired",
    "Terinspirasi dari kedai teh dan penginapan dalam kisah wuxia dan xianxia, tempat kabar dunia persilatan beredar; rancangan game.",
    "Inspired by the teahouses and inns of wuxia and xianxia tales, where the news of the martial world goes round; game design.")
add(K + "pengembara.source",
    "Sumber: Liaozhai Zhiyi, 'Pendeta Tao dari Gunung Lao': Wang Qi naik ke Gunung Lao mencari seorang pendeta Tao untuk belajar ilmu, lalu pulang hanya membawa sedikit dari yang ia minta. Terinspirasi dari kultivator lepas yang berkelana sendirian dalam A Record of a Mortal's Journey to Immortality. Diskusi Dao, tanding dan hadiahnya rancangan game.",
    "Source: Liaozhai Zhiyi, 'The Taoist of Mount Lao': Wang Qi climbs Mount Lao to learn the arts from a Taoist, and goes home with only a little of what he asked for. Inspired by the lone rogue cultivators who wander in A Record of a Mortal's Journey to Immortality. The Dao discussion, sparring and rewards are game design.")
add(K + "saingan.inspired",
    "Terinspirasi dari Battle Through the Heavens: perebutan Api Teratai Hijau di Gurun Tagore, tempat Xiao Yan berhadapan dengan Ratu Medusa dan kaum ular; dan dari perebutan pusaka yang baru lahir oleh banyak kultivator dalam cerita xianxia. Nama kelompok, dendam dan angkanya rancangan game.",
    "Inspired by Battle Through the Heavens: the struggle for the Green Lotus Core Flame in the Tagore Desert, where Xiao Yan faces Queen Medusa and the snake people; and by the many cultivators racing for a newborn treasure in xianxia stories. Group names, grudges and numbers are game design.")
add(K + "kota_abadi.source",
    "Sumber: Perjalanan ke Barat bab 4-5: Sun Wukong dibawa ke Istana Langit, diangkat menjadi pengurus kuda surgawi lalu penjaga Kebun Persik Abadi, dan memakan persik keabadian. Terinspirasi dari kota-kota para abadi di A Record of a Mortal's Journey to Immortality: Immortal Realm. Kota, kuliah dan angkanya rancangan game.",
    "Source: Journey to the West chapters 4-5: Sun Wukong is brought to the Heavenly Palace, made keeper of the heavenly horses and then guardian of the Garden of Immortal Peaches, and eats the peaches of immortality. Inspired by the immortals' cities of A Record of a Mortal's Journey to Immortality: Immortal Realm. The city, lectures and numbers are game design.")

# ---------------------------------------------------------------- names
add(K + "nama_kota", "Kota %s %s", "%2$s %1$s Town")
add(K + "nama_kota_abadi", "Kota Abadi %s %s", "Immortal City of the %2$s %1$s")
add(K + "nama_orang", "%s %s", "%s %s")
add(K + "papan_nama", "%s · %s", "%s · %s")
NAMES = [("Batu", "Stone"), ("Awan", "Cloud"), ("Angin", "Wind"), ("Gunung", "Mountain"), ("Sungai", "River"), ("Bambu", "Bamboo"),
         ("Teratai", "Lotus"), ("Pinus", "Pine"), ("Bulan", "Moon"), ("Bintang", "Star"), ("Embun", "Dew"), ("Kabut", "Mist"),
         ("Naga", "Dragon"), ("Feniks", "Phoenix"), ("Harimau", "Tiger"), ("Kura-kura", "Tortoise"), ("Pedang", "Sword"),
         ("Lonceng", "Bell"), ("Mata Air", "Spring"), ("Lembah", "Valley"), ("Tebing", "Cliff"), ("Danau", "Lake"),
         ("Pelangi", "Rainbow"), ("Salju", "Snow")]
SUFFIXES = [("Giok", "Jade"), ("Hijau", "Green"), ("Emas", "Golden"), ("Perak", "Silver"), ("Ungu", "Violet"), ("Merah", "Crimson"),
            ("Putih", "White"), ("Biru", "Azure")]
assert len(NAMES) == 24 and len(SUFFIXES) == 8
for i, (a, b) in enumerate(NAMES):
    add(K + "nama.%d" % i, a, b)
for i, (a, b) in enumerate(SUFFIXES):
    add(K + "akhiran.%d" % i, a, b)
SURNAMES = ["Lin", "Wang", "Zhang", "Li", "Chen", "Liu", "Zhao", "Huang", "Zhou", "Wu", "Xu", "Sun", "Ma", "Zhu", "Hu", "Guo", "He",
            "Gao", "Luo", "Song", "Xiao", "Han", "Mo", "Ye"]
GIVEN = ["Feng", "Yun", "Xue", "Ming", "Hao", "Jian", "Lan", "Mei", "Qing", "Rui", "Shan", "Tian", "Wei", "Xin", "Yao", "Zhen",
         "Bai", "Hong", "Jing", "Kai", "Ling", "Long", "Ning", "Ping", "Qiu", "Shu", "Tao", "Yan", "Yu", "Zi", "Hua", "Lei"]
assert len(SURNAMES) == 24 and len(GIVEN) == 32
for i, s in enumerate(SURNAMES):
    add(K + "marga.%d" % i, s, s)
for i, s in enumerate(GIVEN):
    add(K + "nama_diri.%d" % i, s, s)

ROLES = [
    ("juru_lelang", "Juru Lelang", "Auctioneer"),
    ("pemilik_teh", "Pemilik Kedai Teh", "Tea Master"),
    ("makelar_gelap", "Makelar Gelap", "Black-Market Broker"),
    ("pedagang_pil", "Pedagang Pil", "Pill Merchant"),
    ("pandai_pusaka", "Pandai Pusaka", "Treasure Smith"),
    ("penjaga", "Penjaga Kota", "Town Guard"),
    ("warga", "Warga Kota", "Townsfolk"),
    ("pengembara", "Kultivator Pengembara", "Wandering Cultivator"),
    ("saingan", "Kultivator Saingan", "Rival Cultivator"),
    ("abadi", "Penduduk Abadi", "Immortal Resident"),
    ("penceramah", "Penceramah Dao", "Dao Lecturer"),
    ("pedagang_abadi", "Pedagang Pasar Abadi", "Immortal Market Merchant"),
    ("juru_lelang_abadi", "Juru Lelang Abadi", "Immortal Auctioneer"),
    ("utusan", "Utusan Kaisar Giok", "Envoy of the Jade Emperor"),
    ("penjaga_kebun", "Penjaga Kebun Persik", "Keeper of the Peach Garden"),
]
for rid, a, b in ROLES:
    add(K + "peran." + rid, a, b)

# ---------------------------------------------------------------- arrival, spoken lines
add(K + "tiba", "Kamu tiba di %s. Tak ada pertumpahan darah di dalam tembok: Nama Baikmu di kota ini dicatat.",
    "You arrive at %s. No blood is spilled inside the walls: your Good Name in this town is kept.")
add(K + "tiba_abadi", "Awan berpisah: %s terhampar di depanmu, kota para abadi di atas lautan awan.",
    "The clouds part: %s lies before you, the city of immortals above a sea of clouds.")
WARGA = [
    ("Lelang senja nanti katanya ada pil langka. Semua orang membicarakannya.", "They say tonight's dusk auction has a rare pill. Everyone is talking about it."),
    ("Jangan berkelahi di sini. Para penjaga sekte tidak suka keributan.", "Don't fight here. The sect's guards don't like trouble."),
    ("Kedai teh di tepi kolam itu tahu segala kabar, asal kamu mau bayar.", "The teahouse by the pond knows every rumour, if you're willing to pay."),
    ("Toko peti mati di ujung jalan? Aku tak pernah lihat ada yang benar-benar membeli peti di sana.", "The coffin shop down the road? I've never seen anyone actually buy a coffin there."),
    ("Kakekku bilang dulu ada naga melintas di atas kota ini.", "My grandfather says a dragon once flew over this town."),
    ("Hasil panen bagus tahun ini. Qi di sekitar kota terasa lebih tebal.", "The harvest was good this year. The Qi around town feels thicker."),
    ("Kalau malam, tetaplah di jalan yang ada lenteranya.", "At night, keep to the streets with lanterns."),
    ("Para kultivator datang dan pergi, tapi kami warga biasa tetap di sini.", "Cultivators come and go, but us common folk stay."),
    ("Papan misi di alun-alun punya pekerjaan baru setiap hari.", "The notice board on the square has new work every day."),
    ("Dengar-dengar ada kelompok kultivator bertarung di luar kota kemarin.", "I hear a band of cultivators fought outside town yesterday."),
    ("Pedagang pil itu jujur, harganya saja yang tidak.", "The pill merchant is honest; his prices aren't."),
    ("Selamat datang, Tuan Kultivator. Semoga jalanmu lancar.", "Welcome, honoured cultivator. May your road be smooth."),
]
for i, (a, b) in enumerate(WARGA):
    add(K + "warga.kata.%d" % i, a, b)
SAINGAN_KATA = [
    ("Minggir. Urusan kami bukan denganmu.", "Step aside. Our business isn't with you."),
    ("Kau mencium bau harta juga? Lupakan saja.", "You smell the treasure too? Forget it."),
    ("Kelompok kami tak pernah lupa siapa yang menghalangi jalan.", "Our group never forgets who stood in our way."),
    ("Di dunia kultivasi, yang kuat makan yang lemah.", "In the world of cultivation, the strong devour the weak."),
    ("Hmph. Ranahmu lumayan… untuk sekarang.", "Hmph. Your realm is passable… for now."),
    ("Jangan sampai kita bertemu di dekat pusaka berikutnya.", "Pray we don't meet near the next treasure."),
]
for i, (a, b) in enumerate(SAINGAN_KATA):
    add(K + "saingan.kata.%d" % i, a, b)
ABADI_KATA = [
    ("Debu fana sampai juga ke sini? Langit memang luas.", "Mortal dust made it up here too? Heaven is wide indeed."),
    ("Seribu tahun di bawah sama dengan satu hari di atas awan.", "A thousand years below is a single day above the clouds."),
    ("Kuliah Dao di Paviliun Dao dimulai saat matahari di puncak.", "The Dao lecture in the Dao Pavilion begins when the sun is at its height."),
    ("Persik di kebun itu matang menurut waktunya sendiri, bukan waktumu.", "The peaches in that garden ripen in their own time, not yours."),
    ("Batu Asal lebih berharga daripada seribu batu roh.", "An Origin Stone is worth more than a thousand spirit stones."),
    ("Lonceng menara berbunyi saat tengah hari. Dengarkan, lalu diamlah.", "The tower bell rings at noon. Listen, then be still."),
    ("Utusan Kaisar Giok mencari orang yang berguna. Mungkin kamu.", "The Jade Emperor's envoy seeks someone useful. Perhaps you."),
    ("Jalan keabadian tidak berakhir di sini. Di sini ia baru dimulai.", "The road of immortality does not end here. Here it begins."),
]
for i, (a, b) in enumerate(ABADI_KATA):
    add(K + "abadi.kata.%d" % i, a, b)

# ---------------------------------------------------------------- Nama Baik and actions
add(K + "nama_baik.ditolak", "Nama Baikmu di kota ini terlalu buruk: mereka menolak melayanimu.", "Your Good Name in this town is too poor: they refuse to serve you.")
add(K + "nama_baik.turun", "Kamu menyerang warga kota: Nama Baik −%s (sekarang %s).", "You attacked a townsperson: Good Name −%s (now %s).")
add(K + "nama_baik.bunuh", "Darah tertumpah di dalam tembok kota: Nama Baik −%s (sekarang %s). Para penjaga mengingat wajahmu.",
    "Blood was spilled inside the town walls: Good Name −%s (now %s). The guards remember your face.")
add(K + "nama_baik.saksi", "Ada saksi yang melihatmu membunuh pengembara itu: Nama Baik −%s (sekarang %s).",
    "A witness saw you kill that wanderer: Good Name −%s (now %s).")
add(K + "aksi.jauh", "Terlalu jauh: dekati orangnya lagi.", "Too far away: walk back to them.")
add(K + "kurang_batu", "Batu rohmu tidak cukup: perlu %s.", "Not enough spirit stones: you need %s.")
add(K + "nb", "%s NB", "%s SV")

# ---------------------------------------------------------------- screens: shared
add(K + "layar.judul", "Kota Kultivator", "Cultivator Town")
add(K + "layar.judul_abadi", "Kota Abadi", "Immortal City")
add(K + "layar.judul_pertemuan", "Pertemuan di Jalan", "A Meeting on the Road")
add(K + "layar.dompet", "Batu: %s NB · Nama Baik %s", "Stones: %s SV · Good Name %s")
add(K + "layar.sep", "%s · %s", "%s · %s")
add(K + "layar.diskon", "diskon 10 %", "10 % discount")
add(K + "layar.razia", "RAZIA!", "RAID!")
add(K + "layar.kali", "%s ×%s", "%s ×%s")

# ---------------------------------------------------------------- Toko
add(K + "tab.toko", "Toko", "Shop")
add(K + "tab.toko.short", "Toko", "Shop")
add(K + "toko.judul.pil", "Paviliun Pil · %s", "Pill Pavilion · %s")
add(K + "toko.judul.pusaka", "Paviliun Pusaka · %s", "Treasure Pavilion · %s")
add(K + "toko.baris", "%s NB · sisa %s", "%s SV · %s left")
add(K + "toko.tip_beli", "Beli satu seharga %s NB (sisa %s hari ini). Kembaliannya dalam batu yang lebih rendah.",
    "Buy one for %s SV (%s left today). Change is given in lower grades.")
add(K + "toko.jual", "Jual barang di tangan", "Sell what you hold")
add(K + "toko.tip_jual", "Pedagang membeli 0,35-0,5 × harga dasar. Sisa jual hari ini: %s tumpukan.",
    "The merchant buys at 0.35-0.5 × the base price. Sales left today: %s stacks.")
add(K + "toko.kunjungi", "Pedagang Pil ada di Paviliun Pil dan Pandai Pusaka di Paviliun Pusaka: bicaralah dengan mereka untuk berbelanja.",
    "The Pill Merchant is in the Pill Pavilion and the Treasure Smith in the Treasure Pavilion: talk to them to shop.")
add(K + "toko.kosong", "Rak hari ini sudah kosong. Stok baru datang esok hari.", "The shelves are empty today. New stock comes tomorrow.")
add(K + "toko.tangan", "Di tanganmu: %s ×%s, dibeli %s NB.", "In your hand: %s ×%s, bought for %s SV.")
add(K + "toko.tangan_kosong", "Pegang barang yang ingin kamu jual.", "Hold the item you want to sell.")
add(K + "toko.habis", "Barang itu sudah habis.", "That is sold out.")
add(K + "toko.harga_berubah", "Harganya berubah; lihat lagi.", "The price changed; look again.")
add(K + "toko.dibeli", "Dibeli: %s seharga %s.", "Bought: %s for %s.")
add(K + "toko.dijual", "Dijual: %s × %s untuk %s.", "Sold: %s × %s for %s.")
add(K + "toko.tidak_beli", "Pedagang ini tidak membeli barang itu.", "This merchant does not buy that.")
add(K + "toko.batas_jual", "Pedagang hanya membeli %s tumpukan darimu per hari.", "The merchant only buys %s stacks from you per day.")

# ---------------------------------------------------------------- Lelang
add(K + "tab.lelang", "Balai Lelang", "Auction House")
add(K + "tab.lelang.short", "Lelang", "Auction")
add(K + "lelang.judul", "Balai Lelang · %s", "Auction House · %s")
add(K + "lelang.mati", "Lelang kota ditutup di pengaturan.", "Town auctions are off in the settings.")
add(K + "lelang.berjalan", "Sesi sedang berjalan!", "A session is running!")
add(K + "lelang.sudah_hari_ini", "Sesi hari ini sudah selesai. Datang lagi esok saat senja.", "Today's session is over. Come back tomorrow at dusk.")
add(K + "lelang.berikut", "Sesi berikutnya saat senja, dalam %s.", "The next session opens at dusk, in %s.")
add(K + "lelang.di_panggung", "juru lelang harus melihatmu (≤ 20 blok)", "the auctioneer must see you (≤ 20 blocks)")
add(K + "lelang.kosong", "Panggung masih kosong. Lima barang dipamerkan saat senja; empat penawar dari kelompok saingan ikut menawar.",
    "The stage is still empty. Five lots are shown at dusk; four bidders from the rival groups bid too.")
add(K + "lelang.harga", "Tawaran %s NB (dasar %s)", "Bid %s SV (base %s)")
add(K + "lelang.terjual", "Terjual kepada %s", "Sold to %s")
add(K + "lelang.tak_laku", "Tidak laku", "Unsold")
add(K + "lelang.dipamerkan", "Dipamerkan, dibuka dalam %s", "On show, opens in %s")
add(K + "lelang.memimpin", "%s memimpin · palu dalam %s", "%s leads · gavel in %s")
add(K + "lelang.dibuka", "Penawaran dibuka!", "Bidding is open!")
add(K + "lelang.menunggu", "Menunggu giliran", "Waiting its turn")
add(K + "lelang.penawar", "Para penawar", "The bidders")
add(K + "lelang.langkah", "+%s %% (%s)", "+%s %% (%s)")
add(K + "lelang.tip_tawar", "Tawar %s NB. Batumu: %s NB. Batu baru diambil bila kamu menang.",
    "Bid %s SV. Your stones: %s SV. Stones are taken only if you win.")
add(K + "lelang.titip", "Titipkan barang di tangan", "Consign what you hold")
add(K + "lelang.tip_titip", "Sampai %s barang per sesi; dibayar di akhir sesi 0,7-1,3 × nilainya (paling banyak separuh harga dasar lot).",
    "Up to %s items per session; paid at the end at 0.7-1.3 × their value (at most half a lot's base).")
add(K + "lelang.suara.buka", "Lelang malam ini dimulai!", "Tonight's auction begins!")
add(K + "lelang.suara.terjual", "Terjual!", "Sold!")
add(K + "lelang.mulai", "Lonceng berbunyi di Balai Lelang %s: lelang senja dimulai!", "The bell rings in the Auction House of %s: the dusk auction begins!")
add(K + "lelang.naik", "%s menawar %s!", "%s bids %s!")
add(K + "lelang.menang", "Kamu memenangkan %s seharga %s!", "You won %s for %s!")
add(K + "lelang.gagal_bayar", "Kamu menang, tetapi batumu tidak cukup: barang itu dibatalkan dan kamu tidak boleh menawar lagi di sesi ini.",
    "You won, but your stones fell short: the lot is cancelled and you may not bid again this session.")
add(K + "lelang.titipan_laku", "Titipanmu %s laku: kamu menerima %s.", "Your consigned %s sold: you receive %s.")
add(K + "lelang.selesai", "Lelang senja telah usai.", "The dusk auction is over.")
add(K + "lelang.tidak_ada", "Tidak ada barang yang sedang ditawar.", "No lot is open for bids.")
add(K + "lelang.usang", "Tawaranmu ketinggalan: harga sudah naik. Lihat lagi.", "Your bid is stale: the price already rose. Look again.")
add(K + "lelang.titip_penuh", "Kamu sudah menitipkan %s barang di sesi ini.", "You already consigned %s items this session.")
add(K + "lelang.titip_tolak", "Balai lelang tidak menerima titipan itu.", "The auction house does not accept that.")
add(K + "lelang.dititip", "%s dititipkan; dibayar di akhir sesi.", "%s consigned; paid at the end of the session.")
add(K + "lelang.utang_dibayar", "Balai lelang membayar titipanmu yang tertunda: %s.", "The auction house pays your pending consignment: %s.")
add(K + "lelang.seseorang", "Seseorang", "Someone")

# ---------------------------------------------------------------- Kedai Teh
add(K + "tab.teh", "Kedai Teh", "Teahouse")
add(K + "tab.teh.short", "Teh", "Tea")
add(K + "teh.judul", "Paviliun Angin Bambu", "Bamboo Wind Pavilion")
add(K + "teh.nama.embun", "Embun Pagi", "Morning Dew")
add(K + "teh.nama.awan", "Awan Ungu", "Purple Cloud")
add(K + "teh.nama.penenang", "Penenang Hati", "Heart's Ease")
add(K + "teh.efek.embun", "+%s %% XP saat bermeditasi selama 10 menit dalam game.", "+%s %% XP while meditating for 10 in-game minutes.")
add(K + "teh.efek.awan", "+%s %% pemahaman Dao selama 10 menit dalam game (Inti Emas ke atas).", "+%s %% Dao comprehension for 10 in-game minutes (Golden Core and up).")
add(K + "teh.efek.penenang", "Tekanan Iblis Hati naik 20 %% lebih lambat selama 10 menit dalam game (Jiwa Baru Lahir ke atas).", "Heart Demon pressure rises 20 %% slower for 10 in-game minutes (Nascent Soul and up).")
add(K + "teh.tip_harga", "Harga: %s NB. Hanya satu teh yang bekerja; teh baru menggantikan yang lama.", "Price: %s SV. Only one tea works at a time; a new one replaces the old.")
add(K + "teh.minum_tombol", "Minum", "Drink")
add(K + "teh.bungkus", "Bungkus", "To go")
add(K + "teh.tip_bungkus", "Bawa pulang secangkir (barang) untuk diminum nanti.", "Take a cup with you (an item) to drink later.")
add(K + "teh.harga", "%s NB secangkir", "%s SV a cup")
add(K + "teh.terkunci", "Untuk %s ke atas", "For %s and up")
add(K + "teh.aktif", "Teh %s bekerja, sisa %s", "%s tea at work, %s left")
add(K + "teh.tidak_aktif", "Tidak ada teh yang bekerja", "No tea at work")
add(K + "teh.pengembara", "%s pengembara duduk di kedai", "%s wanderers sit in the teahouse")
add(K + "teh.di_kedai", "Pesan teh dan kabar pada Pemilik Kedai Teh.", "Order tea and rumours from the Tea Master.")
add(K + "teh.token", "Token Pasar Gelap (%s NB)", "Black Market Token (%s SV)")
add(K + "teh.tip_token", "Pemilik kedai berbisik hanya kepada pelanggan setia: dengarkan 3 kabar dulu (sudah %s).",
    "The tea master only whispers to loyal guests: hear 3 rumours first (you heard %s).")
add(K + "teh.item.minutes", "Bekerja %s menit dalam game.", "Works for %s in-game minutes.")
add(K + "teh.minum", "Kamu meminum %s: khasiatnya bekerja %s menit dalam game.", "You drink %s: it works for %s in-game minutes.")
add(K + "teh.ranah", "Teh ini terlalu kuat untukmu: butuh %s.", "This tea is too strong for you: it needs %s.")
add(K + "teh.suara.sambut", "Silakan duduk, tehnya baru diseduh.", "Please sit, the tea is freshly brewed.")
add(K + "teh.token_belum", "Pemilik kedai menggeleng: \"Kita belum cukup saling kenal.\" (dengarkan %s kabar dulu)",
    "The tea master shakes his head: \"We don't know each other well enough yet.\" (hear %s rumours first)")
add(K + "teh.token_bisik", "Pemilik kedai menyelipkan sebuah token hitam ke tanganmu: \"Toko peti mati. Pintu belakang. Jangan bilang siapa-siapa.\"",
    "The tea master slips a black token into your hand: \"The coffin shop. The back door. Tell no one.\"")
add(K + "teh.token_tawar", "Pemilik kedai menatapmu lama: \"Kau sering datang… Kalau kau mencari barang yang tak dijual di rak, tanyakan token padaku.\"",
    "The tea master gives you a long look: \"You come often… If you seek goods no shelf sells, ask me for a token.\"")

add(K + "kabar.judul", "Kabar Angin: pilih jenis tempat, lalu jaraknya.", "Rumours: pick a kind of place, then the distance.")
add(K + "kabar.judul_tunggu", "Kabar Angin berikutnya dalam %s", "Next rumour in %s")
add(K + "kabar.tombol", "≤ %s blok · %s NB", "≤ %s blocks · %s SV")
add(K + "kabar.tip", "Menandai satu tempat yang belum kamu temukan di Peta Dunia. Bila langit akan melahirkan harta dalam sehari, pemilik kedai menceritakan arahnya.",
    "Marks one place you have not found on the World Map. If heaven will birth a treasure within a day, the tea master tells you its direction.")
for kid, a, b in [("kota", "Kota", "Town"), ("reruntuhan", "Gua Leluhur", "Ancestral Cave"), ("penjara", "Penjara Iblis", "Demon Prison"),
                  ("menara", "Menara", "Tower"), ("bawah", "Kuil Hantu", "Ghost Shrine"), ("laut", "Pusaran Laut", "Sea Whirlpool")]:
    add(K + "kabar.jenis." + kid, a, b)
add(K + "kabar.harta", "Pemilik kedai merendahkan suara: \"Langit di %s terasa aneh… kira-kira %s blok dari sini. Sesuatu akan lahir.\"",
    "The tea master lowers his voice: \"The sky to the %s feels strange… some %s blocks from here. Something is about to be born.\"")
add(K + "kabar.tunggu", "Pemilik kedai belum mendengar kabar baru. Coba lagi dalam %s detik.", "The tea master has no fresh news. Try again in %s seconds.")
add(K + "kabar.kosong", "Tak ada yang tahu %s di dekat sini. Batumu dikembalikan.", "Nobody knows of a %s nearby. Your stones are returned.")
add(K + "kabar.sudah", "Semua %s di dekat sini sudah kamu kenal. Batumu dikembalikan.", "You already know every %s nearby. Your stones are returned.")
add(K + "kabar.situs", "Kabar angin: ada %s di %s, sekitar %s blok (%s, %s). Tempat itu kini tertera di Peta Dunia.",
    "Rumour: there is a %s to the %s, about %s blocks away (%s, %s). It is now on your World Map.")
for i, (a, b) in enumerate([("utara", "north"), ("timur laut", "north-east"), ("timur", "east"), ("tenggara", "south-east"),
                            ("selatan", "south"), ("barat daya", "south-west"), ("barat", "west"), ("barat laut", "north-west")]):
    add(K + "arah.%d" % i, a, b)
CATATAN = [
    ("Catatan: \"Di bawah tebing yang menghadap matahari terbenam, batu-batu berbisik bila hujan.\"", "Note: \"Under the cliff that faces the sunset, the stones whisper when it rains.\""),
    ("Catatan: \"Aku pernah tersesat tiga hari di kabut Qi. Saat keluar, ranahku naik setingkat.\"", "Note: \"I was once lost in Qi mist for three days. When I came out, I had gained a stage.\""),
    ("Catatan: \"Jangan pernah menerima teh dari pengembara yang tersenyum terlalu lebar.\"", "Note: \"Never accept tea from a wanderer who smiles too widely.\""),
    ("Catatan: \"Pilar cahaya pelangi berarti harta lahir. Pilar cahaya pelangi juga berarti mayat.\"", "Note: \"A rainbow pillar means a treasure is born. A rainbow pillar also means corpses.\""),
    ("Catatan: \"Penjaga makam leluhur menguji kesabaran, bukan kekuatan.\"", "Note: \"The guardians of ancestral tombs test patience, not strength.\""),
    ("Catatan: \"Kota-kota abadi melayang di atas awan. Debu fana tak boleh dibawa naik.\"", "Note: \"The immortal cities float above the clouds. Mortal dust may not be carried up.\""),
    ("Catatan: \"Lao Shan mengajariku menembus dinding. Dinding pertama di rumahku membenturkan kepalaku.\"", "Note: \"Mount Lao taught me to pass through walls. The first wall at home bumped my head.\""),
    ("Catatan: \"Jalan yang paling jauh dimulai dari langkah di bawah kakimu.\"", "Note: \"The longest road begins with the step beneath your feet.\""),
]
for i, (a, b) in enumerate(CATATAN):
    add(K + "catatan.lore.%d" % i, a, b)

# ---------------------------------------------------------------- Kabar Dunia (the teahouse's news of the other modules' events)
add(K + "berita.kata", "Sudah dengar? %s", "Have you heard? %s")
add(K + "berita.langit.meteor_shower", "Hujan meteor roh turun malam ini; para pemburu batu langit berangkat ke padang.", "A spirit meteor shower falls tonight; the sky-stone hunters are heading for the plains.")
add(K + "berita.langit.blood_eclipse", "Bulan berdarah… Langit murka, para penjaga gelisah, dan harga di pasar gelap naik.", "A blood moon… heaven is angry, the guards are restless, and black-market prices rise.")
add(K + "berita.langit.twin_moons", "Dua bulan di langit! Para tetua bilang Qi malam ini lebih tebal dari biasanya.", "Two moons in the sky! The elders say tonight's Qi is thicker than usual.")
add(K + "berita.evolusi", "%s membawa %s melewati kesengsaraan binatang ke tingkat %s.", "%s led %s through a beast tribulation to tier %s.")
add(K + "berita.kapal", "Kapal roh %s berlabuh di %s.", "%s's spirit ship moored at %s.")
add(K + "berita.segel_pecah", "Segel penjara iblis kuno %s telah pecah! Jangan keluar kota sendirian.", "The seal of an ancient demon prison %s has broken! Don't leave town alone.")
add(K + "berita.segel_retak", "Segel penjara iblis kuno %s retak parah. Bisikan terdengar dari bawah tanah.", "The seal of an ancient demon prison %s is badly cracked. Whispers rise from under the ground.")
add(K + "berita.segel_lemah", "Segel penjara iblis kuno %s mulai melemah.", "The seal of an ancient demon prison %s is starting to weaken.")
add(K + "berita.segel_utuh", "Seseorang menyegel ulang penjara iblis kuno %s. Langit kembali tenang.", "Someone resealed an ancient demon prison %s. The sky is calm again.")
add(K + "berita.dekat", "dekat %s, %s", "near %s, %s")
add(K + "berita.entah", "entah di mana", "somewhere")
add(K + "berlabuh", "Kapalmu berlabuh di dekat %s.", "Your ship moors near %s.")

# ---------------------------------------------------------------- Pasar Gelap
add(K + "tab.gelap", "Pasar Gelap", "Black Market")
add(K + "tab.gelap.short", "Gelap", "Black")
add(K + "gelap.judul", "Pasar Gelap di Bawah Toko Peti", "The Market under the Coffin Shop")
add(K + "gelap.bisikan", "Ada bisikan tentang pasar di bawah toko peti mati… Makelarnya hanya melayani di sana, dan pintunya hanya terbuka bagi pemegang Token Pasar Gelap.",
    "There are whispers of a market under the coffin shop… Its broker only trades down there, and its door only opens for a holder of a Black Market Token.")
add(K + "gelap.baris", "%s NB", "%s SV")
add(K + "gelap.tip_beli", "Tanpa batas ranah, 1,5 × harga. Setiap pembelian punya peluang %s %% memancing razia penjaga.",
    "No realm gate, 1.5 × the price. Every purchase has a %s %% chance to bring a guard raid.")
add(K + "gelap.jual", "Jual barang jarahan", "Sell loot")
add(K + "gelap.suap", "Suap penjaga (%s NB)", "Bribe the guards (%s SV)")
add(K + "gelap.reputasi", "Reputasi Gelap %s · peluang razia %s %%", "Shadow Reputation %s · raid chance %s %%")
add(K + "gelap.razia_sisa", "Pintu dijaga, razia berakhir dalam %s", "The door is guarded, the raid ends in %s")
add(K + "gelap.tangan", "Di tanganmu: %s, makelar membayar %s NB.", "In your hand: %s, the broker pays %s SV.")
add(K + "gelap.pintu_dijaga", "Penjaga sekte berdiri di depan pintu: tidak sekarang.", "The sect's guards stand before the door: not now.")
add(K + "gelap.pintu_diam", "Rak peti ini terasa terlalu kokoh… seolah menyembunyikan sesuatu.", "This coffin rack feels too solid… as if it hides something.")
add(K + "gelap.pintu_buka", "Token di tanganmu menghangat. Rak peti bergeser tanpa suara.", "The token in your hand grows warm. The coffin rack slides aside without a sound.")
add(K + "gelap.ditolak", "Makelar tidak melayani orang yang dicari penjaga.", "The broker does not deal with someone the guards are hunting.")
add(K + "gelap.suara.bisik", "Barang bagus… untuk orang yang tahu diam.", "Fine goods… for those who know how to keep quiet.")
add(K + "gelap.suara.razia", "Berhenti! Kota ini dilindungi sekte.", "Halt! This town is under the sect's protection.")
add(K + "gelap.razia", "RAZIA! Penjaga sekte menyerbu pasar gelap dan memburumu. Bayar %s NB kepada seorang penjaga (klik kanan dia) atau bertahanlah 60 detik.",
    "RAID! The sect's guards storm the black market and hunt you. Pay %s SV to a guard (right-click one) or hold out for 60 seconds.")
add(K + "gelap.penjaga_razia", "Penjaga Razia", "Raid Guard")
add(K + "gelap.suap_tawar", "Penjaga melirik kantongmu: \"%s, dan aku tak pernah melihatmu.\" ", "The guard eyes your purse: \"%s, and I never saw you.\" ")
add(K + "gelap.tidak_razia", "Tidak ada razia yang perlu disuap.", "There is no raid to bribe your way out of.")
add(K + "gelap.suap_terima", "Penjaga mengantongi batumu dan berpaling. Razia selesai.", "The guard pockets your stones and looks away. The raid is over.")

# ---------------------------------------------------------------- Papan Misi
add(K + "tab.misi", "Papan Misi", "Notice Board")
add(K + "tab.misi.short", "Misi", "Bounties")
add(K + "misi.judul", "Papan Misi Kota", "The Town's Notice Board")
add(K + "misi.sisa", "Sisa misi hari ini: %s · Nama Baik %s", "Bounties left today: %s · Good Name %s")
add(K + "misi.mati", "Papan misi ditutup di pengaturan.", "The notice board is off in the settings.")
for mid, (ja, je, ia, ie) in {
    "buru": ("Buru Binatang Iblis", "Hunt a Demon Beast", "Seekor binatang iblis elit berkeliaran dekat kota. Bunuh dia.", "An elite demon beast roams near the town. Slay it."),
    "kemah": ("Bersihkan Kemah Bandit", "Clear a Bandit Camp", "Kultivator jahat berkemah di dekat jalan dagang. Usir mereka semua.", "Rogue cultivators camp by the trade road. Drive them all off."),
    "herba": ("Kirim Herba Tua", "Deliver an Aged Herb", "Tabib kota butuh herba roh yang cukup tua. Bawa satu ke papan.", "The town's healer needs a spirit herb old enough. Bring one to the board."),
    "kawal": ("Kawal Pedagang", "Escort a Merchant", "Seorang pedagang ingin sampai selamat ke kota atau desa terdekat.", "A merchant wants to reach the nearest town or village safely."),
}.items():
    add(K + "misi.jenis." + mid, ja, je)
    add(K + "misi.isi." + mid, ia, ie)
add(K + "misi.hadiah_baris", "Hadiah: %s NB · +5 Nama Baik", "Reward: %s SV · +5 Good Name")
add(K + "misi.aktif", "Misi berjalan: %s", "Bounty in progress: %s")
add(K + "misi.aktif_selesai", "Misi selesai: %s. Serahkan di papan!", "Bounty done: %s. Turn it in at the board!")
add(K + "misi.di_papan", "Misi diambil dan diserahkan pada Penjaga Kota di dekat papan.", "Bounties are taken and turned in with a Town Guard near the board.")
add(K + "misi.ambil", "Ambil", "Take")
add(K + "misi.serah", "Serahkan", "Turn in")
add(K + "misi.batal", "Batalkan", "Cancel")
add(K + "misi.sudah_ada", "Selesaikan atau batalkan misimu yang sekarang dulu.", "Finish or cancel your current bounty first.")
add(K + "misi.batas", "Kamu sudah mengambil %s misi hari ini, di semua kota.", "You already took %s bounties today, across all towns.")
add(K + "misi.sudah_diambil", "Misi itu sudah diambil orang lain.", "Someone else already took that bounty.")
add(K + "misi.tidak_bisa", "Misi itu tidak bisa dimulai di sini sekarang.", "That bounty cannot start here right now.")
add(K + "misi.buru_mulai", "Jejak binatang iblis mengarah ke %s, sekitar %s blok. Dia sudah menunggumu.",
    "The demon beast's tracks lead %s, about %s blocks away. It is waiting for you.")
add(K + "misi.buru_selesai", "Binatang iblis itu tumbang. Kembali ke papan misi untuk hadiahmu.", "The demon beast has fallen. Return to the notice board for your reward.")
add(K + "misi.kemah_mulai", "Kemah bandit dilaporkan di %s, di sekitar %s, %s.", "A bandit camp was reported to the %s, around %s, %s.")
add(K + "misi.kemah_selesai", "Kemah bandit bersih. Kembali ke papan misi untuk hadiahmu.", "The bandit camp is cleared. Return to the notice board for your reward.")
add(K + "misi.herba_mulai", "Bawa sebuah herba roh berumur %s tahun atau lebih ke papan misi.", "Bring a spirit herb aged %s years or more to the notice board.")
add(K + "misi.herba_kurang", "Herbamu belum cukup tua: perlu %s tahun.", "Your herb is not old enough: it needs %s years.")
add(K + "misi.kawal_mulai", "Kawal pedagang ini ke %s, sekitar %s blok. Jangan biarkan dia tertinggal atau mati.",
    "Escort this merchant to the %s, about %s blocks. Do not let them fall behind or die.")
add(K + "misi.kawal_tujuan_tidak_ada", "Tidak ada kota atau desa lain yang cukup dekat untuk dituju.", "There is no other town or village near enough to go to.")
add(K + "misi.kawal_selesai", "Pedagang tiba dengan selamat dan berterima kasih. Kembali ke papan misi untuk hadiahmu.",
    "The merchant arrived safely and thanks you. Return to the notice board for your reward.")
add(K + "misi.kawal_gagal", "Pedagang yang kamu kawal tewas. Misi gagal.", "The merchant you escorted died. The bounty failed.")
add(K + "misi.kawal_waktu", "Kamu terlalu lama: pedagang itu menyerah dan pulang. Misi gagal.", "You took too long: the merchant gave up and went home. The bounty failed.")
add(K + "misi.pedagang", "%s · Pedagang", "%s · Merchant")
add(K + "misi.belum", "Misimu belum selesai.", "Your bounty is not done yet.")
add(K + "misi.dibatalkan", "Misi dibatalkan.", "Bounty cancelled.")
add(K + "misi.hadiah", "Misi selesai! Hadiah %s, Nama Baik +%s (sekarang %s).", "Bounty complete! Reward %s, Good Name +%s (now %s).")

# ---------------------------------------------------------------- Pengembara and Diskusi Dao
add(K + "tab.pertemuan", "Pertemuan", "Meeting")
add(K + "tab.pertemuan.short", "Temu", "Meet")
add(K + "pengembara.papan", "%s · Pengembara %s", "%s · %s Wanderer")
for i, (a, b) in enumerate([("Ramah", "Friendly"), ("Angkuh", "Proud"), ("Licik", "Cunning")]):
    add(K + "watak.%d" % i, a, b)
add(K + "watak.isi.ramah", "senang berbagi dan jujur", "glad to share and honest")
add(K + "watak.isi.angkuh", "meremehkan yang lebih lemah", "looks down on the weaker")
add(K + "watak.isi.licik", "sering berbohong tentang niatnya", "often lies about their intent")
SAPA = {
    0: [("Salam, sesama penempuh jalan. Mau duduk sebentar?", "Greetings, fellow traveller of the road. Sit a while?"),
        ("Jarang bertemu orang di jalan sepi ini. Bagaimana kultivasimu?", "One rarely meets anyone on this quiet road. How goes your cultivation?"),
        ("Angin hari ini membawa Qi yang baik. Mari bertukar pandangan.", "Today's wind carries good Qi. Let us share our views.")],
    1: [("Hmph. Kau berani menghalangi jalanku?", "Hmph. You dare stand in my way?"),
        ("Ranahmu… lumayan untuk orang sepertimu.", "Your realm… passable for someone like you."),
        ("Kalau mau belajar dariku, tunjukkan dulu bahwa kau layak.", "If you want to learn from me, first show you are worthy.")],
    2: [("Ah, teman! Aku punya sesuatu yang mungkin kau suka…", "Ah, friend! I have something you might like…"),
        ("Jalan ini berbahaya. Lebih aman kalau kita berjalan bersama, bukan?", "This road is dangerous. Safer if we walk together, no?"),
        ("Hehe… kantongmu terlihat berat. Berat sekali.", "Heh… your purse looks heavy. Very heavy.")],
}
for t, lines in SAPA.items():
    for i, (a, b) in enumerate(lines):
        add(K + "pengembara.sapa.%d.%d" % (t, i), a, b)
add(K + "pengembara.bicara", "[Bicara]", "[Talk]")
add(K + "pengembara.bicara_tip", "Bicara dengan pengembara ini: bertukar pengetahuan, tanding, tantang, atau ajak berkelana.",
    "Talk to this wanderer: exchange knowledge, spar, challenge, or invite them along.")
add(K + "pengembara.suara.dao", "Jalan kita berbeda. Mari bertukar pandangan.", "Our roads differ. Let us share our views.")
add(K + "pengembara.tidak_ada", "Tidak ada pengembara yang menunggumu di dekat sini.", "No wanderer waits for you nearby.")
add(K + "pertemuan.judul", "Pertemuan dengan %s", "A meeting with %s")
add(K + "pertemuan.dao", "Dao %s · %s tingkat %s", "%s Dao · %s stage %s")
add(K + "pertemuan.tanpa_dao", "tanpa Dao", "no Dao")
add(K + "pertemuan.watak", "Watak: %s, %s", "Temper: %s, %s")
add(K + "pertemuan.diskusi_sisa", "Diskusi Dao tersisa hari ini: %s", "Dao discussions left today: %s")
add(K + "pertemuan.tanding", "Tanding", "Spar")
add(K + "pertemuan.tip_tanding", "Duel persahabatan: berhenti saat salah satu tinggal 10 % darah. Menang: 2 % XP batang, Lencana Pengembara, +3 Nama Baik. Kalah: tidak kehilangan apa-apa.",
    "A friendly duel: it stops when either side is at 10 % health. Win: 2 % of the bar as XP, a Wanderer's Badge, +3 Good Name. Lose: nothing lost.")
add(K + "pertemuan.tantang", "Tantang", "Challenge")
add(K + "pertemuan.tip_tantang", "Bertarung sampai mati. Ia menjadi musuhmu; bila mati ia menjatuhkan kantong jarahan. Bila ada saksi, Nama Baik kota terdekat turun 10.",
    "A fight to the death. They become your enemy; slain, they drop a loot pouch. If someone sees it, the nearest town's Good Name drops by 10.")
add(K + "pertemuan.ajak", "Ajak berkelana", "Travel together")
add(K + "pertemuan.tip_ajak", "Hanya pengembara yang ramah, untuk Inti Emas ke atas: ia mengikutimu satu hari dalam game, melawan monster, lalu pergi dengan sebuah hadiah kecil.",
    "Friendly wanderers only, for Golden Core and up: they follow you for one in-game day, fight monsters, then leave with a small gift.")
add(K + "pertemuan.pamit", "Pamit", "Farewell")
add(K + "tanding.mulai", "Tanding dimulai! Berhenti di 10 % darah.", "The sparring match begins! It stops at 10 % health.")
add(K + "tanding.menang", "Kamu mengalahkan %s dalam tanding. Ia menghormatimu dan memberimu Lencana Pengembara.", "You beat %s in the sparring match. They bow and give you a Wanderer's Badge.")
add(K + "tanding.kalah", "%s menang tanding. Kamu tidak kehilangan apa-apa, kecuali sedikit harga diri.", "%s won the sparring match. You lose nothing but a little pride.")
add(K + "tantang.mulai", "%s menerima tantanganmu. Niat membunuh memenuhi udara!", "%s accepts your challenge. Killing intent fills the air!")
add(K + "ajak.tolak", "Pengembara ini tidak mau berjalan bersamamu.", "This wanderer will not travel with you.")
add(K + "ajak.sudah", "Sudah ada pengembara yang berjalan bersamamu.", "A wanderer already travels with you.")
add(K + "ajak.ikut", "%s berjalan bersamamu selama satu hari.", "%s travels with you for a day.")
add(K + "ajak.pergi", "%s berpamitan dan meninggalkan hadiah kecil untukmu.", "%s says goodbye and leaves you a small gift.")
OFFERS = {
    "giok": ("Lempeng Giok", "Jade Slip", "Tawarkan sebuah lempeng giok sebagai bahan diskusi.", "Offer a jade slip to discuss.", "Kamu tidak membawa lempeng giok."),
    "herba": ("Herba", "Herb", "Tawarkan sebuah herba roh.", "Offer a spirit herb.", "Kamu tidak membawa herba roh."),
    "pil": ("Pil", "Pill", "Tawarkan sebuah pil.", "Offer a pill.", "Kamu tidak membawa pil."),
    "pengalaman": ("Pengalaman", "Experience", "Tawarkan pengalamanmu: 5 % batang kultivasimu sekarang.", "Offer your experience: 5 % of your current cultivation bar.", None),
}
ENO = {"giok": "You carry no jade slip.", "herba": "You carry no spirit herb.", "pil": "You carry no pill."}
for oid, (ia, ie, ta, te, missing) in OFFERS.items():
    add(K + "diskusi.beri." + oid, ia, ie)
    add(K + "diskusi.tip_beri." + oid, ta + " Barang kembali bila kamu kalah diskusi.", te + " It is returned if you lose the discussion.")
    if missing:
        add(K + "diskusi.tidak_punya." + oid, missing, ENO[oid])
add(K + "diskusi.tidak_punya.pengalaman", "Batang kultivasimu belum cukup terisi.", "Your cultivation bar is not full enough.")
add(K + "diskusi.kurang_pengalaman", "Batang kultivasimu belum cukup untuk dibagikan.", "Your cultivation bar is not full enough to share.")
add(K + "diskusi.batas", "Kamu sudah berdiskusi %s kali hari ini. Pikiranmu perlu istirahat.", "You already discussed %s times today. Your mind needs rest.")
for sid, (a, b, ta, tb) in {
    "gerak": ("Gerak", "Motion", "Gerak mengalahkan Diam.", "Motion beats Stillness."),
    "diam": ("Diam", "Stillness", "Diam mengalahkan Selaras.", "Stillness beats Harmony."),
    "selaras": ("Selaras", "Harmony", "Selaras mengalahkan Gerak.", "Harmony beats Motion."),
}.items():
    add(K + "diskusi.sikap." + sid, a, b)
    add(K + "diskusi.tip_sikap." + sid, ta + " Dengarkan petunjuknya: ke mana ia condong?", tb + " Listen to the hint: which way do they lean?")
add(K + "diskusi.ronde", "Ronde %s dari %s · menang %s", "Round %s of %s · %s won")
add(K + "diskusi.terakhir.menang", "Kamu unggul: ia memilih %s.", "You prevailed: they chose %s.")
add(K + "diskusi.terakhir.seri", "Seimbang: ia juga memilih %s.", "Even: they also chose %s.")
add(K + "diskusi.terakhir.kalah", "Ia unggul dengan %s.", "They prevailed with %s.")
add(K + "diskusi.hasil.paham", "Pandanganmu terbuka: +%s pemahaman %s.", "Your view opens: +%s %s comprehension.")
add(K + "diskusi.hasil.catatan", "Daonya bukan jalanmu, tetapi ia memberimu Catatan Pengembara.", "Their Dao is not your road, but they give you a Wanderer's Note.")
add(K + "diskusi.hasil.lore", "Kamu kalah diskusi dan barangmu dikembalikan. Ia berkata: %s", "You lost the discussion and your offer is returned. They say: %s")
add(K + "diskusi.ajak", "Setelah diskusi kalian bisa tanding, berpisah, atau berjalan bersama.", "After the discussion you may spar, part ways, or travel together.")
LORE = [
    ("\"Air tidak berebut, namun mengalahkan batu.\"", "\"Water does not contend, yet it wears down stone.\""),
    ("\"Orang yang tergesa naik gunung jatuh di lereng pertama.\"", "\"Who rushes up the mountain falls on the first slope.\""),
    ("\"Wang Qi ingin ilmu tanpa kerja. Ia pulang dengan benjol di kepala.\"", "\"Wang Qi wanted the arts without the work. He went home with a bump on his head.\""),
    ("\"Dao yang bisa diucapkan bukanlah Dao yang abadi.\"", "\"The Dao that can be spoken is not the eternal Dao.\""),
    ("\"Pedangmu tajam, tetapi hatimu masih tumpul.\"", "\"Your sword is sharp, but your heart is still dull.\""),
    ("\"Kembalilah saat kau mengerti mengapa kau kalah.\"", "\"Come back when you understand why you lost.\""),
]
for i, (a, b) in enumerate(LORE):
    add(K + "diskusi.lore.%d" % i, a, b)
# the hint lines per Dao: 0-1 lean Gerak, 2-3 lean Diam, 4-5 lean Selaras
HINTS = {
    "sword": [("\"Pedang yang tak pernah dihunus akan berkarat.\"", "\"A sword never drawn will rust.\""),
              ("\"Tebasan pertama memutuskan segalanya.\"", "\"The first cut decides everything.\""),
              ("\"Pedang terbaik tetap di sarungnya.\"", "\"The finest sword stays in its sheath.\""),
              ("\"Niat pedang lahir dari keheningan.\"", "\"Sword intent is born of silence.\""),
              ("\"Pedang dan pendekar harus menjadi satu.\"", "\"Sword and swordsman must become one.\""),
              ("\"Ikuti arah lawan, lalu tebas di celahnya.\"", "\"Follow the foe's line, then cut through its gap.\"")],
    "fire": [("\"Api yang tak bergerak akan padam.\"", "\"A fire that does not move dies out.\""),
             ("\"Nyala harus menjilat ke atas, selalu.\"", "\"A flame must leap upward, always.\""),
             ("\"Bara di bawah abu lebih panas dari nyala.\"", "\"The ember under the ash is hotter than the flame.\""),
             ("\"Tungku pil butuh api yang sabar.\"", "\"A pill furnace needs a patient fire.\""),
             ("\"Api dan kayu saling membutuhkan.\"", "\"Fire and wood need one another.\""),
             ("\"Api terbaik menghangatkan, bukan membakar.\"", "\"The best fire warms, it does not burn.\"")],
    "ice": [("\"Es yang retak melepaskan dingin sejati.\"", "\"Cracking ice releases true cold.\""),
            ("\"Badai salju tidak menunggu.\"", "\"A blizzard does not wait.\""),
            ("\"Danau beku menyimpan seribu musim.\"", "\"A frozen lake keeps a thousand seasons.\""),
            ("\"Dingin adalah ketenangan yang sempurna.\"", "\"Cold is perfect calm.\""),
            ("\"Es mencair menjadi air, air membeku menjadi es.\"", "\"Ice melts to water, water freezes to ice.\""),
            ("\"Salju jatuh mengikuti angin.\"", "\"Snow falls the way the wind goes.\"")],
    "lightning": [("\"Petir tidak pernah ragu.\"", "\"Lightning never hesitates.\""),
                  ("\"Yang tercepat menang sebelum lawan sadar.\"", "\"The swiftest wins before the foe knows it.\""),
                  ("\"Guntur berkumpul lama di dalam awan.\"", "\"Thunder gathers long inside the cloud.\""),
                  ("\"Sebelum kilat, langit menahan napas.\"", "\"Before the flash, heaven holds its breath.\""),
                  ("\"Yin dan yang bertemu, lahirlah petir.\"", "\"Yin and yang meet, and lightning is born.\""),
                  ("\"Petir mengikuti jalan yang paling mudah.\"", "\"Lightning follows the easiest path.\"")],
    "body": [("\"Otot tumbuh dari setiap pukulan.\"", "\"Muscle grows from every blow.\""),
             ("\"Tubuh yang berhenti berlatih akan melemah.\"", "\"A body that stops training weakens.\""),
             ("\"Gunung tidak bergerak, namun tak tergoyahkan.\"", "\"The mountain does not move, yet nothing shakes it.\""),
             ("\"Tahan pukulan seribu kali, baru kau mengerti.\"", "\"Endure a thousand blows, then you understand.\""),
             ("\"Napas dan tulang harus seirama.\"", "\"Breath and bone must keep one rhythm.\""),
             ("\"Tubuh yang lentur tak mudah patah.\"", "\"A supple body does not break easily.\"")],
    "life": [("\"Benih harus menembus tanah.\"", "\"A seed must break the soil.\""),
             ("\"Musim semi tak pernah meminta izin.\"", "\"Spring never asks permission.\""),
             ("\"Akar tumbuh dalam gelap dan diam.\"", "\"Roots grow in dark and stillness.\""),
             ("\"Pohon tua bijak karena sabar.\"", "\"The old tree is wise because it is patient.\""),
             ("\"Semua kehidupan saling memberi.\"", "\"All life gives to all life.\""),
             ("\"Hutan hidup karena berbagi cahaya.\"", "\"The forest lives by sharing the light.\"")],
    "shadow": [("\"Bayangan yang diam akan tertangkap.\"", "\"A shadow that stays still is caught.\""),
               ("\"Serang dari tempat yang tak terduga.\"", "\"Strike from where none expect.\""),
               ("\"Malam paling gelap sebelum fajar.\"", "\"The night is darkest before dawn.\""),
               ("\"Yang tak terlihat tak perlu bergerak.\"", "\"What is unseen need not move.\""),
               ("\"Bayangan ada karena cahaya.\"", "\"A shadow exists because of light.\""),
               ("\"Ikuti langkah lawan seperti bayangannya.\"", "\"Follow the foe's steps like their own shadow.\"")],
    "space": [("\"Ruang terlipat bagi yang berani melangkah.\"", "\"Space folds for those who dare to step.\""),
              ("\"Satu langkah, seribu li.\"", "\"One step, a thousand li.\""),
              ("\"Kekosongan menampung segalanya.\"", "\"Emptiness holds everything.\""),
              ("\"Titik pusat tidak pernah berpindah.\"", "\"The centre point never moves.\""),
              ("\"Ruang dan waktu adalah satu kain.\"", "\"Space and time are one cloth.\""),
              ("\"Benang-benang ruang saling menjalin.\"", "\"The threads of space weave together.\"")],
}
for dao, lines in HINTS.items():
    assert len(lines) == 6, dao
    for i, (a, b) in enumerate(lines):
        add(K + "diskusi.%s.%d" % (dao, i), a, b)

# ---------------------------------------------------------------- Saingan
GROUPS = [
    ("Keluarga Mo dari Lembah Hitam", "The Mo Clan of Black Valley"),
    ("Sekte Pedang Awan Biru", "Azure Cloud Sword Sect"),
    ("Paviliun Bulan Merah", "Crimson Moon Pavilion"),
    ("Klan Tie dari Utara", "The Tie Clan of the North"),
    ("Lembah Seribu Racun", "Valley of a Thousand Poisons"),
    ("Biksu Kuil Batu", "Monks of the Stone Temple"),
    ("Istana Salju Putih", "White Snow Palace"),
    ("Sekte Bayangan Darah", "Blood Shadow Sect"),
    ("Keluarga Gu dari Rawa Kabut", "The Gu Clan of Mist Marsh"),
    ("Klan Gunung Besi", "Iron Mountain Clan"),
    ("Sekte Kalajengking Hijau", "Green Scorpion Sect"),
    ("Paviliun Pengembara Emas", "Golden Wanderer Pavilion"),
]
for i, (a, b) in enumerate(GROUPS):
    add(K + "grup.%d" % i, a, b)
for sid, a, b in [("licik", "licik", "cunning"), ("angkuh", "angkuh", "proud"), ("pembunuh", "pembunuh bayaran", "assassins"),
                  ("tubuh", "pemurni tubuh", "body refiners"), ("racun", "ahli racun", "poison masters"), ("netral", "netral", "neutral")]:
    add(K + "gaya." + sid, a, b)
add(K + "saingan.dari", "%s dari %s", "%s of the %s")
add(K + "saingan.pemimpin", "%s · Pemimpin %s", "%s · Leader of the %s")
add(K + "saingan.anggota", "%s · %s", "%s · %s")
add(K + "saingan.suara.klaim", "Harta itu milik kami!", "That treasure is ours!")
add(K + "saingan.datang", "Cahaya pedang turun dari langit: %s datang memperebutkan harta!", "Sword light descends from the sky: the %s come to fight for the treasure!")
add(K + "saingan.klaim", "%s merebut harta itu lebih dulu darimu.", "The %s seized the treasure before you.")
add(K + "saingan.niat_membunuh", "Niat membunuh mengunci dirimu dari kejauhan…", "Killing intent locks onto you from afar…")
add(K + "saingan.sergap", "%s menyergapmu untuk merebut hartamu!", "The %s ambush you for your treasure!")
add(K + "saingan.kalah", "Pemimpin %s tumbang. \"Kami akan kembali…\"", "The leader of the %s falls. \"We will return…\"")
add(K + "saingan.semua", "Keenam pemimpin saingan telah kamu kalahkan. Namamu kini ditakuti di seluruh dunia kultivasi.",
    "You have beaten all six rival leaders. Your name is now feared across the cultivation world.")

# ---------------------------------------------------------------- Kota Abadi
add(K + "tab.pasar_abadi", "Pasar Abadi", "Immortal Market")
add(K + "tab.pasar_abadi.short", "Pasar", "Market")
add(K + "tab.kuliah", "Kuliah Dao", "Dao Lecture")
add(K + "tab.kuliah.short", "Kuliah", "Lecture")
add(K + "tab.persik", "Kebun Persik Abadi", "Garden of Immortal Peaches")
add(K + "tab.persik.short", "Persik", "Peach")
add(K + "tab.titah", "Titah Utusan", "The Envoy's Edict")
add(K + "tab.titah.short", "Titah", "Edict")
add(K + "abadi.dompet", "Batu Asal Abadi: %s", "Immortal Origin Stones: %s")
add(K + "abadi.fana", "debu fana: hanya yang telah naik bisa berbelanja", "mortal dust: only the ascended may trade")
add(K + "abadi.pasar_judul", "Pasar Abadi · %s", "Immortal Market · %s")
add(K + "abadi.ke_pasar", "Pedagang Pasar Abadi berjualan di pasar kota: bicaralah dengannya.", "The Immortal Market's merchant trades in the city's market: talk to them.")
add(K + "abadi.harga", "%s Batu Asal", "%s Origin Stones")
add(K + "abadi.langka", "Barang langka, %s Batu Asal. Batas tambang 16 sehari membelinya dalam sekitar seminggu.", "A rare ware, %s Origin Stones. The mining cap of 16 a day buys it in about a week.")
add(K + "abadi.biasa", "%s Batu Asal.", "%s Origin Stones.")
add(K + "abadi.kurang_asal", "Batu Asal Abadimu tidak cukup: perlu %s.", "Not enough Immortal Origin Stones: you need %s.")
add(K + "abadi.dibeli", "Dibeli: %s seharga %s Batu Asal.", "Bought: %s for %s Origin Stones.")
add(K + "kuliah.sudah", "Kamu sudah mendengar kuliah hari ini.", "You already heard today's lecture.")
add(K + "kuliah.sekarang", "Kuliah sedang berlangsung! Duduklah dekat penceramah.", "The lecture is on! Sit near the lecturer.")
add(K + "kuliah.nanti", "Kuliah berikutnya saat tengah hari, dalam %s.", "The next lecture is at noon, in %s.")
add(K + "kuliah.cara", "Duduk (jongkok atau bermeditasi) dalam 6 blok dari penceramah selama 60 detik saat tengah hari: +1-3 pemahaman Dao-mu, atau sebuah pencerahan bila kamu belum punya Dao. Sekali sehari.",
    "Sit (sneak or meditate) within 6 blocks of the lecturer for 60 seconds at noon: +1-3 comprehension of your Dao, or an enlightenment if you have none. Once a day.")
add(K + "kuliah.detik", "Didengar: %s dari %s detik", "Listened: %s of %s seconds")
add(K + "kuliah.suara", "Dengarkan. Dao tidak mengalir ke cangkir yang penuh.", "Listen. The Dao does not flow into a full cup.")
add(K + "kuliah.dengar", "Kamu mendengarkan kuliah Dao: %s / %s detik.", "You listen to the Dao lecture: %s / %s seconds.")
add(K + "kuliah.pencerahan", "Kata-kata penceramah menyalakan sesuatu di dalam dirimu: sebuah pencerahan!", "The lecturer's words kindle something within you: an enlightenment!")
add(K + "kuliah.paham", "Kuliah Dao selesai: pemahaman %s +%s.", "The Dao lecture ends: %s comprehension +%s.")
add(K + "persik.tombol", "Petik persik", "Pick a peach")
add(K + "persik.isi", "Penjaga kebun mengizinkan setiap abadi memetik satu persik tiap %s hari. Dimakan: 2 % batang di Kenaikan Abadi, 5 % di Abadi Sejati, dan separuh darahmu pulih.",
    "The garden's keeper lets every immortal pick one peach every %s days. Eaten: 2 % of the bar at Immortal Ascension, 5 % at True Immortal, and half your health heals.")
add(K + "persik.siap", "Persikmu siap dipetik.", "Your peach is ready to pick.")
add(K + "persik.tunggu", "Persik berikutnya dalam %s hari.", "Your next peach in %s days.")
add(K + "persik.ke_kebun", "Penjaga kebun ada di Kebun Persik Abadi.", "The keeper is in the Garden of Immortal Peaches.")
add(K + "persik.belum", "Persik belum matang untukmu: %s hari lagi.", "No peach is ripe for you yet: %s more days.")
add(K + "persik.petik", "Kamu memetik sebuah Persik Abadi. Wanginya saja sudah menenangkan jiwa.", "You pick an Immortal Peach. Its scent alone calms the soul.")
add(K + "persik.makan", "Manis dan abadi: +%s %% batang kultivasi.", "Sweet and everlasting: +%s %% of your cultivation bar.")
add(K + "titah.ambil", "Terima titah", "Accept the edict")
add(K + "titah.serah", "Laporkan", "Report")
add(K + "titah.kosong", "Utusan Kaisar Giok memberi satu titah setiap minggu dalam game. Terimalah untuk melihat tugasnya.",
    "The Jade Emperor's envoy gives one edict every in-game week. Accept it to see the task.")
for eid, (a, b, ia, ie) in {
    "batu": ("Persembahan Batu Asal", "An Offering of Origin Stones", "Persembahkan %s Batu Asal Abadi kepada Istana Giok.", "Offer %s Immortal Origin Stones to the Jade Palace."),
    "mutiara": ("Mutiara Esensi", "Essence Pearls", "Bawa %s Mutiara Esensi Abadi kepada utusan.", "Bring %s Immortal Essence Pearls to the envoy."),
    "purba": ("Tundukkan Makhluk Purba", "Subdue a Primordial", "Kalahkan %s makhluk purba atau penguasa di Alam Abadi.", "Defeat %s primordial beast or lord in the Immortal Realm."),
    "musuh": ("Bersihkan Jalan Awan", "Clear the Cloud Roads", "Kalahkan %s musuh di Alam Abadi.", "Defeat %s enemies in the Immortal Realm."),
}.items():
    add(K + "titah.jenis." + eid, a, b)
    add(K + "titah.isi." + eid, ia, ie)
add(K + "titah.dibayar", "Titah minggu ini sudah dibayar. Datang lagi minggu depan.", "This week's edict is paid. Come back next week.")
add(K + "titah.siap", "Tugas selesai! Laporkan untuk %s Batu Asal.", "Task done! Report it for %s Origin Stones.")
add(K + "titah.upah", "Upah: %s Batu Asal", "Pay: %s Origin Stones")
add(K + "titah.selesai", "Titah Utusan terpenuhi. Laporkan kepada utusan di Istana Giok.", "The envoy's edict is fulfilled. Report to the envoy in the Jade Palace.")
add(K + "titah.belum", "Titah ini belum terpenuhi.", "This edict is not fulfilled yet.")
add(K + "titah.hadiah", "Utusan mengangguk: \"Kaisar Giok berkenan.\" Kamu menerima %s Batu Asal Abadi.", "The envoy nods: \"The Jade Emperor is pleased.\" You receive %s Immortal Origin Stones.")

# ---------------------------------------------------------------- test commands
add(K + "uji.lelang_mulai", "Lelang uji dimulai pada juru lelang terdekat.", "A test auction started at the nearest auctioneer.")
add(K + "uji.lelang_tutup", "Semua lelang kota ditutup.", "Every town auction closed.")
add(K + "uji.jenis_tidak_dikenal", "Jenis tidak dikenal: %s", "Unknown kind: %s")
add(K + "uji.nama_baik", "Nama Baik di %s diatur ke %s.", "Good Name in %s set to %s.")
add(K + "uji.misi_baru", "Papan misi dan batas harianmu diatur ulang.", "Notice boards and your daily cap were reset.")
add(K + "uji.misi_tidak_ada", "Kamu tidak sedang menjalankan misi.", "You have no bounty in progress.")
add(K + "uji.misi_selesai", "Misimu ditandai selesai: serahkan di papan.", "Your bounty is marked done: turn it in at the board.")
add(K + "uji.stok_ulang", "Stok toko dan lelang semua kota diulang.", "Every town's shop and auction stock was reset.")
add(K + "uji.tidak_bisa", "Tidak bisa memunculkan NPC di sini (batas NPC atau tempat tidak aman).", "Cannot spawn an NPC here (NPC cap or no safe spot).")
add(K + "uji.npc", "Muncul: %s", "Spawned: %s")
add(K + "uji.tidak_ada", "Tidak ada %s dalam 48 blok.", "No %s within 48 blocks.")
add(K + "uji.pintu", "Pintu pasar gelap terdekat dibuka.", "The nearest black-market door is open.")
add(K + "uji.hapus", "%s pengembara dihapus.", "%s wanderers removed.")
add(K + "uji.saingan_kepala", "%s kelompok saingan · tingkat ranah %s · %s perlombaan berjalan · %s sergapan menunggu",
    "%s rival groups · realm band %s · %s races running · %s ambushes pending")
add(K + "uji.saingan_baris", "• %s: %s (%s), wilayah %s, %s · dendam %s · menang %s kalah %s · dikalahkan olehmu %s",
    "• %s: %s (%s), region %s, %s · grudge %s · wins %s losses %s · beaten by you %s")
add(K + "uji.dendam", "Dendam %s padamu diatur ke %s.", "The %s's grudge against you set to %s.")
add(K + "uji.abadi_saja", "Kota Abadi hanya ada di Alam Abadi.", "The Immortal City only exists in the Immortal Realm.")
add(K + "uji.titah", "Titah minggu ini ditandai selesai.", "This week's edict is marked done.")
add(K + "uji.saingan.tanpa_l", "(Modul Langit mati: perlombaan uji memakai titik harta tiruan.)", "(The sky module is off: the test race uses a stand-in treasure spot.)")

# ---------------------------------------------------------------- items, entities, advancements, hub, help
ITEMS = {
    "token_pasar_gelap": ("Token Pasar Gelap", "Black Market Token",
                          "Membuka pintu tersembunyi di toko peti mati. Dijual pemilik kedai teh kepada pelanggan setia.",
                          "Opens the hidden door of the coffin shop. Sold by the tea master to loyal guests.",
                          "inspired", "Terinspirasi dari pasar gelap Wilayah Sudut Hitam dalam Battle Through the Heavens; rancangan game.",
                          "Inspired by the black markets of the Black Corner Region in Battle Through the Heavens; game design."),
    "teh_embun_pagi": ("Teh Embun Pagi", "Morning Dew Tea",
                       "Klik kanan untuk minum: +10 % XP saat bermeditasi.", "Right-click to drink: +10 % XP while meditating.",
                       "inspired", "Terinspirasi dari budaya teh Tiongkok; Lu Yu menulis Kitab Teh (Cha Jing) pada masa Dinasti Tang. Khasiatnya rancangan game.",
                       "Inspired by Chinese tea culture; Lu Yu wrote the Classic of Tea (Cha Jing) in the Tang dynasty. Its effect is game design."),
    "teh_awan_ungu": ("Teh Awan Ungu", "Purple Cloud Tea",
                      "Klik kanan untuk minum: +10 % pemahaman Dao (Inti Emas ke atas).", "Right-click to drink: +10 % Dao comprehension (Golden Core and up).",
                      "inspired", "Terinspirasi dari legenda 'hawa ungu datang dari timur': penjaga Celah Hangu, Yin Xi, melihat awan ungu sebelum Laozi tiba. Khasiatnya rancangan game.",
                      "Inspired by the legend of 'purple air from the east': Yin Xi, keeper of the Hangu Pass, saw purple clouds before Laozi arrived. Its effect is game design."),
    "teh_penenang_hati": ("Teh Penenang Hati", "Heart's Ease Tea",
                          "Klik kanan untuk minum: tekanan Iblis Hati naik 20 % lebih lambat (Jiwa Baru Lahir ke atas).",
                          "Right-click to drink: Heart Demon pressure rises 20 % slower (Nascent Soul and up).",
                          "inspired", "Terinspirasi dari budaya teh Tiongkok dan cawan tanah liat Yixing; khasiatnya rancangan game.",
                          "Inspired by Chinese tea culture and Yixing clay cups; its effect is game design."),
    "kantong_saingan": ("Kantong Saingan", "Rival's Pouch",
                        "Kantong pemimpin saingan yang kalah. Klik kanan untuk membuka: dua gulungan jarahan dan sebuah batu roh.",
                        "A beaten rival leader's pouch. Right-click to open: two loot rolls and a spirit stone.",
                        "inspired", "Terinspirasi dari kebiasaan merampas kantong penyimpanan musuh yang kalah dalam A Record of a Mortal's Journey to Immortality; isinya rancangan game.",
                        "Inspired by the habit of taking a beaten foe's storage pouch in A Record of a Mortal's Journey to Immortality; its contents are game design."),
    "lencana_pengembara": ("Lencana Pengembara", "Wanderer's Badge",
                           "Tanda hormat dari pengembara yang kalah tanding denganmu.", "A token of respect from a wanderer you beat in sparring.",
                           "inspired", "Terinspirasi dari kultivator lepas yang berkelana dalam A Record of a Mortal's Journey to Immortality; rancangan game.",
                           "Inspired by the wandering rogue cultivators of A Record of a Mortal's Journey to Immortality; game design."),
    "persik_abadi": ("Persik Abadi", "Immortal Peach",
                     "Klik kanan untuk makan: 2 % batang di Kenaikan Abadi, 5 % di Abadi Sejati; separuh darah pulih.",
                     "Right-click to eat: 2 % of the bar at Immortal Ascension, 5 % at True Immortal; half your health heals.",
                     "source", "Sumber: Perjalanan ke Barat bab 5: Kebun Persik Abadi punya 3.600 pohon yang buahnya matang tiap 3.000, 6.000 dan 9.000 tahun dan memberi umur panjang; Sun Wukong yang menjaganya memakan persik itu. Khasiat dalam game rancangan game.",
                     "Source: Journey to the West chapter 5: the Garden of Immortal Peaches has 3,600 trees whose fruit ripens every 3,000, 6,000 and 9,000 years and grants long life; Sun Wukong, its guardian, eats the peaches. The in-game effect is game design."),
    "catatan_pengembara": ("Catatan Pengembara", "Wanderer's Note",
                           "Klik kanan untuk membaca: menandai satu tempat yang belum kamu temukan dalam 3.000 blok.",
                           "Right-click to read: marks one place you have not found within 3,000 blocks.",
                           "inspired", "Terinspirasi dari catatan perjalanan Xu Xiake, penjelajah Dinasti Ming; isinya rancangan game.",
                           "Inspired by the travel diaries of Xu Xiake, the Ming dynasty explorer; its contents are game design."),
}
for iid, (na, ne, ta, te, kind, sa, se) in ITEMS.items():
    add("item.kultivator." + iid, na, ne)
    add("item.kultivator." + iid + ".tooltip", ta, te)
    add("item.kultivator." + iid + "." + kind, sa, se)
add("entity.kultivator.kultivator_npc", "Kultivator", "Cultivator")
add("entity.kultivator.kota_pintu", "Pintu Tersembunyi", "Hidden Door")

ADV = {
    "kota_found": ("Kota Kultivator", "Cultivator Town", "Tiba di sebuah Kota Kultivator.", "Arrive at a Cultivator Town."),
    "kota_rumour": ("Kabar Angin", "Rumours on the Wind", "Membeli kabar di kedai teh.", "Buy a rumour in a teahouse."),
    "kota_auction": ("Palu Diketuk", "Going, Going, Gone", "Memenangkan sebuah lot di Balai Lelang kota.", "Win a lot in a town's Auction House."),
    "wanderer_talk": ("Bertukar Pandangan", "Sharing Views", "Berdiskusi Dao dengan seorang kultivator pengembara.", "Hold a Dao discussion with a wandering cultivator."),
    "rival_beaten": ("Yang Kuat Bertahan", "The Strong Endure", "Mengalahkan pemimpin sebuah kelompok saingan.", "Defeat the leader of a rival group."),
    "rival_all": ("Tak Tertandingi", "Unrivalled", "Mengalahkan pemimpin keenam kelompok saingan.", "Defeat the leaders of all six rival groups."),
    "immortal_city": ("Kota di Atas Awan", "City Above the Clouds", "Tiba di Kota Abadi di Alam Abadi.", "Arrive at the Immortal City in the Immortal Realm."),
}
for aid, (ta, te, da, de) in ADV.items():
    add("advancement.kultivator.dunia.%s.title" % aid, ta, te)
    add("advancement.kultivator.dunia.%s.description" % aid, da, de)

add("kultivator.hub.card.kota_abadi", "Kota Abadi", "Immortal City")
add("kultivator.hub.card.kota_abadi.tip", "Kota para abadi di Alam Abadi: pasar, kuliah, persik dan titah.", "The immortals' city in the Immortal Realm: market, lectures, peaches and edicts.")

HELP = {
    "kota": ("Kota Kultivator",
             "Kota Kultivator: kota bertembok yang lahir bersama dunia di chunk baru (sekitar 130 × 130 blok), dengan gerbang paifang, alun-alun, Balai Lelang, Kedai Teh Paviliun Angin Bambu, Paviliun Pil, Paviliun Pusaka, penginapan, kantor sekte penjaga dan sebuah toko peti mati. Penduduknya muncul saat kamu mendekat (paling banyak 10) dan pergi saat kamu menjauh. Klik kanan seorang warga untuk berdagang. Toko menjual barang di sekitar ranahmu dan membeli 0,35-0,5 × harga dasar (16 tumpukan sehari). Balai Lelang membuka sesi saat senja: lima lot, empat penawar dari kelompok saingan, tawar +5/+10/+25 %, batu diambil hanya bila menang. Kedai teh menjual tiga teh (satu yang bekerja) dan Kabar Angin yang menandai tempat di Peta Dunia. Setelah tiga kabar, pemilik kedai menjual Token Pasar Gelap: pintu di balik rak peti terbuka dan makelar menjual barang tanpa batas ranah dengan 1,5 × harga, tetapi tiap pembelian bisa memancing razia (bayar suap atau bertahan 60 detik). Papan Misi memberi 3 misi sehari (buru binatang, kemah bandit, herba tua, kawal pedagang). Nama Baik (−100 sampai 100) naik dengan misi dan lelang, turun saat kamu melukai warga; di bawah −30 penjaga memusuhimu, dari 50 kamu mendapat diskon 10 %. Kota tidak bisa dirusak Mode Kehancuran.",
             "Cultivator Town: a walled town born with the world in new chunks (about 130 × 130 blocks), with a paifang gate, a square, an Auction House, the Bamboo Wind Pavilion teahouse, a Pill Pavilion, a Treasure Pavilion, an inn, the guard sect's office and a coffin shop. Its people appear as you come near (at most 10) and leave when you go. Right-click a townsperson to trade. Shops sell goods around your realm and buy at 0.35-0.5 × the base price (16 stacks a day). The Auction House holds a session at dusk: five lots, four bidders from the rival groups, bid +5/+10/+25 %, stones taken only if you win. The teahouse sells three teas (one at work at a time) and Rumours that mark places on the World Map. After three rumours the tea master sells a Black Market Token: the door behind the coffin rack opens and the broker sells without a realm gate at 1.5 × the price, but every purchase may bring a raid (pay a bribe or hold out 60 seconds). The Notice Board gives 3 bounties a day (hunt a beast, a bandit camp, an aged herb, escort a merchant). Good Name (−100 to 100) rises with bounties and auctions and falls when you hurt townsfolk; below −30 the guards turn on you, from 50 on you get a 10 % discount. Mode Kehancuran cannot damage a town."),
    "pengembara": ("Kultivator Pengembara",
                   "Kultivator Pengembara: dari Pemurnian Qi, kadang seorang pengembara menghampirimu di luar ruangan (sekali sehari paling banyak), dan beberapa duduk di kedai teh. Ranahnya dekat dengan ranahmu; wataknya Ramah, Angkuh atau Licik. Klik [Bicara] di obrolan atau klik kanan dia. Bertukar Pengetahuan: tawarkan lempeng giok, herba, pil atau 5 % batangmu, lalu Diskusi Dao tiga ronde: Gerak mengalahkan Diam, Diam mengalahkan Selaras, Selaras mengalahkan Gerak, dan petunjuknya menunjukkan ke mana ia condong (yang Licik sering berbohong). Menang 2-3 ronde: +2-6 pemahaman Dao-nya bila itu Dao-mu, atau sebuah Catatan Pengembara. Kalah: sepatah nasihat dan barangmu kembali. Tiga diskusi sehari. Tanding: duel yang berhenti di 10 % darah; menang memberi 2 % XP batang, Lencana Pengembara dan Nama Baik. Tantang: ia menjadi musuhmu sampai mati. Ajak Berkelana (Ramah, Inti Emas ke atas): ia ikut satu hari dan melawan monster. Jurus area tidak pernah mengenai pengembara yang damai.",
                   "Wandering Cultivators: from Qi Refining on, a wanderer sometimes walks up to you outdoors (at most once a day), and some sit in teahouses. Their realm is close to yours; their temper Friendly, Proud or Cunning. Click [Talk] in chat or right-click them. Exchange Knowledge: offer a jade slip, a herb, a pill or 5 % of your bar, then a three-round Dao Discussion: Motion beats Stillness, Stillness beats Harmony, Harmony beats Motion, and their hint tells which way they lean (cunning ones often lie). Win 2-3 rounds: +2-6 comprehension of their Dao if it is yours, or a Wanderer's Note. Lose: a word of advice and your offer back. Three discussions a day. Spar: a duel that stops at 10 % health; winning gives 2 % of the bar as XP, a Wanderer's Badge and Good Name. Challenge: they become your enemy to the death. Travel Together (Friendly, Golden Core and up): they follow you for a day and fight monsters. Area techniques never hit a peaceful wanderer."),
    "saingan": ("Kultivator Saingan",
                "Kultivator Saingan: enam kelompok kultivator (keluarga, sekte, paviliun) hidup di dunia ini, masing-masing dengan pemimpin, anggota, wilayah dan gaya bertarung. Ranah mereka tumbuh bersama hari dunia, tetapi tidak pernah lebih dari satu ranah di atas pemain terkuat yang baru-baru ini bermain. Saat langit melahirkan harta, kelompok yang wilayahnya dekat ikut berlomba: bila kamu di dekat harta, mereka turun dari langit dengan pedang, saling bertarung, dan mencoba menyalurkan harta itu selama 5 detik. Mereka menyerang siapa pun yang mengklaim harta atau menyerang mereka. Setelah kamu merebut harta, kelompok yang dendam padamu (atau yang licik) bisa menyergapmu 1-5 menit kemudian di luar kota; kota selalu aman. Paling banyak satu sergapan sehari. Pemimpin yang kalah menjatuhkan Kantong Saingan dan dendamnya turun ke 20. Empat penawar di setiap Balai Lelang juga anggota kelompok ini.",
                "Rival Cultivators: six groups of cultivators (clans, sects, pavilions) live in this world, each with a leader, members, a region and a fighting style. Their realm grows with the world's days but never more than one realm above the strongest player who played lately. When heaven births a treasure, the groups whose region is near join the race: if you are near the treasure they descend from the sky on swords, fight each other and try to channel the treasure for 5 seconds. They attack anyone who claims the treasure or attacks them. After you take a treasure, a group that holds a grudge against you (or a cunning one) may ambush you 1-5 minutes later outside a town; towns are always safe. At most one ambush a day. A beaten leader drops a Rival's Pouch and their grudge falls to 20. The four bidders in every Auction House belong to these groups too."),
    "kota_abadi": ("Kota Abadi",
                   "Kota Abadi: sebuah distrik terapung di Alam Abadi (ketinggian 150-170), hanya untuk yang telah naik. Gerbang Awan, Pasar Abadi, Paviliun Dao, Kebun Persik Abadi, Menara Lonceng, Balai Lelang Abadi dan Istana Giok. Pasar Abadi menjual herba abadi, Pil Abadi, mutiara esensi dan kadang bahan purba dengan Batu Asal Abadi; batas tambang 16 sehari membeli barang langka dalam sekitar seminggu. Kuliah Dao saat tengah hari: duduk dalam 6 blok dari penceramah selama 60 detik untuk +1-3 pemahaman Dao-mu (sekali sehari). Persik Abadi tiap 3 hari: 2 % batang di Kenaikan Abadi, 5 % di Abadi Sejati. Juru lelangnya membuka Lelang Abadi yang sudah ada. Utusan Kaisar Giok memberi satu titah setiap minggu yang dibayar dengan Batu Asal. Lonceng menara berbunyi saat tengah hari.",
                   "Immortal City: a floating district in the Immortal Realm (height 150-170), for the ascended only. The Cloud Gate, the Immortal Market, the Dao Pavilion, the Garden of Immortal Peaches, the Bell Tower, the Immortal Auction House and the Jade Palace. The Immortal Market sells immortal herbs, Immortal Pills, essence pearls and sometimes primordial materials for Immortal Origin Stones; the mining cap of 16 a day buys a rare ware in about a week. The Dao Lecture at noon: sit within 6 blocks of the lecturer for 60 seconds for +1-3 comprehension of your Dao (once a day). An Immortal Peach every 3 days: 2 % of the bar at Immortal Ascension, 5 % at True Immortal. Its auctioneer opens the existing Immortal Auction. The Jade Emperor's envoy gives one edict a week, paid in Origin Stones. The tower's bell rings at noon."),
}
# ================================================================ detail pass (DESIGN 34.5.1-34.5.4): everyday life of the town
# Speech bubbles are one line above the name plate: keep every bubble line short (about 40 characters).


def lines(prefix, pairs):
    for i, (a, b) in enumerate(pairs):
        add(prefix + str(i), a, b)


lines(K + "abadi.cela.", [("Bau dunia fana masih melekat padamu.", "You still smell of the mortal world."),
                          ("Langkahmu berat. Belum Abadi Sejati, rupanya.", "Heavy steps. Not a True Immortal yet."),
                          ("Kota ini bukan untuk yang setengah jadi.", "This city is not for the half-finished."),
                          ("Hm. Pendatang lagi dari bawah awan.", "Hm. Another newcomer from below the clouds.")])
lines(K + "warga.hujan.", [("Hujan begini enak untuk tidur siang.", "Rain like this is good for a nap."),
                           ("Jemuranku basah lagi!", "My laundry got wet again!"),
                           ("Kata orang, hujan membawa qi dari gunung.", "They say rain brings qi down from the hills.")])
lines(K + "warga.pagi.", [("Pagi, Tuan! Pasar sudah buka.", "Morning! The market is open."),
                          ("Embun pagi paling bagus untuk teh.", "Morning dew makes the best tea."),
                          ("Sudah latihan pernapasan pagi ini?", "Done your morning breathing yet?")])
lines(K + "warga.siang.", [("Panas sekali hari ini.", "So hot today."),
                           ("Mi di kedai ujung jalan itu enak.", "The noodles down the street are good."),
                           ("Lelang nanti senja, jangan lupa.", "The auction is at dusk, don't forget.")])
lines(K + "warga.sore.", [("Sebentar lagi lentera dinyalakan.", "The lanterns will be lit soon."),
                          ("Balai Lelang mulai ramai.", "The auction hall is filling up."),
                          ("Pulang sebelum gelap, kata ibuku.", "Home before dark, my mother says.")])
lines(K + "warga.malam.", [("Larut begini masih berkeliaran?", "Still about at this hour?"),
                           ("Malam ini aku bermeditasi saja.", "Tonight I'll just meditate."),
                           ("Hati-hati, malam membawa binatang roh.", "Careful, night brings spirit beasts.")])
lines(K + "hormat.", [("Senior! Hormat saya.", "Senior! My respects."),
                      ("Mohon maaf mengganggu, Senior.", "Forgive the intrusion, Senior."),
                      ("Qi Senior begitu dalam...", "Senior's qi runs so deep..."),
                      ("Junior ini memberi hormat.", "This junior pays respect.")])
lines(K + "penjaga.hormat.", [("Hormat, Senior. Kota ini aman.", "Respects, Senior. The town is safe."),
                              ("Penjaga siap, Senior.", "The watch stands ready, Senior."),
                              ("Silakan lewat, Senior.", "Please pass, Senior."),
                              ("Sekte kami merasa terhormat.", "Our sect is honoured.")])
lines(K + "penjaga.halt.", [("Berhenti di situ!", "Halt right there!"),
                            ("Kau tidak diterima di kota ini.", "You are not welcome in this town."),
                            ("Satu langkah lagi, pedang bicara.", "One more step and the blade speaks.")])
lines(K + "penjaga.gerhana.", [("Gerhana Darah! Semua waspada!", "Blood Eclipse! Everyone on guard!"),
                               ("Jaga gerbang, awasi pasar gelap.", "Hold the gate, watch the black market."),
                               ("Iblis gelisah malam ini.", "Demons are restless tonight."),
                               ("Jangan ada yang keluar tembok!", "Nobody leaves the walls!")])
lines(K + "langit.blood_eclipse.", [("Bulan berdarah... pulang, cepat!", "A bleeding moon... home, quickly!"),
                                    ("Pertanda buruk! Kunci pintumu.", "An ill omen! Bar your door."),
                                    ("Kata nenek, iblis berpesta malam begini.", "Grandma says demons feast on such nights."),
                                    ("Langit memerah... aku takut.", "The sky turns red... I'm scared.")])
lines(K + "langit.meteor_shower.", [("Bintang jatuh! Cepat buat permohonan!", "Falling stars! Make a wish, quick!"),
                                    ("Batu meteor roh! Ada yang jatuh dekat?", "Spirit meteor stones! Any land near?"),
                                    ("Indahnya hujan bintang ini.", "What a beautiful star shower."),
                                    ("Para kultivator pasti memburu batunya.", "The cultivators will hunt those stones.")])
lines(K + "langit.twin_moons.", [("Dua bulan... Langit sedang bermimpi.", "Two moons... Heaven is dreaming."),
                                 ("Malam dua bulan, qi mengalir deras.", "Twin moons: the qi runs strong."),
                                 ("Aku tak percaya mataku sendiri.", "I can't believe my own eyes."),
                                 ("Ayo bermeditasi di bawah dua bulan!", "Let's meditate under the twin moons!")])
lines(K + "langit.harta.", [("Lihat, pilar cahaya di sana!", "Look, a pillar of light over there!"),
                            ("Harta lahir! Para sekte akan berebut.", "A treasure is born! The sects will fight."),
                            ("Cahaya pelangi... pusaka apa itu?", "Rainbow light... what treasure is that?"),
                            ("Jangan dekat-dekat, pasti ada darah.", "Stay away, there'll be bloodshed.")])
lines(K + "langit.reda.", [("Syukurlah, langit kembali tenang.", "Thank heavens, the sky is calm again."),
                           ("Sudah lewat. Ayo buka toko lagi.", "It's over. Let's reopen the shop."),
                           ("Malam yang panjang...", "What a long night...")])
TAWAR = {
    "pedagang_pil": [("Pil segar, baru keluar tungku!", "Fresh pills, straight from the furnace!"),
                     ("Pil Pondasi, harga jujur!", "Foundation Pills, honest prices!"),
                     ("Mampirlah, Tuan Kultivator!", "Do stop by, cultivator!")],
    "pandai_pusaka": [("Pedang tempaan, tajam sampai tulang!", "Forged blades, sharp to the bone!"),
                      ("Pusaka bagus untuk pendekar sejati!", "Fine treasures for true heroes!"),
                      ("Lihat dulu, tak perlu beli.", "Just look, no need to buy.")],
    "pedagang_abadi": [("Batu Asal Abadi diterima di sini.", "Immortal Origin Stones accepted here."),
                       ("Herba abadi, dipetik pagi ini.", "Immortal herbs, picked this morning."),
                       ("Barang langka untuk tamu langka.", "Rare goods for rare guests.")],
    "pemilik_teh": [("Teh hangat, kabar hangat!", "Hot tea, hot news!"),
                    ("Duduklah, istirahatkan kakimu.", "Sit, rest your feet."),
                    ("Teh Awan Ungu baru diseduh!", "Violet Cloud Tea, freshly brewed!")],
    "makelar_gelap": [("Psst... cari barang khusus?", "Psst... looking for something special?"),
                      ("Tak ada yang bertanya, tak ada yang tahu.", "No questions, no answers."),
                      ("Harga tergantung siapa yang bertanya.", "The price depends on who asks.")],
}
for role, pairs in TAWAR.items():
    lines(K + "tawar." + role + ".", pairs)
lines(K + "lelang.seru.", [("Penawaran berikutnya, siapa berani?", "Next bid, who dares?"),
                           ("Barang langka, jarang muncul!", "A rare piece, seldom seen!"),
                           ("Jangan biarkan pesaingmu menang!", "Don't let your rivals win!"),
                           ("Satu kali... dua kali...", "Going once... going twice..."),
                           ("Lihat kilaunya, rasakan qi-nya!", "See its gleam, feel its qi!"),
                           ("Hadirin, inilah kesempatan!", "Honoured guests, here is your chance!")])
lines(K + "kuliah.ajar.", [("Dao yang bisa dikatakan bukan Dao abadi.", "The Dao that can be told is not the eternal Dao."),
                           ("Air paling lembut, namun mengikis batu.", "Water is softest, yet wears away stone."),
                           ("Kosongkan cangkirmu sebelum menuang.", "Empty your cup before pouring."),
                           ("Yang abadi pun bermula dari satu napas.", "Even an immortal began with one breath."),
                           ("Diam bukan berhenti; diam adalah mendengar.", "Stillness is not stopping; it is listening."),
                           ("Langit dan bumi tidak memihak.", "Heaven and earth take no sides."),
                           ("Pahami dirimu, lalu pahami Langit.", "Know yourself, then know Heaven."),
                           ("Perjalanan seribu li dimulai dari langkah.", "A thousand li begin beneath one's feet."),
                           ("Pedang terbaik tak pernah keluar sarung.", "The finest sword never leaves its sheath."),
                           ("Cukup untuk hari ini. Renungkan.", "Enough for today. Reflect.")])
add(K + "kuliah.ajar.source",
    "Sumber: Daodejing (Laozi), bab 1, 5, 8 dan 64, diparafrasakan dalam kuliah; kalimat lain rancangan game.",
    "Source: the Daodejing (Laozi), chapters 1, 5, 8 and 64, paraphrased in the lecture; the other lines are game design.")
OBROL = {
    "harga": [("Harga pil naik lagi minggu ini.", "Pill prices went up again this week."),
              ("Herbanya diborong habis di lelang.", "The herbs were all bought at the auction."),
              ("Para kultivator itu tak pernah puas.", "Those cultivators are never satisfied."),
              ("Begitulah. Kita cuma menonton.", "Well. We little folk just watch.")],
    "lelang": [("Kau lihat lelang semalam?", "Did you see last night's auction?"),
               ("Inti binatang raja terjual mahal!", "A king beast core went for a fortune!"),
               ("Siapa pembelinya?", "Who bought it?"),
               ("Orang bertopeng. Tak ada yang tahu.", "Someone masked. Nobody knows.")],
    "sekte": [("Sekte penjaga menambah patroli.", "The guard sect added patrols."),
              ("Katanya ada perampok di hutan timur.", "They say bandits roam the east woods."),
              ("Semoga mereka cepat ditangkap.", "I hope they're caught soon."),
              ("Dengan pedang sekte, pasti.", "With the sect's swords, surely.")],
    "pengembara": [("Ada pengembara duduk di kedai teh.", "There's a wanderer in the teahouse."),
                   ("Yang memakai caping bambu itu?", "The one in the bamboo hat?"),
                   ("Ya. Katanya ia mencari lawan tanding.", "Yes. They say he seeks a sparring match."),
                   ("Aku sih tak berani mendekat.", "I wouldn't dare go near.")],
    "cuaca": [("Kabut roh tebal pagi tadi.", "The spirit mist was thick this morning."),
              ("Pantas napasku terasa ringan.", "No wonder my breath felt light."),
              ("Bagus untuk berlatih.", "Good for training."),
              ("Atau untuk tidur lebih lama!", "Or for sleeping in!")],
    "latihan": [("Lapisan ketiga Pemurnian Qi, akhirnya!", "Third layer of Qi Refining, at last!"),
                ("Selamat! Berapa lama?", "Congratulations! How long?"),
                ("Tiga tahun, setiap subuh.", "Three years, every dawn."),
                ("Kesabaranmu luar biasa.", "Your patience is remarkable.")],
    "gosip": [("Kau dengar soal toko peti mati itu?", "Heard about the coffin shop?"),
              ("Yang selalu sepi pembeli?", "The one that never has customers?"),
              ("Konon ada pintu rahasia di belakang.", "They say there's a secret door at the back."),
              ("Ssst! Jangan keras-keras.", "Shh! Not so loud.")],
    "makanan": [("Bakpao di penginapan masih hangat.", "The buns at the inn are still warm."),
                ("Isi daging atau kacang merah?", "Meat or red bean?"),
                ("Dua-duanya. Aku lapar.", "Both. I'm starving."),
                ("Belikan satu untukku juga.", "Get one for me too.")],
    "anak": [("Anakku ingin masuk sekte.", "My child wants to join a sect."),
             ("Akarnya bagus?", "Good roots?"),
             ("Kata tetua, akar roh tiga warna.", "The elder says three-colour spirit roots."),
             ("Itu sudah keberuntungan besar!", "That's great fortune already!")],
    "pil": [("Pil kemarin pahit sekali.", "Yesterday's pill was so bitter."),
            ("Pil yang pahit biasanya manjur.", "Bitter pills usually work."),
            ("Semoga. Harganya lima batu roh.", "Let's hope. It cost five spirit stones."),
            ("Lima? Kau dirampok!", "Five? You were robbed!")],
    "senja": [("Lentera mulai dinyalakan.", "The lanterns are being lit."),
              ("Sebentar lagi Balai Lelang buka.", "The auction hall opens soon."),
              ("Aku cuma mau lihat-lihat.", "I just want to look."),
              ("Semua orang bilang begitu.", "That's what everyone says.")],
    "langit_blood_eclipse": [("Langit merah... pertanda apa ini?", "The sky is red... what does it mean?"),
                             ("Gerhana Darah. Iblis menguat.", "A Blood Eclipse. Demons grow strong."),
                             ("Ayo pulang, tutup jendela.", "Let's go home and shut the windows."),
                             ("Jangan menatap bulan.", "Don't look at the moon.")],
    "langit_meteor_shower": [("Lihat! Bintang berjatuhan!", "Look! Stars are falling!"),
                             ("Hujan meteor roh, sekali seumur hidup.", "A spirit meteor shower, once in a lifetime."),
                             ("Aku berharap jadi kultivator.", "I wish to become a cultivator."),
                             ("Aku berharap harga pil turun.", "I wish pill prices would drop.")],
    "langit_twin_moons": [("Kau lihat? Dua bulan!", "Do you see? Two moons!"),
                          ("Kakekku pernah cerita soal ini.", "My grandfather told of this."),
                          ("Apa katanya?", "What did he say?"),
                          ("Malam ini qi mengalir lebih deras.", "Tonight the qi flows stronger.")],
    "abadi_dao": [("Seribu tahun kurenungkan Dao Pedang.", "A thousand years I pondered the Sword Dao."),
                  ("Dan hasilnya?", "And?"),
                  ("Aku baru mengerti satu tebasan.", "I understand a single cut, no more."),
                  ("Satu tebasan itu sudah cukup.", "One cut is enough.")],
    "abadi_persik": [("Persik di kebun matang lagi.", "The garden's peaches are ripe again."),
                     ("Sudah tiga ribu tahun sejak panen lalu?", "Three thousand years since the last harvest?"),
                     ("Waktu berlalu cepat di sini.", "Time flies up here."),
                     ("Jangan biarkan monyet mendekat.", "Keep the monkeys away.")],
    "abadi_fana": [("Ada yang baru naik dari dunia bawah.", "Someone new ascended from below."),
                   ("Masih bau debu, ya?", "Still smells of dust, hm?"),
                   ("Kita pun dulu begitu.", "We were the same once."),
                   ("Hm. Aku lupa rasanya.", "Hm. I forget what it was like.")],
    "abadi_kuliah": [("Kau hadir di kuliah tengah hari?", "Attending the noon lecture?"),
                     ("Tentu, penceramah itu bijak.", "Of course, the lecturer is wise."),
                     ("Kemarin ia bicara soal air.", "Yesterday he spoke of water."),
                     ("Lembut, namun mengikis batu.", "Soft, yet it wears down stone.")],
}
for topic, pairs in OBROL.items():
    lines(K + "obrol." + topic + ".", pairs)

# ---------------------------------------------------------------- the sword duel (DESIGN 35.7.6, module K's side)
for reason, (a, b) in {"sudah": ("Kita sudah beradu pedang. Jalan kita berpisah.", "We have crossed blades. Our paths part here."),
                       "rendah": ("Pedangku bukan untuk yang di bawahku.", "My blade is not for those beneath me."),
                       "nama": ("Buktikan dulu dirimu pada kota ini.", "Prove yourself to this town first."),
                       "razia": ("Tidak sekarang. Kau sedang diburu.", "Not now. You are being hunted."),
                       "sibuk": ("Aku sedang ada urusan.", "I have business elsewhere."),
                       "fana": ("Kembalilah setelah kau naik.", "Come back after you ascend.")}.items():
    add(K + "duel.tolak." + reason, a, b)
TERIMA = {
    "pengembara": [("Baik! Biar pedang kita yang bicara.", "Good! Let our blades talk."),
                   ("Sudah lama aku tak bertemu lawan.", "It's been long since I met a match."),
                   ("Cabut pedangmu. Jangan menahan diri.", "Draw. Hold nothing back.")],
    "saingan": [("Kau akan menyesal menantang kami.", "You'll regret challenging us."),
                ("Kelompok kami tak pernah mundur.", "Our band never backs down."),
                ("Bagus. Aku sudah lama menunggu.", "Good. I've waited long for this.")],
    "penjaga": [("Latihan dengan tamu kota? Senang hati.", "A bout with a guest? Gladly."),
                ("Jaga kuda-kudamu.", "Mind your stance."),
                ("Sekte kami mengajar dengan tombak.", "Our sect teaches with the spear.")],
    "abadi": [("Seribu tahun aku tak menghunus pedang.", "A thousand years since I drew my sword."),
              ("Tunjukkan Dao-mu.", "Show me your Dao."),
              ("Hm. Hiburan yang langka.", "Hm. A rare diversion.")],
}
for role, pairs in TERIMA.items():
    lines(K + "duel.terima." + role + ".", pairs)
lines(K + "duel.menang.", [("Masih kurang latihan.", "You need more training."),
                           ("Pedangmu jujur, tapi lambat.", "Your sword is honest, but slow."),
                           ("Kembalilah sepuluh tahun lagi.", "Come back in ten years."),
                           ("Pertandingan yang bagus.", "A good bout.")])
KALAH = {
    "pengembara": [("Aku kalah. Ilmumu tinggi!", "I yield. Your art is high!"), ("Hari ini aku belajar banyak.", "I learned much today.")],
    "saingan": [("Ini belum berakhir!", "This isn't over!"), ("Kelompokku akan mengingat namamu.", "My band will remember your name.")],
    "penjaga": [("Luar biasa! Kota beruntung punya tamu sepertimu.", "Remarkable! The town is lucky to have you."), ("Aku mengaku kalah.", "I concede.")],
    "abadi": [("Menarik... Dao-mu hidup.", "Interesting... your Dao lives."), ("Langit menilaimu dengan baik.", "Heaven judges you well.")],
}
for role, pairs in KALAH.items():
    lines(K + "duel.kalah." + role + ".", pairs)
lines(K + "duel.tawar.", [("Pedangmu menarik. Mau bertanding?", "An interesting blade. Shall we duel?"),
                          ("Tanganmu gatal? Tanganku juga.", "Itchy hands? Mine too."),
                          ("Pedang mengenal pedang lain.", "A sword knows another sword."),
                          ("Tunjukkan apa yang kau pelajari.", "Show me what you've learned.")])
add(K + "duel.hadiah.pengembara", "%s mengaku kalah: +2%% XP batang, Lencana Pengembara dan Nama Baik.",
    "%s concedes: +2%% of the bar as XP, a Wanderer's Badge and Good Name.")
add(K + "duel.hadiah.penjaga", "%s memberi hormat: Nama Baik kota kini %s.", "%s salutes you: the town's Good Name is now %s.")
add(K + "duel.hadiah.saingan", "%s mundur, tetapi dendam kelompoknya bertambah.", "%s falls back, but the band's grudge grows.")
add(K + "duel.hadiah.abadi", "%s menundukkan kepala di hadapanmu.", "%s bows the head before you.")
add(K + "duel.tantang", "Kau menantang %s berduel pedang. Ia menimbang...", "You challenge %s to a sword duel. They weigh it...")
for style, (a, b) in {"agresif": ("Pedang Tajam (menekan)", "Sharp Sword (pressing)"), "bertahan": ("Pedang Berat (sabar)", "Heavy Sword (patient)"),
                      "penipu": ("Pedang Lunak (menipu)", "Soft Sword (tricky)"),
                      "santo_pedang": ("Tanpa Pedang (santo pedang)", "No Sword (sword saint)")}.items():
    add(K + "gaya_pedang." + style, a, b)
add(K + "pertemuan.duel", "Duel Pedang", "Sword Duel")
add(K + "pertemuan.tip_duel",
    "Duel pedang resmi: bilah beradu, percikan api, tangkisan dan kunci bilah. Gayanya: %s. Ia menjawab 1-2 detik kemudian; menang memberi hadiah Tanding (sekali per pengembara).",
    "A formal sword duel: blades clash, sparks fly, parries and binds. Its style: %s. It answers 1-2 seconds later; a win pays the sparring reward (once per wanderer).")
add(K + "pertemuan.diskusi_gaya", "Diskusi tersisa hari ini: %s · Gaya pedang: %s", "Discussions left today: %s · Sword style: %s")

# ---------------------------------------------------------------- leftovers of wave 1
lines(K + "diskusi.puas.", [("Pandanganmu jernih!", "Your view is clear!"),
                            ("Kau paham lebih dari dugaanku.", "You understand more than I thought."),
                            ("Diskusi yang menyegarkan!", "A refreshing discussion!")])
add(K + "diskusi.jurus", "%s membagikan wawasan tentang %s: +%s poin penguasaan.", "%s shares insight into %s: +%s mastery points.")
add(K + "diskusi.hasil.jurus", "Wawasan tentang %s: +%s poin penguasaan jurus itu.", "Insight into %s: +%s mastery points in that technique.")
add(K + "persik.esensi", "Esensi Abadi +%s%% dari persik: kini %s%%.", "Immortal Essence +%s%% from the peach: now %s%%.")
add(K + "gelap.gerhana", "Gerhana Darah: penyelundup bersembunyi, setiap harga +%s%%.", "Blood Eclipse: the smugglers lie low, every price +%s%%.")

# ---------------------------------------------------------------- tests
add(K + "uji.barisan", "%s NPC berbaris di depanmu (satu per peran).", "%s NPCs line up before you (one per role).")
add(K + "uji.duel", "%s menawarkan duel pedang.", "%s offers a sword duel.")
add(K + "uji.gerak", "%s: gerak %s.", "%s: gesture %s.")
add(K + "uji.langit", "%s warga bereaksi.", "%s townsfolk react.")
add(K + "uji.siapa", "NPC", "NPC")

# ---------------------------------------------------------------- advancements of the detail pass
for aid, (ta, te, da, de) in {
    "wanderer_duel": ("Pedang Bertemu Pedang", "Blade Meets Blade", "Mengalahkan seorang kultivator pengembara dalam duel pedang.",
                      "Beat a wandering cultivator in a sword duel."),
    "wanderer_discussion": ("Tiga Kali Selaras", "Three Times in Accord", "Memenangi ketiga ronde Diskusi Dao.",
                            "Win all three rounds of a Dao Discussion."),
    "immortal_peach": ("Persik Keabadian", "Peach of Immortality", "Memakan Persik Abadi dari Kebun Persik.",
                       "Eat an Immortal Peach from the Peach Garden."),
    "immortal_duel": ("Pedang di Atas Awan", "A Blade Above the Clouds", "Mengalahkan seorang abadi asli dalam duel pedang.",
                      "Beat a native immortal in a sword duel."),
}.items():
    add("advancement.kultivator.dunia.%s.title" % aid, ta, te)
    add("advancement.kultivator.dunia.%s.description" % aid, da, de)

# ---------------------------------------------------------------- help texts: what the detail pass added
HELP_MORE = {
    "kota": ("Kota punya empat rupa menurut biomanya (dataran dan hutan, salju dan taiga, sabana, hutan ceri). Penduduknya hidup: senam pagi saat fajar, ke pasar siang hari, beristirahat saat tengah hari, pulang saat senja dan bermeditasi di rumah pada malam hari; dua warga yang berdekatan bisa mengobrol (gelembung kata di atas papan nama). Pedagang bekerja di balik meja dan memanggil orang lewat; penjaga berpatroli dari pos ke alun-alun, menghentikan yang dimusuhi kota dan memberi hormat kepada yang ranahnya jauh di atas mereka. Gerhana Darah membuat warga bergegas pulang; hujan meteor, dua bulan dan cahaya harta lahir membuat mereka menengadah. Dengan Nama Baik 20 ke atas penjaga mau beradu tombak denganmu dalam duel pedang. Saat Gerhana Darah, Pasar Gelap memberi tahu kenaikan harganya.",
             "Towns have four looks by biome (plains and forests, snow and taiga, savanna, cherry grove). Their people live: morning exercise at dawn, the market by day, a rest at noon, home at dusk and meditation at home at night; two townsfolk near each other may chat (speech bubbles above the name plates). Merchants work behind their counters and call passers-by over; guards walk from their post to the square, halt those the town is hostile to and salute those far above their realm. A Blood Eclipse sends the townsfolk hurrying home; a meteor shower, the twin moons and a newborn treasure's light make them look up. With Good Name 20 or more, a guard will cross spears with you in a sword duel. During a Blood Eclipse the Black Market shows its surcharge."),
    "pengembara": ("Duel Pedang: duel resmi dengan benturan bilah; gaya pedang pengembara mengikuti Dao dan wataknya (Dao Pedang dari Inti Emas: santo pedang). Pengembara yang menekan dan santo pedang kadang menawarkan duel kepada pemain yang memegang bilah. Satu kali beradu (Tanding atau Duel Pedang) per pengembara; yang kalah membayar hadiah Tanding. Diskusi yang dimenangi juga bisa memberi +10 poin penguasaan pada jurus Dao-nya yang sudah kamu kuasai (sekali per pengembara; diskusi sempurna memberinya bersama pemahaman).",
                   "Sword Duel: a formal duel with clashing blades; a wanderer's sword style follows its Dao and temper (the Sword Dao from Golden Core: a sword saint). Pressing wanderers and sword saints sometimes offer a duel to a player holding a blade. One bout (Spar or Sword Duel) per wanderer; a beaten wanderer pays the sparring reward. A won discussion can also give +10 mastery points in a technique of its Dao you know (once per wanderer; a flawless discussion gives it together with comprehension)."),
    "saingan": ("Anggota saingan membawa pedang (Klan Tie dari Utara: kapak) dan mau berduel pedang saat tidak sedang berlomba atau menyergap; mengalahkan mereka dalam duel menambah dendam kelompoknya.",
                "Rival members carry swords (the Tie Clan of the North: axes) and accept sword duels when not racing or ambushing; beating them in a duel raises their band's grudge."),
    "kota_abadi": ("Persik Abadi juga menambah +3 % Esensi Abadi bagi yang telah naik. Kuliah Dao dibacakan baris demi baris di atas kepala penceramah. Abadi asli mau berduel pedang hanya dengan sesama abadi. Sumber kuliah: Daodejing (Laozi), bab 1, 5, 8 dan 64, diparafrasakan.",
                   "An Immortal Peach also adds +3 % Immortal Essence for the ascended. The Dao lecture is spoken line by line above the lecturer's head. Native immortals duel only with fellow immortals. The lecture paraphrases the Daodejing (Laozi), chapters 1, 5, 8 and 64."),
}
for topic, (more_id, more_en) in HELP_MORE.items():
    title, ia, ie = HELP[topic]
    HELP[topic] = (title, ia + " " + more_id, ie + " " + more_en)

SUMBER_OF = {"kota": "kota.inspired", "pengembara": "pengembara.source", "saingan": "saingan.inspired", "kota_abadi": "kota_abadi.source"}
SRC = {k: (i, e) for k, i, e in E}
for topic, (title, ia, ie) in HELP.items():
    si, se = SRC[K + SUMBER_OF[topic]]
    add("kultivator.screen.help.%s.title" % topic, title, {"kota": "Cultivator Town", "pengembara": "Wandering Cultivators",
                                                           "saingan": "Rival Cultivators", "kota_abadi": "Immortal City"}[topic])
    add("kultivator.screen.help." + topic, ia + " " + si, ie + " " + se)


FMT = re.compile(r"%(?:(\d+)\$)?([A-Za-z%]|$)")


def fix_percent(text):
    """A lone % (not %s, %%, %1$s) becomes %% so Minecraft's TranslatableContents never falls back to the raw text."""
    out = []
    i = 0
    while i < len(text):
        m = FMT.match(text, i) if text[i] == "%" else None
        if m and m.group(2) in ("s", "%"):
            out.append(m.group(0))
            i = m.end()
        elif text[i] == "%":
            out.append("%%")
            i += 1
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def check(key, text):
    """Python port of TranslatableContents.decomposeTemplate: raises like Minecraft would."""
    current = 0
    while True:
        m = FMT.search(text, current)
        if not m:
            break
        if "%" in text[current:m.start()]:
            raise SystemExit("lone %% in %s: %r" % (key, text))
        if m.group(2) not in ("s", "%"):
            raise SystemExit("unsupported format in %s: %r" % (key, text))
        current = m.end()
        if m.end() == m.start():
            break
    if "%" in text[current:]:
        raise SystemExit("lone %% in %s: %r" % (key, text))


E = [(k, fix_percent(a), fix_percent(b)) for k, a, b in E]
for k, a, b in E:
    check(k, a)
    check(k, b)
    na = len(re.findall(r"%(?:\d+\$)?s", a))
    nb = len(re.findall(r"%(?:\d+\$)?s", b))
    if na != nb:
        raise SystemExit("argument count differs in %s: %d vs %d" % (k, na, nb))
write("dunia_kota", E)
