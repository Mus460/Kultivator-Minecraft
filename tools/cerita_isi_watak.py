"""38ST content: everyday talk of generated characters (DESIGN 38.S.2, last paragraph). Village heads, clan patriarchs,
young masters, hermits and sect elders carry one of eight temperaments (Tokoh.WATAK); each temperament has its own
greeting and its own answer to the role's topic, the place lore and the rumour choice are shared. Priority -1, so a
recurring character's own dialog (0) and every quest dialog (higher) always win."""

WATAK = ['ramah', 'curiga', 'serakah', 'bijak', 'penakut', 'sombong', 'jujur', 'licik']

# role -> (topic choice, lore choice, lore answer, voice, idle gesture, prop, {watak: (greeting, topic answer, gesture)})
PERAN = {
    'kepala_desa': (
        ('Bagaimana kehidupan di desa ini?', 'How is life in this village?'),
        ('Siapa tetangga kalian di gunung?', 'Who are your neighbours in the mountains?'),
        ('Tiga generasi kami menjual beras dan kayu bakar kepada %s. Kalau mereka sedang baik hati, murid-murid mereka mengusir '
         'binatang dari ladang kami. Kalau sedang tidak, kami menyalakan dupa dan berharap.',
         'For three generations we have sold rice and firewood to %s. When they are kind, their disciples drive the beasts from '
         'our fields. When they are not, we light incense and hope.'),
        'tempat.village_elder', 'sikap_tua', 'tongkat', {
            'ramah': (('Selamat datang, selamat datang! Desa kami kecil, tapi tehnya hangat dan nasinya tidak pernah dingin.',
                       'Welcome, welcome! Our village is small, but the tea is warm and the rice is never cold.'),
                      ('Panen tahun ini cukup, anak-anak sehat, dan sumur belum pernah kering. Langit masih menyayangi kami.',
                       'This year\'s harvest is enough, the children are healthy, and the well has never run dry. Heaven still loves us.'),
                      'lambai'),
            'curiga': (('Orang luar. Hm. Sebutkan urusanmu, dan jangan lama-lama di dekat lumbung.',
                        'An outsider. Hm. State your business, and do not linger near the granary.'),
                       ('Baik-baik saja sampai orang asing datang. Tahun lalu ada "kultivator" yang membawa pergi tiga ekor kerbau.',
                        'Fine until strangers come. Last year a "cultivator" walked off with three of our buffalo.'),
                       'sikap_lipat_tangan'),
            'serakah': (('Tamu! Tamu yang membawa batu roh, semoga? Penginapan kami murah. Hampir murah.',
                         'A guest! A guest carrying spirit stones, I hope? Our lodging is cheap. Almost cheap.'),
                        ('Hidup di sini mahal, Tuan. Garam mahal, besi mahal, dan pajak sekte tidak pernah turun. Sumbangan selalu diterima.',
                         'Life here is costly, sir. Salt is dear, iron is dear, and the sect\'s levy never falls. Donations are always welcome.'),
                        'hitung_koin_gelap'),
            'bijak': (('Duduklah, pengembara. Jalan yang panjang paling baik dibicarakan sambil duduk.',
                       'Sit, traveller. A long road is best talked about sitting down.'),
                      ('Desa ini seperti padi: kalau terlalu tinggi, ia patah oleh angin. Kami tumbuh rendah dan berbuah banyak.',
                       'This village is like rice: grow too tall and the wind breaks you. We grow low and bear much grain.'),
                      'cerita'),
            'penakut': (('O-oh! Tamu. Tamu kultivator. Kami tidak berbuat salah apa pun, sungguh!',
                         'O-oh! A guest. A cultivator guest. We have done nothing wrong, truly!'),
                        ('Tenang. Tenang sekali. Kecuali malam-malam saat serigala menyanyi. Dan saat sekte lewat. Dan saat hujan petir.',
                         'Quiet. Very quiet. Except on nights when the wolves sing. And when a sect passes. And when it storms.'),
                        'takut_sembunyi'),
            'sombong': (('Kau sedang berdiri di desa tertua di lembah ini. Lepaskan sepatumu kalau masuk balai.',
                         'You are standing in the oldest village in this valley. Take off your shoes when you enter the hall.'),
                        ('Desa lain meniru cara kami menanam. Tidak ada yang meniru dengan baik.',
                         'Other villages copy the way we plant. None of them copy it well.'),
                        'sikap_angkuh'),
            'jujur': (('Salam. Aku tidak punya banyak untuk ditawarkan, tapi apa yang ada, kau boleh lihat.',
                       'Greetings. I have little to offer, but what there is, you may see.'),
                      ('Sulit di musim kering, mudah di musim hujan. Kami tidak kaya, tapi kami tidak berutang pada siapa pun.',
                       'Hard in the dry season, easy in the rains. We are not rich, but we owe no one.'),
                      'hormat'),
            'licik': (('Ah, tamu terhormat! Pasti kau lelah. Mau kutunjukkan penginapan sepupuku? Diskon untuk teman.',
                       'Ah, honoured guest! You must be tired. Shall I show you my cousin\'s inn? A discount for friends.'),
                      ('Kehidupan? Lancar, lancar. Terutama bagi yang tahu kepada siapa harus tersenyum.',
                       'Life? Smooth, smooth. Especially for those who know whom to smile at.'),
                      'tawar'),
        }),
    'patriark_klan': (
        ('Ceritakan tentang klanmu.', 'Tell me about your clan.'),
        ('Apa arti aula leluhur bagi kalian?', 'What does the ancestral hall mean to you?'),
        ('Setiap papan nama di aula itu adalah seseorang yang memilih klan di atas dirinya sendiri. Kami membersihkannya setiap '
         'pagi, supaya tidak ada yang lupa harga dari nama keluarga. %s pernah mencoba membeli tanah ini; papan-papan itulah '
         'jawaban kami.',
         'Every tablet in that hall is someone who chose the clan over themselves. We clean them every morning so no one forgets '
         'the price of a family name. %s once tried to buy this land; those tablets were our answer.'),
        'tempat.clan_patriarch', 'sikap_tangan_belakang', 'tongkat', {
            'ramah': (('Tamu di tanah klan adalah tamu leluhur. Masuklah, minum teh bersama kami.',
                       'A guest on clan land is a guest of the ancestors. Come in, drink tea with us.'),
                      ('Kami keluarga besar: tukang besi, petani, dan sedikit kultivator. Yang kuat melindungi yang lemah, yang lemah '
                       'memberi makan yang kuat.',
                       'We are a large family: smiths, farmers and a few cultivators. The strong protect the weak; the weak feed the strong.'),
                      'hormat_dalam'),
            'curiga': (('Ada urusan apa di tanah klanku? Jawab dengan jelas; mata-mata bicara berbelit.',
                        'What business brings you to my clan\'s land? Answer plainly; spies speak in circles.'),
                       ('Klan ini bertahan dua ratus tahun karena kami tidak mudah percaya. Jangan tersinggung.',
                        'This clan has lasted two hundred years because we do not trust easily. Do not be offended.'),
                       'sikap_lipat_tangan'),
            'serakah': (('Orang luar yang kuat. Kekuatan selalu punya harga di tanah ini, dan harga selalu bisa dirundingkan.',
                         'A strong outsider. Strength always has a price on this land, and prices can always be negotiated.'),
                        ('Tambang kecil di bukit itu milik kami. Hasilnya cukup... untuk saat ini. Kalau kau tahu tambang lain, kabari aku.',
                         'The small mine on that hill is ours. It yields enough... for now. If you know of another mine, tell me.'),
                        'hitung_sempoa'),
            'bijak': (('Langkahmu tenang. Orang yang melangkah tenang di tanah orang lain biasanya membawa niat baik.',
                       'Your steps are calm. Those who walk calmly on another\'s land usually carry good intent.'),
                      ('Klan bukan darah. Klan adalah meja yang sama setiap malam. Darah hanya alasan untuk duduk di sana.',
                       'A clan is not blood. A clan is the same table every night. Blood is only the reason to sit at it.'),
                      'cerita'),
            'penakut': (('K-kau dari sekte mana? Kami membayar upeti tepat waktu, tanyakan saja pada mereka!',
                         'W-which sect are you from? We pay our tribute on time, ask them yourself!'),
                        ('Klan kami kecil dan tidak mencari masalah. Kalau ada masalah datang, kami biasanya... menutup pintu.',
                         'Our clan is small and seeks no trouble. When trouble comes, we usually... close the doors.'),
                        'kaget'),
            'sombong': (('Kau berdiri di tanah Klan yang melahirkan tiga tetua Inti Emas. Tundukkan kepalamu sedikit.',
                         'You stand on the land of a clan that bore three Golden Core elders. Lower your head a little.'),
                        ('Leluhur kami menebas raja serigala dengan satu tebasan. Lukisannya ada di aula; silakan iri.',
                         'Our ancestor slew a wolf king with a single stroke. The painting hangs in the hall; feel free to envy it.'),
                        'sikap_angkuh'),
            'jujur': (('Salam. Klan kami sederhana, dan aku bicara apa adanya. Apa yang kau cari?',
                       'Greetings. Our clan is simple, and I say things as they are. What do you seek?'),
                      ('Kami pernah besar, lalu kecil, sekarang sedang tumbuh lagi. Seperti bambu setelah dipotong.',
                       'We were once great, then small, and now we are growing again. Like bamboo after the cut.'),
                      'angguk_dengar'),
            'licik': (('Tamu! Kebetulan sekali. Klan kami sedang mencari sahabat yang kuat. Sahabat, bukan pelayan, tentu.',
                       'A guest! What luck. Our clan is looking for strong friends. Friends, not servants, of course.'),
                      ('Klan ini selalu berpihak pada yang menang. Kebetulan, kami selalu tahu siapa yang akan menang.',
                       'This clan always sides with the winner. As it happens, we always know who will win.'),
                      'kipas'),
        }),
    'tuan_muda': (
        ('Apa yang kau latih?', 'What do you train in?'),
        ('Bagaimana rasanya menjadi pewaris klan?', 'What is it like to be the clan\'s heir?'),
        ('Semua orang menatapku seperti menatap lumbung: berharap isinya penuh. Kalau aku jatuh, yang jatuh nama ayahku. Kau '
         'kira sombong itu mudah? Sombong itu baju zirah.',
         'Everyone looks at me the way they look at a granary: hoping it is full. If I fall, my father\'s name falls. You think '
         'arrogance is easy? Arrogance is armour.'),
        '', 'sikap_berkacak', 'pedang_kayu', {
            'ramah': (('Oh, tamu! Maaf soal penjaga gerbang; mereka terlalu serius. Mau berlatih bersama?',
                       'Oh, a guest! Sorry about the gate guards; they are too serious. Want to train together?'),
                      ('Pedang klan, tiga puluh enam jurus. Aku baru menguasai dua puluh. Jangan bilang ayahku aku bilang "baru".',
                       'The clan sword, thirty-six forms. I have only mastered twenty. Do not tell my father I said "only".'),
                      'latihan_pedang_1'),
            'curiga': (('Kau mengawasiku dari tadi. Kenapa? Siapa yang menyuruhmu?', 'You have been watching me. Why? Who sent you?'),
                       ('Kenapa kau mau tahu? ...Tombak. Puas?', 'Why do you want to know? ...The spear. Satisfied?'),
                       'sikap_tombak'),
            'serakah': (('Kau punya pedang yang bagus. Berapa harganya? Semua ada harganya.',
                         'You have a fine sword. How much? Everything has a price.'),
                        ('Apa pun yang menghasilkan pil gratis dari ayahku. Saat ini, pedang.',
                         'Whatever earns me free pills from my father. At the moment, the sword.'),
                        'lihat_barang'),
            'bijak': (('Kau berjalan seperti orang yang pernah kalah dan belajar dari situ. Aku iri.',
                       'You walk like someone who has lost and learned from it. I envy that.'),
                      ('Kesabaran. Pedangnya hanya alasan. Guruku bilang, pedang yang tergesa memotong pemiliknya.',
                       'Patience. The sword is only the excuse. My teacher says a hurried sword cuts its owner.'),
                      'tai_chi_pagi'),
            'penakut': (('Ja-jangan bilang ayah kau melihatku bersembunyi di gudang.', 'D-do not tell father you saw me hiding in the barn.'),
                        ('Pedang. Tapi aku lebih suka menulis puisi. Puisi tidak berdarah.',
                         'The sword. But I would rather write poems. Poems do not bleed.'),
                        'takut_sembunyi'),
            'sombong': (('Tahu dirilah. Kau sedang berbicara dengan pewaris klan ini.',
                         'Know your place. You are speaking to the heir of this clan.'),
                        ('Semua yang ada. Dan aku sudah menguasai semuanya. Hampir.',
                         'Everything there is. And I have mastered all of it. Nearly.'),
                        'sikap_angkuh'),
            'jujur': (('Salam. Aku masih belajar memimpin, jadi kalau ada yang tidak sopan di desa ini, katakan padaku.',
                       'Greetings. I am still learning to lead, so if anyone in this village is rude, tell me.'),
                      ('Tinju. Ayah ingin pedang, tapi tanganku lebih jujur tanpa besi.',
                       'The fist. Father wants the sword, but my hands are more honest without steel.'),
                      'latihan_tinju'),
            'licik': (('Kau kuat, ya? Bagaimana kalau kita bertaruh? Aku yakin aku menang. Kecuali kalau tidak.',
                       'You are strong, yes? How about a wager? I am sure I will win. Unless I do not.'),
                      ('Pedang, dan sedikit racun di gagangnya. Bercanda! ...Mungkin.',
                       'The sword, and a little poison on the hilt. Joking! ...Probably.'),
                      'tertawa'),
        }),
    'pertapa': (
        ('Apa yang kau cari di sini?', 'What do you seek here?'),
        ('Gua ini dulu milik siapa?', 'Whose cave was this before?'),
        ('Sebelum aku, seorang wanita tua yang mengajar burung-burung bernyanyi. Sebelum dia, seekor beruang. Sebelum beruang, '
         'air. Gua tidak pernah dimiliki; gua hanya dipinjamkan.',
         'Before me, an old woman who taught the birds to sing. Before her, a bear. Before the bear, water. A cave is never owned; '
         'it is only lent.'),
        'tempat.hermit', 'napas_meditasi', '', {
            'ramah': (('Ah, tamu. Air di sini segar; minumlah sebelum bicara.', 'Ah, a visitor. The water here is fresh; drink before you speak.'),
                      ('Suara yang tidak berisik. Di kota semua orang berbicara bersamaan.', 'A voice that is not noise. In town everyone speaks at once.'),
                      'tuang_teh'),
            'curiga': (('Siapa yang memberitahumu jalan ke gua ini?', 'Who told you the way to this cave?'),
                       ('Ketenangan. Dan kau sedang mengganggunya.', 'Stillness. And you are disturbing it.'),
                       'sikap_lipat_tangan'),
            'serakah': (('Kau membawa sesuatu yang manis? Pertapa juga punya lidah.', 'Did you bring anything sweet? Hermits have tongues too.'),
                        ('Kebebasan dari keinginan. Aku masih mencarinya. Sementara itu, aku ingin madu.',
                         'Freedom from desire. I am still looking. Meanwhile, I want honey.'),
                        'tertawa'),
            'bijak': (('Duduklah. Kau tidak datang ke sini secara kebetulan; tidak ada yang datang ke sini secara kebetulan.',
                       'Sit. You did not come here by chance; nobody comes here by chance.'),
                      ('Tidak ada. Mencari adalah cara terlambat untuk menemukan.', 'Nothing. Seeking is a late way of finding.'),
                      'napas_meditasi'),
            'penakut': (('Kau bukan perampok? Bagus. Aku sudah tua untuk berlari.', 'You are not a robber? Good. I am too old to run.'),
                        ('Tempat di mana tidak ada yang mencariku. Sejauh ini berhasil, kecuali hari ini.',
                         'A place where nobody looks for me. It worked so far, except today.'),
                        'sikap_tua'),
            'sombong': (('Kau beruntung. Aku tidak menerima tamu yang belum berjodoh, dan ternyata kau berjodoh.',
                         'You are fortunate. I receive no visitor without a bond, and it seems you have one.'),
                        ('Dao. Yang lain di gunung ini mencari herba.', 'The Dao. The others on this mountain seek herbs.'),
                        'sikap_angkuh'),
            'jujur': (('Aku tidak punya banyak untuk diajarkan, tapi aku punya banyak waktu untuk mendengarkan.',
                       'I have little to teach, but much time to listen.'),
                      ('Jawaban atas kesalahanku sendiri. Di kota aku terlalu sering benar.',
                       'An answer to my own mistakes. In town I was right too often.'),
                      'angguk_dengar'),
            'licik': (('Tamu yang datang ke gua pertapa biasanya menginginkan sesuatu. Apa yang bisa kau tukar?',
                       'Visitors to a hermit\'s cave usually want something. What can you trade?'),
                      ('Rahasia. Dan aku sudah punya banyak. Mau beli satu?', 'Secrets. And I have many. Want to buy one?'),
                      'bisik'),
        }),
    'tetua_sekte': (
        ('Bolehkah aku belajar di sini?', 'May I study here?'),
        ('Bagaimana sekte ini didirikan?', 'How was this sect founded?'),
        ('Pendiri %s menebas gunung menjadi dua dan membangun aula di celahnya, supaya setiap murid yang naik tangga belajar '
         'satu hal: bahkan batu pun memberi jalan bagi tekad. Setidaknya begitu ceritanya. Batunya memang terbelah.',
         'The founder of %s split a mountain in two and built the hall in the gap, so every disciple who climbs the steps learns '
         'one thing: even stone gives way to resolve. At least, so the story goes. The rock is certainly split.'),
        'tempat.sect_visit', 'sikap_tangan_belakang', '', {
            'ramah': (('Tamu sekte! Murid luar akan menunjukkan jalan. Jangan sungkan bertanya.',
                       'A guest of the sect! An outer disciple will show you the way. Do not hesitate to ask.'),
                      ('Gerbang kami terbuka setiap Hari Pembukaan Gerbang. Bawalah hati yang sabar dan kaki yang kuat; tangganya panjang.',
                       'Our gate opens on every Gate Opening Day. Bring a patient heart and strong legs; the stairs are long.'),
                      'hormat'),
            'curiga': (('Kau bukan murid kami. Apa yang kau lihat di sini tidak untuk diceritakan di luar.',
                        'You are not our disciple. What you see here is not to be told outside.'),
                       ('Belajar? Atau mencuri teknik? Banyak yang datang dengan kata pertama dan pergi dengan kata kedua.',
                        'Study? Or steal techniques? Many arrive with the first word and leave with the second.'),
                       'sikap_lipat_tangan'),
            'serakah': (('Tamu yang terhormat. Sekte kami menerima persembahan dalam bentuk apa pun. Batu roh lebih disukai.',
                         'Honoured guest. Our sect accepts offerings in any form. Spirit stones are preferred.'),
                        ('Belajar ada harganya. Pengetahuan yang gratis biasanya pengetahuan yang murah.',
                         'Learning has a price. Free knowledge is usually cheap knowledge.'),
                        'hitung_sempoa'),
            'bijak': (('Langkahmu membawa debu dari banyak jalan. Duduklah; teh di sekte ini lebih tua dari sebagian muridnya.',
                       'Your steps carry the dust of many roads. Sit; the tea in this sect is older than some of its disciples.'),
                      ('Kau sudah belajar sejak kau tiba. Sekte hanya memberi nama pada apa yang kau pelajari.',
                       'You have been learning since you arrived. A sect only gives names to what you learn.'),
                      'minum_teh'),
            'penakut': (('Ka-kau datang sendirian? Tidak ada pasukan di belakangmu? Bagus. Bagus sekali.',
                         'Y-you came alone? No army behind you? Good. Very good.'),
                        ('Tanyakan pada Ketua. Aku tidak berani memutuskan apa pun tanpa Ketua.',
                         'Ask the Sect Master. I would not dare decide anything without the Master.'),
                        'kaget'),
            'sombong': (('Kau berdiri di halaman sekte terbesar di wilayah ini. Kebanyakan tamu menunggu tiga hari untuk masuk.',
                         'You stand in the courtyard of the greatest sect in this region. Most guests wait three days to enter.'),
                        ('Belajar di sini? Ujian masuk kami sudah membuat anak-anak klan menangis. Silakan mencoba.',
                         'Study here? Our entrance trial has made clan children weep. You are welcome to try.'),
                        'sikap_angkuh'),
            'jujur': (('Selamat datang. Sekte kami tidak besar, tetapi tidak ada yang kami sembunyikan dari tamu yang sopan.',
                       'Welcome. Our sect is not large, but we hide nothing from a polite guest.'),
                      ('Kalau kau mau belajar, belajarlah dari murid luar dulu. Mereka mengajarkan hal yang tidak diajarkan tetua: kesabaran.',
                       'If you want to learn, learn from the outer disciples first. They teach what elders do not: patience.'),
                      'angguk_dengar'),
            'licik': (('Tamu! Kau datang tepat waktu. Sekte kami sedang... membutuhkan sahabat dengan tangan yang kuat.',
                       'A guest! You came just in time. Our sect is... in need of friends with strong hands.'),
                      ('Tentu, tentu. Setiap tamu boleh belajar. Setiap tamu juga boleh membantu kami sedikit. Timbal balik.',
                       'Of course, of course. Every guest may learn. Every guest may also help us a little. Give and take.'),
                      'kipas'),
        }),
}


def isi(g):
    D, N, P = g.D, g.N, g.P
    for peran, (topik, tanya_lore, lore, suara, gestur_diam, prop, varian) in PERAN.items():
        isian = ['sekte_terdekat'] if '%s' in lore[0] else []
        for w in WATAK:
            sapa, jawab, gestur = varian[w]
            D(f'{peran}_{w}', f'peran:{peran}', [
                N('mulai', sapa, gestur=gestur, prop=prop if gestur in ('sikap_tua', 'jalan_tua') else '', suara=suara, pilihan=[
                    P(topik, ke='topik'),
                    P(tanya_lore, ke='lore'),
                    P(('Ada kabar apa akhir-akhir ini?', 'Heard any news lately?'), ke='kabar'),
                    P(('Sampai jumpa.', 'Farewell.'))]),
                N('topik', jawab, gestur='bicara_tangan', pilihan=[
                    P(tanya_lore, ke='lore'), P(('Terima kasih.', 'Thank you.'))]),
                N('lore', lore, isian=isian, gestur='cerita', pilihan=[
                    P(('Ada kabar apa akhir-akhir ini?', 'Heard any news lately?'), ke='kabar'), P(('Terima kasih.', 'Thank you.'))]),
                N('kabar', None, kabar=True, gestur='bisik'),
            ], prioritas=-1, syarat=[f'watak {w}'])

    # a village in fear of a far stronger guest (38.R.5)
    D('kepala_desa_takut', 'peran:kepala_desa', [
        N('mulai', ('M-mohon ampun... kami sendiri yang akan memperbaikinya. Kau tidak perlu repot, sungguh, tidak perlu!',
                    'P-please forgive us... we will repair it ourselves. You need not trouble yourself, truly, no need!'),
          gestur='berlutut', suara='tempat.village_fear', pilihan=[
            P(('Berdirilah. Aku akan membayar kerusakannya.', 'Stand up. I will pay for the damage.'), ke='lega'),
            P(('(Pergi tanpa berkata apa-apa)', '(Leave without a word)'))]),
        N('lega', ('...Kau sungguh-sungguh? Langit, ternyata masih ada orang kuat yang punya hati. Tukang kami ada di balai desa.',
                   '...You mean it? Heavens, there are still strong people with hearts. Our craftsmen are at the village hall.'),
          gestur='hormat_dalam'),
    ], prioritas=40, syarat=['takut 60'])

    # clan patriarch after the young master's challenge
    D('patriark_hormat', 'peran:patriark_klan', [
        N('mulai', ('Anakku jatuh di depan seluruh klan, dan bangkit lebih bijak. Itu utang yang tak bisa kubayar dengan perak. Tanah '
                    'klan ini terbuka bagimu, dan aula leluhur pun begitu.',
                    'My son fell before the whole clan and rose wiser. That is a debt I cannot pay in silver. The clan\'s land is open '
                    'to you, and so is the ancestral hall.'),
          gestur='hormat_dalam', suara='tempat.clan_patriarch', pilihan=[
            P(('Ceritakan tentang aula leluhur.', 'Tell me about the ancestral hall.'), ke='aula'), P(('Terima kasih.', 'Thank you.'))]),
        N('aula', ('Setiap papan nama di sana adalah seseorang yang memilih klan di atas dirinya. Suatu hari mungkin ada papan kecil '
                   'untuk tamu yang mengajari anakku cara jatuh.',
                   'Every tablet there is someone who chose the clan over themselves. One day there may be a small tablet for the '
                   'guest who taught my son how to fall.'), gestur='cerita'),
    ], prioritas=10, syarat=['bendera klan_hormat'])
    D('patriark_remeh', 'peran:patriark_klan', [
        N('mulai', ('Ah, yang mengalah pada anakku. Harga di desa ini sedikit lebih mahal untuk orang yang tidak berdiri tegak. Jangan '
                    'tersinggung; itu tradisi.',
                    'Ah, the one who yielded to my son. Prices here are a little higher for those who do not stand tall. Do not be '
                    'offended; it is tradition.'),
          gestur='kipas', suara='tempat.clan_patriarch', pilihan=[
            P(('Aku ingin menantang anakmu lagi.', 'I want to challenge your son again.'), ke='lagi', syarat=['pita 1']),
            P(('Aku mengerti.', 'I understand.'))]),
        N('lagi', ('Oh? Menarik. Ia ada di halaman latihan. Kalau kau menang kali ini, aku akan lupa hari itu pernah terjadi.',
                   'Oh? Interesting. He is in the training yard. If you win this time, I will forget that day ever happened.'),
          gestur='tunjuk', akibat=['bendera klan_meremehkan off', 'bendera tuan_muda_tantang_ulang']),
    ], prioritas=10, syarat=['bendera klan_meremehkan'])
    D('tuan_muda_ulang', 'peran:tuan_muda', [
        N('mulai', ('Ayah bilang kau mau mencoba lagi. Kali ini jangan mengalah; itu membosankan.',
                    'Father says you want to try again. Do not yield this time; it was boring.'),
          gestur='hunus', suara='tempat.young_master', pilihan=[
            P(('(Bertarung)', '(Fight)'), akibat=['tanding_klan']), P(('Belum sekarang.', 'Not now.'))]),
    ], prioritas=12, syarat=['bendera tuan_muda_tantang_ulang', '!bendera klan_hormat'])

    # sect disciples and the demonic elder
    D('murid_luar_sapa', 'peran:murid_sekte_luar', [
        N('mulai', ('Ada keperluan apa ke sekte kami? Tetua sedang bermeditasi; kalau mendesak, aku bisa memanggil kakak seperguruan.',
                    'What brings you to our sect? The elder is meditating; if it is urgent, I can call a senior brother.'),
          gestur='sapu', prop='sapu', suara='tempat.sect_visit', pilihan=[
            P(('Bagaimana hidup murid luar?', 'What is an outer disciple\'s life like?'), ke='hidup'),
            P(('Ada kabar apa?', 'Any news?'), ke='kabar'),
            P(('Tidak mendesak.', 'Nothing urgent.'))]),
        N('hidup', ('Menyapu, mengangkat air, latihan napas, menyapu lagi. Kalau rajin tiga tahun, mungkin naik jadi murid dalam. Kalau '
                    'tidak, ya menyapu lagi.',
                    'Sweeping, carrying water, breathing drills, sweeping again. Work hard for three years and maybe you become an '
                    'inner disciple. If not, well, more sweeping.'), gestur='angkat_air'),
        N('kabar', None, kabar=True, gestur='bisik'),
    ], prioritas=-1)
    D('murid_dalam_sapa', 'peran:murid_sekte_dalam', [
        N('mulai', ('Kau tamu Tetua? Jalan ke aula utama lewat tangga batu itu. Jangan menyimpang ke paviliun kitab; penjaganya tidak '
                    'punya selera humor.',
                    'You are the Elder\'s guest? The way to the main hall is up those stone steps. Do not stray into the scripture '
                    'pavilion; its keeper has no sense of humour.'),
          gestur='sikap_tangan_belakang', suara='tempat.sect_visit', pilihan=[
            P(('Mau tanding latih?', 'Care to spar?'), ke='tanding', syarat=['pita 1']),
            P(('Terima kasih.', 'Thank you.'))]),
        N('tanding', ('Tanding? Dengan tamu? ...Satu ronde. Kalau Tetua bertanya, kita sedang "bertukar pemahaman".',
                      'A spar? With a guest? ...One round. If the Elder asks, we were "exchanging insights".'),
          gestur='hunus', pilihan=[P(('(Mulai)', '(Begin)'), akibat=['tanding_sekte']), P(('Lain kali.', 'Another time.'))]),
    ], prioritas=-1)
    D('tetua_iblis_ancam', 'peran:tetua_iblis', [
        N('mulai', ('Jangan kira kau bisa pulang hidup-hidup! Darahmu akan jadi tinta untuk jimat-jimatku.',
                    'Don\'t think you\'ll leave alive! Your blood will be ink for my talismans.'),
          gestur='ancam', suara='tempat.demon_hideout', pilihan=[
            P(('(Bertarung)', '(Fight)'), akibat=['mulai_duel']), P(('(Mundur)', '(Withdraw)'))]),
    ], prioritas=-1)
