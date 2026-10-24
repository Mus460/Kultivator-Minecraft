"""38ST content: Catatan Tempat, the place lore of DESIGN 38.S.7 (read by LoreTempat, fragment tempat_lore).

Ids follow LoreTempat.idUntuk: kota_besar (+ one per capital district), gilda.<alkemis|pandai|kultivator>, kampung,
kampung.klan, kompleks, gua.<kind>, situs.<kind>, jalan.<post kind>. A table (tabel) is one row picked per site and shown
before the paragraphs: a village's own small legend, a compound's founder tale, a hermit's reason for leaving the world.
The only %s fillers are sect names (isian sekte_situs / sekte_terdekat / sekte_iblis). Every entry that leans on a real
tale, place or object carries a Sumber line; the history of this world (900 years, seven sects) is game design.
Facts kept in line with the characters: Dan Chen's first formula ended the Yellow Leaf plague forty years ago, Lin Fei
lost his arm on the east wall in the Beast Tide twenty years ago, Ou Ye the Younger descends from Ou Ye's students,
House Qian is older than the city, Jing Xin's teacher saw his own demon in a Heart Mirror pool."""

UI = {
    'kultivator.lore.ui.baru': ('Catatan Tempat baru: %s', 'New place record: %s'),
    'kultivator.lore.ui.baca': ('[Baca catatan "%s"]', '[Read the record "%s"]'),
    'kultivator.lore.ui.judul': ('~ %s ~', '~ %s ~'),
    'kultivator.lore.ui.daftar': ('Catatan Tempat yang kau ketahui: %s dari %s', 'Place records you know: %s of %s'),
    'kultivator.lore.ui.butir': ('  - %s', '  - %s'),
    'kultivator.lore.ui.kosong': ('Kau belum mengetahui kisah tempat mana pun. Kunjungi kota, kampung, gua dan jalan; dengarkan orang bercerita.',
                                  'You know no place\'s tale yet. Visit cities, villages, caves and roads; listen when people talk.'),
    'kultivator.lore.ui.tidak_ada': ('Tidak ada catatan bernama "%s".', 'There is no record called "%s".'),
    'kultivator.lore.ui.belum': ('Kau belum mendengar kisah itu. Datangi tempatnya dulu.', 'You have not heard that tale yet. Visit the place first.'),
}


def isi(g):
    for key, pair in UI.items():
        g.tx('tempat_lore', key, pair)
    L = g.L

    # =========================================================================================== the capital
    L('kota_besar', ('Ibu Kota: Tanah Netral Tujuh Sekte', 'The Capital: Neutral Ground of the Seven Sects'), [
        ('Sembilan ratus tahun lalu dataran ini adalah medan perang. Tujuh sekte bertempur selama tiga generasi sampai sungai-sungainya '
         'berwarna karat dan tidak ada lagi petani yang berani menanam. Pada musim dingin tahun keempat puluh, para ketua sekte bertemu '
         'di reruntuhan sebuah pos jalan, karena hanya tempat itu yang tidak dimiliki siapa pun.',
         'Nine hundred years ago this plain was a battlefield. Seven sects fought for three generations until the rivers ran the colour '
         'of rust and no farmer dared to sow. In the winter of the fortieth year the sect masters met in the ruins of a road post, '
         'because it was the only place no one owned.'),
        ('Mereka tidak menandatangani perjanjian. Mereka mematahkan pedang masing-masing dan menanamnya di batu, lalu membangun tembok '
         'di sekeliling batu itu. Hukum pertama kota diukir di atas gerbang: "Di dalam tembok, pedang tidur." Kota ini tidak punya raja. '
         'Paviliun Pengawal Kota menjaganya atas nama semua sekte, dan Pangeran Penjaganya dipilih dewan gilda, bukan dilahirkan.',
         'They signed no treaty. They broke their own swords, planted them in the stone and raised a wall around it. The city\'s first '
         'law was carved above the gate: "Within the walls, swords sleep." The city has no king. The Pavilion of the City Guard keeps it '
         'in the name of every sect, and its Warden Prince is chosen by the guild council, not born.'),
        ('Karena tak satu sekte pun boleh memilikinya, semua orang datang ke sini: pedagang yang takut begal, alkemis yang butuh pembeli, '
         'murid buangan yang mencari kerja, dan kultivator tua yang lelah dengan dendam. Kota tumbuh dari satu alun-alun menjadi sembilan '
         'distrik di dalam tembok, dengan kampung dan lapangan kafilah yang menempel di luarnya seperti lumut di batu.',
         'Because no sect may own it, everyone comes here: merchants afraid of bandits, alchemists in need of buyers, expelled disciples '
         'looking for work, and old cultivators weary of grudges. The city grew from one square into nine districts within the walls, '
         'with a slum and a caravan field clinging to the outside like moss on a stone.'),
        ('Sumpah itu sudah dilanggar berkali-kali, dan setiap kali kota menagih darahnya. Namun setiap sekte tahu: siapa yang merebut ibu '
         'kota akan dimusuhi enam sekte lainnya sekaligus. Kedamaian di sini tidak lahir dari kebaikan, melainkan dari hitungan. '
         'Para pedagang bilang itulah jenis kedamaian yang paling awet.',
         'The oath has been broken many times, and each time the city has claimed its blood. Yet every sect knows that whoever seizes '
         'the capital will have the other six as enemies at once. The peace here was not born of kindness but of arithmetic. '
         'The merchants say that is the most durable kind.')],
        sumber=('Sumber: Terinspirasi dari kota-kota netral tempat semua sekte berdagang dan bertanding dalam novel xianxia, seperti Kota '
                'Tanah Tengah (Zhongzhou) dalam Battle Through the Heavens; sejarah sembilan ratus tahun dan sumpah tujuh sekte rancangan game.',
                'Source: Inspired by the neutral cities where every sect trades and competes in xianxia novels, such as the Central Plains '
                '(Zhongzhou) of Battle Through the Heavens; the nine-hundred-year history and the oath of the seven sects are game design.'))

    L('kota_besar.istana', ('Distrik Istana: Benteng yang Menjadi Rumah', 'The Palace District: A Fortress Turned Home'), [
        ('Istana Pangeran Penjaga dulu adalah benteng bersama tujuh sekte, tempat para ketua menyimpan sandera satu sama lain agar tidak '
         'ada yang berkhianat. Tujuh menara jaganya masih berdiri; masing-masing dibangun oleh satu sekte dengan batunya sendiri, sehingga '
         'tidak ada dua menara yang warnanya sama.',
         'The Warden Prince\'s palace was once the shared fortress of the seven sects, where the masters kept hostages from one another '
         'so no one would betray the rest. Its seven watchtowers still stand; each was raised by one sect with its own stone, so no two '
         'towers share a colour.'),
        ('Sandera terakhir dibebaskan empat ratus tahun lalu, dan ruang-ruang tahanannya kini menjadi balai audiensi. Tamu yang dihormati '
         'masih dijamu di aula yang sama tempat para sandera dulu makan, dengan mangkuk yang sama. Kata para menteri, itu untuk '
         'mengingatkan semua orang betapa dekatnya keramahan dengan ancaman.',
         'The last hostage was freed four hundred years ago, and the cells became audience halls. Honoured guests are still served in '
         'the hall where the hostages once ate, from the same bowls. The ministers say it reminds everyone how close hospitality '
         'sits to threat.'),
        ('Siapa pun yang masuk setelah genderang malam tanpa nama baik di kota ini akan ditahan, bahkan bila ia membawa surat sekte. '
         'Para pengawal istana tidak mengenal kawan atau lawan; mereka hanya mengenal jam.',
         'Anyone who enters after the night drum without standing in this city is held, even with a sect\'s letter in hand. The palace '
         'guards know no friend or foe; they know only the hour.')])

    L('kota_besar.cendekia', ('Distrik Cendekia: Rak Buku Lebih Kuat dari Tembok', 'The Scholars\' District: Shelves Stronger than Walls'), [
        ('Setiap sekte yang bersumpah di batu alun-alun wajib menitipkan salinan kitab dasarnya di Perpustakaan Agung. Rak-raknya kini '
         'menyimpan ilmu lebih dari empat puluh sekte, termasuk sekte yang sudah lama musnah. Tidak ada sekte yang bisa menyerang ibu kota '
         'tanpa membakar ilmunya sendiri.',
         'Every sect that swore upon the stone of the square must leave a copy of its founding manual in the Grand Library. Its shelves '
         'now hold the learning of more than forty sects, including sects long gone. No sect can attack the capital without burning '
         'its own knowledge.'),
        ('Para pustakawan bersumpah tidak membuka kitab yang mereka jaga kecuali untuk menyalin halaman yang lapuk. Ruang baca umum hanya '
         'menyimpan kitab fana: sejarah, obat-obatan, perbintangan, hukum. Ada kuliah harian bagi yang mau duduk diam; kata orang, '
         'pemahaman datang kepada yang sabar mendengar, bukan kepada yang pandai bertanya.',
         'The librarians swear never to open the books they guard except to recopy a crumbling page. The public reading room holds only '
         'mortal books: history, medicine, astronomy, law. There is a lecture every day for those who can sit still; people say '
         'insight comes to the patient listener, not the clever asker.'),
        ('Di ruang bawah tanah ada satu rak kosong yang tidak pernah diisi. Itu untuk sekte kedelapan, kata pustakawan tertua, "yang '
         'belum lahir dan pasti akan datang." Ia tidak pernah menjelaskan bagaimana ia tahu.',
         'In the cellar stands one empty shelf that has never been filled. It is for the eighth sect, says the eldest librarian, "which '
         'is not yet born and will surely come." He has never explained how he knows.')],
        sumber=('Sumber: Terinspirasi dari Siku Quanshu, kumpulan kitab terbesar dinasti Qing yang disalin tujuh kali dan disimpan di tujuh '
                'paviliun perpustakaan; perpustakaan dan sumpahnya rancangan game.',
                'Source: Inspired by the Siku Quanshu, the great Qing-dynasty compilation that was copied seven times and kept in seven '
                'library pavilions; this library and its oath are game design.'))

    L('kota_besar.awan', ('Distrik Awan: Dermaga untuk Langit', 'The Cloud District: A Harbour for the Sky'), [
        ('Dua ratus tahun lalu sebuah kapal roh pertama kali turun di atas ibu kota, dikemudikan seorang tetua yang mabuk dan sangat beruntung. '
         'Kapal itu tersangkut di atap rumah seorang penjual kain. Dewan gilda membeli rumah itu, merobohkannya dan membangun dermaga '
         'di tempat yang sama, karena "langit sudah memilih".',
         'Two hundred years ago the first spirit ship came down over the capital, steered by a drunken and very lucky elder. It caught on '
         'the roof of a cloth seller\'s house. The guild council bought the house, pulled it down and built a dock on the same spot, '
         'because "the sky had chosen".'),
        ('Kini Dermaga Langit melayani perahu awan ke tempat-tempat yang jauh, dan para nakhoda awan menjadi kaum tersendiri: berkulit '
         'gelap karena matahari tinggi, hemat kata, dan selalu tahu dari arah mana angin roh akan bertiup besok. Penjual kain itu, '
         'kata orang, menjadi kaya dan tidak pernah lagi tidur di lantai atas.',
         'Today the Sky Dock serves cloud boats to distant places, and the cloud captains have become a people of their own: tanned by '
         'the high sun, sparing with words, always knowing where the spirit wind will blow tomorrow. The cloth seller, people say, '
         'grew rich and never slept upstairs again.')])

    L('kota_besar.gilda', ('Distrik Gilda: Tiga Gilda, Satu Pertengkaran', 'The Guild District: Three Guilds, One Quarrel'), [
        ('Gilda Alkemis, Gilda Pandai Pusaka dan Gilda Kultivator berdiri bersama kota, dan mereka sudah bertengkar sejak hari pertama: '
         'siapa yang paling berjasa, siapa yang membayar paling banyak, siapa yang berhak atas sumur di tengah distrik. Sumur itu masih '
         'ada, dikelilingi tiga pagar berbeda.',
         'The Alchemist Guild, the Forge Guild and the Cultivators\' Guild rose with the city, and they have quarrelled since the first '
         'day: who served best, who pays most, who owns the well in the middle of the district. The well is still there, ringed by '
         'three different fences.'),
        ('Pertengkaran itulah yang menjaga kota. Dewan gilda memilih Pangeran Penjaga, dan karena tiga gilda tidak pernah setuju pada '
         'satu orang dengan mudah, tidak ada pangeran yang bisa berkuasa terlalu lama tanpa disukai rakyat. Para pedagang menyebutnya '
         '"timbangan tiga kaki": goyah, tetapi tidak pernah roboh.',
         'That quarrel is what keeps the city. The guild council chooses the Warden Prince, and since the three guilds never easily agree '
         'on one man, no prince can rule too long without the people\'s favour. The merchants call it "the three-legged scale": '
         'wobbly, but it never falls.')])

    L('kota_besar.pusat', ('Distrik Pusat: Tugu Pendiri', 'The Central District: The Founders\' Monument'), [
        ('Di tengah Alun-alun Agung berdiri Tugu Pendiri: sebongkah batu hitam setinggi tiga orang dengan tujuh pedang patah tertanam '
         'di dalamnya. Pedang-pedang itu tidak berkarat, meski hujan sembilan ratus tahun sudah membasahinya. Para alkemis bilang itu '
         'karena besi rohnya; para biksu bilang itu karena sumpahnya.',
         'In the middle of the Grand Square stands the Founders\' Monument: a black boulder three men tall with seven broken swords set '
         'into it. The swords do not rust, though nine hundred years of rain have soaked them. The alchemists say it is their spirit '
         'iron; the monks say it is the oath.'),
        ('Di sisi utara alun-alun berdiri Yamen, tempat hakim kota menimbang perkara di bawah tiga hukum: pedang tidur di dalam tembok, '
         'utang dibayar dan janji ditepati, gerbang tertutup setelah genderang malam. Papan buronan di depannya jarang kosong. '
         'Kata orang, pelukis Yamen tidak pernah salah menggambar wajah.',
         'On the north side of the square stands the Yamen, where the city magistrate weighs cases under the three laws: swords sleep '
         'within the walls, debts are paid and promises kept, the gates close after the night drum. The wanted board before it is '
         'rarely empty. People say the Yamen painter has never drawn a face wrong.'),
        ('Pada setiap ulang tahun sumpah, para wakil sekte meletakkan sebatang dupa di kaki tugu. Bila ada sekte yang tidak datang, '
         'semua orang di kota tahu bahwa tahun itu akan berat.',
         'On every anniversary of the oath the sect envoys lay a stick of incense at the foot of the monument. When a sect fails to come, '
         'everyone in the city knows the year will be hard.')])

    L('kota_besar.pasar', ('Pasar Timur: Rumah Uang yang Lebih Tua dari Kota', 'The East Market: A Money House Older than the City'), [
        ('Sebelum ada tembok, sudah ada Rumah Qian. Keluarga penukar batu itu berdagang di pos jalan tempat tujuh sekte kelak berdamai, '
         'dan mereka meminjamkan batu roh kepada ketiga pihak sekaligus selama perang. Ketika perdamaian datang, semua sekte berutang '
         'kepada mereka, dan tidak ada yang berani menyuruh mereka pindah.',
         'Before there was a wall there was House Qian. The family of stone changers traded at the road post where the seven sects '
         'would later make peace, and through the war they lent spirit stones to all sides at once. When peace came every sect was in '
         'their debt, and none dared tell them to move.'),
        ('Pasar Timur tumbuh di sekeliling Rumah Uang seperti kota tumbuh di sekeliling sumur. Setiap malam pasar malam menyalakan '
         'lenteranya, dan setiap tujuh hari Balai Lelang memanggil para kolektor dari seluruh dunia. Pedagang bilang di sini segala '
         'sesuatu ada harganya; hanya waktu yang tidak bisa ditawar.',
         'The East Market grew around the Money House the way a town grows around a well. Every night the night market lights its lanterns, '
         'and every seventh day the Auction House calls collectors from across the world. Merchants say everything here has a price; '
         'only time cannot be haggled.')],
        sumber=('Sumber: Terinspirasi dari Pasar Timur dan Pasar Barat Chang\'an dinasti Tang serta rumah-rumah kiriman uang (piaohao) '
                'Shanxi dinasti Qing; Rumah Qian dan sejarahnya rancangan game.',
                'Source: Inspired by the East and West Markets of Tang-dynasty Chang\'an and the Shanxi remittance houses (piaohao) of the '
                'Qing dynasty; House Qian and its history are game design.'))

    L('kota_besar.hunian_barat', ('Hunian Barat: Rumah-Rumah Bersekat', 'The West Residences: Houses Behind Walls'), [
        ('Di petak barat tinggal para tukang, juru tulis, pelayan gilda dan keluarga kultivator yang memilih hidup fana. Rumahnya '
         'berhalaman empat sisi, dindingnya tinggi dan pintunya menghadap selatan; seorang anak bisa tumbuh besar di sini tanpa pernah '
         'melihat pedang terhunus.',
         'The western ward houses craftsmen, scribes, guild servants and cultivator families who chose a mortal life. The houses face '
         'inward around four-sided courtyards, their walls high and their doors turned south; a child can grow up here without ever '
         'seeing a drawn blade.'),
        ('Setiap gang punya kepala gang yang mencatat siapa pindah dan siapa lahir. Kata para ibu, mereka lebih takut pada kepala gang '
         'daripada pada hakim, karena hakim hanya tahu hukum, sedangkan kepala gang tahu semuanya.',
         'Each lane has a lane head who records who moves in and who is born. The mothers say they fear the lane head more than the '
         'magistrate, because the magistrate knows only the law and the lane head knows everything.')])

    L('kota_besar.gerbang', ('Distrik Gerbang: Penginapan dan Lentera', 'The Gate District: Inns and Lanterns'), [
        ('Gerbang selatan adalah mulut kota. Di belakangnya berderet penginapan, kedai teh dan Jalan Lentera, tempat para pendatang '
         'pertama kali mencium bau kota: teh, keringat kuda dan dupa. Penjaga gerbang konon bisa menebak asal seseorang dari debu di '
         'sepatunya.',
         'The south gate is the city\'s mouth. Behind it run inns, teahouses and Lantern Street, where newcomers first smell the city: '
         'tea, horse sweat and incense. The gate guards, people say, can tell where someone comes from by the dust on their shoes.'),
        ('Genderang di menara gerbang dipukul dua belas kali setiap malam. Setelah pukulan terakhir, gerbang menutup, dan yang tertinggal '
         'di luar harus memohon kepada penjaga atau tidur di Lapangan Kafilah. Banyak kisah cinta kota ini dimulai di bawah gerbang '
         'yang baru saja tertutup.',
         'The drum on the gate tower is struck twelve times each night. After the last beat the gate closes, and those left outside must '
         'plead with the guard or sleep in the Caravan Field. Many of the city\'s love stories began beneath a gate that had just shut.')])

    L('kota_besar.arena', ('Arena Agung: Adu, Bukan Perang', 'The Grand Arena: A Bout, Not a War'), [
        ('Seratus tahun setelah sumpah, dua sekte hampir berperang lagi karena sebuah urat roh. Pangeran Penjaga saat itu menawarkan '
         'jalan lain: masing-masing sekte mengirim satu juara, dan yang kalah melepaskan klaimnya. Pertarungan itu ditonton seluruh kota, '
         'dan kedua sekte pulang tanpa ada yang mati.',
         'A hundred years after the oath two sects nearly went to war again over a spirit vein. The Warden Prince of the day offered another '
         'way: each sect would send one champion, and the loser would give up its claim. The whole city watched the bout, and both sects '
         'went home with no one dead.'),
        ('Sejak itu sengketa sekte diselesaikan di panggung arena, bukan di medan perang. Turnamen Agung Antar-Sekte lahir dari kebiasaan '
         'itu. Penonton bersorak, bandar bertaruh, dan para tetua diam-diam lega: setiap juara yang kalah di sini adalah seratus murid '
         'yang tidak mati di luar sana.',
         'Since then sect disputes have been settled on the arena stage, not the battlefield. The Grand Inter-Sect Tournament grew from that '
         'custom. The crowd cheers, the bookmakers bet, and the elders are quietly relieved: every champion who loses here is a hundred '
         'disciples who do not die out there.')],
        sumber=('Sumber: Terinspirasi dari lei tai, panggung tinggi tempat para pendekar Tiongkok beradu untuk menyelesaikan sengketa atau '
                'menguji ilmu; Arena Agung dan sejarahnya rancangan game.',
                'Source: Inspired by the lei tai, the raised platform on which Chinese martial artists fought to settle disputes or test '
                'their art; the Grand Arena and its history are game design.'))

    L('kota_besar.kumuh', ('Kampung Kumuh: Mereka yang Datang Setelah Binatang', 'The Slums: Those Who Came After the Beasts'), [
        ('Di luar tembok timur, gubuk-gubuk berdesakan seperti sarang burung. Penghuninya kebanyakan pengungsi dari desa-desa yang diserbu '
         'binatang roh; mereka datang ke ibu kota karena tembok, lalu mendapati tembok itu tidak dibangun untuk mereka.',
         'Outside the east wall the shacks crowd together like birds\' nests. Most of those who live there fled villages overrun by spirit '
         'beasts; they came to the capital for its walls, then found the walls were not built for them.'),
        ('Kampung Kumuh punya hukumnya sendiri. Tidak ada yang mencuri dari dapur umum, tidak ada yang melaporkan tetangga kepada penjaga, '
         'dan setiap orang tahu bahwa pengemis tua yang tidur di dekat sumur bukan pengemis biasa, meski tak ada yang pernah melihatnya '
         'melakukan sesuatu yang luar biasa.',
         'The slums have their own law. No one steals from the common kitchen, no one reports a neighbour to the guards, and everyone knows '
         'the old beggar who sleeps by the well is no ordinary beggar, though no one has ever seen him do anything remarkable.'),
        ('Di bawah rumah gadai di ujung gang, kata orang, ada pasar yang hanya buka saat lilin dimatikan. Penjaga tahu, tetapi penjaga '
         'juga perlu membeli obat yang tidak dijual di Pasar Timur.',
         'Beneath the pawnshop at the end of the lane, people say, there is a market that opens only when the candles go out. The guards '
         'know, but the guards also need medicine the East Market does not sell.')])

    L('kota_besar.luar', ('Lapangan Kafilah: Kota di Luar Kota', 'The Caravan Field: A Town Outside the Town'), [
        ('Di selatan gerbang membentang lapangan tempat kafilah berkemah, kuda diberi minum dan muatan ditimbang sebelum pajak gerbang '
         'dibayar. Tugu jarak di tepinya mencatat berapa li ke setiap kota besar yang pernah dikunjungi kafilah dari sini.',
         'South of the gate spreads the field where caravans camp, horses drink and loads are weighed before the gate tax is paid. The '
         'distance post at its edge records how many li it is to every great town a caravan from here has ever reached.'),
        ('Di Paviliun Perpisahan, orang-orang mengantar kerabat yang berangkat jauh dengan secawan arak dan sebatang ranting willow. '
         'Ranting itu ditanam di tepi lapangan; kini barisan willow tua di sana adalah ribuan perpisahan yang tumbuh menjadi pohon.',
         'At the Farewell Pavilion people see off relatives on long journeys with a cup of wine and a willow sprig. The sprigs are planted '
         'at the field\'s edge; the row of old willows there is now thousands of farewells grown into trees.')],
        sumber=('Sumber: Terinspirasi dari kebiasaan zaman Tang mematahkan ranting willow (zhe liu) untuk mengantar orang yang berangkat '
                'jauh, seperti di Jembatan Ba di luar Chang\'an; lapangan dan barisan pohonnya rancangan game.',
                'Source: Inspired by the Tang custom of breaking a willow sprig (zhe liu) to see off someone leaving on a long journey, as at '
                'the Ba Bridge outside Chang\'an; the field and its trees are game design.'))

    # =========================================================================================== the three guilds
    L('gilda.alkemis', ('Gilda Alkemis: Wabah Daun Kuning', 'The Alchemist Guild: The Yellow Leaf Plague'), [
        ('Empat puluh tahun lalu sebuah wabah menyapu ibu kota. Kulit penderitanya menguning seperti daun di musim gugur, dan dalam tujuh '
         'hari mereka berhenti bernapas. Para alkemis sekte menolak datang; tidak ada sekte yang mau muridnya tertular di kota yang bukan '
         'miliknya.',
         'Forty years ago a plague swept the capital. The skin of the sick turned yellow as autumn leaves, and within seven days they '
         'stopped breathing. The sect alchemists refused to come; no sect wanted its disciples infected in a city it did not own.'),
        ('Seorang alkemis gilda yang masih muda bernama Dan Chen tidak tidur selama sembilan belas hari dan meracik rumus pertamanya dari '
         'akar yang biasa dipakai untuk mewarnai kain. Rumus itu menyelamatkan separuh kota. Ia menjadi Ketua Gilda di usia yang membuat '
         'para tetua iri, dan ia tidak pernah memaafkan dirinya untuk separuh yang lain.',
         'A young guild alchemist named Dan Chen did not sleep for nineteen days and brewed his first formula from a root used to dye cloth. '
         'It saved half the city. He became Guild Head at an age that made the elders jealous, and he has never forgiven himself for the '
         'other half.'),
        ('Sejak itu Gilda Alkemis wajib menyimpan cadangan pil penawar untuk seluruh kota, dan setiap alkemis yang lulus ujian gilda '
         'bersumpah datang ketika wabah datang, ke mana pun. Pertemuan Pil setiap pekan adalah cara gilda mencari tangan-tangan baru '
         'yang cukup tenang untuk sumpah itu.',
         'Since then the Alchemist Guild must keep a stock of antidote pills for the whole city, and every alchemist who passes the guild '
         'exam swears to come when a plague comes, wherever it is. The weekly Pill Gathering is the guild\'s way of finding new hands calm '
         'enough for that oath.')])

    L('gilda.pandai', ('Gilda Pandai Pusaka: Murid-Murid Ou Ye', 'The Forge Guild: The Students of Ou Ye'), [
        ('Para empu gilda ini mengaku sebagai keturunan murid Ou Ye, pandai pedang dari zaman yang sangat tua, yang konon menempa lima '
         'pedang termasyhur dari besi yang ditempa air, api dan kesabaran. Tidak ada yang tahu apakah silsilah itu benar. Para empu '
         'tidak peduli; mereka bilang silsilah dibuktikan di landasan, bukan di kertas.',
         'The masters of this guild claim descent from the students of Ou Ye, a swordsmith of a very old age who, it is said, forged five '
         'famous swords from iron worked by water, fire and patience. No one knows whether the lineage is true. The smiths do not care; '
         'they say a lineage is proven on the anvil, not on paper.'),
        ('Setiap empu baru harus menempa satu pedang tanpa nama dan menyerahkannya kepada gilda. Pedang-pedang itu digantung di aula '
         'gilda, ratusan jumlahnya, dan konon pada malam tertentu salah satunya berdenting sendiri ketika pemilik yang ditunggunya '
         'lewat di jalan.',
         'Every new master must forge one nameless sword and give it to the guild. Those swords hang in the guild hall by the hundreds, '
         'and it is said that on certain nights one of them rings by itself when the owner it waits for passes in the street.'),
        ('Empu yang sekarang memimpin gilda disebut Ou Ye Muda. Ia membenci julukan itu dan menempa lebih keras karenanya.',
         'The master who leads the guild now is called Ou Ye the Younger. He hates the name and forges harder because of it.')],
        sumber=('Sumber: Terinspirasi dari Ou Yezi, pandai pedang legendaris negeri Yue pada zaman Musim Semi dan Gugur, yang menurut '
                'Yue Jue Shu menempa pedang-pedang seperti Zhanlu dan Chunjun; garis murid dan gilda ini rancangan game.',
                'Source: Inspired by Ou Yezi, the legendary swordsmith of the state of Yue in the Spring and Autumn period who, according '
                'to the Yue Jue Shu, forged swords such as Zhanlu and Chunjun; the line of students and this guild are game design.'))

    L('gilda.kultivator', ('Gilda Kultivator: Para Pemburu yang Menetap', 'The Cultivators\' Guild: Hunters Who Settled'), [
        ('Gilda ini didirikan oleh para pemburu binatang roh yang terlalu liar untuk sekte dan terlalu kuat untuk desa. Mereka menjual '
         'inti binatang di pos jalan tua, dan ketika tembok kota dibangun, merekalah yang pertama dipanggil untuk menjaganya. Papan misi '
         'gilda dulunya hanya selembar kulit harimau yang ditempeli nama-nama binatang.',
         'This guild was founded by spirit beast hunters too wild for the sects and too strong for the villages. They sold beast cores at '
         'the old road post, and when the city wall was raised, they were the first called to guard it. The guild\'s mission board was once '
         'a single tiger hide pinned with the names of beasts.'),
        ('Dua puluh tahun lalu Gelombang Binatang terbesar dalam ingatan kota menghantam tembok timur. Seorang pemburu muda bernama Lin Fei '
         'bertahan di celah tembok sepanjang malam; saat fajar ia kehilangan lengan kirinya dan seekor harimau tulang terbaring mati di '
         'kakinya. Ia menjadi Ketua Gilda tahun itu juga.',
         'Twenty years ago the greatest Beast Tide in the city\'s memory struck the east wall. A young hunter named Lin Fei held a breach in '
         'the wall all night; at dawn he had lost his left arm and a bone tiger lay dead at his feet. He became Guild Head that same year.'),
        ('Kini gilda mencatat seratus kultivator muda terbaik di Daftar Naga Muda dan memberi lencana berbintang kepada siapa pun yang '
         'menyelesaikan misinya. Lin Fei bilang lencana itu bukan tanda kehormatan, melainkan tanda bahwa gilda tahu di mana mencari '
         'mayatmu.',
         'Today the guild records the hundred finest young cultivators on the Young Dragon List and gives starred badges to anyone who '
         'finishes its missions. Lin Fei says the badge is not an honour but a sign that the guild knows where to look for your body.')])

    # =========================================================================================== villages and clans
    L('kampung', ('Kampung Fana di Bawah Gunung Sekte', 'Mortal Villages Below the Sect Mountains'), [
        ('Kampung-kampung fana hidup di bayangan gunung sekte. Mereka menanam padi, beternak dan menikahkan anak-anaknya seperti kampung '
         'di mana pun, tetapi setiap keluarga tahu cerita tentang anak tetangga yang suatu hari dibawa pergi kultivator berjubah putih '
         'dan pulang lima puluh tahun kemudian tanpa bertambah tua.',
         'Mortal villages live in the shadow of the sect mountains. They plant rice, keep livestock and marry off their children like '
         'villages anywhere, but every family knows a tale of a neighbour\'s child taken away one day by a white-robed cultivator and '
         'returned fifty years later not a day older.'),
        ('Kampung ini, seperti kebanyakan kampung di wilayahnya, punya sejarah panjang dengan %s: kadang dilindungi, kadang dipungut upeti, '
         'kadang dilupakan. Para tetua menyimpan daftar anak kampung yang pernah lulus ujian sekte itu di balik altar Dewa Tanah, dan '
         'mereka membacakan nama-nama itu pada malam tahun baru.',
         'This village, like most in its land, has a long history with %s: sometimes protected, sometimes taxed, sometimes forgotten. The '
         'elders keep a list of the village children who ever passed that sect\'s trial behind the Earth God\'s altar, and read the names '
         'aloud on New Year\'s Eve.'),
        ('Lonceng di lapangan kampung bukan hanya penanda waktu. Bila kampung bersumpah setia kepada sebuah sekte, plakat sekte itu '
         'digantung di tiang lonceng, dan sejak hari itu kampung membayar beras serta mengirim anak-anak berbakat, sementara sekte '
         'berjanji datang ketika binatang atau begal datang. Janji yang kedua lebih sering dilanggar daripada yang pertama.',
         'The bell on the village square is not only a clock. When a village swears loyalty to a sect, that sect\'s plaque hangs on the bell '
         'post, and from that day the village pays rice and sends its gifted children, while the sect promises to come when beasts or '
         'bandits come. The second promise is broken more often than the first.')],
        tabel=[
            ('Legenda kampung ini: Dewa Tanah setempat dulu seorang petani yang menghadang banjir dengan tubuhnya. Setiap musim hujan '
             'penduduk menaruh sepasang sandal jerami baru di kuilnya, supaya ia tidak berjalan tanpa alas saat menjaga tanggul.',
             'This village\'s legend: its Earth God was once a farmer who stopped a flood with his own body. Each rainy season the villagers '
             'leave a new pair of straw sandals at his shrine, so he need not walk barefoot while guarding the dyke.'),
            ('Legenda kampung ini: sumur di lapangan disebut Sumur Untung. Siapa yang menimba air pertama pada hari tahun baru akan beruntung '
             'setahun penuh, sehingga para pemuda tidur di samping sumur pada malam sebelumnya dan bangun saling mendorong.',
             'This village\'s legend: the well on the square is called the Lucky Well. Whoever draws the first water on New Year\'s Day will '
             'be lucky all year, so the young men sleep beside it the night before and wake up shoving one another.'),
            ('Legenda kampung ini: enam puluh tahun lalu sungai meluap dan menghanyutkan separuh rumah. Seekor kura-kura sebesar meja muncul, '
             'membawa bayi kepala kampung di punggungnya sampai ke bukit, lalu kembali ke air. Tidak ada orang di sini yang makan kura-kura.',
             'This village\'s legend: sixty years ago the river burst and carried off half the houses. A turtle as large as a table rose and '
             'bore the headman\'s baby on its back to the hill, then went back into the water. No one here eats turtle.'),
            ('Legenda kampung ini: pohon tua di ujung jalan ditanam oleh seorang kultivator yang singgah meminta air. Ia bilang pohon itu akan '
             'berbunga pada tahun kampung melahirkan anak berakar roh. Pohon itu sudah berbunga tiga kali.',
             'This village\'s legend: the old tree at the end of the road was planted by a cultivator who stopped to ask for water. He said it '
             'would blossom in the year the village bore a child with a spirit root. It has blossomed three times.'),
            ('Legenda kampung ini: Dewa Tanah kampung ini terkenal pelupa. Penduduk menulis permohonan mereka di kertas merah dan menempelkannya '
             'di dinding kuil, supaya ia bisa membacanya lagi bila lupa.',
             'This village\'s legend: its Earth God is famously forgetful. The villagers write their wishes on red paper and paste them on the '
             'shrine wall, so he can read them again when he forgets.'),
            ('Legenda kampung ini: pada malam berkabut, suara gong terdengar dari bukit, padahal tidak ada kuil di sana. Para tetua bilang itu '
             'penjaga kampung dari zaman perang tujuh sekte yang masih berjaga, dan anak-anak dilarang menjawab bila namanya dipanggil.',
             'This village\'s legend: on foggy nights a gong sounds from the hill, though there is no temple there. The elders say it is a '
             'village guard from the war of the seven sects still at his post, and children are forbidden to answer if their name is called.'),
            ('Legenda kampung ini: lumbung kampung tidak pernah dimasuki tikus, karena seekor kucing belang tiga yang tinggal di sana sudah '
             'berumur lebih dari seratus tahun. Kucing itu tidak suka disentuh, dan dua kali ia terlihat berjalan di atas air sawah.',
             'This village\'s legend: no rat has ever entered the village granary, because a calico cat that lives there is over a hundred '
             'years old. It does not like to be touched, and twice it has been seen walking on the paddy water.'),
            ('Legenda kampung ini: kampung ini didirikan tujuh keluarga yang lari dari perang. Mereka membawa tujuh batu dari desa lama dan '
             'menanamnya di bawah tiang lonceng. Selama batu-batu itu di sana, kata orang, tidak ada keluarga pendiri yang punah.',
             'This village\'s legend: seven families fleeing a war founded the village. They carried seven stones from their old homes and '
             'buried them under the bell post. As long as the stones stay there, people say, no founding family will die out.'),
            ('Legenda kampung ini: seorang pengantin perempuan dari kampung ini pernah menolak dinikahkan dengan tuan muda sekte, lalu kabur '
             'ke gunung dan kembali sebagai tetua sekte yang lain. Tuan muda itu, kata orang, sampai sekarang tidak berani lewat sini.',
             'This village\'s legend: a bride from this village once refused to marry a sect\'s young master, fled into the mountains and came '
             'back an elder of another sect. The young master, people say, still does not dare pass this way.'),
            ('Legenda kampung ini: setiap musim panen, Dewa Tanah meminjam satu karung beras dan mengembalikannya dua kali lipat tahun depan. '
             'Tidak ada yang pernah melihat siapa yang mengambil karung itu, tetapi tidak ada pula yang pernah berhenti meninggalkannya.',
             'This village\'s legend: each harvest the Earth God borrows one sack of rice and returns it twice over the next year. No one has '
             'ever seen who takes the sack, but no one has ever stopped leaving it.'),
            ('Legenda kampung ini: saat kemarau besar, seorang tabib keliling menggali sumur dengan tongkatnya dan pergi tanpa meminta bayaran. '
             'Airnya pahit tetapi tidak pernah kering, dan penduduk menyebutnya Air Tabib.',
             'This village\'s legend: in a great drought a wandering healer dug a well with his staff and left without asking payment. Its water '
             'is bitter but has never run dry, and the villagers call it the Healer\'s Water.'),
            ('Legenda kampung ini: seekor rubah roh pernah tinggal di hutan bambu belakang kampung dan menukar ayam curian dengan jamur yang '
             'menyembuhkan demam. Setiap rumah masih menaruh satu telur di pagar pada malam bulan purnama, untuk berjaga-jaga.',
             'This village\'s legend: a spirit fox once lived in the bamboo behind the village and traded stolen chickens for mushrooms that cure '
             'fever. Every house still leaves one egg on the fence on full-moon nights, just in case.')],
        sumber=('Sumber: Terinspirasi dari Tudi Gong, Dewa Tanah penjaga setempat yang punya kuil kecil di hampir setiap desa Tiongkok, dan '
                'dari desa pegunungan asal Han Li di awal A Record of a Mortal\'s Journey to Immortality; legenda-legenda kampung rancangan game.',
                'Source: Inspired by Tudi Gong, the local Earth God with a small shrine in nearly every Chinese village, and by Han Li\'s home '
                'village in the mountains at the start of A Record of a Mortal\'s Journey to Immortality; the village legends are game design.'),
        isian='sekte_terdekat')

    L('kampung.klan', ('Desa Klan: Darah dan Papan Arwah', 'Clan Villages: Blood and Ancestral Tablets'), [
        ('Desa klan bukan kampung biasa. Temboknya tinggi, pemuda-pemudanya berlatih sejak bisa berjalan, dan semua penghuninya memakai '
         'satu nama keluarga. Klan kultivator hidup di antara dunia fana dan dunia sekte: terlalu kecil untuk menjadi sekte, terlalu bangga '
         'untuk tunduk pada sekte mana pun.',
         'A clan village is no ordinary village. Its walls are high, its young train from the day they can walk, and everyone in it bears '
         'one family name. Cultivator clans live between the mortal world and the sect world: too small to be a sect, too proud to bow to '
         'any.'),
        ('Di Aula Leluhur, papan-papan arwah berjajar dari leluhur pendiri sampai yang terakhir gugur. Setiap papan baru ditulis dengan '
         'darah dari ujung jari patriark, dan setiap anak klan yang berbakat dibawa ke aula itu pada hari ulang tahunnya yang ketujuh '
         'untuk diperkenalkan kepada para leluhur. Anak yang menangis dianggap disukai.',
         'In the Ancestral Hall the spirit tablets run from the founding ancestor to the last who fell. Each new tablet is written with '
         'blood from the patriarch\'s fingertip, and every gifted clan child is brought there on their seventh birthday to be introduced '
         'to the ancestors. A child who cries is thought to be favoured.')],
        tabel=[
            ('Asal klan ini: leluhurnya adalah pengawal yang menyelamatkan ketua sebuah sekte dan menolak bergabung, lalu menerima tanah ini '
             'sebagai imbalan. Klan masih menyimpan pedangnya yang tumpul di atas altar.',
             'This clan\'s origin: its ancestor was a bodyguard who saved a sect master and refused to join, then received this land as reward. '
             'The clan still keeps his blunted sword above the altar.'),
            ('Asal klan ini: tiga bersaudara menemukan urat roh kecil di bawah sumur keluarga dan bersumpah tidak pernah menjualnya. Dua ratus '
             'tahun kemudian sumur itu menjadi jantung desa, dan sumpahnya menjadi hukum klan.',
             'This clan\'s origin: three brothers found a small spirit vein under the family well and swore never to sell it. Two hundred years '
             'later the well became the heart of the village, and the oath became clan law.'),
            ('Asal klan ini: leluhurnya murid buangan yang diusir karena mencuri sebuah kitab. Ia tidak pernah mengembalikannya, dan kitab itu '
             'kini menjadi ilmu turun-temurun klan. Sekte asalnya masih menagih kitab itu setiap seratus tahun.',
             'This clan\'s origin: its ancestor was an expelled disciple cast out for stealing a manual. He never returned it, and it became the '
             'clan\'s inherited art. His old sect still asks for it back every hundred years.'),
            ('Asal klan ini: klan ini dulu keluarga pedagang garam yang menyewa kultivator untuk mengawal kafilahnya. Satu generasi kemudian '
             'mereka berhenti menyewa dan mulai berlatih sendiri.',
             'This clan\'s origin: it was once a family of salt merchants who hired cultivators to guard their caravans. A generation later they '
             'stopped hiring and began to train themselves.'),
            ('Asal klan ini: pendirinya seorang perempuan yang menikahi seekor binatang roh yang berwujud manusia, kata dongengnya. Anggota klan '
             'konon tidak pernah digigit ular, dan mereka tidak membantah dongeng itu.',
             'This clan\'s origin: its founder was a woman who married a spirit beast in human form, the tale says. Clan members are said never '
             'to be bitten by snakes, and they do not deny the tale.'),
            ('Asal klan ini: leluhurnya adalah juru masak istana yang membawa pulang satu resep pil dari dapur seorang alkemis. Klan ini kini '
             'terkenal dengan pilnya, dan masakannya.',
             'This clan\'s origin: its ancestor was a palace cook who brought home one pill recipe from an alchemist\'s kitchen. The clan is now '
             'known for its pills, and for its food.'),
            ('Asal klan ini: klan ini selamat dari perang tujuh sekte dengan bersembunyi di gua selama tujuh tahun. Setiap tahun mereka berpuasa '
             'tujuh hari untuk mengenangnya, dan tidak ada tamu yang dijamu pada minggu itu.',
             'This clan\'s origin: the clan survived the war of the seven sects by hiding in a cave for seven years. Each year they fast for seven '
             'days to remember it, and no guest is fed that week.'),
            ('Asal klan ini: patriark pertama mengalahkan seorang tuan muda sekte dalam duel dan memenangkan tanah ini sebagai taruhan. Sejak '
             'itu setiap tuan muda klan diharapkan menantang siapa saja yang lewat, sebuah kebiasaan yang membuat klan ini terkenal dan sering '
             'malu.',
             'This clan\'s origin: the first patriarch beat a sect\'s young master in a duel and won this land as the stake. Since then every '
             'young master of the clan is expected to challenge anyone passing through, a custom that made the clan famous and often embarrassed.')],
        sumber=('Sumber: Terinspirasi dari aula leluhur (citang) klan-klan Tiongkok dengan papan-papan arwahnya, dan dari klan kultivator '
                'seperti Keluarga Xiao dalam Battle Through the Heavens; asal-usul klan rancangan game.',
                'Source: Inspired by the ancestral halls (citang) of Chinese clans with their spirit tablets, and by cultivator clans such as '
                'the Xiao family of Battle Through the Heavens; the clan origins are game design.'))

    # =========================================================================================== NPC sect compounds
    L('kompleks', ('Kompleks Sekte: Halaman, Aula Dalam, Puncak', 'Sect Compounds: Court, Inner Hall, Peak'), [
        ('Di belakang gerbang sebuah sekte, gunung dibagi seperti tangga: halaman murid luar di kaki, aula murid dalam di lereng, dan aula '
         'ketua di puncak. Seorang murid menghabiskan bertahun-tahun untuk naik satu teras. Bagi orang luar, jarak dari gerbang ke puncak '
         'hanya beberapa ratus anak tangga; bagi murid, itu seumur hidup.',
         'Behind a sect\'s gate the mountain is divided like a stair: the outer disciples\' court at the foot, the inner disciples\' hall on '
         'the slope, the master\'s hall at the peak. A disciple spends years climbing a single terrace. To outsiders the gate and the peak '
         'are a few hundred steps apart; to a disciple they are a lifetime.'),
        ('Tamu diperlakukan menurut hubungan sektemu dengan %s. Sekutu dan sahabat disambut sampai aula dalam dan boleh bertanding '
         'persahabatan di lapangan latihan; tamu netral hanya sampai halaman; musuh diperingatkan di gerbang. Dalam perang, gunung yang '
         'sama menjadi benteng, dan setiap anak tangganya dijaga.',
         'Guests are treated by how your sect stands with %s. Allies and friends are welcomed as far as the inner hall and may spar in the '
         'training court; neutral guests only reach the court; enemies are warned at the gate. In war the same mountain becomes a fortress, '
         'and every step is guarded.')],
        tabel=[
            ('Kata para murid, pendiri %s menebas sebuah gunung menjadi dua dengan satu ayunan, lalu membangun aula utamanya di celah itu '
             'supaya muridnya ingat bahwa tidak ada yang mustahil bagi pedang yang tulus.',
             'The disciples say the founder of %s cut a mountain in two with one stroke, then built the main hall in the cleft so the disciples '
             'would remember that nothing is impossible for a sincere blade.'),
            ('Kata para murid, pendiri %s adalah anak gembala yang tertidur di gua dan bermimpi diajari seorang dewa selama seratus tahun. Saat '
             'bangun, hanya satu malam telah berlalu, dan ia sudah mencapai Inti Emas.',
             'The disciples say the founder of %s was a shepherd boy who fell asleep in a cave and dreamed a god taught him for a hundred years. '
             'When he woke only one night had passed, and he had already formed a Golden Core.'),
            ('Kata para murid, pendiri %s menukar matanya dengan sebuah kitab dari roh gunung, dan sejak itu melihat qi alih-alih cahaya. '
             'Karena itu para tetua sekte berlatih dengan mata tertutup sekali setahun.',
             'The disciples say the founder of %s traded her eyes to a mountain spirit for a manual, and from then on saw qi instead of light. '
             'That is why the sect\'s elders train blindfolded once a year.'),
            ('Kata para murid, pendiri %s adalah juru masak yang mencapai pencerahan sambil mengaduk bubur. Sendok kayunya masih tergantung di '
             'aula utama, dan setiap murid baru wajib bertugas di dapur selama tiga bulan.',
             'The disciples say the founder of %s was a cook who reached enlightenment stirring porridge. The wooden ladle still hangs in the '
             'main hall, and every new disciple must serve three months in the kitchen.'),
            ('Kata para murid, pendiri %s menantang langit sendiri dan menahan sembilan sambaran kesengsaraan dengan payung kertas. Payung itu '
             'hangus, ia tidak. Para murid membawa payung kertas saat menerobos ranah, untuk keberuntungan.',
             'The disciples say the founder of %s defied heaven itself and withstood nine tribulation strikes under a paper umbrella. The umbrella '
             'burned; he did not. Disciples carry paper umbrellas when they break through, for luck.'),
            ('Kata para murid, pendiri %s datang ke gunung ini mengejar seekor bangau putih yang mencuri kitabnya, dan tidak pernah pergi lagi. '
             'Bangau-bangau di sekitar puncak dilindungi hukum sekte.',
             'The disciples say the founder of %s came to this mountain chasing a white crane that stole his manual, and never left. The cranes '
             'around the peak are protected by sect law.'),
            ('Kata para murid, pendiri %s mengalahkan tujuh sekte dalam tujuh hari, lalu menolak semua yang ia menangkan kecuali satu batu '
             'tempat ia duduk. Batu itu masih menjadi kursi ketua sekte.',
             'The disciples say the founder of %s defeated seven sects in seven days, then refused all he had won except the stone he sat on. '
             'That stone is still the sect master\'s seat.'),
            ('Kata para murid, pendiri %s menanam satu biji teratai di kolam puncak, dan teratai itu belum pernah layu dalam seribu tahun. '
             'Bila suatu hari ia layu, kata ramalan, sekte akan jatuh.',
             'The disciples say the founder of %s planted one lotus seed in the peak pool, and the lotus has not wilted in a thousand years. If it '
             'ever wilts, the prophecy says, the sect will fall.'),
            ('Kata para murid, pendiri %s adalah dua sahabat yang bertengkar tentang satu jurus selama empat puluh tahun dan akhirnya mendirikan '
             'satu sekte supaya bisa terus bertengkar dengan tenang. Sekte ini masih punya dua aliran yang saling meledek.',
             'The disciples say the founders of %s were two friends who argued over one technique for forty years and finally founded a sect so '
             'they could keep arguing in peace. The sect still has two branches that mock each other.'),
            ('Kata para murid, pendiri %s mendaki gunung ini dengan menggendong ibunya yang sakit, mencari obat yang konon tumbuh di puncak. '
             'Ibunya sembuh, dan kebun obat di halaman adalah kebun yang ia tanam untuknya.',
             'The disciples say the founder of %s climbed this mountain carrying his sick mother, seeking a herb said to grow at the peak. She was '
             'cured, and the herb garden in the court is the garden he planted for her.'),
            ('Kata para murid, pendiri %s mengusir naga banjir dari lembah ini dan mengikat tulang ekornya di bawah menara lonceng. Bila lonceng '
             'berbunyi sendiri, itu berarti naga itu bermimpi.',
             'The disciples say the founder of %s drove a flood dragon from this valley and bound its tailbone under the bell tower. When the bell '
             'rings by itself, the dragon is dreaming.'),
            ('Kata para murid, pendiri %s tidak pernah menulis satu kata pun. Semua kitab sekte ditulis murid-muridnya dari ingatan, dan setiap '
             'kitab berbeda sedikit dari yang lain. Para tetua bilang perbedaan itulah ajaran yang sebenarnya.',
             'The disciples say the founder of %s never wrote a single word. Every manual of the sect was written by students from memory, and '
             'each differs a little from the others. The elders say the differences are the true teaching.')],
        sumber=('Sumber: Terinspirasi dari Sekte Tujuh Misteri dalam A Record of a Mortal\'s Journey to Immortality dan Sekte Penyokong '
                '(Reliance Sect) dalam I Shall Seal the Heavens: halaman murid luar, puncak murid dalam, aula di puncak gunung; kisah para '
                'pendiri rancangan game.',
                'Source: Inspired by the Seven Mysteries Sect of A Record of a Mortal\'s Journey to Immortality and the Reliance Sect of I Shall '
                'Seal the Heavens: an outer disciples\' court, an inner disciples\' peak, a hall at the summit; the founders\' tales are game design.'),
        isian='sekte_situs')

    # =========================================================================================== caves
    L('gua.pertapa', ('Gua Pertapa: Mereka yang Meninggalkan Dunia', 'Hermit Caves: Those Who Left the World'), [
        ('Di lereng yang sunyi, beberapa kultivator kuat memilih hidup sendiri: sepetak herba di bawah lubang cahaya, mata air, satu ruang '
         'untuk bermeditasi. Mereka tidak mencari murid, tetapi kadang menerima satu orang yang cukup sabar untuk tiga ujian mereka.',
         'On quiet slopes some strong cultivators choose to live alone: a patch of herbs beneath a light shaft, a spring, one room for '
         'meditation. They seek no students, but sometimes accept one person patient enough for their three trials.'),
        ('Setelah mewariskan ilmunya, pertapa biasanya pergi mengembara dan tidak kembali. Gua yang ditinggalkannya menjadi tempat tinggal '
         'yang baik bagi siapa pun yang mewarisinya, karena qi di sana sudah dijinakkan oleh puluhan tahun meditasi.',
         'After passing on their art, hermits usually leave to wander and do not return. The cave they leave behind makes a fine abode for '
         'whoever inherits it, for its qi has been tamed by decades of meditation.')],
        tabel=[
            ('Alasan pertapa gua ini meninggalkan dunia: muridnya yang paling ia sayangi membunuh saudara seperguruan demi satu pil. Ia tidak '
             'menghukumnya; ia hanya pergi, karena tidak tahu lagi apa yang sebenarnya ia ajarkan.',
             'Why this cave\'s hermit left the world: the student he loved most killed a fellow disciple over one pill. He did not punish him; '
             'he simply left, because he no longer knew what he had truly been teaching.'),
            ('Alasan pertapa gua ini meninggalkan dunia: ia memenangkan setiap duel selama tiga ratus tahun dan suatu pagi sadar tidak bisa '
             'mengingat wajah satu pun lawannya.',
             'Why this cave\'s hermit left the world: she won every duel for three hundred years and one morning realised she could not remember '
             'a single opponent\'s face.'),
            ('Alasan pertapa gua ini meninggalkan dunia: istrinya yang fana meninggal karena usia sementara ia masih tampak tiga puluh tahun. Ia '
             'menanam pohon plum di depan gua, pohon kesukaan istrinya, dan merawatnya setiap hari.',
             'Why this cave\'s hermit left the world: his mortal wife died of old age while he still looked thirty. He planted a plum tree before '
             'the cave, her favourite, and tends it every day.'),
            ('Alasan pertapa gua ini meninggalkan dunia: sektenya musnah dalam satu malam ketika ia sedang bermeditasi tertutup. Ia keluar dari '
             'meditasi seratus tahun kemudian dan tidak menemukan siapa pun yang bisa ia balas.',
             'Why this cave\'s hermit left the world: her sect was destroyed in one night while she sat in closed meditation. She emerged a hundred '
             'years later and found no one left to avenge.'),
            ('Alasan pertapa gua ini meninggalkan dunia: ia kalah taruhan dengan seorang biksu dan harus diam di gunung sampai bisa menjawab satu '
             'pertanyaan. Biksu itu sudah lama mati. Pertanyaannya belum terjawab.',
             'Why this cave\'s hermit left the world: he lost a wager to a monk and must stay on the mountain until he can answer one question. '
             'The monk is long dead. The question is still unanswered.'),
            ('Alasan pertapa gua ini meninggalkan dunia: ia bosan. Itu saja, katanya, dan ia tertawa setiap kali orang mencari alasan yang lebih '
             'dalam.',
             'Why this cave\'s hermit left the world: she was bored. That is all, she says, and laughs every time someone looks for a deeper reason.'),
            ('Alasan pertapa gua ini meninggalkan dunia: ia hampir dikuasai iblis hatinya di tengah pertempuran dan membunuh orang-orang yang tidak '
             'bersalah. Ia mengasingkan diri supaya tidak ada orang lain yang berada di dekatnya bila itu terjadi lagi.',
             'Why this cave\'s hermit left the world: he was nearly overtaken by his heart demon in battle and killed innocent people. He exiled '
             'himself so no one would be near him if it happened again.'),
            ('Alasan pertapa gua ini meninggalkan dunia: ia mendengar bahwa herba tertentu hanya mekar sekali dalam lima ratus tahun di lereng ini, '
             'dan memutuskan menunggu. Herba itu sudah mekar sekali. Ia memutuskan menunggu lagi.',
             'Why this cave\'s hermit left the world: she heard a certain herb blooms only once in five hundred years on this slope and decided to '
             'wait. It has bloomed once. She decided to wait again.'),
            ('Alasan pertapa gua ini meninggalkan dunia: kaisar sebuah negeri fana memintanya menjadi penasihat perang. Ia menolak dan bersembunyi '
             'di sini, dan negeri itu sudah runtuh dua kali sejak itu.',
             'Why this cave\'s hermit left the world: the emperor of a mortal kingdom asked him to be his war adviser. He refused and hid here, and '
             'that kingdom has fallen twice since.'),
            ('Alasan pertapa gua ini meninggalkan dunia: ia ingin tahu apakah Dao bisa dipahami tanpa guru, tanpa kitab dan tanpa lawan. Setelah '
             'dua abad ia bilang jawabannya "hampir", dan ia masih mencari sisanya.',
             'Why this cave\'s hermit left the world: she wanted to know whether the Dao can be understood without a teacher, a book or a rival. '
             'After two centuries she says the answer is "almost", and she is still looking for the rest.')])

    L('gua.makam', ('Makam Warisan: Pintu yang Menunggu Pewaris', 'Inheritance Tombs: Doors that Wait for an Heir'), [
        ('Para leluhur besar yang mati tanpa murid kadang membangun makam di bawah tanah dan menitipkan sisa jiwanya di sana. Pintunya hanya '
         'sebuah stela di padang terbuka; di bawahnya lorong bermural menceritakan hidup sang leluhur kepada siapa pun yang cukup kuat untuk '
         'turun.',
         'Great ancestors who died without students sometimes built tombs beneath the earth and left a remnant of their soul inside. The door '
         'is only a stele in open country; beneath it a painted corridor tells the ancestor\'s life to anyone strong enough to descend.'),
        ('Tiga ruang ujian menjaga warisannya: Hati, Kekuatan dan Pemahaman. Sisa jiwa itu tidak mencari orang yang paling kuat, melainkan '
         'orang yang paling mirip dengan dirinya di masa muda. Banyak yang lulus dua ujian dan pulang dengan tangan kosong, karena gagal '
         'menjawab pertanyaan yang tidak pernah bisa dijawab leluhur itu sendiri.',
         'Three trial rooms guard the inheritance: Heart, Strength and Comprehension. The remnant soul does not seek the strongest, but the '
         'one most like its own young self. Many pass two trials and go home empty-handed, having failed a question the ancestor never could '
         'answer either.'),
        ('Setelah warisannya diberikan, sisa jiwa itu akhirnya beristirahat. Makamnya tetap berdiri, sunyi, dan para pewaris kadang kembali '
         'untuk menyalakan dupa di aula yang kosong.',
         'Once the inheritance is given the remnant soul finally rests. The tomb remains, silent, and heirs sometimes return to light incense '
         'in the empty hall.')])

    L('gua.sarang_iblis', ('Sarang Kultivator Iblis', 'Lairs of the Demonic Cultivators'), [
        ('Di balik tebing yang tampak biasa, para kultivator jalan iblis menggali sarang: aula kolam darah, sel tawanan, ruang racik pil '
         'terlarang. Mereka menculik orang fana dari jalan dan kampung, karena darah dan jiwa manusia adalah bahan yang tidak bisa dibeli di '
         'pasar mana pun.',
         'Behind ordinary-looking cliffs the demonic path\'s cultivators dig their lairs: a hall of blood pools, cells for captives, rooms for '
         'forbidden pills. They abduct mortals from roads and villages, for human blood and souls are ingredients no market sells.'),
        ('Orang-orang bilang sarang-sarang seperti ini adalah cabang dari %s, atau dari murid-murid yang diusir darinya. Sekte itu tidak '
         'pernah mengakuinya, dan tidak pernah juga menyangkalnya. Tetua iblis yang memimpin sebuah sarang biasanya lebih takut pada sektenya '
         'sendiri daripada pada pahlawan mana pun.',
         'People say lairs like this are branches of %s, or of disciples it cast out. The sect has never admitted it, nor ever denied it. The '
         'demon elder who rules a lair usually fears his own sect more than any hero.'),
        ('Tawanan yang dibebaskan harus diantar ke tempat yang aman; jalan pulang lebih berbahaya daripada sarangnya, karena para pemburu '
         'iblis tidak suka kehilangan bahan.',
         'Freed captives must be escorted to safety; the road home is more dangerous than the lair, for demon hunters hate losing their '
         'ingredients.')],
        sumber=('Sumber: Terinspirasi dari markas para kultivator jalan iblis yang menculik orang fana untuk diracik, kiasan umum novel '
                'xianxia; sarang dan penghuninya rancangan game.',
                'Source: Inspired by the strongholds of demonic-path cultivators who abduct mortals for their brews, a common xianxia trope; '
                'the lairs and their dwellers are game design.'),
        isian='sekte_iblis')

    L('gua.sarang_binatang', ('Sarang Binatang Roh', 'Spirit Beast Dens'), [
        ('Binatang roh yang hidup cukup lama akan menyerap qi bumi sampai tubuhnya berubah: taringnya mengeras seperti besi, matanya '
         'mengingat, dan ia mulai mengumpulkan binatang lain di sekitarnya. Sarangnya bisa berupa gua, bisa juga pohon raksasa yang batangnya '
         'berongga.',
         'A spirit beast that lives long enough absorbs the earth\'s qi until its body changes: its fangs harden like iron, its eyes remember, '
         'and it begins to gather other beasts around it. Its den may be a cave, or a giant tree with a hollow trunk.'),
        ('Para pemburu Gilda Kultivator bilang sarang binatang adalah sekolah terbaik bagi binatang roh peliharaan: yang bertarung di sini '
         'tumbuh lebih cepat. Mereka juga bilang setiap sarang punya ibu, dan anak-anak yang ia tinggalkan kadang tersesat dan bisa dijinakkan '
         'oleh orang yang sabar.',
         'The hunters of the Cultivators\' Guild say a beast den is the best school for a tamed spirit beast: those that fight here grow faster. '
         'They also say every den has a mother, and the young she leaves behind sometimes wander and can be tamed by a patient hand.')])

    L('gua.urat_roh', ('Gua Urat Roh: Tempat Naga Tidur', 'Spirit Vein Grottoes: Where the Dragon Sleeps'), [
        ('Jauh di bawah tanah, qi bumi mengalir seperti sungai dan kadang mengkristal menjadi dinding ungu dan putih yang bersinar sendiri. '
         'Para penambang menyebutnya urat roh; para tetua desa menyebutnya napas naga.',
         'Deep underground the earth\'s qi flows like a river and sometimes crystallises into purple and white walls that glow of themselves. '
         'Miners call it a spirit vein; village elders call it the dragon\'s breath.'),
        ('Menurut mitos, di bawah seluruh benua tertidur seekor naga yang tubuhnya adalah pegunungan. Gua-gua kristal ini adalah tempat sisiknya '
         'retak, dan qi yang bocor dari sana memanggil harta-harta langka untuk lahir di sekitarnya. Penjaga kristal yang berdiri di gua-gua '
         'ini, kata orang, adalah serpihan sisik yang belajar berjalan.',
         'Myth says a dragon sleeps beneath the whole continent, its body the mountain ranges. These crystal grottoes are where its scales '
         'crack, and the qi leaking from them calls rare treasures to be born nearby. The crystal guardians who stand in these caves, people '
         'say, are flakes of scale that learned to walk.')],
        sumber=('Sumber: Terinspirasi dari konsep urat naga (longmai) dalam fengshui Tiongkok, aliran qi bumi yang mengikuti pegunungan; '
                'naga tidur dan penjaga kristal rancangan game.',
                'Source: Inspired by the dragon veins (longmai) of Chinese fengshui, the flow of earth qi that follows mountain ranges; the '
                'sleeping dragon and the crystal guardians are game design.'))

    L('gua.cermin_hati', ('Gua Cermin Hati: Kolam yang Memantulkan Niat', 'Heart Mirror Caves: Pools that Reflect Intent'), [
        ('Di hutan yang lembap ada gua kecil dengan kolam yang airnya tidak pernah beriak. Kolam itu tidak memantulkan wajah, melainkan niat: '
         'siapa yang duduk di tepinya cukup lama akan melihat iblis hatinya sendiri berdiri di permukaan air.',
         'In damp forests lies a small cave with a pool whose water never ripples. It reflects not faces but intentions: whoever sits by it '
         'long enough sees their own heart demon standing on the water.'),
        ('Seorang biksu pernah melihat iblisnya di kolam seperti ini, lalu tertawa sampai menangis, karena iblis itu memakai wajah ibunya yang '
         'sedang memarahinya karena tidak pulang. Ia kemudian menjadi guru Biksu Jing Xin di kuil ibu kota, dan mengajarkan bahwa iblis hati '
         'tidak dikalahkan, melainkan dikenali.',
         'A monk once saw his demon in a pool like this and laughed until he wept, for it wore the face of his mother scolding him for never '
         'coming home. He later became the teacher of Monk Jing Xin at the capital temple, and taught that a heart demon is not defeated but '
         'recognised.')])

    # =========================================================================================== natural sites
    L('situs.puncak_petir', ('Puncak Petir: Batu Catatan Kesengsaraan', 'Thunder Peak: The Stone of Tribulations'), [
        ('Di puncak-puncak tertinggi, sembilan tiang tembaga berdiri di sekitar teras altar. Konon tembaga itu menarik sebagian amarah langit, '
         'sehingga kesengsaraan yang dihadapi di sini sedikit lebih ringan. Banyak kultivator mendaki berhari-hari hanya untuk menerobos di '
         'tempat ini.',
         'On the highest peaks nine copper pillars stand around an altar terrace. It is said the copper draws off some of heaven\'s anger, so '
         'a tribulation faced here is a little lighter. Many cultivators climb for days just to break through at this place.'),
        ('Batu Catatan di tengah teras menyimpan nama setiap orang yang selamat dari kesengsaraan di sini, berikut ranah dan harinya. Para '
         'pengembara suka datang menonton; kata mereka, melihat orang lain ditempa langit adalah cara termurah untuk belajar rendah hati.',
         'The Record Stone in the middle of the terrace keeps the name of everyone who survived a tribulation here, with their realm and '
         'day. Wanderers like to come and watch; they say seeing someone else forged by heaven is the cheapest way to learn humility.')],
        sumber=('Sumber: Terinspirasi dari kesengsaraan petir langit (tianjie) yang harus dilalui kultivator saat menerobos ranah dalam '
                'novel xianxia; tiang tembaga dan Batu Catatan rancangan game.',
                'Source: Inspired by the heavenly lightning tribulation (tianjie) cultivators must pass when breaking through in xianxia '
                'novels; the copper pillars and the Record Stone are game design.'))

    L('situs.panggung_bintang', ('Panggung Bintang: Armilari Para Pengamat', 'Star Stage: The Watchers\' Armillary'), [
        ('Di padang tinggi berdiri panggung bundar dengan cincin-cincin perunggu yang bersilangan: armilari tua yang dibangun para pengamat '
         'langit sebelum sekte-sekte ada. Cincinnya tidak pernah diputar oleh tangan siapa pun, tetapi pada malam peristiwa langit, kata '
         'orang, cincin itu bergerak sendiri mengikuti bintang.',
         'On a high meadow stands a round stage with crossing bronze rings: an old armillary built by sky watchers before the sects existed. '
         'No hand ever turns its rings, yet on nights of heavenly events, people say, they move by themselves to follow the stars.'),
        ('Para pengamat percaya bencana bintang adalah ujian, bukan hukuman, dan siapa yang menghadapinya di bawah armilari akan dihadiahi '
         'lebih banyak oleh langit. Tidak ada yang tahu apakah itu benar, tetapi tidak ada pula yang mau menjadi orang pertama yang '
         'membuktikannya salah.',
         'The watchers believed a star calamity is a test, not a punishment, and that whoever faces it beneath the armillary is rewarded more '
         'by heaven. No one knows whether that is true, but no one wants to be the first to prove it wrong.')],
        sumber=('Sumber: Terinspirasi dari bola armilari Tiongkok kuno, seperti armilari bertenaga air buatan Zhang Heng pada dinasti Han '
                'Timur; panggung dan kepercayaannya rancangan game.',
                'Source: Inspired by ancient Chinese armillary spheres, such as the water-driven armillary built by Zhang Heng in the Eastern '
                'Han dynasty; the stage and its beliefs are game design.'))

    L('situs.mata_air_roh', ('Mata Air Roh', 'Spirit Springs'), [
        ('Di tepi sungai dan di hutan yang tenang, kadang air keluar dari batu giok dengan qi yang cukup kuat untuk dirasakan di lidah. '
         'Penduduk setempat membangun kuil kecil di sampingnya dan meminta orang minum sekali saja per hari; yang serakah, kata mereka, '
         'akan mendapati airnya terasa seperti air biasa.',
         'By rivers and in quiet woods water sometimes rises from jade rock with qi strong enough to taste. Locals build a small shrine beside '
         'it and ask people to drink only once a day; the greedy, they say, find it tastes like ordinary water.'),
        ('Para alkemis menghargai air ini lebih dari emas, karena pil penawar terbaik tidak bisa diracik dengan air sumur. Di ibu kota, '
         'Tetua Dan Chen menyimpan satu guci air mata air roh di bawah mejanya dan tidak pernah meminjamkannya.',
         'Alchemists value this water above gold, for the finest antidotes cannot be brewed with well water. In the capital, Elder Dan Chen '
         'keeps a jar of spirit spring water under his desk and never lends it.')])

    # =========================================================================================== roads and posts
    L('jalan.tonggak', ('Tonggak Jalan: Batu yang Menghitung Li', 'Road Milestones: Stones that Count the Li'), [
        ('Setiap lima ratus langkah kuda di jalan-jalan tua berdiri tonggak batu berukir. Ukirannya menulis jarak dalam li ke tempat-tempat '
         'di kedua ujung jalan, dan di bawahnya, nyaris terhapus, nama Raja Jalan: seorang penguasa fana kuno yang memerintahkan agar semua '
         'jalan di negerinya bisa dibaca bahkan oleh orang yang buta huruf.',
         'Every five hundred horse paces along the old roads stands a carved stone post. Its carving gives the distance in li to the places at '
         'both ends of the road and, beneath it, nearly worn away, the name of the Road King: an ancient mortal ruler who ordered that every '
         'road in his land be readable even by those who could not read.'),
        ('Para pedagang punya kebiasaan menyentuh setiap tonggak yang mereka lewati; kata mereka, jalan yang sudah disentuh tidak akan '
         'menyesatkan. Peta-peta kafilah tua digambar dari tonggak ke tonggak, bukan dari kota ke kota.',
         'Merchants have a habit of touching every milestone they pass; a road once touched, they say, will not lead you astray. Old caravan '
         'maps were drawn from milestone to milestone, not town to town.')],
        sumber=('Sumber: Terinspirasi dari li, satuan jarak Tiongkok kuno, dan gundukan penanda jarak (hou) di tepi jalan zaman Tang yang '
                'dibangun setiap lima dan sepuluh li, serta dari jalan-jalan lurus dinasti Qin; Raja Jalan rancangan game.',
                'Source: Inspired by the li, the old Chinese unit of distance, the roadside distance mounds (hou) of the Tang dynasty raised '
                'every five and ten li, and the straight roads of the Qin dynasty; the Road King is game design.'))

    L('jalan.kuil', ('Kuil Jalan: Dewa Tanah Para Pengembara', 'Road Shrines: The Travellers\' Earth God'), [
        ('Di tepi jalan berdiri kuil-kuil kecil, tidak lebih tinggi dari seorang lelaki, untuk Dewa Tanah yang menjaga para pengembara. Ia '
         'bukan dewa yang perkasa; ia dewa yang mengingat siapa saja yang lewat dan memastikan mereka sampai.',
         'Along the roads stand little shrines, no taller than a man, for the Earth God who guards travellers. He is no mighty god; he is the '
         'god who remembers everyone who passes and makes sure they arrive.'),
        ('Orang yang menyalakan sebatang dupa di sini akan merasa langkahnya lebih ringan untuk beberapa waktu. Para pengembara tua bilang '
         'itu bukan sihir, hanya rasa lega karena ada yang mendengarkan. Mereka tetap menyalakan dupa.',
         'Those who light a stick of incense here feel their steps lighter for a while. Old wanderers say it is not magic, only the relief of '
         'being heard. They light the incense anyway.')],
        sumber=('Sumber: Terinspirasi dari kuil-kuil kecil Tudi Gong (Dewa Tanah) di tepi jalan dan di pintu masuk desa-desa Tiongkok; '
                'Restu Jalan rancangan game.',
                'Source: Inspired by the small roadside shrines of Tudi Gong (the Earth God) at Chinese roadsides and village entrances; the '
                'Road Blessing is game design.'))

    L('jalan.singgah', ('Pos Singgah: Penginapan di Antara Kota', 'Waystations: Inns Between Towns'), [
        ('Dahulu pos-pos ini adalah stasiun kurir Raja Jalan: tempat kuda diganti dan surat diteruskan, sehingga sebuah kabar bisa menempuh '
         'ratusan li dalam beberapa hari. Kerajaan itu sudah lama runtuh, tetapi posnya tetap hidup, dibeli dan dijual dari satu pemilik ke '
         'pemilik berikutnya.',
         'Long ago these posts were the Road King\'s courier stations: where horses were changed and letters passed on, so news could cross '
         'hundreds of li in a few days. The kingdom is long gone, but the posts live on, sold from one owner to the next.'),
        ('Kini pos singgah adalah tempat terbaik untuk mendengar kabar. Pengembara, kurir gilda dan pedagang keliling bertukar cerita di '
         'warung sambil menunggu hujan reda. Pos-pos besar di simpang jalan bahkan punya cakram formasi yang terhubung ke stasiun ibu kota.',
         'Today a waystation is the best place to hear news. Wanderers, guild couriers and peddlers trade stories in the eatery while waiting '
         'for the rain to stop. The great posts at crossroads even have formation discs linked to the capital\'s stations.')],
        sumber=('Sumber: Terinspirasi dari pos-pos estafet (yizhan) jalan kekaisaran Tiongkok, yang pada dinasti Tang berdiri kira-kira '
                'setiap tiga puluh li; pos singgah dan cakramnya rancangan game.',
                'Source: Inspired by the relay posts (yizhan) of the Chinese imperial roads, which in the Tang dynasty stood roughly every '
                'thirty li; the waystations and their discs are game design.'))

    L('jalan.dagang', ('Karavanserai: Halaman Para Kafilah', 'Caravanserais: Courtyards of the Caravans'), [
        ('Di simpang jalan yang ramai, para pedagang membangun karavanserai: halaman dalam yang dikelilingi gudang dan kamar, dengan satu '
         'gerbang yang dikunci saat malam. Kafilah dari berbagai wilayah bertemu di sini dan bertukar barang yang tidak bisa ditemukan di '
         'tempat asal masing-masing.',
         'At busy crossroads merchants built caravanserais: an inner courtyard ringed by storerooms and chambers, with one gate locked at '
         'night. Caravans from many lands meet here and trade goods that cannot be found at home.'),
        ('Hukum karavanserai lebih tua dari hukum kota mana pun: siapa pun yang sudah masuk gerbang adalah tamu, bahkan musuhmu, dan tidak '
         'ada yang menghunus senjata di halaman. Pedagang yang melanggar hukum itu tidak akan pernah diterima lagi di karavanserai mana pun '
         'di sepanjang jalan.',
         'The caravanserai\'s law is older than any city\'s: whoever has passed the gate is a guest, even your enemy, and no one draws a weapon '
         'in the courtyard. A merchant who breaks that law will never again be received at any caravanserai along the road.')],
        sumber=('Sumber: Terinspirasi dari karavanserai di sepanjang Jalur Sutra, penginapan berhalaman tempat kafilah bermalam dan '
                'berdagang; hukum tamunya rancangan game.',
                'Source: Inspired by the caravanserais along the Silk Road, courtyard inns where caravans rested and traded; its law of guests '
                'is game design.'))

    L('jalan.kemah_begal', ('Kemah Begal: Api yang Tak Dinyalakan', 'Bandit Camps: Fires Never Lit'), [
        ('Beberapa puluh langkah dari jalan, tersembunyi di balik pepohonan, para begal mendirikan kemah dengan pagar kayu runcing dan api '
         'unggun yang tidak pernah dinyalakan saat ada kafilah lewat. Kebanyakan mereka petani yang kehilangan ladang, murid yang diusir, atau '
         'kultivator gagal yang tidak sanggup kembali ke desa.',
         'Some dozens of paces from the road, hidden behind trees, bandits pitch camps with sharpened palisades and campfires never lit while a '
         'caravan is near. Most are farmers who lost their fields, expelled disciples, or failed cultivators who cannot face their villages.'),
        ('Gilda Kultivator memasang misi untuk setiap kemah yang dikenal. Kata para pemburu, begal yang paling berbahaya bukan yang paling '
         'kuat, melainkan yang masih punya sesuatu untuk dilindungi.',
         'The Cultivators\' Guild posts a mission for every known camp. The hunters say the most dangerous bandit is not the strongest, but the '
         'one who still has something to protect.')],
        sumber=('Sumber: Terinspirasi dari para perampok jalan dalam Batas Air (Shuihu Zhuan) yang mencegat kafilah di jalan pegunungan; '
                'kemah dan penghuninya rancangan game.',
                'Source: Inspired by the highway robbers of the Water Margin (Shuihu Zhuan) who waylaid convoys on mountain roads; the camps and '
                'their dwellers are game design.'))

    L('jalan.penyeberangan', ('Penyeberangan: Rakit dan Tukang Dayung', 'River Crossings: Rafts and Ferrymen'), [
        ('Di sungai yang terlalu lebar untuk dijembatani, jalan berhenti di sebuah dermaga kecil dan berlanjut di dermaga seberang. Di '
         'antaranya hanya ada rakit dan seorang tukang dayung yang memungut satu batu roh dari setiap penumpang, kultivator atau bukan.',
         'At rivers too wide to bridge, the road stops at a small dock and continues at the dock across. Between them is only a raft and a '
         'ferryman who takes one spirit stone from every passenger, cultivator or not.'),
        ('Para tukang dayung punya pepatah: "Di atas air, semua orang sama beratnya." Bahkan ahli yang bisa terbang konon kadang naik rakit '
         'hanya untuk mendengar tukang dayung bercerita, karena tukang dayung tahu semua kabar dari kedua tepi.',
         'The ferrymen have a saying: "On the water everyone weighs the same." Even masters who can fly are said to ride the raft now and then '
         'just to hear the ferryman talk, for a ferryman knows the news of both banks.')])
