"""Writes lang-src/sekte_misi.{id_id,en_us}.json: every text of Sekte yang Hidup module X, Misi luar dan kepulangan
(DESIGN 36.2.7). Indonesian primary; both files get identical keys (the build's mergeLang checks it). Story texts take
the arguments %1$s leader, %2$s companion (the second member, else the leader), %3$s place, %4$s foe or the NPC sect met,
%5$s the village / missing disciple / sect the template names (else the foe)."""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
M = "kultivator.sekte.misi."
T = {}


def put(key, idn, en):
    if key in T:
        raise SystemExit("duplicate key " + key)
    T[key] = (idn, en)


# ============================================================================== the twelve templates
# id: (name id, name en, desc id, desc en, [(s1 ok id, s1 ok en), (s1 gagal id, s1 gagal en), ... s3 gagal])
TEMPLATES = {
    "kawal": ("Kawal Kafilah", "Escort the Caravan",
              "Kafilah pedagang meminta pengawal melewati celah gunung yang rawan perampok. Upahnya batu roh dan barang dagangan.",
              "A merchant caravan asks for guards through a mountain pass full of bandits. The pay is spirit stones and goods.",
              [("Di %3$s, %1$s membaca jejak roda dan memilih jalan memutar yang aman. Kafilah tidak kehilangan satu karung pun.",
                "At %3$s, %1$s read the wheel ruts and chose the safe way round. The caravan did not lose a single sack."),
               ("Di %3$s kabut turun terlalu cepat; dua gerobak terperosok dan tim kehilangan setengah hari untuk menariknya keluar.",
                "At %3$s the mist came down too fast; two carts slid into a ditch and the team lost half a day hauling them out."),
               ("Pada malam ketiga, %4$s menyergap perkemahan. %2$s menebas lebih dulu, dan para penyerang lari meninggalkan senjata mereka.",
                "On the third night %4$s fell on the camp. %2$s struck first, and the attackers fled, leaving their weapons behind."),
               ("Saat fajar, %4$s menyerbu. Tim bertahan, tetapi beberapa peti dagangan dibawa lari.",
                "At dawn %4$s attacked. The team held, but several crates of goods were carried off."),
               ("Di gerbang kota, kepala kafilah membungkuk dalam-dalam kepada %1$s dan menambah upah dengan tangannya sendiri.",
                "At the city gate the caravan master bowed deeply to %1$s and added to the pay with his own hands."),
               ("Kepala kafilah menghitung kerugian dan memotong upah. %1$s menahan amarah dan menerima apa yang diberikan.",
                "The caravan master counted his losses and cut the pay. %1$s swallowed the anger and took what was given.")]),
    "buru": ("Buru Binatang Iblis", "Hunt the Demonic Beast",
             "Seekor binatang iblis memangsa pengelana di jalan gunung. Inti binatangnya berharga, taringnya berbahaya.",
             "A demonic beast preys on travellers on the mountain road. Its core is precious, its fangs are deadly.",
             [("%1$s menemukan sarang %4$s di %3$s dari bau darah dan batang-batang pohon yang tercakar.",
               "%1$s found the lair of %4$s at %3$s by the smell of blood and the clawed tree trunks."),
              ("Tim berputar-putar di %3$s selama dua hari; jejak %4$s hilang di aliran sungai.",
               "The team circled %3$s for two days; the trail of %4$s vanished in a stream."),
              ("Pertarungan pertama pecah di tepi jurang. Formasi tiga sudut %1$s memaksa %4$s mundur dengan luka di bahu.",
               "The first fight broke out at a cliff edge. %1$s's three-point formation drove %4$s back with a wounded shoulder."),
              ("Ternyata %4$s lebih cepat dari dugaan. Serangannya membuyarkan formasi dan tim terpaksa mundur ke bebatuan.",
               "Yet %4$s was faster than anyone thought. Its charge broke the formation and the team fell back into the rocks."),
              ("Saat bulan naik, %2$s menusuk tepat ke titik vitalnya. Binatang itu roboh, dan intinya masih hangat bercahaya.",
               "As the moon rose, %2$s struck true at its vital point. The beast fell, its core still warm and glowing."),
              ("Binatang itu kabur ke kedalaman gunung sambil meraung. Tim pulang hanya membawa bekas cakarnya.",
               "The beast fled roaring into the deep mountains. The team came home with nothing but its claw marks.")]),
    "reruntuhan": ("Selidiki Reruntuhan", "Explore the Ruins",
                   "Tanah longsor membuka pintu sebuah gua kediaman kuno. Formasinya masih hidup, dan orang lain pun mengincarnya.",
                   "A landslide has opened the door of an ancient cave abode. Its formations still live, and others want it too.",
                   [("%1$s menemukan pintu batu di %3$s, setengah tertimbun lumut, dengan aksara segel yang masih berkilau samar.",
                     "%1$s found a stone door at %3$s, half buried in moss, its sealing script still faintly gleaming."),
                    ("Lorong pertama di %3$s runtuh di belakang tim. Mereka harus menggali jalan lain sepanjang malam.",
                     "The first tunnel at %3$s caved in behind the team. They had to dig another way all night."),
                    ("%2$s membaca pola formasi penjaga dan menemukan celahnya. Satu per satu, segel kuno terbuka tanpa memicu jebakan.",
                     "%2$s read the guardian formation's pattern and found its gap. One by one the old seals opened without springing a trap."),
                    ("Sebuah jebakan formasi menyala; asap racun memaksa tim mundur dan meninggalkan ruang pertama.",
                     "A formation trap flared; poison smoke drove the team out of the first chamber."),
                    ("Di ruang terdalam, %4$s sudah menunggu. %1$s memimpin serangan balik dan tim keluar membawa isi peti batu.",
                     "In the deepest chamber %4$s was already waiting. %1$s led the counterattack and the team came out with the stone chest's contents."),
                    ("Ruang terdalam sudah lebih dulu direbut %4$s. Tim hanya sempat memungut sisa-sisa di lorong.",
                     "The deepest chamber had already been taken by %4$s. The team only managed to pick up what was left in the tunnels.")]),
    "utusan": ("Utusan ke Sekte", "Envoy to a Sect",
               "Ketua menitipkan surat bersegel untuk sekte lain. Kata-kata yang tepat lebih berharga daripada seribu batu roh.",
               "The Sect Master entrusts a sealed letter for another sect. The right words are worth more than a thousand spirit stones.",
               [("Perjalanan ke gerbang %5$s melalui %3$s lancar; %1$s tiba tepat pada hari yang dijanjikan.",
                 "The road to the gate of %5$s through %3$s went smoothly; %1$s arrived on the very day promised."),
                ("Jalan ke %5$s terputus banjir di %3$s. Tim tiba terlambat tiga hari, dan penjaga gerbang mengernyit.",
                 "Floods at %3$s cut the road to %5$s. The team arrived three days late, and the gatekeepers frowned."),
                ("Di aula tamu, %1$s menyerahkan surat Ketua dengan kedua tangan dan menjawab setiap pertanyaan para tetua tanpa ragu.",
                 "In the guest hall %1$s presented the Sect Master's letter with both hands and answered every elder's question without faltering."),
                ("Seorang tetua %5$s menyindir sekte kita; %2$s membalas terlalu tajam dan suasana aula membeku.",
                 "An elder of %5$s mocked our sect; %2$s answered too sharply and the hall went cold."),
                ("Saat jamuan perpisahan, Ketua %5$s sendiri menuangkan arak untuk %1$s dan menitipkan salam hangat.",
                 "At the farewell banquet the master of %5$s poured wine for %1$s with his own hand and sent warm greetings."),
                ("Tim dipersilakan pulang tanpa jamuan. Surat balasan %5$s pendek dan dingin.",
                 "The team was sent home without a banquet. The reply of %5$s was short and cold.")]),
    "herba": ("Cari Herba Langka", "Find a Rare Herb",
              "Kabarnya sebatang herba berumur seribu tahun mekar di tempat terpencil. Yang menjaganya bukan manusia.",
              "Rumour says a thousand-year herb has bloomed in a remote place. What guards it is not human.",
              [("%1$s mengikuti aroma obat yang samar hingga ke %3$s, tempat embun pagi berwarna kehijauan.",
                "%1$s followed a faint medicinal scent all the way to %3$s, where the morning dew is tinged green."),
               ("Peta herba itu ternyata salah. Tim menyisir %3$s seharian tanpa hasil.",
                "The herb map was wrong. The team combed %3$s all day for nothing."),
               ("%2$s menemukan herba itu di celah tebing: daunnya bergaris emas, akarnya memeluk batu roh.",
                "%2$s found the herb in a cleft of the cliff: gold-veined leaves, roots clasping a spirit stone."),
               ("Herba itu sudah dipetik orang lain; yang tersisa hanya tunas muda dan jejak kaki asing.",
                "Someone had picked the herb already; only a young shoot and strange footprints were left."),
               ("Penjaga herba itu, %4$s, terbangun dan menyerang. Tim menahannya cukup lama hingga %1$s mencabut herba itu dengan akar utuh.",
                "The herb's guardian, %4$s, woke and attacked. The team held it long enough for %1$s to pull the herb out roots and all."),
               ("Penjaga herba itu, %4$s, mengamuk. Tim mundur dengan tangan kosong, hanya membawa beberapa daun yang rontok.",
                "The herb's guardian, %4$s, went wild. The team withdrew empty-handed with only a few fallen leaves.")]),
    "desa": ("Tolong Desa", "Aid a Village",
             "Kepala desa datang ke gerbang sekte memohon bantuan. Rakyat yang dilindungi hari ini adalah murid sekte esok hari.",
             "A village headman comes to the sect gate begging for help. The people protected today are the disciples of tomorrow.",
             [("%1$s tiba di %5$s sebelum senja dan mendengar keluhan warga: ternak hilang, dan anak-anak takut keluar rumah.",
               "%1$s reached %5$s before dusk and heard the villagers out: livestock gone, and children afraid to leave their homes."),
              ("Tim tersesat di %3$s dan baru tiba di %5$s keesokan siangnya; warga sudah setengah putus asa.",
               "The team got lost at %3$s and only reached %5$s the next noon; the villagers had half given up."),
              ("Malam itu %4$s datang lagi. Kali ini %2$s menunggu di lumbung, dan pengganggu itu tidak pernah kembali.",
               "That night %4$s came again. This time %2$s was waiting in the granary, and it never came back."),
              ("Kali ini %4$s datang terlalu banyak. Tim melindungi rumah-rumah, tetapi lumbung desa terbakar sebagian.",
               "This time %4$s came in too great a number. The team saved the houses, but part of the village granary burned."),
              ("Warga %5$s mengantar tim sampai batas desa. Beberapa pemuda memandang jubah sekte dengan mata berbinar.",
               "The people of %5$s walked the team to the edge of the village. Some of the youths stared at the sect robes with shining eyes."),
              ("Warga %5$s berterima kasih seadanya. Kepala desa diam-diam bertanya apakah sekte masih sanggup melindungi mereka.",
               "The people of %5$s gave thin thanks. The headman quietly asked whether the sect could still protect them.")]),
    "pusaka": ("Rebut Pusaka", "Seize the Treasure",
               "Cahaya pusaka lahir dari dalam bumi. Setiap sekte di sekitar sudah mengirim orang; yang lambat hanya akan melihat debu.",
               "A treasure's light has risen from the earth. Every sect nearby has sent its people; the slow will see only dust.",
               [("%1$s menyebarkan kabar palsu tentang letak pusaka; separuh pemburu di %3$s bergegas ke arah yang salah.",
                 "%1$s spread a false word about where the treasure lay; half the hunters at %3$s rushed the wrong way."),
                ("Tipuan tim terbongkar. %4$s kini mengawasi setiap gerakan mereka di %3$s.",
                 "The team's ruse was found out. %4$s now watched their every move at %3$s."),
                ("Di kaki pilar cahaya, tim berhadapan dengan %4$s. %2$s menahan tiga lawan sekaligus sementara yang lain maju.",
                 "At the foot of the pillar of light the team faced %4$s. %2$s held off three at once while the others pushed forward."),
                ("Tiba-tiba %4$s menyerang dari dua sisi. Tim tercerai-berai dan kehilangan posisi terbaik.",
                 "Suddenly %4$s struck from two sides. The team was scattered and lost the best ground."),
                ("Saat cahaya meredup, tangan %1$s yang pertama menyentuh pusaka itu. Tim mundur dalam formasi rapat tanpa bisa dikejar.",
                 "As the light faded, %1$s's hand was the first to touch the treasure. The team withdrew in tight formation and could not be followed."),
                ("Pada akhirnya %4$s yang membawa pusaka itu pergi. Tim hanya memungut pecahan batu yang masih hangat.",
                 "In the end it was %4$s who carried the treasure off. The team picked up only shards of still-warm stone.")]),
    "cari": ("Cari Murid yang Pergi", "Find the Lost Disciple",
             "Seorang murid meninggalkan sekte dan mengembara. Kabar terakhir menyebut ia terlihat di jalan-jalan jauh. Mungkin hatinya masih bisa dipanggil pulang.",
             "A disciple left the sect to wander. The last word says it was seen on far roads. Perhaps its heart can still be called home.",
             [("Seorang penjual teh di %3$s mengenali gambaran %5$s dan menunjuk jalan yang ia ambil.",
               "A tea seller at %3$s recognised the description of %5$s and pointed out the road it took."),
              ("Jejak %5$s di %3$s sudah dingin; orang-orang hanya ingat seorang pengelana yang tidak banyak bicara.",
               "The trail of %5$s at %3$s had gone cold; people only remembered a quiet traveller."),
              ("%1$s menemukan %5$s duduk sendirian di bawah pohon tua. Mereka berbicara sampai api unggun padam.",
               "%1$s found %5$s sitting alone under an old tree. They talked until the campfire died."),
              ("%5$s melihat jubah sekte dari jauh dan menghilang di keramaian pasar sebelum %1$s sempat memanggil.",
               "%5$s saw the sect robes from afar and vanished into the market crowd before %1$s could call out."),
              ("%2$s menyampaikan pesan Ketua kata demi kata. %5$s lama terdiam, lalu mengencangkan tali bundelnya.",
               "%2$s delivered the Sect Master's message word for word. %5$s was silent for a long time, then tightened the cord of its bundle."),
              ("\"Katakan pada Guru, aku belum siap,\" kata %5$s. Tim pulang hanya membawa kabar.",
               "\"Tell the Master I am not ready,\" said %5$s. The team came home with nothing but news.")]),
    "perbatasan": ("Jaga Perbatasan", "Guard the Border",
                   "Penyusup terlihat di tepi wilayah sekte. Batas yang tidak dijaga akan diinjak-injak.",
                   "Intruders have been seen at the edge of the sect's land. A border nobody guards will be trampled.",
                   [("Hari pertama di %3$s, tim memukul mundur pengintai %4$s sebelum mereka sempat memasang tanda.",
                     "On the first day at %3$s the team drove off the scouts of %4$s before they could set their marks."),
                    ("Pengintai %4$s lolos dari %3$s membawa kabar tentang kekuatan penjaga kita.",
                     "The scouts of %4$s slipped away from %3$s with news of our guards' strength."),
                    ("%2$s menyisir hutan perbatasan dan menemukan perkemahan musuh yang tersembunyi.",
                     "%2$s combed the border forest and found the enemy's hidden camp."),
                    ("Tim kehilangan jejak di hutan perbatasan; malam itu tidak seorang pun tidur nyenyak.",
                     "The team lost the trail in the border forest; nobody slept well that night."),
                    ("Serangan fajar %1$s menghancurkan perkemahan itu. Patok batas sekte berdiri lagi dengan tegak.",
                     "%1$s's dawn attack broke the camp. The sect's boundary stones stand straight again."),
                    ("Serangan balik %4$s memaksa tim mundur ke dalam wilayah sendiri. Perbatasan masih goyah.",
                     "A counterattack by %4$s pushed the team back inside our own land. The border is still unsteady.")]),
    "lelang": ("Lelang Kota", "City Auction",
               "Rumah lelang besar di kota membuka pelelangan tahunan. Tim membawa poin kontribusi kas aula; separuh kembali bila mereka pulang dengan tangan kosong.",
               "The great auction house in town holds its yearly sale. The team carries the hall's contribution points; half come back if they return empty-handed.",
               [("%1$s mendapat kursi di lantai dua rumah lelang %3$s berkat surat pengantar dan sikap yang sopan.",
                 "%1$s won a seat on the upper floor of the auction house at %3$s with a letter of introduction and good manners."),
                ("Penjaga rumah lelang di %3$s menempatkan tim di sudut paling belakang, jauh dari panggung.",
                 "The auction house wardens at %3$s put the team in the farthest corner, far from the stage."),
                ("%2$s memperhatikan siapa yang menawar dengan gugup dan menunggu saat yang tepat untuk menaikkan harga.",
                 "%2$s watched who bid nervously and waited for the right moment to raise."),
                ("Seorang tuan muda keluarga besar terus menaikkan tawaran hanya untuk mempermalukan sekte kita. Harga melambung tidak masuk akal.",
                 "A great clan's young master kept raising the bid only to shame our sect. The price soared beyond reason."),
                ("Palu diketuk. Barang incaran jatuh ke tangan %1$s, dan juru lelang sendiri membungkusnya.",
                 "The hammer fell. The prize went to %1$s, and the auctioneer wrapped it himself."),
                ("Tim keluar dari rumah lelang dengan kantong yang masih berat dan hati yang kecewa.",
                 "The team left the auction house with purses still heavy and hearts let down.")]),
    "kabar": ("Kabar Angin", "Chase a Rumour",
              "Pedagang keliling membawa kabar tentang cahaya aneh di malam hari. Mungkin pusaka, mungkin cuma cerita.",
              "A pedlar brings word of a strange light at night. Perhaps a treasure, perhaps only a story.",
              [("Penduduk %3$s membenarkan kabar itu: tiga malam berturut-turut cahaya keperakan muncul di balik bukit.",
                "The folk of %3$s confirmed it: three nights running, a silvery light rose behind the hill."),
               ("Di %3$s setiap orang menceritakan kabar itu dengan cara berbeda. Tim membuang waktu mengejar bayangan.",
                "At %3$s everyone told the rumour differently. The team wasted its time chasing shadows."),
               ("%2$s menyamar sebagai pemburu dan mendapatkan peta kasar dari seorang pemabuk di kedai.",
                "%2$s posed as a hunter and got a rough map from a drunk in the tavern."),
               ("Peta yang dibeli %2$s ternyata palsu; penjualnya sudah lama kabur.",
                "The map %2$s bought was a fake; its seller was long gone."),
               ("%1$s menemukan sumber cahaya itu dan mencatat letaknya dengan teliti sebelum pulang.",
                "%1$s found the source of the light and wrote down where it lay before coming home."),
               ("Bukit itu kosong. Apa pun yang pernah bersinar di sana sudah tidak ada.",
                "The hill was empty. Whatever once shone there was gone.")]),
    "samar": ("Penyamaran", "Infiltration",
              "Sebuah sekte bermusuhan sedang menyiapkan sesuatu. Tim menyamar menjadi murid luar mereka untuk mencari tahu.",
              "A hostile sect is preparing something. The team disguises itself as their outer disciples to find out what.",
              [("%1$s dan timnya menyusup ke barisan murid luar %4$s dengan jubah pinjaman dan nama palsu.",
                "%1$s and the team slipped into the outer disciples of %4$s in borrowed robes and false names."),
               ("Seorang penjaga %4$s mencurigai logat %2$s. Tim harus berpencar sebelum sempat masuk.",
                "A guard of %4$s grew suspicious of %2$s's accent. The team had to scatter before they got in."),
               ("Dari balik rak kitab, %2$s mendengar rencana para tetua %4$s dan menyalin denah gudang mereka.",
                "From behind a scripture shelf %2$s overheard the elders of %4$s and copied the plan of their storehouse."),
               ("Tim hanya mendapat potongan-potongan rencana; para tetua %4$s menutup pintu terlalu rapat.",
                "The team caught only scraps of the plan; the elders of %4$s kept their doors too tightly shut."),
               ("Penyamaran terbongkar pada hari terakhir, tetapi %1$s sudah memimpin tim keluar lewat jalur air sebelum tanda bahaya berbunyi.",
                "The disguise was uncovered on the last day, but %1$s had already led the team out by the water channel before the alarm sounded."),
               ("Penyamaran terbongkar. Tim menerobos keluar di bawah hujan serangan %4$s, dan sekte itu kini tahu siapa yang mengirim mereka.",
                "The disguise was uncovered. The team broke out under a hail of attacks from %4$s, and that sect now knows who sent them.")]),
}

for tid, (name_i, name_e, desc_i, desc_e, stages) in TEMPLATES.items():
    assert len(stages) == 6, tid
    put(M + "t." + tid, name_i, name_e)
    put(M + "t." + tid + ".desc", desc_i, desc_e)
    for s in range(3):
        put(M + "t." + tid + ".s%d.ok" % (s + 1), stages[s * 2][0], stages[s * 2][1])
        put(M + "t." + tid + ".s%d.gagal" % (s + 1), stages[s * 2 + 1][0], stages[s * 2 + 1][1])
    put(M + "asal." + tid, "misi " + name_i, "the " + name_e + " mission")

# ============================================================================== kinds, stances, grades, places, foes
for k, i, e in [("tempur", "Tempur", "Combat"), ("licik", "Siasat", "Cunning"), ("damai", "Diplomasi", "Diplomacy"),
                ("jelajah", "Jelajah", "Exploration")]:
    put(M + "jenis." + k, i, e)
for k, i, e, di, de in [
    ("damai", "Damai", "Peaceful", "Utamakan bicara: +8 poin peluang di tahap diplomasi, -5 di tahap tempur.",
     "Talk first: +8 points of chance at diplomacy stages, -5 at combat stages."),
    ("tegas", "Tegas", "Firm", "Tunjukkan kekuatan: +8 poin di tahap tempur, -5 di tahap siasat.",
     "Show strength: +8 points at combat stages, -5 at cunning stages."),
    ("licik", "Licik", "Cunning", "Mata dan telinga lebih dulu: +8 poin di tahap siasat, -5 di tahap diplomasi.",
     "Eyes and ears first: +8 points at cunning stages, -5 at diplomacy stages.")]:
    put(M + "sikap." + k, i, e)
    put(M + "sikap." + k + ".desc", di, de)
for g, i, e in [(0, "Gagal", "Failed"), (1, "Sebagian", "Partial"), (2, "Berhasil", "Success"), (3, "Legendaris", "Legendary")]:
    put(M + "grade.%d" % g, i, e)

PLACES = [("Lembah Kabut Ungu", "Purple Mist Valley"), ("Celah Angin Hitam", "Black Wind Pass"),
          ("Hutan Bambu Seribu Jarum", "the Thousand-Needle Bamboo Forest"), ("Jalur Kafilah Gurun Kuning", "the Yellow Desert caravan road"),
          ("Rawa Bulan Tenggelam", "Sunken Moon Marsh"), ("Puncak Elang Patah", "Broken Eagle Peak"),
          ("kota pasar Sungai Giok", "the Jade River market town"), ("reruntuhan Istana Awan", "the ruins of the Cloud Palace"),
          ("Gua Naga Tidur", "Sleeping Dragon Cave"), ("Danau Cermin Es", "Ice Mirror Lake"),
          ("Pegunungan Binatang Ajaib", "the Magical Beast Mountains"), ("Jembatan Tali Sembilan Tikungan", "the Nine-Bend Rope Bridge"),
          ("Desa Asap Dupa", "Incense Smoke Village"), ("Padang Tulang Putih", "the White Bone Plain"),
          ("kuil tua Lonceng Retak", "the old temple of the Cracked Bell"), ("Hutan Pinus Berbisik", "the Whispering Pine Forest")]
assert len(PLACES) == 16
for n, (i, e) in enumerate(PLACES):
    put(M + "tempat.%d" % n, i, e)
FOES = [("kawanan serigala angin hitam", "a pack of black-wind wolves"), ("perampok gunung bertopeng besi", "iron-masked mountain bandits"),
        ("ular piton bersisik giok", "a jade-scaled python"), ("seorang kultivator jalan iblis", "a demonic-path cultivator"),
        ("beruang api bermata tiga", "a three-eyed fire bear"), ("roh penasaran rawa", "a vengeful marsh wraith"),
        ("pemburu pusaka bayaran", "hired treasure hunters"), ("elang petir raksasa", "a giant thunder hawk"),
        ("boneka mayat sekte sesat", "the corpse puppets of a heretic sect"), ("kalajengking pasir emas", "golden sand scorpions"),
        ("tuan muda sebuah keluarga besar dan pengawalnya", "a great clan's young master and his guards"),
        ("kawanan kera bermata merah", "a troop of red-eyed apes")]
assert len(FOES) == 12
for n, (i, e) in enumerate(FOES):
    put(M + "lawan.%d" % n, i, e)
put(M + "desa_umum", "desa di kaki gunung", "the village below the mountain")
put(M + "sekte_umum", "sebuah sekte jalan iblis", "a demonic-path sect")

# ============================================================================== report story frame
put(M + "buka.damai", "Atas perintah Ketua, %1$s memimpin %3$s murid menuju %2$s dengan pesan: utamakan jalan damai, jangan menghunus pedang lebih dulu.",
    "On the Sect Master's order, %1$s led %3$s disciples towards %2$s with one charge: seek the peaceful way, never draw first.")
put(M + "buka.tegas", "%1$s dan %3$s murid berangkat ke %2$s dengan pedang di punggung. Perintah Ketua jelas: tunjukkan kekuatan sekte bila perlu.",
    "%1$s and %3$s disciples set out for %2$s with swords on their backs. The Sect Master's order was plain: show the sect's strength if need be.")
put(M + "buka.licik", "%1$s membawa %3$s murid ke %2$s dalam pakaian pedagang biasa. Pesan Ketua: mata dan telinga lebih tajam daripada pedang.",
    "%1$s took %3$s disciples to %2$s dressed as common traders. The Sect Master's word: eyes and ears are sharper than swords.")
put(M + "tutup.0", "Tim pulang dengan kepala tertunduk. %1$s berlutut di depan prasasti: \"Murid gagal, Guru. Hukumlah murid.\"",
    "The team came home with bowed heads. %1$s knelt before the stele: \"This disciple failed, Master. Punish me.\"")
put(M + "tutup.1", "Tim pulang membawa sebagian hasil. %1$s berkata pelan, \"Separuh berhasil, separuhnya lagi menjadi pelajaran.\"",
    "The team came home with part of what it went for. %1$s said quietly, \"Half was won, and the other half was a lesson.\"")
put(M + "tutup.2", "Tim pulang dengan selamat dan kepala tegak. %1$s menyerahkan gulungan ini dengan kedua tangan.",
    "The team came home safe with heads held high. %1$s handed over this scroll with both hands.")
put(M + "tutup.3", "Nama sekte terdengar di sepanjang jalan pulang; pedagang dan pengelana membicarakan %1$s dan rekan-rekannya. Kepulangan ini akan diceritakan lama.",
    "The sect's name was heard all along the road home; traders and travellers spoke of %1$s and companions. This homecoming will be told for a long time.")
put(M + "temu.ok", "%1$s berjalan bersama tim melewati gerbang. Ia pulang.", "%1$s walked through the gate with the team. It came home.")
put(M + "temu.gagal", "%1$s tidak ikut pulang. Barangkali lain kali, kata %2$s.", "%1$s did not come back. Another time perhaps, said %2$s.")

# ============================================================================== report screen
for k, i, e in [
    ("judul_layar", "Gulungan Laporan", "Report Scroll"),
    ("judul", "Laporan Misi: %1$s (tingkat %2$s)", "Mission Report: %1$s (tier %2$s)"),
    ("sub", "Hari ke-%1$s · sikap %2$s", "Day %1$s · stance %2$s"),
    ("tim", "Tim:", "Team:"),
    ("pemimpin", " (pemimpin)", " (leader)"),
    ("ditinggal_tanda", " (ditinggal)", " (left behind)"),
    ("batal", "Tahap %s tidak dijalani: tim dipanggil pulang lebih dulu.", "Stage %s never happened: the team was called home first."),
    ("sergap", "Di tengah jalan, %s menyergap tim!", "On the road, %s ambushed the team!"),
    ("luka", "%s terluka dalam kejadian itu.", "%s was hurt in it."),
    ("ditarik", "Atas panggilan Ketua, tim kembali sebelum tugas tuntas.", "At the Sect Master's call, the team came back before the task was done."),
    ("hasil", "Dibawa pulang", "Brought home"),
    ("calon", "%s pemuda berbakat ikut pulang sebagai calon murid", "%s talented youths came along as candidates"),
    ("lagi", "dan %s barang lain", "and %s more items"),
    ("peta", "Letak cahaya pusaka: X %1$s, Z %2$s", "Where the treasure light rose: X %1$s, Z %2$s"),
    ("ditemukan", "%s kembali ke sekte", "%s returned to the sect"),
    ("refund", "Separuh uang lelang kembali ke kas: %s PK", "Half the auction purse came back to the hall: %s PK"),
    ("ditinggal", "%s ditinggal di medan misi dan pulang sendiri", "%s was left behind in the field and came home alone"),
    ("terluka", "%s pulang terluka (30%% nyawa, penyimpangan Qi)", "%s came home hurt (30%% health, Qi Deviation)"),
    ("kosong", "Tidak ada yang dibawa pulang.", "Nothing was brought home."),
    ("kutip", "Dari ingatan %1$s: \"%2$s\"", "From the memories of %1$s: \"%2$s\""),
    ("item", "%1$s× %2$s", "%1$s× %2$s"),
]:
    put(M + "lapor." + k, i, e)

# ============================================================================== the tablet tabs
for k, i, e in [
    ("tab.cerita", "Misi Cerita", "Story Missions"),
    ("tab.kabar", "Kabar Misi", "Mission News"),
    ("papan", "Papan Misi Cerita · %s selesai", "Story Mission Board · %s done"),
    ("tim", "Tim (maks %s)", "Team (max %s)"),
    ("kabar", "Tim di Luar (%1$s/%2$s)", "Teams Abroad (%1$s/%2$s)"),
    ("laporan", "Laporan Terdahulu", "Past Reports"),
    ("mati", "Misi cerita dimatikan di config (kultivator-sekte.json, misi.aktif).",
     "Story missions are switched off in the config (kultivator-sekte.json, misi.aktif)."),
    ("papan_kosong", "Belum ada tawaran hari ini. Papan berganti setiap fajar.", "No offers today. The board changes every dawn."),
    ("kartu", "Tingkat %1$s · %2$s hari", "Tier %1$s · %2$s days"),
    ("harga", "%s PK", "%s PK"),
    ("diambil", "Sudah diambil hari ini", "Already taken today"),
    ("tahap", "Tahap:", "Stages:"),
    ("tip_sikap", "Pilih sikap di bawah papan: sikap yang cocok menambah peluang tahapnya.",
     "Pick a stance under the board: a fitting stance raises its stages' chance."),
    ("cari", "mencari %s", "looking for %s"),
    ("penuh", "Sudah %s tim di luar; tunggu satu pulang.", "%s teams are already abroad; wait for one to return."),
    ("pilih_misi", "Pilih satu tawaran di papan.", "Pick an offer on the board."),
    ("pilih_tim", "Pilih 1-4 murid di sebelah kanan.", "Pick 1-4 disciples on the right."),
    ("menghitung", "Menghitung peluang...", "Working out the chances..."),
    ("jenjang_kurang", "Jenjang tim terlalu rendah untuk tingkat ini.", "The team's ranks are too low for this tier."),
    ("peluang", "Peluang tahap: %1$s · %2$s · %3$s — legendaris %4$s", "Stage chances: %1$s · %2$s · %3$s — legendary %4$s"),
    ("rincian", "Rincian peluang", "Chance breakdown"),
    ("rincian.baris", "Tahap %1$s: %2$s (ranah %3$s, sikap %4$s, watak %5$s, ikatan %6$s, tim %7$s, %8$s)",
     "Stage %1$s: %2$s (realm %3$s, stance %4$s, traits %5$s, ties %6$s, team %7$s, %8$s)"),
    ("rincian.ket", "Angka dalam poin persen; pengali terakhir dari semangat kerja (watak) dan Tetua Urusan Luar.",
     "Numbers are percentage points; the last factor is work spirit (traits) and the Outer Affairs Elder."),
    ("kirim", "Kirim Tim", "Send the Team"),
    ("kirim.tip", "Tim berangkat sebagai data (tidak membebani dunia) dan pulang lewat gerbang membawa laporan.",
     "The team leaves as data (no load on the world) and comes home through the gate with a report."),
    ("tidak_ada", "Tidak ada tim di luar.", "No team is abroad."),
    ("berjalan", "%1$s · tingkat %2$s · %3$s", "%1$s · tier %2$s · %3$s"),
    ("bead", "Tahap %1$s · %2$s: %3$s", "Stage %1$s · %2$s: %3$s"),
    ("bead.tunggu", "menunggu kabar", "awaiting news"),
    ("bead.ok", "berhasil", "succeeded"),
    ("bead.gagal", "gagal, cerita berbelok", "failed, the story turned"),
    ("pulang_jalan", "Dalam perjalanan pulang...", "On the way home..."),
    ("ditarik", "Dipanggil pulang · %s", "Called home · %s"),
    ("sisa", "Sisa %1$s hari (%2$s)", "%1$s days left (%2$s)"),
    ("sergap", "Disergap oleh %s: tahap berikutnya menjadi pertempuran.", "Ambushed by %s: the next stage becomes a fight."),
    ("riwayat_kosong", "Belum ada laporan.", "No reports yet."),
    ("riwayat", "Hari %1$s · %2$s · %3$s · %4$s · calon %5$s · harta %6$s", "Day %1$s · %2$s · %3$s · %4$s · candidates %5$s · treasures %6$s"),
    ("tarik", "Tarik Pulang", "Call Home"),
    ("tarik.tip", "Seluruh tim pulang sekarang; misi berakhir dengan tahap yang sudah dijalani.",
     "The whole team comes home now; the mission ends with the stages done so far."),
    ("tarik.tip_luka", "Seluruh tim pulang dan yang terluka ditandu: butuh seperempat hari.",
     "The whole team comes home carrying the wounded: it takes a quarter day."),
    ("tinggal", "Tanpa yang Luka", "Without the Wounded"),
    ("tinggal.tip", "Yang sehat pulang seketika; yang terluka ditinggal dan pulang sendiri di akhir misi. Mereka akan mengingatnya (Dikorbankan).",
     "The healthy come home at once; the wounded are left to come home alone at the mission's end. They will remember it (Sacrificed)."),
    ("baca", "Baca", "Read"),
]:
    put(M + "ui." + k, i, e)

# ============================================================================== checks before departure
for k, i, e in [
    ("sibuk", "%s sedang sibuk (terobosan, duel, atau sedang dilepas).", "%s is busy (a breakthrough, a duel, or being released)."),
    ("tugas", "%s sedang memegang tugas lain; lepaskan dulu dari tugasnya.", "%s holds another task; release it first."),
    ("jauh", "%s terlalu jauh dari prasasti aula (maks 64 blok).", "%s is too far from the hall stele (64 blocks at most)."),
    ("tercatat", "%s masih Murid Tercatat; minimal Murid Luar.", "%s is still a Registered Disciple; Outer Disciple at least."),
    ("membangkang", "%s menolak berangkat (sedang membangkang).", "%s refuses to go (defiant)."),
    ("dendam", "%s dan %s saling menyimpan dendam dan menolak berangkat dalam satu tim.", "%s and %s hold a grudge and refuse to travel in one team."),
    ("pergi", "%s sudah berada dalam misi.", "%s is already on a mission."),
    ("mati", "Misi cerita dimatikan di config.", "Story missions are switched off in the config."),
    ("aula", "Butuh aula sekte yang aktif.", "This needs an active Sect Hall."),
    ("penuh", "Sudah ada %s misi cerita berjalan; tunggu satu tim pulang.", "%s story missions are already running; wait for a team to return."),
    ("hilang", "Tawaran itu sudah tidak ada.", "That offer is gone."),
    ("ukuran", "Tim harus berisi 1 sampai %s murid.", "A team has 1 to %s disciples."),
    ("hilang_murid", "Seorang anggota tim tidak ditemukan di dekat aula.", "A team member could not be found near the hall."),
    ("jenjang", "Misi tingkat %1$s butuh pemimpin berjenjang lebih tinggi (tim ini maksimal tingkat %2$s).",
     "A tier %1$s mission needs a higher-ranked leader (this team can take tier %2$s at most)."),
    ("pk", "Kas aula kurang: lelang butuh %s PK.", "The hall purse is short: the auction needs %s PK."),
]:
    put(M + "cek." + k, i, e)

# ============================================================================== chat, log, item, scenes, advancements
for k, i, e in [
    ("berangkat", "Tim %3$s (%4$s murid) berangkat untuk %1$s tingkat %2$s dengan sikap %5$s. Mereka akan kembali dalam %6$s hari.",
     "Team %3$s (%4$s disciples) leaves for %1$s, tier %2$s, with a %5$s stance. They will be back in %6$s days."),
    ("lelang.bayar", "Kas aula membayar %s PK sebagai uang lelang.", "The hall purse pays %s PK as the auction money."),
    ("log.berangkat", "Tim %3$s (%4$s murid) berangkat: %1$s tingkat %2$s.", "Team %3$s (%4$s disciples) set out: %1$s, tier %2$s."),
    ("log.selesai", "Misi %1$s selesai (%2$s), pemimpin %3$s: %4$s calon, %5$s harta.",
     "Mission %1$s finished (%2$s), led by %3$s: %4$s candidates, %5$s treasures."),
    ("selesai", "Kabar: tim misi %1$s menuntaskan tugasnya (%2$s). Calon: %3$s, harta: %4$s. Mereka sedang dalam perjalanan pulang.",
     "News: the %1$s team finished its task (%2$s). Candidates: %3$s, treasures: %4$s. They are on their way home."),
    ("standing.naik", "Hubungan dengan %1$s membaik (+%2$s).", "Standing with %1$s rose (+%2$s)."),
    ("standing.turun", "Hubungan dengan %1$s memburuk (-%2$s).", "Standing with %1$s fell (-%2$s)."),
    ("renown", "Misi cerita", "Story mission"),
    ("pk", "Misi cerita", "Story mission"),
    ("kabar.ok", "Kabar misi %1$s, tahap %2$s (%3$s): berhasil. %4$s", "News of %1$s, stage %2$s (%3$s): success. %4$s"),
    ("kabar.gagal", "Kabar misi %1$s, tahap %2$s (%3$s): gagal, cerita berbelok. %4$s",
     "News of %1$s, stage %2$s (%3$s): failed, the story turns. %4$s"),
    ("kabar.luka", "%s terluka.", "%s was hurt."),
    ("kabar.sergap", "Tim misi %1$s disergap oleh %2$s! Tahap berikutnya menjadi pertempuran.",
     "The %1$s team was ambushed by %2$s! The next stage becomes a fight."),
    ("tarik.tidak", "Tim itu tidak bisa dipanggil lagi.", "That team can no longer be called home."),
    ("tarik.ya", "Tim misi %1$s dipanggil pulang.", "The %1$s team is called home."),
    ("tarik.tinggal", "Tim misi %1$s dipanggil pulang tanpa yang terluka. %2$s murid ditinggal di lapangan dan harus pulang sendiri.",
     "The %1$s team is called home without its wounded. %2$s disciples are left in the field and must come home alone."),
    ("gulungan.terima", "Gulungan Laporan misi %s diserahkan kepadamu. Klik kanan untuk membacanya.",
     "The report scroll of %s is handed to you. Right-click to read it."),
    ("gulungan.lore", "Misi: %1$s (tingkat %2$s)", "Mission: %1$s (tier %2$s)"),
    ("gulungan.lore2", "%1$s · hari ke-%2$s", "%1$s · day %2$s"),
    ("gulungan.lore3", "Klik kanan untuk membaca", "Right-click to read"),
    ("calon", "%s pemuda ikut pulang bersama tim dan menunggu Hari Penerimaan berikutnya.",
     "%s youths came home with the team and wait for the next Recruitment Day."),
    ("peta", "Laporan menyebut cahaya pusaka di sekitar X %1$s, Z %2$s.", "The report places a treasure's light near X %1$s, Z %2$s."),
    ("adegan.pulang", "Tim Misi Kembali — %s", "The Mission Team Returns — %s"),
    ("adegan.gemilang", "Kepulangan Gemilang", "A Glorious Homecoming"),
    ("adegan.gemilang.sub", "%s memimpin timnya pulang dengan kemenangan", "%s leads the team home in triumph"),
    ("adv.pulang.title", "Kabar dari Jauh", "News from Afar"),
    ("adv.pulang.desc", "Terima Gulungan Laporan pertama dari tim misi cerita.", "Receive your first report scroll from a story mission team."),
    ("adv.bawa.title", "Pulang Membawa Berkah", "Home with Blessings"),
    ("adv.bawa.desc", "Tim misi pulang membawa calon murid, harta, murid yang hilang, atau letak pusaka.",
     "A mission team comes home with candidates, treasures, a lost disciple or a treasure's place."),
    ("ingat.sukses", "Pulang dari misi luar dengan jasa", "Came home from an outside mission with merit"),
    ("ingat.gagal", "Misi luar yang gagal", "An outside mission that failed"),
    ("ingat.luka", "Terluka dalam misi luar", "Wounded on an outside mission"),
    ("ingat.ditinggal", "Ditinggal terluka di medan misi", "Left wounded in the field"),
    ("sumber", "Sumber: I Shall Seal the Heavens bab 19 (murid luar pulang membawa dua-tiga pemuda berbakat); ekspedisi ke Pegunungan Binatang Ajaib dalam Battle Through the Heavens.",
     "Source: I Shall Seal the Heavens ch. 19 (outer disciples come back with two or three talented youths); the expeditions into the Magical Beast Mountain Range in Battle Through the Heavens."),
]:
    put(M + k, i, e)

# ============================================================================== commands
for k, i, e in [
    ("uji.templat", "Templat tidak dikenal. Pilihan: %s", "Unknown template. Choices: %s"),
    ("uji.nama", "Tidak ada murid dengan nama itu di dekatmu (pisahkan nama dengan koma).",
     "No disciple of that name near you (separate names with commas)."),
    ("uji.selesai", "%s misi kini jatuh tempo; tim pulang pada tick aula berikutnya.", "%s missions are due now; the teams come home on the next hall tick."),
    ("uji.hasil", "%1$s misi akan berakhir dengan %2$s tahap berhasil.", "%1$s missions will end with %2$s successful stages."),
    ("uji.pulang", "MISSION_RETURNED dengan pulang:<jiwa> ditembakkan untuk %s.", "MISSION_RETURNED with pulang:<soul> fired for %s."),
    ("uji.pulang_tidak", "Nama itu tidak ada di daftar yang dicari (%s nama).", "That name is not on the search list (%s names)."),
    ("uji.papan", "Papan misi cerita diundi ulang: %s tawaran.", "The story board was rerolled: %s offers."),
    ("uji.info", "Misi cerita: %1$s/%2$s berjalan · %3$s murid dicari · %4$s selesai", "Story missions: %1$s/%2$s running · %3$s disciples sought · %4$s done"),
    ("uji.baris", "#%1$s %2$s t%3$s · tahap %4$s/3 · peluang %5$s · %6$s", "#%1$s %2$s t%3$s · stage %4$s/3 · chances %5$s · %6$s"),
    ("uji.berjalan", "berjalan", "running"),
    ("uji.pulang_jalan", "pulang", "returning"),
    ("status.judul", "Misi cerita sekte (%1$s/%2$s):", "Sect story missions (%1$s/%2$s):"),
    ("status.baris", "• %1$s tingkat %2$s · tahap %3$s/3 · sisa %4$s hari", "• %1$s tier %2$s · stage %3$s/3 · %4$s days left"),
    ("status.petunjuk", "Buka tablet Misi di aula sekte (bagian Misi Cerita / Kabar Misi), atau kartu Misi Cerita di hub.",
     "Open the Misi tablet of the Sect Hall (sections Story Missions / Mission News), or the Story Missions card in the hub."),
]:
    put(M + k, i, e)

# ============================================================================== outside the module prefix (vanilla / hub rules)
put("item.kultivator.gulungan_laporan", "Gulungan Laporan", "Report Scroll")
put("kultivator.hub.card.sekte_misi_cerita", "Misi Cerita", "Story Missions")
put("kultivator.hub.card.sekte_misi_cerita.tip", "Kirim tim murid ke dunia luar dan baca laporan kepulangan mereka.",
    "Send disciple teams into the world and read the reports they bring home.")


def write(code, index):
    path = os.path.join(REPO, "lang-src", "sekte_misi.%s.json" % code)
    data = {k: v[index] for k, v in sorted(T.items())}
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return len(data)


if __name__ == "__main__":
    n = write("id_id", 0)
    write("en_us", 1)
    print("sekte_misi lang: %d keys" % n)
