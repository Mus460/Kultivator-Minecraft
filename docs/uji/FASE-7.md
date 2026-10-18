# Checklist uji Fase 7 — Dunia yang Hidup

Persiapan: dunia uji dengan cheat aktif, mode Bertahan kecuali disebut lain, kesulitan Normal. Aturan dan angka ada di
[PANDUAN 33–48](../PANDUAN.md#33-dunia-yang-hidup-fase-7), rancangannya di DESIGN bagian 19.

Perintah ditulis singkat: `uji ...` = `/kultivasi uji ...`, `debug ...` = `/kultivasi debug ...`. Ubah ranah dengan
`uji ranah <ranah> [tahap 1-4] [tingkat 1-9]` (tanpa tahap = Puncak; `uji ranah immortal_ascension 1 7` = Kenaikan Abadi
tingkat 7 tahap Awal). Barang Fase 7 cepat didapat dengan `uji kit` (kotak shulker "Fase 7").

**Selalu periksa:** tidak ada error di log, tidak ada kunci bahasa mentah (coba Bahasa Indonesia dan English), tidak ada
blok yang berubah kecuali blok yang kamu patahkan sendiri, hewan peliharaan, penduduk desa, dan pemain lain tidak pernah
terluka. Setiap barang, tingkat elit, afiks, jimat, herba, dan anugerah baru punya baris **Sumber** (poin T).

Poin bertanda **(belum ada)** adalah fitur yang ditunda ke Fase 7b; cukup pastikan tidak ada crash. Seluruh Fase 7
**belum pernah diuji di game** (hanya dikompilasi).

## A. Simpanan lama dan dasar
1. Muat simpanan dari Fase 6c: ranah, teknik, bintang murid, aula, dan Qi Tertimbun tetap sama. Tidak ada crash.
2. Buka gulungan (`'`): tujuh tablet (Status, Akar & Dao, Teknik, Pengaturan, Bantuan, Jalan, Alkimia) muat di 320×240
   (Pengaturan Video › Skala GUI sampai jendela kecil). `/kultivasi jalan` dan `/kultivasi resep` membuka tab yang benar.
3. Matikan `eliteMonsters`, `structureLoot`, `spiritStoneMining`, `herbHarvest`, `dailyTasks`, `guideRewards`,
   `tribulationTelegraph` di `config/kultivator.json` (restart atau `/reload` untuk peti) → fiturnya benar-benar hilang.
   Nyalakan lagi → kembali.
4. `uji kit` → kotak "Fase 7": 16 batu tiap tingkat, 4 herba tiap jenis umur 100, Buah Ginseng 1.200 tahun, satu inti tiap
   tingkat (unsur acak), 4 butir tiap pil baru Sempurna, 8 jimat kekuatan 5, 16 kertas jimat, Kompas Urat Roh.

## B. Binatang Iblis
5. `uji ranah golden_core`, lalu `uji elit 1`, `uji elit 2 api`, `uji elit 3 petir zombie` → muncul 4 blok di depan, aura
   warna unsur, makin besar per tingkat, tingkat 3 punya bilah bos "Raja Siluman · Petir"; chat menulis nyawa, afiks, dan
   baris Sumber.
6. Tatap elit dalam 24 blok → pelat di bawah tanda bidik ("Binatang Iblis · Api", jenis, afiks, bilah nyawa). Tahan
   jongkok → baris Sumber tingkat dan afiks.
7. Keseimbangan: di Inti Emas `uji elit 3 tanah zombie` (±164 nyawa) mati dalam ±7 pukulan jurus dasar; di
   `uji ranah true_immortal` (±304 nyawa) ±4 pukulan dasar atau 1–2 jurus Langit. Di Transformasi Dewa elit tidak pernah
   mati dalam satu pukulan biasa.
8. Skill bertanda: `uji elit 2 api` → setiap 8–10 detik lingkar di kakimu; keluar dalam 1,5 detik = tidak kena. Ulangi
   `uji elit 2 es` (Napas Es: Lambat IV + 3 kerusakan), `uji elit 2 tanah` (Hentakan Bumi melempar ke atas),
   `uji elit 2 angin` (Kedipan ke belakangmu, hanya di tempat aman), `uji elit 2 kayu` (Racun II dalam 5 blok).
9. Pasif tingkat 1 tiap unsur: api membakar, es/air memperlambat, kayu meracuni, logam melemahkan, tanah mendorong, petir
   kadang menyambar (partikel), angin cepat, ruang kadang muncul di belakangmu.
10. Afiks: `uji elit 2 air zombie perisai_unsur`, pilih Dao Api → `uji unsur` sambil menatapnya, jurus Api ±×0,65; Dao Tubuh
    (Tanah mengalahkan Air) ×1,0. `uji elit 2 logam zombie penyerap_qi` → qi berkurang sampai 8 tiap pukulan.
    `uji elit 2 logam zombie pulih_cepat` → pulih 1 nyawa per detik.
11. Hadiah: bunuh elit tingkat 1, 2, 3 → inti (tingkat dan unsur elit) selalu jatuh di tempat elit; tingkat 3 juga 1–3 batu
    rendah; tingkat 2+ kadang herba. Baris "+N Kultivasi", pemahaman Dao (paling banyak +3 per hari game). Pencapaian
    *Pemburu Siluman*, *Penakluk Raja Siluman* (tingkat 3), *Pemburu Binatang Iblis* (50 elit). Raja Siluman yang tumbang
    diumumkan di chat.
12. Murid membunuh elit → inti tetap jatuh di tempat elit, murid mendapat XP tambahan, hitungan elitmu bertambah
    (`debug elit info` sebelum membunuh). Binatang roh yang membunuh → hadiah penuh untukmu.
13. Pengerasan: `uji elit 1 logam skeleton` memegang pedang besi, tidak memanah; `uji elit 1 tanah zombie` di depan pintu
    kayu tidak mendobrak; barang yang dilempar tidak dipungut. Menjauh lebih dari 128 blok → elit hilang (despawn).
14. Konversi: dorong zombi elit ke air sampai menjadi drowned → bukan elit lagi (tanpa aura dan pelat).
15. Kemunculan alami: `uji ranah dao_union`, malam di padang terbuka → sesekali zombi/tengkorak alami menjadi elit (±4% per
    kemunculan, ×2 bulan purnama), paling banyak 3 dalam 64 blok, tidak ada yang lain dalam 24 blok. Di Manusia Fana
    (`uji ranah mortal`) tidak pernah. Spawner dungeon tidak pernah memunculkan elit; di sel sekte tidak ada elit.
16. `eliteMonsters` dimatikan saat elit ada → skill berhenti, bilah bos Raja hilang, kematiannya seperti monster biasa (tanpa
    inti).
17. Pelindung Dao: `uji ranah golden_core 4` lalu `uji tribulasi nascent_soul` → satu monster tiap gelombang adalah elit
    tingkat 1, memberi setengah XP dan menjatuhkan intinya, lalu lenyap saat tribulasi selesai. `uji tribulasi true_immortal`
    → elit tingkat 3.
18. `uji elit hapus` menghapus semua elit dalam 32 blok.

## C. Batu Roh
19. `uji ranah qi_refining`, Bertahan, tambang ±20 bijih berlian (deepslate) dengan beliung besi → kadang 1–2 Batu Roh Rendah
    melompat keluar dengan kilau hijau dan baris di bilah aksi. Dengan Sentuhan Sutra, di Kreatif, atau di Manusia Fana →
    tidak pernah.
20. Resep 9 ↔ 1: `uji batu rendah 18` lalu rakit 9 → 1 Menengah dan 1 → 9; ulangi Menengah ↔ Tinggi dan Tinggi ↔ Agung.
21. Qi seketika: kosongkan qi, pegang Batu Roh Menengah di tangan kanan, jongkok + pakai di udara → qi +200, jeda 1 detik.
    Agung → qi penuh dan pemulihan ×3 selama 60 detik. Saat meditasi → ditolak dengan pesan. Batu di tangan kiri + tangan
    kanan kosong → tidak terpakai.
22. Bahan bakar: batu rendah di tangan kiri saat meditasi → satu habis tiap 30 detik, chat menulis kultivasi ×1,5, HUD
    menunjukkan ikon batu dan waktu, kuras urat separuh. Bilah penuh di leher botol → hasil masuk Qi Tertimbun.
23. Di leher botol dengan batu **Tinggi atau Agung** di tangan kiri saat meditasi → batu tidak dibakar, satu baris chat
    menjelaskan bahwa batu disimpan untuk terobosan.
24. Formasi pelindung: Batu Magnet + 4 Obsidian Menangis (jarak 4), jongkok + pakai Batu Roh Tinggi pada Batu Magnet → daya
    +24 jam (`/kultivasi formation list`); Agung → 48 jam, batu berikutnya ditolak "sudah menyimpan". Kecubung tetap berhenti
    di 2 jam dan tidak terpakai bila daya sudah lebih dari 2 jam.
25. Batu di Hambatan: `uji ranah golden_core 4`, `debug fill`, pegang 2 Batu Roh Agung di tangan kiri, terobos → "Hambatan
    menyerap 2× Batu Roh Agung: kualitas terobosan +0,16", batu habis, pencapaian *Batu di Hambatan*. Gagal atau batal → batu
    tetap. Batu Rendah/Menengah tidak dipakai.
26. Kantor Misi menerima batu (0,5 / 4,5 / 40 / 300 PK), inti (3 / 10 / 30 / 120) dan herba (1 + umur/100), tetap dibatasi
    300 PK per hari.
27. `/summon minecraft:wandering_trader` beberapa kali → sesekali tawaran 2 zamrud → 3 Batu Roh Rendah (4 kali pakai).

## D. Inti Binatang
28. `uji inti rendah api`, jongkok + pakai → XP (paling banyak 150), racun +6, pemahaman bila Dao-mu Api; akar dengan unsur
    Api → XP +50%. Saat Penyimpangan Qi → ditolak. Tooltip menulis XP, racun, Dao, katalis, dan baris Sumber.
29. Bangunkan serigala roh, `uji inti menengah petir`, klik kanan serigala → kultivasi binatang +225 (cocok ×1,5); klik lagi
    dalam 10 detik → "masih mencerna". Binatang milik pemain lain → ditolak. Serigala tidak duduk/berdiri karena klik inti.
30. Monster biasa di Pemurnian Qi+: ±1,5% menjatuhkan inti rendah berunsur monster itu; tidak pernah dari pelindung, boneka
    latihan, Iblis Hati, atau mob spawner.
31. Bunuh Warden pertama hari ini → Inti Raja. Bunuh Warden lagi di hari yang sama → tanpa Inti Raja kedua, XP hanya 25%.
32. Ekspedisi: Perburuan Binatang Iblis (tingkat 4) berhasil → 1 Inti Binatang Menengah di lumbung; Mencari Herba Roh → 1–2
    herba umur 200–600 (tingkat 4); Mengawal Karavan → 2–5 Batu Roh Rendah; Alam Rahasia → 1 Batu Roh Menengah. Tugas Kebun
    murid → kadang Rumput Roh umur 50–200.

## E. Tanaman Roh
33. `uji ranah qi_refining`, `uji herba tampak`, patahkan rumput → Rumput Roh dengan tooltip "Umur: N tahun" dan Sumber.
34. Wortel matang di hutan rimba → Ginseng Roh Sembilan Lekuk; bunga di bioma bersalju → Rumput Es Bersudut Delapan; kutil
    Nether matang → Bunga Api Ungu; bunga di malam hari (purnama ×3) → Rumput Roh Ilusi; bambu saat `/weather thunder` →
    Bambu Petir Emas; korus di End → Buah Ginseng; jamur di bawah y 0 → Jamur Inti Kuning.
35. Tempat yang sama di hari yang sama (tanam ulang / tepung tulang) → paling banyak satu herba per tempat. Indra Ilahi tidak
    lagi menandai tempat yang sudah memberi herba hari ini.
36. Batas harian: setelah 12 herba di hari game yang sama tidak ada herba lagi. Wilayah yang sering dipanen makin jarang
    memberi herba (kepadatan urat turun 0,02 per temuan).
37. `uji herba semua 1200` → semua nama emas "… Seribu Tahun", berkilau, pencapaian *Herba Seribu Tahun*. Temuan asli
    ≥ 1.000 tahun memunculkan satu baris di chat server (paling sering sekali per 10 menit).
38. Herba tidak bisa ditanam (klik kanan ke tanah tidak memasang apa pun).

## F. Urat Roh, Kultivasi Tertutup, Gua Kediaman
39. `debug vein grade` di beberapa wilayah → Agung / Kaya / biasa / Tipis dengan batas 200% / 140% / 100% / 60%.
    `uji urat cari` mencetak Urat Roh Agung dan Kaya terdekat dengan jarak.
40. Di wilayah Agung: `uji urat 200`, meditasi → bilah aksi "Kepadatan Qi 200% · Urat Roh Agung". Di wilayah biasa
    `uji urat 200` dibatasi 100%.
41. Rumus: `uji ranah deity_transformation`, meditasi 60 detik tanpa situs/formasi/batu di 100% → ±240 XP sebelum pengali
    akar/kualitas. `uji ranah true_immortal` → ±83 per detik (±5.000 per menit).
42. Kultivasi Tertutup: saat meditasi `uji tertutup 3` → HUD "Tertutup +15%". `uji tertutup 30m` → pesan peluang pencerahan
    ×1,5. Berhenti setelah ≥ 60 detik → ringkasan "Meditasi …: +… kultivasi · kepadatan …% → …%".
43. Formasi Agung / Bintang Surgawi aktif → kepadatan tidak turun di bawah 30% / 45%.
44. Gua Kediaman: Formasi Pengumpul Qi Kecil di dalam formasi pelindung aktif, duduk di kursinya, meditasi,
    `/kultivasi gua tetapkan` → berhasil, pencapaian *Gua Kediaman*. Di luar formasi pelindung → ditolak dengan alasan.
    `/kultivasi gua lihat` menulis "terlindungi". Berdiri ≤ 10 detik lalu duduk lagi → tumpukan Tertutup tetap. Formasi
    pelindung kehabisan daya → "Gua tidak terlindungi", bonus berhenti. `uji gua` menetapkan tanpa cek formasi.
45. Koin **Gua Kediaman** di tab Status bekerja sama (klik dua kali dalam 3 detik = hapus).
46. Restart server sementara pemilik gua luring, lalu pemain lain menyalakan Mode Kehancuran di dekat gua → tidak ada kawah
    dalam 16 blok dari kursi.
47. Kompas Urat Roh: rakit (kompas + Serpihan Batu Kecubung + batangan emas + Buni Sinar), pakai → qi −5, jarum menunjuk
    wilayah Agung (atau Kaya) terdekat, chat menulis tingkat dan jarak, tooltip "Menunjuk" dan "Jarak: … blok". Pakai lagi
    segera → jeda 60 detik.
48. Murid yang bermeditasi dekat guru tetap mendapat bagian XP meditasi dengan rumus baru.

## G. Jimat
49. Rakit Kertas Jimat Kosong (3 kertas + Rumput Roh + pewarna merah → 3). Di Pemurnian Qi+, kertas di tangan kanan, batangan
    tembaga di tangan kiri, jongkok + pakai → 2 detik, qi −20, Jimat Petir berkekuatan ordinal ranah (Inti Emas = 3); gagal
    → abu, kertas dan katalis hilang. Di Manusia Fana → ditolak.
50. `uji inti tinggi petir` sebagai katalis → kekuatan +2 (paling tinggi 9). `uji jimat tingkat 10` → peluang 95% dan
    pencapaian *Ahli Jimat*. Jimat pertama → *Jimat Pertama*.
51. `uji jimat petir 5` pada zombi dalam 24 blok → 16,5 kerusakan dan rantai 8,25 ke satu monster lain dalam 5 blok. Tidak
    pernah mengenai hewan, penduduk, atau pemain; tidak ada blok berubah.
52. Awan Api membakar monster dalam kerucut 6 blok tanpa api di blok. Perisai: Absorption 60 detik. Pengikat: Lambat VII +
    Lemah 5,5 detik (bos ×0,25). Seribu Li: dua kali pakai, Kecepatan II 60 detik, juga untuk Manusia Fana. Kejernihan: tak
    terlihat 20 detik, monster pengejar kehilangan target, menyerang mengakhirinya, bos tidak terpengaruh.
53. Jimat Pulang: tahan 5 detik, api emas, teleport ke gua → titik bangkit → spawn dunia. Bergerak > 1 blok, terluka, atau
    melepas tombol → batal. Ditolak saat tribulasi, ujian Iblis Hati, ujian warisan, duel atau tanding, Latihan Khusus, laga
    turnamen, Alam Dewa Hampa, perintah Kehancuran yang dibidik atau dihitung mundur, pedang terbang, dan 10 detik setelah
    terluka. Jeda 10 menit (`uji jimat pulang` memberi jimat baru dan mereset jedanya). Tidak pernah berpindah dimensi.
54. Jimat Penangkal Petir: `uji tribulasi golden_core`, pakai di antara petir → chat "petir ke-N … setengah kekuatan", petir
    itu ×0,5. Penangkal ketiga dalam tribulasi yang sama → ditolak. Di luar tribulasi → ditolak.
55. Jimat Perisai saat tribulasimu sendiri → ditolak dengan pesan bahwa hanya Penangkal Petir yang membantu.

## H. Alkimia Lanjutan
56. Kuali di atas api unggun menyala: Rumput Roh Ilusi + Buni Sinar + Serpihan Batu Kecubung + 2 Batu Roh Menengah + Inti
    Binatang Raja + Rumput Roh 1.000 tahun (≤ 10 bahan). Klik kanan kosong → "→ Pil Pencerahan · katalis: peluang +38%,
    kualitas +0,5, paling rendah Sempurna". Racik → mutu Sempurna, lingkar emas, peluang tidak pernah > 95%.
57. Tumpahkan panci (padamkan api) → inti dan herba keluar dengan unsur/umurnya.
58. `uji pil dao_insight sempurna` tanpa Dao → ditolak, pil tetap; dengan Dao → pemahaman +4,8, berhenti di 60 dan setelah 5
    pil per ranah. `uji pil toxin_purging` → racun −35 × mutu, Racun/Layu/Lambat hilang. `uji pil enlightenment` → peluang
    pencerahan ×2 selama 10 menit.
59. Racik Pil Penempa Tulang (Blok Tulang + Batangan Besi + Wortel Emas + `uji inti menengah tanah`) → nyawa maksimum +1,
    tetap setelah mati dan masuk ulang, batas +10. Tanpa inti Tanah Menengah+ → tidak cocok.
60. Resep alternatif: Ginseng Roh Sembilan Lekuk + Wortel Emas + Irisan Semangka Emas → 3 Pil Penyembuh; Bunga Api Ungu +
    Batangan Emas + Apel Emas → 2 Pil Inti Emas; Bambu Petir Emas + Batangan Tembaga + Selaput Angan-Angan + Batangan Besi
    → 2 Pil Penangkal Petir; Buah Ginseng + Air Mata Ghast + Serpihan Gaung + Buah Korus → 3 Pil Pemelihara Jiwa.
61. Beri salah satu pil baru ke murid → "… masih terlalu berat untuk dicerna …; pil tetap disimpan", pil tidak hilang. Efek
    pil baru untuk murid **(belum ada)**.
62. Kitab Alkimia (`/kultivasi resep` atau tab Alkimia): kepala menulis tingkat, XP, peluang api unggun / api unggun jiwa
    (dan Dao Api); kartu per resep dengan ikon hasil dan bahan, garis merah untuk bahan kurang, ikon inti untuk Penempa
    Tulang, centang dan "Bisa diracik 2×"; gulir dengan roda mouse; muat di 320×240.
63. Arahkan kursor ke Buni Sinar → "[Shift] Bahan alkimia"; tahan Shift → daftar pil.

## I. Tribulasi hidup dan Hu Fa
64. `uji ranah dao_union`, `uji tribulasi deity_transformation` → 1,5 detik sebelum tiap petir cincin di kaki. Melangkah 2
    blok keluar → bilah aksi menulis 40%, nyawa berkurang lebih sedikit, tidak pernah nol. Tidak pernah mati.
    `uji tribulasi golden_core cincin` memaksa cincin; `uji tribulasi golden_core biasa` tanpa cincin.
65. Tetap di setiap cincin → chat "Menantang Langit" dan pencapaian *Menantang Langit* (terobosan sungguhan +0,12). Biarkan
    2 petir menyerempet → chat menulis −0,04.
66. `uji tribulasi immortal_ascension 1` → petir ke-4, 8, 12 punya dua cincin berjarak 3 blok; berdiri di salah satunya tetap
    kena penuh. `uji tribulasi golden_core kembar` → setiap cincin kembar.
67. Lingkar tujuh warna di Kenaikan Abadi / Dewa Abadi **(belum ada)**.
68. `tribulationTelegraph` mati, restart, `uji tribulasi deity_transformation` → tidak ada cincin, semua petir penuh.
69. `uji kawal 2` lalu `uji tribulasi dao_union` → petir lebih lemah (×0,95 × 0,95), HUD menulis "Dikawal: 2", gelombang
    pelindung +2 monster.
70. LAN, dua pemain dalam satu sekte: A di Inti Emas Puncak dengan bilah penuh mulai terobosan sungguhan → B mendapat baris
    [Hu Fa] dengan arah, jarak, dan tombol [Kawal]. Klik (atau `/kultivasi kawal <A>` dari ≤ 64 blok) → keduanya mendapat
    pesan. Pemain dalam 24 blok saat petir pertama terdaftar otomatis. Benang qi emas tiap petir; B ber-Dao Petir → ×0,90.
    Berhasil → B mendapat 10% bilahnya dan pencapaian *Hu Fa Tribulasi*; pengawalan kedua dalam 20 menit tanpa hadiah. B
    menjauh > 40 blok selama 10 detik → peran hilang. B keluar dunia → peran hilang.
71. A keluar dunia di tengah tribulasi bercincin, lalu masuk lagi → tidak ada cincin tertinggal, tidak ada error.

## J. Tablet Jalan
72. Dunia baru: slip "Bab 1 · Manusia Fana" di tepi kanan tengah dengan nama tombol yang benar; tidak menutupi bilah bos,
    ikon efek, toast, atau panel pembangunan sekte. Setelah 20 detik mengecil jadi segel; tahan `'` → terbuka ±8 detik.
73. Uji akar di tab Status → lonceng kecubung, kilau emas, langkah berikutnya tampil.
74. `uji jalan bab 1` → chat bab selesai; tab Jalan › Ambil hadiah → 2 Pil Pengumpul Qi (Menengah); tekan lagi → "sudah
    diambil". Matikan `guideRewards` → tombol mati dan server menulis hadiah dimatikan.
75. Pemain lama: `uji ranah golden_core`, keluar-masuk → langkah yang pencapaiannya ada langsung tercentang, satu baris chat
    untuk semua bab.
76. `uji jalan bab 5`, pilih Dao, hadiah bab 4 → 1 Batu Roh Menengah + slip giok Kuning dari Dao itu (atau 2 batu Menengah
    bila semua sudah dikuasai); hadiah bab 5 → 2 Batu Roh Menengah + Jimat Perisai kekuatan 3. Setelah langkah ke-20 pelacak
    hilang dan pencapaian *Jalan Telah Terbuka*.
77. Langkah segel: buka Kitab Teknik (`-`) dan pasang 4 jurus ke Cincin Segel → dalam 5 detik langkahnya tercentang.
78. Koin "Sembunyikan panduan" (tab Jalan) atau "Panduan" (Pengaturan) → pelacak hilang / muncul.
79. Ujian Fondasi: `uji ranah foundation` → `uji jalan ujian` menulis tiga ujian menuju Inti Emas (20 bunuhan jurus, 20 menit
    di situs ×1,5+, injak Nether). Masuk Nether → chat "Ujian Fondasi selesai". `uji ranah golden_core` lalu
    `uji jalan ujian selesai` → pencapaian *Fondasi Kokoh*; Persiapan menulis mutu +0,26 dan faktor petir ×0,9.
80. Kewajiban Harian: `/kultivasi harian` → tiga kewajiban dan hari beruntun; tab Jalan › Harian dengan bilah kemajuan; tiga
    titik di samping nama ranah. `uji harian selesai` → +4% bilah per kewajiban, ketiganya +1 Batu Roh Rendah.
    `uji harian beruntun 4`, `uji harian ulang`, `uji harian selesai` → beruntun 5, pencapaian *Lima Hari Tekun*. Tidur
    melewati malam tidak memberi kewajiban baru sebelum 15 menit bermain. `dailyTasks` mati → dimatikan di chat, HUD, tab.
81. `uji jalan reset` → panduan, ujian, dan kewajiban mulai dari awal (penghitung seumur hidup tetap).

## K. Persiapan dan HUD tribulasi
82. `uji ranah qi_refining`, `debug fill` → tab Status › koin Persiapan: peluang dengan rincian dasar 75% + akar + pil + Qi
    Tertimbun + tempat; duduk di 4 Balok Batu Kecubung → bagian tempat naik.
83. `uji ranah deity_transformation`, `debug fill` (menuju Penyatuan Dao) → "Petir: 9 × 18% … (terakhir 36%) = 180%", perlu
    penyembuhan ±1 bilah darah, iblis hati (bila belum lulus), gelombang Pelindung Dao +1 Binatang Iblis, lingkar petir,
    daftar periksa (darah ≥ 90%, apel emas/Pil Penyembuh dihitung, penangkal, sekutu, racun, deviasi). 2 Batu Roh Agung di
    tangan kiri → "batu roh +0,16". Tooltip talisman Terobos merangkumnya.
84. `uji tribulasi deity_transformation cincin` → HUD tepi kiri tengah: pip "Petir 1/7", cincin waktu, "Berikutnya −x ❤"
    (kuning > separuh darah), peta 6 blok (kuning > 4,5, merah > 5,5, panah kembali), "Keluar dari lingkar!". Kurangi darah
    sampai petir berikutnya menggagalkan → merah berkedip, detak jantung, peringatan. Tidak bertabrakan dengan bilah bos
    Iblis Hati atau langit merah Kehancuran.

## L. Anugerah, Perkokoh Fondasi, Bintang
85. `uji ranah immortal_ascension 1 1` → dalam 1 detik judul "Urat Abadi terbuka" dan chat "… Napas Abadi — …" dengan baris
    Sumber abu-abu miring. Naikkan tingkat → anugerah berikutnya satu per satu. `uji anugerah` mendaftar yang aktif dan
    memutar ulang judul. Kenaikan Abadi tingkat 2+: `/attribute @s minecraft:step_height get` = 1,1.
86. Jiwa Tak Hancur: `uji ranah immortal_ascension 1 7`, biarkan monster memberi pukulan mematikan → bertahan 1 darah,
    Absorption + Ketahanan III, qi habis, pencapaian *Jiwa Tak Hancur*. Pukulan kedua di hari yang sama tetap mematikan;
    `uji jiwa` mereset. `/kill`, kehampaan, dan petir tribulasi tidak pernah dicegah. Totem di tangan terpakai lebih dulu.
87. Perkokoh Fondasi: `uji fondasi golden_core retak`, `debug set storedqi 10000`, meditasi di Formasi Pengumpul Qi Agung,
    `/kultivasi fondasi perkokoh` (atau talisman di tab Status) → ritual 30 detik dengan hitung mundur di talisman dan HUD,
    judul "Fondasi Diperkokoh", Retak → Biasa, Qi Tertimbun −9.225, pencapaian. Berhenti meditasi di tengah → batal tanpa
    biaya. Di formasi Kecil → ditolak.
88. Bintang: `uji ranah true_immortal`, `debug set storedqi 600000`, `/kultivasi bintang padatkan` → ★1, −500.000, nyawa
    maksimum +2, qi maksimum +1%. Talisman Terobos di tab Status berganti "Padatkan Bintang ★2"; Shift + klik = semua;
    bintang mengorbit dantian dan "★n" di HUD. `uji bintang 9` → Gerbang Dewa I (pesan dan pencapaian *Sembilan Bintang*);
    `uji bintang 18` → Gerbang Dewa II. `debug perks show` merangkum.
89. Isi ulang: bilah tidak penuh di leher botol dan Qi Tertimbun > 0, biarkan Penyimpangan Qi berakhir → "Qi Tertimbun
    mengalir kembali…", bilah bertambah sampai Qi Tertimbun/2.
90. Bencana bintang pemain **(belum ada)**. Tooltip Sumber anugerah di tab Status **(belum ada)**; sumbernya tampil di chat.
91. Kurva nyawa: Kenaikan Abadi tingkat 9 Puncak ±+63 dan Dewa Abadi ±+72–75 nyawa sebelum faktor kualitas.

## M. Lima Unsur
92. `uji ranah golden_core`, Dao Pedang, tatap laba-laba, `uji unsur` → ×1,30. Dao Api pada stray → ×1,00. Dao Petir pada
    drowned → ×1,30. Unsur jurus ada di akarmu → resonansi ×1,10.
93. Sparring, duel turnamen, dan tribulasi tidak terpengaruh unsur. Percikan warna unsur muncul pada pukulan ×1,3 (paling
    sering sekali per 0,5 detik per sasaran).
94. Kitab Teknik › detail jurus Dao Pedang → "Unsur Logam · unggul melawan monster Kayu"; jurus Ruang → "×1,05 melawan semua
    monster".

## N. Relik di peti dan Indra Ilahi
95. `uji relik desert_pyramid`, `igloo_chest`, `ancient_city`, `bastion_treasure`, `end_city_treasure` → isi peti vanila +
    barang Fase 7 masuk inventori bersama slip giok dan token Fase 6c; chat menulis peluang tiap kolam. `structureLoot` mati
    + `/reload` → "structureLoot dimatikan", kolam Fase 7 hilang.
96. Pelajari Indra Ilahi, rapal di padang rumput → tanaman terinfus hari ini ditandai hijau. Di dekat kuil gurun atau dungeon
    yang belum dibuka → peti ditandai emas dalam 24 blok; chat "Inderamu menyentuh harta: …". Blok tidak berubah.

## O. Keseimbangan
97. `uji kejenuhan 75` lalu bunuh zombi → XP ±×0,33 dan bilah aksi "Tubuhmu jenuh bertarung: kultivasi dari membunuh ×0,33"
    (paling sering sekali per menit). `debug kejenuhan show` → turun 1 per 5 detik; `debug kejenuhan reset` → 0.
98. Mob dari spawner dungeon → XP ×0,25, tanpa inti atau herba.
99. Dengan Dao terpilih, baca 5 buku pesona yang hanya memberi wawasan di hari game yang sama → buku ke-5 tetap ada dengan
    pesan "pikiranmu sudah penuh hari ini"; tooltip "Buku wawasan hari ini: 4/4". Buku yang mengajarkan jurus baru tetap bisa
    dibaca.
100. `debug toxicity set 95`, telan beberapa Pil Pengumpul Qi → kadang "Pil Membalik!" dengan asap kelabu dan Deviasi Qi
     ringan (tidak pernah mati). Racun 50 → khasiat 67%. Meditasi → racun turun 1 tiap 10 detik. HUD menampilkan racun mulai
     40.
101. Pil Dewa (`debug alchemy give immortal 1`) → XP tepat 1 bilah (Sempurna 1,6), tidak dipengaruhi akar atau racun.
102. Pil Pembersih Sumsum: akar Menengah di Pemurnian Qi → ditolak sebelum pil habis ("sebelum Fondasi"); akar Tinggi dengan
     pil Menengah → ditolak ("pil bermutu Tinggi ke atas"); akar Rendah → 8 detik lalu naik (85%) atau "Sumsum menolak!" dengan
     asap kelabu, akar tetap. Tooltip menulis peluang tiap langkah sesuai mutu pil.

## P. HUD dan kenyamanan
103. `uji hud` → 10 detik: pengukur pedang "≈42 dtk" turun ke kuning/merah dan "Turun!", "Pencerahan 0:07", 6 pip Langkah
     Awan, racun 55%, "Urat Roh Agung", "Tertutup +15%", ikon batu + waktu, "Gua", ★9, tiga titik harian, slip panduan, HUD
     tribulasi contoh (Dikawal: 2), "Bahaya: Binatang Iblis (Zombie) 7 blok".
104. Pedang terbang lama dengan qi sedikit → "≈N dtk" menghitung mundur, lonceng di 10/5/3 detik. Lompat di udara → pip awan
     berkurang (Pemurnian Qi 1, Dewa Abadi 6).
105. Pengaturan › "Pengingat cooldown" nyala, jurus dengan jeda ≥ 10 detik → saat selesai lonceng dan segel berkilau.
106. Peringatan bahaya: meditasi lalu `/summon zombie ~5 ~ ~` → bel, tepi layar merah ke arah zombi, "Bahaya: Zombie 5 blok",
     berulang tiap 5 detik dalam 6 blok. `uji elit 1 zombie` → "Bahaya: Binatang Iblis (Zombie) …". Serigala jinak sendiri dan
     Iblis Hati tidak memicunya. Matikan "Peringatan bahaya" → diam.
107. Cetak biru: Balok Emas, pegang Tongkat End, tatap dari ≤ 8 blok → kubus emas berdenyut di slot kosong, garis hijau di
     blok benar, silang merah di blok salah, label "Formasi Pengumpul Qi Kecil 0/4 · ×1,25 → … 1/9", bertahan 30 detik. Batu
     Magnet → pilar Obsidian Menangis "Formasi Pelindung n/4" dan saran Serpihan Batu Kecubung. Tidak ada blok berubah.
108. LAN: Pengaturan › "Efek pemain lain: Mati" → aura pemain lain hilang, auramu tetap; "Ringan" → jauh lebih tipis.
109. Tab Bantuan: 15 topik Fase 7, "Tablet Jalan" paling atas, masing-masing diakhiri baris Sumber.

## Q. Murid, sekte, dan Kehancuran
110. Kawah Mode Kehancuran di bijih atau tanaman tidak pernah memberi batu roh, herba, atau XP tambang.
111. Murid tidak pernah mengurangi petir tribulasimu, tetapi pelindung bisa memilih mereka sebagai sasaran.
112. Bintang pemain menaikkan ukuran kawah (K) dan batas blok sesuai rumus 6c.

## R. Perbaikan pass Fase 7 (Mode Kehancuran dan jurus Api)
113. Mode Kehancuran nyala: jurus kawah di atas danau → tidak terjadi apa-apa di atas air; mangkuk tidak tenggelam ke dasar
     danau.
114. Kawah di lereng bukit → mangkuk mengikuti tanah tiap kolom (paling tinggi sejauh jari-jari di atas pusat), dipahat
     dari atas ke bawah; tidak ada rongga tertutup di bawah blok yang harus tetap (dekat air, zona terlindung).
115. Pahat lalu `/kultivasi kehancuran pulihkan` → hitungan blok dan pemulihan hanya mencakup blok yang benar-benar berubah
     (udara tidak dihitung).
116. Jurus Api (`uji teknik <id>` pada `uji boneka 3`): heaven_fire_transformation menampilkan cangkang api di setiap tahap
     dan saat dirapal ulang; tongtian_divine_fire_pillars mengenai musuh terdekat masing-masing pilar; samadhi_true_fire
     menggambar semburan di setiap pengulangan; jurus jarak jauh mengenai sasaran 33–64 blok; tanpa lapisan "kena" tetap ada
     percikan bawaan.

## S. Keamanan blok dan performa
117. Elit Api, elit Petir, Jimat Awan Api, Jimat Petir, dan `uji tribulasi deity_transformation` di dekat penangkal petir,
     tembaga, rumput, salju, dan ladang → tidak ada blok berubah, tidak ada api di blok, tidak ada entity petir (tembaga tidak
     berubah, penangkal tidak menyala).
118. Tambang bijih, patahkan tanaman, pakai batu pada Batu Magnet, racik di kuali → hanya blok yang kamu patahkan sendiri
     yang berubah.
119. Dengan 3 elit dan tribulasi berjalan: server tetap 20 TPS, FPS layak di Normal; "Otomatis" turun ke Minimal di bawah 40
     FPS.

## T. Sumber
120. Arahkan kursor ke setiap batu, herba, inti, pil baru, jimat, Kompas Urat Roh; tatap elit sambil jongkok (tingkat dan
     afiks); baca chat anugerah → semuanya punya baris Sumber. Konsep yang namanya tidak terverifikasi ditulis
     "Terinspirasi dari …"; aturan UX dan keseimbangan (Persiapan, HUD, Kejenuhan Bertarung) tidak mengaku kanon.
