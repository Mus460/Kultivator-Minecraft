## 39. Murid Mandiri (Jalan Hidup Murid)

Status: RENCANA (2026-10-23). Dokumen ini hanya rancangan; belum ada kode. Kode adalah kebenaran: setiap klaim tentang
sistem lama di bawah sudah dicek di `src/main/java/dev/kultivator/**` pada tanggal ini (berkas:baris disebut bila penting).

Permintaan pengguna (diterjemahkan): "Bisakah murid tumbuh atau punya kebiasaan sendiri, sampai bisa naik ke Abadi Sejati
tanpa aku ajari? Seperti mereka sudah punya kegiatan, seolah ada AI di baliknya. Setiap murid jalannya beda, kegiatannya
beda. Capek kalau mereka cuma bergerak atas perintahku." Tambahan dari koordinator (permintaan pengguna): "Misalnya murid
pergi ke kota dan membeli perbekalan kultivasi dan lain-lain, secara detail." Belanja ke kota adalah bagian utama
rancangan ini (39.6), bukan tempelan.

Pendekatan: satu modul sekte baru `mandiri` (paket `dev.kultivator.sekte.mandiri`), dibangun di atas DESIGN 36 (watak,
kepuasan, hubungan, ingatan, paviliun, misi, kebun, tetua), DESIGN 20 (jenjang, PK, Hu Fa, bintang, Surpass) dan kontrak
DESIGN 38 (kota besar, jalan, ekonomi, kehidupan NPC). Setiap murid mendapat **Jalan Hidup** (arah hidup), **Cita-cita**
(tujuan jangka panjang yang bisa berubah), **dorongan** (kebutuhan yang naik turun) dan **perencana harian berbasis
utilitas** yang memilih kegiatan nyata yang terlihat saat dimuat, dan disimulasikan sebagai data saat tidak dimuat.
Pekerjaan dibagi ke **tepat lima modul** dengan berkas yang tidak bertumpuk (39.12).

### 39.0 Aturan mengikat (tambahan atas bagian 0 dan 36.0)

1. **Semua aturan 36.0 berlaku**: tanpa perubahan blok saat bermain (kecuali `WorldBreaker`/`BuildPlacer` yang ada), tanpa
   `LightningBolt`, cuaca, waktu atau tick-rate; kamera 7b lewat penjaga lama; laptop lemah (quad aditif, `EffectQuality`,
   LOD, AI ditipiskan); klien/server terpisah; `ItemStack` dibuat malas; teks di `lang-src/<fragmen>.en_us.json` +
   `.id_id.json` dengan kunci identik; akhir baris setiap berkas dipertahankan; Gradle hanya di langkah akhir
   (`compileJava`, `compileClientJava`, `jar`, tanpa tes atau `runClient`).
2. **Perintah guru selalu menang.** Perintah eksplisit (Ikuti, Bantu, Jaga Aula, Tetap, Meditasi, kirim misi, tugas pos,
   penjaga formasi, ekspedisi) menghentikan perencana untuk murid itu sampai guru menekan "Bebaskan" atau jeda perintah
   habis (39.7.2). Kebijakan **Patuh** mematikan perencana sama sekali: perilaku persis seperti sebelum 39.
3. **Tidak ada murid yang mati karena sistem baru, kecuali guru sendiri menyalakannya.** Bawaan: semua risiko berakhir
   dengan "Terluka", "Luka Berat" atau "Tertawan" (39.5.9). Saklar `mandiri.bolehGugur` (bawaan `false`) hanya berlaku
   untuk Kehati-hatian **Nekat** dan **perlu persetujuan pengguna** sebelum default diubah.
4. **Angka lama tidak berubah.** `XP_FACTOR 0.6`, `PILL_SHARE 0.7`, `SectRanks.XP_FACTOR`, jarak Hu Fa, rumus tribulasi,
   harga `Harga`/`Contribution`, rumus `Expeditions` tetap. Modul ini menambah **faktor baru yang berbatas**, semuanya di
   config `mandiri` dan bisa dimatikan. Angka baru yang mengubah laju (`skalaXp`, 39.5.1) ditandai **perlu persetujuan**.
5. **Tidak ada barang atau uang dari ketiadaan.** Setiap batu roh dan barang yang dimiliki murid punya sumber yang tercatat
   (39.6.11). Dompet murid adalah angka NB (Nilai Batu), bukan item; item hanya keluar dari dompet lewat pembelian yang
   membayar lebih dulu.
6. **Guru tetap jalan terbaik.** Pertumbuhan mandiri selalu lebih lambat daripada murid yang dibimbing guru (Bimbingan
   Guru, ajaran, pil dari guru, Hu Fa guru). Mandiri hanya membuat murid **tidak pernah buntu**.
7. **Satu pemilik per berkas** (39.12). Akses ke kode package-private lama dilakukan lewat **berkas jembatan baru** di paket
   yang sama (mis. `sekte/kebun/KebunMandiri.java`), bukan dengan membuka visibilitas berkas lama. Bila jalur lama terikat
   pada `ServerPlayer` guru (mis. `Duties.assign`, `Keepers.assign`, `NatalTreasure.refine`), pemiliknya menambah overload
   `*Self` yang aditif (39.12.1); metode lama tidak berubah.
8. **Setiap fitur punya baris Sumber** (39.11). Yang tidak terverifikasi tidak dikutip.

### 39.1 Audit sistem yang sudah ada (kode adalah kebenaran)

#### 39.1.1 Yang sudah membuat murid tumbuh tanpa pemain

| Jalur | Berkas | Syarat dan batas |
|---|---|---|
| Meditasi sendiri | `DiscipleCultivation.tick` (`:142-232`), `meditationBase` (`:318`) | Hanya **tubuh dimuat** dan sedang duduk (`isMeditatingNow`). XP/detik `max(0.4 + 0.2·ranah, 0.75·Meditation.baseRate)` ×1,5 bila mode MEDITATE, × potensi akar × faktor jenjang (`SectRanks.XP_FACTOR` 0,55-1,00) × kait (`Watak` RAJIN 1,08 / MALAS 0,90, `FoundationTypes`, `Stars`, `DiscipleBridge` formasi ≤ ×1,5, `Duties` makan, `Heir`) × config. |
| Bimbingan Guru | sama, `guidanceShare` (`:239`) | Guru **bermeditasi** dalam `8 + 2·ranah guru` blok: XP sendiri ×(1 + 0,05·ranah guru) + bagian dari XP guru (≤ 60 %). Ini jalur tercepat dan tetap begitu. |
| XP bunuh | `onKill` (`:447`) | HP monster × 0,4. Hanya saat dimuat dan bertarung. |
| Pemahaman Dao | `comprehensionGain` | 0,01/detik meditasi, 0,15 per jurus Dao sendiri, 0,05 per bunuh; ×1,5 bila akar searah. Butuh Dao terpilih. |
| Ekspedisi/misi | `sect/event/Expeditions`, `sekte/misi/*` | Pure data lewat `Halls.storeAway`; memberi PK, rampasan ke Lumbung, bagian bar 15-35 % (Expeditions). Dikirim **oleh guru**. |
| Paviliun Kitab | `sekte/paviliun/BelajarMandiri`, `RakKitab` | Belajar teknik gratis dari buku yang **disetor guru** ke rak (9/18/27 buku), 1 teknik/hari, harus di wilayah dan bebas (`Tempat.free`). Seni lama menunggu kait guru (`blocker == "master"`). |
| Tetua mengajar | `SectRanks.elderTeaches` (`:333-372`) | Tetua mengajari junior 1×/hari, tetapi `if (master == null) return` (`:341-344`): **butuh guru online**. |
| Teknik khusus murid | `selfComprehendDiscipleOnly` (`:1069`) | Hanya spec `DISCIPLE_ONLY` Dao sendiri. |
| Tawaran Dewa | `dewaTitleOffer` (`:1099`) | Tiap 9 bintang; **guru memilih** dari daftar. |
| Bintang Abadi | `disciple/path/Stars` | Setelah TI Puncak; "Otomatis" mengembunkan bintang dari Qi Tersimpan. Gerbang bencana (`Calamity`) **dimulai guru**. |
| Kebun/tambang | `sekte/kebun/*` | Hasil malas ke Lumbung; penugasan **hanya oleh guru** (`Pertumbuhan.assign`, `GiliranTambang.assign` package-private). |
| Pos tugas | `sect/event/Duties` | Air/kebun/dapur/ladang; `assign`/`release` package-private; `refine(ServerPlayer)` mengubah 3 herba fondasi jadi 1 pil (tanpa kuali). |

#### 39.1.2 Yang sudah "hidup" sendiri (DESIGN 36) dan cara 39 memakainya

| Modul 36 | Perilaku otomatis hari ini | Dipakai 39 sebagai |
|---|---|---|
| P Kepribadian | 12 sifat (`Sifat`), Kepuasan harian, Kesetiaan, keadaan SETIA/GELISAH/MEMBANGKANG/AKAN_PERGI, `workFactor` 0,6-1,3 | Bobot dorongan dan jalan hidup; `HatiService.defiant` memblokir kegiatan sekte; kegiatan yang disukai menaikkan Kepuasan lewat `SekteBus.honored` (bukan menulis `extra.hati`). |
| R Hubungan | afinitas berpasangan, `Ikatan` (SAHABAT, KEKASIH, PASANGAN_DAO, SAINGAN, DENDAM), giliran teh 1 pasang/20 s | Pilihan teman perjalanan, Hu Fa sahabat, hadiah, dendam sebagai cita-cita; `HubunganService.adjust` untuk efek kegiatan bersama. |
| A Paviliun | belajar mandiri dari rak | Kegiatan "Belajar" memesan kursi lewat jembatan `PaviliunMandiri` (39.4.2). |
| E Tetua | meja kantor 06-18, laporan pagi (dari `SECT_LOG`) | Laporan pagi menerima ringkasan 39 lewat `SekteLog.log(server, owner, "mandiri", line)`; Tetua Agung bisa menyetujui permohonan naik jenjang (39.5.5). |
| X Misi | tim 1-4, 3 tahap cerita, kepulangan lewat gerbang | Pola kepulangan (`ACT_MUSTER`, gulungan laporan) ditiru untuk perjalanan mandiri; misi guru selalu lebih dulu. |
| W Kebun | bedeng, tambang, hasil × `workFactor` | Kegiatan "Kerja kebun/tambang" mendaftar sendiri lewat jembatan `KebunMandiri`. |
| T Turnamen | musim, Elo, gelar | Cita-cita JUARA, latihan menjelang musim. |
| G Tangga | ujian hati di tangga | Vonis hati dibaca untuk risiko Jalan Iblis. |
| `Ingatan` | 16 kenangan, drift ±40 | Semua peristiwa 39 menulis kenangan `mandiri.<apa>`; jalan hidup dan cita-cita membaca drift. |

#### 39.1.3 Yang sekarang menghalangi murid mencapai Abadi Sejati sendirian

Rantai ranah (`data/Realm`): Fana → Pemurnian Qi → Fondasi → Inti Emas → Jiwa Baru Lahir → Transformasi Dewa → Penyatuan
Dao → Kenaikan Abadi (9 lapis, tiap lapis butuh terobosan) → Abadi Sejati. Itu **16 terobosan**. Penghalangnya:

| # | Penghalang | Bukti | Perbaikan 39 |
|---|---|---|---|
| H1 | **Terobosan wajib dimulai guru, online, ≤ 16 blok.** `breakthroughBlocker` mengembalikan `why.bt_offline` bila `master == null` dan `why.bt_master_far` bila jauh; `startBreakthrough(entity, ServerPlayer master)` tidak punya jalur tanpa guru. | `DiscipleCultivation.java:1383-1423, 1438`; `DiscipleBreakthrough.start(entity, level, master)` `:159` | 39B: `breakthroughBlockerSelf`, `startBreakthroughSelf(entity, Persiapan)`; `DiscipleBreakthrough.start` menerima `@Nullable ServerPlayer` (39.5.2). |
| H2 | **XP hanya bertambah saat tubuh dimuat.** Murid di chunk yang tidak dimuat (guru berpetualang jauh) berhenti total; tidak ada kejar-ketinggalan. | `DiscipleCultivation.tick` dipanggil dari tick entitas | 39A: simulasi malas saat dimuat kembali (39.4.7). |
| H3 | **Bar penuh di bottleneck berhenti** (`distribute` hanya mengisi Qi Tersimpan). Tanpa H1, murid diam selamanya di Puncak. | `:354-385` | H1 + perencana menjadwalkan terobosan. |
| H4 | **Pilihan Dao hanya oleh guru** (`chooseDao(entity, ServerPlayer, Dao)`). Tanpa Dao: tidak ada pemahaman Dao, teknik Dao terkunci (`why.no_dao`), Dewa tidak tersedia. | `:1482-1540` | 39B: `chooseDaoSelf` setelah masa tunggu guru (39.5.4). |
| H5 | **Teknik hanya dari guru**: `teach` butuh guru tahu teknik itu dan membayar qi; Paviliun hanya buku setoran guru; Tetua butuh guru online; slot teknik `SectRanks.SLOTS {2,3,5,∞}` dan naik jenjang `promote(ServerPlayer master, ...)` hanya oleh guru. Tanpa teknik, murid lemah di tribulasi tinggi (bukan penghalang ranah keras, tetapi penghalang kekuatan dan Hu Fa). | `:922-1060`; `RakKitab`; `SectRanks.java:341, 467` | 39B: lima sumber belajar sendiri + permohonan naik jenjang (39.5.5). |
| H6 | **Pil hanya dari tangan guru**: `feed(entity, ServerPlayer master, stack)` mengambil dari inventaris guru (`consume(master, ...)`). Tetua OBAT pun memanggil `feed(worst, master, pill)`. | `:598-690`; `sekte/tetua/UrusanHarian` | 39B: `feedSelf(entity, stack)` dari tas murid (39.5.6). |
| H7 | **Akar Merah tertahan di Inti Emas Puncak** (`TalentEffects.redCap`, `why.red_cap`). Satu-satunya jalan keluar: naik grade akar (Pil Pembersih Sumsum, yang hanya bisa diberikan guru). | `disciple/power/TalentEffects.java:98-108`; `sect/hall/Talents.soulColor` | 39B/39C: murid membeli `marrow_cleansing_pill` sendiri (kota: Normal; Paviliun Yue Lu hall 6: Sempurna) dan menelannya lewat `feedSelf`. |
| H8 | **Tribulasi "doomed" tanpa Hu Fa guru**: mulai ditolak bila nyawa ≤ total bagian sambaran (Abadi Sejati: 6 × 14 % = 84 %) dan `HuFa.active` (yang hanya mengenal guru) palsu. Murid yang terluka atau lemah tidak akan pernah mulai. | `:1425-1436`; `DiscipleBreakthrough.java:100-136` | 39B: persiapan (nyawa penuh, pil Penangkal Tribulasi yang dibeli sendiri, Hu Fa sahabat lewat `DiscipleHooks.addStrikeFactor`) dan cek "doomed" yang mengenal Hu Fa sahabat (39.5.3). |
| H9 | **Tawaran Dewa per 9 bintang dipilih guru** (bukan penghalang TI, tetapi penghalang setelahnya). | `:1099-1197` | 39B: pilihan otomatis setelah 3 hari tanpa jawaban (kebijakan). |
| H10 | **Bencana Bintang dimulai guru** (setelah TI). | `disciple/path/Calamity` | Di luar target TI; 39B hanya menambah "Mohon izin bencana" ke notifikasi. |
| H11 | **Murid tidak punya uang atau barang sendiri.** Semua layanan kota (`Toko`, `PapanMisi`, `BalaiLelang`, `KedaiTeh`, `PasarGelap`) package-private dan butuh `ServerPlayer` dalam jangkauan; `NilaiBatu.pay/give` memakai inventaris pemain. | `dunia/kota/*` | 39C: dompet NB abstrak, tas 9 slot, penyedia pasar `PasarMurid` (kontrak C39.K1) yang tidak menyentuh berkas 38. |
| H12 | **Tidak ada "sibuk" untuk murid.** `util/Sibuk` hanya untuk pemain; setiap modul 36 punya salinan uji bebas sendiri (`Tempat.free`, `Tetua.free`, `GiliranSosial.idle`, `Expeditions.refuses`). | `util/Sibuk.java` | 39A: `util/SibukMurid` dengan registri penyedia (39.10.1). |

Kesimpulan: secara angka murid **bisa** sampai Abadi Sejati (XP tidak dibatasi), tetapi H1 (terobosan butuh guru dekat
dan online) dan H2 (XP berhenti saat tidak dimuat) membuat itu mustahil tanpa guru; H4, H6, H7 dan H8 menghentikan murid
tertentu (tanpa Dao, akar Merah, nyawa rendah) bahkan bila H1 diperbaiki.

#### 39.1.4 Fakta teknis lain yang membentuk rancangan

* **Data murid di chunk yang tidak dimuat tidak bisa disentuh.** Tubuh `DISCIPLE` disimpan di chunk; hanya catatan ringkas
  (nama, ranah, kekuatan) ada di `Halls`. Murid yang sedang pergi (`Halls.storeAway`) disimpan sebagai tag di `AwayStore`
  dan kembali lewat `Halls.addReturnHandler(reason, handler)`. Jadi simulasi luar-layar = **kejar-ketinggalan saat dimuat**
  (untuk murid di rumah) + **catatan perjalanan di `AwayStore`** (untuk murid yang bepergian).
* **Id aktivitas** adalah `byte` tersinkron (`DiscipleEntity.DATA_ACTIVITY`, dibatasi 0-127, tidak disimpan). Terpakai:
  1-12 lama, 13-24 DESIGN 36 (24 cadangan), 25-27 DESIGN 37 (`LangitIds`), 32 `Upacara.ACT_AWAY`. **39 memakai 40-56.**
  Pose klien: `client/sekte/SektePoses.register(activity, Pose, List<Prop>)` (id bebas).
* **Peran tubuh** `DiscipleEntity.Role {DISCIPLE, CANDIDATE, RIVAL, PHANTOM, CLONE}`; hanya `DISCIPLE` disimpan
  (`shouldBeSaved`, `:1525`). 39 menambah `JELMAAN` di akhir enum: tubuh tampilan murid di kota yang tidak pernah
  disimpan (39.6.6).
* **Layar murid**: `DiscipleScreen.MAX_EXTRA_TABS = 2` sudah diisi Hati (P) dan Hubungan (R). 39 menaikkan ke 3.
* **Hub**: seksi MURID berisi 2 kartu 36 (ada ruang); seksi SEKTE sudah 9 kartu (penuh). Kartu 39 masuk MURID.
* **Panduan**: `GuideChapters.CHAPTER_COUNT = 17`; 37 memakai bab 17 (`c17.*`), 38 bab 18-19 (`c18.*`, `c19.*`). 39 memakai
  **bab 20** (`c20.*`), ditambah di akhir setelah 38W.
* **Mata uang**: `NilaiBatu.VALUE {1, 9, 81, 729}` untuk `spirit_stone_low|mid|high|supreme`; kurs sekte `Harga.NB_PER_PK =
  2.0`. PK pribadi murid ada di `SectRanks.pk(data)` / `addPk`; kas aula di `Halls.pool/addPool/spendPool`.
* **Bulan**: `level.getMoonPhase()` (siklus 8 hari) dipakai sebagai irama "pasang qi" (39.4.4). Tidak ada sistem musim
  dunia; turnamen punya `Musim` sendiri, 38 punya `acaraAktif(situsId)` untuk festival kota.

### 39.2 Model data

#### 39.2.1 Per murid: `DiscipleData.extra().mandiri` (ditulis hanya oleh modul 39; dibaca siapa pun)

| Kunci | Tipe | Isi |
|---|---|---|
| `v` | int | versi skema (1) |
| `jalan` | string | id jalan hidup (39.3.1), mis. `pedang` |
| `jalanSkor` | compound | skor terakhir tiap jalan (untuk layar "kenapa") |
| `jalanHari` | long | hari dunia jalan ini dipilih |
| `iblis` | int 0-100 | tingkat godaan Jalan Iblis (39.3.3); ≥ 60 = "Tergoda", ≥ 85 = "Jalan Iblis" |
| `cita` | list ≤ 3 | cita-cita aktif: `{jenis, target, mulai, prog, prio, asal}` (39.3.4) |
| `citaSelesai` | list ≤ 8 | cita-cita tercapai/gagal: `{jenis, target, hari, hasil}` |
| `dorong` | compound | dorongan 0-100: `kultivasi`, `bosan`, `lelah`, `sosial`, `ambisi`, `barang`, `luka` (39.4.1) |
| `kegiatan` | compound | kegiatan berjalan: `{id, mulai, sampai, tempat (long BlockPos), teman (UUID?)}` |
| `riwayat` | int[24] | id kegiatan per jam hari ini (untuk jadwal di layar dan penalti kebaruan) |
| `kemarin` | int[24] | jadwal kemarin |
| `dompet` | long | NB di kantong (≥ 0, ≤ `dompetMaks(ranah)`) |
| `tas` | list ≤ 9 | `ItemStack` tersimpan (codec standar), maks 9 slot (39.6.7) |
| `belanja` | list ≤ 12 | daftar belanja: `{item, n, prio, alasan, maksHarga}` (39.6.3) |
| `perlengkapan` | compound | `pedang` (tier 0-5), `jubah` (tier 0-3, warna), `jimatSaku` (jenis, n) (39.6.7) |
| `terakhir` | long | game time terakhir disimulasikan (kejar-ketinggalan) |
| `tempatTerbaik` | compound | `{pos, faktor}` faktor meditasi terakhir yang diukur di kursi terbaik (39.4.7) |
| `tutup` | compound | Tutup Diri: `{mulai, sampai, tumpuk}` |
| `luka` | compound | `{jenis: TERLUKA/BERAT/TERTAWAN, sampai, sebab}` |
| `tunggu` | compound | antrean keputusan: `terobosan` (hari bar penuh), `dao` (hari menunggu guru), `jenjang`, `dewa` |
| `bebas` | long | game time perintah guru berakhir (jeda perintah, 39.7.2) |
| `kebijakan` | compound | tumpang kebijakan per murid (null = ikut aula): `mode`, `risiko`, `uangSaku`, `izin` (bitmask), `larang` (daftar situs) |
| `statistik` | compound | `perjalanan`, `belanjaNb`, `hasilNb`, `jamKultivasi`, `terobosanSendiri`, `tekSendiri` |

Batas ukuran: ≤ 2 KB per murid tanpa tas; tas ≤ 9 tumpukan. Seluruh bagian ini ikut tablet jiwa (tidak ada salinan kedua).

#### 39.2.2 Per aula: `MandiriData` (SavedData `kultivator:mandiri`, kunci = UUID pemilik aula)

| Kunci | Isi |
|---|---|
| `kebijakan` | kebijakan aula (39.7.1) |
| `harian.<soul>` | cincin Buku Harian ≤ 40 entri per murid: `{hari, jam, jenis, kunci, arg[], suasana, penting}` (teks disusun di klien dari kunci, 39.8.1). Tetap ada saat tubuh tidak dimuat. |
| `kabar` | 30 peristiwa terakhir seluruh aula (untuk kartu hub dan pemberitahuan tertunda) |
| `jalan.<soul>` | catatan perjalanan aktif (cermin dari info `AwayStore`): `{jenis, tujuan, situs, berangkat, tahap[], tiba, pulang, rombongan[], uang, daftar[]}` |
| `tokoh` | registri tokoh bernama ≤ 32 (39.8.3): `{id, nama, peran, situs, watak, sikap.<soul>, hari}` |
| `stokKota.<town>.<hari>` | jumlah barang yang sudah dibeli murid di kota itu hari ini (berbagi stok, 39.6.5) |
| `kas` | log uang saku dan setoran 7 hari: `{hari, soul, jumlah, sebab}` (untuk layar dan audit) |
| `pending.<soul>` | hasil perjalanan yang belum diterapkan (XP, PK, kenangan) sampai tubuh tiba |
| `kotaDikenal` | cache tujuan kota: `{town, dim, pos, jenis, hariCek}` (39.6.2) |

#### 39.2.3 Tipe bersama (paket `sekte.mandiri.api`, data murni, aman di kedua sisi)

`JalanHidup` (enum 10), `JenisCita` (enum 14), `Dorongan` (enum 7), `Kegiatan` (enum ≈ 30, 39.4.2), `Kebijakan` (record),
`ModeMandiri {PENUH, SEIMBANG, PATUH}`, `Kehatian {AMAN, WAJAR, BERANI, NEKAT}`, `JenisJalan` (enum 11, 39.6.1),
`EntriHarian` (record), `Tokoh` (record), `HasilJalan` (record: tahap, rampasan, uang, luka, kenangan).

### 39.3 Jalan Hidup dan Cita-cita

#### 39.3.1 Sepuluh jalan hidup

Setiap murid ber-`DISCIPLE` punya tepat satu jalan. Jalan mengatur **bobot kegiatan**, **cita-cita yang muncul**, **daftar
belanja**, **suara dan pose khas**. Jalan bukan kelas yang mengunci: semua kegiatan tetap mungkin, hanya lebih jarang.

| Id | Nama | Syarat skor terkuat | Kegiatan yang disukai (bobot ×) | Cita-cita khas | Belanja khas | Ciri terlihat |
|---|---|---|---|---|---|---|
| `pedang` | Pendekar Pedang | Dao Pedang atau akar Logam; bakat `sword_sense`; BERANI, AMBISIUS; kenangan turnamen | Latihan Jurus 2,0; Tanding 1,8; Berburu 1,3; Turnamen 2,0 | JUARA, TEKNIK (Pedang), PUSAKA (pedang natal), DENDAM | pedang lebih baik, pil penyembuh, batu roh | latihan kata pedang di lapangan, pedang di pinggang, patroli pedang terbang (bila 37) |
| `alkemis` | Alkemis | bakat `pill_gift`; akar Api/Kayu; RAJIN, TENANG; kenangan kebun | Kebun 1,8; Meramu 2,5; Belajar 1,2; Belanja herba 1,5 | KARYA (pil grade X), RANAH, KEKAYAAN | herba roh, inti binatang, tungku kecil (tier) | duduk di depan kuali, kipas, asap berwarna; tas berbau herba (partikel) |
| `formasi` | Ahli Formasi & Jimat | akar Ruang/Tanah; TENANG; kenangan penjaga formasi | Jaga Formasi 2,0; Menulis Jimat 2,2; Belajar 1,5; Merenung 1,3 | KARYA (jimat), TEKNIK, RANAH | kertas jimat, tinta cinnabar (item `paper`/`red_dye` sebagai bahan), batu roh | meja kaligrafi, kuas, jimat bercahaya; berjalan mengelilingi tiang formasi |
| `pertapa` | Pertapa | TENANG, RENDAH_HATI; pemahaman tinggi; Dao Kehidupan/Ruang; sedikit teman | Meditasi 2,2; Tutup Diri 3,0; Merenung 2,0; Gua Pertapa 2,0 | RANAH (tinggi), DAO (pemahaman), GURU (melampaui) | pil pengumpul qi, batu roh tingkat tinggi, dupa | duduk di tebing/air terjun, segel kertas di pintu saat Tutup Diri |
| `petualang` | Petualang | BERANI, AMBISIUS; akar Angin/Petir; banyak kenangan misi | Perjalanan 2,5; Jelajah Reruntuhan 2,2; Alam Rahasia 2,0; Sosial 1,2 | RAHASIA (alam tier X), RANAH, PUSAKA | jimat seribu li, jimat kembali, ransum, peta (bila 38 menjual) | bekal di punggung, kembali dengan cerita dan luka kecil |
| `pemburu` | Pemburu Binatang | Dao Tubuh/akar Tanah; BERANI; kenangan bunuh | Berburu 2,5; Latihan 1,4; Menjual inti 1,5 | BINATANG (tier X), PUSAKA, KEKAYAAN | busur/pedang, pil penyembuh, umpan | membawa inti binatang pulang, trofi kecil di bahu |
| `pedagang` | Pedagang | LICIK atau AMBISIUS + Kepuasan tinggi; banyak teman; kenangan lelang/lapak | Belanja/Jual 2,5; Lapak Sekte 2,0; Kafilah 1,8; Sosial 1,6 | KEKAYAAN (NB X), JUARA (tawar), JODOH | barang dagangan murah untuk dijual lagi (terbatas, 39.6.11) | menawar dengan isyarat tangan, kotak di punggung, menghitung batu |
| `cendekia` | Cendekiawan Kitab | RAJIN, RENDAH_HATI; pemahaman tinggi; kenangan belajar | Belajar 2,5; Mengajar 2,0; Merenung 1,5; Ziarah 1,2 | TEKNIK (N teknik), DAO, JENJANG (Tetua Ajar) | gulungan teknik, slip giok, tinta | membaca gulungan di paviliun, mengajar junior yang duduk melingkar |
| `penjaga` | Penjaga Sekte | SETIA, BERANI; Pelindung Sekte; kesetiaan tinggi | Jaga/Patroli 2,2; Latihan 1,5; Mengajar 1,3; Tambang 1,2 | MENOLONG (usir N penyerbu), JENJANG, RANAH | pil penyembuh, jimat perisai, jubah sekte | berjaga di gerbang, memberi salam pada yang lewat |
| `iblis` | Jalan Iblis (lapisan, bukan pilihan awal) | lihat 39.3.3 | Tutup Diri gelap 2,0; Pasar Gelap 2,5; Berburu 1,8; Sosial 0,4 | DENDAM, RANAH (cepat) | barang pasar gelap, darah binatang | aura ungu tipis, berbisik sendiri, menghindari guru |

`iblis` tidak pernah dipilih saat dibuat; ia **menimpa** jalan lain (jalan asli disimpan di `jalanAsal`) bila godaan
mencapai 85, dan hilang saat ditebus (39.3.3).

#### 39.3.2 Memilih dan mengganti jalan

Skor tiap jalan `j` (dihitung saat: murid direkrut, kebangkitan (Fana → Pemurnian Qi), naik ke Fondasi, memilih Dao, dan
setiap 10 hari dunia; juga setelah peristiwa besar 39.3.5):

```
skor(j) = 1.0
        + Σ sifat:   +0.6 bila sifat cocok (tabel 39.3.1), −0.4 bila lawannya
        + bakat:     +1.0 (sword_sense → pedang, pill_gift → alkemis, persevering → pertapa, ancient_god → pemburu)
        + Dao:       +1.2 bila Dao cocok; akar searah +0.5
        + kenangan:  +0.15 × Σ bobot kenangan berjenis cocok (Ingatan.ofKind: turnamen.*, misi.*, paviliun.*, kebun.*,
                     hubungan.*, mandiri.<jalan>.*) dibatasi ±1.5
        + kebiasaan: +0.02 × jam kegiatan khas jalan ini dalam 10 hari terakhir, maks +1.0 (murid menjadi apa yang ia kerjakan)
        + guru:      +0.8 bila guru menandai "dorong ke jalan ini" (39.7.3)
        + inersia:   +0.7 untuk jalan sekarang (agar tidak berganti-ganti)
```

Jalan baru diambil bila `skor(baru) > skor(sekarang) + 0.5`. Pergantian menulis kenangan `mandiri.jalan_ganti` (bobot +3),
entri harian khusus ("Hari ini aku tahu: pedang bukan jalanku. Aku ingin meramu."), dan pemberitahuan ke guru. Murid
yang **baru direkrut** mengambil jalan dengan undian berbobot `skor²` (keragaman), sesudahnya selalu argmax.

#### 39.3.3 Jalan Iblis (risiko, bisa ditebus)

Godaan `iblis` (0-100) berubah harian (di langkah harian 39.4.7):

| Penyebab | Δ/hari |
|---|---|
| sifat PENDENDAM / LICIK (masing-masing) | +1 |
| ikatan DENDAM aktif (per ikatan, maks 2) | +2 |
| Kepuasan < 30 | +2 |
| drift ingatan < −15 | +2 |
| bulan baru (`moonPhase == 4`) | +1 |
| berbelanja di Pasar Gelap hari ini | +4 |
| kalah Ujian Hati (39.5.2) | +15 sekali |
| cita-cita DENDAM gagal | +10 sekali |
| sifat SETIA / PENYAYANG / TENANG (masing-masing) | −1 |
| Kepuasan > 70 | −2 |
| Guru menegur/menasihati (aksi tab, 1×/hari) | −6 |
| meminum `heart_calming_pill` (dari tas atau guru) | −10 |
| upacara leluhur hadir (`ANCESTOR_RITE`) | −5 |
| Segel Jiwa (`SoulSeal`) atau pewaris | batas atas 59 (tidak pernah jatuh) |

Ambang: ≥ 60 **Tergoda** (bisikan di gelembung pikiran, mampir ke Pasar Gelap, menyembunyikan sebagian hasil);
≥ 85 **Jalan Iblis**: jalan ditimpa `iblis`; meditasi pada malam bulan baru ×1,3 tetapi setiap terobosan +20 % peluang
Ujian Hati gagal; peluang 2 %/hari mengambil barang Lumbung tanpa izin (dicatat sebagai `DIRAMPAS`-terbalik di kas
log, kenangan buruk murid lain); sekali per 10 hari 25 % peluang "insiden kota" (kejahatan kecil di kota, Nama Baik guru
−3 lewat C39.K3); bila Kesetiaan P jatuh ke `AKAN_PERGI`, kepergiannya berjenis `BERKHIANAT` (sudah ada di P).
**Penebusan**: guru memakai aksi "Tebus" (tab Jalan Hidup) yang memulai ritual 60 s (murid duduk di depan prasasti,
guru dalam 6 blok, `heart_calming_pill` dari guru) → `iblis = 30`, jalan asal kembali, kenangan +9 abadi "Guru
menarikku dari kegelapan". Tanpa guru: murid bisa menebus diri sendiri bila sahabat (`SAHABAT`/`SAUDARA_ANGKAT`) ber-iblis
< 20 menemaninya 3 malam berturut-turut (kegiatan Sosial malam) → `iblis −20` per malam. Saklar `mandiri.jalanIblis`
(bawaan `true`) mematikan seluruh lapisan ini.

#### 39.3.4 Cita-cita (tujuan jangka panjang)

Maks 3 aktif (`prio` 1-3). Diambil dari jalan + keadaan; diperbarui harian. Setiap cita-cita punya fungsi kemajuan murni
yang membaca data yang ada (tanpa data baru selain `prog`).

| Jenis | Target | Kemajuan (sumber data) | Kegiatan yang didorong | Selesai | Gagal / ganti |
|---|---|---|---|---|---|
| RANAH | ranah (+lapis) | `rank.step()` vs target | Meditasi, Tutup Diri, Serap Batu, persiapan terobosan | ranah dicapai → kenangan +6, Kepuasan via `honored(5)` | tidak gagal; target naik |
| DAO | tingkat pemahaman | `comprehension` | Merenung, Latihan Jurus Dao, Gua Pertapa | +5 kenangan | — |
| TEKNIK | N teknik / satu spec / grade | `learnedIds`, `TechniqueSpec.grade` | Belajar, beli gulungan, Mengajar (cendekia) | +4 | spec tak terjangkau 30 hari → ganti |
| PUSAKA | lipatan natal ≥ n / pedang tier ≥ t | `extra.power.natal`, `perlengkapan.pedang` | Berburu (bulu), Belanja, Pesan tempa (C39.K6) | +6 | — |
| JUARA | juara musim / Turnamen Agung (38) | `TurnamenService.champion`, `Elo` | Latihan, Tanding | +8 abadi | kalah final → +ambisi, target tetap |
| DENDAM | jiwa/tokoh tertentu | menang Duel Kehormatan (R) / menang perjumpaan dengan tokoh saingan (39.8.3) | Latihan, Tanding, perjalanan ke wilayah saingan | ikatan DENDAM → SAINGAN, kenangan +7 | 60 hari tanpa hasil → iblis +10 lalu dibuang, atau "memaafkan" bila PENYAYANG |
| JODOH | ikatan KEKASIH/PASANGAN_DAO | `HubunganService.kind` | Sosial, Teh, perjalanan berdua, hadiah | +9 abadi | kekasih gugur → DENDAM atau RANAH (duka) |
| GURU | melampaui guru | `Surpass` (langkah+bintang) | semua kultivasi | `Surpass` terpicu | — |
| JENJANG | jenjang sekte | `SectRanks.rank` | kumpul PK (kerja, misi), permohonan 39.5.5 | +5 | — |
| KEKAYAAN | NB di dompet + sumbangan | `dompet`, `statistik.hasilNb` | Jual, Lapak, Misi Gilda | +4, menyumbang 50 % ke kas aula (SETIA) | — |
| BINATANG | tier binatang dibunuh | `onKill` + catatan perjalanan | Berburu | +5 | — |
| RAHASIA | tier alam rahasia dimasuki | catatan perjalanan | Alam Rahasia | +6 | — |
| MENOLONG | N penyerbu dikalahkan / N desa ditolong | `onKill` saat serbuan, perjalanan Desa | Jaga, Kunjung Desa | +5 | — |
| KARYA | pil/jimat grade X dibuat | statistik meramu/jimat | Meramu, Jimat | +5 | — |

#### 39.3.5 Peristiwa yang mengubah jalan dan cita-cita

| Peristiwa (sumber) | Akibat |
|---|---|
| `TOURNAMENT_ENDED` kalah final | JUARA prio 1; skor `pedang` +0,5 |
| `DISCIPLE_BURIED` sahabat/kekasih | DENDAM (pembunuh, bila tokoh/sekte rival dikenal) atau RANAH prio 1 ("demi dia"); iblis +5 bila PENDENDAM |
| `RELATION_CHANGED` → KEKASIH | JODOH selesai → cita-cita PASANGAN_DAO; perjalanan berdua lebih sering |
| `MISSION_RETURNED` sukses tier tinggi | skor `petualang` +0,4; RAHASIA |
| `DISCIPLE_STUDIED` 3 kali | skor `cendekia` +0,3 |
| Kalah dari tokoh saingan di perjalanan | DENDAM terhadap tokoh itu |
| Ditolong guru saat Tertawan | kenangan +9; skor `penjaga` +0,5 |
| Gagal terobosan 2× berturut | Tutup Diri prio; cita-cita RANAH tetap, iblis +3 |
| Guru menandai jalan (39.7.3) | +0,8 skor jalan itu |
| Melampaui guru (`Surpass`) | GURU selesai; JENJANG (Tetua) atau Bintang |

### 39.4 Perencana harian (AI utilitas, sisi server)

#### 39.4.1 Dorongan (kebutuhan)

Disimpan 0-100 di `extra.mandiri.dorong`, diperbarui **per blok jam** (1 jam dunia = 1000 tick ≈ 50 s) saat dimuat, dan
secara tertutup (rumus, bukan loop) saat kejar-ketinggalan.

| Dorongan | Naik | Turun | Makna |
|---|---|---|---|
| `kultivasi` | +4/jam bila bar < penuh; ×1,5 AMBISIUS; ×0,7 MALAS; +10 setelah melihat murid lain naik ranah | −8/jam Meditasi, −12 Tutup Diri, −5 Latihan | lapar kultivasi |
| `bosan` | +3/jam kegiatan sama dengan jam sebelumnya; +1 per jam di tempat yang sama | −10 kegiatan baru hari ini; −25 perjalanan; −8 Sosial | butuh variasi |
| `lelah` | +6/jam Latihan, Tanding, Kerja, Berburu; +4/jam perjalanan kaki; +2 Meramu/Jimat | −10/jam Istirahat; −4/jam Meditasi; −15 Makan | kelelahan |
| `sosial` | +2/jam sendirian; ×1,5 PENYAYANG; ×0,5 TENANG | −20 Teh/Sosial; −10 kegiatan bersama | butuh teman |
| `ambisi` | dari sifat (AMBISIUS 70, TENANG 30, lain 50) + 10 per cita-cita prio 1 belum selesai > 10 hari | −20 setelah cita-cita tercapai (puas) | dorongan maju |
| `barang` | dihitung: Σ prio item daftar belanja yang belum ada (39.6.3) × 10, maks 100 | 0 setelah belanja | butuh perbekalan |
| `luka` | `(1 − nyawa/maks) × 100`; `luka.jenis` BERAT = 100 | mengikuti nyawa | butuh pulih |

`Kesetiaan` dan `Kepuasan` tidak disalin: dibaca langsung dari `SekteApi.hati()` setiap kali menilai.

#### 39.4.2 Katalog kegiatan

Setiap kegiatan: id enum, aktivitas tubuh (`setActivity`), tempat, durasi blok, efek dorongan, faktor XP `K` (39.5.1),
syarat, dan **pemilik**. "Jembatan" = berkas baru 39 di paket modul lama (39.0 aturan 7).

| Kegiatan | ACT | Tempat | Blok | `K` | Syarat | Pemilik / cara |
|---|---|---|---|---|---|---|
| MEDITASI | 56 `ACT_MEDITASI_MANDIRI` (duduk bermeditasi di titik pilihan; mode guru tidak diubah) | kursi terbaik: `meditasi_<i>`, kursi formasi (`Stacking.fieldAt`), situs roh, pos | 2-6 | 1,5 | — | 39A |
| TUTUP_DIRI | 45 `ACT_TUTUP_DIRI` | kamar/gua kediaman/kursi formasi terbaik | 24-120 (1-5 hari) | 1,8 + 0,05/hari (maks 2,1) | bar ≥ 50 % atau bottleneck dekat; tidak ada perintah | 39B |
| LATIH_JURUS | 40 `ACT_LATIH_JURUS` | lapangan/arena (`Grounds.arena` bila ada) atau dekat pos | 1-2 | 0,6 + penguasaan teknik | ≥ 1 teknik | 39D |
| TANDING | 43 `ACT_TANDING` (berpasangan) | arena / lapangan | 1 | 0,5; +Elo kecil (tidak resmi) | pasangan bebas, keduanya nyawa > 70 % | 39D |
| BELAJAR | 13 `ACT_STUDY` (milik A) | Paviliun | 2-4 | 0,4 | rak ada buku cocok / gulungan di tas | 39B lewat jembatan `PaviliunMandiri` |
| MENGAJAR | 44 `ACT_MENGAJAR` | Paviliun / lapangan | 1-2 | 0,3; junior +0,1 pemahaman, kesempatan ajar (39.5.5) | jenjang ≥ Inti atau `cendekia`; junior ≥ 1 | 39B |
| MERAMU | 41 `ACT_ALKIMIA` | titik dapur (`Duties` kitchen post) atau kuali terdekat yang tercatat | 2 | 0,3 | herba di tas/Lumbung (izin) | 39D |
| JIMAT | 42 `ACT_JIMAT` | meja (titik paviliun / pos) | 1-2 | 0,3 | kertas + tinta di tas | 39D |
| KEBUN | 16 `ACT_TEND` (milik W) | petak herba | 2-3 | 0,3 | petak bebas | 39D lewat `KebunMandiri` |
| TAMBANG | 15 `ACT_MINE` (milik W) | mulut tambang | 2-3 | 0,3 | slot tambang bebas | 39D lewat `KebunMandiri` |
| POS_TUGAS | 1-4 (milik Duties) | pos air/kebun/dapur/ladang | 2 | 0,3 | pos bebas | 39D lewat `Duties.assignSelf` (suntingan aditif) |
| JAGA_FORMASI | 11 (milik keeper) | mata formasi | 3-6 | 0,5 (Perkuat 0,25) | formasi tanpa penjaga | 39D lewat `Keepers.assignSelf` (suntingan aditif) |
| JAGA_GERBANG | (mode GUARD_HALL sementara) | gerbang / tepi (37 `ACT_JAGA_TEPI` bila langit) | 2-4 | 0,4 | `penjaga` atau Pelindung | 39A |
| SOSIAL / TEH | 18 `ACT_SOCIAL` (milik R) | kursi teh / halaman | 1 | 0,2 | teman bebas | 39D lewat `HubunganMandiri.forceTea` |
| MAKAN | 48 `ACT_MAKAN` | pos dapur / meja makan | 1 | 0,2 | jam makan | 39A |
| ISTIRAHAT | 51 `ACT_ISTIRAHAT` | bawah pohon / kamar / bangku | 1-6 | 0,5 (Fana-Fondasi), 0,8 (Inti+, "tidur kultivator") | — | 39A |
| MERENUNG | 53 `ACT_MERENUNG` | tebing, air terjun, tepi pulau (37), makam (A) | 1 | 0,4; pemahaman Dao ×2 | Dao terpilih atau `pertapa` | 39B |
| SERAP_BATU | 54 `ACT_SERAP_BATU` | kursi meditasi | 1 | (39.5.6) | dompet ≥ 9 NB atau batu di tas | 39B |
| JAGA_TEROBOSAN | 55 `ACT_JAGA_TEROBOSAN` | ≤ 8 blok dari sahabat yang menembus | sampai selesai | 0,4 | sahabat sedang terobosan | 39B |
| TEROBOSAN | (milik `DiscipleBreakthrough`) | tempat aman terbuka | sampai selesai | — | 39.5.2 | 39B |
| BERKEMAS | 46 `ACT_BERKEMAS` | depan kamar / Lumbung | 0,2 (200 tick) | — | sebelum/sesudah perjalanan | 39C |
| BERANGKAT / TIBA | 52 `ACT_TERBANG_JAUH` atau jalan kaki ke gerbang | gerbang / panggung pedang | 0,2 | — | perjalanan | 39C |
| PERJALANAN (semua jenis 39.6.1) | — (tubuh pergi lewat `Halls.storeAway`) | luar sekte | 6-120 | (39.6.9) | 39.6 | 39C |
| BERMAIN SALJU/PANEN/FESTIVAL | 18 atau 53 | halaman | 1 | 0,2 | acara (39.4.4) | 39A |
| MERAJUK | 19 `ACT_SULK` (milik P) | — | — | — | P yang memutuskan; 39 tidak pernah memilihnya | P |

Aktivitas 40-45, 48, 51, 53-56 **menahan titik** seperti 13-20 (`holdsActivitySpot`); 46, 47, 49, 50, 52 pose saja. Di
`DiscipleEntity.computeMeditating` (`:1210-1222`) **45, 54 dan 56 duduk bermeditasi** (seperti `ACT_STUDY`), sehingga XP
meditasi lama, regenerasi, Bimbingan Guru dan faktor formasi `DiscipleBridge` bekerja tanpa mengubah mode guru. Id 47
`ACT_BAWA`, 49 `ACT_TAWAR`, 50 `ACT_LIHAT_LAPAK` dipakai tubuh `JELMAAN` di kota dan saat bongkar barang di aula. Aktivitas
40-56 tidak disimpan (dipulihkan sebagai 0 saat dimuat, aturan S15).

#### 39.4.3 Rumus skor dan pemilihan

Untuk murid `m` pada jam `h` dan kegiatan `k`:

```
skor(k) = dasar(k)                                  // 1.0
        × jalan(m.jalan, k)                          // tabel 39.3.1, bawaan 1.0
        × jam(k, h)                                  // kurva irama 39.4.4 (0..2)
        × (1 + Σ_d w(k,d) × (dorong_d / 100))        // bobot dorongan, tabel di bawah
        × sifat(m, k)                                // RAJIN ×1.2 kerja/latih/belajar; MALAS ×1.3 istirahat ×0.7 kerja;
                                                     // BERANI ×1.3 berburu/jelajah; PENAKUT ×0.5 jelajah;
                                                     // PENYAYANG ×1.3 sosial/mengajar; SOMBONG ×1.2 tanding ×0.7 kerja
        × cita(m, k)                                 // ×1.5 bila mendorong cita-cita prio 1, ×1.25 prio 2-3
        × kebaruan(k)                                // ×0.6 bila dilakukan ≥ 3 jam hari ini; ×1.2 belum dilakukan 3 hari
        × izin(k)                                    // 0 bila kebijakan melarang, tempat tidak ada, atau SibukMurid menolak
        × keadaan(m)                                 // GELISAH ×0.7 tugas sekte; MEMBANGKANG: hanya istirahat/merenung/perjalanan
        + inersia                                    // +0.4 untuk kegiatan sekarang bila belum habis bloknya
```

Bobot dorongan `w(k, d)` (yang tidak disebut = 0):

| Kegiatan | kultivasi | bosan | lelah | sosial | ambisi | barang | luka |
|---|---|---|---|---|---|---|---|
| MEDITASI | +1,5 | −0,3 | +0,3 | −0,2 | +0,5 | — | +0,5 |
| TUTUP_DIRI | +2,0 | −1,0 | — | −0,8 | +1,0 | −0,5 | — |
| LATIH_JURUS | +0,5 | +0,3 | −1,0 | — | +0,8 | — | −1,5 |
| TANDING | +0,3 | +0,6 | −1,2 | +0,6 | +1,0 | — | −2,0 |
| BELAJAR | +0,4 | +0,2 | −0,2 | — | +0,6 | — | — |
| MERAMU / JIMAT | — | +0,4 | −0,4 | — | +0,4 | −0,6 | — |
| KEBUN / TAMBANG / POS | — | +0,2 | −0,8 | +0,2 | — | — | −1,0 |
| SOSIAL / TEH | — | +0,6 | +0,3 | +2,0 | — | — | +0,2 |
| ISTIRAHAT | — | −0,5 | +2,5 | — | −0,5 | — | +2,0 |
| MAKAN | — | — | +1,0 | +0,4 | — | — | +0,3 |
| PERJALANAN Belanja | — | +1,0 | −0,6 | +0,3 | +0,3 | +2,5 | −1,5 |
| PERJALANAN lain | +0,3 | +1,5 | −0,8 | +0,2 | +1,2 | — | −2,0 |
| MERENUNG | +0,6 | +0,2 | +0,2 | −0,3 | +0,3 | — | — |

Pemilihan: ambil 3 skor teratas, undi berbobot `skor^(1/T)` dengan suhu `T` = 0,5 (TENANG, RAJIN), 1,0 (bawaan),
1,6 (MALAS, BERANI, LICIK). Perjalanan hanya bisa dipilih di **jam keberangkatan** (05-09) dan bila tidak ada perjalanan
lain dari aula dalam 1 jam terakhir (antrian keberangkatan, 39.9). Semua angka di config `mandiri.bobot` (tabel di atas
sebagai bawaan); pengujian lewat `uji mandiri skor <murid>` yang mencetak tiga teratas beserta setiap faktor.

#### 39.4.4 Irama harian, bulan dan acara

Jam = `(dayTime / 1000 + 6) mod 24` (rumus yang sama dengan 38.N). Kurva `jam(k, h)` bawaan:

| Jam | Irama | Kegiatan yang naik (×1,5-2,0) | Yang turun (×0,2-0,5) |
|---|---|---|---|
| 05-06 | Salam pagi: semua murid bebas berkumpul di halaman 10 menit (gerak napas pagi, pose `ACT_MERENUNG` berjajar), lalu bubar | MERENUNG, MEDITASI | TANDING |
| 06-12 | Kultivasi pagi (qi pagi) | MEDITASI, LATIH_JURUS, TUTUP_DIRI, keberangkatan perjalanan | ISTIRAHAT |
| 12-13 | Makan siang | MAKAN, SOSIAL | LATIH |
| 13-18 | Kerja dan belajar | KEBUN, TAMBANG, POS, MERAMU, JIMAT, BELAJAR, MENGAJAR | TUTUP_DIRI (mulai) |
| 18-19 | Makan malam | MAKAN, TEH | — |
| 19-22 | Waktu bebas | SOSIAL, TANDING, LATIH, MERENUNG | KERJA |
| 22-05 | Malam | MEDITASI (Inti+), ISTIRAHAT (Fana-Fondasi 4 jam wajib), JAGA (penjaga) | semua yang bergerak |

Irama bulan (`moonPhase`): purnama (0) = **pasang qi**: MEDITASI malam ×1,4 dan `K` meditasi malam +0,1; bulan baru (4) =
godaan iblis +1, PERJALANAN malam ×0,3. Acara: hari turnamen (`TurnamenService.running`) semua menonton/latihan; festival
kota (`KotaBesarApi.acaraAktif`) menaikkan PERJALANAN Belanja ke kota itu ×2 (39.6); serbuan (`SectRaids`) membatalkan
semua kegiatan non-jaga (murid kembali ke perilaku tempur lama); hujan: LATIH luar ×0,5, TEH ×1,5; salju (biome dingin):
kegiatan BERMAIN_SALJU kecil untuk Fana-Fondasi (pose lempar bola salju, partikel salju vanilla).
Hari ke-`n mod 7 == 6` (hari istirahat sekte): KERJA ×0,3, SOSIAL ×1,5, perjalanan Belanja ×1,5.

#### 39.4.5 Kebijakan dan masker

Kebijakan efektif = tumpang murid (bila ada) atas kebijakan aula. `izin(k)` = 0 bila:
* `mode == PATUH`, atau murid punya perintah guru aktif (39.7.2);
* kegiatan dimatikan di daftar izin (bitmask 16 kelompok: kultivasi, latihan, tanding, belajar, kerajinan, kerja, sosial,
  jaga, belanja, misi gilda, berburu, reruntuhan, gua, alam rahasia, berkelana, desa/sekte);
* tujuan perjalanan ada di daftar `larang` (situs atau jenis: mis. "pasar_gelap", "kota_air");
* `SibukMurid.why(entity)` tidak null (39.10.1);
* kegiatan butuh tempat yang tidak ada (tidak ada paviliun → BELAJAR 0);
* `Kehatian` lebih rendah dari risiko perjalanan (39.5.9).

`mode == SEIMBANG` (bawaan dunia baru): perjalanan ≤ 2 hari, maks 25 % murid pergi bersamaan, tugas sekte dapat bobot
×1,2. `mode == PENUH`: perjalanan ≤ 5 hari, maks 40 % pergi, keputusan Dao/terobosan/jenjang tanpa menunggu guru
(39.5), murid boleh pindah ke Tutup Diri hingga 5 hari.

#### 39.4.6 Penjadwalan murah

* **Satu penilai per aula** (`Perencana.tick(server)` dari `ServerTickEvents.END_SERVER_TICK`, bukan per entitas). Murid
  dinilai pada tick `(soulHash mod 1000)` dalam tiap jam dunia → 100 murid = 0,1 penilaian per tick. Plafon global
  `mandiri.penilaianPerTick` = 4 (sisanya digeser ke tick berikutnya).
* Penilaian = ±30 kegiatan × perkalian sederhana; tanpa alokasi (array tetap per kegiatan), tanpa pencarian blok.
  Tempat dicari dari **cache titik aula** (jangkar `post_*`, `meditasi_<i>`, `library`, `formation`, `arena`, kursi teh R,
  petak W) yang dibangun sekali per 1200 tick per aula, bukan pemindaian dunia.
* **Anggaran gerak**: maks `mandiri.gerakMaks` = 10 murid per aula berjalan (navigasi aktif) pada saat yang sama; murid
  lain yang memilih kegiatan di tempat lain menunggu (tetap di tempat, kegiatan berikutnya yang tidak butuh jalan
  diutamakan). Navigasi memakai `DuniaBudget.throttled(entity, 4)` dan `moveTo` biasa.
* Pengisian layar/info hanya saat layar dibuka (`DiscipleHooks.addInfo` ringan; jadwal dan buku harian dikirim dengan
  payload sendiri saat diminta).

#### 39.4.7 Simulasi luar-layar (kejar-ketinggalan malas)

Saat tubuh tick20 pertama setelah dimuat (atau tiba dari `AwayStore`), bila `now − terakhir > 2400` tick:

1. `D = min(now − terakhir, mandiri.kejarMaksHari × 24000) / 24000` (bawaan maks 30 hari).
2. **XP tertutup**: `xp = D × xpHarian(m)` dengan `xpHarian = 50 × 24 × meditationBaseTanpaMode(data) × K̄(jalan, kebijakan)
   × xpMultiplier(null, data, MEDITATION) × min(faktorTempat, tempatTerbaik.faktor) × skalaLuarLayar`. `K̄` = rata-rata
   `K` dari jadwal khas jalan itu (tabel 39.5.1), `skalaLuarLayar` = 0,75 (tanpa irama hidup yang lengkap). Diterapkan
   lewat `DiscipleCultivation.distribute` (sudah bekerja tanpa tubuh); di bottleneck sisa menjadi Qi Tersimpan seperti
   aturan lama. **Terobosan tidak pernah terjadi luar-layar**; ia diantrekan (`tunggu.terobosan`) dan dijadwalkan dalam
   jam dunia pertama setelah dimuat (39.5.2).
3. Dorongan diset ke keadaan setimbang jalan itu (bukan dilacak per jam).
4. **Peristiwa**: undi `min(10, ceil(D / 2))` peristiwa kecil dari tabel jalan (belajar satu teknik dari rak bila
   memenuhi syarat, membuat 1-3 pil dari herba Lumbung yang diizinkan, panen, latihan memperdalam penguasaan, teh dengan
   sahabat (`HubunganService.adjust` +1..+3), uang saku diterima). Setiap peristiwa = satu entri harian ber-tanggal masa lalu.
5. Uang saku: `D × uangSaku` dibayar dari kas aula sekaligus (39.6.4), dibatasi isi kas.
6. Simpan `terakhir = now`. Kerja per murid O(1) + ≤ 10 peristiwa; maks 4 murid diproses per tick (antrian).

Murid yang sedang **bepergian** tidak memakai langkah ini: hasilnya dihitung oleh catatan perjalanan (39.6.9) dan
diterapkan dari `MandiriData.pending.<soul>` saat tiba.

#### 39.4.8 Perilaku terlihat saat dimuat

Setiap kegiatan punya pose dan properti klien (39E, `SektePoses.register`), suara (39.8.4) dan gelembung pikiran
(memakai renderer gelembung P bila ada: `KepribadianPayloads.Letup` dibaca P; 39 mengirim payload sendiri
`mandiri_pikir(entityId, ikon, kunci)` dan klien 39E menggambar gelembung kecil yang sama gayanya, ≤ 6 gelembung dalam 32
blok). Daftar animasi (semua pose dipadukan 6 tick, hanya saat diam; kecepatan Minimal/Normal/Maks):

| ACT | Animasi | Properti | Partikel/efek |
|---|---|---|---|
| 40 Latih Jurus | siklus 4 kata 3 s: tusuk, tebas melingkar, tangkis, kuda-kuda; lengan kanan memegang pedang; badan memutar 30° | pedang lurus (`sekte_mandiri_pedang_latih`) | jejak busur tipis 2 quad per tebasan (Minimal: tanpa) |
| 41 Meramu | duduk bersila, tangan kiri mengipas 1,2 Hz, tangan kanan sesekali menabur | kuali kecil + kipas | asap warna Dao pil 1 quad/0,5 s, kilat kecil saat berhasil |
| 42 Jimat | duduk, kepala menunduk, kuas bergerak zig-zag | meja rendah + kertas + kuas | jimat jadi melayang 1 s lalu masuk lengan |
| 43 Tanding | dua murid berhadapan, salam kepalan, 6 pertukaran pose terjadwal (serang/tangkis bergantian), salam penutup | pedang kayu | percikan kecil saat beradu (suara kayu) |
| 44 Mengajar | berdiri, tangan di punggung, sesekali menunjuk; junior duduk (pose `ACT_STUDY`) | gulungan | — |
| 45 Tutup Diri | duduk diam, mata tertutup (lapisan wajah), napas naik-turun lambat | kertas segel melayang di depan | lingkar qi redup berputar (≤ 8 quad) |
| 46 Berkemas | berlutut, memasukkan barang ke buntalan | buntalan | — |
| 47 Bawa | berjalan dengan kotak di punggung, langkah sedikit berat | kotak/buntalan besar | — |
| 48 Makan | duduk, mangkuk di tangan kiri, sumpit naik-turun | mangkuk + sumpit | uap tipis |
| 49 Tawar | berdiri menghadap pedagang, tangan kanan mengacungkan jari (1-3), menggeleng/mengangguk | kantong batu | koin kecil berkilat saat sepakat |
| 50 Lihat Lapak | membungkuk 20°, mengangkat barang, memutar di tangan | barang yang dilihat (ikon item) | — |
| 51 Istirahat | berbaring miring / bersandar ke pohon | — | "Z" kecil (Fana-Fondasi saja) |
| 52 Terbang Jauh | berdiri di atas pedang, lengan menyilang; server menggerakkan naik spiral 20 blok lalu melesat (pola 37 `PatroliPedang`) | pedang terbang di bawah kaki | jejak cahaya |
| 53 Merenung | berdiri/duduk menghadap pemandangan, kepala sedikit menengadah | — | daun/kelopak sesekali (biome) |
| 54 Serap Batu | duduk, dua tangan terbuka; batu roh melayang mengitari lalu hancur menjadi cahaya | batu roh (3 tingkat warna) | serpih cahaya terserap ke dada |
| 55 Jaga Terobosan | berdiri membelakangi yang menembus, lengan bersilang, menoleh ke arah sambaran | — | perisai tipis saat sambaran jatuh |
| 56 Meditasi Mandiri | duduk bersila di kursi pilihan (pose meditasi lama), menghadap pemandangan atau prasasti | — | aura lama; di kursi formasi cincin formasi lama |

### 39.5 Tumbuh sendiri sampai Abadi Sejati

#### 39.5.1 Model XP dan proyeksi laju

Meditasi yang dipilih perencana memakai **jalur XP lama** (tick meditasi `DiscipleCultivation.tick`); 39 hanya menambah
faktor lewat `DiscipleHooks.addXpFactor` untuk sumber `MEDITATION`:

* MEDITASI mandiri saat mode bukan MEDITATE: ×1,5 (sama dengan perintah Meditasi; murid yang tekun atas kemauan sendiri
  tidak dihukum). Bila mode sudah MEDITATE: ×1,0 (tidak dobel).
* TUTUP_DIRI: ×1,8 + 0,05 per hari pengasingan (maks ×2,1), menggantikan ×1,5.
* `skalaXp` (config, bawaan **1,0**; usulan **1,5 perlu persetujuan pengguna**): dikalikan hanya pada XP yang berasal dari
  kegiatan mandiri dan kejar-ketinggalan, tidak pada meditasi yang diperintah atau Bimbingan Guru.

Kegiatan yang bukan duduk bermeditasi memberi XP "napas sambil bekerja" di akhir bloknya lewat metode baru
`DiscipleCultivation.addXpKegiatan(entity, amount)` (39B): `amount = 50 × meditationBaseTanpaMode(data) × K × skalaXp`,
pengali `xpMultiplier(null, data, MEDITATION)` (entity null → tanpa faktor formasi/situs, karena murid tidak duduk di sana),
lalu `distribute` + FX naik tahap yang sama. `meditationBaseTanpaMode` = `meditationBase` tanpa ×1,5 mode (metode baru,
pure). Nilai `K` di 39.4.2.

Jumlah `K` per hari menurut jalan (jadwal khas tanpa perjalanan; dipakai juga untuk `K̄` luar-layar = ΣK/24):

| Jalan | Susunan jam khas (24 jam) | ΣK/hari |
|---|---|---|
| Pertapa | Meditasi 14, Tutup Diri sering, Merenung 3, Istirahat 4, Makan 1, Sosial 1, Belajar 1 | ≈ 28 |
| Seimbang umum (cendekia, penjaga, alkemis, formasi) | Meditasi 9, Latihan 2, Kerja/kerajinan 3, Belajar 2, Sosial 2, Makan 1, Istirahat 5 | ≈ 19,5 |
| Pedang / Pemburu | Meditasi 7, Latihan 4, Tanding 1, Merenung 2, Kerja 2, Sosial 2, Makan 1, Istirahat 5 | ≈ 18 |
| Petualang / Pedagang | seperti Seimbang di rumah; hari perjalanan `K` 0,8 rata-rata + kesempatan | ≈ 17-19 |
| Perintah Meditasi 24/7 lama (pembanding) | duduk di pos sepanjang hari | 36 |

Proyeksi **hari dunia** (1 hari = 20 menit) dari Fana sampai Abadi Sejati Awal, dihitung dari rumus kode
(`Rank.xpRequired`, `Meditation.baseRate`, `meditationBase`): akar Mid (×1,0), Murid Dalam (×0,70), formasi aula ×1,2,
batu+kesempatan +15 %. Kenaikan Abadi (9 lapis) memakan ≈ 72 % dari total.

| Skenario | QR | F | GC | NS | DT | DU | IA (9 lapis) | Total `skalaXp` 1,0 | Total `skalaXp` 1,5 |
|---|---|---|---|---|---|---|---|---|---|
| Seimbang, Mid, Dalam | 1,2 | 3,5 | 9,6 | 24,3 | 25,9 | 25,9 | 233 | **≈ 324 hari** (108 jam main) | ≈ 216 hari |
| Pertapa, Mid, Dalam | 0,8 | 2,5 | 6,7 | 16,9 | 18,0 | 18,0 | 162 | ≈ 225 | ≈ 150 |
| Seimbang, Surgawi, Inti | 0,6 | 1,8 | 4,9 | 12,5 | 13,3 | 13,3 | 120 | ≈ 167 | ≈ 111 |
| Seimbang, Rendah, Luar, tanpa formasi | 2,0 | 5,8 | 15,8 | 40,0 | 42,7 | 42,7 | 384 | ≈ 533 | ≈ 355 |
| Luar-layar terus (Seimbang Mid Dalam, ×0,75) | 1,7 | 4,9 | 13,2 | 33,5 | 35,8 | 35,8 | 322 | ≈ 447 | ≈ 298 |
| Pembanding lama: perintah Meditasi 24/7, Mid, Dalam, formasi ×1,2 | 0,8 | 2,2 | 6,0 | 15,1 | 16,1 | 16,1 | 145 | ≈ 202 (tidak berubah) | — |
| Pembanding: dibimbing guru (Bimbingan Guru + pil + Hu Fa guru) | — | — | — | — | — | — | — | lebih cepat dari semua di atas | — |

Jenjang naik seiring waktu (Luar 0,60 → Dalam 0,70 → Inti 0,80 → Tetua 0,90), jadi murid nyata berada di antara baris.
Perintah `uji mandiri proyeksi <murid>` mencetak tabel ini untuk murid tertentu dengan faktor nyatanya. Keputusan angka
untuk pengguna: **bawaan 1,0 menjaga keseimbangan lama**; 1,5 membuat murid Seimbang sama cepat dengan murid yang
diperintah bermeditasi penuh, tetap lebih lambat daripada yang dibimbing.

#### 39.5.2 Terobosan sendiri

**Kesiapan** (`breakthroughBlockerSelf(entity, Persiapan p)`, 39B): semua cek lama `breakthroughBlocker` **kecuali** dua cek
guru (`bt_offline`, `bt_master_far`); cek "doomed" memakai faktor Hu Fa sahabat yang dijanjikan `p` (39.5.3); kait
`DiscipleHooks.breakthroughBlocker` (mis. tutup Merah) tetap berlaku.

**Urutan perencana** (status di `tunggu.terobosan`):

1. **Bar penuh di bottleneck** → `tunggu.terobosan = hari`; pemberitahuan ke guru: "<Nama> siap menembus <ranah>. Jaga dia,
   atau biarkan ia menembus sendiri?" dengan dua tombol klik (`[Saya akan menjaga]` → menunggu guru sampai 2 hari;
   `[Biarkan]` → lanjut).
2. **Menunggu guru** (Seimbang: `tungguGuruHari` 2; Penuh: 0; Patuh: tak terbatas). Selama menunggu murid bermeditasi
   (Qi Tersimpan bertambah seperti biasa). Bila guru datang dalam 16 blok, jalur lama (guru memulai dari layar) tetap.
3. **Persiapan** (kegiatan terlihat, tiap langkah satu blok):
   * Pulih: ISTIRAHAT/MEDITASI sampai nyawa 100 % dan qi penuh (wajib untuk tribulasi; tanpa ini murid tidak memulai).
   * Pil: telan dari tas lewat `feedSelf` (39.5.6): `foundation_pill` (QR→F), `golden_core_pill` (sebelum tribulasi Inti
     Emas dan Jiwa Baru Lahir), `tribulation_warding_pill` (NS+ bila ada), `heart_calming_pill` sebelum Ujian Hati. Tidak
     punya → masuk daftar belanja prio 1 dan perjalanan Belanja diutamakan, kecuali murid "tergesa" (di bawah).
   * Hu Fa sahabat: undang ≤ 2 teman (ikatan SAHABAT/SAUDARA_ANGKAT/KEKASIH/PASANGAN_DAO atau afinitas ≥ 40, ranah ≥ ranah
     murid − 1, bebas menurut `SibukMurid`) → mereka berjalan ke 4-6 blok dan memulai `ACT_JAGA_TEROBOSAN` (55). Tetua yang
     bebas juga bisa (×0,90).
   * Tempat: titik terbuka di wilayah (cache: `arena`, halaman prasasti, `meditasi_<i>`, tepi pulau 37), tidak di bawah atap
     (pemeriksaan `canSeeSky` satu kali), jauh ≥ 12 blok dari pos kerja yang ramai.
4. **Ujian Hati** (hanya gerbang besar: masuk Jiwa Baru Lahir, Penyatuan Dao, Kenaikan Abadi lapis 5, Abadi Sejati): undian
   data sebelum mulai, `gagal = clamp(0.05 + iblis/200 + (60 − Kepuasan)/300 − drift/200 − 0.10·guruDekat − 0.04·sahabat
   − 0.10·heart_calming, 0.02, 0.50)`. Terlihat: kabut gelap 3 s di sekitar murid, murid gemetar (pose), suara
   `climb_illusion` (36) atau `mandiri_hati_lulus`. Gagal: tidak menembus, Qi Deviation 3600 tick (angka lama), bar −10 %,
   iblis +15, kenangan −5 "Iblis hati menang hari ini"; boleh mencoba lagi setelah 2 hari.
5. **Mulai**: `startBreakthroughSelf(entity, p)` memanggil `DiscipleBreakthrough.start(entity, level, @Nullable master)`
   (dibuat nullable; pesan obrolan hanya bila guru online, selain itu ke Buku Harian + antrian pemberitahuan). Aturan,
   omen, sambaran, Pelindung Dao, keberhasilan, kegagalan (Deviation 3 menit, bar 70 %), `FoundationTypes`, hex pewaris,
   `BreakthroughListener` **semuanya tidak berubah**.

**Tergesa**: murid AMBISIUS atau SOMBONG dengan `ambisi > 80` 25 % melewati langkah 3 kecuali "Pulih" (nyawa tetap harus
penuh untuk tribulasi). Akibat: kualitas terobosan −0,1 lewat `DiscipleHooks.addQualityBonus` (hanya untuk percobaan itu).
Kebijakan "Hati-hati terobosan" (39.7.1) mematikan tergesa.

**Luar-layar**: terobosan tidak pernah terjadi luar-layar; saat dimuat kembali, terobosan yang tertunda dijadwalkan dalam jam
dunia pertama (maks 1 terobosan bersamaan per aula, antrean 1 per 2 menit agar langit tidak penuh awan petir).

#### 39.5.3 Hu Fa sahabat

Penyedia `DiscipleHooks.addStrikeFactor` (39B): untuk murid yang sedang tribulasi, setiap penjaga ber-`ACT_JAGA_TEROBOSAN`
dalam 8 blok yang hidup dan bebas: sambaran ×0,93 (maks 2 penjaga → ×0,865; Tetua ×0,90). Penyedia
`addFoundationChance` +0,04 per penjaga (gathering) dan `addQualityBonus` +0,03 per penjaga. Hu Fa guru tetap jauh lebih
kuat (×0,8, +10 %, +0,1, Tahan Petir), dan bila guru hadir faktor sahabat dibatasi ×0,93 total (aturan "guru tetap
terbaik"). Penjaga ikut melawan Pelindung Dao (perilaku tempur lama: diserang, membalas). Setelah sukses:
`HubunganService.adjust(+8, "hufa")` dan kenangan +5 kedua pihak. Cek "doomed" mandiri: `nyawa ≤ totalShare × Π faktor
penjaga × faktor formasi` → tidak mulai.

#### 39.5.4 Memilih Dao sendiri

Saat Inti Emas tanpa Dao: tunggu guru `tungguDaoHari` (Seimbang 3 hari, Penuh 0, Patuh tak terbatas; pemberitahuan berisi
usulan murid). Skor per Dao: akar searah +2; jalan (pedang→SWORD +2, alkemis→FIRE/LIFE +1,5, formasi→SPACE +1,5,
pertapa→LIFE/SPACE +1, pemburu→BODY +2, petualang→LIGHTNING/SHADOW +1, penjaga→BODY/ICE +1); +0,5 per teknik Dao itu yang
diketahui; +0,5 Dao guru (meneladani); +0,3 Dao sahabat terdekat. Argmax. Upacara: malam berikutnya murid MERENUNG di
tempat terbuka 1 blok, lalu `chooseDaoSelf(entity, dao)` (39B) dengan FX lama `chooseDao` (pilar, cincin, ledakan).

#### 39.5.5 Belajar teknik sendiri dan naik jenjang

Lima sumber, semuanya memakai gerbang lama `specQualifies` + kait `DiscipleHooks.teachBlocker`:

| Sumber | Cara | Batas |
|---|---|---|
| Rak Paviliun (ada) | BELAJAR memesan kursi lewat `PaviliunMandiri.mintaBaca(entity)`; sisanya mesin `BelajarMandiri` | 1 teknik/hari (aturan A) |
| Gulungan di tas | slip giok yang dibeli dengan **PK pribadi** di Paviliun Yue Lu (`KontribusiMurid.beli(entity, TECHNIQUE, specId)`, harga lama `Contribution.Ware.TECHNIQUE` menurut grade) atau rampasan; dibaca seperti rak (`PaviliunMandiri.bacaTas`) | 1/hari; gulungan dari tas boleh disetor ke rak sesudahnya (pilihan SETIA) |
| Tetua mengajar tanpa guru online | `SectRanks.elderTeaches`: syarat `master != null` dilonggarkan bila kebijakan bukan Patuh (pesan ke Buku Harian) | aturan lama (1/hari, 30 % qi tetua) |
| Murid senior mengajar | MENGAJAR (Inti+): satu teknik yang diketahui senior ke junior dalam 6 blok, 30 % qi senior, `DiscipleData.learn` + `Mastery.setDiscipleLevel(level senior − 2)`; penyedia `teachBlocker` dipanggil dengan guru `null` (dua penyedia sekarang, `SpecialCandidates` dan `SectRanks.slotsFull`, tidak memakai argumen guru: verifikasi ulang saat implementasi) | 1/hari per senior, junior 1/3 hari |
| Pencerahan | MERENUNG/TUTUP_DIRI: peluang harian `0.02 + comprehension/5000` (maks 0,08) memahami satu spec **Dao sendiri** yang `teachable`, bukan Dewa, bukan seni lama ber-kait, grade ≤ batas ranah (Fondasi: grade 1, Inti 2, NS 3, DT 4, DU 5, IA 6, TI semua non-Dewa); undian berbobot ke grade rendah | 1 per 10 hari |

Tawaran Dewa (9 bintang): bila guru tidak memilih dalam 3 hari (Seimbang) atau langsung (Penuh), murid memilih dari
`dewaPending` dengan skor jalan (`chooseDewaSelf`, 39B).

**Naik jenjang**: murid yang memenuhi semua syarat lama (`SectRanks.requirements`, versi tanpa guru `requirementsSelf(data,
soul)`) mengajukan permohonan. Disetujui otomatis bila kebijakan Penuh, atau oleh Tetua Agung yang ada (Seimbang), selain
itu masuk antrian guru (tombol klik). PK pribadi dibayar seperti biasa (`PK_COST`). Luar → Dalam tetap menjalankan Latihan
Khusus lama (dimulai saat murid dimuat). `promoteSelf(entity)` (39B) = `promote` dengan guru nullable (advancement hanya bila
guru online).

#### 39.5.6 Pil, batu roh, akar

* `feedSelf(entity, ItemStack stack)` (39B): jalur `feedBlocker` + `applyAlchemy`/`applyVanilla`/`applyMarrow` yang sama
  (bagian 70 %, jeda 1 per jenis per menit, kait `pillEffectFactor`, `onFed`), tetapi mengambil 1 item dari tas, bukan dari
  tangan guru. Bond tidak bertambah (bond = hubungan dengan guru). Terlihat: murid membuka botol (pose singkat), pil
  bercahaya, suara lama.
* Aturan menelan (perencana): penyembuh bila `luka > 40`; pengumpul qi saat Tutup Diri; pil terobosan di 39.5.2;
  `heart_calming_pill` bila `iblis > 50`; `toxin_purging_pill` saat Deviation; `marrow_cleansing_pill` bila akar ≤ Rendah
  (prio 1 bila tertahan tutup Merah, H7); `bone_tempering_pill` kapan saja sampai batas lama.
* **Jatah Lumbung** (kebijakan, bawaan: `healing_pill`, `qi_gathering_pill`, `qi_recovery_pill` boleh diambil 1/hari;
  pil terobosan dan Pembersih Sumsum butuh izin guru per jenis): `Storehouse.take(owner, filter, 1)`, dicatat di `kas`
  dan Buku Harian ("Mengambil 1 Pil Penyembuh dari Lumbung").
* **Serap Batu** (54): satu blok = `K` 3,0 sebagai ganti 1,5; biaya `4 × (ranah + 1)` NB dari dompet; maks 2 blok/hari.
  Visual batu menurut ranah (Rendah QR-F, Menengah GC-NS, Tinggi DT-IA, Agung TI). Paling banyak menambah ≈ 15 % XP
  harian; ini penyerap uang, bukan jalan pintas.
* Akar Merah (H7): rantai lengkap tanpa guru = kumpul NB → beli Pil Pembersih Sumsum di kota (Normal, 80 NB dasar, peluang
  lama `MarrowCleansing.chance`) atau di Paviliun Yue Lu dengan PK pribadi (Sempurna, 400 PK, aula 6) → `feedSelf` →
  grade naik → `Talents.soulColor` naik → tutup Merah lepas.

#### 39.5.7 Tutup Diri (pengasingan)

Dipilih bila `kultivasi` tinggi dan (bar ≥ 50 % atau terobosan < 3 hari lagi); jalan `pertapa` sering. Murid berjalan ke
kamar/kursi terbaik, menempel kertas segel (prop), duduk `ACT_TUTUP_DIRI` 1-5 hari (Seimbang ≤ 2). Tidak makan, tidak
diajak teh (`SibukMurid` "tutup_diri"). Dihentikan oleh: perintah guru, serbuan, atau murid lain terluka berat (PENYAYANG).
Selesai: segel terbakar (partikel), cincin qi (`Fx.ring` warna Dao), suara `mandiri_tutup_keluar`, entri harian. Bila bar
penuh di tengah pengasingan: terobosan langsung di tempat (persiapan "Pulih" sudah terpenuhi). Luar-layar: periode
pengasingan memakai `K̄` = 1,9.

#### 39.5.8 Melampaui guru, bintang, bencana

* `Surpass` terpicu sendiri (langkah + bintang); 39 hanya menambah cita-cita GURU dan permohonan "Tanding dengan Guru"
  (pemberitahuan klik, sekali per 7 hari bila murid AMBISIUS/SOMBONG).
* Bintang: murid mandiri di TI Puncak mengaktifkan "Otomatis" bila kebijakan bukan Patuh (toggle lama `Stars`).
* Bencana Bintang tetap dimulai guru; murid mengajukan permohonan (pemberitahuan). Di luar target Abadi Sejati.

#### 39.5.9 Risiko, luka dan Kehati-hatian

| Kehati-hatian | Tier tujuan maks | Mundur saat nyawa | Malam di jalan | Boleh gugur |
|---|---|---|---|---|
| AMAN | kekuatan − 2 | 60 % | tidak | tidak pernah |
| WAJAR (bawaan) | kekuatan − 1 | 40 % | ya, di penginapan | tidak pernah |
| BERANI | kekuatan | 25 % | ya | tidak pernah |
| NEKAT | kekuatan + 1 | 10 % | ya | bila `bolehGugur` menyala |

`kekuatan` = rumus `Expeditions.memberPower` (ranah + tahap/4 + lapis/9 + bintang/9; disalin sebagai fungsi murni karena
package-private). Sifat menggeser satu tingkat: BERANI +1, PENAKUT −1; murid tidak pernah lebih nekat daripada kebijakan.

| Akibat | Peluang (per kegagalan tahap) | Efek | Pemulihan |
|---|---|---|---|
| Terluka | 70 % | pulang dengan nyawa 30 %, Deviation 3600 tick (sama dengan X) | biasa |
| Luka Berat | 25 % | `luka.BERAT` 2-4 hari: tanpa tanding/perjalanan; nyawa maks sementara −30 % | Balai Tabib (38, `15 × (band+1)` NB), pil penyembuh 3×, atau guru memberi pil |
| Tertawan | 5 % (begal, sekte rival, razia Pasar Gelap) | murid tetap di `AwayStore`; muncul **misi penyelamatan** untuk guru: penanda peta di situs nyata (kemah begal 38 / gerbang sekte rival / kumuh), 3 hari dunia | guru datang (murid dilepas saat penjaga dikalahkan atau tebusan `30 × (band+1)` NB dibayar); lewat 3 hari: murid lolos sendiri dengan Luka Berat, kenangan −6 "Guru tidak datang" (`DIABAIKAN` 3) |
| Gugur | hanya NEKAT + `bolehGugur` + kegagalan tahap terakhir 10 % | `Disciples.markFallen` (tablet jiwa seperti kematian lain) | kebangkitan lama |

### 39.6 Perjalanan, kota dan belanja

#### 39.6.1 Jenis perjalanan

| Jenis | Tujuan nyata | Lama (tanpa jalan) | Risiko dasar | Hasil utama |
|---|---|---|---|---|
| BELANJA | kota (`kota/kota`, `kota/kota_abadi`, Kota Besar 38 `kota/kota_besar`), pasar malam ibu kota, pos singgah 38 | 3-8 jam di kota (+ menginap bila tiba ≥ 18:00) | tier 1 (jalan) | barang daftar belanja, hadiah, kabar |
| MISI_GILDA | Papan Misi kota / Gilda Kultivator ibu kota (38) | 1-2 hari | tier = pangkat misi (E … S) | NB, lencana murid, kenangan |
| JUAL | kota / Lapak Sekte 38 | digabung dengan BELANJA | tier 1 | NB dari karya murid |
| BERBURU | padang/hutan 600-2000 blok; Sarang Binatang 38GU | 0,5-1 hari | tier binatang | inti binatang, bulu ekor roh (natal), XP bunuh simulasi |
| RERUNTUHAN | `reruntuhan/gua_kecil`, `gua_agung`, `gua_kuno`, `menara`, `penjara` (34) | 1-2 hari | tier situs | `Expeditions.lootTable`, halaman kitab hilang |
| GUA_PERTAPA | Gua Pertapa, Gua Urat Roh, Mata Air Roh, Puncak Petir (38GU); situs roh (`SpiritSites`) | 2-5 hari | tier 1-2 | Tutup Diri jauh, `K` 2,0 × faktor situs |
| ALAM_RAHASIA | alam proyeksi `alam/` (`RealmSpecs`) yang dikenal | 2-3 hari | tier alam | fragmen langit hampa, harta, pencerahan |
| BERKELANA | jalan 38 / alam liar, bertemu Pengembara | 1-3 hari | tier 1-2 | tokoh, cerita, pemahaman |
| DESA | desa (`desa/Village`) dalam 2000 blok | 1 hari | tier 1 | Nama Baik desa, calon murid (3 %) lewat `Halls.addCandidates` |
| SEKTE_TAMU | sekte NPC yang sudah `met` (`DiplomasiService`), kompleks sekte 38DS | 1-2 hari | tier 1 (sekutu) / 3 (netral) | tanding persahabatan, `DiplomasiService.adjust` +1 (sekutu) |
| ZIARAH | kuil 38 (`Kuil`), kuil jalan, makam leluhur sendiri (A) | 0,5-1 hari | tier 0 | iblis −5, Kepuasan +, entri puitis |

Tujuan dipilih dari **tempat yang dikenal**: `Towns.known(server)` + situs `DuniaSites.near(level, hallPos, r, jenis)` +
penyedia tujuan dari modul lain (`MandiriApi.addTujuanProvider`, mis. 37 dermaga awan, 38 distrik). Radius menurut ranah: QR
800, F 1500, GC 2500, NS+ 4000 blok; ibu kota tanpa batas bila kedua ujung punya stasiun teleport (38). Bila tidak ada kota
dikenal: sekali per hari dunia per aula `DuniaSites.locate(level, #kultivator:kota, hallPos, 64)`, hasil disimpan di
`kotaDikenal` (tidak pernah per murid, tidak pernah per tick).

#### 39.6.2 Model perjalanan (terlihat dan disimulasikan)

**Rencana** dibuat sekali saat berangkat, disimpan di info `AwayStore` + cermin `MandiriData.jalan.<soul>`: rangkaian tahap
dengan waktu mutlak (game time) dan benih `soul ^ waktuBerangkat`. Hasil tahap ditentukan saat tahap jatuh tempo (malas,
deterministik), sehingga tubuh tampilan bisa muncul di tahap mana pun dengan keadaan yang benar.

Kecepatan (blok per detik waktu dunia, sudah termasuk istirahat): jalan kaki 3,0 (Fana-Fondasi); pedang terbang 10 (Inti
Emas+, bila tidak dilarang); jalan Kekaisaran 38 ×1,15; stasiun teleport 38 (ongkos `ceil(2 + d/250)` NB, maks 120) bila
kedua ujung punya stasiun; Perjalanan Awan 38 (`ceil(6 + d/150)` NB). Moda = termurah yang cukup cepat; MALAS dan SOMBONG
memilih teleport bila mampu; LICIK selalu berjalan/terbang (hemat). Contoh: kota 1500 blok jalan kaki = 500 s = 10.000 tick
≈ 0,42 hari sekali jalan; dengan pedang ≈ 0,13 hari.

**Tahap baku perjalanan BELANJA:**

| # | Tahap | Saat dimuat (terlihat) | Saat tidak dimuat |
|---|---|---|---|
| 1 | Berkemas | `ACT_BERKEMAS` 10 s di depan kamar/Lumbung; mengambil titipan sekte dari Lumbung bila ada; bila guru ≤ 16 blok: membungkuk (`ACT_BOW` 23) + suara `mandiri_pamit` | — |
| 2 | Berangkat | berjalan ke gerbang (pola Kepulangan X terbalik), 24 blok keluar lalu pudar dengan debu; atau `ACT_TERBANG_JAUH` naik spiral 20 blok dan melesat ke arah tujuan (server `setPos` per tick, pola 37 `PatroliPedang`, ≤ 2 bersamaan per aula); lalu `Halls.storeAway(owner, entity, pulangPerkiraan, "mandiri_jalan", info)` | rencana dibuat, murid masuk `AwayStore` |
| 3 | Di jalan (segmen ≤ 500 blok) | bila pemain ≤ 64 blok dari titik perkiraan di jalur: tubuh `JELMAAN` berjalan/terbang sepanjang segmen (`tampilJalan`, maks 2 per pemain) | undi peristiwa jalan per segmen |
| 4 | Tiba di kota | `JELMAAN` muncul di gerbang/arrival situs bila pemain ≤ 128 blok dari pusat kota | — |
| 5 | Kunjungan (3-7 perhentian, 39.6.6) | lihat 39.6.6 | undian sama, hasil sama |
| 6 | Menginap (bila perlu) | masuk penginapan (38 `penginapan_*`), tubuh hilang di pintu; pagi keluar | 4 NB (pos jalan 2 NB) |
| 7 | Pulang (segmen) | seperti 3 | seperti 3 |
| 8 | Tiba di gerbang | `Disciples.arrive` di `arrival` (atau titik kedatangan langit 37), berjalan dengan `ACT_BAWA` (47) ke Lumbung, bongkar `ACT_BERKEMAS`, memberi hadiah, melapor | diterapkan saat aula dimuat (`AwayStore` mencoba lagi sampai bisa) |

**Peristiwa jalan** (per segmen; peluang × Kehati-hatian × kelas jalan 38: Kekaisaran ×0,5, Desa ×1,0, Setapak/liar ×1,5;
malam ×1,5):

| Peristiwa | Peluang | Penyelesaian | Akibat |
|---|---|---|---|
| Kuil jalan | 20 % | selalu | 1 NB dupa, iblis −1, entri singkat |
| Pengembara (tokoh) | 10 % | ngobrol | 30 %: pemahaman +0,5 atau tukar barang kecil; tokoh baru di registri |
| Begal (`kemah_begal` 38 atau acak) | 6 % | `clamp(0.5 + 0.12·(P − band), 0.1, 0.95)` | menang: 2-8 NB, MENOLONG +1, kenangan +3; kabur: dompet −10-30 %; kalah: Terluka, dompet −50 %, 1 tumpukan tas hilang; 2 %: Tertawan |
| Binatang roh | 8 % | kekuatan vs tier | menang: inti binatang ke tas; kalah: Terluka |
| Anggota sekte saingan (`Saingan`) | 4 % | duel data | menang: kenangan +4; kalah: DENDAM/SAINGAN terhadap tokoh itu |
| Kafilah / pedagang keliling | 6 % | — | beli 1 barang daftar −10 %, atau mengawal 0,2 hari dengan upah `5 × (band+1)` NB |
| Pemandangan / ilham | 5 % | — | pemahaman +1, entri harian puitis |
| Hujan / badai | cuaca dunia saat tahap | — | perjalanan +10 %; badai petir: berteduh 1 jam |

#### 39.6.3 Daftar belanja (dari kebutuhan nyata)

Dibangun ulang tiap pagi (≤ 12 baris, `prio` 1-3, `maksHarga`). Harga dasar `Harga.BASE` (NB):

| Kebutuhan | Barang (id `kultivator:`) | Dasar | Prio | Pemakai |
|---|---|---|---|---|
| Terobosan QR → F dalam 3 hari | `foundation_pill` ×1 | 60 | 1 | semua |
| Masuk Inti Emas / NS | `golden_core_pill` ×1 | 400 | 1-2 | semua (bila dompet ≥ harga × 1,2) |
| Tribulasi NS+ | `tribulation_warding_pill` ×1 | 2.000 | 2 | bila mampu; selain itu Hu Fa sahabat |
| Akar Rendah/Sampah (tutup Merah) | `marrow_cleansing_pill` ×1 | 80 | 1 | murid berakar rendah |
| Iblis > 40 atau Ujian Hati berikut | `heart_calming_pill` ×1 | 100 | 1-2 | semua |
| Persediaan luka (< 2 di tas) | `healing_pill` ×3 | 4 | 2 (1 petualang/pemburu) | semua kecuali pertapa |
| Tutup Diri berikut | `qi_gathering_pill` ×2-4 | 4 | 2 | pertapa, semua yang akan mengasingkan diri |
| Meramu | herba roh (ware `herb`, umur menurut band) ×3-6; bahan resep lain `AlchemyRecipes` | menurut umur | 2 | alkemis |
| Menulis jimat | `blank_talisman` ×4-8 + bahan `Inscribe.recipe` | 3 | 2 | formasi |
| Bekal petualang | `thousand_mile_talisman` ×1, `return_talisman` ×1, `shield_talisman` ×2, `storage_pouch` (sekali) | 18 / 40 / 12 / 20 | 2-3 | petualang, pemburu |
| Senjata | naik satu tier `perlengkapan.pedang`: 15-20 / 60-80 / 240-300 / 900-1.100 (38.M) | — | 2 | pedang, pemburu, penjaga |
| Jubah | `perlengkapan.jubah` tier +1 (20 / 60 / 200) | — | 3 | SOMBONG, AMBISIUS, pedagang |
| Gulungan teknik | Paviliun Yue Lu (PK) atau rak gulungan kota bila 38 menjual (C39.K1 jenis `gulungan`) | menurut grade | 2 | cendekia, cita TEKNIK |
| Hadiah | `teh_awan_ungu` (9), Arak Bunga Persik (25, 38), bakpao (2), bunga vanilla; untuk guru `teh_penenang_hati` (20) | — | 3 (1 untuk JODOH) | PENYAYANG, kekasih, sahabat |
| Titipan sekte | `healing_pill` bila Lumbung < 4; herba fondasi untuk `Duties` | — | 2 | SETIA, dibayar `uangTitipan` aula |

`maksHarga` = dasar × (prio 1: 1,25; prio 2: 1,05; prio 3: 0,9) × (MALAS 1,1; LICIK 0,9; pedagang 0,85). Barang dibeli
hanya bila sisa dompet ≥ cadangan `10 × (ranah+1)` NB (kecuali prio 1).

#### 39.6.4 Dompet (uang murid sendiri)

| Sumber | Jumlah | Dari mana (tidak ada uang baru) |
|---|---|---|
| Uang saku harian (06:00) | Tercatat 2, Luar 4, Dalam 8, Inti 16, Tetua 30, Tetua Agung 40 NB (config `uangSaku[]`; per murid 0-200 %) | `Halls.spendPool(owner, nb / 2)` (1 PK = 2 NB). Kas kosong → dibayar sebagian, hanya entri harian (bukan perlakuan buruk) |
| Misi Gilda | menurut pangkat dan band (C39.K4 / 39.6.8) | dunia (upah misi seperti pemain) |
| Menjual karya sendiri | harga beli toko 0,35-0,40 × dasar (atau harga Lapak Sekte 38) | barang **buatan murid** (tanda `kultivator:buatan_murid`) |
| Inti binatang/rampasan dijual | harga beli toko | rampasan perjalanan |
| Peristiwa jalan | 2-8 NB / upah kawal | dunia |
| Pemberian guru | batu roh dari tangan guru (`NilaiBatu.value`) atau NB dari kas | inventaris guru / kas |
| Hadiah turnamen | tetap PK lama | — |

Setoran: SETIA menyetor 30 % pendapatan (bukan uang saku) ke kas (`Halls.addPool(nb / 2)`); pedagang yang mencapai KEKAYAAN
menyetor 50 %; LICIK 0 %. Batas: `dompetMaks = 200 × (ranah + 1)` NB; pendapatan per hari maks `20 × (band + 1)` NB;
kelebihan otomatis ke kas (dicatat). Murid dilepas/dikeluarkan: dompet ke kas, tas ke Lumbung (sekali, saat
`DISCIPLE_LEFT` atau pelepasan). Murid yang membelot (`BERKHIANAT`) membawa dompet dan tasnya (cerita).

#### 39.6.5 Harga, menawar, stok dan godaan

* Harga = penyedia `PasarMurid` (C39.K1). Bawaan tanpa 38: `Harga.base(id) × faktor kota-hari` (undian 0,9-1,3 berbenih
  `(town, hari, id)`, sifat yang sama dengan `Toko`) × `KotaConfig.priceScale`.
* Menawar (`ACT_TAWAR` 6 s): LICIK ×0,85-0,92; pedagang ×0,90; RENDAH_HATI ×0,97; BERANI ×1,0; PENAKUT ×1,03; SOMBONG ×1,05
  (tidak mau menawar); Nama Baik guru di kota itu tinggi (`KotaData.reputation(town, owner)` ≥ ambang pangkat 2 dari 38)
  ×0,95. LICIK 10 % menyinggung pedagang: lapak itu menolak murid hari itu (entri harian lucu).
* Stok: murid (semua aula) boleh membeli maks `stokMuridHari` = 4 unit tiap jenis per kota per hari (`MandiriData.stokKota`).
  Stok pemain (`Toko` 16 jual/hari) **tidak** berkurang oleh murid. 38EK boleh mengganti dengan stok bersama lewat penyedia.
* Godaan Pasar Gelap: bila barang prio 1 habis/terlalu mahal dan murid LICIK/PENDENDAM atau `iblis ≥ 40` → 30 % mampir ke
  kumuh (ibu kota) atau penjual gelap kota. Harga ×1,5 (aturan `PasarGelap`), barang selalu ada; razia 8 % → denda
  `20 × band` NB dari dompet (kurang: Tertawan penjaga, ditebus guru) dan Nama Baik guru −2; iblis +4.

#### 39.6.6 Di dalam kota: naskah kunjungan

Perhentian dipilih dari daftar belanja + watak, diurutkan menurut jarak jalan terpendek. Setiap perhentian: durasi, pose,
baris bicara (lang), aksi data.

| Perhentian | Kapan | Durasi | Yang terlihat (tubuh `JELMAAN`) | Aksi data |
|---|---|---|---|---|
| Gerbang | selalu | 5 s | berhenti, menengadah ke papan nama (pikiran: "Kota <nama>... ramai sekali") | catat kunjungan |
| Lapak pil (`PEDAGANG_PIL`) | ada barang pil/herba | 20-40 s | `ACT_LIHAT_LAPAK` mengangkat botol, `ACT_TAWAR`, pedagang menoleh (`HidupApi.tatap`) dan menjawab (gelembung), koin berkilat, bungkusan masuk tas | beli |
| Pandai pusaka (`PANDAI_PUSAKA`) / toko senjata 38 | senjata/jimat | 20-30 s | mengayun pedang contoh sekali, mengangguk | beli tier |
| Kedai teh (`kota/teh`) | sore, atau SOSIAL tinggi | 60 s | duduk (`ACT_SOCIAL`), cangkir, mendengar kabar | rumor 1 NB; kabar dunia (lewat C39.K8 bila ada) → mungkin target perjalanan baru + entri |
| Rumah makan / bakmi | jam makan | 40 s | `ACT_MAKAN`, mangkuk | 2-25 NB; `lelah −15`; arak: MALAS/SOMBONG 20 % menginap semalam lagi |
| Papan Misi / Gilda | MISI_GILDA | 15 s | membaca papan, mengambil lembar | misi dimulai (39.6.8) |
| Balai Lelang | senja 12000-14000, pedagang/AMBISIUS dengan dompet ≥ 300 | 60 s | duduk di bangku, mengangkat tangan | tawar 1 lot secara data (30 % menang bila tawaran ≥ dasar × 1,2); `BalaiLelang` dan penawar NPC tidak disentuh |
| Kuil | ZIARAH atau iblis > 30 | 30 s | menancapkan dupa, membungkuk | 3 NB, iblis −3 |
| Balai Tabib (38) | Luka Berat | 60 s | duduk, tabib menyentuh bahu | `15 × (band+1)` NB, luka sembuh |
| Lapak Sekte (38) | JUAL | 60 s | menata barang, berseru menawarkan | jual karya (C39.K5) |
| Pasar malam (ibu kota) | malam | 60 s | jajan, memandang lentera | hadiah murah, Kepuasan |
| Pasar Gelap | godaan 39.6.5 | 30 s | menoleh kanan-kiri, tudung | beli gelap / razia |

**Masalah di kota** (undian per kunjungan; data, terlihat bila tubuh ada):

| Peristiwa | Peluang | Terlihat | Akibat |
|---|---|---|---|
| Pencopet | 6 % (kumuh, Kota Air "Tikus Air" ×2) | NPC menabrak murid lalu berlari; murid mengejar 8 s (navigasi biasa), menangkap atau kehilangan jejak | BERANI atau ranah ≥ F: 70 % tangkap → dompet utuh, Nama Baik guru +1, kabar kota (C39.K8); gagal: dompet −10-25 % |
| Provokasi murid sekte rival | 4 % | dua tubuh saling menunjuk, gelembung marah | duel data di Arena (38) atau jalan; kenangan, mungkin DENDAM |
| Penipu | 5 % (murid polos: tidak LICIK, ranah ≤ F) | pedagang keliling mendekat | membeli "pil" palsu (`healing_pill` mutu Rendah), kenangan −2 "pelajaran mahal" |
| Menolong warga | 5 % (PENYAYANG ×2) | menuntun nenek, mendorong gerobak | Nama Baik guru +1, kenangan +2, entri hangat |
| Bertemu calon murid | 3 % | anak menatap pedang murid | `Halls.addCandidates` 1 calon (pola Envoys) |
| Asmara | 3 % (cita JODOH, tanpa kekasih) | mengobrol lama dengan tokoh kota | tokoh "kenalan hati" (39.8.3); kunjungan berikut lebih sering |
| Mabuk | 2 % (MALAS, SOMBONG) | terhuyung, tertidur di bangku | menginap tambahan, dompet −5-15 NB |

**Tubuh `JELMAAN`** (39C): `DiscipleEntity` ber-`Role.JELMAAN` (tidak disimpan; tidak terdaftar di `Disciples`; dicatat di peta
39 `jelmaan → soul`), menyalin kulit, nama, warna jubah sekte (S), tampilan ranah dan perlengkapan dari data murid (salinan
baca saja; data asli tetap di `AwayStore`). Kebal (semua `hurt` ditolak kecuali naskah), tanpa AI tempur. Muncul hanya bila:
tahap = di kota, pemain ≤ 128 blok dari pusat kota, `DuniaBudget.mayAdd(level, "npc")`, ≤ 3 per kota, ≤ 6 per server. Gol:
`JelmaanJalanGoal` (ke perhentian berikut: `TempatApi.titik(level, situs, "lapak" | "meja_makan" | "kursi" | "obrol")` bila 38
ada; selain itu posisi jangkar `Kota` dan NPC berperan yang dicari **sekali per perhentian** dengan
`getEntitiesOfClass(KultivatorNpc, kotak 80)`), `JelmaanPoseGoal` (memutar aktivitas 47-50/18/48 sesuai perhentian). Hilang
saat pemain > 160 blok atau tahap kota selesai (berjalan keluar gerbang lalu `discard`).

**Guru bertemu muridnya di kota** (klik kanan tubuh `JELMAAN` muridnya): baris obrolan "Guru! Murid sedang membeli
<barang>..." + tombol klik `[Lihat belanja]` (daftar, dompet, tas), `[Tambah uang]` (batu roh di tangan guru → dompet,
`NilaiBatu.value`), `[Ikut Guru pulang]` (rekaman `AwayStore` diambil, `Disciples.arrive` di posisi tubuh tampilan, tubuh
tampilan dibuang, murid asli mode Ikuti; sisa tahap kota batal, barang yang sudah dibeli tetap), `[Lanjutkan]`. Pemain lain
hanya melihat label nama + lambang sekte.

#### 39.6.7 Membawa pulang dan memakai barang

Saat tiba: berjalan dengan `ACT_BAWA` (kotak/buntalan menurut jumlah barang) ke Lumbung (jangkar `treasury` atau pos dapur),
`ACT_BERKEMAS` 10 s, lalu:

1. Titipan sekte → `Storehouse.insert(owner, stack)`, sisa → `LumbungOverflow.store(owner, stack)` (aturan S12).
2. Barang pribadi → `tas` (rencana belanja tidak pernah melebihi slot kosong; kelebihan karena rampasan → Lumbung dengan
   catatan pemilik di log kas).
3. Hadiah → berjalan ke penerima (bila dimuat dan bebas), membungkuk, item melayang ke tangan (efek), `HubunganService.adjust`
   +2 (teman) / +4 (sahabat) / +6 (kekasih) beralasan `hadiah`, kenangan kedua pihak; hadiah untuk guru: bila guru ≤ 16
   blok, item ke inventaris guru (dijatuhkan bila penuh) dan `SekteBus.honored(+2)` untuk murid.
4. Melapor: guru ≤ 32 blok → suara `mandiri_pulang` + obrolan ringkas "<Nama> pulang dari <kota>: membeli <n> barang
   (<total> NB), <peristiwa>" dengan klik `[Baca Buku Harian]`; selain itu antrian pemberitahuan + `SekteLog.log` (Laporan
   Pagi Tetua).

**Pemakaian sesudahnya:**

| Barang | Dipakai kapan | Efek nyata |
|---|---|---|
| Pil terobosan, penangkal, penenang | persiapan terobosan / Ujian Hati | `feedSelf` (39.5.6) |
| Pil penyembuh | `luka > 40` | `feedSelf` |
| Pil pengumpul qi | blok pertama Tutup Diri | `feedSelf` |
| Herba, inti binatang | MERAMU (alkemis) | resep `AlchemyRecipes.match`; hasil ke tas/Lumbung (39.6.8) |
| Kertas jimat + bahan | JIMAT | `Talismans.create(kind, power, nama murid)`, peluang `Inscribe.chance(level)` |
| Jimat perisai/seribu li/kembali | dibawa ke perjalanan berikut | perisai: kekalahan jadi Terluka bukan Luka Berat (sekali); seribu li: perjalanan −30 % waktu; kembali: Tertawan → pulang sendiri |
| Senjata (tier) | selalu | `DiscipleHooks.addDamageFactor` jarak dekat ×(1 + 0,04 × tier), maks ×1,2; terlihat di tangan saat Latih Jurus |
| Jubah (tier) | selalu | Kepuasan lewat `honored(1)` per hari selama 5 hari; warna tetap lambang S (tier = pinggiran emas) |
| Gulungan teknik | BELAJAR dari tas | 39.5.5 |
| Bulu ekor roh, berlian | cita PUSAKA | `NatalTreasure.refineSelf/enhanceSelf` (39D): bahan dari tas, PK pribadi |
| Batu roh (rampasan) | masuk dompet | nilai `NilaiBatu.VALUE`; dipakai SERAP_BATU |

#### 39.6.8 Kerajinan murid, Misi Gilda, jual karya

* **Meramu tanpa pemain** (39D, `AlkimiaMurid`): bahan dari tas (atau Lumbung bila jatah mengizinkan) → `AlchemyRecipes.match`
  → peluang `AlchemyFurnace.successChance(levelAlkimiaMurid, apiJiwa, daoApi)`; level alkimia murid = `min(10, jam meramu /
  40)` di `statistik`; mutu memakai ambang `rollQuality` (0,35 / 0,75 / 0,95, +0,03 per level; disalin sebagai konstanta
  karena `private`, diverifikasi saat implementasi). Hasil bertanda `kultivator:buatan_murid = soul`. Terlihat di titik dapur
  dengan prop kuali (tidak butuh kuali blok). Bakat `pill_gift` +25 % naik satu mutu (aturan `Duties.refine`).
* **Jimat** (39D, `JimatMurid`): `Talismans.create`, `Inscribe.chance`, bahan menurut `Inscribe.recipe`.
* **Misi Gilda** (39C): pangkat = lencana murid (E awal; naik tiap 3 sukses; maks menurut jenjang: Luar C, Dalam B, Inti A,
  Tetua S). Sukses gaya `Expeditions` (`clamp(0.45 + 0.12·(P − (tier − 1)), 0.10, 0.95)`). Upah bawaan `6 × (band + 1) ×
  faktor pangkat (E 0,6 … S 2,0)` NB ke dompet (dengan aturan setoran). Bila 38EK menyediakan `GildaMurid` (C39.K4), angka dari
  sana.
* **Jual karya**: hanya barang `buatan_murid` dan rampasan; barang yang dibeli murid bertanda `beli_murid` dan **tidak
  pernah** dijual murid lagi (anti putar-uang).
* **Lapak/Kafilah Sekte 38**: murid pedagang/penjaga mengajukan diri sebagai penjaga lapak/pengawal (C39.K5); guru tetap yang
  memulai lapak dan kafilah (aturan 38).

#### 39.6.9 Perjalanan lain: penyelesaian data

Semua jenis selain BELANJA memakai mesin tahap yang sama: 2-4 tahap, tiap tahap `sukses = clamp(0.45 + 0.12·(P − (tier − 1))
+ 0.05·(ada Inti+) + 0.03·sahabat + jimat, 0.10, 0.95)` dengan `P` kekuatan rombongan (anggota terkuat + 0,25 × sisanya).
Kegagalan tahap tidak mengakhiri perjalanan (pola `JalanCerita` X: cerita bercabang) kecuali tahap terakhir; akibat luka di
39.5.9. XP perjalanan: `K` 0,8 per jam di jalan, 2,0 × faktor situs per jam di Gua Pertapa, 1,2 di alam rahasia; diterapkan
saat tiba dari `pending`.

**Kesempatan** (10 % per perjalanan sukses, 20 % alam rahasia):

| Kesempatan | Efek |
|---|---|
| Buah roh | +8 % bar (tanpa melewati bottleneck) |
| Warisan kecil | gulungan teknik Dao murid (grade ≤ batas ranah) ke tas |
| Mata air roh | +20 % XP mandiri selama 3 hari (`extra.mandiri.berkah`) |
| Pencerahan | pemahaman Dao +3 |
| Ahli tua (tokoh) | penguasaan satu teknik +1 |
| Harta | herba 300 tahun / fragmen langit hampa / bulu ekor roh |
| Racun / jebakan (buruk) | Deviation 3600 tick atau Terluka |

Rampasan: `Expeditions.lootTable(type, tier, random)` (publik) dengan tipe sesuai (berburu → hunt, reruntuhan/alam → secret
realm, desa → herbs), dibatasi `rampasanMinggu` = 12 tumpukan per aula per 7 hari.

#### 39.6.10 Perjalanan berkelompok

Pemimpin (murid yang memilih perjalanan) mengajak 1-3 teman: afinitas ≥ 30 atau ikatan dekat, bebas (`SibukMurid`),
kebijakan mengizinkan, tanpa perjalanan sendiri yang terjadwal. Kekasih/pasangan Dao selalu diajak bila bebas (JODOH).
Rombongan berbagi rencana, tahap, peristiwa dan cerita (satu entri harian per anggota, sudut pandang masing-masing).
Berangkat bersama dari gerbang (berbaris 2 s, `ACT_MUSTER`), pulang bersama. Hasil: `HubunganService.adjust` +3 per anggota
per perjalanan sukses, +6 bila saling menyelamatkan (kegagalan tahap yang tertolong). Maks 25 % (Seimbang) / 40 % (Penuh)
murid aula bepergian bersamaan, maks 2 rombongan berangkat per jam dunia.

#### 39.6.11 Anti-eksploitasi (uang dan barang)

1. Dompet hanya bertambah dari sumber tabel 39.6.4; setiap perubahan lewat satu metode `Dompet.ubah(data, delta, sebab)` yang
   mencatat ke `MandiriData.kas` dan membatasi `[0, dompetMaks]`.
2. Pembelian: dompet dikurangi **sebelum** barang dibuat; gagal mengurangi = tidak ada barang. Barang dibuat malas di tahap
   tiba (bukan di kota), dari id + jumlah dalam rencana.
3. Data perjalanan tersimpan **sekali** di `AwayStore` (tag murid); `MandiriData.jalan` hanya cermin tampilan. Penerapan hasil
   memakai `pending.<soul>` yang dihapus dalam langkah yang sama dengan penerapan (idempoten: kunci `idPerjalanan`).
4. Tubuh `JELMAAN` tidak membawa barang, tidak disimpan, tidak bisa dibunuh untuk rampasan; `[Ikut Guru pulang]` mengambil
   rekaman `AwayStore` sekali (rekaman dihapus sebelum `arrive`).
5. Tablet jiwa: tas dan dompet bagian data murid (satu salinan); kematian tidak menjatuhkan apa pun; salinan tablet ditolak
   aturan lama `Disciples`.
6. Barang `beli_murid` tidak dijual murid; barang `buatan_murid` hanya dibuat dari bahan yang ada.
7. Stok murid per kota/hari (39.6.5), rampasan per aula per minggu (39.6.9), pendapatan per murid per hari (39.6.4).
8. Uang saku dari kas aula yang nyata; kas tidak bisa negatif (`spendPool` boolean).
9. Pemberian guru tidak bisa ditarik kembali sebagai batu (hanya lewat hadiah barang atau setoran ke kas): murid bukan bank.
10. Perintah uji (`uji mandiri dompet`) hanya op dan dicatat dengan sebab `uji`.

#### 39.6.12 Kontrak dengan Kota Besar 38 (dibangun bersamaan)

39 **tidak menyunting berkas 38** dan berjalan penuh tanpa 38 (bawaan memakai `dunia/kota` lama). Kontraknya:

| Kontrak | Arah | Tanda tangan | Bawaan bila pihak lain belum ada |
|---|---|---|---|
| **C39.K1 `PasarMurid`** | 39 mendefinisikan (`sekte/mandiri/api/PasarMurid`), 38EK boleh mendaftar (`MandiriApi.setPasar`) | `List<Tawaran> tawaran(ServerLevel, long town, String jenis, long hari)`; `long harga(long town, String itemId, long hari)`; `boolean pesan(long town, String itemId, int n, long hari)` (stok); `record Tawaran(String itemId, int n, long harga, String jenis)`; jenis `pil`, `herba`, `jimat`, `senjata`, `jubah`, `gulungan`, `makanan`, `teh`, `hadiah` | `Harga.BASE` + `Harga.PILLS` + faktor kota-hari + `stokMuridHari` |
| **C39.K2 Tubuh dan klip kota** | 39 memanggil 38NL/38W | `HidupApi.tatap(npc, jelmaan)`, `HidupApi.klip(npc, klip, prop, tick)` (pedagang menyerahkan barang), `TempatApi.titik(level, situs, jenis)` | jangkar `Kota` + NPC berperan; tanpa klip NPC |
| **C39.K3 Nama Baik guru** | 39 menulis | `KotaData.addReputation(town, ownerUUID, delta)` (API publik lama, kunci UUID); 38KS boleh mendengar `MandiriBus.KOTA_PERISTIWA(owner, soul, town, jenis, delta)` | sudah ada |
| **C39.K4 Misi Gilda murid** | 38EK boleh menyediakan | `MandiriApi.setGilda(GildaMurid)`: `List<MisiMurid> papan(long town, int pangkat)`, `long upah(MisiMurid, boolean sukses)` | rumus 39.6.8 |
| **C39.K5 Relawan lapak/kafilah** | 38EK memanggil 39 | `MandiriApi.relawan(UUID owner, String jenis) → List<UUID>` (murid yang mau, urut minat); `MandiriApi.dagangan(UUID owner, UUID soul) → List<ItemStack>` (karya `buatan_murid` yang boleh dijual) | 38 memakai pilihannya sendiri |
| **C39.K6 Pesanan tempa** | 39 memanggil 38EK bila ada | `PesananTempa` (38EK, `tempa/Forging` diedit 38EK): pesan senjata tier untuk murid dengan NB dompet | senjata tier dibeli langsung di toko |
| **C39.K7 Layanan berbayar** | 39 membaca angka 38.M.2.2 | penginapan 4 (pos 2), hidangan 2/6/12/25, dupa 3/1, teleport `ceil(2 + d/250)`, Perjalanan Awan `ceil(6 + d/150)`, tabib `15 × (band+1)` | konstanta sama di `MandiriConfig` |
| **C39.K8 Kabar** | 39 memanggil 38ST | `CeritaApi.kabar(level, situsId, "mandiri_<jenis>", namaMurid, namaSekte)` (menangkap pencopet, menang di arena, menolong warga) | tanpa kabar (`KabarDunia.add` private, tidak disentuh) |
| **C39.K9 Jalan dan pos** | 39 membaca 38W | `RencanaJalan` (kelas sisi, kemah begal, pos singgah, stasiun) untuk rute dan peluang peristiwa | garis lurus, kelas "liar" |
| **C39.K10 Distrik ibu kota** | 39 membaca 38KS | `TempatApi.tempatDi` / `dekat` untuk distrik (pasar, gilda, gerbang, kumuh) | kota biasa |

Tubuh `JELMAAN` memakai renderer murid lama (bukan `KultivatorNpc`), jadi tidak bergantung pada 38MD. Semua panggilan ke 38
lewat stub `NONE` 38 (langkah 1 38W) atau dibungkus `try`-kelas-ada (`FabricLoader` tidak perlu: satu mod) sehingga urutan
pendaratan bebas.

### 39.7 Kendali guru

#### 39.7.1 Kebijakan (aula dan per murid)

| Pengaturan | Pilihan | Bawaan dunia baru |
|---|---|---|
| Mode | **Mandiri Penuh** / **Seimbang** / **Patuh** | Seimbang |
| Kehati-hatian | Aman / Wajar / Berani / Nekat | Wajar |
| Kegiatan diizinkan | 16 kelompok (39.4.5), centang | semua kecuali alam rahasia (menyala sendiri sejak ranah NS) |
| Tempat terlarang | daftar situs/jenis (dari peta atau daftar kota dikenal; "pasar_gelap" satu centang) | kosong |
| Uang saku | 0-200 % dari tabel jenjang; jatah titipan sekte (NB/minggu) | 100 %; titipan 0 |
| Jatah Lumbung | jenis pil yang boleh diambil sendiri | penyembuh, pengumpul qi, pemulih qi |
| Keputusan besar | tunggu guru: terobosan (hari), Dao (hari), jenjang (Tetua Agung / otomatis / tanya) | 2 / 3 / Tetua Agung |
| Hati-hati terobosan | larang "tergesa" | mati |
| Pemberitahuan | per jenis: terobosan, pulang, luka, tertawan, belanja mahal (≥ 200 NB), teknik baru, jalan berganti, iblis | semua kecuali belanja |

Per murid: tumpang pada bagian mana pun (tombol "Ikut aula" mengembalikan).

#### 39.7.2 Perintah tetap menang

* Setiap perintah eksplisit (mode baru dari tab Order, misi X, ekspedisi, pos tugas, penjaga formasi, kebun/tambang oleh guru)
  menyetel `bebas = now + jedaPerintah` (bawaan 24000 tick = 1 hari dunia; "Selamanya" bila guru memilih) dan membatalkan
  kegiatan mandiri berjalan (aktivitas 40-56 dilepas). Deteksi: kait publik baru `DiscipleEntity.addModeListener` (39A, satu
  baris di `setMode`), dan modul lama yang memanggil `setActivity` dengan id bukan milik 39 otomatis menang karena perencana
  hanya menilai murid ber-aktivitas 0 atau miliknya sendiri.
* Tombol **"Bebaskan ke Jalan Hidup"** (tab dan kartu hub) mengakhiri jeda sekarang; mode diset STAY di pos (perilaku lama
  yang paling tenang).
* Mode FOLLOW/ASSIST: perencana tidur sampai guru melepas (bersama guru = hidup bersama guru).
* Misi guru menimpa perjalanan mandiri yang belum berangkat; perjalanan berjalan bisa dipanggil pulang dari tab
  (`[Panggil pulang]`: tahap sisa dipercepat, pulang ≤ 0,25 hari, barang yang sudah dibeli tetap).

#### 39.7.3 Tab "Jalan Hidup" (layar murid, tab ketiga)

`DiscipleScreen.addExtraTab(glyph JALAN, JalanHidupTab::new)` setelah `MAX_EXTRA_TABS` dinaikkan ke 3 (39E). Data: info
`sekte_mandiri` ringan lewat `DiscipleHooks.addInfo` (jalan, cita, dorongan, kegiatan sekarang, kebijakan efektif, dompet,
tas) + payload `mandiri_harian` atas permintaan (buku harian, jadwal 2 hari).

Tata letak (kertas gulungan seperti tab lain; 3 kolom pada lebar ≥ 380 px, bertumpuk pada layar sempit):

| Bagian | Isi | Aksi |
|---|---|---|
| Kepala | ikon jalan + nama ("Pendekar Pedang"), lencana Iblis bila tergoda, kalimat "kenapa" dari skor ("Bakat Pedang + berani + 3 kenangan turnamen") | `[Dorong ke jalan…]` (+0,8 skor jalan lain), `[Tebus]` bila iblis ≥ 60 |
| Cita-cita | ≤ 3 kartu dengan bilah kemajuan, asal ("sejak kalah final musim 3"), perkiraan hari | `[Dukung]` (prio 1), `[Batalkan]` (kecuali GURU/RANAH) |
| Hari ini | pita 24 jam berwarna per kegiatan (kemarin di bawahnya, redup), penanda jam sekarang; rencana berikut ("Besok pagi: ke Kota Hujan Perak membeli Pil Fondasi") | `[Larang kegiatan ini]` pada pita |
| Dorongan | 7 bilah kecil (Lapar Kultivasi, Bosan, Lelah, Sosial, Ambisi, Barang, Luka) | — |
| Buku Harian | 5 entri terakhir (gulir 40), ikon suasana, cerita pendek (39.8.1) | `[Tegur]` / `[Puji]` (1×/hari: iblis −6 / `honored(2)`) |
| Dompet & tas | NB, uang saku, 9 slot | `[Beri uang]` (batu di tangan guru), `[Ambil barang]` (ke guru; kenangan −1 kecuali hadiah), `[Ubah uang saku]` |
| Hubungan & rencana | 3 ikatan terkuat, rombongan perjalanan berikut, tokoh luar yang dikenal | — |
| Kebijakan | mode, kehati-hatian, izin, larangan (tumpang per murid) | `[Ikut aula]`, `[Bebaskan]` |

#### 39.7.4 Kartu hub "Jalan Hidup Murid" (seksi MURID)

`HubCards.register(HubNav.MURID, "sekte_mandiri_jalan", glyph, lock = punya aula, MandiriBoardScreen::open)`. Layar papan:
semua murid (termasuk yang tidak dimuat dan yang bepergian) dengan kolom Jalan, Kegiatan/Lokasi ("Tutup Diri hari 2/3", "Di
Kota Besar <nama>, pulang ±0,3 hari"), Ranah, Cita-cita prio 1, Dompet; filter (bepergian, terluka, siap menembus, tergoda);
umpan kabar 30 peristiwa aula; editor kebijakan aula; kas aula dan pengeluaran uang saku 7 hari. Klik baris → layar murid
pada tab Jalan Hidup (bila dimuat) atau kartu ringkas (bila tidak).

#### 39.7.5 Pemberitahuan

Peristiwa besar → obrolan berwarna dengan klik `[Lihat]` + toast kecil kanan atas (klien 39E, ≤ 1 per 5 s, digabung bila
banyak: "3 murid pulang dari perjalanan"). Bila `Sibuk.why(guru) != null` (tribulasi, duel, ujian) pemberitahuan ditahan dan
dicoba lagi tiap 200 tick (aturan Sibuk). Guru offline: masuk `MandiriData.kabar` dan dikirim sebagai ringkasan saat masuk
("Selama Guru pergi: Lin Yue naik ke Inti Emas, Bai Qing pulang dari Kota Awan…"). Laporan Pagi Tetua (E) menerima baris yang
sama lewat `SekteLog.log(server, owner, "mandiri", line)`.

### 39.8 Cerita: Buku Harian, kisah perjalanan, tokoh, suara

#### 39.8.1 Buku Harian (disusun dari templat + peristiwa nyata)

Server hanya menyimpan **kunci + argumen** (`EntriHarian(hari, jam, jenis, kunciInti, arg[], suasana, penting)`); klien 39E
menyusun teks saat layar dibuka, sehingga bahasa mengikuti klien dan simpanan tetap kecil. Satu entri = 3-4 kalimat:

```
[pembuka]  kultivator.sekte.mandiri.harian.buka.<konteks>.<n>     konteks: cuaca (cerah/hujan/salju/badai), bulan
                                                                 (purnama/baru), jam (fajar/siang/senja/malam), tempat
[inti]     kultivator.sekte.mandiri.harian.<jenis>.<hasil>.<n>   jenis: belanja, tawar, copet, makan, teh, latih, tanding,
                                                                 tutup, tembus, hati, dao, belajar, ajar, meramu, jimat,
                                                                 kebun, tambang, jalan.<peristiwa>, pulang, hadiah, iblis…
[refleksi] kultivator.sekte.mandiri.harian.renung.<sifat>.<n>    sifat terkuat yang relevan (12 sifat × 4 varian)
[penutup]  kultivator.sekte.mandiri.harian.tutup.<cita>.<n>      cita-cita prio 1 (14 jenis × 3 varian), 50 % peluang
```

Varian dipilih dengan benih `(soul, hari, jenis)` sehingga entri yang sama selalu terbaca sama. Argumen nyata: nama kota,
nama tokoh, barang (`ItemStack` nama terjemahan), harga awal/akhir, nama sahabat, ranah, teknik, angka hari. Kosakata
minimum: 8 pembuka per konteks, 4-6 inti per jenis/hasil, 4 refleksi per sifat, 3 penutup per cita (≈ 450 kalimat id_id +
en_us). Suasana (ikon): senang, tenang, lelah, kesal, sedih, bangga, tergoda.

Contoh yang harus bisa dihasilkan (uji `uji perguruan mandiri harian <murid> uji`):

> **Hari ke-41, senja.** Hujan tipis membasahi jalan batu Kota Hujan Perak. Pedagang pil bermata sipit meminta 72 batu untuk
> sebutir Pil Fondasi; aku menawar sampai 61 dan ia menggerutu sambil membungkusnya. Kata Guru, kesabaran juga bagian dari
> kultivasi. Pemurnian Qi Puncak... sebentar lagi.

> **Hari ke-58, malam purnama.** Bulan penuh, qi turun seperti embun. Kakak Senior Lin Yue menembus Inti Emas di halaman,
> dan aku berdiri menjaganya sampai petir terakhir. Aku iri, sedikit. Tapi lebih banyak bangga.

> **Hari ke-77, fajar.** Di jalan pulang dari reruntuhan, tiga begal menghadang kami. Bai Qing menangkis dua, aku menjatuhkan
> yang ketiga. Kami tertawa sepanjang sisa jalan. Mungkin ini yang disebut saudara seperguruan.

Entri penting (terobosan, Dao, tertawan, ditebus, cita-cita selesai, kematian sahabat) diberi pita merah dan tidak pernah
didorong keluar oleh entri biasa (cincin 40 memprioritaskan entri biasa untuk dibuang).

#### 39.8.2 Kisah perjalanan

Perjalanan = rangkaian tahap; setiap tahap menghasilkan **satu baris kisah** (`kultivator.sekte.mandiri.kisah.<jenis>.<tahap>.
<hasil>.<n>`) dengan argumen nyata, dan seluruh kisah dikirim sebagai "Gulungan Perjalanan" di laporan kepulangan (klik dari
obrolan, atau tab Buku Harian). Bercabang seperti `JalanCerita` X: kegagalan tahap mengubah baris berikut ("Setelah begal itu
merampas separuh batu kami, kami terpaksa tidur di kuil jalan...").

Contoh BERBURU tier 3 (2 murid):
1. "Kami berangkat sebelum fajar ke Hutan Pinus Kelabu, 1.200 blok ke barat." (berangkat)
2. "Jejak serigala angin roh kami temukan di tepi sungai. Bai Qing memasang jimat perisai." (tahap 1 sukses)
3. "Serigala itu lebih cepat dari dugaan; lenganku tergores." (tahap 2 gagal → Terluka ringan)
4. "Akhirnya ia tumbang. Intinya sebesar kepalan, berwarna hijau pucat." (tahap 3 sukses → inti binatang)
5. "Kami pulang menjelang senja. Guru, inti ini untuk Paviliun Obat." (pulang + hadiah ke Lumbung)

#### 39.8.3 Tokoh bernama, saingan, asmara

Registri `MandiriData.tokoh` (≤ 32 per aula; yang paling lama tidak dijumpai dilupakan): `Tokoh(id, nama, peran, situs,
watak, sikap per jiwa −100..100, jumpa, hari)`. Nama dari pembangkit nama yang ada (`DiscipleNames` untuk kultivator,
`KotaNames` untuk warga). Peran dan busur cerita:

| Peran | Muncul dari | Busur |
|---|---|---|
| PEDAGANG langganan | 3× belanja di lapak yang sama | diskon 5 %, menyimpan barang langka 1×/minggu, sapaan khusus |
| PENGEMBARA | peristiwa jalan / kedai teh | 1-3 jumpa: pemahaman, kabar tempat baru (menambah `kotaDikenal`/situs) |
| SAINGAN | kalah dari anggota sekte rival / `Saingan` | 3-5 jumpa → duel penentu (di Arena 38 bila ada, atau di jalan) → menang: cita DENDAM selesai, saingan jadi hormat (sikap +40); kalah: iblis +5, latihan diutamakan |
| KENALAN_HATI | asmara di kota | 3 jumpa → "janji di festival" (acara 38) → bila muda dan berbakat: datang sebagai calon (`Halls.addCandidates`), lalu ikatan R KEKASIH bila diterima; bila tidak: perpisahan pahit-manis (kenangan +6 abadi) |
| AHLI_TUA | kesempatan "Ahli tua" | sekali: penguasaan +1; kedua kali (langka): petunjuk Dao (pemahaman +5) |
| PENCURI | pencopet yang lolos | jumpa ulang 20 %: tertangkap → Nama Baik guru +2, kenangan "akhirnya!" |
| KEPALA_DESA | perjalanan DESA | permintaan berulang; desa terikrar (Desa) memberi sambutan |

Asmara antar-murid tetap milik R (afinitas, Upacara); 39 hanya menambah kesempatan (teh malam, perjalanan berdua, hadiah).

#### 39.8.4 Suara (baris baru yang diusulkan; reuse sampai klip dibuat)

`<v>` = setiap suara murid m1, m2, m3, f1, f2, f3. Sebelum klip ada, kode memakai kunci lama (`greet`, `thanks`, `cheer`,
`tired`, `cultivate`, `protect`, `injured`, `farewell`, 36: `depart`, `mission_report`, `tea`, `study`) lewat
`SekteSuara.line(disciple, baru, cadangan, jeda)`.

| Kunci | Pembicara | Teks (id) | Jepang | Kapan |
|---|---|---|---|---|
| `disciple.<v>.pamit` | murid | Guru, murid pamit ke kota sebentar. | 師匠、少し町へ行ってまいります。 | berangkat belanja, guru dekat |
| `disciple.<v>.pulang_belanja` | murid | Guru, murid pulang. Ini oleh-olehnya. | 師匠、ただいま戻りました。お土産です。 | tiba, membawa hadiah guru |
| `disciple.<v>.tawar` | murid | Terlalu mahal! Kurangi sedikit. | 高すぎる！少しまけてくれ。 | `ACT_TAWAR` (tubuh kota) |
| `disciple.<v>.tawar_sepakat` | murid | Baiklah, sepakat. | よし、それで手を打とう。 | tawar berhasil |
| `disciple.<v>.lihat_lapak` | murid | Hmm... pil ini lumayan. | ふむ…この丹薬、なかなか良い。 | `ACT_LIHAT_LAPAK` |
| `disciple.<v>.copet` | murid | Berhenti, pencuri! | 待て、泥棒！ | mengejar pencopet |
| `disciple.<v>.makan` | murid | Wah, enak sekali! | わあ、美味しい！ | makan di kota/dapur |
| `disciple.<v>.latih` | murid | Satu jurus lagi! | もう一太刀！ | Latih Jurus |
| `disciple.<v>.salam_pagi` | murid | Selamat pagi, Kakak Senior! | おはようございます、先輩！ | salam pagi 05:00 |
| `disciple.<v>.tutup_masuk` | murid | Murid akan menutup diri. Mohon jangan diganggu. | これより閉関いたします。お邪魔なさらぬよう。 | mulai Tutup Diri |
| `disciple.<v>.tutup_keluar` | murid | Pengasingan selesai... qi-ku lebih jernih. | 閉関、終わりました…気が澄んでいる。 | akhir Tutup Diri |
| `disciple.<v>.hufa_minta` | murid | Senior, maukah menjagaku saat menembus? | 先輩、突破の護法をお願いできますか。 | mengundang Hu Fa sahabat |
| `disciple.<v>.hufa_jaga` | murid | Tenang, aku menjagamu. | 安心して、私が護ります。 | penjaga mulai berjaga |
| `disciple.<v>.tembus_sendiri` | murid | Hari ini aku menembus dengan kekuatanku sendiri! | 今日、己の力で突破する！ | mulai terobosan sendiri |
| `disciple.<v>.hati_lulus` | murid | Iblis hati... tidak bisa menggoyahkanku. | 心魔など…私は揺るがない。 | Ujian Hati lulus |
| `disciple.<v>.dao_pilih` | murid | Jalanku adalah Dao ini. | 我が道は、この道なり。 | memilih Dao sendiri |
| `disciple.<v>.belajar_sendiri` | murid | Aku memahaminya sendiri! | 自分で悟った！ | Pencerahan |
| `disciple.<v>.serap_batu` | murid | Batu roh ini... qi-nya murni. | この霊石…霊気が澄んでいる。 | Serap Batu |
| `disciple.<v>.hadiah` | murid | Ini untukmu. Jangan ditolak, ya. | これ、あなたに。受け取ってね。 | memberi hadiah |
| `disciple.<v>.tertawan` | murid | Guru... tolong aku... | 師匠…助けて… | pesan Tertawan (diputar saat guru membuka penanda) |
| `disciple.<v>.diselamatkan` | murid | Guru datang! Aku tahu Guru pasti datang. | 師匠が来てくださった！信じていました。 | diselamatkan |
| `disciple.<v>.iblis_bisik` | murid | Kekuatan... aku butuh lebih banyak kekuatan... | 力が…もっと力が欲しい… | Tergoda, gelembung pikiran (volume rendah) |
| `disciple.<v>.ditebus` | murid | Terima kasih, Guru... aku kembali. | ありがとうございます、師匠…私は戻りました。 | ritual Tebus selesai |
| `disciple.<v>.jalan_ganti` | murid | Aku sudah menemukan jalanku. | 自分の道を見つけた。 | jalan hidup berganti |

Total 24 kunci × 6 suara = 144 klip, dibuat di langkah akhir dengan `tools/gen_voices.py` (VOICEVOX) bersama baris 36/38 yang
belum dibuat; teks `lang-src/voice.*` dihasilkan alat yang sama.

### 39.9 Kinerja (100+ murid, laptop lemah)

| Sumber biaya | Batas | Cara |
|---|---|---|
| Penilaian perencana | ≤ 4 per tick server, 1 per murid per jam dunia | antrean berbenih `soulHash`, array tetap, tanpa alokasi |
| Cache titik aula | 1 bangun ulang / 1200 tick / aula | dari jangkar dan komponen, bukan pemindaian blok |
| Murid bernavigasi | ≤ `gerakMaks` 10 per aula; `DuniaBudget.throttled(entity, 4)` | murid lain memilih kegiatan di tempat |
| Kejar-ketinggalan | O(1) XP + ≤ 10 peristiwa per murid; ≤ 4 murid per tick | rumus tertutup (39.4.7) |
| Perjalanan | data murni; tahap dievaluasi saat jatuh tempo (1 cek per perjalanan per 100 tick) | ≤ 40 % murid pergi; ≤ 2 keberangkatan per jam dunia per aula |
| Tubuh `JELMAAN` | ≤ 3 per kota, ≤ 6 per server; AI tiap 4 tick; muncul hanya ≤ 128 blok dari pemain | `DuniaBudget.mayAdd(level, "npc")` |
| Terbang pedang berangkat | ≤ 2 per aula bersamaan, 6 s | jalur dihitung sekali |
| Terobosan | ≤ 1 bersamaan per aula; antrean 1 per 2 menit | langit tidak penuh |
| Gelembung pikiran | ≤ 6 dalam 32 blok, tiap murid ≤ 1 per 60 s | klien menolak sisanya |
| Partikel kegiatan | Minimal: tanpa jejak/asap; Normal: ≤ 4 quad per murid; Maksimal: ≤ 12; LOD 24 blok | `EffectQuality.current()` |
| Pose | dipadukan klien 6 tick, hanya diam; tidak di luar 48 blok (pose dasar) | `DutyPoses` lama |
| Info layar | `addInfo` ringan (≤ 600 byte); buku harian/jadwal hanya atas permintaan | payload `mandiri_harian` |
| Simpanan | ≤ 2 KB per murid tanpa tas; `MandiriData` ≤ 40 entri × murid + 32 tokoh + 30 kabar | cincin dan LRU |
| `uji perguruan mandiri anggaran` | mencetak pengukur di atas | uji performa |

Target: 120 murid di satu aula yang dimuat menambah < 0,4 ms per tick server rata-rata (diukur dengan `anggaran` di laptop
pengguna; tanpa profiler tambahan).

### 39.10 Integrasi

#### 39.10.1 `util/SibukMurid` (baru, 39A)

```
public final class SibukMurid {
    public static void register(String id, Predicate<DiscipleEntity> sibuk);   // nama kunci teks: kultivator.sibuk_murid.<id>
    public static void addProvider(Function<DiscipleEntity, @Nullable String> p);
    public static @Nullable String why(DiscipleEntity e);
    public static @Nullable String whyExcept(DiscipleEntity e, String self);
    public static boolean busy(DiscipleEntity e);
}
```

Penyedia bawaan (didaftarkan malas, seperti `Sibuk`): `terobosan` (`data.breakingThrough()`), `deviasi` (untuk kegiatan
berat), `duel` (`DuelRules.inDuel`), `tempur` (`getTarget() != null`), `perintah` (`bebas > now` atau mode FOLLOW/ASSIST/
GUARD_HALL), `aktivitas` (aktivitas ≠ 0 dan bukan milik 39), `membangkang` (`SekteApi.hati().defiant`), `turnamen`
(`turnamen().busy`), `kebun` (`kebun().working`), `misi` (`misi().away`), `paviliun` (`paviliun().studying`), `kantor` (`ACT_OFFICE`),
`luka_berat`, `tutup_diri`, `perjalanan`. 37 dan 38 boleh mendaftar (mis. 37 `ACT_TERBANG_PEDANG`, 38 penjara). Modul 36 tetap
memakai uji bebasnya sendiri (tidak disunting); `SibukMurid` hanya dipakai 39 dan modul baru.

Untuk pemain: semua pemberitahuan 39 ke guru memanggil `Sibuk.why(player)` dan menunda 200 tick bila sibuk (aturan M2).

#### 39.10.2 Sekte yang Hidup (36)

| Layanan/peristiwa | 39 memakai | 39 menembak |
|---|---|---|
| `HatiService` | sifat, Kepuasan, Kesetiaan, keadaan, `workFactor` (dikalikan pada XP kerja) | — |
| `SekteBus.DISCIPLE_HONORED` | — | kegiatan yang disukai selesai (+1, maks 3/hari), cita-cita tercapai (+3..+5), hadiah guru dibalas |
| `SekteBus.DISCIPLE_MISTREATED` | — | `DIABAIKAN` 3 bila Tertawan tidak diselamatkan; **tidak pernah** `DIPAKSA_KERJA` (perencana tidak memaksa) |
| `HubunganService` | afinitas, ikatan (teman perjalanan, Hu Fa, hadiah, JODOH) | `adjust` (+2..+8) beralasan `mandiri_*` |
| `Ingatan` | drift (iblis, Ujian Hati, skor jalan) | kenangan `mandiri.<apa>` (jalan_ganti, tembus_sendiri, kota, hadiah, tertawan, ditebus, cita, saingan) |
| `PaviliunService` / A | `studying`, `blessing` | lewat jembatan `PaviliunMandiri` |
| `TetuaService` / E | `holder(TETUA_AGUNG)` menyetujui jenjang; `officeFactor` | `SekteLog.log(..., "mandiri", ...)` → Laporan Pagi |
| `MisiService` / X | `away` (bebas?) | — (misi guru selalu menang) |
| `KebunService` / W | `working`, petak bebas | lewat jembatan `KebunMandiri`; W tetap menembak `WORK_DONE` |
| `TurnamenService` / T | `running`, `champion` (cita JUARA) | — |
| `DiplomasiService` / D | `met`, `stance` (SEKTE_TAMU) | `adjust` +1 sekutu |
| `SekteBus.DISCIPLE_LEFT` | dompet/tas ke kas/Lumbung | — |
| `ANCESTOR_RITE`, `DISCIPLE_BURIED` | iblis −5, cita DENDAM/RANAH | — |

Bus baru `MandiriBus` (39A, Fabric `Event`, pola `SekteBus`): `JALAN_GANTI(owner, soul, dari, ke)`, `CITA_SELESAI(owner, soul,
jenis)`, `JALAN_BERANGKAT(owner, soul, jenis, tujuan, rombongan)`, `JALAN_TIBA(owner, soul, HasilJalan)`, `BELANJA(owner, soul,
town, itemId, n, harga)`, `TEROBOSAN_SENDIRI(owner, soul, rankBaru, berhasil)`, `IBLIS(owner, soul, sebelum, sesudah)`,
`TERTAWAN(owner, soul, situs)`, `KOTA_PERISTIWA(owner, soul, town, jenis, deltaNamaBaik)`.

#### 39.10.3 Sekte Langit (37), Kota Besar (38), dunia

* 37: titik kedatangan langit (`Halls.addArrivalSpotProvider` yang sudah dipakai 37) dipakai untuk tiba; keberangkatan
  pedang dari `panggung_pedang` bila aula langit; feri awan opsional mendengar `MandiriBus.JALAN_BERANGKAT` (37G, tanpa
  menyunting 39); `LangitJatuh` sudah melindungi murid; `ACT_MEMANDANG`/`ACT_JAGA_TEPI` milik 37 dihormati (`SibukMurid`).
* 38: kontrak C39.K1-K10 (39.6.12). 38 boleh mendaftar `MandiriApi.addTujuanProvider` (distrik, gua 38GU, kompleks 38DS).
* Dunia: `Towns`, `DuniaSites`, `Saingan`, `Expeditions.lootTable`, `Village`, `RealmSpecs` hanya dibaca.
* `Disciples.addFollowVeto` tidak terlibat (murid mandiri tidak mengikuti pemain antar dimensi). Perjalanan mandiri hanya di
  dimensi aula (Overworld atau dimensi aula langit 37 yang sama).

#### 39.10.4 Hub, Kitab Panduan, config, perintah

**Hub**: kartu `sekte_mandiri_jalan` (MURID, 39.7.4); tab ketiga layar murid (39.7.3). Ensiklopedia: topik `mandiri` (39E,
lewat registri topik yang ada; teks di fragmen 39E) dengan tautan "?" dari tab dan papan.

**Kitab Panduan** bab 20 "Murid Mandiri" (indeks 19; `CHAPTER_COUNT` 20; ditambah **di akhir** setelah bab 18-19 milik 38W,
id `c20.*`; gerbang `{minRealm QI_REFINING, pastRealm null, requires "hall_level:1"}`, `displayOrder` setelah bab sekte 15/16):

| Langkah | Cek | Teks singkat |
|---|---|---|
| `c20.jalan` | `adv:sekte/mandiri_jalan` (murid mendapat jalan hidup; otomatis saat rekrut pertama) | "Setiap murid punya jalan sendiri. Buka tab Jalan Hidup." |
| `c20.kebijakan` | `adv:sekte/mandiri_kebijakan` (guru mengubah kebijakan sekali) | "Atur Mandiri Penuh / Seimbang / Patuh dari papan Jalan Hidup Murid." |
| `c20.belanja` | `adv:sekte/mandiri_belanja` (murid pulang dari kota) | "Murid turun ke kota membeli perbekalan dengan uang sakunya." |
| `c20.harian` | `adv:sekte/mandiri_harian` (guru membaca Gulungan Perjalanan/Buku Harian) | "Baca kisah mereka." |
| `c20.tembus` | `adv:sekte/mandiri_tembus` (terobosan sendiri berhasil) | "Murid bisa menembus tanpa Guru, dijaga sahabatnya." |
| `c20.cita` (opsional) | `adv:sekte/mandiri_cita` | "Cita-cita murid berubah bersama hidupnya." |
| `c20.abadi` (opsional) | `adv:sekte/mandiri_abadi` (murid mencapai Abadi Sejati tanpa ajaran guru sejak Inti Emas) | "Murid yang tumbuh sendiri sampai ke langit." |

**Config** (`config/kultivator-sekte.json`, seksi `mandiri`, `SekteConfig.section("mandiri", MandiriConfig.class, …, clamp)`;
saklar `umum.mandiri`):

| Kunci | Bawaan | Batas | Arti |
|---|---|---|---|
| `aktif` | true | — | modul hidup (registry selalu, aturan K1) |
| `modeBawaan` | SEIMBANG | enum | kebijakan aula awal |
| `skalaXp` | 1.0 | 0-3 | XP kegiatan mandiri (**1,5 perlu persetujuan**) |
| `skalaLuarLayar` | 0.75 | 0-1 | kejar-ketinggalan |
| `kejarMaksHari` | 30 | 1-120 | batas hari kejar-ketinggalan |
| `penilaianPerTick` | 4 | 1-16 | anggaran perencana |
| `gerakMaks` | 10 | 2-40 | murid bernavigasi per aula |
| `jedaPerintah` | 24000 | 1200-∞ | jeda setelah perintah guru |
| `tungguGuruHari` / `tungguDaoHari` | 2 / 3 | 0-30 | menunggu guru (Seimbang) |
| `uangSaku` | [2, 4, 8, 16, 30, 40] | 0-500 | NB/hari per jenjang |
| `dompetMaksPerRanah` | 200 | 0-5000 | dompet maks × (ranah+1) |
| `hasilMaksHariPerBand` | 20 | 0-500 | pendapatan maks × (band+1) |
| `stokMuridHari` | 4 | 0-64 | per jenis per kota per hari |
| `rampasanMinggu` | 12 | 0-200 | tumpukan per aula per 7 hari |
| `pergiMaksPersen` | [25, 40] | 0-100 | Seimbang, Penuh |
| `bolehGugur` | false | — | hanya NEKAT (**perlu persetujuan bila diubah**) |
| `jalanIblis` | true | — | lapisan godaan |
| `tampilJalan` | true | — | tubuh di jalan dekat pemain |
| `jelmaanPerKota` / `jelmaanServer` | 3 / 6 | 0-12 / 0-32 | tubuh kota |
| `bobot` | tabel 39.4.3 | 0-5 | bobot dorongan dan jalan |
| `harga.*` | 38.M.2.2 | — | layanan bawaan C39.K7 |

**Perintah**. Pemain: `/kultivasi perguruan mandiri` (papan), `… mandiri bebaskan <murid|semua>`, `… mandiri panggil
<murid>`, `… mandiri jelmaan <aksi>` (klik obrolan tubuh kota). Uji (op, `SekteCommands.test("mandiri", …)`, chip grup
`perguruan mandiri`):

| Perintah | Fungsi |
|---|---|
| `uji perguruan mandiri info <murid>` | jalan, cita, dorongan, kegiatan, kebijakan efektif, dompet, tas, tunggu |
| `… skor <murid>` | tiga kegiatan teratas dengan setiap faktor (39.4.3) |
| `… jalan <murid> <id>` / `… cita <murid> <jenis> <target>` | paksa jalan / cita-cita |
| `… dorong <murid> <dorongan> <0-100>` | set dorongan |
| `… lompat <hari>` | jalankan kejar-ketinggalan untuk semua murid pemain seolah `hari` berlalu (XP, peristiwa, uang saku) |
| `… proyeksi <murid>` | tabel hari per ranah sampai Abadi Sejati (39.5.1) dengan faktor nyata |
| `… kirim <murid> <jenis> [tujuan|terdekat]` | mulai perjalanan sekarang |
| `… percepat <murid> [tahap]` | majukan tahap perjalanan (uji tubuh kota dan kepulangan) |
| `… jelmaan <murid>` | munculkan tubuh kota di kota terdekat yang dimuat (uji animasi) |
| `… dompet <murid> <±n>` / `… tas <murid> <item> <n>` | uang/barang uji (sebab `uji`) |
| `… tembus <murid>` | jalankan alur terobosan sendiri sekarang (persiapan dipersingkat) |
| `… hati <murid> lulus|gagal` | paksa hasil Ujian Hati berikut |
| `… iblis <murid> <0-100>` | set godaan |
| `… harian <murid> uji` | susun 5 entri contoh dari semua jenis |
| `… tokoh daftar|buat <peran>` | registri tokoh |
| `… kebijakan <mode>` | kebijakan aula |
| `… anggaran` | pengukur 39.9 |
| `… reset <murid>` | hapus `extra.mandiri` (uji ulang) |

### 39.11 Sumber

Hanya rujukan yang sudah dipakai dan diperiksa di kode/dokumen lama, plus satu rujukan desain game yang umum dikenal. Angka
39 adalah desain game.

* **Murid sekte mencari sumber daya sendiri, membeli di pasar dan menutup diri**: *A Record of a Mortal's Journey* (Han Li
  berdagang di pertemuan pasar kultivator dan berulang kali menutup diri di gua kediamannya); Yue Lu Hall memasok kitab, resep
  dan bahan untuk poin (ch. 153, sudah dikutip di `sect/hall/Contribution.java`) → slip teknik dibeli dengan PK pribadi.
* **Poin kontribusi dan balai misi**: *Martial Peak* (Contribution Hall) dan *A Will Eternal* ch. 9 dan 27 (Missions Office
  menilai tanaman roh dan membayar poin), keduanya dikutip di `Contribution` → Misi Gilda murid, uang dari kerja.
* **Hu Fa (护法) saat menembus**: *Battle Through the Heavens* (Yao Lao menjaga Xiao Yan), dikutip di `disciple/path/HuFa`
  → Hu Fa sahabat sebagai versi lebih lemah.
* **Belajar sendiri di perpustakaan sekte**: biksu penyapu Perpustakaan Sutra Shaolin (*Demi-Gods and Semi-Devils*, Jin Yong),
  dikutip di `sekte/paviliun/RakKitab` → Pencerahan dan membaca gulungan dari tas.
* **Iblis hati di gerbang besar**: Ujian Tangga 36 G dan `trial/HeartDemons` (pemain) memakai konsep 心魔 yang sama → Ujian Hati
  murid (data, lebih ringan).
* **Murid melampaui guru**: *Battle Through the Heavens* dan *Martial Peak*, dikutip di `disciple/path/Surpass`.
* **AI berbasis kebutuhan (motives) dan skor utilitas**: *The Sims* (Maxis, 2000: kebutuhan seperti lapar, energi,
  kesenangan, sosial memilih tindakan) → dorongan dan skor kegiatan 39.4. Ini rujukan desain, bukan cerita.

### 39.12 Modul dan kontrak (tepat lima, berkas tidak bertumpuk)

Catatan: tambahan 39.F (Perseteruan antar-sekte) dikerjakan modul keenam kecil **39F** (39.F.9) yang tidak menyunting berkas
lama; tabel pemilik 39.12.1 tetap berlaku tanpa perubahan pemilik.

Pola 36.4: paket server `dev.kultivator.sekte.mandiri.<sub>` + `<Kelas>.init()` dipanggil dari `Mandiri.init()` (satu baris per
modul, diganti oleh modulnya sendiri), paket klien `dev.kultivator.client.sekte.mandiri.<sub>`, aset `assets/kultivator/
textures/sekte/mandiri/<sub>/`, potongan `assets/kultivator/items/fx/sekte_mandiri_<sub>_*.json`, advancement `data/kultivator/
advancement/sekte/mandiri_*.json` (kriteria `minecraft:impossible`), lang `lang-src/sekte_mandiri_<sub>.en_us.json` +
`.id_id.json` (awalan `kultivator.sekte.mandiri.<sub>.`), payload sendiri (`PayloadTypeRegistry` dari init modul, tidak pernah
`network/Payloads`/`Net`). Modul hanya mengimpor `sekte.mandiri.api.*`, `sekte.*`, `sekte.api.*` dan kode lama, **tidak** paket
modul 39 lain (kecuali lewat `MandiriApi`).

#### Modul 39A — Inti & Perencana (langkah 1 dulu, lalu paralel)

* **Berkas baru**: `sekte/mandiri/{Mandiri, MandiriConfig, MandiriData, MandiriCommands, MandiriKegiatan (registri pelaksana),
  Perencana, Skor, Dorongan, Irama, KebijakanAula, KejarKetinggalan, TempatCache, AnggaranGerak, PilihJalan, CitaCita,
  JalanIblis, PelaksanaDasar (MEDITASI 56, ISTIRAHAT, MAKAN, JAGA_GERBANG, BERMAIN)}.java`; `sekte/mandiri/api/{MandiriApi,
  MandiriBus, JalanHidup, JenisCita, Dorong, Kegiatan, Kebijakan, ModeMandiri, Kehatian, JenisJalan, EntriHarian, Tokoh,
  HasilJalan, PasarMurid, GildaMurid, TujuanProvider, Pelaksana}.java` (stub lengkap di langkah 1); `util/SibukMurid.java`;
  `lang-src/sekte_mandiri_inti.*`; advancement `mandiri_jalan`, `mandiri_kebijakan`.
* **Berkas lama (pemilik 39A)**:
  * `sekte/SekteContent.java`: `Umum.mandiri` + `case "mandiri"` di `on()` + satu baris `Mandiri.init()`.
  * `sekte/SekteIds.java`: id modul `mandiri` di `MODULES`, kunci extra `MANDIRI = "mandiri"`, konstanta `ACT_MANDIRI_FIRST = 40`,
    `ACT_MANDIRI_LAST = 56` (nama per id ada di `MandiriKegiatan`).
  * `disciple/DiscipleEntity.java`: `Role.JELMAAN` di akhir enum; `holdsActivitySpot` + `computeMeditating` untuk 40-56
    (39.4.2); aktivitas 40-56 tidak dipulihkan dari simpanan; kait publik `addModeListener(Consumer<DiscipleEntity>)` dipanggil
    di `setMode`; kait publik `addGoalInstaller(BiConsumer<DiscipleEntity, GoalSelector>)` dipanggil di akhir `registerGoals`;
    `JELMAAN`: `hurt` ditolak kecuali sumber naskah, tidak masuk `Disciples.loaded` (verifikasi filter peran).
  * `command/CheatHelp.java`: grup chip `perguruan mandiri`.
* **Kontrak**: C39.A1 `MandiriApi` (statis, default aman): `jalan(data)`, `kegiatan(entity)`, `sedangPergi(owner, soul)`,
  `catat(owner, soul, EntriHarian)`, `dompet(data)`, `ubahDompet(data, delta, sebab)`, `tas(data)`, `masukTas/ambilTas`,
  `kebijakan(owner, soul)`, `onPerintah(entity)`, `addTujuanProvider`, `setPasar/pasar()`, `setGilda/gilda()`, `relawan`,
  `dagangan`, `register(Kegiatan, Pelaksana)`; C39.A2 `MandiriBus` (39.10.2); C39.A3 `SibukMurid` (39.10.1); C39.A4 skema
  `extra.mandiri` dan `MandiriData` (39.2) — hanya 39A yang mengubah skema, modul lain menulis lewat `MandiriApi`; C39.A5
  `Pelaksana { boolean bisa(DiscipleEntity, Konteks); void mulai(...); boolean jalan(...); void selesai(...); double K(); }`.

#### Modul 39B — Tumbuh Sendiri

* **Berkas baru**: `sekte/mandiri/tumbuh/{Tumbuh, LajuMandiri (kait XP 39.5.1), Proyeksi, TerobosanSendiri, Persiapan,
  UjianHati, HuFaSahabat, DaoSendiri, BelajarSendiri, Pencerahan, AjarSenior (MENGAJAR), PilSendiri, SerapBatu, TutupDiri,
  Merenung, JenjangSendiri, DewaSendiri}.java`; jembatan `sect/hall/KontribusiMurid.java` (membeli `Contribution.Ware` dengan PK
  pribadi murid, aturan harga lama), `sekte/paviliun/PaviliunMandiri.java` (`mintaBaca`, `bacaTas` memakai `RakKitab.blocker`
  dan `BelajarMandiri.learn`); `lang-src/sekte_mandiri_tumbuh.*`; advancement `mandiri_tembus`, `mandiri_abadi`.
* **Berkas lama (pemilik 39B)**:
  * `disciple/DiscipleCultivation.java`: `meditationBaseTanpaMode`, `addXpKegiatan`, `breakthroughBlockerSelf`,
    `startBreakthroughSelf`, `feedSelf`, `chooseDaoSelf`, `chooseDewaSelf` (aditif; metode lama tidak berubah).
  * `disciple/DiscipleBreakthrough.java`: `start(entity, level, @Nullable ServerPlayer master)` (semua `Msg.chat(master, …)`
    dijaga null; listener tetap).
  * `sect/hall/SectRanks.java`: `elderTeaches` tanpa guru online bila `MandiriApi.kebijakan` bukan Patuh; `requirementsSelf`;
    `promoteSelf` (guru nullable).
* **Kontrak**: C39.B1 kait XP (`addXpFactor` MEDITATION untuk ACT 45/56); C39.B2 `addStrikeFactor`/`addFoundationChance`/
  `addQualityBonus` Hu Fa sahabat; C39.B3 pelaksana TUTUP_DIRI, MERENUNG, SERAP_BATU, JAGA_TEROBOSAN, BELAJAR, MENGAJAR,
  TEROBOSAN; C39.B4 `Tumbuh.butuh(data) → List<Kebutuhan>` (pil/ batu yang dibutuhkan untuk terobosan, dibaca daftar belanja 39C).

#### Modul 39C — Perjalanan & Kota

* **Berkas baru**: `sekte/mandiri/jalan/{Perjalanan, Rencana, Tahap, PeristiwaJalan, Tujuan, DaftarBelanja, Dompet, Tas,
  PasarBawaan (C39.K1 bawaan), Tawar, KunjunganKota, MasalahKota, Jelmaan, JelmaanJalanGoal, JelmaanPoseGoal, Kepulangan,
  Hadiah, Rampasan, Kesempatan, GildaBawaan (C39.K4 bawaan), Rombongan, Tawanan, Perlengkapan (kait damage senjata), KontrakKota
  (pembungkus 38: TempatApi, HidupApi, CeritaApi, EkonomiApi, KotaBesarApi), JalanCommands}.java`; `lang-src/sekte_mandiri_jalan.*`
  (baris bicara kota, kisah per tahap); advancement `mandiri_belanja`.
* **Berkas lama**: tidak ada. Memakai API publik: `Halls.storeAway/addReturnHandler/addArrivalSpotProvider/spendPool/addPool/
  addCandidates`, `Disciples.arrive/markFallen`, `Storehouse.insert/take`, `LumbungOverflow.store`, `Towns`, `DuniaSites`,
  `Harga`, `NilaiBatu.VALUE`, `KotaData.reputation/addReputation`, `Expeditions.lootTable`, `SpiritStones`.
  Penanda Tertawan: `DuniaSites.publishLive(...)` dengan jenis `mandiri/tawanan` + koordinat di obrolan; ikon peta khusus
  diminta ke pemilik `DuniaMarkers` (38W) lewat laporan, tidak disunting.
* **Kontrak**: C39.K1-K10 (39.6.12); C39.C1 pelaksana BERKEMAS, BERANGKAT, semua PERJALANAN; C39.C2 tubuh `JELMAAN`
  (`Jelmaan.muncul/hilang`, aksi klik guru lewat perintah `perguruan mandiri jelmaan`).

#### Modul 39D — Kerja, Kerajinan & Pergaulan

* **Berkas baru**: `sekte/mandiri/kerja/{Kerja, LatihJurus, Tanding, AlkimiaMurid, JimatMurid, KerjaSekte (KEBUN, TAMBANG,
  POS_TUGAS, JAGA_FORMASI), TehMandiri, NatalSendiri}.java`; jembatan `sekte/kebun/KebunMandiri.java` (memanggil
  `Pertumbuhan.assign/release`, `GiliranTambang.assign/release` package-private), `sekte/hubungan/HubunganMandiri.java`
  (`GiliranSosial.forceTea`); `lang-src/sekte_mandiri_kerja.*`.
* **Berkas lama (pemilik 39D)**:
  * `sect/event/Duties.java`: `assignSelf(UUID owner, DiscipleEntity, String duty)` dan `releaseSelf` (tanpa `ServerPlayer`;
    pesan ke Buku Harian).
  * `formasi/keeper/Keepers.java`: `assignSelf(UUID owner, ServerLevel, int coreId, DiscipleEntity, KeeperOrder)` (aturan
    lama, tanpa pesan guru).
  * `disciple/power/NatalTreasure.java`: `refineSelf/enhanceSelf(entity, Bahan dariTas, int pkPribadi)` (rumus peluang dan
    biaya lama; bahan dari tas, PK dari `SectRanks.pk`).
* **Kontrak**: C39.D1 pelaksana LATIH_JURUS, TANDING, MERAMU, JIMAT, KEBUN, TAMBANG, POS_TUGAS, JAGA_FORMASI, SOSIAL/TEH;
  C39.D2 tanda item `kultivator:buatan_murid` (komponen data kustom kecil, didaftarkan 39D) dan
  `kultivator:beli_murid` (didaftarkan 39D, ditulis 39C).

#### Modul 39E — Cerita, Layar & Suara

* **Berkas baru (server)**: `sekte/mandiri/cerita/{Cerita, BukuHarian, PilihVarian, Tokoh, BusurSaingan, BusurAsmara,
  Pemberitahuan (Sibuk + ringkasan offline + toast), InfoMandiri (`addInfo` `sekte_mandiri`), MandiriPayloads (`mandiri_harian`,
  `mandiri_papan`, `mandiri_aksi`, `mandiri_pikir`, `mandiri_toast`), Panduan (memberi advancement `mandiri_harian`,
  `mandiri_cita`)}.java`.
* **Berkas baru (klien)**: `client/sekte/mandiri/{MandiriClient, JalanHidupTab, MandiriBoardScreen, PitaJadwal, HarianTeks
  (penyusun kalimat), GelembungMandiri, ToastMandiri, MandiriPoses (pose + prop 40-56)}.java`; aset `textures/sekte/mandiri/**`
  (ikon jalan 10, ikon kegiatan 24, ikon suasana 7, kertas gulungan), potongan prop `items/fx/sekte_mandiri_*.json` (pedang
  latih, kuali kecil, kipas, meja jimat, kuas, buntalan, kotak punggung, mangkuk, kantong batu, kertas segel, batu roh 4 warna,
  pedang terbang); `lang-src/sekte_mandiri_cerita.*` (≈ 450 kalimat harian, kisah umum, UI, Ensiklopedia `mandiri`, panduan
  bab 20).
* **Berkas lama (pemilik 39E)**:
  * `client/disciple/DiscipleScreen.java`: `MAX_EXTRA_TABS = 3`.
  * `guide/GuideChapters.java`: bab 20 di akhir (`CHAPTER_COUNT` 20, langkah `c20.*`, gerbang, `displayOrder`) — **setelah**
    38W menambah bab 18-19.
  * `client/sekte/SekteContentClient.java`: satu baris `MandiriClient.init()`.
  * `tools/gen_voices.py`, `voice/VoiceEvents.java` (+ `sounds.json`, `.ogg`, `lang-src/voice.*` yang dihasilkan): 24 kunci
    `disciple.<v>.*` 39.8.4 — **setelah** 38ST.
  * `client/screen/ui/Glyphs.java` hanya bila glyph baru dibutuhkan (utamakan glyph yang ada).
* **Kontrak**: C39.E1 `Cerita.catat(owner, soul, jenis, hasil, args…)` dipanggil modul lain lewat `MandiriApi.catat`; C39.E2
  `Pemberitahuan.kirim(owner, jenis, Component, penting)`; C39.E3 `MandiriPoses` mendaftarkan semua pose 40-56 (satu-satunya
  pendaftar id itu); C39.E4 payload layar.

#### 39.12.1 Pemilik berkas lama (satu pemilik di 39; semua suntingan aditif)

| Berkas lama | Pemilik | Suntingan | Syarat waktu |
|---|---|---|---|
| `sekte/SekteContent.java` | 39A | saklar + satu baris init | — |
| `sekte/SekteIds.java` | 39A | id modul, kunci extra, rentang ACT 40-56 | — |
| `disciple/DiscipleEntity.java` | 39A | `Role.JELMAAN`, ACT 40-56, `addModeListener`, `addGoalInstaller` | setelah 37 bila 37 menyunting berkas ini (37 hanya memanggil `setActivity`) |
| `command/CheatHelp.java` | 39A | grup chip | setelah 37G/38W (keduanya menambah grup) |
| `disciple/DiscipleCultivation.java` | 39B | metode `*Self`, `addXpKegiatan`, `meditationBaseTanpaMode` | — |
| `disciple/DiscipleBreakthrough.java` | 39B | `start` guru nullable | — |
| `sect/hall/SectRanks.java` | 39B | `elderTeaches` tanpa guru, `requirementsSelf`, `promoteSelf` | — |
| `sect/event/Duties.java` | 39D | `assignSelf`, `releaseSelf` | — |
| `formasi/keeper/Keepers.java` | 39D | `assignSelf` | — |
| `disciple/power/NatalTreasure.java` | 39D | `refineSelf`, `enhanceSelf` | — |
| `client/disciple/DiscipleScreen.java` | 39E | `MAX_EXTRA_TABS = 3` | — |
| `guide/GuideChapters.java` | 39E | bab 20 | setelah 38W |
| `client/sekte/SekteContentClient.java` | 39E | satu baris | — |
| `tools/gen_voices.py`, `voice/VoiceEvents.java` | 39E | 24 kunci | setelah 38ST |
| `client/screen/ui/Glyphs.java` | 39E (hanya bila perlu) | glyph `JALAN` | — |

**Tidak disunting oleh 39**: semua berkas `dunia/kota/**`, `dunia/tempat/**` (38), `sekte/langit/**` (37), modul 36 selain
jembatan baru di paketnya (`KebunMandiri`, `HubunganMandiri`, `PaviliunMandiri` adalah berkas **baru**), `network/Payloads`,
`Net`, `KultivatorConfig`, `CultivateCommand`, `HubCardsPage`, `sect/hall/Halls.java`, `sect/hall/Contribution.java`
(`KontribusiMurid` berkas baru), `alchemy/**` (38EK menyunting `AlchemyFurnace`), `tempa/Forging.java` (38EK), `docs/DESIGN.md`.

#### 39.12.2 Urutan kerja

1. **Langkah 1 (39A, ±1 sesi, berurutan)**: semua tipe `api/*` dan `MandiriApi`/`MandiriBus` sebagai stub bertanda tangan
   lengkap; `SekteIds`/`SekteContent`/`DiscipleEntity`/`CheatHelp`; `MandiriKegiatan` dengan enum lengkap dan pelaksana kosong;
   `SibukMurid`; skema data; nama payload dipesan (badan oleh 39E).
2. **Langkah 2 (paralel)**: 39A menyelesaikan perencana; 39B, 39C, 39D, 39E masing-masing hanya pada berkasnya. Setiap modul
   menjaga `docs/progres/mandiri-<modul>.md` (checklist + langkah berikutnya, aturan progres pengguna) dan menulis daftar
   "butuh dari modul lain" di sana.
3. **Langkah 3 (finish)**: gabung lang (`mergeLang`), `gen_voices.py` (VOICEVOX), generator prop/ikon (`tools/gen_mandiri_*.py`
   milik 39E), lalu satu langkah Gradle (`compileJava`, `compileClientJava`, `jar`, `--no-daemon`). Tanpa tes, `runClient`
   atau `gradlew build`.

### 39.13 Uji

#### 39.13.1 Uji terima (bukti konten nyata, harus terlihat oleh penguji)

1. Aula 30 murid, kebijakan Seimbang, guru diam 2 hari dunia di aula: jadwal tiap murid berbeda (pita 24 jam di tab), ≥ 6
   jenis kegiatan terlihat bersamaan (meditasi, latih jurus berpose, kebun, teh berpasangan, meramu dengan asap, istirahat di
   bawah pohon); tidak ada murid yang berdiri diam > 2 jam dunia tanpa alasan di `uji … skor`.
2. Salam pagi 05:00: murid bebas berbaris di halaman lalu bubar.
3. Murid alkemis berakar Mid kehabisan herba → daftar belanja → pagi berikutnya berkemas, membungkuk pada guru, berjalan
   keluar gerbang (atau terbang pedang bila Inti+) → guru menyusul ke kota: tubuh murid terlihat melihat lapak, menawar
   dengan pedagang yang menoleh, makan bakmi, minum teh → `[Lihat belanja]` menunjukkan barang dan dompet nyata → murid pulang
   membawa kotak di punggung, menyimpan titipan ke Lumbung, memberi teh ke sahabatnya, melapor; Buku Harian memuat entri
   dengan nama kota, harga awal/akhir, dan nama pedagang.
4. Murid yang sama memakai herba itu: MERAMU dengan kuali dan asap, pil bertanda buatan murid muncul di tasnya.
5. Murid Pemurnian Qi Puncak, guru di dimensi lain: murid membeli Pil Fondasi, beristirahat sampai penuh, mengundang dua
   sahabat (mereka berdiri berjaga), menelan pil, menembus; guru menerima ringkasan saat kembali.
6. Murid Inti Emas tanpa Dao, Mandiri Penuh: memilih Dao sendiri di malam hari dengan pilar cahaya; alasannya tampil di tab.
7. Murid akar Merah di Inti Emas Puncak: membeli Pil Pembersih Sumsum (kota atau PK), menelannya, tutup Merah lepas, menembus.
8. `uji … lompat 30` dengan guru jauh: XP bertambah sesuai `proyeksi`, ≤ 10 entri harian bertanggal masa lalu, uang saku
   dipotong dari kas, tidak ada terobosan luar-layar; saat kembali terobosan yang tertunda berjalan satu per satu.
9. Murid tergoda iblis (`uji … iblis 70`): mampir Pasar Gelap, bisikan di gelembung, guru `[Tebus]` → ritual → kembali.
10. Begal mengalahkan murid (`uji … percepat` dengan `hati`/peluang dipaksa): pulang Terluka; Tertawan → situs penyelamatan
    nyata, guru datang, murid berterima kasih (suara/teks).
11. Perintah guru (Ikuti) menghentikan perencana seketika; `[Bebaskan]` mengembalikannya; Patuh = perilaku lama persis.
12. 120 murid: `uji … anggaran` < 0,4 ms/tick rata-rata; ≤ 10 bernavigasi per aula; tidak ada lonjakan saat jam berganti.
13. Tidak ada barang/uang ganda: putuskan koneksi saat tubuh kota hidup, muat ulang, cek dompet/tas/Lumbung/kas log cocok.
14. Proyeksi: murid Seimbang Mid Dalam `proyeksi` ≈ 324 hari (skalaXp 1,0), ≈ 216 (1,5) — sesuai tabel 39.5.1.

#### 39.13.2 Daftar uji (Indonesia, untuk `docs/uji/FASE-39.md`)

**Persiapan**
- [ ] Dunia baru, aula tingkat ≥ 4 dengan Paviliun, kebun, tambang, arena; 10+ murid beragam watak dan akar.
- [ ] `/kultivasi uji perguruan mandiri kebijakan SEIMBANG`; buka kartu hub "Jalan Hidup Murid".

**Jalan hidup dan perencana**
- [ ] Setiap murid punya jalan hidup berbeda yang masuk akal (cek alasan di tab).
- [ ] `uji perguruan mandiri skor <murid>` menampilkan tiga kegiatan dengan faktor.
- [ ] Pita jadwal hari ini terisi; kemarin tampil redup.
- [ ] Salam pagi 05:00 terjadi; makan siang 12:00 di pos dapur.
- [ ] Murid MALAS lebih sering istirahat; RAJIN lebih sering latihan/kerja.
- [ ] Hujan: latihan luar berkurang, teh bertambah. Purnama: meditasi malam bertambah.
- [ ] Ganti jalan (`uji … jalan <murid> alkemis`) lalu tunggu 10 hari (`lompat 10`): jalan bisa kembali bila skor lain unggul.

**Kendali guru**
- [ ] Beri perintah Ikuti: murid berhenti dari kegiatan mandiri seketika.
- [ ] `[Bebaskan]`: murid kembali ke jadwalnya.
- [ ] Kebijakan Patuh: tidak ada kegiatan 40-56, tidak ada perjalanan mandiri.
- [ ] Larang "belanja" untuk satu murid: ia tidak pernah ke kota.
- [ ] Tempat terlarang "pasar_gelap": murid tergoda tidak mampir.
- [ ] Ubah uang saku 0 %: dompet tidak bertambah; kas aula tidak berkurang.

**Belanja ke kota**
- [ ] `uji … kirim <murid> belanja terdekat`: berkemas, membungkuk, berjalan/terbang keluar.
- [ ] Susul ke kota: tubuh murid terlihat (nama + jubah sekte) melihat lapak, menawar, membayar.
- [ ] Pedagang menoleh dan menjawab (bila 38 ada).
- [ ] Klik kanan tubuh murid: `[Lihat belanja]`, `[Tambah uang]`, `[Ikut Guru pulang]` bekerja.
- [ ] Murid makan dan minum teh di kota; entri harian menyebut kota dan harga.
- [ ] Pulang: kotak di punggung, titipan ke Lumbung, hadiah ke sahabat (afinitas naik), laporan ke guru.
- [ ] Barang dipakai: herba diramu, pil terobosan ditelan sebelum menembus.
- [ ] Murid LICIK menawar lebih murah; SOMBONG membayar lebih mahal.
- [ ] Stok murid: murid ke-5 di hari sama tidak mendapat jenis pil yang sama di kota itu.
- [ ] Pencopet: murid berani mengejar; berhasil/gagal tercatat; Nama Baik guru berubah.

**Perjalanan lain**
- [ ] Berburu: inti binatang pulang ke tas; kisah 4-5 baris.
- [ ] Reruntuhan/alam rahasia sesuai Kehati-hatian (Aman tidak memilih tier tinggi).
- [ ] Perjalanan berdua sahabat: kisah dari dua sudut pandang; afinitas naik.
- [ ] Tertawan: situs penyelamatan muncul; selamatkan → suara terima kasih; abaikan 3 hari → murid lolos dengan Luka Berat.
- [ ] `[Panggil pulang]` mempercepat kepulangan.

**Tumbuh sampai Abadi Sejati**
- [ ] Terobosan sendiri dengan dua Hu Fa sahabat; sambaran terasa lebih ringan (angka di aksi bar bila guru dekat).
- [ ] Ujian Hati di masuk Jiwa Baru Lahir: lulus/gagal (paksa dengan `uji … hati`).
- [ ] Dao dipilih sendiri setelah menunggu guru 3 hari (Seimbang) / langsung (Penuh).
- [ ] Teknik dari rak, gulungan PK, tetua tanpa guru online, senior mengajar, pencerahan (`lompat 30`).
- [ ] Naik jenjang disetujui Tetua Agung; Latihan Khusus Luar→Dalam tetap berjalan.
- [ ] Akar Merah lepas setelah Pil Pembersih Sumsum dibeli sendiri.
- [ ] Tutup Diri 2 hari: segel, tidak diganggu, keluar dengan cincin qi.
- [ ] Serap Batu memotong dompet sesuai rumus; maks 2 blok/hari.
- [ ] `uji … proyeksi` sesuai tabel; satu murid uji (`lompat` berulang) mencapai Abadi Sejati tanpa ajaran guru → langkah
      panduan `c20.abadi`.

**Iblis dan risiko**
- [ ] `iblis 70`: bisikan, Pasar Gelap, entri kelam; `[Tebus]` mengembalikan jalan asal.
- [ ] `bolehGugur false`: tidak ada murid gugur karena 39 di semua tingkat Kehati-hatian.

**Cerita dan suara**
- [ ] `uji … harian <murid> uji`: 5 entri berbeda, kalimat id_id dan en_us lengkap (tanpa kunci mentah).
- [ ] Tokoh pedagang langganan muncul setelah 3 kunjungan; saingan muncul dan busurnya selesai.
- [ ] Baris suara baru terdengar (atau cadangan lama bila klip belum ada).
- [ ] Pemberitahuan ditunda saat guru tribulasi; ringkasan "Selama Guru pergi" saat masuk.

**Kinerja dan keamanan data**
- [ ] 120 murid: `anggaran` < 0,4 ms/tick; FPS klien stabil di Minimal.
- [ ] Putus koneksi saat murid di kota dan saat tiba: tidak ada barang/uang ganda; kas log cocok.
- [ ] Simpan-muat dunia di tengah Tutup Diri dan di tengah perjalanan: keadaan berlanjut benar.

**Panduan dan hub**
- [ ] Bab 20 "Murid Mandiri" muncul setelah bab 19; langkah tercentang dari advancement.
- [ ] Tab Jalan Hidup adalah tab ketiga (setelah Hati dan Hubungan); kartu hub di seksi Murid.

### 39.F Perseteruan antar-sekte (tambahan)

Permintaan pengguna (diterjemahkan): "Murid juga bisa bertengkar atau bermusuhan dengan murid sekte lain karena suatu
peristiwa, sehingga muncul kejadian yang seru." Bagian ini dibangun di atas DESIGN 36 D (8 sekte NPC, `standing`, `Sikap`,
pakta, perang, Ketua Sekte), 36 T (turnamen antar-sekte), 36 R (ikatan, Duel Kehormatan), `Ingatan`, DESIGN 35 (Duel Pedang:
`DuelChallenges.startDirect`, ring, intro, berhenti di 10 %), 38 (kota, ibu kota, gilda, jalan) dan 39 (hidup mandiri,
perjalanan, tokoh bernama). Dikerjakan modul keenam yang kecil **39F** (39.F.9); lima modul 39A-39E tetap seperti 39.12, dengan
tiga titik sambung kecil yang dicatat.

#### 39.F.0 Fakta kode yang dipakai (dicek 2026-10-23)

| Fakta | Berkas | Akibat untuk 39.F |
|---|---|---|
| `jumlahSekte = 8` (4-12), aliran LURUS/NETRAL/IBLIS, tier 1-9, gerbang dari worldgen | `sekte/diplomasi/DiplomasiConfig`, `SekteNpc` | daftar murid sekte lain per sekte |
| `standing` −100..100; `Sikap` PERANG < −70, BERMUSUHAN < −30, DINGIN < −10, NETRAL < 20, RAMAH; awal Lurus +10, Netral 0, Iblis −40; perubahan lama: hadiah +5..+15, utusan +5, tolak tantangan −5, turnamen ±10, misi ±5, serbuan −20 | `api/Sikap`, `diplomasi/Standing` | insiden memakai `DiplomasiService.adjust(owner, sect, delta, "mandiri.seteru.<sebab>")` (publik), dengan delta di skala yang sama |
| Perang dinyatakan oleh D (`Perang.nyatakan`, package-private); peristiwa D dipilih `Simulasi` dan diterapkan `Simulasi.apply(server, data, owner, sect, r, Peristiwa, random, today)` (package-private) | `diplomasi/Perang`, `Simulasi:463` | jembatan baru `sekte/diplomasi/DiplomasiMandiri.java` memanggil jalur D sendiri (TANTANG, PERANG, DAMAI), bukan menyalin |
| Turnamen antar-sekte dimulai oleh `TANTANGAN` dari D; hasil ±10 standing; `TurnamenHasil(… guestSect, guestWon)` | `turnamen/AntarSekte`, `api/TurnamenHasil` | kekalahan turnamen menjadi pemicu; tantangan resmi lewat jembatan D |
| Tubuh sekte NPC = `Disciples.spawnUnowned(level, pos, data, name, skin, slim, Role.RIVAL, anchor)`: tidak disimpan, tidak bisa disentuh kecuali pasangan duel | `diplomasi/Tubuh`, `Disciples:881` | murid sekte lain yang ditemui memakai tubuh RIVAL yang sama |
| `DuelChallenges.startDirect(@Nullable ServerPlayer challenger, a, b, pair, rules)`; `DuelIds.PAIR_NPC 2`, `PAIR_TONTON 3`; hasil dibaca dari `DuelSessions.byEntity` | `duel/session/DuelChallenges:445` | duel kehormatan tanpa guru (challenger null) atau disaksikan guru |
| Duel Kehormatan internal sekte (R) mengubah dendam jadi persaingan | `hubungan/DuelKehormatan` | pola yang sama untuk lintas sekte |
| `Sibuk.enemyTaken(player, kind)` (maks 3 peristiwa musuh/hari) | `util/Sibuk` | sergapan/tawuran di dekat guru dihitung sebagai peristiwa musuh |

#### 39.F.1 Murid sekte lain sebagai tokoh tetap

Registri dunia `SeteruData` (SavedData `kultivator:mandiri_seteru`, tidak per aula, karena murid sekte lain sama bagi semua
pemain) menyimpan **daftar murid** setiap sekte NPC:

| Medan | Isi |
|---|---|
| `id` | `<sectId>#<n>` tetap |
| `nama` | dari pembangkit nama murid (`DiscipleNames`, benih `(dunia, sekte, n)`); julukan setelah peristiwa ("Si Lidah Ular", "Pedang Patah") |
| `peran` | Murid Luar / Dalam / Inti / Kakak Tertua (1 per sekte) / Tetua Muda (sekte tier ≥ 6) |
| `ranah` | awal `≈ tier sekte − 1 ± 1` (ordinal), tahap; tumbuh +1 tahap per 8 hari dunia sampai `tier + 1` (dihitung malas dari hari) |
| `sifat` | 2 `Sifat` (bobot aliran: Iblis → LICIK, PENDENDAM, SOMBONG; Lurus → SETIA, BERANI, RENDAH_HATI) |
| `dao`, `senjata`, `kulit`, `suara` | dari benih; Dao sesuai warna lambang sekte bila cocok |
| `sikap.<soul>` | −100..100 terhadap tiap murid pemain yang pernah ditemui |
| `dendam` | ≤ 3 jiwa (murid pemain) yang dibenci; `kasih` ≤ 1 jiwa |
| `luka` | sampai hari (setelah kalah berat: tidak muncul) |
| `jumpa` | jumlah dan hari terakhir; lokasi terakhir (kota/situs) |
| `status` | AKTIF, MEMBELOT (bergabung ke sekte pemain sebagai calon), PERGI (sekte runtuh atau jadi vasal tanpa dia) |

Ukuran: `6 + tier` anggota per sekte (maks 10) → ≤ 80 catatan dunia, ≤ 300 byte masing-masing. Anggota baru menggantikan yang
PERGI saat sekte naik tier (`TUMBUH`).

**Bertemu**: hanya di tempat yang dibuat 39 dan 38: kedai teh, pasar, gilda, arena ibu kota, jalan, situs perburuan,
reruntuhan, desa, gerbang sekte mereka (perjalanan SEKTE_TAMU), dan aula pemain (delegasi turnamen, utusan D). Saat
pertemuan terjadi dan pemain ≤ 128 blok (atau tubuh `JELMAAN` murid kita sedang tampil), tubuh RIVAL dimunculkan dari catatan
(`DiscipleData.fresh` + `setRank` + teknik Dao sesuai ranah dari `TechniqueRegistry`, jubah dan panji lambang sekte seperti
`Tubuh`); selain itu semua diselesaikan sebagai data dengan hasil yang sama (benih tahap). Tubuh hilang saat pemain > 160 blok.
Mereka **tidak pernah mati** (aturan 36.0.3): duel berhenti di 10 %, tawuran memakai pasangan duel (39.F.3).

#### 39.F.2 Pemicu insiden

Diperiksa hanya saat sebuah **tahap perjalanan** 39C jatuh tempo (kontrak C39.C3 `MandiriApi.addPertemuanProvider`) atau saat
peristiwa bus terjadi; tanpa tick per tokoh. `hadir(sekte, tempat)` = 0,25 bila gerbang sekte ≤ 3000 blok dari tempat (×2 bila
≤ 1500; ×2 bila Sikap BERMUSUHAN/PERANG; ×0,5 bila sekutu); maks satu pertemuan sekte lain per perjalanan.

| Pemicu | Di mana / kapan | Peluang insiden saat bertemu | Pengali | Panas awal | Pihak salah |
|---|---|---|---|---|---|
| Hinaan di kedai teh | perhentian kedai teh (39.6.6) | 30 % | SOMBONG salah satu ×1,6; Sikap DINGIN ×1,5, BERMUSUHAN ×2; sekte Iblis ×1,5; murid kita RENDAH_HATI ×0,5 | 15 | mereka (70 %) |
| Rebutan kelahiran harta / herba | BERBURU, RERUNTUHAN, ALAM_RAHASIA; kelahiran harta aktif (`DuniaApi.treasures()`) | 50 % | AMBISIUS ×1,5; tier tempat tinggi ×1,3 | 25 | siapa yang kalah undian |
| Misi gilda yang sama | MISI_GILDA (12 % dua sekte mengambil misi yang sama) | 60 % | LICIK mereka ×1,5 (sabotase) | 20 | undian |
| Kekalahan turnamen | `TOURNAMENT_ENDED` antar-sekte / Turnamen Agung 38 | 40 % (yang kalah SOMBONG/PENDENDAM; 15 % lainnya) | kalah telak (selisih ranah ≥ 1) ×1,5 | 20 | — (dendam satu arah) |
| Rival asmara | murid kita ber-cita JODOH dengan kenalan hati di kota (39.8.3) | 20 % per kunjungan bersama | — | 20 | — |
| Gulungan dicuri | perjalanan dengan gulungan di tas (anggota LICIK sekte lain) 3 %; atau murid kita LICIK/`iblis ≥ 60` mencuri 2 % | 100 % (insiden pasti) | — | 35 | pencuri |
| Perundung di pasar | pasar/pasar malam; anggota SOMBONG/Iblis memalak pedagang atau junior kita | 60 % murid kita turun tangan (PENYAYANG/BERANI 90 %, PENAKUT 20 %) | — | 15 | mereka |
| Melindungi warga desa | DESA; sekte Iblis/Netral memeras penduduk (10 %) | 70 % | PENYAYANG ×1,3 | 25 | mereka; Nama Baik desa +, `Desa` reputasi lewat API publik bila ada |
| Peristiwa D | `RIVAL_SECT_ACTION` `SERGAP_MISI`, `MEREKRUT`, `SERANGAN` | otomatis: panas +10 pada seteru yang sudah ada dengan sekte itu, atau seteru baru 30 % | — | 10 | mereka |

Selain itu, pertemuan yang **tidak** menjadi insiden bisa menjadi persahabatan: 20 % bila Sikap ≥ NETRAL dan salah satu
PENYAYANG/RENDAH_HATI, atau bila mereka berjuang bersama melawan begal/binatang di tahap yang sama (`sikap.<soul>` +15..+30).

#### 39.F.3 Tangga eskalasi (bercabang)

Setiap seteru adalah catatan `Seteru(id, owner, jiwaKita[] ≤ 3, sektenya, tokohMereka[] ≤ 3, sebab, salah, panas 0-100, tingkat,
hariMulai, riwayat ≤ 8, status AKTIF | DAMAI | SAINGAN | DENDAM | TERLARANG)` di `SeteruData.aula.<owner>`. Panas naik oleh
insiden dan langkah, turun 2/hari (`panasTurun`), ×0,5 laju turun bila ada ikatan DENDAM. Setiap kali panas melewati ambang,
langkah berikut **diundi dari cabang** (pengali watak kedua pihak, Sikap sekte, tindakan guru 39.F.4, kehadiran tetua):

| Tingkat | Ambang panas | Kejadian | Cabang dan peluang dasar |
|---|---|---|---|
| T0 Tegang | 1 | saling melirik; gelembung "Hmph." | lewat (panas turun) 60 % / naik 40 % |
| T1 Cekcok | 15 | adu mulut (`ACT_CEKCOK` 57, baris ejekan bersuara, warga menoleh) | reda dengan minta maaf 25 % (RENDAH_HATI ×2) / tantangan duel 45 % / tinggalkan dengan dendam 30 % |
| T2 Duel Kehormatan | 30 | Duel Pedang (39.F.5) di arena kota, alun-alun, gerbang, atau lei tai turnamen; penonton | pemenang dihormati: kalah lapang dada → DAMAI/SAINGAN 50 %; kalah sakit hati → panas +15 (PENDENDAM ×1,5); curang (LICIK mereka, 10 %) → T3 |
| T3 Tawuran | 45 | 2-4 murid per pihak (teman yang ikut perjalanan / anggota mereka), diselesaikan sebagai **2 duel paralel** + sisanya data | menang: mereka mundur, julukan; kalah: Terluka/Luka Berat (39.5.9); keduanya: panas +10 dan cabang "tetua turun tangan" 40 % |
| T4 Sergapan | 60 | geng mereka menunggu di jalan (39.F.6) atau menyergap tahap perjalanan / misi X | lolos (kekuatan) / Terluka / Tertawan 10 % (misi penyelamatan 39.5.9, kali ini di gerbang atau kemah mereka) |
| T5 Campur tangan | 70 (atau kapan pun guru/tetua bertindak) | tetua kedua pihak bertemu: **minta maaf** (pihak salah, 40 %), **ganti rugi** (NB/PK, 30 %), **sumpah balas dendam** (`ACT_SUMPAH` 60, 30 %) | maaf/ganti rugi → panas −40, standing +3; sumpah → status DENDAM, panas tidak turun 10 hari |
| T6 Insiden diplomatik | 80 | kabar sampai ke ketua sekte | `DiplomasiService.adjust(owner, sekte, −5..−15, "mandiri.seteru.<sebab>")` (berkali-kali dibatasi −20/minggu per sekte); kronik D mencatat |
| T7 Pemicu besar | 90 | ketua sekte bertindak lewat jalan D sendiri (jembatan `DiplomasiMandiri`) | **tantangan turnamen antar-sekte resmi** (`Peristiwa.TANTANG`, 60 %) / **pernyataan perang** (`Peristiwa.PERANG`, 25 %, hanya bila `bolehPerang` dan Sikap sudah BERMUSUHAN dan intensitas TINGGI) / **utusan damai** (`Peristiwa.DAMAI` atau UTUSAN, 15 %) |
| Akhir | — | **DAMAI** (rekonsiliasi; bisa jadi sahabat lintas sekte), **SAINGAN SEUMUR HIDUP** (duel ulang tiap musim turnamen, julukan, saling hormat), **DENDAM** (cita DENDAM, iblis +, bisa memicu ulang) | ditulis ke `Ingatan` (`mandiri.seteru.*`, bobot ±6..±9 abadi untuk akhir besar) |

Batas: maks `seteruMaksAula` = 3 seteru AKTIF per aula, maks 1 per murid; intensitas config memotong tangga (RENDAH: maks T3;
SEDANG bawaan: maks T6; TINGGI: T7). Sekte yang berpakta damai/sekutu (`Pakta`) tidak naik melewati T3 (tetua mereka
melerai).

**Persahabatan dan kekasih lintas sekte**: `sikap ≥ 60` dua kali bertemu → sahabat lintas sekte (dicatat sebagai tokoh 39.8.3
peran SAHABAT_LUAR; ikut membantu di tahap perjalanan bila bertemu, menolak ikut tawuran melawan murid kita). Bila sahabat
lawan jenis dan cita JODOH: **Kekasih Terlarang** (status TERLARANG) bila Sikap sekte ≤ DINGIN: pertemuan rahasia di pasar malam
/ festival 38 (25 % per kunjungan), risiko ketahuan 15 % per pertemuan (panas +20 pada seteru kedua sekte, guru diberi tahu).
Akhir: (a) **kawin lari**: tokoh membelot dan datang sebagai calon (`Halls.addCandidates`, status MEMBELOT; standing −10, sekte
itu marah); (b) **perpisahan**: kenangan +7 abadi pahit-manis, cita RANAH prio 1; (c) **dua sekte berdamai karena mereka**
(perlu standing ≥ −10 dan guru menyetujui di layar): standing +15, upacara R (`Upacara`) di aula, pakta damai ditawarkan lewat D.

#### 39.F.4 Peran guru

Guru **mendengar** lewat: Buku Harian murid (entri bertanda seteru), Laporan Pagi (`SekteLog.log(..., "mandiri", ...)`),
pemberitahuan langkah T2+ (klik `[Lihat seteru]`), kartu hub "Perseteruan" (39.F.9), dan kronik D. Tindakan (layar
Perseteruan, atau tombol klik obrolan; masing-masing 1×/hari per seteru):

| Tindakan | Syarat | Efek | Akibat pada murid (P) |
|---|---|---|---|
| **Damaikan** | — | guru menulis surat; peluang `0.5 + standing/200 + 0.1·(guru ranah > ranah ketua mereka)`: panas −40, standing +3; gagal: panas −10 | netral; murid PENDENDAM: `TIDAK_ADIL` 2 bila murid kita korban |
| **Utus tetua** | ada Tetua bebas | tetua pergi 1 hari (perjalanan SEKTE_TAMU khusus, `Halls.storeAway` alasan `mandiri_seteru`), peluang `0.55 + 0.1·(ranah tetua − ranah tetua mereka)`: T5 berakhir maaf/ganti rugi; gagal: tetua pulang dihina, panas +10 | murid: `honored(2)` (sekte membela) |
| **Hukum murid sendiri** | murid kita yang salah atau guru ingin meredakan | panas −25, standing +3 | `SekteBus.mistreated(DIHUKUM, 5)`; bila murid kita korban: tambah `TIDAK_ADIL 4`; kenangan −4; teman sekte melihat (−1 Kepuasan bila PENYAYANG) |
| **Tuntut ganti rugi** | pihak mereka salah | peluang `0.4 + 0.1·(tier aula − tier sekte)`: `20 × tier sekte` NB ke dompet murid korban + panas −30; gagal: standing −5, panas +15 | `honored(3)` |
| **Dukung dalam duel** | duel T2 dijadwalkan | duel dipindah ke depan prasasti atau guru datang ke tempatnya; guru menjadi `challenger` di `startDirect` (penonton `PAIR_TONTON`), murid bertarung lebih berani (mundur di 10 % saja) | `honored(4)`, bond +3; menang: Kesetiaan naik (P) |
| **Tantang tetua mereka** | T5+, guru hadir di gerbang/kota | Duel Pedang guru vs Tetua Muda/Kakak Tertua mereka (tubuh RIVAL, aturan Ketua Sekte: 10 %, ≤ 3 menit): menang → mereka minta maaf (panas → 0, status DAMAI atau SAINGAN), kalah → panas +10, standing −5 | `honored(5)` semua murid yang terlibat |
| **Larang** | — | tempat/sekte itu masuk daftar terlarang murid (39.7.1); panas membeku | AMBISIUS/SOMBONG: Kepuasan −1/hari 3 hari |
| **Biarkan** | — | murid menyelesaikan sendiri | bila murid kita korban dan seteru mencapai T4 tanpa tindakan guru: `DIABAIKAN 3` |

Kebijakan aula (39.7.1) bertambah satu baris **Perseteruan**: "Selalu damaikan" / "Tanya saya" (bawaan) / "Biarkan murid".

#### 39.F.5 Adegan seru (terlihat bila dimuat)

* **Cekcok** (T1): dua tubuh berhadapan, `ACT_CEKCOK` (57: telunjuk menunjuk, dada membusung, kepala miring); baris ejekan dan
  balasan bersuara bergantian (≤ 3 baris), gelembung marah; warga kota dalam 16 blok menoleh (`HidupApi.tatap`, C39.K2).
* **Duel Kehormatan** (T2): `ACT_TANTANG` (58: salam kepalan-telapak) lalu Duel Pedang 35 lewat `DuelChallenges.startDirect`
  (challenger = guru bila hadir, selain itu null; `PAIR_NPC`/`PAIR_TONTON`; aturan `DuelRuleSet.suggested(selisihRanah)` tanpa
  taruhan): **intro duel bawaan 35** (ring, nama, kamera patuh Penuh/Singkat/Mati), ditambah **kartu judul** klien 39F 2 s
  "PERSETERUAN — <nama kita> (<sekte kita>) vs <nama mereka> (<sekte mereka>) — <sebab>". **Kerumunan**: lewat kontrak
  C39.K11 `HidupApi.timpaJadwal(situsId, "tonton:<x,z>", sampai)` warga 38 berjalan ke tepi ring (maks 8, sorak dan
  `NpcLife` reaksi); tanpa 38: suara kerumunan + ≤ 4 warga lama yang menoleh. Hasil dibaca tiap detik dari `DuelSessions.byEntity`
  (pola R). Saat tidak dimuat: undian `0.5 + 0.12·(P kita − P mereka)`, ±0,05 sifat BERANI/SOMBONG.
* **Tawuran** (T3): rombongan dua pihak berhadapan; dua pasangan terkuat bertarung sebagai dua sesi duel serentak (maks 2
  sesi per tawuran, 1 tawuran per server), sisanya pose `ACT_CEKCOK` dan sorak; hasil gabungan = mayoritas + undian data untuk
  pasangan tanpa tubuh. `Sibuk.enemyTaken(guru, "seteru")` bila guru ≤ 64 blok.
* **Sumpah dendam** (T5): yang kalah berlutut (`ACT_SUMPAH` 60, tinju ke tanah), baris `seteru_sumpah`, kabut tipis sesaat.
* **Akibat di dunia**: murid kita yang kalah pulang dengan `ACT_TERLUKA` (59: jalan pincang, perban, berhenti di dekat Balai
  Tabib/dapur) dan efek Slowness I selama Luka Berat; julukan tampil di nama tokoh; **geng patroli** (39.F.6) di jalan; papan
  kabar kota (C39.K8) "Murid <sekte> dan <sekte> bertarung di Arena Timur"; panji sekte mereka ditancapkan di pos jalan
  terdekat sebagai tantangan (prop data di tubuh patroli, bukan blok).
* **Delegasi ke aula** (T7 tantangan resmi): bila D memilih TANTANG, AntarSekte milik T berjalan biasa; 39F hanya memberi nama
  tokoh roster pada tamu yang paling mirip ranahnya (catatan cerita, tubuh tetap milik T) dan entri harian "Dia datang".

#### 39.F.6 Geng patroli dan sergapan

Bila seteru ≥ T4 dan sekte BERMUSUHAN/PERANG: satu **patroli** per aula (2-3 tokoh roster) "menjaga" ruas jalan antara aula
dan kota terdekat (ruas 38 `RencanaJalan` atau garis lurus 200-600 blok dari aula). Data murni; tubuh RIVAL hanya muncul bila
pemain ≤ 96 blok dari titik patroli (berjalan bolak-balik, panji sekte di punggung), maks 3 tubuh. Efek: perjalanan murid kita
yang melewati ruas itu mendapat peristiwa "sergapan" (39.6.2 tabel jalan, peluang 25 %, diselesaikan seperti T4); murid
Kehati-hatian AMAN memutar jalan (+20 % waktu). Guru yang melewati patroli: mereka mengejek dan menantang (baris suara), tidak
menyerang lebih dulu kecuali Sikap PERANG; pemain yang mengalahkan mereka dalam duel mengusir patroli 5 hari (panas −15,
standing −3). Patroli berakhir saat seteru turun di bawah T3 atau DAMAI.

#### 39.F.7 Suara (baris baru; diserahkan ke 39E untuk `gen_voices.py`)

Tubuh RIVAL memakai suara murid (m1-m3, f1-f3), jadi kunci yang sama berlaku untuk murid kita dan murid sekte lain.

| Kunci | Teks (id) | Jepang | Kapan |
|---|---|---|---|
| `disciple.<v>.seteru_ejek` | Murid sekte kecil berani duduk di sini? | 小さな宗門の弟子が、ここに座るとはな。 | hinaan kedai teh |
| `disciple.<v>.seteru_ejek_guru` | Gurumu tidak mengajarimu sopan santun? | 師匠に礼儀を教わらなかったのか？ | cekcok |
| `disciple.<v>.seteru_balas` | Jaga mulutmu! | 口を慎め！ | balasan |
| `disciple.<v>.seteru_tantang` | Kalau berani, naik ke panggung! | 勇気があるなら、擂台に上がれ！ | T2 tantangan |
| `disciple.<v>.seteru_terima` | Tantangan diterima. | その挑戦、受けて立つ。 | menerima |
| `disciple.<v>.seteru_menang` | Ingat hari ini. | 今日のことを覚えておけ。 | menang duel |
| `disciple.<v>.seteru_kalah` | Ini belum selesai! | これで終わりだと思うな！ | kalah duel |
| `disciple.<v>.seteru_keroyok` | Saudara-saudara, hajar mereka! | 皆、やってしまえ！ | tawuran mulai |
| `disciple.<v>.seteru_sergap` | Jalan ini milik sekte kami. | この道は我が宗門のものだ。 | patroli/sergapan |
| `disciple.<v>.seteru_lindungi` | Lepaskan warga itu! | その村人を放せ！ | melindungi warga / pedagang |
| `disciple.<v>.seteru_curi` | Kembalikan gulungan itu, pencuri! | その巻物を返せ、盗人め！ | gulungan dicuri |
| `disciple.<v>.seteru_maaf` | Aku... minta maaf atas kejadian itu. | あの件は…申し訳なかった。 | T5 maaf |
| `disciple.<v>.seteru_sumpah` | Aku bersumpah akan membalas ini! | この恨み、必ず晴らすと誓う！ | T5 sumpah |
| `disciple.<v>.seteru_damai` | Mulai hari ini, kita bukan musuh lagi. | 今日から、もう敵ではない。 | DAMAI |
| `disciple.<v>.seteru_saingan` | Musim depan, aku yang menang. | 次の大会は、私が勝つ。 | SAINGAN SEUMUR HIDUP |
| `disciple.<v>.seteru_kekasih` | Walau sekte kita bermusuhan... aku tetap memilihmu. | 宗門が敵同士でも…私はあなたを選ぶ。 | Kekasih Terlarang |
| `elder.seteru_lerai` | Cukup! Kalian mempermalukan sekte masing-masing. | もうよい！互いの宗門の恥を晒すな。 | tetua melerai |
| `elder.seteru_ganti_rugi` | Sekte kami akan membayar ganti rugi. | 我が宗門が償いをいたそう。 | ganti rugi |

16 kunci murid × 6 suara + 2 kunci tetua; sebelum klip ada: cadangan `attack_alt`, `victory`, `injured`, `farewell`, 36
`grudge`, `beloved`, `leitai_salam`, `elder.welcome` lewat `SekteSuara.line`.

#### 39.F.8 Anggaran, Sibuk, config, perintah, uji terima

**Anggaran**: roster ≤ 80 catatan dunia; seteru ≤ 3 aktif per aula; pemeriksaan pemicu hanya saat tahap perjalanan jatuh tempo
atau peristiwa bus (tanpa tick); tubuh RIVAL milik 39F ≤ 4 per server (termasuk patroli ≤ 3), AI tiap 4 tick; 1 tawuran per
server dengan ≤ 2 sesi duel; kartu judul klien sekali per duel; kerumunan ≤ 8 warga (38) atau ≤ 4 (tanpa 38); peluruhan panas
satu kali per hari dunia per aula (O(seteru)).

**Sibuk**: setiap adegan langsung di dekat guru (duel yang disaksikan, tawuran, patroli menantang, delegasi) memanggil
`Sibuk.why(guru)` dan menunda 200 tick; tawuran/sergapan dekat guru memakai `Sibuk.enemyTaken(guru, "seteru")` (batas 3/hari);
murid: penyedia `SibukMurid` "seteru" selama duel/tawuran/sumpah. Tanpa pemain dekat semuanya data.

**Config** (seksi `mandiri_seteru`, kelas `SeteruConfig` milik 39F): `aktif` true; `frekuensi` 1,0 (0-3, pengali semua peluang
pemicu); `intensitas` SEDANG (RENDAH maks T3 / SEDANG maks T6 / TINGGI maks T7); `bolehPerang` false (hanya berlaku di TINGGI;
**perlu persetujuan pengguna** untuk default true); `seteruMaksAula` 3; `panasTurun` 2; `asmaraLintas` true; `patroli` true;
`tubuhMaks` 4; `kerumunanMaks` 8; `standingMingguMaks` 20; tabel peluang 39.F.2 sebagai `peluang.*`.

**Perintah uji** (`uji perguruan mandiri seteru …`, chip `perguruan mandiri seteru`):

| Perintah | Fungsi |
|---|---|
| `daftar` | seteru aula (tingkat, panas, pihak, status) |
| `roster <sekte>` | murid sekte itu (nama, peran, ranah, sifat, sikap) |
| `mulai <murid> <sekte> <sebab>` | buat seteru dengan pemicu tertentu |
| `panas <id> <0-100>` / `naik <id> [cabang]` | set panas / paksa langkah berikut (cabang tertentu) |
| `temui <murid> <tokoh>` | pertemuan sekarang di dekat pemain (tubuh RIVAL muncul) |
| `duel <id>` / `tawuran <id>` / `sumpah <id>` | jalankan adegan |
| `patroli <id>` | buat patroli di jalan terdekat |
| `asmara <murid> <tokoh>` | mulai Kekasih Terlarang |
| `damai <id>` / `akhir <id> <DAMAI|SAINGAN|DENDAM>` | akhiri |
| `bersih` | hapus semua seteru aula (uji ulang) |

**Uji terima** (harus terlihat):
1. Murid SOMBONG ke kota dekat gerbang sekte Iblis (`uji … kirim belanja`, `frekuensi 3`): di kedai teh tokoh bernama
   mengejek dengan suara, murid kita membalas, warga menoleh; Buku Harian menyebut nama tokoh, sekte, kota.
2. Tantangan → Duel Pedang dengan intro 35, kartu judul, kerumunan berkumpul (38) atau suara kerumunan; tidak ada yang mati;
   hasil tercatat, julukan muncul.
3. Kalah → murid pulang pincang berperban (`ACT_TERLUKA`), lalu sumpah dendam bila PENDENDAM; cita DENDAM tampil di tab.
4. Panas ≥ 60 → geng patroli terlihat di jalan dengan panji sekte; perjalanan berikut disergap atau memutar (AMAN).
5. Guru: Damaikan / Utus tetua (tetua pergi 1 hari, pulang dengan maaf atau dihina) / Hukum (Kepuasan turun, terlihat di tab
   Hati) / Tuntut ganti rugi (NB masuk dompet korban) / Dukung duel (guru jadi penantang) / Tantang tetua mereka.
6. Panas ≥ 80 → standing turun di layar Diplomasi dengan alasan `mandiri.seteru.*`; ≥ 90 (TINGGI) → tantangan turnamen
   antar-sekte resmi datang lewat D (atau perang bila `bolehPerang`).
7. Persahabatan lintas sekte: tokoh membantu murid kita melawan begal di jalan. Kekasih Terlarang: pertemuan pasar malam,
   ketahuan → panas naik; kawin lari → calon baru di aula dengan nama tokoh itu.
8. Sekte berpakta damai tidak melewati T3 (tetua mereka melerai dengan suara `elder.seteru_lerai`).
9. 4 tubuh RIVAL maksimum; `uji … anggaran` tetap < 0,4 ms/tick; duel ditunda saat guru tribulasi.

#### 39.F.9 Modul 39F — Perseteruan (modul keenam yang kecil)

* **Berkas baru (server)**: `sekte/mandiri/seteru/{Seteru (init), SeteruConfig, SeteruData, Roster, Insiden, Tangga,
  Pertemuan (penyedia C39.C3), DuelSeteru, Tawuran, Patroli, CampurTangan, AsmaraLintas, SeteruPayloads, SeteruCommands}.java`;
  jembatan **baru** `sekte/diplomasi/DiplomasiMandiri.java` (memanggil `Simulasi.apply` untuk TANTANG/PERANG/DAMAI/UTUSAN dan
  membaca aliran/tier/gerbang `SekteNpc`; tidak mengubah angka D); `lang-src/sekte_mandiri_seteru.*`; advancement
  `mandiri_seteru_duel`, `mandiri_seteru_damai`, `mandiri_seteru_kekasih`.
* **Berkas baru (klien)**: `client/sekte/mandiri/seteru/{SeteruClient, SeteruScreen (kartu hub MURID "Perseteruan"
  `sekte_mandiri_seteru`), KartuJudul, SeteruPoses (pose 57-60 + prop perban, panji punggung)}.java`; potongan
  `items/fx/sekte_mandiri_seteru_*.json`; ikon `textures/sekte/mandiri/seteru/`.
* **Berkas lama**: tidak ada.
* **Titik sambung dengan lima modul 39** (masuk lingkup modul itu; berkasnya tetap satu pemilik):
  * 39A: rentang aktivitas menjadi **40-60** (`ACT_MANDIRI_LAST = 60`; 57-60 menahan titik, tidak dipulihkan); `MandiriBus`
    menambah `SETERU_MULAI(owner, idSeteru, sekte, sebab)`, `SETERU_NAIK(owner, id, tingkat)`, `SETERU_AKHIR(owner, id,
    status)`; kebijakan aula menambah baris Perseteruan; `MandiriApi.addPertemuanProvider` (stub di langkah 1).
  * 39C: mesin tahap perjalanan memanggil semua `PertemuanProvider` saat tahap jatuh tempo dan menerima hasil (luka,
    dompet, kenangan, panjang tahap) — kontrak **C39.C3**.
  * 39E: `HarianTeks` merender kunci dari fragmen mana pun (entri menyimpan kunci lengkap), jadi kunci `kultivator.sekte.
    mandiri.seteru.*` milik 39F tampil di Buku Harian; 39E menambah 18 kunci suara 39.F.7 ke suntingan `gen_voices.py`/
    `VoiceEvents` miliknya; langkah panduan opsional `c20.seteru` (`adv:sekte/mandiri_seteru_duel`).
* **Kontrak 38** tambahan: **C39.K11 kerumunan** (`HidupApi.timpaJadwal(situsId, "tonton:<x,z>", sampai)`, 38NL; bawaan: tanpa
  kerumunan berjalan).
* **Urutan**: 39F mulai setelah langkah 1 39A (stub `PertemuanProvider`, ACT 57-60, event bus); paralel dengan 39B-39E;
  menjaga `docs/progres/mandiri-39F.md`.

**Sumber**: Duel Kehormatan dan lei tai (36 R, 36 T: panggung tantangan terbuka); perjanjian tiga tahun Xiao Yan dengan Sekte
Awan Berkabut (*Battle Through the Heavens*, sudah dikutip di `AntarSekte`) → perseteruan pribadi yang menjadi tantangan resmi
antar-sekte. Angka 39.F adalah desain game.
