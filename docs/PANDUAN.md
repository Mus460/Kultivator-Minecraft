# Panduan Pemain Kultivator

Panduan lengkap mod **Kultivator** (Minecraft 26.3, Fabric). Di mod ini **kamu sendiri** yang berkultivasi seperti
tokoh novel xianxia: memurnikan qi, menembus ranah demi ranah dari Manusia Fana sampai Dewa Abadi, bertahan dari
Tribulasi Langit, menapaki Jalan Dao, menguasai teknik, terbang di atas pedang, meracik pil, menghadapi Iblis Hati,
membangkitkan binatang roh, dan membangun formasi. Sejak Fase 6 kamu juga mendirikan **Aula Sekte**, menerima murid
dari Hari Penerimaan Murid, dan membimbing mereka naik tanpa batas sampai **Bintang Abadi**, bahkan sampai melampauimu.

Semua angka di panduan ini diambil langsung dari kode mod. Angka kultivasi (XP) selalu ditampilkan sebagai bilangan
bulat di dalam game.

> **Janji keamanan blok:** tidak ada satu pun efek, teknik, peristiwa, makhluk, atau formasi dari mod ini yang
> membakar, merusak, memasang, atau mengubah blok. Satu-satunya pengecualian adalah **Mode Kehancuran** yang harus
> dinyalakan sendiri dan **mati secara bawaan** (lihat [peringatannya](#31-mode-kehancuran-peringatan)). Lihat juga
> [bagian terakhir](#37-janji-keamanan-blok).

---

## Daftar isi

1. [Langkah pertama](#1-langkah-pertama)
2. [Tombol dan cara menggantinya](#2-tombol-dan-cara-menggantinya)
3. [Perintah](#3-perintah)
4. [Ranah dan tahap](#4-ranah-dan-tahap)
5. [Mengumpulkan kultivasi](#5-mengumpulkan-kultivasi)
6. [Leher botol dan Qi Tertimbun](#6-leher-botol-dan-qi-tertimbun)
7. [Terobosan dan Tribulasi Langit](#7-terobosan-dan-tribulasi-langit)
8. [Meditasi](#8-meditasi)
9. [Qi dan HUD](#9-qi-dan-hud)
10. [Akar spiritual](#10-akar-spiritual)
11. [Jalan Dao dan Pemahaman Dao](#11-jalan-dao-dan-pemahaman-dao)
12. [Teknik (23 teknik)](#12-teknik)
13. [Pil vanilla dan racun pil](#13-pil-vanilla-dan-racun-pil)
14. [Alkimia](#14-alkimia)
15. [Iblis Hati](#15-iblis-hati)
16. [Binatang Roh](#16-binatang-roh)
17. [Formasi Pelindung](#17-formasi-pelindung)
18. [Sekte dan Latih Tanding](#18-sekte-dan-latih-tanding)
19. [Murid](#19-murid)
20. [Aula Sekte](#20-aula-sekte)
21. [Hari Penerimaan Murid](#21-hari-penerimaan-murid)
22. [Alam jiwa, Bakat, dan Fisik Langka](#22-alam-jiwa-bakat-dan-fisik-langka)
23. [Jenjang Sekte dan Poin Kontribusi](#23-jenjang-sekte-dan-poin-kontribusi)
24. [Tugas, Lumbung, dan Misi](#24-tugas-lumbung-dan-misi)
25. [Acara sekte: turnamen, perang, Alam Dewa Hampa, menara](#25-acara-sekte-turnamen-perang-alam-dewa-hampa-menara)
26. [Kekuatan murid: Jurus Pamungkas, Api Surgawi, Pusaka Natal](#26-kekuatan-murid-jurus-pamungkas-api-surgawi-pusaka-natal)
27. [Ikatan, Segel Jiwa, Hu Fa, dan Fondasi Dao](#27-ikatan-segel-jiwa-hu-fa-dan-fondasi-dao)
28. [Bintang Abadi](#28-bintang-abadi)
29. [Pewaris, Cincin Jiwa Guru, dan Murid Melampaui Guru](#29-pewaris-cincin-jiwa-guru-dan-murid-melampaui-guru)
30. [Jurus Pemusnah](#30-jurus-pemusnah)
31. [MODE KEHANCURAN (peringatan)](#31-mode-kehancuran-peringatan)
32. [Pengakuan Langit: menjadi Dewa Abadi](#32-pengakuan-langit-menjadi-dewa-abadi)
33. [Tampilan, Kualitas Efek, dan kosmetik](#33-tampilan-kualitas-efek-dan-kosmetik)
34. [Layar kultivasi (gulungan giok)](#34-layar-kultivasi-gulungan-giok)
35. [Pencapaian](#35-pencapaian)
36. [Berkas konfigurasi](#36-berkas-konfigurasi)
37. [Janji keamanan blok](#37-janji-keamanan-blok)

---

## 1. Langkah pertama

1. **Uji akar spiritualmu.** Semua orang mulai sebagai *Manusia Fana*. Tekan `'` untuk membuka gulungan kultivasi
   lalu klik jimat *Uji Akar*, atau ketik `/kultivasi root test`. Upacara 8 detik membaca akarmu: elemen apa yang kamu
   punya dan seberapa tinggi mutunya. Hasilnya permanen.
2. **Kumpulkan kultivasi.** Basmi monster, tambang bijih, makan Wortel Emas, atau duduk bermeditasi (`;`). Bar emas di
   sekitar bola dantian (kiri bawah) terisi.
3. **Bangkit.** Saat bar Manusia Fana penuh (60), tekan `;` lagi sambil bermeditasi (atau ketik
   `/kultivasi breakthrough`). Kebangkitan 6 detik selalu berhasil dan membawamu ke *Pemurnian Qi*.
4. **Naik tahap.** Di setiap ranah ada 4 tahap (Awal, Menengah, Akhir, Puncak). Kamu naik tahap otomatis saat bar
   penuh. Di Tahap Puncak kamu harus menerobos ke ranah berikutnya.
5. **Pelajari teknik.** Di Pemurnian Qi kamu langsung menguasai *Langkah Awan* (lompat lagi di udara) dan *Telapak Qi*
   (tekan `G`). Semakin tinggi ranahmu, semakin banyak teknik yang terbuka.
6. **Pilih Dao** saat mencapai *Inti Emas*: jalan seumur hidup yang menentukan teknik dan kekuatanmu.
7. **Dirikan sekte.** Buat dan pasang **Prasasti Aula Sekte** (lihat [Aula Sekte](#20-aula-sekte)). Setiap beberapa
   hari para calon murid datang ke prasastimu saat fajar: uji mereka, terima yang kamu pilih, lalu bimbing mereka.

Kalau bingung, buka tab **Bantuan** di gulungan (`'`) atau ketik `/kultivasi` untuk melihat kartu statusmu.

---

## 2. Tombol dan cara menggantinya

| Tombol bawaan | Fungsi |
|---|---|
| `G` | Rapal teknik yang sedang dipilih |
| `;` | Mulai / berhenti meditasi. Saat bermeditasi dengan bar penuh di leher botol: memulai terobosan |
| `'` | Buka gulungan kultivasi (layar info) |
| `,` dan `.` | Pilih teknik sebelumnya / berikutnya |
| `\` | Naik / turun dari Pedang Terbang |
| Lompat di udara (`Spasi`) | *Langkah Awan*: lompatan tambahan di udara (setelah teknik ini dikuasai) |

**Mengganti tombol:** Options → Controls → Key Binds → bagian **Kultivator**. Tombol bawaan sengaja dipilih agar tidak
bentrok dengan tombol vanilla, Butter Bot, maupun Iris (K, O, R).

---

## 3. Perintah

### 3.1 `/kultivasi` (alias `/cultivate`) untuk semua pemain

| Perintah | Fungsi |
|---|---|
| `/kultivasi` atau `/kultivasi info` | Kartu status: ranah, kultivasi, Qi Tertimbun, qi, akar, Dao, Penyimpangan Qi |
| `/kultivasi meditate` | Mulai / berhenti meditasi (sama dengan `;`) |
| `/kultivasi breakthrough` | Mencoba terobosan |
| `/kultivasi hardcore on` / `off` | Mode terobosan Hardcore (gagal = turun satu ranah) |
| `/kultivasi root test` | Memulai upacara uji akar spiritual (hanya Manusia Fana yang belum diuji) |
| `/kultivasi root info` | Menampilkan akar spiritualmu |
| `/kultivasi dao` / `dao info` | Keterangan Dao dan Pemahaman Dao |
| `/kultivasi dao choose <dao>` | Memilih Dao (Inti Emas ke atas, sekali seumur hidup). `<dao>`: `sword`, `fire`, `ice`, `lightning`, `body`, `life`, `shadow`, `space` |
| `/kultivasi technique list` | Daftar teknik yang kamu kuasai, biaya, dan jeda |
| `/kultivasi technique select <id>` | Memilih teknik |
| `/kultivasi technique cast <id>` | Merapal teknik tertentu langsung |
| `/kultivasi visual <bagian> on` / `off` | Menyalakan / mematikan kosmetik: `aura`, `robe`, `back_sword`, `eyes`, `dao_circle` |
| `/kultivasi beast list` | Daftar Binatang Rohmu yang termuat |
| `/kultivasi formation list` | Daftar Formasi Pelindungmu |
| `/kultivasi alchemy` / `alchemy help` | Ringkasan cara meracik pil |
| `/kultivasi alchemy recipes` | Semua resep alkimia |
| `/kultivasi alchemy level` | Tingkat alkimia, XP alkimia, dan peluang berhasilmu |
| `/kultivasi murid list` | Daftar bernomor muridmu (lihat [Murid](#19-murid)); murid yang sedang pergi tertulis "alasan · sisa N hari" |
| `/kultivasi murid panggil` | Semua muridmu yang termuat datang ke sebelahmu, juga dari dimensi lain |
| `/kultivasi murid ikut` / `tinggal` / `meditasi` / `bantu` | Perintah untuk murid yang kamu tatap atau yang terdekat dalam 16 blok |
| `/kultivasi murid lupakan <nomor> [konfirmasi]` | Membebaskan tempat murid hidup yang raganya hilang untuk selamanya |
| `/kultivasi murid tahan` | **Tahan Petir**: kamu menerima sambaran berikutnya dari terobosan atau bencana muridmu dalam 16 blok (lihat [Hu Fa](#274-hu-fa-menjaga-terobosan)) |
| `/kultivasi aula info` | Keterangan [Aula Sekte](#20-aula-sekte)-mu: tingkat, ketenaran, batas murid, radius wilayah, kas, letak prasasti, jadwal |
| `/kultivasi aula nama <nama>` | Mengganti nama aula (3–24 huruf, angka, spasi, `-` atau `_`) |
| `/kultivasi kehancuran` / `kehancuran status` | Status [Mode Kehancuran](#31-mode-kehancuran-peringatan): tiga kunci, batas, jurus yang berjalan, sisa jendela pulih |
| `/kultivasi kehancuran sumpah` | Membuka layar merah Sumpah Kehancuran (hanya bila server mengizinkan Mode Kehancuran) |
| `/kultivasi kehancuran pulihkan` | Memulihkan blok dari jurus penghancur milikmu yang masih dalam jendela pulih |
| `/kultivasi kehancuran ya` / `batal` | Sama dengan tombol chat **[Ya, hancurkan]** / **[Batal]** sebuah perintah penghancuran |
| `/kultivasi kehancuran izin on` / `off` | **Khusus operator** (level 2): mengizinkan atau mematikan Mode Kehancuran di server dan menulis `config/kultivator.json` |

### 3.2 `/sekte` (alias `/sect`)

| Perintah | Fungsi |
|---|---|
| `/sekte` atau `/sekte help` | Keterangan sektemu, atau daftar perintah |
| `/sekte create <nama>` | Mendirikan sekte (nama 3–24 huruf); kamu menjadi Ketua Sekte |
| `/sekte invite <pemain>` | Mengundang murid (khusus Ketua Sekte, berlaku 5 menit) |
| `/sekte join <nama>` | Bergabung dengan sekte yang mengundangmu |
| `/sekte leave` | Keluar dari sekte (Ketua Sekte menyerahkan sekte ke anggota tertua) |
| `/sekte kick <pemain>` | Mengeluarkan anggota (khusus Ketua Sekte) |
| `/sekte disband` | Membubarkan sekte (khusus Ketua Sekte; ulangi dalam 10 detik untuk memastikan) |
| `/sekte info [nama]` | Keterangan sektemu atau sekte lain |
| `/sekte list` | Semua sekte di dunia ini |
| `/sekte chat <pesan>` | Bicara kepada saudara sekte yang sedang daring |
| `/sekte spar <pemain>` | Menantang pemain di dekatmu (32 blok) untuk Latih Tanding |
| `/sekte spar accept` / `decline` | Menerima / menolak tantangan (60 detik) |

### 3.3 Perintah uji untuk operator (`/kultivasi debug ...`)

Butuh izin operator (level 2). `[pemain]` berarti target pemain boleh ditambahkan di akhir.

| Perintah | Fungsi |
|---|---|
| `set realm <ranah> [tahap 1-4] [tingkat 1-9] [pemain]` | Mengatur ranah. `<ranah>`: `mortal`, `qi_refining`, `foundation`, `golden_core`, `nascent_soul`, `deity_transformation`, `dao_union`, `immortal_ascension`, `true_immortal` |
| `set qi` / `set xp` / `set storedqi <jumlah> [pemain]` | Mengatur qi, isi bar, atau Qi Tertimbun |
| `set root <mutu> <elemen...>` | Mengatur akarmu sendiri. Mutu: `trash`, `low`, `mid`, `high`, `heavenly`. Elemen: `metal`, `wood`, `water`, `fire`, `earth`, `lightning`, `ice`, `wind`, `space` |
| `set root_of <pemain> <mutu> <elemen...>` | Mengatur akar pemain lain |
| `set dao <dao> [pemain]` / `set dao none [pemain]` | Mengatur atau menghapus Dao |
| `set comprehension <0-100> [pemain]` | Pemahaman Dao terpilih |
| `set quality <cracked\|normal\|perfect\|heavenly> [pemain]` | Kualitas terobosan ranah saat ini |
| `addxp <jumlah> [pemain]` | Menambah kultivasi persis (tanpa pengali) |
| `fill [pemain]` | Bar dan qi penuh |
| `learn <teknik>\|all [pemain]` / `forget <teknik>\|all [pemain]` | Mempelajari / melupakan teknik |
| `deviation clear [pemain]` | Menghapus Penyimpangan Qi |
| `toxicity clear [pemain]` / `toxicity set <0-100>` | Mengatur racun pil |
| `reset [pemain]` | Menghapus seluruh data kultivasi |
| `show <pemain>` | Kartu status pemain lain |
| `enlighten` | Memicu Momen Pencerahan |
| `site` / `vein [persen]` | Melihat tempat spiritual / mengatur kepadatan qi di sini |
| `breakthrough force [pemain]` / `breakthrough cancel [pemain]` | Memaksa terobosan berhasil / membatalkan terobosan |
| `tribulation test [pemain]` | Tribulasi uji (tanpa naik ranah) |
| `root reroll` / `root replay` | Mengacak ulang akar / memutar ulang upacara |
| `dao reset [pemain]` / `dao comprehension <dao> <nilai> [pemain]` | Menghapus Dao / mengatur pemahaman Dao mana pun |
| `technique cooldowns reset` | Semua jeda teknik selesai |
| `heartdemon summon [pemain]` / `heartdemon pressure [nilai [pemain]]` / `heartdemon clear [pemain]` | Memanggil Iblis Hati, melihat/mengatur tekanannya, mengusirnya |
| `beast xp <jumlah>` / `beast tier <0-3>` | Untuk serigala/rubah yang kamu tatap (8 blok) |
| `formation power <menit>` / `formation clear` | Daya Formasi Pelindung yang kamu tatap (10 blok) / hapus semua data formasi di dimensi ini (blok tidak disentuh) |
| `alchemy xp <jumlah>` | Mengatur XP alkimia |
| `alchemy give <pil> <mutu 0-3> [jumlah]` | Memberi pil alkimia dengan mutu tertentu (0 Rendah … 3 Sempurna) |
| `ascension start [pemain]` | Memutar peristiwa Pengakuan Langit sekarang (di bawah Dewa Abadi hanya sebagai uji) |
| `ascension reset [pemain]` | Menghapus status "sudah naik" |
| `ascension progress [pemain]` | Kemajuan pencapaian akhir (menit meditasi, tribulasi, korban teknik) |
| `ascension add meditation\|tribulations\|technique_kills <jumlah> [pemain]` | Menambah penghitung itu untuk pengujian |
| `murid summon` | Murid baru di sebelahmu tanpa gulungan |
| `murid xp <jumlah>` | Menambah kultivasi persis untuk murid yang kamu tatap (atau yang terdekat dalam 16 blok) |
| `murid set realm <ranah> [tahap 1-4] [tingkat 1-9]` (juga `murid realm ...`) | Mengatur ranah murid itu |

Untuk sekte: `/sekte debug addmember <nama>`
menambahkan anggota tiruan agar bisa diuji sendirian.

**Perintah uji Fase 6** (juga di bawah `/kultivasi debug`). Perintah `murid ...` berlaku untuk murid yang kamu tatap,
atau yang terdekat dalam 16 blok.

| Perintah | Fungsi |
|---|---|
| `aula renown <n>` | Menambah ketenaran aulamu sebanyak n |
| `aula pk <n>` | Menambah (atau dengan angka negatif mengurangi) Poin Kontribusi kas aula |
| `aula calon datang [n]` | Calon murid datang sekarang (n = 1–6; tanpa n sebanyak jatah tingkat aula). Berdirilah dekat prasasti aktif |
| `aula calon perang` | Delapan pemuda Perang Seratus Dinasti datang sekarang |
| `aula level` | Menampilkan tingkat aula, batas dari ketenaran dan bangunan, hitungan bangunan, lalu memindai ulang |
| `aula jenjang <0-5>` | Mengatur Jenjang Sekte murid terdekat dalam 16 blok (0 Tercatat, 1 Luar, 2 Dalam, 3 Inti, 4 Tetua, 5 Tetua Agung) |
| `aula pkmurid <n>` | Menambah (atau dengan angka negatif mengurangi) Poin Kontribusi milik murid terdekat dalam 16 blok |
| `event turnamen mulai` / `event turnamen elit` | Memulai Kompetisi Daftar Kuat / Turnamen Elit sekarang |
| `event perang mulai` | Memulai Perang Seratus Dinasti dengan calon yang ada di aula |
| `event hampa <lantai>` | Murid terdekat langsung memasuki Alam Dewa Hampa mulai lantai itu |
| `event terlarang buka` | Membuka Tanah Terlarang Berwarna Darah sekarang |
| `event misi selesai` | Semua tim yang pergi (ekspedisi, utusan, tanah terlarang, bahtera) langsung selesai dan pulang |
| `murid fisik <id>` | Mengatur fisik langka: `none`, `poison`, `taixu`, `dou_di`, `golden_body`, `devouring`, `supreme_bone`, `double_pupils`, `heretic`, `attributeless` |
| `murid bakat <id>` | Menyalakan/mematikan satu bakat: `persevering`, `ancient_god`, `pill_gift`, `spirit_enhancer`, `fear_of_death`, `sword_sense`; `none` menghapus semua |
| `murid istimewa <id>` | Mengatur jenis calon istimewa: `none`, `beggar`, `regressor`, `blocked_dantian` |
| `murid warna <0-6>` | Mengatur warna alam jiwa (0 Merah … 6 Nila) |
| `murid lipat <n>` | Mengatur lipatan Pusaka Natal (memberi Pedang bila belum punya pusaka) |
| `murid api <jenis>` | Memberi Api Surgawi: `green_lotus`, `bone_chilling`, `sea_heart`, `fallen_heart`, `pure_lotus` |
| `murid tempa <n>` | Membuka Kitab Abadi Tak Mati dan mengatur Poin Tempa |
| `murid kunpeng` | Mengajarkan Sayap Kunpeng |
| `murid siap` | Menghapus jeda kekuatan murid (Jurus Pamungkas, wujud, ledakan, dan sebagainya) |
| `murid bintang set <n>` | Mengatur jumlah Bintang Abadi |
| `murid bencana mulai` | Memulai bencana berikutnya tanpa membayar Qi Bintang |
| `murid ikatan <n>` | Mengatur ikatan (0–100) |

### 3.4 Perintah uji cepat (`/kultivasi uji ...`)

Khusus operator. `/kultivasi uji` tanpa tambahan menampilkan daftar ini di chat.

| Perintah | Fungsi |
|---|---|
| `/kultivasi uji maks` | Kamu langsung Dewa Abadi Tahap Puncak: akar Surgawi, Kualitas Terobosan Surgawi, semua teknik, qi penuh (lalu Pengakuan Langit) |
| `/kultivasi uji ranah <ranah> [tahap] [tingkat]` | Lompat ke ranah mana saja, lengkap dengan tekniknya dan qi penuh |
| `/kultivasi uji tur [detik]` / `uji tur stop` | Tur semua ranah beberapa detik per ranah untuk melihat efeknya; data aslimu dikembalikan di akhir |
| `/kultivasi uji kit` | Kotak shulker berisi semua item mod, pil tiap mutu, kitab teknik, item langka, bahan formasi dan alkimia |
| `/kultivasi uji teknik` | Pelajari semua teknik, hapus jeda, isi penuh qi |
| `/kultivasi uji murid <nama\|semua> maks` | Murid itu (atau semua muridmu) langsung Dewa Abadi Tahap Puncak, akar Surgawi, semua teknik yang bisa diajarkan, qi dan nyawa penuh |
| `/kultivasi uji murid <nama\|semua> ranah <ranah> [tahap] [tingkat]` | Lompat ke ranah mana saja |
| `/kultivasi uji murid <nama\|semua> tur [detik]` | Tur ranah untuk murid itu; datanya dikembalikan di akhir |
| `/kultivasi uji murid <nama\|semua> teknik` | Semua teknik yang bisa diajarkan, jeda dihapus, qi penuh |
| `/kultivasi uji murid <nama\|semua> bintang <n>` | Mengatur Bintang Abadi: murid dinaikkan ke Puncak Dewa Abadi dulu, dan bencana yang gerbangnya ≤ n dicatat lewat |

Nama murid dengan spasi ditulis memakai garis bawah (misalnya `Lin_Yuchen`); tekan Tab untuk saran nama.

---

## 4. Ranah dan tahap

Setiap ranah (kecuali Manusia Fana) punya 4 tahap: **Awal, Menengah, Akhir, Puncak**. *Kenaikan Abadi* punya
**9 tingkat**, dan setiap tingkat juga punya 4 tahap. Jadi seluruh jalan berisi 65 posisi, dari Manusia Fana sampai
Dewa Abadi tahap Puncak.

Kultivasi yang dibutuhkan untuk memenuhi bar: `dasar ranah × (1 + 0,35 × tahap) × (1 + 0,30 × tingkat)`
(tahap 0–3, tingkat 0–8).

| # | Ranah | Awal | Menengah | Akhir | Puncak | Cara menembus ke ranah ini |
|---|---|---:|---:|---:|---:|---|
| 0 | Manusia Fana | 60 | – | – | – | – |
| 1 | Pemurnian Qi | 120 | 162 | 204 | 246 | Kebangkitan (pasti berhasil) |
| 2 | Pembangunan Fondasi | 450 | 607 | 765 | 922 | Lemparan peluang (±75%) |
| 3 | Inti Emas | 1.500 | 2.025 | 2.550 | 3.075 | Tribulasi Langit, 3 sambaran |
| 4 | Jiwa Baru Lahir | 4.500 | 6.075 | 7.650 | 9.225 | Iblis Hati + tribulasi, 5 sambaran |
| 5 | Transformasi Dewa | 12.000 | 16.200 | 20.400 | 24.600 | Tribulasi, 7 sambaran |
| 6 | Penyatuan Dao | 30.000 | 40.500 | 51.000 | 61.500 | Iblis Hati + tribulasi, 9 sambaran |
| 7 | Kenaikan Abadi (9 tingkat) | lihat bawah | | | | Tribulasi tiap tingkat, 10–18 sambaran |
| 8 | Dewa Abadi | 250.000 | 337.500 | 425.000 | 512.500 | Iblis Hati + tribulasi, 18 sambaran |

**Kenaikan Abadi, per tingkat:**

| Tingkat | Awal | Menengah | Akhir | Puncak | Tribulasi untuk masuk |
|---|---:|---:|---:|---:|---|
| 1 | 60.000 | 81.000 | 102.000 | 123.000 | 10 sambaran, 19% |
| 2 | 78.000 | 105.300 | 132.600 | 159.900 | 11 sambaran, 19,5% |
| 3 | 96.000 | 129.600 | 163.200 | 196.800 | 12 sambaran, 20% |
| 4 | 114.000 | 153.900 | 193.800 | 233.700 | 13 sambaran, 20,5% |
| 5 | 132.000 | 178.200 | 224.400 | 270.600 | 14 sambaran, 21% |
| 6 | 150.000 | 202.500 | 255.000 | 307.500 | 15 sambaran, 21,5% |
| 7 | 168.000 | 226.800 | 285.600 | 344.400 | 16 sambaran, 22% |
| 8 | 186.000 | 251.100 | 316.200 | 381.300 | 17 sambaran, 22,5% |
| 9 | 204.000 | 275.400 | 346.800 | 418.200 | 18 sambaran, 23% |

### Kekuatan dari kultivasi

Setiap kenaikan langsung memperkuat tubuhmu (ranah = nomor ranah 0–8 di tabel atas):

| Atribut | Bonus |
|---|---|
| Nyawa maksimum | `ranah × 4 + tahap + tingkat × 2` (Dewa Abadi +60 lagi) × faktor kualitas |
| Kerusakan serangan | `ranah × 0,6 + tahap × 0,15 + tingkat × 0,3` |
| Kecepatan gerak | +2,5% per ranah (maks. +25%) |
| Zirah | +1 per ranah (Dao Tubuh ×1,5) |
| Jarak jatuh aman | +1,5 blok per ranah |
| Kecepatan menambang | +5% per ranah |
| Qi maksimum | `(100 + ranah × 120 + tahap × 30 + tingkat × 60)` × faktor kualitas × faktor akar |

*Faktor kualitas* adalah rata-rata semua Kualitas Terobosan yang tercatat (Retak 0,9 · Biasa 1,0 · Sempurna 1,15 ·
Surgawi 1,3). *Faktor akar* qi = `0,85 + 0,15 × pengali mutu akar`.

---

## 5. Mengumpulkan kultivasi

| Sumber | Kultivasi dasar |
|---|---|
| Membunuh monster (`Enemy`) | nyawa maksimum monster × 0,5 |
| Membunuh bos (Wither, Naga Ender, Warden, Elder Guardian) | nyawa maksimum × 2 |
| Monster Pelindung Dao (dari tribulasi) | setengah dari biasanya |
| Hewan, penduduk desa, Iblis Hati | 0 (Iblis Hati punya hadiahnya sendiri) |
| Menambang (tanpa Sentuhan Sutra) | batu bara 1, tembaga 1, kuarsa Nether 1, emas Nether 1, besi 2, redstone 2, gugus kecubung 2, lapis 3, emas 3, berlian 10, zamrud 12; varian deepslate +50%. Puing kuno: 0 |
| Meditasi (per detik) | `0,6 + 0,25 × ranah` × tempat spiritual × formasi × kepadatan qi (lihat [Meditasi](#8-meditasi)) |
| Pil | lihat [Pil](#13-pil-vanilla-dan-racun-pil) dan [Alkimia](#14-alkimia) |
| Momen Pencerahan | 25% dari bar ranah saat ini |
| Mengalahkan Iblis Hati | 10% bar (ujian terobosan) atau 25% bar (Iblis Hati tekanan) |
| Menang Latih Tanding | 10% bar |
| Buku Pesona tanpa teknik | 30 |

**Pengali umum** (berlaku untuk semua sumber di atas kecuali perintah uji):
`mutu akar × faktor kualitas terobosan × xpMultiplier server × 0,5 saat Penyimpangan Qi`.

| Mutu akar | Sampah | Rendah | Menengah | Tinggi | Surgawi |
|---|---|---|---|---|---|
| Pengali kultivasi | ×0,6 | ×0,85 | ×1,0 | ×1,3 | ×1,7 |

Sebelum akarmu diuji, pengali akar dianggap ×1,0.

---

## 6. Leher botol dan Qi Tertimbun

* Di bawah Tahap Puncak, bar yang penuh langsung menaikkanmu satu tahap ("terobosan kecil": suara, efek kecil, baris
  obrolan). Sisa kultivasi terbawa ke tahap berikutnya, jadi satu panen besar bisa menaikkan beberapa tahap sekaligus.
* Di **Tahap Puncak** (dan di Manusia Fana) bar berhenti di penuh: itulah **leher botol**. Semua kultivasi sesudahnya
  masuk ke **Qi Tertimbun**. Qi Tertimbun tidak pernah hilang dan tidak berbatas.
* Setelah terobosan berhasil, Qi Tertimbun mengalir ke bar ranah baru (dengan aturan naik tahap biasa) sampai habis
  atau sampai leher botol berikutnya.
* Qi Tertimbun juga menambah peluang terobosan Pemurnian Qi → Pembangunan Fondasi (maks. +10%).
* Di Dewa Abadi tahap Puncak jalan telah berakhir; kultivasi selanjutnya hanya menjadi **Qi Abadi** (tampil di HUD).

---

## 7. Terobosan dan Tribulasi Langit

### 7.1 Cara memulai

Bar harus penuh di Tahap Puncak (atau di Manusia Fana). Lalu:
* tekan `;` sambil bermeditasi (petunjuk muncul di bar aksi), atau
* klik jimat **Terobosan** di gulungan, atau
* ketik `/kultivasi breakthrough`.

Syarat: hidup, berdiri di tanah (tidak terbang, melayang, menunggang), tidak sedang menerobos, tidak sedang
Penyimpangan Qi, tidak sedang Latih Tanding atau menghadapi Iblis Hati. Manusia Fana harus sudah menguji akarnya.

### 7.2 Jenis terobosan

| Dari → Ke | Jenis |
|---|---|
| Manusia Fana → Pemurnian Qi | **Kebangkitan** 6 detik, pasti berhasil. Luka tidak menghentikannya; bergerak > 6 blok membatalkannya tanpa hukuman |
| Pemurnian Qi → Pembangunan Fondasi | **Penghimpunan qi** 5 detik lalu lemparan peluang. Terhenti tanpa hukuman bila kamu terluka atau bergerak > 6 blok |
| Pembangunan Fondasi → Inti Emas, dan semua langkah sesudahnya (termasuk tiap tingkat Kenaikan Abadi dan ke Dewa Abadi) | **Tribulasi Langit** |

**Peluang Pemurnian Qi → Pembangunan Fondasi:**
`75% + (mutu akar − Menengah) × 5% + bonus pil + bonus tempat + bonus Qi Tertimbun`, paling tinggi 98%.
* Mutu akar: Sampah −10%, Rendah −5%, Menengah 0, Tinggi +5%, Surgawi +10%.
* Bonus pil: Bintang Nether +10%, Pil Pembangunan Fondasi +15% (mutu Menengah; lihat [Alkimia](#14-alkimia)).
  Semua bonus terobosan dari pil dan Iblis Hati bersama-sama paling banyak +50%.
* Bonus tempat: +5% di tempat spiritual, +3% per tingkat formasi pengumpul qi.
* Bonus Qi Tertimbun: `min(10%, Qi Tertimbun ÷ kebutuhan bar × 10%)`.

### 7.3 Tribulasi Langit

1. **Pertanda** (12 detik): judul di layar, awan gelap berkumpul 22 blok di atasmu, guruh bergemuruh pelan. Cuaca
   **tidak** diubah.
2. **Sambaran**: satu sambaran setiap 3,5 detik. Petir hanya partikel dan suara (tidak pernah entity petir).

| Target | Sambaran | Kerusakan per sambaran (dari nyawa maks.) |
|---|---:|---:|
| Inti Emas | 3 | 15% |
| Jiwa Baru Lahir | 5 | 16% |
| Transformasi Dewa | 7 | 17% |
| Penyatuan Dao | 9 | 18% |
| Kenaikan Abadi tingkat N | 9 + N | 19% + 0,5% × (N − 1) |
| Dewa Abadi | 18 | 20% |

* Sambaran terakhir **dua kali lipat**.
* Kerusakan tribulasi menembus zirah, pesona, efek, Ketahanan, dan perisai: tidak bisa ditahan.
* **Tidak pernah mematikan.** Kalau sebuah sambaran *seharusnya* membunuhmu (kerusakan ≥ nyawa + penyerapan),
  tribulasi **gagal** saat itu juga, kamu tersisa minimal 1 nyawa dan terkena Penyimpangan Qi.
* Pengurang kerusakan: Apel Emas Terpesona (×0,85), Pil Inti Emas (×0,85), Pil Penangkal Petir (×0,75), dengan
  mutu pil memperkuat efeknya; gabungan pil dan apel paling kecil ×0,5. Formasi Pelindung yang menyala di sekitarmu
  memberi ×0,85 lagi, di luar batas itu (paling kecil sekitar ×0,43).
  Server bisa mengubah skala dengan `tribulationDamageScale`.
* Tetaplah dalam **6 blok** dari tempatmu memulai. Pergi saat pertanda = batal tanpa hukuman; pergi setelah
  sambaran pertama = gagal. Keluar dunia atau mati setelah sambaran pertama juga dihitung gagal (diterapkan saat kamu
  kembali).

**Varian per dimensi:**
* **Permukaan** (dan dimensi lain): *Tribulasi Langit*, petir putih-ungu dari awan gelap.
* **Nether**: *Tribulasi Api Neraka*, tiang api dan api jiwa, suara blaze dan ghast; tiap sambaran membakar **tubuhmu**
  2 detik (hanya entity, tidak pernah blok).
* **End**: *Tribulasi Kehampaan*, partikel tongkat End, portal, dan napas naga, geraman naga, denyut hampa.

### 7.4 Pelindung Dao

Selama sambaran, gejolak langit menarik gelombang monster (setiap 3 sambaran, `1 + ranah target ÷ 2` ekor) yang muncul
10–16 blok darimu di tanah yang aman:
* Permukaan: tengkorak (pakai pedang, bukan busur), laba-laba, zombi (tidak bisa mendobrak pintu), stray, husk;
  vindicator mulai Transformasi Dewa.
* Nether: tengkorak wither, kubus magma, hoglin.
* End: endermite, phantom.

Mereka tidak memungut barang, tidak memanggil bala bantuan, tidak ikut raid, dan hanya menyerang pemain. Teman, hewan
peliharaan, dan saudara sekte di dekatmu bisa membantu: merekalah *Pelindung Dao*-mu. Sisa monster lenyap saat
tribulasi berakhir. Server bisa mematikan peristiwa ini (`daoProtectorEvent`).

### 7.5 Kualitas Terobosan

Setiap terobosan dinilai:

| Skor | Kualitas | Faktor kekuatan | Bonus Pemahaman Dao |
|---|---|---:|---:|
| < 0,35 | **Retak** | ×0,9 | +0 |
| < 0,70 | **Biasa** | ×1,0 | +2 |
| < 0,90 | **Sempurna** | ×1,15 | +5 |
| ≥ 0,90 | **Surgawi** | ×1,3 | +10 |

* Dengan tribulasi: `acak(0–1) × 0,5 + nyawa terendah selama tribulasi × 0,3 + mutu akar (0–4) × 0,05 + bonus`.
* Tanpa tribulasi: `acak × 0,7 + bonus`.
* Bonus: tempat spiritual/formasi saat memulai, Bintang Nether, pil, kemenangan atas Iblis Hati (+0,1). Di luar
  Pemurnian Qi, Pil Pembangunan Fondasi hanya bisa menaikkan bonus ini sampai +0,15.
* Faktor kekuatan dirata-rata dari semua ranah dan memengaruhi nyawa maksimum, qi maksimum, **dan pengali
  kultivasimu**. Jadi menjaga nyawa tetap tinggi selama tribulasi itu penting.

Terobosan yang berhasil memulihkan nyawa dan qi sepenuhnya. Mulai Inti Emas, seluruh server diberi tahu (bisa
dimatikan dengan `announceBreakthroughs`).

### 7.6 Penyimpangan Qi

Hukuman terobosan yang gagal (juga kalah dari Iblis Hati):
* **30% isi bar hilang** (Qi Tertimbun aman).
* Berlangsung `2400 + 600 × ranah` tick (Pemurnian Qi 2,5 menit, Inti Emas 3,5 menit, …; maks. 10 menit).
* Selama itu: **qi tidak pulih**, kultivasi **×0,5**, Kelemahan I, Kelambatan I, sesekali mual, dan tidak bisa
  menerobos maupun memakai teknik.
* Luka sebesar 25% nyawa maksimum, **tidak pernah mematikan**.
* Botol Madu memendekkannya 20% (sekali per penyimpangan); Pil Penenang Hati dan Pil Penyembuh juga membantu.
* Setiap kegagalan menambah 25 *tekanan Iblis Hati*.
* Dewa Abadi yang telah menjalani [Pengakuan Langit](#32-pengakuan-langit-menjadi-dewa-abadi) **kebal** terhadap
  Penyimpangan Qi.

### 7.7 Mode Hardcore

Aktifkan sendiri dengan `/kultivasi hardcore on` (atau jimat di tab Pengaturan, klik dua kali), atau server
memaksanya untuk semua (`hardcoreForced`). Dalam mode ini terobosan yang gagal **tidak** menyebabkan Penyimpangan Qi,
melainkan **menurunkanmu satu ranah** (ke Tahap Puncak ranah sebelumnya dengan bar kosong; Manusia Fana adalah lantai
terbawah).

---

## 8. Meditasi

Tekan `;` (atau `/kultivasi meditate`) saat berdiri di tanah. Tidak bisa saat berenang, terbang, menunggang, tidur,
atau sedang menerobos. Kamu duduk bersila dengan tangan membentuk mudra, dan butir-butir qi berwarna elemen akarmu berputar
masuk ke dantian.

Meditasi berhenti bila kamu bergerak lebih dari 0,5 blok, terluka, menyerang, memakai barang atau blok, pindah
dimensi, atau mati.

Setiap detik bermeditasi:
* qi pulih **×6** (×1,5 lagi di tempat spiritual),
* kultivasi `(0,6 + 0,25 × ranah) × tempat × formasi × max(0,15; kepadatan qi)` × pengali umum (×1,15 di dekat
  saudara sekte),
* sedikit Pemahaman Dao (+0,004/detik) bila tempat itu cocok dengan Dao-mu.

### 8.1 Tempat spiritual

Diperiksa saat mulai dan setiap 5 detik; pengalinya dikalikan satu sama lain, **maks. ×4**.

| Tempat | Syarat | Pengali | Dao yang diasah |
|---|---|---:|---|
| Puncak Gunung | Permukaan, ketinggian Y ≥ 120, langit terlihat | ×1,5 | Bayangan & Angin |
| Hutan Ceri | bioma Hutan Ceri | ×1,4 | Kehidupan |
| Cahaya Suar | Suar aktif dalam 8 blok | ×1,5 | Api |
| Resonansi Kecubung | ≥ 4 Balok Batu Kecubung / gugus kecubung dalam 4 blok | ×1,3 | Pedang |
| Kehampaan End | dimensi The End | ×1,6 | Ruang |
| Bulan Purnama | malam bulan purnama, langit terlihat | ×1,5 | Es |

### 8.2 Urat roh dan kepadatan qi

Dunia dibagi menjadi wilayah 4×4 chunk, masing-masing dengan **kepadatan qi** 0–100% (awal 100%). Meditasi
menguras `0,15%/detik × (1 + ranah × 0,25)`; kepadatan pulih perlahan 0,02%/detik. Kultivasi meditasi dikalikan
kepadatan (paling kecil ×0,15). Di bawah 20% kamu diperingatkan: saatnya pindah tempat. Kepadatan tampil di HUD dan
di bar aksi selama meditasi.

### 8.3 Momen Pencerahan

Setiap 60 detik meditasi ada peluang `4% × pengali tempat` (+2% bila sudah memilih Dao). Saat terjadi: 10 detik cahaya
keemasan, ledakan kultivasi **25% bar** (masuk ke Qi Tertimbun bila di leher botol), dan **+3 Pemahaman Dao**.

### 8.4 Formasi Pengumpul Qi

Kamu bisa membangun formasi dari blok vanilla di sekitar balok tempat dudukmu. Mod hanya **membaca** blok-blok ini
(diperiksa setiap 5 detik). Formasi aktif menampilkan garis qi dari pilar ke tubuhmu.

Keterangan: `P` = balok tempat kamu duduk, `A` = Balok Batu Kecubung **sejajar (setinggi) dengan balok tempat duduk**,
`E` = Balok Emas (sebagai tempat duduk), `R` = Tongkat End berdiri di atas kecubung, `S` = Suar atau Batu Magnet.
Setiap pilar berjarak tepat 3 blok.

**Formasi Pengumpul Qi Kecil (×1,25)** — 4 kecubung di arah mata angin:

```
Tampak atas (utara di atas)
. . . A . . .
. . . . . . .
. . . . . . .
A . . P . . A
. . . . . . .
. . . . . . .
. . . A . . .
```

**Formasi Pengumpul Qi Agung (×1,6)** — formasi kecil + 4 kecubung di sudut, dan kamu duduk di atas Balok Emas:

```
Tampak atas
A . . A . . A
. . . . . . .
. . . . . . .
A . . E . . A
. . . . . . .
. . . . . . .
A . . A . . A
```

**Formasi Bintang Surgawi (×2,2)** — formasi agung + Tongkat End di atas keempat kecubung mata angin + Suar atau Batu
Magnet tepat di bawah Balok Emas:

```
Tampak atas                    Tampak samping (potongan utara–selatan)
A . . A . . A                  Y+1:  R . . (kamu) . . R
. . . . . . .                  Y  :  A . .   E    . . A
. . . . . . .                  Y-1:  . . .   S    . . .
A . . E . . A
. . . . . . .                  (Tongkat End hanya di 4 kecubung mata angin,
. . . . . . .                   bukan di sudut)
A . . A . . A
```

Formasi juga memberi bonus Kualitas Terobosan (+0,03 per tingkat) bila kamu menerobos sambil duduk di dalamnya.

---

## 9. Qi dan HUD

* **Qi maksimum**: lihat [tabel kekuatan](#kekuatan-dari-kultivasi).
* **Pemulihan qi** per detik: 0,4% dari maksimum; ×6 saat meditasi; ×1,5 di tempat spiritual; 0 saat Penyimpangan Qi.
* Qi dipakai oleh teknik dan Pedang Terbang, dan dipulihkan oleh pil.

**HUD (kiri bawah):**
* **Bola dantian**: qi naik di dalam lingkaran berwarna elemen akarmu.
* **Cincin emas** di sekelilingnya terisi searah jarum jam sesuai isi bar kultivasi.
* Nama ranah dan tahap dalam warna ranah, angka qi, `+N Qi Tertimbun` (atau `Qi Abadi`), petunjuk "Leher botol:
  siap menerobos", sisa waktu Penyimpangan Qi (merah), peringatan racun pil.
* Teknik terpilih sebagai **ubin jimat** dengan biaya qi dan sapuan jeda melingkar.
* Saat meditasi: pengali tempat, kepadatan qi, dan nama formasi.

HUD tersembunyi saat F1 atau F3, dan bisa dimatikan di tab Pengaturan (disimpan di `config/kultivator-client.json`).

---

## 10. Akar spiritual

### 10.1 Upacara pengujian

Hanya untuk Manusia Fana yang belum diuji: jimat *Uji Akar* di gulungan atau `/kultivasi root test`. Selama 8 detik
batu-batu penguji bangkit di sekitarmu, tiap elemen menyala bergantian, lalu hasilnya diumumkan. Hasil disimpan dan
**tidak pernah diacak ulang** (hanya bisa ditingkatkan dengan pil).

**Jumlah elemen:** 5 (10%), 4 (25%), 3 (30%), 2 (25%), 1 (10%).
**Elemen klasik:** Logam, Kayu, Air, Api, Tanah. **Elemen mutan** (6% peluang satu elemen menjadi mutan): Petir, Es,
Angin, Ruang.

**Mutu:** Sampah 15%, Rendah 30%, Menengah 30%, Tinggi 18%, Surgawi 7%. Semakin sedikit elemen semakin murni: mutu
digeser naik `(3 − jumlah elemen) × 0,5` langkah (setengah langkah ditentukan acak). Akar satu elemen hampir selalu
bermutu tinggi; akar lima elemen cenderung rendah.

| Mutu | Pengali kultivasi | Faktor qi maks. | Peluang Fondasi |
|---|---:|---:|---:|
| Sampah | ×0,6 | ×0,94 | −10% |
| Rendah | ×0,85 | ×0,98 | −5% |
| Menengah | ×1,0 | ×1,0 | 0 |
| Tinggi | ×1,3 | ×1,05 | +5% |
| Surgawi | ×1,7 | ×1,11 | +10% |

Elemen akar juga mempercepat **Pemahaman Dao** yang selaras (×1,5): Logam → Pedang, Api → Api, Air atau Es → Es,
Petir → Petir, Tanah → Tubuh, Kayu → Kehidupan, Angin → Bayangan & Angin, Ruang → Ruang.

### 10.2 Pil Pembersih Sumsum

Resep tanpa pola di meja kerajinan: **Irisan Semangka Emas + Air Mata Ghast + Apel Emas + Serpihan Batu Kecubung +
Tepung Tulang**.

Klik kanan untuk menelan (akar harus sudah diuji). 8 detik siksaan (mual, Lapar II, kotoran hitam merembes keluar),
lalu **mutu akar naik satu tingkat** (maks. Surgawi). Akar dengan 3 elemen atau lebih punya 25% peluang kehilangan satu
elemen klasik yang tidak murni. Menambah 15 racun pil. Tidak bisa dipakai lagi selagi pembersihan berlangsung, dan
tidak berguna bila akarmu sudah Surgawi dengan kurang dari 3 elemen.

---

## 11. Jalan Dao dan Pemahaman Dao

### 11.1 Memilih Dao

Mulai **Inti Emas** kamu bisa memilih satu Dao, **sekali seumur hidup** (hanya operator yang bisa mengatur ulang).
Setelah terobosan ke Inti Emas, gulungan terbuka di tab *Akar & Dao* dan obrolan memberi tombol pilihan. Bisa juga
`/kultivasi dao choose <dao>`.

| Dao | ID | Elemen selaras | Teknik Dao (Inti Emas) | Teknik kitab (Jiwa Baru Lahir) |
|---|---|---|---|---|
| Dao Pedang | `sword` | Logam | Tebasan Qi Pedang | Seribu Pedang |
| Dao Api | `fire` | Api | Telapak Api | Api Phoenix |
| Dao Es | `ice` | Es, Air | Lotus Es | Penjara Gletser |
| Dao Petir | `lightning` | Petir | Telapak Petir | Petir Surgawi |
| Dao Tubuh | `body` | Tanah | Tubuh Besi | Hentakan Gunung |
| Dao Kehidupan | `life` | Kayu | Mata Air Kehidupan | Serap Kehidupan |
| Dao Bayangan & Angin | `shadow` | Angin | Langkah Bayangan | Badai Angin |
| Dao Ruang | `space` | Ruang | Kedipan Ruang | Penjara Ruang |

Dao Tubuh juga memberi zirah dari kultivasi ×1,5.

### 11.2 Pemahaman Dao

Nilai 0–100% untuk tiap Dao. Kerusakan teknik dikalikan `1 + pemahaman ÷ 100` (hingga ×2).

| Tingkat | Mulai dari |
|---|---:|
| Pemula | 0% |
| Pemahaman Kecil | 10% |
| Pemahaman Besar | 30% |
| Pemahaman Sempurna | 60% |
| Wilayah Dao | 90% |

**Sumber Pemahaman Dao** (semua ×1,5 bila akarmu selaras):

| Sumber | Jumlah |
|---|---|
| Merapal teknik milik Dao-mu (teknik universal tidak dihitung) | +0,15 per rapalan |
| **Pedang**: membunuh monster sambil memegang pedang | +0,05 |
| **Api**: membunuh monster yang sedang terbakar | +0,05 |
| **Es**: membunuh monster di bioma dingin/bersalju | +0,05 |
| **Petir**: membunuh monster saat badai petir | +0,08 |
| **Tubuh**: terkena pukulan monster yang benar-benar melukai | +0,02 |
| **Kehidupan**: pulih nyawa, memberi makan hewan, Tepung Tulang pada tanaman | +0,03 |
| **Bayangan**: membunuh sambil jongkok, di malam hari, atau di tempat gelap (cahaya ≤ 7) | +0,05 |
| **Ruang**: melempar Mutiara Ender | +0,05 |
| **Ruang**: berada di The End | +0,02 per menit |
| Meditasi di tempat spiritual yang cocok | +0,004 per detik |
| Momen Pencerahan | +3 |
| Kualitas Terobosan | +0 / +2 / +5 / +10 |
| Kitab teknik yang sudah dikuasai / belum bisa dipelajari (Dao-mu) | +5 |
| Menang Latih Tanding | +1 |
| Serpihan Gaung (khusus Dao Ruang) | +1 |

Aksi Dao (baris berhuruf tebal) dihitung paling sering sekali tiap 2 detik per jenis, dan hanya untuk Dao yang kamu
pilih. Melewati setiap tingkat diumumkan dengan efek, suara, dan pencapaian.

### 11.3 Kitab teknik (Buku Pesona)

Teknik tingkat lanjut tersimpan di **Buku Pesona** vanilla. **Jongkok + gunakan** buku itu untuk membacanya:
* bila tekniknya bisa dipelajari sekarang → teknik dikuasai dan buku habis;
* bila teknik sudah dikuasai, atau itu kitab Dao-mu yang belum bisa dipelajari → +5 Pemahaman Dao (buku habis);
* selain itu kamu diberi tahu apa yang kurang, dan buku tetap utuh;
* buku tanpa pesona yang dipetakan → +30 kultivasi.

Tooltip buku menunjukkan teknik apa yang diajarkannya dan apakah kamu sudah bisa memahaminya.

| Pesona | Teknik | Syarat |
|---|---|---|
| Ketajaman (Sharpness), Tepi Menyapu (Sweeping Edge) | Seribu Pedang | Dao Pedang, Jiwa Baru Lahir, Pemahaman 30% |
| Rupa Api (Fire Aspect), Nyala Api (Flame) | Api Phoenix | Dao Api, Jiwa Baru Lahir, Pemahaman 30% |
| Pengarung Kebekuan (Frost Walker) | Penjara Gletser | Dao Es, Jiwa Baru Lahir, Pemahaman 30% |
| Pemusatan (Channeling) | Petir Surgawi | Dao Petir, Jiwa Baru Lahir, Pemahaman 30% |
| Perlindungan (Protection), Duri (Thorns) | Hentakan Gunung | Dao Tubuh, Jiwa Baru Lahir, Pemahaman 30% |
| Pemulihan (Mending) | Serap Kehidupan | Dao Kehidupan, Jiwa Baru Lahir, Pemahaman 30% |
| Selinap Cepat (Swift Sneak), Peredaan Jatuh (Feather Falling) | Badai Angin | Dao Bayangan, Jiwa Baru Lahir, Pemahaman 30% |
| Ketakberhinggaan (Infinity), Kesetiaan (Loyalty) | Penjara Ruang | Dao Ruang, Jiwa Baru Lahir, Pemahaman 30% |
| Penumpas Bangkai (Smite) | Telapak Buddha | teknik universal, Inti Emas (tanpa syarat Dao) |

---

## 12. Teknik

Pilih teknik dengan `,` / `.` atau di tab *Teknik*, lalu rapal dengan `G`. Teknik memakai qi, punya jeda, dan **hanya
mengenai monster** (tidak pernah pemain, hewan peliharaan, penduduk desa, atau hewan damai; kecuali lawan Latih
Tanding yang sudah setuju). Teknik tidak pernah menyentuh blok. Kalau tidak ada sasaran atau tempat aman, teknik
batal tanpa memakan qi maupun jeda.

**Kekuatan teknik (D)** = `(4 + 2,5 × ranah + 0,5 × tahap) × (1 + Pemahaman Dao ÷ 100)`.

Teknik tidak bisa dipakai saat Penyimpangan Qi atau saat menerobos; pengecualiannya ujian Iblis Hati sebelum
terobosan, di mana semua teknik kecuali Pedang Terbang boleh dipakai. Merapal teknik menghentikan meditasi.

### 12.1 Teknik universal

| Teknik | Ranah | Qi | Jeda | Cara membuka | Efek |
|---|---|---:|---:|---|---|
| **Langkah Awan** (`cloud_step`) | Pemurnian Qi | 6 | 0,5 dtk | otomatis (pasif) | Tekan lompat di udara untuk lompatan tambahan: 1× (Pemurnian Qi), 2× (Pembangunan Fondasi), 3× (Inti Emas ke atas); tidak ada luka jatuh sesudahnya |
| **Telapak Qi** (`qi_palm`) | Pemurnian Qi | 10 | 1,5 dtk | otomatis | Hentakan qi lurus 12 blok ke depan, 1 D |
| **Pedang Terbang** (`flying_sword`) | Pembangunan Fondasi | 2, lalu per detik | 2 dtk | otomatis | Berdiri di atas pedang dan benar-benar terbang (tombol `\` atau `G`). Butuh pedang apa pun di inventaris (dipakai yang terbaik). Kecepatan `0,05 + 0,01 × ranah`; qi per detik `max(0,5; 2 − 0,2 × ranah)`. Qi habis, pukulan keras, terobosan, atau tombol lagi → turun dengan Jatuh Lambat 6 detik. Tidak berpengaruh di mode Kreatif/Penonton |
| **Telapak Buddha** (`buddha_palm`) | Inti Emas | 45 | 12 dtk | Kitab: Penumpas Bangkai | Telapak emas raksasa jatuh di titik yang kamu tatap (≤ 24 blok), area 5 blok, 2,5 D, menjatuhkan monster |
| **Indra Ilahi** (`divine_sense`) | Jiwa Baru Lahir | 30 | 20 dtk | otomatis | Monster dalam 48 blok bercahaya 12 detik + bijih dalam radius 12 ditandai 10 detik |
| **Wujud Dharma** (`dharma_form`) | Transformasi Dewa | 80 | 60 dtk | otomatis | 30 detik: tubuh ×2 (butuh ruang di atas kepala), Kekuatan II, Ketahanan I, jangkauan +2, garis emas bercahaya |
| **Wilayah Dao** (`dao_domain`) | Penyatuan Dao | 120 | 90 dtk | otomatis | 20 detik, radius 10 mengikutimu, efek Dao tiap detik pada monster di dalamnya (lihat bawah) |

### 12.2 Teknik Dao

Teknik Inti Emas terbuka otomatis saat memilih Dao. Teknik Jiwa Baru Lahir dipelajari dari kitab (Pemahaman 30%).

| Teknik | Dao | Ranah | Qi | Jeda | Cara membuka | Efek |
|---|---|---|---:|---:|---|---|
| **Tebasan Qi Pedang** (`sword_qi`) | Pedang | Inti Emas | 20 | 3 dtk | pilih Dao | Bulan sabit qi 16 blok, menembus semua monster di garisnya, 1,5 D |
| **Seribu Pedang** (`thousand_swords`) | Pedang | Jiwa Baru Lahir | 90 | 40 dtk | kitab | Hujan pedang cahaya pada setiap monster dalam 16 blok selama 4 detik |
| **Telapak Api** (`flame_palm`) | Api | Inti Emas | 20 | 3 dtk | pilih Dao | Kerucut api 7 blok, 1 D, membakar monster 6 detik |
| **Api Phoenix** (`phoenix_flame`) | Api | Jiwa Baru Lahir | 80 | 35 dtk | kitab | Phoenix api terbang 24 blok ke depan, membakar semua monster yang dilewati, 2 D |
| **Lotus Es** (`frost_lotus`) | Es | Inti Emas | 20 | 4 dtk | pilih Dao | Ledakan es radius 6, 1 D, Kelambatan IV + membeku 6 detik |
| **Penjara Gletser** (`glacier_prison`) | Es | Jiwa Baru Lahir | 70 | 30 dtk | kitab | Target dan monster dalam 5 blok darinya membeku di tempat 8 detik, 1,5 D |
| **Telapak Petir** (`thunder_palm`) | Petir | Inti Emas | 25 | 4 dtk | pilih Dao | Petir partikel ke target, melompat ke 4 monster lain, 1 D |
| **Petir Surgawi** (`heavenly_thunder`) | Petir | Jiwa Baru Lahir | 90 | 40 dtk | kitab | 8 sambaran petir partikel pada monster dalam 16 blok, 2 D tiap sambaran |
| **Tubuh Besi** (`iron_body`) | Tubuh | Inti Emas | 25 | 30 dtk | pilih Dao | 15 detik: Ketahanan II, kebal dorongan, memantulkan 30% serangan jarak dekat |
| **Hentakan Gunung** (`mountain_stomp`) | Tubuh | Jiwa Baru Lahir | 60 | 20 dtk | kitab | Gelombang kejut radius 7, 2 D, melempar monster ke udara |
| **Mata Air Kehidupan** (`spring_of_life`) | Kehidupan | Inti Emas | 30 | 20 dtk | pilih Dao | Memulihkan dirimu, teman, dan hewan peliharaan dalam 8 blok sebesar 40% nyawa maks., menyembuhkan racun dan wither |
| **Serap Kehidupan** (`life_drain`) | Kehidupan | Jiwa Baru Lahir | 60 | 25 dtk | kitab | Menyerap monster dalam 8 blok (1 D tiap monster), memulihkan 50% kerusakan yang dihasilkan |
| **Langkah Bayangan** (`shadow_step`) | Bayangan | Inti Emas | 20 | 6 dtk | pilih Dao | Pindah ke belakang monster yang kamu tatap (≤ 20 blok) dengan aman; serangan berikutnya dalam 5 detik ×2 |
| **Badai Angin** (`gale`) | Bayangan | Jiwa Baru Lahir | 50 | 20 dtk | kitab | Melempar monster dalam 10 blok jauh-jauh + Tak Terlihat dan Kecepatan II 6 detik |
| **Kedipan Ruang** (`space_blink`) | Ruang | Inti Emas | 25 | 5 dtk | pilih Dao | Pindah ke titik yang kamu tatap (≤ 24 blok) bila aman |
| **Penjara Ruang** (`space_prison`) | Ruang | Jiwa Baru Lahir | 80 | 35 dtk | kitab | Monster dalam 12 blok terkunci di tempat 6 detik dan ditarik berkumpul, 1,5 D |

**Efek Wilayah Dao per detik** (monster di dalam radius 10):

| Dao | Efek |
|---|---|
| Tanpa Dao | Tekanan qi 0,4 D |
| Pedang | Sayatan 0,5 D |
| Api | Membakar + 0,3 D |
| Es | Kelambatan III + membeku |
| Petir | Satu sambaran partikel 0,8 D pada monster acak |
| Tubuh | Kamu mendapat Ketahanan II |
| Kehidupan | Kamu dan teman mendapat Regenerasi II, monster mendapat Wither I |
| Bayangan | Monster dibutakan, kamu Tak Terlihat |
| Ruang | Monster Kelambatan V dan ditarik ke tengah |

---

## 13. Pil vanilla dan racun pil

Beberapa barang vanilla bekerja sebagai pil. Makanan dimakan seperti biasa; yang bukan makanan diserap dengan
**jongkok + gunakan** (satu habis, kecuali di mode Kreatif). Tooltip barang menunjukkan efeknya.

| Barang | Cara | Efek | Racun |
|---|---|---|---:|
| Wortel Emas | makan | +8 kultivasi | 2 |
| Buni Sinar (Glow Berries) | makan | +2 kultivasi, +5 qi | 2 |
| Botol Madu | minum | Penyimpangan Qi 20% lebih singkat (sekali per penyimpangan) | 3 |
| Apel Emas | makan | +30 kultivasi, +30 qi | 6 |
| Apel Emas Terpesona | makan | +250 kultivasi, qi penuh, sambaran tribulasi berikutnya ×0,85 | 20 |
| Irisan Semangka Emas | jongkok + gunakan | +20 qi | 3 |
| Serpihan Batu Kecubung | jongkok + gunakan | +15 qi | 3 |
| Serpihan Gaung | jongkok + gunakan | +60 kultivasi, bisikan tentang Indra Ilahi (sekali), Dao Ruang +1 pemahaman | 8 |
| Napas Naga | jongkok + gunakan | +400 kultivasi (pil terlarang; botol kaca dikembalikan) | 25 |
| Bintang Nether | jongkok + gunakan | +1.500 kultivasi, peluang/kualitas terobosan berikutnya +10% (pil terlarang) | 40 |
| Botol Pesona | jongkok + gunakan | +15 kultivasi (tidak dilempar) | 2 |

**Racun pil** (0–100):
* Setiap pil menambah racunnya; racun berkurang 1 setiap 30 detik.
* Kultivasi dari pil dikalikan `1 − racun ÷ 120` (racun 60 → khasiat 50%).
* Mulai 50 kamu diperingatkan. **Di atas 80** setiap pil membuatmu mual dan keracunan 5 detik.
* Racun di atas 80 juga menambah tekanan Iblis Hati (+2 per menit).

Bonus pil seperti ×0,85 tribulasi dan +10% terobosan berlaku untuk terobosan berikutnya saja.

---

## 14. Alkimia

### 14.1 Tungku Alkimia

**Kuali** apa pun (kosong, berisi air, lava, atau salju bubuk) yang berdiri **di atas Api Unggun atau Api Unggun Jiwa
yang menyala** adalah **Tungku Alkimia**. Tungku hanya dibaca: isi Kuali dan apinya tidak pernah diubah.

```
Tampak samping
  [ Kuali ]          <- tempat meracik (isi apa saja)
  [Api Unggun]       <- harus menyala; Api Unggun Jiwa lebih baik
```

### 14.2 Cara meracik

1. **Klik kanan Kuali sambil memegang bahan** → satu bahan masuk ke dalam Kuali (paling banyak 8 bahan). Isi Kuali
   tampil di bar aksi. Hanya barang yang dipakai sebuah resep yang bisa dimasukkan; barang lain (ember, botol, ramuan,
   dst.) tetap berperilaku seperti biasa pada Kuali.
2. **Klik kanan dengan tangan kosong** → melihat isi Kuali dan apakah cocok dengan sebuah resep. Ini hanya terjadi bila
   tangan kirimu juga kosong: barang di tangan kiri (ember, botol, makanan, obor, perisai, dst.) tetap dipakai seperti
   biasa dan tungku tidak bereaksi.
3. **Jongkok + klik kanan dengan tangan kosong** → mulai memurnikan (**10 detik**, uap dan api berkobar). Ini tetap
   berlaku walau tangan kirimu memegang sesuatu, kecuali barang yang bisa dipakai pada Kuali (ember, botol, dst.).
4. Hasilnya melompat keluar ke samping Kuali (ke arahmu) sebagai barang, tidak pernah jatuh ke dalam Kuali, jadi
   Kuali lava pun aman. **Gagal** = asap hitam, bahan hilang. Campuran yang tidak cocok dengan resep apa pun selalu
   menjadi asap hitam.
5. Bila api dipadamkan atau Kuali dirusak, bahan di dalam Kuali jatuh kembali sebagai barang di samping Kuali.

Urutan bahan tidak penting, tetapi isinya harus **persis** sama dengan resep (tidak lebih, tidak kurang).

### 14.3 Resep

| Hasil | Bahan | Efek (mutu Menengah) | Racun |
|---|---|---|---:|
| 2 **Pil Pengumpul Qi** | Serbuk Batu Sinar + Gula + Buni Sinar | +15% bar kultivasi (maks. 450) | 6 |
| 2 **Pil Pemulih Qi** | Serpihan Batu Kecubung + Sarang Madu + Beri Manis | memulihkan qi sebanyak 50% qi maksimum | 5 |
| 2 **Pil Penyembuh** | Irisan Semangka Emas + Wortel Emas + Beri Manis + Kelp | pulih 60% nyawa maks., Penyimpangan Qi tersisa separuh (mutu Sempurna: sembuh total) | 7 |
| 2 **Pil Penenang Hati** | Sarang Madu + Lili Lembah + Biji Kakao + Buni Sinar | tekanan Iblis Hati −50, Penyimpangan Qi 40% lebih singkat | 8 |
| 2 **Pil Binatang Roh** | Tulang + Buni Sinar + Serpihan Batu Kecubung + Wortel Emas | lihat [Binatang Roh](#16-binatang-roh) (tanpa mutu) | – |
| 2 **Pil Pembangunan Fondasi** | Wortel Emas + Serbuk Batu Sinar + Serpihan Batu Kecubung + Tepung Tulang | peluang/kualitas terobosan berikutnya +15% (penuh di Pemurnian Qi; di ranah lain separuh, dan pil hanya bisa menaikkan bonusnya sampai +15%) | 12 |
| 2 **Pil Pembersih Sumsum** | Irisan Semangka Emas + Air Mata Ghast + Apel Emas + Serpihan Batu Kecubung + Tepung Tulang | lihat [Akar spiritual](#102-pil-pembersih-sumsum) (tanpa mutu) | 15 |
| 2 **Pil Inti Emas** | Emas Batangan + Bubuk Kemamang + Apel Emas + Serpihan Batu Kecubung | sambaran tribulasi berikutnya ×0,85 | 15 |
| 2 **Pil Penangkal Petir** | Tembaga Batangan + Selaput Angan-Angan + Serpihan Batu Kecubung + Serbuk Batu Sinar + Besi Batangan | sambaran tribulasi berikutnya ×0,75 | 15 |
| 2 **Pil Pemelihara Jiwa** | Air Mata Ghast + Serpihan Gaung + Buah Korus + Ketuat Nether | +30% bar kultivasi (maks. 3600; hanya Jiwa Baru Lahir ke atas) | 22 |
| 1 **Pil Dewa** | Bintang Nether + Azimat Keabadian + Apel Emas Terpesona + Serpihan Gaung + Jantung Lautan | +100% bar kultivasi, qi penuh, **racun pil menjadi 0** | 0 |

* Pil Pembersih Sumsum dan Pil Binatang Roh juga tetap bisa dibuat di meja kerajinan; di tungku, pemurnian Sempurna
  memberi satu butir tambahan.
* Pil hasil alkimia ditelan dengan **klik kanan**. Jeda 1 detik antar pil yang sama.
* Kultivasi dari pil dikalikan khasiat racun pil seperti pil vanilla. Batas maks. Pil Pengumpul Qi dan Pil Pemelihara
  Jiwa berlaku untuk mutu Menengah dan ikut dikalikan mutu pil (misalnya Sempurna: maks. 720 dan 5760).
* Pengurang tribulasi dari pil saling dikalikan (paling kecil ×0,5); Apel Emas Terpesona hanya menurunkan faktornya ke
  ×0,85 bila masih di atas itu, jadi makanlah apelnya sebelum menelan pil. Semuanya hanya berlaku untuk tribulasi
  berikutnya.
* `/kultivasi alchemy recipes` menampilkan semua resep di dalam game.

### 14.4 Peluang berhasil dan tingkat alkimia

**Peluang berhasil** = `60% + 4% × tingkat alkimia + 10% (Api Unggun Jiwa) + 10% (Dao Api)`, paling tinggi 95%.

**Tingkat alkimia** 0–10 naik dengan XP alkimia (`tingkat n` butuh `40 × n²` XP):

| Tingkat | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| XP alkimia | 40 | 160 | 360 | 640 | 1.000 | 1.440 | 1.960 | 2.560 | 3.240 | 4.000 |

XP alkimia per resep (mutu Rendah): Pengumpul Qi/Pemulih Qi 15, Penyembuh 20, Penenang Hati 22, Binatang Roh 24,
Pembangunan Fondasi 28, Pembersih Sumsum 30, Inti Emas 45, Penangkal Petir 55, Pemelihara Jiwa 75, Pil Dewa 200;
+20% per tingkat mutu di atas Rendah. Kegagalan tetap memberi 25% XP resepnya (campuran tak dikenal: 2 XP).
`/kultivasi alchemy level` menampilkan tingkat dan peluangmu.

### 14.5 Mutu pil

Mutu ditentukan oleh `acak(0–1) + tingkat alkimia × 0,03`; Api Unggun Jiwa memberi 25% peluang menaikkan mutu satu
tingkat.

| Nilai | Mutu | Kekuatan efek | Racun |
|---|---|---:|---:|
| < 0,35 | **Rendah** | ×0,8 | ×1,2 |
| < 0,75 | **Menengah** | ×1,0 | ×1,0 |
| < 0,95 | **Tinggi** | ×1,25 | ×0,8 |
| ≥ 0,95 | **Sempurna** | ×1,6 (berkilau) | ×0,5 |

Nama pil berwarna sesuai mutunya, dan tooltip menunjukkan efek persis untuk mutu itu. Di inventaris mode Kreatif,
setiap pil tersedia dalam keempat mutu. Tingkat alkimia dan jumlah pil yang sudah kamu murnikan juga tampil di tab **Status**
gulungan.

**Perintah:** `/kultivasi alchemy` (bantuan), `alchemy recipes`, `alchemy level`. Uji operator:
`/kultivasi debug alchemy xp <jumlah>`, `/kultivasi debug alchemy give <pil> <mutu 0-3> [jumlah]`
(pil: `qi_gathering`, `qi_recovery`, `foundation`, `golden_core`, `heart_calming`, `healing`, `tribulation_warding`,
`soul_nurturing`, `immortal`).

---

## 15. Iblis Hati

**Iblis Hati** adalah salinan gelap dirimu sendiri: model pemain dengan kulitmu, tubuh yang kelam, dan mata merah
menyala. Ia hanya bertarung melawanmu, kebal terhadap semua yang lain, tidak pernah meledak, tidak menjatuhkan apa
pun, dan tidak tersimpan di dunia.

**Kapan muncul**
* **Ujian terobosan**: sebelum tribulasi ke **Jiwa Baru Lahir**, **Penyatuan Dao**, dan **Dewa Abadi** (sekali per
  ranah; bisa dimatikan server dengan `heartDemonTrials`).
* **Tekanan Iblis Hati**: tekanan bertambah dari terobosan gagal (+25), racun pil di atas 80 (+2 per menit), dan
  banyak membunuh (monster +0,15, makhluk lain +1, paling banyak 6 per menit). Tekanan turun 1 setiap 5 menit. Di atas
  100 ada peluang 2% per menit: kamu diperingatkan 10 detik sebelumnya, dan iblis muncul begitu kamu berdiri di tanah
  yang kokoh.

**Pertarungan**
* Waktu 120 detik. Nyawa iblis = 1,2 × nyawa maksimummu, kerusakannya setara seranganmu.
* Bila nyawamu di bawah 50% saat ia muncul, nyawamu dinaikkan ke 50%.
* **Kalah** bila nyawamu jatuh di bawah 20% (pukulan yang akan melakukannya dibatalkan, jadi kamu tidak mati), waktu
  habis, kamu mati, keluar dunia, atau pindah dimensi.
* Ejekan muncul di obrolan sekitar setiap 15 detik.

**Hasil**
* **Menang — "Hati Dao Teguh"**: tekanan kembali 0, Kualitas Terobosan berikutnya +0,1, dan kultivasi (10% bar plus
  nyawa penuh pada ujian terobosan, lalu tribulasi dimulai; 25% bar pada iblis tekanan).
* **Kalah**: ujian terobosan → terobosan gagal (Penyimpangan Qi, tribulasi tidak dimulai). Iblis tekanan →
  Penyimpangan Qi ringan dan tekanan −30.

---

## 16. Binatang Roh

Serigala yang **kamu jinakkan** atau rubah yang **memercayaimu** bisa dibangkitkan menjadi Binatang Roh dengan **Pil
Binatang Roh**.

* **Resep tanpa pola:** Tulang + Buni Sinar + Serpihan Batu Kecubung + Wortel Emas.
* Klik kanan hewan itu sambil memegang pil. Rubah liar tidak memercayai siapa pun: kembangbiakkan dua rubah dengan Beri
  Manis, dan anaknya akan memercayaimu.
* Pil berikutnya memberi **+40 XP roh** (sekali tiap 30 detik per binatang).
* Binatang roh juga mendapat XP roh dari monster yang ia bunuh (nyawa maks. korban) dan setengahnya dari monster yang
  kamu bunuh dalam 16 blok darinya.

| Tingkat | XP roh total | Ranah tuan minimal | Serigala | Rubah | Bonus |
|---|---:|---|---|---|---|
| 0 | 0 | – | Serigala Tersadar | Rubah Tersadar | – |
| 1 | 100 | – | Serigala Roh | Rubah Roh | +10 nyawa, +2 serangan, +10% kecepatan |
| 2 | 400 | Inti Emas | Serigala Petir Bulan | Rubah Ekor Tiga | +20 nyawa, +4 serangan, +20% kecepatan, ukuran ×1,1 |
| 3 | 1.200 | Jiwa Baru Lahir | Serigala Langit | Rubah Ekor Sembilan | +40 nyawa, +8 serangan, +30% kecepatan, ukuran ×1,2 |

**Kemampuan:**
* Tingkat 2 serigala — **Petir Bulan**: 20% peluang gigitannya memanggil sambaran petir partikel (+50% kerusakan).
* Tingkat 2 rubah — **Api Rubah**: gigitannya membakar musuh 4 detik (hanya tubuh musuh, tidak pernah blok; tidak
  menyala di dekat salju bubuk atau Kuali berisi air/salju bubuk).
* Tingkat 3 serigala — **Penjaga Langit**: setiap 5 detik kamu (dalam 8 blok) pulih 1 nyawa, dan di malam hari
  mendapat Regenerasi.
* Tingkat 3 rubah — **Berkah Ekor Sembilan**: setiap 5 detik kamu pulih 1 nyawa dan mendapat Kecepatan.

Binatang roh tidak bisa melampaui tuannya: evolusi menunggu sampai ranahmu cukup. Setiap evolusi diiringi sinematik,
aura sesuai tingkat, dan namanya berganti (kecuali kamu sudah menamainya sendiri). Rubah roh mengikutimu dan menyerang
apa pun yang menyerangmu; binatang roh hanya menyerang sasaran yang boleh diserang teknikmu.
`/kultivasi beast list` menampilkan semua binatang rohmu.

---

## 17. Formasi Pelindung

Formasi yang mengusir monster dari rumahmu.

**Susunan:** satu **Batu Magnet** dengan 4 **Obsidian Menangis** tepat 4 blok ke utara, selatan, timur, dan barat,
**setinggi** Batu Magnet.

```
Tampak atas (L = Batu Magnet, O = Obsidian Menangis)
. . . . O . . . .
. . . . . . . . .
. . . . . . . . .
. . . . . . . . .
O . . . L . . . O
. . . . . . . . .
. . . . . . . . .
. . . . . . . . .
. . . . O . . . .
```

**Mengaktifkan:**
1. Formasi yang baru dibangun **tertidur** (mati).
2. **Jongkok + gunakan Batu Magnet sambil memegang Serpihan Batu Kecubung**: formasi bangkit dan menjadi milikmu.
   Setiap serpihan = **10 menit daya**, paling banyak **2 jam**.
3. **Jongkok + gunakan dengan tangan kosong** untuk mematikan atau menyalakannya lagi; daya yang tersimpan tetap.
   Hanya pemilik yang bisa mengisi dan menyalakan/mematikannya.
4. Bila ada pilar yang kurang, pilar itu ditandai merah dan kamu diberi tahu yang mana.

**Saat menyala** (dan chunk-nya termuat): monster dalam **radius 16 blok** didorong keluar dan menerima 1 kerusakan
sihir per detik. Kubah berkilau terlihat di sekelilingnya. Saat menyala, formasi juga melemahkan sambaran Tribulasi
Langit bagi siapa pun yang berada di dalamnya menjadi ×0,85. Formasi padam bila dayanya habis, sebuah pilar hilang, atau
Batu Magnetnya lenyap (kamu diberi tahu). Daftar formasimu: `/kultivasi formation list`. Server bisa mematikan fitur
ini (`protectiveFormations`).

---

## 18. Sekte dan Latih Tanding

**Sekte** (untuk server multipemain): pendirinya menjadi **Ketua Sekte**, anggota lain adalah **Murid**. Perintah ada di
[bagian 3.2](#32-sekte-alias-sect).

* **Meditasi bersama:** bermeditasi dalam 16 blok dari saudara sekte memberi kultivasi **×1,15**.
* Saudara sekte di dekatmu ikut menjadi Pelindung Dao saat tribulasi.
* `/sekte chat` untuk bicara hanya dengan sesama anggota.
* Sekte `/sekte` hanyalah keanggotaan antarpemain. Rumah sekte di dunia, tempat murid diterima dan dibimbing, adalah
  [Aula Sekte](#20-aula-sekte) milik satu pemain. Teman satu `/sekte` boleh membuka gulungan aulamu untuk melihat saja
  dan menyumbang ke kasnya.

**Latih Tanding** (duel persahabatan, tidak harus satu sekte):
1. `/sekte spar <pemain>` (dalam 32 blok, dimensi yang sama). Lawan punya 60 detik untuk `/sekte spar accept`.
2. Hitung mundur 3-2-1 (tidak ada pukulan yang masuk), lalu **120 detik** bertarung. Selama itu teknik kalian boleh
   saling mengenai.
3. Pukulan yang akan membuat salah satu di bawah **10% nyawa** dibatalkan dan mengakhiri duel: penyerangnya menang.
   Tidak ada yang mati. Teknik tidak pernah melempar atau menarik lawan (hanya melukai dan menahan); bila seseorang
   tetap jatuh ke bawah dunia, ia dikembalikan ke sebelah lawannya dan kalah.
4. Duel berakhir tanpa pemenang bila ada yang keluar, mati, pindah dimensi, berjarak lebih dari 48 blok, berganti ke
   mode Kreatif/Penonton, atau waktu habis.
5. **Hadiah** pemenang (hanya untuk pertarungan sungguhan: minimal 15 detik, lawan mulai dengan ≥ 80% nyawa dan
   kehilangan ≥ 40% karena pemenang, sekali per 10 menit untuk pasangan yang sama): +10% bar kultivasi dan +1 Pemahaman
   Dao. Sekte pemenang diberi tahu.

Aturan PvP vanilla (game rule `pvp`, tim) tetap berlaku.

---

## 19. Murid

**Murid** adalah pengikut yang kamu terima dan bimbing sendiri. Mereka berkultivasi seperti kamu (ranah, tahap, leher
botol, Qi Tertimbun), mempelajari teknik yang kamu ajarkan, menelan pil darimu, dan bertarung di sisimu. Batas murid
hidup adalah **5**, atau `5 + Tingkat Aula / 2` (paling banyak **9**) selama kamu punya [Aula Sekte](#20-aula-sekte).
Murid yang sedang pergi (misi, utusan, Pelarian Jiwa) tetap dihitung.

Sejak Fase 6 murid tidak berhenti di Dewa Abadi: mereka memadatkan [Bintang Abadi](#28-bintang-abadi) tanpa batas, naik
[jenjang sekte](#23-jenjang-sekte-dan-poin-kontribusi), membawa [bakat dan fisik langka](#22-alam-jiwa-bakat-dan-fisik-langka),
dan bisa [melampaui Gurunya](#293-murid-melampaui-guru).

### 19.1 Menerima murid

* **Hari Penerimaan Murid** adalah jalan utama: dirikan Aula Sekte, lalu para calon datang ke prasastimu setiap
  beberapa hari saat fajar. Uji dan terima mereka di tablet Calon (lihat [bagian 21](#21-hari-penerimaan-murid)).
* **Gulungan Penerimaan Murid** tidak bisa di-craft lagi. Gulungan masih ada di tab kreatif Kultivator (tooltip-nya
  "Hanya kreatif"), lewat `/give @s kultivator:recruitment_scroll`, dan `/kultivasi debug murid summon`. Gunakan
  gulungan itu: seorang murid muncul di depanmu (atau di sampingmu bila di depan tidak ada tanah aman) sebagai
  **Manusia Fana**, dan akar spiritualnya langsung diuji dengan peluang yang sama seperti milikmu (lihat
  [Akar spiritual](#10-akar-spiritual)). Gulungan hanya terpakai bila murid benar-benar datang.
* Setiap murid mendapat nama xianxia acak dan salah satu kulit pemain bawaan. Hanya Gurunya yang bisa mengganti nama
  dengan Label Nama.
* Murid tidak pernah menghilang sendiri, tersimpan bersama dunia, dan tidak bisa diikat tali atau ditunggangi.
* Klik kanan muridmu dengan **tangan kosong** (atau sambil jongkok dengan barang lain) untuk membuka gulungannya. Murid
  orang lain hanya menyebutkan siapa Gurunya.

### 19.2 Gulungan murid

| Tab | Isi |
|---|---|
| **Status** | Ranah, bar kultivasi, qi, Qi Tertimbun, akar dan mutunya, Dao dan Pemahaman Dao, nyawa, kegiatan, perintah, monster yang dikalahkan |
| **Teknik** | Teknik yang kamu kuasai sebagai kartu segel: ajarkan, atau baca alasan kenapa belum bisa |
| **Perintah** | Lima perintah (lihat 19.3) |
| **Pil & Terobosan** | Pil di tanganmu, jeda pil, tombol Terobosan, pilihan Dao, dan tombol Lepaskan murid (klik dua kali) |
| **Bakat** | Alam jiwa, jenjang sekte, bakat, fisik langka, seni sekte, Jurus Pamungkas; tombol *Jurus Pamungkas*, *Fusi*, *Ledakan Gerbang* (lihat [bagian 22](#22-alam-jiwa-bakat-dan-fisik-langka) dan [26](#26-kekuatan-murid-jurus-pamungkas-api-surgawi-pusaka-natal)) |
| **Pusaka** | Pusaka Natal (*Tempa Lipatan*), Kitab Abadi Tak Mati, Api Surgawi, *Sayap Kunpeng* |
| **Jalan** | Empat bagian (pilih dengan "‹ bagian ›"): Bintang, Ikatan, Pewaris, Pemusnah (lihat [bagian 27–31](#27-ikatan-segel-jiwa-hu-fa-dan-fondasi-dao)) |

Gulungan memperbarui isinya sendiri setiap 5 detik selama terbuka dan tertutup bila murid lebih dari 16 blok darimu.
Arahkan kursor ke baris di tab Bakat, Pusaka, dan Jalan untuk membaca keterangan dan baris **Sumber**-nya.

### 19.3 Perintah

| Perintah | Perilaku |
|---|---|
| **Ikut** (bawaan) | Berjalan bersamamu, menyusul dengan teleportasi bila tertinggal lebih dari 24 blok. Melawan apa pun yang menyerangmu atau menyerangnya |
| **Tinggal & Jaga** | Menjaga tempat perintah diberikan: melawan monster dalam 12 blok, lalu kembali ke posnya. Monster netral yang tenang (enderman, piglin, piglin zombi, Warden) dibiarkan selama mereka tidak menyerang |
| **Meditasi** | Duduk bersila di tempat itu dan berkultivasi **×1,5**. Hanya membalas bila diserang |
| **Bantu** | Ikut bersamamu dan menyerang apa yang kamu serang serta apa yang menyerangmu |
| **Jaga Aula** | Berpatroli dalam 32 blok dari Prasasti Aula Sekte-mu dan melawan setiap makhluk jahat di wilayah sekte. Hanya untuk **Pelindung Sekte** (lihat [bagian 23](#231-jenjang-sekte)) |

* **Murid Tercatat** hanya boleh Ikut atau Meditasi (dan menjalankan tugas sekte); perintah lain ditolak dengan
  alasannya.
* Murid yang sedang bertugas di pos aula tidak ikut pindah dimensi bersamamu.
* Murid tidak pernah memulai perkelahian sendiri dengan perintah Ikut atau Bantu.
* Di bawah **25% nyawa** murid mundur ke sisimu; bila kamu tidak terjangkau (misalnya di atas pilar) ia bertarung
  di tempatnya.
* Di luar pertarungan murid pulih 1 nyawa tiap 3 detik; selama bermeditasi 1% nyawa maks. per detik.
* Murid dengan perintah Ikut atau Bantu yang berada dalam 24 blok darimu **ikut pindah dimensi** bersamamu: ia muncul
  langsung di sebelahmu, tidak pernah lewat portal. Bila kamu bangkit di dimensi lain setelah mati, mereka tertinggal;
  `/kultivasi murid panggil` menjemput mereka.
* Murid hanya menyerang sasaran yang boleh diserang teknikmu: monster, makhluk yang menyerangmu, dan lawan Latih
  Tanding. Tidak pernah kamu, pemain lain di luar Latih Tanding, hewan peliharaan (kecuali yang menyerangmu), penduduk
  desa, hewan jinak, murid lain, binatang roh, atau Iblis Hati (urusan hati seorang kultivator). Creeper, ghast, blaze,
  breeze, ravager, silverfish, Wither, dan creaking yang terikat jantungnya juga tidak pernah diserang, karena
  pertarungannya akan mengubah blok.
* Kamu, murid-muridmu yang lain, dan hewan peliharaanmu tidak bisa melukai muridmu; pemain lain hanya dalam Latih
  Tanding denganmu.

### 19.4 Kultivasi murid

Murid naik ranah dengan aturan yang sama denganmu (bar, tahap, leher botol, Qi Tertimbun), tetapi dengan pengali
**warna alam jiwanya** (Merah ×0,6 … Nila ×1,7, menggantikan pengali mutu akar; lihat [bagian 22](#221-warna-alam-jiwa))
× **faktor jenjang sekte** (Murid Tercatat ×0,55 … Tetua Agung ×1,0; lihat [bagian 23](#231-jenjang-sekte)) × bonus
bakat, fisik, bintang, gelar, dan menara × pengali server. Murid fase 5 mendapat warna sesuai mutu akarnya (Menengah =
Kuning) dan jenjang sesuai ranahnya.

| Sumber | Kultivasi |
|---|---|
| Meditasi (duduk bersila: di posnya, di sebelahmu saat kamu diam 5 detik atau bermeditasi, atau saat kamu luring / di dimensi lain) | `0,4 + 0,2 × ranah` per detik, ×1,5 dengan perintah Meditasi |
| Bermeditasi dalam 8 blok darimu saat kamu juga bermeditasi | tambahan **10%** dari kultivasi meditasimu sendiri (tempat spiritual, formasi, urat roh, dan pengalimu ikut terhitung) |
| Monster yang ia kalahkan | nyawa maks. monster × 0,4 |
| Monster yang ia lukai dalam 5 detik terakhir tetapi dikalahkan orang lain (dalam 16 blok) | separuhnya |
| Pil darimu | lihat 19.6 |

Selama **Penyimpangan Qi** kultivasinya setengah dan qinya tidak pulih. Bonus kekuatan murid: nyawa +`(ranah × 6 +
tahap × 1,5 + tingkat × 3)` × kualitas terobosannya, serangan +`ranah + tahap × 0,25 + tingkat × 0,5`, kecepatan +2% per
ranah (maks. 20%). Qi maks. `60 + ranah × 80 + tahap × 20 + tingkat × 40`, pulih 0,6% per detik (×4 saat bermeditasi).

### 19.5 Mengajarkan teknik

* Tab **Teknik** menampilkan semua teknik yang kamu kuasai. Mengajar memakan **20% Qi maksimummu**.
* Murid harus sudah mencapai ranah minimal teknik itu, dan untuk teknik Dao harus menempuh Dao yang sama.
* **Pedang Terbang, Wujud Dharma, Wilayah Dao, Indra Ilahi, dan Langkah Awan tidak bisa diajarkan** (butuh raga dan
  indra seorang kultivator sendiri).
* Murid merapal teknik dengan qinya sendiri (60% biaya qi teknik itu) dan jeda yang sama dengan milikmu, sekali tiap
  detik saat bertarung: Mata Air Kehidupan lebih dulu bila kamu di bawah separuh nyawa atau ia di bawah 40%, Tubuh Besi
  saat musuh dekat, lalu teknik serangan terkuat yang siap. Kerusakan teknik murid `D = (3 + 2 × ranah + 0,5 × tahap) ×
  (1 + Pemahaman Dao / 100)` dikali pengali tiap teknik.
* Setiap teknik Dao miliknya yang ia rapal, setiap detik meditasi, dan setiap monster yang ia kalahkan menambah
  Pemahaman Dao-nya (×1,5 bila akarnya selaras).

### 19.6 Pil untuk murid

Pegang pil lalu klik kanan muridmu (atau pakai tab Pil & Terobosan). Berlaku untuk pil vanilla (lihat
[bagian 13](#13-pil-vanilla-dan-racun-pil)), semua pil alkimia, dan Pil Pembersih Sumsum:

* Efeknya **70%** dari angka untuk pemain (kultivasi, qi, penyembuhan, Penyimpangan Qi, bonus terobosan, keringanan
  tribulasi). Bintang Nether menambah peluang Fondasi berikutnya; Apel Emas Terpesona, Pil Inti Emas, dan Pil Penangkal
  Petir melemahkan sambaran tribulasi berikutnya.
* **Tanpa racun pil**, tetapi hanya **satu pil sejenis per menit** untuk tiap murid.
* Pil Pemelihara Jiwa baru bisa ditelan murid ranah Jiwa Baru Lahir. Pil Binatang Roh ditolak.
* Pil Pembersih Sumsum menaikkan mutu akar satu langkah (paling tinggi Surgawi); akar dengan 3 elemen atau lebih punya
  25% peluang kehilangan satu elemen.

### 19.7 Terobosan murid

Saat bar penuh di tahap Puncak (leher botol), kamu diberi tahu di obrolan. Mulai terobosannya dengan tombol
**Terobosan** di tab Pil & Terobosan. Syarat: kamu dalam **16 blok**, murid tidak sedang Penyimpangan Qi, dan untuk
tribulasi nyawanya minimal 50%. Selama terobosan murid diam di tempat.

| Terobosan | Jalannya |
|---|---|
| Manusia Fana → Pemurnian Qi (**Kebangkitan**) | 6 detik menghimpun qi, selalu berhasil |
| Pemurnian Qi → Pembangunan Fondasi | 5 detik menghimpun qi lalu peluang `70% + (mutu akar − Menengah) × 5%` + bonus pil (5–98%). Batal tanpa akibat bila murid terluka atau dipindahkan lebih dari 6 blok |
| Inti Emas ke atas (**tribulasi kecil**) | 6 detik awan pertanda, lalu `2 + ranah tujuan ÷ 2` sambaran (3 untuk Inti Emas) berselang 3 detik, masing-masing 18% nyawa maks. murid (pil meringankannya). Sambaran **tidak pernah membunuh** |

* **Gagal** (peluang meleset, sambaran yang seharusnya mematikan, atau terbawa pergi setelah sambaran pertama):
  **Penyimpangan Qi 3 menit** (lemah, qi tidak pulih, kultivasi setengah) dan bar berkurang 30%.
* Tribulasi yang ditinggalkan setelah sambaran pertama (tempatnya tidak termuat lagi, atau dunia disimpan dan ditutup)
  juga dihitung **gagal** saat murid termuat kembali, dan kamu diberi tahu begitu daring.
* Berhasil: kualitas terobosan (Retak, Biasa, Sempurna, Surgawi) seperti milikmu, nyawa dan qi penuh, dan Qi Tertimbun
  mengalir ke bar.
* Di **Inti Emas** kamu memilihkan **Dao** untuk murid di tab Pil & Terobosan (sekali seumur hidupnya). Ia mendapat
  pemahaman awal dan bisa mempelajari teknik Dao itu.
* **Jagalah** terobosannya (Hu Fa): bermeditasi atau diam sambil jongkok dalam 8 blok meringankan sambaran, menaikkan
  kualitas dan peluang Fondasi, dan membuka **Tahan Petir**. Mulai tribulasi Inti Emas, gelombang Pengawal Dao datang
  setiap 2 sambaran; yang kamu bunuh sendiri meringankan sambaran berikutnya. Lihat [Hu Fa](#274-hu-fa-menjaga-terobosan).
* Terobosan Pemurnian Qi → Pembangunan Fondasi yang berhasil menentukan **Fondasi Dao** murid (Fana, Bumi, Langit, atau
  Sempurna; lihat [bagian 27.5](#275-fondasi-dao)).
* Sambaran tribulasi tidak pernah membuat nyawa murid di bawah 1, juga setelah pengali kelelahan.
* Semua efek terobosan hanyalah partikel dan suara: tidak ada entity petir, cuaca tidak berubah, blok tidak tersentuh.

### 19.8 Gugur dan Papan Jiwa

* Murid yang gugur meninggalkan **Papan Jiwa Murid** di tempatnya: bercahaya menembus dinding, tidak pernah menghilang,
  kebal api, lava, ledakan, dan kaktus. Kamu diberi tahu koordinat dan dimensinya.
* Pengecualian Fase 6: murid **Jiwa Baru Lahir ke atas** yang gugur saat Aula Sekte-mu aktif tidak meninggalkan papan;
  jiwanya kabur ke prasasti dan terbentuk kembali ±10 menit kemudian ([Pelarian Jiwa](#265-pelarian-jiwa-baru-lahir)).
  Darah Abadi (Kitab Abadi Tak Mati) dan Perlindungan Jiwa (Cincin Jiwa Guru) masing-masing sekali sehari menolak
  pukulan maut.
* Tooltip papan seorang pewaris menyebut generasinya ("Pewaris · Generasi ke-N").
* Bila ia gugur di atas kehampaan (jatuh dari dunia, di tepi pulau End), papannya terbang ke tasmu. Bila kamu sedang
  luring, papan muncul di tempat terakhir ia berdiri dengan aman (atau di posnya, atau di titik muncul dunia), jadi
  papannya tidak pernah hilang.
* Gunakan papan itu untuk memanggil jiwanya kembali di sebelahmu, dengan nyawa penuh dan seluruh kultivasi serta
  teknik yang sudah dipelajari. Hanya Gurunya yang bisa, dan batas murid hidup tetap berlaku.
* Papan dari kehidupan sebelumnya hanyalah gema: jiwa yang sama tidak bisa dibangkitkan dua kali.
* Murid yang gugur saat kamu luring tercatat sebagai gugur begitu kamu masuk lagi, juga setelah server dimulai ulang.
* **Melepaskan** murid (tab Pil & Terobosan, klik dua kali) membuatnya membungkuk, berpamitan, dan pergi untuk
  selamanya tanpa papan; tempatnya langsung kosong.

### 19.9 Perintah obrolan

| Perintah | Fungsi |
|---|---|
| `/kultivasi murid list` | Daftar bernomor semua muridmu: nama, ranah, perintah, dan di mana mereka (jarak, di dimensi lain, jauh dan tidak termuat, sedang pergi dengan alasan dan sisa hari, atau gugur) |
| `/kultivasi murid tahan` | Tahan Petir untuk terobosan atau bencana murid terdekat (lihat [27.4](#274-hu-fa-menjaga-terobosan)) |
| `/kultivasi murid panggil` | Semua muridmu yang termuat datang ke sebelahmu dan kembali mengikutimu, juga dari dimensi lain |
| `/kultivasi murid ikut` / `tinggal` / `meditasi` / `bantu` | Memberi perintah kepada murid yang kamu tatap, atau murid terdekat dalam 16 blok |
| `/kultivasi murid lupakan <nomor>` | Jalan keluar bila raga seorang murid hidup hilang untuk selamanya: tanpa `konfirmasi` hanya memperingatkan; dengan `konfirmasi` tempatnya dibebaskan. Raga yang ternyata masih ada akan pergi saat termuat lagi |

Perintah uji operator: `/kultivasi debug murid summon` (murid baru tanpa gulungan), `murid xp <jumlah>` (kultivasi
persis), `murid set realm <ranah> [tahap 1-4] [tingkat 1-9]` (atau `murid realm ...`) untuk murid yang kamu tatap atau
yang terdekat dalam 16 blok.

**Keamanan blok:** murid tidak membuka pintu, tidak memungut barang, tidak menekan pelat tekan atau tali jebakan, tidak
menginjak ladang atau telur penyu, tidak melewati portal, dan tidak pernah mengubah blok. Monster yang dipukul atau
dilempar murid juga tidak bisa merusak ladang atau melewati portal, dan monster yang dikalahkan murid tanpa bantuan
pemain tidak membuat katalis sculk menyebar.

---

## 20. Aula Sekte

Sejak Fase 6 sektemu punya rumah di dunia: **Aula Sekte**. Para calon murid datang ke prasastinya, dan murid-muridmu
naik jenjang, menjalankan tugas, berangkat misi, dan bertanding di sana. Aula Sekte milik satu pemain (kamu sebagai
**Ketua Sekte**); perintah `/sekte` dari [bagian 18](#18-sekte-dan-latih-tanding) tetap ada untuk multipemain.

> **Tentang baris "Sumber".** Setiap fitur di bagian 20–31 mengadaptasi konsep dari cerita kultivasi sungguhan, dan
> baris **Sumber** menyebut karya serta konsep yang diadaptasi. Angka, resep, tampilan, dan aturan keamanan adalah
> desain game, bukan kanon cerita. Di dalam game, arahkan kursor ke baris di gulungan aula, tab murid, atau tooltip
> item untuk membaca sumbernya.

**Sumber:**
* *Battle Through the Heavens*: monumen batu penguji Dou Qi milik klan Xiao membaca bakat setiap pemuda di depan umum.
  Prasasti aula adalah batu penguji itu.
* *A Record of a Mortal's Journey to Immortality*, bab 153: Aula Yue Lu, kediaman di Gunung Dukun Agung yang
  diselubungi formasi mantra, menyimpan alat sihir, resep pil, dan manual teknik serta memasok bahan. Dari sinilah aula
  dan paviliunnya.
* *I Shall Seal the Heavens*, bab 36–38: Meng Hao belajar semusim penuh di Paviliun Sihir (syarat paviliun).
* *Return of the Mount Hua Sect*: sekte yang runtuh dan tak dikenal meraih kembali ketenarannya lewat kemenangan, lalu
  para pendaftar kembali (ketenaran).
* *Perfect World*, bab 551/558: Shi Hao bersumpah membangun kembali Paviliun Penambal Langit yang runtuh ("bangun
  kembali untuk naik tingkat").

### 20.1 Prasasti Aula Sekte

Resep berbentuk; terbuka di buku resep begitu Batu Magnet masuk ke inventarismu:

```
G B G     G = Batangan Emas      B = Rak Buku
A L A     A = Blok Kecubung      L = Batu Magnet (lodestone)
G B G
```

* Kekerasan 3,5, tahan ledakan 1200, tidak bisa didorong piston, memancarkan cahaya 7. Tambang dengan beliung apa pun
  dan prasasti jatuh sebagai item (dengan tangan kosong tidak jatuh).
* Bentuknya loh giok bertudung emas di atas kaki batu, menghadap pemasangnya.
* Prasasti adalah blok biasa yang **kamu sendiri** pasang dan tambang. Mod tidak pernah memasang atau merusaknya.

### 20.2 Mendirikan, memindahkan, dan membangunkan aula

* **Mendirikan:** pasang prasasti saat kamu belum punya aula. Pilar giok naik dan lonceng berdentang, chat menulis
  "Aula Sekte berdiri! Sekte <nama> membuka gerbangnya.", dan kamu mendapat pencapaian *Gerbang Sekte Terbuka*. Nama
  awalnya "Sekte <namamu>"; ganti di tablet Aula (*Ganti Nama*) atau dengan `/kultivasi aula nama <nama>`.
* **Membuka gulungan aula:** klik kanan prasasti aktifmu.
* **Prasasti kedua** (saat aulamu sudah berdiri) tetap **diam**. Jongkok lalu gunakan dengan tangan kosong: "Pindahkan
  Aula ke sini?"; ulangi dalam 10 detik untuk memastikan. Paling sering sekali per hari game. Ketenaran, kas, lumbung,
  calon, dan regu yang sedang pergi ikut pindah.
* Prasasti dalam **128 blok** dari aula pemain lain juga tetap diam.
* **Tertidur:** bila prasasti hilang (ditambang, atau ketahuan hilang oleh pemeriksaan tiap 30 detik), aula tertidur.
  **Tidak ada yang dihapus**: calon pergi, tugas berhenti, dan regu yang pulang muncul di sebelahmu, bukan di aula.
  Pasang prasasti lagi di mana pun untuk membangunkannya dengan seluruh datanya. Ketenaran hanya bisa diraih, jadi
  memasang ulang tidak memberi apa-apa.
* **Teman satu `/sekte`** boleh membuka gulungan aulamu untuk melihat saja (termasuk Tugas, Misi, Arena, dan Segel
  Merah) dan menyumbang ke kas lewat Kantor Misi.

### 20.3 Tingkat Aula dan bangunan

**Tingkat Aula** (1–9) adalah yang lebih rendah antara tingkat yang diizinkan **ketenaran** dan batas yang diizinkan
**bangunan**.

| Tingkat | Ketenaran | Calon per Penerimaan | Batas murid | Radius wilayah | Harga paviliun | Pos tugas | Misi per hari |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0 | 2 | 5 | 24 | ×1,00 | 1 | 3 |
| 2 | 100 | 2 | 6 | 28 | ×1,00 | 1 | 3 |
| 3 | 300 | 3 | 6 | 32 | ×0,95 | 2 | 3 |
| 4 | 700 | 3 | 7 | 36 | ×0,95 | 2 | 4 |
| 5 | 1.500 | 3 | 7 | 40 | ×0,90 | 2 | 4 |
| 6 | 3.000 | 4 | 8 | 44 | ×0,90 | 3 | 4 |
| 7 | 6.000 | 4 | 8 | 48 | ×0,85 | 3 | 4 |
| 8 | 12.000 | 4 | 9 | 52 | ×0,85 | 3 | 5 |
| 9 | 25.000 | 5 | 9 | 56 | ×0,80 | 4 | 5 |

Rumus (dibulatkan ke bawah): calon `2 + tingkat/3`, batas murid `5 + tingkat/2`, radius `20 + 4 × tingkat`, harga
`1 − 0,05 × ((tingkat − 1)/2)`, pos tugas `1 + tingkat/3`. Tanpa aula batas murid tetap 5.

**Bangunan** kamu bangun sendiri dari blok vanilla; mod hanya *membaca* blok.

| Bangunan | Syarat | Mengizinkan sampai | Sumber |
|---|---|---|---|
| (prasasti saja) | – | Tingkat 2 | – |
| **Paviliun Kitab** | ≥ 10 Rak Buku atau Rak Buku Berukir + 1 Podium dalam 16 blok | Tingkat 4 | Aula Yue Lu (*A Record of a Mortal's Journey* bab 153); Paviliun Sihir (*I Shall Seal the Heavens* bab 36–38) |
| **Formasi Agung** | Formasi Pelindung aktif milikmu dengan Batu Magnet dalam 24 blok dari prasasti | Tingkat 6 | formasi mantra yang menyelubungi Aula Yue Lu |
| **Aula Latihan** | ≥ 4 Blok Sasaran dalam 16 blok | Tingkat 8 | lapangan latihan Gunung Hua (*Return of the Mount Hua Sect*) |
| **Menara Langit Membara** | ≥ 3 ruang menara dalam 32 blok ([25.7](#257-menara-langit-membara)) | Tingkat 9 | *Battle Through the Heavens* bab 491, 549, 622 |

Pemindaian: kotak ±16 blok mendatar (seluruh 33 × 33) dan dari 2 blok di bawah sampai 6 di atas prasasti (ruang menara
±32 blok), dibaca satu lapisan per tick selama 9 tick, setiap 30 detik selama chunk aula termuat dan setiap kali
gulungan dibuka. Hasilnya tampil sebagai daftar centang **Bangunan** di tablet Aula.

### 20.4 Ketenaran

Ketenaran **tidak pernah turun** dan hanya datang dari murid-muridmu sendiri. Setiap tambahan tercatat di tablet
Catatan.

| Peristiwa | Ketenaran |
|---|---:|
| Terobosan murid | 10 × nomor ranah tujuan |
| Bencana murid terlewati ([28](#28-bintang-abadi)) | 100 |
| Setiap bintang ke-9 seorang murid | 50 |
| Turnamen juara 1 / 2 / 3 | 200 / 100 / 50 (Turnamen Elit ×2,5) |
| Ekspedisi berhasil | 10 × tingkat misi |
| Selamat dari Tanah Terlarang | 30 per murid |
| Bos tumbang dan seorang muridmu memberi ≥ 20% kerusakannya | 100 (sekali per jenis bos per hari) |
| Seni yang hilang dipulihkan | 500 |

Saat aula mencapai **Tingkat 5**, seluruh server diberi tahu "Sekte <nama> telah kembali!" (pencapaian *Sekte Telah
Kembali*; tooltip-nya menyebut Gunung Hua). Tingkat 9 memberi pencapaian **Sekte di Bawah Langit**.

### 20.5 Wilayah sekte

Wilayah = jarak mendatar ≤ radius tingkat aula, beda tinggi ≤ 32 blok, di dimensi yang sama dengan prasasti. Wilayah
dipakai oleh calon, tugas, acara, aura Tetua, perintah Jaga Aula, Regenerasi Pelindung Sekte, dan pemurnian Pusaka
Natal.

### 20.6 Gulungan aula

Klik kanan prasasti aktifmu. Loh giok di sisi kiri adalah tablet-tabletnya; tekan angka **1–9** untuk berpindah.

| Tablet | Isi |
|---|---|
| **Aula** | Nama (*Ganti Nama*), segel tingkat, bar ketenaran, angka (batas murid, radius, harga, pos, misi, kas), daftar centang Bangunan dengan petunjuk dan sumbernya, baris Formasi Agung ("Dijaga: <nama>"), jadwal (Hari Penerimaan, Perang Seratus Dinasti, Kompetisi, Tanah Terlarang, Bahtera Purba), dan murid yang sedang pergi |
| **Calon** | Kartu calon murid, koin *Hanya Monster*, tombol *Mulai Perang* pada hari perang ([bagian 21](#21-hari-penerimaan-murid)) |
| **Jenjang** | Pagoda enam lantai ([23.1](#231-jenjang-sekte)) |
| **Perbendaharaan** | Kantor Misi, Paviliun Yue Lu, Seni Sekte yang Hilang ([23.2–23.4](#232-poin-kontribusi)) |
| **Tugas** | Pos tugas, Lumbung Sekte, Murnikan Herba ([24.1](#241-tugas-sekte)) |
| **Misi** | Bagian Papan, Tim, Utusan, Terlarang, Bahtera ([24.3–24.6](#243-ekspedisi-misi)) |
| **Arena** | Bagian Turnamen, Alam Hampa, Menara, Perang ([bagian 25](#25-acara-sekte-turnamen-perang-alam-dewa-hampa-menara)) |
| **Catatan** | 50 baris catatan sekte terbaru (terbaru di atas), bercap "Hari N" atau "Hari ini", bisa digulir |
| **Segel Merah** | Mode Kehancuran; hanya muncul bila server mengizinkannya ([bagian 31](#31-mode-kehancuran-peringatan)) |

Di tablet Misi, Arena, dan tab Jalan murid, bagian dipilih dengan pemilih "‹ bagian ›": klik separuh kiri untuk bagian
sebelumnya, separuh kanan untuk berikutnya.

### 20.7 Formasi Agung Sekte

**Sumber:**
* *A Record of a Mortal's Journey to Immortality*, bab 153: Aula Yue Lu seluruhnya diselubungi formasi mantra.
* *Renegade Immortal*, bab 216: Wang Lin memasang larangan tersembunyi agar tak seorang pun bisa masuk selama ia
  berkultivasi (lapis larangan yang bertumpuk).
* *Soul Land*: Sekte Ubin Kaca Tujuh Harta dijaga dua pelindung Douluo Bergelar, Douluo Pedang dan Douluo Tulang
  (jabatan Pelindung Sekte).

[Formasi Pelindung](#17-formasi-pelindung) milikmu yang Batu Magnetnya berada dalam 24 blok dari prasasti aktif menjadi
**Formasi Agung Sekte**:

* **Radius** `min(32, 16 + 2 × Tingkat Aula)`.
* **Penjaga Formasi:** murid Pembangunan Fondasi ke atas dengan perintah Tinggal atau Meditasi dalam 3 blok dari Batu
  Magnet menjaga formasi tetap menyala **tanpa menghabiskan daya kecubung** selama ia di sana. Tablet Aula menulis
  "Dijaga: <nama>".
* **Lapis Larangan:** 1 + satu per Tetua atau Tetua Agung di dalam wilayah + satu bila Penjaga Formasi berjenjang Dalam
  ke atas, paling banyak 4.

| Lapis | Efek pada monster di dalamnya |
|---|---|
| 1 | Didorong keluar + 1 kerusakan per detik (seperti Formasi Pelindung biasa) |
| 2 | + Kelambatan II |
| 3 | + Kelambatan V selama 3 detik saat melewati tepinya |
| 4 | Kerusakan 2 per detik; monster di dalamnya diberi garis emas-merah yang hanya terlihat olehmu |

* Setiap **Pelindung Sekte** di dalam wilayah mengalikan kerusakan formasi ×1,5 (paling banyak 2 kali).
* Warna lapis dan garis monster hanyalah partikel di komputermu (bukan efek Glowing). Tidak ada blok yang berubah.

---

## 21. Hari Penerimaan Murid

Kini murid bergabung lewat aula. Para calon datang ke prasastimu, kamu menguji mereka, lalu memilih siapa yang
diterima.

**Sumber:**
* *A Record of a Mortal's Journey to Immortality*, bab 1–5: Sekte Tujuh Misteri mengadakan ujian sekali setiap 5 tahun.
  Pendakiannya tiga tahap: hutan bambu, lereng runtuh, dan tali ke Tebing Pemurnian Tulang. Han Li terlambat dari batas
  tengah hari, tetapi tekadnya membuatnya diterima sebagai Murid Tidak Resmi yang diuji ulang setengah tahun kemudian.
* *Renegade Immortal*, bab 3–29: Wang Lin gagal dalam ujian roh dan hanya masuk sebagai murid kehormatan (berjubah
  abu-abu). "Murid Tercatat" adalah nama mod untuk jenjang itu.
* *Battle Through the Heavens*: Akademi Jia Nan mengirim tutor untuk menguji dan merekrut pemuda, dan batu penguji
  membaca Dou Zhi Li dalam 1–9 Duan.
* *Tales of Demons and Gods*, bab 13–14: tujuh warna alam jiwa, merah paling rendah, biru langit dan nila nyaris
  legendaris.
* *Soul Land*: Aula Roh membangkitkan anak-anak desa setiap tahun sesuai jadwal, dan Shrek hanya menerima "monster".
* *I Shall Seal the Heavens*, bab 19: pemuda dengan bakat terpendam diterima sebagai pelayan.
* *Return of the Mount Hua Sect*: pemuda berbakat menghindari sekte yang telah runtuh.

### 21.1 Jadwal kedatangan

* Hari Penerimaan datang setiap `recruitmentIntervalDays` hari (bawaan **3**), dihitung dari hari aula didirikan,
  ditambah fajar pertama setelah aula berdiri.
* Saat fajar, calon datang bila aula aktif, chunk-nya termuat, dan kamu daring dalam **128 blok** dari prasasti. Bila
  belum, kedatangan menunggu sampai syarat itu terpenuhi, tetapi hanya pada hari yang sama: hari yang terlewat tidak
  menumpuk.
* **Jumlah:** jatah tingkat aula (2–5), +1 bila ketenaran ≥ 3.000 ("nama sekte kembali terdengar"), paling banyak 6.
* Mereka keluar dari kepulan kabut pengembara di tanah aman 8–14 blok dari prasasti (tidak pernah di dalam blok atau
  cairan). Lonceng jauh berdentang, kamu mendapat pesan chat, judul "Hari Penerimaan Murid", dan notifikasi.
* Calon tinggal sampai fajar berikutnya. Malam hari mereka duduk dan terkantuk-kantuk; saat fajar mereka membungkuk
  lalu pergi.
* Setiap **Hari Penerimaan keempat** pada Tingkat Aula 3 ke atas menjadi **Perang Seratus Dinasti**: delapan pemuda
  datang untuk bertarung ([25.4](#254-perang-seratus-dinasti)).
* Calon tidak bisa dilukai (kecuali `/kill`), tidak diserang siapa pun, dan tidak menyerang. Raganya hanya tampilan
  dari catatan di aula: dimunculkan lagi saat kamu dalam 64 blok dan tidak pernah disimpan sebagai makhluk. Paling
  banyak 6 raga calon sekaligus (8 pada hari perang); calon tambahan menunggu tempat kosong.

### 21.2 Kartu calon (tablet Calon)

Setiap calon tampil sebagai kartu gulungan: wajah, nama, umur 12–16, bar **Tekad** (20–100, selalu terlihat), segel
vonis, tombol talisman, hitung mundur "Pergi saat fajar", dan "Penerimaan berikutnya: hari N". Akar, warna jiwa, bakat,
fisik, dan jenis istimewanya tertulis "???" sampai ia diuji. Di atas kepalanya tertulis "Calon · ???".

* **Gerbang ketenaran:** selama ketenaran < 300, calon berakar Tinggi diturunkan satu mutu; selama ketenaran < 1.000,
  calon berakar Surgawi juga diturunkan. Kartunya mencatat "Calon berbakat menghindari sekte yang belum dikenal."
* **Koin Hanya Monster:** hanya calon berakar Tinggi ke atas atau berfisik langka yang datang, dan jumlahnya berkurang
  satu (minimal 1). Sumber: *Soul Land* (Akademi Shrek "hanya menerima monster").
* **Calon Jenius** (kelas Jenius dari *Tales of Demons and Gods*): umur ≤ 14 dan warna jiwa Sian ke atas. Ditandai
  "Jenius" dan +5% peluang fisik langka.

### 21.3 Ujian Masuk

Tekan talisman **Uji (1 Serpihan Batu Kecubung)** di kartunya, atau klik kanan calon sambil memegang Serpihan Batu
Kecubung. Satu serpihan terpakai, lalu sinematik 6 detik:

1. **Detik 0–3, pendakian tiga tingkat** di samping calon (hijau bambu, abu-abu batu, emas tebing; tiga tahap pendakian
   Han Li). Tingkat yang gagal runtuh berasap. Tingkat yang didaki:
   * +1 bila mutu akar ≥ Rendah,
   * +1 bila Tekad ≥ 40,
   * +1 bila `mutu × 20 + Tekad ≥ 110` (mutu: Sampah 0, Rendah 1, Menengah 2, Tinggi 3, Surgawi 4).
2. **Detik 3–6, pilar Duan:** prasasti menaikkan pilar cahaya 1–9 segmen dalam **warna jiwa** calon, satu dentang per
   segmen. `Duan = 1 + 2 × mutu + (5 − jumlah elemen)/2 + 1 bila berfisik langka`, dibulatkan ke bawah dan dibatasi
   1–9.

Setelahnya kartu dan chat mengungkap akar, warna jiwa, Duan, bakat, fisik, dan jenis istimewanya.

### 21.4 Vonis

| Vonis | Syarat | Boleh bergabung sebagai |
|---|---|---|
| **Lulus sebelum tengah hari** | ketiga tingkat terdaki | Murid Luar, gratis |
| **Murid Tercatat** | selain itu, tetapi Tekad ≥ 70 (jalan Han Li dan Wang Lin) | Murid Tercatat, gratis; akar Sampah/Rendah juga mendapat bakat **Hati Gigih** |
| **Ditolak** | sisanya (dengan salah satu dari 4 kalimat sopan) | Murid Tercatat lewat **Jalur Pelayan** (*I Shall Seal the Heavens*), seharga 100 PK dari kas |

Calon yang belum diuji boleh diterima "buta": vonisnya dihitung saat itu juga (Ditolak tetap butuh 100 PK), dan
semuanya terungkap saat ia bergabung.

### 21.5 Menerima atau mempersilakan pergi

* **Terima:** batas murid diperiksa, lalu calon bergabung di tempatnya berdiri dengan cincin giok, lonceng, dan
  bungkukan kepadamu. Ia mulai sebagai Manusia Fana (kebangkitan seperti biasa) di jenjang sesuai vonisnya.
  Pencapaian *Hari Penerimaan Murid*, dan untuk Murid Tercatat juga *Tekad Mengalahkan Bakat*.
* **Undang pergi** (klik dua kali): calon membungkuk lalu pulang.

### 21.6 Token Kenaikan Abadi

**Sumber:** *A Record of a Mortal's Journey to Immortality*: Token Kenaikan Abadi membuat pemegangnya boleh masuk sekte
apa pun akar rohnya, dan berhak atas satu Pil Pembangunan Fondasi.

* Tidak bisa di-craft. Ditemukan di peti bawaan dunia: 6% di perpustakaan benteng (stronghold library) dan kota kuno
  (ancient city), 4% di hadiah ominous Trial Chambers.
* Klik kanan **prasasti aktifmu** dengan token: seorang calon langsung datang, sudah teruji, berakar Menengah ke atas
  dengan Tekad ≥ 60.
* Ia bergabung sebagai **Murid Luar** apa pun vonisnya, dan kamu menerima satu Pil Pembangunan Fondasi mutu Menengah.
  Pencapaian *Apa Pun Akarnya*.

### 21.7 Calon Istimewa

Paling banyak satu per kedatangan. Efeknya dijelaskan di tab Bakat murid.

| Jenis | Peluang | Sumber | Efek |
|---|---|---|---|
| **Pengemis di Gerbang** | 2% per kedatangan, sekali per aula | *Return of the Mount Hua Sect*: Santo Pedang Bunga Plum terlahir kembali sebagai bocah pengemis compang-camping, bergabung sebagai murid generasi ketiga yang rendah, dan diam-diam pendekar pedang terhebat | Kartunya menunjukkan Sampah / Merah. Saat mencapai Pembangunan Fondasi: sinematik pengungkapan, akar Surgawi, jiwa Nila, +30 pemahaman bila memilih Dao Pedang (+10 untuk Dao lain), dan Pedang Dua Puluh Empat Bunga Plum bawaan ([23.4](#234-seni-sekte-yang-hilang)). Pencapaian *Pengemis di Gerbang* |
| **Murid Regresor** | 3% | *Volcanic Age*: tetua Gunung Hua yang biasa-biasa saja dikirim kembali ke masa mudanya di ranjang kematian dengan ingatannya. *Absolute Sword Sense*: Soh Woonhwi kembali sepuluh tahun | Mengingat ranah kehidupan lalu R (Inti Emas sampai Penyatuan Dao; "???" sampai diuji). Sampai mencapai R lagi: kultivasi ×1,6, setiap terobosan minimal Sempurna, sambaran ×0,8 |
| **Dantian Tersumbat** | 4% | *Stellar Transformations*: dantian Qin Yu tidak bisa menyimpan energi, maka ia melatih tubuh dan menjadi perkasa | Hanya bisa mempelajari teknik Dao Tubuh. Serangan jarak dekat ×1,6, bonus nyawa ranah ×1,5, tanpa Penyimpangan Qi saat terobosan gagal, kultivasi dari membunuh ×1,4, meditasi qi bintang ×1,5, Kitab Abadi Tak Mati gratis |

### 21.8 Jalan lain mendatangkan calon

* **Undangan Khusus** dari Paviliun Yue Lu (150 PK, sekali sehari): satu calon tambahan datang sekarang.
* **Utus Perekrut** ([24.4](#244-utus-perekrut)): seorang murid pulang membawa 1–3 pemuda yang lebih berbakat.
* **Juara Perang Seratus Dinasti** ([25.4](#254-perang-seratus-dinasti)) bergabung gratis sebagai Murid Luar.

---

## 22. Alam jiwa, Bakat, dan Fisik Langka

Semua ini dilempar saat calon dibuat, tersembunyi sampai Ujian Masuk, dan tampil di tab **Bakat** murid.

### 22.1 Warna alam jiwa

**Sumber:** *Tales of Demons and Gods*, bab 13–14: tujuh warna alam jiwa dari merah sampai nila; Guru Shen Xiu
menyatakan alam merah membatasi kekuatan jiwa di 600 kecuali ada keberuntungan, dan Nie Li bersikeras tidak ada alam
jiwa yang cacat.

Warna jiwa menggantikan pengali mutu akar dalam kultivasi murid:

| Warna | Mutu akar | Kultivasi |
|---|---|---:|
| Merah | Sampah | ×0,6 |
| Jingga | Rendah | ×0,85 |
| Kuning | Menengah (60%) | ×1,0 |
| Hijau | Menengah (40%) | ×1,1 |
| Sian | Tinggi (65%) | ×1,3 |
| Biru Langit | Tinggi (35%) | ×1,5 |
| Nila | Surgawi | ×1,7 |

* **Batas Merah:** murid berjiwa Merah tidak bisa menerobos keluar dari **Puncak Inti Emas** ("Alam jiwa merah
  menahannya…"), dan kultivasi yang melimpahi barnya hanya tersimpan 50% sebagai Qi Tertimbun.
* **Keberuntungan** menaikkan warnanya satu tingkat: Pil Pembersih Sumsum yang diberikan (mutu akar baru mengambil warna
  dasar mutu itu, tidak pernah lebih rendah dari warnanya sekarang), atau 1% per menit saat ia bermeditasi dalam 8 blok
  darimu sementara kamu juga bermeditasi.
* Warna jiwa mewarnai nama di atas kepala dan butir auranya.

### 22.2 Bakat

Saat dibuat: 60% tanpa bakat, 32% satu, 8% dua. Tampil sebagai ubin segel di tab Bakat.

| Bakat (bobot) | Sumber | Efek |
|---|---|---|
| **Hati Gigih** (26, hanya akar Sampah/Rendah) | *A Record of a Mortal's Journey*: Han Li naik berkat ketekunan meski akarnya akar semu yang buruk | Kultivasi dari meditasi dan tugas ×1,3, peluang Fondasi +8%, sambaran tribulasi ×0,9 |
| **Darah Dewa Kuno** (2) | *Renegade Immortal*: Wang Lin mewarisi Dewa Kuno Tu Si; Dewa Kuno tumbuh lewat bintang, dan Tu Si menghadapi ujian pertamanya di bintang delapan | Bonus nyawa ranah ×1,5, serangan jarak dekat ×1,3, nyawa dari bintang ×1,5, bencana pertama baru di bintang 8 |
| **Bakat Pil** (22) | *I Shall Seal the Heavens*: bakat alkimia Meng Hao membuatnya menjadi murid Iblis Pil | Kultivasi dari pil yang diberikan ×1,3, jeda pil −30%; saat Herba Fondasi dimurnikan dan murid ini ada di wilayah, 25% peluang mutu pil naik satu langkah |
| **Jari Penempa Roh** (22) | *A Will Eternal*: bakat Bai Xiaochun dalam penempaan roh | Penempaan Pusaka Natal +15% berhasil dan +1 lipatan maksimal (19) |
| **Takut Mati** (16) | *A Will Eternal*: rasa takut mati Bai Xiaochun yang luar biasa | Mundur saat nyawa 40%, pulih ×2 selama itu, Poin Tempa Kitab Abadi Tak Mati ×2 |
| **Indra Pedang** (12) | *Absolute Sword Sense*: Soh Woonhwi mendengar senjata dan membaca gaya pemegangnya | Melawan monster yang memegang pedang: 30% menghindari pukulannya dan ×1,5 pemahaman saat membunuhnya; setelah 20 pembunuhan seperti itu, kerusakan pedang +10% (jarak dekat dan teknik Dao Pedang) |

### 22.3 Fisik Langka

Paling banyak satu. Peluang: 6%, +1% per Tingkat Aula di atas 1, +5% untuk Calon Jenius; 30% dengan akar Surgawi; 50%
untuk juara Perang Seratus Dinasti. Bobot dalam kurung. Semua efek hanya mengenai monster. Pencapaian *Terlahir
Berbeda*.

| Fisik (bobot) | Sumber | Efek |
|---|---|---|
| **Tubuh Racun Malapetaka** (14) | *Battle Through the Heavens*: Tubuh Racun Malapetaka dan Kitab Racun Tujuh Warna milik Tabib Peri Kecil; racunnya tumbuh tak terkendali sampai Pil Racun buatan Xiao Yan memberinya hidup yang normal | Kebal Racun dan Wither; pukulannya menambah Racun II 4 detik. Sejak Fondasi: aura 0,3 U per detik dalam 4 blok. Meter racun +1 per menit bertarung; di 100 "Racun Mengamuk" 60 detik (aura ×2, bahkan saat mengikutimu). Pil Penyembuh (pengganti Pil Racun) mengosongkan meternya. Sejak Transformasi Dewa: **Kabut Racun Tujuh Warna**, awan 6 blok selama 8 detik (Racun III, Wither II), jeda 45 detik |
| **Darah Naga Purba Taixu** (12) | *Battle Through the Heavens*: Zi Yan, Naga Purba Taixu muda dalam wujud manusia, melahap pil dan berubah menjadi naga | Efek pil ×1,5, jeda pil ×0,5. Sejak Jiwa Baru Lahir: **Wujud Naga** 20 detik (×1,4 lebih besar bila ada ruang di atas kepala, kerusakan +50%, kebal hentakan, kerucut api naga 6 blok sebesar 1,2 U per detik yang membakar tubuh monster 3 detik), jeda 120 detik |
| **Garis Darah Dou Di** (10) | *Battle Through the Heavens*: garis darah kuno Dou Di milik klan Xiao, yang kelak dibangkitkan Xiao Yan | +0,12 skor kualitas di setiap terobosan. Di Penyatuan Dao bangkit sekali: semua atribut +15% dan naik satu tahap |
| **Tubuh Emas Tulang Angkuh** (14) | *Martial Peak*: Tubuh Emas Tulang Angkuh Yang Kai makin kuat lewat pukulan-pukulan yang ia tahan | Tiap 10 kerusakan yang diterima = 1 tumpukan Darah Emas (maks 40, satu memudar tiap 30 detik): +1,5% kerusakan dan +0,5% pengurangan kerusakan per tumpukan. Tiap 500 kerusakan yang ditahan: +0,5 nyawa maksimal selamanya (maks +30) |
| **Pembawa Simbol Melahap** (8) | *Martial Universe*: Lin Dong membawa Simbol Leluhur Melahap | Membunuh memberi +25% kultivasi dan memulihkan 5% nyawa maksimal korban. Membuka Pusaran Melahap ([bagian 30](#30-jurus-pemusnah)) |
| **Tulang Mahatinggi** (10) | *Perfect World*: Tulang Mahatinggi bawaan Shi Hao dengan teknik tulisan tulang; bab 1996, setelah kehilangan tulangnya ia membentuk tulisan tulang yang lebih kuat lagi | Sejak Fondasi satu **Teknik Pusaka** bawaan dari unsur utama akarnya (Logam: Tebasan Qi Pedang, Kayu: Serap Kehidupan, Air/Es: Lotus Es, Api: Telapak Api, Petir: Telapak Petir, Tanah: Hentakan Gunung, Angin: Badai Angin, Ruang: Penjara Ruang), kekuatan ×1,5, tanpa slot, Dao apa pun, tak bisa diajarkan. Kultivasi dari membunuh ×1,25. Di bintang 9 tulangnya berganti: ×2,25 |
| **Pupil Ganda** (10) | *Perfect World*: Pupil Ganda Shi Yi menembakkan pedang dan berkas cahaya | 25% menghindari pukulan monster, kerusakan teknik ×1,2. Tiap 10 detik monster dalam 16 blok mendapat tanda mata yang hanya terlihat olehmu |
| **Nadi Dewa Sesat** (8) | *Against the Gods*, bab 45–47: nadi Yun Che tampak sangat biasa sampai ke-54 pintunya terbuka; bab 46: tujuh gerbang di inti nadi, tiap gerbang yang terbuka menaikkan Seni Dewa Sesat satu ranah, tergolong Berserk, dan membukanya terlalu dini bisa membunuh | Tampak seperti Sampah / Merah (tidak terkena batas Merah). Di terobosan Inti Emas terungkap: jiwa Nila dengan faktor 2,0 (pencapaian *Nadi Dewa Sesat*). **Tujuh gerbang** terbuka di Inti Emas, Jiwa Baru Lahir, Transformasi Dewa, Penyatuan Dao, lapisan 5 Kenaikan Abadi, Dewa Abadi, dan bintang 9 (Jiwa Sesat, Hati Membara, Api Penyucian, Langit Bergemuruh, Raja Neraka, Gerbang Keenam, Gerbang Ketujuh), masing-masing +15% kerusakan. **Ledakan Gerbang** (otomatis di bawah 50% nyawa melawan bos, atau tombol di tab Bakat): 20 detik kerusakan ×(1 + 0,25 × gerbang), lalu 30 detik Kelemahan dan +30% kerusakan diterima, jeda 5 menit. Gerbang 7 membuka Murka Raja Neraka |
| **Alam Jiwa Tanpa Atribut** (14) | *Tales of Demons and Gods*, bab 14: alam merah tanpa atribut milik Nie Li cocok untuk teknik Dewa Langit, lambat di awal, potensinya tertinggi, mampu memakai teknik segala atribut | Kultivasi ×0,8 sebelum Inti Emas, ×1,4 sejak Penyatuan Dao. Mempelajari dan memakai teknik semua Dao |

---

## 23. Jenjang Sekte dan Poin Kontribusi

### 23.1 Jenjang Sekte

**Sumber:**
* *Renegade Immortal*: murid kehormatan adalah jenjang lebih rendah berjubah abu-abu.
* *A Record of a Mortal's Journey*: murid tidak resmi diuji ulang setelah setengah tahun. Bab 871: gelar tetua agung
  "sepantasnya milik kultivator terkuat", ditawarkan kepada Han Li di Jiwa Baru Lahir tahap menengah.
* *I Shall Seal the Heavens*, bab 27–31: Sekte Luar dan Sekte Dalam, dengan latihan promosi khusus yang menerima satu
  murid luar.
* *Battle Through the Heavens*: Akademi Luar dan Akademi Dalam, dan Tetua Agung Su Qian yang memimpin Akademi Dalam.
* *Martial Peak*: Yang Kai adalah Murid Percobaan dan penyapu lantai yang rendah.
* *Return of the Mount Hua Sect*: generasi murid, instruktur, dan tetua, dengan Chung Myung yang terlahir kembali
  sebagai junior dan melatih para seniornya.
* *Soul Land*: dua pelindung Douluo Bergelar menjaga Sekte Ubin Kaca Tujuh Harta.

Jenjang tampil sebagai awalan nama di atas kepala ("[Dalam] Lin Feng · Inti Emas"), warna trim jubah, dan ubin jenjang di
tab Status dan Bakat. **Slot teknik** hanya membatasi pengajaran *baru*; teknik yang sudah dikuasai tetap.

| # | Jenjang | Syarat naik ke jenjang ini | Slot | Faktor kultivasi | Trim | Keistimewaan |
|---|---|---|---:|---:|---|---|
| 0 | **Murid Tercatat** | bergabung lewat tekad atau Jalur Pelayan | 2 | ×0,55 | abu-abu | hanya perintah Ikut / Meditasi plus tugas; hasil tugas ×1,2 |
| 1 | **Murid Luar** | Pemurnian Qi, 30 PK, dan **Uji Ulang**: sedikitnya setengah siklus penerimaan sejak bergabung (36.000 tick dengan pengaturan bawaan) | 3 | ×0,60 | putih | semua perintah, memimpin misi tingkat 1–2 |
| 2 | **Murid Dalam** | Pembangunan Fondasi, 150 PK, dan lulus **Latihan Khusus** ([25.6](#256-latihan-khusus)) | 5 | ×0,70 | biru | misi sampai tingkat 5, Alam Dewa Hampa, menara |
| 3 | **Murid Inti** | Inti Emas, 600 PK, dan tiga besar turnamen (gelar tetap) **atau** lantai 10 Alam Dewa Hampa | semua | ×0,80 | giok | misi semua tingkat, pemimpin (+0,05 peluang) |
| 4 | **Tetua** (1 kursi, 2 mulai Tingkat Aula 5) | Jiwa Baru Lahir, 1.500 PK | semua | ×0,90 | ungu | aura: murid yang bermeditasi dalam 16 blok mendapat kultivasi ×1,15; sekali sehari mengajarkan teknik yang ia kuasai kepada adik-adiknya, dibayar dari 30% qi-nya sendiri |
| 5 | **Tetua Agung** (1 kursi) | Jiwa Baru Lahir tahap Menengah, kekuatan tertinggi di antara murid-muridmu, dan diangkat olehmu | semua | ×1,00 | putih-emas | aura ×1,2 di seluruh wilayah, +1 lapis Formasi Agung |

**Jabatan** (terlepas dari jenjang):
* **Pewaris** ([29.1](#291-pewaris-dan-sembilan-segel)).
* **Pelindung Sekte:** paling banyak 1 (2 mulai Tingkat Aula 7); butuh Penyatuan Dao atau sedikitnya satu bintang.
  Diangkat dengan *Angkat Pelindung* di tablet Jenjang; ia beralih ke perintah **Jaga Aula**, mendapat Regenerasi II di
  dalam wilayah, dan mengalikan kerusakan Formasi Agung ×1,5. Murid yang bukan Pelindung tidak bisa diberi Jaga Aula.

**Tablet Jenjang:** gambar pagoda enam lantai dengan segel murid di setiap lantai. Klik segel untuk membuka bilah
bambu berisi daftar syarat dan tombol:
* **Naikkan (N PK):** dibayar dengan PK milik murid itu sendiri. Kilau jubah 3 detik dalam warna trim baru, lonceng, dan
  baris di chat sekte. Dari Luar ke Dalam, menekan Naikkan memulai **Latihan Khusus** (murid harus berdiri dalam 6 blok
  dari prasasti); PK baru dibayar bila lulus.
* **Turunkan** (klik dua kali): gratis, tidak ada yang dikembalikan; jenjang terendah adalah Murid Tercatat.
* **Angkat Pelindung / Lepas Pelindung.**
* **Anugerah Ketua +10 / +50 / +100 / +500:** memindahkan PK dari kas ke murid ini 1 : 1 (*Martial Peak*: murid
  menerima poin kontribusi dari sekte).

Pencapaian *Naik Pagoda* (kenaikan pertama) dan **Yang Terkuat Duduk Tertinggi** (Tetua Agung). Murid fase 5 tanpa
jenjang otomatis menjadi Murid Luar (di bawah Fondasi), Murid Dalam (Fondasi sampai Inti Emas), atau Murid Inti (Jiwa
Baru Lahir ke atas), dengan 0 PK.

### 23.2 Poin Kontribusi

**Sumber:**
* *A Will Eternal*, bab 9 dan 27: murid luar membawa tanaman roh ke Kantor Misi, seorang tetua menilainya, dan poin jasa
  dibayar sesuai kualitas. Poin jasa membeli ceramah kitab, Paviliun Seni Sihir, dan Paviliun 10.000 Obat.
* *Martial Peak*: Aula Kontribusi menukar poin kontribusi dengan herba, pil, dan resep.
* *A Record of a Mortal's Journey*, bab 153: Aula Yue Lu menyimpan manual dan resep pil serta memasok bahan.
* *Battle Through the Heavens*: Energi Api membayar waktu latihan di menara ([25.7](#257-menara-langit-membara)).

**Poin Kontribusi (PK)** ada di dua tempat: **kas sekte** (milikmu) dan PK **setiap murid**.

**Mendapatkan PK:**
* **Kas, Kantor Misi** (tablet Perbendaharaan, kiri): tumpukan barang di tasmu yang diterima tampil sebagai kartu segel
  dengan nilai PK-nya. Klik untuk mempersembahkan seluruh tumpukan; seorang tetua menilai ("Tetua menilai: kualitas
  tinggi."). Paling banyak **300 PK per hari** dari persembahan.

  | Barang | PK per buah |
  |---|---:|
  | Buni Sinar | 0,5 |
  | Serpihan Batu Kecubung, Wortel Emas, Batangan Emas | 1 |
  | Irisan Semangka Emas | 2 |
  | Zamrud | 6 |
  | Berlian | 8 |
  | Serpihan Gaung | 15 |
  | Pil Kultivator mutu Rendah / Menengah / Tinggi / Sempurna | 5 / 10 / 20 / 40 |

* **Kas, lainnya:** bagian dari misi dan acara (bagian 24–25).
* **Murid, membasmi monster:** `nyawa maksimal korban × 0,05` (dibulatkan ke bawah, minimal 1), paling banyak 120 PK per
  murid per hari.
* **Murid, lainnya:** misi, acara, dan tugas Pikul Air.
* **Anugerah Ketua:** memindahkan PK kas ke murid (tablet Jenjang).
* Teman satu `/sekte` bisa menyumbang barang ke kas aulamu lewat Kantor Misi (tercatat di Catatan).

### 23.3 Paviliun Yue Lu

Tablet Perbendaharaan, kanan. Membelanjakan PK kas; harga dikali faktor harga tingkat aula. Barang terkunci
menampilkan "Tk N" dan alasannya. Pil dibuat sebagai pil alkimia mutu Menengah.

| Barang | Harga dasar | Butuh Tingkat Aula |
|---|---:|---|
| Pil Penyembuh | 40 | – |
| Pil Pengumpul Qi | 60 | – |
| 8 Serpihan Batu Kecubung | 40 | – |
| Undangan Khusus (satu calon tambahan sekarang, sekali sehari) | 150 | – |
| Ceramah Kitab (+5 pemahaman untuk satu murid; ceramah kitab *A Will Eternal*) | 150 | – |
| Pil Pembangunan Fondasi | 250 | – |
| Utusan Siap Lagi (jeda utusan dihapus; ditolak tanpa biaya bila tidak ada utusan yang sedang beristirahat) | 300 | – |
| Gulungan Teknik (Buku Pesona yang dipetakan ke teknik kitab pilihanmu; lihat [11.3](#113-kitab-teknik-buku-pesona)) | 400 | 3 |
| Pil Penangkal Petir | 600 | 3 |
| Pil Inti Emas | 800 | 4 |
| Pil Pembersih Sumsum | 900 | 6 |
| Manual Tubuh Abadi (membuka Kitab Abadi Tak Mati untuk satu murid; [26.4](#264-kitab-abadi-tak-mati)) | 1.200 | 3 |

Talisman **"Untuk: <murid>"** memilih murid di dekatmu untuk Ceramah Kitab dan Manual Tubuh Abadi; **"Gulungan:
<teknik>"** memilih teknik untuk Gulungan Teknik. Teman satu `/sekte` hanya bisa menyumbang, tidak membeli.

### 23.4 Seni Sekte yang Hilang

**Sumber:**
* *Return of the Mount Hua Sect*: Chung Myung memulihkan Pedang Dua Puluh Empat Bunga Plum Gunung Hua yang hilang (qi
  pedang mekar sebagai bunga plum) dan Seni Ilahi Kabut Ungu.
* *Legend of the Northern Blade*: Jin Mu-won mempelajari seni Sekte Langit Utara yang telah bubar dari tulisan-tulisan
  tak lengkap peninggalan ayahnya.
* *Volcanic Age*: tetua yang kembali ke masa muda memakai teknik dari catatan tersembunyi sekte.

**Lembar Kitab yang Hilang** (tiga seni, masing-masing lima lembar). Ditemukan di peti bawaan dunia: perpustakaan
benteng 25%, rumah hutan (woodland mansion) 20%, kota kuno 15%, hadiah ominous Trial Chambers 10%, piramida gurun dan
kuil hutan 8%.

**Memulihkan:** lima lembar *berbeda* dari satu seni (dari tas) dan Paviliun Kitab yang sudah berdiri, lalu tekan
**Pulihkan** di tablet Perbendaharaan. Lembar habis, +500 ketenaran, aksara bercahaya (kelopak plum untuk seni pertama),
pencapaian *Dari Lembar yang Tercecer*. Seni berlaku di seluruh aula untuk murid **Dalam ke atas**:

| Seni | Efek |
|---|---|
| **Pedang Dua Puluh Empat Bunga Plum** | Setiap pukulan jarak dekat atau teknik Pedang ke-4 melepaskan badai kelopak (radius 4, 1,2 U). Murid Inti: setiap pukulan ke-24 adalah **Mekar Penuh** (24 kelopak, radius 6, 2 U) |
| **Seni Ilahi Kabut Ungu** | Pemulihan qi ×1,3, qi maksimal +20%, kabut ungu saat bermeditasi |
| **Warisan Sekte Langit Utara** | Kerusakan +20% di malam hari dan di bioma bersalju |

---

## 24. Tugas, Lumbung, dan Misi

### 24.1 Tugas Sekte

**Sumber:**
* *Renegade Immortal*: tugas Wang Lin memikul sedikitnya sepuluh tong air sehari.
* *A Record of a Mortal's Journey*, bab 150–151: Han Li mengambil tugas Kebun Seratus Obat yang tak diminati, diawasi
  kultivator Fondasi bernama Ma, dan diam-diam mematangkan herba di sana.
* *A Will Eternal*, bab 2 dan 20–26: Bai Xiaochun memulai di Dapur Api, memasak nasi roh di wajan berukir formasi api
  bumi. Ia mencuri Ayam Ekor Roh, yang ekornya terbakar menjadi api tiga warna untuk penempaan roh, lalu malah
  mengusulkan sendiri "satgas pemburu pencuri ayam". (Warna api itu milik penempaan roh, jadi dapur tidak memakainya.)

**Pos tugas** (1–4 sesuai tingkat aula) berdiri 5 blok di utara, timur, selatan, dan barat prasasti. Di tablet
**Tugas**, pilih murid di kiri lalu beri tugas. Murid harus senggang dan dalam 64 blok dari prasasti. Ia berjalan ke pos
kosong pertama, menjaga tempat itu, dan memutar pose (memikul ember, berlutut, mengaduk, menabur pakan). **Tidak ada
blok atau tanaman yang disentuh.** *Bebaskan dari Tugas* melepasnya; pos yang dipegang murid yang gugur, dilupakan,
atau dilepaskan dibebaskan sendiri.

Hasil dihitung dari waktu game yang berlalu setiap 60 detik selama chunk aula termuat dan saat gulungan dibuka; paling
banyak 24.000 tick (satu hari game) per pos yang tertabung. Murid Tercatat menghasilkan ×1,2.

| Tugas | Hasil |
|---|---|
| **Pikul Air** | +0,8 kultivasi per menit; **Tubuh Terlatih** +1 nyawa maksimal permanen tiap 60 menit (maks +10); +10 PK murid tiap 50 menit ("sepuluh tong"); +1 Poin Tempa per menit (Kitab Abadi Tak Mati) |
| **Kebun Seratus Obat** | 1 bahan tiap 4 menit (Buni Sinar, Wortel Emas, Irisan Semangka Emas, atau Beri Manis, peluang sama); ×1,5 dengan akar Kayu atau Dao Kehidupan; ×1,25 pada Tingkat Aula 5+ |
| **Dapur Api** | 1 Hidangan Roh tiap 6 menit; ×1,5 dengan akar Api atau Dao Api |
| **Peternakan Ayam Ekor Roh** | butuh ≥ 4 ayam dalam 16 blok dari prasasti (hanya dihitung); 1 Bulu Ekor Roh tiap 10 menit |

### 24.2 Lumbung Sekte, Hidangan Roh, dan Murnikan Herba

* **Lumbung Sekte:** gudang maya 27 slot di tablet Tugas. Hanya bisa diambil: klik slot untuk memindahkan isinya ke
  inventarismu. Bila penuh, kelebihan hilang dengan peringatan.
* **Hidangan Roh** (makanan: nutrisi 8, saturasi 1,0): dimakan sendiri memberi +8 kultivasi dan +10 qi. Diberikan
  kepada murid (klik kanan): kultivasinya ×1,2 selama 2 menit.
* **Murnikan Herba** (tablet Tugas): setiap 3 Herba Fondasi (dari lumbung dulu, lalu inventarismu) menjadi 1 Pil
  Pembangunan Fondasi. Mutu: Rendah 30% · Menengah 45% · Tinggi 20% · Sempurna 5%. Sisa herba tetap.
* **Telur paskah:** murid Takut Mati yang bertugas punya 5% peluang sehari memakan sampai 3 Bulu Ekor Roh dari lumbung.
  Chat "Siapa pencuri ayam roh?!" dan pencapaian tersembunyi.

### 24.3 Ekspedisi (Misi)

**Sumber:**
* *Battle Through the Heavens*: Xiao Yan berlatih di Pegunungan Binatang Ajaib dan memburu inti sihir.
* *Soul Land*: para master roh masuk ke Hutan Besar Bintang Dou dan Hutan Pemburu Roh mencari binatang berumur tertentu,
  biasanya dikawal.
* *Coiling Dragon*, Buku 19: di Medan Perang Antarbidang, lencana dari pembunuhan dihitung sebagai jasa yang ditukar di
  sebuah prasasti (model hadiah misi).

**Papan Misi** (tablet Misi, bagian Papan) berganti setiap fajar dengan jumlah misi sesuai tingkat aula.
T* = `1 + nomor ranah murid terkuat` (1–9); misi memakai tingkat T*−1, T*, dan T*+1 dalam rentang tiap jenis.

| Misi | Tingkat | Lama | Syarat |
|---|---|---|---|
| Patroli Wilayah | 1–3 | 12.000 tick | Luar+ |
| Mencari Herba Roh | 1–5 | 12.000 tick | Luar+ |
| Mengawal Karavan | 2–6 | 24.000 tick | Luar+; tingkat ≥ 3 butuh Dalam+ |
| Perburuan Binatang Iblis | 3–8 | 24.000 tick | Dalam+ |
| Alam Rahasia | 6–9 | 48.000 tick | pemimpin Inti+ di ranah Jiwa Baru Lahir ke atas |

Pemimpin menentukan tingkat tertinggi: Murid Luar memimpin tingkat 1–2, Murid Dalam sampai 5, Murid Inti ke atas semua
tingkat.

**Berangkat:** pilih misi dan 1–3 murid, tekan **Berangkat**. Mereka pergi dengan awan langkah dan pilar cahaya,
menjadi data murni (tidak ada yang berjalan di dunia), dan waktunya tetap berjalan meski kamu luring. Mereka tetap
dihitung dalam batas murid; daftar murid menandai mereka "sedang pergi · sisa N hari".

**Hasil** dihitung saat waktunya habis (tick aula, saat gulungan dibuka, atau saat kamu masuk dunia):
* Kekuatan anggota `m = nomor ranah + tahap/4 + tingkat/9 + bintang/9`; tim `P = m terbesar + 0,5 × jumlah m yang lain`.
* Kesulitan `d = tingkat misi` (+ bintang anggota terkuat / 9 pada tingkat 9).
* **Peluang berhasil** = `0,45 + 0,12 × (P − (d − 1))`, +0,05 bila ada anggota Inti+, dibatasi 10%–95%.

| Hasil | Tiap anggota | Aula |
|---|---|---|
| **Berhasil** | 15% bar (tingkat 1–3), 25% (4–6), 35% (7–9), +10% untuk Alam Rahasia; 10 × tingkat PK | kas +5 × tingkat; ketenaran +10 × tingkat; rampasan ke lumbung |
| **Gagal** | pulang dengan 30% nyawa, Penyimpangan Qi 3 menit, separuh bagian bar; tanpa PK atau rampasan; **tidak pernah mati** | – |

"Bar" berarti bar kultivasi saat ini, atau harga bintang berikutnya setelah Puncak Dewa Abadi.

**Rampasan menurut tingkat tertinggi:** tingkat 1–2: 2–6 tulang, benang, atau batangan besi; 3–4: 2–5 emas, zamrud, atau
lapis lazuli; 5–6: 1–3 berlian, 2–4 mutiara ender, 1 apel emas; 7–8: 1–2 serpihan gaung, 10% rongsokan netherite, 15%
Buku Pesona yang dipetakan ke teknik kitab; 9: seperti 7–8 plus 1% bintang Nether. Mulai tingkat 4: 20% satu Pil
Pengumpul Qi. Mencari Herba Roh menggandakan bahan kebun.

**Panggil Pulang** (bagian Tim) memanggil tim saat itu juga tanpa hasil apa pun. Pencapaian *Melangkah Keluar Gerbang*
dan *Alam Rahasia Ditaklukkan*.

### 24.4 Utus Perekrut

**Sumber:** *I Shall Seal the Heavens*, bab 19: setiap bulan April murid Sekte Luar di atas tingkat kelima pergi, dan
masing-masing membawa pulang dua atau tiga pemuda berbakat.

* Bagian Utusan di tablet Misi. Seorang murid Luar+ di Pemurnian Qi tahap Menengah ke atas pergi selama 24.000 tick.
* Sekali tiap 5 hari game per aula. **Percepat (300 PK)** dari kas, atau *Utusan Siap Lagi* di Paviliun, membuka kuncinya.
* Ia pulang membawa 1 (30%), 2 (55%), atau 3 (15%) calon. Masing-masing punya 30% peluang mutu akar +1, 50% berbagi unsur
  utama sang utusan, dan peluang bakat 50% (bukan 40%).
* Sang utusan mendapat 40 PK dan 10% bar.
* Murid yang sedang sibuk (bertugas, berlatih, menerobos) ditolak.

### 24.5 Tanah Terlarang Berwarna Darah

**Sumber:** *A Record of a Mortal's Journey to Immortality*, bab 155–160: larangan angin kuno melemah selama lima hari
sekali setiap lima tahun; larangan kedua hanya mengizinkan murid Kondensasi Qi; tanah itu memasok herba utama pil
Fondasi, kurang dari sepertiga yang kembali, dan Han Li masuk untuk memurnikan pilnya sendiri.

* Setiap `forbiddenGroundIntervalDays` hari (bawaan **15**) pada Tingkat Aula 2 ke atas: judul "Segel angin melemah!"
  dan angin merah darah di sekitar prasasti selama satu hari.
* Sampai **3 murid Pemurnian Qi saja** masuk lewat bagian Terlarang di tablet Misi. Yang lebih tinggi ditolak
  ("Pembatasan kedua menolak Fondasi ke atas."). Mereka pergi 6.000 tick ("lima hari").
* **Peluang selamat** tiap murid = `0,30 + 0,08 × tahap + 0,05 × mutu akar + 0,05 × min(3, teknik serangan yang
  dikuasai) + 0,10 bila tidak sendirian`, paling banyak 85%.

| Hasil | Efek |
|---|---|
| **Selamat** | 2–5 Herba Fondasi ke lumbung, 25% bar, 40 PK, ketenaran +30 |
| **Gagal** | "Terluka Parah": Penyimpangan Qi 10 menit, isi bar saat itu berkurang 30%, 30% nyawa; **tidak pernah mati**. Hanya bila Hardcore-mu menyala: yang kembali adalah Papan Jiwanya, di lumbung (bisa dibangkitkan seperti biasa; tempatnya di batas murid langsung kosong) |

Laporan muncul di chat dan di bagian Terlarang. Pencapaian *Keluar dari Angin Merah Darah*. Herbanya dimurnikan di
tablet Tugas ([24.2](#242-lumbung-sekte-hidangan-roh-dan-murnikan-herba)).

### 24.6 Tulang Kunpeng dan Bahtera Purba

**Sumber:**
* *Perfect World*, bab 258–347: tulang purba Kunpeng terbelah enam bagian yang hanya memberi sekitar sepertiga
  warisannya; teknik sejatinya ada di Gua Pemurnian Iblis dan butuh ranah lebih tinggi untuk dipakai sepenuhnya.
* *Against the Gods*, bab 454: Bahtera Mendalam Purba terbuka hanya satu hari tepat setelah turnamen Tujuh Negara.
  Aturan naiknya di sini adalah desain game.

* **Pecahan Tulang Kunpeng** (bagian 1–6): setiap misi Alam Rahasia yang berhasil punya 8% peluang membawanya. Enam
  bagian *berbeda* (di lumbung atau inventaris) → bagian Bahtera di tablet Misi → pilih murid Inti+ → **Satukan**: ia
  mempelajari **Sayap Kunpeng** ([26.6](#266-sayap-kunpeng)).
* **Bahtera Purba:** selama satu hari game setelah setiap **Turnamen Elit**, bahtera hantu dari partikel melayang 18
  blok di atas aula (tanpa blok). Hanya tiga besar turnamen itu yang boleh **Naiki Bahtera**, sekali, selama 24.000
  tick. Hadiah pasti: 1 Buku Pesona teknik, 1 bagian tulang Kunpeng yang belum dimiliki aula (acak bila lengkap), dan
  60% bar. Pencapaian **Penumpang Bahtera Purba**.

---

## 25. Acara sekte: turnamen, perang, Alam Dewa Hampa, menara

### 25.1 Aturan duel

Semua laga sekte (turnamen, Perang Seratus Dinasti, Alam Dewa Hampa, Tanding dengan Guru) memakai aturan duel yang sama:
* Satu laga dalam satu waktu, di dalam cincin partikel radius 9 di prasasti. Berada di luar radius 11 selama 3 detik
  berarti kalah.
* Laga berakhir pada **10% nyawa** (pukulan itu ditahan, tidak pernah mematikan) atau setelah 60 detik; saat waktu habis
  yang sisa nyawanya lebih besar menang.
* Teknik yang diajarkan boleh dipakai, tetapi hanya mengenai lawan duel. Yang lebih lemah mendapat +15% kerusakan per
  ranah di bawah lawannya (maks +45%).
* Dua boss bar vanilla menunjukkan pasangan dan nyawanya, dan satu percikan per pukulan. Murid lain duduk menonton.
* Setelah laga, api, beku, Wither, racun, dan kurungan pada keduanya dihapus, dan selama 5 detik bekas lawan dan bahaya
  lingkungan tetap tidak bisa menurunkan nyawa di bawah 10%.

### 25.2 Kompetisi Daftar Kuat

**Sumber:**
* *A Will Eternal*: pertarungan Terpilih Sekte Luar; tepi utara merebut sebagian besar sepuluh besar, dan para pemenang
  berhak atas Eselon Warisan.
* *Battle Through the Heavens*, bab 566–573: kompetisi Daftar Kuat dengan babak gugur sampai sepuluh besar.
* *I Shall Seal the Heavens*, bab 27–31: latihan promosi berakhir dengan laga final yang diumumkan Tetua Agung.
* *Against the Gods*, bab 434–436 dan 1359: turnamen peringkat Angin Biru dan Tujuh Negara melawan para jenius negeri
  saingan.
* *Soul Land*: Tujuh Monster Shrek memenangkan Kompetisi Elit Benua dan mengharumkan nama Shrek.
* *Return of the Mount Hua Sect*: kemenangan atas Zhongnan menyebarkan nama Gunung Hua.

* **Jadwal:** setiap `tournamentIntervalDays` hari (bawaan **7**) pada tengah hari, atau **Mulai Sekarang** di tablet
  Arena (paling sering tiap 3 hari). **Lewati Minggu Ini** melewatinya.
* **Syarat:** sedikitnya 3 murid Luar+ di dalam wilayah dan kamu dalam 32 blok dari prasasti; bila belum, kompetisi
  menunggu tengah hari berikutnya.
* **Bagan:** sampai 8 muridmu (yang terkuat dulu; klik nama di daftar peserta untuk mencoretnya), sistem gugur dengan
  unggulan dan laga perebutan juara ketiga, maju setiap 3 detik. Slot kosong sampai pangkat dua berikutnya diisi
  **Murid Tepi Utara** (saingan dari 24 nama, ranah sekitar median peserta ±1 tahap, tidak pernah disimpan). Mereka
  memukul dan memakai dua jurus: garis qi 12 blok sebesar 1 U tiap 6 detik dan ledakan unsur radius 3 sebesar 1 U tiap
  10 detik, hanya pada lawan duelnya.

| Tempat | Hadiah |
|---|---|
| Juara 1 | 300 PK, 40% bar, gelar **Nomor Satu Daftar Kuat** 7 hari (nama emas, kerusakan teknik +10%) |
| Juara 2 | 150 PK, 25% bar |
| Juara 3 | 75 PK, 15% bar |
| Setiap peserta | 20 PK, 5% bar, +2 pemahaman, +3 ikatan per laga yang dimenangkan |

Tiga besar juga mendapat gelar tetap "tiga besar" yang membuka syarat Murid Inti. **Daftar Kuat** sepanjang masa
(sepuluh besar) tertulis di bilah bambu di tablet Arena. Pencapaian *Nomor Satu Daftar Kuat*.

### 25.3 Turnamen Elit

Setiap kompetisi **ketiga** adalah Turnamen Elit: tiga jagoan sekte saingan datang (setara peserta terbaikmu −1 tahap,
sama, dan +1 tahap). Ketenaran ×2,5, juaranya juga mendapat gelar **Juara Peringkat**, dan setelahnya **Bahtera Purba**
muncul ([24.6](#246-tulang-kunpeng-dan-bahtera-purba)).

### 25.4 Perang Seratus Dinasti

**Sumber:**
* *Martial Universe*: Perang Seratus Dinasti mempertemukan pemuda dari seratus dinasti; sang juara, Lin Dong, diterima
  di Sekte Dao.
* *Battle Through the Heavens*: Akademi Jia Nan hanya menerima pemuda terbaik suatu wilayah.

* Menggantikan setiap Hari Penerimaan keempat pada Tingkat Aula 3 ke atas: **8 pemuda** datang (chat merah "Hari ini
  Perang Seratus Dinasti"), bukan calon biasa.
* Tekan **Mulai Perang** di tablet Calon (atau bagian Perang di tablet Arena) kapan saja hari itu. Bagan gugur 4 + 2 + 1
  di antara para calon: laga 30 detik, akhir di 10% nyawa, kekuatan dasar, hiasan partikel.
* **Juara:** mutu akar +1 (maks Surgawi), 50% peluang fisik langka, Tekad 100. Ia boleh diterima **gratis sebagai Murid
  Luar** di Pemurnian Qi Awal dengan ikatan 20 (kartu berlabel "Juara"). Calon lain tetap bisa diterima seperti biasa
  sampai fajar. Bagan bilah bambu tampil di bagian Perang. Pencapaian *Juara Seratus Dinasti*.

### 25.5 Alam Dewa Hampa

**Sumber:**
* *Perfect World*, bab 339–345: kultivator bertarung dalam wujud roh di Alam Dewa Hampa; luka terbawa ke tubuh asli
  sebagai efek samping; lantai terbawah adalah Tanah Permulaan dengan tangga batu panjang ke atas. Juga Gua Pemurnian
  Iblis (bab 261).
* *Tales of Demons and Gods*, bab 48: Batas Suci Surgawi, yang dilewati hanya tiga orang dalam berabad-abad, menandai
  bakat paling cemerlang Kota Kemuliaan.

* **Masuk:** tablet Arena, bagian Alam Hampa → pilih murid **Dalam ke atas** → **Masuk Alam Hampa**. Ia duduk di prasasti
  dengan tubuh roh yang tembus pandang; **bayangan** gelap bermata merah muncul 4 blok darinya di cincin 8 blok.
* **Bayangan lantai f:** nyawa `20 × 1,12^f`, kerusakan `3 × 1,10^f`, zirah `min(20, f/3)`; nyawa di atas 1.024 menjadi
  pengurangan kerusakan. Mulai lantai 10 ia memakai jurus saingan tiap 8 detik.
* **Pendakian:** satu per murid per hari game, sampai 5 lantai berturut-turut dengan jeda 3 detik dan pemulihan 25% di
  antaranya. Satu bayangan per aula dalam satu waktu; pendakian lain mengantre. Pendakian yang menaklukkan sedikitnya
  satu lantai menghabiskan jatah hari itu.

| Lantai | Nama |
|---|---|
| 1–9 | Tanah Permulaan |
| 10–19 | Tangga Batu |
| 20–29 | Gerbang Awan |
| 30–39 | Istana Hampa |
| 40–49 | Puncak Hampa |
| 50 | Batas Suci |
| 51–59 | Di Atas Batas |
| 60 | Gua Pemurnian Iblis |
| 61+ | Hampa Tanpa Dasar N |

* **Hadiah:** penaklukan pertama lantai f memberi `4% × (1 + f/20)` bar; setiap penaklukan pertama ke-10 +2 pemahaman;
  penaklukan ulang 25% dari itu. Lantai 50: gelar tetap **Jenius Batas Suci** (kultivasi ×1,1), siaran server, pencapaian.
  Lantai 60 pertama kali: Sayap Kunpeng mencapai kekuatan penuh.
* **Kalah:** bayangan memudar dan tubuh asli mendapat **Luka Jiwa** 5 menit (kerusakan −20%, tanpa kultivasi dari
  membunuh). Tidak ada yang mati; pendakian berakhir.
* Papan lantai terbaik semua murid tampil di bagian Alam Hampa. Pencapaian *Tangga Batu* (lantai 10) dan **Jenius Batas
  Suci** (lantai 50).

### 25.6 Latihan Khusus

**Sumber:** *I Shall Seal the Heavens*, bab 27–31: latihan promosi khusus menerima satu murid Sekte Luar ke Sekte
Dalam; *Battle Through the Heavens*: seleksi Akademi Dalam.

* Dimulai dari tablet Jenjang saat menaikkan murid Luar ke Dalam (syarat lain sudah terpenuhi, murid dalam 6 blok dari
  prasasti).
* Tiga gelombang Pengawal Dao (setara Pembangunan Fondasi, nyawa ×1,6) pada detik 0, 20, dan 40: 2, 3, lalu 4 monster.
* **Lulus:** murid bertahan 60 detik tanpa nyawanya turun di bawah 25%, dan kamu tidak memukul monster ujian ("Guru
  membantu: ujian batal").
* **Gagal:** pukulan yang akan membuatnya di bawah 25% ditahan di 25% dan ujian gagal; monster lenyap dengan asap. Murid
  tidak pernah mati. Coba lagi setelah 1 hari game. PK hanya dibayar saat lulus.

### 25.7 Menara Langit Membara

**Sumber:** *Battle Through the Heavens*, bab 491, 549, 622: ruang latihan di atas Api Jantung Jatuh yang tersegel; api
hati makin kuat makin dalam, makin dekat dengan api; murid membayar Energi Api untuk waktu latihan, dan pendatang baru
"terpanggang"; hari latihan bersama setiap bulan gratis.

* **Ruang:** kamu bangun sendiri. Sebuah **Api Unggun Jiwa yang menyala di atas Obsidian Menangis** dalam 32 blok dari
  prasasti adalah satu ruang; paling banyak 7. Diurutkan dari yang **tertinggi**: ruang paling atas lantai 1, paling
  dalam lantai n. Hanya dibaca, diperiksa ulang tiap 5 detik selama ada yang berlatih.
* **Berlatih:** tablet Arena, bagian Menara → pilih murid **Dalam ke atas** dan lantai → **Berlatih (lantai n)**. Syarat
  nomor ranah ≥ n − 1. Ia berjalan ke ruang itu dan bermeditasi di antara butir api jiwa.
  * Kultivasi meditasi ×(1,2 + 0,2n), pemahaman +0,02n per detik.
  * Biaya n PK murid per menit; latihan berhenti bila PK habis. **Hari Latihan Bersama** (hari game ke-`d` dengan
    `d % 7 = 3`) gratis.
* **Lonjakan api hati** tiap menit, peluang 4% × n: bila pemahamannya ≥ 10n ia bertahan (+2 pemahaman, +0,01 kualitas
  terobosan berikutnya, maks +0,1); bila tidak, 8% nyawa maksimal sebagai kerusakan (tidak pernah di bawah 50%) dan
  terlempar keluar 30 detik.
* Api unggun dipadamkan → ruangnya hilang dan latihan berhenti dengan pesan.

---

## 26. Kekuatan murid: Jurus Pamungkas, Api Surgawi, Pusaka Natal

Semua kekuatan di bagian ini hanya mengenai monster (aturan sasaran yang sama dengan teknikmu), api hanya menyala di
tubuh makhluk, dan petir hanyalah partikel. Tidak ada blok yang tersentuh.

### 26.1 Jurus Pamungkas

* Terbuka di **Jiwa Baru Lahir** untuk Dao murid (ia harus sudah memilih Dao).
* Dilepaskan sendiri dalam pertarungan bila ada bos atau ≥ 3 monster dalam 12 blok, atau atas perintahmu dengan tombol
  **Jurus Pamungkas** di tab Bakat. Jeda 120 detik kecuali disebut lain.
* Tidak pernah berpusat dalam 4 blok darimu atau penduduk desa ("terlalu dekat dengan target").
* **U** = kerusakan teknik murid × semua pengalinya (bintang, segel, lipatan pusaka, api, gelar).
* Pencapaian *Jurus Pamungkas*.

| Dao | Jurus | Sumber | Efek |
|---|---|---|---|
| Pedang | **Formasi Pedang Geng Agung** | *A Record of a Mortal's Journey*: 72 Pedang Bambu Biru Awan Berkerumun milik Han Li; Formasi Pedang Geng Agung (Da Geng Jian Zhen) | 12 / 24 / 36 / 72 pedang terbang partikel (Jiwa Baru Lahir / Transformasi Dewa / Penyatuan Dao / Kenaikan Abadi ke atas) menjalin kubah 10 blok di atas target selama 6 detik, 0,4 U per denyut pada monster di dalamnya |
| Petir | **Petir Pengusir Kejahatan** | Karya yang sama: Petir Ilahi Pengusir Kejahatan yang dibawa Bambu Petir Emas | Sambaran partikel emas pada sampai 6 monster, 2,5 U masing-masing (×1,5 melawan mayat hidup); tiap sambaran merambat sekali ke monster dalam 6 blok untuk 1,25 U |
| Ruang | **Kurungan Larangan** | *Renegade Immortal*, bab 216: larangan-larangan Wang Lin | Kubus glif radius 7 pada target; monster di dalamnya tak bisa bergerak, Kelemahan V selama 4 detik, dan 1 U per detik |
| Tubuh | **Tinju Kaisar Abadi** | *A Will Eternal*: Tinju Kaisar Abadi, jurus rahasia Tulang Abadi | Satu pukulan 6 × kerusakan serangnya plus gelombang kejut 4 blok sebesar 2 U |
| Api | **Teratai Api Amarah Buddha** | *Battle Through the Heavens*: teratai ciptaan Xiao Yan dari perpaduan Api Surgawi, makin dahsyat dengan setiap api yang ditambahkan | Diisi 4 detik, dilempar sampai 24 blok; ledakan partikel saja (bukan ledakan sungguhan), radius 4 (6 dengan ≥ 2 Api Surgawi), `3 U × max(1, jumlah api)`, membakar tubuh monster 6 detik; jeda 45 detik |
| Kehidupan | **Kitab Kehancuran Agung** (bentuk aman blok) | *Martial Universe*: Kitab Kehancuran Agung menghimpun daya hidup ke dalam satu tubuh | Menyerap monster dalam 10 blok, 1,5 U masing-masing; memulihkan dirinya 100%, kamu dan murid lain dalam 10 blok 30%; +30% kerusakan selama 30 detik |
| Es, Bayangan | teknik kitab Dao itu, diperkuat | tanpa konsep baru (Penjara Gletser / Badai Angin yang sudah ada) | +60% kerusakan dan radius ×2 |

**Fusi Api Guru-Murid.** **Sumber:** *Battle Through the Heavens*: teratai pertama memadukan api Xiao Yan dengan Api
Dingin Tulang milik gurunya, Yao Lao. Selama Teratai Api diisi, kamu (bila ber-Dao Api dan dalam 6 blok) menekan
**Fusi** di tab Bakat murid: `5 U × max(1, jumlah api)` dalam radius 6, memakan 30% qi maksimalmu. Pencapaian **Api Guru
dan Murid**.

### 26.2 Benih Api Surgawi

**Sumber:** *Battle Through the Heavens*: Api Surgawi yang berperingkat (Api Inti Bumi Teratai Hijau, Api Dingin Tulang,
Api Hati Lautan, Api Jantung Jatuh, Api Teratai Iblis Murni), dan Mantra Api yang tumbuh dengan melahapnya.

| Benih | Jatuh dari | Peluang |
|---|---|---|
| Api Inti Bumi Teratai Hijau | blaze yang kamu bunuh | 0,5% |
| Api Dingin Tulang | stray yang dibunuh olehmu atau murid | 0,3% |
| Api Hati Lautan | Elder Guardian yang kamu bunuh | pasti pada kali pertama, lalu 25% |
| Api Jantung Jatuh | Wither yang kamu bunuh | pasti pada kali pertama, lalu 25% |
| Api Teratai Iblis Murni | Ender Dragon | selalu |

* **Siapa:** murid berakar Api atau ber-Dao Api, atau murid mana pun sejak Jiwa Baru Lahir.
* Klik kanan murid dengan benih (atau berikan lewat gulungannya): **ritual 20 detik**. Tanpa Hu Fa
  ([27.4](#274-hu-fa-menjaga-terobosan)) ritual gagal 40%: Penyimpangan Qi 3 menit dan benih kembali kepadamu. Ritual yang
  terputus (misalnya murid tidak termuat lagi) mengembalikan benihnya nanti.
* **Tiap api** (paling banyak 3, +1 per 9 bintang): kerusakan teknik api +20%, pukulannya membakar tubuh makhluk 4 detik,
  kebal api dan lava. Tampil sebagai bola api berwarna di auranya.
* Pencapaian *Pelahap Api*.

### 26.3 Pusaka Natal

**Sumber:** *A Will Eternal*, bab 5, 29–38, 637: penempaan roh Bai Xiaochun memberi kilau perak pada satu lipatan lalu
dua dan tiga lipatan; para tetua berhenti di satu atau dua lipatan karena kegagalan makin sering; Payung Abadi-nya
berubah pada penempaan keempat belas; api Ekor Roh tiga warna adalah bahan bakarnya. *A Record of a Mortal's Journey*:
72 pedang adalah pusaka natal Han Li yang terikat nyawa.

* **Memurnikan** (tab Pusaka): sejak Pembangunan Fondasi, sekali per murid, saat kamu berdiri di dalam wilayah aula.
  Biaya 1 Berlian + 8 Bulu Ekor Roh (gratis di mode Kreatif). Pilih bentuknya sekali: **Pedang**, **Payung**,
  **Wajan**, atau **Lonceng**. Pusaka ini bukan item; ia tergambar di punggung murid.
* **Tempa Lipatan** ke-n (maks 18, 19 dengan Jari Penempa Roh): biaya n Bulu Ekor Roh + `n × 40` PK dari kas.
  Peluang = `0,95 − 0,07 × (n − 1)`, +0,15 Jari Penempa Roh, +0,05 Dao Api, +0,05 Tingkat Aula 5+, minimal 0,10. Gagal
  sejak lipatan 3 mematahkan satu lipatan (asap dan letupan, tanpa blok).
* **Setiap lipatan:** +5% kerusakan jarak dekat dan teknik. Lipatan 3: kilau perak. Lipatan 9: emas dan kemampuan
  bentuknya. Lipatan 14: **Payung Abadi**, kemampuannya berlipat dua dan pusakanya mengitari pemiliknya.

| Bentuk | Lipatan 9 | Lipatan 14 (Payung Abadi) |
|---|---|---|
| Pedang | kerusakan teknik +10% | +20% |
| Payung | tiap 60 detik, kamu dan murid dalam 4 blok mendapat Penyerapan II selama 10 detik | Penyerapan IV |
| Wajan (wajan tempurung kura-kura Bai Xiaochun) | kerusakan diterima −20% | −40% |
| Lonceng | setiap tekniknya memberi Kelambatan II 3 detik pada monster dalam 6 blok | Kelambatan IV |

Pencapaian *Pusaka Sembilan Lipatan* dan **Payung Abadi**.

### 26.4 Kitab Abadi Tak Mati

**Sumber:** *A Will Eternal*: tingkatan Kitab Abadi Tak Mati, yaitu Kulit Abadi (Kulit Besi, Perunggu, Perak, Emas), Urat
Abadi, Tulang Abadi (dengan jurus rahasianya Tinju Kaisar Abadi), dan Darah Abadi. Tingkat kekuatan Raja Langit
dilewati sebagai penyederhanaan.

* **Akses:** Manual Tubuh Abadi dari Paviliun Yue Lu, atau gratis untuk murid ber-Dao Tubuh dan Dantian Tersumbat.
* **Poin Tempa:** 1 per 1% nyawa maksimal yang diterima dalam pertarungan (paling banyak 200 sehari; ×2 dengan Takut
  Mati), plus 1 per menit Pikul Air.

| Tahap | Poin | Efek |
|---|---:|---|
| Kulit Perak | 150 | zirah +6 |
| Kulit Emas | 500 | zirah +12, ketangguhan +4, kebal api di tubuhnya, kilau emas |
| Urat Abadi | 1.200 | kecepatan +15%; melesat ke targetnya (8 blok, tiap 6 detik) |
| Tulang Abadi | 2.500 | Tinju Kaisar Abadi di Dao apa pun (tiap 30 detik) |
| Darah Abadi 0–100% | 80 per 1% | +0,05 nyawa per detik tiap 10%. Di 100%: sekali per hari game pukulan maut menyisakannya dengan 1 nyawa dan Ketahanan V 5 detik, tanpa Papan Jiwa. Lalu **Kitab Hidup Abadi**: +1% nyawa maksimal per bintang |

Pencapaian **Darah Abadi**.

### 26.5 Pelarian Jiwa Baru Lahir

**Sumber:** *A Record of a Mortal's Journey*: Jiwa Baru Lahir melarikan diri sebagai seberkas cahaya saat tubuhnya hancur.

Murid **Jiwa Baru Lahir ke atas** yang gugur saat aulamu aktif tidak meninggalkan Papan Jiwa: seberkas cahaya jiwa terbang
ke prasasti, ia tercatat "sedang pergi bertugas" selama 12.000 tick (±10 menit, juga saat kamu luring), lalu terbentuk
kembali di prasasti dengan nyawa penuh dan bar berkurang 10%. Di bawah Jiwa Baru Lahir, atau tanpa aula aktif, aturan
Papan Jiwa biasa berlaku.

### 26.6 Sayap Kunpeng

**Sumber:** *Perfect World*, bab 258–347: tulang purba Kunpeng terbelah enam bagian yang hanya memberi sekitar sepertiga
warisannya; teknik sejatinya ada di Gua Pemurnian Iblis dan butuh ranah lebih tinggi.

Diajarkan dengan menyatukan enam Pecahan Tulang Kunpeng ([24.6](#246-tulang-kunpeng-dan-bahtera-purba)); Dao apa pun.
Tombol **Sayap Kunpeng** di tab Pusaka: melesat 12 blok dengan sabit angin 2 U pada monster di jalurnya, jeda 20
detik (jalurnya harus terbuka; tidak ada blok yang disentuh). Setelah murid menaklukkan lantai 60 Alam Dewa Hampa:
melesat 24 blok, 6 U. Pencapaian **Sayap Sang Kunpeng**.

### 26.7 Formasi Tujuh Peri

**Sumber:** *Against the Gods*, bab 739: Tujuh Peri Awan Beku, tujuh murid elit sekte.

Bila 7 murid Inti ke atas milikmu atau lebih bertarung dalam 16 blok satu sama lain, masing-masing memberi +10%
kerusakan.

---

## 27. Ikatan, Segel Jiwa, Hu Fa, dan Fondasi Dao

### 27.1 Ikatan

**Sumber:** *Tales of Demons and Gods*: murid baru membentuk Segel Jiwa dengan gurunya sebagai tanda kesetiaan, dan bab
343 berjudul "Menerima seorang murid". Bab 13: Nie Li mencocokkan teknik setiap temannya dengan atribut jiwa mereka.

Ikatan (0–100) tampil di tab Jalan, bagian Ikatan.

| Tindakan | Ikatan |
|---|---:|
| Mengajarkan teknik | +5 |
| Memberi pil | +2 (paling banyak 10 sehari) |
| Menjaga terobosan atau bencananya (Hu Fa) | +10 |
| Bermeditasi dalam 8 blok darimu | +1 per menit (paling banyak 20 sehari) |
| Setiap laga turnamen yang ia menangkan | +3 |
| Setiap Balas Budi (ia menyelamatkanmu) | +5 |

Ikatan hilang bila murid dilepaskan. Pada **50** kamu bisa membentuk Segel Jiwa; pada **80** ia bisa menjadi pewaris.

### 27.2 Segel Jiwa

* **Giok Segel Jiwa** (resep tanpa pola): Serpihan Batu Kecubung + Serpihan Gaung + Batangan Emas + Kertas.
* Klik kanan murid dengan ikatan ≥ 50 sambil memegang giok (boleh sambil jongkok; jongkok dengan tangan kosong atau
  barang biasa tetap membuka gulungan murid): ritual aksara segel 5 detik (tetap dalam 6 blok; bila terputus, gioknya
  kembali). Pencapaian *Segel Jiwa*.
* Paling banyak `3 + Tingkat Aula / 3` murid tersegel (3 tanpa aula).
* Murid yang tersegel:
  1. bisa dipanggil ke tempat aman di sisimu dari mana saja di dimensi yang sama (tab Jalan > Ikatan > *Panggil*, jeda
     60 detik),
  2. mendapat +10% nyawa maksimal, kerusakan, dan qi maksimal,
  3. saat nyawamu di bawah 30%: setiap murid tersegel dalam 48 blok mendapat Kecepatan II + Kekuatan I selama 10 detik
     dan beralih ke Bantu sampai pertarungan usai, lalu kembali ke perintah lamanya.

### 27.3 Mengajar sesuai unsur

Teknik yang unsur Dao-nya ada di akar murid hanya memakan **10%** qi maksimalmu (bukan 20%), dan 10 kali pertama ia
memakainya memberi pemahaman dua kali lipat.

### 27.4 Hu Fa: menjaga terobosan

**Sumber:** *Battle Through the Heavens*: Yao Lao melindungi dan membimbing Xiao Yan saat memurnikan Api Inti Teratai Hijau
dan Api Jantung Jatuh (护法, Hu Fa).

**Kapan berlaku:** selama terobosan, bencana, pemadaman Lampu Jiwa, atau penyerapan Api Surgawi seorang murid, kamu
menjaga bila berada dalam **8 blok** dan bermeditasi, atau diam sambil jongkok. Tab Jalan menulis "Hu Fa aktif".

| Efek | Nilai |
|---|---|
| Sambaran | ×0,8 |
| Kualitas terobosan | +0,1 |
| Peluang Fondasi | +10% |
| Penyerapan Api Surgawi | tidak pernah gagal |
| **Tahan Petir** (tombol di tab Jalan atau `/kultivasi murid tahan`) | Sambaran berikutnya mengenaimu: kamu menerima `0,5 × porsi sambaran itu × nyawa maksimalmu` (tidak pernah di bawah 1 nyawa), dan sambaran murid dilewati. Paling banyak 3 kali per ujian, 15% qi maksimalmu setiap kali. Busur kuning terlihat dari murid kepadamu |

**Pengawal Dao untuk murid** (tribulasi Inti Emas ke atas dan bencana bintang):
* Tribulasi: setiap 2 sambaran datang gelombang sebesar `1 + nomor ranah tujuan / 3`.
* Bencana: setiap 3 sambaran, gelombang sebesar `2 + k` dengan nyawa ×(1 + 0,5k). Paling banyak 12 hidup sekaligus dan
  40 per bencana.
* Setiap Pengawal yang **kamu** bunuh membuat sambaran berikutnya ×0,92 (minimal ×0,6) dan +0,05 kualitas (maks +0,2).
  Murid lain ikut melawan, tetapi tidak meringankan sambaran.
* Menjaga sampai berhasil memberi pencapaian *Pelindung Dao Murid*.

### 27.5 Fondasi Dao

**Sumber:** *A Will Eternal*: Fondasi Dao Fana (dengan pil), Dao Bumi, atau Dao Langit (hanya karena keberuntungan; Bai
Xiaochun meraihnya). *I Shall Seal the Heavens*, bab 76 dan 100: pilar Fondasi dinilai dari retaknya (Tanpa Cela, Retak,
Remuk), dan Fondasi Sempurna tanpa retak yang luar biasa langka; Fondasi Meng Hao punya sepuluh pilar.

Dilempar saat terobosan Pemurnian Qi → Pembangunan Fondasi murid berhasil. Tampil sebagai gambar pilar di tab Jalan,
bagian Ikatan, dengan pilar partikel di dunia.

| Jenis | Peluang dasar | Pilar | Efek |
|---|---:|---|---|
| Dao Fana | 60% | 9, remuk | – |
| Dao Bumi | 32% | 9, retak | kultivasi ×1,1, nyawa maksimal +10% |
| Dao Langit | 8% | 9, tanpa cela | kultivasi ×1,25, sambaran berikutnya ×0,85, pilar emas di auranya |
| Sempurna | 0,01% (angka game) | **10**, tanpa retak | kultivasi ×1,4, bencana pertama di bintang 8 |

Peluang bergeser (diambil dari Dao Fana): kamu bermeditasi dalam 8 blok selama ia menghimpun qi (+10% Bumi, +5%
Langit); kualitas Fondasimu sendiri Sempurna atau Surgawi (+3% Langit); Pil Pembangunan Fondasi mutu Sempurna yang
diberikan sebelumnya (+5% Langit). Ketiganya sekaligus: Sempurna menjadi 2%. Pencapaian *Fondasi Dao Langit* dan
**Fondasi Sempurna**.

---

## 28. Bintang Abadi

Murid tidak berhenti di Dewa Abadi. Mereka memadatkan bintang demi bintang, tanpa batas, makin lama makin kuat.

**Sumber:**
* *Renegade Immortal*, bab ~1157–1159: Dewa Kuno tumbuh lewat bintang dan menghadapi Tiga Ujian dengan Tujuh Bencana
  (2 + 2 + 3). Kebanyakan bertemu yang pertama pada enam bintang, sebagian pada tujuh, sangat sedikit pada delapan (Tu
  Si). Gagal berarti mati (di sini: luka). Wang Lin berakhir sebagai Penginjak Langit.
* *Stellar Transformations*: tahap Nebula, Meteor (1, 3, 9 butir emas), Inti Bintang, Planet, Tribulasi, Gemintang, dan
  di atasnya Bintang Gelap dan Lubang Hitam.
* *I Shall Seal the Heavens*, bab ~1276–1362: Lampu Jiwa, kira-kira satu per lima meridian Abadi; Meng Hao punya 33.
  Memadamkan sebuah lampu menyerap klonnya dan sangat menaikkan kekuatan. Angka bonusnya desain game.
* *Coiling Dragon*: Linley memurnikan percikan ilahi menjadi Klon Ilahi yang bertarung di sisinya.
* *Battle Through the Heavens*: tingkatan Dou dalam 1–9 bintang.

### 28.1 Memadatkan bintang

* Di **Puncak Dewa Abadi** dengan bar penuh, setiap kultivasi berikutnya menjadi **Qi Bintang**.
* Bintang ke-n berharga `min(5 miliar, 500.000 × 1,07^(n−1))` Qi Bintang. Jumlah bintang tidak terbatas.
* Dengan **Otomatis** menyala (bawaan), bintang memadat sendiri begitu Qi Bintang cukup dan tidak ada bencana yang
  menghadang; bila mati, tekan **Padatkan Bintang** di tab Jalan, bagian Bintang.
* Butir perak runtuh menjadi bintang yang bergabung dengan orbit di dada murid, diiringi denting. Pencapaian *Bintang
  Pertama Terpadatkan*.

### 28.2 Tujuh Bencana dalam Tiga Ujian

* Bencana k (1–7) menghadang bintang `f + 9 × (k − 1)`. f = 6 bila rata-rata kualitas terobosannya Biasa atau lebih
  rendah, 7 bila Sempurna, 8 bila Surgawi, Fondasi Sempurna, atau Darah Dewa Kuno. Untuk f = 6: bintang 6, 15, 24, 33,
  42, 51, 60.
* **Mulai Bencana k** di tab Jalan (gulungan menutup). Syarat: kamu dalam 16 blok, nyawa murid ≥ 50%, hanya satu
  bencana per Guru dalam satu waktu. Harga bintangnya dibayar di awal; murid yang bergerak sebelum sambaran pertama
  membatalkannya dengan harga kembali penuh.
* Pertanda 6 detik, lalu `6 + k` sambaran sebesar `(0,12 + 0,01 × nomor ujian) × nyawa maksimal`, satu tiap 3 detik,
  yang terakhir ×2. Gelombang Pengawal Dao datang setiap 3 sambaran; Hu Fa dan Tahan Petir berlaku. Petirnya hanya
  partikel: tidak ada petir sungguhan, cuaca tidak berubah, blok tidak tersentuh.

| Hasil | Efek |
|---|---|
| **Lewat** | Bintangnya memadat (+100 ketenaran). Menuntaskan sebuah Ujian (k = 2, 4, 7) mengalikan semua bonus bintang ×1,1 (faktor ujian T, paling banyak ×1,331). Pencapaian *Bencana Terlewati* |
| **Gagal** (sambaran yang akan mematikan, murid tumbang, menjauh lebih dari 6 blok setelah sambaran pertama, atau terputus karena tidak termuat / server dimulai ulang) | **Luka Bencana** (Penyimpangan Qi 12.000 tick), tanpa bintang, separuh harga kembali sebagai Qi Bintang. Murid tidak pernah mati |

Setelah k = 7: gelar **Penginjak Langit**, aura putih-emas, dan pengumuman server. Pencapaian **Penginjak Langit**.

### 28.3 Bonus per bintang

| Bonus | Rumus (n = bintang, T = faktor ujian) |
|---|---|
| Nyawa maksimal | +4 × n × T (×1,5 dengan Darah Dewa Kuno) |
| Serangan | +0,6 × n × T |
| Kerusakan teknik | ×(1 + 0,04 × n × T) |
| Setiap tahap 9 bintang yang tuntas | ×1,15 pada kerusakan teknik dan jarak dekat |
| Qi maksimal | ×(1 + 0,02n) |
| Kultivasi meditasi | ×(1 + 0,04n); ×2 di bawah langit malam terbuka (Qin Yu menyerap kekuatan bintang) |

Nyawa di atas batas atribut 1.024 diubah menjadi pengurangan kerusakan: +0,25% per 4 nyawa lebihnya, maks 80%.

**Kurva kekuatan** (faktor kerusakan teknik; nyawa untuk murid dengan dasar ±77 di Dewa Abadi):

| Bintang | 9 | 18 | 27 | 36 | 54 | 72 | 100 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Teknik ×(1 + 0,04nT) × 1,15^tahap | 1,56 | 2,37 | 3,33 | 4,80 | 8,36 | 14,8 | 29,4 |
| Nyawa maksimal ≈ | 113 | 156 | 196 | 251 | 338 | 460 | 609 |
| Harga bintang berikutnya | 0,86 jt | 1,58 jt | 2,90 jt | 5,34 jt | 18,0 jt | 61 jt | 406 jt |

**Penghasilan bintang.** Setiap hadiah acara adalah bagian dari "bar": bar kultivasi di bawah Puncak Dewa Abadi, atau
harga bintang berikutnya sesudahnya. Jadi pendakian tetap stabil, dan batas harian/mingguan mencegah farming.

| Sumber | Bagian |
|---|---|
| Ekspedisi berhasil | 15–45% |
| Penaklukan pertama lantai f Alam Dewa Hampa | `4% × (1 + f/20)` |
| Turnamen juara 1 / 2 / 3 | 40% / 25% / 15% |
| Bahtera Purba | 60% |
| Meditasi | seperti biasa, dengan bonus bintang |

Perkiraan: ±0,3–1 bintang per hari game untuk tiap murid yang aktif.

### 28.4 Gelar tahap

Setiap 9 bintang (*Stellar Transformations*); nama di atas kepala menjadi misalnya "Lin Feng · Planet ★31".

| Bintang | Gelar | Tampilan |
|---|---|---|
| ★1–9 | Nebula | serpihan perak mengorbit |
| ★10–18 | Meteor | 1, 3, lalu 9 butir emas di ★10, 13, dan 18 |
| ★19–27 | Inti Bintang | cahaya inti emas di dada |
| ★28–36 | Planet | **Klon Ilahi** terbuka |
| ★37–45 | Tribulasi | |
| ★46–54 | Gemintang | aura seperti matahari; 2 klon |
| ★55–63 | Bintang Gelap | |
| ★64–72 | Lubang Hitam | |
| ★73+ | Tak Terbatas I, II, … (satu angka per 9 bintang) | warna aura baru per tahap |

Pengumuman server di ★36, saat menjadi Penginjak Langit, dan setiap Lampu Jiwa ke-9. Setiap bintang ke-9 memberi +50
ketenaran.

### 28.5 Klon Ilahi

Mulai ★28 satu klon, mulai ★46 dua. Klon adalah salinan tembus pandang dengan 50% nyawa dan serangan, teknik 50% U,
bertahan 60 detik, jeda 5 menit. Muncul sendiri saat ada bos dalam 24 blok atau murid di bawah 50% nyawa dalam
pertarungan. Tidak disimpan dan tidak dihitung sebagai murid.

### 28.6 Lampu Jiwa

Setelah Penginjak Langit, satu Lampu Jiwa menyala setiap 10 bintang (paling banyak 33). **Padamkan Lampu** di tab Jalan
adalah bencana dengan `k = 8 + lampu yang sudah padam`. Setiap lampu padam: jangkauan Jurus Pamungkas dan Jurus Pemusnah
+10%, kerusakan +5%. Tab Jalan menampilkan deretan lampu. Pencapaian *Lampu Jiwa Padam*.

---

## 29. Pewaris, Cincin Jiwa Guru, dan Murid Melampaui Guru

### 29.1 Pewaris dan Sembilan Segel

**Sumber:**
* *I Shall Seal the Heavens*, bab 102, 1379, 1534–1556: Liga Penyegel Iblis mewariskan satu Penyegel Iblis per generasi,
  dan setiap generasi harus menciptakan segel baru (Segel Kedelapan, Segel Kesembilan milik Meng Hao). Penguasa Gunung
  dan Laut harus menguasai Sembilan Segel.
* *Renegade Immortal*: Wang Lin mewarisi warisan dan ingatan Tu Si.
* *Martial Universe*: Lin Dong menjadi murid langsung senior Aula Kehancuran.

**Mengangkat pewaris** (tab Jalan, bagian Pewaris, **Angkat Pewaris**):
* Syarat: jenjang Inti ke atas, Jiwa Baru Lahir ke atas, ikatan ≥ 80, dan tersegel.
* Ritual 60 detik: kamu bermeditasi dalam 4 blok darinya, memakan 30% qi maksimalmu. Cahaya giok menghubungkan dantian
  kalian. Pencapaian *Pewaris Ditetapkan*.

**Pewaris mendapat:**
* Semua teknik yang bisa diajarkan yang kamu kuasai, gratis.
* Pemahaman `max(miliknya, milikmu × 0,5)` di Dao-nya.
* Faktor kultivasi +0,05.
* Bagian meditasi dari Guru ×2 (20%, bukan 10%; murid langsung).

**Segel Generasi:** pilih salah satu teknikmu yang dikuasai pewaris dengan pemilih ‹ › lalu **Tandai Segel Generasi**:
versi pewaris memberi +50% dan memperlihatkan cincin segel.

**Segel baru:** setiap ranah baru yang dicapai pewaris, dan setiap 9 bintang, ia boleh menciptakan satu segel (**Ciptakan
Segel (N)**) pada teknik yang belum bersegel: +25% dan efek segel. Paling banyak sembilan.

**Kesembilannya:** gelar **Penguasa Sembilan Segel**, pasif **Tersegel** (setiap 20 detik pukulan berikutnya menahan
monster dalam 6 blok dengan Kelambatan V + Kelemahan V selama 3 detik), dan Segel Gunung-Laut ([bagian
30](#30-jurus-pemusnah)). Pencapaian **Penguasa Sembilan Segel**.

**Mengganti pewaris:** pewaris lama tetap menguasai tekniknya tetapi kehilangan semua segel (tekan sekali lagi untuk
memastikan). Garis perguruan "generasi ke-N" tampil di tab Jalan dan di tooltip Papan Jiwa.

### 29.2 Cincin Jiwa Guru

**Sumber:** *Battle Through the Heavens*: jiwa Yao Lao tinggal di dalam cincin, diam-diam menyerap Dou Qi Xiao Yan selama
tiga tahun, lalu menjadi gurunya, meminjamkan kekuatan jiwa atau mengambil alih dalam pertarungan maut. *Coiling Dragon*:
jiwa Doehring Cowart di Cincin Naga Melingkar mengajari Linley dan menjaganya dalam bahaya.

* **Cincin Jiwa Guru** (resep tanpa pola): Batangan Emas + Serpihan Gaung + Serpihan Batu Kecubung + Mutiara Ender.
  Gunakan pada **pewarismu** saja; satu cincin per pewaris, dan cincin tetap bersama murid (juga di Papan Jiwanya).
* **Terbangun:** selama 72.000 tick (3 hari game) cincin mengambil 50% kultivasi meditasi pewaris. Lalu cincin terikat
  (pesan chat, efek, pencapaian *Guru di Dalam Cincin*).

| Kemampuan | Efek |
|---|---|
| **Bimbingan Jiwa** | Bagian meditasi dari Guru sampai di jarak berapa pun dan di dimensi mana pun |
| **Perlindungan Jiwa** | Sekali per hari game pukulan maut menyisakan pewaris dengan 1 nyawa dan Ketahanan IV 3 detik, dengan bayangan sang guru tua |
| **Jiwa Guru Turun** | Otomatis di bawah 30% nyawa dalam pertarungan, atau tombol di tab Jalan; kamu harus daring di dimensi yang sama; jeda 10 menit. Selama 20 detik pewaris memakai kekuatan teknikmu, dan boleh melepaskan Telapak Buddha serta denyut Wilayah Dao 6 blok dari Dao-mu pada monster, dalam aura warna ranahmu. Biayamu: 40% qi maksimal di awal plus 2 qi per detik, Kelambatan I, dan kamu tidak bisa merapal teknik selama itu |

### 29.3 Murid Melampaui Guru

**Sumber:** *Battle Through the Heavens*: Xiao Yan melampaui Yao Lao dan kelak menyelamatkannya dari Aula Jiwa. *Martial
Peak*: Meng Wu Ya, penjaga Aula Kontribusi, mengenal dan menyembuhkan murid percobaan Yang Kai, yang kemudian
melampauinya.

**Pemicu** (sekali per murid):
* Kekuatan murid (langkah ranah di jalan 65 posisi + bintang) melebihi kekuatanmu (langkah ranahmu + bintangmu; bintang
  pemain baru ada di Fase 7), terlihat di tab Jalan bagian Pewaris; **atau**
* murid menang **Tanding dengan Guru**: duel denganmu, keduanya ≥ 80% nyawa dalam 16 blok, 120 detik, berakhir di 10%
  nyawa; sekali per hari game; keduanya mendapat +1 pemahaman dan +3 ikatan.

Pemicu memutar pilar emas, pesan ke seluruh server, dan pencapaian **Murid Melampaui Guru**.

**Efek:**
* Faktor kultivasi murid +0,1.
* **Kebanggaan Guru:** kultivasimu +5% per murid yang telah melampauimu dan masih hidup, maks +15%.
* **Balas Budi:** sekali per hari game, saat nyawamu di bawah 25% dalam 48 blok di dimensi yang sama, murid teleport ke
  tempat aman di sisimu, memberimu Penyerapan sebesar 25% nyawa maksimalnya dan Ketahanan II 5 detik, dan memancing
  monster dalam 12 blok selama 8 detik.
* **Tetua Kehormatan** (sakelar di tab Jalan): saat kamu bermeditasi dalam 8 blok dari murid ini, kamu mendapat 5% dari
  kultivasi meditasinya.

---

## 30. Jurus Pemusnah

Jurus pamungkas penghancur dunia dari setiap Dao. **Secara bawaan semuanya aman blok**: efek dahsyat dan kerusakan hanya
pada monster.

**Sumber:**
* *A Record of a Mortal's Journey*, bab 2191/2238: Pedang Pembantai Roh Xuantian, harta Xuantian (tingkat tertinggi),
  luar biasa kuat dan menguras mana Han Li. Tidak ada "hukum kehancuran" yang diklaim.
* *Battle Through the Heavens*: Teratai Api Amarah Buddha jauh lebih dahsyat dengan setiap api yang dipadukan.
* *Martial Universe*: Kitab Kehancuran Agung menghancurkan seribu mil di sekeliling penggunanya dan mengumpulkan daya
  hidup ke satu tubuh; Tangan Langit Pemenjara Kehancuran Agung (bab 701); Simbol Leluhur Melahap melahap energi.
* *Soul Land*: Teknik Palu Jubah Kacau milik Sekte Langit Cerah menghimpun tenaga lewat pukulan beruntun, dan Palu Agung
  Sumeru adalah jurus pamungkasnya yang dahsyat.
* *Coiling Dragon*: misteri bumi Linley, Denyut, getaran yang menghancurkan batu dari dalam.
* *Against the Gods*, bab 46: gerbang ketujuh Dewa Sesat; ranah kelima, Raja Neraka, adalah yang terakhir disebut ingatan
  darahnya.
* *I Shall Seal the Heavens*, bab 1556: Penguasa Alam Gunung dan Laut harus menguasai Sembilan Segel.
* *Renegade Immortal*: Penginjak Langit, di luar tiga langkah.

* Terbuka setelah murid melewati **bencana ke-4** (Ujian Kedua, sekitar ★33). Satu jeda bersama 10 menit per murid.
* Dilepaskan sendiri melawan bos dalam 32 blok (selalu aman blok), atau diperintah dari tab Jalan, bagian Pemusnah:
  pilih jurusnya, lalu tekan **Jurus Pemusnah**.
* Hanya dengan **ketiga kunci Mode Kehancuran terbuka** ([bagian 31](#31-mode-kehancuran-peringatan)) jurus yang
  *diperintahkan* juga mengubah blok. Jurus otomatis tidak pernah.
* R = `kehancuranMaxRadius` (bawaan 12), s = bintang murid. Setiap Lampu Jiwa padam menambah jangkauan 10%.

| Jurus | Siapa | Bentuk aman blok (bawaan) | Bentuk di Mode Kehancuran |
|---|---|---|---|
| **Tebasan Xuantian** | Dao Pedang | garis cahaya pedang mulai 3 blok di depan: 8 U pada monster dalam 2 blok darinya | parit sepanjang arah bidikan mulai 3 blok di depan: lebar 3, dalam 4, panjang `min(48, 16 + s/2)` |
| **Teratai Api Amarah Buddha Agung** | Dao Api dengan ≥ 2 Api Surgawi | 8 U dalam radius + 2, membakar tubuh monster 8 detik | bola di titik hantam, radius `min(R, 4 + s/9)` |
| **Getaran Bumi** | Dao Tubuh | 6 U, monster terlempar, Kelambatan IV 4 detik | cakram sedalam 3 di bawah permukaan, radius `min(R + 4, 6 + s/6)`; hanya batu, tanah, pasir, pasir merah, kerikil, dan batu End alami |
| **Kitab Kehancuran Agung** | Dao Kehidupan | bentuk aman blok dari [26.1](#261-jurus-pamungkas) dengan 2 U | permukaan layu dalam radius `min(R + 8, 8 + s/6)`: blok rumput, podzol, miselium, dan tanah berakar menjadi tanah kasar; daun, rumput, pakis, bunga, dan sulur lenyap; tanaman ladang dan tanah garapan dibiarkan; tanpa lubang |
| **Tangan Penjara Langit** | Dao Ruang | 8 U dan monster tertahan 5 detik di dalam kolom 9 × 9 | lubang 9 × 9, dalam `min(12, 3 + s/18)` |
| **Palu Agung Sumeru** | Dao Es, Petir, Bayangan, atau tanpa Dao | sembilan pukulan penghimpun (aksara palu membesar), lalu 15 U dalam radius + 4 | setelah sembilan pukulan, bola radius `min(R, 4 + s/9)` |
| **Murka Raja Neraka** | Nadi Dewa Sesat dengan gerbang 7 | gelombang merah tua 24 blok sebesar 6 U, langit merah (partikel) | bola radius `min(R, 4 + s/5)` |
| **Pusaran Melahap** | Pembawa Simbol Melahap | pusaran radius 8 menarik monster selama 4 detik, 1 U per detik | kolom radius 3 dari permukaan sasaran turun 16 blok |
| **Segel Gunung-Laut** | pewaris dengan kesembilan segel | sembilan denyut segel 3 U, masing-masing radius 4, dalam kisi 3 × 3 | sembilan bola radius `min(4, R)` dalam kisi 3 × 3 berjarak 8 blok |

Kerusakan selalu hanya pada monster: pemain, hewan peliharaan, penduduk desa, dan murid tidak pernah terluka.

---

## 31. MODE KEHANCURAN (peringatan)

> **PERINGATAN — SEGEL MERAH.** Mode Kehancuran adalah **satu-satunya** bagian Kultivator yang menghancurkan **blok
> sungguhan**. Mode ini **MATI secara bawaan** dan tidak bisa menyala sendiri. Nyalakan hanya di dunia uji atau dunia
> yang memang rela kamu rusak, dan **buat cadangan dunia lebih dulu**. Tanpa ketiga kunci di bawah, setiap Jurus Pemusnah
> tetap aman blok.

### 31.1 Apa yang dilakukannya

Bila ketiga kunci terbuka, **Jurus Pemusnah yang kamu perintahkan** untuk seorang murid yang segelnya dilepas benar-benar
menghapus blok dengan bentuk dari [bagian 30](#30-jurus-pemusnah) (parit, bola, cakram, permukaan layu, lubang, kolom,
sembilan bola). Jurus otomatis melawan bos tetap aman blok. Kamu sendiri tidak pernah mendapat teknik penghancur.

### 31.2 Tiga kunci (semuanya wajib)

1. **Izin server.** `modeKehancuran` di `config/kultivator.json` (bawaan `false`). Hanya operator yang mengubahnya:
   `/kultivasi kehancuran izin on` (atau `off`) langsung berlaku dan menulis berkasnya, atau berkas diedit saat server
   mati. Di dunia pemain tunggal ini butuh cheat aktif (atau mengedit berkas).
2. **Sumpah Kehancuran** (per pemain): tombol di tablet **Segel Merah** gulungan aula, tab Jalan bagian Pemusnah, atau
   `/kultivasi kehancuran sumpah`. Di LAN setiap pemain hanya bersumpah untuk murid-muridnya sendiri. Pencapaian
   *Sumpah Kehancuran*.
3. **Lepaskan Segel** (per murid): tab Jalan, bagian Pemusnah, hanya untuk murid yang sudah melewati bencana ke-4. Murid
   yang dilepas bertanda **[Segel Merah]** merah di depan namanya dan berpercik merah di auranya.

Kunci 2 dan 3 masing-masing membuka **layar peringatan merah darah**. **Menutup kunci mana pun cukup satu klik** (Cabut
Sumpah, Tutup Segel Merah, atau `/kultivasi kehancuran izin off`) dan menghentikan setiap penghancuran saat itu juga.
Tablet Segel Merah hanya muncul bila server mengizinkan; bila tidak, tab Pemusnah menulis "Mode Kehancuran mati di
server ini".

### 31.3 Layar peringatan

Perkamen merah tua bertinta hitam dengan cap merah dan detak jantung pelan, berjudul "SEGEL MERAH — MODE KEHANCURAN".
Layar ini menyebut persis bentuk dan radius maksimal, jumlah blok maksimal per jurus, zona terlindung, "Tidak ada drop"
(atau persen drop), dan jendela pulih. Centang talisman **"Saya paham blok akan hancur"**, lalu **tahan** segelnya 5
detik (sumpah) atau 3 detik (melepas segel murid). Esc membatalkan.

### 31.4 Memerintahkan jurus penghancur

1. Tab Jalan, bagian Pemusnah → **Jurus Pemusnah (Hancurkan)** (gulungan menutup). Dalam 10 detik, jongkok sambil
   melihat sasaran (paling jauh 64 blok; hanya membaca blok).
2. Server memeriksa bentuknya terhadap zona terlindung. Bila menyentuh salah satunya: "Ditolak: bentuknya menyentuh …".
   Bila lolos, garis pratinjau merah bentuknya terlihat selama 15 detik.
3. Konfirmasi di chat dalam 15 detik: **[Ya, hancurkan]** atau **[Batal]** (juga `/kultivasi kehancuran ya|batal`).
4. **Peringatan ke seluruh dimensi**: judul dan langit merah dengan hitung mundur `kehancuranWarningSeconds` (bawaan
   30): "<murid> akan melepaskan <jurus> di X Y Z". Tombol **[Batalkan penghancuran]** berlaku sampai nol.
5. Jurus dilepaskan. Dicatat di tablet Catatan, tablet Segel Merah, dan log server
   (`[Kultivator] Kehancuran: ...`). Pencapaian **Pemecah Dunia**.

### 31.5 Zona terlindung

Jurus yang bentuknya menyentuh zona ini **ditolak**, dan posisi di dalam zona selalu dilewati (juga saat jurus sedang
berjalan):

| Zona | Jarak |
|---|---|
| Prasasti Aula Sekte mana pun, aktif, tertidur, atau diam, milik siapa pun (juga yang pemiliknya luring) | `kehancuranHallSafeRadius` (bawaan 64) blok mendatar |
| Kamu dan setiap pemain (diperiksa di awal dan ulang setiap tick) | `kehancuranPlayerSafeRadius` (bawaan 24) blok |
| Area spawn dunia | radius perlindungan spawn server, minimal 16 |
| Penduduk desa, golem besi, Batu Magnet formasi pelindung yang terdaftar | 32 blok |
| Murid (termasuk yang merapal), hewan peliharaan jinak, binatang roh | 6 blok |
| Pulau utama End | 160 blok dari (0, 0) |
| Chunk yang tidak dimuat | dilewati, tidak pernah dipaksa dimuat |

### 31.6 Tidak pernah disentuh

* Batuan dasar dan semua blok dengan kekerasan < 0.
* **Setiap blok berisi** (peti, tong, tungku, kasur, papan tanda, spawner, suar, podium, spanduk, dan sebagainya).
* Portal dan bingkainya, obsidian, obsidian menangis, deepslate berpenguat, Batu Magnet, jangkar bangkit, prasasti aula.
* Cairan dan blok berisi air, serta setiap blok yang bersebelahan dengan cairan, blok berisi, atau chunk yang tidak
  dimuat (agar air atau lava tidak masuk dan papan tanda tidak lepas).
* Blok di tag datapack `#kultivator:indestructible`.

### 31.7 Cara kerjanya

* Satu jurus berjalan di seluruh server, paling banyak 2 mengantre.
* Posisi diurutkan dari tengah ke luar (bola dan kolom) atau dari atas ke bawah (parit, lubang, cakram) dan dipotong di
  `kehancuranMaxBlocksPerCast` (bawaan 4.096), jadi jurus yang terpotong tetap terlihat seperti kawah.
* Setiap tick server menghapus paling banyak `kehancuranBlocksPerTick` (bawaan 48) blok tanpa pembaruan tetangga: tidak
  ada obor yang lepas, air yang mengalir, atau pasir yang jatuh beruntun. Blok gravitasi bisa melayang sampai
  tersentuh.
* Berhenti sejenak selama server lambat (rata-rata tick > `kehancuranMsptPause`, bawaan 45 ms).
* Tanpa api, tanpa ledakan, tanpa item. **Drop:** tidak ada secara bawaan; dengan `kehancuranDropsPct` 1–10 setiap blok
  yang dihapus punya peluang sebesar itu menjatuhkan isinya (paling banyak 64 tumpuk per jurus), dan jurus seperti itu
  tidak bisa dipulihkan.

### 31.8 Pulihkan

Aturan keselamatan, bukan fitur cerita.
* Setiap jurus menyimpan blok yang dihapusnya di memori selama `kehancuranUndoMinutes` (bawaan 10 menit). Tekan
  **Pulihkan** di tablet Segel Merah atau `/kultivasi kehancuran pulihkan`.
* Blok dikembalikan dengan anggaran per tick yang sama, hanya di tempat yang masih berupa udara, dan hanya untuk jurus
  dengan 0% drop. Pemulihan mengabaikan zona terlindung (ia hanya mengembalikan yang diambil).
* Posisi di chunk yang belum dimuat atau tempat seseorang berdiri ditunda dan kembali ke penyimpanan dengan batas waktu
  aslinya ("Tekan Pulihkan lagi nanti").
* Menutup kunci atau mematikan mode tidak menghentikan pemulihan yang sedang berjalan.
* Penyimpanan ini **hilang bila server dimulai ulang**.

### 31.9 Pengaturan

| Opsi di `config/kultivator.json` | Bawaan | Rentang | Arti |
|---|---|---|---|
| `modeKehancuran` | `false` | – | Kunci 1: server mengizinkan Mode Kehancuran |
| `kehancuranMaxRadius` | 12 | 4–24 | R pada rumus bentuk |
| `kehancuranBlocksPerTick` | 48 | 8–256 | Blok yang diubah per tick |
| `kehancuranMaxBlocksPerCast` | 4096 | 256–16384 | Blok maksimal per jurus |
| `kehancuranDropsPct` | 0 | 0–10 | Persen blok yang menjatuhkan isinya |
| `kehancuranHallSafeRadius` | 64 | 48–256 | Zona aman di sekitar setiap prasasti aula |
| `kehancuranPlayerSafeRadius` | 24 | 16–128 | Zona aman di sekitar setiap pemain |
| `kehancuranMsptPause` | 45 | 30–50 | Berhenti sejenak bila tick server lebih lambat dari ini (ms) |
| `kehancuranWarningSeconds` | 30 | 10–60 | Hitung mundur peringatan |
| `kehancuranUndoMinutes` | 10 | 0–30 | Jendela Pulihkan |

`/kultivasi kehancuran status` menampilkan ketiga kunci, batas-batas ini, jurus yang berjalan, dan sisa jendela pulih.

---

## 32. Pengakuan Langit: menjadi Dewa Abadi

Saat kamu mencapai **Dewa Abadi** dan belum pernah diakui, langit menjalankan peristiwa **Pengakuan Langit** sekali saja.
Peristiwa ini dimulai beberapa detik setelah tribulasimu berakhir, ketika kamu hidup, tidak menunggang, tidak sedang
menerobos, tidak menghadapi Iblis Hati, dan tidak sedang Latih Tanding.

**Jalannya (30 detik):**
1. **Langit menggelap** — dunia meredup di sekitarmu, bayangan gelap berputar mendekat.
2. **Pilar cahaya emas** turun dari 40 blok di atas, menghantam tanah dengan kilat cahaya, lalu memancarkan cincin
   cahaya yang meluas setiap detik.
3. **Langit terbuka** — piringan awan emas tersibak di atasmu, berkas cahaya turun dari tepinya, dan kelopak bunga
   ceri berguguran. Butir cahaya pelangi naik dari teratai cahaya di bawah kakimu: rasa melayang, padahal tubuhmu tidak
   digerakkan sama sekali.
4. **Suara Langit** — judul "Langit mengakui seorang Dewa Abadi" muncul, bergema bagi pemain di sekitar, diiringi
   lonceng, suar, akor harpa blok nada, dan totem.
5. **Puncak** — ledakan cahaya pelangi, dan seluruh server diberi tahu (bila `announceBreakthroughs` hidup).

Peristiwa batal **tanpa hukuman** bila kamu keluar dunia, mati, atau pindah dimensi. Bila kamu mati atau pindah
dimensi, ia dimulai lagi sekitar 20 detik kemudian; bila kamu keluar dunia, beberapa detik setelah kamu masuk kembali.

**Anugerah Dewa Abadi** (permanen):
* **Kebal Penyimpangan Qi** — tidak ada kegagalan atau Iblis Hati yang bisa menyebabkannya lagi. Penyimpangan yang
  sedang berjalan dibersihkan saat kamu diakui.
* **Regenerasi abadi** — pulih 1 nyawa setiap 5 detik selama nyawamu belum penuh.
* **Aura pelangi** dan kelopak bunga yang berguguran, terlihat oleh semua pemain (sebelum diakui, aura Dewa Abadi
  masih berwarna ranah).
* Pencapaian **Diakui Langit**.

Catatan: pemulihan qi-mu tetap 0,4% qi maksimum per detik seperti biasa; di ranah ini jumlahnya sudah sangat besar,
jadi tidak ada anugerah qi tambahan.

---

## 33. Tampilan, Kualitas Efek, dan kosmetik

Semua efek digambar di sisi klien dengan partikel dan lapisan render standar (aman untuk Sodium, Iris, ImmediatelyFast,
EMF/ETF), disesuaikan dengan **Kualitas Efek**:

| Kualitas Efek | Jumlah partikel | Keterangan |
|---|---|---|
| Minimal | ×0,25 | untuk komputer lemah |
| Normal | ×1 | bawaan |
| Maksimal | ×2 | ditambah hiasan ekstra (spiral, cincin tambahan, kembang api, dst.) |

Atur di tab **Pengaturan** gulungan; tersimpan di `config/kultivator-client.json` (hanya di komputermu).

**Kosmetik** (terlihat oleh semua orang, bisa dimatikan sendiri dengan `/kultivasi visual <bagian> on|off` atau koin
giok di tab Pengaturan):

| Bagian | ID | Tampilan |
|---|---|---|
| Aura | `aura` | Satu motif per ranah, warna ranahmu, makin rapat setiap tahap: Pemurnian Qi kabut tipis; Pembangunan Fondasi butir cahaya naik dari kaki; Inti Emas + tiga butir emas mengitari pinggang; Jiwa Baru Lahir + bayi jiwa bersila dengan lingkaran cahaya di atas kepala; Transformasi Dewa lidah api qi; Penyatuan Dao dua komet yin-yang; Kenaikan Abadi pilar cahaya (bertambah tiap 2 tingkat); Dewa Abadi lima butir emas; Dewa Abadi yang telah diakui: aura **pelangi** dan kelopak bunga. Maksimal menambah kilau batang End mulai Jiwa Baru Lahir |
| Jubah | `robe` | Mulai Pemurnian Qi: jubah di atas model pemain (dengan rok), warnanya sesuai Dao (warna ranah sebelum memilih Dao); hiasan tepi emas mulai Inti Emas, sulaman lambang di punggung mulai Jiwa Baru Lahir |
| Pedang di punggung | `back_sword` | Mulai Pemurnian Qi: pedang terbaikmu tersandang diagonal di punggung (disembunyikan saat terbang di atasnya atau saat pedang itu dipegang) |
| Mata bercahaya | `eyes` | Mulai Jiwa Baru Lahir: mata menyala warna Dao/ranah (bersinar dengan shader), lebih lembut saat meditasi, berkedip merah darah saat Penyimpangan Qi |
| Lingkaran Dao | `dao_circle` | Susunan rune bercahaya berputar di tanah di bawah kakimu saat bermeditasi, menerobos, atau membentangkan Wilayah Dao (lebih besar dan cepat). Normal menambah cincin teratai, Maksimal menambah rune yang naik di sekeliling tubuh |

Efek lain yang selalu tampil: pose meditasi bersila, pedang sungguhan terbaring di bawah kaki saat Pedang Terbang,
kilau dan garis emas Wujud Dharma, awan dan petir tribulasi, garis formasi pengumpul qi, aura binatang roh, kubah
Formasi Pelindung, dan sinematik Pengakuan Langit. Lapisan tubuh tidak pernah digambar untuk pandangan orang pertamamu
sendiri, jadi layar tetap bersih.

### 33.1 Tampilan Fase 6: murid, aula, dan acara

Semua tampilan Fase 6 juga hanya partikel dan lapisan render di komputermu, ikut Kualitas Efek (Minimal mengurangi
partikel, Maksimal menambah hiasan). Efek yang terus menyala hanya digambar dalam 32 blok (bahtera hantu 64 blok).

| Apa | Tampilan |
|---|---|
| Nama di atas kepala murid | "[Jenjang] Nama · Ranah" dalam warna trim jenjang, nama dalam warna alam jiwa; setelah bintang pertama gelar tahapnya (misalnya "Planet ★31"); tanda merah **[Segel Merah]** untuk murid yang segelnya dilepas. Calon: "Calon · ???" |
| Jubah murid | trim per jenjang: abu-abu (Tercatat), putih (Luar), biru (Dalam), giok (Inti), ungu (Tetua), putih-emas (Tetua Agung) |
| Calon, Murid Tepi Utara, bayangan Alam Hampa | masing-masing berwarna sendiri; tubuh roh di Alam Hampa, bayangan, dan Klon Ilahi tembus pandang |
| Tugas sekte | pose memikul ember, berlutut, mengaduk, menabur pakan; percikan air saat hasil dihitung |
| Bakat dan fisik | butir aura dalam warna jiwa, bola Api Surgawi, kabut Tubuh Racun, wujud naga ungu Taixu, kilau Tubuh Emas, aksara mata Pupil Ganda di atas monster (hanya terlihat oleh Guru), urat gerbang Nadi Dewa Sesat |
| Pusaka Natal | tergambar di punggung: kilau perak (lipatan 3), emas (lipatan 9), mengitari pemiliknya (lipatan 14) |
| Jurus Pamungkas dan Jurus Pemusnah | partikel masing-masing jurus (kubah pedang, sambaran emas, kubus glif, teratai api, pusaran, aksara palu, dan sebagainya) |
| Bintang Abadi | titik bintang mengorbit dada (9 per cincin; 1 cincin di Minimal, sampai 3 di Maksimal), hiasan tahap (butir emas Meteor, inti emas, aura matahari), Lampu Jiwa, aura putih-emas Penginjak Langit |
| Aula | pilar giok saat didirikan dan naik tingkat, kepulan kabut kedatangan calon, tiga tingkat pendakian dan pilar Duan dalam warna jiwa, cincin giok penerimaan, kilau jubah saat naik jenjang, aksara pemulihan seni (kelopak plum untuk seni pertama), asap abu aula yang tertidur, spiral token; warna lapis Formasi Agung |
| Acara | awan langkah saat tim pergi dan pulang, cincin duel dan percikan per pukulan, pusaran portal Alam Hampa, angin merah Tanah Terlarang, bahtera hantu di atas aula, butir api jiwa di menara |
| Jalan Abadi | pertanda dan sambaran bencana (seperti tribulasi), aksara segel, cahaya giok antar dantian pewaris, bayangan guru tua di cincin, pilar emas Murid Melampaui Guru, busur kuning Tahan Petir, segel Hu Fa, pilar Fondasi Dao |
| Mode Kehancuran | langit merah dan abu selama hitung mundur, garis pratinjau bentuk 15 detik, debu hantaman, kilau biru saat pemulihan |

---

## 34. Layar kultivasi (gulungan giok)

Tekan `'`. Gulungan memiliki loh giok vertikal di sisi kiri:

| Tab | Isi |
|---|---|
| **Status** | Lingkaran dantian besar (cincin kultivasi, qi, Qi Tertimbun), ranah, statistik, Penyimpangan Qi, racun pil, tingkat Alkimia; jimat Meditasi, Terobosan, Uji Akar |
| **Akar & Dao** | Pentagram lima elemen yang menyala sesuai akarmu, segi delapan Bagua delapan Dao (klik untuk membaca/memilih) |
| **Teknik** | Kartu segel semua teknik: terkunci/terbuka, biaya, jeda; klik untuk memilih |
| **Pengaturan** | Kualitas Efek, kosmetik, HUD, terobosan Hardcore (klik dua kali dalam 3 detik) |
| **Bantuan** | Ringkasan aturan dan tombol (lihat di bawah) |

**Topik Bantuan Fase 6** (setelah topik lama): Aula Sekte, Hari Penerimaan Murid, Jenjang Sekte, Poin Kontribusi, Tugas
Sekte dan Lumbung Sekte, Misi/Utusan/Tanah Terlarang, Turnamen dan Perang Seratus Dinasti, Alam Dewa Hampa/Menara Langit
Membara/Latihan Khusus, Bakat/alam jiwa/fisik langka, Pusaka Natal/Kitab Abadi Tak Mati/Api Surgawi, Bintang Abadi,
Ikatan/Segel Jiwa/Hu Fa/pewaris, dan Mode Kehancuran (MATI secara bawaan). Sumber cerita tiap fitur dibaca di tooltip
gulungan aula dan tab murid, dan selengkapnya di bagian 20–31 panduan ini.

Gulungan lain di Fase 6: **gulungan murid** (klik kanan muridmu, [19.2](#192-gulungan-murid)) dan **gulungan aula**
(klik kanan prasastimu, [20.6](#206-gulungan-aula)).

---

## 35. Pencapaian

Semua pencapaian ada di tab **Kultivasi** pada layar Pencapaian (Advancements). Bingkai: tugas, *tujuan*, **tantangan**.

| Kelompok | Pencapaian |
|---|---|
| Ranah | Tarikan Qi Pertama (Pemurnian Qi), Meletakkan Fondasi, *Inti Emas Terbentuk*, *Lahirnya Jiwa*, *Bukan Lagi Fana* (Transformasi Dewa), **Menyatu dengan Dao**, **Tangga Menuju Langit** (Kenaikan Abadi), **Melampaui Langit** (Dewa Abadi), Satu Langkah Lebih Tinggi (naik tahap), *Ujung Jalan* (Dewa Abadi tahap Puncak) |
| Terobosan | Kebangkitan, Menembus Leher botol, *Menantang Langit* (tribulasi pertama), *Fondasi Tanpa Cela* (kualitas Sempurna), **Dikasihi Langit** (kualitas Surgawi), Penyimpangan Qi, *Dibaptis Api Neraka* (Nether), *Menatap Kehampaan* (End), **Dewa Abadi** (tribulasi terakhir), *Veteran Tribulasi* (10 tribulasi), *Jalan Tanpa Cela* (semua terobosan setelah Kebangkitan Sempurna/Surgawi, sejak Pembangunan Fondasi sampai minimal Inti Emas), **Diakui Langit** (Pengakuan Langit) |
| Meditasi | Hati yang Tenang, Tanah Berkah (tempat spiritual), *Momen Pencerahan*, Terkuras Habis (urat menipis), Mengumpulkan Qi (formasi kecil), *Takhta Emas* (formasi agung), **Formasi Bintang Surgawi**, *Satu Jam Keheningan* (total 60 menit meditasi) |
| Pil & Alkimia | Menelan Eliksir, Setiap Obat Mengandung Racun, *Eliksir Terlarang*, Membuka Tungku (pil alkimia pertama), Asap Hitam (pemurnian gagal), *Pil Tanpa Cela* (mutu Sempurna), **Mahaguru Tungku Pil** (tingkat alkimia 10), **Pil Para Dewa** (Pil Dewa) |
| Akar | Batu yang Mengetahui, *Murni Bagai Satu Nada* (satu elemen), **Satu di Antara Sepuluh Ribu** (elemen mutan), **Pilihan Langit** (akar Surgawi), Membasuh Sumsum |
| Dao | *Menapaki Dao*, Pemahaman Kecil, Pemahaman Besar, *Pemahaman Sempurna*, **Wilayah Dao**, Warisan Para Leluhur (kitab pertama), Membaca yang Tersirat |
| Teknik | Teknik Pertama, Melangkah di Atas Awan, *Pedang Menunggang Angin*, Mata Jiwa Terbuka, *Telapak Sang Buddha*, *Dharma yang Menjulang*, **Di Dalam Wilayahku**, *Perpustakaan Teknik* (10 teknik), Seni Dao Pertama, *Rahasia Kitab Teknik*, **Hujan Seribu Pedang**, **Sayap Api Phoenix**, **Murka Langit**, *Seratus Tumbang* (100 monster dengan teknik) |
| Iblis Hati | Menghadapi Bayangan, *Hati Dao Teguh*, Hati yang Goyah, *Iblis Hati Ditaklukkan*, **Setenang Danau Gunung** (3 iblis) |
| Binatang Roh | Binatang Roh, Kebangkitan Garis Darah, **Binatang Langit** (tingkat 3), *Serigala dan Rubah* |
| Formasi | Perisai Giok, *Melimpah Qi* (daya penuh 2 jam), *Tak Tertembus* (20 monster terusir) |
| Sekte | Leluhur Pendiri, Sesama Penganut Dao, *Sekte yang Berjaya* (5 anggota), Pelajaran Bersahabat (menang Latih Tanding) |
| Murid | Lahirnya Seorang Guru (murid pertama), *Lima Murid, Satu Jalan*, Mewariskan Dao (mengajarkan teknik), Murid yang Terbangun (Kebangkitan murid), *Inti Emas Sang Murid*, *Kembali dari Mata Air Kuning* (membangkitkan murid dengan Papan Jiwa) |
| Aula Sekte | *Gerbang Sekte Terbuka* (mendirikan aula), Hari Penerimaan Murid (calon pertama diterima), Tekad Mengalahkan Bakat (Murid Tercatat), Apa Pun Akarnya (Token Kenaikan Abadi), Naik Pagoda (kenaikan jenjang pertama), **Yang Terkuat Duduk Tertinggi** (Tetua Agung), *Sekte Telah Kembali* (Tingkat 5), **Sekte di Bawah Langit** (Tingkat 9), *Dari Lembar yang Tercecer* (seni yang hilang dipulihkan) |
| Kegiatan sekte | Melangkah Keluar Gerbang (ekspedisi pertama), *Alam Rahasia Ditaklukkan*, *Keluar dari Angin Merah Darah* (Tanah Terlarang), *Nomor Satu Daftar Kuat* (juara turnamen), **Penumpang Bahtera Purba**, *Juara Seratus Dinasti*, Tangga Batu (lantai 10 Alam Dewa Hampa), **Jenius Batas Suci** (lantai 50), Satgas Pencuri Ayam (tersembunyi) |
| Bakat & kekuatan murid | Terlahir Berbeda (fisik langka), *Pengemis di Gerbang*, *Nadi Dewa Sesat*, *Jurus Pamungkas*, **Api Guru dan Murid** (Fusi), *Pelahap Api* (Api Surgawi), *Pusaka Sembilan Lipatan*, **Payung Abadi** (lipatan 14), **Darah Abadi**, **Sayap Sang Kunpeng** |
| Jalan Abadi | Segel Jiwa, Pelindung Dao Murid (Hu Fa sampai berhasil), *Fondasi Dao Langit*, **Fondasi Sempurna**, Bintang Pertama Terpadatkan, *Bencana Terlewati*, **Penginjak Langit** (ketujuh bencana), *Lampu Jiwa Padam*, *Pewaris Ditetapkan*, **Penguasa Sembilan Segel**, *Guru di Dalam Cincin*, **Murid Melampaui Guru** |
| Mode Kehancuran | Sumpah Kehancuran (butuh izin server), **Pemecah Dunia** (Jurus Pemusnah yang mengubah blok sungguhan) |

Resep yang terbuka di buku resep: Prasasti Aula Sekte (saat kamu mendapat Batu Magnet), serta Giok Segel Jiwa dan Cincin
Jiwa Guru (saat kamu mendapat Serpihan Gaung).
Resep Gulungan Penerimaan Murid sudah dihapus sejak Fase 6.

---

## 36. Berkas konfigurasi

### `config/kultivator.json` (server / dunia pemain tunggal)

| Opsi | Bawaan | Arti |
|---|---|---|
| `xpMultiplier` | `1.0` | Mengalikan semua kultivasi |
| `hardcoreForced` | `false` | Semua pemain dalam mode Hardcore (gagal terobosan = turun satu ranah) |
| `daoProtectorEvent` | `true` | Monster Pelindung Dao datang saat tribulasi |
| `tribulationDamageScale` | `1.0` | Mengalikan kerusakan sambaran tribulasi (tetap tidak pernah mematikan) |
| `heartDemonTrials` | `true` | Ujian Iblis Hati sebelum beberapa terobosan, dan Iblis Hati tekanan |
| `protectiveFormations` | `true` | Formasi Pelindung bisa diberi daya |
| `announceBreakthroughs` | `true` | Umumkan terobosan mulai Inti Emas dan Pengakuan Langit ke seluruh server |
| `sectHall` | `true` | Prasasti Aula Sekte bisa mendirikan aula dan calon murid berdatangan. Bila `false`, prasasti baru tetap diam |
| `recruitmentIntervalDays` | `3` (1–15) | Hari Penerimaan Murid setiap sekian hari game |
| `sectEvents` | `true` | Acara sekte terjadwal: Kompetisi Daftar Kuat, Tanah Terlarang, Perang Seratus Dinasti |
| `tournamentIntervalDays` | `7` (3–30) | Kompetisi Daftar Kuat setiap sekian hari game |
| `forbiddenGroundIntervalDays` | `15` (5–60) | Tanah Terlarang Berwarna Darah terbuka setiap sekian hari game |
| `modeKehancuran` dan `kehancuran...` | `false` dan lainnya | Mode Kehancuran: lihat [31.9](#319-pengaturan) |

Nilai yang hilang diisi bawaan, nilai di luar rentang dipotong ke batasnya, dan berkas ditulis ulang saat server mulai.
`/kultivasi kehancuran izin on|off` (operator) juga menulis berkas ini.

### `config/kultivator-client.json` (komputermu)

| Opsi | Nilai | Arti |
|---|---|---|
| `effect_quality` | `minimal`, `normal`, `maximal` | Kualitas Efek |
| `hud` | `true` / `false` | HUD qi di kiri bawah |

Keduanya diatur dari tab Pengaturan gulungan.

---

## 37. Janji keamanan blok

Kultivator dirancang agar aman di dunia mana pun, termasuk dunia bersama.

> **Satu-satunya pengecualian: Mode Kehancuran.** Mode ini **MATI secara bawaan** dan baru mengubah blok bila ketiga
> kuncinya dibuka dengan sengaja (izin server, Sumpah Kehancuran, Lepaskan Segel), lalu sebuah jurus diperintahkan,
> dipratinjau, dikonfirmasi, dan diumumkan dengan hitung mundur. Zona terlindung, batas blok, jeda saat server lambat,
> dan tombol Pulihkan tetap berlaku. Lihat [bagian 31](#31-mode-kehancuran-peringatan). Semua aturan di bawah berlaku
> penuh selama mode itu mati, dan untuk setiap hal lain di mod ini juga saat mode itu menyala.

* **Tidak ada blok yang dipasang, dirusak, dibakar, diganti, atau diubah** oleh efek, teknik, peristiwa, makhluk,
  atau formasi mod ini. Formasi, tungku alkimia, dan tempat spiritual hanya **membaca** blok. Tungku alkimia tidak
  pernah mengubah air di Kuali atau Api Unggun.
* **Tidak ada ledakan**, tidak ada blok api, dan **tidak pernah entity petir** (petir tribulasi, Telapak Petir, Petir
  Surgawi, dan Petir Bulan hanyalah partikel dan suara, jadi tidak ada penangkal petir yang menyala atau tembaga yang
  berubah).
* **Cuaca dan waktu tidak pernah diubah.**
* Api hanya pada **tubuh** makhluk (yang tidak pernah menyalakan blok), dan tidak pernah di dekat salju bubuk atau
  Kuali berisi air/salju bubuk: makhluk yang terbakar akan mencairkan atau mengurangi isinya, jadi apinya dipadamkan
  lebih dulu.
* Tidak ada proyektil vanilla yang mengenai blok (bola api, tengkorak wither, wind charge, TNT, mutiara ender, ramuan
  lempar).
* Tidak ada efek Weaving, Oozing, Infested, atau Wind Charged.
* Mod tidak pernah memunculkan creeper, ghast, blaze, wither, ravager, enderman, penyihir, atau silverfish; zombi yang
  dimunculkan tidak bisa mendobrak pintu.
* Makhluk yang dimunculkan atau dilempar mod (Pelindung Dao, monster yang terlempar teknik, Binatang Roh) tidak
  menginjak pelat tekan atau tali jebakan, tidak menyalakan bijih redstone, dan tidak pernah melewati portal (yang
  bisa membangun portal keluar atau panggung obsidian End).
* Teknik dan Binatang Roh tidak menyerang Wither (setiap pukulan membuatnya menghancurkan blok di sekitarnya) dan tidak
  pernah menggeser Naga Ender (ia menghancurkan blok yang ditabraknya); Naga Ender tetap bisa dilukai.
* Tribulasi dan Iblis Hati **tidak pernah membunuhmu**: kegagalan berarti luka (Penyimpangan Qi), bukan kematian.
  Bila Iblis Hati melemparmu ke kehampaan, kamu dikembalikan ke tempat aman dan ujiannya berakhir sebagai kekalahan.
* Murid tidak membuka pintu, tidak memungut barang, tidak menekan pelat tekan atau tali jebakan, tidak melewati
  portal, dan teknik mereka mengikuti aturan yang sama dengan teknikmu. Papan Jiwa yang tergeletak juga tidak menekan
  pelat dan tidak melewati portal.
* Teknik hanya mengenai monster. Dalam Latih Tanding, teknik tidak pernah melempar atau menarik lawan, dan pemain
  yang jatuh ke kehampaan dikembalikan ke lawannya (duel berakhir kalah).

**Tambahan Fase 6 (sekte dan murid):**
* **Prasasti Aula Sekte** adalah blok biasa yang hanya **kamu** pasang dan tambang. Pemindaian bangunan, ruang menara,
  dan ayam peternakan hanya **membaca** blok dan menghitung makhluk.
* Tugas sekte tidak menyentuh blok atau tanaman: Kebun tidak memanen, Dapur tidak menyalakan api, Pikul Air tidak
  mengambil air. Lumbung Sekte adalah gudang maya, bukan peti.
* Calon murid, Murid Tepi Utara, bayangan Alam Hampa, Klon Ilahi, monster Latihan Khusus, dan Pengawal Dao murid
  mengikuti aturan makhluk yang sama: tidak merusak blok, tidak menekan pelat, tidak melewati portal. Calon dan bayangan
  tidak pernah disimpan sebagai makhluk.
* Tim yang pergi misi hanyalah data; tidak ada yang berjalan atau menggali di dunia.
* Petir Bencana Bintang Abadi, Petir Pengusir Kejahatan, dan langit merah Murka Raja Neraka hanyalah partikel dan suara.
  Teratai Api adalah ledakan partikel, bukan ledakan sungguhan. Api Surgawi, Wujud Naga, dan Teratai Api hanya membakar
  **tubuh** monster.
* Semua Jurus Pamungkas, Sayap Kunpeng, Formasi Agung Sekte, dan bentuk aman blok Jurus Pemusnah hanya mengenai monster
  lewat aturan sasaran yang sama dengan teknikmu. Pemain, hewan peliharaan, penduduk desa, dan murid tidak terluka.
* Duel sekte, Alam Dewa Hampa, Latihan Khusus, ekspedisi, Tanah Terlarang, dan Bencana Bintang Abadi **tidak pernah
  membunuh murid**: kegagalan berarti luka atau Penyimpangan Qi. Satu-satunya pengecualian ada di Tanah Terlarang saat
  Hardcore-mu menyala: murid yang gagal kembali sebagai Papan Jiwa yang bisa dibangkitkan.
* Bahtera Purba, angin merah, portal Alam Hampa, dan cincin duel adalah partikel; tidak ada blok yang dipasang.
* Kode yang mengubah blok hanya ada di satu tempat, mesin Mode Kehancuran, dan mesin itu tidak berjalan selama ketiga
  kunci belum terbuka.
