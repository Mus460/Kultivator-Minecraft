# Audit UX, Onboarding & Teks (audit:ux)

Tanggal: 2026-10-22. Sifat: **hanya membaca**. Tidak ada berkas proyek yang diubah selain laporan ini dan
`docs/progres/audit-ux.md`, dan Gradle tidak dijalankan. Yang diperiksa: hub (DESIGN 33), tombol, Akses Cepat,
semua layar, topik Ensiklopedia, kata-kata Kitab Panduan dan Jalan, pesan obrolan, HUD, notifikasi, Pengaturan,
suara/teks suara, istilah Indonesia di semua `lang-src`, paritas Inggris, CheatHelp, serta kecocokan README/PANDUAN
dengan kode. Acuan kebenaran adalah kode; DESIGN hanya dipakai sebagai rencana.

Kode "Sekte yang Hidup" (DESIGN 36, `dev.kultivator.sekte.**`) sudah masuk commit `40d06e1`, tetapi masih dikerjakan
oleh workflow lain. Temuan di bagian itu ditandai **[SEDANG DIBANGUN]**.

## Ringkasan

| Tingkat | Jumlah |
|---|---|
| KRITIS | 0 |
| TINGGI | 3 |
| SEDANG | 10 |
| RENDAH | 8 |
| **Total** | **21** |

**Yang sudah sehat (dicek ulang):**
* `lang-src`: ada 14 933 kunci di 121 pasangan fragmen. Tidak ada kunci ganda, dan kunci id/en identik di setiap
  fragmen. Semua beda argumen `%s` hanyalah urutan posisi (`%1$s`/`%2$s`) yang sah.
* Hanya 9 dari 343 event `voice.*` yang tidak punya teks, dan semuanya bunyi tanpa kata (`grunt`, `demon.growl`,
  `boss.roar`). Baris suara Sekte 36 (`sulk`, `rebel`, `mourn`, `report`, `boss.sect_master`) sudah ada di `sounds.json`.
* Kredit VOICEVOX mencakup semua penutur (`voice_ui` credits.0-7, `docs/CREDITS.md`).
* Tidak ada pelanggaran keamanan blok yang terlihat dari sisi UI.
* Setiap guide check `adv:*` punya pemberi di kode (sesi 1).
* Pengaturan memang hanya satu (`SettingsRows`: 5 grup). Saklar duel di `DuelScreen` menulis nilai tersimpan yang sama.

## Tabel temuan

| # | Tingkat | Area | Ringkas | Bukti utama |
|---|---|---|---|---|
| U1 | TINGGI | Hub | Bagian SEKTE berisi 16-17 kartu tanpa gulir dan meluap di 320×240 dan di GUI skala 4; rencana 37 menambah kartu ke-18 **[SEDANG DIBANGUN]** | `HubCardsPage.java:183-196, 235-256`; `HubCards.java:14-16` |
| U2 | TINGGI | Onboarding | Pelacak HUD hilang di masa sepi, dan angka segelnya salah setelah bab 5 | `GuideTracker.java:86-97, 150, 158, 190` |
| U3 | TINGGI | Onboarding/teks | "Dirikan sekte" punya dua arti. Jalan dan Ensiklopedia menyuruh `/sect create` (kelompok pemain, tanpa murid) yang ikut mencentang langkah dan memutar suara Langit. Memasang Aula Sekte yang sungguhan tidak mencentang apa pun | `goals.id_id.json:43`; `screen.id_id.json:170`; `SectCommands.java:194-195`; `Guide.java:99`; `HubCardsPage.java:186` |
| U4 | SEDANG | Bantuan | Ensiklopedia tidak punya topik Duel Pedang maupun 10 modul Sekte 36, dan 14 layar baru tidak punya tautan "?" | `HelpPage.java:39-54` |
| U5 | SEDANG | Bantuan | Topik lama bertentangan dengan kode baru: Turnamen 60 dtk vs 90 dtk, Misi tanpa Misi Cerita, Sekte tanpa Duel Pedang **[sebagian SEDANG DIBANGUN]** | `sectevent.id_id.json:375,377`; `TurnamenConfig.java:33`; `BabakDuel.java:97` |
| U6 | SEDANG | Tombol/teks | `[[key:peta]]`, `[[key:parry]]`, `[[key:duel_camera]]` tampil sebagai "tidak diikat" di tengah kalimat | `KeyHints.java:36`; `PetaClient.java:25`; `DuelKeys.java:21,24` |
| U7 | SEDANG | HUD | Koin "HUD Qi (kiri bawah)" ikut menyembunyikan 7 HUD lain, termasuk peringatan tribulasi, sementara 7 HUD baru tidak terpengaruh | `SettingsRows.java:190`; `TribulationHud.java:46`; `DimensiHud.java:33` |
| U8 | SEDANG | Istilah | Nama ranah tidak konsisten: "Abadi Sejati" dan "Dewa Abadi Sejati" untuk Dewa Abadi, "Pembentukan Fondasi" untuk Pembangunan Fondasi | `aura_high:11`, `tempa:168`, `hub:68`, `dunia_kota:523`, `aura:8` |
| U9 | SEDANG | Istilah | Penggantian nama baru setengah jalan: Cincin Segel → Rangkai Segel, dan Mode Kehancuran → Kehancuran Diri (66 lawan 6). Destroyer Arts punya dua nama | `teknik.id_id.json:159`; `PANDUAN.md:221`; `hub:44` |
| U10 | SEDANG | Dokumen | PANDUAN 62 masih bilang 14 bab / 124 langkah / 84 centang otomatis (kode: 16 / 153 / 112), dan tabel hub PANDUAN tanpa kartu fase 34-36 | `PANDUAN.md:8448-8470, 200-209`; `GuideChapters.java:43` |
| U11 | SEDANG | Hub | Kartu Sekte 36 tidak punya gembok tanpa Aula, sehingga jawaban klik tiap kartu berbeda-beda **[SEDANG DIBANGUN]** | 8× `HubCards.register(..., null, ...)`; `PANDUAN.md:210-211` |
| U12 | SEDANG | Istilah | Dua "Misi", dua "Turnamen", tiga "duel" dengan nama yang hampir sama **[sebagian SEDANG DIBANGUN]** | `hub.id_id.json:40`; `sekte_misi`; `dunia_kota` Papan Misi; `sects` Latih Tanding |
| U13 | SEDANG | Kitab Panduan | Bab 13-16 (isi awal permainan) diletakkan setelah bab 11-12 (Dewa Abadi, Tips), dan "Bab sekarang" tidak pernah menunjuk ke sana pada waktunya | `GuideChapters.java:43`; `GuideProgress.java:126-134` |
| U14 | RENDAH | Teks | Satu hub disebut dengan empat nama: Hub, Gulungan, menu utama, Layar kultivasi | `duel_inti:34`; `guide:13`; `core:125`; `hub:80` |
| U15 | RENDAH | Hub | Kartu "Catatan Tangga" menutup hub dan mencetak ke obrolan, satu-satunya kartu yang begitu **[SEDANG DIBANGUN]** | `TanggaClient.java:53-59` |
| U16 | RENDAH | Bantuan perintah | `/kultivasi perguruan` hanya menyebut 6 dari 10 modul, dan topik "Perintah" tidak menyebut perguruan/duel/peta/panduan | `sekte.id_id.json:9`; `screen.id_id.json:177` |
| U17 | RENDAH | Pengaturan | Tombol "?" di grup Suara membuka topik `visuals`, HUD membuka `jalan`, Kontrol membuka `kitab_teknik` | `SettingsPage.java:28` |
| U18 | RENDAH | Suara | `voice.langit.treasure_born` dan `voice.langit.blood_eclipse` tidak dipakai (kembaran `heaven.langit.*`) | `sounds.json:3508,3517`; `Langit.java:343-355` |
| U19 | RENDAH | Istilah | "Prasasti Sekte" (hub) vs nama blok "Prasasti Aula Sekte"; "kemasyhuran" vs "Ketenaran" | `hub.id_id.json:28,30`; `hall` |
| U20 | RENDAH | Dokumen | Daftar config README tanpa `kultivator-duel.json` dan `kultivator-sekte.json`; `kultivator-duel.json` tidak didokumentasikan di mana pun | `README.md:192-206`; `DuelConfig.java:25` |
| U21 | RENDAH | Rencana 37 | Catatan integrasi Sekte Langit: jumlah kartu salah, tabrakan nama "langit", bantuan perlu disiapkan dari awal | `37-sekte-langit.md:178, 1166-1188` |

---

## Rincian

### U1 · TINGGI · Hub SEKTE meluap, dan rencana 37 menambah kartu [SEDANG DIBANGUN]

**Bukti.**
* Kartu bawaan SEKTE berjumlah 8, ditambah "Dirikan sekte" bila belum punya aula (`HubCardsPage.java:183-196`).
* Sekte 36 menambah 8 kartu lagi: `DiplomasiClient.java:33`, `HubunganClient.java:54`, `KebunClient.java:62`,
  `MisiClient.java:61`, `PaviliunClient.java:60`, `SeragamClient.java:63`, `TetuaClient.java:57`,
  `TurnamenClient.java:43`. Totalnya 16-17 kartu.
* `layoutCards` (`HubCardsPage.java:235-256`) tidak menggulir, dan tinggi kartu minimal 20 px (`:245`).
* Pada GUI 320×240, `CultivationScreen.init` (`:257-263`) memberi `contentW` 254 dan `contentH` 181. Grid jadi 2 kolom ×
  9 baris = 14 + 9·20 + 8·5 = **234 px**, padahal ruangnya hanya 181 − 12 (footer) = **169 px**. Tiga baris terakhir
  tergambar di luar kertas.
* Laptop 1366×768 dengan GUI skala 4 (341×192) juga meluap. Pada skala 3 (455×256) grid 3 kolom masih muat, tapi tipis.
* Kontrak `HubCards.java:14-16` sendiri berbunyi "so every section keeps fitting the 320 × 240 grid".
* Rencana 37 (`37-sekte-langit.md:1184`) menambah kartu `sekte_langit`, sementara `:178` masih menyebut "7 kartu sekte".

**Kenapa memutus kesinambungan.** Kartu Sekte 36 yang paling baru, termasuk Turnamen dan Balai Tetua, justru yang
terpotong. Pemain mengira fiturnya tidak ada.

**Perbaikan.**
* Beri bagian SEKTE dua sub-koin seperti Pengaturan: **Aula** (8 kartu lama) dan **Perguruan** (kartu `sekte_*`).
  Caranya: tambahkan `subNames()`/`showSub()` pada `HubCardsPage` untuk `kind == SEKTE`, lalu saring
  `HubCards.of(kind)` menurut awalan id `sekte_`.
* Cadangan: `layoutCards` menggulir dengan roda tetikus seperti `SettingsPage.mouseScrolled`.
* Perbarui kontrak di `HubCards.java` dan angka di `37-sekte-langit.md:178`. Kartu `sekte_langit` masuk ke sub-koin
  Perguruan.

### U2 · TINGGI · Pelacak HUD hilang di masa sepi dan angka segelnya salah

**Bukti.**
* `GuideTracker.visible()` (`:86-87`) hanya tampil bila `Guide.bits != ALL || world() || abadi()`.
* `abadi()` (`:91-93`) mensyaratkan `data.rank().isMax()`, yaitu Dewa Abadi **Puncak**. Akibatnya, dari selesainya
  Jalan Dunia (bab 6-10) sampai Dewa Abadi Puncak (seluruh Kenaikan Abadi, 36 tahap) pelacak tidak tampil sama sekali.
* Setelah Jalan Abadi selesai, pelacak juga hilang, padahal Kitab Panduan bab 13-16 bisa masih terbuka. Baris Kitab
  hanya ada di dalam panel (`:170`).
* Segel di panel memakai `chapter + 1` (`:190`), sedangkan segel ringkas dan judul memakai `chapter + offset + 1`
  (`:150`, `:158`). Di bab 6 tertulis judul "Bab 6" tetapi segelnya "1".

**Kenapa memutus kesinambungan.** Masa terpanjang permainan berjalan tanpa penunjuk arah, dan angka bab saling
bertentangan di satu panel.

**Perbaikan.**
* `GuideTracker.java:190`: ganti ke `chapter + offset + 1`.
* Ubah `visible()` menjadi: tampil bila Jalan masih tersisa **atau** `GuideClient.synced() && !GuideClient.allDone()`.
  Saat Jalan habis, tampilkan langkah Kitab Panduan pertama yang belum selesai (`GuideClient.current()`).
* Longgarkan `abadi()` menjadi "Jalan Dunia penuh" tanpa `isMax()`. Langkah Jalan Abadi yang belum mungkin cukup
  menampilkan "terbuka di …".

### U3 · TINGGI · "Dirikan sekte" punya dua arti, dan langkah Jalan dicentang oleh hal yang salah

**Bukti.**
* Langkah Jalan "sect" (`Guide.java:99`) dicentang oleh `sect_founded` **atau** `first_recruit_hall`.
* `sect_founded` hanya diberikan oleh `/sect create` (`SectCommands.java:194`), yang juga memutar suara Langit
  `sect_founded` (`:195`, `VoiceHooks.java:158-161`). Perintah itu hanya membuat kelompok pemain, tanpa murid.
* Memasang Prasasti Aula Sekte (`Halls.found`, `Halls.java:512-545`) hanya memberi `sect_gate_open`, tanpa suara
  Langit, dan tidak mencentang langkah.
* Teks pemandunya:
  * `goals.step.sect.how` (`goals.id_id.json:43`): "Dirikan sektemu dengan /sect create, atau terima seorang calon…"
  * Ensiklopedia `help.sects` (`screen.id_id.json:170`): "Dirikan sekte dengan /sekte create lalu undang murid."
* Kartu hub "Dirikan sekte" (`HubCardsPage.java:186`, `hub.id_id.json:28`) justru mengarah ke Prasasti.
* Ada nama ketiga lagi: `/kultivasi perguruan` (Sekte 36).

**Kenapa memutus kesinambungan.** Pemain baru yang patuh mengetik `/sect create`. Ia mendapat suara Langit dan langkah
tercentang, tapi tidak punya Aula. Langkah berikutnya ("murid pertama") memerlukan Aula yang tidak pernah dijelaskan.
Kata "undang murid" di `/sect` berarti pemain, sedangkan di tempat lain "murid" berarti NPC.

**Perbaikan.**
* `Halls.found`: tambahkan `Advancements.grant(player, "sect_founded")` dan `VoiceHooks.sectFounded(player)`.
* `SectCommands.create`: ganti ke advancement baru `player_sect_founded`, atau buang dari langkah Jalan.
* Teks `goals.step.sect.how`, `help.sects` dan `help.sects.title`: "Rakit dan pasang Prasasti Aula Sekte untuk
  mendirikan sekte. /sect hanya untuk mengelompokkan pemain (Latih Tanding, obrolan sekte)." Ubah id dan en bersama.
* Ganti nama kelompok `/sect` di teks menjadi "Kelompok Sekte (pemain)", supaya kata "sekte" tanpa keterangan selalu
  berarti Aula.

### U4 · SEDANG · Ensiklopedia dan "?" belum mencakup Duel Pedang dan Sekte 36

**Bukti.**
* `HelpPage.TOPICS` (`:39-54`) berakhir di `peta_dunia`. Tidak ada `duel_pedang` dan tidak ada satu pun topik Sekte 36:
  kepribadian, hubungan, turnamen lei tai, tangga, paviliun/makam, tetua, misi cerita, lambang/seragam, diplomasi,
  kebun/tambang. Kunci `kultivator.screen.help.*` untuk topik itu juga tidak ada di `lang-src`.
* Hitungan `HubNav.help`/`helpTopic` per layar menghasilkan 0 untuk: `PetaScreen`, `DuelScreen`, `ChallengeScreen`,
  `MenaraScreen`, `DimensiScreen`, `HelmScreen`, `TurnamenScreen`, `DiplomasiScreen`, `JaringScreen`, `KebunScreen`,
  `PaviliunScreen`, `BalaiTetuaScreen`, `LambangDesignerScreen`, `SuasanaScreen`, `MisiLaporanScreen`.
* Sebagai pembanding, `CodexScreen` dan `HallScreen` punya tautan "?".

**Kenapa memutus kesinambungan.** Fitur terbaru tidak punya penjelasan di tempat yang dipakai pemain untuk semua fitur
lama. Kontrak 33 ("setiap halaman: ? ke Ensiklopedia") putus.

**Perbaikan.**
* Tambahkan 2 topik dulu, `duel_pedang` dan `sekte_hidup` (ringkasan 10 modul + baris Sumber), di `duel_ui.*` dan
  `sekte_panduan.*`. Masukkan keduanya ke `TOPICS`.
* Tiap layar baru diberi segel "?" yang memanggil `HubNav.help("<topik>")`. Pola yang sama sudah ada di
  `TitahScreen`/`DuniaScreen`.
* Bagi Sekte 36 [SEDANG DIBANGUN]: tambahkan butir di daftar finish 36.7.

### U5 · SEDANG · Topik bantuan lama bertentangan dengan kode baru [sebagian SEDANG DIBANGUN]

**Bukti.**
* `help.turnamen` (`sectevent.id_id.json:377`) bilang laga berakhir "setelah 60 detik". Babak Lei Tai memakai
  `detikBabak = 90` (`TurnamenConfig.java:33`), +30 di final (`BabakDuel.java:97`). Topik itu tidak menyebut Elo,
  musim, penonton, atau tamu antar-sekte.
* `help.misi` (`:375`) hanya menjelaskan Ekspedisi, tidak menyebut Misi Cerita (sikap, 3 tahap, Gulungan Laporan).
* `help.sects` hanya menyebut Latih Tanding `/sect spar` dan tidak menyebut Duel Pedang. Padahal `DuelHooks.java:94-96`
  sudah memberi lapisan benturan pada pasangan Latih Tanding.

**Perbaikan.**
* Perbarui ketiga teks (id + en) setelah modul T/X final.
* Tambahkan kalimat silang: "Laga Turnamen kini Duel Pedang 90 dtk bila kedua murid berpedang".
* Arahkan ke kartu "Turnamen" / "Misi Cerita".

### U6 · SEDANG · Token tombol yang tidak diikat merusak kalimat

**Bukti.**
* `KeyHints.fill` mengganti tombol yang tidak diikat dengan "tidak diikat", kecuali yang ada di `QUICK_REACH`
  (`KeyHints.java:36`) atau `WHEEL_REACH`.
* Tiga tombol bawaannya tidak diikat:
  * `peta`: `PetaClient.java:25`, tidak masuk `QUICK_REACH` walaupun bisa dijangkau lewat kartu Dunia.
  * `parry` dan `duel_camera`: `DuelKeys.java:21,24`.
* Hasil di layar:
  * `dunia_panduan:7`: "Kartu Peta Dunia di Gulungan › Dunia, tombol **tidak diikat** atau /kultivasi peta…"
  * `guide.c2.tangkis`: "klik kanan (atau **tidak diikat**)"
  * `guide.c6.tonton`: "kamera penonton (**tidak diikat**)"

**Perbaikan.**
* Tambahkan `"peta"` ke `QUICK_REACH`, sehingga muncul "tidak diikat · Akses Cepat (V)".
* Untuk `parry` dan `duel_camera`, beri jalur sendiri, misalnya teks "klik kanan" dan "tombol kamera di layar duel".
* Pilihan lain: ubah teks menjadi "(tombol [[key:parry]] bila diatur)".

### U7 · SEDANG · Koin HUD salah nama dan mencakup HUD yang tidak semestinya

**Bukti.** Label koin "HUD Qi (kiri bawah)" dengan tip "Bola dantian, ranah, dan qi…" (`screen.id_id.json:133`,
`SettingsRows.java:190`).

| Kelompok | HUD | Bukti |
|---|---|---|
| Ikut disembunyikan oleh koin ini | Pelacak panduan | `GuideTracker:107` |
| | Peringatan tribulasi | `TribulationHud:46` |
| | Codex dan cooldown | `CodexHud:175` |
| | Pelat elit | `ElitePlate:36` |
| | HUD bangun | `BuildHud:43` |
| | Gulungan Tangga | `TanggaHud:33` |
| | Lingkar Lei Tai | `LeiTaiHud:27` |
| Tidak terpengaruh | Jiwa (Dunia Bawah) | `DimensiHud:33` |
| | Kapal | `KapalHud` |
| | Naga | `NagaHud` |
| | Langit | `LangitHud:47` |
| | Duel | `DuelHud` |
| | Alarm formasi | `AlarmHud:70` |
| | Laporan Tetua | `LaporanCard:106` |

**Kenapa memutus kesinambungan.** Pemain yang hanya ingin menyembunyikan bola Qi ikut kehilangan peringatan tribulasi,
padahal itu informasi bahaya. Sebaliknya, HUD baru tidak bisa disembunyikan sama sekali.

**Perbaikan.**
* Ganti nama label menjadi "HUD Kultivator" dengan tip yang mendaftar isinya.
* Keluarkan `TribulationHud` dari saklar ini, karena bahaya harus selalu tampil.
* Tambahkan `EffectQuality.hudVisible()` ke 7 HUD yang tidak terpengaruh, kecuali `DuelHud` saat duel aktif dan
  `DimensiHud` timer jiwa (bahaya).

### U8 · SEDANG · Nama ranah tidak konsisten

**Bukti.** Nama baku `kultivator.realm.true_immortal` adalah "Dewa Abadi". Variannya:

| Varian | Lokasi |
|---|---|
| "Abadi Sejati" | `aura_high.id_id.json:11` ("Aura Abadi Sejati") |
| | `tempa.id_id.json:168` ("Perlu Abadi Sejati dengan %s bintang") |
| | `dunia_kota.id_id.json:523` dan 3 tempat lain di fragmen yang sama (persik, cela, help kota_abadi) |
| | `dunia_naga.id_id.json:163` |
| "Dewa Abadi Sejati" | `hub.id_id.json:68` (tip Kitab Panduan) |
| "Pembentukan Fondasi" (baku: Pembangunan Fondasi) | `aura.id_id.json:8` |
| | `alam` (2 baris Sumber; ini kutipan, boleh tetap) |

**Kenapa memutus kesinambungan.** Syarat "Perlu Abadi Sejati" tidak bisa dicocokkan pemain dengan ranah mana pun di
layar Status.

**Perbaikan.**
* Ganti dengan "Dewa Abadi" / "Pembangunan Fondasi" di `aura`, `aura_high`, `tempa`, `dunia_kota`, `dunia_naga` dan
  `hub` (id saja; en sudah "True Immortal").
* Baris Sumber dibiarkan.

### U9 · SEDANG · Penggantian nama setengah jalan

**Bukti.**
* **Cincin Segel → Rangkai Segel** (diumumkan di `PANDUAN.md:221`). Nama lama masih ada di:
  * output `/kultivasi teknik slot` (`teknik.id_id.json:159` dan 3 kunci sesudahnya);
  * `README.md:58`;
  * `PANDUAN.md:244, 1034, 1059, 1076, 1843`.
* **Mode Kehancuran → Kehancuran Diri** (`PANDUAN.md:221`): "Mode Kehancuran" masih muncul 66 kali di 25 fragmen,
  "Kehancuran Diri" hanya 6 kali (judul tab).
* **Destroyer Arts murid** punya dua nama: "Teknik Penghancur" (`hub.id_id.json:44`) dan "Jurus Pemusnah" (25 kali,
  7 fragmen).

**Perbaikan.**
* Cincin Segel: ganti 4 kunci `teknik.cmd.slot.*` dan 6 baris dokumen menjadi "Rangkai Segel".
* Kehancuran: pakai **"Mode Kehancuran"** untuk saklarnya (nama yang dipakai di mana-mana dan di aturan keamanan) dan
  "Kehancuran Diri" hanya untuk nama tab. Perbaiki kalimat `PANDUAN.md:221` sesuai itu.
* Destroyer Arts: `hub.card.segel_merah.tip` diganti menjadi "Jurus Pemusnah murid-muridmu".

### U10 · SEDANG · PANDUAN tertinggal dari kode

**Bukti.**
* `PANDUAN.md:8448-8470` menyebut "14 bab, 124 langkah" dan "84 langkah dicentang sendiri". Menurut
  `GuideChapters.java:43` dan hitungan `step(...)`, isinya sebenarnya **16 bab, 153 langkah, 112 langkah dengan cek
  otomatis**. README (`:115`) sudah benar (16/153).
* Daftar bab di PANDUAN tidak memuat 15 "Murid yang Hidup" dan 16 "Sekte di Dunia".
* Tabel hub `PANDUAN.md:200-209` tidak memuat kartu:
  * Dunia: Duel Pedang, Peta Dunia, Menara Seratus;
  * Keabadian: Kota Abadi;
  * Murid: Suasana Hati, Catatan Tangga;
  * Sekte: 8 kartu `sekte_*`.
* Tabel tombol `PANDUAN.md:176-177` tidak menyebut tombol Peta Dunia, Tangkis dan Kamera duel (semua tidak diikat).

**Perbaikan.** Perbarui PANDUAN 62 (angka + daftar bab), tabel bagian 2.2 dan tabel tombol bagian 2.

### U11 · SEDANG · Kartu Sekte 36 tanpa gembok [SEDANG DIBANGUN]

**Bukti.**
* Kedelapan `HubCards.register(HubNav.SEKTE, …, null, …)` memberi `lock = null`, sedangkan kartu aula bawaan memakai
  `this::hallLock`.
* Tanpa Aula, klik pada kartu-kartu itu menghasilkan hal berbeda:
  * Kebun: pesan obrolan `no_hall` (`KebunPayloads.java:163`);
  * Lambang: perancang hanya-baca (`Seragam.java:270-277`);
  * Hubungan: layar kosong lalu pesan `tanpa_aula`;
  * Tetua/Paviliun: `expectChild` menunggu jawaban.
* `PANDUAN.md:210-211` menjanjikan "kartu yang belum terbuka tampil abu-abu dengan gembok dan alasannya".

**Perbaikan.**
* Sediakan `HubCards.hallLock()` publik, dengan logika yang sama seperti `HubCardsPage.hallLock`, dan berikan sebagai
  `lock` pada 8 kartu `sekte_*` (dan `sekte_langit` pada 37).
* Untuk kartu yang perlu aula tingkat tertentu, beri pesan "butuh Aula tingkat N".

### U12 · SEDANG · Nama kembar: Misi, Turnamen, duel [sebagian SEDANG DIBANGUN]

**Bukti.**
* Di bagian SEKTE ada kartu "Kegiatan" dengan tip "Tugas, misi dan arena" (`hub.id_id.json:40`), sekaligus kartu
  "Misi Cerita" dan kartu "Turnamen". Di Kota ada "Papan Misi Kota" (`dunia_kota`).
* Tiga duel yang namanya berbeda:
  * "Latih Tanding" (`/sect spar`, `sects`);
  * "Duel persahabatan"/Tanding pengembara (`dunia_kota kota.pertemuan.tip_tanding`);
  * "Duel Pedang".
* Help topic `misi` = Ekspedisi, sedangkan langkah Jalan menyebutnya "ekspedisi".

**Perbaikan.**
* Ganti nama tip "Kegiatan" menjadi "Tugas, Ekspedisi dan arena".
* Pakai "Ekspedisi" untuk papan lama (`help.misi.title` dan tablet), "Misi Cerita" untuk 36, dan "Papan Tugas Kota"
  untuk Kota.
* Di `help.sects` dan bantuan duel, sebutkan satu kalimat: "Latih Tanding dan Tanding pengembara kini memakai aturan
  Duel Pedang".

### U13 · SEDANG · Urutan bab Kitab Panduan bukan urutan bermain

**Bukti.**
* `GuideChapters` menaruh bab 13 "Dunia yang Luas" (konten Pemurnian Qi sampai Inti Emas) dan bab 15/16 (Aula
  lv1-3) setelah bab 11 "Dewa Abadi" dan bab 12 "Tips".
* `GuideProgress.currentChapter` (`:126-134`) mengambil bab pertama yang masih punya langkah terbuka. "Bab sekarang"
  baru menunjuk ke bab 13 setelah bab 1-12 selesai.
* Ini sama dengan temuan sintesis T1. Rencana 37 menambah bab 17 di ujung lagi (`37-sekte-langit.md:1168`).

**Perbaikan.**
* Tanpa mengubah urutan bit (`GuideSync` bergantung pada urutan), tambahkan `displayOrder` di `GuideChapters.Chapter`
  dan ranah buka per bab.
* `currentChapter` memilih bab terbuka **yang ranah bukanya ≤ ranah pemain** dengan `displayOrder` terkecil.
* `GuideScreen` mengurutkan daftar bab menurut `displayOrder`.

### U14 · RENDAH · Satu hub dengan empat nama

**Bukti.**

| Nama | Lokasi |
|---|---|
| "Hub › Dunia › Duel Pedang" | `duel_inti:34`, `duel_ui:72,127,280,288` |
| "Gulungan › …" | `guide:13,229`, `dunia_panduan:7`, `dunia_peta:177` |
| "menu utama" | `PANDUAN.md:169, 177, 181`, `README.md:190` |
| "Layar kultivasi" | `key.kultivator.info`, `core:125` |
| "Gulungan Kultivasi" | `hub:80` |
| "hub" | `sekte_misi status.petunjuk` |

**Perbaikan.** Pilih satu nama, "Gulungan", lalu ganti "Hub ›" di 6 kunci duel, "hub" di `sekte_misi`, nama tombol
`info` ("Gulungan (tanpa roda)"), dan kata "menu utama" di dokumen.

### U15 · RENDAH · Kartu "Catatan Tangga" mencetak ke obrolan [SEDANG DIBANGUN]

**Bukti.** `TanggaClient.java:53-59` melakukan `setScreen(null)` lalu `sendCommand("kultivasi perguruan tangga")`.
Semua kartu lain membuka layar lewat `HubNav.openChild`/`expectChild`, sehingga Esc kembali ke hub.

**Perbaikan.** Buat layar daftar kecil (atau tab di `SuasanaScreen`) yang dibuka lewat `HubNav.openChild`, dan
`TanggaHud` memakai data yang sama.

### U16 · RENDAH · Bantuan perintah tidak lengkap

**Bukti.**
* `kultivator.sekte.cmd.player.help` (`sekte.id_id.json:9`) hanya menyebut 6 modul ("murid, hubungan, lambang,
  diplomasi, misi, kebun"), sedangkan yang terdaftar ada 10 (`SekteCommands.player`: tambah `tangga`, `paviliun`,
  `tetua`, `turnamen`).
* `help.commands` (`screen.id_id.json:177`) tidak menyebut `/kultivasi perguruan`, `duel`, `peta`, `panduan`.

**Perbaikan.**
* Buat `playerHelp` (`SekteCommands.java:177`) mencetak daftar literal dari `PLAYERS` secara dinamis, bukan teks
  tetap.
* Lengkapi `help.commands`.

### U17 · RENDAH · Pemetaan "?" di Pengaturan

**Bukti.** `SettingsPage.java:28` mengisi `HELP = {"visuals","momen","visuals","jalan","kitab_teknik"}`. Akibatnya Suara
membuka topik efek visual, HUD membuka Tablet Jalan, dan Kontrol membuka Kitab Teknik.

**Perbaikan.** Tambahkan topik kecil `suara` (mode suara, teks suara, VOICEVOX) dan `tombol` (Akses Cepat, Roda
Segel, bentrok tombol), atau arahkan HUD ke `jalan` hanya bila topik `hud` belum ada.

### U18 · RENDAH · Dua event suara tidak dipakai

**Bukti.**
* `sounds.json:3508,3517` (`voice.langit.treasure_born`, `voice.langit.blood_eclipse`) dibuat oleh
  `tools/gen_voices.py`, daftar WORLD baris `langit.*`.
* Yang dipakai kode adalah `heaven.langit.*` (`Langit.java:343-355`, `Dimensi.java:249,260`).

**Perbaikan.** Hapus dua entri `langit.*` dari daftar WORLD di `gen_voices.py` pada regenerasi berikut (beserta ogg
dan teksnya).

### U19 · RENDAH · Nama prasasti dan ketenaran

**Bukti.**
* "Prasasti Sekte" muncul 3 kali di `hub.id_id.json` (`:28` dll.), sedangkan blok bernama "Prasasti Aula Sekte"
  (15 kali di 8 fragmen).
* "kemasyhuran" (`hub:30`, `sekte_kebun`) dipakai bergantian dengan "Ketenaran" (`hall.ui.renown`).

**Perbaikan.** Seragamkan menjadi "Prasasti Aula Sekte" dan "Ketenaran".

### U20 · RENDAH · Daftar config tidak lengkap

**Bukti.**
* `README.md:192-206` hanya mendaftar `kultivator.json` dan sebagian `kultivator-client.json`.
* `kultivator-duel.json` (`DuelConfig.java:25`) tidak didokumentasikan di README maupun PANDUAN.
* `kultivator-sekte.json` hanya disebut di `PANDUAN.md:9152`.
* Kunci klien `voice_mode`, `voice_text`, aura dan epic tidak tercantum.

**Perbaikan.** Tambahkan satu tabel "Berkas config" di README dan di PANDUAN 52: lima berkas, masing-masing satu
baris, dengan tautan ke bagiannya.

### U21 · RENDAH · Catatan integrasi Sekte Langit (37)

* `37-sekte-langit.md:178` masih menyebut "7 kartu sekte". Kenyataannya 16-17, lihat U1. Kartu `sekte_langit` harus
  menunggu sub-koin SEKTE.
* Bab 17 akan ditambahkan di ujung (`:1168`), sehingga memperparah U13. Ikuti `displayOrder` usulan U13 (bukanya
  Aula lv6).
* Tabrakan nama:
  * `/kultivasi langit feri` (pemain) vs `/kultivasi uji langit` yang sudah ada (peristiwa langit,
    `DuniaCommands.test("langit")`);
  * "Bajak Awan" vs "Bajak langit" (kapal, PANDUAN 64);
  * "Langit" sudah berarti Suara Langit, Titah Langit, dan Peristiwa Langit.
  Usulan: perintah pemain `/kultivasi perguruan awan …` dan istilah tetap "Sekte Awan".
* Siapkan dari awal: topik Ensiklopedia `sekte_langit`, segel "?" di `LangitScreen`, `lock` kartu, dan
  `EffectQuality.hudVisible()` untuk HUD apa pun yang ditambahkan.

---

## Urutan kerja yang disarankan

1. **U3** (teks + 2 baris kode di `Halls.found`/`SectCommands`) dan **U2** (`GuideTracker` 2 tempat). Keduanya murah
   dan langsung terasa oleh pemain baru.
2. **U1 + U11** (sub-koin SEKTE dan gembok aula), setelah workflow Sekte 36 selesai, sebelum 37 dimulai.
3. **U6, U7, U8, U9** (teks dan `KeyHints`/HUD).
4. **U4, U5, U12** (bantuan) dan **U10, U13** (dokumen dan urutan bab).
5. Sisanya (RENDAH).
