"""38ST content: the inheritance chains warisan_<dao>_<tier> of DESIGN 38.S.4 (the design table says "9 Dao"; the code has
8 Dao in dev.kultivator.data.Dao, so there are 24 chains). The remnant soul of each tomb (condition "makam <dao> <tier>",
38GU writes the tomb's Dao and tier into the body's memory) tells its ancestor's story in three parts, then sets a Heart
trial of three illusions shaped by that story; the Strength and Comprehension trials of the tomb are 38GU's (event
"ujian makam:<dao>:<tier>"); the soul's farewell ends the chain."""

DAO = ['sword', 'fire', 'ice', 'lightning', 'body', 'life', 'shadow', 'space']
DAO_NAMA = {'sword': ('Pedang', 'Sword'), 'fire': ('Api', 'Fire'), 'ice': ('Es', 'Ice'), 'lightning': ('Petir', 'Lightning'),
            'body': ('Tubuh', 'Body'), 'life': ('Kehidupan', 'Life'), 'shadow': ('Bayangan dan Angin', 'Shadow and Wind'),
            'space': ('Ruang', 'Space')}


def A(nama, gelar, kisah, ilusi, pamit):
    """One ancestor: name, title, three story parts, three illusions [(scene, [(choice, right?) x3])], farewell."""
    return dict(nama=nama, gelar=gelar, kisah=kisah, ilusi=ilusi, pamit=pamit)


LELUHUR = {
    # ---------------------------------------------------------------- SWORD
    ('sword', 1): A('Hua Qingfeng', ('Pedang Embun Pagi', 'Morning Dew Sword'), [
        ('Aku anak tukang perahu di penyeberangan Sungai Kabut. Waktu umurku sembilan, begal membakar perahu ayahku. Seorang '
         'pengembara memberiku satu jurus pedang, hanya satu, lalu pergi tanpa nama.',
         'I was a ferryman\'s son at the Misty River crossing. When I was nine, bandits burned my father\'s boat. A wanderer gave me '
         'one sword form, only one, and left without a name.'),
        ('Tiga puluh tahun aku menebas kabut sungai setiap fajar dengan jurus yang sama. Orang menertawakanku: "Satu jurus? Anak '
         'sekte tahu seratus." Aku tidak menjawab. Embun di ujung pedangku menjawab.',
         'For thirty years I cut the river mist every dawn with the same form. People laughed: "One form? Sect brats know a '
         'hundred." I did not answer. The dew on my blade answered.'),
        ('Saat gelombang binatang datang ke penyeberangan, aku berdiri di dermaga. Satu jurus, sepuluh ribu kali. Sungai terbelah '
         'sesaat, dan perahu terakhir sampai di seberang. Aku tidak. Aku tidak menyesal.',
         'When the beast tide came to the crossing, I stood on the pier. One form, ten thousand times. The river split for a moment, '
         'and the last boat reached the far bank. I did not. I have no regrets.')], [
        (('Ilusi pertama: seorang guru sekte agung menawarkan seratus teknik, asal kau melupakan satu jurus yang kau latih.',
          'First illusion: a great sect master offers you a hundred techniques if you forget the one form you have trained.'),
         [(('Kuterima seratus teknik itu.', 'I accept the hundred techniques.'), False),
          (('Satu jurus yang kupahami lebih berat dari seratus yang kuhafal.', 'One form understood outweighs a hundred memorised.'), True),
          (('Aku minta dua ratus.', 'I ask for two hundred.'), False)]),
        (('Ilusi kedua: begal menyandera penumpang perahu. Pemimpinnya tertawa di haluan.',
          'Second illusion: bandits hold the ferry passengers hostage. Their leader laughs at the prow.'),
         [(('Berdiri di antara begal dan penumpang, pedang terhunus.', 'Stand between the bandits and the passengers, sword drawn.'), True),
          (('Tebas pemimpinnya secepat mungkin, apa pun yang terjadi.', 'Cut down the leader as fast as possible, whatever happens.'), False),
          (('Lompat ke sungai dan cari bantuan.', 'Leap into the river and go for help.'), False)]),
        (('Ilusi ketiga: pedangmu patah menjadi dua di tengah pertarungan.', 'Third illusion: your sword snaps in two mid-fight.'),
         [(('Pedang hanya besi. Jurusnya ada di tanganku.', 'A sword is only iron. The form lives in my hand.'), True),
          (('Menyerah. Tanpa pedang aku bukan apa-apa.', 'Surrender. Without a sword I am nothing.'), False),
          (('Lari mencari pedang baru.', 'Run for a new sword.'), False)])],
        ('Pergilah. Kalau suatu fajar kau melihat embun di ujung pedangmu, itu aku yang mengangguk.',
         'Go. If one dawn you see dew on the tip of your blade, that is me nodding.')),
    ('sword', 2): A('Ling Xuanyi', ('Pedang Tanpa Sarung', 'The Unsheathed Blade'), [
        ('Guruku dibunuh di malam aku diangkat menjadi murid inti. Aku bersumpah tidak akan menyarungkan pedangku sebelum '
         'pembunuhnya mati. Sarung itu kulempar ke jurang.',
         'My master was murdered the night I became a core disciple. I swore never to sheathe my sword until the killer was dead. '
         'I threw the scabbard into the abyss.'),
        ('Dua puluh tahun pedang telanjang itu kubawa. Ia memotong lawan, lalu teman, lalu tanganku sendiri setiap kali aku tidur. '
         'Murid-muridku belajar membenci sebelum belajar memegang gagang.',
         'For twenty years I carried that naked blade. It cut enemies, then friends, then my own hand each time I slept. My '
         'disciples learned to hate before they learned to hold a hilt.'),
        ('Ketika akhirnya kutemukan pembunuh itu, ia sudah tua, buta, dan menjual kayu bakar. Aku pulang tanpa menebasnya, dan '
         'untuk pertama kalinya aku tidur tanpa luka. Pedang tanpa sarung menebas pemiliknya lebih dulu.',
         'When at last I found the killer, he was old, blind and selling firewood. I went home without striking, and for the first '
         'time I slept without a wound. A sword without a scabbard cuts its owner first.')], [
        (('Ilusi pertama: pembunuh gurumu berlutut di depanmu, tua dan buta, memohon nyawa.',
          'First illusion: your master\'s killer kneels before you, old and blind, begging for his life.'),
         [(('Tebas. Sumpah adalah sumpah.', 'Strike. An oath is an oath.'), False),
          (('Biarkan ia hidup dengan apa yang ia lakukan.', 'Let him live with what he did.'), True),
          (('Buat ia menderita lebih dulu.', 'Make him suffer first.'), False)]),
        (('Ilusi kedua: muridmu memohon agar dendam itu diwariskan kepadanya.', 'Second illusion: your disciple begs to inherit the vengeance.'),
         [(('Kuwariskan; dendam harus dibayar.', 'I pass it on; vengeance must be paid.'), False),
          (('Ajari ia memegang gagang, bukan memegang benci.', 'Teach them to hold a hilt, not a hatred.'), True),
          (('Suruh ia mencari sendiri.', 'Tell them to find their own.'), False)]),
        (('Ilusi ketiga: seseorang mengulurkan sarung pedang yang dulu kau lempar ke jurang.',
          'Third illusion: someone holds out the scabbard you once threw into the abyss.'),
         [(('Terima sarung itu dan sarungkan pedangmu.', 'Take the scabbard and sheathe your sword.'), True),
          (('Lempar lagi ke jurang.', 'Throw it into the abyss again.'), False),
          (('Patahkan sarung itu.', 'Break the scabbard.'), False)])],
        ('Sarungkan pedangmu setiap malam, sekalipun kau sedang marah. Terutama saat kau sedang marah.',
         'Sheathe your sword every night, even when you are angry. Especially when you are angry.')),
    ('sword', 3): A('Jian Wuya', ('Raja Pedang Tak Bertepi', 'Boundless Sword Sovereign'), [
        ('Aku menebas bintang jatuh yang akan menghantam Kota Seribu Jembatan. Langit terbelah, dan kota itu selamat. Aku menjadi '
         'pedang terkuat di zamanku.',
         'I cut down a falling star that was about to strike the City of a Thousand Bridges. The sky split, and the city was saved. '
         'I became the strongest sword of my age.'),
        ('Tetapi niat pedangku terlalu tajam. Burung jatuh dari dahan saat aku lewat. Anak-anak menangis bila aku tersenyum. Kota '
         'yang kuselamatkan menutup gerbangnya setiap kali aku datang.',
         'But my sword intent was too sharp. Birds fell from branches as I passed. Children wept when I smiled. The city I saved '
         'closed its gates whenever I came.'),
        ('Seratus tahun kemudian aku belajar menumpulkan diriku sendiri. Pedang yang melindungi harus bisa didekati. Pedang yang '
         'tidak bisa didekati hanyalah bintang jatuh yang lain.',
         'A hundred years later I learned to blunt myself. A sword that protects must be approachable. A sword that cannot be '
         'approached is only another falling star.')], [
        (('Ilusi pertama: gerbang kota tertutup karena takut padamu. Di dalam, orang-orang sakit membutuhkanmu.',
          'First illusion: the city gate is shut out of fear of you. Inside, sick people need you.'),
         [(('Belah gerbang itu dengan pedang.', 'Split the gate with your sword.'), False),
          (('Letakkan pedangmu di luar dan mengetuk tanpa senjata.', 'Leave your sword outside and knock unarmed.'), True),
          (('Pergi; mereka tidak pantas ditolong.', 'Leave; they do not deserve help.'), False)]),
        (('Ilusi kedua: seorang anak ingin menyentuh pedangmu.', 'Second illusion: a child wants to touch your sword.'),
         [(('Bentak anak itu agar menjauh.', 'Shout at the child to keep away.'), False),
          (('Balikkan bilahnya dan biarkan ia menyentuh punggung pedang.', 'Turn the blade and let them touch its spine.'), True),
          (('Sembunyikan pedangmu.', 'Hide your sword.'), False)]),
        (('Ilusi ketiga: langit menawarkan kekuatan tanpa tanding, asal kau selamanya sendirian.',
          'Third illusion: heaven offers you invincibility, if you remain forever alone.'),
         [(('Kuterima. Yang terkuat memang sendirian.', 'I accept. The strongest are always alone.'), False),
          (('Kutolak. Kekuatan yang tidak bisa dibagi tidak berguna.', 'I refuse. Strength that cannot be shared is useless.'), True),
          (('Kuterima, lalu akan kucari teman nanti.', 'I accept, and I will find friends later.'), False)])],
        ('Niat pedangku kuserahkan padamu. Tumpulkan ia setiap pagi, dan ia akan menjadi tajam saat dunia membutuhkannya.',
         'I entrust my sword intent to you. Blunt it every morning, and it will be sharp when the world needs it.')),

    # ---------------------------------------------------------------- FIRE
    ('fire', 1): A('Tao Hong', ('Penjaga Tungku Desa', 'Keeper of the Village Furnace'), [
        ('Aku pandai besi di desa pegunungan yang musim dinginnya panjang. Tungkuku satu-satunya api yang tidak pernah padam di '
         'desa itu.',
         'I was the smith of a mountain village with long winters. My furnace was the only fire there that never went out.'),
        ('Empat puluh musim dingin, setiap malam, orang-orang tidur di sekitar tungkuku. Aku belajar mengalirkan qi ke dalam bara '
         'agar kayu yang sedikit cukup sampai pagi.',
         'Forty winters, every night, the villagers slept around my furnace. I learned to pour qi into the embers so a little wood '
         'would last until morning.'),
        ('Di musim dingin terakhirku, kayu habis. Aku menyalakan tungku dengan qi-ku sendiri sampai fajar. Desa itu bangun dengan '
         'hangat. Aku tidak bangun, tapi aku hangat.',
         'In my last winter the wood ran out. I fed the furnace with my own qi until dawn. The village woke up warm. I did not '
         'wake, but I was warm.')], [
        (('Ilusi pertama: badai salju, kayu habis, dan bara mulai padam. Apa yang kau bakar?',
          'First illusion: a blizzard, the wood is gone, and the embers are dying. What do you burn?'),
         [(('Pintu rumah tetangga.', 'The neighbour\'s door.'), False),
          (('Qi-ku sendiri.', 'My own qi.'), True),
          (('Tidak ada; setiap orang mengurus dirinya.', 'Nothing; everyone fends for themselves.'), False)]),
        (('Ilusi kedua: seorang saudagar menawar tungkumu dengan emas, untuk dibawa ke kota.',
          'Second illusion: a merchant offers gold for your furnace, to carry it to the city.'),
         [(('Jual. Emas bisa membeli kayu.', 'Sell. Gold buys wood.'), False),
          (('Api ini bukan milikku; ia milik orang yang tidur di sekitarnya.', 'This fire is not mine; it belongs to those who sleep around it.'), True),
          (('Minta emas dua kali lipat.', 'Ask for double the gold.'), False)]),
        (('Ilusi ketiga: seorang anak takut pada api tungku.', 'Third illusion: a child is afraid of the furnace fire.'),
         [(('Tunjukkan cara memanggang ubi di bara.', 'Show them how to roast sweet potatoes in the embers.'), True),
          (('Katakan api itu berbahaya, jangan dekat-dekat.', 'Tell them fire is dangerous, stay away.'), False),
          (('Biarkan saja ia kedinginan.', 'Let them stay cold.'), False)])],
        ('Api yang terbaik bukan yang paling besar, tetapi yang paling lama menghangatkan.',
         'The best fire is not the biggest, but the one that keeps others warm the longest.')),
    ('fire', 2): A('Yan Zhuo', ('Phoenix Abu', 'Ash Phoenix'), [
        ('Aku terbakar tiga kali dalam hidupku, dan tiga kali bangkit dari abuku sendiri. Orang menyebutku phoenix. Mereka tidak '
         'tahu harganya.',
         'Three times in my life I burned, and three times I rose from my own ash. People called me a phoenix. They did not know the price.'),
        ('Setiap kali bangkit, aku kehilangan sesuatu: nama ibuku, wajah kekasihku, lagu masa kecilku. Api tidak membakar tubuh '
         'saja; ia membakar apa pun yang kau genggam terlalu erat.',
         'Each time I rose, I lost something: my mother\'s name, my beloved\'s face, the song of my childhood. Fire does not burn '
         'only the body; it burns whatever you hold too tightly.'),
        ('Pada kali keempat aku memilih tidak bangkit. Aku menyerahkan apiku pada murid-muridku, sepercik untuk masing-masing. '
         'Bangkit tidak selalu berarti kembali menjadi dirimu yang lama.',
         'The fourth time, I chose not to rise. I gave my fire to my disciples, a spark for each. Rising does not always mean '
         'becoming your old self again.')], [
        (('Ilusi pertama: kau terbakar, dan api menawarkan hidup baru asal kau melepaskan satu kenangan.',
          'First illusion: you are burning, and the fire offers new life if you give up one memory.'),
         [(('Lepaskan kenangan yang paling menyakitkan.', 'Give up the most painful memory.'), False),
          (('Lepaskan kenangan yang paling kau genggam, walaupun indah.', 'Give up the memory you clutch hardest, even if it is beautiful.'), True),
          (('Tolak apinya dan mati.', 'Refuse the fire and die.'), False)]),
        (('Ilusi kedua: abumu berbicara dengan suaramu yang lama, memintamu kembali menjadi dirimu yang dulu.',
          'Second illusion: your ash speaks with your old voice, asking you to become who you were.'),
         [(('Kembali. Diriku yang lama lebih kuat.', 'Go back. My old self was stronger.'), False),
          (('Berterima kasih pada abu itu, lalu melangkah maju.', 'Thank the ash, then step forward.'), True),
          (('Sebarkan abu itu ke angin dengan marah.', 'Scatter the ash in anger.'), False)]),
        (('Ilusi ketiga: murid-muridmu kedinginan dan apimu tinggal sedikit.', 'Third illusion: your disciples are cold and little of your fire remains.'),
         [(('Simpan apimu untuk bangkit lagi.', 'Keep your fire to rise again.'), False),
          (('Berikan sepercik untuk masing-masing.', 'Give a spark to each of them.'), True),
          (('Pilih satu murid terbaik.', 'Choose the single best disciple.'), False)])],
        ('Kalau kau terbakar, jangan takut menjadi abu. Takutlah menjadi api yang tidak menghangatkan siapa pun.',
         'If you burn, do not fear becoming ash. Fear becoming a fire that warms no one.')),
    ('fire', 3): A('Chi Yao', ('Matahari Kedua', 'The Second Sun'), [
        ('Di puncak kultivasiku aku bisa menyalakan matahari kedua di langit. Musuh-musuhku menguap sebelum mendekat. Gunung meleleh '
         'menjadi danau kaca.',
         'At the peak of my cultivation I could kindle a second sun in the sky. My enemies evaporated before they came near. Mountains '
         'melted into glass lakes.'),
        ('Lalu datang musim kemarau yang panjang. Matahariku tidak bisa menurunkan hujan, tidak bisa menumbuhkan padi, tidak bisa '
         'menghangatkan seorang bayi tanpa membakarnya.',
         'Then came a long drought. My sun could not bring rain, could not grow rice, could not warm a baby without burning it.'),
        ('Aku menghabiskan tiga ratus tahun belajar membuat api sekecil lilin, cukup untuk membaca, cukup untuk memasak bubur. '
         'Itulah api tertinggi: kehangatan, bukan kehancuran.',
         'I spent three hundred years learning to make a flame as small as a candle, enough to read by, enough to cook porridge. '
         'That is the highest fire: warmth, not ruin.')], [
        (('Ilusi pertama: musuh besar menyerbu kotamu. Matahari keduamu bisa membakar mereka, dan setengah kota.',
          'First illusion: a great enemy assaults your city. Your second sun could burn them, and half the city.'),
         [(('Nyalakan matahari itu. Setengah kota lebih baik dari tidak ada.', 'Kindle the sun. Half a city is better than none.'), False),
          (('Jadikan apimu dinding hangat di sekeliling kota.', 'Make your fire a warm wall around the city.'), True),
          (('Mundur dan biarkan takdir memutuskan.', 'Withdraw and let fate decide.'), False)]),
        (('Ilusi kedua: seorang ibu memintamu menghangatkan bayinya yang membeku.', 'Second illusion: a mother asks you to warm her freezing baby.'),
         [(('Nyalakan api sekecil lilin di telapak tanganmu.', 'Kindle a flame as small as a candle in your palm.'), True),
          (('Tolak; apimu terlalu besar untuk bayi.', 'Refuse; your fire is too great for a baby.'), False),
          (('Suruh ia mencari tabib.', 'Tell her to find a physician.'), False)]),
        (('Ilusi ketiga: kau melihat danau-danau kaca yang dulu gunung.', 'Third illusion: you see the glass lakes that were once mountains.'),
         [(('Banggalah; itu bukti kekuatanmu.', 'Be proud; they prove your strength.'), False),
          (('Tanam pohon di tepinya, satu demi satu.', 'Plant trees on their shores, one by one.'), True),
          (('Lupakan saja.', 'Simply forget them.'), False)])],
        ('Matahari kedua sudah kupadamkan lama sekali. Yang kuwariskan padamu adalah lilinnya.',
         'I put out the second sun long ago. What I leave you is its candle.')),

    # ---------------------------------------------------------------- ICE
    ('ice', 1): A('Bai Shuang', ('Penjaga Benih Beku', 'Keeper of the Frozen Seed'), [
        ('Aku pemetik herba es di lereng utara. Waktu kelaparan besar datang, desa-desa memakan benih padi mereka sendiri.',
         'I gathered frost herbs on the northern slopes. When the great famine came, the villages ate their own seed rice.'),
        ('Aku mengumpulkan sisa benih dari setiap desa dan membekukannya di gua es dengan qi-ku. Orang lapar mengutukku karena '
         'menyimpan makanan.',
         'I gathered the last seed from every village and froze it in an ice cave with my qi. Hungry people cursed me for hoarding food.'),
        ('Musim semi berikutnya, benih itu kubagikan. Lembah itu hijau lagi. Dingin bukan kekejaman; dingin adalah cara menyimpan '
         'harapan sampai musimnya tiba.',
         'The next spring I gave the seed back. The valley turned green again. Cold is not cruelty; cold is how hope is kept until '
         'its season comes.')], [
        (('Ilusi pertama: anak-anak lapar memohon benih yang kau simpan.', 'First illusion: hungry children beg for the seed you keep.'),
         [(('Berikan semuanya sekarang.', 'Give it all now.'), False),
          (('Berikan makananmu sendiri, simpan benihnya.', 'Give them your own food, keep the seed.'), True),
          (('Usir mereka.', 'Send them away.'), False)]),
        (('Ilusi kedua: orang-orang menyebutmu kejam dan berhati es.', 'Second illusion: people call you cruel and ice-hearted.'),
         [(('Biarkan mereka bicara; musim semi akan menjawab.', 'Let them talk; spring will answer.'), True),
          (('Bekukan mulut mereka.', 'Freeze their mouths.'), False),
          (('Tinggalkan desa itu selamanya.', 'Leave the village forever.'), False)]),
        (('Ilusi ketiga: musim semi tiba. Seorang tuan tanah menawar semua benih itu.', 'Third illusion: spring comes. A landlord bids for all the seed.'),
         [(('Jual pada penawar tertinggi.', 'Sell to the highest bidder.'), False),
          (('Bagikan pada setiap desa sesuai yang mereka titipkan.', 'Return it to each village as they entrusted it.'), True),
          (('Simpan untuk dirimu sendiri.', 'Keep it for yourself.'), False)])],
        ('Dingin yang baik menjaga, bukan menggigit. Ingat itu setiap kali tanganmu membeku.',
         'Good cold preserves; it does not bite. Remember that whenever your hands turn to ice.')),
    ('ice', 2): A('Han Yue', ('Bulan Dingin', 'Cold Moon'), [
        ('Kekasihku gugur dalam perang sekte. Aku membekukan hatiku sendiri agar tidak merasakan duka itu.',
         'My beloved fell in a sect war. I froze my own heart so I would not feel the grief.'),
        ('Seratus tahun aku tidak menangis, tidak tertawa, tidak takut. Kultivasiku melesat. Murid-muridku menyebutku bulan: '
         'terang, indah, dan tidak bisa disentuh.',
         'For a hundred years I did not weep, laugh or fear. My cultivation soared. My disciples called me the moon: bright, '
         'beautiful, and untouchable.'),
        ('Sampai seorang murid kecil meninggal karena demam, dan aku tidak merasakan apa-apa. Malam itu aku mencairkan hatiku. '
         'Dukanya datang sekaligus, seratus tahun dalam satu malam. Aku memilih duka itu daripada kosong.',
         'Until a little disciple died of fever, and I felt nothing. That night I thawed my heart. The grief came all at once, a '
         'hundred years in one night. I chose that grief over emptiness.')], [
        (('Ilusi pertama: kekasihmu yang telah tiada tersenyum di depanmu dan menawarkan diri untuk dilupakan.',
          'First illusion: your lost beloved smiles before you and offers to be forgotten.'),
         [(('Lupakan dia agar tidak sakit lagi.', 'Forget them so it no longer hurts.'), False),
          (('Ingat dia, dan biarkan sakitnya menjadi milikmu.', 'Remember them, and let the pain be yours.'), True),
          (('Bekukan ilusi itu.', 'Freeze the illusion.'), False)]),
        (('Ilusi kedua: muridmu demam dan memanggil namamu.', 'Second illusion: your disciple has a fever and calls your name.'),
         [(('Duduk di sampingnya sepanjang malam.', 'Sit beside them all night.'), True),
          (('Kirim tabib; kau sedang berkultivasi.', 'Send a physician; you are cultivating.'), False),
          (('Bekukan demamnya dari jauh.', 'Freeze the fever from afar.'), False)]),
        (('Ilusi ketiga: hatimu mencair dan seratus tahun duka datang sekaligus.', 'Third illusion: your heart thaws and a hundred years of grief arrive at once.'),
         [(('Bekukan lagi, cepat.', 'Freeze it again, quickly.'), False),
          (('Menangislah.', 'Weep.'), True),
          (('Lari dari perasaan itu.', 'Run from the feeling.'), False)])],
        ('Dingin boleh menjadi senjatamu. Jangan biarkan ia menjadi hatimu.', 'Let the cold be your weapon. Never let it be your heart.')),
    ('ice', 3): A('Xue Wuhen', ('Salju Tanpa Jejak', 'Traceless Snow'), [
        ('Ketika laut timur bangkit menjadi gelombang setinggi gunung, aku berdiri di pantai dan membekukannya. Satu provinsi '
         'selamat di balik dinding es sepanjang seribu li.',
         'When the eastern sea rose into a mountain-high wave, I stood on the shore and froze it. A whole province was saved '
         'behind a wall of ice a thousand li long.'),
        ('Dinding itu harus dijaga agar tidak runtuh. Aku duduk di puncaknya tujuh puluh tahun, tidak bergerak, sampai salju '
         'menutupiku dan orang lupa ada seseorang di sana.',
         'The wall had to be held so it would not fall. I sat on its crest for seventy years, unmoving, until snow covered me and '
         'people forgot someone was there.'),
        ('Ketika laut tenang, aku mencairkan dinding itu perlahan, setetes demi setetes, agar tak satu desa pun banjir. Aku pergi '
         'tanpa jejak. Kekuatan terbesar adalah yang tidak meninggalkan bekas luka.',
         'When the sea grew calm, I thawed the wall slowly, drop by drop, so not one village flooded. I left without a trace. The '
         'greatest strength is the one that leaves no scar.')], [
        (('Ilusi pertama: gelombang raksasa datang. Kau bisa membekukannya atau menyelamatkan dirimu.',
          'First illusion: a giant wave is coming. You can freeze it or save yourself.'),
         [(('Bekukan gelombang itu, walau kau tidak bisa pergi lagi.', 'Freeze the wave, even if you can never leave.'), True),
          (('Terbang menjauh; yang lain urusan langit.', 'Fly away; the rest is heaven\'s business.'), False),
          (('Bekukan setengahnya saja.', 'Freeze only half of it.'), False)]),
        (('Ilusi kedua: tujuh puluh tahun lewat. Tak ada yang ingat namamu.', 'Second illusion: seventy years pass. No one remembers your name.'),
         [(('Ukir namamu di dinding es.', 'Carve your name into the ice wall.'), False),
          (('Teruslah duduk. Dindingnya yang penting, bukan namanya.', 'Keep sitting. The wall matters, not the name.'), True),
          (('Tinggalkan dinding itu.', 'Abandon the wall.'), False)]),
        (('Ilusi ketiga: laut tenang. Dindingmu harus dicairkan.', 'Third illusion: the sea is calm. Your wall must be thawed.'),
         [(('Hancurkan sekaligus; lebih cepat.', 'Shatter it at once; faster.'), False),
          (('Cairkan setetes demi setetes, sampai tak ada yang banjir.', 'Thaw it drop by drop, until no one floods.'), True),
          (('Biarkan saja berdiri selamanya.', 'Let it stand forever.'), False)])],
        ('Pergilah tanpa jejak, dan biarkan hanya kebaikan yang tertinggal.', 'Walk without a trace, and let only kindness remain behind.')),

    # ---------------------------------------------------------------- LIGHTNING
    ('lightning', 1): A('Lei Dazhuang', ('Penabuh Lonceng Badai', 'Ringer of the Storm Bell'), [
        ('Petir menyambarku waktu aku masih gembala kecil. Aku hidup, dan sejak itu aku bisa mendengar badai tiga hari sebelum '
         'datang.',
         'Lightning struck me when I was a small herd boy. I lived, and since then I could hear a storm three days before it came.'),
        ('Aku menggantung lonceng besar di bukit dan menabuhnya setiap kali badai mendekat. Gembala membawa ternak pulang, nelayan '
         'menarik perahu. Orang menyebutku si bodoh yang berlari di bawah hujan.',
         'I hung a great bell on the hill and rang it whenever a storm drew near. Herders brought their flocks home, fishermen '
         'pulled in their boats. People called me the fool who runs in the rain.'),
        ('Aku mengajari petir untuk turun di tiang-tiang besi yang kutanam, bukan di rumah orang. Kekuatan petir bukan pada '
         'kerasnya, tetapi pada peringatannya.',
         'I taught the lightning to fall on iron poles I planted, not on people\'s houses. The power of lightning is not in its '
         'loudness, but in its warning.')], [
        (('Ilusi pertama: kau mendengar badai datang, tapi orang-orang menertawakanmu.', 'First illusion: you hear a storm coming, but people laugh at you.'),
         [(('Tabuh lonceng sekeras-kerasnya, biar ditertawakan.', 'Ring the bell as hard as you can, laughter or not.'), True),
          (('Diam; biar mereka belajar sendiri.', 'Stay quiet; let them learn for themselves.'), False),
          (('Pergi ke desa lain.', 'Go to another village.'), False)]),
        (('Ilusi kedua: petir bisa kau arahkan ke rumah orang yang menghinamu.', 'Second illusion: you could guide the lightning to the house of the one who mocked you.'),
         [(('Arahkan ke sana.', 'Guide it there.'), False),
          (('Arahkan ke tiang besi di bukit.', 'Guide it to the iron pole on the hill.'), True),
          (('Biarkan jatuh di mana saja.', 'Let it fall wherever.'), False)]),
        (('Ilusi ketiga: seorang anak tersambar petir dan hidup, seperti dirimu dulu.', 'Third illusion: a child is struck by lightning and lives, as you once did.'),
         [(('Ajari ia mendengar badai.', 'Teach them to hear the storms.'), True),
          (('Katakan bahwa ia terkutuk.', 'Tell them they are cursed.'), False),
          (('Biarkan ia takut selamanya.', 'Let them be afraid forever.'), False)])],
        ('Kalau kau mendengar badai, tabuhlah lonceng. Biar orang tertawa; mereka akan tertawa di dalam rumah yang kering.',
         'If you hear a storm, ring the bell. Let people laugh; they will laugh inside dry houses.')),
    ('lightning', 2): A('Zi Dian', ('Kilat Ungu', 'Violet Flash'), [
        ('Aku jenius tercepat di sekteku. Dua puluh tahun aku melompati tiga ranah. Aku tidak sabar menunggu apa pun, apalagi '
         'kesengsaraan langit.',
         'I was the fastest genius of my sect. In twenty years I leapt three realms. I could not wait for anything, least of all a '
         'heavenly tribulation.'),
        ('Aku memanggil kesengsaraanku sendiri sebelum waktunya. Petir kesembilan merobek meridianku. Aku jatuh dari Inti Emas '
         'ke Pemurnian Qi dalam satu malam.',
         'I called down my tribulation before its time. The ninth bolt tore my meridians. I fell from Golden Core to Qi Refining '
         'in a single night.'),
        ('Empat puluh tahun aku naik lagi, perlahan, satu napas demi satu napas. Kali kedua aku menunggu langit memanggilku. Kilat '
         'tercepat adalah yang tahu kapan harus menunggu awan.',
         'For forty years I climbed again, slowly, one breath at a time. The second time I waited for heaven to call me. The '
         'fastest lightning is the one that knows when to wait for the cloud.')], [
        (('Ilusi pertama: kesengsaraanmu bisa kau panggil sekarang, setahun lebih awal.', 'First illusion: you could call your tribulation now, a year early.'),
         [(('Panggil sekarang. Waktu adalah musuh.', 'Call it now. Time is the enemy.'), False),
          (('Tunggu. Langit tahu musimnya.', 'Wait. Heaven knows its season.'), True),
          (('Panggil dua kesengsaraan sekaligus.', 'Call two tribulations at once.'), False)]),
        (('Ilusi kedua: meridianmu robek. Seseorang menawarkan pil terlarang untuk naik lagi dalam sehari.',
          'Second illusion: your meridians are torn. Someone offers a forbidden pill to climb back in a day.'),
         [(('Telan pilnya.', 'Swallow the pill.'), False),
          (('Tolak, dan mulai lagi dari napas pertama.', 'Refuse, and begin again from the first breath.'), True),
          (('Curi resep pil itu.', 'Steal the pill\'s recipe.'), False)]),
        (('Ilusi ketiga: muridmu yang berbakat ingin melompati ranah sepertimu dulu.', 'Third illusion: your gifted disciple wants to leap realms as you once did.'),
         [(('Dorong ia; bakat harus dipakai.', 'Push them; talent must be used.'), False),
          (('Ceritakan malam petir kesembilan itu.', 'Tell them about the night of the ninth bolt.'), True),
          (('Larang ia berkultivasi.', 'Forbid them from cultivating.'), False)])],
        ('Kecepatanku kuwariskan padamu, bersama kesabaranku. Jangan pernah pakai yang satu tanpa yang lain.',
         'I leave you my speed, together with my patience. Never use one without the other.')),
    ('lightning', 3): A('Lei Zhen', ('Penguasa Guntur', 'Lord of Thunder'), [
        ('Aku menghadapi sembilan kesengsaraan dalam hidupku, dan menang di semuanya. Petir menjadi saudaraku; aku bisa memanggilnya '
         'dari langit yang cerah.',
         'I faced nine tribulations in my life and won them all. Lightning became my brother; I could call it from a clear sky.'),
        ('Pada kesengsaraan murid-muridku, langit mengirim petir yang terlalu kejam untuk mereka. Aku berdiri di atas mereka dan '
         'menerima petir itu dengan tubuhku sendiri.',
         'At my disciples\' tribulations, heaven sent bolts too cruel for them. I stood over them and took those bolts with my own body.'),
        ('Langit marah karena aku mencuri ujiannya. Ia menjatuhkan petir kesepuluh, khusus untukku. Aku tertawa dan menerimanya. '
         'Guntur yang sejati melindungi, bukan menakut-nakuti.',
         'Heaven was angry that I stole its trial. It dropped a tenth bolt, just for me. I laughed and took it. True thunder '
         'protects; it does not frighten.')], [
        (('Ilusi pertama: petir yang terlalu kejam jatuh ke arah muridmu.', 'First illusion: a bolt too cruel falls toward your disciple.'),
         [(('Berdiri di atasnya dan terima petir itu.', 'Stand over them and take the bolt.'), True),
          (('Itu ujiannya, bukan ujianmu.', 'It is their trial, not yours.'), False),
          (('Belokkan petir ke murid lain.', 'Deflect the bolt to another disciple.'), False)]),
        (('Ilusi kedua: langit menawarkan gelar Penguasa Guntur, asal kau tidak pernah lagi melindungi siapa pun.',
          'Second illusion: heaven offers you the title Lord of Thunder, if you never shield anyone again.'),
         [(('Kuterima gelarnya.', 'I accept the title.'), False),
          (('Simpan gelarmu; aku punya murid.', 'Keep your title; I have disciples.'), True),
          (('Tawar gelar yang lebih tinggi.', 'Bargain for a higher title.'), False)]),
        (('Ilusi ketiga: petir kesepuluh jatuh, khusus untukmu.', 'Third illusion: the tenth bolt falls, just for you.'),
         [(('Lari.', 'Run.'), False),
          (('Tertawa dan terima.', 'Laugh, and take it.'), True),
          (('Kutuk langit.', 'Curse heaven.'), False)])],
        ('Guntur yang kuwariskan bukan untuk menakuti dunia. Ia untuk berdiri di antara dunia dan langit yang terlalu kejam.',
         'The thunder I leave you is not for frightening the world. It is for standing between the world and a heaven too cruel.')),

    # ---------------------------------------------------------------- BODY
    ('body', 1): A('Shi Ban', ('Si Batu Pemikul', 'The Stone Bearer'), [
        ('Aku kuli di kaki Gunung Seribu Doa. Para peziarah tua sering jatuh di jalan setapak yang curam.',
         'I was a porter at the foot of the Mountain of a Thousand Prayers. Old pilgrims often fell on the steep path.'),
        ('Setiap hari aku memikul satu batu pipih ke atas dan memasangnya sebagai anak tangga. Satu batu sehari, empat puluh tahun. '
         'Tulangku menjadi keras seperti batu yang kupikul.',
         'Every day I carried one flat stone up and set it as a step. One stone a day, forty years. My bones grew as hard as the '
         'stones I carried.'),
        ('Ketika tangga itu sampai di puncak, aku sudah tua. Aku duduk di anak tangga terakhir dan melihat seorang nenek naik tanpa '
         'jatuh. Tubuh yang ditempa oleh kebaikan tidak pernah lelah.',
         'When the stair reached the top, I was old. I sat on the last step and watched a grandmother climb without falling. A '
         'body tempered by kindness never tires.')], [
        (('Ilusi pertama: hujan deras. Batu hari ini sangat berat, dan tak ada yang akan tahu kalau kau melewatkannya.',
          'First illusion: heavy rain. Today\'s stone is very heavy, and no one would know if you skipped it.'),
         [(('Pikul batunya. Satu hari tetap satu hari.', 'Carry the stone. One day is still one day.'), True),
          (('Lewatkan; besok bawa dua.', 'Skip it; carry two tomorrow.'), False),
          (('Bayar orang lain memikulnya.', 'Pay someone else to carry it.'), False)]),
        (('Ilusi kedua: seorang kultivator menawarimu terbang ke puncak dan membangun tangga dengan teknik dalam sehari.',
          'Second illusion: a cultivator offers to fly you up and build the stair with a technique in a single day.'),
         [(('Terima; lebih cepat lebih baik.', 'Accept; faster is better.'), False),
          (('Tolak dengan hormat; tangga ini juga sedang membangunku.', 'Decline politely; this stair is building me too.'), True),
          (('Minta ia membangun dua tangga.', 'Ask him to build two stairs.'), False)]),
        (('Ilusi ketiga: tangga selesai, dan tak ada yang tahu siapa yang membangunnya.', 'Third illusion: the stair is done, and nobody knows who built it.'),
         [(('Ukir namamu di anak tangga pertama.', 'Carve your name on the first step.'), False),
          (('Duduk dan lihat orang-orang naik.', 'Sit and watch people climb.'), True),
          (('Pungut bayaran dari setiap peziarah.', 'Charge every pilgrim a toll.'), False)])],
        ('Satu batu sehari. Itu seluruh rahasiaku, dan sekarang milikmu.', 'One stone a day. That is my whole secret, and now it is yours.')),
    ('body', 2): A('Tie Gu', ('Tulang Besi', 'Iron Bones'), [
        ('Aku kultivator tubuh yang bekerja di tambang roh. Para penambang menertawakanku karena aku menolak memakai alat; aku '
         'memecah batu dengan tangan.',
         'I was a body cultivator who worked in a spirit mine. The miners laughed because I refused tools; I broke stone with my hands.'),
        ('Suatu hari atap tambang runtuh. Empat puluh penambang terjebak. Aku menahan balok utama dengan punggungku tiga hari tiga '
         'malam sementara mereka menggali jalan keluar.',
         'One day the mine roof collapsed. Forty miners were trapped. I held the main beam on my back for three days and three '
         'nights while they dug their way out.'),
        ('Ketika penambang terakhir keluar, tulangku retak di seratus tempat. Tulang-tulang itu menyambung lagi lebih kuat dari '
         'besi. Tubuh menjadi kuat bukan karena memukul, tetapi karena menahan.',
         'When the last miner was out, my bones were cracked in a hundred places. They knit again stronger than iron. A body grows '
         'strong not by striking, but by holding.')], [
        (('Ilusi pertama: atap runtuh. Kau bisa keluar sendiri sekarang, atau menahan balok untuk yang lain.',
          'First illusion: the roof falls. You can get out alone now, or hold the beam for the others.'),
         [(('Tahan balok itu.', 'Hold the beam.'), True),
          (('Keluar dan cari bantuan.', 'Get out and find help.'), False),
          (('Pecahkan batu untuk jalanmu sendiri.', 'Break stone for your own way out.'), False)]),
        (('Ilusi kedua: hari ketiga. Punggungmu retak. Seorang penambang masih tertinggal di dalam.',
          'Second illusion: the third day. Your back is cracking. One miner is still inside.'),
         [(('Lepaskan; kau sudah menolong cukup banyak.', 'Let go; you have saved enough.'), False),
          (('Tahan satu napas lagi, lalu satu lagi.', 'Hold one more breath, then another.'), True),
          (('Teriak agar ia cepat.', 'Shout at him to hurry.'), False)]),
        (('Ilusi ketiga: para penambang ingin membangun patungmu.', 'Third illusion: the miners want to raise a statue of you.'),
         [(('Minta mereka membangun penyangga atap yang lebih baik.', 'Ask them to build better roof supports instead.'), True),
          (('Minta patungnya dari emas.', 'Ask for a golden statue.'), False),
          (('Biarkan saja.', 'Let them.'), False)])],
        ('Tulang besi tidak lahir dari pukulan. Ia lahir dari beban yang tidak kau lepaskan.',
         'Iron bones are not born from blows. They are born from burdens you refuse to drop.')),
    ('body', 3): A('Pan Yue', ('Gunung Berjalan', 'The Walking Mountain'), [
        ('Tubuhku menjadi begitu kuat sehingga tak ada senjata yang bisa melukaiku. Aku berjalan menembus perang tanpa satu goresan pun.',
         'My body grew so strong that no weapon could wound me. I walked through wars without a single scratch.'),
        ('Tetapi tubuh yang tak terluka juga tak bisa merasakan. Aku tidak bisa merasakan tangan anakku. Aku tidak bisa merasakan '
         'hangatnya teh. Aku menjadi gunung yang berjalan, dan gunung itu kesepian.',
         'But a body that cannot be wounded cannot feel either. I could not feel my child\'s hand. I could not feel warm tea. I '
         'became a walking mountain, and the mountain was lonely.'),
        ('Di akhir hidupku aku berbaring di antara dua lembah yang dipisahkan tebing dan menjadi celah gunung, jalan bagi para '
         'pedagang. Tubuh tertinggi adalah tubuh yang bisa dilalui orang lain.',
         'At the end of my life I lay down between two valleys parted by cliffs and became a mountain pass, a road for merchants. '
         'The highest body is a body others can pass through.')], [
        (('Ilusi pertama: kau bisa membuat tubuhmu tak terluka, tapi tak akan bisa merasakan apa pun lagi.',
          'First illusion: you can make your body invulnerable, but you will never feel anything again.'),
         [(('Pilih tak terluka.', 'Choose invulnerability.'), False),
          (('Pilih tetap bisa merasakan.', 'Choose to keep feeling.'), True),
          (('Pilih keduanya, entah bagaimana.', 'Choose both, somehow.'), False)]),
        (('Ilusi kedua: anakmu meraih tanganmu, tetapi kulitmu sekeras batu.', 'Second illusion: your child reaches for your hand, but your skin is hard as stone.'),
         [(('Lembutkan tanganmu, walau itu melemahkanmu.', 'Soften your hand, even if it weakens you.'), True),
          (('Tarik tanganmu agar ia tidak terluka.', 'Pull your hand away so they are not hurt.'), False),
          (('Suruh ia memakai sarung tangan.', 'Tell them to wear gloves.'), False)]),
        (('Ilusi ketiga: dua lembah terpisah tebing. Para pedagang mati mendaki.', 'Third illusion: two valleys parted by cliffs. Merchants die climbing.'),
         [(('Hancurkan tebingnya dengan tinju.', 'Smash the cliff with your fist.'), False),
          (('Berbaring dan menjadi jalan.', 'Lie down and become the road.'), True),
          (('Bangun benteng di puncaknya.', 'Build a fortress on top.'), False)])],
        ('Jadilah kuat seperti gunung, tapi jangan lupa: gunung terbaik punya jalan setapak.',
         'Be strong as a mountain, but remember: the best mountains have a path.')),

    # ---------------------------------------------------------------- LIFE
    ('life', 1): A('Cao Lu', ('Tabib Rumput Liar', 'Physician of Weeds'), [
        ('Aku tabib desa yang tidak punya uang untuk herba langka. Aku belajar menyembuhkan dengan rumput liar di tepi jalan.',
         'I was a village physician too poor for rare herbs. I learned to heal with the weeds by the roadside.'),
        ('Ketika wabah demam datang, aku mengobati tiga desa tanpa bayaran. Aku menemukan bahwa rumput yang diinjak semua orang '
         'menyimpan obat paling kuat.',
         'When the fever plague came, I treated three villages without pay. I found that the grass everyone trampled held the '
         'strongest cure.'),
        ('Pasien terakhir kuselamatkan, lalu aku sendiri jatuh sakit. Obatnya ada di tanganku, tapi aku memberikannya pada seorang '
         'anak. Kehidupan bukan milik yang menyimpannya; ia milik yang memberikannya.',
         'I saved my last patient, then fell ill myself. The cure was in my hand, but I gave it to a child. Life does not belong '
         'to those who keep it; it belongs to those who give it away.')], [
        (('Ilusi pertama: seorang kaya menawarkan emas agar kau mengobatinya lebih dulu dari orang miskin yang lebih sakit.',
          'First illusion: a rich man offers gold to be treated before a poor man who is sicker.'),
         [(('Obati yang lebih sakit lebih dulu.', 'Treat the sicker one first.'), True),
          (('Terima emas; bisa beli herba untuk semua.', 'Take the gold; it buys herbs for everyone.'), False),
          (('Obati keduanya setengah-setengah.', 'Treat both halfway.'), False)]),
        (('Ilusi kedua: rumput yang kau butuhkan tumbuh di halaman orang yang membencimu.', 'Second illusion: the grass you need grows in the yard of someone who hates you.'),
         [(('Cabut diam-diam malam hari.', 'Pull it secretly at night.'), False),
          (('Ketuk pintunya dan minta dengan jujur.', 'Knock on the door and ask honestly.'), True),
          (('Cari rumput lain yang kurang manjur.', 'Find a weaker grass instead.'), False)]),
        (('Ilusi ketiga: obat terakhir di tanganmu. Kau dan seorang anak sama-sama sakit.', 'Third illusion: the last cure in your hand. You and a child are both ill.'),
         [(('Minum sendiri; tabib harus hidup untuk menolong lebih banyak.', 'Drink it; a physician must live to help more.'), False),
          (('Berikan pada anak itu.', 'Give it to the child.'), True),
          (('Bagi dua.', 'Split it.'), False)])],
        ('Tunduklah kalau melihat rumput di tepi jalan. Ia mungkin menyelamatkan nyawa seseorang hari ini.',
         'Bow when you see the grass by the road. It may save someone\'s life today.')),
    ('life', 2): A('Mu Qing', ('Hutan yang Bernapas', 'The Breathing Forest'), [
        ('Aku prajurit dalam perang tujuh sekte. Padang tempatku bertempur menjadi tanah mati; tidak ada yang tumbuh di atas darah.',
         'I was a soldier in the war of the seven sects. The field where I fought became dead ground; nothing grows on blood.'),
        ('Setelah perang aku kembali ke padang itu dengan sekantong benih. Setiap pohon yang kutanam kuberi nama seorang yang '
         'gugur di sana, kawan maupun lawan.',
         'After the war I returned to that field with a bag of seeds. Every tree I planted I named after someone who fell there, '
         'friend or foe.'),
        ('Dua ratus tahun kemudian padang itu menjadi hutan yang bernapas. Anak-anak dari tujuh sekte bermain di bawahnya. Hidup '
         'yang tumbuh dari kematian adalah satu-satunya kemenangan yang tidak memiliki pihak kalah.',
         'Two hundred years later the field was a breathing forest. Children of all seven sects played beneath it. Life grown from '
         'death is the only victory with no losing side.')], [
        (('Ilusi pertama: kau menanam pohon untuk kawanmu. Seseorang memintamu menanam juga untuk lawan yang membunuhnya.',
          'First illusion: you plant a tree for your comrade. Someone asks you to plant one for the enemy who killed him too.'),
         [(('Tanam juga untuk lawan itu.', 'Plant one for that enemy too.'), True),
          (('Tolak; lawan tidak pantas dikenang.', 'Refuse; enemies deserve no memory.'), False),
          (('Tanam, lalu tebang diam-diam.', 'Plant it, then cut it down secretly.'), False)]),
        (('Ilusi kedua: kemarau. Pohon-pohon mudamu sekarat.', 'Second illusion: drought. Your young trees are dying.'),
         [(('Pindahkan ke tempat yang lebih basah.', 'Move them somewhere wetter.'), False),
          (('Angkut air dari sungai, satu ember demi satu ember.', 'Carry water from the river, one bucket at a time.'), True),
          (('Biarkan; yang kuat akan bertahan.', 'Let them be; the strong will survive.'), False)]),
        (('Ilusi ketiga: seorang raja ingin menebang hutanmu untuk membangun istana.', 'Third illusion: a king wants to fell your forest to build a palace.'),
         [(('Bunuh raja itu.', 'Kill the king.'), False),
          (('Ajak ia berjalan di hutan dan sebutkan nama setiap pohon.', 'Walk him through the forest and name every tree.'), True),
          (('Jual separuh hutan padanya.', 'Sell him half the forest.'), False)])],
        ('Tanamlah sesuatu di setiap tempat yang pernah kau lukai.', 'Plant something in every place you have ever wounded.')),
    ('life', 3): A('Chun Shen', ('Dewi Musim Semi', 'Goddess of Spring'), [
        ('Aku bisa menghidupkan yang layu. Satu sentuhan, dan ladang mati berbunga, pohon tumbang berdiri, sungai kering mengalir.',
         'I could revive the withered. One touch, and dead fields bloomed, fallen trees stood, dry rivers ran.'),
        ('Orang-orang datang membawa jenazah keluarga mereka. "Hidupkan mereka," kata mereka. Aku bisa. Tetapi yang kembali bukan '
         'lagi orang yang mereka cintai.',
         'People came carrying their dead. "Bring them back," they said. I could. But what returned was no longer the one they loved.'),
        ('Aku berhenti menghidupkan yang mati dan mulai mengajari orang berduka. Musim semi bukan musim tanpa kematian; musim semi '
         'adalah musim yang tumbuh dari musim dingin.',
         'I stopped reviving the dead and began teaching people to grieve. Spring is not a season without death; spring is the '
         'season that grows out of winter.')], [
        (('Ilusi pertama: seorang ibu membawa jenazah anaknya dan memohon.', 'First illusion: a mother carries her dead child and begs.'),
         [(('Hidupkan anak itu, apa pun yang kembali.', 'Revive the child, whatever returns.'), False),
          (('Duduk bersamanya dan menangis bersama.', 'Sit with her and weep together.'), True),
          (('Usir ia; aku tidak menghidupkan yang mati.', 'Send her away; I do not raise the dead.'), False)]),
        (('Ilusi kedua: kau bisa menghapus musim dingin selamanya.', 'Second illusion: you could erase winter forever.'),
         [(('Hapus; tak ada lagi yang mati kedinginan.', 'Erase it; no one will freeze again.'), False),
          (('Biarkan musim dingin; tanpanya musim semi tak berarti.', 'Let winter be; without it spring means nothing.'), True),
          (('Hapus separuhnya.', 'Erase half of it.'), False)]),
        (('Ilusi ketiga: tubuhmu sendiri mulai layu karena usia.', 'Third illusion: your own body begins to wither with age.'),
         [(('Hidupkan dirimu lagi.', 'Revive yourself again.'), False),
          (('Biarkan, dan tanam bunga di tempat kau akan berbaring.', 'Let it be, and plant flowers where you will lie.'), True),
          (('Curi hidup dari pohon-pohon.', 'Steal life from the trees.'), False)])],
        ('Kehidupan yang kuwariskan padamu termasuk kematiannya. Peganglah keduanya dengan tangan yang sama lembutnya.',
         'The life I leave you includes its death. Hold both with equally gentle hands.')),

    # ---------------------------------------------------------------- SHADOW AND WIND
    ('shadow', 1): A('Feng Xiao', ('Angin Kecil', 'Little Wind'), [
        ('Aku pengantar surat di masa perang, ketika keluarga-keluarga terpisah oleh garis depan. Tak ada kuda yang bisa '
         'melewatinya; aku berlari.',
         'I carried letters in wartime, when families were split by the front line. No horse could cross it; I ran.'),
        ('Aku belajar berlari bersama angin dan bersembunyi di bayangan pohon. Dua belas tahun aku membawa sebelas ribu surat. '
         'Tak satu pun hilang.',
         'I learned to run with the wind and hide in the shadows of trees. For twelve years I carried eleven thousand letters. Not '
         'one was lost.'),
        ('Surat terakhir yang kubawa adalah surat untukku sendiri, dari ibuku, tapi aku tiba terlambat. Aku tidak menyesal. '
         'Sebelas ribu ibu lain menerima surat tepat waktu.',
         'The last letter I carried was for me, from my mother, but I arrived too late. I have no regrets. Eleven thousand other '
         'mothers got their letters in time.')], [
        (('Ilusi pertama: kau membawa dua surat. Satu untuk jenderal, satu untuk seorang ibu. Hanya satu bisa sampai.',
          'First illusion: you carry two letters. One for a general, one for a mother. Only one can arrive.'),
         [(('Surat jenderal; perang lebih penting.', 'The general\'s; the war matters more.'), False),
          (('Surat sang ibu.', 'The mother\'s.'), True),
          (('Buang keduanya.', 'Throw both away.'), False)]),
        (('Ilusi kedua: penjaga menawarkan uang untuk membaca surat-surat yang kau bawa.', 'Second illusion: a guard offers money to read the letters you carry.'),
         [(('Tolak dan lari lewat bayangan.', 'Refuse and run through the shadows.'), True),
          (('Biarkan ia membaca satu saja.', 'Let him read just one.'), False),
          (('Jual semua surat.', 'Sell every letter.'), False)]),
        (('Ilusi ketiga: kau terlambat untuk suratmu sendiri.', 'Third illusion: you arrive too late for your own letter.'),
         [(('Berhenti mengantar surat selamanya.', 'Stop carrying letters forever.'), False),
          (('Simpan surat itu, lalu antar surat berikutnya.', 'Keep that letter, then carry the next one.'), True),
          (('Kutuk angin yang terlalu lambat.', 'Curse the wind for being slow.'), False)])],
        ('Angin tidak pernah berhenti untuk dirinya sendiri. Itulah sebabnya ia sampai ke mana-mana.',
         'The wind never stops for itself. That is why it reaches everywhere.')),
    ('shadow', 2): A('Ye Ying', ('Bayangan Malam', 'Night Shade'), [
        ('Aku pembunuh bayaran terbaik di Pasar Gelap. Tak pernah gagal, tak pernah terlihat, tak pernah bertanya.',
         'I was the best assassin of the Black Market. Never failed, never seen, never asked.'),
        ('Sampai suatu malam kontraknya adalah seorang anak, pewaris klan kecil. Untuk pertama kalinya aku bertanya: kenapa? '
         'Lalu aku membawa anak itu lari.',
         'Until one night the contract was a child, heir of a small clan. For the first time I asked: why? Then I took the child and ran.'),
        ('Dua puluh tahun bayangan-bayangan lain memburuku. Aku menjaga anak itu sampai ia dewasa, dari balik bayangan, tanpa ia '
         'tahu siapa aku. Bayangan yang paling gelap pun bisa menjadi tempat berteduh.',
         'For twenty years other shadows hunted me. I guarded that child until adulthood, from the shadows, without them ever '
         'knowing who I was. Even the darkest shadow can be a shelter.')], [
        (('Ilusi pertama: kontrak berikutnya adalah seorang anak yang sedang tidur.', 'First illusion: the next contract is a sleeping child.'),
         [(('Selesaikan; kontrak adalah kontrak.', 'Finish it; a contract is a contract.'), False),
          (('Tanyakan kenapa, lalu bawa anak itu pergi.', 'Ask why, then carry the child away.'), True),
          (('Kembalikan uangnya dan pergi.', 'Return the money and leave.'), False)]),
        (('Ilusi kedua: anak itu sudah dewasa dan ingin tahu siapa yang melindunginya.', 'Second illusion: the child is grown and wants to know who protected them.'),
         [(('Tunjukkan dirimu dan minta terima kasih.', 'Show yourself and ask for thanks.'), False),
          (('Tetap di bayangan.', 'Stay in the shadows.'), True),
          (('Pergi selamanya tanpa kabar.', 'Leave forever without a word.'), False)]),
        (('Ilusi ketiga: bayangan lain menawarkan perdamaian asal kau kembali ke Pasar Gelap.', 'Third illusion: another shadow offers peace if you return to the Black Market.'),
         [(('Kembali; perburuan ini melelahkan.', 'Return; this hunt is exhausting.'), False),
          (('Tolak. Bayanganku sekarang untuk berteduh.', 'Refuse. My shadow is for shelter now.'), True),
          (('Pura-pura setuju lalu kabur.', 'Pretend to agree, then flee.'), False)])],
        ('Bersembunyilah kalau perlu, tapi pastikan yang berlindung di bayanganmu bisa tidur nyenyak.',
         'Hide when you must, but make sure whoever shelters in your shadow can sleep soundly.')),
    ('shadow', 3): A('Wu Ying', ('Tanpa Bayangan', 'The Shadowless'), [
        ('Aku menguasai angin dan bayangan sampai bisa berjalan di mana saja tanpa terlihat. Aku memakai kekuatan itu untuk '
         'mencuri pujian: kubiarkan orang tahu siapa yang menolong mereka.',
         'I mastered wind and shadow until I could walk anywhere unseen. I used that power to steal praise: I let people know who '
         'had helped them.'),
        ('Semakin banyak pujian, semakin gelap bayanganku. Suatu hari bayanganku sendiri berdiri dan mencoba menggantikanku. Ia '
         'adalah semua keinginanku untuk dilihat.',
         'The more praise, the darker my shadow grew. One day my own shadow stood up and tried to replace me. It was all my '
         'longing to be seen.'),
        ('Aku mengalahkannya bukan dengan pedang, tetapi dengan menolong seribu orang tanpa ada yang tahu. Bayanganku menipis dan '
         'hilang. Kekuatan angin adalah menggerakkan dunia tanpa terlihat.',
         'I defeated it not with a sword, but by helping a thousand people with no one knowing. My shadow thinned and vanished. '
         'The power of the wind is moving the world unseen.')], [
        (('Ilusi pertama: kau baru menyelamatkan sebuah desa. Mereka bertanya siapa penolongnya.',
          'First illusion: you have just saved a village. They ask who their saviour was.'),
         [(('Sebutkan namamu.', 'Say your name.'), False),
          (('Pergi bersama angin.', 'Leave with the wind.'), True),
          (('Tinggalkan tanda agar mereka ingat.', 'Leave a sign so they remember.'), False)]),
        (('Ilusi kedua: bayanganmu sendiri berdiri dan menantangmu.', 'Second illusion: your own shadow stands up and challenges you.'),
         [(('Tebas bayangan itu.', 'Cut the shadow down.'), False),
          (('Akui bahwa ia adalah keinginanmu untuk dilihat.', 'Admit it is your longing to be seen.'), True),
          (('Lari dari bayanganmu.', 'Run from your shadow.'), False)]),
        (('Ilusi ketiga: seseorang lain mendapat pujian atas pertolonganmu.', 'Third illusion: someone else is praised for your deed.'),
         [(('Biarkan ia menerimanya.', 'Let them have it.'), True),
          (('Bongkar kebohongannya.', 'Expose the lie.'), False),
          (('Tuntut separuh pujian.', 'Demand half the praise.'), False)])],
        ('Bergeraklah seperti angin: semua orang merasakanmu, tak seorang pun perlu melihatmu.',
         'Move like the wind: everyone feels you, no one needs to see you.')),

    # ---------------------------------------------------------------- SPACE
    ('space', 1): A('Lu Zhi', ('Penjaga Pintu Gunung', 'Keeper of the Mountain Door'), [
        ('Aku penjaga gerbang sebuah sekte kecil. Pekerjaanku membuka dan menutup pintu, dan aku melakukannya empat puluh tahun.',
         'I was the gatekeeper of a small sect. My work was opening and closing a door, and I did it for forty years.'),
        ('Suatu malam seorang anak tersesat di balik gunung, dan badai menutup semua jalan. Aku memandangi pintuku dan bertanya: '
         'kenapa pintu hanya bisa membuka ke satu tempat?',
         'One night a child was lost beyond the mountain, and a storm closed every road. I stared at my door and asked: why can a '
         'door open onto only one place?'),
        ('Aku membuka pintu itu, dan di baliknya adalah tempat anak itu menangis. Ruang hanya lipatan kertas bagi orang yang '
         'benar-benar ingin sampai.',
         'I opened that door, and behind it was the place where the child was crying. Space is only a fold of paper to someone '
         'who truly wishes to arrive.')], [
        (('Ilusi pertama: badai menutup semua jalan, dan seorang anak menangis di balik gunung.',
          'First illusion: a storm closes every road, and a child cries beyond the mountain.'),
         [(('Tunggu badai reda.', 'Wait for the storm to pass.'), False),
          (('Buka pintu dengan hati yang ingin sampai.', 'Open the door with a heart that wishes to arrive.'), True),
          (('Kirim burung pembawa pesan.', 'Send a messenger bird.'), False)]),
        (('Ilusi kedua: kau bisa membuka pintu ke perbendaharaan sekte.', 'Second illusion: you could open a door into the sect treasury.'),
         [(('Buka; tidak ada yang akan tahu.', 'Open it; no one will know.'), False),
          (('Pintu adalah amanah, bukan jalan pintas.', 'A door is a trust, not a shortcut.'), True),
          (('Buka sedikit saja.', 'Open it just a crack.'), False)]),
        (('Ilusi ketiga: sekte menawarimu menjadi tetua, meninggalkan gerbang.', 'Third illusion: the sect offers to make you an elder, leaving the gate.'),
         [(('Terima; aku pantas.', 'Accept; I deserve it.'), False),
          (('Tetap di gerbang; masih ada yang tersesat di luar.', 'Stay at the gate; someone is still lost outside.'), True),
          (('Minta gerbang yang lebih besar.', 'Ask for a bigger gate.'), False)])],
        ('Setiap pintu yang kau buka, ingatlah ke mana orang yang menunggu di baliknya ingin pulang.',
         'For every door you open, remember where the one waiting behind it wants to go home.')),
    ('space', 2): A('Xu Kong', ('Penjaga Celah', 'Keeper of the Rift'), [
        ('Ketika perang membakar provinsi selatan, aku membuka sebuah dunia saku, celah kecil di antara ruang, dan menyembunyikan '
         'tiga ribu pengungsi di dalamnya.',
         'When war burned the southern province, I opened a pocket world, a small rift between spaces, and hid three thousand '
         'refugees inside it.'),
        ('Menjaga celah itu tetap terbuka menghabiskan qi-ku setiap hari. Aku tidak bisa bergerak, tidak bisa naik ranah, tidak '
         'bisa pergi. Selama sembilan belas tahun aku menjadi pintu yang tak boleh tertutup.',
         'Keeping the rift open drained my qi every day. I could not move, could not advance, could not leave. For nineteen years '
         'I was a door that must not close.'),
        ('Pengungsi terakhir keluar ketika perang usai. Celah itu menutup bersamaku di dalamnya. Ruang terluas bukan yang kau '
         'kuasai, tetapi yang kau berikan.',
         'The last refugee walked out when the war ended. The rift closed with me inside it. The widest space is not the one you '
         'rule, but the one you give away.')], [
        (('Ilusi pertama: tentara datang. Celahmu hanya muat untuk separuh pengungsi.', 'First illusion: soldiers come. Your rift holds only half the refugees.'),
         [(('Pilih yang paling berguna.', 'Choose the most useful ones.'), False),
          (('Paksa celahnya lebih lebar, walau qi-mu robek.', 'Force the rift wider, though your qi tears.'), True),
          (('Tutup celah dan lari sendiri.', 'Close the rift and flee alone.'), False)]),
        (('Ilusi kedua: sebelas tahun lewat. Seorang tetua menawarkan obat agar kau bisa meninggalkan celah sebentar.',
          'Second illusion: eleven years pass. An elder offers medicine so you can leave the rift for a while.'),
         [(('Pergi sebentar; mereka akan baik-baik saja.', 'Leave for a while; they will be fine.'), False),
          (('Tetap. Pintu yang ditinggal bisa tertutup.', 'Stay. A door left behind may close.'), True),
          (('Suruh pengungsi menjaga diri sendiri.', 'Tell the refugees to fend for themselves.'), False)]),
        (('Ilusi ketiga: perang usai. Celah akan menutup bersamamu di dalamnya.', 'Third illusion: the war is over. The rift will close with you inside.'),
         [(('Keluar lebih dulu.', 'Get out first.'), False),
          (('Biarkan pengungsi terakhir keluar dulu.', 'Let the last refugee out first.'), True),
          (('Tarik seseorang untuk menemanimu.', 'Pull someone in to keep you company.'), False)])],
        ('Kalau kau bisa membuka ruang, bukalah untuk mereka yang tidak punya tempat.',
         'If you can open space, open it for those who have no place.')),
    ('space', 3): A('Tai Xu', ('Kekosongan Agung', 'The Great Void'), [
        ('Aku bisa melangkah ke mana pun dalam satu langkah: puncak gunung tertinggi, dasar laut terdalam, ujung dunia. Jarak tidak '
         'ada artinya bagiku.',
         'I could step anywhere in a single stride: the highest peak, the deepest sea, the edge of the world. Distance meant '
         'nothing to me.'),
        ('Karena itu aku tidak pernah tinggal di mana pun. Aku melihat seribu kota tetapi tidak mengenal satu tetangga pun. Aku '
         'berada di mana-mana, dan karena itu tidak berada di mana-mana.',
         'So I never stayed anywhere. I saw a thousand cities but knew not one neighbour. I was everywhere, and therefore nowhere.'),
        ('Di akhir hidupku aku memilih satu desa kecil dan tinggal di sana tiga puluh tahun tanpa melangkah keluar. Untuk pertama '
         'kalinya aku tiba. Ruang tertinggi bukan menguasai jarak, tetapi memilih tempat untuk berdiri.',
         'At the end of my life I chose one small village and lived there thirty years without stepping out. For the first time, '
         'I arrived. The highest space is not mastering distance, but choosing a place to stand.')], [
        (('Ilusi pertama: kau bisa berada di seribu tempat sekaligus.', 'First illusion: you can be in a thousand places at once.'),
         [(('Pilih seribu tempat.', 'Choose the thousand places.'), False),
          (('Pilih satu tempat, sepenuhnya.', 'Choose one place, completely.'), True),
          (('Pilih sepuluh, agar adil.', 'Choose ten, to be fair.'), False)]),
        (('Ilusi kedua: tetanggamu yang tua meminta tolong memperbaiki atap, padahal dunia memanggilmu.',
          'Second illusion: your old neighbour asks for help mending a roof, while the world calls you.'),
         [(('Perbaiki atapnya dulu.', 'Mend the roof first.'), True),
          (('Dunia lebih penting.', 'The world is more important.'), False),
          (('Pindahkan ia ke rumah lain.', 'Move him to another house.'), False)]),
        (('Ilusi ketiga: langit membuka gerbang ke alam abadi, tetapi desamu harus kau tinggalkan selamanya.',
          'Third illusion: heaven opens a gate to the immortal realm, but you must leave your village forever.'),
         [(('Melangkah masuk.', 'Step through.'), False),
          (('Tutup gerbang itu dengan hormat dan pulang untuk makan malam.', 'Close the gate politely and go home for supper.'), True),
          (('Bawa seluruh desa bersamamu.', 'Take the whole village with you.'), False)])],
        ('Kau akan bisa pergi ke mana saja. Jangan lupa pulang.', 'You will be able to go anywhere. Do not forget to come home.')),
}


def isi(g):
    D, N, P, T, Q = g.D, g.N, g.P, g.T, g.Q
    for dao in DAO:
        for tier in (1, 2, 3):
            a = LELUHUR[(dao, tier)]
            nama = a['nama']
            q = f'warisan_{dao}_{tier}'
            dn = DAO_NAMA[dao]
            Q(q, 'peran:sisa_jiwa', 'makam', (f'Warisan {nama}', f'The Legacy of {nama}'),
              (f'Sisa jiwa {nama}, "{a["gelar"][0]}", menjaga makam Dao {dn[0]} tingkat {tier}. Dengarkan kisahnya dan buktikan '
               f'bahwa kau layak mewarisinya.',
               f'The remnant soul of {nama}, "{a["gelar"][1]}", guards a tier {tier} tomb of the {dn[1]} Dao. Hear the story and '
               f'prove you are worthy of the legacy.'),
              [T('kisah', (f'Dengarkan kisah {nama} sampai selesai.', f'Hear the story of {nama} to its end.'), arah='gua/makam'),
               T('hati', ('Ujian Hati: tetaplah teguh melewati tiga ilusi.', 'Heart Trial: stay steadfast through three illusions.'), arah='gua/makam'),
               T('makam', ('Lewati ujian Kekuatan dan Pemahaman di ruang-ruang makam.', 'Pass the Strength and Comprehension trials in the tomb chambers.'),
                 tujuan=[f'ujian makam:{dao}:{tier}'], arah='gua/makam'),
               T('pamit', (f'Kembalilah kepada sisa jiwa {nama}.', f'Return to the remnant soul of {nama}.'),
                 tujuan=['bicara peran:sisa_jiwa'],
                 akibat=['gelar pewaris_leluhur', 'suara tempat.inheritance_pass', f'bendera {q}', f'efek minecraft:luck {6000 * tier} 0'],
                 hasil=a['pamit'])])
            kisah, ilusi = a['kisah'], a['ilusi']
            simpul = [
                N('mulai', (f'Akulah sisa jiwa {nama}, {a["gelar"][0]}. Tunjukkan bahwa kau layak mewarisi peninggalanku.',
                            f'I am the remnant soul of {nama}, {a["gelar"][1]}. Show me you are worthy of my legacy.'),
                  gestur='sikap_tangan_belakang', suara='tempat.inheritance', akibat=[f'quest_mulai {q}'], pilihan=[
                      P(('Ceritakan kisahmu.', 'Tell me your story.'), ke='k1', syarat=[f'quest {q} tahap kisah']),
                      P(('Aku siap menghadapi ujian Hati.', 'I am ready for the Heart Trial.'), ke='h1', syarat=[f'quest {q} tahap hati']),
                      P(('Apa yang menungguku di dalam?', 'What awaits me inside?'), ke='dalam', syarat=[f'quest {q} tahap makam']),
                      P(('(Membungkuk dan mundur)', '(Bow and step back)'))]),
                N('k1', kisah[0], gestur='cerita', pilihan=[P(('Lalu?', 'And then?'), ke='k2')]),
                N('k2', kisah[1], gestur='tatap_langit', pilihan=[P(('Bagaimana akhirnya?', 'How did it end?'), ke='k3')]),
                N('k3', kisah[2], gestur='hormat_dalam', pilihan=[
                    P(('Aku siap untuk ujian Hati.', 'I am ready for the Heart Trial.'), ke='h1', akibat=[f'quest_maju {q}']),
                    P(('Beri aku waktu untuk merenung.', 'Give me time to reflect.'), akibat=[f'quest_maju {q}'])]),
            ]
            for i, (adegan, pilihan) in enumerate(ilusi, 1):
                nxt = f'h{i + 1}' if i < 3 else 'teguh'
                pil = []
                for teks, benar in pilihan:
                    if benar:
                        pil.append(P(teks, ke=nxt, akibat=[f'quest_maju {q}'] if i == 3 else []))
                    else:
                        pil.append(P(teks, ke='goyah', akibat=['efek minecraft:nausea 6 0', 'efek minecraft:slowness 200 1']))
                simpul.append(N(f'h{i}', adegan, gestur='segel_tangan', pilihan=pil))
            simpul += [
                N('goyah', ('Hatimu goyah, dan ilusi itu runtuh di sekelilingmu. Tenangkan napasmu. Ujian ini menunggu selama yang kau perlukan.',
                            'Your heart wavers, and the illusion collapses around you. Calm your breath. This trial waits as long as you need.'),
                  gestur='hormat_dalam', pilihan=[
                      P(('Aku mau mencoba lagi.', 'I want to try again.'), ke='h1'),
                      P(('Nanti.', 'Later.'))]),
                N('teguh', ('Hatimu teguh. Dua ujian lagi menunggumu di ruang dalam: Kekuatan, dan Pemahaman.',
                            'Your heart holds. Two trials await you in the inner chambers: Strength, and Comprehension.'),
                  gestur='tunjuk'),
                N('dalam', ('Boneka penjaga menguji kekuatanmu, lempeng-lempeng glif menguji pemahamanmu. Keduanya hanya mengulangi '
                            'pertanyaan yang sudah kau jawab dengan hatimu.',
                            'Guardian puppets test your strength, glyph tablets test your understanding. Both only repeat the '
                            'question you already answered with your heart.'), gestur='bicara_tangan'),
            ]
            D(q, 'peran:sisa_jiwa', simpul, prioritas=30, syarat=[f'makam {dao} {tier}', f'!quest {q} selesai'])
            D(f'{q}_pamit', 'peran:sisa_jiwa', [
                N('mulai', a['pamit'], gestur='hormat_dalam', suara='tempat.inheritance_pass'),
            ], prioritas=30, syarat=[f'makam {dao} {tier}', f'quest {q} selesai'])
