"""38ST content: Kitab Panduan chapters 18 "Kota Besar" and 19 "Tempat-Tempat Dunia" (DESIGN 38.D.11; steps defined by 38W
in guide/GuideChapters: save indexes 17 and 18, step ids c18.* and c19.*). Texts only; the numbers in them are the ones of
DESIGN 38.B (services, law, events, teleport), 38.C (villages, compounds, caves, roads) and 38.R (repairs)."""

BAB = {
    18: (('Kota Besar', 'The Great Capital'),
         ('Ibu kota netral tujuh sekte: layanan, gilda, Lelang Agung, hukum Yamen, acara, alur Bayangan di Balik Takhta dan stasiun teleport.',
          'The neutral capital of the seven sects: services, guilds, the Grand Auction, Yamen law, events, the Shadow Behind the Throne '
          'story and the teleport stations.')),
    19: (('Tempat-Tempat Dunia', 'Places of the World'),
         ('Kampung dan desa klan, kompleks sekte, gua pertapa, makam warisan, sarang, jalan bertonggak dan perbaikan tempat yang rusak.',
          'Villages and clan villages, sect compounds, hermit caves, inheritance tombs, lairs, milestone roads and repairing damaged places.')),
}

LANGKAH = {
    18: [
        ('tiba', ('Tiba di ibu kota', 'Reach the capital'),
         ('Ibu kota pertama berdiri di cincin 1800-3200 blok dari pusat dunia bila tanahnya layak; tembok 432 x 432 blok, sembilan distrik '
          'di dalam dan dua di luar. Jalan Kekaisaran berlapis batu dari setiap gerbangnya menuntunmu ke sana, dan tonggak jalan '
          'menggambar jalannya di peta (/kultivasi peta).\n'
          'Genderang malam berbunyi pada 22000: gerbang menutup dan baru membuka lagi pada 1000. Tertinggal di luar? Bicaralah dengan '
          'penjaga gerbang: 2 NB (Nama Baik minimal 0), gratis bila kau Terhormat. Buronan ditolak.\n'
          'Hukum pertama tertulis di atas gerbang: "Di dalam tembok, pedang tidur."',
          'The first capital stands in the ring 1800-3200 blocks from the world centre when the land allows; its wall is 432 x 432 blocks, '
          'with nine districts inside and two outside. The stone Imperial Road from each of its gates leads you there, and road milestones '
          'draw the road on your map (/kultivasi peta).\n'
          'The night drum sounds at 22000: the gates close and open again at 1000. Left outside? Talk to the gate guard: 2 NB (Standing at '
          'least 0), free if you are Honoured. Wanted people are refused.\n'
          'The first law is written above the gate: "Within the walls, swords sleep."')),
        ('layanan', ('Pakai tiga layanan', 'Use three services'),
         ('Ibu kota punya sekitar empat puluh layanan. Coba tiga yang berbeda, misalnya:\n'
          'Penginapan dan rumah makan (istirahat, hidangan berefek), Kuil Langit (dupa 3 NB, Restu Kuil 60 menit, sekali per hari '
          'menurunkan tekanan iblis hati), Balai Tabib (memulihkan Penyimpangan Qi), Perpustakaan (kuliah harian 9000-10000), Rumah '
          'Uang (brankas, surat wesel, gadai), Pasar Binatang Roh, Pandai Pusaka, Pasar Malam (12500-18000).\n'
          'Nama Baik di kota ini menentukan harga: Orang Asing +10 %, Warga Dikenal punya brankas, Terhormat dan Pahlawan Kota mendapat '
          'diskon.',
          'The capital has about forty services. Try three different ones, for example:\n'
          'Inn and eatery (rest, dishes with effects), the Temple of Heaven (incense 3 NB, Temple Blessing for 60 minutes, once a day it lowers '
          'heart demon pressure), the Healers\' Hall (cures Qi Deviation), the Library (daily lecture 9000-10000), the Money House (vault, '
          'money orders, pawning), the Spirit Beast Market, the Forge, the Night Market (12500-18000).\n'
          'Your Standing in this city sets the prices: Strangers pay +10 %, Known Residents get a vault, the Honoured and City Heroes get '
          'discounts.')),
        ('gilda', ('Lencana Gilda pertama', 'First guild badge'),
         ('Di Distrik Gilda berdiri tiga gilda: Alkemis (Tetua Dan Chen), Pandai Pusaka (Ou Ye Muda) dan Kultivator (Lin Fei Si Lengan '
          'Satu). Ambil misi dari papan Gilda Kultivator; setiap misi selesai menambah poin lencana, dan kertas lencana pertamamu dicap '
          'merah pada Bintang 1.\n'
          'Bintang yang lebih tinggi membuka diskon toko gilda, papan misi berpangkat lebih tinggi dan misi tambahan; kenaikan bintang '
          'tertentu butuh ujian di loket gilda. Seratus kultivator muda terbaik dicatat di Daftar Naga Muda.',
          'Three guilds stand in the Guild District: the Alchemists (Elder Dan Chen), the Forge (Ou Ye the Younger) and the Cultivators '
          '(Lin Fei the One-Armed). Take missions from the Cultivators\' Guild board; each finished mission adds badge points, and your '
          'first badge paper is stamped red at Star 1.\n'
          'Higher stars open guild shop discounts, higher-ranked mission boards and extra missions; some stars need an exam at the guild '
          'counter. The hundred finest young cultivators are recorded on the Young Dragon List.')),
        ('lelang', ('Ikut Lelang Agung', 'Join the Grand Auction'),
         ('Lelang Agung digelar di Balai Lelang setiap hari dunia ke-5 dari tujuh (hari % 7 == 5), pukul 12000-15000, dengan delapan lot. '
          'Datanglah sebelum mulai, duduk, lalu tawar lewat layar lelang. Kau juga boleh menitipkan barangmu sendiri sebagai lot; '
          'komisinya masuk Kas Kota.\n'
          'Menang lelang menambah Nama Baik +1.',
          'The Grand Auction is held in the Auction House on the 5th world day of every seven (day % 7 == 5), from 12000 to 15000, with eight '
          'lots. Arrive before it starts, take a seat, then bid on the auction screen. You may also consign your own goods as a lot; the '
          'commission goes to the City Treasury.\n'
          'Winning a lot adds +1 Standing.')),
        ('hukum', ('Baca papan Yamen (pilihan)', 'Read the Yamen board (optional)'),
         ('Yamen di utara Alun-alun Agung mencatat kejahatan yang dilihat saksi: melukai warga 5 poin, membunuh warga 20, mencuri dari peti '
          'milik orang 4, merusak bangunan 2 per 10 blok, melompati tembok saat jam malam 3. Poin turun 2 per hari.\n'
          '10 poin: Dicurigai (penjaga membuntutimu). 25: Buronan (wajahmu di papan, penjaga menyerang, toko dan stasiun menolak). 60: '
          'Buronan Berat (Kapten Penjaga memburumu).\n'
          'Selesaikan dengan membayar denda (poin x 3 x (pita + 1) NB) di Yamen atau kepada penjaga, atau Serahkan Diri dan duduk di sel '
          'beberapa saat. Kabur dari sel menambah 20 poin.',
          'The Yamen north of the Grand Square records crimes seen by witnesses: hurting a resident 5 points, killing a resident 20, stealing '
          'from someone\'s chest 4, damaging buildings 2 per 10 blocks, climbing the wall after curfew 3. Points drop by 2 a day.\n'
          '10 points: Suspected (guards follow you). 25: Wanted (your face on the board, guards attack, shops and stations refuse you). 60: '
          'Most Wanted (the Guard Captain hunts you).\n'
          'Settle it by paying the fine (points x 3 x (band + 1) NB) at the Yamen or to a guard, or Surrender and sit in a cell for a while. '
          'Escaping a cell adds 20 points.')),
        ('acara', ('Ikut satu acara kota', 'Join a city event'),
         ('Kalender ibu kota (satu acara besar per hari): Pertemuan Pil (hari % 7 == 2, 6000-9000, lomba alkimia lima menit), Lelang '
          'Agung (hari % 7 == 5), Hari Pembukaan Gerbang (hari % 10 == 4, lapak sekte dan calon murid), Turnamen Agung Antar-Sekte '
          '(hari % 14 == 9), Festival Lentera (malam bulan purnama, 13000-18000, 40 teka-teki di kios lentera) dan Pertemuan Sekte '
          '(hari % 30 == 15).\n'
          'Kadang genderang perang berbunyi: Gelombang Binatang menyerbu satu gerbang dalam tiga gelombang. Bertahanlah di tembok; '
          'gerbang yang roboh hanya sebuah entitas, tidak ada blok yang berubah.\n'
          'Acara hanya hidup bila ada pemain dalam 256 blok dari pusat kota.',
          'The capital calendar (one big event per day): the Pill Gathering (day % 7 == 2, 6000-9000, a five-minute alchemy contest), the Grand '
          'Auction (day % 7 == 5), Gate Opening Day (day % 10 == 4, sect stalls and disciple candidates), the Grand Inter-Sect Tournament (day % '
          '14 == 9), the Lantern Festival (full-moon nights, 13000-18000, 40 riddles at the lantern stalls) and the Sect Assembly (day % 30 == 15).\n'
          'Sometimes the war drums sound: a Beast Tide storms one gate in three waves. Hold the wall; a fallen gate is only an entity, no block '
          'changes.\n'
          'Events only come alive when a player is within 256 blocks of the city centre.')),
        ('takhta', ('Bayangan di Balik Takhta', 'Shadow Behind the Throne'),
         ('Saat pemain pertama masuk ibu kota, alur cerita kota dimulai: Kapten Tie Hu menyambut di gerbang dan Hakim Bao menempel '
          'pengumuman orang hilang di Kampung Kumuh. Tujuh bab berjalan sekitar 24 hari dunia; bila kau tidak ikut, setiap bab tetap maju '
          'sendiri setelah batas waktunya.\n'
          'Selesaikan bab 3 (Pewaris Sakit): racik atau carikan penawar bagi Yan Ruyi. Pilihanmu di bab-bab berikutnya menentukan siapa '
          'yang naik takhta dan bagaimana kota memperlakukanmu.\n'
          'Lihat babmu dan quest yang berjalan di /kultivasi jurnal.',
          'When the first player enters the capital, the city\'s story begins: Captain Tie Hu greets you at the gate and Judge Bao posts a '
          'notice about people gone missing in the slums. Seven chapters run over about 24 world days; if you stay out of it, each chapter '
          'still moves on by itself after its deadline.\n'
          'Finish chapter 3 (The Ailing Heir): brew or find an antidote for Yan Ruyi. Your choices in the later chapters decide who takes the '
          'throne and how the city treats you.\n'
          'See your chapter and running quests in /kultivasi jurnal.')),
        ('teleport', ('Pakai stasiun teleport', 'Use a teleport station'),
         ('Di stasiun formasi, Penjaga Formasi mengirimmu ke tempat yang sudah kau ketahui: kota, ibu kota lain, kampung besar, desa klan, '
          'pos singgah besar atau kompleks sekte yang sikapnya minimal Netral. Ongkos ceil(2 + jarak / 250) NB per orang, paling banyak '
          '120; murid pengikut dalam 8 blok ikut (paling banyak 4). Kanal 3 detik batal bila kau diserang.\n'
          'Perjalanan Awan di Dermaga Langit menjangkau tujuan sejauh 600 blok atau lebih, siang hari 1000-11000, ongkos ceil(6 + jarak / '
          '150) NB.',
          'At a formation station the Formation Keeper sends you to places you already know: towns, other capitals, large villages, clan '
          'villages, great waystations or sect compounds that stand at least Neutral with you. The fare is ceil(2 + distance / 250) NB per '
          'person, at most 120; following disciples within 8 blocks come along (at most 4). The 3-second channel breaks if you are attacked.\n'
          'Cloud Travel at the Sky Dock reaches destinations 600 blocks or more away, in daytime 1000-11000, for ceil(6 + distance / 150) NB.')),
    ],
    19: [
        ('kampung', ('Kunjungi kampung', 'Visit a village'),
         ('Kampung fana tumbuh menurut biomanya: rumah kayu dan sawah di padang, rumah panggung di rawa, rumah oasis di gurun, sawah '
          'bertingkat di bukit. Ada dusun, desa, desa besar berpasar mingguan dan desa klan bertembok.\n'
          'Di lapangan desa ada lonceng, sumur dan kuil kecil Dewa Tanah; Kepala Desa memberi kabar dan permintaan tolong, Tabib Desa '
          'mengobati Penyimpangan Qi ringan. Setiap hari dunia ke-12 (hari % 12 == 0) ada Festival Panen.\n'
          'Setiap kampung punya legendanya sendiri; dengarkan di /kultivasi legenda.',
          'Mortal villages grow by biome: timber houses and paddies on the plains, stilt houses in the swamp, oasis houses in the desert, '
          'terraced paddies in the hills. There are hamlets, villages, market towns with a weekly market and walled clan villages.\n'
          'The village square has a bell, a well and a small Earth God shrine; the Headman gives news and asks for help, the Village Healer '
          'treats light Qi Deviation. Every 12th world day (day % 12 == 0) there is a Harvest Festival.\n'
          'Every village has its own legend; read it in /kultivasi legenda.')),
        ('ikrar', ('Ikrarkan kampung (pilihan)', 'Pledge a village (optional)'),
         ('Kampung dapat bersumpah setia kepada sektemu lewat Plakat Pelindung Sekte di tiang loncengnya, seperti desa bawahan biasa. '
          'Kampung terikrar membayar upeti dan setiap 10 hari mengirim satu calon murid berbakat ke aulamu.\n'
          'Desa klan lebih angkuh: kalahkan Patriark dalam duel atau capai sikap klan 50. Tuan Muda klan suka menantang tamu yang ranahnya '
          'rendah; duelnya ramah.',
          'A village can swear loyalty to your sect through a Sect Protection Plaque on its bell post, like an ordinary vassal village. A '
          'pledged village pays tribute and every 10 days sends one gifted disciple candidate to your hall.\n'
          'Clan villages are prouder: beat the Patriarch in a duel or reach clan standing 50. The clan\'s Young Master likes to challenge '
          'low-realm guests; the duel is a friendly one.')),
        ('kompleks', ('Bertamu ke kompleks sekte', 'Visit a sect compound'),
         ('Di belakang gerbang setiap sekte NPC kini berdiri kompleksnya: halaman murid luar, aula dalam dan aula puncak, bertingkat di '
          'lereng gunung. Gaya bangunannya mengikuti aliran: Lurus putih-giok, Netral kayu-emas, Iblis hitam-merah.\n'
          'Sikap sekte menentukan sambutan: Sekutu dan Bersahabat boleh masuk aula dalam, bertanding persahabatan dan berbelanja murah di '
          'pasar sekte; Netral hanya sampai halaman; Bermusuhan diperingatkan di gerbang; Perang berarti penjaga menyerang.',
          'Behind every NPC sect\'s gate now stands its compound: the outer disciples\' court, the inner hall and the peak hall, terraced up '
          'the mountain. The buildings follow the alignment: Righteous white and jade, Neutral wood and gold, Demonic black and red.\n'
          'The sect\'s stance decides your welcome: Allies and Friends may enter the inner hall, spar in a friendly bout and buy cheaply at the '
          'sect market; Neutral guests reach only the court; the Hostile are warned at the gate; at War the guards attack.')),
        ('pertapa', ('Menjadi murid pertapa', 'Become a hermit\'s student'),
         ('Gua Pertapa tersembunyi di lereng sunyi. Pertapanya memberi tiga ujian lintas hari: bawa herba berumur 100 tahun atau lebih, '
          'bermeditasi lima menit tanpa terputus di ruangnya, lalu jawab satu koan Dao.\n'
          'Lulus, ia mengajarkan satu teknik Dao-nya atau menambah pemahamanmu, lalu pergi mengembara. Guanya menjadi tempat meditasi yang '
          'baik dan bisa kau jadikan Gua Kediaman.',
          'Hermit Caves hide on quiet slopes. The hermit sets three trials over several days: bring a herb 100 years old or more, meditate '
          'five minutes without a break in their room, then answer one Dao koan.\n'
          'Pass, and the hermit teaches one of their Dao techniques or deepens your comprehension, then leaves to wander. The cave becomes a '
          'good meditation place you can make your Cave Abode.')),
        ('warisan', ('Pewaris makam', 'Heir of a tomb'),
         ('Makam Warisan berpintu stela di padang terbuka. Ranah yang disarankan tertulis di kartu masuk: tier 1 Fondasi, tier 2 Jiwa Baru '
          'Lahir, tier 3 Penyatuan Dao. Murid tidak bisa ikut masuk.\n'
          'Sisa jiwa leluhur menceritakan kisahnya, lalu menguji Hati (tiga ilusi), Kekuatan (boneka penjaga) dan Pemahaman (susun lempeng '
          'glif). Hadiahnya sekali per pemain per makam: giok teknik Dao makam dan pusaka. Giok Warisan menunjuk makam terdekat yang kau '
          'ketahui.',
          'Inheritance Tombs open beneath a stele in open country. The suggested realm is written on the entry card: tier 1 Foundation, tier 2 '
          'Nascent Soul, tier 3 Dao Union. Disciples cannot come in.\n'
          'The ancestor\'s remnant soul tells its story, then tests Heart (three illusions), Strength (guardian puppets) and Comprehension (arrange '
          'the glyph tablets). The reward comes once per player per tomb: the tomb\'s Dao technique jade and a treasure. An Inheritance Jade '
          'points to the nearest tomb you know.')),
        ('sarang', ('Bersihkan sarang', 'Clear a lair'),
         ('Sarang Binatang dijaga binatang elit; binatang roh peliharaanmu tumbuh lebih cepat bila bertarung di sana, dan kadang ada anak '
          'binatang tersesat yang bisa dijinakkan.\n'
          'Sarang Kultivator Iblis tersembunyi di balik pintu tebing yang terbuka bila ranahmu cukup. Kalahkan Tetua Iblis, buka sangkar para '
          'tawanan, lalu kawal mereka ke kampung atau kota terdekat (Nama Baik +3 di kota tujuan). Gilda Kultivator sering memasang misi '
          'untuk sarang seperti ini.',
          'Beast Dens are guarded by an elite beast; your tamed spirit beasts grow faster fighting there, and sometimes a stray cub can be tamed.\n'
          'Demonic Cultivator Lairs hide behind cliff doors that open when your realm is high enough. Defeat the Demon Elder, open the captives\' '
          'cages, then escort them to the nearest village or town (+3 Standing in the destination town). The Cultivators\' Guild often posts '
          'missions for lairs like these.')),
        ('jalan', ('Sentuh tiga tonggak jalan', 'Touch three road milestones'),
         ('Jalan menghubungkan ibu kota, kota, kampung, gerbang sekte dan mulut gua: Jalan Kekaisaran batu, Jalan Desa tanah dan Jalan '
          'Setapak ke gua. Menyentuh tonggak batu di tepinya menggambar jalan itu dan kedua ujungnya di petamu (/kultivasi peta).\n'
          'Sepanjang jalan ada Kuil Jalan (dupa 1 NB: Restu Jalan, Kecepatan I selama 3 menit), pos singgah, karavanserai, penyeberangan '
          'rakit (1 NB) dan kemah begal yang sebaiknya dihindari bila kau masih lemah.',
          'Roads link capitals, towns, villages, sect gates and cave mouths: stone Imperial Roads, earthen Village Roads and Footpaths to the '
          'caves. Touching a stone milestone beside them draws that road and both its ends on your map (/kultivasi peta).\n'
          'Along the way are Road Shrines (incense 1 NB: Road Blessing, Speed I for 3 minutes), waystations, caravanserais, raft crossings (1 NB) '
          'and bandit camps best avoided while you are still weak.')),
        ('perbaikan', ('Ganti rugi atau bantu tukang', 'Pay for damage or help the builders'),
         ('Bila sebuah tempat rusak, para tukang memperbaikinya pelan-pelan di bawah perancah; semua blok hanya dikembalikan seperti '
          'semula. Bila kaulah yang merusak, juru tagih datang membawa Tagihan Ganti Rugi: bayar, tawar, atau tolak (menolak dicatat '
          'sebagai kejahatan).\n'
          'Kau juga boleh membantu tukang bekerja atau menyumbang bahan untuk poin bantu dan Nama Baik. Tempat yang terlalu takut padamu '
          'tidak berani menagih; mereka memperbaiki sendiri, tetapi jauh lebih lama.',
          'When a place is damaged, its builders repair it slowly under scaffolding; blocks are only ever put back as they were. If you did the '
          'damage, a bill collector brings a Damage Bill: pay, haggle, or refuse (refusing is recorded as a crime).\n'
          'You may also work alongside the builders or donate materials for help points and Standing. A place too afraid of you does not dare '
          'send a bill; it repairs itself, but far more slowly.')),
    ],
}


def pct(pair):
    """Guide texts are translatable components and carry no %s: every literal percent sign is doubled."""
    return tuple(t.replace('%', '%%') for t in pair)


def isi(g):
    for bab, (judul, desc) in BAB.items():
        g.tx('tempat_panduan', f'kultivator.guide.chapter.{bab}', judul)
        g.tx('tempat_panduan', f'kultivator.guide.chapter.{bab}.desc', desc)
    for bab, steps in LANGKAH.items():
        for sid, title, body in steps:
            g.tx('tempat_panduan', f'kultivator.guide.c{bab}.{sid}.title', title)
            g.tx('tempat_panduan', f'kultivator.guide.c{bab}.{sid}.body', pct(body))
