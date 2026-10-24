"""38ST content: the everyday talk of the 23 recurring characters (DESIGN 38.S.2). Quest offers and quest-stage talks live
with their quests (cerita_isi_quest, cerita_isi_takhta) and carry a higher priority, so these dialogs are what a
character says when nothing special is going on. Each one has its voice, gesture and temperament."""


def isi(g):
    D, N, P = g.D, g.N, g.P

    # ------------------------------------------------------------------ Yan Wushuang, the Warden Prince (calm, weary)
    D('yan_wushuang_sapa', 'tokoh:yan_wushuang', [
        N('mulai', ('Angkat kepalamu. Di dalam tembok ini, bahkan seorang pangeran hanyalah penjaga yang terlalu lama berjaga.',
                    'Raise your head. Within these walls even a prince is only a guard who has stood watch too long.'),
          gestur='sikap_tangan_belakang', suara='kotabesar.palace_audience', pilihan=[
            P(('Bagaimana kota ini berdiri?', 'How did this city come to be?'), ke='sejarah'),
            P(('Kau terlihat lelah, Yang Mulia.', 'You look tired, Your Highness.'), ke='lelah'),
            P(('Mohon izin undur diri.', 'By your leave.'))]),
        N('sejarah', ('Sembilan ratus tahun lalu tujuh sekte lelah berperang. Mereka menanam tujuh pedang patah di batu alun-alun dan bersumpah: '
                      '"Di dalam tembok, pedang tidur." Sejak itu Pangeran Penjaga dipilih dewan gilda, bukan dilahirkan.',
                      'Nine hundred years ago seven sects grew tired of war. They planted seven broken swords in the stone of the square '
                      'and swore: "Within the walls, swords sleep." Since then the Warden Prince is chosen by the guild council, not born.'),
          gestur='cerita', akibat=['lore kota_besar'], pilihan=[P(('Sumpah yang indah.', 'A fine oath.'), ke='sumpah'), P(('Terima kasih, Yang Mulia.', 'Thank you, Your Highness.'))]),
        N('sumpah', ('Indah, dan berat. Setiap kali sumpah itu dilanggar, akulah yang harus menanggung darahnya. Jagalah pedangmu tetap tidur di sini.',
                     'Fine, and heavy. Each time the oath is broken I am the one who must answer for the blood. Keep your sword asleep here.'),
          gestur='hormat_dalam'),
        N('lelah', ('Tiga puluh tahun aku tidak bertapa satu malam pun. Kultivasiku membeku di tempat yang sama, seperti air kolam di musim dingin. '
                    'Kadang aku iri pada pengemis di Kampung Kumuh; setidaknya ia bebas memilih tidurnya.',
                    'For thirty years I have not meditated a single night. My cultivation is frozen in one place, like a pond in winter. '
                    'Sometimes I envy the beggars of the slums; at least they may choose where to sleep.'),
          gestur='tatap_langit', pilihan=[
            P(('Siapa yang akan menggantikanmu?', 'Who will succeed you?'), ke='pengganti'),
            P(('Semoga kau segera bisa menyepi.', 'May you soon find your solitude.'))]),
        N('pengganti', ('Putriku Ruyi punya hati yang lurus, tetapi tubuhnya lemah. Menteri Gu cakap dan dicintai pedagang. '
                        'Dewan gilda akan memilih; aku hanya bisa berharap mereka memilih dengan mata terbuka.',
                        'My daughter Ruyi has an upright heart, but a frail body. Minister Gu is able and beloved of the merchants. '
                        'The guild council will choose; I can only hope they choose with open eyes.'),
          gestur='bicara_tangan'),
    ])

    # ------------------------------------------------------------------ Yan Ruyi, the heir (gentle, stubborn about justice)
    D('yan_ruyi_sapa', 'tokoh:yan_ruyi', [
        N('mulai', ('Kau orang luar, ya? Ceritakan padaku, bagaimana rakyat di luar tembok memandang istana ini?',
                    'You are from outside, aren\'t you? Tell me, how do people beyond the walls see this palace?'),
          gestur='sikap_tangan_depan', pilihan=[
            P(('Mereka bilang istana jauh dari Kampung Kumuh.', 'They say the palace is far from the slums.'), ke='kumuh'),
            P(('Mereka memuji ketertibannya.', 'They praise its order.'), ke='tertib'),
            P(('Aku tidak tahu, Tuan Putri.', 'I do not know, my lady.'))]),
        N('kumuh', ('Mereka benar. Aku sudah tiga kali meminta ayah membuka lumbung untuk Kampung Kumuh. Tiga kali Menteri Gu bilang kas kota tidak cukup. '
                    'Lalu dari mana datangnya uang untuk panggung Lelang Agung?',
                    'They are right. Three times I asked father to open the granaries to the slums. Three times Minister Gu said the treasury '
                    'was short. Then where does the money for the Grand Auction stage come from?'),
          gestur='marah_tunjuk', pilihan=[
            P(('Kau tidak takut bicara begitu?', 'Aren\'t you afraid to say that?'), ke='takut'),
            P(('Aku akan mengingatnya.', 'I will remember that.'))]),
        N('takut', ('Takut? Kultivasiku hanya cukup untuk menyalakan lilin. Kalau aku diam juga, apa lagi yang tersisa dariku?',
                    'Afraid? My cultivation is barely enough to light a candle. If I kept quiet too, what would be left of me?'),
          gestur='tertawa'),
        N('tertib', ('Ketertiban itu dibayar oleh orang-orang yang tidak pernah kau lihat. Lain kali, lihatlah tukang sapu jalan sebelum fajar.',
                     'That order is paid for by people you never see. Next time, look at the street sweepers before dawn.'),
          gestur='bicara_tangan'),
    ])

    # ------------------------------------------------------------------ Gu Lianshan, the minister (clever, ambitious, not evil)
    D('gu_lianshan_sapa', 'tokoh:gu_lianshan', [
        N('mulai', ('Ah, wajah baru dengan aura yang tidak murahan. Kota ini makmur karena orang-orang sepertimu datang membawa batu roh. '
                    'Ada yang bisa kubantu?',
                    'Ah, a new face with an aura that is not cheap. This city prospers because people like you bring spirit stones. '
                    'How may I help?'),
          gestur='kipas', prop='kipas_lipat', pilihan=[
            P(('Apa pendapatmu tentang pewaris?', 'What do you think of the heir?'), ke='pewaris'),
            P(('Ceritakan tentang Lelang Agung.', 'Tell me about the Grand Auction.'), ke='lelang'),
            P(('Siapa orang di belakangmu itu?', 'Who is that man behind you?'), ke='ajudan'),
            P(('Tidak ada, terima kasih.', 'Nothing, thank you.'))]),
        N('pewaris', ('Tuan Putri Ruyi punya hati emas. Sayang, hati emas tidak bisa membayar gaji empat puluh penjaga. '
                      'Kota butuh tangan yang bisa menghitung.',
                      'Lady Ruyi has a heart of gold. Sadly, a heart of gold cannot pay forty guards. The city needs hands that can count.'),
          gestur='hitung_sempoa', prop='sempoa'),
        N('lelang', ('Delapan lot, penawar dari sembilan sekte, dan komisi yang cukup untuk memperbaiki tembok timur. '
                     'Datanglah saat lonceng lelang berbunyi; aku sendiri sering ikut menawar.',
                     'Eight lots, bidders from nine sects, and a commission large enough to mend the east wall. '
                     'Come when the auction bell rings; I often bid myself.'),
          gestur='bicara_tangan'),
        N('ajudan', ('Mo Ying, ajudanku. Rajin, teliti, tidak pernah lupa satu angka pun. Kadang aku lupa ia ada di ruangan.',
                     'Mo Ying, my aide. Diligent, careful, never forgets a single figure. Sometimes I forget he is in the room.'),
          gestur='kipas', prop='kipas_lipat'),
    ])

    # ------------------------------------------------------------------ Mo Ying, the aide (too polite, eyes never smile)
    D('mo_ying_sapa', 'tokoh:mo_ying', [
        N('mulai', ('Selamat datang, Tuan yang terhormat. Menteri sedang sibuk; apa pun keperluanmu, saya yang akan mencatatnya. Dengan senang hati.',
                    'Welcome, honoured guest. The Minister is busy; whatever your business, I shall note it. With pleasure.'),
          gestur='hormat_dalam', prop='kuas', pilihan=[
            P(('Kau sudah lama melayani Menteri Gu?', 'Have you served Minister Gu long?'), ke='lama'),
            P(('Tanganmu dingin sekali.', 'Your hands are very cold.'), ke='dingin'),
            P(('Tidak ada keperluan.', 'No business.'))]),
        N('lama', ('Tiga tahun, tujuh bulan, sebelas hari. Saya suka angka; angka tidak pernah bertanya dari mana kita datang.',
                   'Three years, seven months, eleven days. I like numbers; numbers never ask where one comes from.'),
          gestur='menulis', prop='kuas'),
        N('dingin', ('Sirkulasi saya buruk sejak kecil, Tuan. Terima kasih sudah memperhatikan. Saya akan... mengingat kebaikan Tuan.',
                     'Poor circulation since childhood, sir. Thank you for noticing. I shall... remember your kindness.'),
          gestur='sikap_tangan_depan'),
    ])

    # ------------------------------------------------------------------ Bao Qing, the magistrate (just, stern, dry humour)
    D('bao_qing_sapa', 'tokoh:bao_qing', [
        N('mulai', ('Yamen tidak menerima suap, tidak menerima pujian, dan tidak menerima alasan. Selain itu, silakan.',
                    'The Yamen accepts no bribes, no flattery and no excuses. Other than that, go ahead.'),
          gestur='sikap_lipat_tangan', pilihan=[
            P(('Apa hukum kota ini?', 'What are the laws here?'), ke='hukum'),
            P(('Bagaimana cara membayar denda?', 'How do I pay a fine?'), ke='denda'),
            P(('Apakah hakim pernah tersenyum?', 'Does a magistrate ever smile?'), ke='senyum'),
            P(('Permisi.', 'Excuse me.'))]),
        N('hukum', ('Tiga hukum saja. Pertama: di dalam tembok, pedang tidur. Kedua: utang dibayar, janji ditepati. '
                    'Ketiga: setelah genderang malam, gerbang tertutup bagi siapa pun, termasuk aku.',
                    'Only three laws. First: within the walls, swords sleep. Second: debts are paid, promises kept. '
                    'Third: after the night drum, the gates are closed to all, myself included.'),
          gestur='palu_hakim', akibat=['lore kota_besar.pusat']),
        N('denda', ('Datang ke meja ini, sebut kesalahanmu, bayar, pergi. Yang melarikan diri mendapat wajahnya di papan buronan. '
                    'Pelukis Yamen sangat teliti.',
                    'Come to this desk, name your wrong, pay, leave. Those who flee get their faces on the wanted board. '
                    'The Yamen painter is very thorough.'),
          gestur='tunjuk'),
        N('senyum', ('Sekali, tahun lalu. Seorang pencuri mengaku karena takut pada ibunya, bukan padaku. Itu lucu.',
                     'Once, last year. A thief confessed because he feared his mother, not me. That was funny.'),
          gestur='sikap_lipat_tangan'),
    ])

    # ------------------------------------------------------------------ Tie Hu, captain of the gate (honest, rigid)
    D('tie_hu_sapa', 'tokoh:tie_hu', [
        N('mulai', ('Selamat datang di ibu kota. Bertarung sendiri di dalam tembok dilarang. Namamu?',
                    'Welcome to the capital. Private fights are forbidden within the walls. Your name?'),
          gestur='berdiri_jaga', prop='tombak', suara='kotabesar.gate_welcome', pilihan=[
            P(('Kapan gerbang ditutup?', 'When do the gates close?'), ke='jam'),
            P(('Ada yang perlu kuketahui?', 'Anything I should know?'), ke='nasihat'),
            P(('Kau terlihat tegang.', 'You look tense.'), ke='tegang'),
            P(('Aku hanya lewat.', 'Just passing.'))]),
        N('jam', ('Genderang malam berbunyi jam sepuluh. Setelah itu gerbang tutup sampai fajar. Kalau terlambat, dua Nilai Batu untuk penjaga, '
                  'dan jangan bilang padaku kau tidak tahu.',
                  'The night drum sounds at ten. After that the gates stay shut until dawn. If you are late, two Spirit Value for the guard, '
                  'and do not tell me you did not know.'),
          gestur='tunjuk'),
        N('nasihat', ('Jangan lewat Kampung Kumuh setelah gelap. Orang hilang di sana, dan atasanku bilang itu urusan Yamen, bukan gerbang.',
                      'Do not cross the slums after dark. People go missing there, and my superiors say that is the Yamen\'s business, not the gate\'s.'),
          gestur='bisik'),
        N('tegang', ('Aku? Tidak. Seorang kapten penjaga tidak tegang. Ia hanya... berhati-hati sejak Menteri mulai memeriksa buku gerbang sendiri.',
                     'Me? No. A gate captain is never tense. He is only... careful, since the Minister began checking the gate book himself.'),
          gestur='sikap_tombak', prop='tombak'),
    ])

    # ------------------------------------------------------------------ Madam Qian Duoduo, the banker (counts everything)
    D('qian_duoduo_sapa', 'tokoh:qian_duoduo', [
        N('mulai', ('Selamat datang di rumah uang. Batu rohmu aman bersama kami. Mau menyimpan, menarik, atau sekadar menghitung kekayaan?',
                    'Welcome to the money house. Your spirit stones are safe with us. Depositing, withdrawing, or just counting your wealth?'),
          gestur='hitung_sempoa', prop='sempoa', suara='kotabesar.bank_welcome', pilihan=[
            P(('Apa itu surat wesel?', 'What is a money note?'), ke='wesel'),
            P(('Rumah uang ini sudah lama berdiri?', 'Has this house stood long?'), ke='lama'),
            P(('Nanti saja.', 'Later.'))]),
        N('wesel', ('Selembar kertas yang bernilai sebanyak batu yang kau titipkan. Bisa ditukar di rumah uang ibu kota mana pun. '
                    'Lebih ringan dari batu, lebih sulit dicuri, asal kau tidak menerima wesel dari orang asing.',
                    'A paper worth as many stones as you deposited, cashable at the money house of any capital. Lighter than stone and harder '
                    'to steal, as long as you never accept a note from a stranger.'),
          gestur='bicara_tangan', suara='kotabesar.bank_note'),
        N('lama', ('Lebih tua dari kota ini. Rumah uang Qian sudah menghitung batu sebelum tujuh sekte berdamai. '
                   'Perang datang dan pergi; bunga tetap berjalan.',
                   'Older than the city. House Qian counted stones before the seven sects made peace. Wars come and go; interest keeps running.'),
          gestur='tertawa', akibat=['lore kota_besar.pasar']),
    ])

    # ------------------------------------------------------------------ Lao Shi One-Eye, stone gambling (sly, honour code)
    D('lao_shi_sapa', 'tokoh:lao_shi', [
        N('mulai', ('Batu mentah, Tuan? Di dalamnya bisa ada giok, bisa juga cuma batu. Langit atau bumi, itu takdirmu sendiri.',
                    'Raw stone, sir? Inside could be jade, or just stone. Heaven or earth, that is your own fate.'),
          gestur='asah_pisau', prop='batu_mentah', pilihan=[
            P(('Bagaimana kau kehilangan matamu?', 'How did you lose your eye?'), ke='mata'),
            P(('Apa kau pernah curang?', 'Have you ever cheated?'), ke='curang'),
            P(('Lain kali.', 'Another time.'))]),
        N('mata', ('Membelah batu terkutuk dua puluh tahun lalu. Batu itu melihatku lebih lama daripada aku melihatnya. '
                   'Sejak itu aku tahu: ada batu yang sebaiknya tetap tertutup.',
                   'Cutting a cursed stone twenty years ago. The stone looked at me longer than I looked at it. '
                   'Since then I know: some stones are better left closed.'),
          gestur='cerita'),
        N('curang', ('Bandar yang curang tidak hidup lama di ibu kota. Aku benci penipu lebih dari aku cinta uang, dan aku sangat cinta uang.',
                     'A cheating banker does not live long in the capital. I hate swindlers more than I love money, and I love money very much.'),
          gestur='tertawa'),
    ])

    # ------------------------------------------------------------------ Young Ou Ye, master smith (perfectionist, few words)
    D('ou_ye_sapa', 'tokoh:ou_ye', [
        N('mulai', ('Besi yang baik menunggu api yang baik.', 'Good iron waits for good fire.'),
          gestur='palu_landasan', prop='palu', suara='kotabesar.smith_master', pilihan=[
            P(('Kau keturunan Ou Ye yang termasyhur?', 'Are you of the famous Ou Ye line?'), ke='leluhur'),
            P(('Pedang apa yang terbaik?', 'What makes the best sword?'), ke='pedang'),
            P(('Aku tidak mengganggu.', 'I won\'t disturb you.'))]),
        N('leluhur', ('Keturunan muridnya. Nama itu terlalu besar untuk palu ini. Aku masih menempa untuk layak memakainya.',
                      'Of his students\' line. The name is too big for this hammer. I still forge to deserve it.'),
          gestur='palu_landasan', prop='palu', akibat=['lore gilda.pandai']),
        N('pedang', ('Yang tahu siapa pemiliknya.', 'One that knows who holds it.'), gestur='sikap_lipat_tangan'),
    ])

    # ------------------------------------------------------------------ Elder Dan Chen, head alchemist (proud to novices)
    D('dan_chen_sapa', 'tokoh:dan_chen', [
        N('mulai', ('Hm. Tanganmu belum pernah mengendalikan api, ya? Gilda Alkemis bukan dapur. Jangan sentuh tungku mana pun.',
                    'Hm. Your hands have never governed a flame, have they? The Alchemist Guild is no kitchen. Touch no furnace.'),
          gestur='sikap_angkuh', pilihan=[
            P(('Bagaimana cara menjadi alkemis gilda?', 'How does one become a guild alchemist?'), ke='ujian'),
            P(('Benarkah kau menghentikan wabah?', 'Is it true you stopped a plague?'), ke='wabah'),
            P(('Maaf mengganggu, Tetua.', 'Forgive the intrusion, Elder.'))]),
        N('ujian', ('Lulus ujian Tingkat Alkemis. Racik satu pil tanpa membakar tungku, tanpa menangis, tanpa bertanya dua kali. '
                    'Kalau kau serius, bicaralah padaku lagi dengan bahan di tanganmu.',
                    'Pass the Alchemist Rank exam. Brew one pill without burning the furnace, without crying, without asking twice. '
                    'If you are serious, talk to me again with ingredients in hand.'),
          gestur='tunjuk'),
        N('wabah', ('Wabah Daun Kuning, empat puluh tahun lalu. Rumus pertamaku menyelamatkan separuh kota. Separuh lagi... '
                    'aku masih menghitungnya setiap malam.',
                    'The Yellow Leaf plague, forty years ago. My first formula saved half the city. The other half... I still count them every night.'),
          gestur='tatap_langit', akibat=['lore gilda.alkemis']),
    ])

    # ------------------------------------------------------------------ Lin Fei the One-Armed, guild head (blunt ex-hunter)
    D('lin_fei_sapa', 'tokoh:lin_fei', [
        N('mulai', ('Mau misi? Papan ada di sana. Mau cerita? Bayar minumanku dulu.',
                    'Want a mission? The board is over there. Want a story? Buy my drink first.'),
          gestur='sikap_berkacak', pilihan=[
            P(('Lenganmu kenapa?', 'What happened to your arm?'), ke='lengan'),
            P(('Apa itu Daftar Naga Muda?', 'What is the Young Dragon List?'), ke='daftar'),
            P(('Aku cari misi saja.', 'Just looking for work.'))]),
        N('lengan', ('Gelombang Binatang dua puluh tahun lalu. Seekor harimau tulang menggigitnya di tembok timur. Aku menggigit balik. '
                     'Ia mati, aku hidup. Kau hitung sendiri siapa yang menang.',
                     'The Beast Tide twenty years ago. A bone tiger bit it on the east wall. I bit back. It died, I lived. You count who won.'),
          gestur='tertawa', akibat=['lore gilda.kultivator']),
        N('daftar', ('Seratus kultivator muda terbaik kota ini, dicatat gilda. Yang di atas mendapat misi bagus dan musuh banyak. '
                     'Bocah Feng Ao sedang memanjat dengan sikut.',
                     'The city\'s hundred finest young cultivators, recorded by the guild. Those on top get good missions and many enemies. '
                     'The boy Feng Ao is climbing with his elbows.'),
          gestur='tunjuk'),
    ])

    # ------------------------------------------------------------------ Xiao Mei, guild clerk (cheerful gossip)
    D('xiao_mei_sapa', 'tokoh:xiao_mei', [
        N('mulai', ('Ada misi baru. Yakin dengan kemampuanmu? Eh, tapi sebelum itu, sudah dengar kabar terbaru?',
                    'New commissions are posted. Confident in your skill? Oh, but before that, have you heard the latest?'),
          gestur='lambai', suara='kotabesar.guild_bounty', pilihan=[
            P(('Kabar apa?', 'What news?'), ke='kabar'),
            P(('Siapa yang paling sering mengambil misi?', 'Who takes the most missions?'), ke='siapa'),
            P(('Nanti saja, Mei.', 'Later, Mei.'))]),
        N('kabar', None, kabar=True, gestur='bisik', pilihan=[
            P(('Ada lagi?', 'Anything else?'), ke='kabar2'), P(('Terima kasih.', 'Thanks.'))]),
        N('kabar2', None, kabar=True, gestur='bisik'),
        N('siapa', ('Feng Ao, tentu saja. Ia mengambil tiga misi sekaligus dan menyelesaikan dua. Yang ketiga ia tinggalkan untuk orang lain '
                    'supaya bisa bilang orang lain gagal.',
                    'Feng Ao, of course. He takes three at once and finishes two. The third he leaves to someone else so he can say they failed.'),
          gestur='tertawa'),
    ])

    # ------------------------------------------------------------------ Feng Ao, examiner and rival (young, proud, gifted)
    D('feng_ao_sapa', 'tokoh:feng_ao', [
        N('mulai', ('Hmph. Kau lagi. Sudah masuk Daftar Naga Muda? Belum? Wajar.',
                    'Hmph. You again. On the Young Dragon List yet? No? Of course not.'),
          gestur='sikap_angkuh', pilihan=[
            P(('Kenapa kau begitu sombong?', 'Why so arrogant?'), ke='sombong'),
            P(('Mau tanding latih?', 'Care to spar?'), ke='tanding', syarat=['ranah 2'], tampil=True,
              alasan=('Feng Ao tidak melayani yang belum Membangun Fondasi.', 'Feng Ao does not spar below Foundation.')),
            P(('Sampai jumpa di arena.', 'See you in the arena.'))]),
        N('sombong', ('Sombong? Aku hanya jujur lebih awal dari orang lain. Ayahku pedagang kain. Tidak ada yang memberiku apa pun. '
                      'Kalau aku tidak meninggikan kepalaku, siapa yang akan melihatnya?',
                      'Arrogant? I am only honest sooner than others. My father sells cloth. No one gave me anything. '
                      'If I do not raise my head, who would see it?'),
          gestur='marah_tunjuk'),
        N('tanding', ('Bagus. Tanpa senjata tajam, tanpa teknik pembunuh. Yang jatuh tiga kali kalah. Siap?',
                      'Good. No sharp blades, no killing arts. Three falls loses. Ready?'),
          gestur='hunus', pilihan=[
            P(('Siap.', 'Ready.'), akibat=['mulai_duel']),
            P(('Lain kali.', 'Another time.'))]),
    ])

    # ------------------------------------------------------------------ Master Wen, librarian (forgetful, encyclopaedic)
    D('tuan_wen_sapa', 'tokoh:tuan_wen', [
        N('mulai', ('Harap tenang di perpustakaan. Nah. Kau mencari kitab apa? Tunggu, di mana aku menaruh kacamataku...',
                    'Quiet in the library, please. Now. Which book do you seek? Wait, where did I put my spectacles...'),
          gestur='membaca', prop='kitab', suara='kotabesar.library_hush', pilihan=[
            P(('Ceritakan tentang perpustakaan ini.', 'Tell me about this library.'), ke='perpus'),
            P(('Apa yang kau ketahui tentang gilda-gilda?', 'What do you know of the guilds?'), ke='gilda'),
            P(('Kacamatamu di kepalamu.', 'Your spectacles are on your head.'), ke='kacamata')]),
        N('perpus', ('Salinan kitab dasar setiap sekte yang pernah menandatangani sumpah kota tersimpan di sini. Tidak ada sekte yang bisa '
                     'menyerang ibu kota tanpa membakar ilmunya sendiri. Itulah kenetralan yang sesungguhnya: bukan tembok, tetapi rak buku.',
                     'Copies of the founding manuals of every sect that ever swore the city oath are kept here. No sect can attack the capital '
                     'without burning its own knowledge. That is true neutrality: not walls, but bookshelves.'),
          gestur='cerita', prop='kitab', akibat=['lore kota_besar.cendekia']),
        N('gilda', ('Tiga gilda, tiga watak. Alkemis menghitung api, Pandai Pusaka menghitung pukulan, Kultivator menghitung kepala binatang. '
                    'Mereka bertengkar sejak kota berdiri, dan itulah sebabnya kota tidak pernah dikuasai satu pihak.',
                    'Three guilds, three tempers. Alchemists count flames, smiths count blows, cultivators count beast heads. '
                    'They have quarrelled since the city was founded, and that is why no single party ever ruled it.'),
          gestur='bicara_tangan'),
        N('kacamata', ('Oh! Benar juga. Terima kasih, terima kasih. Empat puluh tahun aku mencari benda yang sama di tempat yang sama.',
                       'Oh! So they are. Thank you, thank you. Forty years I have looked for the same thing in the same place.'),
          gestur='tertawa'),
    ])

    # ------------------------------------------------------------------ Monk Jing Xin, the temple (patient, wise)
    D('jing_xin_sapa', 'tokoh:jing_xin', [
        N('mulai', ('Biarkan lonceng menjernihkan hatimu. Duduklah sebentar, kalau kau mau.',
                    'Let the bell clear your heart. Sit a while, if you like.'),
          gestur='sikap_doa', suara='kotabesar.temple_bell', pilihan=[
            P(('Bagaimana menenangkan iblis hati?', 'How does one calm a heart demon?'), ke='iblis'),
            P(('Apa itu Cermin Hati?', 'What is the Heart Mirror?'), ke='cermin'),
            P(('Aku hanya ingin berdoa.', 'I only wish to pray.'), ke='doa')]),
        N('iblis', ('Iblis hati tidak dikalahkan, ia dikenali. Saat kau bisa menyebut namanya tanpa marah, ia tinggal bayangan.',
                    'A heart demon is not defeated; it is recognised. When you can say its name without anger, it is only a shadow.'),
          gestur='hormat_dalam'),
        N('cermin', ('Di gua di luar kota ada kolam yang tidak memantulkan wajah, melainkan niat. Seorang biksu pernah melihat iblisnya sendiri '
                     'di sana dan tertawa sampai menangis. Biksu itu guruku.',
                     'In a cave outside the city lies a pool that reflects not faces but intentions. A monk once saw his own demon there and '
                     'laughed until he wept. That monk was my teacher.'),
          gestur='cerita', akibat=['lore gua.cermin_hati']),
        N('doa', ('Dupa ada di sebelah kiri. Doa tidak perlu suara; langit mendengar yang tidak diucapkan.',
                  'Incense is on the left. Prayer needs no voice; heaven hears what is not said.'),
          gestur='sujud_dupa', prop='dupa', akibat=['efek minecraft:luck 600 0']),
    ])

    # ------------------------------------------------------------------ Bibi Hong, innkeeper (talkative, motherly)
    D('bibi_hong_sapa', 'tokoh:bibi_hong', [
        N('mulai', ('Selamat datang! Mau menginap atau makan? Duduk, duduk, teh dulu. Wajahmu kurus sekali, apa sekte tidak memberimu makan?',
                    'Welcome! Staying the night or eating? Sit, sit, tea first. Your face is so thin, does your sect not feed you?'),
          gestur='tuang_teh', prop='teko', suara='kotabesar.inn_welcome', pilihan=[
            P(('Kamar semalam berapa?', 'How much for a night?'), ke='kamar'),
            P(('Ada kabar dari para tamu?', 'Any news from the guests?'), ke='kabar'),
            P(('Teh saja, Bi.', 'Just tea, auntie.'), ke='teh')]),
        N('kamar', ('Tiga Nilai Batu semalam, sarapan bubur jahe. Kasurnya diisi kapuk baru bulan lalu, empuk seperti awan!',
                    'Three Spirit Value a night, ginger porridge for breakfast. The mattresses were stuffed fresh last month, soft as clouds!'),
          gestur='bicara_tangan'),
        N('kabar', None, kabar=True, gestur='bisik', prop='teko'),
        N('teh', ('Ini teh embun pagi dari kebun selatan. Minum pelan-pelan; orang yang minum teh terburu-buru juga hidup terburu-buru.',
                  'Morning dew tea from the southern gardens. Drink slowly; those who rush their tea rush their lives.'),
          gestur='tuang_teh', prop='teko', akibat=['efek minecraft:regeneration 20 0']),
    ])

    # ------------------------------------------------------------------ Fat Zhu, the cook (cheerful, afraid of rats)
    D('gendut_zhu_sapa', 'tokoh:gendut_zhu', [
        N('mulai', ('Hah! Pelanggan! Mi tarik, bakpao isi daging roh, sup tulang sembilan jam. Pilih, pilih!',
                    'Hah! A customer! Pulled noodles, spirit-meat buns, nine-hour bone soup. Choose, choose!'),
          gestur='masak', prop='sendok_sayur', pilihan=[
            P(('Apa rahasia supmu?', 'What is the secret of your soup?'), ke='sup'),
            P(('Kenapa kau melompat tadi?', 'Why did you jump just now?'), ke='lompat'),
            P(('Satu mangkuk mi.', 'One bowl of noodles.'), ke='mi')]),
        N('sup', ('Tulang dari pasar binatang, jahe dari Kampung Selatan, dan aku bernyanyi untuknya setiap jam. Sup yang dinyanyikan tidak pernah pahit.',
                  'Bones from the beast market, ginger from the southern village, and I sing to it every hour. A soup that is sung to is never bitter.'),
          gestur='tertawa'),
        N('lompat', ('Aku? Melompat? Ti-tidak! Itu... gerakan dapur. Bukan karena ada yang lari di bawah meja. Bukan sama sekali.',
                     'Me? Jump? N-no! That was... a kitchen move. Not because something ran under the table. Not at all.'),
          gestur='kaget'),
        N('mi', ('Satu mi tarik! Satu Nilai Batu, dan senyummu. Makan selagi panas!',
                 'One pulled noodle! One Spirit Value, and your smile. Eat it while it\'s hot!'),
          gestur='masak', prop='mangkuk', akibat=['bayar 1', 'efek minecraft:saturation 2 0']),
    ])

    # ------------------------------------------------------------------ Ma Laoliu, beast dealer (loves animals, hates poachers)
    D('ma_laoliu_sapa', 'tokoh:ma_laoliu', [
        N('mulai', ('Yang ini serigala roh yang lahir di malam purnama. Jangan sentuh dulu, biarkan ia mencium tanganmu.',
                    'This one is a spirit wolf born under a full moon. Don\'t touch yet, let it smell your hand.'),
          gestur='lempar_pakan', suara='kotabesar.beast_market', pilihan=[
            P(('Bagaimana kau mendapatkan binatang-binatang ini?', 'How do you get these beasts?'), ke='asal'),
            P(('Binatang mana yang paling setia?', 'Which beast is most loyal?'), ke='setia'),
            P(('Hanya melihat-lihat.', 'Just looking.'))]),
        N('asal', ('Dari peternak yang kukenal, atau dari induk yang menitipkan anaknya padaku. Bukan dari jerat pemburu liar. '
                   'Kalau kau pernah membeli dari mereka, jangan beli dariku lagi.',
                   'From breeders I know, or from mothers who leave their young with me. Never from a poacher\'s snare. '
                   'If you ever bought from them, do not buy from me again.'),
          gestur='marah_tunjuk'),
        N('setia', ('Yang kau beri makan sendiri, setiap pagi, tanpa lupa. Bukan jenisnya; kebiasaanmu.',
                    'The one you feed yourself, every morning, without fail. Not the breed; your habit.'),
          gestur='lempar_pakan'),
    ])

    # ------------------------------------------------------------------ Captain Yun Fei, sky dock (adventurer, braggart)
    D('yun_fei_sapa', 'tokoh:yun_fei', [
        N('mulai', ('Berangkat! Sampai jumpa di atas awan! Eh, kau bukan penumpang? Tetap dengarkan ceritaku, gratis.',
                    'Setting sail! See you above the clouds! Eh, you\'re not a passenger? Listen to my story anyway, it\'s free.'),
          gestur='lambai', suara='kotabesar.dock_depart', pilihan=[
            P(('Cerita apa?', 'What story?'), ke='cerita'),
            P(('Ke mana saja kapalmu terbang?', 'Where does your ship fly?'), ke='rute'),
            P(('Tidak hari ini.', 'Not today.'))]),
        N('cerita', ('Aku pernah melompati punggung naga awan yang sedang tidur. Tiga kali. Yang ketiga ia bangun, dan aku masih di sini, jadi '
                     'jelas aku yang menang.',
                     'I once leapt along the back of a sleeping cloud dragon. Three times. On the third it woke, and I am still here, '
                     'so clearly I won.'),
          gestur='tertawa', pilihan=[P(('Benarkah?', 'Really?'), ke='benar'), P(('Hebat.', 'Impressive.'))]),
        N('benar', ('Sebagian. Naganya benar. Yang lain... angin di atas awan membuat ingatan jadi ringan.',
                    'Partly. The dragon was real. The rest... the wind above the clouds makes memories light.'),
          gestur='kipas'),
        N('rute', ('Istana-istana langit sekte sekutu, ibu kota lain, kota pelabuhan kalau anginnya baik. Bayar di loket, tiket dicap, '
                   'jangan menoleh ke bawah.',
                   'The sky palaces of allied sects, other capitals, the harbour city when the wind is kind. Pay at the booth, get your '
                   'ticket stamped, don\'t look down.'),
          gestur='tunjuk'),
    ])

    # ------------------------------------------------------------------ Grandpa Mud, beggar (hidden Dao Union master)
    D('kakek_lumpur_sapa', 'tokoh:kakek_lumpur', [
        N('mulai', ('Hmm.', 'Hmm.'), gestur='duduk_lantai', pilihan=[
            P(('Kakek, kau lapar?', 'Grandpa, are you hungry?'), ke='lapar'),
            P(('Kakek tidak kedinginan tidur di sini?', 'Aren\'t you cold sleeping here?'), ke='dingin'),
            P(('(Pergi)', '(Leave)'))]),
        N('lapar', ('Lapar itu guru. Tapi bakpao juga guru. Kalau kau punya satu Nilai Batu, aku mau belajar dari bakpao.',
                    'Hunger is a teacher. But buns are teachers too. If you have one Spirit Value, I would like to learn from a bun.'),
          gestur='cerita', pilihan=[
            P(('Ini, Kek. (beri 1 NB)', 'Here, grandpa. (give 1 SV)'), ke='terima', akibat=['bayar 1', 'bendera kakek_diberi']),
            P(('Maaf, Kek.', 'Sorry, grandpa.'))]),
        N('terima', ('Hm. Tanganmu hangat. Orang yang memberi kepada pengemis tanpa melihat siapa yang menonton... jarang sekali.',
                     'Hm. Your hand is warm. Someone who gives to a beggar without looking who is watching... very rare.'),
          gestur='hormat_dalam'),
        N('dingin', ('Dingin itu hanya angin yang belum kenal kita. Aku sudah berkenalan dengannya enam puluh tahun.',
                     'Cold is only a wind that has not met us yet. I have known it sixty years.'),
          gestur='tatap_langit'),
    ])

    # ------------------------------------------------------------------ Little Dou, slum child (lively, messenger)
    D('dou_sapa', 'tokoh:dou', [
        N('mulai', ('Ayo tangkap aku! ...Eh, kau orang baru. Mau dengar kabar? Satu kabar, satu permen!',
                    'Catch me if you can! ...Oh, you\'re new. Want news? One rumour, one sweet!'),
          gestur='main_kejar', suara='tempat.child_play', pilihan=[
            P(('Ini permennya. (bayar 1 NB)', 'Here\'s your sweet. (pay 1 SV)'), ke='kabar', akibat=['bayar 1']),
            P(('Kau tinggal di mana?', 'Where do you live?'), ke='rumah'),
            P(('Hati-hati di jalan.', 'Be careful out there.'))]),
        N('kabar', None, kabar=True, gestur='bisik'),
        N('rumah', ('Di mana-mana! Di bawah jembatan kalau hujan, di atap gudang kalau panas. Kakek Lumpur bilang dunia ini rumah yang belum '
                    'dibagi pintunya.',
                    'Everywhere! Under the bridge when it rains, on the warehouse roof when it\'s hot. Grandpa Mud says the world is a house '
                    'whose doors haven\'t been shared out yet.'),
          gestur='main_lompat'),
    ])

    # ------------------------------------------------------------------ Lao Qiao, travelling peddler (knows every road)
    D('lao_qiao_sapa', 'tokoh:lao_qiao', [
        N('mulai', ('Barang langka, kubawa dari kota yang jauh! Jarum perak, garam gunung, peta yang hanya sedikit salah!',
                    'Rare goods, carried all the way from distant towns! Silver needles, mountain salt, maps only slightly wrong!'),
          gestur='seru_dagang', prop='pikulan', suara='tempat.peddler', pilihan=[
            P(('Jalan mana yang aman?', 'Which roads are safe?'), ke='jalan'),
            P(('Kabar dari kota lain?', 'News from other towns?'), ke='kabar'),
            P(('Peta yang salah?', 'Wrong maps?'), ke='peta')]),
        N('jalan', ('Jalan Kekaisaran yang berubin batu, selalu. Jalan tanah di hutan cuma untuk orang yang suka dibegal. '
                    'Ikuti tonggak; setiap tonggak bertuliskan jarak dalam li, dan raja jalan kuno yang menulisnya tidak pernah bohong.',
                    'The stone-paved Imperial Road, always. Forest dirt tracks are for people who enjoy being robbed. Follow the milestones; '
                    'each one gives the distance in li, and the old road kings who carved them never lied.'),
          gestur='tunjuk'),
        N('kabar', None, kabar=True, gestur='bisik'),
        N('peta', ('Sedikit. Sungainya benar, gunungnya benar, desanya kadang pindah. Desa itu hidup, Tuan, peta tidak.',
                   'A little. The rivers are right, the mountains are right, the villages sometimes move. Villages are alive, sir; maps are not.'),
          gestur='tertawa'),
    ])

    # ------------------------------------------------------------------ Ah Lun, outer disciple (Bibi Hong's son)
    D('ah_lun_sapa', 'tokoh:ah_lun', [
        N('mulai', ('Ada keperluan apa ke sekte kami? ...Maaf, aku cuma murid luar. Aku menyapu halaman dan mengangkat air.',
                    'What brings you to our sect? ...Sorry, I\'m only an outer disciple. I sweep the yard and carry water.'),
          gestur='sapu', prop='sapu', suara='tempat.sect_visit', pilihan=[
            P(('Kau dari ibu kota?', 'Are you from the capital?'), ke='asal'),
            P(('Bagaimana hidup di sekte?', 'How is life in the sect?'), ke='hidup'),
            P(('Lanjutkan pekerjaanmu.', 'Carry on.'))]),
        N('asal', ('Ya! Ibuku punya penginapan di sana. Aku... belum sempat menulis surat. Setiap kali mau menulis, aku malu karena belum jadi apa-apa.',
                   'Yes! My mother keeps an inn there. I... haven\'t written. Every time I try, I\'m ashamed I haven\'t become anything yet.'),
          gestur='sikap_tunduk'),
        N('hidup', ('Bangun sebelum ayam, menyapu sampai matahari tinggi, latihan napas sampai kaki kebas. Berat, tapi aku sudah bisa '
                    'menyalakan api tanpa batu!',
                    'Up before the rooster, sweeping till the sun is high, breathing drills until my legs go numb. Hard, but I can already '
                    'light a fire without flint!'),
          gestur='segel_tangan'),
    ])
