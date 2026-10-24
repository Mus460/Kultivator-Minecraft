"""38ST detail pass (W3, D-ST): dialogs for every 38 role that had none (they only had the greeting sheet), situational
variants (night, curfew, wanted, fear, the throne arc's outcome) and three short chains with branches:
  demam_kumuh    the healer: fever in the slums; refine a pill, give it free or sell it
  pedang_ayah    a palace guard: his father's broken sword; Ou Ye mends it as it was or forges it anew
  kristal_formasi  the array keeper: a cracked teleport pillar; bring amethyst, learn the array or take the fee
General dialogs carry "acak" so the varied greeting sheet still shows most days; offers wait until declined once."""

SEGERA = ['hari 0']


def tahap(q, s):
    return f'quest {q} tahap {s}'


def baru(q):
    return f'quest {q} baru'


def isi(g):
    D, N, P, T, Q, K = g.D, g.N, g.P, g.T, g.Q, g.K
    for tid, pair in {
        'balai_tabib': ('Balai Tabib', "Healers' Hall"),
        'barak_istana': ('Barak Pengawal Istana', 'Palace Guard Barracks'),
        'stasiun_teleport': ('Stasiun Formasi Teleportasi', 'Teleport Array Station'),
    }.items():
        g.tx('tempat_cerita', f'kultivator.cerita.tempat.{tid}', pair)

    # ================================================================== capital: palace guard + pedang_ayah
    q = 'pedang_ayah'
    Q(q, 'peran:pengawal_istana', 'barak_istana', ('Pedang Ayah', "Father's Sword"),
      ('Seorang pengawal istana menyimpan pedang patah ayahnya, yang gugur menahan Gelombang Binatang besar dua puluh tahun lalu. '
       'Ia ingin pedang itu utuh lagi sebelum hari peringatan.',
       'A palace guard keeps his father\'s broken sword; his father fell holding back the great Beast Tide twenty years ago. He '
       'wants it whole again before the day of remembrance.'),
      [T('empu', ('Bawa pedang patah itu kepada Ou Ye Muda di Gilda Pandai Pusaka.', 'Take the broken sword to Young Ou Ye at the Forge Guild.'),
         tujuan=['bicara tokoh:ou_ye']),
       T('besi', ('Ou Ye meminta enam batang besi untuk menyambung bilahnya.', 'Ou Ye asks for six iron ingots to join the blade.'),
         tujuan=['antar minecraft:iron_ingot tokoh:ou_ye x6']),
       T('tunggu', ('Ou Ye bekerja semalaman. Kembalilah besok.', 'Ou Ye works through the night. Come back tomorrow.'),
         tujuan=['tunggu 1'], cabang=[(['bendera pedang_ayah_baru'], 'serah_baru')], ke='serah_lama'),
       T('serah_lama', ('Ambil pedang dari Ou Ye dan serahkan kepada sang pengawal.', 'Collect the sword from Ou Ye and give it to the guard.'),
         tujuan=['bicara peran:pengawal_istana'], akibat=['nama_baik 3', 'bendera pengawal_sahabat', 'kabar pedang_ayah_lama pemain'],
         hasil=('Bilah itu disambung tanpa menyembunyikan bekas patahnya: garis perak tipis di tengah baja tua. Sang pengawal lama '
                'memandanginya, lalu tertawa pendek. "Ayah selalu bilang luka yang disembunyikan tidak pernah sembuh." Sejak hari '
                'itu para pengawal istana memberi hormat setiap kau lewat.',
                'The blade was joined without hiding the break: a thin silver line across the old steel. The guard looked at it a '
                'long time, then gave a short laugh. "Father always said a hidden wound never heals." From that day the palace '
                'guards salute whenever you pass.')),
       T('serah_baru', ('Ambil pedang dari Ou Ye dan serahkan kepada sang pengawal.', 'Collect the sword from Ou Ye and give it to the guard.'),
         tujuan=['bicara peran:pengawal_istana'], akibat=['nama_baik 3', 'bendera pengawal_sahabat', 'kabar pedang_ayah_baru pemain'],
         hasil=('Ou Ye melebur pecahannya dan menempa bilah baru, dengan nama sang ayah terukir di dekat gagang. Sang pengawal '
                'menghunusnya sekali, lalu menyarungkannya lagi dengan tangan gemetar. "Kini ia akan berjaga bersamaku." Para '
                'pengawal istana memberi hormat setiap kau lewat.',
                'Ou Ye melted the pieces and forged a new blade, with the father\'s name carved near the hilt. The guard drew it '
                'once, then sheathed it with trembling hands. "Now he will stand watch beside me." The palace guards salute '
                'whenever you pass.'))])
    D('pengawal_pedang_tawar', 'peran:pengawal_istana', [
        N('mulai', ('Tamu, tunggu. Kau... kau kenal Ou Ye Muda di gilda tempa? Orang bilang ia tidak mau menerima pesanan dari pengawal '
                    'biasa.',
                    'Visitor, wait. Do you... do you know Young Ou Ye at the forge guild? They say he will not take orders from a mere '
                    'guard.'),
          gestur='berdiri_jaga', prop='tombak', pilihan=[
            P(('Ada apa?', 'What is it?'), ke='cerita'),
            P(('Aku tidak kenal.', 'I do not know him.'), ke='tolak', akibat=[f'bendera {q}_tolak'])]),
        N('cerita', ('Ini pedang ayahku. Ia berdiri di tembok saat Gelombang Binatang besar dua puluh tahun lalu, di samping Ketua '
                     'Lin Fei yang waktu itu masih punya dua lengan. Pedangnya patah, ayahku tidak pulang. Hari peringatan sudah dekat.',
                     'This is my father\'s sword. He stood on the wall during the great Beast Tide twenty years ago, beside Guildmaster '
                     'Lin Fei when she still had two arms. The sword broke; my father did not come home. The day of remembrance is near.'),
          gestur='angguk_dengar', pilihan=[
            P(('Akan kubawa kepada Ou Ye.', 'I will take it to Ou Ye.'), ke='terima', akibat=[f'quest_mulai {q}', f'bendera {q}_tolak off']),
            P(('Maaf, aku tak bisa sekarang.', 'Sorry, I cannot right now.'), ke='tolak', akibat=[f'bendera {q}_tolak'])]),
        N('terima', ('Terima kasih. Bungkusnya kain merah; jangan dibuka di jalan. Bilahnya masih tajam, dan ayahku tidak suka '
                     'orang ceroboh.',
                     'Thank you. It is wrapped in red cloth; do not open it on the road. The blade is still sharp, and my father did not '
                     'like careless people.'),
          gestur='hormat'),
        N('tolak', ('Tentu. Aku hanya pengawal; urusanku menjaga gerbang, bukan merepotkan tamu.',
                    'Of course. I am only a guard; my duty is the gate, not troubling guests.'), gestur='sikap_tombak', prop='tombak'),
    ], prioritas=20, syarat=[baru(q), f'!bendera {q}_tolak', 'acak 70'])
    D('pedang_ayah_ou_ye', 'tokoh:ou_ye', [
        N('mulai', ('...Pedang pengawal. Baja kampung, tempaan jujur. Patahnya karena tulang binatang roh, bukan karena bajanya jelek.',
                    '...A guard\'s sword. Village steel, honest work. It broke on a spirit beast\'s bone, not because the steel was poor.'),
          gestur='lihat_barang', prop='palu', pilihan=[
            P(('Sambung seperti semula, bekasnya biarkan.', 'Join it as it was; let the scar show.'), ke='lama'),
            P(('Tempa jadi bilah baru.', 'Forge it into a new blade.'), ke='baru', akibat=['bendera pedang_ayah_baru'])]),
        N('lama', ('Baik. Bekas patah yang jujur lebih kuat dari baja yang berbohong. Enam batang besi.',
                   'Good. An honest scar is stronger than steel that lies. Six iron ingots.'), gestur='angguk_dengar'),
        N('baru', ('Hmm. Lebur, tempa ulang, ukir namanya. Bilah yang lahir dua kali. Enam batang besi.',
                   'Hmm. Melt, reforge, carve the name. A blade born twice. Six iron ingots.'), gestur='palu_landasan', prop='palu'),
    ], prioritas=45, syarat=[tahap(q, 'besi')])
    D('pedang_ayah_tunggu', 'tokoh:ou_ye', [
        N('mulai', ('Belum. Baja tidak bisa diburu-buru. Besok.', 'Not yet. Steel cannot be hurried. Tomorrow.'), gestur='palu_landasan', prop='palu'),
    ], prioritas=45, syarat=[tahap(q, 'tunggu')])
    D('pengawal_sapa', 'peran:pengawal_istana', [
        N('mulai', ('Jalan ini menuju Istana Pengawal Kota. Tamu boleh lewat sampai paifang; lebih jauh dari itu perlu panggilan.',
                    'This road leads to the Warden\'s Palace. Visitors may pass as far as the archway; beyond that you need a summons.'),
          gestur='berdiri_jaga', prop='tombak', pilihan=[
            P(('Bagaimana caranya mendapat audiensi?', 'How does one get an audience?'), ke='audiensi'),
            P(('Ceritakan tentang istana ini.', 'Tell me about this palace.'), ke='sejarah'),
            P(('Kau pernah melihat Pangeran tersenyum?', 'Have you ever seen the Prince smile?'), ke='pangeran'),
            P(('Masih soal pedang ayahmu...', 'About your father\'s sword...'), ke='pedang', syarat=[f'bendera {q}_tolak', baru(q)]),
            P(('Selamat bertugas.', 'Good watch.'))]),
        N('audiensi', ('Nama Baikmu di kota ini harus cukup tinggi, atau kau membawa sesuatu yang membuat Menteri penasaran. Dan '
                       'jangan datang saat jam makan siang; Pangeran memberi makan ikan koi sendiri.',
                       'Your Good Name in this city must be high enough, or you must bring something that makes the Minister curious. '
                       'And do not come at lunch; the Prince feeds the koi himself.'), gestur='bicara_tangan'),
        N('sejarah', ('Dulu ini benteng tujuh sekte. Lihat dinding sebelah barat: batunya beda warna. Itu bekas aula sekte yang sudah '
                      'tidak ada. Kami tidak boleh menyebut namanya.',
                      'Once this was the fortress of seven sects. Look at the west wall: the stones are a different colour. That was '
                      'the hall of a sect that no longer exists. We are not allowed to say its name.'),
          gestur='tunjuk', akibat=['lore kota_besar.istana']),
        N('pangeran', ('...Sekali. Waktu Nona Ruyi kecil jatuh ke kolam koi dan keluar membawa ikan paling besar. Jangan bilang aku '
                       'yang cerita.',
                       '...Once. When little Lady Ruyi fell into the koi pond and came out holding the biggest fish. Do not say I told you.'),
          gestur='bisik'),
        N('pedang', ('Kau berubah pikiran? Pedangnya masih kusimpan di barak.', 'You changed your mind? I still keep the sword in the barracks.'),
          gestur='angguk_dengar', pilihan=[
            P(('Ya. Akan kubawa kepada Ou Ye.', 'Yes. I will take it to Ou Ye.'), akibat=[f'quest_mulai {q}', f'bendera {q}_tolak off']),
            P(('Belum.', 'Not yet.'))]),
    ], prioritas=-2, syarat=['acak 60'])
    D('pengawal_penguasa_ruyi', 'peran:pengawal_istana', [
        N('mulai', ('Sejak Nona Ruyi naik takhta, penjaga istana disuruh menyapa rakyat. Aku belum terbiasa. ...Selamat pagi? Siang? '
                    'Pokoknya selamat.',
                    'Since Lady Ruyi took the throne, palace guards are told to greet the people. I am not used to it yet. ...Good '
                    'morning? Afternoon? Good, anyway.'),
          gestur='lambai', pilihan=[P(('Bagus untuk permulaan.', 'A fine start.'))]),
    ], prioritas=4, syarat=['alur_penguasa ruyi', 'acak 40'])
    D('pengawal_penguasa_gu', 'peran:pengawal_istana', [
        N('mulai', ('Menteri Gu, maksudku Pangeran Gu, menambah jam jaga dan gaji kami. Kami lebih kaya dan lebih lelah. Pasar Gelap '
                    'dirazia tiap pekan; aku belum pernah sesibuk ini.',
                    'Minister Gu, I mean Prince Gu, added watches and raised our pay. We are richer and more tired. The Black Market '
                    'is raided every week; I have never been this busy.'),
          gestur='menguap_peregangan', pilihan=[P(('Semoga kau sempat tidur.', 'I hope you get some sleep.'))]),
    ], prioritas=4, syarat=['alur_penguasa gu', 'acak 40'])
    D('pengawal_hormat', 'peran:pengawal_istana', [
        N('mulai', ('(Sang pengawal mengangkat tombaknya tegak dan memberi hormat.) Penyelamat pedang ayah rekan kami. Barak kami selalu punya '
                    'teh untukmu.',
                    '(The guard raises his spear upright and salutes.) The one who saved our comrade\'s father\'s sword. Our barracks always has '
                    'tea for you.'), gestur='hormat', prop='tombak'),
    ], prioritas=6, syarat=['bendera pengawal_sahabat', 'acak 50'])

    # ================================================================== capital: gate guard (day, curfew, wanted)
    D('gerbang_sapa', 'peran:penjaga_gerbang', [
        N('mulai', ('Selamat datang. Senjata boleh dibawa, asal tidur di sarungnya. Hukum pertama kota: di dalam tembok, pedang tidur.',
                    'Welcome. Weapons may come in, so long as they sleep in their sheaths. The city\'s first law: within the walls, '
                    'swords sleep.'),
          gestur='berdiri_jaga', prop='tombak', suara='kotabesar.gate_welcome', pilihan=[
            P(('Kenapa hukumnya begitu?', 'Why that law?'), ke='hukum'),
            P(('Jam berapa gerbang ditutup?', 'When do the gates close?'), ke='jam'),
            P(('Ada kabar dari jalan?', 'Any news from the road?'), ke='kabar'),
            P(('Terima kasih.', 'Thank you.'))]),
        N('hukum', ('Kota ini didirikan tujuh sekte yang lelah berperang. Mereka menanam tujuh pedang patah di alun-alun dan bersumpah. '
                    'Sejak itu, siapa pun yang menghunus pedang di jalan berurusan dengan Hakim Bao. Percayalah, kau tidak mau itu.',
                    'This city was founded by seven sects tired of war. They planted seven broken swords in the square and swore an '
                    'oath. Since then, whoever draws a sword in the street answers to Magistrate Bao. Trust me, you do not want that.'),
          gestur='cerita', akibat=['lore kota_besar.gerbang']),
        N('jam', ('Genderang jam malam berbunyi sesudah gelap, gerbang dibuka lagi saat lonceng pagi. Yang terlambat tidur di Kampung '
                  'Kumuh, atau di sel kalau memanjat tembok.',
                  'The curfew drums sound after dark; the gates open again with the morning bell. Latecomers sleep in the slums, or '
                  'in a cell if they climb the wall.'), gestur='tunjuk'),
        N('kabar', None, kabar=True, gestur='bisik'),
    ], prioritas=-2, syarat=['acak 60', 'jam 6 21'])
    D('gerbang_malam', 'peran:penjaga_gerbang', [
        N('mulai', ('Gerbang sudah ditutup. Jangan pandangi aku begitu; bukan aku yang menabuh genderang. Kalau urusanmu mendesak, '
                    'tunjukkan lencana atau tunggu lonceng pagi.',
                    'The gates are closed. Do not look at me like that; I did not beat the drums. If your business is urgent, show a '
                    'badge or wait for the morning bell.'),
          gestur='sikap_tombak', prop='tombak', suara='kotabesar.curfew', pilihan=[
            P(('Malam yang panjang?', 'A long night?'), ke='panjang'),
            P(('Aku akan menunggu.', 'I will wait.'))]),
        N('panjang', ('Semua malam panjang di atas tembok. Tadi ada burung hantu yang bertengger di tombakku satu jam penuh. Aku tidak '
                      'berani bergerak; katanya burung hantu di tombak itu pertanda baik.',
                      'Every night is long on the wall. An owl perched on my spear for a whole hour tonight. I did not dare move; they '
                      'say an owl on your spear is good luck.'), gestur='tatap_langit'),
    ], prioritas=3, syarat=['jam 22 24'])
    D('gerbang_buronan', 'peran:penjaga_gerbang', [
        N('mulai', ('Kau... wajahmu ada di papan Yamen. Aku tidak mencari masalah dengan kultivator. Pergilah ke Yamen dan bayar '
                    'dendamu, sebelum kaptenku melihatmu.',
                    'You... your face is on the Yamen board. I want no trouble with cultivators. Go to the Yamen and pay your fine '
                    'before my captain sees you.'),
          gestur='kaget', suara='kotabesar.wanted', pilihan=[
            P(('Aku akan ke Yamen.', 'I will go to the Yamen.')),
            P(('(Pergi tanpa bicara)', '(Leave without a word)'))]),
    ], prioritas=25, syarat=['buronan'])

    # ================================================================== capital: Source Stone House cutter
    D('pemotong_sapa', 'peran:pemotong_batu', [
        N('mulai', ('Mau memotong? Pilih batunya, bayar Lao Shi, lalu doakan aku. Tanganku mantap, tapi nasib orang ada di dalam batu.',
                    'Want a cut? Choose your stone, pay Lao Shi, then pray for me. My hands are steady, but a man\'s luck lives inside '
                    'the stone.'),
          gestur='asah_pisau', prop='pahat', pilihan=[
            P(('Bagaimana kau tahu ada giok di dalamnya?', 'How do you know there is jade inside?'), ke='giok'),
            P(('Pernah memotong sesuatu yang aneh?', 'Ever cut something strange?'), ke='aneh'),
            P(('Apa kata orang di sini?', 'What are folk saying here?'), ke='kabar'),
            P(('Nanti saja.', 'Later.'))]),
        N('giok', ('Kulit batu bercerita. Pita lilin tipis berarti giok mungkin dekat; retakan seperti akar berarti air pernah lewat; '
                   'batu yang terlalu ringan biasanya bohong. Tetap saja, separuhnya tebakan. Itulah sebabnya disebut judi.',
                   'The skin tells a story. A thin waxy band means jade may be near; root-like cracks mean water once passed; a stone '
                   'that feels too light usually lies. Still, half of it is guessing. That is why they call it gambling.'),
          gestur='lihat_barang', prop='batu_mentah', akibat=['lore kota_besar.pasar']),
        N('aneh', ('Sekali, batu sebesar kepala kerbau. Isinya kosong, kecuali seekor katak kecil yang masih hidup. Lao Shi memasangnya '
                   'di kolam belakang. Katak itu sekarang tiga puluh tahun umurnya dan tidak pernah bersuara.',
                   'Once, a stone as big as a buffalo\'s head. Empty inside, except for a tiny frog, still alive. Lao Shi put it in the '
                   'back pond. That frog is thirty years old now and has never made a sound.'),
          gestur='cerita'),
        N('kabar', None, kabar=True, gestur='bisik'),
    ], prioritas=-2, syarat=['acak 65'])

    # ================================================================== capital: Thousand Blades Pavilion seller
    D('penjual_senjata_sapa', 'peran:pedagang_senjata', [
        N('mulai', ('Paviliun Seribu Bilah! Jian, dao, tombak, kipas besi. Pegang dulu, rasakan bobotnya. Bilah yang baik terasa seperti '
                    'ingatan lama di telapak tangan.',
                    'The Thousand Blades Pavilion! Jian, dao, spears, iron fans. Hold one first, feel the weight. A good blade feels '
                    'like an old memory in the palm.'),
          gestur='seru_dagang', pilihan=[
            P(('Senjata apa yang cocok untukku?', 'Which weapon suits me?'), ke='saran_rendah', syarat=['!pita 2']),
            P(('Senjata apa yang cocok untukku?', 'Which weapon suits me?'), ke='saran_tinggi', syarat=['pita 2']),
            P(('Pedang karatan di etalase itu apa?', 'What is that rusty sword in the case?'), ke='karat'),
            P(('Hanya melihat-lihat.', 'Just looking.'))]),
        N('saran_rendah', ('Kakimu masih ringan, qi-mu belum dalam. Jian yang ramping untukmu: cepat ditarik, cepat dikembalikan. Pedang '
                           'besar untuk orang yang sudah tahu cara jatuh.',
                           'Your feet are still light, your qi not yet deep. A slender jian for you: quick out, quick back. Great blades '
                           'are for those who already know how to fall.'), gestur='bicara_tangan'),
        N('saran_tinggi', ('Ah, yang seperti Tuan butuh senjata yang tidak patah saat qi-nya dituang penuh. Lihat yang di rak atas; '
                           'yang bermutu roh. Harganya juga tinggi, tapi kepala Tuan lebih mahal.',
                           'Ah, one like you needs a weapon that will not shatter when your qi pours in full. Look at the top rack; '
                           'the spirit-grade ones. The price is high too, but your head is worth more.'), gestur='tunjuk'),
        N('karat', ('Itu "Pemotong Hujan". Kakekku bersumpah pedang itu pernah membelah hujan sehingga pemiliknya tidak basah. Kami '
                    'tidak pernah menjualnya; kami juga tidak pernah mengujinya saat hujan.',
                    'That is "Rain Cutter". My grandfather swore it once split the rain so its owner stayed dry. We never sell it; we '
                    'have also never tested it in the rain.'), gestur='tertawa'),
    ], prioritas=-2, syarat=['acak 55'])

    # ================================================================== capital: guild alchemist
    D('alkemis_sapa', 'peran:alkemis', [
        N('mulai', ('Jangan sentuh tungku itu. Bukan karena panas, tapi karena Tetua Dan Chen menghitung abunya. Ada perlu apa?',
                    'Do not touch that furnace. Not because it is hot, but because Elder Dan Chen counts its ashes. What do you need?'),
          gestur='kipas_tungku', prop='kipas_tungku', pilihan=[
            P(('Bagaimana cara jadi alkemis?', 'How does one become an alchemist?'), ke='jadi'),
            P(('Seperti apa Tetua Dan Chen?', 'What is Elder Dan Chen like?'), ke='dan_chen'),
            P(('Ada nasihat untuk meracik?', 'Any advice for refining?'), ke='nasihat'),
            P(('Tidak ada.', 'Nothing.'))]),
        N('jadi', ('Mulai dari menumbuk herba sampai tanganmu bau tanah selamanya. Lalu ujian Tingkat Pertama: satu pil, satu tungku, '
                   'tanpa ledakan. Kebanyakan gagal di bagian "tanpa ledakan".',
                   'Start by grinding herbs until your hands smell of soil forever. Then the First Tier trial: one pill, one furnace, '
                   'no explosion. Most fail at the "no explosion" part.'), gestur='tumbuk_herba', prop='alu', akibat=['lore gilda.alkemis']),
        N('dan_chen', ('Ia memanggilku "tungku retak" selama dua tahun. Tahun ketiga ia memanggilku namaku. Aku menangis di gudang herba. '
                       'Jangan bilang siapa-siapa.',
                       'He called me "cracked furnace" for two years. In the third year he called me by my name. I cried in the herb store. '
                       'Do not tell anyone.'), gestur='menangis'),
        N('nasihat', ('Api tidak boleh melonjak saat herba ketiga masuk. Turunkan napasmu, bukan apinya; tungku mengikuti napas '
                      'peraciknya. Dan jangan meracik saat marah. Pil ikut marah.',
                      'The fire must not leap when the third herb goes in. Lower your breath, not the flame; a furnace follows its '
                      'refiner\'s breathing. And never refine angry. The pill gets angry too.'), gestur='napas_meditasi'),
    ], prioritas=-2, syarat=['acak 60'])

    # ================================================================== capital: array keeper + kristal_formasi
    q = 'kristal_formasi'
    Q(q, 'peran:penjaga_formasi', 'stasiun_teleport', ('Kristal Formasi', 'The Array Crystal'),
      ('Salah satu dari empat tiang kristal stasiun teleportasi retak. Penjaga formasi butuh kecubung segar sebelum retaknya menjalar.',
       'One of the four crystal pillars of the teleport station has cracked. The array keeper needs fresh amethyst before the crack spreads.'),
      [T('kecubung', ('Bawa delapan keping kecubung kepada penjaga formasi.', 'Bring eight amethyst shards to the array keeper.'),
         tujuan=['antar minecraft:amethyst_shard peran:penjaga_formasi x8']),
       T('pasang', ('Bantu penjaga formasi memasang kristal baru.', 'Help the array keeper set the new crystal.'),
         cabang=[(['bendera formasi_belajar'], 'belajar')], ke='upah'),
       T('belajar', ('Penjaga formasi mengajarimu membaca ukiran trigram.', 'The array keeper teaches you to read the trigram carvings.'),
         tujuan=SEGERA, akibat=['nama_baik 2', 'kabar kristal_formasi_pulih pemain'],
         hasil=('Sambil menyetel kristal, penjaga formasi menunjukkan bagaimana delapan trigram di cakram batu saling menjawab seperti '
                'percakapan. "Formasi bukan sihir," katanya. "Formasi adalah janji yang diukir begitu dalam sampai langit pun malu '
                'mengingkarinya." Kau pulang dengan kepala penuh garis dan arah.',
                'While tuning the crystal, the array keeper shows how the eight trigrams on the stone disc answer one another like a '
                'conversation. "An array is not magic," he says. "An array is a promise carved so deep that even heaven is ashamed '
                'to break it." You go home with a head full of lines and directions.')),
       T('upah', ('Penjaga formasi membayar jasamu.', 'The array keeper pays for your help.'),
         tujuan=SEGERA, akibat=['beri_nb_pita 6', 'kabar kristal_formasi_pulih pemain'],
         hasil=('Kristal baru berdengung, lalu menyala ungu pucat. Penjaga formasi menghitung kepingan batu roh ke telapakmu satu '
                'per satu, lalu menghitungnya lagi. "Formasi dan uang," katanya, "dua-duanya harus tepat."',
                'The new crystal hums, then glows a pale violet. The array keeper counts spirit stone chips into your palm one by one, '
                'then counts them again. "Arrays and money," he says, "both must be exact."'))])
    D('formasi_tawar', 'peran:penjaga_formasi', [
        N('mulai', ('Jangan berdiri di cakram! ...Maaf. Tiang timur laut retak sehalus rambut, dan kalau retaknya menjalar, orang yang '
                    'berangkat bisa tiba dalam dua potong.',
                    'Do not stand on the disc! ...Sorry. The north-east pillar has a hairline crack, and if it spreads, travellers '
                    'might arrive in two pieces.'),
          gestur='kaget', prop='piringan_formasi', pilihan=[
            P(('Apa yang kau butuhkan?', 'What do you need?'), ke='butuh'),
            P(('Semoga cepat diperbaiki.', 'I hope it is fixed soon.'), ke='tolak', akibat=[f'bendera {q}_tolak'])]),
        N('butuh', ('Kecubung segar dari geode, delapan keping. Kristal lama sudah lelah menyimpan jarak. Kau tahu, setiap orang yang '
                    'lewat meninggalkan sedikit dari perjalanannya di dalamnya.',
                    'Fresh amethyst from a geode, eight shards. The old crystal is tired of holding distances. You see, everyone who '
                    'passes leaves a little of their journey inside it.'),
          gestur='bicara_tangan', pilihan=[
            P(('Akan kucarikan.', 'I will find some.'), akibat=[f'quest_mulai {q}', f'bendera {q}_tolak off']),
            P(('Lain kali.', 'Another time.'), akibat=[f'bendera {q}_tolak'])]),
        N('tolak', ('Ya, ya. Semua orang berharap. Kristal tidak makan harapan.', 'Yes, yes. Everyone hopes. Crystals do not eat hope.'),
          gestur='kipas'),
    ], prioritas=20, syarat=[baru(q), f'!bendera {q}_tolak', 'acak 75'])
    D('formasi_pasang', 'peran:penjaga_formasi', [
        N('mulai', ('Bagus, keping-keping ini masih bernyanyi. Pegang ujung sana; jangan bernapas waktu aku menghitung sampai tujuh.',
                    'Good, these shards still sing. Hold that end; do not breathe while I count to seven.'),
          gestur='segel_tangan', prop='piringan_formasi', pilihan=[
            P(('Ajari aku membaca ukirannya.', 'Teach me to read the carvings.'), akibat=['bendera formasi_belajar', f'quest_maju {q}']),
            P(('(Pegang dan diam)', '(Hold it and keep still)'), akibat=[f'quest_maju {q}'])]),
    ], prioritas=45, syarat=[tahap(q, 'pasang')])
    D('formasi_sapa', 'peran:penjaga_formasi', [
        N('mulai', ('Formasi siap. Sebutkan tujuanmu, bayar ongkosnya, dan pejamkan mata. Yang membuka mata biasanya muntah.',
                    'The array is ready. Name your destination, pay the fare and close your eyes. Those who keep them open usually '
                    'throw up.'),
          gestur='segel_tangan', prop='piringan_formasi', pilihan=[
            P(('Bagaimana formasi ini bekerja?', 'How does this array work?'), ke='kerja'),
            P(('Pernah ada yang tersesat?', 'Has anyone ever got lost?'), ke='tersesat'),
            P(('Terima kasih.', 'Thank you.'))]),
        N('kerja', ('Dua cakram yang diukir dengan janji yang sama saling mengenali, sejauh apa pun jaraknya. Kristal menyimpan jarak '
                    'itu, batu roh membayarnya. Tukang yang mengukir cakram pertama di sini bekerja sembilan tahun tanpa salah satu goresan.',
                    'Two discs carved with the same promise recognise each other, however far apart. The crystals hold the distance, '
                    'the spirit stones pay for it. The craftsman who carved the first disc here worked nine years without a single wrong stroke.'),
          gestur='bicara_tangan', akibat=['lore kota_besar.awan']),
        N('tersesat', ('Sekali. Seorang pedagang garam berangkat ke barat dan tiba di atap kuil, tepat di atas lonceng. Biksunya bilang '
                       'itu pertanda; pedagangnya bilang itu tagihan.',
                       'Once. A salt merchant left for the west and arrived on a temple roof, right on top of the bell. The monk said '
                       'it was an omen; the merchant said it was a bill.'), gestur='tertawa'),
    ], prioritas=-2, syarat=['acak 55'])

    # ================================================================== capital: lecturer
    D('penceramah_sapa', 'peran:penceramah_fana', [
        N('mulai', ('Ah, pendengar! Duduklah. Kuliah hari ini: mengapa kota ini tidak punya kaisar. Jawaban singkatnya tujuh. Jawaban '
                    'panjangnya tiga jam.',
                    'Ah, a listener! Sit. Today\'s lecture: why this city has no emperor. The short answer is seven. The long answer '
                    'is three hours.'),
          gestur='mengajar', prop='kitab', pilihan=[
            P(('Jawaban singkat saja.', 'The short answer, please.'), ke='singkat'),
            P(('Ceritakan tentang Distrik Cendekia.', 'Tell me about the Scholars\' District.'), ke='cendekia'),
            P(('Kenapa fana mengajar di kota kultivator?', 'Why does a mortal teach in a cultivators\' city?'), ke='fana'),
            P(('Lain kali.', 'Another time.'))]),
        N('singkat', ('Tujuh sekte, tujuh pedang patah, satu sumpah. Tidak ada sekte yang rela yang lain menjadi kaisar, jadi mereka '
                      'memilih seorang penjaga. Penjaga bisa diganti; kaisar biasanya hanya bisa dibunuh. Itu kemajuan, kalau kau tanya aku.',
                      'Seven sects, seven broken swords, one oath. No sect would let another become emperor, so they chose a warden. '
                      'A warden can be replaced; an emperor usually can only be killed. That is progress, if you ask me.'),
          gestur='bicara_tangan', akibat=['lore kota_besar.pusat']),
        N('cendekia', ('Perpustakaan Agung menyimpan salinan kitab dasar setiap sekte yang ikut bersumpah. Siapa pun boleh membaca '
                       'halaman pertama; tidak ada yang boleh membaca halaman terakhir. Itulah kenetralan: semua tahu awal, tak ada '
                       'yang memonopoli akhir.',
                       'The Grand Library keeps a copy of the basic manual of every sect that swore the oath. Anyone may read the first '
                       'page; nobody may read the last. That is neutrality: all know the beginning, none owns the end.'),
          gestur='membaca', prop='kitab', akibat=['lore kota_besar.cendekia']),
        N('fana', ('Karena kultivator hidup terlalu lama untuk mengingat hal kecil. Harga beras tahun banjir, nama tukang yang membangun '
                   'jembatan, lagu yang dinyanyikan di pemakaman. Fana mengingat hal kecil. Hal kecil itulah sejarah.',
                   'Because cultivators live too long to remember small things. The price of rice in the flood year, the name of the '
                   'builder of the bridge, the song sung at funerals. Mortals remember small things. Small things are history.'),
          gestur='tatap_langit'),
    ], prioritas=-2, syarat=['acak 70'])

    # ================================================================== capital: healer + demam_kumuh
    q = 'demam_kumuh'
    Q(q, 'peran:tabib', 'balai_tabib', ('Demam di Kampung Kumuh', 'Fever in the Slums'),
      ('Demam merah menyebar di Kampung Kumuh. Tabib kehabisan pil penurun panas, dan orang-orang di sana tidak sanggup membayar.',
       'A red fever is spreading in the slums. The healer has run out of fever pills, and the people there cannot pay.'),
      [T('racik', ('Racik pil apa saja di tungku mana pun; tabib akan mencampurnya menjadi obat demam.',
                   'Refine any pill at any furnace; the healer will blend it into fever medicine.'), tujuan=['racik *']),
       T('bawa', ('Bawa obat ke Kampung Kumuh dan bicaralah dengan Si Kecil Dou, yang tahu rumah mana yang sakit.',
                  'Take the medicine to the slums and speak with Little Dou, who knows which houses are sick.'),
         cabang=[(['bendera demam_bayar'], 'jual')], ke='gratis'),
       T('gratis', ('Kau membagikan obat tanpa bayaran.', 'You hand out the medicine for free.'), tujuan=SEGERA,
         akibat=['nama_baik 4', 'bendera demam_dermawan', 'kabar demam_gratis pemain'],
         hasil=('Dou menuntunmu dari gubuk ke gubuk. Di gubuk terakhir seorang nenek menggenggam tanganmu dan tidak mau melepaskannya '
                'sampai ia tertidur. Tiga hari kemudian demamnya reda di seluruh kampung, dan anak-anak kumuh mulai menyebutmu '
                '"Tabib Langit", meski kau bukan tabib.',
                'Dou leads you from hut to hut. In the last one an old woman holds your hand and will not let go until she falls asleep. '
                'Three days later the fever breaks across the slums, and the slum children start calling you "Sky Healer", though '
                'you are no healer.')),
       T('jual', ('Kau menjual obat dengan harga murah.', 'You sell the medicine cheaply.'), tujuan=SEGERA,
         akibat=['beri_nb_pita 4', 'nama_baik -1', 'kabar demam_jual pemain'],
         hasil=('Orang-orang kumuh mengumpulkan keping demi keping. Demamnya reda, tapi Dou tidak lagi berlari menyambutmu. "Obatnya '
                'manjur," katanya pelan. "Cuma mahal untuk kami." Kakek Lumpur hanya menatapmu lama dari tumpukan jerami.',
                'The slum folk scrape together chip after chip. The fever breaks, but Dou no longer runs to greet you. "The medicine '
                'works," he says quietly. "It is just dear for us." Grandpa Mud only watches you a long while from his pile of straw.'))])
    D('demam_tawar', 'peran:tabib', [
        N('mulai', ('Maaf, aku sibuk... Demam merah di Kampung Kumuh. Sebelas orang sejak kemarin, dan pil penurun panasku habis. '
                    'Tungkuku sendiri retak minggu lalu.',
                    'Sorry, I am busy... Red fever in the slums. Eleven people since yesterday, and my fever pills are gone. My own '
                    'furnace cracked last week.'),
          gestur='periksa_nadi', prop='kotak_obat', pilihan=[
            P(('Bagaimana aku bisa membantu?', 'How can I help?'), ke='bantu'),
            P(('Semoga mereka cepat sembuh.', 'I hope they recover soon.'), ke='tolak', akibat=[f'bendera {q}_tolak'])]),
        N('bantu', ('Racik satu pil, apa saja yang bisa kau racik. Intinya yang kubutuhkan, bukan khasiatnya; aku akan mencampurnya '
                    'dengan akar teratai dan daun mint. Lalu bawa ke Kumuh. Si Kecil Dou tahu rumah mana yang sakit.',
                    'Refine one pill, whatever you can refine. I need its core, not its effect; I will blend it with lotus root and '
                    'mint. Then take it to the slums. Little Dou knows which houses are sick.'),
          gestur='racik_herba', prop='herba', pilihan=[
            P(('Akan kuracik.', 'I will refine one.'), akibat=[f'quest_mulai {q}', f'bendera {q}_tolak off']),
            P(('Aku tidak bisa meracik.', 'I cannot refine.'), ke='tolak', akibat=[f'bendera {q}_tolak'])]),
        N('tolak', ('Ya... Terima kasih sudah mendengar. Itu juga obat, kadang-kadang.', 'Yes... Thank you for listening. That is medicine '
                    'too, sometimes.'), gestur='angguk_dengar'),
    ], prioritas=20, syarat=[baru(q), f'!bendera {q}_tolak', 'acak 75'])
    D('demam_dou', 'tokoh:dou', [
        N('mulai', ('Kakak bawa obat?! Nenek Ma batuk sepanjang malam, si kembar panas sekali, dan Paman Tie tidak bangun dari tikar.',
                    'You brought medicine?! Granny Ma coughed all night, the twins are burning up, and Uncle Tie will not get up from '
                    'his mat.'),
          gestur='lari_lambai', pilihan=[
            P(('Bagikan semuanya. Gratis.', 'Hand it all out. Free.'), akibat=['bendera demam_gratis', f'quest_maju {q}']),
            P(('Bayar semampu mereka.', 'They pay what they can.'), akibat=['bendera demam_bayar', f'quest_maju {q}'])]),
    ], prioritas=45, syarat=[tahap(q, 'bawa')])
    D('tabib_sapa', 'peran:tabib', [
        N('mulai', ('Duduk. Julurkan tangan. ...Nadimu kencang seperti tali busur. Kau terlalu banyak bertarung, terlalu sedikit tidur.',
                    'Sit. Hold out your hand. ...Your pulse is taut as a bowstring. You fight too much and sleep too little.'),
          gestur='periksa_nadi', prop='kotak_obat', pilihan=[
            P(('Apa itu Penyimpangan Qi?', 'What is a Qi Deviation?'), ke='penyimpangan'),
            P(('Ranjang kosong di pojok itu kenapa?', 'Why is the corner bed always empty?'), ke='ranjang'),
            P(('Aku baik-baik saja.', 'I am fine.'), ke='baik')]),
        N('penyimpangan', ('Qi yang berlari ke jalan yang salah. Mula-mula kesemutan, lalu panas di dada, lalu mimpi yang bukan '
                           'milikmu. Jangan menerobos ranah saat marah atau lapar. Kalau sudah terjadi, datanglah ke sini sebelum '
                           'mimpinya mulai bicara.',
                           'Qi running down the wrong road. First tingling, then heat in the chest, then dreams that are not yours. '
                           'Never break through angry or hungry. If it happens, come here before the dreams start talking.'),
          gestur='bicara_tangan'),
        N('ranjang', ('Itu ranjang guruku. Ia merawat orang di sana empat puluh tahun dan meninggal di sana dengan tenang. Orang bilang '
                      'ranjang itu sial. Aku membiarkannya kosong karena alasan lain.',
                      'That was my master\'s bed. She tended people there for forty years and died there peacefully. People say the bed '
                      'is unlucky. I leave it empty for another reason.'), gestur='tatap_langit'),
        N('baik', ('Semua orang bilang begitu sampai mereka dibawa ke sini dengan tandu. Minum teh jahe, tidur sebelum tengah malam.',
                   'Everyone says that until they are carried in on a stretcher. Drink ginger tea, sleep before midnight.'), gestur='kipas'),
    ], prioritas=-2, syarat=['acak 60'])
    D('tabib_dermawan', 'peran:tabib', [
        N('mulai', ('Tabib Langit! Ha, anak-anak kumuh yang memberimu nama itu. Kau tahu, mereka sekarang datang ke sini untuk belajar '
                    'membalut luka. Kau menyembuhkan lebih dari demam.',
                    'Sky Healer! Ha, the slum children gave you that name. You know, they come here now to learn to dress wounds. You '
                    'cured more than a fever.'), gestur='tepuk_bahu'),
    ], prioritas=6, syarat=['bendera demam_dermawan', 'acak 40'])

    # ================================================================== builders (fear and plain)
    D('tukang_sapa', 'peran:tukang', [
        N('mulai', ('Awas kepalamu, ada papan lewat! ...Hup! Kalau Tuan mau membantu, tali itu perlu ditarik. Kalau tidak, minggir '
                    'sedikit, ya.',
                    'Mind your head, plank coming through! ...Heave! If you want to help, that rope needs pulling. If not, step aside a '
                    'little, please.'),
          gestur='angkat_papan', prop='papan_kayu', suara='tempat.repair_work', pilihan=[
            P(('Kau membangun apa?', 'What are you building?'), ke='bangun'),
            P(('Kenapa atapnya melengkung begitu?', 'Why do the roofs curve like that?'), ke='atap'),
            P(('Ada kabar?', 'Any news?'), ke='kabar'),
            P(('Silakan lanjut.', 'Carry on.'))]),
        N('bangun', ('Memperbaiki, lebih tepatnya. Ada yang membuat lubang, kami menambalnya. Kalau kultivator berkelahi, tukang makan '
                     'enak sebulan. Jangan bilang aku senang; aku tidak senang. Sedikit.',
                     'Repairing, rather. Someone makes holes, we patch them. When cultivators fight, builders eat well for a month. Do '
                     'not say I am glad; I am not glad. A little.'), gestur='perbaiki_palu', prop='palu'),
        N('atap', ('Supaya roh jahat yang berjalan lurus terpeleset di ujungnya. Kata guruku begitu. Kata guruku yang lain, supaya air '
                   'hujan jatuh jauh dari dinding. Dua-duanya benar, dan dua-duanya membuat atap mahal.',
                   'So evil spirits that walk straight slip off the end. My master said so. My other master said so rain falls far '
                   'from the walls. Both are right, and both make roofs expensive.'), gestur='tunjuk'),
        N('kabar', None, kabar=True, gestur='bisik'),
    ], prioritas=-2, syarat=['acak 55'])
    D('tukang_takut', 'peran:tukang', [
        N('mulai', ('(Tukang itu menjatuhkan palunya dan menunduk dalam-dalam.) T-Tuan... kami sedang bekerja secepatnya, sungguh. '
                    'Tidak ada yang mengeluh. Tidak ada yang menagih. Silakan lewat.',
                    '(The builder drops his hammer and bows low.) M-my lord... we are working as fast as we can, truly. Nobody is '
                    'complaining. Nobody is billing anyone. Please, go ahead.'),
          gestur='berlutut', suara='tempat.village_fear', pilihan=[
            P(('Bangunlah. Aku tidak akan menyakitimu.', 'Get up. I will not hurt you.'), ke='lega'),
            P(('(Pergi)', '(Leave)'))]),
        N('lega', ('...Terima kasih. Maaf. Lutut ini sudah lebih dulu berlutut sebelum kepalaku sempat berpikir.',
                   '...Thank you. Sorry. My knees knelt before my head had time to think.'), gestur='hormat'),
    ], prioritas=8, syarat=['takut 60'])

    # ================================================================== lantern riddle keeper
    D('teka_sapa', 'peran:penjaga_teka', [
        N('mulai', ('Teka-teki, teka-teki! Satu jawaban benar, satu jimat; satu jawaban salah, satu tawa. Mau coba yang ini? Gratis, '
                    'karena belum festival.',
                    'Riddles, riddles! One right answer, one charm; one wrong answer, one laugh. Try this one? Free, since it is not '
                    'festival time yet.'),
          gestur='main_lentera', prop='lentera_tangan', pilihan=[
            P(('Coba.', 'Go on.'), ke='soal'),
            P(('Kapan Festival Lentera?', 'When is the Lantern Festival?'), ke='kapan'),
            P(('Lain kali.', 'Another time.'))]),
        N('soal', ('"Makin banyak kau ambil dariku, makin besar aku jadinya." Apakah aku?',
                   '"The more you take from me, the bigger I become." What am I?'), gestur='kipas', prop='kipas_lipat', pilihan=[
            P(('Lubang.', 'A hole.'), ke='benar'),
            P(('Utang.', 'A debt.'), ke='salah'),
            P(('Perut Gendut Zhu.', 'Fat Zhu\'s belly.'), ke='salah')]),
        N('benar', ('Lubang! Benar! Kau punya kepala yang jernih. Simpan untuk malam festival; teka-tekiku di sana jauh lebih kejam.',
                    'A hole! Right! You have a clear head. Save it for festival night; my riddles there are much crueller.'),
          gestur='tepuk_tangan', akibat=['bendera teka_jernih']),
        N('salah', ('Ha! Bukan. Lubang. Semakin kau gali, semakin besar ia. ...Utang juga, sebenarnya, tapi itu lebih sedih.',
                    'Ha! No. A hole. The more you dig, the bigger it gets. ...A debt too, actually, but that is sadder.'), gestur='tertawa'),
        N('kapan', ('Pada malam bulan purnama yang ditentukan kalender kota. Seluruh Jalan Lentera akan digantungi teka-teki, dan aku '
                    'akan berdiri di sini sampai suaraku habis.',
                    'On the full-moon night the city calendar sets. All of Lantern Street will hang with riddles, and I will stand here '
                    'until my voice gives out.'), gestur='tatap_langit'),
    ], prioritas=-2, syarat=['acak 70'])

    # ================================================================== arena champion (a real bout through the story fallback)
    D('juara_sapa', 'peran:juara_sekte', [
        N('mulai', ('Kau melihat ke arah lei tai seperti orang yang ingin naik. Semua orang begitu pada mulanya. Lalu mereka melihat '
                    'gigiku di lantai, dan berubah pikiran.',
                    'You look at the lei tai like someone who wants to climb it. Everyone does at first. Then they see my teeth on the '
                    'floor, and change their mind.'),
          gestur='sikap_lipat_tangan', pilihan=[
            P(('Gigimu?', 'Your teeth?'), ke='gigi'),
            P(('Bertanding satu ronde?', 'One round?'), ke='tanding', syarat=['pita 1']),
            P(('Ceritakan tentang Arena Agung.', 'Tell me about the Grand Arena.'), ke='arena'),
            P(('Lain kali.', 'Another time.'))]),
        N('gigi', ('Dua gigi depanku. Turnamen pertamaku, lawan pertamaku, pukulan pertamanya. Aku menang ronde itu. Gigiku tidak.',
                   'My two front teeth. My first tournament, my first opponent, his first strike. I won that bout. My teeth did not.'),
          gestur='tertawa'),
        N('tanding', ('Satu ronde, sampai salah satu mundur. Tanpa taruhan, tanpa dendam. Kalau kau menang, aku traktir mi. Kalau kalah, '
                      'kau yang traktir.',
                      'One round, until one of us yields. No stakes, no grudges. If you win, I buy the noodles. If you lose, you do.'),
          gestur='hunus', pilihan=[
            P(('(Naik ke lei tai)', '(Step onto the lei tai)'), akibat=['mulai_duel agresif']),
            P(('Kupikir-pikir lagi.', 'Let me think about it.'))]),
        N('arena', ('Di sinilah sekte-sekte menyelesaikan sengketa: dua jagoan, satu panggung, semua orang menonton. Darah sedikit lebih '
                    'murah daripada perang, dan pedagang kacang lebih kaya.',
                    'This is where sects settle disputes: two champions, one stage, everyone watching. A little blood is cheaper than '
                    'war, and the peanut sellers get richer.'), gestur='sorak_arena', akibat=['lore kota_besar.arena']),
    ], prioritas=-2, syarat=['acak 65'])

    # ================================================================== villages: smith, elder, farmer, fisher
    D('pandai_desa_sapa', 'peran:pandai_desa', [
        N('mulai', ('Panasnya pas, besinya merah ceri. Jangan berdiri di situ, percikannya suka melompat ke celana orang asing.',
                    'The heat is right, the iron cherry-red. Do not stand there; the sparks like to jump onto strangers\' trousers.'),
          gestur='palu_landasan', prop='palu', pilihan=[
            P(('Benarkah kau membuat cangkul pemotong batu?', 'Is it true you made a hoe that cuts stone?'), ke='cangkul'),
            P(('Kau menempa pedang juga?', 'Do you forge swords too?'), ke='pedang'),
            P(('Silakan lanjut.', 'Carry on.'))]),
        N('cangkul', ('Ah, itu. Seorang kultivator menginap di sini tahun lalu dan menitipkan sepotong besi dingin sebagai bayaran. '
                      'Kucampur ke cangkul. Sekarang cangkulnya membelah batu dan petaninya membelah kakinya sendiri. Dua kali.',
                      'Ah, that. A cultivator stayed here last year and left a piece of cold iron as payment. I mixed it into a hoe. Now '
                      'the hoe splits stone and the farmer split his own foot. Twice.'), gestur='tertawa'),
        N('pedang', ('Pedang? Pedang untuk orang yang mau bertarung. Cangkul untuk orang yang mau makan. Di desa ini, lebih banyak yang '
                     'mau makan. Tapi kalau sekte datang memesan, ya, aku bisa. Tanganku ingat.',
                     'Swords? Swords are for people who want to fight. Hoes are for people who want to eat. In this village more want '
                     'to eat. But if a sect orders, yes, I can. My hands remember.'), gestur='lihat_barang', akibat=['lore kampung']),
    ], prioritas=-2, syarat=['acak 60'])
    D('tetua_desa_sapa', 'peran:tetua_desa', [
        N('mulai', ('Duduklah di bangku ini, Nak. Bangku ini lebih tua dari kepala desa, dan lebih bijak juga, karena tidak pernah bicara.',
                    'Sit on this bench, child. This bench is older than the village head, and wiser too, because it never talks.'),
          gestur='duduk_kursi', suara='tempat.village_elder', pilihan=[
            P(('Ceritakan kisah desa ini.', 'Tell me this village\'s story.'), ke='kisah'),
            P(('Apa kau pernah bertemu kultivator hebat?', 'Have you ever met a great cultivator?'), ke='kultivator'),
            P(('Apa nasihatmu untukku?', 'What is your advice for me?'), ke='nasihat'),
            P(('Sampai jumpa, Kek.', 'Farewell, grandfather.'))]),
        N('kisah', ('Waktu aku seumur cucuku, sungai naik sampai ke lumbung. Kami semua naik ke bukit kuil Dewa Tanah, dan airnya '
                    'berhenti tepat di anak tangga ketiga. Sejak itu setiap panen pertama kami persembahkan di anak tangga itu.',
                    'When I was my grandson\'s age, the river rose to the granary. We all climbed to the Earth God\'s shrine on the hill, '
                    'and the water stopped right at the third step. Since then we offer every first harvest on that step.'),
          gestur='cerita', akibat=['lore kampung']),
        N('kultivator', ('Sekali. Seorang pemuda kurus yang meminta air di sumur kami, enam puluh tahun lalu. Sepuluh tahun kemudian ia '
                         'lewat lagi, terbang di atas pedang. Ia melambai. Aku masih belum tahu namanya.',
                         'Once. A thin young man who asked for water at our well, sixty years ago. Ten years later he passed again, '
                         'flying on a sword. He waved. I still do not know his name.'), gestur='tatap_langit'),
        N('nasihat', ('Jangan lupa makan. Orang-orang kuat yang kukenal semuanya lupa makan, lalu lupa pulang, lalu lupa siapa yang '
                      'menunggu mereka. Mulai dari makan.',
                      'Do not forget to eat. Every strong person I knew forgot to eat, then forgot to come home, then forgot who was '
                      'waiting for them. Start with eating.'), gestur='tepuk_bahu'),
    ], prioritas=-2, syarat=['acak 65'])
    D('petani_sapa', 'peran:petani', [
        N('mulai', ('Hari baik, Tuan. Tanahnya lembap, cacingnya gemuk. Itu tanda panen bagus, kalau belalang tidak ikut senang.',
                    'Good day, sir. The soil is moist, the worms are fat. A sign of a good harvest, if the locusts are not pleased too.'),
          gestur='mencangkul', prop='cangkul', pilihan=[
            P(('Apa yang kau tanam?', 'What do you grow?'), ke='tanam'),
            P(('Sulitkah hidup di dekat sekte?', 'Is it hard living near a sect?'), ke='sekte'),
            P(('Ada kabar?', 'Any news?'), ke='kabar'),
            P(('Semoga panennya melimpah.', 'May your harvest be rich.'))]),
        N('tanam', ('Padi di sawah bawah, ubi di lereng, dan sepetak kecil herba roh di belakang rumah yang tidak boleh diketahui '
                    'pemungut pajak. ...Tuan tidak mendengar bagian terakhir.',
                    'Rice in the lower paddies, yams on the slope, and a little patch of spirit herbs behind the house the tax collector '
                    'must not know about. ...You did not hear that last part.'), gestur='bisik'),
        N('sekte', ('Ada untungnya: binatang buas takut mendekat. Ada ruginya: kadang langit meledak di atas gunung dan ayam-ayam kami '
                    'berhenti bertelur seminggu.',
                    'There are gains: wild beasts fear to come near. There are losses: sometimes the sky explodes over the mountain and '
                    'our hens stop laying for a week.'), gestur='tunjuk'),
        N('kabar', None, kabar=True, gestur='bisik'),
    ], prioritas=-2, syarat=['acak 50'])
    D('nelayan_sapa', 'peran:nelayan', [
        N('mulai', ('Ssst, jangan keras-keras. Ikan di sini punya telinga, dan mereka tidak suka kultivator. Terlalu banyak yang '
                    'memancing dengan petir.',
                    'Shh, not so loud. The fish here have ears, and they do not like cultivators. Too many fish with lightning.'),
          gestur='memancing', prop='joran', pilihan=[
            P(('Pernah menangkap ikan roh?', 'Ever caught a spirit fish?'), ke='ikan_roh'),
            P(('Cuaca besok?', 'Weather tomorrow?'), ke='cuaca'),
            P(('Maaf mengganggu.', 'Sorry to disturb you.'))]),
        N('ikan_roh', ('Sekali, ikan mas bersisik emas sebesar lengan. Ia menatapku dan aku menatapnya. Lalu aku melepaskannya. Istriku '
                       'masih marah sampai sekarang, tapi sejak itu jalaku tidak pernah kosong.',
                       'Once, a gold-scaled carp as long as my arm. It looked at me and I looked at it. Then I let it go. My wife is still '
                       'angry, but since then my net has never come up empty.'), gestur='cerita'),
        N('cuaca', ('Bangau terbang rendah, semut naik ke tiang. Hujan sebelum siang. Nelayan tidak butuh menara pengamat; kami punya '
                    'bangau.',
                    'Herons flying low, ants climbing the posts. Rain before noon. Fishermen need no watch tower; we have herons.'),
          gestur='tatap_langit'),
    ], prioritas=-2, syarat=['acak 50'])

    # ================================================================== clan villages: elder and youth
    D('tetua_klan_sapa', 'peran:tetua_klan', [
        N('mulai', ('Kau berdiri di halaman leluhur klan kami. Sopanlah. Para leluhur suka menonton, dan mereka sangat pendendam.',
                    'You stand in our clan\'s ancestral yard. Be polite. The ancestors like to watch, and they hold grudges.'),
          gestur='sikap_tua', pilihan=[
            P(('Ceritakan sejarah klan ini.', 'Tell me this clan\'s history.'), ke='sejarah'),
            P(('Apa pendapatmu tentang Tuan Muda?', 'What do you think of the young master?'), ke='tuan_muda'),
            P(('Maaf mengganggu.', 'Sorry to intrude.'))]),
        N('sejarah', ('Leluhur pertama kami adalah pemburu yang menyelamatkan seorang tetua sekte dari harimau roh. Sebagai balasan ia '
                      'diberi satu jurus dan satu nasihat. Jurusnya kami wariskan; nasihatnya kami lupakan, dan itulah sebabnya kami '
                      'masih klan kecil.',
                      'Our first ancestor was a hunter who saved a sect elder from a spirit tiger. In return he was given one technique '
                      'and one piece of advice. The technique we passed down; the advice we forgot, which is why we are still a small clan.'),
          gestur='cerita', akibat=['lore kampung.klan']),
        N('tuan_muda', ('Bakat seperti air bah, kesabaran seperti ember bocor. Ayahnya terlalu memanjakannya. Mungkin ia butuh kalah '
                        'sekali dari orang yang tepat.',
                        'Talent like a flood, patience like a leaking bucket. His father spoils him. Perhaps he needs to lose once to the '
                        'right person.'), gestur='kipas'),
    ], prioritas=-2, syarat=['acak 60', '!bendera klan_hormat', '!bendera klan_meremehkan'])
    D('tetua_klan_hormat', 'peran:tetua_klan', [
        N('mulai', ('Orang yang membuat Tuan Muda mencium debu! Sejak hari itu ia berlatih sebelum fajar dan berhenti membentak pelayan. '
                    'Leluhur pasti tertawa di aula mereka.',
                    'The one who made the young master kiss the dust! Since that day he trains before dawn and stopped shouting at '
                    'servants. The ancestors must be laughing in their hall.'), gestur='tertawa'),
    ], prioritas=-2, syarat=['bendera klan_hormat', 'acak 60'])
    D('tetua_klan_remeh', 'peran:tetua_klan', [
        N('mulai', ('Oh, yang mengalah. Tidak apa-apa; air yang mengalah pun akhirnya melubangi batu. Tapi di klan ini, orang menilai '
                    'dari siapa yang berdiri, bukan siapa yang bijak.',
                    'Oh, the one who yielded. No matter; yielding water also wears through stone in the end. But in this clan, people '
                    'judge by who stands, not who is wise.'), gestur='sikap_tua'),
    ], prioritas=-2, syarat=['bendera klan_meremehkan', 'acak 60'])
    D('murid_klan_sapa', 'peran:murid_klan', [
        N('mulai', ('Kau orang luar, kan? Jangan lewat halaman latihan kalau Tuan Muda sedang di sana. Ia suka "menguji" tamu. Ujiannya '
                    'biasanya berakhir dengan tamu di kolam.',
                    'You are an outsider, right? Do not cross the training yard when the young master is there. He likes to "test" '
                    'guests. The test usually ends with the guest in the pond.'),
          gestur='latihan_tinju', pilihan=[
            P(('Kau berlatih apa?', 'What are you training?'), ke='latih'),
            P(('Bagaimana hidup di klan?', 'What is clan life like?'), ke='hidup'),
            P(('Terima kasih atas peringatannya.', 'Thanks for the warning.'))]),
        N('latih', ('Tinju Harimau Penjaga Lumbung. Namanya gagah, tapi jurus pertamanya cuma berdiri seperti kuda selama sebatang dupa. '
                    'Aku sudah tiga tahun di jurus pertama.',
                    'Granary Guardian Tiger Fist. A grand name, but its first form is just standing in a horse stance for one stick of '
                    'incense. I have been on the first form for three years.'), gestur='tiang_berdiri'),
        N('hidup', ('Bangun sebelum ayam, latihan, sarapan, latihan, kerja ladang, latihan. Kalau beruntung, dipilih ikut ujian sekte. '
                    'Kalau tidak, menikah dan mengajari anak berdiri seperti kuda.',
                    'Up before the rooster, train, breakfast, train, field work, train. If lucky, chosen for a sect trial. If not, marry '
                    'and teach your children to stand like a horse.'), gestur='bicara_tangan'),
    ], prioritas=-2, syarat=['acak 55'])

    # ================================================================== sect compound: servant
    D('pelayan_sekte_sapa', 'peran:pelayan_sekte', [
        N('mulai', ('Tamu? Silakan, silakan. Tehnya baru diseduh, dan jangan duduk di bantal biru; itu bantal Tetua Ketiga dan ia bisa '
                    'mencium bekas pantat orang.',
                    'A guest? Please, please. The tea is freshly brewed, and do not sit on the blue cushion; it belongs to the Third '
                    'Elder and he can smell where people have sat.'),
          gestur='tuang_teh', prop='teko', pilihan=[
            P(('Sudah lama melayani sekte ini?', 'Have you served this sect long?'), ke='lama'),
            P(('Apa yang dibicarakan para murid?', 'What do the disciples talk about?'), ke='kabar'),
            P(('Ceritakan tentang pendiri sekte ini.', 'Tell me about this sect\'s founder.'), ke='pendiri'),
            P(('Terima kasih tehnya.', 'Thanks for the tea.'))]),
        N('lama', ('Dua puluh tahun. Aku melihat murid-murid datang sebagai bocah ingusan dan pergi sebagai tetua. Mereka semua lupa '
                   'namaku. Tidak apa-apa; aku ingat nama mereka, dan teh kesukaan mereka.',
                   'Twenty years. I have watched disciples arrive as snotty children and leave as elders. They all forget my name. That '
                   'is fine; I remember theirs, and their favourite tea.'), gestur='seduh_teh', prop='cangkir'),
        N('kabar', None, kabar=True, gestur='bisik'),
        N('pendiri', ('Lukisan di aula utama itu dia. Para murid bilang ia membelah gunung dengan satu tebasan. Aku yang membersihkan '
                      'lukisannya tiap bulan; menurutku ia terlihat seperti orang yang suka tidur siang.',
                      'That painting in the main hall is the founder. The disciples say he split a mountain with one stroke. I dust the '
                      'painting every month; to me he looks like someone who liked afternoon naps.'),
          gestur='tertawa', akibat=['lore kompleks']),
    ], prioritas=-2, syarat=['acak 60'])

    # ================================================================== porters and servants (capital and docks)
    D('kuli_sapa', 'peran:kuli', [
        N('mulai', ('Hup! ...Satu karung beras roh, empat puluh jin. Kalau Tuan kultivator mau mengangkat satu dengan jari kelingking, '
                    'jangan di depan mandorku. Ia nanti minta semua kuli bisa begitu.',
                    'Heave! ...One sack of spirit rice, forty jin. If you cultivators want to lift one with your little finger, not in '
                    'front of my foreman. He will expect every porter to do it.'),
          gestur='jalan_pikul', prop='pikulan', pilihan=[
            P(('Berat pekerjaanmu?', 'Is your work hard?'), ke='berat'),
            P(('Ada kabar dari dermaga?', 'News from the docks?'), ke='kabar'),
            P(('Semangat.', 'Keep it up.'))]),
        N('berat', ('Bahu tahu jawabannya. Tapi lihat, semua barang yang masuk kota ini lewat bahu kami. Pil para tetua, sutra para '
                    'menteri, bahkan pedang Paviliun Seribu Bilah. Kami mengangkat kota ini, satu karung sekali jalan.',
                    'My shoulders know the answer. But look, everything that enters this city passes over our shoulders. The elders\' '
                    'pills, the ministers\' silk, even the Thousand Blades\' swords. We carry this city, one sack at a time.'),
          gestur='angkat_bawa'),
        N('kabar', None, kabar=True, gestur='bisik'),
    ], prioritas=-2, syarat=['acak 45'])
    D('pelayan_sapa', 'peran:pelayan', [
        N('mulai', ('Silakan duduk! Mau teh hijau, teh bunga, atau teh "kata Bibi Hong ini yang paling enak"? Yang terakhir sama dengan '
                    'yang pertama, tapi lebih mahal.',
                    'Please sit! Green tea, flower tea, or "Bibi Hong says this one is best" tea? The last is the same as the first, '
                    'but dearer.'),
          gestur='jalan_bawa', prop='nampan', pilihan=[
            P(('Apa yang paling laris di sini?', 'What sells best here?'), ke='laris'),
            P(('Dengar kabar apa dari para tamu?', 'What news have you heard from guests?'), ke='kabar'),
            P(('Teh hijau saja.', 'Green tea, please.'))]),
        N('laris', ('Bakpao isi daging di pagi hari, mi tarik di siang hari, dan gosip di sepanjang hari. Yang terakhir gratis, tapi '
                    'paling cepat habis.',
                    'Meat buns in the morning, pulled noodles at noon, and gossip all day. The last is free, but sells out fastest.'),
          gestur='tertawa'),
        N('kabar', None, kabar=True, gestur='bisik'),
    ], prioritas=-2, syarat=['acak 45'])

    # rumours of the new chains, spread by the closing stages by id (no quest_<id> trigger, so a branch never gets the other's rumour)
    K('quest', 'pedang_ayah_lama', 'pedang_ayah_lama',
      ('Pengawal istana itu memakai pedang ayahnya lagi, dengan bekas patah perak di tengahnya. Katanya %s yang membawanya ke Ou Ye.',
       'That palace guard wears his father\'s sword again, with a silver scar across it. They say %s took it to Ou Ye.'), bobot=10, umur=6)
    K('quest', 'pedang_ayah_baru', 'pedang_ayah_baru',
      ('Ou Ye menempa ulang pedang tua milik keluarga pengawal istana. Nama ayahnya kini terukir di bilahnya. Semua berkat %s.',
       'Ou Ye reforged an old sword for a palace guard\'s family. His father\'s name is now carved on the blade. All thanks to %s.'),
      bobot=10, umur=6)
    K('quest', 'kristal_formasi_pulih', 'kristal_formasi_pulih',
      ('Stasiun teleportasi menyala ungu lagi. %s membawakan kecubung untuk penjaga formasi; sekarang tak ada lagi yang tiba dalam dua potong.',
       'The teleport station glows violet again. %s brought amethyst for the array keeper; nobody arrives in two pieces any more.'),
      bobot=10, umur=6)
    K('quest', 'demam_gratis', 'demam_gratis',
      ('Demam merah di Kampung Kumuh sudah reda. Anak-anak di sana memanggil %s "Tabib Langit", dan tak ada yang membayar sepeser pun.',
       'The red fever in the slums has broken. The children there call %s the "Sky Healer", and nobody paid a single chip.'),
      bobot=10, umur=7)
    K('quest', 'demam_jual', 'demam_jual',
      ('Obat demam untuk Kampung Kumuh akhirnya datang, dari %s. Murah, katanya. Orang kumuh bilang murah itu kata orang kaya.',
       'Fever medicine for the slums finally came, from %s. Cheap, they say. The slum folk say "cheap" is a rich man\'s word.'),
      bobot=10, umur=7)
