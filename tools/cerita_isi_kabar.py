"""38ST content: rumours that follow the world (DESIGN 38.S.5) and the 40 lantern riddles of the Lantern Festival
(38.B.6.1, read by 38KS through CeritaApi.TEKA_TEKI). Templates by trigger:
  umum                 background talk when nothing lives (penyebar = who says it)
  harta_*, langit_*, segel_pecah, binatang_evolusi, situs_gua    world events (DuniaEvents)
  tempat_rusak, kejahatan, acara_<id>                            place events (TempatEvents)
  sekte_<aksi>, sekte_turnamen                                   NPC sect news (SekteBus; arg = sekte:<id>)
  quest_<id>                                                     a quest finished (arg = the player's name)
  perbaikan.*                                                    38PR repairs and bills (CeritaApi.kabar by id)
A %s in a text is filled with the arguments given when the rumour spread, then the template's own isian."""

KOTA = ['warga', 'pemilik_teh', 'pemilik_penginapan', 'koki', 'pelayan', 'kuli', 'petugas_gilda', 'anak', 'pengemis',
        'pedagang_keliling', 'penjaga_gerbang', 'pedagang_senjata', 'pedagang_pil', 'nelayan', 'petani']
DESA = ['petani', 'kepala_desa', 'tetua_desa', 'anak', 'pandai_desa', 'nelayan', 'pedagang_keliling']
JALAN = ['pedagang_keliling', 'pengembara', 'kuli', 'penjaga_gerbang']
PENJAGA = ['penjaga', 'penjaga_gerbang', 'kapten_penjaga', 'pengawal_istana']

UMUM = [
    ('Katanya harga garam naik lagi. Kafilah dari pantai terlambat tiga hari.',
     'They say salt went up again. The caravan from the coast is three days late.', KOTA),
    ('Anak tukang roti di ujung jalan katanya punya akar roh. Ibunya sudah membeli jubah murid, padahal ujiannya belum.',
     'The baker\'s boy at the end of the street has a spirit root, they say. His mother already bought a disciple\'s robe, '
     'though the trial is not until next month.', KOTA),
    ('Kalau kau ke %s, jangan minum teh di kedai dekat gerbang. Tehnya encer, harganya kental.',
     'If you go to %s, do not drink tea at the stall by the gate. The tea is thin, the price is thick.', KOTA, ['kota']),
    ('Pedagang keliling Lao Qiao bilang jalan ke kota-kota di barat sedang dibegal. Ia sendiri lewat sana kemarin, entah bagaimana.',
     'The peddler Lao Qiao says the roads to the western towns are full of bandits. He went that way himself yesterday, somehow.', KOTA + JALAN),
    ('Ada sekte di pegunungan yang mencari murid baru. Katanya %s. Tangganya seribu anak; separuh calon menyerah di tengah.',
     'A sect in the mountains is looking for new disciples. %s, they say. Its stair has a thousand steps; half the candidates '
     'give up halfway.', KOTA + DESA, ['sekte_terdekat']),
    ('Jangan berkemah dekat sungai tiga malam ini. Nelayan melihat cahaya biru di bawah air.',
     'Do not camp by the river these three nights. The fishermen saw blue light under the water.', DESA + ['nelayan']),
    ('Kata nenekku, kalau angin berbau besi, berarti ada kultivator yang menerobos ranah di dekat sini.',
     'My grandmother says when the wind smells of iron, a cultivator nearby is breaking through.', KOTA + DESA),
    ('Gilda Kultivator memasang misi baru: sarang binatang di hutan timur. Hadiahnya besar, nyawanya kecil.',
     'The Cultivators\' Guild posted a new mission: a beast den in the eastern woods. The reward is big, the odds are small.',
     ['petugas_gilda', 'warga', 'pemilik_teh', 'penguji']),
    ('Feng Ao naik lagi di Daftar Naga Muda. Katanya ia tidak tidur, hanya bermeditasi sambil berdiri.',
     'Feng Ao climbed the Young Dragon List again. They say he never sleeps, only meditates standing up.',
     ['petugas_gilda', 'warga', 'penguji', 'pemilik_teh']),
    ('Biksu di kuil bilang lonceng berbunyi sendiri tadi malam. Tidak ada angin.',
     'The monk at the temple says the bell rang by itself last night. There was no wind.', KOTA + ['rahib']),
    ('Pandai besi di desa sebelah menempa mata cangkul yang bisa memotong batu. Para petani antre sampai jalan.',
     'The smith in the next village forged a hoe blade that cuts stone. The farmers queue all the way down the road.', DESA),
    ('Katanya %s pernah menolak pinangan seorang tuan muda klan dengan satu jurus pedang. Lalu menikahinya setahun kemudian.',
     'They say %s once turned down a clan young master\'s proposal with a single sword form. Then married him a year later.',
     KOTA, ['tokoh:lin_fei']),
    ('Hujan tahun ini datang tepat waktu. Kepala desa menyalakan dupa untuk Dewa Tanah setiap pagi; mungkin itu sebabnya.',
     'The rains came on time this year. The village head lights incense for the Earth God every morning; perhaps that is why.', DESA),
    ('Ada pertapa di gua di lereng utara. Orang yang membawakannya herba tua kadang pulang dengan wajah yang lebih tenang.',
     'There is a hermit in a cave on the northern slope. Those who bring him old herbs sometimes come home calmer.', DESA + JALAN),
    ('Jangan dekati makam tua di balik bukit. Tiap purnama ada cahaya keluar dari stelanya. Katanya makam leluhur.',
     'Stay away from the old tomb past the hill. Every full moon light leaks from its stele. An ancestor\'s tomb, they say.', DESA + JALAN),
    ('Di pos singgah ada kuda yang bisa menghitung sampai tiga. Pemiliknya memungut satu keping untuk setiap hitungan.',
     'At the road post there is a horse that can count to three. Its owner charges one chip per count.', JALAN),
    ('Tonggak-tonggak di jalan kekaisaran ditulis oleh raja jalan kuno. Kalau tulisannya bilang sepuluh li, itu sepuluh li, tidak kurang.',
     'The milestones on the imperial road were carved by the old road kings. If one says ten li, it is ten li, not a step less.', JALAN),
    ('Penjaga bilang ada pencuri yang bisa memanjat tembok tanpa tangan. Kami bilang itu kucing. Penjaga tidak suka dibilang begitu.',
     'The guards say there is a thief who climbs walls without hands. We say it is a cat. The guards do not like that.', KOTA + PENJAGA),
    ('Kapten Yun Fei bilang kapal awannya pernah menabrak burung sebesar rumah. Tidak ada yang percaya, tapi semua mau mendengarnya lagi.',
     'Captain Yun Fei says his cloud ship once hit a bird as big as a house. Nobody believes it, but everyone wants to hear it again.',
     ['nakhoda', 'kuli', 'warga', 'pemilik_teh']),
    ('Gendut Zhu memasak sup tulang sembilan jam lagi. Kalau kau mencium bau jahe dari pasar, itu supnya.',
     'Fat Zhu is making his nine-hour bone soup again. If you smell ginger from the market, that is the soup.', KOTA),
    ('Bibi Hong di penginapan menanyai setiap tamu tentang anaknya. Kalau kau ke sekte di gunung, bawalah kabar untuknya.',
     'Bibi Hong at the inn asks every guest about her son. If you go to a mountain sect, bring her some news.', KOTA),
    ('Nyonya Qian di Rumah Uang tidak pernah lupa satu keping pun. Konon ia menghitung dalam tidurnya.',
     'Madam Qian at the Money House never forgets a single chip. They say she counts in her sleep.', KOTA + ['bankir']),
    ('Lao Shi Si Mata Satu bilang batu mentah minggu ini bagus. Lao Shi selalu bilang begitu.',
     'One-Eyed Lao Shi says this week\'s raw stones are good. Lao Shi always says that.', KOTA + ['bandar_batu', 'pemotong_batu']),
    ('Pasar Binatang punya anak serigala roh baru. Ma Laoliu menolak menjualnya pada siapa pun yang tidak ia sukai, yaitu hampir semua orang.',
     'The Beast Market has a new spirit wolf cub. Ma Laoliu refuses to sell it to anyone he dislikes, which is nearly everyone.',
     KOTA + ['pedagang_binatang']),
    ('Tuan Wen di perpustakaan kehilangan kacamatanya lagi. Kacamatanya ada di kepalanya. Lagi.',
     'Master Wen at the library lost his spectacles again. They are on his head. Again.', KOTA + ['pustakawan']),
    ('Di Kampung Kumuh ada kakek yang tidur di lumpur tiap musim. Anehnya ia tak pernah sakit, dan anjing-anjing liar menghormatinya.',
     'In the slums there is an old man who sleeps in the mud every season. Strangely he never falls ill, and the stray dogs '
     'respect him.', KOTA + ['pengemis']),
    ('Sekte jalan iblis katanya membeli anak-anak di desa perbatasan. Kalau ada orang berjubah hitam menawar anak, laporkan ke penjaga.',
     'They say a demonic sect is buying children in the border villages. If a black-robed man bargains for a child, tell the guards.',
     DESA + PENJAGA),
    ('Nelayan menemukan sisik ikan sebesar tampah di muara. Ada yang bilang naga, ada yang bilang ikan yang makan terlalu banyak.',
     'Fishermen found a fish scale as big as a winnowing tray at the river mouth. Some say a dragon, some say a fish that ate too much.',
     DESA + ['nelayan']),
    ('Penjual pil di pasar menjual "Pil Panjang Umur". Kakek yang membelinya meninggal minggu lalu, umur seratus dua.',
     'A pill seller at the market sells "Long Life Pills". The grandfather who bought them died last week, aged one hundred and two.', KOTA),
    ('Malam ini bulan bulat. Anak-anak membuat lentera dari kertas minyak. Kalau festival datang, kota ini akan seperti sungai cahaya.',
     'The moon is full tonight. The children are making lanterns from oiled paper. When the festival comes, this city will be a river of light.',
     KOTA + ['penjaga_teka']),
    ('Tukang di pos jalan bilang jembatan barat retak karena kereta kultivator yang terlalu berat. Kultivator bilang karena jembatannya tua.',
     'The craftsman at the road post says the west bridge cracked under a cultivator\'s overladen cart. The cultivator says the bridge was old.',
     JALAN + ['tukang']),
    ('Hakim Bao menghukum anak menteri seperti menghukum anak tukang sapu. Separuh kota mencintainya, separuh lagi takut padanya.',
     'Magistrate Bao punishes a minister\'s son like a sweeper\'s son. Half the city loves him; the other half fears him.', KOTA + PENJAGA),
    ('Ada kabar dari %s: mereka butuh tukang batu dan membayar dengan pil. Pil asli, katanya.',
     'News from %s: they need stonemasons and pay in pills. Real pills, they say.', DESA + JALAN, ['sekte_lurus']),
    ('Hati-hati dengan %s. Murid-muridnya tersenyum ramah, tapi tak ada yang pernah melihat mereka makan siang.',
     'Be careful of %s. Their disciples smile kindly, but no one has ever seen them eat lunch.', KOTA + DESA, ['sekte_iblis']),
]

DUNIA = [
    ('harta', 'harta_lahir_1', 'harta_lahir', ('Cahaya pelangi turun di dekat gunung. Tiga kelompok kultivator sudah berangkat semalam.',
                                               'Rainbow light came down near the mountains. Three bands of cultivators set out last night.')),
    ('harta', 'harta_lahir_2', 'harta_lahir', ('Bumi bergetar dan ada bau bunga yang tak pernah tumbuh di sini. Harta langit lahir, kata para tetua.',
                                               'The earth trembled and smelled of a flower that never grows here. A heavenly treasure is born, say the elders.')),
    ('harta', 'harta_diklaim_1', 'harta_diklaim', ('Harta langit itu sudah ada pemiliknya: %s. Katanya tiga saingan pulang dengan jubah robek.',
                                                   'That heavenly treasure has an owner now: %s. Three rivals went home with torn robes, they say.')),
    ('harta', 'harta_diklaim_2', 'harta_diklaim', ('%s mengambil harta yang lahir di gunung. Para pemburu harta minum sampai pagi untuk menghibur diri.',
                                                   '%s took the treasure born in the mountains. The treasure hunters drank till dawn to console themselves.')),
    ('langit', 'langit_meteor_1', 'langit_meteor_shower', ('Hujan bintang malam ini! Orang bilang setiap bintang yang jatuh adalah batu roh. Orang bilang banyak hal.',
                                                           'A star shower tonight! People say every falling star is a spirit stone. People say many things.')),
    ('langit', 'langit_meteor_2', 'langit_meteor_shower', ('Anak-anak naik ke atap untuk menghitung bintang jatuh. Penjaga naik juga, pura-pura mengawasi.',
                                                           'The children climbed onto the roofs to count falling stars. The guards climbed up too, pretending to keep watch.')),
    ('langit', 'langit_gerhana_1', 'langit_blood_eclipse', ('Sejak Gerhana Darah, pintu-pintu dikunci lebih awal. Bahkan anjing tidak mau keluar.',
                                                            'Since the Blood Eclipse, doors are locked early. Even the dogs will not go out.')),
    ('langit', 'langit_gerhana_2', 'langit_blood_eclipse', ('Bulan merah lagi. Para tetua menyalakan dupa dan menyuruh anak-anak tidur di tengah rumah.',
                                                            'The moon is red again. The elders burn incense and make the children sleep in the middle of the house.')),
    ('langit', 'langit_bulan_1', 'langit_twin_moons', ('Dua bulan di langit! Kata pertapa, malam seperti ini bagus untuk menerobos ranah. Kata nenekku, bagus untuk tidur.',
                                                       'Two moons in the sky! The hermits say such a night is good for breakthroughs. My grandmother says it is good for sleeping.')),
    ('langit', 'langit_bulan_2', 'langit_twin_moons', ('Malam bulan kembar. Air sumur naik setengah jengkal, dan ikan-ikan berenang melingkar.',
                                                       'A twin-moon night. The well water rose half a hand, and the fish swim in circles.')),
    ('dunia', 'segel_pecah_1', 'segel_pecah', ('Segel penjara kuno katanya retak. Para tetua sekte berkumpul dan tidak mau bicara pada siapa pun.',
                                               'They say an ancient prison seal has cracked. The sect elders gathered and will speak to no one.')),
    ('dunia', 'segel_pecah_2', 'segel_pecah', ('Burung-burung terbang ke selatan di tengah musim panas. Kalau burung lari, sebaiknya kita juga bersiap.',
                                               'Birds flew south in the middle of summer. When birds flee, we had better be ready too.')),
    ('dunia', 'binatang_evolusi', 'binatang_evolusi', ('Binatang roh milik %s berevolusi! Katanya raungannya terdengar sampai tiga desa.',
                                                       '%s\'s spirit beast evolved! Its roar was heard three villages away, they say.')),
    ('dunia', 'situs_gua', 'situs_gua', ('%s menemukan gua yang tak ada di peta mana pun. Pedagang peta sudah menaikkan harga.',
                                         '%s found a cave that is on no map. The map sellers have already raised their prices.')),
    ('tempat', 'tempat_rusak_1', 'tempat_rusak', ('Ada yang merusak bangunan di sini. Tukang-tukang sudah mengukur dan menghitung, dan wajah mereka tidak senang.',
                                                  'Someone damaged buildings here. The craftsmen have measured and counted, and they do not look happy.')),
    ('tempat', 'tempat_rusak_2', 'tempat_rusak', ('Separuh atap di sudut itu runtuh. Kepala tukang bilang perlu tiga hari, kalau tidak hujan.',
                                                  'Half the roofs in that corner collapsed. The master builder says three days, if it does not rain.')),
    ('tempat', 'kejahatan_1', 'kejahatan', ('Penjaga mencari %s. Kalau kau melihatnya, jangan menghalangi; penjaga tidak suka ditanya-tanya.',
                                            'The guards are looking for %s. If you see them, do not get in the way; the guards hate questions.')),
    ('tempat', 'kejahatan_2', 'kejahatan', ('Wajah %s sudah dilukis di papan Yamen. Pelukis Yamen sangat teliti; ia bahkan melukis tahi lalatnya.',
                                            '%s\'s face is painted on the Yamen board. The Yamen painter is very thorough; he even painted the mole.')),
]

ACARA = [
    ('pasar_malam', ('Pasar malam buka! Kios-kios lentera berderet sampai gerbang timur. Jangan lupa tawar setengah harga.',
                     'The night market is open! Lantern stalls line the way to the east gate. Remember to haggle down by half.')),
    ('pertemuan_pil', ('Pertemuan Pil dimulai di Gilda Alkemis! Asap dari delapan tungku membuat burung-burung pusing.',
                       'The Pill Meeting has begun at the Alchemist Guild! Smoke from eight furnaces is making the birds dizzy.')),
    ('lelang_agung', ('Lonceng Lelang Agung berbunyi! Penawar dari sembilan sekte datang; katanya ada pusaka yang belum pernah dilihat siapa pun.',
                      'The Grand Auction bell rings! Bidders from nine sects have come; they say there is a treasure nobody has ever seen.')),
    ('hari_gerbang', ('Hari Pembukaan Gerbang! Sekte-sekte membuka lapak di alun-alun, dan setiap ibu membawa anaknya untuk diuji.',
                      'Gate Opening Day! The sects set up stalls in the square, and every mother brings her child to be tested.')),
    ('turnamen_agung', ('Turnamen Agung Antar-Sekte dimulai! Kursi di Arena habis sejak subuh; pedagang kacang kaya mendadak.',
                        'The Grand Inter-Sect Tournament begins! The Arena seats sold out at dawn; the peanut sellers are suddenly rich.')),
    ('festival_lentera', ('Festival Lentera malam ini! Teka-teki digantung di setiap lentera. Yang menjawab benar dapat jimat; yang salah ditertawakan.',
                          'Lantern Festival tonight! A riddle hangs on every lantern. Answer right and win a charm; answer wrong and be laughed at.')),
    ('pertemuan_sekte', ('Para utusan sekte berkumpul di Balai Pertemuan. Penjaga istana berjaga dua kali lebih rapat dari biasa.',
                         'Sect envoys gather in the Meeting Hall. The palace guard stands twice as close as usual.')),
    ('gelombang_binatang', ('Genderang perang! Gelombang binatang datang dari hutan! Semua orang masuk rumah, penjaga naik ke tembok!',
                            'War drums! A beast tide is coming from the forest! Everyone indoors, guards to the walls!')),
]

SEKTE = [
    ('perang', ('%s menyatakan perang! Kafilah dagang memutar lewat jalan selatan.', '%s has declared war! The caravans are detouring by the southern road.')),
    ('damai', ('%s mengirim utusan damai. Pedagang di pasar lega; harga besi turun sedikit.', '%s sent envoys of peace. The market merchants are relieved; iron got a little cheaper.')),
    ('sekutu', ('%s menandatangani pakta di Balai Pertemuan. Dua sekte, satu cawan arak, seribu bisik-bisik.',
                '%s signed a pact in the Meeting Hall. Two sects, one cup of wine, a thousand whispers.')),
    ('putus', ('Pakta dengan %s putus. Kata orang, gara-gara satu murid dan satu cawan teh yang tumpah.',
               'The pact with %s is broken. People say it was over one disciple and one spilled cup of tea.')),
    ('tumbuh', ('%s semakin besar. Mereka membangun aula baru dan merekrut murid dari tiga desa.',
                '%s keeps growing. They are building a new hall and recruiting disciples from three villages.')),
    ('serangan', ('%s menyerbu wilayah sekte lain. Asapnya terlihat dari jalan kekaisaran.',
                  '%s raided another sect\'s land. The smoke could be seen from the imperial road.')),
    ('perang_npc', ('%s bertempur dengan sekte tetangganya di lembah. Para pedagang menunggu di pos sampai debu turun.',
                    '%s fought its neighbour sect in the valley. The merchants are waiting at the post until the dust settles.')),
]

QUEST = [
    ('tungku_pertama', ('Ada alkemis baru di papan Gilda Alkemis: %s. Tetua Dan Chen bilang tidak buruk, dan itu pujian terbesar darinya.',
                        'There is a new alchemist on the Alchemist Guild board: %s. Elder Dan Chen said "not bad", which is his highest praise.')),
    ('resep_hilang', ('Halaman terakhir Rumus Sembilan Embun ditemukan di makam tua! Gilda Alkemis ramai seperti pasar.',
                      'The last page of the Nine Dews Formula was found in an old tomb! The Alchemist Guild is as busy as a market.')),
    ('pedang_tanpa_nama', ('Ou Ye Muda menempa pedang tanpa nama bersama %s. Katanya bilahnya berdengung kalau pemiliknya sedih.',
                           'Young Ou Ye forged a nameless sword with %s. They say the blade hums when its owner is sad.')),
    ('batu_bermata_darah', ('Batu terkutuk Lao Shi sudah ditidurkan di kuil. Pemotong batu bisa tidur nyenyak lagi.',
                            'Lao Shi\'s cursed stone was put to sleep at the temple. The stonecutters can sleep soundly again.')),
    ('tiga_ujian_pertapa', ('Pertapa di lereng utara meninggalkan guanya. Katanya ia mendapat murid, %s, lalu pergi mengembara dengan tenang.',
                            'The hermit on the northern slope left his cave. They say he found a student, %s, and went wandering in peace.')),
    ('peta_awan', ('Muatan Kapten Yun Fei sampai di tujuannya, dan petinya tidak dibuka. Yun Fei kehilangan taruhan dengan dirinya sendiri.',
                   'Captain Yun Fei\'s cargo reached its destination unopened. Yun Fei lost a bet with himself.')),
    ('murid_kabur', ('Murid sekte yang kabur sudah ditemukan. Kitabnya kembali, dan tetuanya, katanya, mulai lebih jarang berteriak.',
                     'The runaway sect disciple was found. The manual is back, and his elder, they say, shouts less often now.')),
    ('tikus_dapur', ('Tikus roh di dapur Gendut Zhu tertangkap! Supnya sekarang bernama "Sup Pahlawan".',
                     'The spirit rat in Fat Zhu\'s kitchen was caught! His soup is now called "Hero\'s Soup".')),
]

PERBAIKAN = [
    ('perbaikan.rusak_monster', ('Binatang buas merusak %s bagian bangunan di sini. Tukang-tukang sedang mengukur kerusakannya.',
                                 'Savage beasts damaged %s pieces of building here. The craftsmen are measuring the damage.')),
    ('perbaikan.rusak_pemain', ('Ada kultivator yang menghancurkan %s bagian bangunan di sini. Kepala tukang sedang menyiapkan tagihan.',
                                'A cultivator destroyed %s pieces of building here. The master builder is drawing up the bill.')),
    ('perbaikan.selesai', ('Perbaikan selesai: %s bagian bangunan kembali seperti semula. Tukang-tukang minum teh di bawah atap yang baru.',
                           'Repairs done: %s pieces of building restored. The craftsmen are drinking tea under the new roof.')),
    ('perbaikan.mohon_bantuan', ('Kepala tukang mencari sukarelawan. Siapa pun yang mau mengangkat papan akan diberi makan siang.',
                                 'The master builder is looking for volunteers. Anyone who carries planks gets lunch.')),
    ('perbaikan.sahabat', ('%s membantu tukang-tukang mengangkat papan sepanjang hari. Kultivator yang mau berkeringat! Jarang sekali.',
                           '%s helped the craftsmen carry planks all day. A cultivator who sweats! How rare.')),
    ('perbaikan.tagihan_dibayar', ('%s membayar ganti rugi sampai kepingan terakhir. Orang jujur! Kepala tukang sampai membungkuk dua kali.',
                                   '%s paid the damages to the last chip. An honest one! The master builder bowed twice.')),
    ('perbaikan.tagihan_ditolak', ('Jangan dekati %s... ia merusak tempat ini dan tak ada yang berani menagih.',
                                   'Stay away from %s... they wrecked this place and nobody dares send the bill.')),
    ('perbaikan.rendah_hati', ('%s membayar ganti rugi tanpa ditagih, padahal semua orang takut padanya. Mungkin yang kuat tidak selalu kejam.',
                               '%s paid for the damage without being asked, though everyone feared them. Maybe the strong are not always cruel.')),
]

# 40 lantern riddles (38.B.6.1): (riddle, right, wrong, wrong); written for this game, folk riddles retold
TEKA = [
    (('Aku punya mulut tapi tak pernah makan, punya tepi tapi tak pernah duduk, selalu berlari tapi tak punya kaki.',
      'I have a mouth but never eat, banks but never sit, I always run yet have no feet.'),
     ('Sungai', 'A river'), ('Angin', 'The wind'), ('Jalan', 'A road')),
    (('Semakin banyak kau ambil dariku, semakin besar aku.', 'The more you take from me, the bigger I grow.'),
     ('Lubang', 'A hole'), ('Gunung', 'A mountain'), ('Bayangan', 'A shadow')),
    (('Aku ringan seperti bulu, tapi orang terkuat pun tak bisa menahanku lama.', 'I am light as a feather, yet the strongest man cannot hold me for long.'),
     ('Napas', 'Breath'), ('Asap', 'Smoke'), ('Kapas', 'Cotton')),
    (('Aku punya kota tanpa rumah, hutan tanpa pohon, dan sungai tanpa air.', 'I have cities without houses, forests without trees, and rivers without water.'),
     ('Peta', 'A map'), ('Mimpi', 'A dream'), ('Lukisan', 'A painting')),
    (('Kau harus memecahkanku sebelum bisa memakaiku.', 'You must break me before you can use me.'),
     ('Telur', 'An egg'), ('Batu mentah', 'A raw stone'), ('Kendi', 'A jar')),
    (('Aku selalu ada di depanmu, tapi tak pernah bisa kau lihat.', 'I am always in front of you, yet you can never see me.'),
     ('Masa depan', 'The future'), ('Hidungmu', 'Your nose'), ('Bayanganmu', 'Your shadow')),
    (('Apa yang selalu naik tapi tak pernah turun?', 'What always goes up but never comes down?'),
     ('Umur', 'Your age'), ('Asap', 'Smoke'), ('Lentera langit', 'A sky lantern')),
    (('Aku punya leher tapi tak punya kepala.', 'I have a neck but no head.'),
     ('Kendi', 'A jar'), ('Jubah', 'A robe'), ('Gunung', 'A mountain')),
    (('Semakin banyak yang kukeringkan, semakin basah diriku.', 'The more I dry, the wetter I become.'),
     ('Kain lap', 'A towel'), ('Matahari', 'The sun'), ('Angin', 'The wind')),
    (('Pagi berjalan dengan empat kaki, siang dengan dua, sore dengan tiga.', 'Four legs in the morning, two at noon, three in the evening.'),
     ('Manusia', 'A person'), ('Kuda', 'A horse'), ('Bangau', 'A crane')),
    (('Aku punya satu mata, tapi tak bisa melihat apa pun.', 'I have one eye, yet I can see nothing.'),
     ('Jarum', 'A needle'), ('Lao Shi', 'Lao Shi'), ('Badai', 'A storm')),
    (('Tak punya sayap tapi terbang, tak punya mata tapi menangis.', 'No wings, yet I fly; no eyes, yet I cry.'),
     ('Awan', 'A cloud'), ('Lentera langit', 'A sky lantern'), ('Burung', 'A bird')),
    (('Aku datang di malam hari tanpa dipanggil dan hilang di siang hari tanpa dicuri.', 'I come at night uncalled and vanish by day unstolen.'),
     ('Bintang', 'The stars'), ('Mimpi', 'A dream'), ('Pencuri', 'A thief')),
    (('Aku membawa rumahku di punggung ke mana pun aku pergi.', 'I carry my house on my back wherever I go.'),
     ('Kura-kura', 'A tortoise'), ('Pedagang keliling', 'A peddler'), ('Pertapa', 'A hermit')),
    (('Beri aku makan dan aku hidup; beri aku minum dan aku mati.', 'Feed me and I live; give me a drink and I die.'),
     ('Api', 'Fire'), ('Ikan', 'A fish'), ('Pohon', 'A tree')),
    (('Aku memenuhi seluruh ruangan, tapi tak memakan tempat sedikit pun.', 'I fill a whole room yet take up no space at all.'),
     ('Cahaya', 'Light'), ('Air', 'Water'), ('Asap', 'Smoke')),
    (('Aku tidak menangis saat dipotong, tapi orang yang memotongku menangis.', 'I do not weep when I am cut, but the one who cuts me does.'),
     ('Bawang', 'An onion'), ('Batu bermata darah', 'A blood-eyed stone'), ('Kayu', 'Wood')),
    (('Aku keras seperti batu, lahir dari lumpur, dan memberi minum seluruh rumah.', 'I am hard as stone, born of mud, and give water to the whole house.'),
     ('Kendi tanah liat', 'A clay jar'), ('Sumur', 'A well'), ('Giok', 'Jade')),
    (('Sebut namaku, maka kau memecahkanku.', 'Speak my name and you break me.'),
     ('Keheningan', 'Silence'), ('Janji', 'A promise'), ('Sumpah', 'An oath')),
    (('Kalau kau punya aku, kau ingin membaginya; kalau kau membaginya, aku hilang.', 'If you have me, you want to share me; if you share me, I am gone.'),
     ('Rahasia', 'A secret'), ('Bakpao', 'A bun'), ('Utang', 'A debt')),
    (('Siang hari aku bersembunyi di langit, malam hari aku menerangi jalan para pengembara.',
      'By day I hide in the sky; by night I light the road for travellers.'),
     ('Bulan', 'The moon'), ('Lentera', 'A lantern'), ('Kunang-kunang', 'A firefly')),
    (('Aku punya banyak gigi, tapi tak pernah menggigit.', 'I have many teeth, yet I never bite.'),
     ('Sisir', 'A comb'), ('Harimau', 'A tiger'), ('Anjing penjaga', 'A guard dog')),
    (('Ditanam tanpa benih, tumbuh tanpa air, dipanen dengan pedang.', 'Planted without seed, grown without water, harvested with a sword.'),
     ('Dendam', 'A grudge'), ('Padi', 'Rice'), ('Bambu', 'Bamboo')),
    (('Pengemis memilikinya, orang kaya membutuhkannya, dan kalau kau memakannya kau mati.',
      'The beggar has it, the rich man needs it, and if you eat it you die.'),
     ('Tidak ada apa-apa', 'Nothing'), ('Uang', 'Money'), ('Racun', 'Poison')),
    (('Aku berjalan tanpa kaki dari timur ke barat setiap hari, dan tak pernah lelah.', 'Every day I walk from east to west without feet, and never tire.'),
     ('Matahari', 'The sun'), ('Angin', 'The wind'), ('Sungai', 'A river')),
    (('Semakin tua aku, semakin berharga; orang menguburku hanya untuk menunggu.', 'The older I get, the more I am worth; people bury me just to wait.'),
     ('Arak', 'Wine'), ('Kitab', 'A book'), ('Pedang', 'A sword')),
    (('Aku tidak bertambah berat sedikit pun, tapi semakin lama kau membawaku, semakin berat aku.',
      'I never gain an ounce, yet the longer you carry me, the heavier I become.'),
     ('Beban pikiran', 'A worry'), ('Batu', 'A stone'), ('Pedang', 'A sword')),
    (('Aku punya kepala dan ekor, tapi tak punya tubuh.', 'I have a head and a tail, but no body.'),
     ('Koin', 'A coin'), ('Ular', 'A snake'), ('Naga', 'A dragon')),
    (('Aku punya banyak daun, tapi aku bukan tanaman.', 'I have many leaves, yet I am no plant.'),
     ('Kitab', 'A book'), ('Kipas', 'A fan'), ('Payung', 'An umbrella')),
    (('Aku bisa pecah tanpa disentuh siapa pun.', 'I can break without anyone touching me.'),
     ('Janji', 'A promise'), ('Cermin', 'A mirror'), ('Mangkuk', 'A bowl')),
    (('Aku masuk ke air, tapi tak pernah basah.', 'I go into the water, yet I am never wet.'),
     ('Bayangan', 'A shadow'), ('Ikan', 'A fish'), ('Batu', 'A stone')),
    (('Aku berteriak tanpa mulut dan menjawab setiap kali dipanggil.', 'I shout without a mouth and answer whenever I am called.'),
     ('Gema', 'An echo'), ('Lonceng', 'A bell'), ('Burung', 'A bird')),
    (('Kau membeliku untuk makan, tapi tak pernah memakanku.', 'You buy me to eat, yet you never eat me.'),
     ('Mangkuk', 'A bowl'), ('Bakpao', 'A bun'), ('Garam', 'Salt')),
    (('Lima saudara tinggal di satu rumah, panjangnya tak ada yang sama.', 'Five brothers live in one house, and no two are the same length.'),
     ('Jari tangan', 'Fingers'), ('Batang bambu', 'Bamboo stalks'), ('Tusuk sate', 'Skewers')),
    (('Tujuh warnaku datang setelah hujan, tapi tak seorang pun bisa memegangku.', 'My seven colours come after rain, yet no one can hold me.'),
     ('Pelangi', 'A rainbow'), ('Kain sutra', 'Silk cloth'), ('Kupu-kupu', 'A butterfly')),
    (('Aku memotong tanpa bilah dan menyembuhkan tanpa obat.', 'I cut without a blade and heal without medicine.'),
     ('Waktu', 'Time'), ('Air', 'Water'), ('Angin', 'The wind')),
    (('Semakin lama aku hidup, semakin pendek tubuhku.', 'The longer I live, the shorter I grow.'),
     ('Lilin', 'A candle'), ('Bambu', 'Bamboo'), ('Tongkat', 'A walking stick')),
    (('Aku diberikan kepadamu, tapi orang lainlah yang lebih sering memakaiku.', 'I was given to you, yet others use me far more than you do.'),
     ('Namamu', 'Your name'), ('Jubahmu', 'Your robe'), ('Gelarmu', 'Your title')),
    (('Aku terbang tanpa sayap dan berdoa tanpa suara, dilepas ke langit di malam purnama.',
      'I fly without wings and pray without a voice, released into the sky on a full-moon night.'),
     ('Lentera langit', 'A sky lantern'), ('Burung', 'A bird'), ('Awan', 'A cloud')),
    (('Bulat seperti bulan, manis seperti madu, dibagi bersama keluarga di malam purnama.',
      'Round as the moon, sweet as honey, shared with family on a full-moon night.'),
     ('Kue bulan', 'A mooncake'), ('Bakpao', 'A bun'), ('Lentera', 'A lantern')),
]


def isi(g):
    K = g.K
    for i, row in enumerate(UMUM):
        teks = (row[0], row[1])
        K('umum', f'umum_{i:02d}', 'umum', teks, bobot=10, umur=99, penyebar=row[2], isian=row[3] if len(row) > 3 else ())
    for f, kid, pemicu, teks in DUNIA:
        K(f, kid, pemicu, teks, bobot=10, umur=5)
    for acara, teks in ACARA:
        K('acara', f'acara_{acara}', f'acara_{acara}', teks, bobot=10, umur=2)
    for aksi, teks in SEKTE:
        K('sekte', f'sekte_{aksi}', f'sekte_{aksi}', teks, bobot=10, umur=7)
    K('sekte', 'sekte_turnamen', 'sekte_turnamen', ('Turnamen sekte sudah selesai. Juaranya dielu-elukan di setiap kedai teh, yang kalah '
                                                    'berlatih sampai tengah malam.',
                                                    'The sect tournament is over. The champion is toasted in every teahouse; the losers '
                                                    'train until midnight.'), bobot=10, umur=5)
    for quest, teks in QUEST:
        K('quest', f'quest_{quest}', f'quest_{quest}', teks, bobot=10, umur=6)
    # every inheritance chain gets its own completion rumour (trigger quest_warisan_<dao>_<tier>)
    import cerita_isi_warisan as w
    for (dao, tier), a in w.LELUHUR.items():
        K('warisan', f'quest_warisan_{dao}_{tier}', f'quest_warisan_{dao}_{tier}',
          (f'Ada yang mewarisi peninggalan {a["nama"]}, {a["gelar"][0]}, dari sebuah makam kuno. Kata orang namanya %s.',
           f'Someone inherited the legacy of {a["nama"]}, {a["gelar"][1]}, from an ancient tomb. They say the name is %s.'),
          bobot=10, umur=8)
    for kid, teks in PERBAIKAN:
        K('perbaikan', kid, kid.replace('.', '_'), teks, bobot=10, umur=5)
    for n, (soal, benar, salah1, salah2) in enumerate(TEKA):
        g.tx('tempat_cerita', f'kultivator.cerita.teka.{n}.soal', soal)
        g.tx('tempat_cerita', f'kultivator.cerita.teka.{n}.benar', benar)
        g.tx('tempat_cerita', f'kultivator.cerita.teka.{n}.salah1', salah1)
        g.tx('tempat_cerita', f'kultivator.cerita.teka.{n}.salah2', salah2)
    if len(TEKA) != 40:
        g.ERRORS.append(f'lantern riddles: {len(TEKA)}, CeritaApi.TEKA_TEKI is 40')
