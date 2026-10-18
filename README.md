# Kultivator — mod kultivasi xianxia untuk Minecraft 26.3 (Fabric)

**Bahasa Indonesia** · [English below](#english)

Mod Fabric berdiri sendiri di mana **pemain** berkultivasi seperti di novel xianxia: memurnikan qi, menembus ranah dari
Manusia Fana sampai Dewa Abadi, bertahan dari Tribulasi Langit, memilih Jalan Dao, menguasai teknik, terbang di atas
pedang, meracik pil, menghadapi Iblis Hati, membangkitkan binatang roh, dan mendirikan sekte. Kamu mendirikan
**Aula Sekte**, menerima murid dari Hari Penerimaan Murid, dan membimbing mereka naik tanpa batas sampai **Bintang
Abadi**, bahkan sampai melampauimu. Lalu sektemu **membangun dirinya sendiri**: pilih satu dari sembilan templat
kompleks sekte klasik, setor bahan, dan saksikan sekte tumbuh melewati sembilan tingkat. Setiap fitur sekte dan murid
diadaptasi dari cerita kultivasi sungguhan, dan sumbernya (karya + konsep) tertulis di game dan di panduan.

📖 **Panduan pemain lengkap: [docs/PANDUAN.md](docs/PANDUAN.md)** · Daftar uji per fase: [docs/uji/](docs/uji/) ·
Rancangan dan aturan kode: [docs/DESIGN.md](docs/DESIGN.md)

**Aturan keras:** efek dan kekuatan mod ini tidak pernah membakar, merusak, memasang, atau mengubah blok apa pun.
Petir tribulasi hanya partikel dan suara (tanpa entity petir), cuaca dan waktu tidak diubah, dan tribulasi tidak pernah
membunuh — kalau gagal, pemain terluka (Penyimpangan Qi). Hanya ada dua pengecualian:
* **Pembangunan Sekte** memasang blok **hanya di dalam tapak yang kamu lihat sebagai hologram lalu kamu konfirmasi
  sendiri**, hanya di chunk yang sudah termuat. Tanah dan tanaman alami yang dipindahkan dikreditkan ke Gudang Bahan
  Sekte, bangunan buatan pemain dan blok terlindung tidak pernah dibongkar diam-diam, dan di luar tapak tidak ada yang
  berubah ([aturannya di panduan](docs/PANDUAN.md#20b17-keamanan-blok-pembangunan-sekte)).
* **Mode Kehancuran** untuk jurus murid tingkat tertinggi: **mati secara bawaan**, terkunci di balik izin server, sumpah
  pemain, dan segel per murid, dengan zona terlindung di sekitar aula, tapak sekte, pemain, dan desa, serta tombol
  Pulihkan ([peringatan di panduan](docs/PANDUAN.md#31-mode-kehancuran-peringatan)).

## Fitur

* **9 ranah, 65 posisi** — Manusia Fana, Pemurnian Qi, Pembangunan Fondasi, Inti Emas, Jiwa Baru Lahir, Transformasi
  Dewa, Penyatuan Dao, Kenaikan Abadi (9 tingkat), Dewa Abadi; masing-masing dengan tahap Awal, Menengah, Akhir, Puncak.
  Kultivasi dari monster, bijih, meditasi, pil, dan pencerahan; leher botol di Tahap Puncak menyimpan **Qi Tertimbun**.
* **Terobosan** — kebangkitan, lemparan peluang, lalu **Tribulasi Langit** dengan varian Permukaan, Api Neraka (Nether),
  dan Kehampaan (End), gelombang **Pelindung Dao**, **Kualitas Terobosan** (Retak/Biasa/Sempurna/Surgawi),
  **Penyimpangan Qi**, dan mode Hardcore opsional.
* **Meditasi** — pose bersila, tempat spiritual (puncak gunung, Hutan Ceri, suar, kecubung, End, bulan purnama), urat roh
  dengan kepadatan qi, Momen Pencerahan, dan tiga **formasi pengumpul qi** dari blok vanilla.
* **Akar spiritual** — upacara pengujian dengan 9 elemen dan 5 mutu, Pil Pembersih Sumsum.
* **8 Jalan Dao** dengan Pemahaman Dao dan **23 teknik** (Langkah Awan, Pedang Terbang, Telapak Buddha, Wujud Dharma,
  Wilayah Dao, Seribu Pedang, Api Phoenix, Petir Surgawi, Penjara Ruang, …); kitab teknik dari Buku Pesona vanilla.
* **Pil** — barang vanilla sebagai pil dengan racun pil, dan **Alkimia** di tungku Kuali di atas Api Unggun dengan pil
  bermutu Rendah sampai Sempurna.
* **Iblis Hati**, **Binatang Roh** (serigala dan rubah hingga Serigala Langit / Rubah Ekor Sembilan), **Formasi
  Pelindung**, **Sekte** dan **Latih Tanding**.
* **Pengakuan Langit** — peristiwa sinematik saat menjadi Dewa Abadi: pilar cahaya emas, langit terbuka, suara langit,
  aura pelangi, kebal Penyimpangan Qi, dan regenerasi abadi.
* **Visual mewah** — aura per ranah, jubah sesuai Dao, pedang di punggung, mata bercahaya, Lingkaran Dao, pedang terbang
  di bawah kaki; HUD bola dantian dan layar "Gulungan Giok"; **Kualitas Efek** Minimal / Normal / Maksimal.
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
| 6c | Teknik Dewa: 100+ teknik baru bersumber cerita kultivasi, makin OP sesuai ranah, Kitab Teknik, kehancuran blok lewat saklar gampang | ⏳ |
| 7 | Dunia yang Hidup (monster elite, batu roh, jimat, herba) | ⏳ |

## Perintah singkat

`/kultivasi` atau `/cultivate` — `info`, `meditate`, `breakthrough`, `hardcore on|off`, `root test|info`,
`dao [info|choose <dao>]`, `technique list|select|cast`, `visual <bagian> on|off`, `beast list`, `formation list`,
`murid list|panggil|ikut|tinggal|meditasi|bantu|tahan`, `aula info|nama <nama>`, `kehancuran status|sumpah|pulihkan`,
`aula bangun [templat|pratinjau <1-9> [arah] [glasir]|konfirmasi|setor [semua|butuh]|naik <2-9>|perbaiki|bongkar|lepaskan]`.
Perintah uji cepat untuk operator: `/kultivasi uji ...`, `/kultivasi uji murid <nama|semua> maks|ranah|bintang <n>`, dan
`/kultivasi uji sekte templat <1-9>|tingkat <0-9> [instan|cepat]|sumber|pamer|audit|info|reset|...`
([daftar lengkap](docs/PANDUAN.md#20b16-perintah)).
`/sekte` atau `/sect` — `create`, `invite`, `join`, `leave`, `kick`, `disband`, `info`, `list`, `chat`,
`spar <pemain>|accept|decline`. Perintah uji operator ada di bawah `/kultivasi debug` — daftar lengkapnya di
[panduan](docs/PANDUAN.md#33-perintah-uji-untuk-operator-kultivasi-debug-).

## Tombol bawaan

`G` teknik terpilih · `;` meditasi · `'` layar kultivasi · `,` `.` ganti teknik · `\` pedang terbang · lompat di udara =
Langkah Awan. Semua bisa diganti di Options → Controls → Key Binds → Kultivator.

## Konfigurasi

`config/kultivator.json` (server): `xpMultiplier`, `hardcoreForced`, `daoProtectorEvent`, `tribulationDamageScale`,
`heartDemonTrials`, `protectiveFormations`, `announceBreakthroughs`, `sectHall`, `recruitmentIntervalDays`,
`sectEvents`, `tournamentIntervalDays`, `forbiddenGroundIntervalDays`, `sectConstruction` dan `sectBuild...` (laju,
batas per tick, jeda MSPT, jangkauan, tabungan, templat terbesar, pengembalian bahan, tanpa monster di sekte), serta
`modeKehancuran` (bawaan `false`) dan batas-batas `kehancuran...`. `config/kultivator-client.json` (klien): `effect_quality`, `hud`. Penjelasan di
[panduan](docs/PANDUAN.md#36-berkas-konfigurasi).

## Build

Java 25. `gradlew.bat compileJava compileClientJava jar --no-daemon` → `build/libs/kultivator-<versi>.jar`.
Teks ditulis per modul di `lang-src/*.en_us.json` dan `lang-src/*.id_id.json`; task `mergeLang` menggabungkannya dan
gagal kalau ada kunci yang hanya ada di satu bahasa. Rancangan lengkap: [docs/DESIGN.md](docs/DESIGN.md).

---

## English

A standalone Fabric mod for Minecraft 26.3 in which **you** cultivate like in a xianxia novel: refine qi, break through
nine realms (65 ranks) from Mortal to True Immortal, survive heavenly tribulations (with Nether and End variants and
waves of Dao Protector monsters), walk one of eight Daos, master 23 techniques, fly on your sword, refine pills in an
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

Nothing in this mod ever burns, breaks, places or changes a block; tribulation lightning is particles and sound only,
the weather and time are never changed, and a failed tribulation injures you (Qi Deviation) instead of killing you.
There are two exceptions. **Sect Construction** places blocks only inside the footprint you previewed as a hologram and
confirmed yourself, only in loaded chunks; natural ground it moves is credited to the sect's material store, and
player-made and protected blocks are never removed silently. The opt-in **Destruction Mode** for top-tier disciple arts
is OFF by default and needs server permission, a player oath and a per-disciple seal release. Halls, sect sites, players
and villages stay protected, and an undo window restores the blocks.
The full player guide is in Indonesian: [docs/PANDUAN.md](docs/PANDUAN.md); every rule and number is in
[docs/DESIGN.md](docs/DESIGN.md); per-phase test lists in [docs/uji/](docs/uji/).

License: MIT.
