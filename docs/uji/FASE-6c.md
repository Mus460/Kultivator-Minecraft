# Checklist uji Fase 6c — Teknik Dewa

Persiapan: dunia uji baru dengan cheat aktif (jangan dunia utama: bagian L memahat blok sungguhan). Mulai di mode
Kreatif, pindah ke Bertahan untuk poin yang menyebut Bertahan. Aturan dan angka ada di
[PANDUAN 12](../PANDUAN.md#12-teknik-dewa-169-teknik), rancangannya di DESIGN bagian 22.

Perintah di bawah ditulis singkat: `uji ...` = `/kultivasi uji ...`, `debug ...` = `/kultivasi debug ...`,
`teknik ...` = `/kultivasi teknik ...`. Ubah ranah dengan `uji ranah <ranah> [tahap 1-4] [tingkat 1-9]` (tanpa tahap =
Puncak; contoh `uji ranah immortal_ascension 1 7` = Kenaikan Abadi tingkat 7, tahap Awal). Panggil sasaran dengan
`uji boneka [n] [bos]` atau `/summon zombie`.

**Selalu periksa:** tidak ada error di log, tidak ada blok yang berubah selama saklar Kehancuran MATI, hewan peliharaan,
penduduk desa, dan pemain lain tidak pernah terluka.

Poin bertanda **(belum ada)** adalah fitur yang belum dibuat di fase ini; cukup pastikan tidak ada crash.

## A. Simpanan, registri, audit
1. Muat dunia lama dari sebelum 6c: 23 teknik yang sudah dipelajari, teknik terpilih, jeda, dan Pedang Terbang tetap
   sama. Tulis id palsu `contoh_hilang` ke `learned` dengan `/data`, simpan, muat ulang → id tetap tersimpan, diabaikan,
   tanpa crash.
2. `uji teknik audit` → "169 spec OK · 168 untuk pemain". Baris merah menyebut id dan masalahnya (kunci bahasa, Sumber
   kosong, FX CAST, guncangan pamungkas, prasyarat, gema, batas). `tribulation_thunder_emperor_array` (33 huruf) harus
   ikut terdaftar.
3. Mulai pemain tunggal dua kali berturut-turut → log tidak menulis "duplicate id".
4. `uji teknik semua` → hitungan per set dan tingkat sama dengan tabel PANDUAN 12, lalu "Total 168 untuk pemain (169
   terdaftar)". Log tidak menulis "refused" untuk 9 teknik khusus.

## B. Perintah uji dan boneka
5. `uji teknik` → "Mempelajari N teknik baru: kau menguasai 168 dari 168", jeda nol, qi penuh.
6. `uji boneka 3` dan `uji boneka 1 bos` → boneka diam, tidak bisa mati, papan nama "Kerusakan terakhir: X → Y" (boneka
   bos: "Bos · kerusakan terakhir", angka terpotong batas bos). Boneka tidak memberi penguasaan, prestasi, atau
   pemahaman, hilang setelah 5 menit, saat kamu keluar, atau dengan `uji boneka hapus`.
7. Di Pemurnian Qi tatap boneka, `uji teknik heaven_punishing_sword` → langsung dirapal tanpa cek ranah, qi, atau jeda.
   Chat: P, koefisien, A/D/N, batas radius, jumlah sasaran, bagian bos, K dan blok, lalu per sasaran "mentah → diterima"
   dengan tanda "(batas bos)". Penguasaan tidak naik. Daftar setiap batas kecil di dalam bentuk **(belum ada)**.
8. `uji teknik pamer 3` → semua teknik dirapal bergiliran (yang lama dulu): judul nama Indonesia, subjudul baris
   Sumber, bilah aksi "n/total". Coba `pamer dao sword`, `pamer tingkat dewa`, `pamer 5 dari qi_palm`. Berhenti dengan
   `pamer stop`, saat mati, keluar dunia, atau pindah dimensi. Tanpa biaya, tanpa penguasaan, tanpa kawah.
9. `uji teknik bebas nyala` → roda dan cincin bisa dipakai tanpa gerbang, qi, atau jeda. `bebas mati` → normal lagi.
10. `uji teknik penguasaan semua 4` → semua manik penuh. `uji teknik gerbang` → Dewa Abadi Puncak, sudah naik, pemahaman
    90; kartu Dewa tidak terkunci lagi dan toast "Gerbang Dewa terbuka".

## C. Kekuatan dan skala
11. `debug teknik kekuatan` di Pemurnian Qi: P mendekati kekuatan lama. Di Dewa Abadi Puncak dengan pemahaman 50: P sekitar
    6.300. Baris itu juga menulis A/D/N, tingkat, batas bos per tingkat, K, dan batas blok.
12. Rapal Telapak Qi di Inti Emas lalu `uji ranah true_immortal 3` → jangkauan, ukuran visual, dan kerusakan jauh lebih
    besar; lapisan tingkat tinggi muncul (telapak raksasa, aksara 佛 pada Telapak Buddha, kubah dan lingkar rune Wilayah
    Dao). Qi dan jeda jurus tingkat rendah turun (jeda tidak pernah di bawah 0,5 dtk).
13. Pemulihan qi di Dewa Abadi sekitar ×1,4 dibanding Pemurnian Qi.
14. Penguasaan Puncak menggandakan kerusakan: bandingkan `uji teknik qi_palm` pada boneka di penguasaan 0 dan 4.

## D. Bos, Iblis Hati, arena
15. Warden asli, jurus terbaik tiap ranah: Inti Emas sekitar 12 rapalan, Transformasi Dewa 7, Kenaikan Abadi tingkat 9
    sekitar 2, Dewa Abadi Puncak 1.
16. Pukul Wither dengan jurus saat `mobGriefing` aktif → tidak ada blok yang pecah di sekitarnya (juga tengkorak biru).
    Murid tetap tidak melawan Wither.
17. Ender Dragon: sinar mengenai kepala, nova mengenai tubuh; naga tidak pernah terdorong atau terikat; kristal End tidak
    tersentuh. Membunuh naga dengan jurus menghitung prestasi pembunuhan pertama.
18. Iblis Hati sendiri: satu rapalan paling banyak 12% darahnya, tanpa penguasaan atau pemahaman.
19. Pelindung Dao, penjaga ujian: tetap pertarungan sungguhan (kekuatan lama × 1,5).
20. Latih Tanding, duel turnamen, satu lantai Alam Dewa Hampa: kerusakan sama dengan sebelum 6c (Telapak Buddha 2,5 D). Ke
    pemain paling banyak 15% darah per pukulan dan per rapalan, tanpa eksekusi, tanpa dorongan, tanpa kawah walau
    Kehancuran nyala.

## E. Penargetan dan keamanan blok (saklar Kehancuran MATI)
21. Piglin zombi yang tenang tidak terkena nova, tetapi sinar mengenainya. Benteng Nether tidak marah semua.
22. Hewan peliharaan, penduduk desa, dan pemain lain tidak pernah terkena. Creeper, ravager, Warden, Wither, dan naga tidak
    pernah terdorong. Tidak ada yang terdorong dalam 8 blok dari portal. Panggilan maya tidak pernah menyasar pemain.
23. Rapal semua jurus Api, Petir, Es, dan Tubuh (`uji teknik pamer 3 dao fire`, lalu `lightning`, `ice`, `body`) di dekat
    penangkal petir, tembaga, lahan, telur penyu, salju bubuk, kuali air, dan rumput → tidak ada blok berubah, tidak ada
    api di blok, cuaca dan waktu tidak berubah.
24. Proyektil tanpa pemilik (dispenser, kembang api) tidak pernah dihapus oleh jurus penangkis proyektil.

## F. Penguasaan dan pemahaman
25. Poin penguasaan naik dari rapalan, kena, bunuh, dan bos. `debug teknik mastery qi_palm 2` → toast "Telapak Qi mencapai
    Sukses Besar!", dentingan, cincin emas. Tingkat 3 butuh pemahaman 60, tingkat 4 butuh 90 (universal: Jiwa Baru
    Lahir / Penyatuan Dao). Kemajuan pertama ke Kesempurnaan Agung dan Puncak Jurus memberi pencapaian.
26. `debug teknik mastery tambah qi_palm 500`, lalu `teknik info qi_palm` → poin dan tingkat cocok. Naik tingkat langsung
    membuka jurus berprasyarat penguasaan tanpa keluar-masuk dunia.
27. Rapal ke spawner berulang dalam satu hari game: poin kena/bunuh berhenti di 200 per teknik; rapalan ke udara kosong
    berhenti di sekitar 20. Rapalan `uji` dan pukulan ke boneka tidak memberi poin.
28. Dengan Dao Pedang, rapal jurus Pedang yang mengenai monster: pemahaman +0,15 × faktor tingkat per rapalan, berhenti di
    +3,0 per hari. Melewati ambang langsung membuka jurus baru dengan toast "Pencerahan Jurus". Melewati pemahaman 60 dan
    90 memberi toast.

## G. Cara memperoleh teknik
29. Buku pesona: `debug set realm nascent_soul`, `debug set dao sword`, `debug set comprehension 30`, lalu
    `/give @s enchanted_book[stored_enchantments={"minecraft:sharpness":1}]`. Tooltip menulis isi kolam (urut tingkat)
    dan alasan terkunci. Jongkok + gunakan → jurus pertama yang bisa dipelajari, buku habis. Buku kedua saat semua sudah
    dikuasai atau terkunci → +5 pemahaman dan +15 penguasaan. Buku tanpa kolam
    (`/give @s enchanted_book[stored_enchantments={"minecraft:efficiency":1}]`) → jongkok + gunakan → tetap +30 kultivasi.
30. Slip Giok: `/give @s kultivator:jade_slip[kultivator:technique="overlord_fury"]` di Jiwa Baru Lahir → "Isi slip ini
    terlalu dalam untukmu" + syarat yang kurang, slip tetap. Penuhi syaratnya → jurus dipelajari, pencapaian *Membaca
    Slip Giok*; slip yang sama lagi → +25 penguasaan. Slip jurus Dewa → ditolak "tidak dapat diwariskan lewat slip giok".
31. Slip untuk murid: klik kanan murid sendiri (tanpa jongkok) dengan slip jurus Y → murid belajar tanpa qi guru; slip
    jurus N → ditolak dengan alasan; kamu sendiri tidak ikut mempelajarinya pada klik yang sama.
32. Paviliun Yue Lu: baris "Slip Giok Teknik" menampilkan semua jurus yang dijual dengan harga sesuai tingkat × faktor
    aula dan syarat tingkat aula (Kuning 1, Misterius 3, Bumi 5, Langit 7, Abadi 9). Pembelian memberi slip; yang ketiga
    di hari yang sama ditolak (baris terkunci, atau "Paviliun hanya memberikan paling banyak 2 slip giok sehari").
33. Giok Warisan: `debug set realm deity_transformation`, `debug set dao sword`,
    `/give @s kultivator:giok_warisan[kultivator:theme="sword"]` → klik kanan → pertanda 10 detik (cuaca tetap), tiga
    gelombang dalam 90 detik, penjaga akhir berdarah 4× dan terkena batas bos. Menang → pencapaian *Pewaris Warisan*, pilih
    1 dari 3 lewat chat atau `/kultivasi warisan pilih 2`; pilihan kedua → "Tidak ada warisan yang menunggu pilihanmu".
34. Kalah ujian (lari > 24 blok, darah < 20%, keluar, pindah dimensi, atau waktu habis) → giok kembali, istirahat 1 hari
    game. Pukulan mematikan saat ujian → tidak mati, ujian gagal; `/kill` tetap membunuh. Tema Dao lain, di bawah
    Transformasi Dewa, atau saat Penyimpangan Qi → ditolak, giok tidak hilang.
35. Loot: ulangi `/loot give @s loot minecraft:chests/end_city_treasure` sekitar 30 kali → sesekali Giok Warisan (8%).
    Elder Guardian pertama → giok tema Es pasti jatuh, berikutnya 15%.
36. Benih Api Surgawi: `debug set realm immortal_ascension 1 1`, `debug set dao ice`,
    `/give @s kultivator:heavenly_flame_seed[kultivator:flame_kind=1]` → jongkok + gunakan → Api Dingin Tulang Roh
    dipelajari, benih habis. Jongkok + klik murid dengan benih tetap memulai ritual murid.
37. Kitab Penyegel Iblis: `/give @s kultivator:lost_scripture_page[kultivator:scripture_page={set:4,piece:1}]` (lalu
    piece 2 dan 3) → gunakan → jurus SCR yang syaratnya terpenuhi dipelajari, lembar tidak habis. Tablet Perbendaharaan
    tetap hanya memulihkan 3 seni.
38. Prestasi: `debug teknik feat kills_with 25`, lalu `teknik info <jurus berprasyarat prestasi>` → kemajuan tampil.
    "Menguasai 30 teknik", "Dao kedua ≥ 30", dan "ikatan ≥ 50" wajib di samping jalur lain, juga untuk buku dan slip.
39. Gerbang Dewa: setelah `uji teknik gerbang`, `teknik info heaven_punishing_sword` menunjukkan semua centang hijau.
    Pencapaian *Gerbang Dewa* saat jurus Dewa pertama dipelajari.

## H. Kitab Teknik, cincin, roda, HUD
40. Tekan `-` → gulungan giok terbuka dengan tablet Kodeks, Cincin Segel, Mode Kehancuran (merah), Bantuan. Pada GUI
    320×240 semua baris saringan muat. `teknik kitab qi_palm` membuka kitab dengan kartu itu terfokus.
41. Saringan Dao, tingkat, Ranah ("Bisa kurapal", "Gerbang Dewa"), Jenis, keadaan (Dikuasai/Bisa Dipelajari/Terkunci),
    dan "Hanya pamungkas" bekerja bersamaan. Tutup lalu buka lagi → saringan dan urutan tetap.
42. Langsung ketik "pedang langit", "against", "zhuxian" → grid tersaring; huruf besar/kecil dan aksen tidak berpengaruh.
    Esc pertama berhenti mengetik, Esc kedua menutup.
43. Tooltip 20 kartu acak: nama Indonesia, nama Inggris, tingkat · jenis, Sumber ("Terinspirasi" miring abu-abu), qi dan
    jeda terskala, alasan terkunci. Tanda retak merah pada kartu yang memahat, lencana BARU sampai dibuka.
44. Bilah bambu: menempel kanan bila layar ≥ 420 GUI px. Angka langsung sama dengan `uji teknik <id>`; "Kawah: …" hanya saat
    Kehancuran nyala; "Di arena: kekuatan lama"; bilah penguasaan; daftar cara memperoleh dengan ✓/✗. "Pasang ke Cincin" →
    soket 3; "Pilih" → ubin HUD berganti; "Uji" hanya untuk op di Kreatif.
45. Cincin Segel: seret kartu ke soket, klik soket lalu kartu, klik kanan mengosongkan, "Isi otomatis", "Kosongkan" (klik
    kedua). Keluar-masuk dunia → cincin tetap. Juga `teknik slot 1 qi_palm`, `slot 2 kosong`, `slot isi`, `slot hapus`.
46. Ketuk G → rapal. Tahan G ≥ 250 ms → Roda Segel (jari-jari 56, 46 bila tinggi layar < 260). Mouse atau scroll memilih,
    WASD tetap jalan, lepas = pilih. Saat roda terbuka tekan 1–4 → toast "… menjadi segel cepat n", hotbar tidak berpindah.
    Esc atau klik kanan menutup tanpa memilih.
47. Pengaturan (tombol `'` → Pengaturan): "Lepas = rapal" nyala → melepas roda langsung merapal. "Tahan G untuk roda" mati →
    G merapal saat ditekan. Atur tombol "Roda Segel" dan "Segel Cepat 1–4" di Controls → segel cepat merapal soket 1–4, empat
    ubin kecil tampil di atas ubin HUD.
48. `,` / `.` hanya berputar di dalam cincin (semua teknik yang bisa dirapal bila cincin kosong), carousel 1,2 dtk.
49. HUD: bilah isi "Tekan lagi = lepas" mulai 50%, cincin salur yang berkedip saat hampir putus, pip tahap dan muatan,
    "Pamungkas N dtk", busur Perisai Qi, busur waktu wujud/wilayah/medan, tag merah KEHANCURAN. F1 menyembunyikan semua.
50. Toast giok: "Jurus baru", "Pencerahan Jurus", penguasaan naik dengan dentingan, "Gerbang Dewa terbuka".
51. Tablet Teknik di layar `'`: kartu "Buka Kitab Teknik" dengan "Teknik x/168", Kekuatan, penguasaan tertinggi, teknik
    terpilih. Bantuan memuat topik Teknik, Penguasaan, Kitab Teknik, dan Mode Kehancuran jurus sendiri.

## I. Arus jurus dan batas
52. Dengan `uji teknik bebas nyala`: satu jurus isi (lepas lebih awal = kekuatan sebanding, minimal separuh), satu salur
    (bergerak > 1,5 blok memutus), satu sikap (rapal lagi mematikan), satu bertahap (rapal ulang dalam jendela). Dipukul
    keras saat mengisi → "… terputus: separuh qi kembali kepadamu".
53. Tahap lanjutan dan rapal ulang Langkah Bayangan juga ditolak saat Penyimpangan Qi atau menerobos.
54. Rapal tujuh jurus wilayah/medan → yang ketujuh ditolak "Terlalu banyak jurus aktif". `debug teknik beban` menampilkan
    rapalan aktif, bayangan, proyektil, medan, boneka, DOT/IKAT/TAUT, peristiwa FX per detik, tugas kehancuran. Token
    partikel klien di baris itu **(belum ada)**.
55. Jurus terlarang (Dunia Kematian Darah, Pelarian Bayangan Darah, Bulan Ditarik Bintang Kembali, Hati Membara, Segel
    Harimau Alam Baka) di Bertahan: rapalan pertama meminta konfirmasi "Rapal lagi dalam 10 detik", darah berkurang tetapi
    tidak pernah mati; dengan 1,5 darah ditolak.
56. Setelah sebuah pamungkas, pamungkas lain terkunci 20 detik ("Meridianmu masih pulih …").

## J. Contoh per Dao (sekali per set)
57. Pedang: `uji ranah deity_transformation`, `uji teknik overlord_fury` di dekat zombi → lompatan dan gelombang kejut.
    `uji teknik zhuxian_sword_formation` → formasi persegi berdinding, pilar sudut, ledakan akhir.
58. Api: `uji teknik nine_suns_heavens_fury` → sembilan matahari di kerumunan terpadat. `heaven_fire_transformation` tiga
    kali dalam 30 detik → tiga tahap, lalu Lemah II tanpa pemulihan qi.
59. Es: `uji teknik freeze_mount_qi` → badai salju; detik ke-10 membeku; akhir pecah. Musuh beku menerima ×1,5 dari jurus
    "pecah".
60. Petir: `uji ranah golden_core`, `uji teknik zidian_whip` → petir ungu melompat ke 1–4 monster. `five_thunder_method`
    empat kali → angin, awan, guntur, hujan partikel; cuaca dunia tidak berubah.
61. Tubuh: `uji teknik octane_blast` pada satu boneka → 7 hentakan + 1 besar, ledakan tambahan bila kedelapannya kena.
    `disorderly_cloak_hammer` sambil diam → pukulan ke-9 besar; berjalan > 1,5 blok memutus.
62. Kehidupan: `uji teknik seven_treasure_pagoda` dekat murid → tiap detik satu tingkat pagoda dan penguat. Di dekat
    penduduk desa dan golem yang terluka, `guanyin_sweet_dew` → mereka ikut pulih, mayat hidup terluka.
63. Bayangan: `uji teknik shadow_devil_fusion` dekat 5 zombi → kamu tak terlihat, zombi kehilangan target dan diberi tanda
    ungu yang hanya terlihat olehmu; pukulan pertama ×2. `kunpeng_art` → sabit angin 128 blok menembus dinding.
64. Ruang: `uji teknik purple_gold_gourd` ke zombi → 1 detik kemudian zombi mati, loot tetap jatuh; ke boneka bos hanya 3×P
    + Lambat IV. `stellar_black_hole` → semua terseret ke inti (naga, Wither, Warden tidak tergeser).
65. Universal: `uji ranah dao_union`, rapal Seni Segel Kaisar 4 kali dalam 4 detik → empat segel. Di Inti Emas hanya tahap 1.
    `uji teknik ruyi_staff` → tongkat tumbang sepanjang bidikan.
66. Dewa: `uji teknik gerbang`, lalu `heaven_trampling`, `ancient_god_trample`, `bodhisattva_tree` → wujud dan pohon
    raksasa, guncangan layar kuat di akhir, tanpa kerusakan jatuh setelah mendarat.

## K. Teknik khusus
67. `worldwalking` (Dao Tubuh, Bertahan, `uji ranah nascent_soul`): bidik 30 blok → tenggelam tak terlihat, muncul di titik
    aman dengan pilar batu, tanpa luka jatuh. Bidik ke tebing atau void → "Tidak ada tempat keluar yang aman", qi tetap.
    Melompat lalu rapal → "Kakimu harus menapak tanah".
68. `lei_zhenzi_wings` (`uji ranah immortal_ascension 1 1`, Bertahan): benar-benar terbang, qi turun sekitar 3/dtk; rapal
    lagi menukik. Wujud atau qi habis di udara → Jatuh Lambat. Naiki Pedang Terbang lalu rapal sayap (dan sebaliknya) →
    ditolak ("Teknik terbang lain sedang membawamu" / "Seni terbang lain sudah membawamu"), dan sumber terbang yang
    sedang dipakai tidak tercabut.
69. `somersault_cloud` (`uji ranah deity_transformation`): terbang sekitar 64 blok dalam 2 detik di atas tanah, kebal,
    mendarat aman. Di gua atau menghadap tebing → "Tidak ada tempat mendarat yang aman ke arah itu".
70. `seventy_two_transformations`: Harimau → Elang → Ikan → Serangga → Kuil dengan rapal ulang dalam 5 detik. Elang menukik
    dari udara. Serangga di celah 1 blok saat wujud habis → dipindah aman. Rapal lagi dalam 10 detik setelah selesai →
    ditolak dengan sisa detik.
71. `netherworld_white_tiger`: tanpa murid terikat → ditolak. Murid ikatan ≥ 50, Dao lain, dalam 16 blok, qi ≥ 30% →
    keduanya bersinar 1 detik, harimau menerjang; qi murid berkurang separuh biaya, jeda murid ikut terisi.
72. `profound_mystery_fusion`: tanpa Dao kedua ≥ 30 → "Butuh Dao kedua dengan pemahaman ≥ 30". Dengan Dao kedua → efek
    pasangan (misalnya Api + Es melambatkan).
73. `six_dao_reincarnation` (`uji ranah immortal_ascension 1 7`): rapal 6 jurus berbeda dalam 2 menit, lalu Roda → keenamnya
    diputar ulang tiap 0,3 detik, lalu nova. Pamungkas, jurus khusus, dan sikap tidak ikut; tanpa kawah walau Kehancuran
    nyala.
74. `spatial_ancestral_symbol` (`uji ranah immortal_ascension`): 10 zombi tersebar → ditarik ke titik aman dekat pusat
    (Warden, creeper, pemain, hewan peliharaan tidak). Rapal ulang → berkedip di dalam medan; terlalu cepat → "Ruang masih
    mengendap"; setelah 10 detik → "Lipatan ruang sudah tertutup".
75. `time_spirit_domain`: zombi membeku; pukul satu zombi berkali-kali → tidak terluka selama 8 detik, lalu semua pukulan
    jatuh sekaligus ×1,5 plus 10×P. Boneka bos hanya terpaku 2 detik dan pukulan langsung masuk.

## L. Mode Kehancuran untuk jurus sendiri (dunia uji!)
76. Pemain tunggal: Kitab Teknik → tablet merah → klik segel sekali → tepat satu baris merah "MODE KEHANCURAN NYALA — …
    /kultivasi kehancuran pulihkan (10 menit). [Matikan] [Pulihkan]", satu detak jantung, tag HUD merah. Tanpa layar sumpah,
    tanpa tahan, tanpa centang. `/kultivasi kehancuran nyala|mati` juga bekerja; mati → "Mode Kehancuran mati — dunia aman
    lagi."
77. Di bawah Inti Emas: saklar bisa nyala, halaman menulis "Aktif, tetapi baru mengukir dari Inti Emas", tidak ada kawah.
    Inti Emas: `uji teknik kehancuran nyala`, lalu `uji teknik overlord_fury` 20+ blok dari kasur → mangkuk kecil.
    Pencapaian *Tanganku Membelah Bumi*. Dewa Abadi Puncak → kawah besar yang tumbuh dari tengah ke luar, dibatasi "blok per
    rapalan" di halaman Kehancuran.
78. Jongkok saat merapal → kerusakan sama, tanpa kawah. `uji teknik kehancuran mati` → rapalan `uji` tidak memahat.
79. Tidak ada blok hilang dalam 4 blok di sekitarmu (setinggi dunia, juga tepat di bawah kaki, juga saat bergerak), dalam 24
    blok dari kasur/titik bangkit siapa pun (juga tamu yang luring) dan dari pemain lain, 64 blok dari Prasasti Aula, 32 dari
    penduduk desa, 6 dari murid dan hewan peliharaan, serta di tapak sekte. Bila semua posisi terlindung → bilah aksi "Zona
    terlindung — tanpa kehancuran".
80. Peti, tungku, kasur, papan tanda, portal, obsidian, batuan dasar, air, dan blok di sebelah cairan tidak pernah tersentuh.
    `banana_leaf_fan` hanya mencabut dedaunan; `blue_silver_parasite` hanya melayukan permukaan tanpa lubang.
81. `/kultivasi kehancuran pulihkan` dalam 10 menit → blok kembali di tempat yang masih udara. Satu rapalan dengan beberapa
    bentuk = satu entri pulihkan. `/kultivasi kehancuran status` → baris "Jurus sendiri: NYALA · K … · … blok per rapalan …"
    dan daftar rapalan sendiri. Log server: `[Kultivator] Kehancuran: <pemain> self:<id> …`.
82. Rapal cepat lima jurus pemahat → sebagian menulis "Bumi masih runtuh — kehancuran dilewati" (paling sering sekali per 5
    detik). Server tetap sekitar 20 TPS.
83. Matikan saklar saat kawah masih terbentuk → berhenti setelah kelompok yang sedang berjalan ("Kehancuran berhenti setelah
    N blok; blok itu masih bisa dipulihkan"). Pemulihan yang sedang berjalan tetap lanjut.
84. Pindah dimensi atau mati sebelum pahatan tertunda jatuh → pahatan itu dibatalkan. Serangan bayangan maya, gema, putaran
    ulang, dan pantulan tidak pernah memahat. Proyektil yang meleset tidak menambah titik kawah.
85. LAN: tuan rumah bisa menyalakan; tamu tanpa izin ditolak dengan alasan izin. Server khusus: tanpa
    `/kultivasi kehancuran izin on` saklar ditolak. `izin off` membatalkan semua tugas dan saklar semua tamu terbaca mati.
86. Lebih dari 32 rapalan yang bisa dipulihkan di server → yang tertua dilepas dengan catatan chat ke pemiliknya.
87. Duel, Latih Tanding, terobosan, dan ujian Iblis Hati → tidak ada kawah walau saklar nyala. Murid tidak pernah memahat
    dengan teknik.

## M. 23 teknik lama
88. Semua id lama tetap bisa dirapal, juga `/kultivasi technique cast qi_palm`. Langkah Awan: lompatan udara 1/2/3/4/5/6
    (PQ/PF/IE/TD/KA/DA); mendarat dari ≥ 4 blok di penguasaan 4 menghentak monster.
89. Wujud Dharma membesar sesuai ruang di atas kepala, kembali tanpa tercekik di dinding; efek Ketahanannya ikut batas
    pengurangan kerusakan. Efek ramuan sikap dicabut di tingkatnya sendiri saat berakhir.
90. Wilayah Dao memberi denyut sesuai Dao (Petir menembakkan lebih banyak sambaran di ranah tinggi). Penjara Ruang dan Badai
    Angin tetap tidak mendorong di dekat portal.
91. Pedang Terbang tetap terbang; tabrakan sambil sprint-terbang di penguasaan 4 melukai tanpa mendorong.

## N. Murid
92. Rekrut murid, `uji murid <nama> ranah nascent_soul`, `uji teknik murid <nama>` (atau `uji murid <nama> teknik`) →
    "<nama> mempelajari N teknik". Murid tidak mendapat jurus N (misalnya worldwalking, lei_zhenzi_wings, blood_clones,
    time_spirit_domain); jurus ★n baru dari bintang n.
93. Tab Teknik murid: kartu kitab (terstempel bila dikuasai, gembok bila tidak bisa diajar), saringan Dao/keadaan/urutan,
    detail dengan Sumber dan alasan. Pencarian teks di tab ini **(belum ada)**.
94. Ajarkan jurus lama dan jurus baru dengan tombol Ajarkan → biaya qi guru sesuai tingkat (Kuning 10% … Dewa 50%),
    penguasaan murid mulai dari tingkat guru − 1. Jurus dengan syarat bintang → "butuh lebih banyak bintang".
95. "Siapkan": slot tempur 3 + ranah/2 + bintang/9 (maks. 10). Bertarung → murid merapal dari slotnya (area saat ≥ 3
    musuh, sembuh saat < 50%, pamungkas hanya melawan bos atau ≥ 4 musuh), tidak pernah menyerang creeper, ghast, atau
    Wither, tidak merapal sikap yang sama dua kali; sikapnya berakhir setelah 10 detik atau tanpa musuh dalam 24 blok.
96. `debug murid teknik mastery <nama> semua 3` → penguasaan murid berubah.
97. Gelar bintang baru (kelipatan 9): chat menawarkan satu tombol per jurus Dewa yang layak; klik menjalankan
    `/kultivasi teknik pahamkan <murid> <id>`. Daftar yang sama di tab Teknik murid dengan tombol "Pahami (gelar bintang)",
    gratis. Murid Bayangan ★46 memahami Wilayah Karma sendiri.
98. Pewaris mendapat semua jurus yang bisa diajar milik guru pada tingkat penguasaan guru.

## O. Performa dan efek
99. Kualitas Minimal: paling banyak 2 tampilan raksasa per rapalan (hanya Langit ke atas), klon menjadi siluet partikel.
    "Otomatis": di bawah 40 FPS turun ke Minimal (tooltip "Sekarang: Minimal"), 10 detik di atas 70 FPS kembali Normal.
100. "Tampilan raksasa" mati → pedang, balok, aksara, dan laser raksasa diganti partikel. Tampilan raksasa hanya ada di
     layarmu dan hilang saat ganti dimensi.
101. Guncangan mengikuti "Guncangan layar" (Ringan bawaan) dan Damage Tilt vanilla (0 = tanpa guncangan). Kilatan mengikuti
     "Kilatan layar" dan skala efek layar; "Kurangi gerak" mematikan kilatan dan kilau pelangi Dewa.
102. LAN: "Efek pemain lain: Ringan" → jurus pemain lain ×0,25 tanpa tampilan raksasa; "Mati" → tidak terlihat.
103. Sepuluh perapal sekaligus atau `uji teknik pamer` dengan `debug teknik beban`: FPS layak, server di dalam anggaran.
104. Salinan model AVATAR/CLONE untuk murid dan makhluk lain **(belum ada)**: mereka memakai garis partikel. Untuk pemain
     tampil salinan model tembus pandang.

## P. Sumber
105. Arahkan kursor ke 20 kartu acak dari setiap set: setiap kartu punya baris "Sumber: …" (English: "Source: …"). Nama yang
     tidak terverifikasi ditulis "Terinspirasi dari …". 22 dari 23 teknik lama "Terinspirasi"; Indra Ilahi bersumber langsung.

## Q. DeathGuard
106. Pegang Totem lalu terima pukulan mematikan saat Tiga Helai Rambut Penyelamat aktif → totem terpakai lebih dulu. Tanpa
     totem → rambut menyelamatkan (darah 30%, mundur 8 blok), lalu kunci 10 menit berlaku untuk semua teknik penyelamat
     (juga Tubuh Teratai Nezha).
107. `/kill`, jatuh ke void, sambaran kesengsaraan, dan Penyimpangan Qi tidak pernah dicegah oleh teknik, perisai, atau
     pengurangan kerusakan.
