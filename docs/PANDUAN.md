# Panduan Pemain Kultivator

Panduan lengkap mod **Kultivator** (Minecraft 26.3, Fabric). Di mod ini **kamu sendiri** yang berkultivasi seperti
tokoh novel xianxia: memurnikan qi, menembus ranah demi ranah dari Manusia Fana sampai Dewa Abadi, bertahan dari
Tribulasi Langit, menapaki Jalan Dao, menguasai teknik, terbang di atas pedang, meracik pil, menghadapi Iblis Hati,
membangkitkan binatang roh, membangun formasi, mendirikan sekte, dan membimbing murid.

Semua angka di panduan ini diambil langsung dari kode mod. Angka kultivasi (XP) selalu ditampilkan sebagai bilangan
bulat di dalam game.

> **Janji keamanan blok:** tidak ada satu pun efek, teknik, peristiwa, makhluk, atau formasi dari mod ini yang
> membakar, merusak, memasang, atau mengubah blok. Lihat [bagian terakhir](#25-janji-keamanan-blok).

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
20. [Pengakuan Langit: menjadi Dewa Abadi](#20-pengakuan-langit-menjadi-dewa-abadi)
21. [Tampilan, Kualitas Efek, dan kosmetik](#21-tampilan-kualitas-efek-dan-kosmetik)
22. [Layar kultivasi (gulungan giok)](#22-layar-kultivasi-gulungan-giok)
23. [Pencapaian](#23-pencapaian)
24. [Berkas konfigurasi](#24-berkas-konfigurasi)
25. [Janji keamanan blok](#25-janji-keamanan-blok)

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
| `/kultivasi murid list` | Daftar bernomor muridmu (lihat [Murid](#19-murid)) |
| `/kultivasi murid panggil` | Semua muridmu yang termuat datang ke sebelahmu, juga dari dimensi lain |
| `/kultivasi murid ikut` / `tinggal` / `meditasi` / `bantu` | Perintah untuk murid yang kamu tatap atau yang terdekat dalam 16 blok |
| `/kultivasi murid lupakan <nomor> [konfirmasi]` | Membebaskan tempat murid hidup yang raganya hilang untuk selamanya |

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
* Dewa Abadi yang telah menjalani [Pengakuan Langit](#20-pengakuan-langit-menjadi-dewa-abadi) **kebal** terhadap
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
botol, Qi Tertimbun), mempelajari teknik yang kamu ajarkan, menelan pil darimu, dan bertarung di sisimu. Seorang Guru
membimbing paling banyak **5 murid hidup** sekaligus.

### 19.1 Menerima murid

* **Gulungan Penerimaan Murid** (resep tanpa pola): Kertas + Batangan Emas + Serpihan Batu Kecubung + Kantong Tinta.
* Gunakan gulungan itu: seorang murid muncul di depanmu (atau di sampingmu bila di depan tidak ada tanah aman) sebagai
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
| **Perintah** | Empat perintah (lihat 19.3) |
| **Pil & Terobosan** | Pil di tanganmu, jeda pil, tombol Terobosan, pilihan Dao, dan tombol Lepaskan murid (klik dua kali) |

Gulungan memperbarui isinya sendiri setiap 5 detik selama terbuka dan tertutup bila murid lebih dari 16 blok darimu.

### 19.3 Perintah

| Perintah | Perilaku |
|---|---|
| **Ikut** (bawaan) | Berjalan bersamamu, menyusul dengan teleportasi bila tertinggal lebih dari 24 blok. Melawan apa pun yang menyerangmu atau menyerangnya |
| **Tinggal & Jaga** | Menjaga tempat perintah diberikan: melawan monster dalam 12 blok, lalu kembali ke posnya. Monster netral yang tenang (enderman, piglin, piglin zombi, Warden) dibiarkan selama mereka tidak menyerang |
| **Meditasi** | Duduk bersila di tempat itu dan berkultivasi **×1,5**. Hanya membalas bila diserang |
| **Bantu** | Ikut bersamamu dan menyerang apa yang kamu serang serta apa yang menyerangmu |

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
**0,6 × mutu akarnya** (Sampah ×0,6 … Surgawi ×1,7) dan pengali server.

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
* Semua efek terobosan hanyalah partikel dan suara: tidak ada entity petir, cuaca tidak berubah, blok tidak tersentuh.

### 19.8 Gugur dan Papan Jiwa

* Murid yang gugur meninggalkan **Papan Jiwa Murid** di tempatnya: bercahaya menembus dinding, tidak pernah menghilang,
  kebal api, lava, ledakan, dan kaktus. Kamu diberi tahu koordinat dan dimensinya.
* Bila ia gugur di atas kehampaan (jatuh dari dunia, di tepi pulau End), papannya terbang ke tasmu. Bila kamu sedang
  luring, papan muncul di tempat terakhir ia berdiri dengan aman (atau di posnya, atau di titik muncul dunia), jadi
  papannya tidak pernah hilang.
* Gunakan papan itu untuk memanggil jiwanya kembali di sebelahmu, dengan nyawa penuh dan seluruh kultivasi serta
  teknik yang sudah dipelajari. Hanya Gurunya yang bisa, dan batas 5 murid hidup tetap berlaku.
* Papan dari kehidupan sebelumnya hanyalah gema: jiwa yang sama tidak bisa dibangkitkan dua kali.
* Murid yang gugur saat kamu luring tercatat sebagai gugur begitu kamu masuk lagi, juga setelah server dimulai ulang.
* **Melepaskan** murid (tab Pil & Terobosan, klik dua kali) membuatnya membungkuk, berpamitan, dan pergi untuk
  selamanya tanpa papan; tempatnya langsung kosong.

### 19.9 Perintah obrolan

| Perintah | Fungsi |
|---|---|
| `/kultivasi murid list` | Daftar bernomor semua muridmu: nama, ranah, perintah, dan di mana mereka (jarak, di dimensi lain, jauh dan tidak termuat, atau gugur) |
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

## 20. Pengakuan Langit: menjadi Dewa Abadi

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

## 21. Tampilan, Kualitas Efek, dan kosmetik

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

---

## 22. Layar kultivasi (gulungan giok)

Tekan `'`. Gulungan memiliki loh giok vertikal di sisi kiri:

| Tab | Isi |
|---|---|
| **Status** | Lingkaran dantian besar (cincin kultivasi, qi, Qi Tertimbun), ranah, statistik, Penyimpangan Qi, racun pil, tingkat Alkimia; jimat Meditasi, Terobosan, Uji Akar |
| **Akar & Dao** | Pentagram lima elemen yang menyala sesuai akarmu, segi delapan Bagua delapan Dao (klik untuk membaca/memilih) |
| **Teknik** | Kartu segel semua teknik: terkunci/terbuka, biaya, jeda; klik untuk memilih |
| **Pengaturan** | Kualitas Efek, kosmetik, HUD, terobosan Hardcore (klik dua kali dalam 3 detik) |
| **Bantuan** | Ringkasan aturan dan tombol |

---

## 23. Pencapaian

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

---

## 24. Berkas konfigurasi

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

Nilai yang hilang diisi bawaan dan berkas ditulis ulang saat server mulai.

### `config/kultivator-client.json` (komputermu)

| Opsi | Nilai | Arti |
|---|---|---|
| `effect_quality` | `minimal`, `normal`, `maximal` | Kualitas Efek |
| `hud` | `true` / `false` | HUD qi di kiri bawah |

Keduanya diatur dari tab Pengaturan gulungan.

---

## 25. Janji keamanan blok

Kultivator dirancang agar aman di dunia mana pun, termasuk dunia bersama:

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
