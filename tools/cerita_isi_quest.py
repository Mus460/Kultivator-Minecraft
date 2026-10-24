"""38ST content: the place quest chains of DESIGN 38.S.4 (every chain 2-5 stages with at least one branch), their offer
dialogs, the dialogs that drive their talk stages, and the rumours they spread. The inheritance chains warisan_<dao>_<tier>
live in cerita_isi_warisan. Stage pattern as in cerita_isi_takhta: no goals = waits for a dialog's quest_maju, goal
"hari 0" = ends as soon as it is entered."""

SEGERA = ['hari 0']


def tahap(q, s):
    return f'quest {q} tahap {s}'


def baru(q):
    return f'quest {q} baru'


# Koans of the hermit (trial 3 of tiga_ujian_pertapa): (question, (right, wrong, wrong), index of the right answer shown)
KOAN = [
    (('Seorang pengembara membawa lentera di siang bolong. Untuk apa?', 'A traveller carries a lantern at high noon. Why?'),
     [('Agar ia tidak lupa bahwa malam selalu datang.', 'So he never forgets that night always comes.'),
      ('Karena ia bodoh.', 'Because he is a fool.'),
      ('Untuk menjualnya di kota.', 'To sell it in town.')], 0),
    (('Gunung tidak pernah bergerak, tetapi awan selalu melewatinya. Siapa yang lebih kuat?',
      'The mountain never moves, yet the clouds always pass it. Which is stronger?'),
     [('Gunung, karena ia diam.', 'The mountain, because it is still.'),
      ('Pertanyaannya yang bergerak, bukan keduanya.', 'The question moves, not either of them.'),
      ('Awan, karena ia bebas.', 'The clouds, because they are free.')], 1),
    (('Kau menuang teh ke cangkir yang sudah penuh. Apa yang tumpah?', 'You pour tea into a cup that is already full. What spills?'),
     [('Teh yang baru.', 'The new tea.'),
      ('Cangkirnya.', 'The cup.'),
      ('Apa pun yang tidak mau kau lepaskan.', 'Whatever you refuse to let go of.')], 2),
    (('Pedang terbaik tidak pernah dihunus. Lalu untuk apa ia ditempa?',
      'The finest sword is never drawn. Then why was it forged?'),
     [('Agar tangan yang memegangnya belajar menahan diri.', 'So the hand that holds it learns restraint.'),
      ('Untuk dipajang.', 'To be displayed.'),
      ('Pedang itu belum selesai.', 'That sword is not finished.')], 0),
    (('Sungai mengalir ke laut tanpa tahu jalannya. Bagaimana ia tidak tersesat?',
      'The river flows to the sea without knowing the way. How does it never get lost?'),
     [('Ada dewa sungai yang menuntunnya.', 'A river god guides it.'),
      ('Ia selalu memilih tempat yang lebih rendah.', 'It always chooses the lower place.'),
      ('Ia tersesat, tetapi tidak peduli.', 'It is lost, but does not care.')], 1),
    (('Seorang murid menyapu halaman setiap pagi, dan setiap sore daun gugur lagi. Apakah ia gagal?',
      'A disciple sweeps the yard every morning, and every evening leaves fall again. Has he failed?'),
     [('Ya, ia harus menebang pohonnya.', 'Yes, he should cut the tree down.'),
      ('Ya, kerjanya sia-sia.', 'Yes, his work is in vain.'),
      ('Halamannya tidak pernah menjadi tugasnya; sapuannya yang menjadi tugasnya.',
       'The yard was never his task; the sweeping was.')], 2),
    (('Api membakar kayu sampai habis, lalu padam. Ke mana api itu pergi?', 'Fire burns the wood away, then dies. Where did the fire go?'),
     [('Ke dalam kehangatan orang yang duduk di dekatnya.', 'Into the warmth of those who sat beside it.'),
      ('Ke langit bersama asap.', 'Up to the sky with the smoke.'),
      ('Ke dalam abu.', 'Into the ash.')], 0),
    (('Seekor burung hidup di sangkar emas dan bernyanyi setiap hari. Apakah ia bahagia?',
      'A bird lives in a golden cage and sings every day. Is it happy?'),
     [('Ya, karena ia bernyanyi.', 'Yes, because it sings.'),
      ('Tanyakan pada pintu sangkarnya, bukan pada lagunya.', 'Ask the cage door, not the song.'),
      ('Tidak, emas itu berat.', 'No, gold is heavy.')], 1),
    (('Dua biksu melihat bendera berkibar. Satu bilang benderanya bergerak, satu bilang anginnya. Siapa yang benar?',
      'Two monks watch a flag flutter. One says the flag moves, the other says the wind. Who is right?'),
     [('Yang bilang angin.', 'The one who says the wind.'),
      ('Yang bilang bendera.', 'The one who says the flag.'),
      ('Hati merekalah yang bergerak.', 'It is their hearts that move.')], 2),
    (('Kalau kau menemukan Dao di jalan, apa yang kau lakukan?', 'If you meet the Dao on the road, what do you do?'),
     [('Terus berjalan bersamanya tanpa menyapanya.', 'Keep walking beside it without greeting it.'),
      ('Bersujud padanya.', 'Bow down before it.'),
      ('Menangkapnya agar tidak lari.', 'Catch it so it cannot run.')], 0),
    (('Bayanganmu mengikutimu ke mana pun. Bagaimana cara melepaskannya?', 'Your shadow follows you everywhere. How do you lose it?'),
     [('Berlari lebih cepat darinya.', 'Run faster than it.'),
      ('Duduk di bawah pohon saat matahari tepat di atas kepala.', 'Sit under a tree when the sun is straight overhead.'),
      ('Memotongnya dengan pedang.', 'Cut it with a sword.')], 1),
    (('Seorang guru tidak mengajarkan apa pun selama tiga tahun. Muridnya menjadi bijak. Apa yang ia ajarkan?',
      'A master taught nothing for three years. His student became wise. What did he teach?'),
     [('Cara menunggu.', 'How to wait.'),
      ('Ia mengajar diam-diam di malam hari.', 'He taught secretly at night.'),
      ('Bahwa jawaban tidak datang dari mulut orang lain.', 'That answers do not come from other people\'s mouths.')], 2),
]
WATAK = ['ramah', 'curiga', 'serakah', 'bijak', 'penakut', 'sombong', 'jujur', 'licik']


def isi(g):
    D, N, P, T, Q, K = g.D, g.N, g.P, g.T, g.Q, g.K
    g.tx('tempat_cerita', 'kultivator.cerita.pesan.calon',
         ('Anak itu menunggu di aula sektemu sebagai calon murid. Terimalah ia pada Hari Pembukaan Gerbang.',
          'The child waits at your sect hall as a candidate. Accept them on the next Gate Opening Day.'))
    g.tx('tempat_cerita', 'kultivator.cerita.pesan.calon_kabur',
         ('Murid yang kabur akan mengetuk gerbang sektemu suatu hari nanti.', 'The runaway disciple will knock at your sect gate one day.'))

    # ------------------------------------------------------------------ tungku_pertama (Dan Chen)
    q = 'tungku_pertama'
    Q(q, 'tokoh:dan_chen', 'gilda_alkemis', ('Tungku Pertama', 'The First Furnace'),
      ('Ujian Tingkat Alkemis Pertama: racik satu pil tanpa membakar tungku, tanpa menangis, tanpa bertanya dua kali.',
       'The First Alchemist Rank exam: refine one pill without burning the furnace, without crying, without asking twice.'),
      [T('racik', ('Racik satu pil apa pun di tungku. Jangan biarkan apinya padam.', 'Refine any pill at a furnace. Do not let the flame die.'),
         tujuan=['racik *']),
       T('nilai', ('Bawa hasil racikanmu kepada Tetua Dan Chen.', 'Bring your work to Elder Dan Chen.'),
         hasil=('Namamu tertulis di papan Gilda Alkemis sebagai alkemis Tingkat Pertama, dengan huruf yang lebih kecil dari nama '
                'siapa pun.',
                'Your name is written on the Alchemist Guild board as a First Rank alchemist, in letters smaller than anyone else\'s.'))])
    D('tungku_pertama_tawar', 'tokoh:dan_chen', [
        N('mulai', ('Kau masih di sini? Hm. Kalau kau ingin lebih dari sekadar menonton, ikuti ujian Tingkat Pertama. Biayanya kecil, '
                    'malunya besar kalau gagal.',
                    'Still here? Hm. If you want more than watching, take the First Rank exam. The fee is small; the shame of failing '
                    'is large.'),
          gestur='sikap_angkuh', pilihan=[
            P(('Aku ikut ujian.', 'I will take the exam.'), ke='terima', syarat=['nb_pita 5'], tampil=True,
              akibat=['bayar_pita 5', f'quest_mulai {q}'], alasan=('Biaya ujian belum ada di kantongmu.', 'The exam fee is not in your purse.')),
            P(('Belum siap.', 'Not yet.'))]),
        N('terima', ('Tungku mana pun boleh. Api yang jujur tidak peduli di mana ia menyala. Bawa pilmu ke sini, dan jangan tersenyum '
                     'sebelum aku mengizinkan.',
                     'Any furnace will do. An honest flame does not care where it burns. Bring your pill here, and do not smile '
                     'before I allow it.'),
          gestur='tunjuk'),
    ], prioritas=30, syarat=[baru(q)])
    D('tungku_pertama_nilai', 'tokoh:dan_chen', [
        N('mulai', ('Coba kulihat... Bentuknya bulat, warnanya merata, baunya tidak membuat mataku berair. Hm.',
                    'Let me see... Round in shape, even in colour, and its smell does not make my eyes water. Hm.'),
          gestur='periksa_nadi', prop='labu_pil', pilihan=[
            P(('(Menunggu putusan)', '(Await the verdict)'), ke='lulus', akibat=['bendera alkemis_tingkat_1', 'nama_baik 3', f'quest_maju {q}'])]),
        N('lulus', ('Lulus. Tingkat Alkemis Pertama. Jangan tersenyum dulu; Tingkat Kedua akan membuatmu menangis.',
                    'Passed. First Alchemist Rank. Do not smile yet; the Second Rank will make you weep.'),
          gestur='angguk_dengar'),
    ], prioritas=50, syarat=[tahap(q, 'nilai')])

    # ------------------------------------------------------------------ resep_hilang (Dan Chen)
    q = 'resep_hilang'
    Q(q, 'tokoh:dan_chen', 'makam', ('Resep yang Hilang', 'The Lost Recipe'),
      ('Halaman terakhir Rumus Sembilan Embun tertinggal di sebuah Makam Warisan. Serahkan kepada gilda, atau simpan sendiri?',
       'The last page of the Nine Dews Formula was left in an inheritance tomb. Give it to the guild, or keep it?'),
      [T('makam', ('Cari Halaman Resep Kuno di Makam Warisan terdekat.', 'Find the Ancient Recipe Page in the nearest inheritance tomb.'),
         tujuan=['pergi gua/makam'], akibat=['beri cerita:halaman_resep_kuno'], arah='gua/makam'),
       T('pilih', ('Halaman itu di tanganmu. Bawa kepada Tetua Dan Chen dan putuskan.', 'The page is in your hands. Take it to Elder Dan Chen and decide.'),
         cabang=[(['bendera resep_simpan'], 'simpan')], ke='serah'),
       T('serah', ('Gilda menyalin halaman itu.', 'The guild copies the page.'), tujuan=SEGERA,
         akibat=['nama_baik 5', 'beri_nb_pita 40', 'bendera alkemis_resep_gilda', 'bendera alkemis_tingkat_plus'], ke='selesai',
         hasil=('Gilda menyalin halaman itu ke kitab induknya. Namamu ditulis di catatan kaki, dengan huruf kecil yang sangat rapi.',
                'The guild copied the page into its master book. Your name is in the footnote, in very neat small letters.')),
       T('simpan', ('Kau menyimpan rumus itu.', 'You keep the formula.'), tujuan=SEGERA, akibat=['bendera resep_pribadi', 'nama_baik -2'],
         ke='selesai',
         hasil=('Kau menyimpan Rumus Sembilan Embun untuk dirimu sendiri. Dan Chen tidak mengatakan apa pun, dan diamnya lebih '
                'dingin dari musim dingin.',
                'You kept the Nine Dews Formula for yourself. Dan Chen said nothing, and his silence was colder than winter.'))])
    D('resep_hilang_tawar', 'tokoh:dan_chen', [
        N('mulai', ('Ada yang lebih penting dari pujianmu. Seratus tahun lalu guru dari guruku menyalin Rumus Sembilan Embun dari makam '
                    'seorang leluhur alkemis. Halaman terakhirnya tertinggal di sana. Kitab kami pincang sejak itu.',
                    'There is something more important than your praise. A hundred years ago my master\'s master copied the Nine Dews '
                    'Formula from the tomb of an ancestral alchemist. The last page was left behind. Our book has limped ever since.'),
          gestur='cerita', prop='kitab', pilihan=[
            P(('Akan kucari halaman itu.', 'I will find that page.'), ke='pesan', akibat=[f'quest_mulai {q}']),
            P(('Makam itu berbahaya?', 'Is the tomb dangerous?'), ke='bahaya')]),
        N('bahaya', ('Makam warisan menguji hati, kekuatan, dan pemahaman. Yang gagal tidak mati; mereka hanya pulang dengan tangan '
                     'kosong dan kepala penuh mimpi buruk.',
                     'Inheritance tombs test the heart, strength and understanding. Those who fail do not die; they only come home '
                     'empty-handed with heads full of nightmares.'),
          gestur='bicara_tangan', pilihan=[P(('Akan kucari.', 'I will look for it.'), ke='pesan', akibat=[f'quest_mulai {q}'])]),
        N('pesan', ('Tintanya berbau cendana. Kalau kau mencium cendana di dalam makam, kau sudah dekat.',
                    'The ink smells of sandalwood. If you smell sandalwood in a tomb, you are close.'), gestur='tunjuk'),
    ], prioritas=30, syarat=[baru(q), 'quest tungku_pertama selesai'])
    D('resep_hilang_pilih', 'tokoh:dan_chen', [
        N('mulai', ('Kau menemukannya? Tunjukkan! ...Ini tulisan tangan guruku. Bukan, tulisan guru dari guruku. Tangannya tidak '
                    'gemetar sedikit pun.',
                    'You found it? Show me! ...This is my master\'s hand. No, my master\'s master. His hand did not tremble even once.'),
          gestur='membaca', prop='gulungan', pilihan=[
            P(('Halaman ini milik gilda.', 'This page belongs to the guild.'), ke='serah',
              akibat=['ambil cerita:halaman_resep_kuno', f'quest_maju {q}']),
            P(('Aku akan menyimpannya.', 'I will keep it.'), ke='simpan', akibat=['bendera resep_simpan', f'quest_maju {q}'])]),
        N('serah', ('...Terima kasih. Aku tidak mengucapkan kata itu sejak tahun wabah. Jangan biasakan dirimu mendengarnya.',
                    '...Thank you. I have not said that word since the plague year. Do not get used to hearing it.'),
          gestur='hormat_dalam'),
        N('simpan', ('Begitu. Rumus itu akan hidup di kepalamu dan mati bersamamu. Pilihan yang sangat... pribadi.',
                     'I see. The formula will live in your head and die with you. A very... private choice.'),
          gestur='sikap_angkuh'),
    ], prioritas=50, syarat=[tahap(q, 'pilih')])

    # ------------------------------------------------------------------ pedang_tanpa_nama (Ou Ye)
    q = 'pedang_tanpa_nama'
    Q(q, 'tokoh:ou_ye', 'gilda_tempa', ('Pedang Tanpa Nama', 'The Nameless Sword'),
      ('Ou Ye Muda ingin menempa satu pedang yang tidak dijual: untuk pemiliknya saja. Kristal urat, besi, api, dan air roh.',
       'Young Ou Ye wants to forge one sword that is not for sale: for its owner alone. Vein crystal, iron, fire and spirit water.'),
      [T('kristal', ('Ambil Kristal Urat Roh dari Gua Urat Roh.', 'Take a Spirit Vein Crystal from a Spirit Vein Cave.'),
         tujuan=['pergi gua/urat_roh'], akibat=['beri cerita:kristal_urat'], arah='gua/urat_roh'),
       T('tempa', ('Bawa kristal dan delapan batang besi kepada Ou Ye Muda, lalu tempa bersamanya.',
                   'Bring the crystal and eight iron ingots to Young Ou Ye, then forge with him.')),
       T('celup', ('Celupkan bilah yang masih membara ke Mata Air Roh.', 'Quench the glowing blade in a spirit spring.'),
         tujuan=['pergi situs/mata_air_roh'], akibat=['beri cerita:pedang_tanpa_nama', 'adegan pedang_lahir'], arah='situs/mata_air_roh'),
       T('kembali', ('Kembalilah kepada Ou Ye Muda.', 'Return to Young Ou Ye.'), tujuan=['bicara tokoh:ou_ye'],
         akibat=['gelar pembawa_pedang_tanpa_nama', 'nama_baik 3'],
         hasil=('Ou Ye menatap bilah itu lama sekali, lalu berkata satu kalimat: "Namanya akan datang sendiri." Ukirlah namanya '
                'kapan pun kau siap.',
                'Ou Ye looked at the blade for a long time, then said one sentence: "Its name will come on its own." Carve its '
                'name whenever you are ready.'))])
    D('pedang_tawar', 'tokoh:ou_ye', [
        N('mulai', ('Kau. Tanganmu memegang gagang dengan benar. Jarang.', 'You. Your hand holds a hilt correctly. Rare.'),
          gestur='sikap_lipat_tangan', pilihan=[
            P(('Terima kasih?', 'Thank you?'), ke='usul')]),
        N('usul', ('Aku ingin menempa satu pedang tanpa nama. Bukan untuk dijual. Untuk pemiliknya. Butuh Kristal Urat Roh, delapan '
                   'besi, dan tanganmu di penjepit.',
                   'I want to forge one nameless sword. Not for sale. For its owner. It needs a Spirit Vein Crystal, eight iron, and '
                   'your hand on the tongs.'),
          gestur='palu_landasan', prop='palu', pilihan=[
            P(('Akan kucari kristalnya.', 'I will find the crystal.'), akibat=[f'quest_mulai {q}']),
            P(('Lain kali.', 'Another time.'))]),
    ], prioritas=30, syarat=[baru(q), 'pita 1'])
    D('pedang_tempa', 'tokoh:ou_ye', [
        N('mulai', ('Kristal. Besi. Bagus.', 'Crystal. Iron. Good.'), gestur='palu_landasan', prop='palu', pilihan=[
            P(('(Pegang penjepit dan ikuti irama palunya)', '(Take the tongs and follow the rhythm of his hammer)'), ke='irama',
              syarat=['punya cerita:kristal_urat', 'punya minecraft:iron_ingot 8'], tampil=True,
              akibat=['ambil cerita:kristal_urat', 'ambil minecraft:iron_ingot 8', 'klip palu_landasan palu 120', f'quest_maju {q}'],
              alasan=('Kau butuh Kristal Urat Roh dan delapan batang besi.', 'You need a Spirit Vein Crystal and eight iron ingots.'))]),
        N('irama', ('Satu. Dua. Jangan terlalu keras; besi bukan musuhmu. ...Sekarang bawa ke Mata Air Roh selagi masih merah. Cepat.',
                    'One. Two. Not so hard; the iron is not your enemy. ...Now take it to a spirit spring while it is still red. Hurry.'),
          gestur='palu_landasan', prop='palu'),
    ], prioritas=50, syarat=[tahap(q, 'tempa')])
    D('pedang_selesai', 'tokoh:ou_ye', [
        N('mulai', ('Pedang yang diberi nama terlalu cepat, patah terlalu cepat. Tunggu sampai ia menyelamatkan nyawamu sekali.',
                    'A sword named too soon breaks too soon. Wait until it has saved your life once.'),
          gestur='sikap_lipat_tangan', akibat=['bendera pedang_nasihat']),
    ], prioritas=20, syarat=[f'quest {q} selesai', '!bendera pedang_nasihat'])

    # ------------------------------------------------------------------ daftar_naga_muda (Lin Fei)
    q = 'daftar_naga_muda'
    Q(q, 'tokoh:lin_fei', 'gilda_kultivator', ('Daftar Naga Muda', 'The Young Dragon List'),
      ('Untuk masuk Daftar Naga Muda, kau harus melewati Feng Ao. Tiga kali: di papan misi, di arena, dan sesudahnya.',
       'To enter the Young Dragon List you must get past Feng Ao. Three times: on the mission board, in the arena, and after.'),
      [T('misi', ('Selesaikan satu misi dari papan Gilda Kultivator sebelum Feng Ao mengambilnya.',
                  'Finish one mission from the Cultivators\' Guild board before Feng Ao takes it.'), tujuan=['misi_gilda *']),
       T('arena', ('Tantang Feng Ao di arena dan kalahkan dia dalam duel.', 'Challenge Feng Ao in the arena and beat him in a duel.'),
         tujuan=['duel tokoh:feng_ao'], arah='kota/kota_besar'),
       T('sikap', ('Feng Ao terbaring di pasir arena. Bicaralah dengannya.', 'Feng Ao lies in the arena sand. Speak to him.'),
         cabang=[(['bendera feng_ao_dendam'], 'dendam')], ke='sahabat'),
       T('sahabat', ('Feng Ao menyambut tanganmu.', 'Feng Ao takes your hand.'), tujuan=SEGERA,
         akibat=['gelar sahabat_naga_muda', 'nama_baik 5', 'kabar naga_muda_sahabat pemain'], ke='selesai',
         hasil=('Namamu masuk Daftar Naga Muda, tepat di atas Feng Ao. Ia menggerutu, lalu mentraktirmu mi. Saingan terbaik adalah '
                'yang membuatmu lebih baik.',
                'Your name entered the Young Dragon List, just above Feng Ao. He grumbled, then bought you noodles. The best rival '
                'is the one who makes you better.')),
       T('dendam', ('Feng Ao menepis tanganmu.', 'Feng Ao slaps your hand away.'), tujuan=SEGERA,
         akibat=['nama_baik 2', 'kabar naga_muda_dendam pemain'], ke='selesai',
         hasil=('Namamu masuk Daftar Naga Muda. Feng Ao keluar dari arena tanpa menoleh, dan kau tahu kalian akan bertemu lagi di jalan.',
                'Your name entered the Young Dragon List. Feng Ao left the arena without looking back, and you know you will meet '
                'again on the road.'))])
    D('naga_muda_tawar', 'tokoh:lin_fei', [
        N('mulai', ('Mau masuk Daftar Naga Muda? Ada syaratnya: kau harus melewati Feng Ao. Bocah itu memanjat dengan sikut, tapi '
                    'sikutnya tajam.',
                    'Want onto the Young Dragon List? There is a condition: you must get past Feng Ao. The boy climbs with his '
                    'elbows, but his elbows are sharp.'),
          gestur='sikap_berkacak', pilihan=[
            P(('Aku terima.', 'I accept.'), ke='mulai_lomba', akibat=[f'quest_mulai {q}']),
            P(('Bukan sekarang.', 'Not now.'))]),
        N('mulai_lomba', ('Ambil misi dari papan. Siapa pun yang menyelesaikan lebih dulu, menang babak pertama. Feng Ao sudah membaca '
                          'papan itu tiga kali pagi ini.',
                          'Take a mission from the board. Whoever finishes first wins the first round. Feng Ao has read that board '
                          'three times this morning.'),
          gestur='tunjuk'),
    ], prioritas=30, syarat=[baru(q)])
    D('naga_muda_arena', 'tokoh:feng_ao', [
        N('mulai', ('Jadi kau yang menyalipku di papan misi. Arena. Sekarang. Tanpa senjata tajam, tanpa teknik pembunuh.',
                    'So you are the one who beat me on the mission board. Arena. Now. No sharp blades, no killing arts.'),
          gestur='hunus', pilihan=[
            P(('(Masuk arena)', '(Step into the arena)'), akibat=['mulai_duel']),
            P(('Nanti.', 'Later.'))]),
    ], prioritas=50, syarat=[tahap(q, 'arena')])
    D('naga_muda_sikap', 'tokoh:feng_ao', [
        N('mulai', ('...Kau menang. Puas? Penonton menunggu kau melakukan sesuatu.', '...You won. Happy? The crowd is waiting for you to do something.'),
          gestur='berlutut', pilihan=[
            P(('(Ulurkan tangan) Kau lawan yang bagus.', '(Offer your hand) You were a good opponent.'), ke='sahabat',
              akibat=['bendera feng_ao_sahabat', f'quest_maju {q}']),
            P(('(Tertawakan dia di depan penonton)', '(Laugh at him before the crowd)'), ke='dendam',
              akibat=['bendera feng_ao_dendam', f'quest_maju {q}'])]),
        N('sahabat', ('Hmph. ...Kau traktir minum. Tidak, aku yang traktir. Aku tidak mau berutang padamu.',
                      'Hmph. ...You are buying the drinks. No, I am. I refuse to owe you anything.'), gestur='tertawa'),
        N('dendam', ('Tertawalah selagi bisa. Jalan itu panjang, dan aku ingat setiap wajah.',
                     'Laugh while you can. The road is long, and I remember every face.'), gestur='marah_hentak'),
    ], prioritas=50, syarat=[tahap(q, 'sikap')])
    D('feng_ao_dendam', 'tokoh:feng_ao', [
        N('mulai', ('Kau! Kali ini tidak ada penonton yang menertawakanku. Hunus senjatamu.',
                    'You! This time there is no crowd to laugh at me. Draw.'),
          gestur='ancam', suara='tempat.young_master', pilihan=[
            P(('(Terima tantangan)', '(Accept the challenge)'), akibat=['mulai_duel']),
            P(('Aku minta maaf untuk hari itu.', 'I apologise for that day.'), ke='maaf', syarat=['nama_baik 20'], tampil=True,
              alasan=('Feng Ao tidak percaya pada kata-katamu.', 'Feng Ao does not believe your words.'))]),
        N('maaf', ('...Maaf? Kau pikir... Hmph. Baiklah. Satu mangkuk mi, dan kita impas.',
                   '...Sorry? You think... Hmph. Fine. One bowl of noodles and we are even.'),
          gestur='sikap_angkuh', akibat=['bendera feng_ao_dendam off', 'bendera feng_ao_sahabat']),
    ], prioritas=15, syarat=['bendera feng_ao_dendam'])
    D('feng_ao_sahabat', 'tokoh:feng_ao', [
        N('mulai', ('Kau lagi. ...Peringkatku naik ke tiga. Jangan tersenyum; itu bukan karena kau. Sedikit karena kau.',
                    'You again. ...I have climbed to third place. Do not smile; it is not because of you. A little because of you.'),
          gestur='sikap_angkuh', pilihan=[
            P(('Tanding latih lagi?', 'Spar again?'), akibat=['mulai_duel']),
            P(('Selamat, Feng Ao.', 'Congratulations, Feng Ao.'))]),
    ], prioritas=15, syarat=['bendera feng_ao_sahabat'])

    # ------------------------------------------------------------------ wesel_palsu (Qian Duoduo)
    q = 'wesel_palsu'
    Q(q, 'tokoh:qian_duoduo', 'rumah_uang', ('Wesel Palsu', 'The Forged Notes'),
      ('Surat wesel palsu bercap Rumah Qian beredar di pasar. Lacak pemalsunya sampai ke Kampung Kumuh.',
       'Forged money notes bearing House Qian\'s seal are circulating. Track the forger down to the slums.'),
      [T('tanya', ('Tanyakan pada Si Kecil Dou siapa yang membelanjakan wesel di Kampung Kumuh.',
                   'Ask Little Dou who spends money notes in the slums.'), akibat_masuk=['beri cerita:wesel_palsu']),
       T('pemalsu', ('Hadapi si pemalsu.', 'Confront the forger.'), cabang=[(['bendera wesel_peras'], 'peras')], ke='lapor'),
       T('lapor', ('Kabari Nyonya Qian Duoduo.', 'Report to Madam Qian Duoduo.'), tujuan=['bicara tokoh:qian_duoduo'],
         akibat=['ambil cerita:wesel_palsu', 'beri_nb_pita 40', 'nama_baik 4', 'kabar wesel_tertangkap'],
         hasil=('Pemalsu itu diadili di Yamen. Nyonya Qian membayarmu tepat sampai kepingan terakhir, lalu menghitungnya ulang di '
                'depanmu, dua kali.',
                'The forger was tried at the Yamen. Madam Qian paid you to the last chip, then counted it again in front of you, twice.')),
       T('peras', ('Kau mengantongi uang pemalsu.', 'You pocket the forger\'s money.'), tujuan=SEGERA, akibat=['bendera qian_curiga'],
         ke='selesai',
         hasil=('Wesel palsu berhenti beredar, untuk sementara. Nyonya Qian mendengar ceritanya dari orang lain dan mencoret namamu '
                'dengan tinta hitam.',
                'The forged notes stopped circulating, for now. Madam Qian heard the story from someone else and struck your name '
                'in black ink.'))])
    D('wesel_tawar', 'tokoh:qian_duoduo', [
        N('mulai', ('Seseorang mencetak surat wesel palsu dengan cap Rumah Qian. Tiga pedagang sudah tertipu, dan yang keempat menangis '
                    'di meja ini tadi pagi. Rumah Qian tidak menangis, Tuan. Rumah Qian menagih.',
                    'Someone is printing forged money notes with House Qian\'s seal. Three merchants were cheated, and a fourth wept '
                    'at this desk this morning. House Qian does not weep, sir. House Qian collects.'),
          gestur='hitung_sempoa', prop='sempoa', pilihan=[
            P(('Akan kucari pemalsunya.', 'I will find the forger.'), ke='bukti', akibat=[f'quest_mulai {q}']),
            P(('Bagaimana kau tahu itu palsu?', 'How do you know they are fake?'), ke='tahu')]),
        N('tahu', ('Kertasnya terlalu baru. Kertas Rumah Qian dijemur satu musim dulu supaya tidak menguning. Penipu selalu terburu-buru.',
                   'The paper is too new. House Qian paper is sun-dried for a season so it never yellows. Swindlers are always in a hurry.'),
          gestur='bicara_tangan', pilihan=[P(('Akan kucari pemalsunya.', 'I will find the forger.'), ke='bukti', akibat=[f'quest_mulai {q}'])]),
        N('bukti', ('Bawa contoh ini. Wesel-wesel itu dibelanjakan di Kampung Kumuh; anak-anak di sana tahu segalanya dan menjual '
                    'semuanya seharga permen.',
                    'Take this sample. The notes are being spent in the slums; the children there know everything and sell it all '
                    'for a sweet.'), gestur='tunjuk'),
    ], prioritas=30, syarat=[baru(q)])
    D('wesel_dou', 'tokoh:dou', [
        N('mulai', ('Wesel? Kertas bagus yang dipakai beli tiga puluh bakpao sekaligus? Itu Paman Kertas! Ia memberi kami permen supaya '
                    'tutup mulut. Permennya enak...',
                    'A money note? The nice paper someone used to buy thirty buns at once? That is Uncle Paper! He gives us sweets to '
                    'keep quiet. The sweets are good...'),
          gestur='main_lompat', pilihan=[
            P(('Antar aku padanya. Permenmu kuganti dua kali lipat.', 'Take me to him. I will double your sweets.'), ke='itu',
              akibat=['bayar 2', 'panggil_peran makelar_gelap', f'quest_maju {q}'])]),
        N('itu', ('Itu dia! Yang bertopi lebar, yang pura-pura tidak melihat kita!', 'That is him! The one in the wide hat, pretending not to see us!'),
          gestur='tunjuk'),
    ], prioritas=40, syarat=[tahap(q, 'tanya')])
    D('wesel_pemalsu', 'peran:makelar_gelap', [
        N('mulai', ('Wesel? Aku tidak tahu soal wesel. ...Baiklah, baiklah! Aku cuma tukang cetak! Ada orang istana yang memesan '
                    'capnya, orang yang sopan sekali, tangannya dingin seperti ikan.',
                    'Money notes? I know nothing about notes. ...Fine, fine! I only print them! Someone from the palace ordered the '
                    'seal, a very polite man, with hands as cold as a fish.'),
          gestur='takut_sembunyi', pilihan=[
            P(('Ikut aku ke Yamen.', 'You are coming to the Yamen.'), ke='yamen', akibat=['bendera wesel_serah', f'quest_maju {q}']),
            P(('(Peras dia) Seratus batu roh, dan aku lupa wajahmu.', '(Extort him) A hundred spirit stones and I forget your face.'),
              ke='peras', akibat=['bendera wesel_peras', 'beri_nb_pita 50', 'nama_baik -3', f'quest_maju {q}'])]),
        N('yamen', ('Jangan pukul! Aku ikut, aku ikut! Hakim Bao itu... ia tidak menerima suap, kan? ...Tentu saja tidak.',
                    'Do not hit me! I will come, I will come! That Magistrate Bao... he does not take bribes, does he? ...Of course not.'),
          gestur='berlutut', akibat=['npc_pergi']),
        N('peras', ('Ini, ini! Ambil semuanya! Kau tidak pernah melihatku!', 'Here, here! Take it all! You never saw me!'),
          gestur='lari_takut', akibat=['npc_pergi']),
    ], prioritas=50, syarat=[tahap(q, 'pemalsu')])

    # ------------------------------------------------------------------ batu_bermata_darah (Lao Shi)
    q = 'batu_bermata_darah'
    Q(q, 'tokoh:lao_shi', 'rumah_judi', ('Batu Bermata Darah', 'The Blood-Eyed Stone'),
      ('Batu mentah terkutuk menyedot tenaga pemotongnya. Bawa ke kuil sebelum ia memakan seseorang.',
       'A cursed raw stone drains whoever cuts it. Take it to the temple before it devours someone.'),
      [T('kuil', ('Bawa Batu Bermata Darah kepada Biksu Jing Xin di kuil kota.', 'Bring the Blood-Eyed Stone to Monk Jing Xin at the city temple.'),
         tujuan=['antar cerita:batu_bermata_darah tokoh:jing_xin'],
         akibat_masuk=['beri cerita:batu_bermata_darah', 'efek minecraft:weakness 1200 0']),
       T('uji', ('Tatap mata batu itu bersama Biksu Jing Xin.', 'Gaze into the stone\'s eye with Monk Jing Xin.')),
       T('lapor', ('Kabari Lao Shi bahwa batunya sudah tidur.', 'Tell Lao Shi the stone has gone to sleep.'), tujuan=['bicara tokoh:lao_shi'],
         akibat=['beri_nb_pita 30', 'nama_baik 3', 'bendera lao_shi_hutang_budi'],
         hasil=('Lao Shi menyentuh penutup matanya dan tertawa pelan. "Dua puluh tahun aku menunggu orang yang bisa menidurkan batu. '
                'Kalau kau mau membelah batu di sini, aku sendiri yang memilihkannya."',
                'Lao Shi touched his eyepatch and laughed softly. "Twenty years I waited for someone who could put a stone to sleep. '
                'If you ever cut stone here, I will choose it for you myself."'))])
    D('batu_tawar', 'tokoh:lao_shi', [
        N('mulai', ('Batu ini datang dari tambang utara. Pemotongku pingsan dua kali memegangnya, dan yang ketiga kali ia bermimpi '
                    'tentang mata merah. Aku tidak mau batu ini di rumahku satu malam lagi.',
                    'This stone came from the northern mine. My cutter fainted twice holding it, and the third time he dreamt of a '
                    'red eye. I will not keep it in my house one more night.'),
          gestur='asah_pisau', prop='batu_mentah', pilihan=[
            P(('Biar kubawa ke kuil.', 'Let me take it to the temple.'), ke='bawa', akibat=[f'quest_mulai {q}']),
            P(('Kenapa tidak kau buang saja?', 'Why not throw it away?'), ke='buang')]),
        N('buang', ('Lalu orang lain memungutnya, membelahnya, dan aku membaca namanya di papan duka. Aku licik, bukan jahat.',
                    'Then someone else picks it up, cuts it, and I read their name on the mourning board. I am sly, not wicked.'),
          gestur='bicara_tangan', pilihan=[P(('Biar kubawa ke kuil.', 'Let me take it to the temple.'), ke='bawa', akibat=[f'quest_mulai {q}'])]),
        N('bawa', ('Jangan menatapnya terlalu lama. Kalau tanganmu mulai terasa berat, itu bukan karena batunya.',
                   'Do not look at it too long. If your hands start to feel heavy, it is not because of the stone.'), gestur='bisik'),
    ], prioritas=30, syarat=[baru(q)])
    D('batu_uji', 'tokoh:jing_xin', [
        N('mulai', ('Tatap matanya. Jangan berpaling. Apa yang kau lihat?', 'Look into its eye. Do not look away. What do you see?'),
          gestur='sikap_doa', prop='tasbih_tangan', pilihan=[
            P(('Wajahku sendiri, sedang tersenyum.', 'My own face, smiling.'), ke='salah', akibat=['efek minecraft:nausea 8 0']),
            P(('Seorang pemotong batu yang sangat lelah.', 'A stonecutter, very tired.'), ke='benar1'),
            P(('Tumpukan giok.', 'Piles of jade.'), ke='salah', akibat=['efek minecraft:nausea 8 0'])]),
        N('benar1', ('Ya. Batu ini menyimpan kelelahan semua tangan yang pernah memotongnya. Apa yang ia minta?',
                     'Yes. This stone holds the weariness of every hand that ever cut it. What does it ask for?'),
          gestur='angguk_dengar', pilihan=[
            P(('Dibelah sampai habis.', 'To be cut to the end.'), ke='salah', akibat=['efek minecraft:nausea 8 0']),
            P(('Dijual semahal-mahalnya.', 'To be sold dearly.'), ke='salah', akibat=['efek minecraft:nausea 8 0']),
            P(('Istirahat.', 'Rest.'), ke='benar2', akibat=['efek minecraft:weakness 1 0', f'quest_maju {q}'])]),
        N('benar2', ('(Biksu memukul lonceng kecil. Urat merah di batu memudar menjadi abu-abu.) Ia sudah tidur. Kau juga sebaiknya '
                     'tidur lebih awal malam ini.',
                     '(The monk strikes a small bell. The red vein in the stone fades to grey.) It sleeps now. You should sleep early '
                     'tonight too.'),
          gestur='sujud_dupa', prop='dupa', suara='kotabesar.temple_bell'),
        N('salah', ('Matamu masih lapar. Duduklah sebentar, minum air, lalu kita coba lagi.',
                    'Your eyes are still hungry. Sit a while, drink some water, and we will try again.'), gestur='hormat_dalam'),
    ], prioritas=50, syarat=[tahap(q, 'uji')])

    # ------------------------------------------------------------------ lonceng_retak (Jing Xin)
    q = 'lonceng_retak'
    Q(q, 'tokoh:jing_xin', 'kuil', ('Lonceng yang Retak', 'The Cracked Bell'),
      ('Lonceng kuil retak setelah gempa kecil. Bawa tembaga untuk perunggunya dan tunggu para tukang menuangnya.',
       'The temple bell cracked after a small quake. Bring copper for its bronze and wait for the craftsmen to cast it.'),
      [T('perunggu', ('Bawa enam belas batang tembaga kepada Biksu Jing Xin.', 'Bring sixteen copper ingots to Monk Jing Xin.'),
         tujuan=['antar minecraft:copper_ingot tokoh:jing_xin x16']),
       T('tuang', ('Para tukang menuang perunggu semalaman. Tunggu sampai besok.', 'The craftsmen cast the bronze overnight. Wait until tomorrow.'),
         tujuan=['tunggu 1'], akibat_masuk=['kabar lonceng_dituang']),
       T('dentang', ('Dengarkan dentang pertama lonceng yang sudah ditambal.', 'Hear the first toll of the mended bell.'),
         tujuan=['bicara tokoh:jing_xin'], akibat=['efek minecraft:luck 2400 0', 'nama_baik 4', 'suara kotabesar.temple_bell', 'kabar lonceng_pulih'],
         hasil=('Dentang pertama menggulung di atas atap-atap kota. Anak-anak berhenti bermain untuk mendengarnya, dan Kakek '
                'Lumpur, untuk sekali, membuka matanya.',
                'The first toll rolled over the city roofs. The children stopped playing to listen, and Grandpa Mud, for once, '
                'opened his eyes.'))])
    D('lonceng_tawar', 'tokoh:jing_xin', [
        N('mulai', ('Dengarlah lonceng itu. Suaranya seperti batuk orang tua sejak gempa kecil bulan lalu. Kami butuh perunggu untuk '
                    'menambalnya, dan perunggu butuh tembaga.',
                    'Listen to that bell. Since last month\'s small quake it sounds like an old man\'s cough. We need bronze to mend '
                    'it, and bronze needs copper.'),
          gestur='sikap_doa', pilihan=[
            P(('Aku akan membawakan tembaga.', 'I will bring copper.'), akibat=[f'quest_mulai {q}']),
            P(('Lonceng itu penting?', 'Does the bell matter?'), ke='penting')]),
        N('penting', ('Lonceng tidak memanggil dewa. Lonceng memanggil manusia yang lupa berhenti. Kota tanpa lonceng adalah kota yang '
                      'tidak pernah beristirahat.',
                      'A bell does not call the gods. A bell calls people who forgot to stop. A city without a bell is a city that '
                      'never rests.'),
          gestur='tatap_langit', pilihan=[P(('Aku akan membawakan tembaga.', 'I will bring copper.'), akibat=[f'quest_mulai {q}'])]),
    ], prioritas=25, syarat=[baru(q)])

    # ------------------------------------------------------------------ surat_ah_lun (Bibi Hong) — the full example of 38.S.4
    q = 'surat_ah_lun'
    Q(q, 'tokoh:bibi_hong', 'penginapan', ('Surat untuk Ah Lun', 'A Letter for Ah Lun'),
      ('Anak Bibi Hong, Ah Lun, masuk sekte tiga musim lalu dan tidak pernah menulis surat.',
       'Bibi Hong\'s son Ah Lun joined a sect three seasons ago and has never written.'),
      [T('cari', ('Cari Ah Lun di kompleks sekte terdekat.', 'Look for Ah Lun at the nearest sect compound.'),
         tujuan=['pergi sekte/kompleks'], arah='sekte/kompleks'),
       T('temu', ('Tanyakan pada murid sekte luar tentang Ah Lun dan serahkan surat ibunya.',
                  'Ask the outer disciples about Ah Lun and hand him his mother\'s letter.'),
         cabang=[(['aliran_terdekat 2'], 'tahan')], ke='balasan'),
       T('balasan', ('Ah Lun sedang menulis balasan. Bicaralah lagi dengannya.', 'Ah Lun is writing a reply. Speak to him again.'),
         tujuan=['bicara tokoh:ah_lun'], akibat=['beri cerita:balasan_ah_lun'], ke='pulang'),
       T('pulang', ('Bawa balasan Ah Lun kepada Bibi Hong.', 'Bring Ah Lun\'s reply to Bibi Hong.'),
         tujuan=['antar cerita:balasan_ah_lun tokoh:bibi_hong'],
         akibat=['bendera inap_gratis_7', 'efek minecraft:regeneration 200 0', 'nama_baik 5', 'kabar ah_lun_surat pemain'], ke='selesai',
         hasil=('Bibi Hong membaca surat itu tiga kali, menangis dua kali, dan tertawa sekali. Kamar dan makanmu di penginapan gratis '
                'tujuh hari.',
                'Bibi Hong read the letter three times, cried twice and laughed once. Your room and meals at the inn are free for '
                'seven days.')),
       T('tahan', ('Sekte iblis itu tidak melepaskan muridnya. Kalahkan tetuanya dalam duel, atau bayar tebusan.',
                   'The demonic sect will not release its disciple. Beat its elder in a duel, or pay a ransom.'),
         tujuan=['duel peran:tetua_sekte'], ke='kabari'),
       T('kabari', ('Ah Lun bebas. Kabari Bibi Hong.', 'Ah Lun is free. Tell Bibi Hong.'), tujuan=['bicara tokoh:bibi_hong'],
         akibat=['bendera ah_lun_pulang', 'bendera inap_gratis_7', 'nama_baik 8', 'kabar ah_lun_pulang pemain'],
         hasil=('Ah Lun pulang ke penginapan ibunya dan kini membantu sebagai pelayan. Bibi Hong memanggilmu "anak kedua" setiap kali '
                'kau datang.',
                'Ah Lun came home to his mother\'s inn and now helps as a server. Bibi Hong calls you "my second child" every time '
                'you visit.'))])
    D('bibi_hong_ah_lun', 'tokoh:bibi_hong', [
        N('mulai', ('Kamar? Ada. Tapi... Tuan Kultivator, boleh minta tolong sebentar?', 'A room? Of course. But... honoured cultivator, may I ask a small favour?'),
          gestur='tertawa', prop='teko', pilihan=[
            P(('Ada apa, Bi?', 'What is it, auntie?'), ke='cerita'),
            P(('Aku sibuk.', 'I\'m busy.'), ke='akhir_sibuk', akibat=['bendera hong_tolak'])]),
        N('cerita', ('Anakku Ah Lun masuk sekte di gunung timur tiga musim lalu. Tak ada surat sejak itu.',
                     'My son Ah Lun joined a sect in the eastern mountains three seasons ago. Not a letter since.'),
          gestur='bicara_tangan', pilihan=[
            P(('Sekte mana?', 'Which sect?'), ke='sekte', syarat=['situs_dikenal sekte/kompleks']),
            P(('Akan kucari.', 'I will look for him.'), ke='terima', akibat=['beri cerita:surat_bibi_hong', f'quest_mulai {q}'])]),
        N('sekte', ('Katanya %s. Bawa surat ini, ya?', 'He said %s. Will you take this letter?'), isian=['sekte_terdekat'],
          gestur='menangis', pilihan=[
            P(('Akan kuantar.', 'I will deliver it.'), ke='terima', akibat=['beri cerita:surat_bibi_hong', f'quest_mulai {q}'])]),
        N('terima', ('Semoga langit membalas kebaikanmu! Kamar malam ini gratis.', 'May heaven repay your kindness! Tonight\'s room is free.'),
          gestur='hormat', akibat=['bendera inap_gratis_1']),
        N('akhir_sibuk', ('Ya sudah... kamar tiga NB semalam.', 'Very well... a room is three SV a night.'), gestur='kipas'),
    ], prioritas=30, syarat=[baru(q), '!bendera hong_tolak'])
    D('ah_lun_temu', ['tokoh:ah_lun', 'peran:murid_sekte_luar'], [
        N('mulai', ('Ah Lun? ...Itu aku. Ibu menulis surat? Ibuku?', 'Ah Lun? ...That is me. Mother wrote a letter? My mother?'),
          gestur='kaget', akibat=['jadi_tokoh ah_lun'], pilihan=[
            P(('(Serahkan surat Bibi Hong)', '(Hand over Bibi Hong\'s letter)'), ke='baca', syarat=['punya cerita:surat_bibi_hong'],
              tampil=True, akibat=['ambil cerita:surat_bibi_hong', f'quest_maju {q}'],
              alasan=('Surat itu tidak ada padamu.', 'You do not have the letter.'))]),
        N('baca', ('...Ia masih menaruh kayu manis di setiap surat. (Ah Lun mengusap matanya dengan lengan baju.) Maaf. Debu. Banyak '
                   'debu di halaman ini.',
                   '...She still puts cinnamon in every letter. (Ah Lun wipes his eyes with his sleeve.) Sorry. Dust. Lots of dust in '
                   'this yard.'),
          gestur='menangis'),
    ], prioritas=50, syarat=[tahap(q, 'temu')])
    D('ah_lun_tebus', 'peran:tetua_sekte', [
        N('mulai', ('Murid luar adalah milik sekte sampai sekte melepaskannya. Kalau kau mau membawanya pulang, bayar, atau buktikan '
                    'bahwa kau lebih kuat dari aturan kami.',
                    'An outer disciple belongs to the sect until the sect releases him. If you want to take him home, pay, or prove '
                    'you are stronger than our rules.'),
          gestur='sikap_angkuh', pilihan=[
            P(('(Bayar tebusan)', '(Pay the ransom)'), ke='lepas', syarat=['nb_pita 100'], tampil=True,
              akibat=['bayar_pita 100', f'quest_maju {q}'], alasan=('Tebusannya lebih dari isi kantongmu.', 'The ransom exceeds your purse.')),
            P(('(Tantang duel)', '(Challenge to a duel)'), akibat=['mulai_duel']),
            P(('Nanti.', 'Later.'))]),
        N('lepas', ('Ambil bocah itu. Ia menyapu terlalu lambat.', 'Take the boy. He sweeps too slowly anyway.'), gestur='kipas'),
    ], prioritas=50, syarat=[tahap(q, 'tahan')])
    D('bibi_hong_bahagia', 'tokoh:bibi_hong', [
        N('mulai', ('Anak keduaku! Duduk, duduk, makan dulu. Ah Lun, tuangkan teh untuk penyelamatmu! ...Lihat, ia sudah bisa '
                    'menuang tanpa tumpah.',
                    'My second child! Sit, sit, eat first. Ah Lun, pour tea for your saviour! ...Look, he can pour without spilling now.'),
          gestur='tuang_teh', prop='teko', akibat=['efek minecraft:regeneration 100 0']),
    ], prioritas=8, syarat=[f'quest {q} selesai', 'bendera ah_lun_pulang'])
    D('bibi_hong_surat', 'tokoh:bibi_hong', [
        N('mulai', ('Surat Ah Lun kusimpan di bawah bantal. Setiap malam kubaca sekali. Ia sudah bisa menyalakan api tanpa batu, '
                    'katanya! Anakku!',
                    'I keep Ah Lun\'s letter under my pillow. I read it once every night. He can light fire without flint now, he '
                    'says! My boy!'),
          gestur='tertawa', prop='teko'),
    ], prioritas=8, syarat=[f'quest {q} selesai', '!bendera ah_lun_pulang'])

    # ------------------------------------------------------------------ tikus_dapur (Gendut Zhu)
    q = 'tikus_dapur'
    Q(q, 'tokoh:gendut_zhu', 'rumah_makan', ('Tikus Dapur', 'The Kitchen Rat'),
      ('Seekor tikus roh sebesar anak anjing memakan beras roh Gendut Zhu. Zhu terlalu takut untuk masuk gudangnya sendiri.',
       'A spirit rat the size of a puppy is eating Fat Zhu\'s spirit rice. Zhu is too afraid to enter his own storeroom.'),
      [T('jerat', ('Bawa empat gulung benang untuk jerat kepada Gendut Zhu.', 'Bring four bundles of string for snares to Fat Zhu.'),
         tujuan=['antar minecraft:string tokoh:gendut_zhu x4']),
       T('jaga', ('Berjagalah di ibu kota setelah gelap (jam 21 sampai tengah malam).', 'Keep watch in the capital after dark (21:00 to midnight).'),
         tujuan=['jam 21 24', 'pergi kota/kota_besar'], akibat=['kabar tikus_roh']),
       T('kucing', ('Jerat saja tidak cukup. Pinjam kucing pemburu dari Ma Laoliu di Pasar Binatang.',
                    'Snares are not enough. Borrow a hunting cat from Ma Laoliu at the Beast Market.'), tujuan=['bicara tokoh:ma_laoliu']),
       T('kembali', ('Bawa Si Belang ke dapur Gendut Zhu.', 'Bring Stripes to Fat Zhu\'s kitchen.'), tujuan=['bicara tokoh:gendut_zhu'],
         akibat=['efek minecraft:saturation 10 0', 'efek minecraft:regeneration 200 0', 'bendera makan_gratis', 'nama_baik 3'],
         hasil=('Si Belang menangkap tikus roh itu dalam semalam. Gendut Zhu menamai sup barunya "Sup Pahlawan", dan kau makan '
                'gratis di sana selama ia masih ingat, yaitu selamanya.',
                'Stripes caught the spirit rat in one night. Fat Zhu named his new soup "Hero\'s Soup", and you eat there free for '
                'as long as he remembers, which is forever.'))])
    D('tikus_tawar', 'tokoh:gendut_zhu', [
        N('mulai', ('Ssst! Kau... kau kultivator, kan? Ada... ada TIKUS ROH di gudang! Besar sekali! Matanya menyala! Ia makan sekarung '
                    'beras roh semalam!',
                    'Shh! You... you are a cultivator, right? There is... there is a SPIRIT RAT in the storeroom! Huge! Glowing eyes! '
                    'It ate a whole sack of spirit rice last night!'),
          gestur='kaget', pilihan=[
            P(('Akan kutangkap.', 'I will catch it.'), ke='jerat', akibat=[f'quest_mulai {q}']),
            P(('Kenapa tidak kau tangkap sendiri?', 'Why not catch it yourself?'), ke='takut')]),
        N('takut', ('Aku? Aku koki! Koki memotong daging yang sudah diam! Yang ini... yang ini MELIHATKU!',
                    'Me? I am a cook! Cooks cut meat that already lies still! This one... this one LOOKED AT ME!'),
          gestur='lari_takut', pilihan=[P(('Baiklah, akan kutangkap.', 'Fine, I will catch it.'), ke='jerat', akibat=[f'quest_mulai {q}'])]),
        N('jerat', ('Jerat! Kita butuh jerat! Benang, banyak benang. Aku akan memasak bakpao sebagai umpan. Bakpao terbaikku. Sayang sekali.',
                    'Snares! We need snares! String, lots of string. I will steam buns for bait. My best buns. Such a waste.'),
          gestur='masak', prop='sendok_sayur'),
    ], prioritas=30, syarat=[baru(q)])
    D('tikus_ma', 'tokoh:ma_laoliu', [
        N('mulai', ('Tikus roh di dapur Zhu? Ha! Bawa Si Belang. Ia tidak takut apa pun di dunia ini, kecuali Zhu yang sedang bernyanyi.',
                    'A spirit rat in Zhu\'s kitchen? Ha! Take Stripes. She fears nothing in this world, except Zhu when he sings.'),
          gestur='lempar_pakan', akibat=['bendera tikus_si_belang']),
    ], prioritas=40, syarat=[tahap(q, 'kembali'), '!bendera tikus_si_belang'])

    # ------------------------------------------------------------------ binatang_curian (Ma Laoliu)
    q = 'binatang_curian'
    Q(q, 'tokoh:ma_laoliu', 'pasar_binatang', ('Binatang Curian', 'The Stolen Kit'),
      ('Anak rubah roh berbulu perak dicuri pemburu liar dari kandang Ma Laoliu. Jejaknya menuju jalan dan bau asap kemah.',
       'A silver spirit fox kit was stolen from Ma Laoliu\'s pens by poachers. The trail leads to the road and the smell of campfire.'),
      [T('lacak', ('Lacak pemburu liar ke kemah begal terdekat di tepi jalan.', 'Track the poachers to the nearest bandit camp by the road.'),
         tujuan=['pergi pos/kemah_begal'], arah='pos/kemah_begal'),
       T('rebut', ('Kalahkan para begal di kemah itu dan rebut anak rubah roh.', 'Defeat the bandits at the camp and take back the fox kit.'),
         tujuan=['kalahkan kultivator:rogue_cultivator x3'], akibat=['beri cerita:anak_rubah_roh'], arah='pos/kemah_begal'),
       T('pulang', ('Bawa anak rubah roh pulang kepada Ma Laoliu.', 'Bring the fox kit home to Ma Laoliu.'),
         tujuan=['antar cerita:anak_rubah_roh tokoh:ma_laoliu'], akibat=['beri_nb_pita 25', 'nama_baik 4', 'bendera ma_diskon', 'kabar rubah_pulang pemain'],
         hasil=('Anak rubah itu melompat ke pelukan Ma Laoliu dan menggigit janggutnya. Ma Laoliu tertawa sampai menangis dan '
                'memberimu harga sahabat di lapaknya.',
                'The kit leapt into Ma Laoliu\'s arms and bit his beard. Ma Laoliu laughed until he cried and gave you a friend\'s '
                'price at his stall.'))])
    D('rubah_tawar', 'tokoh:ma_laoliu', [
        N('mulai', ('Anak rubah roh, perak, baru tiga minggu. Dicuri semalam dari kandangku. Gemboknya dibuka, bukan dipatahkan: '
                    'tangan yang sudah sering mencuri. Jejak sepatunya ke arah jalan, dan bau asap kemah.',
                    'A spirit fox kit, silver, three weeks old. Stolen from my pens last night. The lock was opened, not broken: '
                    'practised hands. The boot tracks lead to the road, with the smell of campfire.'),
          gestur='marah_tunjuk', pilihan=[
            P(('Akan kubawa pulang.', 'I will bring it home.'), ke='pesan', akibat=[f'quest_mulai {q}']),
            P(('Untuk apa mereka mencurinya?', 'Why would they steal it?'), ke='kenapa')]),
        N('kenapa', ('Bulu rubah roh perak dijual mahal di Pasar Gelap. Anaknya lebih mahal lagi, karena bisa dipaksa tumbuh dengan pil '
                     'busuk. Aku tidak mau membayangkannya.',
                     'Silver spirit fox fur sells dearly on the Black Market. Kits sell for more, because they can be forced to grow '
                     'with foul pills. I do not want to imagine it.'),
          gestur='menangis', pilihan=[P(('Akan kubawa pulang.', 'I will bring it home.'), ke='pesan', akibat=[f'quest_mulai {q}'])]),
        N('pesan', ('Ia suka disentuh di belakang telinga. Kalau ia menggigit, artinya ia sayang.',
                    'She likes to be touched behind the ears. If she bites, it means she loves you.'), gestur='lempar_pakan'),
    ], prioritas=30, syarat=[baru(q)])

    # ------------------------------------------------------------------ peta_awan (Yun Fei)
    q = 'peta_awan'
    Q(q, 'tokoh:yun_fei', 'dermaga', ('Peta Awan', 'The Cloud Chart'),
      ('Kapten Yun Fei butuh kurir untuk peti bersegelnya, ke istana langit sekte atau ke kota kultivator lain. Jangan dibuka.',
       'Captain Yun Fei needs a courier for his sealed crate, to a sect\'s sky palace or another cultivator town. Do not open it.'),
      [T('muat', ('Muatan Awan ada padamu.', 'The Cloud Cargo is yours to carry.'), tujuan=SEGERA, akibat_masuk=['beri cerita:muatan_awan'],
         cabang=[(['bendera awan_langit'], 'langit')], ke='kota'),
       T('langit', ('Antar Muatan Awan ke istana langit sebuah sekte.', 'Deliver the Cloud Cargo to a sect\'s sky palace.'),
         tujuan=['pergi sekte/istana_langit'], akibat=['ambil cerita:muatan_awan'], ke='bayar', arah='sekte/istana_langit'),
       T('kota', ('Antar Muatan Awan ke sebuah kota kultivator.', 'Deliver the Cloud Cargo to a cultivator town.'),
         tujuan=['pergi kota/kota_kultivator'], akibat=['ambil cerita:muatan_awan'], ke='bayar', arah='kota/kota_kultivator'),
       T('bayar', ('Kembalilah kepada Kapten Yun Fei untuk upahmu.', 'Return to Captain Yun Fei for your pay.'), tujuan=['bicara tokoh:yun_fei'],
         akibat=['beri_nb_pita 40', 'nama_baik 3', 'bendera awan_gratis'],
         hasil=('Yun Fei membayarmu dan bersumpah isi peti itu "cuma surat cinta dan kue bulan". Perjalanan awanmu berikutnya gratis.',
                'Yun Fei paid you and swore the crate held "only love letters and mooncakes". Your next cloud voyage is free.'))])
    D('awan_tawar', 'tokoh:yun_fei', [
        N('mulai', ('Butuh pekerjaan? Peti ini harus sampai ke tujuannya, dan aku harus tetap di sini menunggu angin. Jangan dibuka. '
                    'Serius. Jangan.',
                    'Need work? This crate must reach its destination, and I must stay here and wait for the wind. Do not open it. '
                    'Seriously. Do not.'),
          gestur='lambai', suara='kotabesar.dock_depart', pilihan=[
            P(('Ke istana langit sekte.', 'To a sect\'s sky palace.'), ke='ok', syarat=['situs_dikenal sekte/istana_langit'], tampil=True,
              akibat=['bendera awan_langit', f'quest_mulai {q}'],
              alasan=('Kau belum tahu letak istana langit mana pun.', 'You do not know where any sky palace is.')),
            P(('Ke kota kultivator lain.', 'To another cultivator town.'), ke='ok', akibat=['bendera awan_kota', f'quest_mulai {q}']),
            P(('Apa isinya?', 'What is inside?'), ke='isi')]),
        N('isi', ('Surat cinta dan kue bulan. Dan mungkin satu dua hal yang lebih baik tidak kau ketahui demi kesehatan jantungmu.',
                  'Love letters and mooncakes. And maybe one or two things better left unknown, for the sake of your heart.'),
          gestur='tertawa', pilihan=[
            P(('Baiklah, ke kota kultivator.', 'Fine, to a cultivator town.'), ke='ok', akibat=['bendera awan_kota', f'quest_mulai {q}'])]),
        N('ok', ('Bagus! Angin baik menyertaimu, dan kalau angin buruk, larilah.', 'Splendid! May good winds go with you, and if the wind turns bad, run.'),
          gestur='hormat'),
    ], prioritas=30, syarat=[baru(q)])

    # ------------------------------------------------------------------ kafilah_terakhir (Lao Qiao)
    q = 'kafilah_terakhir'
    Q(q, 'tokoh:lao_qiao', 'jalan', ('Kafilah Terakhir', 'The Last Caravan'),
      ('Kafilah terakhir Lao Qiao musim ini harus sampai di kota, dan jalannya sedang dibegal.',
       'Lao Qiao\'s last caravan of the season must reach town, and the road is plagued by bandits.'),
      [T('begal', ('Para begal menghadang! Kalahkan mereka.', 'Bandits block the way! Defeat them.'),
         akibat_masuk=['panggil_peran pembunuh_bayaran musuh 2'], tujuan=['kalahkan peran:pembunuh_bayaran x2'], batas_hari=2, gagal_ke='hilang'),
       T('siap', ('Bicaralah dengan Lao Qiao dan lanjutkan perjalanan.', 'Speak to Lao Qiao and continue the journey.'),
         batas_hari=2, gagal_ke='hilang'),
       T('kawal', ('Kawal Lao Qiao sampai ke kota terdekat.', 'Escort Lao Qiao to the nearest town.'),
         tujuan=['kawal tokoh:lao_qiao kota/'], batas_hari=2, gagal_ke='hilang', arah='kota/'),
       T('untung', ('Kafilah sampai di kota.', 'The caravan reaches town.'), tujuan=SEGERA,
         akibat=['beri_nb_pita 50', 'ambil cerita:bekal_kafilah', 'nama_baik 3', 'kabar kafilah_selamat pemain'], ke='selesai',
         hasil=('Lao Qiao menjual dagangannya dalam satu sore dan membagi untungnya denganmu: seperempat, sesuai janji, ditambah '
                'sebungkus garam gunung "untuk keberuntungan".',
                'Lao Qiao sold his goods in one afternoon and shared the profit with you: a quarter, as promised, plus a pouch of '
                'mountain salt "for luck".')),
       T('hilang', ('Kafilah tercerai-berai.', 'The caravan scatters.'), tujuan=SEGERA, ke='gagal',
         hasil=('Kafilah tercerai-berai di jalan. Lao Qiao selamat, tetapi dagangannya tidak. "Musim depan," katanya, "selalu ada '
                'musim depan."',
                'The caravan scattered on the road. Lao Qiao survived; his goods did not. "Next season," he said, "there is always '
                'a next season."'))])
    D('kafilah_tawar', 'tokoh:lao_qiao', [
        N('mulai', ('Ini kafilah terakhirku musim ini. Jalan ke kota sedang dibegal, dan pengawalku kabur kemarin. Kawal aku sampai ke '
                    'kota, dan separuh untungku milikmu. Sepertiga. Seperempat, final.',
                    'This is my last caravan of the season. The road to town is full of bandits, and my guard ran off yesterday. '
                    'Escort me to town, and half my profit is yours. A third. A quarter, final.'),
          gestur='seru_dagang', prop='pikulan', suara='tempat.peddler', pilihan=[
            P(('Ayo berangkat.', 'Let us go.'), ke='jalan', akibat=['beri cerita:bekal_kafilah', f'quest_mulai {q}']),
            P(('Separuh, atau tidak sama sekali.', 'Half, or nothing.'), ke='tawar')]),
        N('tawar', ('Separuh? Kau mau aku makan angin? ...Seperempat dan sebungkus garam gunung. Garam gunung, Tuan! Itu hampir emas.',
                    'Half? You want me to eat the wind? ...A quarter and a pouch of mountain salt. Mountain salt, sir! That is almost gold.'),
          gestur='tawar', pilihan=[
            P(('Setuju. Ayo berangkat.', 'Agreed. Let us go.'), ke='jalan', akibat=['beri cerita:bekal_kafilah', f'quest_mulai {q}'])]),
        N('jalan', ('Itu mereka! Begal! Aku sembunyi di belakang gerobak, kau... kau lakukan apa yang kultivator lakukan!',
                    'There they are! Bandits! I will hide behind the cart, you... you do whatever cultivators do!'),
          gestur='takut_sembunyi', suara='tempat.bandit'),
    ], prioritas=30, syarat=[baru(q)])
    D('kafilah_siap', 'tokoh:lao_qiao', [
        N('mulai', ('Kau masih hidup! Bagus, bagus. Ayo cepat, sebelum mereka membawa teman. Tetap di dekatku, ya?',
                    'You are still alive! Good, good. Quickly now, before they bring friends. Stay close to me, yes?'),
          gestur='jalan_pikul', prop='pikulan', pilihan=[
            P(('(Berjalan di samping gerobak)', '(Walk beside the cart)'), akibat=['kawal_mulai 40', f'quest_maju {q}'])]),
    ], prioritas=50, syarat=[tahap(q, 'siap')])

    # ------------------------------------------------------------------ gagal_panen (village head)
    q = 'gagal_panen'
    Q(q, 'peran:kepala_desa', 'kampung', ('Gagal Panen', 'The Failing Harvest'),
      ('Padi desa menguning sebelum waktunya. Air roh, atau binatang yang menginjak saluran air dari sarangnya di hutan?',
       'The village rice yellows before its time. Spirit water, or the beasts that trample the channels from their den in the woods?'),
      [T('pilih', ('Kau memutuskan cara menolong desa.', 'You decide how to help the village.'), tujuan=SEGERA,
         cabang=[(['bendera panen_binatang'], 'sarang')], ke='air'),
       T('air', ('Ambil Air Roh dari Mata Air Roh untuk ladang.', 'Fetch Spirit Water from a spirit spring for the fields.'),
         tujuan=['pergi situs/mata_air_roh'], akibat=['beri cerita:air_roh_ladang'], ke='siram', arah='situs/mata_air_roh'),
       T('siram', ('Bawa Air Roh kepada Kepala Desa.', 'Bring the Spirit Water to the village head.'),
         tujuan=['antar cerita:air_roh_ladang peran:kepala_desa'], ke='syukur', arah='kampung/'),
       T('sarang', ('Pergilah ke Sarang Binatang terdekat di hutan.', 'Go to the nearest Beast Den in the woods.'),
         tujuan=['pergi gua/sarang_binatang'], ke='usir', arah='gua/sarang_binatang'),
       T('usir', ('Usir binatang-binatang buas di sekitar sarang.', 'Drive off the savage beasts around the den.'),
         tujuan=['kalahkan monster x6'], ke='lapor_desa'),
       T('lapor_desa', ('Kabari Kepala Desa bahwa salurannya aman.', 'Tell the village head the channels are safe.'),
         tujuan=['bicara peran:kepala_desa'], ke='syukur', arah='kampung/'),
       T('syukur', ('Desa berterima kasih.', 'The village gives thanks.'), tujuan=SEGERA,
         akibat=['nama_baik 6', 'beri minecraft:bread 8', 'bendera desa_mau_ikrar', 'kabar panen_selamat pemain'], ke='selesai',
         hasil=('Seminggu kemudian padi kembali hijau. Desa mengadakan kenduri kecil dan kursi kehormatan diberikan padamu. Kepala '
                'Desa berbisik: "Kalau kau mau, desa ini akan berikrar padamu."',
                'A week later the rice was green again. The village held a small feast and gave you the seat of honour. The village '
                'head whispered: "If you wish it, this village will pledge itself to you."'))])
    D('panen_tawar', 'peran:kepala_desa', [
        N('mulai', ('Pengembara, beristirahatlah sebentar di desa kami. ...Tapi lihatlah ladang itu: daun padi menguning sebelum '
                    'waktunya. Tetua bilang air kami kehilangan rohnya, atau binatang dari sarang di hutan menginjak-injak saluran.',
                    'Traveller, rest a while in our village. ...But look at those fields: the rice leaves yellow before their time. '
                    'The elders say our water has lost its spirit, or beasts from the den in the woods trample the channels.'),
          gestur='tatap_langit', suara='tempat.village_elder', pilihan=[
            P(('Aku akan mengambil air roh.', 'I will fetch spirit water.'), ke='terima', akibat=['bendera panen_air', f'quest_mulai {q}']),
            P(('Aku akan mengusir binatangnya.', 'I will drive off the beasts.'), ke='terima', akibat=['bendera panen_binatang', f'quest_mulai {q}']),
            P(('Semoga panen kalian membaik.', 'May your harvest recover.'))]),
        N('terima', ('Langit mengirimmu! Kami tidak punya banyak, tapi lumbung kami terbuka untukmu.',
                     'Heaven sent you! We do not have much, but our granary is open to you.'), gestur='hormat'),
    ], prioritas=30, syarat=[baru(q)])

    # ------------------------------------------------------------------ anak_berbakat (village head)
    q = 'anak_berbakat'
    Q(q, 'peran:kepala_desa', 'kampung', ('Anak Berbakat', 'The Gifted Child'),
      ('Seorang anak desa bisa membuat air sumur beriak hanya dengan menatapnya. Bawa ia ke tempat yang bisa mengajarinya.',
       'A village child can make the well water ripple just by looking at it. Take them where they can be taught.'),
      [T('jemput', ('Bicaralah dengan anak itu dan ajak ia ikut.', 'Speak to the child and ask them to come.'),
         cabang=[(['bendera bakat_aula'], 'aula')], ke='sekte', batas_hari=3, gagal_ke='ragu'),
       T('sekte', ('Kawal anak itu ke kompleks sekte terdekat.', 'Escort the child to the nearest sect compound.'),
         tujuan=['kawal peran:anak sekte/kompleks'], akibat=['sikap_sekte terdekat 5', 'kabar bakat_sekte pemain'], ke='selesai',
         batas_hari=3, gagal_ke='ragu', arah='sekte/kompleks',
         hasil=('Tetua sekte menyentuh dahi anak itu dan terdiam lama. "Akar roh air, murni," katanya. Sekte itu mengingat namamu '
                'dengan baik.',
                'The sect elder touched the child\'s brow and was silent a long time. "A pure water spirit root," he said. The sect '
                'remembers your name well.')),
       T('aula', ('Kawal anak itu ke aula sektemu.', 'Escort the child to your sect hall.'), tujuan=['kawal peran:anak sekte/aula'],
         akibat=['bendera anak_berbakat_aula', 'pesan kultivator.cerita.pesan.calon'], ke='selesai', batas_hari=3, gagal_ke='ragu',
         arah='sekte/aula',
         hasil=('Anak itu berdiri di gerbang aulamu dengan mata lebar, memeluk buntalan kecilnya. Hari Pembukaan Gerbang berikutnya, '
                'ia akan menjadi calon pertama yang mengetuk.',
                'The child stood at your hall gate wide-eyed, hugging a small bundle. On the next Gate Opening Day, they will be the '
                'first candidate to knock.')),
       T('ragu', ('Anak itu pulang.', 'The child goes home.'), tujuan=SEGERA, ke='gagal',
         hasil=('Ibunya memanggilnya pulang sebelum perjalanan selesai. Air sumur desa itu masih beriak setiap kali ia lewat.',
                'Their mother called them home before the journey was done. The village well still ripples whenever they pass.'))])
    D('bakat_tawar', 'peran:kepala_desa', [
        N('mulai', ('Ada anak di desa ini yang bisa membuat air sumur beriak hanya dengan menatapnya. Ibunya takut. Aku... aku tidak tahu '
                    'harus bagaimana. Kau kultivator; kau tahu ke mana anak seperti itu harus pergi.',
                    'There is a child in this village who makes the well water ripple just by looking at it. The mother is afraid. '
                    'I... I do not know what to do. You are a cultivator; you know where such a child should go.'),
          gestur='bicara_tangan', pilihan=[
            P(('Aku akan membawanya ke sekteku.', 'I will take them to my sect.'), ke='terima', akibat=['bendera bakat_aula', f'quest_mulai {q}']),
            P(('Sekte terdekat bisa mengajarinya.', 'The nearest sect can teach them.'), ke='terima', akibat=['bendera bakat_npc', f'quest_mulai {q}']),
            P(('Biarkan ia tumbuh sebagai anak biasa.', 'Let them grow up as an ordinary child.'))]),
        N('terima', ('Anaknya di dekat sumur. Bicaralah lembut; ia mudah kaget, dan kalau kaget, sumurnya meluap.',
                     'The child is by the well. Speak gently; they startle easily, and when startled, the well overflows.'),
          gestur='tunjuk'),
    ], prioritas=25, syarat=[baru(q), 'quest gagal_panen selesai'])
    D('bakat_jemput', 'peran:anak', [
        N('mulai', ('Kakak kultivator? Aku... boleh ikut? Ibu bilang aku harus berani. Aku berani. Sedikit.',
                    'Big cultivator? Can I... come with you? Mother says I must be brave. I am brave. A little.'),
          gestur='sikap_anak', pilihan=[
            P(('Pegang tanganku. Kita berangkat.', 'Hold my hand. Let us go.'), akibat=['kawal_mulai 40', f'quest_maju {q}']),
            P(('Pamitlah dulu pada ibumu.', 'Say goodbye to your mother first.'))]),
    ], prioritas=50, syarat=[tahap(q, 'jemput')])

    # ------------------------------------------------------------------ tuan_muda_sombong (young master)
    q = 'tuan_muda_sombong'
    Q(q, 'peran:tuan_muda', 'desa_klan', ('Tuan Muda yang Sombong', 'The Arrogant Young Master'),
      ('Tuan Muda klan menantangmu di depan seluruh desanya. Kalahkan dia, atau mengalah.',
       'The clan\'s young master challenges you before his whole village. Defeat him, or yield.'),
      [T('duel', ('Kalahkan Tuan Muda dalam duel di depan klannya.', 'Defeat the young master in a duel before his clan.'),
         tujuan=['duel peran:tuan_muda'], cabang=[(['bendera klan_meremehkan'], 'mengalah'), (['bendera klan_hormat'], 'menang')],
         ke='menang', batas_hari=3, gagal_ke='mengalah'),
       T('menang', ('Tuan Muda jatuh di depan klannya.', 'The young master falls before his clan.'), tujuan=SEGERA,
         akibat=['nama_baik 5', 'bendera klan_hormat', 'kabar tuan_muda_kalah pemain'], ke='selesai',
         hasil=('Tuan Muda bangkit dari debu dan membungkuk, kaku tapi tulus. Malamnya sang Patriark mengundangmu minum di aula '
                'leluhur; tanah klan ini terbuka bagimu.',
                'The young master rose from the dust and bowed, stiff but sincere. That night the Patriarch invited you to drink in '
                'the ancestral hall; the clan\'s land is open to you.')),
       T('mengalah', ('Kau mengalah.', 'You yield.'), tujuan=SEGERA, akibat=['bendera klan_meremehkan', 'nama_baik -2'], ke='selesai',
         hasil=('Kau mengalah. Tuan Muda tertawa dan klan bersorak. Harga di desa ini sedikit lebih mahal untukmu sekarang; harga '
                'bagi orang yang tidak berdiri tegak.',
                'You yielded. The young master laughed and the clan cheered. Prices in this village are a little higher for you '
                'now; the price for one who does not stand tall.'))])
    D('tuan_muda_tantang', 'peran:tuan_muda', [
        N('mulai', ('Kau. Orang luar yang berjalan di jalan klan kami seperti pemiliknya. Tahu dirilah!',
                    'You. An outsider who walks our clan\'s road as if you owned it. Know your place!'),
          gestur='sikap_angkuh', suara='tempat.young_master', pilihan=[
            P(('Aku hanya lewat.', 'I am only passing through.'), ke='hina'),
            P(('(Tatap matanya tanpa berkata apa-apa)', '(Meet his eyes without a word)'), ke='mundur', syarat=['watak penakut']),
            P(('(Tatap matanya tanpa berkata apa-apa)', '(Meet his eyes without a word)'), ke='hina', syarat=['!watak penakut'])]),
        N('hina', ('Hanya lewat? Seluruh desa melihatmu tidak membungkuk padaku! Terimalah tantanganku, atau berlututlah!',
                   'Only passing? The whole village saw you not bow to me! Accept my challenge, or kneel!'),
          gestur='marah_tunjuk', pilihan=[
            P(('Kuterima tantanganmu.', 'I accept your challenge.'), akibat=['beri cerita:surat_tuan_muda', f'quest_mulai {q}', 'tanding_klan']),
            P(('Aku mengalah.', 'I yield.'), ke='tawa', akibat=['bendera klan_meremehkan', f'quest_mulai {q}', f'quest_maju {q}'])]),
        N('mundur', ('...A-aku hanya menguji keberanianmu! Ya. Kau lulus. Sekarang pergilah sebelum aku berubah pikiran.',
                     '...I-I was only testing your courage! Yes. You pass. Now go before I change my mind.'),
          gestur='lari_takut', akibat=['bendera klan_hormat', f'quest_mulai {q}', f'quest_maju {q}']),
        N('tawa', ('Ha! Kalian lihat? Orang luar pun tahu siapa yang berkuasa di sini!', 'Ha! You see? Even an outsider knows who rules here!'),
          gestur='tertawa'),
    ], prioritas=30, syarat=[baru(q)])
    D('tuan_muda_duel', 'peran:tuan_muda', [
        N('mulai', ('Masih berani kembali? Bagus. Ayo selesaikan!', 'You dare come back? Good. Let us finish this!'),
          gestur='hunus', pilihan=[
            P(('(Bertarung)', '(Fight)'), akibat=['tanding_klan']),
            P(('Aku mengalah.', 'I yield.'), akibat=['bendera klan_meremehkan', f'quest_maju {q}'])]),
    ], prioritas=50, syarat=[tahap(q, 'duel')])

    # ------------------------------------------------------------------ murid_kabur (sect elder)
    q = 'murid_kabur'
    Q(q, 'peran:tetua_sekte', 'kompleks', ('Murid yang Kabur', 'The Runaway Disciple'),
      ('Seorang murid dalam kabur membawa kitab sekte. Ia terlihat di pos singgah di jalan. Kitabnya, atau muridnya?',
       'An inner disciple ran away with a sect manual. He was seen at a road post. The manual, or the disciple?'),
      [T('cari', ('Cari murid yang kabur di pos singgah terdekat.', 'Find the runaway at the nearest road post.'),
         tujuan=['pergi pos/singgah'], arah='pos/singgah'),
       T('temu', ('Bicaralah dengan murid yang kabur.', 'Speak with the runaway.'), akibat_masuk=['panggil_peran murid_sekte_dalam'],
         cabang=[(['bendera kabur_lindungi'], 'lindungi')], ke='kembali'),
       T('kembali', ('Kembalikan Kitab Curian kepada tetua sekte.', 'Return the stolen manual to the sect elder.'),
         tujuan=['antar cerita:kitab_curian peran:tetua_sekte'], akibat=['sikap_sekte terdekat 5', 'beri_nb_pita 30'], ke='selesai',
         arah='sekte/kompleks',
         hasil=('Tetua menerima kitabnya kembali. Ketika kau menyebut air terjun itu, ia terdiam, dan minggu itu tak ada murid yang '
                'berlatih sampai pingsan.',
                'The elder took his manual back. When you mentioned the waterfall he fell silent, and that week no disciple trained '
                'until they fainted.')),
       T('lindungi', ('Kau membiarkannya pergi.', 'You let him go.'), tujuan=SEGERA,
         akibat=['bendera calon_murid_kabur', 'pesan kultivator.cerita.pesan.calon_kabur'], ke='selesai',
         hasil=('Murid itu menghilang di jalan dengan kitabnya. Sekte itu mengingat wajahmu dengan dingin, tetapi seseorang di luar '
                'sana berutang nyawa padamu.',
                'The disciple vanished down the road with his manual. The sect remembers your face coldly, but someone out there '
                'owes you a life.'))])
    D('kabur_tawar', 'peran:tetua_sekte', [
        N('mulai', ('Seorang murid dalam kabur membawa Kitab Langkah Awan, kitab yang belum pantas ia baca. Ia terlihat di pos singgah '
                    'di jalan. Bawa kitab itu kembali. Muridnya... terserah langit.',
                    'An inner disciple ran away with the Cloudstep Manual, a book he is not yet fit to read. He was seen at a road '
                    'post. Bring the manual back. The disciple... is heaven\'s business.'),
          gestur='sikap_tangan_belakang', suara='tempat.sect_visit', pilihan=[
            P(('Akan kucari.', 'I will find him.'), akibat=[f'quest_mulai {q}']),
            P(('Kenapa ia kabur?', 'Why did he run?'), ke='kenapa')]),
        N('kenapa', ('Karena ia lemah. Yang lemah selalu punya alasan, dan alasan tidak membuat siapa pun kuat.',
                     'Because he is weak. The weak always have reasons, and reasons make no one strong.'),
          gestur='sikap_angkuh', pilihan=[P(('Akan kucari.', 'I will find him.'), akibat=[f'quest_mulai {q}'])]),
    ], prioritas=30, syarat=[baru(q)])
    D('kabur_temu', 'peran:murid_sekte_dalam', [
        N('mulai', ('Jangan mendekat! ...Kau dari sekte? Kalau kau mau kitabnya, ambil. Tapi dengar dulu kenapa aku lari.',
                    'Stay back! ...Are you from the sect? If you want the manual, take it. But first hear why I ran.'),
          gestur='takut_sembunyi', pilihan=[P(('Bicaralah.', 'Speak.'), ke='alasan')]),
        N('alasan', ('Tetuaku menyuruh kami berlatih di bawah air terjun sampai pingsan. Dua saudaraku tidak bangun lagi. Kitab ini '
                     'satu-satunya cara aku bisa kuat tanpa dia.',
                     'My elder made us train under the waterfall until we fainted. Two of my brothers never woke. This manual is my '
                     'only way to grow strong without him.'),
          gestur='menangis', prop='kitab', pilihan=[
            P(('Serahkan kitabnya. Aku akan bicara pada tetuamu.', 'Give me the manual. I will speak to your elder.'), ke='serah',
              akibat=['beri cerita:kitab_curian', 'bendera kabur_kembali', f'quest_maju {q}']),
            P(('Pergilah. Aku tidak melihatmu.', 'Go. I never saw you.'), ke='pergi',
              akibat=['bendera kabur_lindungi', 'sikap_sekte terdekat -5', f'quest_maju {q}'])]),
        N('serah', ('...Kau janji akan bicara padanya? ...Baiklah. Ini. Aku akan pulang ke desaku saja.',
                    '...You promise to speak to him? ...Fine. Here. I will just go home to my village.'),
          gestur='sikap_tunduk', akibat=['npc_pergi']),
        N('pergi', ('Terima kasih! Aku tidak akan lupa. Kalau aku jadi kuat, aku akan mencarimu!',
                    'Thank you! I will not forget. When I am strong, I will find you!'), gestur='lari_lambai', akibat=['npc_pergi']),
    ], prioritas=50, syarat=[tahap(q, 'temu')])
    D('kabur_tetua_balas', 'peran:tetua_sekte', [
        N('mulai', ('Kau bilang ia lari karena air terjun itu? ...Mungkin aku terlalu keras. Mungkin. Jangan beri tahu murid lain aku '
                    'berkata begitu.',
                    'You say he ran because of the waterfall? ...Perhaps I was too harsh. Perhaps. Do not tell the other disciples I '
                    'said so.'),
          gestur='tatap_langit', akibat=['bendera kabur_tetua_renung']),
    ], prioritas=20, syarat=[f'quest {q} selesai', 'bendera kabur_kembali', '!bendera kabur_tetua_renung'])

    # ------------------------------------------------------------------ tiga_ujian_pertapa (hermit)
    q = 'tiga_ujian_pertapa'
    Q(q, 'peran:pertapa', 'gua_pertapa', ('Tiga Ujian Pertapa', 'The Hermit\'s Three Trials'),
      ('Herba yang lebih tua darimu, duduk tanpa bergerak selama dupa terbakar, dan satu pertanyaan tanpa jawaban.',
       'A herb older than you, stillness for as long as the incense burns, and one question without an answer.'),
      [T('herba', ('Bawakan herba tua: satu Ginseng Sembilan Lekuk, atau delapan Rumput Roh.',
                   'Bring an old herb: one Ninecurl Ginseng, or eight Spirit Grass.'), arah='gua/pertapa'),
       T('meditasi', ('Bermeditasilah di ruang pertapa tanpa terputus selama dupa terbakar (lima menit).',
                      'Meditate in the hermit\'s chamber without a break for as long as the incense burns (five minutes).'),
         tujuan=['ujian pertapa:meditasi'], arah='gua/pertapa'),
       T('koan', ('Jawablah koan sang pertapa.', 'Answer the hermit\'s koan.'), arah='gua/pertapa'),
       T('lulus', ('Pertapa itu tersenyum.', 'The hermit smiles.'), tujuan=SEGERA,
         akibat=['gelar murid_pertapa', 'beri cerita:tanda_pertapa', 'bendera pertapa_lulus', 'efek minecraft:luck 6000 0',
                 'suara tempat.hermit_koan'], ke='selesai',
         hasil=('Pertapa itu tersenyum untuk pertama kalinya. Esok paginya guanya kosong; hanya tersisa tikar, mata air yang tenang, '
                'dan sebatang bambu bertuliskan satu kata: "Tenang". Gua itu kini milik siapa pun yang mau duduk di sana.',
                'The hermit smiled for the first time. The next morning the cave was empty; only a mat remained, a quiet spring, and '
                'a bamboo stick carved with one word: "Still". The cave now belongs to whoever is willing to sit in it.'))])
    D('pertapa_tawar', 'peran:pertapa', [
        N('mulai', ('Engkau yang berjodoh denganku, duduklah.', 'You who share a bond with me, sit.'),
          gestur='napas_meditasi', suara='tempat.hermit', pilihan=[
            P(('(Duduk bersila)', '(Sit cross-legged)'), ke='ujian'),
            P(('Maaf, aku tersesat.', 'Sorry, I am lost.'))]),
        N('ujian', ('Tiga ujian. Bawakan herba yang lebih tua darimu. Duduklah tanpa bergerak selama dupa terbakar. Lalu jawablah satu '
                    'pertanyaan yang tidak punya jawaban.',
                    'Three trials. Bring a herb older than you. Sit without moving for as long as the incense burns. Then answer '
                    'one question that has no answer.'),
          gestur='angguk_dengar', pilihan=[
            P(('Aku siap.', 'I am ready.'), akibat=[f'quest_mulai {q}']),
            P(('Kenapa kau menyepi di sini?', 'Why do you live alone here?'), ke='sepi')]),
        N('sepi', ('Karena di kota aku terlalu sering didengar, dan terlalu jarang mendengar.',
                   'Because in town I was heard too often, and listened too rarely.'), gestur='tatap_langit', pilihan=[
            P(('Aku siap menempuh ujianmu.', 'I am ready for your trials.'), akibat=[f'quest_mulai {q}'])]),
    ], prioritas=30, syarat=[baru(q)])
    D('pertapa_herba', 'peran:pertapa', [
        N('mulai', ('Herbamu?', 'Your herb?'), gestur='napas_meditasi', pilihan=[
            P(('(Serahkan Ginseng Sembilan Lekuk)', '(Hand over a Ninecurl Ginseng)'), ke='terima', syarat=['punya kultivator:ninecurl_ginseng'],
              tampil=True, akibat=['ambil kultivator:ninecurl_ginseng', f'quest_maju {q}'],
              alasan=('Kau tidak membawa Ginseng Sembilan Lekuk.', 'You carry no Ninecurl Ginseng.')),
            P(('(Serahkan delapan Rumput Roh)', '(Hand over eight Spirit Grass)'), ke='terima', syarat=['punya kultivator:spirit_grass 8'],
              tampil=True, akibat=['ambil kultivator:spirit_grass 8', f'quest_maju {q}'],
              alasan=('Rumput Rohmu kurang dari delapan.', 'You have fewer than eight Spirit Grass.')),
            P(('Belum kutemukan.', 'I have not found one yet.'))]),
        N('terima', ('Akarnya mengingat seratus musim dingin. Bagus. Sekarang duduklah di ruang itu, dan biarkan dupa yang berbicara.',
                     'Its roots remember a hundred winters. Good. Now sit in that chamber, and let the incense speak.'),
          gestur='sujud_dupa', prop='dupa'),
    ], prioritas=50, syarat=[tahap(q, 'herba')])
    simpul = [N('mulai', ('Dengarkan baik-baik. Jawablah bukan dengan kepalamu.', 'Listen well. Do not answer with your head.'),
                gestur='napas_meditasi', suara='tempat.hermit_koan', pilihan=[])]
    for w, name in enumerate(WATAK):
        simpul[0].pilihan.append(P(('(Dengarkan koannya)', '(Listen to the koan)'), ke=f'k{w}', syarat=[f'watak {name}', '!bendera koan_salah']))
        simpul[0].pilihan.append(P(('(Dengarkan koan yang baru)', '(Listen to a new koan)'), ke=f'k{8 + w % 4}',
                                   syarat=[f'watak {name}', 'bendera koan_salah']))
    for k, (soal, jawab, benar) in enumerate(KOAN):
        pil = []
        for i, teks in enumerate(jawab):  # the right answer already sits at index 'benar'
            if i == benar:
                pil.append(P(teks, ke='benar', akibat=['bendera koan_salah off', f'quest_maju {q}']))
            else:
                pil.append(P(teks, ke='salah', akibat=['bendera koan_salah', 'efek minecraft:slowness 200 1']))
        simpul.append(N(f'k{k}', soal, gestur='angguk_dengar', pilihan=pil))
    simpul.append(N('benar', ('...Hm. Itu bukan jawaban. Itu pemahaman. Duduklah sekali lagi bersamaku, untuk terakhir kali.',
                              '...Hm. That is not an answer. That is understanding. Sit with me once more, for the last time.'),
                    gestur='tertawa'))
    simpul.append(N('salah', ('Kau menjawab dengan kepala. Pulanglah, tidurlah, dan kembali saat kepalamu lebih ringan.',
                              'You answered with your head. Go home, sleep, and return when your head is lighter.'),
                    gestur='hormat_dalam'))
    D('pertapa_koan', 'peran:pertapa', simpul, prioritas=50, syarat=[tahap(q, 'koan')])
    D('pertapa_lulus', 'peran:pertapa', [
        N('mulai', ('Mengapa kembali? Gua ini sudah bukan milikku. Duduklah kalau mau; mata air itu tidak menagih apa pun.',
                    'Why come back? This cave is no longer mine. Sit if you wish; the spring asks nothing in return.'),
          gestur='napas_meditasi'),
    ], prioritas=20, syarat=[f'quest {q} selesai'])

    # ------------------------------------------------------------------ tawanan_pulang (captive)
    q = 'tawanan_pulang'
    Q(q, 'peran:tawanan', 'sarang_iblis', ('Pulang', 'The Way Home'),
      ('Tawanan yang kau bebaskan tidak berani berjalan sendirian. Kawal ia pulang ke kampung terdekat.',
       'The captive you freed dares not walk alone. Escort them home to the nearest village.'),
      [T('kawal', ('Kawal tawanan itu pulang ke kampung terdekat.', 'Escort the captive home to the nearest village.'),
         tujuan=['kawal peran:tawanan kampung/'], batas_hari=2, gagal_ke='tersesat', arah='kampung/'),
       T('sambut', ('Keluarganya menyambut.', 'The family runs out to meet them.'), tujuan=SEGERA,
         akibat=['nama_baik 3', 'gelar pembebas', 'beri cerita:tanda_bebas', 'kabar tawanan_pulang pemain'], ke='selesai',
         hasil=('Seorang ibu berlari dari pintu rumahnya tanpa sandal. Tak seorang pun di desa itu bertanya siapa kau; mereka hanya '
                'menuang teh dan menyuruhmu duduk.',
                'A mother ran barefoot from her door. Nobody in the village asked who you were; they only poured tea and told you '
                'to sit.')),
       T('tersesat', ('Kalian terpisah di jalan.', 'You are separated on the road.'), tujuan=SEGERA, ke='gagal',
         hasil=('Kalian terpisah di jalan. Semoga ia menemukan jalannya sendiri; matahari terbit selalu di timur.',
                'You were separated on the road. May they find their own way; the sun always rises in the east.'))])
    D('tawanan_sapa', 'peran:tawanan', [
        N('mulai', ('Kami selamat... terima kasih, sungguh! Tolong, antar aku pulang ke kampungku. Aku tidak berani berjalan sendirian; '
                    'setiap bayangan terlihat seperti jubah hitam.',
                    'We\'re saved... thank you, truly! Please, take me home to my village. I dare not walk alone; every shadow looks '
                    'like a black robe.'),
          gestur='berlutut_rantai', suara='tempat.captive_saved', pilihan=[
            P(('Ikut aku. Aku akan mengantarmu.', 'Come with me. I will take you home.'), ke='ikut', akibat=[f'quest_mulai {q}', 'kawal_mulai 40']),
            P(('Larilah ke arah matahari terbit; desa ada di sana.', 'Run toward the sunrise; the village lies that way.'))]),
        N('ikut', ('Aku akan berjalan tepat di belakangmu. Kalau aku berhenti, itu cuma karena kakiku gemetar, bukan karena takut. '
                   'Sedikit karena takut.',
                   'I will walk right behind you. If I stop, it is only because my legs shake, not from fear. A little from fear.'),
          gestur='pincang'),
    ], prioritas=30, syarat=[baru(q)])

    # ================================================================== rumours of the chains
    K('quest', 'naga_muda_sahabat', 'naga_muda_sahabat', ('Feng Ao naik ke peringkat tiga Daftar Naga Muda, dan ia mentraktir %s mi! '
                                                          'Feng Ao mentraktir orang!',
                                                          'Feng Ao climbed to third on the Young Dragon List, and he bought %s '
                                                          'noodles! Feng Ao, buying for someone!'), bobot=10, umur=6,
      penyebar=['petugas_gilda', 'penguji', 'warga', 'pemilik_teh'])
    K('quest', 'naga_muda_dendam', 'naga_muda_dendam', ('Feng Ao bersumpah membalas %s. Ia berlatih sampai tengah malam di lapangan gilda.',
                                                        'Feng Ao swore revenge on %s. He trains past midnight in the guild yard.'),
      bobot=10, umur=6, penyebar=['petugas_gilda', 'penguji', 'warga'])
    K('quest', 'wesel_tertangkap', 'wesel_tertangkap', ('Pemalsu wesel tertangkap! Nyonya Qian bilang semua wesel lama harus dicap ulang.',
                                                        'The note forger was caught! Madam Qian says every old note must be re-stamped.'),
      bobot=10, umur=5, penyebar=['bankir', 'warga', 'pedagang_senjata', 'pemilik_teh'])
    K('quest', 'lonceng_dituang', 'lonceng_dituang', ('Malam ini tukang-tukang menuang perunggu di halaman kuil. Baunya seperti hujan di atas besi.',
                                                      'Tonight the craftsmen cast bronze in the temple yard. It smells like rain on iron.'),
      bobot=10, umur=2)
    K('quest', 'lonceng_pulih', 'lonceng_pulih', ('Lonceng kuil berbunyi jernih lagi! Biksu Jing Xin tersenyum seharian.',
                                                  'The temple bell rings clear again! Monk Jing Xin smiled all day.'), bobot=10, umur=5)
    K('quest', 'ah_lun_surat', 'ah_lun_surat', ('Bibi Hong akhirnya dapat surat dari anaknya. Katanya %s yang mengantarnya jauh-jauh ke gunung.',
                                                'Bibi Hong finally got a letter from her son. They say %s carried it all the way to the mountains.'),
      bobot=10, umur=6, penyebar=['pemilik_penginapan', 'pelayan', 'warga', 'koki'])
    K('quest', 'ah_lun_pulang', 'ah_lun_pulang', ('Ah Lun pulang! %s menebusnya dari sekte iblis. Bibi Hong memasak tiga hari tanpa henti.',
                                                  'Ah Lun is home! %s freed him from a demonic sect. Bibi Hong cooked for three days straight.'),
      bobot=10, umur=6, penyebar=['pemilik_penginapan', 'pelayan', 'warga', 'koki'])
    K('quest', 'tikus_roh', 'tikus_roh', ('Katanya ada tikus roh sebesar anak anjing di dapur Gendut Zhu. Pantas supnya makin sedikit dagingnya.',
                                          'They say there is a spirit rat the size of a puppy in Fat Zhu\'s kitchen. No wonder his soup has less meat.'),
      bobot=10, umur=3)
    K('quest', 'rubah_pulang', 'rubah_pulang', ('Anak rubah roh Ma Laoliu sudah pulang. %s membersihkan kemah begal di tepi jalan sendirian.',
                                                'Ma Laoliu\'s spirit fox kit is back. %s cleared the bandit camp by the road single-handed.'),
      bobot=10, umur=6, penyebar=['pedagang_binatang', 'warga', 'pedagang_keliling'])
    K('quest', 'kafilah_selamat', 'kafilah_selamat', ('Lao Qiao sampai di kota dengan kafilahnya utuh. Ia bilang %s memukul mundur begal '
                                                      'seperti mengusir ayam.',
                                                      'Lao Qiao reached town with his caravan whole. He says %s drove off the bandits '
                                                      'like shooing chickens.'), bobot=10, umur=6)
    K('quest', 'panen_selamat', 'panen_selamat', ('Padi di desa itu hijau lagi. Orang desa menyebut %s sebagai tamu yang dikirim langit.',
                                                  'The rice in that village is green again. The villagers call %s the guest sent by heaven.'),
      bobot=10, umur=8)
    K('quest', 'bakat_sekte', 'bakat_sekte', ('Seorang anak desa berakar roh air murni diterima sekte. %s yang mengantarnya.',
                                              'A village child with a pure water root was taken in by a sect. %s brought them there.'),
      bobot=10, umur=6)
    K('quest', 'tuan_muda_kalah', 'tuan_muda_kalah', ('Tuan Muda klan dikalahkan di depan seluruh desanya oleh %s. Ia sekarang menyapa '
                                                      'orang luar dengan sopan.',
                                                      'The clan\'s young master was beaten before his whole village by %s. He greets '
                                                      'outsiders politely now.'), bobot=10, umur=6)
    K('quest', 'tawanan_pulang', 'tawanan_pulang', ('Seorang tawanan sarang iblis pulang ke desanya, dikawal %s. Ibunya berlari tanpa sandal.',
                                                    'A captive of the demon lair came home, escorted by %s. Their mother ran out barefoot.'),
      bobot=10, umur=6)
