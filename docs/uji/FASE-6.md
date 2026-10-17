# Checklist uji Fase 6 — Sekte Abadi & Murid OP

Persiapan: dunia uji dengan cheat aktif, mode Kreatif. Detail aturan dan angka ada di [PANDUAN bagian 20–31](../PANDUAN.md#20-aula-sekte).
Setiap fitur punya baris **Sumber** (karya + konsep) di tooltip; poin O memeriksanya.

**Memajukan waktu:**
* Hari, fajar, dan tengah hari (Hari Penerimaan, Kompetisi, Tanah Terlarang) ikut jam dunia: pakai `/time set` atau
  `/time add 24000`.
* Durasi (hasil tugas, tim yang pergi, Pelarian Jiwa, kebangkitan cincin, jeda) memakai waktu game yang **tidak** ikut
  `/time add`. Pakai `/tick sprint <waktu>` (misalnya `/tick sprint 12000` atau `/tick sprint 3d`), atau
  `/kultivasi debug event misi selesai` untuk semua tim yang pergi.

**Murid uji cepat:** `/give @s kultivator:recruitment_scroll 5` (hanya kreatif) atau `/kultivasi debug murid summon`.
Naikkan dengan `/kultivasi uji murid <nama|semua> ranah <ranah> [tahap]` atau `... maks`. Nama berspasi memakai garis
bawah; tekan Tab untuk saran.

## A. Aula Sekte
1. `/give @s kultivator:sect_hall_stele`, pasang → pilar giok, lonceng, chat "Aula Sekte berdiri! Sekte <nama> membuka
   gerbangnya.", pencapaian *Gerbang Sekte Terbuka*. Klik kanan → gulungan aula di tablet Aula (nama, segel tingkat 1,
   bar ketenaran 0/100). Tombol 1–9 berpindah tablet.
2. Resep (survival): ambil Batu Magnet → resep terbuka. G B G / A L A / G B G (emas, rak buku, blok kecubung, Batu
   Magnet) → Prasasti. Tambang dengan beliung → jatuh sebagai item; dengan tangan → tidak jatuh. Piston tidak bisa
   mendorongnya.
3. Ganti nama di tablet Aula (*Ganti Nama*) atau `/kultivasi aula nama Lembah_Giok`. `/kultivasi aula info` menampilkan
   tingkat, ketenaran, batas murid, radius, kas, letak, jadwal.
4. Pindah aula: pasang prasasti kedua jauh dari yang pertama → chat "sudah berdiri" + petunjuk. Jongkok + klik kanan
   tangan kosong → "Pindahkan Aula ke sini?"; ulangi dalam 10 detik → pindah (ketenaran, kas, lumbung, calon ikut). Coba
   lagi hari yang sama → disuruh menunggu besok.
5. Prasasti dalam 128 blok dari aula pemain lain → tetap diam.
6. Tertidur: tambang prasasti aktif → chat "aula tertidur", asap abu, calon pergi; `aula info` menulis "Tertidur".
   Pasang lagi → aula bangun dengan ketenaran dan kas utuh.
7. Tingkat & bangunan: `/kultivasi debug aula renown 25000` lalu `/kultivasi debug aula level` → tetap Tingkat 2.
   10 rak buku + 1 podium dalam 16 blok → setelah ≤ 30 detik (atau buka gulungan, tunggu ±4 detik) Tingkat 4, centang di
   daftar Bangunan, pilar naik tingkat + chat. Formasi Pelindung aktifmu dengan Batu Magnet ≤ 24 blok → 6; 4 blok
   sasaran → 8; 3 Api Unggun Jiwa menyala di atas Obsidian Menangis dalam 32 blok → 9 + *Sekte di Bawah Langit*. Saat
   Tingkat 5: siaran server "Sekte … telah kembali!" + *Sekte Telah Kembali*.
8. Batas murid ikut tingkat (`5 + tingkat/2`, maks 9); tanpa aula tetap 5.
9. Ketenaran bos: murid memberi ≥ 20% kerusakan Elder Guardian/Warden → Catatan "+100 ketenaran" (sekali per jenis bos
   per hari). Terobosan murid → +10 × nomor ranah.
10. Tablet Catatan: maksimal 50 baris terbaru, cap "Hari N"/"Hari ini", bisa digulir.
11. (LAN, butuh pemain kedua) Teman satu `/sekte`: bisa membuka gulungan aulamu untuk melihat saja dan hanya menyumbang
    di Kantor Misi (tercatat di Catatan).

## B. Formasi Agung Sekte
12. Formasi Pelindung dengan Batu Magnet ≤ 24 blok dari prasasti → tablet Aula "Formasi Agung: 1 lapis, radius 18+".
13. Murid Fondasi+ dengan perintah Tinggal/Meditasi ≤ 3 blok dari Batu Magnet → chat Penjaga Formasi, "Dijaga: <nama>",
    daya kecubung tidak berkurang.
14. Tambah Tetua di wilayah → lapis 2 (monster Kelambatan II, warna biru). Lapis 3/4 → Kelambatan V saat melewati tepi,
    2 kerusakan/detik, garis emas-merah di sekitar monster hanya terlihat olehmu (bukan Glowing). Tidak ada blok berubah.
15. Kursor di baris Formasi Agung → Sumber (bab 153; *Renegade Immortal* bab 216).

## C. Hari Penerimaan Murid
16. Berdiri dekat prasasti: `/kultivasi debug aula calon datang 3` → 3 calon keluar dari kabut 8–14 blok, lonceng,
    judul "Hari Penerimaan Murid", notifikasi. Tablet Calon: kartu dengan wajah, umur, bar Tekad, akar/warna "???".
    Di atas kepala: "Calon · ???". Calon tidak bisa dilukai dan tidak menyerang.
17. Jalur alami: `/time add 24000` sampai Hari Penerimaan (setiap 3 hari sejak aula berdiri) saat fajar, kamu ≤ 128
    blok. Hari yang terlewat tidak menumpuk.
18. Ujian Masuk: talisman **Uji** atau klik kanan calon sambil memegang Serpihan Batu Kecubung → 1 serpihan hilang,
    3 detik tiga tingkat (hijau/abu/emas; yang gagal runtuh berasap), lalu pilar Duan 1–9 dalam warna jiwa dengan
    dentang per segmen. Chat + kartu mengungkap akar, warna, Duan, bakat, fisik, istimewa, dan segel vonis.
19. Terima calon **Lulus** → Murid Luar (cincin giok, lonceng, calon membungkuk kepadamu, *Hari Penerimaan Murid*).
    Vonis **Tercatat** → Murid Tercatat (+ Hati Gigih bila akar Sampah/Rendah) + *Tekad Mengalahkan Bakat*.
    **Ditolak** tanpa kas → pesan butuh 100 PK; `/kultivasi debug aula pk 200` lalu terima → kas −100.
    Batas penuh → pesan penuh. Calon belum diuji boleh diterima "buta".
20. *Undang pergi* (klik dua kali) → calon membungkuk lalu hilang. Malam hari calon duduk terkantuk; fajar berikutnya
    semua membungkuk dan pergi.
21. Koin *Hanya Monster* lalu `aula calon datang` → semua calon (setelah diuji) berakar ≥ Tinggi atau berfisik langka;
    jumlahnya berkurang satu.
22. Gerbang ketenaran (aula baru, < 300): calon yang diturunkan menampilkan "Calon berbakat menghindari sekte yang belum
    dikenal." + Sumber *Return of the Mount Hua Sect*.
23. Token: `/give @s kultivator:immortal_ascension_token`, klik kanan prasasti aktifmu → calon teruji datang (akar ≥
    Menengah, Tekad ≥ 60), *Apa Pun Akarnya*. Terima → Murid Luar + 1 Pil Pembangunan Fondasi. Prasasti orang lain atau
    yang diam → pesan salah prasasti.
24. Loot: peti perpustakaan benteng baru beberapa kali → token (6%) dan Lembar Kitab yang Hilang (25%). Token tidak bisa
    di-craft.
25. Calon Istimewa: `/kultivasi debug murid istimewa beggar|regressor|blocked_dantian` pada murid (lihat H).

## D. Jenjang Sekte
26. Murid fase 5 lama otomatis mendapat jenjang (Luar / Dalam / Inti sesuai ranah) dengan 0 PK.
27. Tablet Jenjang: pagoda enam lantai dengan segel murid; klik segel → bilah bambu berisi syarat dan tombol. Tooltip
    *Naikkan* berakhir dengan Sumber tujuh karya.
28. Tercatat → Luar: `/kultivasi uji murid <nama> ranah qi_refining`, PK murid lewat *Anugerah Ketua +50* (butuh kas:
    `aula pk 1000`) atau `/kultivasi debug aula pkmurid 100`. Baris Uji Ulang setengah siklus harus tercentang (tunggu,
    atau `/kultivasi debug aula jenjang 1`). *Naikkan (30 PK)* → kilau jubah, lonceng, chat sekte, *Naik Pagoda*.
29. Luar → Dalam: murid ≤ 6 blok dari prasasti, *Naikkan* memulai Latihan Khusus (lihat G); lulus → naik otomatis, PK
    dibayar saat lulus.
30. *Turunkan* (dua klik) → turun gratis, paling rendah Tercatat.
31. Murid Tercatat diberi Tinggal/Bantu → ditolak dengan alasan; Ikut/Meditasi boleh. Mengajar teknik di atas slot
    jenjang → ditolak.
32. Tetua: `ranah nascent_soul` + PK → Tetua (1 kursi; 2 di Tingkat 5). Murid lain yang bermeditasi ≤ 16 blok → XP
    ×1,15; sekali sehari Tetua mengajarkan teknik yang ia kuasai (chat + sinar).
33. Tetua Agung: Jiwa Baru Lahir Menengah + kekuatan tertinggi → *Yang Terkuat Duduk Tertinggi*.
34. Pelindung Sekte: `ranah dao_union` → *Angkat Pelindung* → murid beralih ke Jaga Aula, Regenerasi II di wilayah;
    murid non-Pelindung diberi Jaga Aula → ditolak.
35. Murid Inti butuh gelar tiga besar turnamen, Nomor Satu, Juara Peringkat, atau lantai 10 Alam Hampa.

## E. Poin Kontribusi, Paviliun Yue Lu, Seni yang Hilang
36. Tablet Perbendaharaan, kiri: kartu tumpukan (zamrud, berlian, pil …) dengan nilai PK; klik → tumpukan diambil,
    chat penilaian tetua, kas naik, bar harian maks 300.
37. Murid membunuh monster → PK murid naik (maks 120/hari).
38. Kanan: beli Pil Penyembuh (40 × faktor harga), *Undangan Khusus* (calon baru sekarang, sekali sehari), *Ceramah
    Kitab* dan *Manual Tubuh Abadi* (pilih murid dengan talisman "Untuk:"), *Gulungan Teknik* (pilih dengan "Gulungan:")
    → Buku Pesona yang dipetakan. Barang terkunci menampilkan "Tk N"; *Utusan Siap Lagi* tanpa utusan yang beristirahat
    ditolak tanpa biaya.
39. `/give @s kultivator:lost_scripture_page[kultivator:scripture_page={set:1,piece:1}]` sampai piece 5. Tanpa Paviliun
    Kitab tombol *Pulihkan* mati; dengan Paviliun → lembar hilang, +500 ketenaran (Catatan), glif + kelopak plum,
    *Dari Lembar yang Tercecer*, baris jadi "Dipulihkan".
40. Seni aktif untuk murid Dalam+: Pedang Bunga Plum (badai kelopak tiap pukulan ke-4; Mekar Penuh ke-24 untuk Inti),
    Kabut Ungu (qi pulih ×1,3, qi maksimal +20% di tab Status, kabut saat meditasi), Langit Utara (+20% malam/salju).

## F. Tugas, Lumbung, Misi
41. Tablet Tugas: pilih murid, *Pikul Air* → murid berjalan ke pos 5 blok dari prasasti dan diam, tanpa blok berubah,
    pose memikul ember. `/tick sprint 6000` lalu buka tablet → percikan air dan chat hasil (XP; PK tiap 50 menit).
    *Kebun* dan *Dapur* mengisi lumbung (buni sinar, wortel emas, Hidangan Roh). Klik slot lumbung → item masuk tas.
42. Peternakan: 4 ayam dalam 16 blok + tugas *Ayam* → `/tick sprint 12000` → Bulu Ekor Roh di lumbung. Kurang dari 4
    ayam → tidak ada bulu.
43. Hidangan Roh: makan sendiri (+8 XP, +10 qi); klik kanan murid → chat XP ×1,2 selama 2 menit, hidangan −1.
44. `/give @s kultivator:foundation_herb 7` → *Murnikan Herba* → 2 Pil Pembangunan Fondasi, 1 herba tersisa.
45. Misi: tablet Misi bagian Papan → misi tingkat rendah + 1–3 murid Luar+ → *Berangkat* → awan + pilar, daftar murid
    dan tablet Aula menandai "pergi", tetap dihitung batas murid. `/kultivasi debug event misi selesai` → pulang di
    samping prasasti dengan chat hasil (berhasil: PK, bar, rampasan di lumbung; gagal: HP 30% + Penyimpangan Qi).
    Murid tidak pernah mati. *Melangkah Keluar Gerbang*.
46. Tim hanya Murid Luar ditolak untuk tingkat ≥ 3 ("need_rank_tier"). Alam Rahasia butuh pemimpin Inti di Jiwa Baru
    Lahir+. *Panggil Pulang* di bagian Tim → langsung pulang tanpa hasil.
47. Utusan: murid Luar di Pemurnian Qi Menengah+ → *Kirim Utusan* → `event misi selesai` → 1–3 calon baru di tablet
    Calon. Tombol terkunci 5 hari; *Percepat* memotong 300 PK kas.
48. Tanah Terlarang: `/kultivasi debug event terlarang buka` → judul "Segel angin melemah!" + angin merah. Murid Fondasi+
    ditolak ("Pembatasan kedua…"). Kirim 1–3 murid Pemurnian Qi → `event misi selesai` → laporan di chat dan bagian
    Terlarang; yang selamat membawa 2–5 Herba Fondasi. Dengan `/kultivasi hardcore on`: murid gagal kembali sebagai Papan
    Jiwa di lumbung.
49. Kunpeng: `/give @s kultivator:kunpeng_bone_fragment[kultivator:kunpeng_piece=1]` untuk bagian 1–6 → bagian Bahtera →
    pilih murid Inti → *Satukan* → keenam bagian hilang, murid mempelajari Sayap Kunpeng.

## G. Acara sekte
50. Turnamen: ≥ 3 murid Luar+ di wilayah, kamu ≤ 32 blok → *Mulai Sekarang* di tablet Arena (atau `/kultivasi debug
    event turnamen mulai`). Cincin partikel emas, dua boss bar, penonton duduk, Murid Tepi Utara mengisi slot dan
    menembakkan garis qi/ledakan hanya ke lawannya. Laga berakhir di 10% nyawa atau 60 detik; 3 detik di luar cincin =
    kalah. Juara: chat, PK, nama emas 7 hari, *Nomor Satu Daftar Kuat*. Daftar Kuat di bilah bambu.
51. `/kultivasi debug event turnamen elit` → 3 jagoan saingan; sesudahnya bahtera hantu di atas aula. Bagian Bahtera:
    tiga besar *Naiki Bahtera* → `event misi selesai` → kitab, bagian Kunpeng, 60% bar, *Penumpang Bahtera Purba*.
52. Perang Seratus Dinasti: `/kultivasi debug aula calon perang` lalu *Mulai Perang* (tablet Calon atau bagian Perang)
    atau `/kultivasi debug event perang mulai` → bagan 4 + 2 + 1, laga 30 detik. Juara berlabel "Juara", diterima gratis
    sebagai Murid Luar; *Juara Seratus Dinasti*. Jalur alami: Tingkat 3+, setiap Hari Penerimaan ke-4 → chat merah
    "Hari ini Perang Seratus Dinasti" dan 8 pemuda perang, bukan calon biasa.
53. Alam Dewa Hampa: murid Dalam+ → *Masuk Alam Hampa* (atau `/kultivasi debug event hampa 9` dekat murid) → murid
    duduk tembus pandang di prasasti, portal ungu, bayangan merah. Sampai 5 lantai, jeda 3 detik. Lantai 10 →
    *Tangga Batu*. `event hampa 50` → siaran + gelar Batas Suci + *Jenius Batas Suci*. Kalah → Luka Jiwa 5 menit, murid
    hidup. Masuk kedua kali di hari yang sama ditolak.
54. Latihan Khusus (dari D-29): gelombang Pengawal Dao di detik 0, 20, 40; chat baris Sumber abu-abu setelah pesan
    mulai. Jangan pukul → lulus setelah 60 detik + pilar emas. Pukul satu monster → "Guru membantu", batal. Nyawa murid
    hampir < 25% → gagal, murid tidak mati. Ulang setelah 1 hari.
55. Menara Langit Membara: beberapa Api Unggun Jiwa menyala di atas Obsidian Menangis (ketinggian berbeda) ≤ 32 blok →
    bagian Menara menampilkan jumlah ruang (tertinggi = lantai 1). *Berlatih* → murid berjalan ke ruang, meditasi dengan
    butir api jiwa, PK turun n per menit (gratis bila hari % 7 = 3). Padamkan api unggun → latihan berhenti + chat.

## H. Alam jiwa, bakat, fisik, calon istimewa
56. Tab Bakat dan Pusaka muncul di gulungan murid; setiap baris punya tooltip dengan "Sumber: …" abu-abu miring.
57. Warna jiwa: `/kultivasi debug murid warna 0` (Merah), lalu `uji murid <nama> ranah golden_core 4` → "Tertahan di
    Puncak Inti Emas", terobosan ditolak. `warna 6` → nama nila, faktor ×1,70.
58. `murid fisik golden_body` → chat fisik langka, partikel, *Terlahir Berbeda*; zombie memukul → meter Darah Emas naik.
59. `murid fisik taixu` + `murid siap` (Jiwa Baru Lahir) + 3 zombie → Wujud Naga ungu, semburan api ke zombie; tidak ada
    blok terbakar.
60. `murid fisik poison` + Transformasi Dewa + `murid siap` → Kabut Racun Tujuh Warna hanya pada monster; Pil
    Penyembuh → "menenangkan racun".
61. `murid fisik heretic` di Pemurnian Qi → "Tak ada yang istimewa di nadinya"; naikkan ke Inti Emas → pengungkapan,
    Nila, gerbang 1, *Nadi Dewa Sesat*. Tombol *Ledakan Gerbang* → 20 detik burst lalu lemah.
62. `murid fisik supreme_bone` (Fondasi+) → teknik bawaan unsur akarnya dalam pertarungan. `double_pupils` → kadang
    menghindar; tiap 10 detik tanda mata di atas monster, hanya terlihat olehmu.
63. `murid istimewa beggar` + `ranah qi_refining 4` → terobosan Fondasi berhasil → "tak pernah benar-benar pengemis",
    akar Surgawi, Nila, *Pengemis di Gerbang*. `istimewa regressor` → ranah lalu "???" sampai diuji.
    `istimewa blocked_dantian` → mengajar teknik non-Tubuh ditolak.
64. `murid bakat persevering|pill_gift|…` → ubin bakat di tab Bakat.

## I. Kekuatan murid
65. Jurus Pamungkas: murid Jiwa Baru Lahir ber-Dao Pedang (pilih di tab Pil) + `murid siap` + 3 zombie → kubah pedang
    otomatis, *Jurus Pamungkas*, pesan action bar. Tombol *Jurus Pamungkas* di tab Bakat; berdiri ≤ 4 blok dari target →
    "terlalu dekat dengan target".
66. Teratai Api + Fusi: `/kultivasi debug set dao fire` untukmu, murid Dao Api Jiwa Baru Lahir, `murid api green_lotus`
    dan `murid api bone_chilling`. Perintahkan Jurus Pamungkas, dalam 4 detik tekan *Fusi* (≤ 6 blok) → qimu −30%,
    ledakan hanya pada monster, *Api Guru dan Murid*.
67. `/give @s kultivator:heavenly_flame_seed` → klik kanan murid berakar Api atau Jiwa Baru Lahir+ → ritual 20 detik;
    jongkok diam di dekatnya (Hu Fa) → sukses, bola api di tab Pusaka, kebal api/lava, *Pelahap Api*. Tanpa Hu Fa →
    kadang gagal, benih kembali.
68. Pusaka Natal: dirikan aula, berdiri di wilayah, murid Fondasi → tab Pusaka → *Pedang* (kreatif gratis) → *Tempa
    Lipatan* berulang (gagal dari lipatan 3 kadang mematahkan satu). `murid lipat 9` → pedang emas di punggung;
    `murid lipat 14` → pusaka mengitari murid, *Payung Abadi* bila Payung.
69. Kitab Abadi Tak Mati: `murid tempa 2500` → pesan Tulang Abadi, zirah naik, kilau emas. `murid tempa 10500` → Darah
    100%, *Darah Abadi*. Murid dibunuh zombie kuat → sekali sehari bertahan di 1 nyawa tanpa Papan Jiwa.
70. Pelarian Jiwa: murid Jiwa Baru Lahir+ mati saat aula aktif (bukan `/kill`) → cahaya terbang ke prasasti, chat +
    baris Sumber, tanpa papan, tercatat pergi. `/tick sprint 12000` → muncul di prasasti dengan nyawa penuh.
71. Sayap Kunpeng: `murid kunpeng` → tombol di tab Pusaka saat ada monster → melesat 12 blok, sabit angin, tidak ada blok
    tersentuh.
72. Formasi Tujuh Peri: 7 murid Inti bertarung dalam 16 blok → baris di tab Bakat, +10% kerusakan.

## J. Ikatan, Segel Jiwa, Hu Fa, Fondasi Dao
73. Tab Jalan muncul dengan bagian Bintang, Ikatan, Pewaris, Pemusnah (pemilih "‹ bagian ›").
74. `murid ikatan 60` + `/give @s kultivator:soul_seal_jade` → klik kanan murid (juga sambil jongkok) → aksara segel 5
    detik, "Segel Jiwa terbentuk", *Segel Jiwa*. Jongkok dengan tangan kosong tetap membuka gulungan murid.
75. *Panggil* murid tersegel dari jauh (dimensi sama, jeda 60 detik). Nyawamu < 30% dekat monster → murid tersegel
    Kecepatan II + Kekuatan I dan beralih ke Bantu.
76. Teknik yang unsurnya ada di akar murid hanya memakan 10% qimu saat diajarkan.
77. Hu Fa: saat terobosan/bencana murid, bermeditasi ≤ 8 blok → "Hu Fa aktif". *Tahan Petir* / `/kultivasi murid tahan`
    → sambaran berikutnya mengenaimu (nyawa tidak pernah < 1), busur kuning, maks 3 kali, 15% qi tiap kali.
78. Fondasi Dao: `/kultivasi debug murid set realm qi_refining 4`, penuhi bar, terobosan Fondasi sambil bermeditasi di
    dekatnya → chat "membangun Fondasi …" + pilar partikel; gambar pilar di bagian Ikatan.

## K. Bintang Abadi
79. `/kultivasi uji murid semua bintang 31` → chat emas "31 Bintang Abadi Planet ★31"; bagian Bintang: judul, bar Qi
    Bintang, 3 berlian bencana terisi, bonus, Klon Ilahi 1. Titik bintang berputar di dada; Kualitas Efek Minimal →
    1 cincin. Nama di atas kepala menampilkan "Planet ★31".
80. `bintang 0`, lalu `/kultivasi debug murid xp 600000` → bintang memadat sendiri (Otomatis), kilau perak + denting,
    *Bintang Pertama Terpadatkan*. Matikan Otomatis → *Padatkan Bintang*.
81. `bintang 5` + `murid xp 2000000` → "bencana 1 menghadang bintang ke-6". *Mulai Bencana 1* → pertanda 6 detik, 7
    sambaran partikel (tidak ada petir sungguhan, cuaca tetap, blok tidak terbakar), Pengawal Dao tiap 3 sambaran.
    Hu Fa → lewat, bintang ke-6, *Bencana Terlewati*. Uji gagal: murid menjauh > 6 blok setelah sambaran pertama → Luka
    Bencana, separuh harga kembali.
82. `/kultivasi debug murid bencana mulai`; `murid bintang set 63` lalu `set 70` → setelah ketujuh bencana: aura
    putih-emas, Penginjak Langit, 7 Lampu Jiwa; *Padamkan Lampu* memulai bencana k = 8.
83. Klon Ilahi (★28+) muncul melawan bos ≤ 24 blok atau saat murid < 50% nyawa; tembus pandang, hilang setelah 60 detik.
84. Qi maksimal murid naik dengan bintang dan Segel Jiwa (lihat angka qi di tab Status).

## L. Pewaris, Cincin Jiwa Guru, Melampaui Guru
85. Pewaris: jenjang Inti, Jiwa Baru Lahir, `murid ikatan 90`, tersegel → *Angkat Pewaris*, meditasi ≤ 4 blok 60 detik →
    cahaya giok antar dantian, "pewarismu, generasi ke-1", tekniknya diwariskan, *Pewaris Ditetapkan*. Tooltip tombol
    berakhir dengan Sumber.
86. Pemilih ‹ › → *Tandai Segel Generasi*; `uji murid <nama> bintang 9` → *Ciptakan Segel (1)*.
87. Papan Jiwa pewaris (bila gugur) bertooltip "Pewaris · Generasi ke-N".
88. `/give @s kultivator:master_soul_ring` → klik kanan pewaris → diterima (murid lain menolak). `/tick sprint 3d` →
    "Cincin … terbangun", *Guru di Dalam Cincin*. *Jiwa Guru Turun* → qimu −40%, aura warna ranahmu 20 detik, Telapak
    Buddha pada musuh murid; selama itu kamu tidak bisa merapal teknik.
89. `uji murid <nama> bintang 80` → ±5 detik: pilar emas, siaran, *Murid Melampaui Guru*; *Tetua Kehormatan* bisa
    dinyalakan. Nyawamu < 25% dalam 48 blok → murid teleport ke sisimu, Penyerapan + Ketahanan II, monster ≤ 12 blok
    mengincar murid. *Tanding dengan Guru* → duel 120 detik (keduanya ≥ 80% nyawa).

## M. Jurus Pemusnah (aman blok)
90. `uji murid <nama> bintang 33`, Dao murid dipilih → bagian Pemusnah: pilih jurus → *Jurus Pemusnah* dekat zombie →
    partikel besar, kerusakan hanya pada monster, TIDAK ADA blok berubah, sapi/penduduk/pemain tidak terluka, jeda 10
    menit. Dekat Wither/Warden/Naga ≤ 32 blok → dilepaskan otomatis, tetap aman blok.

## N. MODE KEHANCURAN — hanya di dunia uji, buat cadangan dulu
91. Bawaan (mati): bagian Pemusnah menulis "Mode Kehancuran mati di server ini"; tablet Segel Merah tidak muncul;
    `/kultivasi kehancuran sumpah` ditolak; `/kultivasi kehancuran status` menunjukkan kunci 1 tertutup.
92. `/kultivasi kehancuran izin on` (operator) → `config/kultivator.json` berisi `"modeKehancuran": true`. Tablet Segel
    Merah → *Sumpah Kehancuran* → layar merah darah berdetak; tanpa centang segel tidak bisa ditahan; centang + tahan 5
    detik → sumpah, *Sumpah Kehancuran*. Esc membatalkan.
93. Bagian Pemusnah → *Lepaskan Segel* → layar merah, tahan 3 detik → murid berpercik merah, nama bertanda
    **[Segel Merah]**.
94. Pergi ≥ 70 blok dari aula dan jauh dari desa → *Jurus Pemusnah (Hancurkan)* → jongkok sambil melihat blok ≥ 30
    blok darimu dalam 10 detik → garis pratinjau merah 15 detik + chat **[Ya, hancurkan]** / **[Batal]**. Ya → peringatan
    ke seluruh dimensi, langit merah + hitung mundur 30 detik, **[Batalkan penghancuran]** berfungsi. Di nol: blok hilang
    bertahap (±48 per tick, tanpa api/ledakan/item); peti, tungku, kasur, air, obsidian tetap utuh; log server
    `[Kultivator] Kehancuran: ...`, catatan di Segel Merah dan Catatan, *Pemecah Dunia*.
95. Zona terlindung: bidik ≤ 24 blok dari dirimu atau dekat penduduk desa / formasi / prasasti → "Ditolak: bentuknya
    menyentuh …". Saat jurus berjalan, masuk ke area → posisi ≤ 24 blok darimu dilewati.
96. Pulihkan: dalam 10 menit `/kultivasi kehancuran pulihkan` (atau tombol di Segel Merah) → blok kembali dengan kilau
    biru. Setelah server dimulai ulang tidak ada yang bisa dipulihkan.
97. Menutup kunci mana pun (*Tutup Segel Merah*, *Cabut Sumpah*, `/kultivasi kehancuran izin off`) → jurus yang berjalan
    berhenti saat itu juga. Jurus otomatis melawan bos tetap aman blok walau ketiga kunci terbuka.

## O. Sumber (baris "Sumber" harus ada)
98. Uji calon: tooltip *Uji* berakhir "Sumber: A Record of a Mortal's Journey bab 1-5 … Battle Through the Heavens
    (Duan 1-9)". Kursor di segel vonis → arti vonis + Sumber; vonis Tolak juga Sumber Jalur Pelayan (ISSTH bab 19).
    *Terima* pada calon teruji → Sumber vonis.
99. Tablet Aula: daftar Bangunan dan baris Formasi Agung bersumber. Tablet Jenjang: tooltip *Naikkan* + Sumber
    Latihan Khusus untuk murid Luar. Tab Bakat/Pusaka/Jalan: setiap baris bertooltip Sumber. Tooltip item (token, lembar
    kitab, benih api, tulang Kunpeng, giok, cincin) dan deskripsi pencapaian tidak menampilkan kunci mentah.

## P. Bantuan, bahasa, kinerja, keamanan blok
100. Layar kultivasi tab Bantuan → topik Aula Sekte, Hari Penerimaan Murid, Jenjang Sekte, Poin Kontribusi, Tugas,
     Misi, Turnamen, Alam Dewa Hampa, Bakat, Pusaka, Bintang Abadi, Ikatan, Mode Kehancuran.
101. Ganti bahasa ke English dan Indonesia: tidak ada kunci mentah (`kultivator.hall...`, `kultivator.dpower...` dst.)
     di gulungan aula, tab murid, tooltip, chat, item, dan pencapaian; tanda % tampil benar.
102. `/kultivasi murid list` menulis murid yang pergi sebagai "alasan · sisa N hari"; `/kultivasi uji` menampilkan
     baris `bintang <n>`.
103. Kinerja: aula dengan 9 murid, tugas berjalan, dan turnamen di laptop → FPS tetap wajar dengan Kualitas Efek
     Normal; Minimal mengurangi partikel jelas. Tidak ada spam error di log.
104. Keamanan blok (Mode Kehancuran mati): selama semua poin di atas tidak ada blok, tanaman, atau api blok yang berubah,
     tidak ada petir sungguhan atau ledakan, cuaca dan waktu tidak berubah sendiri.

## Belum ada / berbeda dari rancangan
* **Belum ada:** Fase 6b *Pembangunan Sekte* (templat kompleks sekte yang membangun dirinya sendiri dari bahan yang
  disetor, DESIGN bagian 21). Aula Fase 6 memakai bangunan yang kamu susun sendiri dari blok vanilla.
* **Belum ada:** Bintang untuk pemain sendiri (Fase 7); pemicu Murid Melampaui Guru memakai langkah ranahmu.
* **Belum ada:** ide yang ditunda di DESIGN 20.6 (jiwa bela diri Soul Land, Fusi Roh Iblis, Alam Semesta Kecil, Empat
  Aula, serangan pembunuh, boneka penjaga, dan lainnya).
* **Berbeda:** Bahtera Purba hanya muncul setelah **Turnamen Elit**, bukan setelah setiap Kompetisi Daftar Kuat.
* **Berbeda:** gagal di Tanah Terlarang memotong 30% **isi bar saat itu**, bukan 30% ukuran bar.
* **Berbeda:** pemuda yang tersingkir di Perang Seratus Dinasti tetap menjadi calon biasa sampai fajar (bisa diterima
  seperti biasa).
* **Berbeda (tidak terlihat):** Petir Pengusir Kejahatan mengirim satu paket efek per sambaran dan rambatan (maks 12 per
  jurus, jeda 120 detik), bukan satu per jurus.
