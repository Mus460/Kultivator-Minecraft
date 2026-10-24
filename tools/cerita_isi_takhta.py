"""38ST content: the capital arc "Bayangan di Balik Takhta" (DESIGN 38.S.3). Seven chapter quests (ids fixed by
AlurTakhta.QUEST), the dialogs that drive their stages, the lobbying and aftermath talk of the characters and the arc
rumours. A stage without goals waits for a dialog's quest_maju; a terminal stage uses the always-true goal "hari 0" so it
ends the moment it is entered. Every chapter has a deadline that leads to a quiet ending, never a dead end."""

Q1, Q2, Q3, Q4 = 'takhta_kedatangan', 'takhta_hilang_di_kumuh', 'takhta_pewaris_sakit', 'takhta_dua_calon'
Q5, Q6, Q7 = 'takhta_topeng_terlepas', 'takhta_malam_gerhana', 'takhta_penobatan'
SEGERA = ['hari 0']  # a goal that always holds: the stage ends as soon as it is entered


def tahap(q, s):
    return f'quest {q} tahap {s}'


def isi(g):
    D, N, P, T, Q, K = g.D, g.N, g.P, g.T, g.Q, g.K

    # ================================================================== chapter 1: Arrival
    Q(Q1, 'tokoh:tie_hu', 'ibu_kota', ('Bab 1 · Kedatangan', 'Chapter 1 · Arrival'),
      ('Tie Hu mencatat setiap wajah di gerbang sejak orang-orang mulai hilang. Hakim Bao menempel pengumuman di Yamen.',
       'Since people began to vanish, Tie Hu writes down every face at the gate. Magistrate Bao has posted a notice at the Yamen.'),
      [T('gerbang', ('Bicaralah dengan Kapten Tie Hu di gerbang ibu kota.', 'Speak with Captain Tie Hu at the capital gate.'),
         arah='kota/kota_besar'),
       T('yamen', ('Temui Hakim Bao Qing di Yamen dan baca pengumumannya.', 'Find Magistrate Bao Qing at the Yamen and read his notice.'),
         akibat=['nama_baik 2'],
         hasil=('Hakim Bao menulis namamu di antara orang yang bersedia menolong. Untuk pertama kalinya kota ini mengenalmu.',
                'Magistrate Bao wrote your name among those willing to help. For the first time, the city knows you.'))],
      alur=True)

    D('takhta1_tie_hu', 'tokoh:tie_hu', [
        N('mulai', ('Berhenti di sana. ...Ah, pendatang. Tulis namamu di buku gerbang. Sejak Kampung Kumuh kehilangan orang, aku '
                    'mencatat setiap wajah yang masuk.',
                    'Halt there. ...Ah, a newcomer. Write your name in the gate book. Since the slums started losing people, I note '
                    'every face that comes in.'),
          gestur='menulis', prop='kuas', suara='kotabesar.gate_welcome', pilihan=[
            P(('Orang hilang?', 'People missing?'), ke='hilang'),
            P(('(Menulis nama di buku gerbang)', '(Write your name in the gate book)'), ke='tulis')]),
        N('tulis', ('Tulisan jelek, nama bagus. Selamat datang di ibu kota. Ingat hukum pertama: di dalam tembok, pedang tidur.',
                    'Poor hand, good name. Welcome to the capital. Remember the first law: within the walls, swords sleep.'),
          gestur='berdiri_jaga', prop='tombak', pilihan=[
            P(('Kau menyebut orang hilang?', 'You mentioned missing people?'), ke='hilang')]),
        N('hilang', ('Tiga anak dan seorang penjual sayur, semua dalam sepekan. Atasanku bilang itu urusan Yamen, bukan gerbang. '
                     'Hakim Bao menempel pengumumannya pagi ini. Kalau kau mau berguna, temui dia.',
                     'Three children and a greengrocer, all in one week. My superiors say it is the Yamen\'s business, not the '
                     'gate\'s. Magistrate Bao posted a notice this morning. If you want to be useful, go see him.'),
          gestur='bisik', pilihan=[
            P(('Akan kutemui Hakim Bao.', 'I will see Magistrate Bao.'), akibat=[f'quest_maju {Q1}'])]),
    ], prioritas=50, syarat=[tahap(Q1, 'gerbang')])

    D('takhta1_bao', 'tokoh:bao_qing', [
        N('mulai', ('Kau membaca papan itu? Bagus. Kebanyakan orang hanya membaca harga beras. Yamen kekurangan tangan: penjaga '
                    'takut masuk gang Kampung Kumuh setelah gelap, dan para pengemis tidak mau bicara pada seragam.',
                    'You read the board? Good. Most people only read the price of rice. The Yamen is short of hands: the guards '
                    'fear the slum alleys after dark, and the beggars will not speak to a uniform.'),
          gestur='sikap_lipat_tangan', pilihan=[
            P(('Kenapa penjaga takut?', 'Why are the guards afraid?'), ke='takut'),
            P(('Aku akan membantu.', 'I will help.'), ke='bantu')]),
        N('takut', ('Karena dua dari mereka masuk gang itu bulan lalu, lalu keluar tanpa ingat apa pun. Tanpa luka, tanpa ingatan. '
                    'Yang begitu bukan kerja pencuri biasa.',
                    'Because two of them went into those alleys last month and came out remembering nothing. No wounds, no '
                    'memory. That is no common thief\'s work.'),
          gestur='bicara_tangan', pilihan=[P(('Aku akan membantu.', 'I will help.'), ke='bantu')]),
        N('bantu', ('Kalau kau menemukan sesuatu, bawa padaku. Bukan desas-desus; bukti. Desas-desus bisa kudengar gratis di kedai teh.',
                    'If you find something, bring it to me. Not rumours; proof. Rumours I can hear for free at the teahouse.'),
          gestur='palu_hakim', akibat=[f'quest_maju {Q1}']),
    ], prioritas=50, syarat=[tahap(Q1, 'yamen')])

    # ================================================================== chapter 2: Lost in the Slums
    Q(Q2, 'tokoh:bao_qing', 'kumuh', ('Bab 2 · Hilang di Kumuh', 'Chapter 2 · Lost in the Slums'),
      ('Anak-anak lenyap dari Kampung Kumuh. Kumpulkan tiga petunjuk dan ikuti jejaknya sampai ke sarang yang menyimpan mereka.',
       'Children are vanishing from the slums. Gather three clues and follow the trail to the lair that holds them.'),
      [T('petunjuk', ('Cari tiga petunjuk di Kampung Kumuh: Si Kecil Dou, Kakek Lumpur, dan seorang pengemis yang melihat sesuatu.',
                      'Find three clues in the slums: Little Dou, Grandpa Mud, and a beggar who saw something.'),
         tujuan=['punya cerita:sandal_berdarah', 'punya cerita:kertas_jimat_hitam', 'bendera takhta2_saksi'],
         batas_hari=6, gagal_ke='hilang', arah='kota/kota_besar'),
       T('sarang', ('Jejak gerobak dan jimat hitam menunjuk ke luar kota. Temukan Sarang Kultivator Iblis terdekat.',
                    'The cart tracks and the black talisman point out of the city. Find the nearest Demonic Cultivator Lair.'),
         tujuan=['pergi gua/sarang_iblis'], akibat=['bendera takhta2_sarang'], batas_hari=6, gagal_ke='hilang', arah='gua/sarang_iblis'),
       T('bebaskan', ('Buka sel-sel di sarang iblis dan bebaskan para tawanan.', 'Open the cells in the demon lair and free the captives.'),
         tujuan=['bebaskan tawanan x2'], akibat=['beri cerita:cap_bayangan', 'alur 2 bukti', 'gelar pembebas'],
         batas_hari=6, gagal_ke='hilang', arah='gua/sarang_iblis'),
       T('lapor', ('Bawa Cap Bayangan yang tertinggal di sarang kepada Hakim Bao.', 'Bring the Shadow Seal left in the lair to Magistrate Bao.'),
         tujuan=['antar cerita:cap_bayangan tokoh:bao_qing'],
         akibat=['nama_baik 5', 'beri_nb_pita 60', 'alur 2 bebas', 'kabar takhta_tawanan_pulang pemain'],
         hasil=('Para tawanan pulang ke Kampung Kumuh. Di meja Hakim Bao tergeletak Cap Bayangan: teratai terbalik, lambang jalan '
                'iblis, ditemukan di dalam kota yang bersumpah pedangnya tidur.',
                'The captives came home to the slums. On Magistrate Bao\'s desk lies the Shadow Seal: an upturned lotus, sign '
                'of the demonic path, found inside a city sworn to let its swords sleep.')),
       T('hilang', ('Jejaknya sudah dingin.', 'The trail has gone cold.'), tujuan=SEGERA, ke='gagal',
         hasil=('Orang-orang yang hilang tak pernah ditemukan. Kampung Kumuh mengunci pintunya lebih awal, dan Dou berhenti '
                'tertawa di bawah jembatan.',
                'The missing were never found. The slums lock their doors earlier now, and Dou no longer laughs under the bridge.'))],
      alur=True)

    D('takhta2_dou', 'tokoh:dou', [
        N('mulai', ('Ssst! Jangan keras-keras. Kau dari Yamen? ...Kawanku Xiao Tu hilang tiga malam lalu. Kami tidur di bawah '
                    'jembatan, dan paginya cuma sandalnya yang tertinggal.',
                    'Shh! Not so loud. Are you from the Yamen? ...My friend Xiao Tu vanished three nights ago. We sleep under the '
                    'bridge, and in the morning only his sandal was left.'),
          gestur='takut_sembunyi', pilihan=[
            P(('Tunjukkan tempatnya.', 'Show me the place.'), ke='gang'),
            P(('(Beri permen) Kau aman bersamaku.', '(Give a sweet) You are safe with me.'), ke='percaya', syarat=['nb 1'],
              akibat=['bayar 1'], tampil=True, alasan=('Kau tidak punya satu Nilai Batu pun.', 'You do not have a single Spirit Value.'))]),
        N('percaya', ('...Kau tidak bohong? Orang dewasa selalu bohong. Baik. Ikut aku, tapi jangan injak genangan; genangan di sini '
                      'punya telinga.',
                      '...You are not lying? Grown-ups always lie. Fine. Follow me, but do not step in the puddles; the puddles here '
                      'have ears.'),
          gestur='main_lompat', pilihan=[P(('(Ikuti Dou)', '(Follow Dou)'), ke='gang')]),
        N('gang', ('Di sini, di gang belakang gudang garam. Lihat, talinya putus, dan ini... darah. Bukan darah binatang: anjing-anjing '
                   'tidak mau mendekat.',
                   'Here, in the alley behind the salt warehouse. Look, the strap is torn, and this... is blood. Not animal blood: '
                   'the dogs will not come near.'),
          gestur='tunjuk', akibat=['beri cerita:sandal_berdarah', 'bendera takhta2_dou_bicara'], pilihan=[
            P(('Aku akan menemukan Xiao Tu. Aku janji.', 'I will find Xiao Tu. I promise.'), ke='janji', akibat=['alur 2 dou']),
            P(('Pulanglah dan kunci pintu.', 'Go home and lock the door.'))]),
        N('janji', ('Janji? Kakek Lumpur bilang janji orang kuat itu berat. Kalau kau ingkar, langit yang menagih. ...Tapi aku '
                    'percaya. Sedikit.',
                    'A promise? Grandpa Mud says a strong person\'s promise is heavy. If you break it, heaven collects the debt. '
                    '...But I believe you. A little.'),
          gestur='sikap_anak'),
    ], prioritas=50, syarat=[tahap(Q2, 'petunjuk'), '!bendera takhta2_dou_bicara'])

    D('takhta2_kakek', 'tokoh:kakek_lumpur', [
        N('mulai', ('...Kau mencari anak-anak itu.', '...You are looking for those children.'), gestur='duduk_lantai', pilihan=[
            P(('Kakek tahu sesuatu?', 'Do you know something, grandpa?'), ke='tahu'),
            P(('(Beri satu bakpao hangat)', '(Give a warm bun)'), ke='bakpao', syarat=['nb 1'], akibat=['bayar 1'])]),
        N('bakpao', ('Hm. Bakpao hangat membuka mulut tua. Duduklah, jangan berdiri seperti tiang jemuran.',
                     'Hm. A warm bun opens an old mouth. Sit down; do not stand there like a laundry pole.'),
          gestur='makan', prop='mangkuk', pilihan=[P(('(Duduk di sampingnya)', '(Sit beside him)'), ke='tahu')]),
        N('tahu', ('Malam-malam ada orang berjubah hitam lewat sini. Kakinya tidak menyentuh lumpur, dan lumpur di sini menyentuh '
                   'semua orang. Ia menjatuhkan ini.',
                   'At night a man in a black robe passes here. His feet do not touch the mud, and the mud here touches everyone. '
                   'He dropped this.'),
          gestur='cerita', akibat=['beri cerita:kertas_jimat_hitam', 'bendera takhta2_kakek_bicara'], pilihan=[
            P(('Ini jimat jalan iblis!', 'This is a demonic talisman!'), ke='iblis'),
            P(('Terima kasih, Kek.', 'Thank you, grandpa.'))]),
        N('iblis', ('Jangan teriak. Dinding kota ini punya telinga, dan sebagian telinga itu memakai cincin pejabat.',
                    'Do not shout. The walls of this city have ears, and some of those ears wear an official\'s ring.'),
          gestur='bisik'),
    ], prioritas=50, syarat=[tahap(Q2, 'petunjuk'), '!bendera takhta2_kakek_bicara'])

    D('takhta2_saksi', 'peran:pengemis', [
        N('mulai', ('Mau apa? Aku tidak lihat apa-apa. Tidak lihat apa-apa!', 'What do you want? I saw nothing. Nothing!'),
          gestur='takut_sembunyi', pilihan=[
            P(('(Beri 2 NB) Ceritakan saja apa yang kau lihat.', '(Give 2 SV) Just tell me what you saw.'), ke='lihat',
              syarat=['nb 2'], akibat=['bayar 2'], tampil=True, alasan=('Uangmu kurang dari 2 NB.', 'You have less than 2 SV.')),
            P(('Hakim Bao akan melindungimu.', 'Magistrate Bao will protect you.'), ke='lihat', syarat=['nama_baik 5'], tampil=True,
              alasan=('Ia belum cukup percaya padamu (Nama Baik 5).', 'He does not trust you enough yet (Good Name 5).')),
            P(('Maaf mengganggu.', 'Sorry to bother you.'))]),
        N('lihat', ('...Gerobak tertutup, lewat gerbang timur sebelum fajar. Penjaga tidak memeriksanya. Penjaga TIDAK PERNAH tidak '
                    'memeriksa gerobak. Kecuali gerobak itu membawa surat dari istana.',
                    '...A covered cart, out through the east gate before dawn. The guards did not search it. The guards NEVER '
                    'fail to search a cart. Unless the cart carries a letter from the palace.'),
          gestur='bisik', akibat=['bendera takhta2_saksi']),
    ], prioritas=40, syarat=[tahap(Q2, 'petunjuk'), '!bendera takhta2_saksi'])

    D('takhta2_bao_cap', 'tokoh:bao_qing', [
        N('mulai', ('Cap Bayangan. Teratai terbalik. Ini lambang jalan iblis, dan ia ditemukan bersama anak-anak kotaku. Seseorang '
                    'di dalam tembok ini memberi mereka jalan keluar-masuk. Aku akan mencari tangan yang memegang cap itu.',
                    'A Shadow Seal. An upturned lotus. The sign of the demonic path, found with the children of my city. Someone '
                    'inside these walls gives them a way in and out. I will find the hand that holds this seal.'),
          gestur='palu_hakim', akibat=['bendera takhta2_bao_cap']),
    ], prioritas=45, syarat=[f'quest {Q2} selesai', '!bendera takhta2_bao_cap'])

    D('dou_pulang', 'tokoh:dou', [
        N('mulai', ('Xiao Tu sudah pulang! Ia bilang orang berjubah hitam menyimpan mereka di sel yang dingin, lalu kau datang seperti '
                    'jenderal di cerita! Kau mau permen? Gratis, khusus untukmu.',
                    'Xiao Tu is home! He says the black robes kept them in a cold cell, and then you came like a general from the '
                    'stories! Want a sweet? Free, just for you.'),
          gestur='main_lompat', suara='tempat.captive_saved', akibat=['efek minecraft:speed 120 0']),
    ], prioritas=10, syarat=[f'quest {Q2} selesai'])

    # ================================================================== chapter 3: The Ailing Heir
    Q(Q3, 'tokoh:dan_chen', 'gilda_alkemis', ('Bab 3 · Pewaris Sakit', 'Chapter 3 · The Ailing Heir'),
      ('Yan Ruyi rebah di tengah upacara: racun darah. Tetua Dan Chen punya rumusnya, tetapi tidak punya bahannya.',
       'Yan Ruyi collapsed during the rites: blood venom. Elder Dan Chen has the formula, but not the ingredients.'),
      [T('tabib', ('Temui Tetua Dan Chen di Gilda Alkemis.', 'Meet Elder Dan Chen at the Alchemist Guild.'),
         cabang=[(['bendera takhta3_beli'], 'pasar'), (['bendera takhta3_urat'], 'urat')], ke='air', batas_hari=6, gagal_ke='lemah'),
       T('air', ('Ambil Air Mata Air Roh di situs Mata Air Roh.', 'Draw Spirit Spring Water at a spirit spring.'),
         tujuan=['pergi situs/mata_air_roh'], akibat=['beri cerita:air_mata_air_roh'], ke='racik', batas_hari=6, gagal_ke='lemah',
         arah='situs/mata_air_roh'),
       T('urat', ('Petik Rumput Bintang di dalam Gua Urat Roh.', 'Pick Starwort inside a Spirit Vein Cave.'),
         tujuan=['pergi gua/urat_roh'], akibat=['beri cerita:rumput_bintang'], ke='racik', batas_hari=6, gagal_ke='lemah',
         arah='gua/urat_roh'),
       T('racik', ('Racik satu pil di tungku mana pun dengan api yang tidak goyah. Bahan roh di tasmu akan menuntun obatnya.',
                   'Refine one pill at any furnace with a flame that does not waver. The spirit ingredient in your bag will guide the cure.'),
         tujuan=['racik *'], akibat=['ambil cerita:air_mata_air_roh', 'ambil cerita:rumput_bintang', 'beri cerita:pil_penawar_racun_darah',
                                     'bendera alkemis_tingkat_plus'],
         ke='antar', batas_hari=6, gagal_ke='lemah'),
       T('pasar', ('Beli pil penawar dari makelar Pasar Gelap.', 'Buy the antidote from a Black Market broker.'),
         ke='antar', batas_hari=6, gagal_ke='lemah'),
       T('antar', ('Bawa Pil Penawar Racun Darah kepada Yan Ruyi di istana.', 'Bring the Blood Venom Antidote Pill to Yan Ruyi in the palace.'),
         tujuan=['antar cerita:pil_penawar_racun_darah tokoh:yan_ruyi'], cabang=[(['bendera takhta3_beli'], 'dibeli')], ke='sembuh',
         batas_hari=6, gagal_ke='lemah'),
       T('sembuh', ('Ruyi menelan pil itu. Warna kembali ke pipinya.', 'Ruyi swallows the pill. Colour returns to her cheeks.'),
         tujuan=SEGERA, akibat=['alur 3 sembuh', 'gelar penyelamat_pewaris', 'beri cerita:lencana_penyelamat', 'nama_baik 5',
                                'adegan pewaris_selamat'], ke='selesai',
         hasil=('Pil yang kau racik dengan tangan jujur menyelamatkan pewaris. Tetua Dan Chen, untuk pertama kalinya, '
                'mengangguk padamu.',
                'The pill you refined with honest hands saved the heir. Elder Dan Chen, for the first time, nodded to you.')),
       T('dibeli', ('Ruyi menelan pil itu. Napasnya tenang, tetapi tak ada yang tahu siapa yang meraciknya.',
                    'Ruyi swallows the pill. Her breath calms, but nobody knows who made it.'),
         tujuan=SEGERA, akibat=['alur 3 dibeli', 'gelar penyelamat_pewaris', 'beri cerita:lencana_penyelamat', 'adegan pewaris_selamat'],
         ke='selesai',
         hasil=('Pewaris selamat oleh pil dari Pasar Gelap. Kota bersyukur; Tetua Dan Chen menolak menatap matamu.',
                'The heir lives thanks to a Black Market pill. The city is grateful; Elder Dan Chen will not meet your eyes.')),
       T('lemah', ('Waktunya habis.', 'Time has run out.'), tujuan=SEGERA, ke='gagal',
         hasil=('Para tabib istana menahan racun itu dengan jarum perak. Ruyi bertahan hidup, tetapi tubuhnya tetap lemah.',
                'The palace physicians held the venom back with silver needles. Ruyi survived, but her body stays frail.'))],
      alur=True)

    D('takhta3_dan_chen', 'tokoh:dan_chen', [
        N('mulai', ('Kau. Ya, kau. Tanganmu masih kasar, tapi hari ini aku tidak punya pilihan. Pewaris diracun racun darah. Tiga tabib '
                    'istana menangis di lorong. Aku tidak menangis; aku menghitung.',
                    'You. Yes, you. Your hands are still rough, but today I have no choice. The heir has been given blood venom. '
                    'Three palace physicians are weeping in the corridor. I do not weep; I count.'),
          gestur='sikap_angkuh', pilihan=[P(('Apa yang dibutuhkan?', 'What is needed?'), ke='resep')]),
        N('resep', ('Pil Penawar Racun Darah. Rumusnya ada di kepalaku, bahannya tidak ada di rakku: Air Mata Air Roh, atau Rumput '
                    'Bintang dari Gua Urat Roh. Satu cukup, asal apinya jujur. Ambil resep ini.',
                    'The Blood Venom Antidote Pill. The formula is in my head, the ingredients are not on my shelves: Spirit Spring '
                    'Water, or Starwort from a Spirit Vein Cave. One is enough, if the flame is honest. Take this recipe.'),
          gestur='bicara_tangan', prop='kitab', akibat=['beri cerita:resep_penawar'], pilihan=[
            P(('Aku berangkat ke Mata Air Roh.', 'I will go to a spirit spring.'), akibat=['bendera takhta3_air', f'quest_maju {Q3}']),
            P(('Aku berangkat ke Gua Urat Roh.', 'I will go to a Spirit Vein Cave.'), akibat=['bendera takhta3_urat', f'quest_maju {Q3}']),
            P(('Tidak adakah jalan yang lebih cepat?', 'Is there no faster way?'), ke='cepat')]),
        N('cepat', ('Ada. Pasar Gelap menjual pil jadi. Tak ada yang tahu siapa meraciknya, dan aku tidak akan menyentuhnya bahkan '
                    'dengan sumpitku. Tapi... nyawa pewaris tidak menunggu kesombonganku.',
                    'There is. The Black Market sells finished pills. Nobody knows who made them, and I would not touch one even '
                    'with my chopsticks. But... the heir\'s life will not wait for my pride.'),
          gestur='marah_tunjuk', pilihan=[
            P(('Aku akan membelinya.', 'I will buy one.'), akibat=['bendera takhta3_beli', f'quest_maju {Q3}']),
            P(('Kalau begitu aku cari bahannya.', 'Then I will find the ingredients.'), ke='resep')]),
    ], prioritas=50, syarat=[tahap(Q3, 'tabib')])

    D('takhta3_pasar', ['peran:makelar_gelap', 'tokoh:lao_shi'], [
        N('mulai', ('Pil penawar racun darah? Hari ini semua orang mencarinya. Aneh, ya, seolah ada yang tahu racun itu akan dipakai.',
                    'A blood venom antidote? Everyone wants one today. Strange, isn\'t it, as if someone knew the venom would be used.'),
          gestur='hitung_koin_gelap', prop='kantong_uang', pilihan=[
            P(('(Bayar berapa pun yang kau minta)', '(Pay whatever you ask)'), ke='beli', syarat=['nb_pita 80'], tampil=True,
              akibat=['bayar_pita 80', 'beri cerita:pil_penawar_racun_darah', 'nama_baik -3', f'quest_maju {Q3}'],
              alasan=('Harga pil Pasar Gelap jauh di atas isi kantongmu.', 'A Black Market pill costs far more than your purse holds.')),
            P(('Siapa lagi yang mencarinya?', 'Who else was looking?'), ke='siapa')]),
        N('siapa', ('Rahasia dagang. ...Baiklah, satu kata gratis: seseorang dari istana membeli bahan racunnya lebih dulu. Orang yang '
                    'sopan sekali. Terlalu sopan untuk pasar ini.',
                    'Trade secret. ...Fine, one free word: someone from the palace bought the venom\'s ingredients first. A very '
                    'polite man. Too polite for this market.'),
          gestur='bisik', pilihan=[
            P(('(Bayar pilnya)', '(Pay for the pill)'), ke='beli', syarat=['nb_pita 80'],
              akibat=['bayar_pita 80', 'beri cerita:pil_penawar_racun_darah', 'nama_baik -3', f'quest_maju {Q3}']),
            P(('Aku pergi.', 'I am leaving.'))]),
        N('beli', ('Senang berbisnis denganmu. Kalau ada yang bertanya, kau tidak pernah ke sini, dan aku tidak pernah lahir.',
                   'A pleasure doing business. If anyone asks, you were never here, and I was never born.'),
          gestur='tertawa'),
    ], prioritas=50, syarat=[tahap(Q3, 'pasar')])

    D('takhta3_ruyi', 'tokoh:yan_ruyi', [
        N('mulai', ('Mereka bilang kau yang membawa pil itu. Budi ini takkan pernah kulupakan. Ambillah lencana ini; tanganku masih '
                    'gemetar, jangan tertawa.',
                    'They say you brought the pill. I will never forget this kindness. Take this badge; my hand still trembles, do '
                    'not laugh.'),
          gestur='hormat_dalam', suara='cerita.ruyi_thanks', akibat=['bendera takhta3_ruyi_terima'], pilihan=[
            P(('Siapa yang meracunimu?', 'Who poisoned you?'), ke='siapa'),
            P(('Istirahatlah, Tuan Putri.', 'Rest, my lady.'))]),
        N('siapa', ('Teh pagi. Selalu dituang oleh orang yang sama, dari teko yang sama. Hari itu tehnya berbau besi. Aku tidak mau '
                    'menuduh tanpa bukti; Hakim Bao akan marah padaku.',
                    'The morning tea. Always poured by the same hands, from the same pot. That day it smelled of iron. I will not '
                    'accuse without proof; Magistrate Bao would scold me.'),
          gestur='bicara_tangan'),
    ], prioritas=45, syarat=[f'quest {Q3} selesai', '!bendera takhta3_ruyi_terima'])

    # ================================================================== chapter 4: Two Claimants
    Q(Q4, 'tokoh:yan_wushuang', 'istana', ('Bab 4 · Dua Calon', 'Chapter 4 · Two Claimants'),
      ('Pangeran Yan ingin menyepi. Ruyi atau Gu Lianshan? Dewan gilda mendengarkan orang yang dipercaya rakyat.',
       'Prince Yan wishes to withdraw. Ruyi or Gu Lianshan? The guild council listens to those the people trust.'),
      [T('audiensi', ('Pangeran Yan Wushuang memanggilmu ke Balairung Istana.', 'Prince Yan Wushuang summons you to the Palace Hall.'),
         cabang=[(['bendera takhta4_netral'], 'netral')], ke='galang', batas_hari=6, gagal_ke='netral'),
       T('galang', ('Galang dukungan tiga dari lima kepala kota: Tetua Dan Chen, Lin Fei, Nyonya Qian, Ou Ye Muda, Hakim Bao.',
                    'Win the backing of three of the five city heads: Elder Dan Chen, Lin Fei, Madam Qian, Young Ou Ye, Magistrate Bao.'),
         tujuan=['tanda dukungan x3'], cabang=[(['bendera takhta4_ruyi'], 'ruyi')], ke='gu', batas_hari=6, gagal_ke='netral'),
       T('ruyi', ('Tiga kepala kota berdiri di belakang Ruyi.', 'Three city heads stand behind Ruyi.'), tujuan=SEGERA,
         akibat=['alur 4 ruyi', 'kabar takhta_dukung_ruyi'], ke='selesai',
         hasil=('Dewan gilda mencatat dukungan untuk Yan Ruyi. Di lorong istana, Gu Lianshan tersenyum dan mengucapkan selamat '
                'lebih dulu dari siapa pun.',
                'The guild council recorded its backing for Yan Ruyi. In the palace corridor, Gu Lianshan smiled and offered '
                'his congratulations before anyone else.')),
       T('gu', ('Tiga kepala kota berdiri di belakang Gu Lianshan.', 'Three city heads stand behind Gu Lianshan.'), tujuan=SEGERA,
         akibat=['alur 4 gu', 'kabar takhta_dukung_gu'], ke='selesai',
         hasil=('Dewan gilda mencatat dukungan untuk Gu Lianshan. Para pedagang menggantung lentera di depan toko mereka; Ruyi '
                'menutup pintu kamarnya.',
                'The guild council recorded its backing for Gu Lianshan. The merchants hung lanterns before their shops; Ruyi '
                'closed the door of her room.')),
       T('netral', ('Kau tidak memihak.', 'You took no side.'), tujuan=SEGERA, akibat=['alur 4 netral'], ke='selesai',
         hasil=('Kau tidak memihak. Kota terbelah dua, dan dewan menunda keputusannya sampai kebenaran berbicara lebih keras.',
                'You took no side. The city is split in two, and the council delays its choice until the truth speaks louder.'))],
      alur=True)

    D('takhta4_pangeran', 'tokoh:yan_wushuang', [
        N('mulai', ('Kau orang yang ada di sana saat kota membutuhkan tangan. Duduklah. Aku akan menyepi ke gunung. Dewan gilda harus '
                    'memilih penggantiku, dan dewan mendengarkan orang yang dipercaya rakyat.',
                    'You are the one who was there when the city needed hands. Sit. I am withdrawing to the mountains. The guild '
                    'council must choose my successor, and the council listens to those the people trust.'),
          gestur='sikap_tangan_belakang', suara='kotabesar.palace_audience', pilihan=[
            P(('Siapa calonnya?', 'Who are the claimants?'), ke='calon')]),
        N('calon', ('Ruyi: adil, keras kepala, tubuhnya lemah. Gu Lianshan: cakap, menghitung secepat bankir, dicintai pedagang. Aku '
                    'ayah Ruyi, jadi suaraku tidak dihitung. Suaramu, mungkin, dihitung.',
                    'Ruyi: just, stubborn, frail of body. Gu Lianshan: able, counts as fast as a banker, beloved of merchants. I '
                    'am Ruyi\'s father, so my voice does not count. Yours, perhaps, does.'),
          gestur='bicara_tangan', pilihan=[
            P(('Aku mendukung Yan Ruyi.', 'I back Yan Ruyi.'), ke='pilih_ruyi', akibat=['bendera takhta4_ruyi', f'quest_maju {Q4}']),
            P(('Aku mendukung Menteri Gu.', 'I back Minister Gu.'), ke='pilih_gu', akibat=['bendera takhta4_gu', f'quest_maju {Q4}']),
            P(('Aku tidak akan memihak.', 'I will not take sides.'), ke='pilih_netral', akibat=['bendera takhta4_netral', f'quest_maju {Q4}'])]),
        N('pilih_ruyi', ('Hatimu memilih dengan berani. Hati saja tidak cukup: galang tiga dari lima kepala kota. Dan Chen, Lin Fei, '
                         'Nyonya Qian, Ou Ye Muda, Hakim Bao. Mereka tidak bisa dibeli, tetapi bisa diyakinkan.',
                         'Your heart chooses bravely. A heart is not enough: win three of the five city heads. Dan Chen, Lin Fei, '
                         'Madam Qian, Young Ou Ye, Magistrate Bao. They cannot be bought, but they can be convinced.'),
          gestur='hormat_dalam'),
        N('pilih_gu', ('Pilihan yang masuk akal. Kota butuh tangan yang bisa menghitung. Galang tiga dari lima kepala kota: Dan Chen, '
                       'Lin Fei, Nyonya Qian, Ou Ye Muda, Hakim Bao.',
                       'A sensible choice. The city needs hands that can count. Win three of the five city heads: Dan Chen, Lin Fei, '
                       'Madam Qian, Young Ou Ye, Magistrate Bao.'),
          gestur='hormat_dalam'),
        N('pilih_netral', ('Kenetralan juga sebuah pilihan, dan kota ini dibangun di atasnya. Aku tidak akan memaksamu.',
                           'Neutrality is a choice too, and this city was built on it. I will not press you.'),
          gestur='tatap_langit'),
    ], prioritas=50, syarat=[tahap(Q4, 'audiensi')])

    def dukung(tokoh, mulai, pilihan, setuju, gestur='bicara_tangan', prop='', gestur_setuju='hormat'):
        flag = f'takhta4_dukung_{tokoh}'
        D(f'takhta4_{tokoh}', f'tokoh:{tokoh}', [
            N('mulai', mulai, gestur=gestur, prop=prop, pilihan=pilihan + [P(('Lain kali.', 'Another time.'))]),
            N('setuju', setuju, gestur=gestur_setuju, akibat=['peristiwa tanda dukungan', f'bendera {flag}', 'nama_baik 3']),
        ], prioritas=50, syarat=[tahap(Q4, 'galang'), f'!bendera {flag}'])

    dukung('dan_chen',
           ('Politik? Aku alkemis. ...Tapi pil tanpa tabib yang jujur hanyalah racun yang sopan. Siapa calonmu, dan kenapa aku harus '
            'peduli?',
            'Politics? I am an alchemist. ...But a pill without an honest healer is only a polite poison. Who is your claimant, '
            'and why should I care?'),
           [P(('Kau sudah melihat ketelitianku di tungku.', 'You have seen my care at the furnace.'), ke='setuju',
              syarat=['quest tungku_pertama selesai'], tampil=True,
              alasan=('Dan Chen hanya mendengarkan alkemis yang lulus ujiannya.', 'Dan Chen only listens to alchemists who passed his exam.')),
            P(('(Sumbang dana tungku gilda)', '(Donate to the guild furnace fund)'), ke='setuju', syarat=['nb_pita 20'], tampil=True,
              akibat=['bayar_pita 20'], alasan=('Uangmu kurang untuk sumbangan yang pantas.', 'You lack the money for a fitting gift.'))],
           ('Hm. Baiklah. Gilda Alkemis akan berdiri di belakang calonmu. Jangan buat aku menyesal; aku benci menyesal lebih dari aku '
            'benci pemula.',
            'Hm. Very well. The Alchemist Guild will stand behind your claimant. Do not make me regret it; I hate regret more than '
            'I hate novices.'), gestur='sikap_angkuh')
    dukung('lin_fei',
           ('Dukungan Gilda Kultivator? Kami ikut siapa yang menang di lapangan, bukan di rapat. Tunjukkan tanganmu.',
            'The Cultivators\' Guild\'s backing? We follow whoever wins in the field, not in meetings. Show me your hand.'),
           [P(('(Adu panco dengan Lin Fei)', '(Arm-wrestle Lin Fei)'), ke='setuju', syarat=['pita 2'], tampil=True,
              alasan=('Lin Fei tertawa: "Kembalilah setelah ranahmu lebih tinggi."', 'Lin Fei laughs: "Come back when your realm is higher."')),
            P(('(Bayar minumannya, 5 NB) Ceritakan kenapa kau peduli.', '(Buy her drink, 5 SV) Tell me why you care.'), ke='cerita',
              syarat=['nb 5'], akibat=['bayar 5'])],
           ('Tanganmu cukup. Gilda Kultivator mendukung. Kalau calonmu mengkhianati kota, aku sendiri yang akan menyeretnya ke Arena.',
            'Your hand will do. The Cultivators\' Guild backs you. If your claimant betrays the city, I will drag them to the Arena myself.'),
           gestur='sikap_berkacak', gestur_setuju='tepuk_bahu')
    # Lin Fei's story node (bought drink) leads to the same support
    DIALOGS = g.DIALOGS
    DIALOGS['takhta4_lin_fei']['simpul'].insert(1, N('cerita', (
        'Dua puluh tahun lalu tembok timur hampir runtuh, dan yang menahannya bukan pangeran, bukan menteri, tapi tukang dan '
        'pemburu. Siapa pun yang duduk di takhta harus ingat siapa yang berdiri di tembok. Calonmu ingat?',
        'Twenty years ago the east wall nearly fell, and what held it was no prince, no minister, but builders and hunters. '
        'Whoever sits on the throne must remember who stood on the wall. Does yours remember?'),
        gestur='minum_arak', prop='cawan_arak', pilihan=[
            P(('Ia akan ingat. Aku yang memastikannya.', 'They will. I will make sure of it.'), ke='setuju'),
            P(('Aku tidak bisa menjanjikannya.', 'I cannot promise that.'))]))
    dukung('qian_duoduo',
           ('Dukungan Rumah Uang ada harganya, Tuan. Bukan suap; jaminan. Rumah Qian tidak pernah mendukung pihak yang tidak bisa '
            'membayar janjinya.',
            'The Money House\'s backing has a price, sir. Not a bribe; a surety. House Qian never backs a side that cannot pay '
            'its promises.'),
           [P(('(Titipkan jaminan)', '(Lodge a surety)'), ke='setuju', syarat=['nb_pita 15'], tampil=True, akibat=['bayar_pita 15'],
              alasan=('Jaminannya lebih besar dari isi kantongmu.', 'The surety is larger than your purse.')),
            P(('Kau sudah tahu aku jujur.', 'You already know I am honest.'), ke='setuju', syarat=['quest wesel_palsu selesai'], tampil=True,
              alasan=('Nyonya Qian belum pernah berurusan denganmu.', 'Madam Qian has never done business with you.'))],
           ('Tercatat, dengan tinta merah: Rumah Qian mendukung. Jangan tersinggung, tinta merah itu untuk orang yang kupercaya.',
            'Recorded, in red ink: House Qian gives its backing. Do not be offended; red ink is for people I trust.'),
           gestur='hitung_sempoa', prop='sempoa')
    dukung('ou_ye',
           ('Bicara politik di bengkel. Hm.', 'Talking politics in a forge. Hm.'),
           [P(('(Serahkan delapan batang besi untuk tungku gilda)', '(Hand over eight iron ingots for the guild forge)'), ke='setuju',
              syarat=['punya minecraft:iron_ingot 8'], tampil=True, akibat=['ambil minecraft:iron_ingot 8'],
              alasan=('Ou Ye melirik tanganmu yang kosong.', 'Ou Ye glances at your empty hands.')),
            P(('Kau pernah menempa pedang bersamaku.', 'You once forged a sword with me.'), ke='setuju',
              syarat=['quest pedang_tanpa_nama selesai'], tampil=True,
              alasan=('Kalian belum pernah berdiri di depan landasan yang sama.', 'You have never stood at the same anvil.'))],
           ('Besi bagus. Gilda Pandai Pusaka ikut. Sudah.', 'Good iron. The Forge Guild is with you. That is all.'),
           gestur='palu_landasan', prop='palu', gestur_setuju='angguk_dengar')
    dukung('bao_qing',
           ('Hakim tidak memihak calon. Hakim memihak hukum. Kalau kau datang untuk meminta namaku, bawalah alasan yang bisa '
            'dibaca di depan sidang.',
            'A magistrate takes no claimant\'s side. A magistrate takes the law\'s side. If you come for my name, bring a reason '
            'that can be read aloud in court.'),
           [P(('Calonku bersumpah menegakkan hukum di depan saksi.', 'My claimant will swear before witnesses to uphold the law.'),
              ke='setuju', syarat=['nama_baik 10'], tampil=True,
              alasan=('Bao Qing belum cukup mengenalmu (Nama Baik 10).', 'Bao Qing does not know you well enough (Good Name 10).'))],
           ('...Yamen tidak memihak. Tetapi namaku boleh ditulis di daftar saksi sumpah itu. Hanya itu, dan itu sudah banyak.',
            '...The Yamen takes no side. But my name may be written among the witnesses of that oath. Only that, and that is a great deal.'),
           gestur='sikap_lipat_tangan', gestur_setuju='palu_hakim')

    D('takhta4_gu', 'tokoh:gu_lianshan', [
        N('mulai', ('Kau sudah dengar? Pangeran akan menyepi. Aku tidak meminta dukunganmu, hanya akal sehatmu. Kas kota kosong separuh; '
                    'aku bisa mengisinya dalam tiga musim.',
                    'Have you heard? The Prince is withdrawing. I do not ask for your support, only your common sense. The treasury '
                    'is half empty; I can fill it in three seasons.'),
          gestur='kipas', prop='kipas_lipat', pilihan=[
            P(('Dan Kampung Kumuh?', 'And the slums?'), ke='kumuh'),
            P(('Kenapa kau mau takhta?', 'Why do you want the throne?'), ke='kenapa'),
            P(('Akan kupikirkan.', 'I will think about it.'))]),
        N('kumuh', ('Kampung Kumuh butuh pekerjaan, bukan bubur gratis. Gudang garam baru akan mempekerjakan dua ratus orang. Bubur '
                    'habis dalam sehari; upah datang setiap bulan.',
                    'The slums need work, not free porridge. The new salt warehouse will employ two hundred. Porridge is gone in a '
                    'day; wages come every month.'),
          gestur='hitung_sempoa', prop='sempoa'),
        N('kenapa', ('Karena aku lahir di jalan pasar, menghitung koin orang lain. Kota ini memberiku segalanya; aku ingin membayar '
                     'utangku dengan bunga. Itu ambisi, ya. Ambisi bukan dosa.',
                     'Because I was born on the market street, counting other people\'s coins. This city gave me everything; I want '
                     'to repay my debt with interest. That is ambition, yes. Ambition is no sin.'),
          gestur='bicara_tangan'),
    ], prioritas=20, syarat=['alur 4', '!alur 5'])

    D('takhta4_ruyi', 'tokoh:yan_ruyi', [
        N('mulai', ('Ayah mau menyepi. Aku tidak meminta takhta; aku meminta lumbung dibuka. Kalau takhta satu-satunya kunci lumbung, '
                    'maka aku mau takhta.',
                    'Father wants to withdraw. I do not ask for the throne; I ask for the granaries to open. If the throne is the '
                    'only key to the granaries, then I want the throne.'),
          gestur='sikap_tangan_depan', pilihan=[
            P(('Tubuhmu lemah.', 'Your body is frail.'), ke='lemah'),
            P(('Menteri Gu juga punya rencana.', 'Minister Gu has plans too.'), ke='gu'),
            P(('Semoga berhasil.', 'Good luck.'))]),
        N('lemah', ('Tubuhku lemah. Hukumku tidak.', 'My body is frail. My law is not.'), gestur='marah_tunjuk'),
        N('gu', ('Gu Lianshan pandai, dan aku tidak membencinya. Tapi rencananya selalu dimulai dari kas dan berakhir di kas. Di mana '
                 'orangnya?',
                 'Gu Lianshan is clever, and I do not hate him. But his plans always start at the treasury and end at the treasury. '
                 'Where are the people?'),
          gestur='bicara_tangan'),
    ], prioritas=20, syarat=['alur 4', '!alur 5'])

    # ================================================================== chapter 5: The Mask Slips
    Q(Q5, 'tokoh:bao_qing', 'yamen', ('Bab 5 · Topeng Terlepas', 'Chapter 5 · The Mask Slips'),
      ('Racun di istana, Cap Bayangan di Kumuh, uang istana yang lenyap: tiga benang, satu simpul. Kumpulkan bukti dan putuskan '
       'cara membuka topeng itu.',
       'Poison in the palace, a Shadow Seal in the slums, palace money vanishing: three threads, one knot. Gather the proofs and '
       'decide how to tear off the mask.'),
      [T('panggil', ('Hakim Bao Qing memanggilmu ke Yamen.', 'Magistrate Bao Qing summons you to the Yamen.'), batas_hari=6, gagal_ke='diam'),
       T('qian', ('Minta Nyonya Qian Duoduo membuka buku Rumah Uang.', 'Ask Madam Qian Duoduo to open the Money House ledgers.'),
         batas_hari=6, gagal_ke='diam'),
       T('kuil', ('Tanyakan pada Biksu Jing Xin tentang peziarah yang tidak pernah membakar dupa.',
                  'Ask Monk Jing Xin about the pilgrim who never burns incense.'), batas_hari=6, gagal_ke='diam'),
       T('cermin', ('Pergilah ke Gua Cermin Hati dan tatap kolamnya, seperti pesan Jing Xin.',
                    'Go to the Heart Mirror Cave and look into its pool, as Jing Xin asked.'),
         tujuan=['pergi gua/cermin_hati'], batas_hari=6, gagal_ke='diam', arah='gua/cermin_hati'),
       T('surat', ('Kembalilah kepada Biksu Jing Xin.', 'Return to Monk Jing Xin.'),
         tujuan=['bicara tokoh:jing_xin'], akibat=['beri cerita:surat_mata_mata', 'alur 5 bukti'], batas_hari=6, gagal_ke='diam'),
       T('pilih', ('Bukti ada di tanganmu. Bawa ke Hakim Bao, atau hadapi Mo Ying sendiri.',
                   'The proofs are in your hands. Take them to Magistrate Bao, or face Mo Ying yourself.'),
         cabang=[(['bendera takhta5_c'], 'suap'), (['bendera takhta5_b'], 'duel')], ke='sidang', batas_hari=6, gagal_ke='diam'),
       T('sidang', ('Palu Hakim Bao jatuh tiga kali.', 'Magistrate Bao\'s gavel falls three times.'), tujuan=SEGERA,
         akibat=['alur 5 a', 'nama_baik 10', 'adegan sidang', 'kabar takhta_sidang pemain'], ke='selesai',
         hasil=('Di sidang terbuka Yamen, Mo Ying tersenyum sampai bukti ketiga dibacakan. Lalu topengnya retak, dan ia menembus '
                'atap Yamen seperti asap hitam. Kota tahu namanya sekarang.',
                'At the open trial in the Yamen, Mo Ying smiled until the third proof was read. Then his mask cracked, and he '
                'burst through the Yamen roof like black smoke. The city knows his name now.')),
       T('duel', ('Kalahkan Mo Ying dalam duel di gudang Distrik Awan.', 'Defeat Mo Ying in a duel in the Cloud District warehouse.'),
         tujuan=['duel tokoh:mo_ying'], akibat=['alur 5 b', 'beri cerita:cincin_mo_ying', 'adegan topeng', 'kabar takhta_topeng'],
         ke='selesai', batas_hari=6, gagal_ke='diam',
         hasil=('Mo Ying tumbang di antara peti-peti garam. Topengnya pecah menjadi dua, dan wajah di baliknya ternyata wajah yang '
                'sama, hanya tanpa senyum. Ia melarikan diri, meninggalkan cincinnya.',
                'Mo Ying fell among the salt crates. His mask split in two, and the face beneath was the same face, only without '
                'the smile. He fled, leaving his ring behind.')),
       T('suap', ('Kertas-kertas itu terbakar.', 'The papers burn.'), tujuan=SEGERA, akibat=['alur 5 c', 'kabar takhta_suap'], ke='selesai',
         hasil=('Kau menerima batu roh Mo Ying. Bukti-bukti terbakar di tungkunya, dan senyumnya, malam itu, lebih lebar dari biasa.',
                'You took Mo Ying\'s spirit stones. The proofs burned in his brazier, and his smile, that night, was wider than ever.')),
       T('diam', ('Waktunya habis.', 'Time has run out.'), tujuan=SEGERA, ke='gagal',
         hasil=('Bukti tidak pernah lengkap. Mo Ying tetap berdiri di belakang Menteri Gu, menulis angka-angka dengan tangannya yang '
                'dingin.',
                'The proofs were never complete. Mo Ying still stands behind Minister Gu, writing figures with his cold hand.'))],
      alur=True)

    D('takhta5_bao', 'tokoh:bao_qing', [
        N('mulai', ('Racun di istana. Cap Bayangan di Kumuh. Uang istana yang lenyap setiap bulan. Tiga benang, satu simpul. Aku butuh '
                    'dua bukti lagi: buku Rumah Uang, dan mata-mata yang bersembunyi di kuil.',
                    'Poison in the palace. A Shadow Seal in the slums. Palace money vanishing every month. Three threads, one knot. '
                    'I need two more proofs: the Money House ledgers, and the spy hiding in the temple.'),
          gestur='palu_hakim', pilihan=[
            P(('Akan kucari.', 'I will find them.'), akibat=[f'quest_maju {Q5}']),
            P(('Kau mencurigai siapa?', 'Whom do you suspect?'), ke='curiga')]),
        N('curiga', ('Hakim yang mencurigai sebelum bukti lengkap adalah hakim yang menghukum karena benci. Aku menunggu. Kau yang '
                     'berlari.',
                     'A magistrate who suspects before the proof is whole is a magistrate who punishes out of hatred. I wait. You run.'),
          gestur='sikap_lipat_tangan', pilihan=[P(('Akan kucari.', 'I will find them.'), akibat=[f'quest_maju {Q5}'])]),
    ], prioritas=50, syarat=[tahap(Q5, 'panggil')])

    D('takhta5_qian', 'tokoh:qian_duoduo', [
        N('mulai', ('Dari Hakim Bao? Duduk, dan jangan sentuh sempoaku. ...Setiap bulan empat ratus batu roh keluar dari rekening istana '
                    'ke rekening tanpa nama, dicap dengan cap Menteri.',
                    'From Magistrate Bao? Sit, and do not touch my abacus. ...Every month four hundred spirit stones leave the '
                    'palace account for a nameless one, stamped with the Minister\'s seal.'),
          gestur='hitung_sempoa', prop='sempoa', pilihan=[
            P(('Jadi Menteri Gu pelakunya?', 'So Minister Gu is the culprit?'), ke='kiri'),
            P(('Boleh kubawa halaman itu?', 'May I take that page?'), ke='beri')]),
        N('kiri', ('Itu yang aneh. Menteri Gu kidal; ia mengecap dengan tangan kiri, condong ke kanan. Cap di halaman ini condong ke '
                   'kiri. Seseorang yang dekat dengannya memakai capnya.',
                   'That is the odd part. Minister Gu is left-handed; he stamps with his left, leaning right. The seal on this page '
                   'leans left. Someone close to him uses his seal.'),
          gestur='tunjuk', pilihan=[P(('Boleh kubawa halaman itu?', 'May I take that page?'), ke='beri')]),
        N('beri', ('Rumah Qian tidak pernah merobek bukunya selama sembilan ratus tahun. Hari ini aku merobeknya. Pakailah dengan bijak, '
                   'dan kembalikan kalau kota sudah aman; aku benci buku yang bolong.',
                   'House Qian has not torn its books in nine hundred years. Today I tear one. Use it wisely, and bring it back when '
                   'the city is safe; I hate books with holes.'),
          gestur='menulis', prop='kuas', akibat=['beri cerita:catatan_pembukuan', 'alur 5 bukti', f'quest_maju {Q5}']),
    ], prioritas=50, syarat=[tahap(Q5, 'qian')])

    D('takhta5_jing_xin', 'tokoh:jing_xin', [
        N('mulai', ('Ada seorang peziarah yang datang setiap hari ketiga. Ia berlutut tanpa dupa dan tanpa doa. Ia tidak berdoa; ia '
                    'menghitung penjaga.',
                    'A pilgrim comes every third day. He kneels without incense and without prayer. He is not praying; he is '
                    'counting the guards.'),
          gestur='sikap_doa', suara='kotabesar.temple_bell', pilihan=[
            P(('Di mana ia menyimpan sesuatu?', 'Where does he hide things?'), ke='altar')]),
        N('altar', ('Di balik altar, mungkin. Aku bisa membukanya, tapi aku harus yakin hatimu tidak mencari dendam. Pergilah ke Gua '
                    'Cermin Hati di luar kota. Tatap kolamnya. Kalau kau kembali tanpa ingin membunuh, aku akan membuka altar.',
                    'Behind the altar, perhaps. I can open it, but I must be sure your heart does not seek revenge. Go to the Heart '
                    'Mirror Cave outside the city. Look into its pool. If you return without wishing to kill, I will open the altar.'),
          gestur='hormat_dalam', akibat=[f'quest_maju {Q5}']),
    ], prioritas=50, syarat=[tahap(Q5, 'kuil')])

    D('takhta5_surat', 'tokoh:jing_xin', [
        N('mulai', ('Kau kembali, dan matamu tenang. Ini yang kutemukan di balik altar: surat bertulisan tangan yang sama dengan '
                    'catatan pembukuan itu. Bayangan menulis dengan tangan yang dingin.',
                    'You have returned, and your eyes are calm. This is what I found behind the altar: a letter in the same hand '
                    'as that ledger. The shadow writes with a cold hand.'),
          gestur='sikap_doa', akibat=['bendera takhta5_surat_dibaca']),
    ], prioritas=45, syarat=[tahap(Q5, 'pilih'), '!bendera takhta5_surat_dibaca'])

    D('takhta5_bao_pilih', 'tokoh:bao_qing', [
        N('mulai', ('Tunjukkan apa yang kau bawa.', 'Show me what you bring.'), gestur='sikap_lipat_tangan', pilihan=[
            P(('Ini buktinya. Adili dia.', 'Here is the proof. Try him.'), ke='adili', syarat=['bukti 3'], tampil=True,
              akibat=['bendera takhta5_a', f'quest_maju {Q5}'],
              alasan=('Bukti belum tiga: Cap Bayangan, Catatan Pembukuan, Surat Mata-Mata.',
                      'You do not hold three proofs: the Shadow Seal, the Secret Ledger, the Spy\'s Letter.')),
            P(('Aku akan menghadapinya sendiri.', 'I will face him myself.'), ke='sendiri', akibat=['bendera takhta5_b', f'quest_maju {Q5}']),
            P(('Belum saatnya.', 'Not yet.'))]),
        N('adili', ('Bukti sudah lengkap. Putusan dijatuhkan! Bawa Mo Ying ke hadapanku!',
                    'The evidence is complete. I pronounce the verdict! Bring Mo Ying before me!'),
          gestur='palu_hakim', suara='cerita.bao_verdict'),
        N('sendiri', ('Keras kepala. Ia ada di gudang Distrik Awan setiap malam, menghitung peti garam yang bukan garam. Kalau kau mati '
                      'di sana, aku tidak bisa mengadilimu.',
                      'Stubborn. He is in the Cloud District warehouse every night, counting salt crates that hold no salt. If you '
                      'die there, I cannot put you on trial.'),
          gestur='bicara_tangan'),
    ], prioritas=50, syarat=[tahap(Q5, 'pilih')])

    D('takhta5_mo_ying', 'tokoh:mo_ying', [
        N('mulai', ('Tuan yang terhormat. Kau membawa kertas-kertas yang berat. Biarkan saya meringankannya.',
                    'Honoured sir. You carry such heavy papers. Allow me to lighten them.'),
          gestur='sikap_tangan_depan', prop='kantong_uang', pilihan=[
            P(('(Terima kantong batu roh)', '(Take the purse of spirit stones)'), ke='terima',
              akibat=['beri_nb_pita 300', 'bendera takhta5_c', 'bendera bersekongkol', 'ambil cerita:catatan_pembukuan',
                      'ambil cerita:surat_mata_mata', 'nama_baik -10', f'quest_maju {Q5}']),
            P(('Kau dalangnya.', 'You are the one behind it.'), ke='topeng', akibat=['bendera takhta5_b', f'quest_maju {Q5}']),
            P(('Aku tidak tertarik.', 'I am not interested.'))]),
        N('terima', ('Bijak sekali. Kertas-kertas itu akan terbakar dengan indah. Kita tidak pernah bertemu, bukan?',
                     'Very wise. Those papers will burn beautifully. We never met, did we?'),
          gestur='hormat_dalam'),
        N('topeng', ('Kau terlambat menyadarinya...! Tapi gudang ini sepi, dan saya suka tempat yang sepi.',
                     'You noticed too late...! But this warehouse is quiet, and I do like quiet places.'),
          gestur='ancam', suara='cerita.moying_reveal', akibat=['adegan topeng'], pilihan=[
            P(('(Bertarung)', '(Fight)'), akibat=['mulai_duel'])]),
    ], prioritas=55, syarat=[tahap(Q5, 'pilih')])

    D('takhta5_mo_ying_duel', 'tokoh:mo_ying', [
        N('mulai', ('Hakim Bao mengirimmu sendirian? Betapa adilnya. Datanglah, saya akan mencatat kekalahanmu dengan rapi.',
                    'Magistrate Bao sent you alone? How very just. Come, I shall record your defeat neatly.'),
          gestur='ancam', suara='cerita.moying_reveal', pilihan=[
            P(('(Bertarung)', '(Fight)'), akibat=['mulai_duel']),
            P(('(Mundur dulu)', '(Withdraw for now)'))]),
    ], prioritas=55, syarat=[tahap(Q5, 'duel')])

    for hasil in ('a', 'b'):
        D(f'mo_ying_lenyap_{hasil}', 'tokoh:mo_ying', [
            N('mulai', ('Masih mencariku? Aku hanya bayangan yang lupa pergi...', 'Still looking for me? I am only a shadow that forgot to leave...'),
              gestur='hormat_dalam', akibat=['npc_pergi']),
        ], prioritas=60, syarat=[f'alur_bab5 {hasil}'])

    # ================================================================== chapter 6: Night of the Eclipse
    Q(Q6, 'tokoh:tie_hu', 'ibu_kota', ('Bab 6 · Malam Gerhana', 'Chapter 6 · Night of the Eclipse'),
      ('Langit memerah. Murid-murid Mo Ying menyerbu dari bayang-bayang bersama gelombang binatang. Pertahankan ibu kota.',
       'The sky turns red. Mo Ying\'s disciples strike from the shadows alongside a beast tide. Defend the capital.'),
      [T('bertahan', ('Kalahkan dua tetua iblis yang menyusup ke ibu kota.', 'Defeat two demonic elders who slipped into the capital.'),
         tujuan=['kalahkan peran:tetua_iblis x2'], akibat=['nama_baik 10', 'beri_nb_pita 80', 'alur 6 menang', 'kabar takhta_gerhana_menang pemain'],
         batas_hari=6, gagal_ke='mundur', arah='kota/kota_besar',
         hasil=('Fajar datang merah, lalu jingga, lalu biasa. Para penjaga menghitung yang selamat, dan untuk pertama kalinya Tie Hu '
                'tidak menghitung sendirian.',
                'Dawn came red, then amber, then ordinary. The guards counted the survivors, and for the first time Tie Hu did '
                'not count alone.')),
       T('mundur', ('Malam berlalu tanpamu.', 'The night passed without you.'), tujuan=SEGERA, ke='gagal',
         hasil=('Para penjaga bertahan sampai fajar dengan banyak luka. Tie Hu tidak menyalahkanmu; ia hanya tidak menyebut namamu.',
                'The guards held until dawn, at great cost. Tie Hu does not blame you; he simply does not say your name.'))],
      alur=True)

    D('takhta6_tie_hu', 'tokoh:tie_hu', [
        N('mulai', ('Gerbang timur! Mereka tidak datang dari jalan, mereka datang dari bayang-bayang! Kau, ikut aku!',
                    'The east gate! They are not coming by the road, they are coming out of the shadows! You, with me!'),
          gestur='hunus', prop='tombak', suara='kotabesar.beast_tide', pilihan=[
            P(('Di mana mereka?', 'Where are they?'), ke='muncul', syarat=['!bendera takhta6_dipanggil'],
              akibat=['bendera takhta6_dipanggil', 'panggil_peran tetua_iblis musuh 2']),
            P(('Aku sudah di sini.', 'I am already here.'))]),
        N('muncul', ('Itu mereka! Jubah hitam, teratai terbalik! Jangan biarkan mereka sampai ke lumbung!',
                     'There they are! Black robes, upturned lotus! Do not let them reach the granary!'),
          gestur='tunjuk'),
    ], prioritas=50, syarat=[tahap(Q6, 'bertahan')])

    D('takhta6_feng_ao', 'tokoh:feng_ao', [
        N('mulai', ('Hmph. Jangan salah paham. Aku di sini karena kota ini tempat aku akan menjadi nomor satu, dan kota yang terbakar '
                    'tidak punya daftar. Punggungmu kujaga. Sekali ini saja.',
                    'Hmph. Do not misunderstand. I am here because this is the city where I will be number one, and a burning city '
                    'has no rankings. I have your back. Just this once.'),
          gestur='hunus', akibat=['efek minecraft:strength 600 0', 'bendera takhta6_feng_ao']),
    ], prioritas=55, syarat=['alur 6', '!alur 7', 'bendera feng_ao_sahabat', '!bendera takhta6_feng_ao'])

    # ================================================================== chapter 7: The Coronation
    Q(Q7, 'tokoh:yan_wushuang', 'istana', ('Bab 7 · Penobatan', 'Chapter 7 · The Coronation'),
      ('Lentera dinyalakan di siang hari. Seluruh kota berkumpul untuk melihat siapa yang naik takhta.',
       'Lanterns are lit at noon. The whole city gathers to see who takes the throne.'),
      [T('alun', ('Datanglah ke ibu kota untuk upacara penobatan.', 'Come to the capital for the coronation.'),
         tujuan=['pergi kota/kota_besar'], arah='kota/kota_besar'),
       T('upacara', ('Saksikan Pangeran Yan Wushuang menyerahkan kota di Alun-alun Agung.',
                     'Watch Prince Yan Wushuang hand over the city in the Grand Square.'),
         cabang=[(['bendera bersekongkol'], 'sunyi')], ke='hormat'),
       T('hormat', ('Pangeran Yan menoleh padamu.', 'Prince Yan turns to you.'), tujuan=SEGERA,
         akibat=['gelar penjaga_takhta', 'beri cerita:lencana_istana', 'nama_baik 5', 'bendera audiensi_bebas'], ke='selesai',
         hasil=('Pangeran Yan menyematkan Lencana Istana di dadamu di depan seluruh kota. Pintu istana kini terbuka bagimu tanpa '
                'tanya.',
                'Prince Yan pinned the Palace Badge to your chest before the whole city. The palace doors now open to you without '
                'question.')),
       T('sunyi', ('Pangeran Yan tidak menoleh padamu.', 'Prince Yan does not look your way.'), tujuan=SEGERA, ke='selesai',
         hasil=('Kau berdiri di antara kerumunan. Tak ada yang menyebut namamu, dan mungkin itu yang terbaik.',
                'You stood among the crowd. Nobody spoke your name, and perhaps that was for the best.'))],
      alur=True)

    D('takhta7_upacara', 'tokoh:yan_wushuang', [
        N('mulai', ('Rakyat ibu kota! Sembilan ratus tahun lalu tujuh sekte menanam pedang mereka di batu ini dan bersumpah. Hari ini '
                    'aku menanam sesuatu yang lebih ringan: jabatanku.',
                    'People of the capital! Nine hundred years ago seven sects planted their swords in this stone and swore an oath. '
                    'Today I plant something lighter: my office.'),
          gestur='hormat_dalam', prop='papan_titah', pilihan=[
            P(('(Berlutut bersama rakyat)', '(Kneel with the people)'), ke='serah', akibat=['alur 7 penobatan'])]),
        N('serah', ('Mulai hari ini, kota ini kuserahkan kepada %s. Jagalah sumpahnya: di dalam tembok, pedang tidur.',
                    'From this day, I entrust this capital to %s. Keep its oath: within the walls, swords sleep.'),
          isian=['penguasa'], gestur='hormat', suara='cerita.coronation', akibat=['adegan penobatan', f'quest_maju {Q7}'], pilihan=[
            P(('Hidup ibu kota!', 'Long live the capital!'))]),
    ], prioritas=50, syarat=[tahap(Q7, 'upacara')])

    # ================================================================== aftermath
    D('ruyi_berkuasa', 'tokoh:yan_ruyi', [
        N('mulai', ('Lumbung sudah kubuka. Gu Lianshan ternyata tetap bekerja di perbendaharaan: ia menghitung, aku memutuskan. Kota '
                    'ini berjalan dengan dua kaki sekarang, dan tak satu pun kaki itu lemah.',
                    'I have opened the granaries. Gu Lianshan, it turns out, still works at the treasury: he counts, I decide. The '
                    'city walks on two legs now, and neither leg is frail.'),
          gestur='sikap_tangan_depan'),
    ], prioritas=10, syarat=['alur_penguasa ruyi'])
    D('gu_berkuasa', 'tokoh:gu_lianshan', [
        N('mulai', ('Toko-toko penuh, tembok timur sudah diperbaiki, dan denda naik seperempat. Kau tidak suka? Ketertiban itu mahal, '
                    'kawan. Seseorang harus membayarnya, dan lebih baik yang melanggar.',
                    'The shops are full, the east wall is mended, and fines are up by a quarter. You do not like it? Order is '
                    'expensive, friend. Someone must pay for it, and better the ones who break it.'),
          gestur='kipas', prop='kipas_lipat', pilihan=[
            P(('Di mana ajudanmu?', 'Where is your aide?'), ke='ajudan', syarat=['alur_bab5 c']),
            P(('Selamat, Penguasa.', 'Congratulations, Lord.'))]),
        N('ajudan', ('Mo Ying? Ia... menghilang sebulan lalu. Aneh. Ia tidak pernah lupa satu angka pun, tapi ia lupa berpamitan.',
                     'Mo Ying? He... disappeared a month ago. Strange. He never forgot a single figure, yet he forgot to say goodbye.'),
          gestur='bicara_tangan'),
    ], prioritas=10, syarat=['alur_penguasa gu'])
    D('ruyi_dipimpin_gu', 'tokoh:yan_ruyi', [
        N('mulai', ('Aku kalah. Tapi aku masih bisa membuka dapur umum di kuil setiap hari ketiga; Biksu Jing Xin meminjamkan panci '
                    'terbesarnya. Takhta bukan satu-satunya kunci lumbung, ternyata.',
                    'I lost. But I can still open a public kitchen at the temple every third day; Monk Jing Xin lent me his largest '
                    'pot. The throne is not the only key to the granary after all.'),
          gestur='tuang_teh', prop='teko'),
    ], prioritas=10, syarat=['alur_penguasa gu'])
    D('gu_dipimpin_ruyi', 'tokoh:gu_lianshan', [
        N('mulai', ('Penguasa Ruyi memintaku tetap di perbendaharaan. Aku menerimanya. Kekalahan yang menggaji tetaplah gaji, dan aku '
                    'masih menghitung lebih cepat dari siapa pun di istana.',
                    'Lady Ruyi asked me to stay at the treasury. I accepted. A defeat that pays a salary is still a salary, and I '
                    'still count faster than anyone in the palace.'),
          gestur='hitung_sempoa', prop='sempoa'),
    ], prioritas=10, syarat=['alur_penguasa ruyi'])
    D('yan_wushuang_pamit', 'tokoh:yan_wushuang', [
        N('mulai', ('Besok aku berangkat ke gunung. Tiga puluh tahun membeku; mungkin musim semi kultivasiku belum terlambat. Kalau '
                    'kau lewat di lereng utara dan melihat orang tua bermeditasi di bawah pinus, jangan bangunkan dia.',
                    'Tomorrow I leave for the mountains. Thirty years frozen; perhaps the spring of my cultivation is not yet too '
                    'late. If you pass the northern slopes and see an old man meditating under a pine, do not wake him.'),
          gestur='tatap_langit'),
    ], prioritas=10, syarat=['alur 8'])
    D('kakek_lumpur_terungkap', 'tokoh:kakek_lumpur', [
        N('mulai', ('Jangan panggil aku Tetua. Aku sudah lama pensiun dari menjadi seseorang. ...Anak itu, Dou, belajar menulis '
                    'sekarang. Itu lebih penting dari takhta mana pun.',
                    'Do not call me Elder. I retired from being somebody long ago. ...That child, Dou, is learning to write now. '
                    'That matters more than any throne.'),
          gestur='duduk_lantai', pilihan=[
            P(('Ajari aku sesuatu, Kek.', 'Teach me something, grandpa.'), ke='ajar'),
            P(('Terima kasih untuk malam itu.', 'Thank you for that night.'))]),
        N('ajar', ('Satu saja: kekuatan yang paling kuat adalah yang tidak perlu dipakai. Sekarang pergi, aku mau tidur.',
                   'Just one thing: the strongest power is the one that never needs using. Now go; I want to sleep.'),
          gestur='tidur_duduk', akibat=['efek minecraft:resistance 300 0']),
    ], prioritas=10, syarat=['alur 7', 'alur_dou'])

    # ================================================================== arc rumours (38.S.5)
    umum = ['warga', 'pemilik_teh', 'pemilik_penginapan', 'koki', 'petugas_gilda', 'anak', 'pedagang_keliling', 'pengemis', 'kuli']
    K('takhta', 'takhta_bab2', 'alur_bab2', ('Ada yang melihat orang berjubah hitam keluar-masuk Kampung Kumuh malam-malam.',
                                             'Someone saw men in black robes slipping in and out of the slums at night.'),
      bobot=10, umur=6, penyebar=umum)
    K('takhta', 'takhta_bab3', 'alur_bab3', ('Pewaris Yan sakit lagi... Kata tabib istana, napasnya berbau besi.',
                                             'Heir Yan is ill again... The palace physicians say her breath smells of iron.'),
      bobot=10, umur=6, penyebar=umum)
    K('takhta', 'takhta_bab4', 'alur_bab4', ('Pangeran Yan mau menyepi! Kedai-kedai teh bertaruh: Tuan Putri Ruyi atau Menteri Gu?',
                                             'Prince Yan is withdrawing! The teahouses are taking bets: Lady Ruyi or Minister Gu?'),
      bobot=10, umur=6, penyebar=umum)
    K('takhta', 'takhta_bab5', 'alur_bab5', ('Hakim Bao tidak tidur tiga malam. Kalau Hakim Bao tidak tidur, seseorang di kota ini akan '
                                             'kehilangan tidurnya juga.',
                                             'Magistrate Bao has not slept in three nights. When Magistrate Bao does not sleep, someone in '
                                             'this city is about to lose sleep too.'), bobot=10, umur=6, penyebar=umum)
    K('takhta', 'takhta_bab6', 'alur_bab6', ('Sejak langit memerah, pintu-pintu dikunci lebih awal. Kata orang, bayang-bayang di gang '
                                             'timur bergerak sendiri.',
                                             'Since the sky turned red, doors are locked earlier. People say the shadows in the east '
                                             'alleys move on their own.'), bobot=10, umur=6, penyebar=umum)
    K('takhta', 'takhta_bab7', 'alur_bab7', ('Lentera dipasang di siang hari! Penobatan sebentar lagi; Gendut Zhu memasak sejak subuh.',
                                             'Lanterns hung at noon! The coronation is near; Fat Zhu has been cooking since dawn.'),
      bobot=10, umur=4, penyebar=umum)
    K('takhta', 'takhta_bab2_hilang', 'alur_bab2_hilang', ('Anak-anak Kampung Kumuh tidak pernah pulang. Penjaga bilang mereka kabur '
                                                           'sendiri. Tidak ada yang percaya.',
                                                           'The slum children never came home. The guards say they ran away. Nobody believes it.'),
      bobot=10, umur=8, penyebar=umum)
    K('takhta', 'takhta_bab3_lemah', 'alur_bab3_lemah', ('Pewaris selamat, tapi tak bisa berdiri lama. Pedagang mulai berbisik bahwa '
                                                         'Menteri Gu lebih pantas.',
                                                         'The heir lives, but cannot stand for long. The merchants whisper that '
                                                         'Minister Gu is the better choice.'), bobot=10, umur=8, penyebar=umum)
    K('takhta', 'takhta_bab5_tersembunyi', 'alur_bab5_tersembunyi', ('Kasus orang hilang ditutup. Hakim Bao menolak menandatanganinya.',
                                                                     'The case of the missing was closed. Magistrate Bao refused to sign it.'),
      bobot=10, umur=8, penyebar=umum)
    K('takhta', 'takhta_bab6_bertahan', 'alur_bab6_bertahan', ('Gerbang timur bertahan, tapi lumbung terbakar separuh. Bubur hari ini '
                                                               'lebih encer.',
                                                               'The east gate held, but half the granary burned. Today\'s porridge is thinner.'),
      bobot=10, umur=8, penyebar=umum)
    K('takhta', 'takhta_bab7_ruyi', 'alur_bab7_ruyi', ('Penguasa Ruyi membuka lumbung di hari pertamanya. Di Kampung Kumuh orang '
                                                       'menyalakan dupa untuknya.',
                                                       'Lady Ruyi opened the granaries on her first day. In the slums people light '
                                                       'incense for her.'), bobot=10, umur=10, penyebar=umum)
    K('takhta', 'takhta_bab7_gu', 'alur_bab7_gu', ('Penguasa Gu menaikkan denda seperempat. Pasar Gelap dirazia dua kali minggu ini.',
                                                   'Lord Gu raised the fines by a quarter. The Black Market was raided twice this week.'),
      bobot=10, umur=10, penyebar=umum)
    K('takhta', 'takhta_tawanan_pulang', 'takhta_tawanan_pulang', ('%s membawa pulang anak-anak Kampung Kumuh. Dou menangis sambil '
                                                                   'tertawa.',
                                                                   '%s brought the slum children home. Dou laughed and cried at once.'),
      bobot=10, umur=8)
    K('takhta', 'takhta_dukung_ruyi', 'takhta_dukung_ruyi', ('Tiga kepala gilda menandatangani dukungan untuk Tuan Putri Ruyi. '
                                                             'Pedagang kain di pasar timur cemberut.',
                                                             'Three guild heads signed their backing for Lady Ruyi. The cloth '
                                                             'merchants of the east market are sulking.'), bobot=10, umur=6)
    K('takhta', 'takhta_dukung_gu', 'takhta_dukung_gu', ('Para kepala gilda memilih Menteri Gu. Kata orang, harga garam akan turun.',
                                                         'The guild heads chose Minister Gu. They say the price of salt will fall.'),
      bobot=10, umur=6)
    K('takhta', 'takhta_sidang', 'takhta_sidang', ('Sidang terbuka di Yamen! Ajudan Menteri ternyata tetua iblis, dan ia kabur menembus '
                                                   'atap. %s yang membawa buktinya.',
                                                   'An open trial at the Yamen! The Minister\'s aide was a demonic elder, and he fled '
                                                   'through the roof. %s brought the proof.'), bobot=10, umur=10)
    K('takhta', 'takhta_topeng', 'takhta_topeng', ('Ada pertarungan di gudang Distrik Awan semalam. Peti-peti garam pecah, isinya '
                                                   'jimat hitam. Topeng seseorang ikut pecah.',
                                                   'There was a fight in a Cloud District warehouse last night. The salt crates '
                                                   'broke open: black talismans inside. Someone\'s mask broke too.'), bobot=10, umur=10)
    K('takhta', 'takhta_suap', 'takhta_suap', ('Kasus orang hilang tiba-tiba ditutup. Asap di cerobong gudang ajudan Menteri berbau kertas.',
                                               'The case of the missing was suddenly closed. The smoke from the Minister\'s aide\'s '
                                               'chimney smelled of paper.'), bobot=10, umur=8)
    K('takhta', 'takhta_gerhana_menang', 'takhta_gerhana_menang', ('Malam gerhana dipatahkan di gerbang timur. Tie Hu bilang %s '
                                                                   'bertarung seperti dua penjaga sekaligus.',
                                                                   'The eclipse night was broken at the east gate. Tie Hu says %s '
                                                                   'fought like two guards at once.'), bobot=10, umur=10)
