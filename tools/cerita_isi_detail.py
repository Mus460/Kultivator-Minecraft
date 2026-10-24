"""38ST detail pass (W3, D-ST): texts the W1 content left out.
  - closing lines after a story bout (AkibatDasar mulai_duel through TandingTempat)
  - subtitles of the harbour-theme scene cards (AdeganCerita recipes for tema_pelabuhan_*)
  - Sumber lines of the capital services and events (SumberLayanan; DESIGN 38.B.3 "Baris Sumber per bangunan layanan")
  - the capital rumours 38KS spreads by template (kota_besar.*), curfew / morning bell / harvest festival rumours, and
    background talk of the capital districts
Indonesian first, English second. Every Sumber line names real works or history and ends with "rancangan game"."""

KOTA_BESAR = ['warga', 'pemilik_teh', 'pemilik_penginapan', 'koki', 'pelayan', 'kuli', 'petugas_gilda', 'anak', 'pengemis',
              'penjaga_gerbang', 'pedagang_senjata', 'pedagang_pil', 'pustakawan', 'tabib', 'pemotong_batu']
PENJAGA = ['penjaga', 'penjaga_gerbang', 'kapten_penjaga', 'pengawal_istana']
DESA = ['petani', 'kepala_desa', 'tetua_desa', 'anak', 'pandai_desa', 'nelayan', 'pedagang_keliling']

ENGINE = {
    'kultivator.cerita.sumber.awal': ('Sumber: %s', 'Source: %s'),
    # a story bout's closing line: <menang|kalah> = the body won / lost
    'kultivator.cerita.duel.kalah.iblis.0': ('Hah... hari ini kau beruntung. Bayanganku tidak akan lupa wajahmu.',
                                            'Hah... you were lucky today. My shadow will not forget your face.'),
    'kultivator.cerita.duel.kalah.iblis.1': ('Darahku menetes di lantai orang lain. Ingat ini: jalan iblis tidak pernah berutang lama.',
                                            'My blood drips on another\'s floor. Remember: the demonic path never stays in debt for long.'),
    'kultivator.cerita.duel.kalah.iblis.2': ('Bunuh aku kalau berani! ...Tidak? Lemah. Itulah sebabnya kalian selalu kalah pada akhirnya.',
                                            'Kill me if you dare! ...No? Soft. That is why your kind always loses in the end.'),
    'kultivator.cerita.duel.menang.iblis.0': ('Lihat dirimu, berlutut di debu. Pulanglah, dan ceritakan pada gurumu siapa yang mengampunimu.',
                                             'Look at you, kneeling in the dust. Go home, and tell your master who spared you.'),
    'kultivator.cerita.duel.menang.iblis.1': ('Qi-mu manis. Sayang aku sedang tidak lapar.', 'Your qi is sweet. A pity I am not hungry today.'),
    'kultivator.cerita.duel.menang.iblis.2': ('Itu saja? Kembalilah setelah kau belajar membenci.', 'Is that all? Come back once you have learned to hate.'),
    'kultivator.cerita.duel.kalah.muda.0': ('Aku... kalah? Tidak, aku hanya lengah! Lain kali tidak akan begini!',
                                           'I... lost? No, I was careless! Next time will be different!'),
    'kultivator.cerita.duel.kalah.muda.1': ('Jurusmu... aku belum pernah melihat yang seperti itu. Ajari aku? Eh, lupakan. Aku tidak bilang apa-apa.',
                                           'That form of yours... I have never seen anything like it. Teach me? Uh, forget it. I said nothing.'),
    'kultivator.cerita.duel.kalah.muda.2': ('Baik. Kuakui kau lebih kuat. Hari ini. Hanya hari ini.', 'Fine. I admit you are stronger. Today. Only today.'),
    'kultivator.cerita.duel.menang.muda.0': ('Ha! Sudah kubilang! Pulang dan berlatihlah sepuluh tahun lagi!',
                                            'Ha! I told you! Go home and train for ten more years!'),
    'kultivator.cerita.duel.menang.muda.1': ('Lumayan, untuk orang luar. Kau boleh menantangku lagi kalau sudah tidak gemetar.',
                                            'Not bad, for an outsider. You may challenge me again once you stop shaking.'),
    'kultivator.cerita.duel.menang.muda.2': ('Jangan sedih. Kalah dariku bukan aib; semua orang kalah dariku.',
                                            'Do not be sad. Losing to me is no shame; everyone loses to me.'),
    'kultivator.cerita.duel.kalah.tua.0': ('Bagus. Tanganmu jujur, langkahmu tenang. Tulang tuaku senang dikalahkan dengan cara begini.',
                                          'Good. Your hands are honest, your steps calm. These old bones are glad to be beaten like this.'),
    'kultivator.cerita.duel.kalah.tua.1': ('Aku kalah. Jangan membungkuk terlalu dalam; nanti aku malu dua kali.',
                                          'I lose. Do not bow too deeply; you will shame me twice.'),
    'kultivator.cerita.duel.kalah.tua.2': ('Hmm. Gelombang yang belakang mendorong yang di depan. Begitulah seharusnya.',
                                          'Hmm. The waves behind push the waves ahead. That is how it should be.'),
    'kultivator.cerita.duel.menang.tua.0': ('Terlalu tergesa. Pedang yang marah selalu sampai lebih dulu, dan selalu kalah lebih dulu.',
                                           'Too hasty. An angry sword always arrives first, and always loses first.'),
    'kultivator.cerita.duel.menang.tua.1': ('Bangunlah. Kekalahan adalah guru yang mahal; jangan sia-siakan pelajarannya.',
                                           'Get up. Defeat is an expensive teacher; do not waste its lesson.'),
    'kultivator.cerita.duel.menang.tua.2': ('Kau punya bakat. Yang belum kau punya adalah kesabaran. Itu tumbuh lebih lambat dari bakat.',
                                           'You have talent. What you lack is patience. It grows slower than talent.'),
    # harbour-theme scene subtitles (titles are 38TK-P's keys kultivator.cerita.adegan.tema_pelabuhan_*)
    'kultivator.cerita.adegan.tema_pelabuhan_lintas.sub': ('Layar jung menghilang di balik kabut', 'The junk\'s sails vanish into the fog'),
    'kultivator.cerita.adegan.tema_pelabuhan_dewi.sub': ('Tandu Dewi Laut diarak ke dermaga', 'The Sea Goddess\'s palanquin is carried to the quay'),
    'kultivator.cerita.adegan.tema_pelabuhan_bajak.sub': ('Genderang tanda bahaya dari menara suar', 'Alarm drums from the lighthouse'),
    'kultivator.cerita.adegan.tema_pelabuhan_heisha.sub': ('Bendera hiu hitam di atas buritan', 'The black shark flag above the stern'),
    'kultivator.cerita.adegan.tema_pelabuhan_pelita.sub': ('Cahaya kembali menuntun kapal pulang', 'The light guides the ships home again'),
}

# Sumber lines of the capital (fragment tempat_lore; keys kultivator.kota_besar.<id>.inspired). Services are shown once
# per player when talking to the service's NPC in a capital, events once per player when the event starts nearby.
SUMBER = {
    'rumah_uang': ('Sumber: Terinspirasi dari "uang terbang" (feiqian) dinasti Tang, surat wesel yang bisa ditukar di kota lain, dan '
                   'rumah uang (qianzhuang) dinasti Ming-Qing; rancangan game.',
                   'Source: Inspired by the "flying money" (feiqian) of the Tang dynasty, notes that could be cashed in another city, '
                   'and the money houses (qianzhuang) of the Ming and Qing; game design.'),
    'judi_batu': ('Sumber: Terinspirasi dari judi batu sumber dalam Shrouded the Heavens (Zhe Tian) karya Chen Dong; rancangan game.',
                  'Source: Inspired by the source-stone gambling of Shrouded the Heavens (Zhe Tian) by Chen Dong; game design.'),
    'gilda_alkemis': ('Sumber: Terinspirasi dari Persekutuan Alkemis dan Pertemuan Pil di Battle Through the Heavens; rancangan game.',
                      'Source: Inspired by the Alchemist Association and the Pill Meeting of Battle Through the Heavens; game design.'),
    'gilda_tempa': ('Sumber: Terinspirasi dari Ou Yezi, empu pedang zaman Musim Semi dan Gugur yang menurut legenda menempa pedang '
                    'Zhanlu dan Longyuan, dan dari paviliun pandai pusaka dalam novel kultivasi; rancangan game.',
                    'Source: Inspired by Ou Yezi, the Spring and Autumn period swordsmith who in legend forged the Zhanlu and '
                    'Longyuan swords, and by the artifact forges of cultivation novels; game design.'),
    'gilda_kultivator': ('Sumber: Terinspirasi dari gilda pemburu dengan papan misi berpangkat dan daftar peringkat jenius muda yang '
                         'sering muncul dalam novel xianxia; rancangan game.',
                         'Source: Inspired by the hunters\' guilds with ranked mission boards and the rankings of young prodigies '
                         'that appear in many xianxia novels; game design.'),
    'balai_tabib': ('Sumber: Terinspirasi dari Apotek Kebajikan Rakyat (Huimin Ju) yang didirikan pemerintah dinasti Song untuk '
                    'menjual obat murah, dan dari balai pengobatan sekte dalam novel kultivasi; rancangan game.',
                    'Source: Inspired by the Benevolent Pharmacies (Huimin Ju) the Song government set up to sell cheap medicine, '
                    'and by the healing halls of sects in cultivation novels; game design.'),
    'pasar_binatang': ('Sumber: Terinspirasi dari pasar burung dan bunga di kota-kota Tiongkok lama dan dari perdagangan anak '
                       'binatang roh dalam novel xianxia; rancangan game.',
                       'Source: Inspired by the bird-and-flower markets of old Chinese cities and the trade in spirit beast cubs '
                       'in xianxia novels; game design.'),
    'toko_senjata': ('Sumber: Terinspirasi dari jalan pandai besi dan toko senjata dalam kisah wuxia, tempat pendekar memilih bilah '
                     'sebelum berkelana; rancangan game.',
                     'Source: Inspired by the smiths\' streets and weapon shops of wuxia stories, where swordsmen choose a blade '
                     'before setting out; game design.'),
    'perpustakaan': ('Sumber: Terinspirasi dari Paviliun Tianyi di Ningbo, perpustakaan pribadi tertua di Tiongkok yang masih berdiri '
                     '(dinasti Ming), dan dari paviliun kitab sekte dalam novel kultivasi; rancangan game.',
                     'Source: Inspired by the Tianyi Pavilion in Ningbo, the oldest surviving private library in China (Ming '
                     'dynasty), and by the scripture pavilions of sects in cultivation novels; game design.'),
    'kuil_langit': ('Sumber: Terinspirasi dari aula Tiga Kemurnian (Sanqing) di kuil-kuil Tao dan tungku dupa besar di halamannya; '
                    'rancangan game.',
                    'Source: Inspired by the Hall of the Three Pure Ones (Sanqing) of Taoist temples and the great incense burners '
                    'in their courtyards; game design.'),
    'dermaga_langit': ('Sumber: Terinspirasi dari kapal terbang dan perahu roh dalam novel kultivasi, dan dari dermaga ramai Kaifeng '
                       'dalam lukisan "Sepanjang Sungai saat Festival Qingming"; rancangan game.',
                       'Source: Inspired by the flying ships and spirit boats of cultivation novels, and the busy quays of Kaifeng '
                       'in the painting "Along the River During the Qingming Festival"; game design.'),
    'stasiun_teleport': ('Sumber: Terinspirasi dari formasi teleportasi kuno dalam A Record of a Mortal\'s Journey to Immortality '
                         '(Fanren Xiuxian Zhuan) karya Wang Yu dan novel kultivasi lain; rancangan game.',
                         'Source: Inspired by the ancient teleportation arrays of A Record of a Mortal\'s Journey to Immortality '
                         '(Fanren Xiuxian Zhuan) by Wang Yu and other cultivation novels; game design.'),
    'yamen': ('Sumber: Terinspirasi dari kantor prefektur (yamen) Kaifeng tempat hakim Bao Zheng mengadili, dan genderang pengaduan '
              '(dengwen gu) yang boleh ditabuh rakyat untuk meminta keadilan; rancangan game.',
              'Source: Inspired by the Kaifeng prefecture office (yamen) where Judge Bao Zheng held court, and the grievance drum '
              '(dengwen gu) commoners could beat to ask for justice; game design.'),
    'gerbang': ('Sumber: Terinspirasi dari jam malam Chang\'an dinasti Tang: genderang jalan yang ditabuh saat senja menutup gerbang '
                'kota dan petak-petaknya, lalu dibuka lagi saat fajar; rancangan game.',
                'Source: Inspired by the curfew of Tang dynasty Chang\'an: the street drums beaten at dusk closed the city gates and '
                'its wards, which opened again at dawn; game design.'),
    'penginapan': ('Sumber: Terinspirasi dari penginapan (kezhan) dalam kisah wuxia, tempat para pendekar bertemu, bertengkar dan '
                   'mendengar kabar; rancangan game.',
                   'Source: Inspired by the inns (kezhan) of wuxia stories, where swordsmen meet, quarrel and hear the news; game '
                   'design.'),
    'rumah_makan': ('Sumber: Terinspirasi dari rumah makan Kaifeng dinasti Song yang dilukiskan Meng Yuanlao dalam "Dongjing Meng '
                    'Hua Lu"; rancangan game.',
                    'Source: Inspired by the eating houses of Song dynasty Kaifeng described by Meng Yuanlao in "Dongjing Meng Hua '
                    'Lu"; game design.'),
    'arena': ('Sumber: Terinspirasi dari panggung lei tai tempat para petarung Tiongkok lama menjawab tantangan; rancangan game.',
              'Source: Inspired by the lei tai platforms where fighters of old China answered challenges; game design.'),
    # events (TempatEvents.ACARA ids of the capital calendar)
    'gelombang_binatang': ('Sumber: Terinspirasi dari gelombang binatang buas (shou chao) yang menyerang kota dalam banyak novel '
                           'xianxia; rancangan game.',
                           'Source: Inspired by the beast tides (shou chao) that assault cities in many xianxia novels; game design.'),
    'pertemuan_pil': ('Sumber: Terinspirasi dari Pertemuan Pil (Dan Hui) dalam Battle Through the Heavens karya Tian Can Tu Dou; '
                      'rancangan game.',
                      'Source: Inspired by the Pill Meeting (Dan Hui) of Battle Through the Heavens by Tian Can Tu Dou; game design.'),
    'lelang_agung': ('Sumber: Terinspirasi dari Rumah Lelang Mi Te dalam Battle Through the Heavens dan balai lelang harta dalam '
                     'banyak novel kultivasi; rancangan game.',
                     'Source: Inspired by the Mittel Auction House of Battle Through the Heavens and the treasure auctions of many '
                     'cultivation novels; game design.'),
    'turnamen_agung': ('Sumber: Terinspirasi dari panggung lei tai Tiongkok lama dan turnamen antar-sekte dalam novel xianxia; '
                       'rancangan game.',
                       'Source: Inspired by the lei tai platforms of old China and the inter-sect tournaments of xianxia novels; '
                       'game design.'),
    'hari_gerbang': ('Sumber: Terinspirasi dari ujian akar roh dan hari penerimaan murid sekte dalam A Record of a Mortal\'s Journey '
                     'to Immortality dan novel kultivasi lain; rancangan game.',
                     'Source: Inspired by the spirit-root tests and sect recruitment days of A Record of a Mortal\'s Journey to '
                     'Immortality and other cultivation novels; game design.'),
    'pertemuan_sekte': ('Sumber: Terinspirasi dari pertemuan para ketua perguruan dalam kisah wuxia, seperti pertemuan Persekutuan '
                        'Pedang Lima Gunung di Gunung Song dalam The Smiling, Proud Wanderer karya Jin Yong; rancangan game.',
                        'Source: Inspired by the gatherings of school leaders in wuxia stories, such as the Five Mountains Sword '
                        'Alliance meeting on Mount Song in The Smiling, Proud Wanderer by Jin Yong; game design.'),
    'pasar_malam': ('Sumber: Terinspirasi dari pasar malam Kaifeng dinasti Song yang buka sampai dini hari, seperti dicatat dalam '
                    '"Dongjing Meng Hua Lu"; rancangan game.',
                    'Source: Inspired by the night markets of Song dynasty Kaifeng that stayed open until the small hours, as '
                    'recorded in "Dongjing Meng Hua Lu"; game design.'),
}

# rumours of the capital events 38KS spreads by template id (CeritaApi.kabar falls back to the trigger, so each id is a
# trigger with several variants); %s = the argument 38KS passes (city name / player name / seat count)
KOTA_BESAR_KABAR = {
    'kota_besar.festival': [
        ('Festival Lentera di %s malam ini! Gadis-gadis sudah melipat lentera teratai sejak subuh, dan pemuda-pemuda pura-pura tidak memperhatikan.',
         'Lantern Festival in %s tonight! The girls have been folding lotus lanterns since dawn, and the young men pretend not to notice.'),
        ('Kata penjaga teka-teki, tahun ini ada lentera yang belum pernah dijawab siapa pun selama tiga festival di %s.',
         'The riddle keeper says this year there is a lantern in %s nobody has answered in three festivals.'),
        ('Jalan Lentera di %s terang seperti siang. Pencopet juga senang; jaga kantongmu.',
         'Lantern Street in %s is bright as day. The pickpockets are happy too; mind your purse.'),
    ],
    'kota_besar.pertahanan_menang': [
        ('Gelombang binatang dipukul mundur dari tembok %s! Tukang daging di pasar menjual taring sebagai jimat; separuhnya taring babi.',
         'The beast tide was driven back from the walls of %s! The butchers sell fangs as charms; half of them are pig teeth.'),
        ('Para penjaga %s minum sampai pagi di menara genderang. Kapten mereka tidak minum; ia menghitung siapa yang tidak pulang.',
         'The guards of %s drank till dawn in the drum tower. Their captain did not drink; he counted who did not come home.'),
        ('Tembok %s bertahan lagi. Kata kakek-kakek, temboknya dibangun dari batu tujuh gunung, jadi binatang buas mencium bau tujuh sekte.',
         'The walls of %s held again. The old men say they were built of stone from seven mountains, so the beasts smell seven sects.'),
    ],
    'kota_besar.kota_terluka': [
        ('Gelombang binatang menembus gerbang %s. Toko-toko tutup sehari, dan Pasar Gelap tiba-tiba menjual obat luka dua kali lipat.',
         'The beast tide broke through the gates of %s. The shops closed for a day, and the Black Market suddenly sells wound salve at double price.'),
        ('Tiga atap di %s runtuh diinjak binatang roh. Tukang-tukang bilang mereka akan memperbaikinya; para penjaga bilang lain kali mereka akan menahan di tembok.',
         'Three roofs in %s were crushed by spirit beasts. The craftsmen say they will fix them; the guards say next time they will hold the wall.'),
        ('Lonceng berkabung berbunyi di %s. Pangeran Penjaga menyalakan dupa sendiri di Tugu Pendiri.',
         'The mourning bell rings in %s. The Warden Prince lit the incense himself at the Founders\' Monument.'),
    ],
    'kota_besar.hari_gerbang': [
        ('Hari Pembukaan Gerbang di %s! Sekte-sekte membuka lapak, dan setiap ibu menyisir rambut anaknya tiga kali.',
         'Gate Opening Day in %s! The sects set up their stalls, and every mother combs her child\'s hair three times.'),
        ('Kata orang, tahun lalu di %s seorang anak penjual tahu ternyata punya akar roh langit. Sekarang semua penjual tahu membawa anaknya.',
         'They say last year in %s a tofu seller\'s child had a heavenly spirit root. Now every tofu seller brings their child.'),
        ('Antrean ujian akar roh di %s mengular sampai jalan raya. Penjual kue beras sudah pulang dengan keranjang kosong sebelum tengah hari.',
         'The spirit-root queue in %s snakes all the way to the main road. The rice-cake sellers went home with empty baskets before noon.'),
    ],
    'kota_besar.buronan': [
        ('Wajah %s ditempel di papan Yamen. Hakim Bao sendiri yang menandatangani suratnya.',
         '%s\'s face is posted on the Yamen board. Magistrate Bao signed the warrant himself.'),
        ('Penjaga gerbang diberi lukisan wajah %s. Mereka memandanginya lama-lama; pelukis Yamen terkenal suka melebih-lebihkan hidung.',
         'The gate guards were given a portrait of %s. They stare at it a long time; the Yamen painter is known for exaggerating noses.'),
        ('Kalau kau melihat %s, jangan ikut campur. Ada hadiah dari Yamen, tapi juga ada orang yang tidak pulang setelah mencoba menangkapnya.',
         'If you see %s, stay out of it. The Yamen offers a reward, but some who tried to catch them never came home.'),
    ],
    'kota_besar.tamu_istana': [
        ('%s diterima dalam audiensi di Istana Pengawal Kota! Para menteri berbisik di balik lengan baju mereka.',
         '%s was received in audience at the Warden\'s Palace! The ministers whisper behind their sleeves.'),
        ('Kata pelayan istana, %s minum teh dengan Pangeran Penjaga dan tidak menumpahkan setetes pun. Itu lebih sulit dari kedengarannya.',
         'The palace servants say %s drank tea with the Warden Prince and did not spill a drop. That is harder than it sounds.'),
    ],
    'kota_besar.juara_pil': [
        ('%s menang di Pertemuan Pil! Tetua Dan Chen mengangguk dua kali, dan para alkemis tua pulang dengan wajah masam.',
         '%s won the Pill Meeting! Elder Dan Chen nodded twice, and the old alchemists went home sour-faced.'),
        ('Asap pil %s berwarna tujuh lapis, kata saksi di tribun. Kata saksi lain, lima. Semua setuju baunya harum.',
         'The smoke of %s\'s pill had seven layers of colour, say witnesses in the stands. Another says five. All agree it smelled sweet.'),
        ('Para murid Gilda Alkemis menyalin catatan %s diam-diam. Tetua mereka pura-pura tidak melihat.',
         'The Alchemist Guild apprentices are copying %s\'s notes in secret. Their elders pretend not to see.'),
    ],
    'kota_besar.pertemuan_sekte': [
        ('Utusan sekte berkumpul di Balai Pertemuan %s; %s kursi terisi. Penjaga istana bilang kursi yang kosong lebih berisik dari yang terisi.',
         'Sect envoys gather in the Meeting Hall of %s; %s seats are filled. The palace guards say the empty seats are louder than the full ones.'),
        ('Pertemuan Sekte di %s: %s utusan, satu meja bundar, tidak ada yang mau duduk paling dekat pintu.',
         'The Sect Meeting in %s: %s envoys, one round table, and nobody willing to sit nearest the door.'),
    ],
    'kota_besar.juara_turnamen': [
        ('%s juara Turnamen Agung! Pedagang kacang di Arena menjual habis dagangannya dua kali.',
         '%s is champion of the Grand Tournament! The peanut sellers at the Arena sold out twice.'),
        ('Nama %s diukir di batu juara Arena Agung. Pemahatnya mengeluh; namanya panjang dan batunya keras.',
         '%s\'s name is carved on the Grand Arena\'s champion stone. The carver grumbles; the name is long and the stone is hard.'),
        ('Anak-anak di gang bermain "jadi %s" dengan pedang kayu. Yang kalah harus jadi lawannya.',
         'The children in the alleys play "being %s" with wooden swords. The loser has to play the opponent.'),
    ],
}

ACARA_LAIN = [
    ('acara_jam_malam', ('Genderang jam malam sudah berbunyi. Yang masih di jalan sebaiknya punya alasan bagus, dan lentera.',
                         'The curfew drums have sounded. Whoever is still in the street had better have a good reason, and a lantern.'), PENJAGA + KOTA_BESAR),
    ('acara_jam_malam', ('Kata penjaga gerbang, tiap malam ada saja kultivator yang mencoba melompati tembok setelah genderang. Tiap pagi mereka ada di sel.',
                         'The gate guards say every night some cultivator tries to leap the wall after the drums. Every morning they are in a cell.'), PENJAGA + KOTA_BESAR),
    ('acara_lonceng_pagi', ('Lonceng pagi! Gerbang dibuka, pedagang sayur berebut masuk, dan kucing-kucing pasar sudah menunggu ikan.',
                            'The morning bell! The gates open, the vegetable sellers jostle in, and the market cats are already waiting for fish.'), KOTA_BESAR),
    ('acara_lonceng_pagi', ('Penjaga lonceng sudah memukul lonceng pagi selama empat puluh tahun. Katanya ia bisa memukulnya sambil tidur, dan kadang memang begitu.',
                            'The bell keeper has struck the morning bell for forty years. He says he can do it asleep, and sometimes he does.'), KOTA_BESAR),
    ('acara_festival_panen', ('Festival Panen! Lumbung penuh, gendang berbunyi, dan kepala desa menari walau lututnya protes.',
                              'Harvest Festival! The granary is full, the drums are playing, and the village head dances though his knees protest.'), DESA),
    ('acara_festival_panen', ('Ada lomba memikul padi di lapangan desa. Juara tahun lalu anak kepala desa, juara tahun ini katanya kerbau.',
                              'There is a rice-carrying race on the village green. Last year\'s champion was the village head\'s son; this year, they say, a buffalo.'), DESA),
    ('acara_festival_panen', ('Persembahan panen pertama sudah diletakkan di kuil Dewa Tanah. Anak-anak dilarang mencicipi; mereka mencicipi juga.',
                              'The first harvest offering lies at the Earth God\'s shrine. The children are forbidden to taste it; they taste it anyway.'), DESA),
]

# background talk of the capital districts (trigger umum, spoken by capital roles)
UMUM_KB = [
    ('Di Pasar Timur ada kios yang menjual "air mata naga". Isinya air sumur dan garam. Laris.',
     'In the East Market a stall sells "dragon tears". It is well water and salt. It sells well.', KOTA_BESAR),
    ('Menara Pengamat Langit menyala semalaman. Para pengamat bilang ada bintang yang bergeser satu jengkal. Para pemabuk bilang semua bintang bergeser.',
     'The Sky Watch Tower was lit all night. The watchers say a star moved one hand\'s width. The drunks say every star moved.', KOTA_BESAR),
    ('Kalau mau lewat Distrik Istana, jangan menatap pengawal terlalu lama. Mereka tidak boleh tersenyum, dan mereka benci itu.',
     'If you pass through the Palace District, do not stare at the guards too long. They are not allowed to smile, and they hate it.',
     KOTA_BESAR + PENJAGA),
    ('Perpustakaan Agung menambah satu rak lagi. Tuan Wen menangis bahagia, lalu lupa rak itu di mana.',
     'The Grand Library added another shelf. Master Wen wept with joy, then forgot where it was.', KOTA_BESAR + ['pustakawan']),
    ('Kapal awan dari utara terlambat dua hari. Kata kuli dermaga, nakhodanya berhenti untuk memancing awan. Entah apa maksudnya.',
     'The cloud ship from the north is two days late. The dock porters say its captain stopped to fish for clouds. Whatever that means.',
     KOTA_BESAR + ['kuli', 'nakhoda']),
    ('Stasiun formasi teleportasi berdengung lebih keras dari biasa. Penjaganya bilang itu normal. Penjaganya juga bilang ia tidak gugup.',
     'The teleport array is humming louder than usual. Its keeper says that is normal. Its keeper also says he is not nervous.',
     KOTA_BESAR + ['penjaga_formasi']),
    ('Di Balai Tabib ada ranjang yang selalu kosong. Katanya tak ada yang pernah sembuh di ranjang itu, jadi tak ada yang mau tidur di sana.',
     'In the Healers\' Hall there is a bed that is always empty. They say nobody ever recovered in it, so nobody will lie there.',
     KOTA_BESAR + ['tabib']),
    ('Arena Agung sedang disapu. Para penyapu menemukan tiga gigi, dua cincin, dan satu pedang patah yang masih bergetar.',
     'The Grand Arena is being swept. The sweepers found three teeth, two rings and one broken sword that still trembles.',
     KOTA_BESAR + ['juara_sekte']),
    ('Anak-anak sekolah di Distrik Cendekia harus menghafal hukum pertama kota: "Di dalam tembok, pedang tidur." Mereka menghafalnya sambil berkelahi pakai sapu.',
     'The schoolchildren of the Scholars\' District must memorise the city\'s first law: "Within the walls, swords sleep." They recite it while fighting with brooms.',
     KOTA_BESAR + ['anak', 'penceramah_fana']),
    ('Pemotong batu di Rumah Batu Sumber bilang ia bisa mendengar batu yang berisi giok. Ia juga tuli sebelah.',
     'The stonecutter at the Source Stone House says he can hear which stones hold jade. He is also deaf in one ear.',
     KOTA_BESAR + ['pemotong_batu']),
    ('Paviliun Seribu Bilah memajang pedang yang katanya pernah memotong hujan. Pedangnya karatan, tapi harganya tidak.',
     'The Thousand Blades Pavilion displays a sword said to have cut the rain. The sword is rusty; the price is not.',
     KOTA_BESAR + ['pedagang_senjata']),
    ('Para menteri bertengkar soal pajak garam lagi. Gu Lianshan menang lagi. Pedagang garam merayakan, petani garam tidak.',
     'The ministers argued over the salt tax again. Gu Lianshan won again. The salt merchants celebrate; the salt farmers do not.',
     KOTA_BESAR + PENJAGA),
]


def isi(g):
    for key, pair in ENGINE.items():
        g.tx('tempat_cerita', key, pair)
    for sid, pair in SUMBER.items():
        g.tx('tempat_lore', f'kultivator.kota_besar.{sid}.inspired', pair)
        if not pair[0].startswith('Sumber:') or not pair[1].startswith('Source:'):
            g.ERRORS.append(f'sumber {sid}: must start with "Sumber:" / "Source:"')
    for pemicu, variants in KOTA_BESAR_KABAR.items():
        for n, teks in enumerate(variants, 1):
            penyebar = KOTA_BESAR + (PENJAGA if pemicu in ('kota_besar.buronan', 'kota_besar.pertahanan_menang') else [])
            g.K('kota_besar', f'{pemicu}.{n}', pemicu, teks, bobot=10, umur=4 if pemicu != 'kota_besar.buronan' else 6,
                penyebar=sorted(set(penyebar)))
    counts = {}
    for pemicu, teks, penyebar in ACARA_LAIN:
        counts[pemicu] = counts.get(pemicu, 0) + 1
        g.K('acara', f'{pemicu}_{counts[pemicu]}', pemicu, teks, bobot=10, umur=1, penyebar=sorted(set(penyebar)))
    for n, (i, e, penyebar) in enumerate(UMUM_KB):
        g.K('umum', f'umum_kb_{n:02d}', 'umum', (i, e), bobot=8, umur=99, penyebar=sorted(set(penyebar)))
