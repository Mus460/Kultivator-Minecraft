# Kultivator — mod kultivasi xianxia untuk Minecraft 26.3 (Fabric)

**Bahasa Indonesia** · [English below](#english)

Mod Fabric berdiri sendiri di mana **pemain** berkultivasi seperti di novel xianxia: memurnikan qi, menembus ranah dari
Manusia Fana sampai Dewa Abadi, bertahan dari Tribulasi Langit, memilih Jalan Dao, menguasai **169 Teknik Dewa** yang
makin dahsyat di setiap ranah, terbang di atas pedang, meracik pil, menghadapi Iblis Hati, membangkitkan binatang roh,
dan mendirikan sekte. Kamu mendirikan **Aula Sekte**, menerima murid dari Hari Penerimaan Murid, dan membimbing mereka
naik tanpa batas sampai **Bintang Abadi**, bahkan sampai melampauimu. Lalu sektemu **membangun dirinya sendiri**: pilih
satu dari sembilan templat kompleks sekte klasik, setor bahan, dan saksikan sekte tumbuh melewati sembilan tingkat.
Setiap teknik serta setiap fitur sekte dan murid diadaptasi dari cerita kultivasi sungguhan, dan sumbernya (karya +
konsep) tertulis di game dan di panduan. Sejak Fase 7 **dunianya hidup**: monster elit Binatang Iblis di setiap ranah,
Batu Roh, Inti Binatang, herba roh berumur, urat roh dan Kultivasi Tertutup di Gua Kediaman, jimat, alkimia lanjutan,
tribulasi dengan lingkar petir dan Hu Fa bersama teman, tablet **Jalan** yang menuntun pemain baru, dan Bintang Abadi untuk
dirimu sendiri.

📖 **Panduan pemain lengkap: [docs/PANDUAN.md](docs/PANDUAN.md)** · Daftar uji per fase: [docs/uji/](docs/uji/) ·
Rancangan dan aturan kode: [docs/DESIGN.md](docs/DESIGN.md)

**Aturan keras:** efek dan kekuatan mod ini tidak pernah membakar, merusak, memasang, atau mengubah blok apa pun.
Petir tribulasi hanya partikel dan suara (tanpa entity petir), cuaca dan waktu tidak diubah, dan tribulasi tidak pernah
membunuh — kalau gagal, pemain terluka (Penyimpangan Qi). Hanya ada dua pengecualian:
* **Pembangunan Sekte** memasang blok **hanya di dalam tapak yang kamu lihat sebagai hologram lalu kamu konfirmasi
  sendiri**, hanya di chunk yang sudah termuat. Tanah dan tanaman alami yang dipindahkan dikreditkan ke Gudang Bahan
  Sekte, bangunan buatan pemain dan blok terlindung tidak pernah dibongkar diam-diam, dan di luar tapak tidak ada yang
  berubah ([aturannya di panduan](docs/PANDUAN.md#20b17-keamanan-blok-pembangunan-sekte)).
* **Mode Kehancuran**, **mati secara bawaan**, dalam dua bentuk:
  * **jurusmu sendiri** ("saklar gampang", Fase 6c): satu klik di Kitab Teknik atau `/kultivasi kehancuran nyala|mati`,
    tanpa layar sumpah, dengan satu baris peringatan merah berisi perintah pulihkan. Selama nyala, jurus serang mulai Inti
    Emas memahat kawah, parit, dan retakan yang makin besar seiring ranah. Jongkok saat merapal = tanpa kawah. Di server
    khusus dan untuk tamu LAN butuh izin server
    ([peringatan di panduan](docs/PANDUAN.md#1211-mode-kehancuran-untuk-jurusmu-sendiri-saklar-gampang));
  * **Jurus Pemusnah murid** tingkat tertinggi: terkunci di balik izin server, sumpah pemain, dan segel per murid
    ([peringatan di panduan](docs/PANDUAN.md#31-mode-kehancuran-peringatan)).

  Keduanya memakai batas blok, jeda saat server lambat, zona terlindung (4 blok di sekitarmu, 24 blok dari kasur dan
  pemain lain, 64 dari aula, tapak sekte, desa), dan tombol Pulihkan 10 menit.

## Fitur

* **9 ranah, 65 posisi** — Manusia Fana, Pemurnian Qi, Pembangunan Fondasi, Inti Emas, Jiwa Baru Lahir, Transformasi
  Dewa, Penyatuan Dao, Kenaikan Abadi (9 tingkat), Dewa Abadi; masing-masing dengan tahap Awal, Menengah, Akhir, Puncak.
  Kultivasi dari monster, bijih, meditasi, pil, dan pencerahan; leher botol di Tahap Puncak menyimpan **Qi Tertimbun**
  (dipakai untuk Perkokoh Fondasi, isi ulang setelah gagal, dan Bintang Abadi pemain).
* **Terobosan** — kebangkitan, lemparan peluang, lalu **Tribulasi Langit** dengan varian Permukaan, Api Neraka (Nether),
  dan Kehampaan (End), gelombang **Pelindung Dao**, **Kualitas Terobosan** (Retak/Biasa/Sempurna/Surgawi),
  **Penyimpangan Qi**, dan mode Hardcore opsional.
* **Meditasi** — pose bersila, tempat spiritual (puncak gunung, Hutan Ceri, suar, kecubung, End, bulan purnama), urat roh
  dengan kepadatan qi, Momen Pencerahan, dan tiga **formasi pengumpul qi** dari blok vanilla.
* **Akar spiritual** — upacara pengujian dengan 9 elemen dan 5 mutu, Pil Pembersih Sumsum.
* **8 Jalan Dao** dengan Pemahaman Dao.
* **Teknik Dewa: 169 teknik** (146 baru + 23 lama yang diperkuat; 168 bisa dipelajari pemain), masing-masing diambil dari
  cerita kultivasi sungguhan dengan baris **Sumber** di game (Formasi Pedang Zhuxian, Murka Langit Sembilan Matahari,
  Membekukan Gunung Qi, Tianzun Guntur Sembilan Langit, Injakan Dewa Kuno, Pohon Kuno Bodhisattva, Lubang Hitam, Gunung Lima
  Unsur, Langkah Penginjak Langit, …). Enam tingkat **Kuning → Misterius → Bumi → Langit → Abadi → Dewa**; kekuatan melonjak
  di setiap ranah sampai sekitar ×165 di Dewa Abadi, dengan area, durasi, dan efek yang ikut membesar (pedang raksasa,
  aksara, sinar, avatar, robekan langit, guncangan kamera; semuanya khusus klien). **Penguasaan Jurus** lima tingkat,
  **Kitab Teknik** (saringan, pencarian, bilah bambu dengan angka langsung), **Cincin Segel** 12 soket, **Roda Segel**,
  segel cepat, Gerbang Dewa, Slip Giok, Ujian Warisan, kolam buku pesona, prestasi, dan murid yang belajar serta bertarung
  dengan teknik baru. Batas bos, aturan arena lama, dan anggaran performa untuk laptop lemah.
* **Pil** — barang vanilla sebagai pil dengan racun pil, dan **Alkimia** di tungku Kuali di atas Api Unggun dengan pil
  bermutu Rendah sampai Sempurna.
* **Iblis Hati**, **Binatang Roh** (serigala dan rubah hingga Serigala Langit / Rubah Ekor Sembilan), **Formasi
  Pelindung**, **Sekte** dan **Latih Tanding**.
* **Pengakuan Langit** — peristiwa sinematik saat menjadi Dewa Abadi: pilar cahaya emas, langit terbuka, suara langit,
  aura pelangi, kebal Penyimpangan Qi, dan regenerasi abadi.
* **Visual mewah** — aura per ranah, jubah sesuai Dao, pedang di punggung, mata bercahaya, Lingkaran Dao, pedang terbang
  di bawah kaki; HUD bola dantian dan layar "Gulungan Giok"; **Kualitas Efek** Minimal / Normal / Maksimal / Otomatis.
* **Murid** — pengikut yang ikut berkultivasi, diajari tekniknya, diberi pil, menerobos dengan tribulasinya sendiri,
  bertarung di sisimu, dan bisa dibangkitkan dengan Papan Jiwa.
* **Aula Sekte** — Prasasti Aula Sekte yang kamu buat dan pasang sendiri; Tingkat Aula 1–9 dari ketenaran dan bangunan
  yang kamu susun dari blok vanilla (Paviliun Kitab, Formasi Agung, Aula Latihan, Menara Langit Membara); wilayah sekte,
  Formasi Agung dengan Penjaga Formasi, dan catatan sekte.
* **Hari Penerimaan Murid** — calon datang ke prasasti saat fajar; Ujian Masuk tiga tingkat dengan pilar Duan dalam
  warna jiwa; vonis Lulus / Tercatat / Ditolak; Token Kenaikan Abadi; calon istimewa (Pengemis di Gerbang, Murid
  Regresor, Dantian Tersumbat).
* **Alam jiwa, bakat, fisik langka** — tujuh warna jiwa, enam bakat, sembilan fisik (Tubuh Racun Malapetaka, Naga Purba
  Taixu, Tulang Mahatinggi, Pupil Ganda, Nadi Dewa Sesat, …).
* **Jenjang Sekte & Poin Kontribusi** — Murid Tercatat, Luar, Dalam, Inti, Tetua, Tetua Agung, dan Pelindung Sekte;
  Kantor Misi, Paviliun Yue Lu, dan tiga Seni Sekte yang Hilang dari lembar kitab yang tercecer.
* **Kegiatan sekte** — tugas (Pikul Air, Kebun Seratus Obat, Dapur Api, Ayam Ekor Roh) dan Lumbung Sekte; ekspedisi,
  Utus Perekrut, Tanah Terlarang Berwarna Darah, Bahtera Purba; Kompetisi Daftar Kuat dan Turnamen Elit, Perang Seratus
  Dinasti, Alam Dewa Hampa tanpa batas lantai, Latihan Khusus, Menara Langit Membara.
* **Kekuatan murid** — Jurus Pamungkas per Dao, Benih Api Surgawi dan Fusi Api Guru-Murid, Pusaka Natal dengan lipatan
  penempaan, Kitab Abadi Tak Mati, Pelarian Jiwa Baru Lahir, Sayap Kunpeng, Formasi Tujuh Peri.
* **Pembangunan Sekte** — sembilan templat kompleks sekte klasik bergaya donghua dan manhua, dari **Sekte Tak
  Berperingkat** (25×29) sampai **Istana Langit** (161×161, lima puncak di atas awan), masing-masing dengan sembilan
  tingkat yang setiap tingkatnya sudah tampak selesai: Aula Pendiri, dapur dan Kebun Seratus Obat, Paviliun Kitab, tembok
  dan air, cakram Formasi Agung, aula utama di atas teras marmer, lapangan latihan, pagoda dan aula leluhur, lalu Menara
  Langit Membara. Pilih templat di Prasasti, lihat tapaknya sebagai hologram, putar, konfirmasi sekali, lalu **cukup setor
  bahan**: setiap bangunan mulai dan membangun dirinya sendiri begitu bahannya lengkap. Atap melengkung dengan ujung
  terangkat dalam glasir Tinta, Giok, atau Langit; sembilan kategori bahan di Gudang Bahan Sekte dan Peti Galian; Misi
  Pembangunan dan Ekspedisi Pengumpulan untuk PK; sampai empat murid sebagai Pekerja Bangunan; Naik Templat, Periksa &
  Perbaiki, Bongkar, dan Pindah dengan pengembalian bahan; penyumbang di LAN; laju hemat untuk laptop lemah (tanpa memuat
  paksa chunk, berhenti saat server sibuk).
* **Jalan Abadi** — ikatan, Segel Jiwa, Hu Fa dan Tahan Petir, Fondasi Dao; **Bintang Abadi** tanpa batas dengan Tujuh
  Bencana dalam Tiga Ujian, gelar tahap Stellar, Klon Ilahi, Lampu Jiwa; pewaris dan Sembilan Segel, Cincin Jiwa Guru,
  **Murid Melampaui Guru**; Jurus Pemusnah yang aman blok, dan **Mode Kehancuran** opsional (mati secara bawaan).
* **Dunia yang Hidup (Fase 7)** — **Binatang Iblis**: monster alami di dekat pemain Pemurnian Qi+ kadang bangkit sebagai
  Siluman, Binatang Iblis, atau Raja Siluman (bilah bos), dengan unsur, skill bertanda yang bisa dihindari, dan afiks
  (Perisai Unsur, Penyerap Qi, Pulih Cepat). **Batu Roh** empat tingkat dari tambang, peti, pedagang keliling, elit, dan
  ekspedisi: bahan bakar meditasi, qi seketika, daya formasi pelindung sampai 48 jam, dan bonus mutu terobosan.
  **Inti Binatang** untuk dimurnikan, makanan binatang roh, dan katalis. **Tanaman Roh**: delapan herba berumur (sampai
  "Seribu Tahun") yang tersembunyi di rumput, ladang, bunga, bambu saat badai, korus, dan jamur. **Urat Roh** Agung/Kaya/
  Tipis dengan kepadatan sampai 200%, rumus meditasi baru, **Kultivasi Tertutup**, **Gua Kediaman** yang terlindung, dan
  Kompas Urat Roh. **Delapan jimat** yang kamu tulis sendiri (Petir, Awan Api, Perisai, Pengikat, Pulang, Penangkal Petir,
  Seribu Li, Kejernihan). **Alkimia Lanjutan**: panci 10 bahan, katalis batu dan inti, herba pembantu, empat pil baru,
  resep alternatif, tablet **Kitab Alkimia**. **Tribulasi Hidup**: lingkar petir mulai Transformasi Dewa (keluar = hanya
  terserempet), Menantang Langit, dan **Hu Fa Tribulasi** bersama saudara sekte. Tablet **Jalan**: 20 Langkah Pertama
  dengan pelacak dan hadiah, Ujian Fondasi, Kewajiban Harian. Panel **Persiapan** dan HUD tribulasi langsung. **Anugerah
  Kenaikan** di setiap tingkat Kenaikan Abadi, **Perkokoh Fondasi**, dan **Bintang Abadi** tanpa batas untuk pemain.
  **Lima Unsur** dalam pertarungan, **Relik di Peti Kuno** dengan Indra Ilahi yang mencium harta, Kejenuhan Bertarung,
  dan kenyamanan HUD (pengukur pedang terbang, peringatan bahaya saat meditasi, cetak biru formasi).
* Semua teks dalam **Bahasa Indonesia** dan **English**. Kompatibel dengan Sodium, Iris, ImmediatelyFast, EMF/ETF,
  Lithium, FerriteCore.

## Status pengembangan

| Fase | Isi | Status |
|------|-----|--------|
| 0 | Proyek, data pemain (tahan respawn/keluar-masuk/pindah dimensi), perintah uji | ✅ |
| 1 | Ranah & tahap, leher botol + Qi Tertimbun, terobosan + tribulasi, meditasi, pil vanilla, bar Qi, layar info | ✅ |
| 2 | Akar spiritual, Jalan Dao, teknik, kitab teknik | ✅ |
| 3 | Iblis Hati, binatang roh, formasi pelindung, sekte | ✅ |
| 4 | Visual mewah, alkimia, Pengakuan Langit (event Dewa Abadi), pencapaian akhir, panduan pemain | ✅ |
| 5 | Murid: bot pengikut yang diajari teknik dan ikut berkultivasi, perintah uji | ✅ |
| 6 | Sekte Abadi & murid OP: Aula Sekte, Hari Penerimaan Murid, bakat dan fisik, jenjang dan Poin Kontribusi, tugas, misi, turnamen, Alam Dewa Hampa, kekuatan murid, Bintang Abadi, pewaris, Mode Kehancuran opsional ([daftar uji](docs/uji/FASE-6.md)) | ✅ |
| 6b | Pembangunan Sekte (9 templat x 9 tingkat): pilih templat, pratinjau hologram, konfirmasi sekali, lalu cukup setor bahan dan sekte membangun dirinya sendiri; Gudang Bahan Sekte, Misi Pembangunan, Ekspedisi Pengumpulan, Pekerja Bangunan, Naik Templat/Perbaiki/Bongkar/Pindah, penyumbang LAN, perintah uji `uji sekte` ([daftar uji](docs/uji/FASE-6b.md), [panduan](docs/PANDUAN.md#20b-pembangunan-sekte)). Belum diuji di game; tagihan belum diseimbangkan | ✅ |
| 6c | Teknik Dewa: 169 teknik (146 baru + 23 lama diperkuat) bersumber cerita kultivasi, makin OP sesuai ranah, Penguasaan Jurus, Kitab Teknik, Cincin dan Roda Segel, Slip Giok, Ujian Warisan, Gerbang Dewa, teknik untuk murid, Mode Kehancuran jurus sendiri lewat saklar gampang, perintah uji `uji teknik` dan `uji boneka` ([daftar uji](docs/uji/FASE-6c.md), [panduan](docs/PANDUAN.md#12-teknik-dewa-169-teknik)). Belum diuji di game; beberapa hal kecil belum ada (pencarian teks di tab Teknik murid, token partikel di `debug teknik beban`) | ✅ |
| 7 | Dunia yang Hidup: Binatang Iblis (elit), Batu Roh, Inti Binatang, herba roh berumur, Urat Roh dan Kultivasi Tertutup, Gua Kediaman, jimat, Alkimia Lanjutan dan Kitab Alkimia, lingkar petir, Menantang Langit, Hu Fa Tribulasi, tablet Jalan (Langkah Pertama, Ujian Fondasi, Kewajiban Harian), Persiapan dan HUD tribulasi, Anugerah Kenaikan, Perkokoh Fondasi, Bintang Abadi pemain, Lima Unsur, Relik di Peti Kuno, keseimbangan, kenyamanan HUD, perintah uji `uji elit|batu|herba|inti|pil|relik|tribulasi|jimat|jalan|hud|...` ([daftar uji](docs/uji/FASE-7.md), [panduan](docs/PANDUAN.md#33-dunia-yang-hidup-fase-7)). Belum diuji di game; ditunda ke 7b: efek pil baru untuk murid, lingkar petir tujuh warna, bencana bintang pemain, tooltip Sumber anugerah di tab Status | ✅ |
| 7b | Momen Epik: tribulasi dan semua momen besar menjadi adegan sinematik langsung di game: shot kamera pendek yang aman dan bisa dilewati (Backspace), bilah layar lebar, kartu judul kaligrafi, langit berubah, pusaran awan dan petir raksasa, pilar cahaya, gerbang, efek layar, gerak lambat tampilan saja, suara berlapis dan musik; Mode Rekam; pengaturan `Kamera sinematik: Penuh / Singkat / Mati`, `/sinematik`, perintah uji `uji momen <id>|tribulasi_penuh|semua|berhenti` ([daftar uji](docs/uji/FASE-7b.md), [panduan](docs/PANDUAN.md#54-momen-epik-fase-7b)). Belum diuji di game; belum dicoba dengan paket shader | ✅ |
| 7c | Formasi Sekte: 24 formasi bersumber cerita dalam 13 aliran dan 5 tingkat, berpusat pada balok Mata Formasi dan pola yang kamu bangun sendiri (hologram cetak biru), Daya dari kecubung dan Batu Roh, Kas Daya, murid sebagai penjaga formasi, batas tumpuk, tablet Formasi di aula dengan peta simpul perpindahan, formasi pembunuh yang hanya melukai monster bermusuhan, perintah `formasi ...` dan uji `uji formasi pasang|semua|musuh|...` ([daftar uji](docs/uji/FASE-7c.md), [panduan](docs/PANDUAN.md#20c-formasi-sekte)). Belum diuji di game; Panji Induk Formasi ditunda | ✅ |
| 8 | Jalan Dunia Kultivasi: empat Alam Rahasia di dimensi datar `kultivator:alam_rahasia` (ruang uji sebagai entitas, penjaga, Gulungan Harta, Mode Ringan tanpa blok), Kultivator Liar (penyergapan, sarang perampok) dan Serbuan Sekte Iblis ke aula dengan Segel Penjarah, Tungku Api Bumi dengan 6 tingkat pusaka, tempa ulang +12, Pusaka Natal Diri dan Roh Senjata, Cincin Penyimpanan (9-54 slot, jejak jiwa), Desa Bawahan (sumpah, kesetiaan, upeti, permohonan, penjarahan), lem progresi (Jalan bab 6-10, baris ke-4 Ujian Fondasi, kewajiban harian, resep pil alternatif, diskon bintang), layar Dunia, perintah `alam|pusaka|cincin|desa|serbuan` dan uji `uji alam|perampok|sergap|serbuan|pusaka|tempa|roh|cincin|desa|hud dunia` ([daftar uji](docs/uji/FASE-8.md), [panduan](docs/PANDUAN.md#55-jalan-dunia-kultivasi-fase-8)). **Belum diuji di game** (hanya dikompilasi) | ✅ |
| 9 | Petualangan Dewa Abadi: Kesengsaraan Kenaikan ke Alam Abadi (dimensi pulau melayang `kultivator:alam_abadi`, batas lunak, tangkapan kehampaan, Jimat Lintas Alam), Batu Asal Abadi dan Esensi Abadi, makhluk Alam Abadi, tiga Binatang Purba (Kun-Peng, Zhulong, Taotie) dengan Gulungan Gunung dan Laut, Tujuh Bencana Bintang dan Murka Langit (Utusan, Panglima Langit), Dunia Batin (petak herba, rubah, Manusia Batu, mutiara, Bencana Bumi), Jurus Ciptaan Sendiri, Lelang Abadi dan Pengejaran, Jalan bab 11-15, Layar Keabadian, perintah `abadi|purba|bencana|batin|ciptaan|lelang` dan uji `uji abadi|purba|bencana|murka|utusan|batin|ciptaan|lelang` ([daftar uji](docs/uji/FASE-9.md), [panduan](docs/PANDUAN.md#56-petualangan-dewa-abadi-fase-9)). **Belum diuji di game** (hanya dikompilasi) | ✅ |

## Perintah singkat

`/kultivasi` atau `/cultivate` — `info`, `meditate`, `breakthrough`, `hardcore on|off`, `root test|info`,
`dao [info|choose <dao>]`, `teknik [kitab|info|pilih|rapal|daftar|slot ...]`, `technique list|select|cast`,
`visual <bagian> on|off`, `beast list`, `formation list`, `murid list|panggil|ikut|tinggal|meditasi|bantu|tahan`,
`aula info|nama <nama>`, `kehancuran nyala|mati|status|sumpah|pulihkan`, `warisan pilih <n>`,
`aula bangun [templat|pratinjau <1-9> [arah] [glasir]|konfirmasi|setor [semua|butuh]|naik <2-9>|perbaiki|bongkar|lepaskan]`,
`jalan`, `resep`, `harian`, `kawal <pemain>`, `gua tetapkan|lihat|hapus`, `fondasi perkokoh`, `bintang padatkan [semua]`.
Perintah uji cepat untuk operator: `/kultivasi uji ...`, `/kultivasi uji murid <nama|semua> maks|ranah|bintang <n>`,
`/kultivasi uji sekte templat <1-9>|tingkat <0-9> [instan|cepat]|sumber|pamer|audit|info|reset|...`
([daftar lengkap](docs/PANDUAN.md#20b16-perintah)), dan
`/kultivasi uji teknik [semua|<id>|pamer [detik]|bebas|penguasaan|gerbang|kehancuran|audit|murid]` serta
`/kultivasi uji boneka [n] [bos]|hapus` ([daftar lengkap dengan contoh](docs/PANDUAN.md#1212-perintah-teknik)).
Fase 7: `uji elit <1-3> [unsur] [jenis] [afiks]|hapus`, `uji tribulasi <ranah> [lapis] [biasa|cincin|kembar]`,
`uji kawal <0-3>`, `uji batu|herba|inti|pil|relik ...`, `uji urat|tertutup|gua|jimat|anugerah|jiwa|fondasi|bintang ...`,
`uji jalan|harian ...`, `uji unsur`, `uji kejenuhan <n>`, `uji hud`
([daftar lengkap dengan contoh](docs/PANDUAN.md#34-perintah-uji-cepat-kultivasi-uji-)).
Fase 7b: `/sinematik penuh|singkat|mati|vfx|rekam|info|reset`, `uji momen daftar|<id>|tribulasi_penuh <ranah>|semua|berhenti`
([panduan](docs/PANDUAN.md#545-perintah-uji-op)). Fase 7c: `formasi [info|pola <id>|setor|isi|penjaga|simpul|pindah ...]`,
`uji formasi pasang <id>|semua|musuh <n>|aula <1-9>|audit|bersih ...` ([panduan](docs/PANDUAN.md#20c3-perintah-modul-a)).
Fase 8: `dunia [alam|pusaka|musuh|desa]`, `alam daftar|masuk <id> [lapisan]|keluar|ikut`, `pusaka info|ikat|lepas|tautkan|lepastautan`,
`cincin`, `desa daftar|info <nama>|nama <lama> <baru>`, `serbuan info`; uji `uji alam <id>|ruang|selesai|gagal|ringan`, `uji perampok <1-4>`,
`uji sergap`, `uji serbuan mulai|menang|kalah|data`, `uji pusaka|tempa|roh|cincin ...`, `uji desa ikrar|minta|jarah ...`, `uji hud dunia`
([daftar lengkap dengan contoh](docs/PANDUAN.md#5511-perintah-uji-op)).
Fase 9: `abadi [tab]|info|naik|lintas|ikut|konversi`, `purba info`, `bencana info|mulai [k]`, `batin [info]|seduh <n>`,
`ciptaan daftar|hapus <1-3>`, `lelang info`; uji `uji abadi naik|masuk|pulang|batu|konversi|pribumi ...`, `uji purba <jenis> [fase]`,
`uji bencana <k> [lulus|gagal]`, `uji murka ...`, `uji utusan <n>`, `uji batin lahir|hari|bencana|herba|rubah|batu|mutiara ...`,
`uji ciptaan ...`, `uji lelang sesi|tutup|kejar` ([daftar lengkap dengan contoh](docs/PANDUAN.md#5612-perintah)).
`/sekte` atau `/sect` — `create`, `invite`, `join`, `leave`, `kick`, `disband`, `info`, `list`, `chat`,
`spar <pemain>|accept|decline`. Perintah uji operator ada di bawah `/kultivasi debug` — daftar lengkapnya di
[panduan](docs/PANDUAN.md#33-perintah-uji-untuk-operator-kultivasi-debug-).

## Tombol bawaan

`G` teknik terpilih (tahan = Roda Segel) · `-` Kitab Teknik · `;` meditasi · `'` layar kultivasi · `,` `.` ganti teknik di
Cincin Segel · `\` pedang terbang · lompat di udara = Langkah Awan · Roda Segel dan Segel Cepat 1–4 belum diatur. Semua
bisa diganti di Options → Controls → Key Binds → Kultivator.

## Konfigurasi

`config/kultivator.json` (server): `xpMultiplier`, `hardcoreForced`, `daoProtectorEvent`, `tribulationDamageScale`,
`heartDemonTrials`, `protectiveFormations`, `announceBreakthroughs`, `sectHall`, `recruitmentIntervalDays`,
`sectEvents`, `tournamentIntervalDays`, `forbiddenGroundIntervalDays`, `sectConstruction` dan `sectBuild...` (laju,
batas per tick, jeda MSPT, jangkauan, tabungan, templat terbesar, pengembalian bahan, tanpa monster di sekte), serta
`modeKehancuran` (bawaan `false`) dan batas-batas `kehancuran...` (termasuk `kehancuranPlayerTechniques`,
`kehancuranCasterSafeRadius`, `kehancuranHomeSafeRadius`), serta `techniqueDamageMultiplier`, `techniqueAreaCap`,
`techniqueBossCaps`, `techniqueMaxActiveCasts`, `techniqueDisplays`, serta saklar Fase 7 `eliteMonsters`,
`eliteChanceScale`, `structureLoot`, `spiritStoneMining`, `herbHarvest`, `guideRewards`, `dailyTasks`,
`tribulationTelegraph`. `config/kultivator-client.json` (klien):
`effect_quality`, `quality_auto`, `hud`, `shake`, `flash`, `phantoms`, `others_fx`, `wheel_hold`, `release_cast`,
`reduce_motion`, `cooldown_ping`, `danger_alert`, dan saringan Kitab Teknik. Penjelasan di
[panduan](docs/PANDUAN.md#52-berkas-konfigurasi).

## Build

Java 25. `gradlew.bat compileJava compileClientJava jar --no-daemon` → `build/libs/kultivator-<versi>.jar`.
Teks ditulis per modul di `lang-src/*.en_us.json` dan `lang-src/*.id_id.json`; task `mergeLang` menggabungkannya dan
gagal kalau ada kunci yang hanya ada di satu bahasa. Rancangan lengkap: [docs/DESIGN.md](docs/DESIGN.md).

---

## English

A standalone Fabric mod for Minecraft 26.3 in which **you** cultivate like in a xianxia novel: refine qi, break through
nine realms (65 ranks) from Mortal to True Immortal, survive heavenly tribulations (with Nether and End variants and
waves of Dao Protector monsters), walk one of eight Daos, master 169 techniques, fly on your sword, refine pills in an
alchemy furnace, face your heart demon, awaken spirit beasts, build protective formations, found a sect and spar with
friends. Reaching True Immortal triggers a cinematic ascension event (a golden pillar of light, the heavens opening, a
celestial voice) that grants a rainbow aura, immunity to Qi Deviation and slow regeneration. Visuals (auras, robes, back
swords, glowing eyes, Dao circles) scale with a client Effect Quality setting.

You also take on disciples. Found a **Sect Hall** with a stele you craft and place yourself; candidates arrive at dawn,
take an entrance test, and join at a sect rank. Disciples carry soul colours, talents and rare physiques, earn
contribution points, run duties and expeditions, fight in tournaments and the endless Void God Realm, unlock Dao
ultimates, Heavenly Flames and a Natal Treasure, and keep growing past True Immortal through endless **Immortal Stars**
and seven calamities, until they may surpass their master. Then the sect **builds itself**: choose one of nine
classical sect templates (from the 25×29 Unranked Sect to the 161×161 Heavenly Court of five peaks above the clouds),
preview the footprint as a hologram, confirm once, and just deposit materials. Each building starts and rises by itself
as soon as its materials are in, through nine tiers that each already look finished. Every sect and disciple feature
adapts a named concept from real cultivation stories, and the game and the guide name each source.

**God-level techniques (phase 6c).** 169 techniques (146 new, the 23 old ones made stronger; 168 for the player), each
taken from a real cultivation novel, donghua or manhua with a Source line in game. Six grades from Yellow to Divine;
power climbs at every rank to about 165× at True Immortal, with areas, durations and client-only visuals (giant swords,
glyphs, beams, avatars, sky tears, camera shake) growing along. Five mastery levels, a Technique Codex with filters,
search and live numbers, a 12-socket Seal Ring with a seal wheel and quick seals, jade slips, inheritance trials, book
pools, feats, the Divine Gate, and disciples who learn and fight with the new arts. Boss caps, the old linear power in
arenas, and strict performance budgets for weak laptops.

**A living world (phase 7).** Elite demonic beasts (three tiers with elements, telegraphed skills you can dodge and
affixes) near any player from Qi Refining on; four grades of spirit stones (meditation fuel, instant qi, formation power,
breakthrough quality), beast cores and eight aged spirit herbs hidden in ordinary plants; living spirit veins up to 200%
density, closed-door cultivation and a protected cave abode; eight talismans you inscribe yourself; deeper alchemy with
catalysts, auxiliary herbs, four new pills and a recipe tablet; a living tribulation with lightning rings, Defy the
Heavens and co-op Tribulation Guardians; a Path tablet with 20 first steps, Foundation Trials and daily chores; a
breakthrough forecast and a live tribulation HUD; Immortal Ascension boons, Strengthen Foundation and endless Immortal
Stars for the player; the five elements in combat; relics in structure chests. Every feature keeps its verified source,
nothing places or breaks a block, and the phase is compiled but not yet tested in game.

**The world path (phase 8).** Four secret realms on the generated flat floor of a `kultivator:alam_rahasia` dimension
(trial chambers built only from entities, a guardian, a treasure scroll, and a zero-block Light Mode fallback); rogue
cultivators who ambush travellers and demonic sects that raid your Sect Hall in waves; an Earthfire Forge whose heat comes
from the block below it, six artifact grades, refinement to +12, a natal treasure bound inside your body with a growing
weapon spirit; storage rings from 9 to 54 slots bound by a spiritual imprint; vassal villages that pledge, pay tribute,
send petitions and must be defended; and progression glue (Path chapters 6-10, a fourth Foundation Trial row, new chores,
alternative pills, a star discount). No new block-changing code; compiled but not yet tested in game.

Nothing in this mod ever burns, breaks, places or changes a block; tribulation lightning is particles and sound only,
the weather and time are never changed, and a failed tribulation injures you (Qi Deviation) instead of killing you.
There are two exceptions. **Sect Construction** places blocks only inside the footprint you previewed as a hologram and
confirmed yourself, only in loaded chunks; natural ground it moves is credited to the sect's material store, and
player-made and protected blocks are never removed silently. The opt-in **Destruction Mode** is OFF by default. For your
own techniques it is one click in the Technique Codex (or `/kultivasi kehancuran nyala|mati`), with a red warning line
and the undo command; damaging arts from Golden Core on then carve craters, trenches and fissures, never while you sneak,
and a dedicated server or a LAN guest also needs the server permission. For top-tier disciple arts it needs server
permission, a player oath and a per-disciple seal release. A safe column around you, beds, other players, halls, sect
sites and villages stay protected, and a 10-minute undo window restores the blocks.
The full player guide is in Indonesian: [docs/PANDUAN.md](docs/PANDUAN.md); every rule and number is in
[docs/DESIGN.md](docs/DESIGN.md); per-phase test lists in [docs/uji/](docs/uji/).

License: MIT.
